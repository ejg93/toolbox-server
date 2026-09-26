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
| 2026-09-26 | 0-1 pom + 골격 | 완료 — 좌표 전부 첫 시도에 풀림: javalin 6.7.0·jackson 2.17.2·h2 2.3.232·picocli 4.7.6·logback 1.5.18·poi 5.3.0·junit 5.11.4·htmlunit 4.11.1·testcontainers 1.20.4(BOM). `target/app.jar` 31MB, `java -jar` → `toolbox-server 0.1.0-SNAPSHOT`. **정한 것(계획 밖)**: shade 제외에 `module-info.class` 추가, 매니페스트 `Multi-Release: true`(1-7 사다리를 미리) | `81dc90c` |
| 2026-09-26 | 0-2 CI | 완료 — `.github/workflows/verify.yml` 행대로. **드러난 것**: `mvnw` 가 git 에 100644 로 들어가 있어 러너의 `./mvnw` 가 못 돈다 → `git update-index --chmod=+x` 로 같은 커밋에서 고침. tools 레인 스텝에 `git config user.*`(hooks-test 가 임시 저장소에 커밋한다). CI 결과는 마무리에서 | `3811259` |
| 2026-09-26 | 0-3 프로필 | 완료 — `ProfileStoreTest` 6(왕복 equals·필드 전부·모르는 키 예외·connections 없음·`Connection` 에 pass 류 필드 없음(리플렉션)·활성 파일과 CLI 우선). **정한 것(계획 밖)**: 하위 record 10개를 `Profile.java` 안에 중첩(청크 파일 수 1~3). 목록 필드는 compact 생성자에서 null → 빈 목록. `ProfileStore` 는 정적 `load/save/list(Path)` + 인스턴스(`profilesDir`·`dataDir`) `list()/load(name)/active()/setActive()/resolveActive(cli)`. 예시의 Windows 경로는 슬래시 | `58623bb` |
