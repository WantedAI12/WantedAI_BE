package com.perfumeryaicore.domain.formula.dto.response;

import com.perfumeryaicore.global.common.CandidateStatus;

public record CandidateResponse(
		Long candidateId,
		Long requestId,
		CandidateStatus status,
		CandidateVersionResponse currentVersion
) {
}
