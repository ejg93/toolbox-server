package kr.ejg.toolbox.core.meta;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 테이블·뷰 하나. {@code type} 은 {@code TABLE}·{@code VIEW}.
 * {@code rowCount}·{@code createdAt}·{@code lastDdlAt} 은 벤더 통계라 없으면 null — 0 과 모름을 가른다.
 * 불변이라 수집은 {@code with*} 로 새 값을 만든다.
 */
public record Table(
        String schema,
        String name,
        String type,
        String comment,
        List<Column> columns,
        PrimaryKey pk,
        List<ForeignKey> fks,
        List<UniqueKey> uniques,
        List<Index> indexes,
        Long rowCount,
        LocalDateTime createdAt,
        LocalDateTime lastDdlAt) {

    public Table {
        columns = columns == null ? List.of() : List.copyOf(columns);
        fks = fks == null ? List.of() : List.copyOf(fks);
        uniques = uniques == null ? List.of() : List.copyOf(uniques);
        indexes = indexes == null ? List.of() : List.copyOf(indexes);
    }

    /** 뼈대만 — 이름·종류·코멘트 */
    public static Table of(String schema, String name, String type, String comment) {
        return new Table(schema, name, type, comment, null, null, null, null, null, null, null, null);
    }

    public Table withComment(String value) {
        return new Table(schema, name, type, value, columns, pk, fks, uniques, indexes, rowCount, createdAt, lastDdlAt);
    }

    public Table withColumns(List<Column> value) {
        return new Table(schema, name, type, comment, value, pk, fks, uniques, indexes, rowCount, createdAt, lastDdlAt);
    }

    public Table withConstraints(PrimaryKey pkValue, List<ForeignKey> fksValue, List<UniqueKey> uniquesValue) {
        return new Table(schema, name, type, comment, columns, pkValue, fksValue, uniquesValue, indexes, rowCount, createdAt,
                lastDdlAt);
    }

    public Table withIndexes(List<Index> value) {
        return new Table(schema, name, type, comment, columns, pk, fks, uniques, value, rowCount, createdAt, lastDdlAt);
    }

    public Table withStats(Long rowCountValue, LocalDateTime createdAtValue, LocalDateTime lastDdlAtValue) {
        return new Table(schema, name, type, comment, columns, pk, fks, uniques, indexes, rowCountValue, createdAtValue,
                lastDdlAtValue);
    }
}
