package kr.ejg.toolbox.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.sse.SseClient;
import java.util.Map;
import java.util.Optional;
import kr.ejg.toolbox.core.job.Job;
import kr.ejg.toolbox.core.job.JobManager;

/**
 * {@code GET /api/jobs/{id}} 상태 · {@code DELETE /api/jobs/{id}} 취소 · {@code GET /api/jobs/{id}/events} SSE.
 * SSE 는 지난 이벤트를 재생한 뒤 라이브로 잇고, 끝 이벤트를 보내면 닫는다.
 * {@code POST /api/jobs/demo} 는 배선 확인용 더미 작업(steps 단계, 단계마다 delayMs).
 */
final class JobRoutes {

    private static final ObjectMapper JSON = new ObjectMapper();

    private JobRoutes() {
    }

    static void register(Javalin app, JobManager jobs) {
        app.post("/api/jobs/demo", ctx -> {
            int steps = clamp(queryInt(ctx.queryParam("steps"), 10), 1, 100);
            int delayMs = clamp(queryInt(ctx.queryParam("delayMs"), 1000), 0, 10_000);
            Job job = jobs.submit("demo", jc -> {
                for (int i = 1; i <= steps; i++) {
                    Thread.sleep(delayMs);
                    jc.checkCancelled();
                    jc.progress(i * 100 / steps, i + "/" + steps);
                }
                return Map.of("steps", steps);
            });
            ctx.status(202).json(Map.of("jobId", job.id()));
        });

        app.get("/api/jobs/{id}", ctx -> {
            Optional<Job> job = jobs.get(ctx.pathParam("id"));
            if (job.isEmpty()) {
                ctx.status(404).json(Map.of("message", "작업이 없다"));
                return;
            }
            ctx.json(job.get().summary());
        });

        app.delete("/api/jobs/{id}", ctx -> {
            String id = ctx.pathParam("id");
            if (jobs.get(id).isEmpty()) {
                ctx.status(404).json(Map.of("message", "작업이 없다"));
                return;
            }
            ctx.json(Map.of("cancelled", jobs.cancel(id)));
        });

        app.sse("/api/jobs/{id}/events", client -> stream(client, jobs));
    }

    private static void stream(SseClient client, JobManager jobs) {
        Optional<Job> job = jobs.get(client.ctx().pathParam("id"));
        if (job.isEmpty()) {
            client.sendEvent("failed", json(Map.of("message", "작업이 없다")));
            client.close();
            return;
        }
        client.keepAlive();
        Runnable unsubscribe = job.get().subscribe(ev -> {
            if (client.terminated()) {
                throw new IllegalStateException("SSE 끊김");
            }
            client.sendEvent(ev.name(), json(ev.data()));
            if (ev.terminal()) {
                client.close();
            }
        });
        client.onClose(unsubscribe);
    }

    private static String json(Object data) {
        try {
            return JSON.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static int queryInt(String s, int dflt) {
        if (s == null || s.isBlank()) {
            return dflt;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
