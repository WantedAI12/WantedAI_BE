# 패키지 구조 & 코딩 컨벤션

## 패키지 구조

```
com.perfumeryaicore
├── domain/                도메인 단위 패키지 (모놀리식, package-by-feature)
│   ├── member             인증, 사용자, 전역 역할
│   ├── project            조직/프로젝트 테넌트, 프로젝트별 멤버·역할 배정
│   ├── request            자연어 향 요청, 구조화 결과, 보완 질문
│   ├── formula            후보 조향식, 버전 이력, 편집/복제
│   ├── safety             안전·규제·공급 적합성 평가, 승인 게이트
│   ├── prediction         성능 프록시, 불확실성·OOD·기권 판정
│   ├── experiment         후보 비교, 실험 후보 확정/상태 관리
│   ├── evidence           증거·감사 이력, 블라인드 관능 검증, PDF 보고서
│   ├── ingredient         원료 마스터, 외부 시험 데이터 가져오기·오류 재처리
│   ├── supply             원료·공급 조건 변경 영향 분석, 재검토 의사결정
│   └── job                비동기 작업(외부 AI 서비스 호출) 공용 상태 관리
│
│   각 도메인 하위:
│     controller / service / repository / entity / dto/{request,response}
│
└── global/                도메인을 가로지르는 공통 요소
    ├── config             스프링 설정 (WebClient, OpenAPI 등)
    ├── exception          전역 예외 및 핸들러
    ├── response           공통 응답 래퍼
    ├── security           JWT 인증/인가, SecurityConfig, BCrypt, Refresh Token
    ├── tenant             테넌트 컨텍스트 홀더·리졸버, 테넌트 필터
    ├── common             BaseEntity, 공통 enum, 공유 타입
    ├── audit              JPA Auditing, createdBy/updatedBy, 감사 로그 연동
    └── client             외부 AI 서비스 WebClient, 요청/응답 계약 DTO, 스키마 버전 검증
```

빈 말단 패키지는 `.gitkeep`으로 커밋되어 있으며, 실제 클래스가 채워지면 삭제한다.

## 도메인 간 의존 규칙

- **서비스 → 다른 도메인 서비스** 호출은 허용하되, 워크플로 방향으로 **단방향**만 둔다:
  `request → formula → safety / prediction → experiment → evidence`
  (`ingredient`, `supply`, `job`, `member`, `project`는 위 도메인들이 참조하는 하위 의존)
- **controller → 다른 도메인 controller** 호출 금지.
- **도메인 간 entity 직접 참조 금지** — 식별자(ID) 또는 DTO로만 주고받는다.
- 여러 도메인을 조율하는 로직이 복잡해지면 얇은 `application`(유스케이스) 계층을 별도로 둔다.

## 기타

- 외부 AI 서비스는 `global.client`를 통해서만 호출한다. 도메인 서비스가 WebClient를 직접 다루지 않는다.
- 모든 엔티티는 `global.common.BaseEntity`를 상속해 생성·수정 이력을 남긴다.
- 조회 쿼리는 `global.tenant` 필터로 테넌트 격리를 적용한다.
