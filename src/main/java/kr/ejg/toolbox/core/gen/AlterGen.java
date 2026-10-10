package kr.ejg.toolbox.core.gen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.FkRule;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.SnapshotDiff;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;

/**
 * 1-60a 반영 DDL — 두 스냅샷(앞 A → 뒤 B)의 차이를 A 쪽 DB 에 적용할 ALTER·CREATE·DROP 스크립트로(설계 23).
 * 비교 결과({@link SnapshotDiff.Result})가 아니라 스냅샷 둘을 직접 받는다 — 결과는 제약이 이름 없는 글이고 인덱스·FK 규칙이 없다.
 * 표 짝짓기는 비교와 같다({@link SnapshotDiff#index}). 조각(타입·기본값·인용·코멘트 글)은 {@link DdlGen.Gen} 을 같이 쓴다.
 * 위험 문장(표·컬럼 삭제)은 주석으로 내고 {@code [확인]} 을 단다. 실행은 하지 않는다 — 글만 만든다.
 */
public final class AlterGen {

    /**
     * @param source 원본 방언(B — 새 표·바뀐 타입의 모습). 같으면 원본 타입 그대로(7-6)
     * @param target 대상 방언(A 를 뜬 DB) — {@link DdlGen#targets()} 하나
     * @param schema 있으면 모든 이름 앞에. 없으면 있는 표는 A 쪽 표의 스키마, 새 표는 A 의 스키마가 하나면 그것
     */
    public record Options(String source, String target, String schema, boolean ignoreSchema, boolean includeIndex, boolean includeComments) {
    }

    /** review — {@code [확인]}·{@code [손]} 줄 수. statements — 살아 있는 문장 수(주석 처리한 것 빼고) */
    public record Result(String sql, List<String> warnings, int statements, int review, int addedTables, int removedTables, int changedTables) {
        public Result {
            warnings = List.copyOf(warnings);
        }
    }

    private AlterGen() {
    }

    public static Result generate(List<Schema> from, List<Schema> to, Options o, TypeMapping types) {
        DdlGen.Spec sp = DdlGen.spec();
        String target = DdlGen.lower(o.target());
        if (!sp.targets().contains(target)) {
            throw new IllegalArgumentException("반영 DDL 대상 방언: " + o.target() + " — " + String.join("·", sp.targets()));
        }
        return new Run(sp, o, target, DdlGen.lower(o.source()), types, from).run(SnapshotDiff.index(from, o.ignoreSchema()),
                SnapshotDiff.index(to, o.ignoreSchema()));
    }

    private static final class Run {
        final DdlGen.Spec sp;
        final Options o;
        final String target;
        final String source;
        final TypeMapping types;
        final Set<String> fromSchemas = new TreeSet<>();
        final Set<String> warnings = new LinkedHashSet<>();
        final Map<String, DdlGen.Gen> gens = new LinkedHashMap<>();
        final StringBuilder dropFk = new StringBuilder();
        final StringBuilder dropIdx = new StringBuilder();
        final StringBuilder create = new StringBuilder();
        final StringBuilder alter = new StringBuilder();
        final StringBuilder addIdx = new StringBuilder();
        final StringBuilder addFk = new StringBuilder();
        final StringBuilder comment = new StringBuilder();
        final StringBuilder dropTable = new StringBuilder();
        int statements;
        int review;
        Map<String, Table> a;
        Map<String, Table> b;

        Run(DdlGen.Spec sp, Options o, String target, String source, TypeMapping types, List<Schema> from) {
            this.sp = sp;
            this.o = o;
            this.target = target;
            this.source = source;
            this.types = types;
            from.forEach(s -> s.tables().forEach(t -> {
                if (!DdlGen.blank(t.schema())) {
                    fromSchemas.add(t.schema());
                }
            }));
        }

        boolean mariadb() {
            return target.equals("mariadb");
        }

        boolean mssql() {
            return target.equals("mssql");
        }

        boolean postgres() {
            return target.equals("postgresql");
        }

        boolean oracleLike() {
            return target.equals("oracle") || target.equals("tibero");
        }

        DdlGen.Gen gen(String schema) {
            String s = schema == null ? "" : schema;
            return gens.computeIfAbsent(s, k -> new DdlGen.Gen(sp, new DdlGen.Options(o.source(), target, k, false, o.includeIndex(),
                    o.includeComments(), null), target, source, types));
        }

        /** 있는 표 — A 쪽 표의 스키마 */
        String schemaOf(Table fromTable) {
            return !DdlGen.blank(o.schema()) ? o.schema() : fromTable.schema();
        }

        /** 새 표 — A 의 스키마가 하나면 그것(개발 DEV → 운영 PRD 처럼 스키마 이름이 다르다), 아니면 B 쪽 표의 것 */
        String schemaOfNew(Table toTable) {
            if (!DdlGen.blank(o.schema())) {
                return o.schema();
            }
            if (fromSchemas.size() == 1) {
                return fromSchemas.iterator().next();
            }
            if (!DdlGen.blank(toTable.schema()) && !fromSchemas.contains(toTable.schema())) {
                warnings.add(toTable.name() + ": 새 표 스키마 확인 — " + toTable.schema() + " 는 앞 스냅샷에 없다");
            }
            return toTable.schema();
        }

        void stmt(StringBuilder out, String s) {
            out.append(s).append(";\n");
            statements++;
        }

        void check(StringBuilder out, String why) {
            out.append("-- [확인] ").append(why).append('\n');
            review++;
        }

        void hand(StringBuilder out, String why) {
            out.append("-- [손] ").append(why).append('\n');
            review++;
        }

        /** 데이터가 사라지는 문장 — 주석으로 낸다(사람이 풀어 쓴다) */
        void commented(StringBuilder out, String why, String s) {
            check(out, why);
            out.append("-- ").append(s).append(";\n");
        }

        Result run(Map<String, Table> from, Map<String, Table> to) {
            this.a = from;
            this.b = to;
            List<String> added = new ArrayList<>();
            List<String> removed = new ArrayList<>();
            List<String> common = new ArrayList<>();
            to.keySet().forEach(k -> (from.containsKey(k) ? common : added).add(k));
            from.keySet().forEach(k -> {
                if (!to.containsKey(k)) {
                    removed.add(k);
                }
            });
            added.removeIf(k -> isView(to.get(k)));
            removed.removeIf(k -> isView(from.get(k)));
            common.removeIf(k -> isView(to.get(k)) || isView(from.get(k)));
            int changed = 0;
            for (String k : common) {
                if (table(from.get(k), to.get(k))) {
                    changed++;
                }
            }
            newTables(added.stream().map(to::get).toList());
            for (String k : removed) {
                Table t = from.get(k);
                commented(dropTable, "데이터가 사라진다 — 표 " + t.name(), "DROP TABLE " + gen(schemaOf(t)).tbl(t.name()));
            }
            StringBuilder body = new StringBuilder();
            section(body, "외래 키 삭제", dropFk);
            section(body, "인덱스·유니크 삭제", dropIdx);
            section(body, "새 표", create);
            section(body, "바뀐 표", alter);
            section(body, "인덱스·유니크 추가", addIdx);
            section(body, "외래 키 추가", addFk);
            section(body, "코멘트", comment);
            section(body, "표 삭제", dropTable);
            gens.values().forEach(g -> warnings.addAll(g.warnings));
            StringBuilder sql = new StringBuilder("-- 반영 DDL — 원본 ").append(source.isEmpty() ? "모름" : source).append(" → 대상 ").append(target)
                    .append(" · 표 추가 ").append(added.size()).append(" · 삭제 ").append(removed.size()).append(" · 변경 ").append(changed)
                    .append(" · 문장 ").append(statements).append(" · 확인 ").append(review).append('\n');
            if (body.length() == 0) {
                sql.append("-- 차이 없음\n");
            } else {
                sql.append(body);
            }
            return new Result(sql.toString(), new ArrayList<>(warnings), statements, review, added.size(), removed.size(), changed);
        }

        void section(StringBuilder out, String title, StringBuilder part) {
            if (part.length() > 0) {
                out.append("\n-- ").append(title).append('\n').append(part);
            }
        }

        static boolean isView(Table t) {
            return t.type() != null && t.type().toUpperCase(Locale.ROOT).contains("VIEW");
        }

        // ---------------------------------------------------------------- 새 표

        void newTables(List<Table> tables) {
            if (tables.isEmpty()) {
                return;
            }
            Map<String, List<Table>> bySchema = new LinkedHashMap<>();
            tables.forEach(t -> bySchema.computeIfAbsent(schemaOfNew(t) == null ? "" : schemaOfNew(t), k -> new ArrayList<>()).add(t));
            bySchema.forEach((schema, list) -> {
                DdlGen.Result r = DdlGen.generate(list, new DdlGen.Options(o.source(), target, schema, false, o.includeIndex(), o.includeComments(),
                        null), types);
                String sql = r.sql().substring(r.sql().indexOf('\n') + 1); // 「-- DDL 생성 — …」 머리 한 줄 뺌
                create.append(sql.startsWith("\n") ? sql.substring(1) : sql);
                for (String line : sql.split("\n")) {
                    if (!line.startsWith("--") && line.trim().endsWith(";")) {
                        statements++;
                    }
                }
                warnings.addAll(r.warnings());
                for (Table t : list) {
                    for (ForeignKey fk : t.fks()) {
                        addFk(t, fk, schema);
                    }
                }
            });
        }

        // ---------------------------------------------------------------- 바뀐 표

        /** 바뀐 것이 있으면 true */
        boolean table(Table ta, Table tb) {
            DdlGen.Gen g = gen(schemaOf(ta));
            String tbl = g.tbl(ta.name());
            StringBuilder out = new StringBuilder();
            int before = statements + review;
            Map<String, Column> ca = cols(ta);
            Map<String, Column> cb = cols(tb);
            Set<String> pkB = new HashSet<>(tb.pk() == null ? List.of() : DdlGen.upper(tb.pk().columns()));
            List<Column> addCols = new ArrayList<>();
            List<Column> dropCols = new ArrayList<>();
            sorted(tb).forEach(c -> {
                if (!ca.containsKey(up(c.name()))) {
                    addCols.add(c);
                }
            });
            sorted(ta).forEach(c -> {
                if (!cb.containsKey(up(c.name()))) {
                    dropCols.add(c);
                }
            });
            for (Column c : addCols) {
                addColumn(out, g, tbl, tb, c, pkB.contains(up(c.name())));
            }
            for (Column c : sorted(tb)) {
                Column old = ca.get(up(c.name()));
                if (old != null) {
                    modifyColumn(out, g, tbl, tb, old, c, pkB.contains(up(c.name())));
                }
            }
            if (!addCols.isEmpty() && !dropCols.isEmpty()) {
                check(out, "같은 표에 컬럼 삭제와 추가가 같이 있다 — 이름이 바뀐 것이면 삭제·추가 대신 RENAME 으로");
            }
            for (Column c : dropCols) {
                commented(out, "데이터가 사라진다 — 컬럼 " + ta.name() + "." + c.name(), "ALTER TABLE " + tbl + " DROP COLUMN " + g.id(c.name()));
            }
            primaryKey(out, g, tbl, ta, tb);
            uniquesAndIndexes(g, tbl, ta, tb);
            foreignKeys(g, ta, tb);
            if (o.includeComments()) {
                comments(g, tbl, ta, tb, addCols);
            }
            if (out.length() > 0) {
                alter.append("-- ").append(ta.name()).append('\n').append(out);
            }
            return statements + review > before;
        }

        void addColumn(StringBuilder out, DdlGen.Gen g, String tbl, Table tb, Column c, boolean inPk) {
            String def = g.column(tb, c, inPk);
            if (!c.nullable() && g.defaultOf(tb, c, g.type(tb, c)) == null) {
                check(out, "NOT NULL 컬럼 " + c.name() + " — 기존 행이 있으면 실패한다, DEFAULT 를 넣는다");
            }
            if (oracleLike()) {
                stmt(out, "ALTER TABLE " + tbl + " ADD (" + def + ")");
            } else if (mssql()) {
                stmt(out, "ALTER TABLE " + tbl + " ADD " + def);
            } else {
                stmt(out, "ALTER TABLE " + tbl + " ADD COLUMN " + def);
            }
        }

        void modifyColumn(StringBuilder out, DdlGen.Gen g, String tbl, Table tb, Column old, Column neu, boolean inPk) {
            boolean typeChanged = !Objects.equals(old.nativeType(), neu.nativeType()) || !Objects.equals(old.length(), neu.length())
                    || !Objects.equals(old.precision(), neu.precision()) || !Objects.equals(old.scale(), neu.scale());
            boolean nullChanged = old.nullable() != neu.nullable();
            boolean defChanged = !Objects.equals(norm(old.defaultValue()), norm(neu.defaultValue()));
            boolean cmtChanged = o.includeComments() && !Objects.equals(norm(old.comment()), norm(neu.comment()));
            if (!typeChanged && !nullChanged && !defChanged && !(mariadb() && cmtChanged)) {
                return;
            }
            String col = g.id(neu.name());
            String type = g.type(tb, neu);
            String def = g.defaultOf(tb, neu, type);
            boolean toNotNull = nullChanged && !neu.nullable();
            if (typeChanged && shrinks(old, neu)) {
                check(out, "컬럼 " + neu.name() + " 타입이 줄거나 바뀐다 — 값이 잘리거나 변환이 실패할 수 있다");
            }
            if (toNotNull) {
                check(out, "컬럼 " + neu.name() + " NOT NULL — NULL 행이 있으면 실패한다");
            }
            if (mariadb()) {
                if (typeChanged || nullChanged || cmtChanged) {
                    stmt(out, "ALTER TABLE " + tbl + " MODIFY COLUMN " + g.column(tb, neu, inPk));
                } else {
                    stmt(out, "ALTER TABLE " + tbl + " ALTER COLUMN " + col + (def == null ? " DROP DEFAULT" : " SET DEFAULT " + def));
                }
                return;
            }
            if (mssql()) {
                if (typeChanged || nullChanged) {
                    stmt(out, "ALTER TABLE " + tbl + " ALTER COLUMN " + col + " " + type + (neu.nullable() && !inPk ? " NULL" : " NOT NULL"));
                }
                if (defChanged) {
                    if (DdlGen.blank(old.defaultValue()) && def != null) {
                        stmt(out, "ALTER TABLE " + tbl + " ADD CONSTRAINT " + g.cname("DF_" + tb.name() + "_" + neu.name()) + " DEFAULT " + def + " FOR " + col);
                    } else {
                        hand(out, "컬럼 " + neu.name() + " 기본값 — 기본값 제약 이름을 sp_helpconstraint 로 찾아 DROP 한다"
                                + (def == null ? "" : ", 그 뒤 ADD CONSTRAINT … DEFAULT " + def + " FOR " + col));
                    }
                }
                return;
            }
            if (postgres()) {
                if (typeChanged) {
                    if (!Objects.equals(types.javaType(old, nullIfBlank(source)), types.javaType(neu, nullIfBlank(source)))) {
                        check(out, "컬럼 " + neu.name() + " 타입 계열이 바뀐다 — 변환이 안 되면 USING " + col + "::" + type + " 을 붙인다");
                    }
                    stmt(out, "ALTER TABLE " + tbl + " ALTER COLUMN " + col + " TYPE " + type);
                }
                if (defChanged) {
                    stmt(out, "ALTER TABLE " + tbl + " ALTER COLUMN " + col + (def == null ? " DROP DEFAULT" : " SET DEFAULT " + def));
                }
                if (nullChanged) {
                    stmt(out, "ALTER TABLE " + tbl + " ALTER COLUMN " + col + (neu.nullable() ? " DROP NOT NULL" : " SET NOT NULL"));
                }
                return;
            }
            // oracle·tibero — 항목마다 한 문장
            if (typeChanged) {
                stmt(out, "ALTER TABLE " + tbl + " MODIFY (" + col + " " + type + ")");
            }
            if (defChanged) {
                stmt(out, "ALTER TABLE " + tbl + " MODIFY (" + col + " DEFAULT " + (def == null ? "NULL" : def) + ")");
            }
            if (nullChanged) {
                stmt(out, "ALTER TABLE " + tbl + " MODIFY (" + col + (neu.nullable() ? " NULL" : " NOT NULL") + ")");
            }
        }

        boolean shrinks(Column old, Column neu) {
            if (old.length() != null && neu.length() != null && neu.length() < old.length()) {
                return true;
            }
            if (old.precision() != null && neu.precision() != null && neu.precision() < old.precision()) {
                return true;
            }
            if (old.scale() != null && neu.scale() != null && neu.scale() < old.scale()) {
                return true;
            }
            return !Objects.equals(types.javaType(old, nullIfBlank(source)), types.javaType(neu, nullIfBlank(source)));
        }

        void primaryKey(StringBuilder out, DdlGen.Gen g, String tbl, Table ta, Table tb) {
            List<String> pa = ta.pk() == null ? List.of() : DdlGen.upper(ta.pk().columns());
            List<String> pb = tb.pk() == null ? List.of() : DdlGen.upper(tb.pk().columns());
            if (pa.equals(pb)) {
                return;
            }
            if (!pa.isEmpty()) {
                String name = DdlGen.nameOr(ta.pk().name(), null);
                if (mariadb()) {
                    stmt(out, "ALTER TABLE " + tbl + " DROP PRIMARY KEY");
                } else if (name == null) {
                    hand(out, "PK 이름을 모른다 — PK(" + String.join(",", ta.pk().columns()) + ") 를 이름으로 찾아 DROP CONSTRAINT");
                } else {
                    stmt(out, "ALTER TABLE " + tbl + " DROP CONSTRAINT " + g.cname(name));
                }
            }
            if (!pb.isEmpty()) {
                if (mariadb()) {
                    stmt(out, "ALTER TABLE " + tbl + " ADD PRIMARY KEY (" + g.idList(tb.pk().columns()) + ")");
                } else {
                    stmt(out, "ALTER TABLE " + tbl + " ADD CONSTRAINT " + g.cname(DdlGen.nameOr(tb.pk().name(), "PK_" + tb.name())) + " PRIMARY KEY ("
                            + g.idList(tb.pk().columns()) + ")");
                }
            }
        }

        /** 유니크·인덱스 — 이름(없으면 구조 글)으로 짝짓고, 구조가 다르면 지우고 다시. PK·유니크와 같은 컬럼의 인덱스는 DdlGen 처럼 뺀다 */
        void uniquesAndIndexes(DdlGen.Gen g, String tbl, Table ta, Table tb) {
            Map<String, UniqueKey> ua = uniques(ta);
            Map<String, UniqueKey> ub = uniques(tb);
            ua.forEach((k, u) -> {
                UniqueKey nu = ub.get(k);
                if (nu == null || !DdlGen.upper(nu.columns()).equals(DdlGen.upper(u.columns()))) {
                    String name = DdlGen.nameOr(u.name(), null);
                    if (name == null) {
                        hand(dropIdx, ta.name() + " 유니크 이름을 모른다 — UQ(" + String.join(",", u.columns()) + ")");
                    } else if (mariadb()) {
                        stmt(dropIdx, "ALTER TABLE " + tbl + " DROP INDEX " + g.cname(name));
                    } else {
                        stmt(dropIdx, "ALTER TABLE " + tbl + " DROP CONSTRAINT " + g.cname(name));
                    }
                }
            });
            int n = 0;
            for (Map.Entry<String, UniqueKey> e : ub.entrySet()) {
                n++;
                UniqueKey old = ua.get(e.getKey());
                UniqueKey u = e.getValue();
                if (old == null || !DdlGen.upper(old.columns()).equals(DdlGen.upper(u.columns()))) {
                    stmt(addIdx, "ALTER TABLE " + tbl + " ADD CONSTRAINT " + g.cname(DdlGen.nameOr(u.name(), "UK_" + tb.name() + "_" + n)) + " UNIQUE ("
                            + g.idList(u.columns()) + ")");
                }
            }
            if (!o.includeIndex()) {
                return;
            }
            Map<String, Index> ia = indexes(ta);
            Map<String, Index> ib = indexes(tb);
            ia.forEach((k, ix) -> {
                Index nx = ib.get(k);
                if (nx == null || !shape(nx).equals(shape(ix))) {
                    String name = DdlGen.nameOr(ix.name(), null);
                    if (name == null) {
                        hand(dropIdx, ta.name() + " 인덱스 이름을 모른다 — " + shape(ix));
                    } else if (mariadb() || mssql()) {
                        stmt(dropIdx, "DROP INDEX " + g.cname(name) + " ON " + tbl);
                    } else {
                        String schema = schemaOf(ta);
                        stmt(dropIdx, "DROP INDEX " + (DdlGen.blank(schema) ? "" : g.id(schema) + ".") + g.cname(name));
                    }
                }
            });
            int x = 0;
            for (Map.Entry<String, Index> e : ib.entrySet()) {
                x++;
                Index old = ia.get(e.getKey());
                Index ix = e.getValue();
                if (old == null || !shape(old).equals(shape(ix))) {
                    stmt(addIdx, (ix.unique() ? "CREATE UNIQUE INDEX " : "CREATE INDEX ") + g.cname(DdlGen.nameOr(ix.name(), "IX_" + tb.name() + "_" + x))
                            + " ON " + tbl + " (" + g.idList(ix.columns()) + ")");
                }
            }
        }

        void foreignKeys(DdlGen.Gen g, Table ta, Table tb) {
            Map<String, ForeignKey> fa = fks(ta);
            Map<String, ForeignKey> fb = fks(tb);
            String tbl = g.tbl(ta.name());
            fa.forEach((k, fk) -> {
                ForeignKey nf = fb.get(k);
                if (nf == null || !fkShape(nf).equals(fkShape(fk))) {
                    String name = DdlGen.nameOr(fk.name(), null);
                    if (name == null) {
                        hand(dropFk, ta.name() + " 외래 키 이름을 모른다 — " + fkShape(fk));
                    } else if (mariadb()) {
                        stmt(dropFk, "ALTER TABLE " + tbl + " DROP FOREIGN KEY " + g.cname(name));
                    } else {
                        stmt(dropFk, "ALTER TABLE " + tbl + " DROP CONSTRAINT " + g.cname(name));
                    }
                }
            });
            fb.forEach((k, fk) -> {
                ForeignKey old = fa.get(k);
                if (old == null || !fkShape(old).equals(fkShape(fk))) {
                    addFk(ta, fk, schemaOf(ta));
                }
            });
        }

        /** 참조 표는 A·B 어디서든 찾는다(새 표를 가리키는 FK 도) */
        void addFk(Table t, ForeignKey fk, String schema) {
            DdlGen.Gen g = gen(schema);
            Table ref = findRef(fk);
            String name = DdlGen.nameOr(fk.name(), "FK_" + t.name() + "_" + (fks(t).size()));
            if (ref == null) {
                warnings.add(t.name() + ": FK " + name + " — 참조 표 " + fk.refTable() + " 를 못 찾아 건너뜀");
                addFk.append("-- [건너뜀] ").append(name).append(" → ").append(fk.refTable()).append('\n');
                return;
            }
            List<String> refCols = fk.refColumns().isEmpty() && ref.pk() != null ? ref.pk().columns() : fk.refColumns();
            String refSchema = a.containsValue(ref) ? schemaOf(ref) : schemaOfNew(ref);
            StringBuilder s = new StringBuilder("ALTER TABLE ").append(g.tbl(t.name())).append(" ADD CONSTRAINT ").append(g.cname(name))
                    .append(" FOREIGN KEY (").append(g.idList(fk.columns())).append(") REFERENCES ").append(gen(refSchema).tbl(ref.name()))
                    .append(" (").append(g.idList(refCols)).append(")");
            rule(s, "DELETE", fk.deleteRule(), t, name);
            rule(s, "UPDATE", fk.updateRule(), t, name);
            stmt(addFk, s.toString());
        }

        void rule(StringBuilder s, String what, FkRule r, Table t, String name) {
            if (r == null || r == FkRule.NO_ACTION || r == FkRule.RESTRICT) {
                return; // 기본 동작 — 안 쓴다
            }
            if (oracleLike() && (what.equals("UPDATE") || r == FkRule.SET_DEFAULT)) {
                warnings.add(t.name() + ": FK " + name + " — " + target + " 는 ON " + what + " " + r.label() + " 를 못 쓴다, 뺐다");
                return;
            }
            if (mssql() && r == FkRule.RESTRICT) {
                return;
            }
            s.append(" ON ").append(what).append(' ').append(r.label());
        }

        Table findRef(ForeignKey fk) {
            if (fk.refTable() == null) {
                return null;
            }
            for (Map<String, Table> m : List.of(b, a)) {
                for (Table t : m.values()) {
                    if (t.name().equalsIgnoreCase(fk.refTable()) && (o.ignoreSchema() || fk.refSchema() == null
                            || fk.refSchema().equalsIgnoreCase(String.valueOf(t.schema())))) {
                        return t;
                    }
                }
            }
            return null;
        }

        void comments(DdlGen.Gen g, String tbl, Table ta, Table tb, List<Column> addCols) {
            if (!Objects.equals(norm(ta.comment()), norm(tb.comment()))) {
                if (mariadb()) {
                    stmt(comment, "ALTER TABLE " + tbl + " COMMENT = " + DdlGen.lit(nz(tb.comment())));
                } else if (mssql()) {
                    mssqlComment(g, ta, null, ta.comment(), tb.comment());
                } else {
                    stmt(comment, "COMMENT ON TABLE " + tbl + " IS " + DdlGen.lit(nz(tb.comment())));
                }
            }
            if (mariadb()) {
                return; // 컬럼 코멘트는 MODIFY COLUMN·ADD COLUMN 안에 인라인
            }
            Map<String, Column> ca = cols(ta);
            Set<String> fresh = new HashSet<>();
            addCols.forEach(c -> fresh.add(up(c.name())));
            for (Column c : sorted(tb)) {
                Column old = ca.get(up(c.name()));
                String before = fresh.contains(up(c.name())) || old == null ? null : old.comment();
                if (Objects.equals(norm(before), norm(c.comment()))) {
                    continue;
                }
                if (mssql()) {
                    mssqlComment(g, ta, c.name(), before, c.comment());
                } else {
                    stmt(comment, "COMMENT ON COLUMN " + tbl + "." + g.id(c.name()) + " IS " + DdlGen.lit(nz(c.comment())));
                }
            }
        }

        /** MS SQL 코멘트 — 앞 값이 없으면 add, 있으면 update, 새 값이 없으면 drop(DdlGen 의 확장 속성 꼴) */
        void mssqlComment(DdlGen.Gen g, Table t, String column, String before, String after) {
            String schema = DdlGen.blank(schemaOf(t)) ? "dbo" : schemaOf(t);
            String proc = DdlGen.blank(before) ? "sp_addextendedproperty" : DdlGen.blank(after) ? "sp_dropextendedproperty" : "sp_updateextendedproperty";
            StringBuilder s = new StringBuilder("EXEC sys.").append(proc).append(" @name=N'MS_Description'");
            if (!DdlGen.blank(after)) {
                s.append(", @value=N").append(DdlGen.lit(after));
            }
            s.append(", @level0type=N'SCHEMA', @level0name=N").append(DdlGen.lit(schema)).append(", @level1type=N'TABLE', @level1name=N")
                    .append(DdlGen.lit(t.name()));
            if (column != null) {
                s.append(", @level2type=N'COLUMN', @level2name=N").append(DdlGen.lit(column));
            }
            stmt(comment, s.toString());
        }

        // ---------------------------------------------------------------- 짝짓기 도우미

        static Map<String, Column> cols(Table t) {
            Map<String, Column> m = new LinkedHashMap<>();
            t.columns().forEach(c -> m.put(up(c.name()), c));
            return m;
        }

        static List<Column> sorted(Table t) {
            List<Column> l = new ArrayList<>(t.columns());
            l.sort(Comparator.comparingInt(Column::ordinal));
            return l;
        }

        /** PK 와 같은 컬럼의 유니크는 뺀다(DdlGen 과 같다 — H2 메타가 PK 를 유니크로도 낸다) */
        static Map<String, UniqueKey> uniques(Table t) {
            List<String> pk = t.pk() == null ? List.of() : DdlGen.upper(t.pk().columns());
            Map<String, UniqueKey> m = new LinkedHashMap<>();
            for (UniqueKey u : t.uniques()) {
                if (u.columns().isEmpty() || DdlGen.upper(u.columns()).equals(pk)) {
                    continue;
                }
                String n = DdlGen.nameOr(u.name(), null);
                m.put(n != null ? up(n) : "UQ(" + String.join(",", DdlGen.upper(u.columns())) + ")", u);
            }
            return m;
        }

        /** PK·유니크와 같은 컬럼 목록의 인덱스는 뺀다(그 제약이 만든다 — DdlGen 과 같다) */
        static Map<String, Index> indexes(Table t) {
            List<List<String>> keys = new ArrayList<>();
            if (t.pk() != null) {
                keys.add(DdlGen.upper(t.pk().columns()));
            }
            t.uniques().forEach(u -> keys.add(DdlGen.upper(u.columns())));
            Map<String, Index> m = new LinkedHashMap<>();
            for (Index ix : t.indexes()) {
                if (ix.columns().isEmpty() || keys.contains(DdlGen.upper(ix.columns()))) {
                    continue;
                }
                String n = DdlGen.nameOr(ix.name(), null);
                m.put(n != null ? up(n) : shape(ix), ix);
            }
            return m;
        }

        static String shape(Index ix) {
            // 정렬은 DESC 가 있을 때만 — 수집기마다 ASC 를 [ASC]·빈 목록·null 로 다르게 낸다
            boolean desc = ix.sorts() != null && ix.sorts().contains(kr.ejg.toolbox.core.meta.SortOrder.DESC);
            return (ix.unique() ? "UNIQUE " : "") + "IX(" + String.join(",", DdlGen.upper(ix.columns())) + ")" + (desc ? ix.sorts() : "");
        }

        static Map<String, ForeignKey> fks(Table t) {
            Map<String, ForeignKey> m = new LinkedHashMap<>();
            for (ForeignKey fk : t.fks()) {
                String n = DdlGen.nameOr(fk.name(), null);
                m.put(n != null ? up(n) : fkShape(fk), fk);
            }
            return m;
        }

        static String fkShape(ForeignKey fk) {
            return "FK(" + String.join(",", DdlGen.upper(fk.columns())) + ")->" + up(String.valueOf(fk.refTable())) + "("
                    + String.join(",", DdlGen.upper(fk.refColumns())) + ") " + FkRule.label(fk.deleteRule()) + "/" + FkRule.label(fk.updateRule());
        }

        static String up(String s) {
            return s == null ? "" : s.toUpperCase(Locale.ROOT);
        }

        static String norm(String s) {
            return s == null || s.isBlank() ? null : s.trim();
        }

        static String nz(String s) {
            return s == null ? "" : s;
        }

        static String nullIfBlank(String s) {
            return s == null || s.isEmpty() ? null : s;
        }
    }
}
