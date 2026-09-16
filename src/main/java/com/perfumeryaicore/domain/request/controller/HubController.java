package com.perfumeryaicore.domain.request.controller;

import com.perfumeryaicore.domain.request.dto.response.HubSummaryResponse;
import com.perfumeryaicore.domain.request.service.HubService;
import com.perfumeryaicore.global.response.ApiResponse;
import com.perfumeryaicore.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Hub")
@RestController
@RequiredArgsConstructor
public class HubController {

	private final HubService hubService;

	@Operation(summary = "로그인 후 허브 요약 (내 프로젝트, 마감 임박 프로젝트, 미완료 체크리스트 항목)")
	@GetMapping("/hub")
	public ApiResponse<HubSummaryResponse> summary(@AuthenticationPrincipal MemberPrincipal principal) {
		return ApiResponse.success(hubService.summary(principal.id()));
	}
}
