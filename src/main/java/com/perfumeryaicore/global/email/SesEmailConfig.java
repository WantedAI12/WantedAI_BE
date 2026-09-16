package com.perfumeryaicore.global.email;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * SES 클라이언트 구성. {@link com.perfumeryaicore.global.storage.S3StorageConfig}와 같은 이유로
 * {@link DefaultCredentialsProvider}를 쓴다 — 액세스 키를 코드나 설정 파일에 직접 넣지 않는다.
 */
@Configuration
@EnableConfigurationProperties(SesEmailProperties.class)
public class SesEmailConfig {

	@Bean
	public SesClient sesClient(SesEmailProperties properties) {
		return SesClient.builder()
				.region(Region.of(properties.region()))
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
	}
}
