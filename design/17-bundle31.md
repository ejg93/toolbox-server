# 설계 17 — 번들 31: 데모에서 본 결함·요구 (Fable, 2026-10-07)

불확실 항목은 설계 세션이 실측으로 다 풀었다(8장 — H2·Oracle COUNT·POI 표시 형식·숨은 탭 연결 해제·코드 검사 속도). 실행 담당(Opus)은 이 문서와 PLAN 행만 보고 친다. 행 0-50·5-21·5-22·5-23·3-10·3-11·1-47·2-21·2-22·2-23·3-12 의 원본은 4장 S1~S11 이다. 번들 모드 규칙(CLAUDE.md 「번들 모드」) 그대로.

사용자가 2026-10-07 정한 것(뒤집지 않는다):
- 배지 연결 — 안 보이는 탭은 끊는다 · 코드 검사 폴더 검사에 중지.
- 첫째 줄 지적 — demo 프로필 줄바꿈 LF · 주석만 든 스크립틀릿은 안 셈 · 파일 단위 지적은 줄 칸에 「파일」.
- 「논리명 변환기」 → **「표준 사전 · 논리명」**(파일명 `logical_name.html` 그대로) · 그 화면 후보 버튼 번호를 산출물 번호(05 표준단어·06 표준도메인·07 표준용어)에 맞춘다.
- 02 테이블 볼륨 — 단위 「건」(행안부 별표4: 「현재 보유 저장량을 **건수**로」) · 통계가 없는 표만 스냅샷 때 `COUNT(*)` · 못 센 표는 0 이 아니라 「통계 없음」.
- 05·07 ↔ 「표준 사전 · 논리명」 연결은 Fable 이 설계해 번들 끝에(2-22·2-23·3-12) · 미등록 약어는 **00_작성안내 새 시트 「미등록 약어」** 에(05 행으로 안 싣는다).
- 머지 위임 없음 — PR 열고 멈춘다.

## 1. 목표와 범위

- **목표**: 탭이 여럿이어도 중지가 바로 먹고, 코드 검사도 중지되며, 코드 검사 1줄 몰림이 사라지고, 「논리명 변환기」 가 무엇을 하는 화면인지 이름에서 보이며, 02 테이블 정의서의 볼륨이 「1,234건」 으로 채워지고, 산출물을 만들면 05·07 이 얼마나 찼는지(사전에 없는 조각 몇 개)가 수로 보이고 그 조각을 채우러 가는 길(00_작성안내 「미등록 약어」 시트 + 화면 링크)이 있다.
- **In**: 행 `0-50` → `5-21` → `5-22` → `5-23` → `3-10` → `3-11` → `1-47` → `2-21` → `2-22` → `2-23` → `3-12` · `profiles/demo.yaml` `lineEnding: LF`(gitignore, 커밋 없음) · 번들 30 AI 리뷰 잔손 둘(S0) · 마무리(리뷰·gate-probe·`--full`·push·PR·CI·AI 리뷰 처분, 머지 안 함).
- **Out**: 탭끼리 연결 공유 · 배지 주기 ping · 작업 진행 SSE → 폴링 · 줄 번호 0 저장(스키마) · `JspLinks` 주석 처리 · 통계가 있는 표도 COUNT · 0건으로 채우기 · 파일명 `logical_name.html`·API 경로 `/api/logical/*`·후보 CSV 파일명 바꾸기 · 05 에 미등록 약어 행 싣기 · 논리명 화면에 표 고르기 UI · 산출물 화면에서 사전 편집 · 사용자 사전 건수 상한(`DictStore.words` LIMIT 200 — 드러난 것으로만) · `pure/` · 새 의존성.

## 2. 현재 구조 요약

### 2.1 원인 실측(2026-10-07, 데모 서버 41790)

- **중지**: 브라우저는 한 호스트에 HTTP/1.1 연결 6개까지. `common.js` `watch()`(89~101)가 탭마다 `EventSource('/api/alive')` 로 하나를 쥐고, 스냅샷 진행 `TB.sse` 가 하나 더. Puppeteer 재현 — 다른 탭 2: DELETE 15ms·`cancelled:true` / 4: DELETE 23,725ms 대기 뒤 `cancelled:false`·「완료 #68」 / 5: 화면이 `/api/conn` 도 못 받음. 서버는 맞다(API DELETE 로 20/184 에서 `CANCELLED`, `Job.emit` 144~146 이 취소 뒤 `DONE`→`CANCELLED`). 2:49 의 두 스냅샷은 로그상 `DONE` → DELETE 가 서버에 못 닿았다.
- **코드 검사 1줄**: 실행 #65(demo egov, 10,618건) 중 줄 1 이 5,427 — `file.lineEnding` 4,642(demo 프로필 `CRLF`, egov 사본 4,647 중 LF 4,642·줄바꿈 없음 5) · `jsp.scriptlets` 326(eGov JSP 머리 `<%\n /**\n  * @Class Name : …\n  */ %>`) · `file.header` 247 · `file.encoding` 210.
- **02 볼륨**: `out/demo/20261007-145442/산출물/02_테이블정의서.xlsx` — 184행 중 163 빈칸, 21 은 `1.0`·`76.0` 처럼 수만. 스냅샷 #66~#68 모두 184 중 rowCount null 163(표 162 + 뷰 1). 데모 Oracle `ALL_TABLES`(EGOV) — `LAST_ANALYZED` null 162 · 분석됨 21(합 977, 예: `COMTNMENUINFO` 163). **통계 없는 162개를 실제로 세면 전부 0건**(0.2초 — 8장). 데모에서는 「없으면 0건」 이 결과로 맞지만, 통계가 없다고 데이터가 없다는 보장은 없어(운영 DB·통계 작업이 꺼진 사업) 세어서 확인한다 — 사용자 결정.

### 2.2 관련 코드

- `common.js`(IIFE, Rhino — fetch·async·`?.` 금지): `var live = null; function watch()` 89~101 · `badge()` 103~116 · 끝의 시작부 `if (document.readyState === 'loading') …` · export `window.TB = {…, copy}`.
- 코드 검사: `code_check.html` 74 `#runDir`·75 `#msg` · `code_check_ext.js` — `msg(id,text,cls)` 22 · `rules` 13(`GET /api/check/rules` — `id·group·kind·params…`) · `start(body,label)` 152~161 · `done()` 163~166 · `poll(jobId)` 168~187(400ms, `QUEUED|RUNNING` 이면 다시; 그 밖 `done()`, `DONE` 아니면 `msg(status…,'err')`) · `render()` 280~300(열 `['파일','줄',…]`, `f.line`) · `copy()` 326~330 · 연결 365. `web/CheckRoutes.java` — rules 44~71 · run 73~(`jobs.submit("check", …)` → `CheckRunner.run(…, jc)` → `store.save` — 취소면 저장 전에 끊김) · export 190~209(행 `[file, line, group, rule, severity, message]`). `CheckRunner` 59 파일마다 `checkCancelled`. `JobRoutes` 48 `DELETE /api/jobs/{id}`.
- 규칙: `check/rules.yaml` — `jsp.scriptlets` 89~96(`regex '<%(?![@=!-])'`, `skipComments: true`, `params {max: 0}` → 건수 규칙, 줄 = 첫 블록) · `kind: file` 넷 136~159. `FileRule` encoding·lineEnding·header 는 줄 1 · mixedIndent 는 실제 줄. `RegexRule.apply` 28~55 · `stripComments` jsp 가지 67(`JspLinks`·`AbsentRule` 도 씀). `Rule.Def` 26~27 record(`params` null → `Map.of()`, `kind` 빈 → `"regex"`).
- 논리명 화면: `logical_name.html` 6 `<title>논리명 변환기</title>` · 48 `<h1>` · 49 `.sub` · 129~131 후보 버튼(`data-kind` terms·words·domains 순, 글 「05 표준용어 후보」·「06 표준단어사전」·「07 표준도메인 후보」 — **산출물 번호와 어긋남**: 산출물 05 표준단어·06 표준도메인·07 표준용어) · 132 wordUse. `index.html` 95~98 런처 카드(이름·설명). `deliverable_sql.html` DOCS 데이터 285 근처 「논리명 변환기 FK 추론용」. `core/deliverable/Grades.java` 05 DocMark 근거 「논리명 변환기에서 채운 뒤」. 후보 CSV 파일명 `LogicalRoutes` 251~252(번호 없음 — 그대로).
- 스냅샷: `core/meta/SnapshotService.run` 43~83 — `conns.open` → `src.collect(scope, listener)`(표마다 `checkCancelled`·진행 20~80) → `warnings` → `checkCancelled` → 진행 80 「저장」 → `store.save(…, schemas, scope, warningLines)` → 결과 `{snapshotId, schemas, tables, elapsedMs, store, warnings}`. `MetaSource.Warning(kind, sqlState, vendorCode, count)`. `Table.withStats(Long rowCount, LocalDateTime createdAt, LocalDateTime lastDdlAt)` 62. `Scope.accepts` 73 — `skipEmpty` 면 `rowCount == 0` 만 뺀다(null 은 남김). 행 수는 벤더 통계(Oracle `ALL_TABLES.NUM_ROWS` `OracleMetaSource` 31~32·92~) — JDBC 일반 수집기는 null.
- 02 정의서: `Definitions.java` 45 열 목록(「테이블 볼륨」) · 132 `t.rowCount() == null ? "" : (Object) t.rowCount()`. `Doc(no, name, columns, rows, @JsonIgnore estimated)` — 값은 문자열·수·"". `XlsxFiller.fill` 34~ — `Number` 면 `setCellValue(double)`, 스타일은 양식 첫 행 열 스타일(`styles`) 또는 1-33 wrap 복제.
- **05·07 ↔ 논리명 화면(실측 2026-10-07)**: `core/deliverable/Standards.build(LogicalRun.Result r, Dictionaries d, DomainMatcher dm, Options o)` 31~34 → `d05`(37~57, **`r.usedTokens()` 만** — 매칭된 약어, `Candidates.stdWordRows` 130~156)·`d06`·`d07`(71~86, `Candidates.termRows` 58~81 — 표·컬럼을 이름(대문자)으로 중복 제거, `flag()` 92~94: src `none` → 「미매칭」, `missing` 비지 않으면 「부분매칭」, 특이사항에 적고 미매칭 조각 글은 안 적는다; 부분매칭 이름은 원문 토큰이 그대로 섞인다 `Converter` 107~113). 세 문서 다 4-인자 `Doc` 이라 `estimated` 빈 맵. `LogicalRun.Result(rows, tableRows, rank, usedTokens, usedWords, stats)` 43 — `Row(owner, table, col, name, src, missing, dtype, …)` 21 · `TableRow(owner, table, name, src, missing)` 29 · `Rank(token, count)` 36(`rank()` 96~108: `missing` 출현 수 합, **사용자 사전 키는 count 0 으로도 든다**) · 태그 given/user/word/multi/mix/none. `DeliverableService.build` 71~139: 05·06·07 중 하나라도 있으면 `dict.load()`(사용자 사전 포함) + `LogicalRun.run(ColumnInputs.fromSchemas(snapshot), dicts, req.skipTokens(), req.orgFirst())` 79~83 → `Standards.build` 90~95 → `Result(files, skipped, guide)` 61 → `writeGuide` 165~200(「항목」·「요약」 180~190 `summary.add(List.of("스냅샷", …))` 꼴·「관계 후보」, `sheets` LinkedHashMap 32~34). `DeliverableRoutes` build 96~157: 스냅샷은 `filtered()`(182~196, `Deliverables.filter(snap, profile.deliverable().filter())`) 로 거른 것, `BuildRequest.skipTokens·orgFirst` 는 화면이 안 보내 프로필 skipTokens·`orgFirst=true`(140~143). 논리명 화면 `/api/logical/run`(`LogicalRoutes` 149~172, `LogicalRequest(snapshotId, csv, owner, skipTokens, orgFirst, dialect, includeTables)` 32~34, `run()` 354~378 — **안 거른 스냅샷**)·응답 `{rows, tableRows, rank:[{token,count,user,conflict}], stats}`. 화면 `logical_name.html`: `#snap`(`loadSnapshots` 200~211 — 늘 최신 `list[0]`), `#pri`(67, 기본 `org` = orgFirst true — 빌드와 같다), `#skip`(프로필에서), `input()` 180~189, `renderRank` 317~336(열 약어·출현·한글·충돌, `<input data-abbr>`), `saveWord` 337~348(`PUT /api/dict/user/{abbr} {ko}` → `run(true)`), `run(keep)` 351~366(`#runMsg` 「컬럼 · 테이블 · 완전 · 혼합 · 부분 · 미매칭」). **`location.search` 를 읽는 화면이 없다**(sql_snippets 만 hash). 화면 사이 링크는 `index.html` 카드뿐. `deliverable_sql.html` `poll()` 900~915 가 `#buildMsg` 에 「완료 — 저장 … / 건너뜀」, `DOCS` 키·번호 어긋남(`d05`→07·`d06`→05·`d07`→06 — 표시 번호는 `no` 라 화면엔 맞게 보인다, 손대지 않음). `Grades` DocMark 07 근거 「사전에 없는 조각은 빈칸」 은 틀렸다(원문 토큰이 이름에 남는다). `Candidates.java` 주석 41·124·158 도 옛 번호.
- 시험: `SmokeHtmlUnitTest.codeCheckFolderRun`(프로필 780 — `encoding: UTF-8`·`lineEnding: LF` 있음, 픽스처 `a/A.java` LF) · `CheckRulesTest.fixturesGolden` 48~79(`fixtures/check/**`, **`neg` 접두 파일은 지적 0**, `golden/check/fixtures.json`) · jsp 픽스처 `pos.jsp`(2줄 코드 스크립틀릿)·`neg.jsp`(4줄) · `CheckCorpusTest`(@corpus, `golden/corpus/check-egov.json` 등 — 규칙별 `{count, files}`, egov `jsp.scriptlets` 603/603) · `SnapshotServiceTest`(H2 + FakeSource — 표 `T1..Tn` 은 DB 에 없다, warnings 단언 없음, `cancelAtTableTwoStopsAndSavesNothingThenRetakeWorks` 157) · `VendorFallbackTest`·`DbCorpusBase`(MetaSource.collect 를 직접 — SnapshotService 를 안 거친다) · `golden/meta/*.json`(collect 결과 — oracle·postgres rowCount null 8) · `XlsxFillerTest.fillsExampleTemplatesGolden`(02·03·10 값 골든 `golden/deliverable/filled-*.json`, 값은 `getNumericCellValue`) · `DefinitionsTest`(02 골든). Puppeteer `scripts/puppeteer/smoke-dbbrowser.js`.

## 3. 설계 결정

- **D1 [고정] 0-50 숨은 탭은 배지 연결을 닫는다**: `visibilitychange` — `hidden` 이면 `live.close(); live = null`, `visible` 이고 백엔드 모드(`backend` 참 — `badge()` ping 성공에서)면 `watch()`. `watch()` 첫 줄에 `document.visibilityState === 'hidden'` 이면 안 연다(3초 재시도가 숨은 탭에서 여는 것 막기). 작업 진행 `TB.sse` 는 그대로.
- **D2 [고정] 5-21 코드 검사 중지**: `#runStop`(btn-g, disabled) · `jobNow` · `start` 응답에서 켬 · `done()` 에서 끔 · `stop()` = DELETE · `poll` 이 `CANCELLED` 면 「중지함 — 이력에 남기지 않았다」. 서버 안 바꿈.
- **D3 [고정] 5-22 주석만 든 스크립틀릿**: `RegexRule.blankCommentScriptlets` — 몸통이 공백·`/* */`·`//` 뿐인 `<%…%>`(`<%@ <%= <%! <%--` 제외)를 공백으로(줄바꿈 유지). 적용은 `RegexRule.apply` 에서 `skipComments` 이고 lang 이 jsp·jspf·tag 일 때만 — `stripComments` 본문은 안 바꾼다.
- **D4 [고정] 5-23 「파일」 표기는 표시만**: `Rule.Def.fileLevel()` = `"file".equals(kind) && !"mixedIndent".equals(String.valueOf(params.get("check")))` · rules 응답에 `fileLevel` · 화면 `render()`·`copy()` 줄 칸 · xlsx export 줄 칸 「파일」. 저장·비교는 줄 1 그대로.
- **D5 [고정] 3-10 후보 버튼 번호**: 순서와 글을 산출물 번호로 — `words` 「05 표준단어 후보」 → `domains` 「06 표준도메인 후보」 → `terms` 「07 표준용어 후보」 → `wordUse` 「공통표준단어 사용여부」. `data-kind`·API·CSV 파일명은 그대로.
- **D6 [고정] 3-11 이름 「표준 사전 · 논리명」**: `<title>`·`<h1>`·런처 카드 이름. 부제·카드 설명 첫 문장(무엇을·무엇으로·어떻게 — CLAUDE.md 글 규칙)은 「컬럼·테이블 영문명을 표준단어 사전과 맞춰 한글 논리명으로 조립하고, 사전에 없는 약어를 채워 표준단어·표준용어 산출물(05·07)을 채운다.」 + 둘째 문장 「입력은 DB 스냅샷 또는 컬럼목록 CSV, 결과는 COMMENT DDL·표준 후보 CSV·미준수 리포트·마스킹 SQL.」(카드는 같은 뜻을 짧게). 이 이름을 가리키는 글 둘도 바꾼다 — `deliverable_sql.html` DOCS 「논리명 변환기 FK 추론용」 → 「표준 사전 · 논리명 화면의 FK 추론용」, `Grades` 05 근거 「논리명 변환기에서」 → 「표준 사전 · 논리명 화면에서」. 파일명·API·순수본 이야기(주석 「순수본 논리명 변환기」)는 그대로 — 순수본 이름은 순수본 것이다.
- **D7 [고정] 1-47 통계 없는 표만 COUNT(*)**: `SnapshotService` 에서 `collect` 뒤·저장 전 — `type` 이 VIEW 가 아니고 `rowCount == null` 인 표만 `SELECT COUNT(*) FROM <q>schema<q>.<q>table<q>`(`q` = `DatabaseMetaData.getIdentifierQuoteString()`, 공백이면 따옴표 없이; 이름 안의 따옴표는 두 번). `Statement.setQueryTimeout(5)`, **전체 60초 예산**(넘으면 남은 표는 안 셈). 표마다 `ctx.checkCancelled()` + 진행 80~90 「행 수 n/m — 표」. 실패·시간초과·예산 초과는 null 로 두고 경고 `rowCount`(SQLState·코드별 건수, 예산 초과는 `rowCount "" 0 ×n`)를 `warnings` 에 더한다(로그도 건수·종류만 — 규칙 3). 센 뒤 `scope.accepts` 로 한 번 더 거른다(skipEmpty 가 이제 0 을 안다). `MetaSource`·벤더 수집기·`golden/meta/*` 는 안 바뀐다(코퍼스·VendorFallback 은 collect 를 직접 부른다). 버린 대안: 0건으로 채우기(데이터 있는 표가 0 으로 적힌다) · 모든 표 COUNT(운영 DB 부하).
- **D8 [고정] 2-21 02 볼륨 「건」**: `Doc` 에 `@JsonIgnore Map<String, String> formats`(열 → 엑셀 표시 형식) 를 더하고(기존 생성자 유지 — 빈 맵), `Definitions` 02 가 `formats = Map.of("테이블 볼륨", "#,##0\"건\"")`. 값: `rowCount != null` 이면 수 그대로 · `null` 이고 표(VIEW 아님)면 문자열 「통계 없음」 · VIEW 는 "". `XlsxFiller.fill` — 값이 `Number` 이고 그 값 열에 형식이 있으면 셀 스타일을 복제해 `setDataFormat(wb.createDataFormat().getFormat(fmt))`(열마다 하나 캐시, 1-33 wrap 캐시와 같은 꼴 — wrap 과 겹치면 둘 다). 화면·CSV 는 안 바꾼다.
- **D11 [고정] 2-22 산출물 결과에 05·07 정확도 수**: `core/deliverable/Coverage(int words, int missingTokens, int missingOccurrences, int terms, int termsPartial, int termsUnmatched)` + `static Coverage of(LogicalRun.Result r, Doc d07)` — `words = r.usedTokens().size()` · `missingTokens` = `r.rank()` 중 `count > 0` 인 수(사용자 사전 키 0 은 뺀다) · `missingOccurrences` = 그 count 합 · `terms` = 07 행 수 · `termsPartial`/`termsUnmatched` = 07 특이사항 열에 「부분매칭」/「미매칭」 이 든 행 수. `DeliverableService.Result` 에 `Coverage coverage`(05·06·07 을 안 만들면 null) → 작업 결과 JSON `coverage` → 00_작성안내 「요약」 에 두 행 `05 표준단어 | 사전 단어 n · 사전에 없는 조각 m(출현 k) — 「미등록 약어」 시트` · `07 표준용어 | 용어 t · 부분매칭 p · 미매칭 q` → 화면 `#buildMsg` 둘째 줄 「05 단어 n · 미등록 약어 m(출현 k) · 07 부분매칭 p · 미매칭 q」. `Grades` 근거 글 둘을 실물에 맞게 — 05 「사전에 있는 단어만 — 미등록 약어는 00_작성안내 「미등록 약어」 시트에 모이고, 표준 사전 · 논리명 화면에서 채운 뒤 다시 만든다」 · 07 「컬럼명을 사전으로 풀어 추정. 사전에 없는 조각은 영문 그대로 이름에 남고 특이사항에 「부분매칭」·「미매칭」」. 버린 대안 — 비율(%)만: 몇 개를 채워야 하는지가 안 보인다.
- **D12 [고정] 2-23 00_작성안내 「미등록 약어」 시트**(사용자 2026-10-07 — 05 행으로 싣지 않는다): `Standards.missingAbbrs(LogicalRun.Result r)` → 행 `[약어, 출현, 예시, 채우는 곳]`, 출현 내림차순 → 약어순. 예시 = 그 약어가 `missing` 에 든 컬럼 `표.컬럼`(없으면 표 `표`) 최대 3개를 「 · 」 로. 채우는 곳 = 「표준 사전 · 논리명 화면 → 미등록 약어 랭킹에 한글을 넣으면 사용자 사전에 저장 → 산출물 다시 만들기」(모든 행 같은 글 — 엑셀에서 열 하나로 보인다). 사용자 사전 키 0 은 뺀다. `writeGuide` 가 「요약」 뒤에 시트 「미등록 약어」(05·06·07 을 만들 때만, 0건이면 머리만). 버린 대안 — 따로 CSV: 안내서 한 파일에 일거리가 모이는 쪽이 현장에서 덜 잃는다.
- **D13 [고정] 3-12 화면 잇기 — 산출물 → 표준 사전 · 논리명**: ① `logical_name.html` 이 `URLSearchParams` 를 읽는다 — `snapshot=<id>` 면 `loadSnapshots()` 뒤 그 id 를 고르고(목록에 없으면 최신 그대로 + `#runMsg` 「스냅샷 #id 가 없다」) **바로 `run()`**, `scope=deliverable` 면 새 체크박스 `#delivScope`(「산출물 범위(프로필 deliverable.filter)로 거르기」, 입력 카드 `#snap` 아래, 기본 꺼짐)를 켠다. `input()` 이 `deliverableFilter: $('delivScope').checked` 를 보낸다. 체크박스는 YAML 조건을 켜고 끄는 것뿐이고 표 고르기 UI 가 아니다(메모리 「거르기 조건은 YAML 로」 와 어긋나지 않는다). ② `LogicalRequest` 에 `Boolean deliverableFilter` — `LogicalRoutes.run()` 이 참이면 `Deliverables.filter(snap, profile.deliverable().filter())`(`DeliverableRoutes.filtered` 와 같은 함수, ctx 없이) 를 거친다. `#runMsg` 앞에 「산출물 범위 · 」 를 붙인다. `#pri` 기본 `org` 라 orgFirst 도 빌드와 같다(바꾸지 않는다). ③ `deliverable_sql.html` — `#buildMsg` 아래 `<div class="msg" id="buildLink"></div>`; 완료 때 `coverage.missingTokens > 0` 이면 `<a href="logical_name.html?snapshot=<id>&scope=deliverable" target="_blank" rel="noopener">미등록 약어 m개 → 표준 사전 · 논리명 화면에서 채운다</a>`(DOM 으로 만든다 — innerHTML 금지, `ToolsFolderTest.extractedScriptsOnlyClearInnerHtml` 은 js 파일만 보지만 같은 규칙), 0 이면 「미등록 약어 없음」. 버린 대안 — 산출물 화면 안에 랭킹 끼워 넣기: 같은 기능이 두 화면에 생긴다.
- **D9 데모 프로필**: `profiles/demo.yaml` 7줄 `lineEnding: CRLF` → `LF`(커밋 없음).
- **D10 S0 잔손**(번들 30 AI 리뷰 「아니오」 둘): `PROGRESS.md` 「(이 커밋)」 여덟 → 1-36 `a2b808c`·1-35 `0228999`·1-33 `983d9ec`·1-34 `ae00e28`·1-31a `b771a86`·1-31b `c2eb1bc`·1-31c `72051e7`·1-31d `39cbf9a`(설계 16 줄 `ea5722b`, 번들 30 마무리 줄 `0835ab9`) · 현재 상태 → 번들 30 머지 `a73e2a7` · `ToolsFolderTest` 1-36 javadoc 을 `deliverableHasNoSqlRun` 바로 위로.

## 4. 실행 스텝

공통: 데모 서버를 끈다(`target/app.jar` 잠금 — `TaskStop bck809shn`, 없으면 `Stop-Process -Id 33772`). 가지 `work/2026-10-07c`(main `a73e2a7`). 청크마다 `bash scripts/verify.sh` → 따로 `git commit` → PLAN 행 상태 + PROGRESS 이력(해시). 「닫힘」 은 부숴서 빨강 확인.

### S0 문서 — 설계 17 + 번들 30 잔손
- **대상**: `design/17-bundle31.md`(신설 — 이 파일, 제목 「설계 17」) · `PLAN.md`(15장 번들 31 행, 16장 새 행 열하나) · `PROGRESS.md`(D10·현재 상태·이력) · `ToolsFolderTest.java`(D10 javadoc).
- **변경**: 번들 표 `| 번들 31 | `0-50`→`5-21`→`5-22`→`5-23`→`3-10`→`3-11`→`1-47`→`2-21`→`2-22`→`2-23`→`3-12` | 2026-10-07(Fable, 설계 17 — `design/17-bundle31.md`). **데모에서 본 결함·요구** — 탭 많으면 중지가 안 먹음·코드 검사 중지·1줄 지적·「표준 사전 · 논리명」·02 볼륨 「건」·05·07 정확도 수와 미등록 약어 시트·산출물 → 논리명 화면 링크. 새 의존성 0. 머지 위임 없음 | 데모 서버 끔 · Docker(`--full`) | 설계 완료(2026-10-07) — 실행 대기 |`. 새 행 설계 칸은 「**설계 17 Sn 이 원본**」 + 요지 한 줄 + 닫힘 한 줄. 선행: 0-50←0-47 · 5-21←5-5 · 5-22←5-1 · 5-23←5-5 · 3-10←3-6 · 3-11←3-8 · 1-47←1-14 · 2-21←2-11 · 2-22←2-13 · 2-23←2-22 · 3-12←2-22·3-11.
- **검증**: `bash scripts/verify.sh` 초록. 커밋 `docs: 번들 31 설계 17 · 번들 30 이력 해시`.

### S1 = 0-50 숨은 탭은 배지 연결을 닫는다
- **대상**: `common.js` · `ToolsFolderTest` · `scripts/puppeteer/smoke-tabs.js`(신설).
- **변경**: ①

```js
  var live = null;
  var backend = false; // ping 이 됐다 — 다시 보일 때 붙을지
  function watch() {
    if (live || typeof EventSource === 'undefined' || document.visibilityState === 'hidden') return;
    …(지금 그대로)
  }
  /* 0-50 — 브라우저는 한 서버에 연결 6개까지만 연다. 숨은 탭이 배지 연결을 쥐고 있으면 보이는 탭의 중지·조회가 줄을 선다 */
  function onVisibility() {
    if (document.visibilityState === 'hidden') {
      if (live) { live.close(); live = null; }
    } else if (backend) {
      watch();
    }
  }
```
`badge()` ping 성공 가지에서 `watch()` 앞에 `backend = true;` · 시작부 옆에 `document.addEventListener('visibilitychange', onVisibility);` · 84~88 주석 끝에 「숨은 탭은 닫는다(0-50)」.
② `ToolsFolderTest.hiddenTabsReleaseAliveConnection` — common.js 에 `addEventListener('visibilitychange'`·`live.close()`·`document.visibilityState === 'hidden'`.
③ `smoke-tabs.js`(smoke-dbbrowser 꼴): 임시 H2 1500 표 프로필 + 서버(포트 41782) → 다른 탭 다섯(index·code_check 번갈아, 배지 「백엔드 연결」 까지 대기) → db_browser 탭 열고 `page.bringToFront()`(나머지가 실제로 hidden — 8장) → 1초 → 찍기 → 「테이블 n/1500」 → 중지 → 5초 안에 「중지함 — 저장하지 않았다」 · DELETE 응답 `cancelled:true` · 스냅샷 목록 0. 끝 줄 「통과 n · 실패 n」, 실패면 exit 1.
- **검증**: `-Dtest='ToolsFolderTest,SmokeHtmlUnitTest#opensWithoutScriptErrors'` 초록 · `scripts/mvn.sh -q -B -DskipTests package` 뒤 `node scripts/puppeteer/smoke-tabs.js` 통과. **닫힘**: 리스너 등록 줄을 빼면 smoke-tabs 가 「완료」 로 빨강.
- **사다리**: 헤드리스에서 `bringToFront` 뒤에도 DELETE 가 기다리면 `headless: false` 로 다시. 그래도면 멈춘다.

### S2 = 5-21 코드 검사 중지
- **대상**: `code_check.html` · `code_check_ext.js` · `SmokeHtmlUnitTest`.
- **변경**: D2. 스모크 `codeCheckStops` — 임시 폴더 `a/` 에 `.java` 1,000 개(각 `package a;` + `class Cn { void f() {` + `System.out.println(1);` 200줄 + `} }` — 실측 7.8초, 8장) → `#dir` → `#runDir` → `#runStop` 켜질 때까지 `waitForBackgroundJavaScript(100)` ×50 → 클릭 → 20초 안에 `#msg` 「중지함 — 이력에 남기지 않았다」 · `#runStop` 꺼짐·`#runDir` 켜짐 · `GET /api/check/runs` 수 그대로.
- **검증**: `-Dtest='SmokeHtmlUnitTest#codeCheck*'` 초록. **닫힘**: `stop()` 의 DELETE 를 빼면 빨강(1,000 개가 끝까지 돌아 「파일 1000 · …」).
- **사다리**: 중지 전에 끝나면 3,000 개로(허용 조건을 넓히지 않는다).

### S3 = 5-22 주석만 든 스크립틀릿은 안 셈
- **대상**: `core/check/RegexRule.java` · `fixtures/check/jsp/neg.jsp` · 단위 시험은 `CheckRulesTest` 에(`RegexRuleTest` 는 없다 — 확인함).
- **변경**: ①

```java
    private static final Pattern COMMENT_SCRIPTLET = Pattern.compile("<%(?![@=!-])(?:\\s|/\\*[\\s\\S]*?\\*/|//[^\\n]*)*%>");

    /** 5-22 — 몸통이 주석·공백뿐인 스크립틀릿(eGov JSP 머리)을 공백으로. 줄바꿈은 남긴다 */
    static String blankCommentScriptlets(String text) {
        Matcher m = COMMENT_SCRIPTLET.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group().replaceAll("[^\\n]", " ")));
        }
        m.appendTail(sb);
        return sb.toString();
    }
```
`apply` 에서 skipComments 로 만든 글에, lang 이 `jsp`·`jspf`·`tag` 면 한 번 더. ② `neg.jsp` 끝에 `\n<%\n /**\n  * @Class Name : neg.jsp\n  */\n%>` 덧붙임(새 파일 아님 — neg 는 지적 0). ③ 단위: 주석만 든 블록은 공백(줄 수 같음) · `<% /* c */ int a; %>` 는 그대로.
- **검증**: `CheckRulesTest` 초록(`fixtures.json` 불변 — neg 행 없음) · `--full` `CheckCorpusTest` 의 `jsp.scriptlets` 수가 **준다** → 그 시험만 `-Dgolden.update=true`, 전후 수를 이력에. 분석 골든 불변. **닫힘**: `apply` 의 새 호출을 빼면 neg 가 걸려 빨강.
- **사다리**: 코퍼스 수가 늘거나 다른 규칙이 바뀌면 멈춘다.

### S4 = 5-23 파일 단위 지적은 줄 칸에 「파일」
- **대상**: `core/check/Rule.java` · `web/CheckRoutes.java` · `code_check_ext.js` · `CheckRoutesTest` · `SmokeHtmlUnitTest`.
- **변경**: D4 — `Def.fileLevel()`(`on()` 옆) · rules 응답 `m.put("fileLevel", d.fileLevel())` · export 행 줄 값 `fileRules.contains(f.rule()) ? "파일" : f.line()` · 화면 `var fileRule = {}`(rules 받은 뒤 채움), `lineText(f)` 를 `render()`·`copy()` 에. 시험: rules 응답 `file.lineEnding` fileLevel true·`file.mixedIndent` false · export xlsx 의 그 행 줄 칸이 문자열 「파일」 · 스모크 `codeCheckFolderRun` 에 `a/B.java` = `"class B {\r\n}\r\n"` 를 더해 `file.lineEnding` 행 줄 칸 「파일」, `common.sysout` 행은 숫자.
- **검증**: `-Dtest='CheckRoutesTest,SmokeHtmlUnitTest#codeCheck*'` 초록. **닫힘**: `lineText` 를 `f.line` 으로 되돌리면 스모크 빨강.

### S5 = 3-10 후보 버튼 번호를 산출물에 맞춘다
- **대상**: `logical_name.html`(129~132) · `SmokeHtmlUnitTest` 또는 `ToolsFolderTest`.
- **변경**: D5 순서·글. 시험 `ToolsFolderTest.logicalCandidateNumbersMatchDeliverables` — 버튼 글이 `data-kind` 별로 words「05 표준단어」·domains「06 표준도메인」·terms「07 표준용어」 로 시작.
- **검증**: `-Dtest='ToolsFolderTest,SmokeHtmlUnitTest#logical*'` 초록. **닫힘**: 옛 글로 되돌리면 빨강.

### S6 = 3-11 이름 「표준 사전 · 논리명」
- **대상**: `logical_name.html`(6·48·49) · `index.html`(95~98) · `deliverable_sql.html`(DOCS 285 근처 한 글) · `Grades.java`(05 근거).
- **변경**: D6. 시험: `LauncherTest` 44~45 꼴로 — 본문에 `href="logical_name.html"` 와 `>표준 사전 · 논리명<`, 「논리명 변환기」 없음 · `ToolsFolderTest.logicalNameRenamed` — `logical_name.html` `<title>표준 사전 · 논리명</title>`, `tools/` 어느 파일에도 「논리명 변환기」 없음.
- **검증**: `-Dtest='LauncherTest,ToolsFolderTest,GradesTest,SmokeHtmlUnitTest#opensWithoutScriptErrors'` 초록. **닫힘**: 런처 이름을 되돌리면 빨강.

### S7 = 1-47 통계 없는 표만 COUNT(*)
- **대상**: `core/meta/SnapshotService.java` · `core/meta/RowCounter.java`(신설) · `SnapshotServiceTest`.
- **변경**: ①

```java
/** 1-47 — 통계가 없는 표(rowCount null, 뷰 제외)만 실제로 센다. 표마다 5초·전체 60초. 실패는 null + 경고 rowCount */
final class RowCounter {
    static final int PER_TABLE_SEC = 5;
    static final long BUDGET_MS = 60_000;
    record Out(List<Schema> schemas, List<MetaSource.Warning> warnings) {}
    static Out fill(Connection conn, List<Schema> schemas, JobContext ctx) throws SQLException
}
```
`fill` — 대상 수 m 을 먼저 세고, 표마다 `ctx.checkCancelled()`·`ctx.progress(80 + 10 * k / m, "행 수 " + k + "/" + m + " — " + 표)` → `try (Statement st = conn.createStatement()) { st.setQueryTimeout(PER_TABLE_SEC); rs = st.executeQuery("SELECT COUNT(*) FROM " + q(schema) + "." + q(table)) }` → `t.withStats(n, t.createdAt(), t.lastDdlAt())`; `SQLException` 은 SQLState·벤더코드별로 세어 경고. 예산 넘으면 남은 표 수만큼 경고 `new Warning("rowCount", "", 0, n)`. 빈 스키마 이름(MariaDB 카탈로그 등)은 `q(table)` 만.
② `SnapshotService.run` — `collect` 와 `src.warnings()` 뒤, 진행 80 「저장」 전에 `RowCounter.Out rc = RowCounter.fill(conn, schemas, ctx)`, `schemas = rc.schemas()` 를 스키마마다 `withTables(tables.filter(scope::accepts))`, `warnings` 에 `rc.warnings()` 를 더해 지금처럼 로그·`warningLines`·결과에. 진행 「저장」 은 90 으로.
③ `SnapshotServiceTest`: H2 에 `CREATE TABLE T1(ID INT)` + 3행, FakeSource 가 T1(rowCount null)·T2(rowCount 7, DB 에 없음 — 안 세므로 그대로 7)·V1(VIEW, null)을 준다 → 스냅샷 결과 T1=3·T2=7·V1=null, 경고 없음 · DB 에 없는 T3(null) 를 더하면 T3=null + 경고 kind `rowCount` · 취소 시험(157) 그대로 초록.
- **검증**: `-Dtest='SnapshotServiceTest,MetaRoutesTest,SmokeHtmlUnitTest#dbBrowser*'` 초록. 데모 Oracle 로 손 확인(S9): 스냅샷 rowCount null 이 163 → 1 이하(VIEW). **닫힘**: `fill` 호출을 빼면 T1=3 단언 빨강.
- **사다리**: H2 에서 `setQueryTimeout` 이 예외면 그 호출을 `try` 로 감싸 무시하지 말고 — 드라이버가 안 받는 경우만 `SQLFeatureNotSupportedException` 을 잡는다.

### S8 = 2-21 02 테이블 볼륨 「건」 + 「통계 없음」
- **대상**: `core/deliverable/Doc.java` · `core/deliverable/Definitions.java`(02) · `core/report/XlsxFiller.java` · 골든 `golden/deliverable/filled-02.json`·`pg-02.json`(`DefinitionsTest`, diff 확인 뒤 갱신) · `XlsxFillerTest`.
- **변경**: D8. ① `Doc` 에 컴포넌트 `@JsonIgnore Map<String, String> formats`(compact 에서 null → `Map.of()`, 키가 columns 에 있어야), 기존 4·5-인자 생성자는 빈 맵. ② 02 값(`Definitions.d02` 125~136 — 뷰도 행에 든다, 거르지 않음): `t.rowCount() != null ? (Object) t.rowCount() : view ? "" : "통계 없음"`(`view` = `t.type() != null && t.type().toUpperCase(Locale.ROOT).contains("VIEW")` — `DeliverableRoutes` nopk 와 같은 꼴), `formats = Map.of("테이블 볼륨", "#,##0\"건\"")`. ③ `XlsxFiller.fill` — `m.columns()` 의 값 열 이름으로 `d.formats().get(field)` 를 찾아 `Number` 셀에 형식 스타일(열마다 캐시, 양식 스타일·1-33 wrap 을 복제한 뒤 `setDataFormat`). ④ 시험 `XlsxFillerTest.volumeShowsCountUnit`: 02 를 채운 xlsx 의 볼륨 셀 — 수 셀은 `getCellStyle().getDataFormatString()` 이 `#,##0"건"`, `DataFormatter` 로 읽으면 「1,234건」 꼴 · rowCount null 표는 문자열 「통계 없음」. 골든 diff 는 null 행이 「통계 없음」 이 되는 것뿐이어야 한다(수 값은 같다).
- **검증**: `-Dtest='XlsxFillerTest,DefinitionsTest,DeliverableRoutesTest,GradesTest'` 초록. **닫힘**: `formats` 적용을 빼면 형식 단언 빨강.

### S9 = 2-22 산출물 결과에 05·07 정확도 수
- **대상**: `core/deliverable/Coverage.java`(신설) · `core/deliverable/DeliverableService.java`(`Result`·`build`·`writeGuide` 요약) · `core/deliverable/Grades.java`(05·07 근거) · `tools/deliverable_sql.html`(`poll` 완료 글) · `StandardsTest`·`DeliverableRoutesTest`·`GradesTest`.
- **변경**: D11. `DeliverableService.Result(files, skipped, guide, coverage)` — 기존 3-인자 호출은 `null`. `build` 에서 `Standards.build` 뒤 `Coverage.of(r, d07)`(07 Doc 은 `docs.get("07")`). `writeGuide` 인자에 `Coverage`(null 이면 두 행 생략). 화면 `poll()` 완료 분기: `res.coverage` 가 있으면 `#buildMsg` 글에 `NL + '05 단어 ' + c.words + ' · 미등록 약어 ' + c.missingTokens + '(출현 ' + c.missingOccurrences + ') · 07 부분매칭 ' + c.termsPartial + ' · 미매칭 ' + c.termsUnmatched`. 시험 — `StandardsTest.coverageCountsFromSample`: pg 샘플(`pgDocs()` 가 쓰는 `LogicalRun.Result`)로 `Coverage.of` 값을 한 번 계산해 **정확한 수**로 단언(실행자가 처음 돌려 값을 적는다 — 0 이면 샘플에 미매칭이 없는 것이라 `rank` 가 빈지 확인) · `DeliverableRoutesTest.buildWritesElevenXlsx` 에 작업 결과 `coverage` 키 여섯 + 00_작성안내 「요약」 에 「05 표준단어」·「07 표준용어」 행 · `GradesTest` 근거 글은 자유(비지 않음만).
- **검증**: `-Dtest='StandardsTest,DeliverableRoutesTest,GradesTest'` 초록. **닫힘**: `Coverage.of` 에서 `count > 0` 조건을 빼면(사용자 사전 0 포함) `coverageCountsFromSample` 빨강 — 샘플에 사용자 사전 키가 없으면 시험 안에서 하나 넣어 0 건을 만든다.
- **의존**: S6(05 근거 글 자리 — S6 이 이름만 바꾸고 S9 가 문장을 다시 쓴다).

### S10 = 2-23 00_작성안내 「미등록 약어」 시트
- **대상**: `core/deliverable/Standards.java`(`missingAbbrs`) · `DeliverableService.writeGuide` · `DeliverableRoutesTest`(+ 픽스처 컬럼).
- **변경**: D12.

```java
    /** 2-23 — 사전에 없는 조각: [약어, 출현, 예시(표.컬럼 최대 3), 채우는 곳]. 출현 내림차순 → 약어순. 사용자 사전 키(출현 0)는 뺀다 */
    static List<List<Object>> missingAbbrs(LogicalRun.Result r)
```
예시는 `r.rows()` 를 돌며 `missing` 에 든 약어마다 `table.col` 을 3개까지, 모자라면 `r.tableRows()` 의 `table`. `writeGuide` — `sheets.put("요약", …)` 뒤 `if (coverage != null) sheets.put("미등록 약어", table(List.of("약어", "출현", "예시", "채우는 곳"), rows))`. 시험 — `DeliverableRoutesTest.buildWritesElevenXlsx`(또는 새 시험)에서 H2 픽스처에 컬럼 `ZZQX_NM`(사전에 없는 조각 `ZZQX`)을 더해 시트 「미등록 약어」 첫 행이 `ZZQX · 1 · <표>.ZZQX_NM · 표준 사전 · 논리명 화면 …` 이고 「요약」 05 행이 「조각 1(출현 1)」. 픽스처 컬럼을 더하면 흔들리는 골든(02·03 값 골든 등)은 그 시험이 쓰는 스냅샷이 공용인지 먼저 본다 — 공용이면 **시험 안에서만** `CREATE TABLE ZZ_T(ZZQX_NM VARCHAR(10))` 을 더한 별도 스냅샷을 찍는다.
- **검증**: `-Dtest='DeliverableRoutesTest,StandardsTest'` 초록, `golden/deliverable/*` 불변. **닫힘**: `missingAbbrs` 의 `count > 0` 거름을 빼거나 시트 추가를 빼면 빨강.
- **의존**: S9.

### S11 = 3-12 화면 잇기 — 산출물 → 표준 사전 · 논리명
- **대상**: `tools/logical_name.html` · `web/LogicalRoutes.java`(`LogicalRequest.deliverableFilter`·`run()`) · `tools/deliverable_sql.html`(`#buildLink`) · `LogicalRoutesTest`·`SmokeHtmlUnitTest`·`ToolsFolderTest`.
- **변경**: D13. 화면 ① `init` 끝: `var q = new URLSearchParams(location.search)` — `q.get('scope') === 'deliverable'` 면 `#delivScope` 켬 · `loadSnapshots()` 가 끝난 뒤(`then`) `q.get('snapshot')` 이 있고 목록에 있으면 `#snap` 값을 그것으로 하고 `run(false)`, 없으면 `msg('runMsg', '스냅샷 #' + id + ' 가 없다 — 최신으로', 'err')`. `HtmlUnit` 은 `URLSearchParams` 를 안다(4.x). ② `LogicalRoutes.run()` 354~378 — `req.deliverableFilter()` 참이면 `ColumnInputs.fromSchemas(Deliverables.filter(snap, active.get().map(Profile::deliverable).map(Deliverable::filter).orElse(null)))`. ③ 산출물 화면 `poll()` 완료: `#buildLink` 비우고 `res.coverage` 가 있으면 `a` 요소(`document.createElement('a')`, href `'logical_name.html?snapshot=' + encodeURIComponent($('snap').value) + '&scope=deliverable'`, `target=_blank`, `rel=noopener`, 글 「미등록 약어 m개 → 표준 사전 · 논리명 화면에서 채운다」) 또는 글 「미등록 약어 없음」. 시험 — `LogicalRoutesTest.deliverableFilterNarrowsRows`: 프로필 `deliverable.filter` 로 표 하나를 빼고 `deliverableFilter: true` 면 그 표 컬럼이 응답 `rows` 에 없고 false 면 있다 · `SmokeHtmlUnitTest.logicalNameOpensFromDeliverable`(`deliverableDdlCard` 처럼 H2 스냅샷을 가진 앱에서): `logical_name.html?snapshot=<id>&scope=deliverable` 을 열면 `#snap` 값 == id · `#delivScope` 켜짐 · `#runMsg` 가 「산출물 범위 · 컬럼」 으로 시작(자동 실행) · 같은 앱의 산출물 화면에서 05 를 켜고 만들면 `#buildLink` 에 `a[href^="logical_name.html?snapshot="]` 또는 「미등록 약어 없음」 · `ToolsFolderTest`: `deliverable_sql.html` 에 `innerHTML` 로 `<a` 를 넣는 줄이 없다(이미 있는 스캔 규칙에 html 도 포함되는지 보고, 아니면 글 단언 한 줄).
- **검증**: `-Dtest='LogicalRoutesTest,SmokeHtmlUnitTest#logical*+deliverable*,ToolsFolderTest'` 초록. **닫힘**: `run()` 의 거름 분기를 빼면 `deliverableFilterNarrowsRows` 빨강 · 자동 `run(false)` 를 빼면 스모크 빨강.
- **의존**: S9·S10(결과 JSON `coverage`)·S6(이름).

### S12 데모 프로필 + 마무리
- `profiles/demo.yaml` `lineEnding: LF`(커밋 없음, 이력에 「커밋 밖」).
- 마무리(CLAUDE.md 1~6): 독립 리뷰 · gate-probe · `--full`(메모리 10GB 확인, S3 코퍼스 갱신은 여기서 드러나면 갱신 커밋 뒤 다시) · `node scripts/puppeteer/smoke-tabs.js`·`smoke-dbbrowser.js` · push 따로 · PR(본문 표 + 원인 실측 숫자) · CI · AI 리뷰 처분 → **머지 안 하고 멈춘다**.
- 데모 다시: `"C:/Program Files/Java/jdk-17.0.19/bin/java" -jar target/app.jar serve --port 41790 --profile demo --data-dir C:/workspace/toolbox-demo/data`(백그라운드) → `/api/ping` · 데모 Oracle 로 스냅샷 하나 찍어 rowCount null 수 확인 · 산출물(05·07 포함)을 한 번 만들어 `#buildMsg` 둘째 줄 수와 00_작성안내 「미등록 약어」 시트 행 수(egov 실측 330 근처)를 이력에 → 사용자에게 볼 것(탭 여럿에서 중지·코드 검사 중지·1줄 지적·이름·02 볼륨·산출물 결과 수와 링크 → 랭킹에서 약어 하나 채우고 다시 만들면 수가 주는지)을 알린다.

## 5. 금지 사항

- `RegexRule.stripComments` 본문 · `CheckStore` 스키마·줄 값 · `JobManager`·`Job`·`JobRoutes` · `MetaSource`·벤더 수집기(행 수는 SnapshotService 에서만) · `golden/meta/*` 를 안 바꾼다.
- 통계가 있는 표를 세지 않는다 · null 을 0 으로 채우지 않는다 · COUNT 결과·SQL 을 로그에 남기지 않는다(건수·종류만).
- `TB.sse` 폴링화 · 배지 주기 ping · 탭 공유 장치 금지. `common.js` 에 fetch·async·`?.`·`??` 금지. `TB` 에 시험용 출구 금지.
- `logical_name.html` 파일명·`/api/logical/*`·후보 CSV 파일명 · `pure/`·`sql_snippets.html` 금지. 새 의존성 금지.
- 05 정의서에 미등록 약어 행을 싣지 않는다(사용자 결정 — 00_작성안내 시트로만). 논리명 화면에 표 고르기 UI 를 만들지 않는다(`#delivScope` 는 YAML 조건 켬·끔뿐). 산출물 화면에서 사전을 편집하지 않는다. `innerHTML` 로 링크를 넣지 않는다. `Standards.d05`·`d07` 의 행 내용(골든)을 바꾸지 않는다 — 수는 밖에서 센다.
- 시험을 고쳐 통과시키지 않는다 — 고쳐도 되는 것은 이 문서가 이름을 댄 것(`codeCheckFolderRun` 픽스처 추가 · `golden/corpus/check-*.json` 의 `jsp.scriptlets` · `golden/deliverable/filled-02.json`·`pg-02.json` 의 null 행 · `StandardsTest:66` 메시지 글)뿐.
- `profiles/demo.yaml` 커밋 금지.

## 6. 최종 검증

- 청크마다 `bash scripts/verify.sh` 초록 뒤 커밋(따로).
- 번들 끝: gate-probe 여덟 · `--full` 초록 · Puppeteer 둘 통과 · 회귀(`SmokeHtmlUnitTest` 전부 · `AnalyzeCorpusTest` 분석 골든 불변 · `golden/meta/*` 불변 · `golden/check/fixtures.json` 불변).
- 수동(사람 — 실브라우저): 탭 다섯 넘게 열고 스냅샷 중지 · 코드 검사 중지 · demo egov 다시 검사(1줄 지적 감소, header·encoding 은 「파일」) · 런처·화면 이름 · 02 볼륨 「n건」·「통계 없음」 · 산출물 만들기 → 둘째 줄 수·링크 → 랭킹에서 약어 하나 채움 → 다시 만들면 미등록 약어 수가 1 준다 · 00_작성안내 「미등록 약어」 시트.

## 7. 중단 조건

멈추고 보고(어느 스텝·무엇이 달랐나·선택지):
- 2장의 함수·줄이 실제와 다르다(같은 이름으로 다시 찾되 없으면 멈춘다).
- 검증이 2회 연속 실패, 원인이 그 스텝 밖.
- 5장을 어기지 않고는 못 간다 · 스텝에 없는 파일을 3개 이상 고쳐야 한다.
- S1 사다리까지 빨강 · S3 코퍼스 수가 늘거나 다른 규칙·분석 골든이 바뀐다 · S7 에서 `golden/meta/*` 가 바뀐다 · S8 골든 diff 에 null 행 말고 다른 값이 바뀐다 · S9~S11 에서 `golden/deliverable/sample-05·06·07.json`·`filled-*`·05/07 행 내용이 바뀐다(수는 밖에서 세야 한다).

## 8. 불확실 항목 — 설계 세션이 다 풀었다(2026-10-07, 실측 포함)

- **COUNT·시간 제한**(S7, 임시 Java 실측 — 지움): H2 2.3.232 `getIdentifierQuoteString()` = `"`, 공백 든 스키마·표 `"My S"."T a"` COUNT 3, `setQueryTimeout(5)` 예외 없음 · Oracle(ojdbc11, 데모) 따옴표 `"`, 통계 없는 162표를 `"EGOV"."표"` 로 `setQueryTimeout(5)` COUNT — **0.2초, 전부 0건**. S7 사다리(H2 `SQLFeatureNotSupportedException`)는 안 쓸 것으로 본다.
- **엑셀 표시 형식**(S8, POI 5.3.0 실측): `cloneStyleFrom(테두리 스타일)` + `setDataFormat(getFormat("#,##0\"건\""))` + wrap → 다시 읽으면 형식 `#,##0"건"`, `DataFormatter` 표시 「1,234건」, 값 1234.0(수 그대로), 테두리 THIN 유지.
- **연결 해제 효과**(S1, Puppeteer 실측 — 데모 서버): 다른 탭 다섯을 `bringToFront` 로 숨기고 그 탭들의 EventSource 를 닫으면 스냅샷 중지 DELETE **8ms**·`cancelled:true`·「중지함」(닫기 전엔 다섯 탭이면 화면 목록도 못 받았다). 0-50 의 방식이 원인을 푼다.
- **코드 검사 속도**(S2): 데모 서버 실측 — 200줄 `.java` 300개 2.6초, 1,000개 7.8초(파일당 약 8ms). 스모크는 1,000개. 측정으로 데모 이력에 검사 두 건이 더해졌다(데모 데이터만).
- **런처 시험 꼴**(S6): `LauncherTest` 44~45 가 `body.contains("href=\"spring_source_generator.html\"") && body.contains(">Table → Spring 소스 생성<")`·옛 이름 없음으로 이름 바꾸기를 잰다 — 같은 꼴.
- **02 와 뷰**(S8): `Definitions.d02`(125~136)는 뷰를 거르지 않는다 → 뷰는 볼륨 "" 유지. 골든 `pg-02.json`·`filled-02.json` 은 8행 모두 표·rowCount null → 전부 「통계 없음」 으로 바뀐다(수 값 없음 — diff 는 그 열뿐이어야).
- **`StandardsTest:66`**(S5): `assertEquals(csvRows("words"), doc("05").rows().size(), "06 표준단어사전 CSV 와 같은 행")` — 메시지 글만 「05 표준단어 후보 CSV 와 같은 행」 으로. `Candidates.java` 주석 41·124·158 의 옛 번호도 S5 에서 같이.
- **05·07 연결 재료**(S9~S11, 탐색 실측): 미매칭 조각은 `LogicalRun.Result.rows[].missing`·`tableRows[].missing`·`rank`(출현 합, 사용자 사전 키 0 포함)에 다 있고 05 는 `usedTokens` 만 쓴다 → 새 계산은 `Result` 만 읽고 `d05`·`d07` 골든을 안 건드린다. 07 특이사항에 「부분매칭」·「미매칭」 이 이미 적힌다(`Candidates.flag` 92~94) → 수는 그 열로 센다. 빌드와 화면의 입력 차이는 **거름(`deliverable.filter`)** 뿐 — skipTokens 는 둘 다 프로필, orgFirst 는 빌드 true·화면 `#pri` 기본 `org` 로 같다 → 3-12 는 거름만 맞춘다. `Deliverables.filter(List<Schema>, Scope)` 가 ctx 없는 정적 함수라 `LogicalRoutes` 에서 그대로 부른다. `deliverable_sql.html` 의 `DOCS` 키·번호 어긋남(`d05`→07 …)은 화면 표시가 `no` 라 맞게 보인다 — 손대지 않는다. HtmlUnit 4.x 는 `URLSearchParams` 를 지원한다(Rhino host 객체 — `opensWithoutScriptErrors` 가 잡아 준다).
- **드러난 것(행 안 세움, 이력에만)**: `DictStore.words` 가 `LIMIT 200` 이라 화면 「사용자 사전 n건」 이 200 에서 멈춘다 · `rank` 가 사용자 사전 키를 출현 0 으로 싣는다(채운 약어가 랭킹에 남는다 — 「채웠다」 표시로 쓰인다, 그대로).

- **`Rule.Def`**(S4): `Rule.java:26-27` record, compact 가 `params` null → `Map.of()`·`kind` 빈 → `"regex"`. `fileLevel()` 은 null 걱정 없음, `on()` 옆.
- **픽스처 골든**(S3): `CheckRulesTest.fixturesGolden`(48~79) — `neg` 접두 파일은 지적 0. jsp 는 `pos.jsp`(코드 스크립틀릿)·`neg.jsp`(4줄). `neg.jsp` 끝에 덧붙이면 5-22 전엔 빨강·뒤엔 0, `fixtures.json` 불변.
- **Puppeteer 숨김**(S1): 헤드리스 실측 — 새 탭 `visible`, 다른 탭 셋 연 뒤 `bringToFront()` 하면 나머지가 실제 `hidden`, `EventSource.close()` 뒤 readyState 2. 흉내(defineProperty)는 안 쓴다.
- **스모크 프로필**(S4): `codeCheckFolderRun` 프로필(780)이 이미 `lineEnding: LF` — CRLF 파일 `a/B.java` 를 더한다.
- **데모 서버**: PID 33772(41790), 백그라운드 셸 `bck809shn`.
- **02 볼륨 원인**(S7·S8): 데모 Oracle `LAST_ANALYZED` null 162(세면 전부 0) · 분석 21(`COMTNMENUINFO` 163 등). 산출물은 21행만 수(`1.0` 꼴), 163 빈칸.
- **행 수 영향 범위**(S7): `VendorFallbackTest`·`DbCorpusBase`(453 용량 단언 포함)·`golden/meta/*` 는 `MetaSource.collect` 를 직접 부른다 → SnapshotService 에서만 세면 안 바뀐다. `SnapshotServiceTest` 의 FakeSource 표 `T1..Tn` 은 H2 에 없다 → 셀 때 실패 → 경고만 늘고 기존 단언(경고 단언 없음)은 그대로. `Scope.accepts` 73 은 null 을 남기고 0 만 뺀다 — 센 뒤 다시 거르면 skipEmpty 가 맞아진다.
- **이름이 박힌 자리**(S6): `tools/` 에서 「논리명 변환기」 는 `logical_name.html` 6·48, `index.html` 96, `deliverable_sql.html` DOCS 한 곳. 자바는 `Grades` 05 근거(바꿈)·`Converter`·`Csv` 주석(「순수본 논리명 변환기」 — 순수본 이름이라 그대로). 시험·README 에는 없다(`LogicalCorpusTest` 주석 「논리명 변환」 은 기능 이름 — 그대로).
- **후보 버튼**(S5): `logical_name.html` 129~132, `data-kind` terms·words·domains·wordUse. `StandardsTest:66` 의 메시지 글 「06 표준단어…」 는 시험 메시지라 S5 에서 「05 표준단어…」 로 같이 고친다(값 단언 아님).
