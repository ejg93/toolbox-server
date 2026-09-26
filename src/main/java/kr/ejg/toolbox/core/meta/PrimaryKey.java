package kr.ejg.toolbox.core.meta;

import java.util.List;

public record PrimaryKey(String name, List<String> columns) {
    public PrimaryKey {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
