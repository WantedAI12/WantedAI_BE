package com.perfumeryaicore.domain.evidence.entity;

import com.perfumeryaicore.domain.job.entity.JobStatus;
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
 * 증거 보고서. 후보·현재 버전·안전 평가·예측·실험 이력·관능 검증을 한데 모은 JSON 번들({@code reportData})과
 * 그걸 그대로 옮겨 그린 PDF(S3에 저장, {@code pdfObjectKey})를 함께 갖는다. 생성 작업(Job)이 성공했을 때만
 * 만들어진다 — {@link com.perfumeryaicore.domain.formula.entity.Candidate}가 AI 생성 성공 시에만
 * 만들어지는 것과 같은 패턴.
 *
 * <p>다운로드 URL은 저장하지 않는다 — 버킷이 비공개라 조회 시점마다 짧게 유효한 서명 URL을
 * 새로 발급한다({@code S3FileStorage.presignedGetUrl}). 여기 저장하는 건 오브젝트 키뿐이다.
 */
@Entity
@Getter
@Table(
		name = "evidence_reports",
		indexes = @Index(name = "idx_evidence_reports_candidate_id", columnList = "candidate_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenceReport extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "candidate_id", nullable = false)
	private Long candidateId;

	@Column(name = "job_id")
	private Long jobId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private JobStatus status;

	@Lob
	@Column(name = "report_data")
	private String reportData;

	/** S3 오브젝트 키. URL이 아니다 — 버킷이 비공개라 조회 시점마다 서명 URL을 새로 만든다. */
	@Column(name = "pdf_object_key")
	private String pdfObjectKey;

	@Column(name = "requested_by", nullable = false)
	private Long requestedBy;

	private EvidenceReport(Long candidateId, Long jobId, String reportData, String pdfObjectKey, Long requestedBy) {
		this.candidateId = candidateId;
		this.jobId = jobId;
		this.reportData = reportData;
		this.pdfObjectKey = pdfObjectKey;
		this.requestedBy = requestedBy;
		this.status = JobStatus.SUCCEEDED;
	}

	public static EvidenceReport completed(Long candidateId, Long jobId, String reportData,
			String pdfObjectKey, Long requestedBy) {
		return new EvidenceReport(candidateId, jobId, reportData, pdfObjectKey, requestedBy);
	}
}
