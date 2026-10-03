package kr.ejg.toolbox.core.analyze;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import kr.ejg.toolbox.core.check.Source;
import kr.ejg.toolbox.core.text.SafeSax;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * MyBatis 매퍼 XML 색인(6-2) — {@code namespace.id} → 문장 태그·동사·테이블·CRUD. 방언 파일 여럿이 같은 ns.id 면 하나로 합친다(표 합집합).
 * <p>
 * 문장 글은 같은 요소 안 조각을 그대로 잇고(SAX 가 엔티티·CDATA 경계에서 자른다), 자식 요소 경계마다 줄바꿈 —
 * {@code FROM} 과 표 이름이 붙거나 끊기지 않게. {@code <when>}·{@code <otherwise>} 는 {@link SqlTables#BRANCH} 도 넣는다.
 * {@code <selectKey>} 는 제 글로 읽어 부모 문장에 R 로 합친다. {@code <sql>} 조각은 다 읽은 뒤 {@code <include>} 자리에 끼운다.
 * 저장하는 것은 식별자뿐 — SQL 글은 안 남긴다(규칙 3).
 */
public final class MapperIndex {

    /** selectKey — selectKey 글에서 읽은 표(화면이 흐리게 보일 수 있게) */
    public record Statement(String id, String tag, String verb, List<SqlTables.Ref> refs, Set<String> selectKey, List<String> files,
            String file, int line) {

        public Statement {
            refs = List.copyOf(refs);
            selectKey = Collections.unmodifiableSet(new TreeSet<>(selectKey));
            files = List.copyOf(files);
        }
    }

    public record Index(Map<String, Statement> statements, List<Unresolved> unresolved) {

        public Index {
            statements = Collections.unmodifiableMap(new LinkedHashMap<>(statements));
            unresolved = List.copyOf(unresolved);
        }

        public Statement get(String nsId) {
            return statements.get(nsId);
        }
    }

    private static final Set<String> STATEMENTS = Set.of("select", "insert", "update", "delete", "sql");
    static final char INC_OPEN = '\u0002';
    private static final char INC_CLOSE = '\u0003';

    /** 파일 하나 안 문장 하나(합치기 전) */
    record Raw(String ns, String id, String tag, String text, List<String> keys, String file, int line) {
    }

    private MapperIndex() {
    }

    public static Index scan(List<Source> xml) {
        List<Raw> raws = new ArrayList<>();
        Map<String, String> fragments = new HashMap<>(); // file + ns.id → 글, ns.id → 처음 본 글
        List<Unresolved> unresolved = new ArrayList<>();
        for (Source s : xml) {
            if (!s.text().contains("<mapper")) {
                continue;
            }
            Handler h = new Handler(s.rel());
            try {
                InputSource in = new InputSource(new StringReader(s.text()));
                in.setSystemId("mapper.xml");
                SafeSax.parse(in, h, true);
            } catch (SAXParseException e) {
                unresolved.add(new Unresolved("parse", s.rel(), Math.max(1, e.getLineNumber()), ""));
                continue;
            } catch (SAXException | IOException e) {
                unresolved.add(new Unresolved("parse", s.rel(), 1, ""));
                continue;
            }
            for (Raw r : h.out) {
                if (r.tag.equals("sql")) {
                    fragments.put(r.file + "|" + r.ns + "." + r.id, r.text);
                    fragments.putIfAbsent(r.ns + "." + r.id, r.text);
                } else {
                    raws.add(r);
                }
            }
        }

        // ns.id → 파일별 결과를 모아 합친다
        Map<String, List<Raw>> byId = new TreeMap<>();
        for (Raw r : raws) {
            byId.computeIfAbsent(r.ns + "." + r.id, k -> new ArrayList<>()).add(r);
        }
        Map<String, Statement> statements = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>(); // kind|detail — 방언마다 같은 미해결을 여덟 번 안 적는다
        for (Map.Entry<String, List<Raw>> e : byId.entrySet()) {
            String nsId = e.getKey();
            Map<String, EnumSet<SqlTables.Crud>> tables = new TreeMap<>();
            Map<String, Integer> fileCount = new TreeMap<>();
            Set<String> keyTables = new TreeSet<>();
            List<String> files = new ArrayList<>();
            String verb = null;
            Raw first = e.getValue().get(0);
            for (Raw r : e.getValue()) {
                files.add(r.file);
                String text = include(r, r.text, fragments, unresolved, seen, 0);
                SqlTables.Result res = SqlTables.extract(text, r.tag);
                if (verb == null) {
                    verb = res.verb();
                }
                Set<String> inFile = new TreeSet<>();
                for (SqlTables.Ref ref : res.refs()) {
                    tables.computeIfAbsent(ref.table(), k -> EnumSet.noneOf(SqlTables.Crud.class)).addAll(ref.crud());
                    inFile.add(ref.table());
                }
                for (String key : r.keys) {
                    for (SqlTables.Ref ref : SqlTables.extract(include(r, key, fragments, unresolved, seen, 0), "select").refs()) {
                        tables.computeIfAbsent(ref.table(), k -> EnumSet.noneOf(SqlTables.Crud.class)).add(SqlTables.Crud.R);
                        keyTables.add(ref.table());
                        inFile.add(ref.table());
                    }
                }
                inFile.forEach(t -> fileCount.merge(t, 1, Integer::sum));
                for (String u : res.unresolved()) {
                    note(unresolved, seen, u, r.file, r.line, nsId);
                }
            }
            int n = e.getValue().size();
            fileCount.forEach((t, c) -> {
                if (c != n) {
                    note(unresolved, seen, "dialect", first.file, first.line, nsId + " " + t);
                }
            });
            List<SqlTables.Ref> refs = new ArrayList<>();
            tables.forEach((t, c) -> refs.add(new SqlTables.Ref(t, c)));
            statements.put(nsId, new Statement(nsId, first.tag, verb, refs, keyTables, files, first.file, first.line));
        }
        return new Index(statements, unresolved);
    }

    private static void note(List<Unresolved> out, Set<String> seen, String kind, String file, int line, String detail) {
        if (seen.add(kind + "|" + detail)) {
            out.add(new Unresolved(kind, file, line, detail));
        }
    }

    /** {@code <include refid>} 자리에 조각 — 점이 없으면 같은 namespace. 같은 파일 조각을 먼저 */
    static String include(Raw r, String text, Map<String, String> fragments, List<Unresolved> unresolved, Set<String> seen, int depth) {
        if (text.indexOf(INC_OPEN) < 0) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int a = text.indexOf(INC_OPEN, i);
            if (a < 0) {
                sb.append(text, i, text.length());
                break;
            }
            int b = text.indexOf(INC_CLOSE, a);
            if (b < 0) { // 핸들러가 늘 짝으로 넣는다 — 깨진 표시면 남은 글을 그대로
                sb.append(text, i, text.length());
                break;
            }
            sb.append(text, i, a);
            String refid = text.substring(a + 1, b);
            String key = refid.contains(".") ? refid : r.ns + "." + refid;
            String frag = fragments.get(r.file + "|" + key);
            if (frag == null) {
                frag = fragments.get(key);
            }
            if (frag == null || depth > 5) {
                note(unresolved, seen, "missing", r.file, r.line, "include " + key);
            } else {
                sb.append('\n').append(include(r, frag, fragments, unresolved, seen, depth + 1)).append('\n');
            }
            i = b + 1;
        }
        return sb.toString();
    }

    private static final class Handler extends DefaultHandler {
        final String file;
        final List<Raw> out = new ArrayList<>();
        Locator loc;
        boolean mapper;
        String ns = "";
        // 문장
        int depth;
        String id;
        String tag;
        int line;
        StringBuilder text;
        List<String> keys;
        // selectKey
        int keyDepth;
        StringBuilder key;

        Handler(String file) {
            this.file = file;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.loc = locator;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {
            if (qName.equals("mapper") && depth == 0) {
                mapper = true;
                ns = atts.getValue("namespace") == null ? "" : atts.getValue("namespace");
                return;
            }
            if (!mapper) {
                return;
            }
            if (depth == 0) {
                if (STATEMENTS.contains(qName) && atts.getValue("id") != null) {
                    depth = 1;
                    id = atts.getValue("id");
                    tag = qName;
                    line = loc == null ? 1 : loc.getLineNumber();
                    text = new StringBuilder();
                    keys = new ArrayList<>();
                }
                return;
            }
            depth++;
            if (keyDepth > 0) {
                keyDepth++;
                key.append('\n');
                return;
            }
            StringBuilder b = text;
            switch (qName) {
                case "selectKey" -> {
                    keyDepth = 1;
                    key = new StringBuilder();
                }
                case "include" -> {
                    String refid = atts.getValue("refid");
                    if (refid != null) {
                        b.append(INC_OPEN).append(refid).append(INC_CLOSE);
                    }
                }
                case "when", "otherwise" -> b.append('\n').append(SqlTables.BRANCH).append('\n');
                case "where" -> b.append("\nWHERE\n");
                case "set" -> b.append("\nSET\n");
                case "trim" -> b.append('\n').append(atts.getValue("prefix") == null ? "" : atts.getValue("prefix")).append('\n');
                default -> b.append('\n');
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (depth == 0) {
                return;
            }
            depth--;
            if (keyDepth > 0) {
                keyDepth--;
                if (keyDepth == 0) {
                    keys.add(key.toString());
                    key = null;
                } else {
                    key.append('\n');
                }
                return;
            }
            if (depth == 0) {
                out.add(new Raw(ns, id, tag, text.toString(), keys, file, line));
                text = null;
                return;
            }
            text.append('\n');
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (depth == 0) {
                return;
            }
            (keyDepth > 0 ? key : text).append(ch, start, length);
        }
    }
}
