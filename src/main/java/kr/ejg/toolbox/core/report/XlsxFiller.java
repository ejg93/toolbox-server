package kr.ejg.toolbox.core.report;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.deliverable.Doc;
import kr.ejg.toolbox.core.deliverable.Forms;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 양식 xlsx 에 값 표를 기입한다(2-4, 5-7). 양식을 템플릿으로 열어 {@code firstRow} 부터 행을 채우고, 셀 모양은 양식의
 * {@code firstRow} 행 것을 그대로 쓴다. 시트 이름·제목·머리 행은 안 건드린다. 수는 수 셀, 나머지는 글 셀.
 */
public final class XlsxFiller {

    private XlsxFiller() {
    }

    public static void fill(Path template, Mapping.DocMapping m, Doc d, Path out) throws IOException {
        try (InputStream in = Files.newInputStream(template); Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = m.sheet() == null || m.sheet().isBlank() ? wb.getSheetAt(0) : wb.getSheet(m.sheet());
            if (sheet == null) {
                throw new IllegalArgumentException(m.file() + " 에 시트 「" + m.sheet() + "」 가 없다");
            }
            Map<String, Integer> header = header(sheet, m.headerRow() - 1);
            Mapping.check(m, header, d);
            int first = m.firstRow() - 1;
            Row styleRow = sheet.getRow(first);
            Map<Integer, CellStyle> styles = new HashMap<>();
            if (styleRow != null) {
                styleRow.forEach(c -> styles.put(c.getColumnIndex(), c.getCellStyle()));
            }
            ColumnWidths widths = new ColumnWidths(header.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1);
            for (Map.Entry<String, String> e : m.columns().entrySet()) { // 1-33 — 양식 머리·넣을 값의 폭. 넓히기만
                int col = header.get(e.getKey());
                widths.see(col, e.getKey());
                int idx = d.columns().indexOf(e.getValue());
                for (List<Object> values : d.rows()) {
                    Object v = values.get(idx);
                    if (v != null) {
                        widths.see(col, String.valueOf(v));
                    }
                }
            }
            Map<Integer, CellStyle> wrapped = new HashMap<>();
            int n = d.rows().size();
            if (n > 1 && sheet.getLastRowNum() > first) {
                sheet.shiftRows(first + 1, sheet.getLastRowNum(), n - 1); // 양식 아래쪽(합계·서명란 등)을 밀어낸다
            }
            for (int i = 0; i < n; i++) {
                List<Object> values = d.rows().get(i);
                Row row = sheet.getRow(first + i) != null ? sheet.getRow(first + i) : sheet.createRow(first + i);
                for (Map.Entry<String, String> e : m.columns().entrySet()) {
                    int col = header.get(e.getKey());
                    Object v = values.get(d.columns().indexOf(e.getValue()));
                    Cell cell = row.getCell(col) != null ? row.getCell(col) : row.createCell(col);
                    CellStyle st = styles.get(col);
                    if (widths.wraps(col)) {
                        st = wrapped.computeIfAbsent(col, k -> {
                            CellStyle ws = wb.createCellStyle();
                            if (styles.get(k) != null) {
                                ws.cloneStyleFrom(styles.get(k));
                            }
                            ws.setWrapText(true);
                            return ws;
                        });
                    }
                    if (st != null) {
                        cell.setCellStyle(st);
                    }
                    if (v instanceof Number num) {
                        cell.setCellValue(num.doubleValue());
                    } else if (v != null && !String.valueOf(v).isEmpty()) {
                        cell.setCellValue(String.valueOf(v));
                    } else {
                        cell.setBlank(); // 빈칸도 모양(테두리)은 남긴다
                    }
                }
            }
            for (String formCol : m.columns().keySet()) {
                widths.applyFrom(sheet, header.get(formCol));
            }
            Path dir = out.toAbsolutePath().getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            try (OutputStream os = Files.newOutputStream(out)) {
                wb.write(os);
            }
        }
    }

    /** 머리 행 글자 → 열 번호. 빈 칸은 뺀다 */
    static Map<String, Integer> header(Sheet sheet, int rowIndex) {
        Map<String, Integer> out = new HashMap<>();
        Row r = sheet.getRow(rowIndex);
        if (r == null) {
            return out;
        }
        DataFormatter f = new DataFormatter();
        r.forEach(c -> {
            String t = f.formatCellValue(c).trim();
            if (!t.isEmpty()) {
                out.put(t, c.getColumnIndex());
            }
        });
        return out;
    }

    /** 예시 양식 한 장 — 1행 제목, 2행 머리(굵게·배경), 3행 빈 데이터 행(테두리만 — 기입 때 이 모양을 복사한다) */
    public static void writeTemplate(Forms.Form f, Path out) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(f.name());
            CellStyle title = wb.createCellStyle();
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);
            title.setFont(big);
            CellStyle head = headStyle(wb);
            CellStyle body = bordered(wb);
            Row r0 = s.createRow(0);
            Cell t = r0.createCell(0);
            t.setCellValue(f.name());
            t.setCellStyle(title);
            Row r1 = s.createRow(1);
            Row r2 = s.createRow(2);
            for (int i = 0; i < f.columns().size(); i++) {
                Cell h = r1.createCell(i);
                h.setCellValue(f.columns().get(i));
                h.setCellStyle(head);
                r2.createCell(i).setCellStyle(body);
                s.setColumnWidth(i, Math.min(60, Math.max(8, f.columns().get(i).length() * 2 + 4)) * 256);
            }
            s.createFreezePane(0, 2);
            Path dir = out.toAbsolutePath().getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            try (OutputStream os = Files.newOutputStream(out)) {
                wb.write(os);
            }
        }
    }

    /** 머리 칸 — 테두리 + 굵게 + 회색. 예시 양식과 table_builder xlsx(TableXlsx)가 같이 쓴다 */
    static CellStyle headStyle(Workbook wb) {
        CellStyle c = bordered(wb);
        Font bold = wb.createFont();
        bold.setBold(true);
        c.setFont(bold);
        c.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        c.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return c;
    }

    /** 가는 테두리 넷 */
    static CellStyle bordered(Workbook wb) {
        CellStyle c = wb.createCellStyle();
        c.setBorderTop(BorderStyle.THIN);
        c.setBorderBottom(BorderStyle.THIN);
        c.setBorderLeft(BorderStyle.THIN);
        c.setBorderRight(BorderStyle.THIN);
        return c;
    }
}
