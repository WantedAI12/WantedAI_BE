package com.perfumeryaicore.domain.lotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.formula.service.CandidateVersionRawView;
import com.perfumeryaicore.domain.lotion.service.LotionDetailMapper;
import com.perfumeryaicore.domain.lotion.service.LotionDetailService;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

/** 접근 제어는 CandidateService에 전부 위임한다 - 별도 권한 로직을 두지 않는다. */
class LotionDetailServiceTest {

	private final CandidateService candidateService = mock(CandidateService.class);
	private final LotionDetailMapper mapper = new LotionDetailMapper();
	private final LotionDetailService service = new LotionDetailService(candidateService, mapper);

	@Test
	void get_denies_access_when_the_candidate_is_not_accessible() {
		when(candidateService.getCurrentVersionRaw(100L, 1L))
				.thenThrow(new BusinessException(ErrorCode.CANDIDATE_ACCESS_DENIED));

		assertThatThrownBy(() -> service.get(100L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_ACCESS_DENIED);
	}

	@Test
	void get_maps_the_stored_raw_response() {
		when(candidateService.getCurrentVersionRaw(100L, 1L))
				.thenReturn(new CandidateVersionRawView(100L, 200L, "{\"status\":\"ready\"}"));

		var response = service.get(100L, 1L);

		assertThat(response.candidateId()).isEqualTo(100L);
		assertThat(response.status()).isEqualTo("ready");
	}
}
