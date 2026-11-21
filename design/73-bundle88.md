# 설계 73 — 번들 88: 보정 Important 여덟 · 번들 87 리뷰 행 여섯 · 가로 점검 2차 Minor · `5-114` (Fable 설계 · Opus 실행, 2026-11-21)

## 맥락

번들 87(#132 `f52a415`)까지 가로 점검 2차(설계 68)의 Critical 34 와 4장 Important 38 을 닫았다. 남은 것은 3장 보정 Important 여덟 · Minor 101 · 번들 87 마무리 리뷰의 새 행 여섯 · 설계가 필요하던 `5-114` · G1-K15 다. 이 번들이 그것을 한 PR 로 닫는다(88~90 합침). 실행은 밤새 무인이고 시간 컷이 없다 — 차례가 곧 우선순위다.

**사용자 결정(2026-11-21 — 고정, 다시 묻지 않는다)**
1. 범위·차례 — ① 보정 Important 8 + LogSql(R-G-9 와 ⑤ 한 행) + 리뷰 행 여섯 + G1-K15 → ② C·D Minor → ③ `5-114` → ④ A·B·F Minor → ⑤ G1·G2 Minor. 한 번들 · PR 하나
2. `5-119` (가) — 줄 주석 안 `${}` 도 다시 error. `/*+` 힌트도, 블록 주석 `/* ${x} */` 도(값의 `*/` 로 탈출) 같이
3. `5-114` (가) — 같은 소스 루트의 VO getter 반환 형식까지 추론. 하나로 못 정하면 건너뜀
4. 머지 위임 있음 · 시간 컷 없음
5. 실행 Opus high · 마무리 독립 리뷰는 Fable 서브에이전트 effort xhigh
6. 레인 — `--db` 는 끝에 한 번. 무거운 레인 앞에 `mvn.sh -DskipTests verify` 한 번
7. (설계 중) `5-119` 의 둘째 규칙 **(가) 둘 다** — `mybatis.dollar`(warn)도 SQL 주석 안을 본다. 두 규칙이 같은 줄을 낸다
8. (설계 중) `8-21` **(가) 알리기만** — `check` 가 못 읽은 파일은 요약과 `[경고]` 로, 끝 코드는 그대로
9. (설계 중) `0-114` **(가) 거절** — 프로필 YAML 의 별칭·병합 키·같은 키 둘·둘째 문서는 읽기를 거절하고 사유를 말한다

**보정(2026-11-21, `0faa904` 트리 — 키 하나씩 코드와 대조)**
- 「확인 — 닫힘」 여덟(행에 안 넣는다):
  | 키 | 닫은 것 | 근거 시험 |
  |---|---|---|
  | C-TBL-BSLASH | `6-47` `e4ec669e` — `\'` 는 뒤가 낱말 글자일 때만 탈출 | `SqlTokensTest.literalShapes` · `SqlTablesTest` 사례 `backslashQuote` |
  | C-WEB-NULL · A-24 · B-21 · F-K14 · G1-K05 | `0-91` `773d91ee` — `StrictJson.checked`(본문 null·목록 안 null 400) · 모르는 charset 400 | `JsonErrorTest.nullBodyIs400`·`nullListElementIs400`·`unknownCharsetIs400` |
  | G1-K17 | `0-89` `65e3bb0c` — 값 끝을 손 훑개로(`valueEnd`·`plainEnd`) | `ProfileRoutesTest.longSpacesInValueAreLinear` |
  | D-runTextSOE | `0-92a`(번들 84) — 붙여넣기 스택 넘침을 가둔다 | `CheckRunnerTest.pastedStackOverflowIsSkipped` |
- D-a11yMultiline · D-piiLogSOE 는 순수본 재주입이 있어야 한다 — `5-100` 에 그대로(범위 밖)
- G1-K22 는 **일부 닫힘** — 번들 85(`5-113`)가 `Text.mixed` 와 코드 정리의 건너뜀을 넣었다. jsp 일괄이 남았다(`4-40`)
- 같은 줄에서 같이 닫히는 것 — G1-K10 은 `0-106`(R-G-6)에서 · G1-K14 는 `0-107`(G1-K15)에서 · C-JSP-QUAD 는 `6-50`(R-C-I3)에서 · D-dollarFragment 의 `${}` 쪽은 `5-119` 에서
- 설계 68 의 줄 번호는 거의 다 밀렸다 — 이 문서의 줄 번호는 `0faa904` 것이다
- `5-119` — 5-118 은 corpus 수를 하나도 안 바꿨다(표본 매퍼에 주석 안 `${}` 가 없다). 되돌려도 corpus 는 그대로다. 순수본 `pure-rules.json` 은 5-118 때 안 바뀌었으니 되돌리면 백엔드와 다시 같다
- `5-114` — 고침은 파일 하나의 글만 받는다(`ValueFix.apply`). 다른 파일을 읽는 길은 `Cleanup` 생성자가 받는 찾개(`LoggerShape.finder`·`PackageTypes.finder`)뿐이다 — 같은 꼴로 하나 더 단다. javaparser-symbol-solver 는 `m2/` 에 없다(새 의존성 금지)
- 레인(`scripts/verify-fingerprint.sh`) — db 레인: `core/meta`·`dialect`·`conn`·`sqlrun`·`quality`·`gen`·`logical` + `core/analyze/SqlProbe.java` + `resources/{db,conn,quality,gen,logical}`. corpus 레인: `core/*`(db·job·profile 빼고) + `resources/{check,gen,logical,dict,tools}`. `web/*`·`cli/*`·`core/{db,job,profile}` 는 java 만
- `Unresolved.KINDS` 는 스물로 못 박혀 있다(H2 열·corpus baseline·`UnresolvedKindsTest`) — 새 종류를 만들지 않는다

## 1. 목표와 범위

목표 — 가로 점검 2차의 남은 결함(보정 Important 여덟 · Minor)과 번들 87 리뷰 행 여섯, `5-114` 를 닫는다. 사용자가 겪는 변화: 이름이 `Order`·`Group` 인 엔티티와 `join o.items` 의 표가 CRUD 에 나온다 · `${ctx}/a.do`·`.do` 아닌 매핑 URL 이 JSP 링크로 잡히고 주석 처리한 스크립트 줄은 안 잡힌다 · 다른 모듈의 같은 이름 서비스가 안 섞인다 · `this.sqlSession`·지역 `SqlSession`·조상의 `@GetMapping`·템플릿 메서드·필드 제네릭·iBATIS `<sqlMap>` 이 프로그램 분석에 잡힌다 · `E_MAIL` 컬럼의 상세 화면이 런타임 오류를 안 낸다 · 틀린 규칙 정규식은 저장이 거절되고 규칙 화면이 안 죽는다 · 스레드 섞인 로그의 SQL 복원이 남의 값을 안 넣는다 · 길이 없는(chunked) 본문은 411 · 주석 안 `${}` 가 다시 error · equals 앞세우기가 같은 소스 루트 VO 의 String getter 를 고친다 · 생성 DTO 가 `\u`·큰 경계·record 금지 이름으로 컴파일이 안 깨진다 · 못 읽는 프로필·모르는 문서 번호·못 읽은 파일이 조용히 지나가지 않는다.

In scope — 코드 행 61(기존 리뷰 행 여섯 + `5-114` 를 a·b 둘로 + 새 행 쉰셋) + `V-67a`·`R-34`·`V-67`

| 묶음 | 행 | 닫는 키 |
|---|---|---|
| ① | `6-49` | R-C-I2 |
| ① | `6-50` · `6-51` | R-C-I3 · C-JSP-QUAD |
| ① | `6-52` | R-C-I6 |
| ① | `6-53` · `6-54` · `6-55` · `6-56` | R-C-I7(다섯 꼴) · R-C-I8 그래프 쪽 |
| ① | `6-57` | R-C-I8 |
| ① | `7-36` | R-A-I11 |
| ① | `0-106` | R-G-6 · G1-K10 |
| ① | `4-37` | R-G-9 · G2-K9 · G1-K18 |
| ① | `7-35` · `1-106` · `6-48` · `5-119` · `2-47` · `0-105` | 번들 87 리뷰 행(기존) |
| ① | `0-107` | G1-K15 · G1-K14 |
| ② | `6-58` | C-TBL-KEYWORD · C-TBL-DOLLAR · C-TBL-HASH |
| ② | `6-59` | C-TBL-CTE · C-TBL-MULTIUPD |
| ② | `6-60` | C-JOIN-SET |
| ② | `6-61` | C-MAP-INCLUDE |
| ② | `6-62` · `6-63` | C-PROBE-REGEX · C-PROBE-SEMI · C-PROBE-DUPCOL · C-PROBE-DBID |
| ② | `5-120` | D-textBlock · D-jspScriptComment |
| ② | `5-121` | D-ignoreInString |
| ② | `5-122` | D-dupId · D-dollarFragment · D-noWhereSubquery |
| ② | `5-123` · `5-124` · `5-125` | D-parseErrCount · D-overrideValidate · D-taintScope |
| ③ | `5-114a` · `5-114b` | `5-114`(기존) |
| ④ | `1-107` · `1-108` | A-18 · A-20 · A-21 · A-19 · A-23 |
| ④ | `1-109` · `1-110` | A-22 · A-29 · A-35 · A-31 |
| ④ | `4-38` · `4-39` | A-08 · A-25 · A-32 · A-26 · A-27 · A-34 |
| ④ | `2-48` · `2-49` · `2-50` · `2-51` | B-16 · B-22 · B-17 · B-23 · B-19 · B-20 · B-24 · B-15 |
| ④ | `3-22` · `3-23` | B-13 · B-14 · B-18 |
| ④ | `1-111` · `1-112` | F-K11 · F-K22 · F-K18 · F-K20 |
| ④ | `3-24` · `0-108` | F-K15 · F-K16 · F-K23 · F-K19 |
| ⑤ | `0-109` · `0-110` · `0-111` | G1-K19 · K21 · K04 · K13 · K12 · K06 · K08 · K07 |
| ⑤ | `0-112` · `0-113` · `4-40` | G1-K09 · K20 · K11 · K22 |
| ⑤ | `0-114` | G2-K3 · K4 · K5 |
| ⑤ | `8-19` · `8-20` · `8-21` | G2-K7 · G1-K16 · G2-K6 · K8 · K13 · K15 |
| ⑤ | `0-115` · `4-41` | G2-K12 · K11 |

Out of scope
- 보정 Minor 다섯(R-A-M2 · R-A-M5 · R-G-14 · R-G-15 · R-C-M1) — 사용자 범위 밖. 탐색으로 다섯 다 「남음」 을 확인했다(번들 89~)
- 잠재 둘(A-33 · A-37) — 지금 앱 진입로로 못 닿는다. 닿는 진입로가 생길 때
- `5-100`·`5-31`(순수본 재주입 — portfolio 세션) · D-a11yMultiline · D-piiLogSOE
- JSP 의 경로 변수 매핑·EL 든 URL 링크 · 클래스 단 `@RequestMapping` 을 조상만 가진 꼴 · iBATIS `useStatementNamespaces=true` · `MyBatisRules` 의 `<sqlMap>`
- 모르는 규칙 id 덮어쓰기 거절(옛 프로필이 막힌다) · `output.dir` 검사 · 줄마다 줄끝 지켜 쓰기
- 화면 변경은 `dev_tools_ext.js`(요약 한 줄)·`jsp_formatter_ext.js`(세 줄)·`table_builder_ext.js`(JSX 함수 셋)뿐. 새 화면·새 라우트 없음
- 마이그레이션 없음(V017 그대로) · 규칙 수 147 그대로

## 2. 현재 구조 요약

행마다 「사실」 줄이 지금 자리와 재현 꼴이다(4절). 여러 행에 걸치는 것만 여기 둔다.

데이터 흐름 — 프로그램 분석: `AnalyzeRunner.run` 이 파일을 읽어 `MapperIndex.scan`(매퍼 XML → 문장 열쇠 `ns.id` → `SqlTables.extract`·`SqlJoins.extract`) → JSP `JspLinks.extract` → `JpaIndex.scan` → `JavaGraph.scan`(컨트롤러 메서드 → `walk` → 문장 id) → 문장 id 를 색인에서 찾아 CRUD. SQL 글은 전부 `SqlTokens`(낱말 자르기) 하나를 지난다 — `SqlTables`·`SqlJoins`·`SqlLint` 가 같이 쓴다. 코드 검사: `RuleSet.load`(내장 → customRules → 프로필 덮어쓰기 → `validate`) → `RuleSet.Run.apply` → 정규식 규칙(`RegexRule` — 주석을 지운 글)·`JavaRules`(AST·`Taint`)·`MyBatisRules`(SAX) → `Ignores` 로 거른다. 코드 정리: `CleanupRunner` → `Cleanup.clean`(파일 하나) → 단계마다 `…Fix.apply`.

함정
- **self-check 기준선**(`golden/check/self-check.txt` — 줄에 값이 들어 있어 값이 바뀌면 그 줄이 「새 줄」 이다): 이 번들이 건드리는 메서드 가운데 기준선에 있는 것 — `JavaGraph` `walk(5)` 42·중첩 5 · `run(2)` 31 · `index(4)` 21 · `views(3)` 19 · `entityManager(4)` 19 · `querydsl(3)` 22 · `repoCall(8)` · **메서드 34** / `JpaIndex` `jpql(6)` 40 · `entity(1)` 18 · **메서드 38** / `MapperIndex.startElement(4)` 19 · `Statement(8)` / `SqlTables.extract(2)` 17 / `SqlJoins.aliases(1)` 19 / `SqlProbe.run(4)` 17 / `AnalyzeRunner.run(4)` 31·길이 116 / `RegexRule.cLike(2)` 27 / `MyBatisRules.startElement(4)` 17 / `RuleSet.load(2)` 28 · `Rule` `Def(11)` / `CleanupRunner` `apply(10)`·`preview(8)`·`writeReport(8)` / `Cleanup` 다섯 줄 / `GenModel.of(4)` 34·**길이 107** / `DtoGenerator.generate(2)` 31 / `Validation` `of(5)` 56·`parse(1)` 28 / `DdlReader` `item(3)` 30·`read(1)` 30·중첩 6 / `DdlGen` `table(1)` 16·`defaultOf(3)` 18·`type(2)` 24 / `InsertGen` `generate(4)` 20·`buildUpsert(5)` 24·`csvLiteral(5)` 31·`genVal(4)` 36·`Col(11)` / `GenRoutes.register(4)` 25 / `DeliverableService` `build(8)` 22·`writeGuide(11)` / `XlsxFiller.fill(4)` 25·중첩 5 / `StructureLint.run(3)` 50 / `TableXlsx.write(2)` 22 / `LogicalRoutes.register(4)` 23·길이 125·중첩 5 / `RowCounter.fill(3)` 16 / `LineDiff.diff(2)` 23 / `LocalFiles` `list(4)` 22·`resultRoot(5)` 22 / `LocalOnly.check(1)` 16 / `ProfileStore.saveCodeCheck(3)` 28 / `CheckRoutes.postRun(6)` 44·길이 120 / `SelfTest.body(0)` 25. 기준선 밖 메서드 상한은 복잡도 15·길이 100·중첩 4·인자 7, 클래스는 메서드 30·길이 1000
- 메서드 수가 상한에 가까운 클래스 — `SqlTables`·`DdlReader`·`DdlPull`·`InsertGen`·`Cleanup`·`Taint`·`DictStore`·`LocalFiles`. 새 도우미 자리는 4절 「공통 사다리」
- `Unresolved` 생성자는 `KINDS` 밖 종류를 던진다. `UnresolvedKindsTest.SHAPES` 는 `new Unresolved("종류"`·`note("종류"` 꼴 리터럴만 센다
- `SqlTokens.scan`(위치 든 `Tok`)은 `SqlLint` 가, `SqlTokens.tokens`(글자 목록)는 `SqlTables`·`SqlJoins` 가 쓴다 — 따옴표 꼴을 바꾸는 것은 `tokens` 쪽만
- `RegexRule.stripComments` 는 순수본 JS 와 글자까지 같은 꼴이고 `JspLinks`·`SqlTables`·`SqlJoins`·`Cleanup`·`Zones` 등 열여섯 자리가 부른다 — `cLike` 의 텍스트 블록(5-120)만 고치고, jsp 의 스크립트 주석은 새 `scriptComments` 로 따로 둔다
- `PureCodeCheckTest`(corpus)는 `fixtures/check/{common,jsp,tsx,security,a11y}` 를 순수본 JS 와 맞댄다 — 그 폴더에 텍스트 블록·스크립트 주석·`tb-ignore` 픽스처를 더하면 빨강. `mybatis/`·`java/`·`ignore/` 는 백엔드만
- `MapperIndexTest.golden`·`namespacesAreStatementKeyPrefixes` 는 `fixtures/analyze/mapper/` 폴더 전체를 본다 — 그 폴더에 파일을 더하지 않는다(인라인 시험)
- `CleanupCorpusTest.allSources` 는 `new Cleanup(…)` 을 직접 만든다 — 생성자 인자를 더하면 거기도 넣어야 corpus 수가 움직인다
- `QualitySqlTest.namesMustBeIdentifiers` 의 한 줄은 지금 B-15 버그 덕에 지나간다(`id(null)` 이 먼저 던진다) — `2-51` 뒤에는 스키마 글자 검사로 지나가야 한다
- `ProfileRoutesTest.conflictLeavesNoBackup` 은 따옴표 친 하위 키 `"groups":` 가 409 를 내는 것에 기대고 있다 — `0-114` 가 중복 키를 거절해도 그 길이 409 로 남아야 한다
- `LocalFiles.read` 를 부르는 폴더 작업은 전부 `IOException | LocalFiles.Refused` 를 같이 잡아 건너뛴다 — `read` 안의 새 `Refused` 는 안전하다. `Generator:112` 의 `writeResult` 는 try 밖이다
- `Profile` 은 `name` 이 null 인 꼴로도 만들어진다(`CheckRoutes.load`)
- `DbCorpusBase` 는 db·corpus 두 레인이고 `SqlProbe`·`CommentDdl.executableLines`·`InsertGen`·`SqlRunner` 를 실물 DB 로 돈다 — `V-67` 이 다시 잰다
- Bash heredoc 은 백슬래시를 벗긴다 — 정규식·역슬래시가 든 편집은 Edit·Write 로

## 3. 설계 결정 — 전부 [고정]

행의 「결정」 줄이 원본이다. 한데 모으면 —
1. **미해결 종류를 늘리지 않는다** — 연관 경로는 `jpql`, 조각 순환은 `missing`, 이름에 붙은 `${}` 는 `table`
2. **JPQL 연관 경로는 푼다**(엔티티 필드 형식으로). 별칭은 아는데 못 풀 때만 `jpql` 미해결(`6-49`)
3. **JSP 컨텍스트 접두**는 `…contextPath` 로 끝나는 EL · 정해 둔 이름 아홉 · 그 JSP 가 `c:set` 으로 정한 이름만. 그 밖 EL 접두는 지금처럼 `jspUrl` 미해결(`6-50`)
4. **`.do` 아닌 URL 은 컨트롤러 매핑과 글자까지 같을 때만** 링크(`6-51`). 버린 대안 — 단서(href·action) 뒤 `/…` 를 전부 링크로: 정적 파일이 섞인다
5. **`JavaGraph` 에 메서드를 더하지 않는다** — 새 로직은 `TypeBind`(이미 있는 도우미 클래스)와 `Acc` 필드로. `walk` 시그니처도 안 바꾼다(`6-54`~`6-56`)
6. **iBATIS 문장 열쇠는 id 그대로**, 치환 꼴은 MyBatis 꼴로 바꿔 담는다(`6-57`)
7. **접근자는 JavaBeans 규칙**(둘째 글자 대문자면 그대로) — 필드 이름·JSP 는 안 바꾼다(`7-36`). 버린 대안 — JSP 를 `${result.EMail}` 로: Spring 바인딩·Jackson 과 또 어긋난다
8. **규칙 덮어쓰기는 저장 전에 적재해 본다**. 이미 틀리게 저장된 프로필은 규칙 화면이 400 + 사유(`0-106`). 모르는 규칙 id 는 무시(지금대로)
9. **로그 SQL 짝은 앞머리 열쇠**. 가를 수 없으면 복원하되 경고(`4-37`)
10. **chunked 본문은 411 로 거절**(읽으며 세지 않는다)(`0-107`)
11. **`${}` 는 SQL 주석 안도 삽입 벡터** — `security.sqlDollar`·`mybatis.dollar` 둘 다 XML 주석 밖 전부(`5-119`, 사용자 (가))
12. **`cLike` 복잡도 27 을 안 넘긴다** — 가지 몸을 도우미로 빼 텍스트 블록 자리를 만든다(`5-120`)
13. **`tb-ignore` 의 주석 안 판정은 C 꼴 언어만**(jsp·xml·sql 은 한 파일에 주석 문법이 섞인다)(`5-121`)
14. **Taint 판정 로직은 안 고친다** — 메서드마다 이름을 잠깐 갈랐다가 되돌린다(`5-125`). 버린 대안 — 오염 집합을 선언 노드 열쇠로: `Taint`·`TaintLink`·`ConstLink`·`Sanitizers` 를 다 고쳐야 한다
15. **`5-114` 의 「하나로 정해짐」** = 형식은 같은 파일 → 단일 import → 같은 패키지 차례로 하나, 메서드는 이름·인자 수가 같은 것이 정확히 하나(없으면 `extends` 조상으로 다섯 단계). 수신자는 이름·`this.필드`·맨 호출만(이어진 호출·정적 호출·JDK 메서드는 건너뜀). 판정(`ValueAst`)은 안 바꾼다
16. **산출물 알림은 `skipped` 한 통로**(`2-47`·`2-48`·`2-49`). 버린 대안 — `Result` 에 `warnings` 필드: 화면·CLI·작성안내 셋을 다 고쳐야 한다
17. **거절로 정한 것** — CSV 머리 중복 · PK 없는 upsert(`4-39`) · 표 이름 빈 COMMENT 행·MSSQL 빈 소유자(`3-22`) · 프로필의 별칭·병합 키·중복 키·둘째 문서(`0-114`) · 경로 글자 든 프로필 이름(`0-112`) · 별 넷 넘는 글롭(`0-110`) · UTF-16 파일(`0-109`) · `;` 든 data 폴더(`0-108`) · 모르는 `--docs` 번호(`8-21`)
18. **동작을 안 바꾸고 알리기만 하는 것** — 겹치는 양식 머리(뒤 칸 그대로)(`2-49`) · 없는 날짜(값 그대로)(`4-39`) · 행 수 상한(`4-38`) · 자동증가 컬럼(SQL 그대로)(`4-38`)
19. **`check` 의 못 읽은 파일은 알리기만**(요약·`[경고]`) — 끝 코드는 안 바꾼다(`8-21`, 사용자 (가)). 버린 대안 — `--fail-on` 이면 실패: 10MB 넘는 데이터 파일이 든 사업의 형상 훅이 늘 빨개진다
20. **CLOB·XML 값은 100만 자까지**(`1-112`)

## 4. 실행 스텝

행마다 끝은 CLAUDE.md 「Task 끝」 순서(분할표 행·이력 → `bash scripts/verify.sh` → `git commit` 따로 → `task-done`). `src/main/java` 를 고치는 행은 시험을 먼저 쓰고 RED 를 본 뒤 고친다. 커밋 제목 `fix: <번호> <무엇>`(시험만이면 `test:`, 문서만이면 `docs:`). 줄 번호는 `0faa904` 기준 — 앞 행이 밀면 메서드 이름으로 찾는다.

공통 사다리
- `SelfCheckTest.mainMatchesBaseline` 빨강 —
  - **오른 값·새 `metric.complexity`·`methodLength`·`nesting`·`params` 줄**: 분기를 도우미로 뺀다. 도우미 자리는 ① 그 메서드가 든 **중첩 클래스**(`Scan`·`Handler`·`Gen` 처럼 이미 있으면 거기) ② 같은 클래스의 `private static` ③ 그래서 `metric.classMethods` 새 줄이 생기면(메서드 30 넘음) 같은 패키지의 새 `final class <클래스>Parts`(package-private, `private` 생성자, static 만)로 옮긴다. `JavaGraph`·`JpaIndex`·`Definitions`·`AlterGen.Run` 은 이미 30 을 넘어 기준선에 있다 — **메서드를 하나도 더하지 않는다**(행에 적힌 도우미 자리를 그대로 쓴다)
  - **내린 값·사라진 줄**: `bash scripts/mvn.sh -q test -Dtest=SelfCheckTest -Dgolden.update=true` 로 갱신하고 diff 가 그 줄뿐인지 본다
  - 오르는 것·새 줄을 받는 행은 없다
- `golden/` diff — 그 행의 「의도된 골든 변화」 에 적힌 것만 갱신. 그 밖은 고침이 넓다 → 고친다
- 새 시험의 시간 단언은 `assertTimeoutPreemptively(Duration.ofSeconds(5), …)`. 시험 글에 실제 사업 이름·SQL 을 안 쓴다(`T88_*`·`TB_X`)
- 미해결 종류(`Unresolved.KINDS` 스물)는 늘리지 않는다 — 이 번들의 새 미해결은 전부 기존 종류(`jpql`·`table`·`missing`·`jspUrl`)다. `UnresolvedKindsTest` 의 20 은 그대로
- PMD·SpotBugs·ErrorProne 은 빠른 `verify.sh` 가 안 본다 — `V-67a` 가 본다. 새 짝이 나오면 코드로 고친다(기준선에 안 더한다)

### S0 — 설계 문서 · 행 · 번들 표 (**실행 세션이 했다**)
- 설계 세션(Fable)이 플랜 승인 뒤: ① 가지 `work/2026-11-21` 그대로(HEAD `0faa904` = main `f52a415` + 닫기 하나 — 같은 PR 에 실린다) ② 이 플랜 전체를 `design/73-bundle88.md` 로 ③ `PROGRESS.md` 「번들 87 닫기」 줄의 `(이 커밋)` → `0faa904` ④ `PLAN.md` 15장 — 「번들 88~」 행을 「번들 88」 행(차례는 아래 · 설계 칸에 「실행 Opus high · 마무리 독립 리뷰 Fable xhigh」 · 필요 조건 「표본 폴더 · node · Docker · 여유 RAM 15GB」 · **머지 위임** · 시간 컷 없음)과 「번들 89~」 행(보정 Minor 다섯 R-A-M2·R-A-M5·R-G-14·R-G-15·R-C-M1 · 잠재 A-33·A-37 · `5-100`·`5-31` · 조건부 `5-14`·`5-15`·`6-9` · 번들 88 이 `wip/` 로 뺀 행)으로 ⑤ 16장 — 기존 행 여섯(`7-35`·`1-106`·`6-48`·`5-119`·`2-47`·`0-105`)의 사실 칸 끝에 ` · **처방**: 설계 73 §4`(`5-119` 는 「(가) 되돌림 — 사용자 2026-11-21」 도) · `5-114` 행 끝에 ` · **처방**: 설계 73 §4 — 5-114a·5-114b((가) 같은 소스 루트 — 사용자 2026-11-21)` · `5-100` 행에서 「`security.sqlDollar` 의 SQL 주석 떼기(…)」 를 지우고 「텍스트 블록·`<script>` 주석·문자열 안 `tb-ignore`(`5-120`·`5-121` — 백엔드만 고쳤다, 순수본 JS 는 옛 동작)」 을 더한다 · 표 끝에 새 행(아래 표의 쉰여섯 줄 + `5-114a`·`5-114b`, 꼴 `| <번호> | <청크> | **설계 73 §4 가 원본**. <요지>(<키>). **축**: … **강제 지점**: … | <선행> | 대기 |`) ⑥ `PROGRESS.md` 이력 한 줄 + 「현재 상태」(진행중 = 번들 88 대기) ⑦ `bash scripts/verify.sh`(docs 레인) → `git commit`(따로) `docs: 설계 73 — 번들 88 행·번들 표`
- 실행 세션의 확인: `git log --oneline -1` 이 그 커밋 · `git status` 깨끗 · `ls design/73-bundle88.md` · 8절 「첫 스텝 전에」

| # | 청크 | 축 | 강제 지점 | 선행 |
|---|---|---|---|---|
| 6-49 | JpaIndex — 예약어 엔티티 · 연관 경로(R-C-I2) | 분석(corpus) | `JpaIndexTest.reservedWordEntityAndPathJoin` | S0 |
| 6-50 | JspLinks — 컨텍스트 접두 · 스크립트 주석 · 제곱 시간(R-C-I3 · C-JSP-QUAD) | 〃 | `JspLinksTest.contextPrefixScriptCommentsAndTime` | S0 |
| 6-51 | JspLinks — `.do` 아닌 URL(R-C-I3) | 〃 | `JspLinksTest.nonDoUrlsMatchMappings` | 6-50 |
| 6-52 | JavaGraph — 구현 색인 FQCN(R-C-I6) | 〃 | `JavaGraphTest.sameNameServicesInTwoModules` | S0 |
| 6-53 | JavaGraph — 문장 실행 호출 꼴(R-C-I7 ①② · R-C-I8) | 〃 | `JavaGraphTest.sessionCallShapes` | 6-52 |
| 6-54 | JavaGraph — 조상의 매핑 메서드(R-C-I7 ③) | 〃 | `JavaGraphTest.inheritedMappingIsAProgram` | 6-53 |
| 6-55 | JavaGraph — 수신자(R-C-I7 ④) | 〃 | `JavaGraphTest.templateMethodReachesOverride` | 6-54 |
| 6-56 | JavaGraph — 필드 타입 인자(R-C-I7 ⑤) | 〃 | `JavaGraphTest.fieldTypeArgumentBindsTheDao` | 6-55 |
| 6-57 | MapperIndex — iBATIS sqlMap(R-C-I8) | 〃 | `MapperIndexTest.ibatisSqlMap` | S0 |
| 7-36 | 접근자 이름(R-A-I11) | 생성기(db) | `DtoGeneratorTest.accessorFollowsJavaBeans` · `GenTemplatesTest.singleLetterTokenAccessor` | S0 |
| 0-106 | 규칙 덮어쓰기 저장 전 검증(R-G-6 · G1-K10) | 프로필 | `ProfileRoutesTest.badRuleRegexIsRefusedBeforeSave`·`unknownKeyProfileIs400WithoutBackup`·`brokenSavedRuleIs400OnRead` | S0 |
| 4-37 | LogSql — 앞머리 짝 · 시간(R-G-9 · G2-K9 · G1-K18) | 관례(corpus) | `LogSqlTest.interleavedThreadsPairByPrefix`·`untypedParamsAreLinear` | S0 |
| 0-107 | 본문 길이 — chunked 411 · 상한(G1-K15 · G1-K14) | 5-12 | `LocalOnlyTest.chunkedBodyIsRefused`·`oversizedLengthIs413` | S0 |
| 6-58 | SqlTokens — 따옴표 예약어 · 붙은 `${}` · `#` 주석(C-TBL-KEYWORD·DOLLAR·HASH) | 분석(corpus) | `SqlTokensTest.quotedKeywordBindSuffixHashComment` · `SqlTablesTest.golden`(넷) | 6-48 |
| 6-59 | SqlTables — 문장마다 WITH · 여러 표 UPDATE(C-TBL-CTE·MULTIUPD) | 〃 | `SqlTablesTest.golden`(셋) | 6-58 |
| 6-60 | SqlJoins — SET 대입(C-JOIN-SET) | 〃 | `SqlJoinsTest.shapes`(둘) | S0 |
| 6-61 | MapperIndex — 조각 순환 · 취소(C-MAP-INCLUDE) | 〃 | `MapperIndexTest.selfIncludeIsCutAndNoted`·`scanHonoursTick` | 6-57 |
| 6-62 | SqlProbe — 끝 정규식 · 끝 `;` · 같은 이름 컬럼(C-PROBE-REGEX·SEMI·DUPCOL) | 분석(db) | `SqlProbeTest.tailRegexIsLinear` 등 | 6-61 |
| 6-63 | `databaseId` 변형(C-PROBE-DBID) | 〃 | `SqlProbeTest.databaseIdVariantIsPicked` | 6-62 |
| 5-120 | RegexRule — 텍스트 블록 · 스크립트 주석(D-textBlock·D-jspScriptComment) | 검사(corpus) | `CheckRulesTest.textBlockDoesNotOpenAComment`·`commentedScriptLinesAreNotFlagged` | 6-50·5-119 |
| 5-121 | Ignores — 문자열 안 표식(D-ignoreInString) | 〃 | `CheckRulesTest.ignoreMarkers` | 5-120 |
| 5-122 | MyBatisRules — dupId · 문장 끝 주석 떼기 · 괄호 밖 WHERE(D-dupId·D-dollarFragment·D-noWhereSubquery) | 〃 | `MyBatisRulesTest.fixturesGolden` | 5-119 |
| 5-123 | 파싱 실패 수(D-parseErrCount) | 〃 | `CheckRunnerTest.parseErrorCountIgnoresRuleSwitchAndMarkers` | 5-122 |
| 5-124 | 덮어쓰기 꼴 검증(D-overrideValidate) | 〃 | `CheckRulesTest.malformedOverridesAreRefused` | 0-106 |
| 5-125 | Taint — 지역 변수 범위(D-taintScope) | 〃 | `TaintTest.sameNameInSiblingScopesDoesNotLeak` | 5-123 |
| 1-107 | DtoGenerator — `\u` · record 금지 이름 · 빈 이름(A-18·A-20·A-21) | 생성기(db) | `DtoGeneratorTest.hostileNamesAndCommentsCompile` | 7-36 |
| 1-108 | 큰 경계 · 패키지 예약어(A-19·A-23) | 〃 | `ValidationTest.wideBoundsCompile` · `GenRoutesTest.badInput` | 1-107 |
| 1-109 | DdlReader — 크기 · 기본값 식 · 따옴표 이름 · 자동증가(A-22·A-29·A-35·A-08) | 〃 | `DdlReaderTest.sizesDefaultsQuotedNamesAuto` | 1-106 |
| 1-110 | 타입 매핑(A-31) | 〃 | `DtoGeneratorTest.typeMapping` | 1-109 |
| 4-38 | InsertGen 생성 — 자동증가 · 행 수 · CHECK IN(A-08·A-25·A-32) | 〃 | `InsertGenTest.autoColumnsAreWarned`·`rowsAreClampedWithWarning` | 1-109 |
| 4-39 | InsertGen CSV — 머리 중복 · 없는 날짜 · PK 없는 upsert(A-26·A-27·A-34) | 〃 | `InsertGenTest.duplicateHeaderIsRefused`·`upsertNeedsKeyColumns`·`impossibleDateIsWarned` | 4-38 |
| 2-48 | 산출물 — 매핑 미리 · 모르는 번호(B-16·B-22) | 산출물(corpus) | `DeliverableServiceTest.unknownDocNumberIsReported`·`missingMappingFailsBeforeWriting` | 2-47 |
| 2-49 | 양식 — 겹치는 머리 · 병합 잇기(B-17·B-23) | 〃 | `XlsxFillerTest.duplicateHeaderIsReported`·`dataRowMergesAreRepeated` | 2-47 |
| 2-50 | 순환 재귀 · 태그 정규식 · 매핑 null(B-19·B-20·B-24) | 〃 | `StructureLintTest.longFkChainDoesNotOverflow` 등 | 2-49 |
| 2-51 | 품질 SQL — 표 없는 nopk(B-15) | 산출물(db) | `QualitySqlTest.nopkNeedsNoTable` | S0 |
| 3-22 | COMMENT DDL — 빈 소유자·표 · 예약어(B-13·B-14) | 〃 | `CommentDdlTest.reservedAndBlankOwner`·`blankTableIsRefused` | S0 |
| 3-23 | 마스킹 종류(B-18) | 〃 | `MaskingTest.passportDriverTelNo` | 3-22 |
| 1-111 | 거절된 COUNT · Oracle 자동 NOT NULL(F-K11·F-K22) | 스냅샷(db) | `SnapshotServiceTest.refusedCountIsItsOwnWarning` · `VendorFallbackTest` | S0 |
| 1-112 | SqlRunner — CLOB 상한 · 배열·XML(F-K18·F-K20) | 〃 | `SqlRunnerTest.longClobIsCut`·`arraysAndXmlAreValues` | S0 |
| 3-24 | 사전 — CSV 수식 · kind · 와일드카드(F-K15·F-K16·F-K23) | 사전(corpus) | `DictRoutesTest.exportEscapesFormulasAndChecksKind` · `DictStoreTest.searchEscapesWildcards` | S0 |
| 0-108 | data 폴더 경로의 `;`(F-K19) | 관례 | `DbTest.semicolonInPathIsRefused` | S0 |
| 0-109 | fs 읽기 — 잠긴 파일 · UTF-16(G1-K19·K21) | fs(corpus) | `LocalFilesTest.utf16IsRefused`·`lockedFileIsRefused` | S0 |
| 0-110 | fs 목록 — 글롭 · 정션·금지 폴더(G1-K04·K13·K12) | 〃 | `LocalFilesTest.badGlobIsRefused`·`listSkipsForbiddenAndJunctions` | 0-109 |
| 0-111 | fs 쓰기 자리(G1-K06·K08·K07) | 절대 규칙 7 | `LocalFilesTest.writeResultRefusesEscape`·`refusals` | 0-110 |
| 0-112 | 프로필 — 이름 · 저장 원자성(G1-K09·K20) | 프로필 | `ProfileStoreTest.nameMustBeAFolderName` · `ProfileRoutesTest.concurrentSavesAndReads` | 0-106 |
| 0-113 | 작업 목록 정리(G1-K11) | 관례 | `JobManagerTest.pruneKeepsRecentFinishedJobs` | S0 |
| 4-40 | jsp 일괄 — 섞인 줄끝(G1-K22) | 절대 규칙 7 | `SmokeHtmlUnitTest.jspFormatterFolderBatch` | 0-111 |
| 0-114 | 프로필 YAML — 별칭·중복 키·둘째 문서·머리 꼴(G2-K3·K4·K5) | 프로필 | `ProfileStoreTest.aliasesMergeKeysDuplicatesAndSecondDocumentAreRefused` | 0-112 |
| 8-19 | CLI — 입력 오류 `[오류]`(G2-K7·G1-K16) | CLI | `BatchCliTest.failures` · `MainTest.serveRefusesBadPort` | 0-108 |
| 8-20 | CLI — 낡은 활성 프로필 · selftest(G2-K6·K8) | 〃 | `BatchCliTest.staleActiveProfileDoesNotBlock` · `SelfTestCliTest.unreadableProfileFails` | 8-19 |
| 8-21 | CLI — 못 읽은 파일 알림 · 모르는 문서 번호(G2-K13·K15) | 〃 | `CodeCliTest.unreadFilesAreReported` · `DocCliTest.unknownDocNumberIsRefused` | 8-20·2-48 |
| 0-115 | 예시 프로필 주석(G2-K12) | 문서 | 없음(주석) | 0-114 |
| 4-41 | 표 편집기 JSX(G2-K11) | 화면 | `SmokeHtmlUnitTest.tableBuilderJsxEdgeShapes` | S0 |
| V-67a | 정적 분석 · corpus 골든 | 4장 | `verify.sh --corpus` | 코드 행 전부 |
| R-34 | 손 판정 · 처분 표 | 4장 | compare ㊲ 절 | V-67a |
| V-67 | `--db` | 4장 | `verify.sh --db` | R-34 |

차례 — `S0→6-49→6-50→6-51→6-52→6-53→6-54→6-55→6-56→6-57→7-36→0-106→4-37→7-35→1-106→6-48→5-119→2-47→0-105→0-107→6-58→6-59→6-60→6-61→6-62→6-63→5-120→5-121→5-122→5-123→5-124→5-125→5-114a→5-114b→1-107→1-108→1-109→1-110→4-38→4-39→2-48→2-49→2-50→2-51→3-22→3-23→1-111→1-112→3-24→0-108→0-109→0-110→0-111→0-112→0-113→4-40→0-114→8-19→8-20→8-21→0-115→4-41→V-67a→R-34→V-67→마무리`

### ① 반드시

### 6-49 — JpaIndex: 예약어 이름 엔티티 · 연관 경로 조인 (R-C-I2)
- 대상: `core/analyze/JpaIndex.java` · 새 `core/analyze/JpqlPaths.java` · `JpaIndexTest`
- 사실: `jpql:472-513` — 엔티티 자리 낱말이 `JPQL_STOP` 이면 `break`(`select o from Order o` → 표 0·미해결 0). `JOIN p.items i` 는 `path` 라 아무것도 안 한다. `jpql(6)` 복잡도 40·`JpaIndex` 메서드 38 은 기준선 — 분기도 메서드도 못 더한다
- 변경:
  1. `JpaIndex` 필드 `private Set<String> jpqlStop = JPQL_STOP;`. `run` 의 엔티티 등록 루프(`byEntityName.computeIfAbsent(…)`, `:229-236`) 바로 뒤에 한 문장 —
     ```java
     // R-C-I2 — Order·Group·Set 처럼 예약어와 겹치는 엔티티 이름은 엔티티 자리에서 멈춤말이 아니다
     jpqlStop = JPQL_STOP.stream().filter(w -> byEntityName.keySet().stream().noneMatch(w::equalsIgnoreCase)).collect(java.util.stream.Collectors.toUnmodifiableSet());
     ```
  2. `jpql` 의 **엔티티 자리**(`:485` `if ("(".equals(id) || JPQL_STOP.contains(id.toUpperCase(Locale.ROOT)))`)만 `jpqlStop.contains(…)` 로. 별칭 자리(`:505`)의 `JPQL_STOP` 은 그대로
  3. `jpql` 의 for 루프가 끝난 바로 뒤(`List<SqlTables.Ref> list = …` 앞)에 한 문장 —
     ```java
     JpqlPaths.resolve(t, n -> byEntity(n, from), e -> types.get(e.fqcn()).decl, refs, id -> out.add(new Unresolved("jpql", file, line, where + ":" + id)));
     ```
     (`refs` 는 그 메서드의 `Map<String, EnumSet<SqlTables.Crud>>` 지역 변수, `t` 는 낱말 목록)
  4. 새 클래스
     ```java
     /** R-C-I2 — JPQL 의 연관 경로 조인({@code JOIN [FETCH] 별칭.속성 [[AS] 별칭]})을 엔티티 필드 형식으로 푼다. JpaIndex 가 메서드 상한이라 따로 둔다 */
     final class JpqlPaths {
         /**
          * @param t      JPQL 낱말(JpaIndex.JPQL_TOKEN 이 자른 것 — 식별자·점 이은 이름·괄호·쉼표)
          * @param byName 엔티티 이름 → 엔티티(참조하는 쪽으로 가른 것). 하나일 때만 쓴다
          * @param decl   엔티티 → 클래스 선언
          * @param refs   표 → 글자. 푼 경로의 대상 표에 R 을 더한다
          * @param miss   별칭은 아는데 속성·대상 엔티티를 못 찾은 경로(「별칭.속성」 글 그대로)
          */
         static void resolve(List<String> t, Function<String, List<JpaIndex.Entity>> byName, Function<JpaIndex.Entity, ClassOrInterfaceDeclaration> decl,
                 Map<String, EnumSet<SqlTables.Crud>> refs, Consumer<String> miss)
     }
     ```
     알고리즘(낱말을 앞에서 한 번 훑는다, 별칭 → 엔티티 맵 하나):
     - `FROM`·`JOIN`(바로 뒤 `FETCH` 는 건너뜀)·그리고 FROM 목록 안 쉼표 뒤 낱말 `id` 를 본다
     - `id` 에 점이 없거나 마지막 마디가 대문자로 시작(FQCN)하면 엔티티 이름(마지막 마디) — `byName` 이 **정확히 하나**면 뒤 낱말(`AS` 건너뜀)이 식별자이고 `JpaIndex.JPQL_STOP` 에 없을 때 별칭으로 맵에 넣는다
     - 그 밖(`별칭.속성` — 마디 둘, 첫 마디가 맵에 있다): `decl` 의 `getFieldByName(속성)` 형식에서 대상 이름 = 타입 인자가 있으면 **마지막 타입 인자**(`List<Item>`·`Map<K, Item>`), 없으면 형식 이름. `byName(대상)` 이 정확히 하나면 `refs` 의 그 표에 `R` 을 더하고, 뒤 별칭을 맵에 넣는다(이어지는 `JOIN i.product p` 가 풀리게). 필드가 없거나 대상이 하나가 아니면 `miss.accept(id)`
     - 첫 마디가 맵에 없는 경로·마디 셋 이상(`a.b.c`)은 아무것도 안 한다(FROM 엔티티가 이미 `jpql` 미해결로 남았거나 임베디드 경로)
     - 메서드마다 복잡도 15 이하로 쪼갠다(`resolve` · `entityAt` · `pathAt` 셋쯤). `JpaIndex.JPQL_STOP` 은 `private` 를 떼 `JpqlPaths` 가 별칭 판정에 쓴다
  5. `jpql` javadoc 의 「연관 경로는 대상 엔티티를 몰라 건너뛴다」 를 「연관 경로는 `JpqlPaths` 가 필드 형식으로 푼다 — 못 풀면 `jpql`」 로
- 시험(먼저): `JpaIndexTest` 새 `reservedWordEntityAndPathJoin` — `deepFileIsSkipped` 의 인라인 `Source` 꼴(파일마다 `package p;` + `import javax.persistence.*;`)로 엔티티 셋(`@Entity class Order { Long id; @OneToMany List<Item> items; Coupon coupon; }` · `@Entity @Table(name = "T88_ITEM") class Item { Long id; }` · 엔티티 아닌 `class Coupon {}`) 을 `JpaIndex.scan` 한 뒤 `x.jpql(…, new JpaIndex.From("p", Map.of(), ""), "f", 1, "w", out)` 로 —
  - `"select o from Order o where o.id = 1"` → 표 하나(Order 의 표), `out` 빔
  - `"select o from Order o join o.items i where i.id = 1"` → 표 둘(Order 표 · `T88_ITEM`), `out` 빔
  - `"select o from Order o join fetch o.coupon c"` → 표 하나, `out` 에 `jpql` 한 건 detail `w:o.coupon`
  - `"select o from Order o order by o.id"` → 표 하나(끝 `order by` 는 그대로 멈춤말)
  RED: 첫 줄이 표 0
- 의도된 골든 변화: `golden/analyze/jpa-index.json`·`jpa-graph.json` — `ProductRepository.byCategory`(`join p.categories c`)에 대상 표가 **더해지거나** `jpql` 미해결 한 줄이 더해지는 것만. corpus(`analyze-shopizer*`·`analyze-egov-msa*`)는 `V-67a`
- 검증: `bash scripts/mvn.sh -q test -Dtest='JpaIndexTest,JavaGraphTest,UnresolvedKindsTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus(`core/analyze`)
- 사다리: `types.get(e.fqcn())` 가 null 인 엔티티가 있으면(색인 밖) `decl` 람다를 `e -> Optional.ofNullable(types.get(e.fqcn())).map(c -> c.decl).orElse(null)` 로 바꾸고 `JpqlPaths` 가 null 선언을 「필드 없음」 으로 다룬다

### 6-50 — JspLinks: 컨텍스트 접두 · 스크립트 주석 · 제곱 시간 (R-C-I3 일부 · C-JSP-QUAD)
- 대상: `core/analyze/JspLinks.java` · `core/check/RegexRule.java`(새 public static 하나) · `JspLinksTest`
- 사실: `extract:111` — `token.startsWith(CONTEXT_PATH)` 정확히 그 글자만 접두로 뗀다(`${ctx}/a.do`·중괄호 안 공백은 `jspUrl` 미해결). `extract:90` — `<%-- --%>`·`<!-- -->` 만 지워 `<script>` 안 `// location.href='/old.do'` 가 링크로 센다. `start:140` 의 `lastIndexOf("${", j)` 와 `line:154` 가 글 머리까지 훑는다(제곱)
- 변경:
  1. `RegexRule` 에 —
     ```java
     private static final Pattern SCRIPT_BODY = Pattern.compile("(?is)(<script\\b[^>]*+>)(.*?)(</script\\s*+>)");

     /** {@code <script>} 몸의 {@code //}·{@code /* *}{@code /} 주석을 같은 길이 빈칸으로(줄바꿈은 남긴다) — 따옴표·백틱 글 안은 그대로. jsp·html 꼴에서 쓴다(D-jspScriptComment · R-C-I3) */
     public static String scriptComments(String text) {
         Matcher m = SCRIPT_BODY.matcher(text);
         StringBuilder sb = new StringBuilder(text.length());
         int from = 0;
         while (m.find()) {
             sb.append(text, from, m.start(2)).append(cLike(m.group(2), true));
             from = m.end(2);
         }
         return sb.append(text, from, text.length()).toString();
     }
     ```
     (`cLike` 는 길이를 지킨다. `stripComments` 는 안 고친다 — 순수본과 같은 꼴)
  2. `JspLinks.extract:90` → `String text = RegexRule.scriptComments(RegexRule.stripComments(jsp.text() == null ? "" : jsp.text(), "jsp"));`
  3. 컨텍스트 접두 — `:111` 의 `String url = token.startsWith(CONTEXT_PATH) ? … : token;` 을 `String url = withoutContext(token, ctxNames);` 로. `ctxNames` 는 `extract` 머리에서 한 번:
     ```java
     /** 컨텍스트 경로를 담는 변수 이름 — 흔한 이름 + 이 JSP 가 c:set 으로 contextPath 를 넣은 이름 */
     private static final Set<String> CTX_NAMES = Set.of("ctx", "cp", "root", "contextpath", "ctxpath", "rootpath", "contextroot", "basepath", "webroot");
     private static final Pattern CTX_SET = Pattern.compile("<c:set\\s[^>]*?var\\s*=\\s*[\"']([\\w.]+)[\"'][^>]*?contextPath[^>]*>", Pattern.CASE_INSENSITIVE);
     private static final Pattern CTX_HEAD = Pattern.compile("^\\$\\{\\s*([\\w.]+)\\s*}(?=/)");

     /** 머리의 EL 하나가 컨텍스트 경로면 뗀다 — {@code ${…contextPath}}(공백 허용) · CTX_NAMES · c:set 으로 정한 이름. 아니면 그대로 */
     private static String withoutContext(String token, Set<String> names) {
         Matcher m = CTX_HEAD.matcher(token);
         if (!m.find()) {
             return token;
         }
         String el = m.group(1).toLowerCase(Locale.ROOT);
         return el.endsWith("contextpath") || names.contains(el) ? token.substring(m.end()) : token;
     }
     ```
     `ctxNames` = `CTX_NAMES` + `CTX_SET` 이 `text` 에서 찾은 이름(소문자). 상수 `CONTEXT_PATH` 는 지운다(쓰는 곳이 없어진다)
  4. `start` — `lastIndexOf("${", j)` 대신 `j - 1` 부터 거슬러 `$` `{` 를 찾되 그 사이에 다른 `}` 를 만나면 멈춘다(EL 안에는 `}` 가 없다):
     ```java
     if (c == '}') {
         int open = elOpen(text, j);   // j 의 } 를 닫는 ${ 의 위치, 없으면 -1
         if (open < 0) {
             break;
         }
         j = open - 1;
     }
     ```
     ```java
     private static int elOpen(String text, int close) {
         for (int k = close - 1; k > 0; k--) {
             char c = text.charAt(k);
             if (c == '}' || c == '\n') {
                 return -1;
             }
             if (c == '{' && text.charAt(k - 1) == '$') {
                 return k - 1;
             }
         }
         return -1;
     }
     ```
  5. `line(text, pos)` — `extract` 가 줄 시작 위치를 한 번 모아(`int[] starts`) `java.util.Arrays.binarySearch` 로 줄을 구한다(도우미 `lineOf(int[] starts, int pos)`). `line(String, int)` 는 지운다
- 시험(먼저): `JspLinksTest` 새 `contextPrefixScriptCommentsAndTime` —
  - `"<a href=\"${ctx}/a.do\">x</a>"` → 링크 `/a.do`(kind `link`), 미해결 0
  - `"<c:set var=\"home\" value=\"${pageContext.request.contextPath}\"/>\n<a href=\"${home}/b.do\">x</a>"` → `/b.do`
  - `"<a href=\"${ pageContext.request.contextPath }/c.do\">x</a>"` → `/c.do`
  - `"<a href=\"${board.url}/d.do\">x</a>"` → 링크 0, `jspUrl` 한 건(지금 동작 그대로)
  - `"<script>\n// location.href='/old/removed.do';\n/* window.open('/old/pop.do') */\nlocation.href='/live.do';\n</script>"` → 링크 `/live.do` 하나
  - `"}".repeat(100_000) + "/x.do"` 와 `"${a}/x.do\n".repeat(20_000)` 가 5초 안
  RED: 첫 줄이 `jspUrl` 미해결 · 스크립트 사례가 링크 셋
- 의도된 골든 변화: `golden/analyze/jsp-links.json` — `ElOnly.jsp` 의 `${ctx}/a.do` 가 미해결에서 링크 `/a.do` 로 **옮겨가는 것만**
- 검증: `bash scripts/mvn.sh -q test -Dtest='JspLinksTest,CheckRulesTest,AnalyzeStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus(`core/analyze`·`core/check`)

### 6-51 — JspLinks: `.do` 아닌 URL (R-C-I3)
- 대상: `core/analyze/JspLinks.java` · `core/analyze/AnalyzeRunner.java` · `JspLinksTest`
- 결정: `.do` 로 안 끝나는 URL 은 **컨트롤러 매핑 URL 과 글자까지 같은 것만** 링크로 센다. 맞는 매핑이 없는 `/…` 글(정적 파일·바깥 주소)은 지금처럼 버린다(미해결도 아님)
- 변경:
  1. `JspLinks.extract(Source jsp)` 는 `extract(jsp, Set.of())` 로 넘기고, 새 `public static Result extract(Source jsp, Set<String> programUrls)` 가 몸을 갖는다. `.do` 훑기가 끝난 뒤 `programUrls` 가 비지 않았으면 `matched(text, ctxNames, programUrls, urls)` 를 부른다
     ```java
     /** 따옴표 글 한 덩이 — 여는 따옴표 뒤부터 같은 따옴표·줄 끝 앞까지(200자 상한) */
     private static final Pattern QUOTED = Pattern.compile("([\"'])([^\"'\\r\\n]{1,200})\\1");

     /** 6-51 — 따옴표 글이 (컨텍스트 접두를 떼고 ?·# 앞까지 잘라) 컨트롤러 매핑 URL 과 같으면 링크. .do 로 끝나는 것은 앞 훑기가 이미 봤다 */
     private static void matched(String text, Set<String> ctxNames, Set<String> programUrls, Map<String, Set<String>> urls)
     ```
     - 후보 = `withoutContext(group(2), ctxNames)` 를 첫 `?`·`#` 앞에서 자른 것. `/` 로 시작하고 `.do` 로 안 끝나고 `programUrls.contains(후보)` 일 때만 `urls.computeIfAbsent(후보, …).add(kind(text, m.start(2)).code())`
  2. `public static Set<String> programUrls(List<JavaGraph.Program> programs)` — `Program.url()` 가운데 `/` 로 시작하고 `{`·`*` 가 없는 것
  3. `AnalyzeRunner.run` — JSP 링크 루프(`:149-159` 의 맵 셋 선언과 `for (Source s : jsp)`)를 **통째로 `JavaGraph.Graph graph = JavaGraph.scan(…)` 줄 바로 뒤로 옮기고**(진행 글 `ctx.progress(85, "JSP 링크")` 줄은 제자리), 루프 안 `JspLinks.extract(s)` 를 `JspLinks.extract(s, urls)` 로, 루프 앞에 `Set<String> urls = JspLinks.programUrls(graph.programs());` 한 줄. 분기를 더하지 않는다(`run(4)` 복잡도 31 = 기준선)
- 시험(먼저): `JspLinksTest.nonDoUrlsMatchMappings` — `extract(new Source("a.jsp", "<a href=\"/user/list\">l</a><a href=\"${ctx}/user/view?id=1\">v</a><img src=\"/img/logo\"/><form action='/user/save'>", …), Set.of("/user/list", "/user/view", "/user/save"))` → 링크 셋(`/user/list` link · `/user/view` link · `/user/save` form), `/img/logo` 없음. 같은 글을 `extract(source)`(한 인자)로 → 링크 0. RED: 두 인자 메서드 없음(컴파일)
- 의도된 골든 변화: 빠른 레인 없음(`AnalyzeStoreTest` 픽스처에 `.do` 아닌 매핑이 있으면 그 링크가 **더해지는 것만**). corpus 는 `V-67a`
- 검증: `bash scripts/mvn.sh -q test -Dtest='JspLinksTest,AnalyzeStoreTest,AnalyzeStoreTest,AnalyzeRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-50(`withoutContext`·`ctxNames`). 레인 corpus
- 한계(설계 문서에만): 경로 변수 매핑(`/user/{id}`)·EL 이 든 URL 은 안 맞춘다

### 6-52 — JavaGraph: 구현 클래스 색인을 FQCN 으로 (R-C-I6)
- 대상: `core/analyze/JavaGraph.java` · `JavaGraphTest`
- 사실: `implementors:149` 열쇠가 인터페이스 **단순 이름**. `app.a.UserService` 를 풀어도 `resolve:620` 이 `implementors.get("UserService")` 로 `app.a`·`app.b` 구현을 다 가져와 남의 모듈 표가 CRUD 에 든다
- 변경(메서드를 더하지 않는다):
  - `interfacesOf:368-385` — 돌려주는 것을 「색인에 있는 인터페이스의 FQCN」 으로:
    ```java
    for (String i : c.interfaces) {
        for (Cls ic : lookup(c, i)) {
            out.add(ic.fqcn);
            out.addAll(interfacesOf(ic, seen));
        }
    }
    ```
    javadoc 을 「…구현하는 인터페이스 FQCN(색인에 있는 것만 — R-C-I6)」 로
  - `resolve:620` — `implementors.getOrDefault(t.name, List.of())` → `implementors.getOrDefault(t.fqcn, List.of())`
  - `:149` 선언 옆 주석 「인터페이스 FQCN → 구현 클래스」
- 시험(먼저): `JavaGraphTest.sameNameServicesInTwoModules` — `sqlTextIsNotAStatementId` 의 인라인 `Source` 꼴로 여섯 파일: `a/UserController.java`(`package app.a; @Controller class UserController { @Autowired UserService userService; @RequestMapping("/a/user/save.do") String save() { userService.save(); return "x"; } }`) · `a/UserService.java`(interface) · `a/UserServiceImpl.java`(`@Service class UserServiceImpl implements UserService { @Autowired UserDAO dao; public void save() { dao.insert("A.insertUser", null); } }`) · `a/UserDAO.java`(`class UserDAO { int insert(String id, Object p) { return 0; } }` — 이름이 DAO 라 싱크) · `b/…` 같은 넷(`package app.b`, 문장 `B.insertUser`) → `/a/user/save.do` 프로그램의 문장이 `[A.insertUser]` 하나 · `g.unresolved()` 에 `ambiguous` 없음. RED: 문장 둘 + `ambiguous`
- 의도된 골든 변화: `golden/analyze/java-graph.json`·`generic-graph.json`·`jpa-graph.json`·`qdsl-graph.json`·`jdbc-graph.json` diff 0(픽스처의 두 구현 `BoardServiceImpl`·`BoardServiceTestImpl` 은 같은 인터페이스 FQCN 이라 그대로). corpus `ambiguous` 가 **주는 것**은 `V-67a`
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,AnalyzeStoreTest,ConsistencyTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus
- 사다리: 시험의 DAO 가 싱크로 안 잡히면(`statements` 빔) — `sqlTextIsNotAStatementId` 가 쓰는 DAO 꼴(조상·이름)을 그대로 따른다

### 6-53 — JavaGraph: 문장 실행 호출 꼴 — `this.필드` · 지역 SqlSession · iBATIS (R-C-I7 ①② · R-C-I8 그래프 쪽)
- 대상: `core/analyze/JavaGraph.java`(`SINKS`·`sink`) · `JavaGraphTest`
- 사실: `sink:502-512` 는 수신자가 `NameExpr` 필드(형식에 `SqlSession`)이거나 `getSqlSession()`·`getSqlSessionTemplate()` 일 때만 참. `this.sqlSession.selectList(…)`(FieldAccessExpr)·지역 변수 `SqlSession s`·`getSqlMapClientTemplate().queryForList(…)` 는 문장 0·미해결 0
- 변경:
  - `SINKS` 에 `"listWithPaging", "queryForList", "queryForObject", "queryForMap"` 을 더한다
  - `sink` 시그니처에 `MethodDeclaration m` 을 더한다(`sink(c, m, scope, self, name, argc)` — 호출 `walk:439` 한 곳). 몸의 `self` 아닌 가지를:
    ```java
    Expression s = scope.get();
    String var = s instanceof NameExpr n ? n.getNameAsString() : s instanceof FieldAccessExpr fa && fa.getScope().isThisExpr() ? fa.getNameAsString() : null;
    if (var != null) {
        Field f = field(c, var);
        String type = f != null ? f.type() : s instanceof NameExpr ? localType(m, var) : null;
        return type != null && SESSION_TYPE.matcher(type).find();
    }
    return s instanceof MethodCallExpr mc && SESSION_GETTERS.contains(mc.getNameAsString());
    ```
    ```java
    /** 문장을 실행하는 객체의 형식 — MyBatis SqlSession·SqlSessionTemplate, iBATIS SqlMapClient·SqlMapClientTemplate */
    private static final Pattern SESSION_TYPE = Pattern.compile("SqlSession|SqlMapClient");
    private static final Set<String> SESSION_GETTERS = Set.of("getSqlSession", "getSqlSessionTemplate", "getSqlMapClientTemplate", "getSqlMapClient");
    ```
    javadoc 에 「`this.필드`·지역 변수·매개변수(R-C-I7) · iBATIS(R-C-I8)」
- 시험(먼저): `JavaGraphTest.sessionCallShapes` — 컨트롤러 하나(`@RequestMapping("/s/a.do")`·`/s/b.do`·`/s/c.do` 가 `dao.a()`·`dao.b(f)`·`dao.c()` 를 부른다)와
  ```java
  package p; public class ShapeDAO { private org.mybatis.spring.SqlSessionTemplate sqlSession; private com.ibatis.sqlmap.client.SqlMapClient client;
    public Object a() { return this.sqlSession.selectList("S.listA"); }
    public Object b(org.apache.ibatis.session.SqlSessionFactory f) { org.apache.ibatis.session.SqlSession s = f.openSession(); return s.selectOne("S.oneB"); }
    public Object c() throws Exception { return client.queryForList("S.listC", null); } }
  ```
  → 프로그램 셋의 문장이 차례로 `[S.listA]`·`[S.oneB]`·`[S.listC]`. RED: 셋 다 빔
- 의도된 골든 변화: `golden/analyze/*-graph.json` diff 0 기대(픽스처 `OtherServiceImpl` 의 맨 `sqlSession.selectList` 는 지금도 잡힌다). corpus 는 `V-67a`
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,AnalyzeStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-52(같은 시험 파일). 레인 corpus
- 사다리: `jdbc-graph.json` 이 바뀌면 — `JdbcTemplate.queryForList` 가 `sink` 에 걸린 것이다. `SESSION_TYPE` 이 `JdbcTemplate` 에 안 맞는지 보고, 맞으면 고침이 넓다

### 6-54 — JavaGraph: 조상이 선언한 매핑 메서드 (R-C-I7 ③)
- 대상: `core/analyze/JavaGraph.java`(`run` 의 프로그램 루프) · `core/analyze/TypeBind.java` · `JavaGraphTest`
- 사실: `run:228` 이 `c.decl.getMethods()` 만 돌아 `@RequestMapping("/dept") class DeptController extends BaseController` 가 조상의 `@GetMapping("/list.do")` 를 프로그램으로 못 낸다
- 변경:
  - `TypeBind` 에
    ```java
    /** R-C-I7 — c 의 메서드와 물려받은 조상 메서드(가까운 쪽이 같은 이름·인자 수로 덮은 것은 뺀다). 값 = 그 메서드를 선언한 클래스. 사다리 상한 MAX_UP */
    static List<Map.Entry<JavaGraph.Cls, MethodDeclaration>> handlers(JavaGraph.Cls c, BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup)
    ```
    — 자기 메서드(선언 차례) 다음 조상 메서드(가까운 조상부터, 선언 차례). 덮임 판정 열쇠 `이름 + "/" + 인자 수`. 조상은 `lookup.apply(cur, cur.superName)` 의 첫째, `seen` 으로 순환을 끊는다
  - `run:228` `for (MethodDeclaration m : c.decl.getMethods()) {` → `for (Map.Entry<Cls, MethodDeclaration> h : TypeBind.handlers(c, this::lookup)) {` + 첫 줄 `MethodDeclaration m = h.getValue();`. 그 루프 안 `c.file` 둘(`note("viewShape", c.file, …)` · `new Program(c.name, m.getNameAsString(), c.file, …)`)을 `h.getKey().file` 로(줄 번호가 그 파일 것이다). `SpringMappings.of(c.decl, m)`·`walked(c, m, acc)`·`views(c, m, badShape)`·`JavaViews.kind(c, m, views)` 의 `c` 는 그대로(접두·필드·호출 대상은 구체 컨트롤러 기준)
  - DAO 훑기 루프(`:264`)는 안 고친다
- 시험(먼저): `JavaGraphTest.inheritedMappingIsAProgram` — `web/BaseController.java`(`public abstract class BaseController { @Autowired InhDAO dao; @GetMapping("/list.do") public String list() { dao.list("I.list", null); return "dept/list"; } }`) · `web/DeptController.java`(`@Controller @RequestMapping("/dept") public class DeptController extends BaseController { @GetMapping("/list.do") … 없음 }`) · `dao/InhDAO.java` → 프로그램 하나: `className` `DeptController` · `url` `/dept/list.do` · `file` `web/BaseController.java` · 문장 `[I.list]`. 하위가 같은 이름·인자 수 메서드를 선언하면 조상 것은 안 나온다(둘째 단언). RED: 프로그램 0
- 의도된 골든 변화: `golden/analyze/*-graph.json` diff 0 기대(픽스처에 매핑을 가진 조상이 없다). corpus 프로그램 수가 **느는 것**은 `V-67a`
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,AnalyzeStoreTest,AnalyzeStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-53. 레인 corpus
- 한계(설계 문서에만): 클래스 단 `@RequestMapping` 을 조상만 가진 꼴은 접두 없이 낸다

### 6-55 — JavaGraph: 수신자 — 템플릿 메서드 (R-C-I7 ④)
- 대상: `core/analyze/JavaGraph.java`(`Acc`·`walk`·`walked`·`repoCall`) · `core/analyze/TypeBind.java` · `JavaGraphTest`
- 사실: `walk:493` 이 조상 메서드로 들어가면 `c` 가 조상이 되고, 그 안 맨 호출은 `targets.add(c)`(`:445`) → `methods(c, …)` 가 조상에서 위로만 찾아 하위 클래스의 재정의(훅)를 못 본다. `walk(5)` 복잡도 42·중첩 5 는 기준선 — 시그니처도 분기 수도 못 바꾼다
- 변경(시그니처 그대로, 분기 수 그대로):
  - `Acc` 에 필드 `Cls recv;` — 「지금 걷는 메서드가 불린 객체의 클래스(맨 호출·this 호출의 대상을 여기서 찾는다)」
  - `walked:296` — `walk(…)` 앞에 `acc.recv = c;`
  - `walk` — ① visited 열쇠 끝에 `+ "@" + acc.recv.fqcn` ② `:445` `targets.add(c);` → `targets.add(acc.recv);` ③ `:493` 을
    ```java
    Cls was = acc.recv;
    acc.recv = self ? was : t;
    walk(x.owner(), x.method(), depth + 1, acc, TypeBind.bindFor(self, c, bind, t, x.owner(), this::lookup));
    acc.recv = was;
    ```
    (`self ? … : …` 삼항이 `walk` 에서 하나 빠지고 하나 들어온다 — 복잡도 42 그대로)
  - `TypeBind` 에
    ```java
    /** 호출 자리의 묶음 — 자기 호출(맨·this·super)이면 c 의 묶음에서, 다른 객체면 그 객체에서 빈 묶음으로. JavaGraph.walk 의 가지를 여기로 옮겼다 */
    static Map<String, JavaGraph.Cls> bindFor(boolean self, JavaGraph.Cls c, Map<String, JavaGraph.Cls> bind, JavaGraph.Cls t, JavaGraph.Cls owner,
            BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
        return self ? bindFor(c, bind, owner, lookup) : bindFor(t, Map.of(), owner, lookup);
    }
    ```
  - `repoCall:701` — `walk(…)` 호출을 `Cls was = acc.recv; acc.recv = x.owner(); walk(…); acc.recv = was;` 로 감싼다
- 시험(먼저): `JavaGraphTest.templateMethodReachesOverride` — `svc/Tmpl.java`(`public abstract class Tmpl { public void run() { step(); } protected abstract void step(); }`) · `svc/TmplImpl.java`(`@Service public class TmplImpl extends Tmpl { @Autowired TmDAO dao; protected void step() { dao.update("T.step", null); } }`) · 컨트롤러(`@Autowired TmplImpl tm; @RequestMapping("/tm/run.do") … tm.run();`) · `dao/TmDAO.java` → 문장 `[T.step]`. RED: 빔
- 의도된 골든 변화: `golden/analyze/*-graph.json` diff 0 기대 — 바뀌면 문장이 **더해지는** 쪽만(같은 메서드를 다른 수신자로 다시 걸어 하위 재정의에 닿은 것). 빠지는 문장은 고침이 틀렸다
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,AnalyzeStoreTest,ConsistencyTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-54. 레인 corpus
- 사다리: 골든에서 `depth` 미해결이 새로 나오면(같은 메서드를 수신자마다 다시 걸어 `MAX_FRAMES` 500 에 닿음) — visited 열쇠의 `@수신자` 를 「`acc.recv` 가 `c` 와 다를 때만」 붙이는 식으로 좁히지 말고 멈춘다(7절)

### 6-56 — JavaGraph: 필드의 타입 인자 (R-C-I7 ⑤)
- 대상: `core/analyze/JavaGraph.java`(`Field`·`index`·`walk`) · `core/analyze/TypeBind.java` · `JavaGraphTest`
- 사실: `index:358` 이 `ct.getNameAsString()` 만 담아 `CrudService<UserDAO> svc` 의 `UserDAO` 를 버린다. `walk:493` 은 다른 객체 호출에 빈 묶음을 줘 `CrudService` 안 `D dao` 가 안 풀린다
- 변경:
  - `private record Field(String type, String bean, int line)`(`:111`) → `record Field(String type, String bean, int line, List<String> args)`(`private` 를 뗀다 — `TypeBind` 가 받는다). `index:358` 의 `new Field(ct.getNameAsString(), b, line(fd))` → `new Field(ct.getNameAsString(), b, line(fd), TypeBind.args(ct))`. 다른 `new Field(` 자리는 `List.of()`
  - `TypeBind` 에 `static List<String> args(ClassOrInterfaceType ct)`(타입 인자의 단순 이름, 없으면 빈 목록 — 지금 `superArgs` 가 쓰는 `simple` 그대로)와, 6-55 의 `bindFor(boolean self, …)` 에 인자 `JavaGraph.Field via` 를 더한 꼴:
    ```java
    static Map<String, JavaGraph.Cls> bindFor(boolean self, JavaGraph.Cls c, Map<String, JavaGraph.Cls> bind, JavaGraph.Cls t, JavaGraph.Cls owner,
            JavaGraph.Field via, BiFunction<JavaGraph.Cls, String, List<JavaGraph.Cls>> lookup) {
        return self ? bindFor(c, bind, owner, lookup) : bindFor(t, fieldBind(c, bind, t, via, lookup), owner, lookup);
    }

    /** 필드 선언의 타입 인자를 그 형식의 타입 매개변수에 자리로 — via 가 없거나 인자가 없으면 빈 묶음. 인자가 타입 변수면 지금 묶음에서, 아니면 이름이 하나로 풀릴 때만 */
    private static Map<String, JavaGraph.Cls> fieldBind(JavaGraph.Cls c, Map<String, JavaGraph.Cls> bind, JavaGraph.Cls t, JavaGraph.Field via, BiFunction<…> lookup)
    ```
    (`next` 와 같은 짝짓기 — `t.decl.getTypeParameters()` 와 `via.args()`)
  - `walk` — 수신자 가지들 앞에 `Field via = null;`, 필드 가지의 `Field f = field(c, fieldName);` 바로 뒤에 `via = f;`(문장 — 분기 아님), `:493` 의 `TypeBind.bindFor(self, c, bind, t, x.owner(), this::lookup)` → `TypeBind.bindFor(self, c, bind, t, x.owner(), via, this::lookup)`
- 시험(먼저): `JavaGraphTest.fieldTypeArgumentBindsTheDao` — `svc/GenSvc.java`(`@Service public class GenSvc<D extends GenBase> { @Autowired protected D dao; public void save() { dao.insert("G.save", null); } }` — `dao.insert` 가 문장이 되게 `D` 가 풀려야 한다: `dao/GenBase.java`(`public abstract class GenBase {}`) · `dao/GenDAO.java`(`@Repository public class GenDAO extends GenBase { public int insert(String id, Object p) { return 0; } }`)) · 컨트롤러(`@Autowired GenSvc<GenDAO> svc; @RequestMapping("/tm/gen.do") … svc.save();`) → 문장 `[G.save]`. RED: 빔
- 의도된 골든 변화: `golden/analyze/generic-graph.json` diff 0(`extends` 묶음 길은 그대로)
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,AnalyzeStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-55. 레인 corpus
- 사다리: 시험에서 `dao.insert` 가 싱크가 아니라 메서드 호출로 걸려 문장이 안 나오면 — `GenDAO.insert` 몸을 `return getSqlSession().insert(id, p);` 로 바꾸고 `GenDAO` 가 `SqlSessionDaoSupport` 꼴 이름의 조상을 갖게 한다(시험 입력만 고친다)

### 6-57 — MapperIndex: iBATIS `<sqlMap>` (R-C-I8)
- 대상: `core/analyze/MapperIndex.java` · `MapperIndexTest`
- 사실: `parse:124` 가 `<mapper` 없는 XML 을 건너뛰고 `Handler.startElement:309` 가 루트 `mapper` 만 받는다 → eGov 3.5 이하(iBATIS) 문장 0. `startElement(4)` 복잡도 19 는 기준선
- 결정: 문장 열쇠는 **id 그대로**(namespace 를 안 붙인다 — iBATIS 기본 `useStatementNamespaces=false`, eGov 관례 `id="loginDAO.actionLogin"`). 치환 꼴은 MyBatis 꼴로 바꿔 담는다(`#이름#` → `#{이름}` · `$이름$` → `${이름}`) — 뒤 판정(바인드·`${}` 위험·표 추출)이 한 꼴만 보게
- 변경:
  - `STATEMENTS` 에 `"statement", "procedure"` · `DYNAMIC` 에 `"dynamic", "isEqual", "isNotEqual", "isGreaterThan", "isGreaterEqual", "isLessThan", "isLessEqual", "isPropertyAvailable", "isNotPropertyAvailable", "isNull", "isNotNull", "isEmpty", "isNotEmpty", "isParameterPresent", "isNotParameterPresent", "iterate"`
  - `parse:124` — `if (!s.text().contains("<mapper") && !s.text().contains("<sqlMap")) {`
  - `Handler` — 필드 `private boolean ibatis;`. `startElement:309-312` 를 (분기 수 그대로)
    ```java
    if (ROOTS.contains(qName) && depth == 0) {
        mapper = true;
        ibatis = "sqlMap".equals(qName);
        ns = namespace(qName, atts);
        return;
    }
    ```
    `private static final Set<String> ROOTS = Set.of("mapper", "sqlMap");` · `Handler` 안 `private static String namespace(String root, Attributes atts)` — `sqlMap` 이면 `""`, 아니면 지금 식(`namespace` 속성, 없으면 `""`)
  - `startElement` 의 `default -> b.append('\n');` → `default -> b.append('\n').append(prepend(atts));` · `private static String prepend(Attributes atts)` — `prepend` 속성이 있으면 그 글 + `'\n'`, 없으면 `""`(iBATIS `<dynamic prepend="WHERE">`·`<isNotEmpty prepend="AND">`)
  - `endElement:375` — `new Raw(ns, id, tag, text.toString(), …)` 의 글을 `sqlText()` 로: `private String sqlText() { return ibatis ? IBATIS_DOLLAR.matcher(IBATIS_HASH.matcher(text).replaceAll("#{$1}")).replaceAll("\\${$1}") : text.toString(); }`
    ```java
    private static final Pattern IBATIS_HASH = Pattern.compile("#([\\w.\\[\\]]++)(?::[\\w:.-]*+)?#");
    private static final Pattern IBATIS_DOLLAR = Pattern.compile("\\$([\\w.\\[\\]]++)\\$");
    ```
  - 열쇠 — `static String key(String ns, String id) { return ns.isEmpty() ? id : ns + "." + id; }` 를 `scan:109` · `parse:140-143`(넷) · `include:265`(`refid.contains(".") ? refid : key(r.ns, refid)`) · `dynamicInclude` 의 같은 식에 쓴다. `namespaces()` 가 모으는 자리에서 빈 namespace 는 뺀다
- 시험(먼저): `MapperIndexTest.ibatisSqlMap`(인라인 — 픽스처 폴더에 파일을 더하지 않는다: `golden`·`namespacesAreStatementKeyPrefixes` 가 폴더 전체를 본다) —
  ```xml
  <sqlMap namespace="Old"><sql id="cols">ID, NM</sql>
  <select id="oldDAO.selectOld" resultClass="map">SELECT <include refid="cols"/> FROM T88_OLD WHERE ID = #id# <isNotEmpty prepend="AND" property="nm">NM = #nm:VARCHAR#</isNotEmpty> ORDER BY $sort$</select>
  <statement id="oldDAO.touch">UPDATE $owner$.T88_OLD SET X = 1 WHERE Y IN (SELECT Y FROM $owner$.T88_REF)</statement></sqlMap>
  ```
  → `idx.get("oldDAO.selectOld")` 가 있고 표 `T88_OLD` R · `dollars()` `[sort]` · `dynamic()` 참 · `idx.get("oldDAO.touch")` 표 `T88_OLD` U·`T88_REF` R · `dollars()` `[owner]` · `idx.unresolved()` 에 `missing` 없음. RED: `idx.get(…)` null
- 의도된 골든 변화: `golden/analyze/mapper-index.json` diff 0(픽스처에 sqlMap 없음 · `not-mapper.xml` 은 그대로 건너뛴다). corpus 에 `<sqlMap` 0(탐색 확인) → `V-67a` diff 0 기대
- 검증: `bash scripts/mvn.sh -q test -Dtest='MapperIndexTest,SqlProbeTest,AnalyzeStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus
- 한계(설계 문서에만): `useStatementNamespaces=true` 프로젝트의 `ns.id` 호출은 `missing` 미해결로 남는다 · 코드 검사 `MyBatisRules` 는 `<sqlMap>` 을 여전히 안 본다

### 7-36 — 접근자 이름: 둘째 글자가 대문자인 필드 (R-A-I11)
- 대상: `core/gen/DtoGenerator.java` · `core/gen/GenModel.java` · `DtoGeneratorTest`(또는 그 클래스의 이름 시험) · `GenTemplatesTest`
- 사실: `E_MAIL` → 필드 `eMail`, 접근자 `getEMail()`(`GenModel.of:128` · `DtoGenerator.bean:209`). JavaBeans 규칙으로 그 속성 이름은 `EMail` 이라 JSP `${result.eMail}` 이 런타임 오류다
- 결정: 접근자를 JavaBeans 규칙에 맞춘다 — 필드 둘째 글자가 대문자면 첫 글자를 안 올린다(`geteMail()`). EL·Spring 바인딩·MyBatis·Jackson 이 모두 `eMail` 로 읽는다. 필드 이름(1-9 규칙)과 JSP 는 안 바꾼다
- 변경:
  - `DtoGenerator` 에
    ```java
    /** 접근자 이름 꼬리 — JavaBeans: 둘째 글자가 대문자면 그대로(eMail → geteMail), 아니면 첫 글자만 올린다(R-A-I11) */
    static String accessor(String field) {
        return field.length() > 1 && Character.isUpperCase(field.charAt(1)) ? field : Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }
    ```
  - `bean:209` `String cap = accessor(f.name());` · `GenModel.of:128` `f.put("Name", DtoGenerator.accessor(name));`(줄 수 그대로)
- 시험(먼저): `DtoGenerator` 시험 클래스에 `accessorFollowsJavaBeans` — `accessor("eMail")` = `eMail` · `accessor("name")` = `Name` · `accessor("x")` = `X` · BEAN 꼴로 `E_MAIL VARCHAR(10)` 컬럼 표를 만들면 `geteMail()`·`seteMail(` 이 있고 `getEMail` 이 없다 · `java.beans.Introspector.decapitalize("eMail")` 이 `eMail`(규칙 확인 한 줄). `GenTemplatesTest` 에 `singleLetterTokenAccessor` — 컬럼 `NOTICE_ID`(PK)·`E_MAIL` 표를 `render("egov35", "oracle", …)` 로 → VO 에 `geteMail()`, Detail JSP 에 `${result.eMail}`. RED: `getEMail`
- 의도된 골든 변화: `golden/gen/**`·corpus `gen-*.json` — 첫 마디가 한 글자인 컬럼(`E_MAIL`·`N_AGE` 꼴)의 접근자 이름이 **바뀌는 것만**. 그런 컬럼이 픽스처에 없으면 diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='DtoGeneratorTest,GenModelTest,GenTemplatesTest,GeneratorTest,GenRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 db + corpus(`core/gen`)

### 0-106 — 규칙 덮어쓰기: 저장 전에 적재해 본다 (R-G-6 · G1-K10)
- 대상: `web/ProfileRoutes.java`(PUT `/api/profiles/{name}/codecheck`) · `web/App.java`(`jsonErrors`) · `core/check/RuleSet.java`(예외 형식) · `ProfileRoutesTest`(PUT 을 시험하는 클래스)
- 사실: PUT 이 `rules` 의 정규식을 안 재고 저장 → 그 뒤 `GET /api/check/rules`·`/api/check/fixes`·`/api/cleanup/rules` 가 `RuleSet.validate` 의 `IllegalArgumentException` 으로 500, 화면에서 되돌릴 길이 없다. 모르는 키 든 프로필은 PUT 이 `UncheckedIOException` 500 + 백업 파일만 남는다(G1-K10)
- 변경:
  1. `RuleSet` 에 `public static final class BadRule extends IllegalArgumentException`(생성자 `(String message)`·`(String message, Throwable cause)`). `validate` 가 던지는 넷과 `build` 의 「모르는 kind」 를 `BadRule` 로(글자 그대로). `IllegalArgumentException` 을 잡는 기존 자리(`CheckRoutes.postRun:154`)는 그대로 듣는다
  2. `App.jsonErrors` — `ProfileUnreadable` 줄 아래에 `app.exception(kr.ejg.toolbox.core.check.RuleSet.BadRule.class, (e, ctx) -> ctx.status(400).json(Map.of(MESSAGE, "규칙을 못 읽었다 — " + e.getMessage() + " (프로필 codecheck.rules 를 고친다)")));`
  3. `ProfileRoutes` PUT — `CodeCheckRequest req = ctx.bodyAsClass(…)` 다음, 백업을 쓰기 **앞에**:
     ```java
     Profile before;
     try {
         before = store.load(name);
     } catch (java.io.UncheckedIOException e) {
         ctx.status(400).json(Map.of("message", e.getMessage())); // G1-K10 — 못 읽는 프로필은 백업도 안 만든다(GET 과 같은 글)
         return;
     }
     try {
         CheckRoutes.load(before, req.groups(), req.rules()); // R-G-6 — 저장하면 못 읽힐 규칙은 여기서 400, 저장·백업 없음
     } catch (IllegalArgumentException e) {
         ctx.status(400).json(Map.of("message", "저장하지 않았다 — " + e.getMessage()));
         return;
     }
     ```
     람다 몸이 길어지면 `private static void putCodeCheck(Context ctx, …)` 로 뺀다(작성 규약)
- 시험(먼저): PUT 시험 클래스에 `badRuleRegexIsRefusedBeforeSave` — `PUT …/codecheck {"rules":{"common.debugger":{"regex":"["}}}` → 400, 글에 `common.debugger`·`정규식 오류` · 프로필 파일 바이트가 그대로 · `out/` 아래 `backup/profiles` 에 새 파일 없음 · 이어서 `GET /api/check/rules` 200. `unknownKeyProfileIs400WithoutBackup` — 모르는 키 `nosuchkey: 1` 을 손으로 넣은 프로필에 PUT → 400(글에 `nosuchkey`), 백업 없음. `brokenSavedRuleIs400OnRead` — 프로필 YAML 에 `rules: {common.debugger: {regex: "["}}` 를 손으로 쓴 뒤 `GET /api/check/rules`·`GET /api/cleanup/rules` → 400 + `규칙을 못 읽었다`. RED: 200 저장 뒤 500 · 500 · 500
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='ProfileRoutesTest,CheckRoutesTest,CleanupRoutesTest,CheckRulesTest,JsonErrorTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus(`core/check`)
- 사다리: PUT 시험 클래스 이름이 다르면 `grep -rl "codecheck" src/test/java/kr/ejg/toolbox/web` 로 찾는다

### 4-37 — LogSql: 앞머리로 짝짓기 · 타입 없는 파라미터 시간 (R-G-9 · G2-K9 · G1-K18)
- 대상: `core/text/LogSql.java` · `src/main/resources/tools/dev_tools_ext.js`(요약 한 줄) · `LogSqlTest`
- 사실: `restore:56-65` 가 「다음 Preparing 앞 첫 Parameters」 를 짝짓는다 — 두 스레드가 섞이면 B 문장에 A 의 값이 경고 없이 들어간다. `params:124` 는 값마다 `TYPE_END.matcher(s).find(pos)` 를 새로 돌려 타입 없는 줄에서 제곱 시간이다
- 결정: 짝은 **앞머리 열쇠**(그 줄의 `Preparing:`·`Parameters:` 앞 글에서 날짜·시각·맨 숫자·화살표를 뺀 것 — 스레드·로거가 남는다)가 같은 것끼리. 같은 열쇠의 Preparing 이 Parameters 없이 연달아 오면 가를 수 없다 — 뒤 문장은 복원하되 경고를 단다
- 변경:
  ```java
  /** 앞머리에서 줄마다 달라지는 것 — 날짜·시각·맨 숫자(pid)·화살표 */
  private static final Pattern VOLATILE = Pattern.compile("\\d{2,4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}:\\d{2}(?::\\d{2})?(?:[.,]\\d+)?|(?<![\\w\\[.-])\\d+(?![\\w\\].-])|[=<>-]+>?");
  static final String MAYBE_PREVIOUS = "같은 앞머리의 Preparing 이 연달아 왔다 — 앞 문장의 Parameters 일 수 있다, 확인";

  /** 짝 열쇠 — 앞머리에서 VOLATILE 을 빼고 공백을 하나로. 앞머리가 없으면 빈 글(전부 같은 열쇠 — 옛 동작) */
  static String key(String line, int at)   // at = 「Preparing:」·「Parameters:」 가 시작하는 위치
  ```
  `restore` 를 한 번 훑기로:
  - 줄마다 — `Preparing:` 이면 `Pending(sql, key)` 를 목록 끝에 더한다. 그 앞에 같은 열쇠의 아직 짝 없는 `Pending` 이 있었으면 새 것에 `after = true` 표시
  - `Parameters:` 이면 같은 열쇠의 **가장 최근** 짝 없는 `Pending` 에 붙인다. 없으면 열쇠와 무관하게 바로 앞(목록 끝) `Pending` 이 짝 없을 때 거기에(옛 동작 — 앞머리 꼴이 줄마다 다른 로그), 그것도 없으면 버린다
  - 끝에 `Pending` 차례대로 `item(sql, paramLine)` — `after` 이고 복원이 됐으면(`warning == null`) `warning = MAYBE_PREVIOUS` 로 바꾼 `Item`
  - `private record`/작은 클래스 `Pending` 은 `LogSql` 안에. 메서드마다 복잡도 15 이하
  `params` — `Matcher m = TYPE_END.matcher(s);` 를 루프 밖으로, `boolean typed = true;` 를 두고 `if (typed && m.find(pos)) { … } else { typed = false; …쉼표로 끊기… }`(한 번 못 찾으면 그 줄의 뒤에도 없다)
  `dev_tools_ext.js` 의 요약(`'문장 ' + LS.length + (warned ? ' · 원문 그대로 ' + warned : '')`) — `warned` 를 「`it.warning` 이 있고 `it.restored === it.sql`」 로 세고, 「`it.warning` 이 있고 `it.restored !== it.sql`」 수를 `' · 확인 ' + n` 으로 잇는다
- 시험(먼저): `LogSqlTest` —
  - `interleavedThreadsPairByPrefix`: `"[exec-1] a.UserMapper.sel : ==>  Preparing: select * from A where id = ?\n[exec-2] a.DeptMapper.sel : ==>  Preparing: select * from B where id = ?\n[exec-1] a.UserMapper.sel : ==> Parameters: 111(Integer)\n[exec-2] a.DeptMapper.sel : ==> Parameters: 222(Integer)\n"` → A 가 `… id = 111`, B 가 `… id = 222`, 경고 둘 다 null. RED: A 「Parameters 줄이 없다」·B 에 111
  - `samePrefixBackToBackIsWarned`: 앞머리 없는 `Preparing: … A …?` · `Preparing: … B …?` · `Parameters: 1(Integer)` → A 「Parameters 줄이 없다 — 원문 그대로」, B 복원 + `MAYBE_PREVIOUS`
  - `timestampsDoNotSplitPairs`: `2026-09-27 10:00:00.001 DEBUG 1234 --- [main] a.M.s : ==>  Preparing: …?` + `2026-09-27 10:00:00.007 DEBUG 1234 --- [main] a.M.s : ==> Parameters: 1(Integer)` → 복원·경고 null
  - `untypedParamsAreLinear`: `"Preparing: ?\nParameters: " + "a, ".repeat(200_000) + "a\n"` 이 5초 안(결과는 「? 1개 ≠ …」 경고)
  - 기존 `cases`·`mybatisGolden` 그대로 초록
- 의도된 골든 변화: `golden/text/logsql-mybatis.json` diff 0 · corpus `logsql-summary.json`·`logsql-warnings.txt`(빈 파일) diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='LogSqlTest,TextRoutesTest,ToolsFolderTest,SmokeHtmlUnitTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus(`core/text`) + tools
- 사다리: `mybatisGolden` 이 바뀌면 — 픽스처의 세 앞머리 꼴 가운데 Preparing 과 Parameters 의 열쇠가 다르게 나온 것이다. `VOLATILE` 에 그 차이(예: 화살표 길이)를 더한다. 그래도 다르면 「열쇠가 다르면 바로 앞 Pending」 길이 받는지 본다

### 7-35 — 템플릿 코멘트: form 태그 속성 · VO doc 의 CHECK 글 (기존 행)
- 대상: `core/gen/GenModel.java` · `templates/gen/egov35/input.ftl`·`List.jsp.ftl`·`Updt.jsp.ftl` · `GenTemplatesTest`
- 사실: `safe()` 의 엔티티가 `<form:input title="[=f.comment]">` 에 들어가면 Jasper 는 `&quot;` 만 풀고 form 태그 htmlEscape 가 `&` 를 다시 이스케이프한다(「Q&A」 → 툴팁 `Q&amp;A`). `f.doc` 에 붙는 `Validation` notes(「CHECK: 조건」)는 `safe` 를 안 지난다
- 변경:
  - `GenModel` 에
    ```java
    /** JSP 커스텀 태그 속성 값(form 태그 title) — 태그가 HTML 이스케이프를 하므로 JSP 인용만: 역슬래시·따옴표·EL 시작(7-35) */
    static String attr(String s) {
        return s.replaceAll("\\R", " ").replace("\\", "\\\\").replace("\"", "&quot;").replace("${", "\\${").replace("#{", "\\#{");
    }
    ```
  - `of` — **줄 수를 지킨다**(`of` 길이 107 = 기준선). 코멘트 원문 식(`:123-124` 두 줄에 걸친 삼항)을 `private static String commentOf(Column c, Options o, String key)` 로 뺀다. 그러면 ① `:123-124` 두 줄이 `String comment = safe(commentOf(c, o, key));` **한 줄**이 되고 ② `f.put("comment", comment);`(`:134`) 바로 아래에 `f.put("title", attr(commentOf(c, o, key)));` **한 줄**이 는다 — 합쳐서 줄 수 그대로. `f.put("doc", …)`(`:138`) 의 notes 쪽은 같은 줄에서 `safe(String.join(" · ", v.notes()))` 로. `of(4)` 복잡도 34 는 삼항이 빠져 내린다(기준선 갱신)
  - 템플릿 — form 태그의 `title="[=f.comment]"` 를 `title="[=f.title]"` 로, `title="[=f.comment] 시작"`·`끝"` 은 `title="[=f.title] 시작"`·`끝"` 으로: `input.ftl:5·7·9·11·13·15·21` · `List.jsp.ftl:26·28·31·34·37` · `Updt.jsp.ftl:20`. `<label>`·`<th>`·`<title>`·`<caption>` 의 `[=f.comment]`·`[=comment]` 는 그대로(JSP 글 자리 — 엔티티가 맞다)
- 시험(먼저): `GenTemplatesTest` 에 둘 — `formTitleIsNotDoubleEscaped`: 컬럼 코멘트 `Q&A "답" ${a} C:\x` 인 표를 `render("egov35", "oracle", …)` → Regist·Updt JSP 의 `title="` 값이 `Q&A &quot;답&quot; \${a} C:\\x` 이고 같은 파일 `<label …>` 글은 `Q&amp;A &quot;답&quot; &#36;{a}` · `checkNoteDoesNotCloseJavadoc`: CHECK `(MEMO <> '*/')` 를 가진 컬럼 → VO 소스에 `*/'` 가 없고(`*&#47;`) javac 없이 「`/**` 뒤 첫 `*/` 가 그 줄 끝」 을 줄 단위로 단언. RED: `Q&amp;A` · `*/'`
- 의도된 골든 변화: `golden/gen/**` 의 JSP — form 태그 `title="…"` 안 엔티티가 원래 글자로 **돌아가는 것만**(코멘트에 `& < >` 가 든 픽스처 컬럼). `model*.json` 에 `title` 키가 **더해지는 것**
- 검증: `bash scripts/mvn.sh -q test -Dtest='GenTemplatesTest,GenModelTest,GeneratorTest,GenerateRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 7-36(같은 파일). 레인 db + corpus

### 1-106 — DdlAlter·DdlGen 잔손 (기존 행)
- 대상: `core/gen/DdlAlter.java` · `core/gen/DdlReader.java` · `core/gen/DdlGen.java` · `web/GenRoutes.java`·`web/InsertRoutes.java`(열쇠 한 줄씩) · `DdlReaderTest` · `DdlGenTest`
- 변경:
  1. `DdlAlter` — `private static final Pattern DEFAULT_NULL = Pattern.compile("\\bDEFAULT\\s++NULL\\b", Pattern.CASE_INSENSITIVE);` · `put:299` `boolean saysNull = NULL_WORD.matcher(DEFAULT_NULL.matcher(bare).replaceAll("DEFAULT")).find();`(`DEFAULT NULL` 의 NULL 은 「NULL 을 적었다」 가 아니다)
  2. `put:303` `d.notes.putIfAbsent(…, DdlReader.AMBIGUOUS)` → `d.notes.merge(c.name().toUpperCase(Locale.ROOT), DdlReader.AMBIGUOUS, (a, b) -> a.contains(b) ? a : a + " / " + b);` · `InsertRoutes.missedLines:153` `DdlReader.AMBIGUOUS.equals(note)` → `note.contains(DdlReader.AMBIGUOUS)`
  3. notes 열쇠 — `DdlReader` 에 `public static String noteKey(String schema, String name)`(`스키마.이름` 대문자, 스키마가 비면 이름만) · `read:247` `notes.put(noteKey(d.schema, d.name), …)` · `Result` javadoc 「notes: `noteKey(스키마, 표)` → …」 · 읽는 두 자리 `GenRoutes:116` `notes.get(DdlReader.noteKey(t.schema(), t.name()))` · `InsertRoutes:152` `parsed.notes().getOrDefault(DdlReader.noteKey(t.schema(), t.name()), Map.of())`
  4. `DdlGen.table:274-278` — `inCreate = true; try { for (…) { … } } finally { inCreate = false; }`
  5. `DdlGen.keyLen:410` — 조건 끝에 `|| given && "postgresql".equals(target)` 을 더한다(길이가 적힌 문자 키는 postgresql 대상이면 그대로 — `TEXT` 키가 합법). 상수가 있으면 그것으로
- 시험(먼저): `DdlReaderTest.modifyKeepsOrWarns` 에 — `CREATE TABLE t (a VARCHAR2(10) NOT NULL); ALTER TABLE t MODIFY (a VARCHAR2(20) DEFAULT NULL);` → `a` 가 여전히 NOT NULL · 새 `notesArePerSchemaAndMerge`: `CREATE TABLE s1.emp (a INT NOT NULL); CREATE TABLE s2.emp (a INT NOT NULL); ALTER TABLE s2.emp MODIFY a BIGINT;` → `r.notes()` 열쇠가 `S2.EMP` 하나(값에 `AMBIGUOUS`), `S1.EMP`·`EMP` 없음. `DdlGenTest.sizesDefaultsKeysIndexNames` 에 pg 사례 — 원본 mariadb `longtext`(길이 4294967295) 유니크 컬럼 → 대상 postgresql 이면 `TEXT` 이고 경고에 「길이 255」 가 없다(대상 oracle 은 지금처럼 255 + 경고). RED: NOT NULL 풀림 · 열쇠 `EMP` · `VARCHAR(255)`
- 의도된 골든 변화: 없음(빠른 레인). `--db` 의 `ddlGenWarnings` 가 pg 대상에서 **주는 것**은 `V-67`
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,DdlGenTest,AlterGenTest,DtoGeneratorTest,InsertRoutesTest,GenRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 7-35. 레인 db + corpus
- 사다리: 스키마 붙은 표의 notes 를 읽는 기존 시험이 빨개지면(열쇠가 `EMP` → `S.EMP`) 그 시험의 열쇠를 `DdlReader.noteKey` 로 바꾼다(단언 값은 그대로)

### 6-48 — SqlTables: `;` 없는 권한 문장 · `IF(` 함수 (기존 행)
- 대상: `core/analyze/SqlTables.java` · `SqlTablesTest`(+ `golden/analyze/sql-tables.json`)
- 변경:
  - `withoutDcl:155-169` 를
    ```java
    private static List<String> withoutDcl(List<String> t) {
        List<String> out = new ArrayList<>(t.size());
        boolean start = true;
        boolean skip = false;
        boolean grantee = false; // 받는 쪽(TO — GRANT·DENY, FROM — REVOKE)을 지났나
        for (String x : t) {
            if (start) {
                skip = DCL.contains(x);
                grantee = false;
            }
            if (skip && ("GO".equals(x) || grantee && VERBS.contains(x))) {
                skip = false; // 6-48 — ; 없는 권한 문장은 GO 나 받는 쪽 뒤 다음 문장 동사에서 끝난다(권한 목록의 SELECT·INSERT 는 받는 쪽 앞이다)
            }
            grantee = grantee || skip && ("TO".equals(x) || "FROM".equals(x));
            start = ";".equals(x);
            if (!skip || start) {
                out.add(x);
            }
        }
        return out;
    }
    ```
  - `listAfter:276` — `return now && !LIST_END.contains(x);` → `return now && (!LIST_END.contains(x) || "IF".equals(x) && at(t, i + 1, "("));`(MySQL `IF(…)` 함수는 절 끝이 아니다)
- 시험(먼저): `SqlTablesTest.CASES` 에 셋 — `{"grantNoSemicolon", "select", "GRANT SELECT, INSERT ON T TO U1 SELECT A FROM B"}` → 표 `B` 하나 · `{"grantThenGo", "select", "GRANT SELECT ON T TO U1\nGO\nSELECT A FROM C"}` → `C` · `{"ifFunctionInJoin", "select", "SELECT * FROM A JOIN B ON IF(A.X > 0, A.X, 0) = B.Y, C"}` → `A`·`B`·`C`. RED: 앞 둘 표 0 · 셋째 `C` 없음. 기존 `revokeFromUsers`·`grantThenSelect` 그대로
- 의도된 골든 변화: `sql-tables.json` 에 사례 셋이 **더해지는 것만**
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlTablesTest,SqlTokensTest,MapperIndexTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus

### 5-119 — `${}`: SQL 주석 안도 다시 본다 (기존 행 · 사용자 (가))
- 대상: `src/main/resources/check/rules.yaml`(한 줄) · `core/check/RegexRule.java` · `core/check/MyBatisRules.java` · `CheckRulesTest` · 픽스처 `fixtures/check/mybatis/NegMapper.xml`·`PosMapper.xml` · `golden/check/mybatis.json`
- 결정(사용자 2026-11-21): MyBatis 는 SQL 주석 안 `${}` 도 치환한다 — 줄 주석(`-- ${x}` — 값의 줄바꿈으로 탈출)·블록 주석(`/* ${x} */` — 값의 `*/` 로 탈출)·힌트(`/*+ … ${ix} */`) 셋 다 삽입 벡터다. `security.sqlDollar`(error)는 XML 주석 `<!-- -->` 밖의 `${` 를 전부 잡는다(5-118 을 되돌린다 — 순수본과 다시 같다). 두 벌 판정이 어긋나지 않게 `mybatis.dollar`(warn)도 SQL 주석 안을 본다
- 변경:
  - `rules.yaml:225` → `params: {when: '<(?:mapper|sqlMap)\b'}   # 5-44a — MyBatis·iBATIS 매퍼만(pom·logback·Spring 설정의 ${…} 는 속성 자리표시자) · 5-119 — SQL 주석(-- · /* */ · 힌트) 안도 본다: MyBatis 는 주석 안 ${} 도 치환한다`
  - `RegexRule` — 필드 `sqlComments`·생성자의 그 줄·`cleaned(Source)` 를 지우고 `applyText:45` 를 5-118 앞 꼴(`skipped(s).split("\n", -1)`)로
  - `MyBatisRules.characters:244` — `check("mybatis.dollar", DOLLAR, sql, endLine);` → `check("mybatis.dollar", DOLLAR, text, endLine);`(주석을 안 지운 조각 — 줄 수는 같다). 클래스 javadoc 의 `mybatis.dollar` 설명에 「SQL 주석 안도(5-119)」
  - 픽스처 — `NegMapper.xml:8-9` 의 두 줄(`/* ${x} 블록 주석 */` · `-- AND ${y} 줄 주석`)을 `PosMapper.xml` 의 새 문장 `<select id="commented">` 로 옮기고 힌트 줄 `SELECT /*+ INDEX(t ${ix}) */ 1 FROM t` 을 더한다(음성 파일에는 `#{}` 만 쓰는 문장이 남는다)
- 시험(먼저): `CheckRulesTest.sqlDollarSkipsSqlComments` 를 `sqlDollarSeesSqlComments` 로 이름을 바꾸고 javadoc 을 「5-119 — SQL 주석 안 ${} 도 security.sqlDollar 다(MyBatis 가 치환한다). XML 주석만 뺀다」 로, 글에 `<!-- ${z} -->` 줄과 힌트 줄을 더해 기대를 「XML 주석 줄만 빠진 전부」 로(지금 글 기준 `[5, 6, 7]` + 더한 힌트 줄). `MyBatisRulesTest.fixturesGolden` 이 `PosMapper.xml` 새 세 줄을 `mybatis.dollar` 로 낸다. RED: `[7]` · 새 줄 없음
- 의도된 골든 변화: `golden/check/mybatis.json` — `PosMapper.xml` 에 `mybatis.dollar` 셋이 **더해지는 것만**(그 문장이 `SELECT … FROM t` 꼴이라 다른 규칙 줄이 같이 생기면 문장을 고쳐 `mybatis.dollar` 만 남긴다) · `pure-rules.json` **diff 0** · `fixtures.json` diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,PureRulesJsonTest,MyBatisRulesTest,CheckStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus
- 사다리: `CheckStoreTest.runnerOverFixtureFolder` 가 픽스처 폴더의 지적 수를 단언하면 그 수는 더한 줄만큼 오른다 — 이 행의 의도된 변화다(수와 이유를 이력에)

### 2-47 — 양식 합계: 안 늘린 배열 수식을 알린다 (기존 행)
- 대상: `core/report/FormulaRange.java` · `core/report/XlsxFiller.java` · `core/deliverable/DeliverableService.java` · `XlsxFillerTest` · `DeliverableServiceTest`
- 결정: 알림 통로는 `DeliverableService.Result.skipped` 다 — 화면 「건너뜀」·CLI `[건너뜀]`·작성안내 「건너뛴 것」 에 이미 보인다(새 필드·화면 변경 없음). 뒤 행 `2-48`·`2-49` 도 이 통로를 쓴다
- 변경:
  - `FormulaRange.widen` → `static int widen(…)` — 돌려주는 값 = 범위가 맞았는데 `setCellFormula` 가 `IllegalStateException` 을 던져 그대로 둔 수식 수. `widened` 가 그 경우를 구별하게 `boolean` 대신 `int`(0 = 해당 없음 · 1 = 넓힘 · 2 = 배열 수식이라 못 넓힘) 또는 out 카운터 — 메서드 수를 늘리지 않는 쪽으로
  - `XlsxFiller.fill` 셋 — 반환 `void` → `List<String>`(알림 글, 없으면 빈 목록). `:95` `int kept = FormulaRange.widen(…);` → 0 보다 크면 `"배열 수식 합계 " + kept + "개는 범위를 안 넓혔다 — 손으로 고친다"`. `fill(4)` 복잡도 25·중첩 5 는 기준선 — 목록 만들기는 도우미로(`private static List<String> notes(int kept)`)
  - `DeliverableService.fill(…)`(private 래퍼 `:300-307`)가 그 목록을 돌려주고, `build:126` `fill(templateDir, m, d, out);` → `fill(templateDir, m, d, out).forEach(n -> skipped.add(no + " — " + n));`(분기 없음 — `build(8)` 복잡도 22 = 기준선)
- 시험(먼저): `XlsxFillerTest.arrayFormulaDoesNotStopFill` 에 — `fill` 이 돌려준 목록이 한 줄이고 `배열 수식 합계 1개` 로 시작. 일반 합계 양식(`…widens…` 시험)은 빈 목록. RED: 반환 형식 없음(컴파일)
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='XlsxFillerTest,DeliverableServiceTest,DeliverableRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus(`core/report`·`core/deliverable`)

### 0-105 — JavaViews: out 인자를 상자 형식으로 (기존 행)
- 대상: `core/analyze/JavaViews.java` · `core/analyze/JavaGraph.java`(`views`) · `JavaGraphTest`(골든 그대로)
- 변경: `JavaViews` 에 `static final class Flag { boolean on; }` · `value(MethodDeclaration m, Expression e, Set<String> out, Flag dynamic)` · `assigned(…, Flag dynamic)` · 몸의 `dynamic[0] = true;` 넷(`:62·80·83·96`) → `dynamic.on = true;` · 넘기는 넷(`:53·58·74·104`)은 그대로 `dynamic`. `JavaGraph.views:910` `boolean[] dynamic = {false};` → `JavaViews.Flag dynamic = new JavaViews.Flag();` · `:932` `if (dynamic[0])` → `if (dynamic.on)`. `views` 의 `boolean[] badShape` 는 안 고친다(JavaGraph 는 PMD 기준선에 `UseVarargs` 가 있다)
- 시험: 새 시험 없음 — `JavaGraphTest` 골든 다섯(`java`·`jpa`·`qdsl`·`jdbc`·`generic`)과 `viewShapeIsNotAView` 가 그대로 초록. `self-check.txt` 의 `JavaViews.java|…value(4)` 줄 그대로
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-56(같은 파일 `JavaGraph`). 레인 corpus

### 0-107 — 본문 길이: chunked 거절 · 상한 (G1-K15 · G1-K14)
- 대상: `web/LocalOnly.java` · `LocalOnlyTest`
- 사실: Javalin 의 `maxRequestSize` 와 `LocalOnly.check:68` 의 Content-Type 판정이 둘 다 `Content-Length` 에 걸려 있다 — chunked 는 `text/plain` 도 지나고(K14) 상한도 없다(K15, 힙 256m 에서 OOM). multipart 는 `Content-Length` 가 있어도 Javalin 상한을 안 탄다
- 결정: 본문이 있는 메서드에 `Transfer-Encoding` 이 오면 **411 로 거절**한다(자기 화면 `fetch` 와 CLI `LocalApp.send` 는 늘 `Content-Length` 를 보낸다 — `LocalApp:110`). `Content-Length` 가 `App.MAX_BODY`(32MB) 를 넘으면 본문 형식과 무관하게 413
- 변경: `check:68-74` 의 `if (ctx.contentLength() > 0) { … }` 블록을 `body(ctx);` 한 줄로 바꾸고
  ```java
  /** 본문 — 길이 없는 chunked 는 거절(상한·형식 판정이 길이에 걸려 있다, G1-K14·K15) · 길이가 상한을 넘으면 413 · 있으면 JSON·multipart 만 */
  private static void body(Context ctx) {
      if (ctx.header("Transfer-Encoding") != null) {
          reject(ctx, new HttpResponseException(HttpStatus.LENGTH_REQUIRED.getCode(), "본문 길이(Content-Length)가 있어야 한다"), "transfer-encoding");
      }
      if (ctx.contentLength() > App.MAX_BODY) {
          reject(ctx, new io.javalin.http.ContentTooLargeResponse(), "content-length");
      }
      if (ctx.contentLength() > 0) {
          String type = lower(ctx.contentType());
          if (!type.startsWith("application/json") && !type.startsWith("multipart/form-data")) {
              reject(ctx, new HttpResponseException(HttpStatus.UNSUPPORTED_MEDIA_TYPE.getCode(), "본문은 application/json 이어야 한다"), "content-type");
          }
      }
  }
  ```
  클래스 javadoc 2번 항목 끝에 「길이 없는 본문(chunked)은 411, 32MB 넘는 길이는 413」. `check(1)` 복잡도 16 줄은 내려가 기준선에서 빠진다(갱신)
- 시험(먼저): `LocalOnlyTest`(그 클래스의 원시 소켓 도우미 `raw(method, path, host, extraHeaders, body)` — 본문이 null 이면 머리만 보낸다. 판정은 머리만으로 난다) — `chunkedBodyIsRefused`: `raw("POST", "/api/fs/out", self(), "Transfer-Encoding: chunked\r\nContent-Type: text/plain\r\n", null)` 와 `Content-Type: application/json` 둘 다 411 · `oversizedLengthIs413`: `raw("POST", "/api/dict/org/import", self(), "Content-Type: multipart/form-data; boundary=x\r\nContent-Length: 34603008\r\n", null)` → 413. 기존 `textPlainBodyIsRejected`(415)·`sameOriginWritePasses` 그대로. RED: 200 또는 400(본문 없음) · 413 아님
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='LocalOnlyTest,TextRoutesTest,DictRoutesTest,FsRoutesTest,SmokeHtmlUnitTest,LocalAppTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 java
- 사다리: HtmlUnit 스모크가 411 로 빨개지면 — 그 요청이 `Transfer-Encoding` 을 보낸다는 뜻이다. 어느 화면 호출인지 이력에 적고 멈춘다(7절 — 거절 대신 「읽으며 세기」 로 바꿀지는 사람이 정한다)

### ② 분석·검사 Minor

### 6-58 — SqlTokens: 따옴표 친 예약어 이름 · 이름에 붙은 `${}` · MySQL `#` 주석 (C-TBL-KEYWORD · C-TBL-DOLLAR · C-TBL-HASH)
- 대상: `core/text/SqlTokens.java` · `core/analyze/SqlTables.java`(`table` 한 가지) · `SqlTokensTest` · `SqlTablesTest`(+ 골든)
- 사실: `quoted:161-172` 가 따옴표를 벗겨 대문자로만 내 `` FROM `order` `` 가 예약어 `ORDER` 와 같은 낱말이 된다(표가 미해결 없이 빠짐). 낱말 가지(`next:97-104`)가 `$` 를 낱말 글자로 먹어 `TB_LOG_${ym}` 이 표 `TB_LOG_$` 가 된다. `#` 줄 주석을 아무도 안 지워 `FROM T1 # FROM T9` 의 `T9` 가 표가 된다
- 변경:
  - `Tok` — `record Tok(String s, int at, boolean quoted)` + `public Tok(String s, int at) { this(s, at, false); }`. `quoted(…)` 가 만드는 토큰만 `quoted = true`
  - `tokens(String)`(글자 목록) — 따옴표 친 토큰이 `STOP` 에 들고 **바로 앞 토큰이 `.` 이 아니면** `'"' + s + '"'` 꼴로 낸다(그 밖은 지금처럼 글자만 — `sch."ORDER"` 의 꼬리는 지금도 표로 읽힌다). `scan` 이 주는 `Tok.s` 는 안 바꾼다(`SqlLint` 는 그대로)
  - `next` 의 낱말 가지 — 낱말을 `wordEnd(s, i)`(낱말 글자가 이어지되 `bindAt` 자리에서 멈춘다)까지 읽고, 바로 뒤가 `bindAt` 이면 **낱말 + 바인드를 `?` 토큰 하나로**(위치 `i`, 끝은 그 `}` 다음) 낸다. 아니면 지금처럼 낱말
  - `next` 의 낱말 가지 **앞에** `#` 주석 가지 — `c == HASH && hashComment(s, i)` 면 줄 끝까지 건너뛴다(토큰 없음). `private static boolean hashComment(String s, int i)` = 「뒤가 공백·탭·줄 끝·글 끝」 이고 「앞이 글 머리이거나 낱말 글자가 아님」(`#{x}`·`#TEMP`·`COL#`·`A#B` 는 주석이 아니다)
  - `SqlTables.table:309` `} else if (word(x) && !STOP.contains(x)) {` → `} else if (tableName(x)) {` · 그 가지 안 `t.get(k)` 를 표 이름으로 쓰는 두 자리는 `unq(t.get(k))` · `private static boolean tableName(String x) { return word(x) && !STOP.contains(x) || quotedStop(x); }` · `quotedStop(x)` = `x.length() > 2 && x.charAt(0) == '"'` · `unq` = 그 따옴표 둘을 뗀 글. `chainEnd`·`isFunction` 은 그대로(따옴표 꼴 낱말은 사슬 한 마디)
- 시험(먼저): `SqlTokensTest` 새 `quotedKeywordBindSuffixHashComment` — `tokens("SELECT * FROM `order` o")` = `[SELECT, *, FROM, "ORDER", O]` · `tokens("FROM \"user_tb\"")` = `[FROM, USER_TB]`(예약어가 아니면 그대로) · `tokens("FROM TB_LOG_${ym} WHERE A = #{a}")` = `[FROM, ?, WHERE, A, =, ?]` · `tokens("FROM T1 # FROM T9\nWHERE X = 1")` = `[FROM, T1, WHERE, X, =, 1]` · `tokens("FROM #TEMP WHERE COL# = 1")` = `[FROM, #TEMP, WHERE, COL#, =, 1]`. `SqlTablesTest.CASES` 에 넷 — `quotedKeywordTable`(``SELECT * FROM `order` o JOIN `user` u ON o.ID = u.ID``) → `ORDER`·`USER` · `bracketKeywordTable`(`SELECT * FROM [ORDER] A JOIN S."GROUP" G ON A.ID = G.ID`) → `ORDER`·`GROUP` · `bindSuffixTable`(`SELECT * FROM TB_LOG_${ym}`) → 표 0·미해결 `table` · `hashComment`(`SELECT * FROM T1 # FROM T9\nWHERE X = 1`) → `T1` 하나. RED: `ORDER` 없음 · `TB_LOG_$` · `T9`
- 의도된 골든 변화: `sql-tables.json` 에 사례 넷이 **더해지는 것만** · `golden/check/sql-lint.json` diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlTokensTest,SqlTablesTest,SqlJoinsTest,SqlLintTest,MapperIndexTest,CheckRulesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-48(같은 골든 파일). 레인 corpus
- 사다리: `sql-lint.json` 이 바뀌면 — 그 사례가 「낱말 + 바인드」 나 `# ` 를 품었는지 본다. 품었으면 의도된 변화(이력에), 아니면 고침이 넓다

### 6-59 — SqlTables: 문장마다 WITH 이름 · 여러 표 UPDATE 의 SET 대상 (C-TBL-CTE · C-TBL-MULTIUPD)
- 대상: `core/analyze/SqlTables.java`(`Scan`·`extract`·`track`) · `SqlTablesTest`(+ 골든)
- 사실: `extract:213` `refs.keySet().removeAll(cteNames(t))` 가 글 전체의 WITH 이름을 글 전체 표에서 뺀다 — 다른 문장의 같은 이름 진짜 표가 빠진다. MySQL `UPDATE A a JOIN B b ON … SET b.X = …` 의 `B` 가 R 로만 남는다. `extract(2)` 복잡도 17 은 기준선
- 변경(도우미는 전부 중첩 클래스 `Scan` 안에 — `SqlTables` 자체에는 메서드를 안 더한다):
  - `Scan` — 생성자를 `Scan(String verb)` 로, 필드
    ```java
    /** 지금 문장의 표 — 문장이 끝나면 그 문장의 WITH 이름만 빼고 all 에 합친다(C-TBL-CTE) */
    final Map<String, EnumSet<Crud>> stmt = new TreeMap<>();
    final Map<String, EnumSet<Crud>> all = new TreeMap<>();
    int from;
    final Consumer<Layer> settle = l -> SqlTables.settle(stmt, l);
    ```
    ```java
    /** 문장 하나를 닫는다 — 층 정리 · SET 대상 · 그 문장의 WITH 이름 빼기 · 전체에 합치기. end = 그 문장 끝(다음 문장 첫 토큰 자리) */
    void close(List<String> t, int end) {
        on.forEach(settle);
        setTargets(t, end);
        stmt.keySet().removeAll(cteNames(t.subList(from, end)));
        stmt.forEach((k, v) -> all.computeIfAbsent(k, n -> EnumSet.noneOf(Crud.class)).addAll(v));
        stmt.clear();
        from = end;
    }

    /** MySQL 여러 표 UPDATE — 바닥 동사가 UPDATE 일 때 괄호 밖 SET 절(다음 WHERE·FROM 앞)의 「이름.컬럼 =」 이 가리키는 표(별칭이면 진짜 표)에 U(C-TBL-MULTIUPD) */
    private void setTargets(List<String> t, int end)
    ```
    `setTargets` — `on.get(0).verb` 가 `UPDATE` 가 아니면 끝. `from..end` 를 괄호 깊이 0 에서 훑어 `SET` 뒤부터 `WHERE`·`FROM` 앞까지, 앞 토큰이 `SET` 이나 `,` 이고 `t[i+1]` 이 `.` 이고 `t[i+3]` 이 `=` 인 `t[i]` 를 `on.get(0).aliases.getOrDefault(이름, 이름)` 으로 풀어 `stmt` 에 그 표가 있으면 `U` 를 더한다
  - `extract` — `Scan sc = new Scan(verb);` · `table(t, i, sc, sc.stmt, unresolved)` · `:212-213` 두 줄을 `sc.close(t, t.size());` 한 줄로 · `refs.forEach(…)` → `sc.all.forEach(…)`(지역 `refs` 는 지운다)
  - `track:240` — `sc.reset(…)` 앞 줄에 `sc.close(t, i);`
- 시험(먼저): `SqlTablesTest.CASES` 에 셋 — `{"cteNameIsATableElsewhere", "select", "WITH ORDERS AS (SELECT 1 FROM CUST) SELECT * FROM ORDERS; SELECT * FROM ORDERS O JOIN CUST C ON 1=1"}` → `CUST` R · `ORDERS` R(둘째 문장의 진짜 표) · `{"multiTableUpdateJoin", "update", "UPDATE TB_A A JOIN TB_B B ON A.ID = B.ID SET B.X = A.X WHERE A.Y = 1"}` → `TB_A` U · `TB_B` RU · `{"multiTableUpdateComma", "update", "UPDATE TB_A a, TB_B b SET b.X = 1, a.Z = 2 WHERE a.ID = b.ID"}` → `TB_A` U · `TB_B` RU. RED: `ORDERS` 없음 · `TB_B` R. 기존 `cte`·`cteNested`·`cteInsert`·`multiStatement`·`updateAliasMssql`·`updateSetFromCommas` 그대로
- 의도된 골든 변화: `sql-tables.json` 에 사례 셋이 **더해지는 것만** · 기존 사례 diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlTablesTest,SqlJoinsTest,MapperIndexTest,JpaIndexTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-58. 레인 corpus
- 사다리: `extract(2)` 값이 17 에서 달라지면 — 내렸으면 갱신, 올랐으면 `extract` 에 든 분기를 `Scan` 으로 옮긴다

### 6-60 — SqlJoins: SET 대입은 조인 등식이 아니다 (C-JOIN-SET)
- 대상: `core/analyze/SqlJoins.java` · `SqlJoinsTest`(+ `golden/analyze/sql-joins.json`)
- 결정: `SET B.X = A.X`(UPDATE·MERGE 의 값 복사)는 두 표의 관계 근거가 아니다 — 조인 등식에서 뺀다
- 변경: `extract` 머리에서 `boolean[] set = setClause(t);` 를 만들고 루프의 `if (left == null) {` 를 `if (left == null || set[i]) {` 로
  ```java
  /** SET 절 안 토큰 표시 — SET 다음부터 WHERE·FROM·WHEN·; 앞까지(괄호 안 서브쿼리의 FROM 도 끝낸다 — 그 안 상관 등식은 조인이다) */
  static boolean[] setClause(List<String> t)
  ```
  클래스 javadoc 에 「SET 대입은 등식으로 안 센다(C-JOIN-SET)」
- 시험(먼저): `SqlJoinsTest.shapes` 의 맵에 둘 — `sql.put("updateSet", "UPDATE TB_A A JOIN TB_B B ON A.ID = B.ID SET B.X = A.X")` → 조인 하나(`ID`) · `sql.put("mergeSet", "MERGE INTO T USING S ON (T.ID = S.ID) WHEN MATCHED THEN UPDATE SET T.X = S.X WHEN NOT MATCHED THEN INSERT (ID) VALUES (S.ID)")` → 조인 하나(`ID`). RED: 둘(`X` 도)
- 의도된 골든 변화: `sql-joins.json` 에 사례 둘이 **더해지는 것만**. corpus `analyze-egov-joins.json` `equalities`·`deliverable-egov-relations.json` 이 **주는 것**은 `V-67a`
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlJoinsTest,MapperIndexTest,RelationsTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus

### 6-61 — MapperIndex: 자기를 끼우는 조각 · 취소 (C-MAP-INCLUDE)
- 대상: `core/analyze/MapperIndex.java` · `core/analyze/AnalyzeRunner.java` · `MapperIndexTest`
- 사실: `include:246-278`·`dynamicInclude:222-243` 이 깊이 5 까지 무조건 펼쳐 자기를 n 번 끼우는 `<sql>` 이 n^6 배가 된다(OOM). `scan` 은 취소를 안 듣는다
- 변경:
  - `include(Raw r, String text, …, Set<String> seen, int depth)`(6 인자 — 그대로)는 `expand(r, text, fragments, unresolved, seen, depth, new HashSet<>())` 로 넘긴다. `private static String expand(…, Set<String> open)` 가 지금 몸이고, 조각을 펼치기 앞에 `open.add(fk)`(`fk` = 실제로 맞은 조각 열쇠 — `r.file + "|" + key` 또는 `key`)가 거짓이면 **순환**: `note(unresolved, seen, "missing", r.file, r.line, "include " + key);` 하고 그 자리를 비운다. 펼친 뒤 `open.remove(fk)`
  - `dynamicInclude` 도 같은 꼴 — 공개 꼴은 그대로, 몸은 `open` 을 받는 private 로. 순환이면 거짓
  - `scan(List<Source> xml)` → `scan(xml, null)` · 새 `public static Index scan(List<Source> xml, Runnable tick)` — `parse` 의 파일 루프와 `merge` 의 문장 루프에서 `tick` 이 null 이 아니면 `tick.run()`(`parse(xml)`·`merge(…)` 에 `tick` 인자를 더한 private 꼴. `SqlProbe` 가 부르는 `parse(List<Source>)` 는 그대로 둔다)
  - `AnalyzeRunner.run` — `:144` 앞에 `Runnable tick = ctx == null ? null : ctx::checkCancelled;` 를 두고 `MapperIndex.scan(xml, tick)` · `JavaGraph.scan(java, naming, jpa, tick, index.namespaces())`(그 줄에 있던 같은 삼항을 지운다 — 분기 수 그대로)
- 시험(먼저): `MapperIndexTest.selfIncludeIsCutAndNoted` — `<mapper namespace="N"><sql id="a">` + `<include refid="a"/>` 서른 개 + `X</sql><select id="s">SELECT 1 FROM T <include refid="a"/></select></mapper>` 를 `assertTimeoutPreemptively(5초, () -> MapperIndex.scan(…))` → `idx.get("N.s")` 표 `T` · 미해결에 `missing` detail `include N.a`. `scanHonoursTick` — `tick` 이 셋째 호출에서 `IllegalStateException` 을 던지면 `scan(xml, tick)` 이 그 예외로 끝난다. RED: 시간 초과(또는 OOM)
- 의도된 골든 변화: `golden/analyze/mapper-index.json` diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='MapperIndexTest,SqlProbeTest,AnalyzeStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-57(같은 파일). 레인 corpus

### 6-62 — SqlProbe: 끝 ORDER BY 정규식 · 끝 `;` 뒤 주석 · 같은 이름 컬럼 (C-PROBE-REGEX · C-PROBE-SEMI · C-PROBE-DUPCOL)
- 대상: `core/analyze/SqlProbe.java` · `SqlProbeTest`
- 변경:
  - 정규식 — `TAIL_ORDER_BY` 를 `Pattern.compile("(?is)\\border\\s+by\\b")`, `TAIL_LIMIT` 를 `Pattern.compile("(?is)\\blimit\\b")` 로 바꾸고, 쓰는 두 자리(`wrap:187·191`)는 `tail(sql, "()'")`·`tail(sql, "()")` 에서 찾는다 — `private static String tail(String sql, String stops)` = `stops` 글자 가운데 마지막으로 나온 자리 뒤의 글(없으면 전체)
  - 끝 `;` — `wrap:183-186` 의 `while (sql.endsWith(";"))` 를 `sql = withoutTailSemicolon(sql);` 로:
    ```java
    /** 끝 ; 와 그 뒤 주석을 뗀다 — 주석을 같은 길이 빈칸으로 바꾼 글에서 끝을 잰다(C-PROBE-SEMI). ; 없는 끝 주석은 그대로 */
    private static String withoutTailSemicolon(String sql) {
        String s = sql;
        String bare = RegexRule.stripComments(s, "sql").stripTrailing();
        while (bare.endsWith(";")) {
            s = s.substring(0, bare.length() - 1).strip();
            bare = RegexRule.stripComments(s, "sql").stripTrailing();
        }
        return s;
    }
    ```
  - 같은 이름 컬럼 — `probe`(`:200-245`)에서 감싼 SQL 이 `SQLException` 으로 실패했을 때, 방언이 `h2`·`mssql`·`sqlserver`·`mysql`·`mariadb`·`maria` 면 **EXISTS 꼴로 한 번 더** 묻는다: `existsForm(text, dialect, binds, slot)` = `wrap` 과 같은 앞 손질(바인드 자리·끝 `;`·mssql `OFFSET 0 ROWS`) 뒤 `"SELECT 1 " + (mysql 계열이면 "FROM DUAL ") + "WHERE 1=0 AND EXISTS (\n" + sql + "\n)"`. 둘째가 통과하면 `PASS`, 둘째도 실패하면 **첫 오류**로 `ERROR`(지금과 같은 글). 거절(`REFUSED`)은 다시 안 묻는다. `wrap` 의 앞 손질을 `prepared(text, dialect, binds, slot)` 로 빼 둘이 같이 쓴다(주석 `// tb-ignore security.sqlConcat …` 을 새 이음 줄에도 붙인다)
- 시험(먼저): `SqlProbeTest` — `wrapKeepsCommentsAndFixesMssqlOrderBy` 에 `wrap("SELECT A FROM T; -- 끝", "h2", b)` = `SELECT 1 FROM (\nSELECT A FROM T\n) tb_probe WHERE 1=0` 과 `wrap("SELECT A FROM T -- 끝 주석", …)` 이 지금 글 그대로 · `tailRegexIsLinear`: `"SELECT 1 FROM T " + "ORDER BY A ".repeat(20_000) + "(x)"` 를 mssql 로 `wrap` 이 5초 안 · `staticSelectsAreAskedOthersSkipped` 의 매퍼에 `<select id="dup">SELECT A.ID, B.ID FROM T A JOIN T B ON A.ID = B.ID</select>` 를 더해 `PASS`(h2). RED: `SKIP_REFUSED` 꼴 글 · 시간 초과 · `ERROR`
- 의도된 골든 변화: 없음(빠른 레인). `--db` 의 `egovProbe`·`ruoyiProbe` 통과가 **느는 것**·`SKIP_REFUSED` 가 **주는 것**은 `V-67`
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlProbeTest,AnalyzeRoutesTest,ReadOnlyJdbcTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-61. 레인 db + corpus(`SqlProbe.java` 는 db 레인 목록에 있다 — `V-67` 이 실물 DB 로 다시 잰다)
- 사다리: h2 가 `SELECT 1 WHERE 1=0 AND EXISTS (…)` 를 못 받으면 h2 도 `FROM DUAL` 꼴로

### 6-63 — 한 파일의 `databaseId` 변형 (C-PROBE-DBID)
- 대상: `core/analyze/MapperIndex.java`(`Raw`·`Handler`) · `core/analyze/SqlProbe.java`(`pick`) · `SqlProbeTest` · `MapperIndexTest`(`new Raw(` 한 자리)
- 변경:
  - `record Raw(…, boolean dynamic)` → 끝에 `String databaseId`(생성자를 따로 적지 않는다 — `metric.params` 는 적은 생성자만 센다). `Handler` 의 문장 시작(`:318-326`)에서 `databaseId = atts.getValue("databaseId");`(문장 — 분기 아님), `endElement` 의 `new Raw(…, dyn, databaseId)`. `MapperIndexTest:95` 의 `new MapperIndex.Raw(…)` 끝에 `null`
  - `SqlProbe.pick` — `if (words.isEmpty()) { return rs.get(0); }` 다음에
    ```java
    MapperIndex.Raw byId = byDatabaseId(rs, words);
    if (byId != null) {
        return byId;
    }
    ```
    ```java
    /** 변형이 databaseId 로 갈린 것이면 — 방언 낱말이 든 databaseId, 없으면 databaseId 없는 것. 아무도 databaseId 가 없으면 null(파일 이름으로 가른다) */
    private static MapperIndex.Raw byDatabaseId(List<MapperIndex.Raw> rs, List<String> words)
    ```
- 시험(먼저): `SqlProbeTest.databaseIdVariantIsPicked` — 한 파일 `<select id="q" databaseId="oracle">SELECT NOPE FROM T</select><select id="q" databaseId="mysql">SELECT A FROM T</select>` 를 방언 `mysql` 로 → `PASS`, `oracle` 로 → `ERROR`(없는 컬럼), `postgresql` 로 → `SKIP_NO_DIALECT_FILE`(맞는 변형도 databaseId 없는 변형도 없다). RED: 셋 다 `SKIP_NO_DIALECT_FILE`
- 의도된 골든 변화: `golden/analyze/mapper-index.json` — `Raw` 가 골든에 실리면 `databaseId` 키가 **더해지는 것만**(안 실리면 diff 0)
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlProbeTest,MapperIndexTest,AnalyzeStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-62. 레인 db + corpus

### 5-120 — RegexRule: 텍스트 블록 · `<script>` 주석 (D-textBlock · D-jspScriptComment)
- 대상: `core/check/RegexRule.java` · `docs/pure-code-check.md`(4.2절 두 줄) · `CheckRulesTest`
- 사실: `cLike:161-200` 이 `"""` 를 따옴표 셋으로 읽어 텍스트 블록 안 `/*` 가 파일 끝까지 지운다. jsp·html 꼴은 `<script>` 안 `//`·`/* */` 를 안 지워 주석 처리한 `alert`·`console.log`·`debugger` 가 지적된다. `cLike(2)` 복잡도 27 은 기준선
- 변경:
  - `cLike` — 따옴표를 여는 가지(`} else if (c == '"' || c == '\'' || c == '`' && slashes) {`) **앞에** `} else if (slashes && c == '"' && s.startsWith("\"\"\"", i)) { i = textBlock(s, i, sb); }` · `private static int textBlock(String s, int i, StringBuilder sb)` — 닫는 `"""`(앞이 `\` 가 아닌)까지 그대로 `sb` 에 옮기고 그 다음 위치(안 닫히면 글 끝). **`cLike` 복잡도는 27 을 넘기지 않는다** — 줄 주석·블록 주석 가지의 몸(안쪽 루프)을 `lineComment(s, i, sb)`·`blockComment(s, i, sb)` 로 빼 자리를 만든다(값이 내리면 기준선 갱신)
  - `skipped:81-86` — jsp 꼴이면 `scriptComments(blankCommentScriptlets(t))`, `html`·`htm`·`xhtml` 이면 `scriptComments(t)`, 그 밖은 `t`(`scriptComments` 는 6-50 이 만든 것)
  - `docs/pure-code-check.md` 4.2절 — java 줄에 「텍스트 블록(`"""`) 안은 그대로(백엔드만 — 순수본 JS 는 옛 동작)」, jsp·html 줄에 「`<script>` 몸의 `//`·`/* */` 주석도(백엔드만)」
- 시험(먼저): `CheckRulesTest` 에 `textBlockDoesNotOpenAComment` — `"class A {\n String s = \"\"\"\n   /* 안 닫는 글\n   \"\"\";\n void m() { System.out.println(1); }\n}\n"` 를 `java` 로 `runText` → `common.sysout` 5행 · `RegexRule.stripComments(그 글, "java")` 의 줄 수가 같다. `commentedScriptLinesAreNotFlagged` — `"<html><body><script>\n// alert(1)\n/* console.log(x) */\n// debugger\nalert(2);\n</script></body></html>"` 를 `jsp` 로 → `common.alert` 5행 하나, `common.consoleLog`·`common.debugger` 없음. RED: 지적 0 · 넷
- 의도된 골든 변화: `golden/check/fixtures.json`·`pure-rules.json` diff 0(`PureCodeCheckTest` 와 견주는 픽스처에 텍스트 블록·스크립트 주석이 없다 — 탐색 확인). corpus `check-*`·`cleanup-*` 의 `common.alert`·`consoleLog`·`debugger` 수가 **주는 것**은 `V-67a`
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,CleanupTest,JspLinksTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 6-50 · 5-119. 레인 corpus
- 사다리: `CleanupTest` 의 console 지우기 시험이 빨개지면 — 주석 처리한 `console.log` 줄을 지우던 기대다. 이제 그 줄은 지적이 아니라 안 지운다 — 이 행의 의도된 변화(그 단언만 고치고 이력에)

### 5-121 — Ignores: 문자열 안 `tb-ignore` 는 표식이 아니다 (D-ignoreInString)
- 대상: `core/check/Ignores.java` · `CheckRulesTest`
- 변경: `Ignores.of` — 표식 후보를 처음 만났을 때 한 번 `String[] clean = IN_COMMENT.contains(lang) ? RegexRule.stripComments(s.text(), lang).split("\n", -1) : null;` 을 만들고, `clean` 이 null 이 아니면 **그 자리(`m.start(1)`)가 `clean[i]` 에서 빈칸일 때만** 표식으로 받는다(주석을 지운 글에서 빈칸 = 주석 안 — `MarkerFix.markerEdit` 와 같은 판정). `private static final Set<String> IN_COMMENT = Set.of("java", "js", "jsx", "ts", "tsx", "mjs", "cjs", "kt", "groovy", "css", "scss");` — jsp·xml·sql·html 은 한 파일에 주석 문법이 섞여 지금처럼 원문 줄에서만 본다. 판정은 도우미 `inComment(String[] clean, int line, int col)` 로
- 시험(먼저): `CheckRulesTest.ignoreMarkers` 에 — `hits.apply("System.out.println(\"// tb-ignore\");\n")` = `[1 common.sysout]` · `hits.apply("System.out.println(1); // tb-ignore common.sysout 사유\n")` = `[]` · `hits.apply("String s = \"-- tb-ignore\"; System.out.println(s);\n")` = `[1 common.sysout]`. RED: 첫·셋째가 `[]`
- 의도된 골든 변화: `golden/check/fixtures.json` diff 0(`ignore/` 픽스처에 문자열 안 표식이 없다) · `CheckStoreTest` 의 `suppressed` 6 그대로
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,CheckStoreTest,CleanupTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-120(텍스트 블록). 레인 corpus

### 5-122 — MyBatisRules: dupId 열쇠 · 문장 끝에서 주석 떼기 · 괄호 밖 WHERE (D-dupId · D-dollarFragment · D-noWhereSubquery)
- 대상: `core/check/MyBatisRules.java`(`Handler`) · `MyBatisRulesTest` · 픽스처 `fixtures/check/mybatis/`(양성·음성 줄) · `golden/check/mybatis.json`
- 사실: `ids` 열쇠가 맨 `id` — `databaseId` 가 다른 같은 id·`<sql id>` 와 같은 이름 문장이 error 오탐. `characters:240` 이 SAX 조각마다 SQL 주석을 지워 엔티티·CDATA 경계에서 주석·따옴표 상태가 끊긴다. `noWhere:229` 의 `WHERE` 는 괄호 깊이를 안 본다. `startElement(4)` 복잡도 17 은 기준선
- 변경:
  - dupId — `:195-201` 의 `String id = atts.getValue("id"); if (id != null && …) { … ids.putIfAbsent(id, line) …` 에서 열쇠만 `idKey(qName, atts)` 로: `private static String idKey(String tag, Attributes a)` = `("sql".equals(tag) ? "sql " : "") + a.getValue("id") + (a.getValue("databaseId") == null ? "" : " @" + a.getValue("databaseId"))`(분기 수 그대로)
  - 주석 떼기 — `characters` 는 **원문 조각**을 쌓는다: `segs.add(new int[] {stmtText.length(), Math.max(1, endLine - newlines(text, 0, text.length()))}); stmtText.append(text); check("mybatis.dollar", DOLLAR, text, endLine);`(`sql` 지역 변수와 `selectStar` 호출을 지운다). `endElement` 의 `depth == 0` 가지를 `closeStatement();` 한 줄로 —
    ```java
    /** 문장이 닫힐 때 — 문장 전체에서 SQL 주석을 한 번 지우고(길이 그대로 — 위치·줄이 안 밀린다) 판정 셋(D-dollarFragment) */
    private void closeStatement() {
        String sql = RegexRule.stripComments(stmtText.toString(), "sql");
        selectStar(sql);
        noWhere(sql);
        sqlLint(sql);
    }
    ```
    `selectStar(String sql)` — `SELECT_STAR` 가 맞은 자리의 줄을 `lineAt(sql, m.start())` 로(한 줄에 한 건). `noWhere`·`sqlLint` 는 `stmtText` 대신 인자 `sql`
  - 괄호 밖 WHERE — `noWhere` 의 `!WHERE.matcher(sql).find()` → `!topWhere(sql)`: `private static boolean topWhere(String sql)` — `WHERE` 가 맞은 자리마다 그 앞 `(`·`)` 수로 깊이를 재 깊이 0 이하면 참
- 시험(먼저): 픽스처 셋을 더하고 `MyBatisRulesTest.fixturesGolden` 이 그 줄들을 낸다 —
  - `PosMapper.xml` 에 `<update id="subWhere">UPDATE users SET name = (SELECT n FROM other WHERE id = 1)</update>`(noWhere 양성 — WHERE 가 서브쿼리 안에만)
  - `NegMapper.xml` 에 `<select id="byDb" databaseId="oracle">SELECT id FROM t</select>`·`<select id="byDb" databaseId="mysql">SELECT id FROM t</select>`·`<sql id="byDb">id</sql>`(dupId 음성 — databaseId 가 다르고 `<sql>` 은 이름 공간이 따로)
  - `PosMapper.xml` 에 `<select id="frag">SELECT /* a &lt; b */ * FROM t</select>`(selectStar 양성 — 주석 안 엔티티가 조각을 끊어 지금은 `SELECT` 와 `*` 가 떨어져 안 잡힌다)
  RED: `subWhere` 줄 없음 · `byDb` 가 dupId 둘 · `frag` 줄 없음
- 의도된 골든 변화: `golden/check/mybatis.json` — 더한 픽스처 줄만(양성 둘이 **더해지고** 음성은 안 나온다) · 기존 줄 diff 0. corpus `mybatis.noWhere`(egov 40)가 **느는 것**은 `V-67a` — 새 줄은 `R-34` 가 손 판정
- 검증: `bash scripts/mvn.sh -q test -Dtest='MyBatisRulesTest,SqlLintTest,CheckRulesTest,CheckStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-119(같은 메서드). 레인 corpus
- 사다리: `frag` 픽스처가 다른 규칙(`mybatis.*` SqlLint)도 같이 내면 문장을 고쳐 `mybatis.selectStar` 만 남긴다. `CheckStoreTest` 의 픽스처 폴더 지적 수는 더한 줄만큼 오른다(의도)
- 한계(설계 문서에만): Oracle 인라인 뷰 갱신 `UPDATE (SELECT … WHERE …) SET …` 은 이제 noWhere 다(괄호 안 WHERE)

### 5-123 — 파싱 실패 수: 규칙·표식과 무관하게 (D-parseErrCount)
- 대상: `core/check/JavaRules.java` · `core/check/MyBatisRules.java` · `core/check/RuleSet.java`(`Run`) · `core/check/CheckRunner.java` · `CheckRunnerTest`
- 사실: `CheckRunner.parseErrors:211` 이 걸러진 지적(`*.parseError`)에서 센다 — 규칙을 끄거나 그 줄에 `tb-ignore` 가 있으면 0
- 변경:
  - `JavaRules`·`MyBatisRules` 에 각각 `private final Set<String> unparsed = new HashSet<>();` + `Set<String> unparsed()`. 파싱 실패 가지에서 `if (on.containsKey(PARSE_ERROR))` **앞에** `unparsed.add(s.rel());`
  - `RuleSet.Run` 에 `public int parseErrors()` — 두 집합의 합집합 크기(그 Run 이 만든 `JavaRules`·`MyBatisRules` 가 없으면 0)
  - `CheckRunner` — `parseErrors(out)` 두 자리(`:141·194`)를 `run.parseErrors()` 로, `private static int parseErrors(List<Finding>)` 를 지운다
- 시험(먼저): `CheckRunnerTest.parseErrorCountIgnoresRuleSwitchAndMarkers` — 깨진 Java 붙여넣기(`"class A { void m( { }"`)를 ① 기본 규칙 ② `java.parseError` 를 끈 프로필(`Map.of("java.parseError", false)`) ③ 첫 줄에 `// tb-ignore-next java.parseError x` 를 붙인 글로 `runText` → 셋 다 `parseErrors()` 1. RED: ②③ 이 0
- 의도된 골든 변화: 없음(`CheckStoreTest` 의 `parseErrors` 2 · `SelfCheckTest` 의 0 그대로)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRunnerTest,CheckStoreTest,CheckRoutesTest,CodeCliTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-122. 레인 corpus

### 5-124 — 규칙 덮어쓰기 꼴 검증 (D-overrideValidate)
- 대상: `core/check/Rule.java`(`Def.override`) · `core/check/RuleSet.java`(`validate`) · `CheckRulesTest`
- 사실: `params.max` 가 수가 아니어도 적재를 지나 검사 중 `NumberFormatException` 으로 작업이 FAILED. Boolean·Map 이 아닌 덮어쓰기, 글자 `enabled`, 목록 아닌 `globs` 는 말없이 무시
- 변경:
  - `Rule.Def.override` — 머리에서 꼴을 잰다(도우미 `private void shape(Object o)`): Boolean·Map 이 아니면 `IllegalArgumentException(id + ": 덮어쓰기는 true·false 또는 {enabled·severity·regex·globs·flags·skipComments·message·params}")` · Map 이면 `enabled`·`skipComments` 가 있는데 Boolean 이 아님 → `id + ": enabled·skipComments 는 true·false"` · `globs`·`flags` 가 있는데 목록이 아님 → `id + ": globs·flags 는 목록"` · `params` 가 있는데 Map 이 아님 → `id + ": params 는 맵"`
  - `RuleSet.validate` 끝에 — `Object max = d.params().get("max");` 가 있고 `Integer.parseInt(String.valueOf(max).trim())` 이 실패하면 `new BadRule(d.id() + ": params.max 는 정수")`(0-106 의 `BadRule`. `override` 가 던진 `IllegalArgumentException` 도 `load` 에서 `BadRule` 로 감싼다 — `load:107` 의 `d0.override(…)` 를 `overridden(d0, cc)` 도우미로 빼 거기서 try/catch. `load(2)` 복잡도 28 = 기준선 — 분기를 더하지 않는다)
  - 모르는 규칙 id 의 덮어쓰기는 지금처럼 무시한다(옛 프로필의 없어진 규칙 id 로 화면이 막히지 않게)
- 시험(먼저): `CheckRulesTest.badRegexOverrideIsRefused` 옆에 `malformedOverridesAreRefused` — `Map.of("common.todo", Map.of("params", Map.of("max", "abc")))` · `Map.of("common.todo", "off")` · `Map.of("common.todo", Map.of("enabled", "false"))` · `Map.of("common.todo", Map.of("globs", "*.java"))` 넷 다 `RuleSet.load` 가 `IllegalArgumentException`, 글이 `common.todo` 로 시작. RED: 넷 다 통과
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,CheckRoutesTest,ProfileRoutesTest,ProfileStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 0-106(`BadRule`). 레인 corpus
- 사다리: `profiles/example.yaml`·시험 프로필의 덮어쓰기가 새 검증에 걸리면 — 그 YAML 이 틀린 꼴이다. 예시 파일을 고치고(값의 뜻은 그대로) 이력에

### 5-125 — Taint: 같은 이름 지역 변수의 범위 (D-taintScope)
- 대상: `core/check/Taint.java`(`scan` 한 자리) · 새 `core/check/LocalScopes.java` · `TaintTest`
- 사실: 오염 집합이 이름 글자 열쇠다(`body:211-249`·`grow:321`) — 형제 블록·익명 클래스 몸이 같은 이름 지역 변수를 따로 선언하면 한쪽의 오염이 다른 쪽 상수에 묻어 error 등급 `security.sqlInjection`·`pathTraversal` 오탐
- 결정: 판정 로직은 안 고친다. 메서드를 보기 **앞에** 같은 이름으로 두 번 넘게 선언된 지역 변수를 선언마다 다른 이름으로 잠깐 바꾸고(`이름$tb2`·`$tb3`…), 판정이 끝나면 되돌린다. AST 는 같은 스레드의 다른 규칙이 뒤에 다시 쓰므로 반드시 되돌린다
- 변경:
  - 새 클래스
    ```java
    /** D-taintScope — 한 메서드 안 같은 이름 지역 변수를 선언마다 갈라 준다. 이름을 잠깐 바꾸고 restore 로 되돌린다 */
    final class LocalScopes {
        /** 바꾼 이름들 — restore() 가 원래 글자로 */
        static final class Renamed { void restore() }
        /** m 안에서 같은 이름으로 둘 넘게 선언된 지역 변수(람다 몸 밖 — Taint.outsideLambda 와 같은 범위)를 둘째 선언부터 「이름$tb<n>」 으로. 없으면 빈 Renamed */
        static Renamed split(CallableDeclaration<?> m)
    }
    ```
    알고리즘: ① `Taint.outsideLambda(m, VariableDeclarator.class)` 를 이름별로 묶어 둘 이상인 이름만 ② 그 이름의 선언을 **안쪽(깊은) 선언부터** 차례로 — 선언의 범위 노드 = 가장 가까운 `BlockStmt`·`ForStmt`·`ForEachStmt`·`TryStmt`·`SwitchEntry`·`CatchClause` 조상 ③ 그 범위 안에서 선언 **뒤에 오는**(시작 위치가 선언 뒤) `NameExpr` 가운데 식별자가 아직 원래 이름인 것과 선언 자신의 `SimpleName` 을 새 이름으로(`setIdentifier`) — 바꾼 `SimpleName` 과 옛 글자를 `Renamed` 에 적는다 ④ 첫 선언(가장 바깥·가장 앞)은 이름을 안 바꾼다. 매개변수는 안 바꾼다
  - `Taint.scan:171` 의 메서드 루프(`for (CallableDeclaration<?> m : cu.findAll(CallableDeclaration.class)) {`) 몸 전체(`Body b = body(m);` 부터 `k.needs().clear();` 까지)를 `LocalScopes.Renamed r = LocalScopes.split(m); try { …지금 몸… } finally { r.restore(); }` 로 감싼다. `ConstLink` 가 따로 부르는 `Taint.body` 는 안 고친다
- 시험(먼저): `TaintTest` 에 `sameNameInSiblingScopesDoesNotLeak` — `hits("if (a) { String q = request.getParameter(\"x\"); stmt.executeQuery(\"select \" + q); } else { String q = \"1\"; stmt.executeQuery(\"select \" + q); }")` 가 **첫 `executeQuery` 줄 하나만**(지금은 둘) · `anonymousClassLocalDoesNotLeak` — 바깥 `String p = request.getParameter("f");` 와 익명 `Runnable` 몸의 `String p = "fixed"; new java.io.File(p);` → 익명 몸 줄에 `pathTraversal` 없음 · `restoreLeavesNamesIntact` — `split` 뒤 `restore()` 하면 `m.toString()` 이 처음과 같다. RED: 둘째 줄도 지적
- 의도된 골든 변화: `golden/check/java.json`·`fixtures.json` diff 0(픽스처는 지역 변수 이름이 겹치지 않는다 — 탐색 확인). corpus `security.sqlInjection`·`pathTraversal` 수가 **주는 것**·`security-juliet.json`·`security-benchmark.json` 의 FP 가 **주는 것**은 `V-67a`(TP 가 주면 고침이 틀렸다 — 7절)
- 검증: `bash scripts/mvn.sh -q test -Dtest='TaintTest,JavaRulesTest,CheckRulesTest,CheckStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-123. 레인 corpus
- 사다리: `TaintTest`·`TaintLinkTest` 의 기존 시험이 빨개지면 — 이름을 글자로 견주는 자리(식의 `toString()`, 또는 `each` 로 넘긴 `Body` 를 루프 밖에서 다시 보는 `TaintLink`)가 `$tb` 꼬리를 본 것이다. 그 자리를 찾아 이력에 적고, 둘째 실패에서 `wip/5-125`

### ③ `5-114`

### 5-114a — 같은 소스 루트의 메서드 반환 형식 (사용자 (가))
- 대상: 새 `core/check/ReturnTypes.java` · 새 `ReturnTypesTest`
- 결정: 형식 해석기(symbol solver)는 안 들인다(새 의존성 0). 「하나로 정해질 때만」 의 뜻을 고정한다 —
  1. 형식 이름 `T`(단순 이름)를 그 파일에서 푼다: ① 같은 파일에 선언된 형식 `T` 가 정확히 하나 ② 아니면 단일 형식 import `import a.b.T;` → `<소스 루트>/a/b/T.java` ③ 아니면 같은 패키지 `<그 파일 폴더>/T.java`. 별 import 로만 올 수 있는 형식·못 찾은 형식은 「모름」
  2. 그 형식에서 이름과 인자 수가 같은 메서드가 **정확히 하나**면 그 반환 형식의 단순 이름. 없으면 `extends` 한 조상 형식을 같은 방법으로 풀어 올라간다(다섯 단계까지). 둘 이상(같은 인자 수 오버로드)·조상을 못 찾음·못 읽음·파싱 실패는 「모름」
  3. 소스 루트 = `LoggerShape.sourceRoot(rel)`(이미 있다 — `src/main/java/` 앞까지)
- 변경:
  ```java
  /** 5-114 — 같은 소스 루트에 선언된 메서드의 반환 형식(단순 이름). 하나로 못 정하면 빈 값. 파일은 형식마다 한 번만 읽는다 */
  final class ReturnTypes {
      /** rel — 묻는 쪽 파일의 상대 경로 · cu — 그 파일 · type — 수신자 형식의 단순 이름 */
      interface Finder {
          Optional<String> of(String rel, CompilationUnit cu, String type, String method, int argc);
      }

      /** 같은 파일 안에서만(붙여넣기·시험·다른 파일을 못 읽는 자리) */
      static final Finder SAME_FILE = …;

      /** 폴더 작업용 — files 로 root 아래 파일을 읽는다. 결과는 형식(파일 경로)마다 기억한다. 스레드 하나에서만 쓴다 */
      static Finder finder(LocalFiles files, String root)
  }
  ```
  - 형식 하나의 요약 `record Shape(Map<String, List<String>> returns /* 「이름/인자 수」 → 반환 형식 단순 이름들 */, String superName, String pkg, Map<String, String> imports, String rel)` 만 기억한다(CompilationUnit 을 붙들지 않는다)
  - 읽기는 `files.read(Path.of(root).resolve(rel).toString())` — `IOException`·`LocalFiles.Refused`·파싱 실패·`StackOverflowError` 는 「모름」(`PackageTypes.scan` 과 같은 처분). 파서는 자기 `JavaSource` 하나
  - 반환 형식 글은 `DeclaredTypes.simpleType(…)` 로(제네릭·배열 꼬리를 뗀 단순 이름 — `String[]` 은 `String` 이 아니게 `scalarType` 쪽 규칙을 쓴다: 배열이면 빈 값)
- 시험(먼저): `ReturnTypesTest`(임시 폴더에 `src/main/java/p/…` 를 쓰고 `new LocalFiles(tmp/data)` — `CleanupRunnerTest.loggerShapeFromSameSourceRoot` 의 꼴) —
  - `sameFile`: `class A { String name() { return ""; } int size() { return 0; } }` → `of(…, "A", "name", 0)` = `String` · `size` = `int`
  - `importedVoAndSuper`: `p/vo/Base.java`(`public class Base { public String getNttSj() … }`) · `p/vo/BoardVO.java`(`public class BoardVO extends Base { public java.util.Date getDt() … }`) · 묻는 파일 `p/web/C.java`(`import p.vo.BoardVO;`) → `getNttSj` = `String`(조상에서) · `getDt` = `Date`
  - `samePackage`: import 없이 같은 폴더 `T.java` → 찾는다
  - `notUnique`: 같은 인자 수 오버로드 둘 · 별 import 로만 오는 형식 · 없는 형식 · 깨진 파일 → 전부 빈 값
  - `readsEachTypeOnce`: 같은 물음 둘째는 파일을 다시 안 읽는다(파일을 지운 뒤에도 같은 답)
  RED: 클래스 없음(컴파일)
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='ReturnTypesTest,ArchitectureTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus(`core/check`)

### 5-114b — equals 앞세우기: getter 반환 형식이 String 이면 고친다
- 대상: `core/check/ValueFix.java` · `core/check/Cleanup.java` · `core/check/CleanupRunner.java` · `CleanupTest` · `CleanupRunnerTest` · `CleanupCorpusTest`(생성자 인자 한 줄)
- 사실: `ValueFix.literalFirst:174-181` 은 수신자가 이름·`this.x` 이고 선언 형식이 `String` 일 때만 뒤집는다(그 밖은 `RECEIVER` 로 건너뜀). 판정(`ValueAst`)은 수신자 형식을 안 본다 — 안 고친다
- 결정: 수신자가 **메서드 호출**일 때 — ① 수신자 없는 호출·`this.호출` → 그 호출을 감싼 형식(가장 가까운 `TypeDeclaration`. 그 사이에 익명 클래스 몸이 있으면 모름)의 메서드 ② `이름.호출`·`this.필드.호출` → `DeclaredTypes.declaredType(그 이름)` 이 준 형식의 메서드. `ReturnTypes.Finder` 가 `String` 을 줄 때만 뒤집는다. 호출이 이어진 꼴(`a.b().c()`)·정적 호출(`Util.x()`)·JDK 메서드(`toString()`·`getParameter()`)는 지금처럼 건너뛴다(허용 목록을 두지 않는다 — 설계 70 결정 2 그대로)
- 변경:
  - `ValueFix` — `apply(String text, List<Finding> cur, Set<String> targets, JavaSource parser, String encoding, Edits e, List<Cleanup.Skip> skips)`(7 인자)의 `String encoding` 을 `Env env` 로: `record Env(String encoding, ReturnTypes.Finder returns, String rel) {}`(인자 수 7 그대로). `literalFirst` 의 조건을
    ```java
    if (!"String".equals(DeclaredTypes.declaredType(scope)) && !stringCall(scope, cu, env)) {
    ```
    (`cu`·`env` 를 `rewrite` → `literalFirst` 로 넘긴다 — 인자 7 을 넘으면 `literalFirst` 가 받는 것을 `Predicate<Expression> stringy` 하나로 묶는다)
    ```java
    /** 5-114 — 수신자가 같은 소스 루트에 선언된 메서드 호출이고 그 반환 형식이 String 으로 하나로 정해지나 */
    private static boolean stringCall(Expression scope, CompilationUnit cu, Env env)
    ```
  - `Cleanup` — 생성자에 여섯째 인자 `ReturnTypes.Finder returns`(지금 다섯 인자 생성자는 `ReturnTypes.SAME_FILE` 로 넘기는 꼴로 남긴다). `:166` 의 `ValueFix.apply(x.text, x.cur, t, parser, rules.encoding(), e, l)` → `ValueFix.apply(x.text, x.cur, t, parser, new ValueFix.Env(rules.encoding(), returns, x.source().rel()), e, l)`
  - `CleanupRunner` — `new Cleanup(…)` 두 자리(`:95·153`)에 `ReturnTypes.finder(files, root)` 를 더한다(`preview`·`apply` 시그니처는 그대로 — 기준선 인자 줄 불변)
  - `CleanupCorpusTest.allSources` 의 `new Cleanup(…)` 에도 `ReturnTypes.finder(files, CorpusFiles.root().toString())`
- 시험(먼저): `CleanupTest.equalsFirstNeedsStringReceiver` — 16행(`return name().equals("c");` — 같은 파일 `String name()`)이 이제 **뒤집힌다**: 기대 건너뜀 목록에서 「줄 16」 을 빼고 결과 글에 `"c".equals(name())` 를 단언한다(이 단언 변경은 이 행의 의도다). 같은 시험 소스에 더한다 — `int size() {…}` 를 부르는 `size().equals("1")` 꼴은 컴파일이 안 되니 대신 `Object o() {…}` 의 `o().equals("x")` → `RECEIVER` 그대로 · 오버로드 둘(`String pick(int)`·`Object pick(String)`)의 `pick(1).equals("a")` → `RECEIVER`(같은 인자 수가 둘) · 익명 클래스 몸 안 맨 호출 → `RECEIVER`. `CleanupRunnerTest` 새 `voGetterFromSameSourceRoot` — 임시 프로젝트 `src/main/java/p/vo/BoardVO.java`(`public String getNttSj()`·`public Integer getNo()`) · `p/web/C.java`(`import p.vo.BoardVO; … if (vo.getNttSj().equals("A")) … if (vo.getNo().equals("1")) …`) 를 `CleanupRunner.preview` → `C.java` 의 첫 줄이 `"A".equals(vo.getNttSj())` 로, 둘째 줄은 `RECEIVER` 건너뜀. RED: 둘 다 `RECEIVER`
- 의도된 골든 변화: 없음(빠른 레인 — `self-cleanup.txt` 불변: `src/main` 에 그 지적 0). corpus `cleanup-egov.json`·`cleanup-roller.json`·`cleanup-jspwiki.json`·`cleanup-commons.json` 의 `RECEIVER` 건너뜀이 **주고** `steps.value` 가 **느는 것**은 `V-67a`
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupRunnerTest,ValueAstTest,ReturnTypesTest,CleanupRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 5-114a. 레인 corpus
- 사다리: `ValueFix.apply` 를 부르는 다른 자리가 있으면(`grep -rn "ValueFix.apply"`) 같은 `Env` 로. 인자 수가 7 을 넘는 메서드가 생기면 `Env` 에 묶는다

### ④ 생성기·산출물·DB Minor

### 1-107 — DtoGenerator: `\u` · record 금지 이름 · 빈 클래스 이름 (A-18 · A-20 · A-21)
- 대상: `core/gen/DtoGenerator.java` · `DtoGeneratorTest`
- 변경:
  - `doc:219-221` — `.replace("\\u", "&#92;u")` 를 더한다(javadoc 안 유니코드 탈출 — `GenModel.safe` 와 같은 꼴). TODO 주석 글(`:105-107` 의 `todo = todo.replace('\r', ' ').replace('\n', ' ');`)에 `.replace("\\", "\\\\")` 를 더한다 — 역슬래시를 전부 겹치면 `\u` 가 탈출로 안 읽힌다(javac 는 `//` 안도 유니코드 탈출을 푼다)
  - `generate:101` 의 `String name = o.style() == Style.TS ? camel(c.name()) : fieldName(c.name());` → `String name = nameFor(o.style(), c.name());`
    ```java
    /** record 성분으로 못 쓰는 이름(JLS 8.10.1) */
    private static final Set<String> RECORD_BANNED = Set.of("clone", "finalize", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait");

    /** 꼴마다 필드 이름 — TS 는 예약어 그대로, record 는 금지 성분 이름에 _ 를 붙인다(A-20) */
    private static String nameFor(Style s, String col) {
        if (s == Style.TS) {
            return camel(col);
        }
        String n = fieldName(col);
        return s == Style.RECORD && RECORD_BANNED.contains(n) ? n + "_" : n;
    }
    ```
    (`generate(2)` 복잡도 31 — 삼항 하나가 빠져 내린다, 기준선 갱신)
  - `className:268-272` — `String camel = fieldName(rest).replaceAll("_+$", "");` 다음에 `if (camel.isEmpty()) { camel = "table"; }`(`TB_`·`_`·`__` → `Table`). javadoc 에 「남는 글자가 없으면 Table(A-21)」
- 시험(먼저): `DtoGeneratorTest` — `namingRules` 에 `className("TB_", List.of("TB"))` = `Table` · `className("_", List.of())` = `Table` · `doc("a \\u0041 b")` = `a &#92;u0041 b`. 새 `hostileNamesAndCommentsCompile` — `CREATE TABLE t (notify INT, wait INT, hash_code INT, ok INT)` 를 RECORD 로 → 성분 `notify_`·`wait_`·`hashCode_`·`ok` 이고 `compile(s)` 통과(BEAN 은 `notify`·`wait` 그대로 — 필드 이름으로는 합법) · 코멘트 `경로 C:\users` 인 컬럼과 못 읽는 줄 `9x \u000a oops` 가 든 DDL 을 BEAN 으로 → `compile(s)` 통과. RED: `StringIndexOutOfBounds` · 컴파일 실패
- 의도된 골든 변화: `golden/gen/*-record.java` 등 diff 0(그런 이름·코멘트가 픽스처에 없다 — 탐색 확인)
- 검증: `bash scripts/mvn.sh -q test -Dtest='DtoGeneratorTest,GenModelTest,GenRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 7-36(같은 파일). 레인 db + corpus

### 1-108 — 큰 경계의 `@Min`·`@Max` · 패키지 마디 예약어 (A-19 · A-23)
- 대상: `core/gen/Validation.java` · `web/GenRoutes.java` · `core/gen/DtoGenerator.java`(상수 공개 한 줄) · `ValidationTest` · `GenRoutesTest`
- 변경:
  - `Validation.of:121-126` — `min = "@Min(" + lower(r.min(), r.minIncl()) + ")";` → `min = whole("@Min", "@DecimalMin", lower(r.min(), r.minIncl()));`, `max` 도 `whole("@Max", "@DecimalMax", upper(…))`:
    ```java
    /** 정수 경계 어노테이션 — int 안이면 그대로, long 안이면 L 을 붙이고, long 을 넘으면 글자 꼴 @DecimalMin·Max(A-19) */
    private static String whole(String anno, String big, String digits) {
        java.math.BigInteger v = new java.math.BigInteger(digits);
        if (v.bitLength() < 32) {
            return anno + "(" + digits + ")";
        }
        return v.bitLength() < 64 ? anno + "(" + digits + "L)" : big + "(\"" + digits + "\")";
    }
    ```
    (`of(5)` 복잡도 56 — 분기를 안 더한다). import 는 지금 `decimal(…)` 길이 `@DecimalMin`·`@DecimalMax` 를 싣는 방식 그대로(어노테이션 이름으로 모으는 자리를 확인해 정수 가지에서도 듣게)
  - `DtoGenerator.KEYWORDS` 를 `static final`(package-private) → `public static boolean keyword(String word)`(= `KEYWORDS.contains(word) || "_".equals(word)`) 를 더한다. `GenRoutes.validPackage` — `if (!PART.matcher(part).matches())` → `if (!PART.matcher(part).matches() || PACKAGE_BANNED.test(part))` 는 분기가 늘므로, 판정을 `private static boolean segment(String part)`(꼴이 맞고 예약어가 아님 — `var`·`record`·`yield` 는 패키지 마디로 합법이라 뺀다)로 빼 `if (!segment(part))` 로
- 시험(먼저): `ValidationTest.oracleCheckForms` 옆 `wideBoundsCompile` — `CHECK (QTY BETWEEN 0 AND 3000000000)` + `Long` → `[…, "@Min(0)", "@Max(3000000000L)"]` · `CHECK (N <= 99999999999999999999)` + `BigInteger` → `@DecimalMax("99999999999999999999")`. `GenRoutesTest.badInput` 에 `packageName` `com.new.int`·`a._.b` → 400, `a.record.b` → 200. RED: `@Max(3000000000)` · 200
- 의도된 골든 변화: 없음(`golden/gen` 에 `@Min`·`@Max` 없음)
- 검증: `bash scripts/mvn.sh -q test -Dtest='ValidationTest,DtoGeneratorTest,GenRoutesTest,GenerateRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-107. 레인 db + corpus

### 1-109 — DdlReader: 크기 자릿수 · 기본값 식 · 공백 든 따옴표 이름 · 자동증가 낱말 (A-22 · A-29 · A-35 · A-08 읽기 쪽)
- 대상: `core/gen/DdlReader.java`(`item`·상수) · `core/gen/DdlPull.java`(`DEFAULT` 를 쓰는 한 자리) · `DdlReaderTest`
- 사실: `item(3)` 복잡도 30·`DdlReader` 메서드 수는 상한 — `item` 에 분기를 안 더하고 `DdlReader` 에 메서드를 안 더한다(도우미는 `DdlPull` 에)
- 변경:
  - 크기 — `:372-375` 의 `Integer.parseInt(size.group(1))`·`Integer.parseInt(size.group(2))`·`Long.parseLong(size.group(1))` 를 `DdlPull.num(…)` 으로: `static long num(String digits)` — 열여덟 자리 넘으면 `Long.MAX_VALUE`(음수 부호는 그대로 파싱), precision·scale 은 `(int) Math.min(Integer.MAX_VALUE, DdlPull.num(…))`
  - 기본값 — `:360-361` 의 `Matcher df = DEFAULT.matcher(m); String def = df.find() ? rest.substring(df.start(1), df.end(1)) : null;` → `String def = DdlPull.defaultExpr(m, rest);`
    ```java
    /** DEFAULT 뒤 식 하나 — 글자·괄호 묶음(겹친 것까지)·함수 호출(이름 + 괄호)·N'…' 를 통째로. GENERATED … BY DEFAULT 의 DEFAULT 는 아니다(A-29). masked 는 글자 안을 비운 사본, 값은 원문에서 */
    static String defaultExpr(String masked, String raw)
    ```
    규칙: `masked` 에서 `\bDEFAULT\s+` 를 찾되 바로 앞 낱말이 `BY` 면 건너뛴다. 식의 끝 = ① `'` 로 시작 → 닫는 `'`(masked 에서 다음 `'`) ② `(` 로 시작 → `DdlReader.matching` 이 준 닫는 괄호 ③ 그 밖 — 공백·쉼표 앞까지 읽되, 읽은 낱말 바로 뒤가 `(` 면 그 괄호의 닫는 자리까지, 바로 뒤가 `'` 면(`N'…'`·`E'…'`·`_utf8'…'`) 닫는 `'` 까지. `DdlPull.typedOption:287` 의 `DdlReader.DEFAULT.matcher(bare)` 도 같은 도우미로. 상수 `DdlReader.DEFAULT` 는 쓰는 곳이 없어지면 지운다
  - 공백 든 이름 — `NAME:53` 은 그대로 두고, `item:348` 의 `!NAME.matcher(name).matches()` 를 `!DdlPull.columnName(head, name)` 로: 원문 첫 토큰이 따옴표·백틱·대괄호로 싸였으면(= `head[0]` 이 그 글자로 시작) 안 글자가 비지 않고 줄바꿈이 없으면 받는다, 아니면 `NAME` 꼴. 같은 줄의 `key` 계산도 같은 판정으로
  - 자동증가 — `:380` 의 11 인자 `new Column(…)` 을 12 인자로, 끝 인자 `DdlPull.auto(upper, type)`: `private static final Pattern AUTO = Pattern.compile("\\b(?:AUTO_INCREMENT|AUTOINCREMENT|IDENTITY)\\b")` 가 `upper`(글자 안을 비운 대문자 사본)에 있거나 타입이 `SERIAL`·`BIGSERIAL`·`SMALLSERIAL`
- 시험(먼저): `DdlReaderTest` 새 `sizesDefaultsQuotedNamesAuto` — `CREATE TABLE t (a NUMBER(99999999999999999999), b VARCHAR(99999999999999999999), c DATE DEFAULT TO_DATE('2020-01-01','YYYY-MM-DD') NOT NULL, d DATETIME DEFAULT (getdate()), e INT DEFAULT ((0)), f NVARCHAR(5) DEFAULT N'abc', g INT GENERATED BY DEFAULT AS IDENTITY, h INT GENERATED ALWAYS AS IDENTITY, i INT AUTO_INCREMENT, j INT IDENTITY(1,1), "Unit Price" INT, k SERIAL)` → `unreadable` 빔 · 컬럼 열둘 · `c` 기본값 `TO_DATE('2020-01-01','YYYY-MM-DD')`·NOT NULL · `d` `(getdate())` · `e` `((0))` · `f` `N'abc'` · `g`·`h` 기본값 null · `g`·`h`·`i`·`j`·`k` 의 `autoIncrement()` 참, `a`·`c` 는 거짓 · 열한째 컬럼 이름 `Unit Price`. 기존 `unreadableLineIsReportedNotDropped`(`123bad stuff`)·`DtoGeneratorTest.unknownTypeAndDdlNotesBecomeTodoButStillCompile`(`9x oops`) 그대로. RED: `NumberFormatException`
- 의도된 골든 변화: 없음(빠른 레인). corpus `ddl-unreadable.txt`·`ddl-summary.json` 의 「못 읽은 줄」 이 **주는 것**(공백 든 따옴표 이름)은 `V-67a`
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,DtoGeneratorTest,InsertGenTest,InsertRoutesTest,GenRoutesTest,ValidationTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-106(같은 파일). 레인 db + corpus
- 사다리: `DdlPull` 이 메서드 30 을 넘으면 새 `core/gen/DdlItems`(package-private, static)로 이 행의 도우미 넷을 옮긴다

### 1-110 — 타입 매핑: LONG RAW · BIT(n) · UNSIGNED · 배열 (A-31)
- 대상: `src/main/resources/gen/type-mapping.yaml` · `core/gen/TypeMapping.java`(`javaType`) · `DtoGeneratorTest`
- 변경:
  - yaml `native` 목록 **맨 위**(DATE 줄 다음)에 넷 —
    ```yaml
    - { native: [LONG RAW, LONG VARBINARY], java: "byte[]" }          # LONG 보다 먼저(접두 맞춤, A-31)
    - { native: [BIT VARYING, VARBIT], java: String }                 # 비트 글자
    - { native: [BIGINT UNSIGNED, INT8 UNSIGNED], java: java.math.BigInteger }
    - { native: [INT UNSIGNED, INTEGER UNSIGNED, MEDIUMINT UNSIGNED, INT4 UNSIGNED], java: Long }
    ```
    맨 아래 byte[] 줄의 `LONG RAW` 는 지운다
  - `TypeMapping.javaType` 머리 — 규칙을 보기 **앞에** 둘: ① 원문 타입이 `[]` 를 품으면(`int[]`·`varchar(10)[]`·`_int4`) `null`(→ Object + TODO) ② `norm` 이 `BIT` 이고 `c.length() != null && c.length() > 1` 이면 `"byte[]"`(MySQL `BIT(8)`). 도우미 `private static String special(Column c, String n)`(없으면 null)로 빼 `javaType` 은 한 줄만 는다
- 시험(먼저): `DtoGeneratorTest.typeMapping` 에 — `LONG RAW` → `byte[]` · `bit varying` → `String` · `int unsigned` → `Long` · `bigint unsigned` → `java.math.BigInteger` · `int[]`·`varchar(10)[]` → null · `BIT` 길이 8(`new Column("f", 1, "bit", null, 8L, …)`) → `byte[]` · `bit`(길이 없음, mssql) → `Boolean` 그대로. RED: `String`·`Boolean`·`Integer`
- 의도된 골든 변화: `golden/gen/**`·`golden/meta/*` diff 0(그런 타입이 픽스처에 없다). `--db` 의 `ddlGenWarnings`·DTO 표본이 바뀌면 그 컬럼 타입이 위 넷인지 `V-67` 에서 본다
- 검증: `bash scripts/mvn.sh -q test -Dtest='DtoGeneratorTest,GenModelTest,DdlGenTest,AlterGenTest,ValidationTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-109. 레인 db + corpus

### 4-38 — InsertGen(생성): 자동증가 인식 · 행 수 상한 알림 · CHECK IN 값의 괄호 (A-08 · A-25 · A-32)
- 대상: `core/gen/InsertGen.java` · `InsertGenTest`
- 변경:
  - `cols:128-129` — `boolean auto = … || d.toUpperCase(Locale.ROOT).contains("IDENTITY");` 끝에 `|| DdlGen.auto(c)` 를 잇는다(`DdlGen.auto(Column)` 은 `c.autoIncrement()` 또는 타입 글의 identity — 1-109 가 DDL 에서, 1-86b 가 스냅샷에서 채운다). 그러면 기존 경고 「자동증가 컬럼: … — 값을 넣어 두었으니 필요하면 해당 컬럼을 지울 것」 이 `GENERATED … AS IDENTITY`·`AUTO_INCREMENT`·`IDENTITY(1,1)` 에도 나온다. 생성 SQL 은 안 바꾼다
  - `generate:427` — `int rows = Math.max(1, Math.min(MAX_ROWS, o.rows()));` → `int rows = rowCount(o.rows(), warns);`
    ```java
    /** 행 수를 1~MAX_ROWS 로 — 바꿨으면 경고(A-25) */
    private static int rowCount(int asked, List<String> warns) {
        int rows = Math.max(1, Math.min(MAX_ROWS, asked));
        if (rows != asked) {
            warns.add("행 수 " + asked + " → " + rows + "(1~" + MAX_ROWS + " 만)");
        }
        return rows;
    }
    ```
    (`generate(4)` 복잡도 20 — 그대로)
  - `CHECK_IN:49-50` — 정규식을 `IN\s*\(` 까지만(`"CHECK\\s*\\(\\s*[\"`\\[]?(\\w+)[\"`\\]]?\\s+IN\\s*\\("`)으로 줄이고, 두 `checkIns` 의 루프를 공통 도우미로: `private static void collect(String text, Map<String, List<String>> out)` — 맞은 자리마다 `int open = m.end() - 1; int close = DdlReader.matching(text, open);` 가 `open` 보다 크면 `out.putIfAbsent(컬럼, splitVals(text.substring(open + 1, close)))`(`matching` 은 글자 안 괄호를 건너뛴다)
- 시험(먼저): `InsertGenTest` — `keyWarns` 꼴로 `autoColumnsAreWarned`: `CREATE TABLE t (id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, nm VARCHAR(10))`·`… id INT AUTO_INCREMENT PRIMARY KEY …`·`… id INT IDENTITY(1,1) PRIMARY KEY …` 셋 다 경고에 `자동증가 컬럼: ID` · `rowsAreClampedWithWarning`: rows 5000 → 문장 1000 개 + 경고 `행 수 5000 → 1000(1~1000 만)`, rows 0 → 1 개 + 경고 · `checkInsFromTable` 에 `CHECK (k IN ('m(2)', 'n'))` → `{K=['m(2)', 'n']}`. RED: 경고 없음 · `'m(2`
- 의도된 골든 변화: `golden/insert/*.sql` diff 0(rows 3 · 자동증가 없음)
- 검증: `bash scripts/mvn.sh -q test -Dtest='InsertGenTest,InsertRoutesTest,DdlReaderTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 1-109(자동증가 읽기). 레인 db + corpus

### 4-39 — InsertGen(CSV): 머리 중복 · 없는 날짜 · PK 없는 upsert (A-26 · A-27 · A-34)
- 대상: `core/gen/InsertGen.java`(`fromCsv`·`csvLiteral`) · `InsertGenTest`
- 결정: 머리에 같은 열이 둘이면 **거절**(어느 값을 쓸지 정할 수 없다). upsert 인데 CSV 에 그 표의 PK 컬럼이 빠졌으면 **거절**(첫 컬럼을 키로 한 MERGE 는 다른 행을 고친다). 없는 날짜는 값을 그대로 싣고 경고(모양 틀린 날짜와 같은 처분)
- 변경:
  - `fromCsv:642-648` 의 머리 루프 — `head.add(c);` 앞에 `if (head.contains(c)) { throw new IllegalArgumentException("CSV 머리에 같은 열이 두 번: " + h.trim()); }`
  - `fromCsv` — `warns` 를 만든 다음(`:649`)에 `requireKeys(t, byName, head, o);` — `o.upsert()` 이고 `byName` 의 `pk()` 컬럼 가운데 `head` 에 없는 것이 있으면 `IllegalArgumentException("upsert 는 CSV 에 PK 컬럼(" + 이름들 + ")이 있어야 한다")`. 표에 PK 가 아예 없으면 지금처럼 간다(「PK 없음 — 첫 컬럼을 키로 가정」)
  - `csvLiteral:706-709` — `if (!dateShape(v)) { warns.add(where + "날짜 모양(YYYY-MM-DD[ HH:MI[:SS]])이 아니다"); return quote(raw, db); }` → `String bad = dateProblem(v); if (bad != null) { warns.add(where + bad); return quote(raw, db); }`
    ```java
    /** 날짜 값의 흠 — 모양이 틀리면 지금 글, 모양은 맞는데 없는 날짜·시각이면 새 글(A-27). 괜찮으면 null */
    static String dateProblem(String v) {
        if (!dateShape(v)) {
            return "날짜 모양(YYYY-MM-DD[ HH:MI[:SS]])이 아니다";
        }
        try {
            LocalDate.parse(v.substring(0, 10));
            if (v.length() > 10) {
                java.time.LocalTime.parse(v.substring(11));
            }
            return null;
        } catch (java.time.DateTimeException e) {
            return "없는 날짜·시각이다";
        }
    }
    ```
    (`csvLiteral(5)` 복잡도 31 — `if` 하나가 `if` 하나로, 그대로)
- 시험(먼저): `InsertGenTest.csvModeWarnsButKeepsValues` 옆 — `duplicateHeaderIsRefused`: `"product_id,code,code\n1,a,b\n"` → `IllegalArgumentException` 글에 `같은 열이 두 번: code` · `upsertNeedsKeyColumns`: `"code\nA\n"` + upsert → 예외 글에 `PK 컬럼(PRODUCT_ID)`, upsert 가 아니면 통과 · `impossibleDateIsWarned`: `csvLiteral(dateCol, "2023-02-30", "oracle", 2, w)` → 경고 `2행 …: 없는 날짜·시각이다`, `"2023-02-28 25:61"` 도 · `"2023-02-28"` 은 경고 없음. RED: 통과·경고 없음
- 의도된 골든 변화: 없음. `--db` 의 `firstRowCsv` 는 실제 날짜라 그대로
- 검증: `bash scripts/mvn.sh -q test -Dtest='InsertGenTest,InsertRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 4-38. 레인 db + corpus

### 2-48 — 산출물: 매핑을 쓰기 전에 본다 · 모르는 문서 번호 (B-16 · B-22)
- 대상: `core/deliverable/DeliverableService.java` · `DeliverableServiceTest`
- 변경: `build` 의 쓰기 루프(`:117`) 앞에 `precheck(order, docs, mapping, skipped);` 한 줄(`build(8)` 복잡도 22 = 기준선 — 분기는 도우미에)
  ```java
  /** 쓰기 전에(B-16·B-22) — 만들 문서마다 매핑이 있는지 본다(없으면 여기서 예외 — 반쯤 쓴 폴더가 안 남는다). 모르는 번호는 건너뛴 것에 적는다 */
  private static void precheck(List<String> order, Map<String, Doc> docs, Mapping mapping, List<String> skipped) {
      for (String no : order) {
          if (docs.containsKey(no)) {
              mapping.of(no);
          } else if (!KNOWN.contains(no)) {
              skipped.add(no + " — 모르는 문서 번호");
          }
      }
  }
  ```
  `KNOWN` = `/api/deliverable/docs` 가 내는 번호와 같은 원천(`Grades.marks()` 의 열쇠 — 01~11·18)
- 시험(먼저): `DeliverableServiceTest` — `unknownDocNumberIsReported`: `Set.of("02", "99")` → 파일 하나 · `skipped` 에 `99 — 모르는 문서 번호` · `missingMappingFailsBeforeWriting`: 문서 `01`·`02` 를 고르고 매핑에 `02` 가 없는 `Mapping.parse(…)`(인라인 YAML — `01` 만) → `IllegalArgumentException`(「매핑에 문서 02 가 없다」)이고 출력 폴더에 xlsx 가 **하나도 없다**. RED: `skipped` 빔 · `01_…xlsx` 가 남음
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='DeliverableServiceTest,DeliverableRoutesTest,DocCliTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 2-47(같은 파일). 레인 corpus(`core/deliverable`)

### 2-49 — 양식 채우기: 겹치는 머리 알림 · 데이터 행 병합 잇기 (B-17 · B-23)
- 대상: `core/report/XlsxFiller.java` · `XlsxFillerTest`
- 결정: 같은 머리 글이 둘이면 지금처럼 **뒤 칸**에 채우고(동작 불변) 알린다. 데이터 첫 행 안의 병합(같은 행 가로 병합)은 채운 행마다 잇는다
- 변경(`fill(4)` 복잡도 25·중첩 5 = 기준선 — `fill` 에 분기를 안 더한다):
  - `header(Sheet, int)` 는 `header(sheet, rowIndex, new ArrayList<>())` 로 넘기고, 새 `static Map<String, Integer> header(Sheet sheet, int rowIndex, List<String> dup)` 가 같은 글을 다시 만나면 `dup` 에 그 글을 더한다
  - `fill:70` `Map<String, Integer> header = header(sheet, m.headerRow() - 1, dup);`(`List<String> dup = new ArrayList<>();` 를 그 앞에) · 2-47 의 알림 목록 만들기에 `dup` 가운데 `m.columns()` 의 열쇠인 것마다 `"머리 「" + 글 + "」 가 양식에 둘 이상 — 맨 뒤 칸에 채웠다"` 를 더한다
  - `:93-96` 의 밀기 블록 **다음**에 무조건 `repeatMerges(sheet, first, n);`
    ```java
    /** 데이터 첫 행 안의 병합을 채운 행마다 잇는다(B-23) — n 이 1 이하면 아무것도 안 한다. 아래로 민 뒤에 부른다 */
    private static void repeatMerges(Sheet sheet, int first, int n) {
        List<CellRangeAddress> own = sheet.getMergedRegions().stream().filter(r -> r.getFirstRow() == first && r.getLastRow() == first).toList();
        for (int i = 1; i < n; i++) {
            for (CellRangeAddress r : own) {
                sheet.addMergedRegion(new CellRangeAddress(first + i, first + i, r.getFirstColumn(), r.getLastColumn()));
            }
        }
    }
    ```
- 시험(먼저): `XlsxFillerTest`(`arrayFormulaDoesNotStopFill` 의 인라인 `XSSFWorkbook` 꼴) — `duplicateHeaderIsReported`: 머리 행 `이름 | 값 | 값` → 반환 목록에 `머리 「값」 가 양식에 둘 이상` 한 줄, 셋째 칸에 값 · `dataRowMergesAreRepeated`: 데이터 행(3행) `A3:B3` 병합 양식에 세 행 → 병합이 `A3:B3`·`A4:B4`·`A5:B5`, 합계 행 병합(있으면)은 한 번만 밀림. RED: 빈 목록 · 병합 하나
- 의도된 골든 변화: 없음(내장 양식에 겹친 머리·데이터 행 병합이 없다 — `builtInMappingEqualsCommitted` 그대로)
- 검증: `bash scripts/mvn.sh -q test -Dtest='XlsxFillerTest,DeliverableServiceTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 2-47. 레인 corpus(`core/report`)
- 사다리: POI 가 겹치는 병합을 거절하면(`IllegalStateException`) — 이미 있는 병합과 같은 범위는 건너뛴다

### 2-50 — 작은 셋: FK 순환 재귀 · 태그 정규식 · 매핑 값 null (B-19 · B-20 · B-24)
- 대상: `core/deliverable/StructureLint.java`(`Scc`) · `core/report/TableXlsx.java` · `core/report/Mapping.java` · `StructureLintTest` · `TableXlsxTest` · `XlsxFillerTest`
- 변경:
  - `Scc.strong:558-575` 를 재귀 없이 —
    ```java
    /** 재귀 없이(B-19 — 표 2만 개 FK 사슬에서 스택 넘침). path = 걷는 중인 꼭짓점, its = 그 이웃 반복자 */
    private void strong(int root) {
        java.util.Deque<Integer> path = new java.util.ArrayDeque<>();
        java.util.Deque<java.util.Iterator<Integer>> its = new java.util.ArrayDeque<>();
        open(root, path, its);
        while (!path.isEmpty()) {
            int v = path.peek();
            if (its.peek().hasNext()) {
                int w = its.peek().next();
                if (index[w] == 0) {
                    open(w, path, its);
                } else if (onStack[w]) {
                    low[v] = Math.min(low[v], index[w]);
                }
                continue;
            }
            path.pop();
            its.pop();
            if (low[v] == index[v]) {
                comps.add(pop(v));
            }
            if (!path.isEmpty()) {
                low[path.peek()] = Math.min(low[path.peek()], low[v]);
            }
        }
    }

    private void open(int v, java.util.Deque<Integer> path, java.util.Deque<java.util.Iterator<Integer>> its) {
        index[v] = next;
        low[v] = next;
        next++;
        stack.push(v);
        onStack[v] = true;
        path.push(v);
        its.push(adj.get(v).iterator());
    }
    ```
    javadoc 의 「재귀 깊이는 FK 사슬 길이」 를 지운다
  - `TableXlsx.TAG:66` — `Pattern.compile("<[^>]*>")` → `Pattern.compile("<[^<>]*+>")`
  - `Mapping.DocMapping` 생성자 — `columns = …Map.copyOf(columns)` 앞에 null 값 검사: 값이 null 인 열쇠를 `columns.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey).toList()` 로 모아(불변 맵에 `containsValue(null)` 을 부르면 NPE 다 — 쓰지 않는다) 비지 않으면 `throw new IllegalArgumentException(file + " — columns 의 값이 빈 열쇠가 있다: " + 그 열쇠들);`. 검사는 도우미 `private static void requireValues(String file, Map<String, String> columns)` 로
- 시험(먼저): `StructureLintTest.longFkChainDoesNotOverflow` — `linked("T0", "T1")`, …, `linked("T19999", "T0")` 이만 표 순환을 `assertTimeoutPreemptively(10초)` 로 `StructureLint.run` → `FK_CYCLE` 지적 하나(예외 없음). 기존 `fkCycleReportsEachComponentOnceWithoutSelfRef` 그대로 · `TableXlsxTest` — `TableXlsx.plain("<".repeat(100_000))` 이 5초 안, `plain("a<b>c</b>")` = `ac` 그대로 · `XlsxFillerTest.mappingTyposAreErrors` 에 `Mapping.parse("docs:\n  '01':\n    file: f.xlsx\n    headerRow: 2\n    firstRow: 3\n    columns:\n      이름:\n")` → 예외 사슬의 글에 `columns 의 값이 빈 열쇠` 와 `이름`. RED: `StackOverflowError` · 시간 초과 · 글에 `NullPointerException`
- 의도된 골든 변화: `golden/deliverable/pg-lint.json`·`golden/table/*.json` diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='StructureLintTest,TableXlsxTest,TableRoutesTest,XlsxFillerTest,DeliverableRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 2-49. 레인 corpus

### 2-51 — 품질 SQL: 표 없는 nopk (B-15)
- 대상: `core/quality/QualitySql.java`(`render`) · `QualitySqlTest`
- 변경: `:102` 의 `String qualified = blank(schema) ? id(table, dk) : id(schema, dk) + "." + id(table, dk);` 를 그 아래 `if (!blank(table)) {` 블록 **안 첫 줄**로 옮긴다(표가 비면 `id(null)` 을 안 부른다)
- 시험(먼저): `QualitySqlTest` — `nopkNeedsNoTable`: `QualitySql.render("nopk", "oracle", null, null, null, null, List.of("APP"))` 가 예외 없이 `'APP'` 을 품은 SQL · `namesMustBeIdentifiers` 의 `List.of("A')--")` 줄은 여전히 `IllegalArgumentException`(이제 스키마 글자 검사로). RED: 「SQL 에 그대로 넣을 수 없는 이름: null」
- 의도된 골든 변화: `golden/quality/*.sql` diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='QualitySqlTest,DeliverableRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 db(`core/quality`) + corpus

### 3-22 — COMMENT DDL: 빈 소유자·표 · 예약어 이름 (B-13 · B-14)
- 대상: `core/logical/Dialect.java` · `core/logical/CommentDdl.java` · `core/logical/Masking.java`(가시성 한 낱말) · `web/LogicalRoutes.java`(comments 라우트) · `CommentDdlTest` · `LogicalRoutesTest`
- 결정: 예약어·특수 글자 이름은 마스킹 SQL 과 같은 판정(`Masking.id` — `DdlGen.reserved`)으로 감싼다. 소유자가 비면 `소유자.` 를 뺀다(접속 기본 스키마). **표 이름이 빈 행**과 **MSSQL 에서 소유자가 빈 행**은 실행할 수 없는 문장이 되므로 400 으로 거절한다(추측해 채우지 않는다). Sybase(대조표 — 실행 안 함)는 지금 그대로
- 변경:
  - `Masking.id(String, Dialect)` 를 `private static` → `static`
  - `Dialect.table`·`column` — ORACLE·TIBERO·POSTGRESQL·MARIADB 가지의 `o + "." + t` 를 `qn(o, t)` 로, `c` 를 `Masking.id(c, this)` 로: `private String qn(String o, String t) { return (o == null || o.isBlank() ? "" : Masking.id(o, this) + ".") + Masking.id(t, this); }`. MSSQL(글자 인자)·SYBASE 가지는 그대로
  - `CommentDdl.generate`·`executableLines` 첫 줄에 `require(r, d, includeTables);`
    ```java
    /** 실행형 방언에서 문장이 안 되는 행 — 표 이름이 비었거나, MSSQL 인데 소유자가 비었다(B-13). 이름 없는 행(안 쓰는 행)은 안 본다 */
    private static void require(LogicalRun.Result r, Dialect d, boolean includeTables)
    ```
    글 — `"표 이름이 빈 행이 있다 — COMMENT 문은 표 이름(TABLE_NAME 열)이 있어야 만든다"` · `"MSSQL 은 스키마(소유자)가 있어야 한다 — owner 칸을 채운다"`. `d.noExec()` 면 검사하지 않는다
  - `LogicalRoutes.register` 의 `/api/logical/comments` 람다 몸을 `private static void postComments(Context ctx, DictStore dict, SnapshotStore snapshots, Supplier<Optional<Profile>> active)` 로 옮기고(작성 규약), `CommentDdl.generate(…)` 를 `try { … } catch (IllegalArgumentException e) { ctx.status(400).json(Map.of("message", e.getMessage())); return; }` 로 감싼다. `register(4)` 의 복잡도·길이·중첩 값은 내려간다(기준선 갱신)
  - `CommentDdl` 클래스 javadoc 첫 줄에 「(예약어·특수 글자 이름을 감싸는 것과 빈 소유자만 순수본과 다르다 — B-13·B-14)」
- 시험(먼저): `CommentDdlTest.quotesAndReviewMarkPosition` 꼴(손으로 만든 `LogicalRun.Result`) — `reservedAndBlankOwner`: 행 `("S", "T", "ORDER", …)`·`("", "EMP", "NM", …)` 를 ORACLE 로 → `COMMENT ON COLUMN S.T."ORDER" IS` 와 `COMMENT ON COLUMN EMP.NM IS`(앞 점 없음), MARIADB 로 → `` `ORDER` `` · `blankTableIsRefused`: 표가 빈 행 → `IllegalArgumentException`, SYBASE 는 통과 · MSSQL + 빈 소유자 → 예외. `LogicalRoutesTest` — `post("/api/logical/comments", Map.of("csv", "COLUMN_NAME\nUSER_NM\n", "dialect", "oracle"))` → 400 + 글에 `표 이름`. `matchesPureJs` 다섯·`tiberoUsesOracleSyntax` 그대로. RED: `.EMP`·맨 `ORDER`·200
- 의도된 골든 변화: `golden/logical/comments-*.sql` diff 0(픽스처 소유자 전부 `SHOP`, 예약어 이름 없음 — 탐색 확인). `--db` 의 COMMENT 실행(`DbCorpusBase:554`) 실패가 **주는 것**은 `V-67`
- 검증: `bash scripts/mvn.sh -q test -Dtest='CommentDdlTest,LogicalRunSampleTest,MaskingTest,LogicalRoutesTest,LogicalCliTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 db(`core/logical`) + corpus

### 3-23 — 마스킹 종류: 여권·운전면허 · `TEL_NO` (B-18)
- 대상: `src/main/resources/logical/masking.yaml` · `MaskingTest`(+ `golden/logical/masking-candidates.json` 은 그대로)
- 변경: `phone.columns` 의 대안에 `TEL_NO` 를 **따로** 더한다 — `'(?:^|_)(?:[A-Z]*TELNO|TEL_NO|MBTLNUM|…)(?:_|$)'`(`[A-Z]*TEL_?NO` 로 넓히지 않는다 — `HOTEL_NO` 가 걸린다). `rrn` 다음에 종류 둘 —
  ```yaml
    - id: passport
      label: 여권번호
      keywords: [여권번호]
      columns: '(?:^|_)(?:PASSPORT_?NO|PASSPORT_?NUM|PSPRT_?NO)(?:_|$)'
      keep: { head: 2, tail: 0 }
    - id: driver
      label: 운전면허번호
      keywords: [운전면허번호, 운전면허]
      columns: '(?:^|_)(?:DRV_?LIC_?NO|DRIVER_?LICENSE_?NO|DRVLCNS_?NO)(?:_|$)'
      keep: { head: 2, tail: 0 }
  ```
- 시험(먼저): `MaskingTest.flagAndCodeColumnsAreNotPii` 옆 `passportDriverTelNo` — `in("T", "PASSPORT_NO", …)`·`in("T", "DRV_LIC_NO", …)`·`in("T", "TEL_NO", …)`·`in("T", "X", null, "여권번호", …)` → 종류 `passport`·`driver`·`phone`·`passport` · `in("T", "HOTEL_NO", …)`·`in("T", "PASSPORT_YN", …)` 는 후보 아님. `sqlGoldenPerDialect` 그대로. RED: 후보 0
- 의도된 골든 변화: `golden/logical/masking-candidates.json`·`masking-*.sql` diff 0(픽스처에 그 이름 없음). corpus `masking-egov.json`·`-candidates.txt` — 탐색으로는 `TEL_NO`·`PASSPORT`·`DRV_LIC` 0 건이라 diff 0 기대, 늘면 새 줄이 그 세 꼴인지 `R-34` 가 본다. `--db` `03.piiGuessed` 가 **느는 것**은 `V-67`
- 검증: `bash scripts/mvn.sh -q test -Dtest='MaskingTest,LogicalRoutesTest,DeliverableServiceTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 3-22. 레인 db(`resources/logical`) + corpus

### 1-111 — 메타 수집 잔손: 거절된 COUNT · Oracle 자동 NOT NULL (F-K11 · F-K22)
- 대상: `core/meta/RowCounter.java` · `core/dialect/OracleMetaSource.java` · `SnapshotServiceTest` · `VendorFallbackTest`
- 변경:
  - `RowCounter.fill:72` — `failed.merge(List.of(e.getSQLState() == null ? "" : e.getSQLState(), e.getErrorCode()), 1, Integer::sum);` → `failed.merge(List.of(state(e), e.getErrorCode()), 1, Integer::sum);`
    ```java
    /** 경고 열쇠의 SQLState — 읽기 전용 래퍼가 거절한 것(이름이 쓰기 낱말이거나 ; 가 든 표)은 REFUSED 로 따로 센다. 시간 예산으로 건너뛴 것("")과 안 섞인다(F-K11) */
    private static String state(SQLException e) {
        if (e instanceof java.sql.SQLFeatureNotSupportedException) {
            return "REFUSED";
        }
        return e.getSQLState() == null ? "" : e.getSQLState();
    }
    ```
    (`fill(3)` 복잡도 16 → 15 — 기준선에서 그 줄이 빠진다, 갱신. `core.conn` 을 import 하지 않는다 — 래퍼의 거절은 그 예외 형식이다)
  - `OracleMetaSource.AUTO_NOT_NULL:51` → `Pattern.compile("(?i)^\\s*(?:\"[^\"]+\"|[\\p{L}\\p{N}_$#]+)\\s+IS\\s+NOT\\s+NULL\\s*$")`(한글·공백 든 따옴표 이름)
- 시험(먼저): `SnapshotServiceTest.countsOnlyTablesWithoutStats` 꼴 — `refusedCountIsItsOwnWarning`: H2 에 `CREATE TABLE S."LOCK"(ID INT)` 와 평범한 표를 두고 스냅샷 → 경고에 `new MetaSource.Warning("rowCount", "REFUSED", 0, 1)` 이 있고 `("rowCount", "", 0, …)` 는 없다. `VendorFallbackTest` — `ALL_CONSTRAINTS` 흉내 표에 `"사원명" IS NOT NULL`·`"A B" IS NOT NULL`·`X > 0` 셋 → CHECK 는 `X > 0` 하나. RED: 경고 열쇠 `""` · CHECK 셋
- 의도된 골든 변화: `golden/meta/oracle.json` diff 0. `--db` 의 Oracle·Tibero 표본 CHECK 수가 **주는 것**은 `V-67`
- 검증: `bash scripts/mvn.sh -q test -Dtest='SnapshotServiceTest,VendorFallbackTest,SnapshotStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 db + corpus
- 사다리: H2 가 `"LOCK"` 표의 COUNT 를 래퍼 거절 없이 받으면(래퍼가 따옴표 이름을 통과시키면) 표 이름을 `"A;B"` 로 바꾼다

### 1-112 — SqlRunner: CLOB 상한 · 배열·XML 값 (F-K18 · F-K20)
- 대상: `core/sqlrun/SqlRunner.java`(`value`) · `SqlRunnerTest`
- 결정: 글 값은 **100만 자**까지 읽고 넘으면 앞부분 뒤에 `" …(CLOB " + 전체 길이 + "자 — 앞 1000000자만)"` 를 붙인다. 배열은 원소를 `value` 로 바꿔 `[a, b]` 꼴, SQLXML 은 글(같은 상한)
- 변경: `value:92-128` 의 Clob 가지 몸을 `return clob(c);` 로 빼고(읽기 루프가 `MAX_TEXT` 에서 멈춘다 — `static final int MAX_TEXT = 1_000_000;`), 끝 `return v.toString();` 앞에 두 가지 —
  ```java
  if (v instanceof java.sql.Array a) {
      return array(a.getArray());
  }
  if (v instanceof java.sql.SQLXML x) {
      return cut(x.getString());
  }
  ```
  `private static String array(Object arr) throws SQLException` — `Object[]` 면 원소마다 `String.valueOf(value(원소))` 를 `", "` 로 이어 `[…]`, 아니면 `String.valueOf(arr)`. `private static String cut(String s)` — `MAX_TEXT` 넘으면 자르고 꼬리. `value` 복잡도는 15 이하로(Clob 가지를 뺀 만큼 자리가 난다)
- 시험(먼저): `SqlRunnerTest` — `longClobIsCut`: H2 `CREATE TABLE L(C CLOB)` 에 150만 자를 넣고 `run` → 값 길이가 `1_000_000` + 꼬리, 꼬리에 `1500000자` · `arraysAndXmlAreValues`: **`ReadOnlyJdbc.wrap(c)` 로 감싼 연결**에서 `CREATE TABLE A(TAGS VARCHAR ARRAY)` + `INSERT … VALUES (ARRAY['x','y'])` → 값 `[x, y]`(`ReadOnlyJdbc(` 로 시작하지 않는다). RED: 150만 자 통째 · `ReadOnlyJdbc(JdbcArray)`
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlRunnerTest,ReadOnlyJdbcTest,InsertRoutesTest,DeliverableRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 db(`core/sqlrun`) + corpus
- 확인해 둔 것: 래퍼의 LOB 프록시(`ReadOnlyJdbc.Lob.handle:348-354`)는 `set*`·`truncate` 만 거절한다 — `getArray()`·`getString()` 은 지나간다. `core/conn` 은 안 고친다

### 3-24 — 사전: 내보내기 CSV 수식 · kind 검증 · 검색 와일드카드 (F-K15 · F-K16 · F-K23)
- 대상: `web/DictRoutes.java` · `core/dict/DictStore.java`(`words`) · `DictRoutesTest` · `DictStoreTest`
- 변경:
  - `/api/dict/export`(`:90-102`) — 머리에서 `if (!EXPORT_KINDS.contains(kind)) { ctx.status(400).json(Map.of("message", "kind 는 user·org·word")); return; }`(`Set.of("user", "org", "word")`). 라우트 몸이 길어지면 `private static void getExport(Context ctx, DictStore dict)` 로 뺀다
  - `csv(String)` — 첫 글자가 `=`·`+`·`-`·`@`·탭·CR 이면 앞에 `'` 를 붙인 뒤 인용 판정을 한다(엑셀이 수식으로 안 읽게). 인용 조건에 `\r` 을 더한다
  - `DictStore.words:360-378` — 검색어를 `like(q)` 로: `\`·`%`·`_` 앞에 `\` 를 붙이고 SQL 을 `… (UPPER(abbr) LIKE ? ESCAPE '\' OR word_ko LIKE ? ESCAPE '\') …` 로
- 시험(먼저): `DictRoutesTest.userDictAndLocalStorageImport` 꼴 — `exportEscapesFormulasAndChecksKind`: 사용자 사전에 한글 `=1+1`·`@x` 를 넣고 export → 그 칸이 `'=1+1`·`'@x` · `GET /api/dict/export?kind=a";x="b` → 400(넣은 낱말은 끝에 DELETE). `DictStoreTest.orgReplacesAndUserUpserts` 옆 `searchEscapesWildcards` — `putUser("upd", "수정")`·`putUser("a_b", "밑줄")` 뒤 `words("_", "user")` 가 `A_B` 하나 · `words("%", "user")` 가 0 건. RED: `=1+1` 그대로 · 200 · 둘
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='DictRoutesTest,DictStoreTest,LogicalRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus(`core/dict`)

### 0-108 — data 폴더 경로의 `;` (F-K19)
- 대상: `core/db/Db.java`(`open`) · `DbTest`
- 변경: `open` 머리(`Path dir = …normalize();` 다음)에 `if (dir.toString().indexOf(';') >= 0) { throw new IllegalStateException("data 폴더 경로에 ; 가 있다 — H2 가 설정으로 읽는다: " + dir + " (; 없는 폴더에 푼다)"); }`
- 시험(먼저): `DbTest.semicolonInPathIsRefused` — `Db.open(tmp.resolve("da;ta"))` → `IllegalStateException` 글에 `; 가 있다` · `tmp` 아래에 `*.mv.db`·`*.trace.db` 가 안 생긴다. RED: H2 예외(90046)나 형제 자리 파일
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='DbTest,MainTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 java(`core/db`)

### ⑤ 웹·CLI Minor

### 0-109 — fs 읽기: 잠긴 파일 · UTF-16 (G1-K19 · G1-K21)
- 대상: `core/fs/LocalFiles.java`(`read`) · `LocalFilesTest` · `FsRoutesTest`
- 사실: `read` 를 부르는 폴더 작업 다섯(`AnalyzeRunner:132`·`CheckRunner:165`·`CleanupRunner:115·180`·`LoggerShape:95`·`PackageTypes:82`·`AnalyzeRoutes:411`)은 `IOException` 과 `Refused` 를 같이 잡아 건너뛴다. 라우트 둘(`FsRoutes:58`·`DiffRoutes:34`)은 `Refused` 만 4xx 로 바꾼다 — 그래서 `read` 안에서 `Refused` 로 바꾸면 500 이 없어지고 폴더 작업은 그대로다
- 변경: `read:325-345` —
  - `Files.size(p)`·`Files.readAllBytes(p)` 를 `try { … } catch (IOException e) { throw new Refused(400, "파일을 못 읽었다 — 다른 프로그램이 쓰는 중이거나 권한이 없다", e); }` 로 감싼다(도우미 `private static byte[] bytes(Path p)` — 크기 검사 413 까지. Windows 의 구간 잠금은 `FileSystemException` 이 아니라 맨 `IOException` 으로 온다 — 그래서 `IOException` 전부를 잡는다. 「파일이 없다」 404 는 그 앞 `isRegularFile` 검사가 이미 낸다)
  - BOM 판정 앞에 UTF-16: `b.length >= 2` 이고 첫 두 바이트가 `FF FE` 나 `FE FF` 면 `throw new Refused(400, "UTF-16 파일은 못 읽는다 — UTF-8 이나 MS949 로 저장한다");`
  - `read` 위 javadoc 한 줄 — 「못 읽는 파일(잠김·권한·UTF-16)은 Refused(400)」
- 시험(먼저): `LocalFilesTest.readDetectsEncodingAndLineEnding` 옆 — `utf16IsRefused`: `FF FE` + UTF-16LE 바이트 파일 → `Refused` 400 글에 `UTF-16` · `lockedFileIsRefused`: `FileChannel.open(p, READ, WRITE)` + `channel.lock()` 을 잡은 채 `read` → `Refused` 400(Windows 에서만 — `os.name` 에 `win` 이 없으면 `Assumptions.abort`: 리눅스 잠금은 읽기를 안 막는다). `FsRoutesTest.refusalsCarryStatus` 에 UTF-16 파일 `GET /api/fs/read` → 400. RED: 깨진 글 200 · `IOException`
- 의도된 골든 변화: 없음(빠른 레인). corpus `LocalFilesCorpusTest`·`check-*`·`cleanup-*`·`analyze-*` 의 「못 읽음」 이 UTF-16 표본만큼 **느는 것**은 `V-67a`(그 파일이 UTF-16 BOM 인지 이력에)
- 검증: `bash scripts/mvn.sh -q test -Dtest='LocalFilesTest,FsRoutesTest,DiffRoutesTest,CheckRunnerTest,CleanupRunnerTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus(`core/fs`)

### 0-110 — fs 목록: 글롭 문법·별 수 · 정션·금지 폴더 (G1-K04 · G1-K13 · G1-K12)
- 대상: `core/fs/LocalFiles.java`(`list`) · 새 `core/fs/PathRules.java` · `LocalFilesTest`
- 결정: 이름 글롭 하나에 `*` 묶음은 **셋까지**(JDK 글롭 정규식의 역추적은 별 수에 지수 — `*a*a*a*a*b` 가 1.5초, 여섯이면 안 끝난다). 정션·링크 폴더는 목록 걷기에서 **안 들어간다**(실제 경로가 다르면). 금지 폴더(`data/`·프로필 폴더 등)는 다른 폴더를 훑다 만나도 건너뛴다
- 변경:
  - 새 클래스(`LocalFiles` 가 메서드 상한에 가깝다)
    ```java
    /** fs 층의 경로·글롭 꼴 판정(0-110·0-111) — LocalFiles 가 메서드 상한에 가까워 따로 둔다 */
    final class PathRules {
        static final int MAX_STARS = 3;
        /** 파일 이름 글롭 → 매처. 문법 오류·별 묶음이 MAX_STARS 를 넘으면 Refused(400) */
        static PathMatcher nameMatcher(String glob)
        /** 정션·링크로 다른 곳을 가리키는 폴더인가 — 속성이 other·link 이고 실제 경로가 자기와 다르다. 못 읽으면 참 */
        static boolean linked(Path dir, BasicFileAttributes a)
    }
    ```
    `nameMatcher` — `glob` 에서 `\*+` 묶음 수가 `MAX_STARS` 를 넘으면 `new LocalFiles.Refused(400, "글롭의 * 는 " + MAX_STARS + "개까지: " + glob)` · `FileSystems.getDefault().getPathMatcher("glob:" + glob)` 가 `IllegalArgumentException`(`PatternSyntaxException` 포함)·`UnsupportedOperationException` 이면 `Refused(400, "글롭을 못 읽었다: " + glob)`
  - `list:213` — `ms.add(PathRules.nameMatcher(g.trim()));`
  - `list` 의 `preVisitDirectory` 첫 조건 `if (!d.equals(root) && (hidden(d) || SKIP_DIRS.contains(String.valueOf(d.getFileName()))))` → `if (!d.equals(root) && skipDir(d, a))` · `private boolean skipDir(Path d, BasicFileAttributes a)` = 숨김 · `SKIP_DIRS` · `forbidden` 가운데 하나의 아래(`under(d, f)`) · `PathRules.linked(d, a)`(`list(4)` 복잡도 22 — 그대로이거나 내린다)
- 시험(먼저): `LocalFilesTest.listSortsFiltersAndSkips` 옆 — `badGlobIsRefused`: 글롭 `[`·`*.{jsp`·`*a*a*a*a*b` → `Refused` 400, `*.java`·`*Test*Impl*.java` 는 통과 · `listSkipsForbiddenAndJunctions`: `tmp/proj/a.txt`, `tmp/proj/data/`(= `new LocalFiles(tmp/proj/data)` 의 금지 폴더)에 `x.db`, `tmp/proj/loop` → `tmp/proj` 정션(`MakeTemplatesTest.link` 의 열두 줄 도우미를 이 시험 클래스에 베낀다 — 링크, 안 되면 `mklink /J`, 그것도 안 되면 `Assumptions.abort`) → `list(tmp/proj)` 가 `a.txt` 하나. RED: 500 꼴 예외 · `data/x.db`·`loop/a.txt` 가 보임
- 의도된 골든 변화: 없음. corpus 목록 수가 바뀌면(표본 안 정션·링크 폴더) 어느 폴더인지 `V-67a` 에서 이력에
- 검증: `bash scripts/mvn.sh -q test -Dtest='LocalFilesTest,FsRoutesTest,DiffRoutesTest,CheckRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 0-109. 레인 corpus
- 사다리: 규칙 글롭(`rules.yaml`)·프로필 글롭 가운데 `*` 묶음이 넷 넘는 것이 있어 기존 시험이 400 이면 — `MAX_STARS` 를 그 수로 올리지 말고 멈춘다(7절)

### 0-111 — fs 쓰기 자리: rel 금지 글자·끝 점 · 없는 경로의 실제 경로 (G1-K06 · G1-K08 · G1-K07)
- 대상: `core/fs/LocalFiles.java`(`checkRel`·`check`) · `core/fs/PathRules.java` · `core/gen/Generator.java`(계획 단계 한 줄) · `LocalFilesTest`
- 변경:
  - `checkRel:457-463` — 지금 조건에 `|| !PathRules.segments(r)` 를 잇는 대신(분기 수) 조건 전체를 `PathRules.relOk(r)` 로 옮긴다: 비었거나 `/` 로 시작·드라이브 꼴·`/../` 는 지금대로 거절, 더해서 **조각마다** — 빈 조각 · 끝이 `.` 이나 공백(Windows 가 떼어 다른 이름이 된다 — `결과_출처.txt.` 가 표식을 덮는다, `...`) · `<>:"|?*` 나 0x20 미만 글자가 있으면 거절. 거절 글은 지금 것 그대로 「결과 폴더 안 상대 경로만」 + ` — 금지 글자·끝 점·끝 공백 없이`
  - `check:162-168` — `if (Files.exists(p)) { try { refuseForbidden(p.toRealPath()); } … }` → `refuseForbiddenReal(p);`
    ```java
    /** 가장 가까운 「있는」 조상의 실제 경로로 금지 폴더를 다시 잰다 — 아직 없는 새 폴더도 정션·끝 점(data.)으로 비켜 가지 못한다(G1-K07) */
    private void refuseForbiddenReal(Path p) {
        Path near = p;
        while (near != null && !Files.exists(near)) {
            near = near.getParent();
        }
        if (near == null) {
            return;
        }
        try {
            Path real = near.toRealPath();
            refuseForbidden(near.equals(p) ? real : real.resolve(near.relativize(p)));
        } catch (IOException e) {
            throw new Refused(400, "경로를 못 읽었다", e);
        }
    }
    ```
  - `Generator` 의 계획 단계 try(`:86-98` — 이미 `IOException | LocalFiles.Refused` 를 경고로 바꾼다) 안에서 경로마다 `LocalFiles.checkRel(경로)` 를 부른다 — 쓰기(`:112`) 도중에 처음 거절되는 일이 없게
- 시험(먼저): `LocalFilesTest.writeResultRefusesEscape` 의 목록에 `"a?.jsp"`·`"a/b<c.txt"`·`"..."`·`"a /b.txt"`·`"x.txt."`·`LocalFiles.MARK + "."` 를 더한다(전부 `Refused` 400, 표식 파일 첫 줄 그대로) · `refusals` 에 `files().check(tmp.resolve("data.").resolve("new").toString())`(data 가 금지 폴더일 때)와 정션 `jdata → data` 아래 `jdata/new` → `Refused` 400. RED: `InvalidPathException`·표식 덮임·통과
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='LocalFilesTest,FsRoutesTest,CleanupRunnerTest,GeneratorTest,GenerateRoutesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 0-110. 레인 corpus(+ `core/gen` — db)

### 0-112 — 프로필: 이름 검사 · 저장 원자성 (G1-K09 · G1-K20)
- 대상: `core/profile/Profile.java` · `core/profile/ProfileStore.java` · `web/ProfileRoutes.java` · `ProfileStoreTest` · `ProfileRoutesTest`
- 변경:
  - `Profile` 생성자(`:30-32`) — `connections = …` 앞에 `folderName(name);`
    ```java
    /** name 은 out/<이름>/ 폴더 조각으로 쓰인다 — 경로 글자·「..」·끝 점·앞뒤 공백이 있으면 거절(G1-K09). null 은 그대로(이름 없는 프로필) */
    static void folderName(String name) {
        if (name != null && (BAD_NAME.matcher(name).find() || name.contains("..") || !name.equals(name.strip()) || name.endsWith("."))) {
            throw new IllegalArgumentException("name: 폴더 이름으로 쓴다 — / \\ : * ? \" < > | 와 「..」·끝 점·앞뒤 공백 없이");
        }
    }
    private static final java.util.regex.Pattern BAD_NAME = java.util.regex.Pattern.compile("[/\\\\:*?\"<>|\\p{Cntrl}]");
    ```
    (`output.dir` 은 어디든 가리키는 설정이라 그대로 둔다)
  - `ProfileStore.saveCodeCheck:192` — `Files.writeString(file, …)` → `replace(file, crlf ? text.replace("\n", "\r\n") : text);`
    ```java
    /** 임시 파일에 쓰고 바꿔 끼운다 — 읽는 쪽이 반쯤 쓴 파일을 안 본다(G1-K20). 원자적 이동이 안 되는 파일 시스템이면 그냥 덮는다 */
    private static void replace(Path file, String text) throws IOException
    ```
    임시 파일 이름 `<파일 이름>.tmp`(같은 폴더 — `list()` 는 `.yaml` 로 끝나는 것만 본다). `Files.move(tmp, file, REPLACE_EXISTING, ATOMIC_MOVE)`, `AtomicMoveNotSupportedException` 이면 `REPLACE_EXISTING` 만
  - `ProfileRoutes` PUT(0-106 이 뺀 `putCodeCheck`) — 백업 이름 정하기부터 저장까지를 `synchronized (store) { … }` 로(같은 초의 두 저장이 백업 이름을 다투지 않는다)
- 시험(먼저): `ProfileStoreTest.networkPathsInProfileAreRejected` 옆 `nameMustBeAFolderName` — `name: ../x`·`name: a/b`·`name: "x."`·`name: " x"` 는 `UncheckedIOException` 글에 `name:` · `name: 사업 A-1(시험)` 은 통과. `ProfileRoutesTest.concurrentSavesAndReads` — 스레드 둘이 PUT 열 번씩(묶음 값만 바꿈), 한 스레드가 `GET /api/profiles/t` 를 그동안 반복 → 응답이 전부 200. RED: `out/..` 로 통과 · 400·500 섞임
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='ProfileStoreTest,ProfileRoutesTest,CheckRoutesTest,SmokeHtmlUnitTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 0-106. 레인 java(`core/profile`·`web`)
- 사다리: 기존 시험 프로필 이름이 새 검사에 걸리면 — 그 이름이 무엇인지 이력에 적고, 금지 글자 목록에서 그 글자를 빼지 말고 멈춘다(7절)

### 0-113 — 작업 목록: 오래된 끝난 작업부터 지운다 (G1-K11)
- 대상: `core/job/JobManager.java` · `JobManagerTest`
- 변경: 필드 `private final java.util.Deque<String> order = new java.util.concurrent.ConcurrentLinkedDeque<>();` · `submit` 의 `jobs.put(job.id(), job);` 다음 `order.add(job.id());` · `prune:138-143` 을
  ```java
  /** 작업이 KEEP_FINISHED 에 닿으면 끝난 작업을 오래된 것부터 절반까지 지운다 — 방금 끝난 작업은 남는다(G1-K11) */
  private void prune() {
      if (jobs.size() < KEEP_FINISHED) {
          return;
      }
      int drop = jobs.size() - KEEP_FINISHED / 2;
      for (java.util.Iterator<String> it = order.iterator(); it.hasNext() && drop > 0;) {
          Job j = jobs.get(it.next());
          if (j == null) {
              it.remove();
          } else if (j.finished()) {
              jobs.remove(j.id());
              it.remove();
              drop--;
          }
      }
  }
  ```
- 시험(먼저): `JobManagerTest.pruneKeepsRecentFinishedJobs` — `steps(1, 0)` 작업 200 개를 내고 전부 `awaitFinished`, 마지막 작업 id 를 적어 둔 뒤 하나 더 낸다 → `jobs.get(마지막 id)` 가 있다 · `jobs.get(첫 id)` 는 없다. RED: 마지막 id 도 없음
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='JobManagerTest,JobSseTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 java(`core/job`)

### 4-40 — jsp 일괄: 줄끝 섞인 파일은 안 쓴다 (G1-K22)
- 대상: `src/main/resources/tools/jsp_formatter_ext.js` · `core/fs/LocalFiles.java`(클래스 javadoc 한 줄) · `SmokeHtmlUnitTest`
- 사실: 번들 85(5-113)가 `Text.mixed` 를 더하고 코드 정리만 건너뛰게 했다. jsp 일괄(`jsp_formatter_ext.js:71`)은 `t.mixed` 를 안 읽고 `/api/fs/result` 에 다수 쪽 줄끝을 보내 안 고친 줄까지 바뀐다
- 변경: `:71` 의 `var row = { rel: f.rel, path: path, encoding: t.encoding, lineEnding: t.lineEnding };` 다음 줄에 `if (t.mixed) { row.err = '줄끝이 섞인 파일 — 쓰면 전 줄이 한 줄끝으로 바뀐다, 건너뜀'; DIR.rows.push(row); … 다음 파일로; return; }`(그 콜백의 「다음 파일」 로 넘어가는 지금 꼴 그대로 — `try` 블록을 건너뛰게만). `LocalFiles` 클래스 javadoc `:33` 의 「읽은 인코딩·줄바꿈 그대로」 → 「읽은 인코딩·줄바꿈 그대로(줄끝이 섞인 파일은 `mixed` 로 알린다 — 쓰는 쪽이 건너뛴다)」
- 시험(먼저): `SmokeHtmlUnitTest.jspFormatterFolderBatch` — 폴더에 `mixed.jsp`(`"<a>\r\n<b>\n<c>\r\n"` 에 정리될 꼴)를 하나 더 두고, 검사 뒤 그 줄의 사유 칸에 `줄끝이 섞인 파일` 이 보이고, 쓰기 뒤 결과 폴더에 `mixed.jsp` 가 없다. RED: 결과 폴더에 생김
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='SmokeHtmlUnitTest,ToolsFolderTest,LocalFilesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 0-111(같은 파일 `LocalFiles`). 레인 tools + corpus
- 한계(설계 문서에만): 줄마다 줄끝을 지켜 쓰기는 안 한다(설계 70 범위 밖 그대로)

### 0-114 — 프로필 YAML: 별칭·병합 키·인코딩 사유 · 중복 키·둘째 문서 · codecheck 머리 꼴 (G2-K3 · G2-K4 · G2-K5)
- 대상: `core/profile/ProfileStore.java` · `ProfileStoreTest`(+ `ProfileRoutesTest.conflictLeavesNoBackup` 은 그대로 초록이어야 한다)
- 결정: 프로필 YAML 은 **한 문서 · 키 하나씩 · 별칭 없이** 만 받는다. 별칭(`*이름`)·병합 키(`<<`)·같은 키 둘·둘째 문서(`---`)는 읽기를 거절하고 사유를 말한다(값이 조용히 사라지거나 앵커 이름이 값이 되는 것보다 낫다). 흐름 꼴 `codecheck: {…}` 머리는 저장이 409(손으로 블록 꼴로)
- 변경:
  - `YAML` 매퍼(`:29-34`) — `YAMLFactory.builder()` 에 `.enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)` · 매퍼에 `.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)`
  - `load:55-69` — `Files.readString` 의 `catch (IOException e)` 앞에 `catch (java.nio.charset.CharacterCodingException e) { throw new UncheckedIOException("프로필을 못 읽었다: " + file + " — UTF-8 이 아니다(UTF-8 로 저장한다)", e); }` · `YAML.readValue` 앞에 `refuseAliases(text, file);`
    ```java
    /** 별칭(*이름)·병합 키(<<)는 Jackson YAML 이 안 풀어 값이 틀리게 읽힌다 — 줄·칸만 말하고 거절(G2-K3). 원문은 안 싣는다 */
    private static void refuseAliases(String text, Path file)
    ```
    — `YAML.getFactory().createParser(text)` 로 토큰을 훑어 `((com.fasterxml.jackson.dataformat.yaml.YAMLParser) p).isCurrentAlias()` 면 `"<줄>줄 <칸>칸 근처 별칭(*이름)은 안 받는다 — 값을 그대로 적는다"`, 필드 이름이 `<<` 면 `"… 병합 키(<<)는 안 받는다"` 로 `UncheckedIOException("프로필을 못 읽었다: " + file + " — " + 사유, new IOException("alias"))`. 훑다 난 `IOException` 은 무시한다(형식 오류는 뒤의 `readValue` 가 지금 글로 낸다)
  - `reason:72-82` — `UnrecognizedPropertyException` 가지 다음에 둘: 글이 `Duplicate field` 로 시작하는 `JsonParseException` → `at + "같은 키가 두 번: " + 그 키`(글의 작은따옴표 안) · 글이 `Trailing token` 을 품은 `MismatchedInputException` → `at + "문서가 둘 이상이다 — --- 로 나눈 뒷부분을 지운다"`
  - `saveCodeCheck` — ① 머리 찾기 정규식(`:161`) `"codecheck:\\s*(#.*)?"` → `"\\uFEFF?(?:codecheck|\"codecheck\"|'codecheck'):\\s*(#.*)?"`(따옴표 키·BOM 첫 줄) ② `:183` `Profile after = YAML.readValue(text, Profile.class);` → `Profile after = reread(text);` — `private static Profile reread(String text)` 가 `JsonProcessingException` 을 `IllegalStateException("codecheck 만 바꾸려 했는데 YAML 이 깨진다 — 쓰지 않았다(YAML 모양을 손으로 고칠 것)")` 로 바꾼다(흐름 꼴 머리·따옴표 하위 키에 둘째 블록이 붙으면 중복 키 → 409). `saveCodeCheck(3)` 복잡도 28 — 그대로
- 시험(먼저): `ProfileStoreTest` — `aliasesMergeKeysDuplicatesAndSecondDocumentAreRefused`: `"name: a\nx: &u jdbc\nconnections:\n  - id: p\n    url: *u\n"`(별칭) · `"base: &b {id: p}\nname: a\nconnections:\n  - <<: *b\n"`(병합) · `"name: a\nconnections: []\nconnections: []\n"`(중복) · `"name: a\n---\nname: b\n"`(둘째 문서) → 넷 다 `UncheckedIOException`, 글에 차례로 `별칭`·`별칭` 또는 `병합 키`·`같은 키가 두 번: connections`·`문서가 둘 이상` · 어느 글에도 YAML 원문 값이 없다 · `cp949ReasonIsReadable`: MS949 바이트로 쓴 `name: 가` → 글에 `UTF-8 이 아니다`. `saveCodeCheckSurvivesColumnZeroComments` 의 꼴 루프에 `"name: a\n\"codecheck\":\n  groups: { java: true }\n"`·`"\uFEFFname: a\ncodecheck:\n  groups: { java: true }\n"` → 저장 뒤 `codecheck` 머리 줄이 하나 · `"name: a\ncodecheck: {groups: {java: true}}\n"` → `IllegalStateException`(파일 바이트 그대로). RED: 넷 다 읽힘 · 머리 둘
- 의도된 골든 변화: 없음. `profiles/example.yaml` 은 그대로 읽힌다(별칭·중복 없음 — 탐색 확인)
- 검증: `bash scripts/mvn.sh -q test -Dtest='ProfileStoreTest,ProfileRoutesTest,SelfTestCliTest,MainTest,SmokeHtmlUnitTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 0-112(같은 파일). 레인 java
- 사다리: `STRICT_DUPLICATE_DETECTION` 이 YAML 파서에서 안 들으면(중복 시험이 여전히 읽힘) — `refuseAliases` 의 토큰 훑기에서 객체 깊이마다 `Set<String>` 으로 필드 이름 중복을 직접 잰다(같은 사유 글). `conflictLeavesNoBackup` 이 409 가 아니면 `reread` 가 그 예외를 못 바꾼 것 — 예외 형식을 넓힌다(`IOException`)

### 8-19 — CLI: 입력 오류는 스택 대신 `[오류]` (G2-K7 · G1-K16)
- 대상: `cli/Main.java`(`commandLine`) · `cli/Serve.java` · `BatchCliTest` · `MainTest`
- 변경:
  - `Main.commandLine()` — 만든 `CommandLine` 에 `setExecutionExceptionHandler(Main::inputError)`:
    ```java
    /** 사용자가 준 경로·파일 탓인 예외는 스택 대신 한 줄과 끝 코드 2. 그 밖은 picocli 기본 처리(스택)로 다시 던진다 */
    static int inputError(Exception e, CommandLine cmd, CommandLine.ParseResult parsed) throws Exception {
        String why = e instanceof java.nio.file.NoSuchFileException n ? "파일이 없다: " + n.getFile()
                : e instanceof java.nio.file.AccessDeniedException a ? "파일을 못 연다(권한·잠김): " + a.getFile()
                : e instanceof java.nio.file.InvalidPathException p ? "경로를 못 읽었다: " + p.getInput() : null;
        if (why == null) {
            throw e;
        }
        cmd.getErr().println("[오류] " + why);
        return 2;
    }
    ```
  - `Serve.call` — 쓰기 검사 앞에 `if (port < 0 || port > 65535) { System.err.println("[오류] --port 는 0~65535: " + port); return 2; }`(그 파일의 다른 `System.err` 줄과 같은 `tb-ignore common.sysout` 꼬리) · `App.start(…)` 의 `catch (Db.LockedException e)` 다음에 `catch (IllegalStateException e) { System.err.println("[오류] " + e.getMessage()); return 1; }`(H2·data 폴더·사전 적재 실패 — 0-108 의 `;` 경로 글도 여기로)
- 시험(먼저): `BatchCliTest.failures` 에 — `fx.run("api", "GET", "api/ping", "--body-file", "nosuch.json")` → 끝 코드 2 · err 가 `[오류] 파일이 없다` 로 시작하고 `at ` 줄(스택)이 없다. `LogicalCliTest.failures` 에 `--csv nofile.csv` 같은 단언. `MainTest.serveRefusesMissingProfile` 꼴로 `serveRefusesBadPort` — `--port 65536`·`--port -5` → 끝 코드 2, 글에 `--port 는 0~65535`. RED: 끝 코드 1 + 스택
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='BatchCliTest,LogicalCliTest,MainTest,CodeCliTest,DocCliTest,ReadmeCommandsTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 0-108. 레인 java(`cli`)

### 8-20 — CLI: 낡은 활성 프로필 · selftest 의 못 읽는 프로필 (G2-K6 · G2-K8)
- 대상: `cli/Batch.java`(`open`) · `cli/SelfTest.java` · `BatchCliTest` · `SelfTestCliTest`
- 변경:
  - `Batch.open:109-115` — 이름이 `--profile` 에서 온 것이면 지금처럼 없을 때 끝 코드 2. **활성 파일에서 온 이름**이 없으면: `needProfile` 이면 지금 글로 끝 코드 2, 아니면 `err().println("[경고] 활성 프로필 " + name + " 의 파일이 없다 — 프로필 없이 연다");` 하고 `name = null`(`Serve:57-61` 과 같은 처분)
  - `SelfTest.body:123-137` 의 ⑬ 블록을 `private void profiles()` 로 통째 옮기고(`body(0)` 복잡도 25 — 내린다, 기준선 갱신) 못 읽은 프로필을 따로 모은다: `plain` 에는 평문 비밀번호만, `broken` 에는 `name + "(" + 사유 + ")"`(사유 = `UncheckedIOException` 글의 `" — "` 뒤, 없으면 예외 단순 이름). 끝에 `check(plain.isEmpty(), true, "프로필 비밀번호", …지금 글…)` 다음 `check(broken.isEmpty(), false, "프로필 읽기", broken.isEmpty() ? "전부 읽힌다" : String.join(" · ", broken) + " 을 못 읽었다 — YAML 을 고친다");`(실패 항목 — 끝 코드에 듣는다)
- 시험(먼저): `BatchCliTest.reviewFollowUps` 옆 `staleActiveProfileDoesNotBlock` — `data/active-profile` 에 `gone` 을 쓰고 `fx.run("snapshots")`·`fx.run("api", "GET", "api/ping")` → 끝 코드 0 · err 에 `[경고] 활성 프로필 gone`, `fx.run("check", "x")`(프로필이 필요한 명령)는 끝 코드 2. `SelfTestCliTest.plainPasswordWarns` 옆 `unreadableProfileFails` — `profiles/bad.yaml` 에 `name: [` → `selftest` 출력에 `[실패] 프로필 읽기 — bad(` 가 있고 끝 코드가 0 이 아니다 · `프로필 비밀번호` 줄에 `bad` 가 없다. RED: 끝 코드 2 · `[경고] 프로필 비밀번호 — bad 못 읽음`
- 의도된 골든 변화: 없음
- 검증: `bash scripts/mvn.sh -q test -Dtest='BatchCliTest,SelfTestCliTest,MainTest,CodeCliTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 8-19. 레인 java
- 사다리: `needsProfile()` 이 참인 명령이 무엇인지는 각 명령 클래스가 정한다 — 시험의 「프로필이 필요한 명령」 은 `check` 가 아니면 `needsProfile()` 을 재정의하지 않은 명령 하나로 바꾼다

### 8-21 — CLI: `check` 의 못 읽은 파일 · `deliverable --docs` 모르는 번호 (G2-K13 · G2-K15)
- 대상: `cli/CodeCommands.java`(`Check`) · `cli/DocCommands.java`(`Deliverable`) · `README.md`(두 줄) · `CodeCliTest` · `DocCliTest`
- 결정: 못 읽은 파일은 **알리기만** 한다(사용자 2026-11-21 (가)) — 요약에 수, stderr 에 `[경고]` 한 줄. 끝 코드는 지금처럼 지적 등급만 본다(소스 트리에 10MB 넘는 데이터 파일이 있는 사업의 형상 훅이 안 깨진다). `--docs` 의 모르는 번호는 끝 코드 2(`--groups` 오타와 같은 처분), 한 자리 수는 앞에 0 을 붙인다
- 변경:
  - `Check.summary:197-207` — `parseErrors` 줄 앞에 `if (r.path("skipped").asInt() > 0) { sb.append(" · 못 읽음 ").append(r.path("skipped").asInt()); }`
  - `Check.body` — `batch.print(r, sb.toString());` 앞에 `warnUnread(r);` · `private void warnUnread(JsonNode r)` — `skipped > 0` 이면 `batch.err().println("[경고] 못 읽은 파일 " + n + "개 — 검사하지 못했다(잠김·10MB 초과·사라짐)")`. 끝 코드 식(`failed(bySeverity) ? 3 : 0`)은 안 고친다. 기준선 저장(`--write-baseline`) 길도 같은 경고를 낸다
  - `Deliverable.body` — `putIf(b, "docs", docs);` 를 `putIf(b, "docs", docNumbers());` 로: `docs` 가 null 이면 null. 값마다 `trim`, 숫자 한 자리면 `"0" + 값`. `GET /api/deliverable/docs` 가 내는 번호에 없으면 `throw batch.fail(2, "모르는 문서 번호: " + 값 + " — " + 아는 번호들)`
  - `README.md:42` 의 `check` 설명 끝에 「못 읽은 파일은 요약의 `못 읽음 N` 과 `[경고]` 로 알린다(끝 코드는 안 바뀐다)」
- 시험(먼저): `CodeCliTest.checkFailOnAndXlsx` 옆 `unreadFilesAreReported` — `tmp/check` 에 `Big.java` 를 `RandomAccessFile.setLength(LocalFiles.MAX_READ + 1)` 로 만들고 `fx.run("check", "check", "--profile", "t")` → out 에 `못 읽음 1` · err 에 `[경고] 못 읽은 파일 1개` · 끝 코드는 그 파일이 없을 때와 같다(`--fail-on error` 로도 — 지적이 없으면 0). `DocCliTest.deliverableBuildsXlsx` 옆 `unknownDocNumberIsRefused` — `--docs 01,99` → 끝 코드 2 · err 에 `모르는 문서 번호: 99`, `--docs 1,2` → 끝 코드 0 · 파일 둘. RED: `못 읽음`·`[경고]` 없음 · 0
- 의도된 골든 변화: 없음(`CliCorpusTest` 는 `--docs` 없이 돈다)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CodeCliTest,DocCliTest,BatchCliTest,ReadmeCommandsTest,SelfCheckTest'` → `bash scripts/verify.sh`(docs 레인 — `doc-lint`)
- 의존: 8-20 · 2-48. 레인 java + docs

### 0-115 — 글: 예시 프로필 주석 · 라우트 주석 (G2-K12)
- 대상: `profiles/example.yaml`(`:15-16`) · `web/CheckRoutes.java`(`:526` javadoc)
- 변경: 예시 주석 두 줄을 「화면이 저장하는 것은 코드 검사 체크 상태뿐이다(`PUT /api/profiles/{이름}/codecheck` — `codecheck.groups`·`codecheck.rules` 두 줄만 갈아 끼우고 주석은 남는다, 앞 판은 `out/<프로필>/<시각>/backup/profiles/` 에)」 · 「그 밖 항목은 이 파일을 손으로 고친다」 로. `CheckRoutes.load` javadoc 의 「(저장은 화면이 PUT /profiles 로)」 → 「(저장은 화면이 `PUT /api/profiles/{이름}/codecheck` 로)」
- 시험: 새 시험 없음(주석만) — `ProfileStoreTest.exampleRoundTrips`·`exampleMapsEveryField` 초록 · `doc-lint`
- 검증: `bash scripts/mvn.sh -q test -Dtest='ProfileStoreTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: 0-114. 레인 java + tools(레인 표가 `profiles/` 를 어디에 두든 빠른 검증이 잰다)

### 4-41 — 표 편집기 JSX: style·불 속성·속성 안 `>`·주석 (G2-K11)
- 대상: `src/main/resources/tools/table_builder_ext.js`(`tbJsxStyle`·`tbJsxAttrs`·`tbJsxHtml`) · `SmokeHtmlUnitTest`
- 변경(ES5 그대로, `innerHTML` 대입 없음):
  - `tbJsxStyle` — `split(';')` 대신 한 글자씩 훑어 **따옴표·괄호 밖** `;` 에서만 끊는다. 훑기 앞에 `&quot;`·`&#39;`·`&amp;` 를 원래 글자로 되돌린다. 속성 이름이 `--` 로 시작하면 camelCase 로 안 바꾸고 `'--이름': 값` 으로(따옴표 친 열쇠)
  - `tbJsxAttrs` — 값 없는 속성(`m[2] === undefined`)은 이름만 낸다(`checked`·`disabled` — JSX 의 참). `=""` 로 내지 않는다
  - `tbJsxHtml` — 태그 정규식의 속성 부분 `([^>]*)` 을 따옴표 글을 통째로 넘는 꼴 `((?:"[^"]*"|'[^']*'|[^>"'])*)` 로. 맨 앞 대안으로 주석 `<!--[\s\S]*?-->` 을 더해 `{/* 안 글 */}` 로 낸다(안 글의 `*/` 는 `* /` 로)
- 시험(먼저): `SmokeHtmlUnitTest.tableBuilderJsxOutput` 옆 `tableBuilderJsxEdgeShapes` — 같은 페이지에서 `page.executeJavaScript("tbJsxHtml(" + JSON 글 + ")")` 로:
  - `<span style="font-family: &quot;A;B&quot;; background: url(data:image/png;base64,AA); --gap: 4px">x</span>` → `fontFamily: '"A;B"'`·`background: 'url(data:image/png;base64,AA)'`·`'--gap': '4px'`
  - `<input checked disabled>` → `<input checked disabled />`
  - `<span title="a> b">t</span>` → `<span title="a> b">t</span>`
  - `a<!-- 메모 -->b` → `a{/* 메모 */}b`
  기존 `tableBuilderJsxOutput`(골든 `table/jsx.txt`) 그대로. RED: `'&quot'` · `checked=""` · 깨진 태그 · `&lt;!--`
- 의도된 골든 변화: `golden/table/jsx.txt` diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='SmokeHtmlUnitTest,ToolsFolderTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 tools(+ corpus — `resources/tools`)
- 사다리: HtmlUnit 의 JS 엔진이 그 정규식을 못 받으면(뒤 참조·게으른 수량자는 ES5 다) 손 훑개로 바꾼다

### V-67a — 정적 분석 · corpus 골든 확인
1. `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify`(PMD·SpotBugs·ErrorProne) — 새 짝은 코드로 고친다(기준선에 안 더한다). 고치기 둘
2. `bash scripts/verify.sh --corpus`(7~12분). 허용 변화 —
   - `analyze-*.json`·`-unresolved.txt`·`-dead-stmt.txt`·`-impact.json`·`-jspurl.txt`·`-orphan-jsp.txt` — 프로그램·문장 참조·JSP 링크·표가 **느는 것**, `ambiguous`·`jspUrl` 미해결과 안 불리는 문장이 **주는 것**, `jpql` 미해결이 **느는 것**(연관 경로 — `별칭.속성` 꼴인지 본다), 조인 등식(`analyze-egov-joins.json`·`deliverable-egov-relations.json`)이 **주는 것**(SET 대입). `table` 미해결(등급 A)이 새로 나오면 7절
   - `sqlfixture.json`·`sqltables-diff.txt` — 「그쪽만」 이 **주는** 줄 · 「우리만」 이 느는 줄은 그 이름이 같은 파일 **다른 문장의 WITH 이름**일 때만(6-59)
   - `check-*.json`·`*-hits.txt` — `common.alert`·`consoleLog`·`debugger`(주석 처리한 스크립트 줄)·`security.sqlInjection`·`pathTraversal`(범위)이 **주는 것**, `mybatis.noWhere`·`mybatis.selectStar` 가 **느는 것**(서브쿼리 WHERE·조각 경계), `mybatis.dollar`·`security.sqlDollar` 는 서로 같은 수여야 한다(5-119)
   - `security-juliet.json`·`security-benchmark.json` — FP 가 **주는 것**만. TP 가 줄면 7절
   - `cleanup-*.json` — `RECEIVER` 건너뜀이 **주고** `steps.value` 가 **느는 것**(5-114) · console 지우기 수가 **주는 것**(주석 줄) · 등급 A(정리 뒤 컴파일·파싱)는 0
   - `ddl-unreadable.txt`·`ddl-summary.json` — 「못 읽은 줄」 이 **주는 것**(공백 든 따옴표 이름)
   - `masking-egov*.{json,txt}` — diff 0 기대, 느는 줄은 `passport`·`driver`·`TEL_NO` 꼴만
   - 파일 읽기·목록 수 — UTF-16 표본의 「못 읽음」, 정션 폴더가 빠진 것(어느 파일·폴더인지 이력에)
   - 그 밖(`gen-hr.json`·`hr-insert-b.txt`·`logsql-*`·`csv-*`·`strip-*`·`deliverable-*`(relations 빼고))은 diff 0 기대 — 바뀌면 원인 행의 「의도된 골든 변화」 와 댄다
   - 허용 밖 — 고치기 둘, 넘으면 `wip/V-67a`
3. diff 줄 수·종류별 건수·벽시계를 이력에
- 의존: 코드 행 전부

### R-34 — 손 판정 · 처분 표
- `design/compare-2026-10-28.md` 끝에 `## ㊲ 번들 88 — 보정 Important 여덟·리뷰 행 여섯·Minor·5-114(2026-11-21)`:
  1. 처분 표 — 이 문서 1절 표의 키 전부 × (행 · 강제 지점 시험 · 닫음/한계/wip). 「확인 — 닫힘」 키(1절)는 근거 커밋과 함께 한 줄씩. 한계(각 행 「한계」 줄)를 키마다
  2. `V-67a` 골든 변화 표(종류별 건수 전 → 후)
  3. 손 판정 —
     - **`cleanup-egov` 에서 새로 뒤집힌 equals 스무 줄**(파일:줄 — 코드 본문은 안 적는다): 수신자 getter 의 선언 반환 형식이 정말 `String` 인지 그 VO 파일에서 확인. 하나라도 아니면 7절
     - **`mybatis.noWhere` 새 줄 전부**: 서브쿼리 안에만 WHERE 가 있는 전체 갱신인지
     - **`jpql` 미해결 새 줄 열**: `별칭.속성` 이 엔티티 아닌 형식(임베디드·기본형 컬렉션)인지
     - **새 JSP 링크 열**(`.do` 아닌 것): 그 URL 을 가진 컨트롤러 매핑이 있는지
     - **`security.sqlInjection`·`pathTraversal` 에서 빠진 줄 전부**: 같은 이름 지역 변수의 다른 범위였는지
  4. 끝에서 끝 — `run.bat --port 18871 --no-browser`(빈 `--data-dir`) → `curl -s -X POST http://127.0.0.1:18871/api/fs/out -H "Transfer-Encoding: chunked" -H "Content-Type: application/json" -d '{"name":"a.txt","text":"x"}'` 가 411 · `curl -s "http://127.0.0.1:18871/api/dict/export?kind=zz"` 가 400 → 서버를 끈다
- 코드·레인 지문이 안 바뀐다
- 의존: V-67a

### V-67 — `--db`
1. 여유 RAM — PowerShell `(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB`. 15 미만이면 7절
2. `bash scripts/docker-up.sh` → `bash scripts/verify.sh --db`(컨테이너 아홉, 17~35분). 다른 JVM 을 같이 띄우지 않는다
3. 허용 변화 —
   - `golden/corpus/db-*.json` — `ddlGenWarnings` 가 **주는 것**(pg 대상 `TEXT` 키) · `03.piiGuessed` 가 **느는 것**(여권·운전면허·`TEL_NO`) · probe 통과가 **느는 것**·`SKIP_REFUSED`·`SKIP_NO_DIALECT_FILE` 이 **주는 것** · Oracle·Tibero 표본의 CHECK 수가 **주는 것**(한글 컬럼 자동 NOT NULL) · rowCount 경고의 열쇠가 `REFUSED` 로 **갈리는 것** — 방향과 수를 이력에
   - `db-*-insert-b.txt`·`db-*-probe-*.txt` — 실패가 **주는 것**
   - 기존 등급 A(`alterRoundTrip`·`ddlRoundTrip`·`rulesChecksAutoRoundTrip`·`dollarNameInsertRuns`·`maskingRuns`·COMMENT 실행)는 0 이어야 한다
4. `verify.sh --db` 와 `git push` 는 따로 낸다
- 의존: R-34

### 마무리
CLAUDE.md 「마무리」 1~8 그대로 — 독립 리뷰 하나(**`model: fable`, effort `xhigh`** — 서브에이전트 호출에 둘 다 명시. Review Focus: ① 「조용히」 가 남은 자리 — 고친 길에서 경고·미해결·400 없이 값이 빠지거나 바뀌는 곳 ② 고침이 넓어 맞던 출력이 바뀐 자리 — 골든 diff 를 행의 「의도된 골든 변화」 와 댄다 ③ `5-114b` 가 String 이 아닌 수신자를 뒤집는 길(오버로드·조상·익명 클래스·같은 이름 다른 패키지) ④ `5-125` 의 이름 바꿈이 `restore` 없이 새는 길(예외·이른 return) ⑤ `6-55` 의 `acc.recv` 가 되돌려지지 않는 길 ⑥ 절대 규칙 2·3 — 새 예외·경고·400 글에 YAML 원문·SQL 본문·값이 실리는 자리(`0-114` 사유 · `0-106` 규칙 글 · `4-37` 경고) ⑦ 절대 규칙 7 — `0-110`·`0-111` 뒤에도 금지 폴더·원본에 쓰는 길 ⑧ `0-107` — 자기 화면·CLI 가운데 chunked 로 보내는 호출) → `bash scripts/gate-probe.sh` → `verify.sh`(리뷰 고침이 건드린 무거운 레인만 다시 — db 레인 파일이면 새 행으로 돌린다) → push → PR(본문에 행마다 시작 세 줄 표) → CI 폴링 → claude[bot] 처분 → **머지 위임** `gh pr merge N --merge --delete-branch` → 끝 보고.

## 5. 금지 사항

- `pure/` · `ext-fixes.yaml` · `config/pmd/exclude-from-failure.properties` · `resources/quality/d90.json` · `golden/check/pure-rules.json` · 마이그레이션 V001~V017 을 고치지 않는다. 새 마이그레이션 없음
- 새 의존성 0(`javaparser-symbol-solver` 포함)
- `rules.yaml` 은 `security.sqlDollar` 의 `params` 한 줄만. 규칙을 더하거나 등급·글롭을 바꾸지 않는다(규칙 수 147)
- `Unresolved.KINDS` 를 늘리지 않는다
- `RegexRule.stripComments` 의 가지(jsp·xml·sql·properties)를 고치지 않는다 — 순수본과 같은 꼴이다. 고치는 것은 `cLike` 의 텍스트 블록 하나, 더하는 것은 `scriptComments` 하나
- self-check 기준선에 있는 메서드의 값을 올리지 않는다 · `JavaGraph`·`JpaIndex`·`Definitions`·`AlterGen.Run` 에 메서드를 더하지 않는다 · `JavaGraph.walk`·`JpaIndex.jpql`·`AnalyzeRunner.run`·`RuleSet.load`·`DeliverableService.build`·`XlsxFiller.fill`·`InsertGen.generate`·`csvLiteral`·`Validation.of`·`DdlReader.item`·`MapperIndex.Handler.startElement`·`MyBatisRules.Handler.startElement`·`SqlTables.extract`·`ProfileStore.saveCodeCheck` 에 분기를 더하지 않는다(행에 적힌 도우미로) · `GenModel.of` 의 줄 수를 늘리지 않는다
- 기존 경고·사유·미해결 글자를 바꾸지 않는다(골든·baseline·화면 열쇠) — 「날짜 모양(YYYY-MM-DD[ HH:MI[:SS]])이 아니다」·「PK 없음 — 첫 컬럼을 키로 가정」·「자동증가 컬럼: …」·「Parameters 줄이 없다 — 원문 그대로」·「매핑에 문서 … 가 없다」·「codecheck 만 바꾸려 했는데 다른 값도 바뀐다 …」·「시스템·서버 폴더는 다루지 않는다」. 새 글은 새 글자로(「결과 폴더 안 상대 경로만」 은 뒤에 덧붙이기만)
- 기존 시험 단언을 지우거나 느슨하게 바꿔 통과시키지 않는다. 바뀌는 단언은 행에 적힌 것뿐 — `CheckRulesTest.sqlDollarSkipsSqlComments`(이름·기대, `5-119`) · 픽스처 `NegMapper.xml:8-9` 를 양성으로(`5-119`) · `CleanupTest.equalsFirstNeedsStringReceiver` 의 16행(`5-114b`) · 주석 처리한 스크립트 줄을 지적·삭제하던 기대(`5-120`) · 픽스처를 더해 오르는 건수 단언
- `ValueAst` 의 판정을 바꾸지 않는다 · `5-114` 에 JDK 메서드 허용 목록·이어진 호출 추론·별 import 추론을 넣지 않는다
- `core/conn`(`ReadOnlyJdbc`)을 고치지 않는다 · `RowCounter` 가 래퍼를 비켜 가게 하지 않는다
- `LocalOnly` 가 chunked 본문을 받아 읽으며 세게 하지 않는다 — 거절한다
- 프로필 — 모르는 규칙 id 의 덮어쓰기를 거절하지 않는다 · `output.dir` 검사를 더하지 않는다 · 금지 글자 목록에서 글자를 빼 시험을 맞추지 않는다
- 보정 Minor 다섯의 자리(`Csv.parse`·`LineDiff`·`AlterGen.modifyColumn` 의 기본값·`InsertGen.camel`·`SqlJoins.aliases`)와 잠재 둘(`DdlGen.fk` 의 빈 참조·`DdlReader.fk` 의 규칙)을 같이 고치지 않는다
- 화면은 `dev_tools_ext.js`·`jsp_formatter_ext.js`·`table_builder_ext.js` 셋만. `tools/*.html`·`common.js`·`common.css` 를 고치지 않는다
- `MapperIndex` 픽스처 폴더(`fixtures/analyze/mapper/`)와 순수본과 맞대는 픽스처 폴더(`fixtures/check/{common,jsp,tsx,security,a11y}`)에 파일을 더하지 않는다
- 시험·경고·예외·400 글에 실제 사업 코드·SQL 본문·YAML 원문 값을 쓰지 않는다(절대 규칙 2·3)
- 다른 저장소(portfolio·satchel)를 고치지 않는다 · worktree·`finishing-a-development-branch` 를 쓰지 않는다

## 6. 최종 검증

- `bash scripts/verify.sh` 초록 · `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify` 초록 · `bash scripts/verify.sh --corpus` 초록(등급 A 0) · `bash scripts/verify.sh --db` 초록(컨테이너 아홉, 기존 등급 A 0) · `bash scripts/gate-probe.sh` 열 전부 산다
- 강제 지점 시험이 전부 있다(S0 표, `wip/` 로 뺀 행 빼고) — 각 행 이력에 RED → GREEN 한 줄
- 손 확인(R-34): compare ㊲ 처분 표 · 새로 뒤집힌 equals 스무 줄 · `mybatis.noWhere` 새 줄 · `jpql` 새 줄 · 새 JSP 링크 · 빠진 오염 지적 · chunked 411 `curl`
- 회귀: `golden/` diff 는 행마다 적은 「의도된 골든 변화」 와 `V-67a`·`V-67` 의 허용 변화뿐 · `pure-rules.json` diff 0 · 규칙 수 147 · 마이그레이션 17 · `Unresolved.KINDS` 20 · `self-check.txt` 는 내린 값·사라진 줄만
- CI 8 초록 · claude[bot] 「아니오」 처분 · PR 머지

## 7. 중단 조건

다음이면 임의로 풀지 말고 멈추고 보고한다(어느 스텝 · 무엇이 예상과 달랐나 · 가능한 선택지).
- 행의 「사실」·2절 구조 설명이 실제 코드와 다르다(줄 번호 어긋남은 아님 — 메서드·흐름이 다를 때)
- 검증이 2회 연속 실패하고 원인이 이 플랜 범위 밖
- 5절을 어기지 않고는 진행할 수 없다(특히 — 기준선 값을 올리거나 `JavaGraph`·`JpaIndex` 에 메서드를 더해야만 되는 고침)
- 스텝에 없는 파일을 3개 이상 고쳐야 한다(골든·픽스처·`self-check.txt` 는 스텝에 있는 것이다)
- `5-119` 에서 `pure-rules.json` 이 바뀐다
- `6-55` 뒤 분석 골든에서 문장이 **빠지거나** `depth` 미해결이 새로 나온다
- `0-107` 뒤 HtmlUnit 스모크·CLI(`LocalAppTest`)가 411 을 받는다
- `0-110` 뒤 기존 시험의 글롭이 400 · `0-112` 뒤 기존 시험 프로필 이름이 거절된다
- `V-67a` — `AnalyzeCorpusTest.egov` 의 `table` 미해결(등급 A)이 새로 나온다 · `security-juliet`·`security-benchmark` 의 TP 가 준다 · 정리 뒤 컴파일(`CleanupCompileCorpusTest`) 등급 A 가 생긴다 · 등급 B baseline 이 상한(표본의 2%)을 넘는다
- `R-34` — 새로 뒤집힌 equals 가운데 수신자가 `String` 이 아닌 것이 있다
- `V-67` — 기존 등급 A 가 새로 빨강 · `--db` 앞 여유 RAM 15GB 미만(다른 세션 컨테이너 — 사람이 조율한다)

번들 모드의 「갈려도 안 멈춘다」 는 위 밖에서만 선다. 행당 계획 밖 결정 둘까지, 넘으면 `wip/<청크>` 로 빼고 다음 행. 빠졌을 때 뒤 행 —
- `6-50` 이 빠지면 `6-51` 은 건너뛰고 `5-120` 이 `RegexRule.scriptComments` 를 자기 행에서 만든다
- `6-55` 가 빠지면 `6-56` 은 건너뛴다 · `6-52`~`6-54` 가운데 하나가 빠져도 뒤 JavaGraph 행은 친다(같은 시험 파일일 뿐)
- `0-106` 이 빠지면 `5-124` 가 `RuleSet.BadRule` 을 자기 행에서 만들고, `0-112` 의 `synchronized` 는 PUT 람다에 둔다
- `1-109` 가 빠지면 `4-38` 의 자동증가 시험은 `SERIAL` 과 스냅샷(`autoIncrement` 참) 꼴로만 단언한다
- `5-114a` 가 빠지면 `5-114b` 는 건너뛴다(같이 번들 89~)
- `2-47` 이 빠지면 `2-48`·`2-49` 는 `skipped` 에 직접 더하는 부분만 한다(`fill` 반환 꼴은 그 행들이 만들지 않는다 — `2-49` 의 머리 알림은 건너뛴다)
- `5-119` 가 빠지면 `5-122` 의 `characters` 는 `mybatis.dollar` 를 주석 지운 조각에 그대로 돌린다(지금 동작)
- `8-19` 가 빠져도 `8-20`·`8-21` 은 친다

## 8. 불확실 항목

사용자만 정할 수 있는 것 — 없음(범위·`5-119` (가)·`5-114` (가)·머지 위임·컷 없음·effort, 그리고 설계 중 물은 셋(`mybatis.dollar` (가) · `8-21` (가) · `0-114` (가)) 다 답 받음).

실행해야만 알 수 있는 것 — 실행자가 해당 스텝에서 확인한다.
- **첫 스텝 전에**: `git log --oneline -1`(S0 커밋) · `git status --short` 깨끗 · `docker ps`(다른 세션 컨테이너가 떠 있으면 `V-67` 앞 RAM 을 미리 본다)
- **Jackson YAML 이 `STRICT_DUPLICATE_DETECTION` 을 듣나**(시험 실행) — `0-114` 사다리
- **H2 가 `SELECT 1 WHERE 1=0 AND EXISTS (…)` 를 받나**(시험 실행) — `6-62` 사다리
- **Jetty 가 본문을 받기 전에 before 처리기를 돌려 머리만으로 411·413 을 내나**(시험 실행) — 안 내면 `0-107` 시험이 본문을 붙여 보낸다(chunked 는 `"7\r\n{\"x\":1}\r\n0\r\n\r\n"`)
- **POI 가 채운 행에 병합을 더할 때 겹침을 거절하나**(시험 실행) — `2-49` 사다리
- **HtmlUnit JS 엔진이 `4-41` 의 정규식을 받나**(시험 실행) — 사다리
- **정션·링크를 만들 권한이 있나**(시험 실행) — `0-110`·`0-111`(못 만들면 그 단언만 `Assumptions.abort`)
- **연관 경로 `jpql` 미해결·`.do` 아닌 링크·뒤집힌 equals 가 표본에서 얼마나 나오나**(`--corpus`) — `V-67a` 허용 변화 · `R-34` 손 판정
- **Oracle·Tibero 표본의 CHECK 수·probe 통과 수가 어떻게 바뀌나**(컨테이너) — `V-67`
- **행마다 self-check 값**(시험 실행) — `-Dtest=SelfCheckTest`. 공통 사다리

