package kr.ejg.toolbox.core.meta;

import java.util.Locale;

/**
 * DB 제품명·버전 글 → 방언 이름(스냅샷의 dbVersion 에서). 0-62b 에서 {@code core.gen.TypeMapping} 에서 옮겼다 — 메타 쪽(스냅샷 목록)이
 * 생성기 패키지를 부르던 고리(meta → gen → meta)를 끊는다. {@code TypeMapping.dialectOf} 는 이것을 부른다
 */
public final class DbProduct {

    private DbProduct() {
    }

    /** 모르면 null */
    public static String dialectOf(String product) {
        String p = product == null ? "" : product.toLowerCase(Locale.ROOT);
        if (p.contains("tibero")) {
            return "tibero";
        }
        if (p.contains("oracle")) {
            return "oracle";
        }
        if (p.contains("postgres")) {
            return "postgresql";
        }
        if (p.contains("mariadb") || p.contains("mysql")) {
            return "mariadb";
        }
        if (p.contains("microsoft") || p.contains("sql server")) {
            return "mssql";
        }
        return null;
    }
}
