# 설계 79 — 번들 92: 가로 점검 4차 결과 + 대기 행 여섯 + 후보·잠재 (Fable 설계 · Opus 실행, 2026-11-24)

## 맥락

번들 91(#136 `fccf261`)이 가로 점검 3차의 결함 152 건을 고쳤다. 그 고침이 **새로 만든 결함·덜 고친 자리**를 찾으려고 가로 점검 4차를 했다 — 번들 91 이 바꾼 `src/main`·화면 파일 79개(`git diff --name-only 57760d1 fccf261`, +2,251줄)를 영역 여섯으로 나눠 점검자(Fable xhigh) 하나씩이 바뀐 덩이와 맞닿는 자리를 읽고 탐침을 돌렸고, 지적마다 내가 인용 줄을 코드로 다시 읽어 판정했다. 결과는 `design/78-audit4.md`(이 플랜의 부록 A 가 그 원문)다. 이 번들이 그 지적과 번들 91 마무리 리뷰의 대기 행 여섯(`5-146`·`0-126`·`6-93`·`3-27`·`5-147`·`1-129`), 설계 76 §4 의 후보·한계 조각, 잠재 A-33·A-37 의 처분을 한 PR 로 닫는다. 실행은 무인이다.

**사용자 결정(2026-11-23 — 고정, 다시 묻지 않는다. 메모리 `bundle92-decisions`)**
1. 범위 **(나)** — 가로 점검 4차(번들 91 diff) + 대기 행 여섯 + 설계 76 §4 후보(「확인 후 고침」 — 재현 안 되면 기록하고 건너뜀)·한계 조각 + 잠재 A-33·A-37. 「최대한 번들에 많이」
2. Fable 설계 → Opus 「번들 해」 · 무인 · **머지 위임** · 시간 컷 없음 · 마무리 독립 리뷰 Fable xhigh · 닫기 커밋은 새 로컬 가지
3. **무거운 검증은 묻지 않고 돈다** — 밤에는 사람이 projectshop 컨테이너를 내려 둔다(`--corpus` → `--db` 차례, 겹쳐 띄우지 않는다)
4. 뺌 — `5-100`·`5-31`·`5-134` 순수본 재주입 · 반입 zip · 조건부 `5-14`·`5-15`·`6-9` · 보류 `2-7`·`2-8`·`5-7`·`4-10` · `2-17`. 코드 검사(`core/check` 판정)를 고친 행은 마무리에서 `5-100` 에 한 줄(CLAUDE.md 「순수본 밀린 목록」)

**보정 A — 대기 행 여섯**(2026-11-23, 트리 `b46906a` = 코드 `fccf261`, 탐침은 jshell 표준입력 · `target/app.jar` 06:14 빌드 = HEAD 코드 — 파일을 쓰지 않았다). 여섯 다 **확인(남음)** — 재현 꼴은 행의 「사실」 줄
- `5-146` 셋 — 점검자 D 가 재현하고 넓혔다(끊긴 따옴표 뒤 줄 수가 주는 것 D4-01 · 주석 안 여는 태그 1c) → `5-146a`·`5-146b`·`5-146c` 로 나눈다
- `0-126` — 기동 쪽은 내가 코드로, 부분 클론 쪽은 점검자 C2 가 git 2.54 로(이 저장소 `.git/config` 에 옛 탐침이 남긴 `remote.zzprobe.partialclonefilter` 로 재현 — 「첫 손」 에서 지운다)
- `6-93` 둘 — 점검자 C2 가 재현하고 같은 뿌리 넷(C2-01·05·06·07)·둘(C2-09·10)을 더 찾았다 → `6-93a`(타입 풀기)·`6-93b`(방문 열쇠)
- `3-27` · `5-147` · `1-129` — 내가 코드·탐침으로. `1-129` 의 `0x…ADD` 거절은 **엄격한 쪽 유지**로 처분(3절)

**보정 B — 설계 76 §4 후보 여섯 · 잠재 둘 · 한계 다섯**
| 것 | 판정 | 처분 |
|---|---|---|
| 후보 `BlankRelRule.lineOf` 제곱 시간 | 확인(4만 줄 10.3초) | `5-146c` |
| 후보 `rel` 이 `NUL`·`COM1` 인 결과 쓰기 | 확인(`relOk` 가 받는다) | `0-127` |
| 후보 DEFAULT 식이 첫 토막만(`SYSDATE + 1`) | 확인(열한 꼴) — 닿는 길은 DTO·INSERT 뿐 | `1-130` |
| 후보 `RowCounter` 가 드라이버의 「지원 안 함」 을 REFUSED 로 | 확인 | `1-133` |
| 후보 `Job.subscribe` 가 락을 쥔 채 SSE 를 쓴다 | 확인(코드) — 재생분이 소켓 버퍼보다 작아 실제로 안 막힌다 | **안 세움** — 고치려면 이벤트 차례 보장을 다시 짜야 한다(무인 실행에 위험) |
| 후보 `removeSpaces` 가 `E Mail`·`EMAIL` 을 합친다 | 확인(코드) | **안 세움** — 순수본 JS 와 같은 판정(`Js.removeSpaces` 는 포팅). 공백 든 컬럼 이름 표본 0 |
| 잠재 A-33 `REFERENCES T ()` | **잠재 유지** | `DdlGen`·`AlterGen` 은 스냅샷만 받고(`DeliverableRoutes:92`·`MetaRoutes:76`) 스냅샷 FK 는 참조 컬럼이 늘 있다. `DdlReader` 결과는 `DtoGenerator`·`InsertGen` 으로만 간다(점검 A — 같이 확인 ③) |
| 잠재 A-37 DDL 의 `ON DELETE` 를 안 읽는다 | **잠재 유지** | 읽은 FK 규칙을 쓰는 소비자가 지금도 없다(`Validation.of:150` 은 주석 글만) |
| 한계 D-11 메서드만 붙인 자바 | 싼 신호 있음(점검 D — 표본 17 에서 참 5·거짓 11) | `5-149` |
| 한계 E-13 JSX 글 줄 | 싼 신호 둘(점검 E) | `5-158`(가운데 줄만 한계로 남는다) |
| 한계 E-15 다수 쪽이 프로필과 같은 섞인 파일 | 가장 작은 고침이 파일 넷·자리 여섯(점검 E) | `5-157a`·`5-157b` |
| 한계 A1-02 뿌리(같은 방언 식 기본값을 안 옮긴다) | **한계 유지** | 옮기면 깨지는 꼴 넷(스키마가 박힌 시퀀스 참조 · 생성 컬럼 식 · MySQL 8 괄호 문법 · `ON UPDATE`)이고 `--db` 왕복이 같은 방언 식 기본값을 안 잰다(점검 A — 같이 확인 ②) |
| 한계 C2-04 감싸개 자체의 미해결 한 줄 | **재현 안 됨** | HEAD 에서 그 줄은 안 나온다 — DAO 훑기가 `quiet` 다(`JavaGraph:269`). 미해결은 부른 쪽에 적힌다 |

**보정 C — 가로 점검 4차 지적**: 부록 A(= `design/78-audit4.md`). 지적 78(Critical 2 · Important 17 · Minor 59) + 후보 18. 행의 「사실」 줄이 그 재현 꼴이다. 점검자가 「실행」 이라 적은 재현은 HEAD 빌드에 돌린 것이고, 내가 다시 읽은 것은 인용 줄의 코드다(다시 돌린 탐침은 DEFAULT 식 열일곱 꼴 · FK 규칙 · `0x…ADD` · `relOk`).

## 1. 목표와 범위

목표 — 가로 점검 4차의 지적 78 가운데 75 와 대기 행 여섯 · 확인된 후보·한계를 행 52 개 · PR 하나로 닫는다. 사용자가 겪는 변화(큰 것만):
- **절대 규칙** — DB 대조 xlsx 의 오류문에 SQL 리터럴 값이 남는 남은 길(역슬래시 따옴표·겹친 주석·`#`·`q'…'` 가 든 SQL)이 닫힌다(규칙 3) · 받은 소스의 `.git/config` 가 include 나 네트워크 경로를 가리키면 git 조회를 거절한다(규칙 1) · 읽기 전용 연결이 `1_0FOR SHARE` 꼴을 거절한다(규칙 6)
- **코드 정리(고친 사본이 틀리는 것)** — 끊긴 따옴표 줄 뒤에서 console 대신 **옆 줄**을 지우던 것 · `if (…)` 뒤 `debugger;` 를 지워 다음 문장이 if 의 몸이 되던 것 · default 를 옮겨 「이미 정의된 변수」 가 되던 것 · 필드로 가려진 이름을 상수로 보고 `equals` 를 뒤집던 것이 건너뛴다
- **코드 검사** — `<script type="text/template">`·자기 닫는 `<script/>`·주석 안 `<script>` 뒤의 HTML 주석 줄을 다시 안 잡는다 · 감싼 script 안 카드번호가 나온다 · 줄끝이 섞인 파일이 지적되고 정리로 통일된다 · 규칙 정의 오류 넷이 500 대신 400 + 사유 · 메서드만 붙여 넣은 자바를 자바로 알아본다
- **프로그램 분석** — 공통 DAO 를 서비스가 한 번 더 감싼 프로젝트의 둘째 문장부터가 CRUD 에 나온다 · 인터페이스·추상 조상에 붙은 `@RequestMapping` 접두가 URL 에 붙는다 · `UPDATE a … FROM T1 a`(MSSQL)의 관계가 별칭 글자가 아니라 표 이름으로 · NOLOCK·ON·USING 뒤 쉼표 조인의 관계가 나온다 · 프로젝트 밖 클래스를 import 한 필드가 이름만 같은 프로젝트 클래스로 안 풀린다
- **DDL·생성기** — 반영 DDL 이 이름만 다른 같은 제약을 DROP + ADD 하지 않는다(유니크가 사라지던 길) · 대소문자 섞인 스키마의 새 표가 다른 스키마에 생기지 않는다 · 스냅샷과 다른 방언으로 INSERT 를 뽑아도 평범한 이름을 안 감싼다(ORA-00942) · 자동증가 부모를 가리키는 FK 자식 컬럼이 같은 정수 타입으로 나간다 · `ALTER … IF NOT EXISTS [c` 가 500 이 아니다
- **빠르기** — 폴더 검사·정리·분석이 파일마다 20ms 씩 더 들던 것(5,000 파일에 약 110초)이 없어진다 · 긴 16진 리터럴·겹괄호·`_blank` 4만 줄·긴 한 줄 붙여넣기의 제곱 시간
- **CLI·프로필** — `--docs` 로 문서를 만들고도 끝 코드 1 이던 것 · 이모지·BMP 밖 글자가 든 프로필의 코드 검사 저장이 늘 400 이던 것

In scope — 행 52 개(4절 S0 표): ⓪ 먼저 넷 · ① 코드 검사 일곱 · ② 코드 정리 일곱 · ④ 분석 Java·VCS 일곱 · ③ 분석 SQL 다섯 · ⑤ DDL·생성기 열하나 · ⑥ 산출물·메타·DB 경계 여섯 · ⑦ 파일·프로필·CLI 다섯 + `S0`·`V-71a`·`R-38`·`V-71`

Out of scope
- 뺀 것(사용자 결정 4) · 부록 A 4절(행으로 안 세운 것 — 잠재 A-33·A-37 · 한계 A1-02 · `Job.subscribe` · `removeSpaces` · 후보 열넷 · C1-12·G4-04·G4-07·E4-10·E4-11) · 행마다 「한계」 줄에 적은 것
- 새 라우트·새 화면·새 규칙·새 마이그레이션·새 의존성 0. 새 파일은 `core/gen/DdlDefault.java` · `core/vcs/GitConfigGuard.java` 둘(`5-146b` 사다리의 `ScriptTags` 는 메서드 수가 넘을 때만). 응답 JSON 에 더하는 칸은 `made` 하나(`8-25`)
- 순수본(`pure/`)·`rules.yaml`·`pure-rules.json` — 안 고친다

## 2. 현재 구조 요약

행마다 「사실」 줄이 지금 자리(줄 번호 `fccf261`)와 재현 꼴이다. 여러 행에 걸치는 것만 여기 둔다.

**self-check 기준선**(`src/test/resources/golden/check/self-check.txt` 121줄 — `SelfCheckTest.mainMatchesBaseline` 이 글자까지 맞춘다. 이름표는 `메서드(인자 수)`). 이 번들이 건드리는 파일의 줄 —
- 분석: `JavaGraph` 메서드 34·`walk(5)` 42·중첩 5·`run(2)` 31·`index(4)` 21·`entityManager(4)` 19·`querydsl(3)` 22·`views(3)` 19·`repoCall(8)` · `JavaViews.value(4)` 18 · `JpaIndex` 메서드 38·`entity(1)` 18·`jpql(6)` 39 · `MapperIndex.startElement(4)` 18·`Statement(8)` · `SqlProbe.run(4)` 17 · `SqlTables.extract(2)` 17 · `AnalyzeRoutes.postRunsByIdExport(5)` 20·길이 106. **줄 없음**: `SqlJoins` · `StmtArgs` · `TypeBind` · `JspLinks` · `JpqlPaths` · `WorkingCopy` · `LogSql`
- 검사·정리: `RegexRule.cLike(2)` 25 · `Rule.Def(11)` · `RuleSet.load(2)` 26 · `MyBatisRules.startElement(4)` 17 · `FileRule.apply(1)` 17 · `JavaRules.apply(1)` 19·`type(2)` 26 · `Cleanup.deleteConsole(4)` 19·`worse(6)` 36·`consoleEnd(3)` 17·`rebuildClause(2)` 16·`fixTsImports(4)` 17 · `CleanupRunner.apply(10)`·`preview(8)`·`writeReport(8)` · `MarkerFix.markerEdit(8)` 34 · `CheckRoutes.postRun(6)` 44. **줄 없음**: `CheckRunner` · `Zones` · `DeclaredTypes` · `ValueAst` · `SwitchFix` · `LogFix` · `ConsoleArgs` · `MarkupFix` · `PiiFix` · `BlankRelRule` · `LuhnRule` · `AbsentRule`
- 생성: `AlterGen.Run` 메서드 40·`modifyColumn(7)` 38·`keyBreakingFks(1)` 18·`uniquesAndIndexes(4)` 19 · `DdlGen.table(1)` 16·`type(2)` 16 · `DdlReader.item(3)` 29·`read(1)` 30·중첩 6 · `InsertGen.generate(4)` 20·`buildUpsert(5)` 24·`csvLiteral(5)` 31·`genVal(4)` 34·`Col(11)` · `InsertRoutes.register(3)` 23. **줄 없음**: `DdlAlter` · `DdlPull` · `DdlReaderParts` · `InsertGenParts` · `Validation.parse(1)` 은 28(줄 있음)
- 산출물·웹·파일: `CodeAndLink.codeCandidates(1)` 29 · `Definitions` 메서드 34·`Options(8)`·`indexRows(8)` · `DeliverableService.build(8)` 22·`writeGuide(11)` · `XlsxFiller.fill(4)` 23·중첩 5 · `LocalFiles.list(4)` 21·`resultRoot(5)` 22 · `ProfileStore.saveCodeCheck(3)` 22 · `SelfTest.body(0)` 19 · `LogicalRoutes.register(4)` 19·길이 104·중첩 5. **줄 없음**: `ReadOnlyJdbc` · `PathRules` · `ProfileRoutes` · `Masking` · `RowCounter` · `SnapshotDiff` · `XlsxWriter` · `FormulaRange` · `Batch` · `DocCommands` · `Profile`
- 줄 없는 메서드의 상한 — 복잡도 15 · 길이 100 · 중첩 4 · 인자 7 · 클래스 메서드 30

**함정**
- 줄 있는 메서드에 분기(`if`·`&&`·`||`·`?:`·`for`·`catch`, 람다 안 것도)나 줄을 더하면 빨강이다 — 행은 그 자리의 **식을 도우미 호출로 바꾸는** 꼴로 적었다. 도우미는 줄 없는 클래스·중첩 클래스·`private static` 에 둔다
- 메서드 수 기준선 클래스(`JavaGraph`·`JpaIndex`·`AlterGen.Run`·`Definitions`)에는 메서드를 못 더한다 — `TypeBind`·`StmtArgs`·`JpqlPaths`·바깥 `AlterGen` static·`JavaViews` 로
- 인자 수가 이름표다 — 줄 있는 메서드에 인자를 더하면 「사라진 줄 + 새 줄」 이다
- 골든 JSON(`sql-joins.json`·`sql-tables.json`)은 `GoldenFiles.assertJson` 이 열쇠를 정렬해 쓴다 — 새 열쇠는 글자 차례 자리에 든다. 갱신은 `-Dgolden.update=true`
- `MapperIndexTest`·`JpaIndexTest` 의 골든 시험은 픽스처 폴더 전체를 본다 — 폴더에 파일을 안 더하고 인라인 `Source` 로
- `SqlTables.BRANCH`(`\u0001`)는 낱말 하나다 — 시험의 `B` 는 `String.valueOf(SqlTables.BRANCH)`
- SpotBugs REDOS 는 「수량자 단 묶음 안 수량자」 를 소유 수량자여도 막는다 — 묶음 반복(`(?:…+)*`)을 새로 쓰지 않는다. `(?:…)?` 는 지난다
- PMD `AvoidFieldNameMatchingMethodName`(대소문자 무시)·`AvoidReassigningLoopVariables`(`for` 변수를 몸에서 안 바꾼다 — `while`) · ErrorProne 은 raw 형식·`Error` 잡기에 민감하다
- Bash heredoc 은 백슬래시를 벗긴다 — 정규식·역슬래시가 든 편집은 Edit·Write 로
- jshell 표준입력 탐침은 `\\` 를 `\` 로 접는다 — 탐침에서 `\r`·`'` 는 `(char) 13`·`(char) 39` 로
- CI 는 ubuntu 다 — Windows 전용 경로 입력(드라이브 글자·대소문자)을 쓰는 시험은 `assumeTrue` 로 OS 를 가린다(`0-131`·`5-148` ③)
- `RegexRule.bare`·`stripComments`·`scriptWrappers` 는 **길이와 줄 수를 지킨다**(불변식) — 이 번들의 `5-146a` 가 그 불변식을 시험으로 못 박는다. 뒤 행이 그 글을 다룰 때 같은 길이 빈칸으로만 바꾼다
- `Zones.scriptLines` 는 `RegexRule.scriptBodies` 를 쓴다 — `5-146b` 가 몸 판정을 고치면 구역(코드 정리의 「script 밖 글」)도 같이 바뀐다
- 행의 시험 클래스는 저장소에 있는 이름이다(`CheckRulesTest`·`CleanupTest`·`ZonesTest`·`JavaGraphTest`·`JspLinksTest`·`JpaIndexTest`·`SqlJoinsTest`·`SqlTablesTest`·`SqlProbeTest`·`MapperIndexTest`·`LogSqlTest`·`DdlReaderTest`·`DdlGenTest`·`AlterGenTest`·`InsertGenTest`·`SnapshotDiffTest`·`SnapshotServiceTest`·`ReadOnlyJdbcTest`·`LocalFilesTest`·`ProfileRoutesTest`·`ProfileStoreTest`·`WorkingCopyTest`·`BatchCliTest`·`DocCliTest`·`MaskingTest`·`LogicalRoutesTest`·`XlsxFillerTest`·`XlsxWriterTest`·`DeliverableServiceTest`·`CodeAndLinkTest`·`DefinitionsTest`). `-Dtest` 목록에 없는 이름이 섞여 있으면 빼고 돈다

## 3. 설계 결정 — 전부 [고정]

1. **기준선은 그대로** — 값이 오르거나 새 줄이 생기는 고침은 안 한다. 낮추는 갱신을 노린 행도 없다(내려가면 공통 사다리대로 갱신하고 이력에). 버린 대안 — 기준선을 통째로 다시 뜨기: 오른 값이 묻힌다
2. **잠재 A-33·A-37 은 잠재로 둔다** — `DdlReader` 결과가 `DdlGen`·`AlterGen` 으로 가는 화면 길이 없다(맥락 보정 B). 버린 대안 — 미리 고쳐 두기: 쓰는 길이 없는 코드에 골든·`--db` 위험만 더한다
3. **읽기 전용 래퍼는 엄격한 쪽 그대로** — `0x…ADD` 로 끝나는 16진 리터럴의 거절은 「의도된 오거절」 로 시험에 못 박는다(`1-129`). 숫자 뒤 토막은 `_` 머리도 모든 꼬리를 잰다 · 긴 토막은 금지 낱말 길이만큼만 잰다(`9-11`)
4. **git 저장소 설정은 자바가 먼저 읽는다**(`0-128`) — include·includeIf 구역은 통째 거절, 경로를 여는 키의 네트워크 꼴 값은 거절. 판정은 git 을 띄우지 않고 설정 글에서 끝난다. 원격 주소(`remote.*.url`)는 안 본다. 버린 대안 — `git config --no-includes` 로 읽기: git 을 띄우는 순간 무엇을 여는지 못 믿는다 / include 대상 파일을 따라가 읽기: 대상이 또 무엇을 가리킬지 끝이 없다
5. **파일용 오류문은 닫힌 실패**(`6-98`) — 리터럴·주석 경계가 방언마다 갈리는 글은 따옴표 토막을 전부 가린다. 표 추출용 `SqlTokens` 의 어림은 안 바꾼다(겹친 블록 주석 C1-12 포함 — 방언마다 갈려 한쪽으로 못 정한다)
6. **`<script>` 몸** — 자기 닫는 태그·`type` 이 JS 가 아닌 태그·주석과 스크립틀릿 안의 여는 태그는 몸을 안 연다(`5-146b`). 브라우저가 `<script src/>` 를 닫는 태그까지 여는 태그로 읽는 것은 따르지 않는다(번들 91 전 동작)
7. **끊긴 따옴표** — 끊긴 그 줄 안에서만, 문자열 밖 첫 `//` 부터 비운다(`5-146a`). `cLike` 는 안 고친다(기준선 25). 버린 대안 — 따옴표 다음 자리로 되감아 다시 훑기: `cLike` 에 분기가 든다
8. **공통 DAO 방문 열쇠**(`6-93b`) — callee 의 `String` 매개변수 자리마다, 꼭대기 층 인자가 닿는 문장 id 리터럴. 못 닿으면 그 매개변수를 문장 실행 호출에 넘기는 감싸개일 때만 출처 자리. 매개변수가 메서드 안에서 대입 대상이면 거슬러 읽기를 멈춘다. `JavaGraph` 는 안 고친다
9. **타입은 명시 import 가 답**(`6-93a`) — 명시 import 가 있으면 그 완전 이름(중첩 포함)과 같은 후보만. 별 import·같은 패키지는 지금처럼 받는다. JPA 길은 후보가 하나일 때만 같은 판정(`6-96`)
10. **타입 수준 매핑 머리는 타입 계층에서**(`6-95`) — c, 조상 사다리, 구현 인터페이스 차례
11. **SqlJoins** — 같은 범위에서는 자기 정의(표 = 이름)보다 별칭 정의가 이긴다(`6-99`) · FROM 이 연 목록 안 같은 깊이 쉼표는 표 자리(`6-100`). 번들 91 의 [고정](분기 표시는 범위를 안 가른다 · 파생 표 별칭은 표가 아니다)은 그대로
12. **반영 DDL 은 이름이 달라도 구조가 같으면 같은 제약**(`1-132`) — 비교(`SnapshotDiff`)와 같은 짝짓기. 이름만 바꾸는 문장은 안 낸다
13. **INSERT 의 정확 이름은 원본 방언으로 잰다**(`4-52`) — `DdlGen` 과 한 식(`DdlGen.exactIn`). upsert·DDL 모드는 그대로
14. **FK 자식 컬럼은 부모 키를 따라간다**(`1-134`) — 자동증가 정수 · 길이
15. **debugger 삭제도 「앞 줄이 문장 끝」 가드**(`5-153`) — `deleteConsole` 은 기준선이라 식을 그대로 두고 같은 판정을 도우미로 한 벌 더 둔다(세미콜론 없는 js 에서 건너뜀이 는다 — 안전한 쪽)
16. **로거 앞 초기화** — `getClass()` 만 뺀다. 람다·익명 클래스 몸의 호출은 지금처럼 센다(바로 부르는 람다가 있다). 메서드 참조·같은 파일 형식의 static 필드 접근·`new 같은 파일 형식` 은 「먼저 돈다」 로 더 센다(`5-147`)
17. **줄끝 섞임은 `Source` 가 들고 다닌다**(`5-157a`·`b`) — 다섯 인자 생성자는 남긴다(섞임 거짓)
18. **JSX 글 줄** — `<pre>` 날글 구역 + 「다음 줄이 `</` 로 시작」 둘만(`5-158`). 가운데 줄은 한계
19. **경로 검사의 기억은 10초**(`0-129`) — 폴더(뿌리 포함)를 기억하고 통째로 버린다. 판정 차례·글은 그대로
20. **배치 끝 코드는 응답의 `made`**(`8-25`) — 파일 이름을 안 본다
21. **한계로 둔다** — 같은 방언 식 기본값(A1-02 뿌리) · `Job.subscribe` 의 락 · `Js.removeSpaces` · 근거는 맥락 보정 B
22. 행 차례 — Critical·규칙 위반 넷(`6-98`·`0-128`·`5-153`·`5-146a`) 먼저, 그 뒤 도우미를 주는 행이 받는 행보다 앞, 같은 파일 행은 이어서

## 4. 실행 스텝

행마다 끝은 CLAUDE.md 「Task 끝」 순서(분할표 행·이력 → `bash scripts/verify.sh` → `git commit` 따로 → `task-done`). `src/main/java` 를 고치는 행은 시험을 먼저 쓰고 RED 를 본 뒤 고친다. 커밋 제목 `fix: <번호> <무엇>`(문서만이면 `docs:`). 줄 번호는 `fccf261` 기준 — 앞 행이 밀면 메서드 이름으로 찾는다. 행의 「변경」 이 방향만 적은 자리(「실행자가 그 가지를 읽고」)는 시험의 전 → 후와 지킴이가 답이다 — 가장 작은 식으로 고친다.

공통 사다리
- `SelfCheckTest.mainMatchesBaseline` 빨강 —
  - **오른 값·새 줄**: 분기를 도우미로 뺀다(자리는 2절 함정). 그래도 오르면 그 조각을 빼고(행의 나머지는 친다) 이력 「정한 것(계획 밖)」 + 새 행
  - **내린 값·사라진 줄**: 그 행이 고친 메서드의 줄이면 `-Dtest=SelfCheckTest -Dgolden.update=true` 로 갱신하고 diff 가 그 줄뿐인지 본다(이력에 한 줄). 그 밖이면 고침이 넓다 — 플랜과 댄다
- `golden/` diff — 행의 「의도된 골든 변화」 에 적힌 것만 갱신(`bash scripts/mvn.sh -q test -Dtest=<시험> -Dgolden.update=true`). 그 밖은 고침이 넓다 → 고친다
- **RED 가 「전」 과 다를 때** — 이미 기대대로 나오는 사례는 지킴이로 두고 이력에 한 줄(그 조각은 고칠 것이 없다). 기대도 「전」 도 아닌 출력이면 7절
- 기존 단언이 빨개질 때 — 그 단언이 이 행이 고치는 결함을 못 박은 것이면(행의 「사실」 과 같은 꼴) 기대를 바꾸고 이력에 적는다. 아니면 고침이 넓다
- 시험 글에 실제 사업 이름·SQL·값을 안 쓴다(`T1`~`T5`·`T92_*`·`pw-probe-1`). 인라인 `Source`·임시 폴더로(픽스처 폴더에 안 더한다)
- 시간 단언은 `assertTimeout`(선형이면 여유 있게 지나는 값 — 행에 적힌 초) — 느린 PC·CI 에서 빨개지면 입력을 반으로 줄이고 이력에
- PMD·SpotBugs·ErrorProne 은 빠른 `verify.sh` 가 안 본다 — `V-71a` 가 본다. 새 짝은 코드로 고친다(기준선에 안 더한다)
- 행의 「대상」 밖 파일을 고쳐야 하면 둘까지(이력에 적는다), 셋부터 7절
- 한 행에 조각이 여럿이면 조각마다 RED → GREEN. 한 조각이 사다리로 빠져도 나머지는 친다(이력에 빠진 조각과 사유 · 새 행)

### S0 — 설계 문서 · 행 · 번들 표 (실행 세션이 했다)
1. 가지 `work/2026-11-23` 그대로(HEAD = main `fccf261` + 번들 91 닫기 `b46906a` — 같은 PR 에 실린다). 새 가지 안 딴다
2. **첫 손**(커밋 없음): `git config --local --unset remote.zzprobe.partialclonefilter`(옛 탐침이 남긴 키 — `0-126` 을 고치면 이 저장소의 git 조회가 거절된다. 없으면 건너뛴다) · 메모리(「설계 세션이 못 친 것」 절)
3. **`design/78-audit4.md` 가 없으면 먼저** — 이 플랜의 「부록 A」 를 그 파일로 옮기고(머리 `# 설계 78 — 가로 점검 4차 결과(2026-11-24)`, 소제목 한 단 올림, 부록 머리의 안내 문단은 뺀다) `PROGRESS.md` 「번들 91 닫기」 줄의 `(이 커밋)` → `b46906a` · 이력 한 줄(「가로 점검 4차 — 설계 78: 지적 78(Critical 2 · Important 17 · Minor 59) + 후보 18, 영역 여섯」, 해시 `(이 커밋)`) → `bash scripts/verify.sh` → `git commit`(따로) `docs: 설계 78 — 가로 점검 4차`. 있으면 건너뛴다
4. 이 플랜 전체를 `design/79-bundle92.md` 로(글자 그대로 — 이 S0 머리만 「실행 세션이 했다」 로, 「부록 A」·「설계 세션이 못 친 것」·「Opus 번들 해 시작 지시문」 절은 빼고 부록 자리에 「부록 A 는 `design/78-audit4.md`」 한 줄)
5. `PROGRESS.md` — 설계 78 줄의 `(이 커밋)` → 그 커밋 해시
6. `PLAN.md` 15장 — 「번들 92~」 행을 둘로:
   - **「번들 92」** — 순서 칸은 아래 「차례」 · 설계 칸 「2026-11-24(Fable, 설계 78 `design/78-audit4.md` — 가로 점검 4차 · 설계 79 `design/79-bundle92.md`. 실행 Opus high · 마무리 독립 리뷰 Fable xhigh). **점검 4차 지적 78(Critical 2 · Important 17) + 대기 행 여섯 + 후보·한계 — 행 52.** 사용자 결정(2026-11-23): 범위 (나) · **머지 위임** · 무거운 검증은 묻지 않고 · 시간 컷 없음」 · 설치 칸 「표본 폴더 · node · Docker · 여유 RAM 15GB」 · 상태 「대기」
   - **「번들 93~」** — 순서 칸 「(설계 전)」 · 요지 「설계 78 4절(행으로 안 세운 것 — 후보 열넷 · C1-12 · G4-04·G4-07 · E4-10·E4-11) · 잠재 A-33·A-37(닿는 길이 생길 때) · `5-100`·`5-31`·`5-134`(순수본 재주입 — 한 번에) · 반입 zip 갱신(다음 반입 날 앞) · 조건부 `5-14`·`5-15`·`6-9` · 번들 92 가 `wip/` 로 뺀 행」 · 상태 「설계 전」
7. `PLAN.md` 16장 —
   - 대기 넷(`0-126`·`3-27`·`5-147`·`1-129`)의 사실 칸 끝에 ` · **처방**: 설계 79 §4`
   - `5-146` 행 — 사실 칸 끝에 ` · **처방**: 설계 79 §4 — `5-146a`·`5-146b`·`5-146c` 로 나눴다`, 상태 「나눔」 · `6-93` 행 — 같은 꼴(`6-93a`·`6-93b`), 상태 「나눔」
   - 표 끝에 새 행 — 아래 S0 표에서 대기 넷을 뺀 행마다(꼴 `| <번호> | <청크> | **설계 79 §4 가 원본**. <근거>(설계 78). **축**: <축> **강제 지점**: <강제 지점> | <선행> | 대기 |`)
8. `PROGRESS.md` 이력 한 줄(S0 설계 79 — 행 52 + `V-71a`·`R-38`·`V-71`, 묶음 일곱과 차례) + 「현재 상태」(진행중 = 번들 92 대기)
9. `bash scripts/verify.sh`(docs 레인) → `git commit`(따로) `docs: 설계 79 — 번들 92 행·번들 표`
- 확인: `git log --oneline -2` 가 두 문서 커밋 · `git status --short` 깨끗 · 8절 「첫 스텝 전에」

| # | 청크 | 근거 | 축(레인) | 강제 지점 | 선행 |
|---|---|---|---|---|---|
| 6-98 | SqlProbe 파일용 오류문 — 닫힌 실패 · 머리 주석 · 끝 `;` | C1-01(Critical)·09·05 | 규칙 3(db) | `SqlProbeTest.fileMessageFailsClosedOnAmbiguousQuoting` | S0 |
| 0-128 | VCS — 저장소 설정의 include · 네트워크 경로 | C2-H1 | 규칙 1(java) | `WorkingCopyTest.gitConfigGuardRefusesIncludesAndRemotePaths` | S0 |
| 5-153 | debugger 삭제 — 앞 줄 문장 끝 가드 | E4-01(Critical) | 정리(corpus) | `CleanupTest.debuggerNeedsAStatementEndBefore` | S0 |
| 5-146a | 끊긴 따옴표 줄 자르기 — 줄 수 · URL 안 `//` | 대기 5-146 ② + D4-01 | 검사(corpus) | `CheckRulesTest.brokenQuoteKeepsLineCountAndCode` | S0 |
| 5-146b | `<script>` 몸 판정 — 비 JS type · 자기 닫는 태그 · 주석 안 여는 태그 | 대기 5-146 ① + D-1c·C2-13·D4-09·E4-05·06 | 〃 | `CheckRulesTest.scriptBodiesSkipNonJsAndCommentedTags` · `ZonesTest` · `JspLinksTest` · `CleanupTest` | 5-146a |
| 5-146c | 감싼 script 안 Luhn·Absent·BlankRel · `lineOf` 시간 | 대기 5-146 ③ + 후보 | 〃 | `CheckRulesTest` · `BlankRelRuleTest.manyFindingsAreLinear` | 5-146b |
| 5-150 | jsp 잔손 — 주석뿐인 스크립틀릿 차례 · 날글 구역 | D4-03·08 | 〃 | `CheckRulesTest` · `ZonesTest` | 5-146c |
| 5-148 | 규칙 적재 — 남은 BadRule · params 키 | D4-02·10 | 〃 | `CheckRulesTest.ruleDefinitionErrorsAreBadRule` · `CheckRoutesTest`(400) | S0 |
| 5-149 | 붙여넣기 언어 추정 — 시간 · record·enum · 메서드만 붙인 자바 | D4-04·05 + 한계 D-11 | 〃 | `CheckRunnerTest` | S0 |
| 5-151 | `mybatis.noWhere` — 괄호 안 맞는 동적 SQL | D4-06 | 〃 | `MyBatisRulesTest` | S0 |
| 5-152 | DeclaredTypes — 다른 범위의 같은 이름 패턴 변수 | D4-07 | 〃 | `JavaRulesTest` | S0 |
| 5-155 | ValueAst — 필드로 가려진 상수 · 초기화 블록 지역 | E4-03 | 정리(corpus) | `ValueAstTest` · `CleanupTest` | 5-152 |
| 5-154 | SwitchFix — default 가 선언한 이름 | E4-02 | 〃 | `CleanupTest`(`P3`) | S0 |
| 5-147 | 정리 잔손 — `cb!!(x)` · `getClass()` · 먼저 도는 초기화 셋 · 태그드 템플릿 | 대기 행 + E4-04·09 | 〃 | `CleanupTest` | S0 |
| 5-156 | MarkupFix 속성 값 안 `>` · PiiFix 홀수 따옴표 | E4-07·08 | 〃 | `CleanupTest` | S0 |
| 5-158 | JSX 글 줄 — `<pre>` 날글 · 다음 줄 `</` | 한계 E-13 | 〃 | `CleanupTest` · `ZonesTest` | 5-153 · 5-150 |
| 5-157a | 줄끝 섞인 파일 — 검사 지적 | 한계 E-15 | 검사(corpus) | `CheckRulesTest` · `CheckRunnerTest` | S0 |
| 5-157b | 줄끝 섞인 파일 — 정리 통일 | 한계 E-15 | 정리(corpus) | `CleanupRunnerTest` | 5-157a |
| 6-93a | 타입 풀기 — 명시 import · 완전 이름 필드 | 대기 6-93 ① + C2-09·10 | 분석(corpus) | `JavaGraphTest.explicitImportDecidesTheType` | S0 |
| 6-93b | 공통 DAO 방문 열쇠 | 대기 6-93 ② + C2-01·05·06·07 | 〃 | `JavaGraphTest.statementIdsThroughLayersAndWrappers` | 6-93a |
| 6-94 | `super.` 로 넘기는 공통 DAO | C2-04 | 〃 | `JavaGraphTest.daoOverridesFrameworkSinkWithSuper` | 6-93b |
| 6-95 | 타입 수준 매핑이 인터페이스·조상에 | C2-02 | 〃 | `JavaGraphTest.typeLevelMappingOnInterfaceOrParent` | 6-94 |
| 6-97 | `c:set` 컨텍스트 값 · 빈 redirect 뷰 | C2-11·12 | 〃 | `JspLinksTest` · `JavaGraphTest` | 6-95 |
| 6-96 | JPA — 순환 상속 · 후보 하나의 import | C2-14·08 | 〃 | `JpaIndexTest.cyclicInheritanceEnds` | 6-93a |
| 0-126 | VCS·기동 잔손 — 부분 클론 키 · 드라이브 판정 한 줄 | 대기 행 + C2-03 | 규칙 1(java) | `WorkingCopyTest` · `BatchCliTest.driveCheckFailureIsOneLine` | 0-128 |
| 6-99 | SqlJoins — 자기 정의 · 따옴표 별칭 · MERGE · 열 이름 APPLY | C1-02·06·13·14 | 분석(corpus) | `SqlJoinsTest.shapes` · `SqlTablesTest.CASES` | S0 |
| 6-100 | SqlJoins — 목록 안 쉼표 · 겹괄호 시간 | C1-11·03 | 〃 | `SqlJoinsTest.shapes` · `nestedParensAreLinear` | 6-99 |
| 6-101 | SqlTables — 분기 뒤 괄호 · USING · 닫힌 뒤 분기 | C1-07·08·15 | 〃 | `SqlTablesTest.CASES` | 6-99 |
| 6-102 | iBATIS 쉼표 꼴 파라미터 | C1-10 | 분석(db) | `MapperIndexTest` · `SqlProbeTest` | S0 |
| 4-51 | 로그 SQL — 짝 없는 Parameters 복제 | C1-04 | 글(corpus) | `LogSqlTest` | S0 |
| 1-129 | DDL 읽기 — 마디 넷 COMMENT · `0x…ADD` 처분 | 대기 행 | DDL(db) | `DdlReaderTest.commentOnFourPartName` · `ReadOnlyJdbcTest.intendedRefusals` | S0 |
| 1-130 | DDL 읽기 — DEFAULT 식 끝 | 후보 | 〃 | `DdlReaderTest.defaultExpressionsRunToTheEnd` | 1-129 |
| 1-137 | ALTER 읽기 잔손 — NPE · ONLINE·IGNORE · 공백 든 제약 이름 | A4-08·12·14 | 〃 | `DdlReaderTest` | 1-130 |
| 1-138 | DDL 읽기 잔손 — `db..표` · 표 안 인덱스 | A4-09·13 | 〃 | `DdlReaderTest` | 1-137 |
| 1-131 | 반영 DDL — 새 표의 스키마 인용 | A4-01 | 생성기(db) | `AlterGenTest.newTableKeepsTheExactSchema` | S0 |
| 1-132 | 반영 DDL — 이름이 달라도 구조가 같으면 | A4-03·04 | 〃 | `AlterGenTest.sameShapeDifferentNameIsNotAChange` | 1-131 |
| 1-134 | DDL 생성 — FK 자식이 부모 키를 따라간다 | A4-05·17 | 〃 | `DdlGenTest.fkChildFollowsTheParentKey` | 1-132 |
| 1-135 | 반영 DDL 잔손 — identity 글 · `NULL` 기본값 | A4-06·11 | 〃 | `AlterGenTest` | 1-134 |
| 1-136 | 스냅샷 비교 — 인덱스 대소문자 | A4-07 | 메타(db) | `SnapshotDiffTest` | S0 |
| 4-52 | INSERT 정확 이름 — 원본 방언 | A4-02 | 생성기(db) | `InsertGenTest` · `InsertRoutesTest` | 1-134 |
| 4-53 | INSERT 값 잔손 | A4-10·15·16·18 | 〃 | `InsertGenTest` | 4-52 |
| 9-11 | ReadOnlyJdbc — `_` 머리 토막 · 긴 토막 시간 | F4-01·02 | 규칙 6(db) | `ReadOnlyJdbcTest.bypassesFromAudit4` | 1-129 |
| 3-27 | 마스킹 `[알림]` 줄바꿈 | 대기 행 | 생성(db) | `MaskingTest.noticesStayOnOneCommentLine` · `LogicalRoutesTest` | S0 |
| 1-133 | 행 수 경고 분류 | 후보 | 메타(db) | `SnapshotServiceTest.driverUnsupportedIsNotRefused` | S0 |
| 2-57 | 08 SELECT 예약어 접기 · 03 여러 낱말 타입 | B4-01·02 | 산출물(db) | `CodeAndLinkTest` · `DefinitionsTest` | S0 |
| 2-58 | 머리 칸 상한 · 같은 파일 경로 표기 | B4-03·04 | 산출물(java) | `XlsxWriterTest` · `DeliverableServiceTest` | 2-57 |
| 2-59 | 양식 다시 계산 표시 | B4-C1 | 〃 | `XlsxFillerTest` | 2-58 |
| 0-127 | 결과 쓰기 — 예약 장치 이름 | 후보 | 규칙 7(java) | `LocalFilesTest.relRefusesDeviceNames` | S0 |
| 0-129 | 경로 검사 — 파일마다 20ms | G4-01 | 자원(java) | `LocalFilesTest.checkIsCheapAfterTheFirst` | 0-127 |
| 0-131 | 드라이브 판정 잔손 — 프로필 사유 · 없는 드라이브 | G4-05·06 | 설정(java) | `LocalFilesTest` · `ProfileStoreTest` | 0-129 |
| 0-130 | 프로필 백업 가림 — BMP 밖 글자 | G4-02 | 규칙 2(java) | `ProfileRoutesTest` | S0 |
| 8-25 | 배치 `--docs` 끝 코드 — `made` | G4-03 | CLI(java) | `DocCliTest` · `DeliverableServiceTest` | 2-58 |
| V-71a | 정적 분석 · corpus 골든 | — | 4장 | `verify.sh --corpus` | 코드 행 전부 |
| R-38 | 손 판정 · 처분 표 | — | 4장 | compare ㊶ 절 | V-71a |
| V-71 | `--db` | — | 4장 | `verify.sh --db` | R-38 |

차례 — `S0 → ⓪ 6-98→0-128→5-153→5-146a → ① 5-146b→5-146c→5-150→5-148→5-149→5-151→5-152 → ② 5-155→5-154→5-147→5-156→5-158→5-157a→5-157b → ④ 6-93a→6-93b→6-94→6-95→6-97→6-96→0-126 → ③ 6-99→6-100→6-101→6-102→4-51 → ⑤ 1-129→1-130→1-137→1-138→1-131→1-132→1-134→1-135→1-136→4-52→4-53 → ⑥ 9-11→3-27→1-133→2-57→2-58→2-59 → ⑦ 0-127→0-129→0-131→0-130→8-25 → V-71a→R-38→V-71→마무리`

행 글은 아래 묶음 절에 있다(절 번호는 영역, 치는 차례는 위 줄). ⓪ 의 넷은 제 영역 절에 있다 — `6-98` 은 ③, `0-128` 은 ④, `5-153` 은 ②, `5-146a` 는 ①. `0-126` 은 묶음 ⑦(기동 쪽)과 묶음 ④(「0-126 ①」 부분 클론 쪽) 두 자리에 나눠 적혀 있다 — 한 행·한 커밋이다. `9-11`·`2-57`~`2-59` 는 ⑥, `8-25`·`0-129`~`0-131` 은 ⑦.


### 묶음 ⑤ DDL 읽기·생성 (db 레인 — `V-71` 이 컨테이너로 잰다)

#### 1-129 — DDL 읽기: 마디 넷 이름의 COMMENT ON COLUMN · 16진 리터럴 `…ADD` 처분 (대기 행 — 번들 91 리뷰 M15·M13)
- 대상: `core/gen/DdlReaderParts.java`(새 상수 `QNAME4`) · `core/gen/DdlReader.java`(`COMMENT_ON` 한 줄) · `DdlReaderTest` · `ReadOnlyJdbcTest`(`intendedRefusals` 에 두 꼴)
- 사실: `DdlReaderParts.QNAME:22` 는 마디 셋까지. `DdlReader.COMMENT_ON:81` 이 그것을 써 `COMMENT ON COLUMN db.s.t.c IS 'x'` 는 셋째 마디 뒤 `.c` 에서 `\s+IS` 가 안 맞아 코멘트가 빠진다(번들 91 `1-123` 전의 `\S+` 는 맞았다 — 회귀). `read:213` 의 처리(`nameParts` → 끝 마디가 컬럼, 앞이 표 — `DdlPull.find` 는 끝 두 마디만 쓴다)는 마디 넷을 이미 받는다 · `ReadOnlyJdbc.glued:182` 는 숫자 뒤 글자 토막의 모든 꼬리를 재서 `SELECT 0x1ADD FROM T` 를 「ADD 이 든 문장」 으로 거절한다
- 변경:
  1. `DdlReaderParts` — `QNAME` 아래에
     ```java
     /** 1-129 — COMMENT ON COLUMN db.스키마.표.컬럼: 마디 넷까지(선택 묶음 하나 더 — 묶음 반복 없이) */
     static final String QNAME4 = QNAME + "(?:\\.{1,2}" + PART + ")?";
     ```
  2. `DdlReader.COMMENT_ON` — `DdlReaderParts.QNAME` → `DdlReaderParts.QNAME4`. 그 밖(`COL_REF`·`FK`·`DdlAlter.HEAD`)은 `QNAME` 그대로
  3. `ReadOnlyJdbc` — **안 고친다**(3절 결정 — 엄격한 쪽 유지). `ReadOnlyJdbcTest.intendedRefusals` 의 목록에 `"SELECT 0x1ADD FROM T"` · `"SELECT 0xBADD FROM T"` 를 더하고 그 시험 javadoc 끝에 「 · 1-129 — 16진 리터럴이 `ADD` 로 끝나는 꼴(숫자에 붙은 꼬리 판정 9-10 의 대가)」
- 시험(먼저): `DdlReaderTest.commentOnFourPartName` — `DdlReader.read("CREATE TABLE s.t (c INT, d INT);\nCOMMENT ON COLUMN db.s.t.c IS 'x';\nCOMMENT ON COLUMN [db].[s].[t].[d] IS 'y';\nCOMMENT ON TABLE db.s.t IS 'z';")` → 표 하나, `c` 코멘트 `x`(전 null) · `d` 코멘트 `y`(전 null) · 표 코멘트 `z`(지킴이 — 지금도 맞다) · `regexesAreLinear` 의 입력 꼴에 `COMMENT ON COLUMN ` + `a.`×20000 + ` IS 'x'` 한 줄을 더해 지금 시간 상한 안
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,ReadOnlyJdbcTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 사다리: `intendedRefusals` 의 새 두 꼴이 이미 초록이면(RED 없음) 그대로 둔다 — 못 박는 시험이다(Ruling 한 줄)
- 의존: S0

#### 1-130 — DDL 읽기: DEFAULT 식이 첫 토막에서 끊긴다 (설계 76 §4 후보 → 확인)
- 대상: 새 파일 `core/gen/DdlDefault.java` · `core/gen/DdlReaderParts.java`(`defaultExpr` · `exprEnd` 삭제 · `group` 을 package-private 로) · `DdlReaderTest`
- 사실(탐침 2026-11-23, `DdlReader.read("CREATE TABLE t (a … DEFAULT <식>, b INT)")` 의 `a` 기본값): `DdlReaderParts.exprEnd:161` 이 글자·괄호로 시작하지 않는 식을 **첫 공백**에서 끊는다
  | DEFAULT 뒤 글 | 지금 | 기대 |
  |---|---|---|
  | `SYSDATE + 1` | `SYSDATE` | `SYSDATE + 1` |
  | `1 + 1 NOT NULL` | `1` | `1 + 1`(NOT NULL 유지) |
  | `'a' \|\| 'b'` | `'a'` | `'a' \|\| 'b'` |
  | `DATE '2000-01-01'` | `DATE` | `DATE '2000-01-01'` |
  | `TIMESTAMP '2000-01-01 00:00:00' NOT NULL` | `TIMESTAMP` | 형식 붙은 글자 통째 |
  | `NEXT VALUE FOR seq1` | `NEXT` | `NEXT VALUE FOR seq1` |
  | `- 1` | `-` | `- 1` |
  | `TRUNC(SYSDATE) + 7` | `TRUNC(SYSDATE)` | `TRUNC(SYSDATE) + 7` |
  | `(1) + 2` | `(1)` | `(1) + 2` |
  | `INTERVAL '1' DAY` | `INTERVAL` | `INTERVAL '1' DAY` |
  | `ON NULL SYSDATE` | `ON` | `SYSDATE` |
  - 닿는 길(점검 4차 A — 같이 확인 ③): `DdlReader.read` 결과는 DDL 모드의 생성기(`GenRoutes:87` → `DtoGenerator`)와 INSERT(`InsertRoutes:140` → `InsertGen`)로만 간다. `DdlGen`·`AlterGen` 은 스냅샷만 받는다 — 잘린 기본값이 변환 DDL 로 나가는 화면 길은 없다(등급 Minor). 실물 표본에서 닿는 줄은 셋(`sqlfluff-dialects` 의 `DEFAULT 1 + 2 * 3 + 4 NOT NULL` · `NEXT VALUE FOR` 둘)
  - 지금 맞는 것(지킴이): `SYSDATE NOT NULL` → `SYSDATE` · `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` → `CURRENT_TIMESTAMP` · `SYSDATE+1`(공백 없음) → `SYSDATE+1` · `N'x'`·`fn(…)`·`(…)`
- 변경:
  1. 새 `core/gen/DdlDefault.java`(메서드 열 — 전부 복잡도 15 아래):
     ```java
     package kr.ejg.toolbox.core.gen;

     import java.util.Locale;
     import java.util.Set;

     /**
      * DEFAULT 뒤 식의 범위(1-130) — 항 하나, 그 뒤 이항 연산자(+ - * / % ||)가 이으면 다음 항까지. 글은 글자 안을 비운 같은 길이 사본(masked).
      * 컬럼 속성 낱말(NOT NULL·ON UPDATE·COMMENT …)은 연산자가 아니라 거기서 끝난다
      */
     final class DdlDefault {
         /** 낱말 + 공백 + '…' 꼴의 형식 붙은 글자 */
         private static final Set<String> TYPED = Set.of("DATE", "TIME", "TIMESTAMP", "TIMESTAMPTZ", "INTERVAL");
         private static final Set<String> UNITS = Set.of("YEAR", "MONTH", "DAY", "HOUR", "MINUTE", "SECOND", "TO");
         private static final String INTERVAL = "INTERVAL";
         private static final String STOPS = ",('";

         private DdlDefault() {
         }

         /** from = DEFAULT 낱말 뒤 첫 글자. {시작, 끝} — 시작은 ON NULL(Oracle 12c)을 건너뛴 자리. 식이 없으면 시작 == 끝 */
         static int[] span(String s, int from) {
             int start = afterOnNull(s, from);
             int end = term(s, start);
             int next = nextTerm(s, end);
             while (next >= 0) {
                 int e = term(s, next);
                 if (e == next) {
                     break;
                 }
                 end = e;
                 next = nextTerm(s, end);
             }
             return new int[] {start, end};
         }

         /** end 뒤(공백 건너)가 이항 연산자면 그 뒤 항의 시작, 아니면 -1. -- 와 블록 주석 머리는 연산자가 아니다 */
         private static int nextTerm(String s, int end) {
             int k = skip(s, end);
             if (k >= s.length() || s.startsWith("--", k) || s.startsWith("/*", k)) {
                 return -1;
             }
             int after = s.startsWith("||", k) ? k + 2 : "+-*/%".indexOf(s.charAt(k)) >= 0 ? k + 1 : -1;
             int t = after < 0 ? s.length() : skip(s, after);
             return t < s.length() ? t : -1;
         }

         /** 항 하나의 끝 — 앞 부호를 건너뛴 맨 항. 항이 비면 from */
         private static int term(String s, int from) {
             int k = from < s.length() && "+-".indexOf(s.charAt(from)) >= 0 ? skip(s, from + 1) : from;
             int e = k < s.length() ? bare(s, k) : k;
             return e == k ? from : e;
         }

         /** 글자·괄호 묶음 · 낱말(바로 뒤 괄호·따옴표는 같이) · 형식 붙은 글자 · NEXT VALUE FOR 이름 */
         private static int bare(String s, int k) {
             if (s.charAt(k) == '\'' || s.charAt(k) == '(') {
                 return DdlReaderParts.group(s, k);
             }
             int e = wordEnd(s, k);
             if (e > k && e < s.length() && (s.charAt(e) == '(' || s.charAt(e) == '\'')) {
                 return DdlReaderParts.group(s, e);
             }
             String w = s.substring(k, e).toUpperCase(Locale.ROOT);
             if (TYPED.contains(w)) {
                 return typed(s, e, INTERVAL.equals(w));
             }
             return "NEXT".equals(w) ? nextValue(s, e) : e;
         }

         /** 낱말 뒤(공백 건너)가 '…' 면 그 묶음까지, INTERVAL 이면 뒤 단위 낱말(DAY · DAY TO SECOND · DAY(2))도. 아니면 낱말 끝 */
         private static int typed(String s, int wordEnd, boolean interval) {
             int q = skip(s, wordEnd);
             if (q >= s.length() || s.charAt(q) != '\'') {
                 return wordEnd;
             }
             int e = DdlReaderParts.group(s, q);
             int u = interval ? unitEnd(s, e) : -1;
             while (u >= 0) {
                 e = u;
                 u = unitEnd(s, e);
             }
             return e;
         }

         /** e 뒤(공백 건너)가 단위 낱말이면 그 끝(바로 뒤 (정밀도) 포함), 아니면 -1 */
         private static int unitEnd(String s, int e) {
             int u = skip(s, e);
             int ue = wordEnd(s, u);
             if (ue == u || !UNITS.contains(s.substring(u, ue).toUpperCase(Locale.ROOT))) {
                 return -1;
             }
             return ue < s.length() && s.charAt(ue) == '(' ? DdlReaderParts.group(s, ue) : ue;
         }

         /** NEXT VALUE FOR 이름 — 아니면 NEXT 낱말 끝 */
         private static int nextValue(String s, int nextEnd) {
             int v = skip(s, nextEnd);
             int ve = wordEnd(s, v);
             int f = skip(s, ve);
             int fe = wordEnd(s, f);
             if (!"VALUE".equalsIgnoreCase(s.substring(v, ve)) || !"FOR".equalsIgnoreCase(s.substring(f, fe))) {
                 return nextEnd;
             }
             int n = skip(s, fe);
             int ne = wordEnd(s, n);
             return ne == n ? nextEnd : ne;
         }

         /** ON NULL 뒤 자리 — 아니면 from */
         private static int afterOnNull(String s, int from) {
             int oe = wordEnd(s, from);
             int n = skip(s, oe);
             int ne = wordEnd(s, n);
             boolean onNull = "ON".equalsIgnoreCase(s.substring(from, oe)) && "NULL".equalsIgnoreCase(s.substring(n, ne));
             return onNull ? skip(s, ne) : from;
         }

         private static int skip(String s, int k) {
             int i = k;
             while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                 i++;
             }
             return i;
         }

         /** 공백·쉼표·괄호·따옴표 앞까지 — 옛 exprEnd 의 낱말 꼴과 같다(SYSDATE+1 은 한 낱말) */
         private static int wordEnd(String s, int k) {
             int i = k;
             while (i < s.length() && !Character.isWhitespace(s.charAt(i)) && STOPS.indexOf(s.charAt(i)) < 0) {
                 i++;
             }
             return i;
         }
     }
     ```
  2. `DdlReaderParts.defaultExpr` — `return raw.substring(m.end(), exprEnd(masked, m.end()));` 를
     ```java
     int[] span = DdlDefault.span(masked, m.end());
     return span[0] < span[1] ? raw.substring(span[0], span[1]) : null;
     ```
     로. javadoc 첫 줄을 「DEFAULT 뒤 식 — 항과 이항 연산자로 이은 식 전체(1-130, `DdlDefault`). GENERATED … BY DEFAULT 의 DEFAULT 는 아니다(A-29)」 로
  3. `DdlReaderParts.exprEnd` 를 지운다 · `group` 의 `private static` → `static`(javadoc 그대로). `closingQuote` 는 그대로
- 시험(먼저): `DdlReaderTest.defaultExpressionsRunToTheEnd` — 위 표 열한 꼴을 `Map<식, 기대>` 로 돌며 `DdlReader.read("CREATE TABLE t (a VARCHAR(9) DEFAULT " + 식 + ", b INT)")` 의 `a` 기본값이 기대와 같고 `b` 가 컬럼으로 읽힌다(둘째 컬럼을 삼키지 않는다) · `NOT NULL` 붙은 두 꼴은 `nullable()==false` · 지킴이 다섯 — `SYSDATE NOT NULL`→`SYSDATE` · `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`→`CURRENT_TIMESTAMP` · `SYSDATE+1`→`SYSDATE+1` · `0 COMMENT '상태 - 사용'`→`0`(글자 안 `-` 는 연산자가 아니다) · `'x' CHECK (a > 0)`→`'x'` · `GENERATED BY DEFAULT ON NULL AS IDENTITY` 는 기본값 null(지금과 같다 — `BY` 뒤 DEFAULT)
- 의도된 골든 변화: 없음(DdlReader 를 받는 골든은 수·이름만 얼린다 — 점검 4차 A 확인. `corpus/ddl-summary.json` 이 움직이면 7절)
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,DdlGenTest,AlterGenTest,InsertGenTest,GeneratorTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): `'x'::타입` 뒤 형 변환 글·`AT TIME ZONE`·`COLLATE` 가 붙은 식은 지금처럼 앞 항까지 · 단항 `NOT` · `InsertGen.cols:131` 의 자동증가 판정은 `nextval(`·`IDENTITY` 글만 봐서 통째로 읽힌 `NEXT VALUE FOR seq` 기본값을 자동증가로 안 본다(새 행 후보)
- 의존: 1-129(같은 파일)

#### 1-131 — 반영 DDL: 새 표의 CREATE·인덱스만 스키마를 안 감싼다 (점검 4차 A4-01 Important)
- 대상: `core/gen/DdlGen.java`(`generate` 겹침 하나) · `core/gen/AlterGen.java`(`Run.newTables:296`) · `AlterGenTest`
- 사실(점검 탐침): postgresql → postgresql, `Options.schema` 없음, A = `Sales.t1`, B = `Sales.t1`(길이 바뀜) + 새 표 `Sales.t2`(FK → t1, 인덱스 하나) → `CREATE TABLE Sales.t2 (…)` · `CREATE INDEX ix_t2 ON Sales.t2` 와 `ALTER TABLE "Sales".t2 ADD CONSTRAINT … REFERENCES "Sales".t1` 이 한 스크립트에 같이 나간다(기대 CREATE·CREATE INDEX·COMMENT 도 `"Sales".t2`). PG 는 `Sales` 를 `sales` 로 접어 스키마 없음 오류가 나거나, `sales` 스키마가 따로 있으면 **거기에 표가 생긴다**. 번들 91 `1-118` 이 `Run.gen():175-181` 의 Gen 에만 `exactSchema` 를 세웠고 `newTables` 는 `DdlGen.generate(…)` 로 새 Gen(거짓)을 만든다
- 변경: `DdlGen` 에 같은 패키지 겹침 `static Result generate(List<Table> tables, Options o, TypeMapping types, boolean exactSchema)` — 지금 `generate(3)` 몸을 옮기고 Gen 을 만든 바로 뒤 `g.exactSchema = exactSchema;`. 공개 `generate(tables, o, types)` 는 `return generate(tables, o, types, false);` · `newTables` 가 `DdlGen.generate(list, new DdlGen.Options(…), types, DdlGen.blank(o.schema()))` 로 부른다(`Run.gen` 과 같은 식). `Run` 에 메서드를 안 더한다
- 시험(먼저): `AlterGenTest.newTableKeepsTheExactSchema` — 사실 줄 꼴 → 스크립트의 `CREATE TABLE`·`CREATE INDEX`·`COMMENT ON` 줄이 전부 `"Sales".t2` 를 쓴다(전 — `Sales.t2`) · 지킴이(점검자 확인): `Options.schema="Sales"` 면 전부 `Sales.`(감싸지 않는다) · 소문자 스키마는 안 감싼다
- 의도된 골든 변화: `alter-*` 단위 골든 0 기대(대소문자 섞인 스키마 픽스처 없음 — 바뀌면 그 줄이 스키마 인용인지 보고 갱신)
- 검증: `bash scripts/mvn.sh -q test -Dtest='AlterGenTest,DdlGenTest,MetaRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 1-132 — 반영 DDL: 이름이 달라도 구조가 같으면 같은 제약 (점검 4차 A4-03 · A4-04 Important)
- 대상: `core/gen/AlterGen.java`(새 static `alignByShape` · `uniques`·`indexes`·`fks` 를 부르는 자리) · `AlterGenTest`
- 사실(점검 탐침): 비교(`SnapshotDiff:145-164`)는 구조 글로 짝짓는데 반영 DDL(`AlterGen.Run.uniques:763`·`indexes:776`·`fks:800`)은 이름 먼저다(3절 [고정] 「비교와 반영 DDL 은 같은 짝짓기」 가 이 자리에 안 닿았다)
  - oracle → oracle, B = `CHILD`(FK `SYS_C003` P1→PARENT.ID · 유니크 `SYS_C004`(CD)), A = 같은 구조에 이름 `FK_CHILD_1`·`UK_CHILD_1`(이 도구가 지은 대체 이름) → `DROP CONSTRAINT FK_CHILD_1` · `DROP CONSTRAINT UK_CHILD_1` · `ADD CONSTRAINT UK_CHILD_1 UNIQUE (CD)` · `ADD CONSTRAINT FK_CHILD_1 …`(문장 넷, `SnapshotDiff.compare` 는 차이 0 — 기대 문장 0). `1-118` 이 대체 이름을 달게 하면서 이 왕복이 매번 생긴다
  - mariadb(B) `member`·`product` 둘 다 유니크 이름 `code`, A = DdlGen mariadb → postgresql 결과(`product` 의 유니크는 `uk_product_1` — `ukName:115-124` 가 바꿨다) → `ALTER TABLE product DROP CONSTRAINT uk_product_1;` + `ADD CONSTRAINT code UNIQUE (code);`(기대 문장 0 — ADD 는 PG 에서 이름 겹침으로 실패하고 DROP 은 이미 돌아 **유니크가 사라진다**)
- 변경: 바깥 `AlterGen` 에 static 도우미(`Run` 메서드 40 기준선 — `Run` 에 안 더한다)
  ```java
  /**
   * 1-132(A4-03·04) — 이름으로 짝이 없는 것끼리 구조 글이 같으면 같은 제약·인덱스로 본다: a 쪽 열쇠를 b 쪽 열쇠로 바꾼 사본
   * (비교 SnapshotDiff 와 같은 짝짓기 — 이름만 다른 것은 차이가 아니다)
   */
  static <X> Map<String, X> alignByShape(Map<String, X> a, Map<String, X> b, java.util.function.Function<X, String> shape) {
      Map<String, X> out = new LinkedHashMap<>();
      Set<String> taken = new HashSet<>();
      a.forEach((k, x) -> {
          String s = shape.apply(x);
          String to = b.containsKey(k) ? k : b.entrySet().stream()
                  .filter(e -> !a.containsKey(e.getKey()) && !taken.contains(e.getKey()) && shape.apply(e.getValue()).equals(s))
                  .map(Map.Entry::getKey).findFirst().orElse(k);
          taken.add(to);
          out.put(to, x);
      });
      return out;
  }
  ```
  `uniques(ta)`·`indexes(ta)`·`fks(ta)`(A 쪽 표의 맵)를 쓰는 자리마다 B 쪽 맵을 먼저 구해 `alignByShape(uniques(ta), ub, AlterGen::uqShape)` 꼴로 감싼다 — 구조 글은 FK `Run.fkShape` · 인덱스 `Run.shape` · 유니크는 `uniques:771` 의 `"UQ(" + … + ")"` 식을 static `uqShape(UniqueKey)` 로 빼서 같이 쓴다. `uniquesAndIndexes(4)`(19)·`keyBreakingFks(1)`(18)는 기준선 — 그 안에서는 호출 식만 바뀐다(분기 없음)
- 시험(먼저): `AlterGenTest.sameShapeDifferentNameIsNotAChange` — 두 꼴의 반영 DDL 에 `DROP CONSTRAINT`·`ADD CONSTRAINT`·`DROP INDEX`·`CREATE INDEX` 가 없다(전 — 문장 넷·둘) · 인덱스 이름만 다른 같은 컬럼 인덱스도 문장 0 · 지킴이 — 이름이 같고 컬럼이 바뀐 유니크는 DROP + ADD(지금과 같다) · 이름이 다르고 구조도 다른 FK 는 DROP + ADD · 기존 `defaultFkRulesAreTheSame`(1-128) 초록
- 의도된 골든 변화: `alter-*` 단위 골든 0 기대 · `--db` 의 `alterRoundTrip`·`alterTwoReleases` 는 문장이 **줄 수 있다**(`V-71` 허용 변화 — 이름만 다른 제약의 DROP + ADD 가 빠진다)
- 검증: `bash scripts/mvn.sh -q test -Dtest='AlterGenTest,SnapshotDiffTest,MetaRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): 제약 이름만 바꾸는 반영(RENAME CONSTRAINT)은 안 낸다(비교도 차이로 안 본다)
- 의존: 1-131

#### 1-134 — DDL 생성: FK 자식 컬럼이 부모 키를 따라간다 — 자동증가 정수 · 길이 (점검 4차 A4-05 Important · A4-17)
- 대상: `core/gen/DdlGen.java`(`typeKey:102-105` · `Gen.keyLen:449-456` · 새 `refColumn`) · `DdlGenTest`
- 사실(점검 탐침): oracle 원본 `CUSTOMERS(CUSTOMER_ID NUMBER(38,0) 자동증가 PK)` · `ORDERS(CUSTOMER_ID NUMBER(38,0) FK)` → postgresql: 부모 `BIGINT GENERATED BY DEFAULT AS IDENTITY` 대 자식 `NUMERIC(38,0)` · mssql·mariadb: `BIGINT IDENTITY(1,1)`/`AUTO_INCREMENT` 대 `DECIMAL(38,0)` — FK 문장이 실패한다(PG 는 numeric → bigint FK 를 거절, MSSQL·MariaDB 는 양쪽 타입이 같아야 한다 — 추적). 번들 91 `1-121` 이 부모만 정수로 냈다(전에는 부모 CREATE 가 실패했다). 실물: 표본 `db-samples/customer_orders/co_create.sql` 의 identity 부모 여섯 · postgresql → mssql, `p(code varchar(50) PK)` · `c(p_code text FK)` → 부모 `NVARCHAR(50)`, 자식 `NVARCHAR(255)`(`keyLen` 이 늘 255 — MSSQL 은 FK 양쪽 길이가 같아야 한다)
- 변경:
  1. 새 `private static Optional<Column> refColumn(Gen g, Table t, Column c)` — `t` 의 FK 가운데 `c` 가 든 것의 같은 자리 참조 컬럼(참조 컬럼 목록이 비면 부모 PK 의 같은 자리)을 대상 표 목록(`Gen` 이 FK 문장에 쓰는 표 맵 — 대문자 이름)에서 찾는다. 부모 표·컬럼이 없으면 빈 값
  2. `typeKey` — `BigDecimal` 가지에서 `auto(c)` 다음에: `refColumn(g, t, c)` 가 자동증가 BigDecimal 컬럼이면 `"Long"`(경고 `<표>.<컬럼>: FK 부모가 자동증가 — 같은 정수 타입으로 냈다`). 판정은 도우미 `private static boolean autoParent(Gen g, Table t, Column c)` 로(`typeKey` 분기 +1)
  3. `keyLen` — 끝의 `255` 자리: `fkColumn(t, c)` 이고 `refColumn` 의 길이가 있으면 그 길이(대상 상한 안일 때), 없으면 지금 값. 식은 도우미 `fkLen(t, c, n)` 호출로
- 시험(먼저): `DdlGenTest.fkChildFollowsTheParentKey` — 첫 꼴을 postgresql·mssql·mariadb 로 → 자식 컬럼 타입이 부모와 같은 `BIGINT`(전 `NUMERIC(38,0)`·`DECIMAL(38,0)`) + 그 경고 · 둘째 꼴(postgresql → mssql) → 자식 `NVARCHAR(50)`(전 255) · 지킴이 — 부모가 자동증가가 아닌 `NUMBER(38,0)` FK 는 양쪽 다 지금 타입 · MSSQL `numeric(18,0) identity` 부모·자식은 지금처럼 둘 다 BIGINT · 기존 `fkColumnFollowsTheKeyLength`(1-119) 초록
- 의도된 골든 변화: `ddl-*` 단위 골든 0 기대 · `--db` `ddlRoundTrip` 은 FK 실패가 **줄 수 있다**(`V-71`)
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlGenTest,AlterGenTest,DeliverableRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): 스냅샷이 Oracle identity 를 자동증가로 싣는지는 드라이버에 달렸다(`JdbcMetaSource:190` — 못 잼)
- 의존: 1-132

#### 1-135 — 반영 DDL 잔손: MSSQL `identity` 타입 글 · 옛 기본값 `NULL` 글 (점검 4차 A4-06 · A4-11)
- 대상: `core/gen/AlterGen.java`(`withoutAuto:109-112` · `:367-371` · `defaultChanged:90`) · `AlterGenTest`
- 사실(점검 탐침): mssql → mariadb, B 에 `id`(nativeType `int identity`, 자동증가, PK) 추가 → `-- [손] 컬럼 id 자동증가 — 키를 만든 뒤 …` 바로 아래 `ADD COLUMN id INT NOT NULL AUTO_INCREMENT`(기대 AUTO_INCREMENT 없음 — `withoutAuto` 가 타입 글의 identity 낱말을 그대로 둔다. 표시가 거짓인 옛 스냅샷은 `[손]` 도 없이 같은 문장) · mariadb → mariadb, A.k 기본값 `NULL`(nullable), B.k 기본값 `uuid()` + NOT NULL → 「…뒤 MODIFY COLUMN 이 기본값을 지운다 — 손으로 다시 넣는다」(기대 「…기본값 없이 나간다 — 손으로 넣는다」 — `:90` 이 「옛 기본값 없음」 을 `blank` 로만 재는데 MariaDB 스냅샷은 기본값 없는 nullable 컬럼에 `NULL` 글을 싣는다)
- 변경: `:367` 근처 조건을 `c.autoIncrement()` → `DdlGen.auto(c)`(낱말도 본다 — `DdlGen:227`) · `withoutAuto` 가 타입 글에서 identity·auto_increment 낱말(`DdlReaderParts.AUTO_WORD` 꼴 — 대소문자 무시, 뒤 `(1,1)` 포함)도 뗀다 · `:90` 의 「없음」 판정을 `DdlGen` 의 `bareDefault` + `nullDefault`(`lostDefault` 가 쓰는 것)로 — 같은 패키지 static 도우미 `DdlGen.noDefault(String)` 하나로 묶어 둘이 같이 쓴다. `modifyColumn(7)`(38)에 분기를 더하지 않는다
- 시험(먼저): `AlterGenTest` — 첫 꼴의 ADD COLUMN 줄에 `AUTO_INCREMENT` 가 없고 `[손]` 줄이 있다(전 — 있다) · 표시 거짓 + 타입 글 `int identity` 도 같다 · 둘째 꼴 → 「기본값 없이 나간다」 글(전 — 「지운다」) · 지킴이 — mariadb → mariadb 새 자동증가 컬럼은 `ADD COLUMN id int NOT NULL` + `[손]`(지금과 같다) · 기존 `unportableDefaultUnchangedOrNew`(1-116) 초록
- 의도된 골든 변화: `alter-*` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='AlterGenTest,DdlGenTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-134

#### 1-136 — 스냅샷 비교: 인덱스 구조 글의 대소문자 (점검 4차 A4-07)
- 대상: `core/meta/SnapshotDiff.java`(`indexes:124-142`) · `SnapshotDiffTest`
- 사실(점검 탐침): `T1(Id, UserId)` 인덱스 `IX_T1(UserId, Id)` 대 `T1(id, userid)` `IX_T1(userid, id)` → `addedIndexes=[IX(userid,id)]`·`removedIndexes=[IX(UserId,Id)]`(컬럼·제약 차이는 0, 반영 DDL 은 「차이 없음」). 번들 91 `1-128` 이 컬럼·제약만 대문자로 접었다 — `ia`·`ib` 와 `backed.contains(ix.columns())` 는 글자 그대로
- 변경: 인덱스 구조 글과 `backed` 견줌도 컬럼·제약과 같은 접기(`folded`/`shown` — 그 파일의 1-128 도우미)를 거친다
- 시험(먼저): `SnapshotDiffTest.columnCaseDoesNotSplit` 옆 — 그 꼴의 인덱스 차이 0(전 — 추가 1·삭제 1) · 지킴이 — 컬럼 차례가 다른 인덱스는 여전히 차이
- 의도된 골든 변화: `diff-*` 골든 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='SnapshotDiffTest,DiffRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 1-137 — ALTER 읽기 잔손: 안 닫힌 이름의 NPE · `ALTER ONLINE/IGNORE TABLE` · 공백 든 제약 이름 (점검 4차 A4-08 · A4-12 · A4-14)
- 대상: `core/gen/DdlAlter.java`(`has:355-358` · `HEAD:19` · `DEFAULT_FOR:49`) · `core/gen/DdlReader.java`(제약 이름 자리 `:41·62·68·88·118` 의 `\S+`) · `DdlReaderTest`
- 사실(점검 탐침): `CREATE TABLE T1 (id INT); ALTER TABLE T1 ADD COLUMN IF NOT EXISTS [c INT;`(`"c`·백틱도) → `DdlReader.read` 가 **NullPointerException**(라우트 500 — 기대 못 읽은 줄 하나. 번들 91 `1-122` 의 `has` 가 `firstToken` 의 null 을 안 본다) · `ALTER ONLINE TABLE T1 ADD COLUMN z INT;`·`ALTER IGNORE TABLE …` → 못 읽은 줄도 없이 통째로 빠진다(기대 z 가 있다) · `ALTER TABLE [dbo].[Order Details] ADD CONSTRAINT [DF Order Details] DEFAULT ((0)) FOR [Unit Price]` → 못 읽은 줄 1·기본값 안 붙음 · `CONSTRAINT [PK Order Details] PRIMARY KEY CLUSTERED ([Order ID] ASC)` → PK 이름 null(`1-123` 이 `QNAME` 을 표·참조 표에만 넣었다)
- 변경: `has` — 토큰이 null 이면 거짓 · `HEAD` 머리에 `(?:ONLINE\\s++|OFFLINE\\s++)?(?:IGNORE\\s++)?` 를 `ALTER\\s++` 와 `TABLE` 사이에 · 제약 이름·`FOR 컬럼` 자리의 `\\S+` 를 `DdlReaderParts.PART` 한 마디로(그 상수를 package-private 로 연다 — `private static final String PART` → `static final`). 각 정규식은 선택 묶음만(묶음 반복 없음)
- 시험(먼저): `DdlReaderTest.alterIfExistsAndOptions` 옆 — 세 안 닫힌 꼴이 예외 없이 못 읽은 줄 하나(전 NPE) · `ALTER ONLINE TABLE`·`ALTER IGNORE TABLE` → 컬럼 z 가 붙는다(전 — 빠짐) · 공백 든 제약 이름 둘 → 기본값 `((0))`(또는 벗긴 `0`)이 `Unit Price` 에 붙고 PK 이름이 `PK Order Details`(전 — 못 읽은 줄·null) · `regexesAreLinear` 에 `CONSTRAINT [` + `a `×20000 한 줄을 더해 지금 상한 안 · 지킴이(점검자 확인) — `ADD COLUMN IF NOT EXISTS (c INT, d INT)` · `DROP COLUMN IF EXISTS (zz, d)` · `ALGORITHM = INPLACE, LOCK=NONE` 조용 · `FOO=1` 못 읽은 줄 하나
- 의도된 골든 변화: `corpus/ddl-summary.json`·`ddl-unreadable.txt` 의 못 읽은 줄이 **줄 수 있다**(공백 든 제약 이름 — `V-71` 에서 수를 이력에)
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,InsertGenTest,GeneratorTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-130(같은 파일)

#### 1-138 — DDL 읽기 잔손: `db..표` 의 빈 스키마 마디 · 표 안 인덱스 두 꼴 (점검 4차 A4-09 · A4-13)
- 대상: `core/gen/DdlPull.java`(`find:89` · `:132-141`) · `core/gen/DdlReaderParts.java`(`keyHead:36-41`) · `DdlReaderTest`
- 사실(점검 탐침): `CREATE TABLE dbo.emp (id INT); ALTER TABLE mydb..emp ADD c INT;` → 컬럼 `[id]`·못 읽은 줄 0(기대 `[id, c]` — 번들 91 `1-124` 의 `other` 식이 요청 스키마가 빈 글인 경우를 「다른 스키마」 로 본다. `mydb.dbo.emp` 는 맞다) · `CREATE TABLE T1 (a INT, b INT, INDEX ix_c CLUSTERED COLUMNSTORE)` → 가짜 컬럼 `INDEX`(타입 `ix_c CLUSTERED COLUMNSTORE`) · ``KEY `my idx` (`a`)``(백틱 이름에 공백) → 가짜 컬럼 `KEY`(둘 다 못 읽은 줄 0)
- 변경: `find` 의 `other` 에 「요청 스키마 마디가 비지 않았다」 조건(식 안 `&&` 하나 — `find` 는 기준선 줄 없음) · `keyHead` — `(` 앞 사이 글을 나눌 때 싼 이름(대괄호·큰따옴표·백틱)은 통째 토큰으로(`DdlReaderParts.PART` 로 훑는다) · 괄호가 없어도 꼬리가 `CLUSTERED COLUMNSTORE`·`NONCLUSTERED COLUMNSTORE` 면 인덱스 줄로 보고 컬럼으로 안 넘긴다(컬럼 없는 인덱스 — 인덱스 목록에는 안 넣는다)
- 시험(먼저): `DdlReaderTest` — 세 꼴(전 → 후): `[id, c]` · 컬럼 `[a, b]`(가짜 컬럼 없음) · 컬럼에 `KEY` 가 없고 인덱스 `my idx(a)` · 지킴이(점검자 확인) — `KEY INT` 는 컬럼 · `INDEX ix_b UNIQUE NONCLUSTERED (b)`·`KEY (a)`·`KEY ix_d USING BTREE (b)` 는 인덱스 · 스키마가 정말 다른 `ALTER TABLE s2.emp`(맨 이름 표의 스키마가 `s1`)는 지금처럼 안 붙는다
- 의도된 골든 변화: `corpus/ddl-summary.json` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-137

#### 4-52 — INSERT 정확 이름: 스냅샷 방언과 고른 방언이 다를 때 (점검 4차 A4-02 Important)
- 대상: `core/gen/InsertGen.java`(`ident:608-611` 과 그것을 부르는 스냅샷 모드 자리) · `core/gen/DdlGen.java`(`Gen.exact:687-696` 의 식을 static 으로) · `web/InsertRoutes.java:96-97` · `InsertGenTest` · `InsertRoutesTest`
- 사실(점검 탐침): 표 `tb_user(user_id, user_nm)`(MariaDB 스냅샷), dialect=oracle → `INSERT INTO "tb_user"("user_id", "user_nm")`(번들 91 전 `INSERT INTO tb_user(user_id, …)`). 같은 표의 DDL 생성 mariadb → oracle 은 `CREATE TABLE tb_user (user_id …)`(안 감쌈 → `TB_USER`)라 **그 INSERT 는 ORA-00942**. 반대(Oracle 스냅샷 `TB_USER` → postgresql)도 `"TB_USER"` 대 DDL `TB_USER`. 번들 91 `4-46` 의 `ident(name, db, exact)` 는 「**대상**이 접는 글자와 다른가」 를 보고, `DdlGen.Gen.exact` 는 「**원본**이 접는 글자와 다른가」 를 본다 — 같은 방언일 때만 둘이 같다. 화면의 `ins_dialect` 는 스냅샷과 무관한 select 이고 첫 값이 oracle 이다
- 변경:
  1. `DdlGen` — `Gen.exact` 의 switch 를 `static boolean exactIn(String source, String name)`(source 가 null·빈 글·그 밖 방언이면 거짓)으로 빼고 `Gen.exact` 는 그것을 부른다
  2. `InsertGen` — 스냅샷 모드의 정확 이름 판정을 `DdlGen.exactIn(스냅샷 원본 방언, 이름)` 으로(대상 방언의 접기로 재지 않는다). 원본 방언은 `InsertRoutes` 가 스냅샷의 DB 버전 글에서 구해(`TypeMapping.dialectOf(dbVersion)` — 모르면 null → 거짓) 지금 `exact` 를 넘기는 자리로 넘긴다. `generate(4)`(20)·`register(3)`(23)는 기준선 — 분기 없이 식·인자 값만 바꾼다(인자 수는 그대로 — 지금 불 값을 넘기는 자리가 있으면 그 값의 계산만 옮긴다)
- 시험(먼저): `InsertGenTest.exactNamesInSnapshotMode` 옆 — MariaDB 원본 소문자 이름 + 대상 oracle → `INSERT INTO tb_user(user_id, user_nm)`(전 — 전부 따옴표) · Oracle 원본 `TB_USER` + 대상 postgresql → 안 감싼다 · 지킴이(4-46) — PostgreSQL 원본의 `"UserName"`(대소문자 섞임) + 대상 postgresql → 감싼다 · Oracle 원본의 `"lower_nm"` + 대상 oracle → 감싼다 · `InsertRoutesTest` — 스냅샷 방언 ≠ `dialect` 요청의 SQL 에 평범한 이름이 따옴표 없이
- 의도된 골든 변화: `hr-insert-*`·`insert-*` 골든 0 기대(같은 방언 픽스처) · `--db` `insertRuns` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='InsertGenTest,InsertRoutesTest,DdlGenTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): upsert·MERGE 틀과 DDL 모드는 지금 그대로(번들 91 [고정] 12)
- 의존: 1-134(`DdlGen` 같은 파일)

#### 4-53 — INSERT 값 잔손: 복합 FK 키의 헛경고 · `BIT(n)` · INTERVAL 앞자리 · 글자·수 섞인 IN 목록 (점검 4차 A4-10 · A4-15 · A4-16 · A4-18)
- 대상: `core/gen/InsertGen.java`(`keyOverlap:372`) · `core/gen/InsertGenParts.java`(`bit:76-79` · `leading:70-73` · `inSet:61-67`) · `InsertGenTest`
- 사실(점검 탐침): `C(A, B)` PK = FK(A,B) → P, 부모 행 (1,1)(1,2)(2,1)(2,2), 4행 → 「(A, B): 값 조합이 2가지인 키라 4행이면 값이 겹친다」 헛경고(INSERT 넷은 서로 다르다 — 값수를 컬럼별 서로 다른 수의 LCM 으로 센다) · PG `F8 bit(8)` → `B'1'`(PG 는 길이가 다른 bit 값을 거절 — 추적) · `IV INTERVAL DAY(2) TO SECOND(6)`, 100행 → 100행째 `INTERVAL '100' DAY(3)`(컬럼 앞자리 정밀도 2 를 넘는다 — ORA-01873 추적) · `CHECK (c IN ('A', 1))` → INSERT 값 `A`(따옴표 없음 — `Validation.parse` 의 섞인 목록이 quoted=false 하나로 온다)
- 변경: `keyOverlap` — 한 FK 가 채운 컬럼 묶음은 부모 행 수 하나로 센다(컬럼마다 distinct 를 곱·LCM 하지 않는다) · `bit` — 길이 n 이 1 보다 크면 n 자리 비트 글(`B'` + `0`×(n-1) + `1` + `'`) · `leading` 을 쓰는 INTERVAL 값은 `idx % 99 + 1`(앞자리 두 자리 안) · `inSet` — quoted=false 인데 수가 아닌 값이 하나라도 있으면 거짓(허용 값 목록으로 안 쓴다 — 옛 길로 물러선다). `genVal(4)`(34)·`csvLiteral(5)`(31)·`Validation.parse(1)`(28)는 기준선 — 식만 바꾼다
- 시험(먼저): `InsertGenTest` — 네 꼴(전 → 후): 경고 없음 · `B'00000001'` · 100행 전부 `DAY` 리터럴의 수가 99 이하 · IN 섞인 목록 컬럼은 허용 값 맵에 없다(값은 타입 기본 길로) · 지킴이(점검자 확인) — `NUMBER(2)` PK 150행은 경고 하나 · `BIT VARYING`·`bit(1)` 은 `B'1'` · pg_dump `= ANY ((ARRAY[…])::text[])`·MSSQL `([c]='B' OR [c]='A')`·`'it''s'`·`N'A'` 는 허용 값으로 읽힌다 · `NOT IN` 은 빈 맵
- 의도된 골든 변화: `insert-*`·`hr-insert-*` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='InsertGenTest,ValidationTest,InsertRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 4-52


### 묶음 ⑥ 논리명·메타 (db 레인)

#### 3-27 — 마스킹 `[알림]` 줄: 제외 키의 줄바꿈 (대기 행 — 번들 91 리뷰 M9)
- 대상: `core/logical/Masking.java`(`sql` 한 줄 · 새 `oneLine`) · `MaskingTest` · `LogicalRoutesTest`
- 사실: `LogicalRoutes.MaskScope.notices:285` 가 한 번도 안 맞은 제외 키(요청 본문 `exclude[*].owner/table/col` 을 대문자로 이은 글)를 그대로 싣고 `Masking.sql:238` 이 `-- [알림] <글>\n` 로 쓴다 — 키에 `\n` 이 있으면 뒤 글이 주석 밖 SQL 줄이 된다(마스킹 SQL 은 사람이 DB 에 돌리는 글이다)
- 변경: `Masking.sql` 의 `notices.forEach(n -> sb.append("-- [알림] ").append(n).append('\n'));` 에서 `append(n)` → `append(oneLine(n))`. 새 도우미(알림이 어디서 오든 한 자리에서 막는다 — `MaskScope` 는 안 고친다):
  ```java
  /** 3-27 — 알림 글의 줄바꿈·제어 글자를 빈칸으로(줄 주석 밖으로 새지 않게). U+2028·U+2029 도 */
  private static String oneLine(String s) {
      StringBuilder b = new StringBuilder(s.length());
      for (int i = 0; i < s.length(); i++) {
          char c = s.charAt(i);
          b.append(Character.isISOControl(c) || c == ' ' || c == ' ' ? ' ' : c);
      }
      return b.toString();
  }
  ```
- 시험(먼저): `MaskingTest.noticesStayOnOneCommentLine` — `Masking.sql(후보 하나, "oracle", 규칙, List.of("제외 1개가 후보에 없다: A.B" + (char) 10 + "DROP TABLE T1", "x" + (char) 13 + "y" + (char) 0x2028 + "z"))` 의 줄마다: `UPDATE` 로 시작하는 첫 줄 앞의 모든 줄이 `--` 로 시작하고, `DROP` 으로 시작하는 줄이 없다(전 — `DROP TABLE T1` 이 한 줄로 선다) · `LogicalRoutesTest.maskingExcludeIsPerSchema` 끝에 — `exclude` 의 `col` 이 `"NOPE" + (char) 10 + "DROP TABLE T1"` 인 요청의 `sql` 에 `\nDROP` 이 없고 `-- [알림] 제외 1개가 후보에 없다: TB_USER.NOPE DROP TABLE T1` 이 있다
- 의도된 골든 변화: 없음(`masking-*` 골든의 알림 글에 제어 글자가 없다)
- 검증: `bash scripts/mvn.sh -q test -Dtest='MaskingTest,LogicalRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 1-133 — 행 수 경고: 드라이버의 「지원 안 함」 을 읽기 전용 거절로 센다 (설계 76 §4 후보 → 확인)
- 대상: `core/meta/RowCounter.java`(`state`) · `SnapshotServiceTest`
- 사실: `RowCounter.state:96-100` 이 `e instanceof SQLFeatureNotSupportedException` 이면 무조건 `"REFUSED"`(읽기 전용 래퍼가 거절). 드라이버가 스스로 던진 같은 형식(예: `setQueryTimeout` 미지원 — `:66`)도 「읽기 전용 거절」 로 세어 원인이 가려진다
- 변경: `state` 를 package-private `static` 으로 열고 판정을 래퍼의 글머리로 좁힌다
  ```java
  static String state(SQLException e) {
      if (e instanceof java.sql.SQLFeatureNotSupportedException && e.getMessage() != null
              && e.getMessage().startsWith(kr.ejg.toolbox.core.conn.ReadOnlyJdbc.REFUSED)) {
          return "REFUSED";
      }
      return e.getSQLState() == null ? "" : e.getSQLState();
  }
  ```
  (`core.meta` → `core.conn` 은 이미 있는 방향이다 — `SnapshotService`. javadoc 에 「드라이버가 던진 지원 안 함(0A000)은 그 SQLState 로」 한 마디)
- 시험(먼저): `SnapshotServiceTest.driverUnsupportedIsNotRefused` — `RowCounter.state(new java.sql.SQLFeatureNotSupportedException("timeout", "0A000"))` → `"0A000"`(전 `"REFUSED"`) · `RowCounter.state(new java.sql.SQLFeatureNotSupportedException(ReadOnlyJdbc.REFUSED + "x"))` → `"REFUSED"` · `RowCounter.state(new java.sql.SQLException("x"))` → `""`. 기존 F-K11 시험(`:181`) 그대로 초록
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='SnapshotServiceTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 9-11 — 읽기 전용 래퍼: `_` 로 시작하는 숫자 뒤 토막 · 긴 16진 토막의 제곱 시간 (점검 4차 F4-01 · F4-02 — 절대 규칙 6)
- 대상: `core/conn/ReadOnlyJdbc.java`(`words:156` · `glued:182-188`) · `ReadOnlyJdbcTest`
- 사실(점검 탐침): `SELECT A FROM T WHERE A=1_0FOR SHARE` · `SELECT 1_0INTO X` → `check` 통과(기대 거절 — 번들 91 `9-10` 의 「숫자에 붙은 토막은 모든 꼬리를 잰다」 가 `_` 머리에서 빈다: `afterNumber && Character.isLetter(w.charAt(0))`). H2 2.3 은 `A=1_0FOR UPDATE` 를 「10 FOR UPDATE」 로 읽는다(실행) — 일곱 방언에서 실제 쓰기·잠금으로 닿는 꼴은 못 찾았다(Minor · 닫힌 쪽으로 메운다) · `SELECT 1 FROM T WHERE H = 0x` + `AB`×n → 10만 자 3.0초 · 30만 자 26.9초(`glued` 가 꼬리마다 substring·해시 — 제곱)
- 변경:
  1. `words` — 조건을 `afterNumber && (Character.isLetter(w.charAt(0)) || w.charAt(0) == '_')` 로(판정은 `private static boolean gluedHead(char c)` 로 빼도 된다)
  2. `glued` — 금지 낱말보다 긴 꼬리는 맞을 수 없다: 루프를 `for (int k = Math.max(1, w.length() - LONGEST); k < w.length(); k++)` 로, `private static final int LONGEST = WRITE.stream().mapToInt(String::length).max().orElse(0);`(`FOR`·`SHARE` 는 그보다 짧다 — `Math.max(LONGEST, 5)` 로 둘을 덮는다)
- 시험(먼저): `ReadOnlyJdbcTest.bypassesFromAudit4` — `"SELECT A FROM T WHERE A=1_0FOR SHARE"`·`"SELECT 1_0INTO X"`·`"SELECT 1_UPDATE T SET A = 2"` 가 `SQLFeatureNotSupportedException`(전 — 통과) · `stillPasses` 에 `"SELECT 1_000 FROM T"`·`"SELECT A_1, B_2 FROM T"` 를 더해 통과 · 시간 — `0x` + `AB`×150000 문장의 `check` 가 `assertTimeout(Duration.ofSeconds(2), …)` 안(전 26초) · 지킴이 — `bypassesFromAudit3` 의 열한 꼴·`intendedRefusals`(1-129 의 `0x1ADD` 포함) 그대로
- 의도된 골든 변화: 없음(`--db` `readOnlyBypasses` 0 그대로)
- 검증: `bash scripts/mvn.sh -q test -Dtest='ReadOnlyJdbcTest,SqlProbeTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): 따옴표 안 UUID·해시 글이 숫자로 시작해 `add` 로 끝나면 거절된다(엄격한 쪽 — 1-129 와 같은 대가) · 이름이 정확히 `DIALOG`·`ADD` 인 표·컬럼
- 의존: 1-129(같은 시험 파일)

#### 2-57 — 산출물 잔손: 08 코드값 SELECT 의 예약어 이름 접기 · 03 제약조건의 여러 낱말 타입 (점검 4차 B4-01 · B4-02)
- 대상: `core/deliverable/CodeAndLink.java`(`quote:123-132`) · `core/deliverable/Definitions.java`(`constraints:360`) · `CodeAndLinkTest` · `DefinitionsTest`
- 사실(점검 탐침): `codeSql(CodeTable(null, "cmm_code", null, null, "code", "name", "condition", null, null), "H2")` → `SELECT code, name, "condition" FROM cmm_code ORDER BY code` → H2 42122(컬럼 없음 — 번들 91 전 글은 통과). `2-54` 가 예약어 이름을 「받은 글자 그대로」 감싼다 — `QualitySql.id:151`·`Masking.id:335` 는 감쌀 때 방언대로 접는다(판정 중복 어긋남) · 조건 `(reg_dt >= ''::timestamp without time zone)` 에 컬럼 `time`·`zone` 이 맞는다 · `…ARRAY[''::character varying…]` 에 `varying` 도(`2-55` 가 앞 글자 `:` 만 뺐다)
- 변경: `CodeAndLink.quote` — 이름이 한 가지 대소문자(전부 소문자거나 전부 대문자)면 감싸기 전에 제품의 접기대로(Oracle·Tibero·H2 는 대문자, PostgreSQL 은 소문자, 그 밖은 그대로) 바꾼다. 섞인 대소문자 이름은 지금처럼 글자 그대로. `codeCandidates(1)`(29)는 안 건드린다 · `Definitions.constraints:360` — 컬럼 이름을 맞추기 전에 조건 글에서 `::` 뒤 타입 낱말 토막(`::` + 낱말 + 그 뒤 `without time zone`·`with time zone`·`varying`·`precision` 꼴 꼬리 낱말)을 지운다(정규식 한 줄 — 묶음 반복 없이 선택 꼬리를 나열. `Definitions` 메서드 34 기준선 — 메서드를 안 더한다: 상수 `Pattern` 과 그 자리의 식만)
- 시험(먼저): `CodeAndLinkTest` — 그 `codeSql` 의 글이 `"CONDITION"` 을 쓰고(H2 메모리에 `CREATE TABLE cmm_code (code VARCHAR(9), name VARCHAR(9), condition VARCHAR(9))` 로 실제 실행 — 통과. 전 42122) · PostgreSQL 은 `"condition"` · 섞인 `"Condition"` 은 그대로 · `DefinitionsTest` — 두 조건 글에서 `time`·`zone`·`varying` 컬럼이 제약에 안 붙는다(전 — 붙는다) · 지킴이 `reg_dt` 는 붙는다 · `timestamp`·`character`·`text` 는 지금처럼 안 붙는다
- 의도된 골든 변화: `deliverable-*` 골든 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='CodeAndLinkTest,DefinitionsTest,DeliverableRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 2-58 — 산출물 쓰기 잔손: 머리 칸의 칸 상한 · 같은 파일 판정의 경로 표기 (점검 4차 B4-03 · B4-04)
- 대상: `core/report/XlsxWriter.java`(`:78` 머리 칸 · `fit:30-33`) · `core/deliverable/DeliverableService.java`(`ready:324`) · `XlsxWriterTest` · `DeliverableServiceTest`
- 사실(추적): `2-53` 의 칸 상한 고침이 값 칸뿐이다 — 열 이름이 32,767자를 넘는 `ResultTable` 의 머리 칸 `setCellValue(이름)` 은 전과 같은 `IllegalArgumentException` · `2-56` 의 「같은 file」 판정이 글자 비교뿐이다 — 매핑 02 file `x.xlsx` · 03 file `./x.xlsx` 는 `inside` 가 같은 경로로 푸는데 `putIfAbsent` 열쇠가 달라 안 걸리고 조용히 덮인다. `00_작성안내.xlsx`·18 의 파일 이름과 같은 file 도 안 걸린다
- 변경: `XlsxWriter` 머리 칸도 `fit(…)` 을 거친다 · `DeliverableService.ready` — 열쇠를 `inside(outDir, file)` 의 정규화 경로 글로 하고, 안내(`GUIDE`)·18 의 파일 이름을 미리 넣어 둔다(겹치면 지금 「같은 file」 거절 글 그대로). `build(8)`(22)·`writeGuide(11)` 는 기준선 — 안 고친다
- 시험(먼저): `XlsxWriterTest` — 열 이름 40,000자 표가 예외 없이 써지고 머리 칸 길이가 32,767 이하(전 — 예외) · `DeliverableServiceTest` — `x.xlsx`·`./x.xlsx` 두 매핑 → 거절(전 — 지난다) · 매핑 file 이 `00_작성안내.xlsx` → 거절 · 지킴이 — 서로 다른 file 은 지금처럼 지난다
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='XlsxWriterTest,DeliverableServiceTest,DeliverableRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 2-57

#### 2-59 — 양식 채우기: 다시 계산 표시가 채운 시트에만 선다 (점검 4차 B4-C1 후보 → 확인)
- 대상: `core/report/FormulaRange.java`(`grow:37-39`) 또는 `core/report/XlsxFiller.java`(저장 직전 한 줄) · `XlsxFillerTest`
- 사실(점검 탐침 — 메모리 통합 문서): 시트 `summary` 에 `SUM(data!B:B)`·`COUNTA(data!A3:A3)`·`data!B4`, `data` 에 5행을 채우면 → `data` 시트의 다시 계산 표시만 참 · 통합 문서 표시 거짓 · `summary` 의 캐시 값 0 그대로(실제 50). 캐시 값을 읽는 쪽(한셀·POI·미리보기)에는 틀린 값이 간다. 엑셀이 시트 하나의 표시로 통합 문서 전체를 다시 계산하는지는 못 쟀다
- 변경: 채운 뒤 통합 문서에 `wb.setForceFormulaRecalculation(true)` 한 줄(시트 표시는 그대로 둔다). `XlsxFiller.fill(4)`(23)에 분기를 더하지 않는다 — 조건 없이 부르거나 `FormulaRange.grow` 의 표시 자리에서
- 시험(먼저): `XlsxFillerTest` — 두 시트 양식을 채운 결과를 다시 열어 `wb.getForceFormulaRecalculation()` 이 참(전 — 거짓) · 지킴이(점검자 확인) — 위쪽 `COUNTA(A3:A3)` → `A3:A7` · `SUM($B$3:$B$3)` → `$B$3:$B$7` · 채운 시트 표시 참
- 의도된 골든 변화: 없음(xlsx 골든은 칸 값만 견준다 — 바뀌면 그 차이가 표시 한 줄인지 보고 갱신)
- 검증: `bash scripts/mvn.sh -q test -Dtest='XlsxFillerTest,DeliverableServiceTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): 다른 시트의 수식 범위는 안 넓힌다 · 이름 정의(`SUM(DATA)`)의 범위는 그대로
- 의존: 2-58

### 묶음 ⑦ 파일·기동 (java 레인)

#### 0-126 — VCS·기동 잔손: 부분 클론 판정의 worktree 설정 · 드라이브 판정 닫힌 실패 한 줄 (대기 행 — 번들 91 리뷰 M7·M14)
- 대상: `core/vcs/WorkingCopy.java`(`refusePartialClone`) · `cli/Batch.java`(`remoteDir`) · `WorkingCopyTest` · `BatchCliTest`
- 사실 ①·변경 ①·시험 ①(M7 + 점검 4차 C2-03): 묶음 ④ 의 「0-126 ①」 절 — 같은 행·한 커밋이다
- 사실 ②(M14): `Batch.remoteDir:149` → `LocalFiles.network:136` → `PathRules.remoteDrive` → `driveType:135` 이 `sun.nio.fs` 를 못 열면(매니페스트 `Add-Opens` 없이 `java -cp` 로 띄움) `IllegalStateException(DRIVE_FAIL)` 을 던진다. `Serve.call:47` 과 `Batch.open:110` 이 그 호출을 try 밖에서 해 picocli 스택으로 끝난다
- 변경 ②: `remoteDir` 를 판정 주입 꼴로 나누고 닫힌 실패를 사유로 돌려준다(두 호출 자리는 그대로 — `[오류] <사유>` + 끝 코드 2)
  ```java
  /** 8-23(G-11) — --data-dir·--profiles-dir 가 네트워크·장치 경로면 그 사유, 아니면 null(Serve 도 쓴다) */
  static String remoteDir(Path dataDir, Path profilesDir) {
      return remoteDir(dataDir, profilesDir, kr.ejg.toolbox.core.fs.LocalFiles::network);
  }

  /** 0-126 — 드라이브 종류를 못 물으면(닫힌 실패 IllegalStateException) 스택 대신 그 사유 한 줄 */
  static String remoteDir(Path dataDir, Path profilesDir, java.util.function.Predicate<String> network) {
      try {
          if (dataDir != null && network.test(dataDir.toString())) {
              return "네트워크·장치 경로는 받지 않는다 — 로컬 드라이브만: --data-dir";
          }
          if (profilesDir != null && network.test(profilesDir.toString())) {
              return "네트워크·장치 경로는 받지 않는다 — 로컬 드라이브만: --profiles-dir";
          }
          return null;
      } catch (IllegalStateException e) {
          return e.getMessage();
      }
  }
  ```
- 시험(먼저) ②: `BatchCliTest.driveCheckFailureIsOneLine` — `Batch.remoteDir(Path.of("data"), Path.of("profiles"), s -> { throw new IllegalStateException("닫힌 실패"); })` → `"닫힌 실패"`(전 — 예외가 그대로 나온다: 3인자 메서드가 없어 컴파일 RED) · `Batch.remoteDir(Path.of("data"), null, s -> false)` → null · `s -> true` → `--data-dir` 사유
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='WorkingCopyTest,BatchCliTest,MainTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 0-128(`WorkingCopy` 같은 파일)

#### 0-127 — 결과 쓰기: 예약 장치 이름(`NUL`·`COM1` …)인 상대 경로 (설계 76 §4 후보 → 확인)
- 대상: `core/fs/PathRules.java`(`relOk` · 새 상수) · `LocalFilesTest`
- 사실: `PathRules.relOk:52-62` 는 빈 마디·`..`·끝 점·끝 공백·금지 글자만 본다. 마디가 Windows 예약 장치 이름(`NUL`·`CON`·`PRN`·`AUX`·`COM1`~`9`·`LPT1`~`9`, 확장자가 붙어도)이면 `writeResult:475` 의 `Files.write` 가 파일이 아니라 장치에 쓴다 — 「썼다」 고 답하는데 결과 폴더에 파일이 없다
- 변경: 상수 `private static final Pattern DEVICE = Pattern.compile("(?i)(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?");` · `relOk` 의 마디 조건 끝에 `|| DEVICE.matcher(seg).matches()`(복잡도 +1 — 기준선 줄 없음, 15 아래). `LocalFiles.checkRel` 의 거절 글은 그대로
- 시험(먼저): `LocalFilesTest.relRefusesDeviceNames` — `LocalFiles.checkRel` 이 `"a/NUL"`·`"a/nul.txt"`·`"COM1"`·`"x/lpt9.log"`·`"Aux.java"` 에서 `Refused`(400)(전 — 지난다) · 지킴이 `"a/NULL.java"`·`"a/console.js"`·`"COM10.txt"`·`"a/com.java"`·`"nul_x.txt"` 는 지난다. OS 무관(글자 판정뿐 — CI ubuntu)
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='LocalFilesTest,CleanupRunnerTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 0-129 — 경로 검사: 파일마다 20ms — 금지 폴더 파일 키 판정을 조상마다 되풀이한다 (점검 4차 G4-01 Important)
- 대상: `core/fs/LocalFiles.java`(`refuseForbiddenReal:170-192` · 새 필드·도우미) · `core/fs/PathRules.java`(새 `same`) · `LocalFilesTest`
- 사실(점검 탐침): `lf.check("…/src/main/java/kr/ejg/toolbox/web/App.java")` ×2000 → 40.9초(20.4ms/회 — 같은 경로의 `toRealPath` 는 0.57ms) · 이 저장소 `src/main/java` 220 파일을 `lf.read` 로 전부 → 4,962ms(`Files.readAllBytes` 는 61ms). 5,000 파일 사업이면 읽기 한 바퀴에 약 110초가 더 든다 — `read` 를 파일마다 부르는 자리: `CheckRunner:184` · `CleanupRunner:115·180` · `AnalyzeRunner:137` · `LoggerShape:95` · `PackageTypes:82` · `ReturnTypes:93` · `AnalyzeRoutes:411`. 번들 91 `0-121` 이 `refuseForbiddenReal` 끝에 넣은 `for (Path f : forbidden) PathRules.sameOrInside(near, f)` 가 금지 폴더 7개 × 조상 전부에 `isSameFile` 을 부른다(`forbidden` 에 `WINDIR`=`SystemRoot` · `ProgramFiles`=`ProgramW6432` 가 겹쳐 든다)
- 변경:
  1. `PathRules` — `static boolean same(Path a, Path dir)`(`Files.exists(a) && Files.isSameFile(a, dir)`, `IOException` 은 거짓, `dir` 이 없으면 거짓). `sameOrInside` 는 그대로 둔다(다른 자리가 쓴다)
  2. `LocalFiles` — 확인한 폴더를 잠깐 기억한다:
     ```java
     /** 0-129(G4-01) — 파일 키로 「금지 폴더가 아니다」 를 확인한 폴더(뿌리 포함). 10초 뒤 통째로 버린다 — subst 는 드라이브 글자가 가리키는 곳을 바꿀 수 있다 */
     private final Set<Path> clean = java.util.concurrent.ConcurrentHashMap.newKeySet();
     private volatile long cleanBorn = System.nanoTime();
     private static final long CLEAN_TTL_NANOS = 10_000_000_000L;

     /** near 나 그 조상이 금지 폴더와 같은 파일인가 — 조상으로 올라가다 이미 확인한 폴더를 만나면 멈춘다. 일반 파일은 폴더와 같은 파일일 수 없어 안 잰다 */
     private boolean forbiddenByKey(Path near) {
         if (System.nanoTime() - cleanBorn > CLEAN_TTL_NANOS) {
             clean.clear();
             cleanBorn = System.nanoTime();
         }
         List<Path> walked = new ArrayList<>();
         for (Path a = near.toAbsolutePath(); a != null && !clean.contains(a); a = a.getParent()) {
             if (Files.isDirectory(a)) {
                 if (sameAsForbidden(a)) {
                     return true;
                 }
                 walked.add(a);
             }
         }
         clean.addAll(walked);
         return false;
     }

     private boolean sameAsForbidden(Path a) {
         for (Path f : forbidden) {
             if (PathRules.same(a, f)) {
                 return true;
             }
         }
         return false;
     }
     ```
     `refuseForbiddenReal` 끝의 `for` 를 `if (forbiddenByKey(near)) { throw new Refused(400, "시스템·서버 폴더는 다루지 않는다"); }` 로. `forbidden` 을 만드는 자리에서 겹치는 경로를 뺀다(`distinct` — 차례 유지)
- 시험(먼저): `LocalFilesTest.checkIsCheapAfterTheFirst` — 깊이 여덟 임시 폴더의 파일 하나를 `check` 2000번 → `assertTimeout(Duration.ofSeconds(10), …)`(전 약 40초) · **판정은 그대로**: 깨끗한 파일을 먼저 `check` 한 뒤 같은 부모 아래 금지 폴더(프로필 폴더·`data`) 안 경로 → 400(기억이 금지 폴더를 지나치지 않는다) · 기존 금지 폴더 사례(대소문자·끝 점·8.3 이름·`..`·ADS — 점검자가 지금 맞다고 확인) 전부 초록
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='LocalFilesTest,FsRoutesTest,CheckRunnerTest,CleanupRunnerTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): 확인 뒤 10초 안에 `subst` 를 다시 걸어 같은 글자 경로를 금지 폴더로 돌리는 것은 못 본다(사람이 할 것 ⑫ 에 한 줄)
- 의존: 0-127

#### 0-130 — 프로필 백업 가림: BMP 밖 글자 뒤의 password 키 자리 (점검 4차 G4-02 Important)
- 대상: `web/ProfileRoutes.java`(`passwordKeys:242-254`) · `ProfileRoutesTest`
- 사실(점검 탐침): `# 🙂 note` 줄 + `connections:` / `- id: dev` / `password: pw-probe-1` → 코드 검사 저장이 늘 400(`keys=[62]`, 정규식 자리 63) — `name: 𠮷p`(U+20BB7)·`name: "🙂"` 가 password 앞에 있어도 같다. `passwordKeys` 가 파서의 글자 자리(`getCharOffset()` — 코드포인트로 센다)를 정규식의 `m.start()`(UTF-16 단위)와 그대로 견준다(번들 91 `0-122`). 누출은 없다(`leaked` 가 막는다) — 사유 글이 엉뚱한 곳을 가리키고 저장이 막힌다
- 변경: `out.add((int) jp.currentTokenLocation().getCharOffset());` 를 `out.add(yaml.offsetByCodePoints(0, (int) jp.currentTokenLocation().getCharOffset()));` 로(BMP 안 글자뿐이면 값이 같다). javadoc 에 「파서 자리는 코드포인트 — UTF-16 자리로 옮긴다(0-130)」
- 시험(먼저): `ProfileRoutesTest` — 세 프로필 꼴(이모지 주석 · BMP 밖 한자 이름 · 따옴표 이모지 이름)로 코드 검사 저장이 200 이고 백업 파일에 `pw-probe-1` 이 없다(전 — 400) · `ProfileRoutes.passwordKeys(글)` 의 자리가 `PASSWORD_KEY` 정규식의 `m.start()` 와 같다 · 지킴이(점검자 확인 — 22 꼴 누출 0): 흐름 꼴·따옴표 키·둘째 문서·블록 스칼라·주석 처리한 접속에서 값이 안 남는다 · `common.password: false` 규칙은 안 건드린다
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='ProfileRoutesTest,ProfilePasswordTest,ProfileStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 0-131 — 드라이브 판정 잔손: 프로필 읽기의 사유 · 없는 드라이브의 드라이브 상대 경로 (점검 4차 G4-05 · G4-06)
- 대상: `core/profile/Profile.java`(`local:194-199`) · `core/fs/PathRules.java`(`remoteDrive:118-122`) · `ProfileStoreTest` · `LocalFilesTest`
- 사실(점검 탐침): Add-Opens 없이 뜬 프로세스에서 경로 칸이 있는 프로필을 읽으면 「119줄 1칸 근처 YAML 형식이 틀렸다」(기대 「드라이브 종류를 확인할 수 없다 — run.bat…」 — `network()` 의 `IllegalStateException` 이 레코드 생성자 안에서 나고 `ProfileStore.reason:141` 은 원인이 `IllegalArgumentException` 일 때만 글을 꺼낸다) · `LocalFiles.network("Q:foo")`(Q: 없음) → `java.io.IOError`(「Unable to get working directory of drive 'Q'」 — `toAbsolutePath()` 가 던지는데 `InvalidPathException` 만 잡는다. 닿는 길: `Profile.local`(output.dir·templateDir·customRules) · `Batch.remoteDir` · `MakeTemplates:55`)
- 변경: `Profile.local` — `network(…)` 호출을 감싸 `IllegalStateException` 을 같은 글의 `IllegalArgumentException` 으로 바꿔 던진다(원인으로 붙인다) · `PathRules.remoteDrive` — `catch (InvalidPathException | java.io.IOError e) { return false; }`(SpotBugs·ErrorProne 이 `Error` 잡기를 막으면 `toAbsolutePath()` 대신 `raw` 글의 머리 두 글자(`X:`)로 뿌리를 구한다 — 드라이브 상대 경로도 같은 드라이브 글자다)
- 시험(먼저): `LocalFilesTest` — `LocalFiles.network("Q:foo")`·`network("Q:")` 가 예외 없이 거짓(없는 드라이브 글자는 `File.listRoots()` 에 없는 것을 골라 쓴다 — Windows 가 아니면 `assumeTrue` 로 건너뛴다) · 지킴이(점검자 확인) `Q:\x`·`C:`·`C:foo`·`/c/x`·상대 경로·빈 글은 거짓, `\\?\C:\x`·`//./C:/x` 는 참 · `ProfileStoreTest` — `Profile` 의 경로 칸 판정이 `IllegalStateException("닫힌 실패")` 를 내는 꼴을 주입할 길이 없으면(`network` 는 static) `Profile.local` 을 판정 주입 겹침(`local(String, Predicate<String>)`)으로 나눠 그 겹침을 잰다: 판정이 ISE 를 던지면 `IllegalArgumentException` + 같은 글
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='LocalFilesTest,ProfileStoreTest,ProfileRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 0-129(`PathRules` 같은 파일)

#### 8-25 — 배치 `--docs`: 문서를 만들고도 끝 코드 1 — 「만들었나」 를 파일 이름 머리로 잰다 (점검 4차 G4-03 Important)
- 대상: `core/deliverable/DeliverableService.java`(`Result:66` · `build` 의 파일 넣는 자리) · `cli/DocCommands.java`(`skippedChosen:112-128`) · `DocCliTest` · `DeliverableServiceTest`
- 사실(점검 탐침): `chosen=["02"]`, `files=["…/tabledef.xlsx"]`, `skipped=["02 — <채우기 알림>"]` → `skippedChosen` 이 참(끝 코드 1 — 기대 0). 매핑의 `file` 을 사용자 양식 이름으로 바꾼 실행에서(매핑 안내 글이 그렇게 하라고 한다 — `Mapping:94`) 그 문서에 채우기 알림(겹친 머리·잘린 칸 — `DeliverableService:133` 이 건너뜀 통로에 `02 — …` 로 싣는다)이 하나라도 있으면 문서를 만들고도 실패로 끝난다. 번들 91 `8-24` 가 「만들었나」 를 파일 이름이 `<번호>_` 로 시작하는지로 쟀다
- 변경:
  1. `DeliverableService.Result` — 칸 `List<String> made`(만든 문서 번호, 차례대로)를 끝에 더한다(불변 사본). `build` 에서 문서 `no` 의 파일을 `files` 에 넣는 자리마다 `made.add(no)` 한 줄(`build(8)` 은 기준선 22 — 분기를 더하지 않는다). `new Result(…)` 자리는 한 곳 — 응답 JSON 에 `made` 가 실린다(화면은 안 쓴다)
  2. `DocCommands.skippedChosen` — 「고른 번호가 `made` 에 없고 건너뜀에 그 번호 줄이 있다」 로. 파일 이름은 안 본다
- 시험(먼저): `DocCliTest`(또는 `skippedChosen` 을 package-private 로 열어 직접) — 사실 줄 응답(`made=["02"]`) → 거짓(전 — 참) · `made=[]` + `skipped=["08 — …"]`, chosen `["08"]` → 참(지킴이) · `DeliverableServiceTest` — 매핑 file 을 `tabledef.xlsx` 로 바꾼 빌드의 `Result.made()` 에 `02` 가 있다 · 건너뛴 문서 번호는 `made` 에 없다
- 의도된 골든 변화: 없음(`Result` 를 통째 얼린 골든이 있으면 `made` 한 줄 — 갱신하고 이력에)
- 검증: `bash scripts/mvn.sh -q test -Dtest='DocCliTest,BatchCliTest,DeliverableServiceTest,DeliverableRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 2-58(`DeliverableService` 같은 파일)

### 묶음 ② 코드 정리 (corpus 레인)

#### 5-147 — 코드 정리 잔손: `cb!!(x)` · 로거 앞 `getClass()` (대기 행 — 번들 91 리뷰 M10·M11)
- 대상: `core/check/ConsoleArgs.java`(`nonNull`) · `core/check/LogFix.java`(`earlyInit` 인스턴스 쪽) · `CleanupTest`
- 사실: `ConsoleArgs.nonNull:128` 은 `!` 앞 글자가 낱말·`)`·`]` 일 때만 호출로 본다 — `cb!!(x)` 는 둘째 `!` 앞이 `!` 라 호출이 아니고, `console.log(cb!!(1));` 이 부수 효과 없는 줄로 지워진다 · `LogFix.earlyInit:266-274` 인스턴스 쪽이 로거 앞 인스턴스 필드 초기화식·초기화 블록의 수신자 없는·`this.` 호출을 전부 세어 `private final String n = getClass().getSimpleName();` 만 있어도 건너뛴다(`getClass()` 는 `Object` 의 final 메서드 — 이 클래스 코드를 안 부른다)
- 변경:
  1. `ConsoleArgs.nonNull` — 앞으로 건너뛸 때 이어진 `!` 도 건너뛴다: `while (j >= 0 && (Character.isWhitespace(s.charAt(j)) || s.charAt(j) == BANG)) { j--; }`. javadoc 에 「`cb!!(x)` 처럼 겹친 단언도(5-147)」
  2. `LogFix.earlyInit` — 끝 줄의 `anyMatch(c -> c.getScope().isEmpty() || c.getScope().get().isThisExpr())` 를 `anyMatch(LogFix::ownCall)` 로, 새 `private static`:
     ```java
     /** 수신자 없는·this. 호출 — getClass() 는 뺀다(Object 의 final 메서드라 이 클래스 코드를 안 부른다, 5-147) */
     private static boolean ownCall(MethodCallExpr c) {
         boolean own = c.getScope().isEmpty() || c.getScope().get().isThisExpr();
         return own && !("getClass".equals(c.getNameAsString()) && c.getArguments().isEmpty());
     }
     ```
     **람다·익명 클래스 몸의 호출은 지금처럼 센다**(3절 결정 — 바로 부르는 꼴 `Optional.of(x).map(v -> init()).get()` 이 있어 초기화 때 안 돈다고 못 박을 수 없다)
- 시험(먼저): `CleanupTest` 의 5-138 블록(`d1` 사례) 아래에 — ts·tsx 로 `"function f() {\n  console.log(cb!!(1));\n  console.log(!!(a));\n}\n"` → 2줄은 남고(`줄 2: ` + `ConsoleArgs.SIDE_EFFECT`, 전 — 지워진다) 3줄은 지워진다(겹친 부정은 호출이 아니다) · `logSkipsEarlyInitOfOtherShapes` 끝에 — `class C3 { private final String name = getClass().getSimpleName(); private final Logger log = LoggerFactory.getLogger(C3.class); void m() { System.out.println("x"); } }` → `log.debug("x");` 로 바뀐다(전 — `EARLY_INIT` 건너뜀) · 지킴이 `class C4 { private final Runnable r = () -> init(); private final Logger log = …; … }` 는 여전히 `EARLY_INIT` 건너뜀
- 의도된 골든 변화: `cleanup-*` corpus 골든에서 로그 고침 건너뜀(`EARLY_INIT`)이 **줄고** 고침이 그만큼 는다 · console 삭제는 줄 수 있다(`!!(` 호출) — `V-71a` 에서 수를 이력에
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupRunnerTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): 람다·익명 클래스 몸의 호출은 여전히 건너뛴다(안전한 쪽)
- 의존: S0
- **덧붙임(점검 4차 E4-04 · E4-09 — 같은 두 파일, 이 행에서 같이 친다)**:
  - 사실: 로거보다 먼저 도는 초기화가 **못 보는** 길 셋(걸리면 로딩·생성 때 NPE — 건너뛰어야 하는데 고친다): ① 로거 앞 `static final List<String> NAMES = Stream.of("a").map(F4::conv).toList();` + `conv` 안 println → `log.debug("conv")`(기대 `EARLY_INIT`) ② 로거 앞 `static final int X = Holder.V;` + 중첩 `Holder { static final int V = compute(); … println }` → 고침(기대 `EARLY_INIT`) ③ 인스턴스 로거 앞 `private final Inner in = new Inner();` + `Inner() { report(); }` + `report()` 안 println → 고침(기대 `EARLY_INIT`). `staticEarly:288-293` 은 `ObjectCreationExpr`·`MethodCallExpr` 만, 인스턴스 쪽 `:270-273` 은 수신자 없는·`this.` 호출만 본다 · ``console.log(tag<string>`x`);``(ts) 가 지워진다(기대 `SIDE_EFFECT` — `5-138` 이 `call` 에는 `>` 를 넣고 `tagged:91-99` 에는 안 넣었다)
  - 변경: `staticEarly` 의 `anyMatch` 에 두 가지를 더한다 — `m.findAll(MethodReferenceExpr.class)` 가 비지 않다 · `m.findAll(FieldAccessExpr.class)` 가운데 scope 가 `local`(같은 파일 형식 이름)인 `NameExpr` 인 것이 있다. 인스턴스 쪽 `earlyInit` 에 — `m.findAll(ObjectCreationExpr.class)` 가운데 형식 이름이 같은 파일 형식인 것이 있다(같은 파일 형식 이름 집합은 `staticEarly` 의 `local` 식을 `private static Set<String> localTypes(TypeDeclaration<?> t)` 로 빼서 같이 쓴다). 분기가 15 를 넘으면 가지마다 `private static boolean` 으로 · `ConsoleArgs.tagged` 의 앞 글자 조건에 `p == '>'` 를 더한다
  - 시험: `CleanupTest.logSkipsEarlyInitOfOtherShapes` 에 세 꼴 → 글 불변 + `EARLY_INIT` 건너뜀(전 — `log.debug` 로 바뀐다) · ``console.log(tag<string>`x`);`` → 남는다(`SIDE_EFFECT`)
  - 의도된 골든 변화: `cleanup-*` 의 로그 고침 건너뜀이 이 조각으로는 **는다**(위 `getClass()` 조각으로는 준다 — 둘의 수를 따로 이력에)

#### 5-153 — console·debugger 삭제: debugger 에는 「앞 줄이 문장 끝」 가드가 없다 (점검 4차 E4-01 Critical — 고친 사본의 뜻이 바뀐다)
- 대상: `core/check/Cleanup.java`(`deleteDebugger:536-562` · 새 `hanging`) · `CleanupTest`
- 사실(점검 탐침 — 셋 다 debugger 줄이 지워져 **다음 문장이 if 의 몸이 된다**, 건너뜀 없음):
  - (가) `z/a.js` `if (/'/.test(u) || u == "http://x")`⏎`debugger;`⏎`next();` — 번들 91 `5-128` 의 `lineCut` 이 끊긴 따옴표 뒤 문자열 속 `//` 부터 비워 줄 끝 `)` 가 사라지고 `BRACELESS` 가 안 맞는다(감싼 JSP 도 같다)
  - (나) `z/r1.jsp` `<script>` 안 `if (/^https?:\/\//.test(u))`⏎`debugger;`⏎`go(u);` — 정규식 리터럴의 `\/\/` 가 줄 주석으로 떼인다(`5-137` 이 jsp·html 에서 새로 드러냈다, `.js` 는 옛것)
  - (다) `z/g1.jsp` `if (x)`⏎`<% /* note */ %>`⏎`debugger;`⏎`next();` — 고침(`bare`)은 주석뿐인 스크립틀릿의 `<%  %>` 를 남겨 앞 줄이 그것이 된다
  - `deleteConsole:586-590` 은 「앞 줄이 문장 끝(`;` `{` `}` · `<script …>`)이 아니면 건너뜀」 가드가 있어 같은 세 입력에서 건너뛴다. `deleteDebugger` 에는 `BRACELESS` 뿐이다
- 변경: `deleteDebugger` 의 `else if (BRACELESS.matcher(previousCode(sl, i)).find()) { … }` 가지를 사유 도우미로 바꾼다(`deleteConsole(4)` 는 기준선 19 — **안 고친다**. 같은 판정이 두 자리에 있는 것은 기준선 때문이다 — 도우미 javadoc 에 적는다)
  ```java
  /**
   * 5-153(E4-01) — 앞 줄(주석 뗀 글)이 문장 끝이 아니면 그 사유, 끝이면 null: 중괄호 없는 제어문 머리 · 이어지는 식·잘린 줄
   * (deleteConsole 의 두 가드와 같은 판정 — 그 메서드는 기준선이라 식을 그대로 둔다)
   */
  private static String hanging(String prev) {
      if (BRACELESS.matcher(prev).find()) {
          return "중괄호 없는 제어문 몸체일 수 있음";
      }
      boolean ends = prev.isEmpty() || prev.endsWith(";") || prev.endsWith("{") || prev.endsWith("}") || ConsoleArgs.scriptOpen(prev);
      return ends ? null : "앞 줄이 문장 끝이 아니다(이어지는 식일 수 있음)";
  }
  ```
  `deleteDebugger` — `String hang = hanging(previousCode(sl, i));` 를 `zone` 옆에 두고 가지를 `else if (hang != null) { skips.add(new Skip(DELETE, "줄 " + line + ": " + hang)); }` 로
- 시험(먼저): `CleanupTest.debuggerNeedsAStatementEndBefore` — 세 입력(js · jsp · jsp)에서 글 불변 + 건너뜀 사유(전 — debugger 줄 삭제) · jsx `<pre>`⏎`debugger;`⏎`</pre>` → 건너뜀(E-13 의 k1 꼴 — 앞 줄이 `>` 끝) · 지킴이 — `foo();`⏎`debugger;` 삭제 · `function f() {`⏎`debugger;`⏎`}` 삭제 · `<script>`⏎`debugger;` 삭제 · `if (x) // c`⏎`debugger;` 는 지금처럼 「중괄호 없는 제어문」 건너뜀
- 의도된 골든 변화: `cleanup-*` corpus — debugger 삭제가 **줄고** 건너뜀이 는다(세미콜론 없는 js — 안전한 쪽. 수를 이력에)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupRunnerTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 5-154 — default 옮기기: default 가 안쪽에서 선언한 이름과 뒤 case 의 지역 이름 (점검 4차 E4-02 Important — 컴파일이 깨진다)
- 대상: `core/check/SwitchFix.java`(`laterLocal:146-156`) · `CleanupTest`
- 사실(점검 탐침): `z/P3.java` `default:` 안 `try { g(); } catch (RuntimeException e) { return -1; } break;` + 뒤 `case 2: String e = "x"; return e.length();` → default 가 case 2 뒤로 옮겨지고 건너뜀이 없다 — 옮긴 뒤 catch 매개변수 `e` 가 case 2 의 지역 `e` 범위 안이라 「이미 정의된 변수」. `laterLocal` 은 default 안 `NameExpr`(쓰임)만 본다(`5-142` 덜 고침 — `worse` 는 구문 오류만 잡는다)
- 변경: `laterLocal` 의 끝 판정에 default 가 **선언한** 이름을 더한다
  ```java
  java.util.Set<String> own = new java.util.HashSet<>();
  d.findAll(com.github.javaparser.ast.body.VariableDeclarator.class).forEach(v -> own.add(v.getNameAsString()));
  d.findAll(com.github.javaparser.ast.body.Parameter.class).forEach(p -> own.add(p.getNameAsString())); // catch·람다 매개변수
  d.findAll(com.github.javaparser.ast.expr.TypePatternExpr.class).forEach(p -> own.add(p.getNameAsString()));
  return !names.isEmpty() && (d.findAll(com.github.javaparser.ast.expr.NameExpr.class).stream().anyMatch(n -> names.contains(n.getNameAsString()))
          || own.stream().anyMatch(names::contains));
  ```
- 시험(먼저): `CleanupTest` 의 5-142 사례(`P3`) 옆 — 사실 줄 꼴 → 글 불변 + `DECL` 건너뜀(전 — 옮긴다) · 람다 매개변수 꼴(`default: list.forEach(e -> g()); break;` + 뒤 `case 2: String e = …`) 도 건너뜀 · 지킴이(점검자 확인) — 뒤 case 의 `int n` 과 default 의 `n = 9;` 는 `DECL` · 이름이 안 겹치면 지금처럼 옮긴다
- 의도된 골든 변화: `cleanup-*` 0 기대 · `CleanupCompileCorpusTest` 컴파일 실패는 0 그대로
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 5-155 — 값 고침: 필드로 가려진 상수 · 초기화 블록 안 지역 (점검 4차 E4-03 Important — 조용히 NPE 가 될 수 있다)
- 대상: `core/check/ValueAst.java`(`shadowed:208-217` · `staticFinal:220-224`) · `ValueAstTest` · `CleanupTest`
- 사실(점검 탐침): (가) 바깥 `static final String name = "k";` + 중첩 `static class Item { String name; boolean is(String s) { return s.equals(name); } }` → `return name.equals(s);` 로 뒤집힌다(`name` 은 중첩 클래스의 인스턴스 필드 — null 일 수 있다. 기대 글 불변) (나) 인스턴스 초기화 블록 `{ String KEY = System.getProperty("x"); String s = "a"; ok = s.equals(KEY); }` + 필드 `static final String KEY = "k"` → `ok = KEY.equals(s);`(지역 `KEY` 가 가린다 — 기대 글 불변). `staticFinal` 은 「같은 파일 어느 클래스든」 첫 static final 을 집고, `shadowed` 는 `CallableDeclaration` 조상이 없으면 거짓이다(`5-139` 덜 고침)
- 변경:
  1. `staticFinal` — 쓰는 자리에서 형식 사다리를 올라 **처음 만난 같은 이름 필드**가 답이다:
     ```java
     private static Optional<VariableDeclarator> staticFinal(Node from, String x) {
         Optional<com.github.javaparser.ast.body.TypeDeclaration> t = from.findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class);
         while (t.isPresent()) {
             Optional<FieldDeclaration> f = t.get().getFieldByName(x);
             if (f.isPresent()) { // 5-155 — 가까운 형식의 같은 이름 필드가 가린다(static final 이 아니면 상수가 아니다)
                 return f.filter(d -> d.isStatic() && d.isFinal())
                         .flatMap(d -> d.getVariables().stream().filter(v -> v.getNameAsString().equals(x)).findFirst());
             }
             t = t.get().findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class);
         }
         return <지금 식 그대로 — 같은 파일 어느 클래스든(둘러싼 형식에 없을 때만)>;
     }
     ```
     (raw 형식 경고가 ErrorProne·PMD 에 걸리면 `TypeDeclaration<?>` 로 받는 꼴로 — 기존 `LogFix` 의 쓰임과 같게)
  2. `shadowed` — 조상 범위를 `CallableDeclaration`, 없으면 `InitializerDeclaration`(초기화 블록)으로: 매개변수 판정은 callable 일 때만, 지역 변수·패턴 변수 판정은 둘 다
- 시험(먼저): `ValueAstTest`(또는 `CleanupTest` 의 `P5` 사례 옆) — (가)(나) 글 불변(전 — 뒤집힌다) · 판정도 같이: 두 꼴에 `java.equalsLiteralFirst` 지적이 없다 · 지킴이(점검자 확인) — 매개변수 `KEY` 로 가려진 줄은 불변 · 안 가려진 상수는 앞세운다 · 같은 클래스의 `static final String` 상수 + 중첩 클래스 메서드(가리는 필드 없음)는 앞세운다 · `tb-ignore` 줄 불변
- 의도된 골든 변화: `check-*`·`cleanup-*` corpus 의 `java.equalsLiteralFirst` 지적·고침이 **줄 수 있다**(가려진 이름 — 수를 이력에)
- 검증: `bash scripts/mvn.sh -q test -Dtest='ValueAstTest,CleanupTest,JavaRulesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-152(`DeclaredTypes` 와 맞닿는다 — `patternNamed` 는 그 행이 고친다)

#### 5-156 — 마크업·개인정보 고침 잔손: 속성 값 안 `>` · 줄 안 홀수 따옴표 (점검 4차 E4-07 · E4-08)
- 대상: `core/check/MarkupFix.java`(`TAG_END:37` · `:188`) · `core/check/PiiFix.java`(`Quotes.inside:99-111`) · `CleanupTest`
- 사실(점검 탐침): `z/m1.tsx` `<a href="/x" target="_blank" title="a > b"`⏎`   rel="noopener">d</a>` → 첫 줄에 ` rel="noopener noreferrer"` 를 또 끼운다(다음 줄에 rel 이 있다 — 중복 속성 TS17001. `(?<!=)>` 가 따옴표 안 `>` 를 태그 끝으로 읽는다. 기대 「여러 줄 태그」 건너뜀) · `z/x1.xml` 매퍼 `/* it's */ SELECT A FROM T WHERE CARD_NO = 4111111111111111` → `CARD_NO = ************1111`(맨 숫자를 가려 SQL 이 깨진다 — 기대 `UNQUOTED` 건너뜀) · `z/x3.jsp` `<script>` 안 `var re = /'/; var n = 4111111111111111;` → `var n = ************1111;`. `Quotes.inside` 가 줄 끝까지 안 닫힌 따옴표도 「안」 으로 본다
- 변경:
  1. `MarkupFix` — `TAG_END.matcher(frag).find()` 자리를 「따옴표(`"…"`·`'…'`)·`{ … }` 밖의 `>`(앞 글자가 `=` 가 아닌 것)가 있나」 를 한 번 훑는 `private static boolean closes(String frag)` 로. 따옴표가 안 닫히면 거짓(여러 줄 태그로 본다 — 안전한 쪽)
  2. `PiiFix.Quotes.inside(to)` — 참을 주기 전에 그 따옴표가 **같은 줄에서 닫히는지** 본다(`to` 뒤에서 같은 따옴표를 역슬래시 탈출을 건너뛰며 찾는다). 안 닫히면 거짓(`UNQUOTED` 로 간다)
- 시험(먼저): `CleanupTest` — tsx 꼴 → 글 불변 + `MULTI_LINE` 건너뜀(전 — rel 중복) · 매퍼·jsp 두 꼴 → 글 불변 + `UNQUOTED`(전 — 가린다) · 지킴이(점검자 확인) — `onClick={() => go()}` 로 끝나는 여러 줄 태그는 건너뜀 · 한 줄 `<a target="_blank" title="a > b">` 는 rel 을 끼운다 · sql 의 `'4111…'` 은 가린다 · 설정 XML `<value>` 는 가린다
- 의도된 골든 변화: `cleanup-*` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 5-157a — 줄끝이 섞인 파일: 검사가 지적을 낸다 (설계 76 한계 E-15 → 행 — 검사 쪽)
- 대상: `core/check/Source.java`(여섯째 칸) · `core/check/FileRule.java:43-48` · `core/check/CheckRunner.java:189` · `CheckRulesTest`(또는 `CheckRunnerTest`)
- 사실(점검 4차 E — 같이 확인): `LocalFiles.Text:86` 은 `mixed`(줄끝 섞임)를 싣는데 `Source:10` 은 다섯 칸이라 버린다(`CheckRunner:189`). `FileRule:43-48` 은 프로필 기준과 `s.lineEnding()`(**다수 쪽**)만 견준다 — 다수 쪽이 프로필과 같은 섞인 파일은 `file.lineEnding` 지적이 0 이고, 그 규칙만 골라 정리해도 통일되지 않는다
- 변경:
  1. `Source` — 레코드에 `boolean mixed` 를 끝 칸으로 더하고, 지금 다섯 인자 꼴은 `mixed = false` 로 넘기는 생성자를 둔다(기존 `new Source(…)` 자리·시험이 그대로 컴파일된다)
  2. `CheckRunner:189` — `t.mixed()` 를 넘긴다
  3. `FileRule:45` — 조건을 `s.mixed() || !expected.equalsIgnoreCase(s.lineEnding())` 로. 섞임일 때 발췌 글은 `섞임 (기준 <기준>)`(새 글 — 지금 글 꼴을 읽고 같은 문체로). `apply(1)` 은 기준선 17 — 분기를 더하지 않게 판정을 `private static boolean off(Source s, String expected)` 로
- 시험(먼저): `new Source("a.java", "a\r\nb\nc\r\n", null, "CRLF", "java", true)` + 프로필 기준 CRLF → `file.lineEnding` 한 건(전 — 0, 여섯 인자 생성자가 없어 컴파일 RED) · `mixed=false` 면 0 · `LocalFiles.read` 로 읽은 섞인 임시 파일을 `CheckRunner` 가 돌려 지적이 나온다
- 의도된 골든 변화: corpus `check-*` 의 `file.lineEnding` 이 **는다**(섞인 파일 — 수를 이력에. 등급 B baseline 상한을 넘으면 7절)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,CheckRunnerTest,CleanupTest,CleanupRunnerTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 5-157b — 줄끝이 섞인 파일: 정리가 통일한다 (설계 76 한계 E-15 → 행 — 정리 쪽)
- 대상: `core/check/Cleanup.java`(`State.source:213-219` · eol 단계 `:311-329` · `changed:187`) · `core/check/CleanupRunner.java:123·195` · `CleanupRunnerTest`
- 사실: `5-157a` 뒤에는 검사가 지적을 내도 정리는 `CleanupRunner:123·195` 가 `t.mixed()` 를 버려 그 지적을 못 본다 — eol 단계가 안 돈다. 세 자리를 같이 안 고치면 조용히 틀린다(점검자 추적): 앞 단계의 재검사에서 `file.lineEnding` 이 빠져 `worse:459-463` 이 「다른 지적이 사라짐」 으로 모든 단계를 되돌린다 · 다수 쪽이 프로필과 같으면 `st.le` 가 그대로라 `changed:187` 이 거짓 — 파일을 안 쓴다
- 변경:
  1. `CleanupRunner:123·195` — `t.mixed()` 를 `Source` 여섯째 칸으로 넘긴다
  2. `Cleanup.State` — 섞임을 들고 다닌다(`State.source()` 가 만드는 `Source` 에 그 값). eol 단계는 재검사 `Source`(`:320`)를 `mixed=false` 로 만들고, 채택하면 상태의 섞임을 끈다
  3. `changed:187` — 「섞였다가 통일됨」(처음 섞임 참 → 끝 거짓)도 바뀐 것으로 센다
  - `worse(6)`(36)·`deleteConsole(4)`·`consoleEnd(3)`·`rebuildClause(2)`·`fixTsImports(4)` 는 기준선 — 안 고친다
- 시험(먼저): `CleanupRunnerTest` — 다수 CRLF·프로필 CRLF 인 섞인 파일(`a\r\nb\nc\r\n`)을 `file.lineEnding` 만 골라 미리보기 → 결과 글이 전부 CRLF 이고 「바뀜」 으로 센다(전 — 지적 0·안 바뀜) · 같은 파일에 console 삭제도 같이 고르면 두 고침이 다 남는다(전 단계 되돌림 없음) · 지킴이 — 안 섞인 파일은 지금과 같다 · `\r\r\n` java 파일 사례(5-144) 초록
- 의도된 골든 변화: `cleanup-*` corpus 의 줄끝 통일 건수가 **는다**(수를 이력에)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupRunnerTest,CleanupRoutesTest,LocalFilesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 사다리: 기존 `CleanupTest`·`CleanupRunnerTest` 단언이 셋 넘게 빨개지고 고치기 둘로 안 풀리면 이 행만 `wip/5-157b` 로 뺀다(`5-157a` 는 남는다 — 지적은 나오고 정리는 손으로)
- 의존: 5-157a

#### 5-158 — console·debugger 삭제: jsx·tsx 의 JSX 글 줄 (설계 76 한계 E-13 → 좁힘)
- 대상: `core/check/Zones.java`(`rawLines:113-140` · `zones:45-53` · `reason`) · `CleanupTest` · `ZonesTest`
- 사실(점검 4차 E — 같이 확인, 전부 실행): jsx·tsx 에서 태그 사이 글 줄을 코드로 보고 지운다 —
  | 입력(tsx) | 지금 |
  |---|---|
  | `{'\n'}`⏎`console.log(x);`⏎`{'\n'}`(`<pre>` 안) | 삭제(앞 줄이 `}` 끝) |
  | `const a = 1;`⏎`console.log(a);`⏎`</pre>`(`<pre>` 안 코드 글) | 삭제 |
  | `<p>`⏎`Call it like this;`⏎`console.log("hi");`⏎`</p>` | 삭제 |
  - 지금 맞는 것: `<pre>`⏎`console.log("hi");`⏎`</pre>` 는 건너뜀(앞 줄이 `>` 끝) · 한 줄 `<pre>{'\n'}console.log(x);{'\n'}</pre>` 는 건너뜀
- 변경(둘 다 `Zones` 안 — `deleteConsole(4)` 기준선 19 에 분기를 안 더한다):
  1. **`<pre>` 날글 구역을 jsx·tsx 에도** — `rawLines` 의 `MARKUP` 문(`:115`)에서 jsx·tsx 는 걸러지지만, 그 두 언어는 `Zones.js(...).code()` 가 참인 자리(문자열·템플릿 밖)의 `<pre` ~ `</pre` 줄을 날글(1)로 본다(`textarea` 는 안 넣는다 — JSX 의 textarea 는 닫는 태그 사이에 글을 안 둔다)
  2. **다음 줄이 `</` 로 시작** — `zones` 가 jsx·tsx 일 때, 주석 뗀 글에서 그 줄 다음의 비지 않은 줄이 `</` 로 시작하면 그 줄을 구역 2(본문)로 적는다(JS·TS 문장은 `</` 로 시작할 수 없다 — 오탐 없음). `Zones.reason` 이 구역 2 를 「태그 사이 글」 사유로 돌려준다(지금 `OUTSIDE_SCRIPT` 글을 그대로 쓰면 뜻이 안 맞는다 — jsx·tsx 용 새 글 `JSX 태그 사이 글일 수 있다`)
- 시험(먼저): `CleanupTest` — 위 표 세 꼴(tsx·jsx) → 글 불변 + 건너뜀(전 — 삭제) · 지킴이 — 보통 컴포넌트 안 `console.log(a);`⏎`return (`… 은 지금처럼 지운다 · `}`⏎`console.log(x);`⏎`</div>` 가 아닌 `}`⏎`console.log(x);`⏎`}` 는 지운다 · ts(JSX 없음)는 구역 변화 0 · `ZonesTest` — tsx 의 `<pre>` 줄 구역 1
- 의도된 골든 변화: `cleanup-*` corpus(jsx·tsx 표본) console 삭제가 **줄 수 있다**(수를 이력에)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,ZonesTest,CheckRulesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): `<pre>` 밖 태그 사이 글의 **가운데** 줄(앞·뒤가 다 글 줄)은 JSX 깊이 없이는 못 가린다
- 의존: 5-153 · 5-150(`Zones` 같은 파일)

### 묶음 ④ 프로그램 분석 — Java·JSP·VCS (corpus · java 레인)

#### 0-128 — VCS: 저장소 설정의 include · 네트워크 경로 (점검 4차 C2-H1 — 절대 규칙 1)
- 대상: 새 파일 `core/vcs/GitConfigGuard.java` · `core/vcs/WorkingCopy.java`(`require`) · `WorkingCopyTest`
- 사실(추적 — 탐침은 네트워크·파일 쓰기라 못 돌렸다): 받은 소스의 `.git/config` 는 남이 쓴 글이다. git 은 모든 명령에서 저장소 설정의 `[include] path`·`[includeIf …] path` 대상을 열고(`WorkingCopy.FILTER_LIST:79` 는 `--includes` 로 스스로도 따라간다 — 0-103), `core.excludesFile`·`core.attributesFile`·`mailmap.file`(log)·`objects/info/alternates`·`.git` 파일의 `gitdir:` 가 가리키는 경로도 연다. 그 값이 `//서버/공유/…`·`\\서버\공유\…` 면 Windows 가 SMB 로 나간다(절대 규칙 1 — 0-120 의 lazy fetch 와 같은 급). `Cli.command:64-72` 의 `-c` 덮기는 fsmonitor·서명만 끈다
- 변경:
  1. 새 `core/vcs/GitConfigGuard.java`(package-private final, 프로세스를 안 띄운다 — 파일 읽기만):
     ```java
     /**
      * 0-128(절대 규칙 1) — git 을 부르기 전에 저장소 설정 글을 자바로 읽어, git 이 스스로 여는 바깥 경로를 거절한다.
      * include·includeIf 구역은 통째 거절(대상 파일이 또 무엇을 가리킬지 모른다) · 경로를 여는 키(core.excludesfile·attributesfile·hookspath·worktree,
      * mailmap.file)의 값과 .git 파일의 gitdir·commondir·objects/info/alternates 줄이 네트워크 꼴(// 또는 \\ 로 시작)이면 거절.
      * 원격 주소(remote.*.url)는 안 본다 — 조회 명령(status·log·diff)은 원격에 안 닿는다
      */
     final class GitConfigGuard {
         static final String INCLUDE = "저장소 설정에 include 가 있다 — git 조회를 하지 않는다";
         static final String REMOTE_PATH = "저장소 설정이 네트워크 경로를 가리킨다 — git 조회를 하지 않는다";
         private static final long MAX = 1_048_576; // 설정 글 상한 — 넘으면 거절(읽지 않은 설정을 믿지 않는다)
         private static final Set<String> PATH_KEYS = Set.of("core.excludesfile", "core.attributesfile", "core.hookspath", "core.worktree", "mailmap.file");

         /** dotGit — 작업 사본의 .git(폴더 또는 gitdir: 파일). 걸리면 IllegalStateException(사유) */
         static void check(Path dotGit) { … }
         /** 설정 글 한 덩이 — 줄마다 구역 머리([이름 "하위"])·키 = 값 을 읽는다. 주석(# ;)·빈 줄은 건너뛴다 */
         static void checkText(String config) { … }
         static boolean network(String value) { … } // 앞뒤 공백·큰따옴표를 벗기고 / 를 \ 로 바꿔 \\ 로 시작하나
     }
     ```
     - `check`: `.git` 이 일반 파일이면 첫 줄 `gitdir: <경로>` 를 읽어 `network` 면 `REMOTE_PATH`, 아니면 그 폴더(상대면 `.git` 파일의 부모 기준)로 간다 · 그 폴더의 `commondir` 파일이 있으면 그 한 줄도 같은 판정 뒤 공통 폴더로 · `config`·`config.worktree`(있을 때)를 `checkText` · `objects/info/alternates`(있을 때)의 줄마다 `network` 면 `REMOTE_PATH`. 파일이 `MAX` 를 넘거나 못 읽으면(`IOException`) `IllegalStateException("저장소 설정을 읽을 수 없다 — git 조회를 하지 않는다")`. 글은 UTF-8 로 읽되 잘못된 바이트는 바꿔 읽는다(`new String(bytes, UTF_8)`)
     - `checkText`: 구역 머리는 `[` 로 시작하는 줄 — 이름은 `[` 뒤 첫 공백·`"`·`]` 앞까지를 소문자로. 이름이 `include`·`includeif` 면 `INCLUDE`. 그 밖 줄은 첫 `=` 앞을 키(소문자·strip)로, `구역.키` 가 `PATH_KEYS` 에 있고 값이 `network` 면 `REMOTE_PATH`. 한 줄에 `[core] excludesfile = …` 처럼 머리 뒤에 키가 이어지는 꼴도 본다(`]` 뒤 글을 키 줄로)
     - 메서드마다 복잡도 15 아래 — 줄 읽기는 도우미로 나눈다
  2. `WorkingCopy.require` — git 이면 `Cli.available` 다음, 돌려주기 전에 `GitConfigGuard.check(dotGit(root))`. `dotGit` 은 `detect` 의 루프처럼 부모로 올라가 처음 만나는 `.git` 경로(새 `private static` — `WorkingCopy` 는 기준선 줄 없음). git 을 부르는 public 진입점이 `require` 를 안 거치는 자리가 있으면(grep `Cli.Exe.GIT`) 그 자리 앞에도 같은 호출
- 시험(먼저): `WorkingCopyTest.gitConfigGuardRefusesIncludesAndRemotePaths`(임시 폴더에 `.git/config` 글만 쓴다 — git 이 없어도 돈다, OS 무관):
  - `[include]\n\tpath = ../x` · `[includeIf "gitdir:/a/"]\n path = y` · `[INCLUDE] path = z`(한 줄 꼴) → `INCLUDE`
  - `[core]\n excludesFile = //h/s/x` · `[core]\n\tattributesfile = "\\\\h\\s\\a"` · `[mailmap]\n file = //h/s/m` · `[core]\n hooksPath = \\\\h\\s` → `REMOTE_PATH`
  - `.git` 이 파일 `gitdir: //h/s/repo.git` → `REMOTE_PATH` · `.git/objects/info/alternates` 에 `//h/s/objects` 줄 → `REMOTE_PATH`
  - 지킴이(안 던진다): `[remote "origin"]\n url = //h/s/repo.git` · `[core]\n excludesFile = C:/x/.gitignore` · `# [include]` 주석 줄 · `[filter "lfs"]` · 빈 설정 · `.git` 파일이 `gitdir: ../.git/modules/a`(그 폴더에 깨끗한 config)
  - `WorkingCopy.changed(임시 저장소)` 가 include 든 저장소에서 `IllegalStateException`(`INCLUDE`) — git 이 PATH 에 없으면 `assumeTrue` 로 건너뛴다(기존 git 시험과 같은 꼴)
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='WorkingCopyTest,CheckRoutesTest,ArchitectureTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 사다리: `ArchitectureTest` 가 `core.vcs` 의 파일 읽기를 막으면(프로세스 자리 고정 시험) 그 시험의 허용 목록이 아니라 구현 자리를 본다 — `WorkingCopy.sizeInside` 가 이미 `Files` 를 쓴다
- 한계(이력에 · 사람이 할 것 ⑫ 에 한 줄): 전역·시스템 설정(`~/.gitconfig`)의 include 는 사용자 것이라 안 본다 · 저장소 설정에 include 를 쓰는 프로젝트는 git 조회가 안 된다(엄격한 쪽) · git 이 실제로 UNC include 를 여는지는 폐쇄망 실측 목록에
- 의존: S0

#### 0-126 — VCS·기동 잔손 ①: 부분 클론 판정 (위 묶음 ⑦ 의 `0-126` 과 한 행 — 사실 ①·변경 ①·시험 ①)
- 사실 ①(M7 + 점검 4차 C2-03): `WorkingCopy.refusePartialClone:187-194` 는 `configArgs("--local")` 만 읽고 키는 `extensions.partialclone`·`remote.*.promisor` 둘뿐이다 — `extensions.worktreeConfig` + `config.worktree` 에 든 promisor 키는 지난다 · git 은 `remote.<이름>.partialclonefilter` 만 있어도 그 원격을 promisor 로 쳐 lazy fetch 를 띄운다(점검 탐침 — 이 저장소 `.git/config` 에 남아 있던 `remote.zzprobe.partialclonefilter` 로 `git cat-file -e <없는 객체>` 가 fetch 를 띄우려 했다, `GIT_NO_LAZY_FETCH=1` 이면 조용). 둘 다 `GIT_NO_LAZY_FETCH` 를 모르는 옛 git(2.44 아래)에서만 샌다
- 변경 ①: `refusePartialClone` — 키 목록을 `filtersOff:166-168` 과 같은 꼴로 모은다(`--local` 은 실패하면 던지고, `--worktree` 는 `.filter(Cli.Result::ok)` 로 더한다) · 판정에 `key.startsWith("remote.") && key.endsWith(".partialclonefilter")` 를 더한다. 분기가 늘면 판정을 `private static boolean promisorKey(String key)` 로 뺀다
- 시험(먼저) ①: `WorkingCopyTest.gitRefusesPartialClone` 옆에 — 임시 저장소에 `git config remote.x.partialclonefilter blob:none` 만 넣고 `changed` → 「부분 클론 저장소」 거절(전 — 지난다) · `git config extensions.worktreeConfig true` + `git config --worktree remote.x.promisor true` → 거절(전 — 지난다). promisor 주소는 안 넣는다(네트워크에 안 닿는다)

#### 6-93a — 타입 풀기: 하위 패키지 import · 완전 이름 필드 · 후보 여럿의 명시 import (대기 행 6-93 ① + 점검 4차 C2-09·C2-10)
- 대상: `core/analyze/JavaGraph.java`(`Cls` 필드 `fqn` · `index` 의 한 줄 · `lookup` · `Field`) · `core/analyze/TypeBind.java`(`importsOther`) · `JavaGraphTest`
- 사실(점검 탐침):
  - `TypeBind.importsOther:154-160` 이 중첩 import 를 「패키지 머리 + 이름 꼬리」 로 어림해 `import a.sub.Page`(프로젝트 밖)를 프로젝트 `a.Page` 로 푼다 → `/l1.do stmts=[a.page.load literal]`(기대 빈 목록)
  - `JavaGraph.lookup:399-407` — 후보가 둘 이상이면 명시 import 가 프로젝트 밖이어도 전부 받는다: `app.legacy.CodeService`·`app.other.CodeService` + `import com.vendor.common.CodeService` → 두 문장 + `ambiguous`(기대 문장 없음) · `import a.Outer.Inner` + 후보 `a.Outer.Inner`(중첩)·`b.Inner` → 둘 다(기대 a 쪽만)
  - 필드 타입을 완전 이름으로 적은 꼴 `@Autowired app.legacy.CodeService legacy;` + `import com.vendor.common.CodeService` → `[]`(번들 91 전에는 `[legacy.code.list]` — 6-90 회귀). `Field.type` 이 스코프를 버린다(`index:369` `ct.getNameAsString()`)
- 변경:
  1. `Cls` 에 `String fqn;`(중첩까지 든 완전 이름) — `index` 에서 `c.fqcn` 을 적는 자리 옆에 `c.fqn = t.getFullyQualifiedName().orElse(c.fqcn);`
  2. `TypeBind.importsOther` — 어림을 버리고 `String imp = from.imports.get(simple); return imp != null && !imp.equals(cand.fqcn) && !imp.equals(cand.fqn);`
  3. `JavaGraph.lookup` —
     ```java
     private List<Cls> lookup(Cls from, String simple) {
         List<Cls> cands = byName.getOrDefault(simple, List.of());
         String imp = from.imports.get(simple);
         if (imp != null) { // 6-93a — 명시 import 가 있으면 그 완전 이름과 같은 후보만. 없으면 프로젝트 밖이다
             return cands.stream().filter(x -> imp.equals(x.fqcn) || imp.equals(x.fqn)).toList();
         }
         if (cands.size() <= 1) {
             return cands;
         }
         String want = from.pkg.isEmpty() ? simple : from.pkg + "." + simple;
         List<Cls> hit = cands.stream().filter(x -> x.fqcn.equals(want)).toList();
         return hit.isEmpty() ? cands : hit;
     }
     ```
  4. 완전 이름 필드 — `Field` 에 `String scope`(스코프 글, 없으면 `""`)를 더하고(`index:369` 에서 `ct.getScope().map(Object::toString).orElse("")`), 필드 타입을 푸는 자리(`resolve` 로 가는 `:369` 근처 `lookup(…, f.type())`)에서 스코프가 있으면 `byName` 후보 가운데 `fqn`·`fqcn` 이 `scope + "." + type` 과 같은 것만 쓰고 import 판정을 건너뛴다. `Field` 생성 자리는 한 곳이다. `JavaGraph` 에 메서드를 더하지 않는다(메서드 34 기준선) — 판정은 `TypeBind` 의 static 으로(`static List<JavaGraph.Cls> scoped(List<JavaGraph.Cls> cands, String scope, String type)`)
- 시험(먼저): `JavaGraphTest.explicitImportDecidesTheType` — 사실 줄 네 꼴(전 → 후): 하위 패키지 import → 문장 없음 · 후보 둘 + 프로젝트 밖 import → 문장 없음·`ambiguous` 없음 · 중첩 import → a 쪽 하나 · 완전 이름 필드 → `[legacy.code.list]` · 지킴이 — `import com.vendor.Page` → 빈 목록 · 중첩 import 후보 하나 → 풀림 · 같은 파일의 둘째 클래스 → 풀림 · 별 import·같은 패키지 → 지금처럼 받는다
- 의도된 골든 변화: `analyze-*` corpus 골든 — `ambiguous` 미해결이 **줄 수 있다**(명시 import 로 갈린다) · 문장이 **줄면** 그 클래스의 import 가 프로젝트 밖인지 `R-38` 이 본다
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,JpaIndexTest,AnalyzeRunnerTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 6-93b — 공통 DAO 방문 열쇠: 가운데 층 · 출처 자리 · id 둘 · 긴 id · 위임 호출 · 재대입 (대기 행 6-93 ② + 점검 4차 C2-01·05·06·07)
- 대상: `core/analyze/StmtArgs.java`(`key`·`origin` · `forwarded` 다시 씀 · `KEY_MAX` 삭제) · `JavaGraphTest`
- 사실(점검 탐침 — 전부 `StmtArgs.key:64-75`·`forwarded:78-86`·`origin:40-58`):
  | 꼴 | 지금 | 기대 |
  |---|---|---|
  | 가운데 층: `CommonService.getList(queryId, p) { return commonDAO.getList(queryId, p); }` → `CommonDAO.getList` 가 `sqlSession.selectList(queryId, p)`. 처리기가 `getList("a.list")`·`getList("b.list")` | `[a.list]` · 미해결 0(둘째가 조용히 빠진다) | `[a.list, b.list]` |
  | 위임: `Svc.update(vo)`→`Biz.update(vo)`→`UDao.update(vo)`, 처리기가 `svc.update(v0)` … `svc.update(v259)` | `depth` 미해결 + 문장 하나 | 문장 하나 · `depth` 없음 |
  | 재대입: `ns(String queryId, p) { queryId = "app." + queryId; return sqlSession.selectList(queryId, p); }` + `ns("selectUser", vo)` | `[selectUser literal]`(틀린 id) | 문장 없음 + `statement` 미해결 |
  | 같은 이름 지역 변수: `a() { String queryId = "foo.a"; return selectList(queryId, vo); }` · `b() { String queryId = "foo.b"; … }` 를 한 처리기가 | `[foo.a]` | `[foo.a, foo.b]` |
  | id 둘: `page(String listId, String countId, p)` — `page("a.list", "cmm.count")`·`page("b.list", "cmm.count")` | `b.list` 빠짐 | 셋 다 |
  | 103자 접두 + `.selectA`/`.selectB` | `selectA` 만 | 둘 다 |
  - 원인: 꼬리가 「SINKS 이름 호출의 첫 인자로 넘기는 첫 매개변수 하나」 의 출처 **글**(100자)이다 — 가운데 층은 꼬리가 없고, 위임(`update(vo)`)은 이름만 SINKS 라 `vo` 글마다 다시 걷고, 글이 같으면(다른 메서드의 같은 이름 변수) 한 열쇠다
- 변경(전부 `StmtArgs` — `JavaGraph` 는 안 고친다):
  1. `origin` — 지금 몸을 `private static At origin(JavaGraph.Cls c, MethodDeclaration m, Expression a, Iterator<Call> it)` 로 옮기고 공개 꼴은 `return origin(c, m, a, calls.iterator());`. 멈춤 조건에 재대입을 더한다: `if (idx < 0 || reassigned(mm, n.getNameAsString()) || !JavaGraph.varLiterals(cc, mm, n.getNameAsString()).isEmpty()) { break; }`
     ```java
     /** 그 매개변수가 메서드 안에서 대입 대상인가 — 값이 리터럴이 아니어도 부른 쪽 인자는 더는 그 값이 아니다(6-93b) */
     private static boolean reassigned(MethodDeclaration m, String name) {
         return m.findAll(com.github.javaparser.ast.expr.AssignExpr.class).stream()
                 .anyMatch(as -> as.getTarget() instanceof NameExpr t && t.getNameAsString().equals(name));
     }
     ```
  2. `key` — 꼬리를 「꼭대기 층이 넘긴 인자 가운데 callee 의 `String` 매개변수 자리마다, 그 인자가 닿는 문장 id 글」 로:
     ```java
     static String key(JavaGraph.Cls c, MethodDeclaration callee, Deque<Call> calls) {
         if (calls.isEmpty() || !Objects.equals(calls.peek().callee(), callee)) {
             return "";
         }
         Call top = calls.peek();
         List<Parameter> ps = callee.getParameters();
         StringBuilder tail = new StringBuilder();
         for (int i = 0; i < ps.size() && i < top.args().size(); i++) {
             if (STRING.equals(ps.get(i).getType().asString())) {
                 tail.append(idText(top, i, calls, forwards(callee, ps.get(i).getNameAsString())));
             }
         }
         return tail.toString();
     }

     /**
      * 꼭대기 층의 i 번째 인자가 닿는 문장 id 글 — 리터럴이면 값(문장 id 꼴일 때만), 지역 변수면 그 리터럴들. 못 닿으면: 그 매개변수를
      * 문장 실행 호출에 넘기는 감싸개(sink)일 때만 출처 자리(클래스#메서드) — 미해결이 부른 메서드마다 남는다. 아니면 빈 글(위임 호출은 한 번만 걷는다)
      */
     private static String idText(Call top, int i, Deque<Call> calls, boolean sink) {
         Iterator<Call> it = calls.iterator();
         it.next(); // 꼭대기 층은 top 자신 — 그 인자는 top.m() 안에 놓였다
         At at = origin(top.c(), top.m(), top.args().get(i), it);
         List<String> lits = at.arg() instanceof StringLiteralExpr sl ? List.of(sl.getValue())
                 : at.arg() instanceof NameExpr n ? JavaGraph.varLiterals(at.c(), at.m(), n.getNameAsString()) : List.of();
         List<String> ids = lits.stream().filter(StmtId::plain).toList();
         if (!ids.isEmpty()) {
             return "|" + String.join(",", ids);
         }
         return sink ? "|@" + at.c().fqcn + "#" + at.m().getNameAsString() : "";
     }

     /** callee 가 그 매개변수를 문장 실행 이름(JavaGraph.SINKS) 호출의 첫 인자로 그대로 넘기나 */
     private static boolean forwards(MethodDeclaration m, String param) {
         return m.findAll(MethodCallExpr.class).stream().anyMatch(mc -> JavaGraph.SINKS.contains(mc.getNameAsString())
                 && mc.getArguments().isNonEmpty() && mc.getArgument(0) instanceof NameExpr n && n.getNameAsString().equals(param));
     }
     ```
     `private static final String STRING = "String";` · `forwarded`·`KEY_MAX` 는 지운다(리터럴 값은 안 자른다). 클래스 javadoc 에 「방문 열쇠 꼬리는 String 매개변수 자리의 문장 id 리터럴(6-93b)」
- 시험(먼저): `JavaGraphTest.statementIdsThroughLayersAndWrappers` — 위 표 여섯 꼴(전 → 후). 위임 꼴은 `unresolved` 에 `depth` 가 없고 문장이 `u.update` 하나 · 지킴이(점검자가 지금 맞다고 확인한 것): 두 층 감싸개의 `foo.find`·`foo.find2` 둘 다 · 인자 자리 바뀜 `sel(vo, "foo.swap")` → `foo.swap` · 재귀 `rec("r.one")`·`rec("r.two")` 둘 다 · 상호 재귀 `mA`/`mB` → `m.a`·`m.b`(`depth` 없음) · 가변 인자 감싸개 · 같은 클래스 상수 `Q_FIND` → var. 기존 `statementIdPassedThroughACommonDao`(6-86) 그대로 초록
- 의도된 골든 변화: `analyze-*` corpus 골든 — 문장·CRUD 가 **는다**(가운데 층) · `depth` 미해결이 **준다**(위임). `depth` 가 **늘면** 사다리
- 사다리: corpus 에서 `depth` 가 느는 프로그램이 있으면(문장 id 가 아닌 리터럴 — 메시지 키 — 을 받는 유틸을 리터럴마다 다시 걷는 꼴) `idText` 의 리터럴 가지를 `sink` 일 때 또는 그 값이 색인된 문장 id(`.` 이 든 글)일 때로 좁힌다(계획 밖 결정 하나, 이력에 수)
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,AnalyzeRunnerTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): 가운데 층이 리터럴 아닌 id 를 넘기면 미해결은 처음 걸은 호출자에만 남는다 · `String` 이 아닌 형식(`CharSequence`·`Object`)의 id 매개변수
- 의존: 6-93a(같은 시험 파일)

#### 6-94 — 공통 DAO 가 프레임워크 메서드를 덮고 `super.` 로 넘기는 꼴 (점검 4차 C2-04)
- 대상: `core/analyze/JavaGraph.java`(`sink` 첫 가지) · `core/analyze/TypeBind.java`(새 static 도우미) · `JavaGraphTest`
- 사실(점검 탐침): `class CommonDAO extends EgovAbstractMapper(색인 밖) { List selectList(String queryId, Object p) { return super.selectList(queryId, p); } int insert(String queryId, Object p) { return super.insert(queryId, p); } }` · `@Repository UserDAO extends CommonDAO { list(vo) { return selectList("user.list", vo); } add(vo) { return insert("user.add", vo); } }` → `stmts=[]`·미해결 0(기대 `[user.add, user.list]`). `JavaGraph.sink:523-526` 이 `super.` 호출도 `methods(c, name, argc)` 로 재서 **자기 자신**(같은 이름·인자 수)을 보고 싱크가 아니라 하고, `walk:459` 의 super 대상은 색인 밖이라 빈다
- 변경: `sink` 의 `if (self)` 가지 — `super.` 호출은 자기 선언이 아니라 **조상 쪽** 선언으로 잰다
  ```java
  if (self) {
      boolean viaSuper = scope.isPresent() && scope.get().isSuperExpr();
      List<Target> declared = viaSuper ? TypeBind.inParents(c, this::lookup, p -> methods(p, name, argc)) : methods(c, name, argc);
      return isDao(c) && !jpaDao(c) && declared.stream().noneMatch(x -> x.method().getParameters().size() == argc);
  }
  ```
  `TypeBind` 에 `static <T> List<T> inParents(JavaGraph.Cls c, BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup, Function<JavaGraph.Cls, List<T>> of)` — `c.superName` 이 null 이거나 색인 밖이면 빈 목록, 아니면 `lookup.apply(c, c.superName)` 의 클래스마다 `of` 결과를 모은다. `Target` 이 `private record` 라 제네릭으로 받는다. `sink` 의 분기가 15 를 넘으면 `viaSuper` 식을 `TypeBind` 쪽 `static boolean superCall(Optional<Expression> scope)` 로 뺀다. `JavaGraph` 에 메서드를 더하지 않는다
- 시험(먼저): `JavaGraphTest.daoOverridesFrameworkSinkWithSuper` — 사실 줄 꼴 → 프로그램 `/u/list.do` 의 문장 `[user.add, user.list]`(전 `[]`) · 지킴이 — 조상이 색인 안이고 그 조상에 같은 이름 메서드가 있으면(`super.selectList` 가 프로젝트 메서드) 지금처럼 그 메서드를 걷는다 · `this.selectList("x.y", p)` 를 자기 선언이 덮으면 싱크 아님(지금과 같다)
- 의도된 골든 변화: `analyze-*` — 그 꼴 표본이 있으면 문장이 는다(점검자 corpus 실측 0 — 0 기대)
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-93b

#### 6-95 — 타입 수준 `@RequestMapping` 이 인터페이스·추상 조상에 (점검 4차 C2-02)
- 대상: `core/analyze/TypeBind.java`(`mappings` · 새 `head`) · `JavaGraphTest`
- 사실(점검 탐침): `@RequestMapping("/api/v1/users") interface UserApi { @GetMapping("/list") Object list(); }` + `@RestController class UserController implements UserApi { public Object list() {…} }` → `GET /list`(기대 `GET /api/v1/users/list`) · `@RequestMapping("/base") abstract class BaseCtl { @GetMapping("/list") abstract Object list(); }` + `SubCtl extends BaseCtl` → `GET /list`(기대 `/base/list`). `TypeBind.mappings:98·103` 이 클래스 머리를 늘 `c.decl` 로 준다 — 번들 91 `6-88` 전에는 프로그램이 없었고 지금은 틀린 URL 이다(URL 은 JSP 링크 맞춤·영향도의 열쇠)
- 변경: `mappings` 의 `SpringMappings.of(c.decl, …)` 두 자리를 `SpringMappings.of(head(c, ifaces, byName, lookup), …)` 로(한 번 구해 지역 변수로)
  ```java
  /** 6-95 — 클래스 머리(타입 수준 @RequestMapping)를 가진 선언: c, 없으면 조상 사다리(MAX_UP), 없으면 c·조상이 구현한 인터페이스(ifaces). 다 없으면 c(Spring 은 타입 계층에서 찾는다) */
  private static ClassOrInterfaceDeclaration head(JavaGraph.Cls c, Set<String> ifaces,
          java.util.function.Function<String, List<JavaGraph.Cls>> byName, BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
      Set<String> seen = new HashSet<>();
      JavaGraph.Cls cur = c;
      for (int up = 0; up <= MAX_UP && cur != null && seen.add(cur.fqcn); up++) {
          if (cur.decl.getAnnotationByName("RequestMapping").isPresent()) {
              return cur.decl;
          }
          cur = parent(cur, lookup);
      }
      for (String f : ifaces) {
          for (JavaGraph.Cls ic : byName.apply(f.substring(f.lastIndexOf('.') + 1))) {
              if (ic.fqcn.equals(f) && ic.decl.getAnnotationByName("RequestMapping").isPresent()) {
                  return ic.decl;
              }
          }
      }
      return c.decl;
  }
  ```
- 시험(먼저): `JavaGraphTest.typeLevelMappingOnInterfaceOrParent` — 두 꼴(전 → 후) + 물려받은 처리기 `SubCtl` 이 조상의 `own()`(`@GetMapping("/own")`)을 안 덮은 꼴 → `/base/own`(전 `/own`) · 지킴이 — c 에 머리가 있으면 c 것(조상 머리를 안 붙인다) · 머리가 어디에도 없으면 지금과 같다 · 기존 `mappingDeclaredOnInterfaceOrAbstractParent`(6-88) 초록
- 의도된 골든 변화: `analyze-*` — 프로그램 URL 이 바뀌는 표본은 0 기대(점검자 corpus 실측: 인터페이스·추상 클래스 매핑 0)
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): 인터페이스 default 메서드에 매핑이 있고 구현 클래스가 안 덮은 꼴은 프로그램이 안 생긴다(점검 후보 H2)
- 의존: 6-94

#### 6-96 — JPA 색인: 순환 상속에서 안 끝남 · 후보 하나의 명시 import (점검 4차 C2-14·C2-08)
- 대상: `core/analyze/JpaIndex.java`(`root` · `pick` 을 부르는 두 자리) · `core/analyze/JpqlPaths.java`(새 static 도우미) · `JpaIndexTest`
- 사실(점검 탐침): `@Entity class A extends B {}` · `@Entity class B extends A {}`(둘 다 `@Table` 없음) 또는 `@Entity class Selfish extends Selfish {}` → `JpaIndex.scan` 이 5초 안에 안 돌아온다 — `root:323-329` 의 `for (up = superEntity(cur); up != null; …)` 에 방문 집합이 없다(`superEntity` 의 `seen` 은 호출마다 새로 선다) · `legacy.CodeRepository extends JpaRepository<Code, Long>` + 컨트롤러 필드 `import com.vendor.common.CodeRepository` → `[legacy.CodeRepository.findAll jpa]`(기대 문장 없음) — `pick:541` 이 후보 하나면 import 를 안 본다(6-90 이 `JavaGraph.lookup` 만 막았다)
- 변경:
  1. `root` — 방문 집합: `Set<String> seen = new HashSet<>(); seen.add(c.fqcn);` · 루프 조건을 `up != null && seen.add(up.fqcn)` 로(메서드를 더하지 않는다 — `JpaIndex` 메서드 38 기준선)
  2. 후보 하나 + 명시 import 가 다른 FQCN — `JpqlPaths` 에 `static <T> List<T> importedOnly(List<T> cands, Function<T, JpaIndex.Cls> type, String simple, JpaIndex.From from)`(후보가 하나이고 `from != null` 이고 `from.imports().get(simple)` 이 null 이 아니며 그 후보의 `fqcn` 과 다르면 빈 목록, 아니면 `cands`)를 두고, `repos`(저장소 필드 타입 — `:158-166`)와 `entitiesNamed`(`:375`)가 `pick(…)` 결과에 그것을 씌운다. `byEntity`(JPQL 엔티티 **이름** — import 와 무관)·`lookup`(조상 — `named` 가 이미 잰다)은 그대로
- 시험(먼저): `JpaIndexTest.cyclicInheritanceEnds` — 두 순환 꼴을 `assertTimeout(Duration.ofSeconds(5), …)` 안에 훑고 엔티티 표가 지은 이름으로 나온다(전 — 시간 초과) · `JpaIndexTest.singleCandidateRespectsExplicitImport`(또는 `JavaGraphTest` — 저장소 필드 꼴) — 사실 줄 둘째 꼴 → 문장 없음(전 `findAll jpa`) · `p.VendorCodeRepo extends JpaRepository<Code, Long>` + `import com.vendor.domain.Code` → 엔티티 미해결(`jpaType`) · 지킴이 — import 가 그 후보의 FQCN 이면 풀린다 · 같은 패키지·별 import 는 지금처럼
- 의도된 골든 변화: `jpa-index.json`·`analyze-*` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='JpaIndexTest,JavaGraphTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-93a

#### 6-97 — 분석 잔손: `c:set` 컨텍스트 값 · 이름 빈 redirect 뷰 (점검 4차 C2-11·C2-12)
- 대상: `core/analyze/JspLinks.java`(`CTX_VALUE:35`) · `core/analyze/JavaViews.java`(`:66` 리터럴 가지) · `core/analyze/JavaGraph.java`(`views` — `:970-973` 한 줄) · `JspLinksTest` · `JavaGraphTest`
- 사실(점검 탐침): `<c:set var="path" value="<%=request.getContextPath()%>"/>` + `<a href="${path}/user/list.do">` → 링크 없음 + `jspUrl ${}/user/list.do`(번들 91 전 `/user/list.do` — `6-85` 가 값을 「`${…contextPath}` 하나」 로 좁혔다). `value="${…scheme}://${…serverName}:${…serverPort}${…contextPath}"`·`value="${fn:escapeXml(pageContext.request.contextPath)}"` 도 같다 · `String view = "redirect:"; view += t; return view;` → `View(redirect, "")` · `return "redirect:?err=" + t;` → `View(redirect, "")` · `String v = "forward:"; if (ok) v = v + "/a.do"; return v;` → `View(forward, "")`(기대 뷰 없음 + `viewDynamic` — `6-92` 의 `addNamed` 가 이어붙임 가지에만 걸렸다)
- 변경:
  1. `JspLinks.CTX_VALUE` — 조건을 「값이 contextPath 식으로 **끝난다**」 로: 끝이 `contextPath}` · `contextPath)}` · `getContextPath()%>`(끝 공백 허용) 가운데 하나. 값 앞쪽 글은 안 본다 — `Pattern.compile("\\bvalue\\s*+=\\s*+[\"'][^\"']*(?:contextPath\\s*+\\)?\\s*+\\}|getContextPath\\(\\)\\s*+%>)\\s*+[\"']", Pattern.CASE_INSENSITIVE)`(묶음에 수량자가 없다 — REDOS 판정을 지난다). 그 값이 `…contextPath}/images/` 처럼 뒤에 경로가 붙은 것은 지금처럼 뺀다(6-85 의도)
  2. `JavaViews` — 리터럴 가지(`:66`)도 `addNamed` 를 거치게 하고, `JavaGraph.views` 가 뷰를 넣는 자리에서 접두(`redirect:`·`forward:`)를 뗀 이름이 비면 넣지 않고 `viewDynamic` 을 적는다(지금 이어붙임 가지와 같은 처분). `views(3)` 은 기준선 19 — 분기를 더하지 않고 `JavaViews` 쪽 static 판정(`JavaViews.named(String view)`)으로
- 시험(먼저): `JspLinksTest.ctxSetNeedsBareContextPath` 에 세 값 꼴 → `/user/list.do` 링크(전 — 없음) · 지킴이 `value="${ctx}/images/"` 는 이름으로 안 잡는다 · `JavaGraphTest` 의 6-92 사례 옆에 세 뷰 꼴 → 뷰 없음 + `viewDynamic`(전 — 빈 이름 뷰) · 지킴이 `"redirect:/a.do"` → redirect `/a.do`
- 의도된 골든 변화: `analyze-*-jspurl`·링크 수 0 기대(점검자 corpus 실측: 새 규칙이 뺀 이름 22개는 전부 `…contextPath}/images/` 꼴)
- 검증: `bash scripts/mvn.sh -q test -Dtest='JspLinksTest,JavaGraphTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-95

### 묶음 ③ 프로그램 분석 — SQL (corpus · db 레인)

#### 6-98 — DB 대조: 파일용 오류문에 리터럴 값이 남는 남은 길 · 머리 주석 · 끝 `;` (점검 4차 C1-01 Critical · C1-09 · C1-05 — 절대 규칙 3)
- 대상: `core/analyze/SqlProbe.java`(`names` · 새 `ambiguous` · `forFile` · `withoutTailSemicolon`) · `SqlProbeTest`
- 사실(점검 탐침 — `forFile("Incorrect DATE value: 'ZZLIT9'", sql)` 가 `'ZZLIT9'` 를 남긴다 / 기대 `Incorrect DATE value: …`):
  - `… WHERE REPLACE(NM, '\'', '') = 'x' AND A = 'ZZLIT9'` · `… WHERE NM LIKE '%\'%' AND A = 'ZZLIT9'` · `… FROM T #'24.03 fix⏎WHERE A = 'ZZLIT9'` · `… WHERE M = nq'[it's]' AND A = 'ZZLIT9'` · `… WHERE M = "he \"ZZLIT9\" ok"`
  - H2 실행: `SELECT ID /* a /* b */ T's */ FROM T WHERE ID = 'ZZLIT9'` → fileMessage `Data conversion error converting "ZZLIT9"; …`
  - 원인: `names:407-421` 이 `SqlTokens` 의 표 추출용 어림(`\'` 는 뒤가 낱말 글자일 때만 탈출 · `#` 는 뒤가 공백일 때만 주석 · 블록 주석은 안 겹친다 · `q'` 는 앞이 낱말 글자면 아니다)으로 리터럴을 가른다 — 어림이 틀리면 따옴표 짝이 뒤집혀 뒤 리터럴이 「자기 낱말」 이 된다. xlsx 로 가는 길은 `AnalyzeRoutes:433`
  - `headCut:358` 이 주석 안 뗀 글에 `HEAD_NEXT` 를 맞춰 머리 주석(`/* ns.id */ SELECT …` — SI 매퍼 관례)이 있으면 V-64 고침이 꺼진다: `forFile("Unknown column 'x' in 'SELECT'", "/* P.q */ SELECT x FROM t")` → 그대로(기대 `Unknown column 'x' in …`)
  - `withoutTailSemicolon:272·275` 만 아직 `RegexRule.stripComments(s, "sql")` — `wrap(mysql)` 의 `SELECT A FROM T; # done` → `SELECT A FROM T; # done⏎LIMIT 0`(기대 `SELECT A FROM T⏎LIMIT 0`), `… WHERE M = 'it\'s'; -- done` 도 `;` 가 남아 거짓 ERROR
- 변경:
  1. `names` 머리에 닫힌 실패 — `if (ambiguous(sql)) { return java.util.Set.of(); }`(자기 낱말을 하나도 안 믿는다 — `quotes` 가 첫 따옴표 토막에서 `…` 로 자른다)
     ```java
     /**
      * 6-98(C1-01, 규칙 3) — 리터럴·주석 경계가 방언에 따라 달리 읽히는 글인가: 따옴표 앞 역슬래시(\' \") · 겹친 블록 주석 · MyBatis 바인드가
      * 아닌 # · Oracle q'…' 인용 · 달러 인용($tag$). 하나라도 있으면 어느 토막이 리터럴인지 믿지 않는다
      */
     static boolean ambiguous(String sql) {
         return sql.contains("\\'") || sql.contains("\\\"") || nestedComment(sql) || AMBIGUOUS.matcher(sql).find();
     }
     ```
     `private static final Pattern AMBIGUOUS = Pattern.compile("#(?!\\{)|(?i)\\bn?q'|\\$\\w*\\$");`(인라인 플래그 자리는 PMD·SpotBugs 가 받는 꼴로 — 안 받으면 `Pattern.CASE_INSENSITIVE` 로 `[nN]?[qQ]'`) · `nestedComment` — `/*` 마다 그 뒤 첫 `*/` 앞에 또 `/*` 가 있으면 참(안 닫힌 `/*` 뒤에 `/*` 가 있어도 참), 한 번 훑기(`ReadOnlyJdbc` 의 겹친 주석 판정과 같은 꼴)
  2. `forFile` — `return headCut(quotes(message, sql), sql == null ? "" : SqlTokens.stripComments(sql));`
  3. `withoutTailSemicolon` — 두 줄의 `RegexRule.stripComments(s, "sql")` 를 `SqlTokens.stripComments(s)` 로(3절 [고정] — 분석 쪽 SQL 주석은 `SqlTokens` 한 자리. `SqlTokens.stripComments` 가 길이를 지키는지 javadoc 으로 확인 — 안 지키면 사다리)
- 시험(먼저): `SqlProbeTest.fileMessageFailsClosedOnAmbiguousQuoting` — 사실 줄 다섯 sql 로 `forFile` → `ZZLIT9` 가 없다(전 — 남는다) · H2 메모리 `SqlProbe.run` 에 겹친 주석 문장 → `fileMessage` 에 `ZZLIT9` 가 없다 · 지킴이 — `SELECT X FROM T` + `Unknown column 'X' in 'field list'` 는 `'X'` 가 남는다 · `M = 'it\'s' AND A = 'ZZLIT9'` 도 이제 통째 `…`(닫힌 실패 — 값은 안 남는다) · 머리 주석 꼴 → `Unknown column 'x' in …` · `wrap` 두 꼴 → `;` 가 떼어진다 · 기존 `fileMessageIgnoresCommentQuotes`·V-64 사례 초록
- 의도된 골든 변화: 없음(`--db` 의 DB 대조 행 상태가 ERROR → PASS 로 바뀌는 것은 `V-71` 허용 변화)
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlProbeTest,AnalyzeRoutesTest,SqlTokensTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 사다리: `SqlTokens.stripComments` 가 길이를 안 지켜 `withoutTailSemicolon` 의 `substring(0, bare.length() - 1)` 이 어긋나면 3번 조각은 `bare` 를 「주석을 뗀 글의 끝이 `;` 인가」 판정에만 쓰고 자르기는 원문에서 마지막 `;` 자리로 한다
- 한계(이력에): 겹친 블록 주석을 표 추출(`SqlTokens.commentEnd`)이 첫 `*/` 에서 끝내는 것은 그대로(점검 C1-12 — 방언마다 갈려 한쪽으로 못 정한다) · `#tmp`(MSSQL 임시 표)·`COL#` 이 든 SQL 의 파일용 오류문은 따옴표 토막이 전부 `…`
- 의존: S0

#### 6-99 — SqlJoins: `UPDATE 별칭 … FROM 표 별칭` 의 지어낸 관계 · 따옴표 친 별칭 · INTO 없는 MERGE · 열 이름 APPLY (점검 4차 C1-02 Important · C1-06 · C1-13 · C1-14)
- 대상: `core/analyze/SqlJoins.java`(`Scopes.table` · 새 `aliasFirst` · `entry`·`derived` 의 별칭 열쇠 · `opens`) · `core/analyze/SqlTables.java`(`open` — 앞 토큰 `.`) · `SqlJoinsTest` · `SqlTablesTest` · 골든 `analyze/sql-joins.json`·`sql-tables.json`
- 사실(점검 탐침):
  | SQL | 지금 | 기대 |
  |---|---|---|
  | `UPDATE a SET a.CNT = (SELECT COUNT(*) FROM T3 c WHERE c.P_ID = a.ID) FROM T1 a WHERE a.USE_YN = 'Y'` | `A.ID—T3.P_ID`(별칭 글자가 표 이름 — 지어냄) | `T1.ID—T3.P_ID` |
  | `UPDATE v SET v.X = (SELECT MAX(c.X) FROM T3 c WHERE c.ID = v.ID) FROM (SELECT ID, X FROM T1) v` | `T3.ID—V.ID` | 없음 |
  | ``SELECT * FROM T1 `group` JOIN T2 b ON `group`.ID = b.ID`` · `SELECT * FROM T1 "ORDER" JOIN T2 b ON "ORDER".ID = b.ID` | 없음 | `T1.ID—T2.ID` |
  | `MERGE T1 AS t USING T2 AS s ON t.ID = s.ID WHEN MATCHED THEN UPDATE SET t.X = s.X` | 없음 | `T1.ID—T2.ID` |
  | `SELECT a.APPLY, b.X FROM T1 a JOIN T2 b ON a.APPLY = b.APPLY ORDER BY a.APPLY DESC, b.X`(`SqlTables`) | `DESC:R T1:R T2:R X:R` + `table` 미해결 | `T1:R T2:R` |
  - 원인: `6-78` 의 `nearest:317-327` — `UPDATE a` 가 `Def(자리, "A")`(자기 정의)로 남고 그것이 「자리 앞 가장 가까운 정의」 다(전에는 뒤 정의가 덮었다 — 회귀) · `entry:354-356`·`derived:278` 이 별칭을 따옴표째 열쇠로 적고 찾을 때는 벗긴다(`6-79` 덜 고침) · `OPENERS:25` 에 `MERGE` 가 없다 · `SqlTables.open:309-317` 과 `SqlJoins.opens` 가 앞 토큰 `.` 을 안 봐 열 이름 `APPLY` 가 여는 말이 된다(`6-75`·`6-80` 이 `APPLY` 를 넣으며 생긴 자리)
- 변경:
  1. `Scopes.table` — `String v = nearest(aliasFirst(defs, name), i);`
     ```java
     /** 6-99(C1-02) — 같은 범위에 별칭 정의(표 ≠ 이름)나 빈 글 정의(파생 표·WITH 이름)가 있으면 자기 정의(표 = 이름 — UPDATE a 의 a)는 후보에서 뺀다(SqlTables.settle 과 같은 판정) */
     private static List<Def> aliasFirst(List<Def> defs, String name) {
         List<Def> real = defs.stream().filter(d -> !d.table().equals(name)).toList();
         return real.isEmpty() ? defs : real;
     }
     ```
  2. `entry`·`derived` — 별칭 열쇠를 `SqlTables.unq(t.get(j))` 로(두 자리)
  3. `opens` — 첫 조건에 「INTO 없는 MERGE」 와 「앞이 `.` 이 아님」 을 도우미로: `if (opener(t, i) || ",".equals(x) && derivedEnd.contains(i - 1))`
     ```java
     /** 여는 말인가 — 열 이름(a.APPLY)·잠금 UPDATE 는 아니다. 문장의 MERGE 는 뒤가 INTO·JOIN 이 아니면 표 자리(T-SQL MERGE T1 AS t, 6-99) */
     private static boolean opener(List<String> t, int i) {
         String x = t.get(i);
         if (i > 0 && ".".equals(t.get(i - 1))) {
             return false;
         }
         if ("MERGE".equals(x)) {
             return i + 1 < t.size() && !"INTO".equals(t.get(i + 1)) && !"JOIN".equals(t.get(i + 1));
         }
         return OPENERS.contains(x) && !lockUpdate(t, i);
     }
     ```
  4. `SqlTables.open` — 앞 토큰이 `.` 이면 안 연다(`listAfter` 에 든 판정과 같은 식 — 그 메서드가 기준선 줄 있는 `extract(2)` 안이면 식을 도우미 호출로)
- 시험(먼저): `SqlJoinsTest.shapes` 에 위 표의 조인 다섯 줄(열쇠는 골든에 글자 차례로) · `SqlTablesTest.CASES` 에 APPLY 열 이름 한 줄 · 지킴이(점검자가 지금 맞다고 확인 — 새 사례로 못 박는다): `UPDATE a SET a.X = b.X, a.Y = b.Y FROM T1 a INNER JOIN T2 b ON a.ID = b.ID` → `T1.ID—T2.ID` · `UPDATE T1 a SET X = b.X FROM T2 b WHERE a.ID = b.ID` → `T1.ID—T2.ID` · 분기 한쪽은 표·한쪽은 파생 표인 같은 별칭 → `T1.ID—T2.ID` 만 · `MERGE INTO T1 t USING (SELECT … T2 a JOIN T3 b …) s ON (t.ID = s.ID)` → `T2.ID—T3.ID` 만
- 의도된 골든 변화: `sql-joins.json`·`sql-tables.json` 에 새 열쇠만(옛 열쇠 그대로) · corpus `analyze-*-joins` 는 `V-71a` 허용 변화(별칭 글자 표 이름이 진짜 표로 바뀐다)
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlJoinsTest,SqlTablesTest,MapperIndexTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 6-100 — SqlJoins: 표 목록 안 쉼표(힌트·ON·USING 뒤) · 겹괄호 세제곱 시간 (점검 4차 C1-11 · C1-03)
- 대상: `core/analyze/SqlJoins.java`(`Scopes.fill` · 새 중첩 클래스 `FromList` · `closedParens` → 짝 자리 배열 · `closing`) · `SqlJoinsTest` · 골든 `analyze/sql-joins.json`
- 사실(점검 탐침): `fill:262` 은 「표 항목 바로 뒤 쉼표」 와 「파생 표 뒤 쉼표」 에서만 다음 표 자리를 연다 — `SqlTables`(6-44·6-80)는 읽는 표를 `SqlJoins` 는 못 읽는다(판정 중복 어긋남, MSSQL NOLOCK 쉼표 조인은 되풀이 꼴)
  | SQL | 지금 | 기대 |
  |---|---|---|
  | `SELECT * FROM T1 a WITH (NOLOCK), T2 b WITH (NOLOCK) WHERE a.ID = b.ID` | 없음 | `T1.ID—T2.ID` |
  | `SELECT * FROM T1 a JOIN T2 b ON a.ID = b.ID, T3 c WHERE c.X = a.X` | `T1.ID—T2.ID` 만 | + `T1.X—T3.X` |
  | `SELECT * FROM T1 a JOIN T2 b USING (ID), T3 c WHERE c.X = a.X` · `SELECT * FROM T1 a IGNORE INDEX (IDX1), T2 b WHERE a.ID = b.ID` | 빠짐 | 위와 같은 꼴 |
  - 시간: `SELECT * FROM ` + `(`×2000 + `SELECT 1` + `)`×2000 + ` v` → 11.3초(세제곱 — `queryParen:301-314` 가 `(` 마다 `closing` 을 다시 훑는다) · 안 닫힌 `(`×80000 → 12.2초. 같은 글에 `SqlTables` 는 10ms
- 변경:
  1. `closedParens` 를 `private static int[] parenPairs(List<String> t)`(여는 자리 → 닫는 자리, 짝 없으면 -1)로 바꿔 `Scopes` 필드 `private int[] pair;` 에 두고(`of` 가 채운다), `closed[i]` 는 `pair[i] >= 0` · `closing(t, from)` 은 `pair[from] >= 0 ? pair[from] : t.size()` 한 줄(인스턴스 메서드로). `queryParen`·`derived` 는 그 `closing` 을 쓴다
  2. 표 목록 안 쉼표:
     ```java
     /** 6-100(C1-11) — FROM 이 연 표 목록의 괄호 깊이를 쌓아, 목록 안 같은 깊이의 쉼표를 다음 표 자리로 본다(힌트·ON·USING 뒤 쉼표 조인) */
     private static final class FromList {
         private static final Set<String> END = Set.of("WHERE", "GROUP", "ORDER", "HAVING", "SET", "VALUES", "SELECT", "UNION", "INTERSECT", "EXCEPT",
                 "MINUS", "LIMIT", "OFFSET", "FETCH", "FOR", "WINDOW", "CONNECT", "START", "RETURNING", "WHEN", ";");
         private final Deque<Integer> lists = new ArrayDeque<>();
         private int depth;

         /** 토큰 하나를 먹는다 — 그것이 목록 안 같은 깊이의 쉼표면 참 */
         boolean comma(String x) {
             if (OPEN.equals(x)) {
                 depth++;
             } else if (CLOSE.equals(x)) {
                 depth--;
                 drop(depth + 1);
             } else if ("FROM".equals(x)) {
                 drop(depth);
                 lists.push(depth);
             } else if (END.contains(x)) {
                 drop(depth);
             }
             return ",".equals(x) && !lists.isEmpty() && lists.peek() == depth;
         }

         /** 깊이 d 이상에서 열린 목록을 닫는다 */
         private void drop(int d) {
             while (!lists.isEmpty() && lists.peek() >= d) {
                 lists.pop();
             }
         }
     }
     ```
     `fill` — 루프 앞에 `FromList list = new FromList();`, 루프 안 첫 줄 `boolean listComma = list.comma(x);`, `expect = opens(t, i, expect);` 를 `expect = opens(t, i, expect) || listComma;` 로. `entry` 가 건너뛴 토큰(스키마 마디·AS·별칭·바로 뒤 쉼표)은 괄호·절 낱말이 아니라 `FromList` 가 안 먹어도 된다
- 시험(먼저): `SqlJoinsTest.shapes` 에 위 표 네 줄 · `SqlJoinsTest.nestedParensAreLinear` — 겹괄호 2000 꼴과 안 닫힌 `(`×80000 꼴이 각각 `assertTimeout(Duration.ofSeconds(3), …)` 안(전 11~12초) · 지킴이(새 사례): `SELECT a, b FROM T1 x WHERE x.ID IN (1, 2)` 조인 없음 · `SELECT * FROM T1 a JOIN T2 b ON a.ID = b.ID ORDER BY a.X, b.Y` → `T1.ID—T2.ID` 만 · `… FOR UPDATE OF a.X, b.Y NOWAIT` → `T1.ID—T2.ID` 만 · `UPDATE T1 a SET a.X = 1, a.Y = 2 WHERE a.ID = 3` 조인 없음 · `FROM T1 a, (SELECT …) v, T3 c WHERE a.ID = v.A_ID(+) AND a.CD = c.CD` → `T1.CD—T3.CD`
- 의도된 골든 변화: `sql-joins.json` 에 새 열쇠만(옛 열쇠 서른다섯 + 번들 91 추가분 그대로 — 바뀌면 7절) · corpus `analyze-*-joins`·`deliverable-egov-relations.json` 은 **느는 것**만(`V-71a`)
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlJoinsTest,MapperIndexTest,RelationsTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 사다리: 옛 골든 열쇠가 바뀌거나 지킴이가 빨개지면 2번 조각(`FromList`)을 빼고 1번(시간)만 친다 — 이력에 그 꼴을 적고 새 행
- 의존: 6-99

#### 6-101 — SqlTables: 분기 뒤 괄호(넓다·좁다) · PL/SQL USING 과 태그 동사 · 닫힌 뒤 분기 표시 (점검 4차 C1-07 · C1-08 · C1-15)
- 대상: `core/analyze/SqlTables.java`(`byBranch` 가지 `:352-357`·`:464-466`·`:476-477` · `opener:585-586`) · `SqlTablesTest` · 골든 `analyze/sql-tables.json`
- 사실(점검 탐침 — `B` 는 분기 표시 `String.valueOf(SqlTables.BRANCH)`):
  | 글(tag) | 지금 | 기대 |
  |---|---|---|
  | `SELECT * FROM B T1 B (T2 JOIN T3 ON T2.ID = T3.ID)` | `T1:R`(미해결 없음 — 번들 91 `6-80` 회귀, 전에는 `parenTable` 로 읽었다) | `T1:R T2:R T3:R` |
  | `INSERT INTO T1 AS t B (COL_A, COL_B) VALUES (?, ?) B (COL_A, COL_C) VALUES (?, ?)` | `COL_A:R COL_B:R T1:C` | `T1:C` |
  | `BEGIN EXECUTE IMMEDIATE q USING V1, V2; END;`(tag `delete`) | `V1:R V2:R` | 표 없음 |
  | `SELECT * FROM B T1 a JOIN T2 b ON a.ID = b.ID B T3 a JOIN T4 b ON a.ID = b.ID` | `T1:R T2:R T4:R`(T3 빠짐 · 미해결 없음) | + `T3:R` |
- 변경(방향 — 실행자가 그 가지를 읽고 가장 작은 식으로):
  1. 분기 뒤 `(` — 「안이 이름·쉼표뿐이고 닫는 괄호 뒤가 `VALUES`·`SELECT`·분기 표시·글 끝」 일 때만 컬럼 목록으로 보고(그때만 `byBranch` 로 닫는다), 아니면 지금 `parenTable` 길로. `AFTER_ALIAS` 가지의 분기 표시(`:476`)도 같은 표시를 세운다
  2. `opener` 의 USING — 문장 바닥이 블록(`BEGIN`·`DECLARE` 로 시작)이면 태그 동사(`tagVerb`)로 열지 않는다(그 글에 실제 `DELETE`·`MERGE` 낱말이 있을 때만)
  3. FROM 이 연 목록 안에서 닫힌 뒤 온 분기 표시는 그 FROM 의 표 자리를 다시 연다
  - `extract(2)` 는 기준선 17 — 그 메서드에 분기를 더하지 않는다(판정은 `private static` 도우미로)
- 시험(먼저): `SqlTablesTest.CASES` 에 위 표 넷 · 지킴이(점검자가 지금 맞다고 확인): `INSERT INTO T1 B (COL_A, COL_B) … B (COL_A, COL_C) …` → `T1:C` · 분기 안 `(SELECT … T2 x JOIN T3 y …)` → `T1:C T2:R T3:R` · `FROM B T1 a B T3 a` → `T1:R T3:R` · `tag select`·`update` 의 `EXECUTE IMMEDIATE … USING` 은 표 없음
- 의도된 골든 변화: `sql-tables.json` 에 새 열쇠만 · corpus `analyze-*` 의 표·CRUD 는 `V-71a` 허용 변화
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlTablesTest,MapperIndexTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 사다리: 넷 가운데 옛 골든 열쇠를 바꾸는 조각은 빼고 나머지만 친다(이력에 · 새 행)
- 의존: 6-99(같은 골든)

#### 6-102 — DB 대조: iBATIS 쉼표 꼴 인라인 파라미터 (점검 4차 C1-10)
- 대상: `core/analyze/MapperIndex.java`(`IBATIS_HASH:93` 근처) · `SqlProbeTest` 또는 `MapperIndexTest`
- 사실(점검 탐침, H2): `<sqlMap namespace="U"><select id="s1">SELECT ID FROM T WHERE NM = #nm,jdbcType=VARCHAR#</select>…` → `s1` 이 ERROR(`Syntax error …`)(기대 PASS — `#nm:VARCHAR#`·`#nm#` 는 PASS). 번들 91 `6-83b` 가 sqlMap 을 대조에 올렸는데 `IBATIS_HASH` 는 `#이름#`·`#이름:…#` 만 안다
- 변경: `IBATIS_HASH` 의 꼬리에 `,` 꼴을 더한다 — 이름 뒤 `[:,]` 로 시작해 다음 `#` 앞까지(`#` 아닌 글자의 소유 반복 하나 — 묶음 반복 없이). `sqlText` 한 자리라 표 추출도 같이 따라온다
- 시험(먼저): `MapperIndexTest` — 그 sqlMap 문장의 SQL 글에 `#nm,jdbcType=VARCHAR#` 이 바인드 자리(`?`)로 바뀐다(전 — 그대로 남는다) · `SqlProbeTest` 가 H2 로 그 문장을 돌려 PASS · 지킴이 `#nm:VARCHAR#`·`#nm#`·MyBatis `#{nm,jdbcType=VARCHAR}`
- 의도된 골든 변화: `mapper-index.json` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='MapperIndexTest,SqlProbeTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 4-51 — 로그 SQL: 짝 없는 Parameters 의 복제가 다른 문장에 붙는다 (점검 4차 C1-04 · 후보-6)
- 대상: `core/text/LogSql.java`(`:97` · `:106-118`) · `LogSqlTest`
- 사실(점검 탐침): 앞머리 없는 로그(StdOutImpl) + REUSE 실행기 — `Preparing: SELECT * FROM A WHERE ID = ?` / `Parameters: 1(Integer)` / `Preparing: SELECT * FROM B WHERE X = ?` / `Parameters: 5(Integer)` / `Parameters: 2(Integer)` → 셋째 항목 `SELECT * FROM B WHERE X = 2`(경고 없음 — A 의 둘째 행이어야 할 수도 있다. 번들 91 전에는 버렸다) · 스레드 섞인 배치 `[t-1] Preparing A` / `[t-1] Parameters 1` / `[t-2] Preparing B` / `[t-1] Parameters 2` / `[t-2] Parameters 9` → `B … = 2`·`B … = 9`(A 의 둘째 행 없음). `4-50`(G-07)의 `else if`(`:114-118`)가 「바로 앞 Pending 이 같은 열쇠 · 이미 짝」 뿐을 근거로 복제하고, `:107` 의 옛 대체 길(다른 열쇠의 짝 없는 Preparing)이 복제보다 앞선다 · `:97` 겹침 경고와 배열 경고가 같이 걸리면 배열 경고만 남는다
- 변경:
  1. 열쇠마다 마지막 Pending 을 적어(`Map<String, Pending>`) 복제는 **그 열쇠의 마지막 문장**에만 하고, 옛 대체 길보다 앞세운다
  2. 복제 항목에는 경고 `배치로 보고 앞 문장을 다시 썼다 — 다른 문장의 값일 수 있다` 를 단다(새 글)
  3. 경고가 둘이면 ` · ` 로 이어 둘 다 남긴다
- 시험(먼저): `LogSqlTest` — 첫 로그 → 셋째 항목에 그 경고가 있다(전 — warning null) · 둘째 로그 → `[t-1]` 의 둘째 Parameters 가 A 문장으로 복제된다(`SELECT * FROM A WHERE ID = 2` + 경고, 전 — B 에 붙는다) · `(byte[])` + 겹침 로그 → 경고 글에 두 사유가 다 있다 · 지킴이(점검자 확인) — 배치 세 줄 → 항목 셋 · 스레드 섞인 두 짝은 경고 없음 · 로거 이름으로 열쇠가 갈린 REUSE 는 버린다 · CRLF
- 의도된 골든 변화: `logsql-*` 골든 0 기대(바뀌면 그 줄이 복제 경고인지 보고 갱신)
- 검증: `bash scripts/mvn.sh -q test -Dtest='LogSqlTest,TextRoutesTest,SmokeHtmlUnitTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

### 묶음 ① 코드 검사 — 판정 (corpus 레인 · 「순수본 밀린 목록」 대상)

#### 5-146a — 끊긴 따옴표 줄 자르기: 줄 수가 준다 · URL 안 `//` (대기 행 5-146 ② + 점검 4차 D4-01 Important)
- 대상: `core/check/RegexRule.java`(`lineCut` · 새 `commentStart`·`closingQuote`) · `CheckRulesTest`
- 사실(점검 탐침): `lineCut:306-314` 가 따옴표 연 자리 뒤 **첫 `//`** 부터 글 끝 앞까지를 빈칸으로 바꾼다
  - 줄 수: (js) `var q = s.replace(/'/g, ""); var h = "<a href=\"http://a.b\">\⏎x</a>";⏎foo();⏎console.log(1);⏎console.log(2);⏎bar();⏎` — 끊긴 따옴표가 「역슬래시 + 줄바꿈」 을 한 번 넘긴 뒤 끊겨, 앞줄의 `//` 부터 그 줄바꿈까지 빈칸이 돼 주석 뗀 글이 한 줄 준다 → 지적이 `3 tsx.console [foo();]`·`4 [console.log(1);]`(기대 4·5), **코드 정리가 `foo();` 를 지우고 `console.log(2);` 를 남긴다**. 최소 꼴 `"//\⏎⏎`(tsx). 「줄 수가 같다」 를 믿는 자리 — `RegexRule.applyText:47` · `Ignores.of` · `LuhnRule` · `Cleanup.deleteConsole` · `PiiFix` · `MarkerFix`
  - URL: (js) `var re = /'/; var u = "http://a.b/c"; eval(x);⏎alert(1);⏎` → `2 common.alert` 뿐(`eval` 놓침 — 대조 `/x/` 는 `1 security.jsEval`). tsx `const a = <p>Don't</p>; const u = "http://a.b"; eval(x);` → 0
- 변경(`cLike(2)` 는 기준선 25 — 안 고친다. `lineCut` 과 새 도우미만):
  ```java
  private static void lineCut(StringBuilder sb, char c, int quoteAt, boolean slashes) {
      if (c != '\n' || !slashes) {
          return;
      }
      int end = sb.length() - 1; // 방금 붙인 줄바꿈
      int from = Math.max(quoteAt + 1, sb.lastIndexOf("\n", end - 1) + 1); // 5-146a(D4-01) — 끊긴 그 줄 안에서만(앞줄의 줄바꿈을 안 지운다)
      for (int k = commentStart(sb, from, end); k >= 0 && k < end; k++) {
          sb.setCharAt(k, ' ');
      }
  }

  /** [from, end) 를 보통 코드로 훑어 문자열 밖 첫 // 자리(없으면 -1) — 닫힌 문자열("http://…") 안 // 는 건너뛴다. 짝 없는 따옴표는 글자로 본다(5-146a) */
  private static int commentStart(StringBuilder sb, int from, int end) {
      int k = from;
      int tries = 0;
      while (k + 1 < end) {
          char ch = sb.charAt(k);
          if (ch == '/' && sb.charAt(k + 1) == '/') {
              return k;
          }
          boolean quote = ch == '"' || ch == '\'' || ch == '`';
          int close = quote && tries++ < QUOTE_TRIES ? closingQuote(sb, k, end) : -1;
          k = close < 0 ? k + 1 : close + 1;
      }
      return -1;
  }

  /** at 의 따옴표를 닫는 같은 따옴표 자리(역슬래시 탈출은 건너뛴다) — (at, end) 에 없으면 -1 */
  private static int closingQuote(StringBuilder sb, int at, int end) {
      char q = sb.charAt(at);
      int k = at + 1;
      while (k < end) {
          char ch = sb.charAt(k);
          if (ch == q) {
              return k;
          }
          k += ch == '\\' ? 2 : 1;
      }
      return -1;
  }
  ```
  `private static final int QUOTE_TRIES = 16;`(짝 찾기 횟수 상한 — 탈출 따옴표가 늘어선 긴 줄의 제곱 시간을 막는다)
- 시험(먼저): `CheckRulesTest.brokenQuoteKeepsLineCountAndCode` — ① 사실 줄 첫 입력(js·tsx) → `console` 지적이 4·5 행(전 3·4) · `RegexRule.bare(text, lang)` 의 줄 수와 줄마다 길이가 원문과 같다(불변식 — js·ts·tsx·jsx·java·jsp·html·css 여덟 언어로 최소 꼴 `"//\⏎⏎` 과 사실 줄 입력) ② URL 꼴(js) → `1 security.jsEval`·`2 common.alert`(전 — `eval` 없음) · tsx 꼴 → `1 security.jsEval` ③ 지킴이 — 끊긴 따옴표 뒤 진짜 주석 `var re = /'/; // eval(x)` 는 지적 0 · `CleanupTest` 에 한 사례: 사실 줄 첫 입력을 정리하면 `console.log(1);`·`console.log(2);` 두 줄만 지워지고 `foo();` 가 남는다(전 — `foo();` 가 지워진다) · 9만 자 한 줄 `'` + `\'`×30000 이 `assertTimeout(Duration.ofSeconds(2), …)` 안
- 의도된 골든 변화: corpus `check-*` 는 0 기대(점검자 표본 실측 0건)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,CleanupTest,ZonesTest,PureRulesJsonTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 5-146b — `<script>` 몸 판정: 비 JS type · 자기 닫는 태그 · 주석·스크립틀릿 안 여는 태그 · 따옴표 값 안 `>` (대기 행 5-146 ① + 점검 4차 D-1c · C2-13 · D4-09)
- 대상: `core/check/RegexRule.java`(`scriptBodies` · `scriptWrappers` · 새 도우미) · `core/check/Zones.java`(`:66` — 몸 재기를 `scriptBodies` 로) · `CheckRulesTest` · `ZonesTest` · `JspLinksTest`
- 사실(점검 탐침): `scriptBodies:189-206` 은 `<script` 로 시작하는 모든 여는 태그의 `>` 뒤부터 첫 `</script` 까지를 JS 몸으로 본다 —
  - 1a (jsp) `<script type="text/template" id="tpl">⏎<!-- row⏎  alert(1); eval(x);⏎-->⏎<div>a</div>⏎</script>` → `4 common.alert`·`4 security.jsEval`(템플릿 몸의 HTML 주석 둘째 줄 — 번들 91 전에는 통째로 비었다)
  - 1b `<script src="a.js"/>⏎<!-- old⏎  alert(1); eval(x);⏎-->⏎<script>var a = 1;</script>` → `3 common.alert`·`3 security.jsEval`
  - 1c 주석·스크립틀릿 안의 짝 없는 여는 태그: `<%-- <script src="old.js"> --%>⏎<!-- old block⏎  alert(1); eval(x);⏎-->⏎<p>x</p>⏎<script>⏎var a = 1;⏎</script>` → `3 common.alert`·`3 security.jsEval`. `<!-- <script src="old.js"> -->`·`<% out.println("<script>"); %>` 도 같다. 이때 `Zones.zones` 가 1~5행 마크업을 「script 안」 으로 적고(`Zones:66` 이 원문으로 몸을 잰다), JSP 링크는 죽은 주석 안 `<a href="/dead/three.do">` 를 살린다(C2-13 — `[/dead/three.do, /live.do]`, 기대 `[/live.do]`)
  - `<script src="<c:url value='/js/a.js'/>">` — `>` 찾기가 속성 값 안 `/>` 에 걸린다(지금은 몸이 `">` 뿐이라 해가 없지만 자기 닫는 태그 판정을 넣으면 틀린다)
  - `scriptWrappers:232` 의 `sb.replace(k, k + 4, "//  ")` 가 같은 길이여도 꼬리를 옮겨 `<!--` 20만 개(1MB)에 646ms(제곱 꼴)
- 변경:
  1. `scriptBodies` — 한 번 훑기를 지키면서:
     - `<` 자리가 `<!--` 면 `-->` 뒤로, `<%--` 면 `--%>` 뒤로, `<%` 면 `%>` 뒤로 건너뛴다(닫는 글이 없으면 글 끝 — 거기서 끝낸다). script 몸 **밖**에서만(몸 안은 지금처럼 `scriptClose` 로 뛴다)
     - 여는 태그의 끝 `>` 는 따옴표 값(`"…"`·`'…'`)을 건너뛰며 찾는다(`tagEnd(text, from)` — 따옴표가 안 닫히면 지금처럼 첫 `>`)
     - 여는 태그가 `/>` 로 끝나면(따옴표 밖 `>` 바로 앞이 `/`) 몸이 없다 — 구간을 안 넣고 `>` 뒤부터 다시 찾는다
     - 여는 태그 글(`<script` ~ `>`)의 `type` 속성 값이 있고 JS 가 아니면(`javascript`·`ecmascript`·`module`·`babel`·`jsx` 가운데 하나도 안 든 값 — 대소문자 무시) 구간을 안 넣는다(몸은 `</script` 까지 건너뛴다 — HTML 주석 지우기가 지금처럼 그 안 주석을 비운다)
     - 도우미는 `private static` 으로 나눠 메서드마다 복잡도 15 아래. `RegexRule` 의 메서드 수가 30 을 넘으면 새 도우미들을 새 package-private 클래스 `ScriptTags` 로 옮긴다
  2. `scriptWrappers` — `sb.replace(k, k + 4, "//  ")` 를 `setCharAt` 네 번으로
  3. `Zones.scriptLines:66` 은 이미 `RegexRule.scriptBodies(text)` 를 쓴다 — 고치지 않는다(1번이 구역도 같이 고친다)
- 시험(먼저): `CheckRulesTest.wrappedScriptIsStillChecked` 옆 새 `scriptBodiesSkipNonJsAndCommentedTags` — 1a·1b·1c 세 꼴(+ `<!-- <script …> -->`·`<% out.println("<script>"); %>`)의 지적 0(전 — 위 지적) · `<script src="<c:url value='/js/a.js'/>"></script>⏎<script>⏎eval(x);⏎</script>` → `3 security.jsEval`(지킴이) · `type="text/javascript"`·`type="module"`·type 없음의 감싸개 `<!-- … //-->` 안 지적은 지금처럼 나온다 · `ZonesTest` — 1c 꼴의 1~5행이 script 구역이 아니다 · `JspLinksTest` — C2-13 입력 → `[/live.do]` · 자기 닫는 태그·`text/template` 뒤 죽은 링크(`/dead/two.do`·`/dead/four.do`)도 안 나온다 · 시간 — `<script>⏎` + `<!--⏎`×200000 + `</script>` 가 `assertTimeout(Duration.ofSeconds(1), …)` 안 · **고침 쪽(점검 4차 E4-05·E4-06)** `CleanupTest` — `z/l1.jsp` `<script src="a.js"/>`⏎`<pre>`⏎`var a = 1;`⏎`console.log(a);`⏎`debugger;`⏎`</pre>`⏎`<script>var b;</script>` → 두 줄이 「<pre>·<textarea> 안」 건너뜀(전 — 삭제) · `z/a2.jsp` `<script src="a.js"/>`⏎`<!--`⏎`<script>`⏎`console.log("old");`⏎`debugger;`⏎`</script>`⏎`-->` → 지적·삭제 0(전 — 주석 안 두 줄 삭제) · `z/a7.html` `<!-- <script> disabled -->` 뒤 여러 줄 HTML 주석 안 `debugger;`·`console.log("old");` → 0 · `z/a3.jsp` `<script type="text/template" id="t">`⏎`console.log("sample");`⏎`<div>x</div>`⏎`</script>` → 2행 건너뜀(전 — 삭제. 비 JS 몸이 구역 2 가 된다)
- 의도된 골든 변화: corpus `check-*` — 비 JS type 몸의 `<!--` 가 든 표본 1파일(`jeesite/…/testDataMainForm.jsp`)의 지적이 **준다** · 그 밖 0 기대. `analyze-*-jspurl`·링크 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,ZonesTest,JspLinksTest,CleanupTest,PureRulesJsonTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): 브라우저는 `<script src/>` 를 닫는 태그까지 여는 태그로 읽는다 — 그 사이 글은 안 본다(번들 91 전과 같다)
- 의존: 5-146a

#### 5-146c — 감싼 script 안 카드번호·없음·`_blank` 판정 · `BlankRelRule.lineOf` 제곱 시간 (대기 행 5-146 ③ + 설계 76 §4 후보 → 확인)
- 대상: `core/check/LuhnRule.java:29` · `core/check/AbsentRule.java:44` · `core/check/BlankRelRule.java`(`dead:89` · `lineOf:141-143`) · `LuhnRuleTest` · `BlankRelRuleTest` · `CheckRulesTest`
- 사실(점검 탐침): (jsp) `<script>⏎<!--⏎var c = 4111111111111111;⏎alert(1);⏎//-->⏎</script>` → `4 common.alert` 뿐(감싸개 두 줄을 뺀 대조는 `2 security.piiCardPlain` 도) — 셋이 아직 `stripComments` 라 감싼 몸이 HTML 주석으로 통째 빈다. 감싸개가 `-->` 없이 끝나면 script 뒤 마크업(`<a target="_blank">`·카드번호)도 다음 `-->` 까지 못 본다 · `BlankRelRule.lineOf` 가 지적마다 글 머리부터 줄을 센다 — `<a href="x.do" target="_blank">x</a>⏎`×40000 → 10.3초(2만 줄 2.5초, 지적 수 × 글 길이)
- 변경:
  1. `LuhnRule:29`·`AbsentRule:44` — 주석 뗀 글을 `RegexRule.bare(text, lang)` 으로
  2. `BlankRelRule.dead:89` — `stripComments(RegexRule.scriptWrappers(text), lang)`(길이가 같아 `MarkupFix` 의 자리는 그대로)
  3. `BlankRelRule` — 줄머리 자리 배열을 한 번 만들어(`int[] starts`) `lineOf` 를 이분 찾기(`java.util.Arrays.binarySearch`)로. `tags()` 가 지적마다 부르는 자리에 그 배열을 넘긴다
- 시험(먼저): `CheckRulesTest`(또는 `LuhnRuleTest`) — 사실 줄 입력 → `3 security.piiCardPlain`·`4 common.alert`(전 — alert 뿐) · `-->` 없이 끝난 감싸개 뒤 마크업 → `5 jsp.blankNoopener`·`6 security.piiCardPlain` 도(음성 — 주석 처리한 `<script>` 블록 안 카드번호는 0) · `BlankRelRuleTest.manyFindingsAreLinear` — 4만 줄 꼴이 `assertTimeout(Duration.ofSeconds(3), …)` 안이고 지적 4만 건의 줄 번호가 1..40000
- 의도된 골든 변화: corpus `check-*` — 감싼 script 안 `piiCardPlain`·`blankNoopener`·absent 규칙이 **늘 수 있다**(수를 이력에)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,LuhnRuleTest,BlankRelRuleTest,CleanupTest,PureRulesJsonTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-146b

#### 5-148 — 규칙 적재: BadRule 이 아닌 남은 오류 · params 키 (점검 4차 D4-02 Important · D4-10)
- 대상: `core/check/RuleSet.java`(`whenOf:166-171` · `validate:204` · `load:104·108` · `knownKeys`) · `CheckRulesTest` · `CheckRoutesTest`
- 사실(점검 탐침 — 프로필 덮어쓰기 · `GET /api/check/rules`(`CheckRoutes:100`)·`/api/cleanup/*` 가 500 「서버 오류」, `/api/check/run` 은 IAE 를 잡아 400):
  - ① `security.cfgErrorPage: {params: {when: ""}}` → `IllegalArgumentException: … kind absent 는 params.when 이 있어야 한다`(`5-130` 이 `whenOf` 를 「when 이 비지 않을 때만」 으로 바꿔 빈 when 이 `validate` 를 지나 `AbsentRule.<init>` 에서 난다)
  - ② `security.piiCardPlain: {regex: "("}` → `PatternSyntaxException` · `{flags: [x]}` → `IllegalArgumentException: … 모르는 flags x`(`validate` 가 regex·absent 만 `d.pattern()` 을 재는데 luhn 도 쓴다)
  - ③ `customRules: "a<b>.yaml"`(Windows) → `InvalidPathException`(`:104` 가 try 밖) · customRules 파일 내용이 `null`·`~` 한 줄이면 `readValue` 가 null 이라 `.rules()` NPE(추적만)
  - ④ `jsp.scriptlets: {params: {1: x}}` → 적재는 지나고 검사가 끝난 뒤 `fingerprint():290` 의 `TreeMap` 에서 `ClassCastException`(`CheckRoutes:230` — 저장 자리)
- 변경: 전부 `BadRule` 로(3절 [고정] — `RuleSet.load` 몸은 안 옮긴다, `load(2)` 는 기준선 26: 식을 도우미 호출로만 바꾼다)
  1. `validate` 의 kind 가지 — luhn 도 `d.pattern()` 을 잰다 · absent 는 `when` 을 비었든 아니든 잰다(비면 `BadRule(id + ": kind absent 는 params.when 이 있어야 한다")`)
  2. `load:104` 의 `base.resolve(…)` 와 `:108` 의 읽기를 도우미 하나로(`private static … customFile(…)`) — `InvalidPathException` 과 null 문서를 `BadRule("customRules: 경로·내용을 읽을 수 없다 — " + 값)` 로
  3. `knownKeys`(또는 `override`) — `params` 의 키가 글이 아니면 `BadRule(id + ": params 의 키는 글이어야 한다")`
- 시험(먼저): `CheckRulesTest.ruleDefinitionErrorsAreBadRule` 에 네 꼴(① ② 둘 ④) → `RuleSet.BadRule`(전 — IAE·PatternSyntax·ClassCast) · ③ 은 임시 폴더의 `customRules` 파일 내용 `~` 한 줄 → `BadRule`(잘못된 글자 경로 꼴은 Windows 에서만 — `assumeTrue(os.name 이 Windows)`) · `CheckRoutesTest` — ① 프로필로 `GET /api/check/rules` 가 400(전 500) · 지킴이 `common.alert: {regex: "("}` 는 지금처럼 `BadRule`
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,CheckRoutesTest,CleanupRoutesTest,PureRulesJsonTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 5-149 — 붙여넣기 언어 추정: 긴 한 줄의 제곱 시간 · `record`·`enum` 으로 시작하는 JS · 메서드만 붙인 자바 (점검 4차 D4-04 · D4-05 + 설계 76 한계 D-11)
- 대상: `core/check/CheckRunner.java`(`JAVA_DECL:26` · `javaDecl:64-74` · `guessLang:239` 근처) · `CheckRunnerTest`
- 사실(점검 탐침):
  - `var s = "` + `a class b `×n + `";` 한 줄 → n=1만 4.1초 · 2만 21.9초 · 4만(400KB) 102초 — `javaDecl` 이 맞은 자리마다 `t.substring(줄머리, m.start()).strip().split(…)` 을 한다(`5-135` 가 만들었다)
  - 자동 추정 `var rows = grid.rows;⏎record = rows[0];⏎alert(record.name);⏎eval(x);⏎` → `java` + `java.parseError` 하나뿐(기대 js · `3 common.alert`·`4 security.jsEval`). `callback(⏎    record⏎);` · `enum Color { Red, Green }`(TS) · `abstract class A {`(TS) 도 `java`
  - 한계 D-11: `@RequestMapping("/a.do")⏎public String list(@RequestParam String id, ModelMap model) throws Exception {…}` → `js`, 지적 0 · `private static final Logger LOG = …;⏎public void f() { e.printStackTrace(); }` → `js`, 0(틀린 것이 안 보이고 「지적 없음」 으로 나간다)
- 변경:
  1. `javaDecl` — `m.start() - 줄머리 > 40`(수식어 셋 길이)이면 그 맞춤을 건너뛴다(자르기·쪼개기 전에)
  2. `record`·`enum` 은 뒤가 `이름 (`·`이름 <`·`이름 {`·`이름 implements` 일 때만 자바 선언으로 본다(`record = …`·`record : 1`·줄 끝 `record` 는 아니다). `enum 이름 {` 와 `abstract class` 는 TS 도 같은 꼴이라 **그것만으로는** 자바로 안 본다 — 다른 자바 신호(`package`·`import a.b.C;`·접근 제한자 붙은 선언)가 같이 있을 때만
  3. 메서드·필드만 붙인 자바(D-11) — 새 신호: 줄머리(들여쓰기 허용)가 접근 제한자(`public`·`private`·`protected`) + 수식어 0~3(`static`·`final`·`abstract`·`synchronized`) + **형식 토큰**(대문자로 시작하는 이름 또는 기본형·`void`, 제네릭·배열 꼬리 허용) + 이름 + `(`·`;`·`=` 인 줄. TS 는 제한자 뒤가 바로 이름이거나 `async`·`get`·`set`·`override`·`readonly`·`declare` 라 그 낱말이 제한자 뒤에 오면 뺀다 · 줄머리 `@Override` 또는 `) throws 대문자` 가 있는 줄도 자바 신호. 그 신호가 하나라도 있고 TS 신호(`: 형식` 꼴 매개변수·`=>`·`function`·`const`·`let`)가 없으면 `java`. 정규식은 줄머리 고정(`(?m)^…`)·묶음 반복 없이, 줄마다 판정(긴 한 줄에서도 선형)
  - `guessLang` 이 기준선 줄 있는 메서드면 새 판정은 `private static boolean` 도우미로
- 시험(먼저): `CheckRunnerTest`(guessLang 사례) — 시간: n=4만 한 줄이 `assertTimeout(Duration.ofSeconds(2), …)` 안(전 102초) · `record = rows[0]` 꼴·`callback(⏎ record⏎);`·`{ record : 1 }`·TS `enum Color { Red, Green }`·TS `abstract class A {` → `js`(또는 지금 js 계열 추정 그대로, 전 `java`) · 자바 다섯(점검자 표본: 애너테이션 붙은 메서드 · 필드 + 메서드 · `List<Map<String, Object>>` 반환 · `void` · 배열 반환) → `java`(전 `js`) · TS 열하나(점검자 표본 — `public name: string;` · `private async load()` · `public get x()` · `protected readonly a = 1` · `public static create(): Foo` 등) → 지금처럼 js 계열 · 지킴이 — `public record Point(int x, int y) {}`·`enum Color { RED }` + `package a;` → `java`
- 의도된 골든 변화: 없음(붙여넣기 추정은 corpus 골든에 안 든다)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRunnerTest,CheckRoutesTest,SmokeHtmlUnitTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 한계(이력에): 제한자 없는 자바 메서드(`String list() {`) · 수식어 넷 · 같은 줄 애너테이션 + 선언 · 프롤로그 없는 XML(`<web-app>`)은 여전히 못 가른다(화면에서 언어를 고른다)
- 의존: S0

#### 5-150 — jsp 잔손: script 안 주석뿐인 스크립틀릿 · 스크립틀릿 문자열 속 `<textarea>`·`<pre>` (점검 4차 D4-03 · D4-08)
- 대상: `core/check/RegexRule.java`(`skipped:79-83`) · `core/check/Zones.java`(`rawLines:113-140`) · `CheckRulesTest` · `ZonesTest`
- 사실(점검 탐침): (jsp) `<script>⏎<% //if (admin) { %>⏎  show();⏎<% //} %>⏎</script>⏎` → `2 jsp.scriptlets [2개 (기준 0)]`(기대 0 — 같은 두 줄이 script 밖이면 0). `5-137` 이 `bare()` 로 묶으며 차례가 뒤집혔다 — 줄 주석 지우기가 `%>` 를 먼저 비워 `<%` 가 짝을 잃는다 · (jsp) `<% String t = "<textarea>"; %>⏎<script>⏎console.log(1);⏎</script>⏎` → `rawLines` 전부 참, 정리가 「줄 3: <pre>·<textarea> 안 console」 로 건너뛴다(기대 삭제). `<a href="#" onclick="$('#x').html('<pre>')">x</a>` 도 같다
- 변경:
  1. `skipped` 의 jsp 가지만 차례를 되돌린다 — `scriptComments(blankCommentScriptlets(stripComments(scriptWrappers(text), lang)))`. `bare` 는 그대로(고침·링크가 쓴다). `RegexRule` 안의 `private static` 이라 그대로 부를 수 있다
  2. `Zones.rawLines` — 훑는 글에서 `<% … %>` 구간과 태그 안 따옴표 값(`"…"`·`'…'`)을 같은 길이 빈칸으로 비운 뒤 `<textarea`·`<pre` 를 찾는다(`BlankRelRule.dead`·`attrs` 의 비우기 꼴 — 같이 쓸 수 있으면 그 도우미를)
- 시험(먼저): `CheckRulesTest` — 첫 입력의 `jsp.scriptlets` 0(전 2개) · 지킴이 — script 밖 주석뿐인 스크립틀릿 0 · 코드가 든 스크립틀릿은 지금처럼 센다 · `ZonesTest.rawLinesSkipsScriptBodies` 옆 — 둘째·셋째 입력의 `rawLines` 가 전부 거짓(전 — 참) · 진짜 `<textarea>` … `</textarea>` 안 줄은 참(지킴이) · `CleanupTest` — 둘째 입력의 `console.log(1);` 이 지워진다(전 — 건너뜀)
- 의도된 골든 변화: corpus `check-*`·`cleanup-*` 0 기대(점검자 표본 실측 0건)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,ZonesTest,CleanupTest,PureRulesJsonTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-146c

#### 5-151 — `mybatis.noWhere`: 글의 괄호가 안 맞는 동적 SQL 뒤 최상위 `<where>` (점검 4차 D4-06)
- 대상: `core/check/MyBatisRules.java`(`noWhere:260-273` · `:184-186` · `topWhere:288-301`) · `MyBatisRulesTest`
- 사실(점검 탐침): ① `UPDATE T1 SET A = (SELECT MAX(X) FROM T2 WHERE K IN (⏎<foreach … close="))">#{k}</foreach>⏎<where> ID = #{id} </where>` ② `UPDATE T1 SET A =⏎<choose><when test="x">NVL(#{a},</when><otherwise>NVL(#{b},</otherwise></choose>⏎0)⏎<where> ID = #{id} </where>` → 둘 다 `mybatis.noWhere` 오탐(기대 0). `5-133` 이 `whereTag ||` 를 빼고 태그가 넣은 WHERE 를 글의 괄호 깊이로만 잰다 — 속성(`open`·`close`·`suffix`)의 괄호와 갈래마다 여는 괄호는 글에 한쪽만 남는다
- 변경: `whereTag`(문장에 `<where>`·`<trim prefix="WHERE…">` 태그가 있었나)를 되살려 **한 가지에만** 쓴다 — 문장 글의 괄호가 끝에서 안 맞으면(깊이 ≠ 0) 깊이 판정을 못 믿으니 태그를 믿는다. 괄호가 맞으면 지금 판정 그대로(`5-133` 의 서브쿼리 안 `<where>` 는 여전히 지적). `startElement(4)` 는 기준선 17 — 그 메서드에 분기를 더하지 않는다(표시를 세우는 자리가 그 안이면 대입 한 줄만)
- 시험(먼저): `MyBatisRulesTest` — 두 입력의 `mybatis.noWhere` 0(전 — 지적) · 지킴이(5-133 사례 그대로 초록): 서브쿼리 안에만 `<where>` 가 있는 UPDATE 는 지적 · 최상위 `<where>` 0 · `<trim prefix="WHERE (" suffix=")">` 0 · 리터럴·주석 속 괄호 0
- 의도된 골든 변화: corpus `check-*` 의 `mybatis.noWhere` 가 **줄 수 있다**(괄호가 안 맞는 동적 SQL — 수를 이력에)
- 검증: `bash scripts/mvn.sh -q test -Dtest='MyBatisRulesTest,CheckRulesTest,PureRulesJsonTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

#### 5-152 — 선언 형식 찾기: 다른 범위의 같은 이름 패턴 변수 (점검 4차 D4-07)
- 대상: `core/check/DeclaredTypes.java`(`find:52-54` · `patternNamed:69-72`) · `JavaRulesTest`(또는 `ValueAstTest`)
- 사실(점검 탐침): `if (other instanceof Integer d) { return d > 0; }⏎else { double d = z; if (d == z * 3) { return true; } }` → 지적 0(기대 `java.floatEquals` — 첫 줄을 `if (other == null) { return false; }` 로 바꾼 대조는 지적). `5-139` 가 「callable 어디든 같은 이름 패턴 변수가 있으면 형식 모름」 으로 넣었는데 `find` 를 값 고침만이 아니라 판정(`java.floatEquals` · `ValueAst.stringy`·`isBoxed` · `ResourceAst:213·234` · `SecurityAst:207`)도 쓴다
- 변경: `find` — 조상 범위를 먼저 올라 지역 변수·매개변수 선언을 찾고(찾으면 그 형식), **클래스 필드로 떨어질 때만** `patternNamed` 를 본다(`5-139` E-09 가 막으려던 것은 「패턴 변수가 필드를 가린다」 뿐이다)
- 시험(먼저): `JavaRulesTest` — 사실 줄 입력 → `java.floatEquals` 한 건(전 0) · 지킴이(5-139 E-09): 패턴 변수 `s` 가 필드 `s`(String)를 가리는 메서드에서 값 고침이 `s` 를 String 으로 안 본다(`ValueAstTest`·`CleanupTest` 의 `C5` 사례 그대로 초록)
- 의도된 골든 변화: corpus `check-*` 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaRulesTest,ValueAstTest,CleanupTest,ResourceAstTest,SecurityAstTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

### V-71a — 정적 분석 · corpus 골든 확인
1. `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify`(PMD·SpotBugs·ErrorProne) — 새 짝은 코드로 고친다(기준선에 안 더한다). 고치기 둘. 새 정규식(`QNAME4`·`AMBIGUOUS`·`CTX_VALUE`·`DEVICE`·`IBATIS_HASH`·`HEAD`·제약 이름의 `PART`·붙여넣기 추정)이 SpotBugs REDOS 에 걸리면 그 식을 단계로 쪼개거나 손 훑기로
2. `bash scripts/verify.sh --corpus`(약 15분). 다른 JVM·컨테이너를 같이 띄우지 않는다. 허용 변화 —
   - `check-*.json`·`check-*-hits.txt` — **주는 것**: 비 JS type `<script>` 몸의 지적(`5-146b` — 표본 1파일) · 괄호가 안 맞는 동적 SQL 의 `mybatis.noWhere`(`5-151`). 가려진 이름의 `java.equalsLiteralFirst`(`5-155`). **느는 것**: 감싼 script 안 `security.piiCardPlain`·`jsp.blankNoopener`·absent 규칙(`5-146c`) · `java.floatEquals` 류(`5-152`) · 줄끝이 섞인 파일의 `file.lineEnding`(`5-157a`). 그 밖(`5-146a`·`5-150`)은 0 기대
   - `cleanup-*`(건수·건너뜀·컴파일) — 로그 고침의 `EARLY_INIT` 건너뜀이 **줄고** 고침이 그만큼 는다 · `!!(` 호출 console 삭제가 준다(`5-147`) · 먼저 도는 초기화의 건너뜀이 **는다**(`5-147` 덧붙임) · debugger 삭제가 **줄고** 건너뜀이 는다(`5-153`) · 가려진 상수의 값 고침이 준다(`5-155`) · 줄끝 통일이 **는다**(`5-157b`) · jsx·tsx 의 console 삭제가 준다(`5-158`) · 그 밖 행의 「의도된 골든 변화」 줄 그대로. `CleanupCompileCorpusTest` 의 컴파일 실패가 **느는 것**은 등급 A — 7절
   - `analyze-*`(`.json`·`-joins`·`-unresolved`·`-jspurl`·`-orphan-jsp`·`-dead-stmt`·`impact`·`spot`·`consistency`) — 조인: 별칭 글자 표 이름이 진짜 표로(`6-99`) · 목록 안 쉼표 조인이 **는다**(`6-100`) · 표·CRUD: `6-101` 의 네 꼴 · 문장·CRUD 가 **는다**·`depth` 미해결이 **준다**(`6-93b`) · `ambiguous` 가 **준다**(`6-93a`) · 그 밖(`6-94`~`6-97`·`6-102`)은 0 기대. `table` 미해결(등급 A)이 새로 나오거나 `depth` 가 **늘면** 7절(`6-93b` 사다리 먼저)
   - `deliverable-egov-relations.json` — 조인 변화(`6-99`·`6-100`)를 따라가는 것만
   - `security-*`·Juliet·Benchmark 채점 — 재현율이 **내려가면** 7절
   - 그 밖(`gen-*`·`hr-insert-*`·`masking-*`·`logsql-*`·`csv-*`·`strip-*`·`sqlfixture`·`metrics`)은 diff 0 기대 — 바뀌면 원인 행과 댄다
   - 등급 B baseline 이 상한(표본의 2%)을 넘으면 7절. 허용 밖 변화 — 고치기 둘, 넘으면 `wip/V-71a`
3. diff 줄 수·종류별 건수(전 → 후)·벽시계를 이력에
- 의존: 코드 행 전부

### R-38 — 손 판정 · 처분 표
- `design/compare-2026-10-28.md` 끝에 `## ㊶ 번들 92 — 가로 점검 4차 + 대기 행 여섯 + 후보·잠재(2026-11-24)`:
  1. 처분 표 — 행마다(행 · 점검 키 · 강제 지점 시험 · 닫음/한계/wip). 한계(각 행 「한계」 줄)를 행마다
  2. `V-71a` 골든 변화 표(파일 종류별 건수 전 → 후). 0 이면 「없음」
  3. 손 판정(SQL 본문·코드·값은 안 적는다 — 파일·줄·문장 id·규칙 id·이름만) —
     - **새·바뀐 조인 각 스물까지**: `6-99` 의 꼴(UPDATE 별칭·따옴표 별칭·MERGE)인지 · `6-100` 의 꼴(힌트·ON·USING 뒤 쉼표)인지. 어느 꼴도 아니면 7절. **빠진 조인 전부**: 별칭 글자가 표 이름이던 것(`6-99`)만
     - **가운데 층으로 새로 풀린 문장 스물**(`6-93b`): 호출 자리 리터럴이 그 문장 id 인지(다른 메서드의 것을 가져왔으면 7절)
     - **`6-93a` 로 빠진 문장 전부(스물 넘으면 스물)**: 그 클래스의 명시 import 가 프로젝트 밖 FQCN 인지. 같은 패키지·별 import 인데 빠졌으면 7절
     - **새 check 지적 열**(`5-146c`): 그 줄이 감싼 `<script>` 안 살아 있는 코드인지 · **빠진 `mybatis.noWhere` 전부(스물까지)**(`5-151`): 그 문장에 최상위 `<where>`·`<trim prefix="WHERE">` 가 있는지
     - **새 로그 고침 열**(`5-147`): 로거 앞 초기화가 `getClass()` 뿐인지
     - **debugger 삭제가 건너뜀으로 바뀐 줄 열**(`5-153`): 앞 줄이 문장 끝이 아닌지(세미콜론 없는 js · 제어문 머리) · **새 `file.lineEnding` 지적 열**(`5-157a`): 그 파일에 CRLF 와 LF 가 정말 같이 있는지(아니면 7절)
     - **프로그램 URL 이 바뀐 것 전부**(`6-95`): 접두가 인터페이스·조상의 타입 수준 `@RequestMapping` 값인지
  4. 끝에서 끝 — `run.bat --port 18873 --no-browser`(빈 `--data-dir`) → ① `.git/config` 에 `[include]` 를 넣은 임시 저장소로 `curl -s "http://127.0.0.1:18873/api/check/vcs?path=<그 폴더>"` 의 사유에 「include」(`0-128`) ② 덮어쓰기 `security.cfgErrorPage: {params: {when: ""}}` 를 넣은 프로필로 `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:18873/api/check/rules` 가 400(`5-148`) → 서버를 끈다
- 코드·레인 지문이 안 바뀐다
- 의존: V-71a

### V-71 — `--db`
1. 여유 RAM — PowerShell `(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB`. 15 미만이면 `docker ps` 로 다른 세션 컨테이너를 본다 — 사용자 결정(2026-11-23): 밤에는 projectshop 을 내려 둔다, **묻지 않고 돈다**. 그래도 15 미만이면 30분 뒤 한 번 더 재고, 여전하면 7절. `--corpus` 가 끝난 뒤에만(겹쳐 띄우지 않는다)
2. `bash scripts/docker-up.sh` → `bash scripts/verify.sh --db`(컨테이너 아홉, 17~35분). 다른 JVM 을 같이 띄우지 않는다
3. 허용 변화 — 골든 diff 0 기대(DdlReader 를 받는 골든은 수·이름만 얼린다 — 점검 4차 A 확인). 움직일 수 있는 것: `corpus/ddl-summary.json`·`ddl-unreadable.txt` 의 못 읽은 줄이 **준다**(`1-137` — 공백 든 제약 이름·`ALTER ONLINE TABLE`) · `alterRoundTrip`·`alterTwoReleases` 의 문장 수가 **준다**(`1-132` — 이름만 다른 제약의 DROP + ADD) · `ddlRoundTrip` 의 FK 실패가 **준다**(`1-134`) · DB 대조 행의 상태가 ERROR → PASS(`6-98` 끝 `;`·`6-102`). 기존 등급 A(`alterRoundTrip`·`alterTwoReleases`·`ddlRoundTrip`·`insertRuns`·`readOnlyBypasses`·`qualitySqlRuns` …)는 0 — 새로 빨개지면 원인 행(`1-131`~`1-135`·`4-52`·`4-53`·`9-11`·`2-57`)을 찾아 고치기 둘, 넘으면 그 행만 `wip/<행>` 로 되돌리고 다시 돈다. 그 밖 줄이 바뀌면 7절
4. `verify.sh --db` 와 `git push` 는 따로 낸다
- 의존: R-38

### 마무리
CLAUDE.md 「마무리」 1~8 그대로 — 독립 리뷰 하나(**`model: fable`, effort `xhigh`** — 서브에이전트 호출에 둘 다 명시. Review Focus: ① 절대 규칙 1 — `GitConfigGuard` 를 안 거치는 git 호출 · 설정 글 판정이 놓치는 꼴(구역 머리 변종·이은 줄 `\`·따옴표·대소문자) · `refusePartialClone` 의 남은 키 ② 절대 규칙 3 — `SqlProbe.ambiguous` 가 놓치는 「따옴표 짝이 뒤집히는」 꼴 · 시험·경고·compare ㊶ 에 SQL 본문·값이 실리는 자리 ③ `RegexRule.lineCut`·`commentStart` — 주석 뗀 글의 길이·줄 수 불변식을 깨는 남은 길 · `scriptBodies` 가 실물 JSP 의 여는·닫는 태그를 놓치거나(지적이 조용히 사라진다) 주석을 몸으로 읽는 꼴 ④ `StmtArgs.key` — 방문 열쇠가 프레임 예산(`MAX_FRAMES`)을 넘겨 `depth` 를 내는 길 · 다른 호출의 인자를 문장으로 읽는 길 · `reassigned` 가 맞던 것을 미해결로 바꾸는 꼴 ⑤ `JavaGraph.lookup`·`TypeBind.importsOther` 의 `fqn` 비교 — 중첩·같은 파일·별 import 에서 맞던 것을 빼는 길 · `TypeBind.head` 가 머리를 잘못 붙이는 꼴 ⑥ `SqlJoins` — `aliasFirst`·`FromList`·`opener` 가 맞던 관계를 빼거나 지어내는 꼴 · `SqlTables` 의 분기 뒤 괄호 판정 ⑦ `DdlDefault.span` — 컬럼 속성 낱말·다음 컬럼을 기본값으로 삼키는 꼴 · `AlterGen.alignByShape` 가 다른 제약을 같은 것으로 짝짓는 꼴 · `DdlGen.exactIn`·`refColumn`·`generate(…, exactSchema)` 가 같은 방언 출력을 바꾸는 길 ⑪ 절대 규칙 2·6·7 — `LocalFiles.forbiddenByKey` 의 기억이 금지 폴더를 지나치게 하는 길 · `ReadOnlyJdbc.glued` 의 `LONGEST` 가 놓치는 꼬리 ⑫ 코드 정리 — `Cleanup.hanging` 밖의 debugger 삭제 길 · `5-157b` 의 섞임 상태가 앞 단계를 되돌리거나 파일을 안 쓰는 남은 길 · `ValueAst.staticFinal`·`SwitchFix.laterLocal` 이 놓치는 가림 ⑧ 기준선 — 오른 값·새 줄이 없는가(낮춘 줄은 행에 적힌 것뿐인가) ⑨ 골든 diff 가 행의 「의도된 골든 변화」 와 다른 자리 ⑩ 새 판정에 양성·음성 단언이 다 있나) → `bash scripts/gate-probe.sh`(「패치 갱신 필요」 면 다시 뜬다) → 「순수본 밀린 목록」: `PLAN.md` `5-100` 행 사실 칸 끝에 `5-146a`·`5-146b`·`5-146c`·`5-150`·`5-151` 줄(와 `5-149` — 순수본 코드 검사 화면 JS 에 붙여넣기 언어 추정이 있을 때만)을 더한다 → `verify.sh`(리뷰 고침이 건드린 무거운 레인만 다시 — corpus 먼저, 그다음 db) → push → PR(본문에 행마다 시작 세 줄 표 — `V-71a`·`R-38`·`V-71` 포함) → CI 폴링 → claude[bot] 「아니오」 처분 → **머지 위임** `gh pr merge N --merge --delete-branch` → 닫기 커밋은 새 로컬 가지에 → 끝 보고.

## 5. 금지 사항

- `pure/` · `ext-fixes.yaml` · `config/pmd/exclude-from-failure.properties` · `config/spotbugs/exclude.xml` · `pom.xml` · 마이그레이션 V001~V017 · `rules.yaml` · `pure-rules.json` 을 고치지 않는다. 새 마이그레이션·새 규칙·새 의존성·새 라우트·새 화면 0. `Unresolved.KINDS` 를 늘리지 않는다
- **self-check 기준선** — 값을 올리거나 새 줄을 만들지 않는다. 내려가는 갱신도 행에 적힌 것만(이 번들에는 없다 — 내려가면 공통 사다리대로 갱신하고 이력에)
- 메서드 수 기준선 클래스(`JavaGraph` 34 · `JpaIndex` 38 · `AlterGen.Run` 40 · `Definitions` 34)에 메서드를 더하지 않는다 · 인자 수가 이름표인 메서드(`repoCall(8)` · `build(8)` · `writeGuide(11)` · `preview(8)` · `apply(10)` · `Def`·`Col`·`Table` 생성자)의 인자 수를 바꾸지 않는다 · 기준선 줄 있는 메서드(`walk(5)`·`views(3)`·`index(4)`·`cLike(2)`·`load(2)`·`extract(2)`·`run(4)`·`startElement(4)`·`item(3)`·`read(1)` …)에 분기·줄을 더하지 않는다(식을 도우미 호출로)
- 기존 경고·사유·미해결·`[손]`·`[알림]` 글자를 바꾸지 않는다(새 경우는 새 글자로) · 기존 시험 단언을 지우거나 느슨하게 바꿔 통과시키지 않는다(옛 결함을 못 박은 단언은 공통 사다리대로 — 이력에 적고 바꾼다) · 골든의 옛 열쇠·사례는 그대로
- 행이 「안 고친다」 고 적은 메서드·파일(`ReadOnlyJdbc` 의 16진 꼬리 판정 — `1-129` · `MaskScope` — `3-27` · `cLike` — `5-146a` · `JavaGraph` — `6-93b` · 람다·익명 몸 판정 — `5-147` · `deleteConsole` — `5-153` · `worse` — `5-157b`)을 고치지 않는다. 행의 대상 밖 파일을 셋 넘게 고쳐야 하면 7절
- 화면(HTML·JS)·`common.js`·`common.css` 를 고치지 않는다(행에 적힌 자리가 있으면 그 한 자리만)
- 픽스처 폴더(`fixtures/analyze/mapper/`·`fixtures/analyze/jpa/`·`fixtures/check/`)에 파일을 더하지 않는다(인라인 `Source`·임시 폴더)
- 시험·경고·`[손]`·`[알림]` 글·compare 절·로그에 실제 사업 코드·SQL 본문·값·비밀번호를 쓰지 않는다(절대 규칙 2·3). 시험 이름은 `T1`~`T5`·`T92_*`
- 탐침·시험에서 네트워크에 닿지 않는다 — `0-128`·`0-126` 의 네트워크 꼴 경로는 **글자**로만 쓴다(`//h/s/x` 를 여는 git 명령을 돌리지 않는다 — 판정은 설정 글 읽기에서 끝난다)
- 무거운 판을 겹쳐 띄우지 않는다(정적 분석 → `--corpus` → 손 판정 → RAM 확인 → Docker → `--db`)
- 다른 저장소(portfolio·satchel)를 고치지 않는다 · worktree·`finishing-a-development-branch` 를 쓰지 않는다 · 새 가지를 안 딴다(닫기 커밋 빼고)
- 뺀 것을 하지 않는다 — `5-100`·`5-31`·`5-134` 순수본 재주입 · 반입 zip · 조건부 `5-14`·`5-15`·`6-9` · 보류 `2-7`·`2-8`·`5-7`·`4-10` · `2-17` · 부록 A 4절(행으로 안 세운 것)

## 6. 최종 검증

- `bash scripts/verify.sh` 초록 · `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify` 초록 · `bash scripts/verify.sh --corpus` 초록(등급 A 0) · `bash scripts/verify.sh --db` 초록(컨테이너 아홉, 등급 A 0) · `bash scripts/gate-probe.sh` 열 전부 산다
- 강제 지점 시험이 전부 있다(S0 표, `wip/` 로 뺀 행 빼고) — 코드 행 이력에 RED → GREEN 한 줄
- 손 확인(R-38): compare ㊶ 처분 표 · 손 판정 · `curl` 둘
- 회귀: `golden/` diff 는 행의 「의도된 골든 변화」 와 `V-71a`·`V-71` 의 허용 변화뿐 · `self-check.txt` diff 0(내려간 줄이 있으면 이력에) · `pure-rules.json`·`rules.yaml` diff 0 · 규칙 수·마이그레이션 수·`Unresolved.KINDS` 그대로 · 커버리지 하한 유지
- 분할표 — 대기 여섯(`5-146`·`0-126`·`6-93`·`3-27`·`5-147`·`1-129`)과 새 행이 다 닫혔다(또는 `wip/` 사유) · 15장 번들 92 완료 · `5-100` 행에 밀린 목록 줄
- CI 초록 · claude[bot] 처분 · PR 머지 · 메모리 `bundle92-done` + `MEMORY.md` 한 줄

## 7. 중단 조건

다음이면 임의로 풀지 말고 멈추고 보고한다(어느 스텝 · 무엇이 예상과 달랐나 · 가능한 선택지).
- 행의 「사실」 이 실제 코드와 다르다(줄 번호 어긋남은 아님 — 메서드·흐름이 다를 때) · RED 가 행의 「전」 과 다르다(이미 맞게 나온다 — 그 사례는 지킴이로 두고 이력에 적은 뒤 계속, 행 전체가 그러면 그 행을 닫고 이력에 「재현 안 됨」)
- 검증이 2회 연속 실패하고 원인이 이 플랜 범위 밖
- 5절을 어기지 않고는 진행할 수 없다(특히 — 기준선 값을 올리거나 메서드 수 기준선 클래스에 메서드를 더해야만 되는 고침)
- 스텝에 없는 파일을 3개 이상 고쳐야 한다(골든·`self-check.txt` 는 스텝에 있는 것이다)
- 골든의 옛 열쇠·사례가 바뀐다(`sql-joins.json` · `sql-tables.json` · `mapper-index.json` · `jpa-index.json` · `alter-*`·`ddl-*`·`insert-*`) — 행의 사다리가 「그 조각을 빼고 친다」 고 적었으면 그대로 하고 계속
- `V-71a` — 등급 A(`table` 미해결·컴파일 실패·파싱 실패)가 새로 나온다 · `depth` 미해결이 는다(`6-93b` 사다리 뒤에도) · 보안 재현율이 내려간다 · 등급 B baseline 이 2% 를 넘는다
- `R-38` — 손 판정 가운데 「아니면 7절」 에 걸린다
- `V-71` — 기존 등급 A 가 새로 빨강(원인 행을 `wip/` 로 되돌린 뒤에도) · 여유 RAM 15GB 미만이 30분 뒤에도 그대로

번들 모드의 「갈려도 안 멈춘다」 는 위 밖에서만 선다. 행당 계획 밖 결정 둘까지, 넘으면 `wip/<청크>` 로 빼고 다음 행. **무인이다 — 위 조건에 걸려도 그 행(또는 그 무거운 판)만 `wip/` 로 빼고 다음으로 갈 수 있으면 그렇게 한다**(이력·끝 보고에 적는다). 번들 전체를 세우는 것은: 절대 규칙을 어기지 않고는 못 가는 때 · `work/*` 트리가 빨간 채 남는 때 · 등급 A 가 원인 행을 못 찾은 채 남는 때뿐이다. 빠졌을 때 뒤 행 — 「의존」 줄의 선행이 빠지면: 같은 파일이라 이은 것(대부분)은 그대로 친다. 도우미를 받아 쓰는 것만 같이 뺀다 — `5-146b`(`scriptBodies`)가 빠지면 `5-146c` 의 `-->` 없는 감싸개 단언만 뺀다 · `6-93a`(`fqn`)가 빠지면 `6-96` 은 `fqcn` 비교로 친다 · `6-99`(`opener`)가 빠지면 `6-100` 은 `opens` 그대로에 `|| listComma` 만 · `0-129` 가 빠지면 `0-131` 은 그대로 친다 · `5-157a` 가 빠지면 `5-157b` 도 뺀다 · `2-58` 이 빠지면 `8-25` 는 그대로 친다.

## 8. 불확실 항목

사용자만 정할 수 있는 것 — 없음(범위 (나) · 머지 위임 · 밤엔 projectshop 끔 — 메모리 `bundle92-decisions`. 이 설계가 정한 처분은 3절).

실행해야만 알 수 있는 것 — 실행자가 해당 스텝에서 확인한다.
- **첫 스텝 전에**: `git log --oneline -3` · `git status --short` 깨끗 · `git config --local --get-regexp zzprobe`(남아 있으면 「첫 손」 대로 지운다) · `docker ps`
- **행마다 RED 가 「전」 과 같은가**(시험 실행) — 점검 탐침은 `fccf261` 의 `target/app.jar` 로 돌린 것이다. 다르면 7절 첫 줄
- **행마다 self-check 값**(`-Dtest=SelfCheckTest`) — 설계는 줄 있는 메서드를 도우미 호출로만 바꿨다. 오르면 공통 사다리
- **git 이 UNC include·경로를 실제로 여는가**(`0-128`) — 네트워크라 설계·실행 어디서도 못 돌린다. 판정은 설정 글에서 끝나므로 시험은 글자 판정만 잰다. 실측은 사람이 할 것 ⑫
- **`StmtArgs.key` 를 바꾼 뒤 corpus 의 `depth`·문장 수**(`6-93b`) — `V-71a` 허용 변화 · 사다리
- **`FromList`·`aliasFirst` 가 corpus 조인을 얼마나 움직이나**(`6-99`·`6-100`) — `V-71a` · `R-38` 손 판정
- **이름만 다른 제약의 짝짓기·FK 자식 타입이 `--db` 왕복을 어떻게 움직이나**(`1-132`·`1-134`) — `V-71` 3 의 사다리
- **SpotBugs REDOS·PMD 가 새 정규식·새 클래스를 받는가** — `V-71a` 1

## 부록 A

부록 A 는 `design/78-audit4.md`.
