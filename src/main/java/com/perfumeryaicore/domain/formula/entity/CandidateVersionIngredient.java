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
 * {@code ingredient} 도메인의 로컬 미러가 아직 없는 지금은 그대로 저장한다.
 * (ingredient 도메인 구현 시 내부 원료 ID와 매핑 예정 — 문서 델타 반영 대상)
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
