package kr.ejg.toolbox;

import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 골든 파일 — {@code src/test/resources/golden/<name>}.
 * {@code -Dgolden.update=true} 면 쓰고 통과, 아니면 비교해 다른 줄을 보여 주고 실패. 파일이 없으면 실패(만들라고 알린다).
 * JSON 은 키 정렬 pretty, 줄바꿈 LF.
 */
public final class GoldenFiles {

    public static final Path DIR = Path.of("src/test/resources/golden");

    public static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private GoldenFiles() {
    }

    public static boolean updating() {
        return Boolean.getBoolean("golden.update");
    }

    public static void assertJson(String name, Object value) {
        try {
            assertText(name, JSON.writeValueAsString(value) + "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void assertText(String name, String actual) {
        String norm = actual.replace("\r\n", "\n");
        Path file = DIR.resolve(name);
        try {
            if (updating()) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, norm, StandardCharsets.UTF_8);
                return;
            }
            if (!Files.exists(file)) {
                fail("골든 파일이 없다: " + file + " — -Dgolden.update=true 로 만들고 diff 를 본 뒤 커밋");
            }
            String expected = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
            if (!expected.equals(norm)) {
                fail("골든과 다르다: " + file + "\n" + diff(expected, norm));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 처음 다른 줄부터 앞뒤 몇 줄 */
    static String diff(String expected, String actual) {
        List<String> a = expected.lines().toList();
        List<String> b = actual.lines().toList();
        int i = 0;
        while (i < a.size() && i < b.size() && a.get(i).equals(b.get(i))) {
            i++;
        }
        StringBuilder sb = new StringBuilder("첫 차이 " + (i + 1) + "번째 줄\n");
        for (int k = Math.max(0, i - 2); k < Math.min(Math.max(a.size(), b.size()), i + 6); k++) {
            String x = k < a.size() ? a.get(k) : "<없음>";
            String y = k < b.size() ? b.get(k) : "<없음>";
            sb.append(x.equals(y) ? "  " : "- ").append(x).append('\n');
            if (!x.equals(y)) {
                sb.append("+ ").append(y).append('\n');
            }
        }
        return sb.toString();
    }
}
