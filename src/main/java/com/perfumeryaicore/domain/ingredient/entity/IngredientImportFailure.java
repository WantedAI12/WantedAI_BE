package com.perfumeryaicore.domain.ingredient.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.Length;

/**
 * BE-063~066: 원료 마스터 대량 등록 중 실패한 행. {@code payloadJson}에 원본 요청을 그대로
 * 보관해 재처리({@code retry})할 수 있게 한다 - 실패했다고 그 행을 잃어버리면 대량 등록의
 * 의미가 없다. 성공적으로 재처리되면 {@link #resolve()}로 표시하고, 실제 삭제는 하지 않아
 * 무엇이 실패했었는지 이력이 남는다.
 */
@Entity
@Getter
@Table(name = "ingredient_import_failures")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngredientImportFailure extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", length = 100)
	private String externalId;

	@Lob
	@Column(name = "payload_json", nullable = false, length = Length.LONG32)
	private String payloadJson;

	@Column(name = "error_message", nullable = false, length = 1000)
	private String errorMessage;

	@Column(name = "imported_by", nullable = false)
	private Long importedBy;

	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;

	private IngredientImportFailure(String externalId, String payloadJson, String errorMessage, Long importedBy) {
		this.externalId = externalId;
		this.payloadJson = payloadJson;
		this.errorMessage = errorMessage;
		this.importedBy = importedBy;
	}

	public static IngredientImportFailure of(String externalId, String payloadJson, String errorMessage,
			Long importedBy) {
		return new IngredientImportFailure(externalId, payloadJson, errorMessage, importedBy);
	}

	public boolean isResolved() {
		return resolvedAt != null;
	}

	public void resolve(LocalDateTime now) {
		this.resolvedAt = now;
	}

	/** 재처리 재시도가 다시 실패하면 최신 사유로 갱신한다(해결 표시는 건드리지 않는다). */
	public void recordRetryFailure(String errorMessage) {
		this.errorMessage = errorMessage;
	}
}
