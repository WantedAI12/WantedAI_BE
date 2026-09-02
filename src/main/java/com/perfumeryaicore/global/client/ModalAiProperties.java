package com.perfumeryaicore.global.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 조향 AI(Modal) 연동 설정. {@code ai.modal.*} 프로퍼티에 바인딩된다.
 *
 * <p>인증 토큰은 소스코드·저장소·프론트엔드·브라우저·오류 메시지에 넣지 않고
 * 백엔드 환경변수({@code AI_MODAL_AUTH_TOKEN}) 또는 Secret Manager로만 주입한다.
 *
 * @param baseUrl           Modal 배포 기본 주소
 * @param authToken         Modal Proxy Token 전체 문자열({@code wk-<id>.ws-<secret>}). 비어 있으면 호출 시 설정 오류로 처리
 * @param connectTimeout    연결 제한 시간 (일반 웹 요청 수준, 약 10초)
 * @param responseTimeout   전체 응답 대기 시간 (Modal 콜드 스타트 + 조향식 계산 고려, 최소 130초)
 * @param requestsPerMinute 분당 호출 상한 (컨테이너 기준 30회)
 * @param maxRetries        일시 오류(429/5xx/타임아웃) 시 최대 재시도 횟수
 */
@ConfigurationProperties(prefix = "ai.modal")
public record ModalAiProperties(
		String baseUrl,
		String authToken,
		Duration connectTimeout,
		Duration responseTimeout,
		int requestsPerMinute,
		int maxRetries
) {

	public boolean hasAuthToken() {
		return authToken != null && !authToken.isBlank();
	}
}
