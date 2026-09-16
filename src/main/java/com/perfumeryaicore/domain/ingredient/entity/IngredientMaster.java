package com.perfumeryaicore.domain.ingredient.entity;

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
 * 원료 마스터(BE-062). 조향 AI가 원료 목록 API를 주지 않아, 지금까지는
 * {@code candidate_version_ingredients}(실제 생성된 조향식에 등장한 원료)만으로 원료 화면을
 * 구성했다 - 이 관측 미러는 그대로 두되({@link com.perfumeryaicore.domain.ingredient.service.IngredientQueryService}
 * 변경 없음), 한 번도 처방에 쓰이지 않은 원료도 등록·검색할 수 있는 별도 마스터를 둔다.
 *
 * <p>BE-107(2026-09-17): AI팀이 전달한 원료 참고 데이터(V91 백엔드 데이터 묶음)를 이 마스터로
 * 가져올 수 있도록 pyramid/profile/가격/risk_tier를 추가했다 - 조향 AI가 실시간으로 주지 않는
 * 정보를 백엔드가 대신 서빙하기 위함이다. {@code pricePerKg}는 등록 시점에 저장한 참고 스냅샷일
 * 뿐이라 "현재 공인 견적"으로 오인되면 안 된다 - 후보 생성 시점의 실측값은
 * {@link com.perfumeryaicore.domain.ingredient.dto.response.IngredientResponse}가 따로 담당한다.
 */
@Entity
@Getter
@Table(
		name = "ingredient_masters",
		indexes = @Index(name = "idx_ingredient_masters_external_id", columnList = "external_id", unique = true)
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IngredientMaster extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 조향 AI가 쓰는 외부 식별자(예: {@code dihydromyrcenol})와 같은 값으로 등록해 서로 연결한다. */
	@Column(name = "external_id", nullable = false, length = 100, unique = true)
	private String externalId;

	@Column(length = 20)
	private String casNumber;

	@Column(nullable = false, length = 200)
	private String name;

	/** 동의어 목록. 콤마로 연결해 저장한다(다른 콤마 구분 필드와 같은 패턴). */
	@Column(name = "synonyms_csv", length = 1000)
	private String synonymsCsv;

	@Column(name = "supplier_name", length = 200)
	private String supplierName;

	@Lob
	@Column(name = "safety_notes", length = Length.LONG32)
	private String safetyNotes;

	@Lob
	@Column(name = "regulatory_notes", length = Length.LONG32)
	private String regulatoryNotes;

	@Column(name = "registered_by", nullable = false)
	private Long registeredBy;

	/** top/heart/base 등 원문 그대로 - 데이터에 없는 값을 만들어 넣지 않는다. */
	@Column(length = 20)
	private String pyramid;

	/** 향 표현별 가중치(JSON 객체 원문, 예: {"citrus":0.7,"fresh":1.0}). 재해석하지 않고 그대로 보존한다. */
	@Lob
	@Column(name = "profile_json", length = Length.LONG32)
	private String profileJson;

	@Column(name = "price_per_kg")
	private Double pricePerKg;

	/** 통화·추정 여부를 가격과 함께 보존한다(예: "USD_estimate") - 현재 견적으로 오인되면 안 된다. */
	@Column(name = "price_currency", length = 20)
	private String priceCurrency;

	@Column(name = "risk_tier")
	private Integer riskTier;

	private IngredientMaster(String externalId, String casNumber, String name, String synonymsCsv,
			String supplierName, String safetyNotes, String regulatoryNotes, Long registeredBy,
			String pyramid, String profileJson, Double pricePerKg, String priceCurrency, Integer riskTier) {
		this.externalId = externalId;
		this.casNumber = casNumber;
		this.name = name;
		this.synonymsCsv = synonymsCsv;
		this.supplierName = supplierName;
		this.safetyNotes = safetyNotes;
		this.regulatoryNotes = regulatoryNotes;
		this.registeredBy = registeredBy;
		this.pyramid = pyramid;
		this.profileJson = profileJson;
		this.pricePerKg = pricePerKg;
		this.priceCurrency = priceCurrency;
		this.riskTier = riskTier;
	}

	public static IngredientMaster register(String externalId, String casNumber, String name, String synonymsCsv,
			String supplierName, String safetyNotes, String regulatoryNotes, Long registeredBy,
			String pyramid, String profileJson, Double pricePerKg, String priceCurrency, Integer riskTier) {
		return new IngredientMaster(externalId, casNumber, name, synonymsCsv, supplierName,
				safetyNotes, regulatoryNotes, registeredBy, pyramid, profileJson, pricePerKg, priceCurrency, riskTier);
	}

	/** {@code null}이 아닌 값만 반영한다. 신원(externalId)은 여기서 바꾸지 않는다 - 다른 원료로 재배정하는 사고를 막는다. */
	public void update(String casNumber, String name, String synonymsCsv, String supplierName,
			String safetyNotes, String regulatoryNotes, String pyramid, String profileJson,
			Double pricePerKg, String priceCurrency, Integer riskTier) {
		if (casNumber != null) {
			this.casNumber = casNumber;
		}
		if (name != null) {
			this.name = name;
		}
		if (synonymsCsv != null) {
			this.synonymsCsv = synonymsCsv;
		}
		if (supplierName != null) {
			this.supplierName = supplierName;
		}
		if (safetyNotes != null) {
			this.safetyNotes = safetyNotes;
		}
		if (regulatoryNotes != null) {
			this.regulatoryNotes = regulatoryNotes;
		}
		if (pyramid != null) {
			this.pyramid = pyramid;
		}
		if (profileJson != null) {
			this.profileJson = profileJson;
		}
		if (pricePerKg != null) {
			this.pricePerKg = pricePerKg;
		}
		if (priceCurrency != null) {
			this.priceCurrency = priceCurrency;
		}
		if (riskTier != null) {
			this.riskTier = riskTier;
		}
	}
}
