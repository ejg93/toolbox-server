package kr.ejg.toolbox.core.logical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import kr.ejg.toolbox.core.dict.Dictionaries;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Test;

/** 3-3 — CSV·스냅샷 두 입력 */
class ColumnInputsTest {

    @Test
    void sampleCsvGives944DistinctColumns() throws Exception {
        List<ColumnInput> in = ColumnInputs.fromCsv(Files.readAllBytes(LogicalRunSampleTest.SAMPLE.resolve("columns-1000.csv")), null);
        assertEquals(996, in.size(), "데이터 행 그대로");
        assertEquals(944, LogicalRun.run(in, Dictionaries.empty(), List.of(), true).rows().size(), "(스키마|테이블|컬럼) 중복 제거");
        ColumnInput first = in.get(0);
        assertEquals(List.of("SHOP", "TB_CUST_MST", "CUST_ID", "VARCHAR2", "20", "Y", "Y", "1"),
                List.of(first.owner(), first.table(), first.column(), first.dataType(), first.length(), first.pk(), first.notNull(),
                        first.ordinal()), "NULLABLE=N 이면 NOT NULL=Y");
    }

    @Test
    void missingColumnHeaderFailsWithNames() {
        byte[] csv = "A,B\n1,2\n".getBytes(StandardCharsets.UTF_8);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ColumnInputs.fromCsv(csv, null));
        assertTrue(e.getMessage().contains("COLUMN_NAME"), e.getMessage());
    }

    @Test
    void noTableColumnMeansColumnsOnly() {
        byte[] csv = "COLUMN_NAME\nUSE_YN\nuse_yn\nITEM_ID\n".getBytes(StandardCharsets.UTF_8);
        List<ColumnInput> in = ColumnInputs.fromCsv(csv, "APP");
        assertNull(in.get(0).table());
        assertEquals("APP", in.get(0).owner(), "직접 입력 스키마");
        LogicalRun.Result r = LogicalRun.run(in, Dictionaries.empty(), List.of(), true);
        assertEquals(2, r.rows().size(), "컬럼만 모드는 컬럼명 대문자로 중복 제거");
        assertEquals(0, r.tableRows().size());
    }

    @Test
    void snapshotBecomesInput() throws Exception {
        List<Schema> pg = GoldenFiles.schemas("meta/postgres-vendor.json");
        List<ColumnInput> in = ColumnInputs.fromSchemas(pg);
        ColumnInput login = in.stream().filter(c -> c.table().equals("users") && c.column().equals("login_id")).findFirst().orElseThrow();
        assertEquals("N", login.pk());
        assertEquals("Y", login.notNull());
        assertEquals("50", login.length());
        ColumnInput price = in.stream().filter(c -> c.column().equals("price")).findFirst().orElseThrow();
        assertEquals("12", price.length(), "수는 precision");
        assertEquals("2", price.scale());
        ColumnInput uid = in.stream().filter(c -> c.table().equals("users") && c.column().equals("user_id")).findFirst().orElseThrow();
        assertEquals("Y", uid.pk());
    }

    @Test
    void notNullNormalizationLikePure() {
        assertEquals("Y", ColumnInputs.normNotNull("N", "NULLABLE"));
        assertEquals("N", ColumnInputs.normNotNull("Y", "IS_NULLABLE"));
        assertEquals("Y", ColumnInputs.normNotNull("Y", "NOT_NULL"));
        assertEquals("Y", ColumnInputs.normNotNull("not null", "널여부"));
        assertEquals("", ColumnInputs.normNotNull("?", "NULLABLE"));
    }
}
