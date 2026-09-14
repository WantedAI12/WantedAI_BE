package com.perfumeryaicore.domain.formula.service;

import com.perfumeryaicore.domain.formula.dto.response.GenerationRejectionResponse;
import com.perfumeryaicore.domain.formula.entity.GenerationRejection;
import com.perfumeryaicore.domain.formula.repository.GenerationRejectionRepository;
import com.perfumeryaicore.domain.request.service.FragranceRequestService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 기권(no_safe_match)한 생성 시도 이력 조회(BE-035 일부). 절대 후보 목록과 섞이지 않는다 -
 * {@link com.perfumeryaicore.domain.formula.controller.CandidateController}의 후보 목록
 * 엔드포인트와 별도 경로로만 노출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GenerationRejectionService {

	private final GenerationRejectionRepository rejectionRepository;
	private final FragranceRequestService fragranceRequestService;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public List<GenerationRejectionResponse> list(Long requestId, Long memberId) {
		fragranceRequestService.getConfirmedRequest(requestId, memberId);
		return rejectionRepository.findByRequestIdOrderByCreatedAtDesc(requestId).stream()
				.map(rejection -> GenerationRejectionResponse.of(rejection, parseOrNull(rejection.getRawResponse())))
				.toList();
	}

	private JsonNode parseOrNull(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return jsonMapper.readTree(raw);
		} catch (JacksonException e) {
			log.warn("[FORMULA] failed to parse stored generation-rejection raw response: {}", e.getMessage());
			return null;
		}
	}
}
