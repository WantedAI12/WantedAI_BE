package com.perfumeryaicore.global.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반을 나타내는 예외. {@link ErrorCode}에 상태·메시지를 위임한다.
 */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}
}
