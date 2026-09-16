package com.perfumeryaicore.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.perfumeryaicore.global.response.ApiResponse;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

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

	/** BE-084: 깨진 JSON 본문(HttpMessageNotReadableException)은 500이 아니라 400으로 응답해야 한다. */
	@Test
	void broken_json_body_maps_to_400_bad_request() {
		ResponseEntity<ApiResponse<Void>> response =
				handler.handleBadRequest(new HttpMessageNotReadableException("broken json", (org.springframework.http.HttpInputMessage) null));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.VALIDATION_FAILED.name());
	}

	/** BE-084: DB 무결성 제약 충돌은 500이 아니라 409로 응답해야 한다. */
	@Test
	void data_integrity_violation_maps_to_409_conflict() {
		ResponseEntity<ApiResponse<Void>> response =
				handler.handleDataIntegrityViolation(new DataIntegrityViolationException("unique constraint"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.DATA_INTEGRITY_VIOLATION.name());
	}
}
