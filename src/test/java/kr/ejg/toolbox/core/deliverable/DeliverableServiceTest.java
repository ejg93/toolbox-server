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
            java.util.Map<String, String> summary = sheet(Path.of(r.guide()), "요약");
            Definitions.CommentFill f = Definitions.commentFill(Definitions.sorted(GoldenFiles.schemas("meta/postgres-vendor.json")));
            assertEquals(f.tablesLine(), summary.get("코멘트 채움(표)"), "2-24");
            assertEquals(f.columnsLine(), summary.get("코멘트 채움(컬럼)"));
        }
    }

    /** 2-27 — 「관계 후보」: 조인 약 먼저(근거 조인), 이름 기반 뒤(근거 이름), 둘 다면 조인 행 하나에 「조인·이름」 */
    @Test
    void candidateRowsMergeJoinAndName() {
        Relations.Result r = new Relations.Result(List.of(),
                List.of(new Relations.Candidate("CUST", "CUST_ID", "ORD", "CUST_ID", 2, false), new Relations.Candidate("A", "X", "B", "Y", 1, true)),
                List.of(new Relations.Named("ORD", "CUST_ID", "CUST", "CUST_ID"), new Relations.Named("PAY", "ORD_NO", "ORD", "ORD_NO")));
        assertEquals(List.of(
                List.of("CUST", "CUST_ID", "ORD", "CUST_ID", 2, "", "조인·이름"),
                List.of("A", "X", "B", "Y", 1, "Y", "조인"),
                List.of("PAY", "ORD_NO", "ORD", "ORD_NO", "", "", "이름")), DeliverableService.candidates(r));
    }

    /** 작성안내 시트 두 열(항목·값)을 맵으로 */
    static java.util.Map<String, String> sheet(Path xlsx, String name) throws Exception {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        try (java.io.InputStream in = Files.newInputStream(xlsx); org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in)) {
            org.apache.poi.ss.usermodel.DataFormatter fmt = new org.apache.poi.ss.usermodel.DataFormatter();
            for (org.apache.poi.ss.usermodel.Row row : wb.getSheet(name)) {
                out.put(fmt.formatCellValue(row.getCell(0)), fmt.formatCellValue(row.getCell(1)));
            }
        }
        return out;
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
