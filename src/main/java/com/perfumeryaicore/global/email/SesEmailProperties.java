package com.perfumeryaicore.global.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AWS SES 발신 설정. 자격증명은 여기서 다루지 않는다 — {@link com.perfumeryaicore.global.storage.S3StorageConfig}와
 * 같은 AWS SDK 기본 자격증명 체인(EC2 IAM 인스턴스 역할 → 환경변수 → 자격증명 파일 순)을 그대로 쓴다.
 */
@ConfigurationProperties(prefix = "aws.ses")
public record SesEmailProperties(
		String region,
		String senderEmail
) {
}
