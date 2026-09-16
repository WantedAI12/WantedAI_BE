package com.perfumeryaicore.domain.experiment.dto.response;

import com.perfumeryaicore.global.common.CandidateStatus;

/**
 * 후보 비교표 한 행.
 *
 * @param goalMatchScore    목표 향 프로필과의 계산 유사도(0~100). 조향 AI {@code similarity_score}를
 *                          그대로 옮긴 것 — 실측 인간 후각 일치도가 아니다.
 * @param cost               현재 버전의 예상 농축액 비용(1kg 기준). 재보정 안 함
 * @param supplyStability    현재 버전 원료들의 평균 가용성(0~100). 조향 AI가 원료별로 준
 *                          {@code availability} 값을 평균한 것 — 별도 공급망 데이터가 아니다
 * @param modelApplicabilityPercent 예측 모델 적용범위(0~100)
 */
public record CandidateCompareRow(
		Long candidateId,
		CandidateStatus status,
		Double goalMatchScore,
		Double cost,
		Double supplyStability,
		Double modelApplicabilityPercent
) {
}
