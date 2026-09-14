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
import com.perfumeryaicore.domain.supply.entity.SupplyReviewStatus;
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
	void register_rejects_a_price_increase_with_missing_price_fields() {
		assertThatThrownBy(() -> service.register("bergamot_oil", 1L,
				new RegisterSupplyChangeRequest(10L, SupplyChangeType.PRICE_INCREASE, null, 140.0, "메모")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SUPPLY_CHANGE_PRICE_FIELDS_INCONSISTENT);
		verify(changeRepository, never()).save(any());
	}

	@Test
	void register_rejects_a_price_increase_whose_prices_actually_went_down() {
		assertThatThrownBy(() -> service.register("bergamot_oil", 1L,
				new RegisterSupplyChangeRequest(10L, SupplyChangeType.PRICE_INCREASE, 140.0, 90.0, "메모")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SUPPLY_CHANGE_PRICE_FIELDS_INCONSISTENT);
		verify(changeRepository, never()).save(any());
	}

	@Test
	void register_rejects_a_price_decrease_whose_prices_actually_went_up() {
		assertThatThrownBy(() -> service.register("bergamot_oil", 1L,
				new RegisterSupplyChangeRequest(10L, SupplyChangeType.PRICE_DECREASE, 90.0, 140.0, "메모")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SUPPLY_CHANGE_PRICE_FIELDS_INCONSISTENT);
		verify(changeRepository, never()).save(any());
	}

	@Test
	void register_rejects_a_price_change_with_equal_prices() {
		assertThatThrownBy(() -> service.register("bergamot_oil", 1L,
				new RegisterSupplyChangeRequest(10L, SupplyChangeType.PRICE_INCREASE, 100.0, 100.0, "메모")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SUPPLY_CHANGE_PRICE_FIELDS_INCONSISTENT);
	}

	@Test
	void register_allows_a_non_price_change_type_without_any_price_fields() {
		when(changeRepository.save(any(SupplyChange.class))).thenAnswer(inv -> withId(inv.getArgument(0), 500L));
		when(candidateRepository.findByProjectIdIn(List.of(10L))).thenReturn(List.of());

		var response = service.register("bergamot_oil", 1L,
				new RegisterSupplyChangeRequest(10L, SupplyChangeType.DISCONTINUED, null, null, "단종 통보"));

		assertThat(response.affectedCandidateCount()).isEqualTo(0);
	}

	@Test
	void pendingReviews_is_forbidden_for_a_non_member() {
		when(accessGuard.requireMember(10L, 1L)).thenThrow(new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED));

		assertThatThrownBy(() -> service.pendingReviews(10L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
	}

	@Test
	void pendingReviews_returns_an_empty_list_when_the_project_has_no_supply_changes() {
		when(changeRepository.findByProjectId(10L)).thenReturn(List.of());

		assertThat(service.pendingReviews(10L, 1L)).isEmpty();
		verify(affectedRepository, never())
				.findBySupplyChangeIdInAndReviewStatusOrderByCreatedAtDesc(any(), any());
	}

	@Test
	void pendingReviews_pairs_each_pending_affected_candidate_with_its_originating_change() {
		SupplyChange change = withId(SupplyChange.create(
				10L, "bergamot_oil", SupplyChangeType.PRICE_INCREASE, 90.0, 140.0, "환율 급등", 1L), 500L);
		when(changeRepository.findByProjectId(10L)).thenReturn(List.of(change));
		SupplyChangeAffectedCandidate affected = SupplyChangeAffectedCandidate.of(500L, 100L, 200L, 8.0);
		when(affectedRepository.findBySupplyChangeIdInAndReviewStatusOrderByCreatedAtDesc(
				List.of(500L), SupplyReviewStatus.PENDING_REVIEW))
				.thenReturn(List.of(affected));

		var result = service.pendingReviews(10L, 1L);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).supplyChangeId()).isEqualTo(500L);
		assertThat(result.get(0).ingredientId()).isEqualTo("bergamot_oil");
		assertThat(result.get(0).changeType()).isEqualTo(SupplyChangeType.PRICE_INCREASE);
		assertThat(result.get(0).candidateId()).isEqualTo(100L);
	}

	@Test
	void get_unknown_change_is_not_found() {
		when(changeRepository.findById(9L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(9L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SUPPLY_CHANGE_NOT_FOUND);
	}

	/** BE-079: KEEP_FORMULA/DISCARD_CANDIDATE는 확정된 결정이므로 영향 행을 REVIEWED로 닫는다. */
	@Test
	void record_decision_marks_the_matching_affected_row_reviewed_for_a_final_decision() {
		Candidate candidate = withId(Candidate.create(1L, 10L, 1L, null), 100L);
		when(candidateRepository.findById(100L)).thenReturn(Optional.of(candidate));
		when(decisionRepository.save(any(SupplyReviewDecision.class))).thenAnswer(inv -> inv.getArgument(0));
		SupplyChangeAffectedCandidate affected =
				SupplyChangeAffectedCandidate.of(500L, 100L, 200L, 8.0);
		when(affectedRepository.findBySupplyChangeIdAndCandidateId(500L, 100L))
				.thenReturn(Optional.of(affected));

		service.recordDecision(100L, 1L, new RecordSupplyReviewDecisionRequest(
				500L, SupplyReviewDecisionType.KEEP_FORMULA, "영향 미미해 그대로 유지"));

		assertThat(affected.getReviewStatus().name()).isEqualTo("REVIEWED");
	}

	/**
	 * BE-079: REVISE_FORMULA(조향식 수정 예정)는 아직 아무것도 해결되지 않았다 - 실제 재평가
	 * 완료 전까지 영향 행을 REVIEWED로 조기 완료 처리하지 않는다.
	 */
	@Test
	void record_decision_does_not_mark_reviewed_when_revision_is_only_planned() {
		Candidate candidate = withId(Candidate.create(1L, 10L, 1L, null), 100L);
		when(candidateRepository.findById(100L)).thenReturn(Optional.of(candidate));
		when(decisionRepository.save(any(SupplyReviewDecision.class))).thenAnswer(inv -> inv.getArgument(0));
		SupplyChangeAffectedCandidate affected =
				SupplyChangeAffectedCandidate.of(500L, 100L, 200L, 8.0);

		service.recordDecision(100L, 1L, new RecordSupplyReviewDecisionRequest(
				500L, SupplyReviewDecisionType.REVISE_FORMULA, "베티버로 일부 대체"));

		assertThat(affected.getReviewStatus().name()).isEqualTo("PENDING_REVIEW");
		verify(affectedRepository, never()).findBySupplyChangeIdAndCandidateId(any(), any());
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
