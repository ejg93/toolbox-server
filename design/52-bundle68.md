# 설계 52 — 번들 68: 생성기 컬럼 역할·입력 꼴·검색 + DdlReader LIKE·인덱스·ALTER PK (Fable 설계 · Opus 실행, 2026-11-01)

## 맥락
번들 표 「번들 68」 요지 행(compare ⑤ 마-1~4·6 + 1-67). 스프링 소스 생성기(`core/gen/GenModel`·`templates/gen/egov35`)는 지금 모든 컬럼을 모든 화면에 입력칸으로 내고, 검색은 PK 아닌 첫 문자열 컬럼 하나를 LIKE 로만 건다. RuoYi 생성기(`GenTableColumn` — `isList`·`isInsert`·`isEdit`·`isQuery`·`queryType`·`htmlType`·`isRequired`, 기본값은 `GenUtils` 의 이름·형식 규칙)가 하는 다섯을 우리 꼴(YAML 프로필 + FreeMarker 템플릿)로 옮긴다. 같은 db 레인(`core/gen`)에 미뤄 둔 `DdlReader` 셋 — `CREATE TABLE … LIKE`(1-67, 설계 47 §3-18) · `CREATE INDEX`·표 안 `KEY`·`ALTER TABLE … ADD (CONSTRAINT … PRIMARY KEY …)`(compare ⑰ 「후보로」 첫째, 사용자 (가)) — 을 같이 닫아 `--db` 를 이 번들 한 번에 모은다(사용자 2026-10-31).

**번들 67 보정**(2026-11-01) — 이력 「정한 것(계획 밖)」 셋(2-32a 규칙 수 단언 당김·`keyed` 컬럼 맞대기 · 4-29 `path` 키·BOM 뗌 · V-47 「완료 행 안 고침」)과 마무리 리뷰 Minor 일곱 중 이 번들과 겹치는 자리:
- Minor ⑦ 「함수 인덱스 정규식」 과 ② 「noPk 인덱스 셈」 — `DdlCorpusTest` 의 시험 쪽 `CREATE_INDEX` 정규식 이야기다. 1-68 이 정규식을 걷어 내면 둘 다 사라진다
- compare ⑰ 「드러난 것」 셋째(ALTER ADD(…PK…) 안 읽음 → db-samples noPk 8) · R-16 「오탐(입력) 8」 — 1-68 이 고친다. 고친 뒤 `ddl-lint.json` db-samples noPk 8 → 0 이 기대
- Minor ① `backs` 순서 · ③ 깨진 문자 수 · ④ `hexToBytes` 0x · ⑥ `decodeOff` 래칫 — corpus 레인(StructureLint·dev_tools·JsCorpusTest). **이 번들에 안 넣는다**(사용자 (나)). compare ⑱ 「후보로」 에 그대로 둔다
- 「H2 FK 자동 인덱스 제외」·다-17·다-19·바-6·바-7 나머지 — 후보 그대로

탐색 실측(2026-11-01, `caff4bd`, 표본 `C:/workspace/toolbox-corpus`, 외부 통신 0, grep 셈 — 코드 본문은 안 옮긴다):

| 재료 | 수치 | 뜻 |
|---|---|---|
| eGov oracle DDL 감사 컬럼 | `FRST_REGIST_PNTTM` 136(NOT NULL 31) · `FRST_REGISTER_ID` 135(28) · `LAST_UPDT_PNTTM` 135(13) · `LAST_UPDUSR_ID` 135(5) / 표 181 | 넷이 표준 이름이고, 대부분 NULL 허용. 다른 이름(`REGIST_DE`·`UPDUSR_ID`)은 0 |
| eGov 매퍼 `LAST_UPDT_PNTTM = SYSDATE`·`NOW()` | 950 자리 | 시각 감사 컬럼은 DB 현재시각 식, 사용자 컬럼은 `#{…}` 이 관례 |
| eGov `*_AT CHAR(1)` | 83(`USE_AT` 32) / CHECK 0 | 여부 컬럼 — Y/N 라디오 재료. CHECK 가 없어 보기는 이름 규칙으로 |
| eGov `*_CODE`·`*_CD` 문자 컬럼 | 94 | 코드 사전(마-5)이 없어 select 로 못 만든다 — text. 마-5 는 범위 밖 |
| eGov `DATE` 컬럼 | 313(`*_DE`·`*_DT`·`*_PNTTM` 308 이 DATE, 59 가 CHAR) | 날짜 입력·BETWEEN 검색 재료 |
| eGov VARCHAR2 길이 분포 | 20: 433 · 60: 81 · 255: 81 · 100: 58 · 1000: 25 · 2000: 21 · 2500: 30 · 4000: 7 · CLOB 10 | textarea 문턱 1000 — 그 아래(500 1건)는 제목·이름 |
| RuoYi `GenUtils` 기본 규칙 | 문자 길이 ≥ 500 또는 TEXT → textarea · 시각 → datetime · `*name` → LIKE · `*status` → radio · `*type`·`*sex` → select(사전) · `*file` → upload · `create_by` 류 편집·목록·검색 제외 | 우리 기본값의 참고. 사전·upload·summernote 는 안 옮긴다 |
| db-samples HR `hr_create.sql` | `CREATE UNIQUE INDEX x` 6(PK 와 같은 이름) → `ALTER TABLE … ADD ( CONSTRAINT x PRIMARY KEY (…) , CONSTRAINT … FOREIGN KEY … )` · `CREATE INDEX` 11 | 1-68 의 두 꼴 다 있다 |
| 표본 `CREATE INDEX` 형태 | egov 8방언 224·47·44·2 · 컬럼 `(COL ASC)` 꼴 다수 · 함수 인덱스 0 · 꼬리(TABLESPACE 등) 0 · chinook sqlite `"IFK_x" ON "Album" ("ArtistId")` | 파서가 받을 꼴 |
| 표본 표 안 `KEY name (cols)` | ruoyi·ruoyi-classic `sql/*.sql` 12 · egov maria·mysql 0 | 인라인 KEY 재료는 ruoyi 뿐 |
| `sample/*.sql`(DdlReaderTest 넷) | 방언마다 `CREATE INDEX ix_*` 2 · `CREATE UNIQUE INDEX ux_users_email` 1 | JDBC 골든 스냅샷과 인덱스까지 맞댈 수 있다(지금은 「ux_* 는 범위 밖」 주석) |

- `GenModel.of`(`core/gen/GenModel.java:59-178`) — 컬럼마다 `f`(column·name·Name·javaType·string·pk·nullable·comment·annotations·doc·length) → `fields`·`pk`·`nonPk`, `searchField` = PK 아닌 첫 String(없으면 첫 String). `Options(basePackage, module, skipTokens, logicalNames, dialect, className, skipColumns)` 7 + 5인자 생성자. 자기 검사 기준선 `self-check.txt:88-89` 에 `of(4)` 「복잡도 40」·「길이 120」 — 키에 숫자가 든다
- 템플릿 열(`templates/gen/egov35`) — `List.jsp.ftl` 검색 칸 하나(`searchKeyword`) + 전 컬럼 표 · `Regist.jsp.ftl`·`Updt.jsp.ftl` 전 컬럼 `form:input`(Updt 는 PK readonly) · `Mapper.xml.ftl` `<sql id="search">` 방언 셋 LIKE · INSERT 전 컬럼 · UPDATE SET nonPk · `VO.java.ftl` 필드 + eGov 검색·페이징 여덟 · `Detail`·`Controller`·`Service`·`ServiceImpl`·`DAO` 는 안 바뀐다. egov4·egov5 는 `extends: egov35`(vars 만 다르다 — egov5 가 `valid: "true"`)
- 프로필 — `Profile.Generator(templateSet, basePackage, outDir, tables)` · `TableGen(className, skipColumns)`(Jackson YAML, `FAIL_ON_UNKNOWN_PROPERTIES`) · `ProfileStoreTest:61` 이 `new Profile.TableGen("Customer", List.of("REG_IP"))` 2인자 · `GenerateRoutes.postGenerate`(복잡도 48 기준선) 가 `tableGens` 를 `Generator.run(12)`(인자 12 기준선) 에 넘긴다
- `Validation.of`(`core/gen/Validation.java`) — CHECK `IN ('A','B')` 를 `InSet` 으로 푼다(`parse`) — select 보기 재료가 이미 있다. NOT NULL → `@NotNull`/`@NotBlank` 는 `vars.valid` 가 "true" 일 때만(egov5)
- 시험 — `GenModelTest`(픽스처 `empHist` 7컬럼 · 골든 `gen/model.json`) · `GenTemplatesTest`(세트 셋 × 10 파일 골든 `gen/egov35|egov4|egov5/*` · `gen/mapper-<방언>.xml` 다섯 · 자기 엔진 왕복 C·R·U·D) · `GeneratorTest` · `GenerateRoutesTest` · `SmokeHtmlUnitTest.crudGeneratorRuns`(H2 `TB_DEPT` 2컬럼) · corpus `GeneratorCorpusTest`(HR 표 7 × 세트 셋 — A: 구문·프로그램 7×7·CRUD 넷·고아·JSP 링크·코드 검사 error/a11y/security·재생성 결정성 · B `gen-hr.json`) · `GeneratorJavacTest`(egov35-lib 있을 때 javac + MyBatis 파싱 + H2 HR 건수·목록 첫 쪽)
- `DdlReader`(`core/gen/DdlReader.java`) — `read(1)` 복잡도 38·중첩 6, `item(3)` 30 기준선. `Draft` 에 `indexes` 없음. `ALTER_PK` 는 `^ALTER … ADD (CONSTRAINT x)? PRIMARY KEY (` 앵커라 `ADD (` 꼴을 못 읽는다. `ALTER_TABLE` 가지의 FK·UQ 는 `find` 라 `ADD (` 꼴도 읽는다. `CREATE INDEX` 문장은 머리가 CREATE TABLE 이 아니라 조용히 넘어간다(unreadable 아님). `CONSTRAINT_LINE` 이 `KEY`·`INDEX` 로 시작하는 항목을 제약 줄로 받고 버린다
- `JdbcMetaSource`(`core/meta`) — `uniques` 는 PK 뺀 유니크 인덱스, `indexes` 는 PK 뺀 전부(유니크 인덱스는 양쪽). DDL 읽기도 같은 꼴로 맞춘다
- `DdlCorpusTest.lintTables`(:248-268) — 시험 쪽 `CREATE_INDEX` 정규식 + `indexColumns`·`lastName`·`FIRST_WORD`. 골든 `ddl-lint.json`(그룹 15 `표 n · noPk a · fkTypeMismatch b · dupIndex c · fkCycle d`) · `ddl-lint-findings.txt`(32 줄, db-samples noPk 8 포함)
- `SqlFixtureCorpusTest`(`core/analyze`) — CREATE TABLE 이 든 sqlfluff 픽스처는 `DdlReader.read` 의 표 이름으로 비교. `sqltables-diff.txt` 32 줄 중 `mariadb/create_table`(그쪽만 Y) · `postgres/create_table`(그쪽만 EMPLOYEES) 이 LIKE 자리 — 둘 다 `create table y LIKE x` 머리 꼴(실측). 1-67 로 둘이 사라져 32 → 30 기대
- `DdlGenTest.roundTripThroughDdlReader` — 만든 DDL(`CREATE INDEX` 포함)을 다시 읽어 표·컬럼·널·PK·FK 수·코멘트만 견준다. 1-68 뒤엔 비유니크 인덱스 수도 견줄 수 있다
- 레인 — `core/gen`(main·test)·`src/main/resources/gen` 은 db·corpus 둘 다. `core/profile`·`templates/`·`web` 은 java 레인. 이 번들은 push 앞 `--corpus` 와 `--db` 둘 다(`--corpus` → Docker → `--db` 순서, 겹치지 않게)
- PMD 기준선 짝 — `DdlReader` 여섯 · `GenModelTest`=AvoidDuplicateLiterals · `GeneratorTest`·`GeneratorCorpusTest`·`GeneratorJavacTest`·`Validation`·`GenerateRoutes`·`GenerateRoutesTest`. **없는 것**: 새 `FieldRoles` · `DdlReaderTest` · `ProfileStoreTest` · `GenTemplatesTest` · `DdlGenTest` — 새 코드가 새 짝을 만들면 안 된다(리터럴 ≥ 4 는 상수, `if` 에 리터럴 금지, 비교는 리터럴 앞)

## 사용자 결정(2026-11-01)
- (가) **DdlReader 인덱스 읽기를 붙인다** — 새 행 1-68(`CREATE [UNIQUE|BITMAP] INDEX` · 표 안 `KEY`/`INDEX` · `ALTER TABLE … ADD (CONSTRAINT … PRIMARY KEY …)`). `DdlCorpusTest` 시험 쪽 정규식 제거, `ddl-lint.json`·`ddl-lint-findings.txt` 갱신
- (나) compare ⑰ 리뷰 Minor 넷은 **뺀다** — corpus 레인, 다음 번들 후보
- (다) **머지 위임 있음**
- 설계가 정한 것(사용자가 안 물은 자리) — 감사 컬럼은 프로필 `generator.audit` 넷 묶음(created·creator·updated·updater), 비면 eGov 표준 넷 · 시각 감사 컬럼은 매퍼에서 DB 현재시각 식(`SYSDATE`·`NOW()`·`GETDATE()`), 사용자 컬럼은 `#{…}` · INSERT 는 created·creator(+ NOT NULL 인 updated·updater), UPDATE 는 updated·updater · 검색 기본: 문자 text → LIKE, select·radio·checkbox → EQ, 날짜·시각 → BETWEEN(문자 From·To 두 칸, 방언별 날짜 변환), 수·textarea → 없음 · textarea 문턱 길이 1000 또는 길이 없음(CLOB) · `*_AT CHAR(1)` → 라디오 Y/N(eGov 「여부」 용어) · CHECK `IN (…)` → select · `byte[]` 는 화면·검색에서 뺀다 · 코드 사전(마-5)·upload·트리·주부 표(마-7·8)·메뉴 SQL(마-9) 은 범위 밖 · 서버 검증(`@NotNull`)은 지금처럼 `vars.valid` 가 "true" 인 세트(egov5)만 — 화면 `required` 는 세트 셋 다 · `GenModel.Options` 는 7인자 유지(className·skipColumns 둘 → `Profile.TableGen`·`Profile.Audit` 둘) · 역할·입력 꼴 판정은 새 클래스 `FieldRoles`(`GenModel.of` 는 호출 한 줄 + `m.put` 넷, 복잡도·길이 줄어든다) · `CREATE UNIQUE INDEX` 는 JDBC 처럼 `uniques`·`indexes` 양쪽, PK 이름과 같거나 PK 컬럼과 똑같은 유니크 인덱스는 뺀다 · 표 안 `UNIQUE KEY` 는 지금처럼 `uniques` 만 · `FULLTEXT`·`SPATIAL` 은 안 읽는다 · 새 화면·순수본·표본 추가 없음 · 설계 41~51 그대로(에이전트 최소 · 검증 인프라 최적화 금지)

실행자는 Opus. 차례 `S0` → `7-16a` → `7-16b` → `7-17` → `1-67` → `1-68` → `V-48a` → `R-17` → `V-48` → 마무리. 가지 `work/2026-11-01d`(로컬 `caff4bd` 같이). 번들 하나·PR 하나.

---

## 1. 목표와 범위
- 한 문장: 생성기가 컬럼마다 역할(목록·등록·수정·검색)과 입력 꼴·필수를 정해 eGov 감사 컬럼을 입력칸에서 빼고 검색 조건 여럿(LIKE·EQ·BETWEEN)을 내며, `DdlReader` 가 `CREATE TABLE … LIKE`·`CREATE INDEX`·표 안 `KEY`·`ALTER TABLE … ADD (CONSTRAINT … PRIMARY KEY …)` 를 읽는다.
- 완료 시 체감: 데모 스냅샷의 eGov 꼴 표(감사 컬럼 넷·`USE_AT`·날짜 컬럼)를 생성하면 등록 화면에 감사 컬럼 입력칸이 없고, `USE_AT` 는 Y/N 라디오, 날짜는 날짜 입력, 2000자 컬럼은 textarea, NOT NULL 칸에 `required` 가 붙는다. 목록 화면에 제목 LIKE·여부 select·날짜 From~To 검색 칸이 함께 나오고 매퍼 `<sql id="search">` 가 방언마다 맞는 날짜 식을 낸다. INSERT 가 `FRST_REGIST_PNTTM` 에 `SYSDATE` 를, UPDATE 가 `LAST_UPDT_PNTTM = SYSDATE` 를 쓴다. 프로필 `generator.tables.<표>.columns` 로 컬럼마다 역할·꼴을 바꿀 수 있다. DDL 붙여넣기(DTO·INSERT·산출물 구조 점검 골든)에서 인덱스와 `ALTER … ADD (…PK…)` 가 읽혀 db-samples HR 의 noPk 오탐 8 이 0 이 된다. compare ⑱.
- In scope: `core/profile/Profile.java`(`Audit`·`ColumnGen`·`Generator.audit`·`TableGen.columns`) · `profiles/example.yaml` · `core/gen/GenModel.java`(Options 7 유지, 역할 호출) · 신설 `core/gen/FieldRoles.java` · `core/gen/Generator.java`·`web/GenerateRoutes.java`(Options 생성 자리) · `templates/gen/egov35/{List.jsp,Regist.jsp,Updt.jsp,Mapper.xml,VO.java}.ftl` · `core/gen/DdlReader.java`(LIKE·인덱스·ALTER PK) · 시험 `GenModelTest`(픽스처 `bbs` + 골든 둘)·`GenTemplatesTest`(bbs 골든·방언 매퍼를 bbs 로)·`ProfileStoreTest`·`DdlReaderTest`(+5, 샘플 비교 확장)·`DdlGenTest`(인덱스 수 한 줄)·`DdlCorpusTest`(정규식 제거) · 골든 `gen/model.json`·`gen/model-bbs.json`(새)·`gen/egov35|egov4|egov5/*`·`gen/mapper-*.xml`·`gen/bbs/*`(새)·`check/self-check.txt`(`GenModel.of`·`DdlReader` 줄)·`corpus/ddl-lint.json`·`corpus/ddl-lint-findings.txt`·`corpus/gen-hr.json`·`corpus/sqltables-diff.txt` · 글(`PLAN.md` 6장 프로필 예시 · `GenModel`·`TableGen` javadoc) · `design/compare-2026-10-28.md` ⑱ · PLAN 분할표·번들 표 · PROGRESS.
- Out of scope: 코드 사전·공통코드 select(마-5) · 트리·주부 표(마-7·8) · 메뉴 SQL(마-9) · 파일 upload 입력 · 날짜 바인딩 어노테이션(`@DateTimeFormat`) · VO 의 eGov 검색·페이징 여덟 필드 제거 · `Detail`·`Controller`·`Service`·`ServiceImpl`·`DAO` 템플릿 · 새 화면·`spring_source_generator.html` 변경 · 순수본 · 표본 추가 · compare ⑰ Minor 넷(사용자 (나)) · H2 FK 자동 인덱스 제외 · `verify-fingerprint.sh` · `JdbcMetaSource` · `core/meta` 레코드 변경 · PK 제약 이름 읽기(`PrimaryKey.name` 은 지금처럼 null — `alter-*.sql` 골든 불변) · `DdlCorpusTest` 기존 시험 넷의 판정 로직.

## 2. 현재 구조 요약
- 「맥락」 의 탐색 실측과 구조 줄 열둘이 요약이다. 덧붙이는 함정:
  - `GenModel.of` 의 `searchField` 블록(:91-92·:134-141·:147-149·:174-176)은 역할 모델로 대체된다 — 지우면 `of(4)` 복잡도·길이가 줄어 `self-check.txt` 두 줄이 바뀐다(의도된 변화 — 갱신 뒤 diff 를 이력에)
  - `Generator.run(12)` 는 인자 12 기준선 — 인자를 더하지 않는다. 감사 설정은 `base` Options 에 실어 넘긴다
  - `GenerateRoutes.postGenerate(7)` 복잡도 48 기준선 — 분기를 더하지 않는다(`g == null ? null : g.audit()` 삼항 하나는 기존 줄 꼴과 같아 `tableGens` 줄처럼 한 줄에)
  - `DdlReaderTest.matchesSnapshotTakenFromTheSameDdl` 의 UNIQUE 대조는 JDBC 쪽을 `UQ_` 접두로 걸렀다(「ux_* 는 범위 밖」). 1-68 뒤 DDL 쪽 `uniques` 에 `UX_USERS_EMAIL` 이 들어와 이 단언이 빨개진다. JDBC 골든은 방언마다 다르다 — mariadb·postgres 는 `ux_users_email` 이 `uniques`·`indexes` 양쪽, oracle·mssql·postgres-vendor 는 `indexes` 에만(실측 2026-11-01). 그래서 JDBC 쪽 거름은 `UQ_` 그대로 두고 **DDL 쪽에서 `indexes` 에 같은 이름이 있는 유니크(= CREATE UNIQUE INDEX 출신)를 뺀 뒤** 견준다. 인덱스 대조는 따로(⊆ 방향, 이름·컬럼·unique) — `ix_products_category`·`ix_orders_user_date`·`ux_users_email` 은 골든 다섯 다 `indexes` 에 있다
  - MySQL/MariaDB JDBC 스냅샷은 FK 마다 자동 인덱스를 갖는다 — 인덱스 대조는 「DDL 의 인덱스 ⊆ JDBC 의 인덱스」 방향만
  - `CorpusHr`(H2 HR)은 `CREATE UNIQUE INDEX` 를 건너뛴다 — JDBC HR 스냅샷(`hr-lint.json`)은 1-68 과 무관(불변)
  - `GeneratorCorpusTest` A 에 `a11y.*` 가 든다 — 새 입력(select·radio·날짜 From·To)마다 `id` + `<label for>` 가 있어야 한다
  - FreeMarker 보간은 `[=…]`, 지시자는 `<#…>` — JSP EL `${}`·MyBatis `#{}` 는 글자 그대로

## 3. 설계 결정 — 전부 [고정]

### 프로필(§3-1)
```java
/** 감사 컬럼 넷 묶음(마-2) — 이름은 대소문자 무시. generator.audit 가 없으면 EGOV, 있으면 적은 묶음만(빈 묶음은 없음) */
public record Audit(List<String> created, List<String> creator, List<String> updated, List<String> updater) {
    public static final Audit EGOV = new Audit(List.of("FRST_REGIST_PNTTM"), List.of("FRST_REGISTER_ID"), List.of("LAST_UPDT_PNTTM"), List.of("LAST_UPDUSR_ID"));
    public Audit { /* null → List.of() */ }
    public static Audit orDefault(Audit a) { return a == null ? EGOV : a; }
}
public record Generator(String templateSet, String basePackage, String outDir, Map<String, TableGen> tables, Audit audit) { … }
/** columns — 컬럼 이름(대소문자 무시) → 역할·꼴 재정의(마-1). 적은 것만 바뀐다 */
public record TableGen(String className, List<String> skipColumns, Map<String, ColumnGen> columns) {
    public TableGen { /* skipColumns null → List.of() · columns null → Map.of() */ }
    public TableGen(String className, List<String> skipColumns) { this(className, skipColumns, null); }  // ProfileStoreTest 그대로
}
/** list·insert·edit — 목록·등록·수정에 보일지. query — EQ|LIKE|BETWEEN|NONE. html — text|textarea|number|date|datetime|time|select|radio|checkbox. options — select·radio 보기 */
public record ColumnGen(Boolean list, Boolean insert, Boolean edit, String query, String html, List<String> options) { /* options null → List.of() */ }
```
- `profiles/example.yaml` `generator:` 절에 주석·예시 — `audit:`(넷 묶음, eGov 기본값 설명 · 「없으면 eGov 넷」) · `tables.TB_CUST_MST.columns: { CUST_NM: { query: LIKE }, MEMO: { html: textarea, list: false }, STATUS_CD: { html: select, options: [A, B], query: EQ }, REG_DT: { query: BETWEEN } }`. `PLAN.md` 6장 프로필 예시 `generator:` 에 `audit` 한 줄.
- `ProfileStoreTest` — 기존 `TableGen` 단언 그대로(2인자) + `columns`·`audit` 단언 하나씩(example.yaml 을 읽는다).

### 생성 모델(§3-2) — `GenModel.Options`
```java
public record Options(String basePackage, String module, List<String> skipTokens, Map<String, String> logicalNames, String dialect,
        Profile.TableGen table, Profile.Audit audit)   // 7인자 유지. table null = 재정의 없음, audit null = Audit.EGOV
```
- 5인자 생성자 그대로(`this(…, null, null)`). `className`·`skipColumns` 는 `o.table()` 에서(`cls` 줄·`skip` 줄만 바뀐다). `Generator.run` 은 `tg`·`base.audit()` 을 넣고, `GenerateRoutes` 는 `base` 에 `g == null ? null : g.audit()`.
- `GenModelTest.classNameAndSkipColumnsOverride` 의 7인자 둘 → `new Profile.TableGen("History", List.of("note", "EMP_NO"))`·`new Profile.TableGen(" ", null)`.

### 역할·입력 꼴 — 신설 `core/gen/FieldRoles.java`(§3-3)
패키지 전용 `final class`, 전부 `static`, 메서드마다 복잡도 ≤ 15. `GenModel.of` 의 컬럼 루프에서 `f` 기본 열한 키를 넣은 뒤 **한 줄** `warnings.addAll(FieldRoles.apply(f, c, simple, isPk, t, o));` — 기존 `searchField` 블록은 지운다.
```java
/** f 에 역할·꼴 키를 넣는다. 되돌리는 것은 경고(모르는 html·query 값) */
static List<String> apply(Map<String, Object> f, Column c, String javaType, boolean pk, Table t, GenModel.Options o)
```
넣는 키(모델 JSON 순서대로):
| 키 | 값 | 기본 규칙 |
|---|---|---|
| `audit` | `""`·`created`·`creator`·`updated`·`updater` | `Audit.orDefault(o.audit())` 의 묶음에 이름(대문자)이 들면 |
| `now` | boolean | `audit` 가 created·updated 이고 javaType 이 `LocalDate`·`LocalDateTime` — 매퍼가 DB 현재시각 식을 쓴다 |
| `html` | 문자열 | `htmlOf` — `Boolean` → checkbox · `LocalDate` → date · `LocalDateTime` → datetime · `LocalTime` → time · `Integer`·`Long`·`Short`·`BigDecimal`·`Double` → number · `byte[]` → `""`(없음) · String: CHECK `IN` 보기 있음 → select · `nativeType` 이 `CHAR` 이고 길이 1 이고 이름이 `_AT` 로 끝남 → radio · 길이 null 또는 ≥ 1000 → textarea · 그 밖 text |
| `options` | 문자열 목록 | select: `Validation.parse` 가 `InSet`(quoted, 컬럼 하나가 이 컬럼)을 낸 CHECK 의 값 · radio: `["Y", "N"]` · 그 밖 빈 목록 |
| `list` | boolean | `audit` 비고 `byte[]` 아니면 true |
| `insert` | boolean | 같은 조건(PK 포함) |
| `edit` | boolean | 같은 조건이고 PK 아님 |
| `query` | `LIKE`·`EQ`·`BETWEEN`·`NONE` | list 가 false 거나 PK → NONE · text → LIKE · select·radio·checkbox → EQ · date·datetime → BETWEEN · textarea·number·time → NONE |
| `required` | boolean | `!c.nullable()` 이고 기본값 없음(`Validation` 의 NOT NULL 조건과 같되 PK 포함 — 등록 화면에서 PK 는 사용자가 친다) |
| `step` | boolean | number 이고 `BigDecimal`·`Double` — 템플릿이 `step="any"` |

- 재정의: `o.table() != null` 이면 `columns.get(이름 대문자)` 의 null 아닌 값만 덮는다. `query`·`html` 은 대문자/소문자로 정규화, 허용 값 밖이면 그 키는 기본값 두고 경고 `"<표>.<컬럼>: 모르는 html/query 값 … — 기본값"`. `options` 가 있으면 그대로(select·radio 가 아니어도 둔다 — 템플릿이 안 쓴다).
- 모델 수준 목록(`GenModel.of` 끝, `m.put` 넷 — `FieldRoles.pick(fields, key)` 도우미):
  - `listFields` = `list` true · `insertFields` = `insert` true(화면 등록) · `editFields` = `edit` true(화면 수정, PK 제외) · `search` = `query` ≠ NONE
  - `insertCols` = insert true **또는** audit created·creator **또는** (audit updated·updater 이고 `!nullable`) · `updateCols` = edit true 또는 audit updated·updater. (`FieldRoles.sqlCols(fields, boolean insert)`)
  - `now` = 방언별 현재시각 식 — oracle·tibero `SYSDATE` · postgresql·mariadb `NOW()` · mssql `GETDATE()`
- `searchField` 키는 없앤다(템플릿이 안 쓴다). `fields`·`pk`·`nonPk`·`imports` 그대로.
- 기준선: `self-check.txt` 의 `GenModel.java|metric.complexity|복잡도 40 > 15 · of(4)`·`metric.methodLength|길이 120 > 100 · of(4)` 두 줄이 더 작은 수로 바뀐다(둘 다 15·100 을 넘으면 줄이 남고, 안 넘으면 사라진다). `FieldRoles` 는 새 줄을 만들지 않는다(≤ 15·≤ 100·인자 ≤ 7·중첩 ≤ 4).

### 템플릿(§3-4) — `templates/gen/egov35`
- `VO.java.ftl` — 필드·접근자는 그대로(전 컬럼). 끝에 `search` 중 BETWEEN 마다 `private String [=f.name]From; private String [=f.name]To;` + 접근자(기존 여덟 목록 꼴로 `<#list>`). 기존 여덟(searchCondition 등)은 둔다.
- `Mapper.xml.ftl`
  - `<sql id="search">` — `search` 마다:
    - LIKE: `<if test="[=f.name] != null and [=f.name] != ''">AND [=f.column] LIKE …</if>` (방언 셋 concat 은 지금 줄 그대로)
    - EQ: `<if test="[=f.name] != null<#if f.string> and [=f.name] != ''</#if>">AND [=f.column] = #{[=f.name]}</if>`
    - BETWEEN: `<if test="[=f.name]From != null and [=f.name]From != ''">AND [=f.column] &gt;= <날짜식 From></if>` · `<if test="[=f.name]To != null and [=f.name]To != ''">AND [=f.column] &lt; <날짜식 To + 1일></if>` — 날짜식은 방언 분기: oracle·tibero·postgresql `TO_DATE(#{x}, 'YYYY-MM-DD')` / `TO_DATE(#{x}, 'YYYY-MM-DD') + 1` · mariadb `STR_TO_DATE(#{x}, '%Y-%m-%d')` / `STR_TO_DATE(#{x}, '%Y-%m-%d') + INTERVAL 1 DAY` · mssql `CONVERT(DATE, #{x}, 23)` / `DATEADD(DAY, 1, CONVERT(DATE, #{x}, 23))`
  - INSERT: `(<#list insertCols as f>[=f.column]<#sep>, </#list>) VALUES (<#list insertCols as f><#if f.now>[=now]<#else>#{[=f.name]}</#if><#sep>, </#list>)`
  - UPDATE SET: `updateCols` 로(`f.now` 면 `[=f.column] = [=now]`), 비면 지금 PK 대입 꼴 그대로
  - resultMap·`cols`·목록·건수·상세·삭제·페이징 셋은 그대로
- `List.jsp.ftl` — 검색 폼: `search` 마다 `<label for>` + 입력 — LIKE·EQ(text): `<form:input path id title/>` · EQ(select·radio): `<form:select path id title><form:option value="">전체</form:option><#list f.options as v><form:option value="[=v]">[=v]</form:option></#list></form:select>` · EQ(checkbox): `<form:select>` 전체·true·false · BETWEEN: `<form:input path="[=f.name]From" id="[=f.name]From" type="date" title="[=f.comment] 시작"/> ~ <form:input path="[=f.name]To" id="[=f.name]To" type="date" title="[=f.comment] 끝"/>`(라벨 둘). 기존 `searchKeyword` 칸은 없앤다. 표 머리·행은 `listFields`(첫 칸이 상세 링크 — `listFields` 의 첫 항목, PK 파라미터는 `pk`).
- `Regist.jsp.ftl` — `insertFields` 마다 `html` 로 분기(FreeMarker 매크로 `<#macro input f readonly=false>` 하나를 `header.ftl` 옆 새 `input.ftl` 에 두고 Regist·Updt 가 `<#include>`): text `<form:input … maxlength?>` · textarea `<form:textarea path id title rows="5"/>` · number `<form:input type="number"<#if f.step> step="any"</#if>/>` · date `type="date"` · datetime `type="datetime-local"` · time `type="time"` · select `<form:select>` + `<form:option value="">선택</form:option>` + 보기 · radio 보기마다 `<form:radiobutton path="[=f.name]" id="[=f.name]_[=v]" value="[=v]"/><label for="[=f.name]_[=v]">[=v]</label>` · checkbox `<form:checkbox path id/>`. `required` 면 `required="required"`(select·checkbox·radio 는 안 붙인다). 끝에 `<form:errors>` 그대로.
- `Updt.jsp.ftl` — `pk` 마다 readonly 입력(지금 줄) + `editFields` 마다 같은 매크로. 
- egov4·egov5 는 `extends` 라 자동. `Detail`·`Controller`·`Service`·`ServiceImpl`·`DAO`·`header` 불변.
- 자기 엔진 왕복(`GenTemplatesTest.roundTripThroughOwnEngine`)·`GeneratorCorpusTest` 의 코드 검사 A(error·a11y·security·`mybatis.noWhere`·parseError)가 템플릿의 RED→GREEN 자리. `mybatis.nullCompare`·`insertNoColumns`·`pagingNoOrder`(번들 63) 가 warn 이라 B 로 센다 — INSERT 는 컬럼 목록이 있고 페이징은 ORDER BY 가 있다.

### 시험·골든(§3-5)
- `GenModelTest`
  - 새 픽스처 `bbs()` — `EGOV.COMTNBBS`(게시판) 13 컬럼: `NTT_ID NUMBER(20) NOT NULL`(PK) · `BBS_ID VARCHAR2(20) NOT NULL`(PK) · `NTT_SJ VARCHAR2(2000) NOT NULL` · `NTT_CN CLOB` · `USE_AT CHAR(1) NOT NULL` · `NTCE_BGNDE DATE` · `RDCNT NUMBER(10)` · `SNTNC_SE VARCHAR2(2)` + CHECK `SNTNC_SE IN ('A','B','C')` · `ATCH_FILE BLOB` · `FRST_REGIST_PNTTM DATE NOT NULL` · `FRST_REGISTER_ID VARCHAR2(20) NOT NULL` · `LAST_UPDT_PNTTM DATE` · `LAST_UPDUSR_ID VARCHAR2(20)`. 코멘트는 한글 논리명.
  - `bbsGolden` → `gen/model-bbs.json`(새). `rolesAndInputKinds`(단언 명시): NTT_SJ text·LIKE·required · NTT_CN textarea·NONE · USE_AT radio[Y,N]·EQ·required · NTCE_BGNDE datetime·BETWEEN · RDCNT number·NONE · SNTNC_SE select[A,B,C]·EQ · ATCH_FILE list·insert·edit 전부 false·NONE · 감사 넷 audit 키·역할 false · `insertCols` 컬럼 = PK 둘 + NTT_SJ·NTT_CN·USE_AT·NTCE_BGNDE·RDCNT·SNTNC_SE + FRST_REGIST_PNTTM(now)·FRST_REGISTER_ID(LAST_* 는 NULL 허용이라 없음) · `updateCols` = edit 여섯 + LAST_UPDT_PNTTM(now)·LAST_UPDUSR_ID · `search` 넷(NTT_SJ·USE_AT·NTCE_BGNDE·SNTNC_SE). `columnOverride`: `TableGen(null, null, {NTT_CN: ColumnGen(true, null, null, "LIKE", "text", null), RDCNT: ColumnGen(null, null, null, "EQ", null, null), NTT_SJ: ColumnGen(null, null, null, "bogus", "wiki", null)})` → NTT_CN list·LIKE·text · RDCNT EQ · NTT_SJ 기본값 + 경고 둘. `auditOverride`: `Audit([REG_DT], [], [], [])` → FRST_* 넷이 보통 컬럼(list true), `REG_DT` 없음 → 경고 없음. `golden()`(empHist)은 갱신된 `gen/model.json`(검색 둘 — JOB_ID·DEPT_NM·CLASS LIKE, START_DATE 는 PK 라 NONE, NOTE textarea).
- `GenTemplatesTest` — `render(set, dialect)` 를 `render(set, dialect, Table)` 로. `setsGolden`·`roundTripThroughOwnEngine` 은 empHist 그대로(골든 재생성) · `mapperPerDialect` 는 **bbs** 로(`gen/mapper-<방언>.xml` 다섯 — BETWEEN 날짜식·`now` 식이 방언마다 골든에) · 새 `bbsGolden` — egov35·oracle 로 bbs 를 그려 `List`·`Regist`·`Updt`·`VO`·매퍼 다섯만 `gen/bbs/<파일>` 골든 + 자기 엔진 왕복(구문·매퍼 여섯·CRUD 넷)을 bbs 에도.
- `GeneratorCorpusTest`·`GeneratorJavacTest` 는 코드 불변 — 골든 `gen-hr.json`(`checkSoft` 변화 기대, `files` 70·`programs` 49 불변)만.
- 골든 갱신은 행마다 한 번 `-Dgolden.update=true`(해당 시험만 `-Dtest=`), diff 를 읽고 이력에 — `gen/egov4`·`gen/egov5` 는 egov35 와 같은 변화여야 한다(다르면 중단 조건).

### DdlReader — LIKE(1-67, §3-6)
설계 47 §3-18 그대로: `nameOnly` — `after` 가 `LIKE\s`(대소문자 무관)로 시작하면 key `like`, notes `"LIKE 원본 — 컬럼은 원본 표"`. `item()` 머리 — 항목이 `LIKE\s` 로 시작하면(PG `(LIKE x INCLUDING ALL)`) `d.notes.put("like", …)` 하고 return(컬럼 아님). `LIKE_CNT INT` 는 `\s` 가 없어 컬럼. 시험 — `likeHead`(`CREATE TABLE Y LIKE X` → [Y] 컬럼 0 notes like · unreadable 0) · `likeInParens`(`CREATE TABLE Y (LIKE X INCLUDING ALL)` → 같음) · `likeColumnNameStillColumn`(`CREATE TABLE T (LIKE_CNT INT)` → 컬럼 1). `gen/*` 골든 diff 0(이 행만 보면 — 7-17 이 먼저 바꾼 것은 그대로).

### DdlReader — 인덱스·ALTER PK(1-68, §3-7)
- `Draft` 에 `final List<Index> indexes = new ArrayList<>()` · `String pkName`.
- `CREATE INDEX` 문장 — `read` 의 CREATE 머리 판정 앞에 한 줄 `if (createIndex(stmt, tables)) { continue; }`:
```java
private static final Pattern CREATE_INDEX = Pattern.compile("^CREATE\\s+(UNIQUE\\s+)?(?:BITMAP\\s+)?INDEX\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?", Pattern.CASE_INSENSITIVE);
/** CREATE [UNIQUE] INDEX 이름 ON 표 (컬럼 …) — 표가 앞에 없으면 버린다(unreadable 아님). 유니크는 uniques 에도(JdbcMetaSource 와 같게) */
private static boolean createIndex(String stmt, Map<String, Draft> tables)
```
이름은 `nameEnd` 로, `ON\s+` 뒤 표 이름도 `nameEnd` 로, 그 뒤 첫 `(` 부터 `matching` 까지가 컬럼 — `splitTop(body, ',')` 마다 `indexColumn(s)`: trim → 끝의 `(?i)\s+(ASC|DESC)(\s+NULLS\s+(FIRST|LAST))?$` 뗌 → `unquote`. 함수식(`UPPER(x)`)은 그 글 그대로. `Index(name, unique, cols)` 를 `d.indexes` 에, unique 면 `UniqueKey(name, cols)` 를 `d.uqs` 에.
- 표 안 `KEY name (cols)`·`INDEX name (cols)` — `item()` 의 제약 줄 가지 첫머리에 `if (INLINE_INDEX.matcher(item).lookingAt()) { d.indexes.add(…); return; }`(`INLINE_INDEX = ^(?:KEY|INDEX)\s+([^\s(]+)\s*\(` — `UNIQUE KEY`·`FULLTEXT KEY`·`SPATIAL INDEX` 는 머리가 달라 안 맞는다. 컬럼은 `matching` 까지 `indexColumn`).
- `ALTER TABLE … ADD` 가지 — 기존 FK·UQ `find` 옆에 PK 도 `find`:
```java
private static final Pattern PK_ADD = Pattern.compile("(?:CONSTRAINT\\s+(\\S+)\\s+)?\\bPRIMARY\\s+KEY\\s*(?:CLUSTERED\\s+|NONCLUSTERED\\s+)?\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);
```
따옴표 글을 비운 나머지(`QUOTED` 치환, UQ 와 같은 글)에서 찾아 `d.pk` 를 바꾸고 `d.pkName = unquote(group(1))`. 기존 `ALTER_PK` 블록(:199-209)은 지운다 — `read` 의 분기 둘이 줄고 `createIndex` 한 줄이 늘어 `read(1)` 복잡도 38 → 37 안팎(실행해야 안다), 중첩 6 그대로.
- 출력 루프 — `withIndexes(dropPk(d))`: `d.indexes` 에서 이름이 `d.pkName` 과 같거나(대소문자 무시) 유니크이고 컬럼이 `d.pk` 와 똑같은(대문자·순서) 것을 뺀다, 같은 이름의 `uqs` 도 뺀다 — JDBC 의 「PK 이름 뺀 전부」 와 같다. HR 의 `CREATE UNIQUE INDEX emp_emp_id_pk` → ALTER PK 뒤 사라진다.
- 기준선 `item(3)` 30 → 32 안팎(LIKE 한 분기 + 인라인 한 분기), `read(1)` 38 → 37 안팎 — `self-check.txt` 두 줄 갱신(의도된 변화, diff 를 이력에).
- `DdlReaderTest`
  - `readsCreateIndexAndInlineKey` — `CREATE TABLE t (a INT, b VARCHAR(10), KEY k_b (b), FULLTEXT KEY ft (b)); CREATE INDEX i_ab ON t (a ASC, b DESC); CREATE UNIQUE INDEX u_a ON t (a); CREATE INDEX i_up ON t (UPPER(b));` → indexes 이름 넷 `K_B`·`I_AB`·`U_A`·`I_UP`(FULLTEXT 없음) · `I_AB` 컬럼 [a, b] · `U_A` unique 이고 uniques 에도 `U_A` · `I_UP` 컬럼 `["UPPER(b)"]` · unreadable 0
  - `alterAddParenthesizedPrimaryKey` — HR 꼴: `CREATE TABLE r (id INT, n VARCHAR(10)); CREATE UNIQUE INDEX r_pk ON r (id); CREATE TABLE c (id INT, r_id INT); ALTER TABLE c ADD ( CONSTRAINT c_pk PRIMARY KEY (id) , CONSTRAINT c_r_fk FOREIGN KEY (r_id) REFERENCES r(id) ); ALTER TABLE r ADD ( CONSTRAINT r_pk PRIMARY KEY (id) );` → c PK [ID]·FK 1 · r PK [ID]·indexes 0·uniques 0(이름이 PK 와 같아 빠짐) · `id` nullable false
  - `matchesSnapshotTakenFromTheSameDdl` 확장 — UNIQUE 단언은 JDBC 쪽 `UQ_` 거름 그대로, DDL 쪽은 `t.indexes()` 에 같은 이름(대소문자 무시)이 있는 유니크를 뺀 집합으로(CREATE UNIQUE INDEX 출신 제외 — JDBC 골든이 방언마다 그것을 uniques 에 넣기도 안 넣기도 한다) · 새 단언: DDL 의 `indexes` 마다(이름 대문자 → unique + 컬럼 대문자 목록) JDBC `indexes` 에 같은 이름·같은 unique·같은 컬럼이 있다(⊆ 방향만 — MySQL FK 자동 인덱스는 DDL 에 없다)
  - 1-67 셋 + 위 둘 = +5
- `DdlGenTest.roundTripThroughDdlReader` — 비유니크 인덱스 수(`indexes` 중 `!unique`)가 원본과 같다는 단언 한 줄.
- `DdlCorpusTest` — `CREATE_INDEX`·`FIRST_WORD`·`indexColumns`·`lastName`·`lintTables` 의 둘째 루프를 지운다(`lintTables` 는 표 합치기만). `structureLint` 의 그룹 글에 `· 인덱스 i`(그룹의 `indexes` 합) 를 더한다 → `ddl-lint.json`·`ddl-lint-findings.txt` 재생성. `ddl-summary.json`·`ddl-unreadable.txt` 는 diff 0 기대(인덱스 문장은 전에도 unreadable 이 아니었다).
- 기대 수치(실행해야 안다): db-samples noPk 8 → 0 · ruoyi dupIndex 0 → n(인라인 KEY 가 읽힌다) · egov dupIndex 22 안팎 유지 · chinook 그대로 · `sqltables-diff.txt` 32 → 30(LIKE 둘 — mariadb `create table y LIKE x`·postgres `create table y LIKE x` 둘 다 머리 LIKE 꼴, 실측).

### 측정·판정(§3-8)
- V-48a 재채점 — `GeneratorCorpusTest`(A 0, `gen-hr.json` 갱신 — `checkSoft` 변화 기대) · `GeneratorJavacTest`(egov35-lib 있음 — javac 0·매퍼 파싱·HR 건수·목록) · `DdlCorpusTest` 다섯(`ddl-lint.json`·findings 갱신, summary·unreadable 불변) · `SqlFixtureCorpusTest`(`sqltables-diff` B 갱신) · `AnalyzeCorpusTest`·`CliCorpusTest`(diff 0 기대). 수치는 게이트 아님. 전·후·벽시계를 이력에.
- R-17 손 판정 — ① `ddl-lint-findings.txt` 의 사라진 줄·새 줄 전부를 참·참(규칙 정의)·오탐으로(기대: db-samples noPk 8 사라짐 = 오탐 해소 · ruoyi 새 dupIndex) ② HR egov35 생성물의 목록 화면 검색 칸 수·등록 화면 입력 꼴 분포(표 7 × 꼴 — 건수만, 생성물 본문은 안 옮긴다) ③ `gen-hr.json` `checkSoft` 변화의 규칙 이름. 결함 둘 이하는 고침(음성 먼저), 넘으면 새 행.
- V-48 — `design/compare-2026-10-28.md` 에 `## ⑱ 재측정 — 번들 68(2026-11-01)`: 표 「역할·입력 꼴 × HR 표 7」(표마다 list·insert·edit·search 수, html 꼴 건수) · 「DdlReader 인덱스 × DDL 표본」(그룹 15 — 인덱스 수·noPk·dupIndex 전후) · `sqltables-diff` 32 → N · `--corpus`·`--db` 벽시계 · 「드러난 것」 · 「후보로」(⑰ Minor 넷 · H2 FK 자동 인덱스 · 마-5 코드 사전 · 마-7·8 · 날짜 바인딩 `@DateTimeFormat` · PK 제약 이름). PLAN 4장 V-16·V-17 구절은 완료 행이라 안 고친다(⑱ 에 적는다).

## 4. 실행 스텝
### S0 — 설계 문서·행·번들 표 (실행 세션이 옮긴다)
- 대상: `design/52-bundle68.md`(이 플랜 A 절 그대로) · `PLAN.md` 번들 표 「번들 68」 행(B 절로 바꾼다) · 분할표 `7-16a`·`7-16b`·`7-17`·`1-68`·`V-48a`·`R-17`·`V-48`(B 절 글, `V-47` 뒤 — `1-67` 행은 있는 그대로) · `PROGRESS.md` 「열려 있는 것」 머리에 「번들 68(설계 52, 생성기 역할·입력 꼴·검색 + DdlReader LIKE·인덱스) 설계 끝 — 가지 `work/2026-11-01d`, 다음은 「번들 해」」 + 이력 한 줄(직전 줄 `번들 67 머지` 의 해시 칸을 `caff4bd` 로).
- 검증: `bash scripts/doc-lint.sh design/52-bundle68.md` · `bash scripts/verify.sh`(docs 레인) · `git commit -m "docs: S0 설계 52 — 번들 68"`.
- 의존: 없음.

### 7-16a — 프로필 `generator.audit`·`tables.<표>.columns`
- 대상: `core/profile/Profile.java`(§3-1 — `Audit`·`ColumnGen`·`Generator` 5인자·`TableGen` 3인자 + 2인자) · `profiles/example.yaml` · `core/profile/ProfileStoreTest.java`(단언 둘) · `PLAN.md` 6장 예시 한 줄.
- 변경(TDD): ① `ProfileStoreTest` 에 `p.generator().audit()` · `tables().get("TB_CUST_MST").columns().get("CUST_NM").query()` 단언 → 컴파일 RED ② 레코드·yaml → GREEN.
- 검증: `bash scripts/mvn.sh -q test -Dtest='ProfileStoreTest,ProfileRoutesTest,SelfCheckTest'` 초록 · `self-check.txt` diff 0 · PMD 새 짝 0.
- 의존: S0.

### 7-16b — `FieldRoles` + `GenModel` 역할 모델 + Options 7
- 대상: 신설 `core/gen/FieldRoles.java` · `core/gen/GenModel.java`(§3-2·§3-3 — Options, searchField 블록 제거, 호출 한 줄, `m.put` 넷) · `core/gen/Generator.java`(Options 생성 줄) · `web/GenerateRoutes.java`(base Options 줄) · `core/gen/GenModelTest.java`(§3-5 — `bbs()`·시험 셋·7인자 둘) · 골든 `gen/model.json`(갱신)·`gen/model-bbs.json`(새) · `check/self-check.txt`(`of(4)` 두 줄). 청크 규칙(파일 1~3) 초과 — Options 시그니처 하나가 네 파일을 묶는다, 한 커밋.
- 변경(TDD): ① `GenModelTest` 새 셋 + 7인자 바꿈 → 컴파일 RED ② `FieldRoles`·`GenModel`·`Generator`·`GenerateRoutes` → `rolesAndInputKinds`·`columnOverride`·`auditOverride` GREEN ③ `-Dgolden.update=true -Dtest='GenModelTest'` 한 번 → `gen/model.json` diff 읽기(searchField 사라짐·키 아홉 추가·목록 넷) · `gen/model-bbs.json` 새 ④ `SelfCheckTest#mainMatchesBaseline` 빨강(두 줄 수 변화) → `-Dgolden.update=true -Dtest='SelfCheckTest#mainMatchesBaseline'` → diff 가 `GenModel.java` 두 줄뿐인지 읽는다(다른 줄이 바뀌면 중단 조건).
- 검증: `bash scripts/mvn.sh -q test -Dtest='GenModelTest,GeneratorTest,GenerateRoutesTest,SelfCheckTest,ArchitectureTest'` 초록(`GenTemplatesTest` 는 7-17 까지 빨강 — 템플릿이 `searchField` 를 아직 쓴다 → **같은 커밋에 넣지 말고 7-17 로**; 빠른 `verify.sh` 는 7-17 뒤에 돈다. 7-16b 커밋은 commit-guard 가 도장을 요구하므로 7-16b·7-17 을 **한 도장**으로 — 7-17 끝에 `verify.sh` 초록 뒤 커밋 둘을 잇따라) · PMD 새 짝 0(`FieldRoles`).
- 의존: 7-16a.

### 7-17 — 템플릿 다섯 + 골든
- 대상: `templates/gen/egov35/{List.jsp,Regist.jsp,Updt.jsp,Mapper.xml,VO.java}.ftl` + 신설 `templates/gen/egov35/input.ftl`(매크로) · `core/gen/GenTemplatesTest.java`(§3-5) · 골든 `gen/egov35|egov4|egov5/*`(갱신)·`gen/mapper-*.xml`(bbs 로 갱신)·`gen/bbs/*`(새 다섯).
- 변경: TDD 예외(템플릿 — 2026-10-29 허락). RED→GREEN 은 `GenTemplatesTest.roundTripThroughOwnEngine`(bbs 포함) + V-48a 의 `GeneratorCorpusTest`. 골든은 `-Dgolden.update=true -Dtest='GenTemplatesTest'` 한 번 → diff 읽기(egov4·egov5 가 egov35 와 같은 변화인지).
- 검증: `bash scripts/mvn.sh -q test -Dtest='GenTemplatesTest,GenModelTest,GeneratorTest,GenerateRoutesTest,SmokeHtmlUnitTest#crudGeneratorRuns'` 초록 · `bash scripts/verify.sh` 초록 → 7-16b 커밋 → 7-17 커밋.
- 의존: 7-16b.

### 1-67 — `DdlReader` `CREATE TABLE … LIKE`
- 대상: `core/gen/DdlReader.java`(§3-6) · `core/gen/DdlReaderTest.java`(+3).
- 변경(TDD): 시험 셋 RED → `nameOnly`·`item` 머리 → GREEN. `self-check.txt` 의 `item(3)` 수가 바뀌면 1-68 끝에서 한 번에 갱신(이 행에서는 빨강을 두지 않는다 — 분기가 `item` 밖 도우미면 안 바뀐다: `item` 머리의 `LIKE\s` 판정은 `likeItem(d, item)` 도우미가 boolean 을 내고 `item` 은 `if` 하나).
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,DdlGenTest,InsertGenTest,SelfCheckTest'` 초록 · `gen/*` 골든 diff 0(7-17 뒤 상태 기준).
- 의존: 7-17(가지 순서일 뿐).

### 1-68 — `DdlReader` 인덱스·표 안 KEY·ALTER ADD(…PK…) + `DdlCorpusTest` 정규식 제거
- 대상: `core/gen/DdlReader.java`(§3-7) · `core/gen/DdlReaderTest.java`(+2, 샘플 비교 확장) · `core/gen/DdlGenTest.java`(한 줄) · `core/gen/DdlCorpusTest.java`(정규식·도우미 제거, 그룹 글 `인덱스 i`) · 골든 `corpus/ddl-lint.json`·`corpus/ddl-lint-findings.txt`(갱신) · `check/self-check.txt`(`DdlReader` 두 줄).
- 변경(TDD): ① `DdlReaderTest` 둘 + 샘플 비교 확장 → RED ② `DdlReader` → GREEN ③ `DdlCorpusTest` 정리 → `-Dgolden.update=true -Djunit.timeout=10m -Dtest='DdlCorpusTest#structureLint'` 한 번 → diff 읽기(db-samples noPk 8 → 0 · 인덱스 수 · ruoyi) ④ `SelfCheckTest` 갱신(DdlReader 두 줄만).
- 검증: `bash scripts/mvn.sh -q test -Djunit.timeout=10m -Dtest='DdlReaderTest,DdlGenTest,DdlCorpusTest,GenRoutesTest,InsertRoutesTest,SelfCheckTest'` 초록 · `ddl-summary.json`·`ddl-unreadable.txt`·`hr-lint.json`·`pg-lint.json` diff 0 · findings ≤ 1,000 줄 · 건수를 이력에.
- 의존: 1-67.

### V-48a — 재채점(corpus)
- 대상: 골든 `corpus/gen-hr.json`·`corpus/sqltables-diff.txt` · `PROGRESS.md`.
- 변경: `bash scripts/mvn.sh -q test -Djunit.timeout=10m -Dtest='GeneratorCorpusTest,GeneratorJavacTest,SqlFixtureCorpusTest,AnalyzeCorpusTest,CliCorpusTest'` → A 0 · B 갱신은 `-Dgolden.update=true` 로 그 둘만 → diff 를 이력에.
- 검증: 위 명령 갱신 없이 초록 · `GeneratorJavacTest` 가 건너뛰면(egov35-lib 없음) 사유를 이력에.
- 의존: 1-68.

### R-17 — 손 판정
- 대상: `design/compare-2026-10-28.md`(⑱ 「손 판정」 절) · `PROGRESS.md` · (고침이 나면) `FieldRoles`·`DdlReader`·시험·골든.
- 변경: §3-8 ②. 표 하나(항목 · 표본 · 판정 · 근거) + 분류 합. 결함 둘 이하는 고치고(음성 먼저 → RED → 고침 → 골든 다시) 넘으면 「후보로」.
- 검증: `bash scripts/doc-lint.sh design/compare-2026-10-28.md` · 분류 합 = 줄 수 · (고침 있으면) 해당 행 검증 재실행.
- 의존: V-48a.

### V-48 — compare ⑱ · `--corpus` · `--db`
- 대상: `design/compare-2026-10-28.md`(⑱ 나머지) · `PROGRESS.md`.
- 검증: `bash scripts/verify.sh` 초록 → `bash scripts/verify.sh --corpus` 초록(다른 컨테이너·WSL 내리고) → `wsl --shutdown` · Docker 다시 → `bash scripts/verify.sh --db` 혼자 초록(약 17~20분, 다른 JVM 안 띄운다). 끝 줄이 더 부르는 레인이 없어야 한다. 벽시계 둘을 이력에.
- 의존: R-17.

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7 · 행의 축·강제 지점과 diff · 역할 기본값의 결정적 음성(감사 컬럼이 등록·수정·목록·검색에 없음 · `byte[]` 없음 · PK 는 edit false · textarea 999 는 text · `_AT` 가 아닌 CHAR(1) 은 text · 재정의 모르는 값은 기본값 + 경고) · INSERT/UPDATE 컬럼 집합(NULL 허용 LAST_* 는 INSERT 에 없음) · 방언 다섯 날짜식 · 템플릿의 `id`·`<label for>` 짝 · `self-check.txt` 변화가 `GenModel.of`·`DdlReader` 넉 줄뿐인가 · `gen/egov4`·`egov5` 가 egov35 와 같은 변화인가 · DdlReader 의 PK 이름 인덱스 제거·`FULLTEXT` 제외·함수식 컬럼 · `ddl-summary.json`·`hr-lint.json`·`pg-lint.json`·`alter-*.sql` 불변 · 골든이 의도된 변화인가) → `bash scripts/gate-probe.sh` → `bash scripts/verify.sh`(리뷰 고침 뒤 `--corpus`·`--db` 는 바뀐 레인만 다시) → `git push -u origin work/2026-11-01d` → `gh pr create --base main`(행마다 시작 세 줄 표 + 로컬 `caff4bd` 같이 + ⑱ 핵심 수치) → CI 폴링(`gh api … check-runs`) → claude[bot] 처분 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → 닫기 커밋은 다음 가지 → 끝 보고. PR 본문 끝 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## 5. 금지 사항
- `pure/`·portfolio·`docs/`·`core/meta`(레코드)·`core/dialect`·`JdbcMetaSource`·`core/deliverable`·`tools/*.html`·`pom.xml`·`scripts/verify-fingerprint.sh`·`corpus/MANIFEST`·표본 수정 금지 · 새 Maven 의존성 금지 · 새 화면·새 탭·서버 라우트 금지
- `GenModel.Options` 인자 7 유지 · `Generator.run` 인자 12 유지 · `GenerateRoutes.postGenerate` 분기 추가 금지 · 새 메서드 복잡도 ≤ 15 · `self-check.txt` 변화는 `GenModel.of` 둘·`DdlReader` 둘(`item`·`read`) 넉 줄까지 — 그 밖 새 줄 금지 · PMD 새 짝 금지(`FieldRoles`·`DdlReaderTest`·`ProfileStoreTest`·`GenTemplatesTest`·`DdlGenTest` 는 기준선에 없다)
- `PrimaryKey.name` 채우기 금지(`alter-*.sql`·`ddl-*.sql` 골든 불변) · `UNIQUE KEY`·`UNIQUE` 제약을 `indexes` 에 넣지 않는다 · `FULLTEXT`·`SPATIAL` 안 읽는다 · `CREATE INDEX` 의 표가 앞에 없으면 조용히 버린다(unreadable 로 올리지 않는다 — 표 뒤 인덱스 꼴만 받는다)
- 템플릿: `Detail`·`Controller`·`Service`·`ServiceImpl`·`DAO`·`header` 불변 · VO 의 eGov 여덟 필드 삭제 금지 · 날짜 바인딩 어노테이션 추가 금지 · `vars.valid` 기본값 변경 금지(egov35·egov4 "false") · 코드 사전·upload 금지
- 골든 `ddl-summary.json`·`ddl-unreadable.txt`·`hr-lint.json`·`pg-lint.json`·`alter-*.sql`·`ddl-*.sql`·`insert/*`·`meta/*` 불변 · `DdlCorpusTest.DDLS` 에 ruoyi 를 넣지 않는다 · 기존 시험 넷 판정 로직 수정 금지
- 골든·compare·이력에 표본 코드 본문·생성물 본문을 싣지 않는다(건수·이름·꼴만) · 원본 되쓰기 API·CLI 금지 · `pmd-baseline.sh` 안 돌린다 · `--db` 와 다른 JVM 겹치기 금지 · `--corpus` → Docker → `--db` 순서

## 6. 최종 검증
- `bash scripts/verify.sh` · `--corpus` · `--db` 초록(순서대로, 따로) · `pmd:check` 0 · `gate-probe.sh` 10/10
- 골든: `gen/model.json`·`gen/model-bbs.json`·`gen/egov35|egov4|egov5/*`·`gen/mapper-*.xml`·`gen/bbs/*`·`gen-hr.json`·`ddl-lint.json`·`ddl-lint-findings.txt`·`sqltables-diff.txt`·`self-check.txt`(넉 줄) 갱신 · 나머지 불변
- 시험: `GenModelTest` +3 · `GenTemplatesTest` +1(bbs) · `ProfileStoreTest` +2 단언 · `DdlReaderTest` +5 + 샘플 비교 확장 · `DdlGenTest` 한 줄 · `DdlCorpusTest` 정규식 0 · `GeneratorCorpusTest`·`GeneratorJavacTest` A 0 · `SmokeHtmlUnitTest.crudGeneratorRuns` 초록 · `ArchitectureTest`·`VerifyLanesTest` 초록
- 수동: 데모 스냅샷 eGov 꼴 표 하나 생성 → 결과 폴더의 `*Regist.jsp` 에 감사 컬럼 없음·`USE_AT` 라디오·`required` · `*List.jsp` 검색 칸 여럿 · 매퍼 INSERT 에 `SYSDATE`(눈으로 한 번, 이력에 한 줄)
- compare ⑱ 표 둘 + R-17 표 + 「드러난 것」 + 「후보로」 · 이력에 건수·벽시계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- `self-check.txt` 에 넉 줄 밖 변화 · `ddl-summary.json`·`ddl-unreadable.txt`·`hr-lint.json`·`pg-lint.json`·`alter-*.sql` 변화 · `pmd:check` 새 짝 · `ArchitectureTest`·`VerifyLanesTest` 빨강
- `gen/egov4`·`gen/egov5` 골든 변화가 egov35 와 다름 · `GeneratorCorpusTest` A(특히 a11y·프로그램 수·CRUD 넷) 고침 둘로 안 닫힘 · `GeneratorJavacTest` javac 오류 또는 매퍼 파싱 실패 고침 둘로 안 닫힘
- `ddl-lint.json` 에서 db-samples noPk 가 0 이 안 됨(ALTER PK 를 못 읽는다) · findings 1,000 줄 초과 · `DdlReaderTest` 샘플 인덱스 대조에서 JDBC 골든에 없는 인덱스(⊆ 가 깨짐 — 골든 메타 스냅샷의 인덱스 수집 범위 문제라 사용자 결정)
- `verify.sh --db` 가 메모리로 끊김(이력 2026-11-01 번들 66: 여유 RAM 최저 2.4GB) — 고아 JVM 끄고 한 번 더, 또 끊기면 보고
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① `self-check.txt` 의 `GenModel.of`·`DdlReader item`·`read` 새 수치 ② `ddl-lint.json` 전후(db-samples noPk 0 · ruoyi dupIndex n · 인덱스 수) ③ `gen-hr.json` `checkSoft` 변화 ④ `--corpus`·`--db` 벽시계. 첫 스텝 전: `bash scripts/verify.sh` 초록 · `git log --oneline -1`(`caff4bd`) · `ls C:/workspace/toolbox-corpus/egov35-lib | wc -l`(설계 때 35 — javac 시험이 돈다) · Docker Desktop 상태. (설계에서 확인한 것 — `sqltables-diff` LIKE 둘은 머리 꼴이라 32 → 30 · JDBC 골든 다섯 다 `ix_*`·`ux_users_email` 이 `indexes` 에 있고 uniques 는 방언마다 달라 §3-7 대조 규칙을 그에 맞췄다.)
- **사용자만 정할 수 있는 것** — 없음((가) 인덱스 읽기 붙임 · (나) Minor 넷 뺌 · (다) 머지 위임 전부 2026-11-01 결정).
