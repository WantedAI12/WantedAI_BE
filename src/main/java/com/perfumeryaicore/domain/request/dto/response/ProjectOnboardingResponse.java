package com.perfumeryaicore.domain.request.dto.response;

import com.perfumeryaicore.domain.project.dto.response.ProjectResponse;

/** BE-091~093: 프로젝트 생성과 첫 요청 생성을 한 번에 처리한 결과. */
public record ProjectOnboardingResponse(
		ProjectResponse project,
		FragranceRequestResponse firstRequest
) {
}
