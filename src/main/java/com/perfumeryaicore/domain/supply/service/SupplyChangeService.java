package com.perfumeryaicore.domain.supply.service;

import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.entity.CandidateVersionIngredient;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.supply.dto.request.RecordSupplyReviewDecisionRequest;
import com.perfumeryaicore.domain.supply.dto.request.RegisterSupplyChangeRequest;
import com.perfumeryaicore.domain.supply.dto.response.AffectedCandidateResponse;
import com.perfumeryaicore.domain.supply.dto.response.PendingSupplyReviewResponse;
import com.perfumeryaicore.domain.supply.dto.response.SupplyChangeResponse;
import com.perfumeryaicore.domain.supply.dto.response.SupplyReviewDecisionResponse;
import com.perfumeryaicore.domain.supply.entity.SupplyChange;
import com.perfumeryaicore.domain.supply.entity.SupplyChangeAffectedCandidate;
import com.perfumeryaicore.domain.supply.entity.SupplyChangeType;
import com.perfumeryaicore.domain.supply.entity.SupplyReviewDecision;
import com.perfumeryaicore.domain.supply.entity.SupplyReviewDecisionType;
import com.perfumeryaicore.domain.supply.entity.SupplyReviewStatus;
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
import java.util.Optional;
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
		accessGuard.requireWriteRole(dto.projectId(), memberId, ProjectRole.SUPPLIER, ProjectRole.FRAGRANCE_RND);

		// BE-070: 동일 변경원천 ID 중복 수신은 새 이벤트를 만들지 않고 기존 이벤트를 그대로 반환한다.
		if (dto.changeSourceId() != null) {
			Optional<SupplyChange> existing = supplyChangeRepository.findByChangeSourceId(dto.changeSourceId());
			if (existing.isPresent()) {
				log.info("[SUPPLY] change source id {} already recorded as change={} - skipping duplicate",
						dto.changeSourceId(), existing.get().getId());
				return SupplyChangeResponse.from(existing.get());
			}
		}

		validatePriceConsistency(dto);
		validateGenericFieldChange(dto);

		SupplyChange change = supplyChangeRepository.save(SupplyChange.create(
				dto.projectId(), ingredientId, dto.changeType(), dto.previousPricePerKg(), dto.newPricePerKg(),
				dto.changedField(), dto.previousValue(), dto.newValue(), dto.changeSourceId(), dto.note(), memberId));

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
		accessGuard.requireWriteRole(candidate.getProjectId(), memberId,
				ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PROJECT_MANAGER);

		SupplyReviewDecision decision = reviewDecisionRepository.save(SupplyReviewDecision.record(
				candidateId, dto.supplyChangeId(), dto.decision(), dto.rationale(), memberId));

		// BE-079: REVISE_FORMULA(조향식 수정 예정)는 아직 아무것도 해결되지 않았다 - 실제 재평가가
		// 끝난 게 아니므로 REVIEWED로 표시하지 않는다. KEEP_FORMULA/DISCARD_CANDIDATE만 확정된
		// 결정이므로 이때만 영향 행을 REVIEWED로 닫는다.
		boolean isFinalDecision = dto.decision() == SupplyReviewDecisionType.KEEP_FORMULA
				|| dto.decision() == SupplyReviewDecisionType.DISCARD_CANDIDATE;
		if (dto.supplyChangeId() != null && isFinalDecision) {
			affectedCandidateRepository
					.findBySupplyChangeIdAndCandidateId(dto.supplyChangeId(), candidateId)
					.ifPresent(SupplyChangeAffectedCandidate::markReviewed);
		}
		log.info("[SUPPLY] decision={} candidate={} type={} change={} by={}",
				decision.getId(), candidateId, dto.decision(), dto.supplyChangeId(), memberId);
		return SupplyReviewDecisionResponse.from(decision);
	}

	/** 재검토 알림 화면(BE-090~098 일부): 프로젝트 내 아직 재검토되지 않은 영향 후보를 최신순으로 모은다. */
	public List<PendingSupplyReviewResponse> pendingReviews(Long projectId, Long memberId) {
		accessGuard.requireMember(projectId, memberId);

		List<SupplyChange> changes = supplyChangeRepository.findByProjectId(projectId);
		if (changes.isEmpty()) {
			return List.of();
		}
		Map<Long, SupplyChange> changeById = changes.stream()
				.collect(Collectors.toMap(SupplyChange::getId, Function.identity()));

		return affectedCandidateRepository
				.findBySupplyChangeIdInAndReviewStatusOrderByCreatedAtDesc(
						List.copyOf(changeById.keySet()), SupplyReviewStatus.PENDING_REVIEW)
				.stream()
				.map(affected -> PendingSupplyReviewResponse.of(affected, changeById.get(affected.getSupplyChangeId())))
				.toList();
	}

	public List<SupplyReviewDecisionResponse> listDecisions(Long candidateId, Long memberId) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));
		accessGuard.requireMember(candidate.getProjectId(), memberId);
		return reviewDecisionRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId).stream()
				.map(SupplyReviewDecisionResponse::from)
				.toList();
	}

	/**
	 * BE-070~080: PRICE_INCREASE/PRICE_DECREASE는 가격 필드가 실제로 그 방향과 맞는지 검증한다.
	 * 그 외 유형은 가격 필드가 없어도 되므로 검사하지 않는다(원인·상세는 note로 남김).
	 */
	private void validatePriceConsistency(RegisterSupplyChangeRequest dto) {
		boolean isPriceChange = dto.changeType() == SupplyChangeType.PRICE_INCREASE
				|| dto.changeType() == SupplyChangeType.PRICE_DECREASE;
		if (!isPriceChange) {
			return;
		}
		if (dto.previousPricePerKg() == null || dto.newPricePerKg() == null) {
			throw new BusinessException(ErrorCode.SUPPLY_CHANGE_PRICE_FIELDS_INCONSISTENT);
		}
		boolean actuallyIncreased = dto.newPricePerKg() > dto.previousPricePerKg();
		boolean directionMatches = dto.changeType() == SupplyChangeType.PRICE_INCREASE
				? actuallyIncreased
				: !actuallyIncreased && dto.newPricePerKg() < dto.previousPricePerKg();
		if (!directionMatches) {
			throw new BusinessException(ErrorCode.SUPPLY_CHANGE_PRICE_FIELDS_INCONSISTENT);
		}
	}

	/** BE-070에서 새로 추가한 필드 기반 변경 유형만 changedField/previousValue/newValue를 필수로 받는다. */
	private static final SupplyChangeType[] FIELD_DIFF_REQUIRED_TYPES = {
			SupplyChangeType.SAFETY_REGULATORY_CHANGE, SupplyChangeType.IDENTITY_CHANGE,
			SupplyChangeType.SUPPLY_TERMS_CHANGE
	};

	/**
	 * BE-070: 가격 외 신규 유형(안전·규제·식별·재고)은 어떤 필드가 어떻게 바뀌었는지를
	 * changedField/previousValue/newValue로 필수로 받는다 - note 자유 텍스트만으로는
	 * "정확한 이전·새 값"을 조회할 수 없다. 기존 유형(DISCONTINUED 등)은 필드 diff 없이도
	 * 등록할 수 있던 기존 동작을 그대로 유지한다.
	 */
	private void validateGenericFieldChange(RegisterSupplyChangeRequest dto) {
		boolean requiresFieldDiff = false;
		for (SupplyChangeType type : FIELD_DIFF_REQUIRED_TYPES) {
			if (dto.changeType() == type) {
				requiresFieldDiff = true;
				break;
			}
		}
		if (!requiresFieldDiff) {
			return;
		}
		if (dto.changedField() == null || dto.changedField().isBlank()
				|| dto.previousValue() == null || dto.newValue() == null) {
			throw new BusinessException(ErrorCode.SUPPLY_CHANGE_FIELD_DIFF_REQUIRED);
		}
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
