package kr.ejg.toolbox.core.report;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kr.ejg.toolbox.core.sqlrun.ResultTable;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * 결과 표 → xlsx(POI 스트리밍 — 행이 많아도 메모리가 일정) · csv(UTF-8 BOM — 엑셀이 한글을 바로 읽는다). 1-7.
 */
public final class XlsxWriter {

    /** UTF-8 BOM — 엑셀이 csv 를 UTF-8 로 읽게 */
    static final char BOM = (char) 0xFEFF;

    private XlsxWriter() {
    }

    public static void write(ResultTable t, Path file) throws IOException {
        java.util.LinkedHashMap<String, ResultTable> one = new java.util.LinkedHashMap<>();
        one.put("결과", t);
        write(one, file);
    }

    /** 시트 여럿 — 이름 → 표, 넣은 순서대로(2-13 작성안내) */
    public static void write(java.util.LinkedHashMap<String, ResultTable> sheets, Path file) throws IOException {
        mkdirs(file);
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200); OutputStream out = Files.newOutputStream(file)) {
            CellStyle head = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            head.setFont(bold);
            for (java.util.Map.Entry<String, ResultTable> e : sheets.entrySet()) {
                sheet(wb.createSheet(e.getKey()), e.getValue(), head);
            }
            wb.write(out);
            wb.dispose();
        }
    }

    private static void sheet(Sheet sheet, ResultTable t, CellStyle head) {
        Row h = sheet.createRow(0);
        for (int i = 0; i < t.columns().size(); i++) {
            Cell c = h.createCell(i);
            c.setCellValue(t.columns().get(i).name());
            c.setCellStyle(head);
        }
        int r = 1;
        for (List<Object> row : t.rows()) {
            Row x = sheet.createRow(r++);
            for (int i = 0; i < row.size(); i++) {
                Object v = row.get(i);
                if (v == null) {
                    continue; // NULL 은 셀을 안 만든다
                }
                Cell c = x.createCell(i);
                if (v instanceof Number n) {
                    c.setCellValue(n.doubleValue());
                } else if (v instanceof Boolean b) {
                    c.setCellValue(b);
                } else {
                    c.setCellValue(v.toString());
                }
            }
        }
        if (t.truncated()) {
            sheet.createRow(r).createCell(0).setCellValue("… 최대 행수(" + t.rows().size() + ")에서 잘렸다");
        }
    }

    public static void writeCsv(ResultTable t, Path file) throws IOException {
        mkdirs(file);
        StringBuilder sb = new StringBuilder().append(BOM);
        sb.append(String.join(",", t.columns().stream().map(c -> csv(c.name())).toList())).append("\r\n");
        for (List<Object> row : t.rows()) {
            sb.append(String.join(",", row.stream().map(v -> v == null ? "" : csv(v.toString())).toList())).append("\r\n");
        }
        Files.writeString(file, sb, StandardCharsets.UTF_8);
    }

    private static void mkdirs(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    static String csv(String s) {
        return s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")
                ? "\"" + s.replace("\"", "\"\"") + "\""
                : s;
    }
}
