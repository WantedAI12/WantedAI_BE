package com.perfumeryaicore.domain.request.controller;

import com.perfumeryaicore.domain.request.dto.request.BriefClarifyRequest;
import com.perfumeryaicore.domain.request.dto.request.BriefReviewRequest;
import com.perfumeryaicore.domain.request.dto.request.CreateFragranceRequestRequest;
import com.perfumeryaicore.domain.request.dto.request.UpdateFragranceRequestRequest;
import com.perfumeryaicore.domain.request.dto.request.UpdateWorkChecklistItemRequest;
import com.perfumeryaicore.domain.request.dto.response.FragranceRequestResponse;
import com.perfumeryaicore.domain.request.dto.response.ProjectProgressResponse;
import com.perfumeryaicore.domain.request.dto.response.WorkChecklistItemResponse;
import com.perfumeryaicore.domain.request.dto.response.WorkProgressResponse;
import com.perfumeryaicore.domain.request.entity.RequestStatus;
import com.perfumeryaicore.domain.request.entity.WorkChecklistItemType;
import com.perfumeryaicore.domain.request.service.BriefReviewService;
import com.perfumeryaicore.domain.request.service.FragranceRequestService;
import com.perfumeryaicore.domain.request.service.WorkChecklistService;
import com.perfumeryaicore.global.client.dto.ClarifyBriefResponse;
import com.perfumeryaicore.global.client.dto.PrepareBriefResponse;
import com.perfumeryaicore.global.response.ApiResponse;
import com.perfumeryaicore.global.response.PageResponse;
import com.perfumeryaicore.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
	private final BriefReviewService briefReviewService;
	private final WorkChecklistService workChecklistService;

	@Operation(summary = "자연어 향 요청 제출 (구조화 결과 즉시 반환)")
	@PostMapping("/projects/{projectId}/requests")
	public ResponseEntity<ApiResponse<FragranceRequestResponse>> create(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long projectId,
			@Valid @RequestBody CreateFragranceRequestRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(requestService.create(projectId, principal.id(), request)));
	}

	@Operation(summary = "요청 목록 조회 (상태별 필터, 페이지네이션)")
	@GetMapping("/projects/{projectId}/requests")
	public ApiResponse<PageResponse<FragranceRequestResponse>> list(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long projectId,
			@RequestParam(required = false) RequestStatus status,
			@PageableDefault(size = 20) Pageable pageable) {
		return ApiResponse.success(requestService.list(projectId, principal.id(), status, pageable));
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

	@Operation(summary = "향수 작업 체크리스트 조회 (고정 6항목)")
	@GetMapping("/requests/{requestId}/checklist")
	public ApiResponse<List<WorkChecklistItemResponse>> checklist(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long requestId) {
		return ApiResponse.success(workChecklistService.list(requestId, principal.id()));
	}

	@Operation(summary = "체크리스트 항목 완료/해제 (동시 수정은 revision 낙관적 잠금으로 409)")
	@PatchMapping("/requests/{requestId}/checklist/{itemType}")
	public ApiResponse<WorkChecklistItemResponse> updateChecklistItem(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long requestId,
			@PathVariable WorkChecklistItemType itemType,
			@Valid @RequestBody UpdateWorkChecklistItemRequest request) {
		return ApiResponse.success(workChecklistService.setCompleted(
				requestId, principal.id(), itemType, request.completed(), request.expectedRevision()));
	}

	@Operation(summary = "향수 작업 단위 체크리스트 진행률")
	@GetMapping("/requests/{requestId}/progress")
	public ApiResponse<WorkProgressResponse> workProgress(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long requestId) {
		return ApiResponse.success(workChecklistService.workProgress(requestId, principal.id()));
	}

	@Operation(summary = "프로젝트 단위 체크리스트 진행률 (소속 작업 항목 수 가중 집계)")
	@GetMapping("/projects/{projectId}/progress")
	public ApiResponse<ProjectProgressResponse> projectProgress(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long projectId) {
		return ApiResponse.success(workChecklistService.projectProgress(projectId, principal.id()));
	}

	@Operation(summary = "AI 구조화 사전 검토 (v2) — 보완 질문이 있으면 status=needs_input과 questions로 반환")
	@PostMapping("/requests/{requestId}/brief-review")
	public ApiResponse<PrepareBriefResponse> reviewBrief(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long requestId,
			@RequestBody(required = false) BriefReviewRequest request) {
		BriefReviewRequest dto = request != null ? request : BriefReviewRequest.empty();
		return ApiResponse.success(briefReviewService.prepare(requestId, principal.id(), dto));
	}

	@Operation(summary = "AI 사전 검토 보완 질문에 대한 답변 제출 (v2)")
	@PostMapping("/requests/{requestId}/brief-review/clarify")
	public ApiResponse<ClarifyBriefResponse> clarifyBrief(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long requestId,
			@Valid @RequestBody BriefClarifyRequest request) {
		return ApiResponse.success(briefReviewService.clarify(requestId, principal.id(), request));
	}
}
