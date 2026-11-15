package kr.ejg.toolbox.core.meta;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ColumnTest {

    private static Column col(boolean auto) {
        return new Column("ID", 1, "BIGINT", -5, null, 19, 0, false, null, null, null, auto);
    }

    /** 1-86a — 베끼는 자리와 JSON 이 자동증가를 지킨다. 거짓은 JSON 에 안 실린다(메타 골든 불변) */
    @Test
    void copiesKeepAutoIncrement() throws Exception {
        assertTrue(col(true).withComment("x").autoIncrement());
        assertFalse(new Column("ID", 1, "BIGINT", -5, null, 19, 0, false, null, null, null).autoIncrement());

        ObjectMapper om = new ObjectMapper();
        String off = om.writeValueAsString(col(false));
        assertFalse(off.contains("autoIncrement"), off);
        String on = om.writeValueAsString(col(true));
        assertTrue(on.contains("\"autoIncrement\":true"), on);
        assertTrue(om.readValue(on, Column.class).autoIncrement());
        assertFalse(om.readValue(off, Column.class).autoIncrement());
    }
}
