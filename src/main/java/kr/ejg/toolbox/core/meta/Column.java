package kr.ejg.toolbox.core.meta;

/**
 * 컬럼. {@code nativeType} 은 벤더 원문(VARCHAR2·NUMBER 등), {@code jdbcType} 은 {@link java.sql.Types} 값.
 * {@code domain} 은 추론값(없으면 null).
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
        String domain) {
}
