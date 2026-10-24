# 설계 37 — 번들 54: 도구를 자기 코드에 — 오탐 고치기 · 재귀 자기 검사 · 품질 축 셋(커버리지·순환·단언 없는 시험) (Fable 설계 · Opus 실행, 2026-10-24)

## 맥락
2026-10-24 사용자 「이 프로젝트 코드는 이 프로젝트가 제공하는 검증만큼 검증받고 있나?」. 우리 코드 검사를 이 저장소 `src/main`(206 파일·45,765 줄)에 돌려 보니 272건 — 오탐 43 · 의도된 것 27 · 판단 61 · 부채 141. 이 저장소에 걸린 것은 시험·SpotBugs·Checkstyle(선언 순서 하나)·ArchUnit·CodeQL 이고, 우리 규칙 90여 개는 현장 코드에만 돌았다.

사용자 결정(2026-10-24, 뒤집지 않는다):
- 도구를 먼저 명확히(오탐 제거) → 재귀 자기 검사 → 패치. 남에게 거는 기준을 자기에게도 건다.
- 빈 catch 는 지금처럼 다 지적(사유 주석이 있어도) — 우리 코드의 8건은 표식 + 사유.
- 복잡도는 (나) — 라우트 등록 넷만 쪼개고 나머지는 기준선(로직 메서드 리팩터 안 함).
- 품질 축 셋 다(JaCoCo · 패키지 순환 금지 · 단언 없는 시험 규칙) + 래칫(기준선은 내려가기만).
- PIT 뮤테이션·작성 규약·PMD·JUnit timeout·ErrorProne 은 다음 번들(55)에서 설계.
- 번들 하나·PR 하나 · 머지 위임.

실행자는 Opus. 차례는 `S0` → `5-44a` → `5-44b` → `5-45` → `0-62b` → `0-62c` → `0-63a` → `0-63b` → `0-64a` → `0-64b` → `0-64c` → `0-62a` → `V-32` → 마무리. 가지 `work/2026-10-24f`.

---

## 1. 목표와 범위
- 목표: 우리 코드 검사를 `src/main`(206 파일·45,765 줄)에 돌리면 **오탐 0·의도된 것은 표식으로 사유가 적힘·남은 부채는 기준선에 고정**되고, 새 지적·복잡도 증가·커버리지 하락·새 패키지 순환·단언 없는 시험이 생기면 빠른 검증이 빨개진다.
- In scope(행 열셋, 차례): `S0` → `5-44a` 정규식 넷 정밀화 + 규칙 게이트 `when` + 순수본 재주입 → `5-44b` 표식 문법 「주석의 첫 낱말」 → `5-45` 규칙 `java.testNoAssert` → `0-62b` 순환 고리 둘 풀기 → `0-62c` ArchUnit 순환 금지 → `0-63a`·`0-63b` 라우트 등록 넷 쪼개기 → `0-64a` 자기 적용(Java: 표식·패치) → `0-64b` 자기 적용(화면·CSS·JS) → `0-64c` `SelfCheckTest`(자기 검사 기준선·정리 멱등·단언 없는 시험) → `0-62a` JaCoCo + 하한 → `V-32` 표본 골든 → 마무리.
- Out of scope: PIT 뮤테이션(다음 번들, 조건부 행 `0-65`) · 복잡도 15 초과 로직 메서드 리팩터(`Validation.of` 56·`StructureLint` 50 등 — 기준선에 남긴다) · HTML `<script>` 안 `//` 주석 벗기기(stripComments 확장 — 표본 골든을 흔든다) · 순수본 엔진에 `when`·새 표식 문법 반영(portfolio — 사람 몫) · `sql_snippets.html`(순수본 그대로여야 함 — 자기 검사에서 뺀다).

## 2. 현재 구조 요약(탐색 2026-10-24 — 에이전트 셋 + 실측)
- **자기 검사 실측**(`check` CLI, `src/main`, framework spring): 272건 = 오탐 43(`sqlDollar` 18 pom·logback, `unusedIgnore` 14 설명 글·문자열, `todo` 10 설명·생성 문구, `hardcodedPassword` 1 `"password=" +`) · 의도 27(localhost 9·sysout 6·osCommand 2·sqlConcat 9 중 7·alert 1) · 오탐 더(sqlConcat 2 — `Unresolved:39` 설명 글·`LocalApp:93` 예외 메시지 `"…DELETE: " + method`) · 판단 61(domXss 28 — 전부 상수 또는 `esc()` 경유, 빈 catch 8 — 전부 사유 주석 있음, 외부 분석기 20 — 빌드 제외 17종과 겹침, a11y 5 — 전부 `sql_snippets.html`) · 부채 141(복잡도 86: 최대 `CheckRoutes.register` 94·`DeliverableRoutes.registerBuild` 57·`AnalyzeRoutes.register` 56·`GenerateRoutes.register` 50, 인자·길이·중첩 48, 중복 1 `OracleMetaSource:59`↔`MssqlMetaSource:54` 49줄, 들여쓰기 섞임 6).
- **규칙 엔진**: `RegexRule.apply` 는 줄 단위, `params` 는 `max` 만 읽는다. 파일 내용 게이트는 `kind: absent` 의 `params.when`(뜻이 다름)과 `MyBatisRules` 의 `contains("<mapper")` 뿐. `stripComments`: java·js 류 `//`·`/* */`, xml·html 은 `<!-- -->` 만(HTML `<script>` 안 `//` 안 벗김), yaml 은 줄 첫 `#` 만. 순수본 export(`PureRulesJsonTest.pureRules`)는 regex kind 전부 64 — 필드 `id, group, severity, langs, regex, flags, skipComments, max, message`; 금지 문법 목록 `JAVA_ONLY`(lookbehind 는 허용 — `common.localhost` 가 이미 씀). `pureHtmlEmbedsGolden` 이 순수본 `var RULES` 와 대조 → regex 바뀌면 같은 청크에 재주입(`node scripts/pure-rules-inject.js` → portfolio 가지 커밋 → `sync-pure.sh`). portfolio 가지 `toolbox/rules-react`(`b190bd4`, main 미반영)가 있다 — **그 가지 위에** 얹는다.
- **표식** `Ignores.MARK = \btb-ignore(-next)?(?![\w-])`: 원문 줄 어디서나 인식(주석 여부 안 봄) → `src/main` 에 우연한 표식 15줄(javadoc·문자열·yaml 주석·index.html 글). `Cleanup.removeMarkers` 는 `stripComments` 로 주석 안인지 본다(이미 보수적). 문법 문서 `docs/pure-code-check.md` §9「주석 문법을 가리지 않고 원문 줄에서 찾는다」.
- **JavaRules**: 검출기는 `private void x(Node, Ctx)` 꼴, `apply` 에서 `on.containsKey(id)` 로 가드, `add(c, rule, node)`. `complexity`: `body.findAll(Node.class)` 라 **람다 안 분기가 감싼 메서드에 든다** → `register` 안 람다 10여 개가 합산. `java.emptyCatch` 는 문장 0 이면(주석만 있어도) 지적. 새 `kind: java` 규칙 = 검출기 + `rules.yaml` 항목 + `fixtures/check/java/Pos*/Neg*` + 골든 `check/java.json`(`JavaRulesTest.fixturesGolden` — java·metric 묶음 규칙마다 양성 1 이상 요구).
- **라우트**: `CheckRoutes.register(app, jobs, db, files, active, dataDir)` L50-417, 공유 지역 `store` 하나, 라우트 11 · `DeliverableRoutes.registerBuild(app, snapshots, conns, dict, jobs, active, analyses, profileFile)` L49-249 라우트 4(지역 없음) · `AnalyzeRoutes.register(app, jobs, db, files, conns, active)` L70-406 지역 `store`·`snapshots`, 라우트 16 · `GenerateRoutes.register(app, jobs, snapshots, dict, files, active, genDir)` L43-156 지역 `types`, 라우트 2. 시험: `CheckRoutesTest` 9·`DeliverableRoutesTest` 9·`AnalyzeRoutesTest` 10·`GenerateRoutesTest` 4 + 스모크.
- **순환 고리**(jdeps 클래스 단위): ① `{conn, gen, meta, profile}` — 기본 순환 넷(meta↔gen · meta↔profile · meta→conn→profile→meta · meta→gen→profile→meta), 최소 컷 2 엣지 = **profile→meta**(`Profile`·`Profile.Deliverable`·`ProfileStore` → `meta.Scope`) + **meta→gen**(`SnapshotStore.Summary.dialect()` → `TypeMapping.dialectOf(String)` 정적 호출 하나). `Scope` 는 record, `accepts(Table)`·`reason(Table)` 이 `t.schema()·t.rowCount()·t.name()` 셋만 쓴다. ② `{deliverable, report}` — `report.Mapping`·`XlsxFiller` 가 `deliverable.Doc`(record, JDK·Jackson 만 의존)·`Forms.Form`(record — `Forms.all()` 은 deliverable 상수 참조) 을 읽는다(6 쌍). `Doc` 사용 파일 9, `Forms.Form` 은 `MakeTemplates`·`Mapping`·`XlsxFiller`·`XlsxFillerTest`.
- **ArchUnit** 1.4.1, `ArchitectureTest` 규칙 8(`slices()` 미사용). `ImportReviewDocTest.namesEveryArchitectureRule` — 새 `@Test` 이름은 `docs/import-review.md` §9 에 적어야 한다.
- **시험 단언**: 765 시험 중 JUnit 직접 호출 673 · 도우미만 81 · 토큰 전무 11(ArchUnit `.check(main)` 8, `VerifyLanesTest.check("db")` 2, `AnalyzeCorpusTest.jpaCorpus` 1). 경계: `DbCorpusBase#snapshotDiffTwoReleases` 는 `golden.put` 만 하고 단언은 `@AfterAll close()`.
- **빌드**: `pom.xml` 에 `pluginManagement`·JaCoCo 없음. surefire 3.5.2 `argLine` 리터럴 `-da:org.h2... -Xmx3g`(`@{argLine}` 없음), 태그 선택은 명령줄만. Checkstyle(validate, DeclarationOrder 하나)·SpotBugs(verify, effort Max, `config/spotbugs/exclude.xml` 17). `offline-build.sh` 채움 = `dependency:go-offline dependency:resolve-plugins` + `-DexcludedGroups=db,corpus verify`, 검사 = `-o -DskipTests verify`. `pom.xml` 은 java 레인이자 **db 레인**(push 앞 `--db` 17분). CI `verify.yml` 은 `-DexcludedGroups=corpus verify`(db 는 돈다). `package.sh` 는 런타임만 넣는다(빌드 플러그인 무관). `docs/import-review.md` §9 정적 검사 표.
- **기준선 도구**: `CorpusFiles.baseline(Path file, String name, List<String> now, int total, boolean update)`(package-private, `golden/corpus/` 밖 경로 가능 — 새로 깨짐·새로 고쳐짐 둘 다 빨강, `-Dgolden.update=true` 로 갱신), 공개 오버로드는 `golden/corpus/` 고정(corpus 레인 지문). `GoldenFiles.assertText` 는 정확 일치. `CheckRunner.run(root 절대경로, RuleSet, LocalFiles, null, null, exclude, null)` · `RuleSet.load(profile, null)` · `CleanupRunner.preview(root, rules, Cleanup.RULES, "LF", files, null, null, exclude)`. 태그 없는 시험은 java 레인에 자동으로 든다(`VerifyLanesTest` 는 db·corpus 만 본다).
- **들여쓰기**: `FileRule.mixed` 는 탭 시작 줄 vs 2칸 이상 공백 시작 줄, 적은 쪽 첫 줄에 1건. css 7·`jsp_formatter_ext.js` 4·`table_builder_ext.js` 7 은 주석 연속줄(고치면 됨), html 셋은 순수본 유래 블록(수백 줄 — 표식).
- 레인: `core/check`·`core/fs`·tools·규칙 YAML·픽스처·`golden/corpus` = corpus 레인, `pom.xml` = db 레인 → 마무리 `--corpus --db`. 새 의존성은 **빌드 플러그인 JaCoCo 하나**(런타임 0).

## 3. 설계 결정 — 전부 [고정]

### A. 도구 정밀화(5-44a·5-44b)
- **`params.when`(regex 규칙 파일 게이트)**: `RegexRule` 생성자에서 `params.when` 을 규칙 flags 로 컴파일, `apply` 첫 줄 `if (when != null && !when.matcher(s.text()).find()) return List.of();`(원문 — 주석 안 `<mapper` 는 드물다). `pureRules()` 출력에 `when` 필드 추가(순수본 엔진은 모르는 키를 무시 — 순수본은 게이트 없이 더 잡는다, 사람 몫에 적는다). `PureCodeCheckTest` 는 DIRS `common·jsp·tsx·security·a11y` 만 비교하므로 게이트 음성 픽스처는 **새 폴더 `fixtures/check/gate/`**(CheckRulesTest 는 걷고 순수본 비교는 안 한다).
- **T1 `security.sqlDollar`**: `when: '<(?:mapper|sqlMap)\b'`. 픽스처 `security/pos.xml` 에 `<mapper namespace="x">` 머리가 없으면 넣는다(있으면 그대로) · `gate/neg-pom.xml`(`<project>` 안 `${junit.version}`) · `gate/neg-logback.xml`.
- **T3 `common.todo`**: `(?://|/\*|\*|#|<!--|<%--|--)\s*(?:TODO|FIXME|XXX)\b` — 주석 토큰 바로 뒤 표시만(문자열·javadoc 본문 가운데 낱말은 제외). `skipComments: false` 그대로. 픽스처 `common/pos.*` 의 TODO 가 `// TODO` 꼴인지 확인(아니면 그 꼴로), `common/neg.java` 에 `"TODO 타입 확인: "` 문자열 한 줄.
- **T4 `security.hardcodedPassword`**: 앞에 `(?<!["'])` — `"password=" + …` 제외. `security/neg.java` 에 `"password=" + (p == null ? "null" : "****")` 한 줄.
- **T7 `security.sqlConcat`**: 키워드만이 아니라 SQL 꼴 — `"[^"]*\b(?:SELECT\b[^"]*\bFROM|INSERT\s+INTO|UPDATE\s+\w+\s+SET|DELETE\s+FROM|MERGE\s+INTO)\b[^"]*"\s*\+\s*[\w(]`, flags `[i]` 그대로. 기존 양성 `security/pos.java:10`(`"SELECT * FROM users WHERE id = '" + id`) 은 그대로 걸린다. `security/neg.java` 에 `"method 는 GET·POST·PUT·DELETE: " + method` 한 줄.
- 넷 다 순수본 공유 regex → 골든 `pure-rules.json`·`fixtures.json` 갱신 → 재주입(portfolio `toolbox/rules-react` 가지에 커밋 추가, `git -C`, push 안 함) → `sync-pure.sh`. `PureRulesJsonTest` 64 그대로(수 불변).
- **T2′ 표식 문법 「주석의 첫 낱말」(5-44b)**: `Ignores.MARK` 를 `(?://|/\*|\*|#|<!--|<%--|--)\s*tb-ignore(-next)?(?![\w-])` 로 — 주석 시작 토큰(또는 javadoc 연속 `*`) 바로 뒤에 올 때만 표식. 문자열 `"tb-ignore"`·`contains("tb-ignore")`·javadoc 가운데 낱말·index.html 본문·yaml 주석 가운데 낱말은 더 이상 표식이 아니다(우연한 표식 15 → 0). `ids()` 의 시작 위치는 그룹 끝 기준. 기존 픽스처(`fixtures/check/ignore/*`·`cleanup/guards/*`·`cleanup/ignore/*`)는 전부 `// tb-ignore`·`<%-- tb-ignore` 꼴이라 그대로. `Cleanup.removeMarkers` 의 주석 판정은 그대로 두되 `MARK` 를 공유. 문서 `docs/pure-code-check.md` §9 문장 교체(「주석의 첫 낱말이어야 한다 — 문자열·설명 글 속 낱말은 표식이 아니다」) + 순수본 재주입 사람 몫 글에 같은 규칙. 시험 `IgnoresTest`(있으면 추가, 없으면 새로): 문자열 안·javadoc 가운데·yaml 가운데는 표식 아님, `//`·`/*`·`*`·`#`·`<!--`·`<%--`·`--` 뒤는 표식.
- **T6**: 자기 검사 프로필에서 묶음 `ext` 끔 — 빌드 `spotbugs:check` 가 같은 엔진을 클래스·메서드 단위 제외(`exclude.xml`)와 함께 verify 마다 돈다(이중 기준 금지). 이유를 `SelfCheckTest` javadoc 에.
- **T5**: 변경 없음(사용자).

### B. 규칙 `java.testNoAssert`(5-45)
- `rules.yaml` ㄴ 묶음: `id: java.testNoAssert, group: java, severity: warn, kind: java, globs: ['*.java'], message: '단언 없는 시험 — 돌기만 하고 틀려도 안 잡는다', fix: 'assert·verify 류 단언을 넣거나, 단언하는 도우미 이름을 params.helpers 에 적는다', params: {helpers: []}`.
- `JavaRules.testNoAssert(MethodDeclaration m, Ctx c)`: 어노테이션 단순 이름이 `Test`·`ParameterizedTest`·`RepeatedTest` 인 메서드(`TestFactory` 제외)에서 몸의 `MethodCallExpr` 이름 가운데 정규식 `^(?:assert|verify|expect|check|fail|should)` 또는 `params.helpers` 목록(정확 일치)에 맞는 것이 없고, 몸에 `throw` 문도 없으면 지적(메서드 이름 줄, 발췌 `이름()`). 람다 안 호출도 센다(`findAll`). `assertThrows`·`assertAll`·ArchUnit `.check(main)`·`VerifyLanesTest.check("db")` 가 자연히 인정된다.
- 픽스처 `java/PosTestNoAssert.java`(`@Test void runs() { new Object().toString(); }` + `@ParameterizedTest` 하나) · `java/NegTestNoAssert.java`(`assertEquals`, `.check(...)`, `assertThrows`, `@TestFactory`). 골든 `check/java.json` 갱신. `CheckRulesTest` 내장 수 104 → 105.
- 자기 적용(0-64c)에서 우리 시험에 걸린 것은 `params.helpers`(예 `jpaCorpus`·`probeCorpus`·`snapshotVia` — 실측으로 확정) 또는 표식(`DbCorpusBase#snapshotDiffTwoReleases` — 단언이 `@AfterAll close()` 에 있다는 사유).

### C. 순환 고리(0-62b·0-62c)
- **고리 ①**: `core.meta.Scope` → **`core.profile.Scope`** 로 옮기고 `accepts(Table)`·`reason(Table)` 을 **`accepts(String schema, String name, Long rowCount)`·`reason(String schema, String name, Long rowCount)`** 로 바꾼다(Table 의존 제거; 호출자는 `scope.accepts(t.schema(), t.name(), t.rowCount())`). 호출 파일 12(`Consistency`·`Deliverables`·`MetaSource`·`SnapshotService`·`SnapshotStore`·`Profile`·`ProfileStore`·`CheckRoutes`·`DeliverableRoutes`·`LogicalRoutes` + 시험) import 교체. `TypeMapping.dialectOf(String)` 본문을 **`core.meta.DbProduct.dialectOf(String)`**(새 final 클래스, 정적 하나)로 옮기고 `TypeMapping.dialectOf` 는 그것을 부르는 위임으로 남긴다(호출자 6 불변) — `SnapshotStore.Summary.dialect()` 는 `DbProduct.dialectOf` 를 부른다. 결과: profile→meta 0 · meta→gen 0.
- **고리 ②**: `core.deliverable.Doc` → **`core.report.Doc`**, `core.deliverable.Forms.Form` → **`core.report.Form`**(record 그대로; `Forms.all()` 은 `report.Form` 을 만든다). `Mapping`·`XlsxFiller` 는 같은 패키지의 `Doc`·`Form` 만 본다 → report→deliverable 0. import 교체 파일 ≈ 12.
- **ArchUnit**(0-62c): `ArchitectureTest.noPackageCycles` — `slices().matching("kr.ejg.toolbox.(**)").should().beFreeOfCycles()`(main 클래스만). `docs/import-review.md` §9 ArchUnit 줄에 이름 추가(`ImportReviewDocTest`). `gate-probe.sh` 에 새 probe 는 안 더한다(기존 arch 프로브 꼴과 같아 게이트 생존이 이미 증명됨 — 이력에 적는다).
- 버린 대안: 고리를 얼리기만(ArchUnit `ignoreDependency`) — 부채가 남는다 · `Scope` 를 `core.meta` 에 두고 `Profile` 이 문자열 설정만 들기 — 프로필 구조·YAML 호환이 바뀐다.

### D. 라우트 등록 쪼개기(0-63a·0-63b) — 복잡도 (나)
- 각 `app.<verb>(path, ctx -> { … })` 람다 몸을 `private static void <동사+자원>(Context ctx, <캡처들>)` 로 옮기고 람다는 한 줄 위임. 캡처가 7개를 넘지 않게(`metric.params` 7) — `CheckRoutes.run` 은 `(ctx, jobs, files, active, store, dataDir)` 6. 긴 작업 람다(`jobs.submit`) 안은 그대로(메서드 하나가 복잡도 15 를 넘어도 기준선에 든다 — 이 행의 목표는 `register*` 넷이 15 아래). 라우트 경로·응답·시험 불변. 메서드 이름은 경로에서(`stack`·`fixes`·`rules`·`run`·`vcs`·`runs`·`runById`·`export`·`trend`·`trendExport`·`deployList` …).
- 0-63a: `CheckRoutes`·`GenerateRoutes` · 0-63b: `AnalyzeRoutes`·`DeliverableRoutes.registerBuild`. 검증은 각 라우트 시험 + 스모크 전체.

### E. 자기 적용(0-64a·0-64b) — 표식은 `tb-ignore <규칙> <사유>`(사유 필수, 코드 본문 없음)
- **Java(0-64a)**: 빈 catch 8 → catch 줄 끝 `// tb-ignore java.emptyCatch <기존 사유>`(안 주석은 지움) · localhost 6(`App:37`·`Main:11`·`LocalApp:99`·`LocalOnly:42,43,53`) → `127.0.0.1 전용 설계(규칙 1)` · sysout 6(`Serve` 5·`MetaCommands` 1) → `CLI 표준 오류 출력` · osCommand 2(`Cli:71`·`App:249`) → `git·svn / 브라우저 열기 — ArchitectureTest 가 자리 고정` · sqlConcat 7(`InsertGen` 3·`SqlProbe:188`·`CodeAndLink:110`·`RowCounter:67`·`InsertRoutes:146`) → `SQL 생성기 / 식별자 검증 뒤` · `OracleMetaSource:59` → `metric.duplicate MssqlMetaSource 와 같은 블록 — 드러난 것으로 새 행 후보` · `rules.yaml:57·63` → 윗줄 `# tb-ignore-next common.localhost 규칙 정의 글`. T3·T4·T7·T2′ 뒤 오탐은 0 이어야 한다(남으면 규칙을 더 고치지 말고 **이력에 적고 표식** — 행당 정한 것 둘 안).
- **화면(0-64b)**: `sql_snippets.html` 은 자기 검사 `exclude` 로(순수본 동결 — `ToolsFolderTest.sqlSnippetsIsPurePlusCommonJs`). `domXss` 26(dev_tools 22·jsp_formatter 3·special_chars 1): 줄 끝 `// tb-ignore security.domXss esc() 경유` 또는 `상수 HTML`(인라인 속성 `dev_tools:559` 는 윗줄 `<!-- tb-ignore-next security.domXss 상수 -->`) · `common.js:235` alert → `사용자 알림` · `index.html:41` localhost → 윗줄 HTML 주석 표식 · 들여쓰기: `common.css`(3칸 연속 7줄 → 탭)·`jsp_formatter_ext.js`·`table_builder_ext.js`(머리 주석 연속줄 → ` * ` 1칸 꼴) 고침, `deliverable_sql.html`·`dev_tools.html`·`program_analysis.html` 은 지적 줄 위에 `<!-- tb-ignore-next file.mixedIndent 순수본 블록 -->`(지적 줄이 `<pre>` 안이면 그 위 줄).
- **코드 정리 자기 적용**: `CleanupRunner.preview(src/main)` 결과 바뀌는 파일이 있으면(쓰지 않는 import 등) 그 변경을 손으로 반영(원본 불변 규칙은 도구 자신에게도 — 결과 폴더에서 가져온다).

### F. 기준선 `SelfCheckTest`(0-64c, java 레인, 태그 없음)
- 프로필: `Project(root=<저장소 절대경로>/src/main, UTF-8, LF)`, framework `spring`, `CodeCheck(groups {ext:false}, rules {}, exclude ['**/sql_snippets.html'])`. `CheckRunner.run(...)` → 지적 키 `file|rule|excerpt`(줄 번호 제외 — 코드 이동에 흔들리지 않게; 같은 키 중복은 ` #2` 접미) 정렬 → `CorpusFiles.baseline(Path.of("src/test/resources/golden/check/self-check.txt"), "self-check", keys, Integer.MAX_VALUE, GoldenFiles.updating())`(5인자 오버로드를 **public** 으로). 새로 깨짐·새로 고쳐짐 둘 다 빨강(래칫 — 줄면 갱신). `parseErrors == 0`·`suppressed` 수는 골든 `self-check.json`(`{findings, suppressed, files, lines, byRule}`)로.
- 정리 멱등: `CleanupRunner.preview(같은 root…)` → `changed == 0`, skips 목록은 `golden/check/self-cleanup.txt` conformance.
- 단언 없는 시험: `CheckRunner.run(<저장소>/src/test/java …)` 결과 중 `rule == java.testNoAssert` 만 → 0 이어야 한다(프로필 B 의 `java.testNoAssert.params.helpers` 는 실측으로 — 설계 기본 후보 `jpaCorpus`·`probeCorpus`).
- 수행 시간 상한 없음(빠른 검증 2~3분에 ~15초 추가).

### G. JaCoCo(0-62a)
- `jacoco-maven-plugin` **0.8.12**: 실행 `prepare-agent`(기본 phase) · `report`(phase `verify`, 먼저) · `check`(phase `verify`). surefire `argLine` 을 `@{argLine} -da:org.h2... -Xmx3g` 로. `check` 규칙 둘: `BUNDLE` LINE·BRANCH `COVEREDRATIO` 하한 + `PACKAGE`(includes `kr.ejg.toolbox.core.*`) LINE 하한 — 값은 **`scripts/mvn.sh -q -B -DexcludedGroups=corpus verify`(CI 와 같은 집합) 실측에서 2%p 내림**(래칫 시작점, 이력에 실측 값). `offline-build.sh` 의 채움 verify(`-DexcludedGroups=db,corpus`)에 `-Djacoco.skip=true`(db 빠져 하한 미달이 정상). `--check`(`-DskipTests verify`)는 실행 데이터가 없어 check 가 건너뛴다(초록). `docs/import-review.md` §9 표에 「JaCoCo 커버리지 하한(verify)」 한 줄, §8 「빌드 도구는 안 넣는다」 그대로. 채움: `bash scripts/offline-build.sh`(네트워크) 한 번 → `--check` 초록.
- 버린 대안: 0.8.13(2025-04, 검증 짧음) · 패키지 21개 개별 하한(래칫이 너무 많다).

### H. 공통
- 새 화면 0 · 고르는 칸 0 · 런타임 의존성 0 · 완료 행 불변 · 등급·표식 외 기존 규칙 뜻 불변.
- 표식 사유는 사람이 읽는 한 줄, 코드 본문 없음(규칙 3).

## 4. 실행 스텝(= PLAN §16 행)
- **S0** — 설계 37 + PLAN 번들 54 행 + §16 열셋 + PROGRESS(번들 53 완료 `3718194`·`ba82dec`). 설계 세션이 친다.
- **5-44a** — `RegexRule`(`when`) · `rules.yaml` 넷(T1·T3·T4·T7) · 픽스처(`gate/neg-pom.xml`·`neg-logback.xml`, `security/neg.java`·`common/neg.java` 줄, `security/pos.xml` 머리) · 골든 `fixtures.json`·`pure-rules.json` · `PureRulesJsonTest.pureRules` `when` 필드 · 재주입(portfolio `toolbox/rules-react` 가지) · `sync-pure.sh` · `docs/pure-code-check.md`. 검증 `-Dtest='CheckRulesTest,PureRulesJsonTest,PureFrozenTest'` + `PureCodeCheckTest`(node).
- **5-44b** — `Ignores.MARK`·`ids` · `Cleanup`(공유) · `IgnoresTest` · `docs/pure-code-check.md` §9. 검증 `-Dtest='IgnoresTest,CheckRulesTest,CleanupTest,CheckStoreTest'`.
- **5-45** — `JavaRules.testNoAssert` · `rules.yaml` · 픽스처 둘 · 골든 `check/java.json` · `CheckRulesTest` 105. 검증 `-Dtest='JavaRulesTest,CheckRulesTest'`.
- **0-62b** — `core.profile.Scope`(시그니처 변경) · `core.meta.DbProduct` · import 교체 ≈ 12+12 파일 · `TypeMapping.dialectOf` 위임. 검증 `-Dtest='ArchitectureTest,SnapshotStoreTest,ProfileStoreTest,DeliverableRoutesTest,XlsxFillerTest,MappingTest'` + 빠른 전체.
- **0-62c** — `ArchitectureTest.noPackageCycles` · `docs/import-review.md` §9. 검증 `-Dtest='ArchitectureTest,ImportReviewDocTest'`(0-62b 전에 돌리면 빨강이어야 — 이력에 적는다).
- **0-63a** — `CheckRoutes`·`GenerateRoutes` 쪼개기. 검증 `-Dtest='CheckRoutesTest,GenerateRoutesTest,CleanupRoutesTest,SmokeHtmlUnitTest'`.
- **0-63b** — `AnalyzeRoutes`·`DeliverableRoutes.registerBuild`. 검증 `-Dtest='AnalyzeRoutesTest,DeliverableRoutesTest,SmokeHtmlUnitTest'`.
- **0-64a** — Java 표식·패치(§E). 파일 ≈ 20(청크 규칙 1~3 예외 — 한 줄짜리 표식, 이력에 적음). 검증: 자기 검사 CLI 돌려 Java 쪽 오탐·의도 0 확인(`check` CLI, 스크래치 프로필).
- **0-64b** — 화면·CSS·JS 표식·들여쓰기 + 정리 미리보기 반영. 검증 `-Dtest='ToolsFolderTest,ToolsA11yTest,SmokeHtmlUnitTest'` + CLI 자기 검사.
- **0-64c** — `SelfCheckTest` 셋(기준선·멱등·단언) · `CorpusFiles.baseline` public · 골든 `check/self-check.txt`·`self-check.json`·`self-cleanup.txt` · 자기 검사 프로필 B `helpers`. 검증 `-Dtest=SelfCheckTest` + 빠른 전체.
- **0-62a** — `pom.xml` JaCoCo · surefire argLine · `offline-build.sh` skip · `docs/import-review.md` · 하한 실측. 검증 `scripts/mvn.sh -q -B -DexcludedGroups=corpus verify` 초록(check 통과) → `bash scripts/offline-build.sh` → `--check` 초록.
- **V-32** — 표본 골든 갱신 diff(`check-egov*.json`·hits·`vcs-egov.json`·`cleanup-*`: todo·sqlDollar·sqlConcat·hardcodedPassword 변화, 표식 문법 변화 0 기대) 이력에. 검증 `verify.sh --corpus`.
- **마무리** — CLAUDE.md 여덟. 레인 java·tools·docs·corpus·db(pom) → `--corpus --db`. 머지 위임. 끝 보고에 사람 몫: portfolio `toolbox/rules-react` 가지(커밋 둘) main 반영 · 순수본 엔진 `when`·표식 문법 반영 요청.

## 5. 금지 사항
- 빈 catch 규칙·등급 규칙·`Finding`/이력 스키마 불변 · 로직 메서드(`Validation.of`·`StructureLint`·`SqlTables`·`JpaIndex`…) 리팩터 금지(기준선에) · `stripComments` 의 HTML `<script>` 확장 금지 · `sql_snippets.html`·`pure/` 직접 수정 금지 · 오탐을 규칙 끄기로 덮지 않는다(규칙 고치기 또는 표식+사유) · 표식에 사유 없이 쓰지 않는다 · JaCoCo 하한을 실측 없이 적지 않는다.

## 6. 최종 검증
`verify.sh` → `--corpus --db` → `gate-probe.sh` → `offline-build.sh --check` → PR → CI(jacoco check 포함) → AI 리뷰 → 머지(위임).

## 7. 중단 조건
- 순환 고리 풀기(0-62b)가 시그니처 변경으로 시험 10개 넘게 깨지면 멈추고 보고(얼리기 대안으로 전환 판단은 사용자).
- 재주입이 portfolio 에서 실패 → 5-44a 를 `wip/5-44a` 로.
- JaCoCo 가 `offline-build.sh --check` 에서 네트워크를 요구하면(플러그인 전이 의존성 누락) 멈추고 보고.
- 그 밖은 CLAUDE.md 번들 모드(행당 정한 것 둘, verify 빨강 둘).

## 8. 불확실 항목(실행해야만 알 수 있는 것)
- 0-62a: 커버리지 실측 값(CI 집합) — 하한은 거기서 정한다.
- 0-64c: `java.testNoAssert` 가 우리 시험에서 몇 개를 잡나(도우미 81 중 이름이 `assert|verify|expect|check|fail|should` 로 안 시작하는 것) → `helpers` 목록·표식 수.
- 0-64a: T3·T4·T7·T2′ 뒤 남는 오탐 0 인지(남으면 이력 + 표식).
- V-32: 표본 골든 diff 크기(todo 가 가장 많이 준다).
