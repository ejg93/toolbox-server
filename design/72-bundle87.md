# 설계 72 — 번들 87: 번들 86 리뷰 행 열 + 가로 점검 2차 남은 Important 스물둘 + JavaGraph 쪼개기 (Fable 설계 · Opus 실행, 2026-11-21)

## 맥락

번들 86(#131)이 조용히 틀린 출력 Critical 열셋을 닫으면서 마무리 리뷰가 회귀 둘(`1-100`·`1-102`)과 잔손 여덟을 새 행으로 남겼다. 가로 점검 2차(설계 68) 4장의 Important 38 가운데 번들 84 가 셋(F-K5·C-RUN-SOE·E-soe), 번들 86 이 열셋을 닫아 **스물둘**이 남았다. 오늘 약 12시간 무인으로 이 셋(리뷰 행 열 · Important 스물둘 · `0-95`)을 한 PR 로 닫는다.

**사용자 결정(고정 — 다시 묻지 않는다)**
1. 범위 — 리뷰 행 열(`1-100`~`1-104`·`6-46`·`2-44`·`4-36`·`0-99`·`0-100`) + 4장 남은 Important + `0-95`. 회귀 `1-100`·`1-102` 맨 앞
2. `1-101` (라) — `MODIFY (…)` 괄호 꼴은 안 적은 NOT NULL·DEFAULT 유지 · 괄호 없는 꼴은 통째 재정의 + 「방언 모호」 알림
3. `0-100` (가) — `toRealPath` 가 `IOException` 이면 거절
4. 뺌 — `5-114` · 보정 Important · Minor 101 · `5-100`·`5-31` · 조건부 `5-14`·`5-15`·`6-9`
5. 머지 위임 · 무인. 레인은 정적 분석 → `--corpus` → Docker → `--db` 하나씩
6. (2026-11-21 설계 중) Important 는 **4장 22 키만** — PLAN 의 「Important 30」 은 3장 보정 남은 8(R-A-I11·R-G-6·R-G-9·R-C-I2·I3·I6·I7·I8)을 겹쳐 센 수다. 그 여덟은 번들 88~
7. (설계 중) B-03 **(가) 꼬리로 제외** — 여부·코드·내용 꼴 컬럼은 개인정보 후보가 아니다. 빠진 후보는 손 판정으로 전부 확인
8. (설계 중) D-sqlDollarCmt **(가) 백엔드만 지금** — 순수본은 `5-100` 재주입 때 같이

**보정(2026-11-21, `51869aa` 트리)**
- 설계 68 줄 번호는 스물두 키 가운데 둘만 밀렸다 — `SnapshotStore` 컬럼 코멘트 `:155` → `:152` · `MyBatisRules` `:239` → `:240·244`. 결함 자리는 전부 그대로다(번들 84~86 이 같은 파일을 고친 곳은 결함 밖)
- **`4-36` 행의 「DdlGen 과 같게」 는 틀렸다** — `DdlGen.PLAIN:57` 도 `^[A-Za-z][A-Za-z0-9_]*$` 라 `$#` 가 없다. InsertGen 만 고치면 변환 DDL(`"emp$no"` 소문자 객체)과 INSERT(맨 이름 → `EMP$NO`)가 서로 다른 객체를 가리킨다 → 둘을 같은 판정 한 자리로
- **`1-103` 행의 「PK 일 때만」 은 CREATE 문맥에서만 옳다** — `DdlGen.table` 은 PK 만 CREATE 안에 내고 유니크·인덱스는 뒤 문장으로 낸다(그래서 MariaDB 1075). `AlterGen.modifyColumn:348` 의 MODIFY 는 키가 이미 있어 `inKey` 가 맞다(1-89 R-A-C1 이 지킨 것)
- **`6-46` 행의 `REVOKE` 처방은 안 듣는다** — `LIST_END` 는 FROM 뒤에서만 본다. `REVOKE … FROM u1, u2` 는 FROM 이 목록을 다시 연다 → 권한 문장을 토큰 단계에서 뺀다
- **`0-99`** — 실제 꼴은 「주인 javadoc → 새 javadoc → 새 메서드 → … → 주인 메서드」 다(javadoc 둘이 붙어 있다)
- **`0-95`** — 행의 1012 는 지금 1026(메서드 40). 「뷰·설명」 구역의 static 여섯이 115줄
- `rules.yaml` 을 고치지 말라는 줄은 CLAUDE.md·PLAN 에 없다(동결은 `pure/` 뿐). `pure-rules.json` 에 실리는 키는 `id·group·severity·langs·regex·flags·skipComments·max·when·message` — `params` 의 다른 키는 안 실린다
- `migration-immutable.sh` 는 `released-baseline` 파일이 없어 지금 아무것도 안 막는다. 그래도 길이 넓히기는 새 V017 로 한다
- Javalin 6.7.0 에 `io.javalin.http.ContentTooLargeResponse` 가 있다(`m2/` jar 확인). 본문 상한 설정은 저장소에 없다(기본 1,000,000 바이트)
- `core/logical` → `core/gen` 의존은 순환이 아니다(`core/gen` 은 `core/logical`·`core/quality` 를 import 하지 않는다)

## 1. 목표와 범위

목표 — 번들 86 이 남긴 회귀·잔손 열과 가로 점검 2차의 남은 Important 스물둘을 닫고 `JavaGraph` 를 길이 상한 아래로 내린다. 사용자가 겪는 변화: ALTER 로 지운·이름 바꾼 컬럼이 키에 안 남는다 · Oracle `MODIFY (…)` 가 NOT NULL 을 안 지운다 · 옛 스냅샷의 MSSQL IDENTITY 가 안 빠진다 · `emp$no` 가 맨 이름으로 나간다 · 프로필 없이 띄운 서버에서 저장이 된다 · 1MB 넘는 붙여넣기·CSV 가 된다 · `_YN`·`_CD` 컬럼이 마스킹 후보에서 빠진다 · 예약어 표 마스킹 SQL 이 돈다 · 08 코드값 조회 실패가 사유와 함께 400 · 긴 코멘트 하나로 스냅샷이 안 깨진다 · 머리 틀린 기관표준 파일이 사전을 안 비운다 · 받은 저장소의 필터 명령이 안 돈다 · 붙여넣기 「자동」 이 `@Value("${…}")` Java 를 Java 로 읽는다 · 주석 처리한 `${}` 가 error 가 아니다 · `check --groups` 가 나머지를 끈다 · `'it\'s'` 뒤 표가 CRUD 에 나온다 · 0열 주석 든 프로필 저장이 된다 · 코멘트의 `*/`·`"` 가 생성 소스를 안 깬다 · 예약어 컬럼 매퍼가 돈다.

In scope
- 리뷰 행 열 + `0-95` — 기존 행 열하나(번호 그대로)
- Important 22 키 — 새 행 열일곱

| 새 행 | 닫는 키 | 새 행 | 닫는 키 |
|---|---|---|---|
| `0-101` | B-05 · A-17 | `0-103` | C-VCS-EQ · C-VCS-SCOPE |
| `0-102` | B-06 · G1-K03 | `0-104` | G2-K14 |
| `2-46` | B-10 · B-11 | `8-18` | G2-K2 |
| `2-45` | B-08 | `5-117` | D-guessLang |
| `3-19` | B-04 | `5-118` | D-sqlDollarCmt |
| `3-20` | B-09 | `6-47` | G2-K10 |
| `3-18` | B-03 | `7-33` | A-13 |
| `1-105` | F-K10 | `7-34` | A-36 |
| `3-21` | F-K12 · F-K13 | | |

- 실물 DB 단언 `9-9` · `V-66a`(정적 분석 + `--corpus`) → `R-33`(compare ㊱) → `V-66`(`--db`)

Out of scope
- 보정 Important 여덟 · Minor 101 · `5-114` · `5-100`·`5-31` · 조건부 `5-14`·`5-15`·`6-9` — 번들 88~
- chunked·multipart 본문 상한(G1-K15) · 공통표준(moi) 파일의 긴 칸 · `/api/deliverable/build` 의 프로필 없음(`DeliverableRoutes:237`)
- 타입 없는 `MODIFY (a NOT NULL)` 을 읽기(지금처럼 「못 읽은 줄」) · 다른 표 FK 의 `refColumns` 이름 바꿈 · `nq'[…]'`
- 매퍼의 표 이름 인용(A-36 은 컬럼) · 템플릿 `<form:option value="[=v]">` 의 값 이스케이프 · 순수본 JS 의 `security.sqlDollar`
- `ProfileRoutes` 의 `UncheckedIOException` 500(뿌리인 블록 끝만 고친다) · 흐름 꼴 `codecheck: {…}` 머리(G2-K5)
- 안에 든 저장소의 작업 사본 변경을 변경분으로 세기(커밋이 바뀐 것만 본다)
- 화면은 `logical_name.html` 세 줄뿐. 그 밖 `tools/*` 변경 0

**시간 — 넘치면 미루는 차례**: 실행 8시간(03:30~11:30 어림). 코드 행을 시작하기 앞에 `date +%H:%M` 을 본다 — **10:00 을 넘었으면** 남은 코드 행을 건너뛰고 `V-66a` 로 간다. 건너뛴 행은 `대기` 그대로 두고 닫기 커밋에서 PLAN 15장 「번들 88~」 요지에 번호를 적는다. 차례가 곧 우선순위다 — 뒤에서부터 밀린다: `7-34` ← `7-33` ← `6-47` ← `5-118` ← `5-117` ← `8-18` ← `0-104` ← `0-103` ← `3-21` ← `1-105` ← `3-18`.

## 2. 현재 구조 요약

| 행 | 지금 자리(`51869aa`) | 재현 꼴 |
|---|---|---|
| 1-100 | `core/gen/DdlAlter.java` `add:95-104`(제약 줄이 아니면 전부 `DdlReader.item`) · `constraint:107-118`(FK·UQ·PK·CHECK 만, 못 읽어도 조용) · `drop:129-130`(`cols`·`pk` 만) · `replace:175`(`pk` 만 이름 바꿈) | `ADD PARTITION p1 …` → 가짜 컬럼 `PARTITION` · `ADD KEY ix (a)` 조용히 버림 · DROP 뒤 유니크가 없는 컬럼을 가리킴 |
| 1-102 | `core/gen/DdlGen.java` `IDENTITY_WORD:67` · `column:292-294`(떼기는 여기만) · `autoHead:518`·`autoTail:535`(`c.autoIncrement()` 만) · `AlterGen.modifyColumn:337`(`g.type` — 안 뗀다) | V016 전 스냅샷 `int identity`(자동증가 거짓) → mssql→mssql `id int NOT NULL` · `ALTER COLUMN … bigint identity` |
| 1-101 | `DdlAlter.redefine:145-154` → `replace:157-184` — 괄호·맨 꼴·CHANGE 가 `inner()` 뒤 한 길 | `MODIFY (nm VARCHAR2(100))` 가 NOT NULL·DEFAULT 를 지움 |
| 1-103 | `DdlGen.keyLen:379-387`(`len > 0` 이면 그대로) · `autoTail:534-543`(`inKey` — PK·유니크·인덱스) · `table:240-289`(PK 만 CREATE 안) | pg `text`(2147483647) UNIQUE → oracle `CLOB` + UNIQUE · mariadb 유니크 키 자동증가 → CREATE 1075 |
| 1-104 | `core/gen/DdlPull.java` `keyShape:125-137` · `find:80-84`(맨 이름은 `tables.get(bare)`) · `web/InsertRoutes.pick:170-175` | `KEY NUMBER(*,0)` 가 인덱스 · `s1.emp`+`emp` 뒤 `ALTER TABLE emp` 가 `s1.emp` 로 · 둘째 표를 못 고름 |
| 4-36 | `InsertGen.PLAIN:42` · `ident:602-613` · `uid:616-618` · `DdlGen.PLAIN:57` · `quoted:597-610` | DDL 모드 `emp$no` → `"emp$no"` → ORA-00904 |
| 6-46 | `core/analyze/SqlTables.java` `LIST_END:76-78` · `listAfter:251-257` · `extract:152-153`(토큰화 한 줄) | `SELECT a FROM t DECLARE @x INT, @y INT` → `table` 미해결 |
| 2-44 | `core/report/FormulaRange.widened:57-59` | 다중 칸 배열 수식 → `IllegalStateException` 으로 채우기 중단 |
| 0-99 | `AlterGen:434-435`(주인 `uniquesAndIndexes:471`) · `Validation:265-266`(주인 2인자 `columnsIn:273`) · `SqlProbe:300-301`(주인 `names:320`) · `ProfileRoutes:262-263`(주인 `withoutBlockPasswords:268`) | javadoc 이 남의 메서드 위에 |
| 0-100 | `cli/MakeTemplates.java` `inside:52-59` · `real:62-72`(`IOException` → 어휘 경로) | 실제 경로를 못 읽으면 링크 판정이 열림 |
| 0-95 | `core/analyze/JavaGraph.java` 1084줄(클래스 1026·메서드 40) — 「뷰·설명」 `kind:914`·`url:972`·`value:978`·`assigned:1020`·`flatten:1040`·`description:1050`(전부 static) · 상수 `TAG:111`·`DESCR_MAX:112` | `metric.classLength` 1026 > 1000 |
| 0-101 | `web/LogicalRoutes.outFile:220-229`(`orElseThrow`) ← `:70·147·213·271` · `GenRoutes:126` | 프로필 없이 저장 → 500 |
| 0-102 | `web/App.java` `create:142-164`(상한 설정 없음) · `jsonErrors:204-218` | 1MB 넘는 본문 → JSON 아닌 413 |
| 2-46 | `web/DeliverableRoutes.java:419-437`(IAE 만) · `core/deliverable/CodeAndLink.codeDoc:136` | 없는 표 → 500 · 빈 `nameCol` → `IndexOutOfBounds` 500 |
| 2-45 | `core/quality/QualitySql.render:99-102` · 틀 `resources/quality/d90.json:27-34`(순수본 추출) | 키 셋 → 셋째 버림 · 키 하나 → `후보키2` 남음 |
| 3-19 | `core/logical/Masking.id:287-296` ← `sql:199·205` | `UPDATE dbo.User SET` — 문법 오류 |
| 3-20 | `LogicalRoutes.MaskCol:44` · `:109-116` · `tools/logical_name.html:443-450·481-482` | 한 스키마만 빼도 다른 스키마 같은 컬럼이 빠짐 |
| 3-18 | `resources/logical/masking.yaml` · `Masking.detect:113-149` · `excluded:151-161` · `rules:68-92` | `EMAIL_YN`·`ADDR_TYPE_CD`·「이메일내용」·「메뉴이름」·`WEB_ADDRESS` 가 후보 |
| 1-105 | `core/meta/SnapshotStore.save:65-84` · `V001:46·50·58·79` · `web/MetaRoutes:41-49` | 코멘트 4,001자·스키마 401개·메모 1,001자 → FAILED, 글에 INSERT 문·값 |
| 3-21 | `core/dict/DictStore.putUser:310-322` · `importOrg:267-307` · `web/DictRoutes:107-129` | 201자 → 500 · 가져오기 반쯤 · 한 열 파일 → 사전 0 건 |
| 0-103 | `core/vcs/WorkingCopy.filtersOff:161-177` · `gitChanged:179-183` | `[filter "a=b"]`·`config.worktree`·안에 든 저장소의 clean 이 status 에서 돈다 |
| 0-104 | `core/profile/ProfileStore.saveCodeCheck:171-174` · `splice:206-224` | 블록 안 0열 주석 → 500·409·`rules:` 둘 |
| 8-18 | `cli/CodeCommands.java:83-87` ↔ `web/CheckRoutes.load:532-537`(`putAll`) | `--groups file` 인데 다른 묶음 지적으로 끝 코드 3 · 오타 통과 |
| 5-117 | `core/check/CheckRunner.guessLang:216-228`(`${` → jsp) | `@Value("${…}")` Java 에서 AST 규칙이 안 돈다 |
| 5-118 | `resources/check/rules.yaml:219-227` · `core/check/RegexRule.applyText:40-45`·`skipped:73-77` | `-- AND a = ${x}` 가 `security.sqlDollar` error |
| 6-47 | `core/text/SqlTokens.java` `next:75-102` · `literalEnd:111-118` · `wordChar:71`(`$`) | `'it\'s'`·`$$…$$`·`q'[…]'` 뒤 표가 빠짐 |
| 7-33 | `core/gen/GenModel.of:117-118·146` → `templates/gen/egov35/*.ftl`(자동 이스케이프 끔 — `Templates:63`) | 코멘트 `*/`·`--`·`"` 로 자바·XML·JSP 가 깨짐 |
| 7-34 | `templates/gen/egov35/Mapper.xml.ftl:13·21-37·51-99` · `core/gen/FieldRoles.apply:111` | `NOTICE_ID, TITLE, ORDER` — 실행 때 문법 오류 |

함정
- **self-check 기준선**(`golden/check/self-check.txt`, 정확 일치 — 줄이 사라져도·값이 내려도 빨강이라 갱신한다, 오른 값·새 줄은 안 받는다): `DdlGen` `table(1)` 16·`defaultOf(3)` 18·`type(2)` 24 · `AlterGen` `Run` 메서드 40·`modifyColumn(7)` 40 · `DdlReader` `item(3)` 30·`read(1)` 30·중첩 6(**메서드 30 = 상한**) · `InsertGen` `generate(4)` 20·`buildUpsert(5)` 24·`csvLiteral(5)` 31·`genVal(4)` 36 · `GenModel.of(4)` 복잡도 34·**길이 107** · `Validation` `parse` 28·`of(5)` 56 · `SqlTables.extract(2)` 17 · `SqlProbe.run(4)` 17 · `JavaGraph` 길이 1026·메서드 40·`value(4)` 18·`entityManager(4)` 19·`views(3)` 19·`index(4)` 21·`querydsl(3)` 22·`run(2)` 31·`walk(5)` 42·중첩 5·`repoCall(8)` · `LogicalRoutes.register(4)` 복잡도 24·**길이 128**·중첩 5 · `DictRoutes.register(2)` 21 · `DictStore.importOrg(1)` 중첩 5 · `ProfileStore.saveCodeCheck(3)` 31 · `CheckRoutes.postRun(6)` 44·길이 120 · `GenRoutes.register(4)` 25 · `InsertRoutes.register(3)` 23 · `DeliverableRoutes` `postDdl(3)` 19·`postBuild(7)` 23 · `RegexRule.cLike(2)` 27 · `MyBatisRules.startElement(4)` 17 · `XlsxFiller.fill(4)` 25·중첩 5 · `CodeAndLink.codeCandidates(1)` 29 · `LocalOnly.check(1)` 16 · `SnapshotStore.constraint(12)`. 기준선 밖 메서드 상한은 복잡도 15·길이 100·중첩 4·인자 7, 클래스는 메서드 30·길이 1000
- `AlterGen.modifyColumn:348`(mariadb MODIFY)·`addColumn:314` 도 `DdlGen.Gen.column` 을 지난다 — `autoTail` 을 PK 로만 좁히면 1-89 가 지킨 MODIFY 의 AUTO_INCREMENT 가 빠진다
- `DdlReader.Result.notes` 는 「표 이름 대문자 → (컬럼 이름 대문자 → TODO 문구)」 — `GenRoutes:84` 가 DTO 필드 TODO 로 낸다(`DtoGenerator:80`). `InsertRoutes` 는 notes 를 안 본다
- `DdlPull.slot:73-77` — 스키마 없는 둘째 표의 자리는 `".EMP"`(빈 스키마 + 점)
- `QualitySqlTest.render` 도우미는 키를 늘 둘 넘긴다 — 골든 `quality/keydup-*.sql` 은 키 둘 꼴
- `d90.json` 은 `scripts/extract-quality.js` 가 순수본에서 뽑는다 — 고치지 않는다
- 마스킹 후보 수가 실리는 골든 — `golden/logical/masking-candidates.json` · `golden/corpus/masking-egov.json`(98)·`masking-egov-candidates.txt` · `golden/corpus/db-*.json` 의 `maskingColumns`(아홉, `DbCorpusBase:1628`)
- `PureCodeCheckTest`(corpus)는 `fixtures/check/security/*` 를 순수본 JS 와 맞댄다 — 그 폴더에 SQL 주석 픽스처를 더하면 빨강. `CheckRulesTest.fixturesGolden` 은 `mybatis/` 폴더를 건너뛴다
- `SqlTokens.scan` 은 `SqlTables` 와 `core/check/SqlLint` 가 같이 쓴다. `LIKE '%\' ESCAPE '\'` 는 표준 SQL 에서 흔한 꼴이다 — 역슬래시를 무조건 탈출로 보면 뒤가 통째로 리터럴에 먹힌다
- `AnalyzeCorpusTest.egov` 는 `table` 미해결을 등급 A 로 본다
- `Cli.run` 은 `core.vcs` 밖에서 못 부른다(`ArchitectureTest`). `WorkingCopy.ok` 는 종료 코드가 0 이 아니면 던진다 — `git config --worktree` 는 여러 작업 트리 + 확장 꺼짐이면 실패한다
- `LogicalRoutesTest`·`GenRoutesTest`·`DeliverableRoutesTest` 는 `@BeforeAll` 에서 프로필 `t` 로 앱을 띄운다. 프로필 없이 띄우는 꼴은 `SmokeHtmlUnitTest.dbBrowserWithoutActiveProfile:499-502`(`new AppConfig(0, null, …)`)
- `core/gen`·`core/logical`·`core/quality`·`core/meta`·`resources/db`·`resources/logical` 을 고치면 db 레인, `core/*`(profile 빼고)·`tools/` 는 corpus 레인. `web/*`·`cli/*`·`core/profile`·`templates/` 는 java 만. `core/dict`·`core/gen` 시험 폴더는 `LANE_DB` 밖 — db 꼬리표 시험을 거기 두면 `VerifyLanesTest` 빨강(실물 단언은 `web/DbCorpusBase`)
- PMD `AvoidDuplicateLiterals`(넷째 등장)·`UnusedPrivateField`·안 쓰는 import — 빠른 `verify.sh` 는 안 본다(`V-66a` 가 본다)

레인 — 이 번들은 java + corpus + db 전부. 끝에 정적 분석 → `--corpus` → Docker → `--db`.

## 3. 설계 결정 — 전부 [고정]

### §3-1 `1-100` ALTER 읽기 남은 꼴
- `ADD PARTITION|SUBPARTITION|SUPPLEMENTAL|OVERFLOW …` 는 「못 읽은 줄」(행 그대로 — `DROP … PARTITION` 과 같은 처분)
- 제약 줄인데 아무것도 못 읽었으면 「못 읽은 줄」. 그 앞에 읽는 것을 둘 넓힌다 — `KEY|INDEX 이름 (…)` 은 `DdlReader.inlineIndex` · MSSQL `[CONSTRAINT 이름] DEFAULT 식 FOR 컬럼` 은 그 컬럼의 기본값(MSSQL 덤프에 흔해 안 읽으면 「못 읽은 줄」 이 표본 상한을 넘는다)
- DROP 컬럼 — 유니크·인덱스에서 **그 컬럼만 뺀다**(다 빠지면 키도 지운다). 좁아진 유니크는 실제보다 엄격할 뿐이라 생성 INSERT 가 안전하다(MySQL 뜻). 그 컬럼을 품은 FK 는 통째로 지운다. CHECK 는 안 건드린다
- CHANGE 이름 바꿈 — 유니크·인덱스·FK 의 `columns` 를 새 이름으로. 다른 표 FK 의 `refColumns` 는 안 고친다(한계)

### §3-2 `1-102` 타입 글의 identity
- 자동증가 판정 한 자리 `Gen.auto(Column)` — `c.autoIncrement()` 이거나 타입 글에 `identity` 낱말. `column`·`autoHead`·`autoTail` 이 쓴다
- 떼기는 `type()` 머리의 `plain(Column)` 한 자리 — `AlterGen` 은 안 고친다(`g.type` 이 뗀 글을 준다)

### §3-3 `1-101` MODIFY (라)
- 세 갈래 — **괄호 꼴** `MODIFY (정의, …)`: 정의가 `NULL`·`DEFAULT` 낱말을 안 적었으면 옛 컬럼 것을 남긴다 · **맨 꼴** `MODIFY 이름 정의`: 통째 재정의, 그 결과 NOT NULL 이나 DEFAULT 가 실제로 사라졌을 때만 알린다 · **MySQL 전용 문법** `MODIFY COLUMN …`·`CHANGE …`: 통째, 알림 없음
- 알림 통로는 `notes`(컬럼 TODO) — DTO 는 필드 TODO 로, INSERT 화면은 경고 한 줄로. 「못 읽은 줄」 에는 안 넣는다(읽었다)
- 「적었나」 는 정의 글에서 따옴표 글을 비운 뒤 `\bNULL\b`·`\bDEFAULT\b` 로 본다

### §3-4 `1-103` 큰 길이 키 · CREATE 안 자동증가
- `keyLen` — 길이가 대상 상한을 넘는 문자 타입도 키 컬럼이면 255(새 경고 글)
- `autoTail` — `table()` 이 컬럼을 내는 동안(`inCreate`)은 PK 일 때만, 그 밖(AlterGen MODIFY·ADD)은 지금처럼 `inKey`. 유니크·인덱스 키인데 PK 가 아니면 새 경고 글. 기존 경고 글은 그대로

### §3-5 `1-104` KEY 뒤 타입 괄호 · 맨 이름
- `keyShape` — 괄호 안이 `*` 로 시작 · `낱말, 숫자)` 꼴 · (괄호 앞 낱말이 `N?VARCHAR|VARBINARY` 이고) `MAX)` 면 타입 크기 → 컬럼. 타입 이름 목록을 두지 않는다
- `find` — 맨 이름은 `".이름"` 자리(스키마 없는 둘째 표) 먼저, 없으면 맨 이름
- `pick` — `스키마.이름` 이면 스키마까지 맞는 표 먼저, 없으면 이름만

### §3-6 `4-36` 맨 이름 판정 한 자리
- `InsertGen.plain(name, db)` — `$` 는 다섯 방언 다 맨 이름 · `#` 는 oracle·mssql·sybase 만(mysql 은 주석 시작, pg 는 못 쓴다). `InsertGen.ident`·`uid` 와 `DdlGen.quoted` 가 같이 부른다

### §3-7 `6-46` 배치 낱말 · 권한 문장
- `LIST_END` 에 행의 열 낱말을 더한다
- `GRANT`·`REVOKE`·`DENY` 로 시작하는 문장은 토큰 단계에서 뺀다(`;` 까지) — `extract` 에 분기를 더하지 않는다

### §3-8 잔손 `2-44`·`0-99`·`0-100`·`0-95`
- `2-44` — `setCellFormula` 의 `IllegalStateException` 은 그 수식을 그대로 둔다
- `0-99` — 떠 버린 javadoc 넷을 주인 메서드 바로 위로 옮긴다. 코드 변화 0
- `0-100` — 판정을 `static boolean under(base, abs, Real)` 로 빼 `IOException` 이면 거짓(거절). 시험은 던지는 `Real` 을 넣는다
- `0-95` — 「뷰·설명」 static 여섯을 새 `core/analyze/JavaViews` 로. 옮긴 `value(4)` 복잡도 18 줄은 파일 이름만 바뀐 것이라 받는다(이 행만의 예외)

### §3-9 `0-101`·`0-102` 웹
- `outFile` — 프로필이 없으면 `Outputs.dir` 처럼 `out/default/<시각>/`
- 본문 상한 32MB(`App.MAX_BODY`) — 읽기 10MB 파일의 JSON 글·logsql 5백만 자(UTF-8 15MB)가 다 든다. 넘으면 `413 {"message":"본문이 32MB 를 넘는다"}`

### §3-10 `2-46`·`2-45` 산출물·품질
- 08 코드값 조회의 `SQLException` → `400` + `"코드값 조회 실패(표·컬럼 이름·권한 확인): " + 드라이버 글`(같은 파일 `:461` 꼴 — 화면 응답이지 로그·이력이 아니다)
- 빈 `nameCol` 은 이름 칸을 비운다
- keydup — `후보키1, 후보키2` 는 키 전부를 쉼표로 · `후보키1 IS NULL OR 후보키2 IS NULL` 은 키 전부를 `OR` 로. 키가 없으면 틀 그대로(지금 동작). 키 둘이면 출력이 지금과 글자까지 같다

### §3-11 `3-19`·`3-20`·`3-18` 마스킹
- 인용 — `DdlGen.reserved` 면 감싼다. 감쌀 때 접기는 `QualitySql.id` 와 같게(oracle·tibero 대문자 · postgresql 소문자)
- 제외 키 — `MaskCol` 에 `owner`. owner 가 있으면 그 스키마만, 없으면(옛 요청·CLI) 모든 스키마(지금 동작)
- 후보 좁히기 — `masking.yaml` 맨 위 `notPii`(종류를 가리기 **앞에** 본다): 논리명·코멘트가 `endings` 로 끝나거나 컬럼 이름이 `columns` 에 걸리면 후보가 아니다. 날짜 꼬리(`_DT`·`_DE`)는 컬럼 쪽에 안 넣는다(`BRTH_DE` 생년월일). 이름 종류의 `exclude` 「X명」 은 「X이름」 도 뺀다. 주소 `excludeColumns` 에 `ADDRESS`

### §3-12 `1-105` 스냅샷 긴 값
- V017 — `snapshot.schemas`·`snap_table.comment`·`snap_column.comment`·`snap_column.default_value` 를 `VARCHAR(1000000)` 으로(V003 `scope` 와 같은 길이)
- 메모는 라우트에서 1000자 넘으면 400
- 그래도 넘치는 값(SQLState 22001)은 `SnapshotStore.save` 가 INSERT 문·값 없이 칸 이름만 든 글로 바꿔 던진다

### §3-13 `3-21` 사전
- `putUser` — 약어 100자·한글 200자 넘으면 `IllegalArgumentException`(라우트가 이미 400 으로 바꾼다)
- `importOrg` — 연결을 열기 **앞에** 줄을 다 본다: 긴 줄은 줄 번호와 함께 거절, 넣을 줄이 0 이면 거절. 사전은 그대로 남는다
- `/api/dict/import` — 긴 항목은 건너뛰고 `skipped` 에 센다

### §3-14 `0-103` git 필터
- 필터 이름은 `--local` + `--worktree`(실패하면 없는 것으로) 둘에서 `-z` 로 모은다
- 이름에 `=` 나 줄바꿈이 있으면 **거절**(`-c` 로 못 끈다 — 변경분 조회를 안 한다)
- `status` 에 `--ignore-submodules=dirty` — 안에 든 저장소의 작업 트리를 안 들여다본다

### §3-15 `0-104`·`8-18` 프로필·CLI
- 블록 끝 — 0열 `#` 줄은 건너뛰고 뒤 줄이 정한다. `splice` 의 딸린 줄 판정도 같다
- `--groups` — `/api/check/rules` 의 묶음 전부에 켬·끔을 실어 보낸다. 모르는 이름은 끝 코드 2

### §3-16 `5-117`·`5-118`·`6-47` 검사·분석
- 언어 추측 — xml → `<%`·`<c:` 또는 (`<` 로 시작하고 `${`) 면 jsp → Java 꼴 → ts/js. `${` 혼자서는 jsp 가 아니다
- `security.sqlDollar` — `params.sqlComments: true` 면 xml 주석을 지운 글에서 SQL 주석도 지운다. `pure-rules.json` 은 안 바뀐다
- 리터럴 끝 — `\'` 는 **바로 뒤가 낱말 글자일 때만** 탈출 · `$$…$$`·`$꼬리표$…$꼬리표$`·`q'[…]'` 는 리터럴 토큰 하나. 닫는 짝이 없으면 지금 동작

### §3-17 `7-33`·`7-34` 생성기 템플릿
- 코멘트는 `GenModel.safe` 한 자리에서 엔티티로 — 템플릿은 안 고친다. 쓰이는 문맥이 javadoc·JSP 글·JSP 속성·XML 주석 넷뿐이라 엔티티는 HTML·javadoc 에서 원래 글자로 보인다
- 매퍼 SQL 의 컬럼은 `f.columnSql`(`InsertGen.ident`) — `FieldRoles.apply` 가 넣는다(`GenModel.of` 는 길이 상한). resultMap 의 `column=` 은 맨 이름 그대로

## 4. 실행 스텝

행마다 끝은 CLAUDE.md 「Task 끝」 순서(분할표 행·이력 → `bash scripts/verify.sh` → `git commit` 따로 → `task-done`). `src/main/java` 를 고치는 행은 시험을 먼저 쓰고 RED 를 본 뒤 고친다. 커밋 제목 `fix: <번호> <무엇>`(시험만이면 `test:`, `0-95`·`0-99` 는 `chore:`). 줄 번호는 `51869aa` 기준 — 앞 행이 밀면 메서드 이름으로 찾는다.

공통 사다리
- `SelfCheckTest.mainMatchesBaseline` 빨강 — **오른 값·새 줄**이면 분기를 같은 클래스의 `private static` 도우미로 뺀다. **내린 값·사라진 줄**은 `bash scripts/mvn.sh -q test -Dtest=SelfCheckTest -Dgolden.update=true` 로 갱신하고 diff 가 그 줄뿐인지 본다. 오르는 것을 받는 행은 없다. 새 줄을 받는 것은 `0-95` 의 `JavaViews.java|…value(4)` 하나
- `golden/` diff — 그 행의 「의도된 골든 변화」 에 적힌 것만 갱신. 그 밖은 고침이 넓다 → 고친다
- 새 시험의 시간 단언은 `assertTimeoutPreemptively`. 시험 글에 실제 사업 이름·SQL 을 안 쓴다(`T87_*`·`TB_X`)
- 코드 행을 시작하기 앞에 `date +%H:%M` — 10:00 을 넘었으면 1절 「시간」 대로

### S0 — 설계 문서 · 행 · 번들 표 (**실행 세션이 했다** — 실행 세션은 확인만)
- 설계 세션(Fable)이 플랜 승인 뒤: ① 가지 `work/2026-11-16` 그대로(HEAD `51869aa` = main `60b8d4f` + 닫기 하나) ② 이 플랜 전체를 `design/72-bundle87.md` 로 ③ `PROGRESS.md` 「번들 86 닫기」 줄의 `(이 커밋)` → `51869aa` ④ `PLAN.md` 15장 — 「번들 87~」 행을 「번들 87」 행(차례는 아래, 필요 조건 「표본 폴더 · node · Docker · 여유 RAM 15GB」, **머지 위임**)과 「번들 88~」 행(보정 Important 여덟 · 묶음 Minor · `5-114` · G1-K15 · 번들 87 이 시간으로 건너뛴 행)으로 ⑤ 16장 — 기존 행 열하나의 사실 칸 끝에 ` · **처방**: 설계 72 §3-n`(`1-101` 은 「(라) — 사용자 2026-11-21」, `0-100` 은 「(가) 거절」 도) · `5-100` 행 끝에 「· `security.sqlDollar` 의 SQL 주석 떼기(`5-118` — 백엔드만 고쳤다, 순수본 JS 는 옛 동작)」 · 표 끝에 새 행 스물하나(아래 표, 꼴 `| <번호> | <청크> | **설계 72 §3-n·§4 가 원본**. <요지>(<키>). **축**: … **강제 지점**: … | <선행> | 대기 |`) ⑥ `PROGRESS.md` 이력 한 줄 + 「현재 상태」(진행중 = 번들 87 대기) ⑦ `bash scripts/verify.sh`(docs 레인) → `git commit`(따로) `docs: 설계 72 — 번들 87 행·번들 표`
- 실행 세션의 확인: `git log --oneline -1` 이 그 커밋 · `git status` 깨끗 · `ls design/72-bundle87.md` · 8절 「첫 스텝 전에」

| # | 청크 | 요지 | 축 | 강제 지점 | 선행 |
|---|---|---|---|---|---|
| 1-100 | (기존) ALTER 읽기 남은 꼴 | §3-1 | 생성기(db) | `DdlReaderTest.alterUnsupportedIsReported`(여덟) · `alterDropRenameFixesKeys` | S0 |
| 1-102 | (기존) MSSQL identity | §3-2 | 〃 | `DdlGenTest.mssqlIdentityTypeOnce` · `AlterGenTest.mssqlIdentityTypeChange` | S0 |
| 1-101 | (기존) MODIFY (라) | §3-3 | 〃 | `DdlReaderTest.modifyKeepsOrWarns` · `InsertRoutesTest.ambiguousModifyIsWarned` | 1-100 |
| 1-103 | (기존) 큰 길이 키 · 자동증가 | §3-4 | 〃 | `DdlGenTest.sizesDefaultsKeysIndexNames`·`rulesChecksAuto` | 1-102 |
| 1-104 | (기존) KEY 뒤 타입 · 맨 이름 | §3-5 | 〃 | `DdlReaderTest.keyNamedColumnsAreColumns`·`sameNameInTwoSchemas` · `InsertRoutesTest.pickBySchemaDotName` | 1-101 |
| 4-36 | (기존) `$`·`#` 이름 | §3-6 | 〃 | `InsertGenTest.reservedAndSpacedNamesAreQuoted` · `DdlGenTest.identifiers` | 1-103 |
| 9-9 | 실물 DB — `$` 든 이름 INSERT | §4 | 4장 | `DbCorpusBase.dollarNameInsertRuns`(컨테이너 아홉, V-66) | 4-36 |
| 6-46 | (기존) 배치 낱말 · 권한 문장 | §3-7 | 분석(corpus) | `SqlTablesTest.golden`(새 사례 넷) | S0 |
| 2-44 | (기존) 배열 수식 | §3-8 | 산출물 | `XlsxFillerTest.arrayFormulaDoesNotStopFill` | S0 |
| 0-99 | (기존) 떠 버린 javadoc | §3-8 | 문서 | 없음(읽기 — 컴파일) | 1-104·4-36 |
| 0-100 | (기존) 실제 경로 못 읽으면 거절 | §3-8 | 절대 규칙 7 | `MakeTemplatesTest.unreadableRealPathIsRefused` | S0 |
| 0-95 | (기존) JavaGraph 쪼개기 | §3-8 | 작성 규약 | `SelfCheckTest.mainMatchesBaseline`(`classLength` 줄 빠짐) | S0 |
| 0-101 | 프로필 없이 저장 | §3-9(B-05·A-17) | 관례 | `GenRoutesTest.saveWithoutActiveProfile` | S0 |
| 0-102 | 본문 상한 32MB · 413 JSON | §3-9(B-06·G1-K03) | 관례 | `TextRoutesTest.bodyLimit` | S0 |
| 2-46 | 08 코드값 — DB 오류 400 · 빈 이름 칸 | §3-10(B-10·B-11) | 산출물 | `DeliverableRoutesTest.codeCandidatesThenRows` · `CodeAndLinkTest.blankNameColumn` | S0 |
| 2-45 | keydup 키 수 | §3-10(B-08) | 산출물(db) | `QualitySqlTest.keydupAnyKeyCount` | S0 |
| 3-19 | 마스킹 예약어 인용 | §3-11(B-04) | 〃 | `MaskingTest.reservedNamesAreQuoted` | S0 |
| 3-20 | 마스킹 제외 키에 스키마 | §3-11(B-09) | 산출물 | `LogicalRoutesTest.maskingExcludeIsPerSchema` | S0 |
| 3-18 | 마스킹 후보 좁히기 | §3-11(B-03) | 산출물(db) | `MaskingTest.flagAndCodeColumnsAreNotPii` | 3-19 |
| 1-105 | 스냅샷 긴 값 | §3-12(F-K10) | 스냅샷(db) | `SnapshotStoreTest.longValuesSurvive`·`tooLongFailsWithoutSqlText` · `MetaRoutesTest.longNoteIs400` · `DbTest` 17 | S0 |
| 3-21 | 사전 길이 · 0 건 | §3-13(F-K12·F-K13) | 사전 | `DictStoreTest.orgRejectsLongAndEmpty` · `DictRoutesTest.longWordsAre400` | S0 |
| 0-103 | git 필터 이름 · 범위 | §3-14(C-VCS-EQ·SCOPE) | 받은 소스(corpus) | `WorkingCopyTest.gitRefusesFilterNameWithEquals`·`gitIgnoresWorktreeAndNestedFilters` | S0 |
| 0-104 | codecheck 블록의 0열 주석 | §3-15(G2-K14) | 프로필 | `ProfileStoreTest.saveCodeCheckSurvivesColumnZeroComments` | S0 |
| 8-18 | `check --groups` 가 나머지를 끈다 | §3-15(G2-K2) | CLI | `CodeCliTest.groupsTurnOffTheRest` | S0 |
| 5-117 | 붙여넣기 언어 추측 | §3-16(D-guessLang) | 검사(corpus) | `CheckStoreTest.guessLang` | S0 |
| 5-118 | sqlDollar — SQL 주석 | §3-16(D-sqlDollarCmt) | 〃 | `CheckRulesTest.sqlDollarSkipsSqlComments` · `PureRulesJsonTest`(골든 불변) | S0 |
| 6-47 | SQL 리터럴 끝 | §3-16(G2-K10) | 분석(corpus) | `SqlTokensTest.literalShapes` · `SqlTablesTest.golden`(새 사례 넷) | 6-46 |
| 7-33 | 템플릿 코멘트 | §3-17(A-13) | 생성기(db) | `GenTemplatesTest.hostileCommentsStayInert` | S0 |
| 7-34 | 매퍼 예약어 컬럼 | §3-17(A-36) | 〃 | `GenTemplatesTest.reservedColumnIsQuotedInMapper` | 7-33·4-36 |
| V-66a | 정적 분석 · corpus 골든 | §4 | 4장 | `verify.sh --corpus` | 코드 행 전부 |
| R-33 | 손 판정 · 처분 표 | §4. compare ㊱ | 4장 | compare ㊱ 절 | V-66a |
| V-66 | `--db` | §4 | 4장 | `verify.sh --db` | R-33 |

차례 — `S0→1-100→1-102→1-101→1-103→1-104→4-36→9-9→6-46→2-44→0-99→0-100→0-95→0-101→0-102→2-46→2-45→3-19→3-20→3-18→1-105→3-21→0-103→0-104→8-18→5-117→5-118→6-47→7-33→7-34→V-66a→R-33→V-66`

### 1-100 — ALTER 읽기 남은 꼴
- 대상: `core/gen/DdlAlter.java` · `core/gen/DdlReader.java`(`alterPk` 가 `boolean` 을 돌려준다 · `inlineIndex` 를 `private` → package-private — 메서드는 안 더한다) · `DdlReaderTest`
- 변경:
  ```java
  private static final Pattern ADD_OTHER = Pattern.compile("^(?:PARTITION|SUBPARTITION|SUPPLEMENTAL|OVERFLOW)\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern DEFAULT_FOR = Pattern.compile("^(?:CONSTRAINT\\s++\\S++\\s++)?DEFAULT\\s++(.+?)\\s++FOR\\s++(\\S++)$",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  /** ADD 뒤 — 컬럼 정의 또는 제약. 괄호로 싼 목록이면 항목마다. 컬럼도 제약도 아닌 것·아무것도 못 읽은 제약은 못 읽음(1-100) */
  private static void add(Draft d, String piece, String text, List<Unreadable> unreadable) {
      String body = column(text);
      if (ADD_OTHER.matcher(body).lookingAt()) {
          miss(d, piece, unreadable);
          return;
      }
      for (String item : inner(body)) {
          String it = column(item);
          if (!DdlPull.constraintLine(it)) {
              DdlReader.item(d, it, unreadable);
          } else if (!constraint(d, it)) {
              miss(d, piece, unreadable);
          }
      }
  }

  /** 제약 하나 — FK·UQ·PK·CHECK·KEY/INDEX·MSSQL DEFAULT … FOR. 하나라도 읽었으면 참 */
  private static boolean constraint(Draft d, String it) {
      int before = d.fks.size() + d.uqs.size() + d.checks.size() + d.indexes.size();
      // (지금의 FK·UQ 루프 그대로)
      boolean pk = DdlReader.alterPk(d, it);
      DdlReader.checks(d, it);
      DdlReader.inlineIndex(d, it);
      return pk | defaultFor(d, it) || d.fks.size() + d.uqs.size() + d.checks.size() + d.indexes.size() > before;
  }
  ```
  - `action:65` → `add(d, p, p.substring(a.end()), unreadable)` · `:87` → `add(d, p, p, unreadable)`
  - `defaultFor(Draft d, String it)` — `DEFAULT_FOR` 가 맞고 `unquote(group(2))` 컬럼이 있으면 그 컬럼을 `defaultValue = group(1).strip()` 로 바꿔 끼우고(12 인자 `Column`, 나머지 성분 그대로) 참. 컬럼이 없으면 거짓
  - `DdlReader.alterPk` — `d == null || !a.find()` 면 `false`, 끝에 `true`
  - `drop` — `d.pk.removeIf(…)` 다음 줄에 `dropFromKeys(d, name);`
  - `replace` — `d.pk.replaceAll(…)` 다음 줄에 `renameInKeys(d, old, c.name());`
  ```java
  /** 지운 컬럼을 유니크·인덱스에서 뺀다(다 빠지면 키도 지운다). 그 컬럼을 품은 FK 는 통째로 */
  private static void dropFromKeys(Draft d, String name)
  /** 이름 바꾼 컬럼을 유니크·인덱스·FK 의 columns 에서 새 이름으로 */
  private static void renameInKeys(Draft d, String old, String neu)
  ```
  레코드는 `UniqueKey(name, columns)` · `Index(name, unique, columns, sorts)`(컬럼을 뺄 때 같은 자리 `sorts` 원소도 뺀다 — `sorts` 가 더 짧으면 있는 만큼만) · `ForeignKey(name, columns, refSchema, refTable, refColumns, deleteRule, updateRule)`. 이름 견줌은 `equalsIgnoreCase`. `renameInKeys` 는 `old.equalsIgnoreCase(neu)` 면 아무것도 안 한다
- 시험(먼저): `alterUnsupportedIsReported` 의 DDL 에 `ALTER TABLE t ADD PARTITION p1 VALUES LESS THAN (10);` · `ALTER TABLE t ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;` · `ALTER TABLE t ADD CONSTRAINT xx EXCLUDE USING gist (a WITH =);` 를 더하고 수 5 → 8(컬럼은 여전히 `a, b`). 새 `alterDropRenameFixesKeys` — `CREATE TABLE t (id INT, a INT, b INT, old_nm VARCHAR(10), PRIMARY KEY (id), UNIQUE (a, b), KEY ix_nm (old_nm), KEY ix_a (a));` + `ALTER TABLE t ADD KEY ix_b (b);` + `ALTER TABLE t ADD CONSTRAINT df_b DEFAULT (0) FOR b;` + `ALTER TABLE t CHANGE old_nm nm VARCHAR(20);` + `ALTER TABLE t DROP COLUMN a;` → `unreadable` 빔 · 유니크의 컬럼 `[[b]]` · 인덱스 「이름=컬럼」 이 `ix_nm=[nm]`·`ix_b=[b]` 둘뿐 · 컬럼 `b` 의 `defaultValue` 에 `0`. RED: 수 5 · `ix_b` 없음 · 유니크 `[a, b]`
- 의도된 골든 변화: 없음(빠른 레인). corpus 는 `V-66a`
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,DtoGeneratorTest,InsertGenTest,InsertRoutesTest,GenRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 db + corpus

### 1-102 — MSSQL identity: 옛 스냅샷 · AlterGen
- 대상: `core/gen/DdlGen.java` · `DdlGenTest` · `AlterGenTest`
- 변경:
  ```java
  /** 1-102 — 자동증가인가: 수집 표시, 또는 MSSQL 타입 이름의 identity 낱말(V016 전 스냅샷은 표시가 없다) */
  boolean auto(Column c) {
      return c.autoIncrement() || c.nativeType() != null && IDENTITY_WORD.matcher(c.nativeType()).find();
  }

  /** 타입 이름의 identity 낱말을 뗀 컬럼 — 절은 autoHead 가 낸다(V-65·1-102) */
  private Column plain(Column c) {
      if (c.nativeType() == null || !IDENTITY_WORD.matcher(c.nativeType()).find()) {
          return c;
      }
      return new Column(c.name(), c.ordinal(), IDENTITY_WORD.matcher(c.nativeType()).replaceFirst(""), c.jdbcType(), c.length(), c.precision(),
              c.scale(), c.nullable(), c.defaultValue(), c.comment(), c.domain(), true);
  }
  ```
  - `type(Table t, Column c)` → `type(Table t, Column raw)`, 첫 줄 `Column c = plain(raw);`(분기 없음 — 기준선 24 그대로)
  - `column:292` → `String type = type(t, c);`(떼기 삭제) · `:294` `c.autoIncrement() ? null` → `auto(c) ? null`
  - `autoHead:518`·`autoTail:535` 의 `!c.autoIncrement()` → `!auto(c)`
  - `AlterGen` 은 안 고친다
- 시험(먼저): `mssqlIdentityTypeOnce` 에 — `Table.of("dbo", "t87", "TABLE", null).withColumns(List.of(col("id", 1, "int identity", null, 10, 0, false, null, null))).withConstraints(new PrimaryKey(null, List.of("id")), List.of(), List.of())`(11 인자 `col` — 자동증가 거짓) 를 mssql→mssql 로 → `id int IDENTITY(1,1) NOT NULL` · mssql→postgresql 로 → `GENERATED BY DEFAULT AS IDENTITY`. `AlterGenTest.mssqlIdentityTypeChange` — `AlterGen.generate(usersIdAuto(null, "int identity"), usersIdAuto(null, "bigint identity"), new AlterGen.Options("mssql", "mssql", null, true, true, true), TypeMapping.load()).sql()` 에 `ALTER COLUMN user_id bigint NOT NULL` 이 있고 `bigint identity` 가 없다. RED: `id int NOT NULL` · `bigint identity NOT NULL`
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlGenTest,AlterGenTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

### 1-101 — ALTER MODIFY (라)
- 대상: `core/gen/DdlAlter.java` · `core/gen/DdlReader.java`(상수 하나) · `web/InsertRoutes.java`(`missedLines`) · `DdlReaderTest`·`InsertRoutesTest`
- 변경:
  ```java
  // DdlReader — 상수만(메서드를 더하지 않는다)
  /** 1-101 — 방언을 모르는 MODIFY 가 NOT NULL·DEFAULT 를 지웠을 때 그 컬럼 notes 의 글 */
  public static final String AMBIGUOUS = "TODO 방언 모호: MODIFY 가 NOT NULL·DEFAULT 를 지웠다(MySQL 뜻) — Oracle·Tibero 면 남는다, 확인";

  // DdlAlter
  private static final Pattern NULL_WORD = Pattern.compile("\\bNULL\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern DEFAULT_WORD = Pattern.compile("\\bDEFAULT\\b", Pattern.CASE_INSENSITIVE);
  /** 재정의 꼴 — KEEP: MODIFY (…) 괄호(Oracle·Tibero) · ASK: 맨 MODIFY(방언 모름) · WHOLE: MODIFY COLUMN·CHANGE(MySQL 전용 문법) */
  private enum Redef { KEEP, ASK, WHOLE }
  ```
  - `inner` 의 조건을 `private static boolean listed(String t)`(= `t.startsWith("(") && DdlReader.matching(t, 0) == t.length() - 1`)로 빼 `inner` 와 `redefine` 이 같이 쓴다
  - `redefine` — 루프 앞에 `String body = column(text); Redef mode = change || COLUMN_WORD.matcher(text.strip()).lookingAt() ? Redef.WHOLE : listed(body.strip()) ? Redef.KEEP : Redef.ASK;`, 호출을 `replace(d, old, def, mode)`
  - `replace(Draft d, String old, String def, Redef mode)` — `Column c`·`Column was` 를 얻은 뒤:
    ```java
    String bare = DdlReader.QUOTED.matcher(def).replaceAll("''");
    boolean saysNull = NULL_WORD.matcher(bare).find();
    boolean saysDefault = DEFAULT_WORD.matcher(bare).find();
    boolean keep = mode == Redef.KEEP;
    Column merged = new Column(c.name(), was.ordinal(), c.nativeType(), c.jdbcType(), c.length(), c.precision(), c.scale(),
            keep && !saysNull ? was.nullable() : c.nullable(), keep && !saysDefault ? was.defaultValue() : c.defaultValue(),
            c.comment() != null ? c.comment() : was.comment(), c.domain(), c.autoIncrement() || keep && was.autoIncrement());
    d.cols.set(at, merged);
    if (mode == Redef.ASK && (!saysNull && !was.nullable() || !saysDefault && was.defaultValue() != null)) {
        d.notes.putIfAbsent(c.name().toUpperCase(Locale.ROOT), DdlReader.AMBIGUOUS);
    }
    ```
    (지금의 `d.cols.set(at, with(…))` 한 줄을 바꾼다. `replace` 복잡도가 15 를 넘으면 `merged` 만들기를 `private static Column merged(Column c, Column was, String def, boolean keep)` 로 뺀다)
  - `InsertRoutes.missedLines` — 못 읽은 줄 경고 뒤에, `parsed.notes().getOrDefault(t.name().toUpperCase(Locale.ROOT), Map.of())` 가운데 값이 `DdlReader.AMBIGUOUS` 인 컬럼마다 `"<컬럼>: " + DdlReader.AMBIGUOUS.substring(5)`(앞 `TODO ` 뗌) 한 줄을 더해 돌려준다(목록은 새 `ArrayList`)
- 시험(먼저): `DdlReaderTest.modifyKeepsOrWarns` — `CREATE TABLE t (a VARCHAR2(10) DEFAULT 'x' NOT NULL, b INT NOT NULL, c INT DEFAULT 1 NOT NULL, d INT, e INT NOT NULL);` + `ALTER TABLE t MODIFY (a VARCHAR2(100));` + `ALTER TABLE t MODIFY b BIGINT;` + `ALTER TABLE t MODIFY COLUMN c BIGINT;` + `ALTER TABLE t MODIFY d BIGINT;` + `ALTER TABLE t MODIFY (e NUMBER(5) NULL);` → `unreadable` 빔 · `a` 길이 100·`nullable` 거짓·기본값에 `x` · `b` `BIGINT`·`nullable` 참·`notes.get("T").get("B")` 가 `DdlReader.AMBIGUOUS` · `c` `nullable` 참·기본값 null·notes 에 `C` 없음 · `D` 없음 · `e` `nullable` 참. `InsertRoutesTest.ambiguousModifyIsWarned` — DDL `CREATE TABLE t (id INT PRIMARY KEY, b INT NOT NULL); ALTER TABLE t MODIFY b BIGINT;` → 응답 `warnings` 에 `B: 방언 모호` 로 시작하는 줄 · MODIFY 가 없는 DDL 이면 그 글이 없다. RED: `a` 의 `nullable` 참 · notes 빔 · 경고 없음
- 의도된 골든 변화: 없음(빠른 레인). corpus `ddl-*` 의 notes 수는 `V-66a`
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,InsertRoutesTest,GenRoutesTest,DtoGeneratorTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-100(같은 파일)

### 1-103 — 큰 길이 키 문자 · CREATE 안 자동증가
- 대상: `core/gen/DdlGen.java` · `DdlGenTest`
- 변경:
  ```java
  private Long keyLen(Table t, Column c, String norm) {
      Long len = c.length();
      boolean given = len != null && len > 0;
      if (given && len <= sp.limits().get(target) || !STRING_TYPE.equals(types.javaType(c, source.isEmpty() ? null : source)) || !inKey(t, c)) {
          return len;
      }
      long n = "UUID".equals(norm) || "UNIQUEIDENTIFIER".equals(norm) ? 36 : 255;
      warnings.add(t.name() + "." + c.name() + (given ? ": 길이 " + len + " 문자 타입이 키 컬럼 — 대상 상한을 넘어 길이 " + n + " 으로 냈다"
              : ": 길이 없는 문자 타입이 키 컬럼 — 길이 " + n + " 으로 냈다"));
      return n;
  }

  /** CREATE TABLE 의 컬럼을 내는 중인가 — 그 안에는 PK 만 있다(유니크·인덱스는 뒤 문장) */
  private boolean inCreate;

  String autoTail(Table t, Column c) {
      if (!auto(c) || !mariadb()) {
          return "";
      }
      boolean pk = t.pk() != null && t.pk().columns().stream().anyMatch(n -> n.equalsIgnoreCase(c.name()));
      if (inCreate ? pk : inKey(t, c)) {
          return " AUTO_INCREMENT";
      }
      warnings.add(t.name() + "." + c.name() + (inKey(t, c) ? ": 자동증가 — PK 가 아닌 키라 CREATE TABLE 안에 키가 없다, 뺐다(키를 만든 뒤 ALTER 로)"
              : ": 자동증가 — 키 컬럼이 아니라 mariadb 가 못 받는다, 뺐다"));
      return "";
  }
  ```
  `table()` — 컬럼 루프 바로 앞 `inCreate = true;`, 바로 뒤 `inCreate = false;`(대입은 복잡도를 안 올린다 — 기준선 16 그대로). `sp.limits().get(target)` 의 형식이 `Integer` 면 비교는 그대로 된다
- 시험(먼저): `sizesDefaultsKeysIndexNames` 의 `keys` 표에 `col("big", 4, "text", 2147483647L, null, null, true, null, null)` 과 `new UniqueKey("uk_t3b", List.of("big"))` 를 더해 — oracle 에 `big VARCHAR2(255)` · 경고에 `t3.big: 길이 2147483647 문자 타입이 키 컬럼 — 대상 상한을 넘어 길이 255 으로 냈다`. `rulesChecksAuto` 끝에 — `Table.of("public", "u86", "TABLE", null).withColumns(List.of(auto("id", 1, "int4"))).withConstraints(null, List.of(), List.of(new UniqueKey("uk_u86", List.of("id"))))` 를 postgresql→mariadb 로 → SQL 에 `AUTO_INCREMENT` 없음 · 경고에 `u86.id: 자동증가 — PK 가 아닌 키라` 로 시작하는 줄. 기존 `c86.seq` 경고 단언·`AlterGenTest.mariadbCommentOnlyKeepsAutoIncrement` 는 그대로 초록. RED: `big CLOB` · `u86` 에 `AUTO_INCREMENT`
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlGenTest,AlterGenTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-102(`auto`)

### 1-104 — KEY 뒤 타입 괄호 · 맨 이름 ALTER · 표 고르기
- 대상: `core/gen/DdlPull.java` · `web/InsertRoutes.java` · `DdlReaderTest`·`InsertRoutesTest`
- 변경:
  ```java
  // DdlPull
  private static final Pattern TYPE_ARGS = Pattern.compile("^(?:\\*|[A-Za-z]++\\s*+,\\s*+\\d++\\s*+\\))");
  private static final Pattern MAX_ARG = Pattern.compile("(?i)^MAX\\s*+\\)");
  private static final Pattern MAX_TYPE = Pattern.compile("(?i)N?VARCHAR|VARBINARY");
  // keyShape — 지금의 return 식 끝에
  //     && !TYPE_ARGS.matcher(inside).lookingAt() && !(n == 1 && MAX_TYPE.matcher(words[0]).matches() && MAX_ARG.matcher(inside).lookingAt());

  // find — 맨 이름은 스키마 없는 둘째 표 자리(「.이름」) 먼저
  Draft q = parts.size() > 1 ? tables.get((parts.get(parts.size() - 2) + "." + bare).toUpperCase(Locale.ROOT)) : tables.get("." + bare);

  // InsertRoutes
  /** 「스키마.이름」 이면 스키마까지 맞는 표 먼저(1-104), 없으면 이름만 */
  private static Optional<Table> pick(List<Table> tables, String name) {
      if (name == null || name.isBlank()) {
          return tables.stream().findFirst();
      }
      String n = name.trim();
      int dot = n.lastIndexOf('.');
      Optional<Table> exact = dot < 0 ? Optional.empty() : tables.stream()
              .filter(t -> n.substring(0, dot).equalsIgnoreCase(t.schema()) && n.substring(dot + 1).equalsIgnoreCase(t.name())).findFirst();
      return exact.isPresent() ? exact : tables.stream().filter(t -> t.name().equalsIgnoreCase(n)).findFirst();
  }
  ```
- 시험(먼저): `keyNamedColumnsAreColumns` 의 DDL 에 `KEY3 INT`(자리 채움) 대신 둘째 표를 더한다 — `CREATE TABLE conf2 (id INT, KEY NUMBER(*,0), INDEX geometry(Point,4326), KEY ix_t (id));` 와 `CREATE TABLE conf3 (KEY VARCHAR(MAX), max INT, KEY ix_max (max));` → `conf2` 컬럼 `id, KEY, INDEX`·인덱스 `ix_t` 하나 · `conf3` 컬럼 `KEY, max`·인덱스 `ix_max` 하나 · `unreadable` 빔. `sameNameInTwoSchemas` 에 — `CREATE TABLE s1.emp (id INT);\nCREATE TABLE emp (id INT);\nALTER TABLE emp ADD c INT;` → 스키마 `s1` 표 컬럼 1 · 스키마 없는 표 컬럼 2. `InsertRoutesTest.pickBySchemaDotName` — DDL `CREATE TABLE s1.emp (id INT); CREATE TABLE s2.emp (id INT, nm VARCHAR(10));` + `"table":"s2.emp"` → SQL 에 `nm` 이 있다 · `"table":"emp"` → 첫 표(`nm` 없음). RED: `conf2` 에 인덱스 셋 · `s1` 컬럼 2 · `s2.emp` 404
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,InsertRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-101(같은 시험 파일)
- 사다리: `InsertRoutes.register` 가 `req.table()` 을 `IDENT`(`:157`)로 재 `s2.emp` 를 400 으로 막으면 — 그 검사에서 `.` 로 나눈 마디마다 재게 고친다(같은 파일)

### 4-36 — `$`·`#` 든 이름
- 대상: `core/gen/InsertGen.java` · `core/gen/DdlGen.java` · `InsertGenTest`·`DdlGenTest`
- 변경:
  ```java
  // InsertGen
  private static final Pattern PLAIN = Pattern.compile("^[A-Za-z][A-Za-z0-9_$]*$");
  private static final Pattern PLAIN_HASH = Pattern.compile("^[A-Za-z][A-Za-z0-9_$#]*$");

  /** 4-36 — 맨 이름으로 나가도 되는 꼴인가. $ 는 다섯 방언 다, # 는 oracle·mssql·sybase 만(mysql 은 주석 시작, pg 는 못 쓴다). DdlGen 이 같이 쓴다 */
  static boolean plain(String name, String db) {
      return ("oracle".equals(db) || "mssql".equals(db) || "sybase".equals(db) ? PLAIN_HASH : PLAIN).matcher(name).matches();
  }
  ```
  - `ident:603` → `boolean plain = plain(name, db);` · `uid:617` → `ident(plain(name, db) ? name.toUpperCase(Locale.ROOT) : name, db)`
  - `DdlGen.quoted:598` → `boolean plain = InsertGen.plain(name, InsertGen.db(target));`, `DdlGen.PLAIN:57` 은 다른 쓰임이 없으면 지운다
- 시험(먼저): `reservedAndSpacedNamesAreQuoted` 에 — 컬럼 `emp$no`·`tmp#1` 을 가진 표(`Table.of(null, "t436", …)`, PK `emp$no`)로 oracle → `\temp$no`·`\ttmp#1` 이 따옴표 없이 · mariadb → `` `tmp#1` `` 이고 `emp$no` 는 맨 이름 · postgresql → `"tmp#1"`. `DdlGenTest.identifiers` 에 — postgresql→oracle 에서 `emp$no` 가 맨 이름이고 경고에 `emp$no` 가 없다. RED: `"emp$no"`
- 의도된 골든 변화: 없음(빠른 레인). `db-*.json` 의 `ddlGenWarnings` 가 주는 것은 `V-66`
- 검증: `bash scripts/mvn.sh -q test -Dtest='InsertGenTest,DdlGenTest,AlterGenTest,QualitySqlTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-103(같은 파일)

### 9-9 — 실물 DB: `$` 든 이름 INSERT
- 대상: `src/test/java/kr/ejg/toolbox/web/DbCorpusBase.java`(새 `@Order(12)` 시험 하나)
- 변경: `dollarNameInsertRuns` — `rulesChecksAutoRoundTrip:1456` 의 꼴 그대로(`skipIfOff("dollarNameInsertRuns")` 로 시작, 실패는 `List<String> a` 에 모아 끝에 `CorpusFiles.none("87 달러 이름 " + ddlTarget(), a, 1)`). ① `CREATE TABLE T87_D (ID INT NOT NULL PRIMARY KEY, EMP$NO VARCHAR(10))`(PostgreSQL 은 소문자 `t87_d`·`emp$no`, Oracle 은 `NUMBER(10)`·`VARCHAR2(10)`) ② 스냅샷에서 이름이 `T87_D`(대소문자 무시)인 표 ③ `InsertGen.generate(<그 표>, Map.of(), new InsertGen.Options(ddlTarget(), 2, false, false, null), null).sql()` 을 `execAll(sql, a, "87 INSERT")` ④ `SELECT COUNT(*)` 가 2 ⑤ `finally` 에서 DROP(실패는 삼킨다). `golden.put` 은 안 쓴다(골든 열쇠를 안 늘린다)
- 시험: 이 시험이 강제 지점이다. RED 는 `V-66` 에서 `4-36` 을 잠깐 되돌려 Oracle 한 컨테이너로 보지 않는다 — 등급 A 0 만 본다
- 검증: `bash scripts/verify.sh`(컴파일·빠른 레인). 실물은 `V-66`
- 의존: 4-36. `4-36` 이 `wip/` 로 빠졌으면 이 행도 건너뛴다

### 6-46 — T-SQL 배치 낱말 · 권한 문장
- 대상: `core/analyze/SqlTables.java` · `SqlTablesTest` · `golden/analyze/sql-tables.json`
- 변경: `LIST_END` 에 `"DECLARE", "EXEC", "EXECUTE", "CALL", "BEGIN", "IF", "PRINT", "RETURN", "REVOKE", "GRANT"`. `extract:153` 을 `List<String> t = withoutDcl(tokens(RegexRule.stripComments(text == null ? "" : text, "sql")));` 로:
  ```java
  private static final Set<String> DCL = Set.of("GRANT", "REVOKE", "DENY");

  /** 6-46 — 권한 문장(GRANT·REVOKE·DENY … ;)은 통째로 뺀다 — FROM·TO 뒤는 사용자 이름이다 */
  private static List<String> withoutDcl(List<String> t) {
      List<String> out = new ArrayList<>(t.size());
      boolean start = true;
      boolean skip = false;
      for (String x : t) {
          if (start) {
              skip = DCL.contains(x);
          }
          start = ";".equals(x);
          if (!skip || start) {
              out.add(x);
          }
      }
      return out;
  }
  ```
- 시험(먼저): `CASES` 끝에 넷 — `{"tsqlBatchDeclare", "select", "SELECT A FROM T DECLARE @X INT, @Y INT"}` · `{"tsqlBatchExec", "select", "SELECT A FROM T EXEC P @A, @B"}` · `{"revokeFromUsers", "select", "REVOKE SELECT ON T FROM U1, U2"}` · `{"grantThenSelect", "select", "GRANT SELECT ON T TO U1; SELECT A FROM B"}`. 골든을 `-Dgolden.update=true` 로 더한 뒤 손으로 본다 — 앞 둘은 `tables` 가 `T` 하나·`unresolved` 빔, 셋째는 `tables` 빔, 넷째는 `B` 하나. RED: 고치기 전 골든 갱신 결과에 `table` 미해결·`U1`·`U2`
- 의도된 골든 변화: `sql-tables.json` 에 사례 넷이 **더해지는 것만**
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlTablesTest,MapperIndexTest,JavaGraphTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus

### 2-44 — 배열 수식에서 채우기가 멈추지 않게
- 대상: `core/report/FormulaRange.java` · `XlsxFillerTest`
- 변경: `widened` 의 `if (hit) { c.setCellFormula(…); }` 를
  ```java
  if (hit) {
      try {
          c.setCellFormula(FormulaRenderer.toFormulaString(ew, ptgs));
      } catch (IllegalStateException e) {
          return false; // 2-44 — 다중 칸 배열 수식의 한 칸은 POI 가 못 바꾼다. 그 수식은 그대로 둔다
      }
  }
  ```
- 시험(먼저): `arrayFormulaDoesNotStopFill` — `totalsFormulaIsWidened` 의 양식에서 합계 행을 `s.setArrayFormula("B3:B3*2", new org.apache.poi.ss.util.CellRangeAddress(3, 3, 1, 2))` 로 바꿔 세 행을 채운다 → 예외 없이 끝나고 결과 파일이 열리며 5행(0 기준) 1열 칸이 `isPartOfArrayFormulaGroup()`. RED: `IllegalStateException`
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='XlsxFillerTest,DeliverableServiceTest'` → `bash scripts/verify.sh`
- 의존: S0
- 사다리: 예외가 `widened` 가 아니라 `sheet.shiftRows`(`XlsxFiller:94`)에서 나면 — POI 가 배열 수식 행을 못 민다. 이 행은 `wip/2-44` 로 빼고 「양식에 다중 칸 배열 수식이 있으면 사유와 함께 거절」 을 새 행으로

### 0-99 — 떠 버린 javadoc 넷
- 대상: `core/gen/AlterGen.java` · `core/gen/Validation.java` · `core/analyze/SqlProbe.java` · `web/ProfileRoutes.java`(주석 줄만 — 파일 넷이지만 코드 변화 0)
- 변경: ① `AlterGen:434` 의 `/** 유니크·인덱스 — … */` 줄을 `uniquesAndIndexes` 선언 바로 위로 ② `Validation:265` 의 `/** 이 조건이 가리키는 표 컬럼… */` 을 2인자 `columnsIn(String condition, Table t)` 바로 위로 ③ `SqlProbe:300` 의 `/** SQL 의 리터럴 밖 낱말(대문자) */` 를 `names` 바로 위로 ④ `ProfileRoutes:262` 의 `/** 블록 꼴 … */` 을 `withoutBlockPasswords` 바로 위로. 글자는 안 바꾼다
- 검증: `git diff --stat` 이 네 파일·더한 줄 = 뺀 줄 · `bash scripts/verify.sh`
- 의존: 1-104·4-36(`core/gen` 을 고치는 행 뒤 — 줄 번호가 덜 밀린다)

### 0-100 — 실제 경로를 못 읽으면 거절
- 대상: `cli/MakeTemplates.java` · `MakeTemplatesTest`
- 변경:
  ```java
  /** 실제 경로 읽기 — 시험이 못 읽는 경우를 넣는다 */
  @FunctionalInterface
  interface Real {
      Path of(Path p) throws java.io.IOException;
  }

  // inside 의 조건 끝 `!real(abs).startsWith(real(base))` → `!under(base, abs, p -> p.toRealPath())`

  /** 0-86·0-100 — 링크·정션을 따라간 자리로 잰다. 실제 경로를 못 읽으면 밖으로 친다(닫힌 실패) */
  static boolean under(Path base, Path abs, Real real) {
      try {
          return real(abs, real).startsWith(real.of(base));
      } catch (java.io.IOException e) {
          return false;
      }
  }

  /** 있는 가장 가까운 조상의 실제 경로 + 나머지 */
  private static Path real(Path abs, Real real) throws java.io.IOException {
      Path up = abs;
      while (up != null && !Files.exists(up)) {
          up = up.getParent();
      }
      return up == null ? abs : real.of(up).resolve(up.relativize(abs));
  }
  ```
- 시험(먼저): `unreadableRealPathIsRefused` — `Path base = Path.of("").toAbsolutePath().normalize();` · `assertTrue(MakeTemplates.under(base, base.resolve("target/tb-make-templates/x"), p -> p.toRealPath()))` · `assertFalse(MakeTemplates.under(base, base.resolve("target/tb-make-templates/x"), p -> { throw new java.io.IOException("x"); }))`. 기존 `outsideRunFolderIsRefused`·`linkOutsideRunFolderIsRefused` 초록. RED: 컴파일 실패(`under` 없음)
- 검증: `bash scripts/mvn.sh -q test -Dtest='MakeTemplatesTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 java

### 0-95 — JavaGraph 쪼개기
- 대상: `core/analyze/JavaGraph.java` · 새 `core/analyze/JavaViews.java` · `golden/check/self-check.txt`
- 변경: 새 `final class JavaViews`(package-private, `private JavaViews() {}`, 클래스 javadoc 「컨트롤러 메서드의 뷰 이름 식·종류·설명(6-x) — JavaGraph 가 길이 상한에 닿아 따로 둔다(0-95)」)로 **글자 그대로 옮긴다**: `kind`(javadoc 포함) · `url` · `value`(javadoc 포함) · `assigned`(javadoc 포함) · `flatten` · `description`(javadoc 포함) + 상수 `TAG`·`DESCR_MAX`. 접근 — `kind`·`url`·`value`·`description` 은 package-private static, `assigned`·`flatten` 은 private static. 형식 이름은 `JavaGraph.Cls`·`JavaGraph.View`. `JavaGraph` 의 부르는 자리 — `:244` `JavaViews.kind(…)` · `:250` `JavaViews.description(m)` · `views` 안 `value(…)` 셋 → `JavaViews.value(…)` · `url(…)` 둘 → `JavaViews.url(…)`. `views`·`VIEW_NAME`·`note`·`line` 은 남는다. 두 파일에서 안 쓰게 된 import 는 지운다. 시험이 `JavaGraph.description` 을 부르면 `JavaViews.description` 으로
- 시험: 새 시험 없음(옮기기 — 기존 `JavaGraphTest` 골든 넷이 지킨다). RED→GREEN 자리는 `SelfCheckTest.mainMatchesBaseline`
- 의도된 골든 변화: `self-check.txt` — `JavaGraph.java|metric.classLength|…` 줄이 **사라진다** · `JavaGraph.java|metric.classMethods|메서드 40` → `34` · `JavaGraph.java|metric.complexity|복잡도 18 > 15 · value(4)` 줄이 `JavaViews.java|metric.complexity|복잡도 18 > 15 · value(4)` 로(파일 이름만). 그 밖 줄이 바뀌면 옮기다 고친 것이다 — 되돌린다. `golden/analyze/*` diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,AnalyzeRunnerTest,AnalyzeStoreTest,SelfCheckTest,ArchitectureTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus

### 0-101 — 프로필 없이 저장
- 대상: `web/LogicalRoutes.java`(`outFile`) · `GenRoutesTest`
- 변경:
  ```java
  /** out/<프로필>/<시각>/<이름> — 폴더까지 만든다(12장). 활성 프로필이 없으면 Outputs.dir 처럼 default(0-101) */
  static java.nio.file.Path outFile(Supplier<Optional<Profile>> active, String name) throws java.io.IOException {
      Profile p = active.get().orElse(null);
      String base = p != null && p.output() != null && p.output().dir() != null ? p.output().dir() : "out";
      java.nio.file.Path file = java.nio.file.Path.of(base, p == null ? "default" : p.name(), LocalDateTime.now().format(STAMP), name).toAbsolutePath();
  ```
  (뒤 줄은 그대로)
- 시험(먼저): `GenRoutesTest.saveWithoutActiveProfile(@TempDir Path tmp)` — `SmokeHtmlUnitTest.dbBrowserWithoutActiveProfile` 처럼 제 앱을 띄운다(`App.start(new AppConfig(0, null, tmp.resolve("data"), <빈 profiles 폴더>, tmp.resolve("drivers"), false))`). `POST /api/gen/dto?save=true`(본문은 `fromDdlWithUnreadableAndSave` 의 것)가 200 이고 `path` 에 `default` 마디가 있다 · `POST /api/logical/candidates` 에 `{"csv":"OWNER,TABLE_NAME,COLUMN_NAME,DATA_TYPE,DATA_LENGTH\nS,T87,C1,VARCHAR2,10"}` 가 200. `finally` — 응답 `path` 들의 `out/default/<시각>` 폴더를 지우고 앱을 끈다(`out/` 은 `.gitignore:4` 라 남아도 커밋에 안 잡히지만 지운다). RED: 500
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='GenRoutesTest,LogicalRoutesTest'` → `git status --short` 깨끗(시험이 남긴 `out/` 없음) → `bash scripts/verify.sh`
- 의존: S0. 레인 java

### 0-102 — 본문 상한 32MB · 413 은 JSON
- 대상: `web/App.java` · `TextRoutesTest`
- 변경: 상수 `/** 0-102 — 요청 본문 상한. 읽기 10MB 파일의 JSON 글·logsql 5백만 자가 든다 */ static final long MAX_BODY = 32L * 1024 * 1024;` · `create` 의 `quietErrors(cfg);` 다음 줄 `cfg.http.maxRequestSize = MAX_BODY;` · `jsonErrors` 에 `app.exception(io.javalin.http.ContentTooLargeResponse.class, (e, ctx) -> ctx.status(413).json(Map.of(MESSAGE, "본문이 32MB 를 넘는다")));`
- 시험(먼저): `TextRoutesTest.bodyLimit` — `post("x".repeat(1_500_000))` 이 413 이 **아니다**(200 또는 400) · `Content-Type: application/json` 으로 `new byte[33 * 1024 * 1024]` 를 `/api/text/logsql` 에 보내면 413 이고 본문이 JSON `message` 에 `32MB`. RED: 첫 단언 413
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='TextRoutesTest,JsonErrorTest,LocalOnlyTest,FsRoutesTest'` → `bash scripts/verify.sh`
- 의존: S0
- 사다리: 413 본문이 JSON 이 아니면(Javalin 이 그 예외를 매퍼에 안 넘긴다) — 예외 매퍼 대신 `app.error(413, ctx -> { if (!String.valueOf(ctx.res().getContentType()).startsWith("application/json")) { ctx.json(Map.of(MESSAGE, "본문이 32MB 를 넘는다")); } });`

### 2-46 — 08 코드값: DB 오류 400 · 빈 이름 칸
- 대상: `web/DeliverableRoutes.java` · `core/deliverable/CodeAndLink.java` · `DeliverableRoutesTest`·`CodeAndLinkTest`
- 변경: `app.post("/api/deliverable/codes/rows", …)` 의 람다 몸을 `private static void postCodeRows(Context ctx, ConnectionRegistry conns)` 로 옮기고(작성 규약 — `register` 의 람다 분기) `catch (IllegalArgumentException e)` 뒤에
  ```java
  } catch (java.sql.SQLException e) { // B-10 — 없는 표·권한 등 DB 가 거절한 조회. 화면 응답이라 드라이버 글을 싣는다(:461 꼴)
      ctx.status(400).json(Map.of("message", "코드값 조회 실패(표·컬럼 이름·권한 확인): " + e.getMessage()));
      return;
  }
  ```
  `CodeAndLink.codeDoc:136` → `Object name = t.nameCol() != null && !t.nameCol().isBlank() ? v.get(k++) : "";`
- 시험(먼저): `codeCandidatesThenRows` 에 — `"tables"` 를 `List.of(Map.of("table", "NOPE87", "codeCol", "CD", "nameCol", "CD_NM"))` 로 보내면 400 이고 `message` 가 `코드값 조회 실패` 로 시작. `CodeAndLinkTest.blankNameColumn` — `codeDocReadsValues` 의 H2 표에 `new CodeAndLink.CodeTable("PUBLIC", "CMM_CD", null, "GRP_CD", "CD", "", null, null, null)` → 세 행, 각 행 7번 칸(코드명)이 빈 글. RED: 500 · `IndexOutOfBoundsException`
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='DeliverableRoutesTest,CodeAndLinkTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

### 2-45 — keydup 키 수
- 대상: `core/quality/QualitySql.java` · `QualitySqlTest`
- 변경: `render` 의 `List<String> ks = …; for (…) { … }` 넉 줄을 `sql = keys(sql, keys == null ? List.of() : keys, dk);` 한 줄로:
  ```java
  /** B-08 — 틀의 후보키 자리 둘을 키 수만큼 편다. 키가 없으면 틀 그대로 */
  private static String keys(String sql, List<String> ks, String dk) {
      if (ks.isEmpty()) {
          return sql;
      }
      List<String> ids = ks.stream().map(k -> id(k, dk)).toList();
      return sql.replace("후보키1, 후보키2", String.join(", ", ids))
              .replace("후보키1 IS NULL OR 후보키2 IS NULL", String.join(" OR ", ids.stream().map(i -> i + " IS NULL").toList()))
              .replace("후보키1", ids.get(0)).replace("후보키2", ids.get(ids.size() > 1 ? 1 : 0));
  }
  ```
  `render` 의 `@param keys` javadoc 을 「후보키 컬럼(keydup) — 전부 쓴다」 로
- 시험(먼저): `keydupAnyKeyCount` — 키 하나 `List.of("A")` → `후보키` 글자가 없고 `SELECT A, COUNT(*)` · 키 셋 `List.of("A", "B", "C")` → `GROUP  BY A, B, C` 와 `C IS NULL`. 기존 `goldenFortyAndNoCorePlaceholderLeft`(키 둘) 초록 — 골든 불변. RED: `후보키2` 남음 · `C` 없음
- 의도된 골든 변화: 없음(`quality/keydup-*.sql` diff 0)
- 검증: `bash scripts/mvn.sh -q test -Dtest='QualitySqlTest,DeliverableRoutesTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 db + corpus

### 3-19 — 마스킹 예약어 인용
- 대상: `core/logical/Masking.java`(`id`) · `MaskingTest`
- 변경:
  ```java
  /** B-04 — 예약어·특수 글자만 감싼다(DdlGen.reserved — QualitySql.id 와 같은 판정). 예약어는 방언이 접는 글자로 */
  private static String id(String name, Dialect d) {
      boolean plain = PLAIN.matcher(name).matches();
      if (plain && !DdlGen.reserved(name)) {
          return name;
      }
      String n = plain ? folded(name, d) : name;
      return switch (d) {
          case MARIADB -> "`" + n.replace("`", "``") + "`";
          case MSSQL -> "[" + n.replace("]", "]]") + "]";
          default -> "\"" + n.replace("\"", "\"\"") + "\"";
      };
  }
  ```
  `folded(name, d)` — `core/logical/Dialect` 의 Oracle·Tibero 상수면 대문자, PostgreSQL 상수면 소문자, 그 밖은 그대로. import `kr.ejg.toolbox.core.gen.DdlGen`
- 시험(먼저): `reservedNamesAreQuoted` — `new Masking.Candidate("APP", "USER", "ORDER", "name", "keyword", null, null, "VARCHAR", 50L, true)` 하나로 `Masking.sql(…)` 을 방언마다 → mssql `[USER]`·`[ORDER]` · mariadb `` `USER` `` · oracle `"USER"`·`"ORDER"` · postgresql `"user"`·`"order"`. 예약어 아닌 이름(`TB_MBER`·`MBER_NM`)은 따옴표 없음. RED: `UPDATE APP.USER SET`
- 의도된 골든 변화: `golden/logical/masking-*.sql` 은 픽스처에 예약어 이름이 있을 때 그 이름에 따옴표가 **더해지는 것만**. 그 밖 diff 는 사다리
- 검증: `bash scripts/mvn.sh -q test -Dtest='MaskingTest,LogicalRoutesTest,ArchitectureTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 db + corpus

### 3-20 — 마스킹 제외 키에 스키마
- 대상: `web/LogicalRoutes.java` · `src/main/resources/tools/logical_name.html` · `LogicalRoutesTest` · `golden/check/self-check.txt`(내린 값)
- 변경: `record MaskCol(String owner, String table, String col)`. `register` 의 `:109-112`(집합 만들기 넉 줄)를 `java.util.function.Predicate<kr.ejg.toolbox.core.logical.Masking.Candidate> ex = excluder(mreq.exclude());` 한 줄로, `:116` 을 `boolean excluded = ex.test(c);` 로:
  ```java
  /** B-09 — 제외 키. owner 가 있으면 그 스키마만, 없으면(옛 요청·CLI) 모든 스키마 */
  private static java.util.function.Predicate<kr.ejg.toolbox.core.logical.Masking.Candidate> excluder(List<MaskCol> exclude) {
      java.util.Set<String> keys = new java.util.HashSet<>();
      for (MaskCol x : exclude == null ? List.<MaskCol>of() : exclude) {
          String owner = x.owner() == null || x.owner().isBlank() ? "*" : x.owner();
          keys.add((owner + "." + nz(x.table()) + "." + nz(x.col())).toUpperCase(java.util.Locale.ROOT));
      }
      return c -> {
          String tail = ("." + nz(c.table()) + "." + nz(c.col())).toUpperCase(java.util.Locale.ROOT);
          return keys.contains(nz(c.owner()).toUpperCase(java.util.Locale.ROOT) + tail) || keys.contains("*" + tail);
      };
  }
  ```
  화면 — `:481` 옆에 `cb.setAttribute('data-o', c.owner || '');` · `maskExclude` 의 `out.push({ … })` 를 `out.push({ owner: boxes[i].getAttribute('data-o') || null, table: boxes[i].getAttribute('data-t'), col: boxes[i].getAttribute('data-c') });`
- 시험(먼저): `maskingExcludeIsPerSchema` — CSV 에 `A,TB_USER,USER_NM,VARCHAR2,50` 과 `B,TB_USER,USER_NM,VARCHAR2,50` → `exclude: [{owner: "A", table: "TB_USER", col: "USER_NM"}]` 이면 후보 행 가운데 owner `A` 만 `excluded` 참이고 SQL 에 `UPDATE B.TB_USER` 는 있고 `UPDATE A.TB_USER` 는 없다 · `owner` 없는 제외는 둘 다 참(지금 동작). 기존 `maskingDetectsExcludesAndSaves` 초록. RED: `B` 도 `excluded` 참
- 의도된 골든 변화: `self-check.txt` 의 `LogicalRoutes.java|…register(4)` 세 줄이 **내린 값으로**(길이 128 → 125 어림, 복잡도 24 → 23 어림)
- 검증: `bash scripts/mvn.sh -q test -Dtest='LogicalRoutesTest,SmokeHtmlUnitTest,ToolsFolderTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 java + corpus(`tools/`)

### 3-18 — 마스킹 후보 좁히기
- 대상: `src/main/resources/logical/masking.yaml` · `core/logical/Masking.java` · `MaskingTest` · `golden/logical/masking-candidates.json`
- 변경: yaml 머리 주석에 한 줄(`# notPii: 종류를 가리기 앞에 본다 — 논리명·코멘트가 endings 로 끝나거나 컬럼 이름이 columns 에 걸리면 개인정보 값이 아니다(여부·코드·내용 꼴, B-03).`)과 `kinds:` 위에
  ```yaml
  notPii:
    endings: [여부, 코드, 구분, 유형, 종류, 일시, 일자, 건수, 횟수, 내용, 제목, 순번, 상태]
    columns: '_(?:YN|AT|CD|CODE|SE|TY|TYPE|CNT|CO|CN|SJ|SN|STTUS)$'
  ```
  주소 종류 `excludeColumns` 의 `(?:ADRES|ADDR)` → `(?:ADRES|ADDR|ADDRESS)`.
  ```java
  public record Rules(List<Kind> kinds, List<String> notEndings, Pattern notColumns) {
      public Rules {
          kinds = List.copyOf(kinds);
          notEndings = List.copyOf(notEndings);
      }
      public Rules(List<Kind> kinds) {
          this(kinds, List.of(), null);
      }
      …
  }

  /** B-03 — 여부·코드·내용 꼴은 종류를 가리기 앞에 뺀다 */
  private static boolean notPii(Input in, String col, Rules r) {
      for (String e : r.notEndings()) {
          if (endsWith(in.logicalName(), e) || endsWith(in.comment(), e)) {
              return true;
          }
      }
      return r.notColumns() != null && r.notColumns().matcher(col).find();
  }
  ```
  - `rules()` — `Map<String, Object> np = (Map<String, Object>) doc.get("notPii");` 로 읽어 `new Rules(kinds, np == null ? List.of() : strings(np.get("endings")), np == null || np.get("columns") == null ? null : Pattern.compile(String.valueOf(np.get("columns"))))`
  - `detect` — `String col = …;` 다음에 `if (notPii(in, col, rules)) { continue; }`
  - `excluded` 의 첫 루프 조건에 「X명 → X이름」: `endsWith(in.logicalName(), e) || endsWith(in.comment(), e) || e.endsWith("명") && (endsWith(in.logicalName(), stem(e) + "이름") || endsWith(in.comment(), stem(e) + "이름"))` — 길면 `private static boolean ends(Input in, String e)` 로 묶고 `stem(e)` 는 `e.substring(0, e.length() - 1)`
- 시험(먼저): `flagAndCodeColumnsAreNotPii` — `in("T", "EMAIL_YN", null, "이메일수신여부", "CHAR", 1L)` · `in("T", "PHONE_CERT_YN", null, null, "CHAR", 1L)` · `in("T", "ADDR_TYPE_CD", null, null, "VARCHAR2", 10L)` · `in("T", "EMAIL_ANSWER_AT", null, null, "CHAR", 1L)` · `in("T", "EMAIL_CN", null, "이메일내용", "VARCHAR2", 4000L)` · `in("T", "MENU_NM", null, "메뉴이름", "VARCHAR2", 60L)` · `in("T", "WEB_ADDRESS", null, null, "VARCHAR2", 200L)` → 후보 0 · 같이 넣은 `EMAIL`(코멘트 「이메일」)·`MBTLNUM`·`BRTH_DE`(코멘트 「생년월일」)·`USER_NM`(코멘트 「이름」)·`EMAIL_ADRES` 는 후보 다섯 그대로. RED: 후보 열둘
- 의도된 골든 변화: `masking-candidates.json` — 후보가 **주는 것만**(픽스처에 그런 꼴이 있을 때). 느는 줄·종류가 바뀌는 줄은 사다리. corpus·db 골든은 `V-66a`·`V-66`
- 검증: `bash scripts/mvn.sh -q test -Dtest='MaskingTest,LogicalRoutesTest,DeliverableServiceTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 3-19(같은 파일)
- 사다리: `detect` 복잡도가 15 를 넘으면 — 두 종류 루프를 `private static Kind hit(Input in, String col, Rules rules, boolean byKeyword)` 로 뺀다

### 1-105 — 스냅샷 긴 값
- 대상: 새 `src/main/resources/db/migration/V017__snapshot_long_values.sql` · `core/meta/SnapshotStore.java` · `web/MetaRoutes.java` · 시험 `SnapshotStoreTest`·`MetaRoutesTest`·`DbTest`
- 변경:
  ```sql
  -- V017 — F-K10 긴 값 하나로 스냅샷 저장이 통째 실패하지 않게(scope 와 같은 길이)
  ALTER TABLE snapshot ALTER COLUMN schemas VARCHAR(1000000);
  ALTER TABLE snap_table ALTER COLUMN comment VARCHAR(1000000);
  ALTER TABLE snap_column ALTER COLUMN comment VARCHAR(1000000);
  ALTER TABLE snap_column ALTER COLUMN default_value VARCHAR(1000000);
  ```
  ```java
  // SnapshotStore.save — catch 를 둘로
  } catch (SQLException e) {
      c.rollback();
      throw tooLong(e);
  } catch (RuntimeException e) {
      c.rollback();
      throw e;
  }

  private static final Pattern LONG_COLUMN = Pattern.compile("column \"([A-Z_]+)");

  /** F-K10 — 길이 넘침(22001)은 INSERT 문·값 없이 칸 이름만 든 글로. 그 밖은 그대로 */
  private static SQLException tooLong(SQLException e) {
      for (SQLException x = e; x != null; x = x.getNextException()) {
          if ("22001".equals(x.getSQLState())) {
              Matcher m = LONG_COLUMN.matcher(String.valueOf(x.getMessage()));
              return new SQLException("스냅샷 저장 실패 — 저장 칸보다 긴 값이 있다" + (m.find() ? "(" + m.group(1) + ")" : ""), "22001");
          }
      }
      return e;
  }
  ```
  `MetaRoutes` — `/api/meta/snapshot` 람다 몸을 `private static void postSnapshot(Context ctx, JobManager jobs, SnapshotService service)` 로 옮기고 `connId` 검사 뒤에 `if (req.note() != null && req.note().length() > 1000) { ctx.status(400).json(Map.of("message", "메모는 1000자까지")); return; }`
- 시험(먼저): `SnapshotStoreTest.longValuesSurvive` — 표·컬럼 코멘트 5,000자, 컬럼 기본값 5,000자, 스키마 401개(빈 표 목록)로 `save` → 되읽은 것이 넣은 것과 같다. `tooLongFailsWithoutSqlText` — `nativeType` 이 4,001자인 컬럼 → `SQLException`, 글에 `NATIVE_TYPE` 이 있고 `INSERT` 와 그 값 앞 20자가 없다. `MetaRoutesTest.longNoteIs400` — 메모 1,001자 → 400. `DbTest` 의 마이그레이션 수 16 → 17. RED: 첫 시험 `Value too long` · 둘째 글에 `INSERT INTO` · 셋째 202
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='SnapshotStoreTest,SnapshotServiceTest,MetaRoutesTest,DbTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 db + corpus
- 사다리: H2 가 `ALTER COLUMN comment` 를 예약어로 거절하면 `"COMMENT"` 로 감싼다(V001 이 만든 이름은 대문자). `migration-immutable` 이 V017 추가를 막으면 7절

### 3-21 — 사전: 길이 · 0 건
- 대상: `core/dict/DictStore.java` · `web/DictRoutes.java` · `DictStoreTest`·`DictRoutesTest` · `self-check.txt`(내린 값)
- 변경:
  ```java
  static final int ABBR_MAX = 100;
  static final int KO_MAX = 200;
  private static final String TOO_LONG = "약어는 100자·한글은 200자까지다";

  // putUser — 빈 값 검사 뒤
  if (a.length() > ABBR_MAX || k.length() > KO_MAX) {
      throw new IllegalArgumentException(TOO_LONG);
  }

  /** 넣을 (약어, 한글) — 빈 칸·겹친 약어는 건너뛴다. 긴 줄은 줄 번호와 함께, 한 줄도 없으면 거절(F-K12·F-K13) — 연결을 열기 앞에 */
  private static List<String[]> orgPairs(List<List<String>> rows, int phys, int ko) {
      List<String[]> out = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      for (int i = 1; i < rows.size(); i++) {
          String p = cell(rows.get(i), phys).trim().toUpperCase(Locale.ROOT);
          String k = cell(rows.get(i), ko).trim();
          if (p.isEmpty() || k.isEmpty() || !seen.add(p)) {
              continue;
          }
          if (p.length() > ABBR_MAX || k.length() > KO_MAX) {
              throw new IllegalArgumentException((i + 1) + "번째 줄 — " + TOO_LONG);
          }
          out.add(new String[] {k, p});
      }
      if (out.isEmpty()) {
          throw new IllegalArgumentException("기관표준단어가 한 줄도 없다 — 머리(물리명·논리명)와 구분자를 확인");
      }
      return out;
  }
  ```
  `importOrg` — `ko` 를 정한 바로 뒤 `List<String[]> pairs = orgPairs(rows, phys, ko);`, 연결 안의 `seen`·`n`·행 루프를 `for (String[] p : pairs) { ps.setString(1, p[0]); ps.setString(2, p[1]); ps.addBatch(); }` 로 바꾸고 `return pairs.size();`. `DictRoutes` — `/api/dict/import` 람다 몸을 `private static void importLocal(Context ctx, DictStore dict)` 로 옮기고 `dict.putUser(abbr, e.getValue()); imported++;` 를 `try { … } catch (IllegalArgumentException x) { skipped++; }` 로 싼다(주석 `// F-K12 — 긴 항목은 건너뛴다`)
- 시험(먼저): `DictStoreTest.orgRejectsLongAndEmpty` — `putUser("A".repeat(101), "가")` 와 `putUser("AB", "가".repeat(201))` 이 `IllegalArgumentException` · 정상 파일을 넣은 뒤 한글 201자 줄이 든 파일은 `IllegalArgumentException` 이고 `count("org")` 그대로 · 한 열짜리 파일(`"이름\n가\n나\n"`)도 `IllegalArgumentException` 이고 그대로. `DictRoutesTest.longWordsAre400` — `PUT /api/dict/user/X` 에 한글 201자 → 400 · `POST /api/dict/import` 에 정상 하나 + 201자 하나 → 200, `imported` 1·`skipped` 1 · `POST /api/dict/org/import` 한 열 파일 → 400. RED: 500 · `{"imported":0}`
- 의도된 골든 변화: `self-check.txt` — `DictStore.java|metric.nesting|…importOrg(1)` 줄이 사라지거나 내리고, `DictRoutes.java|…register(2)` 가 내린 값으로
- 검증: `bash scripts/mvn.sh -q test -Dtest='DictStoreTest,DictRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus(`core/dict`)

### 0-103 — git 필터: 이름 · 범위
- 대상: `core/vcs/WorkingCopy.java` · `WorkingCopyTest`
- 변경:
  ```java
  private static final List<String> FILTER_LIST = List.of("--includes", "--name-only", "--list", "-z");

  static List<String> filtersOff(Path root) {
      Set<String> names = new TreeSet<>(filterNames(ok(Cli.run(Cli.Exe.GIT, configArgs("--local"), root)).text()));
      // config.worktree(C-VCS-SCOPE) — 확장이 꺼져 있고 작업 트리가 여럿이면 git 이 거절한다. 그러면 로컬 것만
      Cli.run(Cli.Exe.GIT, configArgs("--worktree"), root).filter(Cli.Result::ok).ifPresent(r -> names.addAll(filterNames(r.text())));
      List<String> out = new java.util.ArrayList<>();
      for (String n : names) {
          if (n.indexOf('=') >= 0 || n.indexOf('\n') >= 0) { // C-VCS-EQ — -c 는 첫 = 에서 키를 끊는다. 못 끄는 필터가 있으면 status 를 안 돌린다
              throw new IllegalStateException("저장소 설정의 filter 이름에 = 가 있어 끌 수 없다 — 변경분 조회를 하지 않는다");
          }
          String x = "filter." + n;
          out.addAll(List.of("-c", x + ".clean=", "-c", x + ".process=", "-c", x + ".required=false"));
      }
      return out;
  }

  private static List<String> configArgs(String scope) {
      List<String> a = new java.util.ArrayList<>(List.of("config", scope));
      a.addAll(FILTER_LIST);
      return a;
  }

  /** `filter.<이름>.<키>` 의 이름들 — -z 출력(NUL 구분) */
  private static Set<String> filterNames(String text) {
      Set<String> names = new TreeSet<>();
      for (String k : text.split("\u0000")) {
          int dot = k.lastIndexOf('.');
          if (k.startsWith("filter.") && dot > "filter.".length()) {
              names.add(k.substring("filter.".length(), dot));
          }
      }
      return names;
  }
  ```
  `gitChanged` 의 인자 목록에서 `"--untracked-files=all"` 뒤에 `"--ignore-submodules=dirty"`. `filtersOff` javadoc 에 「로컬·작업 트리 설정(include 포함)」·「안에 든 저장소는 들여다보지 않는다」 를 반영
- 시험(먼저): `gitRefusesFilterNameWithEquals` — `gitIgnoresRepoFilterAndGpgCommands` 의 저장소 꼴에서 `.git/config` 끝에 `[filter "a=b"]\n\tclean = "echo hit > eq-hit.txt; cat"\n` 을 덧쓰고 `.gitattributes` 를 `*.java filter=a=b` 로, 파일 mtime 을 5초 뒤로 → `assertThrows(IllegalStateException.class, () -> WorkingCopy.changed(repo))` · `eq-hit.txt` 없음. `gitIgnoresWorktreeAndNestedFilters` — ① `git config extensions.worktreeConfig true` · `git config --worktree filter.evil.clean "echo hit > wt-hit.txt; cat"`(이 명령이 실패하면 `assumeTrue` 로 건너뜀) · `.gitattributes` `*.java filter=evil` · mtime 밀기 → `WorkingCopy.changed(repo)` 뒤 `wt-hit.txt` 없음 ② 안 폴더 `sub` 에 `git init` + `.gitattributes`(`*.java filter=evil`) + 파일 커밋 + `git config filter.evil.clean "echo hit > sub-hit.txt; cat"`, 바깥에서 `git add sub`·커밋, 안 파일 mtime 밀기 → `changed(repo)` 뒤 `sub/sub-hit.txt` 없음. RED: `filtersOff=[-c, filter.a=b.clean=, …]` 로 표식이 생김 · `wt-hit.txt`·`sub-hit.txt` 생김
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='WorkingCopyTest,CheckRoutesTest,ArchitectureTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus(`core/vcs`)
- 사다리: `Cli.Result.text()` 가 NUL 을 안 남기면 — `-z` 를 빼고 `\\R` 로 나눈다(줄바꿈 든 이름 거절은 뺀다). `git add sub` 가 gitlink 를 못 만들면(옛 git) ② 는 `assumeTrue` 로 건너뛰고 이력에 적는다

### 0-104 — codecheck 블록의 0열 주석
- 대상: `core/profile/ProfileStore.java` · `ProfileStoreTest` · `self-check.txt`(내린 값)
- 변경: `saveCodeCheck:171-174` 를 `int end = blockEnd(lines, c);` 로:
  ```java
  /** 블록 [c, end) 의 끝 — 들여쓴 줄·빈 줄까지. 0열 주석은 건너뛰고 뒤 줄이 정한다(G2-K14) */
  private static int blockEnd(List<String> lines, int c) {
      int end = c + 1;
      for (int i = c + 1; i < lines.size(); i++) {
          String l = lines.get(i);
          if (l.startsWith("#")) {
              continue;
          }
          if (!l.isBlank() && !Character.isWhitespace(l.charAt(0))) {
              break;
          }
          end = i + 1;
      }
      return end;
  }
  ```
  `splice` — 딸린 줄 루프의 조건 `lines.get(j).isBlank() || …` 를 `lines.get(j).isBlank() || lines.get(j).startsWith("#") || …` 로, 뒤로 물리는 루프 `lines.get(j - 1).isBlank()` 를 `lines.get(j - 1).isBlank() || lines.get(j - 1).startsWith("#")` 로(0열 주석은 남긴다)
- 시험(먼저): `saveCodeCheckSurvivesColumnZeroComments` — 셋 다 `saveCodeCheck(name, Map.of("jsp", true), Map.of("common.sysout", false))` 뒤 `store.load` 가 저장값과 같고, 파일에 `rules:` 로 시작하는(들여쓰기 무시) 줄이 정확히 하나이고, `# 설명` 줄이 남아 있다: ① `name: a\ncodecheck:\n# 설명\n  groups: { java: true }\n` ② `name: a\ncodecheck:\n  groups: { java: true }\n# 설명\n  rules: { common.sysout: true }\n` ③ `name: a\ncodecheck:\n  rules:\n# 설명\n    common.sysout: true\n  groups: { java: true }\n`. 기존 saveCodeCheck 시험 일곱 초록. RED: `UncheckedIOException`·`IllegalStateException`·`rules:` 둘
- 의도된 골든 변화: `self-check.txt` 의 `ProfileStore.java|…saveCodeCheck(3)` 가 내린 값으로
- 검증: `bash scripts/mvn.sh -q test -Dtest='ProfileStoreTest,ProfileRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 java

### 8-18 — `check --groups` 가 나머지를 끈다
- 대상: `cli/CodeCommands.java` · `CodeCliTest`
- 변경: `body` 의 `if (groups != null) { … }` 블록을 `if (groups != null) { b.put("groups", groupMap()); }` 로:
  ```java
  /** G2-K2 — 고른 묶음만 켜고 나머지는 끈다(도움말 그대로). 모르는 이름은 끝 코드 2 */
  private Map<String, Boolean> groupMap() throws Exception {
      Set<String> known = new TreeSet<>();
      for (JsonNode r : batch.call("GET", "/api/check/rules", null)) {
          known.add(r.path("group").asText());
      }
      Set<String> picked = new TreeSet<>();
      groups.forEach(x -> picked.add(x.trim()));
      List<String> bad = picked.stream().filter(x -> !known.contains(x)).toList();
      if (!bad.isEmpty()) {
          throw batch.fail(2, "모르는 묶음: " + String.join("·", bad) + " — " + String.join("·", known));
      }
      Map<String, Boolean> g = new LinkedHashMap<>();
      known.forEach(k -> g.put(k, picked.contains(k)));
      return g;
  }
  ```
  `/api/check/rules` 응답은 규칙 맵의 배열이고 원소마다 `group` 이 있다(`CheckRoutes.getRules:98-128`). GET 꼴은 `SelfTest:92` 와 같다
- 시험(먼저): `groupsTurnOffTheRest` — `fx.run("check", "check", "--groups", "file", "--json", "--profile", "t")` 의 출력 JSON `findings` 가 비지 않고 전부 묶음 `file`(원소의 `group`, 없으면 규칙 id 가 `file.` 으로 시작) · `fx.run("check", "check", "--groups", "commn", "--profile", "t").code()` 가 2 이고 err 에 `모르는 묶음`. RED: 다른 묶음 지적이 섞임 · 끝 코드 0 또는 3
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='CodeCliTest,BatchCliTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 java

### 5-117 — 붙여넣기 언어 추측
- 대상: `core/check/CheckRunner.java`(`guessLang`) · `CheckStoreTest`
- 변경: `:221` 의 조건을 `if (t.contains("<%") || t.contains("<c:") || t.startsWith("<") && t.contains("${")) {` 로, javadoc 을 「붙여넣기 언어 추정 — XML(mapper) · JSP(스크립틀릿·JSTL, 또는 마크업으로 시작하고 EL) · Java · TS · 나머지 JS. `${` 혼자서는 JSP 가 아니다(D-guessLang — Spring `@Value`·템플릿 리터럴)」 로
- 시험(먼저): `CheckStoreTest.guessLang` 에 — `"package a;\nclass A {\n  @Value(\"${a.b}\") String v;\n}"` → `java` · ``"import { a } from 'b';\nconst s = `${a}`;"`` → `ts` · ``"var s = `${a}`;"`` → `js` · `"<td>${x}</td>"` → `jsp`. RED: 앞 셋이 `jsp`
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckStoreTest,CheckRunnerTest,CheckRoutesTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus(`core/check`)

### 5-118 — `security.sqlDollar`: SQL 주석
- 대상: `src/main/resources/check/rules.yaml`(한 줄) · `core/check/RegexRule.java` · `CheckRulesTest`
- 변경: `rules.yaml:225` → `params: {when: '<(?:mapper|sqlMap)\b', sqlComments: true}   # 5-44a — MyBatis·iBATIS 매퍼만(…) · 5-118 — SQL 주석(-- · /* */) 안은 안 본다`(뒤 주석은 지금 글 + 덧붙임). `RegexRule` — `when` 을 `params` 에서 읽는 자리 옆에 `private final boolean sqlComments;`(= `params` 의 `sqlComments` 가 `true`), `applyText:45` 를
  ```java
  String[] lines = Boolean.TRUE.equals(def.skipComments()) ? cleaned(s).split("\n", -1) : raw;
  ```
  ```java
  /** 주석을 지운 글. sqlComments 면 그 위에 SQL 주석도(5-118 — 매퍼 XML 의 주석 처리한 SQL 줄) */
  private String cleaned(Source s) {
      String t = skipped(s);
      return sqlComments ? stripComments(t, "sql") : t;
  }
  ```
- 시험(먼저): `CheckRulesTest.sqlDollarSkipsSqlComments` — 그 클래스가 실제 규칙으로 `runText` 를 부르는 기존 꼴(`:226·234`)대로, 글 `<?xml version="1.0"?>\n<mapper namespace="a">\n<select id="s">\nSELECT id FROM t\n-- AND a = ${x}\n/* ${y} */\nORDER BY ${sort}\n</select>\n</mapper>\n` 을 `xml` 로 → `security.sqlDollar` 지적이 정확히 하나, 7행. RED: 셋(5·6·7행)
- 의도된 골든 변화: `golden/check/pure-rules.json` **diff 0**(`PureRulesJsonTest` 초록) · `golden/check/fixtures.json` diff 0. corpus 검사 골든의 `security.sqlDollar` 수가 주는 것은 `V-66a`
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,PureRulesJsonTest,MyBatisRulesTest,RuleSetTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus
- 사다리: `RuleSet` 적재가 모르는 `params` 열쇠를 거절하면 그 허용 목록에 `sqlComments` 를 더한다. `pure-rules.json` 이 바뀌면 — `params` 가 실린다는 뜻이다. 멈춘다(7절)

### 6-47 — SQL 리터럴 끝
- 대상: `core/text/SqlTokens.java` · `SqlTokensTest` · `SqlTablesTest`(+ `golden/analyze/sql-tables.json`)
- 변경: `next` — `bindAt` 가지 뒤, 따옴표 식별자 가지 앞에
  ```java
  int lit = quotedLiteral(s, i);
  if (lit > 0) {
      out.add(new Tok(LITERAL, i));
      return lit;
  }
  ```
  ```java
  /** PG $$…$$·$꼬리표$…$꼬리표$ · Oracle q'[…]' 의 끝 다음 위치. 그 꼴이 아니거나 안 닫히면 -1(G2-K10) */
  private static int quotedLiteral(String s, int i) {
      char c = s.charAt(i);
      if (c == DOLLAR) {
          return dollarEnd(s, i);
      }
      boolean q = (c == 'q' || c == 'Q') && i + 2 < s.length() && s.charAt(i + 1) == QUOTE && (i == 0 || !wordChar(s.charAt(i - 1)));
      return q ? qEnd(s, i + 2) : -1;
  }

  private static int dollarEnd(String s, int i) {
      int k = i + 1;
      while (k < s.length() && (Character.isLetterOrDigit(s.charAt(k)) || s.charAt(k) == UNDERSCORE)) {
          k++;
      }
      if (k >= s.length() || s.charAt(k) != DOLLAR) {
          return -1;
      }
      String tag = s.substring(i, k + 1);
      int e = s.indexOf(tag, k + 1);
      return e < 0 ? -1 : e + tag.length();
  }

  private static int qEnd(String s, int open) {
      char d = s.charAt(open);
      char close = d == '[' ? ']' : d == '{' ? '}' : d == '(' ? ')' : d == '<' ? '>' : d;
      int e = s.indexOf(String.valueOf(close) + QUOTE, open + 1);
      return e < 0 ? -1 : e + 2;
  }
  ```
  `literalEnd` 의 걸음 — `k += s.charAt(k) == QUOTE || escapedQuote(s, k) ? 2 : 1;`
  ```java
  /** \' 바로 뒤가 낱말 글자면 벗어난 따옴표('it\'s' — MySQL·PG E''). 뒤가 공백·괄호·끝이면 표준 리터럴의 끝('%\' ESCAPE '\') */
  private static boolean escapedQuote(String s, int k) {
      return s.charAt(k) == '\\' && k + 2 < s.length() && s.charAt(k + 1) == QUOTE && wordChar(s.charAt(k + 2));
  }
  ```
  `literalEnd` javadoc 에 「`\'` 는 뒤가 낱말 글자일 때만 글자 안」 을 더한다
- 시험(먼저): `SqlTokensTest.literalShapes` — `tokens("SELECT 'a\\'b' FROM t1 WHERE c = 'd'")` 가 `[SELECT, ', FROM, T1, WHERE, C, =, ']` · `tokens("SELECT $$it's$$ FROM a")` 가 `[SELECT, ', FROM, A]` · `tokens("SELECT $fn$x'y$fn$ FROM a")` 가 같은 꼴 · `tokens("SELECT q'[it's]' FROM a")` 가 같은 꼴 · `tokens("x LIKE '%\\' ESCAPE '\\' AND y = 1")` 이 `[X, LIKE, ', ESCAPE, ', AND, Y, =, 1]` · 안 닫힌 `$$` 는 예외 없이 끝난다. `SqlTablesTest.CASES` 에 넷 — `backslashQuote`(`SELECT * FROM A WHERE X = 'it\'s' AND Y IN (SELECT ID FROM B)`) · `likeEscapeIdiom`(`SELECT * FROM A WHERE X LIKE '%\' ESCAPE '\' AND Y IN (SELECT ID FROM B)`) · `dollarQuote`(`SELECT $$it's$$ FROM A JOIN B ON A.ID = B.ID`) · `oracleQQuote`(`SELECT q'[it's]' FROM A, B`) — 골든에서 넷 다 `A`·`B` 둘이고 `unresolved` 빔. RED: `B` 없음(`likeEscapeIdiom` 은 지금도 둘 — 회귀 막이)
- 의도된 골든 변화: `sql-tables.json` 에 사례 넷이 **더해지는 것만** · 기존 사례 diff 0. `golden/check/*` 는 diff 0(`SqlLint` 가 같은 훑개를 쓴다 — 바뀌면 그 줄이 위 세 꼴 리터럴을 품었는지 보고, 아니면 고침이 넓다)
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlTokensTest,SqlTablesTest,SqlLintTest,CheckRulesTest,MapperIndexTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-46(같은 골든 파일). 레인 corpus(`core/text`)

### 7-33 — 템플릿 코멘트
- 대상: `core/gen/GenModel.java` · `GenTemplatesTest`
- 변경:
  ```java
  /** A-13 — 코멘트가 자바 주석·XML 주석·JSP 글·속성 어디에 들어가도 깨지지 않게 엔티티로. HTML·javadoc 에서는 원래 글자로 보인다 */
  static String safe(String s) {
      return s.replaceAll("\\R", " ").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
              .replace("*/", "*&#47;").replace("--", "-&#45;").replace("\\u", "&#92;u").replace("${", "&#36;{").replace("#{", "&#35;{");
  }
  ```
  `of` 의 `:117-118` `String comment = <식>;` 을 `String comment = safe(<식>);` 로, `:146` `m.put("comment", <식>);` 을 `m.put("comment", safe(<식>));` 로 — **줄 수를 늘리지 않는다**(`of` 길이 107 = 기준선). 템플릿은 안 고친다
- 시험(먼저): `hostileCommentsStayInert` — 표 코멘트와 컬럼 코멘트가 `공지 */ class X { } --> "표시용" <b> A ${a}`(자바 문자열로 옮길 때 `\\u0041`)인 표를 `render("egov35", "oracle", …)` 로 → 자바 소스 넷에 `*/ class X` 와 `A` 이 없다 · 매퍼 XML 에 `--> "표시용"` 이 없다 · JSP 에 `&quot;표시용&quot;` 과 `&lt;b&gt;` 가 있고 `${a}` 가 없다(`&#36;{a}`). 기존 골든 시험 셋 초록. RED: `*/ class X` 가 있다
- 의도된 골든 변화: `golden/gen/**` 은 픽스처 코멘트에 `& < > "` 가 있을 때 그 글자가 엔티티로 **바뀌는 것만**. 그 밖 diff 는 사다리
- 검증: `bash scripts/mvn.sh -q test -Dtest='GenTemplatesTest,GenModelTest,GeneratorTest,GenerateRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 db + corpus(`core/gen`)

### 7-34 — 매퍼 예약어 컬럼
- 대상: `core/gen/FieldRoles.java` · `templates/gen/egov35/Mapper.xml.ftl` · `GenTemplatesTest`
- 변경: `FieldRoles.apply` 첫 줄에 `f.put("columnSql", InsertGen.ident(c.name(), InsertGen.db(dialect))); // A-36 — SQL 자리의 컬럼(예약어·특수 글자만 방언 따옴표로)`. `Mapper.xml.ftl` — SQL 자리의 `[=f.column]` 을 `[=f.columnSql]`, `[=p.column]` 을 `[=p.columnSql]` 로(`:13·21·23·25·30·34·37·51·60·68·83·87·93·94·99`). **`:8` resultMap 의 `column="[=f.column]"` 은 그대로**(결과 열 이름)
- 시험(먼저): `reservedColumnIsQuotedInMapper` — 컬럼 `NOTICE_ID`(PK)·`TITLE`·`ORDER` 인 표를 oracle 로 → 매퍼에 `"ORDER"` 가 SELECT 목록·INSERT 목록에 있고 `column="ORDER"`(resultMap)도 있다 · mariadb → `` `ORDER` `` · mssql → `[ORDER]` · `NOTICE_ID`·`TITLE` 은 따옴표 없음. RED: 맨 `ORDER`
- 의도된 골든 변화: `golden/gen/mapper-*.xml`·`gen/egov*/…_SQL_*.xml` diff 0(픽스처에 예약어 컬럼이 있으면 그 이름에 따옴표가 더해지는 것만)
- 검증: `bash scripts/mvn.sh -q test -Dtest='GenTemplatesTest,GenModelTest,GeneratorTest,GenerateRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 7-33(같은 시험 파일) · 4-36(`InsertGen.plain`)
- 사다리: `pk`·`insertCols`·`updateCols` 목록의 원소에 `columnSql` 이 없어 FreeMarker 가 던지면 — 그 목록이 `f` 를 베낀 새 맵이다. 베끼는 자리(`GenModel`·`FieldRoles`)에서 `columnSql` 도 같이 옮긴다(`GenModel.of` 의 줄 수는 안 늘린다 — 도우미 안에서)

### V-66a — 정적 분석 · corpus 골든 확인
1. `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify`(PMD·SpotBugs·ErrorProne) — 새 짝은 코드로 고친다(기준선에 안 더한다). 고치기 둘
2. `bash scripts/verify.sh --corpus`(7~12분). 허용 변화 —
   - `golden/corpus/ddl-unreadable.txt`·`ddl-summary.json` — 「못 읽은 줄」 이 **느는** 것(`ADD PARTITION`·빈 제약 줄). 새 줄이 §3-1 의 꼴인지 본다
   - `ddl-lint.json` — 인덱스·기본값 수가 **느는** 것(`ADD KEY`·`DEFAULT … FOR`), notes 가 **느는** 것(방언 모호). 유니크·인덱스의 컬럼 수가 주는 것은 표본에 DROP COLUMN 이 있을 때만(파일:줄을 이력에)
   - `sqlfixture.json`·`sqltables-diff.txt` — 「우리만」 이 **주는** 줄(권한 문장·배치 낱말) · 「그쪽만」 이 **주는** 줄(리터럴 뒤 표). 「우리만」 이 느는 줄은 허용 밖
   - `analyze-egov*.json`·`-missing-in-db.txt` — 표·문장 참조가 **느는** 것. `missing-in-db` 에 새 이름이 생기면 가짜 표인지 본다 — 가짜면 `6-47` 로 돌아가 고친다
   - `masking-egov.json` `candidates` 가 **주는** 것 · `masking-egov-candidates.txt` 에서 줄이 **빠지는** 것만(느는 줄·종류가 바뀌는 줄은 허용 밖)
   - 검사 골든(`check-*`)의 `security.sqlDollar` 수가 **주는** 것
   - `cleanup-*`·`gen-hr.json`·`hr-insert-b.txt`·`deliverable-*` 는 diff 0 기대 — 바뀌면 원인 행을 찾아 그 행의 「의도된 골든 변화」 와 댄다
   - 그 밖은 허용 밖 — 고치기 둘, 넘으면 `wip/V-66a`
3. diff 줄 수·종류별 건수·벽시계를 이력에
- 의존: 코드 행 전부(건너뛴 행 빼고)

### R-33 — 손 판정 · 처분 표
- `design/compare-2026-10-28.md` 끝에 `## ㊱ 번들 87 — 번들 86 리뷰 행 열·Important 스물둘·JavaGraph 쪼개기(2026-11-21)`:
  1. 처분 표 — 리뷰 행 11 + 키 22 × (행 · 강제 지점 시험 · 닫힘/남음/한계/건너뜀). 한계(1절 Out of scope)를 키마다 한 줄. 시간으로 건너뛴 행은 「건너뜀 — 번들 88」
  2. `V-66a` 의 골든 변화 표(종류별 건수 전→후)
  3. 손 판정 — **마스킹에서 빠진 후보 전부**(`masking-egov-candidates.txt` 의 `-` 줄 — 표.컬럼·종류·사유)를 하나씩: 개인정보 값이 아니면 「맞게 빠짐」, 값이면 그 줄을 살리게 `notPii` 의 그 꼬리를 뺀다(한 번 — 다시 `--corpus`) · `ddl-unreadable` 새 줄의 ALTER 꼴 분포(동사별 건수) · `SqlTables` 로 새로 잡힌 표 열 줄(파일:줄 — SQL 본문은 안 적는다)
  4. 끝에서 끝 — `run.bat --port 18870 --no-browser`(**프로필 없이**) → `curl -s -X POST "http://127.0.0.1:18870/api/gen/dto?save=true" -H "Content-Type: application/json" -d '{"ddl":"CREATE TABLE t (id INT, nm VARCHAR2(10) NOT NULL); ALTER TABLE t MODIFY (nm VARCHAR2(100));","packageName":"a.b","style":"RECORD"}'` 가 200 이고 `path` 에 `default` → 그 폴더를 지우고 서버를 끈다
- 코드·레인 지문이 안 바뀐다(3 에서 `masking.yaml` 을 고치면 그 고침은 `fix: 3-18 …` 로 따로 커밋하고 `--corpus` 를 다시)
- 의존: V-66a

### V-66 — `--db`
1. 여유 RAM — PowerShell `(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB`. 15 미만이면 7절
2. `bash scripts/docker-up.sh` → `bash scripts/verify.sh --db`(컨테이너 아홉, 17~35분). 다른 JVM 을 같이 띄우지 않는다
3. 허용 변화 —
   - `golden/corpus/db-*.json` — `maskingColumns` 가 **주는** 것 · `ddlGenWarnings`·`alterWarnings` 가 **주는** 것(`$` 이름) 또는 **느는** 것(큰 길이 키 경고) — 방향과 수를 이력에
   - `db-*-insert-b.txt` — 실패가 **주는** 것
   - `dollarNameInsertRuns`·`rulesChecksAutoRoundTrip`·`roFileMessages` 는 등급 A — 0 이어야 한다
4. `verify.sh --db` 와 `git push` 는 따로 낸다
- 의존: R-33
- 사다리: `dollarNameInsertRuns` 가 어느 방언에서 빨강이면 — 방언·단계·SQLState 만 이력에(값·SQL 본문 없음), 원인이 `4-36` 이면 그 행 시험을 먼저 더해 고친다(고치기 둘). CREATE 자체가 `$` 를 거절하는 방언이면 그 방언은 `skipIfOff` 로 끄고 이력 「드러난 것」

### 마무리
CLAUDE.md 「마무리」 1~8 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: ① 「조용히」 가 남은 자리 — 고친 길에서 경고·못 읽음·`notes`·400 없이 값이 빠지거나 바뀌는 곳(행 28 을 하나씩) ② 고침이 넓어 맞던 출력이 바뀐 자리 — 기존 골든 diff 를 행의 「의도된 골든 변화」 와 댄다 ③ `autoTail` 의 `inCreate` 가 AlterGen 길에서 켜진 채 남는 곳 ④ `notPii` 가 개인정보 값을 빼는 꼴(생년월일·주민번호·계좌 컬럼 이름) ⑤ 절대 규칙 3 — 새 경고·예외 글·400 본문에 SQL 본문·값이 실리는 자리(`2-46` 의 드라이버 글은 화면 응답이라 허용, 로그·이력·xlsx 는 아님) ⑥ 절대 규칙 7 — `outFile` 의 `default` 폴더·`MakeTemplates.under` ⑦ `0-103` — 필터가 도는 길이 남았나(`recent`·`gitPrefix` 등 다른 git 호출)) → `bash scripts/gate-probe.sh` → `verify.sh`(리뷰 고침이 건드린 무거운 레인만 다시 — db 레인 파일이면 새 행으로 돌린다) → push → PR(본문에 행마다 시작 세 줄 표) → CI 폴링 → claude[bot] 처분 → **머지 위임** `gh pr merge N --merge --delete-branch` → 끝 보고(건너뛴 행 목록 포함).

## 5. 금지 사항

- `pure/` · `ext-fixes.yaml` · `config/pmd/exclude-from-failure.properties` · `resources/quality/d90.json` · `golden/check/pure-rules.json` · 기존 마이그레이션 V001~V016 을 고치지 않는다. 새 마이그레이션은 V017 하나. `rules.yaml` 은 `security.sqlDollar` 의 `params` 한 줄만
- 새 의존성 0
- 화면은 `tools/logical_name.html` 의 `3-20` 세 줄만. 템플릿은 `Mapper.xml.ftl` 만(`7-33` 은 템플릿을 안 고친다). `mappings/`·`type-mapping.yaml`·`ddl-types.yaml` 을 고치지 않는다
- 기존 시험 단언을 지우거나 느슨하게 바꿔 통과시키지 않는다. 수가 바뀌는 단언은 행에 적힌 것뿐(`alterUnsupportedIsReported` 5 → 8 · `DbTest` 16 → 17)
- 기존 경고·사유·미해결 글자를 바꾸지 않는다(골든·baseline 열쇠) — `"길이 없는 문자 타입이 키 컬럼 — 길이 … 으로 냈다"`·`"자동증가 — 키 컬럼이 아니라 mariadb 가 못 받는다, 뺐다"`·`"DDL 에서 못 읽은 줄 …"`·`Unresolved` 코드. 새 글은 새 글자로
- self-check 기준선에 있는 메서드의 값을 올리지 않는다 · `DdlReader`·`AlterGen.Run`·`JavaGraph` 에 메서드를 더하지 않는다 · `GenModel.of`·`LogicalRoutes.register`·`SqlTables.extract`·`DdlGen.type`·`table` 에 줄·분기를 더하지 않는다 — 도우미로
- `AlterGen` 을 `1-102`·`1-103` 에서 고치지 않는다(`0-99` 의 javadoc 옮기기만)
- `DdlAlter` 가 타입 없는 `MODIFY (a NOT NULL)` 을 읽게 하지 않는다 · 모르는 표의 ALTER 를 「못 읽음」 으로 내게 하지 않는다
- `SqlTokens` 가 `\'` 를 뒤 글자를 안 보고 탈출로 읽게 하지 않는다
- `notPii.columns` 에 날짜 꼬리(`DT`·`DE`·`DATE`)·`NO`·`ID` 를 넣지 않는다
- `WorkingCopy` 가 `=` 든 필터 이름을 환경 변수(`GIT_CONFIG_COUNT`)로 끄게 하지 않는다 — 거절한다(옛 git 은 그 변수를 모른다)
- `DbCorpusBase` 의 기존 시험을 고치지 않는다 — 새 시험 하나만 더한다
- 1절 Out of scope 와 보정 Important 여덟을 같이 고치지 않는다
- 시험·경고·예외 글에 실제 사업 코드·SQL 을 쓰지 않는다
- worktree·`finishing-a-development-branch` 를 쓰지 않는다

## 6. 최종 검증

- `bash scripts/verify.sh` 초록 · `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify` 초록 · `bash scripts/verify.sh --corpus` 초록(등급 A 0) · `bash scripts/verify.sh --db` 초록(`dollarNameInsertRuns` 아홉 컨테이너 등급 A 0) · `bash scripts/gate-probe.sh` 열 전부 산다
- 강제 지점 시험이 전부 있다(§4 표, 건너뛴 행 빼고) — 각 행 이력에 RED → GREEN 한 줄
- 손 확인(R-33): compare ㊱ 처분 표 · 마스킹에서 빠진 후보 전부 판정 · 프로필 없는 서버의 `curl` 저장
- 회귀: `golden/` diff 는 행마다 적은 「의도된 골든 변화」 와 `V-66a`·`V-66` 의 허용 변화뿐 · `pure-rules.json` diff 0 · 규칙 수 147 그대로 · 마이그레이션 17 · `self-check.txt` 는 내린 값·사라진 줄 + `JavaViews.java|…value(4)` 한 줄
- CI 8 초록 · claude[bot] 「아니오」 처분 · PR 머지

## 7. 중단 조건

다음이면 임의로 풀지 말고 멈추고 보고한다(어느 스텝 · 무엇이 예상과 달랐나 · 가능한 선택지).
- 2절 구조 설명이 실제 코드와 다르다(줄 번호 어긋남은 아님 — 메서드·흐름이 다를 때)
- 검증이 2회 연속 실패하고 원인이 이 플랜 범위 밖
- 5절을 어기지 않고는 진행할 수 없다(특히 — 기준선 값을 올려야만 되는 고침, 기존 경고 글자를 바꿔야만 맞는 골든)
- 스텝에 없는 파일을 3개 이상 고쳐야 한다(골든·픽스처·`self-check.txt` 는 스텝에 있는 것이다)
- `1-105` 에서 `migration-immutable` 이나 `DbTest` 가 V017 을 거절한다
- `5-118` 에서 `pure-rules.json` 이 바뀐다
- `V-66a` 에서 `AnalyzeCorpusTest.egov` 의 `table` 미해결(등급 A)이 새로 나오거나, `ddl-unreadable` baseline 이 상한(표본의 2%)을 넘겨 빨강
- `R-33` 에서 마스킹에서 빠진 후보 가운데 개인정보 값이 꼬리 하나를 뺀 뒤에도 남는다
- `V-66` 에서 기존 등급 A(`alterRoundTrip`·`ddlRoundTrip`·`rulesChecksAutoRoundTrip`·`maskingRuns`)가 새로 빨강
- `--db` 앞 여유 RAM 15GB 미만(다른 세션 컨테이너 — 사람이 조율한다)

번들 모드의 「갈려도 안 멈춘다」 는 위 열 밖에서만 선다. 행당 계획 밖 결정 둘까지, 넘으면 `wip/<청크>`. `4-36` 이 `wip/` 로 빠지면 `9-9`·`7-34` 는 건너뛴다. `1-102` 가 빠지면 `1-103` 의 `auto(c)` 는 `c.autoIncrement()` 로 쓴다. `6-46` 이 빠지면 `6-47` 은 골든 사례만 제 것 넷을 더한다. 10:00 을 넘겨 건너뛴 행은 `wip/` 가 아니다 — 손대지 않고 `대기` 로 둔다.

## 8. 불확실 항목

사용자만 정할 수 있는 것 — 없음(범위·`1-101` (라)·`0-100` (가)·Important 22·B-03 (가)·sqlDollar (가)·머지 위임 다 답 받음).

실행해야만 알 수 있는 것 — 실행자가 해당 스텝에서 확인한다.
- **첫 스텝 전에**: `git log --oneline -1`(S0 커밋) · `git status --short` 깨끗 · `docker ps`(다른 세션 컨테이너가 떠 있으면 `V-66` 앞 RAM 을 미리 본다)
- **Javalin 이 `ContentTooLargeResponse` 를 예외 매퍼에 넘기나**(시험 실행) — `0-102` 사다리
- **`Cli.Result.text()` 가 NUL 을 남기나 · 설치된 git 이 `config --worktree` 와 gitlink `git add` 를 받나**(시험 실행) — `0-103` 사다리
- **POI `shiftRows` 가 다중 칸 배열 수식 행을 미나**(시험 실행) — `2-44` 사다리
- **H2 가 `ALTER COLUMN comment VARCHAR(…)` 를 받나 · 배치 INSERT 의 길이 넘침이 SQLState 22001 로 오나**(시험 실행) — `1-105` 사다리(`getNextException` 사슬까지 본다)
- **`RuleSet` 이 모르는 `params` 열쇠를 받나**(시험 실행) — `5-118` 사다리
- **`pk`·`insertCols`·`updateCols` 가 `f` 와 같은 맵인가**(시험 실행) — `7-34` 사다리
- **MySQL 5.7·8.0·MSSQL 이 맨 이름 `EMP$NO` 로 CREATE 를 받나**(컨테이너) — `9-9`·`V-66` 사다리
- **행마다 self-check 값**(시험 실행) — `-Dtest=SelfCheckTest`. 공통 사다리
