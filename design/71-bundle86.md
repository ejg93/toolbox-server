# 설계 71 — 번들 86: 조용히 틀린 출력 Critical 열셋 + 같은 파일 Important 열일곱 + 대기 잔손 여섯 (Fable 설계 · Opus 실행, 2026-11-15)

## 맥락

가로 점검 2차(설계 68)의 「조용히 틀린 출력」 Critical 열셋은 생성기(DdlGen·AlterGen·DdlReader·InsertGen·Generator)·산출물(XlsxFiller·Relations)·분석(SqlTables)·스냅샷(SnapshotDiff·PostgresMetaSource)에 있다. 전부 경고 없이 틀린 DDL·INSERT·xlsx·CRUD 를 낸다. 같은 파일의 Important 열일곱과 대기 잔손 여섯을 같은 무거운 검증(`--corpus`·`--db`) 한 번에 묶는다.

**사용자 결정(고정 — 다시 묻지 않는다)**
1. 범위 (라)(2026-11-15 저녁) — Critical 13: A-01·A-03·A-11·A-12·A-15·A-28·R-A-C1 · B-01·B-02 · C-TBL-COMMA · F-K8·F-K9·F-K21 / Important 17: R-A-I1·R-A-I13·A-09·A-16·A-14 · R-A-I4·R-A-I5 · A-02·A-10·A-30 · A-04~A-07 · B-07·B-12 · C-TBL-DELALIAS / 잔손 6: `0-86`·`0-87`·`0-94`·`0-96`·`5-115`·`6-43`
2. 뺌 — `0-95`(JavaGraph 쪼개기) · `5-114`(getter 반환 형식)
3. 자동증가 (가) **수집한다**(2026-11-15 설계 중) — `Column.autoIncrement` + JDBC `IS_AUTOINCREMENT` + 마이그레이션 V016 + DdlGen·AlterGen 이 절을 낸다
4. A-03 (가) **읽는다 + 나머지 알림**(2026-11-15 설계 중) — ADD·DROP 컬럼 · ADD CHECK · MODIFY/CHANGE 재정의 · CREATE 앞 ALTER 미루기. 못 읽는 꼴은 「못 읽은 줄」. CREATE 가 글에 없는 표의 ALTER 는 지금처럼 조용히 넘긴다
5. 머지 위임 · 밤새 무인 실행

**보정(2026-11-15, `9fb4548` 트리 — 코드는 `bf4dedc` 와 같다)**
- 설계 68 의 줄 번호는 열아홉 키 전부 지금 코드와 맞다(`core/gen`·`core/meta`·`core/deliverable`·`core/report`·`SqlTables` 는 설계 68 뒤 안 바뀜)
- **자동증가는 모델에 없다** — `Column` 11 성분에 없고 `IS_AUTOINCREMENT` 수집 0 · `snap_column` 에 열 없음. R-A-C1·A-11(자동증가 쪽)은 생성기만 고쳐선 못 닫는다 → 결정 3
- **F-K8** — 반영 DDL(AlterGen)은 `SnapshotDiff.compare` 를 안 쓴다(`index` 만). FK 규칙 변화는 이미 `AlterGen.fkShape:727` 이 잡는다. 빠진 것은 `SnapshotDiff` 의 CHECK·FK 규칙과 **AlterGen 의 CHECK**. 「테이블 변경이력」 산출물은 코드에 없다(javadoc 뿐) — 소비자는 `/api/meta/diff`·`db_browser.html`·CLI `diff`
- **F-K21** — `-1` 은 이미 null(`PostgresMetaSource:86`). `0` 만 문제. 0 행 표는 `MetaSource.collect:101` 의 범위 거름에서 `RowCounter` 보다 먼저 빠진다
- **A-11** — PG `serial` 은 기본값 `nextval(…)` 이 `defaultOf:385` 경고로 떨어져 조용하지 않다. MariaDB AUTO_INCREMENT·MSSQL IDENTITY·PG IDENTITY 가 조용히 빠진다
- **R-A-I5** — 벤더 수집(Oracle·Tibero·PG·MariaDB·MSSQL)의 `uniques` 는 진짜 UNIQUE 제약만이다. 유니크 인덱스가 `UniqueKey` 로 오는 길은 `JdbcMetaSource.uniqueIndexes:242`(H2·모르는 방언·벤더 SQL 물러섬)뿐
- **A-12** — 같은 꼴이 DTO 저장(`GenRoutes:128-130`)에도 있다. 범위 밖(새 행 후보)
- **0-96** — `password: # 주석` 의 주석은 `plainEnd` 가 아니라 `ProfileRoutes.withoutBlockPasswords:287`(group 3 을 버림)에서 사라진다
- **self-check 기준선은 정확 일치다** — 줄이 사라져도(새로 고쳐짐) 빨강. 값이 내린 줄은 갱신, 오른 줄은 안 받는다(예외는 행에 적는다)
- 표본의 `ALTER TABLE … CHANGE` 7,770 줄은 CREATE 없는 코멘트 스크립트(`egov_*_comment.sql`)다 — 결정 4 의 「모르는 표는 조용히」 로 소음이 안 된다
- `DbCorpusBase` 는 `db`+`corpus` 꼬리표라 CI 가 안 돈다 — `verify.sh --db` 만. db 만 붙은 시험(`PostgresMetaSourceTest` 등)은 CI 가 돈다

## 1. 목표와 범위

목표 — 생성 DDL·ALTER·INSERT·소스·xlsx·CRUD·스냅샷 비교가 경고 없이 틀리는 꼴 서른을 고치거나 알리고, 대기 잔손 여섯을 닫는다. 사용자가 겪는 변화: 변환 DDL 에 CHECK·FK 규칙·자동증가가 실린다 · MariaDB 코멘트 변경이 AUTO_INCREMENT 를 안 뺀다 · ALTER 로 더한 컬럼이 DTO·INSERT 에 나온다 · 예약어·대소문자 이름이 따옴표로 나간다 · 양식 합계가 전 행을 센다 · 쉼표 뒤 표가 CRUD 에 나온다 · 비교가 CHECK·FK 규칙을 본다.

In scope
- 결함 키 30(Critical 13 + Important 17) — 행 `1-86a`~`1-99`·`4-33`~`4-35`·`7-32`·`2-41`~`2-43`·`6-44`·`6-45`
- 잔손 여섯 — `0-86`+`0-87`(한 행) · `0-94` · `0-96` · `5-115`(①②③) + 새 행 `5-116`(④⑤⑥) · `6-43`
- 실물 DB 왕복 단언 `9-8` · `V-65a`(정적 분석 + `--corpus`) → `R-32`(compare ㉟) → `V-65`(`--db`)

Out of scope
- `0-95` · `5-114` · `5-100` · 남은 Important 30(번들 87~)
- 식별자 인용 통일(설계 68 6절 ⑨ 전체) — `QualitySql.id`·`Masking.id`·매퍼 템플릿(A-36)·`CommentDdl`(B-14)은 안 고친다. `DdlGen.reserved` 목록이 늘어 `QualitySql` 이 더 감싸는 것은 따라오는 변화
- DdlReader 가 「원문에 따옴표가 있었나」 를 기억하기(A-06 의 대소문자 이름 `Artist`) · A-08·A-29·A-35·A-37
- ALTER 의 PG `ALTER COLUMN`·`RENAME`·제약 DROP 을 모델에 반영(「못 읽은 줄」 로만)
- CHECK 조건의 방언 변환(다른 방언이면 주석으로 남긴다) · 자동증가만 바뀐 것을 비교·반영 DDL 의 차이로 보기
- `SqlJoins.aliases` 의 쉼표 한계 · DTO 저장 경로 겹침(`GenRoutes:128`) · 양식의 단일 칸 참조 `SUM(B3)` 넓히기 · 병합·조건부 서식 잇기(B-23)
- 화면(`tools/*.html`·`*.js`) 변경 0

## 2. 현재 구조 요약

진입로 — `DdlReader.read` 는 `GenRoutes:82`(DTO)·`InsertRoutes:72`(INSERT) 둘뿐. `DdlGen`(`DeliverableRoutes:92` `/api/deliverable/ddl`)·`AlterGen`(`MetaRoutes:84` `/api/meta/alter`)·`Generator`(`GenerateRoutes:162`)는 스냅샷 표만 받는다. AlterGen 의 타입·기본값·인용은 전부 `DdlGen.Gen`(`AlterGen.Run.gen:126`)을 지난다 — 새 표는 `DdlGen.generate(includeFk=false)` 뒤 자기 `addFk`.

| 키 | 지금 자리 | 재현 꼴(설계 68 `judge-*.md` 같은 키 행) |
|---|---|---|
| A-11 | `DdlGen.Gen.table:231-279`(`t.checks()` 안 봄) · `fk:389-403`(규칙 안 봄) · `column:281-295`(자동증가 절 없음) | CASCADE FK + CHECK 표 → `FOREIGN KEY (P_ID) REFERENCES P (ID);` 뿐, `warnings=[]` |
| A-15 · A-14 | `Gen.id:447-457` · `PLAIN:55` · `ddl-types.yaml:26-30`(77 낱말) | pg→pg `userId int, userid int` 충돌 · `LEFT INTEGER` 문법 오류 |
| A-28 | `DdlGen.lit:479`(static, 방언 없음 — mariadb 자리 `:250·292`, AlterGen `:624·628·646·657-662`) · `InsertGen.quote:468`(`literal:465`·`csvLiteral:641·652·660`) | mariadb `COMMENT '경로 C:\'` · `'C:\temp\'` |
| R-A-I1 | `Gen.nativeWithSize:336-349` | mssql→mssql `nvarchar(2147483647)` |
| R-A-I13 | `Gen.defaultOf:351-387`(`NUM` 이면 그대로 `:367`) | mssql bit `((1))` → pg `BOOLEAN DEFAULT 1` |
| A-09 | `Gen.table:275` | mariadb→pg 같은 인덱스 이름 둘 `already exists` |
| A-16 | `Gen.type:307-317`(`len == null` → `String.long`) | pg `uuid` PK → oracle `ID CLOB NOT NULL` |
| R-A-C1 | `AlterGen.Run.modifyColumn:344-346` → `g.column` | 코멘트만 바뀐 `MODIFY COLUMN id BIGINT NOT NULL COMMENT …` — AUTO_INCREMENT 빠짐 |
| R-A-I4 | `Run.addFk:576` `fks(t).size()` | 이름 없는·SYS_ FK 둘이 다 `FK_CH_2` |
| R-A-I5 | `Run.uniquesAndIndexes:436-447` | `DROP CONSTRAINT UX_USERS_EMAIL` — 인덱스라 `not found` |
| A-01 | `DdlReader.CONSTRAINT_LINE:47-49` · `item:318-340` · `inlineIndex:456` · 같은 식 `DdlPull.typedBody:188` | `KEY VARCHAR(50)` → 컬럼 없음 + `Index[name=VARCHAR, columns=[50]]` |
| A-03 | `DdlReader.ALTER_TABLE:87-88` · `read:224-237` | `ALTER TABLE t ADD c INT` → 컬럼 `id` 하나, `unreadable=[]` |
| A-02 | `DdlReader:193·292`(`tables.put(이름)`) · 찾기 `:207·212·227·439` | `s1.emp`·`s2.emp` → 하나만 |
| A-10 | `DdlReader:110-113`(TYPE_END)·`:84`(INDEX_SORT)·`:371`·`:589·607`·`:662` · `DdlPull:47`(JOIN_SEP) · `Validation:65`(ANY_ARRAY)·`columnsIn:268` | 30KB 본문 `/api/gen/dto` 50초 |
| A-30 | `DdlReader.SQLPLUS:136-139` · `stripSqlPlus:714-735` | `ON DELETE`⏎`SET NULL;` 줄이 지워져 FK 와 다음 CREATE 가 사라짐 |
| A-04 | `InsertGen.genVal:254-256·264-274` · `domain:356-371` | CHAR(4) PK `'C_00'` 반복 · TINYINT PK 129 행째 겹침 |
| A-05 | `InsertGen.checkIns:155-165`(`putIfAbsent(컬럼)`) ← `InsertRoutes:78` | A.STATUS 값이 B.STATUS 에 |
| A-06 | `InsertGen:430·438·491·512-556·610` · `u:563` | `INSERT INTO Order Details(` · `ORDER` |
| A-07 | `InsertGen.genVal:281-294` | TIME 컬럼 `'VAL_1'` |
| A-12 | `Generator.run:82-111`(겹침 검사 없음, `writeResult` 덮어씀) | `TB_USER`·`TN_USER` → `UserVO.java` 하나, 둘 다 `created` |
| B-01 | `XlsxFiller.fill:93-95` | 합계 `SUM(B3:B3)` 그대로 — 10(기대 60) |
| B-02 | `Relations.infer:65-73`(`putIfAbsent(이름)`·`declared`) · `Definitions.d04:398-403` | 두 스키마 CUSTOMER·ORDERS → 04 가 먼저 정렬된 스키마에만 |
| B-07 | `Relations.named:143-179` · `DeliverableService.writeGuide:215-216` · `XlsxWriter.write:35-47`(스트림을 먼저 연다) | PK `ID` 표 1,500 → 행 상한 초과, 0 바이트 작성안내 |
| B-12 | `Definitions.constraints:357-362` | `STATUS IN ('ACTIVE','ID','OFF')` 가 ID 컬럼 제약에 |
| C-TBL-COMMA | `SqlTables.table:252-257` · `alias:316-334`(CLOSED 에서 쉼표 무시) | `FROM A, (SELECT …) X, C` → C 없음 |
| C-TBL-DELALIAS | `SqlTables.table:245` · `extract:146-149` | `DELETE a FROM tb_a a` → `A=D TB_A=R` |
| F-K8 | `SnapshotDiff.constraints:137-150` | CHECK 지움·FK CASCADE→NO ACTION 이 `isEmpty=true` |
| F-K9 | `SnapshotDiff.index:153-166`(스냅샷마다 따로) ← `compare:62-63`·`AlterGen.generate:66-67` | a `APP.T1` / b `APP.T1`+`APP_BAK.T1` → 추가 둘·삭제 하나 |
| F-K21 | `PostgresMetaSource.loadStats:86` | `reltuples` 0 → 0 행, skipEmpty 면 표가 사라짐 |

함정
- **self-check 기준선**(`golden/check/self-check.txt`, 정확 일치) — `DdlGen` `table(1)` 16·`defaultOf(3)` 18·`type(2)` 24 · `AlterGen` `Run` 메서드 40·`keyBreakingFks` 18·`uniquesAndIndexes(4)` 19·`modifyColumn(7)` 40(인자 7 = 상한) · `DdlReader` `item(3)` 30·`read(1)` 36·중첩 6, **메서드 정확히 30(상한)** · `Generator.run(12)` 19 · `InsertGen` `generate(4)` 20·`buildUpsert(5)` 24·`csvLiteral(5)` 31·`genVal(4)` 37 · `Validation` `parse` 28·`of(5)` 56 · `SqlTables.extract(2)` 17 · `Relations` `named(2)` 19·`infer(2)` 26 · `XlsxFiller.fill(4)` 25·중첩 5 · `Definitions` 메서드 34 · `JavaGraph` 길이 1013·메서드 40·`run` 31·`walk` 42·`entityManager` 19·`querydsl` 22. 기준선 밖 메서드 상한은 복잡도 15·길이 100·인자 7, 클래스는 메서드 30·길이 1000
- `Column` 을 베끼는 `new Column(` 자리(main 넷: `MariaMetaSource:177`·`DdlPull:231·403`·`DdlReader:245` + `Column.withComment:21`)는 새 성분을 넘겨야 한다 — 안 넘기면 MariaDB 수집에서 자동증가가 사라진다
- `JdbcMetaSource.loadColumns` 는 열 번호 순서대로 한 번씩만 읽는다(Oracle ORA-17027) — `IS_AUTOINCREMENT`(23번)는 `ORDINAL_POSITION`(17번) **뒤에** 읽는다
- pgjdbc 는 serial 컬럼의 `TYPE_NAME` 을 `serial`·`bigserial` 로 준다 — 같은 방언이면 그 타입이 곧 자동증가라 IDENTITY 절을 더 붙이면 안 된다
- `DdlReaderTest.readsCreateIndexAndInlineKey:408` 은 모르는 표의 인덱스에 `unreadable` 이 비어 있음을 단언한다(모르는 표는 조용히 — 결정 4 와 같다)
- `DbCorpusBase.alterRoundTrip:1256` 은 `SnapshotDiff.compare(b, s2)` 의 제약 차이 0 을 단언한다 — `b` 는 `s1`(벤더 스냅샷)에서 만들어 CHECK 가 같이 온다
- `AnalyzeCorpusTest.egov:226-230` 은 `table` 미해결을 등급 A 로 본다 — `SqlTables` 가 새 `table` 미해결을 내면 빨강
- `golden/analyze/sql-tables.json` 은 `SqlTablesTest.CASES` 표가 만든다 — 기존 사례를 안 고치고 사례만 더한다
- `stripComments` 는 주석을 공백으로 바꿔 자리를 보존한다(`RegexRule:134`)
- `core/gen` 시험 폴더는 `LANE_DB` 에 없다 — db 꼬리표 시험을 거기 새로 두면 `VerifyLanesTest.dbLaneCoversDbTests` 빨강. 실물 단언은 `web/DbCorpusBase.java`·`core/dialect/*Test`(이미 있음)에 둔다
- PMD `AvoidDuplicateLiterals`(넷째 등장)·Checkstyle `DeclarationOrder`·`UseVarargs` — 빠른 `verify.sh` 는 안 본다

레인 — `core/gen`·`core/meta`·`core/dialect`·`resources/gen`·`resources/db`·`SqlProbe.java`·`DbCorpusBase.java` 는 db + corpus. `core/analyze`·`core/check`·`core/deliverable`·`core/report`·`core/fs` 는 corpus. `web/*Routes`·`cli`·`core/profile` 은 java 만. → 끝에 정적 분석 → `--corpus` → `--db`.

## 3. 설계 결정 — 전부 [고정]

### §3-1 `1-86a`·`1-86b` 자동증가 수집
- `Column` 12번째 성분 `@JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean autoIncrement`. 11 인자 생성자를 남긴다(false) — 새로 만드는 자리(시험 26·main 여섯)는 안 고친다. 베끼는 자리 다섯만 12 인자로
- 수집은 `JdbcMetaSource.loadColumns` 한 자리 — `getColumns` 의 `IS_AUTOINCREMENT` 가 `YES` 일 때만 참. 열이 없거나 읽다 깨지면 거짓. 벤더 SQL 을 더하지 않는다(Tibero 자기 질의 길 `TiberoMetaSource:91` 은 거짓)
- 저장 — V016 `snap_column.auto_increment BOOLEAN DEFAULT FALSE NOT NULL`. 옛 스냅샷은 거짓(= 모름)
- `SnapshotDiff`·`AlterGen` 은 자동증가를 **비교하지 않는다** — 옛 스냅샷(거짓)과 새 스냅샷(참)이 전부 차이로 뜬다
- 버린 대안 — `Boolean`(null = 모름): 새 스냅샷의 모든 컬럼에 `"autoIncrement": false` 가 실려 메타 골든이 통째 바뀐다

### §3-2 `1-87` 식별자 — 예약어 넓힘 · 접기 · 대소문자 정확 이름(A-14·A-15)
- `Gen.id` 판정 셋 — ① **정확 이름**: 원본 방언이 접는 글자와 다른 이름(원본 oracle·tibero 인데 대문자가 아님 / 원본 postgresql 인데 소문자가 아님)은 원본에서 따옴표로 만든 것 → 글자 그대로 감싼다. 원본이 mariadb·mssql·모름이면 정확 이름이 아니다 ② 예약어(`PLAIN` 이고 정확 이름 아님) → 감싸되 **대상이 접는 글자로**(oracle·tibero 대문자 · postgresql 소문자 · mariadb·mssql 그대로) ③ `PLAIN` 아님(공백·한글) → 글자 그대로 감싼다
- 정확 이름 판정은 **원본이 준 컬럼 이름·표 이름에만** 건다(접두를 붙이기 전 글자로). 제약·인덱스 이름(`cname`)과 대상 스키마(`o.schema()` — 사용자가 친 글)는 정확 이름으로 안 본다 — 우리가 지은 이름(`PK_<표>`·`IX_<표>_1`)과 접두(`G18_`)가 섞여 소문자 원본에서 전부 감싸진다
- 경고 글 — 정확 이름은 새 글 `": 대소문자 섞인 이름 — 따옴표로 감쌌다"`, 나머지는 기존 글 그대로
- 접기 이유 — 예약어가 아닌 대상에서 감싸도 접은 글자면 안 감싼 것과 같은 이름이다(`"start"` ≡ `start`). 목록을 넓혀도 해가 없다
- `ddl-types.yaml` `reserved` 에 더한다(다섯 방언 어디선가 예약어 · 컬럼 이름으로 쓸 만한 것): `ANALYZE, ARRAY, AUDIT, BACKUP, BEGIN, BINARY, BOTH, BREAK, CAST, CHANGE, CHAR, CHARACTER, CLOSE, CLUSTER, COLLATE, COMMIT, COMPRESS, CONDITION, CONNECT, CONVERT, CROSS, CURSOR, DATABASE, DECIMAL, DECLARE, DO, DOUBLE, ESCAPE, EXCEPT, EXCLUSIVE, EXEC, EXECUTE, EXIT, EXPLAIN, FALSE, FETCH, FLOAT, FOREIGN, FULL, IDENTIFIED, IF, IMMEDIATE, INCREMENT, INITIAL, INNER, INT, INTEGER, INTERSECT, JOIN, KEYS, KILL, LATERAL, LEADING, LEFT, LONG, MERGE, MINUS, MODIFY, NATURAL, NOWAIT, NUMERIC, OFFLINE, ONLINE, ONLY, OPEN, OUTER, OVER, PARTITION, PCTFREE, PERCENT, PLAN, PRINT, PRIOR, PROC, PROCEDURE, PUBLIC, RANGE, RAW, READ, REAL, RENAME, REPEAT, REQUIRE, RESOURCE, RETURN, RETURNING, REVOKE, RIGHT, ROLLBACK, ROWID, ROWNUM, RULE, SAVE, SCHEMA, SEPARATOR, SHARE, SHOW, SMALLINT, SOME, SSL, START, SYNONYM, SYSDATE, TOP, TRAILING, TRAN, TRUE, TRUNCATE, USE, USING, VALIDATE, VARCHAR, WHILE, WINDOW`. 안 넣는다: `NAME`·`TYPE`·`STATUS`·`TIME`·`TIMESTAMP`·`YEAR`·`COUNT`(네 엔진 어디서도 컬럼 이름으로 막히지 않는다)
- 버린 대안 — 대소문자 섞인 이름을 원본과 무관하게 전부 감싸기: mssql·mariadb 원본의 `ArtistId` 가 Oracle 에서 대소문자 가리는 이름이 된다

### §3-3 `1-88` MariaDB 리터럴 역슬래시(A-28)
- 대상이 mariadb 면 리터럴 안 `\` 를 `\\` 로(기본 sql_mode 는 역슬래시가 탈출 글자). 다른 방언은 그대로
- `DdlGen.lit(String v, boolean backslash)` 를 더하고 `lit(v)` 는 `lit(v, false)` 로 남긴다. mariadb 자리(`DdlGen:250·292`, AlterGen 의 mariadb 가지)는 `lit(v, true)`
- `InsertGen.quote(String s, String db)` — `"mysql".equals(db)` 일 때 겹친다. `literal(c, v)` → `literal(c, v, db)`
- 원본에서 온 기본값 글(`defaultOf` 의 `STR`)·`c.def()` 는 안 건드린다(이미 그 DB 의 글)
- `NO_BACKSLASH_ESCAPES` 모드는 안 가린다(한계 — javadoc 한 줄)

### §3-4 `1-89` 변환 DDL — FK 규칙 · CHECK · 자동증가 절(A-11 · R-A-C1)
- FK 규칙 — `AlterGen.Run.rule:592` 를 `DdlGen.Gen.rules(Table, ForeignKey, String name)` 로 옮긴다(글·판정 그대로, 못 닿는 `:600` 은 버린다). `DdlGen.fk` 와 `AlterGen.addFk` 가 같이 쓴다. `Run.rule` 은 지운다
- CHECK — `Gen.checks(Table)` 을 `table()` 끝(유니크 뒤, 인덱스 앞)에서 부른다. 원본과 대상 방언이 같으면 `ALTER TABLE … ADD CONSTRAINT <이름> CHECK (<조건>);`, 다르면 주석 `-- [확인] <표> CHECK (<조건 80자>) — 방언이 달라 옮기지 않았다` + 표마다 경고 한 줄. 이름은 `nameOr(k.name(), "CK_" + 표 + "_" + n)`
- 자동증가 절 — 타입 뒤: mssql ` IDENTITY(1,1)` · postgresql ` GENERATED BY DEFAULT AS IDENTITY`(타입 글이 `SERIAL` 로 끝나면 안 붙인다). NOT NULL 뒤: mariadb ` AUTO_INCREMENT`(키 컬럼일 때만 — 아니면 경고하고 뺀다). oracle·tibero 는 절 없이 경고 `": 자동증가 — <대상> 는 시퀀스·트리거(12c+ 는 IDENTITY)를 손으로"`
- 자동증가 컬럼의 기본값은 안 낸다(`nextval(…)` 경고도 없다)
- R-A-C1 은 이것으로 닫힌다 — `modifyColumn:346` 이 `g.column` 을 쓴다. AlterGen 의 mssql·pg·oracle MODIFY 길은 `g.column` 을 안 써 절이 안 샌다(확인 단언)

### §3-5 `1-90` 타입 · 기본값 · 인덱스 이름(R-A-I1 · R-A-I13 · A-16 · A-09)
- R-A-I1 — `nativeWithSize` 의 문자 길이가 mssql 은 8000, 그 밖은 10,485,760 을 넘으면 mssql `(MAX)`, 그 밖은 크기 없이 원문만
- R-A-I13 — 대상 타입이 `BOOLEAN` 이고 값이 `0`·`1` 이면 `FALSE`·`TRUE`
- A-16 — 길이 없는 문자 타입이 키 컬럼(PK·유니크·인덱스)이면 `String.long` 대신 길이를 준다: 원본 타입이 `UUID`·`UNIQUEIDENTIFIER` 면 36, 그 밖은 255 + 경고 `": 길이 없는 문자 타입이 키 컬럼 — 길이 <n> 으로 냈다"`
- A-09 — 대상이 oracle·tibero·postgresql 이면 이번 실행에서 낸 인덱스 이름(대문자)을 모아, 겹치면 `IX_<표>_<번호>` 로 바꾸고 경고. mariadb·mssql 은 표 단위 이름 공간이라 그대로. AlterGen 의 인덱스 이름(`:455·484`)은 안 고친다

### §3-6 `1-91` AlterGen — FK 대체 이름 · 유니크 지우기 · CHECK 차이(R-A-I4 · R-A-I5 · F-K8 반영 DDL)
- R-A-I4 — 대체 이름 번호는 `t.fks()` 안 순번(1부터) — `DdlGen.run:209-213` 과 같은 번호
- R-A-I5 — 원본 방언을 모르면(`o.source()` 빈 글) 유니크 지우기 문장 앞에 `check(Kind.COMPAT, "유니크 <이름> — 제약인지 인덱스인지 스냅샷이 모른다(방언 모름). DROP CONSTRAINT 가 실패하면 DROP INDEX")`. 문장은 그대로 낸다
- CHECK 차이 — 새 메서드 `Run.checks(out, g, tbl, ta, tb)`: 조건(공백 접음)으로 짝짓는다. 사라진 것은 `DROP CONSTRAINT <이름>`(이름이 없거나 DB 가 지은 이름이면 `hand`), 생긴 것은 §3-4 와 같은 글(방언이 다르면 `hand`). `Run` 메서드 수는 `rule` 이 빠져 40 그대로

### §3-7 `1-92` KEY·INDEX·PERIOD 로 시작하는 컬럼(A-01)
- `DdlPull.constraintLine(item)` — `CONSTRAINT_LINE` 이 맞아도 첫 낱말이 `KEY`·`INDEX` 면 「이름(선택) + (선택 USING x) + `(` + 숫자·따옴표가 아닌 글자」 꼴일 때만 제약 줄, `PERIOD` 면 `PERIOD FOR` 일 때만. 아니면 컬럼
- `DdlReader.item:318-319` 와 `DdlPull.typedBody:188` 이 이것을 쓴다

### §3-8 `1-93` 문장 가운데 `SET …` 줄(A-30)
- `stripSqlPlus` — `SET` 으로 시작하는 SQL*Plus 줄은 **문장이 열려 있으면** 안 지운다. 열림 = 마지막 종료(끝이 `;` 인 줄 · `/` 만 있는 줄 · `GO` 만 있는 줄) 뒤에 안 지운 비지 않은 줄이 있다. `PROMPT`·`REM` 등 나머지 명령은 지금 그대로

### §3-9 `1-94` 같은 이름 표, 다른 스키마(A-02)
- `DdlPull.slot(tables, d)` — 맨 이름 자리가 비었거나 같은 스키마면 맨 이름, 스키마가 다른 표가 이미 있으면 `스키마.이름`(대문자). `DdlPull.find(tables, 이름 마디)` — 스키마가 있으면 `스키마.이름` 자리 먼저, 없으면 맨 이름
- 넣기 `:193·292`, 찾기 `:207·212·439` 와 `DdlAlter`. `DdlPull` 안 찾기(LIKE·CTAS 원본)와 `Result.notes` 열쇠는 그대로(한계 — javadoc)

### §3-10 `1-95a`·`1-95b` ALTER 읽기(A-03)
- 새 클래스 `core/gen/DdlAlter` — `read:224-237` 을 옮기고 넓힌다. `DdlReader` 에 메서드를 더하지 않는다
- 아는 표만 고친다. 표를 모르면: `1-95b` 뒤로는 미뤘다 끝에 한 번 더 보고, 그래도 모르면 조용히 넘긴다
- 문장을 괄호 밖 쉼표로 동작으로 나눈다. 동사 없는 동작은 앞 동작이 ADD 였으면 ADD 로 잇는다(MSSQL `ADD a INT, b INT`)
- 읽는 동작 — `ADD [COLUMN] 정의` · `ADD (정의, …)` · `ADD` 제약(FK·UQ·PK 지금 그대로 + CHECK) · `DROP [COLUMN] 이름` · `DROP (이름, …)` · `MODIFY [COLUMN] 정의` · `MODIFY (정의, …)` · `CHANGE [COLUMN] 옛이름 정의` · `COMMENT [=] '글'`(표 코멘트)
- 재정의는 정의가 통째일 때만 — `item()` 을 빈 `Draft` 에 돌려 타입 있는 컬럼 하나가 나오면 옛 컬럼 자리에 넣는다(순번 유지, 코멘트는 새 것이 없으면 옛 것). 타입이 없는 조각(`MODIFY (b NOT NULL)`)은 「못 읽은 줄」
- 모델에 영향 없는 동작은 조용히 — `OWNER TO`·`ENABLE`·`DISABLE`·`[NO]CHECK CONSTRAINT`·`SET TABLESPACE|SCHEMA|LOGGED|(…)`·`ENGINE`·`CHARSET`·`CONVERT TO`·`AUTO_INCREMENT`·`ROW_FORMAT`·`REBUILD`·`SWITCH`·`ATTACH|DETACH PARTITION`·`CLUSTER ON`·`REPLICA IDENTITY`·`VALIDATE CONSTRAINT`·`[NO]LOGGING`·`[NO]PARALLEL`·`MOVE`·`[NO]INHERIT`·`ALTER COLUMN x SET STATISTICS|STORAGE`
- 그 밖(`ALTER COLUMN`·`RENAME`·`DROP CONSTRAINT|INDEX|PRIMARY KEY|FOREIGN KEY|PARTITION`…)은 `Unreadable(표, "ALTER TABLE <표> <동작>" 80자)`
- `InsertRoutes` 는 고른 표의 「못 읽은 줄」 수를 경고 한 줄로 낸다(지금은 버린다)

### §3-11 `1-96` 정규식 제곱 시간(A-10)
- 공백 반복 — 앞에 `(?<!\s)` 를 두고 소유 수량자로(`\s++`). 공백 줄 가운데서는 시작하지 않는다
- `:662` 의 `^\s*GO\s*$` 는 줄 안 공백만(`[ \t]`) — 빈 줄을 건너 훑지 않는다
- `:371` 의 `\([^)]*\)` 지우기는 손 훑기 `DdlPull.dropParens`
- `ANY_ARRAY` 의 `(.*?)\]` → `([^\]]*+)\]`
- `Validation.columnsIn(cond, t, c)` — 이 컬럼 이름이 조건에 없으면 표 전체를 안 돈다(컬럼 × CHECK × 컬럼)
- 요청 크기 상한·시간 상한은 안 넣는다

### §3-12 `4-33`~`4-35` InsertGen(A-06 · A-04 · A-07 · A-05)
- A-06 — `InsertGen.ident(name, db)`: `PLAIN` 이고 `DdlGen.reserved` 가 아니면 그대로, 아니면 방언 따옴표(mysql 백틱 · mssql·sybase 대괄호 · 그 밖 큰따옴표). 예약어는 접어서(oracle 대문자 · pg 소문자), `PLAIN` 아닌 이름은 글자 그대로. SQL 로 나가는 표·컬럼 이름 자리 전부(INSERT·MERGE·ON CONFLICT·별칭). 경고 글과 `#{camel}` 은 그대로
- A-04 — 키 컬럼(PK·유니크)이고 길이가 4 처럼 순번 세 자리 + 밑줄이 안 들어가면 `pad(idx + 1, 길이)`. `domain` 에 문자 길이 1·2(10·100)와 TINYINT(128)를 더해 `keyOverlap` 이 경고한다
- A-07 — TIME 은 `'HH:MM:00'` · YEAR 는 수 · INTERVAL 은 oracle `INTERVAL 'n' DAY|MONTH`, 그 밖 `'n days'`
- A-05 — `InsertGen.checkIns(Table)`(그 표의 `checks()` 만). `InsertRoutes` DDL 모드가 이것을 쓴다. `checkIns(String)` 은 남긴다(시험·표본 하네스가 쓴다). 스냅샷 모드는 지금처럼 빈 맵

### §3-13 `7-32` 출력 경로 겹침(A-12)
- 앞 표가 차지한 경로(소문자로 비교 — 대소문자 안 가리는 파일 시스템)를 뒤 표가 내면 그 표를 통째 건너뛰고 경고 `<표>: 출력 경로가 <앞 표> 와 겹친다(<rel>) — 건너뜀`. 앞 표는 쓴다
- 겹침은 계획 단계에서 `IOException` 으로 던져 기존 catch(`:94`)가 받는다 — `run` 에 분기를 안 더한다

### §3-14 `2-41`~`2-43` 산출물(B-01 · B-02 · B-07 · B-12)
- B-01 — 새 클래스 `core/report/FormulaRange.widen` — 밀린 행의 수식에서 같은 시트 범위(`AreaPtg`)의 끝 행이 데이터 첫 행이면 채운 끝 행으로. 고친 수식이 있으면 `setForceFormulaRecalculation(true)`. 단일 칸 참조·다른 시트 참조·못 읽는 수식은 그대로(javadoc)
- B-02 — 같은 이름(대문자)이 스키마 둘 이상에 있는 표는 추정에서 뺀다(강·약·이름 기반 전부). `declared` 는 전 스키마 표로 만든다. 조인에 나온 그런 이름을 `Result.notes` 한 줄로 → 작성안내 요약 시트
- B-07 — 단일 컬럼 PK 이름을 표 31 개 이상이 같이 쓰면(`CROWD = 30`) 그 이름은 이름 기반 후보에서 빼고 `notes` 한 줄. 「관계 후보」 시트는 20,000 행에서 자른다(`truncated`). `XlsxWriter.write` 는 시트를 다 만든 뒤 스트림을 연다(0 바이트 파일 안 남김)
- B-12 — CHECK 조건에서 `'…'` 리터럴을 비운 글에 컬럼 이름을 댄다

### §3-15 `6-44`·`6-45` SqlTables(C-TBL-COMMA · C-TBL-DELALIAS)
- 괄호 층(`Layer`)마다 「표 목록 안」 플래그 — FROM·USING 이 열면 참, 절 끝 낱말(`WHERE GROUP HAVING ORDER UNION INTERSECT EXCEPT MINUS CONNECT START LIMIT OFFSET FETCH FOR WINDOW QUALIFY SET VALUES RETURNING SELECT INTO WHEN OPTION MODEL`)에서 거짓. 괄호 친 표 층은 켤 때 참
- `CLOSED` 상태에서 쉼표를 만났고 그 층이 켜져 있고 표 목록 안이면 표 자리를 다시 연다(R)
- `ON` 은 절 끝이 아니다(조인 조건 안 쉼표는 괄호 안)
- 별칭 — 층마다 `별칭 → 표` 와 그 층의 대상 이름을 모은다. 층이 닫힐 때·문장이 끝날 때, 대상 이름이 그 층의 별칭이면 U·D 글자를 진짜 표로 옮기고 별칭 줄은 그 글자를 뺀다(비면 지운다). 진짜 표의 R 은 남긴다
- `extract` 에 분기를 안 더한다 — `open`·`alias`·`track` 과 새 도우미에 둔다
- `SqlJoins.aliases` 는 안 고친다

### §3-16 `1-97`~`1-99` 스냅샷(F-K8 · F-K9 · F-K21)
- F-K8 — FK 구조 글 뒤에 기본이 아닌 규칙만 ` ON DELETE CASCADE`·` ON UPDATE SET NULL`(null·NO ACTION·RESTRICT 는 안 붙인다 — 옛 스냅샷·벤더 차이가 차이로 안 뜨게). CHECK 는 `CK(<공백 접은 조건>)`
- F-K9 — `SnapshotDiff.index(a, b, ignoreSchema)` 가 두 스냅샷을 같이 본다: 어느 한쪽에서든 이름이 겹치면 양쪽 다 `스키마.이름`. `compare`·`AlterGen.generate` 가 이것을 쓴다. 한 인자 판 `index(schemas, ignoreSchema)` 는 남긴다(시험이 쓴다). 스키마 이름까지 다른 접속 사이의 겹친 이름은 여전히 삭제+추가(한계 — javadoc)
- F-K21 — `reltuples` 0 이하는 모름(null). 실제 0 행인지는 `RowCounter` 가 센다

### §3-17 잔손 여섯
- `0-86`+`0-87` — `LocalFiles.network(String)`(public static, `trim` → `/` 를 `\` 로 → `\\` 시작) 하나. `LocalFiles.check`·`Profile.local`·`MakeTemplates.inside` 가 부른다. `MakeTemplates.inside` 는 있는 가장 가까운 조상의 `toRealPath()` 가 실행 폴더 `toRealPath()` 아래인지도 본다
- `0-94` — `PackageTypes.scan` 이 `StackOverflowError` 도 잡아 「못 푼다」 · `JavaGraph.walked` 의 `parse` 미해결은 `quiet` 중에도 적는다 · `JpaIndex` 직접 시험
- `0-96` — `withoutBlockPasswords:287` 이 주석(group 3)을 남긴다 · `leaked` catch 에 `IllegalArgumentException | IllegalStateException` · `SqlProbe` javadoc 자리(`6-43` 에서 같이)
- `5-115` — ① `?.(`·`<<=`·`>>=`·`>>>=`·태그드 템플릿은 `SIDE_EFFECT` ② `earlyInit` — 같은 파일에 선언된 형식의 `new` · 수신자가 자기 형식 이름인 호출 · 생성자 있는 enum ③ `PiiFix` — java·js 꼴은 주석 뗀 줄로 따옴표를 훑는다
- `5-116`(새 행 — `5-115` 의 ④⑤⑥) — ④ `MarkupFix` 는 `textarea` 만 날글 ⑤ 섞인 줄끝 파일은 고칠 것이 있을 때만 미리보기 줄 · 통일 길에서는 `"줄끝이 섞여 있었다 — 전부 <LF|CRLF> 로 통일했다"` 한 줄 ⑥ 시험 둘
- `6-43` — ① `headCut` 은 따옴표 토막이 통째 머리 낱말이거나, 머리 낱말 바로 뒤에 그 SQL 의 둘째 낱말이 올 때만 ② `names` 는 왼쪽부터 한 번 훑어 `'…'`·`"…"` 를 뗀다 ③ `Acc.add` 가 `StmtId.ok` 를 재고 아니면 `statement` 미해결 ④ javadoc 자리

## 4. 실행 스텝

행마다 끝은 CLAUDE.md 「Task 끝」 순서(분할표 행·이력 → `bash scripts/verify.sh` → `git commit` 따로 → `task-done`). `src/main/java` 를 고치는 행은 시험을 먼저 쓰고 RED 를 본 뒤 고친다. 커밋 제목 `fix: <번호> <무엇>`(시험만이면 `test:`). 줄 번호는 `9fb4548` 기준 — 앞 행이 밀면 메서드 이름으로 찾는다.

공통 사다리
- `SelfCheckTest.mainMatchesBaseline` 빨강 — **오른 값·새 줄**이면 분기를 도우미(같은 클래스 또는 그 행이 지목한 클래스)로 뺀다. **내린 값·사라진 줄**은 `-Dgolden.update=true` 로 그 줄만 갱신. 오르는 것을 받는 행은 `0-94`·`6-43`(`JavaGraph` 길이)뿐
- `golden/` diff — 그 행의 「의도된 골든 변화」 에 적힌 것만 갱신. 그 밖은 고침이 넓다 → 고친다
- 새 시험의 시간 단언은 `assertTimeoutPreemptively`. 시험 글에 실제 사업 이름·SQL 을 안 쓴다(`TB_X`·`T86_*`)

### S0 — 가지 · 설계 문서 · 행 · 번들 표 (**설계 세션이 했다** — 실행 세션은 확인만)
- 설계 세션(Fable)이 플랜 승인 뒤 한다: ① 가지는 `work/2026-11-15c` 그대로(HEAD `9fb4548` = main `bf4dedc` + 닫기 커밋 하나) ② 이 플랜 파일 전체를 `design/71-bundle86.md` 로 ③ `PROGRESS.md` 「번들 85 닫기」 줄의 `(이 커밋)` → `9fb4548` ④ `PLAN.md` 15장 번들 86 행을 채우고(차례는 아래 표 순서, 필요 조건 「표본 폴더 · node · Docker · 여유 RAM 15GB」, **머지 위임**) 번들 87~ 행 요지를 「Important 30 + …」 로 ⑤ 16장 표 끝에 새 행 서른(아래 표, 꼴 `| <번호> | <청크> | **설계 71 §3-n·§4 가 원본**. <요지>. **축**: … **강제 지점**: … | <선행> | 대기 |`) + 대기 행 여섯(`0-86`·`0-87`·`0-94`·`0-96`·`5-115`·`6-43`)의 사실 칸 끝에 ` · **처방**: 설계 71 §3-17` ⑥ `PROGRESS.md` 이력 한 줄 + 「현재 상태」(진행중 = 번들 86 대기) ⑦ `bash scripts/verify.sh`(docs 레인) → `git commit`(따로) `docs: 설계 71 — 번들 86 행·번들 표`
- 실행 세션의 확인: `git log --oneline -1` 이 그 커밋 · `git status` 깨끗 · `ls design/71-bundle86.md` · 8절 「첫 스텝 전에」

| # | 청크 | 요지 | 축 | 강제 지점 | 선행 |
|---|---|---|---|---|---|
| 1-86a | `Column.autoIncrement` 성분 | §3-1. 12번째 성분 · 베끼는 자리 다섯 | 생성 정확도(설계 68 A) | `ColumnTest.copiesKeepAutoIncrement` | S0 |
| 1-86b | 자동증가 수집 · 저장 | §3-1. JDBC `IS_AUTOINCREMENT` · V016 | 〃 | `JdbcMetaSourceTest.autoIncrementIsCollected` · `SnapshotStoreTest.autoIncrementRoundTrips` · `DbTest` 16 | 1-86a |
| 1-87 | 식별자 — 예약어·접기·정확 이름 | §3-2(A-14·A-15) | 〃 | `DdlGenTest.identifiers` | 1-86b |
| 1-88 | MariaDB 리터럴 역슬래시 | §3-3(A-28) | 〃 | `DdlGenTest.mariadbBackslash` · `InsertGenTest.mysqlBackslash` · 실물 `9-8` | 1-87 |
| 1-89 | 변환 DDL — FK 규칙·CHECK·자동증가 | §3-4(A-11·R-A-C1) | 〃 | `DdlGenTest.rulesChecksAuto` · `AlterGenTest.mariadbCommentOnlyKeepsAutoIncrement` | 1-88 |
| 1-90 | 타입·기본값·인덱스 이름 | §3-5(R-A-I1·R-A-I13·A-16·A-09) | 〃 | `DdlGenTest.sizesDefaultsKeysIndexNames` | 1-89 |
| 1-91 | AlterGen — FK 이름·유니크·CHECK | §3-6(R-A-I4·R-A-I5·F-K8 반영 DDL) | 〃 | `AlterGenTest.fallbackFkNames`·`unknownSourceUniqueIsFlagged`·`checkDiff` | 1-90 |
| 1-97 | 비교 — CHECK·FK 규칙 | §3-16(F-K8) | 스냅샷(설계 68 F) | `SnapshotDiffTest.checkAndFkRuleChangesShow` | S0 |
| 1-98 | 비교 — 표 키를 양쪽 같이 | §3-16(F-K9) | 〃 | `SnapshotDiffTest.duplicateNameOnOneSideKeepsPair` · `AlterGenTest.duplicateNameOnOneSide` | 1-97·1-91 |
| 1-99 | PG 행 수 0 은 모름 | §3-16(F-K21) | 〃 | `PostgresMetaSourceTest.zeroStatIsUnknown`(db) | S0 |
| 1-92 | KEY·INDEX·PERIOD 컬럼 | §3-7(A-01) | 생성 정확도 | `DdlReaderTest.keyNamedColumnsAreColumns` | 1-86a |
| 1-93 | 문장 가운데 `SET` 줄 | §3-8(A-30) | 〃 | `DdlReaderTest.setNullInsideStatementIsKept` | 1-92 |
| 1-94 | 같은 이름 표 다른 스키마 | §3-9(A-02) | 〃 | `DdlReaderTest.sameNameInTwoSchemas` | 1-93 |
| 1-95a | ALTER 읽기 — `DdlAlter` | §3-10(A-03) | 〃 | `DdlReaderTest.alterColumns`·`alterUnsupportedIsReported` | 1-94 |
| 1-95b | CREATE 앞 ALTER · INSERT 알림 | §3-10(A-03) | 〃 | `DdlReaderTest.alterBeforeCreate` · `InsertRoutesTest.unreadableIsWarned` | 1-95a |
| 1-96 | 정규식 제곱 시간 | §3-11(A-10) | 〃 | `DdlReaderTest.longRunsAreLinear` · `ValidationTest.longAnyArrayIsLinear`·`manyColumnsWithChecksAreLinear` | 1-95b |
| 4-33 | INSERT 이름 인용 | §3-12(A-06) | 〃 | `InsertGenTest.reservedAndSpacedNamesAreQuoted` | 1-87 |
| 4-34 | INSERT 값 — 키 문자·TINYINT·TIME | §3-12(A-04·A-07) | 〃 | `InsertGenTest.shortKeysAndTimeTypes` | 4-33 |
| 4-35 | CHECK IN 은 그 표 것만 | §3-12(A-05) | 〃 | `InsertGenTest.checkInsFromTable` · `InsertRoutesTest.checkInIsPerTable` | 4-34·1-95b |
| 7-32 | 출력 경로 겹침 | §3-13(A-12) | 〃 | `GeneratorTest.collidingTablesAreSkipped` | S0 |
| 2-41 | 양식 합계 수식 범위 | §3-14(B-01) | 산출물(설계 68 B) | `XlsxFillerTest.totalsFormulaIsWidened` | S0 |
| 2-42 | 관계 — 같은 이름 표·PK 이름 몰림 | §3-14(B-02·B-07) | 〃 | `RelationsTest.sameNameInTwoSchemasIsNotInferred`·`crowdedPkNameIsDropped` · `DeliverableServiceTest.guideCarriesRelationNotes` · `XlsxWriterTest.failedSheetLeavesNoFile` | S0 |
| 2-43 | CHECK 리터럴 안 낱말 | §3-14(B-12) | 〃 | `DefinitionsTest.checkLiteralIsNotAColumn` | 2-42 |
| 6-44 | 쉼표 뒤 표 | §3-15(C-TBL-COMMA) | 분석(설계 68 C) | `SqlTablesTest.golden`(새 사례 열넷) | S0 |
| 6-45 | 별칭 대상 | §3-15(C-TBL-DELALIAS) | 〃 | `SqlTablesTest.golden`(새 사례 다섯) | 6-44 |
| 5-116 | 코드 정리 — 리뷰 Minor ④⑤⑥ | §3-17 | 고침 안전 | `CleanupTest.markupPreIsMarkup`·`htmlLangSkipsRawLine`·`scriptOpenLongTail` · `CleanupRunnerTest.mixedEolOnlyWhenChanged`·`unifyNotesMixed` | 5-115 |
| 9-8 | 실물 DB 왕복 — 규칙·CHECK·자동증가·역슬래시·행 수 | §4 | 4장 | `DbCorpusBase.rulesChecksAutoRoundTrip`(컨테이너 아홉, V-65) | 1-91·1-99·4-35 |
| V-65a | 정적 분석 · corpus 골든 | §4. PMD → `--corpus` · 허용 변화 | 4장 | `verify.sh --corpus` | 코드 행 전부 |
| R-32 | 손 판정 · 처분 표 | §4. compare ㉟ | 4장 | compare ㉟ 절 | V-65a |
| V-65 | `--db` | §4. 컨테이너 아홉 · 허용 변화 | 4장 | `verify.sh --db` | R-32 |

차례 — `S0→1-86a→1-86b→1-87→1-88→1-89→1-90→1-91→1-97→1-98→1-99→1-92→1-93→1-94→1-95a→1-95b→1-96→4-33→4-34→4-35→7-32→2-41→2-42→2-43→6-44→6-45→0-86(+0-87)→0-94→0-96→5-115→5-116→6-43→9-8→V-65a→R-32→V-65`

### 1-86a — `Column.autoIncrement` 성분
- 대상: `core/meta/Column.java` · 베끼는 자리 `core/dialect/MariaMetaSource.java:177` · `core/gen/DdlPull.java:231·403` · `core/gen/DdlReader.java:245` · 새 시험 `core/meta/ColumnTest.java`
- 변경:
  ```java
  public record Column(String name, int ordinal, String nativeType, Integer jdbcType, Long length, Integer precision, Integer scale,
          boolean nullable, String defaultValue, String comment, String domain,
          @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean autoIncrement) {
      /** 자동증가를 모르는 컬럼(DDL 읽기·옛 스냅샷·시험) */
      public Column(String name, int ordinal, String nativeType, Integer jdbcType, Long length, Integer precision, Integer scale,
              boolean nullable, String defaultValue, String comment, String domain) {
          this(name, ordinal, nativeType, jdbcType, length, precision, scale, nullable, defaultValue, comment, domain, false);
      }
      public Column withComment(String value) { … domain, autoIncrement); }
  }
  ```
  javadoc 에 「`autoIncrement` 는 JDBC `IS_AUTOINCREMENT`(1-86). 거짓은 아님·모름 둘 다」. 베끼는 넷은 끝 인자에 `c.autoIncrement()`(변수 이름은 그 자리 것)
- 시험(먼저): `ColumnTest.copiesKeepAutoIncrement` — `withComment` 가 참을 지킨다 · Jackson 왕복(`new ObjectMapper()`): 거짓이면 JSON 에 `autoIncrement` 글자가 없다 · 참이면 `"autoIncrement":true` · 그 성분 없는 JSON 을 읽으면 거짓. RED: 컴파일 실패(성분 없음)
- 의도된 골든 변화: 없음(전부 거짓 → JSON 불변)
- 검증: `bash scripts/mvn.sh -q test -Dtest='ColumnTest,DdlReaderTest,SnapshotDiffTest,SnapshotStoreTest,DefinitionsTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 db + corpus

### 1-86b — 자동증가 수집 · 저장
- 대상: `core/meta/JdbcMetaSource.java`(`loadColumns:121-160`) · `core/meta/SnapshotStore.java`(`:135-153` 넣기 · `:320-330` 읽기) · 새 `src/main/resources/db/migration/V016__column_auto_increment.sql` · 시험 `core/meta/JdbcMetaSourceWildcardTest`(H2 로 수집하는 기존 클래스 — H2 가 아니면 같은 폴더에 H2 메모리 DB 로 `JdbcMetaSourceAutoTest` 를 새로)·`SnapshotStoreTest`·`DbTest`
- 변경:
  ```sql
  -- V016 — 1-86 자동증가(JDBC IS_AUTOINCREMENT). 옛 줄은 FALSE(모름)
  ALTER TABLE snap_column ADD COLUMN auto_increment BOOLEAN DEFAULT FALSE NOT NULL;
  ```
  ```java
  // loadColumns — int ordinal = rs.getInt("ORDINAL_POSITION"); 바로 다음 줄(열 번호 순서 — 23번)
  boolean auto = auto(rs);
  // new Column(… null) → new Column(… null, auto)

  /** 1-86 — 드라이버가 열을 안 주면 거짓 */
  private static boolean auto(ResultSet rs) {
      try {
          return "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"));
      } catch (SQLException e) {
          return false;
      }
  }
  ```
  `SnapshotStore` — INSERT 열 목록·자리표시에 `auto_increment` 를 15번째로(`ps.setBoolean(15, col.autoIncrement())`), SELECT 끝에 `auto_increment` 를 더해 `new Column(…, rs.getString(11), rs.getBoolean(12))`
- 시험(먼저): `autoIncrementIsCollected` — H2 `CREATE TABLE T86 (ID BIGINT AUTO_INCREMENT PRIMARY KEY, NM VARCHAR(10))` 수집 → `ID` 참·`NM` 거짓 · `SnapshotStoreTest.autoIncrementRoundTrips` — 참인 컬럼을 저장·되읽기 → 참 · `DbTest` 의 마이그레이션 수 단언 15 → 16. RED: 수집 거짓 · 되읽기 거짓
- 의도된 골든 변화: H2 로 수집하는 메타 골든(`golden/meta/*.json`)에 `"autoIncrement" : true` 줄이 **더해지는 것만**(표본 표에 자동증가 컬럼이 있을 때). 그 밖 diff 는 사다리. 벤더 골든(`postgres-vendor.json` 등)은 db 시험이라 V-65 에서
- 검증: `bash scripts/mvn.sh -q test -Dtest='JdbcMetaSource*Test,SnapshotStoreTest,DbTest,SnapshotServiceTest,MetaRoutesTest'` → `bash scripts/verify.sh`
- 의존: 1-86a
- 사다리: H2 가 `IS_AUTOINCREMENT` 를 `YES` 로 안 주면(판 차이) 시험 DDL 을 `ID BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY` 로. `migration-immutable` 훅이 막으면 멈춘다(7절)

### 1-87 — 식별자: 예약어 넓힘 · 접기 · 정확 이름
- 대상: `core/gen/DdlGen.java`(`Gen.id:447-457`) · `src/main/resources/gen/ddl-types.yaml:26-30` · `DdlGenTest.java`
- 변경: yaml `reserved` 에 §3-2 낱말을 더한다(알파벳순 유지). `tbl`·`cname`·`id`(`:434-457`)를
  ```java
  String tbl(String name) {
      return (blank(o.schema()) ? "" : quoted(o.schema(), false) + ".") + named(name, exact(name));
  }

  /** 제약·인덱스 이름 — prefix 를 붙인다. 우리가 지은 이름이 섞여 정확 이름으로 안 본다 */
  String cname(String name) {
      return named(name, false);
  }

  /** prefix 를 붙인 표·제약·인덱스 이름. exact 는 prefix 앞 글자로 잰 값 */
  private String named(String name, boolean exact) {
      String n = prefix + name;
      if (oracleLike() && n.length() > 30) {
          warnings.add(n + ": 30자 넘음 — Oracle 12.1 이하는 못 만든다");
      }
      return quoted(n, exact);
  }

  /** 컬럼 이름 — 원본이 준 글자로 정확 이름을 잰다 */
  String id(String name) {
      return quoted(name, exact(name));
  }

  private String quoted(String name, boolean exactName) {
      boolean plain = PLAIN.matcher(name).matches();
      boolean exact = plain && exactName;
      if (plain && !exact && !sp.reserved().contains(name.toUpperCase(Locale.ROOT))) {
          return name;
      }
      warnings.add(name + (exact ? ": 대소문자 섞인 이름 — 따옴표로 감쌌다" : ": 예약어·특수 글자 — 따옴표로 감쌌다"));
      String n = plain && !exact ? fold(name) : name;
      return switch (target) {
          case "mariadb" -> "`" + n.replace("`", "``") + "`";
          case MSSQL -> "[" + n.replace("]", "]]") + "]";
          default -> "\"" + n.replace("\"", "\"\"") + "\"";
      };
  }

  /** 설계 71 §3-2 — 원본 방언이 접는 글자와 다르면 원본에서 따옴표로 만든 이름 */
  private boolean exact(String name) {
      return switch (source) {
          case "oracle", "tibero" -> !name.equals(name.toUpperCase(Locale.ROOT));
          case "postgresql" -> !name.equals(name.toLowerCase(Locale.ROOT));
          default -> false;
      };
  }

  /** 예약어를 감쌀 때 대상이 접는 글자로 — 감싸도 안 감싼 것과 같은 이름 */
  private String fold(String name) {
      return oracleLike() ? name.toUpperCase(Locale.ROOT) : "postgresql".equals(target) ? name.toLowerCase(Locale.ROOT) : name;
  }
  ```
- 시험(먼저): `DdlGenTest.identifiers` — ① 원본 postgresql 컬럼 `userId`·`userid` → pg 대상 `"userId"` 와 `userid`(충돌 없음), 경고에 `userId: 대소문자 섞인 이름` ② 원본 oracle `LEFT`·`FETCH`·`START` → oracle 대상 `"LEFT"`, pg 대상 `"left"`, mariadb 백틱, mssql `[LEFT]` ③ 원본 mssql `ArtistId` → oracle 대상 `ArtistId`(안 감쌈) ④ 원본 oracle `emp_no`(소문자 — 정확 이름) → `"emp_no"` ⑤ `DdlGen.reserved("left")` 참 ⑥ 원본 postgresql 표 `users`(PK 이름 없음)·접두 `G1_` → 표는 `G1_users`, PK 는 `G1_PK_users` — 둘 다 따옴표 없음(접두·지은 이름은 정확 이름이 아니다) ⑦ 원본 postgresql 표 `UserTable` → `"UserTable"`. RED: ①②④⑦
- 의도된 골든 변화: `golden/gen/ddl-postgresql.sql` 의 `"LEVEL"` → `"level"` 한 낱말 · `golden/gen/alter-oracle.sql`·`alter-tibero.sql` 에서 예약어 이름이 대문자로 접히는 줄(있으면) · `golden/quality/*.sql` 에 따옴표가 더해지는 줄(새 예약어와 겹치는 표본 이름이 있을 때). 그 밖은 사다리
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlGenTest,AlterGenTest,QualitySqlTest,DeliverableRoutesTest,MetaRoutesTest'` → `bash scripts/verify.sh`
- 의존: 1-86b

### 1-88 — MariaDB 리터럴 역슬래시
- 대상: `core/gen/DdlGen.java`(`lit:479` · `:250·292`) · `core/gen/AlterGen.java`(`DdlGen.lit` 을 부르는 mariadb 가지 — `:624` 와 `mariadb()` 조건 안의 것만) · `core/gen/InsertGen.java`(`quote:468` · `literal:458` · `:436` · `csvLiteral` 의 `quote` 셋) · `DdlGenTest`·`AlterGenTest`·`InsertGenTest`
- 변경:
  ```java
  // DdlGen
  static String lit(String v) { return lit(v, false); }
  /** A-28 — mariadb 기본 sql_mode 는 \ 가 탈출 글자라 겹친다(NO_BACKSLASH_ESCAPES 는 안 가린다) */
  static String lit(String v, boolean backslash) {
      String s = backslash ? v.replace("\\", "\\\\") : v;
      return "'" + s.replace("'", "''") + "'";
  }
  // InsertGen
  static String quote(String s, String db) { return DdlGen.lit(s, "mysql".equals(db)); }
  static String literal(Col c, Object v, String db) { … return quote(v.toString(), db); }
  ```
  `InsertGen.quote(String)` 한 인자 판은 지운다(부르는 곳 전부 `db` 를 넘긴다)
- 시험(먼저): `mariadbBackslash` — 표 코멘트 `경로 C:\` · 컬럼 코멘트 `a\b'c` → mariadb `COMMENT = '경로 C:\\'`·`COMMENT 'a\\b''c'`, oracle 대상은 `'경로 C:\'` 그대로 · `AlterGenTest` 에 mariadb 표 코멘트 변경 한 줄 · `InsertGenTest.mysqlBackslash` — `fromCsv` 값 `C:\temp\` → mariadb `'C:\\temp\\'`, postgresql `'C:\temp\'` · `literal` 로 부모 값 `a\b`. RED: 겹치지 않음
- 의도된 골든 변화: 없음(골든에 `\` 없음)
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlGenTest,AlterGenTest,InsertGenTest,InsertRoutesTest'` → `bash scripts/verify.sh`. 실물은 `9-8`
- 의존: 1-87

### 1-89 — 변환 DDL: FK 규칙 · CHECK · 자동증가 절
- 대상: `core/gen/DdlGen.java`(`Gen.table`·`column`·`fk`) · `core/gen/AlterGen.java`(`addFk:586-588` · `rule:592-604` 삭제) · `DdlGenTest`·`AlterGenTest`
- 변경(`Gen` 안 새 메서드 — `table()`·`type()`·`defaultOf()` 에 분기를 더하지 않는다):
  ```java
  /** FK 삭제·갱신 규칙 절 — AlterGen 과 같이 쓴다(1-89). 기본 동작은 안 쓴다 */
  String rules(Table t, ForeignKey fk, String name) {
      return rule("DELETE", fk.deleteRule(), t, name) + rule("UPDATE", fk.updateRule(), t, name);
  }
  private String rule(String what, FkRule r, Table t, String name) {
      if (r == null || r == FkRule.NO_ACTION || r == FkRule.RESTRICT) { return ""; }
      if (oracleLike() && ("UPDATE".equals(what) || r == FkRule.SET_DEFAULT)) {
          warnings.add(t.name() + ": FK " + name + " — " + target + " 는 ON " + what + " " + r.label() + " 를 못 쓴다, 뺐다");
          return "";
      }
      return " ON " + what + " " + r.label();
  }
  /** CHECK 한 줄 — 같은 방언이면 문장, 다르면 null(1-89·1-91 이 같이 쓴다) */
  String checkSql(Table t, Check k, int n) {
      if (source.isEmpty() || !source.equals(target)) { return null; }
      return "ALTER TABLE " + tbl(t.name()) + " ADD CONSTRAINT " + cname(nameOr(k.name(), "CK_" + t.name() + "_" + n)) + " CHECK (" + k.condition().strip() + ")";
  }
  void checks(Table t) {            // table() 끝, 유니크 루프 뒤·인덱스 앞에서 checks(t);
      int n = 0; int left = 0;
      for (Check k : t.checks()) {
          n++;
          if (blank(k.condition())) { continue; }
          String sql = checkSql(t, k, n);
          if (sql != null) { sb.append(sql).append(";\n"); continue; }
          left++;
          String c = k.condition().strip().replaceAll("\\s+", " ");
          sb.append("-- [확인] ").append(t.name()).append(" CHECK (").append(c.length() > 80 ? c.substring(0, 80) : c).append(") — 방언이 달라 옮기지 않았다\n");
      }
      if (left > 0) { warnings.add(t.name() + ": CHECK " + left + "개는 방언이 달라 옮기지 않았다 — 주석으로 남겼다"); }
  }
  /** 키 컬럼인가 — PK·유니크·인덱스(1-89·1-90) */
  boolean inKey(Table t, Column c) { … 대소문자 무시로 t.pk()·t.uniques()·t.indexes() 의 컬럼 목록에 c.name() }
  /** 타입 뒤 자동증가 절 */
  String autoHead(Table t, Column c, String type) {
      if (!c.autoIncrement()) { return ""; }
      if (MSSQL.equals(target)) { return " IDENTITY(1,1)"; }
      if ("postgresql".equals(target)) { return type.toUpperCase(Locale.ROOT).endsWith("SERIAL") ? "" : " GENERATED BY DEFAULT AS IDENTITY"; }
      if (oracleLike()) { warnings.add(t.name() + "." + c.name() + ": 자동증가 — " + target + " 는 시퀀스·트리거(12c+ 는 IDENTITY)를 손으로"); }
      return "";
  }
  /** NOT NULL 뒤 자동증가 절 — mariadb 는 키 컬럼만 */
  String autoTail(Table t, Column c) {
      if (!c.autoIncrement() || !mariadb()) { return ""; }
      if (inKey(t, c)) { return " AUTO_INCREMENT"; }
      warnings.add(t.name() + "." + c.name() + ": 자동증가 — 키 컬럼이 아니라 mariadb 가 못 받는다, 뺐다");
      return "";
  }
  ```
  `column()` — `new StringBuilder(id(c.name())).append(' ').append(type).append(autoHead(t, c, type))` · `String def = c.autoIncrement() ? null : defaultOf(t, c, type);` · NOT NULL 뒤 `d.append(autoTail(t, c))`(mariadb COMMENT 앞). `fk()` — `REFERENCES … (…)` 뒤 `.append(rules(t, fk, name))` 를 `;\n` 앞에. `AlterGen.addFk` — `rule(s, …)` 두 줄을 `s.append(g.rules(t, fk, name));` 로, `Run.rule` 삭제
- 시험(먼저): `DdlGenTest.rulesChecksAuto` — 원본=대상 postgresql: 자식 FK `ON DELETE CASCADE`·`ON UPDATE SET NULL` · CHECK `QTY >= 0` → `ADD CONSTRAINT … CHECK (QTY >= 0);` · 자동증가 `int4` → `GENERATED BY DEFAULT AS IDENTITY` · 타입 `serial` 자동증가 → 절 없음·기본값 없음·`nextval` 경고 없음 / 원본 postgresql → oracle: `ON DELETE CASCADE` 있음 · `ON UPDATE` 는 빠지고 경고 · CHECK 는 `-- [확인]` 주석 + 경고 · 자동증가 경고 / → mariadb: PK 자동증가 `NOT NULL AUTO_INCREMENT` · 키 아닌 자동증가는 경고하고 없음 / → mssql: `INT IDENTITY(1,1)`. `AlterGenTest.mariadbCommentOnlyKeepsAutoIncrement` — 코멘트만 바뀐 자동증가 PK → `MODIFY COLUMN … NOT NULL AUTO_INCREMENT COMMENT '…'` · mssql·postgresql·oracle 대상의 타입 변경 문장에 `IDENTITY`·`AUTO_INCREMENT` 글자가 없다. RED: 전부
- 의도된 골든 변화: 없음(골든 픽스처의 FK 는 규칙 null·`NO ACTION`·`CASCADE`(alter 는 이미 냄), CHECK·자동증가 없음). `self-check.txt` — `Run` 메서드 40 → 39(한 줄 갱신, `1-91` 이 다시 40)
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlGenTest,AlterGenTest,SelfCheckTest,DeliverableRoutesTest,MetaRoutesTest'` → `bash scripts/verify.sh`
- 의존: 1-88

### 1-90 — 타입 · 기본값 · 인덱스 이름
- 대상: `core/gen/DdlGen.java`(`nativeWithSize:342-344` · `defaultOf:367-369` · `type:308` · `table:275`) · `DdlGenTest`
- 변경:
  ```java
  private static final long NO_SIZE = 10_485_760L;      // 어느 방언에도 이보다 긴 길이 지정은 없다
  final Set<String> ixNames = new HashSet<>();           // Gen 필드 — 이번 실행에서 낸 인덱스 이름(대문자)

  // nativeWithSize — return raw + "(" + c.length() + ")"; 를
  return sized(raw, c.length());
  /** R-A-I1 — 길이 상한을 넘으면 mssql (MAX), 그 밖은 크기 없이 */
  private String sized(String raw, long len) {
      if (len <= (MSSQL.equals(target) ? 8000 : NO_SIZE)) { return raw + "(" + len + ")"; }
      return MSSQL.equals(target) ? raw + "(MAX)" : raw;
  }
  // defaultOf — if (NUM.matcher(v).matches()) { return v; } 의 return 을
  return bool(v, type);
  /** R-A-I13 — 대상 타입이 BOOLEAN 이면 0·1 을 FALSE·TRUE 로 */
  private static String bool(String v, String type) {
      if (!"BOOLEAN".equalsIgnoreCase(type)) { return v; }
      return "1".equals(v) ? "TRUE" : "0".equals(v) ? "FALSE" : v;
  }
  // type — Long len = c.length(); 를
  Long len = keyLen(t, c, n);
  /** A-16 — 길이 없는 문자 타입이 키 컬럼이면 CLOB 대신 길이를 준다 */
  private Long keyLen(Table t, Column c, String norm) {
      Long len = c.length();
      if (len != null && len > 0 || !STRING_TYPE.equals(types.javaType(c, source.isEmpty() ? null : source)) || !inKey(t, c)) { return len; }
      long n = "UUID".equals(norm) || "UNIQUEIDENTIFIER".equals(norm) ? 36 : 255;
      warnings.add(t.name() + "." + c.name() + ": 길이 없는 문자 타입이 키 컬럼 — 길이 " + n + " 으로 냈다");
      return n;
  }
  // table — cname(nameOr(ix.name(), "IX_" + t.name() + "_" + x)) 를 cname(ixName(t, ix, x))
  /** A-09 — 스키마 단위 이름 공간(oracle·tibero·postgresql)에서 겹치면 표 이름을 넣은 이름으로 */
  private String ixName(Table t, Index ix, int x) {
      String fallback = "IX_" + t.name() + "_" + x;
      String name = nameOr(ix.name(), fallback);
      if (mariadb() || MSSQL.equals(target) || ixNames.add(name.toUpperCase(Locale.ROOT))) { return name; }
      warnings.add(t.name() + ": 인덱스 이름 " + name + " 가 다른 표와 겹쳐 " + fallback + " 로 바꿨다");
      ixNames.add(fallback.toUpperCase(Locale.ROOT));
      return fallback;
  }
  ```
- 시험(먼저): `sizesDefaultsKeysIndexNames` — mssql→mssql `nvarchar` 길이 2147483647 → `nvarchar(MAX)`, 길이 8000 `varchar` → `varchar(8000)` · pg→pg `varchar` 길이 2147483647 → `varchar` · mssql→pg `bit` 기본값 `((1))` → `BOOLEAN DEFAULT TRUE`, `((0))` → `FALSE`, mssql→oracle 은 `DEFAULT 1` · pg→oracle `uuid` PK → `VARCHAR2(36)` + 경고, `text` 유니크 → `VARCHAR2(255)`, 키 아닌 `text` → `CLOB` · mariadb→pg 표 둘의 `idx_name` → 둘째가 `IX_<표>_1` + 경고, mariadb→mariadb 는 그대로. RED: 전부
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlGenTest,AlterGenTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-89

### 1-91 — AlterGen: FK 대체 이름 · 유니크 지우기 · CHECK 차이
- 대상: `core/gen/AlterGen.java`(`addFk:576` · `uniquesAndIndexes:439-446` · `table:264-309` · 새 `Run.checks`) · `AlterGenTest`
- 변경:
  - `:576` — `"FK_" + t.name() + "_" + (fks(t).size())` → `"FK_" + t.name() + "_" + (t.fks().indexOf(fk) + 1)`
  - `uniquesAndIndexes` 는 기준선 19 — 분기를 더하지 않는다. `stmt(dropIdx, "ALTER TABLE " + tbl + " DROP CONSTRAINT " + g.cname(name));` 를 `dropUnique(tbl, g.cname(name));` 로 바꾸고 `Run` 에
    ```java
    /** R-A-I5 — 방언을 모르는 스냅샷의 유니크는 인덱스일 수 있다 */
    void dropUnique(String tbl, String name) {
        if (DdlGen.blank(o.source())) {
            check(dropIdx, Kind.COMPAT, "유니크 " + name + " — 제약인지 인덱스인지 스냅샷이 모른다(방언 모름). DROP CONSTRAINT 가 실패하면 DROP INDEX");
        }
        stmt(dropIdx, "ALTER TABLE " + tbl + " DROP CONSTRAINT " + name);
    }
    ```
  - **`Run` 메서드 수 셈(기준선 「메서드 40」 정확 일치)** — `1-89` 뒤 39 → 이 행에서 `dropUnique` +1 · `checks` +1 · `Run` 끝의 `static String nullIfBlank(String s)`(`:746-748`, 부르는 곳 둘)를 **바깥 클래스 `AlterGen` 의 `private static`** 으로 옮겨 −1 = **40**. 옮겨도 `Run` 안 호출 글자는 그대로다(바깥 static 이 보인다)
  - `table()` — `primaryKey(out, g, tbl, ta, tb);` 다음 줄에 `checks(out, g, tbl, ta, tb);`
    ```java
    /** F-K8 — CHECK 차이. 조건(공백 접음)으로 짝짓는다 */
    void checks(StringBuilder out, DdlGen.Gen g, String tbl, Table ta, Table tb) {
        Map<String, Check> ca = checkMap(ta);       // 열쇠 = 조건.strip().replaceAll("\\s+", " ") — 정적 도우미는 AlterGen 바깥 클래스에 static 으로
        Map<String, Check> cb = checkMap(tb);
        ca.forEach((k, c) -> {
            if (!cb.containsKey(k)) {
                String name = DdlGen.nameOr(c.name(), null);
                if (name == null) { hand(out, ta.name() + " CHECK 이름을 모른다 — CK(" + k + ")"); }
                else { stmt(out, "ALTER TABLE " + tbl + " DROP CONSTRAINT " + g.cname(name)); }
            }
        });
        int[] n = {ta.checks().size()};
        cb.forEach((k, c) -> {
            if (!ca.containsKey(k)) {
                String sql = g.checkSql(tb, c, ++n[0]);
                if (sql == null) { hand(out, ta.name() + " CHECK (" + k + ") — 방언이 달라 손으로"); }
                else { stmt(out, sql); }
            }
        });
    }
    ```
    `checkMap` 은 `AlterGen`(바깥 클래스)의 `private static` — `Run` 메서드 수에 안 든다. 빈 조건은 뺀다
- 시험(먼저): `fallbackFkNames` — 이름 없는 FK 둘을 가진 새 표 → `FK_<표>_1`·`FK_<표>_2` · `unknownSourceUniqueIsFlagged` — `Options` 원본 `""`·대상 `postgresql`, 유니크 사라짐 → `-- [확인:호환] 유니크 …` 줄 + `DROP CONSTRAINT`, `review` 1 / 원본 `postgresql` 이면 표식 없음 · `checkDiff` — 원본=대상 postgresql: CHECK 생김 → `ADD CONSTRAINT … CHECK`, 사라짐 → `DROP CONSTRAINT`, 조건 바뀜 → 둘 다, 공백만 다른 조건 → 문장 없음 / 대상이 다르면 `-- [손]`. RED: 전부
- 의도된 골든 변화: `golden/gen/alter-*.sql` diff 0(픽스처에 CHECK 없음·FK 이름 있음). `self-check.txt` `Run` 메서드 39 → 40
- 검증: `bash scripts/mvn.sh -q test -Dtest='AlterGenTest,DdlGenTest,SelfCheckTest,MetaRoutesTest'` → `bash scripts/verify.sh`
- 의존: 1-90
- 사다리: `SelfCheckTest` 가 `Run` 메서드 수로 빨강이면 실제 수를 세어 40 이 되게 `Run` 의 static 도우미(`nz` → `norm` → `up` 순)를 하나 더 바깥 `AlterGen` 으로 옮긴다(호출 글자 불변). 39 로 내려가 있으면 방금 옮긴 것을 되돌린다

### 1-97 — 비교: CHECK · FK 규칙
- 대상: `core/meta/SnapshotDiff.java`(`constraints:137-150` · 클래스 javadoc) · `SnapshotDiffTest`
- 변경:
  ```java
  /** 구조 문자열 — 이름 없이. PK(A,B) / FK(A)->[스키마.]T(X)[ ON DELETE 규칙][ ON UPDATE 규칙] / UQ(A) / CK(조건) */
  for (ForeignKey fk : t.fks()) {
      …
      out.add("FK(" + … + ")" + rule(" ON DELETE ", fk.deleteRule()) + rule(" ON UPDATE ", fk.updateRule()));
  }
  for (Check k : t.checks()) {
      if (k.condition() != null && !k.condition().isBlank()) {
          out.add("CK(" + k.condition().strip().replaceAll("\\s+", " ") + ")");
      }
  }
  /** 기본 동작(모름·NO ACTION·RESTRICT)은 안 적는다 — 옛 스냅샷·벤더 차이가 차이로 안 뜬다 */
  private static String rule(String what, FkRule r) {
      return r == null || r == FkRule.NO_ACTION || r == FkRule.RESTRICT ? "" : what + r.label();
  }
  ```
  클래스 javadoc 「제약은 구조로」 줄에 「FK 는 기본이 아닌 규칙까지, CHECK 는 조건 글로(1-97). 자동증가는 안 본다」
- 시험(먼저): `checkAndFkRuleChangesShow` — FK 규칙 CASCADE → NO ACTION: `removedConstraints` 에 `… ON DELETE CASCADE`, `addedConstraints` 에 규칙 없는 글 · NO ACTION ↔ RESTRICT ↔ null 은 `isEmpty` · CHECK 더함·지움·조건 바뀜 → 각각 `CK(…)` · 공백만 다른 조건은 차이 없음. RED: 전부 `isEmpty`
- 의도된 골든 변화: `golden/meta/diff-*.json` diff 0(픽스처 규칙 `NO ACTION`, CHECK 없음)
- 검증: `bash scripts/mvn.sh -q test -Dtest='SnapshotDiffTest,MetaRoutesTest,AlterGenTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 db + corpus

### 1-98 — 비교: 표 키를 양쪽 같이
- 대상: `core/meta/SnapshotDiff.java`(`compare:62-63` · `index:153-166`) · `core/gen/AlterGen.java`(`generate:60-68`) · `SnapshotDiffTest`·`AlterGenTest`
- 변경:
  ```java
  /** 두 스냅샷의 표 키(1-98) — 어느 한쪽에서든 이름이 겹치면 양쪽 다 「스키마.이름」. 스키마 이름까지 다른 접속 사이의 겹친 이름은 못 짝짓는다(삭제+추가) */
  public record Keyed(Map<String, Table> a, Map<String, Table> b) {
  }

  public static Keyed index(List<Schema> a, List<Schema> b, boolean ignoreSchema) {
      Set<String> twice = new HashSet<>(twice(a));
      twice.addAll(twice(b));
      return new Keyed(keyed(a, ignoreSchema, twice), keyed(b, ignoreSchema, twice));
  }

  /** 한 스냅샷만 — 그 안에서 겹친 이름만 가른다(시험·표본 하네스) */
  public static Map<String, Table> index(List<Schema> schemas, boolean ignoreSchema) {
      return keyed(schemas, ignoreSchema, twice(schemas));
  }

  private static Set<String> twice(List<Schema> schemas) { … 이름별 수 > 1 }
  private static Map<String, Table> keyed(List<Schema> schemas, boolean ignoreSchema, Set<String> twice) {
      … boolean plain = ignoreSchema && !twice.contains(t.name()); out.put(plain ? t.name() : t.schema() + "." + t.name(), t);
  }
  ```
  `compare` — `Keyed k = index(a, b, ignoreSchema); Map<String, Table> ma = k.a(); Map<String, Table> mb = k.b();`. `AlterGen.generate:68-69` — `SnapshotDiff.Keyed k = SnapshotDiff.index(from, to, o.ignoreSchema());` 뒤 `return new Run(sp, o, target, DdlGen.lower(o.source()), types, from).run(k.a(), k.b());`
- 시험(먼저): `duplicateNameOnOneSideKeepsPair` — a `APP.T1` / b `APP.T1`·`APP_BAK.T1`, `ignoreSchema=true` → `addedTables=[APP_BAK.T1]`·`removedTables=[]`·`changedTables=[]` · 양쪽 다 한 스키마면 키가 맨 이름 그대로(기존 골든). `AlterGenTest.duplicateNameOnOneSide` — 같은 입력 → 머리 줄 `표 추가 1 · 삭제 0`, `CREATE TABLE` 한 번. RED: 추가 2·삭제 1
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='SnapshotDiffTest,AlterGenTest,MetaRoutesTest'` → `bash scripts/verify.sh`
- 의존: 1-97 · 1-91

### 1-99 — PG 행 수 0 은 모름
- 대상: `core/dialect/PostgresMetaSource.java`(`:86` · 클래스 javadoc `:14-15`) · `core/dialect/PostgresMetaSourceTest.java`(db 꼬리표, PG 17)
- 변경: `rows.put(rs.getString(1), n == null || n <= 0 ? null : n);` · javadoc 「행수는 pg_class.reltuples — 0 이하는 모름(null): 13 이하는 분석 전이 0, 14+ 는 -1. 실제 0 행인지는 RowCounter 가 센다(1-99)」
- 시험(먼저): `zeroStatIsUnknown` — 컨테이너에 `CREATE TABLE t99 (id INT)` → `ANALYZE t99`(reltuples 0) → 세 행 INSERT(분석 안 함) → `collect` → `t99` 의 `rowCount` 가 null · `Scope(…, skipEmpty=true)` 로 `collect` 해도 `t99` 가 남는다. 끝에 `DROP TABLE t99`(같은 클래스의 `vendorCollectMatchesGolden` 이 표 8 개를 단언한다 — 그 시험보다 먼저 돌아도 표가 안 남게 `finally`). RED: `rowCount` 0 · skipEmpty 에서 사라짐
- 의도된 골든 변화: 없음(`postgres-vendor.json` 은 전부 null)
- 검증: Docker 가 켜져 있으면 `bash scripts/mvn.sh -q test -Dgroups=db -Dtest='PostgresMetaSourceTest,VendorFallbackTest'`, 꺼져 있으면 `VendorFallbackTest` 만 돌리고 이력에 「RED·GREEN 은 V-65」 → `bash scripts/verify.sh`
- 의존: S0

### 1-92 — KEY · INDEX · PERIOD 로 시작하는 컬럼
- 대상: `core/gen/DdlPull.java`(새 `constraintLine` · `:188`) · `core/gen/DdlReader.java`(`item:318-319`) · `DdlReaderTest`
- 변경:
  ```java
  // DdlPull
  private static final Pattern KEY_SHAPE = Pattern.compile("(?i)^(?:KEY|INDEX)\\s*+(?:[^\\s(]++\\s*+)?(?:USING\\s++\\w++\\s*+)?\\(\\s*+[^\\d\\s)']");
  private static final Pattern PERIOD_FOR = Pattern.compile("(?i)^PERIOD\\s++FOR\\b");

  /** A-01 — 제약 줄인가. KEY·INDEX·PERIOD 로 시작해도 뒤가 타입 꼴(괄호 없음·괄호 안이 숫자·따옴표)이면 컬럼이다 */
  static boolean constraintLine(String item) {
      Matcher m = DdlReader.CONSTRAINT_LINE.matcher(item);
      if (!m.find()) { return false; }
      String w = m.group(1).toUpperCase(Locale.ROOT);
      if ("PERIOD".equals(w)) { return PERIOD_FOR.matcher(item).lookingAt(); }
      return !"KEY".equals(w) && !"INDEX".equals(w) || KEY_SHAPE.matcher(item).lookingAt();
  }
  ```
  `DdlReader.item` — `Matcher cm = CONSTRAINT_LINE.matcher(item); if (cm.find()) {` → `if (DdlPull.constraintLine(item)) {`(분기 수 그대로). `DdlPull:188` 도 같은 호출로
- 시험(먼저): `keyNamedColumnsAreColumns` — `CREATE TABLE conf (id INT, KEY VARCHAR(50), PERIOD INT, INDEX INT NOT NULL, KEY2 INT, KEY ENUM('a','b'), KEY ix_a (id), INDEX ix_b (id) USING BTREE, PERIOD FOR SYSTEM_TIME (s, e))` → 컬럼 `id·KEY·PERIOD·INDEX·KEY2·KEY`(이름 그대로 여섯), 인덱스 둘(`ix_a`·`ix_b`), `VARCHAR` 라는 인덱스 없음, `unreadable` 빔. 기존 `readsCreateIndexAndInlineKey`·`indexHeadsAndColumnDecorations`·`inlineUniqueAndSpatialAreNotIndexes` 초록. RED: 컬럼 셋 빠짐 + 가짜 인덱스
- 의도된 골든 변화: 없음(V-65a 에서 `ddl-lint.json` 의 컬럼 수가 느는 것은 허용)
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,DtoGeneratorTest,SelfCheckTest,GenRoutesTest'` → `bash scripts/verify.sh`
- 의존: 1-86a

### 1-93 — 문장 가운데 `SET` 줄
- 대상: `core/gen/DdlReader.java`(`stripSqlPlus:714-735`) · `DdlReaderTest`
- 변경:
  ```java
  private static final Pattern SET_LINE = Pattern.compile("(?i)^[ \\t]*SET\\b");

  static String stripSqlPlus(String s) {
      String[] lines = s.split("\n", -1);
      int depth = 0;
      boolean q = false;
      boolean open = false;                        // A-30 — 끝난 뒤로 안 지운 줄이 있다(문장이 열려 있다)
      for (int i = 0; i < lines.length; i++) {
          if (depth == 0 && !q && SQLPLUS.matcher(lines[i]).find() && !(open && SET_LINE.matcher(lines[i]).find())) {
              lines[i] = "";
              continue;
          }
          … 지금 글자 루프 …
          open = stillOpen(lines[i], open);
      }
      return String.join("\n", lines);
  }
  ```
  `stillOpen` 은 `DdlReader` 메서드가 30(상한)이라 **`DdlPull` 에** `static boolean stillOpen(String line, boolean open)`: `String t = line.strip();` 빈 줄이면 `open` 그대로 · `t.endsWith(";")`·`"/".equals(t)`·`"GO".equalsIgnoreCase(t)` 면 거짓 · 그 밖은 참
- 시험(먼저): `setNullInsideStatementIsKept` — `CREATE TABLE p (id INT PRIMARY KEY);`⏎`CREATE TABLE c (id INT, pid INT);`⏎`ALTER TABLE c ADD CONSTRAINT fk FOREIGN KEY (pid) REFERENCES p (id) ON DELETE`⏎`SET NULL;`⏎`CREATE TABLE z (id INT);` → 표 셋(p·c·z), c 의 FK 하나, `unreadable` 빔 · 문장 사이 `SET NULL ''`·`SET ECHO ON` 줄은 지금처럼 지워진다(`CREATE …;`⏎`SET NULL ''`⏎`CREATE …;` → 표 둘) · 기존 `sqlPlusCommandLinesAreSkipped`·`sqlPlusWordsInsideTableAreColumns`·`buriedCreateIsReportedNotDropped` 초록. RED: z 없음·FK 0
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-92
- 건드리지 않는다: 시험 쪽 사본 `src/test/java/kr/ejg/toolbox/DbCorpus.java:57-115`

### 1-94 — 같은 이름 표, 다른 스키마
- 대상: `core/gen/DdlPull.java`(새 `slot`·`find`) · `core/gen/DdlReader.java`(`:193·292` 넣기 · `:207·212·439` 찾기) · `DdlReaderTest`
- 변경:
  ```java
  /** A-02 — 넣을 자리: 맨 이름. 그 자리에 스키마가 다른 표가 이미 있으면 「스키마.이름」 */
  static String slot(Map<String, DdlReader.Draft> tables, DdlReader.Draft d) {
      String bare = d.name.toUpperCase(Locale.ROOT);
      DdlReader.Draft had = tables.get(bare);
      return had == null || had.schema.equalsIgnoreCase(d.schema) ? bare : (d.schema + "." + d.name).toUpperCase(Locale.ROOT);
  }

  /** 이름 마디로 표 찾기 — 스키마가 있으면 「스키마.이름」 자리 먼저, 없으면 맨 이름 */
  static DdlReader.Draft find(Map<String, DdlReader.Draft> tables, List<String> parts) {
      String bare = parts.get(parts.size() - 1).toUpperCase(Locale.ROOT);
      DdlReader.Draft q = parts.size() > 1 ? tables.get((parts.get(parts.size() - 2) + "." + bare).toUpperCase(Locale.ROOT)) : null;
      return q != null ? q : tables.get(bare);
  }
  ```
  넣기 둘 — `tables.put(DdlPull.slot(tables, d), d);`. 찾기 — `:207`(COMMENT ON TABLE)·`:439`(CREATE INDEX ON)은 `DdlPull.find(tables, <그 이름의 nameParts>)`, `:212`(COMMENT ON COLUMN)는 마지막 마디(컬럼)를 뺀 `parts.subList(0, parts.size() - 1)` 로. `:227`(ALTER)은 `1-95a` 가 옮긴다 — 이 행에서는 같은 `find` 로만 바꾼다. `Draft.schema` 가 null 일 수 있으면 `slot` 앞에서 `""` 로(생성자 `:181·288` 은 `""` 를 준다 — 확인만). `Result.notes` 열쇠(`:253`)와 `DdlPull` 안 찾기(`:131·146·322`)는 그대로 — `DdlReader.read` javadoc 에 「같은 이름 둘째 표의 notes·LIKE 원본은 첫 표 것을 본다(한계)」 한 줄
- 시험(먼저): `sameNameInTwoSchemas` — `CREATE TABLE s1.emp (id INT); CREATE TABLE s2.emp (id INT, nm VARCHAR(10)); COMMENT ON TABLE s2.emp IS '둘째'; ALTER TABLE s2.emp ADD CONSTRAINT uq UNIQUE (nm);` → 표 둘(`s1.emp` 컬럼 1·`s2.emp` 컬럼 2), 코멘트·유니크는 `s2.emp` 에만 · 같은 스키마·같은 이름 두 번은 지금처럼 뒤 것 하나. RED: 표 하나
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,InsertGenTest,GenRoutesTest,InsertRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-93

### 1-95a — ALTER 읽기: `DdlAlter`
- 대상: 새 `core/gen/DdlAlter.java` · `core/gen/DdlReader.java`(`read:224-237` 을 호출 한 줄로 · `ALTER_TABLE:87-88` 삭제 · `private static` 인 `alterPk:485`·`uq:535` 와 상수 `FK`·`UQ`·`QUOTED` 를 package-private 로 — `fk:541`·`checks:520`·`matching:615`·`splitTop`·`nameParts`·`unquote`·`item`·`Draft(schema, name)` 은 이미 package-private) · `DdlReaderTest` · `golden/check/self-check.txt`(내린 줄)
- 변경: `read` 루프의 `Matcher at = ALTER_TABLE.matcher(stmt); if (at.lookingAt()) { … }` 블록 전체를 `DdlAlter.apply(stmt, tables, unreadable, null);` 한 줄로. 새 클래스:
  ```java
  /** ALTER TABLE 한 문장(1-95, 설계 71 §3-10) — 아는 표만 고친다. 못 읽는 동작은 unreadable 로 */
  final class DdlAlter {
      private static final Pattern HEAD = Pattern.compile("^ALTER\\s++TABLE\\s++(?:IF\\s++EXISTS\\s++)?(?:ONLY\\s++)?(\\S++)\\s++", Pattern.CASE_INSENSITIVE);
      private static final Pattern ADD = Pattern.compile("^(?:WITH\\s++(?:NO)?CHECK\\s++)?ADD\\b\\s*+", Pattern.CASE_INSENSITIVE);
      private static final Pattern COLUMN_WORD = Pattern.compile("^COLUMN\\b\\s*+(?:IF\\s++(?:NOT\\s++)?EXISTS\\s++)?", Pattern.CASE_INSENSITIVE);
      private static final Pattern DROP = Pattern.compile("^DROP\\b\\s*+", Pattern.CASE_INSENSITIVE);
      private static final Pattern DROP_OTHER = Pattern.compile("^(?:CONSTRAINT|PRIMARY|FOREIGN|INDEX|KEY|PARTITION|CHECK|DEFAULT|PERIOD|UNIQUE)\\b", Pattern.CASE_INSENSITIVE);
      private static final Pattern REDEFINE = Pattern.compile("^(MODIFY|CHANGE)\\b\\s*+", Pattern.CASE_INSENSITIVE);
      private static final Pattern COMMENT = Pattern.compile("^COMMENT\\s*+=?\\s*+'((?:[^']|'')*+)'$", Pattern.CASE_INSENSITIVE);
      private static final Pattern HARMLESS = Pattern.compile("^(?:OWNER\\s++TO|ENABLE|DISABLE|(?:NO)?CHECK\\s++CONSTRAINT|WITH\\s++(?:NO)?CHECK\\s++CHECK\\s++CONSTRAINT"
              + "|SET\\s++(?:TABLESPACE|SCHEMA|LOGGED|UNLOGGED|WITH|WITHOUT|\\()|CLUSTER\\s++ON|REPLICA\\s++IDENTITY|ATTACH\\s++PARTITION|DETACH\\s++PARTITION"
              + "|AUTO_INCREMENT|ENGINE|(?:DEFAULT\\s++)?(?:CHARSET|CHARACTER\\s++SET|COLLATE)|CONVERT\\s++TO|ROW_FORMAT|FORCE|(?:NO\\s++)?INHERIT"
              + "|VALIDATE\\s++CONSTRAINT|REBUILD|SWITCH|TRUNCATE\\s++PARTITION|(?:NO)?LOGGING|(?:NO)?PARALLEL|MOVE|(?:NO)?CACHE"
              + "|ALTER\\s++COLUMN\\s++\\S++\\s++SET\\s++(?:STATISTICS|STORAGE))(?![\\w$#])", Pattern.CASE_INSENSITIVE);

      /** ALTER TABLE 이 아니면 거짓. 표를 모르면 later 에 미루고(later 가 null 이면 조용히 넘긴다) 참 */
      static boolean apply(String stmt, Map<String, DdlReader.Draft> tables, List<DdlReader.Unreadable> unreadable, List<String> later)
  }
  ```
  동작(메서드마다 복잡도 15 아래로 — `apply`·`action`·`add`·`drop`·`redefine`·`constraint`·`inner` 로 나눈다):
  1. `HEAD.lookingAt()` 아니면 거짓. `Draft d = DdlPull.find(tables, DdlReader.nameParts(h.group(1)))`. null 이면 `later` 가 있으면 `later.add(stmt)`, 참을 돌려준다
  2. `DdlReader.splitTop(stmt.substring(h.end()), ',')` 의 조각마다 `strip()` — 빈 조각은 건너뛴다. `boolean adding` 을 들고 돈다(ADD 동작 뒤 참, 다른 동사 뒤 거짓)
  3. `ADD` 가 맞으면 뒤 글을 `add(d, 글, unreadable)`. 동사가 하나도 안 맞고 `adding` 이면 조각 통째를 `add` 로
  4. `add` — 글이 `(` 로 시작하고 `DdlReader.matching(글, 0)` 이 글 끝이면 안쪽을 `splitTop(…, ',')` 로, 아니면 글 하나. 항목마다: `COLUMN_WORD` 머리를 뗀 뒤 `DdlPull.constraintLine(항목)` 이면 `constraint(d, 항목)`, 아니면 `DdlReader.item(d, 항목, unreadable)`
  5. `constraint` — 지금 `read:228-236` 그대로를 항목 글에: `FK` 를 `find` 로 돌며 `d.fks.add(fk(…))` · `UQ.matcher(QUOTED.matcher(항목).replaceAll("''"))` 를 돌며 `d.uqs.add(uq(u))` · `DdlReader.alterPk(d, 항목)` · **더해서** `DdlReader.checks(d, 항목)`
  6. `DROP` — 뒤 글이 `DROP_OTHER` 면 못 읽음. 아니면 `COLUMN_WORD` 머리를 떼고, `(`…`)` 면 안쪽을 쉼표로, 아니면 하나: 이름(`unquote`, 뒤 `CASCADE`·`RESTRICT` 낱말은 뗀다)마다 `d.cols` 에서 대소문자 무시로 찾아 지우고 `d.pk` 에서도 뺀다. 못 찾으면 못 읽음
  7. `REDEFINE` — 뒤 글의 `COLUMN_WORD` 머리를 떼고 `(`…`)` 면 안쪽 항목들, 아니면 하나. `CHANGE` 면 항목의 첫 토큰이 옛 이름·나머지가 정의, `MODIFY` 면 항목이 정의·옛 이름은 그 첫 토큰. `Draft s = new Draft(d.schema, d.name); s.scope = d.scope; List<Unreadable> u = new ArrayList<>(); DdlReader.item(s, 정의, u);` — `u` 가 비었고 `s.cols.size() == 1` 이고 그 컬럼의 `nativeType()` 이 null 이 아니면 `d.cols` 의 옛 이름 자리에 `new Column(c.name(), old.ordinal(), c.nativeType(), c.jdbcType(), c.length(), c.precision(), c.scale(), c.nullable(), c.defaultValue(), c.comment() != null ? c.comment() : old.comment(), c.domain(), c.autoIncrement())` 를 넣고 `d.uqs.addAll(s.uqs); d.checks.addAll(s.checks); d.fks.addAll(s.fks);` · `s.pk` 가 안 비었으면 `d.pk.clear(); d.pk.addAll(s.pk);`(`pk` 는 final 목록). 그 밖(옛 이름 없음 포함)은 못 읽음
  8. `COMMENT` 가 맞으면 `d.comment = 글.replace("''", "'")`. `HARMLESS.lookingAt()` 이면 아무것도 안 한다
  9. 그 밖 — 못 읽음: `unreadable.add(new DdlReader.Unreadable(d.name, cut("ALTER TABLE " + d.name + " " + 조각.replaceAll("\\s+", " "), 80)))`
- 시험(먼저): `alterColumns` — `CREATE TABLE t (id INT PRIMARY KEY, a INT, old_nm VARCHAR(10));` 뒤 `ALTER TABLE t ADD c1 INT;` · `ALTER TABLE t ADD COLUMN c2 VARCHAR(20) NOT NULL;` · `ALTER TABLE t ADD (c3 NUMBER(5), c4 DATE);` · `ALTER TABLE t ADD c5 INT, c6 INT;` · `ALTER TABLE t DROP COLUMN a;` · `ALTER TABLE t MODIFY c1 BIGINT NOT NULL;` · `ALTER TABLE t CHANGE old_nm nm VARCHAR(50) COMMENT '이름';` · `ALTER TABLE t ADD CONSTRAINT ck CHECK (c1 > 0);` · `ALTER TABLE t COMMENT = '표';` · `ALTER TABLE t OWNER TO x;` · `ALTER TABLE t ADD CONSTRAINT fk FOREIGN KEY (c1) REFERENCES t (id), ADD CONSTRAINT uq UNIQUE (c2);` → 컬럼 차례 `id, nm, c1, c2, c3, c4, c5, c6`(`nm` 은 `old_nm` 자리·`VARCHAR` 50·코멘트 `이름`, `c1` 은 `BIGINT`·NOT NULL), CHECK 하나·FK 하나·유니크 하나, 표 코멘트 `표`, `unreadable` 빔. `alterUnsupportedIsReported` — `ALTER TABLE t ALTER COLUMN a TYPE BIGINT;` · `ALTER TABLE t RENAME COLUMN a TO b;` · `ALTER TABLE t DROP CONSTRAINT ck;` · `ALTER TABLE t MODIFY (a NOT NULL);` · `ALTER TABLE t DROP COLUMN nope;` → `unreadable` 다섯, 전부 `table()` 이 `t`·글이 `ALTER TABLE t ` 로 시작, 표의 컬럼은 그대로 · `ALTER TABLE nowhere ADD x INT;` 는 `unreadable` 에 없다. 기존 `readsAlterForeignKeys`·`readsBracketedForeignKeys`·`alterAddParenthesizedPrimaryKey`·`pkNameFromBodyColumnAndAlter` 초록. RED: 컬럼 빠짐·`unreadable` 빔
- 의도된 골든 변화: `self-check.txt` 의 `DdlReader` `read(1)` 복잡도·중첩 줄이 **내린 값으로**(중첩이 4 이하가 되면 그 줄은 사라진다)
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,DtoGeneratorTest,InsertGenTest,SelfCheckTest,GenRoutesTest,InsertRoutesTest'` → `bash scripts/verify.sh`
- 의존: 1-94

### 1-95b — CREATE 앞 ALTER · INSERT 화면 알림
- 대상: `core/gen/DdlReader.java`(`read`) · `web/InsertRoutes.java`(`:72-78` · `body:126-129`) · `DdlReaderTest`·`InsertRoutesTest`
- 변경: `read` 머리에 `List<String> later = new ArrayList<>();`, 루프의 호출을 `DdlAlter.apply(stmt, tables, unreadable, later);`, 루프 뒤(표를 만들기 앞)에 `later.forEach(s -> DdlAlter.apply(s, tables, unreadable, null));`. `InsertRoutes` DDL 모드 — `DdlReader.Result parsed = DdlReader.read(req.ddl());` 로 받아 `tables()` 를 쓰고, 고른 표가 정해진 뒤 `long missed = parsed.unreadable().stream().filter(u -> u.table().equalsIgnoreCase(<고른 표 이름>)).count();` — 0 보다 크면 응답 `warnings` 맨 앞에 `"DDL 에서 못 읽은 줄 " + missed + "개 — 이 표의 컬럼·제약이 빠졌을 수 있다"`(`body(…)` 가 만든 맵의 `warnings` 목록을 새 목록으로 바꿔 끼운다 — `InsertGen.Result` 는 안 고친다). 람다 분기가 `register` 복잡도에 세이면 `private static` 도우미로 뺀다(작성 규약)
- 시험(먼저): `alterBeforeCreate` — `ALTER TABLE t ADD c INT; ALTER TABLE t ADD CONSTRAINT uq UNIQUE (c); CREATE TABLE t (id INT);` → 컬럼 `id, c`·유니크 하나 · 끝까지 CREATE 가 없는 표의 ALTER 는 `unreadable` 에 없다. `InsertRoutesTest.unreadableIsWarned` — DDL `CREATE TABLE t (id INT); ALTER TABLE t RENAME COLUMN id TO no;` → 응답 `warnings` 첫 줄이 `DDL 에서 못 읽은 줄 1개` 로 시작 · 못 읽은 줄이 없으면 그 글이 없다. RED: 컬럼 `c` 없음 · 경고 없음
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,InsertRoutesTest,SelfCheckTest,SmokeHtmlUnitTest'` → `bash scripts/verify.sh`
- 의존: 1-95a

### 1-96 — 정규식 제곱 시간
- 대상: `core/gen/DdlReader.java`(`TYPE_END:110-113` · `INDEX_SORT:84` · `:371` · `:589·607` · `:662`) · `core/gen/DdlPull.java`(`JOIN_SEP:47` · 새 `dropParens`) · `core/gen/Validation.java`(`ANY_ARRAY:65` · `columnsIn` · `of` 의 호출 한 줄) · `DdlReaderTest`·`ValidationTest`
- 변경:
  - `TYPE_END` — 맨 앞 `\\s+` → `(?<!\\s)\\s++`, 안의 `NOT\\s+NULL`·`PRIMARY\\s+KEY`·`CHARACTER\\s+SET`·`ON\\s+UPDATE` 의 `\\s+` → `\\s++`
  - `INDEX_SORT` → `"(?i)(?:(?<!\\s)\\s++(?:ASC|DESC))?(?:(?<!\\s)\\s++NULLS\\s++(?:FIRST|LAST))?$"`
  - `:589`·`:607` 의 `replaceAll("\\s*\\.\\s*", ".")` → 상수 `private static final Pattern DOT = Pattern.compile("(?<!\\s)\\s*+\\.\\s*+");` 의 `DOT.matcher(x).replaceAll(".")`
  - `:662` `"(?im)^\\s*GO\\s*$"` → `"(?im)^[ \\t]*+GO[ \\t\\r]*+$"`
  - `:371` `type.replaceAll("\\([^)]*\\)", " ")` → `DdlPull.dropParens(type)`
    ```java
    /** A-10 — 「(」 부터 다음 「)」 까지를 공백으로. 닫는 괄호가 없으면 거기서 멈춘다(정규식 \([^)]*\) 와 같은 결과, 한 번 훑기) */
    static String dropParens(String s) {
        StringBuilder b = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int open = s.indexOf('(', i);
            int close = open < 0 ? -1 : s.indexOf(')', open);
            if (close < 0) {
                break;
            }
            b.append(s, i, open).append(' ');
            i = close + 1;
        }
        return b.append(s, i, s.length()).toString();
    }
    ```
  - `JOIN_SEP` → `"(?i)(?<!\\s)\\s*+,\\s*+|(?<!\\s)\\s++(?:NATURAL\\s++)?(?:(?:INNER|LEFT|RIGHT|FULL|CROSS)\\s++)?(?:OUTER\\s++)?JOIN\\s++"`
  - `ANY_ARRAY` 의 `(.*?)\\]` → `([^\\]]*+)\\]`
  - `Validation` — `static List<String> columnsIn(String condition, Table t, Column c)`: 조건(리터럴 비운 글)에 `c.name()` 이 낱말로 없으면 `List.of()`, 있으면 `columnsIn(condition, t)`. `of` 의 `List<String> cols = columnsIn(cond, t);` 를 `columnsIn(cond, t, c)` 로(분기 수 그대로)
- 시험(먼저): `DdlReaderTest.longRunsAreLinear` — 각각 5초 안: ① `"CREATE TABLE t (a" + " ".repeat(200_000) + "x INT)"` ② `"CREATE TABLE t (a INT" + "(".repeat(200_000) + ")"` ③ `"\n".repeat(200_000) + "CREATE TABLE t (a INT);"` ④ `"CREATE TABLE s" + " ".repeat(200_000) + "t (a INT)"` ⑤ `"CREATE TABLE t (a INT); CREATE INDEX i ON t (a" + " ".repeat(200_000) + "x)"` ⑥ `"CREATE TABLE t AS SELECT a FROM x" + " ".repeat(200_000) + "y"`. 결과 단언은 「예외 없이 끝난다」 + ③ 은 표 하나. `ValidationTest.longAnyArrayIsLinear` — `"x = ANY (ARRAY[".repeat(20_000)` 의 `normalize` 5초 안 · `manyColumnsWithChecksAreLinear` — 컬럼 5,000(`C<i> INT`) + CHECK 200(`C<i> >= 0`) 표의 `DtoGenerator.generate` 10초 안, `C0` 필드에 `@Min(0)` 이 있다. 기존 `quotedNameWithSpacesAndNoCatastrophicBacktracking`·`ValidationTest` 전부 초록. RED: ①②③⑤ 가운데 하나 이상 시간 초과
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,ValidationTest,DtoGeneratorTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-95b
- 사다리: 여섯 입력 가운데 고친 뒤에도 시간 초과인 것이 있으면 — 그 입력으로 `jstack` 대신 시험 안에서 `DdlReader.read` 를 다른 스레드에 돌리고 3초 뒤 그 스레드의 `getStackTrace()` 맨 위 `kr.ejg` 프레임(파일:줄)을 읽어, 그 줄의 정규식에 같은 처방(`(?<!\s)` + 소유 수량자)을 넣는다. 고치기 둘까지. 그래도 남는 입력은 단언에서 빼지 않고 `wip/1-96`

### 4-33 — INSERT 이름 인용
- 대상: `core/gen/InsertGen.java`(`:430·438` · `buildUpsert:489-560` · `fromCsv:610`) · `InsertGenTest`
- 변경:
  ```java
  private static final Pattern PLAIN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

  /** A-06 — SQL 로 나가는 이름. 예약어·특수 글자만 감싼다(DdlGen.reserved 와 같은 목록). 예약어는 접는 방언이면 접은 글자로 */
  static String ident(String name, String db) {
      boolean plain = PLAIN.matcher(name).matches();
      if (plain && !DdlGen.reserved(name)) {
          return name;
      }
      String n = !plain ? name : "oracle".equals(db) ? name.toUpperCase(Locale.ROOT) : "pg".equals(db) ? name.toLowerCase(Locale.ROOT) : name;
      return switch (db) {
          case "mysql" -> "`" + n.replace("`", "``") + "`";
          case "mssql", "sybase" -> "[" + n.replace("]", "]]") + "]";
          default -> "\"" + n.replace("\"", "\"\"") + "\"";
      };
  }

  /** MERGE·ON CONFLICT 쪽 — 맨 이름은 지금처럼 대문자, 특수 글자 이름은 글자 그대로 */
  private static String uid(String name, String db) {
      return ident(PLAIN.matcher(name).matches() ? name.toUpperCase(Locale.ROOT) : name, db);
  }
  ```
  `generate` — `c.name()`(`:430`)·`t.name()`(`:438`) 을 `ident(…, db)` 로. `fromCsv:610` 의 표·머리 이름도. `buildUpsert` 는 기준선 24 — 분기를 더하지 않는다: `:491` 의 `String tbl = "mysql".equals(db) ? table : table.toUpperCase(Locale.ROOT);` 를 `String tbl = "mysql".equals(db) ? ident(table, db) : uid(table, db);` 로(삼항 그대로 하나), SQL 글에 들어가는 `u(c)` 는 전부 `uid(c.name(), db)` 로 — 경고 글(`:596·620`)과 `#{camel}`(`:427`) 은 그대로
- 시험(먼저): `reservedAndSpacedNamesAreQuoted` — 손으로 만든 표 `Order Details`(컬럼 `ID`·`ORDER`·`Unit Price`, PK `ID`)로 방언마다 `generate`: oracle `INSERT INTO "Order Details"(` + `"ORDER"` + `"Unit Price"`, postgresql `"order"`, mariadb 백틱 셋, mssql 대괄호 · upsert: oracle `MERGE INTO "Order Details" T` 와 `T."ORDER" = S."ORDER"` 꼴, postgresql `ON CONFLICT (ID)` · 예약어가 아닌 표(`products`)의 출력은 지금과 같다(기존 골든). H2 실행 — 기존 `fkValuesAndRunOnH2` 꼴로 `CREATE TABLE "Order Details" ("ID" INT PRIMARY KEY, "ORDER" INT, "Unit Price" INT)` 에 oracle 방언 INSERT 가 들어간다. RED: 따옴표 없음·H2 문법 오류
- 의도된 골든 변화: `golden/insert/*.sql` diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='InsertGenTest,InsertRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-87(`reserved` 목록)

### 4-34 — INSERT 값: 짧은 키 문자 · TINYINT · TIME·YEAR·INTERVAL
- 대상: `core/gen/InsertGen.java`(`genVal:264-274·294` · `domain:356-371`) · `InsertGenTest` · `self-check.txt`(`genVal` 내린 줄)
- 변경: `genVal` 의 `if (CHARISH.matcher(t).matches()) { … }` 몸을 `return charVal(c, idx, sz);` 로 옮기고(몸 그대로 + 한 줄), 끝 `return "'VAL_" + (idx + 1) + "'";` 를 `return other(t, idx, db, base);` 로:
  ```java
  private static String charVal(Col c, int idx, int sz) {
      String seq = pad(idx + 1, 3);
      int lim = sz > 0 ? sz : 30;
      if (lim <= seq.length()) {
          return "'" + seq.substring(seq.length() - lim) + "'";
      }
      if ((c.pk() || c.uq()) && lim - seq.length() - 1 < 1) {
          return "'" + pad(idx + 1, lim) + "'"; // A-04 — 길이 4 키: 이름 한 글자 + 밑줄 + 순번이 잘려 같은 값이 되풀이됐다
      }
      … 지금 :270-273 그대로 …
  }

  /** A-07 — 끝 갈래: TIME·YEAR·INTERVAL, 그 밖은 'VAL_n' */
  private static String other(String t, int idx, String db, LocalDate base) {
      if (t.startsWith("TIME")) {
          return String.format("'%02d:%02d:00'", idx % 24, idx % 60);
      }
      if ("YEAR".equals(t)) {
          return String.valueOf(base.getYear() - idx % 100);
      }
      if (t.startsWith("INTERVAL")) {
          return "oracle".equals(db) ? "INTERVAL '" + (idx + 1) + "' " + (t.contains("YEAR") ? "MONTH" : "DAY") : "'" + (idx + 1) + " days'";
      }
      return "'VAL_" + (idx + 1) + "'";
  }
  ```
  `domain` — 지금 0 을 돌려주는 끝 앞에: `CHARISH` 이고 길이가 1·2 면 `10`·`100`, `"TINYINT".equals(타입)`(불 꼴 길이 1 은 지금 갈래 그대로)이면 `128`. `domain` 이 15 를 넘기면 그 둘을 `private static long small(Col c)` 로
- 시험(먼저): `shortKeysAndTimeTypes` — CHAR(4) PK 12 행 → 값 열두 개가 다 다르다(`'0001'`…`'0012'`) · CHAR(4) 키 아닌 컬럼은 지금 값 그대로 · CHAR(2) PK 150 행 → 경고에 `허용 값이 100개인 키 컬럼` · TINYINT PK 130 행 → 경고에 `허용 값이 128개인 키 컬럼` · TIME → `'00:00:00'`·`'01:01:00'` · YEAR → 수(따옴표 없음) · INTERVAL: postgresql `'1 days'`, oracle `INTERVAL '1' DAY` · `INTERVAL YEAR TO MONTH` oracle `INTERVAL '1' MONTH`. 기존 `valueRules`·`keyOverlap*` 초록. RED: `'C_00'` 반복 · `'VAL_1'`
- 의도된 골든 변화: `golden/insert/*.sql` diff 0 · `self-check.txt` `genVal(4)` 복잡도가 내린 값으로
- 검증: `bash scripts/mvn.sh -q test -Dtest='InsertGenTest,SelfCheckTest,InsertRoutesTest'` → `bash scripts/verify.sh`
- 의존: 4-33

### 4-35 — CHECK IN 은 그 표 것만
- 대상: `core/gen/InsertGen.java`(`checkIns` 옆) · `web/InsertRoutes.java`(`:78`) · `InsertGenTest`·`InsertRoutesTest`
- 변경:
  ```java
  /** A-05 — 이 표의 CHECK 에서만. 컬럼(대문자) → 값 리터럴들 */
  public static Map<String, List<String>> checkIns(Table t) {
      Map<String, List<String>> out = new HashMap<>();
      for (Check k : t.checks()) {
          if (k.condition() != null) {
              Matcher m = CHECK_IN.matcher("CHECK (" + k.condition() + ")");
              while (m.find()) {
                  out.putIfAbsent(m.group(1).toUpperCase(Locale.ROOT), splitVals(m.group(2)));
              }
          }
      }
      return out;
  }
  ```
  `checkIns(String)` javadoc 을 「표가 하나인 DDL 용 — 여러 표면 `checkIns(Table)`(4-35). 시험·표본 하네스가 쓴다」 로. `InsertRoutes` — `allowed = InsertGen.checkIns(req.ddl());` 줄을 지우고, `table` 이 정해진 뒤(404 검사 다음) DDL 모드면 `allowed = InsertGen.checkIns(table.get());`(`allowed` 가 effectively final 이어야 하면 새 변수로)
- 시험(먼저): `InsertGenTest.checkInsFromTable` — `DdlReader.read("CREATE TABLE a (status VARCHAR(10) CHECK (status IN ('NEW','PAID'))); CREATE TABLE b (status NUMBER(1) CHECK (status IN (0, 1)));")` 의 표마다 `checkIns(Table)` → a 는 `'NEW'`·`'PAID'`, b 는 `0`·`1`. `InsertRoutesTest.checkInIsPerTable` — 같은 DDL·표 `b` 요청 → SQL 에 `'NEW'` 가 없다. RED: b 에 `'NEW'`
- 의도된 골든 변화: 없음. `DbCorpusBase.insertRuns:294`(전 DDL 을 이은 `checkIns(String)`)는 안 고친다
- 검증: `bash scripts/mvn.sh -q test -Dtest='InsertGenTest,InsertRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 4-34 · 1-95b(`InsertRoutes` 같은 자리)

### 7-32 — 출력 경로 겹침
- 대상: `core/gen/Generator.java`(`run:82-98`) · `GeneratorTest`
- 변경: `run` 의 `plan` 선언 옆에 `Map<String, String> taken = new HashMap<>();`. 파일 루프 안 `LocalFiles.checkEncodable(…)` 다음 줄에 `claim(taken, target, table.name(), rel);`, `plan.addAll(mine);` 바로 앞에 `mine.forEach(p -> taken.put(pathKey(p.target()), p.table()));`
  ```java
  /** A-12 — 앞 표가 차지한 경로면 던진다(run 의 catch 가 그 표를 건너뛴다). 대소문자는 안 가린다(Windows) */
  private static void claim(Map<String, String> taken, Path target, String table, String rel) throws IOException {
      String owner = taken.get(pathKey(target));
      if (owner != null) {
          throw new IOException("출력 경로가 " + owner + " 와 겹친다(" + rel + ")");
      }
  }

  private static String pathKey(Path p) {
      return p.toString().toLowerCase(Locale.ROOT);
  }
  ```
- 시험(먼저): `collidingTablesAreSkipped` — 표 `TB_USER`·`TN_USER`(skipTokens `TB`·`TN`) → `files` 는 10(앞 표 것만, `table` 이 전부 `TB_USER`), `warnings` 에 `TN_USER: 출력 경로가 TB_USER 와 겹친다(` 로 시작하는 줄 하나, 디스크의 `UserVO.java` 에 `TB_USER` 것의 글 · 클래스 이름이 대소문자만 다른 두 표(`AB_C`·`A_BC` 꼴로 `AbC`·`ABc` 가 나오게)도 같다. 기존 `writesResultAndMarksExisting` 초록. RED: `files` 20·경고 없음
- 의도된 골든 변화: 없음(`gen-hr.json` — HR 표는 안 겹친다)
- 검증: `bash scripts/mvn.sh -q test -Dtest='GeneratorTest,GenerateRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

### 2-41 — 양식 합계 수식 범위
- 대상: 새 `core/report/FormulaRange.java` · `core/report/XlsxFiller.java`(`:93-95`) · `XlsxFillerTest`
- 변경: `fill` 의 `sheet.shiftRows(first + 1, sheet.getLastRowNum(), n - 1);` 다음 줄(같은 `if` 안)에 `FormulaRange.widen((XSSFWorkbook) wb, sheet, first, n);`
  ```java
  /** B-01 — 데이터 행 아래로 밀린 수식의 범위가 데이터 첫 행에서 끝나면(SUM(B3:B3)) 채운 끝 행까지 넓힌다.
   *  같은 시트 범위만. 단일 칸 참조(SUM(B3))·다른 시트 참조·POI 가 못 읽는 수식은 그대로 둔다 — 양식은 범위 꼴로 쓴다 */
  final class FormulaRange {
      static void widen(XSSFWorkbook wb, Sheet sheet, int first, int n) {
          XSSFEvaluationWorkbook ew = XSSFEvaluationWorkbook.create(wb);
          int si = wb.getSheetIndex(sheet);
          boolean any = false;
          for (int r = first + n; r <= sheet.getLastRowNum(); r++) {
              Row row = sheet.getRow(r);
              if (row == null) { continue; }
              for (Cell c : row) {
                  any |= c.getCellType() == CellType.FORMULA && widened(c, ew, si, r, first, n);
              }
          }
          if (any) { sheet.setForceFormulaRecalculation(true); }
      }

      private static boolean widened(Cell c, XSSFEvaluationWorkbook ew, int si, int r, int first, int n) {
          Ptg[] ptgs;
          try {
              ptgs = FormulaParser.parse(c.getCellFormula(), ew, FormulaType.CELL, si, r);
          } catch (FormulaParseException e) {
              return false; // 못 읽는 수식은 그대로
          }
          boolean hit = false;
          for (Ptg p : ptgs) {
              if (p instanceof AreaPtg a && a.getLastRow() == first && a.getFirstRow() <= first) {
                  a.setLastRow(first + n - 1);
                  hit = true;
              }
          }
          if (hit) { c.setCellFormula(FormulaRenderer.toFormulaString(ew, ptgs)); }
          return hit;
      }
  }
  ```
  (`widened` 인자 6 — 상한 7 안. import 는 `org.apache.poi.ss.formula.*`·`org.apache.poi.ss.formula.ptg.*`·`org.apache.poi.xssf.usermodel.*`, POI 5.4.1)
- 시험(먼저): `totalsFormulaIsWidened` — `writeTemplate` 양식의 4 행(색인 3)에 `SUM(B3:B3)`·`COUNTA(A3:A3)`·`B3*2` 세 수식 칸을 더해 저장 → 세 행을 채움 → 결과를 직접 열어(`read()` 도우미는 수식 칸에서 던진다) 6 행의 수식 글이 `SUM(B3:B5)`·`COUNTA(A3:A5)`·`B3*2`, `createFormulaEvaluator().evaluate` 로 합계 60(값 10·20·30) · 한 행만 채우면 수식 그대로. 기존 `rowsBelowFirstRowAreShiftedDown` 초록. RED: `SUM(B3:B3)`·10
- 의도된 골든 변화: `golden/deliverable/filled-*.json` diff 0(예시 양식에 수식 없음)
- 검증: `bash scripts/mvn.sh -q test -Dtest='XlsxFillerTest,DeliverableServiceTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus
- 사다리: `shiftRows` 가 이미 그 범위를 넓혀 놓았으면(판 차이 — 시험이 고침 없이 초록) `widen` 을 넣지 않고 단언만 남긴다. `AreaPtg` 가 아니라 `Area3DPxg` 로 풀리면 그 형식의 `getSheetName()` 이 null 일 때만 같이 넓힌다

### 2-42 — 관계: 같은 이름 표 · PK 이름 몰림 · 후보 상한
- 대상: `core/deliverable/Relations.java`(`Result:45` · `infer:65-73·136` · `named:145-156`) · `core/deliverable/DeliverableService.java`(`writeGuide:205-216` · `candidates:223`) · `core/report/XlsxWriter.java`(`write:35-47`) · `RelationsTest`·`DeliverableServiceTest`·`XlsxWriterTest`(없으면 새로)
- 변경:
  - `Result` — 4번째 성분 `List<String> notes`(생성자에서 `List.copyOf`), 세 인자 생성자 `this(strong, weak, named, List.of())` 를 더하고 두 인자 판은 그것을 부른다
  - `infer` 머리(분기를 더하지 않는다 — 도우미 호출만):
    ```java
    static final int CROWD = 30;

    Set<String> twice = twice(snapshot);                               // 같은 이름(대문자)이 스키마 둘 이상
    Map<String, Table> tables = new HashMap<>();
    for (Table t : Definitions.sorted(snapshot)) {
        tables.putIfAbsent(up(t.name()), t);
    }
    tables.keySet().removeAll(twice);                                  // B-02 — 어느 스키마 것인지 모르는 이름은 추정에서 뺀다
    Set<String> declared = new HashSet<>();
    for (Table t : Definitions.sorted(snapshot)) {                     // 전 스키마 — tables.values() 가 아니다
        …
    }
    List<String> notes = notes(twice, joins, tables);
    …
    return new Result(strong, weak, named(tables, declared), notes);
    ```
    `private static Set<String> twice(List<Schema>)` · `private static List<String> notes(Set<String> twice, List<JoinRow> joins, Map<String, Table> tables)` — ① 조인(`tableA`·`tableB` 대문자)에 나온 `twice` 이름이 있으면 `"같은 이름 표가 여러 스키마에 있다 — 조인 근거를 어느 쪽에도 붙이지 않았다: " + 이름순 ", " 잇기` ② `crowded(tables)` 의 이름마다 `"PK 컬럼 이름 " + 이름 + " 를 표 " + n + "개가 같이 쓴다 — 이름 기반 후보에서 뺐다"`
  - `named` — `byPk` 를 다 채운 뒤(첫 루프 다음 줄) `byPk.values().removeIf(l -> l.size() > CROWD);` 한 줄. `private static Map<String, Integer> crowded(Map<String, Table> tables)` 는 같은 조건(뷰 아님·단일 컬럼 PK)으로 이름별 표 수를 세어 `> CROWD` 만 이름순으로(`TreeMap`)
  - `DeliverableService` — 요약 시트의 한계 글(`:205-208`) 뒤에 `inferred.notes()` 를 한 줄씩 같은 꼴로 더한다. `candidates(inferred)` 결과가 `MAX_CANDIDATES = 20_000` 을 넘으면 앞 20,000 행만 넣고 그 시트의 `ResultTable` 을 `truncated = true` 로(지금 `table(cols, rows)` 가 거짓을 넘긴다 — 그 한 호출만 `truncated` 를 받는 판으로)
  - `XlsxWriter.write(LinkedHashMap, Path)` — `try (SXSSFWorkbook wb = …; OutputStream out = Files.newOutputStream(file))` 를 `try (SXSSFWorkbook wb = …) { …시트 만들기…; try (OutputStream out = Files.newOutputStream(file)) { wb.write(out); } }` 로. 같은 꼴(스트림을 먼저 여는)인 다른 `write` 가 있으면 같이
- 시험(먼저): `sameNameInTwoSchemasIsNotInferred` — 스키마 `APP_A`·`APP_B` 에 각각 `CUSTOMER(ID PK)`·`ORDERS(CUSTOMER_ID)` + 조인 `ORDERS.CUSTOMER_ID = CUSTOMER.ID` → `strong`·`weak`·`named` 다 빔, `notes` 한 줄에 `CUSTOMER, ORDERS` · 한 스키마만 있으면 지금처럼 `strong` 하나(`singlePkParent` 초록). `crowdedPkNameIsDropped` — PK `ID`(같은 타입) 표 40 → `named` 에 `ID` 짝 0, `notes` 에 `PK 컬럼 이름 ID 를 표 40개` · 표 30 이면 지금처럼 짝이 나온다. `guideCarriesRelationNotes` — 위 두 스냅샷으로 `build` → `00_작성안내.xlsx` 요약 시트에 그 두 글. `failedSheetLeavesNoFile` — 시트 이름 `"a[b]"`(POI 가 거절) 로 `write` → 예외 + 그 경로에 파일이 없다. RED: 04 한 줄 · 780 짝 · 0 바이트 파일
- 의도된 골든 변화: `golden/deliverable/pg-*.json` diff 0 · `self-check.txt` 불변(`infer` 26·`named` 19 — 오르면 도우미로)
- 검증: `bash scripts/mvn.sh -q test -Dtest='RelationsTest,DefinitionsTest,DeliverableServiceTest,XlsxWriterTest,SelfCheckTest,DeliverableRoutesTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus

### 2-43 — CHECK 리터럴 안 낱말
- 대상: `core/deliverable/Definitions.java`(`constraints:357-362`) · `DefinitionsTest`
- 변경: 상수 `private static final java.util.regex.Pattern CHECK_LITERAL = java.util.regex.Pattern.compile("'(?:[^']|'')*+'");` · `word.matcher(k.condition()).find()` → `word.matcher(CHECK_LITERAL.matcher(k.condition()).replaceAll("''")).find()`. 메서드를 더하지 않는다(`Definitions` 메서드 34 기준선)
- 시험(먼저): `checkLiteralIsNotAColumn` — 표(컬럼 `ID`·`STATUS`, CHECK `STATUS IN ('ACTIVE', 'ID', 'OFF')`) → `constraints(t, ID)` 는 빈 글, `constraints(t, STATUS)` 는 그 CHECK. 기존 `r9r10r11LengthPkDefault` 초록. RED: ID 에 CHECK
- 검증: `bash scripts/mvn.sh -q test -Dtest='DefinitionsTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 2-42

### 6-44 — 쉼표 뒤 표
- 대상: `core/analyze/SqlTables.java`(`Layer:79-87` · `open:209-216` · `table:229-234` · `alias:331-332`) · `SqlTablesTest`(`CASES`) · `golden/analyze/sql-tables.json`
- 변경:
  ```java
  /** 표 목록(FROM·USING 절)을 끝내는 낱말 — 이 뒤 쉼표는 표 구분이 아니다(6-44) */
  private static final Set<String> LIST_END = Set.of("WHERE", "GROUP", "HAVING", "ORDER", "UNION", "INTERSECT", "EXCEPT", "MINUS", "CONNECT", "START",
          "LIMIT", "OFFSET", "FETCH", "FOR", "WINDOW", "QUALIFY", "SET", "VALUES", "RETURNING", "SELECT", "INTO", "WHEN", "OPTION", "MODEL");

  // Layer 필드
  /** 이 층이 표 목록(FROM·USING 뒤, 절 끝 낱말 앞) 안인가 */
  boolean list;

  // open — return opener(…); 를
  EnumSet<Crud> o = opener(x, sc.top().verb, sc.top().targetDone);
  sc.top().list = listAfter(x, o != null, sc.top().list);
  return o;

  /** FROM·USING 이 열면 목록 안, 절 끝 낱말이면 밖, 그 밖은 그대로 */
  private static boolean listAfter(String x, boolean opened, boolean now) {
      if (opened && ("FROM".equals(x) || "USING".equals(x))) {
          return true;
      }
      return now && !LIST_END.contains(x);
  }

  // table — 괄호 친 표 층을 켜는 자리(sc.top().targetDone = true; 다음 줄)
  sc.top().list = true;

  // alias — default -> { } 를
  default -> reopen(x, sc);

  /** C-TBL-COMMA — 닫힌 상태의 쉼표: 켜진 층의 표 목록 안이면 표 자리를 다시 연다 */
  private static void reopen(String x, Scan sc) {
      if (",".equals(x) && lit(sc) && sc.top().list) {
          sc.st = State.TABLE;
          sc.pending = EnumSet.of(Crud.R);
          sc.target = false;
      }
  }
  ```
  `extract` 는 안 고친다(복잡도 17 기준선). `alias` 의 `default` 는 `CLOSED`·`TABLE` 둘인데 `TABLE` 은 `alias` 로 안 온다(`extract:157`)
- 시험(먼저) — `CASES` 에 더한다(이름 · SQL · 기대 표): `commaAfterSubquery` `SELECT * FROM A, (SELECT X FROM B) V, C` → A·B·C 다 R · `commaAfterOn` `SELECT * FROM A JOIN B ON A.X = B.X, C` → A·B·C · `commaAfterDblink` `SELECT * FROM A@LNK, B` → A·B · `commaAfterPartition` `SELECT * FROM T1 PARTITION (P1), T2` → T1·T2 · `commaAfterTableFn` `SELECT * FROM T1, TABLE(F(T1.X)) V, T2` → T1·T2 · `commaAfterHint` `SELECT * FROM T1 WITH (NOLOCK), T2` → T1·T2 · `commaInParenTable` `SELECT * FROM (A JOIN B ON A.X = B.X, C)` → A·B·C · `commaAfterUsing` `DELETE FROM A USING B JOIN C ON B.X = C.X, D WHERE A.X = B.X` → A=D, B·C·D=R · 음성(표가 늘면 안 된다): `orderByCommas` `SELECT A FROM T ORDER BY A, B` → T · `groupByCommas` `SELECT A, B FROM T GROUP BY A, B` → T · `unionSelectList` `SELECT A FROM T UNION SELECT B, C FROM U` → T·U · `mergeSetCommas` `MERGE INTO T USING S ON (T.A = S.A) WHEN MATCHED THEN UPDATE SET T.B = S.B, T.C = S.C` → T=CU, S=R · `updateSetFromCommas` `UPDATE A SET X = 1, Y = 2 FROM B, C WHERE A.K = B.K` → A=U, B·C=R · `limitCommas` `SELECT A FROM T LIMIT 10, 20` → T. `-Dgolden.update=true` 로 골든에 **더해지는 것만** 확인(기존 사례 diff 0). RED: 앞 여덟에서 뒤 표 없음
- 의도된 골든 변화: `golden/analyze/sql-tables.json` 에 새 사례 열넷만 · `golden/analyze/*-graph.json` diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlTablesTest,MapperIndexTest,JavaGraphTest,SelfCheckTest,AnalyzeRoutesTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus(V-65a 가 표본을 잰다)

### 6-45 — 별칭 대상
- 대상: `core/analyze/SqlTables.java`(`Layer` · `Scan` · `table:245-251` · `alias:321-330` · `track:195-198` · `extract:137·163`) · `SqlTablesTest`(`CASES`) · 골든
- 변경:
  ```java
  // Layer 필드
  /** 이 층에서 읽은 별칭 → 표, 대상(U·D) 자리에서 읽은 이름, 방금 읽은 표(6-45) */
  final Map<String, String> aliases = new HashMap<>();
  final Set<String> targets = new HashSet<>();
  String last;

  // Scan — refs 를 든다
  final Map<String, EnumSet<Crud>> refs;
  Scan(String verb, Map<String, EnumSet<Crud>> refs) { this.refs = refs; reset(verb); }

  /** 층이 닫힐 때·문장이 끝날 때 — 대상 이름이 그 층의 별칭이면 U·D 를 진짜 표로 옮긴다 */
  void settle(Layer l) {
      for (String n : l.targets) {
          String real = l.aliases.get(n);
          EnumSet<Crud> had = refs.get(n);
          if (real == null || real.equals(n) || had == null) {
              continue;
          }
          EnumSet<Crud> moved = EnumSet.copyOf(had);
          moved.retainAll(EnumSet.of(Crud.U, Crud.D));
          refs.computeIfAbsent(real, k -> EnumSet.noneOf(Crud.class)).addAll(moved);
          had.removeAll(moved);
          if (had.isEmpty()) {
              refs.remove(n);
          }
      }
  }
  ```
  끼우는 자리(전부 분기 없는 한 줄씩): `reset` 머리에 `on.forEach(this::settle);`(첫 호출은 빈 목록) · `track` 의 `sc.on.remove(sc.on.size() - 1);` 을 `sc.settle(sc.on.remove(sc.on.size() - 1));` 로 · `extract` 의 `Scan sc = new Scan(verb);` → `new Scan(verb, refs);`, `refs.keySet().removeAll(cteNames(t));` 바로 앞에 `sc.on.forEach(sc::settle);` · `table` 의 `refs.computeIfAbsent(t.get(k), …).addAll(sc.pending);` 다음 줄에 `sc.top().note(t.get(k), sc.target && (sc.pending.contains(Crud.U) || sc.pending.contains(Crud.D)));` — `Layer.note(String name, boolean target)`: `last = name; if (target) { targets.add(name); }` · `alias` 에서 `AFTER_ALIAS` 로 가는 두 자리(`ALIAS_OK` 의 낱말 가지 · `ALIAS` 가지)는 `sc.st = State.AFTER_ALIAS` 앞에 `sc.top().alias(x);` — `Layer.alias(String a)`: `if (last != null) { aliases.putIfAbsent(a, last); }`. `ALIAS` 가지는 식 하나라 `case ALIAS -> sc.st = word(x) ? named(x, sc) : State.CLOSED;` 로 두고 `private static State named(String x, Scan sc) { sc.top().alias(x); return State.AFTER_ALIAS; }` 를 두 자리가 같이 쓴다
- 시험(먼저) — `CASES` 에: `deleteAlias` `DELETE A FROM TB_A A JOIN TB_B B ON A.X = B.X` → TB_A=RD, TB_B=R(A 없음) · `updateAliasMssql` `UPDATE A SET A.X = 1 FROM TB_A A WHERE A.Y = 2` → TB_A=RU · `deleteTwoAliases` `DELETE A, B FROM TB_A A JOIN TB_B B ON A.X = B.X` → TB_A=RD, TB_B=RD · 음성: `updateFromKeepsTarget`(기존 `updateFrom` 과 같은 꼴 — `UPDATE T1 SET X = S.X FROM COMTNSRC S` → T1=U 그대로) · `innerAliasIsNotTarget` `DELETE FROM A WHERE X IN (SELECT B.X FROM TB_B A2)` → A=D, TB_B=R. RED: 앞 셋에서 `A=D`·`A=U`
- 의도된 골든 변화: 새 사례 다섯만. 기존 `updateFrom`·`multiTableDelete` diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlTablesTest,MapperIndexTest,JavaGraphTest,SelfCheckTest,AnalyzeRoutesTest'` → `bash scripts/verify.sh`
- 의존: 6-44
- 사다리: `table()` 이 15 를 넘기면 `note` 호출의 조건식을 `Scan` 의 `boolean targeting()` 로 뺀다

### 0-86 (+ 0-87) — 실행 폴더 밖 링크 · UNC 판정 한 자리
- 대상: `core/fs/LocalFiles.java`(`check:150-154`) · `core/profile/Profile.java`(`local:184-189`) · `cli/MakeTemplates.java`(`inside:51-58`) · `LocalFilesTest`·`ProfileStoreTest`·`MakeTemplatesTest`
- 변경:
  ```java
  // LocalFiles
  /** 0-87 — UNC(\\서버\공유)·장치 경로(\\?\ \\.\) 꼴인가. 앞뒤 공백·/ 를 가리지 않는다. core.profile·cli 가 같이 쓴다 */
  public static boolean network(String raw) {
      return raw != null && raw.trim().replace('/', '\\').startsWith("\\\\");
  }
  // check:152 — raw.trim().replace('/', '\\').startsWith("\\\\") → network(raw)   (root 검사는 그대로)
  // Profile.local — if (LocalFiles.network(path)) { throw … 같은 글 }
  // MakeTemplates.inside
  private Path inside(String option, Path p) {
      Path base = Path.of("").toAbsolutePath().normalize();
      Path abs = p.toAbsolutePath().normalize();
      if (LocalFiles.network(p.toString()) || !abs.startsWith(base) || !real(abs).startsWith(real(base))) {
          throw new picocli.CommandLine.ParameterException(spec.commandLine(), option + " 는 실행 폴더 아래여야 한다: " + base);
      }
      return abs;
  }

  /** 0-86 — 있는 가장 가까운 조상의 실제 경로 + 나머지. 링크·정션을 따라간 자리로 잰다 */
  private static Path real(Path abs) {
      Path up = abs;
      while (up != null && !java.nio.file.Files.exists(up)) {
          up = up.getParent();
      }
      try {
          return up == null ? abs : up.toRealPath().resolve(up.relativize(abs));
      } catch (java.io.IOException e) {
          return abs;
      }
  }
  ```
- 시험(먼저): `MakeTemplatesTest.linkOutsideRunFolderIsRefused` — 실행 폴더 안에 바깥 임시 폴더를 가리키는 심볼릭 링크를 만들고(`LocalFilesTest.forbiddenFolderBehindLinkIsRefusedByRealPath:254` 꼴 — 못 만들면 `Assumptions.abort`) `--out <링크>/x` → 거절, 바깥 폴더에 파일 없음 · `" \\\\srv\\share"`(앞 공백) → 세 자리 다 거절: `LocalFilesTest.networkPathsRefusedBeforeTouching` 에 한 줄, `ProfileStoreTest.networkPathsInProfileAreRejected` 그대로, `MakeTemplatesTest.outsideRunFolderIsRefused` 에 한 줄 · `LocalFilesTest` 에 `network` 표(`null`·상대·`C:\a`·`//s/x`·`\\?\C:\a`). RED: 링크로 밖에 써짐 · `MakeTemplates` 앞 공백 꼴 통과
- 검증: `bash scripts/mvn.sh -q test -Dtest='LocalFilesTest,ProfileStoreTest,MakeTemplatesTest,ArchitectureTest,VerifyLanesTest'` → `bash scripts/verify.sh`
- 의존: S0. 분할표는 `0-86`·`0-87` 두 행 다 완료로, 커밋은 하나(`fix: 0-86 …·0-87 …`)
- 사다리: `ArchitectureTest.noPackageCycles` 또는 `VerifyLanesTest` 가 `core.profile → core.fs` 새 의존으로 빨강이면 `Profile.local` 은 지금 글 그대로 두고(복사 유지) `LocalFiles`·`MakeTemplates` 둘만 `network` 를 쓴다 — 이력 「정한 것」

### 0-94 — 스택 넘침 남은 자리
- 대상: `core/check/PackageTypes.java`(`scan:88`) · `core/analyze/JavaGraph.java`(`walked:290-298`) · `CleanupRunnerTest`·`JpaIndexTest`(없으면 새로)·`JavaGraphTest` · `self-check.txt`(`JavaGraph` 길이 한 줄)
- 변경: `PackageTypes.scan` 의 catch 를 `catch (IOException | LocalFiles.Refused | java.nio.file.InvalidPathException | StackOverflowError x)` 로(주석 「이웃 파일이 넘쳐도 그 패키지만 못 푼다(0-94)」). `JavaGraph.walked` 의 catch 몸:
  ```java
  boolean was = quiet;
  quiet = false;                 // 0-94 — DAO 훑기(quiet) 중에도 못 훑은 메서드는 적는다
  note("parse", c.file, line(m), "");
  quiet = was;
  return false;
  ```
- 시험(먼저): `CleanupRunnerTest.deepNeighborDoesNotSkipTarget` — 같은 패키지에 `Deep.java`(기존 `stackOverflowFileIsSkipped` 의 깊은 식)와 별표 import 를 가진 `Use.java` → `Use.java` 줄에 `내부 오류` 가 없다(`Deep.java` 줄만) · `JpaIndexTest.deepFileIsSkipped` — 깊은 식 파일 하나 + 엔티티 파일 하나를 `JpaIndex` 진입 메서드에 직접 → 예외 없이 엔티티 하나. `JavaGraphTest.walkOverflowIsNoted` — 파싱은 되고 `walk` 에서만 넘치는 입력을 두 꼴로 시도한다: ⓐ `a.b().b()…` 호출 사슬 6,000 마디 ⓑ `x = 1 + 1 + … ` 이항 6,000 개. 한 꼴이라도 「파싱 통과 + `walk` 넘침」 이면 그 입력으로 DAO 클래스를 만들어 `unresolved` 에 `parse` 가 있다고 단언. RED: `Use.java` 가 `내부 오류`
- 의도된 골든 변화: `self-check.txt` `JavaGraph` `길이 1013` → `1016`(세 줄 — 이 행이 허락)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupRunnerTest,JpaIndexTest,JavaGraphTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0
- 사다리: ⓐⓑ 둘 다 파싱에서 먼저 넘치면 `walkOverflowIsNoted` 는 쓰지 않고 이력에 「`walked` catch 는 파싱이 먼저 넘쳐 입력으로 못 지난다 — 고침만 넣음」 한 줄. `JpaIndex` 진입 메서드가 private 이면 `JavaGraph` 를 거쳐 같은 단언(엔티티가 `jpa-graph` 에 나온다)

### 0-96 — 번들 84 잔손
- 대상: `web/ProfileRoutes.java`(`withoutBlockPasswords:287` · `leaked:134`) · `ProfileRoutesTest`
- 변경: `:287` — `m.group(1) + m.group(2) + " ''"` 뒤에 `+ (m.group(3) == null ? "" : " " + m.group(3))`(그 줄이 든 메서드가 15 를 넘기면 `private static String tail(Matcher m)` 로). `:134` — `catch (java.io.IOException | com.fasterxml.jackson.databind.RuntimeJsonMappingException | IllegalArgumentException | IllegalStateException e)`. `SqlProbe` javadoc 자리는 `6-43` 이 한다
- 시험(먼저): `ProfileRoutesTest.backupKeepsCommentAfterEmptyPassword` — `password: # 운영 접속` 줄이 든 프로필 → 백업 글에 `# 운영 접속` 이 남고 비밀번호 값은 없다. RED: 주석 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='ProfileRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 java 만

### 5-115 — 코드 정리 리뷰 Minor ①②③
- 대상: `core/check/ConsoleArgs.java`(`sideEffect:61-70` · `call:73-…` · `risk:32-48`) · `core/check/LogFix.java`(`earlyInit:261-268`) · `core/check/PiiFix.java`(`apply:32` · `:58`) · `CleanupTest`
- 변경:
  - ① `sideEffect` 의 `'='` 가지를 `case '=' -> assign(s, k);` 로:
    ```java
    /** 대입인가 — ==·!=·<=·>=·=> 는 아니다. <<=·>>=·>>>= 는 대입이다(5-115) */
    private static boolean assign(String s, int k) {
        char next = k + 1 < s.length() ? s.charAt(k + 1) : 0;
        if (next == '=' || next == '>') {
            return false;
        }
        char p = k == 0 ? 0 : s.charAt(k - 1);
        if (p == '<' || p == '>') {
            return k >= 2 && s.charAt(k - 2) == p;
        }
        return p != '=' && p != '!';
    }
    ```
    `call` — 뒤로 공백을 건너뛴 글자 `p` 가 `.` 이고 그 앞이 `?` 면 참(`cb?.(x)`). 태그드 템플릿 — `risk` 의 부수 효과 루프 앞에 `if (tagged(args, code)) { return SIDE_EFFECT; }`: `tagged` 는 `args.charAt(k) == '`'` 이고 `k > 0` 이고 `code[k - 1]` 이 참이며 그 글자가 낱말 글자·`)`·`]` 인 자리가 하나라도 있으면 참(`boolean... code` — varargs 로)
  - ② `earlyInit`:
    ```java
    private static boolean earlyInit(TypeDeclaration<?> t, int before) {
        String self = t.getNameAsString();
        Set<String> local = t.findCompilationUnit().map(cu -> cu.findAll(TypeDeclaration.class).stream()
                .map(x -> ((TypeDeclaration<?>) x).getNameAsString()).collect(Collectors.toSet())).orElse(Set.of(self));
        if (t instanceof EnumDeclaration e && !e.getConstructors().isEmpty()) {
            return true; // enum 상수는 static 필드보다 먼저 생성자를 돈다
        }
        return t.getMembers().stream()
                .filter(…지금 두 filter 그대로…)
                .anyMatch(m -> m.findAll(ObjectCreationExpr.class).stream().anyMatch(o -> local.contains(o.getType().getNameAsString()))
                        || m.findAll(MethodCallExpr.class).stream().anyMatch(c -> c.getScope().isEmpty()
                                || c.getScope().filter(s -> s.isNameExpr() && self.equals(s.asNameExpr().getNameAsString())).isPresent()));
    }
    ```
    다른 파일에 선언된 하위 형식의 `new` 는 못 본다(한계 — javadoc)
  - ③ `PiiFix.apply` — `lang` 이 java·js 꼴(`RegexRule.stripComments` 가 `//`·블록 주석을 떼는 언어 — `java`·`js`·`ts`·`jsx`·`tsx`, 그 클래스의 언어 판정을 그대로 쓴다)이면 `String[] bare = RegexRule.stripComments(text, lang).split("\n", -1);` 를 만들어 `:58` 의 `new Quotes(line)` 을 `new Quotes(bare[i])` 로(줄 수·자리는 같다 — 주석은 공백으로 바뀐다). 그 밖 언어는 지금 그대로
- 시험(먼저): `CleanupTest.consoleArgsRisk` 에 음성 넷 — `console.log(cb?.(x));`·`console.log(a <<= 1);`·`console.log(a >>>= 1);`·``console.log(tag`x`);`` 가 남고 사유 `SIDE_EFFECT`, 양성 `console.log(a <= b, a >> 1, a => a, `t`);` 는 지워진다 · `logSkipsEarlyStaticInit` 에 음성 셋 — `static final T I = T.create();` · `static final Inner X = new Inner();`(중첩 형식) · 생성자 있는 enum, 양성 `static final Map<String, String> M = new HashMap<>();` 은 고쳐진다 · `piiQuoteScan` 에 두 줄(js) — 음성 `/* it's */ var n = 4111111111111111;` 는 글 불변(주석 속 `'` 가 따옴표를 열어 맨 숫자를 「따옴표 안」 으로 보던 꼴), 양성 `/* it's */ var s = "4111111111111111";` 는 가려진다. RED: 음성 여덟
- 의도된 골든 변화: `golden/cleanup/*` diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,ZonesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus

### 5-116 — 코드 정리 리뷰 Minor ④⑤⑥
- 대상: `core/check/Zones.java`(`rawLines:32`) · `core/check/MarkupFix.java`(`:48`) · `core/check/CleanupRunner.java`(`preview:101-105` · `apply:174-179`) · `CleanupTest`·`CleanupRunnerTest`·`ZonesTest`
- 변경:
  - ④ `Zones.rawLines(String text, String lang, List<String> tags)` 를 더하고 지금 두 인자 판은 `rawLines(text, lang, RAW_TAGS)` 로. `MarkupFix:48` 은 `Zones.rawLines(s.text(), s.lang(), List.of("textarea"))`. `MarkupFix.RAW` 글자는 안 바꾼다(표본 baseline 열쇠). 삭제 쪽(`Cleanup:533·563`)은 두 인자 판 그대로(`pre` 안 `debugger;` 줄은 계속 안 지운다)
  - ⑤ `preview`·`apply` 의 섞인 줄끝 가지 — `keepsEol` 일 때 **고칠 것이 있을 때만** `MIXED_EOL` 줄을 낸다: 그 가지 안에서 `Cleanup.Result r = c.clean(<그 파일 Source>)` 를 먼저 돌려 글이 안 바뀌면(`r` 의 고친 글이 원문과 같으면) 그 파일은 건너뛴다(줄 없음), 바뀌면 지금 `MIXED_EOL` 줄. 통일 길(`!keepsEol` 이고 `t.mixed()`)에서는 그 파일의 `skips` 끝에 `new Cleanup.Skip("file", UNIFIED_EOL + <LF|CRLF>)` 를 더한다 — 새 상수 `static final String UNIFIED_EOL = "줄끝이 섞여 있었다 — 전부 통일했다: ";`. `preview`·`apply` 가 15 를 넘기면 그 가지를 `private` 도우미로
  - ⑥ 시험만
- 시험(먼저): `markupPreIsMarkup` — `<pre><table><tr><th>이름</th></tr></table></pre>` 의 `th` 에 `scope` 가 끼워진다 · `<textarea>` 안은 지금처럼 `RAW` · `htmlLangSkipsRawLine` — `<textarea>` 안 줄의 `<html>` 글에는 `lang` 을 안 끼운다 · `scriptOpenLongTail` — `"<script" + ">".repeat(200_000)` 가 앞 줄인 입력의 console 삭제가 5초 안 · `CleanupRunnerTest.mixedEolOnlyWhenChanged` — 섞인 줄끝이지만 고칠 것 없는 파일은 미리보기에 줄이 없다, 고칠 것 있으면 `MIXED_EOL` · `unifyNotesMixed` — `file.lineEnding` 을 고르고 섞인 파일 → 고쳐지고 `skips` 에 `줄끝이 섞여 있었다 — 전부 통일했다: LF`. 기존 `markupSkipsRawText`·`mixedEolFileIsSkipped` 는 그 뜻 그대로 초록(`markupSkipsRawText` 가 `pre` 를 날글로 단언하고 있으면 그 줄만 「끼워진다」 로 — 의도된 변화). RED: `pre` 안 건너뜀 · 고칠 것 없는 파일의 줄
- 의도된 골든 변화: `golden/cleanup/*` diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupRunnerTest,ZonesTest,CleanupRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-115

### 6-43 — 분석 리뷰 Minor
- 대상: `core/analyze/SqlProbe.java`(`headCut:258-265` · `names:302-309` · 상수 `:59-70` javadoc) · `core/analyze/JavaGraph.java`(`Acc` · `new Stmt(` 다섯 자리 `:477·530·698·747·828` · `walked`) · `SqlProbeTest`·`JavaGraphTest` · `self-check.txt`(`JavaGraph` 길이)
- 변경:
  - ① `headCut`:
    ```java
    private static final Pattern HEAD_NEXT = Pattern.compile("(?i)^\\s*(SELECT|WITH|INSERT|UPDATE|DELETE)\\s+(\\S+)");

    private static String headCut(String msg, String sql) {
        Matcher h = HEAD_NEXT.matcher(sql == null ? "" : sql);
        if (!h.find()) {
            return msg;
        }
        String head = h.group(1);
        // 따옴표 토막이 통째 머리 낱말(MariaDB 「in 'SELECT'」) 또는 머리 낱말 + 그 SQL 의 둘째 낱말(따옴표 없는 되비춤)
        Matcher w = Pattern.compile("(?i)['\"`]\\s*" + head + "\\s*['\"`]|\\b" + head + "\\s+" + Pattern.quote(h.group(2))).matcher(msg);
        return w.find() ? msg.substring(0, w.start()) + "…" : msg;
    }
    ```
    `HEAD_WORD` 는 다른 데서 안 쓰면 지운다
  - ② `names` — `LITERAL`·`DQUOTED` 두 번 치환 대신 한 번 훑기: `'` 또는 `"` 를 만나면 같은 따옴표(겹친 것은 탈출)까지를 공백으로 바꾼 글을 만들고(안 닫히면 끝까지) 지금 낱말 모으기 그대로. `SqlProbe` 메서드가 30 을 넘기면 훑기는 `private static` 중첩 없이 `names` 안 루프로
  - ④ javadoc — `:59-60` 의 겹친 둘을 각자 상수 위로(`TAIL_LIMIT` 것은 `:70` 위로, `=Pattern` 띄어쓰기), `:64-65` 의 겹친 둘도 `DQUOTED`·`HEAD_NEXT` 위로
  - ③ `Acc` 에:
    ```java
    /** 6-43 — 문장 id 꼴이 아니면(드문 식별자) 담지 않는다. walked 가 그 메서드 줄에 statement 미해결을 적는다 */
    boolean rejected;

    void add(String id, String resolution) {
        if (StmtId.ok(id)) {
            stmts.add(new Stmt(id, resolution));
        } else {
            rejected = true;
        }
    }
    ```
    (`Acc` 는 `JavaGraph:138` 의 `private static final class` — `JavaGraph` 메서드 수에 안 든다.) 다섯 자리의 `acc.stmts.add(new Stmt(X, "…"));` 를 `acc.add(X, "…");` 로(분기 0). `walked` 의 `walk(c, m, 0, acc, Map.of());` 다음 줄에 `if (acc.rejected) { note("statement", c.file, line(m), ""); }`. `StmtId.plain` 으로 이미 재는 세 자리(`:539·548·556`)는 그대로
- 시험(먼저): `SqlProbeTest.headCutIsNarrow` — SQL `SELECT a FROM t`: 오류문 `Unknown column 'x' in 'SELECT'` 는 `'SELECT'` 앞에서 잘린다 · `SELECT list is not in GROUP BY clause` 는 안 잘린다 · `SELECT command denied to user` 는 안 잘린다 · `error near SELECT a FROM t` 는 `SELECT` 앞에서 잘린다 · SQL `WITH c AS (…) SELECT …` 에 오류문 `relation with oid 1 does not exist` 는 안 잘린다. `namesScanQuotesOnce` — `SELECT a FROM t WHERE b = "it's" AND c = 'x'` 의 `names` 에 `IT`·`S`·`X` 가 없고 `A`·`B`·`C`·`T` 는 있다. 기존 `fileMessageDropsDoubleQuotedAndNumbers`·`fileMessageKeepsOnlyOwnIdentifiers` 초록(그 가운데 「머리 낱말이면 무조건 자른다」 를 단언하는 줄이 있으면 새 뜻으로 — 의도된 변화, 이력에 적는다). `JavaGraphTest.oddIdentifierIsNotedNotFatal` — 메서드 이름이 `find€`(또는 NFD 한글)인 매퍼 인터페이스를 부르는 컨트롤러 → 예외 없이 끝나고 `unresolved` 에 `statement` 하나, 다른 프로그램의 문장은 그대로. RED: `SELECT list` 잘림 · `IT`·`S` · `IllegalArgumentException`
- 의도된 골든 변화: `golden/analyze/*-graph.json` diff 0 · `self-check.txt` `JavaGraph` 길이 `1016` → 오른 값 한 줄(이 행이 허락, 열 줄 안)
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlProbeTest,JavaGraphTest,AnalyzeStoreTest,SelfCheckTest,AnalyzeRoutesTest'` → `bash scripts/verify.sh`. 실물은 V-65(`roFileMessages`)
- 의존: 0-94. 레인 db + corpus

### 9-8 — 실물 DB 왕복: 규칙 · CHECK · 자동증가 · 역슬래시 · 행 수
- 대상: `src/test/java/kr/ejg/toolbox/web/DbCorpusBase.java`(새 `@Order(11)` 시험 하나 + 도우미)
- 변경: `rulesChecksAutoRoundTrip` — `skipIfOff("rulesChecksAutoRoundTrip")` 로 시작, 기존 도우미(`exec`·`execAll`·`snapshot(schema)`·`names().schema()`·`tablesOnly`·`premise`·`dropPrefixed`·`ddlTarget()`·`golden.put`·`CorpusFiles.none`)만 쓴다. `List<String> a` 에 실패를 모아 끝에 `CorpusFiles.none("86 왕복 " + ddlTarget(), a, 1)`(등급 A)
  1. 만들기(`dialect()` 로 가른다 — 부모 `T86_P`, 자식 `T86_C`. 아래는 DB 가 받는 SQL 글이다 — 자바 문자열로 옮길 때 `\` 를 한 번 더 겹친다. 저장되는 코멘트 값은 네 방언 다 `C:\tmp\`):
     - MARIA: `CREATE TABLE T86_P (ID INT NOT NULL AUTO_INCREMENT, NM VARCHAR(20), PRIMARY KEY (ID)) COMMENT = 'C:\\tmp\\'` · `CREATE TABLE T86_C (ID INT NOT NULL, P_ID INT, QTY INT, MEMO VARCHAR(50), PRIMARY KEY (ID), CONSTRAINT FK86_C FOREIGN KEY (P_ID) REFERENCES T86_P (ID) ON DELETE CASCADE, CONSTRAINT CK86_QTY CHECK (QTY >= 0))`
     - POSTGRES: `CREATE TABLE t86_p (id INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, nm VARCHAR(20))` · `COMMENT ON TABLE t86_p IS 'C:\tmp\'` · 자식 같은 꼴(소문자)
     - MSSQL: `CREATE TABLE T86_P (ID INT IDENTITY(1,1) NOT NULL PRIMARY KEY, NM NVARCHAR(20))` · 자식 같은 꼴(`MEMO NVARCHAR(50)`)
     - ORACLE: `CREATE TABLE T86_P (ID NUMBER(10) NOT NULL PRIMARY KEY, NM VARCHAR2(20))` · `COMMENT ON TABLE T86_P IS 'C:\tmp\'` · 자식 같은 꼴(`NUMBER(10)`·`VARCHAR2(50)`)
  2. `S` = `snapshot(schema)` 에서 이름이 `T86_` 로 시작하는 표(대소문자 무시). 전제 — 자식 FK `deleteRule == CASCADE` · 부모 `ID` 의 `autoIncrement`(ORACLE 은 안 본다) · 표 코멘트가 `C:\tmp\`(MSSQL 은 안 본다). 자식 CHECK 수는 `golden.put("t86Checks", n)`(MySQL 5.7 은 0 — 전제로 안 건다)
  3. `DdlGen.generate(S, new Options(ddlTarget(), ddlTarget(), null, true, true, true, "G86_"), types)` → `execAll(sql, a, "86 만들기")`
  4. `S2` = `G86_` 로 시작하는 표. 단언(어긋나면 `a` 에): 자식 FK 규칙 CASCADE · 자식 CHECK 수가 `S` 와 같다 · 부모 코멘트가 `S` 와 글자까지 같다 · 부모 `ID` 의 `autoIncrement` 가 `S` 와 같다
  5. MARIA 만 — `S2` 의 부모 `ID` 코멘트만 `번호` 로 바꾼 `b` 로 `AlterGen.generate(S2, b, Options(ddlTarget(), ddlTarget(), null, true, true, true))` → `execAll` → `exec("INSERT INTO G86_T86_P (NM) VALUES ('a')")` 가 성공(AUTO_INCREMENT 유지 — R-A-C1)
  6. 전 방언 — `InsertGen.fromCsv(<S2 자식 표>, "ID,QTY,MEMO\n1,1,C:\\temp\\", new InsertGen.Options(ddlTarget(), 1, false, false, null))` 의 SQL 을 `execAll` → `SELECT MEMO FROM <G86 자식> WHERE ID = 1` 이 `C:\temp\`(A-28)
  7. POSTGRES 만 — `CREATE TABLE t86_z (id INT)` → `ANALYZE t86_z` → 세 행 INSERT → `snapshot` 의 `t86_z` `rowCount` 가 null(F-K21)
  8. `finally` — `dropPrefixed("G86_")` + `T86_C`·`T86_P`·`t86_z` DROP(자식 먼저, 실패는 삼킨다 — 기존 `dropG18` 꼴)
  `golden.put("t86Statements", 실행한 수)`
- 시험: 이 시험이 강제 지점이다. RED 는 못 본다(고친 뒤에 넣는다) — 대신 V-65 에서 등급 A 0 을 본다
- 검증: `bash scripts/verify.sh`(컴파일·빠른 레인). 실물은 V-65
- 의존: 1-91 · 1-99 · 4-35
- 사다리(V-65 에서): 어느 방언에서 `a` 가 안 비면 — 그 방언·단계·SQLState 만 이력에 적고(값·SQL 본문 없음) 원인이 생성기면 그 행의 시험을 먼저 더해 고친다(고치기 둘). 수집이 못 주는 것(Oracle `IS_AUTOINCREMENT` 등)은 그 단언을 전제로 낮추고 이력 「드러난 것」

### V-65a — 정적 분석 · corpus 골든 확인
1. `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify`(PMD·SpotBugs·ErrorProne) — 새 짝은 코드로 고친다(기준선에 안 더한다). 고치기 둘
2. `bash scripts/verify.sh --corpus`(7~12분). 허용 변화 —
   - `golden/corpus/ddl-unreadable.txt`·`ddl-summary.json` — 「못 읽은 줄」 이 **느는** 것(`DdlAlter` 가 알리는 ALTER). 새 줄이 든 파일을 열어 그 ALTER 가 §3-10 의 「못 읽음」 꼴인지 본다
   - `ddl-lint.json` — 컬럼·제약·인덱스 수가 **느는** 것(ALTER 로 더한 것·KEY 컬럼). 주는 것은 DROP COLUMN 이 표본에 있을 때만(파일:줄을 이력에)
   - `sqlfixture.json`·`sqltables-diff.txt` — 「그쪽만」 에서 빠지는 줄(새로 맞음). 「우리만」 이 느는 줄은 허용 밖
   - `analyze-egov*.json`·`-dialect.txt`·`-missing-in-db.txt`·`deliverable-egov-relations.json` — 표·문장 참조가 **느는** 것, `dialect` 미해결이 주는 것. `missing-in-db` 에 새 이름이 생기면 그 이름이 별칭·컬럼(가짜 표)인지 본다 — 가짜면 `6-44` 로 돌아가 고친다
   - `gen-hr.json`·`hr-insert-b.txt` — INSERT 실패가 **주는** 것
   - `cleanup-*.json` — `5-115`·`5-116` 의 건너뜀 수 변화(`SIDE_EFFECT`·`EARLY_INIT` 가 늘고 `RAW` 가 준다)
   - 그 밖(검사 골든·`golden/corpus/db-*` 는 여기서 안 돈다)은 허용 밖 — 고치기 둘, 넘으면 `wip/V-65a`
3. diff 줄 수·종류별 건수·벽시계를 이력에
- 의존: 코드 행 전부(`9-8` 까지)

### R-32 — 손 판정 · 처분 표
- `design/compare-2026-10-28.md` 끝에 `## ㉟ 번들 86 — 조용히 틀린 출력 Critical 열셋·Important 열일곱·잔손 여섯(2026-11-15)`:
  1. 처분 표 — 키 36 × (행 · 강제 지점 시험 · 닫힘/남음/한계). 한계로 남긴 것(§1 Out of scope)을 키마다 한 줄
  2. V-65a 의 골든 변화 표(종류별 건수 전→후)
  3. 손 판정 — `SqlTables` 로 새로 잡힌 표 가운데 스무 줄(파일:줄 — SQL 본문은 안 적는다)이 진짜 표인지 · `ddl-unreadable` 새 줄의 ALTER 꼴 분포(동사별 건수) · `DdlGen` 예약어 경고가 는 표본 이름 열 개
  4. 끝에서 끝 — `run.bat --port 18860 --profile example --no-browser` → `curl -s -X POST http://127.0.0.1:18860/api/gen/dto -H "Content-Type: application/json" -d '{"ddl":"CREATE TABLE t (id INT); ALTER TABLE t ADD KEY VARCHAR(10);","packageName":"a.b","style":"RECORD"}'` 의 소스에 `KEY` 필드가 있다 → 서버를 끈다
- 코드·레인 지문이 안 바뀐다(고침이 나오면 그 행의 시험을 먼저 더해 따로 커밋)
- 의존: V-65a

### V-65 — `--db`
1. 여유 RAM — PowerShell `(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB`. 15 미만이면 7절
2. `bash scripts/docker-up.sh` → `bash scripts/verify.sh --db`(컨테이너 아홉, 17~35분). 다른 JVM 을 같이 띄우지 않는다
3. 허용 변화 —
   - `golden/corpus/db-*.json` — 새 열쇠 `t86Checks`·`t86Statements` · `ddlGenWarnings`·`alterWarnings` 가 **느는** 것(예약어 경고) · `ddlGenStatements`·`alterStatements` 가 **느는** 것(CHECK 문장) · `egovDiff.changed` 변화(CHECK·FK 규칙이 차이로 — 수와 방향을 이력에)
   - `db-*-alter-egov.txt` — CHECK 관련 줄이 더해지는 것
   - `db-*-insert-b.txt` — 실패가 **주는** 것
   - `db-*-ddl-type-drift.txt` — A-16·R-A-I1 로 줄이 주는 것
   - 벤더 메타 골든(`golden/meta/postgres-vendor.json` 등 — db 꼬리표 시험)에 `"autoIncrement" : true` 줄이 더해지는 것
   - `rulesChecksAutoRoundTrip`·`roFileMessages` 는 등급 A — 0 이어야 한다
4. `1-99` 의 `PostgresMetaSourceTest.zeroStatIsUnknown` 이 여기서 처음 돌았으면 RED(고침을 잠깐 되돌려)·GREEN 을 이력에
- `verify.sh --db` 와 `git push` 는 따로 낸다
- 의존: R-32

### 마무리
CLAUDE.md 「마무리」 1~8 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: ① 「조용히」 가 남은 자리 — 고친 길에서 경고·못 읽음·`notes` 없이 값이 빠지거나 바뀌는 곳(키 서른을 하나씩) ② 고침이 넓어 맞던 출력이 바뀐 자리 — 기존 골든 diff 를 행의 「의도된 골든 변화」 와 댄다 ③ `Column` 을 베끼는 자리에서 `autoIncrement` 가 떨어지는 곳 ④ `SqlTables` 의 새 쉼표 규칙이 가짜 표를 내는 꼴 ⑤ 절대 규칙 3 — 새 경고·`Unreadable`·`notes`·예외 글에 SQL 본문·값이 실리는 자리(`Unreadable` 80자는 DDL 이라 허용, INSERT 값·오류문은 아님) ⑥ 절대 규칙 7 — `Generator`·`XlsxWriter`·`MakeTemplates` 의 쓰기 자리) → `bash scripts/gate-probe.sh` → `verify.sh`(리뷰 고침이 건드린 무거운 레인만 다시 — db 레인 파일이면 새 행으로 돌린다) → push → PR(본문에 행마다 시작 세 줄 표) → CI 폴링 → claude[bot] 처분 → **머지 위임** `gh pr merge N --merge --delete-branch` → 끝 보고.

## 5. 금지 사항

- `pure/` · `rules.yaml` · `ext-fixes.yaml` · `config/pmd/exclude-from-failure.properties` · 기존 마이그레이션 V001~V015 를 고치지 않는다. 새 마이그레이션은 V016 하나
- 새 의존성 0(POI 5.4.1 의 `ss.formula` 는 이미 있다)
- 화면(`src/main/resources/tools/`)·템플릿(`templates/gen/`)·`mappings/`·`type-mapping.yaml` 을 고치지 않는다. `ddl-types.yaml` 은 `reserved` 줄만
- `QualitySql`·`Masking`·`CommentDdl`·`SqlJoins`·`SqlTokens`·`StructureLint` 를 고치지 않는다
- 기존 시험 단언을 지우거나 느슨하게 바꿔 통과시키지 않는다. 뜻이 바뀌는 단언은 행에 「의도된 변화」 로 적힌 것뿐(`5-116` 의 `pre`, `6-43` 의 머리 낱말) — 이력에 적는다
- 기존 경고·사유·미해결 글자를 바꾸지 않는다(골든·baseline 열쇠) — `"예약어·특수 글자 — 따옴표로 감쌌다"`·FK 규칙 경고·`MarkupFix.RAW`·`MIXED_EOL`·`Unresolved` 코드. 새 글은 새 상수
- self-check 기준선에 있는 메서드의 값을 올리지 않는다 · `DdlReader`·`AlterGen.Run`·`JavaGraph`·`Definitions` 에 메서드를 더하지 않는다(예외: `Run` 은 `1-89`·`1-91` 의 셈대로 끝이 40, `JavaGraph` 길이는 `0-94`·`6-43`)
- `SqlTables.extract`·`Generator.run`·`XlsxFiller.fill`·`InsertGen.generate`·`buildUpsert`·`Relations.infer`·`named` 에 분기를 더하지 않는다 — 도우미로
- `SnapshotDiff`·`AlterGen` 이 `autoIncrement` 를 비교하게 하지 않는다 · 벤더 MetaSource 에 자동증가 SQL 을 더하지 않는다
- `DdlAlter` 가 모르는 표의 ALTER 를 「못 읽음」 으로 내게 하지 않는다 · `HARMLESS` 밖 동작을 조용히 넘기지 않는다
- `DbCorpusBase` 의 기존 시험(`insertRuns`·`ddlRoundTrip`·`alterRoundTrip`·`mutate`)을 고치지 않는다 — 새 시험 하나만 더한다. `DbCorpus.stripSqlPlus`(시험 쪽 사본)도 안 고친다
- `0-95`·`5-114`·`5-100` 과 §1 Out of scope 를 같이 고치지 않는다
- 시험·경고·예외 글에 실제 사업 코드·SQL 을 쓰지 않는다
- worktree·`finishing-a-development-branch` 를 쓰지 않는다

## 6. 최종 검증

- `bash scripts/verify.sh` 초록 · `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify` 초록 · `bash scripts/verify.sh --corpus` 초록(등급 A 0) · `bash scripts/verify.sh --db` 초록(`rulesChecksAutoRoundTrip` 아홉 컨테이너 등급 A 0) · `bash scripts/gate-probe.sh` 열 전부 산다
- 강제 지점 시험이 전부 있다(§4 표) — 각 행 이력에 RED → GREEN 한 줄
- 손 확인(R-32): compare ㉟ 처분 표 키 36 · `curl` DTO 에 `KEY` 필드
- 회귀: `golden/` diff 는 행마다 적은 「의도된 골든 변화」 와 V-65a·V-65 의 허용 변화뿐 · 규칙 수 147 그대로 · 마이그레이션 16 · `Cleanup.OPTIONAL` 아홉 그대로
- CI 8 초록 · claude[bot] 「아니오」 처분 · PR 머지

## 7. 중단 조건

다음이면 임의로 풀지 말고 멈추고 보고한다(어느 스텝 · 무엇이 예상과 달랐나 · 가능한 선택지).
- 2절 구조 설명이 실제 코드와 다르다(줄 번호 어긋남은 아님 — 메서드·흐름이 다를 때)
- 검증이 2회 연속 실패하고 원인이 이 플랜 범위 밖
- 5절을 어기지 않고는 진행할 수 없다(특히 — 기준선 값을 올려야만 되는 고침, 기존 경고 글자를 바꿔야만 맞는 골든)
- 스텝에 없는 파일을 3개 이상 고쳐야 한다(골든·픽스처·`self-check.txt` 는 스텝에 있는 것이다)
- `1-86b` 에서 `migration-immutable` 훅이나 `DbTest` 가 V016 을 거절한다
- V-65a 에서 `AnalyzeCorpusTest.egov` 의 `table` 미해결(등급 A)이 새로 나오거나, `missing-in-db` 에 가짜 표가 두 번 고친 뒤에도 남는다
- V-65a 에서 `ddl-unreadable` baseline 이 상한(표본의 2%)을 넘겨 빨강
- V-65 에서 `alterRoundTrip`·`alterTwoReleases`·`ddlRoundTrip`(기존 등급 A)가 새로 빨강
- `--db` 앞 여유 RAM 15GB 미만(다른 세션 컨테이너 — 사람이 조율한다)

번들 모드의 「갈려도 안 멈춘다」 는 위 아홉 밖에서만 선다. 행당 계획 밖 결정 둘까지, 넘으면 `wip/<청크>`. `1-86a`·`1-86b` 가 `wip/` 로 빠지면 `1-89` 의 자동증가 절·`9-8` 의 자동증가 단언은 빼고 진행한다(끝 보고에 적는다). `1-94`·`1-95a` 가 빠지면 뒤 DdlReader 행은 그 가지 없이 `tables.get(맨 이름)` 으로 맞춘다.

## 8. 불확실 항목

사용자만 정할 수 있는 것 — 없음(범위·제외·자동증가 (가)·ALTER (가)·머지 위임 다 답 받음).

실행해야만 알 수 있는 것 — 실행자가 해당 스텝에서 확인한다.
- **H2·실물 드라이버가 `IS_AUTOINCREMENT` 를 어떻게 주나**(시험 실행·컨테이너) — `1-86b` 시험 · `9-8` 전제. 처분은 그 행 사다리
- **Jackson 이 레코드 성분의 `@JsonInclude(NON_DEFAULT)` 를 받나**(시험 실행) — `ColumnTest`. 안 받으면 접근자 메서드를 명시로 덮어 그 위에 붙인다
- **POI `shiftRows` 뒤 `AreaPtg` 가 어떤 형식으로 풀리나**(시험 실행) — `2-41` 사다리
- **`walk` 에서만 넘치는 입력이 있나**(시험 실행) — `0-94` 사다리
- **행마다 self-check 값**(시험 실행) — `-Dtest=SelfCheckTest`. 공통 사다리
- **표본에서 새 표·새 「못 읽은 줄」 이 얼마나 느나**(표본 폴더) — `verify.sh --corpus`. 처분은 V-65a 허용 변화·7절
- **실물 DB 가 생성 CHECK·자동증가 절·겹친 역슬래시를 받나**(Testcontainers) — `verify.sh --db`. 처분은 `9-8` 사다리
- **PMD·SpotBugs 새 짝**(빌드) — V-65a 의 1. 후보: 새 정규식 상수 · `DdlAlter` 의 긴 alternation · `FormulaRange` 의 `instanceof` 패턴 · `Acc.rejected`

첫 스텝 전에 — `git status` 깨끗 · `git log --oneline -1` 이 `docs: 설계 71 — 번들 86 행·번들 표` · `git merge-base --is-ancestor bf4dedc HEAD` · `ls C:/workspace/toolbox-corpus` · `node -v` · `docker info` 가 되는지.
