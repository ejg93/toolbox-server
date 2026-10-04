package kr.ejg.toolbox.core.dialect;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import kr.ejg.toolbox.core.meta.MetaSource;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Scope;
import kr.ejg.toolbox.core.meta.Table;

/**
 * 수집 보강(1-19~1-23) — 표본 DDL(sample/*.sql)은 안 건드리고(골든·DDL 읽기 테스트가 문다) 규칙·정렬·CHECK 가 든 표 둘을
 * 잠깐 만들어 수집값을 본 뒤 지운다. prefix 는 스키마 머리(MSSQL 은 표본이 sample 스키마에 있다)
 */
final class MetaMore {

    private MetaMore() {
    }

    static List<String> ddl(String prefix) {
        return List.of("CREATE TABLE " + prefix + "ZZ_P (ID INT PRIMARY KEY)",
                "CREATE TABLE " + prefix + "ZZ_C (ID INT PRIMARY KEY, PID INT, V INT,"
                        + " CONSTRAINT ZZ_C_FK FOREIGN KEY (PID) REFERENCES " + prefix + "ZZ_P(ID) ON DELETE CASCADE,"
                        + " CONSTRAINT ZZ_C_CK CHECK (V > 0))",
                "CREATE INDEX ZZ_C_IX ON " + prefix + "ZZ_C (V DESC, PID)");
    }

    /** 만들고 → 수집 → ZZ_C 를 돌려준다. 끝에 지운다(실패해도) */
    static Table collect(Connection c, String dialect, String schema, String prefix) throws SQLException {
        try (Statement s = c.createStatement()) {
            for (String d : ddl(prefix)) {
                s.execute(d);
            }
            try {
                MetaSource src = MetaSources.forDialect(dialect, c);
                List<Schema> got = src.collect(new Scope(List.of(schema), null, null, null));
                return got.get(0).tables().stream().filter(t -> t.name().equalsIgnoreCase("ZZ_C")).findFirst().orElseThrow();
            } finally {
                for (String t : List.of("ZZ_C", "ZZ_P")) {
                    try {
                        s.execute("DROP TABLE " + prefix + t);
                    } catch (SQLException e) {
                        // 이미 없다
                    }
                }
            }
        }
    }
}
