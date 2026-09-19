package com.perfumeryaicore.domain.request.dto.response;

import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.entity.Intensity;
import com.perfumeryaicore.domain.request.entity.Longevity;
import com.perfumeryaicore.domain.request.entity.RequestStatus;
import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.TargetRegion;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 향 요청 상세 응답. {@code missingFields}가 비어 있어야 {@code POST /requests/{id}/confirm}이 가능하다.
 *
 * <p>{@code requestId}는 전체 요청이 공용으로 쓰는 자동 증가 ID라 API 식별용이다 - 화면에 번호로
 * 보여줄 때는 프로젝트 안에서 1부터 매긴 {@code requestNumber}를 쓴다.
 */
public record FragranceRequestResponse(
		Long requestId,
		int requestNumber,
		RequestStatus status,
		StructuredIntent structuredIntent,
		List<String> missingFields,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {

	public record StructuredIntent(
			String rawText,
			List<String> accords,
			Intensity intensity,
			Longevity longevity,
			ProductCategory productCategory,
			TargetRegion targetRegion,
			Integer riskTier,
			Double usageConcentrationPercent,
			Integer maxIngredientCount,
			Double maxIngredientPricePerKg
	) {
	}

	public static FragranceRequestResponse from(FragranceRequest r, int requestNumber) {
		StructuredIntent intent = new StructuredIntent(
				r.getRawText(),
				r.accords(),
				r.getIntensity(),
				r.getLongevity(),
				r.getProductCategory(),
				r.getTargetRegion(),
				r.getRiskTier(),
				r.getUsageConcentrationPercent(),
				r.getMaxIngredientCount(),
				r.getMaxIngredientPricePerKg());
		return new FragranceRequestResponse(
				r.getId(),
				requestNumber,
				r.getStatus(),
				intent,
				r.missingRequiredFields(),
				r.getCreatedAt(),
				r.getUpdatedAt());
	}
}
