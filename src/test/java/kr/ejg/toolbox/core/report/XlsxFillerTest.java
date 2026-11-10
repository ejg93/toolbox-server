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
