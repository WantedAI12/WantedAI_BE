package com.perfumeryaicore.domain.ingredient.controller;

import com.perfumeryaicore.domain.ingredient.dto.request.CatalogSyncRequest;
import com.perfumeryaicore.domain.ingredient.dto.response.CatalogSyncResultResponse;
import com.perfumeryaicore.domain.ingredient.dto.response.IngredientDetailResponse;
import com.perfumeryaicore.domain.ingredient.dto.response.IngredientResponse;
import com.perfumeryaicore.domain.ingredient.service.CatalogSyncService;
import com.perfumeryaicore.domain.ingredient.service.IngredientQueryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Ingredient")
@RestController
@RequiredArgsConstructor
public class IngredientController {

	private final IngredientQueryService ingredientQueryService;
	private final CatalogSyncService catalogSyncService;

	@Operation(summary = "원료 목록/검색 (생성된 조향식에서 관측된 원료의 로컬 미러)")
	@GetMapping("/ingredients")
	public ApiResponse<List<IngredientResponse>> list(
			@AuthenticationPrincipal MemberPrincipal principal,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String pyramid) {
		return ApiResponse.success(ingredientQueryService.list(principal.id(), query, pyramid));
	}

	@Operation(summary = "카탈로그 동기화 실행 (비동기) — FRAGRANCE_RND / ORG_ADMIN")
	@PostMapping("/ingredients/catalog-sync")
	public ResponseEntity<ApiResponse<JobResponse>> catalogSync(
			@AuthenticationPrincipal MemberPrincipal principal,
			@Valid @RequestBody CatalogSyncRequest request) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(ApiResponse.success(catalogSyncService.sync(principal.id(), request.projectId())));
	}

	@Operation(summary = "카탈로그 동기화 작업 상태·통계 조회")
	@GetMapping("/ingredients/catalog-sync/{jobId}")
	public ApiResponse<CatalogSyncResultResponse> catalogSyncResult(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long jobId) {
		return ApiResponse.success(catalogSyncService.getResult(jobId, principal.id()));
	}

	@Operation(summary = "원료 상세 (관측 단가·가용성·사용 후보)")
	@GetMapping("/ingredients/{ingredientId}")
	public ApiResponse<IngredientDetailResponse> get(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable String ingredientId) {
		return ApiResponse.success(ingredientQueryService.get(principal.id(), ingredientId));
	}
}
