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

    /** 줄마다 「키 = 값」(첫 = 또는 :) — # · ! 주석, 양끝 공백 제거. 줄 이음(\)은 받지 않는다 */
    void see(Source s) {
        count++;
        for (String raw : s.text().split("\n", -1)) {
            String line = raw.strip();
            if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '!') {
                continue;
            }
            int eq = line.indexOf('=');
            int colon = line.indexOf(':');
            int cut = eq < 0 ? colon : colon < 0 ? eq : Math.min(eq, colon);
            if (cut > 0) {
                Set<String> v = values.computeIfAbsent(line.substring(0, cut).strip(), k -> new LinkedHashSet<>());
                if (v.size() < VALUES_MAX) {
                    v.add(line.substring(cut + 1).strip());
                }
            }
        }
    }

    /** 그 키의 값들 — 없으면 빈 집합 */
    Set<String> get(String key) {
        return values.getOrDefault(key, Set.of());
    }

    int files() {
        return count;
    }
}
