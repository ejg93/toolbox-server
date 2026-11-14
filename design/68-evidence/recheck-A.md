남음 22 · 고쳐짐 0 · 모름 0

C1 · 남음 · core/gen/AlterGen.java:331,344-346 · P3-1 `MODIFY COLUMN id BIGINT NOT NULL COMMENT '회원 번호'` 뒤 H2(MariaDB) INSERT → `NULL not allowed for column "ID"`. src/main/java 에 IS_AUTOINCREMENT 수집 0(grep)
C2 · 남음 · core/gen/DdlGen.java:389-403(FK)·231-279(CHECK) · P2-2 CASCADE FK + CHECK 표 → `ADD CONSTRAINT FK_CHILD_P FOREIGN KEY (PID) REFERENCES PARENT (ID);` 규칙·CHECK 없음, `warnings=[]`(oracle·postgresql 둘 다)
C3 · 남음 · core/gen/DdlReader.java:47-49,318-339·193·87-88,224-237 · P1-b CONFIG 컬럼 ID·VALUE 만, `ix=[Index[name=VARCHAR2, … columns=[100]` · P1-a `TABLE s2.CODE` 하나뿐 · P1-c T 컬럼 ID 만 — 셋 다 `unreadable=[]`
C4 · 남음(손 대조 + 출력) · core/gen/InsertGen.java:468-470,660 · core/gen/DdlGen.java:479-481 · 지금 소스 두 자리 다 작은따옴표만 겹침, 역슬래시 처리 없음. P4-5 mariadb `'C:\temp\new'`·`'D:\data\'` · P2-5 mariadb `COMMENT '끝이 역슬래시\'`
I1 · 남음 · core/gen/DdlGen.java:336-349 · P2-1 mssql→mssql `memo nvarchar(2147483647),`
I2 · 남음 · src/main/resources/gen/ddl-types.yaml:26-30 · core/gen/DdlGen.java:447-457 · P2-3 `LEFT INTEGER, START TIMESTAMP, CONDITION …, FETCH …, TOP INTEGER, "ORDER" INTEGER` → H2 세 모드 다 `Syntax error … [*]LEFT … expected "identifier"`
I3 · 남음 · core/gen/DdlGen.java:275 · P2-4 `CREATE INDEX idx_user_id ON reviews (user_id)` → `Index "IDX_USER_ID" already exists`
I4 · 남음 · core/gen/AlterGen.java:576 · P3-2 SYS_ FK 둘 다 `FK_CH_2` → `Constraint "FK_CH_2" already exists`(DdlGen 길은 FK_CH_1·FK_CH_2)
I5 · 남음 · core/gen/AlterGen.java:436-447 · P3-3 `ALTER TABLE USERS DROP CONSTRAINT UX_USERS_EMAIL` → `Constraint "UX_USERS_EMAIL" not found`
I6 · 남음 · core/gen/InsertGen.java:430,438,534 · P4-1 `INSERT INTO Artist( ArtistId, Name )` → `Table "artist" not found (candidates are: "Artist")` · P4-2 `sys_dict( key, desc, order )` → `Syntax error … [*]key`
I7 · 남음 · core/gen/InsertGen.java:155-165 · P4-3 MEMBER(CHAR(1))·BOARD(NUMBER(1)) 의 STATUS 에 `'READY'`·`'DONE'` → `Value too long for column "STATUS CHARACTER(1)"` · `Data conversion error`
I8 · 남음 · core/gen/InsertGen.java:126-127,281-294 · P4-4 TIME·YEAR·RAW 에 `'VAL_1'` → `Cannot parse "TIME" constant "VAL_1"` · t6 `Generated column "PUBLIC.T6.ID" cannot be assigned` · t5·t7(mssql identity) `warnings=[]`
I9 · 남음 · core/gen/Generator.java:84-111 · P5-5 `files=40 warnings=[]` · `DUP 2x …/service/UserVO.java tables=[TB_USER, TN_USER]`(열 경로 전부) · 디스크 UserVO 는 TN_USER 것
I10 · 남음 · core/gen/GenModel.java:113-122 · templates/gen/egov35/VO.java.ftl:14,32-53 · Controller.java.ftl:77-78 · P6 `variable pageSize is already defined` · `variable searchVO is already defined in method insertSearch` · `CalcVO.java:23 illegal start of type` · P5 `BoardConfVO.java:32 illegal unicode escape`
I11 · 남음 · core/gen/GenModel.java:121-122 · templates/gen/egov35/Detail.jsp.ftl:23 · P6-4 `java.beans property names: [EMail, NAge, memberId, …]` vs `JSP | <c:out value="${result.eMail}"/>`·`${result.nAge}`(EL 실행은 안 함 — 옛 탐침과 같은 판정)
I12 · 남음 · core/gen/Validation.java:122,125 · core/gen/DtoGenerator.java:57-61,224-227 · P5-1 `@Max(99999999999)` → `Pay.java:19 integer number too large` · P5-2 `illegal record component name hashCode`·notify·wait
I13 · 남음(손 대조 + 출력) · core/gen/DdlGen.java:351-369 · 지금 소스: 괄호 벗긴 뒤 숫자면 그대로 돌려줌, 대상 타입 BOOLEAN 분기 없음. P2-5 mssql→postgresql `is_active BOOLEAN DEFAULT 1 NOT NULL`
M1 · 남음 · core/gen/DdlReader.java:173,632-656,738-760,43 · P7 `mysqldump 꼴 -> tables=[a(2)] unreadable=1`(b·c·d 없음) · `BOM + CRLF -> tables=[b(1)]` · P1-e `def=((0)`·`def=(getdate()`·`def=N'`·`def=TO_DATE('`
M2 · 남음 · core/gen/AlterGen.java:386 · P3-4 `ALTER TABLE T MODIFY (GUID DEFAULT NULL);` + `warnings=[T.GUID: 기본값 식은 옮기지 않았다 …] review=0`
M3 · 남음 · src/main/resources/gen/type-mapping.yaml:10,13,18(19 보다 앞) · P2-5 `javaType(LONG RAW)=String javaType(INT UNSIGNED)=Integer javaType(BIT len8)=Boolean`
M4 · 남음(넷 중 셋 — 긴 리터럴만 고쳐짐) · core/gen/DtoGenerator.java:259-273 · core/gen/InsertGen.java:577(fromCsv 머리)·229,298(PK 값) · P5-4 `className(TB_) -> StringIndexOutOfBoundsException`·`className(_)` 같음 · P7 `csv dup header -> INSERT INTO s( id, id ) VALUES( 2, 2 )` · P4-7 CHAR(1)·NUMBER(2) 에 `VALUES( '1', 111 )` `warnings=[]`. 고쳐진 쪽: P7 `Validation long literal OK`·`DdlGen long default OK`(옛 트리는 둘 다 StackOverflowError — DdlGen.java:57,59 · Validation.java:70,268)
M5 · 남음 · core/gen/InsertGen.java:427 · P4-6 MERGE `#{class} AS CLASS` vs DTO `private String class_;`

방법: 옛 탐침 P1~P7 을 recheck-A/ 로 옮겨 지금 트리(main/tree/target/classes + main/cp.txt)로 컴파일·실행 — API 변경 없어 탐침 본문은 경로만 고침. 같은 탐침을 옛 트리로도 돌려(recheck-A/old/) 출력을 맞댐 — 차이는 P7 의 두 줄(M4 긴 리터럴)뿐. 소스 차이도 core/gen 13 파일 중 DdlGen·Validation 둘(정규식 소유 한정자 + DdlGen.reserved 추가로 71행 뒤 +5줄)뿐, 템플릿·yaml·core/meta 는 같음. 줄 번호는 main/tree/src/main/java/kr/ejg/toolbox/ 아래 기준(templates·resources 는 트리 루트 기준). 옛 보고의 GenModel:238-257 은 파일이 169줄이라 지금 줄(113-122)로 바로잡음.
