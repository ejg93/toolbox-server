package kr.ejg.toolbox.core.check;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kr.ejg.toolbox.core.db.Db;

/**
 * 검사 이력(H2 {@code check_run}·{@code check_finding}, V001). 저장은 파일·줄·묶음·규칙·등급까지 — 발췌는 버린다(절대 규칙 3).
 */
public final class CheckStore {

    public record RunInfo(long id, String profile, LocalDateTime startedAt, String path, boolean changedOnly, String groups,
            int findings) {
    }

    private final Db db;

    public CheckStore(Db db) {
        this.db = db;
    }

    public long save(String profile, String path, boolean changedOnly, List<String> groups, List<Finding> findings) throws SQLException {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            long id;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO check_run(profile, path, changed_only, rule_groups) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, profile);
                ps.setString(2, cut(path, 1000));
                ps.setBoolean(3, changedOnly);
                ps.setString(4, cut(String.join(",", groups), 200));
                ps.executeUpdate();
                try (ResultSet k = ps.getGeneratedKeys()) {
                    k.next();
                    id = k.getLong(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO check_finding(run_id, file, line, grp, rule, severity) VALUES (?, ?, ?, ?, ?, ?)")) {
                int n = 0;
                for (Finding f : findings) {
                    ps.setLong(1, id);
                    ps.setString(2, cut(f.file(), 1000));
                    ps.setInt(3, f.line());
                    ps.setString(4, cut(f.group(), 20));
                    ps.setString(5, cut(f.rule(), 100));
                    ps.setString(6, cut(f.severity(), 10));
                    ps.addBatch();
                    if (++n % 1000 == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
            }
            c.commit();
            return id;
        }
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** 최근 것부터 */
    public List<RunInfo> runs() throws SQLException {
        List<RunInfo> out = new ArrayList<>();
        try (Connection c = db.connect();
                PreparedStatement ps = c.prepareStatement("SELECT r.id, r.profile, r.started_at, r.path, r.changed_only, r.rule_groups,"
                        + " (SELECT COUNT(*) FROM check_finding f WHERE f.run_id = r.id) FROM check_run r ORDER BY r.id DESC");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(info(rs));
            }
        }
        return out;
    }

    public Optional<RunInfo> run(long id) throws SQLException {
        try (Connection c = db.connect();
                PreparedStatement ps = c.prepareStatement("SELECT r.id, r.profile, r.started_at, r.path, r.changed_only, r.rule_groups,"
                        + " (SELECT COUNT(*) FROM check_finding f WHERE f.run_id = r.id) FROM check_run r WHERE r.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(info(rs)) : Optional.empty();
            }
        }
    }

    private static RunInfo info(ResultSet rs) throws SQLException {
        return new RunInfo(rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toLocalDateTime(), rs.getString(4), rs.getBoolean(5),
                rs.getString(6), rs.getInt(7));
    }

    /** 발췌 없이 — 파일·줄·규칙 순 */
    public List<Finding> findings(long runId) throws SQLException {
        List<Finding> out = new ArrayList<>();
        try (Connection c = db.connect();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT file, line, grp, rule, severity FROM check_finding WHERE run_id = ? ORDER BY file, line, rule")) {
            ps.setLong(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Finding(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5), null));
                }
            }
        }
        return out;
    }
}
