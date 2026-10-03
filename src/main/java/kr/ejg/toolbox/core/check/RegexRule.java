package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 줄 단위 정규식 규칙(ㄱ·ㄹ·ㅁ·ㅇ). {@code skipComments} 면 언어별 주석을 공백으로 지운 글에 맞춘다 — 줄 번호는 그대로.
 * {@code params.max} 가 있으면 건수 규칙이다: 파일 안 맞은 수가 max 를 넘을 때 첫 자리에 한 건(JSP 스크립틀릿 블록 수).
 */
public final class RegexRule implements Rule {

    private final Def def;
    private final Pattern pattern;

    public RegexRule(Def def) {
        this.def = def;
        this.pattern = def.pattern();
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public List<Finding> apply(Source s) {
        String[] raw = s.text().split("\n", -1);
        String[] lines = Boolean.TRUE.equals(def.skipComments()) ? stripComments(s.text(), s.lang()).split("\n", -1) : raw;
        List<Finding> out = new ArrayList<>();
        Object max = def.params().get("max");
        if (max != null) {
            int limit = Integer.parseInt(max.toString());
            int count = 0;
            int first = -1;
            for (int i = 0; i < lines.length; i++) {
                Matcher m = pattern.matcher(lines[i]);
                while (m.find()) {
                    count++;
                    first = first < 0 ? i : first;
                }
            }
            if (count > limit) {
                out.add(new Finding(s.rel(), first + 1, def.group(), def.id(), def.severity(), count + "개 (기준 " + limit + ")"));
            }
            return out;
        }
        for (int i = 0; i < lines.length; i++) {
            if (pattern.matcher(lines[i]).find()) {
                out.add(new Finding(s.rel(), i + 1, def.group(), def.id(), def.severity(), Finding.excerpt(raw[i])));
            }
        }
        return out;
    }

    /**
     * 주석을 공백으로 — 줄바꿈은 남긴다. java·js 꼴은 {@code //}·블록 주석(따옴표 셋 안은 둔다), jsp 는 {@code <%-- --%>}·{@code <!-- -->},
     * xml·html 은 {@code <!-- -->}, properties·yaml 은 줄머리 {@code #}(properties 는 {@code !} 도), sql 은 {@code --}·블록 주석.
     * 정규식 리터럴은 모른다 — 그 안의 {@code //} 를 주석으로 볼 수 있다(실패 사다리: 그 규칙만 skipComments 끔).
     * 프로그램 분석(6-1)도 SQL 주석을 이것으로 지운다
     */
    public static String stripComments(String text, String lang) {
        return switch (lang == null ? "" : lang) {
            case "java", "js", "jsx", "ts", "tsx", "mjs", "cjs", "css", "scss", "kt", "groovy" -> cLike(text, true);
            case "sql" -> cLike(text, false);
            case "jsp", "jspf", "tag" -> blank(blank(text, "<%--", "--%>"), "<!--", "-->");
            case "xml", "html", "htm", "xhtml", "vue" -> blank(text, "<!--", "-->");
            case "properties" -> lineComments(text, "#!");
            case "yml", "yaml", "sh" -> lineComments(text, "#");
            default -> text;
        };
    }

    private static String cLike(String s, boolean slashes) {
        StringBuilder sb = new StringBuilder(s.length());
        char quote = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            char n = i + 1 < s.length() ? s.charAt(i + 1) : 0;
            if (quote != 0) {
                sb.append(c);
                if (slashes && c == '\\' && n != 0) {
                    sb.append(n);
                    i += 2;
                    continue;
                }
                if (c == quote || c == '\n' && quote != '`') {
                    quote = 0;
                }
                i++;
            } else if (c == '"' || c == '\'' || c == '`' && slashes) {
                quote = c;
                sb.append(c);
                i++;
            } else if (slashes && c == '/' && n == '/' || !slashes && c == '-' && n == '-') {
                while (i < s.length() && s.charAt(i) != '\n') {
                    sb.append(' ');
                    i++;
                }
            } else if (c == '/' && n == '*') {
                int e = s.indexOf("*/", i + 2);
                int end = e < 0 ? s.length() : e + 2;
                for (; i < end; i++) {
                    sb.append(s.charAt(i) == '\n' ? '\n' : ' ');
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static String blank(String s, String open, String close) {
        StringBuilder sb = new StringBuilder(s);
        int from = 0;
        int o;
        while ((o = s.indexOf(open, from)) >= 0) {
            int e = s.indexOf(close, o + open.length());
            int end = e < 0 ? s.length() : e + close.length();
            for (int i = o; i < end; i++) {
                if (s.charAt(i) != '\n') {
                    sb.setCharAt(i, ' ');
                }
            }
            from = end;
        }
        return sb.toString();
    }

    private static String lineComments(String s, String marks) {
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n", -1)) {
            String t = line.stripLeading();
            sb.append(!t.isEmpty() && marks.indexOf(t.charAt(0)) >= 0 ? " ".repeat(line.length()) : line).append('\n');
        }
        return sb.substring(0, sb.length() - 1);
    }
}
