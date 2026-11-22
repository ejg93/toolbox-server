# 설계 77 — 번들 91: 가로 점검 3차 결과 + 대기 행 다섯 (Fable 설계 · Opus 실행, 2026-11-22)

## 맥락

번들 90(#135 `57760d1`) 머지 뒤 가로 점검 3차를 했다 — 번들 84~90 이 바꾼 `src/main`·화면·템플릿 파일 127개(`git diff --name-only ceb6881 57760d1`)를 영역 아홉으로 나눠 점검자(Fable xhigh) 하나씩이 통째로 읽고, 지적마다 내가 인용 줄을 코드로 다시 읽거나 탐침을 다시 돌려 「확인」 만 남겼다. 결과는 `design/76-audit3.md`(이 플랜의 부록 A 가 그 원문)다. 이 번들이 그 확인 결함 전부와 대기 행 다섯(`6-75`·`6-76`·`6-77`·`1-116`·`1-117`)을 한 PR 로 닫는다. 실행은 무인이고 시간 컷이 없다.

**사용자 결정(2026-11-22 — 고정, 다시 묻지 않는다. 메모리 `bundle91-decisions`·`bundle90-decisions`)**
1. 묶음 — 점검 3차 확인 결함 전부(같은 파일 Minor 까지) + 대기 행 다섯을 번들 91 하나·PR 하나로(「최대한 많이 묶어서」)
2. `6-76` **(가)** — 파일 이름 토막이 `oracle11g`·`tibero6` 꼴(낱말 + 숫자 + 글자 하나)이어도 방언 낱말로 본다
3. `1-117` — self-check 기준선을 **낮추는** 갱신은 허용 · `6-77` 은 `JpaIndex.java` 를 연다
4. Fable 설계 → Opus 「번들 해」(effort high) · 무인 · **머지 위임** · 시간 컷 없음 · 마무리 독립 리뷰 Fable xhigh · 닫기 커밋은 새 로컬 가지
5. 뺌 — `5-100`·`5-31` 순수본 재주입 · 반입 zip · 조건부 `5-14`·`5-15`·`6-9` · 보류 `2-7`·`2-8`·`5-7`·`4-10`. 코드 검사(`rules.yaml`·`core/check`)를 고친 행은 마무리에서 `5-100` 에 한 줄(CLAUDE.md 「순수본 밀린 목록」)
6. **네트워크 드라이브(점검 G-10)는 막는다**(이 설계 세션에서 답) — 「폴더 경로에 로컬에 있는 파일이 아닌 것까지 읽을 수 있다면 못 하게 해야 한다」. 드라이브 글자에 매핑된 네트워크 드라이브와 링크를 푼 실제 경로가 네트워크인 것을 거절한다(`0-125a`·`0-125b`)

7. **기준선 낮춤은 여섯 줄 다 허용**(이 설계 세션에서 답 — 「그냥 허용」) — `1-117`(`AlterGen.modifyColumn`) 말고 `1-121`(`DdlGen.type`) · `7-37`(`GenModel.of`) · `4-48`(`InsertGen.genVal`) · `6-89`(`JpaIndex.jpql`) · `2-52`(`XlsxFiller.fill`). 전부 분기를 도우미로 빼거나 조건을 덜어 **값이 내려가는** 것이다. 오르는 값·새 줄은 하나도 없고, 그 밖 줄이 움직이면 멈춘다

**보정 A — 대기 행 다섯**(2026-11-22, 트리 `1fa4538` = 코드 `57760d1`, 탐침은 jshell 표준입력 · `target/classes` 10:29 빌드 = HEAD 코드 — 파일을 쓰지 않았다)

### 6-75 SqlJoins — 확인(남음)
- `Scopes.derived:244-253` 은 `expect && t[i]=="(" && startsQuery(t,i+1)` 일 때만 별칭을 적는다
  - 겹괄호 `FROM ((SELECT …)) v` · `FROM ((SELECT …) UNION (SELECT …)) v` — 첫 `(` 뒤가 `(` 라 startsQuery 거짓, 둘째 `(` 에서는 닫는 괄호 뒤가 `)`·`UNION` 이라 별칭 없음
  - `JOIN LATERAL (…) v` — `LATERAL` 이 STOP(SqlTokens.STOP:24)이라 `fill:233` 에서 expect 가 꺼진다
  - `CROSS APPLY (…) v` — `APPLY` 가 `OPENERS:25` 에 없다(`SqlTables.opener:571` 은 APPLY 를 여는 말로 본다 — 판정 중복 어긋남)
- 탐침(지금 출력):
  - `SELECT * FROM T1 v JOIN T2 b ON v.ID = b.ID WHERE b.X IN (SELECT c.X FROM ((SELECT ID, X FROM T3)) v JOIN T4 c ON v.ID = c.ID)` → T1.ID—T2.ID · **T1.ID—T4.ID(지어냄)**
  - `… FROM ((SELECT ID FROM T3) UNION (SELECT ID FROM T5)) v JOIN T4 c ON v.ID = c.ID)` → 같음(지어냄)
  - `… WHERE EXISTS (SELECT 1 FROM T4 c JOIN LATERAL (SELECT ID FROM T3 x WHERE x.P = c.P) v ON v.ID = c.ID)` → T1.ID—T2.ID · T3.P—T4.P · **T1.ID—T4.ID(지어냄)**
  - `… FROM T4 c CROSS APPLY (SELECT ID FROM T3 x WHERE x.P = c.P) v WHERE v.ID = c.ID)` → 같음(지어냄)
  - 안 닫힌 괄호 `SELECT a.X FROM T1 a WHERE (a.Y = 1 UNION SELECT c.X FROM T3 c WHERE c.X = a.X` → **T1.X—T3.X(지어냄)** — `of:191` `sideOrChild(cur, opened.size())` 가 안 닫힌 `(` 를 깊이에 센다
- 지킴이 다섯(지금 맞게 나옴 — 열쇠 없음):
  - 겹친 괄호 UNION `SELECT * FROM T1 a WHERE a.ID IN (((SELECT b.ID FROM T2 b WHERE b.X = a.X)) UNION ((SELECT c.ID FROM T3 c WHERE c.X = a.X)))` → T1.X—T2.X · T1.X—T3.X
  - 섞인 괄호 `SELECT * FROM ((T1 a JOIN T2 b ON a.ID = b.ID) JOIN (SELECT ID FROM T3) v ON v.ID = a.ID) WHERE a.X IN ((SELECT c.X FROM T4 c) UNION (SELECT d.X FROM T5 d WHERE d.Y = b.Y))` → T1.ID—T2.ID · T2.Y—T5.Y
  - 괄호 친 표 + 괄호 UNION `SELECT * FROM (T1 a JOIN T2 b ON a.ID = b.ID) JOIN ((SELECT ID FROM T3) UNION (SELECT ID FROM T4)) u ON u.ID = a.ID` → T1.ID—T2.ID
  - 스칼라 별칭 `SELECT a.ID, (SELECT MAX(c.X) FROM T3 c WHERE c.ID = a.ID) b FROM T1 a JOIN T2 b ON a.ID = b.ID` → T1.ID—T3.ID · T1.ID—T2.ID
  - 쉼표 뒤 파생 표 `SELECT * FROM T1 v JOIN T2 b ON v.ID = b.ID WHERE b.X IN (SELECT c.X FROM T4 c, (SELECT ID FROM T3) v WHERE v.ID = c.ID)` → T1.ID—T2.ID
- **넓힘(점검 3차 — 설계 75 한계를 결함으로 올림)**: 파생 표 뒤 쉼표 조인의 표가 안 읽힌다 — Oracle 식 쉼표 조인에서 관계가 조용히 빠진다
  - `SELECT * FROM (SELECT ID FROM T3) v, T1 a, T2 b WHERE a.ID = b.ID` → `[]`(기대 T1.ID—T2.ID)
  - `SELECT * FROM T1 a, (SELECT ID FROM T3) v, T2 b WHERE a.ID = b.ID AND v.ID = a.ID` → `[]`(기대 T1.ID—T2.ID)
  - `SELECT * FROM T1 a, LATERAL (SELECT ID FROM T3 x WHERE x.P = a.P) v, T2 b WHERE a.ID = b.ID` → T1.P—T3.P 만(기대 + T1.ID—T2.ID)
- 지금 맞는 것: `OUTER APPLY (…) v JOIN T2 b ON a.ID = b.ID` → T1.P—T3.P · T1.ID—T2.ID
- 처방 요지: `queryParen`(겹괄호 — 안쪽 질의 괄호 뒤가 `)`·집합 연산이면 바깥도 질의 괄호) · `fill` 의 expect 에 `LATERAL` 잇기 · `OPENERS` 에 `APPLY` · `matched`(짝 있는 `(` 만 깊이에) · `derived` 가 끝 자리를 돌려주고 그 뒤 쉼표가 표 자리를 연다
- self-check: SqlJoins 줄 없음(전부 15 이하 유지)

### 6-76 hashText — 확인(남음)
- `MapperIndex.hashText:288-294` — `NAME_SEP.split(base)` 토막이 `NOT_MYSQL_FILE` 과 **같을** 때만. `oracle11g`·`tibero6` 은 안 맞는다
- 처방: 토막 끝의 `\d+[a-z]?` 를 떼고 맞댄다(사용자 (가)). `Sync_Oracle_Mapper.xml` 은 그대로(토막 `oracle` — 연산자)
- self-check: `hashText` 줄 없음

### 6-77 JpaIndex 조상 — 확인(남음)
- `JpaIndex.superEntity:310` `lookup(단순 이름, cur.from())` → `pick:540` 후보 하나면 그대로 → `up.size()==1` 이면 오른다. `JpqlPaths.parent:116` 은 `named` 로 좁힌다 — 어긋남
- 같은 꼴 둘 더: `base:350`(저장소 인터페이스 사슬) · `repo:387`(커스텀 저장소 이름)
- `JpqlPaths.named:123` 은 `private static` — package-private 로 열어 셋이 쓴다. JpaIndex 메서드 수 38(기준선) — 메서드를 안 더한다
- self-check: `JpaIndex` 메서드 38 · `entity(1)` 18 · `jpql(6)` 40 — 안 움직인다(`superEntity`·`base`·`repo` 는 줄 없음)

### 1-116 · 1-117 AlterGen — 확인(남음)
- `defaultChanged:81-90` — `redefined ? LOST_REDEFINED : LOST_KEPT`. 옛 기본값이 비었어도 「뒤 MODIFY COLUMN 이 기본값을 지운다」
- `modifyColumn:358` `redefines(mariadb(), typeChanged, nullChanged, cmtChanged)` 와 `:373` `typeChanged || nullChanged || cmtChanged` 가 같은 식 둘
- 처방: `boolean redefined = redefines(…)` 한 번 → `:373` 이 `if (redefined)`. 복잡도 40 → 38 어림(`||` 둘) — 기준선 갱신(내려감, 허용). 옛 기본값이 비면 새 글
- 기준선 줄: `AlterGen` `Run` 메서드 40 · `keyBreakingFks(1)` 18 · `uniquesAndIndexes(4)` 19 · `modifyColumn(7)` 40

**보정 B — 점검 3차 확인 결함**: 부록 A(= `design/76-audit3.md`). 152 건(Critical 8 · Important 32 · Minor 112) 전부 인용 줄을 코드로 다시 읽었다. 행의 「사실」 줄이 그 재현 꼴이다.

## 1. 목표와 범위

목표 — 가로 점검 3차의 확인 결함 152 건과 대기 행 다섯을 행 73 개 · PR 하나로 닫는다. 사용자가 겪는 변화(큰 것만):
- **절대 규칙** — 읽기 전용 연결이 `--\r…`·`1e5INTO`·`ADD SENSITIVITY` 꼴 문장을 거절한다(규칙 6) · 부분 클론 저장소를 폴더 칸에 넣어도 git 이 원격에 안 닿는다(규칙 1) · DB 대조 xlsx 의 「DB 메시지」 에 매퍼 SQL 의 리터럴 값이 안 남는다(규칙 3) · `subst` 드라이브로 프로필을 못 읽는다(규칙 2)
- **코드 검사·정리** — `<script>` 를 `<!-- … //-->` 로 감싼 JSP(eGov 넷 중 하나)의 스크립트 안 지적·링크가 나온다 · JSP 의 `if (…) // 주석`⏎`debugger;` 를 안 지운다 · TS `f<T>()`·`cb!()` 가 든 console 문장을 안 지운다 · 규칙 정의 오류가 500 대신 400 + 사유
- **프로그램 분석** — 공통 DAO(`selectList(queryId, …)`)를 거치는 문장이 CRUD 에 나온다 · 파생 표 뒤 쉼표 조인·분기마다 같은 별칭의 관계가 맞게 나온다 · 프로젝트 밖 클래스를 import 한 필드·조상이 이름만 같은 프로젝트 클래스로 안 풀린다
- **DDL·생성기** — MariaDB 반영 DDL 이 코멘트만 바뀐 컬럼의 못 옮기는 기본값을 `[손]` 으로 알린다 · 이름 없는 FK 둘이 같은 이름으로 안 나간다 · PG 파티션 부모 표가 스냅샷에 실린다 · pg_dump 꼴 CHECK 의 허용 값이 INSERT 에 쓰인다 · 표 이름이 예약어(`TB_CASE`)여도 생성 소스가 컴파일된다
- **산출물** — 양식의 합계·건수 수식이 값과 맞는다 · 큰 스냅샷에서 작성안내가 칸 상한으로 실패하지 않는다 · 프로필의 손으로 적은 규칙 덮어쓰기(`{severity}`)가 화면 저장에 안 사라진다

In scope — 행 73 개(4절 S0 표): ⓪ Critical 독립 넷 · ① 분석 SQL 열 · ② 코드 검사 아홉 · ⑥ 코드 정리 여덟 · ⑤ 분석 Java 아홉 · ③ DDL 열하나 · ④ 생성기 다섯 · ⑦ 산출물 여섯 · ⑧ DB 경계 둘 · ⑨ 웹·CLI 아홉 + `S0`·`V-70a`·`R-37`·`V-70`

Out of scope
- 뺀 것(사용자 결정 5) · 잠재 A-33·A-37 · `2-17`
- 부록 A 4절의 한계 조각과 후보 · 행마다 「한계」 줄에 적은 것
- 새 라우트·새 화면·새 규칙·새 마이그레이션·새 의존성 0(규칙 수 147 · 마이그레이션 17 · `Unresolved.KINDS` 20 그대로). 새 파일은 `core/analyze/StmtArgs.java` 하나. `pom.xml` 은 `0-125a` 의 두 줄(매니페스트 `Add-Opens` · 시험 `argLine`)만
- 순수본(`pure/`) — 안 고친다. 코드 검사 행(`5-128`·`5-134`, 해당하면 `5-133`)은 마무리에서 `5-100` 에 한 줄

## 2. 현재 구조 요약

행마다 「사실」 줄이 지금 자리(줄 번호 `57760d1`)와 재현 꼴이다. 여러 행에 걸치는 것만 여기 둔다.

**self-check 기준선**(`src/test/resources/golden/check/self-check.txt` — `SelfCheckTest.mainMatchesBaseline` 이 글자까지 맞춘다. 이름표는 `메서드(인자 수)`). 이 번들이 건드리는 파일의 줄 —
- 분석: `SqlProbe.run(4)` 17 · `SqlTables.extract(2)` 17 · `MapperIndex.startElement(4)` 18·`Statement(8)` · `JpaIndex` 메서드 38·`entity(1)` 18·`jpql(6)` 40 · `JavaGraph` 메서드 34·`walk(5)` 42·중첩 5·`run(2)` 31·`index(4)` 21·`entityManager(4)` 19·`querydsl(3)` 22·`views(3)` 19·`repoCall(8)` · `JavaViews.value(4)` 18 · `AnalyzeRunner.run(4)` 30·길이 116 · `AnalyzeStore.save(3)`
- 검사·정리: `RegexRule.cLike(2)` 25 · `Rule.Def(11)` · `RuleSet.load(2)` 26 · `MyBatisRules.startElement(4)` 17 · `Cleanup.deleteConsole(4)` 19·`worse(6)` 36·`consoleEnd(3)` 17 · `CleanupRunner.apply(10)`·`preview(8)`·`writeReport(8)`
- 생성: `AlterGen.Run` 메서드 40·`modifyColumn(7)` 40·`keyBreakingFks(1)` 18·`uniquesAndIndexes(4)` 19 · `DdlGen.table(1)` 16·`type(2)` 24 · `DdlReader.item(3)` 29·`read(1)` 30·중첩 6 · `GenModel.of(4)` 32·길이 107 · `InsertGen.generate(4)` 20·`buildUpsert(5)` 24·`csvLiteral(5)` 31·`genVal(4)` 36·`Col(11)` · `Validation.parse(1)` 28·`of(5)` 56 · `DtoGenerator.generate(2)` 30
- 산출물·웹: `XlsxFiller.fill(4)` 25·중첩 5 · `DeliverableService.build(8)` 22·`writeGuide(11)` · `Relations.infer(2)` 26·`named(2)` 19 · `Definitions` 메서드 34 · `LogicalRoutes.register(4)` 19·길이 104 · `LocalFiles.resultRoot(5)` 22·`list(4)` 21 · `ProfileStore.saveCodeCheck(3)` 22 · `SelfTest.body(0)` 19 · `SnapshotStore.constraint(12)` · `CheckRoutes.postRun(6)` 44
- 줄 없는 메서드의 상한 — 복잡도 15 · 길이 100 · 중첩 4 · 인자 7 · 클래스 메서드 30

**함정**
- 줄 있는 메서드에 분기(`if`·`&&`·`||`·`?:`·`for`·`catch`, 람다 안 것도)나 줄을 더하면 빨강이다 — 행은 그 자리의 **식을 도우미 호출로 바꾸는** 꼴로 적었다. 도우미는 줄 없는 클래스·중첩 클래스·`private static` 에 둔다
- 메서드 수 기준선 클래스(`JavaGraph`·`JpaIndex`·`AlterGen.Run`·`Definitions`)에는 메서드를 못 더한다 — `TypeBind`·`StmtId`·`StmtArgs`·`JpqlPaths`·바깥 `AlterGen` static 으로
- 인자 수가 이름표다 — `buildUpsert(5)` 에 인자를 더하면 「사라진 줄 + 새 줄」 이다
- 골든 JSON(`sql-joins.json`·`sql-tables.json`)은 `GoldenFiles.assertJson` 이 열쇠를 정렬해 쓴다 — 새 열쇠는 글자 차례 자리에 든다. 갱신은 `-Dgolden.update=true`
- `MapperIndexTest`·`JpaIndexTest` 의 골든 시험은 픽스처 폴더 전체를 본다 — 폴더에 파일을 안 더하고 인라인 `Source` 로
- `SqlTables.BRANCH`(`\u0001`)는 낱말 하나다 — 시험의 `B` 는 `String.valueOf(SqlTables.BRANCH)`
- `DdlGen.Gen.warnings`·`AlterGen.Run.warnings` 는 `LinkedHashSet` — 같은 경고는 한 번만 남는다
- SpotBugs REDOS 는 「수량자 단 묶음 안 수량자」 를 소유 수량자여도 막는다(번들 90) — 묶음 반복(`(?:…+)*`)을 새로 쓰지 않는다. `(?:…)?` 는 지난다(`DdlPull.JOIN_SEP` 꼴)
- PMD `AvoidFieldNameMatchingMethodName`(대소문자 무시)·`AvoidReassigningLoopVariables`(`for` 변수를 몸에서 안 바꾼다 — `while`) — 플랜 코드는 그렇게 적었다
- Bash heredoc 은 백슬래시를 벗긴다 — 정규식·역슬래시가 든 편집은 Edit·Write 로
- jshell 표준입력 탐침은 `\\` 를 `\` 로 접는다 — 탐침에서 `\r`·`'` 는 `(char) 13`·`(char) 39` 로
- JavaParser 3.26.3 — 패턴 변수 노드는 `TypePatternExpr`(`5-131`·`5-139`). 텍스트 블록이 `\\"""` 로 끝나면 파서도 못 읽는다(`5-128` 은 주석 지우기만 단언)
- `ToolsFolderTest` 가 `CLAUDE.md`·화면 JS 를 읽는다 · `SmokeHtmlUnitTest.client(true)` 는 스크립트 예외를 던진다
- 행의 시험 클래스는 저장소에 있는 이름이다(코드 정리 고침은 `CleanupTest`, 규칙 적재는 `CheckRulesTest`, DdlAlter·DdlPull 은 `DdlReaderTest`, JavaViews 는 `JavaGraphTest`, CLI 는 `BatchCliTest`·`DocCliTest`·`SelfTestCliTest`·`MainTest`). `-Dtest` 목록에 없는 이름이 섞여 있으면 빼고 돈다

## 3. 설계 결정 — 전부 [고정]

1. **기준선은 낮추기만** — 값이 오르거나 새 줄이 생기는 고침은 안 한다. 낮추는 갱신은 여섯 행(`1-117`·`1-121`·`7-37`·`4-48`·`6-89`·`2-52`)의 적힌 줄뿐. 버린 대안 — 기준선을 통째로 다시 뜨기: 오른 값이 묻힌다
2. **`<script>` 몸의 `<!--` 는 JS 줄 주석으로 읽는다**(`5-128`) — `RegexRule.scriptWrappers` 가 몸 안 `<!--` 를 `//  ` 로 바꾼 뒤 지금 주석 지우기를 탄다. `stripComments` 자체는 안 바꾼다(분석·`Zones`·`Ignores` 가 쓴다). 검사(`skipped`)·정리(`Cleanup`·`PiiFix`)·링크(`JspLinks`)가 `RegexRule.bare` 한 글을 본다. 버린 대안 — `blank("<!--", "-->")` 가 script 몸을 건너뛰게: 주석 처리한 `<script>` 블록이 살아난다
3. **SQL 주석·리터럴 경계는 `SqlTokens` 한 자리**(`6-81`) — 분석(`SqlTables`·`SqlJoins`·`SqlProbe`)은 `SqlTokens.stripComments`. `RegexRule.stripComments(…, "sql")` 은 검사 쪽만
4. **분기 표시는 범위를 안 가른다**(설계 75 §3-2 그대로) — 같은 범위의 같은 별칭은 **자리 앞의 가장 가까운 정의**로 푼다(`6-78`)
5. **파생 표** — 겹괄호는 안쪽 질의 괄호 뒤가 `)`·집합 연산일 때만 질의 괄호 · 별칭은 빈 글(표가 아니다) · 그 뒤 쉼표는 표 자리를 연다(`6-75`). 파생 표 별칭을 안쪽 표로 푸는 것은 안 한다
6. **`6-76` (가)** — 파일 이름 토막 끝의 `\d+[a-z]?` 를 떼고 낱말과 맞댄다. `db2` 는 파일 이름에서 안 본다(6-72 그대로)
7. **조상·타입은 「자식의 글이 가리킬 때만」** — `JpaIndex.superEntity`·`base`·`repo` 가 `JpqlPaths.named` 를 같이 쓰고(`6-77`), `JavaGraph.lookup` 은 후보가 하나여도 명시 import 가 다른 FQCN 이면 빈 목록(`6-90`). 별 import·같은 패키지는 지금처럼 받는다(모르면 받는다)
8. **공통 DAO 의 문장 id 는 호출 스택으로 거슬러 읽는다**(`6-86`) — `walk` 는 재귀 앞뒤 `push`·`pop` 과 방문 열쇠 꼬리만. 스택 층은 `callee` 가 같을 때만 쓴다(어긋난 층을 안 읽는다)
9. **git** — 모든 git 호출 환경에 `GIT_NO_LAZY_FETCH=1`, 그리고 promisor 원격·`extensions.partialclone` 이 설정된 저장소는 조회를 거절(`0-120`). 버린 대안 — 환경 변수만: 옛 git 이 모른다
10. **읽기 전용 래퍼는 엄격한 쪽으로** — 홀로 선 CR 은 통째 거절 · 숫자에 붙은 글자 토막은 모든 꼬리를 잰다 · `ADD`·`DIALOG` 는 금지 낱말(`9-10`). `SET`·`USE` 는 그대로(설계 69 §3-6)
11. **`[손]` 꼬리 셋**(`1-116`) — 통째 재정의가 아니면 옛 글 · 재정의인데 옛 기본값이 없으면 「없이 나간다」 · 있으면 「지운다」. 기본값이 안 바뀌어도 재정의면 못 옮기는 식을 알린다
12. **INSERT 의 정확 이름은 스냅샷 모드·INSERT 문만**(`4-46`) — upsert 틀과 DDL 모드는 지금 그대로
13. **규칙 적재 오류는 전부 `BadRule`** — `RuleSet.load` 몸은 안 옮긴다(`5-130`). 덮어쓰기의 모르는 키는 거절
14. **비교와 반영 DDL 은 같은 짝짓기** — FK 기본 규칙은 `FkRule.folded` 한 자리, 컬럼·제약 이름은 대문자로 접어 짝짓는다(`1-128` — `AlterGen` 쪽에 맞춘다)
15. **PG 파티션** — 부모 표를 싣고 자식 파티션은 지금처럼 표로 둔다(`1-126`)
16. **마스킹 알림은 생성 SQL 머리의 `-- [알림]` 줄**(`3-25`) — 화면을 안 고친다. 한글 이름은 맨 이름(예약어가 아니면 안 감싼다)
17. **프로필 백업 가림은 `connections[*].password` 자리만**(`0-122`) — 파서로 자리를 구해 지금 정규식 맞춤을 거른다. 병합은 옛 값이 맵이면 `enabled` 만 바꾼다(`0-123`)
18. **배치 끝 코드** — `--docs` 로 **명시한** 문서가 건너뛰어지면 1(`8-24`). `latest` 는 프로필의 `name` 으로
19. **권한 권고는 폴더로**(`8-24`) — 저장 방식(바꿔 끼우기 — 0-112)은 안 바꾼다
20. **로컬 드라이브만**(`0-125a`, 사용자 2026-11-22) — 드라이브 종류는 OS 에 묻는다(`sun.nio.fs.WindowsNativeDispatcher.GetDriveType` 리플렉션 + 매니페스트 `Add-Opens`). 원격(4)이면 거절, **못 물으면 거절**(닫힌 실패 — Windows 에서만. 그 클래스가 없는 OS 는 드라이브 글자가 없어 해당 없음). 버린 대안 — JNA: 새 의존성 + 임시 폴더에 DLL 을 풀어 폐쇄망 AppLocker 에 걸린다 / `net use` 프로세스: `ArchitectureTest` 가 프로세스 자리를 git·svn 으로 못 박았다 / 한계로 문서화: 사용자가 거절
21. 행 차례 — Critical 독립 넷 먼저, 그 뒤 도우미를 주는 행(`5-128`·`6-81`·`5-137`)이 받는 행보다 앞, 같은 파일 행은 이어서

## 4. 실행 스텝

행마다 끝은 CLAUDE.md 「Task 끝」 순서(분할표 행·이력 → `bash scripts/verify.sh` → `git commit` 따로 → `task-done`). `src/main/java` 를 고치는 행은 시험을 먼저 쓰고 RED 를 본 뒤 고친다. 커밋 제목 `fix: <번호> <무엇>`(문서만이면 `docs:`). 줄 번호는 `57760d1` 기준 — 앞 행이 밀면 메서드 이름으로 찾는다.

공통 사다리
- `SelfCheckTest.mainMatchesBaseline` 빨강 —
  - **오른 값·새 줄**: 분기를 도우미로 뺀다(자리는 2절 함정). 그래도 오르면 그 조각을 빼고(행의 나머지는 친다) 이력 「정한 것(계획 밖)」 + 새 행
  - **내린 값·사라진 줄**: 여섯 행의 적힌 줄이면 `-Dtest=SelfCheckTest -Dgolden.update=true` 로 갱신하고 diff 가 그 줄뿐인지 본다. 그 밖이면 고침이 넓다 — 플랜과 댄다
- `golden/` diff — 행의 「의도된 골든 변화」 에 적힌 것만 갱신(`bash scripts/mvn.sh -q test -Dtest=<시험> -Dgolden.update=true`). 그 밖은 고침이 넓다 → 고친다
- **RED 가 「전」 과 다를 때** — 이미 기대대로 나오는 사례는 지킴이로 두고 이력에 한 줄(그 조각은 고칠 것이 없다). 기대도 「전」 도 아닌 출력이면 7절
- 기존 단언이 빨개질 때 — 그 단언이 이 행이 고치는 결함을 못 박은 것이면(행의 「사실」 과 같은 꼴) 기대를 바꾸고 이력에 적는다. 아니면 고침이 넓다
- 시험 글에 실제 사업 이름·SQL·값을 안 쓴다(`T1`~`T5`·`T91_*`). 인라인 `Source` 로(픽스처 폴더에 안 더한다)
- 시간 단언은 `assertTimeout`(선형이면 여유 있게 지나는 값 — 행에 적힌 초) — 느린 PC 에서 빨개지면 입력을 반으로 줄이고 이력에
- PMD·SpotBugs·ErrorProne 은 빠른 `verify.sh` 가 안 본다 — `V-70a` 가 본다. 새 짝은 코드로 고친다(기준선에 안 더한다)
- 행의 「대상」 밖 파일을 고쳐야 하면 둘까지(이력에 적는다), 셋부터 7절

### S0 — 설계 문서 · 행 · 번들 표 (실행 세션이 했다)
1. 가지 `work/2026-11-22b` 그대로(HEAD = main `57760d1` + 번들 90 닫기 `1fa4538` + `docs: 설계 76` 커밋 — 같은 PR 에 실린다). 새 가지 안 딴다
2. **`design/76-audit3.md` 가 없으면 먼저** — 이 플랜의 「부록 A」 를 그 파일로 옮기고(머리 `# 설계 76 — 가로 점검 3차 결과(2026-11-22)`, 소제목 한 단 올림, 부록 머리의 안내 문단은 뺀다) `PROGRESS.md` 「번들 90 닫기」 줄의 `(이 커밋)` → `1fa4538` · 이력 한 줄(「가로 점검 3차 — 설계 76: 확인 152(Critical 8 · Important 32 · Minor 112), 영역 아홉」, 해시 `(이 커밋)`) → `bash scripts/verify.sh` → `git commit`(따로) `docs: 설계 76 — 가로 점검 3차`. 있으면 건너뛴다
3. 이 플랜 전체를 `design/77-bundle91.md` 로(글자 그대로 — 이 S0 머리만 「실행 세션이 했다」 로, 「부록 A」·「설계 세션이 못 친 것」·「Opus 번들 해 시작 지시문」 절은 빼고 부록 자리에 「부록 A 는 `design/76-audit3.md`」 한 줄)
4. `PROGRESS.md` — 설계 76 줄의 `(이 커밋)` → 그 커밋 해시
5. `PLAN.md` 15장 — 「번들 91~」 행을 둘로:
   - **「번들 91」** — 순서 칸은 아래 「차례」 · 설계 칸 「2026-11-22(Fable, 설계 76 `design/76-audit3.md` — 가로 점검 3차 · 설계 77 `design/77-bundle91.md`. 실행 Opus high · 마무리 독립 리뷰 Fable xhigh). **점검 3차 확인 152(Critical 8) + 대기 행 다섯 — 행 73.** 사용자 결정(2026-11-22): 한 번들·한 PR · `6-76` (가) · 기준선 낮춤 허용 · **머지 위임** · 시간 컷 없음」 · 설치 칸 「표본 폴더 · node · Docker · 여유 RAM 15GB」 · 상태 「대기」
   - **「번들 92~」** — 순서 칸 「(설계 전)」 · 요지 「설계 76 4절 후보·한계 조각 · 잠재 A-33·A-37 · `5-100`·`5-31`(순수본 재주입 — 한 번에) · 반입 zip 갱신(다음 반입 날 앞) · 조건부 `5-14`·`5-15`·`6-9` · 번들 91 이 `wip/` 로 뺀 행」 · 상태 「설계 전」
6. `PLAN.md` 16장 —
   - 대기 다섯(`6-75`·`6-76`·`6-77`·`1-116`·`1-117`)의 사실 칸 끝에 ` · **처방**: 설계 77 §4`. `6-76` 은 선행 칸 「사람 결정」 → 「—」 + 사실 끝에 「사용자 (가) 2026-11-22」. `1-117` 은 선행 칸 「기준선 갱신 번들」 → 「—」
   - 표 끝에 새 행 — 아래 S0 표에서 대기 다섯을 뺀 행마다(꼴 `| <번호> | <청크> | **설계 77 §4 가 원본**. 점검 3차 <점검 키>(설계 76). **축**: <축> **강제 지점**: <강제 지점> | <선행> | 대기 |`)
7. `PROGRESS.md` 이력 한 줄(S0 설계 77 — 행 73 + `V-70a`·`R-37`·`V-70`, 묶음 아홉과 차례) + 「현재 상태」(진행중 = 번들 91 대기)
8. `bash scripts/verify.sh`(docs 레인) → `git commit`(따로) `docs: 설계 77 — 번들 91 행·번들 표`
- 확인: `git log --oneline -2` 가 두 문서 커밋 · `git status --short` 깨끗 · 8절 「첫 스텝 전에」

| # | 청크 | 점검 키 | 축(레인) | 강제 지점 | 선행 |
|---|---|---|---|---|---|
| 9-10 | ReadOnlyJdbc — 홀로 선 CR · 숫자에 붙은 낱말 · `ADD`·`DIALOG` · 첫 낱말 끝 | F-01·02·03·08 | 규칙 6(db) | `ReadOnlyJdbcTest`(새 거절 열하나 · `stillPasses`) | S0 |
| 0-120 | VCS — lazy fetch 금지 · 부분 클론 거절 · 따옴표 든 필터 이름 | C2-01·02 | 규칙 1(java) | `WorkingCopyTest.cliEnvForbidsLazyFetch`·`gitRefusesPartialClone`·`gitRefusesFilterNameWithQuote` | S0 |
| 1-126 | 메타 수집 — PG 파티션 부모 표 | F-04 | 메타(db) | `JdbcMetaSourcePostgresTest.partitionedParentIsListed` | S0 |
| 5-138 | ConsoleArgs — TS 제네릭·non-null 호출 | E-02 | 정리(corpus) | `CleanupTest`(`d1.ts`) | S0 |
| 6-75 | SqlJoins — 겹괄호 파생 표 · LATERAL · APPLY · 안 닫힌 괄호 · 파생 표 뒤 쉼표 | 대기 행 + 넓힘 | 분석(corpus) | `SqlJoinsTest.shapes`(열셋) | S0 |
| 6-78 | SqlJoins — 분기마다 같은 별칭 | C1-02 | 〃 | `SqlJoinsTest.shapes`(둘) | 6-75 |
| 6-79 | SqlJoins — 여는 말 · 따옴표 친 예약어 이름 · DUPLICATE KEY UPDATE | C1-03·04·09 | 〃 | `SqlJoinsTest.shapes`(다섯) | 6-78 |
| 6-80 | SqlTables — 분기 뒤 컬럼 목록 · GO 뒤 DCL · 조건 안 절 끝 낱말 · PL/SQL USING | C1-05·10·11·12 | 〃 | `SqlTablesTest.CASES`(여덟) | S0 |
| 6-81 | SQL 주석 지우기를 SqlTokens 한 자리로 | C1-06 | 〃 | `SqlTokensTest.stripCommentsKnowsLiterals` · `SqlTablesTest.CASES`(둘) | 6-79 · 6-80 |
| 6-82 | SqlProbe — 파일용 오류문의 자기 낱말 | C1-01 | 규칙 3(db) | `SqlProbeTest.fileMessageIgnoresCommentQuotes` | 6-81 |
| 6-83a | SqlProbe — 끝 절의 주석 · 문장 열쇠 · 변형 하나 · `#{` 시간 | C1-08·16·17·18 | 분석(db) | `SqlProbeTest`(wrap·pick·시간·열쇠) | 6-82 |
| 6-83b | DB 대조 — 못 읽은 매퍼 · iBATIS sqlMap | C1-15·19 | 〃 | `SqlProbeTest.unreadableMapperIsCounted` · `AnalyzeRoutesTest`(sqlMap) | 6-83a |
| 6-84 | MapperIndex — 방언별 조각 · selectKey | C1-07·13·14 | 분석(corpus) | `MapperIndexTest.dialectFragmentsAndSelectKey` | S0 |
| 6-76 | MapperIndex.hashText — 판 번호 토막 | 대기 행 | 〃 | `MapperIndexTest.hashHintTakesVersionSuffix` | 6-84 |
| 5-128 | RegexRule — script 감싸개 · script 몸 손 훑기 · 텍스트 블록 · 끊긴 따옴표 | D-01·19·17·10 | 검사(corpus) | `CheckRulesTest.wrappedScriptIsStillChecked`·`unclosedScriptsAreLinear`·`textBlockEndingWithBackslashes`·`ignoreMarkers` | S0 |
| 5-129 | Zones.rawLines — script 몸의 글자 태그 | D-15 | 정리(corpus) | `ZonesTest.rawLinesSkipsScriptBodies` | 5-128 |
| 5-130 | 규칙 적재 — BadRule · 모르는 키 · max 공백 · naming | D-02·12·13·14 | 검사(corpus) | `CheckRulesTest.ruleDefinitionErrorsAreBadRule` · `CheckRoutesTest`(400) | 5-128 |
| 5-131 | LocalScopes — 매개변수·패턴 변수 · 옛 switch | D-03·04 | 〃 | `TaintTest`(넷) | S0 |
| 5-132 | ResourceAst.released — 선언 범위 | D-05 | 〃 | `JavaRulesTest`(형제 블록) | 5-131 |
| 5-133 | MyBatisRules — 시작 태그 줄 · 서브쿼리 where · sql 조각 · 시간 | D-06·07·08·18 | 〃 | `MyBatisRulesTest`(다섯) | S0 |
| 5-134 | `security.sqlDollar` 게이트 | D-09 | 〃 | `CheckRulesTest`(음성 픽스처) · `pure-rules.json` | S0 |
| 5-135 | 붙여넣기 언어 추정 · 줄머리 정규식 | D-11·18 | 〃 | `CheckRunnerTest`(guessLang 여섯) | S0 |
| 5-136 | ReturnTypes — 조상 오버로드 | D-16 | 정리(corpus) | `ReturnTypesTest`(셋) | S0 |
| 5-137 | 코드 정리 — jsp·html script 주석 · 스크립트 밖 글 | E-01·07·13 | 정리(corpus) | `CleanupTest`(`b1.jsp`·`b2.jsp`·`b4.jsp`) | 5-128 · 5-129 |
| 5-139 | ValueFix — 눌린 줄 · 패턴 변수 · 가려진 상수 | E-03·09·10 | 〃 | `ValueAstTest`·`CleanupTest`(`P1`·`C5`·`P5`) | 5-136 |
| 5-140 | LogFix — 먼저 도는 초기화 | E-04·05 | 〃 | `CleanupTest`(`F4`·`C1`) | S0 |
| 5-141 | MarkupFix — tsx rel 따옴표 | E-06 | 〃 | `CleanupTest`(`b3.tsx`) | S0 |
| 5-142 | SwitchFix — 뒤 case 지역 이름 · 꼬리 주석 | E-08·14 | 〃 | `CleanupTest`(`P3`·`P4`) | S0 |
| 5-143 | PiiFix — 주석 뗀 줄 · 매퍼 XML | E-11·12 | 〃 | `CleanupTest`(`C6.sql`·`f2.jsp`·`C3.xml`) | 5-137 |
| 5-144 | 줄끝 — 통일 알림 · `\r\r\n` | E-15·17 | 〃 | `CleanupRunnerTest` · `LocalFilesTest` | S0 |
| 5-145 | EncodingFix — 남의 charset 속성 | E-16 | 〃 | `CleanupTest`(`f1.jsp`) | S0 |
| 6-77 | JpaIndex — 조상은 extends 글이 가리킬 때만 | 대기 행 | 분석(corpus) | `JpaIndexTest.ancestorsMustBeNamedEverywhere` | S0 |
| 6-89 | JPQL — JOIN 뒤 쉼표 | C2-06 | 〃 | `JpaIndexTest.commaAfterJoin` · 기준선 한 줄 | 6-77 |
| 6-85 | JspLinks — script 감싸개 · c:set 컨텍스트 이름 | C2-03·08·12 | 〃 | `JspLinksTest.wrappedScriptLinks`·`ctxSetNeedsBareContextPath` | 5-128 |
| 6-87 | JspLinks — 따옴표 짝 밀림 · 긴 줄 토큰 | C2-07·13 | 〃 | `JspLinksTest`(세 줄 · 시간) | 6-85 |
| 6-86 | JavaGraph — 공통 DAO 의 문장 id | C2-04 | 〃 | `JavaGraphTest.statementIdPassedThroughACommonDao` | S0 |
| 6-88 | 매핑 주석이 인터페이스·추상 조상에 | C2-05 | 〃 | `JavaGraphTest.mappingDeclaredOnInterfaceOrAbstractParent` | 6-86 |
| 6-90 | JavaGraph 타입 풀기 — 선언한 클래스 기준 · import 확인 | C2-09·10 | 〃 | `JavaGraphTest.fieldTypeResolvesWhereItIsDeclared`·`importedOutsideClassIsNotTheProjectOne` | 6-88 |
| 6-91 | 곁 등록부 id — 오버로드 | C2-11 | 〃 | `JavaGraphTest.overloadsKeepSeparateSideIds` | 6-90 |
| 6-92 | JavaViews — 완전 이름 ModelAndView · 빈 redirect | C2-14·15 | 〃 | `JavaGraphTest`(둘) | S0 |
| 1-117 | AlterGen — MODIFY COLUMN 조건 한 식 | 대기 행 | 생성기(db) | 기준선 한 줄(`modifyColumn(7)`) | S0 |
| 1-116 | AlterGen `[손]` 글 — 옛 기본값 없음 · 안 바뀐 못 옮기는 기본값 | 대기 행 + A1-02 | 〃 | `AlterGenTest.unportableDefaultUnchangedOrNew` | 1-117 |
| 1-118 | AlterGen — FK 대체 이름 · 스키마 인용 · 새 자동증가 컬럼 | A1-03·13·14 | 〃 | `AlterGenTest`(셋) | 1-116 |
| 1-119 | DdlGen — FK 컬럼 키 길이 · 유니크 이름 겹침 | A1-01·04 | 〃 | `DdlGenTest.fkColumnFollowsTheKeyLength`·`uniqueNamesCollideAcrossTables` | 1-118 |
| 1-120 | DdlGen 기본값 — 역슬래시 · `NULL::타입` | A1-08·18 | 〃 | `DdlGenTest.defaultsAcrossDialects` | 1-119 |
| 1-121 | DdlGen 타입 — 고정 길이 상한 · 자동증가 소수 · 빈 괄호 | A1-19·21·17 | 〃 | `DdlGenTest.typeKeys` · 기준선 한 줄 | 1-120 |
| 1-122 | DdlAlter — `IF [NOT] EXISTS` · 정규식 · 온라인 DDL 옵션 | A1-05·06·15 | 〃 | `DdlReaderTest.alterIfExistsAndOptions` | S0 |
| 1-123 | DDL 읽기 — 공백 든 따옴표 이름 · 대괄호 타입 | A1-07·11 | 〃 | `DdlReaderTest.quotedNamesWithSpaces`·`bracketTypes` | 1-122 |
| 1-124 | DDL 읽기 잔손 — CRLF GO · 표 안 인덱스 · CHECK 안 NOT NULL · 다른 스키마 | A1-09·10·22·20·16 | 〃 | `DdlReaderTest`(넷) | 1-123 |
| 1-125 | DDL 읽기 정규식 — 제곱 시간 · 스택 | A1-12 | 〃 | `DdlReaderTest.regexesAreLinear` | 1-124 |
| 1-128 | 비교·반영 DDL 짝짓기 — FK 기본 규칙 · 이름 대소문자 | F-06·07 | 〃 | `AlterGenTest.defaultFkRulesAreTheSame` · `SnapshotDiffTest.columnCaseDoesNotSplit` | 1-118 |
| 7-37 | 생성기 이름 — module 예약어 · VO 고정 필드 · `_` 컬럼 | A2-01·05·12 | 생성기(corpus) | `GenModelTest` · `DtoGeneratorTest` · 기준선 한 줄 | S0 |
| 4-46 | INSERT — 스냅샷의 대소문자 이름 · 표 여럿 경고 | A2-02·13 | 생성기(db) | `InsertGenTest.exactNamesInSnapshotMode` · `InsertRoutesTest` | S0 |
| 4-47 | 허용 값 판정 한 자리 · 리터럴 안 `::` | A2-03·06 | 〃 | `InsertGenTest.allowedValuesFromAnyCheckShape` · `ValidationTest` | 4-46 |
| 4-48 | INSERT 값 — CSV 칸 수 · 좁은 키 · FK 키 · INTERVAL · BIT · POINT | A2-04·07·08·09·10·11 | 〃 | `InsertGenTest`(일곱) · 기준선 한 줄 | 4-47 |
| 4-49 | 표 편집기 JSX | A2-14·15·16 | 화면(corpus) | `SmokeHtmlUnitTest`(`tbJsxHtml` 넷) | S0 |
| 2-52 | 양식 수식 — 다시 계산 · 위쪽 수식 · 단일 칸 배열 | B-01·02·04 | 산출물(java) | `XlsxFillerTest`(다섯) · 기준선 한 줄 | S0 |
| 2-53 | xlsx 칸 상한 | B-03·14 | 〃 | `RelationsTest` · `XlsxFillerTest` · `XlsxWriterTest` | 2-52 |
| 2-56 | 산출물 쓰기 전 점검 | B-12·13·16 | 〃 | `DeliverableServiceTest`(넷) | 2-53 |
| 2-54 | 08 코드값 SELECT | B-05·09 | 산출물(db) | `CodeAndLinkTest` · `DeliverableRoutesTest`(400) | S0 |
| 2-55 | 03 제약조건 낱말 경계 | B-08 | 산출물(java) | `DefinitionsTest`(둘) | S0 |
| 3-25 | 마스킹·이름 인용 | B-06·07·10·11·15 | 논리명(db) | `MaskingTest` · `QualitySqlTest` · `LogicalRoutesTest` | S0 |
| 1-127 | 스냅샷 저장 — 긴 버전 글 | F-05 | 메타(db) | `SnapshotStoreTest`(218자) | S0 |
| 3-26 | 사전 올리기 — 칸 길이 · 줄 번호 글 | F-09·10 | 사전(java) | `DictStoreTest`(둘) | S0 |
| 0-121 | LocalFiles — 파일 키 판정 · 결과 쓰기 400 | G-01·18 | 규칙 2·7(java) | `LocalFilesTest`(`sameOrInside` · 400) | 5-144 |
| 0-125a | 로컬 드라이브만 — 매핑된 네트워크 드라이브 · 링크를 푼 실제 경로 | G-10 | 규칙 1(java·db — pom) | `LocalFilesTest`(`driveType`·판정 표) | 0-121 |
| 0-122 | 프로필 백업 가림 | G-02·05 | 규칙 2(java) | `ProfileRoutesTest`(넷) | S0 |
| 0-123 | 프로필 — 규칙 병합 · password 따옴표 | G-03·04 | 설정(java) | `ProfileStoreTest.mergeRulesShapes`·`passwordNeedsQuotes` | 0-122 |
| 0-124 | 요청 가드 — Content-Length · 포트 80 | G-09·13 | 웹(java) | `LocalOnlyTest`(다섯) | S0 |
| 8-23 | 기동 — 명령줄 UNC · 실패 한 줄 | G-11·17 | CLI(java) | `MainTest` · `BatchCliTest` | S0 |
| 8-24 | 배치 CLI — 끝 코드 · latest · selftest · 권고 글 | G-14·15·16·12 | 〃 | `DocCliTest` · `BatchCliTest` · `SelfTestCliTest` | 8-23 |
| 0-125b | selftest 드라이브 종류 판정 항목 · 심의 문서 한 줄 | G-10 | CLI·문서(java) | `SelfTestCliTest` | 0-125a · 8-24 |
| 4-50 | 로그 SQL — 시간 · 배치 · 배열 타입 | G-06·07·08 | 글(corpus) | `LogSqlTest`(넷) | S0 |
| V-70a | 정적 분석 · corpus 골든 | — | 4장 | `verify.sh --corpus` | 코드 행 전부 |
| R-37 | 손 판정 · 처분 표 | — | 4장 | compare ㊵ 절 | V-70a |
| V-70 | `--db` | — | 4장 | `verify.sh --db` | R-37 |

차례 — `S0 → ⓪ 9-10→0-120→1-126→5-138 → ① 6-75→6-78→6-79→6-80→6-81→6-82→6-83a→6-83b→6-84→6-76 → ② 5-128→5-129→5-130→5-131→5-132→5-133→5-134→5-135→5-136 → ⑥ 5-137→5-139→5-140→5-141→5-142→5-143→5-144→5-145 → ⑤ 6-77→6-89→6-85→6-87→6-86→6-88→6-90→6-91→6-92 → ③ 1-117→1-116→1-118→1-119→1-120→1-121→1-122→1-123→1-124→1-125→1-128 → ④ 7-37→4-46→4-47→4-48→4-49 → ⑦ 2-52→2-53→2-56→2-54→2-55→3-25 → ⑧ 1-127→3-26 → ⑨ 0-121→0-125a→0-122→0-123→0-124→8-23→8-24→0-125b→4-50 → V-70a→R-37→V-70→마무리`

행 글은 아래 묶음 절에 있다(절 번호는 영역, 치는 차례는 위 줄). ⓪ 의 넷은 제 영역 절에 있다 — `9-10`·`1-126` 은 ⑧, `0-120` 은 ⑤, `5-138` 은 ⑥.

### 묶음 ① 프로그램 분석 — SQL (corpus 레인)

#### 6-75 — SqlJoins: 겹괄호 파생 표 · LATERAL · APPLY · 안 닫힌 괄호 · 파생 표 뒤 쉼표 (대기 행 + 점검 3차 넓힘)
- 대상: `core/analyze/SqlJoins.java`(`OPENERS` · `Scopes.of`·`fill`·`derived` · 새 `opens`·`queryParen`·`closedParens` · 필드 `derivedEnd`) · `SqlJoinsTest` · 골든 `analyze/sql-joins.json`
- 사실: 맥락 「보정 A」 의 `6-75`. `derived:244` 는 `(` 바로 뒤가 SELECT·WITH 일 때만 · `LATERAL` 은 STOP 이라 `fill:233` 에서 표 자리가 꺼진다 · `APPLY` 는 `OPENERS:25` 에 없다(`SqlTables.opener:571` 은 연다) · `of:191` 이 안 닫힌 `(` 를 깊이에 센다 · 파생 표 별칭 뒤 쉼표가 표 자리를 안 연다
- 변경:
  1. `OPENERS` 에 `"APPLY"` · 상수 `private static final String LATERAL = "LATERAL";`
  2. `Scopes` 필드 `private final Set<Integer> derivedEnd = new HashSet<>();`(파생 표가 끝난 자리 — 별칭, 없으면 닫는 괄호)
  3. `of` — 첫 가지를 `if (OPEN.equals(x) && (closed[i] || startsQuery(t, i + 1)))` 로(`boolean[] closed = closedParens(t);` 를 루프 앞에). 안 닫힌 `(` 는 질의 괄호일 때만 `opened` 에 쌓는다. 그 밖 줄은 그대로
     ```java
     /** 짝이 있는 ( 자리 — 안 닫힌 ( 는 깊이에 안 센다(6-75: 깨진 글·동적 SQL) */
     private static boolean[] closedParens(List<String> t) {
         boolean[] closed = new boolean[t.size()];
         Deque<Integer> open = new ArrayDeque<>();
         for (int i = 0; i < t.size(); i++) {
             if (OPEN.equals(t.get(i))) {
                 open.push(i);
             } else if (CLOSE.equals(t.get(i)) && !open.isEmpty()) {
                 closed[open.pop()] = true;
             }
         }
         return closed;
     }
     ```
  4. `fill` — `expect = …` 한 줄을 `expect = opens(t, i, expect);` 로
     ```java
     /** i 뒤가 표 자리인가 — 여는 말 · 파생 표 뒤 쉼표 · 표 자리의 LATERAL·괄호 친 표(USING ( 는 열 목록)(6-75) */
     private boolean opens(List<String> t, int i, boolean expect) {
         String x = t.get(i);
         if (OPENERS.contains(x) || ",".equals(x) && derivedEnd.contains(i - 1)) {
             return true;
         }
         return expect && (LATERAL.equals(x) || OPEN.equals(x) && !"USING".equals(t.get(i - 1)));
     }
     ```
  5. `derived` — 조건의 `startsQuery(t, i + 1)` 를 `queryParen(t, i)` 로, 끝에 끝 자리를 적는다
     ```java
     private void derived(List<String> t, int i, boolean expect) {
         if (!expect || !OPEN.equals(t.get(i)) || !queryParen(t, i)) {
             return;
         }
         int k = closing(t, i);
         int j = k + 1 < t.size() && "AS".equals(t.get(k + 1)) ? k + 2 : k + 1;
         boolean named = j < t.size() && word(t.get(j)) && !SqlTables.STOP.contains(t.get(j));
         if (named) {
             alias.get(at[j]).put(t.get(j), "");
         }
         derivedEnd.add(named ? j : k);
     }

     /** i 의 ( 가 질의 괄호인가 — 뒤가 SELECT·WITH, 또는 ( 가 겹쳤고 안쪽 질의 괄호마다 바로 뒤가 ) 나 집합 연산(((SELECT …)) · ((SELECT …) UNION (SELECT …)), 6-75) */
     private static boolean queryParen(List<String> t, int i) {
         int k = i;
         while (k + 1 < t.size() && OPEN.equals(t.get(k + 1))) {
             k++;
         }
         boolean query = startsQuery(t, k + 1);
         int p = k;
         while (query && p > i) {
             int after = closing(t, p) + 1;
             query = after < t.size() && (CLOSE.equals(t.get(after)) || SET_OPS.contains(t.get(after)));
             p--;
         }
         return query;
     }
     ```
  6. javadoc — 클래스 머리 「한계」 줄에서 「파생 표 뒤 쉼표 조인의 표는 안 읽는다」 를 지우고 「괄호 친 표 뒤 쉼표 조인의 표는 안 읽는다」 로 · `Scopes` javadoc 끝에 「안 닫힌 ( 는 질의 괄호일 때만 깊이에 센다 · 겹괄호 파생 표·LATERAL·APPLY 뒤 별칭도 가린다 · 파생 표 뒤 쉼표는 표 자리(6-75)」
- 시험(먼저): `SqlJoinsTest.shapes` 에 열쇠 열셋(주석 `// 6-75 — …`). RED 는 골든 갱신 앞에 — 「전」 과 같고 기대와 다른지(지킴이 다섯 빼고)
  | 열쇠 | SQL | 기대 | 전 |
  |---|---|---|---|
  | `derivedDoubleParen` | `SELECT * FROM T1 v JOIN T2 b ON v.ID = b.ID WHERE b.X IN (SELECT c.X FROM ((SELECT ID, X FROM T3)) v JOIN T4 c ON v.ID = c.ID)` | T1.ID—T2.ID | + T1.ID—T4.ID |
  | `derivedParenUnion` | `SELECT * FROM T1 v JOIN T2 b ON v.ID = b.ID WHERE b.X IN (SELECT c.X FROM ((SELECT ID FROM T3) UNION (SELECT ID FROM T5)) v JOIN T4 c ON v.ID = c.ID)` | T1.ID—T2.ID | + T1.ID—T4.ID |
  | `lateralAliasShadows` | `SELECT * FROM T1 v JOIN T2 b ON v.ID = b.ID WHERE EXISTS (SELECT 1 FROM T4 c JOIN LATERAL (SELECT ID FROM T3 x WHERE x.P = c.P) v ON v.ID = c.ID)` | T1.ID—T2.ID · T3.P—T4.P | + T1.ID—T4.ID |
  | `applyAliasShadows` | `SELECT * FROM T1 v JOIN T2 b ON v.ID = b.ID WHERE EXISTS (SELECT 1 FROM T4 c CROSS APPLY (SELECT ID FROM T3 x WHERE x.P = c.P) v WHERE v.ID = c.ID)` | T1.ID—T2.ID · T3.P—T4.P | + T1.ID—T4.ID |
  | `unclosedParenUnion` | `SELECT a.X FROM T1 a WHERE (a.Y = 1 UNION SELECT c.X FROM T3 c WHERE c.X = a.X` | 없음 | T1.X—T3.X |
  | `commaAfterDerived` | `SELECT * FROM (SELECT ID FROM T3) v, T1 a, T2 b WHERE a.ID = b.ID` | T1.ID—T2.ID | 없음 |
  | `commaAroundDerived` | `SELECT * FROM T1 a, (SELECT ID FROM T3) v, T2 b WHERE a.ID = b.ID AND v.ID = a.ID` | T1.ID—T2.ID | 없음 |
  | `commaAfterLateral` | `SELECT * FROM T1 a, LATERAL (SELECT ID FROM T3 x WHERE x.P = a.P) v, T2 b WHERE a.ID = b.ID` | T1.ID—T2.ID · T1.P—T3.P | T1.P—T3.P |
  | `nestedParenUnion`(지킴이) | `SELECT * FROM T1 a WHERE a.ID IN (((SELECT b.ID FROM T2 b WHERE b.X = a.X)) UNION ((SELECT c.ID FROM T3 c WHERE c.X = a.X)))` | T1.X—T2.X · T1.X—T3.X | 같음 |
  | `mixedParens`(지킴이) | `SELECT * FROM ((T1 a JOIN T2 b ON a.ID = b.ID) JOIN (SELECT ID FROM T3) v ON v.ID = a.ID) WHERE a.X IN ((SELECT c.X FROM T4 c) UNION (SELECT d.X FROM T5 d WHERE d.Y = b.Y))` | T1.ID—T2.ID · T2.Y—T5.Y | 같음 |
  | `parenTableParenUnion`(지킴이) | `SELECT * FROM (T1 a JOIN T2 b ON a.ID = b.ID) JOIN ((SELECT ID FROM T3) UNION (SELECT ID FROM T4)) u ON u.ID = a.ID` | T1.ID—T2.ID | 같음 |
  | `scalarAlias`(지킴이) | `SELECT a.ID, (SELECT MAX(c.X) FROM T3 c WHERE c.ID = a.ID) b FROM T1 a JOIN T2 b ON a.ID = b.ID` | T1.ID—T2.ID · T1.ID—T3.ID | 같음 |
  | `commaDerivedShadows`(지킴이) | `SELECT * FROM T1 v JOIN T2 b ON v.ID = b.ID WHERE b.X IN (SELECT c.X FROM T4 c, (SELECT ID FROM T3) v WHERE v.ID = c.ID)` | T1.ID—T2.ID | 같음 |
- 의도된 골든 변화: `sql-joins.json` 새 열쇠 열셋만(옛 서른다섯 그대로) · `self-check.txt` 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlJoinsTest,MapperIndexTest,SqlTablesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력·javadoc): 파생 표 별칭의 관계 · 괄호 친 표 뒤 쉼표 조인의 표 · `FROM <when> 표` 꼴 · 표 함수 `APPLY fn(…) f` 의 별칭

#### 6-78 — SqlJoins: 분기마다 같은 별칭 (C1-02)
- 대상: `core/analyze/SqlJoins.java`(`Scopes.alias` 형식 · `table` · `entry` · `derived` · 새 `Def`·`nearest`) · `SqlJoinsTest` · 골든
- 사실: `<when>`·`<otherwise>` 가 같은 별칭을 다른 표에 쓰면 `entry:277·280` 의 `m.put` 이 덮어써 앞 분기 등식이 뒤 분기 표로 풀린다 — 탐침 `B SELECT a.ID FROM T_A a JOIN T_B b ON a.BID = b.ID B SELECT a.ID FROM T_X a JOIN T_C c ON a.CID = c.ID` → `T_B.ID—T_X.BID`(지어냄) · `T_C.ID—T_X.CID`. 설계 75 §3-2 「분기 표시는 범위를 안 가른다」 는 그대로 둔다(분기 안 `b` 가 문장 범위에 남아야 한다)
- 변경: 범위 안 별칭 하나에 정의를 **자리와 함께 여럿** 적고, 찾을 때 자리 앞의 가장 가까운 정의를 쓴다
  - `private record Def(int at, String table) { }`(Scopes 안) · `alias` 를 `List<Map<String, List<Def>>>` 로
  - `entry` 의 `m.put(이름, real)` 둘 → `m.computeIfAbsent(이름, n -> new ArrayList<>()).add(new Def(자리, real))`(표 이름은 자리 `k`, 별칭은 자리 `j`) · `derived` 의 `put(t.get(j), "")` 도 같은 꼴(자리 `j`)
  - `table(i, name)` — `List<Def> defs = alias.get(s).get(name); if (defs != null) { String v = nearest(defs, i); return v.isEmpty() ? null : v; }`
    ```java
    /** 같은 범위에 같은 별칭이 여럿이면(<when>·<otherwise> 분기) 자리 앞의 가장 가까운 정의, 앞에 없으면 가장 앞 정의(6-78) */
    private static String nearest(List<Def> defs, int i) {
        Def before = null;
        Def first = defs.get(0);
        for (Def d : defs) {
            if (d.at() <= i && (before == null || d.at() > before.at())) {
                before = d;
            }
            first = d.at() < first.at() ? d : first;
        }
        return (before == null ? first : before).table();
    }
    ```
- 시험(먼저): `shapes` 에(주석 `// 6-78 — …`, `B` 는 분기 표시)
  | 열쇠 | SQL | 기대 | 전 |
  |---|---|---|---|
  | `branchSameAlias` | `B + " SELECT a.ID FROM T_A a JOIN T_B b ON a.BID = b.ID " + B + " SELECT a.ID FROM T_X a JOIN T_C c ON a.CID = c.ID"` | T_A.BID—T_B.ID · T_C.ID—T_X.CID | T_B.ID—T_X.BID · T_C.ID—T_X.CID |
  | `aliasUsedBeforeFrom`(지킴이) | `SELECT (SELECT MAX(b.X) FROM T2 b WHERE b.ID = a.ID) FROM T1 a` | T1.ID—T2.ID | 같음 |
- 의도된 골든 변화: 새 열쇠 둘만 · 옛 열쇠 0 · `self-check.txt` 0
- 검증: 6-75 와 같다. 의존: 6-75(같은 파일)
- 한계: 분기 밖(뒤)에서 쓰는 별칭은 마지막 분기의 표(지금과 같다)

#### 6-79 — SqlJoins: 여는 말 · 따옴표 친 예약어 이름 · ON DUPLICATE KEY UPDATE (C1-03 · C1-04 · C1-09)
- 대상: `core/analyze/SqlJoins.java`(`Scopes.opens`·`fill`·`entry` · `statement` · `outsideSet` · `word`) · `core/analyze/SqlTables.java`(`quotedStop`·`unq` 를 package-private 로 — 몸은 그대로) · `SqlJoinsTest` · 골든
- 사실: `FOR UPDATE OF a`·`UPDATE IGNORE T1`·`UPDATE LOW_PRIORITY T1` — UPDATE 뒤 낱말을 표로 적어 진짜 별칭을 덮는다(`OF.ID—T2.ID`·`IGNORE.ID—T2.ID`) · `SqlTokens.tokens:64` 가 따옴표 친 예약어 이름을 `"ORDER"` 꼴로 내는데 `SqlJoins.word:287` 은 `"` 로 시작하면 낱말이 아니다(그 표 관계 0) · `ON DUPLICATE KEY UPDATE T1.CNT = b.CNT` 는 SET 낱말이 없어 대입이 조인으로 센다
- 변경:
  1. `opens` 첫 조건을 `OPENERS.contains(x) && !lockUpdate(t, i) || …` 로 — `private static boolean lockUpdate(List<String> t, int i)` = `"UPDATE".equals(t.get(i)) && i > 0 && ("FOR".equals(t.get(i - 1)) || "KEY".equals(t.get(i - 1)))`(`FOR UPDATE`·`FOR NO KEY UPDATE`·`ON DUPLICATE KEY UPDATE`)
  2. 수식어 — `private static final Set<String> PASS = Set.of("LATERAL", "ONLY", "IGNORE", "LOW_PRIORITY");`(6-75 의 상수 `LATERAL` 은 이 집합으로 바꾼다). `opens` 의 `LATERAL.equals(x)` → `PASS.contains(x)` · `fill` 의 `table` 조건에 `&& !PASS.contains(x)`
  3. `word(x)` 끝에 `|| SqlTables.quotedStop(x)` · `entry` 가 적는 이름은 `SqlTables.unq(t.get(k))`(별칭 `j` 는 따옴표 꼴이 아니다) · `statement` 의 `sc.table(i, t.get(i))`·`sc.table(eq + 1, t.get(eq + 1))` 의 이름 인자도 `SqlTables.unq(…)`
  4. `outsideSet` — 켜는 조건 `"SET".equals(x) && on < 0` 을 `(SET.equals(x) || dupUpdate(t, i)) && on < 0` 으로 — `dupUpdate` = `"UPDATE".equals(t.get(i)) && i > 1 && "KEY".equals(t.get(i - 1)) && "DUPLICATE".equals(t.get(i - 2))`
- 시험(먼저): `shapes` 에(주석 `// 6-79 — …`)
  | 열쇠 | SQL | 기대 | 전 |
  |---|---|---|---|
  | `forUpdateOf` | `SELECT a.X FROM T1 a JOIN T2 b ON a.ID = b.ID FOR UPDATE OF a.X` | T1.ID—T2.ID | OF.ID—T2.ID |
  | `updateIgnore` | `UPDATE IGNORE T1 JOIN T2 ON T1.ID = T2.ID SET T1.X = 1` | T1.ID—T2.ID | IGNORE.ID—T2.ID |
  | `fromOnly` | `SELECT * FROM ONLY T1 a JOIN T2 b ON a.ID = b.ID` | T1.ID—T2.ID | 없음 |
  | `quotedKeywordTable` | ``SELECT * FROM `order` o JOIN order_item i ON o.ID = i.ORDER_ID`` | ORDER.ID—ORDER_ITEM.ORDER_ID | 없음 |
  | `duplicateKeyUpdate` | `INSERT INTO T1 (ID, CNT) SELECT b.ID, b.CNT FROM T2 b ON DUPLICATE KEY UPDATE T1.CNT = b.CNT` | 없음 | T1.CNT—T2.CNT |
- 의도된 골든 변화: 새 열쇠 다섯만 · 옛 열쇠 0 · `self-check.txt` 0(`SqlTables` 는 가시성만)
- 검증: 6-75 와 같다. 의존: 6-78

#### 6-80 — SqlTables: 분기 뒤 컬럼 목록 · GO 뒤 권한 문장 · 조인 조건 안 절 끝 낱말 · PL/SQL USING (C1-05 · C1-10 · C1-11 · C1-12)
- 대상: `core/analyze/SqlTables.java`(`alias`·`table`·`withoutDcl`·`listAfter`·`opener` · `Scan` 필드 하나) · `SqlTablesTest` · 골든 `analyze/sql-tables.json`
- 사실(탐침 — 지금 출력): `INSERT INTO T1 B (COL_A, COL_B) B (COL_A, COL_C) VALUES (?, ?)`(tag insert) → `COL_A:R COL_B:R T1:C` · `GRANT SELECT ON T TO U1⏎GO⏎REVOKE SELECT ON T FROM U2⏎GO⏎SELECT A FROM C` → `C:R U2:R` · `SELECT * FROM A JOIN B ON A.MODEL = B.MODEL, C WHERE 1=1` → `A B`(C 없음) · `BEGIN EXECUTE IMMEDIATE q USING V1, V2; END;` → `V1:R V2:R`
- 변경:
  1. `Scan` 에 `boolean byBranch` — `alias` 의 `ALIAS_OK` 가지가 분기 표시로 `State.TABLE` 을 다시 열 때 참, 쉼표로 열 때와 `table` 이 표 하나를 읽은 뒤·`open` 이 여는 말로 열 때 거짓. `table` 의 `"(".equals(x) && parenTable(t, i)` 에 `&& !sc.byBranch` 를 더하고, `byBranch` 인 `(` 는 `sc.st = State.CLOSED` 로 닫고 `i` 를 돌려준다(분기 안 컬럼 목록 — 괄호 친 표가 아니다)
  2. `withoutDcl` — `start = ";".equals(x);` → `start = ";".equals(x) || "GO".equals(x);`
  3. `listAfter` 의 둘째 줄 — 절 끝 낱말이라도 ① 바로 앞 토큰이 `.` 이면(열 이름) ② `WHEN` 인데 뒤가 `MATCHED`·`NOT` 이 아니면(CASE WHEN) 목록을 안 닫는다
  4. `opener` — `case "FROM", "JOIN", "APPLY"` 와 `case "USING"` 을 가른다: `USING` 은 `verb` 가 `MERGE`·`DELETE` 일 때만 `EnumSet.of(Crud.R)`, 아니면 `null`
- 시험(먼저): `SqlTablesTest.CASES` 에 넷(위 SQL 그대로, 열쇠 `branchColumnList`·`dclAfterGo`·`listEndInCondition`·`plsqlUsing`) → 기대 `T1:C` / `C:R` / `A:R B:R C:R` / 표 없음. `caseWhenInCondition`(`SELECT * FROM A a JOIN B b ON a.X = CASE WHEN b.T = 1 THEN b.Y ELSE b.Z END, C c WHERE 1=1`) → `A:R B:R C:R`(전 `A B`). 지킴이 셋 — `joinUsingThenComma`(`SELECT * FROM A JOIN B USING (ID), C` → `A:R B:R C:R` — 지금과 같다) · `mergeUsingStill`(`MERGE INTO T1 t USING T2 s ON (t.ID = s.ID) WHEN MATCHED THEN UPDATE SET t.X = s.X` → `T1:CU T2:R` — 지금과 같다) · `deleteUsingStill`(`DELETE FROM T1 USING T2 WHERE T1.ID = T2.ID` → `T1:D T2:R` — 지금과 같다)
- 의도된 골든 변화: `sql-tables.json` 에 사례 여덟이 더해지는 것만 · `self-check.txt` — `SqlTables` 줄이 있으면 값이 안 오른다(오르면 공통 사다리)
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlTablesTest,SqlJoinsTest,MapperIndexTest,SqlLintTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계: 한정자 없는 열 이름(`ON MODEL = …`)은 여전히 목록을 닫는다 · `JOIN … USING (열)` 은 `(` 라 원래 표가 아니다

#### 6-81 — SQL 주석 지우기를 SqlTokens 한 자리로 (C1-06)
- 대상: `core/text/SqlTokens.java`(새 `public static String stripComments(String)`) · `core/analyze/SqlTables.java:222` · `core/analyze/SqlJoins.java:43`(호출 한 줄씩) · `SqlTokensTest` · `SqlTablesTest`
- 사실: 분석은 `RegexRule.stripComments(sql, "sql")`(`cLike(false)`)로 주석을 먼저 지우는데 `cLike` 는 `\'` 탈출(6-47)과 `#` 줄 주석(6-58)을 모른다 — 탐침 `SELECT * FROM A WHERE M = 'it\'s' AND X = '--' AND Y IN (SELECT ID FROM B)` → `A` 만 · `SELECT a FROM T1 # note /* old⏎WHERE b IN (SELECT id FROM T2)` → `T1` 만
- 변경: `SqlTokens.stripComments(s)` — 한 번 훑어 `--` 줄 주석·`/* */` 블록 주석·`#` 줄 주석(`hashComment`)을 같은 길이 빈칸으로(줄바꿈은 남긴다). 홑따옴표 글(`literalEnd` — `''`·`\'` 규칙 그대로)·`$$`/`q'[]'`(`quotedLiteral`)·따옴표 식별자(`"`·`` ` ``·`[…]` — `quoted` 와 같은 끝 규칙) 안은 그대로 옮긴다. 안 닫힌 블록 주석은 끝까지 빈칸. `SqlTables.extract`·`SqlJoins.extract` 의 `RegexRule.stripComments(…, "sql")` 를 `SqlTokens.stripComments(…)` 로 바꾼다(`SqlJoins` 의 `RegexRule` import 는 지운다). `RegexRule`·`SqlProbe`·`SqlLint` 는 이 행에서 안 고친다
- 시험(먼저): `SqlTokensTest.stripCommentsKnowsLiterals` — `stripComments("A -- x\nB")` = `"A     \nB"` · `stripComments("A /* x\ny */ B")` 는 줄바꿈만 남는다 · `stripComments("M = 'it\\'s' AND X = '--' AND Y")` 는 입력 그대로 · `stripComments("T1 # note /* old\nWHERE b")` = `"T1 " + 빈칸 14 + "\nWHERE b"` · `stripComments("a = '--' -- c")` 는 리터럴은 남고 꼬리만 빈칸. `SqlTablesTest.CASES` 에 둘(위 탐침 SQL, 열쇠 `escapedQuoteThenDashes`·`hashCommentWithBlockOpen`) → `A:R B:R` / `T1:R T2:R`. RED — 지금 `A` 만 · `T1` 만
- 의도된 골든 변화: `sql-tables.json` 사례 둘 더함 · `sql-joins.json` 0 · `mapper-index.json` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlTokensTest,SqlTablesTest,SqlJoinsTest,MapperIndexTest,SqlLintTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-79 · 6-80(같은 파일들)

#### 6-82 — SqlProbe: 파일용 오류문의 「자기 낱말」 을 SqlTokens 로 (C1-01 — Critical, 절대 규칙 3)
- 대상: `core/analyze/SqlProbe.java`(`names` · `unquoted` 삭제) · `SqlProbeTest`
- 사실: `names:394` 가 쓰는 `unquoted:375-391` 은 SQL 주석과 `\'` 를 모른다 — `SELECT A FROM T -- '24.03 fix⏎WHERE A = 'ZZLIT9'` 에서 주석의 `'` 가 따옴표를 열고 리터럴의 여는 `'` 가 닫아 `ZZLIT9` 가 리터럴 밖 낱말이 된다 → `forFile` 이 오류문의 `"ZZLIT9"` 를 남긴다 → DB 대조 xlsx 「DB 메시지」(`AnalyzeRoutes:433`)에 매퍼 SQL 의 리터럴 값
- 변경: `names(sql)` = `SqlTokens.scan(SqlTokens.stripComments(sql))` 의 토큰 가운데 ① 글자가 `'`(리터럴)·`?`(바인드)인 것 ② 따옴표 식별자인데 원문 그 자리 글자가 `"` 인 것(MySQL 큰따옴표 문자열 — 6-41: 방언을 안 가르고 값으로 본다) ③ 숫자로 시작하는 것(6-41)을 뺀 나머지를 `NOT_NAME` 으로 다시 쪼갠 낱말(대문자). `unquoted` 는 지운다(다른 쓰는 곳이 있으면 같은 처분으로 바꾼다 — `grep -n "unquoted(" SqlProbe.java`)
- 시험(먼저): `SqlProbeTest.fileMessageIgnoresCommentQuotes` —
  | SQL | 오류문 | 기대 `forFile` |
  |---|---|---|
  | `SELECT A FROM T -- '24.03 fix\nWHERE A = 'ZZLIT9'` | `Data conversion error converting "ZZLIT9"; SQL statement:` | `Data conversion error converting …` |
  | `SELECT A /* T's key */ FROM T WHERE A = 'ZZLIT9'` | 같은 글 | 같은 기대 |
  | `SELECT A FROM T WHERE M = 'it\'s' AND A = 'ZZLIT9'` | `Truncated incorrect DOUBLE value: 'ZZLIT9'` | `Truncated incorrect DOUBLE value: …` |
  | `SELECT A FROM T WHERE A = 1`(지킴이) | `Column "A" not found` | `Column "A" not found`(자기 낱말은 남는다) |
  RED — 위 셋이 지금 `ZZLIT9` 를 남긴다. 기존 `forFile`·`namesScanQuotesOnce` 단언은 그대로 초록이어야 한다
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlProbeTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-81(`SqlTokens.stripComments`)
- 사다리: 기존 단언이 빨개지면 — 그 사례가 리터럴 값을 남기는 쪽이면 단언이 옛 결함을 못 박은 것(이력에 적고 기대를 `…` 로), 자기 낱말을 자르는 쪽이면 고침이 넓다(7절)

#### 6-83a — SqlProbe: 끝 절 판정의 주석 · 문장 열쇠 · 변형 하나 · `#{` 제곱 시간 (C1-08 · C1-16 · C1-17 · C1-18)
- 대상: `core/analyze/SqlProbe.java`(`prepared`·`wrap`·`run`·`pick` · `HASH` 삭제) · `SqlProbeTest`
- 사실: `prepared:215`·`wrap:196` 의 `tail(sql, …)` 이 주석을 안 가려 `… WHERE X = 1⏎-- ORDER BY A`(mssql)에 `OFFSET 0 ROWS` 가 붙고 `SELECT A FROM T LIMIT 10 -- (paging)`(mysql)에 `LIMIT 0` 이 한 번 더 붙는다 · `run:83` 의 열쇠 `r.ns() + "." + r.id()` 가 `MapperIndex.key`(namespace 없으면 id 만, 6-57)와 달라 `.nons` · `pick:142` 는 변형이 하나면 `databaseId` 를 안 본다(oracle 문장을 mysql 접속에 대조) · `HASH`(`#\{[^}]*\}`)는 안 닫힌 `#{` 10만 번에 18초
- 변경:
  1. `prepared`·`wrap` — 끝 절 판정(`TAIL_ORDER_BY`·`OFFSET_TOP`·`TAIL_LIMIT`)은 `SqlTokens.stripComments(sql)`(길이 같음)에 한다. 붙이는 대상은 원문 `sql` 그대로
  2. `run` — 열쇠 `MapperIndex.key(r.ns(), r.id())`
  3. `pick` — `rs.size() == 1` 가지: 그 하나의 `databaseId` 가 null 이거나 방언 낱말 목록(`words`)이 비었거나(h2·모름) `databaseId` 소문자가 낱말 하나를 품으면 그것, 아니면 null(→ `SKIP_NO_DIALECT_FILE`). `words` 계산을 그 가지 앞으로 올린다
  4. `prepared` 의 `#{…}` 바꾸기 — 정규식 대신 `indexOf("#{")`·`indexOf('}')` 손 훑기. 닫는 `}` 가 없으면 거기서 끝낸다(남은 글 그대로). `HASH` 상수는 지운다
- 시험(먼저): `SqlProbeTest` — `wrap("SELECT A FROM T WHERE X = 1\n-- ORDER BY A", "mssql", b)` 에 `OFFSET` 이 없다 · `wrap("SELECT A FROM T LIMIT 10 -- (paging)", "mysql", b)` 는 `SELECT 1 FROM (` 로 시작한다(LIMIT 0 을 안 붙인다) · `pick(List.of(oracle 변형 하나), "mysql")` = null · `pick(List.of(databaseId 없는 하나), "mysql")` = 그것 · `pick(List.of(oracle 변형 하나), "h2")` = 그것 · `wrap("SELECT " + "#{".repeat(100_000), "h2", b)` 가 `assertTimeout(2초)` 안 · `wrap("SELECT #{a}, #{b} FROM T", "h2", b)` 의 `b[0] == 2`. namespace 없는 `<mapper>` 의 `run`(H2 메모리) 행 `nsId` 가 `nons`
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlProbeTest,SelfCheckTest'` → `bash scripts/verify.sh`. 레인 db(SqlProbe) — `V-70` 이 컨테이너로 잰다
- 의존: 6-82

#### 6-83b — DB 대조: 못 읽은 매퍼 파일 · iBATIS sqlMap (C1-15 · C1-19)
- 대상: `core/analyze/SqlProbe.java`(`run` · 상수 `SKIP_PARSE` · `STATUS_ORDER`) · `web/AnalyzeRoutes.java:415` · `resources/tools/program_analysis_ext.js`(`SKIP_WORDS` 한 줄) · `SqlProbeTest`
- 사실: `run:79` 가 `Parsed.unresolved()`(못 읽은 매퍼)를 버려 파일 하나가 통째로 빠져도 「통과 1 · 오류 0」 · 대조 라우트는 `<mapper` 든 파일만 실어 iBATIS sqlMap 프로젝트는 「0 문장」(6-57 과 어긋남)
- 변경: `SKIP_PARSE = "SKIP_PARSE"` · `STATUS_ORDER` 끝에 더한다 · `run` 이 문장 루프 뒤(건수 세기 앞)에 `unreadable(p, out);` 한 줄을 부른다 — `private static void unreadable(MapperIndex.Parsed p, List<Outcome> out)` 가 `p.unresolved()` 의 `parse` 항목마다 `new Outcome("(파일)", u.file(), u.line(), SKIP_PARSE, "", "")` 를 더한다(`skippedBy` 가 센다). **`run(4)` 는 기준선 17 — 분기를 더하지 않는다** · `AnalyzeRoutes:415` 조건을 `t.text().contains("<mapper") || t.text().contains("<sqlMap")` 로 · `program_analysis_ext.js` `SKIP_WORDS` 에 `SKIP_PARSE: '매퍼 못 읽음'`
- 시험(먼저): `SqlProbeTest.unreadableMapperIsCounted` — 읽히는 매퍼 하나 + 맨 `<` 로 SAX 오류인 매퍼 하나 → `report.skippedBy()` 에 `SKIP_PARSE=1` · `total == 2`. `SqlProbeTest` 에 sqlMap `Source` 하나로 `run` → `PASS` 한 행(지킴이 — 지금도 통과). 라우트 — `AnalyzeRoutesTest`(있는 probe 시험 꼴로) sqlMap 파일만 든 폴더의 대조가 `total >= 1`
- 의도된 골든 변화: 없음. xlsx 「DB 대조」 시트에 상태 낱말 하나가 는다(골든이 그 시트를 보면 사례를 더한다)
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlProbeTest,AnalyzeRoutesTest,ToolsFolderTest,SmokeHtmlUnitTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-83a

#### 6-84 — MapperIndex: 방언별 조각 · selectKey 의 iBATIS 꼴 · selectKey 안 include (C1-07 · C1-13 · C1-14)
- 대상: `core/analyze/MapperIndex.java`(`parse` 의 조각 등록 · `fragKey` · `Handler.startElement`·`endElement`) · `MapperIndexTest`
- 사실: 조각 열쇠(`parse:165`)에 `databaseId` 가 없어 같은 파일의 `<sql id="src" databaseId="oracle">`·`databaseId="mysql"` 이 마지막 것으로 덮인다 · `endElement:442` 의 `keys.add(key.toString())` 은 `sqlText()` 를 안 지나 iBATIS `<selectKey>` 의 `$tableName$` 이 표 `$TABLENAME$` 로 · `startElement:392` 가 `keyDepth > 0` 이면 include 가지 앞에서 돌아가 `<selectKey>` 안 `<include>` 의 표가 빠진다
- 변경:
  1. 조각 — `databaseId` 가 있는 조각은 열쇠 끝에 `"@" + databaseId` 를 붙인 자리에도(파일 열쇠·전역 열쇠 둘 다, `fragDynamic` 도) 적는다. 지금 열쇠(`@` 없는 것)는 **처음 본 것**이 남게 `putIfAbsent` 로(파일 열쇠도 — 지금은 `put` 이라 마지막). `fragKey(r, refid, fragments)` 는 `r.databaseId` 가 있으면 `@databaseId` 붙은 열쇠(파일 → 전역 차례)를 먼저 찾고 없으면 지금 차례
  2. `endElement` — `keys.add(ibatis ? <key 글에 sqlText 와 같은 두 치환> : key.toString())`. 치환을 `private String ibatisText(CharSequence s)` 로 빼 `sqlText()` 와 같이 쓴다
  3. `startElement` 는 기준선 `startElement(4)` 18 — **분기를 더하지 않는다**: `keyDepth > 0` 가지의 `key.append('\n');` 한 줄을 `key.append(keyChild(qName, atts));` 로 — `private static String keyChild(String qName, Attributes atts)` 가 `include` 이고 `refid` 가 있으면 `INC_OPEN + refid + INC_CLOSE`, 아니면 `"\n"`
- 시험(먼저): `MapperIndexTest.dialectFragmentsAndSelectKey`(인라인 `Source` — 픽스처 폴더에 안 더한다) —
  - 조각: 보고서 C1-07 입력(조각 둘 `T_ORA`·`T_MY`, 문장 `q` 둘) → `N.q` 표 `{T_MY=R, T_ORA=R}`(전 `{T_MY=R}`)
  - iBATIS: `<sqlMap namespace="B"><insert id="ins"><selectKey keyProperty="id" resultClass="int">SELECT MAX(ID) FROM $tableName$</selectKey>INSERT INTO $tableName$ (ID) VALUES (#id#)</insert></sqlMap>` → 표 없음 + 미해결 `table`(전 `{$TABLENAME$=R}`)
  - include: `<sql id="t">T_SEQ</sql>` + `<insert id="i"><selectKey …>SELECT MAX(ID) FROM <include refid="t"/></selectKey>INSERT INTO T1 (ID) VALUES (#{id})</insert>` → `{T1=C, T_SEQ=R}`(전 `{T1=C}`)
- 의도된 골든 변화: `mapper-index.json` 0 기대 · `self-check.txt` 0(`startElement(4)` 18 · `Statement(8)` 그대로)
- 검증: `bash scripts/mvn.sh -q test -Dtest='MapperIndexTest,SqlProbeTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계: `databaseId` 없는 문장이 방언별 조각을 끼우면 처음 본 조각(지금은 마지막)

#### 6-76 — MapperIndex.hashText: 판 번호 붙은 토막 (대기 행 · 사용자 (가))
- 대상: `core/analyze/MapperIndex.java`(`hashText` · 상수 하나) · `MapperIndexTest`
- 사실: 맥락 「보정 A」 의 `6-76`
- 변경: `private static final java.util.regex.Pattern VERSION_TAIL = java.util.regex.Pattern.compile("\\d+[a-z]?$");` · `hashText` 의 `other` 둘째 항을 `java.util.Arrays.stream(NAME_SEP.split(base)).map(x -> VERSION_TAIL.matcher(x).replaceFirst("")).anyMatch(NOT_MYSQL_FILE::contains)` 로. `mysql` 쪽·`databaseId` 쪽은 그대로
- 시험(먼저): `MapperIndexTest.hashHintTakesVersionSuffix` — `q = "SELECT a # b FROM T90_A"`: `T90_SQL_oracle11g.xml` · `sql-map-tibero6.xml` · `T90.postgres14.xml` · `T90_mssql2019.xml` → `{T90_A=R}`(전 `{}`) · `T90_oracle2.backup.xml` → `{T90_A=R}` · `T90_db2.xml` → `{}`(지킴이 — `db2` 는 파일 이름에서 안 본다) · `T90_sqlserver2008r2.xml` → `{}`(지킴이 — 숫자 + 글자 하나까지만)
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='MapperIndexTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-84(같은 파일)
- 한계: `Sync_Oracle_Mapper.xml` 은 그대로 연산자(사용자 (가) 글자 안) · `r2` 꼴 판 번호

### 묶음 ② 코드 검사 — 판정 (corpus 레인 · 「순수본 밀린 목록」 대상)

#### 5-128 — RegexRule: `<script>` 몸의 `<!--` 감싸개 · script 몸 손 훑기 · 텍스트 블록 닫기 · 줄 끝에서 끊긴 따옴표 (D-01 · D-19 · D-17 · D-10)
- 대상: `core/check/RegexRule.java`(`skipped` · `scriptComments` · `cLike` · `textBlock` · 새 `scriptBodies`·`scriptWrappers` · `SCRIPT_BODY` 삭제) · `CheckRulesTest`
- 사실:
  - D-01(Important) — jsp·html 꼴은 `stripComments:154-155` 가 `<!-- … -->` 를 먼저 비운다. `<script>` 몸을 `<!--` … `//-->` 로 감싼 옛 관용 꼴이면 스크립트 몸이 통째로 비어 `skipComments` 규칙(`common.alert`·`a11y.nonStandardDom`·`security.domXss`·`security.jsEval` …)이 0 이다. 점검자 실측 — egov JSP 743 가운데 181 파일, 그 파일들의 `common.alert` 16 → 169
  - D-19 — `SCRIPT_BODY:17` 의 `(.*?)(</script…)` 는 닫는 태그 없는 `<script>` 가 되풀이되면 제곱 시간(8천 번 2.7초)
  - D-17 — `textBlock:215` 는 닫는 `"""` 앞 글자가 `\` 면 안 닫는다 — 내용이 `\\` 로 끝나면 파일 끝까지 텍스트 블록
  - D-10 — `cLike` 가 tsx·jsx 의 JSX 글 아포스트로피(`Don't`)를 문자열 여는 따옴표로 읽어 그 줄 끝 `// tb-ignore …` 가 주석이 아니게 된다(5-121 뒤 표식이 안 먹는다)
- 변경:
  1. `static List<int[]> scriptBodies(String text)` — `{몸 시작, 몸 끝}` 목록. `text.indexOf('<', from)` 로 훑어 `text.regionMatches(true, i, "<script", 0, 7)` 이고 그 뒤 글자가 낱말 글자가 아니면 여는 태그 — 그 태그의 `>` 다음이 몸 시작, 거기서부터 `regionMatches(true, k, "</script", 0, 8)` 인 첫 자리가 몸 끝. 여는 태그의 `>` 나 닫는 태그가 없으면 **거기서 훑기를 끝낸다**(뒤에 더 없다 — 한 번 훑기)
  2. `scriptComments` — 정규식 대신 `scriptBodies` 로 같은 일을 한다(몸마다 `cLike(몸, true)`). `SCRIPT_BODY` 는 지운다
  3. `public static String scriptWrappers(String text)`(`JspLinks` 도 쓴다 — 6-85) — `scriptBodies` 의 몸 안 `<!--` 를 같은 길이 `"//  "` 로 바꾼 글(없으면 원문 그대로). JS 에서 `<!--` 는 한 줄 주석의 시작이다. `skipped` 는 jsp·jspf·tag·html·htm·xhtml 꼴에서 `stripComments(scriptWrappers(s.text()), lang)` 로 시작한다(그 밖 언어는 지금 그대로). `stripComments` 자체는 안 바꾼다(분석·Zones·Ignores 가 쓴다)
  4. `textBlock` — 닫는 `"""` 자리 `k` 앞에 이어진 `\` 수가 **홀수**일 때만 안 닫는다(짝수면 닫는다)
  5. `cLike` — `slashes` 인 꼴에서 `'`·`"` 따옴표가 **줄바꿈으로 끊기면**(안 닫힌 문자열 — 자바·JS 에 없는 꼴: JSX 글·정규식 리터럴) 그 줄에서 따옴표 뒤 첫 `//` 부터 줄 끝까지를 빈칸으로 바꾼다. **`cLike(2)` 는 기준선 25 — 분기를 더하지 않는다**: 여는 가지에 `quoteAt = sb.length();` 한 줄, 닫는 가지(`if (c == quote || c == '\n' && quote != '`')`) 몸에 `lineCut(sb, c, quoteAt, slashes);` 한 줄만. `private static void lineCut(StringBuilder sb, char c, int quoteAt, boolean slashes)` 가 `c == '\n' && slashes` 일 때 `sb.indexOf("//", quoteAt + 1)` 부터 끝 줄바꿈 앞까지를 빈칸으로
- 시험(먼저): `CheckRulesTest` 에
  - `wrappedScriptIsStillChecked` — 보고서 D-01 의 JSP(3행 `<!--` · 9행 `//-->`)와 그 두 줄을 뺀 JSP 를 각각 `CheckRunner.runText(…, "jsp", rules)` → `common.alert`·`common.consoleLog`·`common.debugger` 의 `(규칙, 줄)` 목록이 감싼 쪽 = 뺀 쪽 + 1줄(`5 common.alert`·`6 common.consoleLog`·`7 common.debugger`). html 꼴도 한 번. RED — 감싼 쪽이 지금 빈 목록
  - 지킴이 — `<!-- <script>alert(1)</script> -->`(주석 처리한 스크립트)는 지적 0 · 기존 `commentedScriptLinesAreNotFlagged` 초록
  - `unclosedScriptsAreLinear` — `"<script>\n".repeat(20_000)` 의 `RegexRule.scriptComments` 가 `assertTimeout(2초)` 안
  - `textBlockEndingWithBackslashes` — 보고서 D-17 의 자바 → `RegexRule.stripComments(java, "java")` 의 5행(주석 줄)이 빈칸이고 줄 수가 같다(파싱 여부와 무관한 단언)
  - `ignoreMarkers` 에 한 줄 — tsx `const a = <p onClick={() => alert(1)}>Don't</p>; // tb-ignore common.alert` → `common.alert` 0(전 1)
- 의도된 골든 변화: `golden/check/*`(픽스처) 0 기대 — 바뀌면 그 픽스처가 감싸개 꼴인지 보고 이력에. corpus 는 `V-70a`
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,CheckRunnerTest,PureCodeCheckTest,JspLinksTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 순수본 밀린 목록: ` · 번들 91 5-128 — <script> 몸의 <!-- 감싸개 안을 안 본다(stripComments 가 HTML 주석으로 비운다) · 텍스트 블록 \\""" · 줄 끝에서 끊긴 따옴표 뒤 //`
- 한계: `<script type="text/template">` 안 HTML 주석 줄은 줄 주석으로 읽힌다 · 정규식 리터럴 안 따옴표(5-127)는 그대로

#### 5-129 — Zones.rawLines: `<script>` 몸의 글자 태그는 날글 구역이 아니다 (D-15)
- 대상: `core/check/Zones.java`(`rawLines`) · `ZonesTest`(없으면 `CleanupTest`)
- 사실: `rawLines:42-61` 은 jsp·html 의 `<script>` 안 JS 문자열·주석 속 `<textarea>`·`<pre>`(`$("<textarea>")`)를 여는 태그로 세고 닫는 태그가 없으면 파일 끝까지 날글 구역 — 뒤쪽 console·debugger 지우기·마크업 끼우기가 전부 「`<pre>`·`<textarea>` 안」 사유로 건너뛴다
- 변경: `rawLines` 가 훑는 글 `s` 를 만들 때 `RegexRule.scriptBodies(s)` 의 몸 구간을 줄바꿈만 남기고 빈칸으로 바꾼다(주석 지운 글에 한 번 더). 그 밖은 그대로
- 시험(먼저): `rawLinesSkipsScriptBodies` — 7줄 jsp(3행 `var t = $("<textarea>").val(x);` · 4행 `console.log(1);` — `<script>` 안) → 전부 거짓(전 3~7행 참) · 3행이 `// <pre> 를 만든다` 여도 전부 거짓 · 지킴이 — 스크립트 밖 `<textarea>` … `</textarea>` 는 그 줄들만 참
- 의도된 골든 변화: 픽스처 0. corpus `cleanup-*` 의 「건너뜀(pre·textarea 안)」 이 줄 수 있다(`V-70a`)
- 검증: `bash scripts/mvn.sh -q test -Dtest='ZonesTest,CleanupTest,CleanupTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-128(`scriptBodies`)

#### 5-130 — 규칙 적재: 정의 오류는 전부 BadRule · 덮어쓰기 모르는 키 · `params.max` 공백 · naming 정규식 (D-02 · D-12 · D-13 · D-14)
- 대상: `core/check/RuleSet.java`(`load` · `validate`) · `core/check/Rule.java`(`Def.shape`) · `core/check/RegexRule.java:53`(한 줄) · `CheckRulesTest`(없으면 `CheckRulesTest`)
- 사실: 프로필을 손으로 고쳐 ① `flags: [x]`(`Rule.pattern:117` IAE) ② regex 규칙의 `params.when: "("`(`RegexRule:27` PSE — `validate:179` 는 absent 의 when 만 본다) ③ `customRules` 가 규칙 YAML 아님(`load:110` IAE) ④ `naming.controller: "^[A-Z"`(`JavaRules:113` PSE — 검사 작업 안에서)면 `BadRule` 이 아닌 예외라 규칙 화면(`CheckRoutes.getRules:100`)·코드 정리(`CleanupRoutes:42·79·111`)가 500 「서버 오류」 다 · `shape:79-95` 는 모르는 키(`enable: false`)를 말없이 버리고 `regex: [a, b]` 를 받는다 · `maxIsInt:163` 은 trim 뒤에 재는데 `RegexRule:53` 은 그대로 `parseInt`(`"5 "` → 검사 중 NFE)
- 변경:
  1. `RuleSet.load` 는 기준선 `load(2)` 26 — **분기·try 를 더하지 않는다**(몸을 옮기지도 않는다). 고치는 것은 둘뿐: `customRules` 읽기의 `throw new IllegalArgumentException("codecheck.customRules 를 못 읽었다: …")` 를 `throw new BadRule(…)` 로(글 그대로) · `return` 앞에 `naming(profile);` 한 줄
  2. `validate` — regex·absent 가지의 `try` 가 `d.pattern()` 뒤 `whenOf(d)` 를 부르고(`private static void whenOf(Rule.Def d)` — `params.when` 이 비지 않으면 kind 와 무관하게 `AbsentRule.when(d)`), `catch` 를 `IllegalArgumentException` 으로 넓힌다 — `BadRule(d.id() + ": 정규식 오류 — " + (e instanceof PatternSyntaxException p ? p.getDescription() : e.getMessage()), e)`. 지금의 `if ("absent".equals(d.kind()))` 가지는 `whenOf` 로 대신한다
  3. `private static void naming(Profile profile)` — `profile`·`profile.naming()` 이 null 이 아니면 controller·service·serviceImpl·dao 가운데 null·빈 글 아닌 것을 `Pattern.compile` 해 보고 PSE 면 `BadRule("naming.<이름>: 정규식 오류 — " + e.getDescription(), e)`
  4. `Rule.Def.shape` — 맵이면 키가 `enabled`·`severity`·`regex`·`globs`·`flags`·`skipComments`·`message`·`params`·`fix`·`ref` 밖이면 `IllegalArgumentException(id + ": 모르는 키 " + 키 + " — enabled·severity·regex·globs·flags·skipComments·message·params·fix·ref")` · `severity`·`regex`·`message`·`fix`·`ref` 는 있으면 `CharSequence` 여야 한다(아니면 `id + ": " + 키 + " 는 글"`)
  5. `RegexRule.applyText:53` — `Integer.parseInt(max.toString().trim())`
- 시험(먼저): `ruleDefinitionErrorsAreBadRule` — 위 ①~④ 와 `{enable: false}`·`{regex: [a, b]}` 프로필마다 `assertThrows(BadRule.class, () -> RuleSet.load(…))` + 글에 규칙 id(또는 `naming.controller`·`customRules`)가 든다 · `{jsp.scriptlets: {params: {max: "5 "}}}` 는 적재되고 jsp 한 파일 검사가 예외 없이 돈다. 라우트 — `CheckRoutesTest` 에 ① 프로필로 `GET /api/check/rules` 가 400 이고 본문에 규칙 id
- 의도된 골든 변화: 없음 · `self-check.txt` 0(`load(2)` 26 · `Def(11)` 그대로)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,CheckRulesTest,CheckRoutesTest,CleanupRoutesTest,ProfileStoreTest,ProfileRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 사다리: `profiles/example.yaml`·시험 프로필이 「모르는 키」 로 빨개지면 그 키를 이력에 적고 — `override` 가 실제로 읽는 키면 아는 키 집합에 더한다, 아니면 그 프로필이 오타다(프로필을 고친다)
- 의존: 5-128(같은 파일 `RegexRule`)

#### 5-131 — LocalScopes: 매개변수·패턴 변수와 같은 이름 · 옛 switch 갈래 (D-03 · D-04)
- 대상: `core/check/LocalScopes.java`(`split`·`splitName`·`scope`) · `TaintTest`(5-125 시험이 있는 파일)
- 사실: `split:60-66` 은 지역 변수 선언(`VariableDeclarator`)이 둘 넘는 이름만 가른다 — 바깥 매개변수 `p`(오염)와 익명 클래스 몸의 지역 `String p = "fixed"` 가 섞여 `new File(p)` 에 `security.pathTraversal` 오탐 · 익명 클래스 메서드의 인자가 바깥 매개변수와 같은 이름일 때도 · 앞 블록의 지역 `q`(오염)와 뒤 `if (o instanceof Integer q)` 의 패턴 변수도. `scope:100` 은 옛 꼴 switch 의 선언 범위를 갈래(`SwitchEntry`) 하나로 잡아 뒤 갈래의 같은 변수가 다른 블록의 선언과 섞인다
- 변경:
  1. 선언으로 세는 것을 셋으로 — ① 지금의 지역 `VariableDeclarator` ② `m` 안쪽 callable(익명·지역 클래스의 메서드·생성자)의 `Parameter` ③ 패턴 변수(`TypePatternExpr` — JavaParser 3.26 의 이름 있는 패턴). 셋 다 `Taint.outsideLambda` 와 같은 범위(람다 몸 밖)에서 모은다. 이름 → 선언 노드 목록(`Node`), 이름은 노드의 `SimpleName`
  2. 「첫 선언」 — `m` 자신의 매개변수에 그 이름이 있으면 매개변수가 첫 선언이다(목록의 선언은 **전부** 바꾼다, 선언이 하나여도). 없으면 지금 규칙(가장 바깥·가장 앞이 첫 선언, 둘 넘을 때만)
  3. 범위 — 지역 변수·패턴 변수는 `scope()`(가장 가까운 BlockStmt·ForStmt·ForEachStmt·TryStmt·CatchClause·**SwitchStmt/SwitchExpr**), 안쪽 callable 의 `Parameter` 는 그 callable 노드. `scope()` 에서 `SwitchEntry` 를 만나면 그 부모(switch)를 돌려준다(D-04)
  4. 바꾸는 법은 지금 그대로 — 범위 안에서 선언 자리 **뒤**의 같은 이름 `NameExpr` 와 선언 이름. 안쪽(깊은) 선언부터
- 시험(먼저): `TaintTest`(5-125 `sameNameLocals…` 옆)에 넷 — 보고서 D-03 의 세 꼴(익명 몸의 지역 `p` · 익명 메서드 인자 `p` · 패턴 변수 `q`)은 지적 0(전 각 1) · D-04 의 switch 꼴은 `security.sqlInjection` 이 11행만(전 3행·11행). 지킴이 — 익명 몸이 바깥 매개변수를 그대로 쓰는 꼴(`new File(p)` — 안쪽 선언 없음)은 지금처럼 지적 1 · `restore()` 뒤 AST 의 이름이 원래 글자(기존 단언 꼴)
- 의도된 골든 변화: 픽스처 0 기대. corpus `security-*`·`check-*` 의 보안 지적이 **주는 것**만(`V-70a`)
- 검증: `bash scripts/mvn.sh -q test -Dtest='TaintTest,JavaRulesTest,CheckRulesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 사다리: `TypePatternExpr` 가 그 판에 없으면(`PatternExpr` 만) `PatternExpr` 로 · 패턴 변수를 빼야 초록이면 ③ 을 빼고 이력 「정한 것(계획 밖)」 + 새 행
- 한계: 람다 인자·람다 몸 · `ConstLink` 가 따로 부르는 `Taint.body`(5-125 한계 그대로)

#### 5-132 — ResourceAst.released: 선언 범위 안에서만 (D-05)
- 대상: `core/check/ResourceAst.java`(`released`) · `core/check/LocalScopes.java`(`scope` 를 package-private 로) · `JavaRulesTest`(자원 규칙 시험이 있는 파일)
- 사실: `released:283-287` 이 메서드 몸 전체에서 이름 글자로 찾아, 형제 블록의 같은 이름 자원이 `close()` 되면 안 닫은 쪽도 「해제」 다 — `if (a) { InputStream in = …; try { … } finally { in.close(); } } else { InputStream in = new FileInputStream("b"); in.read(); }` 에서 8행 `security.resourceLeak` 이 안 난다
- 변경: `released` 가 훑는 노드를 `LocalScopes.scope(v).orElse(body)` 로(그 선언의 범위 노드). `releasedAt`·`captured`·`firstAssign` 에 넘기는 `body` 는 그대로
- 시험(먼저): 위 꼴 → `security.resourceLeak` 8행(전 없음) · 지킴이 — `InputStream in = null; try { in = …; } finally { in.close(); }` 는 지금처럼 없음 · 같은 블록에서 닫는 꼴 없음
- 의도된 골든 변화: 픽스처 0 기대. corpus 의 `resourceLeak` 이 **느는 것**만 — 줄마다 형제 블록 같은 이름인지 `R-37`
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaRulesTest,ResourceAstTest,CheckRulesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-131(같은 파일 `LocalScopes`)

#### 5-133 — MyBatisRules: 여러 줄 시작 태그의 줄 · 서브쿼리 안 where 태그 · sql 조각 · 제곱 시간 (D-06 · D-07 · D-08 · D-18 일부)
- 대상: `core/check/MyBatisRules.java`(`Handler.startElement`·`noWhere` · `PACKAGE:42` · 새 `tagLine`) · `MyBatisRulesTest`
- 사실: 문장 시작 줄(`:194`)·dupId 줄(`:202`)·namespace 줄(`:173`)이 SAX Locator — 시작 태그의 `>` 줄이다. 시작 태그가 여러 줄이면 지적 줄이 밀리고 윗줄 `tb-ignore-next` 가 「쓸모없는 표식」 이 된다 · `noWhere:247` 은 `whereTag` 면 바로 돌아가 `<where>` 가 서브쿼리 괄호 안에 있어도 「WHERE 있음」(`UPDATE t SET n = (SELECT n FROM o <where>…</where>)` 누락) · `noWhere` 가 `<sql>` 조각에도 돈다(`<sql id="del">DELETE FROM t</sql>` 오탐) · `PACKAGE:42` 의 `(?m)^\s*` 는 빈 줄 반복에 제곱 시간
- 변경:
  1. `private int tagLine(String qName)` — `loc.getLineNumber()` 줄에서 위로 올라가며 `raw[줄-1]` 에 `"<" + qName` 이 든 첫 줄(끝 줄은 `loc.getColumnNumber()` 앞까지만 본다). 못 찾으면 Locator 줄. 세 자리(`:173`·`:194`·`:202`)가 이것을 쓴다
  2. `noWhere` — 조건에서 `whereTag ||` 를 뺀다(태그가 넣은 `WHERE\n` 가 `stmtText` 에 있어 `topWhere` 가 괄호 깊이로 잰다). `whereTag` 를 다른 데서 안 쓰면 필드도 지운다 · 첫 줄에 `"sql".equals(stmtTag)` 면 돌아간다
  3. `PACKAGE` — `(?m)^\s*` → `(?m)^[ \t]*+`
- 시험(먼저): `MyBatisRulesTest` — 보고서 D-06 의 매퍼(3행 표식 · 4~5행 시작 태그) → 지적 0·억제 1·`common.unusedIgnore` 없음(전 5행 지적 + 3행 unusedIgnore) · D-07 매퍼 → `mybatis.noWhere` 1(전 0) · 지킴이 `UPDATE t SET a = 1 <where> id = #{id} </where>` → 0 · D-08 매퍼 → 조각 줄 지적 0(전 1) · package 없는 자바에 빈 줄 4만 → `assertTimeout(2초)`
- 의도된 골든 변화: 픽스처 골든의 mybatis 지적 줄 번호가 여러 줄 시작 태그에서 **올라가는 것**(끝 줄 → 머리 줄)만. corpus `check-*` 도 같은 꼴(`V-70a`)
- 검증: `bash scripts/mvn.sh -q test -Dtest='MyBatisRulesTest,CheckRulesTest,PureCodeCheckTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 순수본 밀린 목록: ` · 번들 91 5-133 — mybatis.noWhere: 서브쿼리 안 <where> · <sql> 조각 · 여러 줄 시작 태그의 줄`(순수본 JS 에 같은 판정이 있을 때만 — 없으면 안 적는다)

#### 5-134 — `security.sqlDollar` 파일 게이트: 매퍼 목록 설정 파일은 아니다 (D-09)
- 대상: `resources/check/rules.yaml`(`security.sqlDollar` 의 `params.when`) · 골든 `golden/check/pure-rules.json` · 픽스처 `fixtures/check/` 에 음성 하나(mybatis-config 꼴) · `CheckRulesTest`
- 사실: 게이트 `<(?:mapper|sqlMap)\b` 가 `<mappers><mapper resource="a/BMapper.xml"/></mappers>` 에도 맞아 mybatis-config·sql-map-config 의 속성 자리표시자 `value="${url}"` 가 error 로 난다
- 변경: `when: '<(?:mapper|sqlMap)\b(?![^>]*\b(?:resource|url|class)\s*=)'`(자바·JS 둘 다 되는 꼴). 주석 끝에 ` · 5-134 — 매퍼 목록(<mapper resource=…/>)만 든 설정 파일은 아니다`
- 시험(먼저): 음성 픽스처(설정 파일 — `<property name="url" value="${url}"/>` + `<mappers>` 목록) 지적 0(전 1) · 양성 — 기존 매퍼 픽스처 그대로 · iBATIS `<sqlMap namespace="B">` 본문의 `$x$`/`${x}` 는 그대로 지적
- 의도된 골든 변화: `pure-rules.json` 의 그 규칙 `when` 한 줄
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,PureCodeCheckTest,CheckRulesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 순수본 밀린 목록: ` · 번들 91 5-134 — security.sqlDollar 게이트(설정 파일의 <mapper resource=…/>)`

#### 5-135 — 붙여넣기 언어 추정 · 줄머리 정규식 (D-11 · D-18)
- 대상: `core/check/CheckRunner.java`(`guessLang` · `JAVA` · `TS_IMPORT`) · `CheckRunnerTest`
- 사실: `guessLang:217` 은 마크업으로 시작해도 `${` 가 없으면 jsp 가 아니고, `JAVA:22` 의 `(?:public\s+)?(?:class|interface)\s` 는 `public final class`·`enum`·`record` 를 못 넘어 js 로 추정된다(지적 0) · `TS_IMPORT`·`JAVA` 의 `(?m)^\s*` 는 빈 줄 4만에 29초
- 변경: ① `t.startsWith("<")` 면(XML 가지 뒤) `jsp` ② `JAVA` — `(?m)^[ \t]*+(?:package\s+[\w.]+;|import\s+[\w.*]+;|(?:(?:public|protected|private|abstract|final|static|sealed|strictfp)\s+)*+(?:class|interface|enum|record|@interface)\s)` ③ `TS_IMPORT` 의 머리도 `(?m)^[ \t]*+`
- 시험(먼저): `guessLang` — `<form:form commandName="vo">…`(EL 없음) → `jsp` · `@Controller\npublic final class A { … }` → `java` · `public enum E { A }` → `java` · `import x from 'y'` → `ts`(지킴이) · `const a = 1 < 2;` → `js`(지킴이) · `"x" + "\n".repeat(40_000)` 가 `assertTimeout(2초)`
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRunnerTest,CheckRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): 메서드만 붙인 자바는 여전히 js(TS 클래스 멤버와 못 가른다 — 화면에서 언어를 고른다)

#### 5-136 — ReturnTypes: 조상의 같은 이름·인자 수 오버로드 (D-16)
- 대상: `core/check/ReturnTypes.java`(`returnOf`) · `ReturnTypesTest`(없으면 `ValueAstTest`)
- 사실: `returnOf:101-105` 는 그 형식에 `이름/인자 수` 가 하나면 조상을 안 본다 — `Base.code(String): Object` · `Sub.code(int): String` 에서 `s.code("k")` 를 `String` 으로 보고 `ValueFix` 가 `equals` 를 뒤집는다
- 변경: 그 형식에서 하나를 찾았어도 조상 사다리(`MAX_UP` 까지, 찾을 수 있는 데까지)를 올라 같은 열쇠의 반환 형식을 모은다 — 모은 것이 **한 가지 글자**일 때만 그 형식, 둘 넘으면 빈 값. 조상을 못 찾으면(프로젝트 밖) 거기까지 모은 것으로 정한다(지금 동작)
- 시험(먼저): 보고서 D-16 세 클래스 → `of(…, "Sub", "code", 1)` 빈 값(전 `String`) · 지킴이 — 조상이 같은 반환 형식으로 선언(재정의)이면 그 형식 · 조상에 없으면 그 형식
- 의도된 골든 변화: 픽스처 0. corpus `cleanup-*` 의 값 비교 뒤집기가 **주는 것**만
- 검증: `bash scripts/mvn.sh -q test -Dtest='ReturnTypesTest,ValueAstTest,CleanupTest,SelfCheckTest'` → `bash scripts/verify.sh`

### 묶음 ③ DDL 생성·읽기 (db 레인 — `V-70` 이 컨테이너로 잰다)

기준선(이 묶음이 건드리는 파일): `AlterGen` `Run` 메서드 40 · `keyBreakingFks(1)` 18 · `uniquesAndIndexes(4)` 19 · `modifyColumn(7)` 40 / `DdlGen` `table(1)` 16 · `type(2)` 24 / `DdlReader` `item(3)` 29 · `read(1)` 30 · 중첩 6. `Run` 에 메서드를 안 더한다(바깥 `AlterGen` 의 static 도우미로). 줄 있는 메서드에는 분기를 안 더한다 — 식을 도우미 호출로 바꾼다. **값이 내려가거나 줄이 사라지는 갱신은 행에 적힌 것만 허용**(1-117 · 1-121).

#### 1-117 — AlterGen: MODIFY COLUMN 조건을 한 식으로 (대기 행 · 기준선 낮춤)
- 대상: `core/gen/AlterGen.java`(`Run.modifyColumn`) · 골든 `golden/check/self-check.txt`
- 사실: `:358` `redefines(mariadb(), typeChanged, nullChanged, cmtChanged)` 와 `:373` `typeChanged || nullChanged || cmtChanged`(mariadb 가지 안)가 같은 식 둘
- 변경: `:358` 을 두 줄로 — `boolean redefined = redefines(mariadb(), typeChanged, nullChanged, cmtChanged);` · `boolean defChanged = defaultChanged(g, tb, old, neu, redefined, why -> hand(out, why));`. `:373` 을 `if (redefined) {` 로. 그 밖 줄(`:359` 조건 포함)은 그대로
- 시험: 새 시험 없음 — 기존 `AlterGenTest` 전부 초록(특히 `unportableDefaultOnMariaRedefine`·`unportableDefaultOtherPaths`). `bash scripts/mvn.sh -q test -Dtest=SelfCheckTest` 가 `modifyColumn(7)` 줄 하나로만 빨강 → `-Dgolden.update=true` 로 갱신 → diff 가 그 한 줄이고 값이 40 보다 작다
- 의도된 골든 변화: `self-check.txt` 의 `AlterGen … modifyColumn(7)` 한 줄(40 → 38 어림) · `alter-*.sql` 0
- Ruling: 동작이 안 바뀌는 정리라 RED 없음 — 기준선 diff 가 그 자리
- 검증: `bash scripts/mvn.sh -q test -Dtest='AlterGenTest,SelfCheckTest'` → `bash scripts/verify.sh`

#### 1-116 — AlterGen `[손]` 글: 옛 기본값이 없을 때 · 안 바뀐 못 옮기는 기본값 (대기 행 + A1-02)
- 대상: `core/gen/AlterGen.java`(`defaultChanged` · 새 static `lostTail` · 상수 하나) · `AlterGenTest`
- 사실: `defaultChanged:81-90` — ① 기본값이 없던 컬럼에 못 옮기는 기본값이 생기고 MariaDB 가 통째로 다시 쓰면 「뒤 MODIFY COLUMN 이 기본값을 지운다」 가 나오는데 지울 기본값이 없다 ② 기본값이 **안 바뀌었으면** 바로 거짓이라, MariaDB 에서 형식·NULL·코멘트만 바뀐 컬럼의 못 옮기는 식 기본값(`uuid()`·`current_timestamp(6)`)이 `MODIFY COLUMN` 에서 빠진 채 살아 있는 문장으로 나가고 `[손]` 이 없다(warnings 한 줄뿐) — 코멘트 한 줄 고친 반영 DDL 이 기존 기본값을 지운다(A1-02, Important)
- 변경:
  ```java
  private static final String LOST_ADDED = "뒤 MODIFY COLUMN 은 기본값 없이 나간다 — 손으로 넣는다";

  private static boolean defaultChanged(DdlGen.Gen g, Table tb, Column old, Column neu, boolean redefined, java.util.function.Consumer<String> hand) {
      boolean same = Objects.equals(Run.norm(old.defaultValue()), Run.norm(neu.defaultValue()));
      if (same && !redefined) {
          return false;
      }
      boolean lost = DdlGen.lostDefault(neu, g.defaultOf(tb, neu, g.type(tb, neu)));
      if (lost) {
          hand.accept(COLUMN + neu.name() + " 기본값 — 식을 대상 문법으로 못 옮겼다, " + lostTail(redefined, DdlGen.blank(old.defaultValue())));
      }
      return !same && !lost;
  }

  /** [손] 꼬리 — 통째 재정의가 아니면 옛 글, 재정의인데 옛 기본값이 없으면 「없이 나간다」, 있으면 「지운다」(1-116) */
  private static String lostTail(boolean redefined, boolean hadNone) {
      return !redefined ? LOST_KEPT : hadNone ? LOST_ADDED : LOST_REDEFINED;
  }
  ```
  javadoc 에 「기본값이 안 바뀌어도 통째 재정의면 못 옮기는 식을 [손] 으로 알린다(A1-02)」. `modifyColumn` 은 안 고친다(warnings 는 `LinkedHashSet` 이라 `defaultOf` 를 한 번 더 불러도 겹치지 않는다)
- 시험(먼저): `AlterGenTest.unportableDefaultUnchangedOrNew` —
  | 꼴(대상 mariadb) | 기대 |
  |---|---|
  | `products.price` 기본값 `uuid_generate_v4()` 를 A·B 둘 다에 두고 코멘트만 바꿈(`includeComments`) | `MODIFY COLUMN` 이 있다 · `… 못 옮겼다, 뒤 MODIFY COLUMN 이 기본값을 지운다 — 손으로 다시 넣는다` 가 있다(전 `[손]` 없음) |
  | A 는 기본값 없음, B 는 `uuid_generate_v4()` + NULL 여부 바꿈 | `… 못 옮겼다, 뒤 MODIFY COLUMN 은 기본값 없이 나간다 — 손으로 넣는다`(전 「지운다」) |
  | 같은 두 꼴을 `oracle`·`postgresql` 로 | 첫 꼴은 `[손]` 없음(기본값 문장도 없음) · 둘째 꼴은 `(앞 기본값은 그대로)` |
  기존 `unportableDefaultOnMariaRedefine`(옛 기본값 있음 → 「지운다」)는 그대로 초록
- 의도된 골든 변화: `alter-*.sql` 0 기대 — 바뀌면 그 줄이 MariaDB `MODIFY COLUMN` 앞 `[손]` 인지 보고 이력에 · `self-check.txt` 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='AlterGenTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-117
- 한계(이력에): 같은 방언(mariadb → mariadb)에서도 식 기본값은 안 옮긴다(`DdlGen.defaultOf` — 새 행 후보)

#### 1-118 — AlterGen: 이름 없는 FK 대체 이름 · 스키마 인용 · 새 자동증가 컬럼 (A1-03 · A1-13 · A1-14)
- 대상: `core/gen/AlterGen.java`(`Run.addFk`·그 호출 둘 · `Run.addColumn` · `:538`) · `core/gen/DdlGen.java`(`Gen.tbl` · 새 `schemaPrefix` · 필드 `exactSchema`) · `AlterGenTest`
- 사실: `addFk:640` 의 `t.fks().indexOf(fk)` — `t` 는 A 쪽 표, `fk` 는 B 쪽이라 -1 → 대체 이름이 전부 `FK_<표>_0`(둘째 `ADD CONSTRAINT` 가 이름 겹침으로 실패) · `:538` 은 `g.id(schema)` 로, `DdlGen.tbl:608` 은 `quoted(o.schema(), false)` 로 스키마를 내 한 스크립트 안에서 `DROP INDEX "Sales".ix` 대 `ALTER TABLE Sales.t`(스키마는 스냅샷에서 왔다 — 정확 이름이어야 한다) · MariaDB 에 자동증가 컬럼을 새로 더하면 키가 생기기 전에 `ADD COLUMN … AUTO_INCREMENT` 가 먼저 나가 실패한다
- 변경:
  1. `addFk(Table t, Table owner, ForeignKey fk, String schema)` — 인자 `owner`(그 FK 를 가진 B 쪽 표)를 더하고 대체 이름 번호를 `owner.fks().indexOf(fk) + 1` 로. 호출 둘(`keyBreakingFks:601`·`foreignKeys:631`)이 그 자리의 B 표를 넘긴다. 메서드를 더하지 않는다(시그니처만)
  2. `DdlGen.Gen` 에 `boolean exactSchema`(기본 거짓) · `String schemaPrefix()` = `blank(o.schema()) ? "" : quoted(o.schema(), exactSchema) + "."` · `tbl` 이 `schemaPrefix() + named(name, exact(name))`. `AlterGen.Run.gen(schema)` 은 사용자가 준 `o.schema()` 가 비었을 때(스키마가 스냅샷에서 왔다) 만든 `Gen` 의 `exactSchema` 를 참으로. `:538` 은 `g.schemaPrefix() + g.cname(name)`(그 `g` 가 `gen(schemaOf(ta))` 가 아니면 그렇게 만든 것으로)
  3. `addColumn` — `mariadb()` 이고 자동증가 컬럼이면 `[손]` 한 줄(`COLUMN + c.name() + " 자동증가 — 키를 만든 뒤 MODIFY COLUMN … AUTO_INCREMENT 를 손으로 넣는다"`)을 남기고, 자동증가를 끈 사본(같은 값 + `autoIncrement = false`)으로 `g.column` 을 부른다
- 시험(먼저): `AlterGenTest` —
  - `unnamedFksGetDistinctNames` — oracle→oracle, A `CHILD(ID, P1, P2)` FK 없음, B 는 이름 `SYS_C001`·`SYS_C002` FK 둘 → `FK_CHILD_1`·`FK_CHILD_2` 가 하나씩(전 `FK_CHILD_0` 둘)
  - `snapshotSchemaIsQuotedTheSameWay` — postgresql→postgresql, 스키마 `Sales` 의 표(인덱스 삭제 + 컬럼 길이 변경), `Options.schema` 없음 → `DROP INDEX "Sales".` 와 `ALTER TABLE "Sales".` 둘 다(전 `ALTER TABLE Sales.`) · `Options.schema = "Sales"` 면 둘 다 `Sales.`
  - `newAutoColumnOnMariaIsHand` — mariadb→mariadb, B 에 자동증가 PK 컬럼 추가 → `ADD COLUMN` 줄에 `AUTO_INCREMENT` 가 없다 · `[손] 컬럼 id 자동증가` 가 있다(전 `ADD COLUMN … AUTO_INCREMENT`)
- 의도된 골든 변화: `alter-*.sql` 0 기대 · `self-check.txt` 0(`Run` 메서드 40 · `keyBreakingFks(1)` 18 그대로)
- 검증: `bash scripts/mvn.sh -q test -Dtest='AlterGenTest,DdlGenTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-116

#### 1-119 — DdlGen: FK 컬럼의 키 길이 · 유니크 이름 겹침 (A1-01 · A1-04)
- 대상: `core/gen/DdlGen.java`(`Gen.keyLen` · `table` 의 유니크 이름 한 식 · 새 `fkColumn`·`ukName`) · `DdlGenTest`
- 사실: `keyLen:413` 은 `inKey`(PK·유니크·인덱스)만 봐서 PG `text` 키를 가리키는 FK 자식 컬럼이 `CLOB`/`LONGTEXT` 로 나간다(부모는 `VARCHAR2(255)`) — FK 문장이 실패 · `table:303` 의 유니크 이름은 `ixNames` 를 안 봐서 MariaDB 의 표 단위 이름(`code`)이 스키마 단위 대상에서 둘째부터 겹친다
- 변경:
  1. `keyLen` 조건의 `!inKey(t, c)` → `!(inKey(t, c) || fkColumn(t, c))` — `private static boolean fkColumn(Table t, Column c)` = `t.fks()` 의 `columns()` 에 그 이름(대소문자 무시). `inKey` 자체는 안 고친다(`autoTail` 이 쓴다)
  2. `table:303` 의 `cname(nameOr(k.name(), "UK_" + t.name() + "_" + u))` → `cname(ukName(t, k, u))`
     ```java
     /** A1-04 — 유니크 제약 이름도 스키마 단위 이름 공간(mariadb 밖)에서 겹치면 표 이름을 넣은 이름으로 */
     private String ukName(Table t, UniqueKey k, int u) {
         String fallback = "UK_" + t.name() + "_" + u;
         String name = nameOr(k.name(), fallback);
         if (mariadb() || ixNames.add(name.toUpperCase(Locale.ROOT))) {
             return name;
         }
         warnings.add(t.name() + ": 유니크 이름 " + name + " 가 다른 표와 겹쳐 " + fallback + " 로 바꿨다");
         ixNames.add(fallback.toUpperCase(Locale.ROOT));
         return fallback;
     }
     ```
- 시험(먼저): `DdlGenTest` — `fkColumnFollowsTheKeyLength`(postgresql→oracle, `parent(id text PK)` · `child(parent_id text FK → parent.id)`, 인덱스 없음 → `child.parent_id VARCHAR2(255)`, 전 `CLOB`) · `uniqueNamesCollideAcrossTables`(mariadb→oracle, `member`·`product` 둘 다 유니크 이름 `code` → 둘째가 `UK_product_1` + 경고, 전 `code` 둘) · 지킴이 — mariadb→mariadb 는 `code` 둘 그대로
- 의도된 골든 변화: `ddl-*.sql` 0 기대 — 바뀌면 FK 컬럼 길이·유니크 이름 겹침인지 이력에 · `self-check.txt` 0(`table(1)` 16 그대로)
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlGenTest,AlterGenTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-118(같은 파일)

#### 1-120 — DdlGen 기본값: mariadb 대상 역슬래시 · `NULL::타입` (A1-08 · A1-18)
- 대상: `core/gen/DdlGen.java`(`Gen.defaultOf` 의 식 둘 · `lostDefault` · 새 `nullDefault`·`strDefault`) · `DdlGenTest`
- 사실: `defaultOf:453` 이 다른 방언의 문자열 기본값을 mariadb 대상에 그대로 낸다(`'C:\temp\new'` — MariaDB 는 `\t`·`\n` 을 탈출로 읽는다) · `defaultOf:441` 은 `NULL` 만 「없음」 인데 `lostDefault:710` 은 `NULL::타입` 도 「없음」 — `NULL::character varying` 에 「기본값 식은 옮기지 않았다」 헛경고
- 변경: `private static boolean nullDefault(String bare)` = `NULL` 이거나 `NULL::` 로 시작(대소문자 무시) — `defaultOf:441` 과 `lostDefault:710` 이 같이 쓴다 · `defaultOf:453` 의 `return str.group(1);` → `return strDefault(str.group(1));` — `private String strDefault(String lit)` = 대상이 mariadb 이고 원본이 비지 않았고 `maria`·`mysql` 로 시작하지 않으면 `lit.replace("\\", "\\\\")`, 아니면 그대로. `defaultOf` 에 분기를 더하지 않는다
- 시험(먼저): `defaultsAcrossDialects` — postgresql→mariadb `'C:\temp\new'::character varying` → `DEFAULT 'C:\\temp\\new'`(전 한 겹) · mariadb→mariadb 는 그대로 · 원본 방언 없음 → 그대로 · `NULL::character varying` → DEFAULT 없음 + 경고 없음(전 경고)
- 의도된 골든 변화: `ddl-*.sql` 0 기대 · `self-check.txt` 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlGenTest,AlterGenTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-119

#### 1-121 — DdlGen 타입: 고정 길이 상한 · 자동증가의 소수 타입 · 빈 괄호 (A1-19 · A1-21 · A1-17 · 기준선 낮춤)
- 대상: `core/gen/DdlGen.java`(`Gen.type` 의 열쇠 고르기 · `nativeWithSize` · `Spec` · 새 `typeKey`) · `resources/gen/ddl-types.yaml`(`fixedLimits`) · `DdlGenTest` · `self-check.txt`
- 사실: `type:354` 는 고정 길이 문자를 대상 상한 없이 `CHAR({n})` 으로(`nchar(500)` → mariadb `CHAR(500)` 실패, oracle 2000 초과도) · 자동증가 컬럼의 옮긴 타입이 소수여도 자동증가 절을 붙인다(`NUMERIC GENERATED … IDENTITY`·`DECIMAL(38,10) IDENTITY`·`DECIMAL(38,10) AUTO_INCREMENT` — 셋 다 실패) · `nativeWithSize:380` 은 `(` 가 있으면 그대로라 `numeric()` 빈 괄호가 나간다
- 변경:
  1. `ddl-types.yaml` 에 `fixedLimits: { oracle: 2000, tibero: 2000, postgresql: 10485760, mariadb: 255, mssql: 4000 }`(주석 「고정 길이 문자(CHAR)의 상한 — 넘으면 가변 길이로」) · `Spec` 에 `Map<String, Long> fixedLimits` 를 `limits` 와 같은 꼴로 읽고 방언마다 있는지 잰다
  2. `type` 의 `String key = j; … if (STRING_TYPE.equals(j)) { … } else if (BigDecimal …) { … }` 묶음을 `String key = typeKey(t, c, j, n, len);` 한 줄로 —
     ```java
     /** 대상 꼴 열쇠 — 문자: 길이 없음·상한 초과는 long, 고정 길이는 고정 상한 안에서만 fixed(넘으면 가변 + 경고) · 수: 자동증가인 BigDecimal 은 Long(정수여야 한다 + 경고), 정밀도 없으면 free */
     private String typeKey(Table t, Column c, String j, String n, Long len) {
         if (STRING_TYPE.equals(j)) {
             if (len == null || len <= 0 || len > sp.limits().get(target)) {
                 return "String.long";
             }
             if (!FIXED.contains(n)) {
                 return j;
             }
             if (len <= sp.fixedLimits().get(target)) {
                 return "String.fixed";
             }
             warnings.add(t.name() + "." + c.name() + ": 고정 길이 " + len + " 이 대상 상한을 넘어 가변 길이로 냈다");
             return j;
         }
         if (!"java.math.BigDecimal".equals(j)) {
             return j;
         }
         if (auto(c)) {
             warnings.add(t.name() + "." + c.name() + ": 자동증가 — 정수 타입으로 냈다");
             return "Long";
         }
         return c.precision() == null || c.precision() <= 0 ? "java.math.BigDecimal.free" : j;
     }
     ```
     `auto(c)` 는 `type` 이 받은 `raw` 가 아니라 지금 `autoHead` 가 쓰는 판정 그대로(자동증가 표시가 `plain` 에서 지워지면 `raw` 를 넘긴다)
  3. `nativeWithSize` 첫 줄 — `String raw = c.nativeType().trim().replaceAll("\\(\\s*\\)", "");`
- 시험(먼저): `typeKeys` — mssql→mariadb `nchar` 길이 500 → `VARCHAR(500)` + 경고(전 `CHAR(500)`) · mssql→mariadb `nchar` 길이 10 → `CHAR(10)`(지킴이) · oracle→postgresql `NUMBER`(정밀도 없음, 자동증가) → `BIGINT GENERATED BY DEFAULT AS IDENTITY` + 경고(전 `NUMERIC GENERATED …`) · oracle→mssql 같은 컬럼 → `BIGINT IDENTITY(1,1)` · mssql→mssql `numeric() identity`(정밀도 18) → `numeric(18) IDENTITY(1,1)`(전 `numeric() IDENTITY(1,1)`)
- 의도된 골든 변화: `self-check.txt` 의 `DdlGen … type(2)` 한 줄(24 → 내려감) · `ddl-*.sql` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlGenTest,AlterGenTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-120

#### 1-122 — DdlAlter: `IF [NOT] EXISTS` · 정규식 제곱 시간 · 온라인 DDL 옵션 (A1-05 · A1-06 · A1-15)
- 대상: `core/gen/DdlAlter.java`(`add`·`drop`·`action`·`column` · 정규식 넷) · `DdlReaderTest`(ALTER 시험이 있는 파일)
- 사실: `COLUMN_WORD:22` 가 `IF NOT EXISTS` 를 떼기만 해 `ADD COLUMN IF NOT EXISTS c …` 가 이미 있는 `c` 를 한 번 더 더한다(DTO 에 `c_`) · `DROP COLUMN IF EXISTS 없는컬럼` 은 「못 읽은 줄」 · `DEFAULT_FOR:41`·`DROP_TAIL:36` 은 긴 공백에 제곱 시간(10만 자 24초 — 1-96 처방과 어긋남) · `, ALGORITHM=INPLACE, LOCK=NONE` 조각이 ADD 로 이어져 `unreadableN` 컬럼이 된다
- 변경:
  1. `COLUMN_WORD` → `^(?:COLUMN\b\s*+)?(?:IF\s++(?:NOT\s++)?EXISTS\s++)?`(`ADD IF NOT EXISTS c …` 도) · `private static final Pattern IF_EXISTS = Pattern.compile("^(?:COLUMN\\b\\s*+)?IF\\s++(?:NOT\\s++)?EXISTS\\b", Pattern.CASE_INSENSITIVE);`
  2. `add` — 항목의 머리가 `IF_EXISTS` 이고 그 컬럼 이름(`DdlReader.firstToken` 의 첫 토큰을 `unquote` — `firstToken` 은 지금 `private` 이라 package-private 로 연다)이 이미 `d.cols` 에 있으면(대소문자 무시) 그 항목을 건너뛴다 · `drop` — 머리가 `IF_EXISTS` 였으면 없는 이름을 `miss` 로 안 보낸다
  3. `DEFAULT_FOR` 의 `(.+?)\s++FOR` → `(.+?)(?<!\s)\s++FOR` · `DROP_TAIL` 머리 `\s++` → `(?<!\s)\s++`
  4. `HARMLESS` 에 `|(?:ALGORITHM|LOCK)\s*+=?\s*+(?:DEFAULT|INSTANT|INPLACE|COPY|NOCOPY|NONE|SHARED|EXCLUSIVE)` · `action` 의 이어 읽기(`adding && !OTHER_VERB…`) 앞에 — 조각이 `^[\w$#]++\s*+=` 꼴(`이름=값`)이면 `miss`
- 시험(먼저): `alterIfExistsAndOptions` — `CREATE TABLE t (id INT NOT NULL, c VARCHAR(10) NOT NULL); ALTER TABLE t ADD COLUMN IF NOT EXISTS c VARCHAR(10); ALTER TABLE t ADD IF NOT EXISTS d INT; ALTER TABLE t DROP COLUMN IF EXISTS nope;` → 컬럼 `id, c, d`(c 는 NOT NULL 그대로) · 못 읽은 줄 0(전 `c` 둘 + 못 읽은 줄 하나) · ``ALTER TABLE t ADD COLUMN x int NULL AFTER c, ALGORITHM=INPLACE, LOCK=NONE`` → `x` 하나 더, `unreadable*` 컬럼 없음, 못 읽은 줄 0 · `ADD a INT, FOO=1` → `a` 하나 + 못 읽은 줄 하나. 시간 — `"… ADD DEFAULT 0" + " ".repeat(100_000) + "x"` 와 `"… DROP a" + " ".repeat(100_000) + "b"` 가 각각 `assertTimeout(2초)`
- 의도된 골든 변화: `ddl-reader*.json` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,DdlReaderTest,DtoGeneratorTest,SelfCheckTest'` → `bash scripts/verify.sh`

#### 1-123 — DDL 읽기: 공백 든 따옴표 이름 · 대괄호 타입 (A1-07 · A1-11)
- 대상: `core/gen/DdlReader.java`(`FK`·`COMMENT_ON`·`alterPk` 의 이름 묶음 · `item:320`·`names:545` 의 첫 토큰 · `item` 의 `rest`) · `core/gen/DdlAlter.java`(`HEAD`) · `core/gen/DdlReaderParts.java`(새 `QNAME`·`firstName`·`bareType`) · `DdlReaderTest`
- 사실: 컬럼·표 이름은 `[Order Details]`·`[Order ID]` 를 받는데, 키 목록(`item:320`·`names:545` 의 `split("\\s+")[0]`)·`FK:62` 의 `REFERENCES\s+([^\s(]+)`·`DdlAlter.HEAD:19` 의 `(\S++)`·`COMMENT_ON:82` 의 `(\S+)`·`alterPk:483` 은 공백에서 자른다 — PK·FK 가 없는 컬럼(`[Order`)을 가리키고 `ALTER TABLE [dbo].[Order Details] …` 는 표를 못 찾아 조용히 사라진다 · SSMS 의 대괄호 타입(`[int]`·`[nvarchar](40)`·`[decimal](18, 2)`)을 안 벗겨 DTO 필드가 전부 `Object`
- 변경:
  1. `DdlReaderParts.QNAME`(글) — 이름 한 마디 = `\[[^\]]++\]` · `"[^"]++"` · `` `[^`]++` `` · `[^\s(.,;]++` 가운데 하나, 마디 사이는 `\.{1,2}`. 수량자 단 묶음 안에 수량자를 두지 않는다(SpotBugs REDOS — 마디 반복은 `(?:\.{1,2}마디)*+` 한 겹, 마디 안은 소유 수량자). `FK` 의 참조 표 묶음 · `COMMENT_ON` 의 이름 묶음 · `DdlAlter.HEAD` 의 표 묶음 · `alterPk` 의 표 묶음을 `(QNAME)` 으로
  2. `DdlReaderParts.firstName(String part)` — 목록 한 항목의 이름 토큰(따옴표·대괄호 안 공백 포함, 뒤 `ASC`·`DESC`·`(n)` 은 뗀다). `item:320` 과 `names:545` 의 `p.trim().split("\\s+")[0]` 을 이것으로
  3. `DdlReaderParts.bareType(String rest)` — 머리의 `[형식]`(앞에 `[스키마].` 가 있으면 그것도)을 대괄호 없는 글로. `item` 에서 `rest` 를 정한 바로 뒤 `rest = DdlReaderParts.bareType(rest);` 한 줄. **`item(3)` 은 기준선 29 — 분기를 더하지 않는다**
- 시험(먼저): `quotedNamesWithSpaces` — 보고서 A1-07 재현 ① → pk `[Order ID]`, uq `[Unit Price]`, fk 컬럼 `[Order ID]` · 참조 표 `Order Master` · 참조 컬럼 `[Order ID]`(전 `[Order` 꼴) · 재현 ② → pk `[OrderID, ProductID]`, 컬럼 `Discount` 있음(전 pk null · 없음) · `COMMENT ON TABLE "My Table" IS 'x'` → 표 코멘트 `x`. `bracketTypes` — 보고서 A1-11 입력 → nativeType `int`·`nvarchar`(길이 40)·`decimal`(정밀도 18, 자릿수 2)
- 의도된 골든 변화: `ddl-reader*.json` 0 기대(이름에 공백이 없으면 같은 결과) · `self-check.txt` 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,DdlReaderTest,DdlReaderTest,DtoGeneratorTest,InsertGenTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-122(같은 파일 `DdlAlter`)

#### 1-124 — DDL 읽기 잔손: CRLF `GO` · 표 안 인덱스 꼴 · CHECK 안 `NOT NULL` · 다른 스키마의 같은 이름 (A1-09 · A1-10 · A1-22 · A1-20 · A1-16)
- 대상: `core/gen/DdlReader.java`(`statements:654` · `inlineIndex` · `item:362` 의 식 하나) · `core/gen/DdlPull.java`(`keyShape` · `find` · 새 `keyHead`) · `DdlReaderTest`
- 사실: `statements:654` 의 `[ \t\r]*+$` 가 CRLF 의 `\r` 을 먹어 `GO` 줄을 못 본다(1-96 회귀 — 뒤 ALTER·인덱스가 조용히 빠진다) · `keyShape:140` 은 `(` 앞 낱말이 둘 넘으면 컬럼으로 봐 MSSQL `INDEX ix_a NONCLUSTERED (a)`·`KEY IF NOT EXISTS ix_c (b)` 가 가짜 컬럼 `INDEX`·`KEY` 가 된다 · `INLINE_INDEX:95` 는 이름이 꼭 있어야 해 `KEY (a)`·`KEY ix USING BTREE (b)` 를 조용히 버린다 · `item:362` 가 인라인 `CHECK (e IS NOT NULL OR …)` 괄호 안 `NOT NULL` 로 컬럼을 NOT NULL 로 읽는다 · `DdlPull.find:87` 은 `s2.emp` 를 못 찾으면 스키마가 다른 `s1.emp` 로 물러선다
- 변경:
  1. `:654` 정규식 → `(?im)^[ \t]*+GO[ \t]*+\r?$`
  2. `DdlPull.keyHead(String item, int wordEnd)` — `KEY`·`INDEX` 뒤 `(` 앞 낱말을 **손으로** 나눠(정규식 수량자 겹침 금지) `IF NOT EXISTS` · `USING <방식>` · `UNIQUE`·`CLUSTERED`·`NONCLUSTERED`·`HASH`·`COLUMNSTORE` 를 뺀 나머지와 `UNIQUE` 여부·`(` 자리를 돌려준다. `keyShape` 는 「나머지 낱말 0~1」 로 재고(그 밖 조건은 그대로), `inlineIndex` 는 `INLINE_INDEX` 대신 `keyHead` 로 이름(없으면 null)·유니크·컬럼 목록을 읽는다
  3. `item:362` — `Pattern.compile("\\bNOT\\s+NULL\\b").matcher(upper)` 의 `upper` 를 `DdlReaderParts.outsideParens(upper)` 로(괄호 묶음을 깊이로 훑어 같은 길이 빈칸으로 바꾼 글 — 새 도우미). 식만 바꾼다(분기 없음)
  4. `find` — 요청에 스키마가 있고 맨 이름 자리 표의 스키마가 비지 않았고 다르면 null:
     ```java
     Draft bareHit = tables.get(bare);
     boolean other = parts.size() > 1 && bareHit != null && !bareHit.schema.isEmpty() && !bareHit.schema.equalsIgnoreCase(parts.get(parts.size() - 2));
     return q != null ? q : other ? null : bareHit;
     ```
- 시험(먼저): `crlfGo`(보고서 A1-09 입력 → pk `pk_a`, 전 null) · `inlineIndexShapes`(`CREATE TABLE t (a INT NOT NULL, b INT, INDEX ix_a NONCLUSTERED (a), INDEX ix_b UNIQUE NONCLUSTERED (b), KEY IF NOT EXISTS ix_c (b), KEY (a), KEY ix_d USING BTREE (b))` → 컬럼 `a, b` 둘뿐 · 인덱스 `ix_a`·`ix_b`(유니크)·`ix_c`·이름 없는 것·`ix_d`, 전 가짜 컬럼 셋) · `checkNotNullIsNotTheColumn`(`e INT CHECK (e IS NOT NULL OR a > 0)` → `e` nullable, 전 NOT NULL · 지킴이 `f INT NOT NULL CHECK (f > 0)` → NOT NULL) · `otherSchemaSameName`(보고서 A1-16 입력 → `s1.emp` 에 `x`·코멘트·유니크가 **안 붙는다**, 전 붙음)
- 의도된 골든 변화: `ddl-reader*.json` 0 기대 — 바뀌면 표 안 인덱스가 **느는 것**인지 이력에 · `self-check.txt` 0(`item(3)` 29 그대로)
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,DdlReaderTest,DdlReaderTest,DtoGeneratorTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-123

#### 1-125 — DDL 읽기 정규식: 제곱 시간 넷 · 스택 넘침 (A1-12)
- 대상: `core/gen/DdlReader.java`(`CTAS_ANY:123` · `TABLE_COMMENT:106` · `:370` 크기 식) · `core/gen/DdlPull.java`(`EXPR_ALIAS:63` · `MERGE_JOIN:53`) · `DdlReaderTest`
- 사실: 공백 5만 자에 `CTAS_ANY` 18초 · `TABLE_COMMENT` 7.6초 · 크기 식 25초 · `EXPR_ALIAS` 5.2초, `MERGE_JOIN` 은 `NATURAL` 뒤 낱말 2천 개에 `StackOverflowError`(500)
- 변경: `CTAS_ANY` → `(?i)\bAS\s*+\(*+\s*+(?:SELECT|WITH)\b` · `TABLE_COMMENT` → `COMMENT\s*+=?\s*+'((?:[^']++|'')*+)'` · `:370` 을 상수 `SIZE` 로 올리고 `\(\s*+(\d++)(?:\s*+,\s*+(-?\d++)|\s++(?:BYTE|CHAR))?+\s*+\)` · `EXPR_ALIAS` → `(?i)^(.+?)(?<!\s)\s++AS\s++([^\s('\"+\-*/|<>=,]++)$` · `MERGE_JOIN` → `(?i)\bNATURAL\s++(?:(?:INNER|LEFT|RIGHT|FULL)\s++)?(?:OUTER\s++)?JOIN\b|\bUSING\s*+\(`(`JOIN_SEP:51` 과 같은 꼴 — 낱말 반복 묶음을 없앤다)
- 시험(먼저): `regexesAreLinear` — 보고서 A1-12 의 다섯 입력(n = 100,000 · 낱말 5,000)이 각각 `assertTimeout(2초)` 안이고 예외가 없다. 지킴이 — `VARCHAR2(10 BYTE)`·`NUMBER(10, 2)`·`NUMBER( 5 )` 의 길이·정밀도 · `CREATE TABLE t AS SELECT a.x AS y FROM a` · `NATURAL LEFT OUTER JOIN` 이 지금과 같은 결과
- 의도된 골든 변화: `ddl-reader*.json` 0 · `self-check.txt` 0
- 검증: 1-124 와 같다. 의존: 1-124

### 묶음 ④ 생성기 — INSERT · 소스 · 표 편집기 (db·corpus 레인)

기준선: `GenModel` `of(4)` 복잡도 32 · 길이 107 / `InsertGen` `generate(4)` 20 · `buildUpsert(5)` 24 · `csvLiteral(5)` 31 · `genVal(4)` 36 · `Col(11)` / `Validation` `parse(1)` 28 · `of(5)` 56 / `DtoGenerator` `generate(2)` 30. 줄 있는 메서드에는 분기·줄을 더하지 않는다(식을 도우미 호출로). `buildUpsert` 의 인자 수를 바꾸지 않는다(이름표가 바뀐다).

#### 7-37 — 생성기 이름: 파생 module 예약어 · VO 고정 필드와 겹침 · `_` 로 시작하는 컬럼 (A2-01 · A2-05 · A2-12 · 기준선 낮춤)
- 대상: `core/gen/GenModel.java`(`of` 의 식 둘 · 새 `moduleOf` · 상수 `VO_FIXED`) · `core/gen/DtoGenerator.java`(`camel`) · `GenModelTest`·`DtoGeneratorTest` · `self-check.txt`
- 사실: `of:96` — module 을 안 주면 클래스 이름 소문자가 패키지 마디다. 예약어 검사(1-108)는 요청 값에만 있어 `TB_CASE`·`TB_CLASS`·`TB_PACKAGE` 가 `package egovframework.app.case.service;`(열 파일 전부 컴파일 실패, 경고 없음) · `of:107·129` 의 `used` 는 VO 템플릿 고정 필드(`pageIndex`·`pageSize`·`pageUnit`·`firstIndex`·`lastIndex`·`recordCountPerPage`·`searchCondition`·`searchKeyword`)를 몰라 컬럼 `PAGE_SIZE` 가 있으면 필드·접근자가 둘씩 · `DtoGenerator.camel:237` 이 맨 앞 `_x` 를 `X` 로 올려 `_ID` 가 필드 `Id`(JSP `${result.Id}` 런타임 오류, `ID` 컬럼과 getter 겹침)
- 변경:
  1. `of:96` → `String module = moduleOf(o.module(), cls);` — `private static String moduleOf(String given, String cls)`: 준 값이 있으면 trim 한 그것, 없으면 `cls.toLowerCase(Locale.ROOT)` 에 `DtoGenerator.keyword(…)` 면 `_` 를 붙인다(`case_`)
  2. `of:107` → `Set<String> used = new HashSet<>(VO_FIXED);` — `private static final Set<String> VO_FIXED = Set.of("pageIndex", "pageSize", "pageUnit", "firstIndex", "lastIndex", "recordCountPerPage", "searchCondition", "searchKeyword");`
  3. `camel` — 첫 줄을 `String s = LEAD_UNDERSCORE.matcher(col.trim().toLowerCase(Locale.ROOT)).replaceFirst("");`(`private static final Pattern LEAD_UNDERSCORE = Pattern.compile("^_++(?=[a-z0-9])");`) — 앞 밑줄은 떼고 첫 글자는 안 올린다(`_ID` → `id`, 겹치면 `used` 가 `id_`)
- 시험(먼저): `GenModelTest` — 표 `TB_CASE`(skipTokens `[TB]`, module 없음) → 패키지 `….case_.service` · 경로 `…/case_/web/CaseController.java`(전 `case`) · 표 `TB_BBS`(`PAGE_SIZE`·`PAGE_INDEX` 컬럼) → 필드 `pageSize_`·`pageIndex_`(전 `pageSize`·`pageIndex` — 고정 필드와 겹침) · `DtoGeneratorTest` — `fieldName("_ID")` = `id` · `fieldName("__X_Y")` = `xY` · `fieldName("USER_ID")` = `userId`(지킴이). 생성 소스 컴파일 시험(`GeneratorJavacTest`)에 `TB_CASE`·`TB_BBS` 를 더한다
- 의도된 골든 변화: `self-check.txt` 의 `GenModel … of(4)` 복잡도 줄(32 → 내려감, 길이 줄은 그대로거나 내려감) · `gen-*` 골든 0 기대(픽스처에 그런 이름이 없다)
- 검증: `bash scripts/mvn.sh -q test -Dtest='GenModelTest,DtoGeneratorTest,GeneratorTest,GenerateRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): BETWEEN 검색의 `…From`·`…To` 필드와 같은 이름 컬럼 · 표 이름이 예약어여도 클래스 이름(`Case`)은 그대로

#### 4-46 — INSERT: 스냅샷의 대소문자 섞인 이름 · 표가 여럿인 DDL (A2-02 · A2-13)
- 대상: `core/gen/InsertGen.java`(`Options` · `ident` · `generate`·`fromCsv` 의 INSERT 길 호출) · `web/InsertRoutes.java` · `InsertGenTest`·`InsertRoutesTest`
- 사실: `DdlGen.quoted` 는 스냅샷의 대소문자 섞인 이름(PG·Oracle 의 따옴표로 만든 표)을 정확 이름으로 감싸는데 `InsertGen.ident:583` 에는 그 판정이 없다 — 같은 스냅샷의 DDL 은 `CREATE TABLE "Artist"`, INSERT 는 `INSERT INTO Artist(`(대상이 `artist`·`ARTIST` 로 접어 「표가 없다」) · 「CREATE 문에서」 탭은 `table` 을 안 보내 `InsertRoutes.pick:185` 가 첫 표만 고르는데 경고가 없다
- 변경:
  1. `Options` 에 여섯째 성분 `boolean exactNames` + 다섯 인자 생성자(`this(…, false)` — 지금 호출 42곳은 그대로). `InsertRoutes` 는 스냅샷 모드(`parsed == null`)에서만 `true`
  2. `static String ident(String name, String db, boolean exact)` — `exact` 이고 그 이름이 대상에서 접히면(`oracle`: 대문자화한 글과 다름 · `pg`: 소문자화한 글과 다름) 접지 않고 그대로 감싼다, 아니면 지금 `ident(name, db)`. `generate` 와 `fromCsv` 의 **INSERT 문**(upsert 아님)이 표·컬럼 이름에 `ident(…, o.exactNames())` 를 쓴다. `buildUpsert`·`uid` 는 안 고친다
  3. `InsertRoutes` — DDL 모드이고 `req.table()` 이 비었고 읽은 표가 둘 넘으면 `lead` 에 「DDL 에 표 N개 — 첫 표 <이름> 만 만들었다」 한 줄
- 시험(먼저): `InsertGenTest.exactNamesInSnapshotMode` — 표 `Artist`(`ArtistId`·`Name`), `pg`·`exactNames` → `INSERT INTO "Artist"(` · `"ArtistId"` · `"Name"`(전 따옴표 없음) · `oracle` 도 `"Artist"` · `exactNames` 거짓이면 지금대로 · 이름이 `artist`(pg)·`ARTIST`(oracle)면 따옴표 없음(지킴이) · `mysql`·`mssql` 은 그대로. `InsertRoutesTest` — DDL `CREATE TABLE a (id INT); CREATE TABLE b (id INT);`(table 없음) 응답 `warnings` 에 「표 2개 — 첫 표 a 만」
- 의도된 골든 변화: `insert-*`·`hr-insert-*` 0 기대(소문자·대문자 이름뿐) · `self-check.txt` 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='InsertGenTest,InsertRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`. 레인 db
- 한계(이력에): upsert·MERGE 틀은 지금처럼 대문자 이름 · DDL 모드는 원문 따옴표를 기억 못 한다(설계 71 범위 밖 그대로)

#### 4-47 — 허용 값 판정을 한 자리로 · 리터럴 안 `::` (A2-03 · A2-06)
- 대상: `core/gen/InsertGen.java`(`checkIns(Table)`) · `core/gen/Validation.java`(`normalize` 의 캐스트 지우기) · `InsertGenTest`·`ValidationTest`
- 사실: 허용 값 판정이 둘이다 — `Validation.parse:174` 는 겹괄호·`OR` 등식·PG `= ANY (ARRAY[…])` 를 `InSet` 으로 푸는데 `InsertGen.checkIns(Table):166` 은 `CHECK (col IN (` 로 바로 시작하는 꼴(`CHECK_IN:41`)만 본다 — pg_dump·MySQL 8·MSSQL 스크립트에서 INSERT 값이 `'ST_001'`(CHECK 위반), 같은 DDL 의 DTO 는 `@Pattern("^(NEW|PAID)$")` · `normalize:286` 이 `CAST:64` 를 리터럴 안에도 적용해 `'std::string'` 이 `std` 로 잘린다
- 변경:
  1. `checkIns(Table)` — CHECK 마다 `Validation.parse(k.condition())` 이 `InSet` 이면 `컬럼(대문자) → 값들`(`quoted` 면 `'` 로 감싸고 안 `'` 는 겹친 리터럴, 아니면 글 그대로)을 `putIfAbsent`. `InSet` 이 아니면 지금의 `InsertGenParts.collect("CHECK (" + 조건 + ")", out)` 로(물러설 길). DDL 글 전체를 받는 `checkIns(String)` 은 그대로
  2. `normalize` 의 `CAST.matcher(condition).replaceAll("")` 를 따옴표 밖에서만 — `private static String castsOutsideQuotes(String s)`(홑따옴표 글 `'…'`·`''` 를 건너뛰며 밖 토막에만 `CAST` 를 지운다)로 바꿔 부른다. `parse(1)`·`of(5)` 는 안 건드린다
- 시험(먼저): `InsertGenTest.allowedValuesFromAnyCheckShape` — 보고서 A2-03 의 세 DDL(pg_dump `= ANY ((ARRAY[…])::text[])` · 겹괄호 `CHECK ((st IN ('A','B')))` · `CHECK (s2 = 'A' OR s2 = 'B')`)을 `DdlReader.read` → `checkIns(표)` 에 그 컬럼의 값 둘 · 생성 INSERT 값이 그 둘 가운데(전 `'ST_001'`) · 지킴이 `CHECK (st IN ('A','B'))` 지금과 같음. `ValidationTest` — `CHECK (kind IN ('std::string', 'x'))` → `@Pattern(regexp = "^(std::string|x)$")`(전 `^(std|x)$`) · `CHECK ((st)::text = ANY (ARRAY['A'::character varying]))` 지금과 같음
- 의도된 골든 변화: `insert-*` 0 기대 · `self-check.txt` 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='InsertGenTest,ValidationTest,DtoGeneratorTest,InsertRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 4-46(같은 파일)

#### 4-48 — INSERT 값: CSV 칸 수 · 좁은 수 키 · FK 로 채운 키 · INTERVAL · BIT · POINT (A2-04 · A2-07 · A2-08 · A2-09 · A2-10 · A2-11 · 기준선 낮춤)
- 대상: `core/gen/InsertGen.java`(`fromCsv` · `domain` · `keyOverlap` · `other` · `genVal`·`csvLiteral` 의 식 하나씩 · 새 `numish`·`bit`) · `InsertGenTest` · `self-check.txt`
- 사실: `fromCsv:646` — 값 행의 칸 수가 머리와 달라도 경고 없이 넘치는 칸을 버리고 모자란 칸을 NULL 로(값 안 쉼표를 안 감싼 CSV 에서 뒤 칸이 밀리고 마지막 값이 사라진다 — Important) · `domain:386` 은 수 키에 0 이라 `NUMBER(2)` PK 에 150행이어도 경고 없음 · FK 로 채운 키 컬럼(`generate:465` 의 `fv.get(idx % fv.size())`)의 값수를 `keyOverlap` 이 모른다 · `other:322` 의 Oracle `INTERVAL '100' DAY` 는 기본 정밀도 2 를 넘는다 · `genVal:257` 은 PG `BIT` 에 `1`(`csvLiteral:675` 는 `B'1'`), `BIT VARYING` 은 `'VAL_1'` · `NUMISH:53` 의 `.*INT.*` 가 `POINT` 에 걸리고 `csvLiteral:680` 에는 INTERVAL 제외가 없다
- 변경:
  1. `fromCsv` — 행마다 `row.size() != head.size()` 면 `warns` 에 `<줄>행: 칸 수 <n> — 머리 <m>`(값은 안 싣는다)
  2. `private static boolean numish(String t)` = `NUMISH` 가 맞고 `INTERVAL` 로 시작하지 않고 `POINT` 를 품지 않는다. `genVal:276` 의 `NUMISH.matcher(t).matches() && !t.startsWith("INTERVAL")` → `numish(t)` · `csvLiteral:680` 의 `NUMISH.matcher(t).matches()` → `numish(t)`
  3. `genVal:257-258` 의 `return idx % 2 != 0 ? "0" : "1";` → `return bit(idx, db);`(`pg` 면 `B'0'`·`B'1'`) · `other` 머리에 `t.startsWith("BIT VARYING") || t.startsWith("VARBIT")` 이고 `pg` 면 `B'0'`·`B'1'`
  4. `domain` — `numish(t)` 이고 `c.scale() <= 0` 이고 `1 <= c.prec() <= 9` 면 `10^prec − 1`(문자 길이 가지 뒤, TINYINT 가지 앞)
  5. `keyOverlap(Table t, List<Col> cols, int rows, Map<String, List<Object>> fkCol)` — 키 묶음의 컬럼이 `fkCol` 에 있으면 그 값들의 서로 다른 수를 값수로(지금 `domain` 대신). `generate` 가 `fkCol` 을 넘긴다. 다른 호출이 있으면 빈 맵
  6. `other` 의 Oracle INTERVAL — 수의 자릿수가 2 를 넘으면 `DAY(<자릿수>)`·`MONTH(<자릿수>)`
- 시험(먼저): `InsertGenTest` — 보고서 A2-04 CSV → 경고 `2행: 칸 수 4 — 머리 3`·`3행: 칸 수 2 — 머리 3`(전 0) · `cd(cd_no NUMBER(2) PK)` 150행 → 「허용 값이 99개인 키 컬럼」 경고(전 0) · `usr_dtl`(FK 로 채운 PK, 부모 값 둘) 5행 → 「허용 값이 2개」 경고 · oracle `INTERVAL DAY TO SECOND` 100행째 `INTERVAL '100' DAY(3)` · pg `flag BIT` → `B'1'`·`B'0'`, `BIT VARYING(8)` → `B'1'` · pg `pt POINT` 값이 수가 아니다(`'VAL_1'` 꼴 — 지금 `other` 의 끝 가지) · CSV `iv INTERVAL` 값 `1 day` 가 따옴표 리터럴이고 「숫자가 아니다」 경고 없음
- 의도된 골든 변화: `self-check.txt` 의 `InsertGen … genVal(4)` 한 줄(36 → 35) · `insert-*` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='InsertGenTest,InsertRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 4-47

#### 4-49 — 표 편집기 JSX: `>` · 안 닫힌 태그 제곱 시간 · CSS 문자열 탈출 (A2-14 · A2-15 · A2-16)
- 대상: `resources/tools/table_builder_ext.js`(`tbJsxPart`·`tbJsxDecls`) · `SmokeHtmlUnitTest`(4-41 의 `tbJsxHtml` 단언이 있는 시험)
- 사실: `tbJsxPart:223` 이 홀로 선 `<`·`{`·`}` 만 엔터티로 바꿔 셀 글 `a > b` 가 TSX 에서 TS1382 · 태그 정규식이 닫는 `>` 없는 `<a ` 반복에 제곱 시간(24만 자 19.5초) · `tbJsxDecls:146` 이 CSS 문자열 안 `\"` 를 닫는 따옴표로 읽는다
- 변경: ① 정규식 대안 끝에 `|>` 를 더하고 `all === '>'` 면 `&gt;` ② 태그 대안의 속성 부분 `[^>"']` → `[^<>"']`(속성 안에 `<` 가 오면 태그가 아니다 — `<` 마다 끝까지 안 훑는다) ③ `tbJsxDecls` 의 따옴표 안 가지 — `ch === '\\'` 면 다음 글자까지 `cur` 에 담고 건너뛴다
- 시험(먼저): `tbJsxHtml('a > b')` = `a &gt; b` · `tbJsxHtml('<b title="a> b">x</b>')` 는 지금과 같다(지킴이 — 속성 안 `>`) · `tbJsxHtml('<a '.repeat(60000))` 가 2초 안 · 보고서 A2-16 입력 → `content: '"a\\";b"'` 와 `color: 'red'` 두 선언
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='SmokeHtmlUnitTest,ToolsFolderTest'` → `bash scripts/verify.sh`. 레인 corpus(`resources/tools`)
- Ruling: 화면 JS 만 고치는 행 — HtmlUnit 단언이 RED→GREEN 자리

### 묶음 ⑤ 프로그램 분석 — Java·JSP·저장 (corpus 레인)

기준선: `JpaIndex` 메서드 38 · `entity(1)` 18 · `jpql(6)` 40 — `JpaIndex` 에 메서드를 안 더하고 두 메서드에 분기를 안 더한다.

#### 6-77 — JpaIndex: 조상은 자식의 extends 글이 가리킬 때만 (대기 행)
- 대상: `core/analyze/JpaIndex.java`(`superEntity:310` · `base:350` · `repo:387`) · `core/analyze/JpqlPaths.java`(`named` 를 package-private 로 — 몸 그대로) · `JpaIndexTest`
- 사실: 맥락 「보정 A」 의 `6-77`. `lookup` → `pick:540` 은 후보가 하나면 import·패키지를 안 본다 — `import com.vendor.BaseEntity`(프로젝트 밖)인 엔티티가 프로젝트의 `q.BaseEntity` 표를 물려받고(`superEntity`), 프로젝트 밖 `BaseRepo` 를 상속한 인터페이스가 프로젝트의 `q.BaseRepo` 로 저장소가 된다(`base`·`repo`). `JpqlPaths.parent` 는 `named`(6-73)로 좁혔다 — 어긋남
- 변경(세 자리 다 `lookup` 결과에 `JpqlPaths.named(자식, extends 글, 후보)` 를 더 건다 — 메서드를 더하지 않는다):
  1. `superEntity` — `ClassOrInterfaceType e0 = ext.get(0); List<Cls> up = lookup(e0.getName().getIdentifier(), cur.from()); if (up.size() != 1 || !JpqlPaths.named(cur, e0, up.get(0))) { return null; }`
  2. `base:350` — `.filter(u -> u.decl.isInterface() && JpqlPaths.named(c, x, u))`
  3. `repo:387-388` — `if (up.size() == 1 && JpqlPaths.named(c, x, up.get(0)) && base(up.get(0), new HashSet<>()) == null)`
- 시험(먼저): `JpaIndexTest.ancestorsMustBeNamedEverywhere`(인라인 `Source`, 머리 `package <pkg>;\nimport javax.persistence.*;\n`) —
  - `q/BaseEntity.java` `@Entity @Table(name = "T91_BASE") public class BaseEntity { @Id Long id; }` · `p/Vendor.java` `import com.vendor.BaseEntity;` `@Entity public class Vendor extends BaseEntity { }`(@Table 없음) · `p/Star.java` `import q.*;` `@Entity public class Star extends BaseEntity { }` · `p/Full.java` `@Entity public class Full extends q.BaseEntity { }`
  - `q/BaseRepo.java` `import org.springframework.data.jpa.repository.JpaRepository;` `public interface BaseRepo<T> extends JpaRepository<T, Long> { }` · `p/VendorRepo.java` `import com.vendor.BaseRepo;` `public interface VendorRepo extends BaseRepo<Vendor> { }` · `p/StarRepo.java` `import q.BaseRepo;` `public interface StarRepo extends BaseRepo<Star> { }`
  | 무엇 | 기대 | 전 |
  |---|---|---|
  | `Vendor` 의 표 | 지은 이름(`guessed` 참) + 미해결 `entityName` | `T91_BASE`(지은 것 아님) |
  | `Star`·`Full` 의 표 | `T91_BASE` | 같음 |
  | `repositories()` | `p.StarRepo`(엔티티 `p.Star`)만 — `p.VendorRepo` 없음 | 둘 다 |
  기존 `ancestorMustBeNamedByTheChild`·`nestedAncestorNamedThroughItsOuter`·`inheritedAssociationPath`·`golden` 은 그대로 초록
- 의도된 골든 변화: `jpa-index.json` 0 기대 · `self-check.txt` 0. corpus `analyze-*`(JPA 프로젝트 — egov-msa·shopizer·jeecg)의 엔티티 표·저장소가 **주는 것**만 — 줄마다 조상 import 가 프로젝트 밖인지 `R-37`
- 검증: `bash scripts/mvn.sh -q test -Dtest='JpaIndexTest,JavaGraphTest,SelfCheckTest'` → `bash scripts/verify.sh`

기준선(이 묶음): `JavaGraph` 메서드 34 · `walk(5)` 복잡도 42·중첩 5 · `run(2)` 31 · `index(4)` 21 · `entityManager(4)` 19 · `querydsl(3)` 22 / `JavaViews` `value(4)` 18 / `AnalyzeRunner` `run(4)` 30·길이 116 / `AnalyzeStore` `save(3)`. `JavaGraph` 에 메서드를 안 더한다(새 도우미는 `TypeBind`·`StmtId`·새 `StmtArgs` 에). 줄 있는 메서드에는 분기·줄을 안 더한다.

#### 0-120 — VCS: git 이 스스로 원격에 닿는 길 · 따옴표 든 필터 이름 (C2-01 — Critical, 절대 규칙 1 · C2-02)
- 대상: `core/vcs/Cli.java`(`run` 의 환경 · 새 `env`) · `core/vcs/WorkingCopy.java`(`filtersOff` · 새 `refusePartialClone` · `recent`·`gitChanged`·diff 길) · `WorkingCopyTest`
- 사실: `Cli.run:85-88` 의 환경은 `GIT_OPTIONAL_LOCKS`·`GIT_TERMINAL_PROMPT`·`LC_ALL` 셋뿐이다. promisor 원격이 설정된 작업 사본(부분 클론 `--filter=tree:0`, 또는 받은 소스의 `.git/config` 에 `remote.<이름>.promisor = true`)에서 없는 객체가 필요해지면 git 이 스스로 fetch 를 띄운다 — 원격 주소에 접속하고 `remote.<이름>.uploadpack` 에 적힌 명령을 실행한다. 폴더 칸에 그 폴더를 넣기만 해도 `GET /api/check/vcs` 가 `git status`(`:202`)·`git log`(`:370`)를 돈다. 재확인(내 탐침, git 2.54.0) — `git -c remote.zzprobe.promisor=true -c remote.zzprobe.url=C:/nonexistent-zz-probe cat-file -e 1234…` → 「Could not read from remote repository」(fetch 를 시도했다) · 앞에 `GIT_NO_LAZY_FETCH=1` 이면 출력 없이 끝난다. `filtersOff:171` 은 필터 이름의 `=`·줄바꿈만 거절해, 이름에 `"` 가 있으면 Windows 에서 `-c filter.<이름>.clean=` 인자가 갈려 그 필터가 안 꺼진다(공백까지 있으면 `--git-dir`·`-C` 같은 전역 옵션이 끼어든다)
- 변경:
  1. `Cli` — 환경 넷을 `static Map<String, String> env()`(고정 맵 — `GIT_OPTIONAL_LOCKS=0`·`GIT_TERMINAL_PROMPT=0`·`LC_ALL=C`·**`GIT_NO_LAZY_FETCH=1`**)로 빼고 `run` 이 `pb.environment().putAll(env())`
  2. `WorkingCopy.refusePartialClone(Path root)` — `git config --local --includes --name-only --list -z`(지금 `configArgs("--local")`) 출력의 키에 `extensions.partialclone` 이 있거나 `remote.` 로 시작해 `.promisor` 로 끝나는 것이 있으면 `IllegalStateException("부분 클론 저장소(promisor 원격) — git 조회를 하지 않는다")`. `GIT_NO_LAZY_FETCH` 를 모르는 옛 git 대비다. `gitChanged`(→ `filtersOff` 앞)·`recent`·diff 길(`:299`)의 첫 줄에서 부른다 — 설정 읽기(`config`·`rev-parse`)는 아무것도 실행하지 않아 그대로
  3. `filtersOff:171` — 거절 조건에 `n.indexOf('"') >= 0` · 그 꼴의 글은 「저장소 설정의 filter 이름에 따옴표가 있어 끌 수 없다 — 변경분 조회를 하지 않는다」(`=` 글은 그대로)
- 시험(먼저): `WorkingCopyTest` — `cliEnvForbidsLazyFetch`(`Cli.env()` 에 `GIT_NO_LAZY_FETCH=1` 과 옛 셋) · `gitRefusesPartialClone`(임시 저장소에 `git config remote.o.promisor true`·`remote.o.url <임시 폴더 안 없는 경로>` → `WorkingCopy.changed` 가 `IllegalStateException`(글에 「부분 클론」) · `recent` 도 · `extensions.partialclone` 만 있어도) · `gitRefusesFilterNameWithQuote`(`gitRefusesFilterNameWithEquals` 꼴로 `.git/config` 에 `[filter "a\"\"b"]` → 거절) · 지킴이 — 보통 저장소의 `changed`·`recent` 는 지금과 같다(`gitChangedDiffRecent` 초록). 라우트 — `GET /api/check/vcs` 가 그 저장소에 `available: false` + 사유(`CheckRoutesTest` 의 vcs 시험 꼴)
- 의도된 골든 변화: 없음. `scripts/probes/*.patch` 가 `Cli.run` 줄에 걸려 있으면 `gate-probe` 가 「패치 갱신 필요」 — 마무리에서 다시 뜬다
- 검증: `bash scripts/mvn.sh -q test -Dtest='WorkingCopyTest,CheckRoutesTest,ArchitectureTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): 빈 이름 필터(`[filter ""]`)·`core.worktree` 가 다른 폴더를 가리키는 설정은 못 재현 — 0-84 사람 실측 목록에 한 줄

#### 6-85 — JspLinks: `<script>` 감싸개 · `c:set` 컨텍스트 이름 (C2-03 · C2-08 · C2-12)
- 대상: `core/analyze/JspLinks.java`(`extract:126` · `ctxNames` · `CTX_SET`) · `JspLinksTest`
- 사실: `extract:126` 이 `stripComments(…, "jsp")` 로 HTML 주석을 먼저 지워, `<script>` 몸을 `<!--` … `//-->` 로 감싼 꼴의 링크가 미해결 없이 전부 빠진다 — 점검자 표본 실측 egov 742 JSP 가운데 181 파일이 이 꼴이고 (파일, URL) 287 건(전체 2,123 의 13.5%)이 빠진다(egov-enterprise 70 · egov-portal 35) · `CTX_SET:33` 은 값에 `contextPath` 글자만 있으면 그 변수를 컨텍스트 이름으로 넣어 `value="${…contextPath}/admin"` 의 `${adminPath}/user/list.do` 가 접두 빠진 `/user/list.do` 링크가 된다 · `CTX_SET` 의 게으른 `[^>]*?` 둘은 닫는 `>` 없는 `<c:set` 반복에 세제곱 시간(12,000자 6초)
- 변경:
  1. `:126` → `RegexRule.scriptComments(RegexRule.stripComments(RegexRule.scriptWrappers(원문), "jsp"))`
  2. `ctxNames` — 정규식 한 방 대신 `<c:set` 를 `indexOf`(대소문자 무시 — `regionMatches`)로 찾아 그 태그의 첫 `>` 까지를 잘라(없으면 끝낸다) 그 태그 글에만 두 작은 식을 맞춘다: `var\s*+=\s*+["']([\w.]++)["']` 와 `value\s*+=\s*+["']\$\{[^}"']*+contextPath\s*+\}["']`(값이 `${…contextPath}` **하나로 끝날 때만**). 둘 다 맞으면 이름을 넣는다. `CTX_SET` 은 지운다
- 시험(먼저): `JspLinksTest` — `wrappedScriptLinks`(보고서 C2-03 입력 → `[Link(/updateSampleView.do, form)]`, 전 `[]`) · `ctxSetNeedsBareContextPath`(`<c:set var="adminPath" value="${pageContext.request.contextPath}/admin"/>` + `<a href="${adminPath}/user/list.do">` → 링크 없음 + 미해결 `jspUrl ${}/user/list.do`, 전 `/user/list.do` 링크) · 지킴이 `<c:set var="ctxRoot" value="${pageContext.request.contextPath}"/>` + `${ctxRoot}/a.do` → `/a.do` · `"<c:set var='a' ".repeat(2000)` 의 `ctxNames` 가 `assertTimeout(2초)`
- 의도된 골든 변화: 픽스처 골든 0 기대. corpus `analyze-*`(`-jspurl.txt`·`impact`·`orphan-jsp`·화면 전수)의 JSP 링크가 **느는 것**(감싼 스크립트 안)과 `orphan-jsp` 가 **주는 것** — 수는 `V-70a` 가 이력에(점검자 어림 egov +287)
- 검증: `bash scripts/mvn.sh -q test -Dtest='JspLinksTest,AnalyzeStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-128(`RegexRule.scriptWrappers`)

#### 6-86 — JavaGraph: 공통 DAO 가 문장 id 를 매개변수로 넘기는 꼴 (C2-04 — Important)
- 대상: 새 `core/analyze/StmtArgs.java` · `core/analyze/JavaGraph.java`(`walk` 의 두 줄·방문 열쇠 · `statement` · `Acc` 필드 · `varLiterals` 를 package-private 로) · `JavaGraphTest`
- 사실: 프로젝트 안 공통 DAO(`AbstractDAO.selectList(String queryId, Object p) { return sqlSession.selectList(queryId, p); }`)를 상속한 DAO 가 `selectList("foo.find", vo)` 를 부르면 — `sink:520` 은 조상에 같은 이름·인자 수 메서드가 있어 싱크가 아니고, 그 메서드 몸의 `sqlSession.selectList(queryId, …)` 는 첫 인자가 매개변수라 `statement:588` 이 미해결 한 줄만 낸다. 프로그램 `stmts=[]` · CRUD 매트릭스가 통째로 비고 매퍼 문장이 전부 「안 불리는 문장」
- 변경:
  1. `StmtArgs`(final, package-private) —
     ```java
     /** 호출 한 층 — 부른 클래스·메서드, 넘긴 인자, 불린 메서드 */
     record Call(JavaGraph.Cls c, MethodDeclaration m, List<Expression> args, MethodDeclaration callee) { }
     /** 문장 id 식의 출처 — 그 식과 식이 놓인 클래스·메서드 */
     record At(JavaGraph.Cls c, MethodDeclaration m, Expression arg) { }
     ```
     - `static At origin(JavaGraph.Cls c, MethodDeclaration m, Expression a, Deque<Call> calls)` — `a` 가 `m` 의 매개변수 이름(`NameExpr`)이고 `JavaGraph.varLiterals(c, m, 이름)` 이 비었고 스택의 그 층(`callee == m`)이 있고 그 층의 인자 수가 그 매개변수 자리보다 많으면, 그 층의 `(c, m, args[자리])` 로 한 층 올라가 되풀이한다(스택을 위에서 아래로 한 층씩). 아니면 `At(c, m, a)`
     - `static String key(MethodDeclaration callee, Deque<Call> calls)` — `callee` 가 「자기 매개변수를 `JavaGraph.SINKS` 이름 호출의 첫 인자로 그대로 넘기는」 메서드이고 스택 꼭대기 층의 `callee` 가 그것이면 `"|" + 그 자리 인자의 글`(문자열 리터럴이면 값, 아니면 `toString()` 의 앞 100자), 아니면 `""`
  2. `JavaGraph.Acc` 에 `final Deque<StmtArgs.Call> calls = new ArrayDeque<>();`
  3. `walk` — **`walk(5)` 는 기준선 42·중첩 5: 분기를 더하지 않는다**. 방문 열쇠 끝에 `+ StmtArgs.key(m, acc.calls)` · 재귀 앞뒤에 한 줄씩 `acc.calls.push(new StmtArgs.Call(c, m, mc.getArguments(), x.method()));` … `acc.calls.pop();`
  4. `statement` — 첫 줄을 `StmtArgs.At at = StmtArgs.origin(c, m, mc.getArgument(0), acc.calls);` 로 하고 그 뒤 `a`·`c`·`m` 자리에 `at.arg()`·`at.c()`·`at.m()` 을 쓴다(리터럴·접두·변수 리터럴 판정은 그대로, 미해결의 파일·줄·이름도 출처 쪽)
- 시험(먼저): `JavaGraphTest.statementIdPassedThroughACommonDao` — 보고서 C2-04 의 세 파일(`AbstractDAO`·`FooDAO`·`/foo/list.do` 컨트롤러) → 프로그램 `stmts = [foo.find literal]`(전 `[]`) · `daoStatements` 에 `foo.find` · 같은 프로그램이 `find`·`find2`(`selectList("foo.find2", vo)`)를 둘 다 부르면 둘 다(방문 열쇠) · 두 층 감싸개(`BaseDAO.list(String id) { return selectList(id, null); }` → `AbstractDAO.selectList`) 도 `foo.find` · `getSqlSession().selectList(queryId, p)` 꼴 조상도 · 지킴이 — 호출 자리 인자가 리터럴이 아니면(`selectList(makeId(), vo)`) 미해결 `statement` 가 **호출한 쪽** 파일·메서드로 한 건
- 의도된 골든 변화: 픽스처 골든 0 기대. corpus `analyze-*` 의 문장·CRUD 가 **느는 것**과 `dead-stmt`·`statement` 미해결이 **주는 것**(공통 DAO 가 있는 표본 — jeesite·ruoyi-classic·zheng 등) · `depth` 미해결이 새로 늘면 7절
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,AnalyzeStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): 감싸개 자체를 뿌리로 훑을 때(DAO 훑기)의 `statement` 미해결 한 줄은 남는다 · 매개변수를 변수에 옮겨 담아 넘기는 꼴

#### 6-87 — JspLinks: 한 줄의 따옴표 짝 밀림 · 긴 줄의 토큰 (C2-07 · C2-13)
- 대상: `core/analyze/JspLinks.java`(`matched` 의 후보 훑기 · `start`) · `JspLinksTest`
- 사실: `matched:167` 의 `QUOTED`(따옴표 글을 차례로 짝짓는다)가 `onsubmit="return chk('f')" action="/user/save"` 에서 밀려 `.do` 아닌 링크 `/user/save` 를 놓친다(`class="<c:if test='${on}'>on</c:if>"` 뒤 `href` 도) · `start:141` 이 경로 글자만 이어진 긴 줄에서 토큰마다 줄 머리까지 거슬러 URL 글자 합이 제곱(4만 자 한 줄 → 1.6억 자)
- 변경:
  1. `matched` — `QUOTED.matcher(text)` 훑기를 손 훑기로: 글의 `"`·`'` 자리마다 그 자리를 **여는 따옴표로 보고** 같은 줄의 다음 같은 따옴표 앞까지(1~200자, 안에 다른 따옴표·줄바꿈 없음)를 후보로 낸다. 후보의 시작 자리(지금 `m.start(2)`)와 글(지금 `m.group(2)`)을 쓰는 뒤 판정은 그대로. `QUOTED` 는 다른 쓰는 곳이 없으면 지운다
  2. `start` — 500자 넘게 거슬러 가면 그 토큰을 버린다(`start == dot` 을 돌려 `:142` 가 건너뛰게). 상수 `TOKEN_MAX = 500`
- 시험(먼저): `JspLinksTest` — 보고서 C2-07 의 세 줄(매핑 `/user/save`·`/user/list`·`/user/view`) → 각 링크 하나(전 `[]`) · 기존 `rootMappingNeedsACue`·6-51 사례 초록 · `"/a.do".repeat(8000)` 한 줄의 `extract` 가 `assertTimeout(2초)` 안이고 링크 URL 글자 합이 줄 길이의 100배를 안 넘는다
- 의도된 골든 변화: 픽스처 0. corpus 의 `.do` 아닌 링크가 **느는 것**만(zheng 0 기대)
- 검증: 6-85 와 같다. 의존: 6-85

#### 6-88 — 매핑 주석이 인터페이스·추상 조상 메서드에 있는 컨트롤러 (C2-05)
- 대상: `core/analyze/TypeBind.java`(새 `mapped`) · `core/analyze/JavaGraph.java`(`run` 의 주석 읽는 식) · `JavaGraphTest`
- 사실: `interface UserApi { @GetMapping("/users") Object list(); }` 를 구현한 `@RestController`(또는 추상 조상의 `@GetMapping` 추상 메서드를 재정의)는 — `TypeBind.handlers:76-87` 이 조상 클래스만 오르고 하위가 같은 이름·인자 수면 조상 것을 빼며, `JavaGraph.run:237-240` 은 하위 메서드에 주석이 없어 건너뛴다. 프로그램 0 · 미해결 0
- 변경: `TypeBind.mapped(JavaGraph.Cls c, MethodDeclaration m, …)` — `m` 에 매핑 주석이 있으면 `m`, 없으면 같은 이름·인자 수로 ① 조상 사다리(`parent`, `MAX_UP`) ② `c` 와 조상이 구현한 인터페이스(색인에 있는 것 — `JavaGraph.interfacesOf` 가 주는 목록을 인자로 받는다)의 메서드 가운데 매핑 주석이 있는 첫 것, 없으면 `m`. `JavaGraph.run` 은 매핑 주석을 읽는 자리에서만 `m` 대신 `TypeBind.mapped(…)` 의 것을 쓴다(몸·줄·이름은 `m`). **`run(2)` 는 기준선 31 — 식만 바꾼다**
- 시험(먼저): `JavaGraphTest.mappingDeclaredOnInterfaceOrAbstractParent` — 보고서 C2-05 의 두 꼴 → 프로그램 `GET /users` 하나씩(문장 `user.find`), 전 0 · 지킴이 — 하위 메서드에 주석이 있으면 그것 · 같은 URL 이 둘로 안 는다
- 의도된 골든 변화: 픽스처 0. corpus `analyze-*` 의 프로그램이 **느는 것**만(인터페이스 매핑 — egov-msa·shopizer)
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,AnalyzeStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-86(같은 파일)

#### 6-89 — JPQL: JOIN 뒤 쉼표로 이어지는 엔티티 (C2-06 · 기준선 낮춤)
- 대상: `core/analyze/JpaIndex.java`(`jpql:513`) · `core/analyze/JpqlPaths.java`(`resolve` 의 JOIN 가지) · `JpaIndexTest` · `self-check.txt`
- 사실: `select o from Order o join o.items i, Product p where …` 의 `Product` 와 `… , Customer c join c.addresses a` 의 둘이 미해결 없이 빠진다 — `jpql:513` 의 `&& !"JOIN".equals(u)` 가 JOIN 대상 뒤 쉼표를 안 잇고, `JpqlPaths.resolve:49-53` 은 JOIN 에서 `inFrom = false` 라 쉼표가 다음 항목을 안 연다
- 변경: `jpql:513` 조건에서 `&& !"JOIN".equals(u)` 를 뺀다 · `JpqlPaths.resolve` 의 `case "JOIN"` 에서 `inFrom = false;` → `inFrom = true;`(JOIN 은 FROM 절 안이다 — `ON`·`WHERE` 가 끝낸다)
- 시험(먼저): `JpaIndexTest.commaAfterJoin` — 위 두 JPQL → 표에 `T_PRODUCT`(전 없음) · `T_CUSTOMER`·`T_ADDRESS`(전 없음) · 지킴이 `from Order o, Product p join o.items i` 셋 그대로 · `where o.id in (1, 2)` 의 쉼표는 엔티티를 안 연다
- 의도된 골든 변화: `self-check.txt` 의 `JpaIndex … jpql(6)` 한 줄(40 → 39) · `jpa-index.json` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='JpaIndexTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-77(같은 파일)

#### 6-90 — JavaGraph 타입 풀기: 조상 필드는 선언한 클래스 기준 · 후보 하나여도 import 확인 (C2-09 · C2-10)
- 대상: `core/analyze/JavaGraph.java`(`lookup` · `walk:490` 의 인자 하나) · `core/analyze/TypeBind.java`(새 `declarer`) · `JavaGraphTest`
- 사실: `walk:490` 의 `resolve(c, …)` 가 조상이 선언한 필드의 타입을 **걷고 있는 하위 클래스**의 패키지·import 로 풀어(`resolve:633` `lookup(owner, f.type())`) 같은 이름의 다른 패키지 클래스를 잡는다(`a.BaseSvc` 의 `CmmDAO dao` 를 `b.FooSvc` 에서 `b.CmmDAO` 로) · `lookup:399` 은 후보가 하나면 import 를 안 봐, `import com.vendor.common.CodeService`(프로젝트 밖)인 필드가 이름만 같은 `app.legacy.CodeService` 로 풀린다(6-77 과 같은 꼴)
- 변경:
  1. `lookup` — 후보가 하나일 때: `from.imports` 에 그 단순 이름이 있고 그 FQCN 이 후보의 `fqcn` 과 다르면 빈 목록, 아니면 지금대로
  2. `TypeBind.declarer(JavaGraph.Cls c, String field, lookup)` — `c` 에서 조상으로 올라 그 필드를 선언한 클래스(없으면 `c`). `walk:490` 의 `resolve(c, fieldName, f, …)` → `resolve(TypeBind.declarer(c, fieldName, this::lookup), fieldName, f, …)`. **`walk` 에 분기를 더하지 않는다**
- 시험(먼저): `JavaGraphTest.fieldTypeResolvesWhereItIsDeclared` — 보고서 C2-09 입력 → `[A.load]`(전 `[B.load]`) · `importedOutsideClassIsNotTheProjectOne` — C2-10 입력 → 문장 없음(전 `[legacy.code.list]`) · 지킴이 — import 가 그 프로젝트 클래스면 그대로 · 같은 패키지(import 없음)면 그대로 · 중첩 클래스 import(`import a.Outer.Inner;`)가 색인의 `fqcn` 과 맞는다
- 의도된 골든 변화: 픽스처 0. corpus `analyze-*` 의 문장·표가 **주는 것**(import 가 프로젝트 밖 클래스 · 다른 패키지 같은 이름) — `R-37` 이 줄마다 import 를 본다. 넓게 빠지면(표본 하나에서 프로그램의 5% 넘게) 7절
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,AnalyzeStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-88

#### 6-91 — 곁 등록부 id: 오버로드를 가른다 (C2-11)
- 대상: `core/analyze/StmtId.java`(새 `side`) · `core/analyze/JavaGraph.java`(`jdbc:539` · `entityManager`·`querydsl` 의 id 식) · `JavaGraphTest`
- 사실: 곁 등록부 id 가 `클래스.메서드#jdbc`(`#em`·`#qdsl` 도) — 인자 수가 다른 오버로드 둘의 표가 한 id 에 합쳐져(`JpaIndex.record:174`) 부르지 않은 오버로드의 표가 CRUD 에 적힌다
- 변경: `StmtId.side(String fqcn, MethodDeclaration m, String tail)` — 그 메서드를 선언한 형식에 같은 이름 메서드가 **둘 넘으면** `fqcn + "." + 이름 + "/" + 인자 수 + tail`, 아니면 지금 꼴 `fqcn + "." + 이름 + tail`. 세 자리의 id 식을 이것으로. **`entityManager(4)` 19 · `querydsl(3)` 22 는 그대로 — 식만 바꾼다**. id 를 `#`·`.` 로 다시 쪼개는 자리(`StmtId` 의 꼬리 판정·`AnalyzeStore`·화면)가 `/숫자` 를 견디는지 `grep -n "#jdbc\|#em\|#qdsl" src/main` 로 보고, 쪼개는 자리가 있으면 같은 행에서 맞춘다
- 시험(먼저): `JavaGraphTest.overloadsKeepSeparateSideIds` — 보고서 C2-11 의 `ODao`(오버로드 둘, 프로그램은 `find(1L)` 만) → 그 프로그램의 표 `T_A` 만(전 `T_A`·`T_B`) · id 가 `web.ODao.find/1#jdbc` · 지킴이 — 오버로드 없는 메서드의 id 는 지금 꼴
- 의도된 골든 변화: 픽스처·corpus 골든의 곁 id 가운데 **오버로드 메서드의 것만** `/n` 이 붙는다(다른 id 는 0)
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,JpaIndexTest,AnalyzeStoreTest,AnalyzeStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-90

#### 6-92 — JavaViews: 완전 이름 `ModelAndView` · 빈 redirect (C2-14 · C2-15)
- 대상: `core/analyze/JavaViews.java`(`:37-38` 리턴 형식 비교 · `:83` 의 `out.add`) · `JavaGraphTest`(없으면 `JavaGraphTest`)
- 사실: 리턴 형식을 `org.springframework.web.servlet.ModelAndView` 로 적으면 `"ModelAndView".equals(rt)` 가 안 맞아 `kind=json`(화면 전수에서 빠지고 JSP 가 고아) · `"redirect:" + 변수` 가 이름 빈 redirect 뷰 한 줄로 저장된다
- 변경: 리턴 형식 글을 마지막 `.` 뒤 단순 이름으로 줄여 비교한다(도우미 `simple(String)`) · `:83` 의 `out.add(lead.toString())` 를 `addNamed(out, lead.toString())` 로 — `redirect:`·`forward:` 뒤가 비면 안 넣는다. **`value(4)` 는 기준선 18 — 분기를 더하지 않는다**
- 시험(먼저): 보고서 C2-14 메서드 → `kind=view`·`views=[view a/list]`(전 json·빈 목록) · C2-15 메서드 → 뷰 없음 + `viewDynamic` 미해결(전 이름 빈 redirect 뷰) · 지킴이 `return "redirect:/a.do";` 그대로
- 의도된 골든 변화: 픽스처 0. corpus 의 이름 빈 redirect 뷰가 **사라지는 것**과 `kind` 가 json → view 로 바뀌는 것만
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,JavaGraphTest,AnalyzeStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`

### 묶음 ⑥ 코드 정리 — 고침 (corpus 레인)

기준선: `Cleanup` `deleteConsole(4)` 19 · `worse(6)` 36 · `consoleEnd(3)` 17 · `rebuildClause(2)` 16 · `fixTsImports(4)` 17 / `CleanupRunner` `apply(10)`·`preview(8)`·`writeReport(8)`(인자 수 — 시그니처를 안 바꾼다) / `LocalFiles` `list(4)` 21 · `resultRoot(5)` 22.

#### 5-137 — 코드 정리: jsp·html 의 `<script>` 주석 · 스크립트 밖 본문 글 (E-01 — Critical · E-07 · E-13)
- 대상: `core/check/Cleanup.java`(`deleteDebugger`·`deleteConsole` 의 첫 줄과 구역 식) · `core/check/RegexRule.java`(새 `bare`) · `core/check/Zones.java`(새 `zones` · `reason` 오버로드) · `CleanupTest`
- 사실: 고침은 `RegexRule.stripComments(s.text(), s.lang())`(`:537`·`:568`)로 주석 뗀 글을 만드는데 jsp·html 꼴은 `<script>` 안 `//`·`/* */` 를 안 뗀다(판정은 `RegexRule.skipped` 가 `scriptComments` 로 뗀다 — 5-120). 그래서 `if (list.length > 100) // big only`⏎`debugger;` 에서 꼬리 주석이 「중괄호 없는 제어문 몸체」 가드(`BRACELESS`)를 가려 `debugger;` 줄이 지워지고 **다음 문장이 if 의 몸이 된다**(기본 켬 `common.debugger`·`common.consoleLog` — `} else // …`·`for (…) // …`·`if (x)`⏎`// was: foo();`⏎`console.log(x);` 도). 같은 뿌리의 안전한 쪽 — 주석 줄·꼬리 주석 뒤 console 줄을 못 지우고 사유가 틀린다. 마크업 꼴의 날글 판정은 `<pre>`·`<textarea>` 뿐이라 `<div class="code">` 안 예제 글의 `debugger;`·`console.log(…);` 줄도 지운다
- 변경:
  1. `RegexRule.bare(String text, String lang)`(public static) — jsp·jspf·tag·html·htm·xhtml 꼴이면 `scriptComments(stripComments(scriptWrappers(text), lang))`, 아니면 `stripComments(text, lang)`. `skipped` 도 이것을 쓴다(jsp 꼴은 그 위에 `blankCommentScriptlets`)
  2. `deleteDebugger:537`·`deleteConsole:568` — `String stripped = RegexRule.bare(s.text(), s.lang());`
  3. `Zones.zones(String text, String lang)` — 줄마다 `0` 코드 · `1` 날글(`rawLines` 가 참) · `2` 스크립트 밖 본문(마크업 꼴에서 `RegexRule.scriptBodies` 의 몸에 걸치지 않는 줄). 마크업 꼴이 아니면 전부 `0`. `Zones.reason(Js, int[] zones, int i, String word)` 오버로드 — `1` 이면 지금 글, `2` 면 「`<script>` 밖 글 — 마크업 본문」. `deleteDebugger`·`deleteConsole` 의 `boolean[] raw = Zones.rawLines(…)` 를 `int[] raw = Zones.zones(…)` 로(그 뒤 `Zones.reason(js, raw, i, …)` 줄은 글자 그대로). **`deleteConsole(4)` 는 기준선 19 — 분기를 더하지 않는다**
- 시험(먼저): `CleanupTest` — 보고서 E-01 의 `b1.jsp` → 글 불변 · 건너뜀 「줄 3: 중괄호 없는 제어문 몸체일 수 있음」(전 3행 삭제) · `} else // stop here`·`if (x) /* stop */`·html 의 `for (…) // each row` 도 건너뜀 · `if (x)`⏎`// was: foo();`⏎`console.log(x);`⏎`next();` → console 줄이 남는다 · E-07 의 `b2.jsp` → console 두 줄 삭제(전 둘 다 남음) · E-13 ① `b4.jsp`(`<div class="code">` 안) → 글 불변 · 건너뜀 「`<script>` 밖 글」(전 두 줄 삭제) · 지킴이 — 같은 글의 `.js` 는 지금과 같다 · JSP `<script>` 안 보통 `console.log` 줄은 지운다
- 의도된 골든 변화: 픽스처 `cleanup` 골든 0 기대. corpus `cleanup-*` — jsp·html 의 삭제가 **주는 것**(제어문 뒤 · 스크립트 밖)과 **느는 것**(주석 뒤 console) — 줄마다 어느 쪽인지 `R-37`
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,ZonesTest,CheckRulesTest,CleanupTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-128 · 5-129(같은 파일들). 순수본 밀린 목록 — 해당 없음(순수본에 코드 정리 없음)
- 한계(이력에): jsx·tsx 의 JSX 글(`<pre>` 안 코드 글) 줄은 여전히 지울 수 있다

#### 5-138 — ConsoleArgs: TS 제네릭 호출 · non-null 호출 (E-02 — Critical)
- 대상: `core/check/ConsoleArgs.java`(`call`) · `CleanupTest`(없으면 `CleanupTest`)
- 사실: `call:108-114` 는 `(` 앞 글자가 낱말·`)`·`]`·`?.` 일 때만 호출로 센다 — `console.log(q.take<number>());`(앞이 `>`)와 `console.log(cb!(1));`(앞이 `!`)이 부수 효과째 지워진다(기본 켬 `tsx.console`)
- 변경: `call` — `(` 앞 글자가 `>` 면 참(비교 `a > (b)` 가 같이 걸려 건너뛰는 것은 안전한 쪽) · `!` 이고 그 앞(공백 건너) 글자가 낱말 글자·`)`·`]` 면 참
- 시험(먼저): 보고서 E-02 의 `d1.ts`·`d1.tsx` → 2·3·4행 다 `SIDE_EFFECT` 건너뜀(전 2·3행 삭제) · 지킴이 `console.log(a > 1 ? x : y);`·`console.log(!flag);` 는 지금처럼 삭제 · `console.log(a > (b));` 는 건너뜀(새로 — 안전한 쪽)
- 의도된 골든 변화: 픽스처 0. corpus `cleanup-*` 의 console 삭제가 **주는 것**만
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupTest,SelfCheckTest'` → `bash scripts/verify.sh`

#### 5-139 — ValueFix: 눌린 줄은 안 고친다 · 패턴 변수 · 가려진 상수 이름 (E-03 · E-09 · E-10)
- 대상: `core/check/ValueFix.java`(`apply:72`) · `core/check/ValueAst.java`(`literalFirst:250` 의 상수 판정) · `core/check/DeclaredTypes.java`(이름의 선언 형식) · `ValueAstTest`
- 사실: `apply:72` 가 `ValueAst.apply(cu, targets)` 의 hit 를 지금 지적(`cur`)으로 안 걸러 `tb-ignore` 로 눌린 줄도 고친다 — 고친 줄에 `common.unusedIgnore` 가 새로 서 `worse` 가 그 파일의 값 고침을 통째로 되돌리고(사유 「다른 지적이 생김」), unusedIgnore 를 끈 프로필에서는 눌린 줄이 조용히 뒤집힌다(`SwitchFix:49`·`InterruptFix:55` 는 `JavaShape.flagged(cur, x)` 로 거른다) · `DeclaredTypes` 가 패턴 변수를 몰라 `o instanceof Key a` 의 `a == b` 를 같은 이름 필드(`Integer a`) 형식으로 읽고 `Objects.equals` 로 바꾼다 · `ValueAst.literalFirst:250` 은 인자 이름이 static final String 상수면 앞세우는데 그 이름이 매개변수·지역 변수로 가려졌는지 안 본다(`s.equals(KEY)` → `KEY.equals(s)` — 매개변수 `KEY` 가 null 이면 NPE)
- 변경:
  1. `apply` 루프 첫 줄 — hit 의 (시작 줄, 규칙)에 `cur` 의 지적이 없으면 건너뛴다(`JavaShape.flagged` 를 쓸 수 있으면 그것). `apply` 가 `cur` 를 안 받으면 인자로 받는다(`Cleanup.clean:175` 가 넘긴다)
  2. `DeclaredTypes` — 이름의 선언을 찾을 때 그 이름을 쓰는 자리를 감싼 callable 안에 같은 이름 패턴 변수(`TypePatternExpr`)가 있으면 「모름」(null)
  3. `ValueAst` 의 상수 판정 — 그 호출을 감싼 callable 에 같은 이름 매개변수나 지역 변수 선언이 있으면 상수가 아니다
- 시험(먼저): `ValueAstTest` — E-03 의 `P1.java` → 3행 그대로 · 4행만 `"b".equals(t)`(전 글 불변 + 「다른 지적이 생김」) · E-09 의 `C5.java` → 글 불변(전 `Objects.equals(a, b)`) · 필드 `String s` + `o instanceof Key s` + `s.equals("a")` 불변 · E-10 의 `P5.java` → 글 불변(전 `KEY.equals(s)`) · 지킴이 — 가려지지 않은 상수는 지금처럼 앞세운다
- 의도된 골든 변화: 픽스처 0. corpus `cleanup-*` 의 값 고침이 **주는 것**(가려진 이름)과 **느는 것**(눌린 줄과 같이 있던 파일)
- 검증: `bash scripts/mvn.sh -q test -Dtest='ValueAstTest,CleanupTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-136(`ValueFix` 가 쓰는 `ReturnTypes`)

#### 5-140 — LogFix: 로거보다 먼저 도는 초기화 — 같은 파일 형식의 static 호출 · 인스턴스 로거 (E-04 · E-05)
- 대상: `core/check/LogFix.java`(`earlyInit` · `usable:281`) · `CleanupTest`
- 사실: `earlyInit:275-276` 은 로거 앞 static 초기화의 호출을 「수신자 없음」·「수신자가 자기 형식 이름」 만 본다 — `static final Object HOLDER = Helper.create();`(같은 파일의 중첩 형식) 안의 `System.out.println` 을 `log.debug` 로 바꿔 클래스 로딩에서 null 로거로 죽는다 · `usable:281` 은 `lg.isStatic()` 일 때만 판정해, 인스턴스 로거 앞의 `private final String name = init();` 이 부르는 `init()` 안 출력도 바꾼다(`new C1()` 이 NPE)
- 변경: `earlyInit(TypeDeclaration<?> t, int before, boolean stat)` — `stat` 이면 지금 판정에 「수신자가 같은 파일에 선언된 형식 이름(`local`)인 호출」 을 더한다. `stat` 이 아니면 `before` 줄 앞의 **인스턴스** 필드 초기화식·인스턴스 초기화 블록에 수신자 없는 호출이나 `this.` 호출이 있으면 참. `usable:281` 의 `lg.isStatic() &&` 를 빼고 `earlyInit(t, p.line, lg.isStatic())` 로
- 시험(먼저): `CleanupTest` — E-04 의 `F4.java` → `EARLY_INIT` 건너뜀(전 `log.debug("create")`) · E-05 의 `C1.java` → 건너뜀(전 `log.debug("init")`) · 지킴이 — 로거가 첫 필드인 클래스는 지금처럼 바꾼다 · 로거 **뒤** 필드 초기화식이 부르는 메서드는 바꾼다
- 의도된 골든 변화: 픽스처 0. corpus `cleanup-*` 의 로거 고침이 **주는 것**만
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupTest,SelfCheckTest'` → `bash scripts/verify.sh`

#### 5-141 — MarkupFix: tsx 의 rel 은 target 값과 같은 따옴표 (E-06)
- 대상: `core/check/MarkupFix.java`(`addRel:187` · `TARGET`) · `CleanupTest`
- 사실: tsx 길 `addRel` 이 늘 `rel="…"` 를 끼워 `const h = "<a href='/x' target='_blank'>open</a>";` 의 문자열을 깨뜨린다(기본 켬 `tsx.blankNoopener` — 컴파일 안 되는 결과가 나간다). JSP 길(`rels:89`)은 값과 같은 따옴표를 쓴다
- 변경: `TARGET` 이 값의 여는 따옴표를 묶음으로 잡게 하고 — `'` 면 ` rel='noopener noreferrer'`, 그 밖(`"`·`{'_blank'}`·`{"_blank"}`)은 지금 `REL`
- 시험(먼저): E-06 의 `b3.tsx` → `target='_blank' rel='noopener noreferrer'`(전 큰따옴표 rel) · 지킴이 `<a target="_blank">` → 큰따옴표 rel · `target={'_blank'}` → 지금과 같다
- 의도된 골든 변화: 픽스처 0 · corpus `cleanup-egov-react*` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupTest,SelfCheckTest'` → `bash scripts/verify.sh`

#### 5-142 — SwitchFix: 뒤 case 의 지역 이름 · 꼬리 주석 (E-08 · E-14)
- 대상: `core/check/SwitchFix.java`(`refuse`) · `CleanupTest`
- 사실: `refuse:126` 은 `declares` 를 default 에만 물어, 뒤 case 가 선언한 지역 `n` 이 옮겨진 default 안의 `n = 9;`(필드 대입)를 가져간다(컴파일되고 뜻이 바뀐다) · `refuse:117-118` 의 `alone` 이 entry 끝 줄의 꼬리 주석(`break; // base`)을 「다른 코드」 로 봐 「case 가 줄을 나눠 쓴다」 로 건너뛴다(사유가 틀리고 고칠 수 있는 것을 안 고친다)
- 변경: ① default 뒤 entry 들이 문장 층에서 선언한 지역 변수 이름 가운데 default 의 `NameExpr`·대입 대상에 나오는 것이 있으면 `DECL` ② `alone` 의 끝 줄 뒤 글을 주석 뗀 줄(`RegexRule.stripComments(전체 글, "java")` 의 같은 줄)로 본다 — `refuse` 가 그 줄 배열을 받는다
- 시험(먼저): E-08 의 `P3.java` → 글 불변 · 사유 `DECL` 글(전 default 가 옮겨짐) · E-14 의 `P4.java` → default 가 `case 1` 뒤로 옮겨지고 `// base` 가 그 줄째 따라간다(전 「줄을 나눠 쓴다」) · 화살표 꼴 `default -> 0; // base` 도 · 지킴이 — 끝 줄에 다른 문장이 있으면 지금처럼 `SPLIT`
- 의도된 골든 변화: 픽스처 0. corpus `cleanup-*` 의 default 옮기기가 **느는 것**(꼬리 주석)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupTest,SelfCheckTest'` → `bash scripts/verify.sh`

#### 5-143 — PiiFix: sql·jsp·html 도 주석 뗀 줄로 · 매퍼 XML 의 맨 숫자 (E-11 · E-12)
- 대상: `core/check/PiiFix.java`(`apply:37-39` · `C_LIKE` 삭제) · `CleanupTest`
- 사실: 주석 뗀 줄로 따옴표를 훑는 것이 java·js 꼴뿐이라 `/* don't */ SELECT 4111111111111111 FROM dual;`(sql)·`<%-- it's --%> var n = 4111111111111111;`(jsp)의 맨 숫자를 「따옴표 안」 으로 보고 가린다(구문이 깨진다) · `FREE` 에 `xml` 이 있어 매퍼 XML 안 SQL 의 맨 숫자(`WHERE CARD_NO = 4111111111111111`)도 가린다
- 변경: `scan` 을 언어와 무관하게 `RegexRule.bare(text, lang).split("\n", -1)` 로(모르는 언어는 원문 그대로) · `free` 는 `FREE.contains(lang)` 이되 `xml` 이고 글에 `<mapper`·`<sqlMap` 이 있으면 거짓
- 시험(먼저): E-11 의 `C6.sql`·`f2.jsp` → `UNQUOTED` 건너뜀(전 가림) · E-12 의 `C3.xml` → `UNQUOTED` 건너뜀(전 가림) · 지킴이 — 설정 XML 의 `<value>4111111111111111</value>` 는 지금처럼 가린다 · sql 의 `'4111111111111111'` 은 가린다
- 의도된 골든 변화: 픽스처 0 · corpus 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-137(`RegexRule.bare`)

#### 5-144 — 줄끝: 통일 알림은 바뀌었을 때만 · `\r\r\n` 파일 (E-15 · E-17)
- 대상: `core/check/CleanupRunner.java`(`skipsOf`) · `core/fs/LocalFiles.java`(`mixedEol`) · `CleanupRunnerTest`·`LocalFilesTest`
- 사실: `skipsOf:74-81` 이 `r.changed()` 를 안 보고 섞인 파일이면 늘 「줄끝이 섞여 있었다 — 전부 통일했다」 를 붙인다(파일이 안 써졌는데도) · 줄끝이 `\r\r\n` 인 파일은 `LocalFiles.read:362` 의 `replace("\r\n", "\n")` 뒤 줄마다 `\r` 이 남는데 `mixed` 가 아니라, 다른 줄을 고쳐 쓰면 안 고친 줄의 `\r` 하나가 사라진다
- 변경: `skipsOf` — `!t.mixed() || !r.changed()` 면 `r.skips()` 그대로 · `mixedEol(text)` — 글에 `\r\r\n` 이 있으면 참(섞인 파일로 다룬다 — 고침은 5-116 의 섞인 줄끝 처분을 탄다). **`preview(8)`·`apply(10)` 의 시그니처를 안 바꾼다**
- 시험(먼저): `CleanupRunnerTest` — 다수 LF 인 섞인 `a.js`(프로필 LF, 기본 규칙, `console.warn` 한 줄) 미리보기 줄에 「통일했다」 가 없다(전 있음) · `LocalFilesTest` — 바이트 `x\r\r\ny\r\r\n` 의 `read(…).mixed()` 가 참(전 거짓) · 지킴이 — 순수 CRLF·LF 파일은 거짓
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupRunnerTest,LocalFilesTest,FsRoutesTest,SmokeHtmlUnitTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에 · 새 행 후보): 다수 쪽이 프로필과 같은 섞인 파일은 `file.lineEnding` 지적이 없어 그 규칙만 골라서는 통일되지 않는다(`Source` 에 섞임 표시가 없다)

#### 5-145 — EncodingFix: 남의 `charset=` 속성은 이 파일의 선언이 아니다 (E-16)
- 대상: `core/check/EncodingFix.java`(`DECL`) · `CleanupTest`
- 사실: `DECL:25-26` 의 `charset\s*=\s*["']?` 가 `<script src="/js/old.js" charset="euc-kr">`·`accept-charset="…"` 도 그 파일의 선언으로 읽어 「파일 안 인코딩 선언이 프로필과 다르다」 로 거절한다(기본 켬 `file.encoding` 이 안 듣는다)
- 변경: `charset=` 대안을 세 문맥으로 좁힌다 — `contentType\s*=\s*["'][^"']*?charset\s*=\s*` · `<meta\b[^>]*?charset\s*=\s*["']?` · `setContentType\s*\(\s*"[^"]*?charset\s*=\s*`. `pageEncoding`·`@charset`·`<?xml … encoding` 대안은 그대로
- 시험(먼저): E-16 의 `f1.jsp`(MS949 로 읽음, 프로필 UTF-8) → 변환된다(전 거절) · 지킴이 — `<%@ page contentType="text/html; charset=EUC-KR" %>` 는 거절 · `<meta charset="euc-kr">`·`<meta http-equiv="Content-Type" content="text/html; charset=euc-kr">` 거절 · `<form accept-charset="euc-kr">` 는 선언이 아니다
- 의도된 골든 변화: 픽스처 0. corpus `cleanup-*` 의 인코딩 변환이 **느는 것**만
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupTest,SelfCheckTest'` → `bash scripts/verify.sh`

### 묶음 ⑦ 산출물 · 보고 · 논리명 (java·db 레인)

기준선: `XlsxFiller` `fill(4)` 25·중첩 5 / `DeliverableService` `build(8)` 22 · `writeGuide(11)` / `Relations` `infer(2)` 26 · `named(2)` 19 / `Definitions` 메서드 34 / `CodeAndLink` `codeCandidates(1)` 29 / `LogicalRoutes` `register(4)` 19·길이 104·중첩 5. `Definitions` 에 메서드를 안 더한다. `build`·`writeGuide` 의 인자 수를 안 바꾼다.

#### 2-52 — 양식 채우기 수식: 다시 계산 표시 · 데이터 행 위 수식 · 단일 칸 배열 수식 (B-01 · B-02 · B-04 · 기준선 낮춤)
- 대상: `core/report/FormulaRange.java`(새 `grow` · `widen` · `widened`) · `core/report/XlsxFiller.java`(`fill:98-101`) · `XlsxFillerTest` · `self-check.txt`
- 사실: `widen:47` 은 범위를 넓힌 수식이 있을 때만 다시 계산 표시를 켠다 — 안 넓힌 수식(`D5 = B3*2`)과 값이 한 행일 때의 `SUM(B3:B3)` 은 양식의 낡은 캐시 값(0)을 달고 나간다(캐시를 읽는 쪽에서 합계가 0) · `widen:34` 는 밀린 행만 훑어 제목부의 `COUNTA(A3:A3)`(「총 n건」)이 첫 행 범위 그대로 · 아래 행이 없는 양식은 `fill:98` 조건 때문에 `widen` 을 아예 안 부른다 · 단일 칸 배열 수식은 `widened:70` 의 `setCellFormula` 가 배열 표시를 지워 `#VALUE!` 가 된다(알림 없음)
- 변경:
  1. `FormulaRange.grow(XSSFWorkbook wb, Sheet sheet, int first, int n)` — ① `n > 1` 이고 아래 행이 있으면 `sheet.shiftRows(first + 1, sheet.getLastRowNum(), n - 1)` ② `n > 1` 이면 `widen`(아래) ③ 시트에 수식 칸이 하나라도 있으면 `sheet.setForceFormulaRecalculation(true)` ④ `widen` 의 값(못 넓힌 배열 수식 수)을 돌려준다. `fill:97-101` 의 `int kept = 0; if (…) { shiftRows; kept = widen }` 를 `int kept = FormulaRange.grow((XSSFWorkbook) wb, sheet, first, n);` 한 줄로
  2. `widen` — 훑는 행을 「데이터 행(`first` ~ `first + n - 1`) 밖 전부」 로(위쪽 `0 ~ first - 1` 과 아래쪽 `first + n ~ 끝`). `if (any) { setForceFormulaRecalculation }` 는 지운다(`grow` 가 한다)
  3. `widened` — 고친 글을 넣기 전에 `c.isPartOfArrayFormulaGroup()` 을 본다: 그 묶음이 한 칸이면 `removeArrayFormula(c)` 뒤 `setArrayFormula(넓힌 글, 같은 범위)` 로 다시 넣고 1, 여러 칸이면 `KEPT`(지금 예외를 기다리는 `try` 는 그대로 두되 먼저 가른다)
- 시험(먼저): `XlsxFillerTest`(메모리 양식) — 보고서 B-01 양식 + 값 셋 → 결과 시트 `getForceFormulaRecalculation()` 참(전 거짓) · 값 한 행 + `SUM(B3:B3)` 양식도 참 · B-02 양식 → A1 `COUNTA(A3:A5)`(전 `A3:A3`) · 아래 행이 없는 양식의 A1 도 넓어진다 · B-04 양식 → `D6` 이 배열 수식 그대로(`isPartOfArrayFormulaGroup()` 참)이고 글 `SUM(B3:B5*C3:C5)` · 지킴이 — 다중 칸 배열 수식은 지금처럼 그대로 + 알림 하나 · 다른 시트 참조·단일 칸 참조는 안 넓힌다
- 의도된 골든 변화: `self-check.txt` 의 `XlsxFiller … fill(4)` 복잡도 한 줄(25 → 내려감) · 산출물 골든(값 비교)은 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='XlsxFillerTest,DeliverableServiceTest,StructureLintTest,SelfCheckTest'` → `bash scripts/verify.sh`

#### 2-53 — xlsx 칸 상한: 같은 이름 표 알림 · 긴 CHECK·코멘트 (B-03 · B-14)
- 대상: `core/deliverable/Relations.java`(`notes`) · `core/report/XlsxFiller.java`(`fill:139` 의 식) · `core/report/XlsxWriter.java`(`sheet:88`) · `RelationsTest`·`XlsxFillerTest`
- 사실: `Relations.notes:166` 이 같은 이름 표를 전부 한 칸에 이어 붙여 2,400 개면 33,642자 — `XlsxWriter.sheet:88` 의 `setCellValue` 가 엑셀 칸 상한(32,767자) 예외를 내 `00_작성안내.xlsx` 없이 작업이 FAILED(정의서는 이미 써진 뒤) · 스냅샷은 코멘트·기본값·CHECK 를 100만 자까지 담는데(V017) `XlsxFiller.fill:139` 는 32,767자 넘는 칸에서 같은 예외로 멈춘다
- 변경:
  1. `notes` — 이름이 20개를 넘으면 앞 20개 + ` 외 <n>개`
  2. `XlsxFiller` — `cell.setCellValue(String.valueOf(v))` → `cell.setCellValue(clip(String.valueOf(v), notes, 열 머리))` — `private static String clip(…)`: 32,767자(`SpreadsheetVersion.EXCEL2007.getMaxTextLength()`)를 넘으면 그 길이로 자르고 알림 목록(2-47 통로 — `fill` 이 돌려주는 목록)에 「<열> 칸이 32,767자를 넘어 잘랐다」 를 한 번(열마다) 더한다. **`fill(4)` 에 분기를 더하지 않는다**
  3. `XlsxWriter.sheet:88` — 같은 상한에서 자른다(끝 세 글자를 `…` 로)
- 시험(먼저): `RelationsTest` — 스키마 둘에 같은 이름 표 2,400 → 알림 글이 1,000자 안이고 「외 2380개」 로 끝난다(전 33,642자) · `XlsxFillerTest` — CHECK 조건 40,000자 문서 → 예외 없이 써지고 그 칸 길이 32,767 · 알림 하나(전 `IllegalArgumentException`) · `XlsxWriterTest` — 40,000자 칸이 써진다
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='RelationsTest,XlsxFillerTest,XlsxWriterTest,DeliverableServiceTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 2-52(같은 파일)

#### 2-54 — 08 코드값 SELECT: 코드 컬럼 빈칸 · 예약어 이름 (B-05 · B-09)
- 대상: `core/deliverable/CodeAndLink.java`(`codeSql` · 새 `quote` · `:132` 호출) · `CodeAndLinkTest`
- 사실: 코드 컬럼이 비고 정렬 컬럼이 있으면 `codeSql:101-109` 이 코드 열 없는 SELECT 를 만들고 `codeDoc:140-141` 이 `IndexOutOfBoundsException`(미리보기 500 · 작업 실패 「Index 1 out of bounds」) · 08 의 SELECT 만 예약어 이름(`DESC`·`ORDER` — 후보 추측이 고른다)을 안 감싼다(`QualitySql.id`·`Masking.id`·`DdlGen.quoted` 는 감싼다)
- 변경: `codeSql(CodeTable t, String product)` — 첫 줄에 `ident(t.codeCol())`(빈 값이면 지금 글의 `IllegalArgumentException` → 라우트가 400) · 이름마다 `quote(ident(이름), product)` — `DdlGen.reserved(이름)` 이면 감싼다(이름은 받은 글자 그대로): `product` 소문자에 `mysql`·`maria` → 백틱, `sql server`·`sybase`·`adaptive` → `[ ]`, 그 밖 → `"`. `:132` 는 `conn.getMetaData().getDatabaseProductName()` 을 넘긴다
- 시험(먼저): `codeSql(CodeTable("PUBLIC","CMM_CD",null,null,null,"CD_NM",null,null,"SORT_ORD"), "H2")` 가 `IllegalArgumentException`(전 코드 열 없는 SELECT) · `CodeTable("APP","CMM_CODE",null,null,"CODE","NAME","DESC",null,"ORDER")` → Oracle `SELECT CODE, NAME, "DESC" FROM APP.CMM_CODE ORDER BY "ORDER"` · MariaDB 백틱 · SQL Server 대괄호 · 지킴이 — 예약어 아닌 이름은 지금 글자 그대로. 라우트 — `POST /api/deliverable/codes/rows`(codeCol null) 400
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='CodeAndLinkTest,DeliverableRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`

#### 2-55 — 03 「제약조건」: 형 변환 낱말 · 한글 이름 경계 (B-08)
- 대상: `core/deliverable/Definitions.java`(`constraints:359` 의 정규식) · `DefinitionsTest`
- 사실: 낱말 경계 `(?<![\w$#])…(?![\w$#])` 가 PG `::text` 의 `text` 에 맞아 컬럼 `text` 에 남의 CHECK 가 붙고, `\w` 가 한글을 낱말로 안 봐 컬럼 `이름` 에 `사용자이름 IS NOT NULL` 이 붙는다
- 변경: 경계를 `(?<![\p{L}\p{N}_$#:])` … `(?![\p{L}\p{N}_$#])` 로(앞 글자가 `:` 인 맞춤 — 형 변환 낱말 — 도 뺀다). 메서드를 더하지 않는다
- 시험(먼저): 보고서 B-08 ①(컬럼 `status`·`text`·`date` + PG 꼴 CHECK) → `constraints(t, text)` 빈 글(전 그 CHECK) · `status` 는 그 CHECK · ② 컬럼 `이름`·`사용자이름` → `이름` 빈 글
- 의도된 골든 변화: `definitions*` 골든 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='DefinitionsTest,DeliverableServiceTest,SelfCheckTest'` → `bash scripts/verify.sh`

#### 2-56 — 산출물 쓰기 전 점검: 양식·`file`·겹침·크기 (B-12 · B-13 · B-16)
- 대상: `core/deliverable/DeliverableService.java`(`precheck` · 그 호출) · `core/report/XlsxFiller.java`(새 `check`) · `DeliverableServiceTest`
- 사실: `precheck:303-313` 은 매핑이 있는지만 본다 — 양식 파일 없음·시트 없음·머리 오타는 앞 문서를 쓴 뒤에 터져 반쯤 쓴 폴더가 남고, 매핑에 `file:` 이 없으면 `inside:326` 이 `NullPointerException` · 두 문서가 같은 `file` 이면 뒤 문서가 앞 문서를 조용히 덮는다 · 03 이 30만 행이면 XSSF 가 기본 힙을 넘어 사유 「OutOfMemoryError」 뿐(점검자 실측 2만 행 × 25칸 = 344MB)
- 변경: `precheck(order, docs, mapping, skipped, templateDir)` — 만들 문서마다 ① `m.file()` 이 비면 `IllegalArgumentException("매핑 " + no + " 에 file 이 없다")` ② 고른 문서끼리 `file` 이 겹치면(대소문자 무시) 「매핑 <a>·<b> 가 같은 file 을 가리킨다」 ③ `templateDir` 가 있으면 `XlsxFiller.check(inside(templateDir, m.file()), m)` — 새 static: 양식을 열어 `fill:69-75` 와 같은 시트·머리 검사만 하고 닫는다(같은 예외 글) ④ `행 수 × 열 수 × 700` 바이트가 `Runtime.getRuntime().maxMemory() * 0.6` 을 넘으면 「<no> 문서가 너무 크다(행 <n>) — 스키마를 나눠 만든다」. `build` 의 호출에 `templateDir` 를 넘긴다(인자 수는 `precheck` 만 는다)
- 시험(먼저): `DeliverableServiceTest` — 양식 폴더에 01~03 만 두고 01~04 를 고르면 **아무 파일도 안 쓰고** 예외(전 01~03 을 쓴 뒤 실패) · `file` 없는 매핑 → 「file 이 없다」(전 NPE) · 같은 `file` 둘 → 거절(전 DONE + 덮어씀) · 머리 오타 양식 → 쓰기 전 예외 · 지킴이 — 내장 양식(`templateDir` null)은 지금과 같다
- 의도된 골든 변화: 없음 · `self-check.txt` 0(`build(8)` 22 그대로)
- 검증: `bash scripts/mvn.sh -q test -Dtest='DeliverableServiceTest,XlsxFillerTest,DeliverableRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 2-53

#### 3-25 — 마스킹·이름 인용: 표 이름 빈 행 · 뷰 · `#` 든 이름 · 한글 이름 · 안 맞는 제외 키 (B-06 · B-07 · B-10 · B-11 · B-15)
- 대상: `core/logical/Masking.java`(`sql` · `id` · `PLAIN`) · `core/quality/QualitySql.java`(`id`) · `web/LogicalRoutes.java`(마스킹 라우트의 도우미 — `excluder` 둘레 · 스냅샷 입력) · `MaskingTest`·`QualitySqlTest`·`LogicalRoutesTest`
- 사실: 표 이름이 빈 행(컬럼만 CSV)에 마스킹은 `UPDATE "" SET` 을 200 으로 낸다(`CommentDdl.require:42` 는 같은 입력을 400 — 3-22 대 3-19) · 뷰의 컬럼도 후보가 돼 `UPDATE <뷰>` 가 나간다(`ColumnInputs.fromSchemas:82` 가 표 종류를 안 본다) · 「맨 이름」 판정(`Masking.PLAIN:68`·`QualitySql.IDENT:36`)이 방언을 안 봐 `JUMIN#NO` 가 MariaDB·PostgreSQL 에서 안 감싸인다(MariaDB 는 `#` 뒤가 주석) · 한글 이름은 맨 이름이 아니라 받은 글자 그대로 감싸 Oracle·Tibero 에서 소문자 섞인 CSV 입력(`고객_tb`)이 다른 객체를 가리킨다(번들 88 전에는 안 감쌌다) · 후보에 안 맞는 제외 키(`table` 에 `APP.TB_USER`)를 조용히 버려 뺐다고 믿은 컬럼이 SQL 에 남는다
- 변경:
  1. `Masking.sql` — 후보를 모으는 루프에서 표 이름이 빈 것이 있으면 `IllegalArgumentException`(`CommentDdl.require` 와 같은 글 — 그 글을 `Masking` 의 package-private 상수로 올려 둘이 쓴다)
  2. `Masking.id` — 맨 이름 판정을 `plain(name, d)` 로: `^[\p{L}_][\p{L}\p{N}_$#]*$` 에 맞고, `#` 가 있으면 방언이 ORACLE·TIBERO·MSSQL·SYBASE 일 때만 참. (한글 이름은 맨 이름 — 예약어가 아니면 안 감싼다.) `QualitySql.id` 도 감싸는 조건에 「`#` 가 있고 방언 키가 `mysql`·`pg`(그 밖 `#` 를 못 받는 키)」 를 더한다 — 그때는 예약어와 같은 꼴로 감싼다(글자는 그대로)
  3. `LogicalRoutes` — 스냅샷에서 마스킹 입력을 만들 때 `type` 이 `VIEW` 인 표를 빼고 그 수를 센다 · `excluder` 가 `table` 에 점이 있고 `owner` 가 비었으면 마지막 점에서 `owner`·`table` 로 가른다 · 후보를 다 본 뒤 한 번도 안 맞은 제외 키를 모은다. 뷰 수와 안 맞은 키는 생성 SQL 머리(지금 `-- 방언 …` 줄 다음)에 `-- [알림] 뷰 <n>개의 컬럼은 뺐다(UPDATE 대상이 아니다)` · `-- [알림] 제외 <n>개가 후보에 없다: <키 …>` 줄로 싣는다(`Masking.sql` 에 `List<String> notices` 를 받는 오버로드). **`register(4)` 에 줄·분기를 더하지 않는다**(도우미 메서드에서)
- 시험(먼저): `MaskingTest` — 표 이름 빈 후보 → `IllegalArgumentException`(전 `UPDATE "" SET`) · `id("JUMIN#NO", MARIADB)` 백틱 · `(…, POSTGRESQL)` 큰따옴표 · `(…, ORACLE)` 맨 이름 · `id("고객_tb", ORACLE)` = `고객_tb`(전 `"고객_tb"`) · `id("USER", ORACLE)` 는 지금처럼 `"USER"` · `QualitySqlTest` — `render("nullish", "mysql", "shop", "tb_mber", "JUMIN#NO", …)` 에 `` `JUMIN#NO` `` · `LogicalRoutesTest` — 뷰가 든 스냅샷의 마스킹 응답 SQL 에 `UPDATE … V_MBER` 가 없고 `[알림] 뷰 1개` 가 있다 · `exclude: [{table: "APP.TB_USER", col: "USER_NM"}]` 가 먹는다(전 안 먹음) · 없는 컬럼을 빼면 `[알림] 제외 1개가 후보에 없다`
- 의도된 골든 변화: `masking-*`·`comment-ddl*` 골든 — 한글 이름의 따옴표가 **사라지는 것**만(픽스처에 있으면) · corpus `masking-*` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='MaskingTest,CommentDdlTest,QualitySqlTest,LogicalRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`. 레인 db(QualitySql)

### 묶음 ⑧ DB 경계 (db 레인)

기준선: `SnapshotStore` `constraint(12)` · `Table(12)` · `MetaRoutes` `register(5)` 17 — 인자 수·분기를 안 바꾼다.

#### 9-10 — ReadOnlyJdbc: 홀로 선 CR · 숫자에 붙은 낱말 · `ADD`·`DIALOG` · 첫 낱말 끝 (F-01 · F-02 · F-03 — Critical, 절대 규칙 6 · F-08)
- 대상: `core/conn/ReadOnlyJdbc.java`(`check` · `firstWord` · `words` · `WRITE` · 새 `loneCr`·`glued`) · `ReadOnlyJdbcTest`
- 사실(내 코드 대조 + 점검자 H2 실행):
  - F-01 — `firstWord:114-116` 이 `--` 줄 주석을 `\n` 에서만 닫는다. `"--\rCOMMENT ON TABLE T IS '…' /*\nSELECT 1 */"` 는 래퍼가 SELECT 로 읽고 H2 는 COMMENT 를 실행한다(`\r` 로 줄을 닫는 DB — H2·PostgreSQL·SQL Server). 거꾸로 `\n` 에서만 닫는 DB(MySQL)에는 `\r` 로 닫는 판정이 반대쪽 구멍이 된다
  - F-02 — `wordEnd:158-161` 이 숫자 토막을 숫자·`.` 까지만 끊어 `1e5INTO`·`0x1UPDATE`·`1e0FOR` 의 금지 낱말이 `E5INTO`·`X1UPDATE`·`E0FOR` 한 낱말로 가려진다(MySQL 은 `1e5` 뒤를 새 토큰으로 읽는다 — `SELECT 1e5INTO OUTFILE …`)
  - F-03 — `;` 없이 이어지는 SQL Server 문장 `ADD SENSITIVITY CLASSIFICATION`·`ADD SIGNATURE`·`BEGIN DIALOG` 에는 `WRITE:37-43` 낱말이 하나도 없다
  - F-08 — `firstWord:124` 가 ASCII 글자에서 끝나 `SELECT_X 1`·`WITH_X …` 를 SELECT·WITH 로 받는다(`words` 는 `SELECT_X` 한 낱말 — 두 판정이 어긋난다)
- 변경:
  1. `check` — `comments(raw)` 앞에 `if (loneCr(raw)) { throw refuse("홀로 선 CR(줄 끝이 DB 마다 다르다)"); }` — `loneCr` = 뒤에 `\n` 이 안 따르는 `\r` 이 있다. 줄 주석 끝을 어느 쪽으로 읽어도 같아지게 그 글자 자체를 거절한다(`firstWord` 의 `\n` 판정은 그대로)
  2. `words` — 숫자로 시작한 토막 바로 뒤(공백 없이)에 온 글자 토막은 `glued(w)` 로도 잰다: 그 토막의 **모든 꼬리**(둘째 글자부터, 셋째 글자부터 …)가 `WRITE` 에 있으면 그 낱말로 거절, 꼬리가 `FOR` 면 `seenFor`, `seenFor` 뒤 꼬리가 `SHARE` 면 거절. 렉서가 지수·16진을 어디서 끊든 같다
  3. `WRITE` 에 `"ADD"`, `"DIALOG"`(주석 「F-03 — ADD SENSITIVITY·ADD SIGNATURE·BEGIN DIALOG」)
  4. `firstWord` — 낱말 끝을 `wordEnd` 와 같은 낱말 글자(글자·숫자·`_`, 비 ASCII 글자 포함)로 재고 그 통째가 SELECT·WITH 인지 본다
- 시험(먼저): `ReadOnlyJdbcTest` — 거절: `"--\rCOMMENT ON TABLE T IS 'x' /*\nSELECT 1 */"` · `"SELECT 1\r FROM T"`(홀로 선 CR) · `SELECT 1e5INTO T2 FROM T` · `SELECT 1.e5INTO T2 FROM T` · `SELECT 0x1UPDATE T SET A = 2` · `SELECT A FROM T WHERE A=1e0FOR SHARE` · `SELECT 1e5DELETE FROM T` · `SELECT 1 ADD SENSITIVITY CLASSIFICATION TO dbo.T.A WITH (LABEL = 'x')` · `SELECT 1 DECLARE @h UNIQUEIDENTIFIER BEGIN DIALOG @h FROM SERVICE [s] TO SERVICE 't'` · `SELECT_X 1` · `WITH_X 'a'`(전 — 전부 지나감). `stillPasses` 에 — `SELECT 1e5, 0x1F FROM T`(지금 있는 사례 그대로) · `"SELECT 1\r\nFROM T"`(CRLF) · `SELECT DATEADD(day, 1, D), ADD_MONTHS(D, 1) FROM T` · `SELECT 0xFEED FROM T`. H2 끝에서 끝 — 첫 꼴을 `wrap(h2).createStatement().execute` 에 주면 `SQLFeatureNotSupportedException` 이고 표 코멘트가 그대로다
- 의도된 골든 변화: 없음. `V-70` — DB 대조의 `SKIP_REFUSED` 가 **느는 것**(원문에 `ADD`·`DIALOG` 낱말이 든 문장)은 허용, 수를 이력에
- 검증: `bash scripts/mvn.sh -q test -Dtest='ReadOnlyJdbcTest,SqlProbeTest,ArchitectureTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): `SELECT 1 SET …`·`USE …`(설계 69 §3-6 [고정] — 세션 설정이고 데이터 쓰기가 아니다) · 부작용 함수(후보 ⑪) · 리터럴이 통째 `ADD` 인 문장은 거절된다(엄격한 쪽)

#### 1-126 — 메타 수집: PostgreSQL 파티션 부모 표 (F-04 — Critical)
- 대상: `core/meta/JdbcMetaSource.java`(`listTables:108` · 새 `tableTypes`) · `JdbcMetaSourcePostgresTest`(db 레인)
- 사실: `listTables:108` 이 종류를 `{"TABLE", "VIEW"}` 로만 묻는다. pgjdbc 는 선언적 파티션의 부모를 `PARTITIONED TABLE`(`relkind = 'p'`)로 따로 내 — 부모 표 `orders` 없이 자식 파티션(`orders_2024_01` …)만 스냅샷에 실린다(경고 없음). `PostgresMetaSource:21·26·29` 는 `relkind IN ('r','p',…)` 로 부모의 코멘트·통계를 읽을 채비가 돼 있다
- 변경: `private static String[] tableTypes(DatabaseMetaData md)` — `md.getTableTypes()` 에 `PARTITIONED TABLE` 이 있으면 `{"TABLE", "PARTITIONED TABLE", "VIEW"}`, 없으면 지금 둘(못 물으면 지금 둘). `listTables` 가 이것을 넘기고, 읽은 종류 글이 `PARTITIONED TABLE` 이면 표의 종류는 `TABLE` 로 적는다. 자식 파티션은 지금처럼 표로 싣는다(실제 표다 — 빼지 않는다)
- 시험(먼저): `JdbcMetaSourcePostgresTest.partitionedParentIsListed`(`@Tag("db")` — PG 컨테이너) — `CREATE TABLE t91_orders (id int, dt date) PARTITION BY RANGE (dt)` + 파티션 둘 → 표 목록에 `t91_orders`(종류 TABLE, 컬럼 둘)와 파티션 둘(전 파티션 둘뿐). H2 단위 — `tableTypes` 가 그 종류가 없는 드라이버에서 지금 둘을 준다(지킴이)
- Ruling: RED·GREEN 은 `--db`(`V-70`)에서 본다 — 빠른 verify 는 컴파일과 H2 지킴이까지. 행을 닫을 때 이력에 「db 레인 대기」 라 적고 `V-70` 이 채운다
- 의도된 골든 변화: PG corpus 골든(`--db`)에 파티션 표가 있으면 부모가 **는다** — 없으면 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='JdbcMetaSourceWildcardTest,SnapshotServiceTest,SelfCheckTest'` → `bash scripts/verify.sh`

#### 1-127 — 스냅샷 저장: 긴 제품 버전 글 (F-05)
- 대상: `core/meta/SnapshotStore.java`(`insertSnapshot:114` · 새 `version`) · `SnapshotStoreTest`
- 사실: `snapshot.db_version` 은 `VARCHAR(200)`(V001 — V017 이 안 넓힌 칸)인데 `JdbcMetaSource.dbVersion:85` 는 제품 버전 글을 자르지 않는다 — 201자 넘는 배너(여러 줄 Oracle EE 배너 218자)면 수집을 다 끝낸 뒤 저장이 22001 로 통째 실패하고 피할 길이 없다
- 변경: `ps.setString(4, version(version))` — `private static String version(String v)`: null 이면 null, 첫 줄(`\r`·`\n` 앞)만, 200자 넘으면 200자로. 마이그레이션은 안 더한다
- 시험(먼저): 218자 세 줄 버전 글의 스냅샷 `save` 가 성공하고 저장된 값이 첫 줄(전 `SQLException` 22001) · 지킴이 — 짧은 글은 그대로
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='SnapshotStoreTest,SnapshotServiceTest,SelfCheckTest'` → `bash scripts/verify.sh`

#### 1-128 — 비교와 반영 DDL 의 짝짓기: FK 기본 규칙 · 컬럼·제약 이름 대소문자 (F-06 · F-07)
- 대상: `core/meta/FkRule.java`(새 `folded`) · `core/meta/SnapshotDiff.java`(`rule` · 컬럼·제약 짝짓기 열쇠) · `core/gen/AlterGen.java`(`Run.fkShape:777`) · `SnapshotDiffTest`·`AlterGenTest`
- 사실: FK 기본 규칙(모름·NO ACTION·RESTRICT) 접기가 `SnapshotDiff.rule:165` 에만 있고 `AlterGen.fkShape:777-780` 에는 없어 — 같은 두 스냅샷에 화면 비교는 「차이 없음」, 반영 DDL 은 모든 FK 를 `DROP`·`ADD` 한다(규칙을 모르던 옛 스냅샷·드라이버가 바뀐 스냅샷과 견줄 때) · `SnapshotDiff` 는 컬럼·제약 이름을 대소문자 그대로 짝짓고(`:92-93`·`:199-200`) `AlterGen` 은 대문자로 접어(`:718-722`) — `UserId` 대 `USERID` 에 비교는 「삭제 + 추가」, 반영 DDL 은 문장 0
- 변경:
  1. `FkRule.folded(FkRule r)`(static) — null·NO_ACTION·RESTRICT 면 빈 글, 아니면 `label`. `SnapshotDiff.rule` 이 이것을 쓰고(글은 그대로), `fkShape` 의 `FkRule.label(…)` 둘을 `FkRule.folded(…)` 로. `Run` 에 메서드를 더하지 않는다
  2. `SnapshotDiff` 의 컬럼·제약 짝짓기 열쇠(`byName(…, Column::name)` 과 제약 쪽)를 대문자로 접은 이름으로 — `AlterGen` 과 같은 규칙. 표 열쇠(`index`)는 그대로. 보여 주는 이름은 B 쪽(없으면 A 쪽) 글자 그대로
- 시험(먼저): `AlterGenTest.defaultFkRulesAreTheSame` — a `RESTRICT/RESTRICT`(또 `null/null`) · b `NO ACTION/NO ACTION` → 문장 0(전 DROP + ADD) · 지킴이 — a `NO ACTION` · b `CASCADE` 는 지금처럼 DROP + ADD · `SnapshotDiffTest.columnCaseDoesNotSplit` — `UserId` 대 `USERID` → 추가·삭제 없음(전 각 하나) · 지킴이 — 이름이 다른 컬럼은 지금처럼
- 의도된 골든 변화: `alter-*.sql`·`snapshot-diff*` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='SnapshotDiffTest,AlterGenTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-118(같은 파일 `AlterGen`)
- 한계(이력에): 따옴표로 만든 대소문자만 다른 두 컬럼(Oracle·PostgreSQL)은 한 컬럼으로 짝지어진다

#### 3-26 — 사전 올리기: 공통표준단어 칸 길이 · 줄 번호 글 (F-09 · F-10)
- 대상: `core/dict/DictStore.java`(`insertMoiWords:230-248` · `orgPairs:317` · 상수) · `DictStoreTest`
- 사실: 3-21 의 길이 검사가 `putUser`·`importOrg` 에만 있어 공통표준단어 파일 올리기(`importMoiFile`)는 칸보다 긴 값 하나(`형식단어여부` 가 `ABC` 등)에 `JdbcBatchUpdateException` — `DictRoutes:87` 이 IAE 만 잡아 500 「서버 오류」 · 기관표준단어의 「n번째 줄」 은 빈 줄을 뺀 뒤의 행 번호라 실제 줄과 어긋난다
- 변경: `insertMoiWords` — `addBatch` 앞에 칸마다 길이를 잰다(V001 `dict_word`: `word_ko` 200 · `abbr` 100 · `word_en` 200 · `domain` 100 · `description` 4000 · `form_word` 1 · `synonyms` 1000 · `forbidden` 1000 — 상수로). 넘으면 `IllegalArgumentException("자료 " + i + "번째 행(머리·빈 줄 빼고) — " + 칸 한글 이름 + " 이 " + 상한 + "자를 넘는다")`. 값은 안 싣는다. `orgPairs:317` 의 글머리도 `"자료 " + i + "번째 행(머리·빈 줄 빼고) — "` 로(꼬리 `TOO_LONG` 은 그대로)
- 시험(먼저): `DictStoreTest` — `형식단어여부` 가 `ABC` 인 두 줄 CSV 의 `importMoiFile` 이 `IllegalArgumentException`(글에 「형식단어」·「1자」, 전 `JdbcBatchUpdateException`)이고 사전은 그대로 · 보고서 F-10 의 여섯 줄 파일 → 글이 「자료 2번째 행」 으로 시작(전 「3번째 줄」). 라우트 — `POST /api/dict/moi/import` 400(`DictRoutesTest` 가 있으면)
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='DictStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`

### 묶음 ⑨ 웹 · 파일 · 프로필 · CLI · 로그 SQL (java 레인)

기준선: `LocalFiles` `resultRoot(5)` 22 · `list(4)` 21 / `ProfileStore` `saveCodeCheck(3)` 22 / `SelfTest` `body(0)` 19 — 분기를 더하지 않는다(식을 도우미로).

#### 0-121 — LocalFiles: 금지 폴더·결과 폴더 판정을 파일 키로 · 결과 쓰기 실패는 400 (G-01 · G-18)
- 대상: `core/fs/LocalFiles.java`(`refuseForbiddenReal` · `resultRoot`·`writeResult` 의 식 · 새 `sameOrInside`·`mkdirs`·`put`) · `LocalFilesTest`
- 사실: 금지 폴더(프로필·`data/`) 판정이 `toRealPath()` 뒤 글자 비교(`under:196`)다. JDK 의 Windows `toRealPath` 는 재분석 지점만 풀고 `subst` 드라이브 글자는 그대로 둬 — `subst P: C:\workspace` 를 건 PC 에서 `GET /api/fs/read?path=P:\toolbox-server\profiles\<이름>.yaml` 이 지난다(절대 규칙 2), `resultRoot:416-433` 의 「결과 폴더는 원본 밖」 판정도 같은 꼴(절대 규칙 7). 점검자는 JDK 소스로 추적했고 `subst` 를 거는 것이 시스템 상태라 실측은 못 했다 · `resultRoot:447`·`writeResult:476-480` 의 쓰기 `IOException`(없는 드라이브·잠긴 파일)은 `Refused` 가 아니라 `/api/fs/result` 가 500 「서버 오류」(읽기 쪽 `bytes:366` 은 400 — 0-109)
- 변경:
  1. `private static boolean sameOrInside(Path p, Path dir)` — `p` 에서 조상으로 올라가며(있는 것만) `Files.isSameFile(조상, dir)` 이면 참(`dir` 이 없으면 거짓, `IOException` 은 거짓). 파일 키(볼륨 일련번호 + 파일 번호)로 재 `subst`·8.3·대소문자·정션을 가리지 않는다
  2. `refuseForbiddenReal` — 지금 글자 판정 뒤에, 가장 가까운 있는 조상 `near` 와 금지 폴더마다 `sameOrInside(near, f)` 면 같은 글로 거절
  3. `resultRoot` 의 「원본 안 결과 폴더」 판정 식에 `|| sameOrInside(out 의 가장 가까운 있는 조상, 원본)` 을 **도우미 하나**로 감싸 넣는다(`resultRoot(5)` 22 — 식을 `insideSource(out, root)` 호출로 바꾼다) · `Files.createDirectories(out)` → `mkdirs(out)` · `writeResult` 의 `Files.createDirectories(parent)`·`Files.write(p, body)` → `mkdirs(parent)`·`put(p, body)` — 두 도우미는 `IOException` 을 `Refused(400, "결과 폴더에 못 썼다 — " + e.getClass().getSimpleName())` 로
- 시험(먼저): `LocalFilesTest` — `sameOrInside` — 같은 폴더의 다른 표기(대소문자 바꾼 경로 · 끝 `.` 조각 · 있으면 8.3 이름)가 참, 형제 폴더는 거짓 · `writeResult` 의 `outDir` 이 없는 드라이브(`Q:/out` — 그 드라이브가 있으면 `assumeFalse`)면 `Refused` 400(전 `FileSystemException`) · 지킴이 — 기존 거절 꼴(정션·끝 점·프로필 폴더) 전부 그대로 · 보통 결과 쓰기 그대로
- Ruling: `subst` 는 시험이 못 건다(시스템 상태) — RED 는 `sameOrInside` 단위 단언과 400 사례가 그 자리. `PROGRESS.md` 「사람이 할 것」 ⑫(0-84 실측 목록) 끝에 「`subst` 드라이브로 프로필 읽기·원본 안 결과 폴더가 거절되는지」 를 더한다
- 의도된 골든 변화: 없음 · `self-check.txt` 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='LocalFilesTest,FsRoutesTest,ArchitectureTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-144(같은 파일)

#### 0-125a — 로컬 드라이브만: 매핑된 네트워크 드라이브 · 링크를 푼 실제 경로 (G-10 — 사용자 2026-11-22 「못하게 해야 함」, 절대 규칙 1)
- 대상: `core/fs/PathRules.java`(새 `remoteDrive`·`driveType`) · `core/fs/LocalFiles.java`(`network` · `refuseForbiddenReal` 한 줄) · `pom.xml`(shade 매니페스트 `Add-Opens` · surefire `argLine`) · `LocalFilesTest`
- 사실: `LocalFiles.network:136` 은 `\\` 로 시작하는 꼴(UNC·장치 경로)만 본다. 드라이브 글자에 매핑된 네트워크 드라이브(`Z:` = `\\서버\공유`)는 지나가 — 폴더 칸·`deliverable.templateDir`·`--data-dir` 에 넣으면 자바 프로세스가 SMB 로 파일 서버와 통신한다. 링크를 푼 실제 경로(`refuseForbiddenReal:180` 의 `toRealPath()`)가 UNC 여도 금지 폴더 목록과만 견준다. 순수 자바 공개 API 에는 드라이브 종류(원격)가 없다 — 설계 탐침(jshell `--add-opens java.base/sun.nio.fs=ALL-UNNAMED`): `sun.nio.fs.WindowsNativeDispatcher.GetDriveType("C:\\")` = 3(고정) · 없는 드라이브 = 1. 새 의존성 없이 OS 답(4 = `DRIVE_REMOTE`)을 읽을 수 있다
- 변경:
  1. `PathRules.driveType(String root)`(static, 결과 `OptionalInt`) — `Class.forName("sun.nio.fs.WindowsNativeDispatcher").getDeclaredMethod("GetDriveType", String.class)` 를 한 번 찾아 두고(`setAccessible(true)`) 부른다. 그 클래스가 없으면(Windows 가 아님) 빈 값. 열 수 없으면(`InaccessibleObjectException` — `Add-Opens` 없이 띄움) `IllegalStateException("드라이브 종류를 확인할 수 없다 — run.bat·toolbox.bat(또는 java -jar app.jar)로 띄운다")`(닫힌 실패)
  2. `PathRules.remoteDrive(String raw)` — `raw` 를 절대 경로로 만든 것의 뿌리가 `X:\` 꼴이면 `driveType` 이 4(`DRIVE_REMOTE`)인지. 뿌리가 그 꼴이 아니거나 종류가 빈 값이면 거짓. 경로를 못 읽으면(`InvalidPathException`) 거짓(뒤 판정이 거절한다)
  3. `LocalFiles.network(raw)` — 지금 식 `||` `PathRules.remoteDrive(raw)`. 이 한 자리를 `check`·`Profile.local`(`output.dir`·`templateDir`·`customRules`)·`MakeTemplates`·`8-23`(명령줄 폴더)이 같이 쓴다(0-87). 글은 그대로 「네트워크·장치 경로는 받지 않는다 — 로컬 드라이브만」
  4. `refuseForbiddenReal` — `Path real = near.toRealPath();` 다음 줄에 `if (network(real.toString())) { throw new Refused(400, "네트워크·장치 경로는 받지 않는다 — 로컬 드라이브만"); }`(링크·마운트로 네트워크에 닿는 길)
  5. `pom.xml` — shade `manifestEntries` 에 `<Add-Opens>java.base/sun.nio.fs</Add-Opens>` · surefire `argLine` 끝에 ` --add-opens java.base/sun.nio.fs=ALL-UNNAMED`. 의존성은 안 더한다
- 시험(먼저): `LocalFilesTest` — Windows 에서(`assumeTrue`) `PathRules.driveType(시스템 드라이브 뿌리)` 가 3 · `PathRules.remoteDrive("Q:/x")`(없는 드라이브 — 있으면 `assumeFalse`) 거짓 · 종류 → 판정 표(`PathRules.remote(int)` — 4 만 참, 0·1·2·3·5·6 거짓)를 단언 · Windows 가 아니면 `driveType` 이 빈 값 · 지킴이 — 기존 UNC·장치 경로 거절과 로컬 경로 통과 전부 그대로
- Ruling: 매핑된 네트워크 드라이브는 시험이 못 만든다(네트워크 금지) — RED 는 판정 표와 `driveType` 단언이 그 자리. `PROGRESS.md` 「사람이 할 것」 ⑫ 끝에 「매핑된 네트워크 드라이브(`net use Z: \\서버\공유`)의 폴더가 거절되는지」 를 더한다
- 의도된 골든 변화: 없음. `pom.xml` 이 바뀌어 db 레인이 선다(`V-70` 이 돈다) · `bash scripts/offline-build.sh` 초록(의존성 변화 없음 — 확인만)
- 검증: `bash scripts/mvn.sh -q test -Dtest='LocalFilesTest,FsRoutesTest,ProfileStoreTest,MakeTemplatesTest,ArchitectureTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 사다리: SpotBugs·ErrorProne 이 리플렉션을 막으면(`V-70a`) — `config/spotbugs/exclude.xml` 에 그 클래스·그 패턴 한 줄과 사유 주석(이 행만의 예외). 리플렉션이 그 JDK 에서 안 되면(메서드가 없다) 7절
- 의존: 0-121(같은 파일)
- 한계(이력에): `subst` 로 UNC 에 건 드라이브·WebDAV 는 OS 가 원격으로 답할 때만 막힌다 · 드라이브 종류는 요청마다 묻는다(캐시 없음)

#### 0-125b — selftest: 드라이브 종류 판정 항목 · 심의 문서 한 줄 (G-10 딸림)
- 대상: `cli/SelfTest.java`(항목 하나) · `docs/import-review.md`(7절 한 줄) · `SelfTestCliTest`
- 사실: `0-125a` 의 판정은 jar 매니페스트의 `Add-Opens` 에 기댄다 — 반입 zip 의 실제 jar 에서 듣는지는 그 jar 로 띄워야 안다(`rehearse.sh` 가 `selftest` 를 돈다)
- 변경: `SelfTest.body` 에 항목 한 줄 `check(driveOk(), false, "드라이브 종류 판정", driveText());` — `private static boolean driveOk()`: Windows 가 아니면 참, Windows 면 `PathRules` 의 판정을 실행 폴더에 한 번 불러 예외가 없으면 참(`PathRules` 가 package-private 이면 `LocalFiles.network(실행 폴더)` 를 부른다) · `driveText()` 는 「네트워크 드라이브를 가려낸다」/「확인할 수 없다 — run.bat·toolbox.bat 으로 띄운다」. **`body(0)` 는 기준선 19 — 분기를 더하지 않는다**. `docs/import-review.md` 7절에 「폴더 칸·설정 경로는 로컬 드라이브만 — UNC 와 드라이브 글자에 매핑된 네트워크 드라이브를 거절한다(OS 의 드라이브 종류로 잰다)」 한 줄
- 시험(먼저): `SelfTestCliTest` — 출력에 「드라이브 종류 판정」 줄이 「통과」 로 있다 · `doc-lint` 초록
- 의도된 골든 변화: 없음 · `self-check.txt` 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='SelfTestCliTest,MainTest,ToolsFolderTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 0-125a · 8-24(같은 파일 `SelfTest`)

#### 0-122 — 프로필 백업 가림: 접속 비밀번호 자리만 · 태그 붙은 블록 값 (G-02 · G-05)
- 대상: `web/ProfileRoutes.java`(`withoutPasswords` · `withoutBlockPasswords` · 새 `passwordKeys`) · `ProfileRoutesTest`
- 사실: `PASSWORD_KEY:33`(`\bpassword[ \t]*:`)가 접속 비밀번호가 아닌 자리의 글자 — 내장 규칙 id 덮어쓰기 `common.password: false`(`example.yaml` 이 안내하는 꼴)·규칙 정규식 `"password: [a-z]+"` — 에도 맞아 그 값을 `''` 로 바꾸고, `leaked:151` 이 「트리가 달라졌다」 로 거절한다 — 코드 검사 저장이 늘 400 「백업에서 접속 비밀번호를 못 지웠다」(사유가 엉뚱한 줄을 가리킨다) · `withoutBlockPasswords:287` 은 `password: !!str |` 처럼 태그·앵커가 낀 블록 스칼라를 못 알아봐, 본문 줄이 `#` 로 시작하면 백업에 평문이 남는데 `leaked` 가 통과시킨다(절대 규칙 2 — 인위적 입력)
- 변경:
  1. `passwordKeys(String yaml)` — YAML 파서(`YAMLParser`)로 훑어 `connections` 배열 원소 바로 아래 `password` 키 토큰의 시작 글자 자리(`getTokenLocation().getCharOffset()`) 집합. 못 읽으면 null
  2. `withoutPasswords` — `PASSWORD_KEY` 가 맞은 자리 가운데 `passwordKeys` 에 든 것만 지운다(집합이 null 이면 지금처럼 전부 — `leaked` 가 판정한다). 따옴표 키는 따옴표 자리가 시작이다
  3. `withoutBlockPasswords` 의 정규식 — 콜론과 `|`·`>` 사이에 태그·앵커 토막(`[!&]\S++[ \t]++` 0번 이상 — 묶음 반복 없이 `(?:[!&]\S++[ \t]++)?(?:[!&]\S++[ \t]++)?` 두 번까지)을 받는다
- 시험(먼저): `ProfileRoutesTest` — 프로필에 `codecheck.rules: { common.password: false }`(흐름 꼴·블록 꼴·`{ common.password: { severity: info } }`)가 있어도 `backupText` 가 거절하지 않고 접속 `password` 만 빈다(전 400) · 규칙 `regex: "password: [a-z]+"` 도 · `password: !!str |` + `      # S3cret` → 백업 글에 `S3cret` 가 없다(전 남음) · 지킴이 — 0-89 의 기존 사례(따옴표 키·흐름 맵·`FAKE#tail`·블록 스칼라·별칭 거절) 전부 그대로
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='ProfileRoutesTest,ProfileStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 사다리: 파서의 토큰 자리가 흐름 꼴에서 `PASSWORD_KEY` 자리와 안 맞으면 — 자리 대신 「그 키가 든 줄 번호 + 줄 안 차례」 로 맞춘다. 그래도 기존 사례가 빨개지면 2 를 빼고(1·3 만) 이력 「정한 것」 + 새 행

#### 0-123 — 프로필: 규칙 덮어쓰기 병합 · `!`·`&`·`#` 로 시작하는 password (G-03 · G-04)
- 대상: `core/profile/ProfileStore.java`(`mergedRule` · `refuseAliases`) · `ProfileStoreTest`
- 사실: `mergedRule:183` 은 옛 맵에 `regex`·`params` 가 있을 때만 맵을 남긴다 — 손으로 적은 `{ severity: info }`·`{ globs }`·`{ message }`·`{ flags }` 는 화면에서 체크박스만 바꿔도 Boolean 으로 바뀌어 사라진다(등급을 낮춰 둔 규칙이 기본 등급으로 돌아가 `check --fail-on` 결과가 달라진다) · `password: !Abc123`·`&abc`·`#abc1` 은 YAML 이 태그·앵커·주석으로 읽어 **빈 글**이 되는데 `refuseAliases:79-97` 은 별칭·병합 키만 본다 — 빈 비밀번호로 접속을 시도하고(`ConnectionRegistry:90`) 되풀이하면 DB 계정이 잠긴다
- 변경:
  1. `mergedRule` — `v` 가 Boolean 이고 옛 값이 맵이면 **키와 무관하게** 맵을 지키고 `enabled` 만 바꾼다(조건에서 `&& (m.containsKey("regex") || m.containsKey("params"))` 를 뺀다)
  2. `refuseAliases` — `connections` 배열 원소의 `password` 값 토큰에 태그(`getTypeId() != null`)나 앵커(`getObjectId() != null`)가 붙었으면 「password 값이 ! · & 로 시작한다 — 따옴표로 감싼다」, 값이 null·빈 글인데 그 줄의 콜론 뒤에 공백 아닌 글자가 있으면(`#` 로 시작) 「password 값이 # 로 시작한다 — 따옴표로 감싼다」 로 거절(줄·칸만, 원문은 안 싣는다 — 1-41)
- 시험(먼저): `ProfileStoreTest.mergeRulesShapes` 에 — `{a={severity=info}}` + `{a=false}` → `{a={severity=info, enabled=false}}`(전 `{a=false}`) · 다시 `{a=true}` → `{a={severity=info, enabled=true}}` · `globs`·`message`·`flags` 도 · `passwordNeedsQuotes` — `password: !Abc123`·`&abc`·`#abc1` 의 `load` 가 예외(글에 「따옴표」, 값 글자는 없다, 전 빈 비밀번호로 읽힘) · 지킴이 `password: "!Abc123"`·`password: abc # 주석`·`password:`(빈 값) 은 읽힌다
- 의도된 골든 변화: 없음 · `self-check.txt` 0(`saveCodeCheck(3)` 22 그대로)
- 검증: `bash scripts/mvn.sh -q test -Dtest='ProfileStoreTest,ProfileRoutesTest,CheckRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 0-122

#### 0-124 — 요청 가드: 2GB 넘는 `Content-Length` · 포트 80 (G-09 · G-13)
- 대상: `web/LocalOnly.java`(`body` · `check` 의 Host·Origin 식 · 새 `sameHost`·`sameOrigin`) · `LocalOnlyTest`(없으면 `AppTest`)
- 사실: `body:76-79` 의 `ctx.contentLength()` 는 int — `Content-Length: 2147483648` 이면 Jetty 가 -1 을 줘 413·415 판정을 둘 다 건너뛰고 본문을 끝까지 읽는다(0-107 이 막으려던 상한 없는 본문) · `serve --port 80` 이면 브라우저가 `Host: 127.0.0.1`(포트 없음)을 보내 모든 요청이 403 「허용되지 않은 Host」
- 변경: `body` — 길이를 `ctx.req().getContentLengthLong()` 으로 재고, 메서드가 본문 있는 것(안전 메서드가 아님)인데 `Content-Length` 머리가 있고 값이 -1(못 읽음)이면 413 · `check` — Host 판정을 `sameHost(host, port)`(지금 둘 + `port == 80` 이면 포트 없는 `127.0.0.1`·`localhost`), Origin 판정을 `sameOrigin(o, port)`(지금 둘 + `port == 80` 이면 `http://127.0.0.1`·`http://localhost`)로
- 시험(먼저): `Content-Length: 2147483648` POST → 413(전 통과 — 가드 단위 시험으로, 실제 2GB 본문은 안 보낸다) · `sameHost("127.0.0.1", 80)` 참 · `sameHost("127.0.0.1", 8080)` 거짓 · `sameOrigin("http://localhost", 80)` 참 · 지킴이 — 기존 Host·Origin·`Sec-Fetch-Site` 사례 그대로
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='LocalOnlyTest,AppTest,SmokeHtmlUnitTest,SelfCheckTest'` → `bash scripts/verify.sh`

#### 8-23 — 기동: 명령줄 폴더의 UNC · 기동 실패는 한 줄 (G-11 · G-17)
- 대상: `cli/Serve.java`(`call`) · `cli/Batch.java`(`open`) · `web/App.java`(`bind`) · `MainTest`·`BatchCliTest`(CLI 시험이 있는 파일)
- 사실: `--data-dir`·`--profiles-dir` 는 UNC 를 거절하지 않아 `serve --data-dir //10.0.0.5/tb/data` 가 SMB 로 나가고 H2 도 그 위에서 연다(프로필 `output.dir` 의 UNC 는 0-85b 가 막았다 — 절대 규칙 1) · 기동 실패 가운데 `IllegalStateException` 이 아닌 것 — `--port 65535` 가 사용 중일 때 `isFree(65536)` 의 `IllegalArgumentException` · 포트 열 개가 다 찼을 때의 `JavalinBindException` · 배치의 `--data-dir "a;b"`(`Batch.open:124` 는 `LockedException` 만 잡는다) — 은 「[오류] 한 줄」 대신 자바 스택으로 끝난다
- 변경: `Serve.call`·`Batch.open` 머리 — `dataDir`·`profilesDir` 글에 `LocalFiles.network(…)` 면 끝 코드 2 + 「네트워크·장치 경로는 받지 않는다 — 로컬 드라이브만: <옵션>」 · `App.bind` — 다음 포트가 65535 를 넘으면 더 안 올리고 `IllegalStateException("빈 포트가 없다 — --port 로 다른 포트를 준다")` · `Serve.call:74` 와 `Batch.open:124` 의 `catch` 를 `RuntimeException` 으로 넓힌다(`Serve` 는 지금처럼 스택을 `STARTUP_LOG` 에, 콘솔은 한 줄 · `Batch` 는 `fail(1, "서버를 못 열었다 — " + e.getMessage())`)
- 시험(먼저): `serve --data-dir //10.0.0.5/x`(실제 접속 전에 거절 — 시험은 네트워크에 안 닿는다) 끝 코드 2 · 배치 명령의 `--profiles-dir \\srv\p` 끝 코드 2 · `bind` 가 65535 뒤로 안 넘어간다(단위 — `isFree` 를 늘 거짓으로 주는 시험 훅이 없으면 `nextPort(int)` 작은 도우미를 빼 그것만 단언) · 배치 `--data-dir "a;b"` 가 스택 없이 `[오류]` 한 줄·끝 코드 1
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='MainTest,BatchCliTest,MainTest,AppTest,SelfCheckTest'` → `bash scripts/verify.sh`

#### 8-24 — 배치 CLI: 고른 문서가 건너뛰어지면 끝 코드 · `latest` 는 프로필 이름으로 · selftest 는 끝까지 · 권한 권고 글 (G-14 · G-15 · G-16 · G-12)
- 대상: `cli/DocCommands.java`(`Deliverable.body`) · `cli/Batch.java`(`snapshotId` · `open`) · `cli/SelfTest.java`(`body` 의 ⑤~⑧ 식 · `profiles` 의 글) · `docs/import-review.md` 7절 한 줄 · CLI 시험
- 사실: `deliverable --docs 08 --code-conn dev` 는 코드 표를 보낼 옵션이 없어 비밀번호까지 묻고 「산출물 0개」·끝 코드 0 · `--snapshot latest` 는 프로필 **파일 이름**(`Batch:282`)으로 찾는데 스냅샷에는 YAML 의 `name:` 이 저장돼(`SnapshotService:78`) — 예시를 복사해 `name:` 을 안 고친 프로필에서 `latest` 가 안 듣거나 다른 접속의 스냅샷을 집는다 · 활성 프로필이 못 읽히면 selftest 가 ⑥(`GET /api/check/rules` 400)에서 끊겨 ⑦~⑬ 과 요약 줄이 안 나온다 · 프로필 저장이 임시 파일 바꿔 끼우기(0-112)라 파일에 직접 건 권한(ACL)은 저장 때마다 폴더 상속 권한으로 돌아가는데, selftest·심의 문서는 「파일 권한」 을 좁히라고 권한다
- 변경:
  1. `Deliverable.body` — `--docs` 를 **준** 실행에서 그 번호가 `skipped` 에 하나라도 들면 끝 코드 1(`--docs` 없이 전부 만들 때는 지금처럼 0). 도움말 `--code-conn` 설명 끝에 「(코드 표는 화면에서 고른다 — 배치는 08 을 건너뛴다)」
  2. `Batch.open` — 프로필을 연 뒤 `GET /api/profiles/<이름>` 의 `name` 을 `profileLabel` 로 적어 두고(없으면 파일 이름) `snapshotId` 가 그것으로 견준다
  3. `SelfTest.body` — ⑤~⑧ 의 `batch.call(…).size()` 를 `sizeOf(path)` 로 — `private int sizeOf(String path)`: 실패(끝 코드 예외)면 -1. 글은 `countText(n, "개")`(-1 이면 「못 읽음 — 활성 프로필·서버 응답을 본다」). **`body(0)` 는 기준선 19 — 분기를 더하지 않는다**
  4. `SelfTest.profiles` 의 권고 글과 `docs/import-review.md` 7절 — 「파일 권한을 이 PC 계정만 읽기로」 → 「`profiles/` **폴더** 권한을 이 PC 계정만 읽기로(파일에 직접 걸면 화면 저장 때 폴더 권한으로 돌아간다)」
- 시험(먼저): `deliverable --snapshot <id> --docs 08 --code-conn h2` 끝 코드 1(전 0) · `--docs` 없이면 0 · `name: p1` 인 `p2.yaml` 로 `snapshot` 뒤 `ddl --profile p2 --snapshot latest` 가 그 스냅샷을 집는다(전 「스냅샷이 없다」) · 활성 프로필이 깨진 YAML 일 때 `selftest` 출력에 「통과 n · 경고 n · 실패 n」 요약 줄이 있다(전 없음)·끝 코드 1 · `ToolsFolderTest`·`doc-lint` 초록
- 의도된 골든 변화: 없음 · `self-check.txt` 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='BatchCliTest,DocCliTest,SelfTestCliTest,MainTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 8-23(같은 파일 `Batch`)

#### 4-50 — 로그 SQL 복원: 제곱 시간 · 배치 Parameters · 배열 타입 (G-06 · G-07 · G-08)
- 대상: `core/text/LogSql.java`(`restore` · `attach` · `TYPE_END` · `literal`) · `LogSqlTest`
- 사실: `restore:82` 의 `q.forEach(y -> y.doubt = true)` 와 `attach:105` 의 `open.values().forEach(d -> d.remove(…))` 가 짝 없는 Preparing·서로 다른 열쇠 수만큼 돌아 5백만 자 상한 안 입력에 88초·33초 · 한 Preparing 뒤 Parameters 가 여러 줄(MyBatis BATCH)이면 첫 줄만 쓰고 나머지를 경고 없이 버린다 · 타입 글 `(byte[])`·`(String[])` 를 `TYPE_END:42` 가 못 읽어 뒤 값과 합쳐진다
- 변경:
  1. `restore` — 같은 열쇠 큐에 이미 있으면 **큐 머리 하나**에만 `doubt`(머리가 이미 `doubt` 면 건너뛴다 — 뒤는 들어올 때 이미 표시됐다) · `attach` — 넘겨 붙일 때 그 `Pending` 의 **자기 열쇠 큐**(`open.get(x.key)`)에서만 뺀다
  2. `attach` — 짝을 못 찾았고 바로 앞 `Pending` 이 이미 `paired` 이고 열쇠가 같으면(또는 둘 다 빈 열쇠), 그 문장을 복제한 새 `Pending` 에 이 Parameters 를 붙여 `all` 에 더한다(배치 실행기 — 문장 하나에 Parameters 여러 줄)
  3. `TYPE_END` 의 타입 글 — `[A-Za-z_][\w.$]*+(?:\[\])*+` · `literal` — 타입이 `[]` 로 끝나면 값을 `'…'` 로 감싸지 않고 `/* <타입> */ NULL` 자리 표시로 내고 그 항목 `warning` 에 「배열·바이너리 값은 못 옮긴다」
- 시험(먼저): `LogSqlTest` — `"Preparing: ?\n".repeat(380_000)` 과 열쇠가 줄마다 다른 6만 짝이 각각 `assertTimeout(5초)` 안 · 보고서 G-07 로그 → 항목 셋(`(1, 'a')`·`(2, 'b')`·`(3, 'c')`, 전 하나) · G-08 로그 → 파라미터 셋으로 읽고 둘째 자리가 `/* byte[] */ NULL` + 경고(전 「? 3개 ≠ 파라미터 2개」) · 지킴이 — 기존 스레드 섞인 로그 사례(R-G-9·마무리 리뷰 I3) 그대로
- 의도된 골든 변화: `logsql-*` 골든 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='LogSqlTest,TextRoutesTest,SmokeHtmlUnitTest,SelfCheckTest'` → `bash scripts/verify.sh`

### V-70a — 정적 분석 · corpus 골든 확인
1. `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify`(PMD·SpotBugs·ErrorProne) — 새 짝은 코드로 고친다(기준선에 안 더한다). 고치기 둘. 정규식을 손본 행이 많다 — SpotBugs REDOS 는 「수량자 단 묶음 안 수량자」 를 막는다(소유 수량자여도): 걸리면 그 식을 단계로 쪼개거나 손 훑기로
2. `bash scripts/verify.sh --corpus`(약 15분). 다른 JVM·컨테이너를 같이 띄우지 않는다. 허용 변화 —
   - `check-*.json`·`check-*-hits.txt` — **느는 것**: jsp·html 의 `<script>` 감싸개 안 지적(`5-128` — `skipComments` 규칙 전부. egov 가 가장 크다: 점검자 실측 181 파일 · `common.alert` 16 → 169 꼴) · 형제 블록 `security.resourceLeak`(`5-132`) · 서브쿼리 안 `<where>` 의 `mybatis.noWhere`(`5-133`). **주는 것**: 매개변수·패턴 변수·switch 갈래 오탐(`5-131`) · `<sql>` 조각 `mybatis.noWhere`(`5-133`) · 설정 파일의 `security.sqlDollar`(`5-134` — 0 기대). **줄 번호만 바뀌는 것**: 여러 줄 시작 태그의 mybatis 지적(`5-133`)
   - `cleanup-*`(건수·건너뜀·컴파일) — `5-129`·`5-136`~`5-145` 의 「의도된 골든 변화」 줄 그대로. `CleanupCompileCorpusTest` 의 컴파일 실패가 **느는 것**은 등급 A — 7절
   - `analyze-*`(`.json`·`-joins`·`-unresolved`·`-jspurl`·`-orphan-jsp`·`-dead-stmt`·`impact`·`spot`·`consistency`·`noxml`·`notinddl`) — `6-75`~`6-92`·`6-77` 의 「의도된 골든 변화」 줄 그대로. 큰 것 둘: JSP 링크가 **는다**(`6-85` — egov 약 +287 (파일, URL)) · 공통 DAO 표본의 문장·CRUD 가 **는다**(`6-86`). `table` 미해결(등급 A)·`depth` 미해결이 새로 나오면 7절
   - `security-*`·Juliet·Benchmark 채점 — 재현율이 **내려가면** 7절(정밀도가 오르는 것·`resourceLeak` 가 느는 것은 허용 — 수를 이력에)
   - `deliverable-egov-relations.json` — 조인 변화(`6-75`·`6-78`·`6-79`)를 따라가는 것만
   - 그 밖(`gen-*`·`hr-insert-*`·`ddl-*`·`masking-*`·`logsql-*`·`csv-*`·`strip-*`·`sqlfixture`·`metrics`)은 diff 0 기대 — 바뀌면 원인 행과 댄다
   - 등급 B baseline 이 상한(표본의 2%)을 넘으면 7절. 허용 밖 변화 — 고치기 둘, 넘으면 `wip/V-70a`
3. diff 줄 수·종류별 건수(전 → 후)·벽시계를 이력에
- 의존: 코드 행 전부

### R-37 — 손 판정 · 처분 표
- `design/compare-2026-10-28.md` 끝에 `## ㊵ 번들 91 — 가로 점검 3차 + 대기 행 다섯(2026-11-22)`:
  1. 처분 표 — 행마다(행 · 점검 키 · 강제 지점 시험 · 닫음/한계/wip). 한계(각 행 「한계」 줄)를 행마다
  2. `V-70a` 골든 변화 표(파일 종류별 건수 전 → 후). 0 이면 「없음」
  3. 손 판정(SQL 본문·코드·값은 안 적는다 — 파일·줄·문장 id·규칙 id·이름만) —
     - **새 check 지적 스물**(egov 의 감싸개 안 지적에서 규칙이 다른 것으로 고른다): 그 줄이 `<script>` 안 살아 있는 코드인지. 주석 처리된 줄·HTML 본문이면 7절
     - **빠진·새 조인 각 스물까지**: 빠진 것은 `6-75`·`6-78`·`6-79` 의 어느 꼴인지(파생 표 별칭 가림·분기 같은 별칭·잠금 UPDATE·DUPLICATE KEY), 새 것은 파생 표 뒤 쉼표·분기 앞 정의·따옴표 친 예약어 표인지. 어느 꼴도 아니면 7절
     - **새 JSP 링크 스물**: 감싼 스크립트 안의 실제 `.do` 호출인지 · **빠진 JSP 링크 전부**: `c:set` 접두가 붙은 것인지
     - **공통 DAO 로 새로 풀린 문장 스물**: 호출 자리 리터럴이 그 문장 id 인지(다른 메서드의 것을 가져왔으면 7절)
     - **`6-77`·`6-90` 으로 빠진 표·문장 전부(스물 넘으면 스물)**: 그 클래스의 import 가 프로젝트 밖 FQCN 인지 · 선언한 조상의 패키지 것으로 바뀐 것인지. 같은 패키지·별 import 인데 빠졌으면 7절
     - **코드 정리에서 삭제가 준 줄 스물 · 는 줄 스물**: 준 것은 제어문 뒤·스크립트 밖·TS 호출인지, 는 것은 주석 줄 뒤 console 인지
  4. 끝에서 끝 — `run.bat --port 18873 --no-browser`(빈 `--data-dir`) → ① `curl -s "http://127.0.0.1:18873/api/check/vcs?path=<부분 클론 꼴 임시 저장소>"` 가 `"available":false` 와 「부분 클론」 사유(`0-120`) ② 규칙 정의 오류 프로필로 `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:18873/api/check/rules` 가 400(`5-130`) → 서버를 끈다
- 코드·레인 지문이 안 바뀐다
- 의존: V-70a

### V-70 — `--db`
1. 여유 RAM — PowerShell `(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB`. 15 미만이면 7절. `--corpus` 가 끝난 뒤에만(겹쳐 띄우지 않는다)
2. `bash scripts/docker-up.sh` → `bash scripts/verify.sh --db`(컨테이너 아홉, 17~35분). 다른 JVM 을 같이 띄우지 않는다
3. 허용 변화 — 골든 diff 0 기대(`alter*`·`ddl*`·`insert*`·probe 수·품질 SQL). `alterRoundTrip`·`alterTwoReleases`·`ddlRoundTrip`·`insertRuns`·`readOnlyBypasses`·`qualitySqlRuns` 등 기존 등급 A 는 0. 바뀌는 줄이 있으면 — FK 컬럼 길이(`1-119`)·유니크 이름(`1-119`)·`[손]` 글(`1-116`)·타입 열쇠(`1-121`)·품질 SQL 의 `#` 이름 인용(`3-25`)·DB 대조 행의 상태·열쇠(`6-83a`·`6-83b`) 가운데 무엇인지 보고 수를 이력에. 어느 것도 아니면 7절
4. `verify.sh --db` 와 `git push` 는 따로 낸다
- 의존: R-37

### 마무리
CLAUDE.md 「마무리」 1~8 그대로 — 독립 리뷰 하나(**`model: fable`, effort `xhigh`** — 서브에이전트 호출에 둘 다 명시. Review Focus: ① 절대 규칙 1·3 — `Cli.env`·`refusePartialClone` 을 안 거치는 git 호출 · `SqlProbe.names` 가 리터럴 값을 「자기 낱말」 로 남기는 남은 길 · 시험·`[손]`·`[알림]` 글·compare ㊵ 에 SQL 본문·값이 실리는 자리 ② `RegexRule.scriptWrappers`·`bare` — 실제 HTML 주석(주석 처리한 `<script>` 블록)을 살려 오탐을 만드는 길 · `scriptBodies` 가 실물 JSP 의 닫는 태그를 놓치는 꼴 ③ `SqlJoins.Scopes` — `nearest`·`queryParen`·`opens`·`closedParens` 가 맞던 관계를 빼거나 지어내는 꼴 ④ `StmtArgs` — 호출 스택이 어긋나 다른 호출의 인자를 문장으로 읽는 길 · 방문 열쇠가 프레임 예산(`MAX_FRAMES`)을 넘겨 `depth` 를 내는 길 ⑤ `JavaGraph.lookup`·`JpaIndex` 의 `named` — 중첩·같은 파일·별 import 조상에서 맞던 것을 빼는 길 ⑥ 코드 정리 — `RegexRule.bare` 로 바꾼 뒤 고침이 판정과 다른 글을 보는 남은 자리 · `ConsoleArgs.call` 이 놓치는 호출 꼴 · `LogFix.earlyInit` 인스턴스 쪽이 과하게 건너뛰는 꼴 ⑦ DDL — `QNAME` 이 이름 뒤 글자를 삼키는 꼴 · `typeKey`·`ukName`·`fkColumn` 이 같은 방언 출력을 바꾸는 길 · `defaultChanged` 의 호출 횟수·`[손]` 세 글 ⑧ 기준선 — 낮춘 줄이 행에 적힌 여섯뿐인가, 오른 값·새 줄이 없는가 ⑨ 골든 diff 가 행의 「의도된 골든 변화」 와 다른 자리 ⑩ 새 규칙·판정에 양성·음성 단언이 다 있나 ⑪ 절대 규칙 1·2·6 — `PathRules.driveType` 의 닫힌 실패가 Windows 밖·시험·`java -jar` 에서 서는 길과 `network()` 를 안 거치는 경로 입력 · `ReadOnlyJdbc` 의 `glued`·`loneCr` 를 지나는 남은 꼴 · `ProfileRoutes.passwordKeys` 가 접속 비밀번호를 백업에 남기는 길) → `bash scripts/gate-probe.sh`(「패치 갱신 필요」 면 다시 뜬다 — `0-120` 이 `Cli.run` 을 건드린다) → 「순수본 밀린 목록」: `PLAN.md` `5-100` 행 사실 칸 끝에 `5-128`·`5-134` 줄(와 `5-133` — 순수본 JS 에 같은 판정이 있을 때만)을 더한다 → `verify.sh`(리뷰 고침이 건드린 무거운 레인만 다시 — corpus 먼저, 그다음 db) → push → PR(본문에 행마다 시작 세 줄 표 — `V-70a`·`R-37`·`V-70` 포함) → CI 폴링 → claude[bot] 「아니오」 처분(다시 쓴 코멘트에 「아니오」 없을 때까지) → **머지 위임** `gh pr merge N --merge --delete-branch` → 닫기 커밋은 새 로컬 가지에 → 끝 보고.

## 5. 금지 사항

- `pure/` · `ext-fixes.yaml` · `config/pmd/exclude-from-failure.properties` · 마이그레이션 V001~V017 을 고치지 않는다. 새 마이그레이션·새 규칙·새 의존성 0. `pom.xml` 은 `0-125a` 의 두 줄만 · `config/spotbugs/exclude.xml` 은 `0-125a` 사다리의 한 줄만. `Unresolved.KINDS` 를 늘리지 않는다. `rules.yaml` 은 `5-134` 의 한 줄만(`pure-rules.json` 은 그 한 줄만 따라간다)
- **self-check 기준선** — 값을 올리거나 새 줄을 만들지 않는다. 내려가는 갱신은 행에 적힌 것만: `1-117`(`AlterGen.modifyColumn`) · `1-121`(`DdlGen.type`) · `7-37`(`GenModel.of`) · `4-48`(`InsertGen.genVal`) · `6-89`(`JpaIndex.jpql`) · `2-52`(`XlsxFiller.fill`). 그 밖 줄이 움직이면 고침이 넓다
- 메서드 수 기준선 클래스(`JavaGraph` 34 · `JpaIndex` 38 · `AlterGen.Run` 40 · `Definitions` 34)에 메서드를 더하지 않는다 · 인자 수가 이름표인 메서드(`buildUpsert(5)` · `build(8)` · `writeGuide(11)` · `preview(8)` · `apply(10)` · `repoCall(8)` · `Result`·`Col`·`Def` 생성자)의 인자 수를 바꾸지 않는다
- 기존 경고·사유·미해결·`[손]` 글자를 바꾸지 않는다(새 경우는 새 글자로) · 기존 시험 단언을 지우거나 느슨하게 바꿔 통과시키지 않는다(옛 결함을 못 박은 단언은 행의 사다리대로 — 이력에 적고 바꾼다) · 골든의 옛 열쇠·사례는 그대로
- 행이 「안 고친다」 고 적은 메서드·파일을 고치지 않는다. 행의 대상 밖 파일을 셋 넘게 고쳐야 하면 7절
- 화면은 행에 적힌 JS·HTML 한 자리씩만. `common.js`·`common.css` 를 고치지 않는다. `SmokeHtmlUnitTest.JS_OFF` 를 안 바꾼다
- 픽스처 폴더(`fixtures/analyze/mapper/`·`fixtures/analyze/jpa/`)에 파일을 더하지 않는다(인라인 `Source`). `fixtures/check/` 는 `5-134` 의 음성 하나만
- 시험·경고·`[손]`·`[알림]` 글·compare 절·로그에 실제 사업 코드·SQL 본문·값·비밀번호를 쓰지 않는다(절대 규칙 2·3). 시험 이름은 `T1`~`T5`·`T91_*`
- 탐침·시험에서 네트워크에 닿지 않는다 — `0-120` 의 promisor 원격 주소는 임시 폴더 안 없는 경로
- 무거운 판을 겹쳐 띄우지 않는다(정적 분석 → `--corpus` → 손 판정 → RAM 확인 → Docker → `--db`)
- 다른 저장소(portfolio·satchel)를 고치지 않는다 · worktree·`finishing-a-development-branch` 를 쓰지 않는다 · 새 가지를 안 딴다(닫기 커밋 빼고)
- 뺀 것을 하지 않는다 — `5-100`·`5-31` 순수본 재주입 · 반입 zip · 조건부 `5-14`·`5-15`·`6-9` · 보류 `2-7`·`2-8`·`5-7`·`4-10`

## 6. 최종 검증

- `bash scripts/verify.sh` 초록 · `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify` 초록 · `bash scripts/verify.sh --corpus` 초록(등급 A 0) · `bash scripts/verify.sh --db` 초록(컨테이너 아홉, 등급 A 0) · `bash scripts/gate-probe.sh` 열 전부 산다 · `bash scripts/offline-build.sh` 초록(`pom.xml` 을 고쳤다 — 의존성 변화 없음을 확인)
- 강제 지점 시험이 전부 있다(S0 표, `wip/` 로 뺀 행 빼고) — 코드 행 이력에 RED → GREEN 한 줄, 기준선·화면 행은 Ruling 한 줄
- 손 확인(R-37): compare ㊵ 처분 표 · 여섯 손 판정 · `curl` 둘
- 회귀: `golden/` diff 는 행의 「의도된 골든 변화」 와 `V-70a`·`V-70` 의 허용 변화뿐 · `self-check.txt` diff 는 낮춘 여섯 줄뿐 · `pure-rules.json` diff 는 `security.sqlDollar` 의 `when` 한 줄 · 규칙 수 147 · 마이그레이션 17 · `Unresolved.KINDS` 20 · 커버리지 하한 유지
- 분할표 — 대기 다섯(`6-75`·`6-76`·`6-77`·`1-116`·`1-117`)과 새 행이 다 닫혔다(또는 `wip/` 사유) · 15장 번들 91 완료 · `5-100` 행에 밀린 목록 줄
- CI 초록 · claude[bot] 처분 · PR 머지 · 메모리 `bundle91-done` + `MEMORY.md` 한 줄

## 7. 중단 조건

다음이면 임의로 풀지 말고 멈추고 보고한다(어느 스텝 · 무엇이 예상과 달랐나 · 가능한 선택지).
- 행의 「사실」 이 실제 코드와 다르다(줄 번호 어긋남은 아님 — 메서드·흐름이 다를 때) · RED 가 행의 「전」 과 다르다(이미 맞게 나온다 — 그 사례는 지킴이로 두고 이력에 적은 뒤 계속, 행 전체가 그러면 멈춘다)
- 검증이 2회 연속 실패하고 원인이 이 플랜 범위 밖
- 5절을 어기지 않고는 진행할 수 없다(특히 — 기준선 값을 올리거나 메서드 수 기준선 클래스에 메서드를 더해야만 되는 고침)
- 스텝에 없는 파일을 3개 이상 고쳐야 한다(골든·`self-check.txt` 는 스텝에 있는 것이다)
- 골든의 옛 열쇠·사례가 바뀐다(`sql-joins.json` 서른다섯 · `sql-tables.json` · `mapper-index.json` · `jpa-index.json` · `alter-*`·`ddl-*`·`insert-*`)
- `V-70a` — 등급 A(`table` 미해결·컴파일 실패·파싱 실패)가 새로 나온다 · `depth` 미해결이 는다 · 보안 재현율이 내려간다 · 등급 B baseline 이 2% 를 넘는다
- `R-37` — 손 판정 여섯 가운데 「아니면 7절」 에 걸린다
- `V-70` — 기존 등급 A 가 새로 빨강 · `--db` 앞 여유 RAM 15GB 미만(다른 세션 컨테이너 — 사람이 조율한다)

번들 모드의 「갈려도 안 멈춘다」 는 위 밖에서만 선다. 행당 계획 밖 결정 둘까지, 넘으면 `wip/<청크>` 로 빼고 다음 행. 빠졌을 때 뒤 행 — 「의존」 줄의 선행이 빠지면: 같은 파일이라 이은 것(대부분)은 그대로 친다. 도우미를 받아 쓰는 것만 같이 뺀다 — `5-128`(`scriptBodies`·`scriptWrappers`)이 빠지면 `5-129`·`5-137`·`6-85` · `5-137`(`RegexRule.bare`)이 빠지면 `5-143` · `6-81`(`SqlTokens.stripComments`)이 빠지면 `6-82`·`6-83a` · `6-75`(`opens`·`PASS` 자리)가 빠지면 `6-79` 의 1·2 · `1-117` 이 빠지면 `1-116` 은 `redefines(…)` 식을 그대로 넘겨 친다 · `0-125a` 가 빠지면 `0-125b` 도 뺀다(`8-23` 은 UNC 꼴 판정만으로 친다).

## 8. 불확실 항목

사용자만 정할 수 있는 것 — 없음(범위 · `6-76` (가) · 머지 위임 · 컷 없음 · 뺀 것 · 네트워크 드라이브는 막는다 · 기준선 낮춤 여섯 줄 허용 — 다 답 받음. 메모리 `bundle91-decisions` + 이 플랜 맥락 6·7).

실행해야만 알 수 있는 것 — 실행자가 해당 스텝에서 확인한다.
- **첫 스텝 전에**: `git log --oneline -3`(S0 커밋과 `docs: 설계 76` 커밋) · `git status --short` 깨끗 · `docker ps`(다른 세션 컨테이너가 떠 있으면 `V-70` 앞 RAM 을 미리 본다)
- **행마다 RED 가 「전」 과 같은가**(시험 실행) — 점검 탐침은 `57760d1` 의 `target/classes` 로 돌린 것이다. 다르면 7절 둘째 줄
- **행마다 self-check 값**(`-Dtest=SelfCheckTest`) — 설계는 줄 있는 메서드를 도우미 호출로만 바꿨다. 오르면 공통 사다리, 낮춘 여섯의 실제 값은 실행이 정한다
- **JavaParser 3.26.3 의 패턴 변수 형식 이름**(`TypePatternExpr`) — `5-131`·`5-139` 사다리
- **`-c` 인자·promisor 거절이 임시 저장소에서 서는가**(`0-120` 시험) — git 2.54 로 설계 탐침은 초록
- **corpus·db 골든이 얼마나 움직이나** — `V-70a`·`V-70` 허용 변화 · `R-37` 손 판정
- **SpotBugs REDOS 가 새 정규식(`QNAME`·`DEFAULT_FOR`·`SIZE`·`DECL`)을 받는가** — `V-70a` 1

## 부록 A

부록 A 는 `design/76-audit3.md`.
