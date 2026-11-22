# 설계 76 — 가로 점검 3차 결과(2026-11-22)

## 1. 기준 · 방법 · 한계

- **범위** — 번들 84~90 이 바꾼 파일: `git diff --name-only ceb6881 57760d1`(`ceb6881` = 점검 2차 #127 머지, `57760d1` = 번들 90 #135 머지) 가운데 `src/main`·`templates`·화면 127개(+5,846줄). 줄 번호는 `57760d1` 기준. 같은 파일의 안 바뀐 자리도 결함이면 적었다(Minor 까지)
- **기준** — 설계 67 의 4~7절 그대로: 절대 규칙 1·2·3·6·7 · 로컬 공격면 · 입력 극단값 · 조용히 틀린 출력 · 오류 경로 · 자원 · 판정 중복 · 번들 사이 어긋남. 등급은 사용자가 겪는 것으로(Critical — 절대 규칙 위반·조용히 틀린 채 나감 / Important — 기능이 실패하고 보임·표본에서 되풀이 / Minor — 드문 입력·바로 알아챔)
- **방법** — 영역 아홉에 점검자 하나씩(Explore 서브에이전트, Fable xhigh). 대상 파일을 통째로 읽고 `git diff` 와 설계 69~75 의 [고정]·「한계」 줄을 대조했다. 재현은 파일을 쓰지 않는 탐침만 — jshell 표준입력 + `target/classes`(HEAD 와 같은 빌드) · H2 메모리 · 메모리 XSSF · 읽기 전용 git. 저장소·`target/` 을 안 건드렸고 Docker·네트워크·`--db`·`--corpus` 를 안 썼다
- **판정** — 설계 세션이 지적 152 건의 인용 줄을 전부 코드로 다시 읽었다(오탐 0 — 점검자가 설계 한계·대기 행과 겹치는 것을 미리 뺐다). 다시 돌린 탐침: `SqlJoins`·`SqlTables` 꼴 열여덟(C1-02~06·09~12 · 6-75 아홉) · git lazy fetch(C2-01) · `ReadOnlyJdbc` 넷(F-01~03·08 — F-01 은 H2 의 표 코멘트가 실제로 바뀌었다)
- **2차와 다른 것** — 이중 독립 점검(설계 67 ④)과 옛 트리 보정(③)은 안 했다. 그래서 「남은 결함 어림 N」 이 없다
- **한계** — 점검 끝이 결함 없음은 아니다. 실물 DB(MySQL·PostgreSQL·SQL Server·Oracle)의 반응 · `subst`·네트워크 드라이브 · 파일을 쓰는 길은 못 돌렸다 — 그 지적은 「추적만」 이라 적었고 번들 91 의 시험(`--db` 컨테이너 포함)이 RED 로 다시 잰다

## 2. 요약

| 영역 | 대상 | 확인 | Critical | Important | Minor |
|---|---|---|---|---|---|
| A1 DDL 생성·읽기 | `core/gen` 의 AlterGen·DdlAlter·DdlGen·DdlPull·DdlReader | 22 | 0 | 8 | 14 |
| A2 생성기 나머지 | InsertGen·GenModel·DtoGenerator·Validation·템플릿·표 편집기 JS | 16 | 0 | 4 | 12 |
| B 산출물·보고 | `core/deliverable`·`report`·`quality`·`logical` | 16 | 0 | 3 | 13 |
| C1 분석 — SQL | SqlJoins·SqlProbe·SqlTables·MapperIndex·SqlTokens | 19 | 1 | 1 | 17 |
| C2 분석 — Java | JavaGraph·JavaViews·JpaIndex·JspLinks·TypeBind·`vcs` | 15 | 1 | 3 | 11 |
| D 코드 검사 | `core/check` 판정 쪽 · `rules.yaml` | 19 | 0 | 2 | 17 |
| E 코드 정리 | `core/check` 고침 쪽 | 17 | 2 | 5 | 10 |
| F DB 경계 | ReadOnlyJdbc·`meta`·`dict` | 10 | 4 | 2 | 4 |
| G 웹·파일·프로필·CLI | `web`·`fs`·`profile`·`cli`·LogSql | 18 | 0 | 4 | 14 |
| 합 | | **152** | **8** | **32** | **112** |

- Critical 여덟: C1-01(DB 대조 xlsx 에 매퍼 리터럴 값 — 규칙 3) · C2-01(부분 클론에서 git 이 스스로 원격 접속 — 규칙 1) · E-01(JSP 스크립트 꼬리 주석 뒤 `debugger;` 삭제로 다음 문장이 제어문 몸) · E-02(TS `f<T>()`·`cb!()` 든 console 문장 삭제) · F-01·F-02·F-03(읽기 전용 래퍼 우회 — 규칙 6) · F-04(PG 파티션 부모 표 누락)
- 뿌리가 같은 것: `<script>` 몸을 `<!-- … //-->` 로 감싼 옛 꼴을 HTML 주석으로 비우는 것(D-01 검사 · C2-03 링크 · E-01·E-07 정리) — egov JSP 743 가운데 181 파일
- 넓힘 하나: 설계 75 가 한계로 둔 「파생 표 뒤 쉼표 조인의 표」 를 결함으로 올려 `6-75` 에 묶었다(Oracle 식 쉼표 조인에서 관계가 조용히 빠진다 — 탐침 확인)
- 처분: 152 건 전부 번들 91(설계 77)의 행 73 개로. 행에서 한계로 남긴 조각과 사람 결정은 4절

## 3. 영역별 확인 결함

키 · 등급 · 자리 · 무엇 · 닫는 행(설계 77 §4). 재현 입력·실제 출력은 그 행의 「사실」·「시험」 에 있다.

## D 코드 검사(점검자 19 — 실행 탐침)
| 키 | 등급 | 자리 | 무엇 · 재현 요지 · 고침 방향 | 재확인 |
|---|---|---|---|---|
| D-01 | Important | RegexRule.java:154·81-86 | `<script>` 몸을 `<!-- … //-->` 로 감싼 옛 꼴 — `stripComments` 가 HTML 주석으로 스크립트 몸을 통째로 비워 `skipComments` 규칙(alert·domXss·jsEval·nonStandardDom)이 0. egov JSP 743 중 181. 고침: `skipped()` 에서 script 몸 안 `<!--`·`-->` 는 그 토큰만 비움. 순수본도 같은 동작 → 5-100 한 줄 | |
| D-02 | Important | RuleSet.java:177-184·208·110 | 규칙 정의 오류 셋(`flags:[x]` IAE · `params.when:"("` PSE · `customRules` 가 규칙 YAML 아님 IAE)이 `BadRule` 아님 → `CheckRoutes.getRules:100`·`CleanupRoutes:42·79·111` 500. 고침: load 의 IAE 를 BadRule 로 | |
| D-03 | Minor | LocalScopes.java:60-66 | 매개변수·패턴 변수와 같은 이름의 안쪽 지역 변수는 안 가름(선언 둘 이상만) → pathTraversal·sqlInjection 오탐. 고침: 매개변수·패턴 변수도 선언으로 셈 | |
| D-04 | Minor | LocalScopes.java:100 | 옛 switch — 범위를 SwitchEntry 하나로 잡아 뒤 갈래의 같은 변수가 다른 블록 선언과 섞임. 고침: SwitchStmt 전체 | |
| D-05 | Minor | ResourceAst.java:283-287 | `released` 가 메서드 몸 전체에서 이름으로만 — 형제 블록 같은 이름 자원이 닫히면 안 닫은 쪽도 해제로(누락). 고침: 선언 범위 블록 안만 | |
| D-06 | Minor | MyBatisRules.java:194·202·173 | 문장 시작 줄 = SAX Locator(시작 태그의 `>` 줄) — 여러 줄 시작 태그면 지적 줄 밀리고 `tb-ignore-next` 안 먹음 | |
| D-07 | Minor | MyBatisRules.java:247·185-188 | `<where>`·`<trim prefix=WHERE>` 태그가 서브쿼리 안에 있어도 「WHERE 있음」(noWhere 누락). 고침: whereTag 로 끊지 않고 topWhere 한 길 | |
| D-08 | Minor | MyBatisRules.java:246-259 | `noWhere` 가 `<sql>` 조각에도 돈다(오탐) | |
| D-09 | Minor | rules.yaml:225 | `security.sqlDollar` 파일 게이트 `<(?:mapper\|sqlMap)\b` 가 mybatis-config 의 `<mapper resource=…/>` 에도 맞아 `${url}` 를 error 로. pure-rules.json 이 같이 바뀜 → 5-100 감 | |
| D-10 | Minor | Ignores.java:46-49 · RegexRule.java:181-194 | tsx·jsx — JSX 글의 `'`(Don't)가 문자열을 열어 줄 끝 `// tb-ignore` 가 표식 아님(5-121 회귀). 5-127 한계와 뿌리 같음 | |
| D-11 | Minor | CheckRunner.java:212-224·22 | 붙여넣기 「자동」 — EL 없는 마크업·`public final class`·메서드만 붙인 자바를 js 로 추정 → 지적 0 | |
| D-12 | Minor | Rule.java:79-95 | 덮어쓰기 꼴 검증이 모르는 키(`enable:false`)를 말없이 버림 · `regex:[a,b]` 통과 | |
| D-13 | Minor | RuleSet.java:163 ↔ RegexRule.java:53 | `params.max:"5 "` — 검증은 trim, 쓰는 쪽은 parseInt 그대로 → 검사 중 NFE | |
| D-14 | Minor | JavaRules.java:113-116 | 프로필 `naming.*` 정규식을 적재 때 안 재서 검사 작업 안에서 PSE | |
| D-15 | Minor | Zones.java:42-61 | `rawLines` 가 `<script>` 안 JS 문자열·주석 속 `<textarea>`·`<pre>` 를 여는 태그로 → 파일 끝까지 날글 구역(정리 건너뜀 쪽) | |
| D-16 | Minor | ReturnTypes.java:101-105 | 조상에 인자 수 같은 오버로드가 있으면 틀린 반환형 → ValueFix 가 equals 뒤집음 | |
| D-17 | Minor | RegexRule.java:215 | 텍스트 블록이 `\\` 로 끝나면 닫는 `"""` 를 못 봄(JavaParser 3.26.3 도 같은 글을 못 읽음) | |
| D-18 | Minor | CheckRunner.java:21-22 · MyBatisRules.java:42 | `(?m)^\s*…` 셋 — 빈 줄 반복에 제곱 시간(4만 줄 29초). 고침: `^[ \t]*` | |
| D-19 | Minor | RegexRule.java:17·163-171 | `SCRIPT_BODY` — 닫는 태그 없는 `<script>` 반복에 제곱 시간 | |
- 닫는 행: D-01·D-10·D-17·D-19 → `5-128` · D-15 → `5-129` · D-02·D-12·D-13·D-14 → `5-130` · D-03·D-04 → `5-131` · D-05 → `5-132` · D-06·D-07·D-08 → `5-133` · D-09 → `5-134` · D-11·D-18 → `5-135`(D-18 의 `MyBatisRules.PACKAGE` 는 `5-133`) · D-16 → `5-136`. 「재확인」 — 열아홉 다 인용 줄을 코드로 다시 읽어 확인
- 점검자가 뺀 것(한계·겹침): 5-127 정규식 리터럴 · 5-121 결정 13 · 5-122 Oracle 인라인 뷰 UPDATE · 6-57 `<sqlMap>` · 5-119 · 5-125 ConstLink · 5-114 결정 15 · 5-105 · 5-124 모르는 id · 5-100 · 5-31

## A1 DDL(점검자 22 — 실행 탐침, DB 는 안 띄움)
| 키 | 등급 | 자리 | 무엇 · 재현 요지 · 고침 방향 | 재확인 |
|---|---|---|---|---|
| A1-01 | Important | DdlGen.java:537(inKey)·350·413 | `keyLen` 이 PK·유니크·인덱스만 — FK 자식 컬럼(PG text)은 CLOB, 부모 키는 VARCHAR2(255) → FK 문장 실패. 고침: inKey 에 FK 컬럼, 길이는 참조 컬럼 | |
| A1-02 | Important | AlterGen.java:374·358·82-84 | MariaDB — 기본값은 안 바뀌고 형식·NULL·코멘트만 바뀌면 못 옮기는 식 기본값(`uuid()`·`current_timestamp(6)`)이 MODIFY COLUMN 에서 빠진 채 살아 있는 문장, `[손]` 없음(warnings 만). 고침: redefines 참이면 기본값이 안 바뀌어도 lostDefault 를 재서 `[손]` | |
| A1-03 | Important | AlterGen.java:640·631·601 | 바뀐 표에 더하는 이름 없는 FK 대체 이름이 전부 `FK_<표>_0`(`t.fks().indexOf(fk)` — t 는 A 쪽, fk 는 B 쪽 → -1). 고침: B 표의 fks() 순번 | |
| A1-04 | Important | DdlGen.java:303·315·427 | 스키마 단위 이름 겹침 처리(ixName)가 인덱스만 — 유니크 제약 이름은 그대로(MariaDB 표 단위 이름 `code` 가 표마다) → 둘째부터 실패 | |
| A1-05 | Important | DdlAlter.java:125·113·22 | `ADD COLUMN IF NOT EXISTS` 의 IF NOT EXISTS 를 떼기만 → 이미 있는 컬럼을 한 번 더(DTO 에 `c_`) | |
| A1-06 | Important | DdlAlter.java:41(DEFAULT_FOR)·36(DROP_TAIL) | 새 정규식 둘 제곱 시간(10만 공백 24초) — 1-96 처방과 어긋남 | |
| A1-07 | Important | DdlReader.java:320·545·62 · DdlAlter.HEAD:19 · COMMENT_ON:82 · alterPk:483 | 공백 든 따옴표 이름(`[Order Details]`·`[Order ID]`) — 컬럼·표 이름은 받는데 키 목록·ALTER 머리·COMMENT ON 은 공백에서 자름 → PK·FK 가 없는 컬럼 가리킴 · ALTER 조용히 사라짐 | |
| A1-08 | Important | DdlGen.java:453(defaultOf) | 다른 방언 → mariadb 문자열 기본값의 `\` 를 안 겹침(`'C:\temp\new'`) | |
| A1-09 | Minor | DdlReader.java:654 | CRLF 에서 `GO` 줄을 경계로 못 봄(`[ \t\r]*+$` — 1-96 회귀). 화면은 LF 뿐 | |
| A1-10 | Minor | DdlPull.java:140(keyShape) | MSSQL `INDEX ix NONCLUSTERED (a)`·`KEY IF NOT EXISTS ix (b)` 가 컬럼으로(가짜 컬럼 INDEX) — 1-92·1-104 회귀 | |
| A1-11 | Minor | DdlReader.java:366·373 | SSMS 대괄호 타입 `[int]`·`[nvarchar](40)` 의 대괄호를 안 벗김 → DTO 전부 Object | |
| A1-12 | Minor | DdlReader.java:123(CTAS_ANY)·106(TABLE_COMMENT)·370 · DdlPull.java:63(EXPR_ALIAS)·53(MERGE_JOIN) | 옛 정규식 넷 제곱 시간 · MERGE_JOIN 은 2000 낱말에 StackOverflowError(500) | |
| A1-13 | Minor | AlterGen.java:538 ↔ DdlGen.tbl:608 | 대소문자 섞인 PG 스키마 — `DROP INDEX "Sales".ix` 대 `ALTER TABLE Sales.t` | |
| A1-14 | Minor | DdlGen.java:570(autoTail) · AlterGen.addColumn:340 | MariaDB — 자동증가 컬럼을 새로 더할 때 키 없이 `ADD COLUMN … AUTO_INCREMENT` 먼저 | |
| A1-15 | Minor | DdlAlter.java:104·100 | `, ALGORITHM=INPLACE, LOCK=NONE` 조각이 컬럼으로(`unreadableN`) | |
| A1-16 | Minor | DdlPull.java:87(find) | `스키마.이름` 을 못 찾으면 스키마가 다른 맨 이름 표로 물러섬(s2.emp 의 ALTER 가 s1.emp 에) | |
| A1-17 | Minor | DdlGen.java:380 | MSSQL `numeric() identity` — 빈 괄호 그대로 `numeric() IDENTITY(1,1)`(드라이버가 그 글을 주는지는 미확인) | |
| A1-18 | Minor | DdlGen.java:441 ↔ lostDefault:710 | `NULL::타입` 기본값 — lostDefault 는 없음, defaultOf 는 「못 옮긴 식」 헛경고 | |
| A1-19 | Minor | DdlGen.java:354 · ddl-types.yaml:10 | 고정 길이 CHAR 상한(mariadb 255 · oracle 2000)을 안 봄 → `CHAR(500)` | |
| A1-20 | Minor | DdlReader.java:362 | 인라인 CHECK 괄호 안 `IS NOT NULL` 로 컬럼을 NOT NULL 로 읽음 | |
| A1-21 | Minor | DdlGen.java:556(autoHead) | 자동증가 절을 붙일 때 옮긴 타입이 정수인지 안 봄(`NUMERIC GENERATED … IDENTITY`·`DECIMAL(38,10) IDENTITY`) | |
| A1-22 | Minor | DdlReader.java:95(INLINE_INDEX)·447 | 이름 없는 `KEY (a)`·`KEY ix USING BTREE (b)` — CREATE 몸은 조용히 버리고 ALTER 는 못 읽은 줄 | |
- 닫는 행: A1-02 → `1-116` · A1-03·A1-13·A1-14 → `1-118` · A1-01·A1-04 → `1-119` · A1-08·A1-18 → `1-120` · A1-17·A1-19·A1-21 → `1-121` · A1-05·A1-06·A1-15 → `1-122` · A1-07·A1-11 → `1-123` · A1-09·A1-10·A1-16·A1-20·A1-22 → `1-124` · A1-12 → `1-125`. 「재확인」 — 스물둘 다 인용 줄을 코드로 다시 읽어 확인
- 뺀 것: 설계 71 §3-1·3-2·3-3·3-5·3-8·3-9·3-10 · 72 §3-1·3-3 · 73 잠재 둘 · 1-116·1-117 겹침 · 후보 ⑥

## B 산출물·보고(점검자 16 — 실행 탐침(메모리 XSSF·H2) · 전부 코드 대조 확인. 절대 규칙 2·3·6·7 위반 길 없음)
| 키 | 등급 | 자리 | 무엇 | 행 |
|---|---|---|---|---|
| B-01 | Important | report/FormulaRange.java:47 · XlsxFiller.java:98 | 다시 계산 표시가 「넓힌 수식이 있을 때」 만 — 안 넓힌 수식·한 행 `SUM(B3:B3)` 이 낡은 캐시 0 | 2-52 |
| B-02 | Important | FormulaRange.java:34 | 데이터 행 위 수식(`COUNTA(A3:A3)`)은 안 훑어 첫 행 범위 그대로 | 2-52 |
| B-03 | Important | Relations.java:166 → XlsxWriter.java:88 | 같은 이름 표 알림이 칸 상한 32,767자를 넘으면 작성안내 쓰기 예외 — 작업 FAILED | 2-53 |
| B-04 | Minor | FormulaRange.java:68-73 | 단일 칸 배열 수식이 일반 수식으로 바뀌어 `#VALUE!` | 2-52 |
| B-05 | Minor | CodeAndLink.java:140 | 코드 컬럼 빈칸 + 정렬 컬럼 → IndexOutOfBounds(500) | 2-54 |
| B-06 | Minor | Masking.java:230 | 표 이름 빈 행에 `UPDATE "" SET`(COMMENT DDL 은 400) | 3-25 |
| B-07 | Minor | Masking.java:216 · ColumnInputs.java:82 | 뷰 컬럼도 마스킹 후보 — `UPDATE <뷰>` | 3-25 |
| B-08 | Minor | Definitions.java:359 | 03 제약조건 낱말 맞춤이 `::text`·한글 이름에서 남의 CHECK 를 붙임 | 2-55 |
| B-09 | Minor | CodeAndLink.java:98-125 | 08 SELECT 만 예약어 이름을 안 감쌈 | 2-54 |
| B-10 | Minor | Masking.java:68 · QualitySql.java:36 | `#` 든 이름이 MariaDB·PG 에서 안 감싸임 | 3-25 |
| B-11 | Minor | Masking.java:324 → Dialect.java:47 | 한글 이름을 받은 글자 그대로 감싸 Oracle 에서 다른 객체 | 3-25 |
| B-12 | Minor | DeliverableService.java:303-313 | precheck 가 매핑 유무만 — 양식 없음·시트·머리·`file` null 은 쓰는 중에 | 2-56 |
| B-13 | Minor | DeliverableService.java:129-132 | 두 문서가 같은 `file` 이면 뒤가 앞을 덮음 | 2-56 |
| B-14 | Minor | XlsxFiller.java:139 | 32,767자 넘는 CHECK·코멘트 칸에서 예외(V017 은 100만 자) | 2-53 |
| B-15 | Minor | LogicalRoutes.java:227-237 | 후보에 안 맞는 제외 키를 조용히 버림 | 3-25 |
| B-16 | Minor | XlsxFiller.java:68 | 표 1만 규모의 03 이 XSSF 로 힙 초과 — 사유 OutOfMemoryError 뿐 | 2-56 |
- 뺀 것: build 프로필 없음 500(설계 72 범위 밖) · 설계 71 §3-14 · 72 §3-10·3-11 · 73 3-22·2-49 결정 · compare ㉜ 대소문자 · 2-17

## G 웹·파일·프로필·CLI·로그 SQL(점검자 18 — 순수 함수 실행 + JDK·Jetty 바이트코드 추적 · 전부 코드 대조 확인)
| 키 | 등급 | 자리 | 무엇 | 행 |
|---|---|---|---|---|
| G-01 | Important | fs/LocalFiles.java:171-185·416-433 | `subst` 드라이브 별칭이 금지 폴더(프로필)·「결과 폴더는 원본 밖」 판정을 비켜 간다(`toRealPath` 가 subst 를 안 푼다 — 추적만) | 0-121 |
| G-02 | Important | web/ProfileRoutes.java:33·141-155 | 접속 비밀번호가 아닌 `password:` 글자(`common.password` 규칙 덮어쓰기)를 백업 가림이 지워 코드 검사 저장이 늘 400 | 0-122 |
| G-03 | Important | profile/ProfileStore.java:182-190 | 체크박스만 바꿔도 손으로 적은 `{severity}`·`{globs}`·`{message}` 덮어쓰기가 사라진다 | 0-123 |
| G-04 | Important | ProfileStore.java:59-97 | `password: !Abc`·`&abc`·`#abc` 가 빈 글로 읽히는데 거절이 없다 — 빈 비밀번호로 접속 시도 | 0-123 |
| G-05 | Minor | ProfileRoutes.java:287 | 태그 붙은 블록 스칼라 password 의 `#` 본문 줄이 백업에 남는다(규칙 2 — 인위적) | 0-122 |
| G-06 | Minor | text/LogSql.java:82·105 | 짝 없는 Preparing·서로 다른 열쇠에 제곱 시간(88초) | 4-50 |
| G-07 | Minor | LogSql.java:100-112 | 한 Preparing 뒤 Parameters 여러 줄(BATCH)은 첫 줄만 — 경고 없음 | 4-50 |
| G-08 | Minor | LogSql.java:42 | `(byte[])`·`(String[])` 타입 글을 못 읽는다 | 4-50 |
| G-09 | Minor | web/LocalOnly.java:76-79 | `Content-Length` 가 2GB 를 넘으면 413·415 판정을 건너뛴다 | 0-124 |
| G-10 | Minor → **사용자가 올림**(막는다) | LocalFiles.java:155-158 | 드라이브 글자에 매핑된 네트워크 드라이브는 UNC 판정을 지난다(규칙 1) | 0-125a · 0-125b |
| G-11 | Minor | cli/Serve.java:48 · Batch.java:108 | `--data-dir`·`--profiles-dir` 의 UNC 를 안 거절 | 8-23 |
| G-12 | Minor | ProfileStore.java:340-366 | 저장이 바꿔 끼우기라 파일에 직접 건 권한이 풀린다 — 권고 글이 「파일 권한」 | 8-24 |
| G-13 | Minor | LocalOnly.java:43-59 | `--port 80` 이면 모든 요청이 403 | 0-124 |
| G-14 | Minor | cli/DocCommands.java:97-107 | `--docs 08 --code-conn` 은 아무것도 안 만들고 끝 코드 0 | 8-24 |
| G-15 | Minor | Batch.java:121·282 | `--snapshot latest` 가 파일 이름으로 찾는다(스냅샷은 `name:` 으로 저장) | 8-24 |
| G-16 | Minor | cli/SelfTest.java:115 | 활성 프로필이 깨지면 selftest 가 ⑥ 에서 끊긴다 | 8-24 |
| G-17 | Minor | web/App.java:84-103 · Serve.java:68-78 | 기동 실패 일부가 스택으로 끝난다(포트 65536 · 포트 열 개 참 · 배치 data 폴더) | 8-23 |
| G-18 | Minor | LocalFiles.java:447·476-480 · FsRoutes.java:48 | 결과 쓰기 `IOException` 이 500 「서버 오류」 | 0-121 |
- 뺀 것: 설계 73 4-37·0-107·0-112·0-114 결정 · 설계 69 §3-8 · 0-85a · 설계 72 범위 밖(저장 실패 500) · 4-40 한계 · 0-84 사람 실측

## F DB 경계(점검자 10 — H2 메모리 실행 · 전부 코드 대조 확인. 다른 DB 의 반응은 문법 지식 — 추적만)
| 키 | 등급 | 자리 | 무엇 | 행 |
|---|---|---|---|---|
| F-01 | Critical | conn/ReadOnlyJdbc.java:114-116 | `--` 줄 주석을 `\n` 에서만 닫아 `--\rCOMMENT ON …` 이 SELECT 로 읽히고 실행된다(H2 실행 확인 — 규칙 6) | 9-10 |
| F-02 | Critical | ReadOnlyJdbc.java:154-162 | `1e5INTO`·`0x1UPDATE`·`1e0FOR` — 지수·16진 꼬리에 붙은 금지 낱말이 가려진다 | 9-10 |
| F-03 | Critical | ReadOnlyJdbc.java:37-43 | `ADD SENSITIVITY CLASSIFICATION`·`ADD SIGNATURE`·`BEGIN DIALOG` 에 금지 낱말이 없다(SQL Server 잇기) | 9-10 |
| F-04 | Critical | meta/JdbcMetaSource.java:108 | PG 파티션 부모 표(`PARTITIONED TABLE`)가 스냅샷에서 빠지고 자식만 실린다 | 1-126 |
| F-05 | Important | meta/SnapshotStore.java:114 · V001:48 | 201자 넘는 제품 버전 글이면 스냅샷 저장이 통째 실패 | 1-127 |
| F-06 | Important | meta/SnapshotDiff.java:165 ↔ gen/AlterGen.java:777 | FK 기본 규칙 접기가 비교에만 — 「차이 없음」 인데 반영 DDL 은 FK DROP·ADD | 1-128 |
| F-07 | Minor | SnapshotDiff.java:92·199 ↔ AlterGen.java:718 | 컬럼·제약 이름 대소문자 짝짓기가 둘이 다르다 | 1-128 |
| F-08 | Minor | ReadOnlyJdbc.java:123-127 | `SELECT_X 1` 을 SELECT 로 받는다 | 9-10 |
| F-09 | Minor | dict/DictStore.java:232-249 · DictRoutes.java:84 | 공통표준단어 올리기는 긴 칸에 500 「서버 오류」 | 3-26 |
| F-10 | Minor | DictStore.java:316-318 | 기관표준단어의 「n번째 줄」 이 빈 줄을 뺀 번호 | 3-26 |
- 뺀 것: `SET`·`USE`(설계 69 §3-6 [고정]) · 엄격한 쪽 오거절 · 부작용 함수 ⑪ · 설계 71 §3-1·§3-16 · 72 §3-12 · 통계 기준 0

## C2 Java 분석(점검자 15 — 실행 탐침 · 전부 코드 대조 확인, C2-01 은 내가 git 탐침으로 재현)
| 키 | 등급 | 자리 | 무엇 | 행 |
|---|---|---|---|---|
| C2-01 | Critical | vcs/WorkingCopy.java:370·201 · Cli.java:86-88 | 부분 클론(promisor 원격) 저장소에서 git 이 스스로 fetch — 원격 접속·`uploadpack` 명령 실행(규칙 1). `GIT_NO_LAZY_FETCH=1` 로 막힌다 | 0-120 |
| C2-02 | Important | WorkingCopy.java:171-175 | 필터 이름에 `"` — Windows 에서 `-c` 인자가 갈려 필터가 안 꺼지고 전역 옵션이 끼어든다 | 0-120 |
| C2-03 | Important | JspLinks.java:126 | `<script>` 를 `<!-- … //-->` 로 감싼 꼴의 링크가 통째로 빠짐(egov 181 파일 · 287 건 13.5%) | 6-85 |
| C2-04 | Important | JavaGraph.java:520·581-588 | 공통 DAO 가 문장 id 를 매개변수로 받아 넘기면 하위 DAO 문장이 전부 빠짐 | 6-86 |
| C2-05 | Minor | TypeBind.java:76-87 · JavaGraph.java:235-240 | 매핑 주석이 인터페이스·추상 조상 메서드에 있으면 프로그램 0 | 6-88 |
| C2-06 | Minor | JpaIndex.java:513 · JpqlPaths.java:49-53 | JPQL JOIN 뒤 쉼표의 엔티티가 빠짐 | 6-89 |
| C2-07 | Minor | JspLinks.java:35·167 | 한 줄의 따옴표 짝이 밀려 `.do` 아닌 링크를 놓침 | 6-87 |
| C2-08 | Minor | JspLinks.java:33·244-258 | `c:set` 값이 `${…contextPath}/admin` 이어도 컨텍스트 이름으로 — 접두 빠진 링크 | 6-85 |
| C2-09 | Minor | JavaGraph.java:633·490 | 조상이 선언한 필드 타입을 하위 클래스 기준으로 풀어 다른 패키지 같은 이름 | 6-90 |
| C2-10 | Minor | JavaGraph.java:399 | 후보 하나면 import 를 안 봐 프로젝트 밖 클래스가 이름만 같은 프로젝트 클래스로 | 6-90 |
| C2-11 | Minor | JavaGraph.java:539·778·859 | 곁 등록부 id 에 인자 수가 없어 오버로드의 표가 합쳐짐 | 6-91 |
| C2-12 | Minor | JspLinks.java:33 | `CTX_SET` 세제곱 시간(12,000자 6초) | 6-85 |
| C2-13 | Minor | JspLinks.java:141-154 | 경로 글자만 이어진 긴 줄 — URL 글자 합 제곱 | 6-87 |
| C2-14 | Minor | JavaViews.java:37-38 | 완전 이름 `ModelAndView` 리턴이 json 으로 | 6-92 |
| C2-15 | Minor | JavaViews.java:83 | `"redirect:" + 변수` 가 이름 빈 뷰로 | 6-92 |
- 뺀 것: 6-77 겹침 · 6-54·6-51·6-49 한계 · 6-67·6-74 한계 · 5-127 · 0-84 ①

## E 코드 정리(점검자 17 — 15 실행 · 2 추적, 전부 코드 대조 확인)
| 키 | 등급 | 자리 | 무엇 | 행 |
|---|---|---|---|---|
| E-01 | Critical | Cleanup.java:537·555·568·590 | jsp·html 의 `<script>` 주석을 고침이 안 떼 꼬리 주석이 제어문 가드를 가림 — `debugger;`·console 줄을 지워 다음 문장이 if·for 의 몸이 된다 | 5-137 |
| E-02 | Critical | ConsoleArgs.java:109-114 | TS `f<T>(…)`·`cb!(…)` 를 호출로 안 세 부수 효과째 console 문장 삭제 | 5-138 |
| E-03 | Important | ValueFix.java:72 | hit 를 지금 지적으로 안 걸러 `tb-ignore` 줄도 고침 → 파일 통째 되돌림 또는 조용히 뒤집힘 | 5-139 |
| E-04 | Important | LogFix.java:275-276 | 로거 앞 `Helper.create()`(같은 파일 형식 수신자) 안 출력을 `log.debug` 로 — 로딩에서 NPE | 5-140 |
| E-05 | Important | LogFix.java:281 | 인스턴스 로거 앞 필드 초기화식이 부르는 메서드 안 출력을 바꿈 — NPE | 5-140 |
| E-06 | Important | MarkupFix.java:187 | tsx — 작은따옴표 target 에 큰따옴표 rel 을 끼워 문자열이 깨짐 | 5-141 |
| E-07 | Important | Cleanup.java:590-598 | jsp·html — 주석 줄·꼬리 주석 뒤 console 줄을 못 지우고 사유가 틀림 | 5-137 |
| E-08 | Minor | SwitchFix.java:126-142 | 뒤 case 의 지역 이름이 옮겨진 default 의 필드 대입을 가져감 | 5-142 |
| E-09 | Minor | ValueFix.java:183·163 · DeclaredTypes.java:46-88 | 패턴 변수를 같은 이름 필드 형식으로 읽고 뒤집음 | 5-139 |
| E-10 | Minor | ValueFix.java:187 · ValueAst.java:250 | 가려진 상수 이름을 리터럴처럼 앞세움 | 5-139 |
| E-11 | Minor | PiiFix.java:28·39·64 | sql·jsp·html 은 주석 속 `'` 가 따옴표를 열어 맨 숫자를 가림 | 5-143 |
| E-12 | Minor | PiiFix.java:25·37·67 | 매퍼 XML 안 SQL 의 맨 숫자를 가림 | 5-143 |
| E-13 | Minor | Cleanup.java:548·585 · Zones.java:39 | `<div>` 안 예제 글·JSX `<pre>` 글의 debugger·console 줄 삭제 | 5-137(마크업) · JSX 는 한계 |
| E-14 | Minor | SwitchFix.java:117-121 | 꼬리 주석 때문에 「줄을 나눠 쓴다」 로 건너뜀 | 5-142 |
| E-15 | Minor | CleanupRunner.java:74-81·137 | 안 써진 파일에 「전부 통일했다」 알림 | 5-144 |
| E-16 | Minor | EncodingFix.java:25·98 | `<script charset=…>`·`accept-charset` 을 파일 선언으로 읽어 거절 | 5-145 |
| E-17 | Minor | CleanupRunner.java:219 · LocalFiles.java:362 | `\r\r\n` 파일의 안 고친 줄에서 `\r` 이 사라짐 | 5-144 |
- 뺀 것: 설계 70 §3-3·3-4·3-5·3-7·3-11 [고정] · 5-115 ② 한계 · 5-116 ⑤ · 4-40 한계 · 설계 73 결정 15

## A2 생성기 나머지(점검자 16 — 실행 탐침 · 전부 코드 대조 확인)
| 키 | 등급 | 자리 | 무엇 | 행 |
|---|---|---|---|---|
| A2-01 | Important | GenModel.java:96 | 파생 module 이 자바 예약어(`TB_CASE` → `package ….case.service`) — 컴파일 실패, 경고 없음 | 7-37 |
| A2-02 | Important | InsertGen.java:583 | 스냅샷의 대소문자 섞인 이름 — DDL 은 `"Artist"`, INSERT 는 `Artist` | 4-46 |
| A2-03 | Important | InsertGen.java:41·166 · InsertGenParts:57 | 허용 값 판정 둘 — pg_dump `ANY(ARRAY)`·겹괄호·OR 등식을 INSERT 쪽이 못 읽어 CHECK 위반 값 | 4-47 |
| A2-04 | Important | InsertGen.java:646 | CSV 칸 수가 머리와 달라도 경고 없이 밀림·사라짐 | 4-48 |
| A2-05 | Minor | GenModel.java:129 | VO 고정 필드(pageIndex 등)와 같은 이름 컬럼 — 필드 둘씩, 컴파일 실패 | 7-37 |
| A2-06 | Minor | Validation.java:286·64 | `normalize` 가 리터럴 안 `::` 도 캐스트로 지움(`'std::string'` → `std`) | 4-47 |
| A2-07 | Minor | InsertGen.java:277·386 | 좁은 수 PK(`NUMBER(2)`) 값 넘침 경고 없음 | 4-48 |
| A2-08 | Minor | InsertGen.java:463·452 | FK 로 채운 키 컬럼의 값수를 keyOverlap 이 모름 | 4-48 |
| A2-09 | Minor | InsertGen.java:322 | Oracle `INTERVAL '100' DAY` — 앞자리 정밀도 2 초과 | 4-48 |
| A2-10 | Minor | InsertGen.java:257 ↔ 675 | PG BIT — 생성 모드 `1`, CSV 모드 `B'1'` · BIT VARYING `'VAL_1'` | 4-48 |
| A2-11 | Minor | InsertGen.java:53·276·680 | NUMISH `.*INT.*` 가 POINT 에 · csvLiteral 에 INTERVAL 제외 없음 | 4-48 |
| A2-12 | Minor | DtoGenerator.java:237·295 | `_ID` → 필드 `Id`(JSP EL 오류 · getter 겹침) | 7-37 |
| A2-13 | Minor | InsertRoutes.java:184 | DDL 에 표가 여럿인데 첫 표만 — 경고 없음 | 4-46 |
| A2-14 | Minor | table_builder_ext.js:223 | 셀 글의 `>` 를 엔터티로 안 바꿈(TS1382) | 4-49 |
| A2-15 | Minor | table_builder_ext.js:223 | 안 닫힌 `<a ` 반복에 제곱 시간 | 4-49 |
| A2-16 | Minor | table_builder_ext.js:146 | CSS 문자열 안 `\"` 탈출을 모름 | 4-49 |
- 뺀 것: 매퍼 표 이름 인용·form:option 값(설계 72 범위 밖) · DTO save 덮어쓰기 · NO_BACKSLASH_ESCAPES · 스냅샷 모드 CHECK IN(설계 71 §3-12) · DDL 모드 대소문자 · A-33·A-37

## C1 SQL 분석(점검자 19 — 실행 탐침, H2)
| 키 | 등급 | 자리 | 무엇 · 재현 요지 · 고침 방향 | 재확인 |
|---|---|---|---|---|
| C1-01 | Critical | SqlProbe.java:375-391(unquoted)·343·371·352 | `unquoted` 가 SQL 주석·`\'` 를 몰라 주석 안 `'` 하나로 리터럴 안팎이 뒤집힘 → 리터럴 값이 「자기 낱말」 이 돼 `fileMessage`(DB 대조 xlsx 「DB 메시지」)에 남음(규칙 3 — 6-39·6-41·6-43 이 막으려던 길). 고침: 낱말 모으기 전에 주석 지움 + 따옴표 훑기를 SqlTokens 와 한 자리 | |
| C1-02 | Important | SqlJoins.java:179-197·277·280 | `<when>`·`<otherwise>` 분기가 같은 별칭을 다른 표에 쓰면 뒤 분기가 덮어 앞 분기 등식이 뒤 분기 표로(T_B.ID—T_X.BID 지어냄, T_A—T_B 빠짐). 설계 75 §3-2 [고정] 과 맞닿음 | |
| C1-03 | Minor | SqlJoins.java:226-241 | `FOR UPDATE OF a`·`UPDATE IGNORE T1`·`LOW_PRIORITY` — UPDATE 뒤 낱말을 무조건 표로 → `OF.ID—T2.ID` | |
| C1-04 | Minor | SqlJoins.java:287-290(word) | 따옴표 친 예약어 표 이름(`"ORDER"` 낱말 — 6-58)을 SqlJoins.word 가 낱말로 안 봐 그 표 관계 통째로 빠짐 | |
| C1-05 | Minor | SqlTables.java:452-454·347-353 | 표 이름 뒤 분기 표시가 표 자리를 다시 열어 `INSERT INTO T1 <when>(COL_A, COL_B)` 의 컬럼이 R 표로 | |
| C1-06 | Minor | SqlTables.java:222 · RegexRule.java:181-200 | 주석 지우기(cLike)가 `\'`·`#` 줄 주석을 몰라 SqlTokens 와 어긋남 → 뒤쪽 표 빠짐 | |
| C1-07 | Minor | MapperIndex.java:165-168·282-285 | `<sql>` 조각 열쇠에 databaseId 없음 — 같은 파일 방언별 조각이 마지막 것으로 덮임 | |
| C1-08 | Minor | SqlProbe.java:215·232-238·196 | `tail` 이 주석을 안 가려 주석 처리된 `ORDER BY` 에 `OFFSET 0 ROWS`(mssql) · `LIMIT 10 -- (paging)` 뒤 `LIMIT 0` 둘(mysql) | |
| C1-09 | Minor | SqlJoins.java:120(outsideSet) | `ON DUPLICATE KEY UPDATE T1.CNT = b.CNT` 대입이 조인으로 | |
| C1-10 | Minor | SqlTables.java:204-212(withoutDcl) | `GO` 뒤 둘째 GRANT·REVOKE 를 안 뺌 → 사용자 이름이 R 표로 | |
| C1-11 | Minor | SqlTables.java:324(listAfter)·78-81 | 조인 조건 안 열 이름(`A.MODEL`)·`CASE WHEN` 이 표 목록을 닫아 뒤 쉼표 조인 표 빠짐 | |
| C1-12 | Minor | SqlTables.java:571-572 | `EXECUTE IMMEDIATE q USING V1, V2` 의 바인드 변수가 R 표로 | |
| C1-13 | Minor | MapperIndex.java:442·450 | iBATIS `<selectKey>` 글은 sqlText 를 안 지나 `$tableName$` 이 표 이름으로 | |
| C1-14 | Minor | MapperIndex.java:392-396 | `<selectKey>` 안 `<include>` 는 표시를 안 남겨 조각의 표가 빠짐 | |
| C1-15 | Minor | SqlProbe.java:79·137 | `run` 이 못 읽은 매퍼 파일(Parsed.unresolved)을 버려 「전부 통과」 | |
| C1-16 | Minor | SqlProbe.java:83 | 문장 열쇠 `ns + "." + id` — MapperIndex.key(namespace 없으면 id 만)와 어긋남(`.nons`) | |
| C1-17 | Minor | SqlProbe.java:142-144(pick) | 변형이 하나면 databaseId 를 안 보고 고름(oracle 문장을 mysql 에 대조) | |
| C1-18 | Minor | SqlProbe.java:56(HASH) | `#\{[^}]*\}` — 안 닫힌 `#{` 10만 번에 18초 | |
| C1-19 | Minor | web/AnalyzeRoutes.java:415 | 대조 라우트가 `<mapper` 든 파일만 — iBATIS sqlMap 은 「0 문장」(6-57 어긋남) | |
- 닫는 행: C1-01 → `6-82` · C1-02 → `6-78` · C1-03·C1-04·C1-09 → `6-79` · C1-05·C1-10·C1-11·C1-12 → `6-80` · C1-06 → `6-81` · C1-07·C1-13·C1-14 → `6-84` · C1-08·C1-16·C1-17·C1-18 → `6-83a` · C1-15·C1-19 → `6-83b`. 「재확인」 — 열아홉 다 확인(C1-02~06·09~12 는 탐침을 다시 돌려 같은 출력)
- 뺀 것: SqlJoins 쉼표 한계(`JOIN … ON …, T3 c`·`WITH (NOLOCK), T2`) · 파생 표 관계 · GO · `FROM <when> 표` · `<trim suffix=")">` (6-75) · hashText(6-76) · 6-47 [고정] · 부작용 함수 ⑪

## 4. 행으로 안 세운 것 · 사람 결정

- **사람 결정(G-10)** — 드라이브 글자에 매핑된 네트워크 드라이브(`Z:` = `\\서버\공유`)의 파일을 여는 것. 사용자 2026-11-22: 「로컬에 있는 파일이 아닌 것까지 읽을 수 있다면 못 하게 해야 한다」 → 막는다. 공개 API 에는 드라이브 종류가 없어 JDK 안쪽의 `GetDriveType` 을 리플렉션으로 부르고(jar 매니페스트 `Add-Opens` — 새 의존성 없음), 못 물으면 거절한다(설계 77 `0-125a`·`0-125b`). 링크를 푼 실제 경로가 네트워크면 그것도 거절
- 한계로 남긴 조각(행의 「한계」 줄): D-11 의 「메서드만 붙인 자바」(TS 클래스 멤버와 못 가른다) · E-13 의 JSX 글 줄 · E-15 의 딸린 것(다수 쪽이 프로필과 같은 섞인 파일은 `file.lineEnding` 지적이 없다 — `Source` 에 섞임 표시가 없다) · A1-02 의 뿌리(같은 방언에서도 식 기본값을 안 옮긴다) · C2-04 의 감싸개 자체 미해결 한 줄
- 후보(점검자가 확신이 낮아 버린 것 가운데 다음에 볼 만한 것): `BlankRelRule.lineOf` 제곱 시간 · `rel` 이 `NUL`·`COM1` 인 결과 쓰기 · `Job.subscribe` 가 락을 쥔 채 SSE 를 쓰는 것 · `LogicalRun` 의 `removeSpaces` 가 `E Mail` 과 `EMAIL` 을 합치는 것 · DEFAULT 식이 첫 토막만 남는 것(`SYSDATE + 1`) · `RowCounter` 가 `SQLFeatureNotSupportedException` 을 REFUSED 로 세는 것
