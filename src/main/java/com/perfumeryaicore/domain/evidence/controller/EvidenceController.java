package com.perfumeryaicore.domain.evidence.controller;

import com.perfumeryaicore.domain.evidence.dto.request.SensoryTestPlanRequest;
import com.perfumeryaicore.domain.evidence.dto.request.SensoryTestResultCreateRequest;
import com.perfumeryaicore.domain.evidence.dto.response.EvidenceEvent;
import com.perfumeryaicore.domain.evidence.dto.response.EvidenceReportResponse;
import com.perfumeryaicore.domain.evidence.dto.response.SensoryTestDetailResponse;
import com.perfumeryaicore.domain.evidence.dto.response.SensoryTestResponse;
import com.perfumeryaicore.domain.evidence.dto.response.SensoryTestResultResponse;
import com.perfumeryaicore.domain.evidence.service.EvidenceReportService;
import com.perfumeryaicore.domain.evidence.service.EvidenceTimelineService;
import com.perfumeryaicore.domain.evidence.service.SensoryTestService;
import com.perfumeryaicore.domain.job.dto.response.JobResponse;
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

@Tag(name = "Evidence")
@RestController
@RequiredArgsConstructor
public class EvidenceController {

	private final EvidenceTimelineService evidenceTimelineService;
	private final SensoryTestService sensoryTestService;
	private final EvidenceReportService evidenceReportService;

	@Operation(summary = "후보 생성 근거·감사 이력 조회")
	@GetMapping("/candidates/{candidateId}/evidence")
	public ApiResponse<List<EvidenceEvent>> evidence(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId) {
		return ApiResponse.success(evidenceTimelineService.timeline(candidateId, principal.id()));
	}

	@Operation(summary = "독립 블라인드 관능 검증 계획 등록")
	@PostMapping("/candidates/{candidateId}/sensory-tests")
	public ResponseEntity<ApiResponse<SensoryTestResponse>> planSensoryTest(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId,
			@Valid @RequestBody SensoryTestPlanRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(sensoryTestService.plan(candidateId, principal.id(), request)));
	}

	@Operation(summary = "관능 검증 계획/결과 목록")
	@GetMapping("/candidates/{candidateId}/sensory-tests")
	public ApiResponse<List<SensoryTestResponse>> listSensoryTests(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId) {
		return ApiResponse.success(sensoryTestService.list(candidateId, principal.id()));
	}

	@Operation(summary = "검증 상세 (계획, 결과, 예측 유사도 참고값)")
	@GetMapping("/sensory-tests/{testId}")
	public ApiResponse<SensoryTestDetailResponse> getSensoryTest(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long testId) {
		return ApiResponse.success(sensoryTestService.getDetail(testId, principal.id()));
	}

	@Operation(summary = "블라인드 관능 검증 결과 등록")
	@PostMapping("/sensory-tests/{testId}/results")
	public ResponseEntity<ApiResponse<SensoryTestResultResponse>> recordSensoryTestResult(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long testId,
			@Valid @RequestBody SensoryTestResultCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(sensoryTestService.recordResult(testId, principal.id(), request)));
	}

	@Operation(summary = "증거 보고서 생성 요청 (비동기)")
	@PostMapping("/candidates/{candidateId}/evidence-reports")
	public ResponseEntity<ApiResponse<JobResponse>> requestEvidenceReport(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(ApiResponse.success(evidenceReportService.request(candidateId, principal.id())));
	}

	@Operation(summary = "보고서 생성 상태 및 내용 조회")
	@GetMapping("/evidence-reports/{reportId}")
	public ApiResponse<EvidenceReportResponse> getEvidenceReport(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long reportId) {
		return ApiResponse.success(evidenceReportService.get(reportId, principal.id()));
	}
}
