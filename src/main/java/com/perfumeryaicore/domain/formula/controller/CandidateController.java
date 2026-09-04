package com.perfumeryaicore.domain.formula.controller;

import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse;
import com.perfumeryaicore.domain.formula.service.CandidateGenerationService;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.job.dto.response.JobResponse;
import com.perfumeryaicore.global.response.ApiResponse;
import com.perfumeryaicore.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Formula")
@RestController
@RequiredArgsConstructor
public class CandidateController {

	private final CandidateGenerationService generationService;
	private final CandidateService candidateService;

	@Operation(summary = "후보 조향식 생성 요청 (확정된 요청만 가능, 비동기)")
	@PostMapping("/requests/{requestId}/candidates")
	public ResponseEntity<ApiResponse<JobResponse>> generate(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long requestId) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(ApiResponse.success(generationService.enqueue(requestId, principal.id())));
	}

	@Operation(summary = "해당 요청의 후보 목록")
	@GetMapping("/requests/{requestId}/candidates")
	public ApiResponse<List<CandidateResponse>> listByRequest(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long requestId) {
		return ApiResponse.success(candidateService.listByRequest(requestId, principal.id()));
	}

	@Operation(summary = "후보 상세 (원료 구성/배합비율/비용/시간 변화/현재 버전)")
	@GetMapping("/candidates/{candidateId}")
	public ApiResponse<CandidateResponse> get(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId) {
		return ApiResponse.success(candidateService.get(candidateId, principal.id()));
	}

	@Operation(summary = "버전 이력 목록")
	@GetMapping("/candidates/{candidateId}/versions")
	public ApiResponse<List<CandidateVersionResponse>> versions(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId) {
		return ApiResponse.success(candidateService.versions(candidateId, principal.id()));
	}

	@Operation(summary = "특정 버전 상세")
	@GetMapping("/candidates/versions/{versionId}")
	public ApiResponse<CandidateVersionResponse> version(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long versionId) {
		return ApiResponse.success(candidateService.version(versionId, principal.id()));
	}
}
