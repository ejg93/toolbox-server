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
    }

    @Test
    void insideRunFolderWrites() throws Exception {
        Path base = Path.of("target/tb-make-templates");
        assertEquals(0, run(OUT, base.resolve("forms").toString(), MAPPING, base.resolve(M_YAML).toString()));
        assertEquals(11, count(base.resolve("forms"), ".xlsx"));
        assertTrue(Files.isRegularFile(base.resolve(M_YAML)));
    }
}
