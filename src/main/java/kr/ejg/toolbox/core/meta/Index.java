package kr.ejg.toolbox.core.meta;

import java.util.List;

/** {@code sorts} 는 컬럼마다 {@code ASC}·{@code DESC}·{@code ""}(모름), 1-20. 옛 스냅샷·DDL 읽기는 빈 목록 */
public record Index(String name, boolean unique, List<String> columns, List<String> sorts) {
    public Index {
        columns = columns == null ? List.of() : List.copyOf(columns);
        sorts = sorts == null ? List.of() : sorts.stream().map(s -> s == null ? "" : s).toList();
    }

    /** 정렬을 모르는 인덱스 */
    public Index(String name, boolean unique, List<String> columns) {
        this(name, unique, columns, null);
    }

    /** i 번째 컬럼의 정렬, 모르면 "" */
    public String sortAt(int i) {
        return i < sorts.size() ? sorts.get(i) : "";
    }
}
