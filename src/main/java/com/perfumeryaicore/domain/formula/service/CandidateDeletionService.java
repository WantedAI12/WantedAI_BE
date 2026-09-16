package com.perfumeryaicore.domain.formula.service;

import com.perfumeryaicore.domain.evidence.entity.SensoryTest;
import com.perfumeryaicore.domain.evidence.repository.EvidenceReportRepository;
import com.perfumeryaicore.domain.evidence.repository.SensoryTestRepository;
import com.perfumeryaicore.domain.evidence.repository.SensoryTestResultRepository;
import com.perfumeryaicore.domain.experiment.repository.ExperimentStatusLogRepository;
import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.entity.CandidateVersion;
import com.perfumeryaicore.domain.formula.repository.CandidateMemoRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionRepository;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.safety.repository.ApprovalGateRepository;
import com.perfumeryaicore.domain.supply.repository.SupplyChangeAffectedCandidateRepository;
import com.perfumeryaicore.domain.supply.repository.SupplyReviewDecisionRepository;
import com.perfumeryaicore.global.common.CandidateStatus;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 후보(조향식) 한 건 삭제. {@link com.perfumeryaicore.domain.project.service.ProjectDeletionService}와
 * 같은 원칙(가장 깊은 자식부터 위로) - 프로젝트 전체가 아니라 후보 한 건과 그 하위 데이터만
 * 지운다는 점만 다르다. 승인된 후보는 지울 수 없다 - 이미 확정된 결과를 실수로 지우는 사고를
 * 막는다({@code REQUEST_EDIT_NOT_ALLOWED}와 같은 원칙, 확정 상태는 보호).
 *
 * <p>{@code derived_from_candidate_id}로 이 후보를 참조하는 다른 후보(복제본)가 있어도 그 참조는
 * 실제 외래키가 아니라 값 컬럼이라 지운 뒤에도 깨지지 않는다 - 다만 가리키는 원본이 사라졌다는
 * 뜻이 되므로 화면에서 그 값을 신뢰할 때는 참고용으로만 다뤄야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandidateDeletionService {

	/** 새 후보를 만들 수 있는 역할만 지울 수 있다(BE-004와 같은 원칙 - 후보 생명주기 관리 행위). */
	private static final ProjectRole[] DELETE_ROLES = {
			ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PRODUCT_BRAND
	};

	private final CandidateRepository candidateRepository;
	private final CandidateVersionRepository candidateVersionRepository;
	private final CandidateVersionIngredientRepository candidateVersionIngredientRepository;
	private final CandidateMemoRepository candidateMemoRepository;
	private final ProjectAccessGuard accessGuard;

	private final EvidenceReportRepository evidenceReportRepository;
	private final SensoryTestRepository sensoryTestRepository;
	private final SensoryTestResultRepository sensoryTestResultRepository;
	private final ExperimentStatusLogRepository experimentStatusLogRepository;
	private final ApprovalGateRepository approvalGateRepository;

	private final SupplyChangeAffectedCandidateRepository supplyChangeAffectedCandidateRepository;
	private final SupplyReviewDecisionRepository supplyReviewDecisionRepository;

	@Transactional
	public void delete(Long candidateId, Long memberId) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));
		accessGuard.requireWriteRole(candidate.getProjectId(), memberId, DELETE_ROLES);
		if (candidate.getStatus() == CandidateStatus.APPROVED) {
			throw new BusinessException(ErrorCode.CANDIDATE_DELETE_NOT_ALLOWED);
		}

		List<Long> candidateIds = List.of(candidateId);
		List<CandidateVersion> versions = candidateVersionRepository.findByCandidateIdIn(candidateIds);
		List<Long> versionIds = versions.stream().map(CandidateVersion::getId).toList();
		List<SensoryTest> sensoryTests = sensoryTestRepository.findByCandidateIdIn(candidateIds);
		List<Long> sensoryTestIds = sensoryTests.stream().map(SensoryTest::getId).toList();

		sensoryTestResultRepository.deleteAll(sensoryTestResultRepository.findBySensoryTestIdIn(sensoryTestIds));
		sensoryTestRepository.deleteAll(sensoryTests);
		evidenceReportRepository.deleteAll(evidenceReportRepository.findByCandidateIdIn(candidateIds));
		experimentStatusLogRepository.deleteAll(experimentStatusLogRepository.findByCandidateIdIn(candidateIds));
		approvalGateRepository.deleteAll(approvalGateRepository.findByCandidateIdIn(candidateIds));
		supplyReviewDecisionRepository.deleteAll(supplyReviewDecisionRepository.findByCandidateIdIn(candidateIds));
		supplyChangeAffectedCandidateRepository.deleteAll(
				supplyChangeAffectedCandidateRepository.findByCandidateIdIn(candidateIds));
		candidateMemoRepository.deleteAll(candidateMemoRepository.findByCandidateIdIn(candidateIds));
		candidateVersionIngredientRepository.deleteAll(
				candidateVersionIngredientRepository.findByCandidateVersionIdIn(versionIds));
		candidateVersionRepository.deleteAll(versions);
		candidateRepository.delete(candidate);

		log.info("[CANDIDATE] id={} deleted by={} versions={}", candidateId, memberId, versionIds.size());
	}
}
