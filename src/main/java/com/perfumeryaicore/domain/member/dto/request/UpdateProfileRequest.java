package com.perfumeryaicore.domain.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

		@NotBlank
		@Size(max = 50)
		String name
) {
}
