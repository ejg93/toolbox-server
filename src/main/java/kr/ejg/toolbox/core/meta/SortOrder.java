package kr.ejg.toolbox.core.meta;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** 인덱스 컬럼 정렬(1-28) — 글은 {@code ASC}·{@code DESC}·{@code ""}(모름). 저장·골든 JSON 글자 그대로 */
public enum SortOrder {
    ASC("ASC"), DESC("DESC"), UNKNOWN("");

    private final String label;

    SortOrder(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }

    /** 글 → 정렬. {@code ASC}·{@code A} → ASC, {@code DESC}·{@code D} → DESC(JDBC {@code ASC_OR_DESC} 한 글자도), 그 밖·null 은 UNKNOWN */
    @JsonCreator
    public static SortOrder of(String s) {
        String x = s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
        return switch (x) {
            case "ASC", "A" -> ASC;
            case "DESC", "D" -> DESC;
            default -> UNKNOWN;
        };
    }
}
