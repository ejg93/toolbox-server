package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 6-22 — 미해결 종류는 Unresolved.KINDS 한 곳. 생성자가 모르는 종류를 거절하고(강제), 여기서는 소스의 종류 글자와 KINDS 가 맞물리는지 본다 —
 * 소스가 만드는 종류는 전부 KINDS 에 있고, KINDS 에는 소스가 안 쓰는 죽은 항목이 없다
 */
class UnresolvedKindsTest {

    static final Path SRC = Path.of("src/main/java/kr/ejg/toolbox/core/analyze");

    /** 종류를 만드는 꼴 다섯 — note("k" · new Unresolved("k" · note(unresolved, seen, "k" · unresolved.add("k") · note(…? "k" : "k"(삼항은 note·new Unresolved 안만) */
    static final List<Pattern> SHAPES = List.of(
            Pattern.compile("\\bnote\\(\"(\\w+)\""),
            Pattern.compile("new Unresolved\\(\"(\\w+)\""),
            Pattern.compile("note\\(unresolved, seen, \"(\\w+)\""),
            Pattern.compile("unresolved\\.add\\(\"(\\w+)\"\\)"),
            Pattern.compile("(?:note|new Unresolved)\\([^,;\"]*\\? \"(\\w+)\" : \"(\\w+)\""));

    static String sources() throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> s = Files.list(SRC)) {
            for (Path p : s.filter(p -> p.toString().endsWith(".java") && !p.getFileName().toString().equals("Unresolved.java")).sorted().toList()) {
                sb.append(Files.readString(p, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return sb.toString();
    }

    @Test
    void kindsHaveText() {
        assertEquals(19, Unresolved.KINDS.size(), Unresolved.KINDS.keySet().toString()); // 6-31 viewShape
        Unresolved.KINDS.forEach((k, v) -> assertFalse(v.name().isBlank() || v.meaning().isBlank() || v.fix().isBlank(), k));
        assertEquals("parse", Unresolved.KINDS.keySet().iterator().next());
    }

    @Test
    void sourceKindsAreAllInKinds() throws IOException {
        String src = sources();
        Set<String> found = new TreeSet<>();
        for (Pattern p : SHAPES) {
            Matcher m = p.matcher(src);
            while (m.find()) {
                for (int g = 1; g <= m.groupCount(); g++) {
                    found.add(m.group(g));
                }
            }
        }
        Set<String> unknown = new TreeSet<>(found);
        unknown.removeAll(Unresolved.KINDS.keySet());
        assertEquals(Set.of(), unknown, "소스가 만드는데 KINDS 에 뜻이 없는 종류");
        assertTrue(found.size() >= 15, "꼴 다섯이 아직 종류를 잡는다 — " + found);
    }

    @Test
    void noDeadKinds() throws IOException {
        String src = sources();
        for (String k : Unresolved.KINDS.keySet()) {
            assertTrue(src.contains("\"" + k + "\""), "KINDS 에만 있고 소스가 안 쓰는 종류: " + k);
        }
    }

    @Test
    void unknownKindIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Unresolved("nope", "f", 1, ""));
    }
}
