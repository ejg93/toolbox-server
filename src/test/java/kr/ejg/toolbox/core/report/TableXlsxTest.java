package kr.ejg.toolbox.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.GoldenFiles;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 4-6 — 모델 픽스처 둘을 xlsx 로 쓰고 POI 로 다시 읽어 셀·병합·너비를 골든과 대조 */
class TableXlsxTest {

    static final ObjectMapper JSON = new ObjectMapper();

    /** 병합 — 가로 2칸 th, 세로 2칸 td, 정렬·태그·엔티티, caption, 너비 px·%·빈칸. 순수본 저장본에 있는 모르는 필드(cls)도 받는다 */
    static final String MERGE = """
            {"rows":3,"cols":3,"theadRows":1,"caption":"<b>사용자</b> 목록","colWidths":["140px","20%",""],
             "grid":[
              [{"t":"th","cs":2,"rs":1,"text":"이름 &amp; 구분"},{"cover":[0,0]},{"t":"th","cs":1,"rs":1,"text":"비고","align":"center"}],
              [{"t":"td","cs":1,"rs":2,"text":"관리자","cls":"x"},{"t":"td","cs":1,"rs":1,"text":"A<br>B"},{"t":"td","cs":1,"rs":1,"text":"&lt;없음&gt;&nbsp;끝","align":"right"}],
              [{"cover":[1,0]},{"t":"td","cs":1,"rs":1,"text":"C"},{"t":"td","cs":1,"rs":1,"text":""}]
             ]}
            """;

    /** 머리 2행 — 1행은 세로 병합 th 와 가로 병합 th, 2행은 그 아래 th 둘. caption 없음 */
    static final String HEAD2 = """
            {"rows":3,"cols":3,"theadRows":2,"caption":"","colWidths":["","",""],
             "grid":[
              [{"t":"th","cs":1,"rs":2,"text":"항목"},{"t":"th","cs":2,"rs":1,"text":"값"},{"cover":[0,1]}],
              [{"cover":[0,0]},{"t":"th","cs":1,"rs":1,"text":"전"},{"t":"th","cs":1,"rs":1,"text":"후"}],
              [{"t":"td","cs":1,"rs":1,"text":"길이가 조금 긴 항목 이름"},{"t":"td","cs":1,"rs":1,"text":"1"},{"t":"td","cs":1,"rs":1,"text":"2"}]
             ]}
            """;

    @TempDir
    Path tmp;

    @Test
    void mergeCaptionAlignAndText() throws IOException {
        GoldenFiles.assertJson("table/merge.json", roundTrip(MERGE));
    }

    @Test
    void twoHeadRows() throws IOException {
        GoldenFiles.assertJson("table/head2.json", roundTrip(HEAD2));
    }

    @Test
    void brokenModelRefused() {
        TableXlsx.TableModel tooFew = new TableXlsx.TableModel(3, 2, List.of(), 1, "", List.of());
        assertThrows(IllegalArgumentException.class, () -> TableXlsx.write(tooFew, tmp.resolve("x.xlsx")));
    }

    Map<String, Object> roundTrip(String model) throws IOException {
        Path f = tmp.resolve("t.xlsx");
        TableXlsx.write(JSON.readValue(model, TableXlsx.TableModel.class), f);
        Map<String, Object> out = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(f); Workbook wb = new XSSFWorkbook(in)) {
            Sheet s = wb.getSheetAt(0);
            List<String> cells = new ArrayList<>();
            for (Row r : s) {
                for (Cell c : r) {
                    CellStyle st = c.getCellStyle();
                    boolean bold = wb.getFontAt(st.getFontIndex()).getBold();
                    cells.add(new CellReference(c).formatAsString() + " " + (bold ? "B" : "-")
                            + (st.getFillForegroundColor() == org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex() ? "G" : "-")
                            + (st.getBorderTop() == org.apache.poi.ss.usermodel.BorderStyle.THIN ? "T" : "-")
                            + " " + st.getAlignment() + " " + c.getStringCellValue().replace("\n", "\\n"));
                }
            }
            List<String> merges = new ArrayList<>();
            for (CellRangeAddress a : s.getMergedRegions()) {
                merges.add(a.formatAsString());
            }
            merges.sort(null);
            List<Integer> widths = new ArrayList<>();
            int cols = s.getRow(s.getLastRowNum()).getLastCellNum();
            for (int c = 0; c < cols; c++) {
                widths.add(s.getColumnWidth(c) / 256);
            }
            out.put("sheet", s.getSheetName());
            out.put("cells", cells);
            out.put("merges", merges);
            out.put("widths", widths);
            out.put("freezeRow", s.getPaneInformation() == null ? null : (int) s.getPaneInformation().getHorizontalSplitPosition());
        }
        return out;
    }
}
