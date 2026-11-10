package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 코드 정리 마크업(설계 60·61) — 지적 줄을 줄 안에서 고친다: {@code <html>} 에 lang · target=_blank 인 {@code <a>} 에 rel(tsx 는 줄 단위, JSP·HTML 은 BlankRelRule 이 준 태그 자리) */
final class MarkupFix {
    static final String HTML_LANG = "a11y.htmlLang";
    static final String BLANK = "tsx.blankNoopener";
    static final String BLANK_JSP = "jsp.blankNoopener";
    private static final String LANG = " lang=\"ko\"";
    private static final String REL = " rel=\"noopener noreferrer\"";
    private static final String HTML_OPEN = "<html";
    private static final String MULTI_LINE = "여러 줄 태그";
    private static final String BARE = "따옴표 없는 target — 손으로";
    private static final String REL_WORDS = "noopener noreferrer";
    private static final String TARGET_SHAPE = "target 꼴이 달라 못 고침";
    /** 줄 머리(공백 뒤)가 <html 이고 바로 뒤가 공백이나 > — <html:form>(Struts)·문자열 안 "<html>" 은 아니다 */
    private static final Pattern HTML_HEAD = Pattern.compile("^\\s*<html(?=[\\s>])");
    private static final Pattern LANG_ATTR = Pattern.compile("\\blang\\s*=");
    private static final Pattern A_TAG = Pattern.compile("<a\\b[^<]*");
    private static final Pattern TARGET = Pattern.compile("\\btarget\\s*=\\s*(?:\"_blank\"|'_blank'|\\{\\s*[\"']_blank[\"']\\s*\\})");
    private static final Pattern REL_ATTR = Pattern.compile("\\brel\\s*=");
    /** 태그를 닫는 > — 화살표 => 의 > 는 아니다 */
    private static final Pattern TAG_END = Pattern.compile("(?<!=)>");
    private static final Pattern SCRIPT_OPEN = Pattern.compile("(?i)<script\\b");
    private static final Pattern SCRIPT_CLOSE = Pattern.compile("(?i)</script\\s*>");

    private MarkupFix() {
    }

    static void apply(Source s, List<Finding> cur, Set<String> targets, Cleanup.Edits e, List<Cleanup.Skip> skips) {
        String[] lines = s.text().split("\n", -1);
        Map<Integer, Set<String>> byLine = new TreeMap<>();
        cur.stream().filter(f -> targets.contains(f.rule())).forEach(f -> byLine.computeIfAbsent(f.line(), k -> new TreeSet<>()).add(f.rule()));
        Map<Integer, String> out = new TreeMap<>();
        if (targets.contains(BLANK_JSP)) {
            tagRel(s, lines, byLine, out, skips);
        }
        byLine.forEach((no, rules) -> {
            String line = out.getOrDefault(no, lines[no - 1]);
            line = rules.contains(BLANK) ? addRel(line, no, skips) : line;
            out.put(no, rules.contains(HTML_LANG) ? addLang(line, no, lines, skips) : line);
        });
        out.forEach((no, text) -> {
            if (!text.equals(lines[no - 1])) {
                e.replace(no, text);
            }
        });
    }

    /** jsp.blankNoopener — 지적이 선 태그마다 target 값 바로 뒤에 rel(값과 같은 따옴표). 뒤에서부터 끼워 같은 줄 앞쪽 자리가 안 밀린다 */
    private static void tagRel(Source s, String[] lines, Map<Integer, Set<String>> byLine, Map<Integer, String> out, List<Cleanup.Skip> skips) {
        String text = s.text();
        List<BlankRelRule.Tag> tags = new ArrayList<>(BlankRelRule.tags(text, s.lang()));
        java.util.Collections.reverse(tags);
        for (BlankRelRule.Tag t : tags) {
            if (!byLine.getOrDefault(t.line(), Set.of()).contains(BLANK_JSP)) {
                continue; // 억제 표식으로 눌린 태그
            }
            if (t.quote() == 0) {
                skip(skips, t.line(), BARE);
                continue;
            }
            int start = text.lastIndexOf('\n', t.at() - 1) + 1; // target 이 있는 줄의 첫 자리
            int no = 1 + (int) text.substring(0, start).chars().filter(c -> c == '\n').count();
            String line = out.getOrDefault(no, lines[no - 1]);
            int col = t.at() - start;
            out.put(no, line.substring(0, col) + " rel=" + t.quote() + REL_WORDS + t.quote() + line.substring(col));
        }
    }

    /** {@code <html} 바로 뒤에 lang — 줄 머리·같은 줄에서 닫힘·script 밖일 때만 */
    private static String addLang(String line, int no, String[] lines, List<Cleanup.Skip> skips) {
        Matcher m = HTML_HEAD.matcher(line);
        if (!m.find()) {
            skip(skips, no, "줄 머리가 <html> 이 아니다");
            return line;
        }
        int close = line.indexOf('>', m.end());
        if (close < 0) {
            skip(skips, no, MULTI_LINE);
            return line;
        }
        if (LANG_ATTR.matcher(line.substring(m.end(), close)).find()) {
            skip(skips, no, "lang 속성이 이미 있다");
            return line;
        }
        String before = String.join("\n", java.util.Arrays.asList(lines).subList(0, no - 1));
        if (count(SCRIPT_OPEN, before) > count(SCRIPT_CLOSE, before)) {
            skip(skips, no, "script 안 <html>");
            return line;
        }
        int at = line.indexOf(HTML_OPEN) + HTML_OPEN.length();
        return line.substring(0, at) + LANG + line.substring(at);
    }

    /** target=_blank 인 {@code <a>} 조각마다(오른쪽부터) rel 이 없고 그 줄에서 닫히면 target 뒤에 rel */
    private static String addRel(String line, int no, List<Cleanup.Skip> skips) {
        List<int[]> spans = new ArrayList<>();
        Matcher a = A_TAG.matcher(line);
        while (a.find()) {
            spans.add(new int[] {a.start(), a.end()});
        }
        StringBuilder sb = new StringBuilder(line);
        boolean fixed = false;
        boolean sawTarget = false;
        for (int i = spans.size() - 1; i >= 0; i--) {
            String frag = line.substring(spans.get(i)[0], spans.get(i)[1]);
            Matcher t = TARGET.matcher(frag);
            boolean hit = t.find();
            sawTarget |= hit;
            if (hit && !REL_ATTR.matcher(frag).find() && TAG_END.matcher(frag).find()) {
                sb.insert(spans.get(i)[0] + t.end(), REL);
                fixed = true;
            }
        }
        if (!fixed) {
            skip(skips, no, sawTarget ? MULTI_LINE : TARGET_SHAPE);
        }
        return sb.toString();
    }

    private static int count(Pattern p, String text) {
        int n = 0;
        Matcher m = p.matcher(text);
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static void skip(List<Cleanup.Skip> skips, int no, String why) {
        skips.add(new Cleanup.Skip(Cleanup.MARKUP_STAGE, "줄 " + no + ": " + why));
    }
}
