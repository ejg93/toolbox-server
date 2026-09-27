package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.gen.InsertGen;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 4-7 — PG 방언 생성문을 컨테이너 products 에 넣는다. FK 는 라우트의 조회(`InsertRoutes.fkValues`)로 부모 실존 값 */
@Tag("db")
@Testcontainers
class InsertGenPostgresTest {

    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine")
            .withInitScript("sample/postgres.sql");

    @Test
    void generatedInsertsRunOnPostgres() throws Exception {
        Table products = null;
        for (Schema s : GoldenFiles.schemas("meta/postgres-vendor.json")) {
            for (Table t : s.tables()) {
                if (t.name().equals("products")) {
                    products = t;
                }
            }
        }
        try (Connection c = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
            try (Statement s = c.createStatement()) {
                s.execute("INSERT INTO categories (category_id, name) VALUES (7, 'a'), (8, 'b')");
            }
            InsertGen.Result r = InsertGen.generate(products, Map.of(),
                    new InsertGen.Options("postgresql", 5, false, false, LocalDate.of(2026, 1, 31)),
                    (fk, max) -> InsertRoutes.fkValues(c, fk, max));
            assertEquals(List.of(), r.warnings());
            try (Statement s = c.createStatement()) {
                for (String stmt : r.sql().split("\n\n")) {
                    s.execute(stmt);
                }
                try (ResultSet rs = s.executeQuery("SELECT COUNT(*), MIN(category_id), MAX(category_id) FROM products")) {
                    rs.next();
                    assertEquals(5, rs.getInt(1));
                    assertEquals(7, rs.getInt(2));
                    assertEquals(8, rs.getInt(3));
                }
            }
            InsertGen.Result up = InsertGen.fromCsv(products, "product_id,category_id,code,name,price\n1,8,NEW,새 이름,9.5\n",
                    new InsertGen.Options("postgresql", 0, true, false, null));
            try (Statement s = c.createStatement()) {
                s.execute(up.sql().substring(0, up.sql().lastIndexOf(';')));
                try (ResultSet rs = s.executeQuery("SELECT code, name FROM products WHERE product_id = 1")) {
                    rs.next();
                    assertEquals("NEW", rs.getString(1));
                    assertTrue(rs.getString(2).equals("새 이름"), "ON CONFLICT 로 갱신");
                }
            }
        }
    }
}
