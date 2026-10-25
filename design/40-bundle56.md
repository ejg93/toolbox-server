# 설계 40 — 번들 56: 코드 검사 정확도 넷 + CLI 기준선 + JDBC 문장 (Fable 설계 · Opus 실행, 2026-10-25)

## 맥락
2026-10-25 ProjectShop(Java 25·Boot 4.1·Next.js 16) 소스에 코드 검사·프로그램 분석을 읽기 전용으로 돌려 드러난 빈칸(설계 39 §6, `out/projectshop/20261025-140436/findings.tsv`)을 닫는다. 다음 사업이 React + Boot 라 거기서 그대로 나올 문제들이다. satchel 훅이 기존 코드베이스에 `--fail-on` 을 걸 수 있게 하는 기준선(8-17)도 같이. 파일 읽기 10-x 는 satchel 로 넘겼다(D8) — 건드리지 않는다.

**사용자 결정(2026-10-25, 뒤집지 않는다)**
- 0-71(시험 코드 미사용 import)은 **안 함** — 시험 코드라 이득이 작다. PLAN 행 상태를 「안 함(사용자 2026-10-25)」 으로 닫는다
- 6-35 는 조건부지만 **설계하고 번들 맨 뒤**에 둔다 — 사용자가 번들 전에 뺄 수 있게
- 5-50 은 **자바 규칙으로 바꾼다** — 순수본에서 `a11y.jsxInputLabel` 하나가 빠진다(재주입 한 번). JSP 짝 `a11y.inputLabel` 은 정규식 그대로(5-31 대기)

**보정에서 행과 달랐던 사실(설계 39 §6 를 고친다)**
- 5-49 의 원인은 **스프레드 하나**. 인라인 `type` 은 `JsImportRule.names` 가 이미 벗긴다(`CheckRulesTest:272`). ProjectShop `page.tsx:6` 도 같은 줄의 `...apiQueryOf(filter)` 때문이다. 코드 정리(`Cleanup`)가 같은 판정으로 import 를 **지우므로** 지금은 스프레드로만 쓰는 import 를 지워 버린다(드러난 것 — 5-49 가 같이 고친다)
- 5-50 의 ProjectShop 오탐 7건 중 5건은 `<label …>` **다음 줄** `<input …/>` — 줄 단위 정규식(`RegexRule.apply` 가 `split("\n")`)으로는 못 고친다
- 5-47 은 의존성 변경 0 — `javaparser-core 3.26.3`(m2 에 있음)에 `LanguageLevel.JAVA_21` 이 있다(`jre/bin/javap` 실측)

실행자는 Opus. 차례는 `S0` → `5-47` → `5-48a` → `5-48b` → `5-49` → `5-50a` → `5-50b` → `8-17a` → `8-17b` → `6-35a` → `6-35b` → `V-34` → 마무리. 가지 `work/2026-10-25f`(S0 커밋 때 main 에서 딴다). 번들 하나·PR 하나. 머지 위임 없음 — PR 열고 멈춘다.

---

## 1. 목표와 범위
- 한 문장: ProjectShop 실측이 드러낸 코드 검사의 미탐 둘(Java 21 구문·정적 import)·오탐 둘(tsx 스프레드·JSX 레이블)과 프로그램 분석의 빈칸 하나(JdbcClient·JdbcTemplate)를 닫고, CLI `check` 에 기준선을 더해 기존 코드베이스 훅에서 첫날부터 빨개지지 않게 한다.
- In scope: `JavaSource` 판, `JavaRules.imports`, `JsImportRule` 쓰임 판정, 새 자바 규칙 `JsxLabelRule`(규칙 id 유지 `a11y.jsxInputLabel`), 순수본 재주입 한 번, `core.check.Baseline` + CLI `--write-baseline`·`--baseline`, `JavaGraph` JDBC 싱크 + `Unresolved` kind `jdbc`, 픽스처·골든, 설계 40 문서·PLAN 행·번들 표·PROGRESS.
- Out of scope: 0-71(안 함) · 10-1~10-9(satchel) · JSP/HTML `a11y.inputLabel`·`selectLabel`(5-31) · 화면(code_check.html)에 기준선 UI · `--changed-only` 의 CI 동작 변경 · JDBC 문장의 「위험 문장」 탭·SELECT 컬럼(6-33·6-34 는 매퍼만) · JavaParser 판 올리기.

## 2. 현재 구조 요약(탐색 2026-10-25 — 에이전트 셋 + 실측)
- **`core/check/JavaSource.java:14-25`** — 파서 둘 `java17`·`java8`(인스턴스 필드, 스레드 비안전). `parse(text)`: 17 → 실패면 8 → 둘 다 실패면 17 결과(문제 줄용). 캐시 없음. `new JavaSource()` 는 `JavaRules:97`·`Cleanup:73`(→ `deleteJavaImports:580`·`OrderFix:94`)·`analyze/JavaGraph:162`·`analyze/JpaIndex:190` + 시험 넷. **판 설정은 이 한 곳**.
- **parseError** — `JavaRules.java:124-131`: `PARSE_ERROR="java.parseError"`, 첫 problem 줄·메시지 첫 줄 발췌, 나면 나머지 규칙 건너뜀. `rules.yaml:767-772` info. 시험: `CheckCorpusTest:79-88`(등급 A, `JAVAPARSER_LIMIT=Set.of()`, `golden/corpus/check-parse-limit.txt` 0바이트), `CheckStoreTest:79`(parseErrors 2 — `java/PosBroken.java`·`mybatis/PosBroken.xml`), `SelfCheckTest:60`(0), `golden/check/java.json:6-8`(`PosBroken.java:4`). 판끼리 결과 비교 시험은 없다.
- **`JavaRules.imports` `:245-265`** — `isAsterisk` → `java.starImport`(static 별표 포함) · `if (im.isStatic() || !on.containsKey("java.unusedImport")) continue;`(`:255`) · `usedNames(cu)`(`:268-284`) = 모든 `SimpleName` + import 밖 `Name` + **주석 단어**(`[^\w$]+` 분할). `add()`(`:232-236`) 줄 = `getBegin()`, 발췌 = 그 줄 원문. 정적 import 의 마지막 이름은 `im.getName().getIdentifier()`(메서드 호출·필드 참조는 `SimpleName` 으로 잡힌다).
- **`Cleanup`** — 자체 판정 없음. `deleteStage:156-191` 이 `st.cur` 의 `java.unusedImport` 줄을 받아 `deleteJavaImports:580-608`(다시 파싱 → 그 줄에서 시작하는 `ImportDeclaration`, 다른 코드·`tb-ignore` 있으면 skip, 아니면 줄째) → `isStatic` 검사 없음. 픽스처 `fixtures/cleanup/javaimport/Pos.java:7` `import static java.util.Collections.emptyList;`(`:12` 에서 **쓴다**) 주석 `:9` 「star·static 은 그대로」. 골든 `golden/cleanup/steps.json:236`.
- **Java 픽스처 시험은 `JavaRulesTest`**(`CheckRulesTest.fixturesGolden:83` 이 `java/`·`mybatis/`·`ext/` 를 건너뜀). `JavaRulesTest.run:37-45` 가 `fixtures/check/java` 전체를 한 Run 에, `fixturesGolden:48-67` 단언 셋 — `Neg*` 히트 0 · 켜진 java/metric 규칙은 양성에서 ≥1 · `GoldenFiles.assertJson("check/java.json", {file,line,rule})`. 지금 unusedImport 양성은 `PosController.java:3`(`import java.util.List;`), 정적 import 음성은 `NegController.java:3`·`NegTestNoAssert.java:3-4`.
- **`SelfCheckTest`** — `keys:43-53` `파일|규칙|발췌`(줄 번호 없음, 같은 키 `" #n"`), `mainMatchesBaseline` 이 `src/main` 만(`:28`·`:58`) `CorpusFiles.baseline(golden/check/self-check.txt, …)`(양방향 래칫, 133줄, unusedImport 0건). `src/main` 의 `import static` 은 4건(쓰이는지 미확인). `everyTestAsserts:77-85` 가 `src/test/java` 를 돌리되 `java.testNoAssert` 만 단언.
- **`JsImportRule`** — `rules.yaml:135-141` kind `jsUnusedImport`, 글롭 `*.js,*.jsx,*.ts,*.tsx,*.mjs`. `RuleSet.java:139`. `IMPORT`(`:14`) 줄머리 import 만. `statements(Source)`(`:53-`) 가 `RegexRule.stripComments` 한 글에서 import 구간을 공백으로 지운 `body` 를 만들고, 쓰임은 `:77` `Pattern.compile("(?<![\\w$.])" + quote(name) + "(?![\\w$])").matcher(body).find()` — **`.` 앞을 막아 `...name` 이 안 쓰임**. `names(clause):103-128` 이 `type ` 접두·`as` 처리(이미 됨). `Cleanup.java:618,674,701,719` 가 `statements`·`names` 재사용. 순수본에는 없다(`docs/pure-code-check.md:52`). 픽스처 `fixtures/check/tsx/pos.ts`(1·2줄 양성)·`neg.ts`, 골든 `golden/check/fixtures.json:330-337`.
- **`a11y.jsxInputLabel`** — `rules.yaml:613-620` **regex kind**, 글롭 `*.jsx,*.tsx`, `skipComments: true`, `<input\b(?=[^<]*>)(?!…type hidden|button|submit|image|reset…)(?![^<]*\b(?:id|title|aria-label|aria-labelledby)\s*=)` — 같은 줄에 닫힌 태그만(`:604` 주석), 여러 줄 태그는 미탐. regex kind 라 순수본에 있다 — `PureRulesJsonTest.goldenJson:64` 「정규식 규칙 수 64」, `pureHtmlEmbedsGolden`(순수본 `var RULES` = `golden/check/pure-rules.json`), `portableToJavaScript`, `PureCodeCheckTest`(픽스처 32 고정, `DIRS` 에 a11y), `PureFrozenTest`(`pure/MANIFEST` 해시). 재주입 절차(5-40, 설계 35 §2): `node scripts/pure-rules-inject.js`(기본 `C:/workspace/portfolio` — 존재 확인) → portfolio 커밋 → `bash scripts/sync-pure.sh`. 픽스처 `fixtures/check/a11y/pos.tsx:5`·`neg.tsx:5-7`, 골든 `fixtures.json:62-64`. 말뭉치 `golden/corpus/check-egov-react.json:2-5` 에 2건(파일 2). JSP 짝 `a11y.inputLabel`(`:538-545`)·`selectLabel`(`:546-553`) 은 손대지 않는다.
- **`RuleSet.java:137-140`** kind 분기 `regex`·`absent`·`jsUnusedImport`·`file`. `Rule` 인터페이스: `Def def()`·`List<Finding> apply(Source)`. `Finding(file, line, group, rule, severity, excerpt)`(`Finding.java:7`), `Finding.excerpt(줄)`(strip·160자), `EXCERPT`.
- **CLI `check`** — `cli/CodeCommands.java:21-84` `Check extends BatchCommand`: `folder`·`--changed-only`·`--groups`·`--xlsx`·`--fail-on`(값 검증 → `batch.fail(2,…)`), 공통 믹스인 `Batch.java` `--profile`·`--data-dir`·`--profiles-dir`·`--json`. 본문: `batch.job(batch.call("POST","/api/check/run", b))` → `r.path("findings")`(JsonNode 배열, 각 `file`·`line`·`rule`·`severity`·`excerpt`) → severity 수 → 요약 한 줄 → `batch.print(r, sb)`(`--json` 이면 응답 JSON 전체) → `--fail-on` 이면 등급 수 >0 에 `return 3`. 지적을 한 건씩 찍는 텍스트 출력은 없다. 경로는 `batch.userPath(folder)`(`Batch:257` cwd 기준). 끝 코드 규약 `Batch.java:28` 0·1·2·3. `file` 은 검사 루트 기준 `/` 상대 경로(`LocalFiles:180·194·207`).
- **CLI 시험** `cli/CodeCliTest.java` — `@BeforeEach` 가 `fixtures/check` → `tmp/check` 복사, `CliFixture.run(args…)`(`CliFixture:68-81`, `--data-dir`·`--profiles-dir` 덧붙임, `Run(code,out,err)`), 프로필 `t`. `checkFailOnAndXlsx:40-55` 가 0·3·2·1 을 잰다. `--changed-only` 시험 없음.
- **`--changed-only`** — `CheckRoutes:223-225` `WorkingCopy.changed(...)` → `changes::contains`; git 은 `status --porcelain=v1 -z --untracked-files=all`(`WorkingCopy:157-180`). 깨끗한 체크아웃이면 0 파일.
- **프로그램 분석** — `AnalyzeRunner.run:110`: java·xml·jsp 분리 → `MapperIndex.scan` → `JpaIndex.scan`(**낱말 필터** `persistence|Repository|springframework.data`, `:161`) → `JavaGraph.scan(java, naming, jpa, cancel)`(`:163`). `JpaIndex.empty()` 는 **새 인스턴스**(`:123`, 공유 아님). `JavaGraph.walk:366-441` — `SINKS`(`:89-90` selectList·selectOne·selectByPk·selectListWithPaging·list·insert·update·delete) + `sink()`(`:444-454` DAO 이름 없는 호출·`SqlSession*` 필드·`getSqlSession()`) → `statement():456-482`(`StringLiteralExpr`→`literal`, `PLUS` 맨 왼쪽 리터럴→`prefix`, `NameExpr`→`varLiterals`, 아니면 `note("statement")`). 그 밖 호출은 `field(c, name)`/`lookup` 으로 타입을 따라 재귀. EntityManager 경로 `:640-672`: `JpaIndex.literal(a0)` → `createNativeQuery` 면 `SqlTables.extract(q, null).refs()` → `jpa.record(c.fqcn+"."+m+"#em", refs)` + `Stmt(id,"jpa")`; **`!jpa.isEmpty()` 일 때만**(`:394·:408`). `Stmt(id, resolution)` resolution ∈ literal·prefix·var·mapper·jpa·qdsl. `AnalyzeRunner:180` 이 `jpa`·`qdsl` 을 `jpa.recorded(id)` 로 합친다.
- **`JpaIndex.literal(Expression, ClassOrInterfaceDeclaration owner, int depth)`**(`:608-631`) — `StringLiteralExpr`·`TextBlockLiteralExpr`·`EnclosedExpr`·`PLUS`(양쪽 다 풀릴 때만)·`NameExpr`(owner 의 `getFieldByName` 초기값, depth ≤ 8). `literal(e)`(`:603`) 는 owner 없음.
- **`Unresolved.KINDS`**(`Unresolved.java:28-58`) 닫힌 목록 — 모르는 kind 는 생성자가 거절(`:9,21-22`). 각 `Kind(이름, 뜻, 푸는 법)`.
- **`SqlTables.extract(text, tag)`**(`SqlTables.java:75`) → `Result(verb, refs, unresolved)`; `Ref(table, Set<Crud>)`. 매퍼는 `#{}` 자리표시자 — `:name` 이름 바인딩을 받는지는 **미확인**(§4 6-35b 검증에서 잰다).
- **골든·픽스처(analyze)** — `golden/analyze/*.json`(java-graph·jpa-graph·jpa-index·qdsl-graph·…), 픽스처 `fixtures/analyze/{java,jpa,jsp,mapper,qdsl/src/main/java/com/ex/qdsl/}`. `JavaGraphTest.qdslGolden:63-86` 이 새 폴더+골든 꼴의 선례: `Files.walk` → `new Source("qdsl/"+rel, text.replace("\r\n","\n"), null,null,null)` → `JpaIndex.scan` + `JavaGraph.scan(src,null,jpa)` → 접두로 거른 `programs`·`unresolved`·`recorded` → `GoldenFiles.assertJson`. 골든 갱신 `-Dgolden.update=true`.
- **ProjectShop 실물(픽스처 모델)** — Java 21: `Allowed.java:51-54` `switch (this) { case Everything<T> ignored -> true; case Only<T>(Set<T> values) -> …; }`(sealed + 제네릭 레코드 패턴), `Decider.java:90-95` `case Verdict.Granted(ScopedGrant winner, Allowed<String> groups) ->`(한정 레코드 패턴). tsx: `eslint.config.mjs:3-4` `nextVitals`·`nextTs` 가 `:80-81` `...nextVitals,` 로만 쓰임. JSX: `return-decision.tsx:61-62` `<label …>` 다음 줄 `<input type="radio" …/>`, `product-form.tsx:230` `<input {...shared} type={type} … />`, `product-actions.tsx:92` `<label><input type="radio" …/> 글</label>` 한 줄. JDBC: `JdbcClient` 73 파일, `jdbc.sql("literal")`·`jdbc.sql("""text block""")`(350)·상수 연결 `SELECT_TOTAL = "select count(*) from audit_log" + WHERE`(WHERE 는 text block 상수)·이름 바인딩 `:issuedBefore`·`returning user_id`.
- **레인** — `src/main/resources/check/rules.yaml`·`core/check`·`core/analyze`·시험·픽스처는 java 레인 + **corpus 레인**(`verify-fingerprint.sh:62-86` — 파서·규칙·`src/main/resources/tools`·`scripts/puppeteer`·pure). `pure/`·`golden/check/pure-rules.json` 도 corpus. 끝에 `--corpus` 한 번(V-34).
- **함정** ① `JavaRulesTest` 양성 파일 이름은 `Pos*`, 음성은 `Neg*`(접두 대문자) — `CheckRulesTest` 쪽은 `pos.*`/`neg.*`(소문자). ② `CheckStoreTest:79` 가 parseErrors 를 **정확히 2** 로 잰다 — 새 Java 픽스처는 파싱이 돼야 한다. ③ `golden/check/fixtures.json`·`java.json`·`pure-rules.json`·`cleanup/steps.json`·`corpus/check-*.json` 은 `-Dgolden.update=true` 로 다시 쓰되 **diff 를 보고 의도된 변화만**. ④ `PureRulesJsonTest:64` 숫자 64 와 지시문을 같이 고친다(→ 63). ⑤ 순수본 재주입은 portfolio 저장소를 건드린다 — 가지 `toolbox/rules-3`(설계 35 선례 `toolbox/rules-2`)에 커밋, main 머지는 사람 몫 → `sync-pure.sh` 는 그 커밋을 끌어온다(해시 이력에). ⑥ `Cleanup` 의 tsx import 삭제가 `JsImportRule.statements` 를 쓰므로 5-49 가 바뀌면 `golden/cleanup/steps.json` 의 tsx 단계가 바뀔 수 있다 — diff 확인.

## 3. 설계 결정 — 전부 [고정]
- **5-47** `JavaSource` 의 `JAVA_17` 을 **`JAVA_21` 로 바꾼다**(파서는 그대로 둘: 21 → 8). 세 번째 시도를 더하지 않는다 — 21 은 17 의 상위 집합(비프리뷰 추가는 패턴 switch·레코드 패턴만, 빠진 구문 없음)이라 17 로 되는 파일은 21 로 같은 AST. 증명은 V-34 corpus 골든 불변(`check-*.json`·`-hits.txt`·`analyze-*.json`·`cleanup`·`check-parse-limit.txt` 0). `JAVA_8` 폴백은 `record`·`var`·`yield` 를 식별자로 쓴 옛 코드용 — 유지.
- **5-48** `JavaRules.imports:255` 에서 `im.isStatic() ||` 를 뗀다. 정적 별표 import 는 지금처럼 `starImport`. 정적 import 의 쓰임 판정은 비정적과 같다(`usedNames` — 호출·필드 참조·애너테이션 값·주석 단어). `Cleanup` 은 **고치지 않는다** — 지적을 받아 지우므로 정적 미사용 import 도 지운다(안전: 비정적과 같은 판정, 주석에만 쓰여도 안 지운다). `src/main` 에 드러나는 정적 미사용 import 는 그 청크에서 지운다(기준선 0 유지).
- **5-49** `JsImportRule` 쓰임 판정 앞에 `body` 의 `...` 를 공백 셋으로 바꾼다(`body.replace("...", "   ")` — 길이 보존, `...` 는 JS 에서 스프레드·나머지뿐). 정규식은 그대로. 코드 정리가 같은 `statements` 를 쓰므로 스프레드로만 쓰는 import 를 지우는 결함도 같이 닫힌다.
- **5-50** `a11y.jsxInputLabel` 을 **kind `jsxInputLabel` 자바 규칙**(`JsxLabelRule`)으로 바꾼다. id·그룹·등급·message·fix·globs 유지, `regex`·`skipComments` 키 삭제. 판정(주석 제거 글, 여러 줄 태그 포함): `<input\b` 로 시작해 다음 `>` 까지를 태그 하나로(`>` 가 문자열·`{}` 안에 있을 수 있어 **중괄호 깊이 0 에서의 `>` 만**) → 뺀다: ① `type=` 값이 hidden·button·submit·image·reset(`"…"`·`{"…"}`·`{'…'}`) ② `id`·`title`·`aria-label`·`aria-labelledby` 속성 ③ 태그 안에 `{...` 스프레드 ④ **감싼 label** — 태그 시작 위치 앞에서 가장 가까운 `<label\b`…`>` 가 있고 그 뒤로 `</label>` 가 태그 시작 전에 없으면. 그 밖은 `<input` 줄에 지적 하나(발췌 그 줄). 순수본은 이 규칙을 잃는다 — `pure-rules.json` 63 으로 재생성 + 재주입(5-50b). JSP `a11y.inputLabel`·`selectLabel` 은 그대로(5-31).
- **8-17** `core.check.Baseline`(새 파일) 한 곳 — `static String key(file, rule, excerpt)` = `file + "|" + rule + "|" + excerpt`, `static List<String> keys(List<Finding>)`(같은 키 `" #n"` — `SelfCheckTest.keys` 와 같은 꼴, SelfCheckTest 는 이것을 부른다 → 기존 `self-check.txt` 가 그대로 통과해야 한다 = 꼴 증명), `static void write(Path, List<String> keys)`(정렬·UTF-8·LF·끝 개행), `static Set<String> read(Path)`(trim·빈 줄 제외), `record Diff(List<String> fresh, int hidden, int stale)` `static Diff diff(List<String> now, Set<String> base)`. CLI `check`: `--write-baseline <파일>`(검사 뒤 키를 쓰고 `기준선 저장 <경로> — N건` 출력, **`--fail-on` 무시 → 0**) · `--baseline <파일>`(파일 없으면 `batch.fail(2,…)`; `fresh` 만 `--fail-on` 에 셈; 텍스트 출력에 `기준선 <파일> — 가림 H · 새 지적 F · 사라짐 S` 한 줄 + 새 지적마다 `  <file>:<line> <severity> <rule> — <excerpt>`(50줄 넘으면 `  … 외 n건`); `--json` 이면 응답에 `baseline: {file, hidden, fresh:[finding…], stale}` 를 얹는다) · 둘 같이 쓰면 2. `--changed-only` 와 같이 쓰면 「사라짐」 은 `—`(안 본 파일이 있다). 사라진 항목은 **실패가 아니다** — 줄일 때 `--write-baseline` 으로 다시 뜬다(PMD 래칫 정책). 기준선 경로는 `batch.userPath` 로 푼다. 화면·라우트는 손대지 않는다(순수 CLI 후처리 — 키 함수는 core 에 두어 뒤에 라우트가 써도 된다).
- **6-35** `JavaGraph.walk` 에 **JDBC 싱크**를 더한다(MyBatis `SINKS` 와 별도, `jpa.isEmpty()` 와 무관하게 늘 본다): 호출 이름 `sql`(JdbcClient) 또는 `query`·`queryForObject`·`queryForList`·`queryForMap`·`queryForRowSet`·`queryForStream`·`update`·`batchUpdate`·`execute`(JdbcTemplate·NamedParameterJdbcTemplate) **이고** scope 가 `NameExpr`/`this.x` 필드 참조이며 `field(c, name).type()` 의 단순 이름이 `JdbcClient`·`JdbcTemplate`·`NamedParameterJdbcTemplate` 중 하나(타입 문턱 — `restTemplate.execute` 를 안 잡는다; 생성자 주입 record 성분·메서드 인자도 `field()` 가 못 보면 **안 잡는다**, 이력에 한계로). SQL 글은 `JpaIndex.literal(arg0, c.decl, 0)`(owner 있는 꼴 — 리터럴·텍스트 블록·같은 클래스 상수·전부 풀리는 연결) → null 이면 `note("jdbc", c.file, line, c.name+"."+m)`(새 kind) → 아니면 `:[A-Za-z_]\w*` 를 `?` 로 바꾼 뒤 `SqlTables.extract(text, null)` → refs 비면 `note("table",…)` 는 extract 가 낸 unresolved 그대로 → `jpa.record(c.fqcn+"."+m+"#jdbc", refs)` + `Stmt(id, "jdbc")`. `AnalyzeRunner:180` 조건에 `"jdbc"` 추가(`recorded` 로 합침). `Unresolved.KINDS` 에 `jdbc` — 이름 「JDBC 문장 못 읽음」, 뜻 「JdbcClient·JdbcTemplate 호출의 SQL 이 리터럴·텍스트 블록·같은 클래스 상수(연결)가 아니라 변수·메서드 결과·다른 클래스 상수다」, 푸는 법 「SQL 을 같은 클래스 `static final String` 상수로 두거나 그 메서드의 CRUD 는 손으로 적는다」. 한 메서드에 JDBC 호출 여럿이면 같은 `#jdbc` id 에 합친다(`record` 가 합침).
- **공통** 픽스처는 ProjectShop 실물 꼴을 **본떠 새로 쓴다**(복사 금지 — 저장소 밖 코드). 골든 갱신은 `-Dgolden.update=true` 뒤 diff 를 이력에. 청크마다 `bash scripts/verify.sh`(빠른) → 커밋, 끝에 V-34 `--corpus`.

## 4. 실행 스텝

### S0 — 설계 문서·행·번들 표
- 대상: `design/40-bundle56.md`(신설 — 이 플랜의 Context·§1~§3·§5~§7 을 설계 38 꼴로 옮겨 적는다; 「보정에서 달랐던 사실」 절 포함), `PLAN.md`, `PROGRESS.md`.
- PLAN: 행 `5-47`·`5-48`·`5-49`·`5-50`·`6-35`·`8-17` 의 둘째 칸 머리에 `**설계 40 §3 가 원본**(`design/40-bundle56.md`). ` 를 붙이고 마지막 칸 `설계 대기`/`설계 대기(조건부)` → `설계 끝 — 번들 56`. 5-49 둘째 칸의 「`{ type X, Y }` 의 인라인 `type` 지정자를 이름 X 로 읽는다」 를 「인라인 `type` 은 이미 처리된다(보정 2026-10-25) — 원인은 스프레드 하나」 로. 5-50 둘째 칸 끝에 「**결정**(사용자 2026-10-25): 자바 규칙 `jsxInputLabel` 로 — 순수본은 이 규칙을 잃는다(재주입)」. `0-71` 마지막 칸 → `안 함(사용자 2026-10-25 — 시험 코드라 이득 작음)`. 「번들」 표(`:538` 번들 55 아래)에 한 줄:
  `| 번들 56 | `S0`→`5-47`→`5-48a`→`5-48b`→`5-49`→`5-50a`→`5-50b`→`8-17a`→`8-17b`→`6-35a`→`6-35b`→`V-34` | 2026-10-25(Fable, 설계 40 — `design/40-bundle56.md`). **ProjectShop 실측이 드러낸 빈칸** — Java 21 구문(`JAVA_21`)·정적 import 미탐·tsx 스프레드 오탐·JSX 레이블 오탐(자바 규칙으로, 순수본 재주입 1)·CLI 기준선 `--write-baseline`/`--baseline`(satchel 훅용)·JdbcClient·JdbcTemplate 문장(맨 뒤 — 사용자가 뺄 수 있다). 0-71 은 안 함. 번들 하나·PR 하나. 머지 위임 없음 | 설계 39 머지 뒤(됨). portfolio 저장소(`C:/workspace/portfolio`, 재주입) · 표본 폴더 + node(`--corpus`) | 설계 끝 — 착수 대기 |`
- PROGRESS: 「열려 있는 것」 의 설계 39 대기 문구 → 「번들 56(설계 40) 착수 대기 · 0-71 안 함」; 이력 한 줄 `| 2026-10-25 | 설계 40 — 번들 56 | … | (이 커밋) |`(보정 셋·사용자 결정 셋 요약).
- 검증: `grep -c "설계 끝 — 번들 56" PLAN.md` → 6 · `grep -n "^| 0-71 " PLAN.md | grep -c "안 함"` → 1 · `bash scripts/verify.sh`(docs 레인) 초록.
- 커밋 `docs: S0 설계 40 — 번들 56 행·번들 표`. 의존: 없음(가지 `work/2026-10-25f` 를 main 에서 딴 뒤).

### 5-47 — JavaSource JAVA_21 + 픽스처
- 대상: `src/main/java/kr/ejg/toolbox/core/check/JavaSource.java:14` — `JAVA_17` → `JAVA_21`, 필드 이름 `java17` → `java21`, 주석에 「21 은 17 의 상위 집합 — 패턴 switch·레코드 패턴(5-47), 옛 코드는 8 폴백」.
- 새 픽스처 `src/test/resources/fixtures/check/java/PosJava21.java` — 패키지·`import java.util.Set;`·**`import java.util.Map;`(안 씀 → `java.unusedImport` 1건)**; `sealed interface Allowed<T> permits Everything, Only` + `record Everything<T>() implements Allowed<T>` + `record Only<T>(Set<T> values) implements Allowed<T>`; 메서드 `covers(T v)` 가 `switch (this) { case Everything<T> ignored -> true; case Only<T>(Set<T> values) -> values.contains(v); }`; `sealed interface Verdict permits Verdict.Granted, Verdict.Denied` 안에 `record Granted(String who, int n) implements Verdict {}`·`record Denied(String why) implements Verdict {}`; `String describe(Verdict v)` 가 `switch (v) { case Verdict.Granted(String who, int n) when n > 0 -> who + n; case Verdict.Granted g -> g.who(); case Verdict.Denied(String why) -> why; case null -> "none"; }`. 멤버 순서는 `java.declarationOrder` 에 맞게(상수 → 메서드), 메서드 복잡도 ≤ 15, 한 줄 ≤ 120자.
- 골든 `src/test/resources/golden/check/java.json` 갱신(`-Dgolden.update=true -Dtest=JavaRulesTest`) → diff 가 `PosJava21.java:3 java.unusedImport` 한 줄 추가뿐이어야(parseError 없음).
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaRulesTest,CheckStoreTest,SelfCheckTest,CleanupTest'` 초록(CheckStoreTest parseErrors 그대로 2). `bash scripts/verify.sh` 초록.
- 커밋 `feat: 5-47 Java 21 구문 — JavaSource JAVA_21`. 의존: S0.

### 5-48a — 정적 import 미사용 지적
- 대상: `JavaRules.java:255` → `if (!on.containsKey("java.unusedImport")) { continue; }`. 메서드 javadoc/주석에 「정적 import 도 본다(5-48) — 마지막 이름이 호출·참조·주석 단어로 안 나오면」.
- 새 픽스처 `fixtures/check/java/PosStaticImport.java` — `import static java.util.Collections.emptyList;`(안 씀) · `import static java.util.Objects.requireNonNull;`(본문에서 호출 — 씀) · `import static java.util.Map.*;`(→ `java.starImport`, 미사용 판정 안 함) · 보통 import 하나 쓰임. 기존 `NegController.java:3`·`NegTestNoAssert.java:3-4` 는 그대로(음성 — 쓰이는 정적 import 가 0 히트인지 증명; `NegTestNoAssert` 의 `assertThrows` 가 안 쓰이면 **쓰도록 본문을 고친다**, import 를 지우지 않는다).
- `src/main` 의 정적 import 4건(`grep -rn "import static" src/main/java`) 중 안 쓰는 것을 지운다 → `SelfCheckTest.mainMatchesBaseline` 초록(기준선 파일은 안 바꾼다).
- 골든 `java.json` 갱신 → diff: `PosStaticImport.java` 의 `java.unusedImport` 1 + `java.starImport` 1 추가뿐.
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaRulesTest,SelfCheckTest'` 초록. `bash scripts/verify.sh` 초록.
- 커밋 `fix: 5-48a 정적 import 미사용 지적`. 의존: 5-47.

### 5-48b — 코드 정리가 정적 미사용 import 도 지운다(골든)
- 대상: `src/test/resources/fixtures/cleanup/javaimport/Pos.java` — `import static java.util.Objects.requireNonNull;`(안 씀) 한 줄 추가, `:9` 주석 → 「쓰지 않는 import 셋(보통 둘·static 하나), star·쓰이는 static 은 그대로(5-48)」. `golden/cleanup/steps.json` 갱신 → diff: javaimport 단계에서 그 줄 삭제가 더해진 것만.
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest'` 초록. `bash scripts/verify.sh` 초록.
- 커밋 `test: 5-48b 코드 정리 — 정적 미사용 import 삭제 골든`. 의존: 5-48a.

### 5-49 — tsx 스프레드 쓰임
- 대상: `JsImportRule.statements` — `body` 를 만든 직후(`:71` 근처) `body = body.replace("...", "   ");` 한 줄 + 주석 「스프레드·나머지 `...x` 는 `.` 앞 판정에 걸려 안 쓰임이 됐다(5-49) — 길이 보존」. 클래스 javadoc 한 줄 보강.
- 픽스처 `fixtures/check/tsx/pos.ts` 그대로. `fixtures/check/tsx/neg.ts` 에 `import nextVitals from "eslint-config-next/core-web-vitals";` + `import { apiQueryOf, type Page } from "./list";` + 본문 `export const cfg = [...nextVitals];`·`const q = { ...apiQueryOf(1) };`·`let p: Page | null = null;`(이름 전부 쓰임 → 0 히트). 골든 `fixtures.json` 은 불변이어야(neg 는 골든에 안 들어간다) — 바뀌면 멈춘다.
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,CleanupTest'` 초록(steps.json 이 바뀌면 diff 를 보고 스프레드 import 가 **안 지워지는 쪽**으로만 바뀐 것이어야). `bash scripts/verify.sh` 초록.
- 커밋 `fix: 5-49 tsx 미사용 import — 스프레드를 쓰임으로`. 의존: S0.

### 5-50a — JsxLabelRule(자바 규칙) + 픽스처 + pure-rules.json
- 대상: 새 `src/main/java/kr/ejg/toolbox/core/check/JsxLabelRule.java`(`implements Rule`, 생성자 `(Def)`, `apply(Source)` — §3 5-50 판정; 패키지-private `static boolean exempt(String code, int tagStart, int tagEnd)` 를 두어 단위 시험 가능), `RuleSet.java:139` 옆 `case "jsxInputLabel" -> new JsxLabelRule(d);`, `rules.yaml:613-620` → `kind: jsxInputLabel`, `regex`·`skipComments` 삭제, `:604` 주석의 「한 줄에 닫힌 태그만」 은 `jsxImgAlt` 등 나머지에 대한 말로 고친다(「jsxInputLabel 은 자바 규칙 — 여러 줄·감싼 label·스프레드를 본다(5-50)」).
- 픽스처 `fixtures/check/a11y/pos.tsx` — 기존 `:5` 유지 + 여러 줄 태그 양성 하나(`<input\n  type="text"\n  name="q2"\n/>` — 라인은 `<input` 줄). `neg.tsx` — 기존 + ① `<label className="x">\n  <input type="radio" name="r" />\n  글\n</label>` ② `<label><input type="checkbox" name="c" /> 글</label>` ③ `<input {...shared} type={type} />` ④ `<label htmlFor={name}>글</label>\n<input id={name} />` ⑤ `<input type={"hidden"} name="h" />`.
- 골든 `golden/check/fixtures.json` 갱신 → diff: a11y `pos.tsx` 여러 줄 양성 1 추가뿐. `golden/check/pure-rules.json` 갱신(`-Dtest=PureRulesJsonTest`) → `a11y.jsxInputLabel` 항목 삭제만. `PureRulesJsonTest:64` → `assertEquals(63, …, "… 5-50 — jsxInputLabel 자바 규칙으로 1 뺌 …")`.
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRulesTest,PureRulesJsonTest,JsxLabelRuleTest'`(새 단위 시험 `JsxLabelRuleTest` — `exempt` 다섯 경우 + 중괄호 안 `>` 가 태그 끝이 아닌 경우 `onChange={e => f(e)}`) 초록. **`PureRulesJsonTest.pureHtmlEmbedsGolden` 은 이 시점에 빨강**(순수본 `var RULES` 가 아직 64) — 5-50b 가 닫는다 → 이 청크는 `wip` 가 아니라 **5-50b 와 연달아** 하고 verify·커밋은 5-50b 뒤 한 번. (예외 — 청크 둘·커밋 둘로 나누되 커밋은 둘 다 5-50b 검증 뒤: 첫 커밋은 `git commit` 전에 `verify.sh` 도장이 있어야 하므로 **5-50a·5-50b 를 커밋 하나로 합친다**. 파일 수 넷 초과는 「재주입 묶음」 사유로 이력에.)
- 의존: S0.

### 5-50b — 순수본 재주입 + sync
- 대상: `node scripts/pure-rules-inject.js`(portfolio `frontend/public/toolbox/tools/code_check.html` 의 `var RULES` 교체) → portfolio 에서 가지 `toolbox/rules-3` 를 main 에서 따서 커밋 `chore: toolbox 규칙 재주입 — a11y.jsxInputLabel 은 백엔드 자바 규칙(설계 40)`(portfolio 저장소의 규약을 따른다 — 그 저장소 CLAUDE.md 를 먼저 읽는다) → `bash scripts/sync-pure.sh`(pure/ + `pure/MANIFEST` 갱신, 해시 이력에).
- 검증: `bash scripts/mvn.sh -q test -Dtest='PureRulesJsonTest,PureFrozenTest'` 초록. `bash scripts/verify.sh` 초록(java·tools·docs). `PureCodeCheckTest`(node·puppeteer, corpus 레인)는 V-34 에서.
- 커밋(5-50a 와 하나) `feat: 5-50 a11y.jsxInputLabel 자바 규칙 — 감싼 label·여러 줄·스프레드 + 순수본 재주입`. 의존: 5-50a.

### 8-17a — core.check.Baseline + SelfCheckTest 가 쓴다
- 대상: 새 `src/main/java/kr/ejg/toolbox/core/check/Baseline.java`(§3 8-17 의 `key`·`keys`·`write`·`read`·`Diff`·`diff`; 클래스 javadoc 「검사 기준선 — 줄 번호 없는 키 `파일|규칙|발췌`, 자기 검사(`SelfCheckTest`)와 CLI `check --baseline` 이 같은 꼴을 쓴다(8-17)」). `SelfCheckTest.keys` 삭제 → `Baseline.keys(r.findings())`. `diff`: `fresh` = now 중 base 에 없는 키(순서 유지), `hidden` = now ∩ base 수, `stale` = base − now 수.
- 새 시험 `src/test/java/kr/ejg/toolbox/core/check/BaselineTest.java` — `keys` 중복 `#2` · `write`→`read` 왕복(정렬·끝 개행·빈 줄 무시) · `diff` 셋(fresh·hidden·stale) · 단언 있음.
- 검증: `bash scripts/mvn.sh -q test -Dtest='BaselineTest,SelfCheckTest'` 초록 — `self-check.txt` 불변. `bash scripts/verify.sh` 초록.
- 커밋 `feat: 8-17a 검사 기준선 Baseline — 자기 검사와 같은 키`. 의존: S0.

### 8-17b — CLI `--write-baseline`·`--baseline`
- 대상: `cli/CodeCommands.java` `Check` — 옵션 둘(`@Option(names="--write-baseline", description="지금 지적을 기준선 파일로 저장(파일|규칙|발췌, 줄 번호 없음) — --fail-on 은 무시")`, `@Option(names="--baseline", description="기준선 파일에 없는 새 지적만 --fail-on 에 센다. 사라진 항목은 알리기만 — 줄었으면 --write-baseline 으로 다시 뜬다")`); 둘 같이면 `batch.fail(2, "--baseline 과 --write-baseline 은 같이 못 쓴다")`; `--baseline` 파일 없으면 `batch.fail(2, "기준선 파일이 없다: …")`. 본문: findings JsonNode 에서 `Baseline.key(file, rule, excerpt)` 로 키 목록(중복 `#n` 은 `Baseline.keys` 와 같은 규칙 — `Baseline` 에 `static List<String> keys(List<String> rawKeys)` 오버로드를 두어 한 정의) → write 면 `Baseline.write(path, keys)` + 요약 뒤 `기준선 저장 <경로> — N건` 출력, return 0 → baseline 이면 `Diff d = Baseline.diff(keys, Baseline.read(path))`, 요약 뒤 `기준선 <경로> — 가림 H · 새 지적 F · 사라짐 S`(changedOnly 면 `사라짐 —`), fresh 마다 `  file:line severity rule — excerpt`(≤50, 넘으면 `  … 외 n건`), `--json` 이면 `r` 에 `baseline` 노드 추가 뒤 `batch.print`, `bySeverity` 는 fresh 만으로 다시 센다. 명령 description 둘째 줄 뒤에 「`--baseline` 으로 기존 지적을 가리고 새 지적만 막는다(기존 코드베이스 훅 — 첫날 빨강 방지).」. `Batch.java:28` 끝 코드 주석에 `--write-baseline 은 0` 한 마디.
- 시험 `CodeCliTest` 새 메서드 `checkBaseline(@TempDir Path tmp)`: ① `check check --write-baseline <tmp>/b.txt --fail-on error --profile t` → code 0, 파일 존재, 줄 수 = 첫 `check` 의 findings 수, 정렬됨 ② `check check --baseline <tmp>/b.txt --fail-on info --profile t` → 0, out 에 `새 지적 0` ③ `tmp/check/a11y/pos.tsx` 를 `a11y/pos2.tsx` 로 복사 → 같은 명령 → 3, out 에 `pos2.tsx` 와 `새 지적 ` ④ 복사본 삭제 + `tmp/check/tsx/pos.ts` 삭제 → 같은 명령 → 0, out 에 `사라짐 2`(그 파일의 지적 수) ⑤ `--baseline` + `--write-baseline` → 2 ⑥ 없는 기준선 → 2 ⑦ `--json --baseline` → out 이 JSON 이고 `baseline.hidden` 수가 ②와 같다.
- 검증: `bash scripts/mvn.sh -q test -Dtest='CodeCliTest'` 초록. `toolbox.bat check --help` 에 옵션 둘. `bash scripts/verify.sh` 초록.
- 커밋 `feat: 8-17b check --write-baseline·--baseline`. 의존: 8-17a.

### 6-35a — JavaGraph JDBC 싱크 + Unresolved kind
- 대상: `core/analyze/JavaGraph.java` — 상수 `JDBC_TYPES = Set.of("JdbcClient","JdbcTemplate","NamedParameterJdbcTemplate")`, `JDBC_METHODS = Set.of("sql","query","queryForObject","queryForList","queryForMap","queryForRowSet","queryForStream","update","batchUpdate","execute")`; `walk` 의 `for (MethodCallExpr mc …)` 안, MyBatis 싱크 판정 **앞**에 `if (JDBC_METHODS.contains(name) && argc >= 1 && jdbc(c, mc, m, acc)) { continue; }`; 새 `private boolean jdbc(Cls c, MethodCallExpr mc, MethodDeclaration m, Acc acc)` — scope 가 `NameExpr n` 또는 `FieldAccessExpr`(`this.x`) 이고 `field(c, 이름)` 의 `type()` 단순 이름(제네릭·패키지 벗김)이 `JDBC_TYPES` 에 없으면 `return false`; `String q = JpaIndex.literal(mc.getArgument(0), c.decl, 0)`(`literal` 접근 제어가 package-private 이면 `public static` 으로 — 같은 패키지라 그대로 됨), null → `note("jdbc", c.file, line(mc), c.name+"."+m.getNameAsString())`, return true; 아니면 `q = q.replaceAll(":[A-Za-z_]\\w*", "?")` → `SqlTables.Result res = SqlTables.extract(q, null)` → `res.unresolved()` 는 `note` 로, `res.refs()` 비면 그대로 true, 아니면 `String id = c.fqcn+"."+m.getNameAsString()+"#jdbc"; jpa.record(id, res.refs()); acc.stmts.add(new Stmt(id, "jdbc"));` return true. `Stmt` javadoc 의 resolution 목록에 `jdbc`. `core/analyze/Unresolved.java` KINDS 에 `jdbc`(§3 글 그대로). `AnalyzeRunner.java:180` → `st.resolution().equals("jpa") || … "qdsl" || … "jdbc"`.
- `Cls` 에 `decl`(ClassOrInterfaceDeclaration) 이 없으면 — `JpaIndex.literal(e, owner, depth)` 의 owner 를 어떻게 얻는지 `JavaGraph` 의 `Cls` 레코드 필드를 보고 맞춘다(`Cls` 가 `TypeDeclaration` 을 들고 있으면 그것; 없으면 `mc.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null)`).
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,JpaIndexTest,AnalyzeRunnerTest*,UnresolvedTest*'` 초록(기존 골든 불변 — JDBC 픽스처가 아직 없다). `bash scripts/verify.sh` 초록.
- 커밋 `feat: 6-35a 프로그램 분석 — JdbcClient·JdbcTemplate 문장 싱크`. 의존: S0.

### 6-35b — JDBC 픽스처 + 골든
- 대상: `src/test/resources/fixtures/analyze/jdbc/src/main/java/com/ex/jdbc/` — `OrderController.java`(`@RestController`, `@GetMapping("/orders/{id}")` → `service.find(id)`, `@PostMapping("/orders")` → `service.create(...)`, `@GetMapping("/audit")` → `audit.total()`, `@DeleteMapping("/purge")` → `legacy.purge()`, `@GetMapping("/dyn")` → `service.dynamic(cond)`), `OrderService.java`(필드 `private final JdbcClient jdbc;` — ① `find`: `jdbc.sql("select o.id, o.status from orders o where o.id = :id").param(…).query(…)` ② `create`: 텍스트 블록 `"""\n insert into orders(id, status)\n values (:id, :status)\n """` ③ `dynamic`: `jdbc.sql("select * from order_item where " + cond)` → unresolved `jdbc`), `AuditQuery.java`(`private static final String WHERE = """ where created_at > :since """;` `private static final String SELECT_TOTAL = "select count(*) from audit_log" + WHERE;` → `jdbc.sql(SELECT_TOTAL)`), `LegacyDao.java`(`JdbcTemplate template` — `template.update("delete from password_reset_token where issued_at < ?", t)`, `template.query("select user_id from app_user u join user_role r on r.user_id = u.user_id", mapper)`; `NamedParameterJdbcTemplate named` — `named.update("update app_user set email = null where deleted_at < :graceEnd", map)`), `RemoteGateway.java`(`RestTemplate rest` — `rest.execute("http://x", …)` 음성: 타입 문턱으로 안 잡힘 → 지적도 unresolved 도 없어야). 패키지·import 는 실제 Spring 이름(`org.springframework.jdbc.core.simple.JdbcClient` 등 — 컴파일 안 함, 픽스처).
- `JavaGraphTest` 새 `jdbcGolden()`(`qdslGolden` 본뜸, 접두 `jdbc/`, `JpaIndex.empty()` 대신 `JpaIndex.scan(src)` 결과(비어 있어도 그 인스턴스) 로 `JavaGraph.scan(src, null, jpa)` → `programs`·`unresolved`·`recorded` 중 `jdbc/`·`#jdbc` 만) → `golden/analyze/jdbc-graph.json`(처음은 `-Dgolden.update=true` 로 쓰고 **눈으로 검토**: orders R·orders C·audit_log R·password_reset_token D·app_user R·user_role R·app_user U, unresolved `jdbc` 1(dynamic), `RemoteGateway` 0).
- `:name` 처리 확인: 골든에 `orders` 등 표가 정상이면 OK. `SqlTables` 가 `?` 도 못 받으면(표가 안 잡히면) 6-35a 의 치환을 `' '`(공백) 으로 바꾸고 이력에 적는다.
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest'` 초록. `bash scripts/verify.sh` 초록.
- 커밋 `test: 6-35b JDBC 픽스처·골든 jdbc-graph`. 의존: 6-35a.

### V-34 — corpus 레인
- `bash scripts/verify.sh --corpus`(표본 폴더 `C:/workspace/toolbox-corpus` + node 필요, 약 7분; 메모리 먼저 본다 — 데모·컨테이너 꺼진 상태 확인).
- 기대: `check-parse-limit.txt` 0 유지(5-47) · `check-*.json`/`-hits.txt` 중 `java.unusedImport` 가 **늘 수 있다**(5-48 정적) → diff 를 보고 전부 정적 import 면 `-Dgolden.update=true` 로 갱신, 건수를 이력에 · `check-egov-react.json` 의 `a11y.jsxInputLabel` 2건이 바뀌면 그 파일 둘을 열어 감싼 label·스프레드인지 확인 뒤 갱신 · `analyze-*.json`·`cleanup` 골든 불변(JDBC 표본 없음, JAVA_21 상위 집합) — 바뀌면 **멈춘다**(§7) · `PureCodeCheckTest` 초록(픽스처 32 — a11y neg 추가분이 순수본에서도 0 히트인지; 수가 바뀌면 그 상수를 고친다).
- 커밋 `test: V-34 corpus 골든 — 정적 import·JSX 레이블`(갱신이 있을 때만). 의존: 전부.

### 마무리
- 독립 리뷰(`caveman:cavecrew-reviewer` → `git diff origin/main...HEAD`) → `bash scripts/gate-probe.sh` → `bash scripts/verify.sh`(끝 줄이 알리는 무거운 레인 — corpus 는 V-34 에서 돌았다; db 는 pom 불변이라 안 돈다) → `git push -u origin work/2026-10-25f` → `gh pr create --base main`(본문: 청크마다 세 줄 표 + 보정 셋 + portfolio 커밋 해시 + 끝 줄 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`) → CI 폴링(`gh api …/check-runs`) → claude[bot] 리뷰 처분 → **머지 안 함**, 끝 보고(계획 밖 결정·wip·새 행·사람 몫 — portfolio `toolbox/rules-3` 가지 main 머지).

## 5. 금지 사항
- `pure/` 직접 수정 금지 — `sync-pure.sh` 로만. `a11y.inputLabel`·`selectLabel`(JSP)·`jsxImgAlt` 등 다른 regex 규칙 수정 금지(5-31 범위).
- `JavaSource` 에 세 번째 파서·캐시 추가 금지. JavaParser 판 올리기 금지(`m2/` 오프라인 빌드).
- `Cleanup`·`OrderFix` 로직 수정 금지(5-48b 는 픽스처·골든만, 5-49 영향은 `JsImportRule` 경유).
- 라우트·화면(`CheckRoutes`·`code_check.html`)에 기준선 기능 추가 금지 — CLI 후처리만.
- `SelfCheckTest` 기준선 `self-check.txt` 수정 금지(정적 import 는 소스를 고쳐 0 유지). `everyTestAsserts` 확장 금지(0-71 안 함).
- 골든을 **diff 안 보고** `-Dgolden.update=true` 로 덮기 금지. 시험을 고쳐서 통과시키기 금지(픽스처 음성을 양성으로 바꾸는 식).
- ProjectShop 파일 복사 금지(본떠 쓴다). `C:\workspace\ProjectShop` 아래 쓰기 금지.
- `verify.sh` 와 `git commit`·`git push` 한 체인 금지. main 직접 push·머지 금지.
- 새 의존성 금지.

## 6. 최종 검증
- `bash scripts/verify.sh` 초록(java·tools·docs) + `bash scripts/verify.sh --corpus` 초록(V-34) · CI 전부 `completed success`.
- 손 확인(픽스처 폴더): `toolbox.bat check src/test/resources/fixtures/check --profile <임시> --json` 에 `PosJava21.java` parseError 없음·`PosStaticImport.java` unusedImport 있음·`a11y/neg.tsx` 지적 없음·`tsx/neg.ts` 지적 없음. `toolbox.bat check <폴더> --write-baseline b.txt` → `--baseline b.txt --fail-on warn` 끝 코드 0.
- `JavaGraphTest.jdbcGolden` 골든에 표 7·unresolved `jdbc` 1·`RemoteGateway` 0.
- 순수본: `PureRulesJsonTest`·`PureFrozenTest`·`PureCodeCheckTest` 초록, `pure/MANIFEST` 가 portfolio `toolbox/rules-3` 커밋을 가리킨다(이력에 해시).
- 회귀: `golden/corpus/analyze-*.json`·`cleanup` 골든 불변, `check-parse-limit.txt` 0, `CheckStoreTest` parseErrors 2, `self-check.txt` 불변.

## 7. 중단 조건
- §2 의 줄 번호·메서드 꼴이 실제와 다름(특히 `JavaRules.imports:255`, `JsImportRule:77`, `CodeCommands.Check`, `JavaGraph.walk/sink/statement`, `AnalyzeRunner:180`).
- V-34 에서 `analyze-*`·`cleanup` corpus 골든이 바뀜(JAVA_21 이 17 과 다른 AST 를 냄 → 5-47 결정 재검토).
- `SqlTables.extract` 가 `?` 치환 뒤에도 표를 못 읽음(6-35b).
- `pure-rules-inject.js` 가 portfolio 를 못 찾거나 portfolio 트리가 더럽다.
- verify 2회 연속 빨강이고 원인이 이 번들 밖. 스텝에 없는 파일 3개 이상 수정 필요.
- 보고 형식: 어느 스텝 · 무엇이 예상과 달랐는지 · 선택지. 번들 모드 규칙(행당 「정한 것」 둘까지, 넘으면 `wip/<청크>`)은 그대로.

## 8. 불확실 항목(실행해야만 아는 것)
- `SqlTables.extract` 의 `:name`/`?` 허용 — 6-35b 골든에서 잰다(실패 사다리 §4 6-35b).
- `src/main` 정적 import 4건 중 미사용 수 — 5-48a 에서 `SelfCheckTest` 가 알려 준다.
- corpus 의 정적 미사용 import 건수·`check-egov-react` 의 레이블 2건 처분 — V-34 에서 diff 로.
- `Cleanup` steps.json 의 tsx 단계가 5-49 로 바뀌는지 — 5-49 검증에서 diff 로(바뀌면 스프레드 import 가 안 지워지는 쪽만 허용).
