package com.perfumeryaicore.global.exception;

import com.perfumeryaicore.global.response.ApiResponse;
import com.perfumeryaicore.global.response.ApiResponse.FieldErrorDetail;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
