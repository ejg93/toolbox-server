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

    /**
     * 메타 수집 골든 — 실행마다 바뀌는 값을 가린다: dbVersion → `<version>`(이미지 갱신),
     * createdAt → 1970-01-01T00:00(값이 있다는 것만 남긴다), sizeBytes 가 0 보다 크면 1048576(1-23, 실행마다 바뀐다 — 0 은 그대로 남겨 용량 SQL 이 0 을 주는 결함을 골든이 잡는다). null 은 null 그대로 — 「모름」과 「있음」을 가른다.
     */
    public static void assertSchemas(String name, java.util.List<kr.ejg.toolbox.core.meta.Schema> schemas) {
        java.time.LocalDateTime present = java.time.LocalDateTime.of(1970, 1, 1, 0, 0);
        java.util.List<kr.ejg.toolbox.core.meta.Schema> masked = schemas.stream()
                .map(s -> new kr.ejg.toolbox.core.meta.Schema(s.name(), "<version>", s.tables().stream()
                        .map(t -> t.withStats(t.rowCount(), t.createdAt() == null ? null : present, t.lastDdlAt()))
                        .toList(), s.sizeBytes() == null || s.sizeBytes() <= 0 ? s.sizeBytes() : 1048576L))
                .toList();
        assertJson(name, masked);
    }

    /** 메타 골든(1-2·1-3)을 수집 결과 모양으로 읽는다 — 다른 테스트의 픽스처 */
    public static java.util.List<kr.ejg.toolbox.core.meta.Schema> schemas(String name) throws IOException {
        return JSON.readValue(DIR.resolve(name).toFile(),
                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<kr.ejg.toolbox.core.meta.Schema>>() {
                });
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
