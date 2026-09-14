package com.perfumeryaicore.domain.lotion.controller;

import com.perfumeryaicore.domain.lotion.dto.response.LotionDetailResponse;
import com.perfumeryaicore.domain.lotion.service.LotionDetailService;
import com.perfumeryaicore.global.response.ApiResponse;
import com.perfumeryaicore.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Lotion")
@RestController
@RequiredArgsConstructor
public class LotionDetailController {

	private final LotionDetailService lotionDetailService;

	@Operation(summary = "바디로션 후보 상세 조회 (결과판정·점수·검토한계·배합·상세조건)")
	@GetMapping("/candidates/{candidateId}/lotion-detail")
	public ApiResponse<LotionDetailResponse> get(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId) {
		return ApiResponse.success(lotionDetailService.get(candidateId, principal.id()));
	}
}
