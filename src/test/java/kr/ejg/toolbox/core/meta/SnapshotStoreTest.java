package kr.ejg.toolbox.core.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.Path;
import java.util.List;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.db.Db;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 1-5 — 저장 → 조회가 수집 결과와 equals. 픽스처는 1-3 벤더 골든 넷(컨테이너 없이) */
class SnapshotStoreTest {

    @TempDir
    Path tmp;

    Db db;
    SnapshotStore store;

    @BeforeEach
    void up() {
        db = Db.open(tmp);
        store = new SnapshotStore(db);
    }

    @AfterEach
    void down() {
        db.close();
    }

    static List<Schema> golden(String name) throws Exception {
        return GoldenFiles.JSON.readValue(GoldenFiles.DIR.resolve(name).toFile(), new TypeReference<List<Schema>>() {
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"meta/postgres.json", "meta/postgres-vendor.json", "meta/mariadb.json", "meta/mssql.json",
        "meta/oracle.json"})
    void roundTripEqualsCollected(String fixture) throws Exception {
        List<Schema> collected = golden(fixture);
        long id = store.save("p", "dev", "메모", collected);
        assertEquals(collected, store.get(id).orElseThrow());
    }

    @Test
    void listAndGetTable() throws Exception {
        List<Schema> pg = golden("meta/postgres-vendor.json");
        long a = store.save("p", "dev", null, pg);
        long b = store.save("p", "prd", "운영", golden("meta/oracle.json"));
        List<SnapshotStore.Summary> list = store.list();
        assertEquals(List.of(b, a), list.stream().map(SnapshotStore.Summary::id).toList(), "최신이 먼저");
        assertEquals(8, list.get(0).tableCount());
        assertEquals("운영", list.get(0).note());

        Table users = store.getTable(a, null, "users").orElseThrow();
        assertEquals(pg.get(0).tables().stream().filter(t -> t.name().equals("users")).findFirst().orElseThrow(), users);
        assertTrue(store.getTable(a, "public", "nope").isEmpty());
        assertTrue(store.get(999_999).isEmpty());
    }

    @Test
    void emptySchemaAndNamelessPkSurvive() throws Exception {
        Table t = Table.of("S", "T", "TABLE", null)
                .withColumns(List.of(new Column("ID", 1, "INT", 4, null, null, null, false, null, null, null)))
                .withConstraints(new PrimaryKey(null, List.of("ID")), List.of(), List.of());
        List<Schema> in = List.of(new Schema("EMPTY", "X 1", List.of()), new Schema("S", "X 1", List.of(t)));
        long id = store.save("p", "dev", null, in);
        assertEquals(in, store.get(id).orElseThrow());
    }
}
