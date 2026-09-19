package com.perfumeryaicore.domain.ops.dto.response;

import java.time.LocalDateTime;

/**
 * 서비스 운영 상태 - 최근 시스템 이벤트 한 줄(시각 / 이벤트 / 상태). 작업 이력과 팀원 변경 이력을
 * 한 목록으로 합친 것이다.
 *
 * @param category   {@code JOB}(비동기 작업) 또는 {@code MEMBER}(팀원 변경)
 * @param event      화면에 그대로 보일 이벤트 이름(예: "후보 조향식 생성")
 * @param status     상태 코드 - 작업은 {@code PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED}, 팀원 변경은 {@code RECORDED}
 * @param statusLabel 상태의 한글 표시(예: "완료")
 * @param detail     실패 사유·역할 변경 내용 같은 부가 설명. 없으면 {@code null}
 * @param referenceId 작업이면 작업 ID, 팀원 변경이면 변경된 멤버 ID
 */
public record OpsEventResponse(
		LocalDateTime occurredAt,
		String category,
		String event,
		String status,
		String statusLabel,
		String detail,
		Long projectId,
		String projectName,
		Long referenceId
) {
}
