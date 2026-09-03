package com.perfumeryaicore.domain.job.repository;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {

	List<Job> findByStatusOrderByCreatedAtAsc(JobStatus status);
}
