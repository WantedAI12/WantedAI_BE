package com.perfumeryaicore.domain.formula.dto.response;

import com.perfumeryaicore.domain.formula.entity.CandidateMemo;
import com.perfumeryaicore.domain.formula.entity.CandidateMemoType;
import java.time.LocalDateTime;

/**
 * 후보 메모 한 건. 아직 저장된 적 없는 타입은 {@link #empty}로 {@code revision 0}짜리 빈 응답을 낸다 —
 * 클라이언트는 최초 저장 시 그 0을 그대로 {@code expectedRevision}에 실어 보내면 된다.
 */
public record CandidateMemoResponse(
		CandidateMemoType memoType,
		String content,
		int revision,
		Long authorId,
		Long lastEditedBy,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {

	public static CandidateMemoResponse from(CandidateMemo memo) {
		return new CandidateMemoResponse(
				memo.getMemoType(),
				memo.getContent(),
				memo.getRevision(),
				memo.getCreatedBy(),
				memo.getLastEditedBy(),
				memo.getCreatedAt(),
				memo.getUpdatedAt());
	}

	public static CandidateMemoResponse empty(CandidateMemoType type) {
		return new CandidateMemoResponse(type, null, 0, null, null, null, null);
	}
}
