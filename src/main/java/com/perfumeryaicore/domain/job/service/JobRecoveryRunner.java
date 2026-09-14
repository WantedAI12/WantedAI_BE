package com.perfumeryaicore.domain.job.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 앱 기동 완료 직후 1회, 재시작 전에 남아있던 PENDING·고아 RUNNING 작업을 복구한다(BE-044).
 *
 * <p>{@link ApplicationReadyEvent}를 쓰는 이유: {@link JobService#setRetryHandlers}가 세터 주입으로
 * {@code JobRetryHandler} 목록을 받는데, 모든 빈이 완전히 초기화된 뒤에 실행돼야 복구 대상 작업을
 * 실제로 다시 dispatch할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobRecoveryRunner {

	private final JobService jobService;

	@EventListener(ApplicationReadyEvent.class)
	public void recoverJobsAfterRestart() {
		log.info("[JOB] startup recovery: scanning for leftover PENDING/RUNNING jobs");
		jobService.recoverAfterRestart();
	}
}
