package com.perfumeryaicore.domain.supply.service;

import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.entity.CandidateVersionIngredient;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.supply.dto.request.RecordSupplyReviewDecisionRequest;
import com.perfumeryaicore.domain.supply.dto.request.RegisterSupplyChangeRequest;
import com.perfumeryaicore.domain.supply.dto.response.AffectedCandidateResponse;
import com.perfumeryaicore.domain.supply.dto.response.SupplyChangeResponse;
import com.perfumeryaicore.domain.supply.dto.response.SupplyReviewDecisionResponse;
import com.perfumeryaicore.domain.supply.entity.SupplyChange;
import com.perfumeryaicore.domain.supply.entity.SupplyChangeAffectedCandidate;
import com.perfumeryaicore.domain.supply.entity.SupplyReviewDecision;
import com.perfumeryaicore.domain.supply.repository.SupplyChangeAffectedCandidateRepository;
import com.perfumeryaicore.domain.supply.repository.SupplyChangeRepository;
import com.perfumeryaicore.domain.supply.repository.SupplyReviewDecisionRepository;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.common.CandidateStatus;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원료 공급 조건 변경 등록 → (동기) 영향 후보 분석 → 재검토 후속 결정 기록.
 *
 * <p>영향 분석에 외부 AI를 쓰지 않는다. "이 원료를 현재 버전에서 쓰는, 폐기되지 않은 후보"를
 * {@code candidate_version_ingredients}에서 바로 계산해 등록 시점에 확정 저장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplyChangeService {

	private final SupplyChangeRepository supplyChangeRepository;
	private final SupplyChangeAffectedCandidateRepository affectedCandidateRepository;
	private final SupplyReviewDecisionRepository reviewDecisionRepository;
	private final CandidateRepository candidateRepository;
	private final CandidateVersionIngredientRepository candidateVersionIngredientRepository;
	private final ProjectAccessGuard accessGuard;

	@Transactional
	public SupplyChangeResponse register(String ingredientId, Long memberId, RegisterSupplyChangeRequest dto) {
		accessGuard.requireRole(dto.projectId(), memberId, ProjectRole.SUPPLIER, ProjectRole.FRAGRANCE_RND);

		SupplyChange change = supplyChangeRepository.save(SupplyChange.create(
				dto.projectId(), ingredientId, dto.changeType(),
				dto.previousPricePerKg(), dto.newPricePerKg(), dto.note(), memberId));

		int affected = analyzeAndStore(change, ingredientId);
		change.recordAffectedCount(affected);

		log.info("[SUPPLY] change={} project={} ingredient={} type={} affected={} by={}",
				change.getId(), dto.projectId(), ingredientId, dto.changeType(), affected, memberId);
		return SupplyChangeResponse.from(change);
	}

	public SupplyChangeResponse get(Long changeId, Long memberId) {
		return SupplyChangeResponse.from(getAccessibleChange(changeId, memberId));
	}

	public List<AffectedCandidateResponse> affectedCandidates(Long changeId, Long memberId) {
		getAccessibleChange(changeId, memberId);
		return affectedCandidateRepository.findBySupplyChangeIdOrderByCreatedAtAsc(changeId).stream()
				.map(AffectedCandidateResponse::from)
				.toList();
	}

	@Transactional
	public SupplyReviewDecisionResponse recordDecision(Long candidateId, Long memberId,
			RecordSupplyReviewDecisionRequest dto) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));
		accessGuard.requireRole(candidate.getProjectId(), memberId,
				ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PROJECT_MANAGER);

		SupplyReviewDecision decision = reviewDecisionRepository.save(SupplyReviewDecision.record(
				candidateId, dto.supplyChangeId(), dto.decision(), dto.rationale(), memberId));

		if (dto.supplyChangeId() != null) {
			affectedCandidateRepository
					.findBySupplyChangeIdAndCandidateId(dto.supplyChangeId(), candidateId)
					.ifPresent(SupplyChangeAffectedCandidate::markReviewed);
		}
		log.info("[SUPPLY] decision={} candidate={} type={} change={} by={}",
				decision.getId(), candidateId, dto.decision(), dto.supplyChangeId(), memberId);
		return SupplyReviewDecisionResponse.from(decision);
	}

	public List<SupplyReviewDecisionResponse> listDecisions(Long candidateId, Long memberId) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));
		accessGuard.requireMember(candidate.getProjectId(), memberId);
		return reviewDecisionRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId).stream()
				.map(SupplyReviewDecisionResponse::from)
				.toList();
	}

	/** 현재 버전에서 해당 원료를 쓰는, 폐기되지 않은 후보를 찾아 영향 후보로 저장한다. */
	private int analyzeAndStore(SupplyChange change, String ingredientId) {
		List<Candidate> candidates = candidateRepository.findByProjectIdIn(List.of(change.getProjectId())).stream()
				.filter(c -> c.getStatus() != CandidateStatus.REJECTED)
				.filter(c -> c.getCurrentVersionId() != null)
				.toList();
		if (candidates.isEmpty()) {
			return 0;
		}
		Map<Long, Candidate> candidateByVersionId = candidates.stream()
				.collect(Collectors.toMap(Candidate::getCurrentVersionId, Function.identity(), (a, b) -> a));

		List<CandidateVersionIngredient> lines = candidateVersionIngredientRepository
				.findByCandidateVersionIdIn(List.copyOf(candidateByVersionId.keySet()));

		int count = 0;
		for (CandidateVersionIngredient line : lines) {
			if (!ingredientId.equals(line.getIngredientExternalId())) {
				continue;
			}
			Candidate candidate = candidateByVersionId.get(line.getCandidateVersionId());
			if (candidate == null) {
				continue;
			}
			affectedCandidateRepository.save(SupplyChangeAffectedCandidate.of(
					change.getId(), candidate.getId(), line.getCandidateVersionId(),
					line.getConcentratePercent()));
			count++;
		}
		return count;
	}

	private SupplyChange getAccessibleChange(Long changeId, Long memberId) {
		SupplyChange change = supplyChangeRepository.findById(changeId)
				.orElseThrow(() -> new BusinessException(ErrorCode.SUPPLY_CHANGE_NOT_FOUND));
		accessGuard.requireMember(change.getProjectId(), memberId);
		return change;
	}
}
