package kr.ejg.toolbox.core.deliverable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kr.ejg.toolbox.core.meta.Schema;
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

    /** B-22 — 모르는 문서 번호는 건너뛴 것에 적는다 */
    @Test
    void unknownDocNumberIsReported() throws Exception {
        try (Db db = Db.open(tmp.resolve("data"))) {
            DictStore dict = new DictStore(db);
            DeliverableService.Result r = DeliverableService.build(GoldenFiles.schemas("meta/postgres-vendor.json"),
                    new DeliverableService.Request(Set.of("02", "99"), Definitions.Options.empty(), List.of(), true, List.of()), dict,
                    Mapping.load(Path.of("mappings/deliverable/example.yaml")), Path.of("templates/deliverable/example"), tmp.resolve("out"), null, null);
            assertEquals(1, r.files().size(), r.toString());
            assertTrue(r.skipped().contains("99 — 모르는 문서 번호"), r.skipped().toString());
        }
    }

    /** B-16 — 매핑이 빠진 문서가 있으면 쓰기 전에 멈춘다(반쯤 쓴 폴더가 안 남는다) */
    @Test
    void missingMappingFailsBeforeWriting() throws Exception {
        String all = Files.readString(Path.of("mappings/deliverable/example.yaml"), java.nio.charset.StandardCharsets.UTF_8);
        Mapping only01 = Mapping.parse(all.substring(0, all.indexOf("  \"02\":")));
        try (Db db = Db.open(tmp.resolve("data"))) {
            DictStore dict = new DictStore(db);
            IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> DeliverableService.build(
                    GoldenFiles.schemas("meta/postgres-vendor.json"), new DeliverableService.Request(Set.of("01", "02"), Definitions.Options.empty(),
                            List.of(), true, List.of()), dict, only01, Path.of("templates/deliverable/example"), tmp.resolve("half"), null, null));
            assertTrue(e.getMessage().contains("매핑에 문서 02 가 없다"), e.getMessage());
            try (java.util.stream.Stream<Path> s = Files.exists(tmp.resolve("half")) ? Files.walk(tmp.resolve("half")) : java.util.stream.Stream.empty()) {
                assertEquals(List.of(), s.filter(p -> p.toString().endsWith(".xlsx")).toList());
            }
        }
    }

    /** 2-56(B-12·B-13·B-16) — 양식 없음·file 없음·같은 file·머리 오타는 쓰기 전에 멈춘다(아무 xlsx 도 안 남는다) */
    @Test
    void precheckCatchesTemplateAndFileProblems() throws Exception {
        Mapping ex = Mapping.load(Path.of("mappings/deliverable/example.yaml"));
        Path some = tmp.resolve("some");
        Files.createDirectories(some);
        for (String f : List.of("01_데이터베이스정의서.xlsx", "02_테이블정의서.xlsx", "03_컬럼정의서.xlsx")) {
            Files.copy(Path.of("templates/deliverable/example").resolve(f), some.resolve(f));
        }
        java.util.function.BiFunction<String, Mapping.DocMapping, Mapping> with = (no, dm) -> {
            java.util.Map<String, Mapping.DocMapping> docs = new java.util.HashMap<>(ex.docs());
            docs.put(no, dm);
            return new Mapping(docs);
        };
        Mapping.DocMapping m02 = ex.of("02");
        java.util.Map<String, String> typo = new java.util.HashMap<>(m02.columns());
        typo.put("없는머리", typo.values().iterator().next());
        record Case(String label, Set<String> docs, Mapping mapping, Path tpl, String says) {
        }
        List<Case> cases = List.of(
                new Case("양식 없음", Set.of("01", "02", "03", "04"), ex, some, "양식 파일이 없다"),
                new Case("file 없음", Set.of("01", "02"), with.apply("02", new Mapping.DocMapping(null, m02.sheet(), m02.headerRow(), m02.firstRow(), m02.columns())),
                        Path.of("templates/deliverable/example"), "매핑 02 에 file 이 없다"),
                new Case("같은 file", Set.of("01", "02", "07"), with.apply("07", new Mapping.DocMapping(m02.file(), ex.of("07").sheet(), ex.of("07").headerRow(),
                        ex.of("07").firstRow(), ex.of("07").columns())), Path.of("templates/deliverable/example"), "같은 file 을 가리킨다"),
                // 2-58(B4-04) — 경로 표기만 다른 같은 file · 안내 파일 이름과 같은 file
                new Case("같은 file ./", Set.of("01", "02", "07"), with.apply("07", new Mapping.DocMapping("./" + m02.file(), ex.of("07").sheet(),
                        ex.of("07").headerRow(), ex.of("07").firstRow(), ex.of("07").columns())), Path.of("templates/deliverable/example"), "같은 file 을 가리킨다"),
                new Case("안내 file", Set.of("01", "02"), with.apply("02", new Mapping.DocMapping(DeliverableService.GUIDE, m02.sheet(), m02.headerRow(),
                        m02.firstRow(), m02.columns())), Path.of("templates/deliverable/example"), "같은 file 을 가리킨다"),
                new Case("머리 오타", Set.of("01", "02"), with.apply("02", new Mapping.DocMapping(m02.file(), m02.sheet(), m02.headerRow(), m02.firstRow(), typo)),
                        Path.of("templates/deliverable/example"), "없는머리"));
        try (Db db = Db.open(tmp.resolve("data"))) {
            DictStore dict = new DictStore(db);
            dict.importMoi();
            for (Case c : cases) {
                Path out = tmp.resolve("out-" + cases.indexOf(c));
                IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> DeliverableService.build(
                        GoldenFiles.schemas("meta/postgres-vendor.json"), new DeliverableService.Request(c.docs(), Definitions.Options.empty(), List.of(), true,
                                List.of()), dict, c.mapping(), c.tpl(), out, null, null), c.label());
                assertTrue(e.getMessage().contains(c.says()), c.label() + ": " + e.getMessage());
                try (java.util.stream.Stream<Path> s = Files.exists(out) ? Files.walk(out) : java.util.stream.Stream.empty()) {
                    assertEquals(List.of(), s.filter(p -> p.toString().endsWith(".xlsx")).toList(), c.label());
                }
            }
        }
    }

    /** 2-39 — templateDir null·내장 매핑이면 내장 양식으로 기입 — 파일 이름 목록이 디스크 길과 같다 */
    @Test
    void buildsWithoutTemplateDir() throws Exception {
        try (Db db = Db.open(tmp.resolve("data"))) {
            DictStore dict = new DictStore(db);
            dict.importMoi();
            DeliverableService.Request req = new DeliverableService.Request(Set.of("02", "07", "08"), Definitions.Options.empty(), List.of(), true,
                    List.of());
            DeliverableService.Result disk = DeliverableService.build(GoldenFiles.schemas("meta/postgres-vendor.json"), req, dict,
                    Mapping.load(Path.of("mappings/deliverable/example.yaml")), Path.of("templates/deliverable/example"), tmp.resolve("disk"), null,
                    null);
            DeliverableService.Result mem = DeliverableService.build(GoldenFiles.schemas("meta/postgres-vendor.json"), req, dict, Forms.mapping(),
                    null, tmp.resolve("mem"), null, null);
            assertEquals(disk.files().stream().map(f -> Path.of(f).getFileName().toString()).toList(),
                    mem.files().stream().map(f -> Path.of(f).getFileName().toString()).toList());
            assertEquals(disk.skipped(), mem.skipped());
            // 8-25(G4-03) — 만든 문서 번호가 결과에 실린다(파일 이름과 무관)
            assertTrue(mem.made().containsAll(List.of("02", "07")), mem.made().toString());
            assertTrue(mem.skipped().stream().noneMatch(s -> s.startsWith("08 ")) || !mem.made().contains("08"), "건너뛴 문서는 made 에 없다 — " + mem);
            java.util.Map<String, Mapping.DocMapping> docs = new java.util.HashMap<>(Forms.mapping().docs());
            Mapping.DocMapping m02 = docs.get("02");
            docs.put("02", new Mapping.DocMapping("tabledef.xlsx", m02.sheet(), m02.headerRow(), m02.firstRow(), m02.columns()));
            DeliverableService.Result renamed = DeliverableService.build(GoldenFiles.schemas("meta/postgres-vendor.json"), req, dict, new Mapping(docs),
                    null, tmp.resolve("renamed"), null, null);
            assertTrue(renamed.made().contains("02") && renamed.files().stream().anyMatch(f -> f.endsWith("tabledef.xlsx")), renamed.toString());
        }
    }

    /** 2-27 — 「관계 후보」: 조인 약 먼저(근거 조인), 이름 기반 뒤(근거 이름), 둘 다면 조인 행 하나에 「조인·이름」 */
    @Test
    void candidateRowsMergeJoinAndName() {
        Relations.Result r = new Relations.Result(List.of(),
                List.of(new Relations.Candidate("CUST", "CUST_ID", "ORD", "CUST_ID", 2, false), new Relations.Candidate("A", "X", "B", "Y", 1, true)),
                List.of(new Relations.Named("ORD", "CUST_ID", "CUST", "CUST_ID"), new Relations.Named("PAY", "ORD_NO", "ORD", "ORD_NO"),
                        new Relations.Named("LOG_A", "LOG_ID", "LOG_B", "LOG_ID", true)));
        assertEquals(List.of(
                List.of("CUST", "CUST_ID", "ORD", "CUST_ID", 2, "", "조인·이름"),
                List.of("A", "X", "B", "Y", 1, "Y", "조인"),
                List.of("PAY", "ORD_NO", "ORD", "ORD_NO", "", "", "이름"),
                List.of("LOG_A", "LOG_ID", "LOG_B", "LOG_ID", "", "", "이름(PK끼리)")), DeliverableService.candidates(r));
    }

    /** 2-42 — 관계 추정 알림(같은 이름 표·PK 이름 몰림)이 작성안내 요약 시트에 한 줄씩 */
    @Test
    void guideCarriesRelationNotes() throws Exception {
        List<Schema> snap = new java.util.ArrayList<>(RelationsTest.twoSchemas());
        snap.addAll(RelationsTest.idTables(40));
        try (Db db = Db.open(tmp.resolve("data"))) {
            DictStore dict = new DictStore(db);
            DeliverableService.Result r = DeliverableService.build(snap,
                    new DeliverableService.Request(Set.of("02"), Definitions.Options.empty(), List.of(), true, List.of(), null, null, RelationsTest.CUST_JOIN),
                    dict, Forms.mapping(), null, tmp.resolve("out"), null, null);
            List<String> values = new java.util.ArrayList<>();
            try (java.io.InputStream in = Files.newInputStream(Path.of(r.guide()));
                    org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in)) {
                org.apache.poi.ss.usermodel.DataFormatter fmt = new org.apache.poi.ss.usermodel.DataFormatter();
                wb.getSheet("요약").forEach(row -> values.add(fmt.formatCellValue(row.getCell(1))));
            }
            assertTrue(values.stream().anyMatch(v -> v.contains("CUSTOMER, ORDERS")), values.toString());
            assertTrue(values.stream().anyMatch(v -> v.contains("PK 컬럼 이름 ID 를 표 40개")), values.toString());
        }
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
