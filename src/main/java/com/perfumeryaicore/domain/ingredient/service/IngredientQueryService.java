package com.perfumeryaicore.domain.ingredient.service;

import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.entity.CandidateVersion;
import com.perfumeryaicore.domain.formula.entity.CandidateVersionIngredient;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionRepository;
import com.perfumeryaicore.domain.ingredient.dto.response.IngredientDetailResponse;
import com.perfumeryaicore.domain.ingredient.dto.response.IngredientResponse;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 원료 조회. 조향 AI가 원료 목록 API를 제공하지 않으므로, 지정한 프로젝트에서 실제로 생성된
 * 조향식({@code candidate_version_ingredients})에 등장한 원료를 외부 식별자 기준으로 중복 제거해
 * "로컬 미러"처럼 보여준다. 같은 식별자가 여러 버전에 있으면 <b>가장 최근 버전</b>의 값을 채택한다.
 *
 * <p>반드시 프로젝트 하나로 범위를 좁혀 조회한다(BE-069) — 이전에는 요청자가 속한 모든 프로젝트를
 * 한데 모아 집계해서, 같은 외부 원료 ID가 여러 프로젝트에 등장하면 다른 프로젝트의 최신 관측값이
 * 섞여 나올 수 있었다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientQueryService {

	private final ProjectAccessGuard accessGuard;
	private final CandidateRepository candidateRepository;
	private final CandidateVersionRepository candidateVersionRepository;
	private final CandidateVersionIngredientRepository candidateVersionIngredientRepository;

	public List<IngredientResponse> list(Long memberId, Long projectId, String query, String pyramid) {
		accessGuard.requireMember(projectId, memberId);
		return collect(projectId).rows().stream()
				.filter(r -> query == null || r.name().toLowerCase().contains(query.toLowerCase()))
				.filter(r -> pyramid == null || pyramid.equalsIgnoreCase(r.pyramid()))
				.toList();
	}

	public IngredientDetailResponse get(Long memberId, Long projectId, String ingredientId) {
		accessGuard.requireMember(projectId, memberId);
		Observed observed = collect(projectId);
		IngredientResponse row = observed.rows().stream()
				.filter(r -> r.ingredientId() != null && r.ingredientId().equals(ingredientId))
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.INGREDIENT_NOT_FOUND));
		return new IngredientDetailResponse(row,
				observed.candidateIdsByIngredient().getOrDefault(ingredientId, List.of()));
	}

	/** 지정한 프로젝트 범위의 원료 관측 결과를 한 번에 모은다. */
	private Observed collect(Long projectId) {
		List<Candidate> candidates = candidateRepository.findByProjectIdIn(List.of(projectId));
		if (candidates.isEmpty()) {
			return new Observed(List.of(), Map.of());
		}
		Map<Long, Long> candidateIdByVersionId = new LinkedHashMap<>();
		List<Long> candidateIds = candidates.stream().map(Candidate::getId).toList();

		List<CandidateVersion> versions = candidateVersionRepository.findByCandidateIdIn(candidateIds);
		Map<Long, LocalDateTime> versionCreatedAt = versions.stream()
				.collect(Collectors.toMap(CandidateVersion::getId, CandidateVersion::getCreatedAt));
		versions.forEach(v -> candidateIdByVersionId.put(v.getId(), v.getCandidateId()));
		if (versions.isEmpty()) {
			return new Observed(List.of(), Map.of());
		}

		List<CandidateVersionIngredient> lines = candidateVersionIngredientRepository
				.findByCandidateVersionIdIn(List.copyOf(candidateIdByVersionId.keySet()));

		// 외부 식별자(없으면 이름) 기준 그룹핑
		Map<String, List<CandidateVersionIngredient>> grouped = lines.stream()
				.collect(Collectors.groupingBy(IngredientQueryService::keyOf, LinkedHashMap::new, Collectors.toList()));

		List<IngredientResponse> rows = new ArrayList<>();
		Map<String, List<Long>> candidateIdsByIngredient = new LinkedHashMap<>();

		for (Map.Entry<String, List<CandidateVersionIngredient>> entry : grouped.entrySet()) {
			List<CandidateVersionIngredient> group = entry.getValue();
			CandidateVersionIngredient latest = group.stream()
					.max(Comparator.comparing(l -> versionCreatedAt.getOrDefault(
							l.getCandidateVersionId(), LocalDateTime.MIN)))
					.orElseThrow();
			LocalDateTime lastSeenAt = versionCreatedAt.get(latest.getCandidateVersionId());

			List<Long> usingCandidateIds = group.stream()
					.map(l -> candidateIdByVersionId.get(l.getCandidateVersionId()))
					.filter(Objects::nonNull)
					.distinct()
					.toList();
			candidateIdsByIngredient.put(entry.getKey(), usingCandidateIds);

			rows.add(new IngredientResponse(
					latest.getIngredientExternalId(),
					latest.getIngredientName(),
					latest.getPyramid(),
					latest.getPricePerKg(),
					latest.getAvailability(),
					usingCandidateIds.size(),
					lastSeenAt));
		}
		rows.sort(Comparator.comparing(IngredientResponse::name, Comparator.nullsLast(String::compareTo)));
		return new Observed(rows, candidateIdsByIngredient);
	}

	private static String keyOf(CandidateVersionIngredient line) {
		return StringUtils.hasText(line.getIngredientExternalId())
				? line.getIngredientExternalId()
				: line.getIngredientName();
	}

	private record Observed(List<IngredientResponse> rows, Map<String, List<Long>> candidateIdsByIngredient) {
	}
}
