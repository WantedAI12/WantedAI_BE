package com.perfumeryaicore.domain.job;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.perfumeryaicore.domain.job.dto.response.JobResponse;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.service.JobEventBroadcaster;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 개발팀 제안(2026-09-17): 작업 상태 변화를 SSE로 구독자에게 밀어주는 브로드캐스터.
 *
 * <p>{@code onCompletion} 콜백은 실제 서블릿 비동기 요청 안에서만 호출되므로(순수 단위 테스트에서는
 * 트리거되지 않는다), 대신 완료된 {@link SseEmitter}에 {@code send()}를 호출하면 항상
 * {@code IllegalStateException}이 나는 Spring의 문서화된 동작으로 완료 여부를 검증한다.
 */
class JobEventBroadcasterTest {

	private final JobEventBroadcaster broadcaster = new JobEventBroadcaster();

	private static JobResponse response(long jobId, JobStatus status) {
		return new JobResponse(jobId, JobType.CANDIDATE_GENERATION, status, status == JobStatus.FAILED, null,
				status == JobStatus.SUCCEEDED ? 42L : null, LocalDateTime.now(), LocalDateTime.now());
	}

	@Test
	void subscribing_to_a_still_running_job_keeps_the_stream_open() {
		SseEmitter emitter = broadcaster.subscribe(response(1L, JobStatus.RUNNING));

		assertThatCode(() -> emitter.send("still-open")).doesNotThrowAnyException();
	}

	@Test
	void subscribing_to_an_already_finished_job_closes_the_stream_right_away() {
		SseEmitter emitter = broadcaster.subscribe(response(2L, JobStatus.SUCCEEDED));

		assertThatThrownBy(() -> emitter.send("too-late")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void publishing_a_terminal_status_closes_the_previously_open_stream() {
		SseEmitter emitter = broadcaster.subscribe(response(3L, JobStatus.RUNNING));

		broadcaster.publish(response(3L, JobStatus.SUCCEEDED));

		assertThatThrownBy(() -> emitter.send("too-late")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void publishing_a_non_terminal_status_keeps_the_stream_open_for_more_updates() {
		SseEmitter emitter = broadcaster.subscribe(response(5L, JobStatus.PENDING));

		broadcaster.publish(response(5L, JobStatus.RUNNING));

		assertThatCode(() -> emitter.send("still-open")).doesNotThrowAnyException();
	}

	@Test
	void publishing_for_a_job_with_no_subscribers_does_not_throw() {
		broadcaster.publish(response(999L, JobStatus.SUCCEEDED));
	}

	/** FAILED는 재시도로 같은 작업이 다시 PENDING이 될 수 있지만, 이 스트림은 그래도 끝난다. */
	@Test
	void a_failed_job_also_closes_the_stream_even_though_it_can_still_be_retried() {
		SseEmitter emitter = broadcaster.subscribe(response(4L, JobStatus.RUNNING));

		broadcaster.publish(response(4L, JobStatus.FAILED));

		assertThatThrownBy(() -> emitter.send("too-late")).isInstanceOf(IllegalStateException.class);
	}
}
