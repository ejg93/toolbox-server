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

    /** 한쪽에만 같은 이름 표가 다른 스키마로 더 있는 스냅샷 */
    static List<Schema> withBackupUsers(List<Schema> in) {
        Schema s = in.get(0);
        Table u = s.tables().stream().filter(t -> t.name().equals("users")).findFirst().orElseThrow();
        List<Table> ts = new ArrayList<>(s.tables());
        ts.add(Table.of("bak", u.name(), u.type(), u.comment()).withColumns(u.columns()).withConstraints(u.pk(), List.of(), List.of()));
        return List.of(new Schema(s.name(), s.dbVersion(), ts));
    }

    /** 1-98(F-K9) — 한쪽에서만 이름이 겹쳐도 짝이 안 깨진다 */
    @Test
    void duplicateNameOnOneSideKeepsPair() throws Exception {
        SnapshotDiff.Result r = SnapshotDiff.compare(base(), withBackupUsers(base()), true);
        assertEquals(List.of("bak.users"), r.addedTables(), r.toString());
        assertEquals(List.of(), r.removedTables(), r.toString());
        assertEquals(List.of(), r.changedTables(), r.toString());
        assertTrue(SnapshotDiff.compare(base(), base(), true).isEmpty());
    }

    static List<Schema> ordersFk(FkRule delete) throws Exception {
        return edit(base(), "orders", t -> t.withConstraints(t.pk(),
                List.of(new ForeignKey("fk_orders_user", List.of("user_id"), null, "users", List.of("user_id"), delete, FkRule.NO_ACTION)), t.uniques()));
    }

    static List<Schema> usersChecks(Check... cs) throws Exception {
        return edit(base(), "users", t -> t.withChecks(List.of(cs)));
    }

    /** 1-97(F-K8) — FK 규칙·CHECK 차이가 보인다. 기본 규칙(모름·NO ACTION·RESTRICT)과 공백만 다른 조건은 차이가 아니다 */
    @Test
    void checkAndFkRuleChangesShow() throws Exception {
        SnapshotDiff.Result r = SnapshotDiff.compare(ordersFk(FkRule.CASCADE), ordersFk(FkRule.NO_ACTION), true);
        assertEquals(List.of("FK(user_id)->users(user_id) ON DELETE CASCADE"), r.changedTables().get(0).removedConstraints(), r.toString());
        assertEquals(List.of("FK(user_id)->users(user_id)"), r.changedTables().get(0).addedConstraints(), r.toString());
        assertTrue(SnapshotDiff.compare(ordersFk(FkRule.NO_ACTION), ordersFk(FkRule.RESTRICT), true).isEmpty());
        assertTrue(SnapshotDiff.compare(ordersFk(null), ordersFk(FkRule.NO_ACTION), true).isEmpty());

        SnapshotDiff.Result ck = SnapshotDiff.compare(usersChecks(new Check("ck_a", "user_id > 0"), new Check("ck_b", "user_id  <  100")),
                usersChecks(new Check("x", "user_id < 100"), new Check("ck_c", "user_id <> 7")), true);
        assertEquals(List.of("CK(user_id <> 7)"), ck.changedTables().get(0).addedConstraints(), ck.toString());
        assertEquals(List.of("CK(user_id > 0)"), ck.changedTables().get(0).removedConstraints(), ck.toString());
        assertTrue(SnapshotDiff.compare(usersChecks(new Check("a", "x  >  1")), usersChecks(new Check("b", " x > 1 ")), true).isEmpty());
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
