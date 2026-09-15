package com.perfumeryaicore.global.client.dto;

/**
 * Modal {@code POST /v2/briefs/revise} 요청 본문. 저장된 후보 스냅샷과 자연어 지시로 수정된
 * 입력/검토 결과를 만든다 - 새 후보를 저장·승인하지 않는다(README 6번 참고).
 */
public record ReviseCandidateRequest(
		StoredCandidate source,
		String instruction
) {
}
