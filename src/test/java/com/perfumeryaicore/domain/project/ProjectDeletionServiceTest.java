package com.perfumeryaicore.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.project.service.ProjectDeletionService;
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
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 프로젝트 삭제(프로젝트 관리 > 프로젝트 삭제)가 하위 데이터 전체를 올바른 순서로 지우는지 검증한다. */
class ProjectDeletionServiceTest {

	private static final long PROJECT_ID = 10L;
	private static final long MEMBER_ID = 1L;

	private final ProjectRepository projectRepository = mock(ProjectRepository.class);
	private final ProjectMemberRepository projectMemberRepository = mock(ProjectMemberRepository.class);
	private final ProjectMemberAuditLogRepository projectMemberAuditLogRepository =
			mock(ProjectMemberAuditLogRepository.class);
	private final ProjectImageAssetRepository projectImageAssetRepository = mock(ProjectImageAssetRepository.class);
	private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);
	private final FragranceRequestRepository requestRepository = mock(FragranceRequestRepository.class);
	private final WorkChecklistItemRepository checklistItemRepository = mock(WorkChecklistItemRepository.class);
	private final GenerationRejectionRepository generationRejectionRepository =
			mock(GenerationRejectionRepository.class);
	private final CandidateRepository candidateRepository = mock(CandidateRepository.class);
	private final CandidateVersionRepository candidateVersionRepository = mock(CandidateVersionRepository.class);
	private final CandidateVersionIngredientRepository candidateVersionIngredientRepository =
			mock(CandidateVersionIngredientRepository.class);
	private final CandidateMemoRepository candidateMemoRepository = mock(CandidateMemoRepository.class);
	private final EvidenceReportRepository evidenceReportRepository = mock(EvidenceReportRepository.class);
	private final SensoryTestRepository sensoryTestRepository = mock(SensoryTestRepository.class);
	private final SensoryTestResultRepository sensoryTestResultRepository = mock(SensoryTestResultRepository.class);
	private final ExperimentStatusLogRepository experimentStatusLogRepository =
			mock(ExperimentStatusLogRepository.class);
	private final ApprovalGateRepository approvalGateRepository = mock(ApprovalGateRepository.class);
	private final SupplyChangeRepository supplyChangeRepository = mock(SupplyChangeRepository.class);
	private final SupplyChangeAffectedCandidateRepository supplyChangeAffectedCandidateRepository =
			mock(SupplyChangeAffectedCandidateRepository.class);
	private final SupplyReviewDecisionRepository supplyReviewDecisionRepository =
			mock(SupplyReviewDecisionRepository.class);
	private final JobRepository jobRepository = mock(JobRepository.class);
	private final CatalogSyncRunRepository catalogSyncRunRepository = mock(CatalogSyncRunRepository.class);

	private final ProjectDeletionService service = new ProjectDeletionService(
			projectRepository, projectMemberRepository, projectMemberAuditLogRepository, projectImageAssetRepository,
			accessGuard, requestRepository, checklistItemRepository, generationRejectionRepository,
			candidateRepository, candidateVersionRepository, candidateVersionIngredientRepository,
			candidateMemoRepository, evidenceReportRepository, sensoryTestRepository, sensoryTestResultRepository,
			experimentStatusLogRepository, approvalGateRepository, supplyChangeRepository,
			supplyChangeAffectedCandidateRepository, supplyReviewDecisionRepository, jobRepository,
			catalogSyncRunRepository);

	private static void setId(Object entity, long id) {
		try {
			Field field = entity.getClass().getDeclaredField("id");
			field.setAccessible(true);
			field.set(entity, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@BeforeEach
	void stubEmptyChildrenByDefault() {
		when(requestRepository.findByProjectIdIn(List.of(PROJECT_ID))).thenReturn(List.of());
		when(candidateRepository.findByProjectIdIn(List.of(PROJECT_ID))).thenReturn(List.of());
		when(candidateVersionRepository.findByCandidateIdIn(List.of())).thenReturn(List.of());
		when(sensoryTestRepository.findByCandidateIdIn(List.of())).thenReturn(List.of());
	}

	@Test
	void delete_is_forbidden_for_a_role_without_management_access() {
		when(accessGuard.requireRole(PROJECT_ID, MEMBER_ID, ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER))
				.thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.delete(PROJECT_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
		verify(projectRepository, never()).delete(any());
	}

	@Test
	void delete_fails_when_the_project_does_not_exist() {
		when(accessGuard.requireRole(PROJECT_ID, MEMBER_ID, ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER))
				.thenReturn(ProjectRole.ORG_ADMIN);
		when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(PROJECT_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_NOT_FOUND);
	}

	@Test
	void delete_removes_the_full_candidate_graph_before_the_project_itself() {
		when(accessGuard.requireRole(PROJECT_ID, MEMBER_ID, ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER))
				.thenReturn(ProjectRole.ORG_ADMIN);
		Project project = Project.create("삭제될 프로젝트", null, null, null);
		when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

		FragranceRequest request = FragranceRequest.create(PROJECT_ID, MEMBER_ID, "브리프");
		setId(request, 500L);
		when(requestRepository.findByProjectIdIn(List.of(PROJECT_ID))).thenReturn(List.of(request));

		Candidate candidate = Candidate.create(500L, PROJECT_ID, MEMBER_ID, null);
		setId(candidate, 900L);
		when(candidateRepository.findByProjectIdIn(List.of(PROJECT_ID))).thenReturn(List.of(candidate));

		CandidateVersion version = CandidateVersion.builder().candidateId(900L).createdBy(MEMBER_ID).build();
		setId(version, 1200L);
		when(candidateVersionRepository.findByCandidateIdIn(List.of(900L))).thenReturn(List.of(version));

		SensoryTest sensoryTest = mock(SensoryTest.class);
		when(sensoryTest.getId()).thenReturn(2000L);
		when(sensoryTestRepository.findByCandidateIdIn(List.of(900L))).thenReturn(List.of(sensoryTest));

		service.delete(PROJECT_ID, MEMBER_ID);

		verify(sensoryTestResultRepository).deleteAll(any());
		verify(sensoryTestRepository).deleteAll(List.of(sensoryTest));
		verify(evidenceReportRepository).findByCandidateIdIn(List.of(900L));
		verify(candidateVersionIngredientRepository).findByCandidateVersionIdIn(List.of(1200L));
		verify(candidateVersionRepository).deleteAll(List.of(version));
		verify(candidateRepository).deleteAll(List.of(candidate));
		verify(requestRepository).deleteAll(List.of(request));
		verify(projectMemberRepository).deleteAll(any());
		verify(projectRepository).delete(project);
	}

	@Test
	void delete_succeeds_for_a_project_with_no_requests_or_candidates() {
		when(accessGuard.requireRole(PROJECT_ID, MEMBER_ID, ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER))
				.thenReturn(ProjectRole.PROJECT_MANAGER);
		Project project = Project.create("빈 프로젝트", null, null, null);
		when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

		service.delete(PROJECT_ID, MEMBER_ID);

		verify(projectRepository).delete(project);
	}
}
