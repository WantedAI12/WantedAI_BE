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
import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.prediction.service.PredictionService;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.common.CandidateStatus;
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

	/**
	 * 실험 후보로 확정된 배합의 현재 버전과 그 시점의 예측값을 고정해 계획을 만든다(BE-053/057).
	 * 확정 전 후보는 계획 대상이 아니다 — 아직 재검토·변경 가능성이 큰 배합을 검증 대상으로 굳히지
	 * 않는다.
	 */
	@Transactional
	public SensoryTestResponse plan(Long candidateId, Long memberId, SensoryTestPlanRequest dto) {
		Long projectId = candidateService.getProjectId(candidateId, memberId);
		accessGuard.requireWriteRole(projectId, memberId, WRITE_ROLE);

		CandidateResponse candidate = candidateService.get(candidateId, memberId);
		if (candidate.status() == CandidateStatus.UNDER_REVIEW) {
			throw new BusinessException(ErrorCode.SENSORY_TEST_CANDIDATE_NOT_CONFIRMED);
		}
		Long candidateVersionId = candidateService.getCurrentVersionId(candidateId, memberId);
		Double predictedSimilarityAtPlan = predictionService.get(candidateId, memberId).similarityScore();

		SensoryTest test = sensoryTestRepository.save(SensoryTest.plan(
				candidateId, candidateVersionId, predictedSimilarityAtPlan, dto.planDetail(),
				dto.protocolVersion(), dto.sampleCode(), dto.batchLot(), dto.panelSize(), dto.blindLevel(),
				memberId));
		log.info("[EVIDENCE] sensory-test id={} planned candidate={} version={} by={}",
				test.getId(), candidateId, candidateVersionId, memberId);
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

	/**
	 * BE-055: 측정값(resultData)이 없으면 결측 사유가 필수다 - 둘 다 없는 제출을 "결과 등록"으로
	 * 받아들이면 결측인지 등록 실수인지 구분할 수 없다. {@code supersedesResultId}로 이전 결과를
	 * 가리키면 그 결과를 고치는 대신 새 행을 만들고, 원본은 그대로 남는다(BE-055).
	 */
	@Transactional
	public SensoryTestResultResponse recordResult(Long testId, Long memberId, SensoryTestResultCreateRequest dto) {
		SensoryTest test = getAccessibleTest(testId, memberId);
		Long projectId = candidateService.getProjectId(test.getCandidateId(), memberId);
		accessGuard.requireWriteRole(projectId, memberId, WRITE_ROLE);

		if (dto.resultData() == null && (dto.missingReason() == null || dto.missingReason().isBlank())) {
			throw new BusinessException(ErrorCode.SENSORY_RESULT_DATA_OR_MISSING_REASON_REQUIRED);
		}
		if (dto.scaleMin() != null && dto.scaleMax() != null && dto.scaleMin() >= dto.scaleMax()) {
			throw new BusinessException(ErrorCode.SENSORY_RESULT_SCALE_RANGE_INVALID);
		}
		if (dto.supersedesResultId() != null) {
			SensoryTestResult original = sensoryTestResultRepository.findById(dto.supersedesResultId())
					.orElseThrow(() -> new BusinessException(ErrorCode.SENSORY_TEST_RESULT_NOT_FOUND));
			if (!original.getSensoryTestId().equals(testId)) {
				throw new BusinessException(ErrorCode.SENSORY_RESULT_SUPERSEDES_MISMATCH);
			}
		}

		SensoryTestResult result = sensoryTestResultRepository.save(SensoryTestResult.record(
				testId, dto.resultData() == null ? null : dto.resultData().toString(),
				dto.correlationWithPrediction(), dto.panelistIdentifier(), dto.timepointMinutes(),
				dto.scaleMin(), dto.scaleMax(), dto.missingReason(), dto.supersedesResultId(), memberId));
		test.markCompleted();
		log.info("[EVIDENCE] sensory-test id={} result recorded by={} supersedes={}",
				testId, memberId, dto.supersedesResultId());
		return toResultResponse(result);
	}

	/** 결과가 등록된(COMPLETED) 계획만 공개할 수 있다. */
	@Transactional
	public SensoryTestResponse publish(Long testId, Long memberId) {
		SensoryTest test = getAccessibleTest(testId, memberId);
		Long projectId = candidateService.getProjectId(test.getCandidateId(), memberId);
		accessGuard.requireWriteRole(projectId, memberId, PUBLISH_ROLES);
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
		// BE-057: 계획 시점에 고정한 값을 그대로 돌려준다 - 후보가 새 버전을 얻어도 이 관능
		// 계획이 검증한 배합의 당시 예측값은 바뀌지 않는다(조회 시점의 "현재" 예측이 아니다).
		return new SensoryTestDetailResponse(response, test.getPredictedSimilarityScoreAtPlan());
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
				test.getId(), test.getCandidateId(), test.getCandidateVersionId(), test.getPlanDetail(),
				test.getProtocolVersion(), test.getSampleCode(), test.getBatchLot(), test.getPanelSize(),
				test.getBlindLevel(), test.getStatus(), results, test.getCreatedAt(),
				test.isPublished(), test.getPublishedBy(), test.getPublishedAt());
	}

	private SensoryTestResultResponse toResultResponse(SensoryTestResult result) {
		return new SensoryTestResultResponse(
				result.getId(), result.getSensoryTestId(), parse(result.getResultData()),
				result.getCorrelationWithPrediction(), result.getPanelistIdentifier(), result.getTimepointMinutes(),
				result.getScaleMin(), result.getScaleMax(), result.getMissingReason(),
				result.getSupersedesResultId(), result.getRecordedBy(), result.getCreatedAt());
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
