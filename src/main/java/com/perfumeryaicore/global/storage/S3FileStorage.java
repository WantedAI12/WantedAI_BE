package com.perfumeryaicore.global.storage;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * S3 업로드·다운로드 URL 발급. 버킷은 퍼블릭 액세스를 막아두므로(private) 다운로드는 매번
 * 짧게 유효한 서명 URL로 내준다 — DB에는 오브젝트 키만 저장하고 URL은 저장하지 않는다
 * (URL을 저장해두면 만료된 뒤에도 그대로 내려가는 문제가 생긴다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3FileStorage {

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final S3Properties properties;

	public void upload(String key, byte[] content, String contentType) {
		s3Client.putObject(
				PutObjectRequest.builder()
						.bucket(properties.bucket())
						.key(key)
						.contentType(contentType)
						.build(),
				RequestBody.fromBytes(content));
		log.info("[S3] uploaded key={} bytes={}", key, content.length);
	}

	/** {@code ttl} 동안만 유효한 다운로드 URL을 새로 발급한다. */
	public String presignedGetUrl(String key, Duration ttl) {
		GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
				.signatureDuration(ttl)
				.getObjectRequest(GetObjectRequest.builder()
						.bucket(properties.bucket())
						.key(key)
						.build())
				.build();
		return s3Presigner.presignGetObject(presignRequest).url().toString();
	}
}
