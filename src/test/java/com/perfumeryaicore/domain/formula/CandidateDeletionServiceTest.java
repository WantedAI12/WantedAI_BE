package com.perfumeryaicore.domain.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.evidence.entity.SensoryTest;
import com.perfumeryaicore.domain.evidence.repository.EvidenceReportRepository;
import com.perfumeryaicore.domain.evidence.repository.SensoryTestRepository;
import com.perfumeryaicore.domain.evidence.repository.SensoryTestResultRepository;
import com.perfumeryaicore.domain.experiment.repository.ExperimentStatusLogRepository;
import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.entity.CandidateVersion;
import com.perfumeryaicore.domain.formula.repository.CandidateMemoRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionRepository;
import com.perfumeryaicore.domain.formula.service.CandidateDeletionService;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.safety.repository.ApprovalGateRepository;
import com.perfumeryaicore.domain.supply.repository.SupplyChangeAffectedCandidateRepository;
import com.perfumeryaicore.domain.supply.repository.SupplyReviewDecisionRepository;
import com.perfumeryaicore.global.common.CandidateStatus;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 후보 한 건 삭제가 하위 데이터를 지우고, 승인된 후보는 지우지 못하게 막는지 검증한다. */
class CandidateDeletionServiceTest {

	private static final long PROJECT_ID = 10L;
	private static final long CANDIDATE_ID = 900L;
	private static final long MEMBER_ID = 1L;

	private final CandidateRepository candidateRepository = mock(CandidateRepository.class);
	private final CandidateVersionRepository candidateVersionRepository = mock(CandidateVersionRepository.class);
	private final CandidateVersionIngredientRepository candidateVersionIngredientRepository =
			mock(CandidateVersionIngredientRepository.class);
	private final CandidateMemoRepository candidateMemoRepository = mock(CandidateMemoRepository.class);
	private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);
	private final EvidenceReportRepository evidenceReportRepository = mock(EvidenceReportRepository.class);
	private final SensoryTestRepository sensoryTestRepository = mock(SensoryTestRepository.class);
	private final SensoryTestResultRepository sensoryTestResultRepository = mock(SensoryTestResultRepository.class);
	private final ExperimentStatusLogRepository experimentStatusLogRepository =
			mock(ExperimentStatusLogRepository.class);
	private final ApprovalGateRepository approvalGateRepository = mock(ApprovalGateRepository.class);
	private final SupplyChangeAffectedCandidateRepository supplyChangeAffectedCandidateRepository =
			mock(SupplyChangeAffectedCandidateRepository.class);
	private final SupplyReviewDecisionRepository supplyReviewDecisionRepository =
			mock(SupplyReviewDecisionRepository.class);

	private final CandidateDeletionService service = new CandidateDeletionService(
			candidateRepository, candidateVersionRepository, candidateVersionIngredientRepository,
			candidateMemoRepository, accessGuard, evidenceReportRepository, sensoryTestRepository,
			sensoryTestResultRepository, experimentStatusLogRepository, approvalGateRepository,
			supplyChangeAffectedCandidateRepository, supplyReviewDecisionRepository);

	private static void setId(Object entity, long id) {
		try {
			Field field = entity.getClass().getDeclaredField("id");
			field.setAccessible(true);
			field.set(entity, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private Candidate candidate() {
		Candidate candidate = Candidate.create(500L, PROJECT_ID, MEMBER_ID, null);
		setId(candidate, CANDIDATE_ID);
		return candidate;
	}

	@BeforeEach
	void stubEmptyChildrenByDefault() {
		when(candidateVersionRepository.findByCandidateIdIn(List.of(CANDIDATE_ID))).thenReturn(List.of());
		when(sensoryTestRepository.findByCandidateIdIn(List.of(CANDIDATE_ID))).thenReturn(List.of());
	}

	@Test
	void delete_fails_when_the_candidate_does_not_exist() {
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.delete(CANDIDATE_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_NOT_FOUND);
	}

	@Test
	void delete_is_forbidden_for_a_role_without_write_access() {
		Candidate candidate = candidate();
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
		when(accessGuard.requireWriteRole(PROJECT_ID, MEMBER_ID,
				ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PRODUCT_BRAND))
				.thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.delete(CANDIDATE_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
		verify(candidateRepository, never()).delete(any());
	}

	@Test
	void delete_rejects_an_already_approved_candidate() {
		Candidate candidate = candidate();
		candidate.transitionStatus(CandidateStatus.CONFIRMED_FOR_EXPERIMENT);
		candidate.transitionStatus(CandidateStatus.IN_SENSORY_TEST);
		candidate.transitionStatus(CandidateStatus.APPROVED);
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
		when(accessGuard.requireWriteRole(PROJECT_ID, MEMBER_ID,
				ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PRODUCT_BRAND))
				.thenReturn(ProjectRole.PERFUMER);

		assertThatThrownBy(() -> service.delete(CANDIDATE_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_DELETE_NOT_ALLOWED);
		verify(candidateRepository, never()).delete(any());
	}

	@Test
	void delete_removes_the_full_candidate_graph() {
		Candidate candidate = candidate();
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
		when(accessGuard.requireWriteRole(PROJECT_ID, MEMBER_ID,
				ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PRODUCT_BRAND))
				.thenReturn(ProjectRole.PERFUMER);

		CandidateVersion version = CandidateVersion.builder().candidateId(CANDIDATE_ID).createdBy(MEMBER_ID).build();
		setId(version, 1200L);
		when(candidateVersionRepository.findByCandidateIdIn(List.of(CANDIDATE_ID))).thenReturn(List.of(version));

		SensoryTest sensoryTest = mock(SensoryTest.class);
		when(sensoryTest.getId()).thenReturn(2000L);
		when(sensoryTestRepository.findByCandidateIdIn(List.of(CANDIDATE_ID))).thenReturn(List.of(sensoryTest));

		service.delete(CANDIDATE_ID, MEMBER_ID);

		verify(sensoryTestResultRepository).deleteAll(any());
		verify(sensoryTestRepository).deleteAll(List.of(sensoryTest));
		verify(evidenceReportRepository).findByCandidateIdIn(List.of(CANDIDATE_ID));
		verify(experimentStatusLogRepository).findByCandidateIdIn(List.of(CANDIDATE_ID));
		verify(approvalGateRepository).findByCandidateIdIn(List.of(CANDIDATE_ID));
		verify(supplyReviewDecisionRepository).findByCandidateIdIn(List.of(CANDIDATE_ID));
		verify(supplyChangeAffectedCandidateRepository).findByCandidateIdIn(List.of(CANDIDATE_ID));
		verify(candidateMemoRepository).findByCandidateIdIn(List.of(CANDIDATE_ID));
		verify(candidateVersionIngredientRepository).findByCandidateVersionIdIn(List.of(1200L));
		verify(candidateVersionRepository).deleteAll(List.of(version));
		verify(candidateRepository).delete(candidate);
	}

	@Test
	void delete_succeeds_for_a_candidate_with_no_versions_yet() {
		Candidate candidate = candidate();
		when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
		when(accessGuard.requireWriteRole(PROJECT_ID, MEMBER_ID,
				ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PRODUCT_BRAND))
				.thenReturn(ProjectRole.FRAGRANCE_RND);

		service.delete(CANDIDATE_ID, MEMBER_ID);

		verify(candidateRepository).delete(candidate);
	}
}
