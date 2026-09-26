package kr.ejg.toolbox.core.meta;

import java.util.List;

/** 스키마 하나와 그 안의 테이블. {@code dbVersion} 은 접속 때 읽은 {@code getDatabaseProductVersion()} */
public record Schema(String name, String dbVersion, List<Table> tables) {
    public Schema {
        tables = tables == null ? List.of() : List.copyOf(tables);
    }
}
