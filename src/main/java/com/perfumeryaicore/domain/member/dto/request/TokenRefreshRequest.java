package com.perfumeryaicore.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(

		@NotBlank
		String refreshToken
) {
}
