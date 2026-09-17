package com.perfumeryaicore.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer <access-token>} 헤더를 검증해 SecurityContext에 인증 정보를 채운다.
 * 토큰이 없거나 유효하지 않으면 컨텍스트를 비운 채 통과시키고, 보호된 경로는
 * {@link JwtAuthenticationEntryPoint}가 401로 응답한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider tokenProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		String token = resolveToken(request);
		if (token != null) {
			MemberPrincipal principal = tokenProvider.parse(token);
			if (principal != null) {
				UsernamePasswordAuthenticationToken authentication =
						new UsernamePasswordAuthenticationToken(principal, null, List.of());
				authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(authentication);
			}
		}
		filterChain.doFilter(request, response);
	}

	private String resolveToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
			return header.substring(BEARER_PREFIX.length());
		}
		// SSE 스트림(/jobs/{id}/stream)만 예외: 브라우저 EventSource는 커스텀 헤더를 못 보내므로
		// 쿼리 파라미터로도 받는다(AI 개발팀 SSE 제안, 2026-09-17). 토큰이 서버 접근 로그에
		// 남을 수 있어 다른 엔드포인트로는 넓히지 않는다.
		if (request.getRequestURI().endsWith("/stream")) {
			String queryToken = request.getParameter("access_token");
			if (StringUtils.hasText(queryToken)) {
				return queryToken;
			}
		}
		return null;
	}
}
