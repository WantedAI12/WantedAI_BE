package com.perfumeryaicore.domain.formula.dto.response;

import com.perfumeryaicore.global.common.CandidateStatus;

/**
 * {@code requestId}는 전체 요청이 공용으로 쓰는 자동 증가 ID라 API 식별용이다 - 화면에 "향 요청 #N"으로
 * 보여줄 때는 프로젝트 안에서 1부터 매긴 {@code requestNumber}를 쓴다.
 */
public record CandidateResponse(
		Long candidateId,
		Long requestId,
		int requestNumber,
		CandidateStatus status,
		CandidateVersionResponse currentVersion,
		Long derivedFromCandidateId,
		Long derivedFromVersionId,
		String derivationReason
) {
}
