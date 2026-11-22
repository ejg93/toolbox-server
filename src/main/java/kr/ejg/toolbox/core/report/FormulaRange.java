package kr.ejg.toolbox.core.report;

import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.formula.FormulaParser;
import org.apache.poi.ss.formula.FormulaRenderer;
import org.apache.poi.ss.formula.FormulaType;
import org.apache.poi.ss.formula.ptg.AreaPtg;
import org.apache.poi.ss.formula.ptg.Ptg;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFEvaluationWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * B-01 — 데이터 행 아래로 밀린 수식의 범위가 데이터 첫 행에서 끝나면(SUM(B3:B3)) 채운 끝 행까지 넓힌다.
 * 같은 시트 범위만. 단일 칸 참조(SUM(B3))·다른 시트 참조·POI 가 못 읽는 수식은 그대로 둔다 — 양식은 범위 꼴로 쓴다
 */
final class FormulaRange {

    /** widened 결과 — 다중 칸 배열 수식이라 그대로 둔 칸(0 그대로 · 1 넓힘) */
    private static final int KEPT = 2;

    private FormulaRange() {
    }

    /**
     * 2-52 — 데이터 n 행을 채우기 전: 아래 행을 밀고(n &gt; 1) 범위를 넓히고(n &gt; 1) 수식 칸이 하나라도 있으면 다시 계산 표시(B-01 — 안 넓힌 수식·
     * 한 행 SUM(B3:B3) 도 양식의 낡은 캐시 값을 달고 나갔다). @return 못 넓힌 배열 수식 수
     */
    static int grow(XSSFWorkbook wb, Sheet sheet, int first, int n) {
        if (n > 1 && sheet.getLastRowNum() > first) {
            sheet.shiftRows(first + 1, sheet.getLastRowNum(), n - 1); // 양식 아래쪽(합계·서명란 등)을 밀어낸다
        }
        int kept = n > 1 ? widen(wb, sheet, first, n) : 0;
        if (hasFormula(sheet)) {
            sheet.setForceFormulaRecalculation(true);
        }
        return kept;
    }

    private static boolean hasFormula(Sheet sheet) {
        for (Row row : sheet) {
            for (Cell c : row) {
                if (c.getCellType() == CellType.FORMULA) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 데이터 행(first ~ first + n - 1) 밖 전부를 훑는다 — 위쪽 제목부 「총 n건」(B-02)도. @return 범위는 맞았는데 다중 칸 배열 수식이라 못 넓힌 수식 수(묶음 하나를 하나로, 2-47) */
    static int widen(XSSFWorkbook wb, Sheet sheet, int first, int n) {
        XSSFEvaluationWorkbook ew = XSSFEvaluationWorkbook.create(wb);
        int si = wb.getSheetIndex(sheet);
        java.util.Set<String> kept = new java.util.HashSet<>();
        for (Row row : sheet) {
            int r = row.getRowNum();
            if (r >= first && r < first + n) {
                continue;
            }
            for (Cell c : row) {
                if (c.getCellType() == CellType.FORMULA && widened(c, ew, si, r, first, n) == KEPT) {
                    kept.add(c.getArrayFormulaRange().formatAsString());
                }
            }
        }
        return kept.size();
    }

    /** 0 — 해당 없음 · 1 — 넓힘 · 2 — 범위는 맞았는데 배열 수식이라 그대로 둠 */
    private static int widened(Cell c, XSSFEvaluationWorkbook ew, int si, int r, int first, int n) {
        Ptg[] ptgs;
        try {
            ptgs = FormulaParser.parse(c.getCellFormula(), ew, FormulaType.CELL, si, r);
        } catch (FormulaParseException e) {
            return 0; // 못 읽는 수식은 그대로
        }
        boolean hit = false;
        for (Ptg p : ptgs) {
            if (p instanceof AreaPtg a && a.getLastRow() == first && a.getFirstRow() <= first) {
                a.setLastRow(first + n - 1);
                hit = true;
            }
        }
        if (!hit) {
            return 0;
        }
        String text = FormulaRenderer.toFormulaString(ew, ptgs);
        if (c.isPartOfArrayFormulaGroup()) {
            org.apache.poi.ss.util.CellRangeAddress range = c.getArrayFormulaRange();
            if (range.getNumberOfCells() > 1) {
                return KEPT; // 2-44 — 다중 칸 배열 수식의 한 칸은 POI 가 못 바꾼다. 그 수식은 그대로 둔다(2-47 — 알린다)
            }
            c.getSheet().removeArrayFormula(c); // 2-52 B-04 — 단일 칸 배열은 배열로 다시 넣는다(setCellFormula 는 배열 표시를 지워 #VALUE!)
            c.getSheet().setArrayFormula(text, range);
            return 1;
        }
        try {
            c.setCellFormula(text);
        } catch (IllegalStateException e) {
            return KEPT;
        }
        return 1;
    }
}
