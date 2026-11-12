package kr.ejg.toolbox.core.check;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 설계 64 §3-8 — 코드 정리 단계 pii. {@code security.piiCard} 지적 줄의 카드번호 꼴을 끝 네 자리만 남겨 가린다.
 * 설계 65 5-97 — {@code security.piiCardPlain}(대시 없음) 은 Luhn 을 지나는 일치만 앞 열둘을 가린다. 한 줄에 둘 다면 이어 적용해 한 번 바꾼다.
 * 언어 무관 줄 치환 — 규칙 정규식을 그대로 쓰고, 마스크는 그 정규식·다른 pii 정규식에 다시 안 걸린다. 값이 바뀌어 기본 끔.
 */
final class PiiFix {

    static final String CARD = "security.piiCard";
    static final String MASK = "****-****-****-";
    static final String MASK_PLAIN = "************";
    static final String NOT_FOUND = "줄에서 번호를 못 찾음";

    private PiiFix() {
    }

    /** text 는 \n 정규화 · cur 는 지금 지적 · targets 는 고른 규칙 가운데 지적이 있는 것 */
    static void apply(String text, List<Finding> cur, Set<String> targets, RuleSet rules, Edits e, List<Cleanup.Skip> skips) {
        String[] lines = text.split("\n", -1);
        Map<Integer, String> done = new TreeMap<>();
        for (String id : List.of(CARD, LuhnRule.CARD_PLAIN)) {
            if (targets.contains(id)) {
                cover(id, cur, rules, lines, done, skips);
            }
        }
        done.forEach(e::replace);
    }

    private static void cover(String id, List<Finding> cur, RuleSet rules, String[] lines, Map<Integer, String> done, List<Cleanup.Skip> skips) {
        Pattern p = rules.defs().stream().filter(d -> id.equals(d.id())).findFirst().map(Rule.Def::pattern).orElse(null);
        if (p == null) {
            return;
        }
        boolean plain = LuhnRule.CARD_PLAIN.equals(id);
        Set<Integer> at = new TreeSet<>();
        cur.stream().filter(f -> id.equals(f.rule())).forEach(f -> at.add(f.line()));
        for (int n : at) {
            Matcher m = p.matcher(done.getOrDefault(n, lines[n - 1]));
            StringBuilder sb = new StringBuilder();
            boolean any = false;
            while (m.find()) {
                String g = m.group();
                String to = g;
                if (!plain) {
                    to = MASK + g.substring(MASK.length());
                } else if (LuhnRule.valid(g)) {
                    to = MASK_PLAIN + g.substring(MASK_PLAIN.length());
                }
                any |= !to.equals(g);
                m.appendReplacement(sb, Matcher.quoteReplacement(to));
            }
            if (!any) {
                skips.add(new Cleanup.Skip(Cleanup.PII, "줄 " + n + ": " + NOT_FOUND));
                continue;
            }
            m.appendTail(sb);
            done.put(n, sb.toString());
        }
    }
}
