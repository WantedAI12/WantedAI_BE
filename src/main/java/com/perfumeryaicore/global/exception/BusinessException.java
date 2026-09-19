package com.perfumeryaicore.global.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반을 나타내는 예외. {@link ErrorCode}에 상태·메시지를 위임한다.
 */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	/**
	 * 이 예외가 (Job 재시도 같은) 호출부에게 재시도해도 되는지를 명시적으로 알려줄 때만 채운다.
	 * {@code null}이면 호출부가 오류 코드 기준의 기본 판단을 쓴다는 뜻이다 - 오류 코드 하나가
	 * 재시도 가능한 경우와 불가능한 경우를 모두 포함할 수 있어(BE-108, 예: AI_SERVICE_ERROR가
	 * 5xx 일시 오류에도, 4xx 입력 검증 실패에도 쓰임), 코드만으로는 구분할 수 없을 때 필요하다.
	 */
	private final Boolean retryable;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.retryable = null;
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
		this.retryable = null;
	}

	public BusinessException(ErrorCode errorCode, boolean retryable) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.retryable = retryable;
	}

	public BusinessException(ErrorCode errorCode, String message, boolean retryable) {
		super(message);
		this.errorCode = errorCode;
		this.retryable = retryable;
	}
}
