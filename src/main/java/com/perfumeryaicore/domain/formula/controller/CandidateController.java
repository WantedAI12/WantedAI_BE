package com.perfumeryaicore.domain.formula.controller;

import com.perfumeryaicore.domain.formula.dto.request.DuplicateCandidateRequest;
import com.perfumeryaicore.domain.formula.dto.request.RestoreCandidateVersionRequest;
import com.perfumeryaicore.domain.formula.dto.request.ReviseCandidateInstructionRequest;
import com.perfumeryaicore.domain.formula.dto.request.UpsertCandidateMemoRequest;
import com.perfumeryaicore.domain.formula.dto.response.CandidateMemoResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse;
import com.perfumeryaicore.domain.formula.dto.response.GenerationRejectionResponse;
import com.perfumeryaicore.domain.formula.entity.CandidateMemoType;
import com.perfumeryaicore.domain.formula.service.CandidateDeletionService;
import com.perfumeryaicore.domain.formula.service.CandidateDiagnosticReviseService;
import com.perfumeryaicore.domain.formula.service.CandidateGenerationService;
import com.perfumeryaicore.domain.formula.service.CandidateMemoService;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.formula.service.GenerationRejectionService;
import com.perfumeryaicore.domain.job.dto.response.JobResponse;
import com.perfumeryaicore.global.client.dto.ReviseCandidateResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Formula")
@RestController
@RequiredArgsConstructor
public class CandidateController {

	private final CandidateGenerationService generationService;
	private final CandidateService candidateService;
	private final CandidateMemoService candidateMemoService;
	private final GenerationRejectionService generationRejectionService;
	private final CandidateDiagnosticReviseService candidateDiagnosticReviseService;
	private final CandidateDeletionService candidateDeletionService;

	@Operation(summary = "후보 조향식 생성 요청 (확정된 요청만 가능, 비동기, Idempotency-Key로 중복 제출 방지)")
	@PostMapping("/requests/{requestId}/candidates")
	public ResponseEntity<ApiResponse<JobResponse>> generate(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long requestId,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(ApiResponse.success(generationService.enqueue(requestId, principal.id(), idempotencyKey)));
	}

	@Operation(summary = "해당 요청에서 AI가 기권(no_safe_match)한 시도 이력 (후보 아님, 정상 후보 목록과 섞이지 않음)")
	@GetMapping("/requests/{requestId}/generation-rejections")
	public ApiResponse<List<GenerationRejectionResponse>> generationRejections(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long requestId) {
		return ApiResponse.success(generationRejectionService.list(requestId, principal.id()));
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

	@Operation(summary = "후보 삭제 (하위 버전·근거·실험이력 등 전부 포함, 되돌릴 수 없음, "
			+ "승인된 후보는 삭제 불가, PERFUMER/FRAGRANCE_RND/PRODUCT_BRAND)")
	@DeleteMapping("/candidates/{candidateId}")
	public ResponseEntity<Void> delete(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId) {
		candidateDeletionService.delete(candidateId, principal.id());
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "후보 복제 (현재 버전을 새 후보로 복사, AI 호출 없음, PERFUMER/FRAGRANCE_RND/PRODUCT_BRAND)")
	@PostMapping("/candidates/{candidateId}/duplicate")
	public ResponseEntity<ApiResponse<CandidateResponse>> duplicate(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId,
			@Valid @RequestBody DuplicateCandidateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(candidateService.duplicate(candidateId, principal.id(), request.reason())));
	}

	@Operation(summary = "버전 이력 목록")
	@GetMapping("/candidates/{candidateId}/versions")
	public ApiResponse<List<CandidateVersionResponse>> versions(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId) {
		return ApiResponse.success(candidateService.versions(candidateId, principal.id()));
	}

	@Operation(summary = "과거 버전 복원 (새 버전으로 생성, 과거 기록 보존, 동시 수정 충돌 시 409)")
	@PostMapping("/candidates/{candidateId}/versions/restore")
	public ResponseEntity<ApiResponse<CandidateResponse>> restoreVersion(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId,
			@Valid @RequestBody RestoreCandidateVersionRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
				candidateService.restoreVersion(candidateId, principal.id(), request.versionId())));
	}

	@Operation(summary = "특정 버전 상세")
	@GetMapping("/candidates/versions/{versionId}")
	public ApiResponse<CandidateVersionResponse> version(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long versionId) {
		return ApiResponse.success(candidateService.version(versionId, principal.id()));
	}

	@Operation(summary = "후보 메모 3종 조회 (입력내용/검토사항/다음실험, 저장된 적 없으면 revision 0인 빈 값)")
	@GetMapping("/candidates/{candidateId}/memos")
	public ApiResponse<List<CandidateMemoResponse>> memos(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId) {
		return ApiResponse.success(candidateMemoService.list(candidateId, principal.id()));
	}

	@Operation(summary = "후보 메모 저장 (생성·수정 겸용, 낙관적 잠금 — expectedRevision 불일치 시 409)")
	@PutMapping("/candidates/{candidateId}/memos/{memoType}")
	public ApiResponse<CandidateMemoResponse> upsertMemo(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId,
			@PathVariable CandidateMemoType memoType,
			@Valid @RequestBody UpsertCandidateMemoRequest request) {
		return ApiResponse.success(candidateMemoService.upsert(candidateId, principal.id(), memoType, request));
	}

	@Operation(summary = "후보 현재 배합 자연어 수정 검토 (v2, BE-102) — 새 후보를 저장·승인하지 않으며 "
			+ "next_operation을 따라 재확인해야 한다. 근거 등록 여부와 무관하게 동작한다")
	@PostMapping("/candidates/{candidateId}/diagnostic-revise")
	public ApiResponse<ReviseCandidateResponse> reviseDiagnostic(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long candidateId,
			@Valid @RequestBody ReviseCandidateInstructionRequest request) {
		return ApiResponse.success(
				candidateDiagnosticReviseService.revise(candidateId, principal.id(), request.instruction()));
	}
}
