package kr.ejg.toolbox.core.gen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.CorpusHr;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.analyze.AnalyzeRunner;
import kr.ejg.toolbox.core.check.CheckRunner;
import kr.ejg.toolbox.core.check.Finding;
import kr.ejg.toolbox.core.check.JavaSource;
import kr.ejg.toolbox.core.check.RuleSet;
import kr.ejg.toolbox.core.dialect.MetaSources;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Scope;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * V-16 — CRUD 생성기를 실물 HR 스냅샷(표 7 + 뷰 1)에 세트 둘로 돌리고 생성물을 이 도구 엔진으로 다시 읽는다(PLAN 10장 M7 닫힘).
 * <ul>
 *   <li>등급 A: Java 구문 실패 · 프로그램 ≠ 7 × 표 · 미해결 · 등록 화면 밖 프로그램에 제 표 CRUD 없음 · 표마다 C·R·U·D 넷이 안 다 섬 ·
 *       고아(안 불리는 문장·뷰가 안 가리키는 JSP) · JSP 가 부르는 URL 이 프로그램에 없음 · 코드 검사 error · a11y·security·mybatis.noWhere·
 *       java.parseError · 뷰를 만들었음 · 다시 생성할 때 원래 파일이 바뀜</li>
 *   <li>등급 B: 세트마다 파일 수·프로그램 수·설명 있는 프로그램 수·코드 검사 warn·info 규칙별 수 {@code gen-hr.json}</li>
 * </ul>
 */
@Tag("corpus")
class GeneratorCorpusTest {

    @TempDir
    Path tmp;

    static RuleSet rules() {
        Profile p = new Profile("gen", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null,
                new Profile.Naming(null, null, null, null, null, true), new Profile.CodeCheck(null, null, null), "egov35", null, null, null);
        return RuleSet.load(p, null);
    }

    static Map<String, String> hashes(Path root) throws Exception {
        Map<String, String> out = new TreeMap<>();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.filter(Files::isRegularFile).filter(p -> !p.toString().endsWith(".gen")).toList()) {
                out.put(root.relativize(p).toString(), HexFormat.of().formatHex(md.digest(Files.readAllBytes(p))));
            }
        }
        return out;
    }

    @Test
    void hrRoundTrip() throws Exception {
        CorpusFiles.verify();
        List<Table> tables;
        CorpusHr.Loaded hr = CorpusHr.open();
        try {
            List<Schema> snap = MetaSources.forDialect("h2", hr.conn()).collect(new Scope(List.of("PUBLIC"), null, null, null));
            tables = snap.get(0).tables();
        } finally {
            hr.conn().close();
        }
        List<String> a = new ArrayList<>();
        Map<String, Object> golden = new LinkedHashMap<>();
        LocalFiles files = new LocalFiles(tmp.resolve("data"));
        RuleSet rules = rules();
        for (String setName : List.of("egov35", "egov5")) {
            TemplateSet set = TemplateSet.load(GenTemplatesTest.GEN, setName);
            Path out = tmp.resolve(setName);
            Files.createDirectories(out);
            GenModel.Options o = new GenModel.Options("kr.go.hr", null, List.of(), Map.of(), "oracle");
            Generator.Result g = Generator.run(set, tables, o, Map.of(), GenModelTest.TYPES, out, files, "UTF-8", "LF", null);
            int made = (int) tables.stream().filter(t -> t.type() == null || !t.type().toUpperCase().contains("VIEW")).count();
            Set<String> genTables = new TreeSet<>();
            g.files().forEach(f -> genTables.add(f.table()));
            if (genTables.size() != made) {
                a.add(setName + " 생성한 표 " + genTables + " ≠ " + made);
            }
            // 입력에 뷰가 있으면 그 이름마다 「건너뜀」 경고(H2 로 옮긴 HR 은 뷰 없이 표 7 — CorpusHr)
            for (Table t : tables) {
                if (t.type() != null && t.type().toUpperCase().contains("VIEW") && g.warnings().stream().noneMatch(w -> w.startsWith(t.name() + ":"))) {
                    a.add(setName + " 뷰 " + t.name() + " 를 건너뛴다는 경고가 없다");
                }
            }

            // ① 구문
            JavaSource parser = new JavaSource();
            try (Stream<Path> s = Files.walk(out)) {
                for (Path p : s.filter(x -> x.toString().endsWith(".java")).toList()) {
                    if (!JavaSource.ok(parser.parse(Files.readString(p)))) {
                        a.add(setName + " 구문: " + out.relativize(p));
                    }
                }
            }
            // ② 프로그램 분석
            AnalyzeRunner.Result r = AnalyzeRunner.run(out.toString(), files, null, null);
            if (r.rows().size() != 7 * made) {
                a.add(setName + " 프로그램 " + r.rows().size() + " ≠ " + 7 * made);
            }
            r.unresolved().forEach(u -> a.add(setName + " 미해결 " + u));
            // 7-10 — 컨트롤러 이름(생성 모델 Name + Controller) → 제 표
            Map<String, String> ownTable = new TreeMap<>();
            for (Table t : tables) {
                Object nm = GenModel.of(t, o, set.vars(), GenModelTest.TYPES).model().get("Name");
                if (nm != null) {
                    ownTable.put(nm + "Controller", t.name().toUpperCase(java.util.Locale.ROOT));
                }
            }
            Map<String, Set<String>> letters = new TreeMap<>();
            Set<String> urls = new TreeSet<>();
            int withDescr = 0;
            for (AnalyzeRunner.Row row : r.rows()) {
                urls.add(row.program().url());
                withDescr += row.program().description().isEmpty() ? 0 : 1;
                // 7-10 — 등록 화면(…View) 밖은 제 표가 CRUD 키에 있어야 한다
                String own = ownTable.get(row.program().className());
                if (!row.program().method().endsWith("View") && (own == null || !row.crud().containsKey(own))) {
                    a.add(setName + " 제 표 CRUD 없는 프로그램 " + row.program().className() + "." + row.program().method() + " " + own + " " + row.crud());
                }
                row.crud().forEach((t, l) -> {
                    for (char ch : l.toCharArray()) {
                        letters.computeIfAbsent(t, k -> new TreeSet<>()).add(String.valueOf(ch));
                    }
                });
            }
            letters.forEach((t, l) -> {
                if (!l.equals(Set.of("C", "R", "U", "D"))) {
                    a.add(setName + " " + t + " CRUD " + l);
                }
            });
            // ③ 고아 ④ JSP 링크
            r.orphans().forEach(x -> a.add(setName + " 고아 " + x));
            r.jspLinks().forEach((jsp, list) -> list.forEach(u -> {
                if (!urls.contains(u)) {
                    a.add(setName + " JSP 링크가 프로그램에 없음 " + jsp + " → " + u);
                }
            }));
            // ⑤ 코드 검사
            CheckRunner.RunResult c = CheckRunner.run(out.toString(), rules, files, null);
            Map<String, Integer> soft = new TreeMap<>();
            for (Finding f : c.findings()) {
                if (f.severity().equals("error") || f.rule().startsWith("a11y.") || f.rule().startsWith("security.")
                        || f.rule().equals("mybatis.noWhere") || f.rule().equals("java.parseError")) {
                    a.add(setName + " 코드 검사 " + f.file() + ":" + f.line() + " " + f.rule());
                } else {
                    soft.merge(f.rule(), 1, Integer::sum);
                }
            }
            // ⑥ 다시 생성 — 전부 .gen 옆에, 원래 파일 그대로
            Map<String, String> before = hashes(out);
            Generator.Result again = Generator.run(set, tables, o, Map.of(), GenModelTest.TYPES, out, files, "UTF-8", "LF", null);
            if (again.files().stream().anyMatch(f -> !f.status().equals("sidecar"))) {
                a.add(setName + " 다시 생성에 sidecar 아닌 파일");
            }
            if (!before.equals(hashes(out))) {
                a.add(setName + " 다시 생성이 원래 파일을 바꿨다");
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("files", g.files().size());
            m.put("programs", r.rows().size());
            m.put("withDescription", withDescr);
            m.put("checkSoft", soft);
            golden.put(setName, m);
        }
        GoldenFiles.assertJson("corpus/gen-hr.json", golden);
        CorpusFiles.none("CRUD 생성기 HR 왕복", a, 2);
    }
}
