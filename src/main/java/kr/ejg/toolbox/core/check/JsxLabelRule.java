package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * a11y.jsxInputLabel(5-50) — JSX {@code <input>} 에 레이블이 없으면 그 태그 첫 줄에 한 건. 줄 단위 정규식(5-40)이 못 보던 셋을 본다 —
 * 여러 줄에 걸친 태그 · {@code <label>} 로 감싼 입력(다음 줄이어도) · {@code {...x}} 스프레드(id 를 넘길 수 있다).
 * 레이블로 보는 것: id·title·aria-label·aria-labelledby 속성, 감싼 label. type 이 hidden·button·submit·image·reset 이면 안 본다.
 * 태그 끝은 중괄호 깊이 0 의 {@code >} — {@code onChange={e => f(e)}} 의 화살표를 끝으로 안 읽는다. 자바 규칙이라 순수본에는 없다
 */
public final class JsxLabelRule implements Rule {

    private static final Pattern INPUT = Pattern.compile("<input\\b");
    private static final Pattern SKIP_TYPE = Pattern.compile("\\btype\\s*=\\s*(?:[\"']|\\{\\s*[\"'])(?:hidden|button|submit|image|reset)[\"']");
    private static final Pattern LABELLED = Pattern.compile("\\b(?:id|title|aria-label|aria-labelledby)\\s*=");
    private static final Pattern SPREAD = Pattern.compile("\\{\\s*\\.\\.\\.");
    private static final Pattern LABEL_OPEN = Pattern.compile("<label\\b");

    private final Def def;

    public JsxLabelRule(Def def) {
        this.def = def;
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public List<Finding> apply(Source s) {
        String code = RegexRule.stripComments(s.text(), s.lang());
        if (code.length() != s.text().length()) {
            code = s.text();
        }
        String[] raw = s.text().split("\n", -1);
        List<Finding> out = new ArrayList<>();
        for (int line : flagged(code)) {
            out.add(new Finding(s.rel(), line, def.group(), def.id(), def.severity(), Finding.excerpt(raw[line - 1])));
        }
        return out;
    }

    /** 레이블 없는 {@code <input>} 의 줄(1부터) — 주석을 뺀 글에서 */
    static List<Integer> flagged(String code) {
        List<Integer> out = new ArrayList<>();
        Matcher m = INPUT.matcher(code);
        while (m.find()) {
            int start = m.start();
            int end = tagEnd(code, m.end());
            if (end < 0) {
                continue;
            }
            String tag = code.substring(start, end + 1);
            if (!SKIP_TYPE.matcher(tag).find() && !LABELLED.matcher(tag).find() && !SPREAD.matcher(tag).find() && !wrapped(code, start)) {
                out.add(lineOf(code, start));
            }
        }
        return out;
    }

    /** 중괄호 깊이 0 의 {@code >} 자리. 깊이 0 의 따옴표 값은 건너뛴다. 못 닫으면 -1 */
    private static int tagEnd(String code, int from) {
        int depth = 0;
        int i = from;
        while (i < code.length()) {
            char ch = code.charAt(i);
            if (depth == 0 && (ch == '"' || ch == '\'')) {
                i = code.indexOf(ch, i + 1);
                if (i < 0) {
                    return -1;
                }
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && (ch == '>' || ch == '<')) {
                return ch == '>' ? i : -1;
            }
            i++;
        }
        return -1;
    }

    /** 앞에 열린 {@code <label …>} 이 아직 안 닫혔나 — 가장 가까운 여는 label 이 가장 가까운 {@code </label} 보다 뒤 */
    private static boolean wrapped(String code, int at) {
        String before = code.substring(0, at);
        int open = -1;
        Matcher m = LABEL_OPEN.matcher(before);
        while (m.find()) {
            int end = tagEnd(before, m.end());
            if (end < 0 || before.charAt(end - 1) != '/') { // 스스로 닫힌 <label … /> 은 감싸지 않는다(마무리 리뷰)
                open = m.start();
            }
        }
        return open >= 0 && open > before.lastIndexOf("</label");
    }

    private static int lineOf(String code, int at) {
        int line = 1;
        for (int i = 0; i < at; i++) {
            if (code.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
