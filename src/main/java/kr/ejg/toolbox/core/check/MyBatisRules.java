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
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
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
    private static final Set<String> STATEMENTS = Set.of("select", "insert", "update", "delete", "sql");
    private static final Pattern DOLLAR = Pattern.compile("\\$\\{");
    /** 페이징 래퍼 `SELECT * FROM (` 는 뺀다(eGov 실측 604 파일 전부 이 꼴) */
    private static final Pattern SELECT_STAR = Pattern.compile("(?i)\\bSELECT\\s+\\*(?!\\s*FROM\\s*\\()");
    private static final Pattern TYPE = Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    /** DAO 꼴(eGov EgovAbstractMapper) 문장 참조 `selectList("Ns.stmt")` — 문자열 리터럴 전체가 점 이은 식별자(V-11 실측) */
    private static final Pattern STATEMENT_REF = Pattern.compile("\"([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)+)\"");

    private final Map<String, Rule.Def> on = new LinkedHashMap<>();
    private final SAXParserFactory factory;
    private final Set<String> javaTypes = new HashSet<>();
    private final Set<String> javaFqcn = new HashSet<>();
    /** Java 문자열 리터럴 `"Ns.stmt"` 의 Ns */
    private final Set<String> javaRefs = new HashSet<>();
    private final List<Finding> namespaces = new ArrayList<>();
    private final Map<Finding, String> namespaceOf = new HashMap<>();

    MyBatisRules(List<Rule.Def> defs) {
        defs.forEach(d -> on.put(d.id(), d));
        factory = SAXParserFactory.newInstance();
        try {
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            factory.setXIncludeAware(false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("SAX 설정 실패", e);
        }
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
            javaRefs.add(ref.substring(0, ref.lastIndexOf('.')));
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
            SAXParser parser = factory.newSAXParser();
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            InputSource in = new InputSource(new StringReader(s.text()));
            in.setSystemId("mapper.xml");
            parser.parse(in, h);
        } catch (SAXParseException e) {
            out.clear();
            if (on.containsKey(PARSE_ERROR)) {
                out.add(finding(PARSE_ERROR, s, Math.max(1, e.getLineNumber()), Finding.excerpt(String.valueOf(e.getMessage()))));
            }
        } catch (SAXException | ParserConfigurationException | IOException e) {
            out.clear();
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
                mapper = qName.equals("mapper");
                String ns = atts.getValue("namespace");
                if (mapper && ns != null && !ns.isBlank() && on.containsKey("mybatis.namespace")) {
                    Finding f = finding("mybatis.namespace", s, loc.getLineNumber(), Finding.excerpt(ns));
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
            } else if (STATEMENTS.contains(qName)) {
                depth = 1;
                String id = atts.getValue("id");
                if (id != null && on.containsKey("mybatis.dupId")) {
                    int line = loc.getLineNumber();
                    if (ids.putIfAbsent(id, line) != null) {
                        out.add(finding("mybatis.dupId", s, line, excerpt(line)));
                    }
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (depth > 0) {
                depth--;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (!mapper || depth == 0) {
                return;
            }
            String text = new String(ch, start, length);
            String sql = RegexRule.stripComments(text, "sql");
            int endLine = loc.getLineNumber();
            check("mybatis.dollar", DOLLAR, sql, endLine);
            check("mybatis.selectStar", SELECT_STAR, sql, endLine);
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

        private String excerpt(int line) {
            return Finding.excerpt(line - 1 < raw.length ? raw[line - 1] : "");
        }
    }
}
