package com.perfumeryaicore.domain.ops.dto.response;

/**
 * 서비스 운영 상태 - 전체 현황. 호출한 회원이 속한 프로젝트만 집계한다(전체 관리자 개념이 없어
 * 서비스 전체를 합산하면 다른 사용자의 기록이 노출된다). 속한 프로젝트가 없거나 아직 생성 이력이
 * 없으면 개수는 0, 평균 시간·비율은 {@code null}이다.
 */
public record OpsOverviewResponse(
		int projectCount,
		Queue queue,
		GenerationTime generationTime,
		Abstention abstention
) {

	/** 후보 생성 작업 큐 - {@code waiting}은 아직 시작 전, {@code running}은 AI 호출 중. */
	public record Queue(long waiting, long running) {
	}

	/**
	 * 후보 생성 평균 소요 시간(초) - 최근 완료된 작업 최대 {@code sampleSize}건의 AI 호출 시작부터
	 * 완료까지. 큐에서 기다린 시간은 포함하지 않는다. 표본이 없으면 {@code averageSeconds}가 {@code null}.
	 */
	public record GenerationTime(Double averageSeconds, int sampleSize) {
	}

	/**
	 * 기권 현황 - 안전 조건을 만족하는 조향식을 찾지 못해 후보를 만들지 않은 횟수. {@code ratePercent}는
	 * 기권 / (성공한 후보 생성 + 기권) x 100이며 끝난 생성이 없으면 {@code null}.
	 */
	public record Abstention(long count, Double ratePercent) {
	}
}
