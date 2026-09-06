package com.perfumeryaicore.domain.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.entity.CandidateVersion;
import com.perfumeryaicore.domain.formula.entity.CandidateVersionIngredient;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionRepository;
import com.perfumeryaicore.domain.formula.service.CandidatePersistenceService;
import com.perfumeryaicore.global.client.PerfumeryAiResult;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse.Deployment;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse.RecipeLine;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CandidatePersistenceServiceTest {

	private final CandidateRepository candidateRepository = mock(CandidateRepository.class);
	private final CandidateVersionRepository candidateVersionRepository = mock(CandidateVersionRepository.class);
	private final CandidateVersionIngredientRepository ingredientRepository =
			mock(CandidateVersionIngredientRepository.class);

	private final CandidatePersistenceService service = new CandidatePersistenceService(
			candidateRepository, candidateVersionRepository, ingredientRepository);

	@Test
	void persists_candidate_first_version_and_ingredient_lines() {
		when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> {
			Candidate c = inv.getArgument(0);
			ReflectionTestUtils.setField(c, "id", 500L);
			return c;
		});
		when(candidateVersionRepository.save(any(CandidateVersion.class))).thenAnswer(inv -> {
			CandidateVersion v = inv.getArgument(0);
			ReflectionTestUtils.setField(v, "id", 900L);
			return v;
		});

		FormulaGenerationResponse parsed = new FormulaGenerationResponse(
				"prototype_ready", "안전 조건 충족", "f-1", 0.9, 42.0,
				List.of(
						new RecipeLine("dihydromyrcenol", "Dihydromyrcenol", "top", 23.5, 3.5, 18.0, 0.99),
						new RecipeLine("iso_e_super", "Iso E Super", "base", 20.0, 3.0, 85.0, 0.99)),
				List.of(0, 15, 60, 240, 480), null, null, null, "claim boundary",
				null, "headspace-olfactory-twin-2.2",
				new Deployment("modal", "cpu", false, "wheel-sha", "registry-sha", 29240));
		PerfumeryAiResult result = new PerfumeryAiResult("{\"status\":\"prototype_ready\"}", parsed, 1690L);

		Long candidateId = service.persist(5L, 10L, 1L, 77L, result);

		assertThat(candidateId).isEqualTo(500L);

		var candidateCaptor = org.mockito.ArgumentCaptor.forClass(Candidate.class);
		verify(candidateRepository).save(candidateCaptor.capture());
		assertThat(candidateCaptor.getValue().getRequestId()).isEqualTo(5L);
		assertThat(candidateCaptor.getValue().getJobId()).isEqualTo(77L);
		assertThat(candidateCaptor.getValue().getCreatedBy()).isEqualTo(1L);
		assertThat(candidateCaptor.getValue().getCurrentVersionId()).isEqualTo(900L); // attachVersion 반영

		var versionCaptor = org.mockito.ArgumentCaptor.forClass(CandidateVersion.class);
		verify(candidateVersionRepository).save(versionCaptor.capture());
		CandidateVersion version = versionCaptor.getValue();
		assertThat(version.getCandidateId()).isEqualTo(500L);
		assertThat(version.getAiProvider()).isEqualTo("modal");
		assertThat(version.getAiGpuUsed()).isFalse();
		assertThat(version.getAiResponseStatus()).isEqualTo("prototype_ready");
		assertThat(version.getAiLatencyMs()).isEqualTo(1690L);
		assertThat(version.getRawResponse()).isEqualTo("{\"status\":\"prototype_ready\"}");
		assertThat(version.getCost()).isEqualTo(42.0);

		var linesCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
		verify(ingredientRepository).saveAll(linesCaptor.capture());
		@SuppressWarnings("unchecked")
		List<CandidateVersionIngredient> lines = linesCaptor.getValue();
		assertThat(lines).hasSize(2);
		assertThat(lines.get(0).getCandidateVersionId()).isEqualTo(900L);
		assertThat(lines.get(0).getIngredientExternalId()).isEqualTo("dihydromyrcenol");
		assertThat(lines.get(1).getIngredientName()).isEqualTo("Iso E Super");
	}

	@Test
	void empty_recipe_saves_no_ingredient_lines() {
		when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> {
			Candidate c = inv.getArgument(0);
			ReflectionTestUtils.setField(c, "id", 1L);
			return c;
		});
		when(candidateVersionRepository.save(any(CandidateVersion.class))).thenAnswer(inv -> {
			CandidateVersion v = inv.getArgument(0);
			ReflectionTestUtils.setField(v, "id", 1L);
			return v;
		});

		FormulaGenerationResponse parsed = new FormulaGenerationResponse(
				"prototype_ready", null, null, null, null, List.of(),
				null, null, null, null, null, null, null, null);
		PerfumeryAiResult result = new PerfumeryAiResult("{}", parsed, 100L);

		service.persist(5L, 10L, 1L, 77L, result);

		verify(ingredientRepository, never()).saveAll(anyList());
	}
}
