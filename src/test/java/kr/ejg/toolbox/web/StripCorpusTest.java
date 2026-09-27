package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.sun.source.util.JavacTask;
import io.javalin.Javalin;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * V-2 — 주석 삭제(백엔드본 {@code TB_CMT.strip} = 순수본 {@code cmtStrip}) 를 실물 표본 전부에 돌린다.
 * 앱을 띄우고 {@code scripts/puppeteer/corpus-strip.js} 를 부른 뒤, 원본과 결과를 언어별 불변식으로 잰다(PLAN 4장).
 * <ul>
 *   <li>등급 A(코드 파괴): Java 문법 오류가 새로 생김(javac parse) · XML 파싱 실패 · JSP 스크립틀릿 경계 수 변화 ·
 *       SQL 문자열·주석 밖 {@code ;} 수 변화 · properties·YAML 값이 달라짐 · JS 가 파싱 안 됨 · 스크립트 오류</li>
 *   <li>등급 B(덜 지움): 결과 줄머리에 그 언어 주석 기호가 남음 → {@code golden/corpus/strip-<lang>.txt}</li>
 * </ul>
 * 사용자 코드가 아니라 공개 표본이라 결과를 파일에 쓰지만, 로그·보고는 경로·건수만.
 */
@Tag("corpus")
class StripCorpusTest {

    @TempDir
    static Path tmp;

    static Path out;
    static List<JsonNode> rows;
    static Path corpus;

    @BeforeAll
    static void run() throws Exception {
        CorpusFiles.verify();
        corpus = CorpusFiles.root();
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\n", StandardCharsets.UTF_8);
        Javalin app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
        out = Path.of("target", "corpus-strip").toAbsolutePath();
        try {
            ProcessBuilder pb = new ProcessBuilder("node", "scripts/puppeteer/corpus-strip.js",
                    "http://127.0.0.1:" + app.port(), corpus.toString(), out.toString()).redirectErrorStream(true);
            Process p = pb.start();
            String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, p.waitFor(), "corpus-strip.js 실패: " + log.substring(Math.max(0, log.length() - 500)));
        } finally {
            app.stop();
        }
        JsonNode s = new ObjectMapper().readTree(out.resolve("summary.json").toFile());
        assertEquals(0, s.get("pageErrors").size(), "페이지 오류: " + s.get("pageErrors"));
        rows = new ArrayList<>();
        s.get("files").forEach(rows::add);
    }

    /** 원본 — corpus-strip.js 와 같은 규칙(UTF-8 엄격 → MS949, BOM 뗌). 표본에 EUC-KR 파일이 섞여 있다 */
    static String original(JsonNode r) throws IOException {
        String t = kr.ejg.toolbox.core.text.Csv.decode(Files.readAllBytes(corpus.resolve(r.get("file").asText())));
        return t.startsWith(String.valueOf((char) 0xFEFF)) ? t.substring(1) : t;
    }

    static String stripped(JsonNode r) throws IOException {
        return Files.readString(out.resolve(r.get("file").asText()), StandardCharsets.UTF_8);
    }

    static List<JsonNode> of(String... langs) {
        Set<String> want = Set.of(langs);
        return rows.stream().filter(r -> want.contains(r.get("lang").asText()) && !r.has("error")).toList();
    }

    @Test
    void noScriptErrors() {
        List<String> bad = rows.stream().filter(r -> r.has("error")).map(r -> r.get("file").asText()).toList();
        CorpusFiles.none("주석 삭제 스크립트 오류", bad, rows.size());
    }

    // ---------------------------------------------------------------- 등급 A

    @Test
    void javaStillParses() throws IOException {
        List<JsonNode> js = of("java");
        // EUC-KR 원본은 UTF-8 사본으로 — javac(UTF-8)가 원본 인코딩 오류로 판정에서 빼지 않게
        List<Path> origs = new ArrayList<>();
        for (JsonNode r : js) {
            String f = r.get("file").asText();
            if ("euc-kr".equals(r.path("enc").asText())) {
                Path copy = ORIG_UTF8.resolve(f);
                Files.createDirectories(copy.getParent());
                Files.writeString(copy, original(r), StandardCharsets.UTF_8);
                origs.add(copy);
            } else {
                origs.add(corpus.resolve(f));
            }
        }
        Set<String> before = javaSyntaxErrors(origs);
        Set<String> after = javaSyntaxErrors(js.stream().map(r -> out.resolve(r.get("file").asText())).toList());
        List<String> bad = new ArrayList<>();
        for (JsonNode r : js) {
            String f = r.get("file").asText();
            if (!before.contains(f) && after.contains(f)) {
                bad.add(f);
            }
        }
        CorpusFiles.none("Java 문법이 깨짐", bad, js.size());
    }

    /** javac 파싱만(심볼 해석 없음) — 문법 오류가 난 파일(출처/상대경로) */
    static Set<String> javaSyntaxErrors(List<Path> files) throws IOException {
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diag = new DiagnosticCollector<>();
        Set<String> bad = new HashSet<>();
        try (StandardJavaFileManager fm = jc.getStandardFileManager(diag, Locale.ROOT, StandardCharsets.UTF_8)) {
            for (int i = 0; i < files.size(); i += 500) {
                JavacTask task = (JavacTask) jc.getTask(null, fm, diag, List.of("-proc:none", "-Xlint:none"), null,
                        fm.getJavaFileObjectsFromPaths(files.subList(i, Math.min(files.size(), i + 500))));
                task.parse();
            }
        }
        for (Diagnostic<? extends JavaFileObject> d : diag.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR && d.getSource() != null) {
                bad.add(key(Path.of(d.getSource().toUri())));
            }
        }
        return bad;
    }

    static final Path ORIG_UTF8 = Path.of("target", "corpus-orig-utf8").toAbsolutePath();

    static String key(Path p) {
        Path a = p.toAbsolutePath().normalize();
        Path base = a.startsWith(out) ? out : a.startsWith(ORIG_UTF8) ? ORIG_UTF8 : corpus.toAbsolutePath().normalize();
        return base.relativize(a).toString().replace('\\', '/');
    }

    @Test
    void xmlStillParses() throws Exception {
        List<JsonNode> xs = of("xml", "mybatis");
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setValidating(false);
        f.setExpandEntityReferences(false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); // 외부 통신 0
        List<String> bad = new ArrayList<>();
        for (JsonNode r : xs) {
            if (parses(f, original(r)) && !parses(f, stripped(r))) {
                bad.add(r.get("file").asText());
            }
        }
        CorpusFiles.none("XML 파싱이 깨짐", bad, xs.size());
    }

    static boolean parses(DocumentBuilderFactory f, String text) {
        try {
            var b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            b.parse(new org.xml.sax.InputSource(new StringReader(text)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static final Pattern JSP_COMMENT = Pattern.compile("(?s)<%--.*?--%>");
    private static final Pattern JSP_OPEN = Pattern.compile("<%(?!--)");
    private static final Pattern JSP_CLOSE = Pattern.compile("(?<!--)%>");

    @Test
    void jspScriptletBoundariesKept() throws IOException {
        List<JsonNode> js = of("jsp");
        List<String> bad = new ArrayList<>();
        for (JsonNode r : js) {
            // JSP 주석 <%-- … --%> 안의 <% %> 는 코드가 아니다 — 먼저 떼고 센다(첫 판 오탐 11건: 주석 처리한 taglib 등)
            String a = JSP_COMMENT.matcher(original(r)).replaceAll("");
            String b = JSP_COMMENT.matcher(stripped(r)).replaceAll("");
            if (count(JSP_OPEN, a) != count(JSP_OPEN, b) || count(JSP_CLOSE, a) != count(JSP_CLOSE, b)) {
                bad.add(r.get("file").asText());
            }
        }
        CorpusFiles.none("JSP 스크립틀릿 경계 수가 바뀜", bad, js.size());
    }

    static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    @Test
    void sqlStatementCountKept() throws IOException {
        List<JsonNode> ss = of("sql", "mysql");
        List<String> bad = new ArrayList<>();
        for (JsonNode r : ss) {
            boolean my = r.get("lang").asText().equals("mysql");
            if (semicolons(original(r), my) != semicolons(stripped(r), my)) {
                bad.add(r.get("file").asText());
            }
        }
        CorpusFiles.none("SQL 문장 수(문자열·주석 밖 ;)가 바뀜", bad, ss.size());
    }

    /** 참조 렉서 — '…'('' 이스케이프)·"…"·-- 줄·/* *\/ 블록(·mysql #) 밖의 ; 수. 스캐너와 따로 짠 단순판 */
    static int semicolons(String s, boolean mysql) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' || c == '"') {
                int j = i + 1;
                while (j < s.length()) {
                    if (s.charAt(j) == c) {
                        if (j + 1 < s.length() && s.charAt(j + 1) == c) {
                            j += 2;
                            continue;
                        }
                        break;
                    }
                    if (mysql && s.charAt(j) == '\\') {
                        j++;
                    }
                    j++;
                }
                i = j;
            } else if (c == '-' && i + 1 < s.length() && s.charAt(i + 1) == '-' || mysql && c == '#') {
                int e = s.indexOf('\n', i);
                i = e < 0 ? s.length() : e;
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int e = s.indexOf("*/", i + 2);
                i = e < 0 ? s.length() : e + 1;
            } else if (c == ';') {
                n++;
            }
        }
        return n;
    }

    @Test
    void propertiesValuesKept() throws IOException {
        List<JsonNode> ps = of("props");
        List<String> bad = new ArrayList<>();
        for (JsonNode r : ps) {
            Properties a = new Properties();
            Properties b = new Properties();
            a.load(new StringReader(original(r)));
            b.load(new StringReader(stripped(r)));
            if (!a.equals(b)) {
                bad.add(r.get("file").asText());
            }
        }
        CorpusFiles.none("properties 값이 바뀜", bad, ps.size());
    }

    @Test
    void yamlValuesKept() throws IOException {
        List<JsonNode> ys = of("yaml");
        YAMLMapper y = new YAMLMapper();
        List<String> bad = new ArrayList<>();
        for (JsonNode r : ys) {
            List<JsonNode> a = yamlDocs(y, original(r));
            if (a == null) {
                continue; // 원본이 YAML 로 안 읽히면(템플릿 등) 판정하지 않는다
            }
            if (!a.equals(yamlDocs(y, stripped(r)))) {
                bad.add(r.get("file").asText());
            }
        }
        CorpusFiles.none("YAML 값이 바뀜", bad, ys.size());
    }

    static List<JsonNode> yamlDocs(YAMLMapper y, String text) {
        try (MappingIterator<JsonNode> it = y.readerFor(JsonNode.class).readValues(text)) {
            List<JsonNode> docs = new ArrayList<>();
            while (it.hasNext()) {
                docs.add(it.next());
            }
            return docs;
        } catch (Exception e) {
            return null;
        }
    }

    @Test
    void jsStillParses() {
        List<JsonNode> js = of("js");
        List<String> bad = js.stream().filter(r -> r.get("jsBefore").asBoolean() && !r.get("jsAfter").asBoolean())
                .map(r -> r.get("file").asText()).toList();
        CorpusFiles.none("JS 파싱이 깨짐", bad, js.size());
    }

    // ---------------------------------------------------------------- 등급 B

    /** 결과 줄머리에 남은 주석 기호 — 힌트(/*+ · --+)·MySQL 실행 주석(/*!)·셔뱅은 남기는 것이 맞다 */
    static final Map<String, Pattern> LEFT = new LinkedHashMap<>();

    static {
        Pattern slash = Pattern.compile("^(//|/\\*(?!\\+))");
        LEFT.put("java", slash);
        LEFT.put("js", slash);
        LEFT.put("jsx", slash);
        LEFT.put("less", slash);
        LEFT.put("css", Pattern.compile("^/\\*"));
        // <script> 안 줄머리 「<!--」 는 옛 스크립트 숨기기 관용구(JS 로는 주석 아님) — 같은 줄에 닫힌 HTML 주석만 센다
        LEFT.put("jsp", Pattern.compile("^(<%--|<!--.*-->)"));
        LEFT.put("xml", Pattern.compile("^<!--"));
        LEFT.put("mybatis", Pattern.compile("^(<!--|--(?!\\+)|/\\*(?![+!]))"));
        LEFT.put("sql", Pattern.compile("^(--(?!\\+)|/\\*(?![+!]))"));
        LEFT.put("mysql", Pattern.compile("^(--(?!\\+)|#|/\\*(?![+!]))"));
        LEFT.put("props", Pattern.compile("^[#!]"));
        LEFT.put("sh", Pattern.compile("^#(?!!)"));
        LEFT.put("yaml", Pattern.compile("^#"));
        LEFT.put("bat", Pattern.compile("(?i)^(@?rem\\b|::)"));
    }

    @Test
    void leftoverCommentsBaseline() throws IOException {
        Map<String, List<String>> broken = new TreeMap<>();
        Map<String, Integer> total = new TreeMap<>();
        for (JsonNode r : of(LEFT.keySet().toArray(String[]::new))) {
            String lang = r.get("lang").asText();
            total.merge(lang, 1, Integer::sum);
            broken.computeIfAbsent(lang, k -> new ArrayList<>());
            Pattern p = LEFT.get(lang);
            for (String line : stripped(r).split("\n")) {
                if (p.matcher(line.strip()).find()) {
                    broken.get(lang).add(r.get("file").asText());
                    break;
                }
            }
        }
        List<String> fails = new ArrayList<>();
        for (String lang : total.keySet()) {
            try {
                CorpusFiles.baseline("strip-" + lang, broken.get(lang), total.get(lang));
            } catch (AssertionError e) {
                fails.add(e.getMessage());
            }
        }
        assertEquals(List.of(), fails);
    }

    /** 손으로 고른 표본 — 언어별 첫 「지운 것이 있는」 파일의 지운 수·힌트·길이(코드 본문은 골든에 안 싣는다) */
    @Test
    void pickedSummaryGolden() throws IOException {
        Map<String, Map<String, Object>> picked = new TreeMap<>();
        for (JsonNode r : rows) {
            String lang = r.path("lang").asText();
            if (r.has("error") || r.get("removed").asInt() == 0 || picked.containsKey(lang)) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("file", r.get("file").asText());
            m.put("removed", r.get("removed").asInt());
            m.put("hints", r.get("hints").asInt());
            m.put("before", original(r).length());
            m.put("after", stripped(r).length());
            picked.put(lang, m);
        }
        GoldenFiles.assertJson("corpus/strip-picked.json", picked);
    }
}
