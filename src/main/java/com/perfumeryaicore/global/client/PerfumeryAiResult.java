package com.perfumeryaicore.global.client;

import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse;

/**
 * 조향 AI 호출 결과. 원본 응답 전체(재현·감사용)와 파싱된 뷰(검증·화면용)를 분리해 담는다.
 *
 * @param rawJson  Modal 응답 본문 원문. 인증 헤더 등 내부 요청 정보는 포함하지 않는다.
 * @param parsed   백엔드가 사용하는 필드만 매핑한 뷰
 * @param latencyMillis Modal 호출 왕복 시간(ms). 근거(Evidence)로 후보 버전에 함께 저장한다.
 */
public record PerfumeryAiResult(
		String rawJson,
		FormulaGenerationResponse parsed,
		long latencyMillis
) {
}
