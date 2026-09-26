package kr.ejg.toolbox.core.logical;

import java.util.Locale;

/**
 * COMMENT DDL 방언 — 순수본 {@code DIALECTS} 그대로(3-5). Tibero 는 Oracle 문법, MariaDB 는 순수본 mysql.
 * MySQL 컬럼 코멘트의 {@code /* 컬럼타입 명시 필요 *}{@code /} 는 채우지 않는다 — MODIFY COLUMN 은 타입만 쓰면 NOT NULL·DEFAULT 가
 * 풀리는데 컬럼목록 입력엔 기본값이 없다(3-5 행의 개선안을 뒤집음). 사람이 채운다.
 */
public enum Dialect {

    ORACLE("Oracle", false, "-- Oracle/PostgreSQL 공통 COMMENT ON 문법"),
    TIBERO("Tibero", false, "-- Tibero: Oracle 과 COMMENT ON 문법 동일"),
    POSTGRESQL("PostgreSQL", false, "-- PostgreSQL: Oracle 와 COMMENT ON 문법 동일"),
    MARIADB("MySQL/MariaDB", false,
            "-- MySQL/MariaDB: 컬럼 코멘트는 컬럼 정의(타입)를 다시 써야 반영됨. '/* 컬럼타입 명시 필요 */' 를 실제 타입으로 채울 것"),
    MSSQL("SQL Server", false,
            "-- SQL Server: 확장속성(MS_Description) 등록. 이미 있으면 sp_addextendedproperty → sp_updateextendedproperty 로 바꿀 것"),
    SYBASE("Sybase ASE", true,
            "-- Sybase ASE: 컬럼/테이블 코멘트 저장을 표준 지원하지 않음. 아래는 전부 주석이라 실행 대상이 아니다");

    private final String label;
    private final boolean noExec;
    private final String note;

    Dialect(String label, boolean noExec, String note) {
        this.label = label;
        this.noExec = noExec;
        this.note = note;
    }

    public String label() {
        return label;
    }

    /** 실행할 수 없는 대조표만 나오는 방언 */
    public boolean noExec() {
        return noExec;
    }

    public String note() {
        return note;
    }

    /** o·t·v 는 SQL 에 그대로 들어가는 이름, v 는 이스케이프된 코멘트 */
    public String table(String o, String t, String v) {
        return switch (this) {
            case ORACLE, TIBERO, POSTGRESQL -> "COMMENT ON TABLE  " + o + "." + t + " IS '" + v + "';";
            case MARIADB -> "ALTER TABLE " + o + "." + t + " COMMENT = '" + v + "';";
            case MSSQL -> "EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'" + v + "', "
                    + "@level0type=N'SCHEMA', @level0name=N'" + o + "', @level1type=N'TABLE', @level1name=N'" + t + "';";
            case SYBASE -> "-- [미지원] Sybase ASE 테이블 코멘트 표준 없음: " + o + "." + t + " = '" + v + "'";
        };
    }

    public String column(String o, String t, String c, String v) {
        return switch (this) {
            case ORACLE, TIBERO, POSTGRESQL -> "COMMENT ON COLUMN " + o + "." + t + "." + c + " IS '" + v + "';";
            case MARIADB -> "ALTER TABLE " + o + "." + t + " MODIFY COLUMN " + c + " /* 컬럼타입 명시 필요 */ COMMENT '" + v + "';";
            case MSSQL -> "EXEC sys.sp_addextendedproperty @name=N'MS_Description', @value=N'" + v + "', "
                    + "@level0type=N'SCHEMA', @level0name=N'" + o + "', @level1type=N'TABLE', @level1name=N'" + t + "', "
                    + "@level2type=N'COLUMN', @level2name=N'" + c + "';";
            case SYBASE -> "-- [미지원] Sybase ASE 컬럼 코멘트 표준 없음: " + o + "." + t + "." + c + " = '" + v + "'";
        };
    }

    /** 프로필·화면의 이름 — oracle·tibero·postgresql(pg)·mariadb(mysql)·mssql·sybase */
    public static Dialect of(String name) {
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return switch (n) {
            case "oracle" -> ORACLE;
            case "tibero" -> TIBERO;
            case "postgresql", "postgres", "pg" -> POSTGRESQL;
            case "mariadb", "mysql" -> MARIADB;
            case "mssql", "sqlserver" -> MSSQL;
            case "sybase" -> SYBASE;
            default -> throw new IllegalArgumentException("모르는 방언: " + name);
        };
    }
}
