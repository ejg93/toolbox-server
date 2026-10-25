package com.ex.jdbc;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JdbcTemplate(? 자리)·NamedParameterJdbcTemplate(이름 바인딩) — 한 메서드의 문장 셋은 한 id 에 합친다 */
@Repository
public class LegacyDao {

    private final JdbcTemplate template;
    private final NamedParameterJdbcTemplate named;

    public LegacyDao(JdbcTemplate template, NamedParameterJdbcTemplate named) {
        this.template = template;
        this.named = named;
    }

    public void purge() {
        this.template.update("delete from password_reset_token where issued_at < ?", 0L);
        template.query("select u.user_id from app_user u join user_role r on r.user_id = u.user_id", rs -> {
        });
        named.update("update app_user set email = null where deleted_at < :graceEnd", Map.of("graceEnd", 0L));
        template.execute(conn -> null);
    }
}
