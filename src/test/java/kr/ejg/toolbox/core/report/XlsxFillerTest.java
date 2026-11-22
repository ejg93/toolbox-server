package kr.ejg.toolbox.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.deliverable.Definitions;
import kr.ejg.toolbox.core.deliverable.Forms;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 2-4 — 2-1 PG 골든 값 표를 커밋된 예시 양식에 기입 → POI 로 다시 읽어 셀 값 골든(02·03·10). 매핑 오타·머리 불일치 예외.
 * 커밋된 예시 양식·매핑이 값 표 열과 같은지도 잰다(열을 바꾸고 양식을 안 다시 만들면 빨강).
 */
class XlsxFillerTest {

    static final Path TEMPLATES = Path.of("templates/deliverable/example");
    static final Path MAPPING = Path.of("mappings/deliverable/example.yaml");

    @TempDir
    Path tmp;

    static List<Doc> pgDocs() throws Exception {
        return Definitions.build(GoldenFiles.schemas("meta/postgres-vendor.json"),
                new Definitions.Options("홍길동", "행정기관", "정보화팀", "민원", null, null, null, null));
    }

    static Doc doc(List<Doc> docs, String no) {
        return docs.stream().filter(d -> d.no().equals(no)).findFirst().orElseThrow();
    }

    /** 시트 → 행 목록(수는 수, 빈 셀은 "") — 제목·머리 포함 */
    static List<List<Object>> read(Path xlsx) throws Exception {
        List<List<Object>> out = new ArrayList<>();
        try (InputStream in = Files.newInputStream(xlsx); Workbook wb = new XSSFWorkbook(in)) {
            Sheet s = wb.getSheetAt(0);
            for (int r = 0; r <= s.getLastRowNum(); r++) {
                Row row = s.getRow(r);
                List<Object> cells = new ArrayList<>();
                int last = row == null ? 0 : row.getLastCellNum();
                for (int c = 0; c < last; c++) {
                    Cell cell = row.getCell(c);
                    if (cell == null || cell.getCellType() == CellType.BLANK) {
                        cells.add("");
                    } else if (cell.getCellType() == CellType.NUMERIC) {
                        double v = cell.getNumericCellValue();
                        cells.add(v == Math.rint(v) ? (Object) (long) v : (Object) v);
                    } else {
                        cells.add(cell.getStringCellValue());
                    }
                }
                out.add(cells);
            }
        }
        return out;
    }

    /** 2-39 — 내장 양식(메모리) 기입 결과 = 커밋된 디스크 양식 기입 결과. 열한 장 · 02·03·10 은 기존 골든과도 같다 */
    @Test
    void builtInFillEqualsDiskTemplate() throws Exception {
        Mapping m = Mapping.load(MAPPING);
        for (Form f : Forms.all()) {
            List<Object> text = new ArrayList<>();
            List<Object> num = new ArrayList<>();
            for (int i = 0; i < f.columns().size(); i++) {
                text.add(i == 1 ? "" : "가" + i);
                num.add(i == 1 ? "" : (Object) Long.valueOf(i));
            }
            Doc d = new Doc(f.no(), f.name(), f.columns(), List.of(text, num));
            Mapping.DocMapping dm = m.of(f.no());
            Path disk = tmp.resolve("disk-" + dm.file());
            Path mem = tmp.resolve("mem-" + dm.file());
            XlsxFiller.fill(TEMPLATES.resolve(dm.file()), dm, d, disk);
            XlsxFiller.fill(f, dm, d, mem);
            assertEquals(read(disk), read(mem), f.no());
        }
        List<Doc> docs = pgDocs();
        for (String no : List.of("02", "03", "10")) {
            Mapping.DocMapping dm = m.of(no);
            Path out = tmp.resolve("builtin-" + dm.file());
            XlsxFiller.fill(Forms.of(no), dm, doc(docs, no), out);
            GoldenFiles.assertJson("deliverable/filled-" + no + ".json", read(out));
        }
    }

    /** 2-39 — 양식 파일이 xlsx(zip)가 아니면(문서보안 암호화) POI 에 넘기기 전에 경로를 담은 사유로 멈춘다 */
    @Test
    void notXlsxSaysWhy() throws Exception {
        Mapping.DocMapping dm = Mapping.load(MAPPING).of("01");
        Doc d = new Doc("01", "x", Forms.of("01").columns(), List.of());
        Path drm = tmp.resolve("x.xlsx");
        Files.write(drm, "DRMFILE-encrypted".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> XlsxFiller.fill(drm, dm, d, tmp.resolve("o.xlsx")));
        assertTrue(e.getMessage().contains("양식이 xlsx 가 아니다") && e.getMessage().contains("x.xlsx") && e.getMessage().contains("문서보안"),
                e.getMessage());
        IllegalArgumentException none = assertThrows(IllegalArgumentException.class,
                () -> XlsxFiller.fill(tmp.resolve("없음.xlsx"), dm, d, tmp.resolve("o.xlsx")));
        assertTrue(none.getMessage().contains("양식 파일이 없다"), none.getMessage());
    }

    /** 2-39 — 내장 매핑 = 커밋된 예시 매핑 · 예시 자리 판정(비었거나 예시 폴더를 가리키면 내장) */
    @Test
    void builtInMappingEqualsCommitted() throws Exception {
        assertEquals(Mapping.load(MAPPING), Forms.mapping());
        String ex = Forms.EXAMPLE_DIR;
        for (String yes : java.util.Arrays.asList(null, " ", ex, ex + "/", "./" + ex, ex.replace('/', '\\') + "\\",
                Path.of(ex).toAbsolutePath().toString())) {
            assertTrue(Forms.builtIn(yes, ex), String.valueOf(yes));
        }
        for (String no : List.of("templates/deliverable/site", "templates/deliverable")) {
            assertTrue(!Forms.builtIn(no, ex), no);
        }
    }

    @Test
    void fillsExampleTemplatesGolden() throws Exception {
        Mapping m = Mapping.load(MAPPING);
        List<Doc> docs = pgDocs();
        for (String no : List.of("02", "03", "10")) {
            Mapping.DocMapping dm = m.of(no);
            Path out = tmp.resolve(dm.file());
            XlsxFiller.fill(TEMPLATES.resolve(dm.file()), dm, doc(docs, no), out);
            List<List<Object>> rows = read(out);
            assertEquals(2 + doc(docs, no).rows().size(), rows.size(), no + " 제목·머리 + 값 행");
            GoldenFiles.assertJson("deliverable/filled-" + no + ".json", rows);
        }
    }

    @Test
    void dataRowsKeepTemplateStyleAndNumbersStayNumbers() throws Exception {
        Mapping.DocMapping dm = Mapping.load(MAPPING).of("02");
        Path out = tmp.resolve("x.xlsx");
        XlsxFiller.fill(TEMPLATES.resolve(dm.file()), dm, doc(pgDocs(), "02"), out);
        try (InputStream in = Files.newInputStream(out); Workbook wb = new XSSFWorkbook(in)) {
            Sheet s = wb.getSheetAt(0);
            int col = headerCol(s, "순번");
            assertTrue(col >= 0, "머리글에 순번");
            Cell seq = s.getRow(4).getCell(col);
            assertEquals(CellType.NUMERIC, seq.getCellType(), "순번은 수");
            assertEquals(org.apache.poi.ss.usermodel.BorderStyle.THIN, s.getRow(9).getCell(5).getCellStyle().getBorderTop(),
                    "양식 3행의 테두리를 모든 행이 받는다(빈칸 포함)");
            assertEquals("테이블 정의서", s.getSheetName(), "시트 이름은 그대로");
        }
    }

    /** 2-21 — 02 테이블 볼륨: 수 셀은 엑셀 형식 #,##0"건"(값은 수), 모르면 글 「통계 없음」 */
    @Test
    void volumeShowsCountUnit() throws Exception {
        Mapping.DocMapping dm = Mapping.load(MAPPING).of("02");
        Doc pg = doc(pgDocs(), "02");
        int v = pg.columns().indexOf("테이블 볼륨");
        List<List<Object>> rows = new ArrayList<>();
        for (List<Object> r : pg.rows()) {
            rows.add(new ArrayList<>(r));
        }
        rows.get(0).set(v, 1234L);
        Doc d = new Doc(pg.no(), pg.name(), pg.columns(), rows, null, pg.formats());
        Path out = tmp.resolve("vol.xlsx");
        XlsxFiller.fill(TEMPLATES.resolve(dm.file()), dm, d, out);
        try (InputStream in = Files.newInputStream(out); Workbook wb = new XSSFWorkbook(in)) {
            Sheet s = wb.getSheetAt(0);
            int col = headerCol(s, "테이블 볼륨");
            int first = dm.firstRow() - 1;
            Cell num = s.getRow(first).getCell(col);
            assertEquals(CellType.NUMERIC, num.getCellType(), "값은 수");
            assertEquals("#,##0\"건\"", num.getCellStyle().getDataFormatString());
            assertEquals("1,234건", new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(num));
            assertEquals("통계 없음", s.getRow(first + 1).getCell(col).getStringCellValue());
        }
    }

    /** 1-33 — 양식 열 너비는 넓히기만: 좁게 둔 열은 값에 맞게 넓어지고, 더 넓은 열은 그대로 */
    @Test
    void templateColumnsOnlyWiden() throws Exception {
        Mapping.DocMapping dm = Mapping.load(MAPPING).of("02");
        Path tpl = tmp.resolve("narrow.xlsx");
        int narrow;
        int wide;
        try (InputStream in = Files.newInputStream(TEMPLATES.resolve(dm.file())); Workbook wb = new XSSFWorkbook(in)) {
            Sheet s = wb.getSheetAt(0);
            narrow = headerCol(s, "순번");
            wide = headerCol(s, "테이블 볼륨") >= 0 ? headerCol(s, "테이블 볼륨") : narrow + 1;
            s.setColumnWidth(narrow, 2 * 256);
            s.setColumnWidth(wide, 90 * 256);
            try (var os = Files.newOutputStream(tpl)) {
                wb.write(os);
            }
        }
        Path out = tmp.resolve("w.xlsx");
        XlsxFiller.fill(tpl, dm, doc(pgDocs(), "02"), out);
        try (InputStream in = Files.newInputStream(out); Workbook wb = new XSSFWorkbook(in)) {
            Sheet s = wb.getSheetAt(0);
            assertEquals(ColumnWidths.MIN, s.getColumnWidth(narrow) / 256, "「순번」(4)+2 < 하한 6 — 2 에서 6 으로 넓힘");
            assertEquals(90, s.getColumnWidth(wide) / 256, "양식이 더 넓으면 그대로");
        }
    }

    @Test
    void rowsBelowFirstRowAreShiftedDown() throws Exception {
        Path tpl = tmp.resolve("t.xlsx");
        XlsxFiller.writeTemplate(Forms.all().get(10), tpl);
        try (InputStream in = Files.newInputStream(tpl); Workbook wb = new XSSFWorkbook(in)) {
            wb.getSheetAt(0).createRow(4).createCell(0).setCellValue("작성자 서명란");
            try (var os = Files.newOutputStream(tpl)) {
                wb.write(os);
            }
        }
        Mapping.DocMapping dm = Mapping.load(MAPPING).of("11");
        Doc d11 = doc(pgDocs(), "11");
        Path out = tmp.resolve("o.xlsx");
        XlsxFiller.fill(tpl, dm, d11, out);
        List<List<Object>> rows = read(out);
        assertEquals("작성자 서명란", rows.get(rows.size() - 1).get(0), "양식 아래쪽은 값 행 뒤로 밀린다");
        assertEquals(2 + d11.rows().size() + 2, rows.size(), "빈 행 하나 + 서명란");
    }

    /** 2-41(B-01) — 합계 수식이 데이터 첫 행에서 끝나는 범위면 채운 끝 행까지 넓힌다. 단일 칸 참조는 그대로 */
    @Test
    void totalsFormulaIsWidened() throws Exception {
        Path tpl = tmp.resolve("sum.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("S");
            s.createRow(0).createCell(0).setCellValue("제목");
            Row h = s.createRow(1);
            h.createCell(0).setCellValue("이름");
            h.createCell(1).setCellValue("값");
            s.createRow(2);
            Row total = s.createRow(3);
            total.createCell(1).setCellFormula("SUM(B3:B3)");
            total.createCell(2).setCellFormula("COUNTA(A3:A3)");
            total.createCell(3).setCellFormula("B3*2");
            try (var os = Files.newOutputStream(tpl)) {
                wb.write(os);
            }
        }
        Mapping.DocMapping dm = new Mapping.DocMapping("sum.xlsx", "S", 2, 3, Map.of("이름", "nm", "값", "v"));
        Path out = tmp.resolve("sum-out.xlsx");
        assertEquals(List.of(), XlsxFiller.fill(tpl, dm, new Doc("X", "x", List.of("nm", "v"), List.of(List.of("a", 10), List.of("b", 20), List.of("c", 30)), null, null),
                out), "2-47 — 넓힌 합계는 알림 없음");
        try (InputStream in = Files.newInputStream(out); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Row r = wb.getSheetAt(0).getRow(5);
            assertEquals(List.of("SUM(B3:B5)", "COUNTA(A3:A5)", "B3*2"),
                    List.of(r.getCell(1).getCellFormula(), r.getCell(2).getCellFormula(), r.getCell(3).getCellFormula()));
            assertEquals(60.0, wb.getCreationHelper().createFormulaEvaluator().evaluate(r.getCell(1)).getNumberValue());
        }
        Path one = tmp.resolve("sum-one.xlsx");
        XlsxFiller.fill(tpl, dm, new Doc("X", "x", List.of("nm", "v"), List.of(List.of("a", 10)), null, null), one);
        try (InputStream in = Files.newInputStream(one); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            assertEquals("SUM(B3:B3)", wb.getSheetAt(0).getRow(3).getCell(1).getCellFormula());
        }
    }

    /** 2-53(B-14) — 32,767자를 넘는 값(긴 CHECK·코멘트)은 잘라 쓰고 열마다 알림 하나 */
    @Test
    void overlongValueIsClippedWithNote() throws Exception {
        Path tpl = tmp.resolve("long.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("S");
            s.createRow(0).createCell(0).setCellValue("제목");
            Row h = s.createRow(1);
            h.createCell(0).setCellValue("이름");
            h.createCell(1).setCellValue("값");
            try (var os = Files.newOutputStream(tpl)) {
                wb.write(os);
            }
        }
        Path out = tmp.resolve("long-out.xlsx");
        List<String> notes = XlsxFiller.fill(tpl, new Mapping.DocMapping("long.xlsx", "S", 2, 3, Map.of("이름", "nm", "값", "v")),
                new Doc("X", "x", List.of("nm", "v"), List.of(List.of("a", "c".repeat(40_000)), List.of("b", "d".repeat(40_000))), null, null), out);
        assertEquals(List.of("값 칸이 32,767자를 넘어 잘랐다"), notes);
        try (InputStream in = Files.newInputStream(out); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            assertEquals(32_767, wb.getSheetAt(0).getRow(2).getCell(1).getStringCellValue().length());
        }
    }

    /** 2-52(B-01·B-02·B-04) — 다시 계산 표시는 수식이 있으면 늘 · 데이터 행 위 수식도 넓힘 · 아래 행 없는 양식 · 단일 칸 배열 수식은 배열 그대로 */
    @Test
    void formulasAboveRecalcAndSingleCellArray() throws Exception {
        Path tpl = tmp.resolve("f.xlsx");
        Path flat = tmp.resolve("flat.xlsx");
        for (Path p : List.of(tpl, flat)) {
            try (XSSFWorkbook wb = new XSSFWorkbook()) {
                Sheet s = wb.createSheet("S");
                s.createRow(0).createCell(0).setCellFormula("COUNTA(A3:A3)");
                Row h = s.createRow(1);
                h.createCell(0).setCellValue("이름");
                h.createCell(1).setCellValue("값");
                h.createCell(2).setCellValue("수량");
                s.createRow(2);
                if (p.equals(tpl)) {
                    s.createRow(3).createCell(1).setCellFormula("SUM(B3:B3)");
                    s.getRow(3).createCell(4).setCellFormula("B3*2");
                    s.setArrayFormula("SUM(B3:B3*C3:C3)", new org.apache.poi.ss.util.CellRangeAddress(3, 3, 3, 3));
                }
                try (var os = Files.newOutputStream(p)) {
                    wb.write(os);
                }
            }
        }
        Map<String, String> cols = Map.of("이름", "nm", "값", "v", "수량", "q");
        List<List<Object>> three = List.of(List.of("a", 10, 1), List.of("b", 20, 2), List.of("c", 30, 3));
        Path out = tmp.resolve("f-out.xlsx");
        assertEquals(List.of(), XlsxFiller.fill(tpl, new Mapping.DocMapping("f.xlsx", "S", 2, 3, cols),
                new Doc("X", "x", List.of("nm", "v", "q"), three, null, null), out));
        try (InputStream in = Files.newInputStream(out); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet s = wb.getSheetAt(0);
            assertTrue(s.getForceFormulaRecalculation(), "B-01 — 다시 계산");
            assertEquals("COUNTA(A3:A5)", s.getRow(0).getCell(0).getCellFormula(), "B-02 — 데이터 행 위");
            Cell arr = s.getRow(5).getCell(3);
            assertTrue(arr.isPartOfArrayFormulaGroup(), "B-04 — 배열 수식 그대로");
            assertEquals("SUM(B3:B5*C3:C5)", arr.getCellFormula());
            assertEquals("B3*2", s.getRow(5).getCell(4).getCellFormula(), "지킴이 — 단일 칸 참조");
        }
        Path one = tmp.resolve("f-one.xlsx");
        XlsxFiller.fill(tpl, new Mapping.DocMapping("f.xlsx", "S", 2, 3, cols), new Doc("X", "x", List.of("nm", "v", "q"), three.subList(0, 1), null, null), one);
        try (InputStream in = Files.newInputStream(one); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            assertTrue(wb.getSheetAt(0).getForceFormulaRecalculation(), "B-01 — 한 행 SUM(B3:B3) 도 다시 계산");
        }
        Path flatOut = tmp.resolve("flat-out.xlsx");
        XlsxFiller.fill(flat, new Mapping.DocMapping("flat.xlsx", "S", 2, 3, cols), new Doc("X", "x", List.of("nm", "v", "q"), three, null, null), flatOut);
        try (InputStream in = Files.newInputStream(flatOut); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            assertEquals("COUNTA(A3:A5)", wb.getSheetAt(0).getRow(0).getCell(0).getCellFormula(), "아래 행 없는 양식");
        }
    }

    /** 2-44 — 합계 행이 다중 칸 배열 수식이어도 채우기가 끝나고, 그 수식은 그대로 남는다 */
    @Test
    void arrayFormulaDoesNotStopFill() throws Exception {
        Path tpl = tmp.resolve("arr.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("S");
            s.createRow(0).createCell(0).setCellValue("제목");
            Row h = s.createRow(1);
            h.createCell(0).setCellValue("이름");
            h.createCell(1).setCellValue("값");
            s.createRow(2);
            s.createRow(3);
            s.setArrayFormula("B3:B3*2", new org.apache.poi.ss.util.CellRangeAddress(3, 3, 1, 2));
            try (var os = Files.newOutputStream(tpl)) {
                wb.write(os);
            }
        }
        Mapping.DocMapping dm = new Mapping.DocMapping("arr.xlsx", "S", 2, 3, Map.of("이름", "nm", "값", "v"));
        Path out = tmp.resolve("arr-out.xlsx");
        List<String> notes = XlsxFiller.fill(tpl, dm, new Doc("X", "x", List.of("nm", "v"), List.of(List.of("a", 10), List.of("b", 20), List.of("c", 30)), null, null), out);
        assertEquals(1, notes.size(), notes.toString()); // 2-47 — 안 넓힌 배열 수식을 알린다
        assertTrue(notes.get(0).startsWith("배열 수식 합계 1개"), notes.toString());
        try (InputStream in = Files.newInputStream(out); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            assertTrue(wb.getSheetAt(0).getRow(5).getCell(1).isPartOfArrayFormulaGroup());
        }
    }

    /** B-17 — 같은 머리 글이 둘이면 뒤 칸에 채우고 알린다 */
    @Test
    void duplicateHeaderIsReported() throws Exception {
        Path tpl = tmp.resolve("dup.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("S");
            s.createRow(0).createCell(0).setCellValue("제목");
            Row h = s.createRow(1);
            h.createCell(0).setCellValue("이름");
            h.createCell(1).setCellValue("값");
            h.createCell(2).setCellValue("값");
            s.createRow(2);
            try (var os = Files.newOutputStream(tpl)) {
                wb.write(os);
            }
        }
        Mapping.DocMapping dm = new Mapping.DocMapping("dup.xlsx", "S", 2, 3, Map.of("이름", "nm", "값", "v"));
        Path out = tmp.resolve("dup-out.xlsx");
        List<String> notes = XlsxFiller.fill(tpl, dm, new Doc("X", "x", List.of("nm", "v"), List.of(List.of("a", 10)), null, null), out);
        assertEquals(1, notes.size(), notes.toString());
        assertTrue(notes.get(0).startsWith("머리 「값」 가 양식에 둘 이상"), notes.toString());
        try (InputStream in = Files.newInputStream(out); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            assertEquals(10.0, wb.getSheetAt(0).getRow(2).getCell(2).getNumericCellValue());
        }
    }

    /** B-23 — 데이터 첫 행 안 병합은 채운 행마다 잇는다 */
    @Test
    void dataRowMergesAreRepeated() throws Exception {
        Path tpl = tmp.resolve("merge.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("S");
            s.createRow(0).createCell(0).setCellValue("제목");
            Row h = s.createRow(1);
            h.createCell(0).setCellValue("이름");
            h.createCell(2).setCellValue("값");
            s.createRow(2);
            s.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 2, 0, 1));
            try (var os = Files.newOutputStream(tpl)) {
                wb.write(os);
            }
        }
        Mapping.DocMapping dm = new Mapping.DocMapping("merge.xlsx", "S", 2, 3, Map.of("이름", "nm", "값", "v"));
        Path out = tmp.resolve("merge-out.xlsx");
        XlsxFiller.fill(tpl, dm, new Doc("X", "x", List.of("nm", "v"), List.of(List.of("a", 1), List.of("b", 2), List.of("c", 3)), null, null), out);
        try (InputStream in = Files.newInputStream(out); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            List<String> merged = wb.getSheetAt(0).getMergedRegions().stream().map(r -> r.formatAsString()).sorted().toList();
            assertEquals(List.of("A3:B3", "A4:B4", "A5:B5"), merged);
        }
        // 마무리 리뷰 I5 — 병합 잇기는 행 수에 비례(addMergedRegion 은 부를 때마다 기존 병합 전부와 겹침을 잰다 — 8천 행 7초)
        List<List<Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 8000; i++) {
            rows.add(List.of("n" + i, i));
        }
        long t0 = System.nanoTime();
        XlsxFiller.fill(tpl, dm, new Doc("X", "x", List.of("nm", "v"), rows, null, null), tmp.resolve("merge-big.xlsx"));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms < 4000, "8천 행 병합 잇기 " + ms + "ms");
    }

    @Test
    void mappingTyposAreErrors() throws Exception {
        Mapping.DocMapping base = Mapping.load(MAPPING).of("02");
        Doc d02 = doc(pgDocs(), "02");
        Map<String, String> badForm = new LinkedHashMap<>(base.columns());
        badForm.put("테이블명(영어)", "테이블명(영문)");
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class, () -> XlsxFiller.fill(TEMPLATES.resolve(base.file()),
                new Mapping.DocMapping(base.file(), base.sheet(), 2, 3, badForm), d02, tmp.resolve("a.xlsx")));
        assertTrue(e1.getMessage().contains("양식 머리에 「테이블명(영어)」"), e1.getMessage());
        Map<String, String> badField = new LinkedHashMap<>(base.columns());
        badField.put("순번", "번호");
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class, () -> XlsxFiller.fill(TEMPLATES.resolve(base.file()),
                new Mapping.DocMapping(base.file(), base.sheet(), 2, 3, badField), d02, tmp.resolve("b.xlsx")));
        assertTrue(e2.getMessage().contains("「번호」 열이 없다"), e2.getMessage());
        assertThrows(IllegalArgumentException.class, () -> XlsxFiller.fill(TEMPLATES.resolve(base.file()),
                new Mapping.DocMapping(base.file(), base.sheet(), 1, 3, base.columns()), d02, tmp.resolve("c.xlsx")), "머리 행을 틀리게");
        assertThrows(IllegalArgumentException.class, () -> new Mapping.DocMapping("f", null, 3, 3, Map.of()), "firstRow > headerRow");
        // B-24 — columns 값이 빈 열쇠는 NPE 대신 사유
        Exception e = assertThrows(Exception.class, () -> Mapping.parse("docs:\n  '01':\n    file: f.xlsx\n    headerRow: 2\n    firstRow: 3\n    columns:\n      이름:\n"));
        StringBuilder chain = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            chain.append(t.getMessage()).append(" | ");
        }
        assertTrue(chain.toString().contains("columns 의 값이 빈 열쇠") && chain.toString().contains("이름"), chain.toString());
    }

    @Test
    void committedTemplatesAndMappingMatchValueColumns() throws Exception {
        Mapping m = Mapping.load(MAPPING);
        assertEquals(Mapping.exampleYaml(Forms.all(), 2, 3), Files.readString(MAPPING).replace("\r\n", "\n"),
                "값 표 열을 바꿨으면 scripts/make-example-templates.sh 를 돌려 커밋한다");
        for (Form f : Forms.all()) {
            List<List<Object>> rows = read(TEMPLATES.resolve(f.file()));
            assertEquals(new ArrayList<Object>(f.columns()), rows.get(1), f.file() + " 머리 행");
            assertEquals(f.file(), m.of(f.no()).file());
        }
    }

    /** 데이터 앞 행들에서 열 이름의 위치 — 열 순서가 표준으로 바뀌어도 단언이 안 흔들린다(2-10) */
    static int headerCol(Sheet s, String name) {
        for (int r = 0; r < 4; r++) {
            org.apache.poi.ss.usermodel.Row row = s.getRow(r);
            if (row == null) {
                continue;
            }
            for (Cell c : row) {
                if (c.getCellType() == CellType.STRING && name.equals(c.getStringCellValue())) {
                    return c.getColumnIndex();
                }
            }
        }
        return -1;
    }
}
