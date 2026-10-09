package kr.ejg.toolbox.core.analyze;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    /** 영향도 한 줄 — 그 표를 쓰는 프로그램과 그 URL 을 부르는 JSP(정렬) */
    public record ImpactRow(ProgramRow program, List<String> jsps) {

        public ImpactRow {
            jsps = List.copyOf(jsps);
        }
    }

    /** 영향도(6-6) — 표(대문자) → 프로그램(파일·줄 순) → JSP. jsps 는 구분·정렬 */
    public record Impact(String table, List<ImpactRow> rows, List<String> jsps) {

        public Impact {
            rows = List.copyOf(rows);
            jsps = List.copyOf(jsps);
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
                    pp.setString(6, cut(p.verb(), 60));
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
            // 6-27 — 꼴(kind)이 있으면 jsp·url·꼴마다 한 행, 없으면(옛 꼴) kind null 한 행
            try (PreparedStatement pj = c.prepareStatement("INSERT INTO analyze_jsp_link(run_id, jsp, url, kind) VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<String, List<String>> e : r.jspLinks().entrySet()) {
                    List<JspLinks.Link> links = r.jspLinkKinds().getOrDefault(e.getKey(), List.of());
                    for (String url : e.getValue()) {
                        List<String> kinds = links.stream().filter(l -> l.url().equals(url)).map(JspLinks.Link::kind).distinct().toList();
                        for (String kind : kinds.isEmpty() ? java.util.Collections.<String>singletonList(null) : kinds) {
                            pj.setLong(1, id);
                            pj.setString(2, cut(e.getKey(), 1000));
                            pj.setString(3, cut(url, 500));
                            pj.setString(4, kind);
                            pj.addBatch();
                        }
                    }
                }
                pj.executeBatch();
            }
            // 6-26 — 뷰 이름마다 맞는 JSP 파일 수
            try (PreparedStatement pf = c.prepareStatement("INSERT INTO analyze_view_file(run_id, name, files) VALUES (?, ?, ?)")) {
                Set<String> seen = new HashSet<>();
                for (Map.Entry<String, Integer> e : r.viewFiles().entrySet()) {
                    String name = cut(e.getKey(), 500);
                    if (!seen.add(name)) {
                        continue; // 500 자에서 잘려 같은 이름이 되면 첫 것만(PK)
                    }
                    pf.setLong(1, id);
                    pf.setString(2, name);
                    pf.setInt(3, e.getValue());
                    pf.addBatch();
                }
                pf.executeBatch();
            }
            try (PreparedStatement pj = c.prepareStatement(
                    "INSERT INTO analyze_join(run_id, ns_id, table_a, col_a, table_b, col_b) VALUES (?, ?, ?, ?, ?, ?)")) {
                for (Map.Entry<String, List<SqlJoins.Join>> e : r.joins().entrySet()) {
                    for (SqlJoins.Join j : e.getValue()) {
                        pj.setLong(1, id);
                        pj.setString(2, cut(e.getKey(), 300));
                        pj.setString(3, cut(j.tableA(), 200));
                        pj.setString(4, cut(j.colA(), 200));
                        pj.setString(5, cut(j.tableB(), 200));
                        pj.setString(6, cut(j.colB(), 200));
                        pj.addBatch();
                    }
                }
                pj.executeBatch();
            }
            try (PreparedStatement po = c.prepareStatement("INSERT INTO analyze_orphan(run_id, kind, name) VALUES (?, ?, ?)")) {
                for (AnalyzeRunner.Orphan o : r.orphans()) {
                    po.setLong(1, id);
                    po.setString(2, cut(o.kind(), 20));
                    po.setString(3, cut(o.name(), 1000));
                    po.addBatch();
                }
                po.executeBatch();
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

    /** 조인 등식 한 줄(6-13) — 문장(ns.id) 단위 */
    public record JoinRow(String nsId, String tableA, String colA, String tableB, String colB) {
    }

    /** 이 실행의 조인 등식 전부 — 문장·표 이름순 */
    public List<JoinRow> joins(long runId) throws SQLException {
        List<JoinRow> out = new ArrayList<>();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT ns_id, table_a, col_a, table_b, col_b FROM analyze_join WHERE run_id = ? ORDER BY ns_id, table_a, col_a, table_b, col_b")) {
            ps.setLong(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new JoinRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)));
                }
            }
        }
        return out;
    }

    /** 열 = 이 실행에서 쓰인 표 전부(정렬) */
    public Matrix crud(long runId) throws SQLException {
        List<ProgramRow> rows = programs(runId);
        TreeSet<String> tables = new TreeSet<>();
        rows.forEach(r -> tables.addAll(r.crud().keySet()));
        return new Matrix(new ArrayList<>(tables), rows);
    }

    /** 표를 쓰는 프로그램과 그 URL 을 부르는 JSP. 표 이름은 대문자로 맞춘다. 없는 표면 빈 목록 */
    public Impact impact(long runId, String table) throws SQLException {
        String t = table.trim().toUpperCase(java.util.Locale.ROOT);
        Set<Long> ids = new HashSet<>();
        Map<String, List<String>> byUrl = new HashMap<>();
        try (Connection c = db.connect()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT x.program_id FROM analyze_crud x JOIN analyze_program p ON p.id = x.program_id"
                    + " WHERE p.run_id = ? AND x.table_name = ?")) {
                ps.setLong(1, runId);
                ps.setString(2, t);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getLong(1));
                    }
                }
            }
            if (ids.isEmpty()) {
                return new Impact(t, List.of(), List.of());
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT DISTINCT p.url, j.jsp FROM analyze_crud x JOIN analyze_program p"
                    + " ON p.id = x.program_id JOIN analyze_jsp_link j ON j.run_id = p.run_id AND j.url = p.url"
                    + " WHERE p.run_id = ? AND x.table_name = ? ORDER BY j.jsp")) {
                ps.setLong(1, runId);
                ps.setString(2, t);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        byUrl.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
                    }
                }
            }
        }
        List<ImpactRow> rows = new ArrayList<>();
        TreeSet<String> jsps = new TreeSet<>();
        for (ProgramRow p : programs(runId)) {
            if (ids.contains(p.id())) {
                List<String> js = byUrl.getOrDefault(p.url(), List.of());
                rows.add(new ImpactRow(p, js));
                jsps.addAll(js);
            }
        }
        return new Impact(t, rows, new ArrayList<>(jsps));
    }

    /** 뷰 이름 → 맞는 JSP 파일 수(6-26). 옛 실행(행 없음)은 빈 맵 — 「모름」 */
    public Map<String, Integer> viewFiles(long runId) throws SQLException {
        Map<String, Integer> out = new TreeMap<>();
        try (Connection c = db.connect();
                PreparedStatement ps = c.prepareStatement("SELECT name, files FROM analyze_view_file WHERE run_id = ?")) {
            ps.setLong(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        return out;
    }

    /** JSP 가 부르는 URL 한 행(6-27) — kind 는 link·form·popup·ajax·script·other, 옛 행은 null */
    public record JspLink(String jsp, String url, String kind) {
    }

    /** 실행의 JSP 링크 전부 — jsp·url·꼴 순 */
    public List<JspLink> jspLinks(long runId) throws SQLException {
        List<JspLink> out = new ArrayList<>();
        try (Connection c = db.connect();
                PreparedStatement ps = c.prepareStatement("SELECT jsp, url, kind FROM analyze_jsp_link WHERE run_id = ? ORDER BY jsp, url, kind")) {
            ps.setLong(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new JspLink(rs.getString(1), rs.getString(2), rs.getString(3)));
                }
            }
        }
        return out;
    }

    /** 메뉴 CSV 요약(6-29) — 행 수 · URL 있는 행 수 · 올린 시각 */
    public record MenuInfo(int rows, int withUrl, LocalDateTime uploadedAt) {
    }

    /** 그 프로필의 메뉴를 통째로 바꾼다(6-29) — 한 트랜잭션 */
    public void saveMenu(String profile, List<Menus.Row> rows) throws SQLException {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement del = c.prepareStatement("DELETE FROM analyze_menu WHERE profile = ?");
                    PreparedStatement ins = c.prepareStatement(
                            "INSERT INTO analyze_menu(profile, seq, path, name, url, screen_id, use_yn, auth) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                del.setString(1, profile);
                del.executeUpdate();
                for (Menus.Row r : rows) {
                    ins.setString(1, profile);
                    ins.setInt(2, r.seq());
                    ins.setString(3, cut(r.path(), 1000));
                    ins.setString(4, cut(r.name(), 300));
                    ins.setString(5, cut(r.url(), 500));
                    ins.setString(6, cut(r.screenId(), 100));
                    ins.setString(7, cut(r.useYn(), 10));
                    ins.setString(8, cut(r.auth(), 300));
                    ins.addBatch();
                }
                ins.executeBatch();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** 그 프로필의 메뉴 — seq(트리) 순. 없으면 빈 목록 */
    public List<Menus.Row> menu(String profile) throws SQLException {
        List<Menus.Row> out = new ArrayList<>();
        if (profile == null) {
            return out;
        }
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT seq, path, name, url, screen_id, use_yn, auth FROM analyze_menu WHERE profile = ? ORDER BY seq")) {
            ps.setString(1, profile);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Menus.Row(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                            rs.getString(7)));
                }
            }
        }
        return out;
    }

    public Optional<MenuInfo> menuInfo(String profile) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*), COUNT(url), MAX(uploaded_at) FROM analyze_menu WHERE profile = ?")) {
            ps.setString(1, profile);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 0 ? Optional.empty()
                        : Optional.of(new MenuInfo(rs.getInt(1), rs.getInt(2), rs.getTimestamp(3).toLocalDateTime()));
            }
        }
    }

    public int deleteMenu(String profile) throws SQLException {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("DELETE FROM analyze_menu WHERE profile = ?")) {
            ps.setString(1, profile);
            return ps.executeUpdate();
        }
    }

    /** 고아(6-11) — 종류·이름 순 */
    public List<AnalyzeRunner.Orphan> orphans(long runId) throws SQLException {
        List<AnalyzeRunner.Orphan> out = new ArrayList<>();
        try (Connection c = db.connect();
                PreparedStatement ps = c.prepareStatement("SELECT kind, name FROM analyze_orphan WHERE run_id = ? ORDER BY kind, name")) {
            ps.setLong(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new AnalyzeRunner.Orphan(rs.getString(1), rs.getString(2)));
                }
            }
        }
        return out;
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
