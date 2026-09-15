package com.perfumeryaicore.global.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * BE-083: db/migration의 Flyway 스크립트로 만든 스키마가 JPA 엔티티와 일치하는지 검증한다.
 *
 * <p>빈 MySQL에 마이그레이션을 적용한 뒤 운영과 같은 {@code ddl-auto: validate}로 컨텍스트를 띄운다 —
 * 엔티티를 바꾸고 {@code V{n}__*.sql}을 추가하지 않으면 여기서 기동이 실패한다(= 운영 기동 실패를 PR 단계에서 잡음).
 *
 * <p>H2는 MySQL 타입(enum, longtext 등)을 그대로 재현하지 못해 실제 MySQL이 필요하다. 그래서
 * {@code SCHEMA_CHECK_DB_URL}이 있을 때만 실행한다(CI는 MySQL 서비스 컨테이너로 제공, 로컬에선 기본 건너뜀).
 */
@EnabledIfEnvironmentVariable(named = "SCHEMA_CHECK_DB_URL", matches = ".+")
@SpringBootTest(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
		"spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver"
})
class FlywaySchemaValidationTest {

	@DynamicPropertySource
	static void mysql(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> System.getenv("SCHEMA_CHECK_DB_URL"));
		registry.add("spring.datasource.username", () -> System.getenv("SCHEMA_CHECK_DB_USERNAME"));
		registry.add("spring.datasource.password", () -> System.getenv("SCHEMA_CHECK_DB_PASSWORD"));
	}

	@Test
	void flyway_migrations_produce_a_schema_that_matches_the_jpa_entities() {
		// 컨텍스트 기동 성공 = Flyway 적용 + Hibernate 스키마 검증 통과
	}
}
