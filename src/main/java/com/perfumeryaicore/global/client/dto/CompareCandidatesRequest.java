package com.perfumeryaicore.global.client.dto;

import java.util.List;

/** Modal {@code POST /v2/formulas/compare} 요청 본문. 2~10개의 저장 후보 스냅샷을 비교한다. */
public record CompareCandidatesRequest(
		List<StoredCandidate> candidates
) {
}
