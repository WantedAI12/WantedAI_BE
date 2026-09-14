package com.perfumeryaicore.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * BE-082/BE-081: {@code application-prod.yaml}에서 JWT_SECRET/DB_USERNAME/DB_PASSWORD/
 * CORS_ALLOWED_ORIGINS가 개발용 기본값 없이 순수 플레이스홀더({@code ${VAR}}, {@code :} 기본값
 * 구문 없음)로만 선언돼 있는지 직접 검증한다.
 *
 * <p>실제로 {@code SPRING_PROFILES_ACTIVE=prod}로 전체 컨텍스트를 띄워 "환경변수가 없으면
 * 기동이 실패한다"를 증명하는 방법도 검토했지만, 테스트 환경의 공유 H2 인스턴스는 사용자명을
 * 검증하지 않고 어떤 값이든 받아들여서(플레이스홀더 문자열이 그대로 사용자명으로 들어가도
 * 연결이 됨) 신뢰할 수 없었다. 대신 원인(파일에 기본값이 없다는 것) 자체를 직접 확인한다 —
 * 실제 배포 환경의 Spring은 이 파일을 그대로 읽어 해당 환경변수가 없으면
 * "Could not resolve placeholder"로 기동을 멈춘다.
 */
class ProdProfileStartupTest {

	@SuppressWarnings("unchecked")
	@Test
	void prod_profile_declares_the_required_environment_values_without_a_fallback_default() {
		Map<String, Object> root;
		try (InputStream in = getClass().getResourceAsStream("/application-prod.yaml")) {
			root = new Yaml().load(in);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}

		Map<String, Object> spring = (Map<String, Object>) root.get("spring");
		Map<String, Object> datasource = (Map<String, Object>) spring.get("datasource");
		Map<String, Object> jwt = (Map<String, Object>) root.get("jwt");
		Map<String, Object> cors = (Map<String, Object>) root.get("cors");

		// ":"로 이어지는 기본값 구문(${VAR:default})이 없다 - 실제 환경변수가 없으면 그대로
		// 미해결 플레이스홀더로 남아 Spring이 기동을 실패시킨다.
		assertThat(datasource.get("username")).isEqualTo("${DB_USERNAME}");
		assertThat(datasource.get("password")).isEqualTo("${DB_PASSWORD}");
		assertThat(jwt.get("secret")).isEqualTo("${JWT_SECRET}");
		assertThat(cors.get("allowed-origins")).isEqualTo("${CORS_ALLOWED_ORIGINS}");
	}

	@SuppressWarnings("unchecked")
	@Test
	void prod_profile_disables_verbose_sql_logging_and_forbids_implicit_schema_changes() {
		Map<String, Object> root;
		try (InputStream in = getClass().getResourceAsStream("/application-prod.yaml")) {
			root = new Yaml().load(in);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}

		Map<String, Object> jpa = (Map<String, Object>) ((Map<String, Object>) root.get("spring")).get("jpa");
		Map<String, Object> hibernate = (Map<String, Object>) jpa.get("hibernate");

		assertThat(hibernate.get("ddl-auto")).isEqualTo("validate");
		assertThat(jpa.get("show-sql")).isEqualTo(Boolean.FALSE);
	}
}
