package com.perfumeryaicore.domain.ops.controller;

import com.perfumeryaicore.domain.ops.dto.response.OpsEventResponse;
import com.perfumeryaicore.domain.ops.dto.response.OpsOverviewResponse;
import com.perfumeryaicore.domain.ops.service.OpsService;
import com.perfumeryaicore.global.response.ApiResponse;
import com.perfumeryaicore.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Ops")
@RestController
@RequestMapping("/ops")
@RequiredArgsConstructor
public class OpsController {

	private final OpsService opsService;

	@Operation(summary = "서비스 운영 상태 - 전체 현황 (내가 속한 프로젝트 기준: 후보 생성 큐, 평균 생성 시간, 기권 현황)")
	@GetMapping("/overview")
	public ApiResponse<OpsOverviewResponse> overview(@AuthenticationPrincipal MemberPrincipal principal) {
		return ApiResponse.success(opsService.overview(principal.id()));
	}

	@Operation(summary = "서비스 운영 상태 - 최근 시스템 이벤트 (내가 속한 프로젝트 기준: 작업 이력·팀원 변경, 최신순, "
			+ "limit 기본 20·최대 100)")
	@GetMapping("/events")
	public ApiResponse<List<OpsEventResponse>> events(
			@AuthenticationPrincipal MemberPrincipal principal,
			@RequestParam(required = false) Integer limit) {
		return ApiResponse.success(opsService.events(principal.id(), limit));
	}
}
