package kr.ejg.toolbox.core.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScopeTest {

    private static Table t(String name) {
        return Table.of("APP", name, "TABLE", null);
    }

    private static Table rows(String name, Long rowCount) {
        return t(name).withStats(rowCount, null, null);
    }

    private static Scope exclude(Scope.Exclude ex) {
        return new Scope(null, ex, null, null);
    }

    @Test
    void includeListWinsOverExclude() {
        Scope s = new Scope(null,
                new Scope.Exclude(List.of("TMP_"), null, null, null),
                new Scope.Include(List.of("TMP_KEEP", "users")),
                null);
        assertTrue(s.accepts(t("TMP_KEEP")), "include 에 있으면 접두 제외를 안 본다");
        assertTrue(s.accepts(t("USERS")), "대소문자 무시");
        assertFalse(s.accepts(t("ORDERS")), "include 가 있으면 목록 밖은 뺀다");
    }

    @Test
    void prefixExcludes() {
        Scope s = exclude(new Scope.Exclude(List.of("TMP_", "BAK_"), null, null, null));
        assertFalse(s.accepts(t("TMP_USERS")));
        assertFalse(s.accepts(t("bak_orders")));
        assertTrue(s.accepts(t("USERS_TMP_")));
    }

    @Test
    void suffixExcludes() {
        Scope s = exclude(new Scope.Exclude(null, List.of("_BAK", "_OLD"), null, null));
        assertFalse(s.accepts(t("USERS_BAK")));
        assertFalse(s.accepts(t("orders_old")));
        assertTrue(s.accepts(t("BAK_USERS")));
    }

    @Test
    void regexExcludes() {
        Scope s = exclude(new Scope.Exclude(null, null, List.of("_\\d{8}$"), null));
        assertFalse(s.accepts(t("USERS_20240101")));
        assertTrue(s.accepts(t("USERS_2024")));
        assertTrue(s.accepts(t("USERS_20240101_X")));
    }

    @Test
    void tableListExcludes() {
        Scope s = exclude(new Scope.Exclude(null, null, null, List.of("LOGS", "Temp")));
        assertFalse(s.accepts(t("LOGS")));
        assertFalse(s.accepts(t("TEMP")));
        assertTrue(s.accepts(t("LOGS2")));
    }

    @Test
    void skipEmptyDropsZeroButKeepsUnknown() {
        Scope s = new Scope(null, null, null, true);
        assertFalse(s.accepts(rows("EMPTY", 0L)));
        assertTrue(s.accepts(rows("UNKNOWN", null)), "행수 모름은 남긴다");
        assertTrue(s.accepts(rows("FULL", 3L)));
        assertTrue(new Scope(null, null, null, false).accepts(rows("EMPTY", 0L)), "skipEmpty 꺼지면 남긴다");
    }

    @Test
    void badRegexFailsAtConstruction() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Scope.Exclude(null, null, List.of("_\\d{8"), null));
    }

    @Test
    void schemaListFilters() {
        Scope s = new Scope(List.of("app", "CMM"), null, null, null);
        assertTrue(s.accepts(t("USERS")));
        assertFalse(s.accepts(Table.of("OTHER", "USERS", "TABLE", null)));
        assertTrue(Scope.all().accepts(Table.of("OTHER", "USERS", "TABLE", null)));
    }

    /** 3-15 — 범위 한 줄. include 가 있으면 제외는 안 적는다(accepts 가 안 본다) */
    @Test
    void summary() {
        assertEquals("없음(전부)", Scope.all().summary());
        assertEquals("없음(전부)", new Scope(List.of(), new Scope.Exclude(null, null, null, null), new Scope.Include(null), false).summary());
        assertEquals("제외 접두 TMP_·BAK_ · 제외 접미 _BAK", exclude(new Scope.Exclude(List.of("TMP_", "BAK_"), List.of("_BAK"), null, null)).summary());
        assertEquals("포함 목록 2개", new Scope(null, new Scope.Exclude(List.of("TMP_"), null, null, null), new Scope.Include(List.of("A", "B")), null).summary());
        assertEquals("스키마 APP · 빈 표 제외", new Scope(List.of("APP"), null, null, true).summary());
        assertEquals("제외 접두 P1·P2·P3·P4·P5 외 2",
                exclude(new Scope.Exclude(List.of("P1", "P2", "P3", "P4", "P5", "P6", "P7"), null, null, null)).summary());
        assertEquals("제외 정규식 _\\d{8}$ · 제외 목록 1개", exclude(new Scope.Exclude(null, null, List.of("_\\d{8}$"), List.of("X"))).summary());
    }
}
