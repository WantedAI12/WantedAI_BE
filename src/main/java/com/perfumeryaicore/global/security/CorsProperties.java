package com.perfumeryaicore.global.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 허용 오리진 설정(BE-081). {@code cors.allowed-origins} 프로퍼티(콤마 구분)에 바인딩된다.
 *
 * @param allowedOrigins 교차 출처 요청을 허용할 프런트엔드 오리진 목록
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
		List<String> allowedOrigins
) {
}
