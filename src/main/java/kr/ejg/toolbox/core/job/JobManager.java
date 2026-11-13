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
 * 로그에는 작업 이름·id·상태, 실패면 예외 클래스 이름과 우리 코드 자리까지 — 본문 결과·예외 글은 남기지 않는다(사용자 코드·SQL 이 섞일 수 있다).
 */
public final class JobManager {

    /** 작업 본문 */
    @FunctionalInterface
    public interface Body {
        Object run(JobContext ctx) throws Exception;
    }

    private static final Logger LOG = LoggerFactory.getLogger(JobManager.class);
    /** 실패 로그에 싣는 예외 사슬 길이 */
    private static final int CHAIN = 3;
    private static final String OURS = "kr.ejg.toolbox.";
    private static final String MESSAGE = "message";
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
        if (!job.start()) {
            // 이미 끝났으면 emit 이 무시한다
            job.emit(Job.EventName.CANCELLED, Map.of());
            return;
        }
        LOG.info("작업 시작 {} {}", job.id(), job.name());
        try {
            Object result = body.run(ctx);
            if (ctx.isCancelled()) {
                job.emit(Job.EventName.CANCELLED, Map.of());
            } else {
                job.setResult(result);
                job.emit(Job.EventName.DONE, result == null ? Map.of() : result);
            }
        } catch (InterruptedException | JobContext.JobCancelledException e) { // tb-ignore java.interruptedIgnored 취소는 여기서 끝난다 — 작업 스레드가 곧 돌아간다
            job.emit(Job.EventName.CANCELLED, Map.of());
        } catch (Exception e) {
            if (job.cancelRequested()) {
                job.emit(Job.EventName.CANCELLED, Map.of());
            } else {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put(MESSAGE, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                job.emit(Job.EventName.FAILED, data);
                LOG.warn("작업 실패 {} {} — {}", job.id(), job.name(), where(e));
            }
        } catch (Error e) { // tb-ignore java.catchThrowable 0-83a 스택 넘침·메모리 부족을 FutureTask 가 삼켜 작업이 RUNNING 에 멈췄다
            // 이름만 싣는다 — Error 글에 사용자 값이 섞일 수 있다(규칙 3)
            job.emit(Job.EventName.FAILED, Map.of(MESSAGE, e.getClass().getSimpleName()));
            LOG.error("작업 실패 {} {} — {}", job.id(), job.name(), where(e));
        } finally {
            if (!job.finished()) { // 0-85c — catch 몸 안에서 다시 Error 가 나면(메모리 부족) 끝 이벤트가 안 나가 RUNNING 에 멈춘다
                job.emit(Job.EventName.FAILED, Map.of(MESSAGE, "Error"));
            }
            LOG.info("작업 끝 {} {}", job.id(), job.status());
        }
    }

    /** 실패 자리 — 예외 사슬의 클래스 이름(셋까지)과 우리 코드 첫 프레임. 예외 글은 안 싣는다(사용자 코드·SQL·값이 섞인다 — 규칙 3) */
    public static String where(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        for (int i = 0; t != null && i < CHAIN; i++, t = t.getCause()) {
            sb.append(i == 0 ? "" : " ← ").append(t.getClass().getSimpleName());
        }
        for (StackTraceElement f : e.getStackTrace()) {
            if (f.getClassName().startsWith(OURS)) {
                String c = f.getClassName();
                return sb.append(" @ ").append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(f.getMethodName()).append(':')
                        .append(f.getLineNumber()).toString();
            }
        }
        return sb.toString();
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
            job.emit(Job.EventName.CANCELLED, Map.of());
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
