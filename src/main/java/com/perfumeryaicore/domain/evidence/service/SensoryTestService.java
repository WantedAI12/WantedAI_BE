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
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.common.ProjectRole;
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
 *
 * <p>계획·결과 등록은 SENSORY_SCIENTIST만 할 수 있다(BE-006). 결과는 등록만으로 공개되지 않고,
 * {@link #publish}로 명시적으로 공개하기 전까지는 SENSORY_SCIENTIST 외 역할이 조회할 수 없다 —
 * {@link #list}는 미공개 항목을 조용히 빼고, {@link #getDetail}은 403으로 거부한다. 이 목록을
 * 그대로 재사용하는 증거 보고서(PDF)에도 같은 필터가 자동으로 적용된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SensoryTestService {

	private static final ProjectRole WRITE_ROLE = ProjectRole.SENSORY_SCIENTIST;
	/** 공개 권한 역할표는 팀 확인 전까지 SENSORY_SCIENTIST/PROJECT_MANAGER로 임시 지정한다. */
	private static final ProjectRole[] PUBLISH_ROLES = {ProjectRole.SENSORY_SCIENTIST, ProjectRole.PROJECT_MANAGER};

	private final SensoryTestRepository sensoryTestRepository;
	private final SensoryTestResultRepository sensoryTestResultRepository;
	private final CandidateService candidateService;
	private final PredictionService predictionService;
	private final ProjectAccessGuard accessGuard;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Transactional
	public SensoryTestResponse plan(Long candidateId, Long memberId, SensoryTestPlanRequest dto) {
		Long projectId = candidateService.getProjectId(candidateId, memberId);
		accessGuard.requireRole(projectId, memberId, WRITE_ROLE);
		SensoryTest test = sensoryTestRepository.save(
				SensoryTest.plan(candidateId, dto.planDetail(), memberId));
		log.info("[EVIDENCE] sensory-test id={} planned candidate={} by={}", test.getId(), candidateId, memberId);
		return toResponse(test, List.of());
	}

	/** 미공개(published=false) 계획은 SENSORY_SCIENTIST가 아니면 목록에서 조용히 제외한다. */
	public List<SensoryTestResponse> list(Long candidateId, Long memberId) {
		Long projectId = candidateService.getProjectId(candidateId, memberId);
		boolean canViewUnpublished = accessGuard.hasRole(projectId, memberId, WRITE_ROLE);
		List<SensoryTest> tests = sensoryTestRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
		return tests.stream()
				.filter(test -> test.isPublished() || canViewUnpublished)
				.map(test -> toResponse(test, resultsOf(test.getId())))
				.toList();
	}

	@Transactional
	public SensoryTestResultResponse recordResult(Long testId, Long memberId, SensoryTestResultCreateRequest dto) {
		SensoryTest test = getAccessibleTest(testId, memberId);
		Long projectId = candidateService.getProjectId(test.getCandidateId(), memberId);
		accessGuard.requireRole(projectId, memberId, WRITE_ROLE);
		SensoryTestResult result = sensoryTestResultRepository.save(SensoryTestResult.record(
				testId, dto.resultData().toString(), dto.correlationWithPrediction(), memberId));
		test.markCompleted();
		log.info("[EVIDENCE] sensory-test id={} result recorded by={}", testId, memberId);
		return toResultResponse(result);
	}

	/** 결과가 등록된(COMPLETED) 계획만 공개할 수 있다. */
	@Transactional
	public SensoryTestResponse publish(Long testId, Long memberId) {
		SensoryTest test = getAccessibleTest(testId, memberId);
		Long projectId = candidateService.getProjectId(test.getCandidateId(), memberId);
		accessGuard.requireRole(projectId, memberId, PUBLISH_ROLES);
		test.publish(memberId);
		log.info("[EVIDENCE] sensory-test id={} published by={}", testId, memberId);
		return toResponse(test, resultsOf(testId));
	}

	/** 미공개 결과는 SENSORY_SCIENTIST가 아니면 조회를 거부한다. */
	public SensoryTestDetailResponse getDetail(Long testId, Long memberId) {
		SensoryTest test = getAccessibleTest(testId, memberId);
		if (!test.isPublished()) {
			Long projectId = candidateService.getProjectId(test.getCandidateId(), memberId);
			if (!accessGuard.hasRole(projectId, memberId, WRITE_ROLE)) {
				throw new BusinessException(ErrorCode.SENSORY_TEST_ACCESS_DENIED);
			}
		}
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
				results, test.getCreatedAt(), test.isPublished(), test.getPublishedBy(), test.getPublishedAt());
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
