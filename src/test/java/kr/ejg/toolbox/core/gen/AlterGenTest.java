package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.FkRule;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.PrimaryKey;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * 1-60a — 반영 DDL. A = PG 벤더 스냅샷(SnapshotDiffTest 와 같은 픽스처), B = 자바로 바꾼 것 — 새 표·표 삭제·컬럼 추가 둘·삭제·길이 늘림·줄임·
 * NULL 허용 양쪽·기본값 추가·제거·코멘트(표·컬럼)·PK 컬럼 바뀜·유니크 추가·삭제·FK 추가(ON DELETE CASCADE)·삭제·인덱스 추가·삭제.
 * 방언 다섯 골든 — 문장 뒤에 경고 블록을 같이 적는다
 */
class AlterGenTest {

    static List<Schema> fixture() throws Exception {
        return GoldenFiles.JSON.readValue(GoldenFiles.DIR.resolve("meta/postgres-vendor.json").toFile(), new TypeReference<List<Schema>>() {
        });
    }

    static Table edit(List<Table> ts, String name, UnaryOperator<Table> f) {
        for (int i = 0; i < ts.size(); i++) {
            if (ts.get(i).name().equals(name)) {
                Table t = f.apply(ts.get(i));
                ts.set(i, t);
                return t;
            }
        }
        throw new IllegalArgumentException(name);
    }

    static Table cols(Table t, List<Column> cols) {
        return new Table(t.schema(), t.name(), t.type(), t.comment(), cols, t.pk(), t.fks(), t.uniques(), t.indexes(), t.rowCount(), t.createdAt(),
                t.lastDdlAt(), t.checks());
    }

    static Column col(String name, int ord, String type, Long len, Integer p, boolean nullable, String def, String comment) {
        return new Column(name, ord, type, null, len, p, null, nullable, def, comment, null);
    }

    static Column with(Column c, Long len, boolean nullable, String def, String comment) {
        return new Column(c.name(), c.ordinal(), c.nativeType(), c.jdbcType(), len, c.precision(), c.scale(), nullable, def, comment, c.domain());
    }

    /** A → B 변경 묶음 */
    static List<Schema> changed(List<Schema> a) {
        Schema s = a.get(0);
        List<Table> ts = new ArrayList<>(s.tables());
        // 표 삭제
        ts.removeIf(t -> t.name().equals("logs"));
        // users — 컬럼 추가 둘(NULL 허용 · NOT NULL DEFAULT) · email 삭제 · login_id 길이 늘림 · user_name 길이 줄임 · 표 코멘트
        edit(ts, "users", t -> {
            List<Column> c = new ArrayList<>();
            for (Column x : t.columns()) {
                if (x.name().equals("email")) {
                    continue;
                }
                if (x.name().equals("login_id")) {
                    x = with(x, 80L, x.nullable(), x.defaultValue(), x.comment());
                }
                if (x.name().equals("user_name")) {
                    x = with(x, 50L, x.nullable(), x.defaultValue(), "사용자 이름");
                }
                c.add(x);
            }
            c.add(col("phone", 10, "varchar", 20L, null, true, null, "전화"));
            c.add(col("grade", 11, "int4", null, null, false, "1", null));
            Table n = cols(t, c);
            // ux_users_email 인덱스는 email 이 없어지니 뺀다, 새 인덱스 ix_users_name
            return new Table(n.schema(), n.name(), n.type(), "사용자", n.columns(), n.pk(), n.fks(), n.uniques(),
                    List.of(new Index("uq_users_login", true, List.of("login_id"), null), new Index("ix_users_name", false, List.of("user_name"), null)),
                    n.rowCount(), n.createdAt(), n.lastDdlAt(), n.checks());
        });
        // products — price 기본값 제거, created_at NULL 허용, code 유니크 삭제 · 새 유니크 (name), FK 규칙 CASCADE 로(지우고 다시)
        edit(ts, "products", t -> {
            List<Column> c = new ArrayList<>();
            for (Column x : t.columns()) {
                if (x.name().equals("price")) {
                    x = with(x, x.length(), x.nullable(), null, x.comment());
                }
                if (x.name().equals("created_at")) {
                    x = with(x, x.length(), true, x.defaultValue(), x.comment());
                }
                c.add(x);
            }
            List<ForeignKey> fks = List.of(new ForeignKey("fk_products_category", List.of("category_id"), null, "categories", List.of("category_id"),
                    FkRule.CASCADE, null));
            return new Table(t.schema(), t.name(), t.type(), t.comment(), c, t.pk(), fks, List.of(new UniqueKey("uq_products_name", List.of("name"))),
                    List.of(new Index("ix_products_category", false, List.of("category_id"), null)), t.rowCount(), t.createdAt(), t.lastDdlAt(),
                    t.checks());
        });
        // codes — sort_order NOT NULL + 기본값 추가, PK 컬럼 바뀜(code 만)
        edit(ts, "codes", t -> {
            List<Column> c = new ArrayList<>();
            for (Column x : t.columns()) {
                if (x.name().equals("sort_order")) {
                    x = with(x, x.length(), false, "0", x.comment());
                }
                c.add(x);
            }
            Table n = cols(t, c);
            return new Table(n.schema(), n.name(), n.type(), n.comment(), n.columns(), new PrimaryKey("pk_codes", List.of("code")), n.fks(),
                    n.uniques(), n.indexes(), n.rowCount(), n.createdAt(), n.lastDdlAt(), n.checks());
        });
        // order_items — FK 하나 삭제
        edit(ts, "order_items", t -> new Table(t.schema(), t.name(), t.type(), t.comment(), t.columns(), t.pk(),
                t.fks().stream().filter(f -> !f.name().equals("fk_order_items_product")).toList(), t.uniques(), t.indexes(), t.rowCount(), t.createdAt(),
                t.lastDdlAt(), t.checks()));
        // 새 표 — PK + users 로 FK
        ts.add(new Table(s.name(), "user_tags", "TABLE", "사용자 태그",
                List.of(col("tag_id", 1, "int8", null, null, false, null, "태그 ID"), col("user_id", 2, "int8", null, null, false, null, null),
                        col("tag", 3, "varchar", 30L, null, false, "'NEW'", null)),
                new PrimaryKey("pk_user_tags", List.of("tag_id")),
                List.of(new ForeignKey("fk_user_tags_user", List.of("user_id"), null, "users", List.of("user_id"), FkRule.CASCADE, null)), List.of(),
                List.of(new Index("ix_user_tags_user", false, List.of("user_id"), null)), null, null, null, null));
        return List.of(new Schema(s.name(), s.dbVersion(), ts, s.sizeBytes()));
    }

    static AlterGen.Result run(String target) throws Exception {
        return AlterGen.generate(fixture(), changed(fixture()), new AlterGen.Options("postgresql", target, null, true, true, true), TypeMapping.load());
    }

    @ParameterizedTest
    @ValueSource(strings = {"oracle", "tibero", "postgresql", "mariadb", "mssql"})
    void goldenPerTarget(String target) throws Exception {
        AlterGen.Result r = run(target);
        StringBuilder out = new StringBuilder(r.sql()).append("\n-- 경고\n");
        r.warnings().forEach(w -> out.append("-- ").append(w).append('\n'));
        GoldenFiles.assertText("gen/alter-" + target + ".sql", out.toString());
        assertEquals(1, r.addedTables());
        assertEquals(1, r.removedTables());
        assertEquals(4, r.changedTables());
        assertTrue(r.review() >= 4, "확인 — 표 삭제·컬럼 삭제·삭제+추가·NOT NULL: " + r.review());
        assertTrue(r.sql().contains("-- DROP TABLE") && r.sql().contains("-- ALTER TABLE") , "위험 문장은 주석으로");
    }

    @Test
    void noDifference() throws Exception {
        AlterGen.Result r = AlterGen.generate(fixture(), fixture(), new AlterGen.Options("postgresql", "postgresql", null, true, true, true),
                TypeMapping.load());
        assertTrue(r.sql().endsWith("-- 차이 없음\n"), r.sql());
        assertEquals(0, r.statements());
        assertEquals(0, r.changedTables());
    }

    /** 리뷰 지적 — 안 바뀐 표(order_items)의 FK 가 가리키는 PK(orders)를 바꾸면 그 FK 를 먼저 지우고 PK 를 다시 만든 뒤 다시 붙인다 */
    @Test
    void foreignKeyOnUnchangedTableIsDroppedBeforeItsKey() throws Exception {
        List<Schema> b = fixture();
        List<Table> ts = new ArrayList<>(b.get(0).tables());
        edit(ts, "orders", t -> new Table(t.schema(), t.name(), t.type(), t.comment(), t.columns(),
                new kr.ejg.toolbox.core.meta.PrimaryKey(t.pk().name(), List.of("order_id", "user_id")), t.fks(), t.uniques(), t.indexes(),
                t.rowCount(), t.createdAt(), t.lastDdlAt(), t.checks()));
        b = List.of(new Schema(b.get(0).name(), b.get(0).dbVersion(), ts, b.get(0).sizeBytes()));
        for (String target : List.of("postgresql", "mariadb")) {
            String sql = AlterGen.generate(fixture(), b, new AlterGen.Options("postgresql", target, null, true, true, true), TypeMapping.load()).sql();
            String drop = target.equals("mariadb") ? "ALTER TABLE public.order_items DROP FOREIGN KEY fk_order_items_order" : "ALTER TABLE public.order_items DROP CONSTRAINT fk_order_items_order";
            String pkDrop = target.equals("mariadb") ? "ALTER TABLE public.orders DROP PRIMARY KEY" : "ALTER TABLE public.orders DROP CONSTRAINT";
            String add = "ALTER TABLE public.order_items ADD CONSTRAINT fk_order_items_order FOREIGN KEY";
            assertTrue(sql.contains(drop) && sql.indexOf(drop) < sql.indexOf(pkDrop), target + "\n" + sql);
            assertTrue(sql.indexOf(add) > sql.indexOf("PRIMARY KEY (order_id, user_id)"), target + "\n" + sql);
            assertEquals(sql.indexOf(drop), sql.lastIndexOf(drop), "한 번만 지운다\n" + sql);
            assertTrue(!sql.contains("fk_order_items_product"), "다른 FK 는 그대로\n" + sql);
        }
    }

    @Test
    void keepSchemaSeesEveryTableAsNew() throws Exception {
        List<Schema> b = new ArrayList<>();
        for (Schema s : fixture()) {
            List<Table> ts = new ArrayList<>();
            s.tables().forEach(t -> ts.add(new Table("prd", t.name(), t.type(), t.comment(), t.columns(), t.pk(), t.fks(), t.uniques(), t.indexes(),
                    t.rowCount(), t.createdAt(), t.lastDdlAt(), t.checks())));
            b.add(new Schema("prd", s.dbVersion(), ts, s.sizeBytes()));
        }
        AlterGen.Result strict = AlterGen.generate(fixture(), b, new AlterGen.Options("postgresql", "postgresql", null, false, true, true),
                TypeMapping.load());
        assertEquals(fixture().get(0).tables().size(), strict.addedTables());
        assertEquals(fixture().get(0).tables().size(), strict.removedTables());
        AlterGen.Result loose = AlterGen.generate(fixture(), b, new AlterGen.Options("postgresql", "postgresql", null, true, true, true),
                TypeMapping.load());
        assertEquals(0, loose.changedTables() + loose.addedTables() + loose.removedTables(), "스키마 이름만 다르면 차이 없음 — " + loose.sql());
    }

    @Test
    void unknownTarget() {
        assertThrows(IllegalArgumentException.class, () -> AlterGen.generate(fixture(), fixture(), new AlterGen.Options("postgresql", "sybase", null,
                true, true, true), TypeMapping.load()));
    }
}
