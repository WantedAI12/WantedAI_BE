package com.perfumeryaicore.domain.ingredient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.ingredient.dto.request.RegisterIngredientMasterRequest;
import com.perfumeryaicore.domain.ingredient.dto.request.UpdateIngredientMasterRequest;
import com.perfumeryaicore.domain.ingredient.dto.response.BulkImportResultResponse;
import com.perfumeryaicore.domain.ingredient.dto.response.ImportFailureResponse;
import com.perfumeryaicore.domain.ingredient.dto.response.IngredientMasterResponse;
import com.perfumeryaicore.domain.ingredient.entity.IngredientImportFailure;
import com.perfumeryaicore.domain.ingredient.entity.IngredientMaster;
import com.perfumeryaicore.domain.ingredient.repository.IngredientImportFailureRepository;
import com.perfumeryaicore.domain.ingredient.repository.IngredientMasterRepository;
import com.perfumeryaicore.domain.ingredient.service.IngredientMasterService;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** BE-062: 처방에 쓰인 적 없는 원료도 등록·검색 가능한 마스터 - 관측 미러와 별개로 동작해야 한다. */
class IngredientMasterServiceTest {

	private final IngredientMasterRepository repository = mock(IngredientMasterRepository.class);
	private final IngredientImportFailureRepository importFailureRepository =
			mock(IngredientImportFailureRepository.class);
	private final IngredientMasterService service =
			new IngredientMasterService(repository, importFailureRepository);
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void register_rejects_a_duplicate_external_id() {
		when(repository.existsByExternalId("bergamot_oil")).thenReturn(true);
		RegisterIngredientMasterRequest dto = new RegisterIngredientMasterRequest(
				"bergamot_oil", "8007-75-8", "Bergamot Oil", List.of("Citrus bergamia"), "Firmenich", null, null,
				null, null, null, null, null);

		assertThatThrownBy(() -> service.register(1L, dto))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INGREDIENT_MASTER_ALREADY_EXISTS);
	}

	@Test
	void register_saves_synonyms_as_a_joined_list_and_returns_them_split_back_out() {
		when(repository.existsByExternalId("bergamot_oil")).thenReturn(false);
		when(repository.save(any(IngredientMaster.class))).thenAnswer(inv -> inv.getArgument(0));
		RegisterIngredientMasterRequest dto = new RegisterIngredientMasterRequest(
				"bergamot_oil", "8007-75-8", "Bergamot Oil",
				List.of("Citrus bergamia", "Bergamot"), "Firmenich", "IFRA 41차 제한", null,
				null, null, null, null, null);

		IngredientMasterResponse response = service.register(1L, dto);

		assertThat(response.externalId()).isEqualTo("bergamot_oil");
		assertThat(response.synonyms()).containsExactly("Citrus bergamia", "Bergamot");
		assertThat(response.safetyNotes()).isEqualTo("IFRA 41차 제한");
	}

	@Test
	void register_stores_the_odor_profile_and_price_and_returns_them_back_out() {
		when(repository.existsByExternalId("dihydromyrcenol")).thenReturn(false);
		when(repository.save(any(IngredientMaster.class))).thenAnswer(inv -> inv.getArgument(0));
		JsonNode profile = jsonMapper.readTree("{\"citrus\":0.7,\"fresh\":1.0}");
		RegisterIngredientMasterRequest dto = new RegisterIngredientMasterRequest(
				"dihydromyrcenol", "18479-58-8", "Dihydromyrcenol", null, null, null, null,
				"top", profile, 18.0, "USD_estimate", 1);

		IngredientMasterResponse response = service.register(1L, dto);

		assertThat(response.pyramid()).isEqualTo("top");
		assertThat(response.profile().get("citrus").asDouble()).isEqualTo(0.7);
		assertThat(response.pricePerKg()).isEqualTo(18.0);
		assertThat(response.priceCurrency()).isEqualTo("USD_estimate");
		assertThat(response.riskTier()).isEqualTo(1);
	}

	@Test
	void a_never_used_ingredient_can_still_be_found_by_search() {
		IngredientMaster entity = IngredientMaster.register(
				"iso_e_super", null, "Iso E Super", null, null, null, null, 1L, null, null, null, null, null);
		var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
		when(repository.findByNameContainingIgnoreCaseOrCasNumberContainingIgnoreCase("Iso", "Iso", pageable))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(entity)));

		var results = service.search("Iso", pageable);

		assertThat(results.content()).hasSize(1);
		assertThat(results.content().get(0).externalId()).isEqualTo("iso_e_super");
	}

	/** BE-085 후속: 검색어 없이 호출하면 등록된 마스터 전체를 페이지 단위로 조회한다. */
	@Test
	void searching_without_a_query_paginates_the_full_registered_master_list() {
		IngredientMaster entity = IngredientMaster.register(
				"iso_e_super", null, "Iso E Super", null, null, null, null, 1L, null, null, null, null, null);
		var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
		when(repository.findAll(pageable))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(entity), pageable, 30000));

		var results = service.search(null, pageable);

		assertThat(results.content()).hasSize(1);
		assertThat(results.totalElements()).isEqualTo(30000);
	}

	@Test
	void get_throws_not_found_for_an_unregistered_external_id() {
		when(repository.findByExternalId("unknown")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get("unknown"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INGREDIENT_MASTER_NOT_FOUND);
	}

	/** externalId는 update()의 인자에 없다 - 신원을 여기서 바꿀 방법 자체가 없다. */
	@Test
	void update_changes_fields_but_never_the_external_id() {
		IngredientMaster entity = IngredientMaster.register(
				"iso_e_super", null, "Iso E Super", null, null, null, null, 1L, null, null, null, null, null);
		when(repository.findByExternalId("iso_e_super")).thenReturn(Optional.of(entity));
		UpdateIngredientMasterRequest dto = new UpdateIngredientMasterRequest(
				"54464-57-2", "Iso E Super (updated)", null, "Givaudan", null, null, null, null, null, null, null);

		IngredientMasterResponse response = service.update("iso_e_super", 1L, dto);

		assertThat(response.externalId()).isEqualTo("iso_e_super");
		assertThat(response.casNumber()).isEqualTo("54464-57-2");
		assertThat(response.name()).isEqualTo("Iso E Super (updated)");
		assertThat(response.supplierName()).isEqualTo("Givaudan");
	}

	/** BE-107: pyramid/profile/가격/risk_tier도 다른 필드와 같은 null-불변 규칙을 따른다. */
	@Test
	void update_can_also_set_the_odor_profile_fields() {
		IngredientMaster entity = IngredientMaster.register(
				"iso_e_super", null, "Iso E Super", null, null, null, null, 1L, null, null, null, null, null);
		when(repository.findByExternalId("iso_e_super")).thenReturn(Optional.of(entity));
		JsonNode profile = jsonMapper.readTree("{\"woody\":0.9}");
		UpdateIngredientMasterRequest dto = new UpdateIngredientMasterRequest(
				null, null, null, null, null, null, "base", profile, 85.0, "USD_estimate", 1);

		IngredientMasterResponse response = service.update("iso_e_super", 1L, dto);

		assertThat(response.pyramid()).isEqualTo("base");
		assertThat(response.profile().get("woody").asDouble()).isEqualTo(0.9);
		assertThat(response.pricePerKg()).isEqualTo(85.0);
		assertThat(response.riskTier()).isEqualTo(1);
	}

	private RegisterIngredientMasterRequest importDto(String externalId) {
		return new RegisterIngredientMasterRequest(externalId, null, "Name " + externalId, null, null, null, null,
				null, null, null, null, null);
	}

	/** BE-063~066: DB에 이미 있는 행과 배치 내부 중복 행만 실패로 남고, 나머지는 그대로 등록된다. */
	@Test
	void bulkImport_registers_valid_rows_and_queues_duplicates_as_failures() {
		when(repository.findByExternalIdIn(any())).thenReturn(
				List.of(IngredientMaster.register("already_registered", null, "Existing", null, null, null, null, 1L,
						null, null, null, null, null)));
		when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
		when(importFailureRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

		BulkImportResultResponse result = service.bulkImport(1L, List.of(
				importDto("new_one"),
				importDto("already_registered"),
				importDto("new_two"),
				importDto("new_two")));

		assertThat(result.totalCount()).isEqualTo(4);
		assertThat(result.succeededCount()).isEqualTo(2);
		assertThat(result.failedCount()).isEqualTo(2);
		assertThat(result.registered()).extracting("externalId").containsExactlyInAnyOrder("new_one", "new_two");
		assertThat(result.failures()).extracting("externalId")
				.containsExactlyInAnyOrder("already_registered", "new_two");
	}

	@Test
	void retryImportFailure_reregisters_the_stored_payload_and_resolves_it() {
		IngredientImportFailure failure = IngredientImportFailure.of(
				"retry_me", "{\"externalId\":\"retry_me\",\"name\":\"Retry Me\"}", "이미 등록된 외부 원료 ID입니다.", 1L);
		when(importFailureRepository.findById(9L)).thenReturn(Optional.of(failure));
		when(repository.existsByExternalId("retry_me")).thenReturn(false);
		when(repository.save(any(IngredientMaster.class))).thenAnswer(inv -> inv.getArgument(0));

		ImportFailureResponse response = service.retryImportFailure(9L, 1L);

		assertThat(response.resolved()).isTrue();
	}

	@Test
	void retryImportFailure_rejects_an_already_resolved_failure() {
		IngredientImportFailure failure = IngredientImportFailure.of("x", "{}", "err", 1L);
		failure.resolve(java.time.LocalDateTime.now());
		when(importFailureRepository.findById(9L)).thenReturn(Optional.of(failure));

		assertThatThrownBy(() -> service.retryImportFailure(9L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INGREDIENT_IMPORT_FAILURE_ALREADY_RESOLVED);
	}
}
