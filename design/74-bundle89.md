# 설계 74 — 번들 89: 번들 88 리뷰 행 열셋 · 보정 Minor 다섯 (Fable 설계 · Opus 실행, 2026-11-21)

## 맥락

번들 88(#133 `1b3646e`)의 마무리 리뷰·손 판정이 새 행 열셋을 남겼고(`6-64`~`6-70` · `5-126` · `5-127` · `0-116` · `0-117` · `1-113` · `8-22`), 가로 점검 2차(설계 68 §3)의 보정 Minor 다섯(R-A-M2 · R-A-M5 · R-G-14 · R-G-15 · R-C-M1)이 범위 밖으로 남아 있다. 이 번들이 열여덟을 한 PR 로 닫는다. 실행은 무인이고 시간 컷이 없다.

**사용자 결정(2026-11-21 — 고정, 다시 묻지 않는다. 메모리 `bundle89-decisions`)**
1. 범위 — 열여덟 전부. SqlJoins 셋(R-C-M1 · `6-66` · `6-68`)은 한 행(`6-66`)에서 한 손에. S0 에서 `5-114` 원 행 「대기」 를 닫는다
2. R-G-14(Csv) — 순수본과 같은 동작 유지. 행은 「안 고침 — 동결」 로 닫는다
3. `5-127` — 고치지 않고 `docs/pure-code-check.md` 에 한계 줄
4. `0-116` — 지금 동작(「이거나」) 유지. 코드 안 고침. 행은 「지금 동작 유지 — 사용자 결정」, 설계 73 과 다른 이유를 이력에(폐쇄망이라 OneDrive 류 자리 표시 폴더가 드물다 · 링크는 넓게 막는 쪽이 안전하다)
5. `6-70` — MariaDB 에서 EXISTS 재시도를 확인하고, 문제면 오류 코드로 좁힌다
6. Fable 설계 → Opus 「번들 해」 · **머지 위임** · 시간 컷 없음 · 무인
7. (설계 중, 2026-11-22) `6-69` **플랜대로 고친다** — 표본에 혼자 선 `#` 가 0 건이어도(주석 꼴·연산자 꼴 다) 「안 고침 — 한계」 로 닫지 않는다. MySQL 계열이 아님이 드러난 문장만 `#` 를 연산자로
8. (설계 중, 2026-11-22) `6-69` 방언을 **모를 때는 주석**(지금 동작) — 이름 없는 PG 매퍼의 `a # b` 가 틀리게 읽히는 것은 한계로 받는다. `6-58` 을 안 되돌린다
9. (설계 중, 2026-11-22) `0-117` 깨진 정규식 규칙에 끔만 보내면 **400 거절** — 꺼진 규칙의 정규식 검사를 건너뛰게 하지 않는다(`RuleSet.validate` 안 고침). YAML 을 손으로 고친다

**보정(2026-11-21, `4c4d883` 트리 — 행마다 코드와 대조)**
- 열여덟 다 「남음」 을 코드로 확인했다. 줄 번호는 `4c4d883` 것이다
- `6-65` — 표본 매퍼에 `${이름}.표`·`$이름$.표` 꼴이 0 건이다(표본 폴더 grep). 픽스처로만 닫는다
- `6-69` — `SqlTables.extract`·`SqlJoins.extract`·`SqlTokens.tokens` 어디에도 방언 인자가 없다. 방언을 아는 자리는 `MapperIndex.Raw`(`databaseId` · 파일 이름)뿐이다. 표본 폴더 XML 전부에 혼자 선 `#`(뒤가 공백·줄 끝, 앞이 낱말 글자 아님)는 0 건 — corpus 골든은 안 움직인다(픽스처로만 닫는다)
- `6-70` — `DbCorpusBase.roFileMessages` 가 컨테이너 아홉 전부에서 손 매퍼를 `SqlProbe` 로 돌린다(등급 A). `MariaMetaSourceTest`(db 꼬리표, `core/dialect` — db 레인 목록에 있다)가 MariaDB 컨테이너 하나만 띄운다
- R-A-M2 — 골든 `alter-*.sql` 의 `DEFAULT NULL`·`DROP DEFAULT` 한 줄씩은 기본값을 **지운** 꼴(새 값이 빈 값)이라 안 바뀐다
- R-G-15 — 줄 비교는 폴더 비교의 「다름」 행에서만 열린다(`dev_tools_ext.js` `fdList`). CRLF·LF 도 같은 어긋남이다(`LineDiff` javadoc 「같은 줄로 본다」)
- `8-22` — 루트 로거가 CONSOLE(stderr)·FILE 둘 다 달고 있다. `LOG.error(…, e)` 는 콘솔에도 스택이 나온다
- AlterGen·DdlGen 의 `warnings` 는 `LinkedHashSet` — 같은 글을 두 번 더해도 한 줄이다
- 레인 — `core/gen`·`core/sqlrun`·`SqlProbe.java`·`DbCorpusBase.java`·`core/dialect` 시험은 db 레인, `core/*`(profile·db·job 빼고)·`resources/tools` 는 corpus 레인. 이 번들은 둘 다 돈다

## 1. 목표와 범위

목표 — 번들 88 이 남긴 행 열셋과 보정 Minor 다섯을 닫는다. 사용자가 겪는 변화: `@MappedSuperclass` 조상이 선언한 연관(`join d.language`)의 표가 CRUD 에 나온다 · `${schema}.TB` 의 표가 읽힌다 · UNION 가지마다·서브쿼리마다 별칭이 따로라 테이블 관계가 안 사라지고 안 지어진다, WITH 이름은 관계에 안 나온다, MERGE `USING` 표의 관계가 잡힌다 · PostgreSQL·Oracle 매퍼의 `a # b` 뒤가 안 버려진다 · 루트 매핑 `/` 가 있어도 `split('/')` 가 링크가 안 된다 · 같은 파일의 enum·record 이름이 같은 패키지 클래스로 새지 않는다 · 깨진 정규식이 든 규칙을 끔만 보내 저장하는 길이 막힌다 · 기동 실패 스택이 로그 파일에 남는다 · 줄 내용이 같은 「다름」 파일에 무엇이 다른지 나온다 · 못 옮기는 기본값이 앞 기본값을 지우는 문장으로 안 나온다 · MERGE 자리표시가 DTO 필드 이름과 같다 · CLOB 값마다 길이를 안 묻는다.

In scope — 코드·문서 행 열넷 + S0 에서 닫는 셋 + `V-68a`·`R-35`·`V-68`

| 행 | 닫는 것 | 꼴 |
|---|---|---|
| `S0` | `0-116`(지금 동작 유지) · `4-43`(R-G-14 — 안 고침·동결) · `5-114` 원 행 | 문서 |
| `6-64` | JPQL 경로 — 조상 필드 | 코드 |
| `6-65` | `${schema}.표` | 코드 |
| `6-66` | MERGE USING 별칭 + `6-68` 괄호 안 SET + R-C-M1 별칭 범위 | 코드(한 커밋) |
| `6-69` | PG `#` 연산자 | 코드 |
| `6-67` | 루트 매핑 `/` | 코드 |
| `5-126` | ReturnTypes — enum·record 이름 | 코드 |
| `5-127` | 정규식 리터럴 한계 | 문서 |
| `0-117` | 규칙 덮어쓰기 — 같은 병합 | 코드 |
| `8-22` | 기동 실패 스택 | 코드 |
| `4-44` | R-G-15 — 줄 내용이 같은 「다름」 | 코드·화면 |
| `1-114` | R-A-M2 — 못 옮기는 기본값 | 코드 |
| `4-42` | R-A-M5 — MERGE 자리표시 | 코드 |
| `1-113` | CLOB 길이 왕복 | 코드 |
| `6-70` | EXISTS 재시도 — MariaDB 확인 | 시험(+ 조건부 코드) |

Out of scope
- 잠재 둘(A-33 · A-37) · `5-100`·`5-31`(순수본 재주입 — portfolio 세션) · 조건부 `5-14`·`5-15`·`6-9`
- `Csv.parse` · `PathRules.linked` · `LineDiff.diff`·`lines`(줄 비교 판정) — 안 고친다
- `SqlTables.extract`·`SqlJoins.extract` 에 방언 인자 · `SqlLint`(코드 검사 쪽)의 `#` · JavaGraph·JpaIndex 네이티브 질의의 `#`
- 서브쿼리(파생 표) 별칭의 관계(`) v ON v.ID = c.ID`) · `GO` 경계의 조인
- MariaDB `MODIFY COLUMN` 이 식 기본값을 빼는 것(경고만 — 지금대로) · MSSQL 기본값 가지
- 상한 넘은 CLOB 에서 `length()` 를 못 하는 드라이버
- 새 라우트·새 화면·마이그레이션·규칙 없음(규칙 수 147 · 마이그레이션 17 · `Unresolved.KINDS` 20 그대로)

## 2. 현재 구조 요약

행마다 「사실」 줄이 지금 자리와 재현 꼴이다(4절). 여러 행에 걸치는 것만 여기 둔다.

데이터 흐름 — 프로그램 분석: `MapperIndex.scan` 이 문장마다(`Raw` — 파일·`databaseId` 가 있다) 조각을 끼운 글을 `SqlTables.extract(text, tag)`·`SqlJoins.extract(text)` 에 준다. 둘 다 `RegexRule.stripComments(…, "sql")` → `SqlTokens.tokens`(낱말 목록)를 지난다. `JpaIndex.jpql` 은 끝에서 `JpqlPaths.resolve` 로 연관 경로를 푼다. `JspLinks.extract(jsp, programUrls)` 의 `matched` 가 따옴표 글을 매핑 URL 과 맞댄다. 코드 정리: `ValueFix` 가 `ReturnTypes.Finder` 로 수신자 getter 의 반환 형식을 묻는다. 프로필: `PUT /api/profiles/{이름}/codecheck` → `ProfileRoutes.putCodeCheck`(미리 적재 `CheckRoutes.load`) → `ProfileStore.saveCodeCheck`(병합·잘라 붙이기).

함정
- **self-check 기준선**(`golden/check/self-check.txt`) — 이 번들이 건드리는 메서드 가운데 기준선에 있는 것: `SqlJoins.aliases(1)` 19 · `SqlTables.extract(2)` 17 · `SqlProbe.run(4)` 17 · `JpaIndex` `jpql(6)` 40·`entity(1)` 18·**메서드 38** · `MapperIndex.startElement(4)` 18·`Statement(8)` · `AlterGen` `modifyColumn(7)` 40·**`Run` 메서드 40** · `DdlGen` `defaultOf(3)` 18·`table(1)` 16·`type(2)` 24 · `InsertGen.generate(4)` 20 · `ProfileStore.saveCodeCheck(3)` 28 · `LineDiff.diff(2)` 23. 기준선 밖 메서드 상한은 복잡도 15·길이 100·중첩 4·인자 7, 클래스는 메서드 30·길이 1000
- `JpaIndex`(메서드 38)·`AlterGen.Run`(메서드 40)에는 메서드를 하나도 못 더한다. `JpaIndex.jpql`·`AlterGen.Run.modifyColumn` 에는 분기를 못 더한다 — 람다 안 분기도 그 메서드에 센다(가지 없는 람다·메서드 참조는 0)
- `JpaIndex.Cls` 는 `private static final class` 라 `JpqlPaths`(같은 패키지)가 못 본다
- `SqlJoins.aliases`·`outsideSet` 를 부르는 다른 자리는 없다(`TaintLink.aliases` 는 다른 메서드다)
- `SqlTables.cteNames(List<String>)` 는 package-private static — `SqlJoins` 가 그대로 쓴다
- `GoldenFiles.assertJson` 은 맵 열쇠를 정렬해 쓴다 — `sql-joins.json` 의 새 열쇠는 글자 차례 자리에 든다
- `MapperIndexTest.golden`·`namespacesAreStatementKeyPrefixes` 는 `fixtures/analyze/mapper/` 폴더 전체를 본다 — 그 폴더에 파일을 더하지 않는다(인라인 `Source`)
- `DdlGen.Gen.defaultOf` 는 못 옮기는 식에 경고를 더하는 부수 효과가 있다 — `modifyColumn` 은 이른 return **뒤에** 부른다(안 바뀐 컬럼은 경고가 없다). 이 차례를 지킨다
- `LineDiffTest.lineEndingsAndEmptySides` 의 골든(`text/diff-edges.json`)은 CRLF·끝 줄바꿈 차이를 「같음」 으로 못 박고 있다
- `DbCorpusBase` 는 PER_CLASS + `@AfterAll` 골든 대조라 메서드 하나만 못 돌린다 — MariaDB 만 빨리 보려면 `MariaMetaSourceTest`
- PMD `AvoidReassigningLoopVariables` — 새 코드는 `for` 변수를 몸에서 안 바꾼다(`while` 로 쓴다)
- Bash heredoc 은 백슬래시를 벗긴다 — 정규식·역슬래시가 든 편집은 Edit·Write 로

## 3. 설계 결정 — 전부 [고정]

1. **별칭 범위는 문장 바닥 · 질의 괄호(`(SELECT`·`(WITH`) · 집합 연산 가지**. 안쪽은 바깥 별칭을 본다(상관 서브쿼리), 옆 가지·안쪽은 못 본다. 괄호 친 표·`ON (…)`·함수 괄호는 범위를 안 연다(`FROM (A a JOIN B b …) JOIN C c ON a.ID = c.ID` 가 살아야 한다). 버린 대안 — 괄호마다 범위: 괄호 친 표의 별칭이 바깥에서 안 보인다
2. **WITH 이름은 빈 글로 적는다** — 그 별칭으로 온 등식은 버리고, 바깥의 같은 이름 별칭도 가린다. WITH 이름은 문장마다(`;` 로 가른 토막에 `SqlTables.cteNames`)
3. **SET 가림은 그 SET 이 든 괄호가 닫히면 끝난다**(`6-68`). 버린 대안 — 「깊이 0 의 SET 만」: 괄호 안 DML(`WITH U AS (UPDATE … SET …)`)의 대입이 조인이 된다
4. **`USING` 은 표를 여는 말**(`6-66`) — `JOIN … USING (열)` 은 뒤가 `(` 라 안 읽힌다
5. **`${schema}.표` 는 `?` `.` 을 건너뛰고 뒤 이름을 표로**(`6-65`). `${a}.${b}` 는 지금처럼 `table` 미해결
6. **`#` 는 MySQL 계열이 아님이 드러난 문장에서만 연산자**(`6-69`) — `databaseId` 나 파일 이름에 oracle·tibero·postgres·mssql·sqlserver·altibase·cubrid·db2·goldilocks 가 있고 mysql·maria 가 없을 때. 모르면 지금처럼 줄 주석. `MapperIndex` 가 글의 그 `#` 를 `^` 로 바꿔 넘긴다. 버린 대안 — `SqlTables.extract` 에 방언 인자: `extract(2)` 기준선 줄이 새 줄이 되고 부르는 자리 넷을 다 고쳐야 한다
7. **길이 1 URL(`/`)은 단서가 있을 때만 링크**(`6-67`) — `kind` 가 `OTHER` 면 버린다
8. **JPQL 속성은 extends 조상으로 여덟 단계까지 찾는다**(`6-64`). 조상을 하나로 못 정하면 거기서 끝(지금처럼 `jpql` 미해결). `JpaIndex` 에는 `Cls` 의 `private` 를 떼고 호출 한 줄만 바꾼다
9. **클래스·인터페이스가 아닌 형식 이름은 「모름」**(`5-126`) — enum·record·annotation 이름을 `null` 로 적어 같은 패키지 파일로 안 넘어간다
10. **규칙 병합 함수는 `ProfileStore.mergeRules` 하나**(`0-117`) — 미리 적재와 저장이 같이 쓴다. 깨진 정규식이 든 규칙에 끔만 보내면 400(저장·백업 없음). 화면에서 못 끈다 — YAML 을 손으로 고친다(`brokenSavedRuleIs400OnRead` 와 같은 처분)
11. **기동 실패 스택은 로그 파일에만**(`8-22`) — 로거 `kr.ejg.toolbox.startup`(additivity 끔, FILE 만). 콘솔은 `[오류]` 한 줄 그대로. 버린 대안 — `LOG.error`: 콘솔에도 스택이 나와 `8-19` 와 어긋난다
12. **줄 비교 판정은 안 바꾼다**(`4-44`) — 줄 내용이 같으면 응답에 `sameLines` 와 다른 것 목록(`byteDiffs` — 줄끝·끝 줄바꿈·인코딩), 화면이 한 줄로 보여 준다. 버린 대안 — 끝 줄바꿈을 빈 줄 op 로: 골든 셋이 다 바뀌고 CRLF·LF 는 그대로 남는다
13. **못 옮기는 기본값은 「안 바뀜」 으로 치고 `[손]` 한 줄**(`1-114`) — Oracle·Tibero `DEFAULT NULL` · PG·MariaDB `DROP DEFAULT` 를 안 낸다. `modifyColumn` 에 분기를 안 더한다(바깥 클래스의 static 도우미 + 가지 없는 람다)
14. **MERGE 자리표시는 `DtoGenerator.fieldName`**(`4-42`) — `InsertGen.camel` 은 지운다
15. **`6-70` 은 확인이 먼저**(사용자 (가)) — MariaDB 에서 RED 일 때만 좁힌다. 좁히는 열쇠는 벤더 오류 코드: H2 `42121` · MySQL 계열 `1060` · MSSQL `8155`·`8156`
16. **닫기만 하는 셋** — `0-116`(지금 동작 유지) · `4-43`(R-G-14 동결) · `5-114` 원 행. S0 에서 문서로

## 4. 실행 스텝

행마다 끝은 CLAUDE.md 「Task 끝」 순서(분할표 행·이력 → `bash scripts/verify.sh` → `git commit` 따로 → `task-done`). `src/main/java` 를 고치는 행은 시험을 먼저 쓰고 RED 를 본 뒤 고친다. 커밋 제목 `fix: <번호> <무엇>`(문서만이면 `docs:`, 시험만이면 `test:`). 줄 번호는 `4c4d883` 기준 — 앞 행이 밀면 메서드 이름으로 찾는다.

공통 사다리
- `SelfCheckTest.mainMatchesBaseline` 빨강 —
  - **오른 값·새 줄**: 분기를 도우미로 뺀다. 자리는 ① 그 메서드가 든 중첩 클래스 ② 같은 클래스의 `private static` ③ `metric.classMethods` 새 줄이 생기면 같은 패키지의 새 `final class <클래스>Parts`(package-private, `private` 생성자, static 만). `JpaIndex`·`AlterGen.Run` 에는 메서드를 안 더한다
  - **내린 값·사라진 줄**: `bash scripts/mvn.sh -q test -Dtest=SelfCheckTest -Dgolden.update=true` 로 갱신하고 diff 가 행에 적힌 줄뿐인지 본다. 받는 줄 — `SqlJoins.aliases(1)` 사라짐(`6-66`) · `DdlGen.defaultOf(3)` 내림·사라짐(`1-114`) · `ProfileStore.saveCodeCheck(3)` 내림(`0-117`)
- `golden/` diff — 행의 「의도된 골든 변화」 에 적힌 것만 갱신. 그 밖은 고침이 넓다 → 고친다
- 시험 글에 실제 사업 이름·SQL 을 안 쓴다(`T89_*`·`TB_X`)
- PMD·SpotBugs·ErrorProne 은 빠른 `verify.sh` 가 안 본다 — `V-68a` 가 본다. 새 짝은 코드로 고친다(기준선에 안 더한다)

### S0 — 설계 문서 · 행 · 번들 표 (실행 세션이 했다)
1. 가지 `work/2026-11-21b` 그대로(HEAD `4c4d883` = main `1b3646e` + 닫기 하나 — 같은 PR 에 실린다)
2. 이 플랜 전체를 `design/74-bundle89.md` 로(글자 그대로, 이 S0 머리만 「실행 세션이 했다」 로)
3. `PROGRESS.md` 「번들 88 닫기」 줄의 `(이 커밋)` → `4c4d883`
4. `PLAN.md` 15장 — 「번들 89~」 행을 둘로: **「번들 89」** 행(순서 칸은 아래 「차례」 · 설계 칸 「2026-11-21(Fable, 설계 74 — `design/74-bundle89.md`. 실행 Opus high · 마무리 독립 리뷰 Fable xhigh). **번들 88 리뷰 행 열셋 · 보정 Minor 다섯.** 사용자 결정(2026-11-21): 열여덟 전부 · Csv 동결 · `5-127` 문서만 · `0-116` 지금 동작 유지 · `6-70` 확인 뒤 고침 · **머지 위임** · 시간 컷 없음」 · 설치 칸 「표본 폴더 · node · Docker · 여유 RAM 15GB」 · 상태 「대기」)과 **「번들 90~」** 행(잠재 A-33·A-37 · `5-100`·`5-31` · 조건부 `5-14`·`5-15`·`6-9` · 번들 89 가 `wip/` 로 뺀 행 · 상태 「설계 전」)
5. `PLAN.md` 16장 —
   - `5-114` 행 상태 「대기」 → 「완료 — `5-114a`·`5-114b`(번들 88 #133)」
   - `0-116` 행 상태 → 「지금 동작 유지 — 사용자 결정(2026-11-21)」, 사실 칸 끝에 ` · **처분**: 코드 안 고침 — 폐쇄망이라 자리 표시 폴더(OneDrive 류)가 드물고 링크는 넓게 막는 쪽이 안전하다`
   - `6-68` 행 사실 칸 끝에 ` · **처방**: 설계 74 §4 `6-66` 에서 같이`
   - 나머지 열한 행(`6-64`·`6-65`·`6-66`·`6-67`·`6-69`·`6-70`·`5-126`·`5-127`·`0-117`·`1-113`·`8-22`)의 사실 칸 끝에 ` · **처방**: 설계 74 §4`(`6-66` 은 「 — `6-68`·R-C-M1 을 같이」 도)
   - 표 끝에 새 행 넷(꼴 `| <번호> | <청크> | **설계 74 §4 가 원본**. <요지>(<키>). **축**: … **강제 지점**: … | — | 대기 |`):
     - `1-114` AlterGen — 못 옮기는 기본값(R-A-M2). 축 생성기(db 레인). 강제 지점 `AlterGenTest.unportableDefaultIsHandNotLive`
     - `4-42` InsertGen — MERGE 자리표시(R-A-M5). 축 생성기(db 레인). 강제 지점 `InsertGenTest.upsertPlaceholderIsTheDtoField`
     - `4-43` Csv — 칸 가운데 따옴표(R-G-14). 사실 「순수본 `parseCsv` 와 같은 동작」. 상태 「안 고침 — 동결(사용자 2026-11-21)」
     - `4-44` 줄 비교 — 줄 내용이 같은 「다름」(R-G-15). 축 화면·fs. 강제 지점 `DiffRoutesTest.sameLinesSaysWhatDiffers`
6. `PROGRESS.md` 이력 한 줄(S0 — `0-116`·`4-43`·`5-114` 닫음과 그 이유) + 「현재 상태」(진행중 = 번들 89 대기)
7. `bash scripts/verify.sh`(docs 레인) → `git commit`(따로) `docs: 설계 74 — 번들 89 행·번들 표`
- 확인: `git log --oneline -1` 이 그 커밋 · `git status --short` 깨끗 · 8절 「첫 스텝 전에」

| # | 청크 | 축 | 강제 지점 | 선행 |
|---|---|---|---|---|
| 6-64 | JpqlPaths — 조상 필드 | 분석(corpus) | `JpaIndexTest.inheritedAssociationPath` | S0 |
| 6-65 | SqlTables — `${schema}.표` | 〃 | `SqlTablesTest.golden`(셋) | S0 |
| 6-66 | SqlJoins — 별칭 범위 · MERGE USING · 괄호 안 SET(`6-68` · R-C-M1) | 〃 | `SqlJoinsTest.shapes`(열하나) | 6-65 |
| 6-69 | `#` 연산자 — 방언이 드러난 문장 | 〃 | `SqlTokensTest.hashIsAnOperatorOutsideMysql` · `MapperIndexTest.hashOperatorByDialectHint` | S0 |
| 6-67 | JspLinks — 루트 매핑 | 〃 | `JspLinksTest.rootMappingNeedsACue` | S0 |
| 5-126 | ReturnTypes — enum·record 이름 | 고침 범위(corpus) | `ReturnTypesTest.nonClassTypeNamesAreUnknown` | S0 |
| 5-127 | 정규식 리터럴 한계 | 문서 | doc-lint | S0 |
| 0-117 | 규칙 덮어쓰기 — 같은 병합 | 프로필 | `ProfileRoutesTest.booleanOverBrokenRegexIsRefused` | S0 |
| 8-22 | 기동 실패 스택 | CLI | `MainTest.startupFailureStackGoesToTheLogOnly` | S0 |
| 4-44 | 줄 내용이 같은 「다름」(R-G-15) | 화면·fs | `DiffRoutesTest.sameLinesSaysWhatDiffers` | S0 |
| 1-114 | AlterGen — 못 옮기는 기본값(R-A-M2) | 생성기(db) | `AlterGenTest.unportableDefaultIsHandNotLive` | S0 |
| 4-42 | InsertGen — MERGE 자리표시(R-A-M5) | 〃 | `InsertGenTest.upsertPlaceholderIsTheDtoField` | S0 |
| 1-113 | SqlRunner — CLOB 길이 | DB(db) | `SqlRunnerTest.clobLengthIsAskedOnlyWhenCut` | S0 |
| 6-70 | SqlProbe EXISTS 재시도 | DB(db) | `MariaMetaSourceTest.probeKeepsOrderByErrors` · `DbCorpusBase.roFileMessages` | 1-113 |
| V-68a | 정적 분석 · corpus 골든 | 4장 | `verify.sh --corpus` | 코드 행 전부 |
| R-35 | 손 판정 · 처분 표 | 4장 | compare ㊳ 절 | V-68a |
| V-68 | `--db` | 4장 | `verify.sh --db` | R-35 |

차례 — `S0→6-64→6-65→6-66→6-69→6-67→5-126→5-127→0-117→8-22→4-44→1-114→4-42→1-113→6-70→V-68a→R-35→V-68→마무리`

### 6-64 — JpqlPaths: 조상이 선언한 연관
- 대상: `core/analyze/JpqlPaths.java` · `core/analyze/JpaIndex.java`(두 줄) · `JpaIndexTest`
- 사실: `JpqlPaths.pathAt:81-91` 이 `w.decl().apply(owner)` 한 선언의 `getFieldByName` 만 본다. `JpaIndex:520` 이 `e -> types.get(e.fqcn()).decl` 을 넘긴다. `Cls`(`:97`)는 `private static final class`, 조상 찾기는 `lookup(simple, from)`(`:534`, private 인스턴스)
- 변경:
  1. `JpaIndex` — `private static final class Cls` → `static final class Cls`. `:520` 호출을 `JpqlPaths.resolve(t, n -> byEntity(n, from), types::get, this::lookup, tbl -> …, id -> …)` 로(뒤 두 람다는 그대로). 그 밖은 안 고친다
  2. `JpqlPaths` — `Walk` 의 `decl` 을 `Function<String, JpaIndex.Cls> types` 와 `BiFunction<String, JpaIndex.From, List<JpaIndex.Cls>> lookup` 으로(성분 일곱). `resolve` 의 인자도 같게(여섯). `pathAt` 의 `Optional.ofNullable(d).flatMap(c -> c.getFieldByName(field))` 를 `field(w, owner, field)` 로:
     ```java
     /** 조상 사다리 상한 */
     private static final int MAX_UP = 8;

     /** 속성 선언 — 소유 엔티티 클래스에서 extends 조상으로(@MappedSuperclass·엔티티 조상). 조상을 하나로 못 정하면 거기서 끝(6-64) */
     private static Optional<FieldDeclaration> field(Walk w, JpaIndex.Entity owner, String name) {
         JpaIndex.Cls c = w.types().apply(owner.fqcn());
         for (int up = 0; c != null && up <= MAX_UP; up++) {
             Optional<FieldDeclaration> f = c.decl.getFieldByName(name);
             if (f.isPresent()) {
                 return f;
             }
             c = parent(w, c);
         }
         return Optional.empty();
     }

     private static JpaIndex.Cls parent(Walk w, JpaIndex.Cls c) {
         if (c.decl.getExtendedTypes().isEmpty()) {
             return null;
         }
         List<JpaIndex.Cls> up = w.lookup().apply(c.decl.getExtendedTypes(0).getName().getIdentifier(), c.from());
         return up.size() == 1 ? up.get(0) : null;
     }
     ```
     javadoc 의 `@param decl` 줄을 `types`·`lookup` 으로 고친다
- 시험(먼저): `JpaIndexTest.inheritedAssociationPath` — `reservedWordEntityAndPathJoin` 과 같은 인라인 꼴. `p/Base.java`(`@MappedSuperclass public abstract class Base { @ManyToOne Lang language; }`) · `p/Desc.java`(`@Entity @Table(name = "T89_DESC") public class Desc extends Base { @Id Long id; }`) · `p/Lang.java`(`@Entity @Table(name = "T89_LANG")`). `select d from Desc d join d.language l where l.id = 1` → 표 `{T89_DESC, T89_LANG}` · 미해결 0. `select d from Desc d join d.nope n` → 표 `[T89_DESC]` · `jpql w:d.nope`
- 의도된 골든 변화: 빠른 레인 0. corpus(`V-68a`) — `analyze-shopizer*` 의 `jpql` 미해결이 주고 표 R 이 는다
- 검증: `bash scripts/mvn.sh -q test -Dtest='JpaIndexTest,SelfCheckTest,UnresolvedKindsTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus
- 근거: `JavaRules.complexity:622` 는 if·for·while·catch·`?:`·`&&`·`||`·case 만 센다 — 메서드 참조 둘은 `jpql(6)` 값을 안 바꾼다(40 그대로여야 한다)

### 6-65 — SqlTables: `${schema}.표`
- 대상: `core/analyze/SqlTables.java`(`skipEnd`) · `SqlTablesTest` · 골든 `analyze/sql-tables.json`
- 사실: `${schema}.TB` 는 낱말 `?` `.` `TB`. `table:341` 에서 `?` 가 낱말이 아니라 `table` 미해결 + `CLOSED`, 뒤 `TB` 는 안 읽힌다. iBATIS `$owner$.T` 는 `MapperIndex` 가 `${owner}.T` 로 바꿔 넘겨 같은 길이다
- 변경: `skipEnd:398` 의 조건에 `|| "?".equals(x) && at(t, i + 1, ".")` 를 더한다(뒤 `.` 은 이미 건너뛴다 → 다음 낱말이 표). javadoc 에 「스키마 자리 바인드(`${schema}.`)」
- 시험(먼저): `CASES` 끝에 —
  - `{"schemaBind", "select", "SELECT * FROM ${schema}.TB_A A JOIN ${owner}.TB_B B ON A.ID = B.ID"}` → `TB_A:R` `TB_B:R` · 미해결 `[]`
  - `{"schemaBindInsert", "insert", "INSERT INTO ${schema}.TB_A (X) VALUES (#{x})"}` → `TB_A:C`
  - `{"schemaBindThenBind", "select", "SELECT * FROM ${schema}.${table}"}` → 표 없음 · 미해결 `[table]`
- 의도된 골든 변화: `sql-tables.json` 끝에 그 셋만. corpus 0(표본에 그 꼴이 없다)
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlTablesTest,MapperIndexTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus

### 6-66 — SqlJoins: 별칭 범위 · MERGE USING · 괄호 안 SET (`6-68` · R-C-M1 을 같이)
- 대상: `core/analyze/SqlJoins.java` · `SqlJoinsTest` · 골든 `analyze/sql-joins.json` · `golden/check/self-check.txt`
- 사실: `aliases:91-127` 이 문장 전체에 별칭 표 하나를 만든다 — UNION 뒤 가지가 앞 가지의 `a`·`b` 를 덮어 앞 관계가 사라지고, 서브쿼리가 바깥 별칭을 덮어 없는 관계가 생기고, `FROM C`(WITH 이름)가 표로 든다. `USING` 은 여는 말이 아니다(`MERGE INTO T USING S ON (T.ID = S.ID)` → 0 건). `outsideSet:61-72` 는 `CAST(a.NM AS CHAR CHARACTER SET utf8)` 의 `SET` 부터 다음 WHERE·FROM·WHEN 까지 가린다
- 변경: 파일의 `extract`·`outsideSet`·`aliases` 를 아래로 바꾼다(`ref`·`plus`·`word`·`Join` 은 그대로). import 에 `ArrayDeque`·`Deque` 를 더한다. 클래스 javadoc 의 「서브쿼리 범위는 가르지 않고 문장 전체가 별칭 표 하나다」 를 3절 1·2 의 말로 고친다
  ```java
  /** 뒤 낱말이 표인 말 — USING 은 MERGE·PG DELETE 의 표(6-66). JOIN … USING (열) 은 뒤가 ( 라 표가 아니다 */
  private static final Set<String> OPENERS = Set.of("FROM", "JOIN", "INTO", "UPDATE", "USING");
  private static final Set<String> SET_OPS = Set.of("UNION", "INTERSECT", "EXCEPT", "MINUS");
  private static final Set<String> SET_END = Set.of("WHERE", "FROM", "WHEN", ";");
  private static final Set<String> QUERY = Set.of("SELECT", "WITH");

  public static List<Join> extract(String sql) {
      List<String> all = withoutBindSchema(SqlTables.tokens(RegexRule.stripComments(sql == null ? "" : sql, "sql")));
      Set<Join> out = new LinkedHashSet<>();
      int from = 0;
      int depth = 0;
      for (int i = 0; i < all.size(); i++) {
          String x = all.get(i);
          depth = Math.max(0, depth + ("(".equals(x) ? 1 : ")".equals(x) ? -1 : 0));
          if (depth == 0 && ";".equals(x)) {
              statement(all.subList(from, i), out); // WITH 이름·별칭은 문장마다
              from = i + 1;
          }
      }
      statement(all.subList(from, all.size()), out);
      return new ArrayList<>(out);
  }

  /** 문장 하나의 등식 — 옛 extract 의 훑기 그대로, 별칭만 자리의 범위에서 푼다 */
  private static void statement(List<String> raw, Set<Join> out) {
      Scopes sc = Scopes.of(raw, SqlTables.cteNames(raw));
      List<String> t = outsideSet(raw); // 길이가 같다 — 자리가 raw 와 맞는다
      for (int i = 0; i + 2 < t.size(); i++) {
          // … 옛 extract 의 for 몸 그대로. 두 줄만 바꾼다:
          // String ta = sc.table(i, t.get(i));
          // String tb = sc.table(eq + 1, t.get(eq + 1));
      }
  }

  /** 표 자리의 스키마 바인드 「? .」 를 뗀다 — ${schema}.TB a 의 별칭이 읽히게(6-65 와 같은 꼴) */
  static List<String> withoutBindSchema(List<String> t) {
      List<String> out = new ArrayList<>(t.size());
      int i = 0;
      while (i < t.size()) {
          boolean bind = "?".equals(t.get(i)) && i > 0 && i + 2 < t.size() && ".".equals(t.get(i + 1)) && word(t.get(i + 2))
                  && (OPENERS.contains(t.get(i - 1)) || ",".equals(t.get(i - 1)));
          if (!bind) {
              out.add(t.get(i));
          }
          i += bind ? 2 : 1;
      }
      return out;
  }

  /** SET 절 안 토큰을 「~」 로 — SET 다음부터 WHERE·FROM·WHEN·; 앞까지, 또는 그 SET 이 든 괄호가 닫힐 때까지(6-68 — CAST(… CHARACTER SET utf8)) */
  static List<String> outsideSet(List<String> t) {
      List<String> out = new ArrayList<>(t.size());
      int depth = 0;
      int on = -1; // SET 을 만난 괄호 깊이. -1 = 꺼짐
      for (String x : t) {
          depth += "(".equals(x) ? 1 : ")".equals(x) ? -1 : 0;
          if (SET_END.contains(x) || depth < on) {
              on = -1;
          }
          out.add(on >= 0 ? "~" : x);
          if ("SET".equals(x) && on < 0) {
              on = depth;
          }
      }
      return out;
  }

  /**
   * 별칭 범위(R-C-M1) — 문장 바닥, 질의 괄호((SELECT·(WITH), 집합 연산(UNION·INTERSECT·EXCEPT·MINUS)의 가지가 범위 하나.
   * 안쪽 범위는 바깥 별칭을 본다(상관 서브쿼리). 옆 가지·안쪽 범위의 별칭은 못 본다. 괄호 친 표·ON (…)·함수 괄호는 범위를 안 연다
   */
  private static final class Scopes {
      private final List<Map<String, String>> alias = new ArrayList<>();
      private final List<Integer> parent = new ArrayList<>();
      /** 토큰 자리 → 범위 */
      private final int[] of;

      private Scopes(int size) {
          of = new int[size];
      }

      static Scopes of(List<String> t, Set<String> cte) {
          Scopes s = new Scopes(t.size());
          Deque<Integer> opened = new ArrayDeque<>(); // ( 마다 그때의 범위
          int cur = s.open(-1);
          for (int i = 0; i < t.size(); i++) {
              String x = t.get(i);
              if ("(".equals(x)) {
                  opened.push(cur);
                  cur = i + 1 < t.size() && QUERY.contains(t.get(i + 1)) ? s.open(cur) : cur;
              } else if (")".equals(x) && !opened.isEmpty()) {
                  cur = opened.pop();
              } else if (SET_OPS.contains(x)) {
                  cur = s.open(s.parent.get(cur)); // 옆 가지 — 부모가 같다
              }
              s.of[i] = cur;
          }
          s.fill(t, cte);
          return s;
      }

      private int open(int p) {
          alias.add(new HashMap<>());
          parent.add(p);
          return alias.size() - 1;
      }

      /** i 자리에서 보이는 별칭의 표 — 제 범위에서 바깥으로. 없거나 WITH 이름이면 null */
      String table(int i, String name) {
          for (int s = of[i]; s >= 0; s = parent.get(s)) {
              String v = alias.get(s).get(name);
              if (v != null) {
                  return v.isEmpty() ? null : v;
              }
          }
          return null;
      }

      /** 여는 말 뒤 표 낱말과 별칭을 그 낱말의 범위에 적는다. 쉼표 조인은 다음도 표 */
      private void fill(List<String> t, Set<String> cte) {
          boolean expect = false;
          int i = 0;
          while (i < t.size()) {
              String x = t.get(i);
              boolean table = expect && word(x) && !SqlTables.STOP.contains(x);
              expect = OPENERS.contains(x);
              if (table) {
                  i = entry(t, i, cte);
                  expect = i + 1 < t.size() && ",".equals(t.get(i + 1));
                  i += expect ? 1 : 0;
              }
              i++;
          }
      }

      /** i 의 표(스키마 사슬은 끝 마디 — 표 이름도 자기 별칭)와 뒤 별칭을 적고 먹은 끝 자리를 돌려준다. WITH 이름은 빈 글 — 표가 아니고 바깥의 같은 이름을 가린다 */
      private int entry(List<String> t, int i, Set<String> cte) {
          Map<String, String> m = alias.get(of[i]);
          int k = i;
          while (k + 2 < t.size() && ".".equals(t.get(k + 1)) && word(t.get(k + 2))) {
              k += 2;
          }
          String real = cte.contains(t.get(k)) ? "" : t.get(k);
          m.put(t.get(k), real);
          int j = k + 1 < t.size() && "AS".equals(t.get(k + 1)) ? k + 2 : k + 1;
          if (j < t.size() && word(t.get(j)) && !SqlTables.STOP.contains(t.get(j))) {
              m.put(t.get(j), real);
              return j;
          }
          return k;
      }
  }
  ```
- 시험(먼저): `SqlJoinsTest.shapes` 의 맵에 더한다(기대는 (tableA.colA — tableB.colB), 정렬은 `Join` 규칙) —
  | 열쇠 | SQL | 기대 |
  |---|---|---|
  | `unionBranches` | `SELECT a.X FROM T1 a JOIN T2 b ON a.ID = b.ID UNION ALL SELECT a.X FROM T3 a JOIN T4 b ON a.ID = b.ID` | T1.ID—T2.ID · T3.ID—T4.ID |
  | `shadowedAlias` | `SELECT * FROM T1 a JOIN T2 b ON a.ID = b.ID WHERE a.X IN (SELECT a.X FROM T3 a)` | T1.ID—T2.ID |
  | `cteName` | `WITH C AS (SELECT ID FROM T1) SELECT * FROM C JOIN T2 ON C.ID = T2.ID` | 없음 |
  | `cteAlias` | `WITH C AS (SELECT ID FROM T1) SELECT * FROM C x JOIN T2 y ON x.ID = y.ID` | 없음 |
  | `cteInnerJoin` | `WITH C AS (SELECT a.ID FROM T1 a JOIN T3 b ON a.ID = b.ID) SELECT * FROM C` | T1.ID—T3.ID |
  | `parenTable` | `SELECT * FROM (A a JOIN B b ON a.ID = b.A_ID) JOIN C c ON a.ID = c.A_ID` | A.ID—B.A_ID · A.ID—C.A_ID |
  | `twoStatements` | `SELECT * FROM T1 a JOIN T2 b ON a.ID = b.ID; SELECT * FROM T3 a JOIN T4 b ON a.ID = b.ID` | T1.ID—T2.ID · T3.ID—T4.ID |
  | `mergeUsing` | `MERGE INTO TB_T T USING TB_S S ON (T.ID = S.ID) WHEN MATCHED THEN UPDATE SET T.X = S.X` | TB_S.ID—TB_T.ID |
  | `castCharacterSet` | `SELECT * FROM A a JOIN B b ON CAST(a.NM AS CHAR CHARACTER SET utf8) = b.NM AND a.ID = b.ID` | A.ID—B.ID |
  | `setInParenDml` | `WITH U AS (UPDATE A SET X = B.X FROM B WHERE A.ID = B.ID RETURNING A.ID) SELECT * FROM U JOIN C ON U.ID = C.ID` | A.ID—B.ID |
  | `bindSchema` | `SELECT * FROM ${s}.TB_A a JOIN ${s}.TB_B b ON a.ID = b.ID` | TB_A.ID—TB_B.ID |
  RED 는 골든 갱신 앞에 본다 — 새 열쇠의 지금 출력이 위 기대와 다르다(`unionBranches` 는 T3—T4 하나 · `shadowedAlias` 는 T2—T3 · `cteName` 은 C—T2 · `mergeUsing`·`castCharacterSet` 은 없음). 고친 뒤 `-Dgolden.update=true` 로 쓰고 diff 를 표와 댄다
- 의도된 골든 변화: `sql-joins.json` — 새 열쇠 열하나 + **`mergeSet` 이 `[]` → S.ID—T.ID**(USING). 그 밖 열쇠 열둘은 그대로여야 한다. `self-check.txt` — `SqlJoins.java|metric.complexity|… aliases(1)` 줄이 사라진다. `MapperIndexTest.golden`·`AnalyzeRunner` 골든은 0 기대 — 바뀌면 그 문장이 위 꼴 가운데 무엇인지 이력에
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlJoinsTest,MapperIndexTest,SqlTablesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-65(같은 꼴의 스키마 바인드). 레인 corpus
- 한계(이력·javadoc 에): 파생 표 별칭(`) v`)의 관계는 안 잡는다 · 괄호 안 DML 의 별칭은 바깥 범위에 든다 · `GO` 는 문장 경계로 안 본다
- 끝에 — 분할표 `6-68` 행도 「완료 — `6-66` 에서 같이」 로, 이력에 R-C-M1 닫음을 적는다

### 6-69 — `#` 는 MySQL 계열이 아님이 드러난 문장에서 연산자
- 대상: `core/text/SqlTokens.java` · `core/analyze/MapperIndex.java` · `SqlTokensTest` · `MapperIndexTest`
- 사실: `SqlTokens.wordAt:110` — `#` 뒤가 공백이고 앞이 낱말 글자가 아니면 줄 끝까지 버린다(`hashComment`). PG `SELECT a # b FROM t` 는 `FROM t` 가 빠져 표 0·미해결 0
- 변경:
  1. `SqlTokens` 에 —
     ```java
     /** # 줄 주석이 없는 방언의 글 — 주석으로 읽힐 # 를 연산자 글자(^)로 바꾼다(PG 비트 XOR `a # b`, 6-69). 길이·자리는 그대로, #{…}·#TEMP·COL# 은 안 건드린다 */
     public static String hashAsOperator(String s) {
         StringBuilder sb = null;
         for (int i = s.indexOf(HASH); i >= 0; i = s.indexOf(HASH, i + 1)) {
             if (hashComment(s, i)) {
                 sb = sb == null ? new StringBuilder(s) : sb;
                 sb.setCharAt(i, '^');
             }
         }
         return sb == null ? s : sb.toString();
     }
     ```
  2. `MapperIndex` 에 —
     ```java
     /** MySQL 계열이 아님을 드러내는 낱말(databaseId·파일 이름) — 이 문장의 # 는 줄 주석이 아니다(6-69) */
     private static final List<String> NOT_MYSQL = List.of("oracle", "tibero", "postgres", "mssql", "sqlserver", "altibase", "cubrid", "db2", "goldilocks");

     /** 방언이 드러난 문장이면 # 를 연산자로 바꾼 글, 모르거나 MySQL 계열이면 그대로(지금처럼 # 줄 주석) */
     static String hashText(Raw r, String text) {
         String f = r.file == null ? "" : r.file.replace('\\', '/');
         String hint = (r.databaseId + " " + f.substring(f.lastIndexOf('/') + 1)).toLowerCase(Locale.ROOT);
         boolean other = !hint.contains("mysql") && !hint.contains("maria") && NOT_MYSQL.stream().anyMatch(hint::contains);
         return other ? SqlTokens.hashAsOperator(text) : text;
     }
     ```
     `:191` `String text = hashText(r, include(r, r.text, fragments, unresolved, seen, 0));` · `:208` `SqlTables.extract(hashText(r, include(r, key, fragments, unresolved, seen, 0)), "select")`. 그 메서드에 분기를 안 더한다
- 시험(먼저):
  - `SqlTokensTest.hashIsAnOperatorOutsideMysql` — `tokens(hashAsOperator("SELECT a # b FROM t"))` = `[SELECT, A, ^, B, FROM, T]` · `hashAsOperator("x = #{a} AND y # z")` = `"x = #{a} AND y ^ z"` · `hashAsOperator("FROM #TEMP WHERE COL# = 1")` 은 그대로 · `tokens("SELECT a # b FROM t")` 는 지금처럼 `[SELECT, A]`(모르면 주석)
  - `MapperIndexTest.hashOperatorByDialectHint` — `ibatisSqlMap` 과 같은 인라인 꼴. `T89_SQL_postgres.xml`(`<mapper namespace="Pg"><select id="x">SELECT a # b FROM T89_A</select></mapper>`) → `{T89_A=R}` · `T89_SQL_mysql.xml`(namespace `My`, 같은 문장) → `{}` · `T89_SQL.xml`(namespace `Ora`, `<select id="x" databaseId="oracle">`) → `{T89_A=R}`
- 의도된 골든 변화: 빠른 레인 0. corpus(`V-68a`) 0 기대(표본에 혼자 선 `#` 가 없다) — 바뀌면 방언 이름이 든 매퍼에서 `#` 뒤에 있던 표가 **느는 것**만
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlTokensTest,MapperIndexTest,SqlTablesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus
- 한계: 방언을 모르는 매퍼(이름·`databaseId` 에 낱말 없음)의 PG `#` 는 지금처럼 주석 · `SqlLint`·JavaGraph·JpaIndex 네이티브 질의는 방언을 몰라 그대로

### 6-67 — JspLinks: 루트 매핑 `/`
- 대상: `core/analyze/JspLinks.java`(`matched`) · `JspLinksTest`
- 사실: `matched:150` — 매핑 URL 에 `/` 가 있으면 `path.split('/')`·`a + '/' + b` 의 따옴표 글 `/` 가 전부 링크(꼴 `other`)다
- 변경: `matched` 의 끝 `if` 를 —
  ```java
  if (c.startsWith("/") && !c.endsWith(".do") && programUrls.contains(c)) {
      LinkKind k = kind(text, m.start(2));
      if (c.length() > 1 || k != LinkKind.OTHER) { // 6-67 — 「/」 한 글자는 단서(href·action·url …)가 있을 때만
          urls.computeIfAbsent(c, x -> new TreeSet<>()).add(k.code());
      }
  }
  ```
- 시험(먼저): `JspLinksTest.rootMappingNeedsACue` — `extract(new Source("a.jsp", "<script>var p = path.split('/'); var q = a + '/' + b;</script>", …), Set.of("/"))` 의 링크 `[]` · `extract(…"<a href=\"/\">home</a><a href=\"${ctx}/\">h</a>"…, Set.of("/"))` 의 링크 `[Link("/", "link")]`
- 의도된 골든 변화: 0(표본에 루트 매핑이 있으면 `V-68a` 에서 `/` 링크가 **주는 것**만)
- 검증: `bash scripts/mvn.sh -q test -Dtest='JspLinksTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus
- 한계: 단서 창(200자) 안에 `href` 가 있는 줄의 `'/'` 리터럴은 여전히 링크다

### 5-126 — ReturnTypes: 클래스·인터페이스가 아닌 형식 이름은 모름
- 대상: `core/check/ReturnTypes.java`(`shapes`) · `ReturnTypesTest`
- 사실: `shapes:115` 가 `ClassOrInterfaceDeclaration` 만 색인한다 — 묻는 파일에 중첩 `enum Status` 가 있어도 `here` 에 없어 `locate` 가 같은 패키지 `Status.java` 로 간다
- 변경: `shapes` 의 for 뒤에 —
  ```java
  // 5-126 — enum·record·annotation 이름은 「모름」: 같은 이름의 같은 패키지 파일로 안 넘어간다
  cu.findAll(EnumDeclaration.class).forEach(d -> out.put(d.getNameAsString(), null));
  cu.findAll(RecordDeclaration.class).forEach(d -> out.put(d.getNameAsString(), null));
  cu.findAll(AnnotationDeclaration.class).forEach(d -> out.put(d.getNameAsString(), null));
  ```
  클래스 javadoc 에 한 줄(「클래스·인터페이스가 아닌 형식은 모름」)
- 시험(먼저): `ReturnTypesTest.nonClassTypeNamesAreUnknown` — `importedVoAndSuper` 와 같은 꼴(`@TempDir`·`finder`). `src/main/java/p/Status.java`(`public class Status { public String code() { return ""; } }`) · `p/R.java`(`public class R { public String code() { return ""; } }`) · 묻는 파일 `p/C.java` = `class C { enum Status { A; int code() { return 0; } } record R(int code) { } }` → `f.of(C_JAVA, cu(c), "Status", "code", 0)`·`…"R"…` 둘 다 `Optional.empty()`. 중첩 형식이 없는 `p/D.java` 에서 물으면 `Optional.of("String")`(같은 패키지 길은 그대로)
- 의도된 골든 변화: 빠른 레인 0. corpus `cleanup-*.json` — `steps.value` 가 **주고** `RECEIVER` 건너뜀이 **느는 것**만
- 검증: `bash scripts/mvn.sh -q test -Dtest='ReturnTypesTest,CleanupTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus

### 5-127 — 정규식 리터럴 한계(문서만)
- 대상: `docs/pure-code-check.md`
- 변경:
  1. 4절 2번의 `java · js · …` 줄 아래에 새 줄 — `   - 한계 — 정규식 리터럴은 따로 안 읽는다. 리터럴 안 `/*`·`//`(`/^\/*/`)는 주석 시작으로 읽혀 뒤가 빈다(백엔드·순수본 같다, 5-127)`
  2. 9절 「표식」 줄 아래에 새 줄 — `- 한계 — 표식 줄에 따옴표가 든 정규식 리터럴(`s.replace(/"/g, '')`)이 있으면 문자열이 열린 것으로 읽혀 표식을 못 본다. 그 줄은 윗줄에 `tb-ignore-next` 로 단다(5-127)`
- 검증: `bash scripts/verify.sh`(docs 레인 — doc-lint) · `bash scripts/mvn.sh -q test -Dtest=CheckRulesTest`(9절을 읽는 시험이 그대로 초록)
- 의존: S0. 코드·골든 변화 0
- 커밋 `docs: 5-127 정규식 리터럴 한계`

### 0-117 — 규칙 덮어쓰기: 미리 적재와 저장이 같은 병합
- 대상: `core/profile/ProfileStore.java` · `web/ProfileRoutes.java`(`putCodeCheck`) · `ProfileRoutesTest` · `golden/check/self-check.txt`
- 사실: `ProfileRoutes:100` `CheckRoutes.load(before, req.groups(), req.rules())` 는 요청 값이 통째로 덮고(`r.putAll`), `ProfileStore.saveCodeCheck:187-203` 은 Boolean 이 오면 옛 `{regex}`·`{params}` 맵에 `enabled` 만 바꾼다. 손으로 깨뜨린 `{regex: "["}` 에 `false` 만 보내면 미리 적재는 지나고 깨진 정규식이 그대로 저장된다
- 변경:
  1. `ProfileStore` 에 —
     ```java
     /**
      * 규칙 덮어쓰기 병합 — 들어온 값이 켬·끔(Boolean)뿐이고 앞 값이 {regex}·{params} 맵이면 그 맵의 enabled 만 바꾼다(5-32c), 그 밖은 들어온 값이 덮는다.
      * 미리 적재(ProfileRoutes.putCodeCheck)와 저장(saveCodeCheck)이 같이 쓴다(0-117)
      */
     public static java.util.Map<String, Object> mergeRules(java.util.Map<String, Object> before, java.util.Map<String, Object> incoming) {
         java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
         if (before != null) {
             out.putAll(before);
         }
         if (incoming != null) {
             incoming.forEach((id, v) -> out.put(id, mergedRule(out.get(id), v)));
         }
         return out;
     }

     private static Object mergedRule(Object old, Object v) {
         if (v instanceof Boolean on && old instanceof java.util.Map<?, ?> m && (m.containsKey("regex") || m.containsKey("params"))) {
             java.util.Map<String, Object> keep = new java.util.LinkedHashMap<>();
             m.forEach((k, x) -> keep.put(String.valueOf(k), x));
             keep.put("enabled", on);
             return keep;
         }
         return v;
     }
     ```
     `saveCodeCheck:187-203` 을 `java.util.Map<String, Object> mergedRules = mergeRules(before.codecheck() == null ? null : before.codecheck().rules(), rules);` 한 줄로(위 주석 두 줄은 `mergeRules` javadoc 으로 갔다)
  2. `ProfileRoutes.putCodeCheck:100` — `CheckRoutes.load(before, req.groups(), ProfileStore.mergeRules(before.codecheck() == null ? null : before.codecheck().rules(), req.rules()));`
- 시험(먼저): `ProfileRoutesTest.booleanOverBrokenRegexIsRefused` — `badRuleRegexIsRefusedBeforeSave` 와 같은 꼴(활성 `"t"`). 프로필은 `brokenSavedRuleIs400OnRead` 의 글(`…codecheck:\n  rules:\n    common.debugger:\n      regex: "["\n` — 그 시험이 이 프로필로 기동이 됨을 보인다). `PUT /api/profiles/t/codecheck` `{"rules":{"common.debugger":false}}` → 400 · 본문에 `저장하지 않았다` 와 `common.debugger` · 파일 글자 그대로 · `out` 아래 백업 없음. 단위 단언 하나 더 — `ProfileStore.mergeRules(Map.of("a", Map.of("regex", "x")), Map.of("a", false))` = `{a={regex=x, enabled=false}}`
- 근거: `RuleSet.load:124` 는 꺼진 규칙도 `validate` 한다 — 병합된 `{regex: "[", enabled: false}` 가 `BadRule` 이다
- 의도된 골든 변화: `self-check.txt` 의 `ProfileStore.java|metric.complexity|… saveCodeCheck(3)` 값이 내린다(15 이하면 줄이 사라진다)
- 검증: `bash scripts/mvn.sh -q test -Dtest='ProfileRoutesTest,ProfileStoreTest,CheckRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 java

### 8-22 — Serve: 기동 실패 스택을 로그 파일에
- 대상: `cli/Serve.java` · `src/main/resources/logback.xml` · `MainTest`
- 사실: `Serve:72-75` 가 `IllegalStateException` 을 `[오류] 메시지` 한 줄로 끝낸다 — 뜻밖의 버그도 스택이 어디에도 없다. 루트 로거는 CONSOLE·FILE 둘 다라 `LOG.error` 는 콘솔에도 스택을 낸다
- 변경:
  1. `logback.xml` — `<root>` 앞에
     ```xml
     <!-- 8-22 — 기동 실패 스택은 파일에만. 콘솔은 Serve 의 [오류] 한 줄 -->
     <logger name="kr.ejg.toolbox.startup" level="ERROR" additivity="false">
       <appender-ref ref="FILE"/>
     </logger>
     ```
  2. `Serve` — `static final String STARTUP_LOG = "kr.ejg.toolbox.startup";`, `catch (IllegalStateException e)` 의 첫 줄에 `LoggerFactory.getLogger(STARTUP_LOG).error("기동 실패", e);`(`[오류]` 줄·끝 코드 1 은 그대로). `Db.LockedException` 가지는 안 고친다
- 시험(먼저): `MainTest.startupFailureStackGoesToTheLogOnly` — `serveRefusesBadPort` 와 같은 꼴로 `serve --port 0 --no-browser --data-dir <tmp>/a;b --profiles-dir <tmp>/profiles` 를 돌린다(`;` 든 data 폴더 — `Db.java:31-32` 가 `IllegalStateException` 을 던진다). 단언: 끝 코드 1 · logback `Logger`(`(ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Serve.STARTUP_LOG)`)에 `ListAppender` 를 달아 ERROR 한 건이고 `getThrowableProxy() != null` · 그 로거 `isAdditive()` 가 거짓 · `getAppender("FILE") != null`. 끝에 `detachAppender`
- 검증: `bash scripts/mvn.sh -q test -Dtest='MainTest,BatchCliTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 java
- 사다리: `;` 든 폴더가 `unwritable` 에서 먼저 걸려 끝 코드 1 인데 로그가 0 건이면, data 폴더 안에 `toolbox.mv.db` 라는 **폴더**를 미리 만들어 H2 열기를 실패시킨다(그것이 `IllegalStateException` 이 아니면 7절)

### 4-44 — 줄 비교: 줄 내용이 같은 「다름」 (R-G-15)
- 대상: `web/DiffRoutes.java` · `src/main/resources/tools/dev_tools_ext.js`(`fdFile`) · `DiffRoutesTest`
- 사실: 폴더 비교는 바이트(`FolderDiff` — 크기·sha256)로 「다름」, 줄 비교(`LineDiff`)는 CRLF·LF 와 끝 줄바꿈 하나를 같게 본다 — 끝 줄바꿈만 다른 파일을 누르면 전 줄이 `=` 이고 까닭이 안 나온다
- 변경:
  1. `DiffRoutes` — `/api/diff/file` 몸을 `private static void file(io.javalin.http.Context ctx, LocalFiles files)` 로 빼고(`app.get("/api/diff/file", ctx -> file(ctx, files));`) 응답에 둘을 더한다:
     ```java
     boolean same = !r.tooBig() && r.ops().stream().allMatch(o -> "=".equals(o.op()));
     body.put("sameLines", same);
     body.put("byteDiffs", same ? byteDiffs(a, b) : List.of());
     ```
     ```java
     /** 줄 내용이 같은 두 파일에서 다른 것 — 줄끝 · 끝 줄바꿈 · 인코딩(R-G-15). 셋 다 같으면 빈 목록(BOM 등 바이트만) */
     static List<String> byteDiffs(LocalFiles.Text a, LocalFiles.Text b) {
         List<String> out = new java.util.ArrayList<>();
         if (!java.util.Objects.equals(a.lineEnding(), b.lineEnding()) || a.mixed() != b.mixed()) {
             out.add("줄끝 " + a.lineEnding() + (a.mixed() ? "(섞임)" : "") + " · " + b.lineEnding() + (b.mixed() ? "(섞임)" : ""));
         }
         if (endsWithNewline(a.text()) != endsWithNewline(b.text())) {
             out.add("끝 줄바꿈 " + (endsWithNewline(a.text()) ? "A 만" : "B 만"));
         }
         if (!java.util.Objects.equals(a.encoding(), b.encoding())) {
             out.add("인코딩 " + a.encoding() + " · " + b.encoding());
         }
         return out;
     }

     private static boolean endsWithNewline(String s) {
         return s != null && s.endsWith("\n");
     }
     ```
     클래스 javadoc 의 응답 꼴에 `sameLines, byteDiffs` 를 더한다
  2. `dev_tools_ext.js` `fdFile` — `tooBig` 가지 뒤, 표를 만들기 앞에:
     ```js
     if (r.sameLines) {
       var n = document.createElement('div'); n.className = 'ext-card';
       n.textContent = '줄 내용은 같다 — 다른 것: ' + (r.byteDiffs.length ? r.byteDiffs.join(' / ') : '바이트(BOM 등)');
       box.appendChild(n);
     }
     ```
- 시험(먼저): `DiffRoutesTest.sameLinesSaysWhatDiffers` — `foldersThenFile` 과 같은 꼴. `a/x.txt` = `"1\n2\n"` · `b/x.txt` = `"1\n2"` → `/api/diff/folders` 는 `DIFF`, `/api/diff/file` 은 `sameLines` 참 · `byteDiffs` = `["끝 줄바꿈 A 만"]`. `a/y.txt` = `"1\r\n2\r\n"` · `b/y.txt` = `"1\n2\n"` → `byteDiffs` 첫 항목이 `줄끝 CRLF · LF`. `foldersThenFile` 의 파일(줄이 다르다)은 `sameLines` 거짓 · `byteDiffs` `[]`
- 의도된 골든 변화: 0(`text/diff-*.json` 은 `LineDiff.Result` 라 안 바뀐다)
- 검증: `bash scripts/mvn.sh -q test -Dtest='DiffRoutesTest,FsRoutesTest,SmokeHtmlUnitTest,ToolsFolderTest,LineDiffTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 java·tools(+ corpus — `resources/tools`)

### 1-114 — AlterGen: 못 옮기는 기본값 (R-A-M2)
- 대상: `core/gen/DdlGen.java` · `core/gen/AlterGen.java` · `AlterGenTest` · `golden/check/self-check.txt`
- 사실: `AlterGen.Run.modifyColumn:331·338` — 새 기본값이 대상으로 못 옮기는 식이면 `g.defaultOf` 가 경고를 더하고 `null` 을 준다. 그런데 `defChanged` 는 참이라 Oracle·Tibero 는 `MODIFY (GUID DEFAULT NULL)`(`:388`), PG·MariaDB 는 `DROP DEFAULT`(`:376`·`:350`)를 **살아 있는 문장**으로 낸다 — 앞 기본값이 지워진다
- 변경:
  1. `DdlGen`(바깥 클래스 static) —
     ```java
     /** 기본값 글의 겉 — 앞뒤 공백과 통째로 싼 괄호(SQL Server 「((0))」·「(getdate())」)를 벗긴다 */
     static String bareDefault(String v) {
         String s = v.trim();
         while (s.startsWith("(") && s.endsWith(")") && closes(s)) {
             s = s.substring(1, s.length() - 1).trim();
         }
         return s;
     }

     /** 기본값이 있는데(빈 값·NULL 아님) 대상 글로 못 옮겼나 — moved 는 defaultOf 가 준 값(R-A-M2) */
     static boolean lostDefault(Column c, String moved) {
         return moved == null && !blank(c.defaultValue()) && !NULL_WORD.equalsIgnoreCase(bareDefault(c.defaultValue()));
     }
     ```
     `Gen.defaultOf:440-443`(`v = v.trim();` + while) 을 `v = bareDefault(v);` 로
  2. `AlterGen`(바깥 클래스 static — `Run` 에는 안 더한다) —
     ```java
     /** 기본값이 바뀌었나. 바뀌었는데 새 값이 대상으로 못 옮기는 식이면 [손] 을 남기고 거짓 — DEFAULT NULL·DROP DEFAULT 로 앞 기본값을 지우지 않는다(R-A-M2) */
     private static boolean defaultChanged(DdlGen.Gen g, Table tb, Column old, Column neu, java.util.function.Consumer<String> hand) {
         if (Objects.equals(Run.norm(old.defaultValue()), Run.norm(neu.defaultValue()))) {
             return false;
         }
         if (DdlGen.lostDefault(neu, g.defaultOf(tb, neu, g.type(tb, neu)))) {
             hand.accept("컬럼 " + neu.name() + " 기본값 — 식을 대상 문법으로 못 옮겼다, 손으로 쓴다(앞 기본값은 그대로)");
             return false;
         }
         return true;
     }
     ```
     `modifyColumn:331` 을 `boolean defChanged = defaultChanged(g, tb, old, neu, why -> hand(out, why));` 로. 그 밖 줄은 안 고친다
- 시험(먼저): `AlterGenTest.unportableDefaultIsHandNotLive` — 픽스처에서 한 컬럼의 기본값만 못 옮기는 식(예 `uuid_generate_v4()`)으로 바꾼 B 를 만들고(`with(c, len, nullable, def, comment)` 도우미) 대상 `oracle`·`postgresql`·`mariadb` 셋에서: SQL 에 그 컬럼의 `DEFAULT NULL`·`DROP DEFAULT` 가 **없고** `-- [손] 컬럼 … 기본값 — 식을 대상 문법으로 못 옮겼다` 가 있고, 경고에 「기본값 식은 옮기지 않았다」 가 한 줄, `review` 가 1 이상
- 의도된 골든 변화: `alter-*.sql` 다섯 diff 0(기본값을 지운 꼴은 그대로) · `self-check.txt` 의 `DdlGen.java|… defaultOf(3)` 값이 내린다(15 이하면 사라진다). `modifyColumn(7)` 40·`Run` 메서드 40 은 그대로여야 한다
- 검증: `bash scripts/mvn.sh -q test -Dtest='AlterGenTest,DdlGenTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 db·corpus
- 한계: MariaDB `MODIFY COLUMN`(형식·NULL 이 같이 바뀔 때)은 컬럼 정의를 통째로 다시 써 식 기본값이 빠진다 — `[손]` 줄이 같이 나온다(기본값이 바뀐 판) · MSSQL 가지는 `defChanged` 거짓이라 기본값 문장·`[손]`(제약 이름) 대신 이 `[손]` 한 줄

### 4-42 — InsertGen: MERGE 자리표시 = DTO 필드 이름 (R-A-M5)
- 대상: `core/gen/InsertGen.java` · `InsertGenTest`
- 사실: `InsertGen:456` `c -> "#{" + camel(c.name()) + "}"` — `InsertGen.camel:502` 은 자바 키워드·숫자 시작을 안 다룬다. 컬럼 `CLASS` 의 DTO 필드는 `class_`(`DtoGenerator.fieldName`)인데 자리표시는 `#{class}` 다
- 변경: `:456` 을 `c -> "#{" + DtoGenerator.fieldName(c.name()) + "}"` 로, `InsertGen.camel`(`:502-511`)을 지운다(부르는 자리가 없다)
- 시험(먼저): `InsertGenTest.upsertPlaceholderIsTheDtoField` — 컬럼 `ID`(PK)·`CLASS`·`ORDER_NO`·`1ST_NM` 인 표로 `Options(… upsert=true …)` → SQL 에 `#{class_}`·`#{orderNo}`·`#{_1stNm}` 이 있고 `#{class}` 가 없다. 기대 글자는 `DtoGenerator.fieldName` 을 불러 만든다(같은 함수임을 못 박는다)
- 의도된 골든 변화: 0 기대(`upsertThreeShapes`·`hr-insert*` — 보통 이름은 두 함수가 같다). 바뀌면 그 컬럼 이름이 키워드·숫자 시작·겹친 `_` 인지 이력에
- 검증: `bash scripts/mvn.sh -q test -Dtest='InsertGenTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 db·corpus

### 1-113 — SqlRunner: CLOB 길이는 잘랐을 때만
- 대상: `core/sqlrun/SqlRunner.java` · `SqlRunnerTest`
- 사실: `clob:138` 이 값마다 `c.length()` 를 부른다 — 왕복이 하나 늘고 `length()` 를 못 하는 드라이버는 짧은 CLOB 도 조회가 실패한다. `cut:161` 의 꼬리는 SQLXML 에도 「CLOB」 이라 적는다
- 변경:
  ```java
  return cut(sb.toString(), sb.length() >= MAX_TEXT ? c.length() : sb.length(), "CLOB"); // 1-113 — 상한까지 읽었을 때만 전체 길이를 묻는다
  ```
  `cut(String s, long total, String what)` — 꼬리 ` …(" + what + " " + total + "자 — 앞 " + MAX_TEXT + "자만)`. SQLXML 자리는 `cut(xs, xs == null ? 0 : xs.length(), "XML")`
- 시험(먼저): `SqlRunnerTest.clobLengthIsAskedOnlyWhenCut` — `java.lang.reflect.Proxy` 로 만든 `Clob`(`getCharacterStream` → `new StringReader("abc")`, 그 밖 메서드는 `SQLFeatureNotSupportedException`) 에 `SqlRunner.value(clob)` = `"abc"`. 같은 꼴 `SQLXML`(`getString` → `"x".repeat(MAX_TEXT + 5)`) 은 끝이 ` …(XML 1000005자 — 앞 1000000자만)`. `longClobIsCut` 는 그대로 초록(`1500000자`)
- 의도된 골든 변화: 0
- 검증: `bash scripts/mvn.sh -q test -Dtest=SqlRunnerTest` → `bash scripts/verify.sh`
- 의존: S0. 레인 db·corpus

### 6-70 — SqlProbe: EXISTS 재시도 — MariaDB 확인 뒤 좁히기
- 대상: `core/dialect/MariaMetaSourceTest`(시험) · `web/DbCorpusBase.java`(`roFileMessages`) · 조건부 `core/analyze/SqlProbe.java` + `SqlProbeTest`
- 사실: `SqlProbe.probe:293` — 감싼 꼴이 어떤 `SQLException` 으로든 다 실패하면 `exists()` 가 `SELECT 1 [FROM DUAL] WHERE 1=0 AND EXISTS (원문)` 으로 한 번 더 묻는다(H2·MSSQL·MySQL 계열). MySQL 계열이 EXISTS 안 ORDER BY 를 이름을 풀기 전에 지우면 `ORDER BY 없는컬럼` 이 PASS 가 된다
- 스텝:
  1. `bash scripts/docker-up.sh`. `MariaMetaSourceTest` 에 `probeKeepsOrderByErrors` — `enumSetColumnTypes` 와 같은 꼴(`CREATE TABLE zz_p (a INT)` … `finally DROP`). `ReadOnlyJdbc.wrap(c)` 로 `SqlProbe.run(List.of(new Source("Zp_SQL.xml", "<mapper namespace=\"z\">\n<select id=\"bad\">SELECT a FROM zz_p ORDER BY no_such_col</select>\n<select id=\"dup\">SELECT a, a FROM zz_p LIMIT 5</select>\n</mapper>", null, null, null)), ro, "mariadb", null)` → `z.bad` 는 `ERROR` · `z.dup` 는 `PASS`
  2. `bash scripts/mvn.sh -q -B -Djunit.timeout=10m -Dgroups=db -Dtest=MariaMetaSourceTest test`(컨테이너 하나 — 가볍다. `verify.sh:94` 의 db 레인 꼴에 `-Dtest` 만 더한 것)
  3. **`z.bad` 가 ERROR(초록)** — MariaDB 는 EXISTS 안 ORDER BY 를 푼다. `SqlProbe` 를 안 고친다. 이력에 「확인 — 문제 없음」
  4. **`z.bad` 가 PASS(빨강)** — 좁힌다. `SqlProbeTest` 에 먼저 `existsRetryOnlyForDuplicateColumns`(H2: `SELECT A, A FROM T` 는 PASS 그대로 · `SELECT A FROM T ORDER BY NOPE` 는 ERROR), 그다음 `SqlProbe`:
     ```java
     /** C-PROBE-DUPCOL — 파생 표가 못 받는 같은 이름·이름 없는 컬럼 오류인가(벤더 코드: H2 42121 · MySQL 계열 1060 · MSSQL 8155·8156). 이때만 EXISTS 꼴로 다시 묻는다(6-70) */
     private static boolean derivedColumnError(SQLException e) {
         int c = e == null ? 0 : e.getErrorCode();
         return c == 42121 || c == 1060 || c == 8155 || c == 8156;
     }
     ```
     `probe:293` 을 `if (derivedColumnError(first) && exists(conn, text, dialect))` 로(`probe` 는 지금 12 — 13 이 된다)
  5. 어느 쪽이든 `DbCorpusBase.roFileMessages` 의 손 매퍼에 셋을 더하고 반환을 6 으로: `s4` `SELECT A FROM TB_RO84 ORDER BY NO_SUCH_COL_84` → ERROR 가 아니면 `a.add("ORDER BY 없는 컬럼이 ERROR 가 아니다: " + 상태)` · `s5` `SELECT A, A FROM TB_RO84` → PASS 가 아니면 `a.add("같은 이름 컬럼이 PASS 가 아니다: " + 상태)` · `s6` `SELECT COUNT(*) FROM TB_RO84` → PASS 가 아니면 `a.add("이름 없는 컬럼이 PASS 가 아니다: " + 상태)`. (컨테이너 아홉 전부 — `V-68` 이 잰다. 등급 A)
- 의도된 골든 변화: 빠른 레인 0. `V-68` — `db-*-probe-*.txt`·`db-*.json` 의 probe 수(좁혔을 때 PASS 가 ERROR 로 **가는 것** — 문장마다 첫 오류의 벤더 코드를 이력에)
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlProbeTest,VerifyLanesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-113(같은 레인). 레인 db·corpus
- 사다리: Docker 가 안 뜨면 스텝 1·2 를 `V-68` 로 미룬다(시험은 써 두고 커밋) — `V-68` 에서 빨강이면 그때 스텝 4 를 치고 `--db` 를 다시 돈다 · 좁힌 뒤 `V-68` 에서 `s5`·`s6` 이 어느 방언에서 빨강이면 그 방언의 벤더 코드(오류문에 `tb_probe` 가 든 것만)를 `derivedColumnError` 에 더한다

### V-68a — 정적 분석 · corpus 골든 확인
1. `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify`(PMD·SpotBugs·ErrorProne) — 새 짝은 코드로 고친다. 고치기 둘
2. `bash scripts/verify.sh --corpus`(7~12분). 허용 변화 —
   - `analyze-egov-joins.json`·`deliverable-egov-relations.json`·다른 `analyze-*` 의 조인 — **느는 것**(UNION 앞 가지 · MERGE USING · `;` 로 이은 문장 · `CHARACTER SET` 뒤)과 **주는 것**(한쪽이 WITH 이름 · 서브쿼리가 바깥 별칭을 덮어 생긴 것). 줄어든 줄마다 까닭을 `R-35` 가 댄다
   - `analyze-*.json`·`-unresolved.txt` — 표 R 이 **느는 것**(조상 필드의 연관 · 방언 매퍼의 `#` 뒤), `jpql` 미해결이 **주는 것**. `table` 미해결(등급 A)이 새로 나오면 7절
   - `-jspurl.txt`·JSP 링크 — `/` 링크가 **주는 것**만
   - `cleanup-*.json` — `steps.value` 가 **주고** `RECEIVER` 건너뜀이 **느는 것**만(enum·record 수신자). 등급 A(정리 뒤 컴파일·파싱)는 0
   - `sqlfixture.json`·`sqltables-diff.txt` — 「그쪽만」 이 **주는 것**(`${schema}.표`)만
   - 그 밖(`gen-*`·`hr-insert-*`·`ddl-*`·`check-*`·`security-*`·`masking-*`·`logsql-*`·`csv-*`·`strip-*`)은 diff 0 기대 — 바뀌면 원인 행의 「의도된 골든 변화」 와 댄다
   - 허용 밖 — 고치기 둘, 넘으면 `wip/V-68a`
3. diff 줄 수·종류별 건수·벽시계를 이력에
- 의존: 코드 행 전부

### R-35 — 손 판정 · 처분 표
- `design/compare-2026-10-28.md` 끝에 `## ㊳ 번들 89 — 번들 88 리뷰 행 열셋·보정 Minor 다섯(2026-11-21)`:
  1. 처분 표 — 열여덟 × (행 · 강제 지점 시험 · 닫음/한계/동결/지금 동작 유지/wip). 한계(각 행 「한계」 줄)를 행마다
  2. `V-68a` 골든 변화 표(종류별 건수 전 → 후)
  3. 손 판정 —
     - **조인에서 빠진 줄 전부(스물 넘으면 스물)**: 한쪽 표가 그 문장의 WITH 이름인지, 아니면 안쪽 별칭이 바깥 별칭을 덮던 것인지 — 매퍼 문장을 열어 본다(SQL 본문은 안 적는다, 파일·문장 id 만). 둘 다 아니면 7절
     - **새 조인 스물**: UNION 가지·MERGE USING·`;`·`CHARACTER SET` 가운데 무엇인지
     - **`#` 로 는 표 전부**(0 건 기대 — 0 이면 「없음」 한 줄): 그 매퍼가 정말 MySQL 계열이 아닌지(파일 이름·`databaseId`), `#` 가 주석이 아니라 연산자·글자인지
     - **`cleanup` 에서 `RECEIVER` 로 돌아간 줄 전부**: 수신자 형식이 그 파일의 enum·record·annotation 인지
  4. 끝에서 끝 — `run.bat --port 18872 --no-browser`(빈 `--data-dir`) → 두 폴더에 끝 줄바꿈만 다른 파일을 두고 `curl -s "http://127.0.0.1:18872/api/diff/file?a=…&b=…"` 가 `"sameLines":true` 와 `끝 줄바꿈` · 서버를 끈다
- 코드·레인 지문이 안 바뀐다
- 의존: V-68a

### V-68 — `--db`
1. 여유 RAM — PowerShell `(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB`. 15 미만이면 7절
2. `bash scripts/docker-up.sh` → `bash scripts/verify.sh --db`(컨테이너 아홉, 17~35분). 다른 JVM 을 같이 띄우지 않는다
3. 허용 변화 —
   - `db-*-probe-*.txt`·`db-*.json` 의 probe 수 — `6-70` 을 좁혔을 때만 PASS 가 ERROR 로 **가는 것**(문장·벤더 코드를 이력에). 안 좁혔으면 0
   - `readOnlyBypasses`(등급 A) — `s4`·`s5`·`s6` 포함 0 이어야 한다. `s4` 가 빨강이면 `6-70` 스텝 4 를 치고 다시
   - `alterRoundTrip`·`alterTwoReleases`·`ddlRoundTrip`·`insertRuns`·`dollarNameInsertRuns` 등 기존 등급 A 는 0. `[손]` 이 느는 것(못 옮기는 기본값)은 수를 이력에
4. `verify.sh --db` 와 `git push` 는 따로 낸다
- 의존: R-35

### 마무리
CLAUDE.md 「마무리」 1~8 그대로 — 독립 리뷰 하나(**`model: fable`, effort `xhigh`** — 서브에이전트 호출에 둘 다 명시. Review Focus: ① `SqlJoins.Scopes` — 범위가 잘못 갈려 맞던 관계가 빠지는 꼴(괄호 친 표 · 괄호 안 UNION · LATERAL·APPLY · 중첩 WITH)과 여전히 지어지는 꼴 ② `outsideSet` 의 깊이 — SET 대입이 조인으로 새는 길 ③ `hashAsOperator` 가 MySQL 매퍼에 걸리는 길(파일 이름 낱말) · `^` 가 뒤 판정을 바꾸는 길 ④ `ProfileStore.mergeRules` 가 옛 저장 결과와 글자까지 같은가(`saveCodeCheck` 의 「다른 값도 바뀐다」 검사) ⑤ 절대 규칙 2·3 — `기동 실패` 스택·`[손]` 글·`byteDiffs` 에 비밀번호·SQL 본문·파일 내용이 실리는 자리 ⑥ `defaultChanged` 가 거짓이라 빠지는 다른 문장(MSSQL 가지) ⑦ `JpqlPaths.field` 가 다른 패키지의 같은 이름 조상을 잡는 길 ⑧ 골든 diff 가 행의 「의도된 골든 변화」 와 다른 자리) → `bash scripts/gate-probe.sh` → `verify.sh`(리뷰 고침이 건드린 무거운 레인만 다시) → push → PR(본문에 행마다 시작 세 줄 표) → CI 폴링 → claude[bot] 처분 → **머지 위임** `gh pr merge N --merge --delete-branch` → 끝 보고.

## 5. 금지 사항

- `pure/` · `ext-fixes.yaml` · `config/pmd/exclude-from-failure.properties` · `golden/check/pure-rules.json` · `rules.yaml` · 마이그레이션 V001~V017 을 고치지 않는다. 새 마이그레이션·새 규칙·새 의존성 0
- `Unresolved.KINDS` 를 늘리지 않는다
- **안 고치는 코드** — `core/text/Csv.java` · `core/fs/PathRules.java` · `LineDiff.diff`·`lines` · `core/conn`(`ReadOnlyJdbc`) · `RegexRule.stripComments` · `SqlTokens.hashComment`·`wordAt`(판정은 그대로 — 더하는 것은 `hashAsOperator` 하나) · `SqlTables.extract`·`SqlJoins.extract` 의 시그니처
- self-check 기준선에 있는 메서드의 값을 올리지 않는다 · `JpaIndex`·`AlterGen.Run` 에 메서드를 더하지 않는다 · `JpaIndex.jpql`·`AlterGen.Run.modifyColumn`·`SqlTables.extract`·`InsertGen.generate`·`MapperIndex.Handler.startElement`·`LineDiff.diff` 에 분기를 더하지 않는다
- `JpaIndex` 는 `Cls` 의 `private` 떼기와 `:520` 호출(사다리면 필드 둘)만
- 기존 경고·사유·미해결 글자를 바꾸지 않는다(골든·baseline·화면 열쇠) — 「기본값 식은 옮기지 않았다 — …」·「저장하지 않았다 — …」·「codecheck 만 바꾸려 했는데 …」·`[오류] …`·CLOB 꼬리(`…(CLOB N자 — 앞 1000000자만)`). 새 글은 새 글자로
- `RuleSet.validate`·`RuleSet.load` 를 고치지 않는다 — 꺼진 규칙도 정규식을 검사한다(사용자 결정 9)
- 기존 시험 단언을 지우거나 느슨하게 바꿔 통과시키지 않는다. 바뀌는 기대는 `sql-joins.json` 의 `mergeSet` 하나와 행에 적힌 self-check 줄뿐
- `6-70` — MariaDB 에서 초록이면 `SqlProbe` 를 안 고친다(사용자 (가)). 좁힐 때 오류 **글**로 가르지 않는다(벤더 코드만)
- 화면은 `dev_tools_ext.js` 의 `fdFile` 여섯 줄만. `tools/*.html`·`common.js`·`common.css` 를 고치지 않는다
- `MapperIndex` 픽스처 폴더(`fixtures/analyze/mapper/`)와 순수본과 맞대는 픽스처 폴더(`fixtures/check/{common,jsp,tsx,security,a11y}`)에 파일을 더하지 않는다
- 시험·경고·예외·400 글·로그에 실제 사업 코드·SQL 본문·YAML 원문 값을 쓰지 않는다(절대 규칙 2·3)
- 다른 저장소(portfolio·satchel)를 고치지 않는다 · worktree·`finishing-a-development-branch` 를 쓰지 않는다

## 6. 최종 검증

- `bash scripts/verify.sh` 초록 · `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify` 초록 · `bash scripts/verify.sh --corpus` 초록(등급 A 0) · `bash scripts/verify.sh --db` 초록(컨테이너 아홉, 등급 A 0 — `s4`·`s5`·`s6` 포함) · `bash scripts/gate-probe.sh` 열 전부 산다
- 강제 지점 시험이 전부 있다(S0 표, `wip/` 로 뺀 행 빼고) — 각 행 이력에 RED → GREEN 한 줄(`6-70` 은 MariaDB 결과 한 줄)
- 손 확인(R-35): compare ㊳ 처분 표 · 빠진 조인·새 조인 · `#` 로 는 표 · `RECEIVER` 로 돌아간 줄 · `sameLines` `curl`
- 회귀: `golden/` diff 는 행마다 적은 「의도된 골든 변화」 와 `V-68a`·`V-68` 의 허용 변화뿐 · `pure-rules.json` diff 0 · 규칙 수 147 · 마이그레이션 17 · `Unresolved.KINDS` 20 · `self-check.txt` 는 내린 값·사라진 줄 셋(`SqlJoins.aliases` · `DdlGen.defaultOf` · `ProfileStore.saveCodeCheck`)만
- 분할표 — 열여덟이 다 닫혔다: 완료 열넷(`6-68` 은 `6-66` 에서) · `0-116` 지금 동작 유지 · `4-43` 동결 · `5-114` 원 행 완료 · R-C-M1 은 `6-66` 이력에
- CI 초록 · claude[bot] 처분 · PR 머지

## 7. 중단 조건

다음이면 임의로 풀지 말고 멈추고 보고한다(어느 스텝 · 무엇이 예상과 달랐나 · 가능한 선택지).
- 행의 「사실」·2절 구조 설명이 실제 코드와 다르다(줄 번호 어긋남은 아님 — 메서드·흐름이 다를 때)
- 검증이 2회 연속 실패하고 원인이 이 플랜 범위 밖
- 5절을 어기지 않고는 진행할 수 없다(특히 — 기준선 값을 올리거나 `JpaIndex`·`AlterGen.Run` 에 메서드를 더해야만 되는 고침)
- 스텝에 없는 파일을 3개 이상 고쳐야 한다(골든·픽스처·`self-check.txt` 는 스텝에 있는 것이다)
- `6-66` 뒤 `sql-joins.json` 의 옛 열쇠 열둘(`mergeSet` 빼고) 가운데 바뀌는 것이 있다
- `1-114` 뒤 `alter-*.sql` 골든이 바뀐다 · `4-42` 뒤 `hr-insert*`·`upsertThreeShapes` 가 바뀐다
- `8-22` — `;` 폴더도 사다리의 폴더 꼴도 `IllegalStateException` 을 못 낸다
- `V-68a` — `table` 미해결(등급 A)이 새로 나온다 · 정리 뒤 컴파일(`CleanupCompileCorpusTest`) 등급 A 가 생긴다 · 등급 B baseline 이 상한(표본의 2%)을 넘는다
- `R-35` — 빠진 조인 가운데 WITH 이름도 덮인 별칭도 아닌 것이 있다 · `#` 로 는 표가 MySQL 계열 매퍼의 것이다
- `V-68` — 기존 등급 A 가 새로 빨강 · `--db` 앞 여유 RAM 15GB 미만(다른 세션 컨테이너 — 사람이 조율한다)

번들 모드의 「갈려도 안 멈춘다」 는 위 밖에서만 선다. 행당 계획 밖 결정 둘까지, 넘으면 `wip/<청크>` 로 빼고 다음 행. 빠졌을 때 뒤 행 —
- `6-65` 가 빠져도 `6-66` 은 친다(`withoutBindSchema` 는 `6-66` 것이다)
- `6-66` 이 빠지면 `6-68`·R-C-M1 도 같이 번들 90~ 으로(분할표 `6-68` 은 「대기」 그대로)
- `1-113` 이 빠져도 `6-70` 은 친다
- 그 밖 행은 서로 독립이다

## 8. 불확실 항목

사용자만 정할 수 있는 것 — 없음(범위 · Csv 동결 · `5-127` 문서만 · `0-116` 유지 · `6-70` 확인 뒤 고침 · 머지 위임 · 컷 없음, 그리고 설계 중 물은 셋(`6-69` 고침 · 모르면 주석 · `0-117` 400) 다 답 받음).

실행해야만 알 수 있는 것 — 실행자가 해당 스텝에서 확인한다.
- **첫 스텝 전에**: `git log --oneline -1`(S0 커밋) · `git status --short` 깨끗 · `docker ps`(다른 세션 컨테이너가 떠 있으면 `V-68` 앞 RAM 을 미리 본다)
- **MariaDB 가 EXISTS 안 `ORDER BY 없는컬럼` 을 오류로 내나**(컨테이너) — `6-70` 스텝 2. MySQL 5.7·8.0·MSSQL 은 `V-68` 의 `s4`
- **MSSQL 의 같은 이름·이름 없는 컬럼 벤더 코드가 8156·8155 인가**(컨테이너) — 좁혔을 때 `V-68` 의 `s5`·`s6`. 사다리
- **조인 골든이 얼마나 늘고 주나**(`--corpus`) — `V-68a` 허용 변화 · `R-35` 손 판정
- **행마다 self-check 값**(시험 실행) — `-Dtest=SelfCheckTest`. 설계 셈(`JavaRules.complexity` 규칙으로): 새 메서드는 전부 14 이하 · `saveCodeCheck` 28 → 22 · `defaultOf` 18 → 15(줄 사라짐) · `modifyColumn` 40·`jpql` 40 그대로. 다르면 공통 사다리
