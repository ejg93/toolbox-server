package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 코드 정리 마크업(설계 60·61) — 지적 줄을 줄 안에서 고친다: {@code <html>} 에 lang · target=_blank 인 {@code <a>} 에 rel(tsx 는 줄 단위, JSP·HTML 은 BlankRelRule 이 준 태그 자리) · th 에 scope(ThScopeFix) */
final class MarkupFix {
    static final String HTML_LANG = "a11y.htmlLang";
    static final String BLANK = "tsx.blankNoopener";
    static final String BLANK_JSP = "jsp.blankNoopener";
    static final String RAW = "<pre>·<textarea> 안 글 — 마크업이 아니다";
    private static final String LANG = " lang=\"ko\"";
    private static final String REL = " rel=\"noopener noreferrer\"";
    private static final String HTML_OPEN = "<html";
    private static final String MULTI_LINE = "여러 줄 태그";
    private static final String BARE = "따옴표 없는 target — 손으로";
    private static final String REL_WORDS = "noopener noreferrer";
    private static final String TARGET_SHAPE = "target 꼴이 달라 못 고침";
    private static final String SCOPE_ATTR = " scope=\"";
    /** 줄 머리(공백 뒤)가 <html 이고 바로 뒤가 공백이나 > — <html:form>(Struts)·문자열 안 "<html>" 은 아니다 */
    private static final Pattern HTML_HEAD = Pattern.compile("^\\s*<html(?=[\\s>])");
    private static final Pattern LANG_ATTR = Pattern.compile("\\blang\\s*=");
    private static final Pattern A_TAG = Pattern.compile("<a\\b[^<]*");
    /** 묶음 1 — 값이 작은따옴표면 그 따옴표(5-141: rel 도 같은 따옴표 — 작은따옴표 문자열 안 a 태그가 안 깨진다) */
    private static final Pattern TARGET = Pattern.compile("\\btarget\\s*=\\s*(?:\"_blank\"|(')_blank'|\\{\\s*[\"']_blank[\"']\\s*\\})");
    private static final String REL_SINGLE = " rel='noopener noreferrer'";
    private static final Pattern REL_ATTR = Pattern.compile("\\brel\\s*=");
    private static final Pattern SCRIPT_OPEN = Pattern.compile("(?i)<script\\b");
    private static final Pattern SCRIPT_CLOSE = Pattern.compile("(?i)</script\\s*>");

    private MarkupFix() {
    }

    /** 글 자리에 끼울 조각 — rel·scope 를 한 목록으로 모아 at 내림차순으로 끼운다 */
    private record Insert(int at, String text) {
    }

    static void apply(Source s, List<Finding> cur, Set<String> targets, Edits e, List<Cleanup.Skip> skips) {
        String[] lines = s.text().split("\n", -1);
        boolean[] raw = Zones.rawLines(s.text(), s.lang(), List.of("textarea")); // 5-116 — pre 안은 마크업이다
        int[] starts = starts(lines);
        Set<Integer> rawSkipped = new TreeSet<>();
        Map<Integer, Set<String>> byLine = new TreeMap<>();
        cur.stream().filter(f -> targets.contains(f.rule())).forEach(f -> byLine.computeIfAbsent(f.line(), k -> new TreeSet<>()).add(f.rule()));
        Map<Integer, String> out = new TreeMap<>();
        List<Insert> ins = new ArrayList<>();
        if (targets.contains(BLANK_JSP)) {
            ins.addAll(rels(s, byLine, skips));
        }
        if (targets.contains(ThScopeFix.TH_SCOPE)) {
            ins.addAll(scopes(s, skips));
        }
        ins.removeIf(i -> rawLine(raw, lineOf(starts, i.at()), rawSkipped, skips));
        insertAll(lines, starts, ins, out);
        byLine.forEach((no, rules) -> {
            if (rawLine(raw, no - 1, rawSkipped, skips)) {
                return;
            }
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

    /** jsp.blankNoopener — 지적이 선 태그마다 target 값 바로 뒤에 rel(값과 같은 따옴표) */
    private static List<Insert> rels(Source s, Map<Integer, Set<String>> byLine, List<Cleanup.Skip> skips) {
        List<Insert> out = new ArrayList<>();
        for (BlankRelRule.Tag t : BlankRelRule.tags(s.text(), s.lang())) {
            if (!byLine.getOrDefault(t.line(), Set.of()).contains(BLANK_JSP)) {
                continue; // 억제 표식으로 눌린 태그
            }
            if (t.quote() == 0) {
                skip(skips, t.line(), BARE);
                continue;
            }
            out.add(new Insert(t.at(), " rel=" + t.quote() + REL_WORDS + t.quote()));
        }
        return out;
    }

    /** a11y.thScope — ThScopeFix 가 준 자리에 scope. 표마다의 건너뜀 사유를 그대로 옮긴다 */
    private static List<Insert> scopes(Source s, List<Cleanup.Skip> skips) {
        ThScopeFix.Scan sc = ThScopeFix.scan(s.text(), s.lang());
        sc.skips().forEach(k -> skips.add(new Cleanup.Skip(Cleanup.MARKUP_STAGE, k)));
        List<Insert> out = new ArrayList<>();
        sc.ths().forEach(t -> out.add(new Insert(t.at(), SCOPE_ATTR + t.scope() + "\"")));
        return out;
    }

    /** at 오름차순으로 줄마다 한 번에 잇는다(설계 70 §3-8 — 선형) — 줄은 out 에 모아 두고 apply 끝에 한 번 쓴다 */
    private static void insertAll(String[] lines, int[] starts, List<Insert> ins, Map<Integer, String> out) {
        ins.sort(Comparator.comparingInt(Insert::at));
        int k = 0;
        while (k < ins.size()) {
            int idx = lineOf(starts, ins.get(k).at());
            String line = out.getOrDefault(idx + 1, lines[idx]);
            StringBuilder sb = new StringBuilder(line.length() + 64);
            int prev = 0;
            for (; k < ins.size() && lineOf(starts, ins.get(k).at()) == idx; k++) {
                int col = ins.get(k).at() - starts[idx];
                sb.append(line, prev, col).append(ins.get(k).text());
                prev = col;
            }
            out.put(idx + 1, sb.append(line, prev, line.length()).toString());
        }
    }

    /** 줄 시작 글 자리(0 기준 줄) */
    private static int[] starts(String... lines) {
        int[] st = new int[lines.length];
        for (int i = 1; i < lines.length; i++) {
            st[i] = st[i - 1] + lines[i - 1].length() + 1;
        }
        return st;
    }

    private static int lineOf(int[] starts, int at) {
        int r = Arrays.binarySearch(starts, at);
        return r >= 0 ? r : -r - 2;
    }

    /** 날글 줄이면 사유(줄마다 한 번)를 남기고 참 */
    private static boolean rawLine(boolean[] raw, int idx, Set<Integer> rawSkipped, List<Cleanup.Skip> skips) {
        if (!raw[idx]) {
            return false;
        }
        if (rawSkipped.add(idx)) {
            skip(skips, idx + 1, RAW);
        }
        return true;
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
            if (hit && !REL_ATTR.matcher(frag).find() && closes(frag)) {
                sb.insert(spans.get(i)[0] + t.end(), t.group(1) != null ? REL_SINGLE : REL);
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

    /**
     * 5-156(E4-07) — 태그를 닫는 > 가 이 조각에 있나 — 따옴표 값·{ } 식 밖의 > 이고 앞 글자가 = 가 아닌 것(화살표 =>).
     * 따옴표가 안 닫히면 거짓(여러 줄 태그로 본다 — 안전한 쪽)
     */
    private static boolean closes(String frag) {
        char quote = 0;
        int brace = 0;
        for (int i = 0; i < frag.length(); i++) {
            char c = frag.charAt(i);
            if (quote != 0) {
                quote = c == quote ? 0 : quote;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '{' || c == '}') {
                brace += c == '{' ? 1 : -1;
            } else if (c == '>' && brace <= 0 && (i == 0 || frag.charAt(i - 1) != '=')) {
                return true;
            }
        }
        return false;
    }
}
