package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 파일 사이 중복 덩어리 {@code metric.duplicate}(5-35b, 설계 32). 줄을 정규화(빈 줄·주석·import·package·홑 괄호를 빼고 공백을 하나로)해
 * {@code params.minLines} 개씩 FNV-1a 64 비트로 접고, 처음 본 자리를 기억한다. 같은 블록을 다시 만나면 두 쪽이 같은 동안 늘려 한 건 —
 * 뒤 자리에 낸다(뒤 파일이 결과 밖이면 앞 자리에). 같은 파일 안 겹치는 블록은 뺀다. 줄 글은 해시만 들고 발췌는 상대 경로·줄·줄 수(규칙 3).
 * 검사 한 번(Run)마다 새로 만든다.
 */
final class Duplicates {

    /** 처음 본 블록 표 상한 — 넘으면 더 안 넣고 이미 든 것과는 계속 맞댄다(파일 2만 개 ≈ 블록 60만, 설계 32 §2) */
    static final int MAX_BLOCKS = 2_000_000;
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final Rule.Def def;
    private final int minLines;
    private final List<String> files = new ArrayList<>();
    private final List<int[]> hashes = new ArrayList<>();
    private final List<int[]> lines = new ArrayList<>();
    /** 결과에 낼 파일인가 — apply 는 true, 변경분 검사의 안 바뀐 파일(observe)은 false(상대만 된다) */
    private final List<Boolean> report = new ArrayList<>();
    private final Map<Long, Long> first = new HashMap<>();
    private final List<Finding> out = new ArrayList<>();

    Duplicates(Rule.Def def) {
        this.def = def;
        Object v = def.params().get("minLines");
        int n = 30;
        if (v instanceof Number x) {
            n = x.intValue();
        } else if (v != null) {
            try {
                n = Integer.parseInt(v.toString().trim());
            } catch (NumberFormatException e) {
                n = 30;
            }
        }
        this.minLines = Math.max(2, n);
    }

    Rule.Def def() {
        return def;
    }

    /** 파일 하나를 넣는다. {@code reportable} 이 false 면 결과는 안 내고 상대만 된다 */
    void see(Source s, boolean reportable) {
        String[] raw = s.text().split("\n", -1);
        List<Integer> hs = new ArrayList<>();
        List<Integer> ns = new ArrayList<>();
        boolean inBlock = false;
        for (int i = 0; i < raw.length; i++) {
            String l = raw[i].strip();
            if (inBlock) {
                inBlock = !l.contains("*/");
                continue;
            }
            if (l.startsWith("/*")) {
                inBlock = !l.contains("*/");
                continue;
            }
            if (l.isEmpty() || l.startsWith("//") || l.startsWith("*") || l.startsWith("import ") || l.startsWith("package ")
                    || l.equals("{") || l.equals("}") || l.equals("};")) {
                continue;
            }
            hs.add(l.replaceAll("\\s+", " ").hashCode());
            ns.add(i + 1);
        }
        int f = files.size();
        int[] h = hs.stream().mapToInt(Integer::intValue).toArray();
        files.add(s.rel());
        hashes.add(h);
        lines.add(ns.stream().mapToInt(Integer::intValue).toArray());
        report.add(reportable);
        int i = 0;
        while (i + minLines <= h.length) {
            long bh = FNV_OFFSET;
            for (int k = 0; k < minLines; k++) {
                bh = (bh ^ h[i + k]) * FNV_PRIME;
            }
            Long prev = first.get(bh);
            if (prev == null) {
                if (first.size() < MAX_BLOCKS) {
                    first.put(bh, (long) f << 32 | i);
                }
                i++;
                continue;
            }
            int pf = (int) (prev >>> 32);
            int pi = (int) (prev & 0xffffffffL);
            int[] ph = hashes.get(pf);
            if (pf == f && Math.abs(pi - i) < minLines || !same(h, i, ph, pi, minLines)) {
                i++;
                continue;
            }
            int n = minLines;
            while (i + n < h.length && pi + n < ph.length && h[i + n] == ph[pi + n] && !(pf == f && pi + n >= i)) {
                n++;
            }
            add(f, i, pf, pi, n);
            i += n;
        }
    }

    /** 해시 충돌 거르기 — 줄 해시가 실제로 같은가 */
    private static boolean same(int[] a, int ai, int[] b, int bi, int n) {
        for (int k = 0; k < n; k++) {
            if (a[ai + k] != b[bi + k]) {
                return false;
            }
        }
        return true;
    }

    private void add(int f, int i, int pf, int pi, int n) {
        if (report.get(f)) {
            out.add(finding(f, i, pf, pi, n));
        } else if (report.get(pf)) {
            out.add(finding(pf, pi, f, i, n));
        }
    }

    private Finding finding(int f, int i, int of, int oi, int n) {
        String excerpt = Finding.excerpt(files.get(of) + ":" + lines.get(of)[oi] + " 과 같음 — 정규화 " + n + "줄");
        return new Finding(files.get(f), lines.get(f)[i], def.group(), def.id(), def.severity(), excerpt);
    }

    /** 모은 지적 — 한 번만 */
    List<Finding> finish() {
        List<Finding> r = new ArrayList<>(out);
        out.clear();
        return r;
    }
}
