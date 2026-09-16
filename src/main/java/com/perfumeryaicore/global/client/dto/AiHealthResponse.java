package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modal {@code GET /health} 응답. 운영 시 서버 가용성과 배포 Wheel 해시 확인에 사용한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiHealthResponse(

		@JsonProperty("status")
		String status,

		@JsonProperty("runtime")
		String runtime,

		@JsonProperty("gpu_required")
		Boolean gpuRequired,

		@JsonProperty("wheel_sha256")
		String wheelSha256,

		@JsonProperty("registry_sha256")
		String registrySha256
) {

	public boolean isHealthy() {
		return "ok".equals(status) && Boolean.FALSE.equals(gpuRequired);
	}
}
