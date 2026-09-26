package kr.ejg.toolbox.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import kr.ejg.toolbox.core.sqlrun.ResultTable;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 1-7 — 쓰고 POI 로 다시 읽어 셀 비교 */
class XlsxWriterTest {

    @TempDir
    Path tmp;

    static ResultTable sample(boolean truncated) {
        return new ResultTable(
                List.of(new ResultTable.Col("ID", "INTEGER"), new ResultTable.Col("이름", "VARCHAR"), new ResultTable.Col("OK", "BOOLEAN")),
                List.of(Arrays.asList(1, "가,나", true), Arrays.asList(2, null, false)),
                truncated, -1, 3);
    }

    @Test
    void xlsxCellsRoundTrip() throws Exception {
        Path f = tmp.resolve("a/b/result.xlsx");
        XlsxWriter.write(sample(true), f);
        try (InputStream in = Files.newInputStream(f); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet s = wb.getSheet("결과");
            Row h = s.getRow(0);
            assertEquals("ID", h.getCell(0).getStringCellValue());
            assertEquals("이름", h.getCell(1).getStringCellValue());
            assertEquals(1.0, s.getRow(1).getCell(0).getNumericCellValue());
            assertEquals("가,나", s.getRow(1).getCell(1).getStringCellValue());
            assertEquals(true, s.getRow(1).getCell(2).getBooleanCellValue());
            assertEquals(null, s.getRow(2).getCell(1), "NULL 은 빈 셀");
            assertEquals("… 최대 행수(2)에서 잘렸다", s.getRow(3).getCell(0).getStringCellValue());
        }
    }

    @Test
    void csvHasBomAndQuotes() throws Exception {
        Path f = tmp.resolve("result.csv");
        XlsxWriter.writeCsv(sample(false), f);
        String s = Files.readString(f, StandardCharsets.UTF_8);
        assertEquals(XlsxWriter.BOM + "ID,이름,OK\r\n1,\"가,나\",true\r\n2,,false\r\n", s);
    }
}
