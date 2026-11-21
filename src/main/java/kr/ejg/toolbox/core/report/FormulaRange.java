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

    /** @return 범위는 맞았는데 배열 수식이라 못 넓힌 수식 수(배열 묶음 하나를 하나로 센다, 2-47) */
    static int widen(XSSFWorkbook wb, Sheet sheet, int first, int n) {
        XSSFEvaluationWorkbook ew = XSSFEvaluationWorkbook.create(wb);
        int si = wb.getSheetIndex(sheet);
        boolean any = false;
        java.util.Set<String> kept = new java.util.HashSet<>();
        for (int r = first + n; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (Cell c : row) {
                int w = c.getCellType() == CellType.FORMULA ? widened(c, ew, si, r, first, n) : 0;
                any |= w == 1;
                if (w == KEPT) {
                    kept.add(c.getArrayFormulaRange().formatAsString());
                }
            }
        }
        if (any) {
            sheet.setForceFormulaRecalculation(true);
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
        if (hit) {
            try {
                c.setCellFormula(FormulaRenderer.toFormulaString(ew, ptgs));
            } catch (IllegalStateException e) {
                return KEPT; // 2-44 — 다중 칸 배열 수식의 한 칸은 POI 가 못 바꾼다. 그 수식은 그대로 둔다(2-47 — 알린다)
            }
        }
        return hit ? 1 : 0;
    }
}
