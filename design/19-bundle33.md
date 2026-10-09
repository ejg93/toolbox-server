# 설계 19 — 번들 33: 데모 점검 요구 여섯(1-56 · 3-15 · 6-21 · 6-22 · 6-23 · 6-24) (Fable, 2026-10-10)

이 플랜은 실행 담당 모델(Opus)이 그대로 따라 실행한다. 실행자는 탐색 과정을 모른다. 판단이 필요한 지점을 남기지 않았다. 번들 모드 규칙(CLAUDE.md 「번들 모드」) 그대로 — 갈려도 안 멈추고, 행당 「정한 것(계획 밖)」 둘까지.

사용자가 2026-10-09 데모 점검에서 정한 것(뒤집지 않는다):
- **1-56** dev_tools 의 「폴더 일괄 — 위 언어로」 를 통째로 뺀다. 붙여넣기 주석 삭제 · `window.TB_CMT` · `/api/fs/*` · JSP 포매터 폴더 탭은 남긴다.
- **3-15** 산출물 범위(`deliverable.filter`)를 두 화면이 같게 — 표준 사전 화면은 프로필에 filter 가 있으면 켠 채로 시작·없으면 끄고 잠근다, 산출물 화면은 조건 한 줄을 늘 보인다. 글은 서버 한 곳이 만든다.
- **6-21** CRUD 매트릭스는 넓은 격자(열 = 표 전부)를 걷고 **모듈 매트릭스**(기본, 행 = 표 · 열 = 모듈 · 칸 = 합집합)와 **세로 목록**(프로그램 · URL · 표 · CRUD) 둘로. 모듈 = URL 앞 두 마디(마지막 마디는 파일).
- **6-22** 미해결 종류마다 한글 이름 · 뜻 · 푸는 법을 `core/analyze` 한 곳에 두고 화면 칩·xlsx 가 같이 쓴다. 새 종류가 뜻 없이 못 들어온다.
- **6-23** 정합성의 「DB 에 없는 표」 는 스냅샷 범위에 걸려 빠진 표를 「없음」 과 가른다. 사유는 `Scope` 한 곳.
- **6-24** 프로그램 분석 xlsx 는 한 파일에 시트 여럿 — 프로그램목록 · CRUD 목록 · CRUD 모듈 · 미해결 · 정합성(스냅샷을 골랐을 때만).
- **6-20**(개인정보 화면 전수표)은 이 번들에 없다 — 열린 질문 넷의 답 뒤 설계(끝 「사람이 할 것」).

## 1. 목표와 범위

- **목표**: 데모에서 「쓸모가 없다 · 뭔지 모른다 · 너무 넓다 · 두 화면이 다르다」 로 지적된 여섯을 닫는다. 끝나면 — dev_tools 에 폴더 일괄 칸이 없고, 산출물·표준 사전 화면이 같은 범위 글을 보이고, 프로그램 분석의 CRUD 탭이 표 1,000 개여도 모듈 수만큼의 열이고, 미해결 탭이 한글 이름·뜻·푸는 법을 보이고, 정합성이 「범위 밖 — 제외 접두 TMP_」 를 가르고, xlsx 가 한 파일이다.
- **In**: 행 `S0`(문서) → `1-56` → `3-15a`(서버) → `3-15b`(화면) → `6-23a`(엔진) → `6-23b`(화면) → `6-22a`(엔진·API) → `6-22b`(화면) → `6-21a`(엔진·API) → `6-21b`(화면) → `6-24` → 마무리.
- **Out**: 6-20 · 산출물 18(테이블 대 응용프로그램 상관도)의 넓은 격자 — NIA 서식 그대로 둔다(`DeliverableRoutes` 의 상한만 제 자리로 옮긴다) · 00_작성안내 「요약」 의 범위 글(골든이 쥔다 — 안 바꾼다) · `pure/` · 데모 프로필 · 검증 인프라.

## 2. 현재 구조 요약(2026-10-10 실측)

### 2.1 가지 상태
- `work/2026-10-09` = main `9e75670` 위에 행만(3-15 행 커밋). 트리 깨끗. 번들 31·32 머지(#51 `1a44b12` · #52 `6740de6`). 열린 PR 없음.
- `PROGRESS.md` 424·425 줄 해시 칸이 「(이 커밋)」 — 각각 `fe922f3`·`9e75670` 로 채운다(S0).

### 2.2 프로그램 분석 — 코드 자리
- `core/analyze/Unresolved.java`(9줄) — `record Unresolved(String kind, String file, int line, String detail)`. 종류 목록은 javadoc 만. **실제 쓰는 종류 18**(javadoc 에 없는 `jspUrl` 포함): `parse`(JavaGraph:162 · MapperIndex:93·96) · `table`·`tagVerb`(SqlTables:139·93 — `Result.unresolved` 문자열 목록 → MapperIndex:148 `note(unresolved, seen, u, …)`) · `dialect`(MapperIndex:154) · `missing`(AnalyzeRunner:157 · MapperIndex:199 include) · `statement`(JavaGraph:462·632) · `prefix`(JavaGraph:451) · `ambiguous`(JavaGraph:526·578·724 · JpaIndex:491) · `depth`(JavaGraph:352) · `viewDynamic`(JavaGraph:839) · `jspUrl`(JspLinks:67) · `entityName`(JpaIndex:284) · `jpaType`(JpaIndex:362·423) · `jpaMethod`(JpaIndex:419) · `jpql`(JpaIndex:405·491) · `namedQuery`·`criteria`(JavaGraph:639·640) · `querydsl`(JavaGraph:724). 만드는 꼴 다섯 — `note("k", …)` · `new Unresolved("k", …)` · `note(unresolved, seen, "k", …)` · `unresolved.add("k")` · 삼항 `hit.isEmpty() ? "jpql" : "ambiguous"`. 시험에서 직접 만드는 곳은 `AnalyzeStoreTest:43·72`(`"prefix"`)뿐.
- `AnalyzeStore` — `ProgramRow(id, className, method, file, line, verb, url, params, kind, description, views, statements, crud: Map<표, 글자>)`(13인자, crud 는 TreeMap) · `Matrix(tables, rows)` · `crud(runId)` = 표 전부(정렬) + 행 · `unresolved(runId)`(kind·file·line·detail 순) · `orphans(runId)`.
- `web/AnalyzeRoutes`(202줄) — `MAX_TABLES = 16_000`(넓은 매트릭스 열 상한, `DeliverableRoutes:117` 도 18 문서에 쓴다) · `GET …/crud` → `store.crud(id)` 그대로 · `POST …/export {format}` → `Outputs.xlsx` 둘(`프로그램목록-<id>.xlsx` 9열 · `CRUD매트릭스-<id>.xlsx` 프로그램·URL + 표 열) → `{dir, files:[{name,path,rows}×2]}` · `GET …/consistency?snapshotId=` → `Consistency.of` · `GET …/unresolved`. `runId(ctx, store)` 가 400·404 를 쓴다.
- `web/Outputs` — `text(name)`·`num(name)` → `ResultTable.Col` · `dir(Profile)` · `xlsx(Path dir, name, cols, rows)`(표 하나 `XlsxWriter.write(ResultTable, Path)`). `XlsxWriter.write(LinkedHashMap<String, ResultTable> sheets, Path)` 는 이미 있다(2-13 작성안내). `ResultTable(cols, rows, false, -1, 0)`.
- `core/analyze/Consistency`(86줄) — `Missing(table, programs)` · `Unused(schema, table, type)` · `Report(snapshotId, missingInDb, unusedInCode, deadStatements, orphanJsps)` · `of(analyze, snapshots, runId, snapshotId)`: `snapshots.get(id)` 로 표만 본다. 범위를 안 본다.
- `core/meta/Scope`(108줄) — `record Scope(schemas, Exclude, Include, skipEmpty)` · `isFiltered()`(`@JsonIgnore` — `is*` 라 Jackson 이 집어 간다) · `acceptsSchema` · `accepts(Table)`: 스키마 → skipEmpty(rowCount==0) → include 목록(있으면 그것만) → 제외 접두·접미·정규식(`CASE_INSENSITIVE`, 원래 이름에 `find`)·목록. `Table.of(schema, name, type, comment)`(rowCount null).
- `core/meta/SnapshotStore` — `Summary(id, profile, connId, takenAt, note, dbVersion, tableCount, filtered, Scope scope, warningCount)` · `list()` 만 있고 **id 하나의 Summary 를 주는 메서드가 없다** · `get(id) → Optional<List<Schema>>`. `SnapshotService:47` 는 프로필 scope 가 없으면 `Scope.all()` 을 넣어 **새 스냅샷의 scope 는 null 이 아니다**(옛 행만 null, V003).
- `tools/program_analysis.html`(112줄) — 탭 다섯(`TABS` 배열) · CRUD 탭 바 `#fTable`·`#fProg`·`#allRows`(「CRUD 없는 프로그램도」)·`#crudCount` · `#crud` 안에 `#crudLegend` + 격자(`cell()` 로 짠 table, `td.c.c-C/R/U/D` 색) · 미해결 탭 `#fKind`(select, 「영문 n」 옵션) · 정합성 탭 `#conSnap`·`#conRun`·`#conMsg` + 표 넷(`#conMissing` 열 표·프로그램 수). `TB.table(el, 머리[], 행[][])` 는 글자 셀만(title·class 없음). 로컬 `<style>` 은 레이아웃·`#crud` 격자·`.c-*` 색 — `ToolsFolderTest.toolsHaveNoLocalTheme` 가 `:root`·`button{}`·`.btn-*`·`.dl`·목록 밖 고정 색을 막는다. 공용 `.t`/`.t.on`(알약 탭)·`.badge`·`.well`·`.pick`·`.hint`·`.msg` 는 `common.css`.
- `tools/program_analysis_ext.js`(370줄, ES5) — `load(id)` 가 `/crud` + `/unresolved` 를 받아 `matrix`·`unresolved` 에 두고 `renderPrograms`·`renderCrud`·`kindOptions`·`renderUnresolved`·`impactTables`. `renderCrud` 가 격자를 짠다(열 = `matrix.tables`). `xlsx()` → `POST …/export {format:'xlsx'}` → `TB.savedText(paths, dir)`. `consistency()` → `TB.table($('conMissing'), ['표','프로그램 수'], …)`. `CRUD_ORDER`·`crudWords`·`HOW`·`kindLabel` 은 6-18.
- 시험 — `AnalyzeRoutesTest`(픽스처 프로젝트 13 프로그램 · `exportXlsx` 가 파일 둘·열·칸 단언 · `consistency` 스냅샷 없이 · 프로필 `t.yaml` 에 접속 없음) · `ConsistencyTest`(`new Consistency.Missing("COMVNUSERMASTER", 1)` 2인자 단언) · `ScopeTest`(accepts 여덟) · `SmokeHtmlUnitTest.programAnalysisRuns`(805~888: `crudLegend` 글 · `#crud thead th >= 3` · `#crud` 글에 COMTNBBS · `#unresolved tbody tr >= 1` · xlsx 알림 `startsWith("xlsx")` · 정합성 스냅샷 없이) · `AnalyzeCorpusTest`(등급 B 미해결 목록 — kind 글자를 그대로 적는다, 이름 바꾸면 baseline 이 깨진다).
- 픽스처 URL(모듈 예측) — `/bbs/*.do` → `bbs` · `/other`·`/both.do`·`/a.do`·`/b.do` → `/`.

### 2.3 산출물 범위 — 코드 자리
- `Profile.Deliverable(author, org, templateDir, mapping, Scope filter)`. `profiles/example.yaml` 의 filter 는 접두 TMP_·BAK_ · 접미 _BAK·_OLD · 정규식 `_\d{8}$` — **`isFiltered()` 참**(AppTest.config 가 쓰는 프로필이라 공용 `app` 스모크는 「filter 있음」 쪽으로 돈다).
- `core/deliverable/Deliverables.filter(List<Schema>, Scope)`(null 이면 그대로) · `tableCount(List<Schema>)`.
- `web/DeliverableRoutes.registerBuild(app, snapshots, conns, dict, jobs, active, analyses)` — `filtered(ctx, id, snapshots, active)`(거른 뒤 0 이면 400) · `filterCrud`(18 열) · `source(...)` 의 범위 글 「표 n / 스냅샷 m (deliverable.filter)」(00_작성안내 요약 — 안 바꾼다) · `snapshot(ctx, id, snapshots)`. 엔드포인트 `GET` 은 `/api/quality/kinds`·`/api/deliverable/docs` 뿐.
- `GET /api/profiles/{name}` 은 `withoutPasswords()` 한 프로필 JSON(`deliverable.filter` 포함). 두 화면의 `loadProfile()` 이 이것을 읽어 작성자·skipTokens 초안만 채운다.
- `tools/deliverable_sql.html` — 입력 카드 33~35 `#snap`·`#snapMsg`. `loadSnapshots()` → `loadSchemas()`(스냅샷 바뀔 때마다, 681 `msg(id, text, kind)`·682 `fail(id)`). 만들기 결과 866 「표 n / 스냅샷 m (deliverable.filter)」 는 만든 뒤에만.
- `tools/logical_name.html` — 58 `<label class="inline" title=…><input type="checkbox" id="delivScope"> 산출물 범위(deliverable.filter)로 거르기</label>` · 176 `body.deliverableFilter = true` · 377 `runMsg` 앞에 「산출물 범위 · 」 · 564~573 `?scope=deliverable` 이면 `checked = true` 뒤 `loadSnapshots().then(… run(false))` · 574 `loadProfile()`.
- 시험 — `DeliverableRoutesTest.deliverableFilter`(285~: 프로필 fin/fex/fnone, 스냅샷 표 IF_ORDER_RCV·TB_CMM_CD) · `LogicalRoutesTest`(42 프로필 filter ZZ_) · `SmokeHtmlUnitTest.logicalNameOpensFromDeliverable`(589~659: 프로필 **filter 없음**, H2 `smoke312` 표 TB_ZZQX, 646 `delivScope.isChecked()` 단언, 652 `runMsg startsWith("산출물 범위 · 컬럼 ")`) · 다른 logical 스모크(109·143)는 CSV 입력이라 `runMsg` 가 「컬럼 」 으로 시작한다(스냅샷 없음 → 접두 없음).

### 2.4 dev_tools 폴더 일괄 — 자리
- `dev_tools.html` 603~612 `<div class="field" id="td_batch" …>`(라벨·`#td_dir`·`#td_batchPreview`·`#td_batchApply`·`#td_batchMsg`·`#td_batchOut`) — 바로 뒤 614 `<hr class="div">` 는 남긴다. 2702~2703 주석 「백엔드본 폴더 일괄(4-3, dev_tools_ext.js)이 같은 주석 삭제를 쓴다」 + `window.TB_CMT = { strip: cmtStrip, label: CMT_LABEL }` — **내보내기는 남긴다**(V-2 `StripCorpusTest`·`scripts/puppeteer/corpus-strip.js`·`dump-strip.js` 가 쓴다). `#td_killHint`(599~601)는 붙여넣기 주석 삭제가 쓰므로 남긴다.
- `dev_tools_ext.js` — 머리 주석 3 「+ 화면 표시기 「폴더 일괄」 주석 삭제(4-3).」 · 221~305 절(`GLOBS`·`CHUNK`·`SB`·`sbMsg`·`sbOpts`·`sbPreview`·`sbRender`·`sbApply`) · `init()` 318~319 두 줄. `CHUNK` 는 이 절만 쓴다. `joinPath`(24)·`head`(19)·`cell`(12)은 폴더 비교가 쓰므로 남긴다.
- `scripts/puppeteer/smoke-devtools.js` 129~151(폴더 일괄 검사 + `devtools-strip.png`) · 4 머리 주석.
- `index.html:47` 설명 「…서버가 읽는 폴더 비교·주석 삭제 폴더 일괄·로그 SQL 복원 등.」.
- `ToolsFolderTest.saveNoticesGoThroughSavedText`(218~) 233~235 `dev.contains("SB.backupRoot")` 아니면 「dev_tools_ext.js 폴더 적용 알림이 백업 폴더를 안 보인다」 — 이 세 줄은 폴더 일괄 전용이라 같이 뺀다. `PLAN.md:344` 파일 API 행 글 「주석 삭제·jsp 폴더 일괄은 이 위에서 브라우저 JS 가 돈다(4-1·4-3·4-4)」.

### 2.5 시험·검증 레인
- `src/main/resources/tools` 는 java 레인 — 화면만 고쳐도 `mvn test` 가 돈다. `scripts/puppeteer` 는 tools 레인. `design/`·`PLAN.md`·`PROGRESS.md` 는 docs 레인.
- 컨테이너 시험(`@Tag("db")`)은 이 번들이 안 건드린다. `--full` 은 마무리에서만.

## 3. 설계 결정

- **D1 [고정] 범위 글은 `Scope` 한 곳** — `Scope.summary()`(한 줄 요약)·`Scope.reason(String table)`(이름 규칙만 — 걸린 규칙 한 줄, 안 걸리면 빈 글)·`Scope.reason(Table)`(스키마 → skipEmpty → 이름 규칙, `accepts` 와 같은 순서). **`accepts(Table)` 는 `reason(t).isEmpty()` 로 다시 짠다** — 둘이 어긋날 길을 구조로 막는다. 화면·xlsx·정합성·산출물 범위 줄이 전부 이 글을 쓴다. 글 꼴: 「없음(전부)」 / 「스키마 APP·HR」 / 「포함 목록 n개」(include 가 있으면 제외는 안 적는다 — `accepts` 가 안 보니까) / 「제외 접두 TMP_·BAK_」 / 「제외 접미 _BAK」 / 「제외 정규식 _\d{8}$」 / 「제외 목록 n개」 / 「빈 표 제외」, 구분 ` · `, 목록은 다섯까지 + 「 외 n」.
- **D2 [고정] 산출물 범위는 서버 엔드포인트 하나** `GET /api/deliverable/scope[?snapshotId=]` → `{hasFilter, summary, snapshotTables?, tables?}` — 두 화면이 프로필 JSON 을 각자 해석하지 않는다(`filter:` 가 있어도 비어 있으면 `isFiltered` 거짓 — 서버가 가린다). 버린 대안: 화면이 `/api/profiles/{name}` 의 `deliverable.filter` 를 읽어 글을 만들기 — 두 화면이 또 다르게 적는다.
- **D3 [고정] 정합성 사유** — `Consistency.Missing(table, programs, reason)` · `Report` 에 `scopeSummary`(스냅샷 scope 의 `summary()`, 옛 행 null). 사유 결정 순서: 이름 규칙에 걸림 → 「범위 밖 — <규칙>」 / 안 걸렸고 `schemas` 있음 → 「스키마 <목록> 밖일 수 있음」 / `skipEmpty` → 「빈 표라 빠졌을 수 있음」 / 그 밖 → 「없음」. scope null(옛 스냅샷) → 「없음」. 코드가 쓰는 표는 스키마·행 수를 모르므로 **`reason(String)`** 을 쓴다 — `reason(Table.of(null, …))` 을 쓰면 스키마 규칙에 늘 걸린다(버린 길).
- **D4 [고정] 미해결 종류는 `Unresolved.KINDS`(LinkedHashMap, 18) + compact 생성자 검증** — 모르는 kind 는 `IllegalArgumentException`. 이것이 강제 지점이다(픽스처·표본 시험이 종류를 다 만들어 보므로 새 종류가 뜻 없이 들어오면 그 시험이 빨갛다). 시험은 거기에 더해 (ㄱ) KINDS 키마다 `"키"` 글자가 `core/analyze` 소스(Unresolved.java 밖)에 있다(죽은 항목 없음) (ㄴ) 소스의 다섯 꼴 정규식으로 모은 글자가 전부 KINDS 에 있다. **kind 영문 코드는 바꾸지 않는다** — H2 `analyze_unresolved.kind` 와 `golden/corpus/analyze-*-unresolved` 가 쥔다.
- **D5 [고정] CRUD 보기 계산은 `core/analyze/CrudViews` 한 곳** — `module(url)`·`longRows(rows)`·`moduleMatrix(rows)`. `GET /runs/{id}/crud` 응답 = `{tables, rows, longRows, moduleMatrix}`(앞 둘은 지금 그대로 — 프로그램 목록·영향도·산출물 18 이 쓴다). **화면 JS 는 합집합을 계산하지 않는다** — 모듈 보기에서 「프로그램」 거르기는 잠근다(`disabled`, placeholder 「세로 목록에서」), 「표」 거르기는 행만 숨긴다. 버린 대안: 화면이 longRows 로 다시 합치기 — 서버와 두 벌.
- **D6** 모듈 규칙(사용자 확정): URL 을 `/` 로 쪼개 빈 마디를 빼고, **마지막 마디(파일)를 뺀 앞 두 마디**를 `/` 로 잇는다. 마디가 하나(파일만)거나 없으면 `/`. `?` 뒤는 뗀다. 예 — `/sec/gmt/EgovGroupList.do` → `sec/gmt` · `/bbs/list.do` → `bbs` · `/a/b/c/d.do` → `a/b` · `/other` → `/` · null·빈 글 → `/`.
- **D7 [고정] xlsx 한 파일** `프로그램분석-<id>.xlsx` — 시트 순서 프로그램목록 · CRUD목록 · CRUD모듈 · 미해결 · 정합성(요청 `snapshotId` 있을 때만). `Outputs` 에 시트 여럿 오버로드 하나. `AnalyzeRoutes.MAX_TABLES` 는 지운다 — 18 문서의 상한은 `DeliverableRoutes.MAX_TABLES_18` 로 옮긴다(산출물 18 은 NIA 서식의 넓은 격자라 Excel 열 상한이 그대로 산다). 응답 `{dir, files:[하나], sheets:[{name, rows}]}` — 화면 알림이 시트 수를 보인다.
- **D8** 미해결 화면 — `#fKind` select 를 **칩 줄**(`.t` 알약, `#kindChips`)로 바꾼다: 「전체 n」 + 그 실행에 있는 종류만 KINDS 순서로 「<한글 이름> n」(`title` = 영문 코드). 고른 칩 아래 한 줄 `#kindHelp` 「<이름> — <뜻>. 푸는 법: <푸는 법>」. 표 「종류」 칸은 한글 이름, `td.title` 영문. 글은 `GET /api/analyze/unresolved-kinds` 한 번(init).
- **D9** 3-15 화면 — 표준 사전: `#delivScope` 는 `hasFilter` 면 `checked`·활성, 아니면 `checked=false`·`disabled` + 옆 `#delivScopeMsg` 「프로필에 deliverable.filter 없음 — 전부 변환」(있으면 「산출물 범위: <summary>」). `?scope=deliverable` 는 더는 체크를 켜지 않는다(서버 답이 정한다 — 링크 뜻은 「산출물과 같은 범위」 이고 filter 가 없으면 전부가 그 범위다). 산출물: `#snapMsg` 아래 `#scopeMsg` 「산출물 범위: <summary> — 표 <m> → <n>」(스냅샷 없으면 수 없이), `hasFilter` 인데 `tables == 0` 이면 `err` 색.
- **D10** 1-56 — 폴더 일괄 블록·JS 절·스모크·설명·시험 세 줄을 지운다. `TB_CMT` 내보내기 주석은 「V-2 표본 시험(StripCorpusTest · corpus-strip.js · dump-strip.js)이 같은 주석 삭제를 쓴다」 로. 완료 행 4-3·설계 14/18 은 안 고친다(PLAN 8장 파일 행 글만).
- **D11** 청크 가르기 — 3-15·6-21·6-22·6-23 은 서버(a)와 화면(b)로 가른다(파일 수 1~3 규칙). 분할표 행 번호는 그대로 두고 커밋 제목에 `3-15a` 처럼 쓴다. 번들 표 순서도 a·b.

## 4. 실행 스텝

검증 명령은 전부 `bash scripts/mvn.sh -q -B -Dtest='…' test`(맨몸 mvnw 금지). 청크 끝 `bash scripts/verify.sh` → 초록 → `git commit`(따로).

### S0 문서 — 설계 19 + 이력 손질
- **대상**: `design/19-bundle33.md`(이 파일) · `PLAN.md` · `PROGRESS.md`.
- **변경**: 설계 세션이 한다 — 번들 표에 번들 33 행 · 분할표 1-56·3-15·6-21~6-24 행 설계 칸 앞에 「명세 `design/19-bundle33.md` S<n>.」 · `PROGRESS.md` 424·425 해시 `fe922f3`·`9e75670` · 「현재 상태」 진행중 칸 「번들 33 설계 끝 — Opus 「번들 해」」 · 이력 한 줄.
- **검증**: `bash scripts/verify.sh`(docs 레인) 초록. 커밋 `docs: S0 번들 33 설계 19`.

### S1 = 1-56 dev_tools 「폴더 일괄」 걷기
- **대상**: `src/main/resources/tools/dev_tools.html` · `src/main/resources/tools/dev_tools_ext.js` · `scripts/puppeteer/smoke-devtools.js` · `src/main/resources/tools/index.html` · `src/test/java/kr/ejg/toolbox/web/ToolsFolderTest.java` · `PLAN.md:344`(파일 수 규칙 밖 — 글 한 줄).
- **변경**: ① `dev_tools.html` 603~612 `#td_batch` div 삭제(614 `<hr class="div">` 유지). 2702 주석을 「V-2 표본 시험(StripCorpusTest · scripts/puppeteer/corpus-strip.js · dump-strip.js)이 같은 주석 삭제를 쓴다 — 이 IIFE 밖으로 둘만 내보낸다」 로. `window.TB_CMT = …` 줄 유지. ② `dev_tools_ext.js` 3 줄 삭제 · 221~305 절 삭제 · `init()` 의 `$('td_batchPreview').onclick = sbPreview;`·`$('td_batchApply').onclick = sbApply;` 삭제. `joinPath`·`head`·`cell` 유지. ③ `smoke-devtools.js` 129~151 삭제(머리 주석 4 는 그대로 — 「탭 넷」 설명이 맞다), `devtools-strip.png` 줄도 같이. ④ `index.html:47` 「폴더 비교·주석 삭제 폴더 일괄·로그 SQL 복원」 → 「폴더 비교·로그 SQL 복원」. ⑤ `ToolsFolderTest` 233~235 세 줄(`SB.backupRoot` 검사) 삭제. ⑥ `PLAN.md:344` 「주석 삭제·jsp 폴더 일괄은 이 위에서 브라우저 JS 가 돈다(4-1·4-3·4-4)」 → 「jsp 폴더 일괄은 이 위에서 브라우저 JS 가 돈다(4-1·4-4). 주석 삭제 폴더 일괄은 1-56 에서 뺐다」.
- **검증**: `grep -c "td_batch\|td_dir\|sbPreview\|GLOBS" src/main/resources/tools/dev_tools.html src/main/resources/tools/dev_tools_ext.js scripts/puppeteer/smoke-devtools.js` 전부 0 · `-Dtest='ToolsFolderTest,StripCorpusTest,BackendToolsTest'` 초록(StripCorpusTest 는 표본 폴더가 있어야 — 없으면 `ToolsFolderTest,BackendToolsTest` 만) · `scripts/mvn.sh -q -B -DskipTests package` 뒤 `node scripts/puppeteer/smoke-devtools.js` 「전부 통과」. **닫힘**: `TB_CMT` 를 지우면 `StripCorpusTest`(또는 `dump-strip.js`)가 「TB_CMT 가 없다」 로 빨강 — 내보내기가 살아 있음을 확인.
- **사다리**: `smoke-devtools.js` 가 `switchTab('textdisplay')` 뒤 다른 단언에서 빨강이면 지운 줄 범위를 다시 본다(129~151 밖은 손대지 않았는지). 그래도면 `wip/1-56`.

### S2 = 3-15a `Scope.summary()` + `GET /api/deliverable/scope`
- **대상**: `core/meta/Scope.java` · `core/meta/ScopeTest.java` · `web/DeliverableRoutes.java` · `web/DeliverableRoutesTest.java`.
- **변경**: ① `Scope` 에

```java
    /** 범위 한 줄(3-15·6-23) — 화면·xlsx·정합성이 같은 글을 쓴다. 거르지 않으면 「없음(전부)」 */
    public String summary() {
        if (!isFiltered()) {
            return "없음(전부)";
        }
        List<String> parts = new ArrayList<>();
        if (!schemas.isEmpty()) {
            parts.add("스키마 " + few(schemas));
        }
        if (include != null && !include.tables().isEmpty()) {
            parts.add("포함 목록 " + include.tables().size() + "개"); // accepts 가 include 만 보므로 제외는 안 적는다
        } else if (exclude != null) {
            if (!exclude.prefixes().isEmpty()) parts.add("제외 접두 " + few(exclude.prefixes()));
            if (!exclude.suffixes().isEmpty()) parts.add("제외 접미 " + few(exclude.suffixes()));
            if (!exclude.regex().isEmpty()) parts.add("제외 정규식 " + few(exclude.regex()));
            if (!exclude.tables().isEmpty()) parts.add("제외 목록 " + exclude.tables().size() + "개");
        }
        if (Boolean.TRUE.equals(skipEmpty)) {
            parts.add("빈 표 제외");
        }
        return String.join(" · ", parts);
    }

    /** 다섯까지 `·` 로, 넘으면 「 외 n」 */
    static String few(List<String> xs) {
        return xs.size() <= 5 ? String.join("·", xs) : String.join("·", xs.subList(0, 5)) + " 외 " + (xs.size() - 5);
    }
```
(`summary()` 는 `get`/`is` 접두가 없어 Jackson 이 record 직렬화에 안 넣는다 — `@JsonIgnore` 불필요. 스냅샷 scope JSON 왕복 `SnapshotStoreTest` 가 그대로여야 한다.)
② `DeliverableRoutes.registerBuild` 안, `/api/deliverable/build` 앞에

```java
        // 3-15 — 산출물 범위 한 줄. 표준 사전·산출물 두 화면이 같은 글을 쓴다. snapshotId 가 있으면 거른 뒤 표 수까지
        app.get("/api/deliverable/scope", ctx -> {
            kr.ejg.toolbox.core.meta.Scope f = active.get().map(kr.ejg.toolbox.core.profile.Profile::deliverable)
                    .map(kr.ejg.toolbox.core.profile.Profile.Deliverable::filter).orElse(null);
            boolean has = f != null && f.isFiltered();
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("hasFilter", has);
            out.put("summary", has ? f.summary() : "없음(전부)");
            String raw = ctx.queryParam("snapshotId");
            if (raw != null && !raw.isBlank()) {
                long id;
                try {
                    id = Long.parseLong(raw.trim());
                } catch (NumberFormatException e) {
                    ctx.status(400).json(Map.of("message", "snapshotId 가 수가 아니다"));
                    return;
                }
                Optional<List<Schema>> snap = snapshots.get(id);
                if (snap.isEmpty()) {
                    ctx.status(404).json(Map.of("message", "스냅샷이 없다"));
                    return;
                }
                out.put("snapshotTables", kr.ejg.toolbox.core.deliverable.Deliverables.tableCount(snap.get()));
                out.put("tables", kr.ejg.toolbox.core.deliverable.Deliverables.tableCount(
                        kr.ejg.toolbox.core.deliverable.Deliverables.filter(snap.get(), has ? f : null)));
            }
            ctx.json(out);
        });
```
(거른 뒤 0 이어도 400 을 안 낸다 — 화면이 「표 m → 0」 을 빨갛게 보이는 것이 이 엔드포인트의 일이다. `filtered()` 헬퍼의 400 은 만들기 쪽 그대로.)
③ `ScopeTest.summary`: `Scope.all()` → 「없음(전부)」 · `exclude(prefixes [TMP_, BAK_], suffixes [_BAK])` → 「제외 접두 TMP_·BAK_ · 제외 접미 _BAK」 · include [A, B] + exclude 접두 → 「포함 목록 2개」 · schemas [APP] + skipEmpty true → 「스키마 APP · 빈 표 제외」 · 접두 일곱 → 「제외 접두 P1·P2·P3·P4·P5 외 2」 · `filter: {}` 꼴(`new Scope(List.of(), new Scope.Exclude(null,null,null,null), new Scope.Include(null), false)`) → 「없음(전부)」.
④ `DeliverableRoutesTest.deliverableFilter` 에 — fex 활성 뒤 `GET /api/deliverable/scope?snapshotId=<id>` → `hasFilter true` · `summary "제외 접두 IF_"` · `snapshotTables 2` · `tables 1`; fnone 활성 → `tables 0`·200; `t` 활성(finally 뒤 또는 앞) → `hasFilter false`·`summary "없음(전부)"`·`tables == snapshotTables`; `?snapshotId=x` 400 · `?snapshotId=999` 404.
- **검증**: `-Dtest='ScopeTest,DeliverableRoutesTest,SnapshotStoreTest'` 초록. **닫힘**: `summary()` 의 include 가지를 지우면 ScopeTest 빨강 · 엔드포인트 `has ? f : null` 을 `f` 로 바꾸면 fnone 의 `tables` 는 그대로 0 — 대신 `filter: {}` 프로필(`isFiltered` 거짓)에서 `tables` 가 달라지는 경우는 없다 → 닫힘은 ScopeTest 「없음(전부)」 + 라우트 `hasFilter false` 단언.
- **사다리**: `SnapshotStoreTest` 의 scope JSON 왕복이 `summary` 키로 깨지면 `@com.fasterxml.jackson.annotation.JsonIgnore` 를 `summary()` 에 붙인다.

### S3 = 3-15b 두 화면 — 범위 줄 · 체크 잠금
- **대상**: `tools/deliverable_sql.html` · `tools/logical_name.html` · `web/SmokeHtmlUnitTest.java`.
- **변경**: ① `deliverable_sql.html` 35 뒤 `<div class="msg" id="scopeMsg"></div>`. 함수

```js
	/* 3-15 — 산출물 범위(프로필 deliverable.filter)는 만들기에 늘 적용된다. 만들기 전에도 조건과 표 수를 보인다 — 글은 서버 한 곳 */
	function loadScope(id) {
		TB.api('/api/deliverable/scope' + (id ? '?snapshotId=' + encodeURIComponent(id) : '')).then(function (s) {
			var t = '산출물 범위: ' + s.summary;
			if (s.tables !== undefined) t += ' — 표 ' + s.snapshotTables + ' → ' + s.tables;
			msg('scopeMsg', t, s.hasFilter && s.tables === 0 ? 'err' : '');
		}, fail('scopeMsg'));
	}
```
`loadSchemas()` 첫 줄 `var id = $('snap').value;` 뒤에 `loadScope(id);` (스냅샷이 없어도 조건은 보인다).
② `logical_name.html` 58 라벨 뒤 `<span class="hint" id="delivScopeMsg"></span>`. 함수

```js
	/* 3-15 — 산출물 범위 체크는 프로필에 deliverable.filter 가 있을 때만 뜻이 있다. 있으면 켠 채로 시작, 없으면 끄고 잠근다(산출물 화면과 같은 글) */
	function loadScope() {
		return TB.api('/api/deliverable/scope').then(function (s) {
			var c = $('delivScope');
			c.disabled = !s.hasFilter;
			c.checked = s.hasFilter;
			$('delivScopeMsg').textContent = s.hasFilter ? '산출물 범위: ' + s.summary : '프로필에 deliverable.filter 없음 — 전부 변환';
		}, function () { return null; });
	}
```
564~573 을 — `if (q.get('scope') === 'deliverable') $('delivScope').checked = true;` 삭제, `loadSnapshots().then(function () {` → `loadScope().then(loadSnapshots).then(function () {` (체크 상태가 정해진 뒤 `run(false)`).
③ 스모크 — `logicalNameOpensFromDeliverable` 프로필에 `"deliverable:\n  filter:\n    exclude: { prefixes: [ZZ_] }\n"` 를 더한다(표 TB_ZZQX 는 ZZ_ 로 시작하지 않아 결과 그대로). 산출물 화면에서 `waitForBackgroundJavaScript(3000)` 뒤 `assertEquals("산출물 범위: 제외 접두 ZZ_ — 표 1 → 1", page.getElementById("scopeMsg").getTextContent())`. 표준 사전 화면 646 단언 옆에 `assertFalse(delivScope.isDisabled())` · `assertEquals("산출물 범위: 제외 접두 ZZ_", ln.getElementById("delivScopeMsg").getTextContent())`. 새 시험 `deliverableScopeLockedWithoutFilter(@TempDir)`: 프로필 `name: t` 만(접속 없음) 서버 → `logical_name.html` 열고 `waitForBackgroundJavaScript(3000)` → `delivScope` `isDisabled()`·`!isChecked()` · `delivScopeMsg` = 「프로필에 deliverable.filter 없음 — 전부 변환」 → `deliverable_sql.html` → `scopeMsg` = 「산출물 범위: 없음(전부)」.
- **검증**: `-Dtest='SmokeHtmlUnitTest#logicalName*,SmokeHtmlUnitTest#deliverable*'` 초록 · `-Dtest='ToolsFolderTest'` 초록(`.hint` 는 공용 클래스, 로컬 색 없음). **닫힘**: ② 의 `c.disabled = !s.hasFilter` 를 빼면 새 시험 빨강 · ① 을 빼면 3-12 스모크의 `scopeMsg` 단언 빨강.
- **사다리**: 공용 `app`(example 프로필, filter 있음) 스모크 중 `delivScope` 가 켜져서 결과가 바뀌는 것이 있으면 — 기능을 바꾸지 않고 그 시험에서 `delivScope.setChecked(false)` 를 먹인다(어느 시험인지 이력에). 둘 넘으면 멈춘다.

### S4 = 6-23a `Scope.reason` · `SnapshotStore.summary(id)` · `Consistency` 사유
- **대상**: `core/meta/Scope.java` · `core/meta/ScopeTest.java` · `core/meta/SnapshotStore.java` · `core/analyze/Consistency.java` · `core/analyze/ConsistencyTest.java`.
- **변경**: ① `Scope`

```java
    /** 이름 규칙만 — 걸린 규칙 한 줄, 안 걸리면 "". 스키마·행 수를 모르는 표(정합성 6-23)에 쓴다 */
    public String reason(String table) {
        String name = up(table);
        if (include != null && !include.tables().isEmpty()) {
            return upper(include.tables()).contains(name) ? "" : "include 목록 밖";
        }
        if (exclude == null) {
            return "";
        }
        for (String p : exclude.prefixes()) {
            if (name.startsWith(up(p))) return "제외 접두 " + p;
        }
        for (String s : exclude.suffixes()) {
            if (name.endsWith(up(s))) return "제외 접미 " + s;
        }
        for (String r : exclude.regex()) {
            if (Pattern.compile(r, Pattern.CASE_INSENSITIVE).matcher(table == null ? "" : table).find()) return "제외 정규식 " + r;
        }
        return upper(exclude.tables()).contains(name) ? "제외 목록" : "";
    }

    /** accepts 와 같은 순서로 전부 — 스키마 → skipEmpty → 이름 규칙 */
    public String reason(Table t) {
        if (!acceptsSchema(t.schema())) {
            return "스키마 " + few(schemas) + " 밖";
        }
        if (Boolean.TRUE.equals(skipEmpty) && t.rowCount() != null && t.rowCount() == 0L) {
            return "빈 표(skipEmpty)";
        }
        return reason(t.name());
    }

    public boolean accepts(Table t) {
        return reason(t).isEmpty(); // 6-23 — 사유와 판정이 한 몸
    }
```
(옛 `accepts` 본문은 지운다. 기존 `ScopeTest` 여덟이 그대로 초록이어야 한다.) `ScopeTest.reasonMatchesAccepts`: 표 묶음(TMP_A·A_BAK·X_20240101·KEEP·users·빈 표)×Scope 셋(include·exclude·schemas+skipEmpty)에서 `accepts(t) == reason(t).isEmpty()` · `reason("TMP_A")` = 「제외 접두 TMP_」 · `reason("A_BAK")` = 「제외 접미 _BAK」 · 정규식 → 「제외 정규식 _\d{8}$」 · 목록 → 「제외 목록」 · include 밖 → 「include 목록 밖」 · 스키마 밖 Table → 「스키마 APP 밖」 · rowCount 0 + skipEmpty → 「빈 표(skipEmpty)」.
② `SnapshotStore`

```java
    /** 스냅샷 하나의 요약(찍을 때 쓴 scope 포함). 없으면 empty */
    public Optional<Summary> summary(long id) throws SQLException {
        return list().stream().filter(s -> s.id() == id).findFirst();
    }
```
③ `Consistency` — `Missing(String table, int programs, String reason)` · `Report(Long snapshotId, String scopeSummary, List<Missing> missingInDb, …)`(둘째 자리) · `of(...)`: `snapshotId == null` 가지는 `new Report(null, null, …)`; 스냅샷 가지에서 `Scope scope = snapshots.summary(snapshotId).map(SnapshotStore.Summary::scope).orElse(null);` → `missing.add(new Missing(t, n, reasonFor(scope, t)))` · `new Report(snapshotId, scope == null ? null : scope.summary(), …)`.

```java
    /** 「없음」 과 「범위 밖」 을 가른다(6-23). 코드 쪽 표는 스키마·행 수를 모르므로 이름 규칙만 확정, 나머지는 「…일 수 있음」 */
    static String reasonFor(Scope scope, String table) {
        if (scope == null) {
            return "없음";
        }
        String r = scope.reason(table);
        if (!r.isEmpty()) {
            return "범위 밖 — " + r;
        }
        if (!scope.schemas().isEmpty()) {
            return "스키마 " + String.join("·", scope.schemas()) + " 밖일 수 있음";
        }
        if (Boolean.TRUE.equals(scope.skipEmpty())) {
            return "빈 표라 빠졌을 수 있음";
        }
        return "없음";
    }
```
javadoc 의 missingInDb 설명에 「(사유 — 범위 밖 / 스키마 밖일 수 있음 / 빈 표 / 없음)」. ④ `ConsistencyTest.report` — 단언을 `new Consistency.Missing("COMVNUSERMASTER", 1, "없음")` 로(옛 꼴 `save(profile, connId, note, schemas)` 는 scope null → 「없음」, `scopeSummary()` null). 새 시험 `reasonFollowsSnapshotScope`: `save(…, schemas, new Scope(List.of("APP"), new Scope.Exclude(List.of("COMVN"), null, null, null), null, true), List.of())` → `COMVNUSERMASTER` 사유 「범위 밖 — 제외 접두 COMVN」 · `scopeSummary()` = 「스키마 APP · 제외 접두 COMVN · 빈 표 제외」; `reasonFor` 직접 — `(new Scope(List.of("APP"), null, null, null), "X")` → 「스키마 APP 밖일 수 있음」 · `(new Scope(null, null, null, true), "X")` → 「빈 표라 빠졌을 수 있음」 · `(Scope.all(), "X")` → 「없음」.
- **검증**: `-Dtest='ScopeTest,ConsistencyTest,SnapshotStoreTest,AnalyzeRoutesTest#consistency'` 초록(라우트는 그대로 — JSON 에 `reason`·`scopeSummary` 가 더 실린다). **닫힘**: `reason(Table)` 의 skipEmpty 가지를 빼면 `reasonMatchesAccepts` 와 기존 `skipEmptyDropsZeroButKeepsUnknown` 둘이 빨강.
- **사다리**: `AnalyzeCorpusTest` 가 Consistency 를 안 쓴다(확인함) — 빨강이면 다른 원인이니 멈춘다.

### S5 = 6-23b 정합성 탭 — 사유 칸 · 범위 줄
- **대상**: `tools/program_analysis.html` · `tools/program_analysis_ext.js` · `web/SmokeHtmlUnitTest.java`.
- **변경**: ① html 정합성 탭 `.bar` 아래(`.grid` 위)에 `<div class="hint" id="conScope"></div>`. 첫 칸 라벨 「코드가 쓰는데 DB 에 없는 표」 그대로. ② ext.js `consistency()` — `TB.table($('conMissing'), ['표', '프로그램 수', '사유'], r.missingInDb.map(function (x) { return [x.table, x.programs, x.reason]; }))` · 스냅샷을 골랐으면 `$('conScope').textContent = '스냅샷 범위: ' + (r.scopeSummary === null || r.scopeSummary === undefined ? '기록 없음(옛 스냅샷)' : r.scopeSummary);` 아니면 `''`. ③ 스모크 `programAnalysisRuns` 정합성 부분에 `assertEquals("", page.getElementById("conScope").getTextContent())`(스냅샷 없이) — 스냅샷 있는 경로는 S10 의 라우트 시험이 JSON 으로 잰다.
- **검증**: `-Dtest='SmokeHtmlUnitTest#programAnalysisRuns,ToolsFolderTest'` 초록. **닫힘**: ② 의 머리 배열에서 「사유」 를 빼면 S10 뒤 xlsx 와 화면 열이 어긋나는 것을 사람이 본다 — 기계 닫힘은 S10 라우트 시험(`reason` 키).

### S6 = 6-22a `Unresolved.KINDS` + `GET /api/analyze/unresolved-kinds`
- **대상**: `core/analyze/Unresolved.java` · `core/analyze/UnresolvedKindsTest.java`(신설) · `web/AnalyzeRoutes.java` · `web/AnalyzeRoutesTest.java`.
- **변경**: ① `Unresolved`

```java
public record Unresolved(String kind, String file, int line, String detail) {

    /** 종류 하나의 글 — 한글 이름 · 뜻 한 줄 · 푸는 법 한 줄(6-22). 화면 칩·xlsx 미해결 시트가 같은 글을 쓴다 */
    public record Kind(String name, String meaning, String fix) {
    }

    /** 영문 코드 → 글. 넣은 순서가 화면 칩 순서. 코드는 H2 analyze_unresolved.kind 와 corpus baseline 이 쥐므로 바꾸지 않는다 */
    public static final Map<String, Kind> KINDS = kinds();

    public Unresolved {
        if (!KINDS.containsKey(kind)) {
            throw new IllegalArgumentException("모르는 미해결 종류: " + kind + " — Unresolved.KINDS 에 이름·뜻·푸는 법을 더한다");
        }
    }

    private static Map<String, Kind> kinds() {
        Map<String, Kind> m = new LinkedHashMap<>();
        m.put("parse", new Kind("파싱 실패", "Java 또는 매퍼 XML 을 파서가 못 읽어 그 파일을 통째로 건너뛰었다(줄은 첫 오류 자리)", "그 파일이 컴파일·XML 검증을 통과하는지 본다"));
        m.put("table", new Kind("표 자리 식별자 아님", "SQL 의 FROM·JOIN·INTO 뒤가 표 이름이 아니라 변수·${}·괄호라 표를 못 읽었다", "동적 표 이름이면 그 문장의 CRUD 는 손으로 적는다"));
        m.put("tagVerb", new Kind("태그와 동사 다름", "매퍼 태그(select 등)와 SQL 첫 동사가 어긋난다 — CRUD 는 SQL 동사를 따랐다", "태그를 SQL 에 맞춘다"));
        m.put("dialect", new Kind("파일마다 다른 표", "같은 ns.id 문장이 여러 매퍼 파일(방언별)에 있는데 쓰는 표가 다르다 — 합집합으로 셌다", "방언 파일끼리 표 목록을 맞춘다"));
        m.put("missing", new Kind("문장·조각 없음", "코드가 부르는 ns.id(또는 include refid)가 매퍼 색인에 없다. 끝 * 는 접두 호출", "매퍼 파일이 분석 폴더 안에 있는지, 네임스페이스·id 오타를 본다"));
        m.put("statement", new Kind("문장 id 못 읽음", "DAO 호출의 문장 id(또는 createQuery 의 JPQL)가 문자열이 아니라 변수·연산이라 어느 문장인지 모른다", "문자열 상수로 바꾸거나 그 메서드의 CRUD 는 손으로 적는다"));
        m.put("prefix", new Kind("접두 호출", "문장 id 가 \"Login.update\" + x 꼴이라 앞부분이 같은 문장을 전부 후보로 넣었다", "실제 쓰는 문장만 남기려면 호출을 상수로 나눈다"));
        m.put("ambiguous", new Kind("후보 여럿", "필드 타입·저장소·엔티티 이름이 여러 클래스에 맞아 하나를 못 골랐다(모듈마다 같은 이름 서비스 등)", "같은 이름 클래스를 패키지로 구분하거나 결과를 손으로 가른다"));
        m.put("depth", new Kind("호출 깊이 상한", "컨트롤러에서 DAO 까지 호출 사슬이 상한을 넘어 그 아래는 안 따라갔다", "사슬이 긴 메서드는 손으로 보탠다"));
        m.put("viewDynamic", new Kind("뷰 이름 동적", "반환하는 뷰 이름이 변수·연산이라 어느 JSP 인지 모른다", "뷰 이름을 문자열로 두거나 그 프로그램의 JSP 는 손으로 잇는다"));
        m.put("jspUrl", new Kind("JSP 링크에 EL", "JSP 링크 URL 에 ${} 가 있거나 / 로 시작하지 않아 어느 프로그램을 부르는지 모른다(${} 자리는 비웠다)", "영향도의 JSP 목록이 덜 나올 수 있다 — 상수 URL 이면 그대로 쓴다"));
        m.put("entityName", new Kind("엔티티 표 이름 추정", "@Table(name) 이 없어 클래스 이름을 snake_case 로 바꿔 표 이름으로 삼았다", "실제 표 이름이 다르면 @Table(name=…) 을 적는다"));
        m.put("jpaType", new Kind("저장소 엔티티 모름", "저장소 인터페이스의 엔티티 타입을 못 읽었다(타입 인자 없음·엔티티 색인에 없음)", "JpaRepository<엔티티, ID> 꼴로 적거나 엔티티가 분석 폴더에 있는지 본다"));
        m.put("jpaMethod", new Kind("저장소 메서드 CRUD 모름", "파생 메서드 이름이 find·save·delete… 규칙에 안 맞아 CRUD 를 못 정했다", "그 메서드의 CRUD 는 손으로 적는다"));
        m.put("jpql", new Kind("JPQL 해석 실패", "@Query·createQuery 의 JPQL 을 못 읽었거나(상수 아님) JPQL 의 엔티티 이름이 색인에 없다", "JPQL 을 상수로 두고 엔티티 이름을 맞춘다"));
        m.put("namedQuery", new Kind("NamedQuery", "createNamedQuery 는 이름만 있어 본문을 안 따라갔다", "그 쿼리의 표는 손으로 적는다"));
        m.put("criteria", new Kind("Criteria API", "getCriteriaBuilder 로 짠 쿼리는 표를 못 읽는다", "그 메서드의 표는 손으로 적는다"));
        m.put("querydsl", new Kind("QueryDSL Q타입 모름", "Q클래스가 가리키는 엔티티를 색인에서 못 찾았다", "Q 클래스 이름과 엔티티 이름을 맞춘다"));
        return Collections.unmodifiableMap(m);
    }
}
```
javadoc 의 종류 목록은 「종류는 KINDS」 한 줄로 바꾼다(두 벌 금지). ② `UnresolvedKindsTest`(core/analyze): (ㄱ) `KINDS.size() == 18`, 값 셋 모두 비어 있지 않음 (ㄴ) `src/main/java/kr/ejg/toolbox/core/analyze/*.java`(Unresolved.java 제외)를 전부 읽어 이어 붙인 글에 키마다 `"<키>"` 가 있다 (ㄷ) 같은 글에서 정규식 다섯 — `note\("(\w+)"` · `new Unresolved\("(\w+)"` · `note\(unresolved, seen, "(\w+)"` · `unresolved\.add\("(\w+)"\)` · `\? "(\w+)" : "(\w+)"` — 로 모은 글자 집합 ⊆ KINDS 키 (ㄹ) `assertThrows(IllegalArgumentException.class, () -> new Unresolved("nope", "f", 1, ""))`. ③ `AnalyzeRoutes` 에 `app.get("/api/analyze/unresolved-kinds", …)` — `Unresolved.KINDS` 순서대로 `LinkedHashMap{kind, name, meaning, fix}` 목록. javadoc 머리 한 줄 「미해결 종류 글(6-22)」. ④ `AnalyzeRoutesTest.unresolvedKinds`: 200 · 18 · 첫 `kind == "parse"` · 모든 항목 `name`·`meaning`·`fix` 비어 있지 않음 · `kind` 집합이 `runAndHistory` 가 보는 여섯을 포함.
- **검증**: `-Dtest='UnresolvedKindsTest,AnalyzeStoreTest,JavaGraphTest,JpaIndexTest,MapperIndexTest,JspLinksTest,SqlTablesTest,AnalyzeRoutesTest'` 초록. 표본 폴더가 있으면 `AnalyzeCorpusTest` 도(baseline 불변 — 글자만 더했다). **닫힘**: 임시로 `JspLinks:67` 의 `"jspUrl"` 을 `"jspUrl2"` 로 바꾸면 `AnalyzeRoutesTest.impact`(생성자 예외)와 `UnresolvedKindsTest`(ㄷ) 둘이 빨강 — 되돌린다.
- **사다리**: 컴파일 뒤 어떤 시험이 「모르는 미해결 종류」 로 빨갛다 = 실측에서 놓친 열아홉째 종류 → KINDS 에 더하고 이력에 「드러난 것」. 둘 넘으면 멈춘다.

### S7 = 6-22b 미해결 탭 — 칩 · 뜻 줄 · 한글 이름
- **대상**: `tools/program_analysis.html` · `tools/program_analysis_ext.js` · `web/SmokeHtmlUnitTest.java`.
- **변경**: ① html 미해결 탭 바 — `<span class="opt">종류 <select id="fKind">…</select></span>` 를 `<div class="bar" id="kindChips"></div>` 로 바꾸고(`#unCount` 는 그 바 안 끝에 그대로), 바 아래 `<div class="hint" id="kindHelp">종류 칩을 누르면 뜻과 푸는 법</div>`. 로컬 style `#kindChips .t { cursor: pointer; }`. ② ext.js — `var kinds = {};`(코드 → {name, meaning, fix}) · `var kindSel = '';` · `init()` 에서 `TB.api('/api/analyze/unresolved-kinds').then(function (l) { l.forEach(function (k) { kinds[k.kind] = k; }); }, function () {});` · `kindOptions()` → `renderChips()`:

```js
  function kindName(k) { return kinds[k] ? kinds[k].name : k; }

  // 6-22 — 종류 칩. 순서는 KINDS(API 순서)를 따르고 이 실행에 있는 것만. title 은 영문 코드
  function renderChips() {
    var count = {};
    unresolved.forEach(function (u) { count[u.kind] = (count[u.kind] || 0) + 1; });
    if (!count[kindSel]) kindSel = '';
    var box = $('kindChips');
    box.innerHTML = '';
    var order = Object.keys(kinds).filter(function (k) { return count[k]; })
      .concat(Object.keys(count).filter(function (k) { return !kinds[k]; }).sort());
    [['', '전체', unresolved.length]].concat(order.map(function (k) { return [k, kindName(k), count[k]]; })).forEach(function (c) {
      var el = document.createElement('span');
      el.className = c[0] === kindSel ? 't on' : 't';
      el.textContent = c[1] + ' ' + c[2];
      el.title = c[0] || '전체';
      el.onclick = function () { kindSel = c[0]; renderChips(); renderUnresolved(); };
      box.appendChild(el);
    });
    var un = $('unCount');
    box.appendChild(un);
    var k = kinds[kindSel];
    $('kindHelp').textContent = kindSel ? kindName(kindSel) + ' — ' + (k ? k.meaning + '. 푸는 법: ' + k.fix : '뜻 없음') : '종류 칩을 누르면 뜻과 푸는 법';
  }

  function renderUnresolved() {
    var list = unresolved.filter(function (u) { return !kindSel || u.kind === kindSel; });
    var t = TB.table($('unresolved'), ['종류', '파일', '줄', '식별자'], list.map(function (u) { return [kindName(u.kind), u.file, u.line, u.detail || '']; }));
    var trs = t.tBodies[0].rows;
    for (var i = 0; i < trs.length; i++) trs[i].cells[0].title = list[i].kind;
    $('unCount').textContent = list.length + ' / ' + unresolved.length;
  }
```
`init()` 의 `$('fKind').onchange = renderUnresolved;` 삭제. `load()` 의 `kindOptions();` → `renderChips();`. (`TB.table` 이 table 요소를 돌려준다 — `renderPrograms` 가 이미 `t.tBodies[0].rows` 를 쓴다.) `#unCount` 는 html 에서 `#kindChips` 밖 바에 두면 `renderChips` 가 `innerHTML=''` 로 지우므로 — html 에서 `#unCount` 를 `#kindChips` **밖**(바 끝)에 두고 `box.appendChild(un)` 두 줄은 빼는 것이 단순하다: 바 `<div class="bar"><div id="kindChips" class="bar"></div><span class="count" id="unCount"></span></div>` 로 하고 `var un…appendChild` 두 줄은 쓰지 않는다. ③ 스모크 `programAnalysisRuns` 미해결 부분 — `#kindChips .t` 수 ≥ 2 · 첫 칩 글 `startsWith("전체 ")` · 둘째 칩 `title` 이 `[a-zA-Z]+` 이고 글이 한글(`[가-힣]`)로 시작 · 둘째 칩 클릭 → `#kindHelp` 글에 「푸는 법: 」 · `#unresolved tbody tr` 수 == 칩의 수 · 첫 행 첫 칸 `title` == 칩 `title`.
- **검증**: `-Dtest='SmokeHtmlUnitTest#programAnalysisRuns,ToolsFolderTest'` 초록. **닫힘**: `renderUnresolved` 의 `kindName` 을 `u.kind` 로 되돌리면 「한글로 시작」 단언 빨강.
- **사다리**: HtmlUnit 에서 `span.onclick` 이 안 먹으면 `HtmlElement.click()` 대신 `js(page, "document.querySelectorAll('#kindChips .t')[1].click()")` 를 쓴다(기존 `js()` 헬퍼).

### S8 = 6-21a `CrudViews` + `/crud` 응답
- **대상**: `core/analyze/CrudViews.java`(신설) · `core/analyze/CrudViewsTest.java`(신설) · `web/AnalyzeRoutes.java` · `web/AnalyzeRoutesTest.java`.
- **변경**: ① `CrudViews`

```java
/**
 * CRUD 보기 둘(6-21) — 세로 목록(프로그램·표 한 쌍이 한 줄)과 모듈 매트릭스(행 = 표, 열 = 모듈, 칸 = 그 모듈 프로그램들의 CRUD 합집합).
 * 넓은 격자(열 = 표 전부)는 표 1,000 개면 열 1,000 개라 걷었다. 계산은 여기 한 곳 — 화면·xlsx 가 같은 값을 쓴다. 식별자만(규칙 3)
 */
public final class CrudViews {

    public static final String ROOT = "/";
    private static final String ORDER = "CRUD";

    public record LongRow(String program, String url, String module, String table, String crud) {
    }

    /** 표 하나의 행 — cells 는 모듈 → 합집합 글자(C→R→U→D 순), 있는 모듈만 */
    public record ModuleRow(String table, Map<String, String> cells) {
        public ModuleRow {
            cells = Collections.unmodifiableMap(new TreeMap<>(cells));
        }
    }

    public record ModuleMatrix(List<String> modules, List<ModuleRow> rows) {
        public ModuleMatrix {
            modules = List.copyOf(modules);
            rows = List.copyOf(rows);
        }
    }

    private CrudViews() {
    }

    /** URL 앞 두 마디 — 마지막 마디는 파일이라 뺀다. /sec/gmt/EgovGroupList.do → sec/gmt · /bbs/list.do → bbs · /other → / · 빈 값 → / */
    public static String module(String url) {
        if (url == null) {
            return ROOT;
        }
        String u = url.trim();
        int q = u.indexOf('?');
        if (q >= 0) {
            u = u.substring(0, q);
        }
        List<String> parts = Arrays.stream(u.split("/")).filter(s -> !s.isEmpty()).toList();
        if (parts.size() <= 1) {
            return ROOT;
        }
        return parts.size() == 2 ? parts.get(0) : parts.get(0) + "/" + parts.get(1);
    }

    /** 프로그램·파일·줄 순(rows 순서) × 표 이름 순(crud 가 TreeMap) */
    public static List<LongRow> longRows(List<AnalyzeStore.ProgramRow> rows) {
        List<LongRow> out = new ArrayList<>();
        for (AnalyzeStore.ProgramRow r : rows) {
            String url = r.url() + (r.params() == null || r.params().isEmpty() ? "" : " " + r.params());
            String module = module(r.url());
            r.crud().forEach((t, c) -> out.add(new LongRow(r.className() + "." + r.method(), url, module, t, c)));
        }
        return out;
    }

    public static ModuleMatrix moduleMatrix(List<AnalyzeStore.ProgramRow> rows) {
        TreeMap<String, TreeMap<String, String>> cells = new TreeMap<>();
        TreeSet<String> modules = new TreeSet<>();
        for (LongRow l : longRows(rows)) {
            modules.add(l.module());
            cells.computeIfAbsent(l.table(), k -> new TreeMap<>()).merge(l.module(), l.crud(), CrudViews::union);
        }
        List<ModuleRow> out = new ArrayList<>();
        cells.forEach((t, m) -> out.add(new ModuleRow(t, m)));
        return new ModuleMatrix(new ArrayList<>(modules), out);
    }

    /** 글자 합집합 — 순서는 데이터가 아니라 ORDER 가 정한다(6-18 과 같은 결) */
    static String union(String a, String b) {
        StringBuilder sb = new StringBuilder();
        for (char c : ORDER.toCharArray()) {
            if (a.indexOf(c) >= 0 || b.indexOf(c) >= 0) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
```
② `CrudViewsTest` — `module`: `/sec/gmt/EgovGroupList.do`→`sec/gmt` · `/bbs/list.do`→`bbs` · `/a/b/c/d.do`→`a/b` · `/other`→`/` · `/x/y.do?z=1`→`x` · `null`·`""`·`"/"`→`/`. `ProgramRow` 둘(`new AnalyzeStore.ProgramRow(1, "A", "m1", "A.java", 1, "GET", "/bbs/list.do", "", "view", "", List.of(), List.of(), Map.of("COMTNBBS", "R", "T2", "C"))` · `/bbs/add.do` crud `COMTNBBS → "UC"`... 글자는 저장 꼴대로 `"CU"`) · 셋째 `/other` `T2 → "D"`: `longRows` 수 4 · 순서(첫 행 `A.m1 … COMTNBBS R`) · `moduleMatrix.modules == ["/", "bbs"]` · `COMTNBBS` 행 `cells == {bbs: "CRU"}` · `T2` 행 `cells == {"/": "D", bbs: "C"}` · `union("UR", "C") == "CRU"` · `union("", "") == ""`.
③ `AnalyzeRoutes` — `record CrudResponse(List<String> tables, List<AnalyzeStore.ProgramRow> rows, List<CrudViews.LongRow> longRows, CrudViews.ModuleMatrix moduleMatrix) {}` · `/crud` 라우트 `AnalyzeStore.Matrix m = store.crud(id); ctx.json(new CrudResponse(m.tables(), m.rows(), CrudViews.longRows(m.rows()), CrudViews.moduleMatrix(m.rows())));`. 클래스 javadoc 「CRUD 는 세로 목록·모듈 매트릭스(6-21)」. ④ `AnalyzeRoutesTest.runAndHistory` 에 — `crud.get("longRows").size()` == 행들의 `crud` 항목 수 합 · `crud.get("moduleMatrix").get("modules")` 글에 `"bbs"` 와 `"/"` · `moduleMatrix.rows` 중 `table == "COMTNBBS"` 의 `cells.bbs` 에 `R`.
- **검증**: `-Dtest='CrudViewsTest,AnalyzeRoutesTest,DeliverableRoutesTest'` 초록(18 문서는 `store.crud` 그대로). **닫힘**: `union` 의 ORDER 를 `"RCUD"` 로 바꾸면 CrudViewsTest 빨강.

### S9 = 6-21b CRUD 탭 — 모듈 매트릭스 · 세로 목록 · 칸 상세
- **대상**: `tools/program_analysis.html` · `tools/program_analysis_ext.js` · `web/SmokeHtmlUnitTest.java`.
- **변경**: ① html CRUD 탭 바 — 맨 앞에 `<span class="t on" id="crudModeModule">모듈 매트릭스</span><span class="t" id="crudModeList">세로 목록</span>`, `#fTable`·`#fProg` 유지, `<label class="opt"><input type="checkbox" id="allRows"> CRUD 없는 프로그램도</label>` 삭제(목록은 쌍이라 뜻이 없다). `#crud` 뒤에 `<div id="crudDetail" class="well">모듈 칸을 누르면 그 모듈·표의 프로그램 목록</div>`. style — `#crud { flex: 3; }` · `#crudDetail { flex: 1; min-height: 80px; overflow: auto; padding: 8px 10px; white-space: pre; line-height: 1.5; color: var(--code); }` · `#crud td.c { cursor: pointer; }`(모듈 보기만 — 목록은 `.c` 를 안 쓴다 → 목록 CRUD 칸은 클래스 `v v-C` 로 색만: `#crud .v-C { color: var(--teal); font-weight: bold; }` 등 넷을 `.c-*` 옆에 `#crud .c-C, #crud .v-C { … }` 로 합쳐 둔다). 탭 글 「CRUD 매트릭스」 는 그대로(id `tabCrud`). ② ext.js — `var crudMode = 'module';` · `init()`: `$('crudModeModule').onclick = function () { crudMode = 'module'; renderCrud(); }; $('crudModeList').onclick = function () { crudMode = 'list'; renderCrud(); };` · `$('allRows').onchange` 줄 삭제. `renderCrud()`:

```js
  // 6-21 — 넓은 격자(열 = 표 전부)를 걷었다. 모듈 매트릭스(기본)와 세로 목록. 합집합은 서버(CrudViews)가 센다 — 여기서는 안 센다
  function renderCrud() {
    var module = crudMode === 'module';
    $('crudModeModule').className = module ? 't on' : 't';
    $('crudModeList').className = module ? 't' : 't on';
    $('fProg').disabled = module;
    $('fProg').placeholder = module ? '세로 목록에서' : '클래스·메서드·URL';
    var box = $('crud');
    box.innerHTML = '';
    var legend = document.createElement('div');
    legend.className = 'count';
    legend.id = 'crudLegend';
    legend.textContent = 'C=Create · R=Read · U=Update · D=Delete';
    box.appendChild(legend);
    box.appendChild(module ? moduleTable() : listTable());
  }

  function moduleTable() {
    var ft = $('fTable').value.trim().toUpperCase();
    var mm = matrix.moduleMatrix || { modules: [], rows: [] };
    var rows = mm.rows.filter(function (r) { return !ft || r.table.toUpperCase().indexOf(ft) >= 0; });
    var table = document.createElement('table');
    var thead = document.createElement('thead');
    var hr = document.createElement('tr');
    hr.appendChild(cell('th', '표', 'head'));
    mm.modules.forEach(function (m) { hr.appendChild(cell('th', m)); });
    thead.appendChild(hr);
    table.appendChild(thead);
    var tbody = document.createElement('tbody');
    rows.forEach(function (r) {
      var tr = document.createElement('tr');
      tr.appendChild(cell('td', r.table, 'head'));
      mm.modules.forEach(function (m) {
        var v = r.cells[m];
        var td = cell('td', v || '', v ? 'c c-' + v.charAt(0) : 'c');
        if (v) td.onclick = function () { crudDetail(m, r.table); };
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    $('crudCount').textContent = '표 ' + rows.length + ' / ' + mm.rows.length + ' · 모듈 ' + mm.modules.length;
    return table;
  }

  function listTable() {
    var ft = $('fTable').value.trim().toUpperCase();
    var fp = low($('fProg').value.trim());
    var all = matrix.longRows || [];
    var rows = all.filter(function (p) {
      if (ft && p.table.toUpperCase().indexOf(ft) < 0) return false;
      return !fp || (low(p.program) + ' ' + low(p.url)).indexOf(fp) >= 0;
    });
    var table = document.createElement('table');
    var thead = document.createElement('thead');
    var hr = document.createElement('tr');
    ['프로그램', 'URL', '모듈', '표', 'CRUD'].forEach(function (h) { hr.appendChild(cell('th', h)); });
    thead.appendChild(hr);
    table.appendChild(thead);
    var tbody = document.createElement('tbody');
    rows.forEach(function (p) {
      var tr = document.createElement('tr');
      tr.appendChild(cell('td', p.program));
      tr.appendChild(cell('td', p.url));
      tr.appendChild(cell('td', p.module));
      tr.appendChild(cell('td', p.table));
      tr.appendChild(cell('td', p.crud, 'v v-' + p.crud.charAt(0)));
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    $('crudCount').textContent = '쌍 ' + rows.length + ' / ' + all.length;
    return table;
  }

  function crudDetail(module, table) {
    var ps = (matrix.longRows || []).filter(function (p) { return p.module === module && p.table === table; });
    var lines = [module + ' · ' + table + ' — 프로그램 ' + ps.length, ''];
    ps.forEach(function (p) { lines.push('  ' + p.program + '  ' + p.url + '  ' + crudWords(p.crud)); });
    $('crudDetail').textContent = lines.join('\n');
  }
```
`load()` 의 `$('detail').textContent = …` 옆에 `$('crudDetail').textContent = '모듈 칸을 누르면 그 모듈·표의 프로그램 목록';`. 옛 `renderCrud` 본문은 지운다. ③ 스모크 `programAnalysisRuns` CRUD 부분 — 기존 셋(범례 글 · `th >= 3` · COMTNBBS) 유지 + `assertTrue(((HtmlTextInput) page.getElementById("fProg")).isDisabled())` · 첫 th 글 「표」 · `#crud thead th` 글 묶음에 `bbs` · `#crud td.c-R`(또는 글 있는 `td.c`) 하나 클릭 → `#crudDetail` 글이 「bbs · COMTNBBS — 프로그램 」 또는 「/ · 」 로 시작하고 「Read」 포함 · `#crudModeList` 클릭 → `#crud thead th` 수 5 · 첫 th 「프로그램」 · `fProg` 활성 · `#crudCount` `startsWith("쌍 ")` · `#allRows` 가 null.
- **검증**: `-Dtest='SmokeHtmlUnitTest#programAnalysisRuns,ToolsFolderTest'` 초록(`toolsHaveNoLocalTheme` — 새 색은 `var(--teal)` 등 토큰만). **닫힘**: `renderCrud` 가 `fProg` 를 잠그는 줄을 빼면 스모크 빨강.
- **사다리**: `td.onclick` 이 HtmlUnit 에서 안 먹으면 S7 사다리와 같게 `js()` 로 클릭.

### S10 = 6-24 xlsx 한 파일 시트 다섯
- **대상**: `web/AnalyzeRoutes.java` · `web/Outputs.java` · `web/DeliverableRoutes.java` · `web/AnalyzeRoutesTest.java` · `tools/program_analysis_ext.js`(알림·`snapshotId` 한 줄).
- **변경**: ① `Outputs` 에 `static Path xlsx(Path dir, String name, java.util.LinkedHashMap<String, ResultTable> sheets)`(`XlsxWriter.write(sheets, file)`) · `static ResultTable table(List<ResultTable.Col> cols, List<List<Object>> rows)` = `new ResultTable(cols, rows, false, -1, 0)`. ② `AnalyzeRoutes` — `record ExportRequest(String format, Long snapshotId)` · `MAX_TABLES` 삭제 · `/export`:

```java
        // 6-24 xlsx 한 파일 — 시트 프로그램목록 · CRUD목록 · CRUD모듈 · 미해결 · 정합성(snapshotId 가 있을 때만). 칸은 식별자·글자뿐(규칙 3)
        app.post("/api/analyze/runs/{id}/export", ctx -> {
            … format 검사 그대로 …
            Long id = runId(ctx, store);
            if (id == null) return;
            AnalyzeStore.Matrix m = store.crud(id);
            Optional<Consistency.Report> con = Optional.empty();
            if (req.snapshotId() != null) {
                con = Consistency.of(store, snapshots, id, req.snapshotId());
                if (con.isEmpty()) { ctx.status(404).json(Map.of("message", "스냅샷이 없다")); return; }
            }
            java.util.LinkedHashMap<String, ResultTable> sheets = new java.util.LinkedHashMap<>();
            sheets.put("프로그램목록", Outputs.table(List.of(text("클래스"), …9열 지금 그대로), programs));
            List<CrudViews.LongRow> longs = CrudViews.longRows(m.rows());
            sheets.put("CRUD목록", Outputs.table(List.of(text("프로그램"), text("URL"), text("모듈"), text("표"), text("CRUD")),
                    longs.stream().map(l -> Arrays.<Object>asList(l.program(), l.url(), l.module(), l.table(), l.crud())).toList()));
            CrudViews.ModuleMatrix mm = CrudViews.moduleMatrix(m.rows());
            List<ResultTable.Col> mcols = new ArrayList<>(List.of(text("표")));
            mm.modules().forEach(x -> mcols.add(text(x)));
            sheets.put("CRUD모듈", Outputs.table(mcols, mm.rows().stream().map(r -> { List<Object> row = new ArrayList<>(); row.add(r.table());
                    mm.modules().forEach(x -> row.add(r.cells().getOrDefault(x, ""))); return row; }).toList()));
            sheets.put("미해결", Outputs.table(List.of(text("종류"), text("이름"), text("뜻"), text("파일"), num("줄"), text("식별자")),
                    store.unresolved(id).stream().map(u -> { Unresolved.Kind k = Unresolved.KINDS.get(u.kind());
                    return Arrays.<Object>asList(u.kind(), k.name(), k.meaning(), u.file(), u.line(), u.detail()); }).toList()));
            if (con.isPresent()) {
                Consistency.Report r = con.get();
                List<List<Object>> rows = new ArrayList<>();
                r.missingInDb().forEach(x -> rows.add(Arrays.asList("DB 에 없는 표", x.table(), "", "", x.programs(), x.reason())));
                r.unusedInCode().forEach(x -> rows.add(Arrays.asList("안 쓰는 표", x.table(), x.schema(), x.type(), "", "")));
                r.deadStatements().forEach(x -> rows.add(Arrays.asList("안 불리는 문장", x, "", "", "", "")));
                r.orphanJsps().forEach(x -> rows.add(Arrays.asList("고아 JSP", x, "", "", "", "")));
                sheets.put("정합성", Outputs.table(List.of(text("구분"), text("이름"), text("스키마"), text("종류"), text("프로그램 수"), text("사유")), rows));
            }
            Path dir = Outputs.dir(active.get().orElse(null));
            Path f = Outputs.xlsx(dir, "프로그램분석-" + id + ".xlsx", sheets);
            List<Map<String, Object>> sheetInfo = new ArrayList<>();
            sheets.forEach((n, t) -> sheetInfo.add(Map.of("name", n, "rows", t.rows().size())));
            ctx.json(Map.of("dir", dir.toString(), "files", List.of(Map.of("name", f.getFileName().toString(), "path", f.toString(), "rows", programs.size())),
                    "sheets", sheetInfo));
        });
```
(「프로그램 수」 열은 섞인 값이라 `text`. 정합성 시트에 `scopeSummary` 는 넣지 않는다 — 화면 줄과 사유 열이 담는다.) 클래스 javadoc 「내려받기는 xlsx 하나 — 시트 다섯(6-24)」. ③ `DeliverableRoutes` — `static final int MAX_TABLES_18 = 16_000;`(javadoc 「Excel 열 상한 16,384 — 18 문서의 표 열. 넓은 격자는 18 만 남았다(6-24)」) 로 바꾸고 117 의 `AnalyzeRoutes.MAX_TABLES` → `MAX_TABLES_18`. ④ ext.js `xlsx()` — `var body = { format: 'xlsx' }; if ($('conSnap').value) body.snapshotId = Number($('conSnap').value);` → `msg('xlsx ' + TB.savedText([r.files[0].path], r.dir) + ' · 시트 ' + r.sheets.map(function (s) { return s.name + ' ' + s.rows; }).join(' · '), 'ok')`. `#xlsx` 버튼 `title`(html 한 줄) 「시트 — 프로그램목록·CRUD목록·CRUD모듈·미해결, 정합성 탭에서 스냅샷을 골랐으면 정합성도」. ⑤ `AnalyzeRoutesTest` — `t.yaml` 에 `connections:\n  - id: h2\n    dialect: h2\n    url: jdbc:h2:mem:analyzeroutes;DB_CLOSE_DELAY=-1\n    user: sa\n` 추가. `exportXlsx` 다시 쓰기: `DriverManager.getConnection("jdbc:h2:mem:analyzeroutes;DB_CLOSE_DELAY=-1", "sa", "pw")` 로 `CREATE TABLE COMTNBBS (ID INT)` → `POST /api/conn/h2/password {"password":"pw"}` → `POST /api/meta/snapshot {"connId":"h2"}` → `/api/meta/snapshots` 100×100ms 폴링으로 id(스모크 599~620 꼴) → (ㄱ) `export {}` → 200 · `files` 1 · 이름 `프로그램분석-<id>.xlsx` · `sheets` 이름 순서 `[프로그램목록, CRUD목록, CRUD모듈, 미해결]` · POI 로 열어 시트 수 4 · 시트0 머리 「클래스」…「설명」(6-18 단언 그대로) 13행 · 시트1 머리 `프로그램 URL 모듈 표 CRUD`, 행 수 == `GET …/crud` 의 `longRows.size()` · 시트2 머리에 「표」·「bbs」 · 시트3 머리 `종류 이름 뜻 파일 줄 식별자`, 1행 「이름」 칸이 한글 (ㄴ) `export {snapshotId}` → 시트 5, 넷째 뒤 「정합성」, 그 시트에 구분 「DB 에 없는 표」·이름 `COMVNUSERMASTER`·사유 「없음」(프로필 scope 없음 → `Scope.all()` 저장 → 규칙 없음) 행과 「안 불리는 문장」 `Board.unusedOne` 행 (ㄷ) `export {snapshotId: 999}` 404 · `{format: "hwp"}` 400 · `/runs/999/export` 404. `consistency` 시험에도 `?snapshotId=<id>` 한 번 — `scopeSummary == "없음(전부)"` · `missingInDb` 마다 `reason` 키.
- **검증**: `-Dtest='AnalyzeRoutesTest,DeliverableRoutesTest,SmokeHtmlUnitTest#programAnalysisRuns'` 초록(스모크의 `startsWith("xlsx")` 그대로). **닫힘**: `sheets.put("정합성", …)` 을 조건 밖으로 빼면 (ㄱ) 시트 수 4 단언 빨강.
- **사다리**: H2 mem 접속이 `password` 없이 열리면(`"pw"` 가 안 맞으면) 스모크 3-12 와 같은 꼴(`sa`/`pw`)인지 비교. 프로필 `connections` 때문에 다른 AnalyzeRoutesTest 시험이 바뀌면 그 시험은 접속을 안 쓴다 — 원인은 다른 데.

### S11 마무리
CLAUDE.md 「마무리」 여덟 그대로 — 독립 리뷰 → `gate-probe.sh`(패치 갱신이 뜨면 다시 떠서 커밋) → `verify.sh --full`(데모 서버 끔 · 메모리 확인) → push → PR(본문에 청크마다 시작 세 줄 표) → CI 폴링 → 머지 위임 없음 — PR 열고 멈춘다 → 끝 보고. `node scripts/puppeteer/visual.js bundle33` 한 번(프로그램 분석 CRUD·미해결 탭이 바뀌었다) — 집 검증, 결과는 이력에 한 줄.

## 5. 금지 사항

- `pure/` · `sql_snippets.html` · `profiles/demo.yaml` 커밋 · 새 의존성 · `AnalyzeStore` 스키마·저장 값(crud 글자·kind 코드) · `golden/corpus/analyze-*`(kind 글자 그대로여야 한다) · `golden/deliverable/*` · `DeliverableService.Source` 의 범위 글(00_작성안내 요약) · `Deliverables.filter` 본문 · `JavaGraph`·`JpaIndex`·`MapperIndex`·`SqlTables`·`JspLinks` 본문(S6 닫힘 확인의 임시 변경은 되돌린다).
- Unresolved **kind 영문 코드 변경·삭제 금지**. KINDS 밖 종류를 `note` 로 더하지 않는다(더하면 KINDS 에 글 셋도 같이).
- 화면 JS 가 CRUD 합집합·범위 글·미해결 뜻을 만들지 않는다(서버 글만 보인다). `innerHTML` 은 비우기만. ES5(HtmlUnit).
- `program_analysis.html` 로컬 `<style>` 에 `:root`·`button{}`·`.btn-*`·`.dl`·토큰 밖 고정 색 금지(`ToolsFolderTest.toolsHaveNoLocalTheme`).
- 공용 `app`(example 프로필) 스모크의 결과를 맞추려고 `profiles/example.yaml` 의 filter 를 바꾸지 않는다(S3 사다리는 시험 쪽 체크 해제만).
- 시험을 고쳐 통과시키지 않는다 — 고쳐도 되는 것은 이 문서가 이름을 댄 것(`ConsistencyTest` 3인자 · `exportXlsx` 다시 쓰기 · `ToolsFolderTest` 233~235 삭제 · `logicalNameOpensFromDeliverable` 프로필 filter · `programAnalysisRuns` CRUD·미해결·정합성 단언)뿐.
- `scripts/verify*.sh`·훅·CI 를 고치지 않는다(프로젝트 기간 규칙).

## 6. 최종 검증

- 청크마다 `bash scripts/verify.sh` 초록 뒤 커밋(따로). 번들 끝 `--full` 초록 · gate-probe 여덟 · Puppeteer `smoke-devtools.js` 통과 · `visual.js bundle33` 콘솔 오류 0.
- 회귀 — `SmokeHtmlUnitTest` 전부 · `AnalyzeCorpusTest`(표본 있을 때) baseline 불변 · `golden/*` 불변 · `DeliverableRoutesTest`(18 문서 그대로) · `LogicalRoutesTest`.
- 수동(사람 — 실브라우저, `run.bat --profile demo`): dev_tools 주석 삭제 칸 아래 폴더 일괄이 없다 · 산출물 화면 첫 줄에 「산출물 범위: … — 표 m → n」, 스냅샷 바꾸면 수가 바뀐다 · 표준 사전 화면 체크가 켜져 있고 옆에 같은 글(데모 프로필에 filter 가 없으면 잠겨 있고 「없음」) · 프로그램 분석 CRUD 탭 — 열이 모듈 수, 칸 클릭에 아래 목록, 「세로 목록」 전환 · 미해결 탭 칩 한글·뜻 줄 · 정합성에 스냅샷 골라 「사유」 열과 「스냅샷 범위:」 줄 · xlsx 한 파일 시트 다섯을 엑셀로 연다.

## 7. 중단 조건

멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- 2장의 함수·줄이 실제와 다르다(같은 이름으로 다시 찾되 없으면 멈춘다).
- 검증이 2회 연속 실패, 원인이 그 스텝 밖.
- 5장을 어기지 않고는 못 간다 · 스텝에 없는 파일을 3개 이상 고쳐야 한다.
- S6 에서 KINDS 에 더해야 하는 종류가 둘을 넘는다(실측이 틀렸다) · S2 에서 `SnapshotStoreTest` 왕복이 `@JsonIgnore` 로도 안 풀린다 · S10 에서 H2 스냅샷이 10초 안에 안 생긴다 · 어느 스텝에서든 `golden/*`·corpus baseline 이 바뀐다.

## 8. 불확실 항목 — 설계 세션이 푼 것과 남긴 것

- **푼 것**: `Scope.all()` 이 새 스냅샷에 저장된다(`SnapshotService:47`) → S10 의 `scopeSummary == "없음(전부)"` · 사유 「없음」 단언은 확정. `summary()`·`reason()` 이 Jackson 직렬화에 안 들어가는 것은 record 규칙(get/is 접두만) — S2 사다리에 `@JsonIgnore` 를 두었다. `AnalyzeCorpusTest` 는 kind 글자를 baseline 에 그대로 적으므로 글만 더하는 S6 는 불변. `TB.table` 이 table 요소를 돌려준다(`renderPrograms` 가 쓴다). 픽스처 모듈은 `bbs`·`/` 둘 → 기존 `th >= 3` 단언 유지. `example.yaml` filter 는 `isFiltered` 참 → 공용 `app` 스모크에서 `delivScope` 가 켜진다(S3 사다리). `TB_CMT` 사용처는 StripCorpusTest·corpus-strip.js·dump-strip.js 셋 — 내보내기는 남긴다. `CHUNK`·`SB` 는 폴더 일괄 절 밖에서 안 쓴다, `joinPath`·`head`·`cell` 은 폴더 비교가 쓴다.
- **실행자가 첫 스텝 전에 확인할 것**: `SnapshotStoreTest` 에 scope JSON 왕복 단언이 있는지(S2 사다리 발동 여부) · `SmokeHtmlUnitTest` 에서 `app`(example) 으로 `logical_name.html` 을 여는 시험 셋(102·126·153)이 스냅샷 변환을 하는지 — 하면 `delivScope` 가 켜져 결과가 달라질 수 있다(S3 사다리).
- **남긴 것(행 밖)**: 산출물 18 문서는 넓은 격자 그대로(NIA 서식) — 열이 표 수라 1,000 표면 1,000 열. 사용자가 18 도 모듈로 접고 싶으면 새 행. · `SnapshotStore.summary(id)` 가 `list()` 전체를 훑는다 — 스냅샷 수가 수백이어도 ms 단위, 최적화 안 함.
- **6-20 열린 질문(사용자 답 뒤 설계)**: ① 첫 판 범위 — 메뉴 ↔ 프로그램 잇기만(컬럼 단위 SQL·JSP 입력칸·hidden·팝업 판정 없이)으로 시작해도 되는가 ② 개인정보 사전은 프로필 YAML 의 컬럼 이름 정규식 목록(주민번호·전화·이메일·계좌·주소…)으로 시작하는가, 아니면 표준 사전 도메인에서 끌어오는가 ③ 결과 형식 — dcare 5장 열 그대로인가, 범용 열(메뉴 근거·분석 신뢰도)을 더하는가 ④ 메뉴 원천 — 프로필 `menu:` 역할 매핑(초안)으로 가는가, 첫 판은 egov `COMTNMENUINFO`+`COMTNPROGRMLIST` 고정으로 가는가.
