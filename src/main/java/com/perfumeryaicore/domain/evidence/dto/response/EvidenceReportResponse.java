package com.perfumeryaicore.domain.evidence.dto.response;

import com.perfumeryaicore.domain.job.entity.JobStatus;
import tools.jackson.databind.JsonNode;

/**
 * @param fileUrl 항상 {@code null} — PDF 렌더링·파일 스토리지가 아직 없다. {@code reportData}가
 *                지금은 사실상의 보고서다.
 */
public record EvidenceReportResponse(
		Long reportId,
		Long candidateId,
		JobStatus status,
		JsonNode reportData,
		String fileUrl
) {
}
