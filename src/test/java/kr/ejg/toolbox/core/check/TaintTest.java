package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.javaparser.ast.CompilationUnit;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 5-51a(설계 42 §3-2~6) — 오염 추적·상수 전파 엔진. 규칙 id·줄만 본다 */
class TaintTest {

    static final String PT2 = "2 pathTraversal";
    static final String PT3 = "3 pathTraversal";
    static final String PREFIX = "security.";
    static final String XSS4 = "4 xssServlet";
    static final String XSS3 = "3 xssServlet";
    static final String JAVA = "java";
    static final String SECURITY = "security";
    static final String FILE_K = "new java.io.File(this.k);";
    static final String P = "String p = request.getParameter(\"p\");\n";
    static final String OUT = "response.getWriter().print(";
    static final Set<String> ALL = Set.of("security.osCommand", "security.pathTraversal", "security.openRedirect",
            "security.responseSplitting", "security.ssrf", "security.sqlInjection", "security.xssServlet", "security.ldapInjection",
            "security.xpathInjection", "security.trustBoundary", "security.cookieSecureFlag", "security.hardcodedPassword",
            "security.hardcodedKey", "security.weakHash", "security.weakCipher", "security.exceptionMessage");
    /** 내장 정의(정규식 — 상수 판정이 재사용) + 아직 YAML 에 없는 새 id 는 빈 정의 */
    static final Map<String, Rule.Def> DEFS = new HashMap<>();

    static {
        Map<String, Rule.Def> builtin = new HashMap<>();
        RuleSet.builtin().defs().forEach(d -> builtin.put(d.id(), d));
        for (String id : ALL) {
            DEFS.put(id, builtin.getOrDefault(id, new Rule.Def(id, SECURITY, "error", JAVA, List.of("*.java"), null, null, null,
                    id, null, true)));
        }
    }

    static List<String> hits(String body) {
        return hitsIn("class A { String adminPath; void f(javax.servlet.http.HttpServletRequest request, "
                + "javax.servlet.http.HttpServletResponse response, java.util.Map<String, String> map, boolean cond) throws Exception {\n"
                + body + "\n} }");
    }

    static List<String> hitsIn(String code) {
        CompilationUnit cu = new JavaSource().parse(code).getResult().orElseThrow();
        return new Taint(DEFS).scan(cu).stream()
                .map(h -> h.node().getBegin().map(p -> p.line).orElse(0) + " " + h.rule().replace(PREFIX, "")).toList();
    }

    @Test
    void direct() {
        assertEquals(List.of(PT2), hits("new java.io.File(request.getParameter(\"p\"));"));
    }

    @Test
    void throughVariable() {
        assertEquals(List.of(PT3), hits("String p = request.getHeader(\"x\");\nnew java.io.FileInputStream(p);"));
    }

    @Test
    void cookieArray() {
        assertEquals(List.of("4 pathTraversal"),
                hits("javax.servlet.http.Cookie[] cs = request.getCookies();\nString v = cs[0].getValue();\nnew java.io.File(v);"));
    }

    @Test
    void stringBuilderMutator() {
        assertEquals(List.of("4 osCommand"), hits("StringBuilder sb = new StringBuilder(\"ls \");\nsb.append(request.getParameter(\"d\"));\n"
                + "Runtime.getRuntime().exec(sb.toString());"));
    }

    @Test
    void constantIsClean() {
        assertEquals(List.of(), hits("String p = \"fixed\";\nnew java.io.File(p);"));
    }

    @Test
    void sanitizerCleans() {
        assertEquals(List.of(), hits("int n = Integer.parseInt(request.getParameter(\"n\"));\nnew java.io.File(\"f\" + n);"));
    }

    @Test
    void allowListLookupIsClean() {
        assertEquals(List.of(), hits("String v = map.get(request.getParameter(\"k\"));\nnew java.io.File(v);"));
    }

    @Test
    void controllerParameterIsSource() {
        assertEquals(List.of(PT3), hitsIn("@Controller class C {\n@GetMapping public String f(String name, Model m) {\n"
                + "new java.io.File(name); new java.io.File(m.toString()); return \"v\"; } }"));
    }

    @Test
    void plainMethodParameterIsNotSource() {
        assertEquals(List.of(), hitsIn("class S { void f(String name) { new java.io.File(name); } }"));
    }

    @Test
    void bindingAnnotationIsSource() {
        assertEquals(List.of("1 osCommand"), hitsIn("class S { void f(@RequestParam String c) { Runtime.getRuntime().exec(c); } }"));
    }

    @Test
    void conditionalEitherBranch() {
        assertEquals(List.of(PT3), hits("String bar = cond ? \"safe\" : request.getParameter(\"p\");\nnew java.io.File(bar);"));
    }

    @Test
    void branchAssignmentIsFlowInsensitive() {
        assertEquals(List.of(PT3),
                hits("String bar;\nif (cond) bar = \"a\"; else bar = request.getParameter(\"p\"); new java.io.File(bar);"));
    }

    @Test
    void laterAssignmentStillTaints() {
        assertEquals(List.of(PT3), hits("String a = \"x\";\nnew java.io.File(a);\na = request.getParameter(\"p\");"));
    }

    @Test
    void writerVariableAndDirect() {
        assertEquals(List.of(XSS3, XSS4), hits("java.io.PrintWriter w = response.getWriter();\n"
                + "w.print(request.getParameter(\"m\"));\nresponse.getWriter().format(\"%s\", request.getParameter(\"m\"));\nw.print(\"ok\");"));
    }

    @Test
    void sendErrorMessage() {
        assertEquals(List.of("2 xssServlet"), hits("response.sendError(404, \"no \" + request.getParameter(\"q\"));"));
    }

    @Test
    void sessionAttribute() {
        assertEquals(List.of("2 trustBoundary"), hits("request.getSession().setAttribute(\"u\", request.getParameter(\"u\"));"));
    }

    @Test
    void redirectConcat() {
        assertEquals(List.of("1 openRedirect"),
                hitsIn("class C { String f(@RequestParam String next) { return \"redirect:\" + next; } String g() { return \"redirect:\" + adminPath; } }"));
    }

    @Test
    void sqlSinksAndScope() {
        assertEquals(List.of("3 sqlInjection"), hits("String p = request.getParameter(\"p\");\n"
                + "stmt.executeQuery(\"SELECT * FROM t WHERE a='\" + p + \"'\");\njdbcTemplate.update(\"UPDATE t SET a=?\", p);\ndao.update(p);"));
    }

    /** 범주마다 — 자기 싱크는 건너뛰고 다른 범주 싱크는 지적(5-55d) */
    @Test
    void eachFamilyCleansOnlyItsSink() {
        String stmt = "java.sql.Statement stmt = null;\n";
        assertEquals(List.of("6 osCommand"), hits(P + "String q = ESAPI.encoder().encodeForSQL(codec, p);\n" + stmt
                + "stmt.executeQuery(q);\nRuntime.getRuntime().exec(q);"));
        assertEquals(List.of("5 pathTraversal"), hits(P + "String l = ESAPI.encoder().encodeForLDAP(p);\n"
                + "ctx.search(\"ou=people\", l, sc);\nnew java.io.File(l);"));
        assertEquals(List.of("6 sqlInjection"), hits(P + "String x = ESAPI.encoder().encodeForXPath(p);\n" + stmt
                + "xpath.evaluate(x, doc);\nstmt.executeQuery(x);"));
        assertEquals(List.of("5 ldapInjection"), hits(P + "String o = ESAPI.encoder().encodeForOS(codec, p);\n"
                + "Runtime.getRuntime().exec(o);\nctx.search(\"ou=people\", o, sc);"));
        assertEquals(List.of("5 osCommand"), hits(P + "String n = FilenameUtils.normalize(p);\n"
                + "new java.io.File(n);\nRuntime.getRuntime().exec(n);"));
    }

    /** 설계 45 §3-1~3 — KerberosKey · 다른 메서드가 채운 필드 · 매개변수 인자(링크 없으면 버림) */
    @Test
    void constantsAcrossMethodsWithoutLink() {
        assertEquals(List.of("2 hardcodedPassword"), hits("new javax.security.auth.kerberos.KerberosKey(principal, \"7e5tc4s3\".toCharArray(), null);"));
        assertEquals(List.of("3 hardcodedPassword"), hitsIn("class A { String pw;\nvoid a() { pw = \"x1\"; }\n"
                + "void b() throws Exception { java.sql.DriverManager.getConnection(u, s, pw); } }"));
        assertEquals(List.of(), hitsIn("class A { void c(String pw) throws Exception { java.sql.DriverManager.getConnection(u, s, pw); } }"));
    }

    /** 설계 45 §3-6 — 예외 출처: 메시지·스택·예외 자체가 응답에 닿는다. 로그·예외 아닌 getMessage 는 아니다 */
    @Test
    void exceptionReachesResponse() {
        String c = "try { x(); } catch (Exception e) {\n";
        String ex3 = "3 exceptionMessage";
        assertEquals(List.of(ex3), hits(c + "response.getWriter().println(e.getMessage()); }"));
        assertEquals(List.of(ex3), hits(c + "e.printStackTrace(response.getWriter()); }"));
        assertEquals(List.of(), hits(c + "e.printStackTrace(); }"));
        assertEquals(List.of("4 exceptionMessage"), hits(c + "String m = e.toString();\nresponse.sendError(500, m); }"));
        assertEquals(List.of(ex3), hits(c + "model.addAttribute(\"err\", e.getMessage()); }"));
        assertEquals(List.of(), hits(c + "log.error(\"x\", e.getMessage());\nresponse.getWriter().print(\"fail\"); }"));
        assertEquals(List.of(), hits("Result result = svc.run();\nresponse.getWriter().print(result.getMessage());"));
    }

    /** 설계 45 §3-6 — 예외 실행은 따로: JSON 응답에도 · 요청 값은 xss 로만 · 예외 타입 매개변수도 씨앗 */
    @Test
    void exceptionRunIsSeparate() {
        assertEquals(List.of("4 exceptionMessage"), hits("response.setContentType(\"application/json\");\n"
                + "try { x(); } catch (Exception e) {\nresponse.getWriter().write(\"e=\" + e.getMessage()); }"));
        assertEquals(List.of(XSS3, "4 exceptionMessage"), hits(P + OUT + "p);\ntry { x(); } catch (Exception e) { " + OUT + "e); }"));
        assertEquals(List.of("1 exceptionMessage"), hitsIn("class A { void h(Exception ex, javax.servlet.http.HttpServletResponse response) "
                + "throws Exception { response.getWriter().print(ex.getMessage()); } }"));
    }

    /** 설계 45 §3-7 — 숫자·날짜 반환은 깨끗(roller 달력), 맵 조회·String.format 은 그대로 */
    @Test
    void numericReturnsAreClean() {
        assertEquals(List.of(), hits(P + "Calendar cal = model(p).getCalendar();\n" + OUT + "cal.get(Calendar.DAY_OF_MONTH));"));
        assertEquals(List.of(), hits(P + "java.text.SimpleDateFormat formatTitle = new java.text.SimpleDateFormat(\"yyyy\");\n"
                + OUT + "formatTitle.format(p));"));
        assertEquals(List.of(), hits(P + "java.util.List<String> list = java.util.List.of(p);\n" + OUT + "list.size());"));
        assertEquals(List.of("4 xssServlet"), hits(P + "java.util.Map<String, String> m = java.util.Map.of(\"a\", p);\n" + OUT + "m.get(\"a\"));"));
        assertEquals(List.of(XSS3), hits(P + OUT + "String.format(\"%s\", p));"));
        assertEquals(List.of(XSS3), hits(P + OUT + "java.text.MessageFormat.format(\"{0}\", p));"));
    }

    /** 규칙이 받는 범주마다 정화 이름이 있다 — 범주 글자가 어긋나면 조용히 안 씻는다(5-55d) */
    @Test
    void everyAcceptedFamilyHasSanitizer() {
        assertTrue(Sanitizers.registered().containsAll(Sanitizers.ACCEPTS.values()), Sanitizers.ACCEPTS.toString());
    }

    @Test
    void ldapAndXpath() {
        assertEquals(List.of("3 ldapInjection", "4 xpathInjection"), hits("String f = request.getParameter(\"f\");\n"
                + "ctx.search(\"ou=people\", f, sc);\nxpath.evaluate(f, doc);\nctx.search(\"ou=people\", \"(uid=admin)\", sc);"));
    }

    @Test
    void cookieFlags() {
        assertEquals(List.of("2 cookieSecureFlag"), hits("c.setSecure(false);\nc.setSecure(true);\nc.setHttpOnly(Boolean.TRUE);"));
    }

    @Test
    void headerAndCookieValue() {
        assertEquals(List.of("3 responseSplitting"),
                hits("String lang = request.getParameter(\"l\");\nresponse.addCookie(new javax.servlet.http.Cookie(\"lang\", lang));"));
    }

    @Test
    void lambdaBodyIsSkipped() {
        assertEquals(List.of(), hits("Runnable r = () -> new java.io.File(request.getParameter(\"p\"));"));
    }

    @Test
    void pathsGetNeedsPathsScope() {
        assertEquals(List.of(PT2), hits("java.nio.file.Paths.get(\"/a\", request.getParameter(\"p\"));\nlist.get(request.getParameter(\"i\"));"));
    }

    @Test
    void namedPasswordReusesRegex() {
        assertEquals(List.of("2 hardcodedPassword", "3 hardcodedPassword"),
                hits("String pwd = \"s3cret\";\nString dbPassword = \"a\" + \"b\";\nString name = \"x\";\nString pw2 = request.getParameter(\"p\");"));
    }

    @Test
    void passwordSinkArgument() {
        assertEquals(List.of("3 hardcodedPassword"), hitsIn("class D { void f(String url, String pw) throws Exception {\nString data = \"7e5tc4s3\";\n"
                + "java.sql.DriverManager.getConnection(url, \"root\", data);\njava.sql.DriverManager.getConnection(url, \"root\", pw); } }"));
    }

    @Test
    void fieldConstantKey() {
        assertEquals(List.of("2 hardcodedPassword", "3 hardcodedKey", "4 hardcodedKey"),
                hitsIn("class K {\nprivate static final String PASSWORD = \"p@ss\"; static final String KEY = \"0123456789abcdef\";\n"
                        + "void f() { new javax.crypto.spec.SecretKeySpec(KEY.getBytes(), \"AES\");\n"
                        + "new javax.crypto.spec.IvParameterSpec(new byte[] {1, 2, 3}); new javax.crypto.spec.SecretKeySpec(load(), \"AES\"); } }"));
    }

    @Test
    void algorithmThroughVariableAndDefault() {
        assertEquals(List.of("3 weakHash", "4 weakCipher", "6 weakCipher"),
                hits("String a = \"MD5\";\njava.security.MessageDigest.getInstance(a);\n"
                        + "javax.crypto.Cipher.getInstance(props.getProperty(\"alg\", \"DESede/ECB/PKCS5Padding\"));\n"
                        + "javax.crypto.Cipher.getInstance(\"AES/GCM/NoPadding\");\njavax.crypto.KeyGenerator.getInstance(\"DES\");\n"
                        + "java.security.MessageDigest.getInstance(props.getProperty(\"h\", \"SHA512\"));"));
    }

    @Test
    void keyGeneratorHasNoMode() {
        assertEquals(List.of("3 weakCipher"),
                hits("javax.crypto.KeyGenerator.getInstance(\"AES\");\njavax.crypto.KeyGenerator.getInstance(\"DESede\");"));
    }

    @Test
    void modelessAesIsEcb() {
        assertEquals(List.of("2 weakCipher"), hits("javax.crypto.Cipher.getInstance(\"AES\");\njavax.crypto.Cipher.getInstance(\"AES/CBC/PKCS5Padding\");"));
    }

    /** 5-51b — RuleSet 배선(하이브리드)을 거쳐 java 픽스처 둘. security 묶음만 켠다(JavaRulesTest 는 security 를 끈다) */
    @Test
    void hybridThroughRuleSet(@TempDir Path data) throws Exception {
        Map<String, Boolean> groups = new HashMap<>(JavaRulesTest.ONLY_JAVA);
        groups.put(JAVA, false);
        groups.put("metric", false);
        groups.put(SECURITY, true);
        Profile p = new Profile("t", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null, null,
                new Profile.CodeCheck(groups, null, null), "spring", null, null, null);
        RuleSet.Run run = RuleSet.load(p, null).run();
        List<String> got = new ArrayList<>();
        for (Source s : CheckRulesTest.fixtureSources(CheckRulesTest.FIXTURES.resolve("java"), data)) {
            if (s.fileName().contains("Taint")) {
                run.apply(s).forEach(f -> got.add(f.file() + ":" + f.line() + " " + f.rule().replace(PREFIX, "")));
            }
        }
        // 설계 44 §3-7 — 설정 값 알고리즘(PosTaint.cipher 의 getProperty)은 검사 끝에 판정한다
        run.finish().forEach(f -> got.add(f.file() + ":" + f.line() + " " + f.rule().replace(PREFIX, "")));
        got.sort(java.util.Comparator.comparingInt(x -> Integer.parseInt(x.replaceAll("^[^:]+:(\\d+) .*$", "$1"))));
        assertEquals(List.of("PosTaint.java:10 pathTraversal", "PosTaint.java:16 osCommand", "PosTaint.java:21 ssrf",
                "PosTaint.java:26 openRedirect", "PosTaint.java:31 responseSplitting", "PosTaint.java:37 weakHash",
                "PosTaint.java:42 weakCipher", "PosTaint.java:47 hardcodedPassword", "PosTaint.java:52 hardcodedKey"), got);
    }

    /** 5-54c(설계 43) — 메서드 사이 픽스처: 컨트롤러 → 인터페이스 → 구현 · 정적 필드 · 같은 클래스 도우미. Neg* 는 상수·정화 값만 */
    @Test
    void linkThroughRuleSet(@TempDir Path data) throws Exception {
        RuleSet.Run run = TaintLinkTest.rules().run();
        List<Finding> found = new ArrayList<>();
        for (Source s : CheckRulesTest.fixtureSources(CheckRulesTest.FIXTURES.resolve("java/link"), data)) {
            found.addAll(run.apply(s));
        }
        found.addAll(run.finish());
        assertEquals(List.of("PosLinkController.java:17 pathTraversal", "PosLinkController.java:22 exceptionMessage",
                "PosLinkController.java:23 exceptionMessage", "PosLinkServiceImpl.java:14 sqlInjection",
                "PosLinkServiceImpl.java:18 osCommand", "PosLinkServiceImpl.java:22 weakCipher", "PosLinkServiceImpl.java:26 hardcodedPassword",
                "PosLinkServiceImpl.java:27 hardcodedKey", "PosLinkServiceImpl.java:32 hardcodedPassword",
                "PosLinkServiceImpl.java:8 pathTraversal"),
                TaintLinkTest.labels(found));
    }

    /** 못 읽은 .java — 하이브리드 규칙은 정규식으로 되돌린다 */
    @Test
    void unparsableFallsBackToRegex() {
        RuleSet rules = RuleSet.builtin();
        List<String> got = CheckRunner.runText("class A { void f( { new java.io.File(request.getParameter(\"p\")); }", JAVA, rules)
                .findings().stream().map(Finding::rule).filter(r -> r.startsWith(PREFIX)).toList();
        assertEquals(List.of("security.pathTraversal"), got);
    }

    /** 마무리 리뷰 — 스코프 부분 일치·스코프 없는 흔한 이름이 서비스 호출을 싱크로 봤다 */
    @Test
    void serviceCallsAreNotSinks() {
        assertEquals(List.of(), hitsIn("class C { void f(@RequestBody Item item, @RequestParam String q) {\n"
                + "itemService.update(item); memberService.query(q); boardService.create(item); redirectHelper.search(q);\n"
                + "orderService.command(q); cache.resolve(q); rateService.exchange(q); } }"));
    }

    @Test
    void scopeLastNameMatches() {
        assertEquals(List.of("3 sqlInjection", "4 xpathInjection", "5 ssrf"), hits("String q = request.getParameter(\"q\");\n"
                + "this.jdbcTemplate.update(q);\nxp.compile(q);\njava.net.URI.create(q);"));
    }

    @Test
    void numberIsNotPassword() {
        assertEquals(List.of(), hitsIn("class K { private static final int PASSWORD_LENGTH = 8; char PWD_CHAR = 'x'; }"));
    }

    @Test
    void fileStreamIsNotWriter() {
        assertEquals(List.of(), hits("java.io.FileOutputStream os = new java.io.FileOutputStream(\"/tmp/a\");\n"
                + "java.io.InputStream in = request.getInputStream(); byte[] buf = new byte[8]; int n;\n"
                + "while ((n = in.read(buf)) != -1) { os.write(buf, 0, n); }"));
    }

    @Test
    void parameterShadowsFieldConstant() {
        assertEquals(List.of("1 hardcodedPassword"), hitsIn("class D { private String password = \"changeit\";\n"
                + "void f(String url, String password) throws Exception { java.sql.DriverManager.getConnection(url, \"u\", password); } }"));
    }

    @Test
    void localConstantDoesNotLeakToNextMethod() {
        assertEquals(List.of("2 weakHash"), hitsIn("class H { static final String A = \"SHA-256\";\nvoid f() { String a = A; a = \"MD5\"; java.security.MessageDigest.getInstance(a); }\n"
                + "void g() { java.security.MessageDigest.getInstance(A); } }"));
    }

    @Test
    void modelAndViewRedirect() {
        assertEquals(List.of("1 openRedirect"), hitsIn("class C { Object f(@RequestParam String next) { return new ModelAndView(\"redirect:\" + next); } }"));
    }

    @Test
    void hybridOnlyForKnownJavaRules() {
        Rule.Def known = DEFS.get("security.pathTraversal");
        Rule.Def custom = new Rule.Def("custom.x", SECURITY, "warn", "regex", List.of("*.java"), "x", null, null, "x",
                Map.of(JAVA, "taint"), true);
        Rule.Def jspOnly = new Rule.Def(known.id(), SECURITY, "error", "regex", List.of("*.jsp"), known.regex(), null, null, "x",
                known.params(), true);
        assertTrue(RuleSet.hybrid(known));
        assertFalse(RuleSet.hybrid(custom));
        assertFalse(RuleSet.hybrid(jspOnly));
    }

    @Test
    void wantsOnlyTaintRules() {
        assertTrue(Taint.wants(Set.of("security.ssrf")));
        assertTrue(Taint.wants(Set.of("security.weakHash")));
        assertFalse(Taint.wants(Set.of("java.unusedImport")));
    }

    // ---------------------------------------------------------------- 5-54a(설계 43 §3-1·6·9)

    static Taint.Body bodyOf(String code) {
        CompilationUnit cu = new JavaSource().parse(code).getResult().orElseThrow();
        return Taint.body(cu.findFirst(com.github.javaparser.ast.body.CallableDeclaration.class).orElseThrow());
    }

    static List<String> sinks(Taint.Facts f) {
        return f.sinks().stream().map(h -> h.node().getBegin().map(p -> p.line).orElse(0) + " " + h.rule().replace(PREFIX, ""))
                .toList();
    }

    @Test
    void parameterNamesIsSource() {
        assertEquals(List.of(PT2), hits("java.util.Enumeration<String> names = request.getParameterNames(); "
                + "String n = (String) names.nextElement(); new java.io.File(n);"));
    }

    @Test
    void arrayElementAssignment() {
        assertEquals(List.of(PT2), hits("String[] a = new String[3]; a[1] = request.getParameter(\"p\"); "
                + "new java.io.File(a[1]);"));
    }

    @Test
    void objectFieldAssignment() {
        assertEquals(List.of(PT2), hits("Box b = new Box(); b.v = request.getParameter(\"p\"); new java.io.File(b.v);"));
    }

    @Test
    void thisFieldWithinMethod() {
        assertEquals(List.of(PT2), hits("this.k = request.getParameter(\"p\"); " + FILE_K));
    }

    @Test
    void seedRunReachesSinkAndField() {
        Taint.Body b = bodyOf("class A { String k;\nString f(String p) {\nnew java.io.File(p);\nthis.k = p;\nreturn p; } }");
        Taint.Facts f = new Taint(DEFS).run(b, Set.of("p"), false);
        assertEquals(List.of(PT3), sinks(f));
        assertTrue(f.tainted().contains("this.k"));
    }

    @Test
    void seedRunWithoutSources() {
        Taint.Body b = bodyOf("class A {\nvoid f(javax.servlet.http.HttpServletRequest request) {\n"
                + "new java.io.File(request.getParameter(\"x\")); } }");
        assertEquals(List.of(), sinks(new Taint(DEFS).run(b, Set.of(), false)));
        assertEquals(List.of(PT3), sinks(new Taint(DEFS).run(b, Set.of(), true)));
    }

    @Test
    void fieldKeySeeds() {
        Taint t = new Taint(DEFS);
        assertEquals(List.of(PT2), sinks(t.run(bodyOf("class A { String k;\nvoid h() { " + FILE_K + " } }"),
                Set.of("this.k"), false)));
        assertEquals(List.of(PT2), sinks(t.run(bodyOf("class A {\nvoid h() { new java.io.File(H.data); } }"),
                Set.of("H.data"), false)));
        assertEquals(List.of(), sinks(t.run(bodyOf("class A { String k;\nvoid h() { " + FILE_K + " } }"), Set.of(), false)));
    }

    @Test
    void bodyKnowsClassFieldsAndLocals() {
        Taint.Body b = bodyOf("class A { String k; int n;\nvoid f(String p) { String q = p; } }");
        assertEquals("A", b.cls());
        assertEquals(Set.of("k", "n"), b.fields());
        assertEquals(Set.of("p", "q"), b.locals());
    }

    // ---------------------------------------------------------------- 5-55a(설계 44 §3-1·2) 정화 범주

    @Test
    void xssEncodedValueStillReachesSql() {
        assertEquals(List.of("5 sqlInjection"), hits(P + "String s = org.owasp.esapi.ESAPI.encoder().encodeForHTML(p);\n" + OUT + "s);\n"
                + "java.sql.Statement stmt = null; stmt.executeQuery(s);"));
    }

    @Test
    void urlEncoderCleansHeaderOnly() {
        assertEquals(List.of("5 openRedirect"), hits(P + "String u = java.net.URLEncoder.encode(p, \"UTF-8\");\n"
                + "response.setHeader(\"L\", u);\nresponse.sendRedirect(u);"));
    }

    @Test
    void htmlEscapeDoesNotCleanSession() {
        assertEquals(List.of("4 trustBoundary"), hits(P + "String h = org.apache.commons.lang.StringEscapeUtils.escapeHtml(p);\n"
                + "request.getSession().setAttribute(\"a\", h);"));
    }

    @Test
    void moreXssEncoders() {
        assertEquals(List.of(), hits(P + OUT + "Utilities.escapeHTML(p));\n" + OUT + "StringEscapeUtils.escapeJson(p));"));
    }

    @Test
    void anyRawAssignmentLosesTheFamily() {
        assertEquals(List.of(XSS4), hits(P + "String x = StringEscapeUtils.escapeHtml(p); x = p;\n" + OUT + "x);"));
    }

    @Test
    void concatKeepsCommonFamily() {
        assertEquals(List.of(XSS4), hits(P + OUT + "\"<b>\" + StringEscapeUtils.escapeHtml(p) + \"</b>\");\n"
                + OUT + "StringEscapeUtils.escapeHtml(p) + p);"));
    }

    @Test
    void bareEncodeIsNotSanitizer() {
        assertEquals(List.of("3 responseSplitting"), hits(P + "response.setHeader(\"L\", encode(p));"));
    }

    @Test
    void inPlaceSanitizePins() {
        assertEquals(List.of(), hits(P + "p = java.net.URLEncoder.encode(p, \"UTF-8\");\nresponse.setHeader(\"L\", \"/a?l=\" + p);"));
    }

    @Test
    void sanitizedArgumentDoesNotTaintCallResult() {
        assertEquals(List.of(), hits(P + OUT + "String.format(\"%s\", StringEscapeUtils.escapeHtml(p)));"));
    }

    // ---------------------------------------------------------------- 5-55b(설계 44 §3-3·4·5) writer · JSON · 스코프 타입 · 별칭

    @Test
    void streamsMadeWithNewAreNotResponse() {
        assertEquals(List.of(), hits(P + "java.io.PrintWriter out = new java.io.PrintWriter(conn.getOutputStream()); out.print(p);\n"
                + "java.io.OutputStream os = new java.io.FileOutputStream(\"f\"); java.io.OutputStream out2 = new java.io.FileOutputStream(\"g\");\n"
                + "java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(os, \"UTF-8\"); writer.append(p);"));
    }

    @Test
    void wrapperOfResponseIsWriter() {
        assertEquals(List.of(XSS4), hits(P + "java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(response.getOutputStream());\n"
                + "w.append(p);"));
    }

    @Test
    void jspWriterParameter() {
        assertEquals(List.of("2 xssServlet"), hitsIn("class T { void f(javax.servlet.jsp.JspWriter w, @RequestParam String p) throws Exception {\n"
                + "w.print(p); } }"));
    }

    @Test
    void jsonResponseIsNotXss() {
        assertEquals(List.of("5 xssServlet"), hits(P + "response.setContentType(\"application/json;charset=UTF-8\");\n"
                + OUT + "p);\nresponse.sendError(500, p);"));
        assertEquals(List.of(), hits(P + "response.setContentType(MediaType.APPLICATION_JSON_VALUE);\n" + OUT + "p);"));
    }

    @Test
    void declaredTypeScopes() {
        assertEquals(List.of("4 ldapInjection", "6 sqlInjection"), hits(P
                + "javax.naming.directory.InitialDirContext idc = new javax.naming.directory.InitialDirContext(env);\n"
                + "idc.search(\"ou=x\", p, sc);\njava.sql.Statement s = c.createStatement();\ns.execute(p);"));
    }

    @Test
    void thisFieldAndBareFieldAreOne() {
        assertEquals(List.of("2 pathTraversal", PT3), hitsIn("class A { String k;\n"
                + "void f(javax.servlet.http.HttpServletRequest request) { this.k = request.getParameter(\"a\"); new java.io.File(k); }\n"
                + "void g(javax.servlet.http.HttpServletRequest request) { k = request.getParameter(\"a\"); new java.io.File(this.k); } }"));
    }

    /** R-9 — 선언은 null, new 는 뒤 대입 */
    @Test
    void streamAssignedLaterIsStillMade() {
        assertEquals(List.of(), hits(P + "java.io.PrintWriter out = null;\nout = new java.io.PrintWriter(conn.getOutputStream());\nout.print(p);"));
    }

    /** 마무리 리뷰 — 겹겹이 감싼 응답 스트림 · 별칭 사이 범주 교집합 · 나가는 요청의 JSON 헤더 · httpResp */
    @Test
    void reviewCases() {
        assertEquals(List.of("5 xssServlet"), hits(P + "java.io.PrintWriter out = new java.io.PrintWriter(new java.io.OutputStreamWriter(\n"
                + "response.getOutputStream(), \"UTF-8\"));\nout.print(p);"));
        assertEquals(List.of(XSS3), hitsIn("class A { String k;\nvoid f(@RequestParam String p) { this.k = StringEscapeUtils.escapeHtml(p); k = p;\n"
                + "javax.servlet.http.HttpServletResponse response = null; " + OUT + "k); } }"));
        assertEquals(List.of("4 xssServlet"), hits(P + "httpPost.setHeader(\"Accept\", \"application/json\");\n" + OUT + "p);"));
        assertEquals(List.of(XSS3), hitsIn("class A {\nvoid f(@RequestParam String p, javax.servlet.http.HttpServletResponse httpResp) throws Exception {\n"
                + "httpResp.getWriter().print(p); } }"));
    }
}
