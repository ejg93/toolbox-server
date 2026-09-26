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

    /**
     * 작업 이벤트 이름 — SSE 이벤트 이름은 {@link #wire()}(소문자). 화면 {@code common.js} 가 듣는 목록과 같아야 한다
     * (JobEventNamesTest 가 잰다, 0-28). 끝 셋은 JobManager 만 낸다
     */
    public enum EventName {
        PROGRESS, LOG, RESULT, DONE, FAILED, CANCELLED;

        public String wire() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public boolean terminal() {
            return this == DONE || this == FAILED || this == CANCELLED;
        }
    }

    /** SSE 한 건. data 는 JSON 으로 직렬화할 값. name 은 {@link EventName#wire()} */
    public record Event(String name, Object data) {
        static Event of(EventName n, Object data) {
            return new Event(n.wire(), data);
        }

        public boolean terminal() {
            return name.equals(EventName.DONE.wire()) || name.equals(EventName.FAILED.wire()) || name.equals(EventName.CANCELLED.wire());
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

    void emit(EventName eventName, Object data) {
        Event ev;
        int seq;
        synchronized (lock) {
            if (finished()) {
                return;
            }
            // 취소를 받아들인 뒤(requestCancel 이 true 를 돌려준 뒤) 본문이 끝나면 done 이 아니라 cancelled
            EventName n = eventName == EventName.DONE && cancelRequested ? EventName.CANCELLED : eventName;
            ev = n == eventName ? Event.of(n, data) : Event.of(n, Map.of());
            seq = events.size();
            events.add(ev);
            if (n.terminal()) {
                status = switch (n) {
                    case DONE -> Status.DONE;
                    case FAILED -> Status.FAILED;
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

    /**
     * QUEUED → RUNNING. 이미 취소 요청이 왔거나 끝났으면 false — 그때는 본문을 돌리지 않는다.
     * emit·requestCancel 과 같은 락이라, 취소 뒤 RUNNING 으로 덮어 끝 이벤트가 둘 쌓이는 일이 없다.
     */
    boolean start() {
        synchronized (lock) {
            if (status != Status.QUEUED || cancelRequested) {
                return false;
            }
            status = Status.RUNNING;
            return true;
        }
    }

    void setFuture(Future<?> f) {
        future = f;
    }

    boolean cancelRequested() {
        return cancelRequested;
    }

    /** 끝난 작업이면 false */
    boolean requestCancel() {
        synchronized (lock) {
            // 검사와 설정을 emit 과 같은 락 안에서 — 끝 이벤트와 엇갈려 true 를 돌려주고 DONE 이 되는 일을 막는다
            if (finished()) {
                return false;
            }
            cancelRequested = true;
        }
        Future<?> f = future;
        if (f != null) {
            f.cancel(true);
        }
        return true;
    }
}
