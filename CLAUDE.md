# toolbox-server

폐쇄망 반입용 로컬 서버 도구. 계획·결정 사항은 전부 [PLAN.md](PLAN.md) 에 있다. **작업 전에 PLAN.md 의 해당 장을 읽고, PLAN 에 없는 결정을 새로 내려야 하면 먼저 묻는다.**

## 구역

| 경로 | 무엇 | 규칙 |
|---|---|---|
| `pure/` | portfolio `frontend/public/toolbox` 에서 복사한 순수본. `file://` 로 여는 단일 파일 HTML | **동결.** 여기서 고치지 않는다. 수정은 portfolio 에서 하고 `scripts/sync-pure.sh` 로 끌어온다 |
| `src/main/java/kr/ejg/toolbox/core/` | 메타모델·파서·규칙·리포트 | `web`·`cli`·Javalin 을 import 하지 않는다 |
| `src/main/java/kr/ejg/toolbox/web/` | Javalin 라우트·SSE·정적 파일 | `127.0.0.1` 외 바인드 금지 |
| `src/main/java/kr/ejg/toolbox/cli/` | picocli 명령 | |
| `src/main/resources/tools/` | 백엔드본 HTML + `common.js`. 파일명 영문 | 바닐라 JS. 빌드 없음. CDN 금지 |
| `profiles/` `mappings/` `rules/` `templates/` | 사업별 설정. YAML 이 원본 | 실제 사업 프로필·발주처 양식은 커밋하지 않는다(`.gitignore`). 예시 파일만 |
| `src/test/resources/golden/` | 골든 파일 | 갱신은 diff 를 보고 의도된 변화일 때만 |
| `src/test/resources/fixtures/` | 코드 검사 양성·음성 픽스처 | 규칙 하나당 양성·음성 각 1개 이상 |
| `corpus/` | 실물 표본 레시피 — `SOURCES.md`(출처·태그·라이선스)·`MANIFEST`(지문). 표본 실물은 저장소 밖 `C:/workspace/toolbox-corpus`(PLAN 4장) | 표본 파일을 저장소에 넣지 않는다. 지문은 `corpus-fetch.sh` 만 고친다 |

## 절대 규칙

1. **외부 통신 0.** 런타임에 `127.0.0.1` 밖으로 나가는 호출을 만들지 않는다. 텔레메트리·업데이트 확인·CDN 전부 금지. 예외는 사용자가 지정한 서버 둘뿐 — 프로필의 DB 접속(JDBC), 그리고 사용자가 조회를 눌렀을 때 `svn` 명령이 작업 사본의 저장소 서버에 묻는 것(배포 목록 리비전 구간, 2026-10-02). 자바 프로세스가 여는 소켓은 DB 뿐이다
2. **비밀번호는 메모리만.** 파일·H2·로그에 쓰지 않는다
3. **코드 본문을 저장하지 않는다.** 검사 이력은 `파일·줄·규칙` 까지. 로그에도 사용자 코드·SQL 결과를 남기지 않는다. 예외 하나 — 프로그램 분석의 설명 100자(컨트롤러 메서드 javadoc·블록 주석 첫 문장, `analyze_program.descr`, 2026-10-03 사용자 결정)
4. **순수본 동결.** `pure/` 를 직접 고치면 안 된다
5. **새 의존성은 `m2/` 오프라인 빌드를 깨지 않아야 한다.** 추가했으면 `scripts/offline-build.sh` 를 돌려 확인

## 빌드·실행

```
./mvnw -q package            JDK 17. JAVA_HOME 이 다르면 scripts 가 C:/Program Files/Java/jdk-17.0.19 로 잡는다
run.bat [--port N] [--profile 이름]
./mvnw -q test               Testcontainers 사용. Docker Desktop 이 켜져 있어야 한다
scripts/offline-build.sh     네트워크 없이 m2/ 만으로 빌드되는지
scripts/sync-pure.sh         portfolio 순수본 끌어오기 + 해시 비교
scripts/corpus-fetch.sh      실물 표본 받기(네트워크, 저장소 밖 폴더) + corpus/MANIFEST 갱신
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
- `verify.sh` 와 `git commit`, `verify.sh --full` 과 `git push` 는 **따로 낸다** — 훅이 명령이 돌기 전에 재서 한 체인이면 막힌다
- 청크 사이에 턴을 안 끝낸다. 한 줄 보고 뒤 바로 다음 행
- 다 치면 「마무리」

## 청크 규칙 — 어떻게 자르나

- 청크 하나 = 파일 1~3개 = 커밋 하나. `PLAN.md`·`PROGRESS.md` 는 이 수에 안 넣는다
- 커밋 제목은 `<종류>: <청크번호> <무엇>` — 종류는 `feat`·`fix`·`chore`·`docs`·`test`. 예 `feat: 0-4 서버 기동 + ping`. 본문은 왜가 안 보일 때만
- 서로 독립 — 앞이 미완이어도 뒤를 잡을 수 있게
- 커밋 안 된 작업물을 남긴 채 세션을 끝내지 않는다(Stop 훅이 막는다). 미완이면 `wip/<청크>` 에, 버릴 것이면 `git stash`
- 분할표는 결과, 이력은 서사. 완료 행은 안 고친다

## 마무리 — 번들 끝

1. 독립 리뷰 — `Agent(subagent_type: "caveman:cavecrew-reviewer")`, 없으면 `general-purpose` 에게 `git diff origin/main...HEAD` 를 리뷰시킨다. 지적은 지금 고치거나 새 행으로. 오탐은 근거를 이력에
2. `bash scripts/gate-probe.sh` — 게이트 여덟을 부수면 빨개지는지(약 2분). 「패치 갱신 필요」 는 코드가 바뀌어 `scripts/probes/*.patch` 가 안 맞는 것 — 다시 떠서 커밋한다
3. `bash scripts/verify.sh --full` (push 와 따로)
4. `git push -u origin work/<날짜>` — main 직접 push 는 훅이 막는다
5. `gh pr create --base main` — 본문에 청크마다 시작 세 줄 표. 뒤에 커밋을 얹으면 `gh pr edit N --body-file` 로 표도 같이 고친다(AI 리뷰가 본문을 입력으로 본다)
6. CI 폴링 — 30초 간격으로 `gh api repos/ejg93/toolbox-server/commits/$(git rev-parse HEAD)/check-runs --jq '.check_runs[]|[.name,.status,.conclusion]|@tsv'`. 전부 `completed success` 면 다음. `gh pr checks` 는 안 쓴다(권한 분류기가 막는다). 빨강은 고치기 둘까지
7. 머지 위임이 있으면 `gh pr merge N --merge --delete-branch` → `git checkout main && git pull` → 다음 가지. 없으면 PR 열고 멈춘다
8. 끝 보고 — 계획 밖 결정 전부 · wip/ 로 뺀 행과 이유 · 새로 선 행 · 사람이 할 것

## 검증 도장

`bash scripts/verify.sh` 가 `origin/main` 대비 바뀐 레인(java·tools·docs, `scripts/verify-fingerprint.sh`)만 돌리고 `.git/verify-stamp` 에 「레인 지문 단계」를 적는다.
빠른 도장 = java 는 `db` 태그 뺀 테스트. `--full` = 컨테이너 테스트까지. 청크 닫을 땐 빠른 도장, push 앞엔 full. 빠른 검증은 이미 있는 full 도장을 낮추지 않는다 — 문서만 고친 뒤 `--full` 은 문서 레인만 돈다(0-41). 훅이 본다:

| 훅 | 무엇을 막나 |
|---|---|
| Stop `stop-uncommitted` | 커밋 안 된 작업물이 있으면 턴 끝을 한 번 막는다 |
| Stop `stop-stamp` | HEAD 가 origin/main 과 다른데 도장이 없으면 |
| PreToolUse `commit-guard` | `work/*` 에서 작업 트리 지문 ≠ 도장이면 커밋 금지 |
| PreToolUse `push-guard` | main 직접 push 금지. push 는 full 도장 요구 |
| PreToolUse `pr-guard` | PR base 는 main. 열린 작업 PR 이 있으면 새 PR 금지. 체크 안 끝난 머지 금지 |
| PreToolUse `java-home-guard` | 맨몸 `mvnw` 금지 → `scripts/mvn.sh` |
| Stop·UserPromptSubmit `orphan-reap` | 셸이 죽어 남은 Maven·테스트 JVM 을 끈다 — `mvn.sh` 감시 루프의 뒷문(0-42) |
| PostToolUse `doc-lint-*` | 문서 존댓말 |

컨테이너 테스트는 `@Tag("db")`. Docker 가 꺼져 있으면 `bash scripts/docker-up.sh`.

## 검증

- 변환·계산·SQL 생성 로직은 **골든 파일** JUnit. 실패를 남긴 채 끝내지 않는다
- 화면·클릭·콘솔 에러는 HtmlUnit 스모크(JUnit). Puppeteer 는 집 검증에만 쓰고 저장소 스크립트는 `scripts/puppeteer/` 에. 경로에 한글 금지
- DB 는 Testcontainers. Tibero 는 컨테이너가 없어 골든 파일만 유지
- **실물 표본**(PLAN 4장) — `@Tag("corpus")`. 전체는 불변식, 손 고른 ≤10 만 골든. **등급 A**(코드 파괴 — 컴파일·파싱·실행 실패·왕복 불일치)는 baseline 없이 0 이어야 머지. **등급 B**(덜 함·모양 다름)는 `golden/corpus/` baseline — 새로 깨져도·새로 고쳐져도 빨강, 표본의 2% 넘으면 빨강, 갱신은 diff 를 이력에. 번들당 A 고치기 10건, 넘치면 B 로 내리고 새 행. 로컬 `--full` 은 표본 폴더와 node 가 있어야 한다(없으면 빨강), CI 는 건너뛰고 끝 줄에 센다. 실패 출력은 건수 + 처음 20건
- 돌리지 못했으면 못 돌렸다고 쓴다. 안 돌려보고 「통과」 라고 쓰지 않는다

## 글 작성 규칙

portfolio 루트 CLAUDE.md 「글 작성 규칙」 여섯을 그대로 따른다 — 중복 금지·사견 배제·늘어지지 않게·존댓말 금지·자기 기능만 설명·줄바꿈은 렌더 기준. 도구 설명란 첫 문장에는 `무엇을` `무엇으로` `어떻게` 가 들어간다.

## 화면 스타일

순수본과 같은 CSS 변수(`--bg` `--surface` `--border` `--text` `--muted` `--accent`)·폰트(`'Consolas','D2Coding',monospace`). 모드 배지는 `common.js` 가 붙인다.
