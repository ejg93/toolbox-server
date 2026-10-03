package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 「있어야 할 것이 없다」 규칙({@code kind: absent}, 5-17). 파일에 {@code params.when} 이 맞는 줄이 있는데 {@code regex} 가 한 줄도
 * 안 맞으면 첫 {@code when} 줄에 한 건 — 표에 {@code <th>} 는 있는데 {@code <caption>} 이 어디에도 없다, {@code <web-app>} 인데
 * {@code <error-page>} 가 없다. 줄 정규식으로는 「다른 줄에 있음」 을 못 재서 따로 둔다. {@code skipComments} 면 주석을 지운 글에서 본다.
 * 순수본에는 안 간다 — 순수본 규칙 JSON 은 {@code kind: regex} 만 뜬다(5-9)
 */
public final class AbsentRule implements Rule {

    private final Def def;
    private final Pattern present;
    private final Pattern when;

    public AbsentRule(Def def) {
        this.def = def;
        this.present = def.pattern();
        this.when = when(def);
    }

    /** {@code params.when} 을 규칙 flags 로 컴파일 — 없으면 설정 오류 */
    static Pattern when(Def def) {
        Object w = def.params().get("when");
        if (w == null || w.toString().isBlank()) {
            throw new IllegalArgumentException(def.id() + ": kind absent 는 params.when 이 있어야 한다");
        }
        Def probe = new Def(def.id(), def.group(), def.severity(), def.kind(), def.globs(), w.toString(), def.flags(), def.skipComments(),
                def.message(), def.params(), def.enabled());
        return probe.pattern();
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public List<Finding> apply(Source s) {
        String[] raw = s.text().split("\n", -1);
        String[] lines = Boolean.TRUE.equals(def.skipComments()) ? RegexRule.stripComments(s.text(), s.lang()).split("\n", -1) : raw;
        int first = -1;
        for (int i = 0; i < lines.length; i++) {
            if (present.matcher(lines[i]).find()) {
                return List.of();
            }
            if (first < 0 && when.matcher(lines[i]).find()) {
                first = i;
            }
        }
        List<Finding> out = new ArrayList<>();
        if (first >= 0) {
            out.add(new Finding(s.rel(), first + 1, def.group(), def.id(), def.severity(), Finding.excerpt(raw[first])));
        }
        return out;
    }
}
