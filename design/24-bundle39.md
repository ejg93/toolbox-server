# 설계 24 — 번들 39: 유사 도구 비교에서 고른 업그레이드 11 (Fable, 2026-10-13)

원본은 설계 세션 플랜 파일 — S0 에서 옮김(2026-10-14).


이 플랜은 실행 담당 모델(Opus)이 그대로 따라 실행한다. 실행자는 탐색 과정을 모른다. 판단이 필요한 지점을 남기지 않았다. 번들 모드 규칙(CLAUDE.md 「번들 모드」·「청크 규칙」·「마무리」)을 따른다.

사용자가 2026-10-13 정한 것(뒤집지 않는다):
- 비교 문서 `design/compare-2026-10-14.md` 의 「업그레이드」 아홉 + 소품 둘(프로그램 수 열 · 안 쓰는 표 행 수 열). `notRegex` 는 안 한다.
- 근거 열(`ref:`)은 **칸만** 만든다. 가이드 항목 번호는 사람이 뒤에 채운다.
- 코멘트 덧씌움은 **논리명 변환 결과 재사용**(별도 CSV 아님).

## Context

2026-10-13 유사 도구 비교(tbls·SchemaSpy·Atlas·Semgrep·PMD·jQAssistant·MyBatis Generator)에서 「새 화면 아니고 있는 것에 칸·열·줄을 더하는」 것만 골랐다. 전부 산출물 xlsx·코드 검사 결과·반영 DDL 글·프로필 YAML 에 보이는 변화다. 새 의존성 0, 새 화면 0, `pure/` 안 건드림.

---

### 1. 목표와 범위

- **목표**: 정의서 02·03 이 코멘트 없는 표·컬럼을 논리명으로 채우고 「분류명」 을 프로필로 채운다 · 작성안내에 코멘트 채움률 두 줄과 이름 기반 관계 후보가 더해진다 · 반영 DDL `[확인]` 에 종류 꼬리표와 종류별 건수가 붙는다 · 코드 검사 규칙에 `fix`·`ref` 칸이 생겨 결과 표·xlsx 에 「권장 수정」·「근거」 열이 나오고 프로필로 폴더를 뺄 수 있다 · 생성기가 표마다 클래스 이름·빼는 컬럼을 받는다 · 분석 xlsx 에 「프로그램 수」·「행 수」 열이 생긴다.
- **In**: `S0` → `0-54` → `1-63` → `2-24` → `2-25` → `2-26` → `2-27` → `5-25a` → `5-25b` → `5-26` → `6-32` → `7-15` → 마무리. 가지 `work/2026-10-14`(이미 있다 — `15c043f` 비교 문서 커밋 위). 머지 위임 없음(PR 열고 멈춤).
- **Out**: 구조 린트(FK 인덱스 없음·고아 표 등 — 새 판정 묶음, 다음 번들) · 억제 주석(새 주석 문법) · 그림 글 복사 · 탭 검색 · `notRegex` · 줄 범위 조건 · PK 생성 꼴 · `ref:` 값 채우기 · 화면 CRUD 매트릭스에 프로그램 수(xlsx 만) · DTO 생성(db_browser)의 표별 재정의(생성기만) · 1-61 · `pure/`.

---

### 2. 현재 구조 요약(2026-10-13 실측)

경로는 `src/main/java/kr/ejg/toolbox/` 기준. 줄 번호는 main `5a14b03` + `15c043f` 기준.

#### 2.1 프로필(공통 — 2-25·5-26·7-15 가 다 건드린다)
- `core/profile/Profile.java` — record `Profile(name, project, connections, defaultConnection, scope, Deliverable deliverable, naming, codecheck, framework, output, generator, LogicalName logicalName)`(:13-25). `record Deliverable(String author, String org, String templateDir, String mapping, Scope filter)`(:73) · `record CodeCheck(Map<String,Boolean> groups, Map<String,Object> rules, String customRules)`(:85-91, compact 생성자가 null → 빈 맵, `LinkedHashMap` 으로 순서 유지) · `record Generator(String templateSet, String basePackage, String outDir)`(:96) · `record LogicalName(List<String> skipTokens)`(:99).
- 파서는 **Jackson YAML**(`core/profile/ProfileStore.java:29-34`, `FAIL_ON_UNKNOWN_PROPERTIES` 켬 → 레코드에 없는 키가 YAML 에 있으면 로드 실패 「모르는 키 X」:70-80). 레코드 컴포넌트 이름 = YAML 키. **새 키는 레코드 필드를 먼저 더해야 한다.**
- `new Profile.Deliverable(`·`new Profile.Generator(` 직접 호출은 src 에 **0건**(Jackson 만). `new Profile.CodeCheck(` 는 **14건**(main 3 — `web/CheckRoutes.java:293,302`·`ProfileStore.java:187`, 나머지 11 은 테스트).
- `ProfileStore.saveCodeCheck`(:125-202)는 groups·rules 두 줄만 갈아 끼우고 다시 읽어 `expected.equals(after)` 비교(:187-193) — 그때 `new Profile.CodeCheck(merged, mergedRules, customRules)`.
- 예시 `profiles/example.yaml` — `deliverable:`:91-103 · `codecheck:`:119-125 · `generator:`:142-145 · `logicalName:`:150-152. `profiles/demo.yaml`·`docker-ora.yaml` 도 같은 키.
- 시험 `core/profile/ProfileStoreTest` — `exampleMapsEveryField`(:33-54, 예시 YAML 의 모든 필드 단언) · `exampleRoundTrips`(:24) · `unknownKeyFails`(:57).

#### 2.2 산출물(2-24~2-27)
- `core/deliverable/DeliverableService.build`(:71) — `req.docs()` 에 05·06·07 이 **하나라도 있을 때만**(:79-83) `dicts = dict.load()` · `logical = LogicalRun.run(ColumnInputs.fromSchemas(snapshot), dicts, req.skipTokens(), req.orgFirst())`. 02·03 만 고르면 `logical == null`. `Definitions.build(snapshot, options, pii, inferred)`(:84 호출, Definitions :84 정의)는 logical 을 받지 않는다. `inferred = req.joins().isEmpty() ? Relations.Result.empty() : Relations.infer(snapshot, req.joins())`(:86). `writeGuide(guide, order, docs, files, skipped, src, tables, inferred, coverage, missing)`(:136-138 호출, :167 정의) — 「요약」 행 :182-196(스냅샷·찍은 시각·접속·거름·표 수·만든 문서·건너뛴 것·05·07(coverage 있을 때)·안내·관계 추정의 한계), 「관계 후보」 시트 :197-200,207 열 `표A·컬럼A·표B·컬럼B·문장 수·뷰 여부`. 시트 순서 :201-208 항목 → 요약 → (미등록 약어) → 관계 후보. `record Request(...)`(:28, 5인자 생성자 :38 — `DeliverableServiceTest:30`·`DbCorpusBase:1277` 이 쓴다).
- `core/deliverable/Definitions.java` — `record Options(author, org, dept, bizArea, dbDesc, dbName, logicalDbName, os)`(:29-30) · `NO_COMMENT = "(코멘트 없음)"`(:37) · `COLS_02`(:44-46, 「분류명」 = index 14) · `build(...)`(:84) → `d01·d02·d03·d04·d10·d11` · `d01`(:94) 행 index 7 「업무분류체계」 `""`(:104 주석 「사람이 채운다」) · `d02`(:125) 행 :134-135 — 「한글 테이블명」 `korOrMark(t.comment())`, 「분류명」 index 14 `""` · `d03`(:~140) 행 :153 「한글 컬럼명」 `korOrMark(c.comment())` · `korOrMark(String)`(:366 비면 NO_COMMENT) · `kor(String)`(:371 비면 "") · `d04`(:223) 선언 FK 행 :228-240 + strong :242-259(근거 `"추정(조인 n문장)"`, `estimated = {"근거": guessed}`). 키 꼴 `piiKey`(:90, 대문자 `TABLE.COL`).
- `core/deliverable/Grades.java` — `AUTO·GUESS·MANUAL`(:13-15) · `record Grade(doc, column, level, how)`(:17) · `build()`(:72-161) 고정 표, `add(g, doc, column, level, how)`(:163) · 02 「한글 테이블명」 GUESS :91 「테이블 코멘트 — 없으면 (코멘트 없음) 표시…」 · 03 「한글 컬럼명」 GUESS :106 · 01 「업무분류체계」 MANUAL :82 · **02 「분류명」 Grade 행 없음** · `DocMark` 02 reason :35 「…업무 분류·담당자는 수동」 · `counts(doc)`(:64). 등급표 시트는 `DeliverableService.writeGuide`:169-181 — 문서마다 `Grades.of(no)` 중 `d.columns()` 에 있는 열만, GUESS 면 `d.estimated().getOrDefault(col,0)`. `Doc.estimated` 채우는 문서: 03 `{"개인정보 여부": n}`(:164) · 04 `{"근거": n}`(:260) · 02 는 `Map.of()`.
- `core/logical/LogicalRun.java` — `run(List<ColumnInput>, Dictionaries, List<String> skipTokens, boolean orgFirst)`(:61) → `Result(rows, tableRows, rank, usedTokens, usedWords, stats)`(:43). `Row(owner, table, col, name, src, missing, …)`(:21) · `TableRow(owner, table, name, src, missing)`(:29). `src` ∈ `given·user·word·multi·mix·none`(`Converter.java:18,114`) — `none` 이면 name = 원문(영문). owner·table·col 은 trim 한 원문(대문자 아님).
- `core/deliverable/Relations.java` — `Inferred(childTable, childCols, parentTable, parentCols, statements)`(:27) · `Candidate(tableA, colA, tableB, colB, statements, boolean view)`(:34) · `Result(strong, weak)` + `empty()`(:37-46) · `infer(snapshot, joins)`(:51) — 표 맵 대문자(:52-55, 겹치면 첫 것) · 선언 FK 쌍 모음(:56-61) · 조인 묶기·강/약 판정(:63-119) · strong 정렬(:122). 도우미 `pk(Table)`(:127 대문자 PK 이름) · `isView`(:136) · `pair`(:140) · `up`(:144).
- `core/meta/Table.java`(:11-24) `Table(schema, name, type, comment, columns, PrimaryKey pk, fks, uniques, indexes, Long rowCount, createdAt, lastDdlAt, checks)` — rowCount null = 모름. `Column(name, ordinal, nativeType, jdbcType, Long length, Integer precision, Integer scale, boolean nullable, defaultValue, comment, domain)`(`Column.java:7-18`). `PrimaryKey(name, List<String> columns)`, pk 가 null 일 수 있다.
- 시험·골든: `DefinitionsTest.goldenOnPostgresVendorSnapshot`(:58-63, `golden/deliverable/pg-01·02·03·04·10·11.json`) · `r6MarkMissingCommentButKeepRow`(:104, NO_COMMENT 단언) · `r13r14Relations`(:152) · `GradesTest`(:52 02 등급 수 `(5,2,8)` 못박음, :50 ● 집합) · `RelationsTest`(여섯 경우 + `doc04CarriesInferredRows`:111) · `DeliverableServiceTest`(파일·건너뜀만) · `XlsxFillerTest.committedTemplatesAndMappingMatchValueColumns`(:196-205 — COLS 를 바꾸면 양식 재생성 요구. **이 번들은 COLS 를 안 바꾼다**). 골든 갱신 `-Dgolden.update=true`(`GoldenFiles.java:36`). 작성안내 xlsx 골든 없음.

#### 2.3 반영 DDL(1-63)
- `core/gen/AlterGen.java` 내부 클래스 `Run` — `stmt(out, s)`(:135) · `check(out, why)`(:140 `"-- [확인] " + why`, review++) · `hand(out, why)`(:145 `"-- [손] "`, review++) · `commented(out, why, s)`(:151 check 뒤 `"-- " + s + ";"`). 호출: :181 표 삭제 `commented(…"데이터가 사라진다 — 표 "…)` · :274 RENAME 권고 `check(…"같은 표에 컬럼 삭제와 추가가 같이 있다 — …RENAME 으로")` · :277 컬럼 삭제 `commented(…"데이터가 사라진다 — 컬럼 "…)` · :294 기본값 없는 NOT NULL 추가 `check(…"NOT NULL 컬럼 … 기존 행이 있으면 실패한다, DEFAULT 를 넣는다")` · :319 타입 축소 `check(…"타입이 줄거나 바뀐다 — 값이 잘리거나…")` · :322 NOT NULL 변경 `check(…"NOT NULL — NULL 행이 있으면 실패한다")` · :349 PG 계열 `check(…"타입 계열이 바뀐다 — …USING…")` · :340-341,397,421,449,530 `hand(...)` 이름 모름 넷 + MSSQL 기본값. 머리 주석 :193-195 `"-- 반영 DDL — 원본 … → 대상 … · 표 추가 n · 삭제 n · 변경 n · 문장 n · 확인 m"`. `Result(sql, warnings, statements, review, addedTables, removedTables, changedTables)`(:40, 설계 23 [고정]).
- 골든 `golden/gen/alter-{mariadb,mssql,oracle,postgresql,tibero}.sql` 5개(1행 머리 「확인 5·6·5·5·5」, mssql 32행 `[손]` 하나 더). `AlterGenTest.goldenPerTarget`(:138-150, `review() >= 4` 도 단언). 화면 `db_browser.html:454-456` 요약 `'… 확인 ' + r.review …` · CLI `cli/MetaCommands.java:195-197`.

#### 2.4 코드 검사(5-25·5-26)
- `core/check/Rule.java` `record Def(id, group, severity, kind, List<String> globs, regex, List<String> flags, Boolean skipComments, message, Map<String,Object> params, Boolean enabled)`(:26-27) · compact 생성자 :31-36 · `withEnabled`(:47-48 — 11인자 호출) · `override(Object)`(:51-67 — Map 키 `severity, globs, regex, flags, skipComments, message, enabled, params`, 11인자 호출 :64) · `AbsentRule.java:31-32` 도 11인자 probe. **Def 를 직접 만드는 곳 셋.**
- `core/check/RuleSet.java` — `YAML = new ObjectMapper(new YAMLFactory())`(:23, 기본 `FAIL_ON_UNKNOWN_PROPERTIES=true` → yaml 에 모르는 키가 있으면 로드 실패) · `record RulesFile(List<Rule.Def> rules)`(:26) · `load(Profile, Path)`(:58) 순서 내장 → customRules(:69-77) → override(:82) → 켬 판정 → `validate`(:97-114) → `build`(:116) · `globs()`(:153).
- `core/check/Finding.java:7` `Finding(file, int line, group, rule, severity, excerpt)` — message 없음.
- `web/CheckRoutes.java` — `GET /api/check/rules`(:44-72, 항목 `id, group, severity, kind, globs, regex, flags, message, params, enabled, fileLevel, groupEnabled, ruleEnabled`) · 실행 응답 findings = Finding 그대로(:122-130) · xlsx(:190-217) — 이력 `store.findings(id)`(:210), `load(p,null,null).defs()` 로 `id → message` 맵(:201-208), 열 `파일·줄·묶음·규칙·등급·설명`(:215), 행 :213 · `load(Profile, groups, overrides)`(:292-308, `new Profile.CodeCheck(` :293,302).
- `src/main/resources/tools/code_check_ext.js` — `rules` 배열(:13, `loadRules`:25) · 결과 표 `render(p)`(:420-442, 열 `['파일','줄','묶음','규칙','등급','원문']`:428, 행 :429) · `copy(p)`(:472-476 TSV 머리 `파일\t줄\t묶음\t규칙\t등급\t원문`) · 규칙 패널 message :139,142.
- `core/fs/LocalFiles.java` — `list(String dir, List<String> globs, int maxFiles)`(:144) — globs 는 `PathMatcher("glob:"+g)` 로 **파일 이름에만**(:149-154,171-172) · 건너뛰기 `SKIP_DIRS = {.git, target, node_modules}`(:47) + `hidden(d)`(:160,192-202) · `Listing(files, truncated)`(:68) `Entry(rel, size, mtime)`(:65). 부르는 곳: `CheckRunner.java:50`(globs = `rules.globs()`, `/` 든 글롭은 마지막 조각 :45) · `AnalyzeRunner.java:93` · `FolderDiff.java:37-38` · `FsRoutes.java:53`.
- `src/main/resources/check/rules.yaml` — 83 규칙, 칸 `id group severity kind globs regex flags skipComments message params`. security 묶음 :172-392 즈음(regex 29 + absent 4), 2차 머리 주석 :218-220. `rules/example.yaml` 은 프로필이 가리키나 **파일 없음**(`.gitignore:21-22` 가 `rules/example.yaml` 은 허용).
- 시험: `CheckRulesTest`(픽스처 `fixtures/check/<묶음>/pos*·neg*`, 골든 `golden/check/fixtures.json`, `customRulesFileAddsAndReplaces`:122) · `PureRulesJsonTest`(:30-63 — 추출 키 `id, group, severity, langs, regex, flags, skipComments, max, message` 만, 54 고정 :61 — fix·ref 는 안 뽑으니 영향 없음) · `CheckRoutesTest.folderRunHistoryExport`(:85) · `fileLevelFindingsShowFileInExport`(:119, xlsx 셀 1·3) · `SmokeHtmlUnitTest.codeCheckFolderRun`(:1511, td `[1]=줄`·`[3]=규칙`, 복사본 `"파일\t줄\t"` 시작 :1583) · `CheckStoreTest.runnerOverFixtureFolder`.

#### 2.5 생성기(7-15)
- `core/gen/GenModel.java` — `Options(basePackage, module, List<String> skipTokens, Map<String,String> logicalNames, dialect)`(:28) · `of(Table t, Options o, Map vars, TypeMapping types)`(:49) · 클래스 이름 `DtoGenerator.className(t.name(), o.skipTokens())`(:62) · 컬럼 루프 :81-124(`f` 키 `column name Name javaType string pk nullable comment annotations doc length`, `fields`·`pk`·`nonPk`) · `pk.size() != pkCols.size()` 면 건너뜀(:125-128).
- `core/gen/Generator.java` — `run(TemplateSet set, List<Table> tables, GenModel.Options base, Map<String,Map<String,String>> logicalNames, TypeMapping types, Path outDir, LocalFiles files, encoding, lineEnding, JobContext ctx)`(:41-42) · 표마다 `GenModel.Options` 다시 만듦(:54-55).
- `web/GenerateRoutes.java` — `GenerateRequest(snapshotId, List<TableRef> tables, templateSet, basePackage, module, outDir, dialect)`(:33-34) · 프로필 `p.generator()` 로 `pick(req, g)`(:66-69,159-162) · `skip = p.logicalName().skipTokens()`(:139) · `new GenModel.Options(basePackage, module, skip, Map.of(), dialect)`(:143).
- 시험: `GenModelTest.golden`(`golden/gen/model.json`) · `GenTemplatesTest`(`golden/gen/{egov35,egov4,egov5}/*`·`mapper-{방언}.xml`) · `GenerateRoutesTest`(:52-54 프로필 YAML 문자열, `generatesWithProfileDefaults`:107).

#### 2.6 프로그램 분석(6-32)
- `web/AnalyzeRoutes.java` xlsx(:189-273) — CRUD목록 `CrudViews.longRows(m.rows())`(:222-223) · CRUD모듈 `mm = CrudViews.moduleMatrix(m.rows())`(:224-238, 열 `표` + `mm.modules()`, 행 `r.table()` + `cells`) · 정합성(:263-273, 열 `구분·이름·스키마·종류·프로그램 수·사유`:272, 「안 쓰는 표」 행 :268 `("안 쓰는 표", x.table(), x.schema(), x.type(), "", "")`).
- `core/analyze/CrudViews.java` — `LongRow(program, url, module, table, crud)`(:20) · `ModuleRow(table, Map<String,String> cells)`(:24) · `ModuleMatrix(modules, rows)`(:30) · `longRows(List<ProgramRow>)`(:58-66, 프로그램 = `className.method`) · `moduleMatrix(...)`(:68-78).
- `core/analyze/Consistency.java` — `Unused(schema, table, type)`(:34) · 만드는 곳 :90-96 `new Unused(s.name(), t.name(), t.type())` — 그 자리에 `Table t` 가 있다 · 정렬 :99.
- 화면 `program_analysis_ext.js:534` `TB.table($('conUnused'), ['스키마','표','종류'], …)`.
- 시험: `AnalyzeRoutesTest.exportXlsx`(:286-362 — CRUD모듈 머리 `"표|bbs|other"`:329 · 정합성 머리 `"구분|이름|스키마|종류|프로그램 수|사유"`:346) · `ConsistencyTest.report`(:50 `new Consistency.Unused("APP","COMTNUNUSED","VIEW")` 같음 비교) · `CrudViewsTest.longRowsAndModuleMatrix`(:29-45 `ModuleRow` 같음) · corpus `golden/corpus/analyze-egov-consistency.json`(건수만).

#### 2.7 런처(0-54)
- `src/main/resources/tools/index.html:67` 코드 검사 카드 `.desc` — 「…묶음별 켜고 끄기·이전 실행 비교·x…」. 「이전 실행 비교」 는 5-24b 에서 지운 기능.

#### 2.8 검증 레인
- `AlterGen`·골든 `alter-*.sql` 은 **db 레인**(DDL) · `rules.yaml`·`code_check_ext.js`·`program_analysis_ext.js` 는 **corpus 레인**(규칙·화면 JS). 둘 다 마무리에서 `--db --corpus`(약 25분, 한 번). 청크 중간은 빠른 검증만.

---

### 3. 설계 결정

**공통**
- **[고정] 프로필 새 키 셋** — `deliverable.business`(2-25) · `codecheck.exclude`(5-26) · `generator.tables`(7-15). 전부 레코드 필드 추가 + `profiles/example.yaml` 주석 예시 + `ProfileStoreTest.exampleMapsEveryField` 단언. demo·docker-ora 는 안 고친다(키 없으면 null → 빈 값).
- **[고정] 레코드 호환 생성자** — `Profile.CodeCheck` 는 4 컴포넌트 canonical + **3인자 생성자**(`this(groups, rules, customRules, null)`)를 둬서 테스트 11곳을 안 고친다. `Deliverable`·`Generator` 는 직접 호출 0건이라 그냥 컴포넌트 추가.
- **[고정] COLS·양식·매핑 불변** — 정의서 열 이름·순서(`COLS_01~11`)·`templates/deliverable/example/*.xlsx`·`mappings/deliverable/example.yaml` 을 바꾸지 않는다. 바꾸면 `XlsxFillerTest` 가 양식 재생성을 요구한다. 새 값은 **이미 있는 빈 칸**에만 들어간다.
- **[고정] 골든 갱신은 diff 를 읽고** — `-Dgolden.update=true` 뒤 `git diff` 로 바뀐 줄이 그 청크의 의도(꼬리표·새 열·새 값)만인지 확인. 다른 줄이 바뀌면 중단 조건 ②.

**1-63 반영 DDL 꼬리표**
- **[고정] 종류 셋** — `삭제`(표·컬럼 삭제 `commented` :181·:277) · `데이터`(:294 기본값 없는 NOT NULL 추가 · :319 타입 축소 · :322 NOT NULL 변경 · :349 PG 계열) · `호환`(:274 RENAME 권고). `[손]` 은 그대로(종류 없음).
- **[고정] 꼴** — `-- [확인:삭제] …` · `-- [확인:데이터] …` · `-- [확인:호환] …`. `check(out, why)` 를 `check(out, Kind k, why)` 로(enum `Kind { DEL("삭제"), DATA("데이터"), COMPAT("호환") }`), `commented(out, why, s)` 는 `commented(out, Kind, why, s)`. 종류별 카운터 셋.
- **[고정] 머리 주석** — `… · 확인 m(삭제 a · 데이터 b · 호환 c) · 손 h`. `Result` 레코드는 **안 바꾼다**(설계 23 [고정], API·CLI·화면 그대로 — review 합은 같다). 종류별 수는 글에만.
- 버린 대안: `Result` 에 `Map<String,Integer>` 추가 — API 응답·CLI·화면 셋 다 건드리고 가치 하.

**2-24 코멘트 채움률**
- **[고정]** `Definitions` 에 `record CommentFill(int tables, int tablesWithComment, int columns, int columnsWithComment)` + `static CommentFill commentFill(List<Schema> sorted)`(비면 안 센다 — `korOrMark` 와 같은 「비었다」 기준: null 또는 trim 빈 글). `writeGuide` 「요약」 에 「표 수」 행 바로 뒤 두 행 — `["코멘트 채움(표)", "n / m"]` · `["코멘트 채움(컬럼)", "n / m"]`. 거른 뒤 스냅샷(`Definitions.sorted(snapshot)`) 기준.

**2-25 분류명 자동 채움**
- **[고정] 키** — `deliverable.business: { <업무명>: [<표 이름 접두 또는 정규식>…] }`. 규칙: 항목 글이 `^`·`$`·`.`·`[`·`(`·`*`·`\` 중 하나를 포함하면 정규식(대소문자 무시, 표 이름 전체 `matches`), 아니면 **접두**(대문자 비교 `startsWith`). 한 표가 둘 이상에 맞으면 YAML 에 먼저 적힌 업무. 안 맞으면 `""`(지금과 같음).
- **[고정] 어디에** — 02 「분류명」(index 14) 만. 01 「업무분류체계」 는 기관 BRM 이라 수동 그대로. `Definitions.Options` 에 `Map<String, List<String>> business` 컴포넌트 추가(기존 8인자 생성자는 호환 생성자로 유지 — `DefinitionsTest`·`DeliverableRoutes` 호출 안 고침). `Grades.build` 에 02 「분류명」 GUESS 「프로필 deliverable.business(업무명 → 표 접두·정규식)」 추가 → `GradesTest:52` 를 `(5,3,8)` 로(02 행 15개에 「분류명」 이 없었다 — §8). 02 `estimated = {"분류명": 채운 수}`. `DocMark` 02 reason 「…업무 분류·담당자는 수동」 → 「담당자는 수동, 분류명은 프로필 business 로 추정」.

**2-26 코멘트 덧씌움(논리명 재사용)**
- **[고정] 언제 변환을 돌리나** — `DeliverableService.build:79` 조건을 「05·06·07 **또는 02·03** 중 하나라도」 로 넓힌다. 사전이 비어 있으면(`dicts` 단어 0) 변환 결과 `src` 가 전부 `none` 이라 자연히 덧씌움 0.
- **[고정] 무엇으로 채우나** — 코멘트가 비었을 때만. 표는 `LogicalRun.Result.tableRows` 에서 `up(owner)+"."+up(table)` 또는 `up(table)` 키로, 컬럼은 `rows` 에서 `up(table)+"."+up(col)` 키로 찾는다(`Definitions.piiKey` 꼴과 같게). **`src` 가 `given·user·word·multi` 일 때만** 쓴다 — `mix`(영문 조각 남음)·`none`(원문) 은 안 쓴다(틀린 한글명이 산출물에 나가는 것보다 「(코멘트 없음)」 이 낫다 — 2026-09-27 사용자 「현장은 다른 것이 정상」 과 같은 결). 값 꼴은 조립명 그대로(꼬리표 없음). 등급표 「추정」 수로 드러난다.
- **[고정] 통로** — `Definitions.build(snapshot, options, pii, inferred, LogicalNames names)` 5인자 추가(4인자는 호환 — `names = LogicalNames.EMPTY`). `record LogicalNames(Map<String,String> tables, Map<String,String> columns)` + `static LogicalNames of(LogicalRun.Result r)`(null → EMPTY). `korOrMark` 를 `korOrMark(comment, fallback)` 로 — fallback 이 있으면 그것, 없으면 NO_COMMENT. 02 `estimated = {"분류명": …, "한글 테이블명": 덧씌운 수}` · 03 `estimated = {"개인정보 여부": …, "한글 컬럼명": 덧씌운 수}`. 「관련 엔터티명·연관 엔터티명·연관 속성명」(재사용 칸, `kor()`)도 같은 fallback 을 쓴다(한 표 안에서 한글명이 둘로 갈리지 않게).
- `Grades:91,106` how 글 → 「테이블 코멘트 — 없으면 논리명 변환(사전 전부 맞은 것만), 그것도 없으면 (코멘트 없음)」 / 컬럼 같은 꼴.
- 골든 `pg-02·03.json` — 픽스처(`golden/meta/postgres-vendor.json`)에 코멘트 없는 표·컬럼이 있고 테스트가 사전 없이 돌면 `names = EMPTY` 라 **골든 불변**. `DefinitionsTest` 에 새 케이스 — 사전 한 줄(`CUST → 고객`, `NM → 명`)로 `LogicalNames` 를 만들어 코멘트 없는 컬럼 `CUST_NM` 이 「고객명」, `mix` 컬럼은 NO_COMMENT 유지.
- 버린 대안: 별도 CSV(사용자 2026-10-13 결정) · `mix` 도 쓰기(영문 섞인 한글명).

**2-27 이름 기반 관계 후보**
- **[고정] 규칙** — 표 C 의 컬럼 c 와 표 P(≠C) 에서: P 의 PK 가 **단일 컬럼**이고 그 이름이 c 와 같고(대문자) `nativeType` 이 같고(대문자, null 은 같지 않음) `length` 가 같으면(둘 다 null 이면 같음) 후보. 선언 FK 가 이미 있는 (C,P) 쌍은 뺀다. 뷰는 양쪽 다 뺀다. **strong 에는 절대 안 넣는다** — tbls·SchemaSpy 가 이것을 「implied」 로만 다루는 것과 같다.
- **[고정] 담는 곳** — `Relations.Result` 에 `List<Named> named` 컴포넌트 추가(`record Named(childTable, childCol, parentTable, parentCol)`), `Result(strong, weak)` 2인자는 호환 생성자(`named = List.of()`)로 두어 `RelationsTest` 안 고침. `infer(snapshot, joins)` 는 joins 가 비어도 named 는 계산 — `DeliverableService:86` 의 `isEmpty() ? empty() : infer(...)` 를 `infer(snapshot, req.joins())` 하나로(infer 안에서 joins 비면 strong·weak 빈 목록).
- **[고정] 시트** — 「관계 후보」 열을 `표A·컬럼A·표B·컬럼B·문장 수·뷰 여부·근거` 로(열 하나 추가). 기존 weak 행은 근거 `조인`. named 행은 `(C, c, P, pkCol, "", "", "이름")`. 같은 (C,c,P,pkCol) 가 weak 에도 있으면 named 행은 **안 넣고** 그 weak 행 근거를 `조인·이름` 으로. 정렬: weak 먼저(지금 순서), named 는 childTable·childCol 순.
- 「관계 추정의 한계」 요약 글(:195-196)에 한 문장 추가 — 「이름 기반 후보는 컬럼 이름·타입이 다른 표 PK 와 같다는 것뿐이다 — 코드 조인 근거가 없다」.
- 버린 대안: tbls singular 전략(영어 복수형 전제) · named 를 04 에 싣기(근거 약함).

**5-25 규칙 `fix`·`ref` 칸**
- **[고정] Def** — `Def(id, group, severity, kind, globs, regex, flags, skipComments, message, params, enabled, String fix, String ref)` 13 컴포넌트. **11인자 호환 생성자** 추가(`fix = null, ref = null`) → `withEnabled`·`override`·`AbsentRule` probe 는 그대로 두되 **`withEnabled`·`override` 는 fix·ref 를 잃지 않게 13인자로 고친다**(probe 는 11인자 그대로 — 검증용이라 잃어도 됨). `override` Map 키에 `fix`·`ref` 추가.
- **[고정] API** — `GET /api/check/rules` 항목에 `fix`·`ref`. 실행 응답 findings 는 그대로(Finding 불변 — message 도 안 보내는 꼴 유지).
- **[고정] xlsx** — 열 `파일·줄·묶음·규칙·등급·설명·권장 수정·근거` 8. 값은 `load(p,null,null).defs()` 의 같은 맵에서 `fix`·`ref`(없으면 ""). 열은 **끝에 붙인다**(`fileLevelFindingsShowFileInExport` 셀 1·3 불변).
- **[고정] 화면** — 결과 표 열 `['파일','줄','묶음','규칙','등급','원문','권장 수정']` 7(끝에), 값은 `rules` 배열에서 `id` 로 찾은 `fix`(없으면 ""). 복사 TSV 머리·행도 같은 열 추가(끝에 — `"파일\t줄\t"` 시작 불변). 「근거」 는 화면에 안 둔다(값이 비어 있다).
- **[고정] 5-25b 문구** — `rules.yaml` **security 33 규칙**에 `fix:` 한 줄씩. 규칙: 60자 안 · 「무엇을 무엇으로」 꼴 · 코드 조각 없이 글로 · `message` 를 되풀이하지 않는다 · 확신 없으면 비운다(비운 것은 이력에 id 나열). 예 — `security.sqlConcat` → `fix: 문자열 조립 대신 PreparedStatement·MyBatis #{} 바인드로` · `security.osCommand` → `fix: 외부 입력을 명령에 넣지 않는다 — 허용 목록 값으로 치환`. `ref:` 는 전부 비움(사용자 결정). 나머지 50 규칙 `fix` 는 다음 번들.
- **[고정] `rules/example.yaml`** 새 파일 — 사업별 규칙 예시 하나(주석으로 모든 칸 설명, 실제 규칙은 `enabled: false` 로 한 개). 프로필이 이미 가리키고 `.gitignore` 가 허용한다. `CheckRulesTest.customRulesFileAddsAndReplaces` 는 자기 임시 파일을 쓰니 영향 없음 — 다만 **`RuleSet.load` 가 작업 폴더 기준 `rules/example.yaml` 을 실제로 읽게 되니** 예시 규칙은 `enabled: false` 여야 골든이 안 바뀐다.

**5-26 검사 제외 glob**
- **[고정] 키** — `codecheck.exclude: [<glob>…]`, 루트 기준 상대 경로(`/` 구분)에 `**`·`*`·`?` — `Rule.Def.glob()`(:107-123) 과 같은 뜻. 예 `['**/webapp/js/lib/**', '**/*.min.js', 'src/main/resources/static/vendor/**']`. 고정 목록(`.git`·`target`·`node_modules`·숨김)은 그대로 두고 **더한다**.
- **[고정] 어디서 거르나** — `LocalFiles.list(dir, globs, maxFiles)` 는 그대로 두고 `list(dir, globs, excludes, maxFiles)` 오버로드 추가 — 폴더 순회에서 디렉터리 상대 경로가 exclude 에 맞으면 하위 통째로 건너뛰고, 파일 상대 경로가 맞으면 뺀다. 매처는 `PathMatcher("glob:"+g)` 를 **상대 경로(`/` 로 바꾼 글)** 에 적용(Windows 구분자 `\` → `/`). `CheckRunner:50` 만 새 오버로드를 쓴다(분석·포매터·폴더 비교는 그대로 — 거르기는 코드 검사 요구다).
- **[고정] 통로** — `Profile.CodeCheck(groups, rules, customRules, List<String> exclude)` + 3인자 호환 생성자. `CheckRoutes.load`(:293,302) 와 `ProfileStore:187` 은 4인자로 exclude 를 넘긴다. `CheckRunner` 가 `Profile` 또는 `List<String> exclude` 를 받는 길 — 지금 `CheckRunner` 시그니처를 보고 **가장 좁은 추가**(인자 하나)로. 붙여넣기 검사는 해당 없음.
- 시험: `CheckStoreTest.runnerOverFixtureFolder` 옆에 — 임시 폴더 `a/A.java`(pos)·`lib/x.min.js`(pos) 에 `exclude: ['lib/**']` → lib 결과 0.

**6-32 프로그램 수 · 행 수 열**
- **[고정] CRUD모듈** — `CrudViews` 에 `static Map<String,Integer> programCounts(List<LongRow> rows)`(표 → 서로 다른 `program` 수) 추가, `ModuleRow`·`ModuleMatrix` 레코드 불변. xlsx 열 `표 · 프로그램 수 · <모듈…>`(둘째 열). `AnalyzeRoutesTest:329` 머리 → `"표|프로그램 수|bbs|other"`. 화면 CRUD 매트릭스는 안 바꾼다.
- **[고정] 정합성** — `Consistency.Unused(schema, table, type, Long rowCount)` 4 컴포넌트 + 3인자 호환 생성자(rowCount null) — `ConsistencyTest:50` 은 그대로 통과하되, 같은 테스트에 rowCount 가 실리는 단언 한 줄 추가(픽스처 `table()` 에 rowCount 를 넣어). xlsx 정합성 열 `구분·이름·스키마·종류·프로그램 수·사유·행 수` 7(끝에, 다른 행은 ""). 값은 `rowCount == null ? "" : rowCount`. `AnalyzeRoutesTest:346` 머리 갱신. 화면 `conUnused` 열 `['스키마','표','종류','행 수']`.

**7-15 생성기 표별 재정의**
- **[고정] 키** — `generator.tables: { <표 이름>: { className: <이름>, skipColumns: [<컬럼>…] } }`. 표 이름·컬럼 이름 대문자 비교(스키마 없음 — 같은 이름 표가 스키마 둘에 있으면 둘 다 적용).
- **[고정] 레코드** — `Profile.Generator(templateSet, basePackage, outDir, Map<String, TableGen> tables)` + `record TableGen(String className, List<String> skipColumns)`. `GenModel.Options` 에 `String className, List<String> skipColumns` 두 컴포넌트 추가(5인자 호환 생성자). `GenModel.of:62` — `o.className()` 이 비지 않으면 그것(PascalCase 그대로 신뢰), 아니면 지금 규칙. 컬럼 루프 :81 — `skipColumns` 에 든 컬럼은 `fields`·`nonPk` 에서 뺀다. **PK 컬럼은 skipColumns 에 있어도 안 뺀다**(경고 한 줄 「PK 컬럼 X 는 뺄 수 없다」, `Result.warnings`). `Generator.run:54-55` 표마다 Options 만들 때 `base` 의 className·skipColumns 가 아니라 **표별** 값 — `Generator.run` 에 `Map<String, Profile.TableGen> tableGens` 인자 추가(비면 빈 맵), `GenerateRoutes:143` 에서 `p.generator().tables()` 를 넘긴다.
- 시험: `GenModelTest` 새 케이스(className 재정의 · skipColumns 둘 중 하나가 PK → 경고) · `GenerateRoutesTest` 프로필 YAML 에 `tables:` 넣은 케이스 하나(생성 파일 이름이 재정의 클래스 이름). 골든 `model.json`·템플릿 골든 불변(재정의 없는 픽스처).

**0-54 런처 문구**
- `index.html:67` 「이전 실행 비교·」 삭제. 글자 수만 줄인다.

---

### 4. 실행 스텝

청크 = 커밋 하나. `verify.sh` 와 `git commit` 은 따로. 커밋 제목 `feat|fix|chore|docs: <번호> <무엇>`, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`(실행 세션 모델). 각 청크 끝에 PLAN 분할표 행 상태 `완료` + PROGRESS 이력 한 줄(해시).

#### S0. 문서 행
- 가지 `work/2026-10-14` 는 있다(`git branch --show-current` 확인, 아니면 `git checkout work/2026-10-14`). 이 플랜 → `design/24-bundle39.md`(맨 위 「원본은 설계 세션 플랜 파일 — S0 에서 옮김」). `PLAN.md`: 15장 번들 표에 번들 39 행(`S0`→`0-54`→`1-63`→`2-24`→`2-25`→`2-26`→`2-27`→`5-25a`→`5-25b`→`5-26`→`6-32`→`7-15`, 「2026-10-13(Fable, 설계 24 — `design/24-bundle39.md`). **유사 도구 비교 업그레이드 11** — …」, 머지 위임 없음) · 16장 분할표에 행 11개(설계 칸은 「설계 24 §3 <항목>」 한 줄 + 대상 파일, 선행 `없음`(5-25b 는 5-25a), 상태 `대기`). PROGRESS 이력 한 줄.
- 검증: `bash scripts/verify.sh`(docs) → 커밋 `docs: S0 번들 39 설계 24 — 업그레이드 11 행`.

#### 0-54. 런처 문구
- 대상: `src/main/resources/tools/index.html:67`
- 변경: `·이전 실행 비교` 삭제.
- 검증: `grep -c '이전 실행 비교' src/main/resources/tools/index.html` → 0. `verify.sh`. 커밋 `chore: 0-54 런처 코드 검사 카드 — 지운 기능 문구 삭제`.
- 의존: S0

#### 1-63. 반영 DDL 꼬리표
- 대상: `core/gen/AlterGen.java` · `golden/gen/alter-{5}.sql` · `core/gen/AlterGenTest.java`
- 변경: §3 1-63. `enum Kind` · `check(out, Kind, why)` · `commented(out, Kind, why, s)` · 카운터 셋 · 머리 주석 `· 확인 m(삭제 a · 데이터 b · 호환 c) · 손 h`. 호출 7곳 종류 배정(:181·:277 삭제 / :294·:319·:322·:349 데이터 / :274 호환). `AlterGenTest.goldenPerTarget` 에 단언 추가 — oracle 골든 글에 `[확인:삭제]` ≥ 2, `[확인:데이터]` ≥ 1 포함.
- 검증: `bash scripts/mvn.sh -q -B test -Dtest=AlterGenTest -Dgolden.update=true -Dsurefire.failIfNoSpecifiedTests=false` → `git diff --stat src/test/resources/golden/gen/` 다섯 파일, 바뀐 줄이 **`[확인]` → `[확인:…]` 꼬리표와 1행 머리만**인지 `git diff` 로 읽는다(다른 줄 바뀌면 중단 ②). 다시 `-Dtest=AlterGenTest,MetaRoutesTest` 초록. `verify.sh`. 커밋 `feat: 1-63 반영 DDL [확인] 종류 꼬리표 · 종류별 건수`.
- 의존: S0

#### 2-24. 코멘트 채움률
- 대상: `core/deliverable/Definitions.java`(`CommentFill`·`commentFill`) · `core/deliverable/DeliverableService.java`(`writeGuide` 요약 두 행 — 시그니처에 `CommentFill` 인자 추가, :136 호출에서 `Definitions.commentFill(Definitions.sorted(snapshot))`) · `core/deliverable/DefinitionsTest.java`(새 케이스 — 픽스처에서 코멘트 있는/없는 수를 직접 세어 같음)
- 검증: `-Dtest=DefinitionsTest,DeliverableServiceTest` 초록. `verify.sh`. 커밋 `feat: 2-24 작성안내 요약 — 코멘트 채움률 두 줄`.
- 의존: S0

#### 2-25. 분류명 자동 채움
- 대상: `core/profile/Profile.java`(`Deliverable` 에 `Map<String, List<String>> business` — compact 생성자 null → 빈 `LinkedHashMap` unmodifiable) · `profiles/example.yaml`(`deliverable:` 블록에 주석 + 예 `business: { 고객: [TB_CUST_, CUST_], 주문: ['^TB_ORD.*'] }`) · `core/deliverable/Definitions.java`(`Options` 9번째 컴포넌트 `business` + 8인자 호환 생성자 · `static String classify(String table, Map<String,List<String>> business)` · `d02` index 14) · `core/deliverable/Grades.java`(02 「분류명」 GUESS 행 · DocMark 02 reason) · `web/DeliverableRoutes.java:176-184`(Options 에 `pd.business()`) · 시험 `ProfileStoreTest.exampleMapsEveryField` 단언 · `DefinitionsTest` 새 케이스(접두 하나·정규식 하나·둘 다 맞으면 먼저 것·안 맞으면 "") · `GradesTest:52` 수치(§3 규칙대로 세어 고치고 이력에 적는다)
- 검증: `-Dtest=ProfileStoreTest,DefinitionsTest,GradesTest,DeliverableRoutesTest` 초록. `pg-02.json` 골든 불변(픽스처 Options 에 business 없음). `verify.sh`. 커밋 `feat: 2-25 정의서 02 분류명 — 프로필 deliverable.business 로 채움`.
- 의존: S0

#### 2-26. 코멘트 덧씌움(논리명)
- 대상: `core/deliverable/Definitions.java`(`record LogicalNames` + `of(LogicalRun.Result)` · `build` 5인자 + 4인자 호환 · `korOrMark(comment, fallback)`·`kor(comment, fallback)` · d02·d03 estimated) · `core/deliverable/DeliverableService.java`(:79 조건에 02·03 추가 · :84 `Definitions.build(…, Definitions.LogicalNames.of(logical))`) · `core/deliverable/Grades.java:91,106` how 글 · `core/deliverable/DefinitionsTest.java` 새 케이스(§3 2-26)
- 검증: `-Dtest=DefinitionsTest,DeliverableServiceTest,GradesTest` 초록, `pg-02·03.json` 불변. `verify.sh`. 커밋 `feat: 2-26 정의서 02·03 한글명 — 코멘트 없으면 논리명 변환으로`.
- 의존: 2-25(같은 파일 `Definitions`·`Grades` — 충돌 피하려 뒤에)

#### 2-27. 이름 기반 관계 후보
- 대상: `core/deliverable/Relations.java`(`Named` · `Result` 3 컴포넌트 + 2인자 호환 · `infer` 에 named 계산 · joins 비어도 돈다) · `core/deliverable/DeliverableService.java`(:86 한 줄 · 「관계 후보」 열 7 + 행 합치기 + 근거 · :195-196 한 문장) · `core/deliverable/RelationsTest.java` 새 케이스 셋(이름·타입 같음 → named / 타입 다름 → 없음 / 선언 FK 있음 → 없음 / weak 와 겹침 → 근거 `조인·이름` 은 Service 쪽이라 Relations 는 named 에 그대로 두고 Service 가 합친다)
- 검증: `-Dtest=RelationsTest,DeliverableServiceTest,DefinitionsTest` 초록(`doc04CarriesInferredRows`·`pg-04.json` 불변 — named 는 04 에 안 간다). `verify.sh`. 커밋 `feat: 2-27 관계 후보 — 컬럼 이름·타입이 다른 표 PK 와 같은 쌍`.
- 의존: S0

#### 5-25a. 규칙 `fix`·`ref` 칸 · API · xlsx · 화면 열
- 대상: `core/check/Rule.java`(Def 13 + 11인자 호환 · `withEnabled`·`override` 13인자 · override 키 fix·ref) · `web/CheckRoutes.java`(`/rules` 항목 fix·ref · xlsx 열 8) · `src/main/resources/tools/code_check_ext.js`(결과 표 열 7 · copy TSV) · `web/CheckRoutesTest.java`(`fileLevelFindingsShowFileInExport` 에 머리 행 8열 단언 · `textRunAndRules` 에 rules 항목 `fix` 키 존재) · `SmokeHtmlUnitTest.codeCheckFolderRun` 표 머리 7열 단언 한 줄
- 변경 전 확인: `rules.yaml` 에 아직 `fix:` 가 없으니 Def 추가만으로 로드는 그대로 된다.
- 검증: `-Dtest=CheckRulesTest,CheckRoutesTest,PureRulesJsonTest` 초록(`pure-rules.json` 불변). `verify.sh`. 커밋 `feat: 5-25a 코드 검사 규칙 fix·ref 칸 — API·xlsx 권장 수정·근거 열`.
- 의존: S0

#### 5-25b. security 33 `fix` 문구 · `rules/example.yaml`
- 대상: `src/main/resources/check/rules.yaml`(security 33 규칙에 `fix:` — §3 규칙) · `rules/example.yaml`(새 파일 — 머리 주석에 칸 설명 전부(id·group·severity·kind·globs·regex·flags·skipComments·message·params·fix·ref·enabled) + 예시 규칙 하나 `enabled: false`)
- 검증: `-Dtest=CheckRulesTest,PureRulesJsonTest,CheckRoutesTest` 초록 · `golden/check/fixtures.json` 불변(fix 는 판정에 안 끼친다) · `run.bat` 없이 `toolbox.bat`(배치 CLI)로 `GET /api/check/rules` 에 해당하는 명령이 있으면 security 규칙 하나의 fix 가 나오는지, 없으면 `CheckRoutesTest` 단언으로 대신(`security.sqlConcat` 의 fix 비지 않음). `verify.sh`. 커밋 `feat: 5-25b 보안 규칙 33 권장 수정 문구 · rules/example.yaml`. 비운 규칙 id 는 이력에.
- 의존: 5-25a

#### 5-26. 검사 제외 glob
- 대상: `core/profile/Profile.java`(`CodeCheck` 4 + 3인자 호환) · `core/profile/ProfileStore.java:187`(4인자) · `web/CheckRoutes.java:293,302`(4인자) · `core/fs/LocalFiles.java`(`list(dir, globs, excludes, maxFiles)` 오버로드 — 기존 3인자는 `excludes = List.of()` 로 위임) · `core/check/CheckRunner.java:50`(exclude 전달 — 인자 하나 추가) · `profiles/example.yaml`(`codecheck.exclude: []` + 주석 예) · 시험 `ProfileStoreTest.exampleMapsEveryField` · `CheckStoreTest`(또는 새 `CheckRunnerTest`) 제외 케이스
- 검증: `-Dtest=ProfileStoreTest,CheckStoreTest,CheckRoutesTest,CheckRulesTest` 초록. `verify.sh`. 커밋 `feat: 5-26 코드 검사 — 프로필 codecheck.exclude 로 폴더·파일 빼기`.
- 의존: S0

#### 6-32. 프로그램 수 열 · 안 쓰는 표 행 수
- 대상: `core/analyze/CrudViews.java`(`programCounts`) · `core/analyze/Consistency.java`(`Unused` 4 + 3인자 호환, :95 `t.rowCount()`) · `web/AnalyzeRoutes.java`(CRUD모듈 둘째 열 · 정합성 7열) · `src/main/resources/tools/program_analysis_ext.js:534`(열 4) · 시험 `AnalyzeRoutesTest:329,346` 머리 · `ConsistencyTest` rowCount 단언 · `CrudViewsTest` programCounts 케이스
- 검증: `-Dtest=CrudViewsTest,ConsistencyTest,AnalyzeRoutesTest` 초록 · SmokeHtmlUnit 프로그램 분석 케이스 초록. `verify.sh`. 커밋 `feat: 6-32 분석 xlsx — CRUD모듈 프로그램 수 · 정합성 안 쓰는 표 행 수`.
- 의존: S0

#### 7-15. 생성기 표별 재정의
- 대상: `core/profile/Profile.java`(`Generator` 4 + `record TableGen`) · `profiles/example.yaml`(`generator.tables:` 주석 예) · `core/gen/GenModel.java`(`Options` 7 + 5인자 호환 · className · skipColumns · PK 경고) · `core/gen/Generator.java`(`run` 에 `tableGens` 인자 · 표마다 조회) · `web/GenerateRoutes.java:143`(넘김) · 시험 `ProfileStoreTest.exampleMapsEveryField` · `GenModelTest` 둘 · `GenerateRoutesTest` 하나
- `Generator.run` 을 부르는 다른 곳(`cli/`·테스트)이 있으면 빈 맵을 넘기는 **오버로드**로 호출부 불변.
- 검증: `-Dtest=ProfileStoreTest,GenModelTest,GenTemplatesTest,GeneratorTest,GenerateRoutesTest` 초록, 골든 `model.json`·템플릿 골든 불변. `verify.sh`. 커밋 `feat: 7-15 생성기 — 프로필 generator.tables 표별 클래스 이름·빼는 컬럼`.
- 의존: S0

#### 마무리(CLAUDE.md 「마무리」 그대로)
독립 리뷰 → `gate-probe.sh` → `bash scripts/verify.sh` 끝 줄의 무거운 레인(db·corpus 둘 다 나올 것) → `bash scripts/verify.sh --db --corpus`(약 25분, 메모리·Docker 확인 — 메모리 `full-verify-memory-contention`) → push → PR(본문에 청크마다 시작 세 줄 표) → CI 폴링 → 머지 위임 없음, PR 열고 멈춤 → 끝 보고(계획 밖 결정 · wip 로 뺀 행 · 새로 선 행 · 사람이 할 것 — `ref:` 번호 채우기 · `fix` 비운 규칙 · 데모에서 02 분류명·한글명 추정 칸 눈으로).

---

### 5. 금지 사항

- `pure/` · CI · `scripts/verify*` · 새 의존성 · `COLS_*`·양식 xlsx·매핑 YAML · `AlterGen.Result`·`Finding`·`SnapshotDiff` 레코드 모양 · `/api/check/run` 응답 꼴 · `demo.yaml`·`docker-ora.yaml`.
- 레코드에 컴포넌트를 더하면서 **기존 인자 수 생성자를 없애지 않는다**(호환 생성자 — 테스트 호출부 보존).
- 골든을 「초록 되게」 갱신하지 않는다 — 1-63 만 골든이 바뀌고, 그 diff 는 꼬리표·머리 줄만이어야 한다. 다른 청크에서 골든이 바뀌면 중단.
- `mix`·`none` 논리명을 산출물에 쓰지 않는다. 이름 기반 관계를 04(strong)에 넣지 않는다.
- `ref:` 값을 채우지 않는다(사용자 결정 — 사람 몫). `fix:` 는 security 33 만.
- 억제 이유·코드 본문을 H2 에 저장하는 길을 열지 않는다(이 번들에 억제 주석은 없다).
- 화면 CRUD 매트릭스(`program_analysis_ext.js:251-264`)·DTO 생성(db_browser)·분석·포매터·폴더 비교의 폴더 순회는 안 바꾼다.

---

### 6. 최종 검증

- 청크마다 적힌 `-Dtest=` 묶음 초록 + `verify.sh` 빠른 초록(커밋마다).
- 마무리 `verify.sh --db --corpus` 초록 — db(AlterGen 골든·`alterRoundTrip`) · corpus(`PureCodeCheckTest`·`CheckCorpusTest`·`AnalyzeCorpusTest` — `analyze-egov-consistency.json` 건수 불변).
- `gate-probe.sh` 초록(패치 갱신 필요 시 다시 떠서 커밋).
- 사람이 볼 것(데모 프로필): 산출물 SQL → 02·03 만 골라 생성 → 작성안내 「요약」 에 코멘트 채움 두 줄 · 「항목」 시트 02 「분류명」·「한글 테이블명」 추정 수 · 「관계 후보」 근거 열 / DB 탐색기 비교 → 반영 DDL `[확인:삭제]` 꼴 / 코드 검사 폴더 검사 → 결과 표 「권장 수정」 열에 security 규칙 글 · xlsx 8열 / 소스 생성 → `generator.tables` 에 표 하나 넣고 클래스 이름 바뀌는지.

---

### 7. 중단 조건

- ① §2 의 파일·줄·시그니처가 실제와 다르다(이름 바뀜·옮겨짐).
- ② 1-63 골든 diff 에 꼬리표·머리 줄 밖의 변화가 있다, 또는 다른 청크에서 골든이 바뀐다.
- ③ `GradesTest:50` ● 집합이나 다른 문서 수치가 2-25 로 함께 바뀐다(Grades 02 행 추가가 다른 단언을 건드림 — 보고).
- ④ `Profile` 새 키를 더했는데 `exampleRoundTrips` 가 빨강(Jackson 직렬화 순서·null 처리) — 두 번 고쳐도면 보고.
- ⑤ `CheckRunner`·`Generator.run` 시그니처 변경이 호출부 3곳 넘게 번진다(오버로드로 못 막음).
- ⑥ 검증 2회 연속 빨강이고 원인이 플랜 밖 · 스텝에 없는 파일 3개 이상.

보고 형식: 어느 청크 · 무엇이 예상과 달랐는지 · 선택지.

---

### 8. 불확실 항목

설계 세션이 2026-10-13 에 확인한 것(다시 조사하지 않는다):
- `CheckRunner` 는 정적 — `run(root, rules, files, ctx)`(:32) · `run(root, rules, files, ctx, Predicate<String> changed)`(:43) · `runText(...)`(:95). **5-26**: `run(root, rules, files, ctx, changed, List<String> exclude)` 6인자를 추가하고 기존 둘은 `List.of()` 로 위임. `CheckRoutes` 의 폴더 실행 호출만 6인자로.
- `Generator.run` 호출부 — main 1(`GenerateRoutes:145`) + 테스트 8(`GeneratorTest` 5 · `GeneratorCorpusTest` 2 · `GeneratorJavacTest` 1). **7-15**: `tableGens` 인자를 더한 오버로드, 기존 시그니처는 빈 맵으로 위임 → 테스트 불변.
- `Grades.build` 02 행은 15개(:89-103) — AUTO 5 · GUESS 2 · MANUAL 8, 「분류명」 없음. **2-25**: 「분류명」 GUESS 추가 → `GradesTest:52` 는 `(5,3,8)`.
- `LogicalRun.TableRow.owner` = `Table.schema()`(`ColumnInputs:86`). **2-26**: 표 맵 키는 `up(schema)+"."+up(table)` 과 `up(table)` 둘 다 넣고, 찾을 때 스키마 있는 키 먼저.
- `/api/analyze/runs/{id}/consistency`(`AnalyzeRoutes:283-298`) 는 `Consistency.Report` 를 그대로 직렬화 → `Unused.rowCount` 추가만으로 화면이 받는다.

남은 것 없음. **실행은 2026-10-14**(사용자 2026-10-13 「내일 진행」). Opus 세션에서 「번들 해」 → S0 부터. S0 에서 메모리 `compare-research-2026-10-14.md` 에 「설계 24 = 이 플랜, 번들 39 실행 중」 한 줄을 덧붙인다.
