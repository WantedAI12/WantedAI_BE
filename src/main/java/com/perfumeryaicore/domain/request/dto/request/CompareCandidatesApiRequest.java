package com.perfumeryaicore.domain.request.dto.request;

import com.perfumeryaicore.global.client.dto.StoredCandidate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * {@code /v2/formulas/compare} 요청. 각 {@link StoredCandidate}는 호출부가 이전에
 * {@code diagnostic-evaluate}/{@code diagnostic-reassess}로 받은 평가 결과를 그대로 담아
 * 보낸다 - BE는 이 스냅샷을 저장하지 않고 그대로 중계한다(Modal 스키마: 2~10개).
 */
public record CompareCandidatesApiRequest(

		@NotNull
		@Size(min = 2, max = 10)
		List<StoredCandidate> candidates
) {
}
