package com.perfumeryaicore.domain.request.dto.request;

import com.perfumeryaicore.global.client.dto.StoredCandidate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code /v2/briefs/revise} 요청. 새 후보를 저장·승인하지 않는다 - 자연어 지시로 수정된
 * 입력/검토 결과만 돌려준다(README 6번). {@code source}는 {@link CompareCandidatesApiRequest}와
 * 같은 이유로 호출부가 이전에 받은 평가 결과 스냅샷을 그대로 담아 보낸다.
 */
public record ReviseCandidateApiRequest(

		@NotNull
		StoredCandidate source,

		@NotBlank
		@Size(max = 1000)
		String instruction
) {
}
