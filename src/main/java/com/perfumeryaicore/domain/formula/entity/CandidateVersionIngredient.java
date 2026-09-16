package com.perfumeryaicore.domain.formula.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 후보 버전을 구성하는 원료 한 줄. 조향 AI 응답의 원료 식별자는 문자열이라(예: {@code dihydromyrcenol})
 * 그대로 저장한다. {@code ingredientExternalId}는 BE-062의
 * {@link com.perfumeryaicore.domain.ingredient.entity.IngredientMaster#getExternalId}와 같은 값 공간을
 * 쓰지만, 도메인 간 직접 참조를 두지 않는 원칙(ID/DTO로만 연결)에 따라 FK로 묶지는 않는다.
 */
@Entity
@Getter
@Table(
		name = "candidate_version_ingredients",
		indexes = @Index(name = "idx_cvi_candidate_version_id", columnList = "candidate_version_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CandidateVersionIngredient {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "candidate_version_id", nullable = false)
	private Long candidateVersionId;

	@Column(name = "ingredient_external_id", length = 100)
	private String ingredientExternalId;

	@Column(name = "ingredient_name", nullable = false, length = 200)
	private String ingredientName;

	@Column(length = 20)
	private String pyramid;

	@Column(name = "concentrate_percent")
	private Double concentratePercent;

	@Column(name = "finished_product_percent")
	private Double finishedProductPercent;

	@Column(name = "price_per_kg")
	private Double pricePerKg;

	private Double availability;

	@Builder
	private CandidateVersionIngredient(Long candidateVersionId, String ingredientExternalId, String ingredientName,
			String pyramid, Double concentratePercent, Double finishedProductPercent,
			Double pricePerKg, Double availability) {
		this.candidateVersionId = candidateVersionId;
		this.ingredientExternalId = ingredientExternalId;
		this.ingredientName = ingredientName;
		this.pyramid = pyramid;
		this.concentratePercent = concentratePercent;
		this.finishedProductPercent = finishedProductPercent;
		this.pricePerKg = pricePerKg;
		this.availability = availability;
	}
}
