package com.perfumeryaicore.domain.evidence.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.Length;

/**
 * 블라인드 관능 검증 결과 한 건. {@code resultData}는 패널·측정 방식이 표준화되어 있지 않아
 * 구조를 강제하지 않고 제출된 JSON을 그대로 저장한다. {@code correlationWithPrediction}은
 * 백엔드가 계산하지 않는다 — 감각과학 담당자가 직접 산출해 제출한 값이다(임의 산출 금지).
 */
@Entity
@Getter
@Table(
		name = "sensory_test_results",
		indexes = @Index(name = "idx_sensory_test_results_test_id", columnList = "sensory_test_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensoryTestResult extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sensory_test_id", nullable = false)
	private Long sensoryTestId;

	@Lob
	@Column(name = "result_data", length = Length.LONG32)
	private String resultData;

	@Column(name = "correlation_with_prediction")
	private Double correlationWithPrediction;

	/** BE-055: 평가자 익명 식별자(예: {@code P07}). 실제 회원 ID가 아니다 - 블라인드를 깨지 않는다. */
	@Column(name = "panelist_identifier", length = 50)
	private String panelistIdentifier;

	@Column(name = "timepoint_minutes")
	private Integer timepointMinutes;

	@Column(name = "scale_min")
	private Double scaleMin;

	@Column(name = "scale_max")
	private Double scaleMax;

	@Lob
	@Column(name = "missing_reason", length = Length.LONG32)
	private String missingReason;

	/**
	 * BE-055: 이 결과가 이전 결과를 수정한 것이면 그 원본 ID. 수정은 원본 레코드를 고치는 대신
	 * 항상 새 행을 만든다 - 원본은 그대로 남고 어떤 값이 어떻게 바뀌었는지 추적할 수 있다.
	 */
	@Column(name = "supersedes_result_id")
	private Long supersedesResultId;

	@Column(name = "recorded_by", nullable = false)
	private Long recordedBy;

	private SensoryTestResult(Long sensoryTestId, String resultData, Double correlationWithPrediction,
			String panelistIdentifier, Integer timepointMinutes, Double scaleMin, Double scaleMax,
			String missingReason, Long supersedesResultId, Long recordedBy) {
		this.sensoryTestId = sensoryTestId;
		this.resultData = resultData;
		this.correlationWithPrediction = correlationWithPrediction;
		this.panelistIdentifier = panelistIdentifier;
		this.timepointMinutes = timepointMinutes;
		this.scaleMin = scaleMin;
		this.scaleMax = scaleMax;
		this.missingReason = missingReason;
		this.supersedesResultId = supersedesResultId;
		this.recordedBy = recordedBy;
	}

	public static SensoryTestResult record(Long sensoryTestId, String resultData,
			Double correlationWithPrediction, String panelistIdentifier, Integer timepointMinutes,
			Double scaleMin, Double scaleMax, String missingReason, Long supersedesResultId, Long recordedBy) {
		return new SensoryTestResult(sensoryTestId, resultData, correlationWithPrediction, panelistIdentifier,
				timepointMinutes, scaleMin, scaleMax, missingReason, supersedesResultId, recordedBy);
	}
}
