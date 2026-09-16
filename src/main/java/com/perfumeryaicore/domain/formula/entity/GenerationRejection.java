package com.perfumeryaicore.domain.formula.entity;

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
 * 조향 AI가 기권한(안전한 해를 찾지 못한, {@code no_safe_match}) 시도의 진단 기록(BE-035 일부).
 * 후보(Candidate)가 아니다 - 정상 추천 후보 목록에는 절대 섞이지 않는다. AI가 실제로 계산한
 * 근접 후보·매칭 점수 같은 진단 데이터를 감사·재검토 목적으로만 보존한다.
 *
 * <p>{@code reasonCode}는 AI 응답의 {@code status} 문자열을 그대로 저장한다(자체 enum으로
 * 강제 변환하지 않음). AI 쪽 계약이 바뀌어도(v2가 {@code abstained} 등 새 값을 쓰기 시작해도)
 * 이 필드는 그 값을 그대로 받아들인다 - {@code confidence} 필드를 항상 숫자로 가정했다가 겪은
 * 파싱 실패를 반복하지 않기 위함.
 */
@Entity
@Getter
@Table(
		name = "generation_rejections",
		indexes = @Index(name = "idx_generation_rejections_request_id", columnList = "request_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GenerationRejection extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "request_id", nullable = false)
	private Long requestId;

	@Column(name = "project_id", nullable = false)
	private Long projectId;

	@Column(name = "job_id", nullable = false)
	private Long jobId;

	/** AI 응답의 {@code status} 값 그대로(예: {@code no_safe_match}). */
	@Column(name = "reason_code", length = 40)
	private String reasonCode;

	@Lob
	@Column(name = "message", length = Length.LONG32)
	private String message;

	/** AI 응답 원문(JSON). 근접 후보·매칭 점수 등 진단 데이터가 여기 들어있다. */
	@Lob
	@Column(name = "raw_response", length = Length.LONG32)
	private String rawResponse;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	private GenerationRejection(Long requestId, Long projectId, Long jobId, String reasonCode,
			String message, String rawResponse, Long createdBy) {
		this.requestId = requestId;
		this.projectId = projectId;
		this.jobId = jobId;
		this.reasonCode = reasonCode;
		this.message = message;
		this.rawResponse = rawResponse;
		this.createdBy = createdBy;
	}

	public static GenerationRejection of(Long requestId, Long projectId, Long jobId, String reasonCode,
			String message, String rawResponse, Long createdBy) {
		return new GenerationRejection(requestId, projectId, jobId, reasonCode, message, rawResponse, createdBy);
	}
}
