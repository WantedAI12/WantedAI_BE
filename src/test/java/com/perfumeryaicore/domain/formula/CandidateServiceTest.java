package com.perfumeryaicore.domain.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionRepository;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.formula.service.CandidateVersionMapper;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 후보 접근 제어가 생성자 본인이 아니라 프로젝트 멤버십 기준으로 동작하는지 검증한다.
 */
class CandidateServiceTest {

	private static final long PROJECT_ID = 10L;

	private final CandidateRepository candidateRepository = mock(CandidateRepository.class);
	private final CandidateVersionRepository candidateVersionRepository = mock(CandidateVersionRepository.class);
	private final CandidateVersionIngredientRepository ingredientRepository =
			mock(CandidateVersionIngredientRepository.class);
	private final CandidateVersionMapper versionMapper = mock(CandidateVersionMapper.class);
	private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);
	private final CandidateService service = new CandidateService(
			candidateRepository, candidateVersionRepository, ingredientRepository, versionMapper, accessGuard);

	private static Candidate withId(Candidate candidate, long id) {
		try {
			Field field = Candidate.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(candidate, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return candidate;
	}

	@Test
	void a_project_member_who_is_not_the_creator_can_still_read_the_candidate() {
		Candidate candidate = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 100L);
		when(candidateRepository.findById(100L)).thenReturn(Optional.of(candidate));
		when(accessGuard.isMember(PROJECT_ID, 2L)).thenReturn(true);

		assertThat(service.get(100L, 2L).candidateId()).isEqualTo(100L);
	}

	@Test
	void a_non_member_is_denied_even_if_someone_else_owns_the_candidate() {
		Candidate candidate = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 100L);
		when(candidateRepository.findById(100L)).thenReturn(Optional.of(candidate));
		when(accessGuard.isMember(PROJECT_ID, 999L)).thenReturn(false);

		assertThatThrownBy(() -> service.get(100L, 999L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_ACCESS_DENIED);
	}

	@Test
	void listByRequest_only_returns_candidates_in_projects_the_caller_belongs_to() {
		Candidate visible = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 100L);
		Candidate hidden = withId(Candidate.create(1L, 20L, 1L, null), 101L);
		when(candidateRepository.findByRequestIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(visible, hidden));
		when(accessGuard.isMember(PROJECT_ID, 2L)).thenReturn(true);
		when(accessGuard.isMember(20L, 2L)).thenReturn(false);

		assertThat(service.listByRequest(1L, 2L))
				.extracting(CandidateResponse::candidateId)
				.containsExactly(100L);
	}
}
