package kr.ejg.toolbox.core.check;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 검사 기준선(8-17) — 줄 번호 없는 키 {@code 파일|규칙|발췌} 로 코드가 움직여도 안 흔들린다. 같은 키가 또 나오면 {@code  #2} ….
 * 자기 검사({@code SelfCheckTest})와 CLI {@code check --write-baseline}·{@code --baseline} 이 같은 꼴을 쓴다.
 * 기준선은 줄일 때만 다시 뜬다 — 사라진 항목은 실패가 아니다(PMD 기준선과 같은 래칫)
 */
public final class Baseline {

    /** 지금 지적과 기준선의 차이 — 새 지적(기준선에 없음)·가린 수·사라진 수 */
    public record Diff(List<String> fresh, int hidden, int stale) {
        public Diff {
            fresh = List.copyOf(fresh);
        }
    }

    private Baseline() {
    }

    public static String key(String file, String rule, String excerpt) {
        return file + "|" + rule + "|" + excerpt;
    }

    public static List<String> keys(List<Finding> fs) {
        List<String> raw = new ArrayList<>();
        for (Finding f : fs) {
            raw.add(key(f.file(), f.rule(), f.excerpt()));
        }
        return numbered(raw);
    }

    /** 같은 키 두 번째부터 {@code  #n} 을 붙인다 — 들어온 차례 그대로 */
    public static List<String> numbered(List<String> raw) {
        Map<String, Integer> seen = new HashMap<>();
        List<String> out = new ArrayList<>();
        for (String k : raw) {
            int n = seen.merge(k, 1, Integer::sum);
            out.add(n == 1 ? k : k + " #" + n);
        }
        return out;
    }

    /** 정렬·UTF-8·LF·끝 개행(빈 목록이면 빈 파일) */
    public static void write(Path file, List<String> keys) throws IOException {
        Set<String> sorted = new TreeSet<>();
        keys.forEach(k -> sorted.add(k.trim()));
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, sorted.isEmpty() ? "" : String.join("\n", sorted) + "\n", StandardCharsets.UTF_8);
    }

    /** 줄마다 trim, 빈 줄은 뺀다 */
    public static Set<String> read(Path file) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    public static Diff diff(List<String> now, Set<String> base) {
        List<String> fresh = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String k : now) {
            String t = k.trim();
            seen.add(t);
            if (!base.contains(t)) {
                fresh.add(t);
            }
        }
        int stale = 0;
        for (String b : base) {
            if (!seen.contains(b)) {
                stale++;
            }
        }
        return new Diff(fresh, now.size() - fresh.size(), stale);
    }
}
