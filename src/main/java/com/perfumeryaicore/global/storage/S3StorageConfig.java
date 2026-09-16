package com.perfumeryaicore.global.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 클라이언트 구성. 자격증명은 {@link DefaultCredentialsProvider}(EC2 IAM 인스턴스 역할 →
 * 환경변수 {@code AWS_ACCESS_KEY_ID}/{@code AWS_SECRET_ACCESS_KEY} → 자격증명 파일 순으로 탐색)를
 * 쓴다. 액세스 키를 코드나 설정 파일에 직접 넣지 않는다.
 */
@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3StorageConfig {

	@Bean
	public S3Client s3Client(S3Properties properties) {
		return S3Client.builder()
				.region(Region.of(properties.region()))
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
	}

	@Bean
	public S3Presigner s3Presigner(S3Properties properties) {
		return S3Presigner.builder()
				.region(Region.of(properties.region()))
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
	}
}
