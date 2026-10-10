원본은 설계 세션 플랜 파일 — S0 에서 이 파일로 옮겼다(2026-10-10). 실행 세션은 PLAN 분할표 행과 이 파일의 4 절만 읽는다.

# 설계 22 — 번들 37: 공통 프로필 고르기 · 코드 검사 화면 개편 · 저장 표시 마무리 · JSP 포함 꼴 · view 꼴 거르기 (Fable, 2026-10-10)

이 플랜은 실행 담당 모델(Opus)이 그대로 따라 실행한다. 실행자는 탐색 과정을 모른다. 판단이 필요한 지점을 남기지 않았다. 번들 모드 규칙(CLAUDE.md 「번들 모드」) 그대로 — 갈려도 안 멈추고, 행당 「정한 것(계획 밖)」 둘까지.

사용자가 2026-10-10 정한 것(뒤집지 않는다):
- **프로필 고르기는 화면 머리줄 오른쪽**(앱형은 `<header>` 끝, 카드형은 제목 줄 오른쪽). 서버 전역 프로필 하나 유지.
- **코드 검사는 탭 셋**(폴더 검사 · 붙여넣기 검사 · 배포 목록), 결과 표는 탭마다. **규칙 칸은 오른쪽 세로 칸**(두 검사 탭 공통, 배포 탭에선 숨김). 「앞 실행과 비교」 는 버튼·API·`CheckStore.compare` 째 삭제. 「프로필에 저장」 → 대상 파일을 박은 「규칙 켬·끔 → <프로필>.yaml」. 배포 목록은 남기고 쓰임을 적는다.
- 저장 표시는 1-58 규칙 R1~R13(PLAN 1-58a 행).

## Context

오늘 데모에서 코드 검사 화면을 보고 지적한 다섯(붙여넣기가 폴더 결과를 덮음 · 이력 고르기 없음 · 「프로필에 저장」 뜻 모름 · 비교가 쓸모없음 · 배포 목록 쓰임 모름)과 「프로필 고르는 데가 없다」 에서 1-59·5-24 가 섰고, 번들 35 가 저장 표시 규칙을 전 화면에 깔아 1-58h 만 남았다. 번들 34 실측에서 `c:import` 가 「링크」 로 잘못 분류되는 것(6-30)과 URL 조각·응답 글이 view 로 저장되는 것(6-31)이 드러났다. 이 번들이 그 다섯을 닫는다. DB 레인은 안 건드리고(push 앞 `--corpus` 한 번), 새 의존성 0.

### 1. 목표와 범위

- **목표**: 프로필을 쓰는 화면마다 머리줄에서 프로필을 고를 수 있고, 코드 검사가 탭 셋·이력 고르기·새 저장 버튼으로 바뀌며, 저장 표시가 옛 함수 없이 한 꼴이 되고, 프로그램 분석이 `c:import`·`jsp:include`·iframe 을 「포함」 으로, 뷰 이름 꼴이 아닌 글을 미해결로 낸다.
- **In**: `S0` → `1-59a` → `1-59b` → `5-24a` → `5-24b` → `5-24c` → `5-24d` → `1-58h` → `6-30` → `6-31` → 마무리. 가지 `work/2026-10-10f`(main `8d50896` 위). 머지 위임 없음 — PR 열고 멈춘다.
- **Out**: 탭마다 다른 프로필(모든 API 에 프로필을 넘겨야 함) · 검사 이력의 발췌 저장(규칙 3) · 이력 삭제 UI · 배포 목록 양식 · `pure/` · 2-17·6-9 등 조건부 행 · 검증 인프라.

### 2. 현재 구조 요약(2026-10-10 실측)

#### 2.1 프로필(1-59)
- 활성 프로필은 서버 `AtomicReference<String> activeName`(`web/App.java:124`) 하나. `GET /api/profiles` → `{names, active}` · `POST /api/profiles/active {name}` → 404/`{active}`, 이름이 바뀌면 `conns.clearAll()`(`ProfileRoutes.java:91-102`) · `GET /api/ping` → `{version, profile, mode}` · `GET /api/alive` SSE — 이벤트 `alive`, data = 활성 프로필 이름(`AliveRoutes.java:33-41`). `GET /api/profiles/active` 는 없다.
- `tools/common.js` — `badge()`(:175) 가 `/api/ping` 뒤 `setBadge(onText(profile),'on')` + `watch()`(SSE → 배지 글만 갱신, :153). `injectStyle()`(:49) 이 `#tb-mode-badge`(오른쪽 아래 6px, `pointer-events:none`)·`#tb-theme`(30px)·`.tb-table` 스타일 주입. `window.TB = { api, badge, table, sse, snapLabel, joinPath, savedText, result, copy }`(:377). 화면에 전환 이벤트는 안 간다.
- 고르는 칸은 `db_browser.html:36` `<select id="profile">` 하나 — `loadProfiles()`(:137-147, 빈 목록 `(profiles 폴더에 YAML 없음)`, 활성 없으면 `(프로필 고르기)`, `state.active`) · change(:422-431) → POST → `state.conn=null` → `msg('connMsg','프로필을 바꿨다')` → `TB.badge()` → `loadConns()`(첫 접속 자동 → 「프로필을 바꿨다 — 첫 접속 X 선택」). DOMContentLoaded: `loadProfiles().then(loadConns).then(loadSnapshots).then(dtoInit)`(:446).
- 프로필에 따라 init 때 읽는 화면(전환 뒤 다시 읽어야 할 것): code_check(`/api/check/rules`·`/api/fs/defaults`, `init` ext.js:386) · deliverable_sql(`/api/conn`·`/api/profiles/{active}` 작성자·기관, `init` :1077) · logical_name(`/api/profiles/{active}` skipTokens, `init` :585) · spring_source_generator(`/api/profiles/{active}` 생성기 기본값, ext.js `init` :211) · program_analysis(`/api/fs/defaults`, ext.js `init` :569) · jsp_formatter(`/api/fs/defaults`, ext.js `loadRecentDirs` :320 직접 호출) · dev_tools(`/api/conn`, ext.js :148) · db_browser(`/api/conn`). 안 쓰는 화면: table_builder · sql_snippets · special_chars · index.
- 시험: `SmokeHtmlUnitTest` `opensWithoutScriptErrors`(:86-94, 화면마다 `#tb-mode-badge` 「백엔드 연결」) · `dbBrowserFillsProfileAndConnections`(:349, `#profile` 선택 `example`) · `dbBrowserPicksFirstConnection`(:366, `#profile` 을 b 로 → `.item.on` bee · `connMsg` 「프로필을 바꿨다 — 첫 접속 bee 선택」) · `dbBrowserWithoutActiveProfile`(:393, `(프로필 고르기)`). `scripts/puppeteer/smoke-dbbrowser.js:94-95`(`#profile` = zero → 「프로필을 바꿨다」 대기). `ToolsFolderTest.themedPages()` 가 화면 목록. 화면 머리는 두 꼴 — 앱형 `<header><h1>…</h1><span class="opt">…</span></header>`(code_check·program_analysis·jsp_formatter·table_builder·spring_source_generator·dev_tools) · 카드형 `<h1>…</h1><div class="sub">…</div>`(db_browser·deliverable_sql·logical_name). `common.css` 의 `header{display:flex;…;gap:14px;flex-wrap:wrap}` · `.sub{margin-bottom:14px}`.

#### 2.2 코드 검사(5-24·1-58h)
- `tools/code_check.html`(105줄) — header → `.dirbar`(`#dir`·`#dirRecent`·`#changed`·`#vcsInfo`·`#runDir`·`#runStop`·`#msg`) → `.tabbar`(`#tabCheck`·`#tabDeploy`) → `#paneCheck`(`.top` = 붙여넣기 col(`#lang`·`#runText`·`#paste`) + 규칙 col(`#saveRules`「프로필에 저장」·`#ruleMsg`·`#rules`) / `.bottom` = `.bar`(`#fSev`·`#fGroup`·`#fText`·`#copy`·`#xlsx`·`#cmp`·`#count`) + `.split`(`#result.pick`·`#preview`)) → `#paneDeploy`(`#depFrom`·`#depTo`·`#depRecent`·`#depRun`·`#depXlsx`·`#depCount`·`#depMsg`·`#depNote`·`#depResult`). 로컬 `<style>` :8-39(금지 규칙 없음). 폴더·붙여넣기가 **같이 쓰는 것**: `#msg`·`#runStop`·`#result`·`#preview`·`#count`·`#copy`·`#xlsx`·`#cmp`·필터 셋, 상태 `findings`·`shown`·`runId`·`root`·`pastedText`.
- `tools/code_check_ext.js`(412줄) — `msg(id,text,cls)`:23 · `loadRules`:36(`GET /api/check/rules` → `#fGroup`·`renderRules`) · `renderRules`:52(`g_<group>`·`r_<rule>`·`x_<rule>` 동적 id) · `choice`:117 → `{groups, rules}` · `saveRules`:139(`GET /api/profiles` → `PUT /api/profiles/{active}/codecheck` → `msg('ruleMsg', TB.savedText([r.path]))`) · `start(body,label)`:156(`POST /api/check/run` 202 → `poll`) · `done`:170 · `stop`:179(`DELETE /api/jobs/{id}`) · `poll`:186(DONE 때 `runId`·`findings`, `#msg` 요약 「파일 n · 결과 m · … · 이력 #id」, `#copy`·`#xlsx` 켬, `#cmp` 는 `root!==null` 일 때) · `runDir`:208 · `showTab`:220 · `depOptions`:227 · `deploy(xlsx)`:243(`POST /api/check/deploy-list {path:#dir, from, to, xlsx}` → `#depResult`·`#depCount`·`#depMsg`) · `vcsInfo`:260 · `runText`:288 · `render`:298(`#result`·`#count`, 행 클릭 → `preview`) · `preview`:339(`pastedText` 면 메모리, 아니면 `GET /api/fs/read?path=root+file`) · `copy`:349 · `xlsx`:355(`POST /api/check/runs/{id}/export` → `msg('msg','xlsx n행 · '+TB.savedText)`) · `compare`:362 · `loadRecentDirs`:369 · `init`:386. `TB.savedText` 자리 둘(:144·:358) — 저장소 전체에 남은 마지막 둘.
- `web/CheckRoutes.java` — `GET /api/check/rules`:44 · `POST /api/check/run`:74(`{path?|text?, lang?, changedOnly?, groups?, ruleOverrides?}` → 202 `{jobId}`, 결과 `{runId, files, skipped, parseErrors, truncated, findings[]}`) · `GET /api/check/vcs?path=`:136 · `GET /api/check/runs`:175(`RunInfo[] {id, profile, startedAt, path, changedOnly, groups, findings}` 최신순) · `GET /api/check/runs/{id}`:177(`{run, findings[{file,line,group,rule,severity}]}` — 발췌 없음) · `POST /api/check/runs/{id}/export`:191(`{path, rows}`) · `POST /api/check/deploy-list`:220 · `GET /api/check/runs/{id}/compare?prev=`:276-298(`store.previous`·`store.compare` — 쓰는 곳은 이 라우트뿐, CLI 안 씀). `core/check/CheckStore.java` — `RunInfo`:22 · `Compare`:26 · `save`:39 · `runs`:82 · `run`:95 · `previous`:112 · `findings`:126 · `compare`:142 · `key`:154. 표 `check_run(id, profile, started_at, path, changed_only, rule_groups)`·`check_finding(run_id, file, line, grp, rule, severity)`(V001) — 본문 없음(규칙 3).
- 시험: `CheckRoutesTest.folderRunHistoryCompare`:85(두 번 실행 → `/runs`·`/runs/{id}` 발췌 null · `/compare` prevId·removed `java.unusedImport` · `?prev=` 같음 · `/export`) · `CheckStoreTest.saveListCompare`:38(`runs` 순서 · `run(b)` · `findings` 발췌 null · `previous` 셋 · `compare` added/removed/same) · `SmokeHtmlUnitTest.codeCheckStops`:1197(`#runStop` → `#msg` 「중지함 — 이력에 남기지 않았다」) · `codeCheckFolderRun`:1340(`#runDir` → `#result` 행 ≥2 `common.sysout` · 5-23 줄 칸 「파일」 · 행 클릭 → `#preview` `a/A.java:` · 복사 「복사됨: n행」 · `#xlsx` → `#msg` `xlsx` 시작 + 「저장 …코드검사-n.xlsx」 · `r_common.todo` 끄고 다시 …) · `ToolsFolderTest.copyGoesThroughTbCopy`:180(ext.js `TB.copy` ≥1) · `saveNoticesGoThroughSavedText`:218(`code_check_ext.js` savedText 2 — 1-58h 가 지운다) · `RESULT_SITES`(:246-255, 1-58b~f 화면) · `fileButtonsNameWhatNotHow`(:281, RESULT_SITES 의 html `.dl` 글 금지어). `SmokeHtmlUnitTest.savedTextShowsFileOrFolder`:519(`TB.savedText`·`TB.joinPath`). `ProfileRoutes` `PUT …/codecheck` 응답 `{codecheck, path(절대), backup(절대, 비밀번호 비운 판)}`(1-58g).
- 순수본 `pure/tools/code_check.html` 은 id 가 다르고(`src`·`run`·`rules`…) 폴더·배포·xlsx 가 없다 — `ToolsFolderTest` 는 둘의 구조를 안 맞댄다. 백엔드본 구조는 자유.

#### 2.3 프로그램 분석(6-30·6-31)
- `core/analyze/JspLinks.java` — `record Link(url, kind)`:48, kind 는 String `link·form·popup·ajax·script·other`. `CUE` 정규식:52-53(창 200자 안 **마지막** 단서) · `kind(text,start)`:56-79 — 단서 없음·`c:url var` → other / `window.open(`·`.open(` → popup / `action=` → form / `location.href|replace|=` → script / `href=`·**`c:import`** → link / 그 밖(`url:`·`$.get/post/ajax(`·`.load(`·`ajax`) → ajax. `jsp:include`·`<iframe` 은 단서에 없어 other(또는 앞 단서 꼴). `<%@ include file=` 은 JSP 파일 정적 포함이라 `.do` 토큰이 아님 — 대상 아님.
- 꼴 글 네 곳: `Screens.KIND_WORDS`:72-73(link 링크·form 폼·popup 팝업·ajax ajax·script 스크립트·other 기타, `KIND_UNKNOWN="모름"`:24) · `program_analysis_ext.js` `KIND_WORD`:318(+`'모름'`) · `AnalyzeRoutes.java:245-251`(xlsx 「화면전수」 시트 「부르는 화면 · 부르는 꼴(단서)」 — `Screens.callersText`) · `V010__analyze_screens.sql:9` 주석(코드 목록). 저장 `analyze_jsp_link.kind VARCHAR(10)`(V010:10, `AnalyzeStore`:133-138 kind 마다 한 행).
- 시험: `JspLinksTest.kinds()`:47-65(`/g.do` = `c:import` → **`link`** 단언) · `openerIsNotPopup`·`cueWordsNeedAttributeShape` · `ScreensTest`:56-57(`link,popup`·`모름`) · `AnalyzeStoreTest`:76-91 · `SmokeHtmlUnitTest:1056` `BoardList.jsp (링크)` — 픽스처 `fixtures/analyze/jsp/BoardList.jsp:9` 가 `<c:import url="/cmm/fms/selectFileInfs.do">` 라 6-30 뒤 「(포함)」. 골든 `golden/analyze/jsp-links.json` 은 `urls`·`unresolved` 만(꼴 없음) · corpus 골든에 꼴 글 0.
- `core/analyze/JavaGraph.java` — `record View(kind, name)`:62(view·forward·redirect·class) · `kind(c,m,views)`:814-819(json = `@RestController`·`@ResponseBody`·리턴이 String·MAV 아님·뷰가 `jsonView` 뿐) · `views(c,m)`:825-861 — `new ModelAndView(arg)`·`setViewName(arg)`·리턴 String 의 `ReturnStmt` 를 `value()` 로 풀어 `names`, `dynamic` 이면 `note("viewDynamic", …)`:849, `class:`·`redirect:`·`forward:`(`url()` 이 `?` 뒤 뗌) 아니면 `view`:861. **`kind()` 로 안 거른다** — `@ResponseBody String` 이 `"{\"error\":…}"` 를 돌려주면 view 로 저장. `value()`:873-910 — 리터럴 그대로 · 지역 변수 대입 추적 · `+` 는 **앞머리 리터럴만**(`"&qestnrId=" + id` → `&qestnrId=`) · 삼항·호출은 dynamic. `AnalyzeRunner.JSP_NAME = [\p{L}\p{N}_./$-]+`:30 는 JSP 파일 수(:240)에만 — `Program.views`·`analyze_view` 엔 그대로.
- `core/analyze/Unresolved.java` — `KINDS`(`LinkedHashMap`, 코드 → `Kind(name, meaning, fix)`), 생성자가 모르는 코드를 거절(6-22). `GET /api/analyze/unresolved-kinds`(`AnalyzeRoutes:139`) 가 KINDS 순서로 주고 화면 `kinds`·`kindName()`(ext.js:13·:430) 이 쓴다 — **새 종류는 서버 한 곳에만 더하면 화면이 따라온다**.
- 골든: `golden/analyze/java-graph.json`(픽스처 views) · `golden/corpus/analyze-egov.json:22-26`(view 종류 건수 `class 1·forward 379·redirect 384·view 1119`, unresolved 종류 건수) · `analyze-egov-bbs.json`·`analyze-egov-spot.json`(프로그램별 views) · `analyze-egov-consistency.json`(orphanJsps 수) · `analyze-egov-orphan-jsp.txt`. 갱신은 `-Dgolden.update=true`(`GoldenFiles`·`CorpusFiles`), diff 를 이력에(CLAUDE.md 「검증」 B 급).

#### 2.4 검증 레인(번들 36)
- `src/main/resources/tools`·`core/analyze`·`core/check` 는 corpus 레인 — push 앞 `verify.sh --corpus`(약 7분, 표본 폴더·node). db 레인은 안 건드린다. 청크마다 빠른 검증.

### 3. 설계 결정

- **[고정] 프로필 고르기는 `common.js` 가 붙인다.** 화면이 `<body data-profile="접속·범위">`(글은 그 화면이 프로필에서 쓰는 것 — 작은 회색 글로 보인다)를 달면 `badge()` 가 ping 뒤 `profilePicker()` 를 부른다. `<header>` 가 있으면 header 끝에 `margin-left:auto`, 없으면 `h1` 바로 뒤(제목 줄 오른쪽 `float`) — 둘 다 `<span id="tb-profile" class="opt">프로필 <select id="tb-profile-sel"></select> <small>…</small></span>`. 채우기는 `GET /api/profiles`(글은 `db_browser.loadProfiles` 그대로 — 빈 목록 `(profiles 폴더에 YAML 없음)`, 활성 없으면 `(프로필 고르기)`). **바꾸면 `POST /api/profiles/active` 뒤 `location.reload()`** — 버린 대안: 화면마다 다시 읽기 훅(7 화면 × 코드 + 시험, 안 읽은 것이 남는 버그 자리). 다시 읽을 것이 init 전부라 새로 여는 것이 정확하고 0줄이다. 입력한 글이 사라지지만 프로필은 작업 앞에 고른다. SSE `alive` 로 다른 탭이 바꾼 것이 오면 select 값만 맞춘다(reload 안 함 — 지금 배지와 같다). `TB.profiles()` 가 `/api/profiles` 응답을 한 번 받아 두고 돌려준다(화면이 `active` 를 쓸 때 — db_browser·code_check 저장 버튼).
- **[고정] 어느 화면이 다나 — 선언 + 강제.** `data-profile` 는 2.1 의 여덟(code_check·deliverable_sql·logical_name·spring_source_generator·db_browser·program_analysis·jsp_formatter·dev_tools). `ToolsFolderTest.profilePickerDeclared` — html/ext.js 글에 `/api/profiles`·`/api/conn`·`/api/fs/defaults`·`/api/check/rules` 중 하나라도 있으면 그 html 에 `data-profile` 가 있어야, 없으면 없어야(table_builder·sql_snippets·special_chars·index).
- **[고정] db_browser 의 `#profile` 은 없앤다**(1-59b) — 공통 고르기로. `state.active` 는 `TB.profiles()`. 「프로필을 바꿨다 — 첫 접속 X 선택」 글은 사라진다(reload 뒤 1-57 의 첫 접속 자동 선택이 그대로 돈다).
- **[고정] 코드 검사 배치**: `header` → `.tabbar`(`#tabDir` 폴더 검사 · `#tabPaste` 붙여넣기 검사 · `#tabDeploy` 배포 목록) → `.body`(flex row) = 왼쪽 `.panes`(세 pane) + 오른쪽 `#rulesCol`(약 34%, 배포 탭이면 `hidden`). 탭마다 **자기 결과 묶음**: 결과 칸(`TB.result`) · 필터 줄(등급·묶음·찾기·복사·엑셀·건수) · 표·미리보기. id 는 접두 `dir`·`paste`(`#dirRes`·`#dirResult`·`#dirPreview`·`#dirCount`·`#dirCopy`·`#dirXlsx`·`#dirSev`·`#dirGroup`·`#dirText` / `paste…`). 중지는 pane 마다 `#dirStop`·`#pasteStop`(실행 중엔 두 검사 버튼 다 잠금 — 작업은 한 번에 하나). `#msg`·`#count`·`#result`·`#preview`·`#cmp` 는 없어진다. JS 는 `pane` 객체 둘(`{findings, shown, runId, root, pastedText, ids}`)로 같은 함수를 돈다.
- **[고정] 이력 고르기** `#runs`(폴더 탭 dirbar 끝, `select`) — `GET /api/check/runs` 전부(프로필 안 가름 — 이력이 적고 라벨에 경로가 있다), 라벨 `#id MM-DD HH:mm · <경로 끝 마디> · n건` + 변경분이면 ` · 변경분`, 첫 option `(이번 검사)`(값 ''). 검사가 끝나면 목록을 다시 받고 새 id 를 고른다. 옛 것을 고르면 `GET /api/check/runs/{id}` → 표(원문 칸 빈칸 — 발췌는 안 남긴다) · `#dir` 을 그 경로로 · 결과 칸 ok 「이력 #id · 시각 · n건 — 미리보기는 지금 파일(검사 뒤 바뀌었으면 줄이 어긋난다)」 + 경로 · 엑셀 켬(`/export` 는 이력에서 만든다).
- **[고정] 비교 삭제** — 버튼·`compare()`(5-24a) + 라우트·`CheckStore.compare`·`previous`·`Compare`(5-24b) + 시험 둘의 그 단언.
- **[고정] 규칙 저장 버튼** `#saveRules` = `.btn-red` `[YAML] 규칙 켬·끔 → <span id="saveTarget">demo</span>.yaml`(글은 `TB.profiles().active`, SSE 로 바뀌면 따라옴 — `common.js` 가 `document` 에 `tb:profile` 이벤트를 쏜다). 바로 아래 `#ruleRes`: 진행 「저장 중…」 → ok 「규칙 켬·끔 저장 — 묶음 n · 규칙 m」 + 2줄 `path` + 3줄 `backup`(R11) · 활성 없음 → fail 「활성 프로필이 없다 — 머리줄에서 고른다」. 규칙 칸 머리 한 줄 `.hint`: 「내장 `check/rules.yaml` → 프로필 `customRules` → 프로필 `groups`·`rules` 가 덮어쓴다. 저장은 마지막 둘만 쓴다」.
- **[고정] 배포 안내** `#depNote`: 「운영 반영(이관) 요청서에 붙일 변경 파일 목록을 svn·git 작업 사본에서 두 지점(태그·커밋·리비전) 사이 차이로 뽑아 xlsx 로 낸다 — 파일 경로·추가/수정/삭제·크기. 요청서 양식이 없거나 형상관리 도구가 목록을 만들어 주면 쓸 일이 없다」. 배포 결과·오류는 `#depRes`(`TB.result`) — `#depMsg` 없앰. 버튼 `[엑셀] 배포 목록`.
- **[고정] 1-58h** — `TB.savedText` 삭제 · `saveNoticesGoThroughSavedText` 삭제(RESULT_SITES 에 code_check 둘 추가) · `savedTextShowsFileOrFolder` 를 `joinPath` 만 남겨 이름 바꿈 · 전 화면 `.dl` 금지어는 이미 `fileButtonsNameWhatNotHow` 가 RESULT_SITES 전부를 본다 · `.ico-xlsx` 는 xlsx 버튼에만(ToolsFolderTest 새 단언 — `.ico-xlsx` 가 든 버튼의 글이 xlsx 를 만드는 것인지는 못 재니, **CSV·SQL·JAVA·YAML 배지와 `.ico-xlsx` 가 한 버튼에 같이 있지 않다**로).
- **[고정] 6-30** — `core/analyze/LinkKind` enum `LINK("link","링크") FORM("form","폼") POPUP("popup","팝업") AJAX("ajax","ajax") SCRIPT("script","스크립트") INCLUDE("include","포함") OTHER("other","기타")` + `UNKNOWN_WORD="모름"`. `JspLinks.kind()` 는 enum 을 돌려주고 `Link.kind` 는 그 `code()`(String — H2·골든 호환). `Screens.KIND_WORDS` 는 enum 에서 만든다. 단서: `<c:import`·`<jsp:include`·`<iframe` → INCLUDE(`href=` 보다 먼저 본다 — 지금 `c:import` 가 link 에 섞여 있다). 화면 `KIND_WORD` 에 `include:'포함'` + **`ToolsFolderTest.linkKindWordsMatchEnum`** — ext.js 의 `KIND_WORD = {…}` 를 읽어 enum 코드·글과 같은지(+`모름`). V010 주석은 안 고친다(enum javadoc 에 「V010 주석의 목록은 그때 것」).
- **[고정] 6-31** — `JavaGraph.views()` 끝에서 kind `view` 이름이 `VIEW_NAME = ^[\p{L}\p{N}_./$-]+$`(= `AnalyzeRunner.JSP_NAME` 전체 일치) 가 아니면 **빼고** `note("viewShape", c.file, line(m), c.name+"."+m)`(글 자체는 안 남긴다 — 규칙 3). 그리고 `kind()` 가 `json` 이면 views 를 **비운다**(`kind` 를 먼저 구하도록 호출 순서를 바꾼다 — `onlyJson` 은 views 가 비었나를 보니 views 를 구한 뒤 kind 를 구하고, json 이면 `views=List.of()`). `Unresolved.KINDS` 에 `viewShape`(「뷰 이름 꼴 아님」 · 「돌려주는 글이 뷰 이름 꼴이 아니다(URL 조각·응답 글) — 화면 프로그램이 아닐 수 있다」 · 「@ResponseBody 를 붙이거나 뷰 이름을 문자열 그대로 둔다」) — `viewDynamic` 뒤. `AnalyzeRunner.JSP_NAME` 거름(:240)은 그대로(중복이지만 무해). 골든 넷·corpus 요약 갱신, 건수 diff 를 이력에.
- 시간: 청크 빠른 검증 2~3분 · 끝에 `--corpus` 7분. 새 의존성 0.

### 4. 실행 스텝

청크 = 커밋 하나. `verify.sh` 와 `git commit` 은 따로. 커밋 제목 `feat|fix|chore|docs: <번호> <무엇>`, 끝에 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. HtmlUnit 은 ES5 만(화살표·let·옵셔널 체이닝 금지).

#### S0. 가지 · 문서 행
- 대상: `git`, `PLAN.md`·`PROGRESS.md`·`design/22-bundle37.md`.
- 변경: `git checkout main && git pull && git checkout -b work/2026-10-10f`. 이 플랜을 `design/22-bundle37.md` 로(맨 위 한 줄 「원본은 설계 세션 플랜 파일 — S0 에서 옮김」). `PLAN.md` 번들 표에 번들 37 행(`S0`→`1-59a`→…→`6-31`, 설계 22, 머지 위임 없음) · 분할표: 1-59 행 상태를 「설계 22 — 1-59a·1-59b 로 쪼갬」, 5-24 행을 「설계 22 — 5-24a~d 로 쪼갬」, 새 행 `1-59a`·`1-59b`·`5-24a`·`5-24b`·`5-24c`·`5-24d`(각 §4 요약 + 선행 + 「대기」), 6-30·6-31 행 끝 칸 「설계 22 — 대기」, 1-58h 선행에 `5-24d`. `PROGRESS.md` 현재 상태 — 가지·진행중 청크(「남은 1-58h …」 중복 문장 지움) · 이력 한 줄.
- 검증: `bash scripts/verify.sh`(docs) → 커밋 `docs: S0 번들 37 설계 22 — 1-59·5-24 쪼갬 · 번들 표`.

#### 1-59a. 공통 프로필 고르기 — `common.js` · 화면 선언 · 강제
- 대상: `tools/common.js` · 여덟 html 의 `<body>` 한 줄씩 · `ToolsFolderTest` · `SmokeHtmlUnitTest`.
- 변경 ① `common.js`:

  ```js
  var profilesCache = null;
  function profiles() { // {names, active} — 한 번 받아 둔다
    if (!profilesCache) profilesCache = api('/api/profiles');
    return profilesCache;
  }
  /* 1-59 — 프로필 고르기. <body data-profile="접속·범위"> 가 있는 화면에만. header 끝, 없으면 h1 뒤. 바꾸면 새로 연다(init 전부가 프로필을 읽는다) */
  function profilePicker() {
    var uses = document.body.getAttribute('data-profile');
    if (uses === null || document.getElementById('tb-profile')) return;
    var box = document.createElement('span'); box.id = 'tb-profile'; box.className = 'opt';
    box.appendChild(document.createTextNode('프로필 '));
    var sel = document.createElement('select'); sel.id = 'tb-profile-sel'; box.appendChild(sel);
    if (uses) { var s = document.createElement('small'); s.textContent = uses; box.appendChild(s); }
    var header = document.querySelector('header');
    if (header) { box.style.marginLeft = 'auto'; header.appendChild(box); }
    else { var h1 = document.querySelector('h1'); box.className += ' tb-profile-row'; if (h1 && h1.parentNode) h1.parentNode.insertBefore(box, h1.nextSibling); else document.body.insertBefore(box, document.body.firstChild); }
    profiles().then(function (p) { fillProfiles(sel, p); }, function () { opt(sel, '', '(프로필 목록을 못 읽었다)'); });
    sel.addEventListener('change', function () {
      if (!sel.value) return;
      sel.disabled = true;
      api('/api/profiles/active', { body: { name: sel.value } }).then(function () { location.reload(); },
        function (e) { sel.disabled = false; alert('프로필을 못 바꿨다 — ' + e.message); });
    });
  }
  ```
  `fillProfiles(sel, p)` = db_browser `loadProfiles` 의 option 글 그대로(`(profiles 폴더에 YAML 없음)`·`(프로필 고르기)`). `injectStyle` 에 `#tb-profile small{color:var(--muted);margin-left:6px}` · `.tb-profile-row{float:right;margin:2px 0 0}`. `badge()` 의 ping 성공 뒤 `profilePicker()`. `watch()` 의 `alive` 핸들러 — `sel` 이 있고 값이 다르면 `sel.value = ev.data`, `document.dispatchEvent(new CustomEvent('tb:profile', {detail: ev.data}))`(HtmlUnit 에 `CustomEvent` 가 없으면 `document.createEvent('Event')` + `initEvent` 로, `detail` 은 `ev.name` 대신 `TB.activeProfile` 변수에). `window.TB` 에 `profiles: profiles`.
- 변경 ② 여덟 html `<body>` → `<body data-profile="…">`: code_check 「규칙 켬·끔 · 프로젝트 루트」 · deliverable_sql 「접속 · 작성자·기관 · 산출물 범위」 · logical_name 「접속 · 제외 토큰 · 산출물 범위」 · spring_source_generator 「생성기 기본값」 · db_browser 「접속」 · program_analysis 「프로젝트 루트 · 프레임워크」 · jsp_formatter 「프로젝트 루트 · 백업 폴더」 · dev_tools 「접속」.
- 변경 ③ `ToolsFolderTest.profilePickerDeclared` — `themedPages()` 마다 html + 같은 이름 `_ext.js` 글에 `/api/profiles`·`/api/conn`·`/api/fs/defaults`·`/api/check/rules` 중 하나 있음 ⇔ `data-profile=` 있음. (`common.js` 는 뺀다.) `SmokeHtmlUnitTest.profilePickerOnProfileScreens` — `code_check`·`db_browser` 에서 `#tb-profile-sel` 이 있고 선택 글 `example` · `small` 글 비어 있지 않음; `sql_snippets`·`special_chars` 엔 `#tb-profile` 없음.
- 검증: 빠른 검증 초록(`opensWithoutScriptErrors` 12 화면 그대로) → 커밋 `feat: 1-59a 공통 프로필 고르기 — common.js 머리줄 · 화면 선언 · 강제`.
- 의존: S0.

#### 1-59b. db_browser 의 자기 고르기 없애기
- 대상: `tools/db_browser.html` · `SmokeHtmlUnitTest` · `scripts/puppeteer/smoke-dbbrowser.js`.
- 변경: `<label for="profile">프로필</label><select id="profile">` 삭제, 카드 `<h2>프로필 · 접속</h2>` → `<h2>접속</h2>`. `loadProfiles()` → `loadActive()` = `TB.profiles().then(p => state.active = p.active || null)`. change 핸들러 블록(:422-431) 삭제. DOMContentLoaded 사슬은 `loadActive().then(loadConns)…`. 시험: `dbBrowserFillsProfileAndConnections` — `#profile` 단언을 `#tb-profile-sel` 선택 글 `example` 로 · `dbBrowserPicksFirstConnection` — `#tb-profile-sel` 을 b 로 → `wc.waitForBackgroundJavaScript(5000)` → **reload 뒤 페이지를 다시 잡는다** `page = (HtmlPage) wc.getCurrentWindow().getEnclosedPage()` → `.item.on` 이 bee · `#tb-profile-sel` 값 b; 「프로필을 바꿨다 — 첫 접속 bee 선택」 단언은 지운다 · `dbBrowserWithoutActiveProfile` — `#tb-profile-sel` 의 `(프로필 고르기)`. `smoke-dbbrowser.js:94-95` — `page.select('#tb-profile-sel','zero')` → `page.waitForNavigation({waitUntil:'networkidle2'})` → `waitForSelector('#conns .item')`.
- 검증: `SmokeHtmlUnitTest#dbBrowser*` 초록, 빠른 검증 → 커밋 `feat: 1-59b DB 브라우저 프로필 칸을 공통 고르기로`.
- 의존: 1-59a. 실패 사다리: HtmlUnit 이 `location.reload()` 를 안 따라가면 `wc.getPage(url)` 로 다시 연다(핸들러는 그대로).

#### 5-24a. 코드 검사 탭 셋 · 결과 묶음 탭마다 · 결과 칸 · 비교 버튼 삭제
- 대상: `tools/code_check.html` · `tools/code_check_ext.js` · `SmokeHtmlUnitTest`(+`ToolsFolderTest` 한 줄).
- 변경 ① html — §3 배치. 뼈대:

  ```html
  <header><h1>코드 검사</h1><span class="opt">…</span></header>
  <div class="tabbar"><span class="t on" id="tabDir">폴더 검사</span><span class="t" id="tabPaste">붙여넣기 검사</span><span class="t" id="tabDeploy">배포 목록</span></div>
  <div class="body">
    <div class="panes">
      <div class="pane" id="paneDir">
        <div class="dirbar">폴더 #dir #dirRecent #changedLabel #changed #vcsInfo <button class="btn-p" id="runDir">폴더 검사</button> <button class="btn-red" id="dirStop" disabled>중지</button>
          <span class="opt">이력 <select id="runs"><option value="">(이번 검사)</option></select></span></div>
        <div id="dirRes"></div>
        <div class="bar">등급 #dirSev · 묶음 #dirGroup · 찾기 #dirText · <button class="btn-green" id="dirCopy" disabled>복사</button> <button class="dl" id="dirXlsx" disabled><span class="ico ico-xlsx"></span>코드 검사 결과</button> <span id="dirCount"></span></div>
        <div class="split"><div id="dirResult" class="pick"></div><div id="dirPreview" class="well">행을 누르면 그 줄 앞뒤 5줄</div></div>
      </div>
      <div class="pane" id="panePaste" hidden>
        <div class="bar">언어 #lang <button class="btn-p" id="runText">붙여넣기 검사</button> <button class="btn-red" id="pasteStop" disabled>중지</button></div>
        <textarea id="paste" …></textarea>
        <div id="pasteRes"></div>
        <div class="bar">… #pasteSev #pasteGroup #pasteText #pasteCopy #pasteXlsx #pasteCount</div>
        <div class="split"><div id="pasteResult" class="pick"></div><div id="pastePreview" class="well">…</div></div>
      </div>
      <div class="pane" id="paneDeploy" hidden>(지금 배포 pane 그대로 — `#depMsg` 는 5-24d)</div>
    </div>
    <div id="rulesCol"><div class="label">규칙 — 묶음을 누르면 펼친다(개별 켬·정규식)</div><div class="hint">(5-24d)</div><div id="rules" class="well"></div><div class="row"><button id="saveRules">프로필에 저장</button><span id="ruleMsg"></span></div></div>
  </div>
  ```
  `#runs` 는 이 청크에선 비워 두고 5-24c 가 채운다. 로컬 `<style>`: `.body{flex:1;display:flex;gap:12px;padding:10px 14px 12px;overflow:hidden}` `.panes{flex:1;min-width:0;display:flex;flex-direction:column}` `.pane{flex:1;display:flex;flex-direction:column;gap:6px;overflow:hidden}` `#rulesCol{width:34%;min-width:300px;display:flex;flex-direction:column;gap:6px}` `#panePaste textarea{flex:0 0 32%}` 나머지는 지금 규칙을 id 만 바꿔(`#result`→`.pick` 공통, `#preview`→`.preview`). 탭 글·`hidden` 전환.
- 변경 ② ext.js — `panes = { dir: pane('dir'), paste: pane('paste') }`, `pane(prefix)` 가 id 를 만든다(`res`·`result`·`preview`·`count`·`copy`·`xlsx`·`sev`·`group`·`text`·`stop`). `start(pane, body, label)` → `TB.result(pane.res, 'run', {summary: label + ' 시작'})`; `poll` 진행 「검사 중 n%」 run · DONE 「파일 n · 결과 m · … · 이력 #id」 ok · CANCELLED 「중지함 — 이력에 남기지 않았다」 **stop**(R12) · 그 밖 fail. `render(pane)`·`preview(pane,f)`·`copy(pane)`(알림은 `TB.result(pane.res,'ok',{summary:t})` 대신 — 복사 알림은 결과 칸이 아니라 `.bar` 의 `#dirCount` 옆 작은 글 `#dirNote`에 — **정한다: 복사 알림은 `pane.res` 요약 한 줄 ok**) · `xlsx(pane)` → `TB.result(pane.res,'ok',{summary:'코드 검사 결과 n행', path:r.path})`(R4). `compare()`·`#cmp` 삭제. `showTab(name)` — 셋 중 하나 보이고 `#rulesCol.hidden = (name==='deploy')`. 실행 중 `runDir`·`runText` 둘 다 잠금, 중지 버튼은 시작한 pane 것만 켠다. `runDir` 의 「폴더 경로를 넣는다」·`runText` 의 「붙여 넣은 글이 없다」 는 `pane.res` fail.
- 변경 ③ 시험 — `codeCheckStops`: `#runStop`→`#dirStop`, `#msg`→`#dirRes` 글 + 클래스 `res-stop` · `codeCheckFolderRun`: `#result`→`#dirResult`, `#preview`→`#dirPreview`, `#copy`→`#dirCopy`, `#xlsx`→`#dirXlsx`, xlsx 단언을 `#dirRes` `res-ok` + `.res-p` 가 `코드검사-\d+\.xlsx` 로 끝남 · 새 `codeCheckPasteKeepsFolderResult` — 폴더 검사 뒤 탭 전환 → `#paste` 에 `System.out.println(1);` → `#runText` → `#pasteResult` 행 ≥1 **이고 `#dirResult` 행 수 그대로** · `ToolsFolderTest.RESULT_SITES` 에 `code_check.html` 0·`code_check_ext.js` 4 — **아직 `saveNoticesGoThroughSavedText` 의 `code_check_ext.js` 2 는 그대로**(xlsx 는 이 청크에서 `TB.result` 로 가니 1 로 낮춘다. 1-58h 가 없앤다). `fileButtonsNameWhatNotHow` 가 code_check 의 `.dl` 글을 본다 — 「xlsx」 글이 남으면 빨강이니 이 청크에서 `[엑셀] 코드 검사 결과`·`[엑셀] 배포 목록` 으로.
- 검증: `SmokeHtmlUnitTest#codeCheck*`·`ToolsFolderTest` 초록 → 빠른 검증 → 커밋 `feat: 5-24a 코드 검사 탭 셋 — 결과 묶음 탭마다 · 결과 칸 · 비교 버튼 삭제`.
- 의존: 1-59a(머리줄 고르기가 header 에 붙는다 — 배치 확인).

#### 5-24b. 비교 API·저장소 삭제
- 대상: `web/CheckRoutes.java` · `core/check/CheckStore.java` · `CheckRoutesTest` · `CheckStoreTest`.
- 변경: `GET /api/check/runs/{id}/compare` 블록(:276-298) 삭제 · `CheckStore` 의 `Compare` record·`previous`·`compare`·`key` 삭제(다른 곳이 안 쓴다 — 컴파일로 확인). `CheckRoutesTest.folderRunHistoryCompare` → 이름 `folderRunHistoryExport`, `/compare` 단언 셋 삭제(두 번째 실행·`/runs`·`/runs/{id}`·`/export` 는 그대로) · `CheckStoreTest.saveListCompare` → `saveList`, `previous`·`compare` 단언 삭제. `CheckRoutes` 머리 javadoc 의 compare 줄 삭제.
- 검증: `CheckRoutesTest`·`CheckStoreTest` 초록, 빠른 검증 → 커밋 `chore: 5-24b 「앞 실행과 비교」 API·저장소 삭제`.
- 의존: 5-24a.

#### 5-24c. 이력 고르기
- 대상: `tools/code_check_ext.js` · `tools/code_check.html`(한 줄) · `SmokeHtmlUnitTest`.
- 변경: `loadRuns(selectId)` — `GET /api/check/runs` → `#runs` option: `''` `(이번 검사)` + 행마다 `'#' + r.id + ' ' + when(r.startedAt) + ' · ' + tail(r.path) + ' · ' + r.findings + '건' + (r.changedOnly ? ' · 변경분' : '')`(`when` = `MM-DD HH:mm`, `tail` = 마지막 경로 마디, `(붙여넣기)` 경로면 그대로). `poll` DONE 뒤 `loadRuns(runId)`. `#runs` change → 값 없으면 무시, 있으면 `openRun(id)`: `GET /api/check/runs/{id}` → `panes.dir.findings = findings`(excerpt 없음 → 원문 칸 빈칸), `panes.dir.root = run.path`, `panes.dir.runId = id`, `panes.dir.pastedText = null`, `$('dir').value = run.path`(「(붙여넣기)」 이면 빈 채로), `render(panes.dir)`, `dirCopy`·`dirXlsx` 켬, `TB.result('dirRes','ok',{summary:'이력 #' + id + ' · ' + when + ' · ' + n + '건 — 미리보기는 지금 파일(검사 뒤 바뀌었으면 줄이 어긋난다)', path: run.path})`. 미리보기는 지금 `preview` 그대로(`/api/fs/read`). 시험 `codeCheckHistoryPicker` — `codeCheckFolderRun` 의 임시 프로젝트로 폴더 검사 두 번 → `#runs` option ≥3(이번 + 둘), 라벨이 `#` 로 시작하고 `건` 포함 → 첫 실행 id 를 고르면(`setSelectedAttribute`) `#dirResult` 행 수 = 그 실행의 건수 · `#dirRes` 글에 「이력 #」 · 행 클릭 → `#dirPreview` 에 `a/A.java:`.
- 검증: 스모크 초록 → 빠른 검증 → 커밋 `feat: 5-24c 코드 검사 이력 고르기`.
- 의존: 5-24b.

#### 5-24d. 규칙 저장 버튼 · 규칙 머리 한 줄 · 배포 안내 · 결과 칸
- 대상: `tools/code_check.html` · `tools/code_check_ext.js` · `SmokeHtmlUnitTest`.
- 변경: `#saveRules` → `<button class="btn-red" id="saveRules"><span class="ext ext-yaml">YAML</span>규칙 켬·끔 → <span id="saveTarget">…</span>.yaml</button>` + `<div id="ruleRes"></div>`(`#ruleMsg` 삭제). `#saveTarget` 글 = `TB.profiles()` 의 `active`(없으면 `(활성 프로필 없음)`, 버튼 `disabled`); `document.addEventListener('tb:profile', …)` 로 갱신. `saveRules()` — `TB.result('ruleRes','run',{summary:'저장 중…'})` → PUT → `TB.result('ruleRes','ok',{summary:'규칙 켬·끔 저장 — 묶음 ' + Object.keys(c.groups).length + ' · 규칙 ' + Object.keys(c.rules).length, path: r.path, backup: r.backup})` · 실패 fail(서버 글). 규칙 칸 머리 `.hint` 글(§3). `#depNote` 글(§3). 배포: `#depMsg` → `#depRes`(`TB.result` — 조회 중 run · 결과 ok 「추가 a · 수정 m · 삭제 d」(xlsx 면 + `path`) · 오류 fail), 버튼 `[엑셀] 배포 목록`. 시험 — `codeCheckFolderRun` 끝에: `#saveTarget` 글이 활성 프로필 이름 · `#saveRules` 클릭 → `#ruleRes` `res-ok`, `.res-path` 둘(경로·백업) · `#depNote` 가 「운영 반영」 으로 시작. `RESULT_SITES` 의 `code_check_ext.js` 하한을 6 으로.
- 검증: 스모크 초록 → 빠른 검증 → 커밋 `feat: 5-24d 규칙 켬·끔 저장 버튼 · 규칙 머리 · 배포 안내 · 결과 칸`.
- 의존: 5-24c, 1-59a(`TB.profiles`·`tb:profile`), 1-58g(응답 `backup`).

#### 1-58h. `TB.savedText` 삭제 · 시험 정리
- 대상: `tools/common.js` · `ToolsFolderTest` · `SmokeHtmlUnitTest`.
- 변경: `common.js` 의 `savedText` 함수·`window.TB` 항목·머리 주석 삭제. `ToolsFolderTest.saveNoticesGoThroughSavedText` 삭제(「`'저장 ' +` 직접 이어붙이기 금지」 루프는 `saveNoticesGoThroughResult` 로 옮긴다 — common.js 제외 전 파일) · `saveNoticesGoThroughResult` 에 「어느 파일에도 `TB.savedText(` 없음」 단언 · 새 `xlsxIconOnlyOnXlsxButtons` — 모든 html 의 `<button>` 에 `.ico-xlsx` 와 `.ext` 배지가 같이 있지 않다. `SmokeHtmlUnitTest.savedTextShowsFileOrFolder` → `joinPathJoinsWithDirSeparator`(joinPath 단언 둘만).
- 검증: 빠른 검증 → 커밋 `chore: 1-58h TB.savedText 삭제 · 저장 표시 시험 정리`.
- 의존: 5-24a·5-24d.

#### 6-30. JSP 링크 꼴 enum · 「포함」
- 대상: `core/analyze/LinkKind.java`(신설) · `core/analyze/JspLinks.java` · `core/analyze/Screens.java` · `tools/program_analysis_ext.js`(한 줄) · 시험(`JspLinksTest`·`SmokeHtmlUnitTest`·`ToolsFolderTest`).
- 변경: enum(§3) — `code()`·`word()`·`static LinkKind of(code)`·`static Map<String,String> words()`(순서 유지). `JspLinks.CUE` 에 `<c:import`·`<jsp:include`·`<iframe` 단서 추가(`c:import` 는 지금 `href=`·`c:import` 묶음에서 뺀다), `kind()` 는 `LinkKind` 반환, `Link(url, kind.code())`. `Screens.KIND_WORDS = LinkKind.words()` · `KIND_UNKNOWN = LinkKind.UNKNOWN_WORD`. ext.js `KIND_WORD` 에 `include: '포함'`. 시험 — `JspLinksTest.kinds()` 의 `/g.do` → `include` + 줄 둘 추가(`<jsp:include page="<c:url value='/h.do'/>"/>` → include · `<iframe src="/i.do">` → include) · `SmokeHtmlUnitTest:1056` 「BoardList.jsp (포함)」 · `ToolsFolderTest.linkKindWordsMatchEnum` — ext.js 에서 `KIND_WORD = {…}` 를 정규식으로 읽어 `{코드: 글}` 로 만들고 `LinkKind.words()` + `모름` 과 같은지. `JspLinksTest.golden` 은 urls 만이라 불변(확인).
- 검증: 그 시험 셋 초록, 빠른 검증 → 커밋 `feat: 6-30 JSP 링크 꼴 enum · c:import·jsp:include·iframe 은 「포함」`.
- 의존: 없음(1-59·5-24 와 독립).

#### 6-31. view 꼴 거르기
- 대상: `core/analyze/JavaGraph.java` · `core/analyze/Unresolved.java` · 골든(`golden/analyze/java-graph.json` · `golden/corpus/analyze-egov*.json`) · `JavaGraphTest`(케이스 하나).
- 변경: `JavaGraph` 에 `static final Pattern VIEW_NAME = Pattern.compile("[\\p{L}\\p{N}_./$-]+")`. `views()` 끝 루프에서 `view` 가지: `VIEW_NAME.matcher(n).matches()` 아니면 `note("viewShape", c.file, line(m), c.name + "." + m.getNameAsString())` 하고 건너뜀. 프로그램을 만드는 자리(`kind(c, m, views)` 호출)에서 `json` 이면 `views = List.of()`. `Unresolved.KINDS` 에 `viewShape`(§3 글) — `viewDynamic` 뒤. `JavaGraphTest` 에 픽스처 메서드 하나(`return "&qestnrId=" + id;` 와 `@ResponseBody` 로 `"{\"error\":1}"`) → views 없음 + unresolved `viewShape` 1. 골든: `-Dgolden.update=true` 로 `JavaGraphTest`·`AnalyzeCorpusTest`(표본 폴더 필요) 갱신 → diff 를 본다 — `analyze-egov.json` 의 `views.view` 가 줄고 `unresolved.viewShape` 가 생겨야, `orphanJsps`·`jspUrls` 는 그대로여야(달라지면 중단 조건 ③). 건수를 이력에.
- 검증: `JavaGraphTest`·`AnalyzeCorpusTest`(corpus 태그 — `-Dgroups=corpus -Dtest=AnalyzeCorpusTest`) 초록, 빠른 검증 → 커밋 `fix: 6-31 view 이름 꼴 아닌 글은 미해결 viewShape — json 프로그램은 view 없음`.
- 의존: 없음.

#### 마무리(CLAUDE.md 「마무리」 그대로)
1. 독립 리뷰 `git diff origin/main...HEAD`. 2. `gate-probe.sh`. 3. `bash scripts/verify.sh` — 끝 줄의 `corpus 레인 바뀜` → `bash scripts/verify.sh --corpus`(7분). 4. push. 5. PR(본문 표 — 청크 열). 6. CI 폴링. 7. 머지 위임 없음 — PR 열고 멈춤. 8. 끝 보고. PROGRESS 이력·분할표 상태·번들 37 상태.

### 5. 금지 사항

- `pure/` · `src/main/resources/db/migration/*`(V010 주석 포함) · `core/check/Runner`·규칙 YAML · `core/analyze/AnalyzeRunner.JSP_NAME`(그대로) · CI · `scripts/verify*` · `pom.xml`.
- 탭마다 다른 프로필 · 프로필 바꿀 때 화면별 부분 갱신(reload 로 통일) · `#profile` 을 db_browser 에 남기기.
- 검사 이력에 발췌·본문 저장(규칙 3) · `check_*` 표 변경.
- 시험을 느슨하게 해서 초록(골든은 diff 를 보고 뜻이 맞을 때만 갱신 — 6-31 의 `orphanJsps`·`jspUrls` 가 바뀌면 갱신 말고 중단).
- `verify.sh --full`·`--db` 는 이 번들에서 안 돌린다(db 레인 불변).
- ES6 문법(HtmlUnit) · CDN · 새 의존성.

### 6. 최종 검증

- 청크마다 `bash scripts/verify.sh` 초록 · 끝에 `--corpus` 초록(`JsCorpusTest`·`CheckCorpusTest`·`AnalyzeCorpusTest` 포함).
- `SmokeHtmlUnitTest` 전부 초록 — 특히 `profilePickerOnProfileScreens`·`dbBrowser*` 셋·`codeCheck*` 넷(중지·폴더·붙여넣기가 폴더 결과를 안 덮음·이력 고르기)·`BoardList.jsp (포함)`.
- `ToolsFolderTest` — `profilePickerDeclared`·`linkKindWordsMatchEnum`·`xlsxIconOnlyOnXlsxButtons`·`saveNoticesGoThroughResult`(savedText 0).
- `gate-probe.sh` 8 전부. 집 검증: `node scripts/puppeteer/visual.js b37` 로 code_check·db_browser 스크린샷(머리줄 고르기 위치·규칙 세로 칸·결과 칸) — 사람 눈.
- 사람이 볼 것: 데모(`demo` 프로필)에서 머리줄 고르기로 프로필 바꾸기 · 코드 검사 세 탭 · 이력 고르기 · 규칙 저장 버튼 글 · 화면 전수 탭의 「포함」.

### 7. 중단 조건

멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- ① HtmlUnit 이 `location.reload()`·`CustomEvent` 를 두 번 고쳐도 못 다룬다(1-59a·b 사다리 뒤).
- ② 5-24a 에서 `code_check_ext.js` 의 pane 분리가 `deploy`·`vcsInfo` 흐름을 깨 두 번 고쳐도 `codeCheck*` 가 빨강.
- ③ 6-31 골든 갱신에서 `orphanJsps`·`jspUrls`·`analyze-egov-orphan-jsp.txt` 가 바뀐다(view 거름이 고아 판정에 닿았다 — 설계 밖).
- ④ 6-30 에서 `c:import` 를 include 로 돌리자 corpus 골든의 꼴 글이 바뀐다(실측 0 건이라 안 바뀌어야).
- ⑤ 검증 2회 연속 빨강이고 원인이 플랜 밖 · 스텝에 없는 파일 3개 이상.

### 8. 불확실 항목(첫 스텝 전에 확인)

- HtmlUnit(4.x)이 `location.reload()` 뒤 `wc.getCurrentWindow().getEnclosedPage()` 로 새 페이지를 주는지 — 1-59b 에서 잰다. `CustomEvent` 생성자 지원 여부 — 없으면 `createEvent('Event')`.
- `common.css` `header` 가 `flex-wrap:wrap` 이라 설명 글이 길면 고르기가 둘째 줄로 내려간다 — 1400 폭에서 code_check 가 한 줄인지 집 검증(visual.js)으로.
- `GET /api/check/runs` 의 `startedAt` 꼴(ISO 문자열인지) — `when()` 이 `T` 를 공백으로, 앞 16자 중 `MM-DD HH:mm` 만.
- `AnalyzeCorpusTest` 골든 갱신에 표본 폴더·node 가 필요(있다 — 번들 36 에서 돌았다).
- `JspLinks.CUE` 에 `<iframe` 을 더할 때 창 200자 규칙상 `<iframe src="/x.do">` 는 토큰 바로 앞이라 잡힌다 — 시험으로 확인.
