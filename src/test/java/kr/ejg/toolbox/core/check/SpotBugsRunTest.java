package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.job.Job;
import kr.ejg.toolbox.core.job.JobManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 5-38b — 픽스처 둘(fixtures/check/ext)을 JavaCompiler 로 -g 컴파일해 SpotBugs·FindSecBugs 를 돌린다. 시험 폴더는 target 아래
 * (플러그인 클래스로더가 jar 를 쥔다, 5-38a). JDK 가 아니면(컴파일러 없음) 건너뛴다
 */
class SpotBugsRunTest {

    static final Path SRC = Path.of("src/test/resources/fixtures/check/ext");
    static final Path WORK = Path.of("target", "spotbugs-run-test");
    static Path classes;
    static Path plugin;

    @BeforeAll
    static void compile() throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assumeTrue(javac != null, "JDK 컴파일러 없음");
        classes = WORK.resolve("classes");
        if (Files.isDirectory(classes)) { // 지난 판의 클래스가 남으면 수가 어긋난다
            try (Stream<Path> s = Files.walk(classes)) {
                s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        Files.createDirectories(classes);
        int rc = javac.run(null, null, null, "-g", "-encoding", "UTF-8", "-d", classes.toString(), SRC.resolve("Pos.java").toString(),
                SRC.resolve("Neg.java").toString());
        assertEquals(0, rc, "픽스처 컴파일");
        plugin = Analyzers.findSecBugsJar(WORK.resolve("data"));
    }

    static Rule.Def def(String id, Map<String, Object> params) {
        return new Rule.Def(id, "ext", "warn", "external", List.of("*.class"), null, null, null, "m", params, true);
    }

    static List<Rule.Def> both(int minPriority) {
        return List.of(def(SpotBugsRun.SPOTBUGS, Map.of("minPriority", minPriority)), def(SpotBugsRun.FINDSECBUGS, Map.of("minPriority", minPriority)));
    }

    static List<String> rules(SpotBugsRun.Result r) {
        return r.findings().stream().map(f -> f.file() + " " + f.rule()).toList();
    }

    @Test
    void bugsAndSecurityPatternsMapToSourceLines() {
        SpotBugsRun.Result r = SpotBugsRun.run(SRC.getParent(), classes, null, plugin, both(3), null);
        assertEquals(2, r.classes());
        assertTrue(r.note() == null, r.note());
        List<String> got = rules(r);
        assertTrue(got.contains("ext/Pos.java spotbugs.NP_ALWAYS_NULL"), got.toString());
        assertTrue(got.stream().anyMatch(s -> s.startsWith("ext/Pos.java findsecbugs.SQL_")), got.toString());
        assertTrue(got.stream().anyMatch(s -> s.startsWith("ext/Pos.java findsecbugs.PATH_TRAVERSAL")), got.toString());
        assertTrue(got.stream().noneMatch(s -> s.startsWith("ext/Neg.java")), "음성 0 — " + got);
        Finding np = r.findings().stream().filter(f -> f.rule().startsWith("spotbugs.NP_")).findFirst().orElseThrow();
        assertEquals(14, np.line(), "s.length() 줄");
        assertEquals("ext", np.group());
        assertTrue(np.excerpt().length() <= Finding.EXCERPT && np.excerpt().contains(" — "), np.excerpt());
    }

    @Test
    void priorityAndExcludeFilter() {
        SpotBugsRun.Result all = SpotBugsRun.run(SRC.getParent(), classes, null, plugin, both(3), null);
        SpotBugsRun.Result high = SpotBugsRun.run(SRC.getParent(), classes, null, plugin, both(1), null);
        assertTrue(high.findings().size() < all.findings().size(), high.findings().size() + " < " + all.findings().size());
        assertTrue(high.findings().stream().allMatch(f -> f.severity().equals("error")), "우선순위 1 → error");
        SpotBugsRun.Result noNp = SpotBugsRun.run(SRC.getParent(), classes, null, plugin,
                List.of(def(SpotBugsRun.SPOTBUGS, Map.of("minPriority", 3, "exclude", List.of("NP_*")))), null);
        assertTrue(noNp.findings().stream().noneMatch(f -> f.rule().startsWith("spotbugs.NP_")), rules(noNp).toString());
        assertTrue(noNp.findings().stream().noneMatch(f -> f.rule().startsWith("findsecbugs.")), "FindSecBugs 규칙이 꺼지면 그 지적 0 — " + rules(noNp));
    }

    @Test
    void missingOrEmptyClassFolderIsANote() throws Exception {
        assertTrue(SpotBugsRun.run(SRC.getParent(), WORK.resolve("nope"), null, plugin, both(2), null).note().startsWith("클래스 폴더가 없다"));
        Path empty = Files.createDirectories(WORK.resolve("empty"));
        assertTrue(SpotBugsRun.run(SRC.getParent(), empty, null, plugin, both(2), null).note().contains(".class 가 없다"));
        assertEquals(List.of(), SpotBugsRun.run(SRC.getParent(), classes, null, plugin, List.of(), null).findings(), "규칙이 다 꺼지면 안 돈다");
    }

    /** 5-39b — 판별이 찾은 모듈 클래스 폴더 여럿을 한 번에 — 한 폴더일 때와 지적이 같고, 없는 폴더는 note 로 남기고 나머지로 돈다 */
    @Test
    void severalClassFoldersAreOneProject() throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        Path a = WORK.resolve("split-a");
        Path b = WORK.resolve("split-b");
        for (Path d : List.of(a, b)) {
            if (Files.isDirectory(d)) {
                try (Stream<Path> s = Files.walk(d)) {
                    s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
            Files.createDirectories(d);
        }
        assertEquals(0, javac.run(null, null, null, "-g", "-encoding", "UTF-8", "-d", a.toString(), SRC.resolve("Pos.java").toString()));
        assertEquals(0, javac.run(null, null, null, "-g", "-encoding", "UTF-8", "-d", b.toString(), SRC.resolve("Neg.java").toString()));
        SpotBugsRun.Result one = SpotBugsRun.run(SRC.getParent(), classes, null, plugin, both(3), null);
        SpotBugsRun.Result two = SpotBugsRun.run(SRC.getParent(), List.of(a, b), null, plugin, both(3), null);
        assertEquals(2, two.classes());
        assertEquals(rules(one).stream().sorted().toList(), rules(two).stream().sorted().toList());
        SpotBugsRun.Result partial = SpotBugsRun.run(SRC.getParent(), List.of(a, WORK.resolve("nope")), null, plugin, both(3), null);
        assertTrue(partial.note() != null && partial.note().startsWith("클래스 폴더가 없다"), partial.note());
        assertTrue(rules(partial).contains("ext/Pos.java spotbugs.NP_ALWAYS_NULL"), rules(partial).toString());
    }

    @Test
    void webInfLibIsGuessed() {
        assertEquals(Path.of("x", "WEB-INF", "lib").toAbsolutePath().normalize(), SpotBugsRun.webInfLib(Path.of("x", "WEB-INF", "classes")));
        assertEquals(null, SpotBugsRun.webInfLib(Path.of("target", "classes")));
    }

    /** CheckRunner — External 이 있어야 돌고, 결과에 섞여 external 수가 선다 */
    @Test
    void checkRunnerMergesExternalFindings() throws Exception {
        RuleSet rules = RuleSet.builtin();
        LocalFiles lf = new LocalFiles(WORK.resolve("lf"));
        String root = SRC.getParent().toAbsolutePath().toString(); // 패키지 ext = 폴더 ext — 지적이 ext/Pos.java 에 붙는다
        CheckRunner.RunResult plain = CheckRunner.run(root, rules, lf, null, null, List.of());
        assertEquals(0, plain.external());
        CheckRunner.RunResult ext = CheckRunner.run(root, rules, lf, null, null, List.of(),
                new CheckRunner.External(classes.toAbsolutePath().toString(), null, plugin));
        assertTrue(ext.external() > 0, ext.toString());
        assertTrue(ext.findings().stream().anyMatch(f -> f.file().equals("ext/Pos.java") && f.rule().startsWith("spotbugs.")), ext.findings().toString());
        CheckRunner.RunResult onlyNeg = CheckRunner.run(root, rules, lf, null, rel -> rel.equals("ext/Neg.java"), List.of(),
                new CheckRunner.External(classes.toAbsolutePath().toString(), null, plugin));
        assertTrue(onlyNeg.findings().stream().noneMatch(f -> f.rule().startsWith("spotbugs.") || f.rule().startsWith("findsecbugs.")),
                "변경분 — 바뀐 파일의 외부 지적만");
        // 마무리 리뷰 — 클래스 폴더는 목록을 안 거쳐 codecheck.exclude 를 따로 건다(파일·위 폴더)
        CheckRunner.RunResult excl = CheckRunner.run(root, rules, lf, null, null, List.of("ext/**"),
                new CheckRunner.External(classes.toAbsolutePath().toString(), null, plugin));
        assertEquals(0, excl.external(), excl.findings().toString());
        assertTrue(LocalFiles.excluded("ext/Pos.java", List.of("**/Pos.java")));
        assertTrue(LocalFiles.excluded("a/ext/Pos.java", List.of("a/ext")));
        assertTrue(!LocalFiles.excluded("ext/Pos.java", List.of("other/**")));
    }

    /** 마무리 리뷰 — exclude 글롭은 * 만 특수, 나머지 글자는 그대로(정규식 예외 없음) */
    @Test
    void excludeGlobIsLiteralButStar() {
        Rule.Def d = def(SpotBugsRun.SPOTBUGS, Map.of("exclude", List.of("NP_*", "EI_(", "*_REP2")));
        assertTrue(SpotBugsRun.excluded(d, "NP_ALWAYS_NULL"));
        assertTrue(SpotBugsRun.excluded(d, "EI_EXPOSE_REP2"));
        assertTrue(!SpotBugsRun.excluded(d, "EI_EXPOSE_REP"));
        assertTrue(SpotBugsRun.excluded(d, "EI_("));
    }

    /** 취소 — 엔진 안(finishClass)에서 던진 취소가 작업을 CANCELLED 로 끝낸다 */
    @Test
    void cancelInsideEngine() throws Exception {
        JobManager jm = new JobManager(1);
        try {
            Job job = jm.submit("sb", jc -> {
                Thread.currentThread().interrupt(); // JobContext.isCancelled 가 참 — 첫 클래스 끝에서 던진다
                return SpotBugsRun.run(SRC.getParent(), classes, null, plugin, both(3), jc);
            });
            long end = System.nanoTime() + 60_000_000_000L;
            while (System.nanoTime() < end && (job.status() == Job.Status.QUEUED || job.status() == Job.Status.RUNNING)) {
                Thread.sleep(50);
            }
            assertEquals(Job.Status.CANCELLED, job.status());
        } finally {
            jm.shutdown();
        }
        try (Stream<Path> s = Files.list(classes)) {
            assertTrue(s.findAny().isPresent());
        }
    }
}
