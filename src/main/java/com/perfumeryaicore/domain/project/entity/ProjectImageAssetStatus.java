package com.perfumeryaicore.domain.project.entity;

/**
 * 프로젝트 이미지 자산 상태.
 *
 * <pre>
 *   PENDING(업로드만 됨) ──▶ ATTACHED(프로젝트에 연결됨) ──▶ ORPHANED(교체·해제로 연결 끊김)
 * </pre>
 *
 * <p>PENDING 상태로 남아 끝내 연결되지 않는 자산(업로드는 했지만 프로젝트 생성/연결을 포기한 경우)의
 * 정리는 이번 범위 밖이다 — 후속으로 배치 작업이 필요하다.
 */
public enum ProjectImageAssetStatus {
	PENDING,
	ATTACHED,
	ORPHANED
}
