package com.perfumeryaicore.domain.evidence.dto.response;

import com.perfumeryaicore.domain.evidence.entity.BlindLevel;
import com.perfumeryaicore.domain.evidence.entity.SensoryTestStatus;
import java.time.LocalDateTime;
import java.util.List;

public record SensoryTestResponse(
		Long testId,
		Long candidateId,
		Long candidateVersionId,
		String planDetail,
		String protocolVersion,
		String sampleCode,
		String batchLot,
		Integer panelSize,
		BlindLevel blindLevel,
		SensoryTestStatus status,
		List<SensoryTestResultResponse> results,
		LocalDateTime createdAt,
		boolean published,
		Long publishedBy,
		LocalDateTime publishedAt
) {
}
