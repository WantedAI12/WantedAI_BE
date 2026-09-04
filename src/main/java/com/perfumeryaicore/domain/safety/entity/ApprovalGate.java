package com.perfumeryaicore.domain.safety.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 안전·규제 승인 게이트 결정 한 건. 후보마다 이력이 쌓이며(불변 기록), 가장 최근 결정이
 * {@code APPROVED}여야 해당 후보를 실험 후보로 확정할 수 있다(experiment 도메인에서 사용 예정).
 */
@Entity
@Getter
@Table(
		name = "approval_gates",
		indexes = @Index(name = "idx_approval_gates_candidate_id", columnList = "candidate_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalGate extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "candidate_id", nullable = false)
	private Long candidateId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ApprovalDecision decision;

	@Lob
	private String comment;

	@Column(name = "reviewed_by", nullable = false)
	private Long reviewedBy;

	private ApprovalGate(Long candidateId, ApprovalDecision decision, String comment, Long reviewedBy) {
		this.candidateId = candidateId;
		this.decision = decision;
		this.comment = comment;
		this.reviewedBy = reviewedBy;
	}

	public static ApprovalGate register(Long candidateId, ApprovalDecision decision, String comment, Long reviewedBy) {
		return new ApprovalGate(candidateId, decision, comment, reviewedBy);
	}
}
