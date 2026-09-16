package com.perfumeryaicore.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 이메일 등 백엔드가 사용자에게 보내는 링크를 구성할 때 쓰는 프런트엔드 기준 주소. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
		String frontendBaseUrl
) {
}
