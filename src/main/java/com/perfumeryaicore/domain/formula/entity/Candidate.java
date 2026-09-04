package com.perfumeryaicore.domain.formula.entity;

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
 * 후보 조향식. AI 생성이 성공했을 때만 만들어진다(안전 제약을 만족하는 해가 없으면 저장하지 않음).
 * 원료 구성·배합 비율은 {@link CandidateVersion}에 버전으로 쌓인다.
 *
 * <p>ERD 대비: 조회 접근 제어를 단순화하기 위해 {@code created_by}를 여기에도 둔다
 * (원래는 {@code request.created_by}를 통해서만 알 수 있었음 — 문서 델타 반영 대상).
 */
@Entity
@Getter
@Table(
		name = "candidates",
		indexes = @Index(name = "idx_candidates_request_id", columnList = "request_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Candidate extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "request_id", nullable = false)
	private Long requestId;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Column(name = "current_version_id")
	private Long currentVersionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CandidateStatus status;

	@Column(name = "job_id")
	private Long jobId;

	private Candidate(Long requestId, Long createdBy, Long jobId) {
		this.requestId = requestId;
		this.createdBy = createdBy;
		this.jobId = jobId;
		this.status = CandidateStatus.UNDER_REVIEW;
	}

	public static Candidate create(Long requestId, Long createdBy, Long jobId) {
		return new Candidate(requestId, createdBy, jobId);
	}

	public void attachVersion(Long versionId) {
		this.currentVersionId = versionId;
	}

	public boolean isOwnedBy(Long memberId) {
		return createdBy.equals(memberId);
	}
}
