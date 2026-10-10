package kr.ejg.toolbox.core.analyze;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 화면 전수표(6-28) — 뷰(JSP)를 돌려주는 프로그램 하나가 한 행. 코드 사실만 적는다 — 화면명·유형은 추정하지 않는다(사용자 2026-10-10).
 * json·redirect·forward·class 뷰만 있는 프로그램은 행에서 빼고 제외 수로만. 메뉴 CSV(6-29)가 있으면 경로·이름·근거를 덧입힌다.
 * 계산은 여기 한 곳 — 화면·xlsx 가 같은 값. 식별자·경로·메뉴 이름만(규칙 3)
 */
public final class Screens {

    public static final String FILE_YES = "있음";
    public static final String FILE_NO = "없음";
    public static final String FILE_MANY = "여럿";
    public static final String FILE_UNKNOWN = "모름";
    /** 저장 꼴이 null(옛 실행)인 JSP 링크 */
    public static final String KIND_UNKNOWN = LinkKind.UNKNOWN_WORD;

    /** kinds — 그 JSP 가 이 URL 을 부른 꼴 전부({@link LinkKind} 코드, 저장 kind null 은 「모름」), 정렬 */
    public record Caller(String jsp, List<String> kinds) {
        public Caller {
            kinds = List.copyOf(kinds);
        }
    }

    /** menuBasis — 일치·경유·없음, 메뉴가 없으면 null. menuPaths 는 사용 Y 먼저·seq 순 */
    public record Row(int no, String module, String url, String verb, String params, String program, String file, int line, List<String> views,
            String jspFile, Map<String, String> crud, List<Caller> callers, List<String> menuPaths, String menuName, String screenId,
            String useYn, String auth, String menuBasis) {

        public Row {
            views = List.copyOf(views);
            crud = Collections.unmodifiableMap(new TreeMap<>(crud));
            callers = List.copyOf(callers);
            menuPaths = List.copyOf(menuPaths);
        }

        Row withNo(int n) {
            return new Row(n, module, url, verb, params, program, file, line, views, jspFile, crud, callers, menuPaths, menuName, screenId,
                    useYn, auth, menuBasis);
        }

        Row withMenu(List<String> paths, String name, String sid, String use, String au, String basis) {
            return new Row(no, module, url, verb, params, program, file, line, views, jspFile, crud, callers, paths, name, sid, use, au, basis);
        }
    }

    public record Excluded(String kind, int count) {
    }

    public record MenuOnly(int seq, String path, String url, String useYn) {
    }

    public record Report(List<Row> rows, List<Excluded> excluded, boolean menuLoaded, int menuRows, List<MenuOnly> menuOnly) {
        public Report {
            rows = List.copyOf(rows);
            excluded = List.copyOf(excluded);
            menuOnly = List.copyOf(menuOnly);
        }
    }

    static final List<String> EXCLUDED_ORDER = List.of("json", "redirect", "forward", "class");

    /** 꼴 코드 → 한글(xlsx·화면 같은 글). 단서로 정한 추정이다(6-27) */
    public static final Map<String, String> KIND_WORDS = kindWords();

    /** 6-30 — 꼴 글은 {@link LinkKind} 한 곳에서 + 「모름」 */
    private static Map<String, String> kindWords() {
        Map<String, String> m = new LinkedHashMap<>(LinkKind.words());
        m.put(KIND_UNKNOWN, KIND_UNKNOWN);
        return java.util.Collections.unmodifiableMap(m);
    }

    /** 부르는 화면 한 칸 — 「jsp (꼴·꼴)」 를 「; 」 로 */
    public static String callersText(List<Caller> callers) {
        return String.join("; ", callers.stream()
                .map(c -> c.jsp() + " (" + String.join("·", c.kinds().stream().map(k -> KIND_WORDS.getOrDefault(k, k)).toList()) + ")").toList());
    }

    private Screens() {
    }

    /** @param viewFiles 뷰 이름 → JSP 파일 수(옛 실행은 빈 맵 → 모름) @param menu 없으면 빈 목록 */
    public static Report of(List<AnalyzeStore.ProgramRow> programs, Map<String, Integer> viewFiles, List<AnalyzeStore.JspLink> links,
            List<Menus.Row> menu) {
        // (ㄱ) URL → 부르는 JSP(꼴 묶음)
        Map<String, Map<String, TreeSet<String>>> byUrl = new TreeMap<>();
        for (AnalyzeStore.JspLink l : links) {
            byUrl.computeIfAbsent(l.url(), k -> new TreeMap<>()).computeIfAbsent(l.jsp(), k -> new TreeSet<>())
                    .add(l.kind() == null ? KIND_UNKNOWN : l.kind());
        }
        // (ㄴ)(ㄷ) 행 · 제외
        Map<String, Integer> excluded = new LinkedHashMap<>();
        EXCLUDED_ORDER.forEach(k -> excluded.put(k, 0));
        List<Row> rows = new ArrayList<>();
        for (AnalyzeStore.ProgramRow p : programs) {
            if ("json".equals(p.kind())) {
                excluded.merge("json", 1, Integer::sum);
                continue;
            }
            List<String> vs = new ArrayList<>();
            p.views().stream().filter(v -> v.kind().equals("view")).forEach(v -> vs.add(v.name().startsWith("/") ? v.name().substring(1) : v.name()));
            if (vs.isEmpty() && !p.views().isEmpty()) {
                excluded.merge(p.views().get(0).kind(), 1, Integer::sum);
                continue;
            }
            List<Caller> callers = new ArrayList<>();
            byUrl.getOrDefault(p.url(), Map.of()).forEach((jsp, kinds) -> callers.add(new Caller(jsp, new ArrayList<>(kinds))));
            rows.add(new Row(0, CrudViews.module(p.url()), p.url(), p.verb(), p.params(), p.className() + "." + p.method(), p.file(), p.line(),
                    vs, jspFile(vs, viewFiles), p.crud(), callers, List.of(), null, null, null, null, null));
        }
        // (ㄹ) 모듈 → URL → 파일 → 줄
        rows.sort(Comparator.comparing(Row::module).thenComparing(Row::url, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Row::file, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparingInt(Row::line));
        List<Row> numbered = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            numbered.add(rows.get(i).withNo(i + 1));
        }
        List<Excluded> ex = new ArrayList<>();
        excluded.forEach((k, n) -> {
            if (n > 0) {
                ex.add(new Excluded(k, n));
            }
        });
        if (menu.isEmpty()) {
            return new Report(numbered, ex, false, 0, List.of());
        }
        // (ㅁ) 메뉴 덧입히기(6-29) — URL 정확 일치(? 뒤 뗌) → 한 단계 경유(일치 화면의 JSP 가 부름) → 없음
        Map<String, List<Menus.Row>> byMenuUrl = new LinkedHashMap<>();
        for (Menus.Row m : menu) {
            String u = Menus.normUrl(m.url());
            if (u != null) {
                byMenuUrl.computeIfAbsent(u, k -> new ArrayList<>()).add(m);
            }
        }
        List<Row> matched = new ArrayList<>();
        Map<Integer, List<Menus.Row>> direct = new LinkedHashMap<>();
        for (Row r : numbered) {
            List<Menus.Row> ms = byMenuUrl.get(Menus.normUrl(r.url()));
            if (ms != null) {
                direct.put(r.no(), ms);
                matched.add(r);
            }
        }
        List<Row> out = new ArrayList<>();
        for (Row r : numbered) {
            List<Menus.Row> ms = direct.get(r.no());
            String basis = "일치";
            if (ms == null) {
                ms = new ArrayList<>();
                for (Caller c : r.callers()) {
                    for (Row m : matched) {
                        if (servesJsp(m, c.jsp())) {
                            ms.addAll(direct.get(m.no()));
                        }
                    }
                }
                basis = ms.isEmpty() ? "없음" : "경유";
            }
            out.add(ms.isEmpty() ? r.withMenu(List.of(), null, null, null, null, basis) : withMenus(r, ms, basis));
        }
        java.util.Set<String> programUrls = new java.util.HashSet<>();
        programs.forEach(p -> {
            String u = Menus.normUrl(p.url());
            if (u != null) {
                programUrls.add(u);
            }
        });
        List<MenuOnly> only = new ArrayList<>();
        for (Menus.Row m : menu) {
            String u = Menus.normUrl(m.url());
            if (u != null && !programUrls.contains(u)) {
                only.add(new MenuOnly(m.seq(), m.path(), u, m.useYn()));
            }
        }
        return new Report(out, ex, true, menu.size(), only);
    }

    /** 사용 Y 먼저, 다음 seq — 경로는 전부(중복 없이), 이름·화면ID·사용여부·권한은 첫 것 */
    static Row withMenus(Row r, List<Menus.Row> ms, String basis) {
        Map<Integer, Menus.Row> uniq = new LinkedHashMap<>();
        ms.forEach(m -> uniq.putIfAbsent(m.seq(), m));
        List<Menus.Row> sorted = new ArrayList<>(uniq.values());
        sorted.sort(Comparator.comparing((Menus.Row m) -> "Y".equals(m.useYn()) ? 0 : 1).thenComparingInt(Menus.Row::seq));
        Menus.Row first = sorted.get(0);
        return r.withMenu(sorted.stream().map(Menus.Row::path).toList(), first.name(), first.screenId(), first.useYn(), first.auth(), basis);
    }

    /** 그 화면의 뷰가 이 JSP 를 가리키나 — 경로(.jsp 뗀 것)가 뷰 이름과 같거나 「/뷰」 로 끝남(6-26 맞춤 규칙과 같은 꼴) */
    static boolean servesJsp(Row r, String jsp) {
        String noExt = jsp.replace('\\', '/');
        noExt = noExt.endsWith(".jsp") ? noExt.substring(0, noExt.length() - 4) : noExt;
        for (String v : r.views()) {
            if (noExt.equals(v) || noExt.endsWith("/" + v)) {
                return true;
            }
        }
        return false;
    }

    /** 뷰마다 JSP 파일 수 → 있음·없음·여럿·모름. 뷰가 여럿이고 판정이 다르면 「있음·여럿·없음·모름」 순으로 잇는다 */
    static String jspFile(List<String> views, Map<String, Integer> viewFiles) {
        if (views.isEmpty()) {
            return FILE_UNKNOWN;
        }
        TreeSet<Integer> seen = new TreeSet<>();
        for (String v : views) {
            Integer n = viewFiles.get(v);
            seen.add(n == null ? 3 : n == 0 ? 2 : n == 1 ? 0 : 1);
        }
        List<String> words = List.of(FILE_YES, FILE_MANY, FILE_NO, FILE_UNKNOWN);
        return String.join("·", seen.stream().map(words::get).toList());
    }
}
