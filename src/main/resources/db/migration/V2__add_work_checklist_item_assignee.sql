-- BE-109: 체크리스트 항목 담당자 배정 (WorkChecklistItem.assignedTo/assignedBy/assignedAt)
-- 모두 nullable - 기존 항목은 미배정 상태로 남는다.
alter table work_checklist_items
    add column assigned_to bigint,
    add column assigned_by bigint,
    add column assigned_at datetime(6);
