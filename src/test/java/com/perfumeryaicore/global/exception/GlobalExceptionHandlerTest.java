package com.perfumeryaicore.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.perfumeryaicore.global.response.ApiResponse;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	/** BE-047: 작업 큐 포화(RejectedExecutionException)는 500이 아니라 503으로 응답해야 한다. */
	@Test
	void queue_saturation_maps_to_503_service_unavailable() {
		ResponseEntity<ApiResponse<Void>> response =
				handler.handleQueueSaturated(new RejectedExecutionException("pool saturated"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.JOB_QUEUE_SATURATED.name());
	}
}
