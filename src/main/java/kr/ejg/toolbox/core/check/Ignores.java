package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 5-29 — 억제 표식 {@code tb-ignore[-next] [규칙id,…|*] [이유]}. 원문 줄에서 찾는다(주석을 지운 글이 아니라 — 표식은 주석 안에 있다).
 * 5-44b — 주석의 첫 낱말일 때만 표식이다: 주석 시작 토큰({@code //}·{@code /*}·javadoc 연속 {@code *}·{@code #}·{@code <!--}·{@code <%--}·{@code --})
 * 바로 뒤. 문자열·설명 글 가운데 낱말은 표식이 아니다(전엔 그 줄 지적을 다 지우고 「쓸모없는 표식」 을 냈다).
 * 규칙 id 를 비우거나 {@code *} 면 그 줄 전부. 이유 글은 읽지도 저장하지도 않는다(절대 규칙 3).
 */
final class Ignores {

    // 토큰 뒤 경계는 \b 가 아니라 (?![\w-]) — tb-ignore-file 같은 꼴이 「그 줄 전부」 로 읽히지 않게. 그룹 1 = 토큰, 그룹 2 = -next.
    // 정규식은 토큰만 찾고 뒤의 규칙 id 목록은 {@link #ids} 가 손으로 읽는다 — 반복을 겹친 정규식을 두지 않는다(SpotBugs REDOS)
    static final Pattern MARK = Pattern.compile("(?://|/\\*|\\*|#|<!--|<%--|--)[ \\t]*(tb-ignore(-next)?)(?![\\w-])");
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
                Set<String> rules = ids(lines[i], m.end());
                // 다음 줄이 없는 -next(끝 줄바꿈 뒤 빈 조각만 남음)는 아무 줄에도 안 먹는다 — 쓸모없는 표식
                boolean hasNext = i + 1 < lines.length && !(i + 1 == lines.length - 1 && lines[i + 1].isEmpty());
                int line = m.group(2) == null ? i + 1 : hasNext ? i + 2 : -1;
                marks.add(new Mark(i + 1, line, java.util.Collections.unmodifiableSet(rules))); // 적은 순서 그대로(발췌가 실행마다 같게)
            }
        }
        return new Ignores(marks);
    }

    /**
     * 토큰 뒤 공백·탭 하나 이상 다음의 규칙 id 목록 — 영문자로 시작해 {@code [\w.,]} 가 이어지는 한 덩이를 쉼표로 나눈다(빈 조각은 버림).
     * 공백이 없거나 {@code *} 거나 영문자로 시작하지 않으면 빈 집합(= 그 줄 전부)
     */
    static Set<String> ids(String line, int from) {
        int j = from;
        while (j < line.length() && (line.charAt(j) == ' ' || line.charAt(j) == '\t')) {
            j++;
        }
        Set<String> rules = new LinkedHashSet<>();
        if (j == from || j >= line.length() || !isAsciiLetter(line.charAt(j))) {
            return rules;
        }
        int k = j;
        while (k < line.length() && (Character.isLetterOrDigit(line.charAt(k)) && line.charAt(k) < 128 || "_.,".indexOf(line.charAt(k)) >= 0)) {
            k++;
        }
        for (String id : line.substring(j, k).split(",")) {
            if (!id.isEmpty()) {
                rules.add(id);
            }
        }
        return rules;
    }

    private static boolean isAsciiLetter(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
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
