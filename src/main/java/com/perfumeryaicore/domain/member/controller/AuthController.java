package com.perfumeryaicore.domain.member.controller;

import com.perfumeryaicore.domain.member.dto.request.ForgotPasswordRequest;
import com.perfumeryaicore.domain.member.dto.request.LoginRequest;
import com.perfumeryaicore.domain.member.dto.request.LogoutRequest;
import com.perfumeryaicore.domain.member.dto.request.ResetPasswordRequest;
import com.perfumeryaicore.domain.member.dto.request.SignupRequest;
import com.perfumeryaicore.domain.member.dto.request.TokenRefreshRequest;
import com.perfumeryaicore.domain.member.dto.response.MemberResponse;
import com.perfumeryaicore.domain.member.dto.response.TokenResponse;
import com.perfumeryaicore.domain.member.service.AuthService;
import com.perfumeryaicore.domain.member.service.PasswordResetService;
import com.perfumeryaicore.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final PasswordResetService passwordResetService;

	@Operation(summary = "이메일/비밀번호로 회원가입")
	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<MemberResponse>> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(authService.signup(request)));
	}

	@Operation(summary = "로그인, Access/Refresh Token 발급")
	@PostMapping("/login")
	public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.success(authService.login(request));
	}

	@Operation(summary = "게스트 모드 시작 — 가입 없이 즉시 임시 계정으로 로그인, 일정 시간 후 자동 만료")
	@PostMapping("/guest")
	public ResponseEntity<ApiResponse<TokenResponse>> guestLogin() {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(authService.guestLogin()));
	}

	@Operation(summary = "Refresh Token으로 Access Token 재발급")
	@PostMapping("/refresh")
	public ApiResponse<TokenResponse> refresh(@Valid @RequestBody TokenRefreshRequest request) {
		return ApiResponse.success(authService.refresh(request.refreshToken()));
	}

	@Operation(summary = "Refresh Token 무효화")
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request.refreshToken());
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "비밀번호 재설정 요청 (비로그인) — 가입 여부와 무관하게 항상 같은 응답")
	@PostMapping("/password/forgot")
	public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
		passwordResetService.forgotPassword(request.email());
		return ResponseEntity.accepted().build();
	}

	@Operation(summary = "비밀번호 재설정 (비로그인, 토큰 1회용) — 완료 시 기존 세션 전체 종료")
	@PostMapping("/password/reset")
	public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		passwordResetService.resetPassword(request.token(), request.newPassword());
		return ResponseEntity.noContent().build();
	}
}
