package com.perfumeryaicore.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 공통 응답 포맷.
 *
 * <pre>
 * 성공: { "success": true, "data": { ... } }
 * 실패: { "success": false, "error": { "code": "...", "message": "...", "details": [ ... ] } }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<Void> error(String code, String message, List<FieldErrorDetail> details) {
		return new ApiResponse<>(false, null, new ErrorBody(code, message, details));
	}

	public static ApiResponse<Void> error(String code, String message) {
		return error(code, message, null);
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ErrorBody(String code, String message, List<FieldErrorDetail> details) {
	}

	public record FieldErrorDetail(String field, String reason) {
	}
}
