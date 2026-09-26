package kr.ejg.toolbox.core.dialect;

import java.sql.Connection;
import java.util.Locale;
import kr.ejg.toolbox.core.meta.JdbcMetaSource;
import kr.ejg.toolbox.core.meta.MetaSource;

/** 프로필 `connections[].dialect` → 수집기. 모르는 방언은 JDBC 뼈대만(코멘트·통계 없음). */
public final class MetaSources {

    private MetaSources() {
    }

    public static MetaSource forDialect(String dialect, Connection conn) {
        String d = dialect == null ? "" : dialect.toLowerCase(Locale.ROOT);
        return switch (d) {
            case "oracle" -> new OracleMetaSource(conn);
            case "tibero" -> new TiberoMetaSource(conn);
            case "postgresql", "postgres", "pg" -> new PostgresMetaSource(conn);
            case "mariadb", "mysql" -> new MariaMetaSource(conn);
            case "mssql", "sqlserver" -> new MssqlMetaSource(conn);
            default -> new JdbcMetaSource(conn);
        };
    }
}
