# 설계 47 — 번들 63: 철저화 R3-6 — 매퍼 SQL 규칙 + SQL 표 추출 남은 결함 (Fable 설계 · Opus 실행, 2026-10-31)

## 맥락
compare ⑫(번들 62)가 「후보로」 에 둘을 올렸다 — 매퍼 SQL 규칙(`= NULL` 비교 · 암묵 교차 조인 · 페이징에 ORDER BY 없음 · `INSERT` 컬럼 목록 없음 · `NOT IN` 서브쿼리, compare ③ 다-1·2·3·10·12)과 새 행 6-38(`SqlTables`·`DdlReader` 남은 결함 다섯 + 마무리 리뷰 Minor 둘). 매퍼 규칙의 선행이던 표 추출 정확도(어긋남 102 → 35)가 섰다.

탐색 실측(2026-10-31, `d808b33`):
- **패키지 방향** — `core.analyze` → `core.check`(Source·JavaSource·SpringMappings·`RegexRule.stripComments`). `ArchitectureTest.noPackageCycles` 가 패키지 순환을 막는다 → check 의 규칙이 analyze 의 `SqlTables`·`SqlJoins` 를 못 부른다. `core.text` 는 check 를 import 하지 않고 analyze·check 둘 다 text 를 쓴다(SafeSax). corpus 레인·`LANE_DB_SKIP` 에 이미 있다.
- `MyBatisRules.Handler`(`apply` → SAX) — 문장 요소(select·insert·update·delete·sql)가 닫힐 때 `noWhere()`: `stmtText`(조각 글 + 자식 태그마다 `\n`)·`include`·`whereTag`·`stmtLine`. 조각마다 `characters()` 의 끝 줄 `loc.getLineNumber()` — `check()` 가 조각 안 줄바꿈 수로 거슬러 줄을 잰다. 자식 태그의 `\n` 은 실제 줄이 아니라 `stmtText` 의 줄바꿈 수로는 줄을 못 잰다.
- `SqlTables.tokens`(`:414`) — 위치 없는 `List<String>`. `STOP`·`tokens`·`word` 를 같은 패키지 `SqlJoins` 가 쓴다. `Scan.on` 은 `List<Boolean>`(층 켜짐만) · `targetDone` 은 문장 하나에 하나 → `WITH D AS (DELETE FROM A …) SELECT * FROM D` 의 A 가 R(Minor 둘째). `firstVerb(t, 0)` 이 `BEGIN SELECT … ; UPDATE …` 의 SELECT 를 동사로 → 태그 update 와 어긋나 `tagVerb`(Minor 첫째).
- 6-38 원문 — ansi `multi_statement_b` `from Sys.dual`(`table()` 이 `DUMMY.contains(x)` 를 사슬 첫 조각 SYS 로) · postgres `with` `) CYCLE id SET is_cycle USING path`(깊이 0 켜진 층의 USING) · tsql `openquery` `UPDATE OPENQUERY (…) SET`(`isFunction` 이 `pending == {R}` 일 때만) · tsql `predict` `CROSS APPLY PREDICT(MODEL = @model, DATA = d AS data)` — `d` 는 `FROM dbo.mytable AS d` 의 **별칭**, 셋째 사례만 `dbo.test_data`. 표로 세면 별칭이 표가 된다 → 안 한다(§3-16) · mariadb `create table y LIKE x`(`DdlReader.nameOnly` 가 CTAS·PARTITION 만, `core/gen` = db 레인).
- sqlfluff `test/fixtures/rules/std_rule_cases/*.yml` 94 가 표본 폴더에 이미 있다(MANIFEST 2,437 안) — CV05 15 사례 · AM08 28 · AM09 7, 키마다 `pass_str` 또는 `fail_str`(+`fix_str`·`configs`). AM08 의 `test_pass_select_comma`(`FROM a, b`)는 그쪽이 「안 다룬다」 로 pass — 우리는 잡는다(그쪽 특이로 기록).
- `CheckRulesTest` 가 내장 규칙 수 111 을 단언(`:167`) · `MyBatisRulesTest` 는 mybatis 규칙 전부가 `Pos*` 에서 걸리고 `Neg*` 0 · 골든 `check/mybatis.json`. 픽스처 `fixtures/check/mybatis/{Pos,Neg}*.xml` + `src/Neg*.java`(namespace 대조).
- 기준선·PMD — `self-check.txt` 에 `SqlTables … extract(2)` 21 · `tokens(1)` 17 · `MyBatisRules.Handler … startElement(4)` 17. PMD 짝은 FQCN 키(`SqlTables`·`MyBatisRules.Handler` 에 `AvoidLiteralsInIfCondition` 등) — 새 클래스는 깨끗해야 하고 글자 리터럴(`'\''`)도 `if` 안에서 잡힌다.

**사용자 결정(2026-10-31, 뒤집지 않는다)**
- 축은 **매퍼 SQL 규칙 다섯 + 6-38(`SqlTables` 셋 + Minor 둘) + sqlfluff 규칙 사례 채점** — 번들 하나·PR 하나
- 규칙 심각도 — `nullCompare`·`crossJoin`·`pagingNoOrder`·`notInSubquery` **warn** · `insertNoColumns` **info**
- 교차 조인 「이어짐」 판정 — **등식만**(`X.c = Y.c`, Oracle `(+)` 허용 — sqlfluff AM08 과 같은 뜻). BETWEEN·함수 안 등식 오탐은 R-12 손 판정이 잰다
- `CREATE TABLE … LIKE`(1-67, db 레인)는 **번들 68(생성기, `core/gen`)로 미룬다** → 이 번들은 `--db` 안 돈다
- **머지 위임 있음**
- PREDICT 인자 표는 안 한다(별칭 `d` 를 표로 세게 된다 — §3-16)
- 설계 41~46 그대로(에이전트 최소 · 검증 인프라 최적화 금지 · 표본 추가 없음 → `corpus/MANIFEST` 불변)

**라운드** — R3-1(58) → … → R3-5(62) → **R3-6(이 번들)**. 재측정은 V 행.

실행자는 Opus. 차례 `S0` → `5-57a` → `5-57b` → `5-57c` → `V-43a` → `6-38` → `V-43b` → `R-12` → `V-43` → 마무리. 가지 `work/2026-10-31b`(닫기 `d808b33` 로컬, 같은 PR). 번들 하나·PR 하나.

---

## 1. 목표와 범위
- 한 문장: 코드 검사 MyBatis 묶음이 매퍼 문장에서 `= NULL`·`<> NULL` 비교 · 조인 조건 없는 표(쉼표 조인·ON 없는 JOIN) · ORDER BY 없는 페이징 · 컬럼 목록 없는 `INSERT` · `NOT IN (SELECT …)` 를 줄 번호로 지적하고, `SqlTables` 가 `Sys.dual`·PG `CYCLE … USING`·`UPDATE OPENQUERY(…)` 를 표로 세지 않으며 PL/SQL 블록의 동사를 태그로 받고 DML CTE 안 표에 그 DML 글자를 준다.
- 완료 시 체감: 코드 검사에 eGov·레거시 매퍼를 넣으면 `mybatis.nullCompare`·`crossJoin`·`pagingNoOrder`·`insertNoColumns`·`notInSubquery` 가 파일·줄로 나온다(화면 변경 없음 — 규칙은 yaml 에서 읽힌다). sqlfluff CV05·AM08·AM09 사례 50 에서 일치율이 골든에 선다. `sqltables-diff` 35 → 32 안팎(dual·with·openquery 셋이 빠진다 — LIKE 는 번들 68, predict 는 그쪽 특이로). compare ⑬.
- In scope: 신설 `core/text/SqlTokens.java` · 신설 `core/check/SqlLint.java` · `core/check/MyBatisRules.java` · `core/analyze/SqlTables.java` · `resources/check/rules.yaml` · 픽스처 `fixtures/check/mybatis/PosSql.xml`·`NegSql.xml`·`src/NegSqlMapper.java` · 시험 `SqlTokensTest`(신설)·`SqlLintTest`(신설)·`MyBatisRulesTest`(골든)·`CheckRulesTest`(수 116)·`SqlTablesTest`·신설 `SqlRuleCasesCorpusTest` · 골든(`check/mybatis.json`·신설 `check/sql-lint.json`·`analyze/sql-tables.json`·corpus 골든) · `design/compare-2026-10-28.md` ⑬ · PLAN 4장·9장 한 구절 · 분할표·번들 표 · PROGRESS.
- Out of scope: `DdlReader`(`CREATE TABLE … LIKE` 는 1-67 — 번들 68, 사용자 결정) · `SqlJoins` 수정·이동 · `RegexRule.stripComments` 이동 · `PREDICT(… DATA = t)`(§3-16) · 동적 태그 분기(`<choose>`)를 가른 판정 · `<include>` 조각을 풀어 넣은 판정 · 새 화면·순수본 재주입(`pure-rules.json` 불변 — mybatis 규칙은 순수본에 없다) · 표본 추가 · `security.sqlDollar`/`mybatis.dollar` 겹침 · 레인 스크립트 수정 · `core/gen` 전부(db 레인).

## 2. 현재 구조 요약
「맥락」 실측에 더해:

**`SqlTables.extract`**(`:113-162`) — `tokens(stripComments)` → `firstVerb(t, 0)` → `tagVerb` 어긋남 → `Scan`(`on: List<Boolean>` 바닥 = `verb != null`, `targetDone`, `target`, `st`, `pending`) → 토큰마다 `track`(경계·괄호·VERBS 가 층을 켠다) → `open`(`lit` 층에서만 `opener(x, sc.verb, sc.targetDone)`) → `table`/`alias` → 끝에 `cteNames` 뺌. `table()`(`:199-231`): `skipEnd` → 괄호 친 표 → **`DUMMY.contains(x)`(첫 조각)** → 단어면 `chainEnd` → **`isFunction(t, k, pending)`(`pending.equals({R})` 일 때만)** → refs. `opener`(`:375`)·`target`(`:390`). 호출 — `MapperIndex.scan:176·187`, `SqlFixtureCorpusTest`. `SqlJoins`(`:27·88·101`)가 `SqlTables.tokens`·`STOP` 을 쓴다.

**`MyBatisRules`**(265줄) — `Handler.startElement`(`:158`): 루트 `<mapper namespace>` · 자식(`where`·`trim prefix`·`include` 표지, `stmtText.append('\n')`) · 문장 요소 시작(`depth = 1`·`stmtLine`·`stmtText`·`dupId`). `endElement`(`:199`): depth 0 이면 `noWhere()`. `characters`(`:227`): 조각 → `stripComments(sql)` → `stmtText.append` → `check(dollar)`·`check(selectStar)`(끝 줄 − 뒤 줄바꿈 수). `finding(rule, s, line, excerpt)`(`:120`) · `excerpt(line)`(`:261`) 원문 줄 160자. `on` 맵에 있는 규칙만 낸다.

**시험** — `MyBatisRulesTest.fixturesGolden`(ONLY_MYBATIS 프로필, `fixtures/check/mybatis` 전부 + `run.finish()`), `CheckRulesTest.fixturesGolden` 은 `mybatis/` 를 건너뛴다. `LegacyCorpusTest.check`·`CheckCorpusTest.allSources` → `golden/corpus/check-<표본>.json`(규칙별 count·files) + `check-<표본>-hits.txt` conformance(히트 파일 목록). `SqlFixtureCorpusTest` → `sqlfixture.json`·`sqltables-diff.txt`. corpus 시험은 jackson YAML(`YAMLFactory`)로 yml 을 읽는다.

**함정** — ① 기준선 키 `SqlTables|…|tokens(1)` 은 사라진다(허용) · `extract(2)` 숫자는 바뀌어도 된다 · **새 키 금지** — `SqlTokens.scan`·`SqlLint.*`·`Handler.sqlLint`·`lineAt` 전부 복잡도 ≤ 15·중첩 ≤ 4·길이 ≤ 100 ② PMD — `SqlTokens`·`SqlLint`·`SqlTokensTest`·`SqlLintTest`·`SqlRuleCasesCorpusTest` 는 새 FQCN 이라 기준선에 없다 → `AvoidLiteralsInIfCondition`(글자 상수 `static final char`) · `LiteralsFirstInComparisons`(`"X".equals(t)`) · `LooseCoupling`(선언은 `List`·`Map`·`Set`) · `AssignmentInOperand` · `AvoidDuplicateLiterals`(시험의 같은 글자 넷 이상은 상수) · `ReturnEmptyCollectionRatherThanNull` ③ `stmtText` 의 자식 태그 `\n` 은 실제 줄이 아니다 → 조각 시작 오프셋·줄 쌍(`segs`)으로 환산 ④ XML 글의 `<>` 는 CDATA 로 ⑤ `Neg*` 는 어느 mybatis 규칙에도 안 걸려야 — `${}`·`SELECT *`·WHERE 없는 쓰기·모르는 namespace 를 음성에 두지 않는다 ⑥ `dmlCte`·`plsqlBlock` 골든 두 항목이 바뀐다(의도) — 다른 항목 diff 0 ⑦ `AnalyzeCorpusTest`·`CliCorpusTest` 골든에 CRUD 글자가 든다 — DML CTE 가 실물 매퍼에 있으면 글자가 바뀐다(V-43b 가 읽는다) ⑧ `core/gen` 은 db 레인 — 1-67 을 넣으면 push 앞 `--db` ⑨ `std_rule_cases` 의 `configs.core.dialect` 는 무시한다(우리는 방언 안 가린다).

## 3. 설계 결정 — 전부 [고정]

**SqlTokens(core/text, 5-57a)**
1. `public record Tok(String s, int at)` · `public static List<Tok> scan(String stripped)` — `SqlTables.tokens` 로직 그대로 + 토큰 시작 위치(글자 `'` 는 따옴표 자리, `?` 는 `#`/`$` 자리, 따옴표 식별자는 여는 글자 자리) · `public static List<String> tokens(String)` = scan 의 글자 · `public static final Set<String> STOP`(옮김) · `public static boolean word(String)`·`wordChar(char)`. 복잡도 — `scan` 은 루프, 토큰 하나는 `next(s, i, out)`(분기 여섯: 공백·`'`·`#{`/`${`·따옴표·단어·그 밖) · `literalEnd`·`quoted` 옮김. 글자 리터럴은 `private static final char` 상수(PMD).
2. `SqlTables` — `static final Set<String> STOP = SqlTokens.STOP;` · `static List<String> tokens(String s) { return SqlTokens.tokens(s); }` · `word(x)` → `SqlTokens.word(x)` · `wordChar`·`literalEnd`·`quoted` 삭제. `SqlJoins`·시험 무수정, `sql-tables.json`·`sql-joins.json`·`mapper-index.json` diff 0. 버린 대안 — `SqlJoins`·`tokens` 를 통째 `core/text` 로(PMD 새 키·import 일곱 파일) · `SqlLint` 를 analyze 에(순환).

**SqlLint(core/check, 5-57b)** — 주석 뗀 SQL 글 하나 → `List<Hit(Kind kind, int at)>`. `public enum Kind { NULL_COMPARE("mybatis.nullCompare"), CROSS_JOIN("mybatis.crossJoin"), PAGING_NO_ORDER("mybatis.pagingNoOrder"), INSERT_NO_COLUMNS("mybatis.insertNoColumns"), NOT_IN_SUBQUERY("mybatis.notInSubquery") }`(`rule()`). `public static List<Hit> check(String stripped, boolean partial)` — `partial` = `<sql>` 조각이거나 `<include>` 가 있어 문장 전체를 모른다 → 문장 단위 둘(CROSS_JOIN·PAGING_NO_ORDER)은 안 잰다. 다섯 도우미, 각 복잡도 ≤ 15.
3. **NULL_COMPARE** — 토큰 `=`,`NULL` · `<`,`>`,`NULL` · `!`,`=`,`NULL` 이고 **조건 절 안**일 때(`cond`: `WHERE`·`ON`·`HAVING`·`WHEN` 뒤 참, `SET`·`SELECT`·`VALUES`·`THEN`·`ELSE` 뒤 거짓, 처음 거짓) → `at` 은 `=`/`<`/`!`. `UPDATE t SET a = NULL` 은 아니다. `IS NULL` 은 `IS` 가 단어라 안 맞는다. `#{}` 는 `?` 라 `a = #{x}` 는 아니다.
4. **NOT_IN_SUBQUERY** — `NOT`,`IN`,`(`,`SELECT` → `at` 은 `NOT`. `NOT IN (1, 2)`·`<foreach>` 목록은 아니다.
5. **INSERT_NO_COLUMNS** — 첫 단어 `INSERT`, 뒤 넷 안에 `INTO`(`IGNORE`·`LOW_PRIORITY`·`DELAYED`·`HIGH_PRIORITY` 건너뜀, `ALL` 이면 없음), 이름 사슬(단어·`.`) 뒤 토큰이 `VALUES`·`SELECT`·`WITH` 면 → `at` 은 그 토큰. 사슬 뒤가 `(`·`SET`·`?` 면 없음.
6. **PAGING_NO_ORDER** — `partial` 아님 · 첫 단어 `SELECT` 또는 `WITH` · 문장 어디에도 `ORDER`,`BY` 없음 · 페이징 표지 첫 것 — `LIMIT`(다음 토큰이 `1` 이면 아님) · `OFFSET` · `ROWNUM`(뒤가 `=`·`<`·`<=` 와 `1` 이면 아님) · `TOP`(다음이 `1` 또는 `(`,`1` 이면 아님) · `FETCH`,`FIRST|NEXT`(다음이 `1` 이면 아님) → `at` 은 표지. 깊이를 안 가린다(AM09 의 서브쿼리 fail/pass 둘 다 맞는다). `DELETE … LIMIT` 은 아니다.
7. **CROSS_JOIN** — `partial` 아님. 후보 = ⓐ 쉼표 묶음: `FROM`(앞이 `DISTINCT` 아님) 뒤 `표 [AS] 별칭` 을 읽고 `,` 가 이어지는 동안 반복, 항목이 `(` 면 묶음 버림, 둘 이상이면 전부 후보 ⓑ ON 없는 JOIN: `JOIN`(앞 셋 안에 `CROSS`·`NATURAL` 없음) 뒤 항목(`(` 면 짝 괄호 뒤 별칭) → 그 뒤를 깊이 0 에서 훑어 `ON`·`USING` 을 만나면 아님, `JOIN`·`WHERE`·`GROUP`·`ORDER`·`HAVING`·`UNION`·`LIMIT`·`;`·`)`·끝이면 후보. 별칭 = 뒤 단어가 `STOP` 밖이면 그것, 아니면 사슬 끝 이름. 이어짐 = `X . c [(+)] = Y . c` 꼴(X≠Y, 앞이 `.` 아님 — `SqlJoins.extract` 와 같은 뜻을 별칭 단계에서)에 나온 별칭 집합(+ `USING` 으로 닿은 것). 첫 「이어지지 않은 후보」 하나만 `at`. (질문 (나) 의 답이 「둘째」 면 이어짐 = 한 조건(`AND`·`OR`·`WHERE`·`ON` 사이) 안에 두 별칭이 같이 나오면 — 도우미 하나만 다르다.)
8. 라인 환산은 `SqlLint` 밖(MyBatisRules) — `SqlLint` 는 위치만.

**MyBatisRules 배선(5-57c)**
9. `Handler` 에 `List<int[]> segs`(조각 시작 오프셋 · 그 오프셋의 줄) · `String stmtTag`. 문장 시작에 비우고 태그 저장. `characters()`: `stripComments` 뒤 `segs.add(new int[] {stmtText.length(), endLine - newlines(sql)})` 다음 append. `endElement` depth 0: `noWhere(); sqlLint();`. `sqlLint()`: 다섯 규칙 중 켜진 것이 없으면 return · `partial = include || "sql".equals(stmtTag)` · `SqlLint.check(stmtText.toString(), partial)` 의 Hit 마다 `on.containsKey(rule)` 이면 `finding(rule, s, lineAt(at), excerpt(line))`. `lineAt(pos)`: `pos` 이하 시작 오프셋 중 마지막 seg → `seg[1] + stmtText[seg[0]..pos) 의 줄바꿈 수`(≥ 1). 자식 태그 `\n` 은 토큰이 안 걸린다. `startElement` 복잡도 17 그대로(분기 추가 없음), 새 메서드 ≤ 15.
10. `rules.yaml` ㄷ 묶음에 다섯 — `group: mybatis` · `kind: mybatis` · `globs: ['*.xml']` · severity 는 질문 (가) 답 · `message`·`fix`(60자 안)·`ref`(`sqlfluff CV05`·`sqlfluff AM08`·`sqlfluff AM09`·`sqlcheck`·`sqlcheck`). `mybatis.parseError` 앞에 둔다. `CheckRulesTest` 111 → 116.

**SqlTables 6-38 + Minor 둘(6-38)**
11. **더미 사슬 끝** — `table()` 에서 `k = chainEnd(t, i)` 를 먼저 구하고 `DUMMY.contains(t.get(k))` → `ALIAS_OK`, `return k`(`Sys.dual`·`SYS.DUAL`). `STOP` 의 `DUAL` 은 그대로(더미 판정이 앞선다).
12. **CYCLE 의 USING** — `Scan.cycle`(boolean): `track` 에서 바닥 단어 `CYCLE` 이면 참, `SELECT`·`;`·`GO`·`reset` 에서 거짓. `open()` 에서 `x.equals("USING") && sc.cycle` → null. `SEARCH … SET` 은 USING 이 없어 무관.
13. **대상 자리 표 함수** — `isFunction`: `pending.equals({R})` → `!pending.contains(Crud.C)`(R·U·D 는 함수, INSERT 의 C·MERGE 의 C·U 는 표 — `INSERT INTO t (a, b)` 불변). 잠금 힌트 예외 그대로. 기존 사례 `updateTop`(TOP 은 `skipEnd` 가 먼저)·`deleteFrom`·`truncate` 불변.
14. **PL/SQL 블록** — 첫 토큰이 `BEGIN`·`DECLARE` 면 `Result.verb = tagVerb`, `tagVerb` 어긋남 판정 안 함, 훑기 동사(`Scan` 바닥)는 그대로 `firstVerb(t, 0)`. `plsqlBlock` 골든 → verb UPDATE · unresolved [] · 표 그대로.
15. **층마다 동사** — `Scan.on: List<Boolean>` → `List<Layer>`(`final class Layer { String verb; boolean targetDone; }`, verb null = 꺼짐). `(` push 빈 Layer · `)` pop(바닥은 안 뺀다) · VERBS 단어는 **그 층의 verb 가 null 일 때만** 넣는다(첫 동사가 이긴다 — `INSERT INTO b SELECT …` 의 바닥은 INSERT 그대로). `lit` = 꼭대기 verb != null · `open` → `opener(x, top.verb, top.targetDone)` · `targetDone` 은 꼭대기 층에. `reset(v)` 바닥 = `Layer(v)`. `Scan.verb`(결과 동사)·`target`·`st`·`pending` 그대로. `dmlCte` 골든 → COMTNA D. 기존 다른 항목 diff 0(서브쿼리 층 동사는 SELECT → FROM 은 R 그대로).
16. **PREDICT 안 함** — `DATA = d` 의 d 가 별칭이라 표로 세면 틀린다. R-12 가 `predict` 줄을 「그쪽 특이·안 하기로」 로 옮긴다. 기대 `sqltables-diff` 35 → 32(dual·with·openquery). mariadb `create_table`(LIKE)은 1-67(번들 68)까지 남는다.
17. 새 분기는 도우미에(`extract` 21·`table` 의 복잡도 — `table` 은 지금 키가 없다 → 더미 판정을 `dummyEnd(t, i)` 로 빼 ≤ 15 유지).

**DdlReader(1-67 — 이 번들이 아니라 번들 68. 행 본문은 여기 둔다 — 설계 52 가 그대로 쓴다)**
18. `nameOnly` — `after` 가 `LIKE\s`(대소문자 무관)로 시작하면 key `like`, notes `"LIKE 원본 — 컬럼은 원본 표"`. `item()` 머리 — 항목이 `LIKE\s` 로 시작하면(PG `(LIKE x INCLUDING ALL)`) `d.notes.put("like", …)` 하고 return(컬럼 아님). `LIKE_CNT INT` 는 `\s` 가 없어 컬럼. `read` 길이 불변(도우미 안), `item` 복잡도 30 → 31 허용(기준선 숫자). 시험 — `likeHead`(`CREATE TABLE Y LIKE X` → [Y] 컬럼 0 notes like · unreadable 0) · `likeInParens`(`CREATE TABLE Y (LIKE X INCLUDING ALL)` → 같음) · `likeColumnNameStillColumn`(`CREATE TABLE T (LIKE_CNT INT)` → 컬럼 1).

**sqlfluff 규칙 사례 채점(V-43a)**
19. 신설 `src/test/java/kr/ejg/toolbox/core/check/SqlRuleCasesCorpusTest`(`@Tag("corpus")`, corpus 레인 폴더) — `CorpusFiles.files("sqlfluff-dialects", "test/fixtures/rules/std_rule_cases/{CV05,AM08,AM09}.yml")` → 키마다(`rule` 제외) `pass_str`/`fail_str` → `RegexRule.stripComments(sql, "sql")` → `SqlLint.check(text, false)` 에 짝 Kind(CV05→NULL_COMPARE · AM08→CROSS_JOIN · AM09→PAGING_NO_ORDER)가 있나 → 기대(fail=있음·pass=없음)와 대조. 골든 `corpus/sqlrules.json` `{"AM08": {"cases": 28, "agree": n}, …}` + conformance `sqlrules-diff`(줄 `AM08/test_pass_select_comma 기대=pass 우리=hit`) — 새로 깨짐·고쳐짐 빨강. 예외는 등급 A(`CorpusFiles.none`). `configs` 무시. `fix_str` 무시.

**측정·판정**
20. V-43b 재채점 — `CheckCorpusTest`·`LegacyCorpusTest`(새 규칙 다섯의 count·files 가 `check-<표본>.json` 에 생긴다 · `-hits.txt` 갱신) · `SqlFixtureCorpusTest`(35 → N) · `AnalyzeCorpusTest`·`CliCorpusTest`(diff 0 기대 — 글자가 바뀌면 R-12) · `SqlRuleCasesCorpusTest`(일치). `DdlCorpusTest` 는 안 돈다(`DdlReader` 불변). 전·후·벽시계를 이력에. 수치는 게이트 아님(사다리 둘: §3-6 `LIMIT 1` 류 제외 목록 · §3-7 의 ON 없는 JOIN 후보 — 소음이면 쉼표 묶음만).
21. R-12 손 판정 — ① 새 규칙 다섯 × 표본(egov·commons·jspwiki·roller·egov-react·jeecg·jeesite·zheng·ruoyi-classic)에서 규칙마다 새 지적 ≤ 20 줄(파일·줄·판정·근거 한 구절, 코드 본문 안 옮김) → 참/오탐 ② `sqlrules-diff` 줄 전부 분류(우리 결함·그쪽 특이·안 하기로) ③ `sqltables-diff` 남은 줄 중 바뀐 것(dual·with·openquery·predict) 재분류 — `create_table`(LIKE)은 「1-67 번들 68」 ④ analyze 골든 글자 변화가 있으면 그 문장(id·표·전후 글자). 결함 둘 이하는 이번에 고침, 넘으면 새 행.
22. compare ⑬ — `## ⑬ 재측정 — R3-6(번들 63, 2026-10-31)`: 규칙 다섯 × 표본 건수 표 · sqlfluff 사례 일치 표(규칙·사례·일치) · 어긋남 35 → N(종류별) · R-12 표 넷 · 벽시계 · 「드러난 것」 · 「후보로」(번들 64~68 행 이름 + 겹침 규칙 + PREDICT + 1-67). PLAN 4장 sqlfluff 구절에 「`std_rule_cases` CV05·AM08·AM09 → 매퍼 SQL 규칙(V-43 — 일치 N/50)」 · PLAN 9장 `core.text` 줄에 `SqlTokens(SQL 토큰·STOP — analyze·check 공용, 5-57a)` · `core.check` 줄에 `SqlLint`.

## 4. 실행 스텝

### S0 — 설계 문서·행·번들 표 (설계 세션이 한다)
- 대상: 신설 `design/47-bundle63.md`(이 절의 「맥락」~8절) · `PLAN.md`(번들 표 「번들 62」 아래 「번들 63」~「번들 68」 여섯 행 · 분할표 — 6-38 행(대기)을 다시 쓰고 그 아래 `5-57a`·`5-57b`·`5-57c`·`V-43a`·`1-67`·`V-43b`·`R-12`·`V-43` · 4장·9장 한 구절) · `PROGRESS.md`(「열려 있는 것」 머리 → 「**번들 63(설계 47, 철저화 R3-6 — 매퍼 SQL 규칙 + SQL 표 추출 남은 결함) 설계 끝 — 가지 `work/2026-10-31b`**, 다음은 「번들 해」」, 이력 S0 줄 — 직전 줄 해시 칸 `d808b33`).
- 절차: 가지 `work/2026-10-31b` 그대로 → 세 파일 → `bash scripts/doc-lint.sh design/47-bundle63.md PLAN.md PROGRESS.md` → `bash scripts/verify.sh` → `git commit -m "docs: S0 설계 47 — 번들 63 행·번들 표(철저화 R3-6 매퍼 SQL 규칙·6-38)"`.

### 5-57a — `SqlTokens`(core/text) + `SqlTables` 위임 + 시험
- 대상: 신설 `core/text/SqlTokens.java` · `core/analyze/SqlTables.java` · 신설 `src/test/java/kr/ejg/toolbox/core/text/SqlTokensTest.java`
- 변경: §3-1·2. `SqlTokensTest` 둘 — `positions`(`"SELECT a, 'x''y' FROM \"T\" WHERE b = #{b} -- c"` 의 토큰 글자·`at` 목록 단언 — `'` 는 따옴표 자리, `?` 는 `#` 자리, `T` 는 `"` 자리) · `wordsAndStop`(`word("A1")`·`!word("(")`·`STOP.contains("FROM")`).
- 검증: `bash scripts/mvn.sh -q test -Dtest=SqlTokensTest,SqlTablesTest,SqlJoinsTest,MapperIndexTest,SelfCheckTest,ArchitectureTest` → 골든 `sql-tables.json`·`sql-joins.json`·`mapper-index.json` diff 0 · `self-check.txt` 는 `tokens(1)` 줄 삭제뿐 · `bash scripts/mvn.sh -q pmd:check` 0 · `bash scripts/verify.sh`.
- 의존: 없음.

### 5-57b — `SqlLint`(core/check) + 시험
- 대상: 신설 `core/check/SqlLint.java` · 신설 `src/test/java/kr/ejg/toolbox/core/check/SqlLintTest.java` · 신설 골든 `golden/check/sql-lint.json`
- 변경: §3-3~8. `SqlLintTest.golden` — `CASES`(이름 · partial · SQL)를 돌려 `{name, hits: [kind@at …]}` 골든: `nullEq`(`SELECT ID FROM T WHERE A = NULL`) · `nullNe`(`<> NULL`·`!= NULL`·소문자 `null`) · `setNullNotCond`(`UPDATE T SET A = NULL WHERE B = NULL` → 하나, WHERE 쪽) · `isNull`(없음) · `bindNotNull`(`A = #{a}` 없음) · `caseWhenNull`(`SELECT CASE WHEN A = NULL THEN 1 ELSE 0 END FROM T` → WHEN 뒤 하나) · `notInSub` · `notInList`(없음) · `inSub`(없음) · `insertValues`(`INSERT INTO T VALUES (1)`) · `insertSelect` · `insertIgnore`(`INSERT IGNORE INTO T SELECT …`) · `insertCols`(없음) · `insertSet`(없음) · `insertAll`(없음) · `limitBind`(`SELECT ID FROM T LIMIT ?`… 토큰은 `?` — SQL 은 `LIMIT #{a}, #{b}`) · `limitOne`(없음) · `offset` · `rownumPaging`(`… WHERE RN BETWEEN 1 AND 10` 안쪽 `ROWNUM` — ORDER BY 없음 → 있음) · `rownumOne`(없음) · `oraclePagingOrdered`(없음 — `SqlTablesTest.oraclePaging` SQL) · `top`(`SELECT TOP 10 * FROM T`) · `topOne`(없음) · `fetchFirst` · `withLimit`(`WITH C AS (…) SELECT * FROM C LIMIT 5`) · `deleteLimit`(없음) · `partialPaging`(partial true → 없음) · `commaNoLink`(`SELECT a.X FROM TA a, TB b WHERE a.ID = ?`) · `commaLinked`(없음) · `commaThreeOneLoose`(`FROM TA a, TB b, TC c WHERE a.ID = b.A_ID` → c 에서) · `commaPlus`(`(+)` 없음) · `joinNoOn`(`FROM TA a JOIN TB b WHERE a.ID = ?`) · `joinNoOnButWhereLink`(없음 — AM08 `test_noop_join_where`) · `joinOn`(없음) · `leftJoinOnThenJoinNoOn`(둘째에서) · `crossJoinExplicit`(없음) · `naturalJoin`(없음) · `usingJoin`(없음) · `subqueryItem`(`FROM (SELECT …) a, TB b` → 없음 — 묶음 버림) · `partialCross`(partial → 없음) · `extractFromNotGroup`(`SELECT EXTRACT(DAY FROM d) FROM T` 없음).
- 검증: `bash scripts/mvn.sh -q test -Dtest=SqlLintTest,SelfCheckTest` · `pmd:check` 0 · `verify.sh`.
- 의존: 5-57a.

### 5-57c — 규칙 다섯 배선 + 픽스처 + 골든
- 대상: `resources/check/rules.yaml` · `core/check/MyBatisRules.java` · 신설 `fixtures/check/mybatis/PosSql.xml`·`NegSql.xml`·`src/NegSqlMapper.java`(빈 `public interface NegSqlMapper {}`) · `CheckRulesTest`(`:167` 111 → 116) · 골든 `check/mybatis.json`
- 변경: §3-9·10. `PosSql.xml`(namespace `NegSqlMapper`, CDATA 로 `<>`): `nullCmp`(`… WHERE A = NULL AND B <> NULL` — 한 줄에 둘이면 줄 하나) · `cross`(`SELECT a.X FROM TA a, TB b WHERE a.ID = #{id}`) · `joinNoOn` · `paging`(`SELECT ID FROM T LIMIT #{first}, #{count}`) · `rownum`(안쪽 ROWNUM, ORDER BY 없음) · `ins`(`INSERT INTO T VALUES (#{a})`) · `insSel` · `notIn` · `setNullInCond`(`UPDATE T SET A = NULL WHERE B = NULL` — 줄은 WHERE 줄, SET 은 아님: 두 줄로 쓴다) · 줄 환산 사례: `<if>` 자식 뒤 조건(`SELECT ID FROM T WHERE 1=1\n<if test="x != null">\n AND A = NULL\n</if>` → `AND` 줄). `NegSql.xml`: `isNull` · `setNull`(`UPDATE T SET A = NULL WHERE ID = #{id}`) · `commaLinked` · `plus` · `joinOn` · `crossJoinExplicit` · `using` · `pagingOrdered` · `limitOne` · `rownumOne` · `oraclePaging`(안쪽 ORDER BY) · `includeOrder`(`SELECT ID FROM T LIMIT 10 <include refid="ord"/>` + `<sql id="ord">ORDER BY ID</sql>`) · `frag`(`<sql id="frag">FROM TA a, TB b</sql>`) · `cols` · `set`(`INSERT INTO T SET A = #{a}`) · `notInList` · `inSub` · `deleteLimit`. 음성에 `${}`·`SELECT *`·WHERE 없는 쓰기 없음.
- 검증: `bash scripts/mvn.sh -q test -Dtest=MyBatisRulesTest,CheckRulesTest,SelfCheckTest` → `mybatis.json` diff 가 새 항목뿐(기존 10 그대로) · 음성 0 · 「양성에서 안 걸린 규칙」 0 · `pmd:check` 0 · `verify.sh`.
- 의존: 5-57b.

### V-43a — sqlfluff 규칙 사례 채점 시험
- 대상: 신설 `src/test/java/kr/ejg/toolbox/core/check/SqlRuleCasesCorpusTest.java` · 신설 `golden/corpus/sqlrules.json`·`sqlrules-diff.txt`
- 변경: §3-19. 첫 실행 `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest=SqlRuleCasesCorpusTest -Djunit.timeout=10m -Dgolden.update=true` → diff 목록을 읽고 이력에(사례·기대·우리). 둘째 실행 diff 0.
- 검증: 위 명령(갱신 없이) 초록 · `VerifyLanesTest` 초록(새 시험이 `core/check` 시험 폴더라 레인 목록 그대로) · `pmd:check` 0 · `verify.sh`.
- 의존: 5-57c.

### 6-38 — `SqlTables` 남은 결함 셋 + Minor 둘 + 시험
- 대상: `core/analyze/SqlTables.java` · `SqlTablesTest.java` · 골든 `analyze/sql-tables.json`
- 변경: §3-11~17. `CASES` 추가: `dummyChain`(`SELECT 1 FROM SYS.DUAL` → 표 0) · `cycleUsing`(`WITH RECURSIVE C AS (SELECT ID FROM COMTNA UNION ALL SELECT ID FROM C) CYCLE ID SET IS_CYCLE USING PATH SELECT * FROM C` → COMTNA R 만) · `updateTableFunction`(`UPDATE OPENQUERY(LNK, 'SELECT 1') SET X = 1` → 표 0) · `deleteTableFunction`(`DELETE FROM OPENQUERY(LNK, 'SELECT 1')` → 0) · `insertIntoStillTable`(`INSERT INTO COMTNA (X) VALUES (1)` 이미 `insertValues` — 그대로) · `declareBlock`(`DECLARE V NUMBER; BEGIN UPDATE COMTNB SET Y = 1; END;`, tag update → verb UPDATE · B U · unresolved []) · `dmlCteUpdate`(`WITH U AS (UPDATE COMTNA SET X = 1 RETURNING *) UPDATE COMTNB SET Y = 1`, tag update → A U · B U). 기존 `plsqlBlock`·`dmlCte` 두 항목의 골든 변화(§3-14·15)만 허용.
- 검증: `bash scripts/mvn.sh -q test -Dtest=SqlTablesTest,SqlJoinsTest,MapperIndexTest,AnalyzeRunnerTest,SelfCheckTest` · 골든 diff = 새 항목 + 두 항목 · `self-check.txt` 는 `extract(2)` 숫자만 · `pmd:check` 0 · `verify.sh`.
- 의존: 5-57a.

### (1-67 은 이 번들에 없다 — 번들 68. 행 본문은 §3-18, 분할표 행은 선행 「번들 68 S0」)

### V-43b — 재채점(corpus 골든 갱신) + 시간
- 대상: `golden/corpus/check-*.json`·`check-*-hits.txt`(egov·commons·jspwiki·roller·egov-react·jeecg·jeesite·zheng·ruoyi-classic) · `sqlfixture.json`·`sqltables-diff.txt` · `sqlrules.json`·`sqlrules-diff.txt`(V-43a 뒤 바뀌면) · analyze·cli 골든(diff 0 기대)
- 변경: `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='CheckCorpusTest,LegacyCorpusTest,SqlFixtureCorpusTest,SqlRuleCasesCorpusTest,AnalyzeCorpusTest,CliCorpusTest' -Djunit.timeout=10m -Dgolden.update=true` → diff 를 **읽고** 전·후를 이력에(규칙 다섯 × 표본 count·files · 어긋남 35 → N · 일치 n/50 · 벽시계). analyze·cli 골든이 바뀌면 고치지 않고 R-12 로.
- 검증: 여섯 시험 초록(갱신 뒤 다시 돌려 diff 0) · 시험 하나 10분 안 · 등급 A 0.
- 의존: V-43a · 6-38.

### R-12 — 손 판정(새 지적 · 사례 어긋남 · 남은 어긋남 · 글자 변화)
- 대상: `design/compare-2026-10-28.md`(⑬ 「손 판정」 절) · `PROGRESS.md`
- 변경: §3-21. 표 넷. 결함 둘 이하는 고치고 골든 다시(V-43b 명령), 넘으면 새 행 번호를 「후보로」. 코드 본문은 안 옮긴다(규칙 3).
- 검증: `bash scripts/doc-lint.sh design/compare-2026-10-28.md` · 분류 합 = 줄 수.
- 의존: V-43b.

### V-43 — compare ⑬ · PLAN 구절 · `--corpus`
- 대상: `design/compare-2026-10-28.md`(⑬ 나머지 — §3-22) · `PLAN.md` 4장·9장 구절 · `PROGRESS.md`
- 검증: `bash scripts/verify.sh --corpus` 초록 → `verify.sh` 초록. `--db` 는 안 돈다(`core/gen`·MANIFEST·pom 불변 — 끝 줄이 db 를 부르면 보고). 다른 저장소(ProjectShop)가 Docker `--full` 을 돌리는 중이면 `--corpus` 는 그와 겹쳐도 된다(node·파일만) — Docker 는 안 쓴다.
- 의존: R-12.

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7 · 행의 축·강제 지점 · 양성·음성(SET 의 `= NULL` 음성 · `<sql>` 조각의 교차 조인 음성 · `LIMIT 1` 음성 · `INSERT INTO t (…)` 표 유지 · 첫 동사 우선 층 · 바닥 pop 없음) · 골든이 의도된 변화인가(`plsqlBlock`·`dmlCte` 둘뿐 · analyze 골든 불변) · `ArchitectureTest` 순환 0) → `gate-probe.sh` → `verify.sh` → `git push -u origin work/2026-10-31b` → `gh pr create --base main`(행마다 시작 세 줄 표 + 닫기 `d808b33` 같이 + ⑬ 핵심 수치) → CI 폴링 → claude[bot] 처분 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → 닫기 커밋은 다음 가지 → 끝 보고. PR 본문 끝 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## 5. 금지 사항
- `pure/`·portfolio·`docs/`·화면 HTML/JS 수정 금지 · 새 Maven 의존성 금지 · `corpus/MANIFEST`·표본 변경 금지 · `scripts/verify-fingerprint.sh` 수정 금지 · **`core/gen`·`pom.xml` 수정 금지**(db 레인 — `--db` 가 들어온다)
- `core/check` 가 `core/analyze` 를 import 하지 않는다 · `core/text` 가 `core/check` 를 import 하지 않는다(`ArchitectureTest`) · `SqlJoins`·`RegexRule.stripComments` 이동·수정 금지
- `SqlTables` 공개 API(`extract`·`Result`·`Ref`·`Crud`·`BRANCH`) 불변 · `STOP`·`DUMMY`·`SKIP`·`HINTS` 집합 내용 불변(`STOP` 은 자리만 `SqlTokens` 로) · `SELECT INTO` 잡기 금지 · PREDICT 인자 표 금지
- 기존 규칙 여섯(`mybatis.dollar`…`parseError`)의 판정·줄 불변 · `noWhere` 불변 · 새 규칙은 `<mapper>` XML 글에만(Java 의 SQL 문자열은 범위 밖)
- `SqlFixtureCorpusTest`·`CheckCorpusTest`·`LegacyCorpusTest` 판정 로직 수정 금지 · 골든 갱신은 V-43a·V-43b(와 R-12 고침 뒤) 뿐
- `self-check.txt` 새 키 금지(`tokens(1)` 삭제 · `extract(2)`·`item(3)` 숫자 변동만) · PMD 새 짝 금지 · `startElement` 에 분기 추가 금지
- 발췌·골든·compare 에 코드 본문을 싣지 않는다(규칙 3 — 파일·줄·규칙·문장 id·표 이름까지)

## 6. 최종 검증
- `bash scripts/verify.sh` · `--corpus` 초록 · `--db` 안 돈다(끝 줄 확인) · `pmd:check` 0 · `gate-probe.sh` 10/10
- 골든: `sql-joins.json`·`mapper-index.json`·`java-graph.json`·`gen/*`·`fixtures.json`·`java.json`·`pure-rules.json` diff 0 · `sql-tables.json` 은 새 항목 + `plsqlBlock`·`dmlCte` · `mybatis.json` 은 새 항목만 · `self-check.txt` 는 `tokens(1)` 삭제 + `extract(2)` 숫자 · corpus 골든 전·후가 이력·⑬ 과 일치 · analyze·cli 골든 diff 0(아니면 R-12 가 설명)
- 시험: `SqlTokensTest` 2 · `SqlLintTest` 1(골든 사례 40 안팎) · `MyBatisRulesTest` 골든 +18 안팎 · `CheckRulesTest` 116 · `SqlTablesTest` CASES +7 · `SqlRuleCasesCorpusTest` 1 · corpus 시험 10분 안
- 수동: 코드 검사 화면에 `C:/workspace/toolbox-corpus/egov` 매퍼 폴더 → 규칙 다섯이 파일·줄로 보인다(사람 몫 ① 에 한 줄) · 붙여넣기 `SELECT a.X FROM TA a, TB b WHERE a.ID = 1` (xml 로 감싸) → `crossJoin`
- compare ⑬ 표 + R-12 표 넷 + 「드러난 것」 + 「후보로」

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- `ArchitectureTest.noPackageCycles` 빨강 · `self-check.txt` 새 키 · `pmd:check` 에 새 짝
- `sql-tables.json` 에서 `plsqlBlock`·`dmlCte` 밖 기존 항목이 바뀐다 · `sql-joins.json`·`mapper-index.json` 이 바뀐다 · `mybatis.json` 기존 10 이 바뀐다
- V-43b 에서 analyze·cli·ddl 골든이 바뀌는데 DML CTE 로 설명이 안 된다 · `sqlfixture-a` > 0 · 어긋남이 35 보다 는다 · 새 규칙 하나가 한 표본에서 500 건을 넘는다(소음 — 판정 폭 사다리로) · 시험 하나 10분 초과
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① 재채점 수치(§3-20 기대는 추정 — 규칙 다섯의 실물 건수, sqlfluff 일치율, 어긋남 N) ② `AnalyzeCorpusTest`·`CliCorpusTest` 골든에 DML CTE 글자 변화가 있는지(실물 매퍼에 DML CTE 가 있는지) ③ PMD 가 `SqlTokens` 의 `char` 상수 비교를 받아들이는지(`pmd:check` 첫 실행) ④ `std_rule_cases` yml 의 사례 키 수(CV05 15 · AM08 28 · AM09 7 은 `grep -c` 근사). 첫 스텝 전: `bash scripts/verify.sh` 초록 · `ls C:/workspace/toolbox-corpus/sqlfluff-dialects/test/fixtures/rules/std_rule_cases | wc -l`(94) · `git log --oneline -1`(`d808b33`).
- **사용자만 정할 수 있는 것** — 없음(심각도·판정 폭·LIKE 시점·머지 위임은 2026-10-31 받았다 — 「사용자 결정」).

