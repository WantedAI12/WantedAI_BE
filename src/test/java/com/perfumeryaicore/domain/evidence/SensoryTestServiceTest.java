package com.perfumeryaicore.domain.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.evidence.dto.request.SensoryTestPlanRequest;
import com.perfumeryaicore.domain.evidence.dto.request.SensoryTestResultCreateRequest;
import com.perfumeryaicore.domain.evidence.dto.response.SensoryTestDetailResponse;
import com.perfumeryaicore.domain.evidence.dto.response.SensoryTestResponse;
import com.perfumeryaicore.domain.evidence.dto.response.SensoryTestResultResponse;
import com.perfumeryaicore.domain.evidence.entity.SensoryTest;
import com.perfumeryaicore.domain.evidence.entity.SensoryTestResult;
import com.perfumeryaicore.domain.evidence.entity.SensoryTestStatus;
import com.perfumeryaicore.domain.evidence.repository.SensoryTestRepository;
import com.perfumeryaicore.domain.evidence.repository.SensoryTestResultRepository;
import com.perfumeryaicore.domain.evidence.service.SensoryTestService;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse.HumanValidation;
import com.perfumeryaicore.domain.prediction.service.PredictionService;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class SensoryTestServiceTest {

	private static final long PROJECT_ID = 10L;
	private static final long CANDIDATE_ID = 900L;

	private final SensoryTestRepository testRepository = mock(SensoryTestRepository.class);
	private final SensoryTestResultRepository resultRepository = mock(SensoryTestResultRepository.class);
	private final CandidateService candidateService = mock(CandidateService.class);
	private final PredictionService predictionService = mock(PredictionService.class);
	private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);
	private final SensoryTestService service =
			new SensoryTestService(testRepository, resultRepository, candidateService, predictionService, accessGuard);

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@BeforeEach
	void actorIsSensoryScientist() {
		when(candidateService.getProjectId(CANDIDATE_ID, 1L)).thenReturn(PROJECT_ID);
		when(accessGuard.requireRole(PROJECT_ID, 1L, ProjectRole.SENSORY_SCIENTIST)).thenReturn(ProjectRole.SENSORY_SCIENTIST);
		when(accessGuard.hasRole(PROJECT_ID, 1L, ProjectRole.SENSORY_SCIENTIST)).thenReturn(true);
	}

	private SensoryTest test(long id, Long candidateId) {
		SensoryTest test = SensoryTest.plan(candidateId, "5인 패널 삼각 검사", 1L);
		ReflectionTestUtils.setField(test, "id", id);
		return test;
	}

	@Test
	void plan_checks_role_and_creates_a_planned_test() {
		when(testRepository.save(any(SensoryTest.class))).thenAnswer(inv -> {
			SensoryTest t = inv.getArgument(0);
			ReflectionTestUtils.setField(t, "id", 10L);
			return t;
		});

		var response = service.plan(CANDIDATE_ID, 1L, new SensoryTestPlanRequest("5인 패널 삼각 검사"));

		assertThat(response.testId()).isEqualTo(10L);
		assertThat(response.status()).isEqualTo(SensoryTestStatus.PLANNED);
		assertThat(response.published()).isFalse();
	}

	/** BE-006: SENSORY_SCIENTIST가 아니면 계획을 등록할 수 없다. */
	@Test
	void plan_is_forbidden_for_a_non_sensory_scientist() {
		when(accessGuard.requireRole(PROJECT_ID, 1L, ProjectRole.SENSORY_SCIENTIST))
				.thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.plan(CANDIDATE_ID, 1L, new SensoryTestPlanRequest("계획")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
		verify(testRepository, never()).save(any());
	}

	@Test
	void recording_a_result_stores_json_and_marks_test_completed() {
		SensoryTest test = test(10L, CANDIDATE_ID);
		when(testRepository.findById(10L)).thenReturn(Optional.of(test));
		when(resultRepository.save(any(SensoryTestResult.class))).thenAnswer(inv -> {
			SensoryTestResult r = inv.getArgument(0);
			ReflectionTestUtils.setField(r, "id", 50L);
			return r;
		});

		ObjectNode resultData = jsonMapper.createObjectNode();
		resultData.put("panelSize", 5).put("correctPicks", 4);

		SensoryTestResultResponse response = service.recordResult(
				10L, 1L, new SensoryTestResultCreateRequest(resultData, 0.62));

		assertThat(test.getStatus()).isEqualTo(SensoryTestStatus.COMPLETED);
		assertThat(response.resultId()).isEqualTo(50L);
		assertThat(response.correlationWithPrediction()).isEqualTo(0.62);
		assertThat(response.resultData().get("panelSize").asInt()).isEqualTo(5);
	}

	/** BE-006: SENSORY_SCIENTIST가 아니면 결과를 등록할 수 없다. */
	@Test
	void recording_a_result_is_forbidden_for_a_non_sensory_scientist() {
		SensoryTest test = test(10L, CANDIDATE_ID);
		when(testRepository.findById(10L)).thenReturn(Optional.of(test));
		when(accessGuard.requireRole(PROJECT_ID, 1L, ProjectRole.SENSORY_SCIENTIST))
				.thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.recordResult(10L, 1L,
				new SensoryTestResultCreateRequest(jsonMapper.createObjectNode(), 0.5)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
		verify(resultRepository, never()).save(any());
		assertThat(test.getStatus()).isEqualTo(SensoryTestStatus.PLANNED);
	}

	@Test
	void detail_adds_current_prediction_similarity_as_reference() {
		SensoryTest test = test(10L, CANDIDATE_ID);
		test.markCompleted();
		test.publish(1L);
		when(testRepository.findById(10L)).thenReturn(Optional.of(test));
		when(resultRepository.findBySensoryTestIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());
		when(predictionService.get(CANDIDATE_ID, 1L)).thenReturn(new PredictionResponse(
				900L, 1200L, "prototype_ready", 87.4, "kind", 0.7, 64.0, true, "kind", "s", "s",
				new HumanValidation(false, null, null, null, null, null), null, null, null));

		SensoryTestDetailResponse detail = service.getDetail(10L, 1L);

		assertThat(detail.predictedSimilarityScore()).isEqualTo(87.4);
		assertThat(detail.test().testId()).isEqualTo(10L);
	}

	/** BE-006: 미공개 결과는 SENSORY_SCIENTIST가 아니면 상세 조회를 거부한다. */
	@Test
	void detail_of_an_unpublished_test_is_denied_for_a_non_sensory_scientist() {
		SensoryTest test = test(10L, CANDIDATE_ID);
		when(testRepository.findById(10L)).thenReturn(Optional.of(test));
		when(accessGuard.hasRole(PROJECT_ID, 1L, ProjectRole.SENSORY_SCIENTIST)).thenReturn(false);

		assertThatThrownBy(() -> service.getDetail(10L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SENSORY_TEST_ACCESS_DENIED);
	}

	/** BE-006: 목록은 미공개 항목을 예외 없이 조용히 제외한다. */
	@Test
	void list_hides_unpublished_tests_from_a_non_sensory_scientist() {
		SensoryTest unpublished = test(10L, CANDIDATE_ID);
		SensoryTest published = test(11L, CANDIDATE_ID);
		published.markCompleted();
		published.publish(1L);
		when(testRepository.findByCandidateIdOrderByCreatedAtDesc(CANDIDATE_ID))
				.thenReturn(List.of(unpublished, published));
		when(resultRepository.findBySensoryTestIdOrderByCreatedAtDesc(11L)).thenReturn(List.of());
		when(accessGuard.hasRole(PROJECT_ID, 1L, ProjectRole.SENSORY_SCIENTIST)).thenReturn(false);

		List<SensoryTestResponse> result = service.list(CANDIDATE_ID, 1L);

		assertThat(result).extracting(SensoryTestResponse::testId).containsExactly(11L);
	}

	@Test
	void list_shows_everything_to_a_sensory_scientist() {
		SensoryTest unpublished = test(10L, CANDIDATE_ID);
		when(testRepository.findByCandidateIdOrderByCreatedAtDesc(CANDIDATE_ID)).thenReturn(List.of(unpublished));
		when(resultRepository.findBySensoryTestIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());

		List<SensoryTestResponse> result = service.list(CANDIDATE_ID, 1L);

		assertThat(result).extracting(SensoryTestResponse::testId).containsExactly(10L);
	}

	@Test
	void publish_requires_a_completed_test() {
		SensoryTest test = test(10L, CANDIDATE_ID);
		when(testRepository.findById(10L)).thenReturn(Optional.of(test));
		when(accessGuard.requireRole(PROJECT_ID, 1L, ProjectRole.SENSORY_SCIENTIST, ProjectRole.PROJECT_MANAGER))
				.thenReturn(ProjectRole.SENSORY_SCIENTIST);

		assertThatThrownBy(() -> service.publish(10L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SENSORY_TEST_NOT_COMPLETED);
	}

	@Test
	void publish_succeeds_for_a_completed_test() {
		SensoryTest test = test(10L, CANDIDATE_ID);
		test.markCompleted();
		when(testRepository.findById(10L)).thenReturn(Optional.of(test));
		when(resultRepository.findBySensoryTestIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());
		when(accessGuard.requireRole(PROJECT_ID, 1L, ProjectRole.SENSORY_SCIENTIST, ProjectRole.PROJECT_MANAGER))
				.thenReturn(ProjectRole.SENSORY_SCIENTIST);

		SensoryTestResponse response = service.publish(10L, 1L);

		assertThat(response.published()).isTrue();
		assertThat(response.publishedBy()).isEqualTo(1L);
	}

	@Test
	void unknown_test_is_not_found() {
		when(testRepository.findById(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getDetail(404L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SENSORY_TEST_NOT_FOUND);
	}
}
