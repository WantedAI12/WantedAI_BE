package com.perfumeryaicore.domain.evidence.dto.response;

import java.time.LocalDateTime;

/**
 * 후보 감사 이력 한 건. 별도 로그 테이블에 쓰지 않고 formula(버전 생성)·safety(승인 게이트)·
 * experiment(상태 변경) 각자의 기존 기록을 조회 시점에 모아 시간순으로 구성한다 — 문서 델타 반영 대상
 * (ERD의 evidence_logs 테이블은 미사용).
 */
public record EvidenceEvent(
		String action,
		Long candidateVersionId,
		Long actorId,
		LocalDateTime occurredAt,
		String detail
) {
}
