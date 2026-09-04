package com.perfumeryaicore.domain.experiment.dto.response;

import com.perfumeryaicore.domain.experiment.entity.ExperimentStatusLog;
import com.perfumeryaicore.global.common.CandidateStatus;
import java.time.LocalDateTime;

public record ExperimentStatusLogResponse(
		Long candidateId,
		CandidateStatus status,
		Long changedBy,
		LocalDateTime changedAt
) {

	public static ExperimentStatusLogResponse from(ExperimentStatusLog log) {
		return new ExperimentStatusLogResponse(
				log.getCandidateId(), log.getStatus(), log.getChangedBy(), log.getCreatedAt());
	}
}
