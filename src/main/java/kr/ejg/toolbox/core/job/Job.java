package kr.ejg.toolbox.core.job;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * 긴 작업 하나. 이벤트를 전부 쌓아 두고, 늦게 붙은 구독자에게 지난 이벤트를 재생한 뒤 라이브로 넘긴다.
 * 끝 이벤트는 {@code done}·{@code failed}·{@code cancelled} 중 하나이고 그 뒤로는 이벤트가 없다.
 */
public final class Job {

    public enum Status { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

    /** SSE 한 건. data 는 JSON 으로 직렬화할 값 */
    public record Event(String name, Object data) {
        public boolean terminal() {
            return name.equals("done") || name.equals("failed") || name.equals("cancelled");
        }
    }

    private final String id;
    private final String name;
    private final Object lock = new Object();
    private final List<Event> events = new ArrayList<>();
    private final List<Sub> listeners = new CopyOnWriteArrayList<>();

    private volatile Status status = Status.QUEUED;
    private volatile int progress;
    private volatile String message;
    private volatile Object result;
    private volatile boolean cancelRequested;
    private volatile Future<?> future;

    Job(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Status status() {
        return status;
    }

    public int progress() {
        return progress;
    }

    public String message() {
        return message;
    }

    public Object result() {
        return result;
    }

    public boolean finished() {
        Status s = status;
        return s == Status.DONE || s == Status.FAILED || s == Status.CANCELLED;
    }

    /** 상태 요약 — {@code GET /api/jobs/{id}} 응답 */
    public Map<String, Object> summary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("status", status.name());
        m.put("progress", progress);
        m.put("message", message);
        if (status == Status.DONE) {
            m.put("result", result);
        }
        return m;
    }

    public List<Event> events() {
        synchronized (lock) {
            return List.copyOf(events);
        }
    }

    /**
     * 지난 이벤트를 먼저 넘기고 라이브 구독을 건다. 이미 끝난 작업이면 재생만 하고 구독은 안 건다.
     * 반환값을 부르면 구독을 푼다.
     */
    public Runnable subscribe(Consumer<Event> listener) {
        Sub sub;
        synchronized (lock) {
            // 재생은 락 안에서 — 그동안 새 이벤트가 끼어들지 않는다. 라이브는 from 이후 번호만 받는다
            for (Event ev : events) {
                listener.accept(ev);
            }
            sub = new Sub(listener, events.size());
            if (!finished()) {
                listeners.add(sub);
            }
        }
        return () -> listeners.remove(sub);
    }

    void emit(String eventName, Object data) {
        Event ev = new Event(eventName, data);
        int seq;
        synchronized (lock) {
            if (finished()) {
                return;
            }
            seq = events.size();
            events.add(ev);
            if (ev.terminal()) {
                status = switch (eventName) {
                    case "done" -> Status.DONE;
                    case "failed" -> Status.FAILED;
                    default -> Status.CANCELLED;
                };
            }
        }
        for (Sub s : listeners) {
            if (seq < s.from()) {
                continue;
            }
            try {
                s.listener().accept(ev);
            } catch (RuntimeException ignored) {
                listeners.remove(s);
            }
        }
        if (ev.terminal()) {
            listeners.clear();
        }
    }

    private record Sub(Consumer<Event> listener, int from) {
    }

    void setProgress(int p, String msg) {
        progress = Math.max(0, Math.min(100, p));
        message = msg;
    }

    void setResult(Object value) {
        result = value;
    }

    void setRunning() {
        status = Status.RUNNING;
    }

    void setFuture(Future<?> f) {
        future = f;
    }

    boolean cancelRequested() {
        return cancelRequested;
    }

    /** 끝난 작업이면 false */
    boolean requestCancel() {
        if (finished()) {
            return false;
        }
        cancelRequested = true;
        Future<?> f = future;
        if (f != null) {
            f.cancel(true);
        }
        return true;
    }
}
