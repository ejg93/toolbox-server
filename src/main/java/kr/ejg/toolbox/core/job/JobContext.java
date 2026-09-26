package kr.ejg.toolbox.core.job;

import java.util.LinkedHashMap;
import java.util.Map;

/** 작업 본문이 받는 손잡이 — 진행률·중간 이벤트·취소 확인. */
public final class JobContext {

    private final Job job;

    JobContext(Job job) {
        this.job = job;
    }

    public String jobId() {
        return job.id();
    }

    /** 진행률(0~100)과 한 줄 메시지. {@code progress} 이벤트를 낸다 */
    public void progress(int percent, String message) {
        job.setProgress(percent, message);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("progress", job.progress());
        data.put("message", message);
        job.emit(Job.EventName.PROGRESS, data);
    }

    /** 중간 결과·로그 이벤트. 끝 이벤트(done·failed·cancelled)는 JobManager 만 낸다(0-28 — 이름은 enum 이라 화면이 모르는 이름이 없다) */
    public void emit(Job.EventName name, Object data) {
        if (name.terminal()) {
            throw new IllegalArgumentException("끝 이벤트는 JobManager 가 낸다: " + name.wire());
        }
        job.emit(name, data);
    }

    public boolean isCancelled() {
        return job.cancelRequested() || Thread.currentThread().isInterrupted();
    }

    /** 취소됐으면 {@link JobCancelledException} 을 던진다 — 루프 안에서 부르면 된다 */
    public void checkCancelled() {
        if (isCancelled()) {
            throw new JobCancelledException();
        }
    }

    /** 작업 본문 안에서 취소를 알리는 예외 */
    public static final class JobCancelledException extends RuntimeException {
        public JobCancelledException() {
            super("취소됨", null, false, false);
        }
    }
}
