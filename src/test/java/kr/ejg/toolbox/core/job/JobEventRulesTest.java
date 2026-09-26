package kr.ejg.toolbox.core.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 0-28 — 작업 이벤트 이름이 한곳(enum)이고 화면(common.js)이 그 이름을 전부 듣는다. 로그엔 작업 id·이름·상태만(규칙 3) —
 * 실패 작업의 예외 문구(사용자 SQL 이 들어 있을 수 있다)가 로그에 안 찍힌다.
 */
class JobEventRulesTest {

    @Test
    void commonJsListensToEveryEventName() throws Exception {
        String js = Files.readString(Path.of("src/main/resources/tools/common.js"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("var names = \\[([^\\]]*)\\]").matcher(js);
        assertTrue(m.find(), "common.js 의 SSE 이름 목록");
        TreeSet<String> listened = new TreeSet<>();
        Matcher q = Pattern.compile("'([a-z]+)'").matcher(m.group(1));
        while (q.find()) {
            listened.add(q.group(1));
        }
        listened.remove("message"); // EventSource 기본 이름
        TreeSet<String> names = new TreeSet<>();
        Arrays.stream(Job.EventName.values()).forEach(n -> names.add(n.wire()));
        assertEquals(names, listened, "enum 에 이름을 더하면 common.js 목록에도");
    }

    @Test
    void contextCannotEmitTerminalEvents() {
        JobContext ctx = new JobContext(new Job("j", "x"));
        for (Job.EventName n : Job.EventName.values()) {
            if (n.terminal()) {
                assertThrows(IllegalArgumentException.class, () -> ctx.emit(n, Map.of()), n.wire());
            }
        }
    }

    @Test
    void failedJobMessageIsNotLogged() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        JobManager jobs = new JobManager();
        try {
            Job job = jobs.submit("boom", ctx -> {
                throw new IllegalStateException("SELECT secret_col FROM customer WHERE rrn = '800101-1234567'");
            });
            long end = System.nanoTime() + 5_000_000_000L;
            while (job.status() != Job.Status.FAILED && System.nanoTime() < end) {
                Thread.sleep(10);
            }
            assertEquals(Job.Status.FAILED, job.status());
            Thread.sleep(50); // 끝 로그가 finally 에서 찍힌다
            List<String> lines = new ArrayList<>();
            logs.list.forEach(e -> lines.add(e.getFormattedMessage() + (e.getThrowableProxy() == null ? "" : " " + e.getThrowableProxy().getMessage())));
            assertFalse(lines.isEmpty(), "작업 시작·끝은 찍힌다");
            for (String l : lines) {
                assertFalse(l.contains("secret_col") || l.contains("800101"), "로그에 예외 문구: " + l);
            }
            assertTrue(job.events().get(job.events().size() - 1).data().toString().contains("secret_col"), "화면에는 간다(작업 결과)");
        } finally {
            jobs.shutdown();
            root.detachAppender(logs);
        }
    }
}
