package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.core.fs.LocalFiles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 5-32e — {@code java.declarationOrder} 를 실물 표본 Java 전부에서 Checkstyle {@code DeclarationOrder}(0-61e 와 같은 판)와 맞댄다.
 * 두 엔진에 같은 글을 준다 — {@link LocalFiles#read} 로 인코딩을 판별해 읽은 글을 임시 폴더에 UTF-8 로 쓰고 Checkstyle 은 그 파일을,
 * 우리 규칙은 그 글을 본다. 차이(파일:줄, 어느 쪽만)는 등급 B baseline, 어느 쪽이든 못 읽은 파일은 conformance 목록.
 * {@code ignoreModifiers} 모드는 egov·jspwiki 두 원천만(실행 시간).
 */
@Tag("corpus")
class DeclarationOrderCorpusTest {

    /** corpus/MANIFEST 의 Java 원천(2026-10-21 7,173 파일). egov-prev 는 egov 의 옛 판 사본이라 그대로 둔다 */
    static final List<String> SOURCES = List.of("egov", "egov-prev", "egov-portal", "egov-enterprise", "egov-homepage", "jspwiki", "roller",
            "struts", "commons-lang", "commons-io", "commons-collections", "shopizer", "egov-msa");
    static final List<String> IGNORE_SOURCES = List.of("egov", "jspwiki");

    @TempDir
    Path tmp;

    @Test
    void matchesCheckstyle() throws Exception {
        CorpusFiles.verify();
        Rule.Def order = RuleSet.builtin().defs().stream().filter(d -> d.id().equals(JavaRules.ORDER)).findFirst().orElseThrow();
        Rule.Def parse = RuleSet.builtin().defs().stream().filter(d -> d.id().equals(JavaRules.PARSE_ERROR)).findFirst().orElseThrow();
        List<String> skipped = new ArrayList<>();
        List<String> diff = new ArrayList<>();
        int total = compare(SOURCES, order, parse, false, skipped, diff);
        List<String> diffIgnore = new ArrayList<>();
        int totalIgnore = compare(IGNORE_SOURCES, order.override(Map.of("params", Map.of("ignoreModifiers", true))), parse, true,
                new ArrayList<>(), diffIgnore);
        CorpusFiles.conformance("order-skipped", skipped);
        CorpusFiles.baseline("order-diff", diff, total);
        CorpusFiles.baseline("order-diff-ignore", diffIgnore, totalIgnore);
        assertTrue(total > 5000, "표본 Java 가 너무 적다: " + total);
    }

    /** 원천들을 두 엔진으로 돌려 차이를 모은다. 돌려주는 값은 비교한 파일 수 */
    private int compare(List<String> sources, Rule.Def order, Rule.Def parse, boolean ignoreModifiers, List<String> skipped, List<String> diff)
            throws Exception {
        LocalFiles lf = new LocalFiles(tmp.resolve("data"));
        int total = 0;
        for (String source : sources) {
            Path dir = CorpusFiles.root().resolve(source);
            Path copy = tmp.resolve(ignoreModifiers ? "ign" : "def").resolve(source);
            Map<String, Set<Integer>> ours = new TreeMap<>();
            Set<String> failed = new TreeSet<>();
            List<File> files = new ArrayList<>();
            for (Path p : CorpusFiles.files(source, "*.java")) {
                String rel = CorpusFiles.rel(p);
                LocalFiles.Text t = lf.read(p.toString());
                Path c = copy.resolve(dir.relativize(p));
                Files.createDirectories(c.getParent());
                Files.writeString(c, t.text(), StandardCharsets.UTF_8);
                files.add(c.toFile());
                Set<Integer> lines = new TreeSet<>();
                for (Finding f : new JavaRules(List.of(order, parse), null).apply(new Source(rel, t.text(), "UTF-8", t.lineEnding(), "java"))) {
                    if (f.rule().equals(JavaRules.PARSE_ERROR)) {
                        failed.add(rel);
                    } else {
                        lines.add(f.line());
                    }
                }
                ours.put(rel, lines);
            }
            Map<String, Set<Integer>> theirs = checkstyle(files, copy, source, ignoreModifiers, failed);
            // 마무리 리뷰 — Checkstyle 쪽 경로가 우리 키와 어긋나면 그 지적이 비교에서 조용히 빠진다. 어긋난 키는 차이로 남긴다
            for (String rel : theirs.keySet()) {
                if (!ours.containsKey(rel)) {
                    diff.add(rel + " 경로 불일치");
                }
            }
            for (String rel : ours.keySet()) {
                if (failed.contains(rel)) {
                    skipped.add(rel);
                    continue;
                }
                total++;
                Set<Integer> a = ours.get(rel);
                Set<Integer> b = theirs.getOrDefault(rel, Set.of());
                for (int l : a) {
                    if (!b.contains(l)) {
                        diff.add(rel + ":" + l + " 우리만");
                    }
                }
                for (int l : b) {
                    if (!a.contains(l)) {
                        diff.add(rel + ":" + l + " checkstyle만");
                    }
                }
            }
        }
        return total;
    }

    /** Checkstyle 12.3.1 을 안에서 — 파일 → 지적 줄. 예외(못 읽음 등)가 난 파일은 failed 에 */
    private static Map<String, Set<Integer>> checkstyle(List<File> files, Path copy, String source, boolean ignoreModifiers, Set<String> failed)
            throws Exception {
        DefaultConfiguration rule = new DefaultConfiguration("DeclarationOrder");
        if (ignoreModifiers) {
            rule.addProperty("ignoreModifiers", "true");
        }
        DefaultConfiguration walker = new DefaultConfiguration("TreeWalker");
        walker.addChild(rule);
        DefaultConfiguration root = new DefaultConfiguration("Checker");
        root.addProperty("charset", "UTF-8");
        root.addProperty("localeLanguage", "en");
        root.addChild(walker);
        Map<String, Set<Integer>> out = new TreeMap<>();
        Checker checker = new Checker();
        checker.setModuleClassLoader(Checker.class.getClassLoader());
        checker.configure(root);
        checker.setHaltOnException(false);
        checker.addListener(new AuditListener() {
            @Override
            public void addError(AuditEvent e) {
                String rel = source + "/" + copy.relativize(Path.of(e.getFileName())).toString().replace('\\', '/');
                if (e.getViolation() != null && e.getViolation().getKey().startsWith("declaration.order")) {
                    out.computeIfAbsent(rel, k -> new TreeSet<>()).add(e.getLine());
                } else {
                    failed.add(rel);
                }
            }

            @Override
            public void addException(AuditEvent e, Throwable t) {
                failed.add(source + "/" + copy.relativize(Path.of(e.getFileName())).toString().replace('\\', '/'));
            }

            @Override
            public void auditStarted(AuditEvent e) {
            }

            @Override
            public void auditFinished(AuditEvent e) {
            }

            @Override
            public void fileStarted(AuditEvent e) {
            }

            @Override
            public void fileFinished(AuditEvent e) {
            }
        });
        try {
            checker.process(files);
        } finally {
            checker.destroy();
        }
        return out;
    }
}
