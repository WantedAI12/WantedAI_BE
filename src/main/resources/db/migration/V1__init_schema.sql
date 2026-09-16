-- BE-083: 초기 스키마 (Flyway 도입 시점의 JPA 엔티티 기준, Hibernate 7 MySQLDialect로 생성)
-- 이후 스키마 변경은 이 파일을 고치지 말고 V2__*.sql 부터 새 버전으로 추가한다.


create table approval_gates (
    candidate_id bigint not null,
    candidate_version_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    reviewed_by bigint not null,
    updated_at datetime(6) not null,
    comment longtext,
    decision enum ('APPROVED','REJECTED') not null,
    primary key (id)
) engine=InnoDB;

create table candidate_memos (
    revision integer not null,
    candidate_id bigint not null,
    candidate_version_id bigint,
    created_at datetime(6) not null,
    created_by bigint not null,
    id bigint not null auto_increment,
    last_edited_by bigint not null,
    updated_at datetime(6) not null,
    content longtext not null,
    memo_type enum ('INPUT_NOTE','NEXT_EXPERIMENT_NOTE','REVIEW_NOTE') not null,
    primary key (id)
) engine=InnoDB;

create table candidate_version_ingredients (
    availability float(53),
    concentrate_percent float(53),
    finished_product_percent float(53),
    price_per_kg float(53),
    candidate_version_id bigint not null,
    id bigint not null auto_increment,
    pyramid varchar(20),
    ingredient_external_id varchar(100),
    ingredient_name varchar(200) not null,
    primary key (id)
) engine=InnoDB;

create table candidate_versions (
    ai_gpu_used bit,
    cost float(53),
    ai_latency_ms bigint,
    candidate_id bigint not null,
    created_at datetime(6) not null,
    created_by bigint not null,
    id bigint not null auto_increment,
    parent_version_id bigint,
    restored_from_version_id bigint,
    updated_at datetime(6) not null,
    ai_provider varchar(30),
    ai_response_status varchar(40),
    generation_rationale longtext,
    raw_response longtext,
    primary key (id)
) engine=InnoDB;

create table candidates (
    created_at datetime(6) not null,
    created_by bigint not null,
    current_version_id bigint,
    derived_from_candidate_id bigint,
    derived_from_version_id bigint,
    id bigint not null auto_increment,
    job_id bigint,
    project_id bigint not null,
    request_id bigint not null,
    updated_at datetime(6) not null,
    version bigint not null,
    derivation_reason longtext,
    status enum ('APPROVED','CONFIRMED_FOR_EXPERIMENT','IN_SENSORY_TEST','REJECTED','UNDER_REVIEW') not null,
    primary key (id)
) engine=InnoDB;

create table catalog_sync_runs (
    prototype_active_count integer,
    reference_molecule_count integer,
    safety_screened_count integer,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    job_id bigint not null,
    project_id bigint not null,
    requested_by bigint not null,
    synced_at datetime(6) not null,
    updated_at datetime(6) not null,
    catalog_version varchar(60),
    registry_sha256 varchar(80),
    raw_snapshot longtext,
    status enum ('CANCELLED','FAILED','PENDING','RUNNING','SUCCEEDED') not null,
    primary key (id)
) engine=InnoDB;

create table evidence_reports (
    candidate_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    job_id bigint,
    requested_by bigint not null,
    updated_at datetime(6) not null,
    pdf_object_key varchar(255),
    report_data longtext,
    status enum ('CANCELLED','FAILED','PENDING','RUNNING','SUCCEEDED') not null,
    primary key (id)
) engine=InnoDB;

create table experiment_status_logs (
    candidate_id bigint not null,
    candidate_version_id bigint,
    changed_by bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    previous_status enum ('APPROVED','CONFIRMED_FOR_EXPERIMENT','IN_SENSORY_TEST','REJECTED','UNDER_REVIEW'),
    reason longtext,
    status enum ('APPROVED','CONFIRMED_FOR_EXPERIMENT','IN_SENSORY_TEST','REJECTED','UNDER_REVIEW') not null,
    primary key (id)
) engine=InnoDB;

create table fragrance_requests (
    max_ingredient_count integer,
    max_ingredient_price_per_kg float(53),
    risk_tier integer,
    usage_concentration_percent float(53),
    created_at datetime(6) not null,
    created_by bigint not null,
    id bigint not null auto_increment,
    project_id bigint not null,
    updated_at datetime(6) not null,
    accords_csv varchar(500),
    intensity enum ('LIGHT','MODERATE','STRONG'),
    longevity enum ('HIGH','LOW','MEDIUM'),
    product_category enum ('BODY_LOTION','BODY_WASH','CANDLE','DIFFUSER','EAU_DE_COLOGNE','EAU_DE_PARFUM','EAU_DE_TOILETTE','ROOM_SPRAY','SHAMPOO'),
    raw_text longtext not null,
    status enum ('BLOCKED','CONFIRMED','DRAFT','MISSING_FIELDS') not null,
    target_region enum ('EU','KR','US'),
    primary key (id)
) engine=InnoDB;

create table generation_rejections (
    created_at datetime(6) not null,
    created_by bigint not null,
    id bigint not null auto_increment,
    job_id bigint not null,
    project_id bigint not null,
    request_id bigint not null,
    updated_at datetime(6) not null,
    reason_code varchar(40),
    message longtext,
    raw_response longtext,
    primary key (id)
) engine=InnoDB;

create table ingredient_masters (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    registered_by bigint not null,
    updated_at datetime(6) not null,
    cas_number varchar(20),
    external_id varchar(100) not null,
    name varchar(200) not null,
    supplier_name varchar(200),
    synonyms_csv varchar(1000),
    regulatory_notes longtext,
    safety_notes longtext,
    primary key (id)
) engine=InnoDB;

create table jobs (
    attempt integer not null,
    retryable bit not null,
    ai_call_queued_at datetime(6),
    ai_call_started_at datetime(6),
    created_at datetime(6) not null,
    created_by bigint not null,
    id bigint not null auto_increment,
    project_id bigint not null,
    result_ref_id bigint,
    updated_at datetime(6) not null,
    version bigint not null,
    idempotency_key varchar(200),
    failure_reason varchar(500),
    input_payload longtext,
    job_type enum ('CANDIDATE_GENERATION','CATALOG_SYNC','EVIDENCE_REPORT','PREDICTION','REQUEST_STRUCTURING','SUPPLY_IMPACT_ANALYSIS') not null,
    status enum ('CANCELLED','FAILED','PENDING','RUNNING','SUCCEEDED') not null,
    primary key (id)
) engine=InnoDB;

create table members (
    failed_login_attempts integer not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    locked_until datetime(6),
    updated_at datetime(6) not null,
    name varchar(50) not null,
    email varchar(255) not null,
    password_hash varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table password_reset_tokens (
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    member_id bigint not null,
    revoked_at datetime(6),
    updated_at datetime(6) not null,
    token_hash varchar(64) not null,
    primary key (id)
) engine=InnoDB;

create table project_image_assets (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    project_id bigint,
    size_bytes bigint not null,
    updated_at datetime(6) not null,
    uploaded_by bigint not null,
    content_type varchar(100) not null,
    object_key varchar(500) not null,
    status enum ('ATTACHED','ORPHANED','PENDING') not null,
    primary key (id)
) engine=InnoDB;

create table project_member_audit_logs (
    actor_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    project_id bigint not null,
    target_member_id bigint not null,
    updated_at datetime(6) not null,
    action enum ('ADDED','REMOVED','ROLE_CHANGED') not null,
    new_role enum ('AUDITOR','FRAGRANCE_RND','ORG_ADMIN','PERFUMER','PRODUCT_BRAND','PROJECT_MANAGER','SAFETY_REVIEWER','SENSORY_SCIENTIST','SUPPLIER'),
    previous_role enum ('AUDITOR','FRAGRANCE_RND','ORG_ADMIN','PERFUMER','PRODUCT_BRAND','PROJECT_MANAGER','SAFETY_REVIEWER','SENSORY_SCIENTIST','SUPPLIER'),
    primary key (id)
) engine=InnoDB;

create table project_members (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    member_id bigint not null,
    project_id bigint not null,
    updated_at datetime(6) not null,
    role enum ('AUDITOR','FRAGRANCE_RND','ORG_ADMIN','PERFUMER','PRODUCT_BRAND','PROJECT_MANAGER','SAFETY_REVIEWER','SENSORY_SCIENTIST','SUPPLIER') not null,
    primary key (id)
) engine=InnoDB;

create table projects (
    due_date date,
    start_date date,
    assignee_member_id bigint,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    image_asset_id bigint,
    updated_at datetime(6) not null,
    name varchar(100) not null,
    description varchar(500),
    primary key (id)
) engine=InnoDB;

create table refresh_tokens (
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    member_id bigint not null,
    revoked_at datetime(6),
    updated_at datetime(6) not null,
    token_hash varchar(64) not null,
    primary key (id)
) engine=InnoDB;

create table sensory_test_results (
    correlation_with_prediction float(53),
    scale_max float(53),
    scale_min float(53),
    timepoint_minutes integer,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    recorded_by bigint not null,
    sensory_test_id bigint not null,
    supersedes_result_id bigint,
    updated_at datetime(6) not null,
    panelist_identifier varchar(50),
    missing_reason longtext,
    result_data longtext,
    primary key (id)
) engine=InnoDB;

create table sensory_tests (
    panel_size integer,
    predicted_similarity_score_at_plan float(53),
    published bit not null,
    candidate_id bigint not null,
    candidate_version_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    planned_by bigint not null,
    published_at datetime(6),
    published_by bigint,
    updated_at datetime(6) not null,
    batch_lot varchar(50),
    protocol_version varchar(50),
    sample_code varchar(50),
    blind_level enum ('DOUBLE_BLIND','SINGLE_BLIND'),
    plan_detail longtext,
    status enum ('COMPLETED','PLANNED') not null,
    primary key (id)
) engine=InnoDB;

create table supply_change_affected_candidates (
    ingredient_concentrate_percent float(53),
    candidate_id bigint not null,
    candidate_version_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    supply_change_id bigint not null,
    updated_at datetime(6) not null,
    review_status enum ('PENDING_REVIEW','REVIEWED') not null,
    primary key (id)
) engine=InnoDB;

create table supply_changes (
    affected_candidate_count integer not null,
    new_price_per_kg float(53),
    previous_price_per_kg float(53),
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    project_id bigint not null,
    reported_by bigint not null,
    updated_at datetime(6) not null,
    changed_field varchar(100),
    ingredient_external_id varchar(100) not null,
    change_source_id varchar(200),
    analysis_status enum ('CANCELLED','FAILED','PENDING','RUNNING','SUCCEEDED') not null,
    change_type enum ('DISCONTINUED','IDENTITY_CHANGE','LEAD_TIME_INCREASE','OTHER','PRICE_DECREASE','PRICE_INCREASE','SAFETY_REGULATORY_CHANGE','SUPPLY_RESTORED','SUPPLY_TERMS_CHANGE') not null,
    new_value longtext,
    note longtext,
    previous_value longtext,
    primary key (id)
) engine=InnoDB;

create table supply_review_decisions (
    candidate_id bigint not null,
    created_at datetime(6) not null,
    decided_by bigint not null,
    id bigint not null auto_increment,
    supply_change_id bigint,
    updated_at datetime(6) not null,
    decision enum ('DISCARD_CANDIDATE','KEEP_FORMULA','REVISE_FORMULA') not null,
    rationale longtext not null,
    primary key (id)
) engine=InnoDB;

create table work_checklist_items (
    completed bit not null,
    revision integer not null,
    completed_at datetime(6),
    completed_by bigint,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    request_id bigint not null,
    updated_at datetime(6) not null,
    item_type enum ('CANDIDATE_REVIEW','FINAL_CONFIRMATION','FRAGRANCE_BRIEF','SAFETY_REVIEW','SENSORY_EVALUATION','TESTING') not null,
    primary key (id)
) engine=InnoDB;

create index idx_approval_gates_candidate_id
   on approval_gates (candidate_id);

create index idx_candidate_memos_candidate_id
   on candidate_memos (candidate_id);

alter table candidate_memos
   add constraint uk_candidate_memos_candidate_type unique (candidate_id, memo_type);

create index idx_cvi_candidate_version_id
   on candidate_version_ingredients (candidate_version_id);

create index idx_candidate_versions_candidate_id
   on candidate_versions (candidate_id);

create index idx_candidates_request_id
   on candidates (request_id);

create index idx_catalog_sync_runs_job_id
   on catalog_sync_runs (job_id);

create index idx_evidence_reports_candidate_id
   on evidence_reports (candidate_id);

create index idx_experiment_status_logs_candidate_id
   on experiment_status_logs (candidate_id);

create index idx_fragrance_requests_project_id
   on fragrance_requests (project_id);

create index idx_generation_rejections_request_id
   on generation_rejections (request_id);

alter table ingredient_masters
   add constraint idx_ingredient_masters_external_id unique (external_id);

create index idx_jobs_project_id
   on jobs (project_id);

create index idx_jobs_status
   on jobs (status);

alter table jobs
   add constraint uq_jobs_idempotency unique (created_by, job_type, idempotency_key);

alter table members
   add constraint UK9d30a9u1qpg8eou0otgkwrp5d unique (email);

create index idx_password_reset_tokens_member_id
   on password_reset_tokens (member_id);

alter table password_reset_tokens
   add constraint UKajre85ybxavf1tt4omkrs5p6g unique (token_hash);

create index idx_project_image_assets_project_id
   on project_image_assets (project_id);

create index idx_project_member_audit_logs_project_id
   on project_member_audit_logs (project_id);

create index idx_project_members_member_id
   on project_members (member_id);

alter table project_members
   add constraint uq_project_members_project_member unique (project_id, member_id);

create index idx_refresh_tokens_member_id
   on refresh_tokens (member_id);

alter table refresh_tokens
   add constraint UKo2mlirhldriil2y7krapq4frt unique (token_hash);

create index idx_sensory_test_results_test_id
   on sensory_test_results (sensory_test_id);

create index idx_sensory_tests_candidate_id
   on sensory_tests (candidate_id);

create index idx_scac_supply_change_id
   on supply_change_affected_candidates (supply_change_id);

create index idx_supply_changes_project_id
   on supply_changes (project_id);

create index idx_supply_changes_ingredient
   on supply_changes (ingredient_external_id);

alter table supply_changes
   add constraint idx_supply_changes_source_id unique (change_source_id);

create index idx_srd_candidate_id
   on supply_review_decisions (candidate_id);

create index idx_work_checklist_items_request_id
   on work_checklist_items (request_id);

alter table work_checklist_items
   add constraint uk_work_checklist_items_request_type unique (request_id, item_type);
