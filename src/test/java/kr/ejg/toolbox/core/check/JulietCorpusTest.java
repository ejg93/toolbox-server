package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * V-36b(설계 41) — NIST Juliet Java 1.3 선택 표본(MANIFEST 밖, 없으면 건너뜀)으로 보안 규칙을 메서드 영역 단위로 채점한다.
 * 파일마다 bad 영역(이름 {@code _bad} 파일 전체, 또는 {@code bad…} 메서드)과 good 영역({@code _good…} 파일, {@code good…} 메서드)을 가른다.
 * bad 영역이 있는 파일에 매핑 규칙 지적이 bad 영역에 있으면 TP, 없으면 FN · good 영역에 지적이 있으면 FP, 없으면 TN.
 * 검사는 security·java 묶음만 켠다(java 는 parseError 를 보려고) — 1만 파일에 metric·dup 을 돌리지 않는다.
 */
@Tag("corpus")
class JulietCorpusTest {

    /** CWE 폴더 앞 「CWE<n>_」 → 우리 규칙(설계 41 §3-4, CWE330 은 zip 에 없어 뺐다). 비어 있으면 미탐 범주 */
    static final Map<String, List<String>> MAP = new LinkedHashMap<>();

    static {
        MAP.put("CWE78", List.of("security.osCommand"));
        MAP.put("CWE89", List.of("security.sqlConcat"));
        MAP.put("CWE23", List.of("security.pathTraversal"));
        MAP.put("CWE36", List.of("security.pathTraversal"));
        MAP.put("CWE327", List.of("security.weakCipher"));
        MAP.put("CWE328", List.of("security.weakHash"));
        MAP.put("CWE338", List.of("security.weakRandom"));
        MAP.put("CWE601", List.of("security.openRedirect"));
        MAP.put("CWE113", List.of("security.responseSplitting"));
        MAP.put("CWE259", List.of("security.hardcodedPassword"));
        MAP.put("CWE321", List.of("security.hardcodedKey"));
        MAP.put("CWE209", List.of("security.exceptionMessage"));
        MAP.put("CWE80", List.of());
        MAP.put("CWE81", List.of());
        MAP.put("CWE83", List.of());
    }

    /** 메서드 선언 머리 — 이름이 bad…·good… 이면 그 영역, 아니면(main·도우미) 영역 밖 */
    static final Pattern METHOD = Pattern.compile("^\\s*(?:public|private|protected)\\s+(?:static\\s+)?(?:final\\s+)?(?:synchronized\\s+)?[\\w<>\\[\\],. ]+?\\s+(\\w+)\\s*\\(");

    @TempDir
    Path tmp;

    static RuleSet securityOnly() {
        Map<String, Boolean> groups = new LinkedHashMap<>();
        for (String g : List.of("common", "jsp", "tsx", "file", "a11y", "mybatis", "metric", "ext")) {
            groups.put(g, false);
        }
        Profile p = new Profile("juliet", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null,
                new Profile.Naming(null, null, null, null, null, false), new Profile.CodeCheck(groups, null, null), "spring",
                null, null, null);
        return RuleSet.load(p, null);
    }

    @Test
    void score() throws Exception {
        Path root = CorpusFiles.root().resolve("juliet");
        assumeTrue(Files.isDirectory(root), "juliet 없음 — bash scripts/corpus-fetch.sh juliet");
        RuleSet rules = securityOnly();
        LocalFiles files = new LocalFiles(tmp.resolve("data"));
        List<String> a = new ArrayList<>();
        List<String> fp = new ArrayList<>();
        Map<String, Object> golden = new LinkedHashMap<>();
        int[] all = new int[5];
        List<Path> dirs;
        try (Stream<Path> s = Files.list(root)) {
            dirs = s.filter(Files::isDirectory).sorted().toList();
        }
        for (Path dir : dirs) {
            String cwe = dir.getFileName().toString().replaceFirst("_.*", "");
            List<String> mapped = MAP.getOrDefault(cwe, List.of());
            int[] sc = new int[5]; // tp fp fn tn files
            CheckRunner.RunResult r = CheckRunner.run(dir.toString(), rules, files, null);
            if (r.skipped() > 0) {
                a.add(cwe + " 건너뛴 파일 " + r.skipped()); // 못 읽은 파일은 지적 없음으로 TN·FN 에 섞인다
            }
            Map<String, List<Integer>> hitLines = new HashMap<>();
            for (Finding f : r.findings()) {
                if (f.rule().endsWith(".parseError")) {
                    a.add(cwe + " " + f.file() + ":" + f.line() + " " + f.rule());
                } else if (mapped.contains(f.rule())) {
                    hitLines.computeIfAbsent(f.file().replace('\\', '/'), k -> new ArrayList<>()).add(f.line());
                }
            }
            for (Path p : CorpusFiles.files("juliet/" + dir.getFileName(), "*.java")) {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                char[] region = regions(lines(p), p.getFileName().toString());
                boolean hasBad = false;
                boolean hasGood = false;
                for (char c : region) {
                    hasBad |= c == 'b';
                    hasGood |= c == 'g';
                }
                boolean badHit = false;
                boolean goodHit = false;
                for (int line : hitLines.getOrDefault(rel, List.of())) {
                    char c = line >= 1 && line <= region.length ? region[line - 1] : ' ';
                    badHit |= c == 'b';
                    goodHit |= c == 'g';
                }
                sc[4]++;
                if (hasBad) {
                    sc[badHit ? 0 : 2]++;
                }
                if (hasGood) {
                    sc[goodHit ? 1 : 3]++;
                    if (goodHit) {
                        fp.add(cwe + " " + dir.getFileName() + "/" + rel);
                    }
                }
            }
            golden.put(cwe, row(mapped, sc));
            for (int i = 0; i < 5; i++) {
                all[i] += sc[i];
            }
        }
        golden.put("_total", row(List.of(), all));
        GoldenFiles.assertJson("corpus/security-juliet.json", golden);
        CorpusFiles.conformance("security-juliet-fp", fp.stream().sorted().toList());
        CorpusFiles.none("Juliet 코드 검사(파일 " + all[4] + ")", a, all[4]);
    }

    static List<String> lines(Path p) throws IOException {
        try {
            return Files.readAllLines(p, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            return Files.readAllLines(p, StandardCharsets.ISO_8859_1);
        }
    }

    /** 줄마다 영역 — 'b' bad · 'g' good · ' ' 모름. 파일 이름에 _bad·_good 이 있으면 파일 전체가 그 영역 */
    static char[] regions(List<String> lines, String fileName) {
        char[] out = new char[lines.size()];
        char whole = fileName.matches(".*_bad\\.java") ? 'b' : fileName.matches(".*_good\\w*\\.java") ? 'g' : ' ';
        char cur = whole;
        for (int i = 0; i < out.length; i++) {
            if (whole == ' ') {
                Matcher m = METHOD.matcher(lines.get(i));
                if (m.find()) {
                    String name = m.group(1);
                    cur = name.startsWith("bad") ? 'b' : name.startsWith("good") ? 'g' : ' ';
                }
            }
            out[i] = cur;
        }
        return out;
    }

    static Map<String, Object> row(List<String> rules, int[] s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rules", rules);
        m.put("files", s[4]);
        m.put("tp", s[0]);
        m.put("fp", s[1]);
        m.put("fn", s[2]);
        m.put("tn", s[3]);
        m.put("precisionPct", s[0] + s[1] == 0 ? -1 : s[0] * 100 / (s[0] + s[1]));
        m.put("recallPct", s[0] + s[2] == 0 ? -1 : s[0] * 100 / (s[0] + s[2]));
        return m;
    }
}
