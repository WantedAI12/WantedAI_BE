package com.perfumeryaicore.domain.ingredient.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 조향 AI {@code GET /v1/catalog} 스냅샷 한 번의 동기화 결과.
 *
 * <p>주의: {@code /v1/catalog}는 <b>원료 목록이 아니라 집계 통계</b>(reference/screened/active 건수,
 * catalog_version, registry_sha256 등)만 돌려준다. 그래서 이 도메인은 원료를 행 단위로 미러링하지
 * 않고, 통계 스냅샷 원문을 그대로 보관한다({@code rawSnapshot}). 개별 원료 조회는
 * {@code candidate_version_ingredients}(실제 생성된 조향식에서 관측된 원료)에서 읽는다.
 */
@Entity
@Getter
@Table(
		name = "catalog_sync_runs",
		indexes = @Index(name = "idx_catalog_sync_runs_job_id", columnList = "job_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogSyncRun extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "job_id", nullable = false)
	private Long jobId;

	@Column(name = "project_id", nullable = false)
	private Long projectId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private JobStatus status;

	/** {@code /v1/catalog} 응답 원문(JSON). 값을 가공하지 않고 그대로 보관한다. */
	@Lob
	@Column(name = "raw_snapshot")
	private String rawSnapshot;

	/** 스냅샷에서 뽑은 대표 통계 (없으면 null). */
	@Column(name = "reference_molecule_count")
	private Integer referenceMoleculeCount;

	@Column(name = "safety_screened_count")
	private Integer safetyScreenedCount;

	@Column(name = "prototype_active_count")
	private Integer prototypeActiveCount;

	@Column(name = "catalog_version", length = 60)
	private String catalogVersion;

	@Column(name = "registry_sha256", length = 80)
	private String registrySha256;

	@Column(name = "synced_at", nullable = false)
	private LocalDateTime syncedAt;

	@Column(name = "requested_by", nullable = false)
	private Long requestedBy;

	private CatalogSyncRun(Long jobId, Long projectId, String rawSnapshot, Integer referenceMoleculeCount,
			Integer safetyScreenedCount, Integer prototypeActiveCount, String catalogVersion,
			String registrySha256, Long requestedBy) {
		this.jobId = jobId;
		this.projectId = projectId;
		this.status = JobStatus.SUCCEEDED;
		this.rawSnapshot = rawSnapshot;
		this.referenceMoleculeCount = referenceMoleculeCount;
		this.safetyScreenedCount = safetyScreenedCount;
		this.prototypeActiveCount = prototypeActiveCount;
		this.catalogVersion = catalogVersion;
		this.registrySha256 = registrySha256;
		this.syncedAt = LocalDateTime.now();
		this.requestedBy = requestedBy;
	}

	public static CatalogSyncRun completed(Long jobId, Long projectId, String rawSnapshot,
			Integer referenceMoleculeCount, Integer safetyScreenedCount, Integer prototypeActiveCount,
			String catalogVersion, String registrySha256, Long requestedBy) {
		return new CatalogSyncRun(jobId, projectId, rawSnapshot, referenceMoleculeCount, safetyScreenedCount,
				prototypeActiveCount, catalogVersion, registrySha256, requestedBy);
	}
}
