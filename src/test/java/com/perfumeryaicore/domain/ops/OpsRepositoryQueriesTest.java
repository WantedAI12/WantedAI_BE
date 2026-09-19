package com.perfumeryaicore.domain.ops;

import static org.assertj.core.api.Assertions.assertThat;

import com.perfumeryaicore.domain.formula.entity.GenerationRejection;
import com.perfumeryaicore.domain.formula.repository.GenerationRejectionRepository;
import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.repository.JobRepository;
import com.perfumeryaicore.domain.project.entity.ProjectMemberAuditLog;
import com.perfumeryaicore.domain.project.repository.ProjectMemberAuditLogRepository;
import com.perfumeryaicore.global.common.ProjectRole;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서비스 운영 상태 화면용 새 쿼리가 실제 DB에서 프로젝트 범위·종류·상태 조건대로 걸러 주는지 검증한다.
 * 목 기반 테스트로는 JPQL과 파생 쿼리가 실제로 그렇게 동작하는지 알 수 없어서 스프링 컨텍스트로 돌린다
 * (테스트마다 롤백되어 다른 테스트의 데이터를 건드리지 않는다).
 */
@SpringBootTest
@Transactional
class OpsRepositoryQueriesTest {

	private static final long MINE = 900_001L;
	private static final long OTHERS = 900_002L;

	@Autowired
	private JobRepository jobRepository;
	@Autowired
	private GenerationRejectionRepository generationRejectionRepository;
	@Autowired
	private ProjectMemberAuditLogRepository auditLogRepository;

	private Job save(long projectId, JobType type, JobStatus status, LocalDateTime aiCallStartedAt) {
		Job job = Job.pending(projectId, type, 1L, null);
		ReflectionTestUtils.setField(job, "status", status);
		ReflectionTestUtils.setField(job, "aiCallStartedAt", aiCallStartedAt);
		return jobRepository.save(job);
	}

	@Test
	void job_counts_are_limited_to_the_given_projects_job_type_and_status() {
		save(MINE, JobType.CANDIDATE_GENERATION, JobStatus.PENDING, null);
		save(MINE, JobType.CANDIDATE_GENERATION, JobStatus.PENDING, null);
		save(MINE, JobType.CANDIDATE_GENERATION, JobStatus.RUNNING, null);
		save(MINE, JobType.EVIDENCE_REPORT, JobStatus.PENDING, null);
		save(OTHERS, JobType.CANDIDATE_GENERATION, JobStatus.PENDING, null);

		assertThat(jobRepository.countByProjectIdInAndJobTypeAndStatus(
				List.of(MINE), JobType.CANDIDATE_GENERATION, JobStatus.PENDING)).isEqualTo(2);
		assertThat(jobRepository.countByProjectIdInAndJobTypeAndStatus(
				List.of(MINE), JobType.CANDIDATE_GENERATION, JobStatus.RUNNING)).isEqualTo(1);
		assertThat(jobRepository.countByProjectIdInAndJobTypeAndStatus(
				List.of(MINE, OTHERS), JobType.CANDIDATE_GENERATION, JobStatus.PENDING)).isEqualTo(3);
	}

	@Test
	void the_average_sample_query_only_returns_completed_generations_whose_ai_call_started() {
		Job measured = save(MINE, JobType.CANDIDATE_GENERATION, JobStatus.SUCCEEDED, LocalDateTime.now().minusMinutes(2));
		save(MINE, JobType.CANDIDATE_GENERATION, JobStatus.SUCCEEDED, null);
		save(MINE, JobType.CANDIDATE_GENERATION, JobStatus.FAILED, LocalDateTime.now().minusMinutes(2));
		save(MINE, JobType.EVIDENCE_REPORT, JobStatus.SUCCEEDED, LocalDateTime.now().minusMinutes(2));
		save(OTHERS, JobType.CANDIDATE_GENERATION, JobStatus.SUCCEEDED, LocalDateTime.now().minusMinutes(2));

		List<Job> samples = jobRepository.findRecentWithAiCallStarted(
				List.of(MINE), JobType.CANDIDATE_GENERATION, JobStatus.SUCCEEDED, PageRequest.of(0, 50));

		assertThat(samples).extracting(Job::getId).containsExactly(measured.getId());
	}

	@Test
	void the_recent_job_history_is_limited_to_the_given_projects_and_the_page_size() {
		save(MINE, JobType.CANDIDATE_GENERATION, JobStatus.SUCCEEDED, null);
		save(MINE, JobType.EVIDENCE_REPORT, JobStatus.FAILED, null);
		save(MINE, JobType.PREDICTION, JobStatus.PENDING, null);
		save(OTHERS, JobType.CANDIDATE_GENERATION, JobStatus.SUCCEEDED, null);

		List<Job> recent = jobRepository.findByProjectIdInOrderByUpdatedAtDesc(List.of(MINE), PageRequest.of(0, 2));

		assertThat(recent).hasSize(2);
		assertThat(recent).extracting(Job::getProjectId).containsOnly(MINE);
	}

	@Test
	void abstentions_are_counted_only_for_the_given_projects() {
		generationRejectionRepository.save(GenerationRejection.of(1L, MINE, 11L, "no_safe_match", "기권", "{}", 1L));
		generationRejectionRepository.save(GenerationRejection.of(2L, MINE, 12L, "no_safe_match", "기권", "{}", 1L));
		generationRejectionRepository.save(GenerationRejection.of(3L, OTHERS, 13L, "no_safe_match", "기권", "{}", 2L));

		assertThat(generationRejectionRepository.countByProjectIdIn(List.of(MINE))).isEqualTo(2);
		assertThat(generationRejectionRepository.countByProjectIdIn(List.of(MINE, OTHERS))).isEqualTo(3);
	}

	@Test
	void member_changes_are_limited_to_the_given_projects_and_the_page_size() {
		auditLogRepository.save(ProjectMemberAuditLog.added(MINE, 2L, 1L, ProjectRole.SUPPLIER));
		auditLogRepository.save(ProjectMemberAuditLog.removed(MINE, 2L, 1L, ProjectRole.SUPPLIER));
		auditLogRepository.save(ProjectMemberAuditLog.added(OTHERS, 3L, 9L, ProjectRole.AUDITOR));

		List<ProjectMemberAuditLog> logs =
				auditLogRepository.findByProjectIdInOrderByCreatedAtDesc(List.of(MINE), PageRequest.of(0, 10));

		assertThat(logs).hasSize(2);
		assertThat(logs).extracting(ProjectMemberAuditLog::getProjectId).containsOnly(MINE);
	}
}
