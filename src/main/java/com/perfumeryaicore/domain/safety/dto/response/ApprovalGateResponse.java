package com.perfumeryaicore.domain.safety.dto.response;

import com.perfumeryaicore.domain.safety.entity.ApprovalDecision;
import com.perfumeryaicore.domain.safety.entity.ApprovalGate;
import java.time.LocalDateTime;

public record ApprovalGateResponse(
		Long gateId,
		Long candidateId,
		ApprovalDecision decision,
		String comment,
		Long reviewedBy,
		LocalDateTime reviewedAt
) {

	public static ApprovalGateResponse from(ApprovalGate gate) {
		return new ApprovalGateResponse(
				gate.getId(), gate.getCandidateId(), gate.getDecision(), gate.getComment(),
				gate.getReviewedBy(), gate.getCreatedAt());
	}
}
