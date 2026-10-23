package kr.ejg.toolbox.core.check;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 코드 검사 추이(5-36, 설계 32) — 같은 프로필·같은 폴더의 전체 검사 이력을 묶음×등급 건수로 늘어놓는다. 점수가 아니라 건수(사용자 2026-10-23).
 * 변경분 검사는 대상이 매번 달라 수만 센다. 규칙 지문이 앞 점과 다르면 표시만 하고 고쳐짐·새로 생김은 비교하지 않는다.
 * 고쳐짐·새로 생김은 (파일, 규칙) 다중집합의 차 — 줄 번호는 밀리므로 안 본다. 작성자 집계는 없다
 */
public final class Trend {

    /**
     * 실행 하나. {@code rulesChanged} — 앞 점과 지문이 다르거나(어느 쪽이든 없으면 다름) 비교할 수 없다. 첫 점은 false.
     * {@code fixed}·{@code added} — 앞 점에만 있던 수·이 점에만 있는 수. 첫 점·규칙이 바뀐 점은 null
     */
    public record Point(long runId, LocalDateTime at, int files, int lines, int suppressed, String rulesHash, boolean rulesChanged,
            int total, Map<String, Integer> byGroup, Map<String, Integer> bySeverity, Integer fixed, Integer added) {

        public Point {
            byGroup = Collections.unmodifiableMap(new TreeMap<>(byGroup));
            bySeverity = Collections.unmodifiableMap(new TreeMap<>(bySeverity));
        }
    }

    /** 폴더(파일 경로의 앞 depth 조각) 하나의 최근·직전 건수 */
    public record Folder(String folder, int now, int before) {
    }

    /** points — 오래된 것부터. skippedChangedOnly — 같은 폴더의 변경분 실행 수(추이에서 뺐다) */
    public record Report(String path, int depth, List<Point> points, List<Folder> folders, int skippedChangedOnly) {

        public Report {
            points = List.copyOf(points);
            folders = List.copyOf(folders);
        }
    }

    public static final int DEFAULT_DEPTH = 2;
    public static final int DEFAULT_LIMIT = 20;
    static final String ROOT = "(루트)";

    private Trend() {
    }

    /**
     * @param depth 폴더 묶음 조각 수(1~6)
     * @param limit 최근 몇 번까지(1~200)
     */
    public static Report of(CheckStore store, String profile, String path, int depth, int limit) throws SQLException {
        int d = Math.max(1, Math.min(6, depth));
        int lim = Math.max(1, Math.min(200, limit));
        String want = norm(path);
        List<CheckStore.RunInfo> runs = new ArrayList<>();
        int changedOnly = 0;
        for (CheckStore.RunInfo r : store.runs()) { // 최근 것부터
            if (!Objects.equals(profile, r.profile()) || !want.equals(norm(r.path()))) {
                continue;
            }
            if (r.changedOnly()) {
                changedOnly++;
            } else if (runs.size() < lim) {
                runs.add(r);
            }
        }
        Collections.reverse(runs);
        List<Point> points = new ArrayList<>();
        Map<String, Integer> prevBag = null;
        String prevHash = null;
        List<Finding> prevFindings = List.of();
        List<Finding> lastFindings = List.of();
        for (int i = 0; i < runs.size(); i++) {
            CheckStore.RunInfo r = runs.get(i);
            Map<String, Integer> byGroup = new TreeMap<>();
            Map<String, Integer> bySeverity = new TreeMap<>();
            int total = 0;
            for (Map.Entry<String, Integer> e : store.counts(r.id()).entrySet()) {
                int bar = e.getKey().indexOf('|');
                byGroup.merge(e.getKey().substring(0, bar), e.getValue(), Integer::sum);
                bySeverity.merge(e.getKey().substring(bar + 1), e.getValue(), Integer::sum);
                total += e.getValue();
            }
            List<Finding> findings = store.findings(r.id());
            Map<String, Integer> bag = bag(findings);
            boolean changed = i > 0 && (r.rulesHash() == null || prevHash == null || !r.rulesHash().equals(prevHash));
            Integer fixed = null;
            Integer added = null;
            if (i > 0 && !changed) {
                fixed = minus(prevBag, bag);
                added = minus(bag, prevBag);
            }
            points.add(new Point(r.id(), r.startedAt(), r.files(), r.lines(), r.suppressed(), r.rulesHash(), changed, total, byGroup, bySeverity,
                    fixed, added));
            prevBag = bag;
            prevHash = r.rulesHash();
            prevFindings = lastFindings;
            lastFindings = findings;
        }
        // 마무리 리뷰 — 최근 점이 규칙이 바뀐 점이면 직전 지적과 폴더도 견주지 않는다(직전 0)
        boolean lastChanged = !points.isEmpty() && points.get(points.size() - 1).rulesChanged();
        return new Report(path, d, points, folders(lastFindings, lastChanged ? List.of() : prevFindings, d), changedOnly);
    }

    /** 경로 맞추기 — 절대·정규화·슬래시·대소문자 무시(Windows), 끝 슬래시 뗌 */
    static String norm(String p) {
        if (p == null) {
            return "";
        }
        String s;
        try {
            s = Path.of(p.trim()).toAbsolutePath().normalize().toString();
        } catch (InvalidPathException e) {
            s = p.trim();
        }
        s = s.replace('\\', '/').toLowerCase(Locale.ROOT);
        while (s.length() > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static Map<String, Integer> bag(List<Finding> fs) {
        Map<String, Integer> out = new HashMap<>();
        for (Finding f : fs) {
            out.merge(f.file() + '\u0000' + f.rule(), 1, Integer::sum);
        }
        return out;
    }

    /** a 에서 b 를 뺀 다중집합의 크기 */
    private static int minus(Map<String, Integer> a, Map<String, Integer> b) {
        int n = 0;
        for (Map.Entry<String, Integer> e : a.entrySet()) {
            n += Math.max(0, e.getValue() - b.getOrDefault(e.getKey(), 0));
        }
        return n;
    }

    /** 최근·직전 지적을 폴더(앞 depth 조각, 파일 이름 뺀)로 묶는다 — 최근 내림차순, 같으면 이름순 */
    static List<Folder> folders(List<Finding> now, List<Finding> before, int depth) {
        Map<String, int[]> m = new TreeMap<>();
        for (Finding f : now) {
            m.computeIfAbsent(folder(f.file(), depth), k -> new int[2])[0]++;
        }
        for (Finding f : before) {
            m.computeIfAbsent(folder(f.file(), depth), k -> new int[2])[1]++;
        }
        List<Folder> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(new Folder(k, v[0], v[1])));
        out.sort((x, y) -> x.now() != y.now() ? Integer.compare(y.now(), x.now()) : x.folder().compareTo(y.folder()));
        return out;
    }

    static String folder(String file, int depth) {
        String[] parts = file.replace('\\', '/').split("/");
        int dirs = parts.length - 1;
        if (dirs <= 0) {
            return ROOT;
        }
        return String.join("/", java.util.Arrays.copyOfRange(parts, 0, Math.min(depth, dirs)));
    }
}
