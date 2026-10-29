# 설계 42 — 번들 58: 철저화 R3-1 — 보안 오염 추적(메서드 안) · 상수 전파 · 등급 A 승격 (Fable 설계 · Opus 실행, 2026-10-29)

## 맥락
R1(번들 57, 설계 41)이 잰 수치 — Benchmark 재현율 46%(pathtraver 0/133 · cmdi 26% · xss·ldapi·xpathi·trustbound·securecookie 규칙 없음) · Juliet 정밀도 50%(CWE78·CWE89 가 bad·good 을 같이 지적) · Juliet 재현율 0(CWE23·36·113·259·321·601). 원인은 하나다 — 정규식은 **값의 출처를 못 본다**. `new File(…getParameter(` 한 문장만 잡고, `String p = request.getCookies()[0].getValue(); new File(p)` 는 놓치며, `data = "foo"; new File(root + data)` 는 bad 와 똑같이 지적한다.

**사용자 결정(2026-10-29, 뒤집지 않는다)**
- R3 첫 번들의 축은 **보안 오염 추적**. 나머지 후보 16(SQL 정확도·Java 결함 묶음·구조 점검·생성기·포매터 등)은 `design/compare-2026-10-28.md` 「후보 행 제안」에 그대로 남아 뒤 번들의 입력이 된다
- R1 이 얼린 **등급 A 승격 둘 다** — 자바 쪽 `legacy-a`·`sqlfixture-a` 를 `none` 으로, 노드 스크립트 SKIP 에서 레거시 넷을 뺀다(실패 사다리 있음)
- **머지 위임 있음** — CI 초록 + AI 리뷰 처분 뒤 실행자가 머지
- 설계 41 의 결정은 그대로 — 에이전트 최소(마무리 독립 리뷰 하나), 장기 진행, 검증 인프라 최적화 금지

**라운드** — R1(번들 57) → **R3-1(이 번들)** → R3-2…(남은 후보 — 입력이 서면 설계) → 표본 재측정은 번들마다 V 행.

실행자는 Opus. 차례는 `5-51a` → `5-51b` → `5-51c` → `5-52a` → `5-52b` → `5-53` → `V-38a` → `V-38b` → `V-38` → 마무리. 가지 `work/2026-10-29`(이미 있다 — 번들 57 닫기 커밋 `4c1702f` 와 이 설계 커밋이 앞에 있다). 번들 하나·PR 하나.

---

## 1. 목표와 범위
- 한 문장: 코드 검사의 보안 규칙에 **메서드 안 오염 추적**(요청 값 → 지역 변수 → 싱크)과 **상수 전파**(글자 상수 → 자격 증명·키 싱크, 약한 알고리즘 이름)를 JavaParser 로 넣어, 정규식 아홉을 그 위로 옮기고 싱크 규칙 여섯을 더하며, Benchmark·Juliet 로 전·후를 잰다.
- 완료 시 체감: 코드 검사가 `String path = request.getParameter("f"); new FileInputStream(path)` 를 경로 조작으로 잡고, `"redirect:" + adminPath` 같은 고정 값은 더 이상 지적하지 않는다. 서블릿 XSS·SQL 실행 인자·LDAP·XPath·신뢰 경계·쿠키 Secure 가 새 규칙으로 보인다. `golden/corpus/security-benchmark.json`·`security-juliet.json` 의 재현율·정밀도가 오르고 compare 문서 ⑧ 에 전·후 표가 있다. 레거시 넷 표본이 등급 A 게이트에 들어간다.
- In scope: 새 `core/check/Taint.java` + `TaintTest` · `RuleSet`·`RegexRule`·`JavaRules` 배선 · `rules.yaml`(정규식 아홉에 `params.java`, 새 규칙 여섯) · 픽스처(`security/pos.java`·`neg.java` 줄 추가, `java/PosTaint.java`·`NegTaint.java` 신설) · `App.java`·`Cli.java` 의 `tb-ignore security.osCommand` 표식 둘 삭제 · 골든(`check/fixtures.json`·`check/java.json`·`corpus/check-*.json`·`-hits`·`security-*.json`·`-fp`·`-fn`) · `SecurityBenchmarkCorpusTest`·`JulietCorpusTest` 매핑과 서블릿 하위 지표 · `LegacyCorpusTest`·`SqlFixtureCorpusTest` A 승격 · `corpus-common.js` SKIP · 노드 corpus 골든·baseline · `design/compare-2026-10-28.md` ⑧ · PLAN 4장 표본 문단 한 문장 · 분할표·번들 표 · PROGRESS.
- Out of scope: 메서드 밖 오염 추적(호출 그래프·필드) · `pure/`(동결 — 순수본 정규식은 그대로 돈다, §3-1) · portfolio 재주입 · `security.sqlConcat`·`sqlDollar` 정규식 변경 · XXE·인증서 검증·자원 해제 규칙(후보에 남는다) · SpotBugs 채점 · 화면 변경 · 새 Maven 의존성 · Juliet 을 MANIFEST 에 넣기 · 검증 속도.

## 2. 현재 구조 요약(탐색 2026-10-29 실측)

**규칙 배선**
- `RuleSet.load`(`RuleSet.java:95-112`) — def 마다 `kind` 로 가른다: `marker`·`external`·`dup` 는 전용 자리, `java`·`mybatis` 는 `parsedDefs`, 나머지(`regex`·`absent`·`file`·`jsUnusedImport`·`jsxInputLabel`)는 `build(d)` 로 `Rule` 객체(`:136-142`, 모르는 kind 는 예외). `of(kind)`(`:212-214`)가 `parsedDefs` 를 kind 로 거른다. `Run`(`:222`)은 `of("java")` 가 비지 않으면 `new JavaRules(of("java"), naming)`.
- `Run.apply`(`:241`) — `javaRules != null && "java".equals(s.lang())` 일 때만 `javaRules.apply(s)`. 정규식 `Rule` 들은 `def.matches(s)`(글롭)로 돈다. 끝에 `Ignores`(tb-ignore)가 줄 단위로 거른다 — kind 와 무관하다.
- `RegexRule.apply`(`RegexRule.java:32`) — `params.when` 파일 게이트 → 줄마다 정규식. `Rule.Def.params()` 는 null 이면 `Map.of()`(`Rule.java:35`).
- `JavaRules`(611줄) — `on` = id → Def. `apply(Source)`(`:119-170`): `JavaSource` 로 파싱(JAVA_21 → 실패 시 JAVA_8, `JavaSource.java:14-15`, 심볼 해석기 없음) → `imports` → 타입마다 `type` → catch → 선언 순서 → 메트릭 → `testNoAssert`. 지적은 `add(Ctx, rule, Node)`(`:232`) = `finding(rule, s, line, excerpt(그 줄 원문))`. `Ctx` 는 private record(`Source s, String[] raw, List<Finding> out`). `annotated(t, CONTROLLER)`(`:331`)가 `@Controller`·`@RestController` 판정.
- `CheckRunner.runText(text, lang, rules)`(`CheckRunner.java:163`) — 글 하나 검사. 단위 시험에 쓴다.
- `Finding(file, line, group, rule, severity, excerpt)`, 발췌 160자.

**정규식 보안 규칙(옮길 아홉)** — `rules.yaml` 줄: `pathTraversal` 246(`new File…([^;]*getParameter(`, globs java·jsp) · `hardcodedPassword` 254(`\w*(password|passwd|pwd)\w*\s*=\s*"(?!\s*\+)[^"]+"`, i, java) · `osCommand` 275(`Runtime.getRuntime().exec(|new ProcessBuilder(`, java·jsp) · `ssrf` 299(`new URL([^;]*getParameter|(RestTemplate|HttpClient|HttpURLConnection|openConnection)[^;]*getParameter`, java) · `openRedirect` 307(`sendRedirect([^;]*getParameter|"redirect:"\s*\+`, java) · `responseSplitting` 315(`(setHeader|addHeader|addCookie|new Cookie)([^;]*getParameter`, java) · `weakHash` 323(`MessageDigest.getInstance("(MD2|MD4|MD5|SHA-?1)"`, java·jsp) · `weakCipher` 332(`Cipher.getInstance("(DES|DESede|RC2|RC4|ARCFOUR|Blowfish)\b` · `/ECB/` · `"(AES|DESede|Blowfish)"` 정확히, java·jsp — 5-16: 모드 없는 이름은 ECB) · `hardcodedKey` 349(`(SecretKeySpec|IvParameterSpec|PBEKeySpec)\s*\(\s*"`, java). 보안 36 전부 `ref:` 비어 있음(사람 몫). 내장 규칙 105(`CheckRulesTest.java:167`).

**순수본 동기 — 함정 ①(이 설계의 핵심 제약)**
- `PureRulesJsonTest.goldenJson`(`:30-65`) — 내장 def 중 **`kind == "regex"` 만** 모아 `golden/check/pure-rules.json`(63) 을 만들고, `pureHtmlEmbedsGolden` 이 `pure/tools/code_check.html` 의 `var RULES` 와 같아야 한다. 골든 항목 열은 `flags·group·id·langs·max·message·regex·severity·skipComments` — **`params` 는 안 들어간다**(`max` 만 따로).
- `PureCodeCheckTest`(`@Tag("corpus")`, node) — 순수본을 `file://` 로 열어 `fixtures/check/{common,jsp,tsx,security,a11y}` 32 파일에 돌린 결과가 자바 골든 `check/fixtures.json` 중 **id 가 `pure-rules.json` 에 있는 것**과 파일·줄·규칙으로 같아야 한다(`:52-66`). 음성 픽스처는 0.
- 따라서 정규식 아홉을 `kind: java` 로 바꾸면 `pure-rules.json` 이 54 로 줄고 순수본은 못 고치므로(동결·portfolio 인계 금지) `pureHtmlEmbedsGolden` 이 빨강으로 **막힌다**. 5-50 때는 규칙 하나를 빼고 순수본을 재주입했지만 지금은 그 길이 없다. → §3-1 하이브리드.

**픽스처·골든**
- `CheckRulesTest.fixturesGolden`(`:76-109`) — 그룹 java·mybatis 를 끄고 `fixtures/check/` 를 돈다(`java/`·`mybatis/`·`ext/` 폴더는 건너뜀). 음성 히트 0 · **켜진 규칙 전부가 양성에서 한 번은 걸려야 한다** · `check/fixtures.json`. `security/pos.java` 는 34줄 — `q(HttpServletRequest request, Model model, String id)`(7·10·11·12줄 히트)와 `sec2(request, response, Upload file)`(17~32줄, 「한 줄에 한 규칙」). `neg.java` 33줄 — `sec2(HttpServletResponse response)` 에 안전한 꼴(`sendRedirect("/fixed.do")`·`new URL("https://…")`·`addCookie(new Cookie("a","b"))`·`Cipher.getInstance("AES/GCM/NoPadding")`).
- `JavaRulesTest.fixturesGolden`(`:47-66`) — 프로필 egov35 로 `fixtures/check/java/{Pos*,Neg*}.java` 22 파일. `Neg*` 히트 0 · 그룹 java·metric 의 kind java 규칙은 전부 걸려야 함(보안 규칙은 이 집합 밖) · `check/java.json`. 기존 22 파일에 싱크 낱말(`sendRedirect`·`exec(`·`new File`·`getParameter`·`executeQuery`·`getWriter`·`password`·`getInstance(`·`setAttribute`·`setSecure`)이 **없다** — 새 규칙이 기존 Neg 를 건드리지 않는다.
- 자기 검사 `SelfCheckTest.mainMatchesBaseline`(`golden/check/self-check.txt`) — 우리 코드에 새 지적이 생기면 빨강. `App.java:249`·`Cli.java:71` 의 `// tb-ignore security.osCommand …` 표식은 지금 정규식 지적을 뺀다 — 오염 판정이 되면 지적이 없어져 `common.unusedIgnore`(쓸모없는 표식, `rules.yaml:85`)가 새 지적으로 선다. `ArchitectureTest:108-116` 은 ProcessBuilder·exec 자리를 두 클래스로 고정한다(표식과 무관).

**채점 하네스(설계 41)**
- `SecurityBenchmarkCorpusTest`(`:31-41` MAP, `:67-100` 채점) — `CheckCorpusTest.rules(false)` 로 `benchmark/src/main/java/org/owasp/benchmark/testcode` 2,740 파일, CSV 행마다 매핑 규칙 하나라도 있으면 hit. `s[4]`(`otherRules`) = 매핑 밖 security 지적 수. 골든 `security-benchmark.json` + `-fp`·`-fn` conformance + A `none`. 미탐 범주 `xss`·`ldapi`·`xpathi`·`trustbound`·`securecookie` 는 `rules: []`.
- `JulietCorpusTest`(`:37-53` MAP, `:81-141`) — `assumeTrue` 선택 표본. CWE 폴더마다 bad·good 영역 채점, `row(mapped, tp fp fn tn files)`. `security-juliet.json` + `security-juliet-fp` + A `none`. 파일 이름이 출처를 적는다 — `CWE78_OS_Command_Injection__getParameter_Servlet_01.java`. 서블릿 출처(`_Servlet`) 파일 수: CWE23 180/722 · CWE36 180/722 · CWE78 180/722 · CWE89 915/3,668 · CWE113 549/2,202 · CWE601 540/542 · CWE80 1,080/1,084 · CWE81 540/542 · CWE83 540/542. 나머지 출처는 `console_readLine`·`connect_tcp`·`listen_tcp`·`database`·`Environment`·`File`·`Property`·`PropertiesFile`·`URLConnection` — 웹 요청이 아니다.
- Benchmark 실물(FN·FP 표본에서 본 꼴) — pathtraver FN: `request.getCookies()` → `fileName` 지역 변수 → `new java.io.FileInputStream(new java.io.File(fileName))`. cmdi FN: `param = request.getHeader(…)` → `java.net.URLDecoder.decode(param,…)` → `String[] argsEnv = {param}` → `r.exec(args, argsEnv)`(`Runtime r = Runtime.getRuntime()` 로 갈라 정규식이 못 본다). sqli FP 206: `sqlConcat` 이 `"SELECT … '" + bar + "'"` 를 `bar` 가 상수여도 지적. xss 싱크: `response.getWriter().printf·write·println·format·print(bar)` · `out.write(`(PrintWriter 지역 변수). ldapi: `ctx.search(base, filter, sc)`(DirContext). xpathi: `xp.compile(expression)`·`xpath.evaluate(expression, xmlDocument)`. trustbound: `request.getSession().setAttribute(name, bar)` — 어느 인자든. securecookie: `cookie.setSecure(false)` 18 + setSecure 없음 18. crypto·hash FN: `String algorithm = benchmarkprops.getProperty("cryptoAlg1", "DESede/ECB/PKCS5Padding"); Cipher.getInstance(algorithm)` — 기본값이 약하다 · `KeyGenerator.getInstance("DES")`. hash `getProperty("hashAlg1","SHA512")` 는 정적으로 못 안다.
- Juliet 실물 — bad: `data = request.getParameter("name")` / `request.getQueryString()` / `getCookies()[0].getValue()`, good: `data = "foo"`(G2B) 또는 bad 출처 뒤 검증(B2G). 싱크 — CWE23·36 `new File(root + data)` → `new FileInputStream(file)` · CWE78 `Runtime.getRuntime().exec(osCommand + data)` · CWE89 `sqlStatement.execute("… '" + data + "'")`·`executeUpdate`·`executeQuery`(B2G 는 `prepareStatement("… ?")` + `setString`) · CWE113 `response.setHeader("Location", data)`·`addCookie(new Cookie("lang", data))` · CWE601 `response.sendRedirect(data)`(앞에 `new URI(data)`) · CWE80·83 `response.getWriter().println("…" + data)` · CWE81 `response.sendError(404, "…" + data)` · CWE259 `DriverManager.getConnection("data-url", "root", data)`(`data` 는 글자 상수) · CWE321 `new SecretKeySpec(data.getBytes("UTF-8"), "AES")` · CWE327 good 이 `Cipher.getInstance("AES")`(모드 없음 — 5-16 결정으로 우리는 ECB 로 보고 지적한다, FP 34 는 의도).

**레거시 골든에 든 아홉의 건수**(`check-<표본>.json`) — jeecg: osCommand 2 · hardcodedPassword 4 · weakHash 2 · sqlConcat 78 / jeesite: osCommand 3 · **openRedirect 95** · hardcodedPassword 1 · weakHash 1 / zheng: openRedirect 2 · hardcodedPassword 2 · weakHash 1 · weakCipher 2 / ruoyi-classic: weakHash 2. egov·commons·jspwiki·roller 골든도 같은 id 가 있으면 바뀐다 — 실행자가 diff 를 이력에 적는다. jeesite 95 는 `"redirect:" + adminPath + …`(필드) 꼴이라 오염 판정 뒤 0 언저리로 내려간다 — 의도된 정밀도 상승.

**A 승격 재료**
- `LegacyCorpusTest.java:55` `CorpusFiles.conformance("legacy-a", aCandidates)` · `SqlFixtureCorpusTest.java:107` `conformance("sqlfixture-a", a)` — 둘 다 실측 0(`golden/corpus/legacy-a.txt`·`sqlfixture-a.txt` 빈 파일). `CorpusFiles.none(what, broken, total)`(`CorpusFiles.java:195`)이 등급 A 게이트. A 후보 = parseError·예외(파일 시간 「느림」은 R1 이 뺐다 — `CheckRunner.run` 이 폴더 단위).
- `scripts/puppeteer/corpus-common.js:8-9` `SKIP` 에 `'jeecg', 'jeesite', 'zheng', 'ruoyi-classic', 'benchmark', 'juliet', 'sqlfluff-dialects'`. 노드 시험 셋 — `JspFmtCorpusTest`(`web/`, `corpus-jspfmt.js`, `none("jsp_formatter")` + `conformance("jspfmt-b")`) · `StripCorpusTest`(`corpus-strip.js`, 언어별 `none` 아홉 + `baseline("strip-<lang>")`) · `JsCorpusTest`(`corpus-js.js`, `none` 셋 + `conformance("js-json-lenient")` + `baseline("js-bytes-unmappable")`). 레거시 넷 = JSP 623 · Java 1,538 · 매퍼 88 · FTL 209 · Thymeleaf html 138. benchmark(Java 2,741)·juliet(11,151)·sqlfluff-dialects(SQL 1,166)는 프로젝트가 아니라 포매터·주석 삭제 표본으로 뜻이 없고 시간만 든다 — SKIP 에 남긴다.
- 함정 ② — `corpus/MANIFEST` 는 db 레인. 이 번들은 MANIFEST 를 안 고친다(새 표본 없음). 바뀌는 레인: java(`src/main`·`src/test`), corpus(`core/check`·`golden/corpus`·`scripts/puppeteer`), docs. push 앞 `verify.sh --corpus` 가 필요하고 `--db` 는 pom·db 경로를 안 건드리면 필요 없다(`scripts/verify-fingerprint.sh` LANE_DB).
- 함정 ③ — 시험 하나 상한: corpus 10분. `LegacyCorpusTest` 2분 8초 · Juliet 2분 18초(단독 실행은 `-Djunit.timeout=10m`) · Benchmark 1분 53초. 오염 추적은 파싱된 AST 를 메서드마다 한 번 더 걷는다 — 파싱보다 싸다. 노드 셋에 레거시 JSP·Java 를 더하면 `StripCorpusTest` 가 길어진다(실측은 V-38b).
- 함정 ④ — PMD errorprone·bestpractices 는 CI `verify` 단계에서만 돈다(빠른 검증·`--corpus` 는 안 탄다, 번들 57 CI 처분). 새 클래스·새 시험은 손으로 `bash scripts/mvn.sh -q pmd:check` 를 돌려 0 을 본다. 자주 걸린 것 — `LiteralsFirstInComparisons`·`UseVarargs`·`AvoidCatchingGenericException`·`AvoidLiteralsInIfCondition`.
- 함정 ⑤ — `metric.complexity` ≤ 15(새 메서드) · 빈 catch 표식 · 단언 없는 시험 금지 — `SelfCheckTest` 가 본다. 식 판정은 분기가 많으니 노드 종류마다 메서드를 나눈다.

## 3. 설계 결정 — 전부 [고정]

1. **하이브리드 규칙 — 정규식 def 는 그대로 두고 `params.java` 로 Java 쪽 판정을 덧댄다.** 아홉 규칙은 `kind: regex`·`regex:`·`globs` 를 유지한다(순수본·`pure-rules.json`·`PureRulesJsonTest` 63 불변). `params: {java: taint|secret|algorithm}` 이 붙은 def 는 백엔드에서 **`.java` 파일은 `JavaRules`(오염·상수 판정)가, 나머지 글롭(`.jsp`)은 정규식이** 본다. `RegexRule.apply` 가 `"java".equals(s.lang()) && def.params().get("java") != null` 이면 빈 목록, `RuleSet.load` 가 그 def 를 `rules` 와 `parsedDefs` **둘 다**에 넣고, `Run` 은 `javaDefs()`(kind java ∪ `params.java` 있는 regex)로 `JavaRules` 를 만든다. 순수본 동기 시험은 `security/pos.java`·`neg.java` 의 **같은 줄**이 양쪽에서 걸려야 하므로 기존 양성 줄은 전부 「출처가 싱크 식 안에 바로 있는 꼴」이고 오염 판정도 그 줄을 잡는다(§4 5-51b 검증). 오염 전용 양성(변수 한 번 거침)은 `fixtures/check/java/PosTaint.java` 에 — `PureCodeCheckTest.DIRS` 밖. 버린 대안 — ⓐ `kind: java` 로 전환(순수본에서 아홉이 빠져야 해 막힘) ⓑ 새 id 로 추가(같은 줄 이중 지적 + 아홉의 KISA `ref:` 자리·억제 표식 호환이 깨짐) ⓒ 정규식 def 를 `enabled: false`(순수본 동기 시험이 백엔드 히트를 요구해 빨강).
2. **엔진은 메서드 안, 흐름 무관(flow-insensitive), 고정점.** 새 클래스 `core/check/Taint.java`(package-private). 메서드·생성자(`CallableDeclaration`) 몸마다 ① 오염 집합 T 와 상수 집합 L 을 대입문 순회로 변하지 않을 때까지(상한 10회) 키우고 ② 싱크 호출의 지정 인자를 판정한다. 분기·순서를 안 본다 — `bar = param` 이 한 가지에만 있어도 `bar` 는 오염. 정밀도를 일부 잃고(Benchmark 「항상 안전한 가지」 꼴·Juliet B2G) 재현율과 단순함을 얻는다. 버린 대안 — 흐름 감응 데이터플로(상태 복제·합류, 람다·예외 경로 — JavaParser 만으로 이 번들 안에 끝나지 않는다).
3. **출처(source)** — 식이 다음이면 오염: ⓐ 메서드 호출 이름이 `getParameter`·`getParameterValues`·`getParameterMap`·`getHeader`·`getHeaders`·`getHeaderNames`·`getCookies`·`getQueryString`·`getRequestURI`·`getRequestURL`·`getPathInfo`·`getServletPath`·`getInputStream`·`getReader`·`getPart`·`getParts`·`getOriginalFilename`·`getRemoteUser`·`getRequestedSessionId` ⓑ `@RequestParam`·`@PathVariable`·`@RequestHeader`·`@CookieValue`·`@RequestBody`·`@ModelAttribute`·`@RequestPart`·`@MatrixVariable` 이 붙은 매개변수 이름 ⓒ **컨트롤러 처리 메서드의 모든 매개변수**(둘러싼 클래스에 `@Controller`·`@RestController`, 또는 메서드에 이름이 `Mapping` 으로 끝나는 애너테이션) — 단, 타입 단순 이름이 `HttpServletRequest`·`HttpServletResponse`·`HttpSession`·`Model`·`ModelMap`·`ModelAndView`·`BindingResult`·`Errors`·`Principal`·`Authentication`·`Locale`·`RedirectAttributes`·`SessionStatus`·`WebRequest`·`NativeWebRequest` 면 뺀다(Spring 은 단순형 매개변수를 애너테이션 없이도 요청에서 묶는다). `System.getenv`·`getProperty`·`readLine`·`ResultSet.getString` 은 출처가 **아니다**(설정·파일·DB 는 공격자 입력이 아니다 — Juliet 의 비서블릿 출처 변형은 설계상 FN, §3-9 서블릿 지표로 따로 본다). 버린 대안 — 타입 기반 출처(심볼 해석기 없음).
4. **전파** — `tainted(e)`: `NameExpr` ∈ T · `MethodCallExpr`: 이름 ∈ 출처 → 참 / 스코프 오염 && 이름 ∉ SCOPE_CLEAN → 참 / 인자 하나라도 오염 && 이름 ∉ ARG_CLEAN ∪ SANITIZER → 참 · `BinaryExpr`(+ 만) 어느 쪽 · `ConditionalExpr` 어느 가지 · `EnclosedExpr`·`CastExpr` 안 · `ObjectCreationExpr`·`ArrayCreationExpr`(초기화 요소)·`ArrayAccessExpr`(배열 이름)·`FieldAccessExpr`(스코프) 전파 · 리터럴·`this`·필드 이름(선언 없는 NameExpr)은 거짓. 대입: `VariableDeclarator` 초기값 · `AssignExpr`(`=`·`+=`) 대상이 NameExpr 면 T 에 · `for (String s : taintedArr)` → `s` · **변경자 호출** `append`·`insert`·`add`·`addAll`·`put`·`putAll`·`push`·`offer`·`set` 의 인자가 오염이고 스코프가 NameExpr 면 그 이름을 T 에(StringBuilder·List·Map 오염). SCOPE_CLEAN = `equals equalsIgnoreCase length size isEmpty isBlank contains containsKey startsWith endsWith indexOf lastIndexOf matches hashCode compareTo compareToIgnoreCase getClass`. ARG_CLEAN = `get getOrDefault containsKey contains indexOf equals matches startsWith endsWith`(오염된 **키**로 깨끗한 맵·목록에서 꺼낸 값은 깨끗 — Benchmark 허용목록 꼴). SANITIZER = `parseInt parseLong parseDouble parseFloat parseBoolean parseShort parseByte encodeForHTML encodeForHTMLAttribute encodeForJavaScript encodeForSQL encodeForLDAP encodeForDN encodeForXPath encodeForXML encodeForURL encodeForOS htmlEscape htmlEscapeHex escapeHtml escapeHtml4 escapeXml escapeXml10 escapeXml11 escapeJava escapeEcmaScript escapeSql cleanXSS forHtml forHtmlAttribute forJavaScript forUri forUriComponent getCanonicalPath normalize`. 람다 몸은 걷지 않는다. 버린 대안 — 문자열 연산을 전부 전파(`length()` 가 int 를 오염시켜 `new File("f" + n)` 오탐).
5. **싱크 표(규칙 id → 메서드·생성자 이름 → 보는 인자)** — `*` 는 어느 인자든, 숫자는 0 기준. 스코프 조건은 NameExpr 스코프의 이름 정규식(i) 또는 스코프가 지정 호출.

   | id | 싱크 | 인자 | 스코프 조건 |
   |---|---|---|---|
   | `security.osCommand` | `exec` · `new ProcessBuilder` · `command` | `*` | 없음 |
   | `security.pathTraversal` | `new File`·`FileInputStream`·`FileOutputStream`·`FileReader`·`FileWriter`·`RandomAccessFile` · `Paths.get`·`Path.of` · `resolve` · `transferTo` | `*` | 없음 |
   | `security.openRedirect` | `sendRedirect` · `new RedirectView` · `new ModelAndView` 의 0 · `return`·`new ModelAndView` 안 `"redirect:" + 오염`(BinaryExpr 왼쪽 끝이 `redirect:` 로 시작하는 리터럴) | `*` / 0 | 없음 |
   | `security.responseSplitting` | `setHeader`·`addHeader` · `new Cookie` | `*` | 없음 |
   | `security.ssrf` | `new URL`·`new URI`·`URI.create` · `getForObject`·`getForEntity`·`postForObject`·`postForEntity`·`exchange`·`execute`(RestTemplate) · `new HttpGet`·`new HttpPost`·`new HttpPut` · `uri`(HttpRequest.Builder) | 0 | `execute` 는 스코프 `(?i)(rest|template|client)` |
   | `security.sqlInjection`(새) | `executeQuery`·`executeUpdate`·`executeLargeUpdate`·`addBatch`·`prepareStatement`·`prepareCall`·`createQuery`·`createNativeQuery`·`createSQLQuery`·`createNamedQuery`·`queryForList`·`queryForObject`·`queryForMap`·`queryForRowSet`·`queryForStream`·`batchUpdate` · `execute`·`update`·`query` | 0 | `execute`·`update`·`query` 는 스코프 `(?i)(jdbc|template|statement|stmt|conn|connection|session|em|entitymanager)` |
   | `security.xssServlet`(새) | `print`·`println`·`printf`·`format`·`write`·`append` · `sendError` | `*` / `sendError` 1 | 스코프가 `getWriter()`·`getOutputStream()` 호출, 또는 그 호출로 초기화된 지역 변수(W 집합 — 메서드 안 `VariableDeclarator`·`AssignExpr`), 또는 이름 `(?i)^(out|writer|pw|printWriter|os|outputStream)$` |
   | `security.ldapInjection`(새) | `search` · `new LdapName` · `new InitialDirContext` | `*` | `search` 는 스코프 `(?i)(ctx|context|ldap|dir)` |
   | `security.xpathInjection`(새) | `compile`·`evaluate` · `selectNodes`·`selectSingleNode`·`selectObject` | 0 | `compile`·`evaluate` 는 스코프 `(?i)xpath` |
   | `security.trustBoundary`(새) | `setAttribute`·`putValue` | `*` | 스코프가 `getSession()` 호출 또는 이름 `(?i)session` |
   | `security.cookieSecureFlag`(새) | `setSecure(false)`·`setHttpOnly(false)` — 인자가 `false` 리터럴 | — | 오염과 무관(싱크만) |

   같은 줄·같은 규칙은 한 건. `addCookie(new Cookie(k, 오염))` 은 안쪽 `new Cookie` 가 잡는다. `new FileInputStream(new File(오염))` 도 안쪽 한 건. 버린 대안 — JdbcTemplate `update(sql, args…)` 의 모든 인자(바인드 인자는 삽입이 아니다 — 0 번만).
6. **상수 전파(`secret`·`algorithm`)** — `constant(e)`: 글자·문자 리터럴 · `NameExpr` ∈ L · 스코프가 상수인 `getBytes`·`toCharArray`·`trim`·`toUpperCase`·`toLowerCase`·`concat`·`intern`·`substring` · 상수끼리 `+` · `getProperty(k, 기본값)`·`getOrDefault(k, 기본값)` 의 **둘째 인자가 상수면 그 값**(「기본값이 약하다」). L 은 메서드 안 대입 + **클래스 필드 초기값**(`FieldDeclaration` 의 리터럴 초기값 — `private static final String KEY = "…"`). 판정은 **그 규칙의 정규식을 재사용**한다 — 해석된 리터럴로 정규식이 보던 꼴의 글을 합성해 `def.pattern()` 에 묻는다: `algorithm` 은 `MessageDigest.getInstance(x)`·`Cipher.getInstance(x)`·`KeyGenerator.getInstance(x)`·`SecretKeyFactory.getInstance(x)`·`Mac.getInstance(x)` 의 0 번 인자 x 가 상수 집합이면 그 값마다 `<def 가 보는 클래스>.getInstance("<값>")`(weakHash 는 `MessageDigest`, weakCipher 는 `Cipher`) 글을 만들어 정규식 매치 — 5-16 의 ECB·DES 판정이 그대로 산다. `secret` 은 ⓐ `hardcodedPassword`: 대입·선언 대상 이름 N, 값이 상수면 `N = "<값>"` 글을 정규식에(이름 판정은 정규식이 한다) + 싱크 인자 — `getConnection` 2 · `setPassword` 0 · `new PasswordAuthentication` 1 · `login` 1 · `setCredentials`·`new UsernamePasswordCredentials` 1 · `connect` 2 가 상수면 지적 ⓑ `hardcodedKey`: `new SecretKeySpec`·`IvParameterSpec`·`PBEKeySpec`·`GCMParameterSpec`·`SecretKeySpec` 0 번(바이트 배열 리터럴 `new byte[]{…}` 도 상수) 이 상수면 `SecretKeySpec("` 글을 정규식에. 버린 대안 — 정규식 본문을 자바로 다시 쓰기(5-16 판정이 둘로 갈린다).
7. **발췌는 싱크 줄 원문**(기존 `add(Ctx, rule, node)`) — 출처 이름을 덧붙이지 않는다(규칙 3 — 발췌 160자 안에 사용자 값이 더 들어갈 여지를 안 만든다).
8. **매핑 갱신** — Benchmark `sqli→security.sqlInjection`(sqlConcat 은 `otherRules` 로 내려간다) · `xss→xssServlet` · `ldapi→ldapInjection` · `xpathi→xpathInjection` · `trustbound→trustBoundary` · `securecookie→cookieSecureFlag`, 나머지 그대로. Juliet `CWE89→sqlInjection` · `CWE80·81·83→xssServlet`, 나머지 그대로.
9. **Juliet 서블릿 지표** — `row()` 에 `servlet: {files, tp, fn, recallPct}` 를 더한다(파일 이름에 `_Servlet` 이 든 것만, 그 CWE 에 하나도 없으면 키 생략). 전체 재현율은 비서블릿 출처 때문에 낮게 남는다 — 그것이 설계라는 것을 숫자가 말하게 한다. 수치는 **게이트가 아니다**(골든 갱신 + 이력). 목표(안 맞아도 빨강 아님): Benchmark 전체 재현율 ≥ 65 · Juliet 서블릿 재현율(매핑 CWE) ≥ 80 · Juliet 전체 정밀도 ≥ 60.
10. **A 승격** — `legacy-a`·`sqlfixture-a` 는 `CorpusFiles.none` 으로, 빈 골든 둘 삭제. 노드 SKIP 에서 `jeecg`·`jeesite`·`zheng`·`ruoyi-classic` 만 뺀다(benchmark·juliet·sqlfluff-dialects 는 남긴다 — §2). 실패 사다리는 V-38b.
11. **compare ⑧** — `design/compare-2026-10-28.md` 끝에 `## ⑧ 재측정 — R3-1(번들 58)` 절: 표본 수치 표(⑦ 과 같은 열 + 「전 → 후」), 범주별 P/R 전·후, 레거시 넷 아홉 규칙 건수 전·후, 「드러난 것」. 비교표 ①~⑥ 과 「후보 행 제안」 은 안 고친다 — 행으로 올라간 후보 넷(나-1·나-2·나-12·securecookie)은 ⑧ 에 「번들 58 로」 한 줄.
12. **`tb-ignore security.osCommand` 표식 둘 삭제**(`App.java:249`·`Cli.java:71`) — 주석의 나머지(「ArchitectureTest 가 자리 고정」)는 남긴다. 버린 대안 — 표식 유지(`common.unusedIgnore` 가 자기 검사 빨강).
13. 행 접두 — 규칙 행은 `5-51`·`5-52`·`5-53`, 승격·재측정은 `V-38`. 설계 문서 `design/42-bundle58.md`(이 글).

## 4. 실행 스텝

### S0 — 설계 문서·행·번들 표 (설계 세션이 했다)
- `design/42-bundle58.md` · PLAN 분할표 행 9 · 번들 표 「번들 58」 · PROGRESS 「열려 있는 것」·이력. 커밋은 이력에.

### 5-51a — `Taint` 엔진 + 단위 시험
- 대상: 신설 `src/main/java/kr/ejg/toolbox/core/check/Taint.java`, 신설 `src/test/java/kr/ejg/toolbox/core/check/TaintTest.java`
- 변경:
  - `Taint` — 입력은 `CompilationUnit` 과 켜진 규칙 Def 맵, 출력은 `List<Hit>`:
    ```java
    /** 오염 추적·상수 전파(설계 42 §3-2~6) — 메서드 안, 흐름 무관, 고정점. JavaRules 가 파일마다 한 번 부른다 */
    final class Taint {
        record Hit(String rule, Node node) {}
        /** 규칙 id → 분석기 — regex def 의 params.java(taint·secret·algorithm) 또는 kind java 의 새 보안 규칙(taint) */
        Taint(Map<String, Rule.Def> on) { … }
        List<Hit> scan(CompilationUnit cu) { … }   // 타입마다 필드 상수 L0 수집 → CallableDeclaration 마다 method(m, L0)
        static boolean tainted(Expression e, Set<String> t)   // §3-4 — 노드 종류마다 private 메서드로 나눈다(복잡도 15)
        static boolean constant(Expression e, Set<String> l)  // §3-6
        static Set<String> resolve(Expression e, Map<String,String> l) // 상수 값 집합(getProperty 기본값 포함)
    }
    ```
    - 집합 키우기: `body.findAll(VariableDeclarator.class)`·`AssignExpr`·`ForEachStmt`·변경자 `MethodCallExpr` 를 한 바퀴 돌며 T·L·W(writer 변수) 갱신, 바뀜이 없을 때까지(상한 10).
    - 싱크 표(§3-5)와 SCOPE_CLEAN·ARG_CLEAN·SANITIZER·SOURCES·EXCLUDED_PARAM_TYPES 는 `static final Set/Map` 상수. 싱크 표는 `record Sink(Set<String> names, int arg /* -1 = any */, Pattern scope, boolean callScope)` 목록을 id 로 묶는다.
    - 상수 판정(§3-6)은 `def.pattern()` 에 합성 글을 묻는다 — `Rule.Def` 를 받아 둔다.
    - 같은 (rule, line) 중복 제거.
  - `TaintTest` — `CheckRunner.runText(code, "java", rules)` 로 보안 규칙만 켠 `RuleSet`(`CheckCorpusTest.rules(false)` 같은 꼴 — `RuleSet.load(profile, null)`)을 쓰되 5-51a 시점엔 배선이 없으므로 **`Taint` 를 직접 부른다**: `new JavaSource()` 로 파싱 → `new Taint(defs).scan(cu)` → `Hit` 의 규칙·줄 단언. 사례(각각 `@Test` 하나, 단언 필수):
    ① 직접 — `new File(request.getParameter("p"))` → pathTraversal 1 ② 변수 한 번 — `String p = request.getHeader("x"); new FileInputStream(p)` → 1 ③ 쿠키 배열 — `Cookie[] cs = request.getCookies(); String v = cs[0].getValue(); new File(v)` → 1 ④ StringBuilder — `sb.append(p); exec(sb.toString())` → osCommand 1 ⑤ 깨끗 — `String p = "fixed"; new File(p)` → 0 ⑥ 정화 — `int n = Integer.parseInt(request.getParameter("n")); new File("f" + n)` → 0 ⑦ 허용목록 — `String v = map.get(request.getParameter("k")); new File(v)` → 0 ⑧ 컨트롤러 매개변수 — `@Controller class C { @GetMapping String f(String name, Model m) { new File(name); } }` → 1, `Model` 은 0 ⑨ `@RequestParam` 없는 비컨트롤러 메서드 매개변수 → 0 ⑩ 조건 가지 — `bar = cond ? "safe" : param` → 1(흐름 무관) ⑪ 분기 대입 — `if (x) bar = "a"; else bar = param; new File(bar)` → 1 ⑫ writer — `PrintWriter out = response.getWriter(); out.print(p)` → xssServlet 1, `out.print("ok")` → 0 ⑬ sendError 1번 인자 ⑭ 세션 — `request.getSession().setAttribute("u", p)` → trustBoundary 1 ⑮ `"redirect:" + p` 반환 → openRedirect 1, `"redirect:" + adminPath`(필드) → 0 ⑯ sqlInjection — `stmt.executeQuery("SELECT * FROM t WHERE a='" + p + "'")` → 1, `jdbcTemplate.update(sql, p)`(sql 상수) → 0, `dao.update(vo)` → 0(스코프 조건) ⑰ secret — `String pwd = "x"` → hardcodedPassword 1(정규식 재사용) · `DriverManager.getConnection(url, user, "abc")` → 1 · `getConnection(url, user, pw)` 에서 `pw` 가 매개변수 → 0 ⑱ 필드 상수 키 — `static final String KEY = "0123456789abcdef"; … new SecretKeySpec(KEY.getBytes(), "AES")` → hardcodedKey 1 ⑲ algorithm — `String a = "MD5"; MessageDigest.getInstance(a)` → weakHash 1 · `getProperty("alg", "DESede/ECB/PKCS5Padding")` 기본값 → weakCipher 1 · `Cipher.getInstance("AES/GCM/NoPadding")` → 0 · `KeyGenerator.getInstance("DES")` → weakCipher 1 ⑳ cookieSecureFlag — `c.setSecure(false)` 1 · `c.setSecure(true)` 0 · `new Cookie` 만 → 0 ㉑ 고정점 — 뒤 줄 대입이 앞 줄 싱크를 오염시키는 꼴(`new File(a)` 뒤에 `a = p`) → 1(흐름 무관 명시) ㉒ 람다 안 싱크 → 0(안 걷는다).
    - 시험용 Def 는 `RuleSet.load(null, null)`(내장 전부)에서 `defs()` 로 꺼내 id 로 고른다 — rules.yaml 에 새 id·params 가 5-51b·5-52 에서 들어가므로 **5-51a 에서는 시험 안에서 `Rule.Def` 를 손으로 만든다**(`new Rule.Def(id, "security", "error", "regex", globs, regex, flags, true, msg, Map.of("java","taint"), true, fix, null)` 꼴 — 생성자는 `Rule.java:40`). 5-51b 뒤 내장 def 로 바꾸지 않아도 된다(단위 시험은 엔진만 본다).
- 검증: `bash scripts/mvn.sh -q test -Dtest=TaintTest` 초록 · `bash scripts/mvn.sh -q pmd:check` 0 · `bash scripts/verify.sh` 초록(`SelfCheckTest` — 새 메서드 복잡도·단언).
- 의존: 없음.

### 5-51b — 하이브리드 배선 + `taint` 다섯 전환 + 픽스처·표식·골든
- 대상: `RuleSet.java` · `RegexRule.java` · `JavaRules.java` · `rules.yaml` · `fixtures/check/security/pos.java`·`neg.java` · 신설 `fixtures/check/java/PosTaint.java`·`NegTaint.java` · `App.java:249` · `Cli.java:71` · 골든
- 변경:
  - `RuleSet.load`(`:107-110`): `java`·`mybatis` 가지 뒤에 `else if (d.on()) { rules.add(build(d, profile)); if (hybrid(d)) parsedDefs.add(d); }` — `static boolean hybrid(Rule.Def d) { return d.kind().equals("regex") && d.params().get("java") != null; }`. `validate` 에 `params.java` 값은 `taint`·`secret`·`algorithm` 만(아니면 `IllegalArgumentException(d.id() + ": params.java 는 taint·secret·algorithm")`).
  - `RuleSet.javaDefs()` = `parsedDefs.stream().filter(d -> d.kind().equals("java") || hybrid(d))`. `Run` 의 `javaRules` 는 `javaDefs()` 로. `of("mybatis")` 는 그대로.
  - `RegexRule.apply` 첫 줄: `if ("java".equals(s.lang()) && def.params().get("java") != null) return List.of();` — 주석 「설계 42 §3-1 — .java 는 JavaRules 의 오염·상수 판정이 본다」.
  - `JavaRules`: 필드 `private final Taint taint;` — 생성자에서 `Taint.wants(defs)`(params.java 가 있거나 §3-5 새 id 인 def 가 하나라도) 면 `new Taint(on)` 아니면 null. `apply` 의 `imports(cu, c);` 바로 뒤 `if (taint != null) { for (Taint.Hit h : taint.scan(cu)) add(c, h.rule(), h.node()); }`. `add` 는 `on.containsKey` 를 보니 안전.
  - `rules.yaml`: `pathTraversal`·`osCommand`·`ssrf`·`openRedirect`·`responseSplitting` 다섯에 `params: {java: taint}` 한 줄 + 주석 `# 설계 42 — .java 는 오염 추적(Taint), 다른 글롭은 이 정규식`. `message`·`severity`·`regex`·`globs`·`flags` 는 **글자 하나도 안 바꾼다** — 전부 `pure-rules.json` 의 열이다(`osCommand` 의 「외부 입력이 닿는지 확인」도 그대로). 머리 주석(`:2`)에 `params.java` 설명 한 줄.
  - `security/pos.java` — 기존 줄 **안 고친다**(순수본 동기). `neg.java` 의 `sec2` 에 매개변수 `javax.servlet.http.HttpServletRequest request` 를 더하고(기존 줄 번호 불변 — 시그니처 줄만 바뀐다) 끝에 안전한 꼴 세 줄: `int n = Integer.parseInt(request.getParameter("n")); new java.io.File("f" + n);` · `response.sendRedirect("redirect:" + adminPath);`(어 — `"redirect:" +` 정규식에 걸린다. **쓰지 않는다**) → 대신 `String fixed = "/home.do"; response.sendRedirect(fixed);` · `new java.net.URL(BASE + "/api");`. 순수본 정규식도 이 줄들에 안 걸리는지 `PureCodeCheckTest` 로 확인(corpus 레인).
  - `fixtures/check/java/PosTaint.java`(`@Controller` 클래스 하나, `java.declarationOrder`·`metric.*` 에 안 걸리게 짧게 — 멤버 순서 static → instance → 생성자 → 메서드, 메서드 ≤ 10줄): 변수 한 번 거친 다섯 — pathTraversal(`getCookies`)·osCommand(`Runtime r = …; r.exec(cmd)`)·ssrf(`new URL(u)`)·openRedirect(`return "redirect:" + next`, 컨트롤러 매개변수)·responseSplitting(`response.addCookie(new Cookie("l", lang))`). `NegTaint.java`: 같은 싱크에 상수·정화·허용목록 값만 — 히트 0.
  - `App.java:249`·`Cli.java:71`: `// tb-ignore security.osCommand ` 부분만 지운다 → `// 기동 뒤 브라우저 열기 — ArchitectureTest 가 자리 고정` · `// git·svn 고정 목록 — ArchitectureTest 가 자리 고정`.
  - 골든 갱신(`-Dgolden.update=true`): `check/fixtures.json`(security 줄은 **diff 0 이어야 한다** — 다르면 §7 중단) · `check/java.json`(PosTaint 다섯 추가) · `golden/check/self-check.txt`(표식 둘이 빠진 만큼) · corpus 골든은 5-53 에서 한 번에.
- 검증: `bash scripts/mvn.sh -q test -Dtest=CheckRulesTest,JavaRulesTest,PureRulesJsonTest,SelfCheckTest,ArchitectureTest` 초록 · `git diff --stat src/test/resources/golden/check/fixtures.json` 이 0 · `PureRulesJsonTest` 63 그대로 · `bash scripts/mvn.sh -q pmd:check` 0 · `bash scripts/verify.sh` 초록.
- 의존: 5-51a.

### 5-51c — `secret`·`algorithm` 넷 전환
- 대상: `rules.yaml` · `Taint.java`(§3-6 가 5-51a 에 들어 있으면 배선 확인만) · `fixtures/check/java/PosTaint.java`·`NegTaint.java` · 골든
- 변경:
  - `rules.yaml`: `hardcodedPassword`·`hardcodedKey` 에 `params: {java: secret}`, `weakHash`·`weakCipher` 에 `params: {java: algorithm}`.
  - `PosTaint.java` 에 넷 — `String algo = "MD5"; MessageDigest.getInstance(algo)` · `Cipher.getInstance(props.getProperty("alg", "DES/ECB/PKCS5Padding"))` · `DriverManager.getConnection(url, user, "s3cret")` · `static final String IV = "0123456789abcdef"; new IvParameterSpec(IV.getBytes())`. `NegTaint.java` 에 `getInstance("SHA-256")` 변수 경유 · `getConnection(url, user, pw)`(매개변수).
  - `security/pos.java` 7·23·24·26·28 줄은 그대로 걸려야 한다(이름 정규식·합성 글 재사용).
- 검증: 5-51b 와 같은 시험 + `fixtures.json` diff 0 · `check/java.json` 넷 추가 · `bash scripts/verify.sh` 초록.
- 의존: 5-51b.

### 5-52a — 새 규칙 `sqlInjection`·`xssServlet`
- 대상: `rules.yaml` · `fixtures/check/security/pos.java`·`neg.java` · `Taint.java`(싱크 표에 이미 있다) · 골든
- 변경:
  - `rules.yaml` ㅇ 2차 블록 끝(`cfgCookieSecure` 앞)에 둘 — `kind: java`, `globs: ['*.java']`, `group: security`:
    ```yaml
      # ---------------------------------------------------------------- ㅇ 4차 — 오염 추적 싱크(설계 42, 2026-10-29). .java 만 — JavaRules 의 Taint 가 본다
      - id: security.sqlInjection
        group: security
        severity: error
        kind: java
        globs: ['*.java']
        message: SQL 삽입 — 요청 값이 SQL 실행 인자까지 간다
        fix: 'PreparedStatement·매퍼 #{} 바인딩으로 — 값은 SQL 글자에 넣지 않는다'
      - id: security.xssServlet
        group: security
        severity: error
        kind: java
        globs: ['*.java']
        message: XSS — 요청 값을 응답 본문에 그대로 쓴다
        fix: 'HtmlUtils.htmlEscape 같은 출력 인코딩을 거치거나 JSP·템플릿의 이스케이프 출력으로'
    ```
  - `security/pos.java` `sec2` 끝에 두 줄(기존 줄 번호 불변): `conn.createStatement().executeQuery(request.getParameter("q"));` · `response.getWriter().print(request.getParameter("m"));` — 순수본 정규식(sqlConcat·exceptionMessage)에 안 걸리는 꼴(SQL 동사 리터럴 없음, `getMessage` 없음). `neg.java` 끝에 `conn.createStatement().executeQuery("SELECT 1");` · `response.getWriter().print("ok");`.
  - `CheckRulesTest.java:167` 105 → 107(주석에 「설계 42 오염 싱크 둘」).
- 검증: `CheckRulesTest`·`PureRulesJsonTest`(63 그대로) 초록 · `fixtures.json` 에 security/pos.java 두 줄만 추가 · `bash scripts/verify.sh`.
- 의존: 5-51b.

### 5-52b — 새 규칙 `ldapInjection`·`xpathInjection`·`trustBoundary`·`cookieSecureFlag`
- 대상: `rules.yaml` · `security/pos.java`·`neg.java` · 골든
- 변경:
  - 넷 추가(같은 블록): `security.ldapInjection`(error, 「LDAP 삽입 — 요청 값이 검색 필터에 간다」, fix 「필터 값은 `encodeForLDAP` 로 이스케이프하거나 허용 목록으로」) · `security.xpathInjection`(error, 「XPath 삽입 — 요청 값이 XPath 식에 간다」, fix 「XPath 변수(`XPathVariableResolver`)로 값을 넘긴다」) · `security.trustBoundary`(warn, 「신뢰 경계 위반 — 검증 없는 요청 값을 세션에 넣는다」, fix 「세션에는 검증·변환한 값만, 요청 값은 요청 범위에」) · `security.cookieSecureFlag`(warn, 「쿠키 Secure·HttpOnly 를 끈다」, fix 「`setSecure(true)`·`setHttpOnly(true)` — HTTPS 아닌 개발 환경은 설정으로 가른다」).
  - `pos.java` 네 줄: `ctx.search("ou=people", request.getParameter("f"), sc);` · `xpath.evaluate(request.getParameter("x"), doc);` · `request.getSession().setAttribute("u", request.getParameter("u"));` · `cookie.setSecure(false);`. `neg.java`: `ctx.search("ou=people", "(uid=admin)", sc);` · `cookie.setSecure(true);` · `request.getSession().setAttribute("u", user.getId());`(`user` 는 매개변수 아닌 지역 — 또는 생략).
  - `CheckRulesTest.java:167` → 111.
- 검증: 5-52a 와 같음 · `bash scripts/verify.sh`.
- 의존: 5-52a.

### 5-53 — Benchmark·Juliet 매핑 갱신 + 서블릿 지표 + corpus 골든 재채점
- 대상: `SecurityBenchmarkCorpusTest.java` · `JulietCorpusTest.java` · `golden/corpus/` (`security-*.json`·`-fp`·`-fn`, `check-*.json`·`-hits.txt` 전부, `legacy`·`analyze-*` 는 안 바뀜)
- 변경:
  - MAP(§3-8). Juliet `row()` 에 서블릿 하위 지표(§3-9) — `score()` 안에서 파일 이름 `contains("_Servlet")` 이면 `sv[0..2]`(tp fn files) 따로 세고 `row` 에 `servlet` 맵.
  - 재채점: `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='SecurityBenchmarkCorpusTest,JulietCorpusTest,CheckCorpusTest,LegacyCorpusTest' -Djunit.timeout=10m -Dgolden.update=true` → diff 를 읽고 **수치 전·후를 이력에**(범주별 P/R, 레거시 아홉 건수). `-fp`·`-fn`·`-hits` conformance 는 통째로 바뀐다 — 줄 수만 적는다.
  - 기대(게이트 아님): pathtraver 재현율 0 → 90 안팎 · cmdi 26 → 80 안팎 · sqli FP 206 → 50 아래 · xss 0 → 80 안팎 · Juliet CWE23·36·601·113 서블릿 재현율 90 안팎 · CWE78·89 정밀도 50 → 65 안팎(B2G FP 남음) · CWE259·321 재현율 0 → 80 안팎 · CWE327 FP 34 그대로(5-16 의도).
  - 기대 밖이면 **고치지 않고** 이력에 「기대 밖 — 원인 추정」 한 줄(사다리: 원인이 §3 의 집합 하나(출처·정화·싱크 이름)면 그 상수에 더하고 다시 — 둘까지. 넘으면 그대로 둔다).
- 검증: 네 시험 초록(갱신 뒤 다시 돌려 diff 0) · `bash scripts/verify.sh --corpus` 는 V-38 에서 한 번.
- 의존: 5-52b.

### V-38a — 등급 A 승격(자바)
- 대상: `LegacyCorpusTest.java:55` · `SqlFixtureCorpusTest.java:107` · `golden/corpus/legacy-a.txt`·`sqlfixture-a.txt` 삭제
- 변경: `CorpusFiles.conformance("legacy-a", aCandidates)` → `CorpusFiles.none("레거시 코드 검사·분석(파일 " + total + ")", aCandidates, total)`(`total` 은 바로 아래 단언이 쓰는 값) · `conformance("sqlfixture-a", a)` → `CorpusFiles.none("sqlfluff 표 추출(파일 " + all[0] + ")", a, all[0])`(`all[0]` 이 파일 수 — 바로 아래 `assertTrue(all[0] > 1000, …)` 과 같은 값). `golden.put("_a", a.size())` 는 남긴다(json 열 유지). 주석 「설계 41 §3-1 — R1 은 얼린다」 → 「설계 42 §3-10 — 등급 A 게이트」. 빈 골든 둘 `git rm`.
- 검증: `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='LegacyCorpusTest,SqlFixtureCorpusTest' -Djunit.timeout=10m` 초록.
- 의존: 5-53.

### V-38b — 노드 SKIP 해제(조건부 — 실패 사다리)
- 대상: `scripts/puppeteer/corpus-common.js:7-9` · `golden/corpus/{jspfmt-b.txt, jspfmt-picked.json, strip-*.txt, strip-picked.json, js-*.txt, js-summary.json}` · PLAN 4장 219줄 마지막 문장
- 변경:
  - SKIP 에서 `'jeecg', 'jeesite', 'zheng', 'ruoyi-classic'` 를 빼고 주석을 「설계 41 R1 표본 중 benchmark·juliet·sqlfluff-dialects 는 프로젝트가 아니라(채점 정답지) 노드 스크립트가 안 걷는다 — 레거시 넷은 설계 42 V-38b 가 들였다」로.
  - `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='JspFmtCorpusTest,StripCorpusTest,JsCorpusTest' -Djunit.timeout=10m` — 셋의 벽시계를 적는다.
  - **사다리**(순서대로, 해당하면 멈춘다): ① A 0 → B baseline·conformance·picked 골든을 `-Dgolden.update=true` 로 갱신, 새로 든 B 항목 수와 2% 한도 대비를 이력에 ② A 1~10 이고 원인이 백엔드(`web/`·`core/text`·확장 JS `tools/*_ext.js`) → 고친다(규칙 「번들당 A 10」) ③ A 가 순수본 JS(`pure/`) 결함이거나 11 이상 → 그 표본만 SKIP 에 되돌리고(「`'jeecg', // V-38b — jsp_formatter A n, 순수본 결함 — portfolio 인계」) 이력 + 「사람이 할 것 ④」 에 한 줄 ④ 시험 하나가 10분을 넘으면 가장 큰 표본(jeecg 1,199 파일)만 SKIP 에 되돌리고 이력에 시간.
  - PLAN 4장 219줄 끝 「새 표본은 `corpus-common.js` SKIP 에 둔다 — R3 가 A 로 승격할 때 뺀다.」 → 「레거시 넷은 노드 시험에도 든다(V-38b); benchmark·juliet·sqlfluff-dialects 는 채점 정답지라 SKIP 에 남는다.」(사다리 ③·④ 로 남긴 표본이 있으면 그 이름도).
- 검증: 셋 초록 · 골든 diff 가 이력과 맞음.
- 의존: V-38a.

### V-38 — 재측정 · compare ⑧ · 전체 corpus 검증
- 대상: `design/compare-2026-10-28.md`(끝에 ⑧) · `PROGRESS.md`
- 변경: §3-11 대로 ⑧ 절 — 표본 수치 표 전·후(⑦ 과 같은 열) · Benchmark 범주 11 P/R 전·후 · Juliet CWE 15 P/R(+서블릿 재현율) 전·후 · 레거시 넷 아홉 규칙 건수 전·후 · 「드러난 것」(기대 밖 수치, 오탐 꼴, 새 후보). 후보 표에서 올라간 넷은 ⑧ 머리에 「번들 58 로 — 나-1·나-2·나-12·securecookie(나-7 일부)」. `bash scripts/doc-lint.sh design/compare-2026-10-28.md`.
- 검증: `bash scripts/verify.sh --corpus` 초록(corpus 도장 full) · `bash scripts/verify.sh` 초록.
- 의존: V-38b.

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰(`cavecrew-reviewer`) → `gate-probe.sh` → `verify.sh`(끝 줄이 알리는 레인 — corpus 는 V-38 에서 돌았으면 도장이 산다; db 는 pom·db 경로가 안 바뀌어 불필요) → `git push -u origin work/2026-10-29` → `gh pr create --base main`(본문: 행마다 시작 세 줄 표 + 「검증」 + ⑧ 전·후 핵심 수치 다섯 줄) → CI 폴링 → claude[bot] 처분 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → 끝 보고. PR 본문 끝 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## 5. 금지 사항
- `pure/` 수정 금지(동결) · portfolio 저장소 수정 금지(인계만) · `pure-rules.json` 이 바뀌면 설계가 깨진 것 — §7
- `security.sqlConcat`·`sqlDollar`·`exceptionMessage` 등 아홉 밖 정규식의 `regex`·`globs`·`severity`·`message` 변경 금지. 아홉도 `id`·`group`·`severity`·`globs`·`regex`·`flags`·`skipComments`·`message` 는 그대로(전부 `pure-rules.json` 열) — 더하는 것은 `params.java` 한 줄만
- `CorpusFiles`·`GoldenFiles`·`verify.sh`·훅·CI 수정 금지(검증 인프라 — 사용자 결정). 시험을 고쳐 통과시키기 금지 — 골든 갱신은 diff 를 읽고 이력에 적은 뒤에만
- 새 Maven 의존성 금지(JavaParser 3.26.3 만). 심볼 해석기(`javaparser-symbol-solver`) 추가 금지
- 메서드 밖 추적(필드·호출 그래프·`JavaGraph` 연결) 금지 — 이 번들은 메서드 안
- `core/` 가 `web`·`cli` 를 import 금지(`ArchitectureTest`). `Taint` 는 `core.check` 안
- 발췌·로그에 출처 값·변수 값을 더 싣지 않는다(규칙 3)
- 화면(`tools/`)·순수본 JS·`docs/` 사용자 문서 변경 금지(규칙 수가 바뀌어도 화면은 YAML 을 읽는다)
- `corpus/MANIFEST`·`SOURCES.md`·`corpus-fetch.sh` 변경 금지(새 표본 없음 — db 레인을 안 건드린다)
- Juliet 을 MANIFEST 에 넣기·`assumeTrue` 제거 금지(선택 표본 — 설계 41 결정)
- 스타일 리팩터·이름 바꾸기 금지(`JavaRules` 기존 메서드 안 건드린다 — `apply` 에 한 줄, 필드·생성자 한 줄씩)

## 6. 최종 검증
- `bash scripts/verify.sh` 초록 + `bash scripts/verify.sh --corpus` 초록(corpus 도장 full) — db 는 LANE_DB 경로가 안 바뀌었으면 불필요(끝 줄이 알린다)
- `bash scripts/mvn.sh -q pmd:check` 0
- 골든: `check/fixtures.json` 의 `security/` 기존 줄 diff 0(+ 새 규칙 여섯 줄만 추가) · `check/pure-rules.json` diff 0 · `check/java.json` PosTaint 추가 · `self-check.txt` 표식 둘 감소 · corpus 골든 전·후 수치가 PROGRESS 이력과 compare ⑧ 에 일치
- 규칙 수 111(`CheckRulesTest`) · 정규식 63(`PureRulesJsonTest`) · `PureCodeCheckTest` 초록(corpus 레인 — 순수본 ↔ 백엔드 동기)
- `legacy-a.txt`·`sqlfixture-a.txt` 없음, 두 시험이 `none` 으로 초록
- `corpus-common.js` SKIP 에 레거시 넷 없음(사다리로 남긴 것은 주석과 이력에)
- `gate-probe.sh` 10/10
- 수동: `run.bat` 뒤 코드 검사 붙여넣기 탭에 `String p = request.getParameter("f"); new java.io.FileInputStream(p);` → `security.pathTraversal` 한 건, `String p = "a"; new java.io.FileInputStream(p);` → 0

## 7. 중단 조건
다음이면 임의로 해결하지 말고 멈추고 보고(어느 스텝 · 무엇이 예상과 달랐나 · 선택지):
- 5-51b 에서 `check/fixtures.json` 의 `security/` 기존 줄이 바뀐다(오염·상수 판정이 정규식 줄을 안 잡거나 더 잡는다) — 순수본 동기가 깨지는 신호. 어느 줄·어느 규칙인지 적고 멈춘다
- `pure-rules.json` 이 바뀌거나 `PureRulesJsonTest` 가 63 이 아니다
- `PureCodeCheckTest` 가 빨강(순수본 ≠ 백엔드) — 새로 더한 pos·neg 줄이 순수본 정규식에 걸린 것. 그 줄을 적고 멈춘다
- 플랜의 구조 설명(§2 의 줄 번호·메서드 이름)이 실제 코드와 다르다
- 검증이 2회 연속 실패하고 원인이 플랜 범위 밖(JavaParser 가 표본 파일을 못 읽는다 등)
- 스텝에 없는 파일을 3개 이상 고쳐야 한다
- 5-53 재채점에서 등급 A(parseError·예외)가 새로 생긴다 — `Taint` 가 던진 예외다. 스택을 적고 멈춘다
- V-38b 사다리 네 칸을 다 써도 셋이 초록이 안 된다
- 시험 하나가 10분을 넘는다(5-53 네 시험 포함)

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① 재채점 수치(5-53 「기대」는 추정) ② 노드 셋에 레거시를 들였을 때 A 수와 벽시계(V-38b 사다리가 받는다) ③ egov·commons·jspwiki·roller 골든에서 아홉 규칙 건수가 얼마나 바뀌나(diff 로 보고 이력에) ④ `PosTaint.java` 가 `java.declarationOrder`·`metric.*` 에 안 걸리는지(`JavaRulesTest` 가 바로 말한다 — 걸리면 픽스처를 고친다, 규칙이 아니라). 첫 스텝 전 확인할 명령: `bash scripts/verify.sh`(현재 트리 초록, 도장 있음) · `ls C:/workspace/toolbox-corpus | grep -c .`(표본 폴더 있음 — benchmark·juliet 포함) · `node -v`.
- **사용자만 정할 수 있는 것** — 없음(축·A 승격·머지 위임은 2026-10-29 에 받았다).
