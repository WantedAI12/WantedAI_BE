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
import com.perfumeryaicore.domain.request.service.WorkChecklistService;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.common.TargetRegion;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FragranceRequestServiceTest {

	private final FragranceRequestRepository repository = mock(FragranceRequestRepository.class);
	private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);
	private final WorkChecklistService workChecklistService = mock(WorkChecklistService.class);
	private final FragranceRequestService service =
			new FragranceRequestService(repository, accessGuard, workChecklistService);

	@BeforeEach
	void memberIsProjectMember() {
		when(accessGuard.isMember(10L, 1L)).thenReturn(true);
		when(accessGuard.requireWriteRole(10L, 1L, ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PRODUCT_BRAND))
				.thenReturn(ProjectRole.PERFUMER);
	}

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
		var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
		when(repository.findByProjectIdAndStatusOrderByCreatedAtDesc(10L, RequestStatus.CONFIRMED, pageable))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

		assertThat(service.list(10L, 1L, RequestStatus.CONFIRMED, pageable).content()).isEmpty();
	}

	@Test
	void list_is_denied_for_a_non_member() {
		var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
		assertThatThrownBy(() -> service.list(10L, 999L, null, pageable))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.REQUEST_ACCESS_DENIED);
	}

	/** BE-085: 상태 필터 없이 조회하면 전체 프로젝트 요청을 페이지 단위로 조회하고, 메타데이터를 그대로 돌려준다. */
	@Test
	void list_without_a_status_filter_returns_page_metadata_from_the_repository() {
		var pageable = org.springframework.data.domain.PageRequest.of(1, 2);
		FragranceRequest a = FragranceRequest.create(10L, 1L, "raw-a");
		FragranceRequest b = FragranceRequest.create(10L, 1L, "raw-b");
		var page = new org.springframework.data.domain.PageImpl<>(
				List.of(a, b), pageable, 5);
		when(repository.findByProjectIdOrderByCreatedAtDesc(10L, pageable)).thenReturn(page);

		var result = service.list(10L, 1L, null, pageable);

		assertThat(result.content()).hasSize(2);
		assertThat(result.page()).isEqualTo(1);
		assertThat(result.totalElements()).isEqualTo(5);
		assertThat(result.totalPages()).isEqualTo(3);
		assertThat(result.hasNext()).isTrue();
	}

	/**
	 * 화면에 보여줄 번호는 전체가 공용으로 쓰는 요청 ID가 아니라 프로젝트 안에서 1부터 매긴 순번이어야
	 * 한다 - 목록은 최신순으로 나와도 각 요청은 자기 생성 순서의 번호를 유지한다.
	 */
	@Test
	void list_numbers_each_request_by_its_position_within_the_project_regardless_of_the_global_id() {
		var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
		FragranceRequest newest = FragranceRequest.create(10L, 1L, "raw-newest");
		FragranceRequest oldest = FragranceRequest.create(10L, 1L, "raw-oldest");
		org.springframework.test.util.ReflectionTestUtils.setField(newest, "id", 57L);
		org.springframework.test.util.ReflectionTestUtils.setField(oldest, "id", 13L);
		when(repository.findByProjectIdOrderByCreatedAtDesc(10L, pageable))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(newest, oldest)));
		when(repository.findIdsByProjectIdOrderByIdAsc(10L)).thenReturn(List.of(13L, 40L, 57L));

		var result = service.list(10L, 1L, null, pageable);

		assertThat(result.content()).extracting(FragranceRequestResponse::requestId).containsExactly(57L, 13L);
		assertThat(result.content()).extracting(FragranceRequestResponse::requestNumber).containsExactly(3, 1);
	}

	@Test
	void a_single_request_response_carries_the_per_project_number() {
		FragranceRequest request = FragranceRequest.create(10L, 1L, "raw");
		org.springframework.test.util.ReflectionTestUtils.setField(request, "id", 13L);
		when(repository.findById(13L)).thenReturn(Optional.of(request));
		when(repository.countByProjectIdAndIdLessThanEqual(10L, 13L)).thenReturn(1L);

		FragranceRequestResponse res = service.get(13L, 1L);

		assertThat(res.requestId()).isEqualTo(13L);
		assertThat(res.requestNumber()).isEqualTo(1);
	}

	@Test
	void create_is_denied_for_a_non_member() {
		assertThatThrownBy(() -> service.create(10L, 999L, createDto(true)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.REQUEST_ACCESS_DENIED);
	}

	/** BE-004/BE-002: SUPPLIER/AUDITOR 같은 비쓰기 역할은 요청을 만들거나 바꿀 수 없다. */
	@Test
	void create_is_forbidden_for_a_non_write_role() {
		when(accessGuard.requireWriteRole(10L, 1L, ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PRODUCT_BRAND))
				.thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.create(10L, 1L, createDto(true)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	@Test
	void update_is_forbidden_for_a_non_write_role() {
		FragranceRequest incomplete = FragranceRequest.create(10L, 1L, "raw");
		when(repository.findById(5L)).thenReturn(Optional.of(incomplete));
		when(accessGuard.requireWriteRole(10L, 1L, ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PRODUCT_BRAND))
				.thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.update(5L, 1L, new UpdateFragranceRequestRequest(
				"new text", null, null, null, null, null, null, null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}
}
