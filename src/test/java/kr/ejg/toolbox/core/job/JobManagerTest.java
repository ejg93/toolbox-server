package kr.ejg.toolbox.core.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JobManagerTest {

    private final JobManager jobs = new JobManager();

    @AfterEach
    void down() {
        jobs.shutdown();
    }

    private static JobManager.Body steps(int n, long delayMs) {
        return ctx -> {
            for (int i = 1; i <= n; i++) {
                Thread.sleep(delayMs);
                ctx.checkCancelled();
                ctx.progress(i * 100 / n, i + "/" + n);
            }
            return Map.of("steps", n);
        };
    }

    private static void awaitFinished(Job job) throws InterruptedException {
        long end = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!job.finished()) {
            assertTrue(System.nanoTime() < end, "10초 안에 끝나야 한다: " + job.status());
            Thread.sleep(10);
        }
    }

    @Test
    void tenStepsGiveTenProgressEventsThenDone() throws Exception {
        Job job = jobs.submit("dummy", steps(10, 50));
        awaitFinished(job);

        assertEquals(Job.Status.DONE, job.status());
        assertEquals(100, job.progress());
        List<Job.Event> events = job.events();
        assertEquals(10, events.stream().filter(e -> e.name().equals("progress")).count());
        assertEquals("done", events.get(events.size() - 1).name());
        assertEquals(Map.of("steps", 10), job.result());
        assertEquals(8, job.id().length());
    }

    @Test
    void cancelMidwayGivesCancelled() throws Exception {
        CountDownLatch third = new CountDownLatch(3);
        Job job = jobs.submit("dummy", steps(10, 50));
        Runnable off = job.subscribe(e -> {
            if (e.name().equals("progress")) {
                third.countDown();
            }
        });
        assertTrue(third.await(5, TimeUnit.SECONDS));
        assertTrue(jobs.cancel(job.id()));
        awaitFinished(job);
        off.run();

        assertEquals(Job.Status.CANCELLED, job.status());
        List<Job.Event> events = job.events();
        assertEquals("cancelled", events.get(events.size() - 1).name());
        assertTrue(job.progress() < 100);
        assertFalse(jobs.cancel(job.id()), "끝난 작업은 다시 취소 안 된다");
    }

    @Test
    void failureGivesFailedWithMessage() throws Exception {
        Job job = jobs.submit("boom", ctx -> {
            throw new IllegalStateException("터짐");
        });
        awaitFinished(job);
        assertEquals(Job.Status.FAILED, job.status());
        Job.Event last = job.events().get(job.events().size() - 1);
        assertEquals("failed", last.name());
        assertEquals(Map.of("message", "터짐"), last.data());
    }

    @Test
    void lateSubscriberGetsReplayWithoutDuplicates() throws Exception {
        Job job = jobs.submit("dummy", steps(5, 30));
        Thread.sleep(80);
        List<String> seen = new CopyOnWriteArrayList<>();
        job.subscribe(e -> seen.add(e.name() + (e.data() instanceof Map<?, ?> m && m.get("message") != null ? m.get("message") : "")));
        awaitFinished(job);
        Thread.sleep(50);
        assertEquals(List.of("progress1/5", "progress2/5", "progress3/5", "progress4/5", "progress5/5", "done"), seen);
    }

    @Test
    void cancelQueuedJobBeforeItRuns() throws Exception {
        JobManager one = new JobManager(1);
        try {
            Job blocker = one.submit("blocker", steps(5, 50));
            Job queued = one.submit("queued", steps(1, 1));
            assertTrue(one.cancel(queued.id()));
            assertEquals(Job.Status.CANCELLED, queued.status());
            awaitFinished(blocker);
            assertEquals(Job.Status.DONE, blocker.status());
        } finally {
            one.shutdown();
        }
    }
}
