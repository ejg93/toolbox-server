package kr.ejg.toolbox.core.meta;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.sql.DatabaseMetaData;
import java.util.Locale;

/**
 * FK 삭제·갱신 규칙(1-28) — 닫힌 다섯. 글({@link #label()})은 저장(`snap_constraint`)·골든 JSON·정의서 칸 글자 그대로({@code SET NULL} 처럼 띄어 쓴다).
 * 모르면 null 로 둔다(열거형 값이 아니라).
 */
public enum FkRule {
    CASCADE("CASCADE"), SET_NULL("SET NULL"), SET_DEFAULT("SET DEFAULT"), RESTRICT("RESTRICT"), NO_ACTION("NO ACTION");

    private final String label;

    FkRule(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }

    /** 글 → 규칙. 대소문자·밑줄(`SET_NULL`)을 가리지 않는다. null·빈 글·모르는 글은 null */
    @JsonCreator
    public static FkRule of(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String x = s.trim().toUpperCase(Locale.ROOT).replace('_', ' ');
        for (FkRule r : values()) {
            if (r.label.equals(x)) {
                return r;
            }
        }
        return null;
    }

    /** {@code getImportedKeys} 의 {@code DELETE_RULE}·{@code UPDATE_RULE} 값 → 규칙. 모르는 값은 null */
    public static FkRule jdbc(short v) {
        return switch (v) {
            case DatabaseMetaData.importedKeyCascade -> CASCADE;
            case DatabaseMetaData.importedKeyRestrict -> RESTRICT;
            case DatabaseMetaData.importedKeySetNull -> SET_NULL;
            case DatabaseMetaData.importedKeyNoAction -> NO_ACTION;
            case DatabaseMetaData.importedKeySetDefault -> SET_DEFAULT;
            default -> null;
        };
    }

    /** 칸에 쓸 글 — null 이면 빈 글 */
    public static String label(FkRule r) {
        return r == null ? "" : r.label;
    }
}
