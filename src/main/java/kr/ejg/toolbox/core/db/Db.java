package kr.ejg.toolbox.core.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 내장 H2 — {@code <dataDir>/toolbox.mv.db}. 사전·스냅샷·실행 이력만 둔다(5-1). 코드 본문·비밀번호는 넣지 않는다.
 * 열려 있는 동안 연결 하나를 쥐고 있어 파일 락을 유지한다 — 두 번째 프로세스는 {@link LockedException}.
 */
public final class Db implements AutoCloseable {

    /** H2 「Database may be already in use」 */
    private static final int H2_DATABASE_ALREADY_OPEN = 90020;

    private final String url;
    private final Path file;
    private final Connection keeper;

    private Db(String url, Path file, Connection keeper) {
        this.url = url;
        this.file = file;
        this.keeper = keeper;
    }

    /** 열고 마이그레이션까지. 다른 프로세스가 쥐고 있으면 {@link LockedException} */
    public static Db open(Path dataDir) {
        Path dir = dataDir.toAbsolutePath().normalize();
        Path file = dir.resolve("toolbox.mv.db");
        // retry: — file: 은 쓰는 스레드가 인터럽트(작업 취소)되면 채널이 닫혀 DB 전체가 닫힌다(90098, DbInterruptTest)
        String url = "jdbc:h2:retry:" + dir.resolve("toolbox").toString().replace('\\', '/');
        Connection keeper;
        try {
            Files.createDirectories(dir);
            keeper = DriverManager.getConnection(url, "sa", "");
        } catch (SQLException e) {
            if (isLocked(e)) {
                throw new LockedException(file, e);
            }
            throw new IllegalStateException("H2 를 못 열었다: " + file, e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("data 폴더를 못 만들었다: " + dir, e);
        }
        Db db = new Db(url, file, keeper);
        try {
            Migrator.migrate(keeper);
        } catch (SQLException e) {
            db.close();
            throw new IllegalStateException("스키마 마이그레이션 실패: " + e.getMessage(), e);
        }
        return db;
    }

    private static boolean isLocked(SQLException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException s && s.getErrorCode() == H2_DATABASE_ALREADY_OPEN) {
                return true;
            }
        }
        return false;
    }

    /** 새 연결. 쓰고 닫는다 */
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    public Path file() {
        return file;
    }

    public int schemaVersion() throws SQLException {
        return Migrator.currentVersion(keeper);
    }

    @Override
    public void close() {
        try {
            keeper.close();
        } catch (SQLException ignored) {
            // 닫는 중 오류는 무시
        }
    }

    /** 다른 프로세스가 같은 data 폴더의 H2 를 쥐고 있다 */
    public static final class LockedException extends RuntimeException {
        private final Path file;

        LockedException(Path file, Throwable cause) {
            super("다른 프로세스가 H2 파일을 쓰고 있다: " + file, cause);
            this.file = file;
        }

        public Path file() {
            return file;
        }
    }
}
