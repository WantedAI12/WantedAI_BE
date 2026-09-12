package com.perfumeryaicore.domain.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.formula.dto.request.UpsertCandidateMemoRequest;
import com.perfumeryaicore.domain.formula.dto.response.CandidateMemoResponse;
import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.entity.CandidateMemo;
import com.perfumeryaicore.domain.formula.entity.CandidateMemoType;
import com.perfumeryaicore.domain.formula.repository.CandidateMemoRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionRepository;
import com.perfumeryaicore.domain.formula.service.CandidateMemoService;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.formula.service.CandidateVersionMapper;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 후보 메모 3종 CRUD와 revision 기반 낙관적 잠금을 검증한다.
 */
class CandidateMemoServiceTest {

	private static final long CANDIDATE_ID = 100L;
	private static final long MEMBER_ID = 1L;
	private static final long VERSION_ID = 500L;

	private final CandidateMemoRepository memoRepository = mock(CandidateMemoRepository.class);
	private final CandidateRepository candidateRepository = mock(CandidateRepository.class);
	private final CandidateVersionRepository candidateVersionRepository = mock(CandidateVersionRepository.class);
	private final CandidateVersionIngredientRepository ingredientRepository =
			mock(CandidateVersionIngredientRepository.class);
	private final CandidateVersionMapper versionMapper = mock(CandidateVersionMapper.class);
	private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);
	private final CandidateService candidateService = new CandidateService(
			candidateRepository, candidateVersionRepository, ingredientRepository, versionMapper, accessGuard);
	private final CandidateMemoService service = new CandidateMemoService(memoRepository, candidateService);

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

	@BeforeEach
	void candidateIsAccessible() {
		Candidate candidate = withId(Candidate.create(1L, 10L, MEMBER_ID, null), CANDIDATE_ID);
		candidate.attachVersion(VERSION_ID);
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
		when(accessGuard.isMember(10L, MEMBER_ID)).thenReturn(true);
	}

	@Test
	void list_returns_all_three_types_with_empty_defaults_when_nothing_saved_yet() {
		when(memoRepository.findByCandidateId(CANDIDATE_ID)).thenReturn(List.of());

		List<CandidateMemoResponse> memos = service.list(CANDIDATE_ID, MEMBER_ID);

		assertThat(memos).hasSize(CandidateMemoType.values().length);
		assertThat(memos).allSatisfy(m -> {
			assertThat(m.content()).isNull();
			assertThat(m.revision()).isEqualTo(0);
		});
	}

	@Test
	void upsert_creates_a_new_memo_when_none_exists_and_expected_revision_is_zero() {
		when(memoRepository.findByCandidateIdAndMemoType(CANDIDATE_ID, CandidateMemoType.REVIEW_NOTE))
				.thenReturn(Optional.empty());
		when(memoRepository.save(org.mockito.ArgumentMatchers.any(CandidateMemo.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		CandidateMemoResponse response = service.upsert(CANDIDATE_ID, MEMBER_ID, CandidateMemoType.REVIEW_NOTE,
				new UpsertCandidateMemoRequest("색이 살짝 진하다", 0));

		assertThat(response.content()).isEqualTo("색이 살짝 진하다");
		assertThat(response.revision()).isEqualTo(1);
		assertThat(response.authorId()).isEqualTo(MEMBER_ID);
	}

	@Test
	void creating_a_memo_that_someone_already_created_is_a_conflict() {
		when(memoRepository.findByCandidateIdAndMemoType(CANDIDATE_ID, CandidateMemoType.REVIEW_NOTE))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.upsert(CANDIDATE_ID, MEMBER_ID, CandidateMemoType.REVIEW_NOTE,
				new UpsertCandidateMemoRequest("내용", 1)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_MEMO_CONFLICT);
	}

	@Test
	void upsert_updates_an_existing_memo_when_revision_matches() {
		CandidateMemo memo = CandidateMemo.create(CANDIDATE_ID, CandidateMemoType.NEXT_EXPERIMENT_NOTE,
				"초안", VERSION_ID, MEMBER_ID);
		when(memoRepository.findByCandidateIdAndMemoType(CANDIDATE_ID, CandidateMemoType.NEXT_EXPERIMENT_NOTE))
				.thenReturn(Optional.of(memo));

		CandidateMemoResponse response = service.upsert(CANDIDATE_ID, MEMBER_ID, CandidateMemoType.NEXT_EXPERIMENT_NOTE,
				new UpsertCandidateMemoRequest("농도 재측정 필요", 1));

		assertThat(response.content()).isEqualTo("농도 재측정 필요");
		assertThat(response.revision()).isEqualTo(2);
		assertThat(response.lastEditedBy()).isEqualTo(MEMBER_ID);
	}

	@Test
	void concurrent_edit_with_a_stale_revision_is_rejected_without_overwriting() {
		CandidateMemo memo = CandidateMemo.create(CANDIDATE_ID, CandidateMemoType.INPUT_NOTE,
				"원본", VERSION_ID, MEMBER_ID);
		// 다른 사용자가 먼저 저장해 revision이 2로 올라간 상태를 흉내낸다.
		memo.update("먼저 저장된 내용", 1, VERSION_ID, 2L);
		when(memoRepository.findByCandidateIdAndMemoType(CANDIDATE_ID, CandidateMemoType.INPUT_NOTE))
				.thenReturn(Optional.of(memo));

		assertThatThrownBy(() -> service.upsert(CANDIDATE_ID, MEMBER_ID, CandidateMemoType.INPUT_NOTE,
				new UpsertCandidateMemoRequest("내가 쓴 내용", 1)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_MEMO_CONFLICT);

		assertThat(memo.getContent()).isEqualTo("먼저 저장된 내용");
		assertThat(memo.getRevision()).isEqualTo(2);
	}

	@Test
	void a_non_member_cannot_read_or_write_memos() {
		// projectId 20L은 accessGuard에 스텁하지 않았으므로 Mockito 기본값(false)으로 비멤버 처리된다.
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(
				withId(Candidate.create(1L, 20L, 999L, null), CANDIDATE_ID)));

		assertThatThrownBy(() -> service.list(CANDIDATE_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_ACCESS_DENIED);
	}
}
