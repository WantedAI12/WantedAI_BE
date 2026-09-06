package com.perfumeryaicore.domain.supply.entity;

import com.perfumeryaicore.domain.job.entity.JobStatus;
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
 * 원료 가격/공급 조건 변경 이벤트.
 *
 * <p>영향 분석에는 외부 AI가 필요 없다(조향 AI에 해당 엔드포인트가 없음). "이 원료를 쓰는 후보"는
 * {@code candidate_version_ingredients} 조회로 즉시 계산되므로, §3 Request와 같은 이유로
 * 등록 시 <b>동기적으로</b> 분석해 영향 후보를 확정한다. {@code SUPPLY_IMPACT_ANALYSIS} Job 타입은
 * 사용하지 않는다(문서 델타 반영 대상). {@code analysisStatus}는 스키마 호환을 위해 남겨 두며 항상 SUCCEEDED다.
 */
@Entity
@Getter
@Table(
		name = "supply_changes",
		indexes = {
				@Index(name = "idx_supply_changes_project_id", columnList = "project_id"),
				@Index(name = "idx_supply_changes_ingredient", columnList = "ingredient_external_id")
		}
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplyChange extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id", nullable = false)
	private Long projectId;

	@Column(name = "ingredient_external_id", nullable = false, length = 100)
	private String ingredientExternalId;

	@Enumerated(EnumType.STRING)
	@Column(name = "change_type", nullable = false, length = 30)
	private SupplyChangeType changeType;

	@Column(name = "previous_price_per_kg")
	private Double previousPricePerKg;

	@Column(name = "new_price_per_kg")
	private Double newPricePerKg;

	@Lob
	@Column(name = "note")
	private String note;

	@Enumerated(EnumType.STRING)
	@Column(name = "analysis_status", nullable = false, length = 20)
	private JobStatus analysisStatus;

	@Column(name = "affected_candidate_count", nullable = false)
	private int affectedCandidateCount;

	@Column(name = "reported_by", nullable = false)
	private Long reportedBy;

	private SupplyChange(Long projectId, String ingredientExternalId, SupplyChangeType changeType,
			Double previousPricePerKg, Double newPricePerKg, String note, Long reportedBy) {
		this.projectId = projectId;
		this.ingredientExternalId = ingredientExternalId;
		this.changeType = changeType;
		this.previousPricePerKg = previousPricePerKg;
		this.newPricePerKg = newPricePerKg;
		this.note = note;
		this.reportedBy = reportedBy;
		this.analysisStatus = JobStatus.SUCCEEDED;
		this.affectedCandidateCount = 0;
	}

	public static SupplyChange create(Long projectId, String ingredientExternalId, SupplyChangeType changeType,
			Double previousPricePerKg, Double newPricePerKg, String note, Long reportedBy) {
		return new SupplyChange(projectId, ingredientExternalId, changeType,
				previousPricePerKg, newPricePerKg, note, reportedBy);
	}

	public void recordAffectedCount(int count) {
		this.affectedCandidateCount = count;
	}
}
