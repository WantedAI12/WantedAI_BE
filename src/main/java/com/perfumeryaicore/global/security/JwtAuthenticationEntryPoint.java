package com.perfumeryaicore.global.security;

import com.perfumeryaicore.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 인증되지 않은 요청이 보호된 리소스에 접근할 때 공통 포맷의 401 응답을 반환한다.
 * 필터 체인 단계에서 동작하므로 메시지 컨버터에 의존하지 않고 직접 JSON을 직렬화한다.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {

		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());

		String body = """
				{"success":false,"error":{"code":"%s","message":"%s"}}"""
				.formatted(ErrorCode.UNAUTHORIZED.name(), ErrorCode.UNAUTHORIZED.getMessage());
		response.getWriter().write(body);
	}
}
