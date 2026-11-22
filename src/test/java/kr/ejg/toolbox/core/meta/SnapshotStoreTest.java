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
import kr.ejg.toolbox.core.profile.Scope;

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

    /** 1-127(F-05) — 여러 줄·200자 넘는 제품 버전 글도 저장한다(첫 줄 · 200자) · 짧은 글은 그대로 */
    @Test
    void longVersionBannerIsSaved() throws Exception {
        String first = "Oracle Database 19c Enterprise Edition Release 19.0.0.0.0 - Production " + "x".repeat(60);
        String banner = first + "\n" + "Version 19.3.0.0.0 " + "y".repeat(40) + "\r\n" + "z".repeat(30);
        assertTrue(banner.length() > 200, String.valueOf(banner.length()));
        long id = store.save("p", "dev", null, List.of(new Schema("S", banner, List.of())));
        long shortId = store.save("p", "dev", null, List.of(new Schema("S", "X 1", List.of())));
        java.util.Map<Long, String> v = new java.util.HashMap<>();
        store.list().forEach(x -> v.put(x.id(), x.dbVersion()));
        assertEquals(first, v.get(id));
        assertEquals("X 1", v.get(shortId), "지킴이 — 짧은 글 그대로");
        assertEquals(200, SnapshotStore.version("v".repeat(250)).length());
        assertEquals(null, SnapshotStore.version(null));
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

    /** 1-19~1-23 — FK 규칙·인덱스 정렬·CHECK·스키마 용량 왕복. 모르는 값(null 규칙·빈 정렬·null 용량)과 6000자 넘는 CHECK(PR #42 리뷰)도 그대로 */
    @Test
    void rulesSortsChecksSurvive() throws Exception {
        Table t = Table.of("S", "C", "TABLE", null)
                .withColumns(List.of(new Column("ID", 1, "INT", 4, null, null, null, false, null, null, null),
                        new Column("PID", 2, "INT", 4, null, null, null, true, null, null, null)))
                .withConstraints(new PrimaryKey("PK_C", List.of("ID")),
                        List.of(new ForeignKey("FK_C", List.of("PID"), null, "P", List.of("ID"), FkRule.CASCADE, null)), List.of())
                .withIndexes(List.of(new Index("IX_C", false, List.of("PID", "ID"), List.of(SortOrder.DESC, SortOrder.UNKNOWN))))
                .withChecks(List.of(new Check("CK_C", "PID > 0"), new Check("CK_LONG", "PID IN (" + "1, ".repeat(2000) + "2)")));
        List<Schema> in = List.of(new Schema("S", "X 1", List.of(t), 123456L), new Schema("E", "X 1", List.of(), null));
        long id = store.save("p", "dev", null, in);
        assertEquals(in, store.get(id).orElseThrow(), "1-23 스키마 용량(모르면 null)도 왕복");
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

    /** 1-105(F-K10) — 긴 코멘트·기본값·스키마 수백 개 하나로 스냅샷이 통째 실패하지 않는다 */
    @Test
    void longValuesSurvive() throws Exception {
        String longText = "가".repeat(5000);
        Table t = Table.of("S", "T", "TABLE", longText)
                .withColumns(List.of(new Column("ID", 1, "VARCHAR", 12, 10L, null, null, true, longText, longText, null)));
        List<Schema> in = new java.util.ArrayList<>();
        in.add(new Schema("S", "X 1", List.of(t)));
        for (int i = 0; i < 400; i++) {
            in.add(new Schema("S" + i, "X 1", List.of()));
        }
        long id = store.save("p", "dev", null, in);
        assertEquals(in, store.get(id).orElseThrow());
    }

    /** 1-105 — 그래도 넘치는 값(22001)은 INSERT 문·값 없이 칸 이름만 든 글로 */
    @Test
    void tooLongFailsWithoutSqlText() {
        String type = "Q" + "w".repeat(4000);
        Table t = Table.of("S", "T", "TABLE", null)
                .withColumns(List.of(new Column("ID", 1, type, 12, 10L, null, null, true, null, null, null)));
        java.sql.SQLException e = org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,
                () -> store.save("p", "dev", null, List.of(new Schema("S", "X 1", List.of(t)))));
        String msg = String.valueOf(e.getMessage());
        assertTrue(msg.contains("NATIVE_TYPE") && !msg.contains("INSERT") && !msg.contains(type.substring(0, 20)), msg);
    }

    /** 1-86b — 자동증가 참이 저장·되읽기에서 남는다 */
    @Test
    void autoIncrementRoundTrips() throws Exception {
        Table t = Table.of("S", "T", "TABLE", null)
                .withColumns(List.of(new Column("ID", 1, "BIGINT", -5, null, 19, 0, false, null, null, null, true),
                        new Column("NM", 2, "VARCHAR", 12, 10L, null, null, true, null, null, null)))
                .withConstraints(new PrimaryKey(null, List.of("ID")), List.of(), List.of());
        List<Schema> in = List.of(new Schema("S", "X 1", List.of(t)));
        long id = store.save("p", "dev", null, in);
        assertEquals(in, store.get(id).orElseThrow());
        assertTrue(store.get(id).orElseThrow().get(0).tables().get(0).columns().get(0).autoIncrement());
    }

    /** 1-14 — 찍을 때 쓴 범위·경고가 왕복하고, 범위·경고 없이 저장한 행(옛 행과 같은 null)은 안 거른 것 */
    @Test
    void scopeAndWarningsRoundTrip() throws Exception {
        Scope scope = new Scope(List.of("S"), new Scope.Exclude(List.of("TMP_"), null, List.of("^BAK_.*"), null),
                null, true);
        long a = store.save("p", "dev", null, List.of(new Schema("S", "X 1", List.of())));
        long b = store.save("p", "dev", null, List.of(new Schema("S", "X 1", List.of())), scope,
                List.of("comments 42S02/42102 ×2", "stats 42S02/42102 ×1"));
        long c = store.save("p", "dev", null, List.of(new Schema("S", "X 1", List.of())), Scope.all(), List.of());

        List<SnapshotStore.Summary> list = store.list();
        SnapshotStore.Summary sc = list.get(0);
        SnapshotStore.Summary sb = list.get(1);
        SnapshotStore.Summary sa = list.get(2);
        assertEquals(List.of(c, b, a), List.of(sc.id(), sb.id(), sa.id()));

        assertTrue(sb.filtered());
        assertEquals(scope, sb.scope());
        assertEquals(2, sb.warningCount());

        assertEquals(false, sa.filtered(), "범위 없음(옛 행) — 안 거른 것");
        assertEquals(null, sa.scope());
        assertEquals(0, sa.warningCount());

        assertEquals(false, sc.filtered(), "제한 없는 범위 — 안 거른 것");
        assertEquals(Scope.all(), sc.scope());
    }

    @Test
    void filteredByEachPart() {
        assertEquals(false, SnapshotStore.filtered(Scope.all()));
        assertTrue(SnapshotStore.filtered(new Scope(List.of("S"), null, null, null)));
        assertTrue(SnapshotStore.filtered(new Scope(null, new Scope.Exclude(null, List.of("_BAK"), null, null), null, null)));
        assertTrue(SnapshotStore.filtered(new Scope(null, null, new Scope.Include(List.of("T")), null)));
        assertTrue(SnapshotStore.filtered(new Scope(null, null, null, true)));
        assertEquals(false, SnapshotStore.filtered(new Scope(null, new Scope.Exclude(null, null, null, null), null, false)));
        assertEquals(false, SnapshotStore.filtered(null), "옛 행");
    }

    /** 1-24 — isFiltered 는 스냅샷 scope JSON 에 새 키로 안 실린다(읽기가 모르는 키에서 안 깨지게) */
    @Test
    void isFilteredNotSerialized() throws Exception {
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new Scope(List.of("S"), null, null, true));
        assertEquals(false, json.contains("filtered"), json);
    }
}
