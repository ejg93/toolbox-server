# 설계 68 — 가로 점검 2차 결과(2026-11-14)

설계 67(보강판) 기준으로 지금 트리(`b1a3df5`, 코드는 `c1837db` 와 같음)를 영역 여덟 × 서로 모르는 Fable 점검자 둘로 점검하고, 지적마다 Opus 판정자가 탐침을 다시 돌려 확인한 결과다. 결함은 고치지 않았다 — 번들 84 설계(Fable)의 입력이다.

## 1. 요약

| 영역 | 확인 결함 | Critical | Important | Minor | n1 | n2 | m | 어림 N ≈ n1·n2/m | 판정이 뺀 것 |
|---|---|---|---|---|---|---|---|---|---|
| A 생성기 | 37 | 6 | 13 | 18 | 22 | 30 | 16 | 41 | 0 |
| B 산출물·보고 | 24 | 2 | 10 | 12 | 13 | 18 | 7 | 33 | 0 |
| C 프로그램 분석 | 20 | 2 | 4 | 14 | 14 | 13 | 7 | 26 | 설계 한계 1 |
| D 코드 검사 | 14 | 0 | 2 | 12 | 11 | 14 | 11 | 14 | 설계 한계 3 |
| E 코드 정리 | 19 | 12 | 1 | 6 | 12 | 11 | 4 | 33 | 0 |
| F DB 경계 | 22 | 9 | 4 | 9 | 16 | 15 | 9 | 27 | 제외 겹침 1 |
| G1 웹 | 22 | 2 | 1 | 19 | 17 | 18 | 13 | 23.5 | 0 |
| G2 CLI·설정·글·화면 | 15 | 1 | 3 | 11 | 12 | 11 | 8 | 16.5 | 0 |
| 합 | 173 | 34 | 38 | 101 | | | | 214 | 5 |

- 점검자 지적 245 건(#1 114 · #2 131)을 판정자가 모두 다시 돌렸다. 불확인 0 — 재현 의무(설계 67 ①)가 그럴듯한 오탐을 미리 걸렀다
- n1·n2 는 보고서 지적 수가 아니라 판정자가 뿌리별로 나누고(한 지적에 뿌리 둘) 묶은(한 보고서 안 같은 뿌리 둘) 결함 수다 — 그래서 n 합 247 이 지적 245 와 다르다
- 영역 사이 같은 뿌리 여섯(6절)을 묶으면 서로 다른 결함 167
- 보정 지적 가운데 본 점검이 다시 못 낸 채 남은 것 19(3절) — 합치면 번들 84 후보 186
- 어림 합 214 대 확인 173 — 영역마다 아직 못 찾은 결함이 있다는 뜻으로 읽는다. m 이 작은 E(4)·B(7)·C(7)는 두 점검자가 서로 다른 곳을 봤다(어림이 크고 불안정). m 이 n 에 가까운 D(11/14)는 거의 다 찾았다
- 한계 — 포획-재포획은 두 점검자가 같은 확률로 결함을 찾는다고 가정한다. 같은 점검표를 받은 같은 모델이라 쉬운 결함에 몰리면 N 은 작게 나온다(하한으로 읽는다)

## 2. 보정과 점검표 보강

- 보정 재현율 6/16 = 0.375(A 2/2 · G 3/10 · C 1/4, 옛 트리 `eb632c6`) — 설계 67 §11. 0.5 밑이나 재보정은 안 했다(사용자)
- 놓친 꼴을 범주로만 더한 설계 67 4.7 일곱(GET 부작용 · 정규식 20만 자 · 크기 상한 · 쓰기 자리 · 판정 우회 · Error 글 · 시간 초과 뒤 자식) 가운데 본 점검이 새로 잡은 것: ② 정규식·제곱 시간 열넷(A-10·B-20·C-JSP-QUAD·C-PROBE-REGEX·D-piiLogSOE·E-fixQuadratic·E-consoleRegexTime·G1-K13·G1-K17·G2-K9 등) · ③ 크기 상한 다섯(G1-K03·G1-K15·B-06·F-K10·F-K18) · ④ 쓰기 자리 넷(G1-K07·G1-K08·G1-K09·F-K19) · ⑤ 판정 우회 여덟(F-K1~K6·G2-K1·G1-K14) · ① GET 부작용 하나(C-VCS-SCOPE 의 `GET /api/check/vcs`). ⑦ 은 새로 잡은 것 없음
- 보정 지적의 정답 꼴 가운데 본 점검이 다시 낸 것: G-1(G1-K02) · G-2(G1-K01) · G-5(G1-K03) · 0-83b 꼴(크기 상한) — 4.7 보강이 들었다

## 3. 보정 지적 51 재확인(옛 트리 지적이 지금도 있나)

Fable 셋이 옛 탐침을 지금 트리(`target/classes`)로 다시 돌렸다(`design/68-evidence/recheck-{A,G,C}.md`).

| 영역 | 지적 | 남음 | 일부 남음 | 고쳐짐 |
|---|---|---|---|---|
| A | 22 | 22(M4 넷 가운데 긴 리터럴 하나만 고쳐짐) | 0 | 0 |
| G | 16 | 13 | 1(G-1 — 값 안 `#`·`,`·`}` 꼬리 꼴만) | 2(G-3 UNC 로드 거절 · G-4 Error → FAILED) |
| C | 13 | 11 | 1(I-4 — RUNNING 고착은 고쳐지고 파일 단위 건너뜀은 없음) | 1(I-5 fsmonitor) |
| 합 | 51 | 46 | 2 | 3 |

남은 48 가운데 본 점검 판정 행과 같은 결함은 「출처」 칸에 보정 id 로 붙였다. 본 점검이 다시 못 낸 19 는 아래다 — 등급은 보정 점검자의 것이고 Opus 판정은 안 거쳤다(재확인 점검자가 실행으로 「남음」 을 확인).

| 키 | 등급 | 자리 | 무엇이 틀렸나 · 사용자가 겪는 것 |
|---|---|---|---|
| R-A-C1 | Critical | core/gen/AlterGen.java:331·344-346 | MariaDB 코멘트만 바뀌어도 MODIFY COLUMN 이 AUTO_INCREMENT 를 뺀다(자동증가 수집 0) · 반영 뒤 INSERT 가 NULL 거절 |
| R-A-I1 | Important | DdlGen.java:336-349 | mssql→mssql `nvarchar(2147483647)`(MAX 여야) · CREATE 실패 |
| R-A-I4 | Important | AlterGen.java:576 | 이름 없는·SYS_ FK 대체 이름이 전부 같아 둘째 FK 실패 |
| R-A-I5 | Important | AlterGen.java:436-447 | 유니크 인덱스를 DROP CONSTRAINT 로 지움 · 실패 |
| R-A-I11 | Important | GenModel.java:121-122 · egov35 Detail.jsp.ftl:23 | `E_MAIL` → 속성 `EMail` 대 JSP `eMail` · EL 런타임 오류 |
| R-A-I13 | Important | DdlGen.java:351-369 | mssql bit DEFAULT `((1))` → PG `BOOLEAN DEFAULT 1` · CREATE 거절 |
| R-A-M2 | Minor | AlterGen.java:386 | 못 옮기는 기본값이 `MODIFY (GUID DEFAULT NULL)` 살아 있는 문장으로(경고만) |
| R-A-M5 | Minor | InsertGen.java:427 | MERGE 자리표시 `#{class}` 대 DTO 필드 `class_` |
| R-G-6 | Important | ProfileStore.java:125 · CheckRoutes.java:59 | 잘못된 규칙 정규식을 검증 없이 저장 → 규칙 목록 조회 500, 화면에서 되돌릴 길 없음 |
| R-G-9 | Important | core/text/LogSql.java:56-64 | 스레드 섞인 로그에서 다른 문장의 Parameters 를 짝지어 틀린 SQL(경고 없음) |
| R-G-14 | Minor | core/text/Csv.java:100 | 칸 가운데 따옴표 하나가 뒤 줄을 삼킴(본 점검 둘은 순수본과 같은 동작이라 버림 — 번들 84 설계가 동결 여부를 가린다) |
| R-G-15 | Minor | core/text/LineDiff.java:33-35 | 끝 줄바꿈 차이를 폴더 비교는 DIFF, 줄 비교는 차이 0 |
| R-C-1 | Critical | core/analyze/JavaGraph.java:463-467·510·527 · AnalyzeStore.save | DAO 이름 클래스의 `update("SQL 글")` 첫 인자를 문장 id 로 H2·xlsx 에 저장 · SQL 본문·값이 이력에(절대 규칙 3) |
| R-C-I2 | Important | JpaIndex.java:93·480·484 | 엔티티 이름 Order·Group·Set 이 JPQL 예약어와 겹쳐 통째로 빠짐 · 연관 경로 조인 미해결 없이 빠짐 |
| R-C-I3 | Important | JspLinks.java:95 | `.do` 아닌 URL 링크 0 건·미해결 없음 · `${ctx}` 붙은 것 버림 · 주석 속 링크 셈(상대 `.do` 는 설계 한계 — C-JSP-REL) |
| R-C-I6 | Important | JavaGraph.java:212·581 | implementors 열쇠가 단순 이름 → 남의 모듈 표가 CRUD 에 |
| R-C-I7 | Important | JavaGraph.java:225·406·422·468 | 조용히 빠지는 호출 꼴 다섯(this.sqlSession·지역 SqlSession·조상 @GetMapping·템플릿 메서드·필드 제네릭) — 미해결 0 |
| R-C-I8 | Important | MapperIndex.java:124·309 | iBATIS `<sqlMap>` 색인 0 · eGov 3.5 이하 CRUD 전부 빔 |
| R-C-M1 | Minor | SqlJoins.java:75-111 | 별칭 표가 문장 하나라 UNION·서브쿼리에서 관계가 사라지거나 지어짐 · CTE 이름이 표로 |

## 4. 영역별 확인 결함

판정 「확인」 만 싣는다. 재현 명령·실제 출력은 판정 파일(`design/68-evidence/judge-<영역>.md`)의 같은 키 행에 있다 — 탐침 소스 자체는 세션 폴더(scratch)에 있었고 저장소에 안 넣었으니, 번들 84 실행 세션은 판정 파일의 입력 꼴·실제 출력으로 RED 시험을 다시 만든다. 출처 — #1·#2 는 찾은 점검자, 짝 = 둘 다, 보정 id = 3절 재확인에서 남은 같은 결함.

### A 생성기(37)

| 키 | 등급 | 자리 | 무엇이 틀렸나 · 사용자가 겪는 것 | 출처 |
|---|---|---|---|---|
| A-01 | Critical | core/gen/DdlReader.java:47·318 | `KEY`·`PERIOD`·`INDEX` 로 시작하는 맨 컬럼을 제약 줄로 보고 버리고 가짜 인덱스를 만든다 · DTO·INSERT 에서 컬럼이 조용히 빠짐 | 짝 · C3 |
| A-02 | Important | DdlReader.java:193 | 표를 이름만으로 담아 스키마 다른 같은 이름 표의 앞 것이 사라짐 | 짝 · C3 |
| A-03 | Critical | DdlReader.java:87·224-237 | `ALTER … ADD/DROP/MODIFY` 와 CREATE 앞 ALTER 제약을 알림 없이 버림 · 마이그레이션 스크립트에서 컬럼이 빠진 DTO | 짝 · C3 |
| A-04 | Important | InsertGen.java:254-274 | 길이 4 문자 PK·TINYINT PK 값이 겹치고 경고 없음 · 생성 INSERT 가 PK 위반 | 짝 |
| A-05 | Important | InsertGen.java:155-165 | CHECK IN 허용 값을 컬럼 이름만으로 모아 다른 표의 같은 이름 컬럼에 씀 · CHECK 위반 | 짝 · I7 |
| A-06 | Important | InsertGen.java:430·438·610 | INSERT 의 표·컬럼 이름을 인용 안 함(DdlGen `id()` 와 어긋남) · 예약어·공백 이름 표 문법 오류 | 짝 · I6 |
| A-07 | Important | InsertGen.java:281-294 | TIME·YEAR·INTERVAL 컬럼에 `'VAL_n'` · 실행 실패 | 짝 · I8 |
| A-08 | Minor | InsertGen.java:126-127 | DDL 모드에서 `GENERATED ALWAYS AS IDENTITY` 를 자동증가로 못 알아봄 · 경고 없이 DB 거절 | #1 · I8 |
| A-09 | Important | DdlGen.java:275 | 표 단위 이름 공간 인덱스 이름을 그대로 내 스키마 단위 대상에서 부딪침 · 둘째 CREATE INDEX 실패 | 짝 · I3 |
| A-10 | Important | DdlReader.java:371·607·662 · DdlPull.java:47 · Validation | 공백·괄호·빈 줄 반복에 정규식 제곱 시간 · 30KB 본문 하나로 `/api/gen/dto` 50초, 요청 못 끊음 | 짝 |
| A-11 | Critical | DdlGen.java:231·389-403 | 변환 DDL 이 CHECK·FK ON DELETE·자동증가를 경고 없이 버림(AlterGen 은 규칙을 씀) · 조용히 다른 DB 가 만들어짐 | #2 · C2 |
| A-12 | Critical | Generator.java:98·108 | 출력 경로가 같은 두 표가 서로 덮어쓰는데 둘 다 `created` · 소스 일부가 사라진 채 성공 보고 | #2 · I9 |
| A-13 | Important | templates/gen/egov35 header.ftl:2 · Mapper.xml.ftl:3 · List.jsp.ftl | 코멘트를 이스케이프 없이 넣음 · `*/`·`--`·`"` 든 코멘트로 자바·XML·JSP 깨짐 | #2 · I10 일부 |
| A-14 | Important | resources/gen/ddl-types.yaml:26 | 예약어 목록에 LEFT·RIGHT·FETCH 등이 없어 맨 이름으로 나감(AlterGen 도) · CREATE·ALTER 실패 | #2 · I2 |
| A-15 | Critical | DdlGen.java:447 | 대소문자 섞인 따옴표 이름을 인용 없이 내 대상에서 다른 이름으로 접힘 · 대소문자만 다른 컬럼 충돌 | #2 |
| A-16 | Important | DdlGen.java:309-311 | 길이 없는 문자 타입(UUID·TEXT) PK·UNIQUE 컬럼이 CLOB 으로 · CREATE 실패, 경고 없음 | #2 |
| A-17 | Important | web/GenRoutes.java:126 | 활성 프로필 없이 `?save=true` → `LogicalRoutes.outFile` 500 | #2 · 6절 ② |
| A-18 | Minor | DtoGenerator.java:219 | javadoc 코멘트의 `\u` 를 안 막음 · 생성 DTO 컴파일 실패 | 짝 |
| A-19 | Minor | Validation.java:122·125 | `@Min`·`@Max` 에 `L` 이 없음 · int 넘는 경계에서 컴파일 실패 | 짝 · I12 |
| A-20 | Minor | DtoGenerator.java:57-63 | record 성분 금지 이름(notify·wait·hashCode)을 안 피함 · 컴파일 실패 | 짝 · I12 |
| A-21 | Minor | DtoGenerator.java:272 | 표 이름이 비면 `charAt(0)` 예외 · 사유 없는 500 | 짝 · M4 |
| A-22 | Minor | DdlReader.java:379·382 | 크기 자릿수 넘침에 `parseInt` 예외 · 500 | #1 |
| A-23 | Minor | web/GenRoutes.java:42-54 | 패키지 마디에 자바 예약어를 받음 · 컴파일 안 되는 package 문이 200 | 짝 |
| A-24 | Minor | GenRoutes.java:61 · InsertRoutes.java:54 | 본문 JSON `null`·`skipTokens:[null]` 이면 NPE 500 | #2 · 6절 ⑦ |
| A-25 | Minor | InsertGen.java:391 | 행 수를 경고 없이 1000 으로 자름 | #1 |
| A-26 | Minor | InsertGen.java:586-606 | CSV 머리 중복 컬럼 · 컬럼 목록 겹치고 값이 바뀜, 경고 없음 | 짝 · M4 |
| A-27 | Minor | InsertGen.java:569-571·649-655 | CSV 날짜를 모양만 봐 없는 날짜(2월 30일)가 리터럴로 | #1 |
| A-28 | Critical | InsertGen.java:468-470 · DdlGen.java:479-481 | MariaDB 대상 리터럴에서 `\` 를 안 겹침 · 리터럴이 안 닫힘(생성 출력까지만 확인, 실 DB 실행 못 함) | 짝 · C4 |
| A-29 | Minor | DdlReader.java:43·367-368 | 기본값 식을 첫 `)`·쉼표·공백에서 자르고 IDENTITY 의 `AS` 를 기본값으로 읽음 | #1 · M1 |
| A-30 | Important | DdlReader.java:136-139·714-735 | 문장 가운데 줄머리 `SET NULL` 을 SQL*Plus 명령으로 지움 · FK 와 뒤 CREATE TABLE 하나가 사라짐 | #1 |
| A-31 | Minor | resources/gen/type-mapping.yaml:13·18·19 | 접두 맞춤으로 LONG RAW→String, BIT(8)→Boolean, INT UNSIGNED→Integer, 배열→String | #2 · M3 |
| A-32 | Minor | InsertGen.java:47 | CHECK_IN 이 값 안 `)` 에서 끊김 · 닫히지 않은 리터럴 | #2 |
| A-33 | Minor(잠재) | DdlGen.java:397-402 | 참조 컬럼·부모 PK 둘 다 없으면 `REFERENCES T ()` — 지금 진입로로는 못 닿음 | #2 |
| A-34 | Minor | InsertGen.java:494-499 | CSV 머리에 PK 가 없으면 DDL 에 PK 가 있어도 「PK 를 못 찾아」 라며 첫 컬럼으로 MERGE | #2 |
| A-35 | Minor | DdlReader.java:314 | 따옴표 안 공백 든 컬럼 이름을 못 읽어 `unreadable3` 컬럼이 INSERT 에 | #2 |
| A-36 | Important | templates/gen/egov35/Mapper.xml.ftl:13 | 매퍼 SQL 에 예약어 컬럼을 인용 없이 넣음 · CRUD 실행 때 SQL 오류 | #2 |
| A-37 | Minor(잠재) | DdlReader.java:40·93 | DDL 의 `ON DELETE CASCADE` 를 안 읽음 — 지금 그 규칙을 쓰는 소비자 없음 | #2 |

### B 산출물·보고(24)

| 키 | 등급 | 자리 | 무엇이 틀렸나 · 사용자가 겪는 것 | 출처 |
|---|---|---|---|---|
| B-01 | Critical | core/report/XlsxFiller.java:93-95 | 데이터 행 아래를 밀기만 하고 합계 수식 범위를 안 넓힘 · 양식 합계가 첫 행 값만 센 채 나감 | 짝 |
| B-02 | Critical | core/deliverable/Relations.java:67 · Definitions.java:399 | 표를 대문자 이름만으로 색인 · 같은 이름 표가 두 스키마면 04 관계가 먼저 정렬된 스키마에 붙고 둘째는 빠짐 | #1 |
| B-03 | Important | resources/logical/masking.yaml:15·19·24 · Masking.java:113-149 | 키워드·정규식 한 마디만 맞아도 개인정보 후보 · `_YN`·`_CD`·`_AT` 컬럼을 `*` 로 덮는 UPDATE, 03 개인정보 Y(표본에서 되풀이) | 짝 |
| B-04 | Important | core/logical/Masking.java:287-296 | 영문·숫자 꼴이면 예약어도 안 감쌈(QualitySql 은 감쌈 — 판정 중복 어긋남) · `User`·`Order` 표 마스킹 SQL 문법 오류 | 짝 |
| B-05 | Important | web/LogicalRoutes.java:221 | `outFile` 이 `orElseThrow` · 프로필 없이 띄운 서버의 저장 다섯 라우트가 사유 없는 500(Outputs·TableRoutes 는 default) | #1 · G-7 · 6절 ② |
| B-06 | Important | web/LogicalRoutes.java:56·156 | 컬럼목록 CSV 가 Javalin 기본 본문 상한 1MB 에 걸려 JSON 아닌 413 | #1 · 6절 ① |
| B-07 | Important | Relations.java:143-173 · DeliverableService.java:215-216 | PK 이름이 같은 표를 전부 짝지어 n(n-1)/2 행 · 1,500 표에서 xlsx 행 상한을 넘어 실패, 0 바이트 작성안내가 남음 | #1 |
| B-08 | Important | core/quality/QualitySql.java:99-102 | keydup 키를 둘까지만 치환 · 셋째 키를 말없이 버리고 하나면 `후보키2` 자리표시자가 남음 | #2 |
| B-09 | Important | web/LogicalRoutes.java:109-116 | 마스킹 제외 키에 스키마가 없음 · 다른 스키마 같은 컬럼도 빠져 개인정보가 안 가려짐 | #2 |
| B-10 | Important | web/DeliverableRoutes.java:430-435 | 08 코드값 조회가 IllegalArgumentException 만 잡음 · 없는 표 등 DB 거절이 사유 없는 500 | #2 |
| B-11 | Important | core/deliverable/CodeAndLink.java:135-138 | 코드명 컬럼이 비면 행 읽기가 범위를 넘음 · 500 | #2 |
| B-12 | Important | Definitions.java:357-362 | CHECK 를 낱말 맞춤으로 컬럼에 붙임 · 리터럴 안 낱말이 컬럼 이름과 같으면 남의 CHECK 가 03 제약조건에 | #2 |
| B-13 | Minor | ColumnInputs.java:62 → Dialect.java:46-62 · CommentDdl.java:42-53 | 소유자·표가 비어도 점을 이어 `.EMP`·`..COL` · 실행 불가 DDL 이 200 | 짝 |
| B-14 | Minor | core/logical/Dialect.java:46·56 | COMMENT DDL 이 예약어 컬럼을 안 감쌈 · 문법 오류 | #1 |
| B-15 | Minor | QualitySql.java:91 | 표 없는 nopk 가 식별자 검사에 걸려 400(화면은 대개 비켜 감) | #2 |
| B-16 | Minor | DeliverableService.java:126 | 매핑 누락을 문서를 쓰는 중에 발견 · 앞 문서만 쓴 폴더가 남음 | 짝 |
| B-17 | Minor | XlsxFiller.java:152-166 | 머리 글이 겹치면 뒤 칸만 채움 · 앞 칸이 경고 없이 빔 | 짝 |
| B-18 | Minor | resources/logical/masking.yaml | 여권·운전면허 종류가 없고 `TEL_NO` 를 못 잡음 | 짝 |
| B-19 | Minor | core/deliverable/StructureLint.java:558-566 | SCC 를 재귀로 돌아 표 2만 개 FK 사슬에서 StackOverflowError | #1 |
| B-20 | Minor | core/report/TableXlsx.java:66 | 닫는 `>` 없는 `<` 연속에서 태그 정규식 제곱 시간 · 표 내보내기 요청 수십 초 | #2 |
| B-21 | Minor | DeliverableRoutes.java:256 · LogicalRoutes.java:244-246 | 목록 안 null 원소 NPE 500 | #2 · 6절 ⑦ |
| B-22 | Minor | DeliverableService.java:118-120 | 모르는 문서 번호를 건너뛰고 성공(skipped 에도 없음) | #2 · G-13 |
| B-23 | Minor | XlsxFiller.java:96-98 | 데이터 행 병합을 둘째 행부터 안 이음 | #2 |
| B-24 | Minor | core/report/Mapping.java:21 | 매핑 columns 값이 null 이면 사유가 예외 클래스 이름뿐 | #2 |

### C 프로그램 분석(20)

| 키 | 등급 | 자리 | 무엇이 틀렸나 · 사용자가 겪는 것 | 출처 |
|---|---|---|---|---|
| C-PROBE-MSG | Critical | core/analyze/SqlProbe.java:240-242 · web/AnalyzeRoutes.java:433 | xlsx 로 가는 DB 오류문에서 SQL 을 떼는 판정이 `near '…'` 꼴만 앎 · `DB대조-N.xlsx` 에 매퍼 SQL 본문·리터럴 값이 저장(절대 규칙 3, H2 로 확인) | 짝 |
| C-TBL-COMMA | Critical | core/analyze/SqlTables.java:223-259·316-334 | 괄호·`@DBLINK`·ON 절이 닫힌 뒤 쉼표가 표 문맥을 안 엶 · CRUD·영향도에서 표가 미해결 없이 빠짐(표본 92 문장) | 짝 · C-2 |
| C-VCS-EQ | Important | core/vcs/WorkingCopy.java:161-177 | 필터 이름에 `=` 가 들면 `-c filter.<이름>.clean=` 이 다른 키로 갈려 받은 소스의 clean 명령이 `git status` 에서 돈다 | #1 |
| C-VCS-SCOPE | Important | WorkingCopy.java:162 | 필터 이름을 바깥 저장소 `--local` 로만 모아 `config.worktree`·안에 든 저장소 필터 명령이 돈다(`GET /api/check/vcs` 로도) | 짝 |
| C-TBL-DELALIAS | Important | SqlTables.java:245·412-437 | `DELETE 별칭 FROM 표 별칭` 에서 별칭을 대상 표로 · 없는 표가 D·U, 진짜 표는 R(표본 16) | 짝 · I-1 |
| C-RUN-SOE | Important | core/analyze/AnalyzeRunner.java:165-167 | Java 파일 하나의 StackOverflowError 를 못 가둠 · 분석 전체 FAILED, 어느 파일인지 모름 | 짝 · I-4 · 6절 ⑧ |
| C-TBL-KEYWORD | Minor | SqlTables.java:239 · SqlTokens.java:21 | 따옴표를 벗긴 뒤 예약어에 대어 예약어 이름 표가 미해결 없이 빠짐 | #1 |
| C-TBL-DOLLAR | Minor | SqlTables.java:239-251 | `이름_${…}` 이 `이름_$` 라는 표로, 미해결 없음 | #1 · M-2 |
| C-TBL-CTE | Minor | SqlTables.java:163 | WITH 이름을 문장 경계 없이 지워 같은 이름의 진짜 표가 빠짐 | 짝 |
| C-TBL-BSLASH | Minor | core/text/SqlTokens.java:111-118 | 역슬래시로 벗어난 홑따옴표를 리터럴 끝으로 읽어 뒤 표가 빠짐 | #1 · 6절 ④ |
| C-TBL-HASH | Minor | SqlTables.extract | MySQL `#` 줄 주석을 안 지워 주석 안 `FROM 이름` 이 표가 됨 | #1 |
| C-MAP-INCLUDE | Minor | core/analyze/MapperIndex.java:246-278 | 자기를 끼우는 `<sql>` 조각을 n^깊이로 펼치고 취소 확인이 없음 · OOM·취소 지연 | 짝 |
| C-JSP-QUAD | Minor | core/analyze/JspLinks.java:135-152 | `}` 마다 글 머리까지 훑는 제곱 시간 | #1 |
| C-PROBE-REGEX | Minor | SqlProbe.java:57 | `TAIL_ORDER_BY` 정규식 제곱 시간 | #1 |
| C-PROBE-DUPCOL | Minor | SqlProbe.java:188 | 파생 표로 감싸 같은 이름 컬럼 SELECT 가 거짓 ERROR | #2 · M-3 |
| C-PROBE-DBID | Minor | SqlProbe.java:133-158 | 같은 파일 `databaseId` 변형 둘을 방언 파일 여럿으로 봐 대조 건너뜀 | #2 |
| C-PROBE-SEMI | Minor | SqlProbe.java:176-179 | 끝 `;` 뒤 주석이면 「여러 문장」 거절로 건너뜀 | #2 · M-3 |
| C-TBL-MULTIUPD | Minor | SqlTables.java:245-249 | MySQL 여러 표 UPDATE 에서 SET 이 고치는 둘째 표가 R | #2 |
| C-JOIN-SET | Minor | core/analyze/SqlJoins.java:27-55 | SET 대입 `B.X = A.X` 를 조인 등식으로 냄(결함 여부 갈릴 수 있음) | #2 |
| C-WEB-NULL | Minor | AnalyzeRoutes.java:119-120·250·430-433 · DiffRoutes.java:29-30 | 본문 JSON `null`·`rows:[null]` 이면 500 | #2 · 6절 ⑦ |

설계 한계로 뺀 것: C-JSP-REL(상대 `.do` 링크 버림 — `JspLinks` javadoc 이 밝힘).

### D 코드 검사(14)

| 키 | 등급 | 자리 | 무엇이 틀렸나 · 사용자가 겪는 것 | 출처 |
|---|---|---|---|---|
| D-guessLang | Important | core/check/CheckRunner.java:216 | 붙여넣기 언어 「자동」(기본값)이 글 어디든 `${` 면 jsp 로 정함 · `@Value("${…}")` Java·템플릿 리터럴 TS 에서 AST·오염·tsx 규칙이 조용히 안 돎 | 짝 |
| D-sqlDollarCmt | Important | rules.yaml:219 대 MyBatisRules.java:239 | 매퍼 `${` 판정이 두 벌인데 `security.sqlDollar` 는 SQL 주석을 안 뺌 · 주석 처리한 줄마다 error 오탐, 두 규칙 줄 집합이 어긋남 | 짝 |
| D-textBlock | Minor | core/check/RegexRule.java:166 | cLike 가 텍스트 블록을 몰라 블록 안 `/*` 가 주석을 열어 파일 끝까지 지움 · 그 아래 정규식 규칙 0 건 | 짝 |
| D-parseErrCount | Minor | CheckRunner.java:206 | 「파싱 실패」 수를 걸러진 지적에서 셈 · 규칙을 끄거나 표식이 있으면 0 | 짝 |
| D-piiLogSOE | Minor | rules.yaml:477(security.piiLog) | `log.info(` 뒤 약 2,000자 한 줄에서 스택 넘침 · 그 파일 전체가 「못 읽음」 | 짝 |
| D-runTextSOE | Minor | CheckRunner.java:184-189 | 붙여넣기 길이 StackOverflowError 를 안 잡아 작업 FAILED(폴더 길은 잡음) | 짝 · 6절 ⑧ |
| D-taintScope | Minor | core/check/Taint.java:219-228 | 같은 이름 지역 변수를 범위로 안 가름 · error 등급 sqlInjection·pathTraversal 오탐 | 짝 |
| D-dupId | Minor | MyBatisRules.java:196-199 | `databaseId`·`<sql>` 이름 공간을 안 봐 error 등급 dupId 오탐 | 짝 |
| D-dollarFragment | Minor | MyBatisRules.java:235-239 | SAX 조각마다 SQL 주석을 지워 엔티티·CDATA 뒤 `${}` 오탐·누락 | 짝 |
| D-a11yMultiline | Minor | rules.yaml:629·637 · RegexRule.java:63-67 | 부정 전방 탐색이 줄 끝까지 봐 여러 줄 태그 오탐·같은 줄 앞 태그 누락 | 짝 |
| D-ignoreInString | Minor | core/check/Ignores.java:20 | 문자열 리터럴 안 `tb-ignore` 도 표식으로 읽어 그 줄 지적이 전부 억제(문서와 다름) | 짝 |
| D-noWhereSubquery | Minor | MyBatisRules.java:229 | 서브쿼리 안 WHERE 만 있는 전체 UPDATE 를 놓침 | #2 |
| D-jspScriptComment | Minor | RegexRule.java:144 | jsp 꼴이 `<script>` 안 `//`·`/* */` 주석을 안 지워 주석 처리한 alert·console·debugger 지적 | #2 |
| D-overrideValidate | Minor | RegexRule.java:49 · RuleSet validate | 덮어쓰기 `params.max` 가 수가 아니어도 적재 통과 → 검사 중 FAILED, 모양 틀린 덮어쓰기는 말없이 무시 | #2 |

설계 한계로 뺀 것 셋: `tb-ignore a, b` 둘째 id(문법 문서가 밝힘) · 람다 몸 오염 누락(Taint javadoc) · 깊은 식 파일의 폴더 길 「못 읽음」(0-85f 의도).

### E 코드 정리(19)

| 키 | 등급 | 자리 | 무엇이 틀렸나 · 사용자가 겪는 것 | 출처 |
|---|---|---|---|---|
| E-eqRecv | Critical | core/check/ValueAst.java:264 · ValueFix.java:173 | 기본 켬 `java.equalsLiteralFirst` 가 수신자 형식을 안 봄 · 비대칭 equals 결과가 뒤집힌 코드가 컴파일 통과로 나감 | #1 |
| E-consoleSide | Critical | core/check/Cleanup.java:561-620 | 기본 켬 console 지우기가 인자의 부수 효과(pop·증감·대입)째 지움 · 정리한 JS 가 다른 값으로 돔 | 짝 |
| E-backtickParity | Critical | Cleanup.java:530-547·565-585 | 템플릿 리터럴 판정이 백틱 홀짝 · 리터럴 안 console·debugger 줄을 지워 문자열 값이 바뀜 | 짝 |
| E-regexParen | Critical | Cleanup.java:626-652 | 정규식 리터럴 안 `\(` 를 괄호로 세어 무관한 문장 둘을 같이 지움 | #2 |
| E-htmlTextArea | Critical | Cleanup.java:525-620 · ThScopeFix.java:77 | HTML `<pre>`·`<textarea>` 안 글을 코드·마크업으로 봐 화면 글·textarea 값이 바뀜 | #2 |
| E-switchHeadComment | Critical | core/check/SwitchFix.java:80-82 | switch 머리 줄에서 열린 블록 주석 꼬리까지 옮겨 사이 case 가 주석 안으로 | #1 |
| E-xmlNoDecl | Critical | core/check/EncodingFix.java:88-104 | 선언 없는 XML(UTF-8)을 프로필 MS949 로 바꿔 씀 · 매퍼 XML 파서가 못 읽음 | #1 |
| E-fieldOrder | Critical | core/check/OrderFix.java:232·284 | 기본 켬 `java.declarationOrder` 가 접근 제어 섞인 인스턴스 필드를 다시 놓음 · 리플렉션 차례(엑셀·직렬화 열)가 바뀜 | #2 |
| E-loggerInit | Critical | core/check/LogFix.java:175·227 | 고른 규칙 `common.sysout` 이 로거보다 먼저 초기화되는 싱글턴 생성자 println 을 바꿈 · 클래스 로딩 NPE | #1 |
| E-interruptLoop | Critical | core/check/InterruptFix.java:87·109 | 고른 규칙이 조건부 return 하나만 있어도 루프 탈출로 봄 · 바쁜 루프 | #1 |
| E-twrQuietClose | Critical | core/check/ResourceAst.java:518-527 · ResourceFix.java:115 | finally 의 조용히 닫기를 try-with-resources 로 바꿈 · 삼키던 close 예외가 밖으로, catch 몸 삭제(closeQuietly 꼴과 처리 어긋남) | #2 |
| E-twrFormBCatch | Critical | ResourceAst.java:584-606 | 꼴 B 는 try 에 catch 가 있어도 고침 · close 예외가 그 catch 로 가 반환값이 바뀜(꼴 A 는 거절 — 어긋남) | #2 |
| E-soe | Important | core/check/CleanupRunner.java:98·166 | 파일별 catch 가 RuntimeException 만 · 파일 하나의 StackOverflowError 로 폴더 작업 전체 FAILED | 짝 · 6절 ⑧ |
| E-logDomainType | Minor | LogFix.java:48·227 | 단순 이름 `Log` 인 도메인 형식 필드를 로거로 봄 · 컴파일 실패 | #1 |
| E-piiQuoteParity | Minor | core/check/PiiFix.java:60·84 | 따옴표 홀짝 판단으로 `"it's"` 뒤 맨 숫자를 가림 · JS 구문 오류 | #1 |
| E-mixedEol | Minor | core/fs/LocalFiles.java:311 → CleanupRunner.java:184 | 섞인 줄끝을 다수 쪽으로 다시 써 안 고친 줄도 바뀜 | 짝 · 6절 ⑥ |
| E-fixQuadratic | Minor | core/check/MarkupFix.java:95-103 · PiiFix.java:84 | 끼울 자리마다 앞머리 재훑기로 제곱 시간, 파일 안 취소 안 들음 | #1 |
| E-consoleRegexTime | Minor | Cleanup.java:595·647 | console 뒤 긴 공백·`<script ` 반복에 정규식 역추적 제곱 | #2 |
| E-applyNull | Minor | CleanupRunner.java:140 · CleanupRoutes apply | `files:[null]` 이면 400 대신 FAILED + NPE 글이 응답에 | #2 · 6절 ⑦ |

### F DB 경계(22)

| 키 | 등급 | 자리 | 무엇이 틀렸나 · 사용자가 겪는 것 | 출처 |
|---|---|---|---|---|
| F-K1 | Critical | core/conn/ReadOnlyJdbc.java:61-68·103 | 블록 주석을 첫 `*/` 에서 닫아 겹친 주석(`/* /* */ SELECT */ …`) 뒤 문장이 실행 · COMMENT·SET PASSWORD·RUNSCRIPT·SHUTDOWN(절대 규칙 6, H2 실행 확인) | #1 |
| F-K6 | Critical | ReadOnlyJdbc.java:35-36·72 | 쓰기 낱말 정규식이 ASCII 로만 대소문자를 접음 · 점 없는 ı 로 쓴 INSERT 가 실제로 행을 넣음(H2 실행 확인) | #2 |
| F-K2 | Critical | ReadOnlyJdbc.java:35-36·69 | 여러 문장 판정이 `;` 뿐 · `;` 없이 이은 SHUTDOWN·BACKUP·KILL·DENY·DBCC 가 통과(H2 대역 — SQL Server 실행은 못 봄) | 짝 |
| F-K3 | Critical | ReadOnlyJdbc.java:36·37 | `\b` 가 숫자와 글자 사이에 없어 `1INTO`·`1FOR SHARE` 통과(H2 대역) | #1 |
| F-K4 | Critical | ReadOnlyJdbc.java:102-105 | MySQL 실행 주석 `/*! … */` 를 주석으로 지움 · DB 는 안을 실행(H2 대역) | #1 |
| F-K8 | Critical | core/meta/SnapshotDiff.java:81-113·137-150 | 비교가 CHECK·FK 규칙을 안 봄 · 바뀌어도 「차이 없음」, 변경이력·반영 DDL 에서 빠짐 | 짝 |
| F-K9 | Critical | SnapshotDiff.java:153-166 | 표 키가 스냅샷마다 따로 정해져 한쪽에만 같은 이름 표가 두 스키마면 그대로인 표가 삭제+추가 · 반영 DDL 이 CREATE·DROP | 짝 |
| F-K17 | Critical | core/profile/Profile.java:61-63 → ConnectionRegistry.java:48-51 | URL 안 비밀 판정을 `password1=`·`%70assword=`·`sslpassword=`·`trustStorePassword=` 가 지나 `/api/conn` 응답에 그대로(절대 규칙 2) | #1 · 6절 ③ |
| F-K21 | Critical | core/dialect/PostgresMetaSource.java:28·86 | `reltuples` 0 을 실제 0 행으로 받음 · 행 수가 틀리고 skipEmpty 면 표가 스냅샷에서 사라짐(H2 흉내 표로 확인) | #2 |
| F-K5 | Important | ReadOnlyJdbc.java:36-37·76 | 잠금 판정을 주석 끼운 `FOR/**/SHARE`·SQL Server 잠금 힌트가 지남 · 운영 DB 잠금(H2 대역) | 짝 |
| F-K10 | Important | core/meta/SnapshotStore.java:90-100·155 · V001 | 길이 넘치는 값 하나(코멘트 4,001자·스키마 401개·메모 1,001자)로 수집 뒤 스냅샷 통째 FAILED, 오류 글에 내부 INSERT 와 값 앞머리 | 짝 |
| F-K12 | Important | core/dict/DictStore.java:266-321 · DictRoutes.java:35-120 | 사전 길이 넘침이 500, 가져오기는 앞 항목만 남고 다시 눌러도 같은 자리에서 멈춤 | 짝 |
| F-K13 | Important | DictStore.java:266-300 | 머리·구분자를 못 알아본 기관표준 파일도 DELETE 뒤 0 건 커밋 · 사전이 빔 | #2 |
| F-K11 | Minor | core/meta/RowCounter.java:67-82 | 이름이 쓰기 낱말이거나 `;` 든 표는 COUNT 가 자기 래퍼에 거절, 경고는 시간 초과와 합쳐짐 | 짝 |
| F-K14 | Minor | MetaRoutes.java:43·67-68 · TableRoutes.java:29-30 · DictRoutes.java:36 | 본문 JSON `null` 이면 NPE 500 | 짝 · 6절 ⑦ |
| F-K15 | Minor | DictRoutes.java:99·132 | 사전 CSV 내보내기 수식 인젝션(`=`·`+`·`-`·`@`) | 짝 |
| F-K16 | Minor | DictRoutes.java:90-100 | export `kind` 를 검증 없이 Content-Disposition 에 넣음 · 머리 인자 주입 | #1 |
| F-K18 | Minor | core/sqlrun/SqlRunner.java:114-125 | CLOB 을 상한 없이 다 읽음 · OOM | #1 |
| F-K19 | Minor | core/db/Db.java:33 | data 폴더 경로의 `;` 가 H2 설정이 됨 · 기동 실패 또는 H2 파일이 밖에 | #1 |
| F-K20 | Minor | SqlRunner.java:92-127 · ReadOnlyJdbc.java:160·296-301 | Array·SQLXML 값이 `ReadOnlyJdbc(JdbcArray)` 글로 | #2 |
| F-K22 | Minor | core/dialect/OracleMetaSource.java:51·150 | 자동 NOT NULL 판정 `\w` 가 ASCII · 한글 컬럼의 NOT NULL 이 CHECK 로(H2 흉내 표) | #2 |
| F-K23 | Minor | DictStore.java:340-347 | 검색어 LIKE 와일드카드 미이스케이프 | #2 |

제외 겹침으로 뺀 것: F-K7 시퀀스 진행(`NEXT VALUE FOR` — compare ㉜ ⑪ 부작용 함수와 같은 뿌리).

### G1 웹(22)

| 키 | 등급 | 자리 | 무엇이 틀렸나 · 사용자가 겪는 것 | 출처 |
|---|---|---|---|---|
| G1-K01 | Critical | core/fs/LocalFiles.java:93-104 · FsRoutes.java:58 | 금지 목록에 프로필 폴더가 없어 `GET /api/fs/read` 가 프로필 YAML 을 비밀번호째 줌(절대 규칙 2) | 짝 · G-2 |
| G1-K02 | Critical | web/ProfileRoutes.java:33-34·123-137 | 따옴표 없는 비밀번호에 `#`·`,`·`}` 면 정규식이 앞만 지우고 `leaked` 도 꼬리를 못 잡아 백업에 비밀번호 꼬리 | 짝 · G-1 |
| G1-K03 | Important | web/App.java:142 · TextRoutes.java:14 · LocalFiles.java:41 | 본문 상한이 Javalin 기본 1MB · logsql 5백만 자·읽기 10MB 보다 먼저 413, 읽은 큰 파일 결과를 못 씀 | 짝 · G-5 · 6절 ① |
| G1-K04 | Minor | LocalFiles.java:178 | 잘못된 glob(중괄호 글롭 포함)이 사유 없는 500 | 짝 · G-11 |
| G1-K05 | Minor | FsRoutes.java:60·71 · TextRoutes.java:24 · ProfileRoutes.java:74·97 | 본문 JSON `null`·없는 charset 이 500 | 짝 · 6절 ⑦ |
| G1-K06 | Minor | LocalFiles.java:390·403·407-414 | rel 의 Windows 금지 글자·`...` 를 미리 안 걸러 500, 빈 결과 폴더 남음 | 짝 · G-11 |
| G1-K07 | Minor | LocalFiles.java:125-133 | 실제 경로 검사가 있는 경로만 · `data./새폴더`·정션 아래 새 폴더로 서버 `data/` 안에 씀 | 짝 |
| G1-K08 | Minor | LocalFiles.java:391 | 표식 판정이 `Path.equals` · `결과_출처.txt.` 로 출처 표식을 덮음 | 짝 · G-12 |
| G1-K09 | Minor | FsRoutes.java:103-108 · Outputs.java:31-34 | 프로필 `name`·`output.dir` 을 검사 없이 경로 조각으로 · `name: ../x` 면 `out/` 밖에 | 짝 |
| G1-K10 | Minor | ProfileRoutes.java:76-93 · ProfileStore.java:199 | 모르는 키 든 프로필 저장이 500, 백업 파일만 남음 | #1 |
| G1-K11 | Minor | core/job/JobManager.java:138-143 | 작업 200 개에서 끝난 작업을 전부 지워 방금 끝난 작업도 404 | 짝 |
| G1-K12 | Minor | LocalFiles.java:183-225 | 목록 걷기가 정션을 따라가고 금지 폴더를 안 거름 · 순환 되풀이, `data/` 안 이름 노출 | 짝 |
| G1-K13 | Minor | LocalFiles.java:178·210 | 별 여럿 glob 역추적으로 요청 스레드가 안 끝남 | #1 |
| G1-K14 | Minor | web/LocalOnly.java:68 | Content-Type 판정이 Content-Length 있을 때만 · chunked `text/plain` 이 지나 파일이 써짐 | 짝 · G-16 |
| G1-K15 | Minor | App.java:142 · DictRoutes.java:54 · FsRoutes.java:71 | chunked·multipart 는 본문 상한이 없어 OOM 500 | 짝 · 6절 ① |
| G1-K16 | Minor | App.java:83·244 | `serve --port` 범위 밖이 스택을 찍음 | #1 |
| G1-K17 | Minor | ProfileRoutes.java:34 | PASSWORD 정규식이 값 안 긴 공백에서 이차 시간 · 저장 수십 초 | #1 |
| G1-K18 | Minor | core/text/LogSql.java:118-125 ← TextRoutes.java:33 | 타입 없는 파라미터 줄에서 logsql 제곱 시간 | #2 · 6절 ⑤ |
| G1-K19 | Minor | LocalFiles.java:293 | 잠긴 파일 읽기 IOException 이 사유 없는 500 | #2 |
| G1-K20 | Minor | ProfileRoutes.java:79-83 · ProfileStore.java:195 | 프로필 저장이 원자적이지 않음 · 동시 읽기 400, 동시 저장 500 | #2 |
| G1-K21 | Minor | LocalFiles.java:296-309 | UTF-16 파일을 MS949 로 풀어 깨진 글이 조용히 나감 | #2 |
| G1-K22 | Minor | LocalFiles.java:311-328·422-425 | 섞인 줄끝을 다수 쪽으로 다시 씀(javadoc 「줄바꿈 그대로」 와 어긋남) | #2 · 6절 ⑥ |

### G2 CLI·설정·글·화면(15)

| 키 | 등급 | 자리 | 무엇이 틀렸나 · 사용자가 겪는 것 | 출처 |
|---|---|---|---|---|
| G2-K1 | Critical | core/profile/Profile.java:61 | `PASSWORD_IN_URL` 이 Oracle 따옴표 비밀번호·`sslpassword`·`trustStorePassword`·`accessToken` 을 못 봄 · url 이 `/api/conn`·`/api/profiles/{이름}` 응답에 값째(절대 규칙 2) | 짝 · 6절 ③ |
| G2-K2 | Important | cli/CodeCommands.java:43·85 ↔ CheckRoutes.java:536 | `check --groups` 가 나머지 묶음을 안 끔 · 형상 훅이 다른 묶음 지적으로 빨개짐, 이름 오타도 통과 | 짝 · G-8 |
| G2-K10 | Important | core/text/SqlTokens.java:111 | 리터럴 끝을 `''` 로만 가려 `\'`·`$$`·`q'[…]'` 뒤 표가 분석 결과에서 경고 없이 빠짐 | 짝 · 6절 ④ |
| G2-K14 | Important | core/profile/ProfileStore.java:172 · ProfileRoutes.java:90 | `saveCodeCheck` 블록이 0열 주석에서 끊김 · 저장 500(백업만 남음)·늘 409·`rules:` 키 둘 | #2 |
| G2-K3 | Minor | ProfileStore.java:65 | YAML 별칭을 앵커 이름 글자로 읽거나 틀린 사유로 거절, CP949 사유는 영어 클래스 이름뿐 | 짝 |
| G2-K4 | Minor | ProfileStore.java:29·65 | 중복 키·둘째 문서를 조용히 받음 · 앞 접속이 말없이 사라짐 | 짝 |
| G2-K5 | Minor | ProfileStore.java:161 | 흐름 꼴·따옴표 키 `codecheck` 머리를 못 찾아 둘째 블록을 붙임 | 짝 |
| G2-K6 | Minor | cli/Batch.java:110 | 낡은 활성 프로필이 프로필 필요 없는 명령까지 막음(끝 코드 2) | #1 |
| G2-K7 | Minor | cli/MetaCommands.java:51 · LogicalCommands.java:52 · Serve.java:63 | 없는 파일·범위 밖 포트가 `[오류]` 대신 자바 스택 | 짝 |
| G2-K8 | Minor | cli/SelfTest.java:133·136 | 못 읽는 프로필을 경고·틀린 글로 내고 실패 0 | #1 |
| G2-K9 | Minor | core/text/LogSql.java:124-125 | 타입 없는 파라미터에서 제곱 시간 | 짝 · 6절 ⑤ |
| G2-K11 | Minor | tools/table_builder_ext.js:140·154·175 | JSX 출력이 style·불 속성·속성 값 안 `>`·주석·`--` 변수를 틀리게 옮김 | #1 |
| G2-K12 | Minor | profiles/example.yaml:15 | 예시 주석이 없는 저장 라우트를 말함 | #1 |
| G2-K13 | Minor | cli/CodeCommands.java:110-126·182 | `check --fail-on` 이 못 읽은 파일을 안 세 끝 코드 0 | #2 · G-10 |
| G2-K15 | Minor | cli/DocCommands.java:53·72 | `deliverable --docs` 모르는 번호를 조용히 버림 | #2 · G-13 |

## 5. 버린 것

- 불확인 0 — 245 건 모두 재현
- 판정이 세지 않은 것 5: 설계 한계 4(C-JSP-REL · D 셋) · 제외 겹침 1(F-K7 — compare ㉜ ⑪)
- 점검자가 스스로 「제외 겹침」 으로 표시하고 세지 않은 것: 0-86·0-87 꼴(UNC·링크) · compare ㉜ 「파일당 시간 상한」(a11y 정규식 255KB 24.9초) · ⑪ 부작용 함수 · `QualitySql` 대소문자 · `Cli` join
- 점검자가 못 돌려 「버린 후보」 로 둔 것은 지적이 아니다(재현 의무). 그 가운데 다음 점검에서 볼 만한 것: `.git/config` `include.path` UNC(규칙 1 — 네트워크 금지라 못 돌림) · MSSQL·Oracle 실물에서의 래퍼 우회(F-K2~K5 는 H2 대역까지만)

## 6. 영역 사이 같은 뿌리

번들 84 행은 아래를 하나로 묶는다(합치면 173 → 167).

| | 묶음 | 키 | 한 자리 |
|---|---|---|---|
| ① | 요청 본문 상한 | G1-K03 · B-06(· G1-K15 chunked) | `App.create` 본문 상한 설정 |
| ② | 프로필 없을 때 내려받기 자리 | B-05 · A-17 · 보정 G-7 | `LogicalRoutes.outFile` 대 `Outputs.dir` |
| ③ | URL 안 비밀번호 판정 | G2-K1 · F-K17 | `Profile.PASSWORD_IN_URL` |
| ④ | SQL 토큰 리터럴 끝 | G2-K10 · C-TBL-BSLASH | `SqlTokens.literalEnd` |
| ⑤ | LogSql 제곱 시간 | G2-K9 · G1-K18 | `LogSql.params` |
| ⑥ | 섞인 줄끝 | G1-K22 · E-mixedEol | `LocalFiles.read`·`encode` |

묶지 않았으나 한 처방이 여럿을 덮는 꼴(판정 중복 — 설계 67 4.6):

- ⑦ JSON `null` 본문·목록 안 null → 500: G1-K05 · F-K14 · A-24 · B-21 · C-WEB-NULL · E-applyNull — 본문 읽기 한 자리
- ⑧ 파일 하나의 StackOverflowError 를 파일 단위로 못 가둠: C-RUN-SOE · E-soe · D-runTextSOE — `CheckRunner.java:179`(0-85f) 하나만 가둔다
- ⑨ 식별자 인용 판정이 흩어짐: A-06 · A-14 · A-36 · B-04 · B-14 · A-15 — `DdlGen.reserved`·`id()` 하나로
- ⑩ 같은 이름 표 다른 스키마: A-02 · B-02 · B-09 · F-K9
- ⑪ CHECK·FK 규칙 빠짐: A-11 · F-K8 · A-37
- ⑫ `ReadOnlyJdbc.check` 우회 다섯(F-K1~K6) — 첫 낱말·정규식 판정 대신 토큰 판정

## 7. 번들 84 설계에 넘길 것

- Critical 34 + 보정 Critical 2(R-A-C1 · R-C-1) — 앞쪽 행. 절대 규칙에 닿는 것 먼저: 규칙 2(G1-K01 · G1-K02 · G2-K1·F-K17) · 규칙 3(C-PROBE-MSG · R-C-1) · 규칙 6(F-K1~K4 · F-K6)
- 코드 정리 Critical 12(E) — 기본 켬 여덟(E-eqRecv · E-consoleSide · E-backtickParity · E-regexParen · E-htmlTextArea · E-switchHeadComment · E-xmlNoDecl · E-fieldOrder)은 고치기 전까지 기본 끔으로 내리는 선택지가 있다 — 사람이 고른다
- Important 38 + 보정 Important 12 — 번들 84 행
- Minor 101 + 보정 Minor 5 — 사람이 고를 대기 목록. 6절 ⑦·⑧ 처럼 한 자리 고침이 여럿을 덮는 것은 Minor 여도 묶어 행으로 올릴 만하다
- 「확인(H2 대역)」 F-K2~K5 · F-K21 · F-K22 와 「출력만」 A-28 은 실물 DB 에서 다시 재야 한다 — 번들 84 의 db 레인(Testcontainers)에서 RED 시험으로 확인
- 어림 N 이 크고 m 이 작은 영역(E · B · C · A)은 번들 84 뒤 3차 점검의 첫 대상
