package com.perfumeryaicore.domain.ingredient.service;

import com.perfumeryaicore.domain.ingredient.dto.request.RegisterIngredientMasterRequest;
import com.perfumeryaicore.domain.ingredient.dto.request.UpdateIngredientMasterRequest;
import com.perfumeryaicore.domain.ingredient.dto.response.IngredientMasterResponse;
import com.perfumeryaicore.domain.ingredient.entity.IngredientMaster;
import com.perfumeryaicore.domain.ingredient.repository.IngredientMasterRepository;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import com.perfumeryaicore.global.response.PageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원료 마스터 등록·조회(BE-062). 프로젝트에 매인 관측 데이터({@link IngredientQueryService})와
 * 달리 이 마스터는 프로젝트 범위가 아니다 - 원료 자체(CAS 번호로 식별되는 화학물질)는 프로젝트를
 * 넘나드는 공용 참조 데이터이기 때문이다. 등록·수정 권한 범위는 아직 팀 확인 전이라, 이번엔
 * 로그인한 모든 회원에게 열어둔다(가정 - 확인 필요, PR 설명 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientMasterService {

	private final IngredientMasterRepository repository;

	@Transactional
	public IngredientMasterResponse register(Long memberId, RegisterIngredientMasterRequest dto) {
		if (repository.existsByExternalId(dto.externalId())) {
			throw new BusinessException(ErrorCode.INGREDIENT_MASTER_ALREADY_EXISTS);
		}
		IngredientMaster saved = repository.save(IngredientMaster.register(
				dto.externalId(), dto.casNumber(), dto.name(), joinSynonyms(dto.synonyms()),
				dto.supplierName(), dto.safetyNotes(), dto.regulatoryNotes(), memberId));
		log.info("[INGREDIENT] master registered externalId={} by={}", dto.externalId(), memberId);
		return IngredientMasterResponse.from(saved);
	}

	@Transactional
	public IngredientMasterResponse update(String externalId, Long memberId, UpdateIngredientMasterRequest dto) {
		IngredientMaster entity = getOrThrow(externalId);
		entity.update(dto.casNumber(), dto.name(), joinSynonyms(dto.synonyms()), dto.supplierName(),
				dto.safetyNotes(), dto.regulatoryNotes());
		log.info("[INGREDIENT] master updated externalId={} by={}", externalId, memberId);
		return IngredientMasterResponse.from(entity);
	}

	public IngredientMasterResponse get(String externalId) {
		return IngredientMasterResponse.from(getOrThrow(externalId));
	}

	/**
	 * 한 번도 처방에 쓰이지 않은 원료도 검색된다 - 이 목록은 관측 미러가 아니라 등록된 마스터 전체다.
	 * 조향 AI의 registry 규모(약 3만 건)까지 등록될 수 있어 페이지네이션한다(BE-085 후속).
	 */
	public PageResponse<IngredientMasterResponse> search(String query, Pageable pageable) {
		var page = (query == null || query.isBlank())
				? repository.findAll(pageable)
				: repository.findByNameContainingIgnoreCaseOrCasNumberContainingIgnoreCase(query, query, pageable);
		return PageResponse.of(page.map(IngredientMasterResponse::from));
	}

	private IngredientMaster getOrThrow(String externalId) {
		return repository.findByExternalId(externalId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INGREDIENT_MASTER_NOT_FOUND));
	}

	private static String joinSynonyms(List<String> synonyms) {
		if (synonyms == null) {
			return null;
		}
		return synonyms.isEmpty() ? "" : String.join(",", synonyms);
	}
}
