package com.perfumeryaicore.domain.formula.service;

import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse;
import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.entity.CandidateVersion;
import com.perfumeryaicore.domain.formula.entity.CandidateVersionIngredient;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionRepository;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.request.service.FragranceRequestService;
import com.perfumeryaicore.global.common.CandidateStatus;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 후보·후보 버전 조회. 접근 제어는 후보가 속한 프로젝트의 멤버십 기준
 * ({@link ProjectAccessGuard}) — 프로젝트 멤버라면 생성자가 아니어도 조회·수정할 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandidateService {

	/** SUPPLIER·AUDITOR는 후보를 복제할 수 없다(BE-004와 같은 원칙 - 새 후보 생성 행위). */
	private static final ProjectRole[] DUPLICATE_ROLES = {
			ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PRODUCT_BRAND, ProjectRole.ORG_ADMIN
	};

	private final CandidateRepository candidateRepository;
	private final CandidateVersionRepository candidateVersionRepository;
	private final CandidateVersionIngredientRepository ingredientRepository;
	private final CandidateVersionMapper versionMapper;
	private final ProjectAccessGuard accessGuard;
	private final FragranceRequestService fragranceRequestService;

	/**
	 * 요청에 속한 후보 전체를 조회한다. 후보마다 버전·원료를 각각 조회하면(N+1) 후보 수만큼
	 * 쿼리가 늘어나므로, 현재 버전 ID를 모아 한 번에 조회한다(BE-085 후속).
	 */
	public List<CandidateResponse> listByRequest(Long requestId, Long memberId) {
		List<Candidate> candidates = candidateRepository.findByRequestIdOrderByCreatedAtDesc(requestId).stream()
				.filter(c -> accessGuard.isMember(c.getProjectId(), memberId))
				.toList();
		if (candidates.isEmpty()) {
			return List.of();
		}

		List<Long> versionIds = candidates.stream()
				.map(Candidate::getCurrentVersionId)
				.filter(Objects::nonNull)
				.toList();
		Map<Long, CandidateVersion> versionsById = candidateVersionRepository.findAllById(versionIds).stream()
				.collect(Collectors.toMap(CandidateVersion::getId, Function.identity()));
		Map<Long, List<CandidateVersionIngredient>> ingredientsByVersionId = ingredientRepository
				.findByCandidateVersionIdIn(versionIds).stream()
				.collect(Collectors.groupingBy(CandidateVersionIngredient::getCandidateVersionId));

		// 목록의 후보는 모두 같은 요청 소속이라 순번은 한 번만 계산한다.
		int requestNumber = fragranceRequestService.requestNumber(
				candidates.get(0).getProjectId(), candidates.get(0).getRequestId());
		return candidates.stream()
				.map(c -> toResponse(c, requestNumber, versionsById, ingredientsByVersionId))
				.toList();
	}

	private CandidateResponse toResponse(Candidate candidate, int requestNumber,
			Map<Long, CandidateVersion> versionsById, Map<Long, List<CandidateVersionIngredient>> ingredientsByVersionId) {
		CandidateVersionResponse current = null;
		if (candidate.getCurrentVersionId() != null) {
			CandidateVersion version = versionsById.get(candidate.getCurrentVersionId());
			if (version != null) {
				List<CandidateVersionIngredient> ingredients =
						ingredientsByVersionId.getOrDefault(version.getId(), List.of());
				current = versionMapper.toResponse(version, ingredients);
			}
		}
		return new CandidateResponse(candidate.getId(), candidate.getRequestId(), requestNumber,
				candidate.getStatus(), current,
				candidate.getDerivedFromCandidateId(), candidate.getDerivedFromVersionId(),
				candidate.getDerivationReason());
	}

	public CandidateResponse get(Long candidateId, Long memberId) {
		return toResponse(getAccessibleCandidate(candidateId, memberId));
	}

	public List<CandidateVersionResponse> versions(Long candidateId, Long memberId) {
		Candidate candidate = getAccessibleCandidate(candidateId, memberId);
		return candidateVersionRepository.findByCandidateIdOrderByCreatedAtDesc(candidate.getId()).stream()
				.map(this::toVersionResponse)
				.toList();
	}

	/**
	 * 후보의 현재 버전 원문을 반환한다. safety/prediction 도메인이 각자 필요한 구간만 파싱해 쓴다
	 * (formula → safety/prediction 워크플로 방향, {@link CandidateVersionRawView} 참고).
	 */
	public CandidateVersionRawView getCurrentVersionRaw(Long candidateId, Long memberId) {
		Candidate candidate = getAccessibleCandidate(candidateId, memberId);
		if (candidate.getCurrentVersionId() == null) {
			throw new BusinessException(ErrorCode.CANDIDATE_VERSION_NOT_FOUND);
		}
		CandidateVersion version = candidateVersionRepository.findById(candidate.getCurrentVersionId())
				.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_VERSION_NOT_FOUND));
		return new CandidateVersionRawView(candidate.getId(), version.getId(), version.getRawResponse());
	}

	/** 접근 가능한 후보인지만 확인한다(존재 + 소유자). 다른 도메인의 접근 제어 재사용용. */
	public void assertAccessible(Long candidateId, Long memberId) {
		getAccessibleCandidate(candidateId, memberId);
	}

	/** 후보가 속한 프로젝트 ID. evidence 도메인이 Job 등록 시 필요(다른 도메인의 접근 제어 재사용용). */
	public Long getProjectId(Long candidateId, Long memberId) {
		return getAccessibleCandidate(candidateId, memberId).getProjectId();
	}

	/** 후보의 현재 버전 ID. 메모 등 부가 컨텍스트 기록용(다른 도메인의 접근 제어 재사용용). */
	public Long getCurrentVersionId(Long candidateId, Long memberId) {
		return getAccessibleCandidate(candidateId, memberId).getCurrentVersionId();
	}

	/**
	 * 실험 워크플로 상태를 전이한다. 상태 순서만 이 메서드가 검증하고, 안전 게이트 승인 같은
	 * 다른 도메인 조건은 experiment 도메인이 호출 전에 확인한다(formula → safety 역방향
	 * 의존을 만들지 않기 위해).
	 */
	@Transactional
	public CandidateStatus transitionStatus(Long candidateId, Long memberId, CandidateStatus target) {
		Candidate candidate = getAccessibleCandidate(candidateId, memberId);
		candidate.transitionStatus(target);
		return candidate.getStatus();
	}

	/**
	 * BE-025: 후보의 현재 버전(원료 구성 포함)을 통째로 복사해 새 후보를 만든다. 새 후보는
	 * UNDER_REVIEW로 새로 시작하며 원본의 승인·실험 확정·관능 검증 이력을 상속하지 않는다.
	 * AI를 호출하지 않는 순수 DB 복사다.
	 */
	@Transactional
	public CandidateResponse duplicate(Long candidateId, Long memberId, String reason) {
		Candidate source = getAccessibleCandidate(candidateId, memberId);
		accessGuard.requireWriteRole(source.getProjectId(), memberId, DUPLICATE_ROLES);
		if (source.getCurrentVersionId() == null) {
			throw new BusinessException(ErrorCode.CANDIDATE_VERSION_NOT_FOUND);
		}
		CandidateVersion sourceVersion = candidateVersionRepository.findById(source.getCurrentVersionId())
				.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_VERSION_NOT_FOUND));

		Candidate duplicated = candidateRepository.save(Candidate.duplicate(
				source.getRequestId(), source.getProjectId(), memberId,
				source.getId(), sourceVersion.getId(), reason));

		CandidateVersion newVersion = candidateVersionRepository.save(CandidateVersion.builder()
				.candidateId(duplicated.getId())
				.parentVersionId(null)
				.cost(sourceVersion.getCost())
				.generationRationale(sourceVersion.getGenerationRationale())
				.aiProvider(sourceVersion.getAiProvider())
				.aiGpuUsed(sourceVersion.getAiGpuUsed())
				.aiResponseStatus(sourceVersion.getAiResponseStatus())
				.aiLatencyMs(sourceVersion.getAiLatencyMs())
				.rawResponse(sourceVersion.getRawResponse())
				.createdBy(memberId)
				.build());
		duplicated.attachVersion(newVersion.getId());

		List<CandidateVersionIngredient> copiedLines = ingredientRepository
				.findByCandidateVersionId(sourceVersion.getId()).stream()
				.map(line -> CandidateVersionIngredient.builder()
						.candidateVersionId(newVersion.getId())
						.ingredientExternalId(line.getIngredientExternalId())
						.ingredientName(line.getIngredientName())
						.pyramid(line.getPyramid())
						.concentratePercent(line.getConcentratePercent())
						.finishedProductPercent(line.getFinishedProductPercent())
						.pricePerKg(line.getPricePerKg())
						.availability(line.getAvailability())
						.build())
				.toList();
		if (!copiedLines.isEmpty()) {
			ingredientRepository.saveAll(copiedLines);
		}

		return toResponse(duplicated);
	}

	/**
	 * BE-026/027: 과거 버전을 복원한다. 과거 레코드를 수정하지 않고 항상 새 버전을 만들어
	 * 붙인다 - v1을 복원해도 그 사이에 쌓인 v2 기록은 그대로 남고, 새로 만들어지는 버전은
	 * v2 다음(parentVersionId=v2)으로 이어지며 원본은 restoredFromVersionId로 추적한다.
	 * 과거 승인·실험 확정·관능 검증 상태는 새 버전에 자동으로 재활성화되지 않는다 -
	 * {@link Candidate#status}는 그대로 두고 버전만 바뀐다.
	 *
	 * <p>{@link Candidate#version}(낙관적 잠금)이 동시 복원/편집 충돌을 막는다 - 같은 후보를
	 * 동시에 복원하면 나중 커밋이 {@code ObjectOptimisticLockingFailureException}으로 실패하고
	 * 호출자는 409로 처리해야 한다.
	 */
	@Transactional
	public CandidateResponse restoreVersion(Long candidateId, Long memberId, Long targetVersionId) {
		Candidate candidate = getAccessibleCandidate(candidateId, memberId);
		accessGuard.requireWriteRole(candidate.getProjectId(), memberId, DUPLICATE_ROLES);

		CandidateVersion target = candidateVersionRepository.findById(targetVersionId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_VERSION_NOT_FOUND));
		if (!target.getCandidateId().equals(candidateId)) {
			throw new BusinessException(ErrorCode.CANDIDATE_VERSION_NOT_FOUND);
		}

		CandidateVersion restored = candidateVersionRepository.save(CandidateVersion.builder()
				.candidateId(candidateId)
				.parentVersionId(candidate.getCurrentVersionId())
				.cost(target.getCost())
				.generationRationale(target.getGenerationRationale())
				.aiProvider(target.getAiProvider())
				.aiGpuUsed(target.getAiGpuUsed())
				.aiResponseStatus(target.getAiResponseStatus())
				.aiLatencyMs(target.getAiLatencyMs())
				.rawResponse(target.getRawResponse())
				.createdBy(memberId)
				.restoredFromVersionId(target.getId())
				.build());
		candidate.attachVersion(restored.getId());

		List<CandidateVersionIngredient> copiedLines = ingredientRepository
				.findByCandidateVersionId(target.getId()).stream()
				.map(line -> CandidateVersionIngredient.builder()
						.candidateVersionId(restored.getId())
						.ingredientExternalId(line.getIngredientExternalId())
						.ingredientName(line.getIngredientName())
						.pyramid(line.getPyramid())
						.concentratePercent(line.getConcentratePercent())
						.finishedProductPercent(line.getFinishedProductPercent())
						.pricePerKg(line.getPricePerKg())
						.availability(line.getAvailability())
						.build())
				.toList();
		if (!copiedLines.isEmpty()) {
			ingredientRepository.saveAll(copiedLines);
		}

		return toResponse(candidate);
	}

	public CandidateVersionResponse version(Long versionId, Long memberId) {
		CandidateVersion version = candidateVersionRepository.findById(versionId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_VERSION_NOT_FOUND));
		Candidate candidate = candidateRepository.findById(version.getCandidateId())
				.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));
		if (!accessGuard.isMember(candidate.getProjectId(), memberId)) {
			throw new BusinessException(ErrorCode.CANDIDATE_ACCESS_DENIED);
		}
		return toVersionResponse(version);
	}

	private Candidate getAccessibleCandidate(Long candidateId, Long memberId) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));
		if (!accessGuard.isMember(candidate.getProjectId(), memberId)) {
			throw new BusinessException(ErrorCode.CANDIDATE_ACCESS_DENIED);
		}
		return candidate;
	}

	private CandidateResponse toResponse(Candidate candidate) {
		CandidateVersionResponse current = null;
		if (candidate.getCurrentVersionId() != null) {
			CandidateVersion version = candidateVersionRepository.findById(candidate.getCurrentVersionId())
					.orElse(null);
			current = version == null ? null : toVersionResponse(version);
		}
		return new CandidateResponse(candidate.getId(), candidate.getRequestId(),
				fragranceRequestService.requestNumber(candidate.getProjectId(), candidate.getRequestId()),
				candidate.getStatus(), current,
				candidate.getDerivedFromCandidateId(), candidate.getDerivedFromVersionId(),
				candidate.getDerivationReason());
	}

	private CandidateVersionResponse toVersionResponse(CandidateVersion version) {
		return versionMapper.toResponse(version, ingredientRepository.findByCandidateVersionId(version.getId()));
	}
}
