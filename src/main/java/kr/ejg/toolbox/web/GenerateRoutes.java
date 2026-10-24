package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.dict.DictStore;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.gen.GenModel;
import kr.ejg.toolbox.core.gen.Generator;
import kr.ejg.toolbox.core.gen.TemplateSet;
import kr.ejg.toolbox.core.gen.TypeMapping;
import kr.ejg.toolbox.core.job.Job;
import kr.ejg.toolbox.core.job.JobManager;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.SnapshotStore;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.profile.Profile;

/**
 * CRUD 생성기(7-3). {@code GET /api/generate/templates} — 세트 목록. {@code POST /api/generate}(job) — 스냅샷의 표를 골라 세트로 그려
 * 결과 폴더(outDir, 없으면 {@code out/<프로필>/<stamp>/generate})에 쓴다. 빈 값은 활성 프로필 {@code generator} 로. 프로젝트 폴더
 * ({@code project.root})는 안 건드리고 이미 있는 파일은 상태 {@code exists} 로만 알린다(9-3c). 이력은 DB 에 안 남긴다.
 */
final class GenerateRoutes {

    record TableRef(String schema, String name) {
    }

    record GenerateRequest(Long snapshotId, List<TableRef> tables, String templateSet, String basePackage, String module, String outDir,
            String dialect) {
    }

    static final int MAX_TABLES = 200;
    /** 타입 표 — 한 번 읽어 둔다(0-63a: 라우트 메서드 인자를 7 이하로) */
    private static final TypeMapping TYPES = TypeMapping.load();

    private GenerateRoutes() {
    }

    static void register(Javalin app, JobManager jobs, SnapshotStore snapshots, DictStore dict, LocalFiles files,
            Supplier<Optional<Profile>> active, Path genDir) {
        app.get("/api/generate/templates", ctx -> getTemplates(ctx, files, genDir));

        app.post("/api/generate", ctx -> postGenerate(ctx, jobs, snapshots, dict, files, active, genDir));
    }

    /** GET /api/generate/templates */
    private static void getTemplates(Context ctx, LocalFiles files, Path genDir) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String name : TemplateSet.list(genDir)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            try {
                TemplateSet s = TemplateSet.load(genDir, name);
                m.put("vars", s.vars());
                m.put("files", s.files().stream().map(TemplateSet.FileSpec::template).toList());
            } catch (IllegalArgumentException e) {
                m.put("error", e.getMessage());
            }
            out.add(m);
        }
        ctx.json(out);
    }

    /** POST /api/generate */
    private static void postGenerate(Context ctx, JobManager jobs, SnapshotStore snapshots, DictStore dict, LocalFiles files, Supplier<Optional<Profile>> active,
            Path genDir) throws Exception {
        GenerateRequest req = ctx.bodyAsClass(GenerateRequest.class);
        Profile p = active.get().orElse(null);
        Profile.Generator g = p == null ? null : p.generator();
        String setName = pick(req.templateSet(), g == null ? null : g.templateSet());
        String basePackage = pick(req.basePackage(), g == null ? null : g.basePackage());
        String outDir = pick(req.outDir(), g == null ? null : g.outDir());
        String module = req.module() == null || req.module().isBlank() ? null : req.module().trim();
        if (setName == null || !TemplateSet.list(genDir).contains(setName)) {
            ctx.status(400).json(Map.of("message", "템플릿 세트가 없다: " + setName));
            return;
        }
        if (req.tables() == null || req.tables().isEmpty()) {
            ctx.status(400).json(Map.of("message", "표를 하나 이상 고른다"));
            return;
        }
        if (req.tables().size() > MAX_TABLES) {
            ctx.status(400).json(Map.of("message", "표는 한 번에 " + MAX_TABLES + " 개까지"));
            return;
        }
        if (basePackage == null || !GenRoutes.validPackage(basePackage) || !lowerParts(basePackage)) {
            ctx.status(400).json(Map.of("message", "basePackage 가 소문자 자바 패키지가 아니다: " + basePackage));
            return;
        }
        if (module != null && (!GenRoutes.validPackage(module) || !lowerParts(module))) {
            ctx.status(400).json(Map.of("message", "module 이 소문자 자바 패키지 꼴이 아니다: " + module));
            return;
        }
        if (req.snapshotId() == null) {
            ctx.status(400).json(Map.of("message", "snapshotId 가 있어야 한다"));
            return;
        }
        Optional<List<Schema>> snap = snapshots.get(req.snapshotId());
        if (snap.isEmpty()) {
            ctx.status(404).json(Map.of("message", "스냅샷이 없다: " + req.snapshotId()));
            return;
        }
        String dialect = req.dialect();
        List<Table> tables = new ArrayList<>();
        for (TableRef ref : req.tables()) {
            Table found = null;
            for (Schema s : snap.get()) {
                if (dialect == null || dialect.isBlank()) {
                    dialect = TypeMapping.dialectOf(s.dbVersion());
                }
                for (Table t : s.tables()) {
                    if (ref.name() != null && ref.name().equalsIgnoreCase(t.name())
                            && (ref.schema() == null || ref.schema().isBlank() || ref.schema().equalsIgnoreCase(t.schema()))) {
                        found = t;
                    }
                }
            }
            if (found == null) {
                ctx.status(404).json(Map.of("message", "스냅샷에 없는 표: " + ref.name()));
                return;
            }
            tables.add(found);
        }
        if (dialect == null || dialect.isBlank()) {
            dialect = "oracle"; // 스냅샷 제품명으로 못 알면
        }
        dialect = dialect.trim().toLowerCase(java.util.Locale.ROOT);
        if (!GenModel.DIALECTS.contains(dialect)) {
            ctx.status(400).json(Map.of("message", "생성기가 모르는 방언: " + dialect + " — " + String.join("·", new java.util.TreeSet<>(GenModel.DIALECTS))));
            return;
        }
        TemplateSet set = TemplateSet.load(genDir, setName);
        List<String> skip = p == null || p.logicalName() == null ? List.of() : p.logicalName().skipTokens();
        Map<String, Map<String, String>> logical = GenRoutes.logicalNames(tables, dict, skip);
        String encoding = p == null || p.project() == null || p.project().encoding() == null ? LocalFiles.UTF8 : p.project().encoding();
        String lineEnding = p == null || p.project() == null || p.project().lineEnding() == null ? LocalFiles.LF : p.project().lineEnding();
        GenModel.Options base = new GenModel.Options(basePackage, module, skip, Map.of(), dialect);
        // 7-15 — 프로필 generator.tables(표별 클래스 이름·뺄 컬럼)
        Map<String, kr.ejg.toolbox.core.profile.Profile.TableGen> tableGens = p == null || p.generator() == null ? Map.of() : p.generator().tables();
        // 9-3c — 결과 폴더(기본 out/<프로필>/<stamp>/generate). 프로젝트 폴더 안·프로젝트를 품는 폴더는 거절(절대 규칙 7). 검증을 다 지난 뒤에 만든다
        String root = p == null || p.project() == null ? null : p.project().root();
        Path projectRoot = root == null || root.isBlank() ? null : files.check(root);
        String stamp = FsRoutes.stamp(null);
        Path out = files.resultRoot(outDir, FsRoutes.outRoot(active, stamp).resolve("generate"), projectRoot, "generate", stamp);
        Job job = jobs.submit("generate", jc -> {
            Generator.Result r = Generator.run(set, tables, base, logical, tableGens, TYPES, out, projectRoot, files, encoding, lineEnding, jc);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("outDir", out.toString());
            result.put("files", r.files());
            result.put("warnings", r.warnings());
            result.put("created", r.files().stream().filter(f -> f.status().equals("created")).count());
            result.put("exists", r.files().stream().filter(f -> f.status().equals("exists")).count());
            jc.emit(Job.EventName.RESULT, result);
            return result;
        });
        ctx.status(202).json(Map.of("jobId", job.id()));
    }


    private static String pick(String a, String b) {
        String v = a != null && !a.isBlank() ? a : b;
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** 패키지 마디가 소문자·_ 로 시작 — 클래스 이름과 안 헷갈리게 */
    private static boolean lowerParts(String p) {
        for (String part : p.split("\\.", -1)) {
            char c = part.charAt(0);
            if (!(c == '_' || Character.isLowerCase(c))) {
                return false;
            }
        }
        return true;
    }
}
