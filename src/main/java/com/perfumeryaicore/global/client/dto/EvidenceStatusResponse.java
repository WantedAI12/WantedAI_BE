package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code GET /v2/evidence/status} 응답. 공개 자료 연결과 운영자 증거 묶음 등록 상태 - 현재
 * 배합의 적합 판정이 아니다(README 참고). {@code contract}는 프레임워크별 매칭 통계 등 깊은
 * 구조라 원문 노드로 보존한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvidenceStatusResponse(

		@JsonProperty("schema_version")
		String schemaVersion,

		JsonNode contract,

		@JsonProperty("operator_bundle_registered")
		Boolean operatorBundleRegistered,

		@JsonProperty("public_sources_registered")
		Boolean publicSourcesRegistered,

		@JsonProperty("public_source_diagnostics_available")
		Boolean publicSourceDiagnosticsAvailable,

		@JsonProperty("default_operational_gate_bypassed")
		Boolean defaultOperationalGateBypassed,

		JsonNode coverage
) {
}
