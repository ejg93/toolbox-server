# 설계 54 — 번들 70: 오류 잡기 — 구조 점검 넷·DdlReader 넷·개발자 도구 Hex 셋 (Fable 설계 · Opus 실행, 2026-11-02)

## 맥락
번들 67(설계 51)·68(설계 52)·69(설계 53)가 쌓아 둔 「후보로」·마무리 리뷰 Minor 가운데 **오류**만 모아 닫는다. 새 화면·새 기능은 없다. 셋 — ① 산출물 SQL 「91 구조 점검」(`core/deliverable/StructureLint`): H2 스냅샷의 FK 자동 인덱스(`*_FK_INDEX_n`)가 dupIndex 오탐 8(compare ⑰ R-16 「참(규칙 정의)」) · `backs` 가 같은 컬럼 유니크 인덱스 둘 중 먼저 온 것을 받침으로 삼아 지적이 인덱스 순서에 달렸다(번들 67 Minor ①) · 다-17 유니크의 NULL 허용 컬럼 · 다-19 LOB 컬럼 수(compare ③·⑰ 「후보로」) ② CREATE 문 읽기(`core/gen/DdlReader`): PK 제약 이름을 안 채운다(`PrimaryKey.name` 늘 null — 구조 점검 지적이 「PK(…)」 로만 나온다) · typed·CTAS·LIKE 표가 같은 입력 안에 원본 표·형식이 있어도 컬럼 0(1-66·1-67·1-69 는 이름만) · 번들 69 Minor 둘(`INDEX_PREFIX` 가 숫자 인자 하나 함수식 `f(10)` 도 뗀다 · typed 표 괄호 몸의 PK 안 읽음) ③ 개발자 도구 Hex·인코딩 탭(`tools/dev_tools.html`) 번들 67 Minor 셋(③ 「깨진 문자 n」 ↔ 「깨진 자리 n」 셈이 끝 잘림·후속 바이트에서 어긋남 · ④ `hexToBytes` 가 글 중간 `0x` 도 뗀다 · ⑥ `JsCorpusTest` 가 `decodeOff` 비면 B 기준선을 안 불러 「새로 고쳐짐」 래칫이 안 돈다).

**이미 끝난 것 — 걷어 낸다**(2026-11-02, 이력·분할표·compare ⑰·⑱·⑲ 대조):
- ⑰ 「후보로」 첫째(DdlReader `CREATE INDEX`·표 안 `KEY`·`ALTER … ADD (…PK…)`) — 번들 68 1-68 완료. 그 뒤 `DdlCorpusTest` 시험 쪽 정규식도 걷었다
- ⑱ 「후보로」 textarea 문턱 · typed table 이름만 · 사람 감사 컬럼 · 문자형 감사 시각 · 리뷰 Minor 여섯 — 번들 69(7-18a·7-18b·7-19·7-20a~c·7-21·1-69) 완료. `INDEX_SORT` 의 `NULLS` 단독·접두 길이는 7-18b 가 닫았고, 이번 Minor 는 그 `INDEX_PREFIX` 가 **함수식 `f(10)` 까지** 떼는 새 결함이다
- ⑲ 「후보로」 가운데 이 번들 밖 — 생성기 결과물(마-7·8 · `@DateTimeFormat` · 7-22 · INSERT 의 NULL 허용 수정자) · 코드 검사 규칙(a11y `<form:*>` · ⑭·⑮ 후보) · 개발자 도구 새 탭(해시·Charcode·진법) · fkTypeMismatch 동의어 표(표본 소음 0)
- 번들 67 Minor ② `noPk` 인덱스 셈이 같은 이름 겹침에서 틀릴 수 있음(DDL·JDBC 에선 안 생김) · ⑤ HR 똑같은 비유니크 둘의 이름 순 판정 · ⑦ 함수 인덱스 정규식(번들 68 에서 사라짐) — 기록만, 이번에도 안 함(⑤ 는 「H2 FK 자동 인덱스 제외」 가 그 재료 8 을 없애 같이 사라진다)

**사용자 결정(2026-11-02) — 이 번들 범위 밖, PLAN 에 행으로만**:
- 생성 INSERT 의 NULL 허용 수정자(`LAST_UPDUSR_ID`·`LAST_UPDT_PNTTM`) — **(나) 등록 때도 로그인 ID·DB 현재시각을 넣는다**. 설계 52 [고정](「INSERT 에 NULL 허용 updater 넣기 금지」)·설계 53 §5 를 뒤집는다. 근거: eGov 표본 등록 INSERT 264 중 198(75%)이 둘 다 넣는다 → 새 행 7-23 「대기」(생성기 번들에서 친다, 7-22 와 같이)
- 마-5 공통코드 select(코드 사전) — **안 함**(사용자: 안 끌림). compare ⑳ 「후보로」 에서 내린다고 적는다

탐색 실측(2026-11-02, `e9c3a4c`, 표본 `C:/workspace/toolbox-corpus`, 외부 통신 0, `target/classes` 의 `DdlReader` 를 임시 자바 한 파일로 돌려 셈 — 코드 본문은 안 옮긴다):

| 재료 | 수치 | 뜻 |
|---|---|---|
| HR(H2 JDBC) `hr-lint.json` | 10 건 — dupIndex 9 · fkCycle 1 | dupIndex 9 중 8 이 `*_FK_INDEX_n`(H2 가 FK 제약 `DEPT_LOC_FK` 마다 만드는 `DEPT_LOC_FK_INDEX_9` 꼴 — 이름 = `<FK 이름>_INDEX_<숫자>`, 컬럼 = FK 컬럼, 비유니크)과 손 인덱스·PK 의 겹침. 덮인 쪽 5·덮는 쪽 3 양쪽에 선다. 남는 참은 `JHIST_EMPLOYEE_IX ⊂ JHIST_EMP_ID_ST_DATE_PK` 하나 → **10 → 2 기대** |
| eGov oracle DDL(182 표) PK 제약 | `CONSTRAINT <표>_PK PRIMARY KEY` **181**(이름 있음) · 유니크 0 · 유니크 인덱스 0 | `ddl-lint-findings.txt` egov:oracle dupIndex 22 줄의 detail 「⊂ PK(…)」 가 전부 「⊂ COMTN…_PK(…)」 로 바뀐다. uniqueNullable 은 eGov 에서 **0** — 픽스처로만 잰다 |
| db-samples HR·OE PK 제약 | `hr_create.sql` 7 · `oe_cre.sql` 7 이름 있음(`jhist_emp_id_st_date_pk`), `ALTER TABLE … ADD (CONSTRAINT …)` 꼴 | `alterPk` 가 이름을 `pkName` 에 받아 두고도 `read` 가 `new PrimaryKey(null, …)` 로 버린다 → findings db-samples 1 줄도 「PK(」 → 이름 |
| eGov oracle LOB 둘 이상 표 | **3** — `COMTNSTPLATINFO`(CLOB 2) · `COMTNONLINEMANUAL`(CLOB 2) · `COMTNWIKMNTHNGREPRT`(CLOB 2). CLOB 10 · BLOB 2 전체 | tooManyLobs(상한 1 = SchemaCrawler 기본) egov:oracle 3 기대. 다른 방언도 같은 표 셋(형식 이름만 다름 — mariadb `LONGTEXT`·postgres `TEXT`?) → **실행해야 안다**(PG `text` 는 LOB 로 안 센다 — 아래 §3-2) |
| PG 벤더 골든(`meta/postgres-vendor.json`, `pg-lint.json` 11 건) | `users.email` NULL 허용 + 유니크 인덱스 `ux_users_email` · `orders.memo text` 하나 | `pg-lint.json` **11 → 12**(uniqueNullable 1) · tooManyLobs 0 |
| `alter-*.sql`·`ddl-*.sql` 골든 입력 | `AlterGenTest` = `meta/postgres-vendor.json`(JDBC 골든, PK 이름 있음) + 자바 편집 · `DdlGenTest` = 손 표(`PrimaryKey("SYS_C0012345", …)`) | **DdlReader 를 안 거친다** → PK 이름 읽기가 `alter-*.sql`·`ddl-*.sql` 을 안 바꾼다. `DdlReaderTest.alterAddParenthesizedPrimaryKey` 의 `assertNull(rt.pk().name(), "PK 이름은 안 채운다(alter-*.sql 골든)")` 는 근거가 틀렸다 — 이 번들이 뒤집는다 |
| DdlReader 결과가 흐르는 다른 골든 | `sqltables-diff`(이름만) · `ddl-summary`(표·못 읽은 줄 수) · `gen-hr`(JDBC HR) · `analyze-egov*`(PK 이름 없음) · `DbCorpusBase` 비교(컬럼 수·PK 컬럼) · `CliCorpusTest`(표 수) | PK 이름으로 바뀌는 골든은 `ddl-lint-findings.txt` 23 줄뿐 |
| typed table 실물 | sqlfluff `postgres/create_table.sql` — `CREATE TYPE employee_type AS (name text, salary numeric); CREATE TABLE employees OF employee_type (PRIMARY KEY (name), salary WITH OPTIONS DEFAULT 1000)` · db-samples `oc_cre.sql` — `CREATE TYPE category_typ OID '…' AS OBJECT (… , NOT instantiable MEMBER FUNCTION … ) NOT INSTANTIABLE NOT FINAL` · `leaf_category_typ … UNDER category_typ (product_ref_list …, OVERRIDING MEMBER FUNCTION …)` · `CREATE TABLE categories_tab OF category_typ (category_id PRIMARY KEY) NESTED TABLE … STORE AS …` | 형식 몸 꼴 셋 — PG `AS (`, Oracle `[OID '…'] AS OBJECT (`, Oracle `UNDER 부모 (`. 몸엔 속성 외에 MEMBER·MAP·ORDER·CONSTRUCTOR·`NOT INSTANTIABLE`·`OVERRIDING` 줄 · 표 몸엔 `컬럼 PRIMARY KEY`·`컬럼 WITH OPTIONS …` · 표 꼬리 `NESTED TABLE …`. `AS TABLE OF`·`AS VARRAY(n) OF`·`AS ENUM (…)`·`CREATE TYPE BODY` 는 속성 형식이 아니다 |
| CTAS·LIKE·PARTITION OF 실물 | DDL 표본(egov·db-samples·chinook·ruoyi)엔 같은 입력 안 원본이 있는 CTAS·LIKE·PARTITION OF 가 **없다** | 끌어오기는 픽스처로 잰다. 표본 골든은 typed(`categories_tab` 컬럼 0 → 3)만 움직인다 |
| 기준선 `self-check.txt` | 73 `StructureLint run(3) 복잡도 50` · 84 `DdlReader item(3) 30` · 85 `read(1) 36` · 86 `read(1) 중첩 6` · 93~97 `InsertGen` 다섯 | 넉 줄 **불변** — 새 분기는 전부 도우미·`structural`·`nameOnly` 에 |
| PMD 기준선 짝 | `StructureLint`=AvoidLiteralsInIfCondition · `StructureLintTest`=AvoidDuplicateLiterals · `DdlReader` 여섯 · `DdlCorpusTest` 다섯 · `HrSnapshotCorpusTest` 둘 · `DeliverableRoutesTest` 둘 | **없는 것**: `DdlReaderTest`·`JsCorpusTest` — 새 짝 금지(되풀이 리터럴 ≥ 4 는 상수, `if` 에 리터럴 금지, 비교는 리터럴 앞) |
| 레인(`scripts/verify-fingerprint.sh`) | `core/deliverable` = corpus(LANE_DB_SKIP) · `core/gen` = db + corpus · `src/main/resources/tools`(dev_tools.html) = java(src) + corpus · `scripts/puppeteer`(corpus-js.js) = tools + corpus · `web/JsCorpusTest`·`HrSnapshotCorpusTest`·`core/gen` 시험 = corpus | 지시의 「dev_tools 는 tools 레인」 은 실물과 다르다 — 화면 JS 는 **corpus 레인**(`JsCorpusTest` 가 백엔드본 JS 를 돌린다). push 앞 `--corpus`(StructureLint·dev_tools·DdlReader) → `--db`(DdlReader) |
| `SmokeHtmlUnitTest.JS_OFF` | `dev_tools` 포함 — HtmlUnit 은 dev_tools JS 를 안 돌린다 | 순수 함수 단언은 `corpus-js.js`(puppeteer, 백엔드본 화면) + `JsCorpusTest`(Java 대조)뿐. 4-30 의 RED→GREEN 은 그 길 |
| `JsCorpusTest.hexAndVerdictMatchJava` | `enc[]` = `enc·hex64·dumpLines·roundTrip·badUtf8(첫 자리)·lineEucKr·lineUtf8` · `decodeOff` 비면 `baseline` 안 부름 · `summaryGolden` 은 files·koreanLines·columns 만 | `enc[]` 에 칸을 더해도 `js-summary.json` 은 안 바뀐다. `CorpusFiles.baseline` 은 빈 목록이면 빈 파일을 쓰고 빈 파일 ↔ 빈 목록은 초록 |

구조 줄:
- `StructureLint.keyed(t)`(:288-309) — PK → 유니크(`backs(ks, pkBacked, k)` 컬럼 같음 첫 것) → 인덱스(유니크면 `backs(ks, backed, k)`), `addKeyed` 가 같은 이름(대문자)을 거른다. `dupIndexes`·`noPk`·(새) `uniqueNullable` 이 `keyed` 를 쓴다. `covered(b, a)`(:425-434) 는 그대로
- `Rule` enum 열셋 — 끝에 더한다(`ruleIds` 시험 「넷은 enum 끝」·`DdlCorpusTest.NEW_RULES` EnumSet 넷·`FINDING_GROUPS` 셋). 규칙 수 글 — `README.md:38`·`cli/DocCommands.java:134` 「열세 규칙」 · `PLAN.md:170` 「열세 규칙 — …·FK 순환」 · `StructureLint` javadoc 「열세 규칙(설계 25 아홉 + 설계 51 넷…)」 · 단언 `DeliverableRoutesTest:238` 13 · `HrSnapshotCorpusTest:110` 13 · `StructureLintTest.offRules…` 12 · `goldenOnPostgresVendorSnapshot` 11
- `DdlReader.read`(:155-245) — `createIndex` → `CREATE` 머리 → `nameOnly` → 몸 `item` → `BURIED_CREATE` → `COMMENT ON` → `ALTER … ADD`(`alterPk` 가 `d.pkName` 을 채움). 끝에 `new PrimaryKey(null, List.copyOf(d.pk))`. `item`(:292-375) 제약 줄 가지의 PK 정규식은 인라인 `Pattern.compile`(이름 그룹 없음) · 컬럼 가지 `inlinePk` 는 `upper` 에 `\bPRIMARY\s+KEY\b`(이름 못 받음). `indexColumns(body)`(:427-436) 는 Draft 를 모른다. `nameOnly`(:248-272) 넷 가지(partition·like·typed·ctas) → 이름만 + notes. `likeItem`(:378-382) 은 괄호 안 `LIKE x` 에 notes 만
- `dev_tools.html` — `utf8Invalid`(:1924-1940) 실패하면 `out.push(i); i++`(후속 바이트가 다시 선두로 셈) · `hexToBytes`(:1914-1922) `replace(/0x|\\x|%/gi,'')` 가 글 어디서나 `0x` 를 뗀다 · `encVerdict` 「깨진 문자」 = `TextDecoder` 의 U+FFFD 수 · `encHexRender` 「깨진 자리」 = `utf8Invalid(look, 200)` 중 창 안. `corpus-js.js:80-93` enc 절 · `JsCorpusTest:196-250`

## 사용자 결정(2026-11-02)
- 범위 셋(구조 점검 넷 · DdlReader 넷 · dev_tools Minor 셋) · 범위 밖(생성기 결과물 · 코드 검사 규칙 · 개발자 도구 새 탭) · 머지 위임 있음 · 7-23 (나) 「대기」 · 마-5 안 함
- 설계가 정한 것(사용자가 안 물은 자리 — 사람 결정이 생기면 「대기」 행으로 빼라는 지시대로 묻지 않았고, 기본값이 뚜렷한 것만 정했다): FK 자동 인덱스는 **H2 꼴만**(이름 `<FK 이름>_INDEX_<숫자>` + 컬럼 = FK 컬럼 + 비유니크) 제외하고 `keyed` 한 자리에서 뺀다(dupIndex·noPk 인덱스 셈 둘 다 — `fkNoIndex` 의 `indexed` 는 `t.indexes()` 를 바로 봐서 그대로 「인덱스 있음」) · `backs` 는 이름이 같은 것을 먼저, 그 다음 컬럼 같은 것 · 다-17 은 PK 아닌 유니크(제약·유니크 인덱스, `keyed` 로 합친 것)마다 NULL 허용 컬럼이 하나라도 있으면 하나 · 다-19 는 **LOB 둘 이상**(SchemaCrawler `TooManyLobs` 기본 상한 1, 프로필 키 추가 없음 — 끄려면 `off`), LOB 판정은 jdbcType(BLOB 2004·CLOB 2005·NCLOB 2011·LONGVARBINARY −4) 또는 nativeType 목록(CLOB·NCLOB·BLOB·BFILE·LONG RAW·LONGBLOB·MEDIUMBLOB·TINYBLOB·LONGTEXT·MEDIUMTEXT·IMAGE·NTEXT·BYTEA) — **`TEXT`·`TINYTEXT` 는 안 센다**(PG 의 보통 문자 형식과 겹친다; SchemaCrawler 도 PG text 는 VARCHAR 로 본다) · PK 이름은 표 몸 `CONSTRAINT x PRIMARY KEY`·컬럼 줄 `CONSTRAINT x PRIMARY KEY`·ALTER 셋에서, 이름 없으면 null 그대로 · `INDEX_PREFIX` 는 괄호 앞이 그 표의 컬럼 이름일 때만 뗀다(함수식 `f(10)` 은 글 그대로) · 끌어오기 — LIKE·PARTITION OF 는 컬럼 + PK, CTAS 는 컬럼만(단순 SELECT — `*` 또는 맨 이름·`t.이름`·`AS 별칭` 목록, FROM 표 하나, 그 뒤 WHERE·ORDER BY·GROUP BY 만), typed 는 형식 속성 + 몸의 `PRIMARY KEY`·`NOT NULL`·`UNIQUE`·제약 줄, **끌어오면 notes 를 안 단다**(TODO 가 없다), 못 끌어오면 지금처럼 이름만 + notes · 형식 몸의 MEMBER·MAP·ORDER·CONSTRUCTOR·STATIC·FINAL·INSTANTIABLE·OVERRIDING 줄은 속성이 아니라 조용히 건너뛴다(그 밖 못 읽은 줄은 형식 이름으로 unreadable) · `PARTITION OF` 도 같은 가지라 같이 끌어온다(지시의 셋 + 하나 — 같은 `nameOnly` 한 줄) · `utf8Invalid` 는 실패 앞까지 읽은 바이트(선두 + 맞는 후속)를 한 자리로(WHATWG 디코더의 U+FFFD 하나와 같은 셈) · `hexToBytes` 의 `0x` 는 토막 머리(글 처음·공백·쉼표 뒤)에서만, `\x`·`%` 는 어디서나(16진 글자가 아니라 뜻이 하나) · `js-enc-decode` 기준선은 늘 부른다(빈 파일 하나 새 골든) · 새 화면·순수본·표본·프로필 키 없음 · 설계 41~53 그대로(에이전트 최소 · 검증 인프라 최적화 금지)

실행자는 Opus. 차례 `S0`(이 설계 세션이 했다) → `2-34` → `2-35` → `1-70` → `1-71` → `4-30` → `V-50a` → `R-19` → `V-50` → 마무리. 가지 `work/2026-11-02b`(로컬 닫기 `e9c3a4c`·S0 같이). 번들 하나·PR 하나.

---

## 1. 목표와 범위
- 한 문장: 구조 점검이 H2 흔적·순서 의존 없이 PK 이름까지 담아 지적하고 유니크의 NULL 허용·LOB 둘 이상을 더 짚으며, DDL 읽기가 PK 이름과 typed·CTAS·LIKE 표의 컬럼을 같은 입력에서 끌어오고, 개발자 도구 Hex·인코딩 탭의 셈·입력 정리가 WHATWG 와 같아진다.
- 완료 시 체감: 데모 HR 스냅샷 91 구조 점검의 dupIndex 가 9 → 1(`JHIST_EMPLOYEE_IX ⊂ JHIST_EMP_ID_ST_DATE_PK`)이고 「PK(…)」 대신 제약 이름이 보인다. eGov DDL 을 붙여 넣으면 `COMTNSTPLATINFO` 가 「LOB 컬럼 둘 이상」 으로 선다. DDL 탭에 `CREATE TYPE … ; CREATE TABLE x OF 형식 (…)` 을 넣으면 컬럼이 있는 DTO 가 나온다. 인코딩 판별의 「깨진 문자」 와 Hexdump 「깨진 자리」 가 같은 셈이다(범위만 다르다 — 전체 파일 ↔ 보이는 창). compare ⑳.
- In scope: `core/deliverable/StructureLint.java` · `core/gen/DdlReader.java` · `tools/dev_tools.html`(JS 넷) · `scripts/puppeteer/corpus-js.js`(enc 절) · 시험 `StructureLintTest`·`DdlReaderTest`·`DdlCorpusTest`(NEW_RULES)·`HrSnapshotCorpusTest`(13)·`DeliverableRoutesTest`(13)·`JsCorpusTest` · 글 넷(`README.md:38`·`DocCommands.java:134`·`PLAN.md:170`·`StructureLint` javadoc) · 골든 `deliverable/pg-lint.json`·`corpus/hr-lint.json`·`corpus/ddl-lint.json`·`corpus/ddl-lint-findings.txt`·`corpus/js-enc-decode.txt`(새) · `design/compare-2026-10-28.md` ⑳ · PLAN 분할표·번들 표 · PROGRESS.
- Out of scope: 생성기 결과물 전부(마-5·마-7·8·`@DateTimeFormat`·7-22·7-23) · 코드 검사 규칙(a11y `<form:*>`·⑭·⑮) · 개발자 도구 새 탭(해시·Charcode·진법) · fkTypeMismatch 동의어 표 · 프로필 키 추가(`deliverable.lint.maxLobs` 같은 것) · `cp949Invalid` 셈 · CTAS 의 조인·식·서브쿼리 컬럼 · typed 몸 `WITH OPTIONS DEFAULT` 값 · `core/meta` 레코드 · 새 화면·순수본·표본.

## 2. 현재 구조 요약
「맥락」 의 실측과 구조 줄이 요약이다. 덧붙이는 함정:
- `StructureLint.run(3)` 복잡도 50 — **분기를 더하지 않는다**. 새 규칙 둘은 `structural` 에 `if (on(…))` 둘(지금 4 → 6, 기준선 밖)
- `keyed` 가 `noPk`·`dupIndexes` 공용 — FK 자동 인덱스를 여기서 빼면 `noPk` 의 「인덱스 n」 도 준다(의도 — javadoc 에 적는다). HR 표는 전부 PK 가 있어 `hr-lint` noPk 는 그대로 0
- `DdlReader.read(1)` 복잡도 36·중첩 6 — `createIndex(stmt, tables)` 호출을 `otherCreate(stmt, tables, types)` 하나로 바꿔 그 안에서 `createIndex || createType` 를 돈다(`||` 를 `read` 에 두면 복잡도 +1). `item(3)` 30 — PK 이름은 `pkNameOf(String found, String current)` 도우미 한 줄(삼항은 도우미 안). `nameOnly` 는 기준선 밖(지금 약 7) — 끌어오기는 `pull(…)` 도우미로 ≤ 15
- `Draft.pkName` — `alterPk` 가 채우는데(`""` = 이름 없음) `read` 가 버린다. `prunePkIndexes` 는 `x.name().equalsIgnoreCase(d.pkName)` — null 이면 false 라 안전. 이름이 몸에서 오면 Oracle 「`CREATE UNIQUE INDEX pk_x` + 같은 이름 PK」 도 pruning 에 든다(의도)
- `CREATE TYPE … AS TABLE OF`·`VARRAY`·`AS ENUM (` 은 속성 형식이 아니다 — `CREATE_TYPE` 정규식이 `AS\s*\(`·`AS\s+OBJECT\s*\(`·`UNDER\s+이름\s*\(` 만 받아 나머지는 지금처럼 조용히 지나간다(statements 가 CREATE TABLE 도 ALTER 도 아니면 아무것도 안 한다)
- typed 표의 몸 `category_id PRIMARY KEY` 는 `item` 의 컬럼 가지로 가면 타입이 비어 unreadable 이 된다 → typed 몸은 `typedBody` 가 따로 읽는다(제약 줄은 `item` 에, `컬럼 [WITH OPTIONS] PRIMARY KEY|NOT NULL|UNIQUE|DEFAULT …` 는 표식만)
- `TYPE_END` 에 `WITH OPTIONS` 가 없어도 typed 몸은 `item` 으로 안 가니 상관없다
- `hexToBytes` 의 오류 글 「n번째 글자」 는 정리한 글 기준(지금 그대로)
- `utf8Invalid(bytes, 1)[0]`(첫 자리 — `JsCorpusTest` 가 Java 엄격 디코더 멈춘 자리와 대조)는 고쳐도 같다 — 첫 실패 위치는 변하지 않고 그 뒤 건너뛰는 폭만 바뀐다
- `-Dgolden.update=true` 는 행마다 해당 시험만(`-Dtest=`) 한 번, diff 를 읽고 이력에

## 3. 설계 결정 — 전부 [고정]

### 2-34 — `StructureLint` H2 FK 자동 인덱스 제외 + `backs` 이름 먼저(§3-1)
- `keyed(Table t)`:
  - 인덱스 목록을 먼저 거른다 — `fkAuto(t, ix)` 참이면 아예 안 본다: `!ix.unique()` 이고 `t.fks()` 중 이름이 비지 않은 FK 하나에 대해 `up(ix.name())` 이 `up(fk.name()) + "_INDEX_"` 로 시작하고 그 뒤가 숫자만이며 `upAll(ix.columns()).equals(upAll(fk.columns()))`
  - 받침 두 단계 — 유니크 루프(PK 받침)·인덱스 루프(PK·유니크 받침) 둘 다: **1단계** 이름이 같은 것(`up(name)` 같음 + 컬럼 같음)을 먼저 받침으로 표시, **2단계** 남은 것 중 컬럼 같은 것을 아직 안 받친 제약에. 도우미 `backing(List<Keyed> ks, boolean[] backed, List<Keyed> cands)` → 받침이 안 된 후보 목록을 돌려주고, 호출자가 그것만 `addKeyed`. 기존 `backs` 는 2단계 안에서 그대로 쓴다
  - javadoc: 「H2 가 FK 마다 만드는 `<FK 이름>_INDEX_<n>` 은 FK 의 자리일 뿐이라 안 센다(dupIndex·noPk) — `fkNoIndex` 는 `indexed` 가 `t.indexes()` 를 바로 보니 「있음」」
- `StructureLintTest` 새 시험 둘(단언 명시):
  - `h2FkAutoIndexIsNotAKey` — `F1`: FK `FK_F1_P`(cols [P_ID]) · 인덱스 `FK_F1_P_INDEX_3`(P_ID) · `IX_F1_P`(P_ID) → dupIndex **없음**(양쪽 다 안 짚음) · `fkNoIndex` 없음 · `F2`: 같은 꼴인데 자동 인덱스 컬럼이 `[Q_ID]`(FK 와 다름) + `IX_F2_Q`(Q_ID) → `IX_F2_Q ⊂ FK_F2_P_INDEX_3`(이름 순 — 자동이 아니다) · `F3`: PK 없음 + FK 자동 인덱스만 → `noPk` 없음(`keyed` 가 비어 noPk 아님)·`noIndex` 아님(`t.indexes()` 는 있다 — 지금 `noIndex` 판정은 `t.indexes().isEmpty()` 라 그대로 「아니다」)
  - `backingPrefersSameName` — `B1`: 유니크 `UQ_B(B)` · 인덱스 `[UX_OTHER unique (B), UQ_B unique (B)]` → `UX_OTHER ⊂ UQ_B`(지금은 `UX_OTHER` 가 받침이 되고 `UQ_B` 인덱스는 같은 이름 거름에 걸려 지적 0) · 순서를 바꾼 `B2` 도 같은 결과(순서 무관) · `D7` 꼴(이름 다른 받침 `CONSTRAINT_INDEX_9`)은 기존 시험 그대로 통과
- 골든: `hr-lint.json` 10 → 2(`JHIST_EMPLOYEE_IX ⊂ JHIST_EMP_ID_ST_DATE_PK` · fkCycle) — V-50a 에서 갱신(`HrSnapshotCorpusTest`, corpus 태그) · `pg-lint.json`·`ddl-lint*` 불변 기대(egov 인덱스는 비유니크, 자동 FK 인덱스 없음) · `self-check.txt` 불변

### 2-35 — 규칙 둘 `uniqueNullable`·`tooManyLobs` + 글(§3-2)
- `Rule` 끝에:
```java
UNIQUE_NULLABLE("uniqueNullable", "유니크에 NULL 허용 컬럼", "유니크 제약·인덱스에 NULL 허용 컬럼이 있다 — NULL 중복을 DB 마다 다르게 다뤄(Oracle·PG 는 여러 행, MSSQL 은 한 행) 유일성이 뜻대로 안 선다"),
TOO_MANY_LOBS("tooManyLobs", "LOB 컬럼 둘 이상", "CLOB·BLOB 류 컬럼이 둘 이상 — 행이 커져 전체 조회·백업이 느리다(SchemaCrawler 기본 상한 1). 분리 표가 보통이다");
```
- `structural` 에 `if (on(opt, Rule.UNIQUE_NULLABLE)) { uniqueNullable(t, s, out); }`·`if (on(opt, Rule.TOO_MANY_LOBS)) { lobs(t, s, out); }`(`cycles` 앞)
- `uniqueNullable(t, s, out)` — `keyed(t)` 중 `k.unique() && !k.pk()` 마다 NULL 허용 컬럼(`column(t, c)` 가 있고 `nullable()`) 목록이 비지 않으면 `Finding(UNIQUE_NULLABLE, s, t.name(), String.join(",", nullable), "유니크 " + k.name() + "(" + String.join(",", k.cols()) + ")")`. 받침 인덱스는 `keyed` 가 이미 뺐으니 제약당 하나
- `lobs(t, s, out)` — `isLob(c)` = `c.jdbcType()` 이 `LOB_JDBC`(2004·2005·2011·−4) 에 있거나 `norm(c.nativeType())` 이 `LOB_TYPES` 에 있음. 둘 이상이면 `Finding(TOO_MANY_LOBS, s, t.name(), null, "LOB " + n + ": " + String.join("·", names))`. 상수 `MAX_LOBS = 1`
- 글: `README.md:38`·`DocCommands.java:134` 「열세 규칙」 → 「열다섯 규칙」 · `PLAN.md:170` 「열세 규칙 — …·FK 순환」 → 「열다섯 규칙 — …·FK 순환·유니크의 NULL 허용 컬럼·LOB 컬럼 둘 이상」 + 끝 칸 「번들 67(2-32) · 번들 70(2-35)」 · `StructureLint` javadoc 「열다섯 규칙(설계 25 아홉 + 설계 51 넷 + 설계 54 둘 — SchemaCrawler 의 NullColumnsInIndex·TooManyLobs)」
- 시험·단언: `StructureLintTest` 새 `uniqueNullableAndLobs` — `U1`: 컬럼 `A NOT NULL`·`B NULL`·`C NULL`, 유니크 `UQ_AB(A,B)`·`UQ_A(A)`·유니크 인덱스 `UX_C(C)`·PK `A` → `U1.B 유니크 UQ_AB(A,B)` · `U1.C 유니크 UX_C(C)`(UQ_A 는 NULL 허용 없음, PK 는 아님) · `L1`: `CLOB`·`BLOB`·`VARCHAR2` → `L1 LOB 2: X·Y` · `L2`: `TEXT`·`TEXT`(jdbcType null) → 없음 · `L3`: jdbcType 2005 둘(nativeType 모름) → 있음 · `L4`: `CLOB` 하나 → 없음 · `ruleIds` 에 `List.of(Rule.UNIQUE_NULLABLE, Rule.TOO_MANY_LOBS) == byId 둘` 「설계 54 — 둘은 enum 끝」 · `offRules…` 12 → **14** · `goldenOnPostgresVendorSnapshot` 11 → **12** + `assertEquals(1, r.counts().get(Rule.UNIQUE_NULLABLE))`(users.email) + `assertEquals(0, …TOO_MANY_LOBS)` · `DeliverableRoutesTest:238` 13 → 15 · `HrSnapshotCorpusTest:110` 13 → 15 · `DdlCorpusTest.NEW_RULES` 에 둘 추가(counts 글에 `· uniqueNullable n · tooManyLobs n` 이 붙어 `ddl-lint.json` 15 그룹 전부 바뀐다 — V-50a)
- 골든: `-Dgolden.update=true -Dtest='StructureLintTest'` 한 번 → `pg-lint.json` +1(`users.email ux_users_email`) diff 를 읽고 이력에. `ddl-lint*`·`hr-lint` 는 V-50a

### 1-70 — `DdlReader` PK 제약 이름 + `INDEX_PREFIX` 컬럼일 때만(§3-3)
- 상수 `PK_LINE = (?:CONSTRAINT\s+(\S+)\s+)?\bPRIMARY\s+KEY\s*(?:CLUSTERED\s+|NONCLUSTERED\s+)?\(([^)]*)\)`(CASE_INSENSITIVE) — `item` 제약 줄 가지의 인라인 `Pattern.compile` 을 이것으로, 컬럼은 `group(2)`, `d.pkName = pkNameOf(pk.group(1), d.pkName)`
- 상수 `INLINE_PK = (?:CONSTRAINT\s+(\S+)\s+)?\bPRIMARY\s+KEY\b`(CASE_INSENSITIVE) — 컬럼 가지에서 `upper` 대신 `rest` 에 맞춘다(이름 대소문자 보존). `if (inlinePk) { d.pk.add(name); d.pkName = pkNameOf(ip.group(1), d.pkName); }`
- `pkNameOf(String found, String current)` = `found == null ? current : unquote(found)` · `read` 끝 `new PrimaryKey(pkNameOrNull(d), List.copyOf(d.pk))`, `pkNameOrNull(d)` = `d.pkName == null || d.pkName.isEmpty() ? null : d.pkName`(둘 다 도우미 — `item`·`read` 복잡도 불변)
- `indexColumns(String body, Draft d)` — `INDEX_PREFIX` 는 괄호 앞 글(`unquote`)이 `d.cols` 의 컬럼 이름(대소문자 무시)일 때만 뗀다. 호출 둘(`createIndex`·`inlineIndex`) 에 `d` 를 넘긴다. 표 안 `KEY` 가 컬럼 선언보다 앞에 오면 접두를 못 떼는 한계는 javadoc 한 줄(MySQL 덤프는 컬럼 뒤에 KEY 가 온다)
- `DdlReaderTest`:
  - `alterAddParenthesizedPrimaryKey` 의 `assertNull(rt.pk().name(), "PK 이름은 안 채운다(alter-*.sql 골든)")` → `assertEquals("r_pk", rt.pk().name(), "1-70 — ALTER 의 PK 이름")` + `assertEquals("c_pk", ct.pk().name())` (RED)
  - 새 `pkNameFromBodyColumnAndAlter` — `CREATE TABLE a (id INT, CONSTRAINT pk_a PRIMARY KEY (id)); CREATE TABLE b (id INT CONSTRAINT pk_b PRIMARY KEY); CREATE TABLE c (id INT PRIMARY KEY); CREATE TABLE d (id INT, PRIMARY KEY (id)); CREATE TABLE e ([id] INT, CONSTRAINT [PK_e] PRIMARY KEY CLUSTERED ([id]))` → 이름 `pk_a`·`pk_b`·null·null·`PK_e`, 컬럼 전부 `[id]`, unreadable 0
  - `matchesSnapshotTakenFromTheSameDdl` 에 한 줄 — DDL 쪽 PK 이름이 있으면 JDBC 골든의 PK 이름과 대소문자 무시로 같다(mssql·oracle 샘플은 `CONSTRAINT pk_*`, mariadb·postgres 는 이름 없음 → 건너뜀)
  - 새 `prefixLengthOnlyForColumns` — `CREATE TABLE t (a INT, b VARCHAR(30)); CREATE INDEX i6 ON t (f(10)); CREATE INDEX i7 ON t (b(10)); CREATE INDEX i8 ON t (LEFT(b, 10))` → `i6:[f(10)]`·`i7:[b]`·`i8:[LEFT(b, 10)]`
- 골든: `ddl-lint-findings.txt` 23 줄 detail 「⊂ PK(」 → 「⊂ <제약 이름>(」(egov 22 `COMTN…_PK` · db-samples 1 `jhist_emp_id_st_date_pk`) — V-50a · `gen/*`·`ddl-*.sql`·`alter-*.sql`·`sqltables-diff`·`ddl-summary`·`gen-hr`·`analyze-*` 불변 · `self-check.txt` 불변

### 1-71 — typed·CTAS·LIKE(+PARTITION OF) 컬럼 끌어오기 + typed 몸(§3-4)
- `read` — `Map<String, List<Column>> types = new LinkedHashMap<>()` 지역, `if (otherCreate(stmt, tables, types, unreadable)) continue;` 로 `createIndex` 호출을 감싼다. `otherCreate` = `createIndex(stmt, tables) || createType(stmt, types, unreadable)`
- `CREATE_TYPE = ^CREATE\s+(?:OR\s+REPLACE\s+)?(?:(?:NON)?EDITIONABLE\s+)?TYPE\s+(?!BODY\b)([^\s(]+)\s+(?:OID\s+'[^']*'\s+)?(?:AS\s+OBJECT|AS|UNDER\s+([^\s(]+))\s*\(`(CASE_INSENSITIVE) — g1 이름, g2 부모(UNDER). 몸 = `matching` 으로 닫는 괄호까지. 항목: `TYPE_MEMBER = ^(?:NOT\s+)?(?:INSTANTIABLE|FINAL|OVERRIDING|MAP|ORDER|MEMBER|STATIC|CONSTRUCTOR)\b`(CASE_INSENSITIVE) 는 건너뛰고, 나머지는 임시 `Draft(형식 이름)` 에 `item(tmp, it, unreadable)`(못 읽은 줄은 `Unreadable(형식 이름, 줄)`). 결과 = (부모 속성 복사 →) 자기 속성, `types.put(UPPER(이름), cols)`. 머리가 안 맞으면(`AS TABLE OF`·`VARRAY`·`AS ENUM (`·`TYPE BODY`) false
- `nameOnly(stmt, m, tables, types, unreadable)` — 가지 판정 뒤 `pull(d, key, after, tables, types, unreadable)`:
  - `partition`: `PARTITION OF 부모` 의 부모 = `tables.get(UPPER(last(nameParts)))` → 컬럼 + PK(`d.pk`·`d.pkName`) 복사
  - `like`: `LIKE 원본` 같은 꼴 → 컬럼 + PK 복사
  - `typed`: `OF 형식` → `types.get(UPPER(형식))` 의 컬럼 복사(ordinal 다시) · 그 뒤 첫 글자가 `(` 면 `typedBody(d, 몸, unreadable)`: 항목이 `CONSTRAINT_LINE` 이면 `item(d, …)`(PK·UNIQUE·FK·CHECK) · 아니면 `TYPED_OPT = ^([^\s(]+)(?:\s+WITH\s+OPTIONS)?\s*(.*)$` — 이름 + 나머지: 나머지에 `PRIMARY KEY` 면 `d.pk.add`, `NOT NULL` 이면 그 컬럼을 nullable false 로 바꿔 끼움(컬럼이 있을 때), `UNIQUE` 면 `d.uqs.add(new UniqueKey(null, List.of(이름)))`, `DEFAULT …` 는 무시 · 그 밖 글(`PRIMARY KEY`·`NOT NULL`·`UNIQUE`·`DEFAULT`·`NULL`·`CONSTRAINT`·`CHECK`·`REFERENCES` 낱말이 하나도 없는 나머지)은 unreadable. 형식이 없어도 몸은 읽는다(PK 만 있는 표 — `GenModel` 은 컬럼 0 이라 필드 없이 모델을 만들고 `InsertGen` 은 빈 SQL + 경고 그대로)
  - `ctas`: `ctasColumns(after, tables)` — 따옴표 글을 비운 뒤 깊이 0 의 `SELECT … FROM 표 [WHERE|ORDER|GROUP|HAVING|LIMIT|FETCH|OFFSET|)|끝]` 만: 목록이 `*` 면 표의 컬럼 전부 · 아니면 항목마다 `(?:[^\s.]+\.)?([^\s]+)(?:\s+(?:AS\s+)?([^\s]+))?` — 원 컬럼(이름 그대로 찾기), 별칭 있으면 이름만 바꾼다 · `(`·연산자·따옴표가 든 항목 또는 FROM 뒤에 조인·쉼표·서브쿼리 → null. 머리의 컬럼 이름 목록 `CREATE TABLE T (C1, C2) AS SELECT A, B FROM X`(`ctasShapes` 일곱째 꼴)은 `rest` 의 이름 뒤 괄호 목록이 **전부 맨 이름**이면 그 이름을 순서대로 씌운다(수가 다르면 null). 컬럼만 복사(PK 없음 — CTAS 는 제약을 안 옮긴다, NOT NULL 은 복사한 그대로)
  - 끌어왔으면(`src != null`) notes 를 안 단다. 못 끌어오면 지금처럼 `NAME_ONLY` 글
- `likeItem(d, item, tables)` — 괄호 안 `LIKE x [INCLUDING …]` 도 원본이 있으면 그 자리에 컬럼 + PK 를 끼우고 notes 없음(없으면 지금처럼 notes). `item` 시그니처에 `tables` 추가(호출 셋 — `read`·`createType`·`typedBody`; `createType` 은 `Map.of()`)
- `DdlReaderTest`(상수 `TYPED = "typed"` 추가):
  - `typedTable` 에 단언 추가 — `employees` PK `[name]`(형식 없어도 몸 PK 는 읽는다) · 컬럼 0 · notes typed 그대로
  - 새 `typedTableFromType` — PG: `CREATE TYPE employee_type AS (name text, salary numeric); CREATE TABLE employees OF employee_type (name WITH OPTIONS NOT NULL, PRIMARY KEY (name), salary WITH OPTIONS DEFAULT 1000);` → 컬럼 `[name, salary]`·name nullable false·PK `[name]`·notes 없음(`assertNull(r.notes().get("EMPLOYEES"))`)·unreadable 0 · Oracle: `CREATE TYPE cat_typ OID 'X' AS OBJECT (id NUMBER(2), nm VARCHAR2(50), NOT INSTANTIABLE MEMBER FUNCTION f RETURN VARCHAR2) NOT INSTANTIABLE NOT FINAL; CREATE TYPE leaf_typ UNDER cat_typ (extra VARCHAR2(10), OVERRIDING MEMBER FUNCTION f RETURN VARCHAR2); CREATE TABLE cats OF leaf_typ (id PRIMARY KEY) NESTED TABLE x STORE AS y;` → 컬럼 `[id, nm, extra]`·PK `[id]`·`id` nullable false·unreadable 0·notes 없음 · 음성: `CREATE TYPE mood AS ENUM ('a','b'); CREATE TABLE t OF mood;` → 컬럼 0 + notes typed · `CREATE TYPE BODY x AS …; CREATE TABLE u OF x` → 컬럼 0 + notes
  - 새 `likeFromSource` — `CREATE TABLE X (A INT NOT NULL, B VARCHAR(10) DEFAULT 'x', CONSTRAINT pk_x PRIMARY KEY (A)); CREATE TABLE Y LIKE X; CREATE TABLE Z (LIKE X INCLUDING ALL, C INT);` → Y 컬럼 2·PK `[A]`·PK 이름 `pk_x`·B default `'x'`·notes 없음 · Z 컬럼 `[A, B, C]`·PK `[A]`·notes 없음 · 기존 `likeHead`·`likeInParens`(원본 없음) 그대로
  - 새 `ctasFromSource` — `CREATE TABLE T1 (A INT NOT NULL, B VARCHAR(10), PRIMARY KEY (A)); CREATE TABLE T2 AS SELECT * FROM T1; CREATE TABLE T3 AS SELECT B, t1.A AS A2 FROM T1 t1 WHERE A > 0; CREATE TABLE T4 (C1, C2) AS SELECT A, B FROM T1; CREATE TABLE T5 AS SELECT A, COUNT(*) FROM T1 GROUP BY A; CREATE TABLE T6 AS SELECT A FROM T1 JOIN T9 ON 1 = 1` → T2 `[A, B]`·PK 없음·notes 없음 · T3 `[B, A2]`(A2 nativeType INT, nullable false)·notes 없음 · T4 `[C1, C2]` · T5 컬럼 0 + notes ctas · T6 컬럼 0 + notes ctas · 기존 `ctasIsTableWithoutColumns`·`ctasShapes`(원본 없음) 그대로
  - 새 `partitionFromParent` — `CREATE TABLE P (ID INT, DT DATE, PRIMARY KEY (ID, DT)) PARTITION BY RANGE (DT); CREATE TABLE P1 PARTITION OF P FOR VALUES FROM (1) TO (10);` → P1 컬럼 2·PK 2·notes 없음 · 기존 `partitionOf`(부모 없음) 그대로
- 골든: 표본 중 원본이 같은 입력에 있는 것은 db-samples `oc_cre.sql` 의 `categories_tab`(형식 `category_typ`, 속성 3 — `category_name`·`category_description`·`category_id`, PK `category_id`)뿐 → `ddl-lint.json` db-samples:oracle 표 25 그대로(noPk 0 그대로 — 인덱스 없음) · `ddl-summary.json` 테이블 36 그대로, 못 읽은 줄 3 → **3 기대**(`oe_cre.sql`·`oc_cre.sql` 형식 몸이 다 속성·MEMBER 줄 — 실행해야 안다, 바뀌면 R-19 가 판정) · `ddl-unreadable.txt`·`sqltables-diff`·`gen/*`·`insert/*` 불변 · `InsertGenTest.tableWithoutColumnsIsWarning`(`t2 OF typ2`, 형식 없음) 그대로 · `self-check.txt` 불변(`read`·`item` 분기 수 그대로)

### 4-30 — 개발자 도구 Hex·인코딩 Minor 셋 + corpus 대조(§3-5)
- `dev_tools.html`:
  - `utf8Invalid` — 후속 검사 루프를 「읽은 바이트 수 `k`」 로 바꾼다: 선두 뒤 맞는 후속마다 `k++`, 틀리면 멈춤. `if (ok) i += need + 1; else { out.push(i); i += k; }`(`k ≥ 1` — 선두가 틀리면 1). javadoc 「실패 앞까지 읽은 바이트를 한 자리로 — WHATWG 디코더가 U+FFFD 하나를 내는 묶음과 같다. 끝에서 잘린 열도 한 자리」
  - `hexToBytes` — `var s=String(text).replace(/\\x|%/gi,'').replace(/(^|[\s,])0x/gi,'$1').replace(/[\s,]+/g,'');` javadoc 「`0x` 는 토막 머리에서만(글 중간 `0x` 는 그대로 두어 `x` 로 걸린다), `\x`·`%` 는 어디서나」. 자리 표시 글(placeholder)은 그대로
  - 「깨진 문자」 글(`encVerdict`)·「깨진 자리」 글(`encHexRender`)은 그대로 — 셈이 같아지고 범위(전체 파일 ↔ 보이는 창, 상한 200)만 다르다. `encHexRender` 의 `info` 글에 「(보이는 창 안)」 을 붙인다: `'깨진 자리 '+bad.length+…+'(보이는 창 안, 처음 0x…)'`
- `scripts/puppeteer/corpus-js.js` enc 절:
  - CSV 마다 `badCount: utf8Invalid(bytes).length` · `fffd: (new TextDecoder('utf-8').decode(bytes).match(/\uFFFD/g)||[]).length` 를 더한다(상한 없이 전부 — 앞 4,096바이트)
  - 새 `out.encUnit` — 고정 벡터를 화면 함수에: `[[0xE2,0x82], [0xE0,0x80,0x80], [0xF0,0x90,0x80,0x41], [0xED,0xA0,0x80], [0xC0,0x80], [0xEA,0xB0,0x80], [0xEF,0xBB,0xBF,0x41], [0x80,0x80], [0xF4,0x90,0x80,0x80]]` → `{hex: hexOf(b), badCount, fffd, at: utf8Invalid(b)}`
  - 새 `out.hexCases` — `['0x41 0x42', '41,42', '%EA%B0%80', '\\xEA\\xB0', 'ef bb bf', '0X41', 'A00x41', '0x410x42', 'EA B', 'EA B0 8G']` → `{in, out: hexOf(hexToBytes(in))}` 또는 `{in, error: e.message}`
- `JsCorpusTest`:
  - `hexMismatches` 에 `if (n.path("badCount").asInt(-1) != n.path("fffd").asInt(-2)) bad.add(f + " 깨진 자리 " + badCount + " ≠ 깨진 문자 " + fffd)`(A — 칸이 없으면 −1 ≠ −2 라 RED)
  - 새 `encUnitVectorsMatchWhatwg` — `s.get("encUnit")` 아홉: `badCount == fffd` 전부 + 기대 `[1, 3, 1, 3, 2, 0, 0, 2, 4]` 와 `at` 첫 자리(`[0]`·`[0,1,2]`·`[0]`·`[0,1,2]`·`[0,1]`·`[]`·`[]`·`[0,1]`·`[0,1,2,3]`) · `s.get("hexCases")` 열: `41 42`·`41 42`·`EA B0 80`·`EA B0`·`EF BB BF`·`41` · `A00x41` 은 error 에 `"x"` · `0x410x42` 는 error 에 `"x"` · `EA B` 는 error 에 「홀수」 · `EA B0 8G` 는 error 에 `"G"`. `assertEquals` 로 명시
  - `decodeOff` — `if (!decodeOff.isEmpty())` 를 없애고 늘 `CorpusFiles.baseline("js-enc-decode", decodeOff, enc.size())`. 첫 실행 RED(「baseline 이 없다」) → `-Dgolden.update=true -Dtest=JsCorpusTest` 한 번 → 빈 `golden/corpus/js-enc-decode.txt`(0 바이트) → GREEN. javadoc 「B 는 빈 목록이어도 기준선을 둔다 — 새로 깨지면 빨강」
- TDD 예외(화면 JS)지만 RED→GREEN 자리는 `JsCorpusTest`(node + 표본, `bash scripts/mvn.sh -q test -Djunit.timeout=10m -Dtest=JsCorpusTest`) — 자바 단언을 먼저 쓰고(RED: `badCount` 없음·`encUnit` null·baseline 없음) JS 를 고친다. HtmlUnit 은 `JS_OFF` 라 못 쓴다
- 골든: `js-enc-decode.txt` 새(빈) · `js-summary.json` 불변(`files`·`koreanLines`·`columns` 만) · `csv-*` 불변

### 측정·판정(§3-6)
- V-50a 재채점 — `bash scripts/mvn.sh -q test -Djunit.timeout=10m -Dtest='DdlCorpusTest,HrSnapshotCorpusTest,SqlFixtureCorpusTest,AnalyzeCorpusTest,CliCorpusTest,JsCorpusTest,GeneratorCorpusTest'` → A 0 · B 갱신은 `-Dgolden.update=true` 로 **`ddl-lint.json`(15 그룹 글 — 새 규칙 둘 칸) · `ddl-lint-findings.txt`(PK 이름 23 줄 + egov:oracle tooManyLobs 3 줄 + uniqueNullable 0) · `hr-lint.json`(10 → 2) · `js-enc-decode.txt`(새, 빈)** 만 기대 → diff 를 읽고 이력에. `ddl-summary.json`·`ddl-unreadable.txt`·`sqltables-diff.txt`(30)·`sqlfixture.json`·`gen-hr.json`·`analyze-*`·`cli-*`·`js-summary.json` 불변 기대 — 바뀌면 R-19 가 판정(「드러난 것」), 이름 셈 밖이면 중단 조건
- R-19 손 판정 — ① `ddl-lint-findings.txt` 바뀐·새 줄 전부(PK 이름 23 · tooManyLobs 3 · 그 밖) 참·참(규칙 정의)·오탐 ② `hr-lint.json` 사라진 8 — 전부 `*_FK_INDEX_n` 인가 ③ `pg-lint.json` +1(`users.email`) ④ db-samples `categories_tab` 컬럼 3·PK 1(`DdlReader` 로 `oc_cre.sql` 한 번 — 이름·수만) ⑤ `ddl-summary`·`ddl-unreadable` 변화가 있으면 그 줄. 표 하나(항목 · 표본 · 판정 · 근거) + 분류 합. 결함 둘 이하는 고침(음성 먼저), 넘으면 새 행. 코드 본문은 안 옮긴다
- V-50 — `design/compare-2026-10-28.md` 끝에 `## ⑳ 재측정 — 번들 70(날짜)`: 표 「구조 점검 전후(⑰·⑱ 대비 — hr-lint 10 → 2 · pg-lint 11 → 12 · ddl-lint 그룹별 uniqueNullable·tooManyLobs · findings 24 → N)」 · DdlReader(PK 이름 23 · typed `categories_tab` 컬럼 0 → 3 · sqltables-diff 30) · Hex(`badCount = fffd` 100/100 · encUnit 9 · hexCases 10 · `js-enc-decode` 0) · 정적 분석 rc · 벽시계 둘 · 「드러난 것」 · 「후보로」(생성기 — 7-22·7-23(사용자 (나)) · 마-7·8 · `@DateTimeFormat` · **마-5 는 사용자 안 함(2026-11-02) — 후보에서 내림** · a11y `<form:*>` · 개발자 도구 새 탭 · fkTypeMismatch 동의어 표 · CTAS 의 조인·식 컬럼 · typed 몸 `WITH OPTIONS DEFAULT`). 그 앞에 `bash scripts/mvn.sh -DskipTests verify`(PMD·SpotBugs·ErrorProne) rc 0 을 이력에

## 4. 실행 스텝
### S0 — 설계 문서·행·번들 표 (**이 설계 세션이 한다**, Opus 는 건너뛴다)
- 대상: `design/54-bundle70.md`(A 절) · `PLAN.md` 「번들」 표 「번들 70」 행 + 분할표(`2-34`·`2-35`·`1-70`·`1-71`·`4-30`·`V-50a`·`R-19`·`V-50` 새 행 + `7-23` 「대기」 행, B 절) · `PROGRESS.md` 「열려 있는 것」 머리에 「번들 70(설계 54, …) 설계 끝 — 가지 `work/2026-11-02b`, 다음은 「번들 해」」 + 이력 한 줄(직전 줄 「번들 69 머지」 해시 `(이 커밋)` → `e9c3a4c`).
- 검증: `bash scripts/doc-lint.sh design/54-bundle70.md` · `bash scripts/verify.sh`(docs 레인) · `git commit -m "docs: S0 설계 54 — 번들 70"`. push 안 함.

### 2-34 — `StructureLint` H2 FK 자동 인덱스 제외 + `backs` 이름 먼저
- 대상: `core/deliverable/StructureLint.java`(`keyed`·`fkAuto`·`backing`) · `core/deliverable/StructureLintTest.java`(§3-1).
- 변경(TDD): ① 시험 둘 `h2FkAutoIndexIsNotAKey`·`backingPrefersSameName` → RED ② `keyed` → GREEN.
- 검증: `bash scripts/mvn.sh -q test -Dtest='StructureLintTest,DeliverableRoutesTest,SelfCheckTest'` 초록 · `pg-lint.json` diff 0 · `self-check.txt` diff 0 · `hr-lint.json` 은 V-50a.
- 의존: S0.

### 2-35 — 규칙 둘 `uniqueNullable`·`tooManyLobs` + 글 넷 + 단언 수
- 대상: `core/deliverable/StructureLint.java`(enum 둘·`structural`·`uniqueNullable`·`lobs`·`isLob`·javadoc) · `StructureLintTest.java`(`uniqueNullableAndLobs`·`ruleIds`·`offRules…` 14·`goldenOnPostgresVendorSnapshot` 12) · `web/DeliverableRoutesTest.java`(15) · `web/HrSnapshotCorpusTest.java`(15) · `core/gen/DdlCorpusTest.java`(`NEW_RULES` 여섯) · `README.md:38` · `cli/DocCommands.java:134` · `PLAN.md:170` · 골든 `deliverable/pg-lint.json`(§3-2).
- 변경(TDD): ① `uniqueNullableAndLobs` + 단언 수 → RED(컴파일) ② enum·규칙 → GREEN ③ `-Dgolden.update=true -Dtest='StructureLintTest'` 한 번 → `pg-lint.json` diff 가 `users.email` 한 건뿐인지 ④ 글 넷(TDD 예외 — 문서·설명).
- 검증: `bash scripts/mvn.sh -q test -Dtest='StructureLintTest,DeliverableRoutesTest,SmokeHtmlUnitTest,ToolsFolderTest,SelfCheckTest'` 초록 · `bash scripts/doc-lint.sh README.md PLAN.md` · `self-check.txt` diff 0 · PMD 새 짝 0(`StructureLint` 의 `if` 리터럴은 기준선 짝이 있다 — 그래도 `MAX_LOBS` 상수로).
- 의존: 2-34.

### 1-70 — `DdlReader` PK 제약 이름 + `INDEX_PREFIX` 컬럼일 때만
- 대상: `core/gen/DdlReader.java`(`PK_LINE`·`INLINE_PK`·`pkNameOf`·`pkNameOrNull`·`indexColumns(body, d)`) · `core/gen/DdlReaderTest.java`(§3-3).
- 변경(TDD): ① `alterAddParenthesizedPrimaryKey` 단언 뒤집기 + `pkNameFromBodyColumnAndAlter` + `prefixLengthOnlyForColumns` + 샘플 비교 한 줄 → RED ② `DdlReader` → GREEN.
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,DdlGenTest,AlterGenTest,InsertGenTest,GenModelTest,SelfCheckTest'` 초록 · `gen/*`·`ddl-*.sql`·`alter-*.sql`·`insert/*` diff 0 · `self-check.txt` diff 0.
- 의존: S0(가지 순서상 2-35 뒤).

### 1-71 — typed·CTAS·LIKE·PARTITION OF 컬럼 끌어오기 + typed 몸
- 대상: `core/gen/DdlReader.java`(`CREATE_TYPE`·`TYPE_MEMBER`·`TYPED_OPT`·`otherCreate`·`createType`·`pull`·`typedBody`·`ctasColumns`·`likeItem(d, item, tables)`·`item(…, tables)`) · `core/gen/DdlReaderTest.java`(§3-4).
- 변경(TDD): ① `typedTable` 단언 추가 + `typedTableFromType`·`likeFromSource`·`ctasFromSource`·`partitionFromParent` → RED ② `DdlReader` → GREEN.
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,DdlGenTest,InsertGenTest,GenModelTest,GenRoutesTest,SelfCheckTest'` 초록 · 골든 diff 0 · `self-check.txt` diff 0(`read`·`item` 넉 줄 — 새 분기는 전부 도우미) · 새 메서드마다 복잡도 ≤ 15.
- 의존: 1-70.

### 4-30 — 개발자 도구 Hex·인코딩 Minor 셋 + corpus 대조
- 대상: `src/main/resources/tools/dev_tools.html`(`utf8Invalid`·`hexToBytes`·`encHexRender` 글) · `scripts/puppeteer/corpus-js.js`(enc 절 `badCount`·`fffd` · `encUnit` · `hexCases`) · `web/JsCorpusTest.java`(`hexMismatches` 한 줄 · `encUnitVectorsMatchWhatwg` · `decodeOff` 늘 baseline) · 골든 `corpus/js-enc-decode.txt`(새, 빈)(§3-5).
- 변경: ① `JsCorpusTest` 먼저 → RED(`bash scripts/mvn.sh -q test -Djunit.timeout=10m -Dtest=JsCorpusTest`: `badCount` 없음·`encUnit` null·baseline 없음) ② `corpus-js.js`·`dev_tools.html` → ③ `-Dgolden.update=true -Dtest=JsCorpusTest` 한 번(빈 `js-enc-decode.txt`) → GREEN · `js-summary.json` diff 0.
- 검증: 위 명령 갱신 없이 초록 · `bash scripts/mvn.sh -q test -Dtest='ToolsFolderTest,BackendToolsTest,SmokeHtmlUnitTest'` 초록 · 브라우저 한 번(`run.bat` → 개발 도구 인코딩 탭: MS949 CSV 올려 「깨진 문자」·Hexdump 글 · Hex 변환에 `A00x41` → 토스트 「16진이 아니다: "x"」 · `0x41 0x42` → `AB` · 콘솔 0) — 눈으로 한 번, 이력에 한 줄.
- 의존: 1-71(가지 순서일 뿐).

### V-50a — 재채점(corpus)
- 대상: 골든 `corpus/ddl-lint.json`·`ddl-lint-findings.txt`·`hr-lint.json`·`js-enc-decode.txt` · `PROGRESS.md`.
- 변경: §3-6 명령 → A 0 · B 갱신은 `-Dgolden.update=true` 로 바뀐 것만 → diff 를 이력에(findings 24 → N · hr-lint 10 → 2 · ddl-lint 그룹 글).
- 검증: 위 명령 갱신 없이 초록 · `ddl-summary.json`·`ddl-unreadable.txt`·`sqltables-diff.txt`·`sqlfixture.json`·`gen-hr.json`·`analyze-*`·`js-summary.json` diff 0(바뀌면 R-19 로 — 이름 셈 밖이면 중단).
- 의존: 4-30.

### R-19 — 손 판정
- 대상: `design/compare-2026-10-28.md`(⑳ 「손 판정」 절) · `PROGRESS.md` · (고침이 나면) `StructureLint`·`DdlReader`·시험·골든.
- 변경: §3-6 ①~⑤. 표 하나 + 분류 합. 결함 둘 이하는 고치고(음성 먼저) 넘으면 「후보로」.
- 검증: `bash scripts/doc-lint.sh design/compare-2026-10-28.md` · (고침 있으면) 해당 행 검증 재실행.
- 의존: V-50a.

### V-50 — compare ⑳ · 정적 분석 · `--corpus` · `--db`
- 대상: `design/compare-2026-10-28.md`(⑳ 나머지) · `PROGRESS.md`.
- 검증: `bash scripts/mvn.sh -DskipTests verify` rc 0 → `bash scripts/verify.sh` 초록 → `bash scripts/verify.sh --corpus` 초록(다른 컨테이너·WSL 내리고) → `wsl --shutdown` · Docker 다시 → **여유 RAM 확인** `powershell -c "[math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1MB,1)"` ≥ 15(GB) — 모자라면 사용자에게 브라우저·데스크톱 앱을 내려 달라고 하고 기다린다(번들 69 는 12.9GB 출발이 하네스 메모리 걷기로 끊겼다) → `bash scripts/verify.sh --db` 혼자 초록(약 17분, 다른 JVM 안 띄운다). 끝 줄이 더 부르는 레인이 없어야 한다. 벽시계 둘을 이력에.
- 의존: R-19.

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7 · 행의 축·강제 지점과 diff · FK 자동 인덱스 판정이 H2 이름 꼴 + 컬럼 같음 + 비유니크 셋을 다 보나(이름만 맞고 컬럼 다른 `F2` 가 자동이 아닌가) · `backing` 이 순서 무관인가 · uniqueNullable 이 PK·받침 인덱스를 안 세나 · `TEXT` 를 LOB 로 안 세나 · PK 이름이 ALTER·몸·컬럼 셋에서 다 오고 없으면 null 인가 · `INDEX_PREFIX` 가 컬럼일 때만인가 · typed 몸 unreadable 규칙 · CTAS 가 조인·식에서 null 로 물러나나 · 끌어온 표에 notes 가 없나 · `utf8Invalid` 가 `[0x80,0x80]` 을 둘로, `[0xE2,0x82]` 를 하나로 세나 · `hexToBytes` 가 `A00x41` 을 거절하고 `0x41 0x42` 를 받나 · `self-check.txt` 넉 줄 불변 · PMD 새 짝 0 · 골든이 의도된 변화인가(findings 23 줄은 이름만 바뀜)) → `bash scripts/gate-probe.sh` → `bash scripts/mvn.sh -DskipTests verify` → `bash scripts/verify.sh`(리뷰 고침 뒤 `--corpus`·`--db` 는 바뀐 레인만 다시) → `git push -u origin work/2026-11-02b` → `gh pr create --base main`(행마다 시작 세 줄 표 + 로컬 닫기 `e9c3a4c`·S0 같이 + ⑳ 핵심 수치 + 「사람 결정」 7-23 (나)·마-5 안 함) → CI 폴링(`gh api … check-runs`) → claude[bot] 처분 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → 닫기 커밋은 다음 가지 → 끝 보고. PR 본문 끝 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## 5. 금지 사항
- `pure/`·portfolio·`docs/`·`core/meta`·`core/dialect`·`core/profile`(`Profile.Lint` 키 추가 금지)·`JdbcMetaSource`·`SnapshotStore`·`tools/*.html`(dev_tools 밖)·`pom.xml`·`scripts/verify-fingerprint.sh`·`corpus/MANIFEST`·표본 수정 금지 · 새 Maven 의존성 금지 · 새 화면·라우트·프로필 키 금지
- `self-check.txt` 변화 금지(`StructureLint run(3)` · `DdlReader item(3)`·`read(1)` 둘 — 새 분기는 `structural`·`nameOnly`·도우미에) · 새 메서드 복잡도 ≤ 15 · PMD 새 짝 금지(`DdlReaderTest`·`JsCorpusTest` 는 기준선에 없다 — 되풀이 리터럴은 상수) · SpotBugs 새 지적 금지
- `fkNoIndex` 판정(`indexed`) 수정 금지 · `covered` 수정 금지 · 규칙 순서(enum 앞 열셋) 변경 금지 · 상한 1 을 프로필 키로 빼지 않는다 · `TEXT`·`TINYTEXT`·`JSON`·`XML` 을 LOB 로 세지 않는다
- `PrimaryKey.name` 은 이름 없는 PK 에 `""` 나 `"PK"` 를 넣지 않는다(null) · `prunePkIndexes` 규칙 변경 금지 · CTAS 에 조인·서브쿼리·식 컬럼 추측 금지(null 로 물러난다) · typed·LIKE·PARTITION 표의 유니크·인덱스·FK·CHECK 를 원본에서 복사하지 않는다(PK·컬럼만) · 끌어온 표에 notes 를 달지 않는다 · 형식 몸의 못 읽은 줄을 조용히 버리지 않는다(MEMBER 류만 건너뛴다)
- `cp949Invalid`·`encVerdict` 판정식 수정 금지 · `hexOf`·`hexDump` 수정 금지 · `js-summary.json`·`csv-*` 골든 변화 금지 · `JsCorpusTest` 의 다른 시험 수정 금지
- 골든 `gen/*`·`ddl-*.sql`·`alter-*.sql`·`insert/*`·`meta/*`·`ddl-summary.json`·`ddl-unreadable.txt`·`sqltables-diff.txt`·`gen-hr.json`·`analyze-*` 불변(바뀌면 R-19 판정 뒤 중단 조건 참조) · `DdlCorpusTest` 판정 로직 수정 금지(`NEW_RULES` 둘 추가만)
- 골든·compare·이력에 표본 코드 본문을 싣지 않는다(표·컬럼·인덱스 이름과 건수만) · 원본 되쓰기 API·CLI 금지 · `pmd-baseline.sh` 안 돌린다 · `--db` 와 다른 JVM 겹치기 금지 · `--corpus` → Docker → `--db` 순서 · `--db` 앞 여유 RAM 15GB

## 6. 최종 검증
- `bash scripts/mvn.sh -DskipTests verify` rc 0 · `bash scripts/verify.sh` · `--corpus` · `--db` 초록(순서대로, 따로) · `gate-probe.sh` 10/10
- 골든: `pg-lint.json`(12) · `hr-lint.json`(2) · `ddl-lint.json`(15 그룹 글) · `ddl-lint-findings.txt`(PK 이름 23 + tooManyLobs 3) · `js-enc-decode.txt`(새, 빈) 갱신 · 나머지 불변 · `self-check.txt` 불변
- 시험: `StructureLintTest` +3(`h2FkAutoIndexIsNotAKey`·`backingPrefersSameName`·`uniqueNullableAndLobs`) + 단언 수 셋(14·12·둘 끝) · `DdlReaderTest` +6(`pkNameFromBodyColumnAndAlter`·`prefixLengthOnlyForColumns`·`typedTableFromType`·`likeFromSource`·`ctasFromSource`·`partitionFromParent`) + 뒤집은 단언 둘 + 샘플 비교 한 줄 · `JsCorpusTest` +1(`encUnitVectorsMatchWhatwg`) + `badCount = fffd` + baseline 늘 · `DeliverableRoutesTest`·`HrSnapshotCorpusTest` 15 · `DdlCorpusTest` A 0 · `ArchitectureTest`·`VerifyLanesTest` 초록
- 수동: 데모 HR 스냅샷 91 구조 점검 → dupIndex 1(`JHIST_EMPLOYEE_IX ⊂ JHIST_EMP_ID_ST_DATE_PK`)·「PK(」 없음 · DDL 탭에 PG typed 둘 붙여 넣어 DTO 에 컬럼 둘 · 인코딩 탭 §4 4-30 의 셋(눈으로 한 번, 이력에 한 줄)
- compare ⑳ 표 + R-19 표 + 「드러난 것」 + 「후보로」(마-5 내림) · 이력에 건수·벽시계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- `self-check.txt` 변화 · `pg-lint.json` 변화가 `users.email` 한 건 밖 · `hr-lint.json` 이 2 가 안 됨(남은 줄이 `*_FK_INDEX_n` 을 담음) · `ddl-lint-findings.txt` 변화가 「PK 이름·tooManyLobs 3」 밖 · `ddl-summary`·`ddl-unreadable`·`sqltables-diff`·`gen/*`·`ddl-*.sql`·`alter-*.sql` 변화 · `pmd:check`·SpotBugs 새 짝 · `ArchitectureTest`·`VerifyLanesTest` 빨강
- `JsCorpusTest` 가 CSV 100 중 하나라도 `badCount ≠ fffd`(WHATWG 와 다른 꼴이 있다 — 벡터를 이력에 적고 멈춘다) · `encUnit` 기대 아홉 중 둘 이상 틀림
- typed 형식 몸에서 unreadable 이 새로 생겨 `ddl-summary` 못 읽은 줄이 3 을 넘음(어느 형식·줄인지 적고 멈춘다 — `TYPE_MEMBER` 낱말 추가는 하나까지)
- `verify.sh --db` 메모리로 끊김 — 고아 JVM 끄고 한 번 더, 또 끊기면 보고
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① 다른 방언(mariadb·postgresql·altibase·goldilocks·tibero·cubrid) egov 그룹의 tooManyLobs 수(oracle 은 3 — 같은 표 셋이면 3, PG 가 `TEXT` 면 0) ② `oe_cre.sql`·`oc_cre.sql` 형식 몸이 unreadable 을 내는지(`ddl-summary` 못 읽은 줄 3 그대로인가) ③ `JsCorpusTest` 벽시계와 CSV 100 의 `badCount = fffd` ④ `ddl-lint-findings.txt` 의 PK 이름 꼴(원문 대소문자 — `COMTNBBS_PK`·`jhist_emp_id_st_date_pk`) ⑤ `--corpus`·`--db` 벽시계. 첫 스텝 전: `bash scripts/verify.sh` 초록 · `git log --oneline -1`(S0 커밋) · `ls C:/workspace/toolbox-corpus/csv | wc -l`(100) · node 있음 · Docker Desktop 상태
- **사용자만 정할 수 있는 것** — 없음(범위 셋·범위 밖·7-23 (나)·마-5 안 함·머지 위임 전부 2026-11-02 결정. 상한 1·`TEXT` 제외·H2 꼴만·PARTITION OF 포함은 설계가 정했다 — 바꾸고 싶으면 새 행)
