# 설계 48 — 번들 64: 보안 — XXE · 인증서·호스트 검증 끔 + 5-57d (Fable 설계 · Opus 실행, 2026-11-01)

## 맥락
compare ② 나-3(XXE — FindSecBugs XXE_* 아홉, KISA 1-8)·나-9(인증서·호스트 검증 끔 — WEAK_TRUST_MANAGER·WEAK_HOSTNAME_VERIFIER, KISA 2-11)가 「후보 행 제안」 에 「새 자바 규칙 · 상」 으로 올라 있고, 번들 63 ⑬ 「후보로」 가 번들 64~68 차례를 적었다(사용자 승인 2026-10-31). 대기 행 5-57d(교차 조인의 표 함수 JOIN)는 작아서 같이 얹는다(사용자 2026-11-01).

탐색 실측(2026-11-01, `c62b2c6`):
- **기존 규칙에 없음** — `rules.yaml`·`JavaRules`·`Taint` 에 xxe·TrustManager·HostnameVerifier 가 없다. `check/ext-fixes.yaml` 의 `XXE_DOCUMENT`·`WEAK_TRUST_MANAGER` 는 외부 분석기(FindSecBugs, 클래스 폴더가 있을 때만) 권장 문구다 — 소스만 있는 검사엔 안 닿는다. `core/text/SafeSax` 는 우리 파서(설정을 parse 와 같은 메서드에 두는 이유가 「FindSecBugs 가 메서드 안만 본다」 — 5-3 실측). 이 규칙도 같은 범위를 기본으로 한다.
- **표본 — XML 팩토리** 자리 10(`.java`, grep `newInstance|newFactory|createXMLReader`): 막음 없음 4 — jeecg `OfficeHtmlUtil.java:105`(DBF — `newDocumentBuilder().newDocument()`, 파싱 없음)·`:112`(TransformerFactory, DOM → HTML) · jeesite `ActProcessService.java:211`(`XMLInputFactory.newInstance()`, BPMN 스트림 파싱) · struts `DomHelper.java:181`(`SAXTransformerFactory`, static 초기화 블록). 막음 있음 6 — egov·egov-prev `EgovXMLDoc.java:131`(`EgovXMLConstants.FEATURE_SECURE_PROCESSING`·`EXTERNAL_GENERAL_ENTITIES`·`ACCESS_EXTERNAL_DTD`·`setExpandEntityReferences(false)`)·`:253`(TF + `FEATURE_SECURE_PROCESSING`) · struts `DomHelper.java:102`(SPF + external-*-entities false + SECURE_PROCESSING) · jspwiki `XMLGroupDatabase.java:232`·`XMLUserDatabase.java:202`(`setExpandEntityReferences(false)` 만). zheng·ruoyi-classic·commons 셋·roller·shopizer·egov-msa·egov-react 0. `SchemaFactory`·`XMLReaderFactory` 는 표본 0 — 같은 코드 길이라 집합에 넣는다(픽스처로만 잰다).
- **표본 — TLS** 1 — ruoyi-classic `HttpUtils.java` `TrustAnyTrustManager`(`:269` 빈 `checkClientTrusted` · `:274` 빈 `checkServerTrusted` · `:279` `getAcceptedIssuers` 빈 배열) · `TrustAnyHostnameVerifier`(`:287` `verify` → `return true`) · `:231` `setHostnameVerifier(new TrustAnyHostnameVerifier())`. 나머지 표본 0.
- **Juliet 해당 없음** — `C:/workspace/toolbox-corpus/juliet` 에 CWE611·CWE295 폴더가 없고 zip 도 안 남았다. Juliet Java 1.3 CWE 목록에 둘 다 없다. OWASP Benchmark 도 범주 없음. 표본 추가 없음(`corpus/MANIFEST` 불변).
- **`JavaRules`** 648줄 · **메서드 정확히 30**(`metric.classMethods` 한계) · `apply(1)` 복잡도 19·`type(2)` 26 기준선. **새 메서드·새 분기 금지** → 새 클래스 `SecurityAst` 하나, 기존 `taint(cu, c)` 몸에 for 하나(복잡도 2 → 3, 새 키 없음). `kind: java` 규칙은 `RuleSet.javaDefs()`(kind java 또는 하이브리드)로 `JavaRules` 에 가고 `Rule` 인스턴스는 안 만든다(`build` 의 switch 에 안 걸린다 — 4차 `security.sqlInjection` 과 같은 꼴).
- **픽스처 자리** — `CheckRulesTest.fixturesGolden` 은 `java/`·`mybatis/`·`ext/` 밖 모든 픽스처에 security 묶음을 돌려 「양성에서 한 번도 안 걸린 규칙 0 · 음성 히트 0」 을 단언한다 → 새 규칙은 `fixtures/check/security/pos.java`·`neg.java` 에 걸려야 한다. **`PureCodeCheckTest` 가 security 폴더 파일 수를 32 로 고정**하고 pure 규칙(정규식)분만 골든과 맞댄다 → 새 파일을 못 늘린다. 기존 `pos.java`(40줄)·`neg.java`(44줄) 끝에 메서드를 **덧붙인다**(기존 줄 번호 불변 → `fixtures.json` 기존 행 diff 0). `JavaRulesTest` 는 java·metric 묶음만 보므로 무관.
- **수** — `CheckRulesTest:167` 내장 116 → 118 · `:168` security 42 → 44 · `:175` `fix` ≤ 60자.
- **재채점 골든** — `CheckCorpusTest`(egov·commons·jspwiki·roller·egov-react → `check-<묶음>.json`+`-hits.txt`) · `LegacyCorpusTest`(jeecg·jeesite·zheng·ruoyi-classic) · `VcsCorpusTest`(`vcs-egov.json` — egov 전체 지적 수; 번들 63 이 빠뜨려 첫 `--corpus` 가 빨갰다) · `CliCorpusTest`(CLI = 엔진 대조, 골든 아님) · `JulietCorpusTest`(`security-juliet.json`) · `SecurityBenchmarkCorpusTest`(`security-benchmark.json`) · `SqlRuleCasesCorpusTest`(`sqlrules.json`·`sqlrules-diff.txt`, 5-57d) · `SqlFixtureCorpusTest`(`sqltables-diff.txt`, HINTS 이동 diff 0). 기대 — jeecg +2(xxe) · jeesite +1 · ruoyi-classic +3(trustAll) · 나머지 diff 0 · sqlrules AM08 23 → 25.
- **5-57d** — `SqlLint.item`(`:290`)은 이름 사슬 뒤가 `(` 여도 표로 돌려준다. `SqlTables.HINTS`(`:72`, private, 쓰는 자리 둘)가 잠금 힌트 집합 — `core/check` 는 `core/analyze` 를 import 못 하므로(`ArchitectureTest`) `SqlTokens`(core/text)로 옮긴다(STOP 전례, 5-57a). `SqlLintTest.CASES` 는 `{이름, WHOLE|PART, SQL, 기대}` 네 칸, `X` = CROSS_JOIN.
- **레인** — `core/check`·`core/text`·`core/analyze`·픽스처·골든 전부 corpus 레인 → push 앞 `--corpus`(약 20분, Docker 안 씀). `core/gen`·pom·MANIFEST 불변 → `--db` 안 돈다.
- **함정(실행자에게)** — Bash·python heredoc 이 `'\n'` 의 백슬래시를 벗긴다 — 자바 글자 리터럴·정규식은 Edit 로 · `verify.sh` 와 `git commit` 은 따로 낸다(한 체인이면 훅이 막고 체인 전체가 안 돈다) · 새 클래스는 PMD 기준선에 없다 — `AvoidLiteralsInIfCondition`(글자·숫자 상수는 `static final`) · `AvoidFieldNameMatchingMethodName` · `AvoidCatchingGenericException` · `LiteralsFirstInComparisons`(`"x".equals(y)`) · `UnusedPrivateField` · `LooseCoupling`(선언은 `Set`·`List`) · 시험 `AvoidDuplicateLiterals`(같은 글자 넷 이상은 상수).

## 사용자 결정(2026-11-01)
- (가) 심각도 — `security.xxe` **warn**(입력이 닿는지 봐야 한다 — 등급 규칙 줄 277) · `security.trustAll` **error**(쓰는 순간)
- (나) 막음 판정은 **같은 파일 한 홉 인정** — 팩토리를 만든 메서드 몸에 막음이 없어도, 그 팩토리 변수를 인자로 넘긴 같은 컴파일 단위의 메서드 몸에 막음이 있으면 막은 것
- (다) **5-57d 얹는다**
- (라) **머지 위임 있음**
- 설계가 정한 것(사용자가 안 물은 자리, 번들 모드 「정한 것」 아님 — 설계 결정) — `getAcceptedIssuers` 가 null·빈 배열인 것만으로는 안 잡는다(검증을 끄는 자리가 아니다 — check 메서드 둘이 그 클래스를 이미 잡는다) · `SchemaFactory`·`XMLReaderFactory` 는 집합에 넣는다(코드 길 같음) · KISA `ref:` 는 yaml 주석으로 후보만(1-8 · 2-11), 칸 확정은 「사람이 할 것」 의 보안 33 규칙 ref 와 같이 · Juliet 표본 추가 없음(폴더가 없다)
- 설계 41~47 그대로(에이전트 최소 · 검증 인프라 최적화 금지 · 표본 추가 없음 · 새 화면 없음 · 순수본 불변 — kind java 라 `pure-rules.json` 안 바뀐다)

실행자는 Opus. 차례 `S0` → `5-58` → `5-57d` → `V-44a` → `R-13` → `V-44` → 마무리. 가지 `work/2026-10-31c`(닫기 `c62b2c6` 로컬, 같은 PR). 번들 하나·PR 하나.

---

## 1. 목표와 범위
- 한 문장: 코드 검사 security 묶음이 `.java` 에서 ① XML 팩토리(`DocumentBuilderFactory`·`SAXParserFactory`·`XMLInputFactory`·`TransformerFactory`·`SAXTransformerFactory`·`SchemaFactory`·`XMLReaderFactory`)를 만든 자리에서 같은 메서드(또는 같은 파일 도우미 한 홉)에 DTD·외부 개체 막음이 없으면 `security.xxe`(warn), ② 몸이 빈 `checkServerTrusted`·`checkClientTrusted` · `return true` 인 `verify(String, SSLSession)` · `(h, s) -> true` 람다 · `ALLOW_ALL_HOSTNAME_VERIFIER`·`NoopHostnameVerifier`·`TrustAllStrategy` 이름을 `security.trustAll`(error)로 줄 번호에 지적하고, `SqlLint` 교차 조인이 표 함수 JOIN 을 후보에서 뺀다.
- 완료 시 체감: 코드 검사에 ruoyi-classic 을 넣으면 `HttpUtils.java` 에 trustAll 3 건, jeecg·jeesite 에 xxe 2·1 건이 파일·줄로 나온다(화면 변경 없음 — 규칙은 yaml 에서 읽힌다). egov 는 0(막혀 있다). sqlfluff AM08 일치 23 → 25(45 → 47/50). compare ⑭.
- In scope: 신설 `core/check/SecurityAst.java` · `core/check/JavaRules.java`(`taint()` 몸 한 줄 + javadoc) · `resources/check/rules.yaml`(5차 둘) · `fixtures/check/security/pos.java`·`neg.java`(덧붙임) · `CheckRulesTest`(118·44) · 골든 `check/fixtures.json` · 5-57d — `core/text/SqlTokens.java`(HINTS)·`core/analyze/SqlTables.java`(위임)·`core/check/SqlLint.java`(`item`)·`SqlLintTest`(사례 둘)·골든 `check/sql-lint.json` · corpus 골든(위 목록) · `design/compare-2026-10-28.md` ⑭ · PLAN 4장 sqlfluff 구절·9장 `core.check` 한 구절 · 분할표·번들 표 · PROGRESS.
- Out of scope: `getAcceptedIssuers` 단독 판정 · dom4j `SAXReader`·jdom `SAXBuilder`·`XMLDecoder`(역직렬화 규칙이 본다) · `SSLContext.init(null, …)` 자리 · `TrustSelfSignedStrategy` · `.jsp` 안 자바(글롭 `*.java` 만) · 외부 분석기(`ext.findsecbugs`) 연동 · Juliet·Benchmark 표본 추가 · 새 화면·순수본 재주입 · `core/gen`·pom · 레인 스크립트 · 보안 33 규칙 `ref:` 확정.

## 2. 현재 구조 요약
「맥락」 실측에 더해:

**`JavaRules`** — `apply(Source)`(`:128`): 파싱 → `imports` → `taint(cu, c)`(`:225`, `link != null` 이면 `TaintLink.summarize` 의 `Taint.Hit(rule, node)` 를 `add`) → 타입마다 `type` → emptyCatch·order·metric·testNoAssert 분기. `add(c, rule, node)`(`:269`)는 `on.containsKey(rule)` 일 때만 `finding(rule, s, line(node), 원문 줄 발췌)`. `Taint.Hit` 은 같은 패키지 record(`Taint.java:44`). `on` 은 `Map<String, Rule.Def>`(켜진 규칙).

**`rules.yaml`** — security 4차(`:470~513`, `kind: java`·`globs: ['*.java']`·message·fix, 정규식 없음)가 JavaRules 전용 규칙의 꼴. 설정 파일 규칙 `security.cfgCookieSecure` 가 `:571~580`, 그 뒤 ㅈ 접근성(`:582`). 새 5차는 `:581` 빈 줄 자리에.

**`CheckRulesTest.fixturesGolden`**(`:77`) — security 묶음 켜고 `fixtures/check/**` 를 돌려 `{file, line, rule}` 를 `check/fixtures.json` 골든으로. `neg*` 히트 0 · 켜진 규칙 전부 `pos*` 에서 한 번 이상. `fixtureSources` 는 폴더를 걸어 정렬(파일 추가 없이 덧붙이면 기존 행 그대로).

**`SqlLint.item(t, j)`**(`:290`) — `(` 면 짝 괄호 뒤 별칭, 단어면 `chainEnd` 뒤 `[AS] 별칭` 또는 사슬 끝 이름. `crossJoin`(`:236`)·`commaGroup`(`:267`)이 부른다. `SqlTables.isFunction`(`:293`)이 `at(t, k+1, "(") && … && !HINTS.contains(t.get(k+2))` 로 같은 뜻을 가른다.

**시험·골든** — `SqlLintTest.golden`(`CASES` → `check/sql-lint.json`) · `SqlRuleCasesCorpusTest`(`sqlrules.json` + `sqlrules-diff` conformance — 현재 다섯 줄, 그중 `test_pass_unnest`·`test_pass_lowercase_unnest` 가 우리 결함) · `SqlTablesTest`(`analyze/sql-tables.json`, HINTS 이동 diff 0).

**함정** — ① `JavaRules` 메서드 30 · `apply` 19 — 메서드·분기를 더하면 `self-check.txt` 새 키(금지) ② `PureCodeCheckTest` 32 — security 폴더에 파일 추가 금지 ③ pos.java 새 줄이 다른 security 정규식·오염 규칙에 걸리면 안 된다(`getParameter`·`password`·`new URL(`·`Random`·숫자 꼴 금지 — `HttpsURLConnection` 은 `security.ssrf` 정규식(`HttpURLConnection`)에 안 맞는다) · neg.java 새 줄은 어느 규칙에도 ④ JavaParser 가 두 픽스처를 끝까지 읽어야 한다(익명 클래스·람다는 된다, 해석은 안 한다 — FQN·단순 이름 둘 다 글자로 본다) ⑤ `kind: java` 규칙은 `RuleSet.javaDefs()` 가 넘긴다 — `Taint.knows` 에 넣지 않는다(하이브리드가 아니다) ⑥ `fix` ≤ 60자(`CheckRulesTest:175`) ⑦ 「막음」 의 값 — `setFeature(FEATURE_SECURE_PROCESSING, false)`·`setExpandEntityReferences(true)` 는 막음이 아니다 ⑧ 5-57d 뒤 `JOIN t (NOLOCK)` 은 표 그대로(HINTS) ⑨ `VcsCorpusTest` 를 재채점에 넣는다(번들 63 교훈).

## 3. 설계 결정 — 전부 [고정]

**SecurityAst(core/check, 5-58)** — `final class SecurityAst`, 인스턴스 없음(private 생성자), JavaParser AST 만 본다(타입 해석 없음).
1. `static final String XXE = "security.xxe"` · `TRUST_ALL = "security.trustAll"` · `static List<Taint.Hit> apply(CompilationUnit cu, Set<String> on)` — 둘 다 안 켜졌으면 `List.of()`. 켜진 것만 돈다(`on.contains`). 결과는 `JavaRules.add` 가 거른다(이중 가드 무해).
2. **XXE 앵커** — `cu.findAll(MethodCallExpr.class)` 중 이름 ∈ `CREATORS = {newInstance, newFactory, newDefaultInstance, createXMLReader}` 이고 scope 의 마지막 식별자(`NameExpr` 이름 또는 `FieldAccessExpr.getNameAsString()` — `javax.xml.parsers.DocumentBuilderFactory.newInstance()` 도 `DocumentBuilderFactory`) ∈ `FACTORIES = {DocumentBuilderFactory, SAXParserFactory, XMLInputFactory, TransformerFactory, SAXTransformerFactory, SchemaFactory, XMLReaderFactory}`. 앵커마다 한 건, 노드 = 그 호출(줄 = 호출 줄).
3. **범위** — 앵커의 `findAncestor(BodyDeclaration.class)`(메서드·생성자·필드 초기화·static 블록 — struts `:181` 은 static 블록). 없으면 건너뜀.
4. **막음 호출 `guards(MethodCallExpr c)`** — 이름 `setExpandEntityReferences` 이고 첫 인자가 `BooleanLiteralExpr false` → 참. 이름 ∈ {setFeature, setAttribute, setProperty} 이고 첫 인자 키 글(`StringLiteralExpr` 값 · `FieldAccessExpr`/`NameExpr` 식별자 · 그 밖은 `toString()`)을 대문자로 해서 — `KEYS_ON = {DISALLOW-DOCTYPE-DECL, SECURE_PROCESSING}` 중 하나를 `contains` 하면 둘째 인자가 `false` 리터럴이 아닐 때 참 · `KEYS_OFF = {EXTERNAL-GENERAL-ENTITIES, EXTERNAL-PARAMETER-ENTITIES, LOAD-EXTERNAL-DTD, EXTERNAL_GENERAL_ENTITIES, EXTERNAL_PARAMETER_ENTITIES, IS_SUPPORTING_EXTERNAL_ENTITIES, SUPPORT_DTD, LOAD_EXTERNAL_DTD}` 중 하나를 `contains` 하면 둘째 인자가 `true` 리터럴이 아닐 때 참 · `KEYS_ANY = {ACCESS_EXTERNAL_DTD, ACCESS_EXTERNAL_SCHEMA, ACCESS_EXTERNAL_STYLESHEET}` 는 값 불문 참. 그 밖(`setNamespaceAware`·`setValidating`·`setXIncludeAware`)은 거짓. 둘째 인자가 리터럴이 아니면(변수·상수) 값을 모른다 → 막음으로 친다.
5. **한 홉(사용자 (나))** — 앵커가 `VariableDeclarator` 초기값이거나 `AssignExpr` 값이면 변수 이름 `v`(`NameExpr`·`FieldAccessExpr` 의 이름). 범위 몸의 `MethodCallExpr c` 중 인자에 `NameExpr v` 가 있고 `cu.findAll(MethodDeclaration.class)` 에 같은 이름(인자 수 같음)의 메서드가 있으며 그 몸에 `guards` 참인 호출이 있으면 막은 것. 한 홉만(도우미 안의 도우미는 안 본다). 변수가 없는 사슬(jeecg `:105`)은 홉 없음.
6. **판정** — 범위 몸에 `guards` 참인 호출이 하나도 없고 한 홉도 없으면 Hit(XXE, 앵커). 호출 scope 가 앵커 변수인지는 안 본다(같은 메서드에서 팩토리 둘을 만들고 하나만 막는 꼴은 미탐 — 사다리에).
7. **trustAll ① 메서드** — `cu.findAll(MethodDeclaration.class)` 중 (ⅰ) 이름 ∈ {checkServerTrusted, checkClientTrusted} 이고 몸이 있고 몸의 문장이 전부 `EmptyStmt` 또는 값 없는 `ReturnStmt`(문장 0 포함 — 주석만 든 몸은 문장 0) → Hit(TRUST_ALL, `d.getName()`). `X509ExtendedTrustManager` 의 셋째 인자 꼴도 이름이 같아 잡힌다. (ⅱ) 이름 `verify` · 매개변수 2 · 둘째 매개변수 타입 글이 `SSLSession` 으로 끝남 · 몸이 문장 하나 `return true;` → Hit. `verify(String, String)` 는 아니다(음성).
8. **trustAll ② 람다** — `cu.findAll(LambdaExpr.class)` 중 매개변수 2 · 몸이 `BooleanLiteralExpr true`(식 몸) 또는 문장 하나 `return true;`(블록 몸) · 부모가 `MethodCallExpr` 이고 그 이름이 `HostnameVerifier` 를 contains(`setHostnameVerifier`·`setDefaultHostnameVerifier`·`hostnameVerifier`) → Hit(TRUST_ALL, 람다).
9. **trustAll ③ 이름** — `cu.findAll(SimpleName.class)` 중 식별자 ∈ `TRUST_NAMES = {ALLOW_ALL_HOSTNAME_VERIFIER, NoopHostnameVerifier, TrustAllStrategy}` 이고 `findAncestor(ImportDeclaration.class)` 가 비었음 → Hit(TRUST_ALL, 그 이름). `NoopHostnameVerifier.INSTANCE`·`new NoopHostnameVerifier()` 는 각각 한 건. 같은 줄 둘이면 둘(정렬은 `apply` 끝에서 줄·규칙).
10. **`getAcceptedIssuers` 는 안 본다**(설계 결정 — 「사용자 결정」 절). 사다리 ①: R-13 에서 check 메서드 없이 `getAcceptedIssuers` 만 null 인 TrustManager 가 실물에 있으면 새 행.
11. 복잡도 — `apply`·`xxe`·`guards`·`keyText`·`hopGuarded`·`trustMethods`·`trustLambdas`·`trustNames`·`trivialBody`·`simpleName`·`varName` 전부 ≤ 15·길이 ≤ 100·중첩 ≤ 4, 클래스 메서드 ≤ 30(열둘 안팎). 글자 상수는 `private static final String`(PMD). `Set`·`List` 로 선언.

**JavaRules 배선(5-58)**
12. `taint(cu, c)` 몸 끝에 `for (Taint.Hit h : SecurityAst.apply(cu, on.keySet())) { add(c, h.rule(), h.node()); }`. javadoc 에 「+ 파서·TLS 설정 결함(설계 48, SecurityAst)」. **메서드 추가 없음 · `apply` 분기 추가 없음** — `self-check.txt` 의 `JavaRules` 네 줄 숫자 불변.

**rules.yaml(5-58)** — `security.cfgCookieSecure` 뒤, ㅈ 앞에 5차 구역:
13. 머리 주석 `# ---- ㅇ 5차 — 파서·TLS 설정 결함(설계 48, 2026-11-01). .java 만 — JavaRules 의 SecurityAst 가 본다`. 규칙 둘 — `security.xxe`(group security · severity **warn** · kind java · globs `['*.java']` · message `XML 외부 개체 주입(XXE) — 팩토리를 만든 자리에서 DTD·외부 개체를 안 막는다` · fix `'FEATURE_SECURE_PROCESSING 을 켜고 disallow-doctype-decl 을 같은 메서드에서'`(≤ 60자 확인) · 주석 `# ref 후보: KISA 1-8 XML 외부 개체 주입 — 번호 확정은 사람 몫(보안 33 ref 와 같이)`) · `security.trustAll`(severity **error** · message `인증서·호스트 검증 끔 — 빈 checkServerTrusted·return true 인 verify·ALLOW_ALL` · fix `'기본 TrustManager·HostnameVerifier 를 쓰고 사설 인증서는 truststore 에'` · 주석 `# ref 후보: KISA 2-11 부적절한 인증서 유효성 검증`). `ref:` 칸은 비운다.
14. `CheckRulesTest:167` 116 → 118(주석에 `설계 48 파서·TLS 둘`) · `:168` 42 → 44.

**픽스처(5-58)** — 기존 두 파일 끝(닫는 `}` 앞)에 메서드 덧붙임. 기존 줄 불변.
15. `security/pos.java` — 메서드 `xml()`: `DocumentBuilderFactory.newInstance()` 변수 대입(막음 없음) · `javax.xml.parsers.SAXParserFactory.newInstance()`(FQN scope) · `XMLInputFactory.newFactory()` · `TransformerFactory.newInstance()` · `SchemaFactory.newInstance(W3C)` · `XMLReaderFactory.createXMLReader()` · `DocumentBuilderFactory f2 = …newInstance(); f2.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);`(값 false — 막음 아님) · `f2.setNamespaceAware(true)`(막음 아님) → xxe 7. 메서드 `tls(HttpsURLConnection conn)`: `conn.setHostnameVerifier((h, s) -> true);` · `conn.setHostnameVerifier(SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);` · `conn.setHostnameVerifier(new NoopHostnameVerifier());` · 익명 `X509TrustManager` — `checkClientTrusted` 몸 `{ }` · `checkServerTrusted` 몸 `{ return; }` · `getAcceptedIssuers` `return null;`(안 잡힘) · 익명 `HostnameVerifier` — `verify(String h, SSLSession s) { return true; }` → trustAll 6. 한 줄에 한 건. `getParameter`·`password`·`new URL(`·`Random`·숫자 띄 글 금지.
16. `security/neg.java` — 메서드 `xmlSafe()`: DBF + `setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)` · SPF + `setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)` + `setFeature("http://xml.org/sax/features/external-general-entities", false)` · XIF + `setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)` · TF + `setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")` · DBF `d2` + `setExpandEntityReferences(false)` · DBF `d3` + `harden(d3)` (한 홉) · `SchemaFactory` + `setProperty(ACCESS_EXTERNAL_SCHEMA, "")`(static import 꼴 NameExpr) · TF `t2` + `setFeature(SECURE, on)`(값이 변수 — 막음으로 친다). `private void harden(DocumentBuilderFactory f) throws Exception { f.setFeature(EXTERNAL_PARAMETER_ENTITIES, false); }`. 메서드 `tlsSafe(HttpsURLConnection conn, X509TrustManager delegate)`: `conn.setHostnameVerifier((h, s) -> h.equals(HOST));` · `conn.setHostnameVerifier(new DefaultHostnameVerifier());` · 익명 `X509TrustManager` 가 `delegate` 에 위임(`checkServerTrusted` 는 `if (c == null || c.length == 0) { throw new CertificateException("empty"); } delegate.checkServerTrusted(c, a);`) · 익명 `HostnameVerifier.verify` 가 `return HOST.equals(h);` · 클래스 메서드 `boolean verify(String code, String expected) { return true; }`(SSLSession 아님 — 음성). 어느 security 규칙에도 안 걸려야 한다(`exceptionMessage` 는 응답에 안 싣는다).
17. 골든 `check/fixtures.json` — `security/pos.java` 행 +13(xxe 7 · trustAll 6), 기존 행 diff 0. `PureCodeCheckTest` 32 그대로(pure 규칙분만 비교 — 새 규칙은 kind java 라 안 든다).

**5-57d — 표 함수 JOIN**
18. `SqlTokens` 에 `public static final Set<String> HINTS = Set.of("NOLOCK", "READUNCOMMITTED", "READPAST", "ROWLOCK", "UPDLOCK", "HOLDLOCK", "TABLOCK", "TABLOCKX", "XLOCK", "NOWAIT", "INDEX", "FORCESEEK")`(SqlTables 의 집합 그대로) · `SqlTables` 는 `private static final Set<String> HINTS` 삭제, 쓰는 자리 둘 → `SqlTokens.HINTS`. 골든 `sql-tables.json`·`sqltables-diff.txt` diff 0.
19. `SqlLint.item` — 단어 가지에서 `k = chainEnd(t, j)` 뒤 `if (OPEN.equals(s(t, k + 1)) && !SqlTokens.HINTS.contains(s(t, k + 2))) { return null; }`(표 함수 — 후보 아님). `JOIN t (NOLOCK)` 은 그대로 표. `(` 가지(서브쿼리)는 불변. `item` 복잡도 ≤ 15 유지(지금 8 안팎 + 2).
20. `SqlLintTest.CASES` 둘 — `{"joinUnnest", WHOLE, "SELECT * FROM TA a JOIN UNNEST(a.ARR) u WHERE a.ID = 1", NONE}` · `{"joinNolockHint", WHOLE, "SELECT * FROM TA a JOIN TB (NOLOCK) WHERE a.ID = 1", X}`. 골든 `check/sql-lint.json` +2, 기존 diff 0. 쉼표 묶음(`FROM t, UNNEST(t.x) u`)은 항목 null 로 묶음이 끊겨 후보 0 — 사례 셋째 `{"commaUnnest", WHOLE, "SELECT * FROM TA a, UNNEST(a.ARR) u WHERE a.ID = 1", NONE}` 도 넣는다(+3).
21. `SqlRuleCasesCorpusTest` — AM08 `test_pass_unnest`·`test_pass_lowercase_unnest` 가 일치로 → `sqlrules.json` AM08 agree 23 → 25 · `sqlrules-diff.txt` 두 줄 삭제(남는 셋 — `test_fail_missing_multiple_joins_not_last`·`test_pass_positional_join_duckdb`·`test_pass_select_comma`, ⑬ 분류 그대로). V-44a 가 갱신.

**측정·판정**
22. V-44a 재채점 — `CheckCorpusTest`·`LegacyCorpusTest`·`VcsCorpusTest`·`CliCorpusTest`·`JulietCorpusTest`·`SecurityBenchmarkCorpusTest`·`SqlRuleCasesCorpusTest`·`SqlFixtureCorpusTest` 를 `-Dgolden.update=true` 로 한 번 → diff 를 **읽고** 전·후를 이력에(규칙 둘 × 표본 count·files · sqlrules 45 → 47 · 벽시계). 기대 — `check-jeecg.json` xxe {count 2, files 1} · `check-jeesite.json` xxe {1, 1} · `check-ruoyi-classic.json` trustAll {3, 1} · `-hits.txt` 셋 갱신 · 나머지(egov·commons·jspwiki·roller·egov-react·vcs-egov·juliet·benchmark·sqltables-diff) diff 0. 수치는 게이트 아님. 사다리 — ① 어떤 표본에서 xxe 가 50 건을 넘으면(소음) `guards` 를 호출 scope 가 앵커 변수일 때만으로 좁히지 **않고**(미탐 방향) R-13 에서 판정 ② trustAll 이 람다·이름에서 오탐이면 그 가지를 R-13 고침 둘 안에.
23. R-13 손 판정 — ① 새 지적 전부(jeecg 2 · jeesite 1 · ruoyi-classic 3 — 파일·줄·판정·근거 한 구절, 코드 본문 안 옮김) → 참/오탐. jeecg `:105`(`newDocument()`, 파싱 없음)·`:112`(DOM → HTML) 는 「참(규칙 정의) — 입력 없음, warn 확인 대상」 으로 적는다. 오탐으로 판정하면 고침 ①: 앵커 호출의 부모 사슬이 `newDocumentBuilder().newDocument()` 로 끝나면 제외(파싱 없음) — 픽스처 음성 한 줄 추가. ② CLI(`toolbox.bat check <폴더> --groups security --json` 또는 `bash scripts/…`)로 struts·egov-prev 를 한 번 돌려 struts `DomHelper.java:181`(static 블록) 1 건 · egov-prev 0 건을 확인해 표에(골든 아님 — 두 표본은 corpus 시험 밖). ③ `sqlrules-diff` 남은 셋이 ⑬ 분류 그대로인지. 결함 둘 이하는 고치고 골든 다시(V-44a 명령), 넘으면 새 행.
24. V-44 compare ⑭ — `## ⑭ 재측정 — 번들 64(2026-11-01)`: 규칙 둘 × 표본 건수 표(egov·commons·jspwiki·roller·egov-react·jeecg·jeesite·zheng·ruoyi-classic + CLI struts·egov-prev) · R-13 표 · sqlfluff 사례 45 → 47/50 · 시험 시간 · 「드러난 것」(표본의 XML 파싱 자리 10 중 막음 6 — eGov 표준 프레임워크는 막혀 있다 · TLS 끔은 ruoyi-classic 연계 유틸 하나 · Juliet 에 CWE611·295 없음) · 「후보로」(`newDocument()` 제외 · `getAcceptedIssuers` 단독 · dom4j·jdom 파서 · `SSLContext.init(null, …)` · 같은 메서드 팩토리 둘 중 하나만 막음 · 번들 65~68). ② 표의 나-3·나-9 행은 안 고친다(⑬ 전례 — 다-1 도 「없음」 그대로). PLAN 4장 sqlfluff 구절 `45/50` → `47/50(번들 64 5-57d)` · 9장 `core.check` 줄 `JavaRules` 뒤에 `·SecurityAst(파서·TLS 설정 결함, 5-58)`.

## 4. 실행 스텝

### S0 — 설계 문서·행·번들 표 (설계 세션이 한다)
- 대상: 신설 `design/48-bundle64.md`(이 파일의 「맥락」~8절) · `PLAN.md`(번들 표 「번들 64」 행 — `S0`→`5-58`→`5-57d`→`V-44a`→`R-13`→`V-44`, 2026-11-01(Fable, 설계 48), 요지 + 머지 위임 있음, 선행 「번들 63 머지 · 표본 폴더 + node(`--corpus`)」, 상태 「대기 — 설계 끝」 · 분할표 — 5-57d 행(`:1086`)을 「**설계 48 §3-18~21·§4 가 원본**」 으로 다시 쓰고(선행 `5-58`) 그 아래 `5-58`·`V-44a`·`R-13`·`V-44` · 4장·9장은 V-44 가 고친다) · `PROGRESS.md`(「열려 있는 것」 머리 → 「**번들 64(설계 48, 보안 — XXE · 인증서·호스트 검증 끔 + 5-57d) 설계 끝 — 가지 `work/2026-10-31c`**, 다음은 「번들 해」」, 이력 S0 줄 — 직전 줄(번들 63 닫기) 해시 칸 `c62b2c6`, 이 줄은 `(이 커밋)`).
- 절차: 가지 `work/2026-10-31c` 그대로 → 세 파일 → `bash scripts/doc-lint.sh design/48-bundle64.md PLAN.md PROGRESS.md` → `bash scripts/verify.sh` → (따로) `git commit -m "docs: S0 설계 48 — 번들 64 행·번들 표(XXE·인증서·호스트 검증 끔 + 5-57d)"`.

### 5-58 — `SecurityAst` + 배선 + yaml + 픽스처 + 골든
- 대상: 신설 `core/check/SecurityAst.java` · `core/check/JavaRules.java`(`taint` 몸·javadoc) · `resources/check/rules.yaml` · `fixtures/check/security/pos.java`·`neg.java` · `CheckRulesTest`(`:167`·`:168`) · 골든 `check/fixtures.json`
- 변경(TDD — 실패 먼저): ① yaml 둘 + `CheckRulesTest` 118·44 + 픽스처 덧붙임(§3-13~16) → `bash scripts/mvn.sh -q test -Dtest=CheckRulesTest` → RED(「양성 픽스처에서 한 번도 안 걸린 규칙」 에 `security.xxe`·`security.trustAll`, 골든 diff). ② `SecurityAst`(§3-1~11) + `taint` 한 줄(§3-12) → 같은 시험 → 골든 갱신 `-Dgolden.update=true` 한 번 → diff 가 `security/pos.java` 새 행 13 뿐인지 **읽는다** → 다시 돌려 GREEN. 음성 히트 0.
- 검증: `bash scripts/mvn.sh -q test -Dtest=CheckRulesTest,PureCodeCheckTest,JavaRulesTest,TaintTest,SelfCheckTest,ArchitectureTest` 초록 · `self-check.txt` diff 0(새 키 없음 · `JavaRules` 네 줄 숫자 그대로) · `bash scripts/mvn.sh -q pmd:check` 0 · `bash scripts/verify.sh`.
- 의존: S0.

### 5-57d — 표 함수 JOIN 은 후보 아님
- 대상: `core/text/SqlTokens.java` · `core/analyze/SqlTables.java` · `core/check/SqlLint.java` · `SqlLintTest.java` · 골든 `check/sql-lint.json`
- 변경(TDD): ① `SqlLintTest.CASES` 셋(§3-20) → `-Dtest=SqlLintTest` RED(`joinUnnest`·`commaUnnest` 가 X 를 낸다). ② HINTS 이동(§3-18) + `item` 분기(§3-19) → 골든 갱신 한 번 → diff 가 새 항목 셋뿐인지 읽는다 → GREEN.
- 검증: `bash scripts/mvn.sh -q test -Dtest=SqlLintTest,SqlTablesTest,SqlJoinsTest,MapperIndexTest,MyBatisRulesTest,SelfCheckTest,ArchitectureTest` → `sql-tables.json`·`sql-joins.json`·`mapper-index.json`·`mybatis.json` diff 0 · `self-check.txt` 새 키 없음(`item` 숫자 변동만 허용 — 기준선 키가 없으면 ≤ 15 이어야) · `pmd:check` 0 · `verify.sh`.
- 의존: 없음(5-58 과 독립 — 차례는 5-58 뒤).

### V-44a — 재채점(corpus 골든 갱신) + 시간
- 대상: `golden/corpus/check-jeecg.json`·`check-jeesite.json`·`check-ruoyi-classic.json` + `-hits.txt` 셋 · `sqlrules.json`·`sqlrules-diff.txt` · (diff 0 기대) `check-egov*`·`check-commons*`·`check-jspwiki*`·`check-roller*`·`check-egov-react*`·`check-zheng*`·`vcs-egov.json`·`security-juliet.json`·`security-benchmark.json`·`sqltables-diff.txt`·`sqlfixture.json`
- 변경: `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='CheckCorpusTest,LegacyCorpusTest,VcsCorpusTest,CliCorpusTest,JulietCorpusTest,SecurityBenchmarkCorpusTest,SqlRuleCasesCorpusTest,SqlFixtureCorpusTest' -Djunit.timeout=10m -Dgolden.update=true` → `git diff --stat src/test/resources/golden/corpus` 를 읽고 전·후를 이력에(§3-22). 기대 밖 골든이 바뀌면 고치지 않고 R-13 으로.
- 검증: 같은 명령(갱신 없이) 초록 · 시험 하나 10분 안 · 등급 A 0.
- 의존: 5-58 · 5-57d.

### R-13 — 손 판정(새 지적 · struts·egov-prev CLI · 남은 어긋남)
- 대상: `design/compare-2026-10-28.md`(⑭ 「손 판정」 절) · `PROGRESS.md`
- 변경: §3-23. 표 둘(새 지적 6 + CLI 2 표본 · sqlrules 남은 셋). 결함 둘 이하는 고치고 골든 다시(V-44a 명령), 넘으면 새 행 번호를 「후보로」. 코드 본문은 안 옮긴다(규칙 3 — 파일·줄·규칙까지).
- 검증: `bash scripts/doc-lint.sh design/compare-2026-10-28.md` · 분류 합 = 줄 수.
- 의존: V-44a.

### V-44 — compare ⑭ · PLAN 구절 · `--corpus`
- 대상: `design/compare-2026-10-28.md`(⑭ 나머지 — §3-24) · `PLAN.md` 4장 sqlfluff 구절·9장 `core.check` 구절 · `PROGRESS.md`
- 검증: `bash scripts/verify.sh --corpus` 초록(약 20분, Docker 안 씀) → `bash scripts/verify.sh` 초록. `--db` 는 안 돈다(`core/gen`·MANIFEST·pom 불변 — 끝 줄이 db 를 부르면 멈추고 보고).
- 의존: R-13.

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7 · 행의 축·강제 지점과 diff · 양성·음성(값 false 인 SECURE_PROCESSING 양성 · 한 홉 음성 · `verify(String, String)` 음성 · `getAcceptedIssuers` 안 잡음 · `JOIN t (NOLOCK)` 표 유지) · 골든이 의도된 변화인가(`fixtures.json` 새 13 행만 · `sql-lint.json` 새 셋만 · corpus 는 jeecg·jeesite·ruoyi-classic·sqlrules 만) · `JavaRules` 메서드 30·새 키 0 · `ArchitectureTest` 순환 0) → `bash scripts/gate-probe.sh` → `bash scripts/verify.sh` → `git push -u origin work/2026-10-31c` → `gh pr create --base main`(행마다 시작 세 줄 표 + 닫기 `c62b2c6` 같이 + ⑭ 핵심 수치) → CI 폴링(`gh api … check-runs`) → claude[bot] 처분 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → 닫기 커밋은 다음 가지 → 끝 보고. PR 본문 끝 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## 5. 금지 사항
- `pure/`·portfolio·`docs/`·화면 HTML/JS 수정 금지 · 새 Maven 의존성 금지 · `corpus/MANIFEST`·표본 변경 금지 · `scripts/verify-fingerprint.sh` 수정 금지 · **`core/gen`·`pom.xml` 수정 금지**(db 레인)
- **`JavaRules` 에 메서드·분기 추가 금지**(메서드 30·`apply` 19 한계) — 새 로직은 전부 `SecurityAst` · `self-check.txt` 새 키 금지 · PMD 새 짝 금지
- `fixtures/check/security/` 에 **파일 추가 금지**(`PureCodeCheckTest` 32) · 기존 픽스처 줄 수정·삭제 금지(덧붙임만) · `fixtures.json` 기존 행 변경 금지
- `core/check` 가 `core/analyze` 를 import 하지 않는다 · `core/text` 가 `core/check` 를 import 하지 않는다(`ArchitectureTest`) · `SqlTables.isFunction`·`STOP`·`DUMMY` 불변(HINTS 는 자리만 `SqlTokens` 로)
- 새 규칙을 `Taint.knows`·`SINKS`·하이브리드(`params.java`)에 넣지 않는다 · `rules.yaml` 의 `ref:` 칸을 채우지 않는다(주석만) · `getAcceptedIssuers`·`SSLContext.init` 판정 추가 금지 · 글롭은 `*.java` 만
- 기존 규칙 전부의 판정·줄 불변 · `SqlLint` 다른 네 판정 불변 · `item` 의 `(` 가지 불변
- `CheckCorpusTest`·`LegacyCorpusTest`·`VcsCorpusTest` 판정 로직 수정 금지 · 골든 갱신은 5-58(fixtures)·5-57d(sql-lint)·V-44a(corpus)와 R-13 고침 뒤뿐
- 발췌·골든·compare 에 코드 본문을 싣지 않는다(규칙 3 — 파일·줄·규칙까지) · Juliet·Benchmark 표본 추가 금지

## 6. 최종 검증
- `bash scripts/verify.sh` · `--corpus` 초록 · `--db` 안 돈다(끝 줄 확인) · `pmd:check` 0 · `gate-probe.sh` 10/10
- 골든: `fixtures.json` = 기존 + `security/pos.java` 13 행 · `sql-lint.json` = 기존 + 셋 · `sql-tables.json`·`sql-joins.json`·`mapper-index.json`·`mybatis.json`·`java.json`·`pure-rules.json`·`gen/*` diff 0 · `self-check.txt` 새 키 0(`JavaRules` 네 줄 숫자 그대로) · corpus 골든 변화 = jeecg·jeesite·ruoyi-classic(+hits)·`sqlrules.json`·`sqlrules-diff.txt` 뿐
- 시험: `CheckRulesTest` 118·44 · `SqlLintTest` 사례 +3 · `PureCodeCheckTest` 32 · `SqlRuleCasesCorpusTest` AM08 25/28 · corpus 시험 10분 안
- 수동: 코드 검사 화면에 `C:/workspace/toolbox-corpus/ruoyi-classic/ruoyi-common` → `HttpUtils.java` 에 `security.trustAll` 3 건(줄 269·274·287) · 붙여넣기 `.java` 에 `DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();` 한 줄 메서드 → `security.xxe` warn(사람 몫 ① 에 한 줄)
- compare ⑭ 표 + R-13 표 둘 + 「드러난 것」 + 「후보로」

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- `self-check.txt` 에 새 키 또는 `JavaRules` 줄 숫자 변화 · `pmd:check` 에 새 짝 · `ArchitectureTest` 빨강
- `fixtures.json` 기존 행이 바뀐다 · `PureCodeCheckTest` 가 빨강(pure 규칙분이 달라졌다 — 새 줄이 정규식 규칙에 걸린 것) · 음성 픽스처 히트
- V-44a 에서 egov·commons·jspwiki·roller·egov-react·vcs-egov·juliet·benchmark 골든이 바뀐다(기대 0) · 한 표본에서 xxe 50 건 초과 · 시험 하나 10분 초과
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① 재채점 수치(§3-22 기대는 grep 추정 — jeecg 2·jeesite 1·ruoyi-classic 3, Juliet·Benchmark 0) ② JavaParser 가 두 픽스처의 덧붙인 익명 클래스·람다를 끝까지 읽는지(`CheckRulesTest` 첫 실행 — `java.parseError` 가 security 묶음엔 없어 못 읽으면 `hybridFallback` 뿐이고 새 규칙은 조용히 0 → 「양성에서 안 걸린 규칙」 으로 드러난다) ③ `item` 에 기준선 키가 없으므로 분기 둘 더한 복잡도가 15 이하인지(`SelfCheckTest` 첫 실행) ④ `-Dgolden.update=true` 가 `check/fixtures.json`·`sql-lint.json` 을 갱신하는 플래그인지(설계 47 과 같다 — `GoldenFiles`). 첫 스텝 전: `bash scripts/verify.sh` 초록 · `git log --oneline -1`(S0 커밋) · `ls C:/workspace/toolbox-corpus | wc -l`(33).
- **사용자만 정할 수 있는 것** — 없음(심각도·한 홉·5-57d·머지 위임은 2026-11-01 받았다 — 「사용자 결정」).
