package com.perfumeryaicore.domain.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.perfumeryaicore.domain.evidence.dto.response.EvidenceEvent;
import com.perfumeryaicore.domain.evidence.dto.response.SensoryTestResponse;
import com.perfumeryaicore.domain.evidence.entity.SensoryTestStatus;
import com.perfumeryaicore.domain.evidence.service.EvidenceReportBundle;
import com.perfumeryaicore.domain.evidence.service.EvidenceReportPdfRenderer;
import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse.GenerationMeta;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse.IngredientLine;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse.HumanValidation;
import com.perfumeryaicore.domain.safety.dto.response.SafetyEvaluationResponse;
import com.perfumeryaicore.global.common.CandidateStatus;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceReportPdfRendererTest {

	private final EvidenceReportPdfRenderer renderer = new EvidenceReportPdfRenderer();

	private EvidenceReportBundle bundle() {
		CandidateVersionResponse version = new CandidateVersionResponse(
				1200L, 900L, null,
				List.of(new IngredientLine("dihydromyrcenol", "Dihydromyrcenol", "top", 23.5, 3.5, 18.0, 0.99)),
				42.0, "안전·가격·의미 조건을 충족한 R&D 후보입니다.",
				new GenerationMeta("modal", false, "prototype_ready", 1690L),
				null, 7L, LocalDateTime.now());
		CandidateResponse candidate = new CandidateResponse(
				900L, 5L, CandidateStatus.CONFIRMED_FOR_EXPERIMENT, version);

		SafetyEvaluationResponse safety = new SafetyEvaluationResponse(
				900L, 1200L, "PASSED", true, false, "internal", 62.5, false, true, false,
				"EU", "eau_de_parfum", "audit-9001", "2026-09-01", "2027-03-01",
				null, null, null, null);

		PredictionResponse prediction = new PredictionResponse(
				900L, 1200L, "prototype_ready", 87.42, "semantic_profile_proxy", 0.71, 64.0, true,
				"monte_carlo_quantile", "not_independently_validated", "research_only",
				new HumanValidation(false, null, null, null, null, null),
				null, null, null);

		List<EvidenceEvent> timeline = List.of(
				new EvidenceEvent("CANDIDATE_VERSION_CREATED", 1200L, 7L,
						LocalDateTime.now().minusHours(2), "버전 생성 (AI: modal)"),
				new EvidenceEvent("APPROVAL_GATE_APPROVED", null, 3L,
						LocalDateTime.now().minusHours(1), "IFRA 기준 충족 확인"));

		List<SensoryTestResponse> sensoryTests = List.of(new SensoryTestResponse(
				10L, 900L, "5인 패널 블라인드 삼각 검사", SensoryTestStatus.COMPLETED, List.of(), LocalDateTime.now()));

		return new EvidenceReportBundle(900L, candidate, safety, prediction, timeline, sensoryTests,
				LocalDateTime.now(), 7L);
	}

	@Test
	void renders_a_non_trivial_pdf_with_korean_text() {
		byte[] pdf = renderer.render(bundle());

		assertThat(pdf).isNotEmpty();
		assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).startsWith("%PDF-");
		assertThat(pdf.length).isGreaterThan(2000); // 임베드된 서브셋 폰트 + 본문
	}

	@Test
	void tolerates_missing_sections_without_failing() {
		EvidenceReportBundle sparse = new EvidenceReportBundle(
				900L, null, null, null, List.of(), List.of(), LocalDateTime.now(), 7L);

		byte[] pdf = renderer.render(sparse);

		assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).startsWith("%PDF-");
	}
}
