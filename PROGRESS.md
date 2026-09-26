# 진행 로그

## 현재 상태

**대상**: 폐쇄망 반입용 로컬 서버 도구(Javalin 6 + JDK 17 + H2). **가지**: `work/2026-09-26`(부트스트랩 뒤 첫 작업 가지. 밤 세션이 여기에 쌓는다).

| 무엇 | 상태 | 다음 손 |
|---|---|---|
| 진행중 청크 | **없다.** 번들 0(`0-1`~`0-8`) 완료 — 마무리(리뷰·PR·CI) 중 | PLAN.md 「번들」 표의 번들 1 첫 행 `0-9` |
| 열려 있는 것 | 새 행 `0-14`(run.bat java 버전 사유) — 번들 밖 | |
| 사람이 할 것 | 없음 | |

**기록 규칙** — 분할표(PLAN.md)는 결과, 이력(여기)은 서사다. 이력 한 줄 = 청크 하나. 커밋 해시까지. 「정한 것(계획 밖)」·「드러난 것」은 이력에만 적는다.

## 이력

| 날짜 | 청크 | 결과 | 커밋 |
|---|---|---|---|
| 2026-09-26 | B. 부트스트랩 | 완료 — `PLAN.md`(14장 + 번들·분할표)·`CLAUDE.md`·`PROGRESS.md`·`README.md`·`.gitignore`, `scripts/`(verify·fingerprint·doc-lint·mvn·offline-build·sync-pure·docker-up·hooks-test)·`scripts/hooks/` 열, `.claude/settings.json`·`prompts/overnight.md`, `pure/`(portfolio 순수본 8 + MANIFEST), `mvnw`(portfolio backend 에서 복사, Maven 3.9.9). ProjectShop 의 밤샘 청크 방식을 레인(java·tools·docs)만 바꿔 옮겼다. GitHub private 저장소 생성·main push·`work/2026-09-26` 가지 | |
| 2026-09-26 | 0-1 pom + 골격 | 완료 — 좌표 전부 첫 시도에 풀림: javalin 6.7.0·jackson 2.17.2·h2 2.3.232·picocli 4.7.6·logback 1.5.18·poi 5.3.0·junit 5.11.4·htmlunit 4.11.1·testcontainers 1.20.4(BOM). `target/app.jar` 31MB, `java -jar` → `toolbox-server 0.1.0-SNAPSHOT`. **정한 것(계획 밖)**: shade 제외에 `module-info.class` 추가, 매니페스트 `Multi-Release: true`(1-7 사다리를 미리) | `81dc90c` |
| 2026-09-26 | 0-2 CI | 완료 — `.github/workflows/verify.yml` 행대로. **드러난 것**: `mvnw` 가 git 에 100644 로 들어가 있어 러너의 `./mvnw` 가 못 돈다 → `git update-index --chmod=+x` 로 같은 커밋에서 고침. tools 레인 스텝에 `git config user.*`(hooks-test 가 임시 저장소에 커밋한다). CI 결과는 마무리에서 | `3811259` |
| 2026-09-26 | 0-3 프로필 | 완료 — `ProfileStoreTest` 6(왕복 equals·필드 전부·모르는 키 예외·connections 없음·`Connection` 에 pass 류 필드 없음(리플렉션)·활성 파일과 CLI 우선). **정한 것(계획 밖)**: 하위 record 10개를 `Profile.java` 안에 중첩(청크 파일 수 1~3). 목록 필드는 compact 생성자에서 null → 빈 목록. `ProfileStore` 는 정적 `load/save/list(Path)` + 인스턴스(`profilesDir`·`dataDir`) `list()/load(name)/active()/setActive()/resolveActive(cli)`. 예시의 Windows 경로는 슬래시 | `58623bb` |
| 2026-09-26 | 0-4 서버 기동 + ping | 완료 — `AppTest` 3(host 상수·ping 200 `mode=backend`·점유 포트 → 다음 포트). **정한 것(계획 밖)**: ① 버전 읽기를 `core.Version` 으로 뺐다 — `web` 이 `cli.Main` 을 부르지 않게. Main 은 그걸 부른다 ② 바인드 전에 `ServerSocket`(reuseAddress 끔)으로 점유를 미리 잰다 — Javalin 이 바인드 실패를 `ERROR Failed to start Javalin` 으로 찍어 기동마다 붉은 줄이 보였다. 경합은 기존 catch 가 받는다. surefire 에 `toolbox.logDir=target/logs`. **드러난 것**: surefire 콘솔에선 한글 로그 일부가 깨져 보인다 — `logs/toolbox.log` 는 UTF-8 정상. netstat 확인은 `serve` 가 생기는 0-10 뒤 | `9ef33e8` |
| 2026-09-26 | 0-5 run.bat | 완료 — JDK 17 로 `.\run.bat --port 41790` → 버전 출력·종료 0, `data`·`out`·`logs` 생성. 읽기 전용 검사: 스크래치 폴더에 `data` 를 파일로 두고 실행 → 「[오류] …\data 에 쓸 수 없다 …」 한글 사유 + `pause` + 종료 1. **정한 것(계획 밖)**: 오류 경로마다 `pause` — 더블클릭 창이 바로 닫혀 사유를 못 읽는다. **드러난 것**: ① `JAVA_HOME`=11 이면 영어 `UnsupportedClassVersionError` → 새 행 `0-14` ② 이 셸에선 `cmd /c run.bat` 이 현재 폴더를 안 찾는다(`.\run.bat` 필요) — 더블클릭엔 무관 | `92c290a` |
| 2026-09-26 | 0-6 common.js | 완료 — `TB.api/badge/table/sse` + 정적 서빙(`/tools` ← classpath `/tools`, 0-4 행의 설정 그대로). `StaticFilesTest` 2(common.js 200·javascript·`window.TB` / 없는 파일 404). **정한 것(계획 밖)**: fetch·async 대신 XHR + Promise — 0-11 HtmlUnit(Rhino) 대비. `file://` 이면 ping 없이 「순수」. `TB.sse` 는 끝 이벤트(done·failed·cancelled)에 스스로 닫고 `EventSource` 가 없으면 `failed` 로 알린다(1-8 사다리를 미리) | `dd5f6c6` |
| 2026-09-26 | 0-7 백엔드 런처 | 완료 — 카드 11(텍스트·코드 6 / DB·산출물 5, `ready` 7·예정 4), `GET /` → 302. `LauncherTest` 2. **정한 것(계획 밖)**: 카드를 JS `TOOLS` 배열이 아니라 정적 마크업 + `data-file`·`data-status` 속성으로 — 닫힘 조건 「본문 `data-file` 11개」를 원시 HTTP 본문에서 세려면 그래야 한다. 순수본 런처의 설정 백업 칸은 안 옮겼다. 색 토큰은 `--card` 대신 `--surface`(common.js 와 맞춤) | `6823c0c` |
| 2026-09-26 | 0-8 기존 7 도구 백엔드본 | 완료 — 7 파일 복사·개명, `</head>` 앞 태그 한 줄(7 파일 다 `</head>` 하나). `BackendToolsTest` 7(200·태그·`</head>` 앞). **정한 것(계획 밖)**: `logical_name.html` 의 `<a href="산출물_sql.html#…">` 링크·해시 8곳을 `deliverable_sql.html` 로 — 개명으로 끊기는 링크만 고쳤다. **드러난 것**: `deliverable_sql.html`(2곳)·`sql_snippets.html`(5곳)의 SQL 주석·안내문이 「논리명_변환기.html」 을 파일명으로 부른다 — 링크가 아니라 글이라 그대로 뒀다. 2.x 패치(M2)에서 문구를 도구 이름으로 | `d2d8707` |
| 2026-09-26 | 번들 0 마무리 | 독립 리뷰(`cavecrew-reviewer`) — 버그 0, 위험 3·질문 1 모두 처분: ① run.bat 이 `serve` 를 넘기는데 Main 이 무시 → 계획대로(0-10) ② CI 의 `hooks-test.sh` 호환 → 로컬 41경우 초록, CI 가 판정 ③ `chcp 65001` → 0-5 에서 cmd 한글 출력 확인 ④ 포트 사전 검사가 마지막 시도를 건너뜀 → 의도(마지막은 catch 가 예외를 그대로 던진다). `verify.sh --full` 초록 | |
