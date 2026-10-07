# 설계 16 — 번들 30 데모 요구 다섯(1-36·1-35·1-33·1-34·1-31) (Fable, 2026-10-07)

실행 담당(Opus)은 이 문서와 PLAN 행만 보고 친다. 행 1-36·1-35·1-33·1-34·1-31a~d 의 원본은 이 문서 4장 S1~S8 이다(B-1=S1 … B-5d=S8). 번들 모드 규칙(CLAUDE.md 「번들 모드」)대로 갈려도 안 멈춘다: 실패 사다리에 있으면 그대로, 없으면 정하고 이력에 「정한 것(계획 밖)」.

사용자가 2026-10-07 정한 여덟(그대로 간다, 뒤집지 않는다): 1-36 실행 버튼 둘 다 빼고 라우트 유지 · 1-31 Oracle·Tibero 는 `@Size` + 바이트 주석 · 1-31 기본 켬 + 끄기 옵션 · 1-34 문서 종류로 고정 판정 · 1-33 양식은 넓히기만 · 1-34 카드 한 줄은 산출물 화면만 · 1-35 선택할 칸 없는 복사는 글만 「복사됨: n행」 · #49 머지 + 번들 30 머지까지(머지 위임).

## 1. 목표와 범위

- **목표**: 데모(2026-10-05)에서 나온 화면·산출물 요구 다섯을 닫는다. 사용자가 보는 변화 — 산출물 화면에 실행 버튼이 없고 문서마다 ●◐○ 와 근거가 보이며 카드가 한 줄씩 · 모든 화면의 복사가 「선택돼 보이고 복사됨」 한 꼴 · 만든 xlsx 열이 글 길이에 맞다 · DTO·egov5 VO 에 `@NotNull`·`@Size`·`@Digits`·`@Min/@Max`·`@Pattern` 이 붙는다(끌 수 있다).
- **In**: 행 `1-36` → `1-35` → `1-33` → `1-34` → `1-31a` → `1-31b` → `1-31c` → `1-31d`(1-31 을 넷으로 가름 — 2장) · 번들 마무리(리뷰·gate-probe·`--full`·push·PR·CI·AI 리뷰 처분·**머지**, 위임 있음) · 새 의존성 **시험 범위 둘**(`javax.validation:validation-api:2.0.1.Final`·`jakarta.validation:jakarta.validation-api:3.0.2`, 1-31b) → `scripts/offline-build.sh`(네트워크).
- **Out**: `pure/`·portfolio(사람 몫 ④) · `sql_snippets.html` 의 복사(순수본 핀 — `ToolsFolderTest.sqlSnippetsIsPurePlusCommonJs`) · `XlsxFiller.writeTemplate` 너비 식(예시 양식 생성 — 양식 대역이라 안 바꾼다) · identity·길이 단위(BYTE/CHAR) 수집 · 검증 그룹 · 산출물 정확도의 스냅샷 실측 · 다른 화면의 카드 배치 · `/api/sql/run`·`SqlRoutes`·`core.sqlrun` 삭제 · 조건부 행(2-7·2-8·4-10·5-7·5-14·5-15·6-9)·2-17.
- **설치**: 데모 서버 끔(`target/app.jar` 잠금) · Docker + 이미지 아홉(`--full`) · `--full` 앞 메모리 10GB 여유(첫 `--full` 은 2026-10-07 메모리 부족으로 끊겼다) · 1-31b 뒤 `bash scripts/offline-build.sh`(네트워크 — `m2/` 에 API jar 둘) · CI `offline` 잡은 pom 이 바뀌면 스스로 돈다(`.github/workflows/verify.yml:56-83`).

## 2. 현재 구조 요약

### 2.1 보정 — 행(2026-10-05 탐색)과 지금 실물이 다른 자리

| 행 | 행이 적은 것 | 지금(2026-10-07 실측) | 설계가 택한 것 |
|---|---|---|---|
| 1-36 | 실행 버튼 934~941·997~1004, `runSql` 1046~1056 | 버튼에 id 가 없다 — `renderGuide`(912~954)·`makeQuality`(979~1013) 가 JS 로 만든다. `runSql` 1047~1057. `/api/sql/run` 을 부르는 화면은 이 파일 1052 뿐, `export` 는 0 | 두 함수에서 만들기를 지우고 `runSql` 삭제 |
| 1-35 | 복사 자리 열넷 아홉 갈래 | 열다섯(dev_tools b64 `<img>` 태그 포함). `복사했다` 둘(deliverable 1076·logical_name 437), `복사함` 하나(dev_tools_ext 141). db_browser `dtoCopy`(295~300) 는 1-32 가 이미 「복사됨」+`select()` — 본보기. `TB` 에 알림 함수 없음(화면마다 `msg`/`toast`/`showToast`) | `TB.copy(src, notify)` — 알림은 화면 함수를 넘긴다 |
| 1-35 ③ | 순수본 유래 화면을 고쳐도 되나 「확인 필요」 | 순수본과 같음을 재는 시험은 `sql_snippets` 뿐(`ToolsFolderTest:109-117`). `PureFrozenTest` 는 `pure/` 만 | 백엔드본 안의 복사 함수 몸통을 직접 고친다(sql_snippets 제외) |
| 1-33 | xlsx 골든이 흔들릴까 | xlsx 바이트 골든 없음. 너비 단언은 `TableXlsxTest`(`golden/table/merge.json`·`head2.json` `widths`)뿐 — 나머지는 값만 | `TableXlsx.width` 를 옮기되 `TableXlsx` 의 클램프(6~80·+2)는 그대로 → 골든 불변 |
| 1-34 | `Grades` 비율로 ●◐○ | `Grades` 는 「설명이 필요한 열」 만 담는다(`DeliverableService:164`). 수로 가르면 02·03·08 이 ◐, 07·09 가 ○ — feasibility 표(`design/14-feasibility.md:9-23`)와 어긋난다. 18 은 `Grades` 에 없다. 문서 목록 GET 라우트 없음 — 화면의 `DOC_NOS`(710) 하드코딩 | 판정은 문서마다 **고정값**(feasibility 표 그대로)을 `Grades` 에 두고, 수는 근거 글에만. 사용자 「문서 종류로 고정 판정」 과 같다 |
| 1-31 | 「기본 켬」 | validation API jar 가 pom·`m2/`·`toolbox-corpus/egov35-lib` 어디에도 없다. `DtoGeneratorTest.compile`·`DdlCorpusTest.dtosCompile` 은 **클래스패스 없이** javac → 켜면 빨강 | 시험 범위 의존성 둘 + javac `-cp` 에 `java.class.path` |
| 1-31 | CHECK 파싱 | `Check(name, condition)` 표 단위 원문. `DdlReader` 는 CHECK·UNIQUE 를 안 읽는다(CREATE 탭 DTO 는 `checks=[]`). 스냅샷 픽스처 다섯 모두 `checks: []`. 유일한 파서 `InsertGen.checkIns`(DDL 원문의 `IN` 만, 124~170) | `Validation.parse(condition)` 신설 + `DdlReader` 가 CHECK 를 `Table.checks` 로(1-31a) |
| 1-31 | identity·BYTE/CHAR | 모델에 없음. `DdlReader:226-235` 는 `BYTE|CHAR` 를 맞추고 버린다 | 수집 안 함(Out). PK·DEFAULT 로 `@NotNull` 제외, 바이트는 주석 |
| 1-31 | 파일 수 | 한 행이면 `DtoGenerator`·`GenRoutes`·`DocCommands`·`db_browser.html`·`GenModel`·`VO.java.ftl`·pom·골든 9+11·시험 다섯 | 1-31a(핵심)·b(DTO 생성기)·c(API·CLI·화면)·d(CRUD VO) |
| 1-34 | 12개 체크 단언 「SmokeHtmlUnitTest 157·404」 | `deliverableGuideSwitchesDialect` 229 | |

### 2.2 산출물 화면 `src/main/resources/tools/deliverable_sql.html`(1090줄)

- CSS 22 `.grid { display: grid; grid-template-columns: 280px 1fr; gap: 12px; align-items: start; }` · 23 `.card` · 44 `.checks label { display: inline-block; … }`. 이 파일에만 영향(다른 화면의 `.grid` 는 각자 인라인).
- 카드 순서: 왼쪽(56~79) 입력 57 · 옵션 63 · 문서 74(`#docChecks` 76·`#anRun` 77) / 오른쪽(81~158) 생성 82 · 08 표준코드 89(`#codeConn` 93) · 09 연계 99 · 90 품질 진단 105(안내 107 `진단 → 정제 → 검증 순서. 결과는 화면에만 — 저장하지 않는다. 개인정보가 보이면 멈추고 보고한다.`, `#qMake` 115, `#qMsg` 118, `#qOut` 119) · DDL 123(`#ddlCopy` 138·`#ddlOut` 142·`#ddlMsg`) · SQL 가이드 145(`#guideDoc` 148·`#dialect` 149·`#guide` 155).
- JS(IIFE 699~1087): `msg(id,text,kind)` 705 · `DOC_NOS` 710 · `docLabel` 711~715(`DOCS` 배열 163~696 에서 이름, 18 은 하드코딩) · `loadConns` 741~749 · `renderChecks` 783~796(`<label><input type=checkbox data-no=NN> NN 이름</label>`, 18 만 기본 꺼짐) · `checkedDocs` 797 · `build` 863~881(`POST /api/deliverable/build`) · `renderGuide` 912~954(SQL 마다 `h3`·`pre.sql`·`.row`[복사 931~934 → `copyText(text)`, 실행 935~937 `고른 접속에서 실행`]·`out`(.tbl) 938·`m`(.msg) 940·실행 핸들러 942 `runSql(text,out,m)`) · `makeQuality` 979~1013(`POST /api/quality/sql` 985 → `#qOut` 에 복사 994~997·실행 998~1000 `고른 접속에서 실행(첫 문장)`·`out` 1001·`m` 1003·핸들러 1005) · `copyText` 1038~1045(숨은 textarea, 실패 삼킴) · `runSql` 1047~1057(`#codeConn` 없으면 `접속을 먼저 고른다(08 카드의 접속)`, `TB.api('/api/sql/run', …)` 1052) · DDL 복사 1076 `copyText($('ddlOut').value); msg('ddlMsg', '복사했다', 'ok')` · `init` 1059~1084.
- `#codeConn` 쓰는 곳: `previewCodes` 838 · `findLinks` 854 · `build` 872 · `runSql` 1048 → 1-36 뒤에도 셋이 남아 첫 option `(접속 — 코드값·DB링크용)` 은 그대로 맞다.
- 서버: `web/DeliverableRoutes.java` — `POST /api/deliverable/ddl` 50 · `build` 96(`BuildRequest` 30~32) · `GET /api/quality/kinds` 208 · `POST /api/quality/sql` 216 · `nopk` 226 · `codes/candidates` 245 · `codes/rows` 252 · `links/candidates` 273. 문서 목록 GET 없음. `App.java:174-175` 에서 등록. `SqlRoutes.register` 는 `App.java:170`(`/api/sql/run` 35·`export` 50) — `MetaRoutesTest.snapshotFlow:113·115` 가 직접 잰다.
- `core/deliverable/Grades.java`: `record Grade(String doc, String column, String level, String how)` 17 · 상수 `AUTO="자동"`·`GUESS="추정"`·`MANUAL="수동"` 13~15 · `all()` 25 · `of(doc)` 29 · `build()` 33~122 · `add(…)` 124. 문서별 수(자동/추정/수동): 01 5/0/8 · 02 5/2/8 · 03 10/4/6 · 04 4/1/0 · 05 1/1/1 · 06 1/1/0 · 07 0/1/2 · 08 3/1/3 · 09 0/2/5 · 10 1/0/0 · 11 1/0/0 · 18 없음. `DeliverableService.writeGuide` 165~200 이 「항목」 시트로 쓴다(173 `Grades.of(no)`). `GradesTest`(`core/deliverable/GradesTest.java`): `everyGradedColumnExists` 21~30 · `levelsAreThreeWordsAndHowIsFilled` 33~39.
- 문서 이름은 코드에 흩어져 있다: `Definitions.java` 107 「01 데이터베이스 정의서」·136 「02 테이블 정의서」·161 「03 컬럼 정의서」·257 「04 테이블 관계 정의서」·280 「10 인덱스 정의서」·314 「11 제약조건 정의서」 / `Standards.java` 56·67·85(05·06·07) / `CodeAndLink.java` 144·239(08·09) / 18 은 `DeliverableService.DOC18` 파일명 상수 49.
- feasibility 표(`design/14-feasibility.md:4-26`): ● 02·03·08·10·11·18 / ◐ 01·04·05·06·07·09 / 생성 안 하는 16·17·물리 ERD ◐, 논리 ERD·오너십·업무규칙 ○. 범례 4 「● 가능(핵심 항목 자동, 수동은 정책 칸 1~3) · ◐ 일부(핵심 일부가 추정·수동) · ○ 불가(핵심이 DB·소스에 없음)」.
- 시험: `SmokeHtmlUnitTest.deliverableGuideSwitchesDialect` 223~238(229 체크 12 · `#guide` 에 `ROW_NUMBER() OVER` · 236 `#qKind` option 8) · `deliverableDdlCard` 483~530(`#ddlMake` → `#ddlOut` `CREATE TABLE TB_DEPT (`) · `opensWithoutScriptErrors` 63~87. `ToolsFolderTest.saveNoticesGoThroughSavedText` 160(이 파일 `TB.savedText(` ≥ 2 — 894·1034). Puppeteer 스크립트는 이 화면을 안 건드린다.
- PLAN 서술: 53(2.1 부산물 「SQL 실행 대행」) · 100(2.5 「`/api/sql/run`·`export` 는 다른 화면이 써서 남긴다」) · 339(8장 API 표 「산출물 부산물」). 완료 행 596(2-5)·597(2-6)·683(4-11)은 안 고친다.

### 2.3 복사 자리 열다섯 + `common.js`

`common.js`(211줄, IIFE, Rhino 호환 — fetch·async·`?.` 금지, 4줄 주석): `api` 11~45 · `injectStyle` 49~65(`#tb-mode-badge`·`.tb-table` CSS 한 번) · `badge` 103 · `table` 119 · `sse` 158 · `snapLabel` 183 · `joinPath` 190 · `savedText` 196 · export 204 `window.TB = { api, badge, table, sse, snapLabel, joinPath, savedText }`. 알림 함수 없음.

| # | 자리 | 원천 | 지금 글 | 알림 | 1-35 뒤 |
|---|---|---|---|---|---|
| 1 | `db_browser.html` `dtoCopy` 295~300(버튼 139) | `#dtoOut`/`#dtoSnapOut` textarea(`dtoOutId`) | 복사됨 / 복사할 결과가 없다 / 복사 실패 — 직접 선택해 복사 | `msg('dtoMsg')` | `TB.copy(out, function (t, ok) { msg('dtoMsg', t, ok ? 'ok' : 'err'); })` |
| 2 | `deliverable_sql.html` `renderGuide` 931~934 | 문자열 `text`(= `pre.sql` 925) | 없음 | 없음 | `TB.copy({ el: pre }, say(m))` — `m` div 를 알림 자리로 남긴다 |
| 3 | 〃 `makeQuality` 994~997 | `r.sql`(= `pre.sql` 988) | 없음 | 없음 | 〃 |
| 4 | 〃 DDL 1076 | `#ddlOut` textarea | **복사했다** | `msg('ddlMsg')` | `TB.copy($('ddlOut'), …)`; `copyText` 1038~1045 삭제 |
| 5 | `logical_name.html` `maskCopy` 431~438(버튼 154) | `#maskSql` textarea | **복사했다** | `msg('maskMsg')` | `TB.copy($('maskSql'), …)` |
| 6 | `code_check_ext.js` `copy` 326~337(버튼 code_check.html 99) | 보이는 행 TSV(머리 `파일\t줄\t묶음\t규칙\t등급\t원문`) | `n행 복사` / 복사가 막혔다 — 브라우저 권한 | `msg('msg')` | `TB.copy({ text: tsv, label: shown.length + '행' }, …)` → 「복사됨: n행」 |
| 7 | `dev_tools.html` `copyEl` 1737~1744·`copyElLegacy` 1745~1748·`execCopy` 1749~1751(호출 9 + `dev_tools_ext.js:211`) | `el.value` | 복사됨 / 복사 실패 — 칸을 직접 선택해 복사할 것 | `showToast` | `copyEl = function (id) { TB.copy($(id), function (t) { showToast(t); }); }` — 몸통만 바꾼다 |
| 8 | 〃 `b64CopyImgTag` 1452~1467 | `<img>` 문자열 | `<img> 태그 복사됨` | `showToast` | `TB.copy({ text: tag, label: '<img> 태그' }, …)` → 「복사됨: <img> 태그」 |
| 9 | `dev_tools_ext.js` `copy` 24~30 · 문장 버튼 125~126 · `lsCopyAll` 138~142 | 복원 문장 / 전체 | 없음 / **전부 복사함 — 문장 n** | — / `msg('ls_msg')` | 문장: `TB.copy({ text: it.restored, el: pre }, …)`(그 `pre` 선택) · 전체: `TB.copy({ text: all, label: '문장 ' + LS.length }, …)`; 사설 `copy` 삭제 |
| 10 | `jsp_formatter.html` `copyOut` 759~774(버튼 106) | `#out` textarea | 복사됨 / 결과 없음 / 복사 실패 — 출력 칸을 … | `toast` | `showPane("out"); TB.copy($('out'), toast)` |
| 11 | `table_builder.html` `copyOut` 860~873(버튼 144) | `#out` textarea | 복사됨 / 복사 실패 … | `toast` | `TB.copy($('out'), toast)` |
| 12 | `special_chars.html` `copy` 429~447(호출 346·407·424·458) | 글자 하나·조합 | `복사됨: ★` / 복사 실패 — 브라우저가 … | `toast` | `TB.copy({ text: text, label: text }, toast)` |
| 13 | `sql_snippets.html` `copyQuery` 2016~2032 | `currentQuery` | 복사됨 | `showToast` | **안 고친다**(순수본 핀). 글 금지 시험의 예외 목록에 |

HtmlUnit 4.11.1: `navigator.clipboard` 없음 → 모든 자리가 `execCommand` 로 떨어진다. `document.execCommand('copy')` 는 아는 명령이라 안 던지지만 클립보드에 닿지 않는다(반환값 불명). 스모크는 `page.executeJavaScript` 로 `navigator.clipboard` 를 **심어** 복사된 글을 `window.__copied` 에 받는다(선례 — `savedTextShowsFileOrFolder` 381~389 가 `TB.*` 를 직접 부른다). `JS_OFF = Set.of("dev_tools")` 38 — dev_tools 는 스모크로 못 잰다(글 스캔만).

`ToolsFolderTest`: `files()` 29~33(`src/main/resources/tools` 1단) · 패턴 스캔 꼴 `noExternalLoads` 47~59(`file:line match` 모아 `assertEquals(List.of(), hits)`) · `saveNoticesGoThroughSavedText` 159~191(파일별 최소 횟수 + `common.js` 제외 금지 패턴 — **새 시험의 본보기**) · `patternCatchesKnownShapes` 219~232.

### 2.4 xlsx 쓰기 셋 `core/report/`

- `XlsxWriter`(102줄): `write(ResultTable, Path)` 28~32 → 시트 「결과」 · `write(LinkedHashMap<String,ResultTable>, Path)` 35~48(`SXSSFWorkbook(200)`, 머리 굵게 하나) · `sheet(Sheet, ResultTable, CellStyle)` 50~78(0행 머리, 1행부터 값 — null 은 셀 없음, `Number`→double, `Boolean`, 그 밖 `toString`; `truncated()` 면 끝 행 `… 최대 행수(n)에서 잘렸다`). 너비·wrap 없음. `ResultTable` 은 메모리 안(rows 목록) → 두 번 훑어도 된다. 부르는 곳: `SqlRoutes:73` · `Outputs:39`(← `AnalyzeRoutes:147·149`, `CheckRoutes:206·261`) · `LogicalRoutes:335` · `DeliverableService:157`(18)·`199`(00 작성안내).
- `XlsxFiller.fill(Path template, Mapping.DocMapping m, Doc d, Path out)` 34~80: `XSSFWorkbook` 35 · 시트 36~39 · `header = header(sheet, m.headerRow()-1)` 40(양식 머리 글 → 열 번호) · `first = m.firstRow()-1` 42 · 열별 양식 스타일 `styles` 43~47 · `shiftRows` 49~51 · 쓰기 52~71(`col = header.get(formCol)`, 숫자/문자/blank). 너비를 안 읽고 안 쓴다. `Mapping.DocMapping(file, sheet, headerRow, firstRow, columns)` `core/report/Mapping.java:21`. 부르는 곳 `DeliverableService:122`. 시험 `XlsxFillerTest`(값만 — `read()` 49~72, `fillsExampleTemplatesGolden` 75~86 `golden/deliverable/filled-NN.json`, 스타일은 `BorderStyle.THIN` 하나 89~103).
- `TableXlsx` 96~123: 병합 안 된 칸만 `textWidth[c] = max(width(text))` → `setColumnWidth(c, min(80, max(6, chars(w, textWidth[c]))) * 256)`, `chars` 191~201(px/7 · % · 아니면 `+2`). `width(String)` 178~189(줄마다 `> 0x2E80` 이면 2, 가장 긴 줄). 스타일 캐시 150~165 가 전부 `setWrapText(true)`. 골든 `golden/table/merge.json:6` `[20,20,11]`·`head2.json:6` `[26,6,6]`.

### 2.5 DTO·CRUD 생성

- `core/meta/Column(name, ordinal, nativeType, jdbcType, Long length, Integer precision, Integer scale, boolean nullable, String defaultValue, comment, domain)` 7~18 · `Table(…, pk, fks, uniques, indexes, …, List<Check> checks)` 11~24 · `Check(name, condition)` · `PrimaryKey(name, columns)` · `UniqueKey(name, columns)` · `ForeignKey(name, columns, refSchema, refTable, refColumns, deleteRule, updateRule)`. `JdbcMetaSource.loadColumns` 111~151: `length` 는 CHAR/VARCHAR/…/BINARY 류만(133), `precision/scale` 은 NUMERIC/DECIMAL 만(141~142), `nullable` 143, `defaultValue` 144. PG `text` 는 `length 2147483647`.
- `core/gen/DtoGenerator`(212줄): `Style { RECORD, BEAN, EGOV_VO }` 18~30 · `Options(packageName, style, skipTokens, logicalNames, notes, dialect)` 37~44 · `Source(className, text)` 46 · 사설 `Field(name, type, doc, todo)` 61 · `generate(Table, Options)` 64~121(69~89 컬럼마다 `types.javaType(c, dialect)`, null 이면 `Object` + TODO; FQN 은 `imports` TreeSet; `doc` = 코멘트 → 논리명 83) · `record(...)` 123~133(`        // TODO…` 줄 + `        Type name,`; @param 은 클래스 javadoc 102~113) · `bean(...)` 135~159(`    /** doc */` + `    private Type name; // TODO`). `TypeMapping.javaType` 84~106(`gen/type-mapping.yaml`, `decimal` → `resolve` 108~119: scale>0·p null → BigDecimal, p≤9 Integer, p≤19 Long).
- `core/gen/DdlReader`: `CONSTRAINT_LINE` 60~62(CHECK 포함하나 `item` 187~201 은 PK·FK 만) · `TYPE_END` 64~67(`CHECK|IDENTITY|…` 에서 타입 끝) · `item` 183~244(`nullable` 216~217 · `defaultValue` 218~219 · 길이 226~235 — `BYTE|CHAR` 버림) · PK 컬럼 `nullable=false` 169~173 · `withConstraints(pk, fks, List.of())` 175.
- `InsertGen.checkIns(String ddl)` 124~141 + `splitVals` 142~170(`'…'` 밖 쉼표로 가르고 따옴표 유지). `Definitions.java:195-208` 의 컬럼 낱말 정규식 `(?i)(?<![\w$#])NAME(?![\w$#])` — CHECK 가 어느 컬럼을 가리키는지 재는 꼴(재사용).
- 호출: `web/GenRoutes` `POST /api/gen/dto` 58~130(`DtoRequest(snapshotId, tables, ddl, packageName, style, dialect, skipTokens)` 35~36 · DDL 길 77~81 · 스냅샷 길 82~98 · 111~112 `gen.generate(t, new Options(...))` · `?save=true` 121~128) · `cli/DocCommands.Dto` 133~164(`--snapshot`·`--tables`·`--package`·`--style`) · `db_browser.html` DTO 카드 113~143(공통 줄 115~118 `#dtoStyle`·`#dtoPkg` — **1-32 결정 ④: 1-31 옵션은 이 줄 끝** · 탭 119 · `#dtoDialect` 129 · `#dtoDialectHelp` 「방언은 DATE 타입만 가른다 …」 · `#dtoSave`·`#dtoCopy`·`#dtoMsg` 137~142) · JS `dtoBody(mode)` 259~271 · `dtoMake` 273~284 · `dtoSave` 287~293.
- CRUD: `templates/gen/egov35/set.yaml`(`vars.ee: javax`, `valid: "false"`) · `egov4/set.yaml`(extends egov35, `ee: javax`, `valid: "false"`) · `egov5/set.yaml`(`ee: jakarta`, `valid: "true"`) · `egov35/VO.java.ftl`(69줄 — 7~11 `hasSize` 계산과 `import [=vars.ee].validation.constraints.Size;`, 18~22 `/** [=f.comment] */` + `@Size(max = [=f.length?c])` 조건 + `private [=f.javaType] [=f.name];`) · `GenModel.of(Table, Options, vars, TypeMapping)` 49~153(필드 map 96~107: `column·name·Name·javaType·string·pk·nullable·comment·length`; 상위 126~151 에 `imports`) · `Generator.run` 41~102 · `GenerateRoutes:63-156`.
- 골든: DTO 9(`golden/gen/{order_items,products,users}-{record,bean,egovvo}.java`, `DtoGeneratorTest.goldenAndCompiles` 52~63 → `GoldenFiles.assertText` + javac **클래스패스 없이** 65~75; `quotedTableNameCannotCloseJavadoc` 114~120·`unknownTypeAndDdlNotesBecomeTodoButStillCompile` 123~134 도 javac) · 픽스처 표: `ORDER_ITEMS`(NUMBER 다섯, 전부 NN, PK 둘, FK 둘, DEFAULT 없음) · `products`(code varchar(30) NN, name varchar(200) NN, price numeric(12,2) NN DEFAULT 0, created_at NN DEFAULT CURRENT_TIMESTAMP, PK product_id, unique code, FK category_id) · `users`(login_id varchar(50) NN, email varchar(200) null, user_name varchar(100) NN, use_yn bpchar(1) NN DEFAULT 'Y', PK user_id, unique login_id). CRUD 33(`golden/gen/egov35|egov4|egov5/` 10 + `paths.json`, `mapper-*.xml` 5, `model.json`) — `GenTemplatesTest.setsGolden` 44~54 · `GenModelTest.golden`(`empHist()` 25~35: EMP_NO NUMBER(6,0) NN PK · START_DATE DATE NN PK · JOB_ID VARCHAR2(10) NN · DEPT_NM VARCHAR2(30) · SALARY NUMBER(8,2) · CLASS VARCHAR2(5) · NOTE CLOB) · egov5 `EmpHistVO.java` 에 `jakarta…Size` + `@Size(max = 10|30|5)`.
- 표본: `GeneratorJavacTest`(V-17, egov35 만, `egov35-lib` 에 validation 없음 — egov35 는 `valid false` 라 영향 없음) · `GeneratorCorpusTest`(V-16, 문법만) · `DdlCorpusTest.dtosCompile` 160~196(**클래스패스 없이** javac) · `GenRoutesTest` 64~111(`contains` 단언 — `public record CustMst(`·`@param amt 금액`·`BigDecimal amt`; H2 `TB_CUST_MST (CUST_ID BIGINT PRIMARY KEY, USE_YN CHAR(1) NOT NULL, AMT DECIMAL(12,2))`, 시험 프로필에 `framework` 없음) · `SmokeHtmlUnitTest.dbBrowserMakesDtoFromDdl` 139~151(`public record TItem(`·`String itemNm`; DDL `T_ITEM (ITEM_ID INT PRIMARY KEY, ITEM_NM VARCHAR(50))`).
- `Profile(name, project, connections, defaultConnection, scope, deliverable, naming, codecheck, String framework, output, generator, logicalName)` 13~25 — `framework` 자유 글(`RuleSet:47` `startsWith("egov")`, `CheckRoutes:319` 기본 `egov35`). `ProfileStore` 는 `FAIL_ON_UNKNOWN_PROPERTIES` 켬(33) — **프로필 키를 더하지 않는다**(옵션은 요청·화면·CLI 에만). `profiles/demo.yaml` 은 `framework: egov4` + `templateSet: egov5`(어긋남 — DTO 는 framework, CRUD 는 세트 `ee` 를 따른다. 둘 다 제 설정에 맞고 서로 안 섞인다).

## 3. 설계 결정

- **D1 [고정] 1-36**: 실행 버튼 둘·`runSql`·실행 결과 칸(`out`)을 지운다. `/api/sql/run`·`export`·`SqlRoutes`·`core.sqlrun`·`MetaRoutesTest:113·115` 는 그대로(사용자 「라우트 유지」). 안내 107 은 「진단 → 정제 → 검증 순서. SQL 은 복사해 DB 툴에서 돌린다. 개인정보가 보이면 멈추고 보고한다.」. 버린 대안 — 라우트까지 삭제: CLI `api` 경로·시험·PLAN 8장까지 번져 범위가 커진다.
- **D2 [고정] 1-35 `TB.copy(src, notify)`** 한 함수. `src` 는 요소(`textarea`·`input` 은 `.value`, `pre`·`div` 는 `.textContent`) 또는 `{ text, el?, label? }`. 순서 — ① 글이 비면 `notify('복사할 것이 없다', false)` 끝 ② 눈에 보이게 고른다: 요소가 textarea·input 이면 `el.focus(); el.select()`(readonly 라도 된다), 그 밖 요소면 `Range.selectNodeContents` + `getSelection().addRange` ③ `navigator.clipboard && navigator.clipboard.writeText` 가 있으면 그것(Promise) → 성공 `복사됨` / 실패 ④ ④ `document.execCommand('copy')` — `el` 이 없으면 숨은 textarea 를 만들어 고르고 복사하고 지운다(이때 보이는 선택은 없다 — 사용자 결정 「글만」) ⑤ 글: 성공 `복사됨` + (`label` 있으면 `: ` + label) / 실패는 `el` 이 있으면 `복사 실패 — 선택된 글을 Ctrl+C`, 없으면 `복사 실패 — 브라우저가 클립보드를 막았다`. 성공 글에 「복사했다」·「복사함」 은 없다. 알림 자리는 화면마다 지금 자리(`msg`·`toast`·`showToast`)를 `notify` 로 넘긴다(사용자 ② 「자리는 그대로」). 버린 대안 — `_ext.js` 에서 `window.copyEl = …` 덮어쓰기: 페이지 스크립트가 IIFE 면 못 닿고, 닿아도 두 구현이 공존한다. 순수본 유래 넷(dev_tools·jsp_formatter·table_builder·special_chars)은 백엔드본의 그 함수 **몸통만** 고친다 — 순수본 핀 시험이 없다(2.1). `sql_snippets` 는 안 건드린다.
- **D3 [고정] 1-35 강제 지점** — `ToolsFolderTest.copyGoesThroughTbCopy`: ① `tools/` 모든 파일에 `복사했다`·`복사함` 없음 ② `execCommand(`·`clipboard.writeText` 는 `common.js` 와 `sql_snippets.html` 에만 ③ `TB.copy(` 최소 횟수 — db_browser 1·deliverable_sql 3·logical_name 1·code_check_ext 1·dev_tools 2·dev_tools_ext 2·jsp_formatter 1·table_builder 1·special_chars 1.
- **D4 [고정] 1-33 `core/report/ColumnWidths`**: 글 폭 = 한글(`> 0x2E80`) 2·그 밖 1, 줄바꿈이 있으면 가장 긴 줄(`TableXlsx.width` 를 옮긴다). 열 너비(칸) = `min(60, max(6, 머리·값 폭 최댓값 + 2))`. 폭 최댓값이 60 을 넘는 열은 **값 셀에 wrapText**. 수·날짜는 `toString()`(xlsx 가 표시 형식을 안 쓴다 — `XlsxWriter` 는 double 그대로). 머리는 후보다(사용자 예 ②). `XlsxFiller.fill` 은 **넓히기만** — `max(양식 너비, 계산)`. `TableXlsx` 는 클램프 6~80·`chars()` 그대로 두고 `width` 만 공용으로 → `golden/table/*.json` 불변. `writeTemplate` 은 안 바꾼다. 버린 대안 — SXSSF `autoSizeColumn`: 글꼴 측정(AWT)이 헤드리스·반입 PC 에서 흔들린다.
- **D5 [고정] 1-34 판정은 고정값**: `Grades.DocMark(no, name, mark, reason)` 12개를 `Grades` 에 둔다 — feasibility 표 그대로(● 02·03·08·10·11·18 / ◐ 01·04·05·06·07·09). 근거 글은 4.4 에 12개를 적었다 — 그대로 쓴다(지어내지 않는다). 수(자동·추정·수동)는 `Grades.of(no)` 로 세어 근거 뒤에 붙인다. 새 라우트 `GET /api/deliverable/docs`. 화면 `docLabel` 글은 그대로(사용자가 본 글). 기호 + 색 둘 다(`.mark-full` 초록·`.mark-part` 주황·`.mark-none` 회색). 버린 대안 — `Grades` 수로 계산: 2.1 보정 참조.
- **D6 [고정] 1-34 카드 한 줄**: `.grid { grid-template-columns: 1fr }` 만 바꾼다 — 왼쪽·오른쪽 감싸개 div 는 그대로라 순서는 입력 → 옵션 → 문서 → 생성 → 08 → 09 → 90 → DDL → SQL 가이드(행 쟁점 ⑥). 접기·차례 없음(프로젝트 기간 규칙).
- **D7 [고정] 1-31 변환은 한 곳 `core/gen/Validation`**: `of(Table, Column, javaType, dialect, Ns)` → `annotations`(줄)·`notes`(javadoc 조각)·`todos`·`imports`. 규칙 — `@NotNull`/`@NotBlank`(String): `!nullable` 이고 PK 컬럼 아니고 `defaultValue == null` 일 때만(사용자 ①) · `@Size(max = n)`: String 이고 `0 < length < 100000`(PG text 2147483647 제외) · `@Digits(integer = p - s, fraction = s)`: `precision > 0` 이고 자바 타입이 BigDecimal·Integer·Long·Short·Byte·BigInteger·Double·Float · CHECK(D8) · FK → note `FK → REF(COL)` · UNIQUE → note `UNIQUE` 또는 `UNIQUE(A,B)` · oracle·tibero 방언의 `@Size` 에 note `DB 길이 n 은 바이트일 수 있다(한글 1자 = 3바이트)`(사용자 ②). `Ns.of(framework)`: `egov5` → `jakarta.validation.constraints`, 그 밖·null → `javax.validation.constraints`.
- **D8 [고정] CHECK 파싱 범위**(행 쟁점 ④): 정규화 — `::type` 캐스트 제거(PG) · 식별자 따옴표 `"` `` ` `` `[` `]` 제거 · 바깥 괄호 벗김 · 최상위 `AND`/`OR` 로 가름. 원자 꼴 — `COL BETWEEN a AND b` · `COL op lit`/`lit op COL`(op ∈ `= <> != < <= > >=`) · `COL IN (…)` · PG `COL = ANY (ARRAY[…])`. 한 컬럼만 가리키고 ① AND 묶음이 전부 비교 → 범위(`@Min/@Max` 정수형: Integer·Long·Short·Byte·BigInteger — 배타면 ±1 / `@DecimalMin/@DecimalMax(value = "v", inclusive = false)` BigDecimal·Double·Float / 그 밖 타입은 note 만) ② OR 묶음이 전부 `=` 이거나 IN·ANY → 집합(String 이면 `@Pattern(regexp = "^(A|B)$")` 값은 `Pattern.quote` 아닌 손 이스케이프 `[\\^$.|?*+()[]{}` + note `허용 값: 'A','B'` / 수 타입은 note 만) ③ 그 밖(둘 이상 컬럼·함수·`IS NOT NULL`·`<>`·LIKE) → 가리키는 컬럼마다 note `CHECK: 원문` + todo `CHECK 를 코드로 옮긴다`. `IS NOT NULL` 만인 조건은 아무것도 안 낸다(Oracle 자동 제약은 이미 걸러진다, PG·MSSQL 도 같은 뜻). 방언별 픽스처는 4.5 ③.
- **D9 [고정] 1-31 켜기·끄기**: 생성기 `Options.validation`(`Ns` 또는 null = 끔). 라우트 `DtoRequest.validation`(`Boolean`, null → **켬**) + 네임스페이스는 **활성 프로필 `framework`** 로(요청에 안 받는다). 화면 체크박스 `#dtoValid` 기본 켬(공통 줄 끝, 1-32 ④). CLI `--no-validation`. 프로필 키는 안 더한다(`FAIL_ON_UNKNOWN_PROPERTIES` + 12-인자 생성자 넷). CRUD VO 는 **세트 `vars.valid`** 가 스위치(이미 있다 — egov5 켬·egov35·egov4 끔) — V-17 javac(egov35-lib 에 validation 없음)가 안 깨진다.
- **D10 [고정] 시험 의존성**: pom `<scope>test</scope>` 로 `javax.validation:validation-api:2.0.1.Final`·`jakarta.validation:jakarta.validation-api:3.0.2`. javac 를 부르는 시험 셋(`DtoGeneratorTest.compile`·`DdlCorpusTest.dtosCompile`·`GeneratorJavacTest` 는 그대로)은 `-cp System.getProperty("java.class.path")`. 버린 대안 — 시험이 어노테이션 스텁 소스를 지어 같이 컴파일: 우리 오타를 우리 스텁이 그대로 받아 못 잡는다.
- **D11 1-31 가름**: 1-31a 핵심(`Validation` + `DdlReader` CHECK) · 1-31b DTO 생성기 + 골든 9 + pom · 1-31c API·CLI·화면 · 1-31d CRUD VO(`GenModel`·`VO.java.ftl`·egov5 골든·`model.json`). PLAN 1-31 행은 「설계 16 — a~d 로 가름」 으로 남기고 새 행 넷.
- **D12 머지 위임**(사용자 2026-10-07): PR #49 는 머지됐다(main `b63f450`). 번들 30 가지는 `work/2026-10-07b` 를 그 main 에서 딴다. 번들 30 은 CI 여덟 초록 + AI 리뷰 처분 뒤 머지한다.

## 4. 실행 스텝

공통(CLAUDE.md 번들 모드·청크 규칙 그대로): 청크마다 `bash scripts/verify.sh` → 따로 `git commit` → PLAN 행 상태·PROGRESS 이력 한 줄(해시). 가지 `work/2026-10-07b`(`git checkout main && git pull` → main `b63f450` 에서 딴다). 부숴 봄은 각 「닫힘」 의 빨강 확인을 말한다.

### S0 문서 — 설계 16 을 저장소로

- **대상**: `design/16-bundle30.md`(신설) · `PLAN.md` 15장 번들 표·16장 행 · `PROGRESS.md`.
- **변경**: ① 이 파일을 `design/16-bundle30.md` 로 그대로 복사(제목만 「설계 16」). ② PLAN 15장 번들 표 끝에 — `| 번들 30 | `1-36`→`1-35`→`1-33`→`1-34`→`1-31a`→`1-31b`→`1-31c`→`1-31d` | 2026-10-07(Fable, 설계 16 — `design/16-bundle30.md`). **데모 요구 다섯** — 실행 버튼 제거·복사 한 꼴·xlsx 열 너비·산출물 정확도 표시·검증 어노테이션(넷으로 가름). 새 의존성 시험 범위 둘(validation API). 머지 위임(사용자 2026-10-07) | 데모 서버 끔 · Docker + 이미지 아홉 · 1-31b 뒤 `offline-build.sh`(네트워크) | 설계 완료(2026-10-07) — 실행 대기 |`. ③ 16장 — 행 1-36·1-35·1-33·1-34 의 설계 칸 맨 앞에 `**설계 16 B-n 이 원본**(쟁점 확정 2026-10-07 사용자) — ` 를 붙이고 상태 「설계 대기」 → 「설계 완료(2026-10-07) — 실행 대기」(B-1=1-36·B-2=1-35·B-3=1-33·B-4=1-34). 행 1-31 상태 → 「설계 16 — 1-31a~d 로 가름」. 그 아래 새 행 넷 `1-31a`·`1-31b`·`1-31c`·`1-31d`(설계 칸은 「설계 16 B-5a~d」 한 줄 + 닫힘 한 줄, 선행 1-31a ← 1-22·1-9 / b ← a / c ← b / d ← a). ④ PROGRESS 이력 「2026-10-07 | 설계 16 — 번들 30 | 보정 셋(2.1)…」 + 현재 상태 「진행중 청크」 를 번들 30 으로.
- **검증**: `bash scripts/verify.sh` 초록(docs 레인 doc-lint). 커밋 `docs: 번들 30 설계 16`.
- **의존**: 없음.

### S1 = 1-36 산출물 화면 실행 버튼 제거

- **대상**: `src/main/resources/tools/deliverable_sql.html` · `src/test/java/kr/ejg/toolbox/web/ToolsFolderTest.java` · `SmokeHtmlUnitTest.java` · `PLAN.md` 53·100·339.
- **변경**: ① `renderGuide` — 실행 버튼(935~937)·`out`(938~939)·핸들러(942)와 `row.appendChild(run)`·`box.appendChild(out)` 을 지운다. 복사 버튼·`m` 은 남긴다(1-35 가 `m` 을 알림 자리로 쓴다). ② `makeQuality` — 998~1005 같은 식으로. ③ `runSql` 1046~1057 삭제. ④ 안내 107 → `진단 → 정제 → 검증 순서. SQL 은 복사해 DB 툴에서 돌린다. 개인정보가 보이면 멈추고 보고한다.` ⑤ `ToolsFolderTest.deliverableHasNoSqlRun`: 파일에 `/api/sql/run`·`고른 접속에서 실행`·`runSql` 없음. ⑥ `SmokeHtmlUnitTest.deliverableGuideSwitchesDialect` — 방언 바꾼 뒤 `#guide button` 들의 글에 `실행` 이 없고 `복사` 는 있다(`page.querySelectorAll("#guide button")` 순회). ⑦ PLAN 53 「부산물: SQL 실행 대행 …」 → 「부산물: SQL 실행 API(`/sql/run`·`export`) — 화면 버튼은 전부 뺐다(2.5 스니펫 2026-10-04, 산출물 1-36 2026-10-05)」 · 100 「다른 화면이 써서 남긴다」 → 「부르는 화면이 없다 — `MetaRoutesTest`·CLI `api` 가 쓰며 삭제는 범위 밖(1-36)」 · 339 비고 → 「화면 버튼 없음(2.5·1-36). 시험·CLI `api` 만」.
- **검증**: `bash scripts/mvn.sh -q -B -Dtest='ToolsFolderTest,SmokeHtmlUnitTest#deliverableGuideSwitchesDialect' -DexcludedGroups=db test` 초록. **닫힘**: ⑤ 에서 `runSql` 글 단언만 빼고 실행 버튼 한 줄을 되살리면 ⑤·⑥ 빨강. `MetaRoutesTest.snapshotFlow` 초록(라우트 산다).
- **의존**: S0.

### S2 = 1-35 복사 한 꼴

- **대상**: `src/main/resources/tools/common.js` · 2.3 표의 파일 아홉(db_browser·deliverable_sql·logical_name·code_check_ext·dev_tools·dev_tools_ext·jsp_formatter·table_builder·special_chars) · `ToolsFolderTest` · `SmokeHtmlUnitTest`. 파일 수가 규칙(1~3)을 넘는다 — 한 함수로 모으는 청크라 가르면 두 구현이 공존한다(행 사유).
- **변경**: ① `common.js` 에 아래를 더하고 export 에 `copy` 를 넣는다.

```js
  // 복사(1-35) — 원천을 눈에 보이게 고른 뒤 클립보드에 넣고 알린다. src: 요소(textarea·input 은 value, 그 밖은 textContent)
  // 또는 { text, el, label }. notify(text, ok) 는 화면의 msg·toast. 성공 글은 어디서나 「복사됨」 하나.
  function copy(src, notify) {
    var say = notify || function () {};
    var el = src && src.nodeType ? src : (src && src.el) || null;
    var text = src && src.nodeType ? valueOf(src) : (src && src.text) || '';
    var label = src && !src.nodeType && src.label ? ': ' + src.label : '';
    if (!text) { say('복사할 것이 없다', false); return Promise.resolve(false); }
    if (el) { select(el); }
    var fail = el ? '복사 실패 — 선택된 글을 Ctrl+C' : '복사 실패 — 브라우저가 클립보드를 막았다';
    function done(ok) { say(ok ? '복사됨' + label : fail, ok); return ok; }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      return navigator.clipboard.writeText(text).then(function () { return done(true); }, function () { return done(legacy(el, text)); });
    }
    return Promise.resolve(done(legacy(el, text)));
  }
  function valueOf(el) { var t = el.tagName; return (t === 'TEXTAREA' || t === 'INPUT') ? el.value : el.textContent; }
  function select(el) {
    var t = el.tagName;
    if (t === 'TEXTAREA' || t === 'INPUT') { el.focus(); el.select(); return; }
    var r = document.createRange(); r.selectNodeContents(el);
    var s = window.getSelection(); s.removeAllRanges(); s.addRange(r);
  }
  function legacy(el, text) {
    var tmp = null;
    if (!el) {
      tmp = document.createElement('textarea'); tmp.value = text;
      tmp.style.position = 'fixed'; tmp.style.opacity = '0'; document.body.appendChild(tmp); tmp.select();
    }
    var ok = false;
    try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
    if (tmp) { document.body.removeChild(tmp); }
    return ok;
  }
```

② 자리 열셋을 2.3 표 「1-35 뒤」 열대로 바꾼다 — deliverable `copyText`·dev_tools_ext 사설 `copy`·dev_tools `copyElLegacy`/`execCopy` 는 지운다. deliverable 가이드·품질의 알림 `say(m)` 은 `function (t, ok) { m.textContent = t; m.className = 'msg ' + (ok ? 'ok' : 'err'); }`(기존 `msg()` 가 id 를 받아 `m` 요소엔 못 쓴다). ③ `ToolsFolderTest.copyGoesThroughTbCopy`(D3) + `patternCatchesKnownShapes` 에 `복사했다` 가 잡히는 줄 하나. ④ 스모크 — 공통 도우미 `stubClipboard(page)`: `page.executeJavaScript("navigator.clipboard = { writeText: function (t) { window.__copied = t; return Promise.resolve(); } }")`. 단언 다섯: (a) `dbBrowserMakesDtoFromDdl` 끝에 `#dtoCopy` 클릭 → `window.__copied` == `#dtoOut` value · `#dtoOut` 의 `selectionStart == 0 && selectionEnd == value.length` · `#dtoMsg` 「복사됨」 (b) `logicalNameMasking` 에 `#maskCopy` 클릭 → `__copied` == `#maskSql` value · `#maskMsg` 「복사됨」 (c) `deliverableDdlCard` 에 `#ddlCopy` → `__copied` == `#ddlOut` value · `#ddlMsg` 「복사됨」 (d) `deliverableGuideSwitchesDialect` 에 `#guide` 첫 「복사」 버튼 → `__copied` == 그 `pre.sql` textContent · `window.getSelection().toString()` == 같은 글 · 그 `.msg` 「복사됨」 (e) `codeCheckFolderRun` 에 `#copy` → `__copied` 가 `파일\t줄` 로 시작 · `#msg` 「복사됨: n행」(n = 보이는 행 수). HtmlUnit 에서 `selectionStart`·`getSelection()` 은 `page.executeJavaScript("document.getElementById('dtoOut').selectionEnd")` 꼴로 읽는다.
- **검증**: `-Dtest='ToolsFolderTest,SmokeHtmlUnitTest'` 초록. **닫힘**: `TB.copy` 에서 `select(el)` 줄을 빼면 (a)·(d) 빨강 · deliverable DDL 글을 「복사했다」 로 되돌리면 ③ 빨강.
- **의존**: S1(deliverable 의 `m` 자리).
- **실패 사다리**: HtmlUnit 이 `Range`/`getSelection` 을 `pre` 에 못 쓰면(d 의 선택 단언만) — 그 단언은 `__copied` 와 「복사됨」 으로 줄이고 이력에 · `el.select()` 뒤 HtmlUnit 의 `selectionEnd` 가 0 이면 (a) 의 선택 단언은 `document.activeElement.id == 'dtoOut'` 으로.

### S3 = 1-33 xlsx 열 너비

- **대상**: `core/report/ColumnWidths.java`(신설) · `XlsxWriter.java` · `XlsxFiller.java` · `TableXlsx.java`(`width` 호출만) · `ColumnWidthsTest.java`(신설) · `XlsxWriterTest` · `XlsxFillerTest`.
- **변경**: ① 

```java
/** xlsx 열 너비(1-33) — 머리·값 중 가장 긴 글 폭 + 2, 6~60 칸. 넘는 열은 값 셀을 줄바꿈. 글 폭은 한글 2·그 밖 1(TableXlsx 에서 옮김) */
public final class ColumnWidths {
    public static final int MIN = 6, MAX = 60, PAD = 2;
    private final int[] raw;
    public ColumnWidths(int cols)
    public void see(int col, String text)        // null·빈 글은 무시. 가장 긴 줄 폭을 최댓값으로
    public int chars(int col)                    // Math.min(MAX, Math.max(MIN, raw + PAD))
    public boolean wraps(int col)                // raw + PAD > MAX
    public void applyFrom(Sheet s, int col)      // 넓히기만: max(s.getColumnWidth(col), chars*256)
    public void apply(Sheet s, int col)          // chars*256
    static int width(String text)                // TableXlsx.width 그대로
}
```

② `XlsxWriter.sheet` — 쓰기 전에 `ColumnWidths w` 로 머리·모든 값(`String.valueOf` — Number 는 `toString`, null 건너뜀, 잘림 행 제외)을 훑고, 값 셀 스타일은 `wraps(c)` 인 열만 `wrap`(`CellStyle` 하나, `setWrapText(true)`) → 끝에 `w.apply(sheet, c)`. SXSSF 는 `setColumnWidth` 를 행 뒤에 불러도 된다. ③ `XlsxFiller.fill` — `m.columns()` 의 양식 머리 글과 그 열 값을 `ColumnWidths` 로 훑어 쓰기 뒤 `applyFrom(sheet, col)`; `wraps` 열은 데이터 셀 스타일을 `wb.createCellStyle(); cloneStyleFrom(styles.get(col)); setWrapText(true)` 로(열마다 하나 캐시). ④ `TableXlsx.width` → `ColumnWidths.width` 호출(클램프·`chars()` 그대로). ⑤ 시험 — `ColumnWidthsTest`: 사용자 예 ① 머리 `관련 엔터티명`, 값 `행정코드`·`공통분류코드`·`DB서비스모니터링로그정보` → `chars == 26`(2 + 22 + 2) ② 머리 `테이블 소유자`, 값 `EGOV` → `15`(13 + 2) ③ 빈 열 → 6 ④ 80칸 글 → 60 이고 `wraps` ⑤ 줄바꿈 든 글은 긴 줄. `XlsxWriterTest`: 만든 시트의 `getColumnWidth(c)/256` 이 예 ①②와 같다 · 긴 열의 값 셀 `getCellStyle().getWrapText()` 참. `XlsxFillerTest`: 양식 열 너비를 손으로 좁혀 둔 사본(`setColumnWidth(col, 4*256)`) → 채운 뒤 계산값으로 넓어짐 · 양식이 더 넓으면(`90*256`) 그대로 90 · `golden/deliverable/filled-*.json` 불변.
- **검증**: `-Dtest='ColumnWidthsTest,XlsxWriterTest,XlsxFillerTest,TableXlsxTest,DeliverableRoutesTest'` 초록, `golden/table/*.json` diff 없음. **닫힘**: `see` 가 머리를 안 보게 하면 예 ② 빨강 · `applyFrom` 을 `apply` 로 바꾸면 「양식이 더 넓으면 그대로」 빨강.
- **의존**: S0.

### S4 = 1-34 산출물 정확도 표시 + 카드 한 줄

- **대상**: `core/deliverable/Grades.java` · `web/DeliverableRoutes.java` · `tools/deliverable_sql.html` · `GradesTest` · `DeliverableRoutesTest` · `SmokeHtmlUnitTest` · `ToolsFolderTest`.
- **변경**: ① `Grades` 에 `public record DocMark(String no, String name, String mark, String reason)` + `public static List<DocMark> marks()`(12, 번호순) + `public static int[] counts(String no)`(자동·추정·수동 수). 12개 **그대로**:

| no | name | mark | reason |
|---|---|---|---|
| 01 | 데이터베이스 정의서 | ◐ | 한 행이라 DB 이름·버전은 자동, 담당·운영 정보는 프로필·화면 입력 |
| 02 | 테이블 정의서 | ● | 표·코멘트·행 수·생성일은 메타에서. 업무 분류·담당자는 수동 |
| 03 | 컬럼 정의서 | ● | 컬럼·타입·NULL·기본값·PK·FK 는 메타에서. 개인정보 여부는 이름으로 추정 |
| 04 | 테이블 관계 정의서 | ◐ | FK 는 메타에서. 매퍼 조인으로 추정한 관계는 뚜렷한 것만 |
| 05 | DB 표준단어 | ◐ | 사전에 있는 단어만 — 미매칭 조각은 논리명 변환기에서 채운 뒤 다시 만든다 |
| 06 | DB 표준도메인 | ◐ | 타입·길이에서 추정. 도메인 이름은 수동 |
| 07 | DB 표준용어 | ◐ | 컬럼명을 사전으로 풀어 추정. 사전에 없는 조각은 빈칸 |
| 08 | DB 표준코드 | ● | 고른 코드 표의 값을 접속에서 그대로. 코드 설명은 표에 있는 만큼 |
| 09 | 연계데이터 목록 정의서 | ◐ | DB 링크·외부 표 이름은 단서일 뿐 — 연계 상대·주기는 수동 |
| 10 | 인덱스 정의서 | ● | 인덱스·컬럼·정렬·유니크는 메타에서 |
| 11 | 제약조건 정의서 | ● | PK·FK·UNIQUE·CHECK 원문은 메타에서 |
| 18 | 테이블 대 응용프로그램 상관도 | ● | 프로그램 분석 CRUD 그대로. 동적 호출·타일즈는 못 본다 |

② `DeliverableRoutes.register` 에 `GET /api/deliverable/docs` → `[{no, name, mark, reason, auto, guess, manual}]`(18 은 수 0·0·0). ③ 화면 — `renderChecks` 가 먼저 `TB.api('/api/deliverable/docs')` 를 받아(실패하면 표시 없이 그린다) label 을 `<input> NN 이름 <span class="mark mark-full|part|none" title="근거 — 자동 a · 추정 g · 수동 m">●</span>` 로. `#docChecks` 아래 범례 한 줄 `<div class="msg">● 핵심 항목이 메타·분석에서 나온다 · ◐ 일부가 추정·수동 · ○ 핵심이 DB·소스에 없다 — 열마다의 등급은 00_작성안내.xlsx</div>`. CSS `.mark { margin-left: 4px; cursor: help; } .mark-full { color: #3a3; } .mark-part { color: #c90; } .mark-none { color: var(--muted); }`. ④ `.grid` 22 → `grid-template-columns: 1fr`(D6). ⑤ 시험 — `GradesTest.docMarksArePinned`: 12개 번호순, mark ∈ {●,◐,○}, reason 비지 않음, ● = {02,03,08,10,11,18} 정확히 · `DeliverableRoutesTest`: GET 12 건, 02 의 `auto == 5`. `SmokeHtmlUnitTest.deliverableGuideSwitchesDialect`: `#docChecks .mark` 12개, 글이 ●/◐ 이고 `title` 에 `자동` 이 든다(18 제외), 02 는 ●·05 는 ◐. `ToolsFolderTest.deliverableCardsAreOneColumn`: `.grid {` 규칙 글에 `grid-template-columns: 1fr` 가 있고 `280px` 가 없다(HtmlUnit 은 CSS 를 안 재서 글로).
- **검증**: 위 넷 초록. **닫힘**: 02 를 ◐ 로 바꾸면 `GradesTest` 빨강 · `.grid` 를 되돌리면 `ToolsFolderTest` 빨강 · 라우트를 빼면 스모크 빨강.
- **의존**: S1(같은 파일 — 충돌 피함).

### S5 = 1-31a `Validation` 핵심 + `DdlReader` CHECK

- **대상**: `core/gen/Validation.java`(신설) · `core/gen/DdlReader.java` · `ValidationTest.java`(신설) · `DdlReaderTest`(있으면; 없으면 `ValidationTest` 에).
- **변경**: ① 

```java
/** 테이블 제약 → Bean Validation 어노테이션·javadoc 조각(1-31). DTO 생성기(1-31b)와 CRUD VO(1-31d)가 같이 쓴다 */
public final class Validation {
    public enum Ns { JAVAX("javax.validation.constraints"), JAKARTA("jakarta.validation.constraints"); public final String pkg; }
    public static Ns nsOf(String framework)          // "egov5" → JAKARTA, 그 밖·null → JAVAX
    /** annotations 는 import 없이 단순 이름(@Size(max = 30)), imports 는 FQN. notes 는 javadoc 뒤에 「 · 」 로 잇는 조각, todos 는 // TODO */
    public record Result(List<String> annotations, List<String> notes, List<String> todos, Set<String> imports)
    public static Result of(Table t, Column c, String javaType, String dialect, Ns ns)   // D7·D8
    /** 조건 글 → 규칙. 못 풀면 null */
    static Rule parse(String condition)
    sealed interface Rule { record Range(String column, BigDecimal min, boolean minIncl, BigDecimal max, boolean maxIncl) implements Rule {} record InSet(String column, List<String> values) implements Rule {} }
    static List<String> columnsIn(String condition, Table t)   // Definitions 의 낱말 정규식 꼴
}
```

`javaType` 은 `TypeMapping.javaType` 이 준 글(FQN 일 수 있다 — 마지막 조각으로 가른다). 어노테이션 순서 고정: NotNull/NotBlank → Size → Digits → Min/DecimalMin → Max/DecimalMax → Pattern. 값 문자열의 `'` 는 벗긴다. `@Pattern` 의 regexp 는 자바 문자열로 이스케이프(`\\` 두 번). ② `DdlReader` — 표 단위 `CONSTRAINT name CHECK (…)`·`CHECK (…)` 줄과 컬럼 항목 안 `CHECK (…)` 를 괄호 균형으로 잘라 `Check(name 또는 null, 안쪽 글)` 로 모아 `withChecks`. `TYPE_END` 는 그대로(타입 끝). ③ `ValidationTest` — 표 하나(`users` 꼴: `USE_YN CHAR(1) NOT NULL DEFAULT 'Y'`, `LOGIN_ID VARCHAR(50) NOT NULL`, `EMAIL VARCHAR(200)`, `AMT NUMBER(12,2) NOT NULL`, `QTY NUMBER(10,0)`, PK `USER_ID NUMBER(19,0) NOT NULL`, UNIQUE LOGIN_ID, FK DEPT_ID → DEPT(ID))로 단언: USE_YN 은 `@Size(max = 1)` 만(DEFAULT → NotBlank 없음) · LOGIN_ID `@NotBlank`·`@Size(max = 50)`·note `UNIQUE` · EMAIL `@Size` 만 · AMT `@NotNull`·`@Digits(integer = 10, fraction = 2)` · USER_ID 는 PK 라 `@NotNull` 없음 · DEPT_ID note `FK → DEPT(ID)` · oracle 방언이면 LOGIN_ID note 에 `바이트` · `imports` 가 ns 의 pkg 로 시작. CHECK 방언 픽스처(조건 글 → 결과): Oracle `QTY BETWEEN 0 AND 100` → `@Min(0)`·`@Max(100)` · `"USE_YN" IN ('Y', 'N')` → `@Pattern(regexp = "^(Y|N)$")` + note · PG `(status = ANY (ARRAY['A'::bpchar, 'B'::bpchar]))` → Pattern · `(qty >= 0)` → `@Min(0)` · `(amt > 0)`(BigDecimal) → `@DecimalMin(value = "0", inclusive = false)` · MSSQL `([QTY]>=(0) AND [QTY]<=(100))` → Min·Max · `([USE_YN]='Y' OR [USE_YN]='N')` → Pattern · Maria `` `qty` > 0 `` → `@Min(1)` · 두 컬럼 `START_DT <= END_DT` → 둘 다 note `CHECK: …` + todo · `"COL" IS NOT NULL` → 아무것도 없음 · 값에 `.`·`|` 든 IN → 이스케이프. `DdlReader`: `CREATE TABLE T (A INT CHECK (A > 0), B CHAR(1), CONSTRAINT CK_B CHECK (B IN ('Y','N')))` → checks 2(이름 null·`CK_B`).
- **검증**: `-Dtest='ValidationTest,DdlReaderTest,DdlCorpusTest'`(corpus 는 `--full` — 여기선 `ValidationTest,DdlReaderTest` 만) 초록. **닫힘**: `of` 에서 PK 제외를 빼면 USER_ID 단언 빨강 · `parse` 의 `ANY (ARRAY` 분기를 빼면 PG 픽스처 빨강.
- **의존**: S0.
- **실패 사다리**: `DdlCorpusTest.unreadableLinesBaseline`(150~157, `CorpusFiles.baseline("ddl-unreadable", …)`)이 바뀌면 — CHECK 줄이 전엔 「못 읽은 줄」 로 세어졌다가 이제 읽힌 것이라 B 수가 **준** 것이면 baseline 갱신(diff 요지 이력에, 「새로 고쳐져도 빨강」 규칙). 늘었거나 다른 종류면 멈춘다. `--full` 에서만 돈다.

### S6 = 1-31b DTO 생성기 + 골든 + 시험 의존성

- **대상**: `core/gen/DtoGenerator.java` · `pom.xml` · `DtoGeneratorTest.java` · `DdlCorpusTest.java`(`-cp`) · `golden/gen/*-{record,bean,egovvo}.java` 9.
- **변경**: ① `Options` 에 `Validation.Ns validation`(null = 끔) — 7-인자 생성자 추가, 기존 6-인자 보조 생성자는 `null`. ② `generate` — 컬럼마다 `Validation.of(t, c, javaType, o.dialect(), o.validation())`(null 이면 빈 Result). `Field` 에 `List<String> annotations`·`List<String> notes`·`todos`. imports 에 `r.imports()` 합침. 렌더 — RECORD: 컴포넌트 앞에 어노테이션 한 줄씩 `        @NotBlank` 꼴, `// TODO` 줄은 todos 를 ` / ` 로 이어 하나, 클래스 javadoc `@param name doc · note1 · note2`(doc 이 없고 note 만 있어도 `@param name note`) · BEAN/VO: `    /** doc · note */` 뒤 어노테이션 줄들 `    @Size(max = 30)` 그리고 `private …; // TODO …`. ③ pom `<dependency>` 둘 `scope test`(D10). ④ `DtoGeneratorTest` — `compile` 에 `"-cp", System.getProperty("java.class.path")`; `goldenAndCompiles` 는 `validation = Validation.Ns.JAVAX` 로 돌리고 골든 9 를 `-Dgolden.update=true` 로 갱신 **한 번** 뒤 diff 를 눈으로: `products` 의 `code` → `@NotBlank`·`@Size(max = 30)`, `price` → `@Digits(integer = 10, fraction = 2)`(DEFAULT 라 NotNull 없음), `created_at` 없음, `category_id` note `FK`; `users` 의 `login_id` → `@NotBlank`·`@Size(max = 50)`·note `UNIQUE`, `email` `@Size(max = 200)`, `use_yn` `@Size(max = 1)`; `ORDER_ITEMS` 의 `QTY`·`AMOUNT`·`PRODUCT_ID` → `@NotNull`·`@Digits`, `ORDER_ID`·`LINE_NO` PK 라 Digits 만. 새 시험 `validationOffHasNoAnnotations`: `validation = null` → 본문에 `@` 로 시작하는 줄 0·`validation.constraints` 없음 · `jakartaNamespace`: JAKARTA 면 `import jakarta.validation.constraints.` · 기존 javac 시험 둘도 `-cp`. ⑤ `DdlCorpusTest.dtosCompile` 도 `-cp` + `validation = JAVAX`.
- **검증**: `-Dtest='DtoGeneratorTest'` 초록 → `bash scripts/offline-build.sh`(네트워크, `m2/` 채움) 끝 줄 초록. **닫힘**: `Validation.of` 호출을 빼면 골든 9 빨강 · pom 의존성을 빼면 javac 빨강(「끄면 지금과 같다」 는 ④ 의 off 시험).
- **의존**: S5.
- **실패 사다리**: `jakarta.validation-api:3.0.2` 좌표를 못 풀면 `3.0.0` · `validation-api:2.0.1.Final` 못 풀면 `2.0.0.Final`.

### S7 = 1-31c API·CLI·화면

- **대상**: `web/GenRoutes.java` · `cli/DocCommands.java` · `tools/db_browser.html` · `GenRoutesTest` · `SmokeHtmlUnitTest` · `DocCliTest`(있으면 한 줄).
- **변경**: ① `DtoRequest` 에 `Boolean validation`; 라우트에서 `Validation.Ns ns = Boolean.FALSE.equals(req.validation()) ? null : Validation.nsOf(profile.framework())`(프로필은 skipTokens 를 읽는 자리와 같은 활성 프로필) → `Options` 7-인자. ② CLI `Dto` 에 `@Option(names = "--no-validation", description = "검증 어노테이션을 안 붙인다")` → body `validation: false`. ③ 화면 공통 줄(115~118) 끝에 `<label><input type="checkbox" id="dtoValid" checked> 검증 어노테이션</label>`; `dtoBody` 에 `validation: $('dtoValid').checked`(둘 다 탭). `#dtoDialectHelp` → `방언은 DATE 타입과 길이 안내(oracle·tibero 는 바이트)를 가른다 …`(기존 뒷글 유지). ④ `GenRoutesTest` — 기본 요청에 `@NotBlank`·`@Size(max = 1)`(USE_YN)·`import javax.validation.constraints.` 가 있고 `"validation": false` 면 `@` 줄 없음. `SmokeHtmlUnitTest.dbBrowserMakesDtoFromDdl` — `#dtoOut` 에 `@Size(max = 50)` · `#dtoValid` 끄고 다시 만들면 없음 · `dbBrowserDtoSaveUsesShownResult` 는 그대로 초록(요청에 validation 이 함께 저장된다).
- **검증**: `-Dtest='GenRoutesTest,SmokeHtmlUnitTest#dbBrowser*,DocCliTest'` 초록. **닫힘**: 라우트가 `req.validation()` 을 무시하게 하면 ④ 「false 면 없음」 빨강.
- **의존**: S6.

### S8 = 1-31d CRUD VO

- **대상**: `core/gen/GenModel.java` · `templates/gen/egov35/VO.java.ftl` · `golden/gen/egov5/EmpHistVO.java` · `golden/gen/model.json` · `GenModelTest`·`GenTemplatesTest`.
- **변경**: ① `GenModel.of` — `vars.get("valid")` 가 `"true"` 면 `Validation.Ns` 를 `vars.ee`(`jakarta` → JAKARTA, 그 밖 JAVAX)로 잡고 필드마다 `Validation.of(...)` → 필드 map 에 `annotations`(List<String>)·`doc`(comment + notes 를 ` · ` 로) 추가, `imports` 에 합침. `valid` 가 아니면 `annotations: []`, `doc = comment`. `comment` 는 그대로(JSP 라벨). ② `VO.java.ftl` — 7~11 의 `hasSize`·Size import 블록 삭제(imports 에 든다), 18~22 를

```
    /** [=f.doc] */
<#list f.annotations as a>
    [=a]
</#list>
    private [=f.javaType] [=f.name];
```

③ 골든 — `egov5/EmpHistVO.java`: JOB_ID `@NotBlank`·`@Size(max = 10)` + note 바이트(dialect oracle 이면) · DEPT_NM `@Size(max = 30)` · SALARY `@Digits(integer = 6, fraction = 2)` · CLASS `@Size(max = 5)` · EMP_NO·START_DATE PK 라 EMP_NO `@Digits(integer = 6, fraction = 0)` 만 · NOTE 없음. import 가 `jakarta.validation.constraints.{Digits,NotBlank,Size}`. `egov35`·`egov4` 골든 **불변**. `model.json` 은 키 둘이 더해져 바뀐다(의도). ④ `GenTemplatesTest`·`GenModelTest` 갱신은 diff 가 ③ 과 같을 때만.
- **검증**: `-Dtest='GenModelTest,GenTemplatesTest,GenerateRoutesTest'` 초록, `egov35`·`egov4` 골든 diff 0. **닫힘**: `valid` 분기를 늘 참으로 하면 egov35 골든 빨강.
- **의존**: S5(S6 과 독립).

### S9 마무리

CLAUDE.md 「마무리」 1~8 그대로 + D12(머지). PR 본문 표는 S1~S8 행마다 「무엇을 닫나 / 걸리는 축 / 강제 지점」. 이력에 「정한 것(계획 밖)」·보정 셋(2.1).

## 5. 금지 사항

- `pure/` · `sql_snippets.html` 의 복사 코드 · `XlsxFiller.writeTemplate` 너비 식 · `TableXlsx` 의 클램프(6~80)·`chars()` · `/api/sql/run`·`export`·`SqlRoutes`·`core.sqlrun`·`MetaRoutesTest:113·115` · 프로필 YAML 스키마(`Profile` 레코드에 키 추가 금지) · `templates/gen/*/set.yaml` 의 `valid` 값 · `golden/table/*.json`·`golden/gen/egov35/*`·`golden/gen/egov4/*`·`golden/deliverable/filled-*.json`(불변) · 완료 행(PLAN 596·597·683 등).
- 런타임 의존성 추가 금지(시험 범위 둘만, D10). 어노테이션 스텁 소스로 javac 를 통과시키지 않는다.
- 시험을 고쳐 통과시키지 않는다 — 고쳐도 되는 것은 이 문서가 이름을 댄 것뿐(`DtoGeneratorTest` 골든·`-cp`, `DdlCorpusTest` `-cp`, `GenModelTest`·`GenTemplatesTest` egov5·model.json, 스모크 단언 추가).
- 화면에 스냅샷 실측 정확도·카드 접기·차례·문서 고르기 UI 를 더하지 않는다. 다른 화면의 `.grid` 를 건드리지 않는다.
- 복사 성공 글에 「복사했다」·「복사함」 을 쓰지 않는다. `execCommand`·`clipboard.writeText` 를 `common.js` 밖에 새로 쓰지 않는다.
- `common.js` 에 fetch·async·`?.`·`??` 를 쓰지 않는다(Rhino).
- 로그·오류 글에 SQL·코드·DDL 본문을 남기지 않는다(규칙 3). 127.0.0.1 밖 호출 금지(규칙 1).

## 6. 최종 검증

- 청크마다 `bash scripts/verify.sh` 초록 뒤 커밋(훅 순서 — 따로).
- 번들 끝: `bash scripts/gate-probe.sh` 여덟 산다(url-password 패치가 안 맞으면 다시 뜬다) · `bash scripts/offline-build.sh --check` 초록(S6 뒤 `m2/` 가 채워져 있어야) · `bash scripts/verify.sh --full` 초록(컨테이너 아홉 + 표본; 메모리 10GB 여유 확인 뒤) · `git push` 따로 · PR → CI 여덟 초록 → AI 리뷰 처분(버그만 고침, 나머지는 PR 본문에 처분) → 머지 → `git checkout main && git pull`.
- 회귀: `DeliverableRoutesTest`(00·01~11·18 생성) · `XlsxFillerTest` 골든 · `TableXlsxTest` 너비 · `GenTemplatesTest` egov35·egov4 · `GeneratorJavacTest`(V-17, `--full`) · `DdlCorpusTest` · `MetaRoutesTest.snapshotFlow`.
- 수동(사람 몫으로 적는다, 실브라우저): 산출물 화면 카드 한 줄·●◐○ 마우스 올림 · 복사 버튼 누르면 글이 파랗게 선택되고 「복사됨」 · 엑셀에서 열 너비·줄바꿈 · db_browser DTO 체크박스.

## 7. 중단 조건

다음이면 임의로 풀지 말고 멈추고 보고한다. 보고: 어느 스텝, 무엇이 예상과 달랐는지, 선택지.

- 2장의 구조·줄 번호가 실제와 다르다(같은 이름의 함수로 다시 찾되 함수가 없으면 멈춘다).
- 검증이 2회 연속 실패하고 원인이 그 스텝 범위 밖이다.
- 5장을 어기지 않고는 못 간다.
- 스텝이 적지 않은 파일을 3개 이상 고쳐야 한다.
- 「불변」 골든(`golden/table/*`·`egov35`·`egov4`·`filled-*`)이 바뀐다.
- S5: `DdlCorpusTest` 골든이 `checks` 말고 다른 칸까지 바뀐다.
- S6: `offline-build.sh` 가 새 jar 둘을 못 받는다(사다리 좌표 둘까지).
- S2: HtmlUnit 에서 `execCommand('copy')` 가 예외를 던져 `opensWithoutScriptErrors` 가 빨갛다(try 로 감쌌는데도).
- 번들 모드 규칙대로 행당 「정한 것」 둘까지, 넘으면 `wip/<청크>` 로 빼고 다음 행.

## 8. 불확실 항목 — 실행 세션이 S0 에서 다 풀었다(2026-10-07)

- PR #49 머지 `b63f450` · `DdlReaderTest` 있음(`core/gen/`) · `DdlCorpusTest` 는 표 JSON 골든이 없고 `ddl-unreadable` baseline 만(S5 사다리) · `GenRoutes.register(app, dict, snapshots, Supplier<Optional<Profile>> active)` 55 — `framework` 는 72 의 `active.get().map(Profile::framework)` 로.
- HtmlUnit 4.11.1 실측(임시 시험, 지움): readonly textarea `focus(); select()` 뒤 `selectionStart,selectionEnd,activeElement.id` = `0,11,t` · `pre` 에 `Range.selectNodeContents` + `addRange` 뒤 `getSelection().toString()` = 그 글 · `document.execCommand('copy')` = `true`(안 던진다) · `typeof navigator.clipboard` = `undefined`, `navigator.clipboard = {…}` 대입으로 심어진다. → S2 실패 사다리 둘은 안 쓴다. 심지 않으면 `TB.copy` 는 execCommand 로 「복사됨」 을 낸다 — 스모크는 심어서 복사된 글까지 잰다.
- 순수본 유래 넷의 복사 함수는 전부 전역 함수(`<script>` 최상위, `onclick="copyEl('x')"`·`onclick="copyOut()"`) — 몸통만 바꾼다.
- `Grades` 02 자동 5(영문 DB명·테이블 소유자·영문 테이블명·테이블 볼륨·최초등록일) — S4 단언값 그대로.
- `XlsxFiller.fill` 의 `styles.get(col)` 은 양식 첫 행에 셀이 없으면 null(지금 코드가 null 을 건너뛴다) → wrap 스타일은 null 이면 `wb.createCellStyle()` 새로, 아니면 `cloneStyleFrom`.
- SXSSF `setColumnWidth` 는 시트 속성이라 행을 쓴 뒤 불러도 된다(`ResultTable` 은 메모리라 먼저 훑어 정해도 된다 — S3 은 먼저 훑는다).
