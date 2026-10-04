package kr.ejg.toolbox.core.deliverable;

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
import kr.ejg.toolbox.core.meta.Check;
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

    /** 표준 열(행안부고시 제2025-19호 별표2·4, 2-10) → 확장 열(구판 서식에 있던 것) 순 */
    static final List<String> COLS_01 = List.of("기관명", "부서명", "관련법령", "한글 DB명", "영문 DB명", "구축일자", "DB 설명",
            "업무분류체계", "DBMS 정보", "운영체제정보", "DB 형태", "테이블 수", "데이터 용량", "적용업무");
    static final List<String> TAIL = List.of("담당부서", "담당자", "최초등록일", "최종수정일", "최종수정자", "변경구분");
    static final List<String> COLS_02 = cat(List.of("영문 DB명", "테이블 소유자", "한글 테이블명", "영문 테이블명", "테이블 유형",
            "관련 엔터티명", "테이블 설명", "발생주기", "테이블 볼륨", "공개/비공개 여부", "개방데이터목록", "순번", "보존기간", "예상발생량(건)",
            "분류명", "태그정보"), TAIL);
    static final List<String> COLS_03 = cat(List.of("영문 테이블명", "한글 컬럼명", "영문 컬럼명", "컬럼 설명", "연관 엔터티명", "연관 속성명",
            "데이터 타입", "데이터 길이", "Not Null 여부", "PK정보", "AK정보", "FK정보", "제약조건", "개인정보 여부", "암호화 여부",
            "공개/비공개 여부", "순번", "스키마명", "기본값"), TAIL);
    /** 04·10·11 은 표준 문서가 없다(확장) — 열 이름만 별표 용어로(2-11) */
    static final List<String> COLS_04 = List.of("순번", "부모 영문 DB명", "부모 테이블 소유자", "부모 한글 테이블명", "부모 영문 테이블명",
            "부모 한글 컬럼명", "부모 영문 컬럼명", "자식 영문 DB명", "자식 테이블 소유자", "자식 한글 테이블명", "자식 영문 테이블명",
            "자식 한글 컬럼명", "자식 영문 컬럼명", "삭제규칙", "갱신규칙", "근거");
    static final List<String> COLS_10 = List.of("순번", "영문 DB명", "영문 테이블명", "인덱스명", "인덱스구분", "컬럼순서", "컬럼명", "정렬", "유니크여부");
    static final List<String> COLS_11 = List.of("순번", "영문 DB명", "영문 테이블명", "제약조건명", "제약유형", "제약내용");

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
        return build(schemas, o, Set.of());
    }

    /** @param pii 개인정보 후보 컬럼 열쇠 「스키마.표.컬럼」(대문자) — 03 개인정보 여부 = Y, 추정 건수에 센다(2-14) */
    public static List<Doc> build(List<Schema> schemas, Options o, Set<String> pii) {
        return build(schemas, o, pii, Relations.Result.empty());
    }

    /** @param inferred 매퍼 조인으로 추정한 관계(2-19) — 강한 것만 04 에 「추정(조인 n문장)」 으로 */
    public static List<Doc> build(List<Schema> schemas, Options o, Set<String> pii, Relations.Result inferred) {
        List<Table> tables = sorted(schemas);
        return List.of(d01(schemas, tables, o), d02(tables, o), d03(tables, o, pii), d04(tables, inferred), d10(tables), d11(tables));
    }

    /** 개인정보 후보 열쇠 */
    public static String piiKey(String schema, String table, String col) {
        return (nz(schema) + "." + nz(table) + "." + nz(col)).toUpperCase(Locale.ROOT);
    }

    static Doc d01(List<Schema> schemas, List<Table> tables, Options o) {
        List<String> names = new ArrayList<>();
        schemas.forEach(s -> names.add(s.name()));
        names.sort(null);
        String version = schemas.isEmpty() ? "" : nz(schemas.get(0).dbVersion());
        String physical = blank(o.dbName()) ? String.join("/", names) : o.dbName();
        // DBMS 정보 = DBMS명 + 버전 한 칸. 버전 글이 이미 DBMS명으로 시작하면(「Oracle Database 19c」) 버전만
        String name = dbmsName(version);
        String dbms = blank(name) || version.toLowerCase(Locale.ROOT).startsWith(name.toLowerCase(Locale.ROOT)) ? version
                : name + (blank(version) ? "" : " " + version);
        // 관련법령·구축일자·업무분류체계는 사람이 채운다(빈칸). DB 형태는 관계형만 다뤄 「정형」
        List<Object> row = List.of(nz(o.org()), nz(o.dept()), "", nz(o.logicalDbName()), physical, "", nz(o.dbDesc()), "", dbms,
                nz(o.os()), "정형", tables.size(), size(schemas), nz(o.bizArea()));
        return new Doc("01", "데이터베이스 정의서", COLS_01, List.of(row));
    }

    /** R17 — 데이터용량 = 스키마 용량 합(1-23). 아는 것만 더하고 전부 모르면 빈칸. 1GiB 미만 MB, 이상 GB(소수 한 자리) */
    static String size(List<Schema> schemas) {
        Long sum = null;
        for (Schema s : schemas) {
            if (s.sizeBytes() != null) {
                sum = (sum == null ? 0L : sum) + s.sizeBytes();
            }
        }
        if (sum == null) {
            return "";
        }
        double mb = sum / 1048576.0;
        return mb < 1024 ? String.format(Locale.ROOT, "%.1f MB", mb) : String.format(Locale.ROOT, "%.1f GB", mb / 1024);
    }

    static Doc d02(List<Table> tables, Options o) {
        List<List<Object>> rows = new ArrayList<>();
        int n = 0;
        for (Table t : tables) {
            String db = blank(o.dbName()) ? nz(t.schema()) : o.dbName();
            // 공개/비공개 여부·개방데이터목록은 사람이 채운다
            List<Object> r = new ArrayList<>(List.of(db, nz(t.schema()), korOrMark(t.comment()), t.name(), "", kor(t.comment()), "", "",
                    t.rowCount() == null ? "" : (Object) t.rowCount(), "", "", ++n, "", "", "", ""));
            r.addAll(tail(t, o));
            rows.add(r);
        }
        return new Doc("02", "테이블 정의서", COLS_02, rows);
    }

    static Doc d03(List<Table> tables, Options o, Set<String> pii) {
        List<List<Object>> rows = new ArrayList<>();
        int n = 0;
        int piiCount = 0;
        for (Table t : tables) {
            List<String> pk = t.pk() == null ? List.of() : t.pk().columns();
            List<Column> cols = new ArrayList<>(t.columns());
            cols.sort(Comparator.comparingInt(Column::ordinal));
            for (Column c : cols) {
                // R7 Not Null 여부 = 표준 표기(Y = Nullable, 사용자 2026-10-04) · R10 PK정보 = PK + 참여 순서 두 자리
                // 개인정보·암호화·공개 여부는 사람이 채운다(개인정보는 2-14 가 추정)
                List<Object> r = new ArrayList<>(List.of(t.name(), korOrMark(c.comment()), c.name(), "", kor(t.comment()), kor(c.comment()),
                        nz(c.nativeType()), length(c), c.nullable() ? "Y" : "N", pkInfo(pk, c.name()), akInfo(t, c.name()), fkInfo(t, c.name()),
                        constraints(t, c), "", "", "", ++n, nz(t.schema()), nz(c.defaultValue())));
                if (pii.contains(piiKey(t.schema(), t.name(), c.name()))) {
                    r.set(COLS_03.indexOf("개인정보 여부"), "Y");
                    piiCount++;
                }
                r.addAll(tail(t, o));
                rows.add(r);
            }
        }
        return new Doc("03", "컬럼 정의서", COLS_03, rows, piiCount == 0 ? Map.of() : Map.of("개인정보 여부", piiCount));
    }

    /** PK정보 — PK + 참여 순서 두 자리(PK01), 아니면 빈칸(별표2 작성지침) */
    static String pkInfo(List<String> pk, String col) {
        int i = indexIgnoreCase(pk, col);
        return i < 0 ? "" : String.format(Locale.ROOT, "PK%02d", i + 1);
    }

    /** AK정보 — AK_<유니크 키 순번>-<참여 순서 두 자리>(AK_1-01). 유니크 키 순번은 uniques() 순서, 여럿이면 ", " */
    static String akInfo(Table t, String col) {
        List<String> out = new ArrayList<>();
        for (int k = 0; k < t.uniques().size(); k++) {
            int i = indexIgnoreCase(t.uniques().get(k).columns(), col);
            if (i >= 0) {
                out.add(String.format(Locale.ROOT, "AK_%d-%02d", k + 1, i + 1));
            }
        }
        return String.join(", ", out);
    }

    /** FK정보 — 참조테이블.참조컬럼(다른 스키마면 스키마.테이블.컬럼), 여럿이면 ", " */
    static String fkInfo(Table t, String col) {
        List<String> out = new ArrayList<>();
        for (ForeignKey fk : t.fks()) {
            int i = indexIgnoreCase(fk.columns(), col);
            if (i >= 0) {
                String ref = (fk.refSchema() == null ? "" : fk.refSchema() + ".") + fk.refTable();
                out.add(ref + "." + (i < fk.refColumns().size() ? fk.refColumns().get(i) : ""));
            }
        }
        return String.join(", ", out);
    }

    /** 제약조건 — DEFAULT 원문 · 이 컬럼 이름이 든 CHECK 의 「CHECK (조건)」, "; " 로 잇는다 */
    static String constraints(Table t, Column c) {
        List<String> out = new ArrayList<>();
        if (!blank(c.defaultValue())) {
            out.add("DEFAULT " + c.defaultValue().strip());
        }
        java.util.regex.Pattern word = java.util.regex.Pattern.compile("(?i)(?<![\\w$#])" + java.util.regex.Pattern.quote(c.name()) + "(?![\\w$#])");
        for (Check k : t.checks()) {
            if (k.condition() != null && word.matcher(k.condition()).find()) {
                out.add("CHECK (" + k.condition() + ")");
            }
        }
        return String.join("; ", out);
    }

    private static int indexIgnoreCase(List<String> xs, String x) {
        for (int i = 0; i < xs.size(); i++) {
            if (xs.get(i).equalsIgnoreCase(x)) {
                return i;
            }
        }
        return -1;
    }

    /** R13 — 삭제·갱신규칙은 수집값(1-19), 모르면 빈칸 */
    static Doc d04(List<Table> tables, Relations.Result inferred) {
        Map<String, Table> byName = new HashMap<>();
        tables.forEach(t -> byName.put(key(t.schema(), t.name()), t));
        List<List<Object>> rows = new ArrayList<>();
        int n = 0;
        for (Table child : tables) {
            for (ForeignKey fk : child.fks()) {
                String parentSchema = fk.refSchema() == null ? nz(child.schema()) : fk.refSchema();
                Table parent = byName.get(key(parentSchema, fk.refTable()));
                for (int i = 0; i < fk.columns().size(); i++) {
                    String pc = i < fk.refColumns().size() ? fk.refColumns().get(i) : "";
                    String cc = fk.columns().get(i);
                    rows.add(List.of(++n, parentSchema, parentSchema, parent == null ? "" : kor(parent.comment()), fk.refTable(),
                            parent == null ? "" : kor(commentOf(parent, pc)), pc, nz(child.schema()), nz(child.schema()), kor(child.comment()),
                            child.name(), kor(commentOf(child, cc)), cc, nz(fk.deleteRule()), nz(fk.updateRule()), "선언"));
                }
            }
        }
        // 강한 추정 관계 — 선언 FK 뒤에 부모·자식 이름순. 삭제·갱신규칙은 모른다(빈칸)
        Map<String, Table> byTable = new HashMap<>();
        tables.forEach(t -> byTable.putIfAbsent(t.name().toUpperCase(Locale.ROOT), t));
        int guessed = 0;
        for (Relations.Inferred r : inferred.strong()) {
            Table parent = byTable.get(r.parentTable().toUpperCase(Locale.ROOT));
            Table child = byTable.get(r.childTable().toUpperCase(Locale.ROOT));
            if (parent == null || child == null) {
                continue;
            }
            for (int i = 0; i < r.parentCols().size(); i++) {
                String pc = r.parentCols().get(i);
                String cc = i < r.childCols().size() ? nz(r.childCols().get(i)) : "";
                rows.add(List.of(++n, nz(parent.schema()), nz(parent.schema()), kor(parent.comment()), parent.name(), kor(commentOf(parent, pc)), pc,
                        nz(child.schema()), nz(child.schema()), kor(child.comment()), child.name(), kor(commentOf(child, cc)), cc, "", "",
                        "추정(조인 " + r.statements() + "문장)"));
                guessed++;
            }
        }
        return new Doc("04", "테이블 관계 정의서", COLS_04, rows, guessed == 0 ? Map.of() : Map.of("근거", guessed));
    }

    static Doc d10(List<Table> tables) {
        List<List<Object>> rows = new ArrayList<>();
        int n = 0;
        for (Table t : tables) {
            Set<String> seen = new HashSet<>();
            if (t.pk() != null && !t.pk().columns().isEmpty()) {
                n = indexRows(rows, n, t, nz(t.pk().name()), "PK", true, t.pk().columns(), null);
                seen.add(nz(t.pk().name()).toUpperCase(Locale.ROOT));
            }
            for (UniqueKey u : t.uniques()) {
                if (seen.add(nz(u.name()).toUpperCase(Locale.ROOT))) {
                    n = indexRows(rows, n, t, nz(u.name()), "UNIQUE", true, u.columns(), indexNamed(t, u.name()));
                }
            }
            for (Index ix : t.indexes()) {
                if (seen.add(nz(ix.name()).toUpperCase(Locale.ROOT))) {
                    n = indexRows(rows, n, t, nz(ix.name()), ix.unique() ? "UNIQUE" : "일반", ix.unique(), ix.columns(), ix);
                }
            }
        }
        return new Doc("10", "인덱스 정의서", COLS_10, rows);
    }

    /**
     * R15 — 정렬은 수집값(1-20). UNIQUE 제약은 같은 이름 인덱스의 정렬, PK 는 PK 인덱스를 안 모아(loadIndexes) 빈칸. 모르면 빈칸
     */
    private static int indexRows(List<List<Object>> rows, int n, Table t, String name, String kind, boolean unique, List<String> cols,
            Index sorted) {
        for (int i = 0; i < cols.size(); i++) {
            rows.add(List.of(++n, nz(t.schema()), t.name(), name, kind, i + 1, cols.get(i), sorted == null ? "" : sorted.sortAt(i),
                    unique ? "Y" : "N"));
        }
        return n;
    }

    private static Index indexNamed(Table t, String name) {
        return t.indexes().stream().filter(ix -> nz(ix.name()).equalsIgnoreCase(nz(name))).findFirst().orElse(null);
    }

    /** R16 — PK·UNIQUE·CHECK(1-21 — 제약내용 = 딕셔너리 조건 글) */
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
            for (Check c : t.checks()) {
                rows.add(List.of(++n, nz(t.schema()), t.name(), nz(c.name()), "CHECK", nz(c.condition())));
            }
        }
        return new Doc("11", "제약조건 정의서", COLS_11, rows);
    }

    /** R3·R4·R5 — 02 와 03 이 같은 테이블에서 같은 값을 쓰도록 한 곳에서 만든다 */
    /**
     * 관리 열(R5, 2-13) — 최초등록일 = DB 생성 시각(없으면 빈칸) · 최종수정일·변경구분은 빈칸(사용자 2026-10-04 — 모르는 값을 확인된 값처럼
     * 안 보인다) · 최종수정자 = 작성자(비면 표시) · 담당부서 = 옵션 · 담당자 빈칸
     */
    static List<Object> tail(Table t, Options o) {
        String created = t.createdAt() == null ? "" : t.createdAt().format(DAY);
        return List.of(nz(o.dept()), "", created, "", blank(o.author()) ? NO_AUTHOR : o.author().trim(), "");
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
