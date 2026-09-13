package com.perfumeryaicore.domain.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.Test;

/**
 * BE-060: 공백 없는 긴 문자열(URL·해시 등)을 강제로 글자 단위로 자를 때, 실제로 페이지 폭 안에
 * 들어가는 줄만 생성하는지 검증한다. 이전 구현은 마지막 한 글자만 떼어내고 나머지 긴 앞부분을
 * 폭 검사 없이 그대로 한 줄로 밀어넣어 여전히 폭을 넘는 줄이 만들어졌다.
 */
class EvidenceReportPdfRendererWrapTest {

	private static final float MARGIN = 50f;
	private static final float USABLE_WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;
	private static final float BODY_SIZE = 10.5f;

	@Test
	void wraps_a_single_unbroken_token_into_lines_that_all_fit_within_the_page_width() throws Exception {
		try (PDDocument document = new PDDocument()) {
			EvidenceReportPdfRenderer renderer = new EvidenceReportPdfRenderer();
			PDFont font = renderer.loadFont(document);
			EvidenceReportPdfRenderer.Cursor cursor = new EvidenceReportPdfRenderer.Cursor(document, font);

			// 공백 없는 300자 - 실제 URL/SHA-256 해시 등을 흉내낸 극단적으로 긴 토큰.
			String longToken = "audit-evidence-reference-".repeat(12);

			List<String> lines = cursor.wrap(longToken, BODY_SIZE);

			assertThat(lines.size()).isGreaterThan(1);
			for (String line : lines) {
				float width = font.getStringWidth(line) / 1000f * BODY_SIZE;
				assertThat(width).isLessThanOrEqualTo(USABLE_WIDTH);
			}
			// 내용 손실 없이 원문을 그대로 이어붙이면 복원되어야 한다.
			assertThat(String.join("", lines)).isEqualTo(longToken);
		}
	}

	@Test
	void short_text_is_not_split_unnecessarily() throws Exception {
		try (PDDocument document = new PDDocument()) {
			EvidenceReportPdfRenderer renderer = new EvidenceReportPdfRenderer();
			PDFont font = renderer.loadFont(document);
			EvidenceReportPdfRenderer.Cursor cursor = new EvidenceReportPdfRenderer.Cursor(document, font);

			List<String> lines = cursor.wrap("안전 조건을 충족한 R&D 후보입니다.", BODY_SIZE);

			assertThat(lines).hasSize(1);
			assertThat(lines.get(0)).isEqualTo("안전 조건을 충족한 R&D 후보입니다.");
		}
	}
}
