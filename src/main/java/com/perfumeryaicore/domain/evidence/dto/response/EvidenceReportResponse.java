package com.perfumeryaicore.domain.evidence.dto.response;

import com.perfumeryaicore.domain.job.entity.JobStatus;
import tools.jackson.databind.JsonNode;

/**
 * @param fileUrl PDF가 S3에 저장된 뒤에만 값이 있다 — {@link com.perfumeryaicore.domain.evidence.service.EvidenceReportService#get}이
 *                반환하는 15분 유효 presigned 다운로드 URL. 보고서가 아직 생성 중이거나 실패했으면 {@code null}.
 */
public record EvidenceReportResponse(
		Long reportId,
		Long candidateId,
		JobStatus status,
		JsonNode reportData,
		String fileUrl
) {
}
