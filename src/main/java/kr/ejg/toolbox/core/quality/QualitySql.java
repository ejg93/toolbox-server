package kr.ejg.toolbox.core.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 데이터 품질 진단 8종 SQL 생성(2-6). 원문은 순수본 90번 — {@code scripts/extract-quality.js} 가 뽑은 {@code /quality/d90.json}.
 * 자리표시자를 고른 이름으로 채울 뿐 실행·저장은 하지 않는다 — 실행은 화면이 1-7 실행기로, 결과는 화면에만(규칙 3, 순수본 메모).
 */
public final class QualitySql {

    public record Kind(String id, String title, Map<String, String> q) {
        public Kind {
            q = Map.copyOf(q);
        }
    }

    record Pack(String sub, List<Kind> kinds, List<String> notes) {
    }

    /** 채운 결과 — 남은 자리표시자(업무테이블·공통코드테이블·해당그룹 등)는 사람이 고친다 */
    public record Rendered(String id, String title, String dialect, String sql, List<String> notes) {
        public Rendered {
            notes = List.copyOf(notes);
        }
    }

    static final Pattern IDENT = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_$#@]*");
    private static final Pack PACK = load();

    private QualitySql() {
    }

    private static Pack load() {
        try (InputStream in = QualitySql.class.getResourceAsStream("/quality/d90.json")) {
            if (in == null) {
                throw new IllegalStateException("quality/d90.json 이 없다");
            }
            return new ObjectMapper().readValue(in, Pack.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Kind> kinds() {
        return List.copyOf(PACK.kinds());
    }

    public static List<String> notes() {
        return List.copyOf(PACK.notes());
    }

    /** 순수본 방언 키 — oracle·mysql·pg·mssql·sybase. Tibero 는 Oracle, MariaDB 는 MySQL 판 */
    public static String dialectKey(String dialect) {
        String d = dialect == null ? "" : dialect.trim().toLowerCase(Locale.ROOT);
        return switch (d) {
            case "oracle", "tibero" -> "oracle";
            case "mysql", "mariadb" -> "mysql";
            case "pg", "postgres", "postgresql" -> "pg";
            case "mssql", "sqlserver" -> "mssql";
            case "sybase" -> "sybase";
            default -> throw new IllegalArgumentException("모르는 방언: " + dialect);
        };
    }

    /**
     * @param keys    후보키 컬럼(keydup) — 앞 둘이 「후보키1·2」 자리
     * @param schemas 「__SCHEMAS__」 자리(nopk)
     */
    public static Rendered render(String id, String dialect, String schema, String table, String column, List<String> keys,
            List<String> schemas) {
        Kind k = PACK.kinds().stream().filter(x -> x.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("모르는 진단: " + id));
        String dk = dialectKey(dialect);
        String sql = k.q().get(dk);
        if (sql == null) {
            throw new IllegalArgumentException(k.title() + " 에 " + dk + " 판이 없다");
        }
        if (id.equals("datecheck")) {
            sql = spliceCase(sql, PACK.kinds().stream().filter(x -> x.id().equals("datefix")).findFirst().map(x -> x.q().get(dk)).orElse(null));
        }
        String qualified = blank(schema) ? name(table) : name(schema) + "." + name(table);
        // 긴 자리표시자부터 — 「스키마명.테이블명」 을 먼저, 「테이블명」 은 그 뒤
        if (!blank(table)) {
            sql = sql.replace("스키마명.테이블명", qualified).replace("테이블명", qualified);
        }
        if (!blank(column)) {
            sql = sql.replace("대상컬럼", name(column)).replace("코드컬럼", name(column));
        }
        List<String> ks = keys == null ? List.of() : keys;
        for (int i = 0; i < ks.size() && i < 2; i++) {
            sql = sql.replace("후보키" + (i + 1), name(ks.get(i)));
        }
        if (schemas != null && !schemas.isEmpty()) {
            List<String> lits = new ArrayList<>();
            schemas.forEach(s -> lits.add("'" + name(s) + "'"));
            sql = sql.replace("__SCHEMAS__", "(" + String.join(", ", lits) + ")");
        }
        return new Rendered(k.id(), k.title(), dk, sql, PACK.notes());
    }

    /**
     * [검증] 의 「CASE /* ← [정제] 항목의 CASE WHEN ~ END 통째로 붙여넣기 *&#47; END」 자리에 같은 방언 [정제] 의 CASE … END 를 넣는다.
     * 어느 한쪽 모양이 순수본과 달라 못 찾으면 원문 그대로(사람이 붙여 넣는다)
     */
    static String spliceCase(String check, String fix) {
        if (fix == null) {
            return check;
        }
        int hole = check.indexOf("CASE /*");
        int holeEnd = hole < 0 ? -1 : check.indexOf("*/ END", hole);
        int from = fix.indexOf("CASE");
        int to = from < 0 ? -1 : fix.indexOf("END AS 날짜_정규화", from);
        if (hole < 0 || holeEnd < 0 || from < 0 || to < 0) {
            return check;
        }
        return check.substring(0, hole) + fix.substring(from, to + "END".length()) + check.substring(holeEnd + "*/ END".length());
    }

    /** SQL 에 그대로 들어가는 이름 — 식별자 모양만(따옴표·세미콜론으로 문장을 바꾸지 못하게) */
    static String name(String s) {
        String t = s == null ? "" : s.trim();
        if (!IDENT.matcher(t).matches()) {
            throw new IllegalArgumentException("SQL 에 그대로 넣을 수 없는 이름: " + s);
        }
        return t;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
