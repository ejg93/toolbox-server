package kr.ejg.toolbox.core.analyze;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * CRUD 보기 둘(6-21) — 세로 목록(프로그램·표 한 쌍이 한 줄)과 모듈 매트릭스(행 = 표, 열 = 모듈, 칸 = 그 모듈 프로그램들의 CRUD 합집합).
 * 넓은 격자(열 = 표 전부)는 표 1,000 개면 열 1,000 개라 걷었다. 계산은 여기 한 곳 — 화면·xlsx 가 같은 값을 쓴다. 식별자만(규칙 3)
 */
public final class CrudViews {

    public static final String ROOT = "/";
    private static final String ORDER = "CRUD";

    public record LongRow(String program, String url, String module, String table, String crud) {
    }

    /** 표 하나의 행 — cells 는 모듈 → 합집합 글자(C→R→U→D 순), 있는 모듈만 */
    public record ModuleRow(String table, Map<String, String> cells) {
        public ModuleRow {
            cells = Collections.unmodifiableMap(new TreeMap<>(cells));
        }
    }

    public record ModuleMatrix(List<String> modules, List<ModuleRow> rows) {
        public ModuleMatrix {
            modules = List.copyOf(modules);
            rows = List.copyOf(rows);
        }
    }

    private CrudViews() {
    }

    /** URL 앞 두 마디 — 마지막 마디는 파일이라 뺀다. /sec/gmt/EgovGroupList.do → sec/gmt · /bbs/list.do → bbs · /other → / · 빈 값 → / */
    public static String module(String url) {
        if (url == null) {
            return ROOT;
        }
        String u = url.trim();
        int q = u.indexOf('?');
        if (q >= 0) {
            u = u.substring(0, q);
        }
        List<String> parts = Arrays.stream(u.split("/")).filter(s -> !s.isEmpty()).toList();
        if (parts.size() <= 1) {
            return ROOT;
        }
        return parts.size() == 2 ? parts.get(0) : parts.get(0) + "/" + parts.get(1);
    }

    /** 프로그램(rows 순서) × 표 이름 순(crud 가 TreeMap) */
    public static List<LongRow> longRows(List<AnalyzeStore.ProgramRow> rows) {
        List<LongRow> out = new ArrayList<>();
        for (AnalyzeStore.ProgramRow r : rows) {
            String url = r.url() + (r.params() == null || r.params().isEmpty() ? "" : " " + r.params());
            String module = module(r.url());
            r.crud().forEach((t, c) -> out.add(new LongRow(r.className() + "." + r.method(), url, module, t, c)));
        }
        return out;
    }

    public static ModuleMatrix moduleMatrix(List<AnalyzeStore.ProgramRow> rows) {
        TreeMap<String, TreeMap<String, String>> cells = new TreeMap<>();
        TreeSet<String> modules = new TreeSet<>();
        for (LongRow l : longRows(rows)) {
            modules.add(l.module());
            cells.computeIfAbsent(l.table(), k -> new TreeMap<>()).merge(l.module(), union(l.crud(), ""), CrudViews::union);
        }
        List<ModuleRow> out = new ArrayList<>();
        cells.forEach((t, m) -> out.add(new ModuleRow(t, m)));
        return new ModuleMatrix(new ArrayList<>(modules), out);
    }

    /** 글자 합집합 — 순서는 데이터가 아니라 ORDER 가 정한다(6-18 과 같은 결) */
    static String union(String a, String b) {
        StringBuilder sb = new StringBuilder();
        for (char c : ORDER.toCharArray()) {
            if (a.indexOf(c) >= 0 || b.indexOf(c) >= 0) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
