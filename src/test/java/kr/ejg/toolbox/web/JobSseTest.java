package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JobSseTest {

    static Javalin app;
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeAll
    static void up() {
        app = App.start(AppTest.config(0));
    }

    @AfterAll
    static void down() {
        app.stop();
    }

    private static URI uri(String path) {
        return URI.create("http://127.0.0.1:" + app.port() + path);
    }

    private static String startDemo(int steps, int delayMs) throws Exception {
        HttpResponse<String> res = HTTP.send(
                HttpRequest.newBuilder(uri("/api/jobs/demo?steps=" + steps + "&delayMs=" + delayMs))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(202, res.statusCode());
        return new ObjectMapper().readTree(res.body()).get("jobId").asText();
    }

    /** SSE 스트림에서 이벤트 이름을 끝 이벤트까지 모은다. 10초 제한 */
    private static List<String> eventNames(String jobId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(uri("/api/jobs/" + jobId + "/events"))
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(10)).GET().build();
        CompletableFuture<List<String>> f = CompletableFuture.supplyAsync(() -> {
            List<String> names = new ArrayList<>();
            try {
                HttpResponse<Stream<String>> res = HTTP.send(req, HttpResponse.BodyHandlers.ofLines());
                try (Stream<String> lines = res.body()) {
                    Iterator<String> it = lines.iterator();
                    while (it.hasNext()) {
                        String line = it.next();
                        if (line.startsWith("event:")) {
                            String name = line.substring("event:".length()).trim();
                            names.add(name);
                            if (name.equals("done") || name.equals("failed") || name.equals("cancelled")) {
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return names;
        });
        return f.get(10, TimeUnit.SECONDS);
    }

    @Test
    void streamGivesProgressThenDone() throws Exception {
        List<String> names = eventNames(startDemo(10, 50));
        assertTrue(names.stream().filter("progress"::equals).count() >= 1, names.toString());
        assertEquals("done", names.get(names.size() - 1), names.toString());
    }

    @Test
    void finishedJobReplaysAllEvents() throws Exception {
        String id = startDemo(3, 0);
        Thread.sleep(300);
        List<String> names = eventNames(id);
        assertEquals(List.of("progress", "progress", "progress", "done"), names);
    }

    @Test
    void deleteCancelsRunningJob() throws Exception {
        String id = startDemo(10, 200);
        Thread.sleep(300);
        HttpResponse<String> del = HTTP.send(
                HttpRequest.newBuilder(uri("/api/jobs/" + id)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, del.statusCode());
        List<String> names = eventNames(id);
        assertEquals("cancelled", names.get(names.size() - 1), names.toString());

        HttpResponse<String> st = HTTP.send(HttpRequest.newBuilder(uri("/api/jobs/" + id)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals("CANCELLED", new ObjectMapper().readTree(st.body()).get("status").asText());
    }

    @Test
    void unknownJobIs404() throws Exception {
        HttpResponse<String> st = HTTP.send(HttpRequest.newBuilder(uri("/api/jobs/nope1234")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, st.statusCode());
    }
}
