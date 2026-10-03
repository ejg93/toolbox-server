package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 7-3 — 생성 한 번: 새 파일 → 둘째 실행은 전부 .gen 옆 파일 · 원본 불변 · 밖을 가리키는 세트 거절 · PK 없는 표 경고 */
class GeneratorTest {

    @TempDir
    Path tmp;

    static GenModel.Options opts() {
        return new GenModel.Options("kr.go.hr", null, List.of("TB"), Map.of(), "oracle", Map.of());
    }

    @Test
    void createsThenSidecars() throws Exception {
        TemplateSet set = TemplateSet.load(GenTemplatesTest.GEN, "egov35");
        Path out = tmp.resolve("out");
        Files.createDirectories(out);
        LocalFiles files = new LocalFiles(tmp.resolve("data"));
        Table noPk = new Table("HR", "LOG", "TABLE", null, GenModelTest.empHist().columns(), null, null, null, null, null, null, null);
        Generator.Result a = Generator.run(set, List.of(GenModelTest.empHist(), noPk), opts(), Map.of(), GenModelTest.TYPES, out, files,
                "UTF-8", "LF", null);
        assertEquals(10, a.files().size());
        assertTrue(a.files().stream().allMatch(f -> f.status().equals("created")), a.files().toString());
        assertTrue(a.warnings().stream().anyMatch(w -> w.contains("LOG") && w.contains("PK 없음")), a.warnings().toString());
        Path ctl = out.resolve("src/main/java/kr/go/hr/emphist/web/EmpHistController.java");
        String before = Files.readString(ctl, StandardCharsets.UTF_8);

        Generator.Result b = Generator.run(set, List.of(GenModelTest.empHist()), opts(), Map.of(), GenModelTest.TYPES, out, files, "UTF-8",
                "LF", null);
        assertTrue(b.files().stream().allMatch(f -> f.status().equals("sidecar") && f.rel().endsWith(".gen")), b.files().toString());
        assertEquals(before, Files.readString(ctl, StandardCharsets.UTF_8), "있는 파일은 안 덮는다");
        assertTrue(Files.exists(out.resolve("src/main/java/kr/go/hr/emphist/web/EmpHistController.java.gen")));
    }

    @Test
    void refusesSetThatPointsOutside() throws Exception {
        Path gen = tmp.resolve("gen");
        Files.createDirectories(gen.resolve("bad"));
        Files.writeString(gen.resolve("bad/set.yaml"), "name: bad\nfiles:\n  - { template: A.ftl, path: '../../escape-[=Name].txt' }\n");
        Files.writeString(gen.resolve("bad/A.ftl"), "x");
        Path out = tmp.resolve("out2");
        Files.createDirectories(out);
        TemplateSet set = TemplateSet.load(gen, "bad");
        assertThrows(IllegalArgumentException.class, () -> Generator.run(set, List.of(GenModelTest.empHist()), opts(), Map.of(),
                GenModelTest.TYPES, out, new LocalFiles(tmp.resolve("data")), "UTF-8", "LF", null));
        assertTrue(!Files.exists(tmp.resolve("escape-EmpHist.txt")));
    }
}
