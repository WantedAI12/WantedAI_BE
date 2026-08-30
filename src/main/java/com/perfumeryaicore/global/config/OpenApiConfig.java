package com.perfumeryaicore.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi 설정. 모든 API에 Bearer(JWT) 인증 스킴을 노출한다.
 */
@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("Perfumery AI Core API")
						.version("1.0")
						.description("자연어 향 의도를 안전·가격·가용성 제약이 반영된 정량 조향식과 "
								+ "검증 가능한 연구 워크플로로 변환하는 웹 기반 조향 R&D 운영체제 API."))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
				.components(new Components().addSecuritySchemes(BEARER_SCHEME,
						new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")));
	}
}
