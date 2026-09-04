package com.perfumeryaicore.domain.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.request.dto.request.CreateFragranceRequestRequest;
import com.perfumeryaicore.domain.request.dto.request.UpdateFragranceRequestRequest;
import com.perfumeryaicore.domain.request.dto.response.FragranceRequestResponse;
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.entity.RequestStatus;
import com.perfumeryaicore.domain.request.repository.FragranceRequestRepository;
import com.perfumeryaicore.domain.request.service.FragranceRequestService;
import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.TargetRegion;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FragranceRequestServiceTest {

	private final FragranceRequestRepository repository = mock(FragranceRequestRepository.class);
	private final FragranceRequestService service = new FragranceRequestService(repository);

	private CreateFragranceRequestRequest createDto(boolean complete) {
		return new CreateFragranceRequestRequest(
				"지속력 좋은 시트러스 우디 남성 향수",
				complete ? ProductCategory.EAU_DE_PARFUM : null,
				complete ? TargetRegion.KR : null,
				complete ? 1 : null,
				null, null, null, null, null,
				List.of("citrus", "woody"));
	}

	private void stubSaveEcho() {
		when(repository.save(any(FragranceRequest.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void create_incomplete_request_is_missing_fields() {
		stubSaveEcho();

		FragranceRequestResponse res = service.create(10L, 1L, createDto(false));

		assertThat(res.status()).isEqualTo(RequestStatus.MISSING_FIELDS);
		assertThat(res.missingFields()).contains("productCategory", "targetRegion", "riskTier");
		assertThat(res.structuredIntent().accords()).containsExactly("citrus", "woody");
	}

	@Test
	void create_complete_request_is_draft_ready_to_confirm() {
		stubSaveEcho();

		FragranceRequestResponse res = service.create(10L, 1L, createDto(true));

		assertThat(res.status()).isEqualTo(RequestStatus.DRAFT);
		assertThat(res.missingFields()).isEmpty();
	}

	@Test
	void confirm_requires_all_required_fields() {
		FragranceRequest incomplete = FragranceRequest.create(10L, 1L, "raw");
		when(repository.findById(5L)).thenReturn(Optional.of(incomplete));

		assertThatThrownBy(() -> service.confirm(5L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.REQUEST_NOT_CONFIRMABLE);
	}

	@Test
	void confirm_succeeds_when_complete_then_blocks_further_edits() {
		FragranceRequest complete = FragranceRequest.create(10L, 1L, "raw");
		complete.applyUpdate(null, ProductCategory.EAU_DE_PARFUM, TargetRegion.EU, 1,
				null, null, null, null, null, null);
		when(repository.findById(5L)).thenReturn(Optional.of(complete));

		FragranceRequestResponse confirmed = service.confirm(5L, 1L);
		assertThat(confirmed.status()).isEqualTo(RequestStatus.CONFIRMED);

		assertThatThrownBy(() -> service.update(5L, 1L, new UpdateFragranceRequestRequest(
				"new text", null, null, null, null, null, null, null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.REQUEST_EDIT_NOT_ALLOWED);
	}

	@Test
	void update_filling_missing_fields_flips_status_to_draft() {
		FragranceRequest incomplete = FragranceRequest.create(10L, 1L, "raw");
		when(repository.findById(5L)).thenReturn(Optional.of(incomplete));
		assertThat(incomplete.getStatus()).isEqualTo(RequestStatus.MISSING_FIELDS); // 초기 상태

		FragranceRequestResponse res = service.update(5L, 1L, new UpdateFragranceRequestRequest(
				null, ProductCategory.CANDLE, TargetRegion.US, 2, null, null, null, null, null, null));

		assertThat(res.status()).isEqualTo(RequestStatus.DRAFT);
		assertThat(res.missingFields()).isEmpty();
		assertThat(res.structuredIntent().productCategory()).isEqualTo(ProductCategory.CANDLE);
	}

	@Test
	void access_is_denied_for_non_owner() {
		FragranceRequest owned = FragranceRequest.create(10L, 1L, "raw");
		when(repository.findById(5L)).thenReturn(Optional.of(owned));

		assertThatThrownBy(() -> service.get(5L, 999L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.REQUEST_ACCESS_DENIED);
	}

	@Test
	void unknown_request_is_not_found() {
		when(repository.findById(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(404L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.REQUEST_NOT_FOUND);
	}

	@Test
	void list_filters_by_status_when_given() {
		when(repository.findByProjectIdAndStatusOrderByCreatedAtDesc(10L, RequestStatus.CONFIRMED))
				.thenReturn(List.of());

		assertThat(service.list(10L, RequestStatus.CONFIRMED)).isEmpty();
	}
}
