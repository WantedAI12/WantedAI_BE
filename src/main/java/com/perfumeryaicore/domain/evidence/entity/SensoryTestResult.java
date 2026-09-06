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
	@Column(name = "result_data")
	private String resultData;

	@Column(name = "correlation_with_prediction")
	private Double correlationWithPrediction;

	@Column(name = "recorded_by", nullable = false)
	private Long recordedBy;

	private SensoryTestResult(Long sensoryTestId, String resultData, Double correlationWithPrediction,
			Long recordedBy) {
		this.sensoryTestId = sensoryTestId;
		this.resultData = resultData;
		this.correlationWithPrediction = correlationWithPrediction;
		this.recordedBy = recordedBy;
	}

	public static SensoryTestResult record(Long sensoryTestId, String resultData,
			Double correlationWithPrediction, Long recordedBy) {
		return new SensoryTestResult(sensoryTestId, resultData, correlationWithPrediction, recordedBy);
	}
}
