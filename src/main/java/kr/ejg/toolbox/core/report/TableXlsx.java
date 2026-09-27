package kr.ejg.toolbox.core.report;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * table_builder 표 → xlsx(4-6, 2.6). 모델은 순수본 {@code G} 그대로 —
 * {@code {rows, cols, grid[r][c] = {t: th|td, cs, rs, text, align?, cover?}, theadRows, caption, colWidths[]}}.
 * <ul>
 *   <li>병합: cs·rs → {@link CellRangeAddress}. 덮인 칸({@code cover})도 테두리를 칠해 병합 테두리가 이어진다</li>
 *   <li>th 는 굵게·회색({@link XlsxFiller#writeTemplate} 머리와 같은 결), 모든 칸 테두리·줄바꿈</li>
 *   <li>caption 이 있으면 1행 제목(표 너비만큼 병합), 표는 2행부터</li>
 *   <li>글: 태그를 벗기고 {@code <br>} 는 줄바꿈, 엔티티 다섯과 {@code &nbsp;} 를 푼다</li>
 *   <li>열 너비: {@code 120px} → 글자 수 근사(px/7), {@code 20%} → 100자 기준 비율, 없으면 글 길이(한글 2칸)로</li>
 * </ul>
 */
public final class TableXlsx {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Slot(String t, Integer cs, Integer rs, String text, String align, List<Integer> cover) {
        public Slot {
            cover = cover == null ? null : List.copyOf(cover);
        }

        int colSpan() {
            return cs == null || cs < 1 ? 1 : cs;
        }

        int rowSpan() {
            return rs == null || rs < 1 ? 1 : rs;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TableModel(int rows, int cols, List<List<Slot>> grid, int theadRows, String caption, List<String> colWidths) {
        public TableModel {
            // List.copyOf 는 null 을 못 담는다 — 빈 칸은 빈 td, 빈 너비는 ""
            grid = grid == null ? List.of() : List.copyOf(grid.stream()
                    .map(r -> r == null ? List.<Slot>of() : List.copyOf(r.stream()
                            .map(x -> x == null ? new Slot("td", 1, 1, "", null, null) : x).toList()))
                    .toList());
            colWidths = colWidths == null ? List.of() : List.copyOf(colWidths.stream().map(w -> w == null ? "" : w).toList());
        }
    }

    public static final int MAX_ROWS = 2000;
    public static final int MAX_COLS = 200;
    private static final Pattern BR = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern PX = Pattern.compile("\\s*(\\d+(?:\\.\\d+)?)\\s*(px)?\\s*");
    private static final Pattern PCT = Pattern.compile("\\s*(\\d+(?:\\.\\d+)?)\\s*%\\s*");

    private TableXlsx() {
    }

    public static void write(TableModel m, Path out) throws IOException {
        if (m.rows() < 1 || m.cols() < 1 || m.rows() > MAX_ROWS || m.cols() > MAX_COLS || m.grid().size() < m.rows()) {
            throw new IllegalArgumentException("표 크기가 맞지 않다: " + m.rows() + "×" + m.cols());
        }
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("표");
            Map<String, CellStyle> styles = new HashMap<>();
            String caption = plain(m.caption()).trim();
            int off = 0;
            if (!caption.isEmpty()) {
                CellStyle title = wb.createCellStyle();
                Font big = wb.createFont();
                big.setBold(true);
                big.setFontHeightInPoints((short) 14);
                title.setFont(big);
                Cell t = s.createRow(0).createCell(0);
                t.setCellValue(caption);
                t.setCellStyle(title);
                if (m.cols() > 1) {
                    s.addMergedRegion(new CellRangeAddress(0, 0, 0, m.cols() - 1));
                }
                off = 1;
            }
            int[] textWidth = new int[m.cols()];
            for (int r = 0; r < m.rows(); r++) {
                Row row = s.createRow(r + off);
                List<Slot> line = m.grid().get(r);
                for (int c = 0; c < m.cols(); c++) {
                    Slot slot = c < line.size() ? line.get(c) : null;
                    Cell cell = row.createCell(c);
                    if (slot == null || slot.cover() != null) {
                        Slot owner = owner(m, slot);
                        cell.setCellStyle(style(wb, styles, owner == null ? "td" : owner.t(), owner == null ? null : owner.align()));
                        continue;
                    }
                    String text = plain(slot.text());
                    cell.setCellValue(text);
                    cell.setCellStyle(style(wb, styles, slot.t(), slot.align()));
                    int cs = Math.min(slot.colSpan(), m.cols() - c);
                    int rs = Math.min(slot.rowSpan(), m.rows() - r);
                    if (cs > 1 || rs > 1) {
                        s.addMergedRegion(new CellRangeAddress(r + off, r + off + rs - 1, c, c + cs - 1));
                    } else {
                        textWidth[c] = Math.max(textWidth[c], width(text));
                    }
                }
            }
            for (int c = 0; c < m.cols(); c++) {
                String w = c < m.colWidths().size() ? m.colWidths().get(c) : "";
                s.setColumnWidth(c, Math.min(80, Math.max(6, chars(w, textWidth[c]))) * 256);
            }
            if (m.theadRows() > 0 && m.theadRows() < m.rows()) {
                s.createFreezePane(0, m.theadRows() + off);
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

    /** 덮인 칸의 주인 — 스타일(th·td·정렬)을 이어받는다 */
    private static Slot owner(TableModel m, Slot covered) {
        if (covered == null || covered.cover() == null || covered.cover().size() < 2) {
            return null;
        }
        int r = covered.cover().get(0);
        int c = covered.cover().get(1);
        if (r < 0 || r >= m.grid().size() || c < 0 || c >= m.grid().get(r).size()) {
            return null;
        }
        return m.grid().get(r).get(c);
    }

    private static CellStyle style(Workbook wb, Map<String, CellStyle> cache, String t, String align) {
        boolean head = "th".equals(t);
        String a = align == null ? "left" : align;
        return cache.computeIfAbsent((head ? "th:" : "td:") + a, k -> {
            CellStyle st = wb.createCellStyle();
            st.setBorderTop(BorderStyle.THIN);
            st.setBorderBottom(BorderStyle.THIN);
            st.setBorderLeft(BorderStyle.THIN);
            st.setBorderRight(BorderStyle.THIN);
            st.setWrapText(true);
            st.setVerticalAlignment(VerticalAlignment.CENTER);
            st.setAlignment(switch (a) {
                case "center" -> HorizontalAlignment.CENTER;
                case "right" -> HorizontalAlignment.RIGHT;
                default -> HorizontalAlignment.LEFT;
            });
            if (head) {
                Font bold = wb.createFont();
                bold.setBold(true);
                st.setFont(bold);
                st.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return st;
        });
    }

    /** 셀 HTML → 글. 태그를 벗기고 엔티티를 푼다({@code &amp;} 는 마지막 — 두 번 풀지 않게) */
    static String plain(String html) {
        if (html == null) {
            return "";
        }
        String s = BR.matcher(html).replaceAll("\n");
        s = TAG.matcher(s).replaceAll("");
        return s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&amp;", "&");
    }

    /** 가장 긴 줄의 칸 수 — 한글 등 넓은 글자는 2 */
    static int width(String text) {
        int best = 0;
        for (String line : text.split("\n", -1)) {
            int w = 0;
            for (int i = 0; i < line.length(); i++) {
                w += line.charAt(i) > 0x2E80 ? 2 : 1;
            }
            best = Math.max(best, w);
        }
        return best;
    }

    static int chars(String w, int textWidth) {
        var px = PX.matcher(w);
        if (px.matches()) {
            return (int) Math.round(Double.parseDouble(px.group(1)) / 7);
        }
        var pct = PCT.matcher(w);
        if (pct.matches()) {
            return (int) Math.round(Double.parseDouble(pct.group(1)));
        }
        return textWidth + 2;
    }
}
