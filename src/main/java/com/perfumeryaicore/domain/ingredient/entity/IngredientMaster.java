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
 * <p>가격·가용성 같은 관측값은 여기 두지 않는다 - 그 값은 후보 생성 시점의 AI 응답 스냅샷일
 * 뿐이라 "현재 공인 견적"으로 오인되면 안 된다({@link com.perfumeryaicore.domain.ingredient.dto.response.IngredientResponse}
 * 참고). 이 엔티티는 원료의 신원(외부 ID·CAS·명칭·동의어)과 공급사·안전/규제 메모만 다룬다.
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

	private IngredientMaster(String externalId, String casNumber, String name, String synonymsCsv,
			String supplierName, String safetyNotes, String regulatoryNotes, Long registeredBy) {
		this.externalId = externalId;
		this.casNumber = casNumber;
		this.name = name;
		this.synonymsCsv = synonymsCsv;
		this.supplierName = supplierName;
		this.safetyNotes = safetyNotes;
		this.regulatoryNotes = regulatoryNotes;
		this.registeredBy = registeredBy;
	}

	public static IngredientMaster register(String externalId, String casNumber, String name, String synonymsCsv,
			String supplierName, String safetyNotes, String regulatoryNotes, Long registeredBy) {
		return new IngredientMaster(externalId, casNumber, name, synonymsCsv, supplierName,
				safetyNotes, regulatoryNotes, registeredBy);
	}

	/** {@code null}이 아닌 값만 반영한다. 신원(externalId)은 여기서 바꾸지 않는다 - 다른 원료로 재배정하는 사고를 막는다. */
	public void update(String casNumber, String name, String synonymsCsv, String supplierName,
			String safetyNotes, String regulatoryNotes) {
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
	}
}
