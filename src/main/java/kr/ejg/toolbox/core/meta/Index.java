package kr.ejg.toolbox.core.meta;

import java.util.List;

public record Index(String name, boolean unique, List<String> columns) {
    public Index {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
