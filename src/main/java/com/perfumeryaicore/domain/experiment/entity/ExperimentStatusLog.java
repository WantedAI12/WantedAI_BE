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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 후보의 실험 상태 변경 이력 한 건(불변). 상태 변화 자체는 {@code formula} 도메인의
 * {@code Candidate}가 소유·검증하고, 이 로그는 "누가 언제 어떤 상태로 바꿨는지"만 기록한다.
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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CandidateStatus status;

	@Column(name = "changed_by", nullable = false)
	private Long changedBy;

	private ExperimentStatusLog(Long candidateId, CandidateStatus status, Long changedBy) {
		this.candidateId = candidateId;
		this.status = status;
		this.changedBy = changedBy;
	}

	public static ExperimentStatusLog record(Long candidateId, CandidateStatus status, Long changedBy) {
		return new ExperimentStatusLog(candidateId, status, changedBy);
	}
}
