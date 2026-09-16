package com.perfumeryaicore.domain.formula.service;

/**
 * 다른 도메인(safety, prediction)이 후보의 현재 버전 원문을 읽을 때 쓰는 내부 전용 뷰.
 * REST로 직접 노출되지 않는다 — {@code rawResponse}는 조향 AI 응답 원문이며, 이걸 필요한
 * 만큼만 각 도메인이 알아서 파싱한다(예: safety는 {@code safety} 구간, prediction은 유사도·
 * 적용범위 필드).
 */
public record CandidateVersionRawView(Long candidateId, Long versionId, String rawResponse) {
}
