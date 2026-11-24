package kr.ejg.toolbox.core.check;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.text.SafeSax;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * MyBatis XML 묶음 ㄷ(5-3). {@code <mapper>} 가 든 XML 만 본다(iBATIS {@code <sqlMap>} 은 아님). 줄 번호가 필요해 JDK SAX 로 읽는다 —
 * 외부 DTD·엔티티를 안 읽는다(XXE, 절대 규칙 1). 문장 요소(select·insert·update·delete·sql) 안의 글(텍스트·CDATA — XML 주석은 SAX 가 안 준다)에서
 * SQL 주석을 뗀 뒤 잰다. {@code mybatis.namespace} 는 파일 사이 상태 — 같은 실행에서 본 Java 소스의 타입 이름·경로와 견주고
 * {@link #finish()} 에서 낸다. Java 소스를 하나도 못 봤으면(붙여넣기·XML 만 있는 폴더) 재지 않는다.
 */
public final class MyBatisRules {

    public static final String PARSE_ERROR = "mybatis.parseError";
    private static final String SQL = "sql";
    private static final Set<String> STATEMENTS = Set.of("select", "insert", "update", "delete", SQL);
    private static final Pattern DOLLAR = Pattern.compile("\\$\\{");
    /** 페이징 래퍼 `SELECT * FROM (` 는 뺀다(eGov 실측 604 파일 전부 이 꼴) */
    private static final Pattern SELECT_STAR = Pattern.compile("(?i)\\bSELECT\\s+\\*(?!\\s*FROM\\s*\\()");
    /** WHERE 없는 쓰기(5-19) — 리터럴을 비운 문장의 첫 낱말과 WHERE */
    private static final Pattern FIRST_WORD = Pattern.compile("^\\s*(\\w+)");
    private static final Pattern WHERE = Pattern.compile("(?i)\\bWHERE\\b");
    private static final Pattern LITERAL = Pattern.compile("'[^']*'");
    private static final Pattern TRIM_WHERE = Pattern.compile("(?i)^\\s*where\\b");
    private static final Pattern TYPE = Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^[ \\t]*+package\\s+([\\w.]+)\\s*;"); // 5-133 — 줄머리 공백 수량자가 줄바꿈까지 먹으면 빈 줄 반복에 제곱
    /** DAO 꼴(eGov EgovAbstractMapper) 문장 참조 `selectList("Ns.stmt")` — 문자열 리터럴 전체가 점 이은 식별자(V-11 실측) */
    private static final Pattern STATEMENT_REF = Pattern.compile("\"([A-Za-z_$][\\w$.]*)\""); // 중첩 반복 없이(ReDoS 판정) — 점 모양은 코드가 잰다

    /** 구문을 못 읽은 파일(rel) — 규칙 켬·끔·표식과 무관(D-parseErrCount). RuleSet.Run 이 바로 읽는다(JavaRules 메서드 상한) */
    final java.util.Set<String> unparsed = new java.util.HashSet<>();
    private final Map<String, Rule.Def> on = new LinkedHashMap<>();
    private final Set<String> javaTypes = new HashSet<>();
    private final Set<String> javaFqcn = new HashSet<>();
    /** Java 문자열 리터럴 `"Ns.stmt"` 의 Ns */
    private final Set<String> javaRefs = new HashSet<>();
    private final List<Finding> namespaces = new ArrayList<>();
    private final Map<Finding, String> namespaceOf = new HashMap<>();

    MyBatisRules(List<Rule.Def> defs) {
        defs.forEach(d -> on.put(d.id(), d));
    }

    /** Java 소스 — namespace 대조용 타입 이름과 FQCN 만 모은다 */
    void seeJava(Source s) {
        String code = RegexRule.stripComments(s.text(), "java");
        Matcher p = PACKAGE.matcher(code);
        String pkg = p.find() ? p.group(1) + "." : "";
        Matcher m = TYPE.matcher(code);
        while (m.find()) {
            javaTypes.add(m.group(1));
            javaFqcn.add(pkg + m.group(1));
        }
        Matcher r = STATEMENT_REF.matcher(code);
        while (r.find()) {
            String ref = r.group(1);
            int dot = ref.lastIndexOf('.');
            if (dot > 0 && dot < ref.length() - 1 && !ref.contains("..")) {
                javaRefs.add(ref.substring(0, dot));
            }
        }
    }

    List<Finding> apply(Source s) {
        List<Finding> out = new ArrayList<>();
        if (!s.text().contains("<mapper")) {
            return out;
        }
        String[] raw = s.text().split("\n", -1);
        Handler h = new Handler(s, raw, out);
        try {
            InputSource in = new InputSource(new StringReader(s.text()));
            in.setSystemId("mapper.xml");
            SafeSax.parse(in, h, true);
        } catch (SAXParseException e) {
            out.clear();
            unparsed.add(s.rel()); // D-parseErrCount
            if (on.containsKey(PARSE_ERROR)) {
                out.add(finding(PARSE_ERROR, s, Math.max(1, e.getLineNumber()), Finding.excerpt(String.valueOf(e.getMessage()))));
            }
        } catch (SAXException | IOException e) {
            out.clear();
            unparsed.add(s.rel());
            if (on.containsKey(PARSE_ERROR)) {
                out.add(finding(PARSE_ERROR, s, 1, Finding.excerpt(String.valueOf(e.getMessage()))));
            }
        }
        out.sort(java.util.Comparator.comparingInt(Finding::line).thenComparing(Finding::rule));
        return out;
    }

    /** namespace 가 본 Java 타입(점이 있으면 FQCN, 없으면 단순 이름)에도, Java 문장 참조 `"ns.문장"` 에도 없으면 */
    List<Finding> finish() {
        List<Finding> out = new ArrayList<>();
        if (!javaTypes.isEmpty()) {
            for (Finding f : namespaces) {
                String ns = namespaceOf.get(f);
                boolean found = (ns.contains(".") ? javaFqcn.contains(ns) : javaTypes.contains(ns)) || javaRefs.contains(ns);
                if (!found) {
                    out.add(f);
                }
            }
        }
        namespaces.clear();
        namespaceOf.clear();
        return out;
    }

    private Finding finding(String rule, Source s, int line, String excerpt) {
        Rule.Def d = on.get(rule);
        return new Finding(s.rel(), line, d.group(), rule, d.severity(), excerpt);
    }

    private final class Handler extends DefaultHandler {
        private final Source s;
        private final String[] raw;
        private final List<Finding> out;
        private Locator loc;
        private boolean mapper;
        private boolean root = true;
        private int depth;
        private final Map<String, Integer> ids = new HashMap<>();
        /** 문장 하나의 상태 — WHERE 없는 쓰기(5-19). 시작 줄·주석 지운 글·include(자식 where 는 글에 WHERE 로 든다 — 5-133) */
        private int stmtLine;
        private StringBuilder stmtText;
        private boolean include;
        /** 5-151(D4-06) — 문장에 where·trim prefix=WHERE 태그가 있었나 */
        private boolean whereTag;
        /** 문장 요소 이름(select·insert·update·delete·sql) — sql 조각은 문장 단위 판정을 안 한다(5-57c) */
        private String stmtTag;
        /** 글 조각마다 {stmtText 안 시작 위치, 그 자리의 줄} — 자식 태그가 넣은 줄바꿈은 실제 줄이 아니라 이것으로 줄을 잰다 */
        private final List<int[]> segs = new ArrayList<>();

        Handler(Source s, String[] raw, List<Finding> out) {
            this.s = s;
            this.raw = raw;
            this.out = out;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.loc = locator;
        }

        /** 외부 DTD 를 안 읽는다 — 빈 글을 돌려준다 */
        @Override
        public InputSource resolveEntity(String publicId, String systemId) {
            return new InputSource(new StringReader(""));
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {
            if (root) {
                root = false;
                mapper = "mapper".equals(qName);
                String ns = atts.getValue("namespace");
                if (mapper && ns != null && !ns.isBlank() && on.containsKey("mybatis.namespace")) {
                    Finding f = finding("mybatis.namespace", s, tagLine(qName), Finding.excerpt(ns));
                    namespaces.add(f);
                    namespaceOf.put(f, ns.trim());
                }
                return;
            }
            if (!mapper) {
                return;
            }
            if (depth > 0) {
                depth++;
                stmtText.append('\n');
                if ("where".equals(qName) || "trim".equals(qName) && atts.getValue("prefix") != null
                        && TRIM_WHERE.matcher(atts.getValue("prefix")).find()) {
                    whereTag = true;
                    stmtText.append("WHERE\n"); // 태그가 붙이는 WHERE — 매퍼 SQL 판정(nullCompare 조건 절)이 보게(마무리 리뷰)
                } else if ("include".equals(qName)) {
                    include = true;
                }
            } else if (STATEMENTS.contains(qName)) {
                depth = 1;
                stmtLine = tagLine(qName);
                stmtText = new StringBuilder();
                include = false;
                whereTag = false;
                stmtTag = qName;
                segs.clear();
                String id = atts.getValue("id");
                if (id != null && on.containsKey("mybatis.dupId")) {
                    int line = tagLine(qName);
                    if (ids.putIfAbsent(idKey(qName, atts), line) != null) { // D-dupId — sql 조각·databaseId 변형은 이름 공간이 따로
                        out.add(finding("mybatis.dupId", s, line, excerpt(line)));
                    }
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (depth > 0) {
                depth--;
                if (depth == 0) {
                    closeStatement();
                } else {
                    stmtText.append('\n');
                }
            }
        }

        /**
         * 5-133(D-06) — 시작 태그의 머리 줄. SAX Locator 는 시작 태그의 {@code >} 줄이라 여러 줄 태그면 위로 올라가며 {@code <이름} 이 든 첫 줄
         * (끝 줄은 Locator 칸 앞까지). 못 찾으면 Locator 줄
         */
        private int tagLine(String qName) {
            int end = loc.getLineNumber();
            String open = "<" + qName;
            for (int line = end; line >= 1; line--) {
                String text = line - 1 < raw.length ? raw[line - 1] : "";
                String upto = line == end ? text.substring(0, Math.min(text.length(), Math.max(0, loc.getColumnNumber() - 1))) : text;
                if (upto.contains(open)) {
                    return line;
                }
            }
            return end;
        }

        /** 문장이 닫힐 때 — 문장 전체에서 SQL 주석을 한 번 지우고(길이 그대로 — 위치·줄이 안 밀린다) 판정 셋(D-dollarFragment) */
        private void closeStatement() {
            String sql = RegexRule.stripComments(stmtText.toString(), SQL);
            selectStar(sql);
            noWhere(sql);
            sqlLint(sql);
        }

        /** SELECT * — 맞은 자리의 줄에 한 건(한 줄에 한 건). 문장 전체에서 보니 엔티티·CDATA 조각 경계에 안 끊긴다 */
        private void selectStar(String sql) {
            if (!on.containsKey("mybatis.selectStar")) {
                return;
            }
            Matcher m = SELECT_STAR.matcher(sql);
            Set<Integer> lines = new HashSet<>();
            while (m.find()) {
                int line = lineAt(sql, m.start());
                if (lines.add(line)) {
                    out.add(finding("mybatis.selectStar", s, line, excerpt(line)));
                }
            }
        }

        /** 문장이 닫힐 때 — 첫 낱말이 UPDATE·DELETE 인데 괄호 밖 WHERE(글자·where·trim prefix)가 없고 include 도 없으면 시작 줄에 한 건 */
        private void noWhere(String text) {
            if (!on.containsKey("mybatis.noWhere") || SQL.equals(stmtTag) || include) { // 5-133 — <sql> 조각은 문장이 아니다 · <where> 는 WHERE 글로 topWhere 가 잰다
                return;
            }
            String sql = LITERAL.matcher(text).replaceAll("''");
            Matcher m = FIRST_WORD.matcher(sql);
            if (!m.find()) {
                return;
            }
            String verb = m.group(1).toUpperCase(java.util.Locale.ROOT);
            if (("UPDATE".equals(verb) || "DELETE".equals(verb)) && !whereFound(sql)) {
                out.add(finding("mybatis.noWhere", s, stmtLine, excerpt(stmtLine)));
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (!mapper || depth == 0) {
                return;
            }
            String text = new String(ch, start, length);
            int endLine = loc.getLineNumber();
            segs.add(new int[] {stmtText.length(), Math.max(1, endLine - newlines(text, 0, text.length()))});
            stmtText.append(text); // D-dollarFragment — 원문 조각을 쌓고 주석은 문장이 닫힐 때 한 번 지운다
            check("mybatis.dollar", DOLLAR, text, endLine); // 5-119 — SQL 주석 안 ${} 도(MyBatis 가 치환한다)
        }

        /** 5-151(D4-06) — 괄호 밖 WHERE · 글의 괄호가 끝에서 안 맞으면(속성·갈래 괄호) 깊이를 못 믿으니 태그를 믿는다 */
        private boolean whereFound(String sql) {
            return topWhere(sql) || whereTag && depthAtEnd(sql) != 0;
        }

        private static int depthAtEnd(String sql) {
            int depth = 0;
            for (int i = 0; i < sql.length(); i++) {
                char c = sql.charAt(i);
                depth += c == '(' ? 1 : c == ')' ? -1 : 0;
            }
            return depth;
        }

        /** 괄호 밖(깊이 0) WHERE 가 있나 — 서브쿼리 안 WHERE 는 바깥 갱신을 좁히지 않는다(D-noWhereSubquery) */
        private static boolean topWhere(String sql) {
            Matcher m = WHERE.matcher(sql);
            while (m.find()) {
                int depth = 0;
                for (int i = 0; i < m.start(); i++) {
                    char c = sql.charAt(i);
                    depth += c == '(' ? 1 : c == ')' ? -1 : 0;
                }
                if (depth <= 0) {
                    return true;
                }
            }
            return false;
        }

        /** dupId 열쇠 — sql 조각은 따로, databaseId 가 있으면 그것까지(D-dupId) */
        private static String idKey(String tag, Attributes a) {
            return (SQL.equals(tag) ? "sql " : "") + a.getValue("id") + (a.getValue("databaseId") == null ? "" : " @" + a.getValue("databaseId"));
        }

        /** 문장이 닫힐 때 — 매퍼 SQL 판정 다섯(5-57c, {@link SqlLint}). include 가 든 문장·sql 조각은 문장 전체를 몰라 partial. 한 줄에 규칙마다 한 건 */
        private void sqlLint(String text) {
            if (java.util.Arrays.stream(SqlLint.Kind.values()).noneMatch(k -> on.containsKey(k.rule()))) {
                return;
            }
            Set<String> seen = new HashSet<>();
            for (SqlLint.Hit h : SqlLint.check(text, include || SQL.equals(stmtTag))) {
                String rule = h.kind().rule();
                int line = lineAt(text, h.at());
                if (on.containsKey(rule) && seen.add(rule + ":" + line)) {
                    out.add(finding(rule, s, line, excerpt(line)));
                }
            }
        }

        /** stmtText 위치 → 원문 줄 — 그 위치를 품은 글 조각의 시작 줄 + 조각 안 줄바꿈 수 */
        private int lineAt(String text, int pos) {
            int[] seg = {0, stmtLine};
            for (int[] g : segs) {
                if (g[0] <= pos) {
                    seg = g;
                }
            }
            return seg[1] + newlines(text, seg[0], pos);
        }

        /** 조각 끝 줄에서 뒤의 줄바꿈 수만큼 거슬러 올라가 맞은 자리의 줄을 얻는다. 한 줄에 한 건 */
        private void check(String rule, Pattern p, String sql, int endLine) {
            if (!on.containsKey(rule)) {
                return;
            }
            Matcher m = p.matcher(sql);
            int last = -1;
            while (m.find()) {
                int after = 0;
                for (int i = m.start(); i < sql.length(); i++) {
                    if (sql.charAt(i) == '\n') {
                        after++;
                    }
                }
                int line = Math.max(1, endLine - after);
                if (line != last) {
                    out.add(finding(rule, s, line, excerpt(line)));
                    last = line;
                }
            }
        }

        private int newlines(String text, int from, int to) {
            int n = 0;
            for (int i = from; i < to; i++) {
                if (text.charAt(i) == '\n') {
                    n++;
                }
            }
            return n;
        }

        private String excerpt(int line) {
            return Finding.excerpt(line - 1 < raw.length ? raw[line - 1] : "");
        }
    }
}
