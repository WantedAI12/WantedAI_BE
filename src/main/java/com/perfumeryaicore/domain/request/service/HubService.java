package com.perfumeryaicore.domain.request.service;

import com.perfumeryaicore.domain.project.dto.response.ProjectResponse;
import com.perfumeryaicore.domain.project.service.ProjectService;
import com.perfumeryaicore.domain.request.dto.response.HubChecklistItemResponse;
import com.perfumeryaicore.domain.request.dto.response.HubSummaryResponse;
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.entity.WorkChecklistItem;
import com.perfumeryaicore.domain.request.repository.FragranceRequestRepository;
import com.perfumeryaicore.domain.request.repository.WorkChecklistItemRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 후 허브 요약(BE-090~098 일부). 유저플로우의 "내 체크리스트 목록"·"마감 임박 항목"·
 * "허브 요약 데이터"에 해당한다 - "최근 활동 피드"는 여러 도메인에 걸친 이벤트 집계가 필요해
 * 범위가 커서 다루지 않는다.
 *
 * <p>"내 체크리스트 목록"은 실제로 나에게 배정된(assignedTo) 미완료 항목이다. 배정되지 않은
 * 항목은 아직 담당자가 없는 상태이므로 여기 포함하지 않는다(BE-109 이전에는 담당자 배정이
 * 없어 "내가 속한 프로젝트의 모든 미완료 항목"으로 넓게 해석했었다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HubService {

	/** "마감 임박"의 기준 일수. 실제 기준이 정해지면 조정한다(임의값). */
	private static final int DUE_SOON_WITHIN_DAYS = 7;

	private final ProjectService projectService;
	private final FragranceRequestRepository requestRepository;
	private final WorkChecklistItemRepository checklistItemRepository;

	public HubSummaryResponse summary(Long memberId) {
		List<ProjectResponse> projects = projectService.listMine(memberId);

		LocalDate dueSoonCutoff = LocalDate.now().plusDays(DUE_SOON_WITHIN_DAYS);
		List<ProjectResponse> dueSoonProjects = projects.stream()
				.filter(p -> p.dueDate() != null && !p.dueDate().isAfter(dueSoonCutoff))
				.toList();

		List<Long> projectIds = projects.stream().map(ProjectResponse::projectId).toList();
		List<HubChecklistItemResponse> pendingChecklistItems = projectIds.isEmpty()
				? List.of()
				: myPendingChecklistItems(projectIds, memberId);

		return new HubSummaryResponse(projects, dueSoonProjects, pendingChecklistItems);
	}

	private List<HubChecklistItemResponse> myPendingChecklistItems(List<Long> projectIds, Long memberId) {
		List<FragranceRequest> works = requestRepository.findByProjectIdIn(projectIds);
		if (works.isEmpty()) {
			return List.of();
		}
		Map<Long, Long> projectIdByRequestId = works.stream()
				.collect(Collectors.toMap(FragranceRequest::getId, FragranceRequest::getProjectId));

		List<Long> requestIds = works.stream().map(FragranceRequest::getId).toList();
		return checklistItemRepository.findByRequestIdIn(requestIds).stream()
				.filter(item -> !item.isCompleted() && memberId.equals(item.getAssignedTo()))
				.map(item -> new HubChecklistItemResponse(
						projectIdByRequestId.get(item.getRequestId()), item.getRequestId(), item.getItemType()))
				.toList();
	}
}
