# 설계 75 — 번들 90: 번들 89 리뷰 행 일곱 · 5-100 누적 규칙 (Fable 설계 · Opus 실행, 2026-11-22)

## 맥락

번들 89(#134 `d25fcbf`)의 마무리 리뷰가 Minor 여덟(M2~M9)을 새 행 일곱으로 남겼다 — `6-71`(M2·M3) · `6-72`(M4) · `6-73`(M5) · `1-115`(M6) · `6-74`(M7) · `4-45`(M8) · `0-118`(M9). 이 번들이 일곱을 한 PR 로 닫고, 순수본 재주입을 미루는 동안 어긋남을 잃지 않게 「5-100 누적 규칙」 을 CLAUDE.md 에 넣는다. 실행은 무인이고 시간 컷이 없다. 머지 뒤 다음 손은 가로 점검 3차(번들 84~90 이 바꾼 파일)라 이 일곱이 고칠 파일(SqlJoins·MapperIndex·JpqlPaths·AlterGen·JspLinks·DiffRoutes)을 먼저 닫아 둔다.

**사용자 결정(2026-11-22 — 고정, 다시 묻지 않는다. 메모리 `bundle90-decisions`)**
1. 범위 — 새 행 일곱 전부. 한 PR
2. `6-72` **(가)** — `db2` 는 `databaseId` 에서만. 다른 낱말도 파일 이름에서 `_`·`-`·`.` 로 끊긴 토막일 때만
3. `1-115` **(가)** — MariaDB 통째 재정의(`MODIFY COLUMN`) 길에서만 `[손]` 글을 바꾼다
4. `4-45` **(가)** — 빈 목록 글을 실제 까닭(섞인 줄끝 자리만 다름)으로 + HtmlUnit 단언
5. `6-71` **(가)** — 셋 다: 괄호 안 UNION 바깥 별칭 · `<when>` 분기 뒤 `(SELECT` 범위 · 파생 표 별칭 가림
6. Fable 설계 → Opus 「번들 해」 · **머지 위임** · 시간 컷 없음 · 무인
7. 반입 zip 갱신 · 순수본 재주입(`5-100`·`5-31`)은 나중. 그때까지 `5-100` 이 「순수본 밀린 목록」 — 백엔드 코드 검사만 고친 번들은 끝에 그 행에 한 줄(이 번들이 규칙을 넣는다)
8. 가로 점검 3차(84~90)는 번들 90 **머지 뒤** — 이 번들 범위 아님. 15장 「번들 91~」 행에 적기만

**보정(2026-11-22, `1ca773d` 트리 — 행마다 코드와 대조. 줄 번호는 `1ca773d` 것)**
- 일곱 다 「남음」 을 코드로 확인했다
- `6-71` — `SqlJoins.Scopes.of:176` 의 집합 연산 가지는 `open(parent.get(cur))` 하나다. 질의 아닌 괄호 안(`((SELECT …) UNION (SELECT …))`)에서는 `cur` 가 바깥 범위라 옆 가지의 부모가 바깥의 부모(문장 바닥이면 `-1`)가 된다 → 바깥 별칭을 잃는다. `:173` 은 `(` 바로 뒤 낱말만 본다 — `MapperIndex.Handler:404` 가 `<when>`·`<otherwise>` 자리에 넣는 `SqlTables.BRANCH`(`\u0001`, 낱말 하나)가 끼면 범위가 안 열린다. `fill:203` 은 `) v` 를 안 적는다 → `v` 가 바깥의 같은 별칭으로 풀린다
- `6-72` — `MapperIndex.hashText:285` 는 `databaseId + " " + 파일 이름` 을 한 글로 이어 `contains` 로 본다
- `6-73` — `JpaIndex.pick:540` 은 후보가 하나면 import·패키지를 안 본다. `JpqlPaths.parent:114` 가 그 결과를 그대로 오른다. `Cls.imports`(단순 이름 → FQCN)·`Cls.stars`(별 import 의 패키지 이름)·`Cls.pkg` 는 package-private 필드라 `JpqlPaths` 가 읽는다
- `6-74` — `JspLinks.matched:151` 의 `kind(text, start)` 는 200자 창 안 **마지막** 단서의 꼴만 돌려준다. 단서와 따옴표 사이 글은 안 본다
- `1-115` — `AlterGen.defaultChanged:75` 는 `modifyColumn:346` 이 MariaDB 길(`:362` — 형식·NULL·주석 가운데 하나라도 바뀌면 `MODIFY COLUMN`)을 정하기 **전에** 불린다. `cmtChanged:347` 은 그 뒤 줄이다
- `4-45` — 빈 `byteDiffs` 는 양쪽 다 줄끝이 섞였고(다수 쪽·끝 줄바꿈·인코딩이 같고) 섞인 자리만 다를 때뿐이다: BOM 은 `LocalFiles.read` 가 인코딩 `UTF-8-BOM` 으로 알려 「인코딩」 줄로 나오고, `LineDiff.lines` 는 끝 줄바꿈 하나만 떼어 빈 줄 수 차이는 줄 차이로 나온다. **`dev_tools.html` 은 HtmlUnit 이 못 읽는다**(`SmokeHtmlUnitTest.JS_OFF` — 인라인 스크립트의 배열 스프레드, 0-11). `dev_tools_ext.js` 는 ES5 다
- `0-118` — 시험 파일이 넷(`MapperIndexTest`·`SqlJoinsTest`·`ProfileStoreTest`·`AlterGenTest`)이라 청크 규칙(파일 1~3)에 맞게 `0-118a`·`0-118b` 로 가른다
- 표본 매퍼에 혼자 선 `#` 는 0 건(설계 74 보정) — `6-72` 로 corpus 골든은 안 움직인다
- 레인 — `core/analyze`·`resources/tools` 는 corpus, `core/gen`(AlterGen) 은 db. 둘 다 돈다. 이 번들은 코드 검사(`core/check`·`rules.yaml`)를 안 고친다 — `5-100` 에 더할 줄 없음

## 1. 목표와 범위

목표 — 번들 89 마무리 리뷰가 남긴 행 일곱을 닫고 「5-100 누적 규칙」 을 넣는다. 사용자가 겪는 변화: 프로그램 분석의 테이블 관계(산출물 「관계 후보」·04 근거·18 상관도)에서 괄호 친 UNION 옆 가지의 관계가 안 빠지고, `<when>` 분기로 시작하는 서브쿼리와 파생 표 별칭이 바깥 별칭을 덮어 지어내던 관계가 사라진다 · `OrderMapper_db2.xml`·`OracleSyncMapper.xml` 같은 이름의 MySQL 매퍼에서 `#` 주석 뒤 표가 안 지어진다 · 프로젝트 밖 조상을 import 한 엔티티의 JPQL 경로가 이름만 같은 프로젝트 클래스로 안 풀린다 · 루트 매핑 `/` 가 있는 프로젝트에서 `href` 가 있는 JSP 의 `split('/')` 가 링크가 안 된다 · MariaDB 반영 DDL 의 `[손]` 글이 「뒤 MODIFY COLUMN 이 기본값을 지운다」 를 알린다 · 폴더 비교의 줄 비교가 「섞인 줄끝의 자리」 를 알린다.

In scope

| 행 | 닫는 것 | 꼴 |
|---|---|---|
| `S0` | 설계 문서 · 15장·16장 행 · 이력 | 문서 |
| `6-71` | SqlJoins 범위 — 괄호 안 UNION · 분기 표시 · 파생 표 별칭(M2·M3) | 코드 |
| `6-72` | MapperIndex.hashText — 파일 이름 낱말(M4) | 코드 |
| `6-73` | JpqlPaths.parent — 다른 패키지 같은 이름 조상(M5) | 코드 |
| `6-74` | JspLinks 루트 매핑 — 단서와 맞닿음(M7) | 코드 |
| `1-115` | AlterGen `[손]` 글 — MariaDB MODIFY COLUMN 길(M6) | 코드 |
| `4-45` | 줄 비교 화면 줄 — 글 · HtmlUnit 단언(M8) | 화면·시험 |
| `0-118a` | 시험 빈칸 — `hashText` 음성 · `SqlJoinsTest`(LATERAL·APPLY·괄호 친 UNION)(M9) | 시험 |
| `0-118b` | 시험 빈칸 — `mergeRules` · `AlterGenTest` 기본값(MSSQL · 형식 + 기본값)(M9) | 시험 |
| `0-119` | 5-100 누적 규칙 — CLAUDE.md 「마무리」 · `5-100` 행 | 문서 |
| `V-69a`·`R-36`·`V-69` | 정적 분석·corpus · 손 판정 · `--db` | 4장 |

Out of scope
- 가로 점검 3차(84~90) · 반입 zip 갱신 · 순수본 재주입(`5-100`·`5-31`) · 잠재 A-33·A-37 · 조건부 `5-14`·`5-15`·`6-9`
- `5-100` 목록의 소급 채우기(번들 84~89 가 고친 백엔드 코드 검사 대조) — 규칙만 넣는다
- 파생 표 별칭의 **관계**(`) v ON v.ID = c.ID` 를 안쪽 표로 푸는 것) · 파생 표 뒤 쉼표 조인의 표(`FROM (SELECT …) v, T c`) · `FROM <when> 표` 꼴 · `GO` 경계 — 한계로 둔다
- `JpaIndex.pick`·`lookup`·`superEntity`(엔티티 표 짓기 쪽의 조상 찾기) — `JpqlPaths.parent` 만 좁힌다
- MariaDB `MODIFY COLUMN` 이 **안 바뀐** 식 기본값을 빼는 것(경고만 — 지금대로) · MSSQL 기본값 가지
- `byteDiffs` 응답 모양 · `LineDiff` 판정 · `dev_tools.html`
- 새 라우트·새 화면·마이그레이션·규칙·의존성 없음(규칙 수 147 · 마이그레이션 17 · `Unresolved.KINDS` 20 그대로)

## 2. 현재 구조 요약

행마다 「사실」 줄이 지금 자리와 재현 꼴이다(4절). 여러 행에 걸치는 것만 여기 둔다.

데이터 흐름 — 프로그램 분석: `MapperIndex.merge` 가 문장마다(`Raw` — 파일·`databaseId`) 조각을 끼운 글을 `hashText` 로 거른 뒤 `SqlTables.extract`·`SqlJoins.extract(text)` 에 준다. `SqlJoins.extract` 는 깊이 0 의 `;` 로 문장을 가르고 `statement` 가 `Scopes.of`(범위 표) + `outsideSet` 뒤 등식을 훑어 `Scopes.table(자리, 별칭)` 으로 푼다. `JpaIndex.jpql:520` 은 끝에서 `JpqlPaths.resolve(…, types::get, this::lookup, …)` 로 연관 경로를 풀고 `JpqlPaths.field` 가 `parent` 로 여덟 단계까지 오른다. `JspLinks.extract(jsp, programUrls)` 의 `matched` 가 따옴표 글을 매핑 URL 과 맞댄다. 반영 DDL: `AlterGen.Run.modifyColumn` 이 바깥 static `defaultChanged` 로 기본값 변화를 묻고 방언 가지로 간다. 폴더 비교: `dev_tools_ext.js` `fdRun`(`POST /api/diff/folders`) → `fdList` 의 「다름」 행 클릭 → `fdFile`(`GET /api/diff/file`) → `DiffRoutes.file` 의 `sameLines`·`byteDiffs`.

함정
- **self-check 기준선**(`golden/check/self-check.txt`) — 이 번들이 건드리는 파일의 줄: `AlterGen` `modifyColumn(7)` 40 · **`Run` 메서드 40** · `keyBreakingFks(1)` 18 · `uniquesAndIndexes(4)` 19 / `MapperIndex` `startElement(4)` 18 · `Statement(8)` / `JpaIndex` 메서드 38 · `entity(1)` 18 · `jpql(6)` 40. `SqlJoins`·`JpqlPaths`·`JspLinks`·`DiffRoutes` 는 줄이 없다. 기준선 밖 메서드 상한은 복잡도 15·길이 100·중첩 4·인자 7, 클래스는 메서드 30
- `AlterGen.Run` 에는 메서드를 못 더하고 `modifyColumn` 에는 분기(`if`·`&&`·`||`·`?:`, 람다 안 것도)를 못 더한다 — 바깥 클래스 static 도우미와 인자 없는 호출로만 넓힌다. `JpaIndex` 는 안 고친다
- `SqlJoinsTest.shapes` 의 골든 `analyze/sql-joins.json` 은 `GoldenFiles.assertJson` 이 열쇠를 정렬해 쓴다 — 새 열쇠는 글자 차례 자리에 든다. 옛 열쇠는 스물일곱
- `MapperIndexTest.golden`·`namespacesAreStatementKeyPrefixes` 는 `fixtures/analyze/mapper/` 폴더 전체를 본다 — 그 폴더에 파일을 안 더한다(인라인 `Source`)
- `SqlTables.BRANCH` 는 `char` — 낱말 목록에서는 `String.valueOf(BRANCH)` 한 글자짜리 낱말이다
- `JspLinks.QUOTED` 는 줄을 안 넘지만 한 줄 안에서는 따옴표 짝이 밀린다(`"…'/'…"` 뒤 `"` 가 다음 글의 여는 따옴표가 된다) — 시험 JSP 는 사례마다 줄을 가른다
- `SmokeHtmlUnitTest.client(true)` 는 스크립트 예외를 던진다(`setThrowExceptionOnScriptError(true)`)
- `DdlGen.Gen.defaultOf` 는 못 옮기는 식에 경고를 더하는 부수 효과가 있다 — `defaultChanged` 를 부르는 횟수·차례(이른 return 앞 한 번)를 안 바꾼다
- PMD `AvoidFieldNameMatchingMethodName` 은 대소문자를 안 가린다 — 상수 `OPEN`·`CLOSE`·`QUERY`·`BRANCH` 와 같은 이름의 메서드를 안 만든다(번들 89 V-68a 가 `of` 로 한 번 걸렸다). `AvoidReassigningLoopVariables` — `for` 변수를 몸에서 안 바꾼다(`while`)
- Bash heredoc 은 백슬래시를 벗긴다 — 정규식·역슬래시가 든 편집(`6-74` `GAP`·`4-45` `INLINE_SCRIPT`)은 Edit·Write 로
- `ToolsFolderTest` 가 `CLAUDE.md`·`dev_tools_ext.js` 를 읽는다

## 3. 설계 결정 — 전부 [고정]

1. **집합 연산 가지의 부모는 괄호 깊이로 가른다**(`6-71`) — 범위마다 열린 괄호 깊이를 적는다. 집합 연산이 제 범위가 열린 깊이에 있으면 옆 가지(부모가 같다, 지금 동작), 더 깊은 괄호 안(질의 아닌 괄호)에 있으면 제 범위의 **아이**. 버린 대안 — `((SELECT` 의 바깥 괄호도 질의 괄호로: `FROM ((SELECT …) v JOIN B b ON …) JOIN C c ON b.ID = c.ID` 의 `b` 가 바깥에서 안 보인다
2. **`(` 뒤 분기 표시는 건너뛰고 본다**(`6-71`) — `(` `BRANCH`* `SELECT|WITH` 면 질의 괄호. 분기 표시 자체는 범위를 안 가른다(`FROM A a <when> JOIN B b …</when>` 의 `b` 가 문장 범위에 남아야 한다)
3. **표 자리의 질의 괄호 뒤 별칭은 빈 글로 적는다**(`6-71`) — WITH 이름과 같은 처분: 그 별칭의 등식은 버리고 바깥의 같은 별칭을 가린다. 표 자리(`FROM`·`JOIN`·쉼표·`USING` 뒤)의 괄호만 — SELECT 목록의 스칼라 서브쿼리 별칭은 안 적는다. 파생 표 별칭을 안쪽 표로 푸는 것은 안 한다
4. **`#` 방언 짐작의 파일 이름은 낱말 일치**(`6-72`, 사용자 (가)) — 파일 이름(확장자 포함)을 `_`·`-`·`.` 로 끊은 토막이 oracle·tibero·postgres·postgresql·mssql·sqlserver·altibase·cubrid·goldilocks 가운데 하나와 **같을** 때만. `db2` 는 파일 이름에서 안 본다. `databaseId` 는 지금처럼 `contains`(db2 포함). MySQL 쪽(mysql·maria)은 지금처럼 둘 다에서 `contains` — 넓게 잡을수록 「모르면 주석」 쪽이다. `postgresql` 을 낱말 목록에 넣는 까닭 — 지금 `contains("postgres")` 로 잡히던 `_postgresql.xml` 이 낱말 일치로 빠지면 안 된다
5. **조상은 자식의 extends 글이 가리킬 때만 오른다**(`6-73`) — 스코프 붙은 이름(`extends q.Base`)은 FQCN 이 같거나 같은 패키지의 `패키지.글` 과 같을 때 · 명시 import 가 있으면 그 FQCN 과 같을 때 · 없으면 같은 패키지이거나 별 import 패키지일 때. `JpqlPaths` 안에서만 — `JpaIndex.pick` 은 안 고친다(엔티티 표 짓기·저장소 찾기가 같이 쓴다)
6. **길이 1 URL(`/`)은 단서가 따옴표와 맞닿을 때만 링크**(`6-74`) — 가장 가까운 단서의 끝과 URL 글 시작 사이가 공백·`=`·`(`·`+`·따옴표·`${…}` 뿐이고, 끝에 `<c:url value=` + 따옴표 하나를 더 허용한다. `<c:url value=` 를 허용하는 까닭 — `href="<c:url value='/'/>"` 는 JSP 의 흔한 루트 링크이고 지금 링크로 잡힌다(행 글자대로면 회귀). 길이 2 이상 URL 은 지금 그대로
7. **`[손]` 글은 MariaDB 가 컬럼을 통째로 다시 쓸 때만 다르다**(`1-115`, 사용자 (가)) — 조건은 `modifyColumn` 의 MariaDB `MODIFY COLUMN` 가지와 같은 식(`mariadb && (형식 || NULL || 주석)`)을 바깥 static `redefines` 가 센다. 옛 글은 글자 그대로. `modifyColumn` 의 `:362` 조건은 안 바꾼다(복잡도가 내려가 기준선이 움직인다)
8. **빈 목록 글은 「섞인 줄끝의 자리」**(`4-45`, 사용자 (가)) — 화면 `fdFile` 의 글만 바꾼다. 서버 `byteDiffs` 는 빈 목록 그대로(javadoc 만)
9. **`4-45` 의 HtmlUnit 단언은 실제 `dev_tools.html` 에서 인라인 스크립트만 뺀 응답으로** — `WebConnectionWrapper` 가 `/tools/dev_tools.html` 응답의 `src` 없는 `<script>` 를 지운다. 마크업·`common.js`·`dev_tools_ext.js` 는 실물. 버린 대안 — 손으로 만든 조각 DOM: `dev_tools_ext.js` `init` 이 건드리는 id 가 스물 남짓이라 화면이 바뀔 때마다 어긋난다
10. **`0-118` 은 둘로**(`0-118a` 분석 · `0-118b` 프로필·생성기) — 시험만 더하는 행이라 RED 가 없다. 기대와 다른 출력은 골든·단언에 못 박지 않는다(공통 사다리)
11. **5-100 누적 규칙은 CLAUDE.md 「마무리」 목록 아래 한 문단**(`0-119`) — 번호를 안 늘린다(「마무리 3~8」 을 가리키는 자리가 여럿). 적는 때는 push 앞 — 같은 PR 에 실린다
12. 행 차례 — 코드 여섯 → 시험 둘(고친 뒤 동작을 잰다) → 문서 → 무거운 판

## 4. 실행 스텝

행마다 끝은 CLAUDE.md 「Task 끝」 순서(분할표 행·이력 → `bash scripts/verify.sh` → `git commit` 따로 → `task-done`). `src/main/java` 를 고치는 행은 시험을 먼저 쓰고 RED 를 본 뒤 고친다. 커밋 제목 `fix: <번호> <무엇>`(문서만이면 `docs:`, 시험만이면 `test:`). 줄 번호는 `1ca773d` 기준 — 앞 행이 밀면 메서드 이름으로 찾는다.

공통 사다리
- `SelfCheckTest.mainMatchesBaseline` 빨강 —
  - **오른 값·새 줄**: 분기를 도우미로 뺀다. 자리는 ① 그 메서드가 든 중첩 클래스 ② 같은 클래스의 `private static`. `AlterGen.Run`·`JpaIndex` 에는 메서드를 안 더한다
  - **내린 값·사라진 줄**: 이 번들은 0 기대다. 생기면 고침이 넓다 — 플랜 코드와 댄다
- `golden/` diff — 행의 「의도된 골든 변화」 에 적힌 것만 갱신(`bash scripts/mvn.sh -q test -Dtest=<시험> -Dgolden.update=true`). 그 밖은 고침이 넓다 → 고친다
- **시험만 더하는 행(`0-118a`·`0-118b`)** — 출력이 행의 「기대」 와 다르면 그 사례를 빼고 새 행(대기)으로 적는다. 틀린 출력을 골든·단언에 못 박지 않고, 그 행에서 `src/main` 을 안 고친다
- 시험 글에 실제 사업 이름·SQL 을 안 쓴다(`T90_*`·`T1`~`T4`)
- PMD·SpotBugs·ErrorProne 은 빠른 `verify.sh` 가 안 본다 — `V-69a` 가 본다. 새 짝은 코드로 고친다(기준선에 안 더한다)

### S0 — 설계 문서 · 행 · 번들 표 (실행 세션이 했다)
1. 가지 `work/2026-11-22` 그대로(HEAD `1ca773d` = main `d25fcbf` + 번들 89 닫기 — 같은 PR 에 실린다). 새 가지 안 딴다
2. 이 플랜 전체를 `design/75-bundle90.md` 로(글자 그대로 — 이 S0 머리만 「실행 세션이 했다」 로, 끝의 「Opus 번들 해 시작 지시문」 절은 뺀다)
3. `PROGRESS.md` 「번들 89 닫기」 줄의 `(이 커밋)` → `1ca773d`
4. `PLAN.md` 15장 — 「번들 90~」 행을 둘로:
   - **「번들 90」** — 순서 칸은 아래 「차례」 · 설계 칸 「2026-11-22(Fable, 설계 75 — `design/75-bundle90.md`. 실행 Opus high · 마무리 독립 리뷰 Fable xhigh). **번들 89 리뷰 행 일곱 · 5-100 누적 규칙.** 사용자 결정(2026-11-22): 일곱 전부 · `6-72`·`1-115`·`4-45`·`6-71` 다 (가) · **머지 위임** · 시간 컷 없음」 · 설치 칸 「표본 폴더 · node · Docker · 여유 RAM 15GB」 · 상태 「대기」
   - **「번들 91~」** — 순서 칸 「(설계 전)」 · 요지 「**가로 점검 3차**(번들 84~90 이 바꾼 파일 — 번들 90 머지 뒤 점검 세션, 결과가 번들 91. 사용자 2026-11-22) · 잠재 A-33·A-37(닿는 진입로가 생길 때) · `5-100`·`5-31`(순수본 재주입 — 코드 검사 손질이 잦아든 뒤 한 번에) · 반입 zip 갱신(다음 반입 날 앞) · 조건부 `5-14`·`5-15`·`6-9` · 번들 90 이 `wip/` 로 뺀 행」 · 상태 「설계 전」
5. `PLAN.md` 16장 —
   - 일곱 행(`6-71`·`6-72`·`6-73`·`1-115`·`6-74`·`4-45`·`0-118`)의 사실 칸 끝에 ` · **처방**: 설계 75 §4`(`0-118` 은 「 — `0-118a`·`0-118b` 로 가름」 도)
   - 표 끝에 새 행 여섯(꼴 `| <번호> | <청크> | **설계 75 §4 가 원본**. <요지>. **축**: … **강제 지점**: … | <선행> | 대기 |`):
     - `0-118a` 시험 빈칸 — `hashText` 음성 · `SqlJoinsTest` LATERAL·APPLY·괄호 친 UNION(M9). 축 시험(corpus 레인). 강제 지점 `MapperIndexTest.hashHintNegatives` · `SqlJoinsTest.shapes`(셋). 선행 `6-71`·`6-72`
     - `0-118b` 시험 빈칸 — `mergeRules` · `AlterGenTest` 기본값(M9). 축 시험(db 레인). 강제 지점 `ProfileStoreTest.mergeRulesShapes` · `AlterGenTest.unportableDefaultOtherPaths`. 선행 `1-115`
     - `0-119` 5-100 누적 규칙 — CLAUDE.md 「마무리」 한 문단 · `5-100` 행(사용자 2026-11-22). 축 문서. 강제 지점 「없음 — 절차 글(사용자 결정 7)」. 선행 —
     - `V-69a` 정적 분석 · corpus 골든 — `mvn.sh -DskipTests verify` → `--corpus` · 허용 변화. 축 4장. 강제 지점 `verify.sh --corpus`. 선행 코드 행 전부
     - `R-36` 손 판정 · 처분 표 — compare ㊴. 축 4장. 강제 지점 compare ㊴ 절. 선행 `V-69a`
     - `V-69` `--db` — 컨테이너 아홉 · 허용 변화. 축 4장. 강제 지점 `verify.sh --db`. 선행 `R-36`
6. `PROGRESS.md` 이력 한 줄(S0 설계 75 — 행 열셋: 코드·시험·문서 열 + `V-69a`·`R-36`·`V-69`, `0-118` 을 둘로 가른 까닭) + 「현재 상태」(진행중 = 번들 90 대기)
7. `bash scripts/verify.sh`(docs 레인) → `git commit`(따로) `docs: 설계 75 — 번들 90 행·번들 표`
- 확인: `git log --oneline -1` 이 그 커밋 · `git status --short` 깨끗 · 8절 「첫 스텝 전에」

| # | 청크 | 축 | 강제 지점 | 선행 |
|---|---|---|---|---|
| 6-71 | SqlJoins 범위 — 괄호 안 UNION · 분기 표시 · 파생 표 별칭 | 분석(corpus) | `SqlJoinsTest.shapes`(다섯) | S0 |
| 6-72 | MapperIndex.hashText — 파일 이름 낱말 | 〃 | `MapperIndexTest.hashHintNeedsAWholeWord` | S0 |
| 6-73 | JpqlPaths.parent — 조상은 extends 글이 가리킬 때만 | 〃 | `JpaIndexTest.ancestorMustBeNamedByTheChild` | S0 |
| 6-74 | JspLinks 루트 매핑 — 단서와 맞닿음 | 〃 | `JspLinksTest.rootMappingNeedsACue`(새 줄) | S0 |
| 1-115 | AlterGen `[손]` 글 — MariaDB MODIFY COLUMN 길 | 생성기(db) | `AlterGenTest.unportableDefaultOnMariaRedefine` | S0 |
| 4-45 | 줄 비교 화면 줄 — 글 · HtmlUnit 단언 | 화면(corpus) | `SmokeHtmlUnitTest.devToolsLineDiffSaysWhatDiffers` | S0 |
| 0-118a | 시험 빈칸 — 분석 | 시험(corpus) | `MapperIndexTest.hashHintNegatives` · `SqlJoinsTest.shapes`(셋) | 6-71 · 6-72 |
| 0-118b | 시험 빈칸 — 프로필·생성기 | 시험(db) | `ProfileStoreTest.mergeRulesShapes` · `AlterGenTest.unportableDefaultOtherPaths` | 1-115 |
| 0-119 | 5-100 누적 규칙 | 문서 | 없음 — 절차 글 | S0 |
| V-69a | 정적 분석 · corpus 골든 | 4장 | `verify.sh --corpus` | 코드 행 전부 |
| R-36 | 손 판정 · 처분 표 | 4장 | compare ㊴ 절 | V-69a |
| V-69 | `--db` | 4장 | `verify.sh --db` | R-36 |

차례 — `S0→6-71→6-72→6-73→6-74→1-115→4-45→0-118a→0-118b→0-119→V-69a→R-36→V-69→마무리`

### 6-71 — SqlJoins 범위: 괄호 안 UNION · 분기 표시 · 파생 표 별칭
- 대상: `core/analyze/SqlJoins.java`(`Scopes` · 상수 하나 · 도우미 하나 · javadoc) · `SqlJoinsTest` · 골든 `analyze/sql-joins.json`
- 사실: 보정 `6-71` 줄. 재현 — 아래 시험 표의 「전」
- 변경:
  1. 상수 — `WHEN` 아래에
     ```java
     /** MyBatis 분기 표시 낱말(&lt;when&gt;·&lt;otherwise&gt; — {@link SqlTables#BRANCH}) */
     private static final String BRANCH_MARK = String.valueOf(SqlTables.BRANCH);
     ```
  2. 바깥 클래스 도우미 — `plus` 아래에
     ```java
     /** i 부터 분기 표시를 건너뛴 첫 낱말이 SELECT·WITH 인가 — ( 뒤가 분기 표시여도 질의 괄호다(6-71) */
     private static boolean startsQuery(List<String> t, int i) {
         int k = i;
         while (k < t.size() && BRANCH_MARK.equals(t.get(k))) {
             k++;
         }
         return k < t.size() && QUERY.contains(t.get(k));
     }
     ```
  3. `Scopes` — 필드 하나를 더하고 `of`·`open`·`fill` 을 아래로 바꾸고 `sideOrChild`·`derived`·`closing` 을 더한다(`table`·`entry` 는 그대로)
     ```java
     /** 범위가 열린 괄호 깊이 — 집합 연산이 옆 가지인지 질의 아닌 괄호 안인지 가른다(6-71) */
     private final List<Integer> depth = new ArrayList<>();

     static Scopes of(List<String> t, Set<String> cte) {
         Scopes s = new Scopes(t.size());
         Deque<Integer> opened = new ArrayDeque<>(); // ( 마다 그때의 범위
         int cur = s.open(-1, 0);
         for (int i = 0; i < t.size(); i++) {
             String x = t.get(i);
             if (OPEN.equals(x)) {
                 opened.push(cur);
                 cur = startsQuery(t, i + 1) ? s.open(cur, opened.size()) : cur;
             } else if (CLOSE.equals(x) && !opened.isEmpty()) {
                 cur = opened.pop();
             } else if (SET_OPS.contains(x)) {
                 cur = s.sideOrChild(cur, opened.size());
             }
             s.at[i] = cur;
         }
         s.fill(t, cte);
         return s;
     }

     /**
      * 집합 연산 뒤 가지 — 제 범위가 열린 깊이면 옆 가지(부모가 같다). 더 깊은 괄호 안(질의 아닌 괄호 —
      * ((SELECT …) UNION (SELECT …)))이면 제 범위의 아이라 바깥 별칭을 본다(6-71)
      */
     private int sideOrChild(int cur, int now) {
         return now > depth.get(cur) ? open(cur, now) : open(parent.get(cur), depth.get(cur));
     }

     private int open(int p, int d) {
         alias.add(new HashMap<>());
         parent.add(p);
         depth.add(d);
         return alias.size() - 1;
     }

     /** 여는 말 뒤 표 낱말과 별칭을 그 낱말의 범위에 적는다. 쉼표 조인은 다음도 표, 여는 말 뒤 ( 는 괄호 친 표(USING ( 는 열 목록) */
     private void fill(List<String> t, Set<String> cte) {
         boolean expect = false;
         int i = 0;
         while (i < t.size()) {
             String x = t.get(i);
             boolean table = expect && word(x) && !SqlTables.STOP.contains(x);
             derived(t, i, expect);
             expect = OPENERS.contains(x) || expect && "(".equals(x) && !"USING".equals(t.get(i - 1));
             if (table) {
                 i = entry(t, i, cte);
                 expect = i + 1 < t.size() && ",".equals(t.get(i + 1));
                 i += expect ? 1 : 0;
             }
             i++;
         }
     }

     /** 표 자리의 질의 괄호(파생 표) 뒤 별칭을 빈 글로 — 표가 아니라 그 별칭의 등식은 버리고 바깥의 같은 별칭을 가린다(6-71) */
     private void derived(List<String> t, int i, boolean expect) {
         if (!expect || !OPEN.equals(t.get(i)) || !startsQuery(t, i + 1)) {
             return;
         }
         int k = closing(t, i);
         int j = k + 1 < t.size() && "AS".equals(t.get(k + 1)) ? k + 2 : k + 1;
         if (j < t.size() && word(t.get(j)) && !SqlTables.STOP.contains(t.get(j))) {
             alias.get(at[j]).put(t.get(j), "");
         }
     }

     /** from 의 ( 를 닫는 ) 자리 — 없으면 끝 */
     private static int closing(List<String> t, int from) {
         int d = 0;
         int k = from;
         while (k < t.size()) {
             d += OPEN.equals(t.get(k)) ? 1 : CLOSE.equals(t.get(k)) ? -1 : 0;
             if (d == 0) {
                 return k;
             }
             k++;
         }
         return t.size();
     }
     ```
     `fill` 은 `derived(t, i, expect);` 한 줄만 더한 것이다(`expect` 를 고치기 **앞** 줄)
  4. javadoc — 클래스 머리 「한계」 줄을 「한계 — 파생 표 별칭({@code ) v})의 관계는 안 잡는다(바깥의 같은 별칭은 가린다) · 파생 표 뒤 쉼표 조인의 표는 안 읽는다 · 괄호 안 DML 의 별칭은 바깥 범위에 든다 · GO 는 문장 경계로 안 본다」 로. `Scopes` javadoc 끝에 「질의 아닌 괄호 안 집합 연산의 가지는 그 괄호를 연 범위의 아이 · ( 뒤 분기 표시는 건너뛰고 본다(6-71)」
- 시험(먼저): `SqlJoinsTest.shapes` 맵에 더한다(주석 `// 6-71 — …`). `B` 는 `String.valueOf(SqlTables.BRANCH)` —
  | 열쇠 | SQL | 기대 | 전 |
  |---|---|---|---|
  | `parenUnionOuterAlias` | `SELECT * FROM T1 a WHERE a.ID IN ((SELECT b.ID FROM T2 b) UNION (SELECT c.ID FROM T3 c WHERE c.X = a.X))` | T1.X—T3.X | 없음 |
  | `parenUnionThree` | `SELECT * FROM T1 a WHERE a.ID IN ((SELECT b.ID FROM T2 b WHERE b.X = a.X) UNION (SELECT c.ID FROM T3 c WHERE c.X = a.X) UNION (SELECT d.ID FROM T4 d WHERE d.X = a.X))` | T1.X—T2.X · T1.X—T3.X · T1.X—T4.X | T1.X—T2.X 하나 |
  | `parenUnionNoLeak` | `SELECT * FROM T1 a WHERE a.ID IN ((SELECT b.ID FROM T2 b) UNION (SELECT c.ID FROM T3 c WHERE c.X = b.X))` | 없음(옆 가지 별칭은 못 본다) | 없음(지킴이) |
  | `branchQueryParen` | `"SELECT * FROM T1 a JOIN T2 b ON a.ID = b.ID WHERE a.X IN (" + B + " SELECT a.X FROM T3 a " + B + " SELECT a.X FROM T4 a)"` | T1.ID—T2.ID | T2.ID—T4.ID |
  | `derivedAliasShadows` | `SELECT * FROM T1 v JOIN T2 b ON v.ID = b.ID WHERE b.X IN (SELECT c.X FROM (SELECT ID, X FROM T3) AS v JOIN T4 c ON v.ID = c.ID)` | T1.ID—T2.ID | T1.ID—T2.ID · T1.ID—T4.ID |
  RED 는 골든 갱신 앞에 본다 — 새 열쇠의 지금 출력이 「전」 과 같고 기대와 다르다(`parenUnionNoLeak` 빼고). 고친 뒤 `-Dgolden.update=true` 로 쓰고 diff 를 표와 댄다
- 의도된 골든 변화: `sql-joins.json` — 새 열쇠 다섯만. 옛 열쇠 스물일곱은 그대로여야 한다. `self-check.txt` 0. `MapperIndexTest.golden`·`AnalyzeRunner` 골든 0 기대 — 바뀌면 그 문장이 세 꼴 가운데 무엇인지 이력에
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlJoinsTest,MapperIndexTest,SqlTablesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus
- 한계(이력·javadoc 에): 파생 표 별칭의 관계 · 파생 표 뒤 쉼표 조인의 표 · `FROM <when> 표` 꼴(여는 말 뒤 분기 표시)

### 6-72 — MapperIndex.hashText: 파일 이름은 낱말 일치
- 대상: `core/analyze/MapperIndex.java`(`hashText` · 상수 둘) · `MapperIndexTest`
- 사실: `hashText:285-290` 이 `databaseId` 와 파일 이름을 한 글로 이어 `NOT_MYSQL` 낱말을 `contains` 로 찾는다 — `OrderMapper_db2.xml`(두 번째 데이터소스) · `OracleSyncMapper.xml` 의 MySQL 매퍼가 「MySQL 아님」 이 돼 `#` 주석 뒤 글이 살아난다
- 변경:
  ```java
  /** MySQL 계열이 아님을 드러내는 databaseId 낱말 — 이 문장의 # 는 줄 주석이 아니다(6-69) */
  private static final List<String> NOT_MYSQL = List.of("oracle", "tibero", "postgres", "mssql", "sqlserver", "altibase", "cubrid", "db2", "goldilocks");
  /** 파일 이름에서는 _ - . 로 끊긴 토막이 이것과 같을 때만(6-72). db2 는 두 번째 데이터소스 이름으로 흔해 안 본다 */
  private static final Set<String> NOT_MYSQL_FILE = Set.of("oracle", "tibero", "postgres", "postgresql", "mssql", "sqlserver", "altibase", "cubrid", "goldilocks");
  private static final java.util.regex.Pattern NAME_SEP = java.util.regex.Pattern.compile("[_.-]");

  /** 방언이 드러난 문장이면 # 를 연산자로 바꾼 글, 모르거나 MySQL 계열이면 그대로(지금처럼 # 줄 주석, 6-69). 파일 이름은 낱말 일치(6-72) */
  static String hashText(Raw r, String text) {
      String id = r.databaseId == null ? "" : r.databaseId.toLowerCase(java.util.Locale.ROOT);
      String f = r.file == null ? "" : r.file.replace('\\', '/');
      String base = f.substring(f.lastIndexOf('/') + 1).toLowerCase(java.util.Locale.ROOT);
      boolean mysql = id.contains("mysql") || id.contains("maria") || base.contains("mysql") || base.contains("maria");
      boolean other = NOT_MYSQL.stream().anyMatch(id::contains) || java.util.Arrays.stream(NAME_SEP.split(base)).anyMatch(NOT_MYSQL_FILE::contains);
      return !mysql && other ? kr.ejg.toolbox.core.text.SqlTokens.hashAsOperator(text) : text;
  }
  ```
  `NOT_MYSQL` 의 javadoc 만 위 글로 고치고, 새 상수 둘은 그 바로 아래(private static — DeclarationOrder 그대로)
- 시험(먼저): `MapperIndexTest.hashHintNeedsAWholeWord` — `q = "SELECT a # b FROM T90_A"`, 매퍼마다 namespace 를 달리(`<mapper namespace="…"><select id="x">q</select></mapper>`), `letters(idx.get("<ns>.x"))` —
  | 파일 | databaseId | 기대 | 전 |
  |---|---|---|---|
  | `OrderMapper_db2.xml` | — | `{}` | `{T90_A=R}` |
  | `OracleSyncMapper.xml` | — | `{}` | `{T90_A=R}` |
  | `T90_SQL_postgresql.xml` | — | `{T90_A=R}` | 같음 |
  | `T90-oracle.xml` | — | `{T90_A=R}` | 같음 |
  | `T90.tibero.xml` | — | `{T90_A=R}` | 같음 |
  | `T90.xml` | `db2` | `{T90_A=R}` | 같음 |
- 의도된 골든 변화: 없음(`mapper-index.json` 0 · `self-check.txt` 0)
- 검증: `bash scripts/mvn.sh -q test -Dtest='MapperIndexTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus
- 한계(이력에): 낱말 경계가 대소문자뿐인 이름(`UserMapperOracle.xml`)과 `_db2` 파일은 모름 → 주석(지금 「모르면 주석」 과 같은 쪽)

### 6-73 — JpqlPaths.parent: 조상은 자식의 extends 글이 가리킬 때만
- 대상: `core/analyze/JpqlPaths.java`(`parent` · 새 `named`) · `JpaIndexTest`
- 사실: `parent:110-116` 이 `lookup(단순 이름, c.from())` 후보가 하나면 오른다. `JpaIndex.pick:540` 은 후보 하나면 import·패키지를 안 본다 — `import com.vendor.BaseEntity`(프로젝트 밖)인 엔티티가 프로젝트의 `q.BaseEntity` 필드로 경로를 푼다
- 변경:
  ```java
  private static JpaIndex.Cls parent(Walk w, JpaIndex.Cls c) {
      if (c.decl.getExtendedTypes().isEmpty()) {
          return null;
      }
      ClassOrInterfaceType ext = c.decl.getExtendedTypes(0);
      List<JpaIndex.Cls> up = w.lookup().apply(ext.getName().getIdentifier(), c.from());
      return up.size() == 1 && named(c, ext, up.get(0)) ? up.get(0) : null;
  }

  /** c 의 extends 글이 u 를 가리키나 — 스코프 붙은 이름 · 명시 import · 같은 패키지 · 별 import(6-73). 이름만 같은 다른 패키지 클래스로 안 오른다 */
  private static boolean named(JpaIndex.Cls c, ClassOrInterfaceType ext, JpaIndex.Cls u) {
      if (ext.getScope().isPresent()) {
          String q = ext.getNameWithScope();
          return q.equals(u.fqcn) || (c.pkg + "." + q).equals(u.fqcn);
      }
      String imp = c.imports.get(ext.getName().getIdentifier());
      if (imp != null) {
          return imp.equals(u.fqcn);
      }
      return u.pkg.equals(c.pkg) || c.stars.contains(u.pkg);
  }
  ```
  `JpaIndex.java` 는 안 고친다
- 시험(먼저): `JpaIndexTest.ancestorMustBeNamedByTheChild` — 머리 `h(pkg) = "package " + pkg + ";\nimport javax.persistence.*;\n"`, 소스:
  - `q/BaseEntity.java` — `h("q") + "import p.Lang;\n@MappedSuperclass\npublic abstract class BaseEntity {\n    @ManyToOne Lang language;\n}\n"`
  - `p/Lang.java` — `@Entity @Table(name = "T90_LANG")`, `@Id Long id`
  - `p/Vendor.java` — `h("p") + "import com.vendor.BaseEntity;\n@Entity\n@Table(name = \"T90_VENDOR\")\npublic class Vendor extends BaseEntity {\n    @Id Long id;\n}\n"`
  - `p/Star.java` — `import q.*;` · `T90_STAR` · `extends BaseEntity`
  - `p/Named.java` — `import q.BaseEntity;` · `T90_NAMED` · `extends BaseEntity`
  - `p/Full.java` — import 없음 · `T90_FULL` · `extends q.BaseEntity`

  `from = new JpaIndex.From("p", Map.of(), "")`, 엔티티마다 `x.jpql("select e from <이름> e join e.language l", from, "f", 1, "w", out)` —
  | 엔티티 | 기대 표 | 기대 미해결 | 전 |
  |---|---|---|---|
  | Vendor | `[T90_VENDOR]` | `jpql w:e.language` | `T90_VENDOR`·`T90_LANG`, 미해결 없음 |
  | Star · Named · Full | `<제 표>`·`T90_LANG` | 없음 | 같음 |
- 의도된 골든 변화: 없음(`jpa-index.json` 0 · `self-check.txt` 0). `inheritedAssociationPath`(같은 패키지) 초록 그대로
- 검증: `bash scripts/mvn.sh -q test -Dtest='JpaIndexTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus
- 한계(이력에): 엔티티 표 짓기 쪽(`JpaIndex.superEntity`·`pick`)은 그대로 — 이름만 같은 조상을 여전히 잡는다

### 6-74 — JspLinks 루트 매핑: 단서가 따옴표와 맞닿을 때만
- 대상: `core/analyze/JspLinks.java`(`matched` 한 줄 · 새 `cueTouches` · 상수 `GAP`) · `JspLinksTest`
- 사실: `matched:151-152` — 길이 1 URL 은 `kind != OTHER` 면 링크다. `kind` 는 200자 창 안 마지막 단서만 봐서 `<a href="x.jsp">` 뒤 스크립트의 `split('/')` 가 `link` 로 적힌다
- 변경(정규식은 Edit 로):
  ```java
  /** 단서 끝과 URL 글 사이에 올 수 있는 것 — 공백·= ( + 따옴표·${…}, 끝에 <c:url value= 와 따옴표 하나(6-74). REDOS — 바깥 *+ · ?+ */
  private static final Pattern GAP = Pattern.compile("(?:[\\s=(+'\"]|\\$\\{[^}]*})*+(?:<c:url\\s+value\\s*=\\s*[\"'])?+");

  /** start 앞 창에서 가장 가까운 단서가 start 와 맞닿았나 — 사이가 {@link #GAP} 뿐(6-74) */
  static boolean cueTouches(String text, int start) {
      int from = Math.max(0, start - CUE_WINDOW);
      Matcher m = CUE.matcher(text.substring(from, start));
      int end = -1;
      while (m.find()) {
          end = m.end();
      }
      return end >= 0 && GAP.matcher(text.substring(from + end, start)).matches();
  }
  ```
  `GAP` 은 `CTX_HEAD` 아래(private static) · `cueTouches` 는 `kind` 아래. `matched` 의 조건 한 줄 —
  ```java
  if (c.length() > 1 || k != LinkKind.OTHER && cueTouches(text, m.start(2))) { // 6-67 · 6-74 — 「/」 한 글자는 맞닿은 단서가 있을 때만
  ```
- 시험(먼저): `JspLinksTest.rootMappingNeedsACue` 끝에 더한다 —
  ```java
  // 6-74 — 단서가 창 안에 있어도 따옴표와 맞닿지 않으면 아니다
  Source far = new Source("c.jsp", "<a href=\"x.jsp\">t</a><script>var p = path.split('/');</script>", null, null, null);
  assertEquals(List.of(), JspLinks.extract(far, Set.of("/")).links());
  Source cues = new Source("d.jsp", String.join("\n", "<a href=\"<c:url value='/'/>\">h</a>", "<form action=\"/\">",
          "<script>location.href = '/';", "$.ajax({ url: '/' });", "window.open('/');</script>"), null, null, null);
  assertEquals(List.of("ajax", "form", "link", "popup", "script"), JspLinks.extract(cues, Set.of("/")).links().stream().map(JspLinks.Link::kind).toList());
  ```
  RED — `far` 가 지금 `[Link("/", "link")]`. `cues` 는 전·후 같다(지킴이 — `<c:url value=` 허용)
- 의도된 골든 변화: 없음(픽스처 골든 0 · `self-check.txt` 0). corpus 의 `/` 링크는 `V-69a`
- 검증: `bash scripts/mvn.sh -q test -Dtest='JspLinksTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus
- 한계(이력에): 식별자를 이은 꼴(`location.href = base + '/'`)은 링크로 안 적는다

### 1-115 — AlterGen `[손]` 글: MariaDB MODIFY COLUMN 길
- 대상: `core/gen/AlterGen.java`(`defaultChanged` · 새 static `redefines` · 상수 둘 · `modifyColumn` 두 줄) · `AlterGenTest`
- 사실: `defaultChanged:80` 의 글 「(앞 기본값은 그대로)」 — MariaDB 에서 형식·NULL·주석이 같이 바뀌면 `:363` 의 `MODIFY COLUMN` 이 정의를 통째로 다시 써(`g.column` 은 못 옮긴 기본값을 안 싣는다) 앞 기본값이 지워진다
- 변경:
  ```java
  /** [손] 못 옮긴 기본값 — 꼬리 둘(1-115). 앞 것은 옛 글 그대로 */
  private static final String LOST_KEPT = "손으로 쓴다(앞 기본값은 그대로)";
  private static final String LOST_REDEFINED = "뒤 MODIFY COLUMN 이 기본값을 지운다 — 손으로 다시 넣는다";

  /**
   * 기본값이 바뀌었나. 바뀌었는데 새 값이 대상으로 못 옮기는 식이면 [손] 을 남기고 거짓 — DEFAULT NULL·DROP DEFAULT 로 앞 기본값을 지우지 않는다(R-A-M2).
   * redefined — 뒤 문장이 컬럼 정의를 통째로 다시 쓴다(MariaDB MODIFY COLUMN): 앞 기본값이 지워진다고 알린다(1-115)
   */
  private static boolean defaultChanged(DdlGen.Gen g, Table tb, Column old, Column neu, boolean redefined, java.util.function.Consumer<String> hand) {
      if (Objects.equals(Run.norm(old.defaultValue()), Run.norm(neu.defaultValue()))) {
          return false;
      }
      if (DdlGen.lostDefault(neu, g.defaultOf(tb, neu, g.type(tb, neu)))) {
          hand.accept(COLUMN + neu.name() + " 기본값 — 식을 대상 문법으로 못 옮겼다, " + (redefined ? LOST_REDEFINED : LOST_KEPT));
          return false;
      }
      return true;
  }

  /** MariaDB 가 컬럼 정의를 통째로 다시 쓰는가 — modifyColumn 의 MODIFY COLUMN 가지와 같은 조건(1-115) */
  private static boolean redefines(boolean mariadb, boolean type, boolean nullable, boolean comment) {
      return mariadb && (type || nullable || comment);
  }
  ```
  상수 둘은 `COLUMN` 아래. `modifyColumn:346-347` 은 두 줄의 차례를 바꾸고 인자 하나를 더한다 —
  ```java
  boolean cmtChanged = o.includeComments() && !Objects.equals(norm(old.comment()), norm(neu.comment()));
  boolean defChanged = defaultChanged(g, tb, old, neu, redefines(mariadb(), typeChanged, nullChanged, cmtChanged), why -> hand(out, why));
  ```
  `modifyColumn` 의 그 밖 줄(특히 `:348`·`:362` 조건)은 안 건드린다
- 시험(먼저): `AlterGenTest.unportableDefaultOnMariaRedefine` — `unportableDefaultIsHandNotLive` 꼴로 `products.price` 를 `with(x, x.length(), !x.nullable(), "uuid_generate_v4()", x.comment())`(기본값 + NULL 여부를 같이 바꾼다) —
  | 대상 | 기대 |
  |---|---|
  | `mariadb` | `MODIFY COLUMN` 이 있다 · `-- [손] 컬럼 price 기본값 — 식을 대상 문법으로 못 옮겼다, 뒤 MODIFY COLUMN 이 기본값을 지운다 — 손으로 다시 넣는다` 가 있다 · `(앞 기본값은 그대로)` 가 없다 |
  | `oracle` · `postgresql` | `-- [손] 컬럼 price 기본값 — 식을 대상 문법으로 못 옮겼다, 손으로 쓴다(앞 기본값은 그대로)` 가 있다 |
  같은 시험 안에서 기본값만 바꾼 꼴(`with(x, x.length(), x.nullable(), "uuid_generate_v4()", x.comment())`)의 `mariadb` 도 `(앞 기본값은 그대로)` 인지 단언한다. RED — `mariadb` 줄이 지금 `(앞 기본값은 그대로)`
- 의도된 골든 변화: 없음(`alter-*.sql` 0 · `self-check.txt` 0 — `modifyColumn(7)` 40 · `Run` 메서드 40 그대로)
- 검증: `bash scripts/mvn.sh -q test -Dtest='AlterGenTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 db(`V-69` 가 컨테이너로 잰다)

### 4-45 — 줄 비교 화면 줄: 글 · HtmlUnit 단언
- 대상: `resources/tools/dev_tools_ext.js`(`fdFile` 한 줄) · `web/DiffRoutes.java`(javadoc 한 줄) · `SmokeHtmlUnitTest`
- 사실: `fdFile:79` 의 빈 목록 글 `'바이트(BOM 등)'` 이 실제와 다르다(보정 `4-45`). 그 줄에 화면 단언이 없다
- 변경:
  1. `dev_tools_ext.js:79` — `'바이트(BOM 등)'` → `'섞인 줄끝의 자리'`
  2. `DiffRoutes.byteDiffs` javadoc — 「셋 다 같으면 빈 목록(BOM 등 바이트만)」 → 「셋 다 같으면 빈 목록 — 양쪽 다 줄끝이 섞였고 섞인 자리만 다르다(4-45)」. 코드는 안 고친다
- 시험(먼저 — 정규식은 Edit 로): `SmokeHtmlUnitTest` 에
  ```java
  /** dev_tools.html 의 src 없는 script — Rhino 가 못 읽는 인라인 본문(JS_OFF) */
  private static final java.util.regex.Pattern INLINE_SCRIPT = java.util.regex.Pattern.compile("(?s)<script(?![^>]*\\bsrc\\s*=)[^>]*>.*?</script>");

  /**
   * 4-45 — 폴더 비교의 줄 비교: 줄 내용이 같은 「다름」 은 다른 것을 한 줄로(끝 줄바꿈 · 섞인 줄끝의 자리).
   * dev_tools 의 인라인 스크립트는 응답에서 빼고 실물 마크업 + common.js + dev_tools_ext.js 만 돌린다
   */
  @Test
  void devToolsLineDiffSaysWhatDiffers(@TempDir Path tmp) throws Exception {
      Path a = Files.createDirectories(tmp.resolve("a"));
      Path b = Files.createDirectories(tmp.resolve("b"));
      Files.writeString(a.resolve("eol.txt"), "x\ny\n");
      Files.writeString(b.resolve("eol.txt"), "x\ny");
      Files.writeString(a.resolve("mixed.txt"), "x\r\ny\nz\r\nw\r\n");
      Files.writeString(b.resolve("mixed.txt"), "x\ny\r\nz\r\nw\r\n");
      try (WebClient wc = client(true)) {
          new org.htmlunit.util.WebConnectionWrapper(wc) {
              @Override
              public org.htmlunit.WebResponse getResponse(org.htmlunit.WebRequest req) throws java.io.IOException {
                  org.htmlunit.WebResponse r = super.getResponse(req);
                  if (!req.getUrl().getPath().endsWith("/dev_tools.html")) {
                      return r;
                  }
                  byte[] html = INLINE_SCRIPT.matcher(r.getContentAsString()).replaceAll("").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                  return new org.htmlunit.WebResponse(new org.htmlunit.WebResponseData(html, 200, "OK",
                          List.of(new org.htmlunit.util.NameValuePair("Content-Type", "text/html; charset=utf-8"))), req, r.getLoadTime());
              }
          };
          HtmlPage page = wc.getPage("http://127.0.0.1:" + app.port() + "/tools/dev_tools.html");
          wc.waitForBackgroundJavaScript(3000);
          ((org.htmlunit.html.HtmlInput) page.getElementById("fd_a")).setValue(a.toString());
          ((org.htmlunit.html.HtmlInput) page.getElementById("fd_b")).setValue(b.toString());
          page.getElementById("fd_run").click();
          wc.waitForBackgroundJavaScript(5000);
          Map<String, String> said = new java.util.TreeMap<>();
          for (String rel : List.of("eol.txt", "mixed.txt")) {
              DomElement row = page.getElementById("fd_list").getElementsByTagName("tr").stream()
                      .filter(tr -> tr.getTextContent().contains(rel)).findFirst().orElseThrow();
              row.click();
              wc.waitForBackgroundJavaScript(5000);
              said.put(rel, page.getElementById("fd_file").getFirstElementChild().getTextContent());
          }
          assertEquals(Map.of("eol.txt", "줄 내용은 같다 — 다른 것: 끝 줄바꿈 A 만", "mixed.txt", "줄 내용은 같다 — 다른 것: 섞인 줄끝의 자리"), said);
      }
  }
  ```
  RED — `mixed.txt` 가 지금 「… 바이트(BOM 등)」. 파일 머리의 import 는 있는 것을 쓰고 모자라면 더한다
- 사다리(시험 하네스 — `src/main` 은 위 둘뿐):
  1. 스크립트 예외가 `common.js`·`dev_tools_ext.js` 에서 나면 — 인라인 스크립트가 정의하던 전역이 빠진 것이다. 예외 글의 이름을 `page.executeJavaScript("window.<이름> = function () {};")` 로 심고 `dev_tools_ext.js` 를 다시 읽히지 말고, 래퍼가 돌려주는 HTML 의 `</head>` 앞에 `<script>window.<이름> = function () {};</script>` 를 끼운다
  2. `setValue` 가 없으면 `setValueAttribute` · `getFirstElementChild` 가 없으면 `getElementsByTagName("div").get(0)`
  3. 둘 해도 안 되면 — `ToolsFolderTest` 에 정적 단언(`dev_tools_ext.js` 의 `function fdFile(` 몸에 「섞인 줄끝의 자리」 가 있고 「BOM」 이 없다) + `DiffRoutesTest` 에 위 `mixed.txt` 두 파일로 `sameLines` 참·`byteDiffs` `[]` 사례. 이력 「정한 것(계획 밖)」 에 HtmlUnit 단언을 못 세운 까닭(예외 글)을 적는다
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='SmokeHtmlUnitTest,ToolsFolderTest,DiffRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus(`resources/tools`)

### 0-118a — 시험 빈칸: `hashText` 음성 · SqlJoins LATERAL·APPLY·괄호 친 UNION
- 대상: `MapperIndexTest` · `SqlJoinsTest` · 골든 `analyze/sql-joins.json`. `src/main` 안 고친다
- 시험:
  1. `MapperIndexTest.hashHintNegatives` — `q = "SELECT a # b FROM T90_A"`, 전부 `{}` 기대:
     | 파일 | databaseId | 무엇 |
     |---|---|---|
     | `T90_SQL_maria.xml` | — | maria |
     | `T90_SQL_oracle_mysql.xml` | — | 두 낱말(MySQL 이 이긴다) |
     | `T90_SQL_oracle.xml` | `mariadb` | 두 낱말(파일·id) |
     | `oracle/T90_SQL.xml` | — | 폴더 이름만 |
  2. `SqlJoinsTest.shapes` 에 열쇠 셋(주석 `// 0-118 — …`):
     | 열쇠 | SQL | 기대 |
     |---|---|---|
     | `lateral` | `SELECT * FROM T1 a JOIN LATERAL (SELECT b.ID FROM T2 b WHERE b.A_ID = a.ID) v ON v.ID = a.ID` | T1.ID—T2.A_ID |
     | `apply` | `SELECT * FROM T1 a CROSS APPLY (SELECT b.ID FROM T2 b WHERE b.A_ID = a.ID) v` | T1.ID—T2.A_ID |
     | `parenUnionTop` | `(SELECT a.X FROM T1 a JOIN T2 b ON a.ID = b.ID) UNION ALL (SELECT a.X FROM T3 a JOIN T4 b ON a.ID = b.ID)` | T1.ID—T2.ID · T3.ID—T4.ID |
- 의도된 골든 변화: `sql-joins.json` 새 열쇠 셋만
- Ruling: 시험만 더하는 행이라 RED 없음(TDD 예외). 기대와 다르면 공통 사다리 「시험만 더하는 행」
- 검증: `bash scripts/mvn.sh -q test -Dtest='MapperIndexTest,SqlJoinsTest'` → `bash scripts/verify.sh`
- 의존: 6-71 · 6-72(같은 시험 파일 — 고친 뒤 동작을 잰다)

### 0-118b — 시험 빈칸: `mergeRules` · AlterGen 기본값
- 대상: `core/profile/ProfileStoreTest` · `AlterGenTest`. `src/main` 안 고친다
- 시험:
  1. `ProfileStoreTest.mergeRulesShapes`(`ProfileStore.mergeRules` 는 public static) —
     | before | incoming | 기대 |
     |---|---|---|
     | `null` | `null` | `{}` |
     | `null` | `{a=true}` | `{a=true}` |
     | `{a={regex=x}}` | `null` | `{a={regex=x}}` |
     | `{a={params={max=5}}}` | `{a=false}` | `{a={params={max=5}, enabled=false}}` |
     | `{a={regex=x}}` | `{a={regex=y}}` | `{a={regex=y}}` |
     | `{a=true}` | `{a=false}` | `{a=false}` |
  2. `AlterGenTest.unportableDefaultOtherPaths` — `products.price` 기본값 `uuid_generate_v4()`:
     - **MSSQL**(기본값만 바꿈) — `-- [손] 컬럼 price 기본값 — 식을 대상 문법으로 못 옮겼다` 가 있다 · `sp_helpconstraint` 가 없다 · `DEFAULT` 가 든 살아 있는 문장(`-- ` 로 시작 안 하는 줄)이 없다
     - **형식 + 기본값**(`new Column(x.name(), x.ordinal(), x.nativeType(), x.jdbcType(), x.length(), 30, x.scale(), x.nullable(), "uuid_generate_v4()", x.comment(), x.domain())` — precision 을 픽스처와 다른 값으로) — `oracle`: `MODIFY (` + price 형식 문장이 있다 · `[손] … (앞 기본값은 그대로)` 가 있다 · `DEFAULT NULL` 이 없다 / `postgresql`: `ALTER COLUMN` … ` TYPE ` 문장이 있다 · `[손]` 이 있다 · `DROP DEFAULT` 가 없다
- 의도된 골든 변화: 없음
- Ruling: 시험만 더하는 행이라 RED 없음. MSSQL 이 그 식을 옮기면(`[손]` 이 없다) 그 사례를 빼고 새 행 — 공통 사다리
- 검증: `bash scripts/mvn.sh -q test -Dtest='ProfileStoreTest,AlterGenTest'` → `bash scripts/verify.sh`
- 의존: 1-115(같은 시험 파일)

### 0-119 — 5-100 누적 규칙
- 대상: `CLAUDE.md`(「마무리」) · `PLAN.md`(`5-100` 행)
- 변경:
  1. `CLAUDE.md` 「마무리 — 번들 끝」 의 번호 목록(8 「끝 보고」) 바로 아래, 「## Superpowers 와 잇는 법」 앞에 빈 줄을 두고 한 문단 —
     ```
     **순수본 밀린 목록**(사용자 2026-11-22) — 번들이 백엔드 코드 검사의 규칙·판정(`src/main/resources/check/rules.yaml` · `core/check`)을 고쳤는데 같은 판정이 순수본 코드 검사 화면의 JS 에도 있고 거기는 옛 동작이면, 4(push) 앞에 `PLAN.md` `5-100` 행 사실 칸 끝에 한 줄(` · 번들 N <행 번호> — <순수본이 옛 동작인 자리>`)을 더해 같은 PR 에 싣는다. 해당 없으면 안 적는다. 순수본은 번들마다 맞추지 않는다 — `5-100` 이 한 번에 재주입한다(코드 검사 손질이 잦아든 뒤 · 반입 직전)
     ```
  2. `PLAN.md` `5-100` 행 — 청크 칸 「순수본 재주입 묶음」 → 「순수본 재주입 묶음 — 순수본 밀린 목록」, 사실 칸 끝에 ` · **누적 규칙**(사용자 2026-11-22): 백엔드 코드 검사만 고친 번들은 마무리에서 여기에 한 줄(CLAUDE.md 「마무리」). 재주입은 한 번에 — 코드 검사 손질이 잦아든 뒤(반입 직전·프로젝트 끝)`. 상태 「대기」 그대로
- 이 번들은 코드 검사를 안 고친다 — `5-100` 에 더할 줄 없음(이력에 한 줄)
- Ruling: 문서 행이라 TDD 예외 — `doc-lint` 와 `bash scripts/mvn.sh -q test -Dtest=ToolsFolderTest`(CLAUDE.md 를 읽는 시험) 초록이 그 자리
- 검증: `bash scripts/verify.sh`
- 의존: S0

### V-69a — 정적 분석 · corpus 골든 확인
1. `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify`(PMD·SpotBugs·ErrorProne) — 새 짝은 코드로 고친다. 고치기 둘
2. `bash scripts/verify.sh --corpus`(약 15분). 다른 JVM·컨테이너를 같이 띄우지 않는다. 허용 변화 —
   - `analyze-egov-joins.json`·`deliverable-egov-relations.json`·다른 `analyze-*` 의 조인 — **느는 것**(질의 아닌 괄호 안 UNION 옆 가지의 바깥 별칭 등식 · 분기로 시작하는 서브쿼리의 안쪽 별칭이 제 표로 풀린 것)과 **주는 것**(분기 뒤 `(SELECT` 의 안쪽 별칭이 바깥을 덮어 생긴 것 · 파생 표 별칭이 바깥의 같은 별칭 표로 풀린 것). 줄마다 까닭을 `R-36` 이 댄다
   - `analyze-*.json`·`-unresolved.txt` — `jpql` 미해결이 **느는 것**·표 R 이 **주는 것**(`6-73` — 조상의 import 가 프로젝트 밖 클래스일 때만). `table` 미해결(등급 A)이 새로 나오면 7절
   - `-jspurl.txt`·JSP 링크 — `/` 링크가 **주는 것**만
   - `#` 로 표가 바뀌는 것 — 0 기대
   - 그 밖(`gen-*`·`hr-insert-*`·`ddl-*`·`check-*`·`cleanup-*`·`security-*`·`masking-*`·`logsql-*`·`csv-*`·`strip-*`·`sqlfixture`)은 diff 0 기대 — 바뀌면 원인 행과 댄다
   - 허용 밖 — 고치기 둘, 넘으면 `wip/V-69a`
3. diff 줄 수·종류별 건수·벽시계를 이력에
- 의존: 코드 행 전부

### R-36 — 손 판정 · 처분 표
- `design/compare-2026-10-28.md` 끝에 `## ㊴ 번들 90 — 번들 89 리뷰 행 일곱(2026-11-22)`:
  1. 처분 표 — 열(코드 여섯 · 시험 둘 · `0-119` · `0-118` 원 행) × (행 · 강제 지점 시험 · 닫음/한계/wip). 한계(각 행 「한계」 줄)를 행마다
  2. `V-69a` 골든 변화 표(종류별 건수 전 → 후). 0 이면 「없음」 한 줄
  3. 손 판정(SQL 본문·코드는 안 적는다 — 파일·문장 id·별칭 이름만) —
     - **조인에서 빠진 줄 전부(스물 넘으면 스물)**: 그 문장을 열어 분기 뒤 `(SELECT` 인지 파생 표 별칭인지. 둘 다 아니면 7절
     - **새 조인 전부(스물 넘으면 스물)**: 괄호 친 UNION 옆 가지인지 분기 괄호의 안쪽 별칭인지
     - **`jpql` 로 돌아간 경로 전부**: 그 엔티티의 조상 import 가 프로젝트 밖 FQCN 인지(같은 패키지·별 import 인데 빠졌으면 7절)
     - **빠진 `/` 링크 전부**: 단서와 따옴표 사이 글이 무엇인지 — 실제 루트 링크(`href`·`action`·`location` 의 값)가 빠졌으면 7절
  4. 끝에서 끝 — `run.bat --port 18873 --no-browser`(빈 `--data-dir`) → `4-45` 시험의 `mixed.txt` 두 파일을 임시 폴더 둘에 두고 `curl -s "http://127.0.0.1:18873/api/diff/file?a=…&b=…"` 가 `"sameLines":true` 와 `"byteDiffs":[]` · 서버를 끈다
- 코드·레인 지문이 안 바뀐다
- 의존: V-69a

### V-69 — `--db`
1. 여유 RAM — PowerShell `(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB`. 15 미만이면 7절. `--corpus` 가 끝난 뒤에만(겹쳐 띄우지 않는다)
2. `bash scripts/docker-up.sh` → `bash scripts/verify.sh --db`(컨테이너 아홉, 17~35분). 다른 JVM 을 같이 띄우지 않는다
3. 허용 변화 — 골든 diff 0 기대(`alter*`·`insert*`·probe 수). `alterRoundTrip`·`alterTwoReleases`·`ddlRoundTrip`·`insertRuns`·`readOnlyBypasses` 등 기존 등급 A 는 0. `[손]` 글이 바뀐 줄이 있으면 MariaDB `MODIFY COLUMN` 길인지 보고 수를 이력에
4. `verify.sh --db` 와 `git push` 는 따로 낸다
- 의존: R-36

### 마무리
CLAUDE.md 「마무리」 1~8 그대로 — 독립 리뷰 하나(**`model: fable`, effort `xhigh`** — 서브에이전트 호출에 둘 다 명시. Review Focus: ① `SqlJoins.Scopes` — `sideOrChild` 의 깊이 판정이 맞던 관계를 빼거나 옆 가지 별칭을 보이게 하는 꼴(중첩 괄호 · 짝 없는 `)` · 괄호 안 괄호 친 표) · `derived` 가 실제 표 별칭이나 스칼라 서브쿼리 별칭을 가리는 길 · `startsQuery` 가 분기 표시를 건너뛰어 잘못 여는 범위 ② `hashText` — 낱말 일치로 놓치는 흔한 매퍼 이름 꼴과 여전히 MySQL 매퍼에 걸리는 길 ③ `JpqlPaths.named` — 중첩 클래스·같은 파일 조상·스코프 붙은 이름에서 맞던 경로를 빼는 길 ④ `JspLinks.GAP` — 실제 루트 링크를 놓치는 꼴 · 역추적(REDOS) ⑤ `AlterGen.redefines` 가 `modifyColumn` 의 `MODIFY COLUMN` 가지 조건과 글자까지 같은가 · `defaultChanged` 부르는 횟수·차례 ⑥ `devToolsLineDiffSaysWhatDiffers` 의 하네스가 실물 화면과 다른 것을 재는 자리 ⑦ 절대 규칙 3 — 시험·`[손]` 글·compare ㊴ 에 SQL 본문·파일 내용이 실리는 자리 ⑧ 골든 diff 가 행의 「의도된 골든 변화」 와 다른 자리) → `bash scripts/gate-probe.sh`(「패치 갱신 필요」 면 다시 뜬다) → `verify.sh`(리뷰 고침이 건드린 무거운 레인만 다시 — corpus 먼저, 그다음 db) → push → PR(본문에 행마다 시작 세 줄 표 — `V-69a`·`R-36`·`V-69` 포함) → CI 폴링 → claude[bot] 「아니오」 처분(다시 쓴 코멘트에 「아니오」 없을 때까지) → **머지 위임** `gh pr merge N --merge --delete-branch` → 닫기 커밋은 새 로컬 가지에 → 끝 보고. 「순수본 밀린 목록」 — 이 번들은 해당 없음.

## 5. 금지 사항

- `pure/` · `ext-fixes.yaml` · `config/pmd/exclude-from-failure.properties` · `golden/check/pure-rules.json` · `rules.yaml` · 마이그레이션 V001~V017 을 고치지 않는다. 새 마이그레이션·새 규칙·새 의존성 0. `Unresolved.KINDS` 를 늘리지 않는다
- **안 고치는 코드** — `JpaIndex.java`(전부) · `SqlTables.java` · `core/text/SqlTokens.java` · `LineDiff` · `LocalFiles` · `FolderDiff` · `DdlGen.java` · `ProfileStore.java` · `DiffRoutes` 의 코드(javadoc 한 줄만) · `SqlJoins.extract`·`statement`·`outsideSet`·`setEnd`·`withoutBindSchema`·`Scopes.table`·`entry` · `JspLinks.kind`·`CUE`·`QUOTED` · `MapperIndex.Handler`
- self-check 기준선의 값을 올리지 않는다 · `AlterGen.Run` 에 메서드를 더하지 않는다 · `AlterGen.Run.modifyColumn` 에 분기를 더하지 않는다(줄 차례 바꾸기와 인자 하나만) · `:348`·`:362` 조건을 안 바꾼다
- 기존 경고·사유·미해결·`[손]` 글자를 바꾸지 않는다 — 「… 못 옮겼다, 손으로 쓴다(앞 기본값은 그대로)」 는 글자 그대로 남는다. 새 글은 새 글자로
- 화면은 `dev_tools_ext.js` 의 `fdFile` 한 줄만. `tools/*.html`·`common.js`·`common.css` 를 고치지 않는다. `SmokeHtmlUnitTest.JS_OFF` 를 안 바꾼다
- 기존 시험 단언을 지우거나 느슨하게 바꿔 통과시키지 않는다. `sql-joins.json` 의 옛 열쇠 스물일곱은 그대로
- `0-118a`·`0-118b` 에서 `src/main` 을 고치지 않는다 · 기대와 다른 출력을 골든·단언에 못 박지 않는다
- `MapperIndex` 픽스처 폴더(`fixtures/analyze/mapper/`)·`fixtures/analyze/jpa/` 에 파일을 더하지 않는다(인라인 `Source`)
- `0-119` — 「마무리」 의 번호(1~8)를 늘리거나 바꾸지 않는다 · `5-100` 에 번들 84~89 것을 소급해 적지 않는다
- 시험·경고·`[손]` 글·compare 절·로그에 실제 사업 코드·SQL 본문·파일 내용을 쓰지 않는다(절대 규칙 2·3)
- 무거운 판을 겹쳐 띄우지 않는다(정적 분석 → `--corpus` → 손 판정 → RAM 확인 → Docker → `--db`)
- 다른 저장소(portfolio·satchel)를 고치지 않는다 · worktree·`finishing-a-development-branch` 를 쓰지 않는다 · 새 가지를 안 딴다(닫기 커밋 빼고)

## 6. 최종 검증

- `bash scripts/verify.sh` 초록 · `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify` 초록 · `bash scripts/verify.sh --corpus` 초록(등급 A 0) · `bash scripts/verify.sh --db` 초록(컨테이너 아홉, 등급 A 0) · `bash scripts/gate-probe.sh` 열 전부 산다
- 강제 지점 시험이 전부 있다(S0 표, `wip/` 로 뺀 행 빼고) — 코드 행 이력에 RED → GREEN 한 줄, 시험·문서 행은 Ruling 한 줄
- 손 확인(R-36): compare ㊴ 처분 표 · 빠진·새 조인 · `jpql` 로 돌아간 경로 · 빠진 `/` 링크 · `byteDiffs` `curl`
- 회귀: `golden/` diff 는 `sql-joins.json` 새 열쇠 여덟(`6-71` 다섯 · `0-118a` 셋)과 `V-69a`·`V-69` 의 허용 변화뿐 · `self-check.txt` diff 0 · `pure-rules.json` diff 0 · 규칙 수 147 · 마이그레이션 17 · `Unresolved.KINDS` 20
- 분할표 — 일곱이 다 닫혔다(`0-118` 은 「완료 — `0-118a`·`0-118b`」) · 새 행 여섯 완료 · 15장 번들 90 완료 · 번들 91~ 행에 가로 점검 3차
- CI 초록 · claude[bot] 처분 · PR 머지 · 메모리 `bundle90-done`(다음 손 = 가로 점검 3차, 84~90) + `MEMORY.md` 한 줄

## 7. 중단 조건

다음이면 임의로 풀지 말고 멈추고 보고한다(어느 스텝 · 무엇이 예상과 달랐나 · 가능한 선택지).
- 행의 「사실」·2절 구조 설명이 실제 코드와 다르다(줄 번호 어긋남은 아님 — 메서드·흐름이 다를 때)
- 검증이 2회 연속 실패하고 원인이 이 플랜 범위 밖
- 5절을 어기지 않고는 진행할 수 없다(특히 — 기준선 값을 올리거나 `AlterGen.Run` 에 메서드·`modifyColumn` 에 분기를 더해야만 되는 고침 · `JpaIndex` 를 고쳐야만 되는 고침)
- 스텝에 없는 파일을 3개 이상 고쳐야 한다(골든·`self-check.txt` 는 스텝에 있는 것이다)
- `6-71` 뒤 `sql-joins.json` 의 옛 열쇠 스물일곱 가운데 바뀌는 것이 있다
- `1-115` 뒤 `alter-*.sql` 골든이 바뀐다
- `V-69a` — `table` 미해결(등급 A)이 새로 나온다 · 등급 B baseline 이 상한(표본의 2%)을 넘는다
- `R-36` — 빠진 조인 가운데 분기 괄호도 파생 표 별칭도 아닌 것이 있다 · 같은 패키지·별 import 조상인데 `jpql` 로 돌아갔다 · 실제 루트 링크가 빠졌다
- `V-69` — 기존 등급 A 가 새로 빨강 · `--db` 앞 여유 RAM 15GB 미만(다른 세션 컨테이너 — 사람이 조율한다)

번들 모드의 「갈려도 안 멈춘다」 는 위 밖에서만 선다. 행당 계획 밖 결정 둘까지, 넘으면 `wip/<청크>` 로 빼고 다음 행. 빠졌을 때 뒤 행 —
- `6-71` 이 빠지면 `0-118a` 의 SqlJoins 셋은 그대로 친다(지금 코드에서도 같은 기대다)
- `6-72` 가 빠지면 `0-118a` 의 `hashHintNegatives` 는 그대로 친다(전·후 같은 기대다)
- `1-115` 가 빠져도 `0-118b` 는 친다
- 그 밖 행은 서로 독립이다

## 8. 불확실 항목

사용자만 정할 수 있는 것 — 없음(범위 · 세부 넷 (가) · 머지 위임 · 컷 없음 · 반입·순수본 나중 · 점검 3차는 머지 뒤 — 다 답 받음).

실행해야만 알 수 있는 것 — 실행자가 해당 스텝에서 확인한다.
- **첫 스텝 전에**: `git log --oneline -1`(S0 커밋) · `git status --short` 깨끗 · `docker ps`(다른 세션 컨테이너가 떠 있으면 `V-69` 앞 RAM 을 미리 본다)
- **분기 표시가 낱말 하나로 잘리나**(시험 실행) — `6-71` RED 에서 `branchQueryParen` 의 지금 출력이 T2.ID—T4.ID 인지. 다르면(표시가 낱말로 안 남으면) 7절 「사실이 다르다」
- **HtmlUnit 이 인라인 스크립트를 뺀 `dev_tools.html` 에서 `common.js`·`dev_tools_ext.js` 를 예외 없이 돌리나**(시험 실행) — `4-45` 사다리
- **MSSQL 이 `uuid_generate_v4()` 를 못 옮기나**(시험 실행) — `0-118b`. 옮기면 그 사례를 빼고 새 행
- **조인·`jpql`·`/` 링크 골든이 얼마나 움직이나**(`--corpus`) — `V-69a` 허용 변화 · `R-36` 손 판정
- **행마다 self-check 값**(시험 실행) — `-Dtest=SelfCheckTest`. 설계 셈: 새·고친 메서드는 전부 14 이하(`Scopes.fill` 10 그대로 · `derived` 9 · `hashText` 9 · `named` 6) · `modifyColumn` 40 그대로. 다르면 공통 사다리
