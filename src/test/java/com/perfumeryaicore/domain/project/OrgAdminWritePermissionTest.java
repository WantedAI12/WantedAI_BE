package com.perfumeryaicore.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.evidence.repository.EvidenceReportRepository;
import com.perfumeryaicore.domain.evidence.repository.SensoryTestRepository;
import com.perfumeryaicore.domain.evidence.repository.SensoryTestResultRepository;
import com.perfumeryaicore.domain.evidence.service.SensoryTestService;
import com.perfumeryaicore.domain.experiment.repository.ExperimentStatusLogRepository;
import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.repository.CandidateMemoRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionRepository;
import com.perfumeryaicore.domain.formula.service.CandidateDeletionService;
import com.perfumeryaicore.domain.formula.service.CandidateGenerationService;
import com.perfumeryaicore.domain.formula.service.CandidatePersistenceService;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.formula.service.CandidateVersionMapper;
import com.perfumeryaicore.domain.formula.service.FormulaRequestMapper;
import com.perfumeryaicore.domain.job.dto.response.JobResponse;
import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.service.JobExecutor;
import com.perfumeryaicore.domain.job.service.JobService;
import com.perfumeryaicore.domain.prediction.service.PredictionService;
import com.perfumeryaicore.domain.project.entity.ProjectMember;
import com.perfumeryaicore.domain.project.repository.ProjectMemberRepository;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.request.dto.request.CreateFragranceRequestRequest;
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.repository.FragranceRequestRepository;
import com.perfumeryaicore.domain.request.service.FragranceRequestService;
import com.perfumeryaicore.domain.request.service.WorkChecklistService;
import com.perfumeryaicore.domain.safety.repository.ApprovalGateRepository;
import com.perfumeryaicore.domain.safety.service.ApprovalGateService;
import com.perfumeryaicore.domain.safety.service.SafetyEvaluationService;
import com.perfumeryaicore.domain.supply.repository.SupplyChangeAffectedCandidateRepository;
import com.perfumeryaicore.domain.supply.repository.SupplyReviewDecisionRepository;
import com.perfumeryaicore.global.client.PerfumeryAiClient;
import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.common.TargetRegion;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 기획 결정(2026-09-19): 조직 관리자(ORG_ADMIN)도 실무 쓰기(향 요청·후보 생성·복제·삭제)를 할 수 있다.
 * 관능시험 결과 등록, 안전·규제 검토, 최종 승인은 기존 담당 역할 권한을 유지한다. 게스트(조향사로 등록된
 * 1인 프로젝트 사용자)의 동작은 그대로여야 한다. 목이 아닌 실제 {@link ProjectAccessGuard}로 검증한다.
 */
class OrgAdminWritePermissionTest {

	private static final long PROJECT_ID = 10L;
	private static final long MEMBER_ID = 1L;
	private static final long TEAM_SIZE = 3L;

	private final ProjectMemberRepository membership = mock(ProjectMemberRepository.class);
	private final ProjectAccessGuard guard = new ProjectAccessGuard(membership);

	private void memberWith(ProjectRole role, long memberCount) {
		when(membership.findByProjectIdAndMemberId(PROJECT_ID, MEMBER_ID))
				.thenReturn(Optional.of(ProjectMember.create(PROJECT_ID, MEMBER_ID, role)));
		when(membership.existsByProjectIdAndMemberId(PROJECT_ID, MEMBER_ID)).thenReturn(true);
		when(membership.countByProjectId(PROJECT_ID)).thenReturn(memberCount);
	}

	// --- 서비스 구성 ---

	private FragranceRequestService requestService() {
		FragranceRequestRepository repository = mock(FragranceRequestRepository.class);
		when(repository.save(any(FragranceRequest.class))).thenAnswer(inv -> inv.getArgument(0));
		return new FragranceRequestService(repository, guard, mock(WorkChecklistService.class));
	}

	private static CreateFragranceRequestRequest newRequest() {
		return new CreateFragranceRequestRequest("겨울에 쓸 수 있는 따듯한 향", ProductCategory.EAU_DE_PARFUM,
				TargetRegion.KR, 1, null, null, null, null, null, List.of());
	}

	private final CandidateRepository candidateRepository = mock(CandidateRepository.class);
	private final CandidateVersionRepository candidateVersionRepository = mock(CandidateVersionRepository.class);

	private CandidateService candidateService() {
		return new CandidateService(candidateRepository, candidateVersionRepository,
				mock(CandidateVersionIngredientRepository.class), mock(CandidateVersionMapper.class), guard,
				mock(FragranceRequestService.class));
	}

	private Candidate storedCandidate() {
		Candidate candidate = Candidate.create(500L, PROJECT_ID, MEMBER_ID, null);
		ReflectionTestUtils.setField(candidate, "id", 100L);
		when(candidateRepository.findById(100L)).thenReturn(Optional.of(candidate));
		return candidate;
	}

	private CandidateDeletionService deletionService() {
		return new CandidateDeletionService(candidateRepository, candidateVersionRepository,
				mock(CandidateVersionIngredientRepository.class), mock(CandidateMemoRepository.class), guard,
				mock(EvidenceReportRepository.class), mock(SensoryTestRepository.class),
				mock(SensoryTestResultRepository.class), mock(ExperimentStatusLogRepository.class),
				mock(ApprovalGateRepository.class), mock(SupplyChangeAffectedCandidateRepository.class),
				mock(SupplyReviewDecisionRepository.class));
	}

	private final FragranceRequestService generationRequestService = mock(FragranceRequestService.class);
	private final JobService jobService = mock(JobService.class);
	private final JobExecutor jobExecutor = mock(JobExecutor.class);

	private CandidateGenerationService generationService() {
		FragranceRequest confirmed = FragranceRequest.create(PROJECT_ID, MEMBER_ID, "citrus woody");
		ReflectionTestUtils.setField(confirmed, "id", 5L);
		when(generationRequestService.getConfirmedRequest(5L, MEMBER_ID)).thenReturn(confirmed);
		Job job = Job.pending(PROJECT_ID, JobType.CANDIDATE_GENERATION, MEMBER_ID, "5");
		ReflectionTestUtils.setField(job, "id", 77L);
		when(jobService.enqueue(PROJECT_ID, JobType.CANDIDATE_GENERATION, MEMBER_ID, "5", null)).thenReturn(job);
		when(jobService.get(77L, MEMBER_ID)).thenReturn(
				new JobResponse(77L, JobType.CANDIDATE_GENERATION, JobStatus.PENDING, false, null, null, null, null));
		return new CandidateGenerationService(generationRequestService, jobService, jobExecutor,
				mock(PerfumeryAiClient.class), mock(FormulaRequestMapper.class),
				mock(CandidatePersistenceService.class), guard);
	}

	private SensoryTestService sensoryTestService() {
		com.perfumeryaicore.domain.formula.service.CandidateService candidates =
				mock(com.perfumeryaicore.domain.formula.service.CandidateService.class);
		when(candidates.getProjectId(100L, MEMBER_ID)).thenReturn(PROJECT_ID);
		return new SensoryTestService(mock(SensoryTestRepository.class), mock(SensoryTestResultRepository.class),
				candidates, mock(PredictionService.class), guard);
	}

	private ApprovalGateService approvalGateService() {
		com.perfumeryaicore.domain.formula.service.CandidateService candidates =
				mock(com.perfumeryaicore.domain.formula.service.CandidateService.class);
		when(candidates.getProjectId(100L, MEMBER_ID)).thenReturn(PROJECT_ID);
		return new ApprovalGateService(mock(ApprovalGateRepository.class), candidates,
				mock(SafetyEvaluationService.class), guard);
	}

	// --- 조직 관리자: 팀원이 있어도 실무 쓰기 가능 ---

	@Test
	void an_org_admin_of_a_team_project_can_create_a_request() {
		memberWith(ProjectRole.ORG_ADMIN, TEAM_SIZE);

		var response = requestService().create(PROJECT_ID, MEMBER_ID, newRequest());

		assertThat(response.structuredIntent().rawText()).isEqualTo("겨울에 쓸 수 있는 따듯한 향");
	}

	@Test
	void an_org_admin_of_a_team_project_can_start_candidate_generation() {
		memberWith(ProjectRole.ORG_ADMIN, TEAM_SIZE);

		generationService().enqueue(5L, MEMBER_ID);

		verify(jobExecutor).execute(eq(77L), eq(JobType.CANDIDATE_GENERATION), any());
	}

	/** 역할 검사를 통과한 뒤에야 나오는 오류(후보 버전 없음)로, 검사를 통과했음을 확인한다. */
	@Test
	void an_org_admin_of_a_team_project_passes_the_role_check_for_duplicating_and_restoring_a_candidate() {
		memberWith(ProjectRole.ORG_ADMIN, TEAM_SIZE);
		storedCandidate();

		assertThatThrownBy(() -> candidateService().duplicate(100L, MEMBER_ID, "복제"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_VERSION_NOT_FOUND);
		assertThatThrownBy(() -> candidateService().restoreVersion(100L, MEMBER_ID, 999L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_VERSION_NOT_FOUND);
	}

	@Test
	void an_org_admin_of_a_team_project_can_delete_a_candidate() {
		memberWith(ProjectRole.ORG_ADMIN, TEAM_SIZE);
		Candidate candidate = storedCandidate();

		deletionService().delete(100L, MEMBER_ID);

		verify(candidateRepository).delete(candidate);
	}

	// --- 기존 담당 역할 권한 유지: 조직 관리자에게 열지 않는다 ---

	@Test
	void an_org_admin_of_a_team_project_still_cannot_plan_a_sensory_test() {
		memberWith(ProjectRole.ORG_ADMIN, TEAM_SIZE);

		assertThatThrownBy(() -> sensoryTestService().plan(100L, MEMBER_ID, null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	@Test
	void an_org_admin_still_cannot_register_the_final_approval() {
		memberWith(ProjectRole.ORG_ADMIN, TEAM_SIZE);

		assertThatThrownBy(() -> approvalGateService().register(100L, MEMBER_ID, null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	/** 읽기·외부 협업 역할은 실무 쓰기를 못 한다(BE-004) - 이번 변경으로 넓어지지 않는다. */
	@Test
	void a_supplier_or_auditor_still_cannot_create_requests() {
		for (ProjectRole role : List.of(ProjectRole.SUPPLIER, ProjectRole.AUDITOR, ProjectRole.SAFETY_REVIEWER)) {
			memberWith(role, TEAM_SIZE);

			assertThatThrownBy(() -> requestService().create(PROJECT_ID, MEMBER_ID, newRequest()))
					.as("역할 %s", role)
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
		}
	}

	// --- 게스트: 조향사로 등록된 1인 프로젝트 사용자 - 동작이 그대로여야 한다 ---

	@Test
	void a_guest_shaped_solo_perfumer_behaves_exactly_as_before() {
		memberWith(ProjectRole.PERFUMER, 1L);

		// 가능했던 것: 요청 작성, 후보 생성, 복제, 삭제
		assertThat(requestService().create(PROJECT_ID, MEMBER_ID, newRequest())).isNotNull();
		generationService().enqueue(5L, MEMBER_ID);
		verify(jobExecutor).execute(eq(77L), eq(JobType.CANDIDATE_GENERATION), any());
		Candidate candidate = storedCandidate();
		deletionService().delete(100L, MEMBER_ID);
		verify(candidateRepository).delete(candidate);

		// 원래도 못 했던 것: 관능시험 계획, 최종 승인 - 이번 변경으로 달라지지 않는다
		assertThatThrownBy(() -> sensoryTestService().plan(100L, MEMBER_ID, null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
		assertThatThrownBy(() -> approvalGateService().register(100L, MEMBER_ID, null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	/** 혼자인 조직 관리자가 원래 쓰던 1인 프로젝트 예외는 그대로 남는다. */
	@Test
	void a_solo_org_admin_keeps_the_previous_solo_exception_for_everything_else() {
		memberWith(ProjectRole.ORG_ADMIN, 1L);

		assertThat(requestService().create(PROJECT_ID, MEMBER_ID, newRequest())).isNotNull();
	}
}
