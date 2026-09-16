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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공급 변경 등록 시점에 확정한 영향 후보 스냅샷. 이후 조향식이 바뀌어도 "그때 무엇이 영향받았는지"가
 * 감사 목적상 그대로 남도록 별도 저장한다.
 */
@Entity
@Getter
@Table(
		name = "supply_change_affected_candidates",
		indexes = @Index(name = "idx_scac_supply_change_id", columnList = "supply_change_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplyChangeAffectedCandidate extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "supply_change_id", nullable = false)
	private Long supplyChangeId;

	@Column(name = "candidate_id", nullable = false)
	private Long candidateId;

	@Column(name = "candidate_version_id", nullable = false)
	private Long candidateVersionId;

	/** 영향 원료가 그 조향식에서 차지하던 농축액 기준 배합 비율(%). AI 응답값 그대로. */
	@Column(name = "ingredient_concentrate_percent")
	private Double ingredientConcentratePercent;

	@Enumerated(EnumType.STRING)
	@Column(name = "review_status", nullable = false, length = 20)
	private SupplyReviewStatus reviewStatus;

	private SupplyChangeAffectedCandidate(Long supplyChangeId, Long candidateId, Long candidateVersionId,
			Double ingredientConcentratePercent) {
		this.supplyChangeId = supplyChangeId;
		this.candidateId = candidateId;
		this.candidateVersionId = candidateVersionId;
		this.ingredientConcentratePercent = ingredientConcentratePercent;
		this.reviewStatus = SupplyReviewStatus.PENDING_REVIEW;
	}

	public static SupplyChangeAffectedCandidate of(Long supplyChangeId, Long candidateId, Long candidateVersionId,
			Double ingredientConcentratePercent) {
		return new SupplyChangeAffectedCandidate(supplyChangeId, candidateId, candidateVersionId,
				ingredientConcentratePercent);
	}

	public void markReviewed() {
		this.reviewStatus = SupplyReviewStatus.REVIEWED;
	}
}
