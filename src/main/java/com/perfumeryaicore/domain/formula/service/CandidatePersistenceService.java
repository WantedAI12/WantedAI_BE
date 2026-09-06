package com.perfumeryaicore.domain.formula.service;

import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.entity.CandidateVersion;
import com.perfumeryaicore.domain.formula.entity.CandidateVersionIngredient;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionRepository;
import com.perfumeryaicore.global.client.PerfumeryAiResult;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse.RecipeLine;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	@Transactional
	public Long persist(Long requestId, Long projectId, Long memberId, Long jobId, PerfumeryAiResult result) {
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
}
