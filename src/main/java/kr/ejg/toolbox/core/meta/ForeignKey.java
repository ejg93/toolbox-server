package kr.ejg.toolbox.core.meta;

import java.util.List;

/**
 * {@code refTable} 은 참조 테이블 이름(스키마 없이). 다른 스키마를 가리키면 {@code refSchema}.
 * {@code deleteRule}·{@code updateRule} 은 {@link FkRule}(1-28 — 전엔 글), 모르면 null(1-19). JSON 글자는 그대로({@code SET NULL} …)
 */
public record ForeignKey(String name, List<String> columns, String refSchema, String refTable, List<String> refColumns,
        FkRule deleteRule, FkRule updateRule) {
    public ForeignKey {
        columns = columns == null ? List.of() : List.copyOf(columns);
        refColumns = refColumns == null ? List.of() : List.copyOf(refColumns);
    }

    /** 규칙을 모르는 FK — DDL 읽기·옛 스냅샷 */
    public ForeignKey(String name, List<String> columns, String refSchema, String refTable, List<String> refColumns) {
        this(name, columns, refSchema, refTable, refColumns, null, null);
    }
}
