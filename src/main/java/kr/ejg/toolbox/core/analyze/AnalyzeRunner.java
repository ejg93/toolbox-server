package kr.ejg.toolbox.core.analyze;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import kr.ejg.toolbox.core.check.Source;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.job.JobContext;
import kr.ejg.toolbox.core.profile.Profile;

/**
 * 프로그램 분석 한 번(6-4) — 폴더의 {@code *.java} 는 {@link JavaGraph}, {@code *.xml} 은 {@link MapperIndex}, {@code *.jsp} 는
 * {@link JspLinks}(6-6) 로 읽고 프로그램마다 문장 참조를 색인에 맞춰 테이블·CRUD 를 합친다. 색인에 없는 ns.id 는 {@code missing}. 글은 메모리에서만 쓴다(규칙 3).
 */
public final class AnalyzeRunner {

    static final int PROGRESS_EVERY = 50;

    /** 프로그램 하나와 그 표 → 글자(정렬) */
    public record Row(JavaGraph.Program program, Map<String, String> crud) {

        public Row {
            crud = java.util.Collections.unmodifiableMap(new TreeMap<>(crud));
        }
    }

    /** files — 읽은 파일 수, skipped — 못 읽은 파일, truncated — 목록 상한, statements — 색인 문장 수 */
    /** 아무도 안 가리키는 것(6-11) — kind {@code statement}(매퍼 ns.id)·{@code jsp}(JSP 상대 경로) */
    public record Orphan(String kind, String name) {
    }

    /**
     * jspLinks — JSP 경로 → 부르는 URL(정렬). jsps — 읽은 JSP 수(6-6). orphans — 안 불리는 문장·뷰가 안 가리키는 JSP(6-11).
     * joins — 매퍼 문장마다 조인 등식(6-13, 추정 관계 2-19 의 입력)
     */
    /**
     * viewFiles — 뷰 이름(kind view) → 맞는 JSP 파일 수(6-26). jspLinkKinds — JSP → (url, 꼴) 목록(6-27, 한 URL 에 꼴이 여럿이면 항목 여럿)
     */
    public record Result(List<Row> rows, List<String> tables, List<Unresolved> unresolved, int files, int skipped, boolean truncated,
            int statements, Map<String, List<String>> jspLinks, int jsps, List<Orphan> orphans, Map<String, List<SqlJoins.Join>> joins,
            Map<String, Integer> viewFiles, Map<String, List<JspLinks.Link>> jspLinkKinds) {

        public Result(List<Row> rows, List<String> tables, List<Unresolved> unresolved, int files, int skipped, boolean truncated,
                int statements, Map<String, List<String>> jspLinks, int jsps, List<Orphan> orphans) {
            this(rows, tables, unresolved, files, skipped, truncated, statements, jspLinks, jsps, orphans, null);
        }

        public Result(List<Row> rows, List<String> tables, List<Unresolved> unresolved, int files, int skipped, boolean truncated,
                int statements, Map<String, List<String>> jspLinks, int jsps, List<Orphan> orphans, Map<String, List<SqlJoins.Join>> joins) {
            this(rows, tables, unresolved, files, skipped, truncated, statements, jspLinks, jsps, orphans, joins, null, null);
        }

        public Result {
            rows = List.copyOf(rows);
            tables = List.copyOf(tables);
            unresolved = List.copyOf(unresolved);
            Map<String, List<String>> links = new TreeMap<>();
            jspLinks.forEach((k, v) -> links.put(k, List.copyOf(v)));
            jspLinks = java.util.Collections.unmodifiableMap(links);
            orphans = List.copyOf(orphans);
            Map<String, List<SqlJoins.Join>> js = new java.util.LinkedHashMap<>();
            if (joins != null) {
                joins.forEach((k, v) -> js.put(k, List.copyOf(v)));
            }
            joins = java.util.Collections.unmodifiableMap(js);
            viewFiles = java.util.Collections.unmodifiableMap(viewFiles == null ? new TreeMap<>() : new TreeMap<>(viewFiles));
            Map<String, List<JspLinks.Link>> kinds = new TreeMap<>();
            if (jspLinkKinds != null) {
                jspLinkKinds.forEach((k, v) -> kinds.put(k, List.copyOf(v)));
            }
            jspLinkKinds = java.util.Collections.unmodifiableMap(kinds);
        }
    }

    private AnalyzeRunner() {
    }

    /** @param ctx 진행률·취소 — 없으면 null(테스트·표본). 취소는 파일·그래프·합치기 어디서든 받는다(6-25) */
    public static Result run(String root, LocalFiles files, Profile.Naming naming, JobContext ctx) throws IOException {
        LocalFiles.Listing list = files.list(root, List.of("*.java", "*.xml", "*.jsp"), LocalFiles.MAX_FILES);
        Path base = files.check(root);
        List<Source> java = new ArrayList<>();
        List<Source> xml = new ArrayList<>();
        List<Source> jsp = new ArrayList<>();
        int skipped = 0;
        int n = list.files().size();
        for (int i = 0; i < n; i++) {
            if (ctx != null) {
                ctx.checkCancelled();
                if (i % PROGRESS_EVERY == 0) {
                    ctx.progress(n == 0 ? 0 : i * 80 / n, i + "/" + n + " 파일 읽기");
                }
            }
            String rel = list.files().get(i).rel();
            LocalFiles.Text t;
            try {
                t = files.read(base.resolve(rel).toString());
            } catch (IOException | LocalFiles.Refused e) {
                skipped++;
                continue;
            }
            Source s = new Source(rel, t.text(), t.encoding(), t.lineEnding(), null);
            String lower = rel.toLowerCase(Locale.ROOT);
            (lower.endsWith(".java") ? java : lower.endsWith(".jsp") ? jsp : xml).add(s);
        }
        if (ctx != null) {
            ctx.progress(80, "매퍼 색인");
        }
        MapperIndex.Index index = MapperIndex.scan(xml);
        if (ctx != null) {
            ctx.checkCancelled();
            ctx.progress(85, "JSP 링크");
        }
        Map<String, List<String>> jspLinks = new TreeMap<>();
        List<Unresolved> jspUnresolved = new ArrayList<>();
        for (Source s : jsp) {
            JspLinks.Result jr = JspLinks.extract(s);
            if (!jr.urls().isEmpty()) {
                jspLinks.put(s.rel(), jr.urls());
            }
            jspUnresolved.addAll(jr.unresolved());
        }
        if (ctx != null) {
            ctx.checkCancelled();
            ctx.progress(90, "호출 그래프");
        }
        // 6-15 — JPA 색인은 JPA·Spring Data 낱말이 든 파일만 읽는다(MyBatis 프로젝트에서 두 번 읽지 않게)
        JpaIndex jpa = JpaIndex.scan(java.stream().filter(s -> s.text().contains("persistence") || s.text().contains("Repository")
                || s.text().contains("springframework.data")).toList());
        JavaGraph.Graph graph = JavaGraph.scan(java, naming, jpa, ctx == null ? null : ctx::checkCancelled);

        Map<String, Unresolved> unresolved = new LinkedHashMap<>();
        index.unresolved().forEach(u -> unresolved.putIfAbsent(key(u), u));
        jpa.unresolved().forEach(u -> unresolved.putIfAbsent(key(u), u));
        graph.unresolved().forEach(u -> unresolved.putIfAbsent(key(u), u));
        jspUnresolved.forEach(u -> unresolved.putIfAbsent(key(u), u));
        List<Row> rows = new ArrayList<>();
        Set<String> tables = new TreeSet<>();
        int done = 0;
        for (JavaGraph.Program p : graph.programs()) {
            if (ctx != null && ++done % PROGRESS_EVERY == 0) {
                ctx.checkCancelled();
            }
            Map<String, EnumSet<SqlTables.Crud>> crud = new TreeMap<>();
            for (JavaGraph.Stmt st : p.statements()) {
                if (st.resolution().equals("jpa") || st.resolution().equals("qdsl")) {
                    List<Unresolved> out = new ArrayList<>();
                    int dot = st.id().lastIndexOf('.');
                    List<SqlTables.Ref> refs = jpa.recorded(st.id())
                            .orElseGet(() -> dot < 0 ? List.of() : jpa.refs(st.id().substring(0, dot), st.id().substring(dot + 1), out));
                    out.forEach(u -> unresolved.putIfAbsent(key(u), u));
                    refs.forEach(r -> crud.computeIfAbsent(r.table(), k -> EnumSet.noneOf(SqlTables.Crud.class)).addAll(r.crud()));
                    continue;
                }
                List<MapperIndex.Statement> hits = new ArrayList<>();
                if (st.resolution().equals("prefix")) {
                    index.statements().forEach((id, s) -> {
                        if (id.startsWith(st.id())) {
                            hits.add(s);
                        }
                    });
                } else if (index.get(st.id()) != null) {
                    hits.add(index.get(st.id()));
                }
                if (hits.isEmpty()) {
                    Unresolved u = new Unresolved("missing", p.file(), p.line(), st.id() + (st.resolution().equals("prefix") ? "*" : ""));
                    unresolved.putIfAbsent("missing|" + u.detail(), u);
                }
                for (MapperIndex.Statement s : hits) {
                    for (SqlTables.Ref r : s.refs()) {
                        crud.computeIfAbsent(r.table(), k -> EnumSet.noneOf(SqlTables.Crud.class)).addAll(r.crud());
                    }
                }
            }
            Map<String, String> letters = new TreeMap<>();
            crud.forEach((t, c) -> letters.put(t, new SqlTables.Ref(t, c).letters()));
            tables.addAll(letters.keySet());
            rows.add(new Row(p, letters));
        }
        if (ctx != null) {
            ctx.progress(100, n + "/" + n + " 파일");
        }
        Set<String> matched = new HashSet<>();
        Map<String, Integer> vf = viewFiles(graph, jsp, matched);
        return new Result(rows, new ArrayList<>(tables), new ArrayList<>(unresolved.values()), java.size() + xml.size() + jsp.size(),
                skipped, list.truncated(), index.statements().size(), jspLinks, jsp.size(), orphans(index, graph, jsp, matched), index.joins(),
                vf, Map.of());
    }

    /**
     * 뷰 이름(kind view) → 경로가 /<이름>.jsp 로 끝나는(또는 같은) JSP 파일 수(6-26). 6-11 고아 JSP 와 한 규칙 — 어느 뷰에도 안 세인 JSP 가 고아.
     * @param matched 비어서 들어오고, 어느 뷰에라도 세인 JSP 상대 경로가 채워진다
     */
    static Map<String, Integer> viewFiles(JavaGraph.Graph graph, List<Source> jsp, Set<String> matched) {
        Map<String, Integer> out = new TreeMap<>();
        for (JavaGraph.Program p : graph.programs()) {
            for (JavaGraph.View v : p.views()) {
                if (v.kind().equals("view")) {
                    out.putIfAbsent(v.name().startsWith("/") ? v.name().substring(1) : v.name(), 0);
                }
            }
        }
        for (Source s : jsp) {
            String rel = s.rel().replace('\\', '/');
            String noExt = rel.substring(0, rel.length() - 4);
            // 후보 = 전체 경로, 그 뒤 '/' 다음 접미마다 — 옛 orphans 의 seen 과 같은 집합
            List<String> cands = new ArrayList<>();
            cands.add(noExt);
            for (int i = noExt.indexOf('/'); i >= 0; i = noExt.indexOf('/', i + 1)) {
                cands.add(noExt.substring(i + 1));
            }
            for (String cand : cands) {
                if (out.containsKey(cand)) {
                    out.merge(cand, 1, Integer::sum);
                    matched.add(rel);
                }
            }
        }
        return out;
    }

    /**
     * 안 불리는 매퍼 문장 — 색인 ns.id 중 DAO 전체·프로그램 어느 문장 참조도 안 가리키는 것(prefix 는 그 접두로 시작하면 가리킨 것).
     * 뷰가 안 가리키는 JSP — 어느 프로그램의 view 이름 N 에도 경로가 {@code /N.jsp} 로 안 끝나는 것. 둘 다 후보다(동적 호출·타일즈는 못 본다)
     */
    static List<Orphan> orphans(MapperIndex.Index index, JavaGraph.Graph graph, List<Source> jsp, Set<String> matched) {
        Set<String> exact = new HashSet<>();
        List<String> prefixes = new ArrayList<>();
        List<JavaGraph.Stmt> refs = new ArrayList<>(graph.daoStatements());
        graph.programs().forEach(p -> refs.addAll(p.statements()));
        for (JavaGraph.Stmt s : refs) {
            if (s.resolution().equals("prefix")) {
                prefixes.add(s.id());
            } else {
                exact.add(s.id());
            }
        }
        List<Orphan> out = new ArrayList<>();
        for (String id : index.statements().keySet()) {
            if (!exact.contains(id) && prefixes.stream().noneMatch(id::startsWith)) {
                out.add(new Orphan("statement", id));
            }
        }
        // 6-26 — 어느 뷰에도 안 세인 JSP(viewFiles 가 채운 matched 의 보수). 맞춤 규칙은 viewFiles 한 곳
        for (Source s : jsp) {
            String rel = s.rel().replace('\\', '/');
            if (!matched.contains(rel)) {
                out.add(new Orphan("jsp", rel));
            }
        }
        out.sort(Comparator.comparing(Orphan::kind).thenComparing(Orphan::name));
        return out;
    }

    private static String key(Unresolved u) {
        return u.kind() + "|" + u.file() + "|" + u.line() + "|" + u.detail();
    }
}
