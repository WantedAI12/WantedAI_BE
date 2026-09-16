package com.perfumeryaicore.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3(호환) 오브젝트 스토리지 설정. 자격증명은 여기서 다루지 않는다 — AWS SDK 기본 자격증명
 * 체인(EC2 IAM 인스턴스 역할 → 환경변수 → 자격증명 파일 순)을 그대로 쓴다.
 */
@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(
		String bucket,
		String region
) {
}
