package com.perfumeryaicore.domain.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.request.dto.request.BriefClarifyRequest;
import com.perfumeryaicore.domain.request.dto.request.BriefReviewRequest;
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.entity.Intensity;
import com.perfumeryaicore.domain.request.entity.Longevity;
import com.perfumeryaicore.global.client.PerfumeryAiClient;
import com.perfumeryaicore.global.client.PerfumeryAiResult;
import com.perfumeryaicore.global.client.dto.ClarifyBriefRequest;
import com.perfumeryaicore.global.client.dto.ClarifyBriefResponse;
import com.perfumeryaicore.global.client.dto.PrepareBriefRequest;
import com.perfumeryaicore.global.client.dto.PrepareBriefResponse;
import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.TargetRegion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** v2 연동 답변(2026-09-15) 1단계: /v2/briefs/prepare, /v2/briefs/clarify 위임을 검증한다. */
class BriefReviewServiceTest {

	private static final long REQUEST_ID = 500L;
	private static final long MEMBER_ID = 1L;
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final FragranceRequestService requestService = mock(FragranceRequestService.class);
	private final PerfumeryAiClient perfumeryAiClient = mock(PerfumeryAiClient.class);
	private final BriefReviewService service = new BriefReviewService(requestService, perfumeryAiClient);

	private FragranceRequest fullyStructuredRequest() {
		FragranceRequest request = FragranceRequest.create(10L, MEMBER_ID, "피오니와 청사과 향");
		request.applyUpdate(null, ProductCategory.EAU_DE_PARFUM, TargetRegion.EU, 2,
				Intensity.MODERATE, Longevity.HIGH, 15.0, 30, 180.0, List.of());
		return request;
	}

	private PrepareBriefResponse readyResponse(String reviewId) {
		return new PrepareBriefResponse("rd-brief-2", "ready", List.of(), List.of(), List.of(),
				JSON.createObjectNode(), JSON.createObjectNode(), null, JSON.createObjectNode(),
				JSON.createObjectNode(), reviewId, true, false, "result-1");
	}

	@Test
	void prepare_maps_the_stored_request_into_the_v2_formula_schema() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		ArgumentCaptor<PrepareBriefRequest> captor = ArgumentCaptor.forClass(PrepareBriefRequest.class);
		when(perfumeryAiClient.prepareBrief(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", readyResponse("review-1"), 10L));

		PrepareBriefResponse response = service.prepare(REQUEST_ID, MEMBER_ID, BriefReviewRequest.empty());

		assertThat(response.isReady()).isTrue();
		assertThat(response.reviewId()).isEqualTo("review-1");
		JsonNode formula = captor.getValue().request().get("formula");
		assertThat(formula.get("brief").asString()).isEqualTo("피오니와 청사과 향");
		assertThat(formula.get("max_risk_tier").asInt()).isEqualTo(2);
		assertThat(formula.get("product_concentration_percent").asDouble()).isEqualTo(15.0);
		assertThat(formula.get("max_ingredient_price_per_kg").asDouble()).isEqualTo(180.0);
		assertThat(formula.get("target_region").asString()).isEqualTo("EU");
		assertThat(formula.get("product_category").asString()).isEqualTo("eau_de_parfum");
	}

	@Test
	void prepare_includes_only_the_evidence_policy_fields_that_were_provided() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		ArgumentCaptor<PrepareBriefRequest> captor = ArgumentCaptor.forClass(PrepareBriefRequest.class);
		when(perfumeryAiClient.prepareBrief(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", readyResponse("review-1"), 10L));

		service.prepare(REQUEST_ID, MEMBER_ID, new BriefReviewRequest(1000.0, null, null));

		JsonNode policy = captor.getValue().evidencePolicy();
		assertThat(policy.get("finished_batch_mass_g").asDouble()).isEqualTo(1000.0);
		assertThat(policy.has("maximum_lead_time_days")).isFalse();
		assertThat(policy.has("maximum_purchase_cost_usd")).isFalse();
	}

	@Test
	void clarify_forwards_the_prepared_result_id_and_answers() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		ArgumentCaptor<ClarifyBriefRequest> captor = ArgumentCaptor.forClass(ClarifyBriefRequest.class);
		ClarifyBriefResponse clarifyResponse = new ClarifyBriefResponse(
				"rd-clarification-2", "result-1", List.of("request.formula.target_region"),
				JSON.createObjectNode(), JSON.createObjectNode(), 1, true);
		when(perfumeryAiClient.clarifyBrief(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", clarifyResponse, 10L));

		BriefClarifyRequest dto = new BriefClarifyRequest(
				"result-1", Map.of("request.formula.target_region", "EU"), null, null, null);
		ClarifyBriefResponse response = service.clarify(REQUEST_ID, MEMBER_ID, dto);

		assertThat(response.previousResultId()).isEqualTo("result-1");
		assertThat(response.stateChanged()).isTrue();
		assertThat(captor.getValue().preparedResultId()).isEqualTo("result-1");
		assertThat(captor.getValue().answers().get("request.formula.target_region").asString()).isEqualTo("EU");
	}

	@Test
	void prepare_checks_project_access_through_the_existing_request_service() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		when(perfumeryAiClient.prepareBrief(any(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", readyResponse("review-1"), 10L));

		service.prepare(REQUEST_ID, MEMBER_ID, BriefReviewRequest.empty());

		verify(requestService).getAccessibleRequest(eq(REQUEST_ID), eq(MEMBER_ID));
	}
}
