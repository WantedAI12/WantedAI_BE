package com.perfumeryaicore.domain.prediction.controller;

import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionUncertaintyResponse;
import com.perfumeryaicore.domain.prediction.service.PredictionService;
import com.perfumeryaicore.global.response.ApiResponse;
import com.perfumeryaicore.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 성능 프록시 예측 조회. 재계산 트리거(POST /predictions)는 실질적으로 후보 재생성과 같아
 * formula 도메인의 편집 기능과 함께 후속 개발 예정이며 아직 배포되지 않았다.
 */
@Tag(name = "Prediction")
@RestController
@RequiredArgsConstructor
public class PredictionController {

	private final PredictionService predictionService;

	@Operation(summary = "성능 프록시 예측 결과 조회")
	@GetMapping("/candidates/{candidateId}/predictions")
	public ApiResponse<PredictionResponse> get(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId) {
		return ApiResponse.success(predictionService.get(candidateId, principal.id()));
	}

	@Operation(summary = "불확실성·데이터 적용범위 진단 조회")
	@GetMapping("/candidates/{candidateId}/predictions/uncertainty")
	public ApiResponse<PredictionUncertaintyResponse> getUncertainty(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId) {
		return ApiResponse.success(predictionService.getUncertainty(candidateId, principal.id()));
	}
}
