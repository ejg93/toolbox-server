package kr.ejg.toolbox.core.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Test;

/** 1-6 — 기준은 1-3 PG 벤더 골든. 변형 넷(컬럼 추가·삭제·타입 변경·스키마명만 다름)을 골든과 대조 */
class SnapshotDiffTest {

    static List<Schema> base() throws Exception {
        return SnapshotStoreTest.golden("meta/postgres-vendor.json");
    }

    /** 이름이 name 인 테이블만 바꾼 사본 */
    static List<Schema> edit(List<Schema> in, String name, UnaryOperator<Table> f) {
        List<Schema> out = new ArrayList<>();
        for (Schema s : in) {
            out.add(new Schema(s.name(), s.dbVersion(), s.tables().stream().map(t -> t.name().equals(name) ? f.apply(t) : t).toList()));
        }
        return out;
    }

    static Table withColumns(Table t, UnaryOperator<List<Column>> f) {
        return t.withColumns(f.apply(new ArrayList<>(t.columns())));
    }

    @Test
    void addedColumn() throws Exception {
        List<Schema> b = edit(base(), "users", t -> withColumns(t, cs -> {
            cs.add(new Column("phone", 6, "varchar", 12, 20L, null, null, true, null, "전화번호", null));
            return cs;
        }));
        SnapshotDiff.Result r = SnapshotDiff.compare(base(), b, true);
        assertEquals(List.of("phone"), r.changedTables().get(0).addedColumns());
        GoldenFiles.assertJson("meta/diff-added-column.json", r);
    }

    @Test
    void removedColumnAndTable() throws Exception {
        List<Schema> b = edit(base(), "orders", t -> withColumns(t, cs -> {
            cs.removeIf(c -> c.name().equals("memo"));
            return cs;
        }));
        b = List.of(new Schema(b.get(0).name(), b.get(0).dbVersion(),
                b.get(0).tables().stream().filter(t -> !t.name().equals("logs")).toList()));
        SnapshotDiff.Result r = SnapshotDiff.compare(base(), b, true);
        assertEquals(List.of("logs"), r.removedTables());
        GoldenFiles.assertJson("meta/diff-removed.json", r);
    }

    @Test
    void typeLengthNullChanged() throws Exception {
        List<Schema> b = edit(base(), "products", t -> withColumns(t, cs -> {
            for (int i = 0; i < cs.size(); i++) {
                Column c = cs.get(i);
                if (c.name().equals("price")) {
                    cs.set(i, new Column(c.name(), c.ordinal(), c.nativeType(), c.jdbcType(), c.length(), 14, 2, true,
                            c.defaultValue(), c.comment(), c.domain()));
                }
                if (c.name().equals("code")) {
                    cs.set(i, new Column(c.name(), c.ordinal(), c.nativeType(), c.jdbcType(), 50L, null, null, c.nullable(),
                            c.defaultValue(), "제품 코드", c.domain()));
                }
            }
            return cs;
        }).withComment("제품"));
        SnapshotDiff.Result r = SnapshotDiff.compare(base(), b, true);
        GoldenFiles.assertJson("meta/diff-changed.json", r);
    }

    /** 개발 DEV·운영 PRD — 스키마명만 다르고 제약 이름도 다른(시스템 이름) 같은 구조는 차이 없음 */
    @Test
    void schemaNameAndConstraintNamesOnlyDiffer() throws Exception {
        List<Schema> dev = rename(base(), "DEV", "");
        List<Schema> prd = rename(base(), "PRD", "SYS_C");
        SnapshotDiff.Result r = SnapshotDiff.compare(dev, prd, true);
        assertTrue(r.isEmpty(), r.toString());
        GoldenFiles.assertJson("meta/diff-schema-only.json", r);

        SnapshotDiff.Result strict = SnapshotDiff.compare(dev, prd, false);
        assertFalse(strict.isEmpty(), "스키마를 보면 전부 추가·삭제");
        assertEquals(8, strict.addedTables().size());
    }

    /** 1-61 — 인덱스는 구조로: 더함·지움은 보이고, 이름만 바꿈·정렬 모름 대 ASC·제약이 받치는 유니크 인덱스는 안 보인다 */
    @Test
    void indexAddedRemovedByStructure() throws Exception {
        List<Schema> b = edit(base(), "orders", t -> {
            List<Index> xs = new ArrayList<>(t.indexes());
            xs.removeIf(i -> i.name().equals("ix_orders_user_date"));
            xs.add(new Index("ix_orders_status", false, List.of("status"), List.of(SortOrder.DESC)));
            return t.withIndexes(xs);
        });
        b = edit(b, "products", t -> t.withIndexes(t.indexes().stream()
                .map(i -> i.name().equals("ix_products_category") ? new Index("ix_prod_cat", i.unique(), i.columns(), i.sorts()) : i).toList()));
        SnapshotDiff.Result r = SnapshotDiff.compare(base(), b, true);
        assertEquals(1, r.changedTables().size(), r.toString());
        SnapshotDiff.TableDiff d = r.changedTables().get(0);
        assertEquals("orders", d.name());
        assertEquals(List.of("IX(status DESC)"), d.addedIndexes());
        assertEquals(List.of("IX(user_id,ordered_at)"), d.removedIndexes());
        assertTrue(d.addedConstraints().isEmpty() && d.removedConstraints().isEmpty(), d.toString());
        GoldenFiles.assertJson("meta/diff-index.json", r);

        List<Schema> unknown = edit(base(), "orders", t -> t.withIndexes(t.indexes().stream()
                .map(i -> new Index(i.name(), i.unique(), i.columns())).toList()));
        assertTrue(SnapshotDiff.compare(base(), unknown, true).isEmpty(), "옛 스냅샷(정렬 모름) 대 ASC 는 같다");

        List<Schema> backed = edit(base(), "users", t -> {
            List<Index> xs = new ArrayList<>(t.indexes());
            xs.add(new Index("ux_users_login2", true, List.of("login_id")));
            return t.withIndexes(xs);
        });
        assertTrue(SnapshotDiff.compare(base(), backed, true).isEmpty(), "UQ(login_id) 가 받치는 유니크 인덱스는 안 보인다");
        List<Schema> pkBacked = edit(base(), "orders", t -> {
            List<Index> xs = new ArrayList<>(t.indexes());
            xs.add(new Index("PRIMARY_KEY_4", true, t.pk().columns()));
            return t.withIndexes(xs);
        });
        assertTrue(SnapshotDiff.compare(base(), pkBacked, true).isEmpty(), "H2 처럼 indexes 에 남은 PK 인덱스는 안 보인다");
    }

    static List<Schema> rename(List<Schema> in, String schema, String constraintPrefix) {
        List<Schema> out = new ArrayList<>();
        for (Schema s : in) {
            out.add(new Schema(schema, s.dbVersion(), s.tables().stream().map(t -> {
                Table x = new Table(schema, t.name(), t.type(), t.comment(), t.columns(),
                        t.pk() == null ? null : new PrimaryKey(constraintPrefix + t.pk().name(), t.pk().columns()),
                        t.fks().stream().map(f -> new ForeignKey(constraintPrefix + f.name(), f.columns(), f.refSchema(), f.refTable(),
                                f.refColumns())).toList(),
                        t.uniques().stream().map(u -> new UniqueKey(constraintPrefix + u.name(), u.columns())).toList(),
                        t.indexes(), t.rowCount(), t.createdAt(), t.lastDdlAt());
                return x;
            }).toList()));
        }
        return out;
    }
}
