package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.meta.Check;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.FkRule;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.PrimaryKey;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.SortOrder;
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
        if (target.equals("oracle")) {
            assertTrue(r.sql().split("\\[확인:삭제]", -1).length - 1 >= 2, "표·컬럼 삭제 꼬리표");
            assertTrue(r.sql().contains("[확인:데이터]"), "NOT NULL·타입 꼬리표");
        }
        assertTrue(!r.sql().contains("[확인]"), "꼬리표 없는 [확인] 이 남지 않는다");
    }

    @Test
    void noDifference() throws Exception {
        AlterGen.Result r = AlterGen.generate(fixture(), fixture(), new AlterGen.Options("postgresql", "postgresql", null, true, true, true),
                TypeMapping.load());
        assertTrue(r.sql().endsWith("-- 차이 없음\n"), r.sql());
        assertEquals(0, r.statements());
        assertEquals(0, r.changedTables());
    }

    /** R-A-M2 — 새 기본값이 대상으로 못 옮기는 식이면 DEFAULT NULL·DROP DEFAULT 로 앞 기본값을 지우지 않고 [손] 한 줄 */
    @Test
    void unportableDefaultIsHandNotLive() throws Exception {
        List<Schema> b = fixture();
        List<Table> ts = new ArrayList<>(b.get(0).tables());
        edit(ts, "products", t -> {
            List<Column> c = new ArrayList<>();
            for (Column x : t.columns()) {
                c.add(x.name().equals("price") ? with(x, x.length(), x.nullable(), "uuid_generate_v4()", x.comment()) : x);
            }
            return cols(t, c);
        });
        b = List.of(new Schema(b.get(0).name(), b.get(0).dbVersion(), ts, b.get(0).sizeBytes()));
        for (String target : List.of("oracle", "postgresql", "mariadb")) {
            AlterGen.Result r = AlterGen.generate(fixture(), b, new AlterGen.Options("postgresql", target, null, true, true, true), TypeMapping.load());
            String sql = r.sql();
            assertTrue(!sql.contains("DEFAULT NULL") && !sql.contains("DROP DEFAULT"), target + "\n" + sql);
            assertTrue(sql.contains("-- [손] 컬럼 price 기본값 — 식을 대상 문법으로 못 옮겼다"), target + "\n" + sql);
            assertEquals(1, r.warnings().stream().filter(w -> w.contains("기본값 식은 옮기지 않았다")).count(), target + " " + r.warnings());
            assertTrue(r.review() >= 1, target + " " + r.review());
        }
    }

    /** 1-115 — MariaDB 가 컬럼을 통째로 다시 쓰면(MODIFY COLUMN) [손] 글이 「뒤 MODIFY COLUMN 이 기본값을 지운다」. 다른 대상·기본값만 바꾼 꼴은 옛 글 */
    @Test
    void unportableDefaultOnMariaRedefine() throws Exception {
        String lostKept = "-- [손] 컬럼 price 기본값 — 식을 대상 문법으로 못 옮겼다, 손으로 쓴다(앞 기본값은 그대로)";
        String lostRedefined = "-- [손] 컬럼 price 기본값 — 식을 대상 문법으로 못 옮겼다, 뒤 MODIFY COLUMN 이 기본값을 지운다 — 손으로 다시 넣는다";
        List<Schema> both = priceAs(x -> with(x, x.length(), !x.nullable(), "uuid_generate_v4()", x.comment()));
        String maria = alterSql(both, "mariadb");
        assertTrue(maria.contains("MODIFY COLUMN") && maria.contains(lostRedefined) && !maria.contains("(앞 기본값은 그대로)"), maria);
        for (String target : List.of("oracle", "postgresql")) {
            String sql = alterSql(both, target);
            assertTrue(sql.contains(lostKept), target + "\n" + sql);
        }
        String onlyDefault = alterSql(priceAs(x -> with(x, x.length(), x.nullable(), "uuid_generate_v4()", x.comment())), "mariadb");
        assertTrue(onlyDefault.contains(lostKept) && !onlyDefault.contains(lostRedefined), onlyDefault);
    }

    /** 1-116(A1-02) — 기본값이 안 바뀌어도 통째 재정의면 못 옮기는 식을 [손] 으로 · 옛 기본값이 없으면 「없이 나간다」 */
    @Test
    void unportableDefaultUnchangedOrNew() throws Exception {
        String head = "-- [손] 컬럼 price 기본값 — 식을 대상 문법으로 못 옮겼다, ";
        String uuid = "uuid_generate_v4()";
        List<Schema> a1 = priceAs(x -> with(x, x.length(), x.nullable(), uuid, x.comment()));
        List<Schema> b1 = priceAs(x -> with(x, x.length(), x.nullable(), uuid, "바뀐 코멘트"));
        String m1 = between(a1, b1, "mariadb");
        assertTrue(m1.contains("MODIFY COLUMN") && m1.contains(head + "뒤 MODIFY COLUMN 이 기본값을 지운다 — 손으로 다시 넣는다"), m1);
        List<Schema> a2 = priceAs(x -> with(x, x.length(), x.nullable(), null, x.comment()));
        List<Schema> b2 = priceAs(x -> with(x, x.length(), !x.nullable(), uuid, x.comment()));
        String m2 = between(a2, b2, "mariadb");
        assertTrue(m2.contains(head + "뒤 MODIFY COLUMN 은 기본값 없이 나간다 — 손으로 넣는다") && !m2.contains("기본값을 지운다"), m2);
        for (String target : List.of("oracle", "postgresql")) {
            String t1 = between(a1, b1, target);
            assertFalse(t1.contains(head), target + "\n" + t1);
            String t2 = between(a2, b2, target);
            assertTrue(t2.contains(head + "손으로 쓴다(앞 기본값은 그대로)"), target + "\n" + t2);
        }
    }

    private static String between(List<Schema> a, List<Schema> b, String target) {
        return AlterGen.generate(a, b, new AlterGen.Options("postgresql", target, null, true, true, true), TypeMapping.load()).sql();
    }

    /** 0-118 — 못 옮기는 기본값의 다른 길: MSSQL(기본값만) · 형식 + 기본값(oracle MODIFY · PG TYPE) — [손] 만, 지우는 문장 없음 */
    @Test
    void unportableDefaultOtherPaths() throws Exception {
        String hand = "-- [손] 컬럼 price 기본값 — 식을 대상 문법으로 못 옮겼다";
        String ms = alterSql(priceAs(x -> with(x, x.length(), x.nullable(), "uuid_generate_v4()", x.comment())), "mssql");
        assertTrue(ms.contains(hand) && !ms.contains("sp_helpconstraint"), ms);
        assertTrue(ms.lines().noneMatch(l -> !l.startsWith("-- ") && l.contains("DEFAULT")), ms);
        List<Schema> typed = priceAs(x -> new Column(x.name(), x.ordinal(), x.nativeType(), x.jdbcType(), x.length(), 30, x.scale(), x.nullable(),
                "uuid_generate_v4()", x.comment(), x.domain()));
        String ora = alterSql(typed, "oracle");
        assertTrue(ora.lines().anyMatch(l -> l.contains("MODIFY (") && l.contains("price")), ora);
        assertTrue(ora.contains(hand + ", 손으로 쓴다(앞 기본값은 그대로)") && !ora.contains("DEFAULT NULL"), ora);
        String pg = alterSql(typed, "postgresql");
        assertTrue(pg.lines().anyMatch(l -> l.contains("ALTER COLUMN") && l.contains(" TYPE ")), pg);
        assertTrue(pg.contains(hand) && !pg.contains("DROP DEFAULT"), pg);
    }

    /** products.price 만 f 로 바꾼 B */
    private static List<Schema> priceAs(UnaryOperator<Column> f) throws Exception {
        List<Schema> b = fixture();
        List<Table> ts = new ArrayList<>(b.get(0).tables());
        edit(ts, "products", t -> {
            List<Column> c = new ArrayList<>();
            for (Column x : t.columns()) {
                c.add(x.name().equals("price") ? f.apply(x) : x);
            }
            return cols(t, c);
        });
        return List.of(new Schema(b.get(0).name(), b.get(0).dbVersion(), ts, b.get(0).sizeBytes()));
    }

    private static String alterSql(List<Schema> b, String target) throws Exception {
        return AlterGen.generate(fixture(), b, new AlterGen.Options("postgresql", target, null, true, true, true), TypeMapping.load()).sql();
    }

    /** 마무리 리뷰 I1 — PG 의 NULL::타입 기본값은 NULL 이다(못 옮기는 식이 아니다) — 지우는 문장을 그대로 낸다 */
    @Test
    void nullCastDefaultIsCleared() throws Exception {
        List<Schema> b = fixture();
        List<Table> ts = new ArrayList<>(b.get(0).tables());
        edit(ts, "products", t -> {
            List<Column> c = new ArrayList<>();
            for (Column x : t.columns()) {
                c.add(x.name().equals("price") ? with(x, x.length(), x.nullable(), "NULL::numeric", x.comment()) : x);
            }
            return cols(t, c);
        });
        b = List.of(new Schema(b.get(0).name(), b.get(0).dbVersion(), ts, b.get(0).sizeBytes()));
        String pg = AlterGen.generate(fixture(), b, new AlterGen.Options("postgresql", "postgresql", null, true, true, true), TypeMapping.load()).sql();
        assertTrue(pg.contains("DROP DEFAULT") && !pg.contains("[손]"), pg);
        String ora = AlterGen.generate(fixture(), b, new AlterGen.Options("postgresql", "oracle", null, true, true, true), TypeMapping.load()).sql();
        assertTrue(ora.contains("DEFAULT NULL") && !ora.contains("[손]"), ora);
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

    /** 1-64 — 인덱스 DESC 를 반영 DDL 에 싣는다. ASC→DESC 만 바뀌어도 지우고 다시 */
    @Test
    void descIndexKeepsSort() throws Exception {
        List<Schema> asc = withUsersIndex(List.of(SortOrder.ASC, SortOrder.ASC));
        List<Schema> desc = withUsersIndex(List.of(SortOrder.ASC, SortOrder.DESC));
        AlterGen.Options o = new AlterGen.Options("postgresql", "postgresql", null, true, true, true);
        String c = desc.get(0).tables().stream().filter(t -> t.name().equals("users")).findFirst().orElseThrow().columns().get(1).name();
        String added = AlterGen.generate(fixture(), desc, o, TypeMapping.load()).sql();
        assertTrue(added.contains(", " + c + " DESC)"), added);
        String flipped = AlterGen.generate(asc, desc, o, TypeMapping.load()).sql();
        assertTrue(flipped.contains("DROP INDEX public.ix_users_sort"), flipped);
        assertTrue(flipped.indexOf("CREATE INDEX ix_users_sort") > flipped.indexOf("DROP INDEX public.ix_users_sort"), flipped);
        assertTrue(flipped.contains(", " + c + " DESC)"), flipped);
        assertTrue(!AlterGen.generate(fixture(), asc, o, TypeMapping.load()).sql().contains(" ASC"), "ASC 는 안 쓴다");
    }

    static List<Schema> withUsersIndex(List<SortOrder> sorts) throws Exception {
        List<Schema> b = fixture();
        List<Table> ts = new ArrayList<>(b.get(0).tables());
        edit(ts, "users", t -> {
            List<Index> ix = new ArrayList<>(t.indexes());
            ix.add(new Index("ix_users_sort", false, List.of(t.columns().get(0).name(), t.columns().get(1).name()), sorts));
            return new Table(t.schema(), t.name(), t.type(), t.comment(), t.columns(), t.pk(), t.fks(), t.uniques(), ix,
                    t.rowCount(), t.createdAt(), t.lastDdlAt(), t.checks());
        });
        return List.of(new Schema(b.get(0).name(), b.get(0).dbVersion(), ts, b.get(0).sizeBytes()));
    }

    static List<Schema> with(UnaryOperator<List<Table>> f) throws Exception {
        List<Schema> b = fixture();
        List<Table> ts = f.apply(new ArrayList<>(b.get(0).tables()));
        return List.of(new Schema(b.get(0).name(), b.get(0).dbVersion(), ts, b.get(0).sizeBytes()));
    }

    /** 1-91(R-A-I4) — 이름 없는 FK 의 대체 번호는 그 표 FK 안 순번 */
    @Test
    void fallbackFkNames() throws Exception {
        List<Schema> to = with(ts -> {
            ts.add(Table.of("public", "t91", "TABLE", null)
                    .withColumns(List.of(col("a", 1, "int8", null, 19, true, null, null), col("b", 2, "int8", null, 19, true, null, null)))
                    .withConstraints(null, List.of(new ForeignKey(null, List.of("a"), null, "users", List.of("user_id")),
                            new ForeignKey(null, List.of("b"), null, "users", List.of("user_id"))), List.of()));
            return ts;
        });
        String sql = AlterGen.generate(fixture(), to, new AlterGen.Options("postgresql", "postgresql", null, true, true, true),
                TypeMapping.load()).sql();
        assertTrue(sql.contains("ADD CONSTRAINT FK_t91_1 ") && sql.contains("ADD CONSTRAINT FK_t91_2 "), sql);
    }

    /** 1-91(R-A-I5) — 방언 모르는 스냅샷의 유니크 지우기에 호환 표식 */
    @Test
    void unknownSourceUniqueIsFlagged() throws Exception {
        List<Schema> to = with(ts -> {
            edit(ts, "products", t -> t.withConstraints(t.pk(), t.fks(), List.of()));
            return ts;
        });
        AlterGen.Result r = AlterGen.generate(fixture(), to, new AlterGen.Options("", "postgresql", null, true, true, true), TypeMapping.load());
        assertTrue(r.sql().contains("-- [확인:호환] 유니크 uq_products_code — 제약인지 인덱스인지 스냅샷이 모른다(방언 모름)"), r.sql());
        assertTrue(r.sql().contains("DROP CONSTRAINT uq_products_code"), r.sql());
        assertEquals(1, r.review(), r.sql());
        String known = AlterGen.generate(fixture(), to, new AlterGen.Options("postgresql", "postgresql", null, true, true, true),
                TypeMapping.load()).sql();
        assertFalse(known.contains("[확인:호환] 유니크"), known);
    }

    static List<Schema> usersChecks(Check... cs) throws Exception {
        return with(ts -> {
            edit(ts, "users", t -> t.withChecks(List.of(cs)));
            return ts;
        });
    }

    /** 1-91(F-K8) — CHECK 생김·사라짐·바뀜을 조건으로 짝지어 반영. 공백만 다르면 그대로, 방언이 다르면 손으로 */
    @Test
    void checkDiff() throws Exception {
        List<Schema> a = usersChecks(new Check("ck_a", "user_id > 0"), new Check("ck_b", "user_id  <  100"), new Check("ck_d", "user_id <> 5"));
        List<Schema> b = usersChecks(new Check("ck_b", "user_id < 100"), new Check("ck_d", "user_id <> 6"), new Check("ck_c", "user_id <> 7"));
        String pg = AlterGen.generate(a, b, new AlterGen.Options("postgresql", "postgresql", null, true, true, true), TypeMapping.load()).sql();
        assertTrue(pg.contains("DROP CONSTRAINT ck_a;") && pg.contains("DROP CONSTRAINT ck_d;"), pg);
        assertTrue(pg.contains("ADD CONSTRAINT ck_d CHECK (user_id <> 6);") && pg.contains("ADD CONSTRAINT ck_c CHECK (user_id <> 7);"), pg);
        assertFalse(pg.contains("ck_b"), pg);
        String ora = AlterGen.generate(a, b, new AlterGen.Options("postgresql", "oracle", null, true, true, true), TypeMapping.load()).sql();
        assertTrue(ora.contains("-- [손] users CHECK (user_id <> 7) — 방언이 달라 손으로"), ora);
    }

    /** 1-98(F-K9) — 한쪽에서만 이름이 겹쳐도 표 추가 하나만 */
    @Test
    void duplicateNameOnOneSide() throws Exception {
        List<Schema> to = with(ts -> {
            Table u = ts.stream().filter(t -> t.name().equals("users")).findFirst().orElseThrow();
            ts.add(Table.of("bak", u.name(), u.type(), u.comment()).withColumns(u.columns()).withConstraints(u.pk(), List.of(), List.of()));
            return ts;
        });
        AlterGen.Result r = AlterGen.generate(fixture(), to, new AlterGen.Options("postgresql", "postgresql", null, true, true, true), TypeMapping.load());
        assertEquals(1, r.addedTables(), r.sql());
        assertEquals(0, r.removedTables(), r.sql());
        assertEquals(1, r.sql().split("CREATE TABLE", -1).length - 1, r.sql());
    }

    static List<Schema> usersIdAuto(String comment, String type) throws Exception {
        List<Schema> b = fixture();
        List<Table> ts = new ArrayList<>(b.get(0).tables());
        edit(ts, "users", t -> {
            List<Column> cs = new ArrayList<>(t.columns());
            Column c = cs.get(0);
            cs.set(0, new Column(c.name(), c.ordinal(), type, c.jdbcType(), c.length(), c.precision(), c.scale(), c.nullable(),
                    c.defaultValue(), comment, c.domain(), true));
            return t.withColumns(cs);
        });
        return List.of(new Schema(b.get(0).name(), b.get(0).dbVersion(), ts, b.get(0).sizeBytes()));
    }

    /** 1-89(R-A-C1) — mariadb 코멘트만 바뀐 자동증가 PK 의 MODIFY 가 AUTO_INCREMENT 를 지킨다. 다른 대상의 타입 변경엔 절이 안 샌다 */
    @Test
    void mariadbCommentOnlyKeepsAutoIncrement() throws Exception {
        String maria = AlterGen.generate(usersIdAuto(null, "int8"), usersIdAuto("사용자 번호", "int8"),
                new AlterGen.Options("postgresql", "mariadb", null, true, true, true), TypeMapping.load()).sql();
        assertTrue(maria.contains("NOT NULL AUTO_INCREMENT COMMENT '사용자 번호'"), maria);
        for (String target : List.of("mssql", "postgresql", "oracle")) {
            String sql = AlterGen.generate(usersIdAuto(null, "int4"), usersIdAuto(null, "int8"),
                    new AlterGen.Options("postgresql", target, null, true, true, true), TypeMapping.load()).sql();
            assertTrue(sql.contains("user_id"), target + " " + sql);
            assertFalse(sql.contains("IDENTITY") || sql.contains("AUTO_INCREMENT"), target + " " + sql);
        }
    }

    /** 1-102 — mssql 타입 바꿈의 ALTER COLUMN 에 수집 타입 글의 identity 낱말이 안 실린다 */
    @Test
    void mssqlIdentityTypeChange() throws Exception {
        String sql = AlterGen.generate(usersIdAuto(null, "int identity"), usersIdAuto(null, "bigint identity"),
                new AlterGen.Options("mssql", "mssql", null, true, true, true), TypeMapping.load()).sql();
        assertTrue(sql.contains("ALTER COLUMN user_id bigint NOT NULL"), sql);
        assertFalse(sql.contains("bigint identity"), sql);
    }

    /** 1-88(A-28) — mariadb 표 코멘트 변경의 리터럴 역슬래시를 겹친다 */
    @Test
    void mariadbCommentBackslash() throws Exception {
        List<Schema> b = fixture();
        List<Table> ts = new ArrayList<>(b.get(0).tables());
        edit(ts, "users", t -> new Table(t.schema(), t.name(), t.type(), "C:\\x", t.columns(), t.pk(), t.fks(), t.uniques(),
                t.indexes(), t.rowCount(), t.createdAt(), t.lastDdlAt(), t.checks()));
        List<Schema> to = List.of(new Schema(b.get(0).name(), b.get(0).dbVersion(), ts, b.get(0).sizeBytes()));
        String sql = AlterGen.generate(fixture(), to, new AlterGen.Options("postgresql", "mariadb", null, true, true, true),
                TypeMapping.load()).sql();
        assertTrue(sql.contains("COMMENT = 'C:\\\\x'"), sql);
    }

    @Test
    void unknownTarget() {
        assertThrows(IllegalArgumentException.class, () -> AlterGen.generate(fixture(), fixture(), new AlterGen.Options("postgresql", "sybase", null,
                true, true, true), TypeMapping.load()));
    }
}
