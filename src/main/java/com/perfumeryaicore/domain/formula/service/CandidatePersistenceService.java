package com.perfumeryaicore.domain.formula.service;

import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.entity.CandidateVersion;
import com.perfumeryaicore.domain.formula.entity.CandidateVersionIngredient;
import com.perfumeryaicore.domain.formula.entity.GenerationRejection;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionRepository;
import com.perfumeryaicore.domain.formula.repository.GenerationRejectionRepository;
import com.perfumeryaicore.global.client.PerfumeryAiResult;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse.RecipeLine;
import com.perfumeryaicore.global.client.dto.LotionDesignResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * 조향 AI 생성 결과를 후보(첫 버전)로 저장한다. {@link com.perfumeryaicore.domain.job.service.JobExecutor}가
 * 실행하는 작업 본문 안에서 호출되므로, 상태 갱신(job)과 독립된 자체 트랜잭션을 가진다.
 */
@Service
@RequiredArgsConstructor
public class CandidatePersistenceService {

	private final CandidateRepository candidateRepository;
	private final CandidateVersionRepository candidateVersionRepository;
	private final CandidateVersionIngredientRepository ingredientRepository;
	private final GenerationRejectionRepository generationRejectionRepository;

	@Transactional
	public Long persist(Long requestId, Long projectId, Long memberId, Long jobId,
			PerfumeryAiResult<FormulaGenerationResponse> result) {
		FormulaGenerationResponse parsed = result.parsed();

		Candidate candidate = candidateRepository.save(Candidate.create(requestId, projectId, memberId, jobId));

		CandidateVersion version = candidateVersionRepository.save(CandidateVersion.builder()
				.candidateId(candidate.getId())
				.parentVersionId(null)
				.cost(parsed.estimatedConcentrateCostPerKg())
				.generationRationale(parsed.message())
				.aiProvider(parsed.deployment() != null ? parsed.deployment().provider() : null)
				.aiGpuUsed(parsed.deployment() != null ? parsed.deployment().gpuRequired() : null)
				.aiResponseStatus(parsed.status())
				.aiLatencyMs(result.latencyMillis())
				.rawResponse(result.rawJson())
				.createdBy(memberId)
				.build());

		candidate.attachVersion(version.getId());

		List<RecipeLine> recipe = parsed.recipe();
		if (recipe != null && !recipe.isEmpty()) {
			List<CandidateVersionIngredient> lines = recipe.stream()
					.map(r -> CandidateVersionIngredient.builder()
							.candidateVersionId(version.getId())
							.ingredientExternalId(r.ingredientId())
							.ingredientName(r.name())
							.pyramid(r.pyramid())
							.concentratePercent(r.concentratePercent())
							.finishedProductPercent(r.finishedProductPercent())
							.pricePerKg(r.pricePerKg())
							.availability(r.availability())
							.build())
					.toList();
			ingredientRepository.saveAll(lines);
		}

		return candidate.getId();
	}

	/**
	 * 바디로션 설계 성공 결과를 후보(첫 버전)로 저장한다. {@code persist}와 같은 구조를 쓰되,
	 * recipe 라인 구조를 아직 확정하지 못해(실제 성공 응답 예시 미확인, 1단계) 각 줄을
	 * {@link JsonNode}에서 방어적으로 뽑는다 - 흔한 필드명을 가정하되 없으면 조용히 null로
	 * 둔다(추측으로 잘못된 값을 채우지 않음).
	 */
	@Transactional
	public Long persistLotion(Long requestId, Long projectId, Long memberId, Long jobId,
			PerfumeryAiResult<LotionDesignResponse> result) {
		LotionDesignResponse parsed = result.parsed();

		Candidate candidate = candidateRepository.save(Candidate.create(requestId, projectId, memberId, jobId));

		CandidateVersion version = candidateVersionRepository.save(CandidateVersion.builder()
				.candidateId(candidate.getId())
				.parentVersionId(null)
				.aiResponseStatus(parsed.status())
				.aiLatencyMs(result.latencyMillis())
				.rawResponse(result.rawJson())
				.createdBy(memberId)
				.build());

		candidate.attachVersion(version.getId());

		if (parsed.recipe() != null && !parsed.recipe().isEmpty()) {
			List<CandidateVersionIngredient> lines = parsed.recipe().stream()
					.map(line -> toIngredientLine(version.getId(), line))
					.toList();
			ingredientRepository.saveAll(lines);
		}

		return candidate.getId();
	}

	private CandidateVersionIngredient toIngredientLine(Long versionId, JsonNode line) {
		String ingredientId = textOrNull(line, "ingredient_id");
		String name = textOrNull(line, "name");
		return CandidateVersionIngredient.builder()
				.candidateVersionId(versionId)
				.ingredientExternalId(ingredientId)
				.ingredientName(name != null ? name : (ingredientId != null ? ingredientId : "unknown"))
				.pyramid(textOrNull(line, "pyramid"))
				.concentratePercent(numberOrNull(line, "concentrate_percent"))
				.finishedProductPercent(numberOrNull(line, "finished_product_percent"))
				.pricePerKg(numberOrNull(line, "price_per_kg"))
				.availability(numberOrNull(line, "availability"))
				.build();
	}

	private static String textOrNull(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() || value.isNull() ? null : value.asString();
	}

	private static Double numberOrNull(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() || value.isNull() || !value.isNumber() ? null : value.asDouble();
	}

	/**
	 * BE-035: 기권(no_safe_match 등) 응답의 진단 데이터를 후보와 별도로 보존한다. 절대 후보로
	 * 만들지 않는다 - 정상 추천 후보 목록에 섞이면 안 된다. 제품(향수/로션 등)에 관계없이
	 * 재사용할 수 있도록 특정 응답 타입에 묶지 않고 원시값으로 받는다.
	 */
	@Transactional
	public void persistRejection(Long requestId, Long projectId, Long jobId, Long memberId,
			String reasonCode, String message, String rawResponse) {
		generationRejectionRepository.save(GenerationRejection.of(
				requestId, projectId, jobId, reasonCode, message, rawResponse, memberId));
	}
}
