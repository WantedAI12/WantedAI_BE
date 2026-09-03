package com.perfumeryaicore.domain.job.entity;

/**
 * 비동기 작업 종류. 각 값은 외부 AI 서비스 호출이 필요한 액션 하나에 대응한다.
 */
public enum JobType {

	/** 자연어 향 요청 구조화 */
	REQUEST_STRUCTURING,
	/** 후보 조향식 생성 (Modal {@code POST /v1/formulas}) */
	CANDIDATE_GENERATION,
	/** 원료 카탈로그 동기화 (Modal {@code GET /v1/catalog}) */
	CATALOG_SYNC,
	/** 증거 보고서 PDF 생성 */
	EVIDENCE_REPORT,
	/** 공급 조건 변경 영향 분석 */
	SUPPLY_IMPACT_ANALYSIS,
	/** 성능 프록시 예측 재계산 */
	PREDICTION
}
