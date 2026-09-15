package com.perfumeryaicore.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @Scheduled} 주기 작업(만료 토큰 정리 등)을 활성화한다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
