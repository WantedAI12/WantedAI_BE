package com.perfumeryaicore.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 작업 실행기 설정.
 *
 * <p>실제 Modal 호출은 {@code PerfumeryAiClient}가 세마포어로 동시 1개로 직렬화하므로
 * 이 풀은 작게 유지한다. 풀의 역할은 트리거 요청 스레드를 즉시 반환시키고, 대기 중인
 * 작업을 순서대로 넘겨주는 것이다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean("jobTaskExecutor")
	public Executor jobTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("job-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		executor.initialize();
		return executor;
	}
}
