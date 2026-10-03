package kr.ejg.toolbox.core.analyze;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
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
 * 프로그램 분석 한 번(6-4) — 폴더의 {@code *.java} 는 {@link JavaGraph}, {@code *.xml} 은 {@link MapperIndex} 로 읽고
 * 프로그램마다 문장 참조를 색인에 맞춰 테이블·CRUD 를 합친다. 색인에 없는 ns.id 는 {@code missing}. 글은 메모리에서만 쓴다(규칙 3).
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
    public record Result(List<Row> rows, List<String> tables, List<Unresolved> unresolved, int files, int skipped, boolean truncated,
            int statements) {

        public Result {
            rows = List.copyOf(rows);
            tables = List.copyOf(tables);
            unresolved = List.copyOf(unresolved);
        }
    }

    private AnalyzeRunner() {
    }

    /** @param ctx 진행률·취소 — 없으면 null(테스트·표본) */
    public static Result run(String root, LocalFiles files, Profile.Naming naming, JobContext ctx) throws IOException {
        LocalFiles.Listing list = files.list(root, List.of("*.java", "*.xml"), LocalFiles.MAX_FILES);
        Path base = files.check(root);
        List<Source> java = new ArrayList<>();
        List<Source> xml = new ArrayList<>();
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
            (rel.toLowerCase(Locale.ROOT).endsWith(".java") ? java : xml).add(s);
        }
        if (ctx != null) {
            ctx.progress(80, "매퍼 색인");
        }
        MapperIndex.Index index = MapperIndex.scan(xml);
        if (ctx != null) {
            ctx.checkCancelled();
            ctx.progress(90, "호출 그래프");
        }
        JavaGraph.Graph graph = JavaGraph.scan(java, naming);

        Map<String, Unresolved> unresolved = new LinkedHashMap<>();
        index.unresolved().forEach(u -> unresolved.putIfAbsent(key(u), u));
        graph.unresolved().forEach(u -> unresolved.putIfAbsent(key(u), u));
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
        return new Result(rows, new ArrayList<>(tables), new ArrayList<>(unresolved.values()), java.size() + xml.size(), skipped,
                list.truncated(), index.statements().size());
    }

    private static String key(Unresolved u) {
        return u.kind() + "|" + u.file() + "|" + u.line() + "|" + u.detail();
    }
}
