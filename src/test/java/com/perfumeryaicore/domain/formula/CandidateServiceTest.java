package com.perfumeryaicore.domain.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.entity.CandidateVersion;
import com.perfumeryaicore.domain.formula.entity.CandidateVersionIngredient;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionRepository;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.formula.service.CandidateVersionMapper;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.request.service.FragranceRequestService;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
	private final FragranceRequestService fragranceRequestService = mock(FragranceRequestService.class);
	private final CandidateService service = new CandidateService(
			candidateRepository, candidateVersionRepository, ingredientRepository, versionMapper, accessGuard,
			fragranceRequestService);

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

	private static CandidateVersion withId(CandidateVersion version, long id) {
		try {
			Field field = CandidateVersion.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(version, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return version;
	}

	@Test
	void a_project_member_who_is_not_the_creator_can_still_read_the_candidate() {
		Candidate candidate = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 100L);
		when(candidateRepository.findById(100L)).thenReturn(Optional.of(candidate));
		when(accessGuard.isMember(PROJECT_ID, 2L)).thenReturn(true);

		assertThat(service.get(100L, 2L).candidateId()).isEqualTo(100L);
	}

	/**
	 * 화면에 "향 요청 #N"으로 보여줄 번호는 전체가 공용으로 쓰는 요청 ID가 아니라 프로젝트 안에서
	 * 1부터 매긴 순번이어야 한다 - 다른 프로젝트·다른 사용자의 요청 개수가 섞이면 안 된다.
	 */
	@Test
	void the_response_carries_the_per_project_request_number_not_the_global_request_id() {
		Candidate candidate = withId(Candidate.create(13L, PROJECT_ID, 1L, null), 100L);
		when(candidateRepository.findById(100L)).thenReturn(Optional.of(candidate));
		when(accessGuard.isMember(PROJECT_ID, 2L)).thenReturn(true);
		when(fragranceRequestService.requestNumber(PROJECT_ID, 13L)).thenReturn(1);

		CandidateResponse response = service.get(100L, 2L);

		assertThat(response.requestId()).isEqualTo(13L);
		assertThat(response.requestNumber()).isEqualTo(1);
	}

	@Test
	void listByRequest_computes_the_request_number_once_for_all_candidates_of_the_request() {
		Candidate a = withId(Candidate.create(13L, PROJECT_ID, 1L, null), 100L);
		Candidate b = withId(Candidate.create(13L, PROJECT_ID, 1L, null), 101L);
		when(candidateRepository.findByRequestIdOrderByCreatedAtDesc(13L)).thenReturn(List.of(a, b));
		when(accessGuard.isMember(PROJECT_ID, 2L)).thenReturn(true);
		when(fragranceRequestService.requestNumber(PROJECT_ID, 13L)).thenReturn(3);

		List<CandidateResponse> responses = service.listByRequest(13L, 2L);

		assertThat(responses).extracting(CandidateResponse::requestNumber).containsExactly(3, 3);
		verify(fragranceRequestService).requestNumber(PROJECT_ID, 13L);
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

	/** BE-085 후속: 후보마다 버전/원료를 따로 조회하지 않고(N+1), 현재 버전 ID를 모아 한 번에 조회한다. */
	@Test
	void listByRequest_batches_version_and_ingredient_lookups_instead_of_querying_per_candidate() {
		Candidate a = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 100L);
		a.attachVersion(200L);
		Candidate b = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 101L);
		b.attachVersion(201L);
		when(candidateRepository.findByRequestIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(a, b));
		when(accessGuard.isMember(PROJECT_ID, 2L)).thenReturn(true);

		CandidateVersion versionA = withId(CandidateVersion.builder().candidateId(100L).createdBy(1L).build(), 200L);
		CandidateVersion versionB = withId(CandidateVersion.builder().candidateId(101L).createdBy(1L).build(), 201L);
		when(candidateVersionRepository.findAllById(List.of(200L, 201L))).thenReturn(List.of(versionA, versionB));
		CandidateVersionIngredient ingredientA = CandidateVersionIngredient.builder()
				.candidateVersionId(200L).ingredientExternalId("bergamot_oil").build();
		when(ingredientRepository.findByCandidateVersionIdIn(List.of(200L, 201L)))
				.thenReturn(List.of(ingredientA));

		service.listByRequest(1L, 2L);

		verify(candidateVersionRepository).findAllById(List.of(200L, 201L));
		verify(candidateVersionRepository, never()).findById(any());
		verify(ingredientRepository).findByCandidateVersionIdIn(List.of(200L, 201L));
		verify(ingredientRepository, never()).findByCandidateVersionId(any());
		verify(versionMapper).toResponse(versionA, List.of(ingredientA));
		verify(versionMapper).toResponse(versionB, List.of());
	}

	@Test
	void duplicate_is_forbidden_for_a_role_without_write_access() {
		Candidate source = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 100L);
		when(candidateRepository.findById(100L)).thenReturn(Optional.of(source));
		when(accessGuard.isMember(PROJECT_ID, 2L)).thenReturn(true);
		when(accessGuard.requireWriteRole(PROJECT_ID, 2L, ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND,
				ProjectRole.PRODUCT_BRAND)).thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.duplicate(100L, 2L, "재검토용"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
		verify(candidateRepository, never()).save(any());
	}

	@Test
	void duplicate_fails_when_the_source_has_no_current_version() {
		Candidate source = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 100L);
		when(candidateRepository.findById(100L)).thenReturn(Optional.of(source));
		when(accessGuard.isMember(PROJECT_ID, 1L)).thenReturn(true);

		assertThatThrownBy(() -> service.duplicate(100L, 1L, "재검토용"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_VERSION_NOT_FOUND);
	}

	@Test
	void duplicate_copies_the_current_version_and_ingredients_into_a_new_under_review_candidate() {
		Candidate source = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 100L);
		source.attachVersion(200L);
		when(candidateRepository.findById(100L)).thenReturn(Optional.of(source));
		when(accessGuard.isMember(PROJECT_ID, 1L)).thenReturn(true);

		CandidateVersion sourceVersion = withId(CandidateVersion.builder()
				.candidateId(100L)
				.cost(42.0)
				.rawResponse("{\"ok\":true}")
				.createdBy(1L)
				.build(), 200L);
		when(candidateVersionRepository.findById(200L)).thenReturn(Optional.of(sourceVersion));
		when(ingredientRepository.findByCandidateVersionId(200L)).thenReturn(List.of(
				CandidateVersionIngredient.builder()
						.candidateVersionId(200L)
						.ingredientExternalId("bergamot_oil")
						.ingredientName("Bergamot Oil")
						.concentratePercent(8.0)
						.build()));
		when(candidateRepository.save(any(Candidate.class)))
				.thenAnswer(inv -> withId(inv.getArgument(0, Candidate.class), 300L));
		when(candidateVersionRepository.save(any(CandidateVersion.class)))
				.thenAnswer(inv -> withId(inv.getArgument(0, CandidateVersion.class), 400L));

		CandidateResponse response = service.duplicate(100L, 1L, "베티버로 대체 검토");

		assertThat(response.candidateId()).isEqualTo(300L);
		assertThat(response.status().name()).isEqualTo("UNDER_REVIEW");
		assertThat(response.derivedFromCandidateId()).isEqualTo(100L);
		assertThat(response.derivedFromVersionId()).isEqualTo(200L);
		assertThat(response.derivationReason()).isEqualTo("베티버로 대체 검토");

		ArgumentCaptor<CandidateVersion> versionCaptor = ArgumentCaptor.forClass(CandidateVersion.class);
		verify(candidateVersionRepository).save(versionCaptor.capture());
		assertThat(versionCaptor.getValue().getCandidateId()).isEqualTo(300L);
		assertThat(versionCaptor.getValue().getRawResponse()).isEqualTo("{\"ok\":true}");
		assertThat(versionCaptor.getValue().getCost()).isEqualTo(42.0);

		ArgumentCaptor<List<CandidateVersionIngredient>> lineCaptor = ArgumentCaptor.forClass(List.class);
		verify(ingredientRepository).saveAll(lineCaptor.capture());
		assertThat(lineCaptor.getValue()).hasSize(1);
		assertThat(lineCaptor.getValue().get(0).getCandidateVersionId()).isEqualTo(400L);
		assertThat(lineCaptor.getValue().get(0).getIngredientExternalId()).isEqualTo("bergamot_oil");
	}

	@Test
	void restoreVersion_is_forbidden_for_a_role_without_write_access() {
		Candidate candidate = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 100L);
		when(candidateRepository.findById(100L)).thenReturn(Optional.of(candidate));
		when(accessGuard.isMember(PROJECT_ID, 2L)).thenReturn(true);
		when(accessGuard.requireWriteRole(PROJECT_ID, 2L, ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND,
				ProjectRole.PRODUCT_BRAND)).thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.restoreVersion(100L, 2L, 200L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
		verify(candidateVersionRepository, never()).save(any());
	}

	@Test
	void restoreVersion_rejects_a_version_belonging_to_a_different_candidate() {
		Candidate candidate = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 100L);
		when(candidateRepository.findById(100L)).thenReturn(Optional.of(candidate));
		when(accessGuard.isMember(PROJECT_ID, 1L)).thenReturn(true);

		CandidateVersion versionOfAnotherCandidate = withId(CandidateVersion.builder()
				.candidateId(999L)
				.createdBy(1L)
				.build(), 200L);
		when(candidateVersionRepository.findById(200L)).thenReturn(Optional.of(versionOfAnotherCandidate));

		assertThatThrownBy(() -> service.restoreVersion(100L, 1L, 200L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_VERSION_NOT_FOUND);
	}

	/**
	 * v1 복원으로 만든 v3은 직전 버전(v2)을 parentVersionId로 잇고, restoredFromVersionId로만
	 * v1을 가리킨다 - v2 기록 자체는 지우거나 건드리지 않는다(BE-026).
	 */
	@Test
	void restoreVersion_creates_a_new_version_chained_after_the_current_one_not_overwriting_history() {
		Candidate candidate = withId(Candidate.create(1L, PROJECT_ID, 1L, null), 100L);
		candidate.attachVersion(250L); // v2가 현재 버전
		when(candidateRepository.findById(100L)).thenReturn(Optional.of(candidate));
		when(accessGuard.isMember(PROJECT_ID, 1L)).thenReturn(true);

		CandidateVersion v1 = withId(CandidateVersion.builder()
				.candidateId(100L)
				.cost(10.0)
				.rawResponse("{\"v\":1}")
				.createdBy(1L)
				.build(), 200L);
		when(candidateVersionRepository.findById(200L)).thenReturn(Optional.of(v1));
		when(ingredientRepository.findByCandidateVersionId(200L)).thenReturn(List.of());
		when(candidateVersionRepository.save(any(CandidateVersion.class)))
				.thenAnswer(inv -> withId(inv.getArgument(0, CandidateVersion.class), 300L));

		service.restoreVersion(100L, 1L, 200L);

		assertThat(candidate.getCurrentVersionId()).isEqualTo(300L);

		ArgumentCaptor<CandidateVersion> captor = ArgumentCaptor.forClass(CandidateVersion.class);
		verify(candidateVersionRepository).save(captor.capture());
		assertThat(captor.getValue().getParentVersionId()).isEqualTo(250L); // v2 다음으로 이어짐
		assertThat(captor.getValue().getRestoredFromVersionId()).isEqualTo(200L); // v1을 복원했음을 추적
		assertThat(captor.getValue().getRawResponse()).isEqualTo("{\"v\":1}");
	}
}
