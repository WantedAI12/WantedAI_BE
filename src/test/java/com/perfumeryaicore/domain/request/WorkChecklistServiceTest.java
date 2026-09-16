package com.perfumeryaicore.domain.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.request.dto.response.ProjectProgressResponse;
import com.perfumeryaicore.domain.request.dto.response.WorkProgressResponse;
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.entity.WorkChecklistItem;
import com.perfumeryaicore.domain.request.entity.WorkChecklistItemType;
import com.perfumeryaicore.domain.request.repository.FragranceRequestRepository;
import com.perfumeryaicore.domain.request.repository.WorkChecklistItemRepository;
import com.perfumeryaicore.domain.request.service.WorkChecklistService;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorkChecklistServiceTest {

	private static final long PROJECT_ID = 10L;
	private static final long REQUEST_ID = 100L;
	private static final long MEMBER_ID = 1L;

	private final WorkChecklistItemRepository checklistItemRepository = mock(WorkChecklistItemRepository.class);
	private final FragranceRequestRepository requestRepository = mock(FragranceRequestRepository.class);
	private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);
	private final WorkChecklistService service =
			new WorkChecklistService(checklistItemRepository, requestRepository, accessGuard);

	private static FragranceRequest withId(FragranceRequest request, long id) {
		try {
			Field field = FragranceRequest.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(request, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return request;
	}

	private static FragranceRequest request(long id) {
		return withId(FragranceRequest.create(PROJECT_ID, MEMBER_ID, "brief"), id);
	}

	/** completedCount개는 완료 상태, 나머지는 미완료 상태인 totalCount개짜리 체크리스트 목록. */
	private static List<WorkChecklistItem> items(long requestId, int completedCount, int totalCount) {
		List<WorkChecklistItem> list = new ArrayList<>();
		for (int i = 0; i < totalCount; i++) {
			WorkChecklistItem item = WorkChecklistItem.create(requestId, WorkChecklistItemType.values()[i % 6]);
			if (i < completedCount) {
				item.setCompleted(true, 0, MEMBER_ID);
			}
			list.add(item);
		}
		return list;
	}

	@Test
	void initialize_creates_all_six_item_types_when_none_exist() {
		when(checklistItemRepository.findByRequestIdOrderByItemTypeAsc(REQUEST_ID)).thenReturn(List.of());

		service.initialize(REQUEST_ID);

		verify(checklistItemRepository, org.mockito.Mockito.times(6)).save(any());
	}

	@Test
	void initialize_does_nothing_if_items_already_exist() {
		when(checklistItemRepository.findByRequestIdOrderByItemTypeAsc(REQUEST_ID))
				.thenReturn(items(REQUEST_ID, 0, 6));

		service.initialize(REQUEST_ID);

		verify(checklistItemRepository, never()).save(any());
	}

	@Test
	void list_is_denied_for_a_non_member() {
		when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request(REQUEST_ID)));
		when(accessGuard.isMember(PROJECT_ID, MEMBER_ID)).thenReturn(false);

		assertThatThrownBy(() -> service.list(REQUEST_ID, MEMBER_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.REQUEST_ACCESS_DENIED);
	}

	@Test
	void setCompleted_rejects_a_stale_expected_revision() {
		when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request(REQUEST_ID)));
		when(accessGuard.isMember(PROJECT_ID, MEMBER_ID)).thenReturn(true);
		WorkChecklistItem item = WorkChecklistItem.create(REQUEST_ID, WorkChecklistItemType.FRAGRANCE_BRIEF);
		item.setCompleted(true, 0, MEMBER_ID); // revision is now 1
		when(checklistItemRepository.findByRequestIdAndItemType(REQUEST_ID, WorkChecklistItemType.FRAGRANCE_BRIEF))
				.thenReturn(Optional.of(item));

		assertThatThrownBy(() -> service.setCompleted(REQUEST_ID, MEMBER_ID,
				WorkChecklistItemType.FRAGRANCE_BRIEF, false, 0))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.WORK_CHECKLIST_ITEM_CONFLICT);
	}

	@Test
	void setCompleted_toggles_completion_and_advances_revision() {
		when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request(REQUEST_ID)));
		when(accessGuard.isMember(PROJECT_ID, MEMBER_ID)).thenReturn(true);
		WorkChecklistItem item = WorkChecklistItem.create(REQUEST_ID, WorkChecklistItemType.SAFETY_REVIEW);
		when(checklistItemRepository.findByRequestIdAndItemType(REQUEST_ID, WorkChecklistItemType.SAFETY_REVIEW))
				.thenReturn(Optional.of(item));

		var response = service.setCompleted(REQUEST_ID, MEMBER_ID, WorkChecklistItemType.SAFETY_REVIEW, true, 0);

		assertThat(response.completed()).isTrue();
		assertThat(response.revision()).isEqualTo(1);
		assertThat(response.completedBy()).isEqualTo(MEMBER_ID);
	}

	@Test
	void assign_rejects_a_stale_expected_revision() {
		when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request(REQUEST_ID)));
		when(accessGuard.isMember(PROJECT_ID, MEMBER_ID)).thenReturn(true);
		when(accessGuard.isMember(PROJECT_ID, 2L)).thenReturn(true);
		WorkChecklistItem item = WorkChecklistItem.create(REQUEST_ID, WorkChecklistItemType.FRAGRANCE_BRIEF);
		item.setCompleted(true, 0, MEMBER_ID); // revision is now 1
		when(checklistItemRepository.findByRequestIdAndItemType(REQUEST_ID, WorkChecklistItemType.FRAGRANCE_BRIEF))
				.thenReturn(Optional.of(item));

		assertThatThrownBy(() -> service.assign(REQUEST_ID, MEMBER_ID,
				WorkChecklistItemType.FRAGRANCE_BRIEF, 2L, 0))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.WORK_CHECKLIST_ITEM_CONFLICT);
	}

	@Test
	void assign_rejects_an_assignee_outside_the_project() {
		when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request(REQUEST_ID)));
		when(accessGuard.isMember(PROJECT_ID, MEMBER_ID)).thenReturn(true);
		when(accessGuard.isMember(PROJECT_ID, 999L)).thenReturn(false);

		assertThatThrownBy(() -> service.assign(REQUEST_ID, MEMBER_ID,
				WorkChecklistItemType.FRAGRANCE_BRIEF, 999L, 0))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_MEMBER_NOT_FOUND);
		verify(checklistItemRepository, never()).findByRequestIdAndItemType(any(), any());
	}

	@Test
	void assign_sets_the_assignee_and_advances_the_shared_revision() {
		when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request(REQUEST_ID)));
		when(accessGuard.isMember(PROJECT_ID, MEMBER_ID)).thenReturn(true);
		when(accessGuard.isMember(PROJECT_ID, 2L)).thenReturn(true);
		WorkChecklistItem item = WorkChecklistItem.create(REQUEST_ID, WorkChecklistItemType.SAFETY_REVIEW);
		when(checklistItemRepository.findByRequestIdAndItemType(REQUEST_ID, WorkChecklistItemType.SAFETY_REVIEW))
				.thenReturn(Optional.of(item));

		var response = service.assign(REQUEST_ID, MEMBER_ID, WorkChecklistItemType.SAFETY_REVIEW, 2L, 0);

		assertThat(response.assignedTo()).isEqualTo(2L);
		assertThat(response.assignedBy()).isEqualTo(MEMBER_ID);
		assertThat(response.revision()).isEqualTo(1);
	}

	@Test
	void assign_with_no_assignee_clears_the_assignment() {
		when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request(REQUEST_ID)));
		when(accessGuard.isMember(PROJECT_ID, MEMBER_ID)).thenReturn(true);
		WorkChecklistItem item = WorkChecklistItem.create(REQUEST_ID, WorkChecklistItemType.SAFETY_REVIEW);
		item.assignTo(2L, 0, MEMBER_ID); // revision is now 1
		when(checklistItemRepository.findByRequestIdAndItemType(REQUEST_ID, WorkChecklistItemType.SAFETY_REVIEW))
				.thenReturn(Optional.of(item));

		var response = service.assign(REQUEST_ID, MEMBER_ID, WorkChecklistItemType.SAFETY_REVIEW, null, 1);

		assertThat(response.assignedTo()).isNull();
		assertThat(response.assignedBy()).isNull();
		assertThat(response.revision()).isEqualTo(2);
	}

	@Test
	void workProgress_reflects_completed_over_total() {
		when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request(REQUEST_ID)));
		when(accessGuard.isMember(PROJECT_ID, MEMBER_ID)).thenReturn(true);
		when(checklistItemRepository.findByRequestIdOrderByItemTypeAsc(REQUEST_ID))
				.thenReturn(items(REQUEST_ID, 3, 6));

		WorkProgressResponse response = service.workProgress(REQUEST_ID, MEMBER_ID);

		assertThat(response.completedCount()).isEqualTo(3);
		assertThat(response.totalCount()).isEqualTo(6);
		assertThat(response.percent()).isEqualTo(50.0);
	}

	@Test
	void workProgress_returns_exactly_100_when_every_item_is_complete() {
		when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request(REQUEST_ID)));
		when(accessGuard.isMember(PROJECT_ID, MEMBER_ID)).thenReturn(true);
		when(checklistItemRepository.findByRequestIdOrderByItemTypeAsc(REQUEST_ID))
				.thenReturn(items(REQUEST_ID, 6, 6));

		assertThat(service.workProgress(REQUEST_ID, MEMBER_ID).percent()).isEqualTo(100.0);
	}

	/** 반올림하면 100.0이 되는 미완료 상태(1999/2000 = 99.95%)는 99.9로 낮춘다. */
	@Test
	void workProgress_caps_at_99_9_instead_of_rounding_up_to_100_while_incomplete() {
		when(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request(REQUEST_ID)));
		when(accessGuard.isMember(PROJECT_ID, MEMBER_ID)).thenReturn(true);
		when(checklistItemRepository.findByRequestIdOrderByItemTypeAsc(REQUEST_ID))
				.thenReturn(items(REQUEST_ID, 1999, 2000));

		assertThat(service.workProgress(REQUEST_ID, MEMBER_ID).percent()).isEqualTo(99.9);
	}

	@Test
	void projectProgress_sums_items_across_works_instead_of_averaging_percentages() {
		FragranceRequest workA = withId(request(1L), 1L);
		FragranceRequest workB = withId(request(2L), 2L);
		when(accessGuard.requireMember(PROJECT_ID, MEMBER_ID)).thenReturn(null);
		when(requestRepository.findByProjectIdOrderByCreatedAtDesc(PROJECT_ID)).thenReturn(List.of(workA, workB));
		List<WorkChecklistItem> combined = new ArrayList<>();
		combined.addAll(items(1L, 1, 1)); // 작업 A: 1/1 = 100%
		combined.addAll(items(2L, 0, 9)); // 작업 B: 0/9 = 0%
		when(checklistItemRepository.findByRequestIdIn(List.of(1L, 2L))).thenReturn(combined);

		ProjectProgressResponse response = service.projectProgress(PROJECT_ID, MEMBER_ID);

		// 단순 평균이면 50%, 항목 수 가중 합산이면 1/10 = 10%
		assertThat(response.completedCount()).isEqualTo(1);
		assertThat(response.totalCount()).isEqualTo(10);
		assertThat(response.percent()).isEqualTo(10.0);
	}

	@Test
	void projectProgress_is_zero_when_the_project_has_no_works() {
		when(accessGuard.requireMember(PROJECT_ID, MEMBER_ID)).thenReturn(null);
		when(requestRepository.findByProjectIdOrderByCreatedAtDesc(PROJECT_ID)).thenReturn(List.of());

		ProjectProgressResponse response = service.projectProgress(PROJECT_ID, MEMBER_ID);

		assertThat(response.percent()).isEqualTo(0.0);
		assertThat(response.workCount()).isEqualTo(0);
	}
}
