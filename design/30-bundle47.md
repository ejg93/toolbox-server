# 설계 30 — 번들 47: 「코드 정리」 화면 — 자동 수정 일곱 (Fable 설계 · Opus 실행, 2026-10-22)

## 맥락
코드 검사는 **지적만** 한다. 사용자가 2026-10-21 에 「자동 처리는 새 화면 「코드 정리」 로 따로, 엔진(`rules.yaml`·`CheckRunner`)은 같이 쓴다」 고 정했고, 번들 46 이 그 첫 단계로 `java.declarationOrder` 규칙을 넣었다(PR #78). 이 번들이 둘째 단계 — 85 규칙 가운데 **전제 없이 안전하게 고칠 수 있는 A 일곱**(`common.debugger`·`tsx.debugger`·`java.unusedImport`·`tsx.unusedImport`·`common.unusedIgnore`·`java.declarationOrder`·`file.lineEnding`)을 폴더 단위로 미리보기 → 백업 뒤 덮어쓰기 하는 화면·API·CLI 를 만든다. B(전제·프로필 값이 필요한 18)는 다음 번들이 몇 개씩 더한다.

실행자는 Opus. 이 플랜은 실행자가 그대로 따른다. 차례는 `S0` → `5-34a` → `5-34b` → `5-34c` → `5-34d` → `5-34e` → 마무리(번들 모드). **전제: PR #78 이 main 에 머지돼 있어야 한다**(pr-guard 가 열린 작업 PR 이 있으면 새 PR 을 막는다). 열려 있으면 스텝 0 에서 머지한다(사용자 위임 2026-10-22). 번들 47 자체의 PR 은 머지 위임 없음.

사용자 결정(뒤집지 않는다):
- 자동 수정은 「코드 정리」 화면. 코드 검사 화면·순수본은 안 건드린다.
- 규칙은 **전부 체크 → 한 번에**. 수정 하나 = 글→글 함수, 고정 순서, 단계마다 다시 검사, 나빠지면 그 파일 그 단계만 되돌림, 전제 없으면 건너뜀 + 사유.
- A 일곱으로 파이프라인부터. B 는 다음.
- 「변경분만」(git·svn 작업 사본의 바뀐 파일만) 체크박스를 **이번 번들에** 넣는다(2026-10-22) — 코드 검사의 `WorkingCopy` 그대로.

---

## 1. 목표와 범위
- 목표: 런처에 「코드 정리」 카드가 생기고, 폴더(또는 변경분만)를 **미리보기**하면 파일마다 「지적 전→후 · 적용한 단계 · 건너뜀 사유」 가 표로 나오고, 체크한 파일을 **확인창 뒤** 백업하고 덮어쓴다. 같은 일을 `toolbox.bat cleanup <폴더> [--apply]` 로도 한다.
- 체감 변화: `debugger;` 줄·쓰지 않는 import(Java·TS)·쓸모없는 `tb-ignore` 표식이 지워지고, 선언 순서가 틀린 Java 는 안전한 파일만 규약 순서로 옮겨지며, 줄바꿈이 프로필 `project.lineEnding` 으로 맞춰진다. 원본은 `out/<프로필>/<시각>/backup/<상대경로>` 에 있다. 미리보기는 파일을 쓰지 않는다.
- In scope: `core/check` 의 `Cleanup`(파이프라인 + 수정기 여섯 — debugger 둘은 하나)·`CleanupRunner`(폴더 미리보기·적용) · `JavaRules.order` 의 판정 추출(공용) · `JsImportRule` 문장 단위 결과 공개 · `web/CleanupRoutes` · `FsRoutes.stamp` 공용화 · `CheckRoutes.changedOnlyError` 공용화 · CLI `cleanup` + README · 화면 `code_cleanup.html`·`code_cleanup_ext.js` + 런처 카드 · 시험(단위 골든·폴더·라우트·스모크·corpus 불변식) · PLAN/PROGRESS/설계 30.
- Out of scope: B·C 규칙 수정 · 코드 검사 화면 변경 · 순수본 · H2 이력(정리 결과는 `out/…/정리_결과.txt` 글 파일 하나, 코드 본문 없음) · 코드 정리의 xlsx · 5-33(이름 일치 면제) · 선언 순서 수정에서 「안전하지 않은 파일」 을 억지로 고치는 것.

## 2. 현재 구조 요약
- **검사 엔진** `core/check/` — `RuleSet.load(profile, base)`(`:61`)·`builtin()`·`defs()`·`globs()`·`run()` → `Run.apply(Source)`(`:190`, 파일 하나: 정규식·java·mybatis 규칙 + `Ignores` 거르기)·`Run.finish()`(`:233`, dupMapping·namespace + **`common.unusedIgnore` 는 여기서만 나온다** — 파일 하나를 다시 검사할 때도 `apply` 뒤 `finish` 를 불러야 한다)·`Run.suppressed()`. `Rule.Def`(`Rule.java:27`) `id·group·severity·kind·globs·regex·flags·skipComments·message·params·enabled·fix·ref`, `on()`, `fileLevel()`. `Finding(file, line, group, rule, severity, excerpt)`(`Finding.java:7`) — **열·범위 없음**, 수정기가 줄 번호로 자리를 다시 찾는다. `Source(rel, text, encoding, lineEnding, lang)`(`Source.java:10`) — `text` 는 `\n` 정규화(`LocalFiles.read` 와 같다), `lineEnding` 은 파일에서 읽었을 때만. `CheckRunner.run(root, rules, files, ctx, changed, exclude)`(`CheckRunner.java:55-105`) 가 폴더 루프의 본 — `files.list(root, globs, exclude, MAX_FILES)` → 50개마다 `ctx.progress`·`checkCancelled` → `files.read` 실패는 `skipped` → `Source` → `run.apply`. `RunResult(findings, files, skipped, parseErrors, truncated, suppressed)`.
- **규칙 일곱의 판정**(`rules.yaml`):
  - `common.debugger`(`:9`, jsp·jspf·html·htm)·`tsx.debugger`(`:119`, js·jsx·ts·tsx·mjs) — 정규식 `\bdebugger\b`, `skipComments: true`, 줄마다 하나. 문자열 안 `"debugger"` 도 걸린다(`RegexRule.stripComments` 는 문자열을 남긴다).
  - `java.unusedImport`(`:593`, kind java) — `JavaRules.imports()`(`:181-201`): `*` 는 `starImport`, static 은 안 봄, 마지막 조각 이름이 `usedNames(cu)`(모든 `SimpleName`·`Name`·**주석의 단어**)에 없으면 지적. 줄 = `ImportDeclaration.getBegin().line`.
  - `tsx.unusedImport`(`:135`, kind jsUnusedImport) — `JsImportRule.apply`(`:29-61`): `IMPORT = (?m)^[ \t]*import\s+(?:type\s+)?([^;'"]*?)\s+from\s+['"]`(여러 줄 절 가능), `names(clause)`(`:64`, package-private static) 가 `Default, { a, b as c, type D }`·`* as ns` → 바인딩 이름, 본문에 `(?<![\w$.])NAME(?![\w$])` 가 없으면 안 쓰임. **문장당 지적 하나, 어느 이름이 안 쓰이는지는 결과에 없다.** 부수 효과 import(`from` 없음)는 안 본다.
  - `common.unusedIgnore`(`:82`, kind marker) — `Ignores`(package-private) `MARK = \btb-ignore(-next)?(?![\w-])`(`:18`) 를 **원문 줄**에서 찾고(`of()` `:34`) `ids()`(`:54`) 로 규칙 id 목록. `Mark(at, line, rules)` — 표식 줄·먹는 줄·규칙(비면 전부). 열 없음. `Run.finish()` 가 쓰이지 않은 표식마다 지적(줄 = `at`). **꺼진 규칙의 표식은 「안 쓰임」 으로 나온다**(묶음을 끄면 그 묶음 표식이 전부 unused).
  - `java.declarationOrder`(`:665`) — `JavaRules.order(members, mods, c)`(`:297-333`) 상태기계(설계 29 §3 [고정]). 타입 몸체(`cu.findAll(TypeDeclaration)`)·enum 상수 몸체·익명 몸체마다 따로. 상수 `ORDER`·`PARSE_ERROR`(public), `STATIC·INSTANCE·CTOR·METHOD`(private static), `rank(AccessSpecifier)`(private static).
  - `file.lineEnding`(`:168`, kind file) — `FileRule.apply`(`:43-48`): `expected`(프로필 `project.lineEnding`, `RuleSet.expected` `:132`) 가 있고 `s.lineEnding()` 과 대소문자 무시 다르면 줄 1 에 `"CRLF ≠ LF"`. **프로필에 `project.lineEnding` 이 없으면 규칙이 안 난다.** `LocalFiles.encode` 는 `CRLF`·`LF` 정확히(대문자).
- **JavaParser 3.26.3 실측**(2026-10-22, jshell): `Node.getRange()`(`begin.line/column`·`end`, 1부터), 필드·메서드 범위는 **애너테이션 포함, javadoc 제외**. `cu.getAllComments()` 가 파일의 주석 전부(범위 포함). **주석 귀속은 못 믿는다** — 필드 뒤 같은 줄 `// x` 가 `getComment()` 를 차지하고 위 javadoc 은 고아가 된다. `EnumDeclaration.getEntries()`(상수)와 `getMembers()` 가 분리돼 있다. CRLF 글도 열이 같다(글은 어차피 `\n`). `ParserConfiguration` 기본 `attributeComments=true`. `JavaSource.parse` 는 17 → 8.
- **파일 층** `core/fs/LocalFiles.java` — `check(raw)`(`:95`, 절대·UNC 거절·금지 루트)·`list(dir, globs, excludes, max)`(`:152`, `.git`·`target`·`node_modules`·숨김 제외, `Listing{files[{rel,size,mtime}], truncated}`)·`read(path)`(`:257`) → `Text(text, encoding, lineEnding)`(BOM→`UTF-8-BOM`, 엄격 UTF-8→`UTF-8`, 아니면 `MS949`; `\r\n`→`\n`, 홀로 `\r` 은 남김)·**`write(path, root, text, encoding, lineEnding, backupRoot)`**(`:305-334`) — root 안의 있는 파일만, 실제 경로로 한 번 더, 인코딩 먼저(못 쓰는 글자면 400, 원본 그대로), `backupRoot/<root 기준 상대>` 에 첫 원본만, 백업 실패면 안 씀, 원자적 아님·mtime 비교 없음. `exists(path)` → `Exists(exists, dir)`. `MAX_FILES = 20_000`. `remember(path)`.
- **백업 경로** `web/FsRoutes.java` — `backupRoot(active, stamp)`(`:90`) = `outRoot(active, stamp)/backup`, `outRoot` = `<profile.output.dir|out>/<profile|default>/<stamp>` 절대. `STAMP_RE = \d{8}-\d{6}`(`:33`, private)·새 stamp `yyyyMMdd-HHmmss`(`:60-61`). `/api/fs/write` 가 첫 응답의 stamp 를 다시 받아 한 폴더에 모은다.
- **작업** `core/job/` — `JobManager.submit(name, jc -> …)` → `Job`(id 8자). `JobContext.progress(int, String)`·`checkCancelled()`·`emit(RESULT, …)`. `JobRoutes` `GET /api/jobs/{id}` → `{status, progress, message, result}` · `DELETE` 취소. 화면은 SSE 가 아니라 **400ms 폴링**(`code_check_ext.js:256 poll`). 중지는 `DELETE /api/jobs/{id}`(`:248 stop`). 중지해도 이미 쓴 것은 그대로(4-20).
- **코드 검사 라우트** `web/CheckRoutes.java` — `register(app, jobs, db, files, active)`(`:43`). `POST /api/check/run`(`:78-140`): path 검증(`files.check` + `exists().dir()` → 404 「폴더가 없다」) → **changedOnly 검증**(`:103-109`: `WorkingCopy.detect(root)`, `kind NONE`·`!available` 면 400 `reason`) → `jobs.submit("check", …)` 안에서 `WorkingCopy.changed(root)` → `changes::contains` 술어 → 202 `{jobId}`. `GET /api/check/vcs?path=`(`:143-181`) → `{kind, available, reason, changed, recent}` — 화면 「변경분만」 체크박스가 이걸로 켜진다. `static RuleSet load(Profile, groups, overrides)`(`:301-317`, package-private) 가 프로필 codecheck 위에 요청을 얹는다(프로필 없으면 egov35 가짜). 프로필 `codecheck.exclude` 는 폴더 검사에서만.
- **화면 선례** — `code_check.html`(135줄)·`code_check_ext.js`(620줄, ES5 IIFE): `.dirbar` 에 `#dir`(datalist `#dirRecent`)·`#changedLabel>#changed`(disabled)·`#vcsInfo`·`#runDir.btn-p`·`#dirStop.btn-red`; `vcsInfo()`(`:396-425`, `vcsSeq` 가드, `/api/check/vcs`)·`runDir()`(`:330`, `changedOnly` 는 체크박스가 켜졌고 disabled 아닐 때)·`start/poll/stop/done`(`:223-276`)·`loadRecentDirs`(`:492`, `/api/fs/defaults` → `projectRoot`·`recent`). `jsp_formatter_ext.js` 「폴더」 탭: 결과 표를 `insertRow`+`textContent` 로 직접(체크박스 열, 머리 `#dirAll` indeterminate), `dirSummary()` 「n개 · … · 덮어쓸 대상 n」, **`dirApply()`(`:424-453`) `confirm(todo.length + '개 파일을 덮어쓴다. 원본은 out/<프로필>/<시각>/backup 에 백업한다. 계속?')`** → 거절하면 아무것도 안 씀 → `TB.result(…, {summary: '덮어씀 ok/n개 · 실패 k', dir, backup})`(1-58 R11, 일부 실패·중지는 `stop` 노랑 R12). `common.js` `TB.api/table/result/copy/profiles/joinPath`(`:469`) — toast·confirm·폴더 고르기 도우미 없음. 버튼: 실행 `.btn-p`, 덮어쓰기·중지 `.btn-red`.
- **화면 시험·목록**(새 화면이면 손으로 더하는 곳): `web/LauncherTest.java:36-49` `launcherHasElevenCards` — `data-file=` 11·`data-status="ready"` 11 → **12** · PLAN `:179` 「= 11」 → 12. `web/BackendToolsTest.java:28-31` `@ValueSource` 이름 목록. `web/SmokeHtmlUnitTest.java:93-96` `opensWithoutScriptErrors` 목록. `web/ToolsFolderTest.java:32-46` `RESULT_SITES`(파일 → `TB.result` 자리 수, 줄면 빨강). `scripts/puppeteer/visual.js:62` `PAGES`(집 검증). `ToolsFolderTest` 자동 규칙: 파일명 `[a-z0-9_]+\.(html|js|css)` · `*.js` 는 `innerHTML = ''` 만 · `/api/fs/defaults`·`/api/check/rules` 를 부르면 `<body data-profile="…">` 필수 · 화면 `<style>` 에 `:root`·`button{}`·`.btn-*`·`.dl`·고정 색 금지 · `common.css` link 가 첫 `<style>` 앞 · `.dl` 글에 xlsx/저장/파일로 금지 · 복사는 `TB.copy`. `ToolsA11yTest` 가 ㅈ 규칙을 `tools/*.html|js` 에 돌린다(레이블 없는 입력 금지 — `aria-label` 또는 `<label>`). 런처 카드 `tools/index.html:65-68`(`.tool-card data-file data-status href` + `.icon/.name/.desc`), 「텍스트·코드」 묶음은 `:43-69`. 도구 설명 첫 문장에 `무엇을`·`무엇으로`·`어떻게`(CLAUDE.md 글 작성 규칙).
- **CLI** `cli/CodeCommands.java:21-84` `Check` — `BatchCommand` 상속, `@Parameters folder`, `@Option --changed-only/--groups/--xlsx/--fail-on`, `batch.userPath(folder)`·`batch.call("POST", path, body)`·`batch.job(accepted)`(200ms 폴링, `[진행]` stderr)·`batch.print(json, human)`·`batch.fail(code, msg)`. `cli/Main.java:12-15` `subcommands` 목록. `README.md:27-46` 명령 표 — `ReadmeCommandsTest` 가 모든 부명령·옵션이 표에 있는지 잰다. `cli/CodeCliTest`(시험 3) 가 선례.
- **시험 선례** — `CheckRoutesTest.java:35-82`(`@TempDir static`, 프로필 yaml 손으로, `App.start(new AppConfig(0, "t", data, profiles, drivers, false))`, `post/get/waitJob`). `SmokeHtmlUnitTest.jspFormatterFolderBatch`(`:757-850`): `client(true)`, `wc.setConfirmHandler((p, m) -> { asked.add(m); return false; })` → 한 번 묻고 바이트 그대로 → `true` 로 다시 → 「덮어씀 2/2」. `LocalFilesTest.writeKeepsEncodingLineEndingAndBacksUpFirst`(`:70`). `GoldenFiles.assertText(name, actual)`(`:69`, `\r\n`→`\n`)·`assertJson`. `JavaRulesTest.ownSourcesAreInOrder`(우리 코드 303 파일 루프). `CorpusFiles.files(source, "*.java")`·`rel`·`baseline`·`none`. `CheckCorpusTest`(`:35-39` 무리 egov·commons·jspwiki·roller).
- **레인**: `core/check`·`resources/tools`·`test/.../core/check`·`golden/corpus` 는 corpus 레인 → push 앞 `verify.sh --corpus`. `web`·`cli` 는 java(빠른). **pom·DB 코드 안 바뀜 → `--db` 없음.** 새 corpus 꼬리표 시험은 `core/check` 아래라 `VerifyLanesTest` 통과.
- **실측 보강**(2026-10-22, Plan 검토 — jar·코드 확인): `getAccessSpecifier()` 는 `NodeWithModifiers` 기본 메서드로 **수식어만** 본다 → 인터페이스·애너테이션 필드는 판정기에도 정렬 키에도 「인스턴스·package」 다(실제 의미는 static — 초기화 분석은 `effStatic` 으로 따로). `ParseResult.getCommentsCollection()` 이 귀속과 무관한 주석 전체. `JavaToken.getCategory().isWhitespaceOrComment()` 로 토큰 다중집합 비교 가능(`cu.getTokenRange()`, `storeTokens` 기본 true). `ParserConfiguration` 기본 `tabSize=1`·`preprocessUnicodeEscapes=false` → 열 = 글자 offset+1(바꾸면 열이 어긋난다). `LocalFiles.read` 는 `\r\n` 만 정규화 — 홀로 `\r` 은 남고 JavaParser 는 그걸 줄바꿈으로 센다(`split("\n")` 은 아님). `RegexRule.stripComments(text, lang)`(`:125-134`) 가 다루는 언어: `java·js·jsx·ts·tsx·mjs·cjs·css·scss·kt·groovy`(cLike — 따옴표 추적, 정규식 리터럴은 모른다)·`sql`·`jsp·jspf·tag`(`<%-- --%>`·`<!-- -->`)·`xml·html·htm·xhtml·vue`(`<!-- -->`)·`properties`(`#!`)·`yml·yaml·sh`(`#`), 그 밖은 글 그대로. `common.unusedIgnore`·`java.dupMapping` 은 `Run.finish()` 에서만 나온다.
- **함정**(0-61 이력, 2026-10-21 우리 코드 111건 손 이동에서): ① static 블록이 채우는 필드는 블록과 같이 가야 한다 ② `COLS_02` 가 `TAIL` 을 읽는 식으로 **뒤 상수를 읽는 초기화식**은 옮기면 컴파일이 깨진다(JLS 8.3.3 전방 참조) ③ 엇갈려 붙은 javadoc ④ 구획 머리 주석(`// ----`)은 남겼다. → 자동 수정은 「안전한 파일만」, 나머지는 사유와 함께 건너뛴다(§3).

## 3. 설계 결정
- [고정] 패키지·클래스: `core/check/Cleanup.java`(파이프라인 + 수정기, `Ignores`·`JsImportRule.names`·`JavaRules` 의 package-private 를 그대로 쓰려고 같은 패키지)·`core/check/OrderFix.java`(선언 순서 수정기, package-private)·`core/check/CleanupRunner.java`(폴더 미리보기·적용)·`web/CleanupRoutes.java`·화면 `tools/code_cleanup.html`+`code_cleanup_ext.js`·CLI `CodeCommands.Cleanup`. 버린 대안: `core/cleanup` 새 패키지 — `Ignores.MARK`·`names()` 를 public 으로 여는 수술이 더 든다.
- [고정] **단계와 차례**(파일 하나, 고른 규칙만, 켜진 규칙만):
  ```
  ① delete   — common.debugger·tsx.debugger(줄째) → java.unusedImport(import 줄째) → tsx.unusedImport(이름 또는 줄째)
  ② marker   — common.unusedIgnore(표식 주석 떼기)
  ③ order    — java.declarationOrder(OrderFix, 안전한 몸체만)
  ④ eol      — file.lineEnding(글은 그대로, 쓸 때 줄바꿈만 프로필 값)
  ```
  단계마다 「그 단계의 지적 줄」 은 **직전 단계 뒤 다시 검사한 결과**에서 읽는다(줄이 밀리므로). 삭제는 줄 번호 내림차순.
- [고정] **재검사·되돌림 규칙** — 단계 뒤 그 파일을 `Run r = rules.run(); f = r.apply(s); f.addAll(r.finish());` 로 다시 검사한다(**`finish` 필수** — `common.unusedIgnore` 는 거기서만 나온다. `Run` 은 파일마다 새로). 비교는 건수가 아니라 **(규칙, 발췌) 다중집합**이다 — 발췌는 줄 글이라 줄이 밀려도 따라간다(건수 비교는 「표식이 다른 줄을 먹게 된 것」 을 못 본다). `file.*` 규칙은 발췌가 건수 글(`탭 3줄 · …`)이라 **규칙 id 있음·없음**으로만 비교.
  - `*.parseError` 가 새로 생기면 되돌림(「구문이 깨짐」).
  - 대상 규칙의 건수 합이 **줄지 않으면** 되돌림(「효과 없음」). 대상 규칙의 새 (규칙, 발췌) 는 허용(합만 줄면 된다).
  - 그 밖은 아래 표대로. 표 밖의 어떤 (규칙, 발췌) 가 **생기면** 되돌림(「다른 지적이 생김: <규칙>」), 표 밖의 것이 **사라져도** 되돌림(「다른 지적이 사라짐: <규칙>」 — 지운 줄이 코드였다는 뜻).

    | 단계 | 대상(합이 줄어야) | 생겨도 됨 | 대상 말고 사라져도 됨 | 글 불변식 |
    |---|---|---|---|---|
    | ① delete | java: `java.unusedImport` · js/ts: `tsx.unusedImport`+`tsx.debugger` · jsp/html: `common.debugger` | `common.unusedIgnore`(먹던 줄이 사라진 표식 — ② 가 치운다) | `file.mixedIndent`·`file.header` | 새 줄 목록 = 옛 줄 목록에서 지운 번호만 뺀 것(tsx 일부 삭제 줄은 그 줄만 다름) |
    | ② marker | `common.unusedIgnore` | 없음 | 지운 주석 줄 위의 지적(`common.todo`·`tsx.eslintDisable`)·`file.mixedIndent`·`file.header` | 남은 줄은 옛 줄과 같거나 옛 줄의 꼬리를 뗀 것, 지운 줄은 주석뿐이었음 |
    | ③ order | `java.declarationOrder` | 없음 | 없음 | 줄 수 같음 · 줄 전체 다중집합(빈 줄 포함) 같음 · 토큰 다중집합 같음(§OrderFix 6) |
    | ④ eol | `file.lineEnding` → 0 | 없음 | 없음 | 글 그대로(줄바꿈만 쓸 때) |

  되돌림은 그 단계만 — 앞 단계 결과는 남는다. 파일의 `Result` 에 `steps`(stage·rule·before·after)·`skips`(stage·reason, 되돌림·건너뜀 둘 다) 를 둔다. **파일 공통 전제**: `java.parseError` 가 있는 Java 는 ①(import)·③ 을 건너뛴다(「구문을 못 읽음」); 글에 홀로 `\r` 이 있거나 `\\u+[0-9A-Fa-f]{4}` 가 있으면 ①(import)·③ 건너뜀(「\r·유니코드 이스케이프 — 줄 매핑 불가」); ① 은 `tb-ignore-next` 가 든 줄은 절대 지우지 않는다.
- [고정] **수정기 규칙**:
  - debugger: 지적 줄이 `^\s*debugger\s*;\s*$`(js·ts·mjs·cjs 는 `;` 생략 허용 `;?`, jsx·tsx·jsp·html 은 `;` 필수)에 맞고, `RegexRule.stripComments(text, lang)` 의 같은 줄에도 `debugger` 가 남아 있고(주석 안 아님), 그 줄 앞까지의 stripped 글에서 백틱 `` ` `` 수가 짝수(템플릿 리터럴 안 아님)일 때만 **줄째 삭제**. 아니면 그 줄은 두고 skip 「debugger 와 다른 코드가 한 줄·문자열 안 n」.
  - java import: 다시 파싱 → `cu.getImports()` 중 `getBegin().line` 이 지적 줄인 것. 그 노드가 **열 규칙**(§OrderFix 5-②와 같다 — 시작 줄에서 노드 앞 글은 공백, 끝 줄에서 노드 뒤 글은 공백 또는 그 줄에서 닫히는 주석)을 지키면 `begin.line..end.line` 줄째 삭제(여러 줄 import 도). 아니면 skip 「import 줄에 다른 코드」.
  - tsx import: `JsImportRule` 에 `static List<Stmt> statements(Source)` 를 더한다 — `Stmt(int startLine, int endLine, int clauseStart, int clauseEnd, int stmtEnd, List<String> names, List<String> unused)`(offset 은 `s.text()` 기준; `stmtEnd` = 닫는 따옴표 뒤의 선택적 `;` 까지). `apply` 는 이걸 써서 지적을 낸다(판정 불변 — 골든 `fixtures.json` 그대로). 수정에서 **지키는 이름**: `KEEP_JS = {React, h, Fragment, jsx, jsxs}`(JSX 팩터리 — 검사기는 못 본다) + 파일의 **주석 어디든 단어로 나오는 이름**(`JavaRules.usedNames` 와 같은 원칙, `/** @type {Foo} */`) → 안 쓰여도 안 지운다(남은 지적은 그대로). 전부 지울 수 있으면 `startLine..endLine` 줄째 삭제(끝 줄의 문장 뒤가 공백 또는 `//` 꼬리 주석일 때 — 아니면 skip). 일부만이면 **문장이 import 줄에서 끝나는 한 줄 문장만**(`from ['"][^'"]*['"]\s*;?\s*(//.*)?$`): 머리를 `,` 로, `{ … }` 안을 `,` 로 나눠 지울 조각을 빼고(`type ` 접두·`x as y` 는 조각째 유지), `{}` 가 비면 중괄호와 그 앞 쉼표째, 아무것도 안 남으면 줄째, 따옴표 종류·`;`·꼬리 주석·끝 쉼표 꼴은 보존. 여러 줄 문장의 일부 삭제는 skip 「여러 줄 import 는 일부만 못 지운다」. 부수 효과 import 는 손대지 않는다.
  - unusedIgnore: 지적 줄(표식 줄)마다. **못 지우는 표식**(skip, 사유): 규칙 목록이 비었거나(`*`·없음) `java.dupMapping`·`mybatis.namespace` 를 포함 → 「파일 사이 규칙·전부 표식 — 폴더 검사에서만 판정 가능」(파일 하나 재검사는 짝 파일을 못 봐 쓰이는 표식을 unused 로 본다) · 목록에 켜지지 않은 규칙 → 「꺼진 규칙의 표식」 · 한 줄에 `MARK` 둘 이상 → 「표식 둘」 · `lang` 이 `stripComments` 가 모르는 언어 → 「주석 문법을 모르는 파일」. **주석 모양 판정은 `S = stripComments(text, lang)` 로**: 표식 offset `p` 가 `S` 에서 공백(주석 안)이어야 하고, 그 줄에서 `p` 앞 `S` 의 마지막 비공백 글자 다음을 `q`, `q` 부터 줄 끝까지 `S` 가 공백뿐(주석 뒤 코드 없음)이어야 한다. `seg = text[q..줄끝]`, `seg.trim()` 이 `//`·`/*`·`<!--`·`<%--`·`#`·`--` 중 하나로 시작하고 표식 앞에 둘째 `//`·`/*` 가 없어야(정규식 리터럴·`http://` 거름). jsp·html 은 `<!-- -->`·`<%-- --%>` 꼴만, properties·yaml 은 줄 전체 꼴만. 그러면:
    - `q` 앞이 공백뿐(줄 전체가 주석)이고 표식이 열림 바로 다음 토큰 → **줄째 삭제**.
    - 줄 전체가 주석인데 표식 앞에 글이 있다(`/** Returns foo. tb-ignore x */`·javadoc 가운데 `* 설명 tb-ignore x`) → 표식부터 주석 글 끝까지만 잘라 열림·닫힘은 남긴다.
    - 코드 뒤 꼬리 → `seg` 를 통째 자르고 `stripTrailing()`.
    - 그 밖 → skip 「표식이 든 주석 모양을 못 읽음」.
  - lineEnding: 프로필 `project.lineEnding` 이 없으면 단계 자체를 skip 「프로필 project.lineEnding 없음」. 있으면 `Result.lineEnding = 프로필 값 대문자`(글 불변). 다시 검사는 새 lineEnding 으로 `Source` 를 만들어.
- [고정] **선언 순서 수정기 `OrderFix`** — AST 로 다시 찍지 않고 **줄 슬롯 재조립**(주석·공백·탭·들여쓰기 그대로):
  1. `JavaRules.order` 의 판정을 `static List<BodyDeclaration<?>> JavaRules.orderViolations(NodeList<BodyDeclaration<?>> members, boolean mods)` 로 뽑아 `order()` 와 `OrderFix` 가 같이 쓴다(판정 불변 — `java.json`·`java-order-ignore.json`·corpus 골든 그대로). `rank` 는 package-private static 으로. `mods` 는 규칙 `params.ignoreModifiers` 그대로(끄면 접근자 순서는 안 맞춘다).
  2. 몸체 목록 = `cu.findAll(TypeDeclaration)`·`findAll(EnumConstantDeclaration).getClassBody()`·`findAll(ObjectCreationExpr).getAnonymousClassBody()` 를 시작 위치 순으로. 루프(최대 50회): 파싱 → 위반 있는 첫 몸체(이미 건너뛴 몸체 제외) → 재조립 → 다시 파싱. 없으면 끝. 몸체 식별은 `{` 줄 번호(슬롯 재조립은 줄 수를 안 바꿔 안 밀린다).
  3. 한 몸체의 정렬 대상 = `FieldDeclaration`·`ConstructorDeclaration`·`CompactConstructorDeclaration`·`MethodDeclaration`. **고정 멤버**(자리 그대로, 풀처럼) = 중첩 타입·`AnnotationMemberDeclaration`. **정렬 키는 `order()` 와 같은 술어**: `mods=true` 면 (버킷: `f.isStatic()` 필드 0 · 아닌 필드 1 · 생성자 2 · 메서드 3, 필드는 둘째 키 `rank(getAccessSpecifier())`), **`mods=false`(`ignoreModifiers`) 면 (필드 0 · 생성자 1 · 메서드 2) 만**(static·인스턴스 합침, 접근자 없음 — 판정기가 안 보는 것은 안 옮긴다). 인터페이스·애너테이션 필드는 수식어만 보는 두 술어에서 「인스턴스·package」 — 키도 그대로(그래야 재조립 뒤 `order()==0`). 안정 정렬 → 정렬 i번째 조각을 i번째 슬롯에.
  4. 조각 = 통째 줄 `[L0, L1]`: `L1` = 노드 끝 줄, `L0` = 노드 시작 줄(애너테이션 포함)에서 **바로 위로 이어지는 주석뿐인 줄**을 끌어안는다. 「주석뿐인 줄」 판정은 `ParseResult.getCommentsCollection()`(귀속 무관 전체) 범위로 — 그 줄의 글 가운데 주석 범위 밖이 공백뿐이고, **그 줄을 덮는 모든 주석이 접두 안에서 시작**해야(경계를 걸친 블록 주석 거름). 빈 줄은 **javadoc(`/** … */`) 일 때만 하나 건너** 끌어안는다(javadoc 도구가 빈 줄 너머 다음 선언에 붙인다). 구획 줄 `^\s*//\s*[-=*#/]{4,}` 은 접두에 안 넣는다(0-61 「구획 머리 주석은 남김」). 조각 사이 글은 풀이라 자리를 지킨다.
  5. **몸체를 건너뛰는 조건**(사유를 `skips` 에 「{줄}: 사유」, 파일의 다른 몸체는 계속):
     - ① `InitializerDeclaration`(`static {}`·`{}`) 이 있다 → 「초기화 블록」(함정 ①).
     - ② **열 규칙** 위반 → 「한 줄에 선언 둘」: 어느 정렬 대상이든 `L0`(접두 전 노드 시작 줄)에서 노드 시작 열 앞 글이 공백 또는 `[L0, L1]` 안에 든 주석이 아니거나, `L1` 에서 노드 끝 열 뒤 글이 공백 또는 `L1` 에서 열고 닫히는 주석이 아니거나(`} // end` 허용, `int a; int b;`·`RED, GREEN; private int v;`·`int x;;` 거절), 조각끼리·고정 멤버와 줄이 겹친다. 이 규칙이 `{`·`}` 줄 검사를 대신한다.
     - ③ 어떤 주석의 줄 범위가 조각(접두+노드)과 **일부만** 겹친다 → 「경계를 걸친 주석」(걸친 `/*` 가 옮겨져 뒤 코드를 주석으로 삼키는 것 — 줄 다중집합으론 못 잡는다).
     - ④ **읽기 쌍 보존**: 몸체의 어느 필드 F 의 초기화식(람다·익명 몸체 안까지) 안 `SimpleName`·`NameExpr`·`FieldAccessExpr` 이름이 같은 몸체의 다른 필드 G 의 이름과 같으면 (F, G) 의 **상대 순서가 옛 순서와 새 순서에서 같아야** 한다(양방향 — `private static int b = C.a + 1; public static int a = 5;` 는 합법인데 a 가 b 위로 가면 값이 바뀐다) → 「초기화 순서(다른 필드를 읽음)」(함정 ②). 메서드 이름과 겹치는 오탐은 안전한 쪽.
     - ⑤ **초기화식 분류와 고정**(`effStatic(f) = f.isStatic() || 소유 타입이 인터페이스·애너테이션` 으로 static/인스턴스 범주를 나눈다): 필드마다(선언자 여럿이면 가장 나쁜 것) **inert** = 초기화식 없음, 또는 식 트리가 `LiteralExpr`(텍스트 블록 포함)·`ClassExpr`·`NameExpr`·`FieldAccessExpr`·`ThisExpr`(호출 인자 아님)·`UnaryExpr`(`++`·`--` 아님)·`BinaryExpr`·`ConditionalExpr`·`CastExpr`·`EnclosedExpr`·`InstanceOfExpr`·`ArrayAccessExpr`·`ArrayInitializerExpr`·`ArrayCreationExpr` 로만 돼 있고 **이 파일에 선언된 타입 이름을 루트로 읽지 않음**(`H.v` 는 H 의 static 초기화를 끈다) / **external-pure** = inert 조건에 더해 `MethodCallExpr` 의 scope 가 대문자로 시작하는 이름 사슬이고 그 루트가 이 파일의 타입·필드가 아님(`LoggerFactory.getLogger(X.class)`·`Pattern.compile("…")`), 또는 익명 몸체 없는 `ObjectCreationExpr` 의 타입 이름이 이 파일에 없음(`new ArrayList<>()`), 인자는 전부 inert·`ThisExpr`·람다·메서드 참조 없음 / **opaque** = 나머지(한정 없는 호출·`this`·`super`·필드 scope 호출·이 파일 타입 호출·람다·메서드 참조·`SwitchExpr`·`AssignExpr`·`++`·`--`·익명 몸체). 고정 규칙(범주 안에서): opaque 필드는 **같은 범주 모든 필드**와 상대 순서 유지 · non-inert(external-pure·opaque) 둘은 서로 상대 순서 유지 · inert ↔ inert·external-pure 는 ④만 지키면 자유. 위반 → 「초기화 부수 효과 순서」.
     - ⑥ 소유 타입 또는 바깥 타입에 `@AllArgsConstructor`·`@RequiredArgsConstructor`·`@Value`·`@Data`·`@Builder`·`@SuperBuilder`(단순 이름) → 「Lombok 생성자 인자 순서」(필드 순서가 생성자 시그니처 — 같은 타입 인자가 소리 없이 바뀐다).
     - ⑦ 몸체 안 `java.declarationOrder` 지적 가운데 **억제된 것**(`Ignores.of(s)` 가 그 줄을 `ORDER` 또는 전부로 먹음)이 하나라도 있다 → 「억제 표식이 있는 몸체」(사용자가 그 자리를 고정한 것). 억제 안 된 지적이 0 이면 그 몸체는 손대지 않는다.
     받아들이는 위험(skip 아님, 설계에 기록): 필드 순서를 반사로 읽는 쪽(Jackson 기본 속성 순서·JAXB `propOrder` 없는 `@XmlType`·Lombok `@ToString`) 의 출력 순서가 바뀔 수 있다. `serialVersionUID` 기본 계산은 이름순이라 무관.
  6. **루프와 확인**: 반복마다 파싱 → 몸체(시작 순) 가운데 ①~⑦ 에 안 걸리고 위반이 있는 첫 몸체를 재조립 → 다시 파싱해 **확인**: 파싱 성공 · 줄 수 같음 · 줄 전체 다중집합(빈 줄 포함) 같음 · **토큰 다중집합**(`cu.getTokenRange()` 에서 `getCategory().isWhitespaceOrComment()` 아닌 토큰의 kind+text) 같음 · 주석 글 다중집합 같음 · 그 몸체 위반 0 · **파일 전체 위반 수가 줄었음**. 하나라도 틀리면 **③ 전체를 그 파일에서 되돌리고**(「재검 실패」) 끝. 상한 = 몸체 수 + 1(50 아님). 건너뛴 몸체는 사유만 남기고 다른 몸체는 고친다(몸체는 서로 독립).
  버린 대안: JavaParser `LexicalPreservingPrinter` — 주석 귀속이 틀려(실측) javadoc 이 다른 선언에 붙어 나간다 · 전체 재출력 — 포맷이 바뀐다 · 몸체 하나 실패에 파일 전체 skip — 몸체는 독립이라 과하다(확인 실패만 전체 되돌림).
- [고정] **미리보기·적용 두 단계, 적용은 다시 계산**: 미리보기 작업이 파일마다 `hash`(입력: SHA-256 of `encoding|lineEnding|text`, hex) 와 `outHash`(결과: SHA-256 of `newLineEnding|newText`) 를 돌려주고, 적용 요청은 `files[{rel, hash, outHash}]` 를 보낸다. 적용 작업은 파일을 **다시 읽어 `hash` 가 다르면** 「미리보기 뒤 바뀜」, 파이프라인을 다시 돌린 결과의 `outHash` 가 다르면 「미리보기와 결과가 다름(규칙·프로필이 바뀜)」 으로 건너뛰고, 둘 다 같을 때만 `LocalFiles.checkEncodable` → `LocalFiles.write(path, root, text, encoding, newLineEnding, backupRoot)`. 버린 대안: 미리보기 결과 글을 서버 메모리에 들고 있기 — 작업 결과에 코드 본문이 실려 200개가 남고, 그 사이 파일이 바뀌면 낡은 글을 덮는다.
- [고정] 쓰기는 `LocalFiles.write` 하나로(규칙 5-5: root 안의 있는 파일만, 백업 먼저, 인코딩·줄바꿈 보존). 백업 루트 = `FsRoutes.backupRoot(active, stamp)`. 적용 작업 하나 = stamp 하나. 결과 글 파일 `FsRoutes.outRoot(active, stamp)/정리_결과.txt`(UTF-8: 머리 요약 한 줄 + 파일마다 「상대경로 · 단계 · 전→후 · 건너뜀 사유」 — 코드 본문 없음, 규칙 3).
- [고정] 화면은 코드 검사의 폴더 칸·변경분만·폴링·중지를 **복제**(ES5, `innerHTML = ''` 만), 결과 표는 JSP 포매터처럼 체크박스 열을 손으로. 덮어쓰기 앞 `confirm` 글은 JSP 포매터와 같은 꼴. 규칙 일곱 체크박스는 전부 켜진 채 시작, 프로필에서 꺼진 규칙은 체크 해제 + disabled + title 「프로필에서 꺼짐」, lineEnding 은 프로필 값이 없으면 title 「프로필 project.lineEnding 없음」 + disabled.
- [고정] 폴더 목록 globs = 고른 규칙들의 `globs` 합집합(Java 만 골랐으면 `*.java` 만 읽는다). 프로필 `codecheck.exclude` 적용. 변경분만이면 `WorkingCopy.changed(root)::contains` 로 거른다(안 바뀐 Java 를 `observe` 할 필요 없음 — 파일 사이 규칙은 수정 대상이 아니다).
- [고정] H2 에 안 남긴다. CLI `cleanup` 은 미리보기만이 기본, `--apply` 면 미리보기 뒤 바뀐 파일 전부를 적용(확인창 없음 — 배치).
- 청크: `S0`(설계 30 + PLAN 행 + §3.1·§8·런처 12) → `5-34a`(`Cleanup` 파이프라인 + ①②④ 수정기 + 골든) → `5-34b`(`OrderFix` + `JavaRules.orderViolations` 추출 + 골든·건너뜀·우리 코드·corpus 불변식) → `5-34c`(`CleanupRunner` + 폴더 시험) → `5-34d`(`CleanupRoutes` + `FsRoutes.stamp`·`CheckRoutes.changedOnlyError` + CLI + README + 라우트·CLI 시험) → `5-34e`(화면 + 런처 카드 + 시험 목록 + 스모크). 5-34a·b·e 는 파일 1~3 초과 — 한 기능의 코드·픽스처·골든은 한 커밋이어야 verify 가 초록(행에 적는다).
- 가지: `work/2026-10-22b`(main 에서, #78 머지 뒤). 머지 위임 없음 — PR 열고 멈춘다.

## 4. 실행 스텝

**스텝 0 — 전제·가지**
- 변경: PR #78 이 열려 있으면 **머지한다**(사용자 위임 2026-10-22 — CI 초록·AI 리뷰 「아니오」 0 확인 뒤): `gh api repos/ejg93/toolbox-server/commits/$(gh pr view 78 --json headRefOid --jq .headRefOid)/check-runs --jq '.check_runs[]|[.name,.status,.conclusion]|@tsv'` 전부 `completed success` → `gh pr merge 78 --merge --delete-branch`. 그 뒤 `gh pr list --state open` 이 비어 있는지 → `git checkout main && git pull` → `git checkout -b work/<오늘 날짜>`(같은 날 가지가 이미 있으면 `work/<날짜>b`).
- 검증: `git log --oneline -1` 에 #78 머지 커밋이 보인다 · `git rev-parse --abbrev-ref HEAD` = `work/<날짜>`.
- 의존: 없음.

**스텝 1 — S0 설계 파일 + PLAN**
- 대상: 새 `design/30-bundle47.md` · `PLAN.md` §3.1(`:134` 억제 주석 단락 뒤)·런처 카드(`:179`)·§8 API 표(코드 검사 행 뒤)·§15 번들 표(`| 번들 46` 행 뒤)·§16 분할표(`| 5-33` 행 뒤).
- 변경:
  - `design/30-bundle47.md` = 이 플랜의 머리와 1~7절 그대로(설계 29 꼴: `# 설계 30 — 번들 47: …`, `## 맥락`, `## 1.`~`## 7.`).
  - §3.1 끝에 단락:
    `**코드 정리**(설계 30, 번들 47) — 코드 검사는 지적만 한다. 자동 수정은 화면 「코드 정리」(\`code_cleanup.html\`) 가 같은 엔진으로 한다: A 일곱(debugger 둘·쓰지 않는 import 둘·쓸모없는 tb-ignore·선언 순서·줄바꿈)을 고른 폴더(또는 변경분만)에서 ① 삭제 → ② 표식 → ③ 선언 순서 → ④ 줄바꿈 차례로 글→글 함수로 고치고, 단계마다 다시 검사해 나빠지면 그 파일 그 단계만 되돌린다. 미리보기(쓰기 없음) → 확인창 → \`LocalFiles.write\`(백업). B 열여덟(전제·프로필 값 필요)은 뒤 번들이 몇 개씩 더한다.`
  - 런처 `:179`: 「… = 11」 → 「… + 코드 정리(설계 30) = 12」.
  - §8 표 행(코드 검사 행 뒤): `| 코드 정리 | \`GET /cleanup/rules\` → [{id, group, severity, message, fix, enabled, expected?}] · \`POST /cleanup/preview\`(job) {path, rules[], changedOnly} → {files, skipped, changed, truncated, rows[{rel, hash, outHash, before, after, changed, lineEnding, steps[], skips[]}]} · \`POST /cleanup/apply\`(job) {path, rules[], files[{rel, hash, outHash}], stamp?} → {written, failed, stale, stamp, backup, report, rows[{rel, ok, reason, backup}]} | 적용은 다시 읽어 hash 가 같고 다시 계산한 결과의 outHash 도 같을 때만 쓴다(설계 30) |`
  - 번들 표 행: `| 번들 47 | \`S0\`→\`5-34a\`→\`5-34b\`→\`5-34c\`→\`5-34d\`→\`5-34e\` | 2026-10-22(Fable, 설계 30 — \`design/30-bundle47.md\`). **「코드 정리」 화면 — 자동 수정 A 일곱**(debugger 둘·import 둘·tb-ignore·선언 순서·줄바꿈) · 고정 순서 파이프라인 + 단계마다 재검사·되돌림 · 선언 순서는 줄 슬롯 재조립(안전한 몸체만) · 미리보기 → 확인 → \`LocalFiles.write\` 백업 · 변경분만 · CLI \`cleanup\`. 새 의존성 0, 새 화면 1(\`code_cleanup.html\`), 프로필 키 0. 머지 위임 없음 | 표본 폴더 + node(\`--corpus\`) | 대기 |`
  - 분할표 행 여섯:
    `| 5-34 S0 | 설계 30 반영 — PLAN §3.1·§8·런처 12·번들 47 행 | 문서만 | 없음 | 대기 |`
    `| 5-34a | 코드 정리 파이프라인 — Cleanup(단계·재검사·되돌림) + 수정기 ①②④ | 설계 30 §3·§4 스텝 2. **강제 지점**: \`CleanupTest\`(골든 \`golden/cleanup/*.txt\`·되돌림·건너뜀 사유). **건드리는 자리**: \`core/check/Cleanup.java\`·\`JsImportRule.statements\`·\`fixtures/cleanup/\`·골든. 청크 규칙 초과 — 한 기능의 코드·픽스처·골든은 한 커밋 | 없음 | 대기 |`
    `| 5-34b | 선언 순서 수정기 OrderFix — 줄 슬롯 재조립, 안전한 몸체만 | 설계 30 §3 [고정] 1~6. **사실**: JavaParser 주석 귀속은 못 믿는다(2026-10-22 실측) → 줄 규칙 + 귀속 무관 주석 전체. **결정**: 건너뜀 일곱(초기화 블록·한 줄에 선언 둘·경계를 걸친 주석·읽기 쌍 순서·초기화 부수 효과 순서·Lombok 생성자·억제 표식) + 확인 여섯(파싱·줄 수·줄 다중집합·토큰 다중집합·몸체 위반 0·파일 위반 감소) 틀리면 ③ 전체 되돌림. **강제 지점**: \`CleanupOrderTest\`(골든·건너뜀 일곱·ignoreModifiers 키·우리 코드 0 변경·멱등)·\`CleanupCorpusTest\`(corpus, A: 「구문이 깨짐」·「재검 실패」 0 · 적용 몸체 위반 0 · 줄·토큰 다중집합 보존, 골든 \`golden/corpus/cleanup-*.json\`). **건드리는 자리**: \`OrderFix.java\`·\`JavaRules.orderViolations\`·픽스처·골든. 청크 규칙 초과 — 같은 이유 | 5-34a | 대기 |`
    `| 5-34c | CleanupRunner — 폴더 미리보기·적용(hash·백업·결과 글) | 설계 30 §4 스텝 4. **강제 지점**: \`CleanupRunnerTest\`(미리보기 무쓰기·적용 백업·인코딩 보존·낡은 hash·결과 글에 코드 없음). **건드리는 자리**: \`CleanupRunner.java\`·시험 | 5-34a | 대기 |`
    `| 5-34d | CleanupRoutes + CLI cleanup | 설계 30 §4 스텝 5. **건드리는 자리**: \`web/CleanupRoutes.java\`·\`App\` 등록·\`FsRoutes.stamp\`·\`CheckRoutes.changedOnlyError\`·\`CodeCommands.Cleanup\`·\`Main\`·README·\`CleanupRoutesTest\`·\`CodeCliTest\` | 5-34c | 대기 |`
    `| 5-34e | 화면 code_cleanup + 런처 카드 | 설계 30 §4 스텝 6. **강제 지점**: \`SmokeHtmlUnitTest.codeCleanupPreviewApply\`(확인 거절 → 바이트 그대로)·\`LauncherTest\` 12·\`ToolsFolderTest\`·\`ToolsA11yTest\`. **건드리는 자리**: \`tools/code_cleanup.html\`·\`code_cleanup_ext.js\`·\`index.html\`·시험 목록 넷·\`visual.js\`. 청크 규칙 초과 — 화면 둘 + 목록 | 5-34d | 대기 |`
- 검증: `bash scripts/verify.sh` 초록(docs). 커밋 `docs: S0 번들 47 설계 30 반영`.
- 의존: 0.

**스텝 2 — 5-34a `Cleanup` 파이프라인 + 수정기 ①②④**
- 대상 ①: `src/main/java/kr/ejg/toolbox/core/check/JsImportRule.java` — `apply` 를 쪼갠다.
  ```java
  /** import 문 하나 — 줄·절 범위(text 기준 offset)·바인딩 이름·안 쓰이는 이름. 코드 정리(5-34)가 같은 판정으로 자리를 찾는다 */
  public record Stmt(int startLine, int endLine, int clauseStart, int clauseEnd, int stmtEnd, List<String> names, List<String> unused) {
  }

  /** 문장 전부(안 쓰이는 이름이 없어도) — apply 는 unused 가 빈 것을 뺀다 */
  public static List<Stmt> statements(Source s) { … 기존 apply 의 본문 그대로, 각 매치마다 Stmt 를 만든다.
      stmtEnd: m.end() 뒤에서 여는 따옴표 글자(code.charAt(m.end() - 1))와 같은 글자를 찾아 그 다음, 거기서 공백 없이 ';' 가 이어지면 그 뒤
      clauseStart/End = m.start(1)/m.end(1), startLine = lineOf(code, m.start()), endLine = lineOf(code, stmtEnd - 1) }
  ```
  `apply` = `statements(s)` 중 `unused` 가 빈 것을 빼고 `Finding(... startLine ...)` — 골든 `fixtures.json` 불변.
- 대상 ②: 새 `src/main/java/kr/ejg/toolbox/core/check/Cleanup.java`
  ```java
  /**
   * 코드 정리(5-34, 설계 30) — 코드 검사 지적 가운데 A 일곱을 글→글 함수로 고친다. 단계 차례 고정(① 삭제 → ② 표식 → ③ 선언 순서 → ④ 줄바꿈),
   * 단계마다 다시 검사해 나빠지면 그 단계만 되돌린다. 파일을 쓰지 않는다(쓰기는 CleanupRunner → LocalFiles.write).
   */
  public final class Cleanup {
      public static final List<String> RULES = List.of("common.debugger", "tsx.debugger", "java.unusedImport", "tsx.unusedImport",
              "common.unusedIgnore", "java.declarationOrder", "file.lineEnding");
      public static final String DELETE = "delete";
      public static final String MARKER = "marker";
      public static final String ORDER = "order";
      public static final String EOL = "eol";

      /** 적용한 단계 — 대상 규칙의 건수 전·후 */
      public record Step(String stage, String rule, int before, int after) { }
      /** 건너뜀·되돌림 — 사유는 사람이 읽는 한 줄, 코드 본문 없음 */
      public record Skip(String stage, String reason) { }
      public record Result(String text, String lineEnding, boolean changed, List<Finding> before, List<Finding> after,
              List<Step> steps, List<Skip> skips) { }

      private final RuleSet rules;
      private final Set<String> wanted;      // 고른 규칙 ∩ RULES ∩ 켜진 규칙
      private final String lineEnding;       // 프로필 project.lineEnding(대문자) 또는 null

      public Cleanup(RuleSet rules, Collection<String> wanted, String lineEnding)
      public Result clean(Source s)          // 단계 네 개 차례로. 각 단계: 지적 수집 → 수정기 → 재검사 → 비교 → 채택/되돌림
      static final Set<String> KEEP_JS = Set.of("React", "h", "Fragment", "jsx", "jsxs");   // JSX 팩터리 — 검사기가 못 본다
      static final Set<String> CROSS_FILE = Set.of("java.dupMapping", "mybatis.namespace"); // 파일 하나 재검사로는 표식 쓰임을 못 판정

      static List<Finding> check(RuleSet rules, Source s)   // rules.run() 새로: apply + finish, 파일 하나
      /** (규칙, 발췌) 다중집합 — file.* 규칙은 (규칙, "") 로 */
      static Map<String, Integer> keys(List<Finding> fs)
      /** 되돌림 판정(§3 표) — null 이면 채택, 아니면 사유 */
      static String worse(String stage, String lang, List<Finding> before, List<Finding> after, List<String> linesBefore, List<String> linesAfter)
      static boolean javaUnsafe(String text, List<Finding> before)   // parseError 있음 · 홀로 \r · \\u+XXXX → ①(import)·③ 건너뜀
      // 수정기(package-private static, 글→글. 못 고친 줄은 skips 에 사유+건수)
      static String deleteDebugger(Source s, List<Integer> lines, List<Skip> skips)
      static String deleteJavaImports(String text, List<Integer> lines, JavaSource parser, List<Skip> skips)
      static String fixTsImports(Source s, List<Integer> lines, List<Skip> skips)
      static String removeMarkers(Source s, List<Integer> lines, Set<String> enabledRules, List<Skip> skips)
      static List<String> lines(String text)  // split("\n", -1)
      static String join(List<String> lines)
  }
  ```
  `worse` 의 규칙은 §3 [고정] 되돌림 규칙 그대로(허용 규칙: `DELETE` 뒤 `common.unusedIgnore`). ③ 은 5-34b 에서 `OrderFix.apply(text, mods, skips)` 를 끼운다 — 이 스텝에선 `ORDER` 단계를 **「아직 없음」 으로 건너뛰지 말고 자리만**(`if (wanted.contains(JavaRules.ORDER)) { /* 5-34b */ }`).
- 대상 ③: 픽스처 `src/test/resources/fixtures/cleanup/` — 실행자가 쓴다(글자 그대로 강제 없음, 아래 **내용 요건**만):
  - `debugger/pos.jsp`: `debugger;` 홀로 줄 둘(들여쓰기 다르게) + `var x = 1; debugger;` 한 줄(남아야) + `<%-- debugger --%>`(주석, 지적 없음) · `debugger/pos.ts`: `debugger` 홀로 + `if (a) { debugger; }`(남아야).
  - `javaimport/Pos.java`: 안 쓰는 import 둘(하나는 중간), 쓰는 import 하나, `import java.util.*;`(starImport — 안 지움), static import(안 봄).
  - `tsximport/pos.ts`: `import A, { b, c as d } from 'x';`(b 만 안 씀 → `import A, { c as d } from 'x';`) · `import { e } from 'y'`(전부 안 씀 → 줄째) · 여러 줄 `import {\n f,\n g\n} from 'z';`(f 만 안 씀 → 그대로 + skip) · `import type { T } from 't';`(안 씀 → 줄째) · 부수 효과 `import './a.css';`(그대로).
  - `ignore/pos.java`: 줄 전체 `// tb-ignore common.sysout 이유` (지적 없음 → 줄 삭제) · `int x = 1; // tb-ignore common.todo` (꼬리 → `int x = 1;`) · `String s = "http://a"; // tb-ignore common.todo`(꼬리, `//` 앞 `:` 아님 — `http://` 의 `//` 는 따옴표 홀수 검사에 걸린다 → 마지막 `//` 는 따옴표 밖이라 자른다) · `/* tb-ignore common.todo */ int y;`(앞 주석 — 「모양을 못 읽음」 skip) · `// tb-ignore java.dupMapping`(java 묶음을 끈 프로필로 돌리는 시험에서 「꺼진 규칙의 표식」 skip) · `ignore/pos.jsp`: `<%-- tb-ignore * --%>` 줄 전체 · `<p>x</p> <%-- tb-ignore jsp.scriptlet --%>` 꼬리.
  - `eol/pos.java`(CRLF 로 저장, `.gitattributes` 에 `fixtures/cleanup/eol/* -text` 추가).
- 대상 ④: 새 `src/test/java/kr/ejg/toolbox/core/check/CleanupTest.java`
  - `profile(lineEnding, groupsOff…)` 도우미 → `RuleSet.load(p, null)`. `Cleanup(rules, Cleanup.RULES, "LF")`.
  - 시험 `fixturesGolden`: 픽스처 전부를 `Source` 로 읽어 `clean` → 결과 글을 `GoldenFiles.assertText("cleanup/<folder>-<file>.txt", r.text())`, `steps`·`skips` 를 `GoldenFiles.assertJson("cleanup/steps.json", …)`(파일 → {steps, skips, changed, lineEnding}). 더해서 단언: 결과 글의 `after` 에 `*.parseError` 0 · debugger 픽스처의 남아야 할 줄이 남음 · `eol/pos.java` 는 `text` 같고 `lineEnding == "LF"`.
  - 시험 `disabledRuleMarkerStays`: java 묶음 끈 프로필 → `ignore/pos.java` 의 `java.dupMapping` 표식 줄이 남고 skip 「꺼진 규칙의 표식」.
  - 시험 `worseRevertsStage`: `worse` 단위 — parseError 새로 → 사유 · 대상 안 줄음 → 「효과 없음」 · 표 밖 (규칙, 발췌) 생김 → 사유 · 표 밖 것 사라짐 → 사유 · DELETE 뒤 unusedIgnore 생겨도 null · `file.mixedIndent` 발췌만 바뀌어도 null(id 비교) · 발췌가 같은 지적이 줄만 밀려도 null.
  - 시험 `guards`(픽스처 `guards/`): `tb-ignore-next` 가 든 `debugger;` 줄은 안 지움 · 템플릿 리터럴 안 `debugger;` 줄은 안 지움 · `import React from 'react'`(안 쓰임) 는 남음(KEEP_JS) · `/** @type {Foo} */` 가 있으면 `import { Foo }` 남음 · `x = /\/\//; // tb-ignore common.todo` 는 skip(둘째 `//`) · `// tb-ignore java.dupMapping` 은 skip(파일 사이 규칙) · `// tb-ignore *` 는 skip · parseError 든 Java 는 import 안 지움 + skip 「구문을 못 읽음」.
  - 시험 `tsStatements`: `JsImportRule.statements` 의 범위(한 줄·여러 줄·`;` 없음·꼬리 주석) 넷.
- 검증(차례대로):
  1. `bash scripts/mvn.sh -q -B -Dtest='CleanupTest,CheckRulesTest,JavaRulesTest' -Dsurefire.failIfNoSpecifiedTests=false -Dgolden.update=true test` → 새 골든이 생긴다. **골든을 눈으로 읽어** 픽스처 요건(남아야 할 줄·지워져야 할 줄)과 맞는지 본다. `git diff src/test/resources/golden/check/` 가 **비어 있어야**(판정 불변).
  2. 같은 명령에서 `-Dgolden.update` 빼고 초록 → `bash scripts/mvn.sh -q -B validate` 초록(선언 순서) → `bash scripts/verify.sh` 초록.
  3. 커밋 `feat: 5-34a 코드 정리 파이프라인 + 수정기 ①②④`.
- 의존: 1.

**스텝 3 — 5-34b `OrderFix` + corpus 불변식**
- 대상 ①: `JavaRules.java` — `order(members, mods, c)` 의 판정 루프를 뽑는다:
  ```java
  /** 선언 순서 위반 멤버(설계 29 §3 상태기계 그대로) — order() 와 OrderFix(5-34b) 가 같이 쓴다 */
  static List<BodyDeclaration<?>> orderViolations(NodeList<BodyDeclaration<?>> members, boolean mods) { … 기존 본문, add 대신 out.add(f)/out.add(m) … }
  private void order(NodeList<BodyDeclaration<?>> members, boolean mods, Ctx c) { for (BodyDeclaration<?> m : orderViolations(members, mods)) add(c, ORDER, m); }
  static int rank(AccessSpecifier a)   // private → package-private
  ```
  선언 자리는 그대로(우리 코드 선언 순서 검사).
- 대상 ②: 새 `src/main/java/kr/ejg/toolbox/core/check/OrderFix.java`(package-private final) — §3 [고정] 1~6 그대로.
  ```java
  final class OrderFix {
      static final int MAX_ROUNDS = 50;
      /** 글 전체. 못 고친 몸체는 skips 에 사유(「{줄}: 사유」)로, 고친 몸체 수를 돌려준다. 글이 안 바뀌면 같은 글 */
      static String apply(String text, boolean mods, JavaSource parser, List<Cleanup.Skip> skips)
      // 안 — record Slice(int l0, int l1, BodyDeclaration<?> node, int bucket, int access) · record Body(NodeList<BodyDeclaration<?>> members, int openLine, int closeLine)
      static List<Body> bodies(CompilationUnit cu)                      // 시작 위치 순
      static String reorder(String text, Body b, boolean mods, CompilationUnit cu, List<Cleanup.Skip> skips)   // null 이면 skip(사유 넣음)
      static boolean[] commentOnlyLines(String[] lines, CompilationUnit cu) // getAllComments 범위로
      static Set<String> namesIn(Expression init)                        // 모든 SimpleName
      static boolean impure(Expression init)                             // MethodCallExpr·ObjectCreationExpr 있음
      static List<String> nonBlank(String[] lines)                       // 정렬한 다중집합 비교용
  }
  ```
  `Cleanup.clean` 의 `ORDER` 단계: `OrderFix.apply(text, mods, parser, skips)` → 재검사·`worse` → 더해서 줄 수 같음·다중집합 같음 아니면 되돌림.
- 대상 ③: 픽스처 `fixtures/cleanup/order/`(실행자가 쓴다, 내용 요건):
  - `Pos.java`: 접근자 역순 필드·메서드 뒤 상수(javadoc + `@Deprecated` 달린)·메서드 뒤 생성자·enum 상수 몸체 안 역순·익명 클래스 안 역순·구획 주석 `// ----` 뒤 빈 줄(자리 유지)·꼬리 주석 든 필드 — 기대: 규약 순서, 주석은 제 선언과 같이, 구획 주석 자리 그대로, 줄 수 같음.
  - 건너뜀 일곱(§3 OrderFix 5 의 ①~⑦ 하나씩, 파일 이름 `Skip<사유>.java`, 각각 다른 위반 하나도 들어 있어 「고치려 들지만 skip」 이 됨): `SkipInit`(static 블록) · `SkipSameLine`(`int a; int b;`·enum `RED, GREEN; private int v;`) · `SkipStraddle`(`int a; /* note` 다음 줄 `continues */` 다음 `private int b;`) · `SkipRead`(둘 — `private static final String B = Fwd.A + "x"; static final String A = "a";` 처럼 정렬 뒤 **합법이 되는** 쪽도 상대 순서가 바뀌므로 skip, 그리고 `public static final int X = Y + 1; private static final int Y = 1;`) · `SkipImpure`(`private static int a = compute(); public static int b = 5;` opaque · `private static final Logger LOG = LoggerFactory.getLogger(X.class); public static final Foo F = new Foo();` non-inert 둘 · 인터페이스 `int X = Util.next(); public int Y = Util.next();` effStatic) · `SkipLombok`(`@Data class U { private String a; public String b; }`) · `SkipSuppressed`(`// tb-ignore java.declarationOrder` 가 든 위반 + 다른 위반) · 더해서 **고쳐지는** 가장자리 `PosEdge.java`: javadoc + 빈 줄 + 필드(javadoc 이 같이 옮겨짐) · `// ----` 구획 줄은 자리 유지 · `/* a */ int x;`(한 줄 주석 뒤 선언 — 허용) · `} // end`(꼬리 주석 허용) · `LOG = LoggerFactory.getLogger(X.class)`(external-pure) 와 상수 `public static final int N = 1;` 의 교차(허용) · `Mixed.java`(최상위 타입 둘 — 하나는 고쳐지고 하나는 skip, 둘 다 `steps.json` 에) · `IgnoreMods.java`(프로필 `ignoreModifiers: true` 로 돌리는 시험 — `A(){} public int x; private static int y;` → `x, y, A()` 만, 접근자 순서는 안 바뀜).
  - 입력으로 **기존** `fixtures/check/java/PosOrder.java`·`PosOrderEdge.java` 도 읽어(복사 안 함, 경로로) 결과를 골든 `golden/cleanup/order-PosOrder.txt`·`order-PosOrderEdge.txt` 에 — 가장자리 전부에 대한 동작 기록(어느 몸체가 skip 되는지 `steps.json` 에).
- 대상 ④: 새 `src/test/java/kr/ejg/toolbox/core/check/CleanupOrderTest.java`
  - `fixturesGolden`(위 픽스처 + PosOrder 둘 → 글 골든 + `golden/cleanup/order-steps.json`) · 적용된 파일은 `JavaRules` 로 다시 검사해 `java.declarationOrder` 0 단언(건너뛴 몸체 있는 파일은 그 몸체 줄만 남음) · 줄 수·줄 전체 다중집합·토큰 다중집합 같음 단언(시험이 독립으로 다시 잰다).
  - `skipsAreNamed`: Skip 일곱의 사유 글이 §3 의 ①~⑦ 과 같고 각 `Skip*.java` 가 제 사유로만 건너뛴다.
  - `ignoreModifiersKey`: `IgnoreMods.java` 골든.
  - `ownSourcesUnchanged`: `src/main/java`·`src/test/java` 전부에 `OrderFix.apply` → 글 그대로(0-61e 가 위반 0 을 보증).
  - `idempotent`: 골든 결과에 다시 `apply` → 같은 글.
- 대상 ⑤: 새 `src/test/java/kr/ejg/toolbox/core/check/CleanupCorpusTest.java`(`@Tag("corpus")`) — `CheckCorpusTest` 무리(egov·commons·jspwiki·roller)의 Java·JS·JSP 를 `RuleSet.load(egov 프로필)` + `Cleanup(rules, RULES, "LF")` 로 전부 `clean`(쓰기 없음). 단언(등급 A, `CorpusFiles.none`): `skips` 에 「구문이 깨짐」·「재검 실패」 0 · 적용한 파일마다 `after` 의 parseError 0 · `ORDER` 를 적용한 파일마다 건너뛴 몸체 밖 `java.declarationOrder` 0 · ③ 적용 파일의 줄 수·줄 다중집합·토큰 다중집합 같음(시험이 독립으로 다시 잰다) · `before.size() >= after.size()`. 골든 `golden/corpus/cleanup-<무리>.json` = `{files, changed, steps: {stage: n}, skips: {reason: n}}`(등급 B 꼴로 갱신은 `-Dgolden.update`). 실행 시간을 이력에(JavaParser 두 번 + 재검사 — 3분 넘으면 알림만).
- 검증:
  1. `bash scripts/mvn.sh -q -B -Dtest='CleanupOrderTest,CleanupTest,JavaRulesTest,CheckRulesTest' -Dsurefire.failIfNoSpecifiedTests=false -Dgolden.update=true test` → 골든 생성. **`order-PosOrder.txt`·`order-PosOrderEdge.txt` 를 줄마다 읽어** 규약 순서·주석 동반·구획 주석 자리를 확인(틀리면 7절). `git diff src/test/resources/golden/check/` 비어 있음.
  2. `-Dgolden.update` 빼고 초록 → `validate` 초록 → `bash scripts/corpus-check.sh` → `bash scripts/mvn.sh -q -B -Dgroups='corpus & !db' -Dtest=CleanupCorpusTest -Dsurefire.failIfNoSpecifiedTests=false -Dgolden.update=true test` → `cleanup-*.json` 넷을 읽어 무리별 changed·steps·skips 를 이력에(skip 사유가 §3 의 ①~⑦ + 「재검 실패」 + 파일 공통 전제 둘 안이어야 — 새 사유가 보이면 7절. **「재검 실패」·「구문이 깨짐」 은 0 이어야** — 하나라도 있으면 수정기 결함, 7절).
  3. `bash scripts/verify.sh` 초록. 커밋 `feat: 5-34b 선언 순서 수정기 OrderFix`.
- 의존: 2.

**스텝 4 — 5-34c `CleanupRunner`**
- 대상 ①: 새 `src/main/java/kr/ejg/toolbox/core/check/CleanupRunner.java`
  ```java
  public final class CleanupRunner {
      public record FileRow(String rel, String hash, String outHash, int before, int after, boolean changed, String lineEnding, List<Cleanup.Step> steps, List<Cleanup.Skip> skips) { }
      public record Preview(List<FileRow> rows, int files, int skipped, int changed, boolean truncated) { }
      public record Written(String rel, boolean ok, String reason, String backup) { }
      public record Applied(List<Written> rows, int written, int failed, int stale, Path report) { }

      /** 미리보기 — 쓰지 않는다. globs 는 wanted 규칙들의 globs 합집합. changed 가 있으면 그 파일만 */
      public static Preview preview(String root, RuleSet rules, Collection<String> wanted, String lineEnding, LocalFiles files, JobContext ctx,
              Predicate<String> changed, List<String> exclude) throws IOException
      /** 적용 — rel 마다 다시 읽어 hash 가 같고 다시 계산한 결과의 outHash 도 같을 때만 checkEncodable → LocalFiles.write. 결과 글 파일을 reportDir/정리_결과.txt 에 */
      public static Applied apply(String root, RuleSet rules, Collection<String> wanted, String lineEnding, LocalFiles files, JobContext ctx,
              List<FileRef> refs, Path backupRoot, Path reportDir) throws IOException
      public record FileRef(String rel, String hash, String outHash) { }
      static String hash(String encoding, String lineEnding, String text)   // SHA-256 hex of encoding + "|" + lineEnding + "|" + text (UTF-8)
      static List<String> globs(RuleSet rules, Collection<String> wanted)
  }
  ```
  루프·진행률·취소·읽기 실패 처리는 `CheckRunner.run` 과 같은 꼴(50개마다 progress). `apply` 는 요청 순서대로, 취소되면 그때까지 쓴 것은 그대로(4-20). `Written.reason`: 「미리보기 뒤 바뀜」(hash)·「미리보기와 결과가 다름」(outHash — 규칙·프로필이 바뀜)·「바뀐 것 없음」·`Refused`/`IOException` 메시지(`checkEncodable` 거절 포함). 결과 글: 첫 줄 「코드 정리 <stamp> — 폴더 <root> · 덮어씀 n · 실패 k · 낡음 s」, 파일마다 「<rel> · <단계,…> · 전 n → 후 m · <skip 사유;…>」, 실패는 「<rel> · 실패: <사유>」.
- 대상 ②: 새 `src/test/java/kr/ejg/toolbox/core/check/CleanupRunnerTest.java`(`@TempDir`) — 폴더에 `fixtures/cleanup/` 복사 + MS949·CRLF 로 저장한 Java 하나(쓰지 않는 import 든) + `c.txt`(glob 밖): `previewWritesNothing`(바이트 전부 그대로, rows 에 changed 파일·hash 있음) · `applyBacksUpAndKeepsEncoding`(MS949·CRLF 그대로, 백업 원본 바이트 같음, `written` 수, 결과 글 파일에 코드 줄 없음 — `import` 글자 없음) · `staleHashIsSkipped`(미리보기 뒤 파일을 고치고 적용 → `stale 1`, 바이트 그대로) · `changedOnlyPredicate`(술어로 한 파일만) · `eolOnlyChangesLineEnding`(CRLF 파일 + 프로필 LF → 글 같고 LF 로 써짐).
- 검증: `bash scripts/mvn.sh -q -B -Dtest=CleanupRunnerTest -Dsurefire.failIfNoSpecifiedTests=false test` 초록 → `validate` → `bash scripts/verify.sh` 초록. 커밋 `feat: 5-34c CleanupRunner — 폴더 미리보기·적용`.
- 의존: 2(③ 없이도 돈다 — 3 과 독립이지만 차례는 뒤).

**스텝 5 — 5-34d 라우트 + CLI**
- 대상 ①: `web/FsRoutes.java` — `STAMP_RE` 를 package-private 로, `static String stamp(String given)`(유효하면 그대로, 아니면 지금 시각) 를 뽑아 `/api/fs/write` 도 그걸 쓴다.
- 대상 ②: `web/CheckRoutes.java` — `:103-109` 의 changedOnly 검증을 `static String changedOnlyError(LocalFiles files, String path)`(문제 없으면 null, 아니면 400 에 실을 글) 로 뽑아 둘이 쓴다.
- 대상 ③: 새 `web/CleanupRoutes.java` — `register(Javalin app, JobManager jobs, LocalFiles files, Supplier<Optional<Profile>> active)`:
  - `GET /api/cleanup/rules` → `Cleanup.RULES` 차례로 `{id, group, severity, message, fix, enabled, expected}`(`enabled` = `CheckRoutes.load(profile, null, null)` 에서 켜짐, `expected` 는 `file.lineEnding` 에만 프로필 `project.lineEnding` 또는 null).
  - `POST /api/cleanup/preview` `PreviewRequest(path, List<String> rules, Boolean changedOnly)` → path 검증(check·exists dir → 404 「폴더가 없다」)·`changedOnlyError` → 400 · `wanted` = rules ∩ RULES(비면 RULES 전부) · `jobs.submit("cleanup", …)` 안에서 변경분이면 `WorkingCopy.changed(root)::contains` → `CleanupRunner.preview` → `result = {files, skipped, changed, truncated, rows}` → `emit(RESULT)` → 202 `{jobId}`.
  - `POST /api/cleanup/apply` `ApplyRequest(path, List<String> rules, List<CleanupRunner.FileRef> files, String stamp)`(`FileRef(rel, hash, outHash)`) → 같은 검증 + `files` 비면 400 「덮어쓸 파일이 없다」 · `stamp = FsRoutes.stamp(req.stamp())` · `backupRoot = FsRoutes.backupRoot(active, stamp)` · `reportDir = FsRoutes.outRoot(active, stamp)` → job → `{written, failed, stale, stamp, backup: backupRoot, report, rows}` · `files.remember(root)`.
  - `App.create` `:179` 뒤에 `CleanupRoutes.register(app, jobs, localFiles, active);`.
- 대상 ④: `cli/CodeCommands.java` — `Cleanup extends BatchCommand`(`@Command(name = "cleanup", description = {"폴더의 코드 검사 지적 가운데 자동으로 고칠 수 있는 일곱을 미리보기한다(쓰지 않음). --apply 면 바뀐 파일 전부를 백업 뒤 덮어쓴다.", "규칙은 프로필 codecheck 와 같다. 결과 글은 out/<프로필>/<시각>/정리_결과.txt"})`: `@Parameters folder`, `@Option --apply`, `--changed-only`, `--rules`(split ","). 본문: preview job → 사람 글 「정리 미리보기 — 파일 n · 바뀜 m · 건너뜀 k」 + 바뀐 파일마다 「  <rel> · <단계,…> · 전→후」(stderr 아님, stdout) → `--apply` 면 `changed` 행으로 apply job → 「덮어씀 ok/n · 실패 k · 낡음 s\n<backup>\n<report>」. `Main` `subcommands` 에 `CodeCommands.Cleanup.class`(`Check.class` 뒤). README 표 `check` 행 뒤: `| \`cleanup <폴더>\` | 코드 정리 미리보기(쓰지 않음). \`--apply\` 면 바뀐 파일을 백업 뒤 덮어쓴다, \`--changed-only\`·\`--rules a,b\` |`.
- 대상 ⑤: 새 `web/CleanupRoutesTest.java`(`CheckRoutesTest` 꼴 — 프로필 `project.lineEnding: LF`, `fixtures/cleanup` 복사): `rulesListsSeven`(7, `file.lineEnding` 의 `expected == "LF"`) · `previewThenApply`(preview rows → apply 로 바뀐 파일 전부 → `written == changed`, 백업 폴더에 원본, 결과 글 파일 존재, 같은 stamp) · `staleHashRefused`(파일을 고친 뒤 → `stale`) · `outHashRefused`(`outHash` 를 틀리게 보내 → `stale`, 바이트 그대로) · `refusals`(폴더 없음 404·files 비면 400·changedOnly 비-VCS 400). `cli/CodeCliTest` 에 `cleanupPreviewAndApply` 하나(`--apply` 뒤 끝 코드 0, 출력 「덮어씀」).
- 검증: `bash scripts/mvn.sh -q -B -Dtest='CleanupRoutesTest,CheckRoutesTest,FsRoutesTest,CodeCliTest,ReadmeCommandsTest,MainTest' -Dsurefire.failIfNoSpecifiedTests=false test` 초록 → `validate` → `bash scripts/verify.sh` 초록. 커밋 `feat: 5-34d CleanupRoutes + CLI cleanup`.
- 의존: 4.

**스텝 6 — 5-34e 화면 + 런처**
- 대상 ①: 새 `src/main/resources/tools/code_cleanup.html`(코드 검사 머리·`.dirbar` 꼴 복제, 화면 `<style>` 은 이 화면만의 것 — 표 열 너비·`.skip` 회색 정도):
  - `<head>`: `common.css` link 가 첫 `<style>` 앞, `<script src="/tools/common.js" defer>`·`code_cleanup_ext.js defer`.
  - `<body data-profile="프로젝트 루트 · 백업 폴더">`, `<header><h1>코드 정리</h1><span class="opt">고른 규칙의 지적을 폴더의 파일에서 자동으로 고친다 — 미리보기는 쓰지 않고, 덮어쓰기는 원본을 out/&lt;프로필&gt;/&lt;시각&gt;/backup 에 둔 뒤</span></header>`.
  - `.dirbar`: `폴더` + `#dir`(list `dirRecent`, placeholder `C:\project\src`) + `<datalist id="dirRecent">` + `<label class="opt" id="changedLabel"><input type="checkbox" id="changed" disabled> 변경분만</label>` + `#vcsInfo` + `<button class="btn-p" id="preview">미리보기</button>` + `<button class="btn-red" id="apply" disabled>체크한 파일 덮어쓰기(백업 후)</button>` + `<button class="btn-red" id="stop" disabled>중지</button>`.
  - `#rules.well`: 일곱 `<label><input type="checkbox" id="r_<id>" checked> <id> — <message></label>`(JS 가 `/api/cleanup/rules` 로 채운다, 처음엔 빈 well + `.hint` 「규칙을 읽는 중」).
  - `#res`(TB.result 칸) · 표 `<table id="rows" class="tb-table"><thead><tr><th><input type="checkbox" id="all" aria-label="전부"></th><th>파일</th><th>지적 전→후</th><th>적용한 단계</th><th>건너뜀</th></tr></thead><tbody></tbody></table>` · `#sum.opt`.
- 대상 ②: 새 `code_cleanup_ext.js`(ES5 IIFE, `innerHTML = ''` 만, `textContent`·`insertRow`):
  - `loadRules()` → 체크박스 일곱(`enabled` 아니면 unchecked+disabled+title 「프로필에서 꺼짐」, `file.lineEnding` 이 `expected` 없으면 unchecked+disabled+title 「프로필 project.lineEnding 없음」).
  - `loadRecentDirs()`·`vcsInfo()`(코드 검사 복제, `/api/check/vcs`) · `chosen()` → 체크된 규칙 id 목록.
  - `preview()`: `TB.api('/api/cleanup/preview', {body: {path, rules, changedOnly}})` → `poll(jobId, onDone)` 400ms · 중지 `DELETE /api/jobs/<id>` · 결과 `ROWS = result.rows`, `render()`(체크박스 기본 = `changed`), `sum()` 「파일 n · 바뀜 m · 건너뜀 k · 덮어쓸 대상 c」, `#apply.disabled = c === 0`. `TB.result('res','ok',{summary: '파일 n · 바뀜 m · 못 읽음 s', dir})`.
  - `apply()`: `todo` = 체크된 changed 행 → `confirm(todo.length + '개 파일을 덮어쓴다. 원본은 out/<프로필>/<시각>/backup 에 백업한다. 계속?')` 거절이면 return → `POST /api/cleanup/apply {path, rules, files: todo.map({rel, hash, outHash})}` → poll → 「덮어씀 ok/n개 · 실패 k · 낡음 s」 + `dir`·`backup`(R11; 일부 실패·중지는 `stop`) → 행에 결과 표시(`textContent`) → 적용 뒤 `#apply.disabled = true`(다시 미리보기 해야).
  - `TB.result` 호출은 `msg(state, summary, extra)` 한 자리로 모은다 → `RESULT_SITES.put("code_cleanup_ext.js", 1)`·`("code_cleanup.html", 0)`.
- 대상 ③: `tools/index.html` 코드 검사 카드(`:68`) 뒤에
  `<a class="tool-card" data-file="code_cleanup.html" data-status="ready" href="code_cleanup.html" target="_blank" rel="noopener"><div class="icon">🧹</div><div class="name">코드 정리</div><div class="desc">폴더의 코드 검사 지적 일곱(debugger·쓰지 않는 import·쓸모없는 tb-ignore·선언 순서·줄바꿈)을 같은 규칙으로 미리보기한 뒤 백업하고 덮어써 고친다. 변경분만·중지·결과 글.</div></a>`.
- 대상 ④: 시험 목록 — `LauncherTest`(메서드 `launcherHasTwelveCards`, 11 → 12 둘, 주석에 5-34e) · `BackendToolsTest` `@ValueSource` 에 `"code_cleanup"` · `SmokeHtmlUnitTest` `opensWithoutScriptErrors` 목록에 `"code_cleanup"` · `ToolsFolderTest.RESULT_SITES` 둘 · `scripts/puppeteer/visual.js` `PAGES` 에 `{ tool: 'code_cleanup', states: [{ name: 'preview', prep: async p => { await p.waitForFunction(() => document.getElementById('dir').value, { timeout: 5000 }).catch(() => {}); await click(p, '#preview'); await waitText(p, '#res', /^파일 \d+|중지|FAILED|ERROR|실패/, 30000); } }] }`.
- 대상 ⑤: `SmokeHtmlUnitTest.codeCleanupPreviewApply(@TempDir)`(`jspFormatterFolderBatch` 꼴): 프로필(`project.root`·`lineEnding: LF`·`output.dir`) + 폴더에 `fixtures/cleanup/javaimport/Pos.java`·`debugger/pos.jsp` 복사 + MS949·CRLF Java 하나 → 페이지 열기 → `#dir` 기본값 = root → `#preview` 클릭 → `#rows tbody tr` ≥ 2, `#sum` 끝 「덮어쓸 대상 n」 → `setConfirmHandler(false)` → `#apply` 클릭 → 한 번 물었고 글에 「개 파일을 덮어쓴다」, 바이트 그대로 → `true` → 「덮어씀 n/n」 으로 시작, `#res` class `tb-result res-ok`, 백업 파일 있음, MS949·CRLF 보존.
- 검증: `bash scripts/mvn.sh -q -B -Dtest='SmokeHtmlUnitTest,LauncherTest,BackendToolsTest,ToolsFolderTest,ToolsA11yTest,StaticFilesTest' -Dsurefire.failIfNoSpecifiedTests=false test` 초록 → `bash scripts/verify.sh` 초록. 손 확인 하나(실행자): `node scripts/puppeteer/visual.js b47`(집 검증, 콘솔 오류 0·버튼 판정) — node 가 없으면 못 돌렸다고 이력에. 커밋 `feat: 5-34e 코드 정리 화면 + 런처 카드`.
- 의존: 5.

**스텝 7 — 마무리**(CLAUDE.md 「마무리」, 머지 위임 없음)
- 독립 리뷰(`caveman:cavecrew-reviewer`, `git diff origin/main...HEAD`) — 되돌림 규칙·OrderFix 건너뜀 조건·hash·백업 경로·확인창을 보게 한다. 결함은 지금 고치고, 나머지는 행으로.
- `bash scripts/gate-probe.sh`(열 산다) → `bash scripts/verify.sh --corpus`(`CleanupCorpusTest` 포함) — push 와 따로. `--db` 는 안 돈다(pom·DB 코드 안 바뀜 — 끝 줄이 요구하면 그때).
- PROGRESS 「현재 상태」·PLAN 번들 47 행 완료 → 빠른 verify → 커밋 `docs: 번들 47 마무리`.
- `git push -u origin work/<날짜>` → `gh pr create --base main`(청크 표 + 「새 의존성 0 · 새 화면 1」) → CI 폴링 → claude[bot] 지적 처분 → 멈춤.
- 의존: 6.

## 5. 금지 사항
- `pure/`·코드 검사 화면(`code_check.html`·`code_check_ext.js`)·`rules.yaml` 판정·`golden/check/*`·`golden/corpus/check-*`·`order-*.txt` 수정 금지. `JavaRules.orderViolations` 추출은 판정을 한 글자도 안 바꾼다(`java.json`·`java-order-ignore.json`·`check-*.json` diff 0 이 증거).
- 파일 쓰기는 `LocalFiles.write` 로만. 새 쓰기 경로·`Files.write` 직접 호출 금지(결과 글 파일 `정리_결과.txt` 는 `out/` 안이라 `Files.writeString` 허용 — 사용자 소스 폴더엔 절대).
- 미리보기가 파일을 쓰거나, 적용이 hash 검사 없이 쓰거나, 확인창 없이 덮어쓰는 것 금지(CLI `--apply` 만 예외).
- 건너뜀 조건을 느슨하게 해서 corpus changed 수를 늘리기 금지. 되돌림 규칙의 허용 목록에 규칙을 더하기 금지(설계 §3 하나뿐).
- H2 저장·로그에 코드 본문 금지(규칙 3). 결과 글 파일에 코드 줄 금지(시험이 `import` 글자로 잰다).
- 새 의존성 금지. 화면은 바닐라 ES5·CDN 금지·`innerHTML` 은 `''` 만. 화면 `<style>` 에 `:root`·`.btn-*`·고정 색 금지.
- `verify.sh` 와 `git commit`, `--corpus` 와 `git push` 한 체인 금지. 맨몸 `mvnw` 금지. 우리 코드 선언 순서(0-61e) 어기기 금지 — `validate` 가 막는다.

## 6. 최종 검증
- `CleanupTest`·`CleanupOrderTest`·`CleanupRunnerTest`·`CleanupRoutesTest`·`CodeCliTest`·`SmokeHtmlUnitTest.codeCleanupPreviewApply`·`LauncherTest` 12 초록 · 기존 `JavaRulesTest`·`CheckRulesTest` 골든 불변.
- `bash scripts/verify.sh` 초록 · `--corpus` 초록(`CleanupCorpusTest` A 0 · 골든 넷) · `gate-probe.sh` 산다 10.
- 손 확인(실행자): `run.bat` 뒤 「코드 정리」 카드 → `src/test/resources/fixtures/cleanup` 을 미리보기 → 표에 파일·전→후·단계·건너뜀 → 체크 하나 풀고 덮어쓰기 → 확인창 취소 → 바뀐 것 없음 → 다시 확인 → 「덮어씀 n/n」 + 백업 경로 → `git checkout -- src/test/resources/fixtures/cleanup`(픽스처 복원!). 또는 `toolbox.bat cleanup <그 폴더 절대 경로>`(쓰지 않음).
- CI check-runs 전부 `completed success`.

## 7. 중단 조건
- 스텝 0 에서 PR #78 의 check-run 이 초록이 아니거나 머지가 거절됨.
- 스텝 2·3 검증 1 에서 골든이 픽스처 요건과 다르고 한 번 고쳐서 안 맞음, 또는 `golden/check/` 에 diff 가 생김(판정이 바뀐 것).
- 스텝 3 corpus 에서 「구문이 깨짐」·「재검 실패」 되돌림이 1 이상(파이프라인이 잡았어도 수정기 결함) · 새 skip 사유가 §3 목록 밖 · `ownSourcesUnchanged` 빨강.
- 스텝 2·3 에서 §3 의 건너뜀·되돌림 조건을 **느슨하게 해야** 골든·corpus 가 맞을 것 같을 때(금지 — 멈추고 보고).
- 스텝 4·5 에서 `LocalFiles.write` 가 거절하는 경로를 우회하고 싶어짐(금지 — 멈추고 보고).
- 스텝 6 에서 `ToolsA11yTest`·`ToolsFolderTest` 가 한 번 고쳐서 안 풀림.
- 검증 2회 연속 실패·원인이 플랜 밖·스텝에 없는 파일 3개 이상.
- 보고: 어느 스텝, 예상과 다른 것, 선택지.
