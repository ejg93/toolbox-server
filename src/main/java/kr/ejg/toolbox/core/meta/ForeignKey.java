package kr.ejg.toolbox.core.meta;

import java.util.List;

/** {@code refTable} 은 참조 테이블 이름(스키마 없이). 다른 스키마를 가리키면 {@code refSchema} */
public record ForeignKey(String name, List<String> columns, String refSchema, String refTable, List<String> refColumns) {
    public ForeignKey {
        columns = columns == null ? List.of() : List.copyOf(columns);
        refColumns = refColumns == null ? List.of() : List.copyOf(refColumns);
    }
}
