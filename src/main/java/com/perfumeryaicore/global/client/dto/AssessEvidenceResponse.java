package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code POST /v2/formulas/assess-evidence} 응답. {@code status}/{@code gatePassed}/
 * {@code blockers}가 실제 판정 결과다. 프레임워크별 상세 검사 결과({@code frameworkChecks}),
 * 공개 자료 스크린 원문({@code publicSourceScreen}) 등은 원문 노드로 보존한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssessEvidenceResponse(

		@JsonProperty("schema_version")
		String schemaVersion,

		@JsonProperty("snapshot_version")
		String snapshotVersion,

		@JsonProperty("evaluated_on")
		String evaluatedOn,

		@JsonProperty("input_id")
		String inputId,

		@JsonProperty("evidence_contract")
		JsonNode evidenceContract,

		@JsonProperty("required_frameworks")
		JsonNode requiredFrameworks,

		String status,

		@JsonProperty("gate_passed")
		Boolean gatePassed,

		List<EvidenceBlocker> blockers,

		JsonNode materials,

		@JsonProperty("quoted_formula_cost_usd_per_kg")
		Double quotedFormulaCostUsdPerKg,

		@JsonProperty("purchase_cost_usd")
		Double purchaseCostUsd,

		@JsonProperty("manufacturing_approval")
		Boolean manufacturingApproval,

		@JsonProperty("framework_checks")
		JsonNode frameworkChecks,

		String scope,

		@JsonProperty("public_source_screen")
		JsonNode publicSourceScreen,

		@JsonProperty("result_id")
		String resultId
) {

	/** 근거 부족 등으로 차단됐는지. {@code gatePassed}가 false여도 blockers가 없을 수 있어 함께 본다. */
	public boolean isBlocked() {
		return Boolean.FALSE.equals(gatePassed) && blockers != null && !blockers.isEmpty();
	}
}
