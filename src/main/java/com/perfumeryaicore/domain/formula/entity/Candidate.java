package com.perfumeryaicore.domain.formula.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import com.perfumeryaicore.global.common.CandidateStatus;
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

	/**
	 * 실험 워크플로 상태 전이. 순서만 검증한다 — 안전 게이트 승인 여부 같은 다른 도메인
	 * 조건은 experiment 도메인(호출부)이 이 메서드를 부르기 전에 확인한다.
	 */
	public void transitionStatus(CandidateStatus target) {
		if (!isValidTransition(status, target)) {
			throw new BusinessException(ErrorCode.CANDIDATE_STATUS_TRANSITION_INVALID,
					"허용되지 않는 상태 전이입니다: " + status + " → " + target);
		}
		this.status = target;
	}

	private static boolean isValidTransition(CandidateStatus from, CandidateStatus to) {
		if (from == to) {
			return false;
		}
		return switch (to) {
			case CONFIRMED_FOR_EXPERIMENT -> from == CandidateStatus.UNDER_REVIEW;
			case IN_SENSORY_TEST -> from == CandidateStatus.CONFIRMED_FOR_EXPERIMENT;
			case APPROVED -> from == CandidateStatus.IN_SENSORY_TEST;
			case REJECTED -> from == CandidateStatus.UNDER_REVIEW
					|| from == CandidateStatus.CONFIRMED_FOR_EXPERIMENT
					|| from == CandidateStatus.IN_SENSORY_TEST;
			case UNDER_REVIEW -> false;
		};
	}

	public boolean isOwnedBy(Long memberId) {
		return createdBy.equals(memberId);
	}
}
