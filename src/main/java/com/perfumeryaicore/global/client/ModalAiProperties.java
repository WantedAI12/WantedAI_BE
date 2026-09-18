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
 * @param responseTimeout   전체 응답 대기 시간 (AI 개발팀 확인: 추론 큐 대기 110초 + Modal 함수 타임아웃 300초, 기본 305초)
 * @param requestsPerMinute 분당 호출 상한 (컨테이너 기준 30회)
 * @param maxRetries        일시 오류(429/5xx/타임아웃) 시 최대 재시도 횟수
 * @param krwPerUsd         원/달러 환율 근사치. 화면·사용자 입력은 원화 기준이지만 Modal
 *                          {@code FormulaRequest} 스키마의 가격 필드(예: {@code max_ingredient_price_per_kg})는
 *                          USD/kg 기준이라, 보내기 전 이 값으로 나눠 변환한다(2026-09-18 확인).
 *                          실시간 환율 연동이 아니므로 주기적으로 갱신이 필요하다.
 */
@ConfigurationProperties(prefix = "ai.modal")
public record ModalAiProperties(
		String baseUrl,
		String authToken,
		Duration connectTimeout,
		Duration responseTimeout,
		int requestsPerMinute,
		int maxRetries,
		double krwPerUsd
) {

	public boolean hasAuthToken() {
		return authToken != null && !authToken.isBlank();
	}
}
