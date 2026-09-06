package com.perfumeryaicore.domain.evidence.entity;

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
 * 독립 블라인드 관능 검증 계획. 결과가 처음 등록되면 {@link SensoryTestStatus#COMPLETED}로 바뀐다.
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

	@Lob
	@Column(name = "plan_detail")
	private String planDetail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SensoryTestStatus status;

	@Column(name = "planned_by", nullable = false)
	private Long plannedBy;

	private SensoryTest(Long candidateId, String planDetail, Long plannedBy) {
		this.candidateId = candidateId;
		this.planDetail = planDetail;
		this.plannedBy = plannedBy;
		this.status = SensoryTestStatus.PLANNED;
	}

	public static SensoryTest plan(Long candidateId, String planDetail, Long plannedBy) {
		return new SensoryTest(candidateId, planDetail, plannedBy);
	}

	public void markCompleted() {
		this.status = SensoryTestStatus.COMPLETED;
	}
}
