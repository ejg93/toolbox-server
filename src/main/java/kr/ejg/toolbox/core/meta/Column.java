package kr.ejg.toolbox.core.meta;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 컬럼. {@code nativeType} 은 벤더 원문(VARCHAR2·NUMBER 등), {@code jdbcType} 은 {@link java.sql.Types} 값.
 * {@code domain} 은 추론값(없으면 null). {@code autoIncrement} 는 JDBC {@code IS_AUTOINCREMENT}(1-86) —
 * 거짓은 아님·모름 둘 다.
 */
public record Column(
        String name,
        int ordinal,
        String nativeType,
        Integer jdbcType,
        Long length,
        Integer precision,
        Integer scale,
        boolean nullable,
        String defaultValue,
        String comment,
        String domain,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean autoIncrement) {

    /** 자동증가를 모르는 컬럼(DDL 읽기·옛 스냅샷·시험) */
    // tb-ignore-next metric.params 레코드 성분 그대로 — 1-86 전 11 인자 호출 자리를 안 고친다
    public Column(String name, int ordinal, String nativeType, Integer jdbcType, Long length, Integer precision, Integer scale,
            boolean nullable, String defaultValue, String comment, String domain) {
        this(name, ordinal, nativeType, jdbcType, length, precision, scale, nullable, defaultValue, comment, domain, false);
    }

    public Column withComment(String value) {
        return new Column(name, ordinal, nativeType, jdbcType, length, precision, scale, nullable, defaultValue, value, domain,
                autoIncrement);
    }
}
