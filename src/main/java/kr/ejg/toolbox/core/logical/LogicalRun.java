package kr.ejg.toolbox.core.logical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kr.ejg.toolbox.core.dict.Dictionaries;
import kr.ejg.toolbox.core.text.Js;

/**
 * 컬럼 목록 전체 변환 — 순수본 {@code run}·{@code renderRank} 를 옮겼다(3-2).
 * 컬럼은 (스키마|테이블|컬럼) 대문자로 중복 제거, 테이블은 (스키마.테이블) 첫 등장 순서. 무시 토큰은 테이블에만.
 */
public final class LogicalRun {

    /** 컬럼 결과 한 줄 — 순수본 ROWS 한 칸 */
    public record Row(String owner, String table, String col, String name, String src, List<String> missing,
            String dtype, String dlen, String dscale, String pk, String nnull, String ord) {
        public Row {
            missing = List.copyOf(missing);
        }
    }

    /** 테이블 결과 한 줄 — 순수본 TROWS 한 칸 */
    public record TableRow(String owner, String table, String name, String src, List<String> missing) {
        public TableRow {
            missing = List.copyOf(missing);
        }
    }

    /** 미등록 약어 랭킹 한 칸 */
    public record Rank(String token, int count) {
    }

    /** 결과 표 위 요약(README 기대 수치) */
    public record Stats(int columns, int tables, int exact, int multi, int mix, int none) {
    }

    public record Result(List<Row> rows, List<TableRow> tableRows, List<Rank> rank, Map<String, Converter.Used> usedTokens,
            Set<String> usedWords, Stats stats) {
        public Result {
            rows = List.copyOf(rows);
            tableRows = List.copyOf(tableRows);
            rank = List.copyOf(rank);
            usedTokens = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(usedTokens));
            usedWords = java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(usedWords));
        }
    }

    private LogicalRun() {
    }

    /**
     * @param skipTokens 무시 토큰(테이블명에만) — 대소문자 무시, 공백 걸러냄
     * @param orgFirst   토큰 우선순위 — 기본(기관표준단어 먼저) true
     */
    public static Result run(List<ColumnInput> inputs, Dictionaries dicts, List<String> skipTokens, boolean orgFirst) {
        Converter conv = new Converter(dicts, orgFirst);
        List<String> skip = skipTokens.stream().map(s -> Js.trim(s).toUpperCase(Locale.ROOT)).filter(s -> !s.isEmpty()).toList();
        boolean colOnly = !inputs.isEmpty() && inputs.stream().allMatch(c -> c.table() == null);

        List<Row> rows = new ArrayList<>();
        Map<String, TableRow> tabs = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (ColumnInput in : inputs) {
            String ow = Js.trim(in.owner());
            String tb = in.table() == null ? "" : Js.trim(in.table());
            String co = Js.removeSpaces(Js.trim(in.column()));
            if (!co.isEmpty() && (colOnly || !tb.isEmpty())) {
                String key = (colOnly ? co : ow + "|" + tb + "|" + co).toUpperCase(Locale.ROOT);
                if (seen.add(key)) {
                    Converter.Result c = conv.convert(co, List.of());
                    rows.add(new Row(ow, tb, co, c.name(), c.src(), c.missing(), nz(in.dataType()), nz(in.length()),
                            nz(in.scale()), nz(in.pk()), nz(in.notNull()), nz(in.ordinal())));
                }
            }
            String tk = ow + "." + tb;
            if (!colOnly && !tb.isEmpty() && !tabs.containsKey(tk)) {
                Converter.Result t = conv.convert(tb, skip);
                tabs.put(tk, new TableRow(ow, tb, t.name(), t.src(), t.missing()));
            }
        }
        List<TableRow> tableRows = new ArrayList<>(tabs.values());
        return new Result(rows, tableRows, rank(rows, tableRows, dicts.user().keySet()), conv.usedTokens(),
                conv.usedWords(), stats(rows, tableRows));
    }

    /**
     * 순수본 renderRank — 못 찾은 토큰 출현 수(컬럼 → 테이블 순으로 셈) + 사용자 사전 키는 0 이라도.
     * JS 객체 키 순서(배열 인덱스 모양 키가 먼저, 숫자순 — 나머지는 넣은 순)에서 출현 수 내림차순 안정 정렬.
     */
    static List<Rank> rank(List<Row> rows, List<TableRow> tableRows, Set<String> userKeys) {
        Map<String, Integer> cnt = new LinkedHashMap<>();
        rows.forEach(r -> r.missing().forEach(t -> cnt.merge(t, 1, Integer::sum)));
        tableRows.forEach(r -> r.missing().forEach(t -> cnt.merge(t, 1, Integer::sum)));
        userKeys.forEach(t -> cnt.putIfAbsent(t, 0));
        List<String> keys = new ArrayList<>();
        cnt.keySet().stream().filter(Js::isArrayIndex).sorted(Comparator.comparingLong(Long::parseLong)).forEach(keys::add);
        cnt.keySet().stream().filter(k -> !Js.isArrayIndex(k)).forEach(keys::add);
        List<Rank> out = new ArrayList<>();
        keys.forEach(k -> out.add(new Rank(k, cnt.get(k))));
        out.sort(Comparator.comparingInt(Rank::count).reversed()); // List.sort 는 안정 정렬
        return out;
    }

    static Stats stats(List<Row> rows, List<TableRow> tableRows) {
        int exact = 0;
        int multi = 0;
        int mix = 0;
        int none = 0;
        List<String> srcs = new ArrayList<>();
        rows.forEach(r -> srcs.add(r.src()));
        tableRows.forEach(r -> srcs.add(r.src()));
        for (String s : srcs) {
            switch (s) {
                case "given", "user", "word" -> exact++;
                case "multi" -> multi++;
                case "mix" -> mix++;
                default -> none++;
            }
        }
        return new Stats(rows.size(), tableRows.size(), exact, multi, mix, none);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
