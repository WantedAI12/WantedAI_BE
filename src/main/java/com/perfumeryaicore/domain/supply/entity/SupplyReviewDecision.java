package com.perfumeryaicore.domain.supply.entity;

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
 * 영향 후보 재검토 후속 결정(조향식 유지/수정/폐기). 후보에 종속되며, 특정 공급 변경과 연결될 수 있다.
 */
@Entity
@Getter
@Table(
		name = "supply_review_decisions",
		indexes = @Index(name = "idx_srd_candidate_id", columnList = "candidate_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplyReviewDecision extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "candidate_id", nullable = false)
	private Long candidateId;

	@Column(name = "supply_change_id")
	private Long supplyChangeId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SupplyReviewDecisionType decision;

	@Lob
	@Column(nullable = false)
	private String rationale;

	@Column(name = "decided_by", nullable = false)
	private Long decidedBy;

	private SupplyReviewDecision(Long candidateId, Long supplyChangeId, SupplyReviewDecisionType decision,
			String rationale, Long decidedBy) {
		this.candidateId = candidateId;
		this.supplyChangeId = supplyChangeId;
		this.decision = decision;
		this.rationale = rationale;
		this.decidedBy = decidedBy;
	}

	public static SupplyReviewDecision record(Long candidateId, Long supplyChangeId,
			SupplyReviewDecisionType decision, String rationale, Long decidedBy) {
		return new SupplyReviewDecision(candidateId, supplyChangeId, decision, rationale, decidedBy);
	}
}
