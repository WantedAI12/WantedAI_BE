package com.perfumeryaicore.domain.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import com.perfumeryaicore.domain.formula.service.FormulaRequestMapper;
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.global.client.ModalAiProperties;
import com.perfumeryaicore.global.client.dto.FormulaGenerationRequest;
import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.TargetRegion;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI 개발팀 확인(2026-09-19): v2 리뷰 경로({@code BriefReviewService})에만 원화→USD 환산을
 * 적용하고, 실제 배합 생성 경로(이 매퍼가 만드는 {@code /v1/formulas} 요청)에는 빠뜨렸던 문제를
 * 검증한다 - 화면 입력(KRW/kg)이 그대로 Modal에 USD/kg인 것처럼 전달되면 안 된다.
 */
class FormulaRequestMapperTest {

	private static final double KRW_PER_USD = 1350.0;

	private final ModalAiProperties modalAiProperties = new ModalAiProperties(
			"http://ai.local", "token", Duration.ofSeconds(1), Duration.ofSeconds(2), 30, 1, KRW_PER_USD);
	private final FormulaRequestMapper mapper = new FormulaRequestMapper(modalAiProperties);

	private FragranceRequest requestWithPrice(double krwPerKg) {
		FragranceRequest request = FragranceRequest.create(10L, 1L, "시트러스 우디");
		request.applyUpdate(null, ProductCategory.EAU_DE_PARFUM, TargetRegion.EU, 1,
				null, null, 15.0, 12, krwPerKg, List.of());
		return request;
	}

	@Test
	void toModalRequest_converts_the_stored_krw_price_to_usd_per_kg() {
		FormulaGenerationRequest modalRequest = mapper.toModalRequest(requestWithPrice(180_000.0));

		assertThat(modalRequest.maxIngredientPricePerKg()).isCloseTo(180_000.0 / KRW_PER_USD, offset(1e-9));
	}

	@Test
	void toUsdIngredientPricePerKg_matches_the_conversion_used_for_formula_generation() {
		FragranceRequest request = requestWithPrice(6_450.0);

		assertThat(mapper.toUsdIngredientPricePerKg(request)).isCloseTo(6_450.0 / KRW_PER_USD, offset(1e-9));
	}

	@Test
	void a_missing_price_stays_null_after_conversion() {
		FragranceRequest request = FragranceRequest.create(10L, 1L, "시트러스 우디");

		assertThat(mapper.toModalRequest(request).maxIngredientPricePerKg()).isNull();
		assertThat(mapper.toUsdIngredientPricePerKg(request)).isNull();
	}
}
