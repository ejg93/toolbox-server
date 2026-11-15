package kr.ejg.toolbox.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** 0-85d — make-templates 는 실행 폴더 아래에만 쓴다(절대 규칙 7 — 도구가 제 폴더 밖에 쓰지 않는다) */
class MakeTemplatesTest {

    private static final String OUT = "--out";
    private static final String MAPPING = "--mapping";
    private static final String M_YAML = "m.yaml";

    @TempDir
    Path tmp;

    private static int run(String... args) {
        CommandLine cmd = new CommandLine(new MakeTemplates());
        cmd.setOut(new PrintWriter(new StringWriter()));
        cmd.setErr(new PrintWriter(new StringWriter()));
        return cmd.execute(args);
    }

    private static long count(Path dir, String ext) throws Exception {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(ext)).count();
        }
    }

    @Test
    void outsideRunFolderIsRefused() throws Exception {
        Path forms = tmp.resolve("forms");
        assertNotEquals(0, run(OUT, forms.toString(), MAPPING, "target/tb-make-templates/x.yaml"));
        assertEquals(0, count(forms, ""), "밖 폴더에 아무것도 안 쓴다");
        assertNotEquals(0, run(OUT, "target/tb-make-templates/forms-x", MAPPING, tmp.resolve(M_YAML).toString()));
        assertTrue(Files.notExists(tmp.resolve(M_YAML)));
        assertNotEquals(0, run(OUT, "\\\\srv\\share\\forms"));
        assertNotEquals(0, run(OUT, " \\\\srv\\share\\forms"), "0-87 — 앞 공백 꼴도");
    }

    /** 심볼릭 링크, 못 만들면 Windows 정션(권한 없이 된다), 그것도 안 되면 건너뜀 */
    static void link(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
            return;
        } catch (java.io.IOException | UnsupportedOperationException e) {
            // tb-ignore java.emptyCatch 정션으로 다시 해 본다
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        if (windows && new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor() == 0 && Files.exists(link)) {
            return;
        }
        org.junit.jupiter.api.Assumptions.abort("링크·정션을 못 만든다");
    }

    /** 0-86 — 실행 폴더 안 링크가 밖을 가리키면 링크를 따라간 자리로 재서 거절 */
    @Test
    void linkOutsideRunFolderIsRefused() throws Exception {
        Path outside = tmp.resolve("outside");
        Files.createDirectories(outside);
        Path link = Path.of("target/tb-make-templates/link-out");
        Files.createDirectories(link.getParent());
        Files.deleteIfExists(link);
        link(link.toAbsolutePath(), outside);
        try {
            assertNotEquals(0, run(OUT, link.resolve("x").toString(), MAPPING, "target/tb-make-templates/link.yaml"));
            assertEquals(0, count(outside, ""), "링크 너머 밖 폴더에 아무것도 안 쓴다");
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test
    void insideRunFolderWrites() throws Exception {
        Path base = Path.of("target/tb-make-templates");
        assertEquals(0, run(OUT, base.resolve("forms").toString(), MAPPING, base.resolve(M_YAML).toString()));
        assertEquals(11, count(base.resolve("forms"), ".xlsx"));
        assertTrue(Files.isRegularFile(base.resolve(M_YAML)));
    }
}
