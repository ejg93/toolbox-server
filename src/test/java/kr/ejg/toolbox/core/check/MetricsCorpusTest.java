package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.profile.Profile;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.cpd.CPDConfiguration;
import net.sourceforge.pmd.cpd.CpdAnalysis;
import net.sourceforge.pmd.cpd.Mark;
import net.sourceforge.pmd.cpd.Match;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 5-35c — 묶음 ㅊ(크기·복잡도·중복)을 eGov 표본에서 PMD 7.28·CPD 와 기계로 맞댄다(5-32e Checkstyle 대조와 같은 꼴, 설계 32).
 * 두 엔진에 같은 UTF-8 사본을 주고, 같은 기준값으로 돌려 한쪽에만 있는 것을 목록으로 굳힌다(conformance — 바뀌면 빨강).
 * 정의가 다른 것은 맞추지 않는다 — NcssCount(문장 수) 대 methodLength(줄 수), TooManyMethods(짧은 get·set·is 뺌) 대 classMethods(전부),
 * CPD(토큰) 대 metric.duplicate(정규화 줄). 그 차이는 목록에 남기고 사람이 본다(이 번들에서 정의를 고치지 않는다).
 */
@Tag("corpus")
class MetricsCorpusTest {

    static final String SOURCE = "egov";
    /** CPD 최소 토큰 — 정규화 30줄에 가깝게 실측해 고른 값(2026-10-24: 150→114·250→32·350→8 짝, 우리 31)*/
    static final int CPD_TILE = Integer.getInteger("metrics.cpdTile", 250);
    /** 우리 규칙 → PMD 규칙(design.xml) · 같은 기준(PMD 는 「≥ 값」, 우리는 「> max」) */
    static final Map<String, String> PMD_RULE = Map.of("CyclomaticComplexity", JavaRules.COMPLEXITY, "ExcessiveParameterList", JavaRules.PARAMS,
            "NcssCount", JavaRules.METHOD_LENGTH, "TooManyMethods", JavaRules.CLASS_METHODS);
    static final Map<String, String> DIFF_NAME = Map.of(JavaRules.COMPLEXITY, "cc", JavaRules.PARAMS, "params", JavaRules.METHOD_LENGTH, "len",
            JavaRules.CLASS_METHODS, "methods");

    @TempDir
    Path tmp;

    record Hit(String file, int begin, int end, String text) {
    }

    @Test
    void matchesPmd() throws Exception {
        CorpusFiles.verify();
        Path copy = tmp.resolve("src");
        LocalFiles lf = new LocalFiles(tmp.resolve("data"));
        int files = 0;
        Path dir = CorpusFiles.root().resolve(SOURCE);
        for (Path p : CorpusFiles.files(SOURCE, "*.java")) {
            Path c = copy.resolve(dir.relativize(p));
            Files.createDirectories(c.getParent());
            Files.writeString(c, lf.read(p.toString()).text(), StandardCharsets.UTF_8);
            files++;
        }
        // 우리 — 묶음 metric 만, 기본 기준
        Map<String, Boolean> only = new LinkedHashMap<>();
        RuleSet.builtin().defs().forEach(d -> only.put(d.group(), d.group().equals("metric")));
        Profile prof = new Profile("corpus", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null, null,
                new Profile.CodeCheck(only, null, null), "egov35", null, null, null);
        List<Finding> ours = CheckRunner.run(copy.toString(), RuleSet.load(prof, null), lf, null).findings();

        // PMD — 같은 기준
        Path ruleset = tmp.resolve("ruleset.xml");
        Files.writeString(ruleset, """
                <?xml version="1.0"?>
                <ruleset name="tb" xmlns="http://pmd.sourceforge.net/ruleset/2.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://pmd.sourceforge.net/ruleset/2.0.0 https://pmd.sourceforge.io/ruleset_2_0_0.xsd">
                  <description>5-35c</description>
                  <rule ref="category/java/design.xml/CyclomaticComplexity"><properties>
                    <property name="methodReportLevel" value="16"/><property name="classReportLevel" value="999"/></properties></rule>
                  <rule ref="category/java/design.xml/ExcessiveParameterList"><properties>
                    <property name="minimum" value="8"/></properties></rule>
                  <rule ref="category/java/design.xml/NcssCount"><properties>
                    <property name="methodReportLevel" value="101"/><property name="classReportLevel" value="99999"/></properties></rule>
                  <rule ref="category/java/design.xml/TooManyMethods"><properties>
                    <property name="maxmethods" value="30"/></properties></rule>
                </ruleset>
                """, StandardCharsets.UTF_8);
        PMDConfiguration cfg = new PMDConfiguration();
        cfg.addRuleSet(ruleset.toString());
        cfg.setIgnoreIncrementalAnalysis(true);
        cfg.setSourceEncoding(StandardCharsets.UTF_8);
        Map<String, List<Hit>> pmd = new TreeMap<>();
        List<String> pmdErrors = new ArrayList<>();
        try (PmdAnalysis a = PmdAnalysis.create(cfg)) {
            a.files().addDirectory(copy);
            Report r = a.performAnalysisAndCollectReport();
            for (RuleViolation v : r.getViolations()) {
                String rule = PMD_RULE.get(v.getRule().getName());
                if (rule != null) {
                    pmd.computeIfAbsent(rule, k -> new ArrayList<>()).add(new Hit(rel(copy, v.getFileId().getAbsolutePath()), v.getBeginLine(),
                            v.getEndLine(), v.getDescription()));
                }
            }
            r.getProcessingErrors().forEach(e -> pmdErrors.add(rel(copy, e.getFileId().getAbsolutePath())));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("files", files);
        summary.put("pmdErrors", pmdErrors.size());
        for (String rule : List.of(JavaRules.COMPLEXITY, JavaRules.PARAMS, JavaRules.METHOD_LENGTH, JavaRules.CLASS_METHODS)) {
            List<Hit> theirs = pmd.getOrDefault(rule, List.of());
            List<Finding> mine = ours.stream().filter(f -> f.rule().equals(rule)).toList();
            List<String> diff = diff(mine, theirs);
            summary.put(rule, Map.of("ours", mine.size(), "pmd", theirs.size(), "diff", diff.size()));
            CorpusFiles.conformance("metrics-diff-" + DIFF_NAME.get(rule), diff);
        }

        // CPD — 같은 사본, 식별자·리터럴 그대로(우리도 글자 그대로 견준다)
        CPDConfiguration cc = new CPDConfiguration();
        cc.setMinimumTileSize(CPD_TILE);
        cc.setOnlyRecognizeLanguage(cc.getLanguageRegistry().getLanguageById("java"));
        cc.setSourceEncoding(StandardCharsets.UTF_8);
        List<String[]> cpdPairs = new ArrayList<>();
        try (CpdAnalysis a = CpdAnalysis.create(cc)) {
            a.files().addDirectory(copy);
            a.performAnalysis(report -> {
                for (Match m : report.getMatches()) {
                    Mark first = m.getFirstMark();
                    for (Mark k : m) {
                        if (k != first) {
                            cpdPairs.add(new String[] {loc(copy, first), loc(copy, k)});
                        }
                    }
                }
            });
        }
        List<String[]> ourPairs = new ArrayList<>();
        for (Finding f : ours) {
            if (f.rule().equals("metric.duplicate")) {
                String other = f.excerpt().substring(0, f.excerpt().indexOf(" 과 같음"));
                ourPairs.add(new String[] {other, f.file() + ":" + f.line()});
            }
        }
        List<String> dupDiff = pairDiff(ourPairs, cpdPairs);
        summary.put("metric.duplicate", Map.of("ours", ourPairs.size(), "cpd", cpdPairs.size(), "diff", dupDiff.size(), "cpdTile", CPD_TILE));
        CorpusFiles.conformance("metrics-diff-dup", dupDiff);
        CorpusFiles.conformance("metrics-pmd-errors", pmdErrors);
        GoldenFiles.assertJson("corpus/metrics-egov.json", summary);
        assertTrue(files > 1000, "표본 Java 가 너무 적다: " + files);
    }

    static String rel(Path root, String abs) {
        return root.relativize(Path.of(abs)).toString().replace('\\', '/');
    }

    static String loc(Path root, Mark m) {
        return rel(root, m.getLocation().getFileId().getAbsolutePath()) + ":" + m.getLocation().getStartLine();
    }

    /** 우리 지적 줄(이름 줄)이 PMD 지적 범위 안이면 짝 — 범위 시작에 가까운 것부터. 짝 없는 것을 양쪽에서 */
    static List<String> diff(List<Finding> mine, List<Hit> theirs) {
        List<Finding> left = new ArrayList<>(mine);
        List<String> out = new ArrayList<>();
        for (Hit h : theirs) {
            Finding best = null;
            for (Finding f : left) {
                if (f.file().equals(h.file()) && f.line() >= h.begin() && f.line() <= h.end() && (best == null || f.line() < best.line())) {
                    best = f;
                }
            }
            if (best == null) {
                out.add("pmd만 " + h.file() + ":" + h.begin() + " " + h.text());
            } else {
                left.remove(best);
            }
        }
        left.forEach(f -> out.add("우리만 " + f.file() + ":" + f.line() + " " + f.excerpt()));
        return out;
    }

    /** 두 자리 짝(순서 무관) — 양 끝이 같은 파일이고 줄이 ±3 안이면 같은 중복 */
    static List<String> pairDiff(List<String[]> mine, List<String[]> theirs) {
        List<String[]> left = new ArrayList<>(mine);
        List<String> out = new ArrayList<>();
        for (String[] t : theirs) {
            String[] hit = null;
            for (String[] m : left) {
                if (near(m[0], t[0]) && near(m[1], t[1]) || near(m[0], t[1]) && near(m[1], t[0])) {
                    hit = m;
                    break;
                }
            }
            if (hit == null) {
                out.add("cpd만 " + t[0] + " == " + t[1]);
            } else {
                left.remove(hit);
            }
        }
        left.forEach(m -> out.add("우리만 " + m[0] + " == " + m[1]));
        return out;
    }

    static boolean near(String a, String b) {
        int i = a.lastIndexOf(':');
        int j = b.lastIndexOf(':');
        return a.substring(0, i).equals(b.substring(0, j)) && Math.abs(Integer.parseInt(a.substring(i + 1)) - Integer.parseInt(b.substring(j + 1))) <= 3;
    }
}
