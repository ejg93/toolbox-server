package kr.ejg.toolbox.core.analyze;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import kr.ejg.toolbox.core.db.Db;

/**
 * 프로그램 분석 이력(H2 {@code analyze_*}, V002). 식별자·CRUD 글자·설명 100자·미해결 자리만 — SQL·코드 본문은 없다(규칙 3).
 */
public final class AnalyzeStore {

    public record RunInfo(long id, String profile, LocalDateTime startedAt, String path, int programs, int statements, int tables,
            int unresolved) {
    }

    /** 저장된 프로그램 하나 — crud 는 표 → 글자(정렬) */
    public record ProgramRow(long id, String className, String method, String file, int line, String verb, String url, String params,
            String kind, String description, List<JavaGraph.View> views, List<JavaGraph.Stmt> statements, Map<String, String> crud) {

        public ProgramRow {
            views = List.copyOf(views);
            statements = List.copyOf(statements);
            crud = java.util.Collections.unmodifiableMap(new TreeMap<>(crud));
        }
    }

    /** CRUD 매트릭스 — 열(표 이름 정렬)과 행(프로그램) */
    public record Matrix(List<String> tables, List<ProgramRow> rows) {

        public Matrix {
            tables = List.copyOf(tables);
            rows = List.copyOf(rows);
        }
    }

    private final Db db;

    public AnalyzeStore(Db db) {
        this.db = db;
    }

    public long save(String profile, String path, AnalyzeRunner.Result r) throws SQLException {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            long id;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO analyze_run(profile, path, programs, statements, tables, unresolved) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, profile);
                ps.setString(2, cut(path, 1000));
                ps.setInt(3, r.rows().size());
                ps.setInt(4, r.statements());
                ps.setInt(5, r.tables().size());
                ps.setInt(6, r.unresolved().size());
                ps.executeUpdate();
                id = key(ps);
            }
            try (PreparedStatement pp = c.prepareStatement("INSERT INTO analyze_program(run_id, class_name, method, file, line, verb, url,"
                    + " params, kind, descr) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                    PreparedStatement pv = c.prepareStatement("INSERT INTO analyze_view(program_id, kind, name) VALUES (?, ?, ?)");
                    PreparedStatement pst = c.prepareStatement("INSERT INTO analyze_stmt(program_id, ns_id, resolution) VALUES (?, ?, ?)");
                    PreparedStatement pc = c.prepareStatement("INSERT INTO analyze_crud(program_id, table_name, crud) VALUES (?, ?, ?)")) {
                for (AnalyzeRunner.Row row : r.rows()) {
                    JavaGraph.Program p = row.program();
                    pp.setLong(1, id);
                    pp.setString(2, cut(p.className(), 300));
                    pp.setString(3, cut(p.method(), 200));
                    pp.setString(4, cut(p.file(), 1000));
                    pp.setInt(5, p.line());
                    pp.setString(6, cut(p.verb(), 10));
                    pp.setString(7, cut(p.url(), 500));
                    pp.setString(8, cut(p.params(), 200));
                    pp.setString(9, cut(p.kind(), 10));
                    pp.setString(10, cut(p.description(), 100));
                    pp.executeUpdate();
                    long pid = key(pp);
                    for (JavaGraph.View v : p.views()) {
                        pv.setLong(1, pid);
                        pv.setString(2, cut(v.kind(), 10));
                        pv.setString(3, cut(v.name(), 500));
                        pv.addBatch();
                    }
                    for (JavaGraph.Stmt s : p.statements()) {
                        pst.setLong(1, pid);
                        pst.setString(2, cut(s.id(), 300));
                        pst.setString(3, cut(s.resolution(), 10));
                        pst.addBatch();
                    }
                    for (Map.Entry<String, String> e : row.crud().entrySet()) {
                        pc.setLong(1, pid);
                        pc.setString(2, cut(e.getKey(), 200));
                        pc.setString(3, cut(e.getValue(), 4));
                        pc.addBatch();
                    }
                }
                pv.executeBatch();
                pst.executeBatch();
                pc.executeBatch();
            }
            try (PreparedStatement pu = c.prepareStatement(
                    "INSERT INTO analyze_unresolved(run_id, program_id, kind, file, line, detail) VALUES (?, ?, ?, ?, ?, ?)")) {
                for (Unresolved u : r.unresolved()) {
                    pu.setLong(1, id);
                    pu.setNull(2, Types.BIGINT);
                    pu.setString(3, cut(u.kind(), 20));
                    pu.setString(4, cut(u.file(), 1000));
                    pu.setInt(5, u.line());
                    pu.setString(6, cut(u.detail(), 300));
                    pu.addBatch();
                }
                pu.executeBatch();
            }
            c.commit();
            return id;
        }
    }

    private static long key(PreparedStatement ps) throws SQLException {
        try (ResultSet k = ps.getGeneratedKeys()) {
            k.next();
            return k.getLong(1);
        }
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** 최근 것부터 */
    public List<RunInfo> runs() throws SQLException {
        List<RunInfo> out = new ArrayList<>();
        try (Connection c = db.connect();
                PreparedStatement ps = c.prepareStatement("SELECT id, profile, started_at, path, programs, statements, tables, unresolved"
                        + " FROM analyze_run ORDER BY id DESC");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(info(rs));
            }
        }
        return out;
    }

    public Optional<RunInfo> run(long id) throws SQLException {
        try (Connection c = db.connect();
                PreparedStatement ps = c.prepareStatement("SELECT id, profile, started_at, path, programs, statements, tables, unresolved"
                        + " FROM analyze_run WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(info(rs)) : Optional.empty();
            }
        }
    }

    private static RunInfo info(ResultSet rs) throws SQLException {
        return new RunInfo(rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime(), rs.getString(4), rs.getInt(5),
                rs.getInt(6), rs.getInt(7), rs.getInt(8));
    }

    /** 프로그램 — 파일·줄 순. 뷰·문장·CRUD 를 붙인다 */
    public List<ProgramRow> programs(long runId) throws SQLException {
        Map<Long, String[]> head = new LinkedHashMap<>();
        Map<Long, List<JavaGraph.View>> views = new TreeMap<>();
        Map<Long, List<JavaGraph.Stmt>> stmts = new TreeMap<>();
        Map<Long, Map<String, String>> crud = new TreeMap<>();
        Map<Long, Integer> lines = new TreeMap<>();
        try (Connection c = db.connect()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id, class_name, method, file, line, verb, url, params, kind, descr"
                    + " FROM analyze_program WHERE run_id = ? ORDER BY file, line, url, verb")) {
                ps.setLong(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long pid = rs.getLong(1);
                        head.put(pid, new String[] {rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(6), rs.getString(7),
                            rs.getString(8), rs.getString(9), rs.getString(10)});
                        lines.put(pid, rs.getInt(5));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT v.program_id, v.kind, v.name FROM analyze_view v JOIN analyze_program p"
                    + " ON p.id = v.program_id WHERE p.run_id = ?")) {
                ps.setLong(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        views.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new JavaGraph.View(rs.getString(2), rs.getString(3)));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT s.program_id, s.ns_id, s.resolution FROM analyze_stmt s JOIN"
                    + " analyze_program p ON p.id = s.program_id WHERE p.run_id = ? ORDER BY s.ns_id")) {
                ps.setLong(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        stmts.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new JavaGraph.Stmt(rs.getString(2), rs.getString(3)));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT x.program_id, x.table_name, x.crud FROM analyze_crud x JOIN"
                    + " analyze_program p ON p.id = x.program_id WHERE p.run_id = ?")) {
                ps.setLong(1, runId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        crud.computeIfAbsent(rs.getLong(1), k -> new TreeMap<>()).put(rs.getString(2), rs.getString(3));
                    }
                }
            }
        }
        List<ProgramRow> out = new ArrayList<>();
        head.forEach((pid, h) -> out.add(new ProgramRow(pid, h[0], h[1], h[2], lines.get(pid), h[3], h[4], h[5], h[6], h[7],
                views.getOrDefault(pid, List.of()), stmts.getOrDefault(pid, List.of()), crud.getOrDefault(pid, Map.of()))));
        return out;
    }

    /** 열 = 이 실행에서 쓰인 표 전부(정렬) */
    public Matrix crud(long runId) throws SQLException {
        List<ProgramRow> rows = programs(runId);
        TreeSet<String> tables = new TreeSet<>();
        rows.forEach(r -> tables.addAll(r.crud().keySet()));
        return new Matrix(new ArrayList<>(tables), rows);
    }

    /** 종류·파일·줄 순 */
    public List<Unresolved> unresolved(long runId) throws SQLException {
        List<Unresolved> out = new ArrayList<>();
        try (Connection c = db.connect();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT kind, file, line, detail FROM analyze_unresolved WHERE run_id = ? ORDER BY kind, file, line, detail")) {
            ps.setLong(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Unresolved(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4)));
                }
            }
        }
        return out;
    }
}
