# 판정 A — Opus (2026-11-14)

(핸드백 글을 실행 세션이 저장)

판정자 폴더 `A/judge-A/`. 원래 탐침 소스·입력을 `p1/`(#1)·`p2/`(#2)로 복사. `run1.sh`·`run2.sh` 는 경로만 바꿈. 새 탐침 `p2/MapperSql.java`(A/2-27 확인용). 서버는 `judge-A/srv` 에서 127.0.0.1:18731, 끝에 끔. 판정자 java 남지 않음. 원래 탐침 폴더·tree·target 안 고침.

진입로 주의: `DdlReader.read` 를 부르는 곳은 `GenRoutes`(DTO)·`InsertRoutes`(INSERT) 둘뿐. `DdlGen`·`AlterGen`·`Generator` 는 스냅샷 표만 받는다. DdlReader→DdlGen 탐침 지적은 스냅샷 꼴로도 생기는지 따로 봤다. A-33·A-37 은 앱 진입로가 없어 「잠재」.

| 결함 키 | #1 id | #2 id | 판정 | 등급 | 보정 같음 | 파일:줄 | 한 문장 | 재현(판정자 명령 + 실제 출력 한 토막) |
|---|---|---|---|---|---|---|---|---|
| A-01 | A/1-01 | A/2-01 | 확인 | Critical | 보정 C3 와 같음 | core/gen/DdlReader.java:47(CONSTRAINT_LINE)·318 | `KEY`·`PERIOD`·`INDEX` 로 시작하는 맨 컬럼을 제약 줄로 보고 조용히 버리고 가짜 인덱스까지 만든다 | `run1.sh P1.java read in-key.sql` → 컬럼 `id`·`val` 둘, `idx=[Index[name=VARCHAR…columns=[50]]]`, `unreadable=[]`. `node srv-probe.mjs 18731 key` → `record AppConfig( Long id, String val )`. `run2.sh Probe read in/r2.sql` → CONF 에 KEY·PERIOD 없음 |
| A-02 | A/1-02 | A/2-13(스키마 부분) | 확인 | Important | 보정 C3 와 같음 | DdlReader.java:193 | 표를 이름만으로 담아 스키마가 다른 같은 이름 표의 앞 것이 사라진다 | `read in-dupschema.sql` → `표 sales.emp` 하나, `unreadable=[]`. r1 → `S2.DUP` 만 |
| A-03 | A/1-03 | A/2-13(ALTER 부분) | 확인 | Critical | 보정 C3 와 같음 | DdlReader.java:87·224-237 | `ALTER … ADD 컬럼`·`ADD COLUMN`·`ADD (…)`·DROP·MODIFY 와 CREATE 앞 ALTER 제약을 알리지 않고 버린다 | `read in-alter.sql` → 컬럼 `id` 하나, `unreadable=[]`. `run2.sh Probe read in/r1.sql` → EMP ID·NM, `uqs=[]` |
| A-04 | A/1-04 | A/2-06 | 확인 | Important | — | core/gen/InsertGen.java:264-274(+254-256 TINYINT) | 길이 4 문자 PK 는 순번이 잘려 같은 값이 되풀이되고 TINYINT PK 는 129행째에 겹친다. 경고 없음 | `run1.sh P1.java ins in-varchar4.sql postgresql 3 PostgreSQL` → `'C_00'` 반복, `H2 실패(문장 2): Unique index or primary key violation`. tinyint 130 → `실패(문장 129)`. `run2.sh Probe ins … in/i1.sql 12` → `'C_00'`×9·`'C_01'`×3 |
| A-05 | A/1-05 | A/2-05 | 확인 | Important | 보정 I7 와 같음 | InsertGen.java:155-165(checkIns) | CHECK IN 허용 값을 컬럼 이름만으로 모아 다른 표의 같은 이름 컬럼에 쓴다 | `ins in-checkin2.sql … B` → B.STATUS `'A'`, `Check constraint violation`. i2 → MEMBER `'NEW'`·`'PAID'`, `rows in MEMBER = 0` |
| A-06 | A/1-06 | A/2-08 | 확인 | Important | 보정 I6 와 같음 | InsertGen.java:430·438·610 대 DdlGen.java:447 | INSERT 의 표·컬럼 이름을 인용하지 않아 DdlGen `id()` 와 판정이 어긋난다 | `ins in-reserved.sql` → `Syntax error … [*]ORDER … expected "identifier"`. i4 → `INSERT INTO [*]Order Details(` |
| A-07 | A/1-07 | A/2-07 | 확인 | Important | 보정 I8 와 같음 | InsertGen.java:281-294 | TIME(·YEAR·INTERVAL) 컬럼이 끝 갈래로 떨어져 `'VAL_n'` 이 된다 | `ins in-time.sql` → `Cannot parse "TIME" constant "VAL_1"`. i1 TM_T 12건 모두 같은 실패 |
| A-08 | A/1-08 | — | 확인 | Minor | 보정 I8 와 같음 | InsertGen.java:126-127 | DDL 모드에서 `GENERATED ALWAYS AS IDENTITY` 를 자동증가로 못 알아보고 경고도 없다 | `ins in-identity.sql` → `warnings=[]`, `Generated column "PUBLIC.T.ID" cannot be assigned` |
| A-09 | A/1-09 | A/2-10 | 확인 | Important | 보정 I3 와 같음 | core/gen/DdlGen.java:275 | 표 단위 이름 공간의 인덱스 이름을 그대로 내 스키마 단위 대상에서 부딪친다 | `run1.sh P4.java in-ddlgen-index.sql mariadb postgresql PostgreSQL` → `warnings=[]`, `Index "IDX_NAME" already exists`. d2 → oracle 대상 같은 실패 |
| A-10 | A/1-10 | A/2-14 | 확인 | Important | — | DdlReader.java:607·662·371·353(TYPE_END)·471, DdlPull.java:47, Validation ANY_ARRAY | 붙여 넣은 DDL 의 공백·괄호·빈 줄 반복에 정규식이 제곱 시간으로 돌고 요청 스레드는 끊을 수 없다(자리 일부만 겹침 — 스레드 덤프에서 607 이 양쪽 입력에 다 나옴) | `P2.java re a/b/c 20000·40000` → a 993→5017ms, b 827→5493ms, c 900→3630ms. 10만 자 덤프 → `openParen:607`·`statements:662`·`item:371`. `Slow 30 20000/40000` → col-spaces 4047→17495ms, many-cols 6325→TIMEOUT. HTTP 30KB `/api/gen/dto` → `200 time=50.4s` |
| A-11 | — | A/2-02 | 확인 | Critical | 보정 C2 와 같음 | DdlGen.java:231·389-403 | 변환 DDL 이 CHECK·FK ON DELETE 규칙·자동증가를 경고 없이 버린다(AlterGen:587 은 규칙을 쓴다) | `run2.sh FkRuleProbe`(스냅샷 꼴 메타) → `FOREIGN KEY (P_ID) REFERENCES P (ID);` CHECK 없음, `warnings=[]`(oracle·postgresql). d6 → `ID NUMBER(19) NOT NULL` |
| A-12 | — | A/2-03 | 확인 | Critical | 보정 I9 와 같음 | core/gen/Generator.java:98·108 | 출력 경로가 같은 표들이 서로 덮어쓰는데 둘 다 `created` 로 보고된다 | `run2.sh Gen egov35 oracle in/g1.sql gen-g1` → created 30줄, 디스크 20 파일, `AbCVO.java` 안 `public class ABcVO` |
| A-13 | — | A/2-04 | 확인 | Important | 보정 I10 와 일부 같음 | templates/gen/egov35/header.ftl:2·Mapper.xml.ftl:3·List.jsp.ftl | 템플릿이 표·컬럼 코멘트를 이스케이프 없이 넣어 자바·XML·JSP 가 깨진다 | gen-g1 NoticeVO javac → `error: class, interface, enum, or record expected`. `Xml …Notice_SQL_oracle.xml` → `XML FAIL The string "--" is not permitted within comments.` JSP `title="제목 "표시용" <b>"` |
| A-14 | — | A/2-09 | 확인 | Important | 보정 I2 와 같음 | resources/gen/ddl-types.yaml:26 · DdlGen.java:447 | 예약어 목록에 LEFT·RIGHT·FETCH 등이 없어 맨 이름으로 나간다(AlterGen 도 같다) | d1 → `LEFT INTEGER`, `warnings=[]`, `Syntax error … [*]LEFT`. alter a1→a2 → `ALTER TABLE EMP ADD ([*]LEFT INT)` |
| A-15 | — | A/2-11 | 확인 | Critical | — | DdlGen.java:447(id) | 대소문자 섞인 따옴표 이름(`"userId"`)이 인용 없이 나가 대상에서 다른 이름으로 접힌다 | d4 pg→pg → `userId int NOT NULL, userid int NOT NULL`, `Duplicate column name "USERID"`, `warnings=[]` |
| A-16 | — | A/2-12 | 확인 | Important | — | DdlGen.java:309-311 | 길이 없는 문자 타입(UUID·TEXT)이 PK·UNIQUE 컬럼이어도 CLOB 으로 바뀌고 경고 없음 | d7 pg→oracle → `ID CLOB NOT NULL`, `warnings=[]`, `Feature not supported: "Index on column: ""ID"" CHARACTER LARGE OBJECT` |
| A-17 | — | A/2-15 | 확인 | Important | — | web/GenRoutes.java:126 → LogicalRoutes.outFile:221 | 활성 프로필 없이 `?save=true` 면 사유 없는 500 | curl `/api/gen/dto?save=true` → `{"message":"서버 오류"} [500]`, 로그 `NoSuchElementException @ LogicalRoutes.outFile:221` |
| A-18 | A/1-11 | A/2-16 | 확인 | Minor | — | core/gen/DtoGenerator.java:219(doc) | javadoc 코멘트의 `\u` 를 막지 않아 생성 DTO 가 컴파일되지 않는다 | `P2.java dto in-dto-comment.sql BEAN -` + javac → `AtchFile.java:8: error: illegal unicode escape`. c1 → `FileInfo.java:6` 같은 오류 |
| A-19 | A/1-12 | A/2-17 | 확인 | Minor | 보정 I12 와 같음 | core/gen/Validation.java:122·125 | `@Min`·`@Max` 에 `L` 을 안 붙여 int 를 넘는 경계에서 컴파일되지 않는다 | dto-max → `@Max(999999999999)`, `Pay.java:17: error: integer number too large`. c1 → `@Max(3000000000)` 같은 오류 |
| A-20 | A/1-13 | A/2-18 | 확인 | Minor | 보정 I12 와 같음 | DtoGenerator.java:57-63(KEYWORDS) | record 성분 금지 이름(notify·wait·hashCode 등)을 피하지 않는다 | dto-record javac → `illegal record component name notify`·`wait`. c4 → `hashCode`·`wait` |
| A-21 | A/1-14 | A/2-19 | 확인 | Minor | 보정 M4 와 같음 | DtoGenerator.java:272(className) | 표 이름이 `_` 이거나 토큰을 뗀 뒤 비면 `charAt(0)` 이 던져 500 | `srv-probe tbname` → `dto TB_ → 500`·`dto _ → 500`, 로그 `StringIndexOutOfBoundsException @ DtoGenerator.className:272` |
| A-22 | A/1-15 | — | 확인 | Minor | — | DdlReader.java:379·382 | 괄호 안 크기를 바로 parseInt 해 자릿수 넘침이 500 | `read in-bignum.sql` → `NumberFormatException … DdlReader.item(DdlReader.java:379)`. 라우트 dto·insert 둘 다 500(`:379`·`:382`) |
| A-23 | A/1-16 | A/2-24(package 부분) | 확인 | Minor | — | web/GenRoutes.java:42-54(validPackage) | 패키지 마디에 자바 예약어를 받아 컴파일되지 않는 package 문을 200 으로 낸다 | `srv-probe pkg` → `200 package com.new.int;` |
| A-24 | — | A/2-24(null 부분) | 확인 | Minor | — | GenRoutes.java:61 · InsertRoutes.java:54 · DtoGenerator$Options:42 | 본문 `null`·`skipTokens:[null]` 이면 NPE 가 500 으로 나간다 | curl 본문 `null` → dto·insert `[500]`, skipTokens → `[500]`, 로그 `NullPointerException @ GenRoutes.lambda$register$1:61`·`InsertRoutes…:54`·`DtoGenerator$Options.<init>:42` |
| A-25 | A/1-17 | — | 확인 | Minor | — | InsertGen.java:391 | 행 수를 경고 없이 1000 으로 자른다 | `srv-probe rows` → `200 INSERT 수 1000 warnings []` |
| A-26 | A/1-18 | A/2-23(머리 중복 부분) | 확인 | Minor | 보정 M4 와 같음 | InsertGen.java:586-592·603-606 | CSV 머리에 같은 컬럼이 두 번이면 경고 없이 컬럼 목록이 겹치고 값도 바뀐다 | `csv in-csv.sql oracle in-csv-dup.csv` → `INSERT INTO FILE_INFO( ID, ID, PATH ) VALUES( 2, 2, 'a' )`, `warnings=[]`. i5c → `Duplicate column name "PATH"` |
| A-27 | A/1-19 | — | 확인 | Minor | — | InsertGen.java:569-571·649-655 | CSV 날짜를 모양만 보고 없는 날짜를 경고 없이 리터럴로 낸다 | `csv … mariadb in-csv-backslash.csv` → `'2023-02-30 00:00:00'`, `warnings=[]` |
| A-28 | A/1-20 | A/2-25 | 확인(출력만) | Critical | 보정 C4 와 같음 | InsertGen.java:468-470 · DdlGen.java:479-481 | MariaDB 대상 리터럴이 `'` 만 겹치고 `\` 는 그대로. 실 DB 실행은 못 함 | 같은 csv 명령 → `'C:\temp\'`. i5 mariadb → `'C:\upload\',`. d6 pg→mariadb → `COMMENT '경로 C:\'` |
| A-29 | A/1-21 | — | 확인 | Minor | 보정 M1 와 같음 | DdlReader.java:43(DEFAULT)·367-368 | 기본값 식을 첫 `)`·쉼표·공백에서 자르고 IDENTITY 의 `AS` 를 기본값으로 읽는다 | `read in-default.sql` → `def=AS`, `def=TO_DATE('`, `def=(getdate()`, `def=N'` |
| A-30 | A/1-22 | — | 확인 | Important | — | DdlReader.java:136-139(SQLPLUS)·714-735 | 문장 가운데 줄머리 `SET NULL` 을 SQL*Plus 명령으로 보고 지워 FK 와 뒤 CREATE TABLE 하나가 사라진다 | `read in-setnull.sql` → 표 p·c(z 없음), c FK 0, `unreadable=[… ON DELETE]` |
| A-31 | — | A/2-20 | 확인 | Minor | 보정 M3 와 같음 | resources/gen/type-mapping.yaml:13·18·19 | 접두 맞춤으로 LONG RAW→String, BIT(8)·BIT VARYING→Boolean, INT UNSIGNED→Integer, 배열→String | `Probe dto bean in/r3.sql oracle` → `private String f; private Boolean g; private Boolean l; private Integer e; private String h;` |
| A-32 | — | A/2-21 | 확인 | Minor | — | InsertGen.java:47(CHECK_IN) | 허용 값 안 `)` 에서 끊겨 닫히지 않은 리터럴이 나온다 | i3 → `'m(2`, `Syntax error` |
| A-33 | — | A/2-22 | 확인 | Minor(잠재) | — | DdlGen.java:397-402 | 참조 컬럼이 없고 부모에 PK 도 없으면 `REFERENCES LOG_T ()`. 스냅샷 FK 는 참조 컬럼이 늘 차서 앱 진입로 없음 | d5 → `REFERENCES LOG_T ([*])` 문법 오류, `warnings=[LOG_T: PK 없음]` |
| A-34 | — | A/2-23(upsert 키 부분) | 확인 | Minor | — | InsertGen.java:494-499 | CSV 머리에 PK 가 없으면 DDL 에 PK 가 있어도 「DDL에서 PRIMARY KEY를 찾지 못해」 라 하고 첫 컬럼으로 MERGE | i5b upsert → `-- ⚠ DDL에서 PRIMARY KEY를 찾지 못해 첫 컬럼(PATH)…`, `ON (T.PATH = S.PATH)` |
| A-35 | — | A/2-26 | 확인 | Minor | — | DdlReader.java:314(item) | 따옴표 안에 공백이 든 컬럼 이름을 못 읽어 `unreadable3` 컬럼이 INSERT 에 실린다 | `Probe read in/i4.sql` → `COL [unreadable3] type=[null]`, unreadable 에 `"Unit Price" INT` |
| A-36 | — | A/2-27 | 확인 | Important | — | templates/gen/egov35/Mapper.xml.ftl:13 | 매퍼 SQL 에 예약어 컬럼을 인용 없이 넣어 CRUD 실행 때 SQL 오류 | gen-g1 매퍼 14행 `NOTICE_ID, TITLE, ORDER, USE_YN`. 판정자 `MapperSql.java`(H2 Oracle) → `SQL FAIL Syntax error … [*]ORDER` |
| A-37 | — | A/2-28 | 확인 | Minor(잠재) | — | DdlReader.java:93·40 | DDL 의 `ON DELETE CASCADE` 를 안 읽는다. 지금 DdlReader 결과의 FK 규칙을 쓰는 소비자는 없다 | `Probe read in/d3.sql` → `deleteRule=null, updateRule=null` |

n1 = 22 · n2 = 30 · m = 16 · N ≈ 22·30/16 ≈ 41

- 「확인(출력만)」 A-28 을 넣고 셌다. 빼면 n1 = 21 · n2 = 29 · m = 15 · N ≈ 40.6
- 한 보고서 안 뿌리가 다른 것을 섞은 지적은 나눴다: A/2-13 → A-02·A-03, A/2-23 → A-26·A-34, A/2-24 → A-23·A-24. A/1-04 의 TINYINT 는 같은 뿌리로 하나
- A-10 은 짝으로 셌지만 정규식 자리가 일부만 겹친다. 따로 세면 m = 15, N ≈ 44
- 등급별: Critical 6(A-01·A-03·A-11·A-12·A-15·A-28) · Important 14 · Minor 17
- 제외 겹침 0. A/1-20·A/2-25 는 쓰는 쪽(InsertGen·DdlGen)이라 ⑥(DdlReader 읽는 쪽)과 자리가 다르다. A/2-11 은 DdlGen 이라 「QualitySql 대소문자」 와 자리가 다르다
- 설계 한계 0
- 보정 행 가운데 이번 본 점검 둘 다 다시 내지 않은 것: C1, I1, I4, I5, I11, I13, M2, M5(AlterGen·JSP EL·MERGE 별칭 쪽)

## 불확인

없음. 50 개 지적을 모두 다시 돌려 같은 결함을 얻었다. 출력 차이 둘: A/2-14 HTTP 57.3s → 50.4s · A/2-23 경고 목록에 `PK 없음 — 첫 컬럼을 키로 가정` 도 함께 나옴(보고서가 인용한 글은 SQL 주석 쪽).

잠재 A-33(A/2-22)·A-37(A/2-28)은 탐침 출력은 재현됐지만 지금 앱 진입로에서는 사용자에게 드러나지 않는다.
