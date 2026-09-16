package com.perfumeryaicore.domain.request.service;

import com.perfumeryaicore.domain.project.dto.response.ProjectResponse;
import com.perfumeryaicore.domain.project.service.ProjectService;
import com.perfumeryaicore.domain.request.dto.request.CreateProjectWithFirstRequestRequest;
import com.perfumeryaicore.domain.request.dto.response.FragranceRequestResponse;
import com.perfumeryaicore.domain.request.dto.response.ProjectOnboardingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BE-091~093: 프로젝트 생성과 첫 요청(체크리스트 자동 초기화 포함) 생성을 한 트랜잭션으로 묶는다.
 * 새 프로젝트는 생성자 외에 멤버가 없어 일반 요청 생성의 쓰기 역할 검사를 통과할 수 없으므로,
 * {@link FragranceRequestService#createAsProjectCreator}로 그 검사를 건너뛴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectOnboardingService {

	private final ProjectService projectService;
	private final FragranceRequestService requestService;

	@Transactional
	public ProjectOnboardingResponse createProjectWithFirstRequest(
			Long memberId, CreateProjectWithFirstRequestRequest dto) {
		ProjectResponse project = projectService.create(memberId, dto.project());
		FragranceRequestResponse firstRequest = requestService.createAsProjectCreator(
				project.projectId(), memberId, dto.firstRequest());
		log.info("[PROJECT-ONBOARDING] project={} firstRequest={} by={}",
				project.projectId(), firstRequest.requestId(), memberId);
		return new ProjectOnboardingResponse(project, firstRequest);
	}
}
