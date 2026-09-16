package com.perfumeryaicore.domain.ingredient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.entity.CandidateVersion;
import com.perfumeryaicore.domain.formula.entity.CandidateVersionIngredient;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionRepository;
import com.perfumeryaicore.domain.ingredient.dto.response.IngredientResponse;
import com.perfumeryaicore.domain.ingredient.service.IngredientQueryService;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngredientQueryServiceTest {

	private static final long PROJECT_ID = 10L;
	private static final long OTHER_PROJECT_ID = 20L;
	private static final long MEMBER_ID = 1L;

	private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);
	private final CandidateRepository candidateRepository = mock(CandidateRepository.class);
	private final CandidateVersionRepository candidateVersionRepository = mock(CandidateVersionRepository.class);
	private final CandidateVersionIngredientRepository lineRepository =
			mock(CandidateVersionIngredientRepository.class);
	private final IngredientQueryService service = new IngredientQueryService(
			accessGuard, candidateRepository, candidateVersionRepository, lineRepository);

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

	private static CandidateVersion version(long id, long candidateId, LocalDateTime createdAt) {
		CandidateVersion v = withId(CandidateVersion.builder().candidateId(candidateId).createdBy(1L).build(), id);
		try {
			Field f = CandidateVersion.class.getSuperclass().getDeclaredField("createdAt");
			f.setAccessible(true);
			f.set(v, createdAt);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return v;
	}

	private static CandidateVersionIngredient line(long versionId, String extId, String name,
			String pyramid, Double price) {
		return CandidateVersionIngredient.builder()
				.candidateVersionId(versionId)
				.ingredientExternalId(extId)
				.ingredientName(name)
				.pyramid(pyramid)
				.concentratePercent(10.0)
				.pricePerKg(price)
				.availability(0.9)
				.build();
	}

	private void memberOfProject(long projectId) {
		when(accessGuard.requireMember(projectId, MEMBER_ID)).thenReturn(ProjectRole.PERFUMER);
	}

	@Test
	void non_member_is_denied() {
		when(accessGuard.requireMember(PROJECT_ID, MEMBER_ID))
				.thenThrow(new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED));

		assertThatThrownBy(() -> service.list(MEMBER_ID, PROJECT_ID, null, null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
	}

	@Test
	void returns_empty_when_the_project_has_no_candidates() {
		memberOfProject(PROJECT_ID);
		when(candidateRepository.findByProjectIdIn(List.of(PROJECT_ID))).thenReturn(List.of());

		assertThat(service.list(MEMBER_ID, PROJECT_ID, null, null)).isEmpty();
	}

	@Test
	void dedupes_by_external_id_and_keeps_the_latest_version_values() {
		memberOfProject(PROJECT_ID);
		Candidate c1 = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 100L);
		Candidate c2 = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 101L);
		when(candidateRepository.findByProjectIdIn(List.of(PROJECT_ID))).thenReturn(List.of(c1, c2));

		CandidateVersion older = version(200L, 100L, LocalDateTime.of(2026, 1, 1, 0, 0));
		CandidateVersion newer = version(201L, 101L, LocalDateTime.of(2026, 6, 1, 0, 0));
		when(candidateVersionRepository.findByCandidateIdIn(List.of(100L, 101L)))
				.thenReturn(List.of(older, newer));

		when(lineRepository.findByCandidateVersionIdIn(anyList())).thenReturn(List.of(
				line(200L, "iso_e_super", "Iso E Super (old name)", "base", 20.0),
				line(201L, "iso_e_super", "Iso E Super", "base", 24.0)));

		List<IngredientResponse> result = service.list(MEMBER_ID, PROJECT_ID, null, null);

		assertThat(result).hasSize(1);
		IngredientResponse iso = result.get(0);
		assertThat(iso.ingredientId()).isEqualTo("iso_e_super");
		assertThat(iso.name()).isEqualTo("Iso E Super");
		assertThat(iso.pricePerKg()).isEqualTo(24.0);
		assertThat(iso.usedInCandidateCount()).isEqualTo(2);
	}

	/**
	 * BE-069: 같은 외부 원료 ID가 다른 프로젝트에도 등장하더라도, 조회한 프로젝트에 없는 값은
	 * 절대 섞여 들어오지 않는다 — {@code findByProjectIdIn}을 그 프로젝트 하나로만 호출한다.
	 */
	@Test
	void does_not_mix_observations_from_a_different_project() {
		memberOfProject(PROJECT_ID);
		Candidate ownCandidate = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 100L);
		when(candidateRepository.findByProjectIdIn(List.of(PROJECT_ID))).thenReturn(List.of(ownCandidate));
		when(candidateVersionRepository.findByCandidateIdIn(List.of(100L)))
				.thenReturn(List.of(version(200L, 100L, LocalDateTime.of(2026, 1, 1, 0, 0))));
		when(lineRepository.findByCandidateVersionIdIn(anyList())).thenReturn(List.of(
				line(200L, "iso_e_super", "Iso E Super", "base", 20.0)));

		List<IngredientResponse> result = service.list(MEMBER_ID, PROJECT_ID, null, null);

		assertThat(result).extracting(IngredientResponse::pricePerKg).containsExactly(20.0);
		// 다른 프로젝트(OTHER_PROJECT_ID)의 후보 목록을 조회하지 않았다.
		org.mockito.Mockito.verify(candidateRepository).findByProjectIdIn(List.of(PROJECT_ID));
		org.mockito.Mockito.verify(candidateRepository, org.mockito.Mockito.never())
				.findByProjectIdIn(List.of(OTHER_PROJECT_ID));
	}

	@Test
	void detail_of_unknown_ingredient_is_not_found() {
		memberOfProject(PROJECT_ID);
		when(candidateRepository.findByProjectIdIn(List.of(PROJECT_ID))).thenReturn(List.of());

		assertThatThrownBy(() -> service.get(MEMBER_ID, PROJECT_ID, "ghost_molecule"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INGREDIENT_NOT_FOUND);
	}

	@Test
	void list_filters_by_pyramid() {
		memberOfProject(PROJECT_ID);
		Candidate c = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 100L);
		when(candidateRepository.findByProjectIdIn(List.of(PROJECT_ID))).thenReturn(List.of(c));
		when(candidateVersionRepository.findByCandidateIdIn(List.of(100L)))
				.thenReturn(List.of(version(200L, 100L, LocalDateTime.of(2026, 1, 1, 0, 0))));
		when(lineRepository.findByCandidateVersionIdIn(anyList())).thenReturn(List.of(
				line(200L, "bergamot_oil", "Bergamot Oil", "top", 90.0),
				line(200L, "ambroxan", "Ambroxan", "base", 130.0)));

		assertThat(service.list(MEMBER_ID, PROJECT_ID, null, "top")).extracting(IngredientResponse::ingredientId)
				.containsExactly("bergamot_oil");
	}
}
