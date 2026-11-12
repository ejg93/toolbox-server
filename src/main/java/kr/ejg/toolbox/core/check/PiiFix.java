package kr.ejg.toolbox.core.check;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 설계 64 §3-8 — 코드 정리 단계 pii. {@code security.piiCard} 지적 줄의 카드번호 꼴을 끝 네 자리만 남겨 가린다.
 * 언어 무관 줄 치환 — 규칙 정규식을 그대로 쓰고, 마스크는 그 정규식·다른 pii 정규식에 다시 안 걸린다. 값이 바뀌어 기본 끔.
 */
final class PiiFix {

    static final String CARD = "security.piiCard";
    static final String MASK = "****-****-****-";
    static final String NOT_FOUND = "줄에서 번호를 못 찾음";

    private PiiFix() {
    }

    /** text 는 \n 정규화 · cur 는 지금 지적 */
    static void apply(String text, List<Finding> cur, RuleSet rules, Edits e, List<Cleanup.Skip> skips) {
        Pattern p = rules.defs().stream().filter(d -> CARD.equals(d.id())).findFirst().map(Rule.Def::pattern).orElse(null);
        if (p == null) {
            return;
        }
        Set<Integer> at = new TreeSet<>();
        cur.stream().filter(f -> CARD.equals(f.rule())).forEach(f -> at.add(f.line()));
        String[] lines = text.split("\n", -1);
        for (int n : at) {
            Matcher m = p.matcher(lines[n - 1]);
            StringBuilder sb = new StringBuilder();
            boolean any = false;
            while (m.find()) {
                any = true;
                m.appendReplacement(sb, Matcher.quoteReplacement(MASK + m.group().substring(MASK.length())));
            }
            if (!any) {
                skips.add(new Cleanup.Skip(Cleanup.PII, "줄 " + n + ": " + NOT_FOUND));
                continue;
            }
            m.appendTail(sb);
            e.replace(n, sb.toString());
        }
    }
}
