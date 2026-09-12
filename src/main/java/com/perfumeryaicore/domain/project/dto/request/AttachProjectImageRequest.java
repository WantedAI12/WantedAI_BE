package com.perfumeryaicore.domain.project.dto.request;

import jakarta.validation.constraints.NotNull;

/** {@code POST /projects/images}로 임시 업로드한 자산을 프로젝트에 연결(최초 연결·교체 겸용)한다. */
public record AttachProjectImageRequest(

		@NotNull
		Long assetId
) {
}
