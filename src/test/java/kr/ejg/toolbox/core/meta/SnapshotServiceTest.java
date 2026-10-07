package kr.ejg.toolbox.core.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.ejg.toolbox.core.conn.ConnectionRegistry;
import kr.ejg.toolbox.core.db.Db;
import kr.ejg.toolbox.core.job.Job;
import kr.ejg.toolbox.core.job.JobManager;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 1-11 — 스냅샷 수집의 표 단위 진행률·취소, 결과의 걸린 시간·저장 위치 */
class SnapshotServiceTest {

    /** 표 다섯. {@code blockAt} 표의 컬럼을 읽을 때 멈춰 서서 release 를 기다린다 — 인터럽트를 삼켜 표 사이 확인만 남긴다 */
    static class FakeSource implements MetaSource {
        final List<String> loaded = new CopyOnWriteArrayList<>();
        final String blockAt;
        final CountDownLatch reached = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        FakeSource(String blockAt) {
            this.blockAt = blockAt;
        }

        @Override
        public List<String> listSchemas() {
            return List.of("S");
        }

        @Override
        public List<Table> listTables(String schema) {
            List<Table> out = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                out.add(new Table(schema, "T" + i, "TABLE", null, null, null, null, null, null, null, null, null));
            }
            return out;
        }

        @Override
        public Table loadColumns(Table t) {
            loaded.add(t.name());
            if (t.name().equals(blockAt)) {
                reached.countDown();
                boolean interrupted = false;
                while (true) {
                    try {
                        release.await();
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return t;
        }

        @Override
        public Table loadConstraints(Table t) {
            return t;
        }

        @Override
        public Table loadIndexes(Table t) {
            return t;
        }

        @Override
        public String dbVersion() {
            return "fake 1";
        }
    }

    @TempDir
    Path tmp;

    Db db;
    SnapshotStore store;
    JobManager jobs;
    ConnectionRegistry conns;
    Optional<Profile> active;

    @BeforeEach
    void up() {
        db = Db.open(tmp);
        store = new SnapshotStore(db);
        jobs = new JobManager(1);
        Profile p = new Profile("t", null,
                List.of(new Profile.Connection("h2", "h2", "jdbc:h2:mem:snapsvc" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa")),
                null, null, null, null, null, null, null, null, null);
        conns = new ConnectionRegistry(() -> Optional.of(p));
        active = Optional.of(p);
    }

    @AfterEach
    void down() {
        jobs.shutdown();
        db.close();
    }

    private SnapshotService service(MetaSource src) {
        return new SnapshotService(conns, (dialect, c) -> src, store, () -> active);
    }

    private static Job await(Job job) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!job.finished() && System.nanoTime() < end) {
            Thread.sleep(20);
        }
        assertTrue(job.finished(), "작업이 안 끝났다: " + job.summary());
        return job;
    }

    private static List<String> progressMessages(Job job) {
        List<String> out = new ArrayList<>();
        for (Job.Event e : job.events()) {
            if (e.name().equals(Job.EventName.PROGRESS.wire())) {
                out.add(String.valueOf(((Map<?, ?>) e.data()).get("message")));
            }
        }
        return out;
    }

    /**
     * 1-47 — 통계가 없는 표(rowCount null)만 센다. T1 은 H2 에 3행, T2 는 통계 7(안 센다 — DB 에 없어도 7), V1 은 뷰(안 센다),
     * T3 은 DB 에 없다 → 못 세어 null + 경고 rowCount
     */
    @Test
    void countsOnlyTablesWithoutStats() throws Exception {
        String url = active.orElseThrow().connections().get(0).url();
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(url, "sa", ""); java.sql.Statement st = c.createStatement()) {
            st.execute("CREATE SCHEMA S");
            st.execute("CREATE TABLE S.T1(ID INT)");
            st.execute("INSERT INTO S.T1 VALUES (1), (2), (3)");
        }
        FakeSource src = new FakeSource(null) {
            @Override
            public List<Table> listTables(String schema) {
                return List.of(new Table(schema, "T1", "TABLE", null, null, null, null, null, null, null, null, null),
                        new Table(schema, "T2", "TABLE", null, null, null, null, null, null, 7L, null, null),
                        new Table(schema, "T3", "TABLE", null, null, null, null, null, null, null, null, null),
                        new Table(schema, "V1", "VIEW", null, null, null, null, null, null, null, null, null));
            }
        };
        Job job = await(jobs.submit("snapshot", service(src).take("h2", "n")));
        assertEquals(Job.Status.DONE, job.status(), job.summary().toString());
        Map<?, ?> r = (Map<?, ?>) job.result();
        Map<String, Long> rows = new java.util.HashMap<>();
        for (Table t : store.get(((Number) r.get("snapshotId")).longValue()).orElseThrow().get(0).tables()) {
            rows.put(t.name(), t.rowCount());
        }
        assertEquals(3L, rows.get("T1"));
        assertEquals(7L, rows.get("T2"));
        assertEquals(null, rows.get("T3"));
        assertEquals(null, rows.get("V1"));
        assertTrue(rows.containsKey("T3") && rows.containsKey("V1"), rows.toString());
        List<?> w = (List<?>) r.get("warnings");
        assertTrue(w.stream().anyMatch(x -> ((MetaSource.Warning) x).kind().equals("rowCount") && ((MetaSource.Warning) x).count() == 1),
                w.toString());
        assertTrue(progressMessages(job).contains("행 수 2/2 — T3"), progressMessages(job).toString());
    }

    @Test
    void progressPerTableAndResultHasElapsedAndStore() throws Exception {
        Job job = await(jobs.submit("snapshot", service(new FakeSource(null)).take("h2", "n")));

        assertEquals(Job.Status.DONE, job.status(), job.summary().toString());
        List<String> msgs = progressMessages(job);
        List<String> tables = msgs.stream().filter(m -> m.startsWith("테이블 ")).toList();
        assertEquals(List.of("테이블 1/5 — T1", "테이블 2/5 — T2", "테이블 3/5 — T3", "테이블 4/5 — T4", "테이블 5/5 — T5"), tables);
        Map<?, ?> r = (Map<?, ?>) job.result();
        assertEquals(5, r.get("tables"));
        assertTrue(((Number) r.get("elapsedMs")).longValue() >= 0, r.toString());
        assertTrue(String.valueOf(r.get("store")).endsWith("toolbox.mv.db"), r.toString());
        assertEquals(1, store.list().size());
    }

    @Test
    void cancelAtTableTwoStopsAndSavesNothingThenRetakeWorks() throws Exception {
        FakeSource src = new FakeSource("T2");
        Job job = jobs.submit("snapshot", service(src).take("h2", "n"));
        assertTrue(src.reached.await(30, TimeUnit.SECONDS), "표 2 에 닿는다");
        assertTrue(jobs.cancel(job.id()));
        src.release.countDown();
        await(job);

        assertEquals(Job.Status.CANCELLED, job.status(), job.summary().toString());
        assertEquals(List.of("T1", "T2"), src.loaded, "표 2 뒤로는 안 읽는다");
        assertEquals(0, store.list().size(), "취소면 저장 안 한다");
        assertFalse(progressMessages(job).contains("테이블 3/5 — T3"));

        Job again = await(jobs.submit("snapshot", service(new FakeSource(null)).take("h2", "다시")));
        assertEquals(Job.Status.DONE, again.status(), again.summary().toString());
        assertEquals(1, store.list().size());
    }

    @Test
    void emptyScopeDoesNotDivideByZero() throws SQLException {
        MetaSource none = new FakeSource(null) {
            @Override
            public List<Table> listTables(String schema) {
                return List.of();
            }
        };
        List<String> seen = new ArrayList<>();
        assertEquals(1, none.collect(Scope.all(), (d, t, s, n) -> seen.add(n)).size());
        assertEquals(List.of(), seen);
    }
}
