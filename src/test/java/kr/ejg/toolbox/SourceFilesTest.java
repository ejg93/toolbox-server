package kr.ejg.toolbox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 소스에 보이지 않는 BOM 글자(U+FEFF)를 두지 않는다(3-1). 편집 도구가 `U+FEFF` 이스케이프를 실제 글자로 적는 일이
 * 1-7·3-1 에서 두 번 났다 — 글자가 안 보여 리뷰로 못 잡는다. 필요하면 {@code (char) 0xFEFF} 로 쓴다.
 */
class SourceFilesTest {

    @Test
    void noBomCharactersInJavaSources() throws IOException {
        List<String> bad;
        try (Stream<Path> s = Stream.concat(Files.walk(Path.of("src/main/java")), Files.walk(Path.of("src/test/java")))) {
            bad = s.filter(p -> p.toString().endsWith(".java")).filter(p -> {
                try {
                    return Files.readString(p, StandardCharsets.UTF_8).indexOf((char) 0xFEFF) >= 0;
                } catch (IOException e) {
                    return true;
                }
            }).map(Path::toString).toList();
        }
        assertEquals(List.of(), bad);
    }
}
