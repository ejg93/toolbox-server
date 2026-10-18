package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 5-29 — 억제 표식 {@code tb-ignore[-next] [규칙id,…|*] [이유]}. 원문 줄에서 찾는다(주석을 지운 글이 아니라 — 표식은 주석 안에 있다).
 * 규칙 id 를 비우거나 {@code *} 면 그 줄 전부. 이유 글은 읽지도 저장하지도 않는다(절대 규칙 3).
 */
final class Ignores {

    static final Pattern MARK = Pattern.compile("\\btb-ignore(-next)?\\b(?:[ \\t]+([A-Za-z][\\w.]*(?:,[A-Za-z][\\w.]*)*|\\*))?");
    static final String UNUSED = "common.unusedIgnore";

    /** at = 표식이 있는 줄(1부터), line = 먹는 줄, rules 비면 전부 */
    record Mark(int at, int line, Set<String> rules) {
    }

    private final List<Mark> marks;
    private final boolean[] used;
    private int suppressed;

    private Ignores(List<Mark> marks) {
        this.marks = marks;
        this.used = new boolean[marks.size()];
    }

    static Ignores of(Source s) {
        List<Mark> marks = new ArrayList<>();
        String[] lines = s.text().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = MARK.matcher(lines[i]);
            while (m.find()) {
                Set<String> rules = new LinkedHashSet<>();
                if (m.group(2) != null && !m.group(2).equals("*")) {
                    rules.addAll(List.of(m.group(2).split(",")));
                }
                // 다음 줄이 없는 -next(끝 줄바꿈 뒤 빈 조각만 남음)는 아무 줄에도 안 먹는다 — 쓸모없는 표식
                boolean hasNext = i + 1 < lines.length && !(i + 1 == lines.length - 1 && lines[i + 1].isEmpty());
                int line = m.group(1) == null ? i + 1 : hasNext ? i + 2 : -1;
                marks.add(new Mark(i + 1, line, Set.copyOf(rules)));
            }
        }
        return new Ignores(marks);
    }

    boolean isEmpty() {
        return marks.isEmpty();
    }

    /** 남길 것만 돌려준다. 뺀 수는 {@link #suppressed()} 에 더하고 쓰인 표식을 기억. {@code common.unusedIgnore} 는 안 뺀다 */
    List<Finding> filter(List<Finding> in) {
        List<Finding> out = new ArrayList<>(in.size());
        for (Finding f : in) {
            boolean hit = false;
            if (!UNUSED.equals(f.rule())) {
                for (int i = 0; i < marks.size(); i++) {
                    Mark m = marks.get(i);
                    if (m.line() == f.line() && (m.rules().isEmpty() || m.rules().contains(f.rule()))) {
                        used[i] = true;
                        hit = true;
                    }
                }
            }
            if (hit) {
                suppressed++;
            } else {
                out.add(f);
            }
        }
        return out;
    }

    int suppressed() {
        return suppressed;
    }

    /** 아무것도 안 뺀 표식 — 나-7 */
    List<Mark> unused() {
        List<Mark> out = new ArrayList<>();
        for (int i = 0; i < marks.size(); i++) {
            if (!used[i]) {
                out.add(marks.get(i));
            }
        }
        return out;
    }
}
