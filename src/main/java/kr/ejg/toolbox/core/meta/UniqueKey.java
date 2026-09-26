package kr.ejg.toolbox.core.meta;

import java.util.List;

/** UNIQUE 제약 */
public record UniqueKey(String name, List<String> columns) {
    public UniqueKey {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
