package kr.ejg.toolbox.core.logical;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import kr.ejg.toolbox.core.text.Csv;
import kr.ejg.toolbox.core.text.Js;

/**
 * 변환기 입력 만들기(3-3). CSV 는 순수본 setCol 의 열 추측·run 의 PK·NULL 판정을 그대로 옮겼다.
 * 스냅샷(메타모델) 입력은 {@code fromSchemas}(3-3 에서 더한다).
 */
public final class ColumnInputs {

    static final List<String> OWNER = List.of("OWNER", "스키마", "DB명");
    static final List<String> TABLE = List.of("TABLE_NAME", "테이블");
    static final List<String> COLUMN = List.of("COLUMN_NAME", "컬럼");
    static final List<String> PK = List.of("IS_PK", "PK여부", "PK");
    static final List<String> ORD = List.of("COLUMN_ID", "ORDINAL_POSITION", "컬럼순서", "순서");
    static final List<String> TYPE = List.of("DATA_TYPE", "타입", "데이터타입");
    static final List<String> LEN = List.of("DATA_LENGTH", "LENGTH", "길이");
    static final List<String> SCALE = List.of("DATA_SCALE", "SCALE", "소수점");
    static final List<String> NULL = List.of("NULLABLE", "IS_NULLABLE", "NOT_NULL", "NOTNULL", "NOT NULL", "널여부");

    /** 순수본 FKPKY — PK 로 보는 값 */
    static final Set<String> PK_YES = Set.of("Y", "1", "P", "PK", "TRUE", "T", "YES", "예");

    private ColumnInputs() {
    }

    /**
     * 컬럼목록 CSV → 입력. 순수본은 테이블·컬럼 열 추측이 빗나가면 0번 열로 떨어진다 — 화면에서 사람이 고치는 전제다.
     * 서버엔 고를 화면이 없어 조용히 엉뚱한 열을 읽게 되므로(3-3): 컬럼 열이 없으면 예외, 테이블 열이 없으면 「컬럼만」 모드.
     *
     * @param ownerFixed OWNER 열이 없을 때 쓸 스키마명(순수본 「직접 입력」). 열이 있으면 무시
     */
    public static List<ColumnInput> fromCsv(byte[] csv, String ownerFixed) {
        List<List<String>> rows = Csv.parse(Csv.decode(csv));
        if (rows.size() < 2) {
            throw new IllegalArgumentException("데이터가 부족하다 — 헤더와 한 줄 이상");
        }
        List<String> h = rows.get(0);
        int oi = Csv.guess(h, OWNER, true);
        int ti = Csv.guess(h, TABLE, true);
        int ci = Csv.guess(h, COLUMN, true);
        if (ci < 0) {
            throw new IllegalArgumentException("컬럼명 열이 없다 — 헤더에 " + String.join("·", COLUMN)
                    + " 중 하나가 있어야 한다. 테이블명 열은 " + String.join("·", TABLE) + (ti < 0 ? "(없음 — 컬럼만 변환)" : ""));
        }
        int pk = Csv.guess(h, PK, true);
        int ord = Csv.guess(h, ORD, true);
        int dt = Csv.guess(h, TYPE, true);
        int dl = Csv.guess(h, LEN, true);
        int ds = Csv.guess(h, SCALE, true);
        int nn = Csv.guess(h, NULL, true);
        String nnHead = nn >= 0 ? h.get(nn) : "";
        List<ColumnInput> out = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            out.add(new ColumnInput(
                    oi >= 0 ? cell(r, oi) : (ownerFixed == null ? "" : ownerFixed),
                    ti >= 0 ? cell(r, ti) : null,
                    cell(r, ci),
                    dt >= 0 ? Js.trim(cell(r, dt)) : "",
                    dl >= 0 ? Js.trim(cell(r, dl)) : "",
                    ds >= 0 ? Js.trim(cell(r, ds)) : "",
                    pk >= 0 ? (PK_YES.contains(Js.trim(cell(r, pk)).toUpperCase(Locale.ROOT)) ? "Y" : "N") : "",
                    nn >= 0 ? normNotNull(cell(r, nn), nnHead) : "",
                    ord >= 0 ? Js.trim(cell(r, ord)) : String.valueOf(i)));
        }
        return out;
    }

    /**
     * 스냅샷(메타모델) → 입력(3-3, 2.2 C — 업로드 대신 스키마 읽기). 길이는 문자형이면 length, 수는 precision.
     * PK 는 테이블 PK 컬럼이면 Y, NOT NULL 은 nullable 의 반대, 순번은 컬럼 순번.
     */
    public static List<ColumnInput> fromSchemas(List<kr.ejg.toolbox.core.meta.Schema> schemas) {
        List<ColumnInput> out = new ArrayList<>();
        for (kr.ejg.toolbox.core.meta.Schema s : schemas) {
            for (kr.ejg.toolbox.core.meta.Table t : s.tables()) {
                java.util.Set<String> pkCols = t.pk() == null ? java.util.Set.of() : java.util.Set.copyOf(t.pk().columns());
                for (kr.ejg.toolbox.core.meta.Column c : t.columns()) {
                    Long len = c.length() != null ? c.length() : (c.precision() == null ? null : Long.valueOf(c.precision()));
                    out.add(new ColumnInput(t.schema(), t.name(), c.name(), c.nativeType() == null ? "" : c.nativeType(),
                            len == null ? "" : String.valueOf(len), c.scale() == null ? "" : String.valueOf(c.scale()),
                            pkCols.contains(c.name()) ? "Y" : "N", c.nullable() ? "N" : "Y", String.valueOf(c.ordinal())));
                }
            }
        }
        return out;
    }

    /** 순수본 normNN — 헤더에 NULLABLE 이 있으면 Y 는 「NULL 허용」, 아니면(NOT_NULL 류) Y 는 「NOT NULL」 */
    static String normNotNull(String v, String header) {
        String t = Js.trim(v).toUpperCase(Locale.ROOT);
        if (t.isEmpty()) {
            return "";
        }
        boolean nullableHeader = header != null && header.toUpperCase(Locale.ROOT).contains("NULLABLE");
        boolean notNull;
        if (t.equals("NOT NULL") || t.equals("NOTNULL")) {
            notNull = true;
        } else if (t.equals("NULL")) {
            notNull = false;
        } else if (t.equals("Y") || t.equals("YES") || t.equals("TRUE") || t.equals("T") || t.equals("1")) {
            notNull = !nullableHeader;
        } else if (t.equals("N") || t.equals("NO") || t.equals("FALSE") || t.equals("F") || t.equals("0")) {
            notNull = nullableHeader;
        } else {
            return "";
        }
        return notNull ? "Y" : "N";
    }

    private static String cell(List<String> row, int i) {
        return i >= 0 && i < row.size() ? row.get(i) : "";
    }
}
