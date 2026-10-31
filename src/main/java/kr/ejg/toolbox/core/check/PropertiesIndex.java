package kr.ejg.toolbox.core.check;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 검사 폴더의 {@code *.properties} 값 — 키 → 값들(설계 44 §3-6). 알고리즘 규칙이 {@code getProperty("키", 기본값)} 을 검사 끝에 이 값으로 판정한다.
 * 값은 판정에만 쓰고 어디에도 적지 않는다(규칙 3). {@link RuleSet.Run} 이 파일마다 넘긴다
 */
final class PropertiesIndex {

    /** 키마다 값 상한 */
    static final int VALUES_MAX = 8;
    /** 아무것도 안 읽은 색인 — 링크 없이 도는 판정(단위 시험·붙여넣기)은 기본값으로 */
    static final PropertiesIndex EMPTY = new PropertiesIndex();

    private final Map<String, Set<String>> values = new HashMap<>();
    private int count;

    /**
     * 줄마다 「키 = 값」 — # · ! 주석, 양끝 공백 제거. 키 끝은 이스케이프 아닌 첫 = · : · 공백, 줄 끝 \ 는 다음 줄과 잇는다.
     * 키·값의 \: · \= · \공백 · \\ 는 벗긴다(\\uXXXX 는 안 푼다, 설계 45 §3-8)
     */
    void see(Source s) {
        count++;
        StringBuilder cur = new StringBuilder();
        for (String raw : s.text().split("\n", -1)) {
            String line = raw.strip();
            if (cur.length() == 0 && (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '!')) {
                continue;
            }
            cur.append(line);
            if (continued(cur)) {
                cur.setLength(cur.length() - 1);
                continue;
            }
            entry(cur.toString());
            cur.setLength(0);
        }
        if (cur.length() > 0) {
            entry(cur.toString());
        }
    }

    /** 줄 끝 \ 가 홀수 개 — 다음 줄로 잇는다 */
    private static boolean continued(CharSequence c) {
        int n = 0;
        for (int i = c.length() - 1; i >= 0 && c.charAt(i) == '\\'; i--) {
            n++;
        }
        return n % 2 != 0;
    }

    private void entry(String line) {
        int i = 0;
        while (i < line.length() && "=: \t".indexOf(line.charAt(i)) < 0) {
            i += line.charAt(i) == '\\' ? 2 : 1;
        }
        int k = Math.min(i, line.length());
        int j = k;
        while (j < line.length() && Character.isWhitespace(line.charAt(j))) {
            j++;
        }
        if (j < line.length() && (line.charAt(j) == '=' || line.charAt(j) == ':')) {
            j++;
        }
        String key = unescape(line.substring(0, k));
        String value = unescape(line.substring(Math.min(j, line.length())).strip());
        if (!key.isEmpty() && !value.isEmpty()) {
            Set<String> v = values.computeIfAbsent(key, x -> new LinkedHashSet<>());
            if (v.size() < VALUES_MAX) {
                v.add(value);
            }
        }
    }

    private static String unescape(String s) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            boolean escaped = s.charAt(i) == '\\' && i + 1 < s.length() && ":= \\".indexOf(s.charAt(i + 1)) >= 0;
            int at = escaped ? i + 1 : i;
            b.append(s.charAt(at));
            i = at + 1;
        }
        return b.toString();
    }

    /** 그 키의 값들 — 없으면 빈 집합 */
    Set<String> get(String key) {
        return values.getOrDefault(key, Set.of());
    }

    int files() {
        return count;
    }
}
