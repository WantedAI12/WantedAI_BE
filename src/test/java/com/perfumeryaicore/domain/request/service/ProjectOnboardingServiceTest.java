package com.perfumeryaicore.domain.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.project.dto.request.CreateProjectRequest;
import com.perfumeryaicore.domain.project.dto.response.ProjectResponse;
import com.perfumeryaicore.domain.project.service.ProjectService;
import com.perfumeryaicore.domain.request.dto.request.CreateFragranceRequestRequest;
import com.perfumeryaicore.domain.request.dto.request.CreateProjectWithFirstRequestRequest;
import com.perfumeryaicore.domain.request.dto.response.FragranceRequestResponse;
import com.perfumeryaicore.domain.request.dto.response.ProjectOnboardingResponse;
import com.perfumeryaicore.domain.request.entity.RequestStatus;
import com.perfumeryaicore.global.common.ProjectRole;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** BE-091~093: 프로젝트+첫 요청 생성을 한 트랜잭션으로 묶는 온보딩 서비스. */
class ProjectOnboardingServiceTest {

	private static final long MEMBER_ID = 1L;
	private static final long PROJECT_ID = 10L;

	private final ProjectService projectService = mock(ProjectService.class);
	private final FragranceRequestService requestService = mock(FragranceRequestService.class);
	private final ProjectOnboardingService service = new ProjectOnboardingService(projectService, requestService);

	private CreateProjectWithFirstRequestRequest dto() {
		CreateProjectRequest projectDto = new CreateProjectRequest("신제품 A", "설명", null, null);
		CreateFragranceRequestRequest requestDto = new CreateFragranceRequestRequest(
				"시트러스 우디 향", null, null, null, null, null, null, null, null, List.of());
		return new CreateProjectWithFirstRequestRequest(projectDto, requestDto);
	}

	@Test
	void createProjectWithFirstRequest_creates_the_project_then_the_first_request_as_its_creator() {
		ProjectResponse projectResponse = new ProjectResponse(
				PROJECT_ID, "신제품 A", "설명", ProjectRole.ORG_ADMIN, 1, null, null, null, LocalDateTime.now());
		when(projectService.create(eq(MEMBER_ID), eq(dto().project()))).thenReturn(projectResponse);

		FragranceRequestResponse requestResponse = new FragranceRequestResponse(
				100L, RequestStatus.MISSING_FIELDS, null, List.of("productCategory"), LocalDateTime.now(), null);
		when(requestService.createAsProjectCreator(eq(PROJECT_ID), eq(MEMBER_ID), eq(dto().firstRequest())))
				.thenReturn(requestResponse);

		ProjectOnboardingResponse response = service.createProjectWithFirstRequest(MEMBER_ID, dto());

		assertThat(response.project().projectId()).isEqualTo(PROJECT_ID);
		assertThat(response.firstRequest().requestId()).isEqualTo(100L);
		verify(requestService).createAsProjectCreator(PROJECT_ID, MEMBER_ID, dto().firstRequest());
	}
}
