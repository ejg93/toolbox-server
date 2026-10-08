# 설계 18 — 번들 32: 화면 테마 통일(common.css) · 표준 사전 화면 재배치 마감 · 시각 검증 하네스 (Fable, 2026-10-08)

이 플랜은 실행 담당 모델(Opus)이 그대로 따라 실행한다. 실행자는 탐색 과정을 모른다. 판단이 필요한 지점을 남기지 않았다. 번들 모드 규칙(CLAUDE.md 「번들 모드」) 그대로 — 갈려도 안 멈추고, 행당 「정한 것(계획 밖)」 둘까지.

사용자가 2026-10-08 정한 것(뒤집지 않는다):
- 도구 화면 CSS 를 **공용 하나(`tools/common.css`)로 통일**한다. 범위는 「화면 전체 테마」(색 토큰·본문·카드·입력칸·표·버튼·탭·메시지).
- 테마는 **켜고 끌 수 있는 구조로 일괄** — 사용자는 어둡게로 고정해 쓰지만 구성은 모든 화면이 같은 스위치를 따라야 한다.
- `sql_snippets.html` 은 글자 고정(D5)이라 **common.js 가 런타임에 `<link>` 를 끼운다**.
- 버튼 색 체계는 추천안: 실행 = 파랑 `.btn-p` · 파일 = 도는 테두리 `.dl`(xlsx 는 엑셀 아이콘, csv 는 글만) · 복사 = 초록 `.btn-green` · 위험(지우기·비우기·삭제·덮어쓰기·중지) = 빨강 글자 `.btn-red` · 나머지 보조 = 회색 채움(기본).
- 「표준 사전 · 논리명」 화면 재배치(한 열 · 입력 통합 · CSV 파일 · 둘 중 하나 고르기 · DB 유형은 스냅샷에서 · 마스킹 전체 선택 · 버튼 색)와 **DB COMMENT 실행 삭제**는 이미 `wip/logical-ui-layout` 에 커밋돼 있다 — 이번 번들이 시험을 붙여 닫는다.
- 검증은 사용자가 「하라」 할 때까지 미뤘다 → 이 번들에서 몰아서 한다. CSS 검증은 AI 도구(Puppeteer 스크린샷 + 모델 판독 + 독립 리뷰)를 최대로 쓴다. 사용자가 끝에 전체 검수한다.

## 1. 목표와 범위

- **목표**: 도구 열둘이 한 색 체계·한 버튼 체계·한 테마 스위치를 따르고, 「저장」 버튼이 배경에 묻히지 않으며, 표준 사전 · 논리명 화면의 새 배치가 시험으로 닫히고, 화면 변화를 스크린샷·계산 스타일로 기계가 먼저 잡아내는 하네스가 생긴다.
- **In**: 행 `S0`(잔손) → `3-13` → `3-14` → `1-48`(하네스·기준 촬영) → `0-51`(common.css 토큰·테마 토글·link 주입) → `1-49`(카드형 셋) → `1-50`(런처·특수문자) → `1-51`(앱형 셋) → `1-52`(jsp_formatter·table_builder) → `1-53`(dev_tools) → `1-54`(강제 시험 + 사후 촬영·판독·수정) → `1-55`(문서) → 마무리.
- **Out**: `pure/` · `sql_snippets.html` 본문 · dev_tools 의 인라인 `style=""` 193개와 JS `.style.display` 토글(HtmlUnit 이 CSS 끈 채 inline style 로 보임을 잰다 — B5) · 순수본에서 베껴 온 JS 함수 한 줄(D16, 예 `dev_tools.html:2020` `btn.className='btn-g'`) · `.grid` 레이아웃(화면마다 인라인 유지, B3) · `.tb-table`·배지 CSS 를 common.js 밖으로 빼기(B2) · 탭 **동작**(JS) 변경 — 탭은 **꼴만** 통일 · 새 의존성(이미지 비교 패키지 포함 — 캔버스로 직접 센다) · 내려받기(브라우저 Blob) 추가 · 반입 zip 구조.

## 2. 현재 구조 요약

### 2.1 가지 상태
- `work/2026-10-07c` = 번들 31, PR #51 열림(CI 초록, 머지 위임 없음, **아직 안 머지**). `wip/logical-ui-layout` 은 그 끝 `268466d` 위에 wip 커밋 7개(`0da75f3`→`f1a6c65`): 한 열 배치 · 사전을 입력에 합침 · CSV 파일 선택 · 변환 카드·COMMENT 실행 삭제 · 필수/선택 표시·둘 중 하나 · DB 유형 자동·마스킹 전체 선택 · 버튼 색 · xlsx 아이콘. **검증 도장 없음**(사용자 지시로 미룸). `pr-guard` 가 열린 작업 PR 이 있으면 새 PR 을 막는다.
- wip 커밋이 바꾼 파일: `src/main/resources/tools/logical_name.html` · `web/LogicalRoutes.java`(`registerApply`·`IDENT`·`checkIdent`·`ApplyRequest` 삭제, `register(app, dict, snapshots, active)` 4인자, `dialectFor(asked, snapshotId, snapshots)` 추가) · `web/App.java:172`(4인자 호출) · `cli/LogicalCommands.java`(머리 주석) · `core/meta/SnapshotStore.Summary.dialect()`(`@JsonProperty("dialect")`, `TypeMapping.dialectOf(dbVersion)`) · 삭제 `core/logical/CommentApply.java`·`CommentApplyTest.java` · `LogicalRoutesTest`(apply 시험 삭제, `with()` 도우미).

### 2.2 도구 CSS 두 계열(조사 2026-10-08)
- **A 전체화면 앱형, 어둡기 고정**: code_check · program_analysis · spring_source_generator · jsp_formatter · dev_tools · table_builder · (sql_snippets 동결). `body{height:100vh;flex;overflow:hidden}`, `header`, `.opt`, 입력칸 `#3c3c3c/#555`, 버튼 테두리 없음·hover `opacity:.85`. 고정 색이 많다 — `#111`(우물), `#2a2a2b`(탭 바탕), `#2a2d2e`(행 hover), `#04395e`·`#094771`(선택), `#9cdcfe`(코드 글), `#ccc`.
- **B 카드 스크롤형, 밝은 테마 있음**: db_browser · deliverable_sql · logical_name. `body{padding:16px}`, `h1`·`.sub`·`.card`·`label`·`.msg`·`.tbl`, 입력칸 `var(--bg)/var(--border)`, 버튼 투명+테두리(logical_name 은 `--btn` 토큰 회색 채움으로 이미 바꿈).
- 따로: index(런처, 밝은 테마 있음) · special_chars(밝은 테마 있음, **토큰 이름이 다르다** `--card --ink --sub --line --accent-soft --chip --toast`).
- 기본 6 토큰 값은 11개 파일 전부 같다(`--bg #1e1e1e --surface #252526 --border #3c3c3c --text #d4d4d4 --muted #858585 --accent #0078d4`). 밝은 팔레트도 다섯 파일이 같다(`#f7f9fc #fff #dde4ee #26303e #6b7686 #3f6fb5`, `--red #c62828 --green #2e7d32`). 추가 토큰: `--green #16825d --red #f44747 --yel/--yellow #d7ba7d --teal #4ec9b0 --amber #d7a316(밝음 #b26a00) --th-bg #2d3a4d --sel #264f78 --btn #3c3c3c --btn-hover #4a4a4a --btn-line #5a5a5a`(밝음 `#e6ebf2 #d8dfe9 #c3ccd9`).
- **같은 클래스 이름이 파일마다 뜻이 다르다** — `.bar`(B 진행 막대 / A 툴바) · `.box`(스크롤 틀 / 패널) · `.row` · `.tabs` · `.msg`(B 상태 줄 / code_check `#rules .rule .msg` 규칙 메모) · `.sub` · `.hint` · `.grid`. 공용 CSS 는 이 이름을 **새로 정의하지 않거나**(`.bar .box .row .tabs .grid`) 겹쳐도 안전한 것만(`.msg` — code_check 는 로컬 override) 쓴다.
- 탭 꼴 다섯 갈래: `.tabbar .t/.t.on`(code_check=program_analysis) · `.modes .t`(jsp)=`.dm-tabs .t`(dev_tools) · `.tabs .t`(db_browser, jsp 작은 것) · `.tab.active` 밑줄(dev_tools 상단) · `.b64-tab/.url-tab` 알약 · `.sql-db-btn`/`.db-tab` DB 색 테두리 · `.seg button.on`(special_chars) · `.radio-btn.active`·`.item.on`·`.opt-card.on`. `t`/`on` 클래스는 JS 가 붙인다(code_check_ext:223, program_analysis_ext:57, jsp_formatter_ext:26·265, dev_tools_ext:194, db_browser:246).
- 버튼 현황은 `PROGRESS.md` 이력 2026-10-08 조사와 같다 — 파일 만드는 버튼: xlsx 다섯(code_check `#xlsx`·`#depXlsx`, program_analysis `#xlsx`, table_builder `#xlsxBtn`, deliverable_sql `#build`) · sql 하나(deliverable_sql `#ddlSave`) · java(db_browser `#dtoSave`, spring `#run`). 어긋난 색: DANGER 인데 `.btn-p`(dev_tools `#td_batchApply`, jsp `#dirApply`) · NAV 인데 `.btn-p`(dev_tools 전체화면 보기) · NAV 인데 `.btn-green`(table_builder HTML 불러오기) · COPY 인데 `.btn-g`(dev_tools :307 날짜 복사, dev_tools_ext.js:118 「복사 n」).

### 2.3 common.js · 정적 서빙
- `common.js`(IIFE, Rhino 호환 — fetch·async·`?.` 금지): `injectStyle()` 49~65 가 `<style id="tb-common-style">` 로 `#tb-mode-badge`(`.on .off .down`)·`.tb-table` 를 끼운다. `badge()` 114~128: `location.protocol==='file:'` 면 「순수」, 아니면 `/api/ping` → `on` + `/api/alive` SSE. 끝에 `window.TB = {api, badge, table, sse, snapLabel, joinPath, savedText, copy}`.
- `App.java:149-153` 정적 파일 `hostedPath=/tools, directory=/tools, CLASSPATH`. 확장자 허용 목록·CSP 없음. `.css` 는 Jetty 기본 mime `text/css`(`jetty-http-11.0.25` `mime.properties` 실측 — 8장 1).
- 지문 레인: `src/main/resources/tools` 는 **java 레인**(`verify-fingerprint.sh:34-36`) — 바꾸면 `mvn test/verify` 가 돈다. `scripts/puppeteer` 는 tools 레인(셸 문법·settings 회귀만).

### 2.4 시험이 보는 것(바꿀 때 걸리는 것)
- `ToolsFolderTest`: `fileNamesAreAsciiLowerSnake`(`.css` 허용) · `noExternalLoads`(`/tools/common.css` 통과, common.css 본문도 검사 — `url(http` 금지) · `sqlSnippetsIsPurePlusCommonJs`(**B1** — sql_snippets 글자 고정) · `deliverableCardsAreOneColumn`(**B3** — deliverable_sql 안의 `.grid {` 에 `grid-template-columns: 1fr;`) · `copyGoesThroughTbCopy`(「복사했다」「복사함」 글 금지 — CSS 주석에도) · `logicalNameRenamed`(「논리명 변환기」 글 금지) · `logicalCandidateNumbersMatchDeliverables`(`data-kind="words">05 표준단어` — **`data-kind` 가 `>` 바로 앞 마지막 속성**) · `modeBadgeSitsBottomRight`(**B2** — common.js 의 `BADGE_ID + '{` 뒤 CSS 글에 `bottom:`·`right:`, `left:` 없음) · `extractedScriptsOnlyClearInnerHtml`(tools/*.js innerHTML 은 `''` 만).
- `StaticFilesTest.commonJsCssVarsHaveFallbacks`: common.js 의 모든 `var(--x` 에 대체값.
- `SmokeHtmlUnitTest`(CSS 꺼짐, dev_tools 는 JS 꺼짐): `dbBrowserActionButtonsArePrimary`(:241 — `snapTake dtoTable dtoFromDdl diffRun` 클래스에 `btn-p` 포함, `connTest snapStop dtoSave dtoCopy` 는 **포함 안 함** — contains 비교라 `btn-primary` 같은 이름 금지) · `deliverableGuideSwitchesDialect`(`#guide button` textContent 가 정확히 「복사」, `.mark`·`pre.sql`·`.msg` 유지) · `crudGeneratorRuns`(`.opt input[type=checkbox]` 0개 — `.opt` 이름 유지) · `dbBrowserDtoTabsKeepOwnResult`·`codeCheckFolderRun:975`(**B5** — 인라인 style display 로 보임을 잰다) · `logicalNameRunsFromCsv`(`#csv` textarea 에 setText — 숨겨도 됨) · `opensWithoutScriptErrors`(12 화면 스크립트 오류 0 + 배지 「백엔드 연결」).
- `BackendToolsTest.toolIsServedWithCommonJs:32-38`: 10 도구에 `<script src="/tools/common.js" defer></script>` 가 `</head>` 앞.
- `scripts/puppeteer/smoke-devtools.js:90,115` `#dm-create button.btn-p` 하나만 맞아야 함 · `:63,95` `e.style.display`. `smoke-dbbrowser.js`·`smoke-tabs.js` `#conns .item`.
- `scripts/probes/iframe-sandbox.patch` 가 `table_builder.html` iframe 줄(지금 155) 문맥을 쥔다 — `<style>` 을 줄이면 오프셋이 바뀌어 gate-probe 가 「패치 갱신 필요」 를 낸다(마무리 2 에서 다시 뜬다).
- `design/14-ui-demo.md` D16: 순수본에서 온 JS 함수는 안 고친다. `PLAN.md` 0-40: portfolio 패치를 백엔드본에 `patch` 로 얹는다 — `<style>` 큰 변경은 다음 패치가 안 맞을 수 있다(알고 간다, 사람 몫에 적는다).

### 2.5 Puppeteer(집 검증 전용)
- `require('C:/workspace/node_modules/puppeteer')`, `headless: 'new'`, 스크린샷 `target/puppeteer/`. 가장 좋은 본은 `scripts/puppeteer/smoke-dbbrowser.js`(임시 폴더 + H2 `INIT=RUNSCRIPT` 프로필 + 서버 spawn `--no-browser --port --profile --profiles-dir --data-dir` + `/api/ping` 60×500ms 대기 + `pageerror`·console error·`status>=400` 수집 + `setViewport(1400×900)` + `finally` 정리). 탭 전환은 `smoke-devtools.js`. `networkidle0` 금지(ToolsFolderTest). `emulateMediaFeatures` 는 아직 아무 스크립트도 안 쓴다. 이미지 비교 패키지 없음(`C:/workspace/node_modules` 에 puppeteer 셋뿐). `.gitignore`: `target/ out/ data/ logs/`. 경로에 한글 금지(스크립트 경로).
- 화면이 뭘 먹어야 내용이 보이나: db_browser·deliverable_sql·logical_name·spring = 스냅샷(+접속) · code_check·program_analysis = `project.root`(픽스처 `src/test/resources/fixtures/analyze/{java,mapper}`, `fixtures/check/*`) · dev_tools·table_builder·special_chars·jsp_formatter(붙여넣기)·index·sql_snippets = 없음. 스냅샷 만들기: `POST /api/meta/snapshot {"connId":"h2"}` → `GET /api/meta/snapshots` 비어 있지 않을 때까지(비밀번호 없는 H2 는 `/api/conn/h2/password` 생략).
- 탭 선택자: dev_tools `#main-tabs .tab[data-tab=X]`(camel json date dummy regex b64 sql url jwt textdisplay bytes inlist xml enc fdiff logsql; dummy 안 `#dm-tab-create/#dm-tab-snap`) · code_check `#tabCheck #tabDeploy` · program_analysis `#tabPrograms #tabCrud #tabUnresolved #tabImpact #tabConsistency` · jsp_formatter `#tab-paste #tab-dir`, `#tab-out #tab-cmp` · db_browser `#dtoTabSnap #dtoTabDdl` · sql_snippets `.db-tab`(`switchDb`) · table_builder `input[name=mode]` · 나머지 단일 페이지(fullPage).
- 준비 신호: `#tb-mode-badge` 글이 「백엔드 연결」 로 시작(`opensWithoutScriptErrors` 와 같음).

### 2.6 함정
- HtmlUnit 4.11.1 은 `classList.toggle` 둘째 인자(force)를 버리고 `indeterminate` 가 없다 — 3-13 이 logical_name 의 2인자 toggle 을 `onOff()` 로 바꾸고, 중간 상태는 Puppeteer 가 잰다(8장 3).
- 공용 `.msg` 가 code_check `#rules .rule .msg`(규칙 메모)에 `min-height·margin` 을 더한다 → code_check 로컬 override.
- 공용 `label{display:block}` 이 A 계열 `.opt`(inline 라벨)와 `label.radio-btn`(dev_tools) 을 세운다 → `.opt label, label.inline, label.radio-btn, .check-row label { display:inline-flex }` 를 공용에 같이 둔다.
- `.dl` 은 `::before/::after` 를 다 쓴다 — 아이콘은 안의 `<span class="ico ico-xlsx">` 로만.
- table_builder 미리보기 iframe `srcdoc` 은 자기 `<style>` 을 만든다 — common.css 를 넣지 않는다.
- 밝은 테마에서 sql_snippets 는 공용 토큰만 바뀌고 자기 고정 색(`#111` 등)은 남는다 — 사용자는 어둡게 고정이라 알고 간다(8장).

## 3. 설계 결정

- **D1 [고정] 공용 CSS 자리와 연결** — `src/main/resources/tools/common.css` 하나. 편집 가능한 11 파일(10 도구 + index)은 `<head>` 의 **첫 스타일**로 `<link rel="stylesheet" href="/tools/common.css">` 를 정적으로 둔다(그 뒤 `<style>` 로컬이 같은 특이도에서 이긴다). common.js 는 로드되면 `document.querySelector('link[href="/tools/common.css"]')` 가 없고 `location.protocol!=='file:'` 일 때만 `<link>` 를 **`document.head.insertBefore(l, document.head.querySelector('style'))`** 로 첫 `<style>` 앞에 끼운다(없으면 끝) — sql_snippets 만 이 길로 간다. 앞에 끼우는 이유(실측 2026-10-08): sql_snippets 는 `textarea`·`input[type=text]`·`button`·`#toast` 를 요소·id 선택자로 정의한다(`sql_snippets.html:43-53`). 뒤에 끼우면 같은 특이도라 공용이 이겨 padding·색이 바뀐다. 앞이면 로컬이 이기고 공용은 토큰·리셋·로컬이 안 쓰는 속성(예 `button` 바탕 `var(--btn)` — 로컬은 `border:none` 만)만 채운다. 버린 대안: 전부 주입(FOUC) · 뒤에 주입(위) · sql_snippets 시험 뒤집기(D5·0-40 패치 호환 깨짐).
- **D2 [고정] 토큰은 common.css 에만** — `:root` 와 `@media (prefers-color-scheme)` 블록은 도구 `<style>` 에 남기지 않는다(1-54 시험이 막는다). 토큰 목록(어둡기 → 밝음):
  `--bg #1e1e1e→#f7f9fc · --surface #252526→#fff · --border #3c3c3c→#dde4ee · --text #d4d4d4→#26303e · --muted #858585→#6b7686 · --accent #0078d4→#3f6fb5 · --red #f44747→#c62828 · --green #16825d→#2e7d32 · --yel #d7ba7d→#8a6d00 · --teal #4ec9b0→#0f766e · --amber #d7a316→#b26a00 · --well #111→#eef2f7(코드·결과 우물 바탕) · --code #9cdcfe→#1f4e8c(코드 글) · --raised #2a2a2b→#e9eef5(탭·입력 바탕) · --hover #2a2d2e→#e3e9f1(행 hover) · --sel #04395e→#cfe0f5(선택 행) · --sel2 #094771→#b9d2f0(짙은 선택) · --th-bg #2d3a4d→#dfe7f2 · --btn #3c3c3c→#e6ebf2 · --btn-hover #4a4a4a→#d8dfe9 · --btn-line #5a5a5a→#c3ccd9 · --accent-soft rgba(0,120,212,.16)→#e8f0fb · --chip #2d2d30→#eef3fa · --toast #0d1117→#26303e · --oracle #f0a500 · --mysql #00bcd4 · --pg #336791 · --ms #c586c0 · --sy #ce9178`(DB 색은 두 테마 같음). special_chars 의 `--card --ink --sub --line` 은 `--surface --text --muted --border` 로 **이름을 바꾼다**(그 파일 CSS·마크업 안에서만; common.js 대체값은 그대로 둔다 — StaticFilesTest).
- **D3 [고정] 테마 스위치** — 세 블록: `:root{…어둡기}` · `:root[data-theme="light"]{…밝음}` · `@media (prefers-color-scheme: light){ :root:not([data-theme="dark"]){…밝음} }`. common.js 가 로드 즉시(badge 보다 먼저) `localStorage['tb-theme']`(`'dark'|'light'`, 없으면 자동)를 `document.documentElement.setAttribute('data-theme', …)` 로 건다(없으면 `removeAttribute`). `badge()` 가 배지 위에 고정 버튼 `#tb-theme`(`position:fixed;bottom:30px;right:8px`, 글 「테마 자동」→「어둡게」→「밝게」 순환, `pointer-events:auto`)를 끼운다. localStorage 읽기·쓰기는 try/catch. 배지 CSS 글(`BADGE_ID + '{'` 뒤)은 손대지 않는다(B2). 버린 대안: OS 설정만 따르기(사용자가 「켜고 끌 수 있게」 를 요구) · 도구마다 토글(일괄이 아님). `defer` 라 OS 밝음 + 저장 어둡게일 때 첫 그림이 잠깐 밝다 — 받아들인다(8장).
- **D4 [고정] 버튼 체계** — common.css 가 정의: 기본 `button{background:var(--btn);color:var(--text);border:1px solid var(--btn-line);border-radius:4px;padding:5px 12px;font:inherit 12px;letter-spacing:.5px;cursor:pointer}` `button:hover:not(:disabled){background:var(--btn-hover)}` `button:disabled{opacity:.4;cursor:default}` · `.btn-p{background:var(--accent);border-color:var(--accent);color:#fff;font-weight:bold}` `.btn-p:hover:not(:disabled){background:var(--accent);opacity:.85}`(1-40 의 `:not(:disabled)` 이유 그대로) · `.btn-green{background:var(--green);border-color:var(--green);color:#fff}` · `.btn-red{background:var(--btn);color:var(--red);border-color:var(--red)}` · `.btn-g` 는 **기본과 같은 꼴의 별칭**(규칙 없이 비워 둔다 — 순수본 JS 가 붙이는 이름이라 남긴다, D16) · `.dl`(logical_name 의 도는 테두리 그대로, `::after` 바탕 `var(--btn)`, hover `var(--btn-hover)`, `prefers-reduced-motion` 멈춤) · `.ico`·`.ico-xlsx`(data-URI SVG). 도구 `<style>` 의 `button{}`·`.btn-*`·`.btn-del`·`.sec`·`.combine-wrap button` 규칙은 지운다. 마크업 재분류는 4장 각 행의 표대로. `.btn-del` 은 `.btn-red` 로 이름을 바꾼다(table_builder 마크업·CSS; JS 가 그 이름을 안 쓴다는 것을 grep 으로 확인). 「중지」 는 위험에 넣는다(설계 17 D2 의 `btn-g` 를 뒤집는다 — 사용자 추천안 수락).
- **D5 [고정] 공용 클래스(이름 충돌 회피)** — common.css 가 정의하는 것: 리셋 `*{box-sizing;margin:0;padding:0}` · `body{font-family:'Consolas','D2Coding',monospace;font-size:12px;background:var(--bg);color:var(--text)}`(높이·padding·flex 는 로컬) · `h1`·`.sub`(B 꼴) · `header`·`header h1`·`.opt`·`.opt label`·`.dirbar`(A 꼴, code_check 값) · `.card`·`.card h2`(logical_name 꼴 — 13px, accent 밑줄)·`.card h3` · `label{display:block;color:var(--muted);font-size:11px;margin:6px 0 3px}` + 인라인 예외(2.6) · `select, input[type=text], input[type=number], input[type=date], textarea {background:var(--bg);color:var(--text);border:1px solid var(--border);border-radius:4px;padding:5px 6px;font:inherit}` + `:focus{border-color:var(--accent);outline:none}` · `input[type=checkbox]{accent-color:var(--accent)}` · `input[type=file]` · `textarea{background:var(--well);resize:none}` · `.msg/.msg.err/.msg.ok`(`white-space:pre-wrap` 포함) · `.hint` · `.badge.req/.badge.opt` · `.tbl{overflow:auto;max-height:420px;margin-top:6px}` · `.well{background:var(--well);border:1px solid var(--border);border-radius:4px}` · `table.pick tr{cursor:pointer}` `.pick tr:hover{background:var(--hover)}` `.pick tr.sel{background:var(--sel)}` · `.progress{height:4px;background:var(--border);border-radius:2px;overflow:hidden}` `.progress>div{height:100%;width:0;background:var(--accent);transition:width .2s}` · 탭 `.t{font-size:11px;letter-spacing:1px;padding:4px 14px;background:var(--raised);color:var(--muted);border:1px solid var(--border);border-bottom:none;border-radius:4px 4px 0 0;cursor:pointer}` `.t.on{background:var(--accent);border-color:var(--accent);color:#fff}` + 알약 `.b64-tab,.url-tab{…같은 값, radius 4px}` `.b64-tab.active,.url-tab.active{accent}` · `#toast{position:fixed;…background:var(--toast);color:#fff;opacity:0;transition:opacity .18s}` `#toast.on{opacity:.9}` · `.step/.k/.v/.choice/.opt-card`(logical_name 에서 옮김 — 다른 화면도 쓸 수 있게) · 스크롤바(dev_tools 134~139 꼴). **정의하지 않는 것**: `.bar .box .row .tabs .grid .sub(dev_tools 뜻) .item .tab(.active) .sql-db-btn .db-tab .seg .radio-btn .snip-btn .cat .chars .c`(로컬 유지). `.tb-table`·`#tb-mode-badge` 는 common.js 주입 그대로(B2).
- **D6 [고정] 로컬 `<style>` 에 남기는 것** — 도구에만 있는 블록(2장 「9. 대형 로컬」): 레이아웃(`.top .col .bottom .split .main .left .right .grid .sidebar .splitter`), `#rules` 트리, `#crud` 매트릭스, `#cmp/#dRisk` diff, `#page-textdisplay`, `#grid` 편집기, `.ext-*`, `.date-*`, `.jwt-*`, `.sql-db-btn.*`, special_chars 칩·바·레벨, index `.cards/.card(링크)`. 고정 hex 는 토큰으로 바꾼다(`#111→var(--well)` `#2a2a2b→var(--raised)` `#2a2d2e→var(--hover)` `#04395e→var(--sel)` `#094771→var(--sel2)` `#9cdcfe→var(--code)` `#ccc→var(--text)` `#3c3c3c(입력 바탕)→var(--raised)` `#555→var(--border)`). diff 색(add/del)·CRUD 글자색·JWT 색·DB 색은 그대로 둔다(두 테마에서 읽힌다).
- **D7 [고정] 시각 하네스** — `scripts/puppeteer/visual.js <label>` 과 `scripts/puppeteer/visual-diff.js <before> <after>`. 출력 `out/visual/<label>/`(gitignore, `mvn clean` 에 안 지워짐). 자급 H2 픽스처(Docker 없음) — 데모 Oracle 은 쓰지 않는다(전후 비교에 데이터가 흔들린다). 행렬: 화면(13 = 12 도구 + index) × 탭(2.5) × 테마(dark·light — `page.emulateMediaFeatures([{name:'prefers-color-scheme',value}])`) × 폭(1400·375). 모든 샷 `fullPage:true`. 샷마다 `styles.json` 에 보이는 `button` 전부의 `{id, class, text, bg, color, border, afterBg(.dl), disabled, ancestorBg}` 와 판정, `console.json` 에 pageerror·console error·`status>=400`. 비교는 Puppeteer 안 캔버스로 픽셀을 센다(패키지 추가 없음). 판독은 **모델**: Opus 가 `Read` 로 PNG 를 보고, 도구별로 병렬 Agent 넷에 나눠 맡긴다. 버린 대안: pixelmatch 설치(새 의존성) · HtmlUnit(CSS 꺼짐) · 데모 환경(비결정적).
- **D8 [고정] 가지·커밋** — `work/2026-10-08` 을 `wip/logical-ui-layout` 끝에서 만든다. wip 커밋 7개는 그대로 둔다(제목 `chore: wip …` — 이력이 사실이다; squash 안 함). 새 커밋은 `<종류>: <행> <무엇>`. PR 은 **#51 이 머지된 뒤**(pr-guard) — 안 됐으면 push 까지 하고 멈춘다(7장). 머지 위임 없음.
- **D9 [고정] 손대지 않는 것** — 인라인 `style=""`(B5) · JS `.style.display` 토글 · 순수본에서 온 JS 함수(D16) · `.grid` 규칙(B3) · common.js 배지·`.tb-table` CSS 글(B2) · `pure/` · `sql_snippets.html` 본문 · 탭 동작 JS · 스모크가 누르는 id 전부.

## 4. 실행 스텝

행마다 끝에 「빠른 도장 `bash scripts/verify.sh` 초록 → `git commit`(따로)」. CSS 행(1-49~1-53)은 커밋 전에 `node scripts/puppeteer/visual.js work-<행>` 으로 그 행 화면만 찍어(`--only <도구,…>`) 콘솔 오류 0 과 `styles.json` 판정 0 을 본다. 중간에 멈춰도 — 각 행이 끝난 상태는 시험 초록·화면 동작 그대로다(공용 CSS 와 로컬 CSS 가 겹치는 과도기도 특이도·순서상 로컬이 이긴다).

### S0. 가지 · 잔손 · 설계 문서
- 대상: 가지 · `src/main/java/kr/ejg/toolbox/cli/LogicalCommands.java:87`(`--kind` 도움말) · `design/18-ui-theme.md`(새 파일) · `PLAN.md` 번들 표·분할표 · `PROGRESS.md` 상태.
- 변경: ① `git checkout -b work/2026-10-08 wip/logical-ui-layout`. ② `--kind` 도움말 글을 「words(05 표준단어)·domains(06 표준도메인)·terms(07 표준용어)·wordUse(공통표준단어 사용여부)」 로(3-10 과 같은 번호). ③ 이 플랜을 `design/18-ui-theme.md` 로 그대로 옮긴다(머리 두 줄만 「설계 18 …」 꼴). ④ `PLAN.md` 「번들」 표에 `| 번들 32 | S0→3-13→3-14→1-48→0-51→1-49→1-50→1-51→1-52→1-53→1-54→1-55 | 2026-10-08(Fable, 설계 18 — design/18-ui-theme.md). 화면 테마 통일(common.css·테마 토글·버튼 체계) · 표준 사전 화면 재배치 마감 · DB COMMENT 실행 삭제 · 시각 검증 하네스. 새 의존성 0. 머지 위임 없음 | Puppeteer(집) · #51 머지 | 진행중 |`. 분할표에 아래 행들을 「설계 18 S<n> 이 원본」 꼴로 더한다(3-13 · 3-14 · 1-48 · 0-51 · 1-49 · 1-50 · 1-51 · 1-52 · 1-53 · 1-54 · 1-55; 선행은 순서대로, 상태 「대기」). 번들 31 행은 #51 머지 뒤 「완료」 로 — 이 번들에서는 손대지 않는다. ⑤ `PROGRESS.md` 「진행중 청크」 를 번들 32 로, 이력에 「2026-10-08 설계 18 — 번들 32」 한 줄(해시는 이 커밋 뒤 채움).
- 검증: `grep -n "05 표준단어" src/main/java/kr/ejg/toolbox/cli/LogicalCommands.java` 1건 · `bash scripts/verify.sh` 초록(docs·java 레인).
- 의존: 없음.

### 3-13. 표준 사전 · 논리명 화면 재배치 — 시험으로 닫기
- 대상: `src/main/resources/tools/logical_name.html`(`readCsvFile`) · `src/test/java/kr/ejg/toolbox/web/SmokeHtmlUnitTest.java` · `src/test/java/kr/ejg/toolbox/web/LogicalRoutesTest.java` · `src/test/java/kr/ejg/toolbox/web/ToolsFolderTest.java`.
- 변경:
  - **`classList.toggle(name, force)` 를 쓰지 않는다** — HtmlUnit 4.11.1 의 `DOMTokenList.toggle` 은 인자 하나만 받아 둘째(force)를 버린다(javap 실측). `srcMode()` 의 두 줄 `$('optSnap').classList.toggle('on', !csv); $('optCsv').classList.toggle('on', csv);` 를 도우미로:
    ```js
    function onOff(el, c, on) { if (on) el.classList.add(c); else el.classList.remove(c); }
    // srcMode 안
    onOff($('optSnap'), 'on', !csv);
    onOff($('optCsv'), 'on', csv);
    ```
    (`logical_name.html` 의 다른 2인자 toggle 은 없다 — `grep -n "classList.toggle(" logical_name.html` 0건이 끝 상태. dev_tools 의 2인자 toggle 은 순수본 JS·HtmlUnit JS 꺼짐이라 손대지 않는다.)
  - `readCsvFile()` 은 그대로 둔다 — HtmlUnit 에 `TextDecoder`(ArrayBuffer·ArrayBufferView 받음)·`FileReader.readAsArrayBuffer`·`HtmlFileInput.setFiles(File...)` 가 다 있다(javap 실측). 단 HtmlUnit 의 TextDecoder 는 `{fatal:true}` 를 무시해 EUC-KR 대체 길은 실브라우저에서만 돈다 — 시험은 UTF-8 파일만 쓴다.
  - `SmokeHtmlUnitTest.logicalNameRunsFromCsvFile`: 페이지 열기 → `#srcCsv` 라디오 `click()` → `((HtmlFileInput) page.getElementById("csvFile")).setFiles(tmpCsv)`(UTF-8 로 쓴 `OWNER,TABLE_NAME,COLUMN_NAME,DATA_TYPE` + `S,TB_USE,USE_YN,CHAR` + `S,TB_USE,QWZX_CD,VARCHAR`) → `fireEvent("change")` → `waitForBackgroundJavaScript(3000)` → `#csvMsg` 가 `.csv · ` 와 `2행` 을 포함 → `#run` 클릭 → `#runMsg` 가 「컬럼 2 · 테이블 1」 로 시작 · `#rank` 에 `QWZX`. 그리고 `#optCsv` 클래스에 `on`, `#optSnap` 에 없음 · `#snap` 이 `disabled`.
  - `SmokeHtmlUnitTest.logicalNameChoiceCardsAreExclusive`: 스냅샷이 없는 기본 앱(example 프로필)에서 열면 `#srcCsv.checked` 참 · `#snapNone` 글 「스냅샷 없음」 포함 · `#dialectRow` 가 `#optCsv` 안에 있고 `hidden` 아님. (스냅샷 있는 쪽은 `logicalNameOpensFromDeliverable` 가 이미 `#snap` 값·`delivScope` 를 잰다 — 거기에 `assertTrue(page.getElementById("optSnap").getAttribute("class").contains("on"))` 과 `#dialectRow` `hidden` 참, `#snapDb` 글 「DB 유형 」 시작 한 줄씩 더한다.)
  - `SmokeHtmlUnitTest.logicalNameMasking` 끝에 전체 선택: `#maskAll` 클릭 → tbody 체크박스(enabled) 전부 `checked` 거짓 → 다시 클릭 → 전부 참. **`indeterminate` 는 HtmlUnit 의 `HTMLInputElement` 에 없다**(javap 실측) — 중간 상태는 1-48 `visual.js` 의 logical_name 확인(아래)이 실브라우저로 잰다.
  - `LogicalRoutesTest.dialectForFollowsSnapshotDbVersion`(`@TempDir Path tmp`): `Db db = Db.open(tmp); SnapshotStore store = new SnapshotStore(db);`(`SnapshotServiceTest.java:101-103` 꼴) · `long pg = store.save("t", "c", "", List.of(new Schema("PUBLIC", "PostgreSQL 16.1", List.of())));` · `long sy = store.save("t", "c", "", List.of(new Schema("dbo", "Sybase ASE 16", List.of())));` — `Schema(String name, String dbVersion, List<Table> tables)` 3칸 생성자(`Schema.java:15`), `save(profile, connId, note, schemas)`(`SnapshotStore.java:59`). 단언: `dialectFor(null, pg, store)` = `"postgresql"` · `dialectFor("mssql", pg, store)` = `"mssql"` · `dialectFor(null, null, store)` = `"oracle"` · `dialectFor(null, sy, store)` = `"oracle"`(못 정하면 옛 기본) · `dialectFor("", pg, store)` = `"postgresql"`. 끝에 `db.close()`. `dialectFor` 는 package-private `static` 이라 같은 패키지 시험에서 부른다.
  - `ToolsFolderTest.logicalNameInputIsOneCardWithThreeSteps`: `logical_name.html` 글에 `<h2>1. 입력</h2>` 뒤 `<h2>2. 설정</h2>` 앞 구간에 `class="k">① 공통표준단어`·`② 기관표준단어`·`③ 컬럼 목록` 셋과 `badge req` 1개·`badge opt` 2개 · `id="srcSnap"`·`id="srcCsv"` · 글 「CSV 붙여넣기」 없음.
- 검증: `bash scripts/mvn.sh -q test -Dtest='SmokeHtmlUnitTest#logicalName*,LogicalRoutesTest,ToolsFolderTest' -Dsurefire.failIfNoSpecifiedTests=false` 초록. `onOff` 두 줄을 옛 `classList.toggle('on', …)` 로 되돌리면 `logicalNameRunsFromCsvFile` 의 `#optCsv` `on` 단언이 빨강인지 한 번 확인하고 되돌린다(이력에 「빨강 확인」).
- 의존: S0.

### 3-14. DB COMMENT 실행 삭제 — 닫기
- 대상: `src/test/java/kr/ejg/toolbox/web/LogicalRoutesTest.java` · `src/test/java/kr/ejg/toolbox/web/ToolsFolderTest.java` · `design/14-ui-demo.md`(**읽기만** — 완료 설계는 안 고친다).
- 변경: `LogicalRoutesTest.applyRouteIsGone`: `post("/api/logical/comments/apply", Map.of("csv", …, "connId", "h2"))` 가 404. `ToolsFolderTest.noCommentApply`: `tools/` 어디에도 `comments/apply`·「COMMENT 실행」 글 없음, `src/main/java` 에 `CommentApply` 클래스 파일 없음(`Files.exists` 거짓). PLAN 3-9 행은 완료 행이라 안 고친다 — 3-14 행 비고에 「3-9 를 뒤집음(사용자 2026-10-08: 개발자는 DB 도구에서 실행)」.
- 검증: 위 두 시험 초록 · `grep -rn "comments/apply" src/` 0건.
- 의존: 3-13.

### 1-48. 시각 하네스 + 기준 촬영
- 대상: `scripts/puppeteer/visual.js`(새) · `scripts/puppeteer/visual-diff.js`(새) · `scripts/puppeteer/visual-fixture/`(새 — `ddl.sql`, `profile.yaml` 틀).
- 변경 `visual.js`(본은 `smoke-dbbrowser.js`; `require('C:/workspace/node_modules/puppeteer')`; `networkidle0` 금지):
  - 인자: `node scripts/puppeteer/visual.js <label> [--only tool1,tool2] [--port 41783]`. 출력 `out/visual/<label>/{png,styles.json,console.json,summary.txt}`.
  - 픽스처: `fs.mkdtempSync(os.tmpdir()+'/visual-')` 에 `ddl.sql`(표 다섯 + 뷰 하나, COMMENT 포함 — `TB_CUST_MST(CUST_ID, CUST_NM '고객명', MBTLNUM '휴대폰번호', EMAIL '이메일', REG_DT)`, `TB_ORDER_MST(ORDER_NO, CUST_ID, ORDER_AMT, ORDER_DT)`, `TB_CODE(GROUP_CD, CODE, CODE_NM, USE_YN)`, `TB_USE_HIST(USE_YN, QWZX_CD)`, `ZZ_SKIP(ZZQX_CD)`, `V_CUST AS SELECT …`) → `jdbc:h2:mem:visual;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM '<slash>'`. 프로필 `visual.yaml`: `connections[{id:h2, dialect:h2, url, user:sa}]`, `scope.schemas:[PUBLIC]`, `deliverable.filter.exclude.prefixes:[ZZ_]`, `project.root: <tmp>/proj`(여기에 `src/test/resources/fixtures/analyze/java`·`mapper` 복사 + `fixtures/check` 의 양성 파일 몇 개), `project.framework: egov35`, `project.encoding: UTF-8`, `project.lineEnding: LF`, `output.dir: <tmp>/out`. 서버 spawn 뒤 `/api/ping` 대기 → `POST /api/meta/snapshot {"connId":"h2"}` → `/api/meta/snapshots` 비어 있지 않을 때까지(60×500ms).
  - 화면 행렬(`SHOTS` 상수 배열; 항목 `{tool, name, prep(page) }`):
    `index` · `special_chars` · `sql_snippets`(oracle · mysql 탭: `page.evaluate(()=>switchDb('mysql'))`) · `table_builder` · `jsp_formatter`(붙여넣기 · `#tab-dir` 클릭) · `dev_tools`(탭 16 전부 `#main-tabs .tab[data-tab=X]` 클릭; dummy 는 `#dm-tab-snap` 도) · `db_browser`(초기 · `#dtoTabDdl`) · `deliverable_sql`(초기) · `logical_name`(초기 · `#run` 클릭 뒤 `#runMsg` 가 「컬럼」 시작까지) · `spring_source_generator`(초기) · `code_check`(`#runDir` 클릭 → `#msg` 가 「완료」 포함까지 10초 · `#tabDeploy`) · `program_analysis`(`#run` 클릭 → `#msg` 「완료」 까지 · `#tabCrud`).
    각 항목 × 테마 `['dark','light']`(`page.emulateMediaFeatures([{name:'prefers-color-scheme', value}])`, `localStorage.removeItem('tb-theme')` 뒤 reload) × 폭 `[1400, 375]`(`setViewport({width, height:900})`). 준비: `waitForFunction(() => /^백엔드 연결/.test((document.getElementById('tb-mode-badge')||{}).textContent||''))` 5초 → prep → `sleep(300)` → `screenshot({path, fullPage:true})`. 파일명 `<tool>-<name>-<theme>-<width>.png`.
  - `styles.json`: 샷마다 `page.evaluate` 로 보이는 `button`(`offsetParent!==null`) 전부 `{id, cls, text(앞 20자), bg, color, border, afterBg: getComputedStyle(el,'::after').backgroundColor, disabled, ancestorBg: 올라가며 첫 비투명 배경}` 과 토큰 값 `{accent, green, red, btn}` (`getComputedStyle(document.documentElement).getPropertyValue(...)` → 같은 페이지 안 임시 div 에 넣어 rgb 로). 판정(`after`·`work-*` 라벨에서만 실패로 센다, `before` 는 기록만): ① enabled 버튼의 칠(`.dl` 은 `afterBg`, 아니면 `bg`)이 `ancestorBg` 와 같으면 「배경에 묻힘」 ② 글/칠 대비(WCAG 상대 휘도) < 3.0 이면 「대비 부족」 ③ `.btn-p` 칠 ≠ accent · `.btn-green` 칠 ≠ green · `.btn-red` 글 ≠ red 면 「체계 어긋남」. `console.json`: `pageerror`, console `error`(「Failed to load resource」 제외), `status>=400`(favicon 제외) — 하나라도 있으면 실패.
  - 동작 확인(`checks` — 샷과 별개로 실브라우저에서 잰다, 실패하면 라벨과 무관하게 exit 1; `before` 에서도 돈다 — 3-13 뒤라 이미 통과해야 한다):
    ① logical_name — `#maskFind` 클릭 → `#maskTbl tbody tr` ≥ 2 까지 → `#maskAll` 클릭 두 번 뒤 행 체크 전부 참 → 첫 행 체크 해제 → `document.getElementById('maskAll').indeterminate === true`.
    ② logical_name — `#srcCsv` 클릭 → `#optCsv` 에 `on`, `#optSnap` 에 없음, `#snap.disabled`, `#dialectRow` 의 부모가 `#optCsv` · `#srcSnap` 클릭 → 반대 · `#snapDb` 글이 「DB 유형 」 로 시작.
    ③ (0-51 뒤부터) index — `#tb-theme` 클릭 → `documentElement.getAttribute('data-theme')==='dark'` → 클릭 → `'light'` → reload 뒤 그대로 `'light'` → 클릭 → 속성 없음 → `localStorage.getItem('tb-theme')===null`. 0-51 전에는 `#tb-theme` 가 없으니 「건너뜀」 으로 적는다.
    ④ (0-51 뒤부터) sql_snippets — `document.querySelector('link[href="/tools/common.css"]')` 가 있고 `document.head` 안에서 첫 `<style>` 보다 앞.
  - 끝: `summary.txt`(샷 수·판정 건수·오류 건수·checks 통과/실패/건너뜀) 출력, 판정·오류 있으면 `process.exit(1)`(before 라벨은 판정만 0 으로 봐준다 — 오류·checks 실패는 1).
- 변경 `visual-diff.js <beforeLabel> <afterLabel>`: 같은 이름 PNG 쌍마다 Puppeteer 빈 페이지에서 `file://` 로 둘을 `<img>` 에 올려 캔버스에 그리고(크기 다르면 큰 쪽으로, 빈 곳은 다름으로 셈) 채널 차 > 32 인 픽셀을 센다 → `out/visual/<after>/diff/<name>.png`(다른 픽셀 빨강 덧칠) · `diff.json`(`{name, changedPct}` 내림차순) · `report.html`(행마다 before|after|diff 세 그림 + %). 한쪽에만 있는 이름은 `only-before/only-after` 목록.
- 기준 촬영: `node scripts/puppeteer/visual.js before` → `out/visual/before/` 에 PNG 약 100장, `styles.json`. 콘솔 오류가 있으면 **지금 코드의 버그**다 — 고치지 말고 이력에 적고 넘어간다(이 행은 기록이 목적).
- 검증: `node scripts/puppeteer/visual.js before` 종료 0 · `ls out/visual/before/*.png | wc -l` ≥ 90 · `styles.json` 에 `logical_name-run-dark-1400` 의 `#run` 이 `.btn-p` 로 accent 칠 · `node scripts/puppeteer/visual-diff.js before before` 가 모든 쌍 0% 와 `report.html` 생성. `bash scripts/verify.sh`(tools 레인 — `bash -n` 만) 초록.
- 의존: S0(가지). 3-13·3-14 와 독립이지만 순서상 뒤(기준이 재배치 뒤 화면이어야 CSS 변화만 잡힌다).

### 0-51. common.css 토큰·테마 토글 · common.js link 주입
- 대상: `src/main/resources/tools/common.css`(새) · `src/main/resources/tools/common.js` · `src/test/java/kr/ejg/toolbox/web/StaticFilesTest.java`.
- 변경:
  - `common.css`: 머리 주석(「도구 공용 테마 — 토큰·본문·카드·입력칸·표·버튼·탭·메시지. 도구 <style> 은 그 도구에만 있는 것만. 토큰은 여기에만(ToolsFolderTest)」) + D2 토큰 세 블록 + D4 버튼 + D5 공용 클래스 + `.ico-xlsx` + `@keyframes dlspin` + `@media (prefers-reduced-motion: reduce)`. `url(` 은 data-URI 만. 글에 「복사했다」「복사함」「논리명 변환기」 없음.
  - `common.js`: IIFE 첫 줄들에
    ```js
    function theme() {
      var t = null;
      try { t = localStorage.getItem('tb-theme'); } catch (e) { /* 저장소 막힘 */ }
      if (t === 'dark' || t === 'light') document.documentElement.setAttribute('data-theme', t);
      else document.documentElement.removeAttribute('data-theme');
      return t;
    }
    function linkCss() {
      if (location.protocol === 'file:' || document.querySelector('link[href="/tools/common.css"]')) return;
      var l = document.createElement('link'); l.rel = 'stylesheet'; l.href = '/tools/common.css'; document.head.appendChild(l);
    }
    ```
    단 `linkCss` 의 끼우기는 `document.head.insertBefore(l, document.head.querySelector('style'))`(D1 — 첫 `<style>` 앞, 없으면 끝). `theme(); linkCss();` 를 `injectStyle()` 호출 앞에서 부른다(그래야 `tb-common-style` 보다 sql_snippets 자기 `<style>` 이 첫 `<style>` 이다). `badge()` 끝에 `#tb-theme` 버튼(없으면 만든다): 글은 `t===null?'테마 자동':t==='dark'?'어둡게':'밝게'`, 클릭 → `null→'dark'→'light'→null` 순환, `localStorage.setItem/removeItem` try/catch, `theme()` 다시. 스타일은 `injectStyle()` 글에 **배지 규칙 뒤에** 한 줄 더: `'#tb-theme{position:fixed;bottom:30px;right:8px;z-index:9999;padding:2px 8px;font-size:11px;font-family:…;background:var(--surface,var(--card,#252526));color:var(--muted,var(--sub,#858585));border:1px solid var(--border,var(--line,#3c3c3c));border-radius:3px;cursor:pointer;opacity:.9}'` — `var(` 마다 대체값(StaticFilesTest). 배지 규칙 글(`BADGE_ID + '{'` … `}`)은 바꾸지 않는다.
  - `StaticFilesTest.commonCssIsServed`: `GET /tools/common.css` 200, `Content-Type` 에 `text/css`, 본문에 `--accent` 와 `[data-theme="light"]`. `commonJsCssVarsHaveFallbacks` 는 그대로 통과해야 한다.
- 검증: `bash scripts/mvn.sh -q test -Dtest='StaticFilesTest,ToolsFolderTest,SmokeHtmlUnitTest#opensWithoutScriptErrors' …` 초록(모든 화면 스크립트 오류 0 — localStorage·setAttribute 가 Rhino 에서 도는지 여기서 드러난다) · `node scripts/puppeteer/visual.js work-0-51 --only sql_snippets,index` 종료 0 — sql_snippets 샷에서 `document.querySelector('link[href="/tools/common.css"]')` 가 있고(prep 에서 evaluate 로 확인해 summary 에 적는다), `#tb-theme` 클릭 두 번 뒤 `document.documentElement.dataset.theme==='light'` 와 reload 뒤에도 유지(이 확인은 visual.js 의 `index` 항목 prep 에 넣어 둔다 — 영구 시험).
- 의존: 1-48.

### 1-49. 카드형 셋 — db_browser · deliverable_sql · logical_name
- 대상: `src/main/resources/tools/db_browser.html` · `deliverable_sql.html` · `logical_name.html`.
- 변경(세 파일 공통 절차): ① `<head>` 의 `<style>` 바로 앞에 `<link rel="stylesheet" href="/tools/common.css">`. ② `<style>` 에서 지운다: `:root` 둘(어둡기·밝음) · `*{}` 리셋 · `body` · `h1` · `.sub` · `.card`·`.card h2`·`.card h3` · `label` · `select, input[type=text], textarea` · `button`·`button:hover`·`button:disabled`·`.btn-p`·`.btn-p:hover` · `.row` 는 **남긴다**(공용에 없음) · `.msg*` · `.tbl` · `.bar`·`.bar > div`(→ 마크업 `class="bar"` 를 `class="progress"` 로; `#bar` id 와 JS 는 그대로) · `.tabs .t`(db_browser) → 공용 `.t` 가 맡는다(`.tabs{display:flex;gap:3px;margin:8px 0 6px}` 컨테이너만 남김) · logical_name 의 `.btn`·`.dl`·`.ico`·`.step`·`.choice`·`.opt-card`·`.badge`·`.hint`·`label.inline`·`input[type=file]` 전부(공용으로 갔다). ③ 남는 로컬: db_browser `.grid .split .list .item(.on) .pane`·textarea 높이 등; deliverable_sql `.grid`(B3 — 글자 그대로) `.checks` `.mark-*` `pre.sql` `ul.notes` `.tb-table select`; logical_name `.tb-table input` `.warn`. ④ 고정 hex → 토큰(D6). ⑤ 버튼 재분류(마크업 class 만):

  | 파일 | 버튼 | 지금 | 바꿈 |
  |---|---|---|---|
  | db_browser | `#snapStop` 중지 | 기본 | `btn-red` |
  | db_browser | `#dtoSave` 파일로 저장(.java) | 기본 | `dl` |
  | db_browser | `#dtoCopy` 복사 | 기본 | `btn-green` |
  | db_browser | `#connTest` 접속 시험 | 기본 | 기본 |
  | deliverable_sql | `#build` 고른 문서 xlsx 만들기 | 기본 | `dl` + `<span class="ico ico-xlsx" aria-hidden="true"></span>` 글 앞 |
  | deliverable_sql | `#ddlSave` 파일로 저장(.sql) | 기본 | `dl` |
  | deliverable_sql | `#ddlCopy` 복사, JS 가 만드는 가이드·품질 「복사」(`:970`·`:1025` `createElement('button')`) | 기본 | `btn-green`(`b.className='btn-green'`; textContent 「복사」 그대로 — 스모크) |
  | deliverable_sql | `#codeFind #linkFind #qMake #qNoPk #ddlMake`(실행) | 기본 | `btn-p` |
  | deliverable_sql | `#codePreview` | 기본 | 기본 |
  | logical_name | (이미 재분류됨) | — | 변화 없음; `class="cand dl" data-kind=…` 순서 유지 |

  `style="margin-top:0"`·`style="width:auto"` 인라인은 그대로 둔다(B5·범위 밖).
- 검증: `bash scripts/mvn.sh -q test -Dtest='SmokeHtmlUnitTest,ToolsFolderTest,BackendToolsTest' …` 초록(특히 `dbBrowserActionButtonsArePrimary`·`deliverableCardsAreOneColumn`·`deliverableGuideSwitchesDialect`·`logicalCandidateNumbersMatchDeliverables`) · `node scripts/puppeteer/visual.js work-1-49 --only db_browser,deliverable_sql,logical_name` 종료 0(판정 0·오류 0) · `node scripts/puppeteer/smoke-dbbrowser.js` 통과 · `grep -c ":root" db_browser.html deliverable_sql.html logical_name.html` 전부 0.
- 의존: 0-51.

### 1-50. 런처 · 특수문자 — index · special_chars
- 대상: `src/main/resources/tools/index.html` · `special_chars.html`.
- 변경: index — link 추가, `:root` 둘·리셋·`body` 글꼴·색 줄 지움(`body` 의 `min-height·flex·padding` 은 남김), `.sub`·`h2` 로컬 유지(뜻이 다름 — 이름을 `.launcher-sub`·`.section` 으로 바꿔 충돌 회피, 마크업 같이), `.cards`·`.card`(링크 카드)는 **`.card` 이름을 `.tool-card` 로 바꾼다**(공용 `.card` 와 충돌). 확인됨: `LauncherTest:40` 은 `data-file="` 수만, `SelfTest.java:26` 은 `data-file` 정규식만 본다 — `class="card"` 를 세는 시험·JS 없음(index 의 `<a class="card"` 11개 + CSS `.card` `a.card:hover` `.card.off` `.card .soon` 을 같이 바꾼다). special_chars — link 추가, `:root` 둘 지움, 토큰 이름 바꿈(`--card→--surface --ink→--text --sub→--muted --line→--border`; `--accent-soft --chip --toast` 는 공용 토큰), `.combine-wrap button`·`.seg button` 규칙은 로컬 유지하되 색은 토큰(`.seg button.on{background:var(--accent);color:#fff}`), 스크롤바 밝음 변형은 지움(공용 토큰이 따라간다), `#toast` 로컬 지움(공용). 버튼: 조합 「복사」 → `btn-green`, 「지우기」 → `btn-red`.
- 검증: `LauncherTest`·`SmokeHtmlUnitTest#opensWithoutScriptErrors`·`ToolsFolderTest` 초록 · `node scripts/puppeteer/visual.js work-1-50 --only index,special_chars` 종료 0 · `grep -c "\-\-card\|\-\-ink\|\-\-sub\b\|\-\-line" special_chars.html` 0.
- 의존: 0-51.

### 1-51. 앱형 셋 — code_check · program_analysis · spring_source_generator
- 대상: 세 HTML (+ `code_check_ext.js`·`program_analysis_ext.js`·`spring_source_generator_ext.js` 는 **읽기만** — 클래스 이름 `t`·`on`·`sel`·`msg` 를 JS 가 붙이는지 확인).
- 변경: link 추가 · `:root`·리셋·`body` 글꼴/색(높이·flex·overflow 는 남김)·`header`·`header h1`·`.opt`·`.dirbar`·`select,input`·`input[type=checkbox]`·`button*`·`.btn-*`·`.tabbar .t/.t.on`(컨테이너 `.tabbar{display:flex;gap:3px;padding:8px 16px 0}` 만 남김)·`#msg.err/.ok`(→ 마크업에 `class="msg"` 를 더하고 JS 는 `className='msg err'`? — **아니다**, JS 가 `className='err'` 를 붙이므로 로컬에 `#msg.err{color:var(--red)} #msg.ok{color:var(--green)}` 두 줄만 남긴다) 지움 · `#preview #rules #detail #impJsps` 우물 → `class="well"` 더하고 로컬 규칙은 높이·overflow 만 · `#result tr/#programs tr/#files tr` hover·sel 셋 → 표에 `class="pick"`(JS 가 `tr.className='sel'` 붙이는 것 확인) · 고정 hex → 토큰 · code_check 로컬에 `#rules .rule .msg{min-height:0;margin:0}` override. 버튼:

  | 파일 | 버튼 | 지금 | 바꿈 |
  |---|---|---|---|
  | code_check | `#runStop` 중지 | btn-g | `btn-red` |
  | code_check | `#runText` 붙여넣기 검사 · `#depRun` 조회 | btn-g · btn-p | `btn-p` · 그대로 |
  | code_check | `#copy` | btn-green | 그대로 |
  | code_check | `#xlsx` · `#depXlsx` | btn-g | `dl` + 아이콘 span |
  | code_check | `#saveRules` 프로필에 저장 · `#cmp` 비교 | btn-g | 기본(class 제거) |
  | program_analysis | `#xlsx` | btn-g | `dl` + 아이콘 |
  | program_analysis | `#impRun #conRun` | btn-g | `btn-p` |
  | spring | `#run` 생성(파일 10종) | btn-p | `dl`(파일을 만드는 버튼) |

- 검증: `SmokeHtmlUnitTest#codeCheck*,#programAnalysisRuns,#crudGeneratorRuns`·`ToolsFolderTest` 초록 · `visual.js work-1-51 --only code_check,program_analysis,spring_source_generator` 종료 0.
- 의존: 0-51.

### 1-52. jsp_formatter · table_builder
- 대상: 두 HTML(+ `jsp_formatter_ext.js`·`table_builder_ext.js` 읽기만).
- 변경: 공통 절차 그대로. jsp_formatter — `.modes .t`·`.tabs .t`(작은 것 — 공용 `.t` 로 가되 로컬 `.tabs .t{font-size:10px;padding:2px 10px}` 만 남김)·`.badge`(로컬 유지 — 뜻이 다름, 이름 `.cnt` 로 바꾸고 ext.js 가 붙이는지 확인 뒤 같이)·`#toast` 지움·`#cmp/#dRisk` 색 토큰. table_builder — `.btn-del`→`btn-red`(마크업 8곳), `#toast` 지움, `.box`(패널) 로컬 유지, `#grid` 로컬, `'맑은 고딕'` 글꼴 줄 지움(공용 글꼴), **iframe `srcdoc` 스타일은 손대지 않음**. 버튼: jsp `#dirApply` 덮어쓰기 btn-p→`btn-red` · 「비우기」 btn-g→`btn-red` · 「결과 복사」 그대로 · `#dirPreview` btn-g→`btn-p`; table_builder 「HTML 불러오기」 btn-green→기본 · `#xlsxBtn` 기본→`dl`+아이콘 · 「복사」 그대로 · 모달 「불러오기」 btn-p 그대로 · 「새 표」 그대로.
- 검증: `SmokeHtmlUnitTest#jspFormatter*,#tableBuilder*`·`ToolsFolderTest` 초록 · `visual.js work-1-52 --only jsp_formatter,table_builder` 종료 0 · `bash scripts/gate-probe.sh` 는 여기서 안 돈다(마무리에서) — 대신 `git apply --check scripts/probes/iframe-sandbox.patch` 를 돌려 「패치 갱신 필요」 가 되는지 **기록만**.
- 의존: 0-51.

### 1-53. dev_tools
- 대상: `src/main/resources/tools/dev_tools.html`(`<style>` 7~140 과 버튼 마크업만) · `dev_tools_ext.js:118`.
- 변경: link 추가 · `:root`·리셋·`body` 글꼴/색·`select,input`·`textarea` 바탕·`button*`·`.btn-p .btn-g .btn-green .btn-red`·`.b64-tab/.url-tab`·`.dm-tabs .t`·`#toast`·스크롤바 지움(공용) · `.tab/.tab.active`(상단 밑줄 strip)·`.sql-db-btn.*`·`.radio-btn`·`.ext-*`·`.date-*`·`.jwt-*`·`#page-textdisplay` 로컬 유지, 고정 hex → 토큰 · `.ext-box .card` 는 이름 충돌 — CSS 세 줄(`dev_tools.html:44-46` `.ext-box .card` · `.ext-box .card pre` · `.ext-box .card .w`)을 `.ext-card` 로 바꾸고, 그 클래스를 붙이는 자리 둘(`dev_tools_ext.js:73` `p.className = 'card'` · `:117` `c.className = 'card'` — 백엔드 JS 라 고쳐도 된다, 실측)을 `'ext-card'` 로, `scripts/puppeteer/smoke-devtools.js:54` `#ls_out .card pre` 를 `#ls_out .ext-card pre` 로. 버튼(정적 마크업만; `:2020` 프리셋 `className='btn-g'` 는 순수 JS — 안 건드림, 별칭이라 꼴은 기본):

  | 버튼 | 지금 | 바꿈 |
  |---|---|---|
  | 「지우기」 ×8(:169 :224 :333 :402 :473 :529 :551 :581) · `#td_btnClear` | btn-g | `btn-red` |
  | `#td_batchApply` 적용(덮어쓰기) | btn-p | `btn-red` |
  | 「전체화면 보기」 | btn-p | 기본 |
  | 날짜 「복사」(:307) | btn-g | `btn-green` |
  | `dev_tools_ext.js:118` 「복사 n」 | `'btn-g'` | `'btn-green'` |
  | `#dm-create` 안 `▷ INSERT 생성` | btn-p | 그대로(smoke-devtools 가 `#dm-create button.btn-p` 하나를 고른다 — 다른 btn-p 를 그 안에 만들지 않음) |
  | 나머지 | — | 그대로 |

- 검증: `SmokeHtmlUnitTest#opensWithoutScriptErrors`·`ToolsFolderTest(insertResultBoxesStaySeparate 포함)` 초록 · `node scripts/puppeteer/smoke-devtools.js http://127.0.0.1:<visual 포트>` 는 서버가 필요 — `visual.js work-1-53 --only dev_tools --keep` 로 서버를 남기고(`--keep` 옵션: 끝에 서버를 안 죽이고 포트를 출력) 그 주소로 smoke-devtools 를 돌린 뒤 수동으로 끝낸다(`taskkill` PID 출력) — 통과 · `visual.js work-1-53 --only dev_tools` 종료 0.
- 의존: 0-51.

### 1-54. 강제 시험 · 사후 촬영 · 판독 · 수정
- 대상: `src/test/java/kr/ejg/toolbox/web/ToolsFolderTest.java` · `BackendToolsTest.java` · (판독 결과에 따라) 1-49~1-53 의 파일.
- 변경:
  - `ToolsFolderTest.toolsHaveNoLocalTheme`: `tools/*.html` 중 `sql_snippets.html` 뺀 전부의 `<style>` 안에 `:root` · `prefers-color-scheme` · `^\s*button\s*\{` · `\.btn-(p|g|green|red)\b\s*\{` · `\.dl\b\s*\{` 가 없다. 그리고 `#[0-9a-fA-F]{3,6}\b` 고정 색은 허용 목록(`#fff #000 #16825d #4ec9b0 #9cdcfe`(dl 그라데이션) + diff/CRUD/JWT/DB 색 — 실측해 목록을 시험 안 상수로)에 든 것만.
  - `ToolsFolderTest.toolsLinkCommonCss`: 같은 집합 전부 `<link rel="stylesheet" href="/tools/common.css">` 를 **첫 `<style>` 앞**에 둔다(indexOf 비교). `BackendToolsTest.toolIsServedWithCommonJs` 에 같은 단언 한 줄.
  - `ToolsFolderTest.commonCssDefinesButtonScheme`: `common.css` 에 `.btn-p`·`.btn-green`·`.btn-red`·`.dl`·`[data-theme="light"]`·`prefers-color-scheme: light` 가 있고 `url(http` 없음.
  - 사후 촬영: `node scripts/puppeteer/visual.js after` 종료 0 → `node scripts/puppeteer/visual-diff.js before after` → `out/visual/after/report.html`·`diff.json`.
  - **판독(AI 도구)**: Opus 가 `diff.json` 상위를 `Read` 로 열어 본다(PNG 는 Read 가 그림으로 보여 준다). 동시에 `Agent`(general-purpose) 넷을 병렬로 띄운다 — 각자 도구 셋씩(① index·special_chars·sql_snippets ② db_browser·deliverable_sql·logical_name ③ code_check·program_analysis·spring_source_generator·jsp_formatter ④ dev_tools·table_builder) `out/visual/before/<tool>-*.png` 와 `after/<tool>-*.png` 를 Read 로 보고 **표로** 보고: `| 샷 | 문제 | 어디 | 심각도 |` — 볼 것: 글자 잘림·겹침 · 배경에 묻힌 버튼 · 밝은 테마에 남은 어두운 고정 색(검은 우물·회색 탭) · 대비 안 읽힘 · 버튼 유형 색이 표(D4)와 다름 · 375px 가로 스크롤·겹침 · 사라진 요소. 에이전트 보고는 **데이터**다 — 지적마다 Opus 가 그 PNG 를 직접 보고 맞으면 고친다.
  - 수정 고리: 지적을 1-49~1-53 파일에서 고치고(범위 안 파일만) `visual.js after2 --only <도구>` → 다시 본다. 고리는 둘까지(번들 규칙). 남은 것은 이력 「드러난 것」 + 새 행.
- 검증: `bash scripts/mvn.sh -q test -Dtest='ToolsFolderTest,BackendToolsTest,SmokeHtmlUnitTest' …` 초록 · `visual.js after` 종료 0(판정 0·콘솔 오류 0) · `report.html` 생성 · 판독 지적 처분표가 이력에.
- 의존: 1-49 1-50 1-51 1-52 1-53.

### 1-55. 문서
- 대상: `CLAUDE.md` 「구역」 표 `src/main/resources/tools/` 행 · 「화면 스타일」 절 · `README.md`(도구 화면 절이 있으면).
- 변경: 구역 행 「백엔드본 HTML + common.js **+ common.css**」. 「화면 스타일」 절을 바꿔 쓴다: 「토큰·본문·카드·입력칸·표·버튼·탭은 `tools/common.css` 하나. 도구 `<style>` 은 그 도구에만 있는 것만(ToolsFolderTest 가 막는다). 테마는 `data-theme`(common.js 토글, localStorage `tb-theme`) → 없으면 OS. 버튼 유형: 실행 `.btn-p` · 파일 `.dl`(xlsx 는 `.ico-xlsx`) · 복사 `.btn-green` · 위험(지우기·덮어쓰기·중지) `.btn-red` · 보조 기본. 모드 배지·`.tb-table` 은 common.js 가 붙인다. `sql_snippets` 는 common.js 가 link 를 끼운다(D5). 집 검증 `node scripts/puppeteer/visual.js <label>` + `visual-diff.js`」. 존댓말 없음(doc-lint).
- 검증: `bash scripts/verify.sh`(docs 레인 doc-lint) 초록.
- 의존: 1-54.

### 마무리(CLAUDE.md 「마무리」 그대로)
1. 독립 리뷰 `Agent(subagent_type:"caveman:cavecrew-reviewer")` 에 `git diff work/2026-10-07c...HEAD`(#51 머지 전이면 이 범위; 머지됐으면 `origin/main...HEAD`). 지적은 버그만 고친다(프로젝트 기간 규칙).
2. `bash scripts/gate-probe.sh` — `iframe-sandbox.patch` 「패치 갱신 필요」 면 다시 떠서 커밋.
3. `bash scripts/verify.sh --full`(약 25분; 메모리 확인 뒤).
4. `git push -u origin work/2026-10-08`.
5. **#51 이 머지돼 있으면** `gh pr create --base main` — 본문에 행마다 시작 세 줄 표 + wip 커밋 7개가 `chore: wip` 제목인 이유 한 줄 + 판독 처분표. 아니면 **여기서 멈추고** 사람 몫에 「#51 머지 → `git checkout work/2026-10-08 && git rebase main`(충돌 없을 것 — 겹치는 파일은 logical_name.html·LogicalRoutes·LogicalRoutesTest 셋, 전부 wip 가 #51 위에 쌓인 것) → PR」 을 적는다.
6. CI 폴링(30초, `gh api …/check-runs`). 빨강은 고치기 둘까지. AI 리뷰(`claude[bot]`) 처분 — 버그만 고치고 push, 나머지는 PR 본문 「AI 리뷰 처분」.
7. 머지 위임 없음 — PR 열고 멈춘다.
8. 끝 보고: 계획 밖 결정 · wip/ 로 뺀 행 · 새 행 · 사람이 할 것(#51 머지·전체 검수 — `run.bat --profile demo` 뒤 열둘을 OS 어둡게/밝게로 한 번씩, `#tb-theme` 토글 · `wip/logical-ui-layout` 가지 삭제 `git branch -D` · portfolio 몫: sql_snippets 순수본을 같은 버튼 체계로 + 0-40 패치가 바뀐 `<style>` 에 안 맞을 수 있음).

## 5. 금지 사항

- `pure/` · `sql_snippets.html` 본문 · `.github/` · `scripts/hooks/` · `scripts/probes/*.patch`(마무리 2 재생성만) · `m2/` · 프로필 실물.
- common.js 의 배지 CSS 글(`BADGE_ID + '{'` … `}`)과 `.tb-table` 규칙 — 글자 하나도(B2). `var(` 에 대체값 없이 쓰기(StaticFilesTest).
- 도구의 인라인 `style=""` 와 JS `.style.display/.style.width` 토글(B5) · 순수본에서 온 JS 함수(D16 — `dev_tools.html:2020` 포함) · `.grid` 규칙 옮기기(B3) · `.opt`·`.item`·`.mark`·`pre.sql`·`#guide .msg`·`.cand`·`data-kind` 위치(시험) · 스모크가 누르는 id.
- 새 npm 패키지·새 Maven 의존성 · CDN·외부 URL(`url(http` 포함) · `networkidle0`.
- 시험을 고쳐서 통과시키기 — 바꿔야 하는 시험은 이 플랜이 이름 붙인 것만(`dbBrowserActionButtonsArePrimary` 는 **안 바꾼다** — 새 클래스가 그대로 통과한다).
- 버튼 글 바꾸기(「복사」 등) · 아이콘을 글자로 넣기(span 만) · `btn-primary` 처럼 `btn-p` 를 포함하는 새 클래스 이름.
- 데모 Oracle(`toolbox-demo-ora`)을 하네스 픽스처로 쓰기 · `docker rm`.
- 광범위 리팩터: 탭 동작 JS 통일 · dev_tools 인라인 스타일 정리 · 다른 도구에 `.step/.opt-card` 적용.

## 6. 최종 검증

- `bash scripts/verify.sh --full` 초록(java·tools·docs 셋 다 full 도장).
- `node scripts/puppeteer/visual.js after` 종료 0 — 샷 ≥ 90 · `styles.json` 판정 0(배경 묻힘·대비 부족·체계 어긋남) · `console.json` 0. `visual-diff.js before after` 의 `report.html` 이 있고, 판독 지적 처분표가 `PROGRESS.md` 이력에(지적 → 고침/안 고침·이유).
- `node scripts/puppeteer/smoke-dbbrowser.js`·`smoke-tabs.js`·`smoke-devtools.js` 통과.
- `bash scripts/gate-probe.sh` 여덟 산다.
- 회귀 확인 대상: `SmokeHtmlUnitTest` 전부(특히 `dbBrowserActionButtonsArePrimary`·`deliverableGuideSwitchesDialect`·`crudGeneratorRuns`·`codeCheckFolderRun`·`dbBrowserDtoTabsKeepOwnResult`) · `ToolsFolderTest` 전부(`sqlSnippetsIsPurePlusCommonJs`·`deliverableCardsAreOneColumn`·`modeBadgeSitsBottomRight`) · `StaticFilesTest` · `LauncherTest` · `BackendToolsTest`.
- 수동(사람 몫): 데모 서버에서 열둘을 어둡게·밝게 한 번씩, `#tb-theme` 토글이 새로고침 뒤에도 남는지, 「저장」 계열 버튼이 다 보이는지.

## 7. 중단 조건

다음이면 임의로 풀지 말고 멈추고 보고(어느 스텝 · 무엇이 예상과 달랐나 · 선택지):
- 플랜의 구조 설명이 실제 코드와 다름(예: `SnapshotStore.save` 서명이 3-13 시험 스니펫과 다름 — 열어 맞추는 정도는 진행, 저장 모델 자체가 다르면 멈춤).
- 검증이 2회 연속 빨강이고 원인이 플랜 범위 밖(예: Rhino 가 `dataset`·`localStorage` 를 못 돌려 `opensWithoutScriptErrors` 가 전부 빨강 — 0-51 에서 `setAttribute`·try/catch 로 한 번 고쳐 보고, 그래도면 멈춤).
- 금지 사항을 어기지 않고는 진행 불가(예: 어떤 시험이 `.btn-g` 규칙 존재를 재서 별칭으로 못 둠).
- 스텝에 없는 파일을 3개 이상 고쳐야 함.
- 판독 수정 고리 둘을 넘어도 `styles.json` 판정이 남음 → 남은 것을 이력·새 행으로 적고 1-55 로 간다(멈춤 아님) — 단 **콘솔 오류**가 남으면 멈춤.
- `visual.js before` 가 지금 코드에서 콘솔 오류를 내면 — 기록하고 진행(1-48 명시), 단 서버가 안 뜨거나 스냅샷이 안 만들어지면 멈춤.
- #51 이 마무리 때 아직 안 머지 → push 까지 하고 PR 없이 멈춤(마무리 5).

## 8. 불확실 항목

설계 세션이 2026-10-08 실측으로 다 풀었다. 실행자가 다시 확인할 것은 없다 — 아래는 근거다.
1. **Jetty `.css` mime** — `m2/org/eclipse/jetty/jetty-http/11.0.25/…/mime.properties` 에 `css=text/css`. 손댈 것 없다. `StaticFilesTest.commonCssIsServed` 가 영구로 잰다.
2. **HtmlUnit 4.11.1 의 파일·인코딩 API** — `TextDecoder`(ArrayBuffer·ArrayBufferView 를 받는다; `{fatal}` 옵션은 버린다) · `FileReader.readAsArrayBuffer/readAsText/getResult` · `HtmlFileInput.setFiles(File...)` 전부 있다(javap). `readAsText` 대체 길은 넣지 않는다.
3. **HtmlUnit 의 `classList.toggle`** — 인자 하나만(`toggle(String)`) — 3-13 이 `logical_name.html` 의 2인자 호출을 `onOff()` 로 바꾼다. **`indeterminate`** 는 HtmlUnit 에 없다 → Puppeteer `checks` ① 이 잰다. `Storage.getItem/setItem/removeItem`·`HTMLElement.dataset`·`hidden` 은 있다 — 그래도 테마는 `setAttribute/removeAttribute('data-theme')` 로 건다(dataset 쓰기는 안 쓴다).
4. **헤드리스 Chrome 의 `prefers-color-scheme` 흉내** — `page.emulateMediaFeatures([{name:'prefers-color-scheme',value}])` 뒤 `matchMedia('(prefers-color-scheme: light)').matches` 가 dark→false · light→true(번들 Chrome, `headless:'new'` 실측). `localStorage` 는 http 페이지에서 된다(data: URL 에선 막힘 — 하네스는 http 만 연다).
5. **sql_snippets 꼴** — common.css 를 첫 `<style>` 앞에 끼우므로(D1) 로컬 `button{border:none…}`·`textarea`·`input[type=text]`·`#toast` 가 이긴다. 공용에서 새어 드는 것은 로컬이 안 쓰는 속성뿐 — `button` 바탕(`var(--btn)` 회색; 로컬 버튼은 `.btn-green`·`.snip-btn`·`.fav-btn`·`.clear-btn` 이 바탕을 다시 정해 실제로 보이는 차이 없음)·`label{display:block}`(flex 자식이라 꼴 안 바뀜). 밝은 테마에서 로컬 고정 `#111`·`#9cdcfe`·`#3c3c3c` 가 남는다 — 사용자 어둡게 고정, 받아들인다(이력에 적고 portfolio 몫).
6. **`scripts/probes/iframe-sandbox.patch`** — 지금 `git apply --check` 통과. 문맥 세 줄은 `<body>` 마크업(`col-preview`·`label`·주석)이라 `<style>` 을 줄여도 오프셋만 바뀌고 맞는다. 마무리 2 에서 「패치 갱신 필요」 가 나오면 그때만 다시 뜬다.
7. **3-13 시험 서명** — `Schema(String name, String dbVersion, List<Table> tables)`(`Schema.java:15`) · `SnapshotStore.save(String profile, String connId, String note, List<Schema>)`(`:59`) · `Db.open(Path)` + `new SnapshotStore(db)`(`SnapshotServiceTest.java:101-103`). 스니펫에 반영했다.
8. **런처 `.card`** — 시험·JS 가 안 본다(`LauncherTest:40` `data-file=` 수, `SelfTest.java:26` `data-file` 정규식). `.tool-card` 로 바꾼다.
9. **dev_tools `.ext-box .card`** — 붙이는 곳은 백엔드 JS `dev_tools_ext.js:73`·`:117` 둘, 고르는 곳은 `smoke-devtools.js:54` 하나. `.ext-card` 로 바꾼다(1-53 에 반영).
10. **테마 첫 그림 깜박임**(`defer`) — OS 와 저장 테마가 다를 때만. 사용자는 OS·저장 둘 다 어둡게라 안 보인다. 받아들인 결정(D3).
11. **PR #51** — 2026-10-08 확인 OPEN(머지 안 됨). 마무리 5 대로: 끝에 #51 이 여전히 열려 있으면 push 까지 하고 PR 없이 멈춘다(pr-guard).
12. **메모리** — 실측 여유 11.6 GB(31.5 중). `--full`(테스트 JVM `-Xmx3g` + Testcontainers) 앞에 데모 서버(`java -jar … --port 41790`, 백그라운드)를 끄고 시작한다 — [[full-verify-memory-contention]].
