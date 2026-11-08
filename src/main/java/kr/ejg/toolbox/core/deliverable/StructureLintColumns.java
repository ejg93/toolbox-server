package kr.ejg.toolbox.core.deliverable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.ejg.toolbox.core.deliverable.StructureLint.Finding;
import kr.ejg.toolbox.core.deliverable.StructureLint.Rule;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Table;

/**
 * 구조 점검 규칙 둘(설계 54 — SchemaCrawler 의 NullColumnsInIndex·TooManyLobs). StructureLint 의 메서드 수 상한(metric.classMethods 30) 때문에 따로 둔다.
 */
final class StructureLintColumns {

    /** SchemaCrawler TooManyLobs 기본 상한 — 이보다 많으면 짚는다 */
    static final int MAX_LOBS = 1;
    /** BLOB · CLOB · NCLOB · LONGVARBINARY */
    private static final Set<Integer> LOB_JDBC = Set.of(2004, 2005, 2011, -4);
    /** TEXT·TINYTEXT 는 안 센다 — PG 의 보통 문자 형식과 겹친다. 2-37 — LONG VARCHAR·STRING·LONG 은 센다(eGov 가 oracle CLOB 자리에 쓰는 형식) */
    private static final Set<String> LOB_TYPES = Set.of("CLOB", "NCLOB", "BLOB", "BFILE", "LONG RAW", "LONGBLOB", "MEDIUMBLOB", "TINYBLOB",
            "LONGTEXT", "MEDIUMTEXT", "IMAGE", "NTEXT", "BYTEA", "LONG VARCHAR", "STRING", "LONG");
    /** 2-36 — fkTypeMismatch 비교용 동의어(방언 안에서 같은 저장 형식). 글은 원문 꼴로 낸다. N-형식·DATETIME/TIMESTAMP 는 다른 것으로 둔다(Oracle·MySQL FK 가 거절한다) */
    private static final Map<String, String> CANON = Map.ofEntries(
            Map.entry("INT", "INTEGER"), Map.entry("INT4", "INTEGER"), Map.entry("SERIAL", "INTEGER"), Map.entry("SERIAL4", "INTEGER"),
            Map.entry("INT8", "BIGINT"), Map.entry("BIGSERIAL", "BIGINT"), Map.entry("SERIAL8", "BIGINT"),
            Map.entry("INT2", "SMALLINT"), Map.entry("SMALLSERIAL", "SMALLINT"), Map.entry("SERIAL2", "SMALLINT"),
            Map.entry("VARCHAR2", "VARCHAR"), Map.entry("CHARACTER VARYING", "VARCHAR"),
            Map.entry("NVARCHAR2", "NVARCHAR"), Map.entry("NATIONAL CHARACTER VARYING", "NVARCHAR"),
            Map.entry("CHARACTER", "CHAR"), Map.entry("BPCHAR", "CHAR"), Map.entry("NATIONAL CHARACTER", "NCHAR"),
            Map.entry("NUMBER", "NUMERIC"), Map.entry("DECIMAL", "NUMERIC"), Map.entry("DEC", "NUMERIC"),
            Map.entry("DOUBLE PRECISION", "DOUBLE"), Map.entry("FLOAT8", "DOUBLE"), Map.entry("FLOAT4", "REAL"),
            Map.entry("BOOL", "BOOLEAN"),
            Map.entry("TIMESTAMP WITHOUT TIME ZONE", "TIMESTAMP"), Map.entry("TIMESTAMP WITH TIME ZONE", "TIMESTAMPTZ"));

    private StructureLintColumns() {
    }

    /** norm 한 형식의 대표 이름 — 표에 없으면 그대로 */
    static String canon(String normed) {
        return CANON.getOrDefault(normed, normed);
    }

    /** PK 아닌 유니크(제약·유니크 인덱스, 받침 인덱스는 keyed 가 뺐다)마다 NULL 허용 컬럼이 하나라도 있으면 하나 */
    static void uniqueNullable(Table t, String s, List<StructureLint.Keyed> ks, List<Finding> out) {
        for (StructureLint.Keyed k : ks) {
            List<String> nullable = new ArrayList<>();
            for (String c : k.unique() && !k.pk() ? k.cols() : List.<String>of()) {
                Column col = StructureLint.column(t, c);
                if (col != null && col.nullable()) {
                    nullable.add(col.name());
                }
            }
            if (!nullable.isEmpty()) {
                out.add(new Finding(Rule.UNIQUE_NULLABLE, s, t.name(), String.join(",", nullable), ("유니크 " + k.name()).trim() + "(" + String.join(",", k.cols()) + ")"));
            }
        }
    }

    /** LOB 컬럼이 MAX_LOBS 보다 많으면 하나 */
    static void lobs(Table t, String s, List<Finding> out) {
        List<String> names = new ArrayList<>();
        for (Column c : t.columns()) {
            if (isLob(c)) {
                names.add(c.name());
            }
        }
        if (names.size() > MAX_LOBS) {
            out.add(new Finding(Rule.TOO_MANY_LOBS, s, t.name(), null, "LOB " + names.size() + ": " + String.join("·", names)));
        }
    }

    /** jdbcType 이 LOB 이거나 형식 이름(대문자·괄호 뗌)이 LOB 목록에 있다 */
    static boolean isLob(Column c) {
        String n = StructureLint.norm(c.nativeType());
        return c.jdbcType() != null && LOB_JDBC.contains(c.jdbcType()) || n != null && LOB_TYPES.contains(n);
    }
}
