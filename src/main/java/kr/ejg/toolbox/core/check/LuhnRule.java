package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 설계 65 — kind luhn. 정규식이 잡은 숫자열 가운데 Luhn 검사를 지나는 것만 지적한다(대시 없는 카드번호). 줄마다 한 건 */
final class LuhnRule implements Rule {

    static final String CARD_PLAIN = "security.piiCardPlain";

    private final Def ruleDef;
    private final Pattern pattern;

    LuhnRule(Def def) {
        this.ruleDef = def;
        this.pattern = def.pattern();
    }

    @Override
    public Def def() {
        return ruleDef;
    }

    @Override
    public List<Finding> apply(Source s) {
        String[] raw = s.text().split("\n", -1);
        String bare = Boolean.TRUE.equals(ruleDef.skipComments()) ? RegexRule.stripComments(s.text(), s.lang()) : s.text();
        String[] code = bare.length() == s.text().length() ? bare.split("\n", -1) : raw;
        List<Finding> out = new ArrayList<>();
        for (int i = 0; i < raw.length && i < code.length; i++) {
            Matcher m = pattern.matcher(code[i]);
            while (m.find()) {
                if (valid(m.group())) {
                    out.add(new Finding(s.rel(), i + 1, ruleDef.group(), ruleDef.id(), ruleDef.severity(), Finding.excerpt(raw[i])));
                    break;
                }
            }
        }
        return out;
    }

    /** Luhn — 오른쪽에서 짝수째 자리를 두 배(9 넘으면 −9)해 더한 합이 10 의 배수 */
    static boolean valid(String digits) {
        int sum = 0;
        boolean twice = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (twice) {
                d = d * 2 > 9 ? d * 2 - 9 : d * 2;
            }
            sum += d;
            twice = !twice;
        }
        return sum % 10 == 0;
    }
}
