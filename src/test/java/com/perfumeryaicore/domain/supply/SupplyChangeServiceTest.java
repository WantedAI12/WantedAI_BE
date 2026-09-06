package com.perfumeryaicore.domain.supply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.entity.CandidateVersionIngredient;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.supply.dto.request.RecordSupplyReviewDecisionRequest;
import com.perfumeryaicore.domain.supply.dto.request.RegisterSupplyChangeRequest;
import com.perfumeryaicore.domain.supply.entity.SupplyChange;
import com.perfumeryaicore.domain.supply.entity.SupplyChangeAffectedCandidate;
import com.perfumeryaicore.domain.supply.entity.SupplyChangeType;
import com.perfumeryaicore.domain.supply.entity.SupplyReviewDecision;
import com.perfumeryaicore.domain.supply.entity.SupplyReviewDecisionType;
import com.perfumeryaicore.domain.supply.repository.SupplyChangeAffectedCandidateRepository;
import com.perfumeryaicore.domain.supply.repository.SupplyChangeRepository;
import com.perfumeryaicore.domain.supply.repository.SupplyReviewDecisionRepository;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SupplyChangeServiceTest {

	private final SupplyChangeRepository changeRepository = mock(SupplyChangeRepository.class);
	private final SupplyChangeAffectedCandidateRepository affectedRepository =
			mock(SupplyChangeAffectedCandidateRepository.class);
	private final SupplyReviewDecisionRepository decisionRepository = mock(SupplyReviewDecisionRepository.class);
	private final CandidateRepository candidateRepository = mock(CandidateRepository.class);
	private final CandidateVersionIngredientRepository lineRepository =
			mock(CandidateVersionIngredientRepository.class);
	private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);
	private final com.perfumeryaicore.domain.supply.service.SupplyChangeService service =
			new com.perfumeryaicore.domain.supply.service.SupplyChangeService(
					changeRepository, affectedRepository, decisionRepository,
					candidateRepository, lineRepository, accessGuard);

	private static <T> T withId(T entity, long id) {
		try {
			Field f = entity.getClass().getDeclaredField("id");
			f.setAccessible(true);
			f.set(entity, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return entity;
	}

	private RegisterSupplyChangeRequest priceJump() {
		return new RegisterSupplyChangeRequest(10L, SupplyChangeType.PRICE_INCREASE, 90.0, 140.0, "환율 급등");
	}

	@Test
	void register_is_forbidden_for_a_role_other_than_supplier_or_fragrance_rnd() {
		when(accessGuard.requireRole(eq(10L), eq(1L), any(ProjectRole.class), any(ProjectRole.class)))
				.thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.register("bergamot_oil", 1L, priceJump()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	@Test
	void register_records_candidates_whose_current_version_uses_the_ingredient() {
		when(changeRepository.save(any(SupplyChange.class))).thenAnswer(inv -> withId(inv.getArgument(0), 500L));

		Candidate uses = Candidate.create(1L, 10L, 1L, null);
		withId(uses, 100L);
		uses.attachVersion(200L);
		Candidate doesNot = Candidate.create(1L, 10L, 1L, null);
		withId(doesNot, 101L);
		doesNot.attachVersion(201L);
		when(candidateRepository.findByProjectIdIn(List.of(10L))).thenReturn(List.of(uses, doesNot));

		when(lineRepository.findByCandidateVersionIdIn(anyList())).thenReturn(List.of(
				CandidateVersionIngredient.builder().candidateVersionId(200L)
						.ingredientExternalId("bergamot_oil").ingredientName("Bergamot Oil")
						.concentratePercent(8.0).build(),
				CandidateVersionIngredient.builder().candidateVersionId(201L)
						.ingredientExternalId("vetiver").ingredientName("Vetiver")
						.concentratePercent(5.0).build()));

		var response = service.register("bergamot_oil", 1L, priceJump());

		assertThat(response.affectedCandidateCount()).isEqualTo(1);
		verify(affectedRepository).save(any(SupplyChangeAffectedCandidate.class));
	}

	@Test
	void get_unknown_change_is_not_found() {
		when(changeRepository.findById(9L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(9L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SUPPLY_CHANGE_NOT_FOUND);
	}

	@Test
	void record_decision_marks_the_matching_affected_row_reviewed() {
		Candidate candidate = withId(Candidate.create(1L, 10L, 1L, null), 100L);
		when(candidateRepository.findById(100L)).thenReturn(Optional.of(candidate));
		when(decisionRepository.save(any(SupplyReviewDecision.class))).thenAnswer(inv -> inv.getArgument(0));
		SupplyChangeAffectedCandidate affected =
				SupplyChangeAffectedCandidate.of(500L, 100L, 200L, 8.0);
		when(affectedRepository.findBySupplyChangeIdAndCandidateId(500L, 100L))
				.thenReturn(Optional.of(affected));

		service.recordDecision(100L, 1L, new RecordSupplyReviewDecisionRequest(
				500L, SupplyReviewDecisionType.REVISE_FORMULA, "베티버로 일부 대체"));

		assertThat(affected.getReviewStatus().name()).isEqualTo("REVIEWED");
	}

	@Test
	void record_decision_on_unknown_candidate_is_not_found() {
		when(candidateRepository.findById(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.recordDecision(404L, 1L, new RecordSupplyReviewDecisionRequest(
				null, SupplyReviewDecisionType.KEEP_FORMULA, "영향 미미")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_NOT_FOUND);
		verify(decisionRepository, never()).save(any());
	}
}
