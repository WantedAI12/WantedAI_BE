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
import com.perfumeryaicore.domain.evidence.entity.BlindLevel;
import com.perfumeryaicore.domain.evidence.entity.SensoryTest;
import com.perfumeryaicore.domain.evidence.entity.SensoryTestResult;
import com.perfumeryaicore.domain.evidence.entity.SensoryTestStatus;
import com.perfumeryaicore.domain.evidence.repository.SensoryTestRepository;
import com.perfumeryaicore.domain.evidence.repository.SensoryTestResultRepository;
import com.perfumeryaicore.domain.evidence.service.SensoryTestService;
import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse.HumanValidation;
import com.perfumeryaicore.domain.prediction.service.PredictionService;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.common.CandidateStatus;
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
		when(accessGuard.requireWriteRole(PROJECT_ID, 1L, ProjectRole.SENSORY_SCIENTIST)).thenReturn(ProjectRole.SENSORY_SCIENTIST);
		when(accessGuard.hasRole(PROJECT_ID, 1L, ProjectRole.SENSORY_SCIENTIST)).thenReturn(true);
		// BE-053: plan()이 실험 확정 상태와 현재 버전을 확인한다.
		when(candidateService.get(CANDIDATE_ID, 1L)).thenReturn(
				new CandidateResponse(CANDIDATE_ID, 5L, 1, CandidateStatus.CONFIRMED_FOR_EXPERIMENT, null, null, null, null));
		when(candidateService.getCurrentVersionId(CANDIDATE_ID, 1L)).thenReturn(1200L);
		when(predictionService.get(CANDIDATE_ID, 1L)).thenReturn(prediction(87.4));
	}

	private static PredictionResponse prediction(Double similarity) {
		return new PredictionResponse(
				CANDIDATE_ID, 1200L, "prototype_ready", similarity, "kind", "0.7", 64.0, true, "kind", "s", "s",
				new HumanValidation(false, null, null, null, null, null), null, null, null);
	}

	private SensoryTest test(long id, Long candidateId) {
		return test(id, candidateId, 87.4);
	}

	private SensoryTest test(long id, Long candidateId, Double predictedSimilarityAtPlan) {
		SensoryTest test = SensoryTest.plan(candidateId, 1200L, predictedSimilarityAtPlan, "5인 패널 삼각 검사",
				"protocol-v1", "S-14", "lot-2026-09", 5, BlindLevel.DOUBLE_BLIND, 1L);
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

		var response = service.plan(CANDIDATE_ID, 1L, new SensoryTestPlanRequest(
				"5인 패널 삼각 검사", "protocol-v1", "S-14", "lot-2026-09", 5, BlindLevel.DOUBLE_BLIND));

		assertThat(response.testId()).isEqualTo(10L);
		assertThat(response.status()).isEqualTo(SensoryTestStatus.PLANNED);
		assertThat(response.published()).isFalse();
		assertThat(response.candidateVersionId()).isEqualTo(1200L);
	}

	/** BE-053: 실험 후보로 확정되지 않은(UNDER_REVIEW) 후보는 관능 계획을 세울 수 없다. */
	@Test
	void plan_is_rejected_when_the_candidate_is_not_confirmed_for_experiment() {
		when(candidateService.get(CANDIDATE_ID, 1L)).thenReturn(
				new CandidateResponse(CANDIDATE_ID, 5L, 1, CandidateStatus.UNDER_REVIEW, null, null, null, null));

		assertThatThrownBy(() -> service.plan(CANDIDATE_ID, 1L, new SensoryTestPlanRequest(
				"계획", null, null, null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SENSORY_TEST_CANDIDATE_NOT_CONFIRMED);
		verify(testRepository, never()).save(any());
	}

	/** BE-006: SENSORY_SCIENTIST가 아니면 계획을 등록할 수 없다. */
	@Test
	void plan_is_forbidden_for_a_non_sensory_scientist() {
		when(accessGuard.requireWriteRole(PROJECT_ID, 1L, ProjectRole.SENSORY_SCIENTIST))
				.thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.plan(CANDIDATE_ID, 1L, new SensoryTestPlanRequest(
				"계획", null, null, null, null, null)))
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
				10L, 1L, new SensoryTestResultCreateRequest(
						resultData, 0.62, "P07", 240, 0.0, 100.0, null, null));

		assertThat(test.getStatus()).isEqualTo(SensoryTestStatus.COMPLETED);
		assertThat(response.resultId()).isEqualTo(50L);
		assertThat(response.correlationWithPrediction()).isEqualTo(0.62);
		assertThat(response.resultData().get("panelSize").asInt()).isEqualTo(5);
	}

	/** BE-055: 측정값도 결측 사유도 없는 제출은 거부한다 - 결측인지 실수인지 구분할 수 없다. */
	@Test
	void recording_a_result_without_data_or_a_missing_reason_is_rejected() {
		SensoryTest test = test(10L, CANDIDATE_ID);
		when(testRepository.findById(10L)).thenReturn(Optional.of(test));

		assertThatThrownBy(() -> service.recordResult(10L, 1L,
				new SensoryTestResultCreateRequest(null, null, "P07", null, null, null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SENSORY_RESULT_DATA_OR_MISSING_REASON_REQUIRED);
		verify(resultRepository, never()).save(any());
	}

	/** BE-055: 결측 사유만 있으면(측정값 없이도) 등록을 허용한다. */
	@Test
	void recording_a_result_with_only_a_missing_reason_is_accepted() {
		SensoryTest test = test(10L, CANDIDATE_ID);
		when(testRepository.findById(10L)).thenReturn(Optional.of(test));
		when(resultRepository.save(any(SensoryTestResult.class))).thenAnswer(inv -> {
			SensoryTestResult r = inv.getArgument(0);
			ReflectionTestUtils.setField(r, "id", 51L);
			return r;
		});

		var response = service.recordResult(10L, 1L, new SensoryTestResultCreateRequest(
				null, null, "P07", null, null, null, "평가자 불참", null));

		assertThat(response.missingReason()).isEqualTo("평가자 불참");
		assertThat(response.resultData()).isNull();
	}

	@Test
	void recording_a_result_with_an_inverted_scale_range_is_rejected() {
		SensoryTest test = test(10L, CANDIDATE_ID);
		when(testRepository.findById(10L)).thenReturn(Optional.of(test));

		assertThatThrownBy(() -> service.recordResult(10L, 1L, new SensoryTestResultCreateRequest(
				jsonMapper.createObjectNode(), 0.5, "P07", null, 100.0, 0.0, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SENSORY_RESULT_SCALE_RANGE_INVALID);
		verify(resultRepository, never()).save(any());
	}

	/** BE-055: 수정은 원본을 고치지 않고 새 행을 만든다 - supersedesResultId로 원본을 가리킨다. */
	@Test
	void recording_a_correction_creates_a_new_row_referencing_the_original() {
		SensoryTest test = test(10L, CANDIDATE_ID);
		when(testRepository.findById(10L)).thenReturn(Optional.of(test));
		SensoryTestResult original = SensoryTestResult.record(
				10L, "{\"score\":3}", 0.5, "P07", null, null, null, null, null, 1L);
		ReflectionTestUtils.setField(original, "id", 50L);
		when(resultRepository.findById(50L)).thenReturn(Optional.of(original));
		when(resultRepository.save(any(SensoryTestResult.class))).thenAnswer(inv -> {
			SensoryTestResult r = inv.getArgument(0);
			ReflectionTestUtils.setField(r, "id", 51L);
			return r;
		});

		ObjectNode corrected = jsonMapper.createObjectNode();
		corrected.put("score", 4);
		var response = service.recordResult(10L, 1L, new SensoryTestResultCreateRequest(
				corrected, 0.6, "P07", null, null, null, null, 50L));

		assertThat(response.resultId()).isEqualTo(51L);
		assertThat(response.supersedesResultId()).isEqualTo(50L);
	}

	/** BE-055: 다른 관능 검증에 속한 결과를 수정 대상으로 지정할 수 없다. */
	@Test
	void recording_a_correction_that_references_a_result_from_another_test_is_rejected() {
		SensoryTest test = test(10L, CANDIDATE_ID);
		when(testRepository.findById(10L)).thenReturn(Optional.of(test));
		SensoryTestResult originalFromAnotherTest = SensoryTestResult.record(
				999L, "{\"score\":3}", 0.5, "P07", null, null, null, null, null, 1L);
		ReflectionTestUtils.setField(originalFromAnotherTest, "id", 50L);
		when(resultRepository.findById(50L)).thenReturn(Optional.of(originalFromAnotherTest));

		assertThatThrownBy(() -> service.recordResult(10L, 1L, new SensoryTestResultCreateRequest(
				jsonMapper.createObjectNode(), 0.6, "P07", null, null, null, null, 50L)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SENSORY_RESULT_SUPERSEDES_MISMATCH);
		verify(resultRepository, never()).save(any());
	}

	/** BE-006: SENSORY_SCIENTIST가 아니면 결과를 등록할 수 없다. */
	@Test
	void recording_a_result_is_forbidden_for_a_non_sensory_scientist() {
		SensoryTest test = test(10L, CANDIDATE_ID);
		when(testRepository.findById(10L)).thenReturn(Optional.of(test));
		when(accessGuard.requireWriteRole(PROJECT_ID, 1L, ProjectRole.SENSORY_SCIENTIST))
				.thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.recordResult(10L, 1L,
				new SensoryTestResultCreateRequest(
						jsonMapper.createObjectNode(), 0.5, "P07", null, null, null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
		verify(resultRepository, never()).save(any());
		assertThat(test.getStatus()).isEqualTo(SensoryTestStatus.PLANNED);
	}

	/** BE-057: 상세 조회는 조회 시점의 "현재" 예측이 아니라 계획 시점에 고정한 값을 돌려준다. */
	@Test
	void detail_returns_the_prediction_fixed_at_plan_time_not_the_current_one() {
		SensoryTest test = test(10L, CANDIDATE_ID, 87.4);
		test.markCompleted();
		test.publish(1L);
		when(testRepository.findById(10L)).thenReturn(Optional.of(test));
		when(resultRepository.findBySensoryTestIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());
		// 이후 후보의 "현재" 예측이 바뀌었더라도(예: 새 버전) 이 관능 결과의 참고값은 그대로여야 한다.
		when(predictionService.get(CANDIDATE_ID, 1L)).thenReturn(prediction(40.0));

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
		when(accessGuard.requireWriteRole(PROJECT_ID, 1L, ProjectRole.SENSORY_SCIENTIST, ProjectRole.PROJECT_MANAGER))
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
		when(accessGuard.requireWriteRole(PROJECT_ID, 1L, ProjectRole.SENSORY_SCIENTIST, ProjectRole.PROJECT_MANAGER))
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
