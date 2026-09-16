-- BE-063~066: 원료 마스터 대량 등록 실패 행 보관(IngredientImportFailure).
-- #125가 Flyway 도입(BE-083)보다 먼저 develop에 머지돼 V1에 빠져 있던 테이블이다.
create table ingredient_import_failures (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    imported_by bigint not null,
    resolved_at datetime(6),
    updated_at datetime(6) not null,
    external_id varchar(100),
    error_message varchar(1000) not null,
    payload_json longtext not null,
    primary key (id)
) engine=InnoDB;

-- 미해결 행을 오래된 순으로 훑는 재처리 큐 조회(findByResolvedAtIsNullOrderByCreatedAtAsc) 전용.
create index idx_ingredient_import_failures_resolved_at
    on ingredient_import_failures (resolved_at, created_at);
