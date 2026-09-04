package com.perfumeryaicore.domain.safety.controller;

import com.perfumeryaicore.domain.safety.dto.request.ApprovalGateCreateRequest;
import com.perfumeryaicore.domain.safety.dto.response.ApprovalGateResponse;
import com.perfumeryaicore.domain.safety.dto.response.SafetyEvaluationResponse;
import com.perfumeryaicore.domain.safety.service.ApprovalGateService;
import com.perfumeryaicore.domain.safety.service.SafetyEvaluationService;
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

/**
 * 안전·규제·공급 제약 게이트. 평가 재실행(POST /safety-evaluation)은 실질적으로 후보 재생성과
 * 같아 formula 도메인의 편집 기능과 함께 후속 개발 예정이며 아직 배포되지 않았다.
 */
@Tag(name = "Safety")
@RestController
@RequiredArgsConstructor
public class SafetyController {

	private final SafetyEvaluationService safetyEvaluationService;
	private final ApprovalGateService approvalGateService;

	@Operation(summary = "안전·규제·공급 적합성 평가 결과 조회")
	@GetMapping("/candidates/{candidateId}/safety-evaluation")
	public ApiResponse<SafetyEvaluationResponse> getSafetyEvaluation(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId) {
		return ApiResponse.success(safetyEvaluationService.get(candidateId, principal.id()));
	}

	@Operation(summary = "게이트 결정 이력 조회")
	@GetMapping("/candidates/{candidateId}/approval-gate")
	public ApiResponse<List<ApprovalGateResponse>> getApprovalGateHistory(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId) {
		return ApiResponse.success(approvalGateService.history(candidateId, principal.id()));
	}

	@Operation(summary = "안전·규제 승인 게이트 결정 등록")
	@PostMapping("/candidates/{candidateId}/approval-gate")
	public ResponseEntity<ApiResponse<ApprovalGateResponse>> registerApprovalGate(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId,
			@Valid @RequestBody ApprovalGateCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(approvalGateService.register(candidateId, principal.id(), request)));
	}
}
