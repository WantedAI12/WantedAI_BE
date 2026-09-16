package com.perfumeryaicore.domain.formula.service;

import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse;
import com.perfumeryaicore.domain.request.dto.request.BriefReviewRequest;
import com.perfumeryaicore.domain.request.dto.request.ReassessDiagnosticRequest;
import com.perfumeryaicore.domain.request.dto.request.ReviseCandidateApiRequest;
import com.perfumeryaicore.domain.request.service.BriefReviewService;
import com.perfumeryaicore.global.client.PerfumeryAiResult;
import com.perfumeryaicore.global.client.dto.EvaluationResponse;
import com.perfumeryaicore.global.client.dto.EvidenceLine;
import com.perfumeryaicore.global.client.dto.PrepareBriefResponse;
import com.perfumeryaicore.global.client.dto.ReviseCandidateResponse;
import com.perfumeryaicore.global.client.dto.StoredCandidate;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * BE-102: 저장된 후보의 현재 배합을 자연어 지시로 수정 검토한다. v1으로 생성된 후보는 v2 스냅샷을
 * 갖고 있지 않으므로(스키마가 다르다 - {@code /v1/formulas}와 {@code /v2/formulas/evaluate}는
 * 별개 계약이다), 매번 세 단계를 서버에서 이어서 호출해 브리지한다.
 *
 * <ol>
 *   <li>{@link BriefReviewService#prepare}를 현재 배합({@code lines})으로 진단 모드 호출해
 *       그 배합에 맞는 {@code review_id}를 받는다.</li>
 *   <li>{@link BriefReviewService#reassessDiagnostic}으로 같은 배합을 재평가해 v2 스냅샷
 *       ({@code diagnostic_candidates[0]})을 얻는다 - 고정 배합이라 새로 탐색하지 않아 빠르다.</li>
 *   <li>{@link BriefReviewService#reviseCandidate}에 그 스냅샷과 지시문을 넣어 수정된 검토
 *       결과를 받는다.</li>
 * </ol>
 *
 * <p>결과는 새 후보를 저장·승인하지 않는다 - {@code next_operation}이 안내하는 재확인 절차를
 * 호출부(FE)가 따라야 한다. 근거 등록 여부와 무관하게 동작한다(compare/revise는 근거를 직접
 * 요구하지 않음, AI 개발팀 확인).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandidateDiagnosticReviseService {

	private final CandidateService candidateService;
	private final BriefReviewService briefReviewService;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public ReviseCandidateResponse revise(Long candidateId, Long memberId, String instruction) {
		CandidateResponse candidate = candidateService.get(candidateId, memberId);
		CandidateVersionResponse version = candidate.currentVersion();
		if (version == null || version.ingredients() == null || version.ingredients().isEmpty()) {
			throw new BusinessException(ErrorCode.CANDIDATE_VERSION_NOT_FOUND);
		}
		List<EvidenceLine> lines = version.ingredients().stream()
				.map(i -> new EvidenceLine(i.ingredientId(), i.concentratePercent()))
				.toList();

		PrepareBriefResponse prepared = briefReviewService.prepare(candidate.requestId(), memberId,
				new BriefReviewRequest(null, null, null, lines, true));
		if (!prepared.isReady() || prepared.reviewId() == null) {
			log.warn("[CANDIDATE-REVISE] candidate={} prepare not ready status={} missing={}",
					candidateId, prepared.status(), prepared.missingFields());
			throw new BusinessException(ErrorCode.CANDIDATE_REVISE_PREPARE_NOT_READY);
		}

		PerfumeryAiResult<EvaluationResponse> reassessResult = briefReviewService.reassessDiagnostic(
				candidate.requestId(), memberId,
				new ReassessDiagnosticRequest(prepared.reviewId(), lines, null, null, null));
		EvaluationResponse evaluation = reassessResult.parsed();
		if (evaluation.diagnosticCandidates() == null || evaluation.diagnosticCandidates().isEmpty()) {
			throw new BusinessException(ErrorCode.CANDIDATE_REVISE_NO_DIAGNOSTIC_RESULT);
		}
		String snapshotCandidateId = evaluation.diagnosticCandidates().get(0).candidateId();
		// Modal이 평가 원문의 해시 무결성을 검증하므로(README) 타입 모델을 거치지 않고 원문
		// 바이트를 그대로 트리로 옮긴다 - 재직렬화하면 우리가 매핑하지 않은 필드가 사라진다.
		JsonNode evaluationNode = jsonMapper.readTree(reassessResult.rawJson());
		StoredCandidate source = new StoredCandidate(
				evaluationNode, snapshotCandidateId, "candidate-" + candidateId + "-v" + version.versionId());

		ReviseCandidateResponse response = briefReviewService.reviseCandidate(
				candidate.requestId(), memberId, new ReviseCandidateApiRequest(source, instruction));
		log.info("[CANDIDATE-REVISE] candidate={} version={} nextOperation={} by={}",
				candidateId, version.versionId(), response.nextOperation(), memberId);
		return response;
	}
}
