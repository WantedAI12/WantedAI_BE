package com.perfumeryaicore.domain.evidence.service;

import com.perfumeryaicore.domain.evidence.dto.response.EvidenceEvent;
import com.perfumeryaicore.domain.evidence.dto.response.SensoryTestResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse.IngredientLine;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse;
import com.perfumeryaicore.domain.safety.dto.response.SafetyEvaluationResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * {@link EvidenceReportBundle}을 PDF 바이트로 그린다. 한글 표시를 위해 조향 AI 응답이나 사용자
 * 입력을 재해석하지 않고 그대로 줄글로 옮긴다 — 표·그래프 없이 문서화가 목적인 1차 버전이다.
 *
 * <p>PDFBox 표준 14 폰트는 한글 글리프가 없어 별도로 한글 지원 폰트(나눔고딕, SIL OFL 라이선스)를
 * {@code classpath:/fonts/NanumGothic-Regular.ttf}에 번들해 임베드한다. 문서에 실제 쓰인 글리프만
 * 남기도록 서브셋 임베딩을 쓴다({@link PDType0Font#load}의 embedSubset=true + {@link PDFont#subset()}).
 * (Noto Sans KR은 가변 폰트만 배포되어 PDFBox 서브셋터의 cmap 처리와 맞지 않아 정적 폰트인 나눔고딕을 쓴다.)
 */
@Component
public class EvidenceReportPdfRenderer {

	private static final String FONT_RESOURCE = "/fonts/NanumGothic-Regular.ttf";
	private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
	private static final float MARGIN = 50f;
	private static final float USABLE_WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;
	private static final float BODY_SIZE = 10.5f;
	private static final float HEADING_SIZE = 14f;
	private static final float LINE_GAP = 15f;
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public byte[] render(EvidenceReportBundle bundle) {
		try (PDDocument document = new PDDocument()) {
			PDFont font = loadFont(document);
			Cursor cursor = new Cursor(document, font);

			cursor.heading("증거 보고서 — 후보 #" + bundle.candidateId());
			cursor.text("생성 시각: " + bundle.generatedAt().format(TIMESTAMP)
					+ "   생성자 회원 ID: " + bundle.generatedBy());
			cursor.text("이 보고서는 연구개발 단계 계산 프록시 근거이며, 제조 승인이나 시장 출시 승인이 아닙니다.");
			cursor.gap();

			writeCandidateSection(cursor, bundle.candidate());
			writeSafetySection(cursor, bundle.safety());
			writePredictionSection(cursor, bundle.prediction());
			writeTimelineSection(cursor, bundle.timeline());
			writeSensorySection(cursor, bundle.sensoryTests());

			cursor.finish();

			// embedSubset=true로 로드한 폰트는 document.save()가 알아서 서브셋 임베딩한다
			// (PDFBox 3.x — 별도 font.subset() 호출은 오히려 이중 처리로 깨진다).
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			document.save(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("증거 보고서 PDF 렌더링에 실패했습니다.", e);
		}
	}

	private void writeCandidateSection(Cursor cursor, CandidateResponse candidate) throws IOException {
		cursor.heading("1. 후보 개요");
		if (candidate == null) {
			cursor.text("후보 정보를 불러오지 못했습니다.");
			cursor.gap();
			return;
		}
		cursor.text("후보 ID: " + candidate.candidateId() + "   요청 ID: " + candidate.requestId()
				+ "   상태: " + candidate.status());

		CandidateVersionResponse version = candidate.currentVersion();
		if (version == null) {
			cursor.text("생성된 버전이 없습니다.");
			cursor.gap();
			return;
		}
		cursor.text("현재 버전: #" + version.versionId()
				+ "   예상 농축액 비용(1kg): " + orNone(version.cost())
				+ "   생성 시각: " + (version.createdAt() == null ? "-" : version.createdAt().format(TIMESTAMP)));
		if (version.generationRationale() != null) {
			cursor.text("생성 근거: " + version.generationRationale());
		}

		List<IngredientLine> ingredients = version.ingredients();
		if (ingredients == null || ingredients.isEmpty()) {
			cursor.text("원료 구성 정보 없음");
		} else {
			cursor.text("원료 구성 (" + ingredients.size() + "종):");
			for (IngredientLine ingredient : ingredients) {
				cursor.bullet(ingredient.name() + " [" + orNone(ingredient.pyramid()) + "] 농축액 "
						+ orNone(ingredient.concentratePercent()) + "%  완제품 "
						+ orNone(ingredient.finishedProductPercent()) + "%  가용성 "
						+ orNone(ingredient.availability()));
			}
		}
		cursor.gap();
	}

	private void writeSafetySection(Cursor cursor, SafetyEvaluationResponse safety) throws IOException {
		cursor.heading("2. 안전·규제 평가");
		if (safety == null || safety.status() == null) {
			cursor.text("안전 평가 데이터 없음");
			cursor.gap();
			return;
		}
		cursor.text("상태: " + safety.status()
				+ "   내부 게이트 통과: " + orNone(safety.internalGatePassed())
				+ "   제조 준비: " + orNone(safety.manufacturingReady()));
		cursor.text("증거 커버리지: " + orNone(safety.evidenceCoveragePercent()) + "%"
				+ "   대상 시장: " + orNone(safety.targetRegion())
				+ "   제품군: " + orNone(safety.productCategory()));
		cursor.text("위반 사항: " + describeNode(safety.violations()));
		cursor.text("경고: " + describeNode(safety.warnings()));
		cursor.text("미비 서류: " + describeNode(safety.missingDocuments()));
		cursor.gap();
	}

	private void writePredictionSection(Cursor cursor, PredictionResponse prediction) throws IOException {
		cursor.heading("3. 성능 프록시 예측");
		if (prediction == null || prediction.status() == null) {
			cursor.text("예측 데이터 없음");
			cursor.gap();
			return;
		}
		cursor.text("목표 유사도(계산값): " + orNone(prediction.similarityScore())
				+ "   유사도 종류: " + orNone(prediction.similarityKind()));
		cursor.text("모델 적용범위: " + orNone(prediction.modelApplicabilityPercent()) + "%"
				+ "   도메인 통과: " + orNone(prediction.scientificModelDomainPassed()));
		boolean claimAuthorized = prediction.humanValidation() != null
				&& Boolean.TRUE.equals(prediction.humanValidation().similarity90ClaimAuthorized());
		cursor.text("인간 후각 유사도 주장 승인 여부: " + (claimAuthorized ? "예 (독립 서명된 관능 결과 존재)" : "아니오"));
		cursor.gap();
	}

	private void writeTimelineSection(Cursor cursor, List<EvidenceEvent> timeline) throws IOException {
		cursor.heading("4. 감사 이력");
		if (timeline == null || timeline.isEmpty()) {
			cursor.text("이력 없음");
			cursor.gap();
			return;
		}
		for (EvidenceEvent event : timeline) {
			String when = event.occurredAt() == null ? "-" : event.occurredAt().format(TIMESTAMP);
			cursor.bullet(when + "  " + event.action() + "  (actor #" + event.actorId() + ")"
					+ (event.detail() != null ? " — " + event.detail() : ""));
		}
		cursor.gap();
	}

	private void writeSensorySection(Cursor cursor, List<SensoryTestResponse> tests) throws IOException {
		cursor.heading("5. 독립 블라인드 관능 검증");
		if (tests == null || tests.isEmpty()) {
			cursor.text("등록된 검증 없음");
			cursor.gap();
			return;
		}
		for (SensoryTestResponse test : tests) {
			int resultCount = test.results() == null ? 0 : test.results().size();
			cursor.bullet("검증 #" + test.testId() + "  상태: " + test.status()
					+ "  결과 " + resultCount + "건  계획: " + orNone(test.planDetail()));
		}
		cursor.gap();
	}

	private static String describeNode(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return "없음";
		}
		if (node.isArray()) {
			return node.isEmpty() ? "없음" : node.toString();
		}
		return node.toString();
	}

	private static String orNone(Object value) {
		return value == null ? "-" : value.toString();
	}

	/**
	 * classpath 리소스를 임시 파일로 한 번 풀어두고 재사용한다. PDFBox의 서브셋 임베딩은
	 * 저장 시점(save)에 폰트 테이블을 다시 읽으므로 스트림보다 파일 기반 로드가 안전하다.
	 */
	private static volatile Path fontFile;

	private PDFont loadFont(PDDocument document) throws IOException {
		// embedSubset=true, closeData=true → PDFBox가 save 시점에 폰트를 다시 읽고 끝나면 닫는다.
		return PDType0Font.load(document, new RandomAccessReadBufferedFile(resolveFontFile()), true, true);
	}

	private static Path resolveFontFile() throws IOException {
		Path resolved = fontFile;
		if (resolved != null && Files.exists(resolved)) {
			return resolved;
		}
		synchronized (EvidenceReportPdfRenderer.class) {
			if (fontFile != null && Files.exists(fontFile)) {
				return fontFile;
			}
			try (InputStream fontStream = EvidenceReportPdfRenderer.class.getResourceAsStream(FONT_RESOURCE)) {
				if (fontStream == null) {
					throw new IOException("한글 폰트 리소스를 찾을 수 없습니다: " + FONT_RESOURCE);
				}
				Path temp = Files.createTempFile("perfumery-evidence-font-", ".ttf");
				temp.toFile().deleteOnExit();
				Files.copy(fontStream, temp, StandardCopyOption.REPLACE_EXISTING);
				fontFile = temp;
				return temp;
			}
		}
	}

	/** 페이지 넘김·줄바꿈을 관리하는 작은 커서. */
	private static final class Cursor {
		private final PDDocument document;
		private final PDFont font;
		private PDPageContentStream stream;
		private float y;

		Cursor(PDDocument document, PDFont font) throws IOException {
			this.document = document;
			this.font = font;
			newPage();
		}

		void heading(String text) throws IOException {
			for (String line : wrap(text, HEADING_SIZE)) {
				ensureSpace(HEADING_SIZE + LINE_GAP);
				write(line, HEADING_SIZE);
				y -= LINE_GAP;
			}
		}

		void text(String text) throws IOException {
			for (String line : wrap(text, BODY_SIZE)) {
				ensureSpace(BODY_SIZE + LINE_GAP);
				write(line, BODY_SIZE);
				y -= LINE_GAP;
			}
		}

		void bullet(String text) throws IOException {
			text("- " + text);
		}

		void gap() {
			y -= LINE_GAP / 2;
		}

		void finish() throws IOException {
			stream.close();
		}

		private void write(String sanitizedLine, float fontSize) throws IOException {
			stream.beginText();
			stream.setFont(font, fontSize);
			stream.newLineAtOffset(MARGIN, y);
			stream.showText(sanitizedLine);
			stream.endText();
		}

		/** 페이지 폭을 넘는 줄을 단어 단위(공백 없으면 글자 단위)로 잘라 여러 줄로 만든다. */
		private List<String> wrap(String rawText, float fontSize) throws IOException {
			String sanitized = rawText == null ? "" : rawText.replace("\r", " ").replace("\n", " ");
			List<String> lines = new ArrayList<>();
			StringBuilder current = new StringBuilder();
			for (String word : sanitized.split(" ")) {
				String candidate = current.isEmpty() ? word : current + " " + word;
				if (width(candidate, fontSize) <= USABLE_WIDTH || current.isEmpty()) {
					current = new StringBuilder(candidate);
					// 단어 하나 자체가 폭을 넘으면 글자 단위로 강제 절단
					while (width(current.toString(), fontSize) > USABLE_WIDTH && current.length() > 1) {
						lines.add(current.substring(0, current.length() - 1));
						current = new StringBuilder(current.substring(current.length() - 1));
					}
				} else {
					lines.add(current.toString());
					current = new StringBuilder(word);
				}
			}
			if (!current.isEmpty() || lines.isEmpty()) {
				lines.add(current.toString());
			}
			return lines;
		}

		private float width(String text, float fontSize) throws IOException {
			return font.getStringWidth(text) / 1000f * fontSize;
		}

		private void ensureSpace(float needed) throws IOException {
			if (y - needed < MARGIN) {
				stream.close();
				newPage();
			}
		}

		private void newPage() throws IOException {
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			stream = new PDPageContentStream(document, page);
			y = PAGE_HEIGHT - MARGIN;
		}
	}
}
