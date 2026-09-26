package kr.ejg.toolbox.core.deliverable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kr.ejg.toolbox.core.gen.TypeMapping;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;

/**
 * 산출물 정의서 01·02·03·04·10·11 값 표(2-1). 입력은 스냅샷 — 순수본 `산출물_sql.html` 의 쿼리가 하던 일을 스냅샷이 대신하고,
 * 문서마다 적힌 「수기」 메모를 규칙 R1~R20 으로 코드에 옮겼다(규칙 번호는 PLAN 2-1 행).
 */
public final class Definitions {

    /** 사람이 채우는 칸 — 비면 빈칸. author 만 비면 R4 표시 */
    public record Options(String author, String org, String dept, String bizArea, String dbDesc, String dbName, String logicalDbName,
            String os) {
        public static Options empty() {
            return new Options(null, null, null, null, null, null, null, null);
        }
    }

    public static final String NO_AUTHOR = "__작성자_미입력__";
    public static final String NO_COMMENT = "(코멘트 없음)";
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    static final List<String> COLS_01 = List.of("기관명", "부서명", "적용업무", "DB설명", "논리DB명", "물리DB명", "DBMS명", "DBMS버전",
            "운영체제명", "테이블수", "데이터용량");
    static final List<String> TAIL = List.of("담당부서", "담당자", "최초등록일", "최종수정일", "최종수정자", "변경구분");
    static final List<String> COLS_02 = cat(List.of("순번", "테이블소유자", "테이블명(영문)", "테이블명(한글)", "테이블용도", "관련엔터티명",
            "테이블설명", "보존기간", "갱신주기", "테이블볼륨(건)", "예상발생량(건)", "분류명", "태그정보"), TAIL);
    static final List<String> COLS_03 = cat(List.of("순번", "스키마명", "테이블명", "컬럼명(영문)", "컬럼명(한글)", "연관엔터티명", "연관속성명",
            "Not Null", "데이터타입", "데이터길이", "기본값", "PK정보", "컬럼설명"), TAIL);
    static final List<String> COLS_04 = List.of("순번", "부모 영문DB명", "부모 테이블소유자", "부모 한글테이블명", "부모 영문테이블명",
            "부모 한글컬럼명", "부모 영문컬럼명", "자식 영문DB명", "자식 테이블소유자", "자식 한글테이블명", "자식 영문테이블명", "자식 한글컬럼명",
            "자식 영문컬럼명", "삭제규칙", "갱신규칙");
    static final List<String> COLS_10 = List.of("순번", "DB명", "테이블명", "인덱스명", "인덱스구분", "컬럼순서", "컬럼명", "정렬", "유니크여부");
    static final List<String> COLS_11 = List.of("순번", "DB명", "테이블명", "제약조건명", "제약유형", "제약내용");

    private Definitions() {
    }

    private static List<String> cat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return List.copyOf(out);
    }

    /** R19 — 테이블은 스키마·이름 순, 컬럼은 순번 순 */
    public static List<Table> sorted(List<Schema> schemas) {
        List<Table> out = new ArrayList<>();
        schemas.forEach(s -> out.addAll(s.tables()));
        out.sort(Comparator.comparing((Table t) -> nz(t.schema())).thenComparing(Table::name));
        return out;
    }

    public static List<Doc> build(List<Schema> schemas, Options o) {
        List<Table> tables = sorted(schemas);
        String dialect = schemas.isEmpty() ? null : TypeMapping.dialectOf(schemas.get(0).dbVersion());
        return List.of(d01(schemas, tables, o), d02(tables, o), d03(tables, o), d04(tables, dialect), d10(tables), d11(tables));
    }

    static Doc d01(List<Schema> schemas, List<Table> tables, Options o) {
        List<String> names = new ArrayList<>();
        schemas.forEach(s -> names.add(s.name()));
        names.sort(null);
        String version = schemas.isEmpty() ? "" : nz(schemas.get(0).dbVersion());
        String physical = blank(o.dbName()) ? String.join("/", names) : o.dbName();
        List<Object> row = List.of(nz(o.org()), nz(o.dept()), nz(o.bizArea()), nz(o.dbDesc()), nz(o.logicalDbName()), physical,
                dbmsName(version), version, nz(o.os()), tables.size(), "");
        return new Doc("01", "데이터베이스 정의서", COLS_01, List.of(row));
    }

    static Doc d02(List<Table> tables, Options o) {
        List<List<Object>> rows = new ArrayList<>();
        int n = 0;
        for (Table t : tables) {
            List<Object> r = new ArrayList<>(List.of(++n, nz(t.schema()), t.name(), korOrMark(t.comment()), "", kor(t.comment()), "", "",
                    "", t.rowCount() == null ? "" : (Object) t.rowCount(), "", "", ""));
            r.addAll(tail(t, o));
            rows.add(r);
        }
        return new Doc("02", "테이블 정의서", COLS_02, rows);
    }

    static Doc d03(List<Table> tables, Options o) {
        List<List<Object>> rows = new ArrayList<>();
        int n = 0;
        for (Table t : tables) {
            Set<String> pk = t.pk() == null ? Set.of() : Set.copyOf(t.pk().columns());
            List<Column> cols = new ArrayList<>(t.columns());
            cols.sort(Comparator.comparingInt(Column::ordinal));
            for (Column c : cols) {
                List<Object> r = new ArrayList<>(List.of(++n, nz(t.schema()), t.name(), c.name(), korOrMark(c.comment()), kor(t.comment()),
                        kor(c.comment()), c.nullable() ? "N" : "Y", nz(c.nativeType()), length(c), nz(c.defaultValue()),
                        pk.contains(c.name()) ? "Y" : "N", ""));
                r.addAll(tail(t, o));
                rows.add(r);
            }
        }
        return new Doc("03", "컬럼 정의서", COLS_03, rows);
    }

    static Doc d04(List<Table> tables, String dialect) {
        Map<String, Table> byName = new HashMap<>();
        tables.forEach(t -> byName.put(key(t.schema(), t.name()), t));
        List<List<Object>> rows = new ArrayList<>();
        int n = 0;
        boolean oracle = "oracle".equals(dialect) || "tibero".equals(dialect);
        for (Table child : tables) {
            for (ForeignKey fk : child.fks()) {
                String parentSchema = fk.refSchema() == null ? nz(child.schema()) : fk.refSchema();
                Table parent = byName.get(key(parentSchema, fk.refTable()));
                for (int i = 0; i < fk.columns().size(); i++) {
                    String pc = i < fk.refColumns().size() ? fk.refColumns().get(i) : "";
                    String cc = fk.columns().get(i);
                    rows.add(List.of(++n, parentSchema, parentSchema, parent == null ? "" : kor(parent.comment()), fk.refTable(),
                            parent == null ? "" : kor(commentOf(parent, pc)), pc, nz(child.schema()), nz(child.schema()), kor(child.comment()),
                            child.name(), kor(commentOf(child, cc)), cc, "", oracle ? "NO ACTION" : ""));
                }
            }
        }
        return new Doc("04", "테이블 관계 정의서", COLS_04, rows);
    }

    static Doc d10(List<Table> tables) {
        List<List<Object>> rows = new ArrayList<>();
        int n = 0;
        for (Table t : tables) {
            Set<String> seen = new HashSet<>();
            if (t.pk() != null && !t.pk().columns().isEmpty()) {
                n = indexRows(rows, n, t, nz(t.pk().name()), "PK", true, t.pk().columns());
                seen.add(nz(t.pk().name()).toUpperCase(Locale.ROOT));
            }
            for (UniqueKey u : t.uniques()) {
                if (seen.add(nz(u.name()).toUpperCase(Locale.ROOT))) {
                    n = indexRows(rows, n, t, nz(u.name()), "UNIQUE", true, u.columns());
                }
            }
            for (Index ix : t.indexes()) {
                if (seen.add(nz(ix.name()).toUpperCase(Locale.ROOT))) {
                    n = indexRows(rows, n, t, nz(ix.name()), ix.unique() ? "UNIQUE" : "일반", ix.unique(), ix.columns());
                }
            }
        }
        return new Doc("10", "인덱스 정의서", COLS_10, rows);
    }

    /** R15 — 정렬은 스냅샷에 없어 ASC 로 적는다 */
    private static int indexRows(List<List<Object>> rows, int n, Table t, String name, String kind, boolean unique, List<String> cols) {
        for (int i = 0; i < cols.size(); i++) {
            rows.add(List.of(++n, nz(t.schema()), t.name(), name, kind, i + 1, cols.get(i), "ASC", unique ? "Y" : "N"));
        }
        return n;
    }

    /** R16 — PK·UNIQUE. CHECK 는 스냅샷에 없다 */
    static Doc d11(List<Table> tables) {
        List<List<Object>> rows = new ArrayList<>();
        int n = 0;
        for (Table t : tables) {
            if (t.pk() != null && !t.pk().columns().isEmpty()) {
                rows.add(List.of(++n, nz(t.schema()), t.name(), nz(t.pk().name()), "PK", String.join(", ", t.pk().columns())));
            }
            for (UniqueKey u : t.uniques()) {
                rows.add(List.of(++n, nz(t.schema()), t.name(), nz(u.name()), "UNIQUE", String.join(", ", u.columns())));
            }
        }
        return new Doc("11", "제약조건 정의서", COLS_11, rows);
    }

    /** R3·R4·R5 — 02 와 03 이 같은 테이블에서 같은 값을 쓰도록 한 곳에서 만든다 */
    static List<Object> tail(Table t, Options o) {
        String created = t.createdAt() == null ? "" : t.createdAt().format(DAY);
        LocalDateTime last = t.lastDdlAt();
        String modified = last == null ? created : last.format(DAY);
        String change = last == null || (t.createdAt() != null && !last.toLocalDate().isAfter(t.createdAt().toLocalDate())) ? "신규" : "수정";
        return List.of(nz(o.dept()), "", created, modified, blank(o.author()) ? NO_AUTHOR : o.author().trim(), change);
    }

    /** R9 — 문자형 length, 수형 precision(소수점 있으면 p,s) */
    static String length(Column c) {
        if (c.length() != null) {
            return String.valueOf(c.length());
        }
        if (c.precision() != null) {
            return c.precision() + (c.scale() != null && c.scale() > 0 ? "," + c.scale() : "");
        }
        return "";
    }

    static String dbmsName(String version) {
        String d = TypeMapping.dialectOf(version);
        if (d == null) {
            return "";
        }
        return switch (d) {
            case "oracle" -> "Oracle";
            case "tibero" -> "Tibero";
            case "postgresql" -> "PostgreSQL";
            case "mariadb" -> version.toLowerCase(Locale.ROOT).contains("mariadb") ? "MariaDB" : "MySQL";
            case "mssql" -> "SQL Server";
            default -> d;
        };
    }

    private static String commentOf(Table t, String column) {
        for (Column c : t.columns()) {
            if (c.name().equalsIgnoreCase(column)) {
                return c.comment();
            }
        }
        return null;
    }

    /** R6 — 한글명 칸: 코멘트가 없으면 빈칸 대신 표시(누락 파악용) */
    static String korOrMark(String comment) {
        return blank(comment) ? NO_COMMENT : comment.trim();
    }

    /** R8·R14 — 재사용 칸은 표시 없이 빈칸 */
    static String kor(String comment) {
        return blank(comment) ? "" : comment.trim();
    }

    private static String key(String schema, String name) {
        return (nz(schema) + "." + name).toUpperCase(Locale.ROOT);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
