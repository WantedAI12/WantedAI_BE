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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 후보 조향식의 한 버전. 원료 구성은 {@link CandidateVersionIngredient}로 별도 저장한다.
 *
 * <p>{@code rawResponse}에 조향 AI 응답 원문을 그대로 보관한다(재현·감사용, 값을 재보정하지 않음).
 * 화면용 파싱 결과는 {@link com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse}가 맡는다.
 */
@Entity
@Getter
@Table(
		name = "candidate_versions",
		indexes = @Index(name = "idx_candidate_versions_candidate_id", columnList = "candidate_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateVersion extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "candidate_id", nullable = false)
	private Long candidateId;

	@Column(name = "parent_version_id")
	private Long parentVersionId;

	private Double cost;

	@Lob
	@Column(name = "generation_rationale")
	private String generationRationale;

	@Column(name = "ai_provider", length = 30)
	private String aiProvider;

	@Column(name = "ai_gpu_used")
	private Boolean aiGpuUsed;

	@Column(name = "ai_response_status", length = 40)
	private String aiResponseStatus;

	@Column(name = "ai_latency_ms")
	private Long aiLatencyMs;

	/** 조향 AI 응답 원문(JSON). 근거·재현 목적. 인증 헤더 등 내부 정보는 포함하지 않는다. */
	@Lob
	@Column(name = "raw_response")
	private String rawResponse;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Builder
	private CandidateVersion(Long candidateId, Long parentVersionId, Double cost, String generationRationale,
			String aiProvider, Boolean aiGpuUsed, String aiResponseStatus, Long aiLatencyMs,
			String rawResponse, Long createdBy) {
		this.candidateId = candidateId;
		this.parentVersionId = parentVersionId;
		this.cost = cost;
		this.generationRationale = generationRationale;
		this.aiProvider = aiProvider;
		this.aiGpuUsed = aiGpuUsed;
		this.aiResponseStatus = aiResponseStatus;
		this.aiLatencyMs = aiLatencyMs;
		this.rawResponse = rawResponse;
		this.createdBy = createdBy;
	}
}
