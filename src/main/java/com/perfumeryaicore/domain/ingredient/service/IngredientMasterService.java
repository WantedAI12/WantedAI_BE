package com.perfumeryaicore.domain.ingredient.service;

import com.perfumeryaicore.domain.ingredient.dto.request.RegisterIngredientMasterRequest;
import com.perfumeryaicore.domain.ingredient.dto.request.UpdateIngredientMasterRequest;
import com.perfumeryaicore.domain.ingredient.dto.response.BulkImportResultResponse;
import com.perfumeryaicore.domain.ingredient.dto.response.ImportFailureResponse;
import com.perfumeryaicore.domain.ingredient.dto.response.IngredientMasterResponse;
import com.perfumeryaicore.domain.ingredient.entity.IngredientImportFailure;
import com.perfumeryaicore.domain.ingredient.entity.IngredientMaster;
import com.perfumeryaicore.domain.ingredient.repository.IngredientImportFailureRepository;
import com.perfumeryaicore.domain.ingredient.repository.IngredientMasterRepository;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import com.perfumeryaicore.global.response.PageResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
	private final IngredientImportFailureRepository importFailureRepository;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Transactional
	public IngredientMasterResponse register(Long memberId, RegisterIngredientMasterRequest dto) {
		if (repository.existsByExternalId(dto.externalId())) {
			throw new BusinessException(ErrorCode.INGREDIENT_MASTER_ALREADY_EXISTS);
		}
		IngredientMaster saved = repository.save(IngredientMaster.register(
				dto.externalId(), dto.casNumber(), dto.name(), joinSynonyms(dto.synonyms()),
				dto.supplierName(), dto.safetyNotes(), dto.regulatoryNotes(), memberId,
				dto.pyramid(), writeProfile(dto.profile()), dto.pricePerKg(), dto.priceCurrency(), dto.riskTier()));
		log.info("[INGREDIENT] master registered externalId={} by={}", dto.externalId(), memberId);
		return toResponse(saved);
	}

	/**
	 * BE-063~066: 대량 등록. 한 행이 이미 등록됐거나(DB 기준) 같은 배치 안에서 중복이면 그 행만
	 * {@link IngredientImportFailure}로 남기고 나머지는 그대로 등록한다 - 행 하나 때문에 전체를
	 * 되돌리지 않는다. 실패 행은 원본 요청을 그대로 보관해 {@link #retryImportFailure}로
	 * 다시 시도할 수 있다.
	 */
	@Transactional
	public BulkImportResultResponse bulkImport(Long memberId, List<RegisterIngredientMasterRequest> items) {
		Set<String> existingExternalIds = new HashSet<>(repository.findByExternalIdIn(
				items.stream().map(RegisterIngredientMasterRequest::externalId).toList())
				.stream().map(IngredientMaster::getExternalId).toList());

		Set<String> seenInBatch = new HashSet<>();
		List<RegisterIngredientMasterRequest> valid = new ArrayList<>();
		List<IngredientImportFailure> failures = new ArrayList<>();
		for (RegisterIngredientMasterRequest item : items) {
			if (existingExternalIds.contains(item.externalId())) {
				failures.add(toFailure(item, "이미 등록된 외부 원료 ID입니다.", memberId));
			} else if (!seenInBatch.add(item.externalId())) {
				failures.add(toFailure(item, "같은 배치 내에 중복된 외부 원료 ID입니다.", memberId));
			} else {
				valid.add(item);
			}
		}

		List<IngredientMaster> saved = repository.saveAll(valid.stream()
				.map(dto -> IngredientMaster.register(dto.externalId(), dto.casNumber(), dto.name(),
						joinSynonyms(dto.synonyms()), dto.supplierName(), dto.safetyNotes(), dto.regulatoryNotes(),
						memberId, dto.pyramid(), writeProfile(dto.profile()), dto.pricePerKg(),
						dto.priceCurrency(), dto.riskTier()))
				.toList());
		List<IngredientImportFailure> savedFailures = importFailureRepository.saveAll(failures);

		log.info("[INGREDIENT] bulk import total={} succeeded={} failed={} by={}",
				items.size(), saved.size(), savedFailures.size(), memberId);
		return new BulkImportResultResponse(
				items.size(), saved.size(), savedFailures.size(),
				saved.stream().map(this::toResponse).toList(),
				savedFailures.stream().map(ImportFailureResponse::from).toList());
	}

	/** 아직 해결되지 않은 대량 등록 실패 행(재처리 큐)을 오래된 순으로 조회한다. */
	public PageResponse<ImportFailureResponse> pendingImportFailures(Pageable pageable) {
		return PageResponse.of(
				importFailureRepository.findByResolvedAtIsNullOrderByCreatedAtAsc(pageable)
						.map(ImportFailureResponse::from));
	}

	/** 저장해 둔 원본 요청으로 등록을 다시 시도한다. 성공하면 해결로 표시하고, 다시 실패하면 사유만 갱신한다. */
	@Transactional
	public ImportFailureResponse retryImportFailure(Long failureId, Long memberId) {
		IngredientImportFailure failure = importFailureRepository.findById(failureId)
				.orElseThrow(() -> new BusinessException(ErrorCode.INGREDIENT_IMPORT_FAILURE_NOT_FOUND));
		if (failure.isResolved()) {
			throw new BusinessException(ErrorCode.INGREDIENT_IMPORT_FAILURE_ALREADY_RESOLVED);
		}
		RegisterIngredientMasterRequest dto = jsonMapper.readValue(
				failure.getPayloadJson(), RegisterIngredientMasterRequest.class);
		if (repository.existsByExternalId(dto.externalId())) {
			failure.recordRetryFailure("이미 등록된 외부 원료 ID입니다.");
			return ImportFailureResponse.from(failure);
		}
		register(memberId, dto);
		failure.resolve(LocalDateTime.now());
		log.info("[INGREDIENT] import failure retried and resolved id={} externalId={} by={}",
				failureId, dto.externalId(), memberId);
		return ImportFailureResponse.from(failure);
	}

	private IngredientImportFailure toFailure(RegisterIngredientMasterRequest item, String message, Long memberId) {
		return IngredientImportFailure.of(item.externalId(), jsonMapper.writeValueAsString(item), message, memberId);
	}

	@Transactional
	public IngredientMasterResponse update(String externalId, Long memberId, UpdateIngredientMasterRequest dto) {
		IngredientMaster entity = getOrThrow(externalId);
		entity.update(dto.casNumber(), dto.name(), joinSynonyms(dto.synonyms()), dto.supplierName(),
				dto.safetyNotes(), dto.regulatoryNotes(), dto.pyramid(), writeProfile(dto.profile()),
				dto.pricePerKg(), dto.priceCurrency(), dto.riskTier());
		log.info("[INGREDIENT] master updated externalId={} by={}", externalId, memberId);
		return toResponse(entity);
	}

	public IngredientMasterResponse get(String externalId) {
		return toResponse(getOrThrow(externalId));
	}

	/**
	 * 한 번도 처방에 쓰이지 않은 원료도 검색된다 - 이 목록은 관측 미러가 아니라 등록된 마스터 전체다.
	 * 조향 AI의 registry 규모(약 3만 건)까지 등록될 수 있어 페이지네이션한다(BE-085 후속).
	 */
	public PageResponse<IngredientMasterResponse> search(String query, Pageable pageable) {
		var page = (query == null || query.isBlank())
				? repository.findAll(pageable)
				: repository.findByNameContainingIgnoreCaseOrCasNumberContainingIgnoreCase(query, query, pageable);
		return PageResponse.of(page.map(this::toResponse));
	}

	private IngredientMasterResponse toResponse(IngredientMaster entity) {
		return IngredientMasterResponse.from(entity, jsonMapper);
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

	private String writeProfile(JsonNode profile) {
		return profile == null ? null : jsonMapper.writeValueAsString(profile);
	}
}
