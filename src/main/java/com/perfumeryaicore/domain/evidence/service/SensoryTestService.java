package com.perfumeryaicore.domain.evidence.service;

import com.perfumeryaicore.domain.evidence.dto.request.SensoryTestPlanRequest;
import com.perfumeryaicore.domain.evidence.dto.request.SensoryTestResultCreateRequest;
import com.perfumeryaicore.domain.evidence.dto.response.SensoryTestDetailResponse;
import com.perfumeryaicore.domain.evidence.dto.response.SensoryTestResponse;
import com.perfumeryaicore.domain.evidence.dto.response.SensoryTestResultResponse;
import com.perfumeryaicore.domain.evidence.entity.SensoryTest;
import com.perfumeryaicore.domain.evidence.entity.SensoryTestResult;
import com.perfumeryaicore.domain.evidence.repository.SensoryTestRepository;
import com.perfumeryaicore.domain.evidence.repository.SensoryTestResultRepository;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.prediction.service.PredictionService;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 독립 블라인드 관능 검증 계획·결과. 접근 제어는 {@code sensoryTest.candidateId}를 통해
 * formula 도메인의 후보 접근 제어를 그대로 재사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SensoryTestService {

	private final SensoryTestRepository sensoryTestRepository;
	private final SensoryTestResultRepository sensoryTestResultRepository;
	private final CandidateService candidateService;
	private final PredictionService predictionService;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Transactional
	public SensoryTestResponse plan(Long candidateId, Long memberId, SensoryTestPlanRequest dto) {
		candidateService.assertAccessible(candidateId, memberId);
		SensoryTest test = sensoryTestRepository.save(
				SensoryTest.plan(candidateId, dto.planDetail(), memberId));
		log.info("[EVIDENCE] sensory-test id={} planned candidate={} by={}", test.getId(), candidateId, memberId);
		return toResponse(test, List.of());
	}

	public List<SensoryTestResponse> list(Long candidateId, Long memberId) {
		candidateService.assertAccessible(candidateId, memberId);
		List<SensoryTest> tests = sensoryTestRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
		return tests.stream()
				.map(test -> toResponse(test, resultsOf(test.getId())))
				.toList();
	}

	@Transactional
	public SensoryTestResultResponse recordResult(Long testId, Long memberId, SensoryTestResultCreateRequest dto) {
		SensoryTest test = getAccessibleTest(testId, memberId);
		SensoryTestResult result = sensoryTestResultRepository.save(SensoryTestResult.record(
				testId, dto.resultData().toString(), dto.correlationWithPrediction(), memberId));
		test.markCompleted();
		log.info("[EVIDENCE] sensory-test id={} result recorded by={}", testId, memberId);
		return toResultResponse(result);
	}

	public SensoryTestDetailResponse getDetail(Long testId, Long memberId) {
		SensoryTest test = getAccessibleTest(testId, memberId);
		SensoryTestResponse response = toResponse(test, resultsOf(testId));
		Double predictedSimilarity = predictionService.get(test.getCandidateId(), memberId).similarityScore();
		return new SensoryTestDetailResponse(response, predictedSimilarity);
	}

	private SensoryTest getAccessibleTest(Long testId, Long memberId) {
		SensoryTest test = sensoryTestRepository.findById(testId)
				.orElseThrow(() -> new BusinessException(ErrorCode.SENSORY_TEST_NOT_FOUND));
		candidateService.assertAccessible(test.getCandidateId(), memberId);
		return test;
	}

	private List<SensoryTestResultResponse> resultsOf(Long testId) {
		return sensoryTestResultRepository.findBySensoryTestIdOrderByCreatedAtDesc(testId).stream()
				.map(this::toResultResponse)
				.toList();
	}

	private SensoryTestResponse toResponse(SensoryTest test, List<SensoryTestResultResponse> results) {
		return new SensoryTestResponse(
				test.getId(), test.getCandidateId(), test.getPlanDetail(), test.getStatus(),
				results, test.getCreatedAt());
	}

	private SensoryTestResultResponse toResultResponse(SensoryTestResult result) {
		return new SensoryTestResultResponse(
				result.getId(), result.getSensoryTestId(), parse(result.getResultData()),
				result.getCorrelationWithPrediction(), result.getRecordedBy(), result.getCreatedAt());
	}

	private JsonNode parse(String json) {
		if (json == null) {
			return null;
		}
		try {
			return jsonMapper.readTree(json);
		} catch (JacksonException e) {
			log.warn("[EVIDENCE] failed to re-parse stored sensory result data: {}", e.getMessage());
			return null;
		}
	}
}
