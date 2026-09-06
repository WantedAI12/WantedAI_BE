package com.perfumeryaicore.domain.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.evidence.dto.request.SensoryTestPlanRequest;
import com.perfumeryaicore.domain.evidence.dto.request.SensoryTestResultCreateRequest;
import com.perfumeryaicore.domain.evidence.dto.response.SensoryTestDetailResponse;
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
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class SensoryTestServiceTest {

	private final SensoryTestRepository testRepository = mock(SensoryTestRepository.class);
	private final SensoryTestResultRepository resultRepository = mock(SensoryTestResultRepository.class);
	private final CandidateService candidateService = mock(CandidateService.class);
	private final PredictionService predictionService = mock(PredictionService.class);
	private final SensoryTestService service =
			new SensoryTestService(testRepository, resultRepository, candidateService, predictionService);

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private SensoryTest test(long id, Long candidateId) {
		SensoryTest test = SensoryTest.plan(candidateId, "5인 패널 삼각 검사", 1L);
		ReflectionTestUtils.setField(test, "id", id);
		return test;
	}

	@Test
	void plan_checks_access_and_creates_a_planned_test() {
		when(testRepository.save(any(SensoryTest.class))).thenAnswer(inv -> {
			SensoryTest t = inv.getArgument(0);
			ReflectionTestUtils.setField(t, "id", 10L);
			return t;
		});

		var response = service.plan(900L, 1L, new SensoryTestPlanRequest("5인 패널 삼각 검사"));

		verify(candidateService).assertAccessible(900L, 1L);
		assertThat(response.testId()).isEqualTo(10L);
		assertThat(response.status()).isEqualTo(SensoryTestStatus.PLANNED);
	}

	@Test
	void recording_a_result_stores_json_and_marks_test_completed() {
		SensoryTest test = test(10L, 900L);
		when(testRepository.findById(10L)).thenReturn(Optional.of(test));
		when(resultRepository.save(any(SensoryTestResult.class))).thenAnswer(inv -> {
			SensoryTestResult r = inv.getArgument(0);
			ReflectionTestUtils.setField(r, "id", 50L);
			return r;
		});

		ObjectNode resultData = jsonMapper.createObjectNode();
		resultData.put("panelSize", 5).put("correctPicks", 4);

		SensoryTestResultResponse response = service.recordResult(
				10L, 2L, new SensoryTestResultCreateRequest(resultData, 0.62));

		verify(candidateService).assertAccessible(900L, 2L);
		assertThat(test.getStatus()).isEqualTo(SensoryTestStatus.COMPLETED);
		assertThat(response.resultId()).isEqualTo(50L);
		assertThat(response.correlationWithPrediction()).isEqualTo(0.62);
		assertThat(response.resultData().get("panelSize").asInt()).isEqualTo(5);
	}

	@Test
	void detail_adds_current_prediction_similarity_as_reference() {
		SensoryTest test = test(10L, 900L);
		when(testRepository.findById(10L)).thenReturn(Optional.of(test));
		when(resultRepository.findBySensoryTestIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());
		when(predictionService.get(900L, 1L)).thenReturn(new PredictionResponse(
				900L, 1200L, "prototype_ready", 87.4, "kind", 0.7, 64.0, true, "kind", "s", "s",
				new HumanValidation(false, null, null, null, null, null), null, null, null));

		SensoryTestDetailResponse detail = service.getDetail(10L, 1L);

		assertThat(detail.predictedSimilarityScore()).isEqualTo(87.4);
		assertThat(detail.test().testId()).isEqualTo(10L);
	}

	@Test
	void unknown_test_is_not_found() {
		when(testRepository.findById(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getDetail(404L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SENSORY_TEST_NOT_FOUND);
	}
}
