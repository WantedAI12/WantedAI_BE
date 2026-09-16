package com.perfumeryaicore.domain.project.service;

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
import com.perfumeryaicore.domain.formula.repository.GenerationRejectionRepository;
import com.perfumeryaicore.domain.ingredient.repository.CatalogSyncRunRepository;
import com.perfumeryaicore.domain.job.repository.JobRepository;
import com.perfumeryaicore.domain.project.entity.Project;
import com.perfumeryaicore.domain.project.repository.ProjectImageAssetRepository;
import com.perfumeryaicore.domain.project.repository.ProjectMemberAuditLogRepository;
import com.perfumeryaicore.domain.project.repository.ProjectMemberRepository;
import com.perfumeryaicore.domain.project.repository.ProjectRepository;
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.repository.FragranceRequestRepository;
import com.perfumeryaicore.domain.request.repository.WorkChecklistItemRepository;
import com.perfumeryaicore.domain.safety.repository.ApprovalGateRepository;
import com.perfumeryaicore.domain.supply.repository.SupplyChangeAffectedCandidateRepository;
import com.perfumeryaicore.domain.supply.repository.SupplyChangeRepository;
import com.perfumeryaicore.domain.supply.repository.SupplyReviewDecisionRepository;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 삭제(프로젝트 관리 > 프로젝트 삭제). 프로젝트에 걸린 모든 하위 데이터를 물리 삭제한다 -
 * 요청·후보·버전·원료·근거·안전평가·실험이력·공급변경까지 전부. 되돌릴 수 없다.
 *
 * <p>외래키 순서를 지키기 위해 가장 깊은 자식(후보 하위 데이터)부터 위로 올라가며 지운다.
 * S3에 올라간 프로젝트 이미지 원본 파일은 지우지 않는다 - 기존 {@code unlink()}도 DB 행만
 * 정리하고 실제 오브젝트는 그대로 두는 것과 같은 원칙(별도 정리 배치 대상).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectDeletionService {

	private final ProjectRepository projectRepository;
	private final ProjectMemberRepository projectMemberRepository;
	private final ProjectMemberAuditLogRepository projectMemberAuditLogRepository;
	private final ProjectImageAssetRepository projectImageAssetRepository;
	private final ProjectAccessGuard accessGuard;

	private final FragranceRequestRepository requestRepository;
	private final WorkChecklistItemRepository checklistItemRepository;
	private final GenerationRejectionRepository generationRejectionRepository;

	private final CandidateRepository candidateRepository;
	private final CandidateVersionRepository candidateVersionRepository;
	private final CandidateVersionIngredientRepository candidateVersionIngredientRepository;
	private final CandidateMemoRepository candidateMemoRepository;

	private final EvidenceReportRepository evidenceReportRepository;
	private final SensoryTestRepository sensoryTestRepository;
	private final SensoryTestResultRepository sensoryTestResultRepository;
	private final ExperimentStatusLogRepository experimentStatusLogRepository;
	private final ApprovalGateRepository approvalGateRepository;

	private final SupplyChangeRepository supplyChangeRepository;
	private final SupplyChangeAffectedCandidateRepository supplyChangeAffectedCandidateRepository;
	private final SupplyReviewDecisionRepository supplyReviewDecisionRepository;

	private final JobRepository jobRepository;
	private final CatalogSyncRunRepository catalogSyncRunRepository;

	@Transactional
	public void delete(Long projectId, Long memberId) {
		accessGuard.requireRole(projectId, memberId, ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER);
		Project project = projectRepository.findById(projectId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));

		List<FragranceRequest> requests = requestRepository.findByProjectIdIn(List.of(projectId));
		List<Long> requestIds = requests.stream().map(FragranceRequest::getId).toList();
		List<Candidate> candidates = candidateRepository.findByProjectIdIn(List.of(projectId));
		List<Long> candidateIds = candidates.stream().map(Candidate::getId).toList();
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
		candidateRepository.deleteAll(candidates);

		generationRejectionRepository.deleteAll(generationRejectionRepository.findByProjectId(projectId));
		checklistItemRepository.deleteAll(checklistItemRepository.findByRequestIdIn(requestIds));
		requestRepository.deleteAll(requests);

		supplyChangeRepository.deleteAll(supplyChangeRepository.findByProjectId(projectId));
		jobRepository.deleteAll(jobRepository.findByProjectId(projectId));
		catalogSyncRunRepository.deleteAll(catalogSyncRunRepository.findByProjectId(projectId));
		projectImageAssetRepository.deleteAll(projectImageAssetRepository.findByProjectId(projectId));
		projectMemberAuditLogRepository.deleteAll(
				projectMemberAuditLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId));
		projectMemberRepository.deleteAll(projectMemberRepository.findByProjectIdOrderByCreatedAtAsc(projectId));

		projectRepository.delete(project);
		log.info("[PROJECT] id={} deleted by={} requests={} candidates={}",
				projectId, memberId, requestIds.size(), candidateIds.size());
	}
}
