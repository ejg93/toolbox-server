package kr.ejg.toolbox.core.analyze;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.db.Db;
import kr.ejg.toolbox.core.gen.DdlReader;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.SnapshotStore;
import kr.ejg.toolbox.core.text.Csv;
import kr.ejg.toolbox.core.fs.LocalFiles;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * V-13·V-14 — 프로그램 분석(6-1~6-4)·영향도(6-6)를 eGov 공통컴포넌트 실물에 돌린다(PLAN 4장·10장 M6 「게시판 CRUD 골든」).
 * <ul>
 *   <li>등급 A: 실행 예외 · {@code parse}(표본은 전부 컴파일·배포된 코드) · 매핑 애너테이션 자리(정규식으로 센 것) ≠ 프로그램 자리 ·
 *       표 자리 미해결(table) · 표 이름이 {@code SqlTables.STOP} · 게시판·스폿 골든 불일치 ·
 *       읽은 JSP 수 ≠ 표본 JSP 수 · 영향도 손 대조 둘(게시물 목록 ← EgovArticleReply.jsp · 마스터 등록 ← EgovBBSMasterRegist.jsp)</li>
 *   <li>등급 B: 수치 골든 {@code analyze-egov.json} + 미해결 목록 {@code analyze-egov-unresolved}·{@code analyze-egov-dialect}
 *       (늘어도 줄어도 빨강). 매퍼 XML 이 표본에 없는 egov-portal·enterprise·homepage 는 수만 {@code analyze-egov-noxml.json}.
 *       영향도 {@code analyze-egov-impact.json}(COMTNBBS·COMTNBBSMASTER) + JSP URL 미해결 {@code analyze-egov-jspurl}.
 *       교차 정합성(V-15) — oracle DDL 로 만든 스냅샷과 맞대 수 {@code analyze-egov-consistency.json} + 목록 넷</li>
 * </ul>
 * 시간은 안 잰다(이력에).
 */
@Tag("corpus")
class AnalyzeCorpusTest {

    static final String BBS = "egovframework/com/cop/bbs/web/";
    /** 게시판 밖 손 대조 — 접두 문장(LoginDAO)·<delete> 가 UPDATE 를 감싼 문장(EgovNoteTrnsmit) */
    static final Set<String> SPOT = Set.of("/uat/uia/actionLogin.do");
    static final Pattern MAPPING = Pattern.compile("^\\s*@(Request|Get|Post|Put|Delete|Patch)Mapping\\b");

    @TempDir
    Path tmp;

    /**
     * 6-13 — egov 매퍼 조인 등식 수·표 쌍 수 B baseline. 설계 실측(642·139)은 정규식으로 잰 값이라 스캐너 값과 다를 수 있다 —
     * 처음 값을 얼린다. 기존 analyze-egov*.json 은 안 건드린다(새 골든)
     */
    @Test
    void egovJoins() throws Exception {
        CorpusFiles.verify();
        AnalyzeRunner.Result r = AnalyzeRunner.run(CorpusFiles.root().resolve("egov").toString(), new LocalFiles(tmp.resolve("data")), null,
                null);
        int equalities = 0;
        Set<String> pairs = new TreeSet<>();
        for (List<SqlJoins.Join> js : r.joins().values()) {
            equalities += js.size();
            js.forEach(j -> pairs.add(j.tableA() + "|" + j.tableB()));
        }
        Map<String, Object> g = new java.util.LinkedHashMap<>();
        g.put("statementsWithJoins", r.joins().size());
        g.put("equalities", equalities);
        g.put("tablePairs", pairs.size());
        kr.ejg.toolbox.GoldenFiles.assertJson("corpus/analyze-egov-joins.json", g);
    }

    @Test
    void egov() throws Exception {
        CorpusFiles.verify();
        LocalFiles files = new LocalFiles(tmp.resolve("data"));
        Path root = CorpusFiles.root().resolve("egov");
        List<String> a = new ArrayList<>();
        AnalyzeRunner.Result r = AnalyzeRunner.run(root.toString(), files, null, null);

        // A — JSP 를 전부 읽었나(V-14)
        int jspFiles = CorpusFiles.files("egov", "*.jsp").size();
        if (r.jsps() != jspFiles) {
            a.add("JSP 읽음 " + r.jsps() + " ≠ 표본 " + jspFiles);
        }

        // A — parse · 표 자리 미해결(table) — 6-8
        for (Unresolved u : r.unresolved()) {
            if (u.kind().equals("parse") || u.kind().equals("table")) {
                a.add(u.file() + ":" + u.line() + " " + u.kind() + " " + u.detail());
            }
        }
        // A — 매핑 애너테이션 자리(정규식) = 프로그램 자리. 같은 줄에 매핑 값이 여럿이면 프로그램이 여럿
        Set<String> annotated = new TreeSet<>();
        for (Path p : CorpusFiles.files("egov", "*.java")) {
            String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            if (!text.contains("@Controller") && !text.contains("@RestController")) {
                continue;
            }
            String rel = root.relativize(p).toString().replace('\\', '/');
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (MAPPING.matcher(lines[i]).find() && i > 0 && !classLevel(lines, i)) {
                    annotated.add(rel + ":" + (i + 1));
                }
            }
        }
        Set<String> programs = new TreeSet<>();
        r.rows().forEach(x -> programs.add(x.program().file() + ":" + x.program().line()));
        Set<String> missingPrograms = new TreeSet<>(annotated);
        missingPrograms.removeAll(programs);
        missingPrograms.forEach(x -> a.add(x + " 매핑인데 프로그램 없음"));
        // A — 표 이름이 멈춤말(SqlTables.STOP 전체와 대조 — 6-8)
        r.tables().stream().filter(SqlTables.STOP::contains).forEach(t -> a.add("표 이름이 멈춤말: " + t));

        // 골든 — 게시판 컨트롤러 둘의 프로그램 전부 + 스폿
        List<Map<String, Object>> bbs = new ArrayList<>();
        List<Map<String, Object>> spot = new ArrayList<>();
        for (AnalyzeRunner.Row row : r.rows()) {
            JavaGraph.Program p = row.program();
            if (p.file().contains(BBS)) {
                bbs.add(golden(row));
            } else if (SPOT.contains(p.url()) || p.statements().stream().anyMatch(s -> s.id().equals("NoteTrnsmit.deleteNoteTrnsmit"))) {
                spot.add(golden(row));
            }
        }
        GoldenFiles.assertJson("corpus/analyze-egov-bbs.json", bbs);
        GoldenFiles.assertJson("corpus/analyze-egov-spot.json", spot);

        // B — 수치·미해결 목록
        Map<String, Object> sum = new LinkedHashMap<>();
        sum.put("files", r.files());
        sum.put("programs", r.rows().size());
        sum.put("statements", r.statements());
        sum.put("tables", r.tables().size());
        Map<String, Integer> kinds = new TreeMap<>();
        Map<String, Integer> views = new TreeMap<>();
        int withDescr = 0;
        int withCrud = 0;
        for (AnalyzeRunner.Row row : r.rows()) {
            kinds.merge(row.program().kind(), 1, Integer::sum);
            row.program().views().forEach(v -> views.merge(v.kind(), 1, Integer::sum));
            withDescr += row.program().description().isEmpty() ? 0 : 1;
            withCrud += row.crud().isEmpty() ? 0 : 1;
        }
        sum.put("kinds", kinds);
        sum.put("views", views);
        sum.put("withDescription", withDescr);
        sum.put("withCrud", withCrud);
        Map<String, Integer> un = new TreeMap<>();
        List<String> unresolvedList = new ArrayList<>();
        List<String> dialect = new ArrayList<>();
        List<String> jspUrl = new ArrayList<>();
        for (Unresolved u : r.unresolved()) {
            un.merge(u.kind(), 1, Integer::sum);
            String line = u.kind() + " " + u.file() + ":" + u.line() + " " + u.detail();
            if (u.kind().equals("dialect")) {
                dialect.add(line);
            } else if (u.kind().equals("jspUrl")) {
                jspUrl.add(line);
            } else if (!u.kind().equals("parse")) {
                unresolvedList.add(line);
            }
        }
        sum.put("unresolved", un);
        Set<String> linked = new TreeSet<>();
        r.jspLinks().values().forEach(linked::addAll);
        sum.put("jsps", r.jsps());
        sum.put("jspUrls", linked.size());
        sum.put("jspLinkedPrograms", r.rows().stream().filter(x -> linked.contains(x.program().url())).count());
        GoldenFiles.assertJson("corpus/analyze-egov.json", sum);
        CorpusFiles.conformance("analyze-egov-unresolved", unresolvedList);
        CorpusFiles.conformance("analyze-egov-dialect", dialect);
        CorpusFiles.conformance("analyze-egov-jspurl", jspUrl);

        // 영향도(V-14) — 임시 H2 에 저장하고 표 둘을 거꾸로 찾는다
        try (Db db = Db.open(tmp.resolve("db"))) {
            AnalyzeStore store = new AnalyzeStore(db);
            long id = store.save("corpus", root.toString(), r);
            Map<String, Object> impact = new LinkedHashMap<>();
            boolean seenList = false;
            boolean seenMaster = false;
            for (String t : List.of("COMTNBBS", "COMTNBBSMASTER")) {
                AnalyzeStore.Impact im = store.impact(id, t);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (AnalyzeStore.ImpactRow x : im.rows()) {
                    AnalyzeStore.ProgramRow p = x.program();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("program", p.className() + "." + p.method() + " " + p.verb() + " " + p.url() + (p.params().isEmpty() ? "" : " " + p.params()));
                    m.put("crud", p.crud().get(t));
                    m.put("jsps", x.jsps());
                    rows.add(m);
                    // A — 손 대조: 게시물 목록은 댓글 화면(EgovArticleReply.jsp)이 부른다
                    seenList |= t.equals("COMTNBBS") && p.url().equals("/cop/bbs/selectArticleList.do");
                    seenMaster |= t.equals("COMTNBBSMASTER") && p.url().equals("/cop/bbs/insertBBSMaster.do");
                    if (t.equals("COMTNBBS") && p.url().equals("/cop/bbs/selectArticleList.do")
                            && x.jsps().stream().noneMatch(j -> j.endsWith("cop/bbs/EgovArticleReply.jsp"))) {
                        a.add("영향도 손 대조: selectArticleList.do 의 JSP 에 EgovArticleReply.jsp 가 없다 — " + x.jsps());
                    }
                    // A — 손 대조: 게시판 마스터 등록은 등록 화면 하나(EgovBBSMasterRegist.jsp)만 부른다(표본 grep 과 같음)
                    if (t.equals("COMTNBBSMASTER") && p.url().equals("/cop/bbs/insertBBSMaster.do")
                            && !(x.jsps().size() == 1 && x.jsps().get(0).endsWith("cop/bbs/EgovBBSMasterRegist.jsp"))) {
                        a.add("영향도 손 대조: insertBBSMaster.do 의 JSP 가 EgovBBSMasterRegist.jsp 하나가 아니다 — " + x.jsps());
                    }
                }
                impact.put(t, Map.of("programs", rows, "jsps", im.jsps().size()));
            }
            // A — 손 대조 대상이 영향도에 아예 없으면 위 대조가 안 돈다(6-10)
            if (!seenList) {
                a.add("영향도 손 대조: COMTNBBS 영향도에 selectArticleList.do 가 없다");
            }
            if (!seenMaster) {
                a.add("영향도 손 대조: COMTNBBSMASTER 영향도에 insertBBSMaster.do 가 없다");
            }
            GoldenFiles.assertJson("corpus/analyze-egov-impact.json", impact);

            // 교차 정합성(V-15) — 표본 DDL(oracle)로 스냅샷을 만들어 코드와 맞댄다
            byte[] ddl = Files.readAllBytes(root.resolve("script/ddl/oracle/com_DDL_oracle.sql"));
            String text = Csv.decode(ddl);
            text = text.startsWith(String.valueOf((char) 0xFEFF)) ? text.substring(1) : text;
            DdlReader.Result dr = DdlReader.read(text);
            SnapshotStore snapshots = new SnapshotStore(db);
            long snap = snapshots.save("corpus", "ddl-oracle", null, List.of(new Schema("EGOV", null, dr.tables())));
            Consistency.Report cr = Consistency.of(store, snapshots, id, snap).orElseThrow();
            List<String> missing = cr.missingInDb().stream().map(x -> x.table() + " " + x.programs()).toList();
            List<String> unusedTables = cr.unusedInCode().stream().map(x -> x.table()).toList();
            // A — 손 대조 셋: 게시물 목록 화면은 뷰가 가리킨다 · 게시물 목록 문장은 불린다 · 게시판 표는 DDL 에 있다
            if (cr.orphanJsps().stream().anyMatch(j -> j.endsWith("cop/bbs/EgovArticleList.jsp"))) {
                a.add("정합성 손 대조: EgovArticleList.jsp 가 고아로 나왔다");
            }
            if (cr.deadStatements().contains("BBSArticle.selectArticleList")) {
                a.add("정합성 손 대조: BBSArticle.selectArticleList 가 안 불리는 문장으로 나왔다");
            }
            if (cr.missingInDb().stream().anyMatch(x -> x.table().equals("COMTNBBS"))) {
                a.add("정합성 손 대조: COMTNBBS 가 DDL 에 없다고 나왔다");
            }
            Map<String, Object> cons = new LinkedHashMap<>();
            cons.put("ddlTables", dr.tables().size());
            cons.put("ddlUnreadable", dr.unreadable().size());
            cons.put("missingInDb", missing.size());
            cons.put("unusedInCode", unusedTables.size());
            cons.put("deadStatements", cr.deadStatements().size());
            cons.put("orphanJsps", cr.orphanJsps().size());
            GoldenFiles.assertJson("corpus/analyze-egov-consistency.json", cons);
            CorpusFiles.conformance("analyze-egov-missing-in-db", missing);
            CorpusFiles.conformance("analyze-egov-unused-in-code", unusedTables);
            CorpusFiles.conformance("analyze-egov-dead-stmt", cr.deadStatements());
            CorpusFiles.conformance("analyze-egov-orphan-jsp", cr.orphanJsps());
        }

        // B — 매퍼 XML 이 표본에 없는 출처: 프로그램 수·missing 수만
        Map<String, Object> noxml = new LinkedHashMap<>();
        for (String source : List.of("egov-portal", "egov-enterprise", "egov-homepage")) {
            AnalyzeRunner.Result x;
            try {
                x = AnalyzeRunner.run(CorpusFiles.root().resolve(source).toString(), files, null, null);
            } catch (RuntimeException e) {
                a.add(source + " 예외 " + e);
                continue;
            }
            x.unresolved().stream().filter(u -> u.kind().equals("parse")).forEach(u -> a.add(source + "/" + u.file() + " parse"));
            noxml.put(source, Map.of("programs", x.rows().size(), "jsps", x.jsps(),
                    "missing", x.unresolved().stream().filter(u -> u.kind().equals("missing")).count()));
        }
        GoldenFiles.assertJson("corpus/analyze-egov-noxml.json", noxml);
        CorpusFiles.none("프로그램 분석(파일 " + r.files() + ")", a, r.files());
    }

    /** 클래스 머리 매핑 — 애너테이션 뒤 첫 선언 줄이 class·interface 면 */
    static boolean classLevel(String[] lines, int i) {
        for (int j = i + 1; j < lines.length; j++) {
            String t = lines[j].strip();
            if (t.isEmpty() || t.startsWith("@") || t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                continue;
            }
            return t.matches(".*\\b(class|interface)\\s.*");
        }
        return false;
    }

    static Map<String, Object> golden(AnalyzeRunner.Row row) {
        JavaGraph.Program p = row.program();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("program", p.className() + "." + p.method());
        m.put("verb", p.verb());
        m.put("url", p.url());
        m.put("params", p.params());
        m.put("kind", p.kind());
        m.put("description", p.description());
        List<String> v = new ArrayList<>();
        p.views().forEach(x -> v.add(x.kind() + ":" + x.name()));
        m.put("views", v);
        List<String> s = new ArrayList<>();
        p.statements().forEach(x -> s.add(x.id() + (x.resolution().equals("literal") ? "" : " (" + x.resolution() + ")")));
        m.put("statements", s);
        m.put("crud", row.crud());
        return m;
    }
}
