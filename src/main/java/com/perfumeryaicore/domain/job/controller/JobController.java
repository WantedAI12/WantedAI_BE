package com.perfumeryaicore.domain.job.controller;

import com.perfumeryaicore.domain.job.dto.response.JobResponse;
import com.perfumeryaicore.domain.job.service.JobService;
import com.perfumeryaicore.global.response.ApiResponse;
import com.perfumeryaicore.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Job")
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

	private final JobService jobService;

	@Operation(summary = "작업 상태·결과 참조 조회")
	@GetMapping("/{jobId}")
	public ApiResponse<JobResponse> get(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long jobId) {
		return ApiResponse.success(jobService.get(jobId, principal.id()));
	}

	@Operation(summary = "실패한 작업 재시도")
	@PostMapping("/{jobId}/retry")
	public ResponseEntity<Void> retry(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long jobId) {
		jobService.retry(jobId, principal.id());
		return ResponseEntity.accepted().build();
	}

	@Operation(summary = "대기/실행 중 작업 취소")
	@PostMapping("/{jobId}/cancel")
	public ApiResponse<JobResponse> cancel(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long jobId) {
		return ApiResponse.success(jobService.cancel(jobId, principal.id()));
	}
}
