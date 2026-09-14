package com.perfumeryaicore.global.exception;

import com.perfumeryaicore.global.response.ApiResponse;
import com.perfumeryaicore.global.response.ApiResponse.FieldErrorDetail;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 전역 예외 핸들러. 모든 오류 응답을 공통 포맷({@link ApiResponse})으로 변환한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
		ErrorCode code = e.getErrorCode();
		log.warn("BusinessException: {} - {}", code.name(), e.getMessage());
		return ResponseEntity.status(code.getStatus())
				.body(ApiResponse.error(code.name(), e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		List<FieldErrorDetail> details = e.getBindingResult().getFieldErrors().stream()
				.map(GlobalExceptionHandler::toDetail)
				.toList();
		return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
				.body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.name(),
						ErrorCode.VALIDATION_FAILED.getMessage(), details));
	}

	/** 낙관적 잠금 충돌(예: Job 동시 선점/재시도/취소 경합, BE-043). */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException e) {
		log.info("Optimistic locking conflict: {}", e.getMessage());
		ErrorCode code = ErrorCode.CONCURRENT_MODIFICATION;
		return ResponseEntity.status(code.getStatus())
				.body(ApiResponse.error(code.name(), code.getMessage()));
	}

	/** 작업 큐 포화(BE-047). 호출자 스레드가 대신 실행하지 않고 즉시 503으로 응답한다. */
	@ExceptionHandler(RejectedExecutionException.class)
	public ResponseEntity<ApiResponse<Void>> handleQueueSaturated(RejectedExecutionException e) {
		log.warn("Job queue saturated: {}", e.getMessage());
		ErrorCode code = ErrorCode.JOB_QUEUE_SATURATED;
		return ResponseEntity.status(code.getStatus())
				.body(ApiResponse.error(code.name(), code.getMessage()));
	}

	/** 잘못된 쿼리·경로 파라미터, 그리고 깨진 JSON 본문(BE-084) — 모두 사용자 입력 오류다. */
	@ExceptionHandler({MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class,
			HttpMessageNotReadableException.class})
	public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
		log.warn("Bad request parameter: {}", e.getMessage());
		return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
				.body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.name(),
						ErrorCode.VALIDATION_FAILED.getMessage()));
	}

	/** DB 무결성 제약 충돌(BE-084) — 예: 애플리케이션 검증을 통과한 뒤 동시 요청이 유니크 제약을 깬 경우. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
		log.warn("Data integrity violation: {}", e.getMessage());
		ErrorCode code = ErrorCode.DATA_INTEGRITY_VIOLATION;
		return ResponseEntity.status(code.getStatus())
				.body(ApiResponse.error(code.name(), code.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
		log.error("Unhandled exception", e);
		return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
				.body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.name(),
						ErrorCode.INTERNAL_ERROR.getMessage()));
	}

	private static FieldErrorDetail toDetail(FieldError fieldError) {
		return new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage());
	}
}
