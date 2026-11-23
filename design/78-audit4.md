# 설계 78 — 가로 점검 4차 결과(2026-11-24)

## 1. 기준 · 방법 · 한계

- **범위** — 번들 91 이 바꾼 파일: `git diff --name-only 57760d1 fccf261`(`57760d1` = 번들 90 #135 머지, `fccf261` = 번들 91 #136 머지) 가운데 `src/main`·화면 79개(+2,251줄). 줄 번호는 `fccf261` 기준. 바뀐 덩이와 그 맞닿는 자리가 중심이고, 같은 파일의 안 바뀐 자리는 눈에 띈 것만(Minor)
- **기준** — 설계 67 의 4~7절 그대로: 절대 규칙 1·2·3·6·7 · 로컬 공격면 · 입력 극단값 · 조용히 틀린 출력 · 오류 경로 · 자원 · 판정 중복 · 번들 사이 어긋남. 등급은 사용자가 겪는 것으로(Critical — 절대 규칙 위반·조용히 틀린 채 나감 / Important — 기능이 실패하고 보임·표본에서 되풀이될 꼴 / Minor — 드문 입력·바로 알아챔)
- **방법** — 영역 여섯에 점검자 하나씩(Explore 서브에이전트, Fable xhigh). `git diff` 덩이마다 지금 메서드 전체와 부르는 쪽을 읽고 설계 77 의 [고정]·「한계」 줄과 댔다. 재현은 파일을 쓰지 않는 탐침만 — jshell 표준입력 + `target/app.jar`(HEAD 와 같은 빌드) · H2 메모리 · 메모리 XSSF · 읽기 전용 git. 저장소·`target/` 을 안 건드렸고 Docker·네트워크·`--db`·`--corpus` 를 안 썼다. 탐침은 jshell 67회·꼴 약 640개(표본 폴더 읽기 전수 비교 넷 포함)
- **판정** — 설계 세션이 지적의 인용 줄을 코드로 다시 읽었다(오탐 0 — 점검자가 설계 한계·대기 행과 겹치는 것을 미리 뺐다). 설계 세션의 전제 하나를 점검자 A 가 바로잡았다: `DdlReader` 결과가 `DdlGen`·`AlterGen` 으로 가는 화면 길은 없다(잠재 A-33·A-37 은 잠재 유지)
- **3차와 다른 것** — 대상이 번들 하나의 diff 다(3차는 번들 일곱). 점검 도중 토큰 한도로 점검자 셋(A·E·BFG)을 한 번 멈췄다 이어 돌렸다(같은 문맥)
- **한계** — 점검 끝이 결함 없음은 아니다. 실물 DB 의 반응 · 네트워크(git 의 UNC include·SMB) · 파일을 쓰는 길(`CleanupRunner.apply`·결과 폴더 권한)은 못 돌렸다 — 그 지적은 「추적만」 이고 번들 92 의 시험이 RED 로 다시 잰다(`0-128` 은 설정 글 판정만 잰다 — 실측은 사람 목록)

## 2. 요약

| 영역 | 대상 | 지적 | Critical | Important | Minor | 후보 |
|---|---|---|---|---|---|---|
| D 코드 검사 판정 | `core/check` 의 RegexRule·Zones·RuleSet·CheckRunner·MyBatisRules·DeclaredTypes | 10 | 0 | 2 | 8 | 0 |
| C1 분석 — SQL | SqlJoins·SqlTables·SqlProbe·MapperIndex·SqlTokens·LogSql | 15 | 1 | 1 | 13 | 6 |
| C2 분석 — Java·VCS | StmtArgs·TypeBind·JavaGraph·JpaIndex·JspLinks·JavaViews·`vcs` | 14 | 0 | 4 | 10 | 4 |
| E 코드 정리 | `core/check` 고침 쪽 | 8 | 1 | 2 | 5 | 3 |
| A DDL·생성기 | `core/gen` · SnapshotDiff | 18 | 0 | 5 | 13 | 3 |
| BFG 산출물·DB 경계·웹 | ReadOnlyJdbc·`fs`·`profile`·`cli`·`deliverable`·`report` | 13 | 0 | 3 | 10 | 2 |
| 합 | | **78** | **2** | **17** | **59** | **18** |

- Critical 둘: C1-01(DB 대조 xlsx 오류문에 SQL 리터럴 값 — 규칙 3, 번들 91 `6-82` 가 세 꼴만 닫았다) · E4-01(`if (…)` 뒤 `debugger;` 삭제로 다음 문장이 제어문 몸 — `5-128` 의 줄 자르기가 가드를 무력하게 했다)
- 번들 91 이 새로 만든 것(회귀): D4-01(줄 수) · D4-03 · D4-04·05 · D4-06 · D4-07 · C1-02 · C1-03 · C1-04 · C1-07 · C2-02(없던 프로그램이 틀린 URL 로) · C2-09 · C2-11 · A4-02 · A4-05 · A4-08(NPE) · A4-09 · G4-01(파일마다 20ms) · G4-02 · G4-03 · B4-01 — 나머지는 덜 고친 자리와 옛것
- 새 길 하나: 받은 소스의 `.git/config` 가 include·네트워크 경로를 가리키면 git 이 그것을 연다(C2-H1, 규칙 1 — 추적만)
- 처분: 지적 78 가운데 75 와 후보 넷(C2-H1 · E4-09 · B4-C1 · 설계 76 의 `lineOf`)을 번들 92(설계 79)의 행 52 개로. 나머지는 4절

## 3. 영역별 지적

키 · 등급 · 자리 · 무엇 · 닫는 행(설계 79 §4). 재현 입력·실제 출력은 그 행의 「사실」·「시험」 에 있다.

## D 코드 검사 판정(점검자 10 — 실행 탐침 23회 + 표본 빈도 둘)

| 키 | 등급 | 자리 | 무엇 | 행 |
|---|---|---|---|---|
| D4-01 | Important | RegexRule.java:306-314 | 끊긴 따옴표가 역슬래시 줄바꿈을 넘긴 뒤 끊기면 주석 뗀 글의 줄 수가 하나 준다 — 지적 줄이 밀리고 정리가 옆 줄을 지운다 | 5-146a |
| D4-02 | Important | RuleSet.java:166-171·204·104 | 적재 오류 셋(빈 when 의 absent · luhn 의 정규식·flags · customRules 경로·null 문서)이 BadRule 이 아니라 500 | 5-148 |
| D4-03 | Minor | RegexRule.java:79-83 | script 안 `//` 주석뿐인 스크립틀릿이 `jsp.scriptlets` 에 세인다 | 5-150 |
| D4-04 | Minor | CheckRunner.java:64-74 | 붙여넣기 자동 추정이 긴 한 줄에서 제곱 시간(400KB 102초) | 5-149 |
| D4-05 | Minor | CheckRunner.java:26·239 | `record =`·TS `enum`·`abstract class` 로 시작하는 JS·TS 를 java 로 추정 | 5-149 |
| D4-06 | Minor | MyBatisRules.java:260-273 | 글의 괄호가 안 맞는 동적 SQL 뒤 최상위 `<where>` 가 `mybatis.noWhere` 오탐 | 5-151 |
| D4-07 | Minor | DeclaredTypes.java:52-54·69-72 | 다른 범위의 같은 이름 패턴 변수 때문에 지역 변수 형식이 「모름」 | 5-152 |
| D4-08 | Minor | Zones.java:113-140 | 스크립틀릿 문자열·속성 값 속 `<textarea>`·`<pre>` 글자가 날글 구역을 연다 | 5-150 |
| D4-09 | Minor | RegexRule.java:226-237 | `scriptWrappers` 의 `sb.replace` 가 제곱 꼴 | 5-146b |
| D4-10 | Minor | RuleSet.java:290 · Rule.java:103 | params 의 키가 글이 아니면 검사 뒤 지문에서 ClassCastException | 5-148 |
- 같이 확인: 대기 `5-146` 셋 확인(남음) + 주석·스크립틀릿 안 여는 태그 꼴(1c) · `BlankRelRule.lineOf` 제곱 확인 · D-11 싼 신호 있음
- 뺀 것: `ref: 12`·모르는 키 400([고정] 13) · 감싼 script 안 `document.write("<script …>")` · 줄머리 `--> alert(1);` · `LocalScopes` 탐침 다섯 정상 · `ReturnTypes` 공변 반환 · 예외 퍼즈 5만 4천 회 0

## C1 SQL 분석(점검자 15 — 실행 탐침 10회·꼴 약 150)

| 키 | 등급 | 자리 | 무엇 | 행 |
|---|---|---|---|---|
| C1-01 | Critical | SqlProbe.java:407-429 | 리터럴·주석 경계가 방언마다 갈리는 SQL 에서 파일용 오류문에 리터럴 값이 남는다(규칙 3) | 6-98 |
| C1-02 | Important | SqlJoins.java:317-327 | `UPDATE 별칭 SET … FROM 표 별칭` 의 상관 등식이 별칭 글자를 표 이름으로(지어낸 관계) | 6-99 |
| C1-03 | Minor | SqlJoins.java:301-314 | 표 자리 겹괄호에서 세제곱 시간(4KB 11초) | 6-100 |
| C1-04 | Minor | LogSql.java:106-118 | 짝 없는 Parameters 복제가 다른 문장에 경고 없이 붙는다 | 4-51 |
| C1-05 | Minor | SqlProbe.java:272·275 | `withoutTailSemicolon` 만 아직 RegexRule 주석 지우기 — `#` 주석·역슬래시 따옴표 뒤 `;` 가 남아 거짓 ERROR | 6-98 |
| C1-06 | Minor | SqlJoins.java:278·358 | 따옴표 친 예약어 별칭을 따옴표째 적고 벗겨 찾는다 | 6-99 |
| C1-07 | Minor | SqlTables.java:352-357·464·476 | 분기 뒤 `(` 판정이 한쪽은 넓고 한쪽은 좁다 | 6-101 |
| C1-08 | Minor | SqlTables.java:585-586 | PL/SQL `USING` 이 태그 동사(`<delete>`)에 진다 | 6-101 |
| C1-09 | Minor | SqlProbe.java:358-360 | 머리 주석이 있으면 `headCut` 이 안 자른다 | 6-98 |
| C1-10 | Minor | MapperIndex.java:93 | iBATIS `#nm,jdbcType=VARCHAR#` 이 치환 안 돼 대조가 거짓 ERROR | 6-102 |
| C1-11 | Minor | SqlJoins.java:262·286 | 힌트·ON·USING 뒤 쉼표 조인의 표를 못 읽는다(SqlTables 와 어긋남) | 6-100 |
| C1-12 | Minor | SqlTokens.java:106-109 | 겹친 블록 주석을 첫 `*/` 에서 끝낸다 | 안 세움(4절) |
| C1-13 | Minor | SqlJoins.java:25 | INTO 없는 MERGE 의 대상 표 | 6-99 |
| C1-14 | Minor | SqlTables.java:309-317 | `.` 뒤 낱말(열 이름 APPLY)도 여는 말로 | 6-99 |
| C1-15 | Minor | SqlTables.java:476-477 | 닫힌 상태의 분기 표시가 표 자리를 다시 안 연다 | 6-101 |
- 뺀 것: [고정] 4·5·6 과 6-78·6-75·6-76·6-84 한계 · 6-58·6-69·6-57 결정 · 토큰 차이 오탐

## C2 Java 분석·VCS(점검자 14 — 실행 탐침 12회·꼴 약 75 + git 읽기 전용 여섯)

| 키 | 등급 | 자리 | 무엇 | 행 |
|---|---|---|---|---|
| C2-01 | Important | StmtArgs.java:64-86 | id 를 한 번 더 넘기는 가운데 층은 프로그램당 한 번만 걸려 둘째 문장부터 조용히 빠진다 | 6-93b |
| C2-02 | Important | TypeBind.java:103-104 | 타입 수준 `@RequestMapping` 이 인터페이스·추상 조상에 있으면 접두 빠진 URL | 6-95 |
| C2-03 | Important(옛 git) | WorkingCopy.java:190 | `remote.*.partialclonefilter` 만 있어도 git 이 lazy fetch 를 띄운다 | 0-126 |
| C2-04 | Important | JavaGraph.java:525 | 프레임워크 메서드를 덮고 `super.` 로 넘기는 공통 DAO 는 문장 0·미해결 0 | 6-94 |
| C2-05 | Minor | StmtArgs.java:72-74 | 꼬리가 식의 글이라 다른 메서드의 같은 이름 지역 변수가 한 열쇠 | 6-93b |
| C2-06 | Minor | StmtArgs.java:78-86 | 넘기는 첫 매개변수 하나만 본다 | 6-93b |
| C2-07 | Minor | StmtArgs.java:22·74 | 100자 자르기가 긴 네임스페이스 id 를 합친다 | 6-93b |
| C2-08 | Minor | JpaIndex.java:541 | JPA 길은 후보 하나면 import 를 안 본다 | 6-96 |
| C2-09 | Minor | TypeBind.java:154-160 · JavaGraph.java:369 | 완전 이름으로 적은 필드 타입이 6-90 뒤 빠진다 | 6-93a |
| C2-10 | Minor | JavaGraph.java:404-406 | 후보 둘 이상이면 프로젝트 밖 명시 import 여도 전부 받는다 | 6-93a |
| C2-11 | Minor | JspLinks.java:35 | `c:set` 값 판정이 좁아 `getContextPath()` 꼴 이름이 빠진다 | 6-97 |
| C2-12 | Minor | JavaViews.java:66 | 이름 빈 redirect·forward 뷰가 리터럴 가지로 저장된다 | 6-97 |
| C2-13 | Minor | RegexRule.java:189-206 · JspLinks.java:128 | 주석 안 안 닫힌 `<script` 가 몸을 열어 죽은 링크가 살아난다 | 5-146b |
| C2-14 | Minor | JpaIndex.java:323-329 | 순환 상속 엔티티에서 `root()` 가 안 끝난다 | 6-96 |
- 후보: H1 저장소 설정의 include·UNC(규칙 1) → `0-128` · H2 인터페이스 default 매핑 · H3 JPQL `ON` 뒤 쉼표 · H4 `forwarded` 의 반복 훑기(`6-93b` 가 없앤다)
- 같이 확인: 대기 `6-93` 둘·`0-126` 앞 조각 확인(남음) · 한계 C2-04 는 재현 안 됨

## E 코드 정리(점검자 8 — 실행 탐침 7회·입력 47)

| 키 | 등급 | 자리 | 무엇 | 행 |
|---|---|---|---|---|
| E4-01 | Critical | Cleanup.java:555 | 앞 줄이 잘리거나 스크립틀릿이면 debugger 삭제가 제어문 몸을 바꾼다 | 5-153 |
| E4-02 | Important | SwitchFix.java:146-156 | default 가 선언한 이름(catch·람다 매개변수)과 뒤 case 지역 이름이 겹치면 옮긴 뒤 컴파일 오류 | 5-154 |
| E4-03 | Important | ValueAst.java:262-265·208-224 | 필드로 가려진 이름·초기화 블록 지역을 상수로 보고 `equals` 를 뒤집는다 | 5-155 |
| E4-04 | Minor | LogFix.java:288-293·270-273 | 먼저 도는 초기화가 못 보는 길 셋(메서드 참조·static 필드 접근·`new 같은 파일 형식`) | 5-147 |
| E4-05 | Minor | RegexRule.java:189-206 | 자기 닫는 `<script/>`·주석 안 `<script>` 뒤 날글·주석 줄을 지운다 | 5-146b |
| E4-06 | Minor | Zones.java:45-53 | 비 JS type script 몸이 코드 구역 | 5-146b |
| E4-07 | Minor | MarkupFix.java:188 | 속성 값 안 `>` 로 rel 을 중복해 끼운다 | 5-156 |
| E4-08 | Minor | PiiFix.java:99-111 | 줄 안 홀수 따옴표 뒤 맨 숫자를 가려 SQL·JS 가 깨진다 | 5-156 |
- 후보: E4-09 `tagged` 의 `>` → `5-147` · E4-10 인스턴스 로거 건너뜀 사유 글 · E4-11 적용 보고서에 줄끝 섞임 알림 없음
- 같이 확인: E-13 싼 신호 둘 → `5-158` · E-15 가장 작은 고침 → `5-157a`·`5-157b`

## A DDL·생성기(점검자 18 — 실행 탐침 jshell 8회·74 꼴 + node 27 꼴)

| 키 | 등급 | 자리 | 무엇 | 행 |
|---|---|---|---|---|
| A4-01 | Important | AlterGen.java:296-297 | 새 표의 CREATE·인덱스만 스냅샷 스키마를 안 감싼다 | 1-131 |
| A4-02 | Important | InsertGen.java:608-611 | 정확 이름을 대상 방언의 접기로 재서 방언이 다르면 전부 따옴표 | 4-52 |
| A4-03 | Important | DdlGen.java:115-124 ↔ AlterGen.java:551 | 바꾼 유니크 이름을 반영 DDL 이 모른다 — DROP + ADD | 1-132 |
| A4-04 | Important | AlterGen.java:800-807·763-774 | 시스템 이름 대 대체 이름: 차이 0 인데 FK·유니크 DROP + ADD | 1-132 |
| A4-05 | Important | DdlGen.java:102-105 | 자동증가 부모는 정수, FK 자식은 소수 타입 — FK 실패 | 1-134 |
| A4-06 | Minor | AlterGen.java:109-112·367-371 | MSSQL `int identity` 글이 `[손]` 과 어긋난 AUTO_INCREMENT | 1-135 |
| A4-07 | Minor | SnapshotDiff.java:124-142 | 인덱스 구조 글은 대소문자를 안 접는다 | 1-136 |
| A4-08 | Minor | DdlAlter.java:355-358 | `IF NOT EXISTS` 뒤 안 닫힌 이름에서 NPE | 1-137 |
| A4-09 | Minor | DdlPull.java:89 | `db..표` 의 빈 스키마 마디로 ALTER 가 빠진다 | 1-138 |
| A4-10 | Minor | InsertGen.java:372 | 복합 FK 키의 헛경고 | 4-53 |
| A4-11 | Minor | AlterGen.java:90 | 옛 기본값 `NULL` 글을 기본값으로 센다 | 1-135 |
| A4-12 | Minor | DdlAlter.java:19 | `ALTER ONLINE/IGNORE TABLE` 이 통째로 빠진다 | 1-137 |
| A4-13 | Minor | DdlReaderParts.java:36-41 | 표 안 인덱스 두 꼴이 가짜 컬럼 | 1-138 |
| A4-14 | Minor | DdlAlter.java:49 · DdlReader.java | 제약 이름·`FOR 컬럼` 자리가 아직 공백에서 끊긴다 | 1-137 |
| A4-15 | Minor | InsertGenParts.java:76-79 | PG `BIT(n)` 에 `B'1'` | 4-53 |
| A4-16 | Minor | InsertGenParts.java:70-73 | INTERVAL 앞자리 정밀도를 넘는 값 | 4-53 |
| A4-17 | Minor | DdlGen.java:454 | FK 자식 길이가 늘 255 | 1-134 |
| A4-18 | Minor | InsertGenParts.java:61-67 | 글자·수 섞인 IN 목록의 글자 값이 따옴표 없이 | 4-53 |
- 후보: `strDefault` 반대 방향 역슬래시 · `AlterGen` 의 자동증가 가드 없는 기본값 경고 · `MODIFY IF EXISTS 없는컬럼`
- 같이 확인: 1-130 이 표본에서 닿는 줄 셋 · 바뀔 골든 0 · A1-02 뿌리는 한계 유지 · `DdlReader` → `DdlGen`·`AlterGen` 화면 길 없음

## BFG 산출물·DB 경계·웹(점검자 13 — 실행 탐침 7회·꼴 약 120 + H2 줄 주석 닫는 글자 전수)

| 키 | 등급 | 자리 | 무엇 | 행 |
|---|---|---|---|---|
| F4-01 | Minor | ReadOnlyJdbc.java:156 | 숫자 뒤 토막이 `_` 로 시작하면 꼬리를 안 잰다(`1_0FOR SHARE`) | 9-11 |
| F4-02 | Minor | ReadOnlyJdbc.java:182-188 | 긴 16진 토막에서 제곱 시간(30만 자 27초) | 9-11 |
| G4-01 | Important | LocalFiles.java:188-192 | 경로 검사가 파일마다 약 20ms | 0-129 |
| G4-02 | Important | ProfileRoutes.java:242-254 | BMP 밖 글자 뒤 password 키 자리가 어긋나 코드 검사 저장이 늘 400 | 0-130 |
| G4-03 | Important | DocCommands.java:108·112-128 | 문서를 만들고도 끝 코드 1 | 8-25 |
| G4-04 | Minor | SelfTest.java:76-83 | selftest 드라이브 판정 실패 꼴은 나올 길이 없다 | 안 세움 |
| G4-05 | Minor | Profile.java:194-199 | Add-Opens 없으면 프로필 읽기가 「YAML 형식」 으로 실패 | 0-131 |
| G4-06 | Minor | PathRules.java:118-122 | 없는 드라이브의 드라이브 상대 경로에서 IOError | 0-131 |
| G4-07 | Minor | LocalFiles.java:464 | 결과 폴더 표식 쓰기 실패가 500 | 안 세움 |
| B4-01 | Minor | CodeAndLink.java:123-132 | 08 SELECT 만 예약어 이름을 안 접고 감싼다 | 2-57 |
| B4-02 | Minor | Definitions.java:360 | 여러 낱말 타입의 둘째 낱말이 컬럼 이름으로 맞는다 | 2-57 |
| B4-03 | Minor | XlsxWriter.java:78 | 머리 칸은 칸 상한을 안 탄다 | 2-58 |
| B4-04 | Minor | DeliverableService.java:324 | 같은 file 판정이 글자 비교뿐 | 2-58 |
- 후보: B4-C1 다시 계산 표시가 채운 시트에만 → `2-59` · G4-C1 링크가 가리키는 UNC 는 닿은 뒤 거절
- 뺀 것: `SET`·`USE` 통과와 부작용 함수([고정]) · `ADD`·`DIALOG` · `latest` · 프로필 백업 22 꼴 누출 0 · `mergeRules` 넷 정상 · LocalOnly

## 4. 행으로 안 세운 것 · 처분

- **잠재 유지** — A-33(`REFERENCES T ()`)·A-37(DDL 의 `ON DELETE`): 닿는 화면 길이 없다(설계 79 맥락 보정 B)
- **한계 유지** — A1-02 뿌리(같은 방언 식 기본값) · `Job.subscribe` 의 락 · `Js.removeSpaces`(순수본과 같은 판정) · `5-147` 의 람다·익명 클래스 몸 · `5-158` 의 `<pre>` 밖 가운데 글 줄 · C1-12(겹친 블록 주석의 표 추출 — 방언마다 갈린다)
- **안 세운 지적** — G4-04(죽은 가지 — `0-126` 뒤에는 `[오류]` 한 줄로 끝난다) · G4-07(권한 없는 결과 폴더 — 시험이 OS 권한에 달렸다) · E4-10(사유 글 — 기존 글자를 안 바꾼다) · E4-11(적용 보고서의 줄끝 알림)
- **후보(다음에 볼 만한 것)** — C1 후보-1(`databaseId="pg"`·`"ms"` 변형 하나) · 후보-2(대조 합계에 「(파일)」 행) · 후보-3(대조 라우트가 못 읽은 xml 을 안 센다) · 후보-4(안 닫히는 `$t$` 제곱) · 후보-5·6(LogSql — 6 은 `4-51` 이 닫는다) · C2-H2(인터페이스 default 매핑) · H3(JPQL `ON` 뒤 쉼표) · A 후보 셋 · G4-C1 · `InsertGen.cols` 의 `NEXT VALUE FOR` 자동증가 판정(`1-130` 한계)
- **사람이 할 것(⑫ 에 더한다)** — git 이 저장소 설정의 UNC include·경로를 실제로 여는지(`0-128`) · 확인 뒤 10초 안에 `subst` 를 다시 거는 길(`0-129`)
