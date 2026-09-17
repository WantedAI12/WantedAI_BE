package com.perfumeryaicore.domain.job.service;

import com.perfumeryaicore.domain.job.dto.response.JobResponse;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 작업 상태 변화를 SSE로 구독자에게 밀어준다(AI 개발팀 제안, 2026-09-17) - 화면이 계속 폴링하지
 * 않아도 되고, 연결이 끊겼다 재접속해도 구독 시점의 최신 상태를 즉시 받는다.
 *
 * <p>단일 인스턴스 배포를 전제로 메모리에만 구독자를 둔다(작업 상태 자체는 DB가 진실이라 이
 * 목록을 잃어도 폴링으로 항상 복구 가능 - {@code GET /jobs/{jobId}}는 그대로 남겨둔다).
 * FAILED는 재시도로 같은 작업 ID가 다시 PENDING으로 돌아갈 수 있어({@link JobStatus#isTerminal()}
 * 기준과 다름) 이 스트림에서는 SUCCEEDED/CANCELLED/FAILED 모두 종료 신호로 본다 - 재시도 후
 * 진행 상황을 계속 보려면 다시 구독해야 한다.
 */
@Slf4j
@Component
public class JobEventBroadcaster {

	private static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(30);
	private static final String EVENT_NAME = "job-update";

	private final Map<Long, List<SseEmitter>> emittersByJobId = new ConcurrentHashMap<>();

	/** 구독 시작. 현재 상태를 즉시 한 번 보내고, 이미 끝난 작업이면 그대로 스트림을 닫는다. */
	public SseEmitter subscribe(JobResponse current) {
		SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
		Long jobId = current.jobId();
		emittersByJobId.computeIfAbsent(jobId, key -> new CopyOnWriteArrayList<>()).add(emitter);
		emitter.onCompletion(() -> unregister(jobId, emitter));
		emitter.onTimeout(() -> unregister(jobId, emitter));
		emitter.onError(e -> unregister(jobId, emitter));

		send(emitter, jobId, current);
		if (isStreamEnd(current.status())) {
			emitter.complete();
		}
		return emitter;
	}

	/** 상태가 바뀔 때마다 호출한다(트랜잭션 커밋 이후에만 - {@link JobService}가 보장). */
	public void publish(JobResponse updated) {
		List<SseEmitter> emitters = emittersByJobId.get(updated.jobId());
		if (emitters == null || emitters.isEmpty()) {
			return;
		}
		boolean streamEnd = isStreamEnd(updated.status());
		for (SseEmitter emitter : List.copyOf(emitters)) {
			send(emitter, updated.jobId(), updated);
			if (streamEnd) {
				emitter.complete();
			}
		}
	}

	private void send(SseEmitter emitter, Long jobId, JobResponse response) {
		try {
			emitter.send(SseEmitter.event().name(EVENT_NAME).data(response));
		} catch (IOException | IllegalStateException e) {
			log.info("[JOB-STREAM] job={} emitter send failed, dropping subscriber: {}", jobId, e.getMessage());
			emitter.completeWithError(e);
		}
	}

	private boolean isStreamEnd(JobStatus status) {
		return status == JobStatus.SUCCEEDED || status == JobStatus.FAILED || status == JobStatus.CANCELLED;
	}

	private void unregister(Long jobId, SseEmitter emitter) {
		emittersByJobId.computeIfPresent(jobId, (key, emitters) -> {
			emitters.remove(emitter);
			return emitters.isEmpty() ? null : emitters;
		});
	}
}
