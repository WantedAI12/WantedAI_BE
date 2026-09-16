package com.perfumeryaicore.domain.request.dto.request;

import com.perfumeryaicore.domain.project.dto.request.CreateProjectRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** BE-091~093: 프로젝트와 첫 번째 요청(체크리스트 자동 생성 포함)을 한 트랜잭션으로 생성한다. */
public record CreateProjectWithFirstRequestRequest(

		@NotNull
		@Valid
		CreateProjectRequest project,

		@NotNull
		@Valid
		CreateFragranceRequestRequest firstRequest
) {
}
