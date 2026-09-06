package com.perfumeryaicore.domain.project.controller;

import com.perfumeryaicore.domain.project.dto.request.AddProjectMemberRequest;
import com.perfumeryaicore.domain.project.dto.request.ChangeProjectMemberRoleRequest;
import com.perfumeryaicore.domain.project.dto.request.CreateProjectRequest;
import com.perfumeryaicore.domain.project.dto.request.UpdateProjectRequest;
import com.perfumeryaicore.domain.project.dto.response.ProjectMemberResponse;
import com.perfumeryaicore.domain.project.dto.response.ProjectResponse;
import com.perfumeryaicore.domain.project.service.ProjectService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Project")
@RestController
@RequiredArgsConstructor
public class ProjectController {

	private final ProjectService projectService;

	@Operation(summary = "프로젝트(테넌트) 생성 — 생성자는 ORG_ADMIN으로 자동 등록")
	@PostMapping("/projects")
	public ResponseEntity<ApiResponse<ProjectResponse>> create(
			@AuthenticationPrincipal MemberPrincipal principal,
			@Valid @RequestBody CreateProjectRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(projectService.create(principal.id(), request)));
	}

	@Operation(summary = "내가 속한 프로젝트 목록")
	@GetMapping("/projects")
	public ApiResponse<List<ProjectResponse>> listMine(@AuthenticationPrincipal MemberPrincipal principal) {
		return ApiResponse.success(projectService.listMine(principal.id()));
	}

	@Operation(summary = "프로젝트 상세 (내 역할·멤버 수 포함)")
	@GetMapping("/projects/{projectId}")
	public ApiResponse<ProjectResponse> get(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long projectId) {
		return ApiResponse.success(projectService.get(projectId, principal.id()));
	}

	@Operation(summary = "프로젝트 정보 수정 (ORG_ADMIN / PROJECT_MANAGER)")
	@PatchMapping("/projects/{projectId}")
	public ApiResponse<ProjectResponse> update(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long projectId,
			@Valid @RequestBody UpdateProjectRequest request) {
		return ApiResponse.success(projectService.update(projectId, principal.id(), request));
	}

	@Operation(summary = "멤버 초대 및 역할 배정 (ORG_ADMIN / PROJECT_MANAGER)")
	@PostMapping("/projects/{projectId}/members")
	public ResponseEntity<ApiResponse<ProjectMemberResponse>> addMember(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long projectId,
			@Valid @RequestBody AddProjectMemberRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(projectService.addMember(projectId, principal.id(), request)));
	}

	@Operation(summary = "멤버 목록·역할 조회 (프로젝트 멤버)")
	@GetMapping("/projects/{projectId}/members")
	public ApiResponse<List<ProjectMemberResponse>> listMembers(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long projectId) {
		return ApiResponse.success(projectService.listMembers(projectId, principal.id()));
	}

	@Operation(summary = "멤버 역할 변경 (ORG_ADMIN / PROJECT_MANAGER)")
	@PatchMapping("/projects/{projectId}/members/{memberId}")
	public ApiResponse<ProjectMemberResponse> changeMemberRole(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long projectId,
			@PathVariable Long memberId,
			@Valid @RequestBody ChangeProjectMemberRoleRequest request) {
		return ApiResponse.success(
				projectService.changeMemberRole(projectId, principal.id(), memberId, request));
	}

	@Operation(summary = "멤버 제거 (ORG_ADMIN / PROJECT_MANAGER)")
	@DeleteMapping("/projects/{projectId}/members/{memberId}")
	public ResponseEntity<Void> removeMember(
			@AuthenticationPrincipal MemberPrincipal principal,
			@PathVariable Long projectId,
			@PathVariable Long memberId) {
		projectService.removeMember(projectId, principal.id(), memberId);
		return ResponseEntity.noContent().build();
	}
}
