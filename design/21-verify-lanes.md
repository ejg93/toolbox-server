원본은 설계 세션 플랜 파일(`~/.claude/plans/dazzling-wishing-cupcake.md`) — S0 에서 이 파일로 옮겼다(2026-10-10). 실행 세션은 PLAN 분할표 행(0-53a·0-53b)과 이 파일의 4 절만 읽는다.

# 설계 21 — 검증 레인 다섯: 무거운 검증은 DB·파서 변경과 반입 전에만 (Fable, 2026-10-10)

이 플랜은 실행 담당 모델(Opus)이 그대로 따라 실행한다. 실행자는 탐색 과정을 모른다. 판단이 필요한 지점을 남기지 않았다. 번들 모드 규칙(CLAUDE.md 「번들 모드」) 그대로.

사용자가 2026-10-10 정한 것(뒤집지 않는다):
- 무거운 검증(컨테이너 DB 아홉 종 약 17분 · 실물 표본 약 4분)은 **그 코드가 바뀌었을 때와 반입 전 최종에만**. 화면만 고친 번들에 full 20분은 낭비다.
- db 레인은 **DB 에 붙는 코드만** — 메타 수집·접속·SQL 실행·품질 SQL·INSERT·DDL·COMMENT·마스킹·마이그레이션·드라이버 판. 스니펫 글·`pure/`·산출물 xlsx·골든은 **빼서** 반입 전 `--full` 에서만.
- 무거운 레인은 **push 앞에만** 돈다. 청크마다는 빠른 검증(2~3분)만, 끝 줄에 「db 레인 바뀜 — push 앞 `--db`」 알림.
- 레인 목록은 **ArchUnit 시험(`VerifyLanesTest`)이 지킨다** — 자바 패키지 닫힘 + 시험이 부르는 node 스크립트 폴더. 목록이 낡으면 빠른 검증이 빨강.
- 가지: **번들 35 PR 을 먼저 열고**, 그 위에 `work/2026-10-10e` 를 따서 친다. PR 은 #57 머지 뒤(번들 29 꼴).

## Context

번들 35(`work/2026-10-10d`, 화면 결과 칸 통일 — JS·CSS·라우트 둘)를 닫으며 `verify.sh --full` 이 20분 돌았고, 첫 번은 Oracle 컨테이너 중 메모리 부족으로 죽었다. 사용자가 「여태 매번 이렇게 검증했나」를 알고 화냈다. 원인: `verify.sh` 가 바뀐 파일을 레인 셋(java·tools·docs)으로만 가르고, `push-guard` 가 java 레인에 full 도장을 요구해 자바 파일 하나만 바뀌어도 컨테이너 아홉 + 표본이 다 돈다. CI(`verify.yml`)가 push 마다 `mvn verify -DexcludedGroups=corpus` 로 컨테이너 시험(메타 수집 넷·접속·품질 PG·INSERT PG)을 어차피 돌려 로컬 full 은 표본과 `DbCorpusBase`(표본 폴더 필요라 CI 에 없음) 말고는 중복이다. 결과: 레인을 다섯으로 늘리고, push 는 바뀐 레인의 도장만 요구하며, `--full` 은 「전부」로 남겨 반입 전에 손으로 돌린다. 이번 번들처럼 화면만 바뀐 경우 push 앞 검증은 약 3분이 된다.

### 1. 목표와 범위

- **목표**: `verify.sh` 가 레인 다섯(java·db·corpus·tools·docs)을 가르고, push 훅이 **바뀐 레인의 도장만** 요구한다. 화면만 고치면 push 앞 3분, DB 코드를 고치면 `--db` 17분, 파서를 고치면 `--corpus` 4분, 반입 전엔 `--full`. 레인 목록이 낡으면 빠른 검증이 빨강.
- **In scope**: `S0`(번들 35 push·PR → 새 가지 → 문서 행) → `0-53a`(레인·지문·verify 플래그·`VerifyLanesTest`) → `0-53b`(push-guard·package.sh·hooks-test 회귀·CLAUDE.md 글) → 마무리(독립 리뷰·gate-probe·push·PR).
- **Out of scope**: CI 변경(그대로 — db 는 CI 가 push 마다 돈다) · 컨테이너 기동을 줄이는 일(비용은 아홉 번 기동이라 시험을 골라 돌려도 안 줄어듦) · `commit-guard`·`stop-stamp` 의 레인(java·tools·docs 그대로 — 커밋·턴 끝은 빠른 도장만) · 1-58h·5-24·1-59·6-30·6-31(설계 대기 그대로) · 세션 메모리(`heavy-verify-when.md` 갱신은 세션 몫, 저장소 밖).

### 2. 현재 구조 요약(2026-10-10 실측)

#### 2.1 가지·도장
- `work/2026-10-10d` = main `289ba8d` 위 번들 35 커밋 여덟(`bae83f0`…`3a57ab8`). 트리 깨끗. `verify.sh --full` 이 18:0x 에 종료 0 — **S0 첫 줄에서 `.git/verify-stamp` 에 `java <h> full` 이 있는지 확인**(없으면 중단 조건 ①).
- `.git/verify-stamp` 꼴: 한 줄 `<레인> <지문> <단계>`(단계 fast|full). `scripts/verify.sh` 끝 루프가 쓴다(0-41 — 이미 full 인 지문은 fast 로 안 낮춤).

#### 2.2 지문·검증·훅(건드리는 자리)
- `scripts/verify-fingerprint.sh`(42줄) — 트리 하나의 레인별 지문. `listed=(…)` 경로를 `git cat-file --batch-check` 로 한 번에 풀고 `lane <이름> <경로…>` 함수가 「경로 해시」 글을 `git hash-object` 로 접는다. 지금 `lane java src pom.xml .mvn mvnw mvnw.cmd config templates mappings` · `lane tools scripts .claude/settings.json .claude/prompts .github` · `lane docs CLAUDE.md PLAN.md PROGRESS.md README.md`. 경로는 폴더든 파일이든 된다(`tree:path` 가 blob 이면 blob 해시). 없는 경로는 `-`. **주석에 「경로를 더할 때 여기 한 곳만 고친다」** — 새 파일(`lanes.txt`)을 안 만들고 여기에 둔다.
- `scripts/verify.sh`(78줄) — `level=fast|full`(`--full` 하나). 작업 트리 지문 vs `origin/main` 으로 `changed <레인>`, `stamped <레인>`(요청 단계 이상 도장). java: full 이면 `corpus-check.sh` 뒤 `mvn verify`(CI 면 `-DexcludedGroups=corpus`), fast 면 `mvn -DexcludedGroups=db,corpus test`. 뒤에 `migration-immutable.sh`. tools: `bash -n`·settings.json 파싱·`hooks-test.sh`. docs: `doc-lint.sh`. 끝 루프가 `for d in java tools docs` 로 도장을 다시 쓴다(안 바뀐 레인은 `full`, 옛 full 유지).
- `scripts/hooks/push-guard.sh` — `git push` 글자를 잡고 main 금지 뒤, `for d in java tools docs` 로 HEAD 지문이 main 과 다른 레인마다 `<레인> <h> full` 줄을 요구. 없으면 exit 2 「full 도장이 없다:<레인> — …」.
- `scripts/hooks/commit-guard.sh`·`stop-stamp.sh` — 같은 `java tools docs` 루프, 단계 무관(어느 도장이든). **안 고친다.**
- `scripts/hooks-test.sh` — `mkrepo`(임시 저장소: origin/main + `work/x` 에 `src/A.java` 한 줄 → java 레인만 바뀜, `verify-fingerprint.sh` 만 복사) · `stamp_head <단계>` · `stamp_worktree` · `case_ 이름 훅 기대exit stdin [stderr머리]`. push-guard 케이스: 34·35(main 금지) · 36~37(`stamp_head fast` → exit 2 「full 도장이 없다」) · 38~39(`stamp_head full` → 0) · 41(main 가지) · 43(ls). tools 레인에서 돈다(CI 도).
- `scripts/corpus-check.sh` — 표본 폴더(`$TOOLBOX_CORPUS` 또는 `../toolbox-corpus`) 있나. CI 면 건너뜀 메시지 + 0.
- `scripts/package.sh` — ① 앞 검사(깨끗한 트리·`--with`·`bundle-fetch --check`·`offline-build --check`) → ② jar → ③ 무대 → ④ MANIFEST → `package-check.sh`. **도장은 안 본다.**
- `.github/workflows/verify.yml` — push 마다 `./mvnw -B -q -DexcludedGroups=corpus verify` + tools·docs 레인. **그대로.**
- `pom.xml` surefire 3.5.2(`argLine -Xmx3g`, `groups` 설정 없음 — 명령줄 `-Dgroups`·`-DexcludedGroups` 가 JUnit 태그 식으로 먹는다) · ArchUnit 1.4.1(test).

#### 2.3 시험 꼬리표(실측)
- `@Tag("db")`: `core/conn/ConnectionRegistryPostgresTest` · `core/dialect/{Maria,Mssql,Oracle,Postgres}MetaSourceTest` · `core/meta/JdbcMetaSourcePostgresTest` · `core/quality/QualitySqlPostgresTest` · `core/gen/InsertGenPostgresTest` · `web/DbCorpusBase`(**db·corpus 둘 다** — 상속한 아홉 `MariaCorpusTest`·`Mssql2017`·`Mssql`·`Mysql57`·`Mysql80`·`Oracle11`·`Oracle`·`Postgres12`·`Postgres` 가 꼬리표를 물려받는다. 절 순서 접속 실패→DDL→INSERT→적재→메타→COMMENT→diff→품질→스니펫→DDL 왕복→마스킹→산출물).
- `@Tag("corpus")`(db 아닌 것): `cli/CliCorpusTest` · `core/analyze/AnalyzeCorpusTest` · `core/check/{CheckCorpusTest,PureCodeCheckTest}` · `core/fs/{FolderDiff,LocalFiles}CorpusTest` · `core/gen/{Ddl,Generator,GeneratorJavac}CorpusTest` · `core/logical/{Logical,Masking}CorpusTest` · `core/meta/HrSnapshotCorpusTest` · `core/text/{Csv,LogSql}CorpusTest` · `core/vcs/VcsCorpusTest` · `web/{Js,JspFmt,Strip}CorpusTest` · `CorpusFilesTest`.
- 시험이 부르는 node 스크립트(전부 `scripts/puppeteer/`): `CorpusNode.run("corpus-snippets.js"|"corpus-js.js"|"corpus-jspfmt.js"|"corpus-strip.js")`(`web/CorpusNode.java` 35줄이 `"scripts/puppeteer/" + script`) · `PureCodeCheckTest` `"scripts/puppeteer/corpus-check-pure.js"` · `LogicalCorpusTest` `"scripts/puppeteer/dump-logicalname.js"`. Puppeteer 는 `C:/workspace/node_modules/puppeteer`.
- 본 코드 패키지 의존(import 실측): `meta→conn,db,job,profile` · `dialect→meta` · `conn→profile` · `gen→fs,job,meta,text` · `logical→dict,meta,text` · `deliverable→analyze,dict,gen,job,logical,meta,report,sqlrun` · `report→deliverable,sqlrun` · `analyze→check,db,fs,job,meta,profile,text` · `check→db,fs,job,profile,text` · `dict→db,text` · `profile→meta` · `vcs→text` · `quality`·`sqlrun`·`db`·`fs`·`job`·`text`→없음. `web`·`cli` 는 거의 전부에 닿는다(접착제).
- 함정: `DbCorpusBase` 는 `web` 패키지에서 `App.start` 를 **같은 패키지 참조**로 쓴다(import 줄 없음) — import 글자 긁기는 못 본다. ArchUnit 은 바이트코드라 본다. `App` 을 지나면 전부에 닿으므로 닫힘은 `core` 안에서만 따라간다.
- `ArchitectureTest`(`src/test/java/kr/ejg/toolbox/`) 가 `new ClassFileImporter().withImportOption(DO_NOT_INCLUDE_TESTS).importPackages("kr.ejg.toolbox")` 꼴 — 같은 자리에 `VerifyLanesTest` 를 둔다(시험 포함해서 import).
- 시험 시간(이번 full 실측): `CliCorpusTest` 54초 · `CheckCorpusTest` 40초 · `AnalyzeCorpusTest` 27초 · `OracleMetaSourceTest` 21초 · `LocalFilesCorpusTest` 11초. 컨테이너 기동이 레인 시간의 대부분.

### 3. 설계 결정

- **[고정] 레인 다섯, 경로 목록은 `verify-fingerprint.sh` 한 곳.** 레인은 겹친다(`core/meta` 는 java 와 db 둘 다) — 레인마다 도장이 따로라 괜찮다. 새 파일(`lanes.txt`)은 안 만든다(버린 이유: 「경로는 여기 한 곳」 주석과 `hooks-test` 가 그 스크립트 하나만 복사한다).
- **[고정] db 레인 = DB 에 붙는 코드만**(사용자). 자바: `core/{meta,dialect,conn,sqlrun,quality,gen,logical}`. 비자바: `src/main/resources/{db,conn,quality,gen,logical}` · `pom.xml` · `.mvn` · db 시험 소스(`src/test/java/kr/ejg/toolbox/core/{conn,dialect,meta,quality}` · `core/gen/InsertGenPostgresTest.java` · `web/DbCorpusBase.java` · `DbCorpus.java` · `CorpusHr.java`). **제외**(`LANE_DB_SKIP`): `core/{db,job,profile,fs,text,dict,deliverable,report,analyze,check,vcs}` — 공용 바닥·산출물·파서. 스니펫 글(`pure/`·`tools/sql_snippets.html`·`corpus-snippets.js`)·골든도 뺀다 → 반입 전 `--full`.
- **[고정] corpus 레인 = 파서·규칙·표본 입력.** 자바: `core/{analyze,check,text,vcs,fs,gen,logical,dict,deliverable,meta,dialect,sqlrun,quality}` + `cli` + `web`? — **아니다**: `web`·`cli` 는 접착제라 안 넣는다(CLI 표본 `CliCorpusTest` 가 `cli` 를 쓰지만 cli 는 core 호출만 — cli 변경은 빠른 시험 `*CliTest` 가 잰다). 비자바: `pure` · `scripts/puppeteer` · `corpus/MANIFEST` · `src/test/resources/golden/corpus` · `src/test/resources/sample` · `src/main/resources/{check,gen,logical,dict,tools}` · corpus 시험 소스(2.3 목록의 폴더·파일). **제외**(`LANE_CORPUS_SKIP`): `core/{db,job,profile}`. `src/main/resources/tools` 가 들어 화면 JS 를 고치면 corpus 레인(약 4분)이 깬다 — `JsCorpusTest`·`StripCorpusTest`·`JspFmtCorpusTest` 가 백엔드본 화면 JS 를 표본에 돌리므로 맞다.
- **[고정] 도장 단계**: java·tools·docs 는 지금처럼 fast|full. db·corpus 는 `full` 하나(돌았다/안 돌았다). `--full` 은 「전부」(지금 `mvn verify`) 그대로 두고 java·db·corpus 셋에 full 을 찍는다.
- **[고정] push 는 바뀐 레인의 도장만**: java·tools·docs 는 어느 단계든 HEAD 지문 줄이 있으면 됨(CI 가 `mvn verify` 로 SpotBugs·컨테이너를 돈다), db·corpus 는 바뀌었으면 `full` 줄. 버린 대안: java 에 full 유지 — 그게 지금의 20분이다.
- **[고정] 무거운 레인은 알림만, 자동 실행 없음**(사용자 「push 앞에만」). `verify.sh`(플래그 없음)는 db·corpus 가 바뀌었고 도장이 없으면 끝에 한 줄씩 찍되 초록으로 끝낸다. 돌리는 건 `--db`·`--corpus`(둘 같이 가능)·`--full`.
- **[고정] `VerifyLanesTest` 가 목록을 지킨다** — ① db·corpus 꼬리표 시험(상속 포함)이 직접 쓰는 `core` 클래스에서 출발, `core` 안에서만 참조를 따라가되 SKIP 패키지는 **들어가지 않는다**(deliverable 을 건너뛰면 그 뒤 analyze 도 안 닿는다) → 닿는 패키지 폴더가 전부 `lane db` 줄에 있어야 ② 꼬리표 시험의 소스 파일이 그 레인 목록에 들어 있어야 ③ 시험 소스가 부르는 node 스크립트(`CorpusNode.run("…js")` · `"scripts/…js"` 글자)의 폴더가 corpus 레인에 있어야. 실패 글은 「`<경로>` 를 <레인> 레인에 더하라(또는 `LANE_<레인>_SKIP` 에)」. 버린 대안: 손 목록 + CI — corpus 는 CI 에 없어 낡아도 반입 전까지 모른다.
- **[고정] `package.sh` 가 반입 zip 앞에 full 도장(java·db·corpus, HEAD)을 요구한다** — 「반입 전 한 번」을 강제 지점으로.
- **[고정] CI·`commit-guard`·`stop-stamp` 는 안 고친다.**
- 시간 어림(실측 기반): 화면만 → 빠른 3분 · 파서 → +corpus 약 4~5분 · DB 코드 → +db 약 17분(DbCorpusBase 아홉 포함, 표본 폴더 필요) · 반입 전 `--full` 약 20분.

### 4. 실행 스텝

청크 = 커밋 하나. `verify.sh` 와 `git commit` 은 따로 낸다(훅). 커밋 제목 `chore: 0-53a …` 꼴, 끝에 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

#### S0. 번들 35 매듭 → 새 가지 → 문서 행
- 대상: 가지 둘, `PLAN.md`·`PROGRESS.md`·`design/21-verify-lanes.md`.
- 변경:
  1. `cat .git/verify-stamp` — `java <h> full` 이 있어야(2.1). `git status` 깨끗.
  2. `git push -u origin work/2026-10-10d` → `gh pr create --base main --title "저장 표시 통일 — 번들 35(1-58a~g)" --body-file <파일>` — 본문은 PROGRESS 이력의 번들 35 줄 여덟을 청크 표(행·커밋·무엇)로 + 리뷰 처분 요약 + 끝에 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`. **CI 폴링·머지는 안 한다**(머지 위임 없음). PR 번호를 적어 둔다(=N).
  3. `git checkout -b work/2026-10-10e`(번들 35 HEAD 위).
  4. 이 플랜 파일을 `design/21-verify-lanes.md` 로 복사(맨 위 한 줄 「원본은 설계 세션 플랜 파일 — S0 에서 옮김」). `PLAN.md` 「번들」 표에 번들 36 행(`S0`→`0-53a`→`0-53b`, 설계 21, 머지 위임 없음 — PR 은 #N 머지 뒤) · 분할표에 `0-53a`·`0-53b` 행(아래 스텝을 요약, 선행 `0-41`, 상태 「대기」) — 0-52 행 바로 뒤. `PROGRESS.md` 「현재 상태」 가지를 `work/2026-10-10e`, 진행중 청크를 「번들 36 S0」로; 이력 한 줄 「번들 35 PR #N 열림 · 번들 36 시작」.
- 검증: `bash scripts/verify.sh` 초록(docs 레인만 돈다) → `git commit -m "docs: 0-53 설계 21 — 검증 레인 다섯 · 번들 36 행"`.
- 의존: 없음.

#### 0-53a. 레인·지문·verify 플래그·`VerifyLanesTest`
- 대상: `scripts/verify-fingerprint.sh` · `scripts/verify.sh` · `src/test/java/kr/ejg/toolbox/VerifyLanesTest.java`(신설).
- 변경 ①: `verify-fingerprint.sh` — `listed` 에 새 경로를 더하고(아래 전부) `lane` 두 줄 + SKIP 변수 두 줄을 **한 줄씩** 둔다(`VerifyLanesTest` 가 정규식 `^lane (db|corpus) (.+)$` · `^LANE_(DB|CORPUS)_SKIP="(.*)"$` 로 읽는다 — 줄 이음 금지, 주석은 줄 위에):

  ```bash
  # db 레인(설계 21) — DB 에 붙는 코드만. 바뀌면 push 앞 `verify.sh --db`(컨테이너 아홉, 약 17분). 스니펫 글·pure·골든·산출물은 뺀다 → 반입 전 --full.
  # VerifyLanesTest 가 db 꼬리표 시험의 core 닫힘(SKIP 제외)이 이 줄에 다 있는지 잰다. 경로를 더할 땐 listed 에도.
  LANE_DB_SKIP="core/db core/job core/profile core/fs core/text core/dict core/deliverable core/report core/analyze core/check core/vcs"
  lane db src/main/java/kr/ejg/toolbox/core/meta src/main/java/kr/ejg/toolbox/core/dialect src/main/java/kr/ejg/toolbox/core/conn src/main/java/kr/ejg/toolbox/core/sqlrun src/main/java/kr/ejg/toolbox/core/quality src/main/java/kr/ejg/toolbox/core/gen src/main/java/kr/ejg/toolbox/core/logical src/main/resources/db src/main/resources/conn src/main/resources/quality src/main/resources/gen src/main/resources/logical pom.xml .mvn src/test/java/kr/ejg/toolbox/core/conn src/test/java/kr/ejg/toolbox/core/dialect src/test/java/kr/ejg/toolbox/core/meta src/test/java/kr/ejg/toolbox/core/quality src/test/java/kr/ejg/toolbox/core/gen/InsertGenPostgresTest.java src/test/java/kr/ejg/toolbox/web/DbCorpusBase.java src/test/java/kr/ejg/toolbox/DbCorpus.java src/test/java/kr/ejg/toolbox/CorpusHr.java
  # corpus 레인(설계 21) — 파서·규칙·표본 입력. 바뀌면 push 앞 `verify.sh --corpus`(약 4분). web·cli 는 접착제라 안 넣는다.
  LANE_CORPUS_SKIP="core/db core/job core/profile"
  lane corpus src/main/java/kr/ejg/toolbox/core/analyze src/main/java/kr/ejg/toolbox/core/check src/main/java/kr/ejg/toolbox/core/text src/main/java/kr/ejg/toolbox/core/vcs src/main/java/kr/ejg/toolbox/core/fs src/main/java/kr/ejg/toolbox/core/gen src/main/java/kr/ejg/toolbox/core/logical src/main/java/kr/ejg/toolbox/core/dict src/main/java/kr/ejg/toolbox/core/deliverable src/main/java/kr/ejg/toolbox/core/meta src/main/java/kr/ejg/toolbox/core/dialect src/main/java/kr/ejg/toolbox/core/sqlrun src/main/java/kr/ejg/toolbox/core/quality src/main/resources/check src/main/resources/gen src/main/resources/logical src/main/resources/dict src/main/resources/tools pure scripts/puppeteer corpus/MANIFEST src/test/resources/golden/corpus src/test/resources/sample src/test/java/kr/ejg/toolbox/cli/CliCorpusTest.java src/test/java/kr/ejg/toolbox/core/analyze src/test/java/kr/ejg/toolbox/core/check src/test/java/kr/ejg/toolbox/core/fs src/test/java/kr/ejg/toolbox/core/gen src/test/java/kr/ejg/toolbox/core/logical src/test/java/kr/ejg/toolbox/core/meta src/test/java/kr/ejg/toolbox/core/text src/test/java/kr/ejg/toolbox/core/vcs src/test/java/kr/ejg/toolbox/web/CorpusNode.java src/test/java/kr/ejg/toolbox/web/JsCorpusTest.java src/test/java/kr/ejg/toolbox/web/JspFmtCorpusTest.java src/test/java/kr/ejg/toolbox/web/StripCorpusTest.java src/test/java/kr/ejg/toolbox/web/DbCorpusBase.java src/test/java/kr/ejg/toolbox/CorpusFiles.java src/test/java/kr/ejg/toolbox/CorpusFilesTest.java src/test/java/kr/ejg/toolbox/DbCorpus.java src/test/java/kr/ejg/toolbox/CorpusHr.java
  ```
  `core/gen/InsertGenPostgresTest.java` 처럼 파일 하나만 넣는 자리는 `lane` 이 `entry` 에 없으면 `git rev-parse tree:path` 로 푼다(이미 그렇다). **`VerifyLanesTest` 가 처음 돌 때 찍는 닫힘 집합이 위 자바 일곱(db)·열셋(corpus)과 다르면 그 집합을 따른다**(SKIP 에 없는 새 패키지가 나오면 중단 조건 ④가 아니라 — 그 패키지를 lane 줄에 더한다. 단 web·cli 는 절대 안 더한다 — 닫힘이 web 에 닿았다면 시험이 `core` 밖을 따라간 것이니 시험을 고친다).
- 변경 ②: `verify.sh` — 플래그를 셋으로. 인자 루프 `--full → level=full` · `--db → want_db=1` · `--corpus → want_corpus=1`(둘 다 가능). `fp_of`·`changed`·`stamped` 는 레인 이름을 받으니 그대로. java 블록은 그대로(full 이면 `mvn verify` 전부). 그 뒤에 두 블록:

  ```bash
  heavy() { # 레인 그룹식 분
    local lane=$1 expr=$2 minutes=$3
    if changed "$lane" && ! grep -qx "$lane $(fp_of "$lane") full" "$st" 2>/dev/null; then
      if [ "$level" = full ]; then :   # mvn verify 가 전부 돌았다 — 끝 루프가 full 을 찍는다
      elif [ "$(eval echo \$want_$lane)" = 1 ]; then
        echo "== $lane 레인 바뀜 → mvn test -Dgroups='$expr' (약 $minutes분)"
        bash scripts/corpus-check.sh && bash scripts/mvn.sh -q -B -Dgroups="$expr" test || fail=1
        eval "ran_$lane=1"
      else
        pending="$pending $lane"
      fi
    fi
  }
  heavy db "db" 17
  heavy corpus "corpus & !db" 4
  ```
  `--db`·`--corpus` 만 주고 java 가 안 바뀌었거나 이미 도장이면 java 블록은 지금처럼 건너뛴다. 끝 도장 루프를 `for d in java tools docs db corpus` 로 넓히되 db·corpus 는: 안 바뀜 → `full` · 옛 줄이 `full` 이고 지문 같음 → 유지 · 이번에 돌았거나(`ran_<레인>=1`) `level=full` 이고 java 가 돌았으면 → `full` · 그 밖(알림만) → **줄을 안 쓴다**(없음 = 안 돌았다). 끝 줄: `pending` 이 비어 있지 않으면 초록 줄 **앞에** 레인마다 `== <레인> 레인 바뀜 — push 앞에 bash scripts/verify.sh --<레인>(약 n분). 전부 돌리려면 --full` 을 찍는다. 종료 코드는 그대로(알림은 빨강이 아니다). 머리 주석의 사용법 넷 줄도 고친다.
- 변경 ③: `VerifyLanesTest.java` — `ArchitectureTest` 옆. 뼈대:

  ```java
  /** 설계 21 — db·corpus 레인 경로 목록(scripts/verify-fingerprint.sh)이 꼬리표 시험의 실제 의존과 맞나. 낡으면 빠른 검증이 빨강 */
  class VerifyLanesTest {
      static final Path SCRIPT = Path.of("scripts/verify-fingerprint.sh");
      static final String ROOT = "kr.ejg.toolbox.";
      static JavaClasses all; // 시험 포함 — new ClassFileImporter().importPackages("kr.ejg.toolbox")

      record Lane(String name, List<String> paths, Set<String> skip) {}   // skip 은 "core/db" 꼴
      static Lane lane(String name) { /* 정규식 ^lane <name> (.+)$ · ^LANE_<NAME>_SKIP="(.*)"$ — 한 줄씩. 없으면 fail("lane <name> 줄") */ }

      static boolean tagged(JavaClass c, String tag) { /* c 와 getAllRawSuperclasses() 의 @Tag(value) · @Tags 안의 @Tag 를 본다 */ }
      static boolean isTest(JavaClass c) { return c.getSource().map(s -> s.getUri().toString().contains("/test-classes/")).orElse(false); }
      static String pkgPath(JavaClass c) { return c.getPackageName().substring(ROOT.length()).replace('.', '/'); } // "core/meta"
      static Set<String> closure(String tag, Set<String> skip) {
          // 씨앗: tagged(c, tag) 인 시험 클래스가 직접 쓰는(getDirectDependenciesFromSelf) core 클래스 중 skip 밖
          // BFS: core 클래스의 getDirectDependenciesFromSelf 를 따라가되 target 이 core 밖(web·cli·java.*)이거나 skip 패키지면 안 들어간다
          // 반환: 닿은 패키지의 pkgPath 집합
      }
      static boolean covered(List<String> paths, String rel) { return paths.stream().anyMatch(p -> rel.equals(p) || rel.startsWith(p + "/")); }

      @Test void dbLaneCoversDbTests()  { check("db"); }
      @Test void corpusLaneCoversCorpusTests() { check("corpus"); }
      void check(String tag) {
          Lane l = lane(tag);
          List<String> bad = new ArrayList<>();
          for (String pkg : closure(tag, l.skip())) if (!covered(l.paths(), "src/main/java/kr/ejg/toolbox/" + pkg)) bad.add("패키지 " + pkg);
          for (JavaClass t : all) if (isTest(t) && tagged(t, tag) && !t.isInnerClass()) {   // ② 시험 소스 자체
              String src = "src/test/java/" + t.getName().replace('.', '/') + ".java";
              if (!covered(l.paths(), src)) bad.add("시험 " + src);
          }
          assertEquals(List.of(), bad, tag + " 레인(scripts/verify-fingerprint.sh 의 lane " + tag + ") 에 더하라 — 또는 LANE_" + tag.toUpperCase() + "_SKIP 에");
      }

      /** ③ 시험이 부르는 node 스크립트 폴더는 corpus 레인에 */
      @Test void nodeScriptsUnderCorpusLane() throws IOException {
          // src/test/java 를 걸어 "scripts/<폴더>/…js" 와 CorpusNode.run("<이름>.js") 글자를 모은다. CorpusNode 의 접두 "scripts/puppeteer/" 로 run 이름을 경로로.
          // 각 경로의 부모 폴더가 covered(lane("corpus").paths(), 폴더) 여야. 파일이 실제로 있어야(없으면 「스크립트 없음」)
      }
  }
  ```
  `web/DbCorpusBase` 가 꼬리표 둘이라 db·corpus 둘 다에서 ②에 걸린다 — 양쪽 목록에 넣었다. 내부 클래스(`$`)는 ②에서 뺀다. 씨앗이 `core` 밖 클래스만 쓰면(예: `web` 만) 그 시험은 닫힘에 기여 안 한다 — 괜찮다(②가 소스 파일은 잡는다).
- 검증: ① `bash scripts/mvn.sh -q -B -DexcludedGroups=db,corpus test -Dtest=VerifyLanesTest -Dsurefire.failIfNoSpecifiedTests=false` 초록(처음 빨강이면 실패 글의 경로를 lane 줄에 더한다 — web·cli 제외) ② 꼬리표 식 확인 `bash scripts/mvn.sh -q -B test -Dgroups='corpus & !db' -Dtest=CheckCorpusTest -Dsurefire.failIfNoSpecifiedTests=false` 가 그 시험을 돌린다(surefire 보고 `target/surefire-reports/TEST-…CheckCorpusTest.xml` 생김) · `-Dgroups=db -Dtest=PostgresMetaSourceTest` 가 PG 컨테이너 하나로 돈다(2~3분) ③ `bash scripts/verify-fingerprint.sh HEAD` 가 다섯 줄(`java tools docs db corpus`) ④ `bash scripts/verify.sh` — 끝에 「db 레인 바뀜 — push 앞 …」 가 **안** 찍히고(이 청크는 db·corpus 경로를 안 건드린다 — `pom.xml` 그대로) 초록 ⑤ `bash scripts/verify.sh --corpus` 한 번(약 4~5분, 표본 폴더·node 필요) — 도장에 `corpus <h> full` ⑥ 커밋 `chore: 0-53a 검증 레인 다섯 — db·corpus 지문 · verify 플래그 · VerifyLanesTest`.
- 의존: S0.

#### 0-53b. push-guard · package.sh · hooks-test · 글
- 대상: `scripts/hooks/push-guard.sh` · `scripts/package.sh` · `scripts/hooks-test.sh` · 문서(`CLAUDE.md`·`README.md` — 수에 안 넣음).
- 변경 ①: `push-guard.sh` 루프를

  ```bash
  fail=''
  for d in java tools docs; do   # 빠른 도장이면 된다 — CI 가 push 마다 mvn verify(컨테이너·SpotBugs)를 돈다(설계 21)
    h=$(echo "$head" | grep "^$d "); [ "$h" = "$(echo "$main" | grep "^$d ")" ] && continue
    grep -q "^$h " "$st" 2>/dev/null || fail="$fail $d"
  done
  heavy=''
  for d in db corpus; do          # 바뀐 무거운 레인은 돌았어야(full)
    h=$(echo "$head" | grep "^$d "); [ "$h" = "$(echo "$main" | grep "^$d ")" ] && continue
    grep -qx "$h full" "$st" 2>/dev/null || heavy="$heavy $d"
  done
  [ -n "$fail" ] && { echo "검증 도장이 없다:$fail — push 앞엔 bash scripts/verify.sh 가 HEAD 에서 초록이어야 한다." >&2; exit 2; }
  [ -n "$heavy" ] && { echo "무거운 레인이 바뀌었는데 안 돌았다:$heavy — bash scripts/verify.sh$(for d in $heavy; do printf ' --%s' "$d"; done) (db 약 17분 · corpus 약 4분, 표본 폴더 필요). 반입 전엔 --full." >&2; exit 2; }
  ```
  머리 주석 「full 도장」 → 「바뀐 레인의 도장 — db·corpus 는 돌았어야」.
- 변경 ②: `package.sh` ① 앞 검사 끝에:

  ```bash
  st="$(git rev-parse --git-dir)/verify-stamp"; fp=$(bash scripts/verify-fingerprint.sh HEAD); miss=''
  for d in java db corpus; do grep -qx "$(echo "$fp" | grep "^$d ") full" "$st" 2>/dev/null || miss="$miss $d"; done
  [ -z "$miss" ] || { echo "[빨강] 반입 zip 은 전부 검증 뒤 —$miss 에 full 도장이 없다. bash scripts/verify.sh --full(약 20분) 을 HEAD 에서"; exit 1; }
  ```
- 변경 ③: `hooks-test.sh` — `mkrepo` 를 그대로 두고 `mkrepo_db` 를 더한다(mkrepo 뒤 `work/x` 에 `src/main/java/kr/ejg/toolbox/core/meta/M.java` 한 줄 커밋 → java·db 레인 바뀜; 임시 저장소엔 `verify-fingerprint.sh` 만 있어 lane 줄의 다른 경로는 `-`). 케이스: 36~37 「work 가지, fast 도장뿐」 기대 exit **0**(새 규칙) · 새 「db 레인 바뀜, java fast 만」 exit 2 stderr 「무거운 레인이 바뀌었는데 안 돌았다」 · 새 「db 레인 바뀜, db full 도장」 exit 0(`stamp_head full` — 모든 레인 full) · 새 「도장 비움」 exit 2 「검증 도장이 없다」 · `package.sh` 는 hooks-test 범위 밖(閉 ④).
- 변경 ④: 글 — `CLAUDE.md` 「빌드·실행」 표 `./mvnw -q test` 줄 아래 `bash scripts/verify.sh [--db] [--corpus] [--full]` 한 줄 · 「번들 모드」 넷째 점 「`verify.sh --full` 과 `git push` 는 따로」 → 「`verify.sh --db`·`--corpus` 와 `git push` 는 따로」 · 「마무리」 3 「`bash scripts/verify.sh --full` (push 와 따로)」 → 「`bash scripts/verify.sh` 끝 줄이 알려 주는 무거운 레인만(`--db`·`--corpus`). `--full` 은 반입 전」 · 「검증 도장」 절 두 문단을 레인 다섯·단계·「push 는 바뀐 레인의 도장만 — java·tools·docs 는 빠른, db·corpus 는 돌았어야」·「`--full` = 전부, 반입 전·`package.sh` 가 요구」 로 다시 쓰고 표의 push-guard 줄 「push 는 full 도장 요구」 → 「바뀐 db·corpus 레인은 돌았어야」 · 「검증」 절 실물 표본 줄 「로컬 `--full` 은 표본 폴더와 node 가 있어야」 → 「`--corpus`·`--db`·`--full` 은 …」. `README.md` 에 verify 언급이 있으면 같은 꼴로(실측: 없음 — 그러면 안 건드린다). 존댓말 금지(doc-lint).
- 검증: ① `bash scripts/hooks-test.sh` 전부 통과 ② `bash scripts/verify.sh` 초록(tools·docs) ③ 손 확인 — `git stash` 없이: `.git/verify-stamp` 를 복사해 두고 db 줄을 지운 뒤 `bash scripts/hooks/push-guard.sh` 에 `{"tool_input":{"command":"git push"}}` 를 stdin 으로 → exit 0(이 가지는 db 레인이 안 바뀜) · 도장 원복 ④ `bash scripts/package.sh` 를 도장 없는 상태로 돌리면 ① 단계에서 「[빨강] 반입 zip 은 전부 검증 뒤」(`bundle-fetch --check` 앞이 아니라 뒤에 두어도 된다 — jre 가 없으면 그 줄이 먼저 빨갛다. 그러면 메시지만 눈으로) ⑤ 커밋 `chore: 0-53b push 는 바뀐 레인 도장만 · package.sh full 요구 · 훅 회귀 · 글`.
- 의존: 0-53a.

#### 마무리(CLAUDE.md 「마무리」 그대로, 바뀐 것만)
1. 독립 리뷰(`caveman:cavecrew-reviewer`) `git diff origin/main...HEAD` — 번들 35 커밋이 섞여 보이니 **`git diff <PR N 의 HEAD>...HEAD`** 로.
2. `bash scripts/gate-probe.sh` — 「패치 갱신 필요」면 다시 떠서 커밋.
3. `bash scripts/verify.sh` — 끝 줄에 무거운 레인 알림이 **없어야**(이 번들은 db·corpus 경로를 안 건드렸다 — 0-53a 의 `--corpus` 도장은 보너스). 알림이 있으면 그 레인을 돌린다.
4. `git push -u origin work/2026-10-10e`(push-guard 가 새 규칙으로 통과해야 — 통과 자체가 0-53b 의 실측).
5. PR 은 **#N 머지 뒤**(pr-guard 가 열린 작업 PR 이 있으면 새 PR 을 막는다) — 사람이 #N 을 머지하면 `git checkout main && git pull && git checkout work/2026-10-10e && git rebase main` 뒤 `gh pr create --base main`. 머지 전이면 멈추고 보고.
6. PROGRESS 이력 두 줄(0-53a·0-53b) + 마무리 줄 · 분할표 상태 「완료」 · 번들 36 상태.

### 5. 금지 사항

- `.github/workflows/*` · `scripts/hooks/commit-guard.sh` · `stop-stamp.sh` · `corpus-check.sh` · `pom.xml`(의존성·surefire 설정) · `pure/` · `src/main/**`(본 코드 0줄).
- 새 파일은 `VerifyLanesTest.java`·`design/21-verify-lanes.md` 둘뿐. `lanes.txt` 같은 목록 파일 금지.
- `lane db`·`lane corpus` 에 `web`·`cli`·`src`(통째)·`src/main/java/kr/ejg/toolbox/core`(통째) 금지 — 넣으면 뜻이 없어진다.
- 시험을 느슨하게 해서 초록 만들기(SKIP 에 패키지를 넣어 빨강을 지우는 것 포함 — SKIP 은 이 플랜의 두 목록 그대로).
- `verify.sh --full` 을 이 번들 안에서 돌리지 않는다(사용자 2026-10-10 — 이번 변경은 db·corpus 코드가 아니다).
- 메모리(`~/.claude/projects/...memory`)는 실행 세션이 안 건드린다.

### 6. 최종 검증

- `bash scripts/hooks-test.sh` 전부 통과 · `bash scripts/verify.sh` 초록이고 끝에 무거운 레인 알림 없음 · `bash scripts/verify-fingerprint.sh HEAD` 다섯 줄 · `.git/verify-stamp` 에 `db`·`corpus` 줄(안 바뀐 레인은 `full`).
- `VerifyLanesTest` 가 빠른 검증에 포함되어 초록(`-DexcludedGroups=db,corpus` 로도 돈다 — 꼬리표 없음).
- 부정 실측 하나(이력에 적는다): `verify-fingerprint.sh` 의 `lane db` 줄에서 `…/core/meta` 를 잠시 지우고 `-Dtest=VerifyLanesTest` → 빨강 「패키지 core/meta」 → 원복.
- 회귀: 번들 35 와 같은 꼴(화면 JS 만 바뀐 가지)에서 push 앞 요구가 빠른 도장 + corpus(tools 리소스가 corpus 레인이라) 뿐인지 — `hooks-test` 케이스가 대신 잰다.
- 사람이 볼 것: PR #N 머지 뒤 이 가지 PR 의 CI 초록(CI 는 안 바뀌었다).

### 7. 중단 조건

다음이면 임의로 풀지 말고 멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- ① S0 에서 `.git/verify-stamp` 에 `java <HEAD 지문> full` 이 없다(full 이 돌았는데 도장이 fast 다).
- ② surefire 가 `-Dgroups='corpus & !db'` 식을 못 먹는다(0-53a 검증 ②에서 CheckCorpusTest 가 안 돌거나 DbCorpusBase 계열이 같이 돈다) — 선택지: `-DexcludedGroups=db -Dgroups=corpus` 둘 같이.
- ③ `VerifyLanesTest` 닫힘이 `web`·`cli` 에 닿는다(BFS 가 core 밖으로 샌다 — 시험 버그) 또는 ArchUnit 이 `@Tags` 안의 `@Tag` 를 못 읽어 DbCorpusBase 계열을 못 찾는다(두 번 고쳐도).
- ④ 닫힘 집합에 SKIP 에도 lane 줄에도 없는 패키지가 **둘 넘게** 나온다(하나·둘은 lane 줄에 더한다 — 3 절 결정).
- ⑤ 검증이 2회 연속 빨갛고 원인이 이 플랜 밖(예: 표본 폴더·node 없음, Docker 꺼짐 → `bash scripts/docker-up.sh` 는 해도 된다).
- ⑥ 스텝에 없는 파일을 3개 이상 고쳐야 한다.

### 8. 불확실 항목(첫 스텝 전에 확인)

- `.git/verify-stamp` 가 full 로 찍혀 있나(S0 ①).
- surefire 3.5.2 의 `groups` 가 JUnit 태그 식(`&`·`!`)을 받나 — 받는다고 본다(JUnit Platform 제공자 문서). 0-53a 검증 ② 가 잰다.
- ArchUnit 1.4.1 이 반복 `@Tag` 둘(`DbCorpusBase`)을 `@Tags` 컨테이너로 보이는지, `getAllRawSuperclasses()` 로 상속 꼬리표를 따라갈 수 있는지 — 둘 다 처리하게 적었다.
- 닫힘 실측이 3 절의 자바 목록(db 일곱 · corpus 열셋)과 같은지 — 다르면 실측을 따른다(web·cli 제외).
- `gate-probe.sh` 패치가 `verify.sh` 변경에 안 맞을 수 있다 — 마무리 2 에서 다시 뜬다.
- `hooks-test` 임시 저장소에서 `lane db` 의 없는 경로가 전부 `-` 라 main·work 지문이 같다 — `mkrepo_db` 가 `core/meta/M.java` 를 더해야 달라진다(적었다).
