package com.perfumeryaicore.domain.experiment.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import com.perfumeryaicore.global.common.CandidateStatus;
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
import org.hibernate.Length;

/**
 * 후보의 실험 상태 변경 이력 한 건(불변). 상태 변화 자체는 {@code formula} 도메인의
 * {@code Candidate}가 소유·검증하고, 이 로그는 "누가 언제 어떤 상태에서 어떤 상태로, 어떤 배합
 * 버전을 대상으로, 왜 바꿨는지"를 기록한다(BE-040).
 */
@Entity
@Getter
@Table(
		name = "experiment_status_logs",
		indexes = @Index(name = "idx_experiment_status_logs_candidate_id", columnList = "candidate_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperimentStatusLog extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "candidate_id", nullable = false)
	private Long candidateId;

	/** 전이 직전 상태. 최초 전이 이력은 이론상 없을 수 없다 — 후보는 항상 UNDER_REVIEW로 시작한다. */
	@Enumerated(EnumType.STRING)
	@Column(name = "previous_status", length = 30)
	private CandidateStatus previousStatus;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CandidateStatus status;

	/** 전이 시점에 고정한 후보 버전(BE-040) — 이후 새 버전이 생겨도 이 결정의 대상은 바뀌지 않는다. */
	@Column(name = "candidate_version_id")
	private Long candidateVersionId;

	@Lob
	@Column(name = "reason", length = Length.LONG32)
	private String reason;

	@Column(name = "changed_by", nullable = false)
	private Long changedBy;

	private ExperimentStatusLog(Long candidateId, CandidateStatus previousStatus, CandidateStatus status,
			Long candidateVersionId, String reason, Long changedBy) {
		this.candidateId = candidateId;
		this.previousStatus = previousStatus;
		this.status = status;
		this.candidateVersionId = candidateVersionId;
		this.reason = reason;
		this.changedBy = changedBy;
	}

	public static ExperimentStatusLog record(Long candidateId, CandidateStatus previousStatus, CandidateStatus status,
			Long candidateVersionId, String reason, Long changedBy) {
		return new ExperimentStatusLog(candidateId, previousStatus, status, candidateVersionId, reason, changedBy);
	}
}
