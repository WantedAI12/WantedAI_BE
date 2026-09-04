package com.perfumeryaicore.domain.request.controller;

import com.perfumeryaicore.domain.request.dto.request.CreateFragranceRequestRequest;
import com.perfumeryaicore.domain.request.dto.request.UpdateFragranceRequestRequest;
import com.perfumeryaicore.domain.request.dto.response.FragranceRequestResponse;
import com.perfumeryaicore.domain.request.entity.RequestStatus;
import com.perfumeryaicore.domain.request.service.FragranceRequestService;
import com.perfumeryaicore.global.response.ApiResponse;
import com.perfumeryaicore.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Request")
@RestController
@RequiredArgsConstructor
public class FragranceRequestController {

	private final FragranceRequestService requestService;

	@Operation(summary = "자연어 향 요청 제출 (구조화 결과 즉시 반환)")
	@PostMapping("/projects/{projectId}/requests")
	public ResponseEntity<ApiResponse<FragranceRequestResponse>> create(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long projectId,
			@Valid @RequestBody CreateFragranceRequestRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(requestService.create(projectId, principal.id(), request)));
	}

	@Operation(summary = "요청 목록 조회 (상태별 필터)")
	@GetMapping("/projects/{projectId}/requests")
	public ApiResponse<List<FragranceRequestResponse>> list(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long projectId,
			@RequestParam(required = false) RequestStatus status) {
		return ApiResponse.success(requestService.list(projectId, status));
	}

	@Operation(summary = "구조화 결과/누락 항목/상태 조회")
	@GetMapping("/requests/{requestId}")
	public ApiResponse<FragranceRequestResponse> get(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long requestId) {
		return ApiResponse.success(requestService.get(requestId, principal.id()));
	}

	@Operation(summary = "누락 항목 보완 / 구조화 결과 수정")
	@PatchMapping("/requests/{requestId}")
	public ApiResponse<FragranceRequestResponse> update(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long requestId,
			@Valid @RequestBody UpdateFragranceRequestRequest request) {
		return ApiResponse.success(requestService.update(requestId, principal.id(), request));
	}

	@Operation(summary = "구조화된 향 의도 확정")
	@PostMapping("/requests/{requestId}/confirm")
	public ApiResponse<FragranceRequestResponse> confirm(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long requestId) {
		return ApiResponse.success(requestService.confirm(requestId, principal.id()));
	}
}
