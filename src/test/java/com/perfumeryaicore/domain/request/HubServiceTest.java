package com.perfumeryaicore.domain.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.project.dto.response.ProjectResponse;
import com.perfumeryaicore.domain.project.service.ProjectService;
import com.perfumeryaicore.domain.request.dto.response.HubSummaryResponse;
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.entity.WorkChecklistItem;
import com.perfumeryaicore.domain.request.entity.WorkChecklistItemType;
import com.perfumeryaicore.domain.request.repository.FragranceRequestRepository;
import com.perfumeryaicore.domain.request.repository.WorkChecklistItemRepository;
import com.perfumeryaicore.domain.request.service.HubService;
import com.perfumeryaicore.global.common.ProjectRole;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HubServiceTest {

	private static final long MEMBER_ID = 1L;

	private final ProjectService projectService = mock(ProjectService.class);
	private final FragranceRequestRepository requestRepository = mock(FragranceRequestRepository.class);
	private final WorkChecklistItemRepository checklistItemRepository = mock(WorkChecklistItemRepository.class);
	private final HubService service = new HubService(projectService, requestRepository, checklistItemRepository);

	private static ProjectResponse project(long id, LocalDate dueDate) {
		return new ProjectResponse(id, "프로젝트" + id, null, ProjectRole.ORG_ADMIN, 1, null, dueDate, null, null);
	}

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

	private static FragranceRequest work(long id, long projectId) {
		return withId(FragranceRequest.create(projectId, MEMBER_ID, "brief"), id);
	}

	@Test
	void summary_returns_every_project_the_member_belongs_to() {
		when(projectService.listMine(MEMBER_ID)).thenReturn(List.of(project(1L, null), project(2L, null)));
		when(requestRepository.findByProjectIdIn(List.of(1L, 2L))).thenReturn(List.of());

		HubSummaryResponse response = service.summary(MEMBER_ID);

		assertThat(response.projects()).hasSize(2);
	}

	@Test
	void summary_marks_a_project_due_within_seven_days_as_due_soon() {
		LocalDate soon = LocalDate.now().plusDays(3);
		LocalDate far = LocalDate.now().plusDays(30);
		when(projectService.listMine(MEMBER_ID)).thenReturn(List.of(project(1L, soon), project(2L, far)));
		when(requestRepository.findByProjectIdIn(List.of(1L, 2L))).thenReturn(List.of());

		HubSummaryResponse response = service.summary(MEMBER_ID);

		assertThat(response.dueSoonProjects()).extracting(ProjectResponse::projectId).containsExactly(1L);
	}

	@Test
	void summary_treats_an_overdue_project_as_due_soon_too() {
		LocalDate overdue = LocalDate.now().minusDays(2);
		when(projectService.listMine(MEMBER_ID)).thenReturn(List.of(project(1L, overdue)));
		when(requestRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());

		assertThat(service.summary(MEMBER_ID).dueSoonProjects()).hasSize(1);
	}

	@Test
	void summary_excludes_a_project_with_no_due_date_from_due_soon() {
		when(projectService.listMine(MEMBER_ID)).thenReturn(List.of(project(1L, null)));
		when(requestRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of());

		assertThat(service.summary(MEMBER_ID).dueSoonProjects()).isEmpty();
	}

	/** BE-109: '내 체크리스트'는 실제로 나에게 배정되고 아직 완료되지 않은 항목만 모은다. */
	@Test
	void summary_collects_only_my_incomplete_assigned_checklist_items_across_all_my_projects() {
		when(projectService.listMine(MEMBER_ID)).thenReturn(List.of(project(1L, null), project(2L, null)));
		FragranceRequest workA = work(100L, 1L);
		FragranceRequest workB = work(200L, 2L);
		when(requestRepository.findByProjectIdIn(List.of(1L, 2L))).thenReturn(List.of(workA, workB));

		WorkChecklistItem assignedToMe = WorkChecklistItem.create(100L, WorkChecklistItemType.FRAGRANCE_BRIEF);
		assignedToMe.assignTo(MEMBER_ID, 0, MEMBER_ID);
		WorkChecklistItem done = WorkChecklistItem.create(200L, WorkChecklistItemType.SAFETY_REVIEW);
		done.assignTo(MEMBER_ID, 0, MEMBER_ID);
		done.setCompleted(true, 1, MEMBER_ID);
		WorkChecklistItem assignedToSomeoneElse = WorkChecklistItem.create(200L, WorkChecklistItemType.TESTING);
		assignedToSomeoneElse.assignTo(999L, 0, MEMBER_ID);
		WorkChecklistItem unassigned = WorkChecklistItem.create(100L, WorkChecklistItemType.SENSORY_EVALUATION);
		when(checklistItemRepository.findByRequestIdIn(List.of(100L, 200L)))
				.thenReturn(List.of(assignedToMe, done, assignedToSomeoneElse, unassigned));

		var items = service.summary(MEMBER_ID).pendingChecklistItems();

		assertThat(items).hasSize(1);
		assertThat(items.get(0).requestId()).isEqualTo(100L);
		assertThat(items.get(0).projectId()).isEqualTo(1L);
		assertThat(items.get(0).itemType()).isEqualTo(WorkChecklistItemType.FRAGRANCE_BRIEF);
	}

	@Test
	void summary_skips_checklist_lookup_entirely_when_the_member_has_no_projects() {
		when(projectService.listMine(MEMBER_ID)).thenReturn(List.of());

		HubSummaryResponse response = service.summary(MEMBER_ID);

		assertThat(response.pendingChecklistItems()).isEmpty();
		verify(requestRepository, never()).findByProjectIdIn(org.mockito.ArgumentMatchers.any());
	}
}
