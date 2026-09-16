-- 게스트 모드(로그인 없이 체험): 임시 계정 표시와 만료 시각.
alter table members
    add column is_guest bit not null default 0,
    add column guest_expires_at datetime(6);

-- 만료 정리 배치(findByGuestTrueAndGuestExpiresAtBefore)용 조회 인덱스.
create index idx_members_guest_expires_at
    on members (is_guest, guest_expires_at);
