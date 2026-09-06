package com.perfumeryaicore.domain.supply.controller;

import com.perfumeryaicore.domain.supply.dto.request.RecordSupplyReviewDecisionRequest;
import com.perfumeryaicore.domain.supply.dto.request.RegisterSupplyChangeRequest;
import com.perfumeryaicore.domain.supply.dto.response.AffectedCandidateResponse;
import com.perfumeryaicore.domain.supply.dto.response.SupplyChangeResponse;
import com.perfumeryaicore.domain.supply.dto.response.SupplyReviewDecisionResponse;
import com.perfumeryaicore.domain.supply.service.SupplyChangeService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Supply")
@RestController
@RequiredArgsConstructor
public class SupplyController {

	private final SupplyChangeService supplyChangeService;

	@Operation(summary = "원료 공급 조건 변경 등록 → 즉시 영향 후보 분석 (SUPPLIER / FRAGRANCE_RND)")
	@PostMapping("/ingredients/{ingredientId}/supply-changes")
	public ResponseEntity<ApiResponse<SupplyChangeResponse>> register(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable String ingredientId,
			@Valid @RequestBody RegisterSupplyChangeRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(supplyChangeService.register(ingredientId, principal.id(), request)));
	}

	@Operation(summary = "공급 변경 이벤트 및 분석 상태 조회")
	@GetMapping("/supply-changes/{changeId}")
	public ApiResponse<SupplyChangeResponse> get(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long changeId) {
		return ApiResponse.success(supplyChangeService.get(changeId, principal.id()));
	}

	@Operation(summary = "영향받는 후보 목록 (재검토 필요 후보)")
	@GetMapping("/supply-changes/{changeId}/affected-candidates")
	public ApiResponse<List<AffectedCandidateResponse>> affectedCandidates(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long changeId) {
		return ApiResponse.success(supplyChangeService.affectedCandidates(changeId, principal.id()));
	}

	@Operation(summary = "재검토 후속 결정 기록 (PERFUMER / FRAGRANCE_RND / PROJECT_MANAGER)")
	@PostMapping("/candidates/{candidateId}/supply-review-decisions")
	public ResponseEntity<ApiResponse<SupplyReviewDecisionResponse>> recordDecision(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId,
			@Valid @RequestBody RecordSupplyReviewDecisionRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(
						supplyChangeService.recordDecision(candidateId, principal.id(), request)));
	}

	@Operation(summary = "재검토 의사결정 이력 조회 (프로젝트 멤버, AUDITOR)")
	@GetMapping("/candidates/{candidateId}/supply-review-decisions")
	public ApiResponse<List<SupplyReviewDecisionResponse>> listDecisions(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId) {
		return ApiResponse.success(supplyChangeService.listDecisions(candidateId, principal.id()));
	}
}
