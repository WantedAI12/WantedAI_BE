package com.perfumeryaicore.domain.experiment.controller;

import com.perfumeryaicore.domain.experiment.dto.request.ExperimentStatusChangeRequest;
import com.perfumeryaicore.domain.experiment.dto.response.CandidateCompareRow;
import com.perfumeryaicore.domain.experiment.dto.response.ExperimentStatusLogResponse;
import com.perfumeryaicore.domain.experiment.service.CandidateCompareService;
import com.perfumeryaicore.domain.experiment.service.ExperimentStatusService;
import com.perfumeryaicore.global.response.ApiResponse;
import com.perfumeryaicore.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Experiment")
@RestController
@RequiredArgsConstructor
public class ExperimentController {

	private final CandidateCompareService candidateCompareService;
	private final ExperimentStatusService experimentStatusService;

	@Operation(summary = "후보 비교표 조회 (목표일치도/비용/공급안정성/예측치)")
	@GetMapping("/requests/{requestId}/candidates/compare")
	public ApiResponse<List<CandidateCompareRow>> compare(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long requestId,
			@RequestParam String candidateIds) {
		List<Long> ids = List.of(candidateIds.split(",")).stream()
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.map(Long::valueOf)
				.toList();
		return ApiResponse.success(candidateCompareService.compare(requestId, ids, principal.id()));
	}

	@Operation(summary = "실험 후보로 확정 또는 상태 변경")
	@PostMapping("/candidates/{candidateId}/experiment-status")
	public ApiResponse<ExperimentStatusLogResponse> changeStatus(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId,
			@Valid @RequestBody ExperimentStatusChangeRequest request) {
		return ApiResponse.success(
				experimentStatusService.changeStatus(candidateId, principal.id(), request.status()));
	}

	@Operation(summary = "현재 실험 상태 및 상태 변경 이력 조회")
	@GetMapping("/candidates/{candidateId}/experiment-status")
	public ApiResponse<List<ExperimentStatusLogResponse>> history(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId) {
		return ApiResponse.success(experimentStatusService.history(candidateId, principal.id()));
	}
}
