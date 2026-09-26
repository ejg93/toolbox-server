package kr.ejg.toolbox.core.deliverable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.sqlrun.ResultTable;
import kr.ejg.toolbox.core.sqlrun.SqlRunner;

/**
 * 산출물 08 표준코드·09 연계(2-3). 순수본은 두 단계 SQL 을 사람이 고쳐 돌렸다 — 여기선 1단계(후보)를 스냅샷으로 판정하고,
 * 2단계(코드값)는 사람이 고른 표·컬럼으로 SELECT 를 만들어 {@link SqlRunner} 로 돈다. 결과는 문서로만 간다 — H2·로그 없음(규칙 3).
 */
public final class CodeAndLink {

    /** 공통코드 표 후보 + 이름 패턴으로 고른 컬럼(화면에서 바꾼다). 없으면 null */
    public record CodeTable(String schema, String table, String comment, String groupCol, String codeCol, String nameCol, String descCol,
            String useCol, String sortCol) {
    }

    /** 09 후보 — kind: 테이블·뷰·DB링크 */
    public record LinkCandidate(String kind, String schema, String name, String comment, String reason, String items) {
    }

    public record Options(String org, String dept) {
    }

    /** SQL 에 그대로 들어가는 이름 — 따옴표 없이 쓸 수 있는 식별자만(번들 4 리뷰와 같은 결) */
    static final Pattern IDENT = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_$#@]*");
    static final int MAX_ROWS = 20_000;

    static final List<String> COLS_08 = List.of("순번", "기관명", "DB명", "관리부서명", "코드명(한글)", "코드명(영문)", "데이터타입", "데이터길이",
            "코드값", "코드값의미", "코드값설명", "제정일자", "사용여부", "특이사항");
    static final List<String> COLS_09 = List.of("순번", "송/수신", "연계정보명", "주기", "방식", "연계항목", "제공기관·시스템명", "활용기관·시스템명",
            "연계기간", "비고");

    private CodeAndLink() {
    }

    // ── 08 ──────────────────────────────────────────────────────────────────

    /** 순수본 1단계 — 이름에 CD·CODE·COMM 또는 코멘트에 「코드」, 그리고 코드·코드명 컬럼 짝이 보이는 표 */
    public static List<CodeTable> codeCandidates(List<Schema> schemas) {
        List<CodeTable> out = new ArrayList<>();
        for (Table t : Definitions.sorted(schemas)) {
            String n = t.name().toUpperCase(Locale.ROOT);
            boolean byName = n.contains("CD") || n.contains("CODE") || n.contains("COMM");
            boolean byComment = t.comment() != null && t.comment().contains("코드");
            if (!(byName || byComment) || t.columns().size() < 2) {
                continue;
            }
            List<String> codes = new ArrayList<>();
            String name = null;
            String desc = null;
            String use = null;
            String sort = null;
            for (Column c : t.columns()) {
                String u = c.name().toUpperCase(Locale.ROOT);
                if (u.endsWith("_YN") || u.startsWith("USE")) {
                    use = use == null ? c.name() : use;
                } else if (u.endsWith("CD") || u.endsWith("CODE")) {
                    codes.add(c.name());
                } else if (u.endsWith("NM") || u.endsWith("NAME")) {
                    name = name == null ? c.name() : name;
                } else if (u.contains("DESC") || u.endsWith("_DC") || u.contains("EXPLN") || u.endsWith("_CN")) {
                    desc = desc == null ? c.name() : desc;
                } else if (u.contains("SORT") || u.contains("ORD") || u.contains("SEQ")) {
                    sort = sort == null ? c.name() : sort;
                }
            }
            if (codes.isEmpty() || name == null) {
                continue;
            }
            String group = null;
            String code = codes.get(codes.size() - 1);
            if (codes.size() > 1) {
                group = codes.stream().filter(x -> x.toUpperCase(Locale.ROOT).matches(".*(GROUP|GRP|UPPER|UP_|PARENT|PRNT).*")).findFirst()
                        .orElse(codes.get(0));
                String g = group;
                code = codes.stream().filter(x -> !x.equals(g)).reduce((a, b) -> b).orElse(code);
            }
            out.add(new CodeTable(t.schema(), t.name(), t.comment(), group, code, name, desc, use, sort));
        }
        return out;
    }

    /** 순수본 2단계 틀을 고른 컬럼으로 채운 SELECT. 이름이 식별자 모양이 아니면 예외 */
    static String codeSql(CodeTable t) {
        List<String> cols = new ArrayList<>();
        for (String c : new String[] {t.groupCol(), t.codeCol(), t.nameCol(), t.descCol(), t.useCol()}) {
            if (c != null && !c.isBlank()) {
                cols.add(ident(c));
            }
        }
        List<String> order = new ArrayList<>();
        if (t.groupCol() != null && !t.groupCol().isBlank()) {
            order.add(ident(t.groupCol()));
        }
        order.add(t.sortCol() != null && !t.sortCol().isBlank() ? ident(t.sortCol()) : ident(t.codeCol()));
        String from = (t.schema() == null || t.schema().isBlank() ? "" : ident(t.schema()) + ".") + ident(t.table());
        return "SELECT " + String.join(", ", cols) + " FROM " + from + " ORDER BY " + String.join(", ", order);
    }

    static String ident(String s) {
        String t = s == null ? "" : s.trim();
        if (!IDENT.matcher(t).matches()) {
            throw new IllegalArgumentException("SQL 에 그대로 넣을 수 없는 이름: " + s);
        }
        return t;
    }

    /** 고른 표마다 코드값을 읽어 08 행으로. 표마다 {@link #MAX_ROWS} 까지 — 넘으면 그 표 첫 행 특이사항에 적는다 */
    public static Doc codeDoc(Connection conn, List<CodeTable> picks, Options o) throws SQLException {
        List<List<Object>> rows = new ArrayList<>();
        int n = 0;
        for (CodeTable t : picks) {
            ResultTable r = SqlRunner.run(conn, codeSql(t), List.of(), MAX_ROWS, SqlRunner.DEFAULT_TIMEOUT_SEC);
            boolean group = t.groupCol() != null && !t.groupCol().isBlank();
            int i = 0;
            int codeIdx = group ? 1 : 0;
            String type = r.columns().size() > codeIdx ? r.columns().get(codeIdx).type() : "";
            for (List<Object> v : r.rows()) {
                int k = 0;
                Object grp = group ? v.get(k++) : null;
                Object code = v.get(k++);
                Object name = v.get(k++);
                Object desc = t.descCol() != null && !t.descCol().isBlank() ? v.get(k++) : "";
                Object use = t.useCol() != null && !t.useCol().isBlank() ? v.get(k) : "";
                String note = i++ == 0 && r.truncated() ? MAX_ROWS + "행까지만 — 나머지는 직접 조회" : "";
                rows.add(List.of(++n, nz(o.org()), nz(t.schema()), nz(o.dept()), nz(t.comment()), group ? str(grp) : t.table(), nz(type), "",
                        str(code), str(name), str(desc), "", str(use), note));
            }
        }
        return new Doc("08", "DB 표준코드", COLS_08, rows);
    }

    // ── 09 ──────────────────────────────────────────────────────────────────

    /** 순수본 단서 2·3 — 이름 IF·_IF_·RCV·SND 또는 코멘트 연계·수신·송신 인 표, 그리고 뷰 전부 */
    public static List<LinkCandidate> linkCandidates(List<Schema> schemas) {
        List<LinkCandidate> out = new ArrayList<>();
        for (Table t : Definitions.sorted(schemas)) {
            String n = t.name().toUpperCase(Locale.ROOT);
            String c = t.comment() == null ? "" : t.comment();
            boolean view = t.type() != null && t.type().toUpperCase(Locale.ROOT).contains("VIEW");
            List<String> why = new ArrayList<>();
            if (n.startsWith("IF") || n.contains("_IF_")) {
                why.add("이름 IF");
            }
            if (n.contains("RCV")) {
                why.add("이름 RCV");
            }
            if (n.contains("SND")) {
                why.add("이름 SND");
            }
            for (String w : List.of("연계", "수신", "송신")) {
                if (c.contains(w)) {
                    why.add("코멘트 " + w);
                }
            }
            if (view) {
                why.add("뷰 — 뷰 개방 방식 연계일 수 있다");
            }
            if (!why.isEmpty()) {
                out.add(new LinkCandidate(view ? "뷰" : "테이블", t.schema(), t.name(), t.comment(), String.join(", ", why), items(t)));
            }
        }
        return out;
    }

    /** 연계항목 칸 — 컬럼(타입·길이) 목록. 순수본 메모: 인터페이스 표가 정해지면 03 쿼리를 그 표로 좁혀 채운다 */
    static String items(Table t) {
        List<String> out = new ArrayList<>();
        for (Column c : t.columns()) {
            String len = Definitions.length(c);
            out.add(c.name() + "(" + nz(c.nativeType()) + (len.isEmpty() ? "" : " " + len) + ")");
        }
        return String.join(", ", out);
    }

    /** 순수본 단서 1 — DB링크·연결서버 조회. 개념이 없는 방언은 null */
    public static String dbLinkSql(String dialect) {
        String d = dialect == null ? "" : dialect.toLowerCase(Locale.ROOT);
        return switch (d) {
            case "oracle", "tibero" -> "SELECT OWNER, DB_LINK, HOST FROM ALL_DB_LINKS";
            case "postgresql", "postgres", "pg" -> "SELECT srvname, array_to_string(srvoptions, ',') FROM pg_foreign_server";
            case "mssql", "sqlserver" -> "SELECT name, data_source FROM sys.servers WHERE is_linked = 1";
            case "mariadb", "mysql" -> "SELECT TABLE_SCHEMA, TABLE_NAME FROM information_schema.TABLES WHERE ENGINE = 'FEDERATED'";
            default -> null;
        };
    }

    /** DB링크 조회 결과 → 후보. 첫 열이 이름, 나머지는 비고 */
    public static List<LinkCandidate> dbLinks(ResultTable r) {
        List<LinkCandidate> out = new ArrayList<>();
        for (List<Object> v : r.rows()) {
            List<String> rest = new ArrayList<>();
            for (int i = 1; i < v.size(); i++) {
                rest.add(str(v.get(i)));
            }
            out.add(new LinkCandidate("DB링크", "", str(v.get(0)), "", String.join(" · ", rest), ""));
        }
        return out;
    }

    /** 09 — DB 에서 얻는 건 단서까지. 주기·방식·송수신·기관은 수기(순수본 메모). 이름의 RCV·SND 만 송/수신 초안 */
    public static Doc linkDoc(List<LinkCandidate> cands) {
        List<List<Object>> rows = new ArrayList<>();
        int n = 0;
        for (LinkCandidate c : cands) {
            String u = c.name().toUpperCase(Locale.ROOT);
            String dir = u.contains("RCV") ? "수신" : u.contains("SND") ? "송신" : "";
            String how = c.kind().equals("DB링크") ? "DB링크" : c.kind().equals("뷰") ? "뷰" : "";
            String title = c.comment() == null || c.comment().isBlank() ? c.name() : c.comment();
            rows.add(List.of(++n, dir, title, "", how, nz(c.items()), "", "", "",
                    (c.schema() == null || c.schema().isEmpty() ? "" : c.schema() + ".") + c.name() + " — " + c.reason()));
        }
        return new Doc("09", "연계데이터 목록 정의서", COLS_09, rows);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
