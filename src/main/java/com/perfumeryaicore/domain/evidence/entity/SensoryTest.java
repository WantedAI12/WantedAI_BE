package com.perfumeryaicore.domain.evidence.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 독립 블라인드 관능 검증 계획. 결과가 처음 등록되면 {@link SensoryTestStatus#COMPLETED}로 바뀐다.
 *
 * <p>완료됐다고 자동으로 공개되지 않는다 — {@link #publish}로 명시적으로 공개하기 전까지는
 * SENSORY_SCIENTIST 외 역할이 결과를 조회할 수 없다(BE-006).
 */
@Entity
@Getter
@Table(
		name = "sensory_tests",
		indexes = @Index(name = "idx_sensory_tests_candidate_id", columnList = "candidate_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensoryTest extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "candidate_id", nullable = false)
	private Long candidateId;

	/**
	 * 계획 시점에 고정한 후보 버전(BE-053). 이후 후보가 새 버전을 얻어도 이 관능 계획·결과는
	 * 이 버전의 배합을 검증한 것으로 남는다 — 조회 때마다 "현재" 버전을 따라가지 않는다.
	 */
	@Column(name = "candidate_version_id", nullable = false)
	private Long candidateVersionId;

	/**
	 * 계획 시점에 고정한 성능 프록시 예측값(BE-057). {@code getDetail}에서 이 값을 그대로
	 * 돌려준다 — 조회 시점의 "현재" 예측을 다시 읽지 않는다. 계획 당시 예측이 없었으면 null.
	 */
	@Column(name = "predicted_similarity_score_at_plan")
	private Double predictedSimilarityScoreAtPlan;

	@Lob
	@Column(name = "plan_detail")
	private String planDetail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SensoryTestStatus status;

	@Column(name = "planned_by", nullable = false)
	private Long plannedBy;

	@Column(nullable = false)
	private boolean published;

	@Column(name = "published_by")
	private Long publishedBy;

	@Column(name = "published_at")
	private LocalDateTime publishedAt;

	private SensoryTest(Long candidateId, Long candidateVersionId, Double predictedSimilarityScoreAtPlan,
			String planDetail, Long plannedBy) {
		this.candidateId = candidateId;
		this.candidateVersionId = candidateVersionId;
		this.predictedSimilarityScoreAtPlan = predictedSimilarityScoreAtPlan;
		this.planDetail = planDetail;
		this.plannedBy = plannedBy;
		this.status = SensoryTestStatus.PLANNED;
		this.published = false;
	}

	public static SensoryTest plan(Long candidateId, Long candidateVersionId, Double predictedSimilarityScoreAtPlan,
			String planDetail, Long plannedBy) {
		return new SensoryTest(candidateId, candidateVersionId, predictedSimilarityScoreAtPlan, planDetail, plannedBy);
	}

	public void markCompleted() {
		this.status = SensoryTestStatus.COMPLETED;
	}

	/** 결과가 등록된(COMPLETED) 계획만 공개할 수 있다 — 아무 결과 없이 공개하지 않는다. */
	public void publish(Long memberId) {
		if (status != SensoryTestStatus.COMPLETED) {
			throw new BusinessException(ErrorCode.SENSORY_TEST_NOT_COMPLETED);
		}
		this.published = true;
		this.publishedBy = memberId;
		this.publishedAt = LocalDateTime.now();
	}
}
