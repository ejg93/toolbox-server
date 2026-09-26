package kr.ejg.toolbox.core.deliverable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.db.Db;
import kr.ejg.toolbox.core.dict.DictStore;
import kr.ejg.toolbox.core.report.Mapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 2-5 — 작업 본문: 고른 문서만, 08 은 접속 없으면 건너뜀, 매핑 file 은 폴더 밖을 못 가리킨다 */
class DeliverableServiceTest {

    @TempDir
    Path tmp;

    @Test
    void buildsChosenDocsAndSkips08WithoutConnection() throws Exception {
        try (Db db = Db.open(tmp.resolve("data"))) {
            DictStore dict = new DictStore(db);
            dict.importMoi();
            DeliverableService.Result r = DeliverableService.build(GoldenFiles.schemas("meta/postgres-vendor.json"),
                    new DeliverableService.Request(Set.of("02", "07", "08"), Definitions.Options.empty(), List.of(), true, List.of()), dict,
                    Mapping.load(Path.of("mappings/deliverable/example.yaml")), Path.of("templates/deliverable/example"), tmp.resolve("out"), null,
                    null);
            assertEquals(2, r.files().size(), r.toString());
            assertTrue(r.files().get(0).endsWith("02_테이블정의서.xlsx"));
            assertTrue(r.files().get(1).endsWith("07_DB표준용어.xlsx"));
            assertEquals(1, r.skipped().size());
            assertTrue(r.skipped().get(0).startsWith("08"));
            assertTrue(Files.exists(Path.of(r.files().get(0))));
        }
    }

    @Test
    void mappingFileMustStayInsideFolder() {
        Path dir = tmp.resolve("out");
        assertTrue(DeliverableService.inside(dir, "02_x.xlsx").startsWith(dir.toAbsolutePath()));
        assertThrows(IllegalArgumentException.class, () -> DeliverableService.inside(dir, "../evil.xlsx"));
        assertThrows(IllegalArgumentException.class, () -> DeliverableService.inside(dir, tmp.resolve("abs.xlsx").toAbsolutePath().toString()));
        assertThrows(IllegalArgumentException.class, () -> DeliverableService.inside(dir, "."));
    }
}
