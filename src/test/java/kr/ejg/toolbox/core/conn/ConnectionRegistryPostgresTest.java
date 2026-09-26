package kr.ejg.toolbox.core.conn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("db")
@Testcontainers
class ConnectionRegistryPostgresTest {

    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void realDriverTest() {
        Profile p = new Profile("t", null, List.of(new Profile.Connection("pg", "postgresql", DB.getJdbcUrl(), DB.getUsername())),
                "pg", null, null, null, null, null, null, null, null);
        ConnectionRegistry reg = new ConnectionRegistry(() -> Optional.of(p));
        reg.setPassword("pg", DB.getPassword().toCharArray());
        ConnectionRegistry.TestResult r = reg.test("pg");
        assertTrue(r.ok(), String.valueOf(r.message()));
        assertEquals("PostgreSQL", r.productName());
    }
}
