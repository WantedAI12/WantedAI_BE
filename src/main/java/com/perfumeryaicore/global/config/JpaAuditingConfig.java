package com.perfumeryaicore.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화 ({@code @CreatedDate}, {@code @LastModifiedDate}).
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
