# 설계 27 — 번들 43: DdlReader UNIQUE · ENUM/SET 값 · axe-core 시각 검증 · 화면 a11y 게이트 (Fable, 2026-10-18)

원본은 설계 세션 플랜 파일이다. 설계 세션이 바로 옮겨 커밋했다(S0). 실행은 `4-26` 부터다.

이 플랜은 실행 담당 모델(Opus)이 그대로 따라 실행한다. 실행자는 탐색 과정을 모른다. 판단이 필요한 지점을 남기지 않았다. 번들 모드 규칙(CLAUDE.md 「번들 모드」·「청크 규칙」·「마무리」)을 따른다.

인계: 앞 Opus 세션(Fable 결제가 막힌 세션)이 사용자 지시로 번들 43 설계를 넘겼다. 범위는 4-26 · 4-25 · UI 기계 검사 둘(0-60 axe-core · 5-30 화면 a11y 게이트)이다. UI 둘은 ejg-db 세션이 조사해 제안했고 사용자가 골랐다. 메모리 「검증·인프라 행 제안 안 함」(10-06)의 예외로 사용자가 승인했다.

사용자 결정(2026-10-18, 뒤집지 않는다):
- UI ① → ② 둘 다. 색 대비는 axe 표준 **4.5**(visual.js 손 판정 3.0 은 대체).
- **4-25 는 DDL + 스냅샷 둘 다**(4-25a·4-25b) — 스냅샷 쪽은 db 레인(V012 + MariaDB COLUMN_TYPE), 마무리 `--db`.
- **5-30 은 백엔드본을 고치고 같은 고침을 portfolio 지시문으로** — `sql_snippets.html`(순수본 그대로)은 검사에서 제외.

보정(번들 42 ↔ 이 번들): 번들 42 「정한 것」은 코드 검사 억제 엔진·regex REDOS 처분 — 이 번들의 코드 검사 쪽은 **규칙·픽스처를 안 바꾼다**(§3) 라 닿지 않는다. 드러난 것 둘을 그대로 쓴다 — 새 정규식 청크(4-26·4-25a)는 `scripts/mvn.sh -q -B -DskipTests compile spotbugs:check` 를 검증에 넣는다 · `--db` 앞 메모리 확인(4-25b).

---

### 1. 목표와 범위

- **목표**: CREATE 탭(「CREATE 문에서」)이 UNIQUE·ENUM/SET 을 읽어 순수본과 같은 키 겹침 경고·값 순환을 내고, MariaDB/MySQL 스냅샷의 ENUM/SET 컬럼이 나열 값을 실어 「스냅샷에서」 INSERT 가 실제 들어가는 값을 내며, 집 시각 검증이 axe-core(WCAG 2.2 AA, 대비 4.5, target-size)로 화면을 판정하고, 화면 폴더에 우리 코드 검사 ㅈ 묶음이 0건임을 `mvn verify` 가 지킨다.
- **체감**: CREATE 탭에 `CONSTRAINT uq UNIQUE (use_yn)` 표를 넣고 5행 → 「USE_YN: 허용 값이 2개인 키 컬럼이라 5행이면 값이 겹친다」. `st ENUM('A','B','C')` → 'A','B','C','A'… MySQL 스냅샷 ENUM 컬럼도 같다(지금은 `'VAL_1'` 로 깨짐). 탭·모드 버튼이 Tab 키로 닿고 Enter 로 눌린다. `node scripts/puppeteer/visual.js x` 가 `a11y.json` 을 내고 serious·critical 이면 빨강.
- **In**: `S0`(설계 커밋 — 끝남) → `4-26` → `4-25a` → `0-60a` → `5-30` → `0-60b` → `4-25b` → 마무리. 가지 `work/2026-10-18b`. 머지 위임 없음(PR 열고 멈춤).
- **Out**: `pure/`(동결 — 지시문만) · a11y 규칙 정규식 수정(→ **5-31 대기**: inputLabel 감싼-label 오탐) · PG enum 타입(순수본도 안 다룬다 — JDBC TYPE_NAME 이 사용자 타입 이름) · `CREATE UNIQUE INDEX` 문 읽기(DdlReader 는 CREATE TABLE·ALTER·COMMENT 만 — 기존 규약) · PG `UNIQUE NULLS NOT DISTINCT` · 클릭 div 를 `<button>` 으로 바꾸기(공용 `button{}` 이 붙어 그림이 바뀐다) · axe best-practice 태그 · CI 에 Puppeteer.

---

### 2. 현재 구조 요약(2026-10-18 실측, main `e5e604e`)

경로는 `src/main/java/kr/ejg/toolbox/` 기준.

#### 2.1 DdlReader(`core/gen/DdlReader.java`)
- `read()` :97-186. 문장마다 CREATE 머리 → 괄호 몸통을 `splitTop(body, ',')` → `item(d, item, unreadable)`. 끝 :179 `withConstraints(pk, d.fks, List.of())` — **UNIQUE 를 버린다**. `Draft`(:81-95) 에 `pk`·`fks`·`checks` 만.
- `item()` :188-251: `CONSTRAINT_LINE`(:61, UNIQUE 포함)에 걸리면 PK·FK·CHECK 만 줍고 return(:192-208). 컬럼 줄은 `TYPE_END`(:65, UNIQUE 포함)로 타입을 끊고 :228 `base = type.replaceAll("\\([^)]*\\)", " ")` — **`ENUM('A','B')` → `ENUM`, 목록을 버린다**. `size` 정규식은 숫자만(ENUM 괄호엔 안 걸려 length null). `inlinePk`(:222)는 `\bPRIMARY\s+KEY\b` 를 `upper`(이름 뒤 나머지)에서 본다.
- ALTER: `ALTER_TABLE`(:48) lookingAt 뒤 나머지에 `FK`(:54) find 반복(:148-156) · `ALTER_PK`(:45). UNIQUE 는 없다.
- FK 정규식 꼴(:54) `(?:CONSTRAINT\s+(\S+)\s+)?FOREIGN\s+KEY\s*(?:([^\s(]+)\s*)?\(([^)]*)\)…` — 이름 g1 또는 g2(MySQL 인덱스 이름). UNIQUE 도 같은 꼴로.
- 중첩 반복 금지(:36-38 주석·SpotBugs REDOS — 번들 42 드러난 것).

#### 2.2 InsertGen(`core/gen/InsertGen.java`)
- `record Col(name, type, len, prec, scale, notNull, def, allowed, pk, uq, auto)` :55 — 11칸. 생성 `cols()` :112 하나 + 시험 `InsertGenTest` 7곳(:119·120·128·129·137·143·144).
- `cols(Table, allowed)` :93 — `uq` = `t.uniques()` + 유니크 `indexes()`. `type(nativeType)` :72 가 괄호를 지운다(`ENUM('A','B')` → `ENUM`).
- `genVal` :208 순서: allowed(CHECK IN) → `_YN` CHAR(1) → def(pk·uq 아닐 때) → BOOL → BIT → TINYINT → UUID → JSON → CHAR류 → 수 → 날짜 → CLOB → BLOB → `'VAL_n'`. **ENUM/SET 갈래 없음** → `'VAL_n'`.
- `domain` :332 — allowed → `_YN` → BOOL/BIT/TINYINT(1). ENUM 없음. `keyOverlap` :295.
- 순수본 `tools/dev_tools.html` `genVal` :1159-1168 — def 뒤·BOOL 앞에 `if(/^(ENUM|SET)$/i.test(t) && col.size){ ev = col.size.split(',')…; return ev[idx % ev.length]; }`(`col.size` = 괄호 안 원문 `'A','B'`, 값에 따옴표 포함). `narrowVals` :1277 도 같은 자리.
- DDL 모드 `allowed` 는 `web/InsertRoutes.java:78` `InsertGen.checkIns(req.ddl())`(원문 정규식) — 바꿀 것 없음.

#### 2.3 메타 수집·저장
- `core/meta/JdbcMetaSource.loadColumns` :111 — `TYPE_NAME` 그대로(MariaDB·MySQL 드라이버는 ENUM 컬럼에 `ENUM`, 목록 없음). `uniqueIndexes` :222.
- `core/dialect/MariaMetaSource.java` — `VendorMetaSource` 상속. `loadConstraints` :139 가 `vendor("uniques", base, …)`·`fkRules`·`checks` 를 차례로. **`loadColumns` 오버라이드 없음**. 상수들 :26-52(`COL_COMMENTS` 가 이미 `information_schema.COLUMNS` 를 쓴다). `vendor(kind, fallback, call)` :47 — SQLException 이면 fallback + warnings. `prepare(sql)` :60.
- `core/meta/SnapshotStore.java:135` `snap_column` INSERT, `native_type` 은 `V001__init.sql:72` **`VARCHAR(128)`** — `enum('a',…,'z')` 긴 목록은 넘쳐 저장 전체가 깨진다(V008 이 CHECK 조건에서 같은 일을 겪음). 마지막 마이그레이션 `V011`. `core/db/DbTest` :49·:64·:67 `11`.
- nativeType 소비자(괄호 든 값이 가도 되는지): `InsertGen.type` 괄호 제거 ✓ · `TypeMapping.norm` :75 괄호 제거 ✓(DTO 자바 타입 불변) · `DdlGen.nativeWithSize` :328 **`raw.contains("(")` 면 raw 그대로** ✓(같은 방언 변환이 지금 `ENUM` 만 내던 깨진 DDL 을 `ENUM('A','B')` 로 — 덤 개선) · `Definitions:299`·`CodeAndLink`·`ColumnInputs`·`DeliverableService` 는 글자 그대로 싣는다(정의서 타입 열에 목록이 보인다 — 의도) · `SnapshotDiff:97`·`AlterGen:323` 은 글자 비교(옛 스냅샷 `ENUM` ↔ 새 `enum('a','b')` 가 한 번 타입 변경으로 보인다 — 이력에 적는다).

#### 2.4 시험 자리(4-26·4-25)
- `core/gen/DdlReaderTest.matchesSnapshotTakenFromTheSameDdl` :42-72 — 샘플 넷(`src/test/resources/sample/{postgres,mariadb,mssql,oracle}.sql`)을 읽어 골든 `meta/*.json` 과 컬럼·PK·코멘트만 견준다. **uniques 는 안 본다.** 샘플 넷 전부 `CONSTRAINT uq_products_code UNIQUE (code)`·`CONSTRAINT uq_users_login UNIQUE (login_id)`, 골든에 `uniques: [{name: uq_products_code, columns:[code]}]` 꼴(`ux_users_email` 은 `CREATE UNIQUE INDEX` — 골든엔 인덱스로). 샘플·골든에 ENUM 은 없다(grep 0).
- `core/gen/InsertGenTest` — `golden/insert/*` 는 PG 벤더 JSON(`meta/postgres-vendor.json`) 표에서 → **4-26·4-25 로 불변**. `keyOverlapUniqueAndIndexMerged` :238 는 UniqueKey 를 손으로 넣는다. `valueRules` :77 는 `DdlReader.read(DDL)`.
- `core/gen/DdlCorpusTest`(corpus) — `summaryGolden` 은 파일·테이블·못 읽은 줄 수만 → 불변. `insertGenerationNeverThrows` 가 표본 DDL 전부 × 방언으로 돌린다(새 갈래의 예외 방어).
- `core/gen/DdlGenTest` — 입력은 골든 Table(DdlReader 는 :137·:146 모서리만) → 불변.
- `core/dialect/MariaMetaSourceTest`(db, `mariadb:11`, `sample/mariadb.sql`) — `vendorCollectMatchesGolden`(warnings 비어야·8표·골든) · `metaMore`(임시 표).

#### 2.5 visual.js(`scripts/puppeteer/visual.js`, 309줄)
- 샷마다 `collectButtons` → `judge(tool, data)` :118-138 — ① 묻힘 :130 · **② 대비 3.0 :132** · ③ 체계 :133-135. `JUDGE = LABEL !== 'before'` :26. `SKIP_TOOLS = {sql_snippets}` :115. 출력 `styles.json`·`console.json`·`summary.txt` :294-305, 종료 코드 :307.
- Puppeteer 25.8.0 `C:/workspace/node_modules`, `C:/workspace/package.json` 있음(의존성 8, axe-core **없음**). `ToolsFolderTest.puppeteerDoesNotWaitForNetworkIdle0` 이 스크립트 폴더를 본다(networkidle2 유지).

#### 2.6 화면 ㅈ 묶음 실측 — 62건(규칙 정규식 그대로 node 로 잼, 자바 `RegexRule` 과 같은 줄 단위 `find`, html 은 `<!-- -->` 지움)
| 파일 | 규칙 | 건수 | 자리 |
|---|---|---|---|
| dev_tools.html | a11y.mouseOnly | 32 | `.tab` 16(:124-139) · `.b64-tab` 3(:413-415)+5(:442-446) · `.sql-db-btn` 5(:484-488) · `.url-tab` 3(:523-525) |
| dev_tools.html | a11y.inputLabel | 13 | :153-166 `<label class="radio-btn" id="lbl_…"><input type="radio" name="dir|mode">` 11 · :713-714 `name="il_q"` 2 — 전부 label 이 감싼다(규칙 오탐) |
| dev_tools.html | a11y.imgAlt | 1 | :1446 JS 문자열 `label: '<img> 태그'`(오탐) |
| dev_tools.html | a11y.thScope · tableCaption | 1+1 | :1739 JS 템플릿 `<table class="by-table"><tr><th>줄</th>…`(바이트 계산) — 파일에 scope·caption 이 하나도 없다 |
| jsp_formatter.html | a11y.mouseOnly | 5 | :138-139 `<span class="t" onclick="showPane(…)">` · :660·:672·:683 JS 템플릿 `<div class="it … link" onclick="jumpLine(` |
| sql_snippets.html | a11y.mouseOnly | 5 | 순수본 그대로(`ToolsFolderTest.sqlSnippetsIsPurePlusCommonJs`) → **제외** |
| table_builder.html | a11y.inputLabel | 3 | :105-107 `<span class="opt"><input type="radio" name="mode">`(label 없음 — 진짜 결함) |
| table_builder.html | a11y.iframeTitle | 1 | :144 `<iframe id="pv" sandbox="">` |
- 규칙 자리 `src/main/resources/check/rules.yaml:486-589`(a11y 12). `core/check/PureCodeCheckTest`(corpus)가 `fixtures/check/a11y` 를 **순수본 JS 엔진(주입된 `pure-rules.json`)** 과 맞댄다 → 자바 정규식을 고치면 corpus 빨강 + portfolio 재주입 왕복. 그래서 이 번들은 규칙을 안 고친다.
- 시험 틀: `web/ToolsFolderTest`(`DIR = src/main/resources/tools`, `files()` :31) · `core/check/CheckRulesTest.profile(CodeCheck)` :33(패키지 공개 static) · `Profile.CodeCheck` 4칸 + 3인자 호환(:131) · `Source(rel, text, encoding, lineEnding, lang)` · `rules.run().apply(s)` + `finish()`(5-29b 규약).
- JS 참조 안전: dev_tools 라디오는 `input[name=mode|dir|il_q]`(:769·770·1755·1989-1990)·`lbl_*`(:784-785)로만 고른다. table_builder 는 `input[name=mode]`(:479·773), `.opt` CSS 는 클래스 선택자(:14 `.opt.disabled`). `common.css:48` `.opt{display:flex…}` 도 클래스. HtmlUnit 스모크·visual.js 는 `#main-tabs .tab[data-tab]` 를 쓴다(속성 추가에 안전).
- `common.js`(478줄) IIFE :8-476, `window.TB` :468, `document.addEventListener('visibilitychange', …)` :472 — 키 처리 한 줄을 둘 자리.
- 대비 예측(토큰 계산): dark `--muted #858585` 가 `--surface #252526` 위 ≈ **4.15**(빨강, `--bg` 위 4.52), light `--muted #6b7686` 가 `--bg #f7f9fc` 위 ≈ **4.26**(빨강, `#fff` 위 4.52). `.btn-p` 흰 글 on `#0078d4` ≈ 4.53. 토큰 자리 `common.css:12·21·30`.

---

### 3. 설계 결정

**[고정] 4-26 UNIQUE 읽기** — `Draft.uqs: List<UniqueKey>`. 정규식 하나(FK 와 같은 꼴, 앵커 없음, 중첩 반복 없음):
```java
/** 제약 줄·ALTER 의 UNIQUE — CONSTRAINT 이름(g1) 또는 MySQL `UNIQUE KEY 이름 (c)`(g2), 컬럼 g3 */
private static final Pattern UQ = Pattern.compile(
        "(?:CONSTRAINT\\s+(\\S+)\\s+)?\\bUNIQUE\\b(?:\\s+(?:KEY|INDEX))?(?:\\s+(?:NON)?CLUSTERED)?(?:\\s+([^\\s(]+))?\\s*\\(([^)]*)\\)",
        Pattern.CASE_INSENSITIVE);
```
- `item()` 제약 분기(:202 FK 뒤): `Matcher u = UQ.matcher(item); if (u.find()) d.uqs.add(new UniqueKey(u.group(1) != null ? unquote(u.group(1)) : u.group(2) != null ? unquote(u.group(2)) : null, names(u.group(3))));`
- 컬럼 분기(:222 옆): `boolean inlineUq = Pattern.compile("\\bUNIQUE\\b").matcher(upper).find();` → `if (inlineUq) d.uqs.add(new UniqueKey(null, List.of(name)));`(패턴은 static 상수로).
- ALTER(:152 FK 반복 옆): `Matcher u = UQ.matcher(stmt.substring(at.end())); while (d != null && u.find()) d.uqs.add(…)`.
- :179 `withConstraints(pk, d.fks, List.copyOf(d.uqs))`.
- 버린 것: `CREATE UNIQUE INDEX` 읽기(DdlReader 범위 밖 — 인덱스는 `Table.indexes()` 가 따로고 순수본도 안 읽는다) · PK 와 같은 컬럼의 UNIQUE 제거(DdlGen:252 가 이미 거른다, InsertGen 은 묶음 집합).

**[고정] 4-25a ENUM/SET — DDL 경로**
- `DdlReader:228` 뒤: `String nativeType = base.toUpperCase(Locale.ROOT).matches("ENUM|SET") ? type.trim() : base;` → Column 의 nativeType 에 `nativeType`. 그 밖 컬럼은 그대로(`base`). 벤더 원문 규약과 맞다(MySQL `SHOW COLUMNS` Type 열이 이 꼴).
- `InsertGen.Col` 12번째 `List<String> values`(ENUM/SET 나열 리터럴, 따옴표 포함 `'A'`) + **11인자 호환 생성자**(`this(…, List.of())`) — 시험 7곳 불변.
- `cols()`: `List<String> values = ty.matches("ENUM|SET") && nt != null && nt.indexOf('(') > 0 && nt.lastIndexOf(')') > nt.indexOf('(') ? splitVals(nt.substring(nt.indexOf('(') + 1, nt.lastIndexOf(')'))) : List.of();`(`splitVals` :142 가 `''` 이스케이프·쉼표를 이미 다룬다).
- `genVal` — def 검사(:218-220) **뒤**, BOOL(:221) **앞**: `if (!c.values().isEmpty()) return c.values().get(idx % c.values().size());` (순수본 순서 그대로 — DEFAULT 'A' 가 있고 키가 아니면 순수본처럼 'A' 반복, 키면 순환).
- `domain` — `_YN`(:337) 뒤·BOOL 앞: `if (!c.values().isEmpty()) return c.values().size();`.
- 버린 것: 값을 `allowed` 에 섞기(순서가 달라져 DEFAULT 든 ENUM 이 순수본과 어긋남) · `Column` 칸 추가(생성자 호출 16파일·마이그레이션) · ENUM 을 `Check(col IN …)` 로 꾸미기(정의서·DdlGen·왕복 표본이 가짜 CHECK 를 본다).

**[고정] 4-25b ENUM/SET — 스냅샷(MariaDB·MySQL)**
```java
/** 4-25b — ENUM·SET 은 JDBC TYPE_NAME 이 목록 없이 ENUM 이라 COLUMN_TYPE(`enum('a','b')`) 로 덮는다. 5.7·8.0·MariaDB 전부 있다 */
private static final String COLUMN_TYPES =
        "SELECT COLUMN_NAME, COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND DATA_TYPE IN ('enum','set')";

@Override
public Table loadColumns(Table t) throws SQLException {
    Table base = super.loadColumns(t);
    return vendor("enumTypes", base, () -> {
        Map<String, String> types = new HashMap<>();
        try (PreparedStatement ps = prepare(COLUMN_TYPES)) {
            ps.setString(1, t.schema()); ps.setString(2, t.name());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) types.put(rs.getString(1), rs.getString(2)); }
        }
        if (types.isEmpty()) return base;
        List<Column> cols = new ArrayList<>();
        for (Column c : base.columns()) {
            String ct = types.get(c.name());
            cols.add(ct == null ? c : new Column(c.name(), c.ordinal(), ct, c.jdbcType(), c.length(), c.precision(), c.scale(), c.nullable(), c.defaultValue(), c.comment(), c.domain()));
        }
        return base.withColumns(cols);
    });
}
```
- 소문자 `enum('a','b')` 그대로(벤더 원문). `InsertGen.type()` 이 대문자화 → `ENUM` → 4-25a 갈래가 그대로 먹는다.
- `V012__native_type_long.sql`: `-- 4-25b ENUM/SET 나열 목록이 native_type 에 실린다 — 128 을 넘으면 스냅샷 저장 전체가 롤백된다(V008 과 같은 까닭)` + `ALTER TABLE snap_column ALTER COLUMN native_type VARCHAR(4000);`. `DbTest` 11→12(:49·:64·:67, 주석 끝 ` + V012(4-25b)`, 글 「V001~V012」).
- 드러날 것(이력에 적는다): 같은 DB 의 옛 스냅샷(`ENUM`) ↔ 새 스냅샷(`enum(…)`) 비교·반영 DDL 이 ENUM 컬럼을 타입 변경으로 한 번 본다. 정의서 타입 열에 목록이 보인다. `--db` 의 DbCorpusBase maria 골든에 ENUM 컬럼이 있으면 nativeType 만 바뀐다(그 밖 diff 는 중단 조건).
- 버린 것: PG enum(`pg_enum` — 순수본도 안 다루고 TYPE_NAME 이 사용자 타입 이름이라 갈래가 다르다 — 범위 밖) · `Column` 칸 추가.

**[고정] 0-60 axe-core(visual.js)**
- 설치는 실행 세션 몫, 0-60a 첫 명령: `cd C:/workspace && npm i --save-exact axe-core@4.13.0`(네트워크 1회, 저장소 밖 — `C:/workspace/package.json` 에 적힌다, 반입 안 함). **판 고정 4.13.0**(2026-08-05 출시). 최신 4.14.0 은 2026-10-05 로 13일밖에 안 됐다(공급망 — 2주 안 된 판은 안 쓴다). 4.13 에는 패치 판이 없다(npm 실측).
- `const AXE = 'C:/workspace/node_modules/axe-core/axe.min.js'`. 없으면 `console.error('axe-core 없음 — cd C:/workspace && npm i --save-exact axe-core@4.13.0'); process.exit(2);`(조용히 건너뛰지 않는다). 읽은 뒤 `axe.version` 이 `4.13.0` 이 아니면 경고 한 줄(실패 아님).
- **target-size 는 axe 기본 꺼짐**(`lib/rules/target-size.json` `"enabled": false`, impact serious, tags `wcag22aa`·`wcag258` — v4.12.1 원본 실측). `runOnly` 태그만으로 꺼진 규칙이 도는지는 API.md 에 안 적혀 있다. API.md 예 4 는 `rules: { id: { enabled: true } }` 를 runOnly 와 함께 쓰면 그 규칙이 들어간다고 적는다. 그래서 `rules` 로 **명시해 켠다**. 실제로 돌았는지는 아래 강제 지점이 잰다.
- `open(tool)` 뒤 `await page.addScriptTag({ path: AXE })`(페이지마다 1회). 샷마다(:217 `collectButtons` 옆):
```js
const AXE_OPT = { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'] }, rules: { 'target-size': { enabled: true } }, iframes: false, resultTypes: ['violations'] };
const res = await page.evaluate(o => axe.run(document, o).then(r => ({
  ran: [].concat(r.violations, r.passes, r.incomplete, r.inapplicable).map(x => x.id),
  v: r.violations.map(v => ({ id: v.id, impact: v.impact, help: v.help, nodes: v.nodes.map(n => ({ target: n.target.join(' '), summary: n.failureSummary })) })) })), AXE_OPT);
a11y[name] = res.v;
if (res.ran.includes('target-size')) targetSizeRan = true;
if (!SKIP_TOOLS.has(t.tool)) for (const v of res.v) if (v.impact === 'serious' || v.impact === 'critical') for (const n of v.nodes) judged.push(name + ': a11y ' + v.id + ' ' + n.target);
```
- `resultTypes: ['violations']` 는 passes·inapplicable 의 노드 목록만 줄인다. 규칙 id 는 네 목록에 다 남는다. 그래서 `ran` 으로 규칙이 돌았는지 볼 수 있다.
- **강제 지점**: 끝에 `check(targetSizeRan, '⑤ axe target-size 규칙이 돌았다')` — label 과 상관없이 실패다. 꺼진 채 조용히 지나가지 않는다. 안 돌면 실패 사다리: `runOnly` 를 `{ type: 'rule', values: [...axe.getRules(['wcag2a','wcag2aa','wcag21a','wcag21aa']).map(r => r.ruleId), 'target-size'] }` 로 바꾼다. 이 바꿈은 이력에 「정한 것(계획 밖)」 으로 적는다.
- `a11y.json` 을 `styles.json` 옆에 쓴다. summary 첫 줄에 ` · a11y 위반 n(serious+ m)`. `before` 는 기록만(기존 `JUDGE`). `SKIP_TOOLS`(sql_snippets, 순수본)는 기록만.
- **:132 손 대비 판정 삭제**(머리 주석 :5 의 ② 도 「axe color-contrast 4.5」 로). ①묻힘·③체계는 유지.
- 빨강 처분(0-60b): 먼저 `common.css` 토큰(`--muted` dark `#9a9a9a`(≈5.4) · light `#5f6a7a`(≈5.2) 후보 — 첫 실행 수치로 확정) → 화면 마크업 → 그래도 남으면 `A11Y_SKIP = [{ tool, rule, target, why }]`(≤5, 순수본·서드파티 사유만) → 넘으면 새 행. target-size 는 375 폭 작은 요소(`.ext` 9px·`.badge`)가 걸릴 수 있다 — 실측이 정한다. `before` 결과 목록(규칙별 건수 + 처음 20건)을 이력에.
- 버린 것: Playwright·Lighthouse(ejg-db 조사 — puppeteer 와 중복) · best-practice 태그(landmark 류 — 「표준」 아님, 소음) · axe 를 반입·CI(집 검증 레인) · 샷 하나에만 돌리기(탭·상태마다 보이는 요소가 다르다).

**[고정] 5-30 화면 a11y 게이트**
- 시험 `src/test/java/kr/ejg/toolbox/web/ToolsA11yTest.java`:
```java
/** 5-30 — 우리 화면에 우리 코드 검사 ㅈ(a11y) 묶음 0건. sql_snippets 는 순수본 그대로라 뺀다(portfolio 지시문) */
class ToolsA11yTest {
    @Test
    void toolsPassOwnA11yRules() throws Exception {
        // CheckRulesTest 는 core.check 패키지의 package-private 클래스라 web 에서 못 부른다 — 같은 생성 식(CheckRulesTest:33-36)을 둔다
        Profile p = new Profile("t", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null,
                new Profile.Naming(null, null, null, null, null, true), new Profile.CodeCheck(null, null, null), "egov35", null, null, null);
        RuleSet rules = RuleSet.load(p, null);
        RuleSet.Run run = rules.run();
        List<String> bad = new ArrayList<>();
        try (Stream<Path> s = Files.list(Path.of("src/main/resources/tools"))) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                String n = p.getFileName().toString();
                if (n.equals("sql_snippets.html") || !(n.endsWith(".html") || n.endsWith(".js"))) continue;
                Source src = new Source(n, Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n"), "UTF-8", "LF", n.endsWith(".js") ? "js" : "html");
                for (Finding f : run.apply(src)) if (f.group().equals("a11y")) bad.add(n + ":" + f.line() + " " + f.rule());
            }
        }
        for (Finding f : run.finish()) if (f.group().equals("a11y")) bad.add(f.file() + ":" + f.line() + " " + f.rule());
        assertEquals(List.of(), bad, "화면이 우리 a11y 규칙에 걸린다 — 규칙이 틀렸으면 5-31 로, 화면이 틀렸으면 고친다");
    }
}
```
  `RuleSet`(public, `load` :61·`run()` :174·`Run` :178)·`Source`·`Finding` 은 public 레코드·클래스 — web 패키지에서 쓸 수 있다(확인됨). 첫 실행 기대값 **62**(§2.6) — 다르면 중단 조건.
- 마크업 고침(백엔드본, 사용자 결정):
  - dev_tools `:123 <div class="tabs" id="main-tabs">` → `role="tablist"`. `.tab` 16(:124-139) 각 `role="tab" tabindex="0"`. `switchTab`(:759) forEach 안에 `t.setAttribute('aria-selected', t.dataset.tab===name ? 'true' : 'false');`.
  - 그 밖 클릭 요소 — `.b64-tab` 8 · `.sql-db-btn` 5 · `.url-tab` 3 · jsp_formatter `.t` 2(:138-139) · jsp_formatter JS 템플릿 3(:660·672·683 `<div class="it … link"`) → `role="button" tabindex="0"`.
  - 키 처리 — `common.js` IIFE 끝(:472 옆) 한 줄: `document.addEventListener('keydown', function (e) { if ((e.key === 'Enter' || e.key === ' ') && e.target && e.target.matches && e.target.matches('[tabindex="0"][onclick]')) { e.preventDefault(); e.target.click(); } });` — 모든 화면이 common.js 를 싣는다(sql_snippets 포함 — 순수본 `.db-tab` 도 tabindex 가 생기면 바로 먹는다).
  - inputLabel — dev_tools 라디오 13 에 `id`(`dir_snake`·`dir_camel`·`mode_basic`·`mode_pascal`·`mode_vo`·`mode_resultmap`·`mode_select`·`mode_insert`·`mode_update`·`mode_delete`·`mode_mybatis_if`·`il_q_str`·`il_q_num` — 기존 id 와 안 겹침, grep 으로 확인). table_builder :105-108 `<span class="opt">` 넷 → `<label class="opt">` + input `id`(`mode_pure`·`mode_cls`·`mode_inline`; `s-nbsp` 는 이미 id 있음) — `getMode()`·:479·:773 은 `input[name=mode]` 라 불변.
  - imgAlt — dev_tools:1446 `label: '<img> 태그'` → `'img 태그'`. iframeTitle — table_builder:144 `title="미리보기"`. thScope·tableCaption — dev_tools:1739 템플릿 `<th>` 여섯에 `scope="col"`, `<table class="by-table">` 바로 뒤 `<caption class="sr-only">줄별 바이트 수</caption>`; `common.css` 에 `.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }`(토큰 아님 — `toolsHaveNoLocalTheme` 무관).
- portfolio 지시문(PROGRESS 「사람이 할 것 ④」 다섯째): 위 고침을 파일·줄·속성 그대로 + `sql_snippets.html` `.db-tab` 5 에 `role="button" tabindex="0"`. 고친 뒤 `sync-pure.sh`, 백엔드본은 그때 `git apply` 가 안 붙는 줄만 손으로.
- **5-31 대기 행(새)**: `a11y.inputLabel`·`selectLabel` 이 감싼 `<label>` 을 못 본다(오탐 13). 고치려면 자바·JS 둘 다 되는 bounded lookbehind `(?<!<label\b[^<]{0,200}>\s{0,20})` 꼴 + `pure-rules.json` 재생성 + portfolio 재주입 + `PureCodeCheckTest` — 순수본 반입 행과 같이. 입력(portfolio 세션) 있을 때.

---

### 4. 실행 스텝

청크 = 커밋 하나. `verify.sh` 와 `git commit` 따로. 제목 `feat|fix|test|docs: <번호> <무엇>`, 끝 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`(실행 모델 이름). 청크마다 분할표 행 `완료` + PROGRESS 한 줄(앞 줄 해시 채움). 시험 명령 `bash scripts/mvn.sh -q -B test -Dtest=<클래스> -Dsurefire.failIfNoSpecifiedTests=false`. 정규식을 넣는 청크(4-26·4-25a)는 `bash scripts/mvn.sh -q -B -DskipTests compile spotbugs:check` 도.

#### S0(설계 세션, 끝남) — 설계 커밋
- `git checkout -b work/2026-10-18b` · `design/27-bundle43.md`(§1~§8) · PLAN 분할표: 4-25 행을 `4-25a`·`4-25b` 로 바꿈(원문 유지 + 「설계 27 §3」) · 4-26 행에 「설계 27 §3·§4」 · 새 행 `0-60a`·`0-60b`·`5-30`·`5-31(대기)` · 번들 표 `번들 43 | S0→4-26→4-25a→0-60a→5-30→0-60b→4-25b | 2026-10-18(Fable, 설계 27) … | npm i --save-exact axe-core@4.13.0(C:/workspace, 네트워크) · Puppeteer · Docker(--db, V012 + Maria) · 표본 폴더(--corpus) | 설계 완료 — 실행 대기` · PROGRESS 이력 한 줄 · 커밋 `docs: 설계 27 번들 43 — UNIQUE·ENUM/SET·axe·a11y 게이트`. push 없음.
- 검증: `bash scripts/verify.sh`(docs 레인 — 존댓말 린트) 초록 뒤 커밋(commit-guard).

#### 4-26. DdlReader UNIQUE
- 대상: `core/gen/DdlReader.java`(`UQ` 상수 · `Draft.uqs` · `item()` 두 분기 · ALTER 반복 · :179) · `core/gen/DdlReaderTest.java`(`matchesSnapshotTakenFromTheSameDdl` 에 단언 하나 + 새 `readsUniqueConstraints`) · `core/gen/InsertGenTest.java`(새 `keyOverlapFromDdlUnique`).
- `matchesSnapshot…` 추가 단언(PK 단언 뒤): 골든 uniques 중 이름이 `UQ_` 로 시작하는 것(샘플의 제약 UNIQUE 는 전부 `uq_*`, `ux_*` 는 CREATE UNIQUE INDEX 라 범위 밖)의 (이름 대문자, 컬럼 대문자 목록) 집합 == DDL 이 읽은 uniques 집합.
- `readsUniqueConstraints`: `"CREATE TABLE t (a INT UNIQUE, b VARCHAR(3), c CHAR(1), d INT, CONSTRAINT uq_bc UNIQUE (b, c), UNIQUE KEY ix_d (d), UNIQUE NONCLUSTERED (b));\nALTER TABLE t ADD CONSTRAINT uq_c UNIQUE (c);"` → uniques 다섯 `[null:[a]] [uq_bc:[b,c]] [ix_d:[d]] [null:[b]] [uq_c:[c]]` 순서대로, `unreadable` 비어 있음, 컬럼 넷·타입 그대로(UNIQUE 가 타입에 안 섞임).
- `keyOverlapFromDdlUnique`: `keyWarns("CREATE TABLE t (use_yn CHAR(1), del_yn CHAR(1), nm VARCHAR2(10), CONSTRAINT uq UNIQUE (use_yn, del_yn))", 5, false)` == 「(USE_YN, DEL_YN): 값 조합이 2가지인 키라 5행이면 …」 한 줄 · `cols()` 의 `uq` 가 true 라 `genVal` 이 CHAR 기본값을 안 쓴다 — `"CREATE TABLE t (cd CHAR(2) DEFAULT 'ZZ' UNIQUE, nm VARCHAR2(5))"` 2행 → 첫 값 `'01'`·둘째 `'02'`(기존 `pkIgnoresQuotedDefault` 꼴).
- 검증: `-Dtest=DdlReaderTest,InsertGenTest,DdlGenTest,DtoGeneratorTest` 초록 · `golden/insert` diff 0 · spotbugs · `verify.sh`. 커밋 `feat: 4-26 DdlReader UNIQUE 읽기 — CREATE 탭 키 경고`.
- 의존: 없음

#### 4-25a. ENUM/SET — DDL 경로
- 대상: `core/gen/DdlReader.java`(:228 nativeType 분기) · `core/gen/InsertGen.java`(`Col` 12칸 + 호환 생성자 · `cols()` · `genVal` · `domain`) · `core/gen/DdlReaderTest.java`(새 `readsEnumAndSetValues`) · `core/gen/InsertGenTest.java`(새 `enumSetCycleLikePure`).
- `readsEnumAndSetValues`: `"CREATE TABLE t (id INT PRIMARY KEY, st ENUM('A','B','C') NOT NULL DEFAULT 'A' COMMENT '상태', fl SET('X','Y'), nm VARCHAR(10))"` → st nativeType `ENUM('A','B','C')`·length null·nullable false·def `'A'`·comment 상태 · fl `SET('X','Y')` · nm `VARCHAR` length 10 · unreadable 비어 있음.
- `enumSetCycleLikePure`(mariadb, 4행, `InsertGen.checkIns(ddl)`): `st ENUM('A','B','C') NOT NULL` → 행 1~4 `'A','B','C','A'` · `kd ENUM('P','Q') DEFAULT 'Q'` → 넷 다 `'Q'`(순수본처럼 기본값 우선) · `fl SET('X','Y') NOT NULL` → `'X','Y','X','Y'` · `keyWarns("CREATE TABLE t (st ENUM('A','B') PRIMARY KEY, nm VARCHAR(10))", 3, false)` == 「ST: 허용 값이 2개인 키 컬럼이라 3행이면 값이 겹친다 — 행 수를 2 이하로 두거나 값을 손으로 바꿀 것」 · CHECK IN 과 ENUM 둘 다면 CHECK 값(allowed 우선).
- 검증: `-Dtest=DdlReaderTest,InsertGenTest,DdlGenTest,DtoGeneratorTest,InsertRoutesTest` 초록 · `golden/insert` diff 0 · spotbugs · `verify.sh`(끝 줄이 corpus 를 알린다 — 마무리에서). 커밋 `feat: 4-25a InsertGen ENUM/SET 값 갈래 — DDL 경로`.
- 의존: 4-26(같은 파일 — 충돌 방지 순서)

#### 0-60a. visual.js 에 axe-core
- 대상: `scripts/puppeteer/visual.js`(§3 — AXE 상수·존재·판 확인·addScriptTag·샷마다 run·`targetSizeRan`·확인 ⑤·a11y.json·summary·:132 삭제·머리 주석) · `C:/workspace`(`npm i --save-exact axe-core@4.13.0`, 저장소 밖).
- 검증: `node -e "console.log(require('C:/workspace/node_modules/axe-core/package.json').version)"` → `4.13.0` · `bash scripts/mvn.sh -q package -DskipTests` → `node scripts/puppeteer/visual.js before`(기록만) → `out/visual/before/a11y.json` 있음 · summary 첫 줄에 `a11y 위반` · 콘솔 오류 0 · 동작 확인 전부 통과(**⑤ target-size 포함**) · 샷 수 전과 같음. **before 의 serious·critical 목록(규칙별 건수 + 처음 20건)을 PROGRESS 이력에 적는다**(0-60b 의 입력). `-Dtest=ToolsFolderTest` 초록(networkidle2). 커밋 `feat: 0-60a 시각 검증에 axe-core — WCAG 2.2 AA · 대비 4.5 · target-size`.
- 의존: 없음

#### 5-30. 화면 a11y 게이트 — JUnit + 마크업 62건
- 대상: `src/test/java/kr/ejg/toolbox/web/ToolsA11yTest.java`(신설, §3) · `tools/dev_tools.html` · `tools/jsp_formatter.html` · `tools/table_builder.html` · `tools/common.js`(키 한 줄) · `tools/common.css`(`.sr-only`) · PROGRESS 「사람이 할 것 ④」 다섯째(지시문).
- 순서: 시험을 먼저 넣고 돌려 **62** 확인(다르면 중단) → 고침 → 0.
- 검증: `-Dtest=ToolsA11yTest,ToolsFolderTest,SmokeHtmlUnitTest,LauncherTest` 초록(스모크 46 전부 — 탭 선택자 불변) · `node scripts/puppeteer/smoke-devtools.js`(있으면) · `verify.sh`. 커밋 `feat: 5-30 화면 a11y 게이트 — ToolsA11yTest 0건 · 탭 키보드 · 레이블 · caption`.
- 의존: 0-60a(axe before 가 고침 전 상태를 찍어 두게)

#### 0-60b. axe 빨강 처분
- 대상: `tools/common.css`(토큰 — `--muted` 둘, 수치는 before 실측으로) · 화면(before 목록이 가리키는 자리) · `scripts/puppeteer/visual.js`(`A11Y_SKIP` ≤5, 사유 글 필수).
- 검증: `node scripts/puppeteer/visual.js after` **초록**(판정 0 · 콘솔 오류 0 · 확인 실패 0) → `node scripts/puppeteer/visual-diff.js before after` 보고서 눈으로(그림이 토큰 둘 밖에서 안 바뀜) · `-Dtest=ToolsFolderTest`(`commonCssDefinesButtonScheme`·`toolsHaveNoLocalTheme`) · `verify.sh`. 둘 넘게 남으면 `A11Y_SKIP` 로, 다섯 넘으면 새 행 + 그대로 커밋(before 는 기록만이라 빨강이 아니다 — after 가 빨강이면 커밋 못 하니 **after 가 초록이 될 때까지가 이 청크**). 커밋 `fix: 0-60b axe 위반 처분 — muted 대비 4.5 · …`.
- 의존: 5-30

#### 4-25b. ENUM/SET — MariaDB·MySQL 스냅샷(db 레인)
- 대상: `core/dialect/MariaMetaSource.java`(`COLUMN_TYPES` + `loadColumns` §3) · `src/main/resources/db/migration/V012__native_type_long.sql`(신설) · `core/db/DbTest`(11→12 셋) · `core/dialect/MariaMetaSourceTest.java`(새 `enumSetColumnTypes` — `Statement` 로 `CREATE TABLE zz_e (k INT PRIMARY KEY, e ENUM('A','B') NOT NULL, s SET('X','Y'))` → `src.loadColumns(src.listTables("test") 중 zz_e)` → nativeType `enum('A','B')`·`set('X','Y')`(equalsIgnoreCase) · `src.warnings()` 비어 있음 → `InsertGen.generate(t, Map.of(), Options("mariadb", 3, …))` 의 1~3행이 `'A','B','A'` · 끝에 DROP).
- 검증(실행 전 남은 메모리 확인 — 번들 42 드러난 것): `bash scripts/docker-up.sh` → `-Dtest=MariaMetaSourceTest,DbTest,SnapshotStoreTest` 초록 → `verify.sh`(빠른) → 마무리에서 `verify.sh --db`. 커밋 `feat: 4-25b MariaDB 스냅샷 ENUM/SET 나열 값 — COLUMN_TYPE · V012`.
- 의존: 4-25a

#### 마무리(CLAUDE.md 「마무리」 그대로)
독립 리뷰 → `gate-probe.sh` → `verify.sh` 끝 줄 — **corpus**(DdlReader·InsertGen·화면 JS: `DdlCorpusTest`·`CheckCorpusTest` 골든 불변·`PureCodeCheckTest` 28) 와 **db**(V012·Maria) 둘 다 알린다 → `--corpus`·`--db` 따로(push 와 따로, 메모리 확인) → push → PR(청크마다 세 줄 표) → CI 폴링(SpotBugs 포함) → AI 리뷰 처분(버그만) → 머지 위임 없음, PR 열고 멈춤.

---

### 5. 금지 사항

- `pure/` 아무것도. `tools/sql_snippets.html` 도(순수본 + common.js 한 줄 — `ToolsFolderTest.sqlSnippetsIsPurePlusCommonJs`).
- `rules.yaml` a11y 정규식·`fixtures/check/*`·`golden/check/pure-rules.json`·`golden/corpus/*` 수정 금지(PureCodeCheckTest·CheckCorpusTest 가 순수본과 맞댄다 — 오탐은 5-31 로).
- `Column` 레코드 칸 추가 금지. `InsertGen.Col` 은 호환 생성자로(시험 7곳 안 고친다). `genVal` 의 기존 갈래 순서·글 불변(`golden/insert` diff 0 이어야).
- `V001~V011` 수정 금지. 새 파일 `V012` 만.
- 클릭 div·span 을 `<button>` 으로 바꾸지 않는다(공용 `button{}` 이 붙는다). `.tab`·`[data-tab]`·`#main-tabs` 선택자·id 는 그대로(HtmlUnit·visual.js·dev_tools_ext.js 가 본다).
- `visual.js` 의 `networkidle2` 유지 · `A11Y_SKIP` 다섯 넘기지 않기 · axe 결과를 보고 규칙을 `rules: {id: {enabled: false}}` 로 끄지 않기(target-size 켬 외).
- 새 의존성 0(저장소). `C:/workspace/package.json` 의 axe-core 는 저장소 밖.
- 시험을 고쳐 통과시키지 않기(DbTest 11→12 · `matchesSnapshot…` 단언 추가는 행이 정한 것).

---

### 6. 최종 검증

- `bash scripts/verify.sh` 초록 + `--corpus`(약 7분: `DdlCorpusTest` `ddl-summary.json` 불변 · `insertGenerationNeverThrows` 0 · `CheckCorpusTest`·`PureCodeCheckTest` 불변) + `--db`(약 17분: V012 · `MariaMetaSourceTest` 셋 · DbCorpusBase maria 골든 diff 는 ENUM 컬럼 nativeType 만).
- `gate-probe.sh` 아홉.
- `node scripts/puppeteer/visual.js after` 초록, `out/visual/after/a11y.json` 에 serious·critical 0(sql_snippets 제외).
- 수동(`run.bat --port 41791`, 데이터 폴더 저장소 밖): ① dev_tools INSERT 탭 CREATE 문 `CREATE TABLE t (use_yn CHAR(1), del_yn CHAR(1), CONSTRAINT uq UNIQUE (use_yn, del_yn))` 5행 → 경고 한 줄 ② `st ENUM('A','B','C') NOT NULL` 4행 → A,B,C,A ③ Tab 키로 dev_tools 탭 이동 → Enter → 탭 바뀜 ④ 데모 MariaDB 가 있으면 ENUM 표 스냅샷 → 「스냅샷에서」 값이 나열 값 ⑤ 콘솔 오류 0.
- 회귀: `golden/insert/*` 불변 · `golden/meta/*` 불변 · 스모크 46 초록.

---

### 7. 중단 조건

멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- 구조 설명이 코드와 다름(예: `DdlReader:179` 가 이미 uniques 를 넘긴다 · `MariaMetaSource` 에 `loadColumns` 가 있다 · `Col` 생성이 시험 7곳 밖에도 있다).
- `ToolsA11yTest` 첫 실행이 62 가 아님(규칙·측정이 어긋남).
- `golden/insert`·`golden/meta`·`golden/corpus` 가 바뀜.
- 검증 2회 연속 실패, 원인이 범위 밖. 스텝에 없는 파일 3개 이상.
- 0-60b 에서 after 를 초록으로 만들 수 없음(SKIP 다섯 넘음) → `wip/0-60b` 로 빼고 보고(번들 모드 규칙).
- 번들 모드대로 — 갈리면 행당 둘까지 정하고 「정한 것(계획 밖)」.

---

### 8. 불확실 항목

설계 세션이 2026-10-18 전부 확인해 §2·§3 에 넣었다. 실행자가 따로 확인할 것 **없음**. 확인한 것:
- `CheckRulesTest` 는 `core.check` 패키지의 package-private 클래스(:29 `class CheckRulesTest`)다. web 패키지의 `ToolsA11yTest` 는 `profile(...)` 을 못 부른다. 그래서 §3 에 같은 `Profile` 생성 식을 넣었다. `RuleSet`·`RuleSet.Run`·`Source`·`Finding` 은 public 이다.
- axe-core 판(npm 레지스트리 실측): 최신 4.14.0(2026-10-05), 4.13.0(2026-08-05), 4.12.1(2026-06-10). 4.13 에는 패치 판이 없다. 공급망 기준으로 2주 안 된 4.14.0 은 빼고 **4.13.0 정확 고정**.
- `target-size`(원본 `lib/rules/target-size.json`, v4.12.1): `enabled: false`·impact serious·tags `wcag22aa`·`wcag258` → `rules` 로 명시해 켜고 확인 ⑤ 가 돌았는지 잰다. 4.12.1 에 있으니 4.13.0 에도 있다.
- axe impact(rule-descriptions): `color-contrast`·`frame-title`·`html-has-lang`·`document-title`·`link-name` 은 serious, `label`·`button-name`·`aria-required-parent` 은 critical → `a11y.json` 의 빨강 기준(serious·critical)에 다 든다.
- `DdlGen.nativeWithSize` 가 괄호 든 원문을 그대로 낸다 · 샘플·골든에 ENUM 없음 · `CodeCheck` 3인자 생성자 있음(:131) · jsp_formatter mouseOnly 5 = :138·139·660·672·683 · `information_schema.COLUMNS.COLUMN_TYPE` 은 MySQL 5.7·8.0·MariaDB 전부 있다 · `DdlGenTest` 입력은 골든 Table(4-26 으로 불변).
