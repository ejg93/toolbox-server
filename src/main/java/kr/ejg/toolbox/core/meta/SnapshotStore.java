package kr.ejg.toolbox.core.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.ejg.toolbox.core.db.Db;

/**
 * 메타모델 스냅샷을 H2 에 저장·조회한다(5-3, 1-5). 한 스냅샷 = 한 접속의 수집 결과.
 * 목록 값(컬럼 목록)은 JSON 배열 문자열. 읽는 순서는 수집기와 같다 — 스키마·테이블·제약·인덱스는 이름순, 컬럼은 순번순 —
 * 그래서 저장 → 조회가 {@code equals}.
 */
public final class SnapshotStore {

    /**
     * 목록 한 줄. {@code filtered} = 찍을 때 범위로 걸렀는지(1-14) — 거른 스냅샷끼리 비교하면 빠진 표가 삭제로 보인다.
     * {@code scope} 는 찍을 때 쓴 범위(옛 행은 null), {@code warningCount} 는 벤더 SQL 물러섬 건수
     */
    public record Summary(long id, String profile, String connId, LocalDateTime takenAt, String note, String dbVersion,
            int tableCount, boolean filtered, Scope scope, int warningCount) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final Db db;

    public SnapshotStore(Db db) {
        this.db = db;
    }

    /** 스냅샷이 행으로 들어가는 H2 파일(파일이 따로 생기지 않는다) */
    public Path file() {
        return db.file();
    }

    /** 범위·경고 없이 — 수집이 아닌 길(DDL 읽기·시험) */
    public long save(String profile, String connId, String note, List<Schema> schemas) throws SQLException {
        return save(profile, connId, note, schemas, null, List.of());
    }

    /** @param scope 찍을 때 쓴 범위(없으면 null) @param warnings 벤더 SQL 물러섬 한 줄씩 */
    public long save(String profile, String connId, String note, List<Schema> schemas, Scope scope, List<String> warnings)
            throws SQLException {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                long id = insertSnapshot(c, profile, connId, note, schemas, scope, warnings);
                for (Schema s : schemas) {
                    for (Table t : s.tables()) {
                        insertTable(c, id, t);
                    }
                }
                c.commit();
                return id;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private static long insertSnapshot(Connection c, String profile, String connId, String note, List<Schema> schemas,
            Scope scope, List<String> warnings) throws SQLException {
        String version = schemas.isEmpty() ? null : schemas.get(0).dbVersion();
        List<String> names = schemas.stream().map(Schema::name).toList();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO snapshot(profile, conn_id, note, db_version, schemas, scope, warnings) VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, profile);
            ps.setString(2, connId);
            ps.setString(3, note);
            ps.setString(4, version);
            ps.setString(5, json(names));
            ps.setString(6, scope == null ? null : json(scope));
            ps.setString(7, warnings == null || warnings.isEmpty() ? null : json(warnings));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void insertTable(Connection c, long id, Table t) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO snap_table(snapshot_id, schema_name, table_name, table_type, comment, row_count, created_at,"
                + " last_ddl_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, t.schema());
            ps.setString(3, t.name());
            ps.setString(4, t.type());
            ps.setString(5, t.comment());
            setLong(ps, 6, t.rowCount());
            setTime(ps, 7, t.createdAt());
            setTime(ps, 8, t.lastDdlAt());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO snap_column(snapshot_id, schema_name, table_name, name, ordinal, native_type, jdbc_type, length,"
                + " precision, scale, nullable, default_value, comment, domain) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (Column col : t.columns()) {
                ps.setLong(1, id);
                ps.setString(2, t.schema());
                ps.setString(3, t.name());
                ps.setString(4, col.name());
                ps.setInt(5, col.ordinal());
                ps.setString(6, col.nativeType());
                setInt(ps, 7, col.jdbcType());
                setLong(ps, 8, col.length());
                setInt(ps, 9, col.precision());
                setInt(ps, 10, col.scale());
                ps.setBoolean(11, col.nullable());
                ps.setString(12, col.defaultValue());
                ps.setString(13, col.comment());
                ps.setString(14, col.domain());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO snap_constraint(snapshot_id, schema_name, table_name, name, kind, columns, ref_schema, ref_table,"
                + " ref_columns) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            if (t.pk() != null) {
                constraint(ps, id, t, t.pk().name() == null ? "" : t.pk().name(), "PK", t.pk().columns(), null, null, null);
            }
            for (ForeignKey fk : t.fks()) {
                constraint(ps, id, t, fk.name(), "FK", fk.columns(), fk.refSchema(), fk.refTable(), fk.refColumns());
            }
            for (UniqueKey uq : t.uniques()) {
                constraint(ps, id, t, uq.name(), "UQ", uq.columns(), null, null, null);
            }
            ps.executeBatch();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO snap_index(snapshot_id, schema_name, table_name, name, is_unique, columns) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (Index ix : t.indexes()) {
                ps.setLong(1, id);
                ps.setString(2, t.schema());
                ps.setString(3, t.name());
                ps.setString(4, ix.name());
                ps.setBoolean(5, ix.unique());
                ps.setString(6, json(ix.columns()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void constraint(PreparedStatement ps, long id, Table t, String name, String kind, List<String> cols,
            String refSchema, String refTable, List<String> refCols) throws SQLException {
        ps.setLong(1, id);
        ps.setString(2, t.schema());
        ps.setString(3, t.name());
        ps.setString(4, name);
        ps.setString(5, kind);
        ps.setString(6, json(cols));
        ps.setString(7, refSchema);
        ps.setString(8, refTable);
        ps.setString(9, refCols == null ? null : json(refCols));
        ps.addBatch();
    }

    public List<Summary> list() throws SQLException {
        List<Summary> out = new ArrayList<>();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT s.id, s.profile, s.conn_id, s.taken_at, s.note, s.db_version,"
                + " (SELECT COUNT(*) FROM snap_table t WHERE t.snapshot_id = s.id), s.scope, s.warnings"
                + " FROM snapshot s ORDER BY s.id DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Scope scope = scope(rs.getString(8));
                out.add(new Summary(rs.getLong(1), rs.getString(2), rs.getString(3), time(rs, 4), rs.getString(5),
                        rs.getString(6), rs.getInt(7), filtered(scope), scope, strings(rs.getString(9)).size()));
            }
        }
        return out;
    }

    /** 스키마 지정·include·exclude 중 하나라도 있거나 skipEmpty 면 거른 것. 옛 행(null)은 안 거른 것 */
    static boolean filtered(Scope scope) {
        if (scope == null) {
            return false;
        }
        Scope.Exclude ex = scope.exclude();
        boolean excludes = ex != null
                && !(ex.prefixes().isEmpty() && ex.suffixes().isEmpty() && ex.regex().isEmpty() && ex.tables().isEmpty());
        boolean includes = scope.include() != null && !scope.include().tables().isEmpty();
        return !scope.schemas().isEmpty() || excludes || includes || Boolean.TRUE.equals(scope.skipEmpty());
    }

    private static Scope scope(String json) {
        if (json == null) {
            return null;
        }
        try {
            return JSON.readValue(json, Scope.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("스냅샷 scope JSON 이 깨졌다", e);
        }
    }

    /** 스냅샷 하나를 수집 결과 모양으로. 없으면 empty */
    public Optional<List<Schema>> get(long id) throws SQLException {
        try (Connection c = db.connect()) {
            String version;
            List<String> schemaNames;
            try (PreparedStatement ps = c.prepareStatement("SELECT db_version, schemas FROM snapshot WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    version = rs.getString(1);
                    schemaNames = strings(rs.getString(2));
                }
            }
            Map<String, List<Table>> bySchema = new LinkedHashMap<>();
            schemaNames.forEach(n -> bySchema.put(n, new ArrayList<>()));
            for (Table t : tables(c, id, null, null)) {
                bySchema.computeIfAbsent(t.schema(), k -> new ArrayList<>()).add(t);
            }
            List<Schema> out = new ArrayList<>();
            bySchema.forEach((n, ts) -> out.add(new Schema(n, version, ts)));
            return Optional.of(out);
        }
    }

    /** 테이블 하나. schema 가 null 이면 이름만으로(여럿이면 스키마 이름순 첫째) */
    public Optional<Table> getTable(long id, String schema, String name) throws SQLException {
        try (Connection c = db.connect()) {
            List<Table> ts = tables(c, id, schema, name);
            return ts.isEmpty() ? Optional.empty() : Optional.of(ts.get(0));
        }
    }

    private static List<Table> tables(Connection c, long id, String schema, String name) throws SQLException {
        List<Table> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT schema_name, table_name, table_type, comment, row_count, created_at, last_ddl_at FROM snap_table"
                + " WHERE snapshot_id = ? AND (? IS NULL OR schema_name = ?) AND (? IS NULL OR table_name = ?)"
                + " ORDER BY schema_name, table_name")) {
            ps.setLong(1, id);
            ps.setString(2, schema);
            ps.setString(3, schema);
            ps.setString(4, name);
            ps.setString(5, name);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Table t = Table.of(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
                    Long rows = rs.getLong(5);
                    if (rs.wasNull()) {
                        rows = null;
                    }
                    out.add(t.withStats(rows, time(rs, 6), time(rs, 7)));
                }
            }
        }
        List<Table> full = new ArrayList<>();
        for (Table t : out) {
            full.add(fill(c, id, t));
        }
        return full;
    }

    private static Table fill(Connection c, long id, Table t) throws SQLException {
        List<Column> cols = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name, ordinal, native_type, jdbc_type, length, precision, scale, nullable, default_value, comment, domain"
                + " FROM snap_column WHERE snapshot_id = ? AND schema_name = ? AND table_name = ? ORDER BY ordinal")) {
            bindTable(ps, id, t);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cols.add(new Column(rs.getString(1), rs.getInt(2), rs.getString(3), intOrNull(rs, 4), longOrNull(rs, 5),
                            intOrNull(rs, 6), intOrNull(rs, 7), rs.getBoolean(8), rs.getString(9), rs.getString(10),
                            rs.getString(11)));
                }
            }
        }
        PrimaryKey pk = null;
        List<ForeignKey> fks = new ArrayList<>();
        List<UniqueKey> uqs = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name, kind, columns, ref_schema, ref_table, ref_columns FROM snap_constraint"
                + " WHERE snapshot_id = ? AND schema_name = ? AND table_name = ? ORDER BY kind, name")) {
            bindTable(ps, id, t);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String n = rs.getString(1);
                    List<String> colsJson = strings(rs.getString(3));
                    switch (rs.getString(2)) {
                        case "PK" -> pk = new PrimaryKey(n.isEmpty() ? null : n, colsJson);
                        case "FK" -> fks.add(new ForeignKey(n, colsJson, rs.getString(4), rs.getString(5), strings(rs.getString(6))));
                        case "UQ" -> uqs.add(new UniqueKey(n, colsJson));
                        default -> throw new IllegalStateException("모르는 제약 종류: " + rs.getString(2));
                    }
                }
            }
        }
        List<Index> ixs = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name, is_unique, columns FROM snap_index WHERE snapshot_id = ? AND schema_name = ? AND table_name = ?"
                + " ORDER BY name")) {
            bindTable(ps, id, t);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ixs.add(new Index(rs.getString(1), rs.getBoolean(2), strings(rs.getString(3))));
                }
            }
        }
        return t.withColumns(cols).withConstraints(pk, fks, uqs).withIndexes(ixs);
    }

    private static void bindTable(PreparedStatement ps, long id, Table t) throws SQLException {
        ps.setLong(1, id);
        ps.setString(2, t.schema());
        ps.setString(3, t.name());
    }

    private static String json(Object xs) {
        try {
            return JSON.writeValueAsString(xs);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> strings(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return JSON.readValue(json, STRINGS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("스냅샷 목록 값이 JSON 이 아니다", e);
        }
    }

    private static void setLong(PreparedStatement ps, int i, Long v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.BIGINT);
        } else {
            ps.setLong(i, v);
        }
    }

    private static void setInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.INTEGER);
        } else {
            ps.setInt(i, v);
        }
    }

    private static void setTime(PreparedStatement ps, int i, LocalDateTime v) throws SQLException {
        if (v == null) {
            ps.setNull(i, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(i, Timestamp.valueOf(v));
        }
    }

    private static LocalDateTime time(ResultSet rs, int i) throws SQLException {
        Timestamp t = rs.getTimestamp(i);
        return t == null ? null : t.toLocalDateTime();
    }

    private static Integer intOrNull(ResultSet rs, int i) throws SQLException {
        int v = rs.getInt(i);
        return rs.wasNull() ? null : v;
    }

    private static Long longOrNull(ResultSet rs, int i) throws SQLException {
        long v = rs.getLong(i);
        return rs.wasNull() ? null : v;
    }
}
