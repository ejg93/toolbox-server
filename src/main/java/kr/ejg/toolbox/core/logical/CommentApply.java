package kr.ejg.toolbox.core.logical;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import kr.ejg.toolbox.core.job.JobContext;
import kr.ejg.toolbox.core.sqlrun.SqlRunner;

/**
 * COMMENT 직접 실행(3-9, 2.2 C 「내려주기 또는 직접 실행」). 3-5 가 만든 실행 줄을 한 줄씩 던진다.
 * 코멘트는 서로 독립이라 실패해도 계속한다. 로거가 없다 — 문장·오류 원문은 작업 결과로만 화면에 간다(규칙 3).
 */
public final class CommentApply {

    /** MariaDB 컬럼 줄 — 타입을 채워야 도는 틀이라 실행하지 않는다 */
    static final String NEEDS_TYPE = "/* 컬럼타입 명시 필요 */";

    public record Failure(int line, String message) {
    }

    /** skipped = 전체 줄 − 실행한 줄([검토]·타입 없는 MariaDB 컬럼 줄) */
    public record Outcome(int applied, int skipped, List<Failure> failed) {
        public Outcome {
            failed = List.copyOf(failed);
        }
    }

    private CommentApply() {
    }

    /**
     * @param lines       {@link CommentDdl#executableLines} 결과
     * @param reviewLines 실행 대상에서 이미 빠진 줄 수([검토]) — skipped 에 더한다
     */
    public static Outcome apply(Connection conn, List<String> lines, int reviewLines, JobContext ctx) {
        int applied = 0;
        int skipped = reviewLines;
        List<Failure> failed = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (ctx != null) {
                ctx.checkCancelled();
                ctx.progress(i * 100 / Math.max(1, lines.size()), (i + 1) + " / " + lines.size());
            }
            String sql = lines.get(i).strip();
            if (sql.contains(NEEDS_TYPE)) {
                skipped++;
                continue;
            }
            if (sql.endsWith(";")) {
                sql = sql.substring(0, sql.length() - 1); // Oracle·Tibero 드라이버는 끝 ; 를 못 받는다
            }
            try {
                SqlRunner.run(conn, sql, List.of(), 0, SqlRunner.DEFAULT_TIMEOUT_SEC);
                applied++;
            } catch (SQLException e) {
                failed.add(new Failure(i + 1, e.getMessage()));
            }
        }
        if (ctx != null) {
            ctx.progress(100, "완료");
        }
        return new Outcome(applied, skipped, failed);
    }
}
