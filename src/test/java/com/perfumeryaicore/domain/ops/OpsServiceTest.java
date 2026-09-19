package com.perfumeryaicore.domain.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.formula.repository.GenerationRejectionRepository;
import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.repository.JobRepository;
import com.perfumeryaicore.domain.ops.dto.response.OpsEventResponse;
import com.perfumeryaicore.domain.ops.dto.response.OpsOverviewResponse;
import com.perfumeryaicore.domain.ops.service.OpsService;
import com.perfumeryaicore.domain.project.entity.Project;
import com.perfumeryaicore.domain.project.entity.ProjectMember;
import com.perfumeryaicore.domain.project.entity.ProjectMemberAuditLog;
import com.perfumeryaicore.domain.project.repository.ProjectMemberAuditLogRepository;
import com.perfumeryaicore.domain.project.repository.ProjectMemberRepository;
import com.perfumeryaicore.domain.project.repository.ProjectRepository;
import com.perfumeryaicore.global.common.ProjectRole;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 서비스 운영 상태 화면 API. 호출한 회원이 속한 프로젝트만 집계하는지(게스트도 본인 프로젝트 기준),
 * 그리고 카드·이벤트 표에 보일 값이 화면 요구대로 계산되는지 검증한다.
 */
class OpsServiceTest {

	private static final long MEMBER_ID = 1L;
	private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 19, 10, 0, 0);

	private final ProjectMemberRepository projectMemberRepository = mock(ProjectMemberRepository.class);
	private final ProjectRepository projectRepository = mock(ProjectRepository.class);
	private final JobRepository jobRepository = mock(JobRepository.class);
	private final GenerationRejectionRepository generationRejectionRepository =
			mock(GenerationRejectionRepository.class);
	private final ProjectMemberAuditLogRepository auditLogRepository = mock(ProjectMemberAuditLogRepository.class);
	private final OpsService service = new OpsService(projectMemberRepository, projectRepository, jobRepository,
			generationRejectionRepository, auditLogRepository);

	private void memberBelongsTo(long... projectIds) {
		List<ProjectMember> memberships = java.util.Arrays.stream(projectIds)
				.mapToObj(id -> ProjectMember.create(id, MEMBER_ID, ProjectRole.PERFUMER))
				.toList();
		when(projectMemberRepository.findByMemberIdOrderByCreatedAtDesc(MEMBER_ID)).thenReturn(memberships);
	}

	private static Job job(long id, long projectId, JobType type, JobStatus status, LocalDateTime updatedAt,
			LocalDateTime aiCallStartedAt, String failureReason) {
		Job job = Job.pending(projectId, type, MEMBER_ID, null);
		ReflectionTestUtils.setField(job, "id", id);
		ReflectionTestUtils.setField(job, "status", status);
		ReflectionTestUtils.setField(job, "updatedAt", updatedAt);
		ReflectionTestUtils.setField(job, "aiCallStartedAt", aiCallStartedAt);
		ReflectionTestUtils.setField(job, "failureReason", failureReason);
		return job;
	}

	private static Project project(long id, String name) {
		Project project = Project.create(name, null, null, null);
		ReflectionTestUtils.setField(project, "id", id);
		return project;
	}

	private static ProjectMemberAuditLog withCreatedAt(ProjectMemberAuditLog log, LocalDateTime createdAt) {
		ReflectionTestUtils.setField(log, "createdAt", createdAt);
		return log;
	}

	// --- overview ---

	/** 게스트는 본인이 만든 프로젝트의 멤버라 같은 경로로 본인 기준이 된다 - 속한 프로젝트가 없으면 아무것도 조회하지 않는다. */
	@Test
	void a_member_without_any_project_gets_empty_metrics_and_nothing_is_queried() {
		when(projectMemberRepository.findByMemberIdOrderByCreatedAtDesc(MEMBER_ID)).thenReturn(List.of());

		OpsOverviewResponse overview = service.overview(MEMBER_ID);

		assertThat(overview.projectCount()).isZero();
		assertThat(overview.queue().waiting()).isZero();
		assertThat(overview.queue().running()).isZero();
		assertThat(overview.generationTime().averageSeconds()).isNull();
		assertThat(overview.abstention().count()).isZero();
		assertThat(overview.abstention().ratePercent()).isNull();
		verifyNoInteractions(jobRepository, generationRejectionRepository);
		assertThat(service.events(MEMBER_ID, null)).isEmpty();
		verifyNoInteractions(auditLogRepository);
	}

	@Test
	void overview_only_queries_the_projects_the_member_belongs_to() {
		memberBelongsTo(10L, 20L);

		service.overview(MEMBER_ID);

		verify(jobRepository).countByProjectIdInAndJobTypeAndStatus(
				List.of(10L, 20L), JobType.CANDIDATE_GENERATION, JobStatus.PENDING);
		verify(generationRejectionRepository).countByProjectIdIn(List.of(10L, 20L));
	}

	@Test
	void overview_reports_queue_counts_average_generation_time_and_abstention_rate() {
		memberBelongsTo(10L);
		when(jobRepository.countByProjectIdInAndJobTypeAndStatus(any(), eq(JobType.CANDIDATE_GENERATION),
				eq(JobStatus.PENDING))).thenReturn(3L);
		when(jobRepository.countByProjectIdInAndJobTypeAndStatus(any(), eq(JobType.CANDIDATE_GENERATION),
				eq(JobStatus.RUNNING))).thenReturn(1L);
		when(jobRepository.countByProjectIdInAndJobTypeAndStatus(any(), eq(JobType.CANDIDATE_GENERATION),
				eq(JobStatus.SUCCEEDED))).thenReturn(6L);
		when(generationRejectionRepository.countByProjectIdIn(List.of(10L))).thenReturn(2L);
		// AI 호출 시작 → 완료: 60초, 120초 -> 평균 90초
		when(jobRepository.findRecentWithAiCallStarted(eq(List.of(10L)), eq(JobType.CANDIDATE_GENERATION),
				eq(JobStatus.SUCCEEDED), any(Pageable.class))).thenReturn(List.of(
						job(1L, 10L, JobType.CANDIDATE_GENERATION, JobStatus.SUCCEEDED, T0.plusSeconds(60), T0, null),
						job(2L, 10L, JobType.CANDIDATE_GENERATION, JobStatus.SUCCEEDED, T0.plusSeconds(120), T0, null)));

		OpsOverviewResponse overview = service.overview(MEMBER_ID);

		assertThat(overview.projectCount()).isEqualTo(1);
		assertThat(overview.queue().waiting()).isEqualTo(3);
		assertThat(overview.queue().running()).isEqualTo(1);
		assertThat(overview.generationTime().averageSeconds()).isEqualTo(90.0);
		assertThat(overview.generationTime().sampleSize()).isEqualTo(2);
		assertThat(overview.abstention().count()).isEqualTo(2);
		// 기권 2 / (성공 6 + 기권 2) = 25%
		assertThat(overview.abstention().ratePercent()).isEqualTo(25.0);
	}

	@Test
	void the_average_is_null_until_a_generation_has_completed_and_the_rate_is_null_until_one_has_finished() {
		memberBelongsTo(10L);
		when(jobRepository.findRecentWithAiCallStarted(any(), any(), any(), any(Pageable.class)))
				.thenReturn(List.of());

		OpsOverviewResponse overview = service.overview(MEMBER_ID);

		assertThat(overview.generationTime().averageSeconds()).isNull();
		assertThat(overview.generationTime().sampleSize()).isZero();
		assertThat(overview.abstention().ratePercent()).isNull();
	}

	/** 시계 오차 등으로 완료 시각이 호출 시작보다 앞서 보이는 표본은 평균을 깎지 않게 뺀다. */
	@Test
	void a_sample_that_ends_before_it_started_is_ignored() {
		memberBelongsTo(10L);
		when(jobRepository.findRecentWithAiCallStarted(any(), any(), any(), any(Pageable.class))).thenReturn(List.of(
				job(1L, 10L, JobType.CANDIDATE_GENERATION, JobStatus.SUCCEEDED, T0.plusSeconds(30), T0, null),
				job(2L, 10L, JobType.CANDIDATE_GENERATION, JobStatus.SUCCEEDED, T0.minusSeconds(5), T0, null)));

		OpsOverviewResponse overview = service.overview(MEMBER_ID);

		assertThat(overview.generationTime().averageSeconds()).isEqualTo(30.0);
		assertThat(overview.generationTime().sampleSize()).isEqualTo(1);
	}

	// --- events ---

	@Test
	void events_merge_jobs_and_member_changes_newest_first_with_screen_ready_labels() {
		memberBelongsTo(10L);
		when(jobRepository.findByProjectIdInOrderByUpdatedAtDesc(eq(List.of(10L)), any(Pageable.class)))
				.thenReturn(List.of(
						job(7L, 10L, JobType.CANDIDATE_GENERATION, JobStatus.FAILED, T0.plusMinutes(5), null,
								"AI_SERVICE_ERROR: 모델 수치 조건이 중복되었습니다. (LANGUAGE_DUPLICATE_CONSTRAINT)"),
						job(6L, 10L, JobType.EVIDENCE_REPORT, JobStatus.SUCCEEDED, T0, null, "무시될 사유")));
		when(auditLogRepository.findByProjectIdInOrderByCreatedAtDesc(eq(List.of(10L)), any(Pageable.class)))
				.thenReturn(List.of(withCreatedAt(
						ProjectMemberAuditLog.roleChanged(10L, 2L, MEMBER_ID, ProjectRole.PERFUMER,
								ProjectRole.PROJECT_MANAGER), T0.plusMinutes(3))));
		when(projectRepository.findAllById(any())).thenReturn(List.of(project(10L, "가을 향수")));

		List<OpsEventResponse> events = service.events(MEMBER_ID, null);

		assertThat(events).extracting(OpsEventResponse::event)
				.containsExactly("후보 조향식 생성", "팀원 역할 변경", "증거 보고서 생성");
		assertThat(events).extracting(OpsEventResponse::statusLabel).containsExactly("실패", "기록됨", "완료");

		OpsEventResponse failed = events.get(0);
		assertThat(failed.category()).isEqualTo("JOB");
		assertThat(failed.status()).isEqualTo("FAILED");
		assertThat(failed.detail()).contains("LANGUAGE_DUPLICATE_CONSTRAINT");
		assertThat(failed.projectName()).isEqualTo("가을 향수");
		assertThat(failed.referenceId()).isEqualTo(7L);

		OpsEventResponse member = events.get(1);
		assertThat(member.category()).isEqualTo("MEMBER");
		assertThat(member.status()).isEqualTo("RECORDED");
		assertThat(member.detail()).isEqualTo("PERFUMER → PROJECT_MANAGER");
		assertThat(member.occurredAt()).isEqualTo(T0.plusMinutes(3));

		// 실패한 작업이 아니면 사유를 내리지 않는다.
		assertThat(events.get(2).detail()).isNull();
	}

	@Test
	void events_are_trimmed_to_the_limit_after_merging_both_sources() {
		memberBelongsTo(10L);
		when(jobRepository.findByProjectIdInOrderByUpdatedAtDesc(any(), any(Pageable.class))).thenReturn(List.of(
				job(2L, 10L, JobType.CANDIDATE_GENERATION, JobStatus.RUNNING, T0.plusMinutes(9), null, null),
				job(1L, 10L, JobType.CANDIDATE_GENERATION, JobStatus.PENDING, T0.plusMinutes(1), null, null)));
		when(auditLogRepository.findByProjectIdInOrderByCreatedAtDesc(any(), any(Pageable.class)))
				.thenReturn(List.of(withCreatedAt(
						ProjectMemberAuditLog.added(10L, 2L, MEMBER_ID, ProjectRole.SUPPLIER), T0.plusMinutes(5))));

		List<OpsEventResponse> events = service.events(MEMBER_ID, 2);

		assertThat(events).extracting(OpsEventResponse::event).containsExactly("후보 조향식 생성", "팀원 추가");
		assertThat(events.get(1).detail()).isEqualTo("역할: SUPPLIER");
	}

	@Test
	void the_event_limit_defaults_to_20_and_is_clamped_between_1_and_100() {
		memberBelongsTo(10L);
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

		service.events(MEMBER_ID, null);
		service.events(MEMBER_ID, 0);
		service.events(MEMBER_ID, 1000);

		verify(jobRepository, org.mockito.Mockito.times(3))
				.findByProjectIdInOrderByUpdatedAtDesc(any(), pageable.capture());
		assertThat(pageable.getAllValues()).extracting(Pageable::getPageSize).containsExactly(20, 1, 100);
	}

	@Test
	void events_do_not_look_up_project_names_when_there_is_nothing_to_show() {
		memberBelongsTo(10L);

		assertThat(service.events(MEMBER_ID, null)).isEmpty();

		verify(projectRepository, never()).findAllById(any());
	}
}
