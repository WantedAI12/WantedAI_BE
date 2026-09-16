## 관련 이슈
closes #

## 변경 사항

## 작업 도메인
<!-- CONVENTIONS.md 기준: member / project / request / formula / safety / prediction / experiment / evidence / ingredient / supply / job / global -->

## 테스트
- [ ] `./gradlew test` 통과
- [ ] 로컬에서 직접 확인함

## 체크리스트
- [ ] 도메인 간 의존 방향 규칙 준수 (`request → formula → safety/prediction → experiment → evidence`)
- [ ] 도메인 간 entity 직접 참조 없음 (ID/DTO로만 주고받음)
- [ ] 외부 AI 호출은 `global.client`를 통해서만 수행
