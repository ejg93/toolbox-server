package kr.ejg.toolbox.core.report;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 양식 xlsx 에 값 표를 기입한다(2-4, 5-7). 양식을 템플릿으로 열어 {@code firstRow} 부터 행을 채우고, 셀 모양은 양식의
 * {@code firstRow} 행 것을 그대로 쓴다. 시트 이름·제목·머리 행은 안 건드린다. 수는 수 셀, 나머지는 글 셀.
 */
public final class XlsxFiller {

    /** xlsx(zip) 의 첫 네 바이트 */
    private static final byte[] ZIP = {'P', 'K', 3, 4};

    private XlsxFiller() {
    }

    /** 디스크 양식 — 지정한 폴더의 파일. xlsx 가 아니면 POI 에 넘기기 전에 읽을 수 있는 사유로 멈춘다 */
    /** @return 알림 글(안 넓힌 배열 수식 합계 등, 2-47) — 없으면 빈 목록 */
    public static List<String> fill(Path template, Mapping.DocMapping m, Doc d, Path out) throws IOException {
        requireXlsx(template);
        try (InputStream in = Files.newInputStream(template)) {
            return fill(in, m, d, out);
        }
    }

    /** 2-56 — 쓰기 전 점검: 디스크 양식을 열어 fill 과 같은 xlsx·시트·머리 검사만 하고 닫는다(같은 예외 글) */
    public static void check(Path template, Mapping.DocMapping m, Doc d) throws IOException {
        requireXlsx(template);
        try (InputStream in = Files.newInputStream(template); Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = m.sheet() == null || m.sheet().isBlank() ? wb.getSheetAt(0) : wb.getSheet(m.sheet());
            if (sheet == null) {
                throw new IllegalArgumentException(m.file() + " 에 시트 「" + m.sheet() + "」 가 없다");
            }
            Mapping.check(m, header(sheet, m.headerRow() - 1, new ArrayList<>()), d);
        }
    }

    /** 내장 양식(설계 61) — writeTemplate 과 같은 바이트를 메모리에 만들어 연다. 디스크 xlsx 를 안 읽는다 */
    public static List<String> fill(Form builtIn, Mapping.DocMapping m, Doc d, Path out) throws IOException {
        return fill(new ByteArrayInputStream(templateBytes(builtIn)), m, d, out);
    }

    /** 문서보안(DRM)이 암호화한 파일은 zip 이 아니다 — 사유에는 경로만 싣는다 */
    private static void requireXlsx(Path template) throws IOException {
        if (!Files.isRegularFile(template)) {
            throw new IllegalArgumentException("양식 파일이 없다: " + template);
        }
        byte[] head;
        try (InputStream in = Files.newInputStream(template)) {
            head = in.readNBytes(ZIP.length);
        }
        if (!java.util.Arrays.equals(head, ZIP)) {
            throw new IllegalArgumentException("양식이 xlsx 가 아니다: " + template + " — 문서보안(DRM) 암호화 여부를 확인한다");
        }
    }

    private static List<String> fill(InputStream in, Mapping.DocMapping m, Doc d, Path out) throws IOException {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = m.sheet() == null || m.sheet().isBlank() ? wb.getSheetAt(0) : wb.getSheet(m.sheet());
            if (sheet == null) {
                throw new IllegalArgumentException(m.file() + " 에 시트 「" + m.sheet() + "」 가 없다");
            }
            List<String> dup = new ArrayList<>(); // B-17 — 양식에 둘 이상인 머리 글
            Map<String, Integer> header = header(sheet, m.headerRow() - 1, dup);
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
            Map<Integer, CellStyle> formatted = new HashMap<>(); // 2-21 — 수 셀의 엑셀 표시 형식(열마다 하나)
            int n = d.rows().size();
            int kept = FormulaRange.grow((XSSFWorkbook) wb, sheet, first, n); // 2-52 — 밀기·넓히기·다시 계산 표시
            Set<String> clipped = new LinkedHashSet<>(); // 2-53 — 32,767자를 넘어 자른 열 머리
            repeatMerges(sheet, first, n);
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
                    String fmt = d.formats().get(e.getValue());
                    if (fmt != null && v instanceof Number) {
                        CellStyle base = st;
                        st = formatted.computeIfAbsent(col, k -> {
                            CellStyle fs = wb.createCellStyle();
                            if (base != null) {
                                fs.cloneStyleFrom(base);
                            }
                            fs.setDataFormat(wb.createDataFormat().getFormat(fmt));
                            return fs;
                        });
                    }
                    if (st != null) {
                        cell.setCellStyle(st);
                    }
                    if (v instanceof Number num) {
                        cell.setCellValue(num.doubleValue());
                    } else if (v != null && !String.valueOf(v).isEmpty()) {
                        cell.setCellValue(clip(String.valueOf(v), clipped, e.getKey()));
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
            List<String> said = notes(kept, dup, m);
            clipped.forEach(c -> said.add(c + " 칸이 32,767자를 넘어 잘랐다"));
            return said;
        }
    }

    /** 2-53(B-14) — 엑셀 칸 상한(32,767자)을 넘는 글은 그 길이로 자르고 열 머리를 clipped 에 */
    private static String clip(String v, Set<String> clipped, String col) {
        int max = SpreadsheetVersion.EXCEL2007.getMaxTextLength();
        if (v.length() <= max) {
            return v;
        }
        clipped.add(col);
        return v.substring(0, max);
    }

    /** 2-47·B-17 — 채우기 알림 글 — 안 넓힌 배열 수식 · 매핑이 쓰는 머리 가운데 양식에 둘 이상인 것 */
    private static List<String> notes(int kept, List<String> dup, Mapping.DocMapping m) {
        List<String> out = new ArrayList<>();
        if (kept > 0) {
            out.add("배열 수식 합계 " + kept + "개는 범위를 안 넓혔다 — 손으로 고친다");
        }
        dup.stream().distinct().filter(m.columns()::containsKey).forEach(d -> out.add("머리 「" + d + "」 가 양식에 둘 이상 — 맨 뒤 칸에 채웠다"));
        return out;
    }

    /** 데이터 첫 행 안의 병합을 채운 행마다 잇는다(B-23) — n 이 1 이하면 아무것도 안 한다. 아래로 민 뒤에 부른다 */
    private static void repeatMerges(Sheet sheet, int first, int n) {
        List<CellRangeAddress> own = sheet.getMergedRegions().stream().filter(r -> r.getFirstRow() == first && r.getLastRow() == first).toList();
        for (int i = 1; i < n; i++) {
            for (CellRangeAddress r : own) {
                sheet.addMergedRegionUnsafe(new CellRangeAddress(first + i, first + i, r.getFirstColumn(), r.getLastColumn())); // 민 뒤 새 행 — 겹칠 수 없다(마무리 리뷰 I5)
            }
        }
    }

    /** 머리 행 글자 → 열 번호. 빈 칸은 뺀다 */
    static Map<String, Integer> header(Sheet sheet, int rowIndex) {
        return header(sheet, rowIndex, new ArrayList<>());
    }

    /** dup — 같은 글을 다시 만나면 그 글을 더한다(뒤 칸이 이긴다, B-17) */
    static Map<String, Integer> header(Sheet sheet, int rowIndex, List<String> dup) {
        Map<String, Integer> out = new HashMap<>();
        Row r = sheet.getRow(rowIndex);
        if (r == null) {
            return out;
        }
        DataFormatter f = new DataFormatter();
        r.forEach(c -> {
            String t = f.formatCellValue(c).trim();
            if (!t.isEmpty()) {
                if (out.containsKey(t)) {
                    dup.add(t);
                }
                out.put(t, c.getColumnIndex());
            }
        });
        return out;
    }

    /** 예시 양식 한 장 — 1행 제목, 2행 머리(굵게·배경), 3행 빈 데이터 행(테두리만 — 기입 때 이 모양을 복사한다) */
    public static void writeTemplate(Form f, Path out) throws IOException {
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        Files.write(out, templateBytes(f));
    }

    /** 예시 양식 한 장의 바이트 — writeTemplate 과 내장 양식 기입이 같이 쓴다 */
    static byte[] templateBytes(Form f) throws IOException {
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
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            wb.write(os);
            return os.toByteArray();
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
