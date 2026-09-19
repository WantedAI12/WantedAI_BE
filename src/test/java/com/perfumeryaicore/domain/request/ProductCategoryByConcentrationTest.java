package com.perfumeryaicore.domain.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.TargetRegion;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 향수 제품 유형(오 드 코롱·뚜왈렛·퍼퓸)은 사용 농도로 정해진다 - 사용자가 농도를 어떻게 정해도 항상
 * 오 드 퍼퓸으로 저장되던 문제(2026-09-19)를 막는다.
 */
class ProductCategoryByConcentrationTest {

	@ParameterizedTest(name = "농도 {0}% -> {1}")
	@CsvSource({
			"0.5,  EAU_DE_COLOGNE",
			"2,    EAU_DE_COLOGNE",
			"4.99, EAU_DE_COLOGNE",
			"5,    EAU_DE_TOILETTE",
			"10,   EAU_DE_TOILETTE",
			"14.99, EAU_DE_TOILETTE",
			"15,   EAU_DE_PARFUM",
			"20,   EAU_DE_PARFUM",
			"30,   EAU_DE_PARFUM"
	})
	void the_grade_follows_the_concentration_with_half_open_boundaries(double percent, ProductCategory expected) {
		assertThat(ProductCategory.forFragranceConcentration(percent)).isEqualTo(expected);
	}

	@Test
	void only_the_three_fragrance_grades_are_concentration_graded() {
		assertThat(ProductCategory.EAU_DE_PARFUM.isFragranceGrade()).isTrue();
		assertThat(ProductCategory.EAU_DE_TOILETTE.isFragranceGrade()).isTrue();
		assertThat(ProductCategory.EAU_DE_COLOGNE.isFragranceGrade()).isTrue();
		assertThat(ProductCategory.BODY_LOTION.isFragranceGrade()).isFalse();
		assertThat(ProductCategory.CANDLE.isFragranceGrade()).isFalse();
	}

	private static FragranceRequest request(ProductCategory category, Double concentrationPercent) {
		FragranceRequest request = FragranceRequest.create(10L, 1L, "겨울에 쓸 수 있는 따듯한 향");
		request.applyUpdate(null, category, TargetRegion.KR, 1, null, null, concentrationPercent, null, null, List.of());
		return request;
	}

	/** 프론트가 항상 보내던 EAU_DE_PARFUM이라도 농도가 낮으면 농도가 우선한다. */
	@Test
	void a_low_concentration_overrides_the_default_perfume_grade_the_client_sends() {
		assertThat(request(ProductCategory.EAU_DE_PARFUM, 3.0).getProductCategory())
				.isEqualTo(ProductCategory.EAU_DE_COLOGNE);
		assertThat(request(ProductCategory.EAU_DE_PARFUM, 10.0).getProductCategory())
				.isEqualTo(ProductCategory.EAU_DE_TOILETTE);
		assertThat(request(ProductCategory.EAU_DE_PARFUM, 18.0).getProductCategory())
				.isEqualTo(ProductCategory.EAU_DE_PARFUM);
	}

	/** 사용자가 직접 뚜왈렛을 골랐어도 농도가 정한다(농도가 기준). */
	@Test
	void a_grade_the_client_picked_does_not_win_over_the_concentration() {
		assertThat(request(ProductCategory.EAU_DE_TOILETTE, 18.0).getProductCategory())
				.isEqualTo(ProductCategory.EAU_DE_PARFUM);
	}

	@Test
	void changing_only_the_concentration_later_re_grades_the_stored_product() {
		FragranceRequest request = request(ProductCategory.EAU_DE_PARFUM, 18.0);

		request.applyUpdate(null, null, null, null, null, null, 4.0, null, null, null);

		assertThat(request.getProductCategory()).isEqualTo(ProductCategory.EAU_DE_COLOGNE);
		assertThat(request.getUsageConcentrationPercent()).isEqualTo(4.0);
	}

	@Test
	void without_a_concentration_the_chosen_grade_is_kept() {
		assertThat(request(ProductCategory.EAU_DE_TOILETTE, null).getProductCategory())
				.isEqualTo(ProductCategory.EAU_DE_TOILETTE);
	}

	@Test
	void products_that_are_not_fragrance_grades_are_never_touched() {
		assertThat(request(ProductCategory.BODY_LOTION, 1.5).getProductCategory())
				.isEqualTo(ProductCategory.BODY_LOTION);
		assertThat(request(ProductCategory.CANDLE, 3.0).getProductCategory())
				.isEqualTo(ProductCategory.CANDLE);
	}

	/** 규칙 도입 전에 농도와 어긋나게 저장된 임시 요청도, AI가 실제로 받는 확정 시점에는 맞춰진다. */
	@Test
	void confirming_re_grades_a_draft_that_was_stored_before_the_rule_existed() {
		FragranceRequest stale = request(ProductCategory.EAU_DE_PARFUM, null);
		ReflectionTestUtils.setField(stale, "usageConcentrationPercent", 3.0);
		assertThat(stale.getProductCategory()).isEqualTo(ProductCategory.EAU_DE_PARFUM);

		stale.confirm();

		assertThat(stale.getProductCategory()).isEqualTo(ProductCategory.EAU_DE_COLOGNE);
		assertThat(stale.isConfirmed()).isTrue();
	}
}
