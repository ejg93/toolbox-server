# 설계 51 — 번들 67: 구조 점검 넷 + 개발 도구 Hex (Fable 설계 · Opus 실행, 2026-11-01)

## 맥락
번들 표 「번들 67」 행(요지 — compare ③ 다-14·15·16·18 · ⑥ 바-7). 산출물 SQL 91 구조 점검(`core/deliverable/StructureLint`, 설계 25 의 아홉 규칙)에 SchemaCrawler 가 하고 우리가 안 하던 넷 — PK 없는 표(인덱스는 있음) · FK 형식·길이 불일치 · 중복 인덱스 · FK 순환 — 을 더하고, 개발 도구 「인코딩 판별」 탭에 CyberChef Data format 의 Hex·Hexdump 를 붙인다. 둘 다 db 레인 밖(`core/deliverable`·`tools` 는 corpus 레인 — `--corpus` 한 번, `--db` 없음).

**번들 66 보정**(2026-11-01) — 이력 「정한 것(계획 밖)」 과 겹치는 자리 없음. 66 뒤 사람 몫 둘(PMD 기준선 `6912e5f` · 화면 눈 확인 `3f587ca`)이 이 가지에 로컬로 있다 — 같은 PR 로 간다.

탐색 실측(2026-11-01, `3f587ca`, 표본 `C:/workspace/toolbox-corpus`, 외부 통신 0, grep 셈 — 코드 본문은 안 옮긴다):

| 표본 | CREATE TABLE | CREATE INDEX | FOREIGN KEY | PRIMARY KEY | 비고 |
|---|---|---|---|---|---|
| egov DDL mysql·maria·postgres(공통 파일 하나씩) | 182 | 224 | 64 | 181 | 인덱스가 많아 `dupIndex` 재료 |
| egov DDL oracle·tibero | 182 | 47 | 64 | 181 | |
| egov DDL altibase | 182 | 44 | 62 | 181 | |
| egov DDL goldilocks | 182 | 44 | 0 | 180 | FK 0 — FK 규칙 셋은 0 이 정상 |
| egov DDL cubrid | 181 | 2 | 1 | 180 | |
| ruoyi `sql/ry_*.sql` · ruoyi-classic | 20·21 | 0(인라인 `KEY` 6) | 0 | 20·21 | 인라인 KEY 는 DdlReader 가 안 읽는다(제약 줄로 버림) |
| ruoyi `sql/quartz.sql` | 11 | 0 | 5 | 11 | FK 다섯 — 순환 없음 기대 |
| db-samples HR `hr_create.sql` | 7 | 18 | 10(REFERENCES) | 7 | departments ↔ employees 순환(V-6 B 에서 이미 앎) · `REFERENCES departments` 처럼 부모 컬럼 생략 꼴 |
| db-samples CO·SH | 7·9 | 8·0 | 9·10 | 7·9 | |
| csv(공공데이터) | 100 파일 | — | — | — | `csv-summary.json`: MS949 84 · UTF-8 BOM 16 · 깨진 바이트 1(`csv-broken-bytes.txt`). 크기 최소 239 B · 중앙 32 KB · 최대 3.0 MB |

- `DdlReader`(`core/gen`, db 레인)는 `CREATE INDEX` 와 인라인 `KEY`·`INDEX` 를 **안 읽는다** — DDL 스냅샷의 `indexes` 는 늘 비어 있다. 컬럼 `nativeType`(괄호 뗀 대문자)·`length`·`precision`·`scale`, PK, FK(`refColumns` — 생략하면 빈 목록), 유니크는 채운다
- 제품의 구조 점검은 JDBC 스냅샷(`SnapshotStore`)만 받는다 — 인덱스가 있다. `JdbcMetaSource.loadIndexes` 는 PK 이름만 빼고, **유니크 인덱스는 `uniques`·`indexes` 양쪽**에 같은 이름으로 든다(설계 25 §2.1). H2 는 FK 마다 `CONSTRAINT_INDEX_n` 비유니크 인덱스를 스스로 만든다 → HR H2 스냅샷에선 손으로 만든 `*_IX` 와 겹쳐 `dupIndex` 가 H2 흔적으로 나올 수 있다(골든 B 로 둔다)
- `hr-lint.json` 은 지금 `[ ]`(HR 에 아홉 규칙 지적 0). PG 벤더 골든 `meta/postgres-vendor.json` 은 `indexes` 전부 빈 목록, `categories.parent_id` 자기 참조 하나 → 새 규칙 넷의 기대 0(`pg-lint.json` 불변 기대)
- `DeliverableRoutesTest.structureLint` 픽스처(`TB_CMM_CD` PK 있음 · `IF_ORDER_RCV` 아무것도 없음, FK 0) → 새 규칙 지적 0, `rules` 9 → 13 만 바뀐다
- 자기 검사 기준선 `golden/check/self-check.txt:73` 에 `StructureLint.java|metric.complexity|복잡도 50 > 15 · run(3)` — 키에 숫자가 들어 **`run` 의 분기 수가 바뀌면 빨강**. 새 규칙은 `run` 에 분기 없이 한 줄 호출로 단다
- `dev_tools.html` 인코딩 판별 탭(`#page-enc`, `encAnalyze(file)` 1874~1917 줄) — 파일을 UTF-8·EUC-KR 로 해석해 나란히, 판정 글은 치환 문자 수·한글 수로. 바이트를 보는 자리가 없다. 바이트 계산 탭의 `cp949len` 은 비ASCII 를 2 로 세는 근사(인코딩 표 없음) — 브라우저 `TextEncoder` 는 UTF-8 만 인코딩, `TextDecoder` 는 `euc-kr`(WHATWG = windows-949 = Java MS949)·`utf-16le/be` 디코딩이 된다. HtmlUnit 은 dev_tools JS 를 안 돌린다(`JS_OFF`) — 기계 검증은 `JsCorpusTest` + `scripts/puppeteer/corpus-js.js`(node, corpus 레인)뿐. V-7 행이 적은 「EUC-KR 500: 인코딩 판별 결과 = 실물」 은 corpus-js.js 에 **구현된 적이 없다**(grep 0)
- 고정 색 — `ToolsFolderTest.toolsHaveNoLocalTheme` 허용 목록 밖 색 금지. `encAnalyze` 안 `#f48771`(허용 목록)·`#4ec9b0`(목록 밖인데 이미 있음) 둘은 토큰으로 바꾼다

## 사용자 결정(2026-11-01)
- (가) Hex 변환의 텍스트 → 바이트 — **클라이언트만**(UTF-8 만 인코딩, 디코딩은 UTF-8·EUC-KR·UTF-16). 서버 라우트 없음. EUC-KR 바이트는 파일 Hexdump 와 Hex → 텍스트 디코딩으로 본다
- (나) **머지 위임 있음**
- 설계가 정한 것(사용자가 안 물은 자리) — 새 규칙 넷은 enum **끝**에(기존 골든·라우트 시험의 `rules[0]` 순서 불변) · 프로필 키 추가 없음(`off` 에 새 id 넷이 그냥 든다) · `fkCycle` 은 자기 참조 FK 제외, 강연결 성분(SCC)마다 지적 하나 · `fkTypeMismatch` 는 둘 다 알 때만 비교(모르면 안 짚는다), 동의어 표 없음 · `dupIndex` 는 정렬 방향(`sorts`) 무시 · DDL 표본의 `CREATE INDEX` 는 **시험 쪽 정규식**으로 붙인다(`DdlReader` 는 db 레인 — 「후보로」 번들 68) · corpus 시험은 `DdlCorpusTest`(레인 목록에 있는 `core/gen` 시험)에 메서드로 — `core/deliverable` 시험 폴더는 `LANE_CORPUS` 에 없고 `verify-fingerprint.sh` 는 안 고친다 · Hexdump 깨진 자리 표시는 판정된 인코딩 기준(UTF-8 → `utf8Invalid` · EUC-KR → `cp949Invalid`) · 설계 41~50 그대로(에이전트 최소 · 검증 인프라 최적화 금지 · 표본 추가 없음 · 새 화면 없음 · 순수본 불변)

실행자는 Opus. 차례 `S0` → `2-32a` → `2-32b` → `2-33` → `4-28` → `4-29` → `R-16` → `V-47` → 마무리. 가지 `work/2026-11-01c`(로컬 셋 같이). 번들 하나·PR 하나.

---

## 1. 목표와 범위
- 한 문장: 산출물 SQL 91 구조 점검이 PK 없는 표(인덱스는 있음)·FK 형식 불일치·중복 인덱스·FK 순환을 더 짚고(아홉 → 열셋), 개발 도구 인코딩 판별 탭이 파일의 Hexdump(깨진 자리 표시)와 Hex ↔ 텍스트 변환을 한다.
- 완료 시 체감: 데모 스냅샷에서 「구조 점검」을 누르면 규칙 13 의 건수가 보이고, FK 순환이 있는 스키마(HR 의 departments ↔ employees)는 「FK 순환」 한 줄로, FK 컬럼이 부모와 길이가 다르면 「FK 형식 불일치」 로 나온다. xlsx 도 같다. CLI `lint` 글이 열셋을 센다. 개발 도구 인코딩 판별 탭에 MS949 CSV 를 떨어뜨리면 판정 아래 Hexdump(앞 1,024바이트, UTF-8 로 읽으면 깨지는 자리 강조)가 보이고, Hex 변환 칸에 `EA B0 80` 을 넣고 UTF-8 로 디코딩하면 「가」 가 나온다. compare ⑰.
- In scope: 수정 `core/deliverable/StructureLint.java`(규칙 넷 + 도우미, `run` 분기 불변) · `StructureLintTest`(양성·음성 넷) · `DeliverableRoutesTest`·`HrSnapshotCorpusTest`(수 9 → 13) · `DdlCorpusTest`(구조 점검 골든 + ruoyi DDL 읽기 + 시험 쪽 `CREATE INDEX`) · 골든 `corpus/ddl-lint.json`·`corpus/ddl-lint-findings.txt`(새)·`corpus/hr-lint.json`(갱신) · 글 넷(README · `DocCommands.Lint` 설명 · PLAN 170 행 · `StructureLint` javadoc · 91 카드 안내) · `tools/dev_tools.html`(Hexdump·Hex 변환 + 순수 함수 여섯 + `encAnalyze` 토큰 색) · `tools/index.html` 개발 도구 설명 한 줄 · `scripts/puppeteer/corpus-js.js`(enc 절) · `JsCorpusTest`(Hex·판정 대조) · `design/compare-2026-10-28.md` ⑰ · PLAN 분할표·번들 표 · PROGRESS.
- Out of scope: `DdlReader` 의 `CREATE INDEX`·인라인 `KEY` 읽기(db 레인 — 번들 68 후보) · 서버 Hex 라우트(사용자 (가)) · Charcode·진법 변환(바-7 나머지) · 해시 탭(바-6) · `fkTypeMismatch` 동의어 표·방언 간 비교 · 유니크 인덱스의 NULL 컬럼(다-17)·LOB 수(다-19) · 90 품질 SQL·`/api/quality/nopk` 변경 · 프로필 키 추가 · 순수본 · 표본 추가 · `verify-fingerprint.sh` 수정 · `--db`.

## 2. 현재 구조 요약
- `core/deliverable/StructureLint.java`(260줄) — `enum Rule(id, label, meaning)` 아홉(순서 = 결과·counts 순서, `byId`) · `record Options(maxColumns, requiredColumns, Set<Rule> off)`(`of(Profile.Lint)`) · `record Finding(rule, schema, table, column, detail)` · `record Result(findings, counts, tables, basis)` · `run(snapshot, whole, o)`: `Definitions.sorted(snapshot)` 에서 뷰 뺀 `tables`, `whole` 로 `referenced` 집합, 표마다 아홉 규칙 `if (on(opt, Rule.X))` 분기, 끝에 정렬(규칙 ordinal → schema → table → column) · `counts` 는 켠 규칙 전부 0 포함 · `basis` 세 줄. 도우미 `indexed(t, fkCols)`(PK·유니크·인덱스 앞머리 집합) · `on` · `isView` · `refKey(child, fk)`(refSchema 없으면 자식 스키마) · `key(schema, table)` · `up` · `nz`. 복잡도 기준선 `run(3)` 50 — 키에 숫자가 든다.
- `core/meta` — `Table(schema, name, type, comment, columns, pk, fks, uniques, indexes, rowCount, createdAt, lastDdlAt, checks)` · `Column(name, ordinal, nativeType, jdbcType, Long length, Integer precision, Integer scale, nullable, defaultValue, comment, domain)` · `PrimaryKey(name, columns)` · `UniqueKey(name, columns)` · `Index(name, unique, columns, sorts)` · `ForeignKey(name, columns, refSchema, refTable, refColumns, deleteRule, updateRule)`(5인자 생성자 있음). `Schema(name, dbVersion, tables, sizeBytes)` + 3인자.
- `web/DeliverableRoutes.postLint`(:111) — `filtered` 스냅샷 + `whole` 로 `StructureLint.run`, `rules`(id·label·meaning·n)·`findings`·`basis`·`tables`, `save` 면 `Outputs.xlsx("구조점검-<id>.xlsx", 규칙·스키마·표·컬럼·내용·뜻)`. 규칙 수를 코드가 안 센다 — enum 이 늘면 그대로 따라온다. `cli/DocCommands.Lint`(:133) 설명 글에 「아홉 규칙」. `README.md:38` 같은 글. `PLAN.md:170` 「아홉 규칙 — …」. `tools/deliverable_sql.html:103` 91 카드 안내 「PK 없는 표는 위 90 의 버튼」(90 `#qNoPk` 는 `/api/quality/nopk` — PK 없는 표 전부, 거름 없음).
- 시험 — `StructureLintTest`(규칙마다 양성·음성, `of(r, rule)` 가 `표.컬럼 detail` 글 목록, `offRulesDisappearFromCountsAndOrderIsRuleThenTable` 가 counts 8 단언, `goldenOnPostgresVendorSnapshot` 11건 `deliverable/pg-lint.json`) · `DeliverableRoutesTest.structureLint`(:233 — `rules` 9 · 지적 넷 · xlsx 행 4) · `HrSnapshotCorpusTest.structureLint`(:103 — counts 9 · `corpus/hr-lint.json`) · `DdlCorpusTest`(corpus, `core/gen` — `DDLS` = egov 8방언 `*.sql`(모듈 DDL 포함, 방언마다 118 파일) + db-samples(CREATE TABLE 있는 것) + chinook, `read(p)`(BOM 뗌)·`strip`·`creates`, 골든 `ddl-summary.json`(방언마다 「파일 n · 테이블 n · 못 읽은 줄 n」)·`ddl-unreadable.txt`, `CorpusFiles.none`(A)·`baseline`(B)). PMD 기준선 짝: `StructureLint`=AvoidLiteralsInIfCondition · `StructureLintTest`=AvoidDuplicateLiterals · `DdlCorpusTest`=다섯 · `HrSnapshotCorpusTest`=둘 · `DeliverableRoutesTest`=둘. `JsCorpusTest` 는 짝 없음 — 새 코드가 새 짝을 만들면 안 된다.
- `VerifyLanesTest` — corpus 꼬리표 시험 소스는 `LANE_CORPUS` 경로 안이어야 한다. `src/test/java/kr/ejg/toolbox/core/gen`·`web/JsCorpusTest.java`·`scripts/puppeteer`·`golden/corpus`·`src/main/resources/tools`·`core/deliverable`(main) 는 안에 있고 `src/test/java/kr/ejg/toolbox/core/deliverable` 은 **없다**.
- `tools/dev_tools.html` — 탭 16, `#page-enc`: `.enc-drop#enc_drop`(+`#enc_file`) · `#enc_verdict` · `.row` 안 `.col` 둘(`#enc_u_lbl #enc_u_bad #enc_u` · `#enc_k_lbl #enc_k_bad #enc_k`, `.enc-pre`). `encAnalyze(file)`: `file.arrayBuffer()` → UTF-16 BOM·NUL 비율 → 아니면 UTF-8 BOM → `TextDecoder` 둘로 치환 문자 수 `bu`·`bk`·한글 수로 판정 글 + 색(`#4ec9b0`·`#f48771`) → 미리보기 1,500자. 도우미 `esc`·`lsGet/lsSet('devt_…')`·`showToast`·`TB.copy(el, showToast)` 꼴 복사(:1720). CSS 23~25 줄 `.enc-drop .enc-pre`. 백엔드 전용 탭 JS 는 `dev_tools_ext.js` — 인코딩 탭은 `dev_tools.html` 안이라 거기서 고친다.
- `scripts/puppeteer/corpus-js.js` — 떠 있는 서버의 `dev_tools.html` 을 열고 JSON·XML·바이트(`u8len`·`cp949len`)·카멜·table_builder 를 `dev.evaluate` 로 돌려 `summary.json`(`files`·`bytes`·`camel`…). `corpusFiles(CORPUS, keep)` → `{src, rel}`, `read(f)` 는 UTF-8/EUC-KR 글. `JsCorpusTest`(corpus, `@BeforeAll` 에서 `CorpusNode.run("corpus-js.js", …)`) 가 Java 와 대조(`byteCountsMatchJava` 가 MS949 `getBytes`). `CorpusFiles.files("csv", "*.csv")` 100.
- 골든 갱신 `-Dgolden.update=true`(`GoldenFiles.updating`·`CorpusFiles.baseline`). `bash scripts/doc-lint.sh <md>` 문서 존댓말.

## 3. 설계 결정 — 전부 [고정]

### 공통
- §3-1 새 규칙 넷은 `Rule` enum **끝**에 이 순서로. id·label·meaning:

| enum | id | label | meaning |
|---|---|---|---|
| `NO_PK` | `noPk` | PK 없는 표 | PK 가 없다 — 유니크·인덱스는 있다(아무것도 없는 표는 noIndex, 전체 목록은 90 「PK 없는 표」) |
| `FK_TYPE_MISMATCH` | `fkTypeMismatch` | FK 형식 불일치 | FK 컬럼의 형식·길이·정밀도가 가리키는 부모 컬럼과 다르다 — 암묵 변환으로 인덱스를 못 타거나 값이 잘린다 |
| `DUP_INDEX` | `dupIndex` | 중복 인덱스 | 앞 컬럼이 같은 다른 인덱스·PK·유니크에 덮인다 — 쓰기 비용만 든다 |
| `FK_CYCLE` | `fkCycle` | FK 순환 | FK 로 서로 가리켜 되돌아온다 — 이관·삭제 순서가 안 정해진다(자기 참조는 제외) |

- §3-2 `run(3)` 은 **분기를 더하지 않는다** — 정렬 줄 바로 앞에 한 줄:
```java
structural(tables, byKey(whole == null ? snapshot : whole), opt, out);
```
`byKey` 는 `Definitions.sorted(...)` 의 뷰 아닌 표를 `key(schema, name)` → `Table`(`LinkedHashMap`, 같은 키는 첫 것). 새 메서드는 전부 `private static`, 복잡도 ≤ 15.
```java
private static void structural(List<Table> tables, Map<String, Table> whole, Options opt, List<Finding> out) {
    for (Table t : tables) {
        String s = nz(t.schema());
        if (on(opt, Rule.NO_PK)) { noPk(t, s, out); }
        if (on(opt, Rule.FK_TYPE_MISMATCH)) { fkTypes(t, s, whole, out); }
        if (on(opt, Rule.DUP_INDEX)) { dupIndexes(t, s, out); }
    }
    if (on(opt, Rule.FK_CYCLE)) { cycles(tables, out); }
}
```
- §3-3 키 묶음 — PK·유니크·인덱스를 한 목록으로(이름으로 중복 거름, PK → 유니크 → 인덱스 순, 이름 없는 것은 안 거른다):
```java
/** pk 는 PK 자리, unique 는 PK·유니크·유니크 인덱스. 같은 이름(대문자)은 먼저 든 것만 */
private record Keyed(String name, boolean pk, boolean unique, List<String> cols) { }
private static List<Keyed> keyed(Table t)
```
PK 이름이 null 이면 `"PK"`. `Options`·`basis`·`counts`·정렬·xlsx 열은 그대로.

### noPk(§3-4)
- PK 없음(`pk == null || columns 비어 있음`) **그리고** `keyed(t)` 가 비어 있지 않음. `Finding(NO_PK, s, t.name(), null, "유니크 u · 인덱스 i")` — u = `t.uniques().size()`, i = `keyed(t).size() − u`. `noIndex` 와 겹치지 않는다(그쪽은 셋 다 비었을 때). 뷰 제외는 `tables` 가 이미 했다.

### fkTypeMismatch(§3-5)
- FK 마다 부모 `whole.get(refKey(t, fk))` — 없으면(스냅샷 밖) 안 짚는다. 부모 컬럼 = `fk.refColumns()`, 비면 부모 `pk().columns()`(PK 도 없으면 안 짚는다). 자식·부모 열 수가 다르면 안 짚는다.
- 컬럼 짝마다 `differ(child, parent)` — 컬럼을 이름(대소문자 무시)으로 못 찾으면 null. 비교 셋, **둘 다 알 때만**(한쪽이 null 이면 그 항목은 안 본다):
  - 형식 `norm(nativeType)` = 대문자 · `(…)` 뗌 · 공백 하나로 — 다르면 `"형식 VARCHAR2 ≠ NUMBER"`
  - 형식이 같고 `length` 둘 다 있고 다르면 `"길이 20 ≠ 10"`
  - 형식이 같고 `precision` 둘 다 있고 (precision 또는 scale(null 은 0)) 다르면 `"정밀도 10,0 ≠ 8,2"`
- `Finding(FK_TYPE_MISMATCH, s, t.name(), 자식컬럼, "FK <이름> → <부모표>.<부모컬럼>: <why>")` — 짝마다 하나. 동의어 표(INT=INTEGER 등)는 두지 않는다 — 같은 스냅샷은 같은 철자다. R-16 에서 동의어 소음이 보이면 고침 사다리 ① `norm` 에 다섯 짝 안 동의어 표(INT·INTEGER / DECIMAL·NUMERIC / CHARACTER VARYING·VARCHAR / TIMESTAMP WITHOUT TIME ZONE·TIMESTAMP / BOOL·BOOLEAN).

### dupIndex(§3-6)
- `keyed(t)` 의 모든 순서쌍 (b, a), a ≠ b 에서 `covered(b, a)` 가 참인 첫 a 로 b 하나당 지적 하나: `Finding(DUP_INDEX, s, t.name(), b.cols 쉼표, "인덱스 <b> ⊂ <a>(<a.cols 쉼표>)")`.
```java
/** b 가 a 에 덮인다 — b 의 컬럼(대문자)이 a 의 앞머리(b.size ≤ a.size). 정렬 방향은 안 본다 */
static boolean covered(Keyed b, Keyed a) {
    if (b.cols().isEmpty() || b.cols().size() > a.cols().size() || !up(a.cols().subList(0, b.cols().size())).equals(up(b.cols()))) { return false; }
    boolean same = a.cols().size() == b.cols().size();
    if (b.unique()) { return same && a.unique() && (a.pk() || (!b.pk() && b.name().compareTo(a.name()) > 0)); } // 유니크는 제약 — 똑같은 유니크가 또 있을 때만, PK 가 이긴다
    return !same || a.unique() || b.name().compareTo(a.name()) > 0; // 비유니크 — 앞머리면 중복, 똑같으면 유니크 쪽·이름 앞인 쪽이 남는다
}
```
`up(List<String>)` 도우미(대문자 목록). JDBC 스냅샷에서 유니크 인덱스가 `uniques`·`indexes` 양쪽에 같은 이름으로 들어도 `keyed` 가 걸러 자기 자신과 안 맞댄다. H2 의 `CONSTRAINT_INDEX_n` 이 손 인덱스와 겹치면 지적이 난다 — H2 흔적(골든 B, 「드러난 것」).

### fkCycle(§3-7)
- 노드 = `tables`(거른 범위, 뷰 제외) 의 `key(schema, name)`. 간선 = 자식 → 부모(`refKey`), 부모가 노드에 없으면 버림, 자기 참조 버림, 중복 버림. Tarjan SCC(`private static final class Scc` — `index`·`low`·`stack`·`onStack`, 재귀 `strong(v)`; 복잡도 ≤ 15 로 `strong` 과 성분 모으기를 가른다). 크기 ≥ 2 인 성분마다 지적 하나: 이름 목록 = 성분의 표 이름 오름차순(같은 이름이 다른 스키마에 있으면 `스키마.표`), `Finding(FK_CYCLE, 첫 표의 schema, 첫 표 이름, null, "표 N: A·B·C")`. 재귀 깊이는 FK 사슬 길이(표본 최대 수십).

### 시험·골든(§3-8)
- `StructureLintTest` 새 메서드 넷(규칙마다 양성·음성, 기존 `t`·`fk`·`snap`·`of` 도우미 재사용):
  - `noPkOnlyWhenKeyed` — PK 없음 + 인덱스 하나 → 지적 「유니크 0 · 인덱스 1」 · PK 없음 + 아무것도 없음 → noIndex 만(noPk 아님) · PK 있음 → 아님 · 유니크만 → 「유니크 1 · 인덱스 0」
  - `fkTypeMismatchComparesTypeLengthPrecision` — VARCHAR2(20) → VARCHAR2(10) 「길이」 · NUMBER(10,0) → NUMBER(8,2) 「정밀도」 · VARCHAR2 → NUMBER 「형식」 · 길이 한쪽 null → 아님 · refColumns 빈 FK 는 부모 PK 로 · 부모가 스냅샷 밖 → 아님 · `whole` 에만 있는 부모는 비교한다
  - `dupIndexPrefixExactAndUniqueRules` — IX(A) ⊂ IX(A,B) · 똑같은 비유니크 둘 → 이름 뒤 것만 · 비유니크(A) = PK(A) → 비유니크 쪽 · 유니크(A) ⊂ IX(A,B) → 아님 · 유니크(A) = 유니크(A) 둘 → 이름 뒤 것 · 같은 이름이 uniques·indexes 양쪽 → 지적 0 · 빈 컬럼 → 아님
  - `fkCycleReportsEachComponentOnceWithoutSelfRef` — A→B→A 한 줄 「표 2: A·B」 · 자기 참조만 → 0 · A→B→C→A + D→A(순환 밖) → 「표 3: A·B·C」 하나 · 부모가 범위 밖 → 0
  - `offRulesDisappearFromCountsAndOrderIsRuleThenTable` 의 `assertEquals(8, …)` → 12 · `ruleIds` 에 `noPk`·`fkCycle` 한 줄.
- `goldenOnPostgresVendorSnapshot`(`pg-lint.json` 11건) 은 **불변 기대** — 바뀌면 중단 조건.
- `DeliverableRoutesTest.structureLint` `rules` 9 → 13(지적 넷·xlsx 행 4 그대로) · `HrSnapshotCorpusTest.structureLint` counts 9 → 13, `hr-lint.json` 갱신(새 지적은 `fkCycle`「표 2: DEPARTMENTS·EMPLOYEES」 + H2 흔적 `dupIndex` 기대 — 실행해야 안다).
- `DdlCorpusTest.structureLint`(V-47a, 2-33) — 방언마다 `DDLS` 를 합쳐 스냅샷 하나(같은 이름 표는 첫 것, `Schema("", "x", tables)`), 거기에 `LINT_EXTRA`(ruoyi `sql/*.sql` + ruoyi-classic `sql/*.sql`, 방언 `mariadb`, 그룹 `ruoyi:mariadb`) 를 더한다. **시험 쪽 정규식**으로 `CREATE [UNIQUE] INDEX 이름 ON 표 (컬럼…)` 을 붙인다(`strip(text)` 뒤, 표 이름은 마지막 조각 대문자, 컬럼은 쉼표로 갈라 따옴표·`ASC`·`DESC` 뗀 첫 낱말) → `withIndexes`. `StructureLint.run(스냅샷, Options.DEFAULT)`.
  - A(`CorpusFiles.none`): 예외 · 지적의 표가 스냅샷에 없음
  - 골든 `corpus/ddl-lint.json`: 그룹(`<src>:<dialect>`, TreeMap) → `"표 n · noPk a · fkTypeMismatch b · dupIndex c · fkCycle d"` 
  - 골든 `corpus/ddl-lint-findings.txt`: `egov:oracle`·`db-samples:oracle`·`ruoyi:mariadb` 셋의 새 규칙 넷 지적 전부 — 줄 `그룹|규칙id|표|컬럼|detail`, 정렬은 결과 순서. 1,000 줄 넘으면 중단 조건(표본 수치는 문서에, 코드 본문 없음)
- JS 측정(§3-12) 은 `JsCorpusTest`.

### dev_tools 인코딩 판별 탭 — Hexdump·Hex 변환(§3-9)
- `#page-enc` 의 `.row`(미리보기 둘) 아래에 둘을 더한다. 화면 `<style>` 에는 `.enc-bad`·`.enc-hex` 만(토큰만 — `background:var(--red);color:var(--bg)` 꼴, 고정 색 금지):
```html
<div class="row" style="flex-shrink:0;align-items:center">
  <label for="enc_hex_n" style="min-width:auto">Hexdump</label>
  <select id="enc_hex_n" onchange="encHexRender()"><option value="256">앞 256바이트</option><option value="1024" selected>앞 1,024바이트</option><option value="4096">앞 4,096바이트</option><option value="16384">앞 16,384바이트</option></select>
  <span id="enc_hex_bad" style="font-size:11px;color:var(--muted)"></span>
</div>
<pre class="enc-pre enc-hex" id="enc_hex" aria-label="Hexdump" tabindex="0"></pre>
<div class="label">Hex 변환</div>
<div class="row" style="flex-shrink:0">
  <div class="col"><div class="label">입력</div><textarea id="hx_in" aria-label="Hex 변환 입력" placeholder="텍스트, 또는 16진 — EF BB BF · efbbbf · 0xEF,0xBB · %EF%BB · \xEF\xBB"></textarea></div>
  <div class="col"><div class="label" id="hx_out_lbl">출력</div><textarea id="hx_out" aria-label="Hex 변환 출력" class="out-area" readonly></textarea></div>
</div>
<div class="row" style="flex-shrink:0;align-items:center">
  <button type="button" class="btn-p" onclick="hexEncode()">텍스트 → Hex (UTF-8)</button>
  <select id="hx_enc" aria-label="디코딩 인코딩"><option value="utf-8">UTF-8</option><option value="euc-kr">EUC-KR/CP949</option><option value="utf-16le">UTF-16LE</option><option value="utf-16be">UTF-16BE</option></select>
  <button type="button" class="btn-p" onclick="hexDecode()">Hex → 텍스트</button>
  <button type="button" class="btn-green" onclick="copyOut('hx_out')">복사</button>
  <span style="font-size:11px;color:var(--muted)">브라우저는 EUC-KR 로 인코딩하지 못한다 — EUC-KR 바이트는 파일 Hexdump 로 본다</span>
</div>
```
복사 버튼은 기존 `TB.copy(el, showToast)` 를 감싼 도우미(:1720 꼴)를 쓴다 — 이름이 다르면 그 이름으로. 접근성(axe): 모든 입력·출력에 `aria-label` 또는 `<label for>`.
- 순수 함수 여섯(DOM 없음 — corpus-js.js 가 `dev.evaluate` 로 부른다), `encAnalyze` 바로 위에:
```js
function hexOf(bytes, max){ … }            // 'EF BB BF' — 대문자 두 자리, 공백 구분, max 바이트까지(없으면 전부)
function hexDump(bytes, max){ … }          // 줄마다 '00000000  EF BB BF 31 32 2C 33 34  35 36 0D 0A 41 42 43 44  |...12,3456..ABCD|' — 8자리 오프셋, 16바이트(8 뒤 두 칸), 0x20~0x7E 밖은 '.'
function hexToBytes(text){ … }            // 0x·%·\x 접두와 쉼표·공백·줄바꿈을 떼고 16진 짝 → Uint8Array. 홀수·16진 밖 글자 → throw new Error('16진이 아니다: ' + 자리)
function utf8Invalid(bytes, limit){ … }    // 잘못된 UTF-8 시작 오프셋 목록(≤ limit) — 선두 바이트별 길이(C2~DF 1 · E0~EF 2 · F0~F4 3), 후속 10xxxxxx, overlong(E0 A0 미만·F0 90 미만), 서러게이트(ED A0~BF), F4 90 이상·C0·C1·F5~FF, 끝에서 잘린 열. 자리를 하나 건너뛰고 계속
function cp949Invalid(bytes, limit){ … }   // 선두 0x81~0xFE 뒤 후속이 0x41~0x5A·0x61~0x7A·0x81~0xFE 밖, 단독 0x80·0xFF, 끝에서 잘린 선두
function encVerdict(bytes){ … }            // encAnalyze 의 판정을 DOM 없이 — {enc:'utf-16le'|'utf-16be'|'utf-8'|'euc-kr'|'ascii'|null, bom:boolean, badUtf8:n, badEucKr:n, text:'판정 글', ok:boolean}. 판정 글·순서는 지금 encAnalyze 와 글자까지 같다
```
`encAnalyze` 는 `encVerdict` 를 불러 판정 글을 그리고(색은 `var(--green-text)`·`var(--red-text)` — `#4ec9b0`·`#f48771` 제거), 바이트를 `encBytes` 에 두고 `encHexRender()` 를 부른다. `encHexRender` — `enc_hex_n` 바이트까지 `hexDump`, 강조 자리 = 판정이 `utf-8`/`ascii` 면 `utf8Invalid`, `euc-kr` 면 `cp949Invalid`, 그 밖(UTF-16·판별 불가)은 없음. 강조는 그 바이트의 두 글자를 `<mark class="enc-bad">` 로(innerHTML, 나머지는 `esc` — 기존 줄처럼 `// tb-ignore security.domXss 입력은 esc() 경유`), `#enc_hex_bad` 에 「깨진 자리 n(처음 0x0000AB)」 또는 「깨진 자리 없음」. 강조 상한 200 자리.
- `hexEncode` — `hx_in` 글을 `TextEncoder` 로 → `hexOf` 전부, 출력 라벨 「출력 — Hex(UTF-8, n바이트)」. `hexDecode` — `hexToBytes(hx_in)` → `new TextDecoder(hx_enc).decode` 출력, 라벨 「출력 — 텍스트(<enc>, n바이트)」, 오류는 `showToast(e.message)` + 출력 비움.
- 순수본 `pure/` 는 안 건드린다. 런처 `tools/index.html` 개발 도구 설명에 「인코딩 판별(Hexdump)」 을 넣는다(:48).

### corpus-js.js enc 절 + JsCorpusTest(§3-10)
- `corpus-js.js` 바이트 절 뒤에: CSV 100 마다 `fs.readFileSync` 원바이트 → 앞 4,096 바이트 `head` 와 첫 줄 바이트 `line`(첫 0x0A 앞까지, 없으면 head) 을 배열로 `dev.evaluate`:
```js
const r = await dev.evaluate((b, l) => {
  const bytes = Uint8Array.from(b), lb = Uint8Array.from(l), v = encVerdict(bytes), rt = hexToBytes(hexOf(bytes));
  let same = rt.length === bytes.length; for (let i = 0; same && i < rt.length; i++) same = rt[i] === bytes[i];
  return { enc: v.enc, hex64: hexOf(bytes, 64), dumpLines: hexDump(bytes).split('\n').length, roundTrip: same,
           badUtf8: (utf8Invalid(bytes, 1)[0] ?? -1), lineEucKr: new TextDecoder('euc-kr').decode(lb), lineUtf8: new TextDecoder('utf-8').decode(lb) };
}, head, line);
out.enc.push(Object.assign({ file: f.src + '/' + f.rel }, r));
```
- `JsCorpusTest.hexAndVerdictMatchJava`(A, `CorpusFiles.none`) — 항목마다 같은 파일을 Java 로 읽어:
  - `hex64` = `HexFormat.ofDelimiter(" ").withUpperCase().formatHex(head, 0, min(64, n))`
  - `dumpLines` = ceil(n / 16) · `roundTrip` 참
  - `badUtf8` = Java 엄격 UTF-8 디코더(`CodingErrorAction.REPORT`, `decode(in, out, true)`)가 멈춘 `in.position()`(오류 없으면 −1) — JS 상태기계와 바이트 자리까지 같다
  - `enc` = 파일 전체가 엄격 UTF-8 로 읽히면(`CsvCorpusTest` 규칙) 비ASCII 있음 → `utf-8` · 전부 ASCII → `ascii`, 아니면 `euc-kr`. `csv-broken-bytes.txt` 의 파일(UTF-8 도 MS949 도 엄격히 안 읽힘)은 판정을 안 본다
  - MS949 파일은 `lineEucKr` = `new String(line, MS949)` · UTF-8 파일은 `lineUtf8` = `new String(line, UTF_8)`(BOM 포함) — WHATWG euc-kr(windows-949) 와 Java MS949 의 표 대조. 어긋나면 고침 사다리가 아니라 **B**(`CorpusFiles.baseline("js-enc-decode", …)`) 로 내리고 「드러난 것」
- 걸리는 시간은 CSV 100 × 4 KB — 초 단위.

### 글·문서(§3-11)
- 「아홉 규칙」 → 「열세 규칙」: `README.md:38`(규칙 예에 「FK 순환」 추가) · `DocCommands.Lint` description · `PLAN.md:170`(규칙 목록에 넷 이어 붙임) · `StructureLint` 클래스 javadoc(「아홉 규칙」 → 「열세 규칙(설계 25 아홉 + 설계 51 넷)」, 「PK 없는 표는 90 칸」 문장을 noPk 설명으로) · 91 카드 안내 `:103` 「PK 없는 표 전체는 위 90 의 버튼, 91 은 인덱스·유니크만 있는 표(noPk)와 아무것도 없는 표(noIndex)를 가른다」. `profiles/example.yaml` 주석은 그대로(`off` 에 id 를 적는 규칙은 같다).

### 측정·판정(§3-12)
- V-47a(= 2-33 골든) 수치: `ddl-lint.json` 열넷 그룹의 건수 · `hr-lint.json` 변화 · `pg-lint.json` 불변 — 이력과 compare ⑰ 표 「새 규칙 넷 × 표본」.
- R-16 손 판정 — `ddl-lint-findings.txt`(egov oracle · HR · ruoyi) 와 `hr-lint.json` 을 읽고 규칙마다 ≤ 20 줄을 참·참(규칙 정의)·오탐으로 가른다. 코드 본문은 안 옮긴다(표·컬럼 이름·detail 은 메타라 적어도 된다). 결함 둘 이하는 고침(픽스처 음성 먼저 → RED → 고침 → 2-33 골든 다시), 넘으면 새 행 번호를 「후보로」. 사다리: `fkTypeMismatch` 동의어 소음 → §3-5 ① · `dupIndex` 의 H2 `CONSTRAINT_INDEX_n` → 고치지 않고 「드러난 것」 · `fkCycle` 의 모듈 간 참조 과다 → 성분 크기 분포만 적는다.
- V-47 — `design/compare-2026-10-28.md` 에 `## ⑰ 재측정 — 번들 67(2026-11-01)`: 표 「새 규칙 넷 × DDL 표본」(그룹 14 행) · 「Hex·판정 × CSV」(hex 100/100 · 판정 일치 n/99 · 줄 디코딩 일치 n) · `--corpus` 벽시계 · 「드러난 것」 · 「후보로」(DdlReader CREATE INDEX·인라인 KEY — 번들 68 db 레인 · 다-17·19 · 바-7 Charcode·진법 · 바-6 해시 · 동의어 표). PLAN 4장 V-7 행 끝에 「인코딩 판별·Hex 는 번들 67 V-47a(`JsCorpusTest.hexAndVerdictMatchJava`)」 한 줄. 집 검증 `node scripts/puppeteer/visual.js hex67 --only dev_tools`(dark·light × 1400·375, 콘솔 오류 0, axe 0) — 인코딩 탭을 연 상태를 못 찍으면 탭 기본 화면만 찍고 이력에 적는다.

## 4. 실행 스텝
### S0 — 설계 문서·행·번들 표 (실행 세션이 옮긴다)
- 대상: `design/51-bundle67.md`(이 플랜 A 절 그대로) · `PLAN.md` 번들 표 「번들 67」 행 · 분할표 `2-32a`·`2-32b`·`2-33`·`4-28`·`4-29`·`R-16`·`V-47`(B 절 글, `V-46` 뒤) · `PROGRESS.md` 이력 한 줄.
- 검증: `bash scripts/doc-lint.sh design/51-bundle67.md` · `bash scripts/verify.sh`(docs 레인) · `git commit -m "docs: S0 설계 51 — 번들 67"`.
- 의존: 없음.

### 2-32a — `StructureLint` 넷 + `StructureLintTest`
- 대상: `core/deliverable/StructureLint.java`(§3-1~7) · `core/deliverable/StructureLintTest.java`(§3-8 메서드 넷 + 8 → 12 + `ruleIds`).
- 변경(TDD): ① 시험 넷 + 12 → 컴파일 RED(enum 없음). ② enum 넷 + `structural`·`keyed`·`noPk`·`fkTypes`·`differ`·`norm`·`dupIndexes`·`covered`·`cycles`·`Scc` → GREEN. `run` 에는 호출 한 줄만.
- 검증: `bash scripts/mvn.sh -q test -Dtest='StructureLintTest,SelfCheckTest,ArchitectureTest'` 초록 · `pg-lint.json` diff 0 · `self-check.txt` diff 0(새 메서드 전부 ≤ 15, `run(3)` 50 그대로) · `pmd:check` 0(기존 짝 안에서).
- 의존: S0.

### 2-32b — 수 13 + 글 넷
- 대상: `web/DeliverableRoutesTest.structureLint`(9 → 13) · `web/HrSnapshotCorpusTest.structureLint`(9 → 13, 골든은 2-33) · `cli/DocCommands.Lint` 설명 · `README.md:38` · `PLAN.md:170` · `tools/deliverable_sql.html:103` 안내 글 · `StructureLint` javadoc(§3-11).
- 검증: `bash scripts/mvn.sh -q test -Dtest='DeliverableRoutesTest,SmokeHtmlUnitTest,ToolsFolderTest,CliCommandsTest'` 초록(CLI 시험 이름이 다르면 `cli` 폴더 시험 전부) · `bash scripts/verify.sh` 초록.
- 의존: 2-32a.

### 2-33 — DDL 표본 구조 점검 골든(V-47a) + HR 골든 갱신
- 대상: `core/gen/DdlCorpusTest.java`(`LINT_EXTRA` 로 ruoyi 둘 읽기 · `CREATE_INDEX` 정규식 · `structureLint()`) · 골든 `corpus/ddl-lint.json`·`corpus/ddl-lint-findings.txt`(새) · `corpus/hr-lint.json`(갱신).
- 변경(TDD): ① 시험 메서드 + 골든 없음 → RED(골든 없음 메시지). ② `-Dgolden.update=true` 로 `DdlCorpusTest#structureLint`·`HrSnapshotCorpusTest#structureLint` 만 → diff 읽기(새 파일 둘 · `hr-lint.json` 새 행만 · `ddl-summary.json`·`ddl-unreadable.txt` diff 0) → 갱신 없이 다시 GREEN. `ddl-summary.json` 이 바뀌면(ruoyi 를 `DDLS` 에 넣었다는 뜻) 되돌린다 — ruoyi 는 `LINT_EXTRA` 에만.
- 검증: `bash scripts/mvn.sh -q test -Djunit.timeout=10m -Dtest='DdlCorpusTest#structureLint+everyCreateTableIsRead,HrSnapshotCorpusTest#structureLint'` 초록(`verify.sh` 는 `-DexcludedGroups=db,corpus` 로 뺄 뿐이라 `-Dtest` 로 집으면 태그 플래그 없이 돈다) · `ddl-lint-findings.txt` ≤ 1,000 줄 · 지적 건수를 이력에.
- 의존: 2-32b.

### 4-28 — dev_tools Hexdump·Hex 변환
- 대상: `tools/dev_tools.html`(§3-9 — HTML·CSS 두 클래스·함수 여섯·`encAnalyze`·`encHexRender`·`hexEncode`·`hexDecode`) · `tools/index.html:48` 한 구절.
- 변경: TDD 예외(화면 HTML/JS — 2026-10-29 허락). RED→GREEN 자리는 4-29 의 `JsCorpusTest.hexAndVerdictMatchJava`. 이 스텝의 번호 시험은 `ToolsFolderTest`(고정 색·TB.copy 수)·`BackendToolsTest`·`SmokeHtmlUnitTest`(dev_tools 는 JS 끔 — 마크업만).
- 검증: `bash scripts/mvn.sh -q test -Dtest='ToolsFolderTest,BackendToolsTest,SmokeHtmlUnitTest'` 초록 · 브라우저로 `run.bat` 뒤 인코딩 탭에 `C:/workspace/toolbox-corpus/csv/15001446.csv`(MS949) 떨어뜨려 Hexdump 와 강조 · Hex 변환 `EA B0 80` → UTF-8 「가」 · `B0 A1` → EUC-KR 「가」 · 콘솔 오류 0(눈으로 한 번, 이력에 한 줄).
- 의존: 2-33(가지 순서일 뿐 — 코드 의존 없음).

### 4-29 — corpus-js.js enc 절 + `JsCorpusTest.hexAndVerdictMatchJava`
- 대상: `scripts/puppeteer/corpus-js.js`(`out.enc`) · `web/JsCorpusTest.java`(§3-10).
- 변경(TDD): ① 시험 먼저 → RED(`summary.json` 에 `enc` 없음). ② corpus-js.js 절 → GREEN. PMD 새 짝 금지(`JsCorpusTest` 는 기준선에 없다 — 리터럴은 상수로, `if` 에 리터럴 금지).
- 검증: `bash scripts/mvn.sh -q test -Djunit.timeout=10m -Dtest='JsCorpusTest'` 초록(node 필요, 태그 플래그 없이) · 판정 일치 수·디코딩 일치 수를 이력에 · `VerifyLanesTest` 초록.
- 의존: 4-28.

### R-16 — 손 판정(새 규칙 넷 × egov oracle·HR·ruoyi)
- 대상: `design/compare-2026-10-28.md`(⑰ 「손 판정」 절) · `PROGRESS.md` · (고침이 나면) `StructureLint`·`StructureLintTest`·골든.
- 변경: §3-12. 표 하나(규칙 · 표본 · 표:컬럼 · 판정 · 근거) + 분류 합. 결함 둘 이하는 고치고(음성 한 줄 먼저 → RED → 고침 → 2-33 명령으로 골든 다시) 넘으면 「후보로」.
- 검증: `bash scripts/doc-lint.sh design/compare-2026-10-28.md` · 분류 합 = 줄 수 · (고침 있으면) 2-32a·2-33 검증 재실행.
- 의존: 4-29.

### V-47 — compare ⑰ · PLAN 4장 · `--corpus` · 집 검증
- 대상: `design/compare-2026-10-28.md`(⑰ 나머지) · `PLAN.md` 4장 V-7 행 한 줄 · `PROGRESS.md`.
- 검증: `bash scripts/verify.sh --corpus` 초록(약 20분 — 다른 컨테이너·WSL 내리고) → `bash scripts/verify.sh` 초록. `--db` 는 끝 줄이 부르지 않아야 한다(db 레인 파일을 안 고쳤다 — 부르면 중단 조건). `node scripts/puppeteer/visual.js hex67 --only dev_tools` 결과(샷 4 · 판정 0 · axe 0 · 콘솔 0)를 이력에.
- 의존: R-16.

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7 · 행의 축·강제 지점과 diff · 규칙 넷의 양성·음성(§3-8 의 결정적 음성 — noIndex 와 noPk 겹침 0 · 한쪽 null 비교 안 함 · 유니크 앞머리 아님 · 자기 참조 아님 · 범위 밖 부모) · `run(3)` 복잡도 50 불변 · `self-check.txt` diff 0 · `pg-lint.json`·`ddl-summary.json` 불변 · 골든이 의도된 변화인가 · 화면 고정 색 0 · `hexToBytes`·`utf8Invalid` 경계(홀수·끝 잘림·F4 90) · innerHTML 이 `esc` 경유인가) → `bash scripts/gate-probe.sh` → `bash scripts/verify.sh` → `git push -u origin work/2026-11-01c` → `gh pr create --base main`(행마다 시작 세 줄 표 + 로컬 셋 `2df04f9`·`6912e5f`·`3f587ca` 같이 + ⑰ 핵심 수치) → CI 폴링(`gh api … check-runs`) → claude[bot] 처분 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → 닫기 커밋은 다음 가지 → 끝 보고. PR 본문 끝 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## 5. 금지 사항
- `pure/`·portfolio·`docs/`·`core/gen/DdlReader.java`·`core/meta`·`core/dialect`·`pom.xml`·`scripts/verify-fingerprint.sh`·`corpus/MANIFEST`·표본 수정 금지 · 새 Maven 의존성 금지 · 새 화면·새 탭·서버 라우트 금지(사용자 (가))
- **`StructureLint.run(3)` 에 분기 추가 금지**(호출 한 줄만) · 새 메서드 복잡도 ≤ 15 · `self-check.txt` 새 키·바뀐 줄 금지 · PMD 새 짝 금지(`JsCorpusTest` 포함) · `Rule` 기존 아홉의 순서·id·label·meaning·판정 불변 · `Options`·`Profile.Lint` 키 추가 금지 · `basis` 세 줄 불변
- 기존 골든 `deliverable/pg-lint.json`·`corpus/ddl-summary.json`·`corpus/ddl-unreadable.txt`·`csv-*`·`js-*` 불변 · `DdlCorpusTest.DDLS` 에 ruoyi 를 넣지 않는다(`LINT_EXTRA`) · `DdlCorpusTest` 기존 시험 넷의 판정 로직 수정 금지
- 화면: `<style>` 에 `:root`·`button{}`·`.btn-*`·고정 색 금지 · innerHTML 은 `esc` 경유 + `tb-ignore security.domXss` 주석 · `dev_tools_ext.js` 에 넣지 않는다(인코딩 탭은 dev_tools.html) · `cp949len` 근사 함수 수정 금지 · 기존 탭 16 의 순서·id 불변
- 골든·compare·이력에 CSV 내용·코드 본문을 싣지 않는다(규칙 3 — 표·컬럼 이름·detail·16진 64바이트 비교는 시험 안에서만, 문서엔 건수) · 원본 되쓰기 API·CLI 금지 · 표본 추가 금지 · `pmd-baseline.sh` 안 돌린다 · `--db` 안 돌린다

## 6. 최종 검증
- `bash scripts/verify.sh` · `--corpus` 초록 · `--db` 안 부름 · `pmd:check` 0 · `gate-probe.sh` 10/10
- 골든: `pg-lint.json` 불변 · `hr-lint.json` 새 행(fkCycle 1 + H2 dupIndex n) · `ddl-lint.json` 14 그룹 · `ddl-lint-findings.txt` ≤ 1,000 줄 · `ddl-summary.json`·`ddl-unreadable.txt` 불변 · `self-check.txt` 불변
- 시험: `StructureLintTest` 새 넷 + 12 · `DeliverableRoutesTest` 13 · `HrSnapshotCorpusTest` 13 · `DdlCorpusTest.structureLint` A 0 · `JsCorpusTest.hexAndVerdictMatchJava` A 0(판정 99/99 또는 B 사유) · `ToolsFolderTest` 고정 색 0(둘 줄어듦) · `VerifyLanesTest` 초록
- 수동: 산출물 화면 데모 스냅샷 구조 점검 → 규칙 13 건수 줄 · xlsx 열 여섯 그대로 · 개발 도구 인코딩 탭 Hexdump·Hex 변환(4-28 검증) · `visual.js hex67` 샷
- compare ⑰ 표 둘 + R-16 표 + 「드러난 것」 + 「후보로」 · 이력에 건수·벽시계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- `self-check.txt` 가 바뀜(run 복잡도·새 메서드 > 15) · `pg-lint.json`·`ddl-summary.json`·`ddl-unreadable.txt` 변화 · `pmd:check` 새 짝 · `ArchitectureTest`·`VerifyLanesTest` 빨강
- `ddl-lint-findings.txt` 1,000 줄 초과(규칙 하나가 표본 전체를 덮는다 — 판정을 좁힐지 사용자 결정) · `DdlCorpusTest.structureLint` 예외 · `hr-lint.json` 에 fkCycle 이 없음(SCC 가 틀렸다)
- 4-29 에서 `hex64`·`roundTrip`·`dumpLines` 어긋남(Hex 함수 결함 — 고침 둘로 안 닫히면) · `badUtf8` 자리 어긋남 셋 이상(상태기계 정의 차이 — Java 를 기준으로 고침, 둘로 안 닫히면) · 디코딩 어긋남은 중단이 아니라 B(§3-10)
- `verify.sh` 끝 줄이 `--db` 를 부름 · 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① `ddl-lint.json` 건수 열넷 그룹(기대: egov goldilocks FK 규칙 0 · cubrid 거의 0 · mysql·pg dupIndex 가 oracle 보다 많다 · HR 순환 1) ② `hr-lint.json` 의 H2 `CONSTRAINT_INDEX_n` dupIndex 건수 ③ `fkTypeMismatch` 가 egov 에서 동의어 소음을 내는지(R-16 사다리 ①) ④ `pg-lint.json` 불변 ⑤ WHATWG euc-kr 과 Java MS949 의 줄 디코딩 일치 수(84 기대, 어긋나면 B) ⑥ `badUtf8` 자리 JS=Java 일치(100 기대) ⑦ `--corpus` 벽시계(66 은 1,240초) ⑧ `visual.js` 가 인코딩 탭 상태를 찍는지. 첫 스텝 전: `bash scripts/verify.sh` 초록 · `git log --oneline -1`(`3f587ca`) · `ls C:/workspace/toolbox-corpus | wc -l`(33) · `node --version`.
- **사용자만 정할 수 있는 것** — 없음((가) Hex 클라이언트만 · (나) 머지 위임 전부 2026-11-01 결정).
