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
	PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");

	private final HttpStatus status;
	private final String message;
}
