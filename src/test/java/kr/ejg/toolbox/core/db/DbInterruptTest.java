package kr.ejg.toolbox.core.db;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 작업 취소({@code Future.cancel(true)})가 쓰는 도중의 스레드를 인터럽트해도 H2 가 닫히지 않는다(0-43, 설계 14 U-11) */
class DbInterruptTest {

    @TempDir
    Path tmp;

    @Test
    void interruptDuringWriteKeepsDatabaseOpen() throws Exception {
        try (Db db = Db.open(tmp)) {
            try (Connection c = db.connect(); Statement s = c.createStatement()) {
                s.execute("CREATE TABLE interrupt_t(id INT, v VARCHAR(4000))");
            }
            ExecutorService ex = Executors.newFixedThreadPool(1);
            try {
                for (int round = 1; round <= 3; round++) {
                    CountDownLatch started = new CountDownLatch(1);
                    Future<?> f = ex.submit(() -> {
                        try (Connection c = db.connect()) {
                            c.setAutoCommit(false);
                            try (PreparedStatement ps = c.prepareStatement("INSERT INTO interrupt_t VALUES(?,?)")) {
                                for (int i = 0; i < 100_000; i++) {
                                    ps.setInt(1, i);
                                    ps.setString(2, "x".repeat(500));
                                    ps.executeUpdate();
                                    if (i == 2000) {
                                        started.countDown();
                                    }
                                    if (i % 5000 == 0) {
                                        c.commit();
                                    }
                                }
                            }
                            c.commit();
                        } catch (Throwable e) {
                            // 인터럽트로 깨지는 것은 기대한 일 — 살아남는지는 아래에서 잰다
                        }
                        started.countDown();
                        return null;
                    });
                    assertTrue(started.await(60, TimeUnit.SECONDS), "round " + round + " 쓰기 시작");
                    Thread.sleep(50);
                    f.cancel(true);
                    Thread.sleep(500);

                    try (Connection c = db.connect(); Statement s = c.createStatement();
                            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM interrupt_t")) {
                        assertTrue(rs.next(), "round " + round + " 새 접속");
                    }
                    assertTrue(db.schemaVersion() > 0, "round " + round + " keeper 접속");
                }
            } finally {
                ex.shutdownNow();
                assertTrue(ex.awaitTermination(30, TimeUnit.SECONDS));
            }
        }
    }
}
