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
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class EvidenceReportPdfRendererTest {

	private final EvidenceReportPdfRenderer renderer = new EvidenceReportPdfRenderer();

	private EvidenceReportBundle bundle() {
		CandidateVersionResponse version = new CandidateVersionResponse(
				1200L, 900L, null,
				List.of(new IngredientLine("dihydromyrcenol", "Dihydromyrcenol", "top", 23.5, 3.5, 18.0, 0.99)),
				42.0, "안전·가격·의미 조건을 충족한 R&D 후보입니다.",
				new GenerationMeta("modal", false, "prototype_ready", 1690L),
				null, null, null, null, 7L, LocalDateTime.now(), null);
		CandidateResponse candidate = new CandidateResponse(
				900L, 5L, 1, CandidateStatus.CONFIRMED_FOR_EXPERIMENT, version, null, null, null);

		SafetyEvaluationResponse safety = new SafetyEvaluationResponse(
				900L, 1200L, "PASSED", true, false, "internal", 62.5, false, true, false,
				"EU", "eau_de_parfum", "audit-9001", "2026-09-01", "2027-03-01",
				null, null, null, null);

		PredictionResponse prediction = new PredictionResponse(
				900L, 1200L, "prototype_ready", 87.42, "semantic_profile_proxy", "0.71", 64.0, true,
				"monte_carlo_quantile", "not_independently_validated", "research_only",
				new HumanValidation(false, null, null, null, null, null),
				null, null, null);

		List<EvidenceEvent> timeline = List.of(
				new EvidenceEvent("CANDIDATE_VERSION_CREATED", 1200L, 7L,
						LocalDateTime.now().minusHours(2), "버전 생성 (AI: modal)"),
				new EvidenceEvent("APPROVAL_GATE_APPROVED", null, 3L,
						LocalDateTime.now().minusHours(1), "IFRA 기준 충족 확인"));

		List<SensoryTestResponse> sensoryTests = List.of(new SensoryTestResponse(
				10L, 900L, 1200L, "5인 패널 블라인드 삼각 검사", null, null, null, null, null,
				SensoryTestStatus.COMPLETED, List.of(), LocalDateTime.now(),
				true, 3L, LocalDateTime.now()));

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

	/**
	 * BE-109: 실제로 열어봐야만 드러나는 폰트 서브셋 손상을 잡기 위해, 렌더링한 PDF를 PDFBox
	 * 자체 텍스트 추출기로 다시 읽어 원문 한글·영문·숫자가 그대로 나오는지 검증한다. 이전에는
	 * PDF 헤더/크기만 확인해서, 실제 서비스에서 보고서 본문 글자가 무작위로 빠지거나 한글이
	 * 통째로 사라지는 손상(운영에서 보고, 2026-09-17)을 이 테스트가 잡아내지 못했다.
	 */
	@Test
	void the_rendered_text_round_trips_without_dropped_or_garbled_characters() throws Exception {
		byte[] pdf = renderer.render(bundle());

		String extracted;
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			extracted = new PDFTextStripper().getText(doc);
		}

		assertThat(extracted).contains("증거 보고서");
		assertThat(extracted).contains("후보 개요");
		assertThat(extracted).contains("Dihydromyrcenol");
		assertThat(extracted).contains("23.5");
		assertThat(extracted).contains("3.5");
		assertThat(extracted).contains("안전·가격·의미 조건을 충족한 R&D 후보입니다.");
	}

	@Test
	void tolerates_missing_sections_without_failing() {
		EvidenceReportBundle sparse = new EvidenceReportBundle(
				900L, null, null, null, List.of(), List.of(), LocalDateTime.now(), 7L);

		byte[] pdf = renderer.render(sparse);

		assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).startsWith("%PDF-");
	}
}
