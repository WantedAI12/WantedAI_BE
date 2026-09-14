package com.perfumeryaicore.domain.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.formula.entity.GenerationRejection;
import com.perfumeryaicore.domain.formula.repository.GenerationRejectionRepository;
import com.perfumeryaicore.domain.formula.service.GenerationRejectionService;
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.service.FragranceRequestService;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** BE-035: 기권 이력 조회가 후보 접근 제어와 같은 규칙(프로젝트 멤버십)을 쓰는지, 원문이 진단으로 파싱되는지 검증한다. */
class GenerationRejectionServiceTest {

	private final GenerationRejectionRepository rejectionRepository = mock(GenerationRejectionRepository.class);
	private final FragranceRequestService fragranceRequestService = mock(FragranceRequestService.class);
	private final GenerationRejectionService service =
			new GenerationRejectionService(rejectionRepository, fragranceRequestService);

	@Test
	void list_denies_access_when_the_request_is_not_accessible() {
		when(fragranceRequestService.getConfirmedRequest(5L, 1L))
				.thenThrow(new BusinessException(ErrorCode.REQUEST_ACCESS_DENIED));

		assertThatThrownBy(() -> service.list(5L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.REQUEST_ACCESS_DENIED);
	}

	@Test
	void list_parses_the_raw_response_into_diagnostics() {
		when(fragranceRequestService.getConfirmedRequest(5L, 1L)).thenReturn(mock(FragranceRequest.class));
		GenerationRejection rejection = GenerationRejection.of(
				5L, 10L, 77L, "no_safe_match", "요청 기준 95.00점 미충족",
				"{\"status\":\"no_safe_match\",\"closest_candidate\":{\"score\":29.45}}", 1L);
		when(rejectionRepository.findByRequestIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(rejection));

		List<com.perfumeryaicore.domain.formula.dto.response.GenerationRejectionResponse> result =
				service.list(5L, 1L);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).reasonCode()).isEqualTo("no_safe_match");
		assertThat(result.get(0).diagnostics()).isNotNull();
		assertThat(result.get(0).diagnostics().path("closest_candidate").path("score").asDouble()).isEqualTo(29.45);
	}

	@Test
	void list_tolerates_an_unparsable_raw_response() {
		when(fragranceRequestService.getConfirmedRequest(5L, 1L)).thenReturn(mock(FragranceRequest.class));
		GenerationRejection rejection = GenerationRejection.of(
				5L, 10L, 77L, "no_safe_match", "메시지", "not json", 1L);
		when(rejectionRepository.findByRequestIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(rejection));

		var result = service.list(5L, 1L);

		assertThat(result.get(0).diagnostics()).isNull();
	}
}
