package com.ex.jdbc;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 같은 클래스 상수 둘의 연결(텍스트 블록 WHERE) */
@Repository
public class AuditQuery {

    private static final String WHERE = """
             where created_at > :since
            """;
    private static final String SELECT_TOTAL = "select count(*) from audit_log" + WHERE;

    private final JdbcClient jdbc;

    public AuditQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long total() {
        return jdbc.sql(SELECT_TOTAL).param("since", 0L).query(Long.class).single();
    }
}
