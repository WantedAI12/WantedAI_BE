package com.perfumeryaicore.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 도메인 공통 에러 코드. API 명세서 부록의 코드 체계를 따른다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 공통
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 유효하지 않습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

	// 인증/회원
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
	REFRESH_TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED,
			"이미 폐기된 Refresh Token이 재사용되어 해당 계정의 모든 세션을 종료했습니다."),
	EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
	PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),

	// 자연어 향 요청(Request)
	REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "향 요청을 찾을 수 없습니다."),
	REQUEST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 향 요청에 접근할 권한이 없습니다."),
	REQUEST_EDIT_NOT_ALLOWED(HttpStatus.CONFLICT, "이미 확정된 요청은 수정할 수 없습니다."),
	REQUEST_NOT_CONFIRMABLE(HttpStatus.CONFLICT, "핵심 정보가 누락되거나 확정할 수 없는 상태입니다."),
	REQUEST_NOT_CONFIRMED(HttpStatus.CONFLICT, "확정되지 않은 요청으로는 후보를 생성할 수 없습니다."),

	// 비동기 작업(Job)
	JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다."),
	JOB_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 작업에 접근할 권한이 없습니다."),
	JOB_NOT_RETRYABLE(HttpStatus.CONFLICT, "재시도할 수 없는 작업입니다."),
	JOB_NOT_CANCELLABLE(HttpStatus.CONFLICT, "이미 종료되어 취소할 수 없는 작업입니다."),
	JOB_RETRY_NOT_SUPPORTED(HttpStatus.NOT_IMPLEMENTED, "이 작업 종류는 아직 재시도를 지원하지 않습니다."),
	JOB_ILLEGAL_STATE(HttpStatus.CONFLICT, "작업 상태 전이가 올바르지 않습니다."),

	// 조향 AI(Modal) 연동
	AI_AUTH_MISCONFIGURED(HttpStatus.INTERNAL_SERVER_ERROR, "조향 AI 인증 설정 오류로 서비스에 연결할 수 없습니다."),
	AI_SERVICE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "조향 AI 응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요."),
	AI_RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "조향 AI 호출이 일시적으로 많습니다. 잠시 후 다시 시도해 주세요."),
	AI_SERVICE_ERROR(HttpStatus.BAD_GATEWAY, "조향 AI 처리 중 오류가 발생했습니다."),
	AI_SCHEMA_VERSION_MISMATCH(HttpStatus.BAD_GATEWAY, "조향 AI 응답 형식이 예상과 달라 결과를 저장하지 않았습니다.");

	private final HttpStatus status;
	private final String message;
}
