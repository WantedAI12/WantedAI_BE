package com.perfumeryaicore.domain.job.repository;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {

	List<Job> findByStatusOrderByCreatedAtAsc(JobStatus status);

	List<Job> findByProjectId(Long projectId);

	/** BE-046: 중복 제출 방지 조회. */
	Optional<Job> findByCreatedByAndJobTypeAndIdempotencyKey(Long createdBy, JobType jobType, String idempotencyKey);
}
