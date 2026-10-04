package kr.ejg.toolbox.core.meta;

import java.util.List;

/**
 * 스키마 하나와 그 안의 테이블. {@code dbVersion} 은 접속 때 읽은 {@code getDatabaseProductVersion()}.
 * {@code sizeBytes} 는 스키마 전체 데이터 용량(1-23, 벤더 뷰 — 권한이 없거나 모르면 null). 범위로 거른 표만의 합이 아니다
 */
public record Schema(String name, String dbVersion, List<Table> tables, Long sizeBytes) {
    public Schema {
        tables = tables == null ? List.of() : List.copyOf(tables);
    }

    /** 용량을 모르는 스키마 */
    public Schema(String name, String dbVersion, List<Table> tables) {
        this(name, dbVersion, tables, null);
    }

    public Schema withTables(List<Table> value) {
        return new Schema(name, dbVersion, value, sizeBytes);
    }

    public Schema withSizeBytes(Long value) {
        return new Schema(name, dbVersion, tables, value);
    }
}
