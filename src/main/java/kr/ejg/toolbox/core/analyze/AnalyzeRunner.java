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

    /** jspLinks — JSP 경로 → 부르는 URL(정렬). jsps — 읽은 JSP 수(6-6). orphans — 안 불리는 문장·뷰가 안 가리키는 JSP(6-11) */
    public record Result(List<Row> rows, List<String> tables, List<Unresolved> unresolved, int files, int skipped, boolean truncated,
            int statements, Map<String, List<String>> jspLinks, int jsps, List<Orphan> orphans) {

        public Result {
            rows = List.copyOf(rows);
            tables = List.copyOf(tables);
            unresolved = List.copyOf(unresolved);
            Map<String, List<String>> links = new TreeMap<>();
            jspLinks.forEach((k, v) -> links.put(k, List.copyOf(v)));
            jspLinks = java.util.Collections.unmodifiableMap(links);
            orphans = List.copyOf(orphans);
        }
    }

    private AnalyzeRunner() {
    }

    /** @param ctx 진행률·취소 — 없으면 null(테스트·표본) */
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
        JavaGraph.Graph graph = JavaGraph.scan(java, naming);

        Map<String, Unresolved> unresolved = new LinkedHashMap<>();
        index.unresolved().forEach(u -> unresolved.putIfAbsent(key(u), u));
        graph.unresolved().forEach(u -> unresolved.putIfAbsent(key(u), u));
        jspUnresolved.forEach(u -> unresolved.putIfAbsent(key(u), u));
        List<Row> rows = new ArrayList<>();
        Set<String> tables = new TreeSet<>();
        for (JavaGraph.Program p : graph.programs()) {
            Map<String, EnumSet<SqlTables.Crud>> crud = new TreeMap<>();
            for (JavaGraph.Stmt st : p.statements()) {
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
        return new Result(rows, new ArrayList<>(tables), new ArrayList<>(unresolved.values()), java.size() + xml.size() + jsp.size(),
                skipped, list.truncated(), index.statements().size(), jspLinks, jsp.size(), orphans(index, graph, jsp));
    }

    /**
     * 안 불리는 매퍼 문장 — 색인 ns.id 중 DAO 전체·프로그램 어느 문장 참조도 안 가리키는 것(prefix 는 그 접두로 시작하면 가리킨 것).
     * 뷰가 안 가리키는 JSP — 어느 프로그램의 view 이름 N 에도 경로가 {@code /N.jsp} 로 안 끝나는 것. 둘 다 후보다(동적 호출·타일즈는 못 본다)
     */
    static List<Orphan> orphans(MapperIndex.Index index, JavaGraph.Graph graph, List<Source> jsp) {
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
        Set<String> views = new HashSet<>();
        for (JavaGraph.Program p : graph.programs()) {
            for (JavaGraph.View v : p.views()) {
                if (v.kind().equals("view")) {
                    views.add(v.name().startsWith("/") ? v.name().substring(1) : v.name());
                }
            }
        }
        for (Source s : jsp) {
            String rel = s.rel().replace('\\', '/');
            String noExt = rel.substring(0, rel.length() - 4);
            boolean seen = views.contains(noExt);
            for (int i = noExt.indexOf('/'); !seen && i >= 0; i = noExt.indexOf('/', i + 1)) {
                seen = views.contains(noExt.substring(i + 1));
            }
            if (!seen) {
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
