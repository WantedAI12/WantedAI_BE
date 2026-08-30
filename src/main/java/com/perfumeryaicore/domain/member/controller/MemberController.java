package com.perfumeryaicore.domain.member.controller;

import com.perfumeryaicore.domain.member.dto.request.ChangePasswordRequest;
import com.perfumeryaicore.domain.member.dto.request.UpdateProfileRequest;
import com.perfumeryaicore.domain.member.dto.response.MemberResponse;
import com.perfumeryaicore.domain.member.service.MemberService;
import com.perfumeryaicore.global.response.ApiResponse;
import com.perfumeryaicore.global.security.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Member")
@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;

	@Operation(summary = "내 프로필/소속 프로젝트/역할 조회")
	@GetMapping("/me")
	public ApiResponse<MemberResponse> getMe(@AuthenticationPrincipal MemberPrincipal principal) {
		return ApiResponse.success(memberService.getMe(principal.id()));
	}

	@Operation(summary = "프로필 수정")
	@PatchMapping("/me")
	public ApiResponse<MemberResponse> updateProfile(
			@AuthenticationPrincipal MemberPrincipal principal,
			@Valid @RequestBody UpdateProfileRequest request) {
		return ApiResponse.success(memberService.updateProfile(principal.id(), request));
	}

	@Operation(summary = "비밀번호 변경")
	@PatchMapping("/me/password")
	public ResponseEntity<Void> changePassword(
			@AuthenticationPrincipal MemberPrincipal principal,
			@Valid @RequestBody ChangePasswordRequest request) {
		memberService.changePassword(principal.id(), request);
		return ResponseEntity.noContent().build();
	}
}
