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
				@Index(name = "idx_supply_changes_ingredient", columnList = "ingredient_external_id"),
				@Index(name = "idx_supply_changes_source_id", columnList = "change_source_id", unique = true)
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

	/**
	 * BE-070: 가격 외 변경(안전·규제·식별·재고 등)의 필드별 이전/새 값. 어떤 필드가 바뀌었는지는
	 * {@code changedField}에 자유 텍스트로 남긴다(예: {@code ifra_restriction_category},
	 * {@code cas_number}, {@code moq_kg}) - 변경 유형별로 스키마를 늘리는 대신, 필드명 자체를
	 * 값으로 취급해 유형이 늘어나도 엔티티를 매번 바꾸지 않아도 되게 한다.
	 */
	@Column(name = "changed_field", length = 100)
	private String changedField;

	@Lob
	@Column(name = "previous_value")
	private String previousValue;

	@Lob
	@Column(name = "new_value")
	private String newValue;

	/**
	 * BE-070: 외부 원천(공급사 시스템 등)의 변경 이벤트 ID. 있으면 동일 원천 ID 중복 수신 시
	 * 중복 이벤트를 만들지 않고 기존 이벤트를 그대로 반환한다(Job의 idempotencyKey와 같은 패턴).
	 */
	@Column(name = "change_source_id", length = 200)
	private String changeSourceId;

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
			Double previousPricePerKg, Double newPricePerKg, String changedField, String previousValue,
			String newValue, String changeSourceId, String note, Long reportedBy) {
		this.projectId = projectId;
		this.ingredientExternalId = ingredientExternalId;
		this.changeType = changeType;
		this.previousPricePerKg = previousPricePerKg;
		this.newPricePerKg = newPricePerKg;
		this.changedField = changedField;
		this.previousValue = previousValue;
		this.newValue = newValue;
		this.changeSourceId = changeSourceId;
		this.note = note;
		this.reportedBy = reportedBy;
		this.analysisStatus = JobStatus.SUCCEEDED;
		this.affectedCandidateCount = 0;
	}

	public static SupplyChange create(Long projectId, String ingredientExternalId, SupplyChangeType changeType,
			Double previousPricePerKg, Double newPricePerKg, String changedField, String previousValue,
			String newValue, String changeSourceId, String note, Long reportedBy) {
		return new SupplyChange(projectId, ingredientExternalId, changeType, previousPricePerKg, newPricePerKg,
				changedField, previousValue, newValue, changeSourceId, note, reportedBy);
	}

	public void recordAffectedCount(int count) {
		this.affectedCandidateCount = count;
	}
}
