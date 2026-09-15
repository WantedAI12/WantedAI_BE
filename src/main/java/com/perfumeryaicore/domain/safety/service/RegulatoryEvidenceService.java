package com.perfumeryaicore.domain.safety.service;

import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.safety.dto.request.AssessEvidenceApiRequest;
import com.perfumeryaicore.global.client.PerfumeryAiClient;
import com.perfumeryaicore.global.client.dto.AiCapabilitiesResponse;
import com.perfumeryaicore.global.client.dto.AssessEvidenceRequest;
import com.perfumeryaicore.global.client.dto.AssessEvidenceResponse;
import com.perfumeryaicore.global.client.dto.EvidenceCoverageResponse;
import com.perfumeryaicore.global.client.dto.EvidenceLine;
import com.perfumeryaicore.global.client.dto.EvidenceStatusResponse;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * v2 연동 답변(2026-09-15) 2단계: 규제·공급 근거 조회. {@code status}/{@code coverage}는 조향 AI의
 * registry 전체를 대상으로 한 전역 참조 데이터라 프로젝트 범위를 두지 않는다(로그인한 모든 회원에게
 * 열림, {@code IngredientMasterService}와 같은 이유). {@code assess}만 후보의 현재 배합을 대상으로
 * 하므로 기존 candidate 접근 제어를 그대로 탄다.
 *
 * <p>여기서 반환하는 "공개 자료 연결"은 현재 배합의 적합 판정이나 운영 승인과 다르다 - 문서 원문의
 * 경고를 그대로 따른다(README "규제 확인" 절).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegulatoryEvidenceService {

	private static final int DEFAULT_COVERAGE_LIMIT = 100;
	private static final int MAX_COVERAGE_LIMIT = 500;

	private final CandidateService candidateService;
	private final PerfumeryAiClient perfumeryAiClient;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public EvidenceStatusResponse status(Long memberId) {
		return perfumeryAiClient.evidenceStatus("evidence-status-" + memberId).parsed();
	}

	/** 지원 제품군·연산별 등록/가동 여부·모델 버전 - 로그인한 모든 회원에게 열린 런타임 참조 정보. */
	public AiCapabilitiesResponse capabilities() {
		return perfumeryAiClient.capabilities();
	}

	public EvidenceCoverageResponse coverage(Long memberId, Integer offset, Integer limit) {
		int safeOffset = offset != null && offset >= 0 ? offset : 0;
		int safeLimit = limit != null ? Math.min(Math.max(limit, 1), MAX_COVERAGE_LIMIT) : DEFAULT_COVERAGE_LIMIT;
		return perfumeryAiClient.evidenceCoverage(safeOffset, safeLimit, "evidence-coverage-" + memberId).parsed();
	}

	/** 후보의 현재 버전 배합(원료·농도)을 그대로 근거 평가에 넣는다 - 저장된 값을 임의로 보정하지 않는다. */
	public AssessEvidenceResponse assess(Long candidateId, Long memberId, AssessEvidenceApiRequest dto) {
		CandidateResponse candidate = candidateService.get(candidateId, memberId);
		if (candidate.currentVersion() == null || candidate.currentVersion().ingredients().isEmpty()) {
			throw new BusinessException(ErrorCode.CANDIDATE_VERSION_NOT_FOUND);
		}
		var lines = candidate.currentVersion().ingredients().stream()
				.map(i -> new EvidenceLine(i.ingredientId(), i.concentratePercent()))
				.toList();

		AssessEvidenceRequest request = new AssessEvidenceRequest(
				lines,
				dto.targetRegion().modalValue(),
				dto.productCategory().getModalValue(),
				dto.productConcentrationPercent(),
				dto.maxFormulaCostPerKg(),
				dto.maxIngredientPricePerKg(),
				buildPolicy(dto));

		var result = perfumeryAiClient.assessEvidence(request, "candidate-" + candidateId, null);
		log.info("[EVIDENCE] candidate={} status={} gatePassed={} by={}",
				candidateId, result.parsed().status(), result.parsed().gatePassed(), memberId);
		return result.parsed();
	}

	private JsonNode buildPolicy(AssessEvidenceApiRequest dto) {
		ObjectNode policy = jsonMapper.createObjectNode();
		if (dto.finishedBatchMassG() != null) {
			policy.put("finished_batch_mass_g", dto.finishedBatchMassG());
		}
		if (dto.maximumLeadTimeDays() != null) {
			policy.put("maximum_lead_time_days", dto.maximumLeadTimeDays());
		}
		if (dto.maximumPurchaseCostUsd() != null) {
			policy.put("maximum_purchase_cost_usd", dto.maximumPurchaseCostUsd());
		}
		return policy;
	}
}
