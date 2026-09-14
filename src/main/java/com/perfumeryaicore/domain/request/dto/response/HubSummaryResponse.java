package com.perfumeryaicore.domain.request.dto.response;

import com.perfumeryaicore.domain.project.dto.response.ProjectResponse;
import java.util.List;

/**
 * 로그인 후 허브 화면 요약(BE-090~098 일부). "최근 활동 피드"는 다루지 않는다 - 여러 도메인에
 * 걸친 이벤트 집계가 필요해 범위가 커서 별도 설계가 필요하다.
 */
public record HubSummaryResponse(
		List<ProjectResponse> projects,
		List<ProjectResponse> dueSoonProjects,
		List<HubChecklistItemResponse> pendingChecklistItems
) {
}
