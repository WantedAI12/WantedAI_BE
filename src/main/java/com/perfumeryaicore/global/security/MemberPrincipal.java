package com.perfumeryaicore.global.security;

/**
 * 인증된 요청의 주체. 컨트롤러에서 {@code @AuthenticationPrincipal MemberPrincipal}로 주입된다.
 * 프로젝트별 역할(Role)은 project 도메인 구현 시 확장한다.
 */
public record MemberPrincipal(Long id, String email) {
}
