package com.perfumeryaicore.domain.ingredient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.ingredient.dto.request.RegisterIngredientMasterRequest;
import com.perfumeryaicore.domain.ingredient.dto.request.UpdateIngredientMasterRequest;
import com.perfumeryaicore.domain.ingredient.dto.response.IngredientMasterResponse;
import com.perfumeryaicore.domain.ingredient.entity.IngredientMaster;
import com.perfumeryaicore.domain.ingredient.repository.IngredientMasterRepository;
import com.perfumeryaicore.domain.ingredient.service.IngredientMasterService;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** BE-062: 처방에 쓰인 적 없는 원료도 등록·검색 가능한 마스터 - 관측 미러와 별개로 동작해야 한다. */
class IngredientMasterServiceTest {

	private final IngredientMasterRepository repository = mock(IngredientMasterRepository.class);
	private final IngredientMasterService service = new IngredientMasterService(repository);

	@Test
	void register_rejects_a_duplicate_external_id() {
		when(repository.existsByExternalId("bergamot_oil")).thenReturn(true);
		RegisterIngredientMasterRequest dto = new RegisterIngredientMasterRequest(
				"bergamot_oil", "8007-75-8", "Bergamot Oil", List.of("Citrus bergamia"), "Firmenich", null, null);

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
				List.of("Citrus bergamia", "Bergamot"), "Firmenich", "IFRA 41차 제한", null);

		IngredientMasterResponse response = service.register(1L, dto);

		assertThat(response.externalId()).isEqualTo("bergamot_oil");
		assertThat(response.synonyms()).containsExactly("Citrus bergamia", "Bergamot");
		assertThat(response.safetyNotes()).isEqualTo("IFRA 41차 제한");
	}

	@Test
	void a_never_used_ingredient_can_still_be_found_by_search() {
		IngredientMaster entity = IngredientMaster.register(
				"iso_e_super", null, "Iso E Super", null, null, null, null, 1L);
		when(repository.findByNameContainingIgnoreCaseOrCasNumberContainingIgnoreCase("Iso", "Iso"))
				.thenReturn(List.of(entity));

		List<IngredientMasterResponse> results = service.search("Iso");

		assertThat(results).hasSize(1);
		assertThat(results.get(0).externalId()).isEqualTo("iso_e_super");
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
				"iso_e_super", null, "Iso E Super", null, null, null, null, 1L);
		when(repository.findByExternalId("iso_e_super")).thenReturn(Optional.of(entity));
		UpdateIngredientMasterRequest dto = new UpdateIngredientMasterRequest(
				"54464-57-2", "Iso E Super (updated)", null, "Givaudan", null, null);

		IngredientMasterResponse response = service.update("iso_e_super", 1L, dto);

		assertThat(response.externalId()).isEqualTo("iso_e_super");
		assertThat(response.casNumber()).isEqualTo("54464-57-2");
		assertThat(response.name()).isEqualTo("Iso E Super (updated)");
		assertThat(response.supplierName()).isEqualTo("Givaudan");
	}
}
