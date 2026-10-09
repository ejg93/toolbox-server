package kr.ejg.toolbox.core.analyze;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import kr.ejg.toolbox.core.text.Csv;

/**
 * 메뉴·화면 목록 CSV(6-29) — 경로 한 열(「대 > 중 > 소」, 깊이 제한 없음)과 URL. 트리 재귀는 사용자가 뽑는 SQL 쪽, 행 순서가 트리 순서.
 * 열은 {@link Csv#guess} 로 헤더 이름을 맞춘다(ColumnInputs.fromCsv 와 같은 결). 메뉴 이름·URL 만(규칙 3)
 */
public final class Menus {

    static final List<String> PATH = List.of("메뉴경로", "메뉴", "경로", "menupath", "menu", "path");
    static final List<String> URL = List.of("url", "주소", "링크");
    static final List<String> SCREEN = List.of("화면id", "화면번호", "screenid", "screen_id");
    static final List<String> USE = List.of("사용여부", "useyn", "use_yn", "use_at", "사용");
    static final List<String> AUTH = List.of("권한", "authority", "auth", "role");
    static final Set<String> YES = Set.of("Y", "1", "TRUE", "사용", "예");
    static final Set<String> NO = Set.of("N", "0", "FALSE", "미사용", "아니오");

    /** seq — CSV 데이터 행 번호(1부터, 트리 순서). path 는 「대 > 중 > 소」, name 은 마지막 마디. url 없으면 null(중간 메뉴) */
    public record Row(int seq, String path, String name, String url, String screenId, String useYn, String auth) {
    }

    /** withUrl — URL 있는 행 수(전수 이음에 쓰는 것). warnings — 한 줄씩(사용여부 값이 Y/N 밖 · 경로 빈 행) */
    public record Parsed(List<Row> rows, int withUrl, List<String> warnings) {
        public Parsed {
            rows = List.copyOf(rows);
            warnings = List.copyOf(warnings);
        }
    }

    private Menus() {
    }

    public static Parsed parse(String csv) {
        List<List<String>> rows = Csv.parse(csv == null ? "" : csv);
        if (rows.size() < 2) {
            throw new IllegalArgumentException("데이터가 부족하다 — 헤더와 한 줄 이상");
        }
        List<String> h = rows.get(0).stream().map(x -> x == null ? "" : x.toLowerCase(Locale.ROOT)).toList();
        int pi = Csv.guess(h, PATH, true);
        int ui = Csv.guess(h, URL, true);
        if (pi < 0 || ui < 0) {
            throw new IllegalArgumentException("메뉴 경로 열(" + String.join("·", PATH) + ")과 URL 열(" + String.join("·", URL) + ")이 있어야 한다");
        }
        int si = Csv.guess(h, SCREEN, true);
        int yi = Csv.guess(h, USE, true);
        int ai = Csv.guess(h, AUTH, true);
        List<Row> out = new ArrayList<>();
        int withUrl = 0;
        int noPath = 0;
        int oddUse = 0;
        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (r.stream().allMatch(x -> x == null || x.isBlank())) {
                continue;
            }
            List<String> parts = Arrays.stream(cell(r, pi).split("[>＞]")).map(String::trim).filter(x -> !x.isEmpty()).toList();
            if (parts.isEmpty()) {
                noPath++;
                continue;
            }
            String url = normUrl(cell(r, ui));
            if (url != null) {
                withUrl++;
            }
            String use = null;
            if (yi >= 0) {
                String raw = cell(r, yi).trim();
                String up = raw.toUpperCase(Locale.ROOT);
                if (YES.contains(up)) {
                    use = "Y";
                } else if (NO.contains(up)) {
                    use = "N";
                } else if (!raw.isEmpty()) {
                    use = raw;
                    oddUse++;
                }
            }
            out.add(new Row(i, String.join(" > ", parts), parts.get(parts.size() - 1), url, blank(si >= 0 ? cell(r, si) : null), use,
                    blank(ai >= 0 ? cell(r, ai) : null)));
        }
        List<String> warnings = new ArrayList<>();
        // PR 리뷰 — Csv.guess 는 정확 일치가 없으면 포함 일치로 넘어간다(menu_no 가 「menu」 에 걸린다). 그때는 잡은 열을 알린다
        if (!exact(h.get(pi), PATH)) {
            warnings.add("메뉴 경로 열을 「" + rows.get(0).get(pi) + "」 로 잡았다 — 이름이 정확히 맞지 않는다, 확인한다");
        }
        if (!exact(h.get(ui), URL)) {
            warnings.add("URL 열을 「" + rows.get(0).get(ui) + "」 로 잡았다 — 이름이 정확히 맞지 않는다, 확인한다");
        }
        if (noPath > 0) {
            warnings.add("경로 빈 행 " + noPath + " — 건너뜀");
        }
        if (oddUse > 0) {
            warnings.add("사용여부가 Y/N 밖 " + oddUse + " — 원래 글 그대로");
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("메뉴 행이 없다 — 경로 열이 비었다");
        }
        return new Parsed(out, withUrl, warnings);
    }

    /** 전수 이음용 — 앞뒤 공백·「?」 뒤를 뗀다. 빈 글은 null */
    public static String normUrl(String url) {
        if (url == null) {
            return null;
        }
        String u = url.trim();
        int q = u.indexOf('?');
        if (q >= 0) {
            u = u.substring(0, q).trim();
        }
        return u.isEmpty() ? null : u;
    }

    private static boolean exact(String header, List<String> candidates) {
        String h = header == null ? "" : header.replaceAll("\\s", "");
        return candidates.stream().anyMatch(c -> c.replaceAll("\\s", "").equals(h));
    }

    private static String cell(List<String> r, int i) {
        return i >= 0 && i < r.size() && r.get(i) != null ? r.get(i) : "";
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
