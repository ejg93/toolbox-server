# 진행 로그

## 현재 상태

**대상**: 폐쇄망 반입용 로컬 서버 도구(Javalin 6 + JDK 17 + H2). **가지**: `work/2026-09-26`(부트스트랩 뒤 첫 작업 가지. 밤 세션이 여기에 쌓는다).

| 무엇 | 상태 | 다음 손 |
|---|---|---|
| 진행중 청크 | **없다.** 2026-09-26 저녁 부트스트랩(훅·verify·순수본·계획)만 main 에 있다 | PLAN.md 「번들」 표의 번들 0 첫 행 `0-1` |
| 열려 있는 것 | 없음 | |
| 사람이 할 것 | 없음 | |

**기록 규칙** — 분할표(PLAN.md)는 결과, 이력(여기)은 서사다. 이력 한 줄 = 청크 하나. 커밋 해시까지. 「정한 것(계획 밖)」·「드러난 것」은 이력에만 적는다.

## 이력

| 날짜 | 청크 | 결과 | 커밋 |
|---|---|---|---|
| 2026-09-26 | B. 부트스트랩 | 완료 — `PLAN.md`(14장 + 번들·분할표)·`CLAUDE.md`·`PROGRESS.md`·`README.md`·`.gitignore`, `scripts/`(verify·fingerprint·doc-lint·mvn·offline-build·sync-pure·docker-up·hooks-test)·`scripts/hooks/` 열, `.claude/settings.json`·`prompts/overnight.md`, `pure/`(portfolio 순수본 8 + MANIFEST), `mvnw`(portfolio backend 에서 복사, Maven 3.9.9). ProjectShop 의 밤샘 청크 방식을 레인(java·tools·docs)만 바꿔 옮겼다. GitHub private 저장소 생성·main push·`work/2026-09-26` 가지 | |
