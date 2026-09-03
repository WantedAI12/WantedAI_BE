package com.perfumeryaicore.domain.job.service;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.dto.response.JobResponse;
import com.perfumeryaicore.domain.job.repository.JobRepository;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비동기 작업 생성·조회·재시도·취소.
 *
 * <p>작업 <em>본문</em> 실행은 {@link JobExecutor}가, 종류별 재시도 재구성은 도메인이 등록한
 * {@link JobRetryHandler}가 맡는다. 이 서비스는 수명주기와 접근 제어만 책임진다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class JobService {

	private final JobRepository jobRepository;
	private final Map<JobType, JobRetryHandler> retryHandlers = new EnumMap<>(JobType.class);

	public JobService(JobRepository jobRepository, List<JobRetryHandler> handlers) {
		this.jobRepository = jobRepository;
		for (JobRetryHandler handler : handlers) {
			JobRetryHandler previous = retryHandlers.put(handler.supportedType(), handler);
			if (previous != null) {
				throw new IllegalStateException(
						"JobRetryHandler 중복 등록: " + handler.supportedType());
			}
		}
	}

	/**
	 * 새 작업을 대기 상태로 만든다. 도메인 서비스는 반환된 작업 ID로 {@link JobExecutor#execute}를 호출한다.
	 *
	 * @param inputPayload 재시도 시 요청 재구성을 위한 입력 JSON (없으면 {@code null})
	 */
	@Transactional
	public Job enqueue(Long projectId, JobType jobType, Long memberId, String inputPayload) {
		Job job = jobRepository.save(Job.pending(projectId, jobType, memberId, inputPayload));
		log.info("[JOB] id={} type={} PENDING project={} by={}", job.getId(), jobType, projectId, memberId);
		return job;
	}

	public JobResponse get(Long jobId, Long memberId) {
		return JobResponse.from(getAccessibleJob(jobId, memberId));
	}

	@Transactional
	public void retry(Long jobId, Long memberId) {
		Job job = getAccessibleJob(jobId, memberId);
		JobRetryHandler handler = retryHandlers.get(job.getJobType());
		if (handler == null) {
			throw new BusinessException(ErrorCode.JOB_RETRY_NOT_SUPPORTED);
		}
		job.resetForRetry();
		handler.redispatch(job);
		log.info("[JOB] id={} type={} retry dispatched", jobId, job.getJobType());
	}

	@Transactional
	public JobResponse cancel(Long jobId, Long memberId) {
		Job job = getAccessibleJob(jobId, memberId);
		job.cancel();
		log.info("[JOB] id={} type={} CANCELLED by={}", jobId, job.getJobType(), memberId);
		return JobResponse.from(job);
	}

	/**
	 * 작업 생성 주체만 접근 허용. project 도메인 구현 시 프로젝트 멤버 접근을 추가한다.
	 */
	private Job getAccessibleJob(Long jobId, Long memberId) {
		Job job = jobRepository.findById(jobId)
				.orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
		if (!job.isOwnedBy(memberId)) {
			// TODO(project): 같은 프로젝트 멤버도 조회 가능하도록 확장
			throw new BusinessException(ErrorCode.JOB_ACCESS_DENIED);
		}
		return job;
	}
}
