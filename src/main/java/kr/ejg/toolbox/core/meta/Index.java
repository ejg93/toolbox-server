package kr.ejg.toolbox.core.meta;

import java.util.List;

/** {@code sorts} 는 컬럼마다 {@link SortOrder}(1-20, 1-28 열거형 — JSON 글자는 {@code ASC}·{@code DESC}·{@code ""} 그대로). 옛 스냅샷·DDL 읽기는 빈 목록 */
public record Index(String name, boolean unique, List<String> columns, List<SortOrder> sorts) {
    public Index {
        columns = columns == null ? List.of() : List.copyOf(columns);
        sorts = sorts == null ? List.of() : List.copyOf(sorts.stream().map(s -> s == null ? SortOrder.UNKNOWN : s).toList()); // copyOf — SpotBugs 가 불변으로 본다
    }

    /** 정렬을 모르는 인덱스 */
    public Index(String name, boolean unique, List<String> columns) {
        this(name, unique, columns, null);
    }

    /** i 번째 컬럼의 정렬, 모르면 UNKNOWN */
    public SortOrder sortAt(int i) {
        return i < sorts.size() ? sorts.get(i) : SortOrder.UNKNOWN;
    }
}
