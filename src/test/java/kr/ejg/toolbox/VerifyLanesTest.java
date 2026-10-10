package kr.ejg.toolbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

/**
 * 설계 21 — db·corpus 레인의 경로 목록({@code scripts/verify-fingerprint.sh} 의 {@code LANE_DB}·{@code LANE_CORPUS})이
 * 꼬리표 시험의 실제 의존과 맞나. 목록이 낡으면 빠른 검증이 빨강 — 「경로를 더하라」 가 실패 글에 찍힌다.
 * <ul>
 *   <li>① 꼬리표 시험(상속 포함)이 직접 쓰는 core 클래스에서 출발해 core 안에서만 참조를 따라간 패키지가 전부 목록에 있나.
 *       SKIP 패키지(공용 바닥·산출물·파서)는 들어가지 않는다 — 그 뒤도 안 닿는다. web·cli 는 접착제라 안 따라간다(App 을 지나면 전부에 닿는다)</li>
 *   <li>② 꼬리표 시험과 그것이 쓰는 시험 도우미(꼬리표 없는 시험 쪽 클래스)의 소스 파일이 목록에 있나 — 도우미가 쓰는 core 도 ①에 든다</li>
 *   <li>②' 그 소스들이 글자로 읽는 시험 자원({@code "sample/…"})이 목록에 있나 — 컨테이너 초기화 SQL 같은 DB 입력</li>
 *   <li>③ 시험이 부르는 node 스크립트 폴더가 corpus 레인에 있나(Puppeteer 를 다른 것으로 바꿔 폴더가 늘어도 구멍이 없게)</li>
 * </ul>
 * 레인 목록은 한 곳(그 스크립트)에만 — 여기서 복제하지 않고 읽는다.
 */
class VerifyLanesTest {

    static final Path SCRIPT = Path.of("scripts/verify-fingerprint.sh");
    static final String ROOT = "kr.ejg.toolbox.";
    static final String CORE = ROOT + "core.";
    static final String MAIN = "src/main/java/kr/ejg/toolbox/";
    static final String TEST = "src/test/java/";

    static JavaClasses all;
    static String script;

    @BeforeAll
    static void load() throws IOException {
        all = new ClassFileImporter().importPackages("kr.ejg.toolbox"); // 시험 포함 — target/test-classes 가 classpath 에 있다
        script = Files.readString(SCRIPT, StandardCharsets.UTF_8);
    }

    record Lane(String name, List<String> paths, Set<String> skip) {
    }

    /** `LANE_<NAME>=(` 줄부터 `)` 줄까지 토큰 하나가 경로 하나(주석 뺌) · `LANE_<NAME>_SKIP="…"` */
    static Lane lane(String name) {
        String up = name.toUpperCase(java.util.Locale.ROOT);
        Matcher a = Pattern.compile("(?m)^LANE_" + up + "=\\(\\s*$(.*?)^\\)\\s*$", Pattern.DOTALL).matcher(script);
        assertTrue(a.find(), SCRIPT + " 에 LANE_" + up + "=( … ) 배열");
        List<String> paths = new ArrayList<>();
        for (String line : a.group(1).split("\n")) {
            String t = line.replaceFirst("#.*$", "").trim();
            if (!t.isEmpty()) {
                paths.add(t);
            }
        }
        Matcher s = Pattern.compile("(?m)^LANE_" + up + "_SKIP=\"([^\"]*)\"").matcher(script);
        assertTrue(s.find(), SCRIPT + " 에 LANE_" + up + "_SKIP=\"…\"");
        Set<String> skip = new LinkedHashSet<>(List.of(s.group(1).trim().split("\\s+")));
        return new Lane(name, paths, skip);
    }

    static boolean isTest(JavaClass c) {
        return c.getSource().map(src -> src.getUri().toString().contains("/test-classes/")).orElse(false);
    }

    /** 꼬리표 — 클래스·상위 클래스·그 메서드의 @Tag, 둘 이상이면 @Tags 안의 @Tag(CorpusFilesTest 는 메서드에만 단다) */
    static boolean tagged(JavaClass c, String tag) {
        List<JavaClass> chain = new ArrayList<>(List.of(c));
        chain.addAll(c.getAllRawSuperclasses());
        for (JavaClass k : chain) {
            if (has(k, tag)) {
                return true;
            }
            for (com.tngtech.archunit.core.domain.JavaMethod m : k.getMethods()) {
                if (has(m, tag)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean has(com.tngtech.archunit.core.domain.properties.HasAnnotations<?> a, String tag) {
        if (a.isAnnotatedWith(Tag.class) && tag.equals(a.getAnnotationOfType(Tag.class).value())) {
            return true;
        }
        if (a.isAnnotatedWith(Tags.class)) {
            for (Tag t : a.getAnnotationOfType(Tags.class).value()) {
                if (tag.equals(t.value())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** "core/meta" 꼴. core 바닥(Version)은 "core" */
    static String pkgPath(JavaClass c) {
        return c.getPackageName().substring(ROOT.length()).replace('.', '/');
    }

    static boolean isCore(JavaClass c) {
        return c.getPackageName().startsWith(CORE) && !isTest(c);
    }

    static boolean covered(List<String> paths, String rel) {
        return paths.stream().anyMatch(p -> rel.equals(p) || rel.startsWith(p + "/"));
    }

    /** 꼬리표 시험 + 그것이 (사슬로) 쓰는 시험 쪽 클래스 — 도우미(DbCorpus·CorpusFiles·CorpusNode…) */
    static Set<JavaClass> testSide(String tag) {
        Set<JavaClass> seen = new LinkedHashSet<>();
        Deque<JavaClass> q = new ArrayDeque<>();
        for (JavaClass t : all) {
            if (isTest(t) && tagged(t, tag) && seen.add(t)) {
                q.add(t);
            }
        }
        while (!q.isEmpty()) {
            for (Dependency d : q.poll().getDirectDependenciesFromSelf()) {
                JavaClass x = d.getTargetClass().getBaseComponentType();
                if (isTest(x) && seen.add(x)) {
                    q.add(x);
                }
            }
        }
        return seen;
    }

    /** ① — 꼬리표 시험과 도우미가 직접 쓰는 core 클래스에서 출발, core 안에서만 따라간다. skip 패키지는 들어가지 않는다 */
    static Set<String> closure(String tag, Set<String> skip) {
        Set<JavaClass> seen = new LinkedHashSet<>();
        Deque<JavaClass> q = new ArrayDeque<>();
        for (JavaClass t : testSide(tag)) {
            for (Dependency d : t.getDirectDependenciesFromSelf()) {
                JavaClass x = d.getTargetClass().getBaseComponentType();
                if (isCore(x) && !skip.contains(pkgPath(x)) && seen.add(x)) {
                    q.add(x);
                }
            }
        }
        while (!q.isEmpty()) {
            JavaClass c = q.poll();
            for (Dependency d : c.getDirectDependenciesFromSelf()) {
                JavaClass x = d.getTargetClass().getBaseComponentType();
                if (isCore(x) && !skip.contains(pkgPath(x)) && seen.add(x)) {
                    q.add(x);
                }
            }
        }
        Set<String> pkgs = new TreeSet<>();
        for (JavaClass c : seen) {
            if (!pkgPath(c).equals("core")) { // core 바닥(Version)은 안 센다
                pkgs.add(pkgPath(c));
            }
        }
        return pkgs;
    }

    static String sourceOf(JavaClass t) {
        JavaClass top = t;
        while (top.getEnclosingClass().isPresent()) {
            top = top.getEnclosingClass().get();
        }
        return TEST + top.getName().replace('.', '/') + ".java";
    }

    void check(String tag) {
        Lane l = lane(tag);
        List<String> bad = new ArrayList<>();
        Set<String> pkgs = closure(tag, l.skip());
        assertTrue(!pkgs.isEmpty(), tag + " 꼬리표 시험이 하나도 없거나 core 를 안 쓴다");
        for (String pkg : pkgs) {
            if (!covered(l.paths(), MAIN + pkg)) {
                bad.add("패키지 " + MAIN + pkg);
            }
        }
        Set<String> sources = new TreeSet<>();
        for (JavaClass t : testSide(tag)) {
            sources.add(sourceOf(t)); // 중첩 클래스는 바깥 파일로
        }
        for (String src : sources) {
            if (!covered(l.paths(), src)) {
                bad.add("시험 " + src);
            }
            Path f = Path.of(src);
            if (Files.isRegularFile(f)) {
                Matcher m = SAMPLE_LIT.matcher(readQuietly(f));
                while (m.find()) {
                    String res = "src/test/resources/" + m.group(1);
                    if (!covered(l.paths(), res)) {
                        bad.add("자원 " + res + " (" + f.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(List.of(), bad, tag + " 레인 — " + SCRIPT + " 의 LANE_" + tag.toUpperCase(java.util.Locale.ROOT)
                + " 에 더하라(또는 LANE_" + tag.toUpperCase(java.util.Locale.ROOT) + "_SKIP 에). 닫힘: " + pkgs);
    }

    @Test
    void dbLaneCoversDbTests() {
        check("db");
    }

    @Test
    void corpusLaneCoversCorpusTests() {
        check("corpus");
    }

    /** 레인에 web·cli·src 통째가 들어오면 뜻이 없다 — 접착제는 CI·빠른 시험이 잰다 */
    @Test
    void heavyLanesExcludeGlue() {
        for (String name : List.of("db", "corpus")) {
            for (String p : lane(name).paths()) {
                assertTrue(!p.equals("src") && !p.equals("src/main") && !p.equals("src/main/java") && !p.equals(MAIN.replaceAll("/$", ""))
                        && !p.equals(MAIN + "core") && !p.startsWith(MAIN + "web") && !p.startsWith(MAIN + "cli"), name + " 레인에 " + p);
            }
        }
    }

    static final Pattern SAMPLE_LIT = Pattern.compile("\"(sample/[A-Za-z0-9_./-]+\\.[a-z]+)\"");

    static String readQuietly(Path f) {
        try {
            return Files.readString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static final Pattern SCRIPT_LIT = Pattern.compile("\"(scripts/[A-Za-z0-9_./-]+\\.js)\"");
    static final Pattern RUN_LIT = Pattern.compile("CorpusNode\\.run\\(\"([A-Za-z0-9_.-]+\\.js)\"");
    static final Pattern RUN_PREFIX = Pattern.compile("\"(scripts/[A-Za-z0-9_./-]+/)\"\\s*\\+\\s*script");

    /** ③ — 시험이 부르는 node 스크립트는 있어야 하고 그 폴더가 corpus 레인에 있어야 */
    @Test
    void nodeScriptsUnderCorpusLane() throws IOException {
        Lane corpus = lane("corpus");
        String node = Files.readString(Path.of(TEST + "kr/ejg/toolbox/web/CorpusNode.java"), StandardCharsets.UTF_8);
        Matcher pm = RUN_PREFIX.matcher(node);
        assertTrue(pm.find(), "CorpusNode.run 의 스크립트 접두 \"scripts/…/\" + script");
        String prefix = pm.group(1);
        Set<String> scripts = new TreeSet<>();
        try (Stream<Path> s = Files.walk(Path.of(TEST))) {
            for (Path p : s.filter(f -> f.toString().endsWith(".java")).toList()) {
                String body = Files.readString(p, StandardCharsets.UTF_8);
                Matcher a = SCRIPT_LIT.matcher(body);
                while (a.find()) {
                    scripts.add(a.group(1));
                }
                Matcher b = RUN_LIT.matcher(body);
                while (b.find()) {
                    scripts.add(prefix + b.group(1));
                }
            }
        }
        assertTrue(scripts.size() >= 6, "시험이 부르는 node 스크립트: " + scripts);
        List<String> bad = new ArrayList<>();
        for (String sc : scripts) {
            if (!Files.isRegularFile(Path.of(sc))) {
                bad.add("스크립트 없음 " + sc);
            }
            String dir = sc.substring(0, sc.lastIndexOf('/'));
            if (!covered(corpus.paths(), dir)) {
                bad.add("폴더 " + dir + " 가 corpus 레인에 없다");
            }
        }
        assertEquals(List.of(), bad, SCRIPT + " 의 LANE_CORPUS 에 더하라");
    }
}
