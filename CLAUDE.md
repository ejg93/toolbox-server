# toolbox-server

폐쇄망 반입용 로컬 서버 도구. 계획·결정 사항은 전부 [PLAN.md](PLAN.md) 에 있다. **작업 전에 PLAN.md 의 해당 장을 읽고, PLAN 에 없는 결정을 새로 내려야 하면 먼저 묻는다.**

## 구역

| 경로 | 무엇 | 규칙 |
|---|---|---|
| `pure/` | portfolio `frontend/public/toolbox` 에서 복사한 순수본. `file://` 로 여는 단일 파일 HTML | **동결.** 여기서 고치지 않는다. 수정은 portfolio 에서 하고 `scripts/sync-pure.sh` 로 끌어온다 |
| `src/main/java/kr/ejg/toolbox/core/` | 메타모델·파서·규칙·리포트 | `web`·`cli`·Javalin 을 import 하지 않는다 |
| `src/main/java/kr/ejg/toolbox/web/` | Javalin 라우트·SSE·정적 파일 | `127.0.0.1` 외 바인드 금지 |
| `src/main/java/kr/ejg/toolbox/cli/` | picocli 명령 | |
| `src/main/resources/tools/` | 백엔드본 HTML + `common.js` + `common.css`. 파일명 영문 | 바닐라 JS. 빌드 없음. CDN 금지 |
| `profiles/` `mappings/` `rules/` `templates/` | 사업별 설정. YAML 이 원본 | 실제 사업 프로필·발주처 양식은 커밋하지 않는다(`.gitignore`). 예시 파일만 |
| `src/test/resources/golden/` | 골든 파일 | 갱신은 diff 를 보고 의도된 변화일 때만 |
| `src/test/resources/fixtures/` | 코드 검사 양성·음성 픽스처 | 규칙 하나당 양성·음성 각 1개 이상 |
| `bundle/` | 반입 재료 레시피 — `SOURCES.md`(출처·판·라이선스)·`MANIFEST`(지문)·드라이버 pom 둘. 받은 jre·drivers 는 추적 안 함. 반입 zip 의 `jre/` 는 그 JDK 의 jlink 이미지(`package.sh` `JLINK_MODULES`) | 지문은 `bundle-fetch.sh` 만 고친다 |
| `corpus/` | 실물 표본 레시피 — `SOURCES.md`(출처·태그·라이선스)·`MANIFEST`(지문). 표본 실물은 저장소 밖 `C:/workspace/toolbox-corpus`(PLAN 4장) | 표본 파일을 저장소에 넣지 않는다. 지문은 `corpus-fetch.sh` 만 고친다 |
| `design/` | 설계 세션 명세 — PLAN 행이 가리키는 상세(코드 꼴·화면 배치·실측·근거). `14-ui-demo.md` 가 번들 20~25 | 실행 세션은 행과 그 행이 가리키는 U-절만 읽는다. 행이 완료되면 안 고친다. 반입 zip 에 안 들어간다 |

## 절대 규칙

1. **외부 통신 0.** 런타임에 `127.0.0.1` 밖으로 나가는 호출을 만들지 않는다. 텔레메트리·업데이트 확인·CDN 전부 금지. 예외는 사용자가 지정한 서버 둘뿐 — 프로필의 DB 접속(JDBC), 그리고 사용자가 조회를 눌렀을 때 `svn` 명령이 작업 사본의 저장소 서버에 묻는 것(배포 목록 리비전 구간, 2026-10-02). 자바 프로세스가 여는 소켓은 DB 뿐이다
2. **DB 비밀번호는 프로필 YAML 에만.** 접속 항목 `password`. H2·로그·API 응답·오류 글에는 쓰지 않는다(2026-10-06 사용자 결정 — 「메모리만」 폐기)
3. **코드 본문을 저장하지 않는다.** 검사 이력은 `파일·줄·규칙` 까지. 로그에도 사용자 코드·SQL 결과를 남기지 않는다. 예외 하나 — 프로그램 분석의 설명 100자(컨트롤러 메서드 javadoc·블록 주석 첫 문장, `analyze_program.descr`, 2026-10-03 사용자 결정)
4. **순수본 동결.** `pure/` 를 직접 고치면 안 된다
5. **새 의존성은 `m2/` 오프라인 빌드를 깨지 않아야 한다.** 추가했으면 `scripts/offline-build.sh` 를 돌려 확인
6. **외부 DB 는 읽기만.** `ConnectionRegistry.open` 이 주는 연결은 `ReadOnlyJdbc` 래퍼 — SELECT·WITH 만 지나고 DML·DDL·프로시저·다중 문장은 거절(번들 48, 설계 31). 외부 DB 연결을 다른 자리에서 열지 않는다(`ArchitectureTest`)
7. **원본 소스를 안 고친다.** 고친 파일은 결과 폴더(`out/<프로필>/<시각>/<기능>/` 또는 사용자가 고른 빈 폴더, 표식 `결과_출처.txt`)에 같은 상대 경로로 `LocalFiles.writeResult` 가 쓴다. 원본을 되쓰는 API·CLI 를 만들지 않는다 — 그 밖 쓰기는 도구 자기 출력(`out/`·`data/`·`logs/`)과 화면에서 저장한 프로필(`profiles/`)뿐(번들 48, 사용자 2026-10-23)

## 빌드·실행

```
./mvnw -q package            JDK 17. scripts 가 TOOLBOX_JDK17_HOME → JAVA_HOME(17 일 때) → C:/Program Files/Java/jdk-17.0.19 순으로 잡는다(scripts/lib/jdk17.sh)
run.bat [--port N] [--profile 이름]
./mvnw -q test               Testcontainers 사용. Docker Desktop 이 켜져 있어야 한다
bash scripts/verify.sh [--db] [--corpus] [--full]   바뀐 레인만. 무거운 레인(db 약 17분 · corpus 약 7분)은 플래그로, --full 은 반입 전
scripts/offline-build.sh     네트워크 없이 m2/ 만으로 빌드되는지
scripts/sync-pure.sh         portfolio 순수본 끌어오기 + 해시 비교
scripts/corpus-fetch.sh      실물 표본 받기(네트워크, 저장소 밖 폴더) + corpus/MANIFEST 갱신
scripts/bundle-fetch.sh      반입 재료 받기 — jre(JDK 17, jlink 재료)·drivers(네트워크) + bundle/MANIFEST. --check 는 네트워크 없이
scripts/package.sh           실행 전용 반입 zip(app.jar·jlink jre·드라이버·예시 설정·심의 자료, MANIFEST.txt·금지 검사). 깨끗한 트리만
scripts/rehearse.sh <zip>    저장소 밖에 풀어 동봉 런타임만으로 version·selftest·기동(127.0.0.1 만) — 여섯 단계
toolbox.bat <명령>           배치 CLI(소켓 없이 화면과 같은 라우트). toolbox.bat --help
scripts/pit.sh               PIT 뮤테이션 — core.check 만, 리포트 out/pit/index.html(게이트 아님, 약 12분, 네트워크 — 집 전용)
scripts/pmd-baseline.sh      PMD 기준선 다시 뜨기 — 지적을 줄였을 때만
```

## 재개 프로토콜 — 「다음 청크 해」

1. 「현재 상태」는 SessionStart 훅이 넣어 준다. 그 블록이 단일 진실이다. `PROGRESS.md` 를 다시 안 읽는다
2. `진행중` 청크가 있으면 그것부터
3. 없으면 `PLAN.md` 「청크 분할표」에서 선행이 `완료` 인 다음 행. **그 행과 행이 가리키는 장만 읽는다.** 통짜로 안 읽는다
4. 시작에 세 줄을 말한다 — 무엇을 닫나 / 걸리는 축 / 강제 지점을 어디에 두나. 행에 있으니 확인하고 달라진 것만 밝힌다
5. 갈리면 사용자에게 선택지를 낸다. **번들 모드는 예외다**(아래)
6. 끝에 다섯을 채운다 — `bash scripts/verify.sh` 초록 / 남긴 강제 지점 한 줄 / 드러난 것 처분(새 행으로) / 커밋 하나 / 분할표 행 + `PROGRESS.md` 이력 한 줄(해시까지)
7. 가지는 `work/<날짜>`. 청크마다 안 민다. 미는 것은 마무리 앞 한 번

## 번들 모드 — 「번들 해」

설계 세션(Fable)이 적어 둔 행을 순서대로 친다. 차례는 `PLAN.md` 「번들」 표.

- **갈려도 안 멈춘다** — 실패 사다리에 있으면 그대로, 없으면 정하고 이력에 「정한 것(계획 밖)」. 행당 둘까지. 넘으면 `wip/<청크>` 로 빼고 다음 행
- verify 빨강은 고치기 둘까지. 그래도면 `wip/<청크>` 곁가지에 커밋하고 번들 가지로 돌아온다. **빨간 트리를 `work/*` 에 안 올린다** — commit hook 이 막고 뒤 청크 검증이 다 빨개진다
- `verify.sh` 와 `git commit`, `verify.sh --db`·`--corpus` 와 `git push` 는 **따로 낸다** — 훅이 명령이 돌기 전에 재서 한 체인이면 막힌다
- 청크 사이에 턴을 안 끝낸다. 한 줄 보고 뒤 바로 다음 행
- 다 치면 「마무리」

## 청크 규칙 — 어떻게 자르나

- 청크 하나 = 파일 1~3개 = 커밋 하나. `PLAN.md`·`PROGRESS.md` 는 이 수에 안 넣는다
- 커밋 제목은 `<종류>: <청크번호> <무엇>` — 종류는 `feat`·`fix`·`chore`·`docs`·`test`. 예 `feat: 0-4 서버 기동 + ping`. 본문은 왜가 안 보일 때만
- 서로 독립 — 앞이 미완이어도 뒤를 잡을 수 있게
- 커밋 안 된 작업물을 남긴 채 세션을 끝내지 않는다(Stop 훅이 막는다). 미완이면 `wip/<청크>` 에, 버릴 것이면 `git stash`
- 분할표는 결과, 이력은 서사. 완료 행은 안 고친다

## 마무리 — 번들 끝

1. 독립 리뷰 — 「Superpowers 와 잇는 법」 의 Final Review **하나**(모델 `fable`). 지적은 지금 고치거나 새 행으로. 오탐은 근거를 이력에
2. `bash scripts/gate-probe.sh` — 게이트 열을 부수면 빨개지는지(약 2분). 「패치 갱신 필요」 는 코드가 바뀌어 `scripts/probes/*.patch` 가 안 맞는 것 — 다시 떠서 커밋한다
3. `bash scripts/verify.sh` — 끝 줄이 알려 주는 무거운 레인만 `--db`·`--corpus` 로(push 와 따로). `--full` 은 반입 전(`package.sh` 가 요구)
4. `git push -u origin work/<날짜>` — main 직접 push 는 훅이 막는다
5. `gh pr create --base main` — 본문에 청크마다 시작 세 줄 표. 뒤에 커밋을 얹으면 `gh pr edit N --body-file` 로 표도 같이 고친다(AI 리뷰가 본문을 입력으로 본다)
6. CI 폴링 — 30초 간격으로 `gh api repos/ejg93/toolbox-server/commits/$(git rev-parse HEAD)/check-runs --jq '.check_runs[]|[.name,.status,.conclusion]|@tsv'`. 전부 `completed success` 면 다음. `gh pr checks` 는 안 쓴다(권한 분류기가 막는다). 빨강은 고치기 둘까지
7. 머지 위임이 있으면 `gh pr merge N --merge --delete-branch` → `git checkout main && git pull` → 다음 가지. 없으면 PR 열고 멈춘다
8. 끝 보고 — 계획 밖 결정 전부 · wip/ 로 뺀 행과 이유 · 새로 선 행 · 사람이 할 것

## Superpowers 와 잇는 법

절차는 Superpowers 플러그인(`obra/superpowers`, `.claude/settings.local.json` `enabledPlugins`)이 굴리고, 관문은 이 저장소의 훅·`verify.sh`·CI 가 든다(2026-10-29 사용자 결정 — ProjectTicket·portfolio 와 같은 넷: TDD 예외 미리 허락 · 독립 리뷰 하나 · brainstorming 선호 선언 · 계획 검토 안 물음).
**이 파일이 Superpowers 스킬보다 앞선다.** 둘이 다르면 여기를 따른다(`using-superpowers` 「User instructions take precedence」).
**확인한 판: 7.0.0**(2026-10-29, `0-74`). 판이 오르면 아래 두 표와 「실행 규칙」 의 덮은 자리를 스킬 원문과 다시 댄다.

| 스킬 기본 동작 | 여기서는 |
|---|---|
| `using-superpowers` — 1% 규칙 · plan mode 앞 brainstorming | 창작에만 선다. 「다음 청크 해」·「번들 해」·점검·검증·리뷰·질문 답변·파일 2개 이하 수정은 brainstorming·writing-plans 없이 바로 한다. `/plan` 은 전역 CLAUDE.md 의 8절 양식이 설계 문서라 brainstorming 을 앞에 안 부른다 |
| `brainstorming` — 메시지당 질문 하나 · spec 을 `docs/superpowers/specs/` 에 커밋 · builder-check 서브에이전트 · 시각 보조 · 승인 뒤 `writing-plans` | 설계 세션(Fable)이 쓴다. **선호 선언**: 사람만 답할 질문은 한 메시지에 (가)/(나) 로 모아 묻는다 · 설계 문서는 `design/<번호>-<주제>.md` + `PLAN.md` 행이다(spec 파일 없음) · builder-check 와 시각 보조는 안 쓴다 · 승인 뒤 `writing-plans` 로 안 넘어간다 — 실행은 다른 세션(Opus)이 「번들 해」 로 연다 |
| `writing-plans` — 계획을 `docs/superpowers/plans/` 에 · 헤더 `subagent-driven-development (recommended)` · 쓴 뒤 검토와 실행 방식을 묻고 기다림 | 「번들 해」 의 첫 손. 번들 표의 행을 `.superpowers/plans/<날짜>-번들<N>.md` 로 옮긴다(gitignore, 커밋 안 함). 행 하나 = Task 하나. **원천은 `PLAN.md` 분할표다** — 계획 파일은 실행용 사본이라 둘이 다르면 `PLAN.md` 가 이긴다. `Spec:` 줄은 번들 표의 설계 파일(`design/<번호>-*.md`)과 행 번호. 헤더 REQUIRED SUB-SKILL 은 `superpowers:executing-plans` 하나. Global Constraints 에 절대 규칙 1·3·6·7 과 아래 「Task 끝」 순서. **검토도 실행 방식도 묻지 않는다** — 검토는 설계가 행을 적을 때 끝났다. 경로 한 줄만 알리고 `executing-plans` 로 간다 |
| `executing-plans` — Setup 에서 worktree · spec 없으면 provisional · 멈추는 넷(비가역·보안·push·머지·깨진 계획) · 끝에 `finishing-a-development-branch` | 번들 모드의 본체. 아래 「실행 규칙」 |
| `using-git-worktrees` — 선언이 없으면 worktree 를 묻는다 | **worktree 선호: 쓰지 않는다. `EnterWorktree` 도 안 쓴다.** 제자리 `work/<날짜>` 체크아웃에서 친다 — 훅이 `CLAUDE_PROJECT_DIR`(세션을 연 폴더, 시작 때 굳는다)의 가지·`.git/verify-stamp` 만 봐서 worktree 커밋은 도장 검사를 안 지난다 |
| `test-driven-development` — 모든 구현에 · 예외는 사람 허락 | `src/main/java` 를 고치는 행에 건다. 「실패하는 시험 먼저」 = 골든 파일·픽스처(규칙 하나당 양성·음성)·단언을 먼저 쓴다. **예외 허락(2026-10-29)**: 문서·설정·`scripts/`·훅·`pure/` sync·골든·baseline 갱신·화면 HTML/JS 만 고치는 행은 그 단계를 빼고, 그 행의 마지막 번호 시험(`verify.sh` 레인·HtmlUnit 스모크·`doc-lint`)이 RED→GREEN 자리다. 원장에 `Ruling:` 한 줄 |
| `systematic-debugging` — 근본 원인 전엔 안 고친다 | 따르되 횟수 상한은 「번들 모드」 가 이긴다 — 고치기 둘까지, 그래도면 `wip/<청크>` |
| `finishing-a-development-branch` — 선택지를 내밀고 기다림 | **부르지 않는다** — 메뉴를 덮어도 스킬이 「그대로 내밀고 기다리라」 고 반박한다. 「마무리」 3~8 이 대신한다 |

**실행 규칙** — 「번들 해」 를 `executing-plans` 로 칠 때. 「번들 모드」·「청크 규칙」 은 그대로고 여기는 스킬과 맞닿는 자리만

- 실행 모델은 Opus 다(`PLAN.md` 15장). 다른 모델이면 첫 응답에서 말하고 계속한다
- 한 세션이 인라인으로 친다. Task 마다 서브에이전트를 안 띄운다(`subagent-driven-development` 는 사용자가 지목할 때만)
- 「갈려도 안 멈춘다」 가 스킬의 「멈추는 넷」 보다 앞선다. 비가역·보안만 멈춘다. push·PR 은 묻지 않는다(2026-10-29 사용자 위임). 머지는 번들 표 행에 「머지 위임」 이 있을 때만 — 없으면 PR 열고 멈춘다(「마무리」 7)
- 원장 `Ruling:` 은 이력 「정한 것(계획 밖)」 의 사본이다. 이력이 원본. 행당 둘까지는 「번들 모드」 그대로
- 시작 세 줄은 행에 있다. `task-start PLAN N` 이 준 brief 와 BASE 로 확인만 하고 말하지 않는다
- **Task 끝** — 순서가 훅을 지난다. ① 분할표 행 상태·이력 한 줄을 먼저 고친다 ② `bash scripts/verify.sh` 초록 ③ `git commit`(따로 낸다 — commit-guard 가 작업 트리 지문을 도장과 맞춘다) ④ `task-done PLAN N BASE -- bash scripts/verify.sh`. ④ 는 같은 지문이라 도장에서 바로 초록이다. `task-done` 은 명령을 `"$@"` 로 직접 실행해서 환경 접두가 안 돈다 — Maven 을 직접 줄 때는 `bash scripts/mvn.sh …`(맨몸 `mvnw` 는 java-home-guard 가 막는다)
- 워크스페이스 `.superpowers/sdd/<계획>/` 과 계획 `.superpowers/plans/` 는 `.gitignore` 라 Stop 훅(`stop-uncommitted`)에 안 잡힌다. `docs/superpowers/` 에 쓰면 docs 레인 지문이 바뀌어 도장·커밋·push 가 다 막힌다

**마무리와 맞닿는 자리**

| 「마무리」 | 스킬 |
|---|---|
| 1 독립 리뷰 | `executing-plans` 의 Final Review **하나** — `review-package PLAN $(git merge-base origin/main HEAD) HEAD` + `requesting-code-review/code-reviewer.md`, 서브에이전트에 `model: fable` 명시. 계획 Review Focus 에 이 저장소 질문: 절대 규칙 1·3·6·7 을 어긴 자리 · 행의 축·강제 지점과 diff 가 다른 자리 · 새 규칙에 양성·음성 픽스처가 다 있나 · 골든 갱신이 의도된 변화인가. 대화는 안 준다. `cavecrew-reviewer` 를 따로 또 돌리지 않는다. 처분은 「마무리」 1 의 처분표(지금 고침·새 행·오탐 근거)가 플러그인의 「Critical/Important 한 번의 fix pass · Minor 는 원장」 보다 앞선다 |
| 2~3 탐침·verify | 그대로. 리뷰 고침 커밋 뒤에 돈다 — 먼저 돌면 고침이 지문을 바꿔 push-guard 가 막는다 |
| 4~7 push·PR·CI·머지 | 그대로. CI 는 `gh api` check-runs 폴링(`gh pr checks` 는 권한 분류기가 막는다). 머지 전 claude[bot] 코멘트를 처분한다 |
| 8 끝 보고 | 원장의 `Ruling:` 줄이 이력 「정한 것(계획 밖)」 에 다 있는지 대조한 뒤 `.superpowers/sdd/<계획>/` 을 지운다 |

## 검증 도장

`bash scripts/verify.sh` 가 `origin/main` 대비 바뀐 레인(java·tools·docs·db·corpus, 경로는 `scripts/verify-fingerprint.sh` 한 곳)만 돌리고 `.git/verify-stamp` 에 「레인 지문 단계」를 적는다(설계 21).
빠른 도장 = java 는 `db`·`corpus` 태그 뺀 테스트. db 레인(DB 에 붙는 코드 — 메타 수집·접속·SQL 실행·품질·INSERT·DDL·마스킹·마이그레이션·pom)과 corpus 레인(파서·규칙·화면 JS·순수본·표본 지문)은 바뀌어도 빠른 검증이 끝 줄에 알리기만 하고, `--db`·`--corpus` 로 돌린다. `--full` = 전부(반입 전). 무거운 검증은 그 코드가 바뀌었을 때와 반입 전에만 — 화면만 고친 번들에 20분을 돌리지 않는다(사용자 2026-10-10). 레인 목록이 꼬리표 시험의 실제 의존과 어긋나면 `VerifyLanesTest` 가 빨강. 빠른 검증은 이미 있는 full 도장을 낮추지 않는다(0-41). 훅이 본다:

| 훅 | 무엇을 막나 |
|---|---|
| Stop `stop-uncommitted` | 커밋 안 된 작업물이 있으면 턴 끝을 한 번 막는다 |
| Stop `stop-stamp` | HEAD 가 origin/main 과 다른데 도장이 없으면 |
| PreToolUse `commit-guard` | `work/*` 에서 작업 트리 지문 ≠ 도장이면 커밋 금지 |
| PreToolUse `push-guard` | main 직접 push 금지. push 는 바뀐 레인의 도장 — java·tools·docs 는 빠른, 바뀐 db·corpus 는 돌았어야 |
| PreToolUse `pr-guard` | PR base 는 main. 열린 작업 PR 이 있으면 새 PR 금지. 체크 안 끝난 머지 금지 |
| PreToolUse `java-home-guard` | 맨몸 `mvnw` 금지 → `scripts/mvn.sh` |
| Stop·UserPromptSubmit `orphan-reap` | 셸이 죽어 남은 Maven·테스트 JVM 을 끈다 — `mvn.sh` 감시 루프의 뒷문(0-42) |
| PostToolUse `doc-lint-*` | 문서 존댓말 |

컨테이너 테스트는 `@Tag("db")`. Docker 가 꺼져 있으면 `bash scripts/docker-up.sh`.

## 검증

- 변환·계산·SQL 생성 로직은 **골든 파일** JUnit. 실패를 남긴 채 끝내지 않는다
- 화면·클릭·콘솔 에러는 HtmlUnit 스모크(JUnit). Puppeteer 는 집 검증에만 쓰고 저장소 스크립트는 `scripts/puppeteer/` 에. 경로에 한글 금지
- DB 는 Testcontainers. Tibero 는 컨테이너가 없어 골든 파일만 유지
- **실물 표본**(PLAN 4장) — `@Tag("corpus")`. 전체는 불변식, 손 고른 ≤10 만 골든. **등급 A**(코드 파괴 — 컴파일·파싱·실행 실패·왕복 불일치)는 baseline 없이 0 이어야 머지. **등급 B**(덜 함·모양 다름)는 `golden/corpus/` baseline — 새로 깨져도·새로 고쳐져도 빨강, 표본의 2% 넘으면 빨강, 갱신은 diff 를 이력에. 번들당 A 고치기 10건, 넘치면 B 로 내리고 새 행. `--corpus`·`--db`·`--full` 은 표본 폴더와 node 가 있어야 한다(없으면 빨강), CI 는 표본을 건너뛰고 끝 줄에 센다. 실패 출력은 건수 + 처음 20건
- 돌리지 못했으면 못 돌렸다고 쓴다. 안 돌려보고 「통과」 라고 쓰지 않는다

## 작성 규약 — 빌드가 막는 것만

| 규약 | 강제 |
|---|---|
| 새 메서드 순환 복잡도 ≤ 15 | `SelfCheckTest.mainMatchesBaseline` — `metric.complexity` 기준선, 새 지적이 빨강 |
| 빈 catch 는 `// tb-ignore java.emptyCatch <사유>` | `SelfCheckTest.mainMatchesBaseline` — `java.emptyCatch` |
| 패키지 의존은 한 방향 | `ArchitectureTest.noPackageCycles` |
| 시험은 단언 필수. 단언하는 도우미는 `SelfCheckTest.HELPERS` 에 | `SelfCheckTest.everyTestAsserts` — `java.testNoAssert` |
| `tb-ignore` 는 주석 첫 낱말 + 규칙 + 사유 | `CheckRulesTest.ignoreMarkers`(문법 `docs/pure-code-check.md` 9절) |
| 라우트 몸은 `register` 안 람다 대신 `private static void <이름>(Context ctx, …)` | `metric.complexity` 가 람다 분기를 `register` 에 센다(0-63a) |
| 커버리지 하한 — 전체 줄 0.91·분기 0.78, `core.*` 패키지 줄 0.68 | JaCoCo `check`(verify) |

빌드가 더 거는 것(번들 55) — ErrorProne ERROR(컴파일) · PMD errorprone·bestpractices — 기준선 `config/pmd/exclude-from-failure.properties` 에 없는 클래스·규칙 짝(verify, 기준선에 든 짝은 건수가 늘어도 통과) · 시험 하나 2분(db·corpus 10분). 스타일(이름·로깅)은 안 적는다 — 강제 없는 규약은 두지 않는다.

## 글 작성 규칙

portfolio 루트 CLAUDE.md 「글 작성 규칙」 여섯을 그대로 따른다 — 중복 금지·사견 배제·늘어지지 않게·존댓말 금지·자기 기능만 설명·줄바꿈은 렌더 기준. 도구 설명란 첫 문장에는 `무엇을` `무엇으로` `어떻게` 가 들어간다.

## 화면 스타일

토큰·본문·카드·입력칸·표·버튼·탭은 `tools/common.css` 하나(설계 18). 각 화면은 그 link 를 첫 `<style>` 앞에 두고, 화면 `<style>` 에는 그 화면에만 있는 것만 둔다 — `:root`·`button{}`·`.btn-*`·`.dl`·목록 밖 고정 색은 `ToolsFolderTest.toolsHaveNoLocalTheme` 가 막는다. 변수 이름은 순수본과 같다(`--bg` `--surface` `--border` `--text` `--muted` `--accent`, 더해서 `--well` `--code` `--raised` `--hover` `--sel` 등). 폰트 `'Consolas','D2Coding',monospace`.

- 테마: `<html data-theme="dark|light">`(오른쪽 아래 「테마」 버튼, localStorage `tb-theme`) → 없으면 OS 설정. 글자 고정 화면(`sql_snippets` — 순수본 + common.js 한 줄)은 common.js 가 link 를 끼우고 어둡기로 고정한다
- 버튼 유형: 실행 `.btn-p` · 파일을 만드는 것 `.dl`(xlsx 는 안에 `.ico-xlsx`) · 복사 `.btn-green` · 지우기·덮어쓰기·중지 `.btn-red` · 나머지 기본(회색). `.btn-g` 는 순수본 JS 가 붙이는 이름이라 기본과 같은 꼴
- 모드 배지·`.tb-table` 은 `common.js` 가 붙인다
- 집 검증: `node scripts/puppeteer/visual.js <label>`(화면 × dark/light × 1400/375 샷 · 버튼 판정 · 콘솔 오류) → `node scripts/puppeteer/visual-diff.js <전> <후>`(out/visual/<후>/report.html)
