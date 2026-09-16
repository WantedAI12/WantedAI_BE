-- BE-109: 체크리스트 항목 담당자 배정 (WorkChecklistItem.assignedTo/assignedBy/assignedAt)
-- 모두 nullable - 기존 항목은 미배정 상태로 남는다.
-- 한 문장에 여러 add column을 묶는 건 MySQL 전용 문법이다. 테스트는 H2(MODE=MySQL)로 같은
-- 마이그레이션을 돌리고 H2는 그 형태를 파싱하지 못해서, 문장을 컬럼별로 나눠 둔다.
alter table work_checklist_items add column assigned_to bigint;
alter table work_checklist_items add column assigned_by bigint;
alter table work_checklist_items add column assigned_at datetime(6);
