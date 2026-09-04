package com.perfumeryaicore.domain.request.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.TargetRegion;
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
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 자연어 향 요청과 구조화된 향 의도.
 *
 * <p>이 도메인은 외부 AI를 호출하지 않는다. 사용자가 입력한 자연어와 구조화 필드를 정규화·검증하고,
 * 필수 항목이 모두 채워지면 {@code confirm}으로 확정한다. 확정된 요청만 후보 조향식 생성을 시작할 수 있다.
 */
@Entity
@Getter
@Table(
		name = "fragrance_requests",
		indexes = @Index(name = "idx_fragrance_requests_project_id", columnList = "project_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FragranceRequest extends BaseTimeEntity {

	/** brief 길이 상한 (조향 AI 제한과 동일). */
	public static final int RAW_TEXT_MAX = 2000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id", nullable = false)
	private Long projectId;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Lob
	@Column(name = "raw_text", nullable = false)
	private String rawText;

	@Enumerated(EnumType.STRING)
	@Column(name = "product_category", length = 30)
	private ProductCategory productCategory;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_region", length = 4)
	private TargetRegion targetRegion;

	@Column(name = "risk_tier")
	private Integer riskTier;

	@Enumerated(EnumType.STRING)
	@Column(length = 12)
	private Intensity intensity;

	@Enumerated(EnumType.STRING)
	@Column(length = 12)
	private Longevity longevity;

	@Column(name = "usage_concentration_percent")
	private Double usageConcentrationPercent;

	@Column(name = "max_ingredient_count")
	private Integer maxIngredientCount;

	@Column(name = "max_ingredient_price_per_kg")
	private Double maxIngredientPricePerKg;

	/** 향조 목록. 콤마로 연결해 저장한다. */
	@Column(name = "accords_csv", length = 500)
	private String accordsCsv;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RequestStatus status;

	private FragranceRequest(Long projectId, Long createdBy, String rawText) {
		this.projectId = projectId;
		this.createdBy = createdBy;
		this.rawText = rawText;
		this.status = RequestStatus.MISSING_FIELDS;
		recomputeStatus();
	}

	public static FragranceRequest create(Long projectId, Long createdBy, String rawText) {
		return new FragranceRequest(projectId, createdBy, rawText);
	}

	/** 부분 수정. {@code null}이 아닌 값만 반영하고 상태를 다시 계산한다. */
	public void applyUpdate(String rawText, ProductCategory productCategory, TargetRegion targetRegion,
			Integer riskTier, Intensity intensity, Longevity longevity, Double usageConcentrationPercent,
			Integer maxIngredientCount, Double maxIngredientPricePerKg, List<String> accords) {

		if (!status.isEditable()) {
			throw new BusinessException(ErrorCode.REQUEST_EDIT_NOT_ALLOWED);
		}
		if (rawText != null) {
			this.rawText = rawText;
		}
		if (productCategory != null) {
			this.productCategory = productCategory;
		}
		if (targetRegion != null) {
			this.targetRegion = targetRegion;
		}
		if (riskTier != null) {
			this.riskTier = riskTier;
		}
		if (intensity != null) {
			this.intensity = intensity;
		}
		if (longevity != null) {
			this.longevity = longevity;
		}
		if (usageConcentrationPercent != null) {
			this.usageConcentrationPercent = usageConcentrationPercent;
		}
		if (maxIngredientCount != null) {
			this.maxIngredientCount = maxIngredientCount;
		}
		if (maxIngredientPricePerKg != null) {
			this.maxIngredientPricePerKg = maxIngredientPricePerKg;
		}
		if (accords != null) {
			this.accordsCsv = accords.isEmpty() ? null : String.join(",", accords);
		}
		recomputeStatus();
	}

	public void recomputeStatus() {
		if (status == RequestStatus.CONFIRMED) {
			return;
		}
		this.status = missingRequiredFields().isEmpty() ? RequestStatus.DRAFT : RequestStatus.MISSING_FIELDS;
	}

	public void confirm() {
		if (status == RequestStatus.CONFIRMED) {
			throw new BusinessException(ErrorCode.REQUEST_EDIT_NOT_ALLOWED, "이미 확정된 요청입니다.");
		}
		List<String> missing = missingRequiredFields();
		if (!missing.isEmpty()) {
			throw new BusinessException(ErrorCode.REQUEST_NOT_CONFIRMABLE,
					"필수 항목이 비어 있습니다: " + String.join(", ", missing));
		}
		if (status == RequestStatus.BLOCKED) {
			throw new BusinessException(ErrorCode.REQUEST_NOT_CONFIRMABLE, "진행이 차단된 요청입니다.");
		}
		this.status = RequestStatus.CONFIRMED;
	}

	/** 확정에 필요한데 아직 비어 있는 항목 이름. */
	public List<String> missingRequiredFields() {
		List<String> missing = new ArrayList<>();
		if (!StringUtils.hasText(rawText)) {
			missing.add("rawText");
		}
		if (productCategory == null) {
			missing.add("productCategory");
		}
		if (targetRegion == null) {
			missing.add("targetRegion");
		}
		if (riskTier == null) {
			missing.add("riskTier");
		}
		return missing;
	}

	public List<String> accords() {
		if (!StringUtils.hasText(accordsCsv)) {
			return List.of();
		}
		return Arrays.stream(accordsCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
	}

	public boolean isConfirmed() {
		return status == RequestStatus.CONFIRMED;
	}

	public boolean isOwnedBy(Long memberId) {
		return createdBy.equals(memberId);
	}
}
