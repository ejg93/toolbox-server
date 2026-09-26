package kr.ejg.toolbox.core.job;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 작업 큐. 스레드 넷. 작업 id 는 8자.
 * 본문이 값을 돌려주면 {@code done}(data = 값), 예외면 {@code failed}(message), 취소면 {@code cancelled}.
 * 로그에는 작업 이름·id·상태만 — 본문 결과·예외 메시지는 남기지 않는다(사용자 코드·SQL 이 섞일 수 있다).
 */
public final class JobManager {

    /** 작업 본문 */
    @FunctionalInterface
    public interface Body {
        Object run(JobContext ctx) throws Exception;
    }

    private static final Logger LOG = LoggerFactory.getLogger(JobManager.class);
    private static final String ID_CHARS = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final int KEEP_FINISHED = 200;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService pool;

    public JobManager() {
        this(4);
    }

    public JobManager(int threads) {
        AtomicInteger n = new AtomicInteger();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "job-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.pool = Executors.newFixedThreadPool(threads, tf);
    }

    public Job submit(String name, Body body) {
        prune();
        Job job = new Job(newId(), name);
        jobs.put(job.id(), job);
        JobContext ctx = new JobContext(job);
        job.setFuture(pool.submit(() -> run(job, ctx, body)));
        return job;
    }

    private void run(Job job, JobContext ctx, Body body) {
        if (job.cancelRequested()) {
            job.emit("cancelled", Map.of());
            return;
        }
        job.setRunning();
        LOG.info("작업 시작 {} {}", job.id(), job.name());
        try {
            Object result = body.run(ctx);
            if (ctx.isCancelled()) {
                job.emit("cancelled", Map.of());
            } else {
                job.setResult(result);
                job.emit("done", result == null ? Map.of() : result);
            }
        } catch (InterruptedException | JobContext.JobCancelledException e) {
            job.emit("cancelled", Map.of());
        } catch (Exception e) {
            if (job.cancelRequested()) {
                job.emit("cancelled", Map.of());
            } else {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("message", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                job.emit("failed", data);
            }
        } finally {
            LOG.info("작업 끝 {} {}", job.id(), job.status());
        }
    }

    public Optional<Job> get(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    /** 없거나 이미 끝난 작업이면 false. 큐에서 아직 안 돈 작업은 바로 {@code cancelled} */
    public boolean cancel(String id) {
        Job job = jobs.get(id);
        if (job == null || !job.requestCancel()) {
            return false;
        }
        if (job.status() == Job.Status.QUEUED) {
            job.emit("cancelled", Map.of());
        }
        return true;
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    private void prune() {
        if (jobs.size() < KEEP_FINISHED) {
            return;
        }
        jobs.values().removeIf(Job::finished);
    }

    private String newId() {
        while (true) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(ID_CHARS.charAt(random.nextInt(ID_CHARS.length())));
            }
            String id = sb.toString();
            if (!jobs.containsKey(id)) {
                return id;
            }
        }
    }
}
