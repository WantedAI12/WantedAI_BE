package com.perfumeryaicore.domain.formula.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 후보의 현재 배합을 자연어 지시로 수정 검토한다(BE-102, v2 diagnostic revise 기반). */
public record ReviseCandidateInstructionRequest(

		@NotBlank
		@Size(max = 1000)
		String instruction
) {
}
