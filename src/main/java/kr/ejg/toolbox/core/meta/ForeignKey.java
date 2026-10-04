package kr.ejg.toolbox.core.meta;

import java.sql.DatabaseMetaData;
import java.util.List;

/**
 * {@code refTable} 은 참조 테이블 이름(스키마 없이). 다른 스키마를 가리키면 {@code refSchema}.
 * {@code deleteRule}·{@code updateRule} 은 {@code CASCADE}·{@code SET NULL}·{@code SET DEFAULT}·{@code RESTRICT}·{@code NO ACTION}, 모르면 null(1-19)
 */
public record ForeignKey(String name, List<String> columns, String refSchema, String refTable, List<String> refColumns,
        String deleteRule, String updateRule) {
    public ForeignKey {
        columns = columns == null ? List.of() : List.copyOf(columns);
        refColumns = refColumns == null ? List.of() : List.copyOf(refColumns);
    }

    /** 규칙을 모르는 FK — DDL 읽기·옛 스냅샷 */
    public ForeignKey(String name, List<String> columns, String refSchema, String refTable, List<String> refColumns) {
        this(name, columns, refSchema, refTable, refColumns, null, null);
    }

    /** {@code getImportedKeys} 의 {@code DELETE_RULE}·{@code UPDATE_RULE} 값 → 규칙 이름. 모르는 값은 null */
    public static String rule(short v) {
        return switch (v) {
            case DatabaseMetaData.importedKeyCascade -> "CASCADE";
            case DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
            case DatabaseMetaData.importedKeySetNull -> "SET NULL";
            case DatabaseMetaData.importedKeyNoAction -> "NO ACTION";
            case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
            default -> null;
        };
    }
}
