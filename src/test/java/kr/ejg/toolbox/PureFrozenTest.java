package kr.ejg.toolbox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 순수본 동결(절대 규칙 4, 0-34). `pure/` 의 html 을 다시 해시해 `scripts/sync-pure.sh` 가 남긴 `pure/MANIFEST` 와 대조한다.
 * 해시는 CR 을 뺀 LF 기준 — Windows 작업 사본(CRLF)과 CI(LF)가 같은 값을 낸다. 고칠 일이 있으면 portfolio 에서 고치고 sync-pure 로 끌어온다.
 */
class PureFrozenTest {

    private static final Path PURE = Path.of("pure");

    @Test
    void pureMatchesManifest() throws IOException, NoSuchAlgorithmException {
        Map<String, String> expected = new TreeMap<>();
        for (String line : Files.readAllLines(PURE.resolve("MANIFEST"), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            int sp = line.indexOf("  ");
            expected.put(line.substring(sp + 2), line.substring(0, sp));
        }
        Map<String, String> actual = new TreeMap<>();
        try (Stream<Path> s = Files.walk(PURE)) {
            for (Path p : s.filter(p -> p.toString().endsWith(".html")).toList()) {
                actual.put(PURE.relativize(p).toString().replace('\\', '/'), lfSha256(p));
            }
        }
        assertEquals(expected, actual, "pure/ 가 MANIFEST 와 다르다 — 순수본은 portfolio 에서 고치고 scripts/sync-pure.sh 로 끌어온다");
    }

    private static String lfSha256(Path p) throws IOException, NoSuchAlgorithmException {
        byte[] raw = Files.readAllBytes(p);
        ByteArrayOutputStream lf = new ByteArrayOutputStream(raw.length);
        for (byte b : raw) {
            if (b != '\r') lf.write(b);
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(lf.toByteArray()));
    }
}
