package com.perfumeryaicore.domain.job.repository;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, Long> {

	List<Job> findByStatusOrderByCreatedAtAsc(JobStatus status);

	List<Job> findByProjectId(Long projectId);

	/** BE-046: 중복 제출 방지 조회. */
	Optional<Job> findByCreatedByAndJobTypeAndIdempotencyKey(Long createdBy, JobType jobType, String idempotencyKey);

	/** 서비스 운영 상태: 프로젝트들 안의 특정 종류·상태 작업 수. */
	long countByProjectIdInAndJobTypeAndStatus(Collection<Long> projectIds, JobType jobType, JobStatus status);

	/**
	 * 서비스 운영 상태: AI 호출이 실제로 시작된 최근 작업(최신 완료순) - 평균 생성 시간 계산용이라
	 * {@code aiCallStartedAt}이 없는(호출 전에 끝난) 작업은 제외한다.
	 */
	@Query("select j from Job j where j.projectId in :projectIds and j.jobType = :jobType "
			+ "and j.status = :status and j.aiCallStartedAt is not null order by j.updatedAt desc")
	List<Job> findRecentWithAiCallStarted(@Param("projectIds") Collection<Long> projectIds,
			@Param("jobType") JobType jobType, @Param("status") JobStatus status, Pageable pageable);

	/** 서비스 운영 상태: 프로젝트들의 최근 작업 이력(최근 상태 변경순). */
	List<Job> findByProjectIdInOrderByUpdatedAtDesc(Collection<Long> projectIds, Pageable pageable);
}
