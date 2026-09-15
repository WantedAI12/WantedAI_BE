package com.perfumeryaicore.domain.request.service;

import com.perfumeryaicore.domain.request.dto.request.BriefClarifyRequest;
import com.perfumeryaicore.domain.request.dto.request.BriefReviewRequest;
import com.perfumeryaicore.domain.request.dto.request.EvaluateDiagnosticRequest;
import com.perfumeryaicore.domain.request.dto.request.ReassessDiagnosticRequest;
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.global.client.PerfumeryAiClient;
import com.perfumeryaicore.global.client.dto.ClarifyBriefRequest;
import com.perfumeryaicore.global.client.dto.ClarifyBriefResponse;
import com.perfumeryaicore.global.client.dto.EvaluateFormulaRequest;
import com.perfumeryaicore.global.client.dto.EvaluationResponse;
import com.perfumeryaicore.global.client.dto.PrepareBriefRequest;
import com.perfumeryaicore.global.client.dto.PrepareBriefResponse;
import com.perfumeryaicore.global.client.dto.ReassessFormulaRequest;
import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.TargetRegion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * v2 연동 답변(2026-09-15/16) 1~2단계: 자연어 향 요청을 AI가 구조화 검토하고, 부족한 값은 보완
 * 질문으로 돌려준다({@code /v2/briefs/prepare}, {@code /v2/briefs/clarify}). {@link FragranceRequestService}의
 * 기존 규칙 기반 정규화(외부 AI 미호출)는 그대로 두고, 이건 그 위에 추가되는 별도의 AI 기반
 * 사전 검토 단계다 - confirm() 이전 어느 시점에도 호출할 수 있다.
 *
 * <p>등록된 규제·공급 근거가 없으면 일반 evaluate/reassess는 422/abstained로 거부된다. 대신
 * {@code diagnosticOnly=true}로 진단 전용 계산을 시도할 수 있다(AI 개발팀 확인) -
 * {@link #evaluateDiagnostic}/{@link #reassessDiagnostic} 참고. 진단 결과는 정식 승인 후보가
 * 아니므로 별도 엔티티로 저장하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BriefReviewService {

	private final FragranceRequestService requestService;
	private final PerfumeryAiClient perfumeryAiClient;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public PrepareBriefResponse prepare(Long requestId, Long memberId, BriefReviewRequest dto) {
		FragranceRequest request = requestService.getAccessibleRequest(requestId, memberId);
		JsonNode requestNode = buildRequestNode(request);
		JsonNode evidencePolicy = buildEvidencePolicy(
				dto.finishedBatchMassG(), dto.maximumLeadTimeDays(), dto.maximumPurchaseCostUsd());

		var result = perfumeryAiClient.prepareBrief(
				PrepareBriefRequest.of(requestNode, evidencePolicy, dto.isDiagnosticOnly()),
				"request-" + requestId, null);
		log.info("[BRIEF-REVIEW] request={} status={} reviewId={} diagnosticOnly={} by={}",
				requestId, result.parsed().status(), result.parsed().reviewId(), dto.isDiagnosticOnly(), memberId);
		return result.parsed();
	}

	public ClarifyBriefResponse clarify(Long requestId, Long memberId, BriefClarifyRequest dto) {
		FragranceRequest request = requestService.getAccessibleRequest(requestId, memberId);
		JsonNode requestNode = buildRequestNode(request);
		JsonNode evidencePolicy = buildEvidencePolicy(
				dto.finishedBatchMassG(), dto.maximumLeadTimeDays(), dto.maximumPurchaseCostUsd());
		JsonNode answers = jsonMapper.valueToTree(dto.answers());

		var result = perfumeryAiClient.clarifyBrief(
				ClarifyBriefRequest.of(
						requestNode, evidencePolicy, dto.preparedResultId(), answers, dto.isDiagnosticOnly()),
				"request-" + requestId, null);
		log.info("[BRIEF-REVIEW] request={} clarified previousResultId={} diagnosticOnly={} by={}",
				requestId, result.parsed().previousResultId(), dto.isDiagnosticOnly(), memberId);
		return result.parsed();
	}

	/**
	 * 등록된 규제·공급 근거 없이도 시도할 수 있는 진단 전용 확정(AI 개발팀 확인, 2026-09-16).
	 * {@code confirmedReviewId}는 반드시 {@link #prepare}를 {@code diagnosticOnly=true}로 호출해
	 * 받은 {@code review_id}여야 한다 - 일반 모드 review_id를 재사용하면 409가 난다.
	 *
	 * <p>결과는 정식 승인 후보가 아니므로 {@link com.perfumeryaicore.domain.formula.entity.Candidate}로
	 * 저장하지 않고 그대로 반환한다 - {@link EvaluationResponse#isDiagnostic()}과
	 * {@code diagnosticCandidates[].recommendationAllowed}를 호출부(화면)가 반드시 확인해야 한다.
	 */
	public EvaluationResponse evaluateDiagnostic(Long requestId, Long memberId, EvaluateDiagnosticRequest dto) {
		FragranceRequest request = requestService.getAccessibleRequest(requestId, memberId);
		JsonNode requestNode = buildRequestNode(request);
		JsonNode evidencePolicy = buildEvidencePolicy(
				dto.finishedBatchMassG(), dto.maximumLeadTimeDays(), dto.maximumPurchaseCostUsd());

		var result = perfumeryAiClient.evaluateFormula(
				EvaluateFormulaRequest.diagnostic(requestNode, evidencePolicy, dto.confirmedReviewId()),
				"request-" + requestId, null);
		log.info("[BRIEF-REVIEW] request={} diagnostic evaluate reviewId={} status={} candidates={} by={}",
				requestId, dto.confirmedReviewId(), result.parsed().status(),
				result.parsed().diagnosticCandidates() == null ? 0 : result.parsed().diagnosticCandidates().size(),
				memberId);
		return result.parsed();
	}

	/** {@link #evaluateDiagnostic}와 같은 제약, 고정 배합({@code lines})을 유지한 채 재평가한다. */
	public EvaluationResponse reassessDiagnostic(Long requestId, Long memberId, ReassessDiagnosticRequest dto) {
		FragranceRequest request = requestService.getAccessibleRequest(requestId, memberId);
		JsonNode requestNode = buildRequestNode(request);
		JsonNode evidencePolicy = buildEvidencePolicy(
				dto.finishedBatchMassG(), dto.maximumLeadTimeDays(), dto.maximumPurchaseCostUsd());

		var result = perfumeryAiClient.reassessFormula(
				ReassessFormulaRequest.diagnostic(requestNode, evidencePolicy, dto.lines(), dto.confirmedReviewId()),
				"request-" + requestId, null);
		log.info("[BRIEF-REVIEW] request={} diagnostic reassess reviewId={} status={} candidates={} by={}",
				requestId, dto.confirmedReviewId(), result.parsed().status(),
				result.parsed().diagnosticCandidates() == null ? 0 : result.parsed().diagnosticCandidates().size(),
				memberId);
		return result.parsed();
	}

	/** BE 저장 요청을 v2 {@code request.formula} 스키마로 변환한다 - v1 {@code FormulaRequestMapper}와 같은 필드 매핑. */
	private JsonNode buildRequestNode(FragranceRequest request) {
		ObjectNode formula = jsonMapper.createObjectNode();
		formula.put("brief", request.getRawText());
		putIfPresent(formula, "max_risk_tier", request.getRiskTier());
		putIfPresent(formula, "product_concentration_percent", request.getUsageConcentrationPercent());
		putIfPresent(formula, "max_ingredient_price_per_kg", request.getMaxIngredientPricePerKg());
		putIfPresent(formula, "max_ingredients", request.getMaxIngredientCount());
		TargetRegion region = request.getTargetRegion();
		if (region != null) {
			formula.put("target_region", region.modalValue());
		}
		ProductCategory category = request.getProductCategory();
		if (category != null) {
			formula.put("product_category", category.getModalValue());
		}

		ObjectNode requestNode = jsonMapper.createObjectNode();
		requestNode.set("formula", formula);
		return requestNode;
	}

	private JsonNode buildEvidencePolicy(Double finishedBatchMassG, Integer maximumLeadTimeDays,
			Double maximumPurchaseCostUsd) {
		ObjectNode policy = jsonMapper.createObjectNode();
		putIfPresent(policy, "finished_batch_mass_g", finishedBatchMassG);
		putIfPresent(policy, "maximum_lead_time_days", maximumLeadTimeDays);
		putIfPresent(policy, "maximum_purchase_cost_usd", maximumPurchaseCostUsd);
		return policy;
	}

	private static void putIfPresent(ObjectNode node, String field, Integer value) {
		if (value != null) {
			node.put(field, value);
		}
	}

	private static void putIfPresent(ObjectNode node, String field, Double value) {
		if (value != null) {
			node.put(field, value);
		}
	}
}
