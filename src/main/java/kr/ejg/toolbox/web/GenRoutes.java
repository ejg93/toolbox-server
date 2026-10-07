package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.dict.DictStore;
import kr.ejg.toolbox.core.gen.DdlReader;
import kr.ejg.toolbox.core.gen.DtoGenerator;
import kr.ejg.toolbox.core.gen.TypeMapping;
import kr.ejg.toolbox.core.gen.Validation;
import kr.ejg.toolbox.core.logical.ColumnInputs;
import kr.ejg.toolbox.core.logical.LogicalRun;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.SnapshotStore;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.profile.Profile;

/**
 * DTO 생성(1-9) — `POST /api/gen/dto {snapshotId, tables[{schema, name}] | ddl, packageName, style, dialect, skipTokens}`
 * → `{files[{name, source}], unreadable[]}`. `?save=true` 면 `out/<프로필>/<시각>/dto/*.java` 에도 쓴다.
 */
final class GenRoutes {

    record TableRef(String schema, String name) {
    }

    /** validation — 검증 어노테이션(1-31). null 이면 켬, 네임스페이스는 활성 프로필 framework(egov5 → jakarta) */
    record DtoRequest(Long snapshotId, List<TableRef> tables, String ddl, String packageName, String style, String dialect,
            List<String> skipTokens, Boolean validation) {
    }

    /** 자바 식별자 한 조각. 점 이음은 split 으로 — 중첩 반복 정규식은 ReDoS(SpotBugs) */
    static final java.util.regex.Pattern PART = java.util.regex.Pattern.compile("[\\p{L}_$][\\p{L}\\p{N}_$]*");

    /** 점으로 이은 자바 식별자 — package 문에 그대로 들어간다(번들 4 리뷰) */
    static boolean validPackage(String p) {
        for (String part : p.split("\\.", -1)) {
            if (!PART.matcher(part).matches()) {
                return false;
            }
        }
        return true;
    }

    private GenRoutes() {
    }

    static void register(Javalin app, DictStore dict, SnapshotStore snapshots, Supplier<Optional<Profile>> active) {
        TypeMapping types = TypeMapping.load();
        DtoGenerator gen = new DtoGenerator(types);
        app.post("/api/gen/dto", ctx -> {
            DtoRequest req = ctx.bodyAsClass(DtoRequest.class);
            DtoGenerator.Style style;
            try {
                style = DtoGenerator.Style.of(req.style());
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", e.getMessage()));
                return;
            }
            if (req.packageName() != null && !req.packageName().isBlank() && !validPackage(req.packageName().trim())) {
                ctx.status(400).json(Map.of("message", "패키지 이름이 자바 규칙에 안 맞는다: " + req.packageName()));
                return;
            }
            List<String> skip = req.skipTokens() != null ? req.skipTokens()
                    : active.get().map(Profile::logicalName).map(Profile.LogicalName::skipTokens).orElse(List.of());
            Validation.Ns ns = Boolean.FALSE.equals(req.validation()) ? null
                    : Validation.nsOf(active.get().map(Profile::framework).orElse(null));
            List<Table> tables = new ArrayList<>();
            Map<String, Map<String, String>> notes = new HashMap<>();
            List<DdlReader.Unreadable> unreadable = List.of();
            String dialect = req.dialect();
            if (req.ddl() != null && !req.ddl().isBlank()) {
                DdlReader.Result r = DdlReader.read(req.ddl());
                tables.addAll(r.tables());
                notes.putAll(r.notes());
                unreadable = r.unreadable();
            } else if (req.snapshotId() != null) {
                Optional<List<Schema>> snap = snapshots.get(req.snapshotId());
                if (snap.isEmpty()) {
                    ctx.status(404).json(Map.of("message", "스냅샷이 없다: " + req.snapshotId()));
                    return;
                }
                for (Schema s : snap.get()) {
                    if (dialect == null) {
                        dialect = TypeMapping.dialectOf(s.dbVersion());
                    }
                    for (Table t : s.tables()) {
                        if (req.tables() == null || req.tables().isEmpty() || req.tables().stream().anyMatch(ref -> ref.name() != null
                                && ref.name().equalsIgnoreCase(t.name()) && (ref.schema() == null || ref.schema().equalsIgnoreCase(t.schema())))) {
                            tables.add(t);
                        }
                    }
                }
            } else {
                ctx.status(400).json(Map.of("message", "snapshotId 또는 ddl 이 있어야 한다"));
                return;
            }
            if (tables.isEmpty()) {
                ctx.status(400).json(Map.of("message", "만들 테이블이 없다 — CREATE TABLE 을 못 찾았거나 고른 테이블이 스냅샷에 없다"));
                return;
            }
            Map<String, Map<String, String>> logical = logicalNames(tables, dict, skip);
            List<Map<String, String>> files = new ArrayList<>();
            for (Table t : tables) {
                String key = t.name().toUpperCase(Locale.ROOT);
                DtoGenerator.Source src = gen.generate(t, new DtoGenerator.Options(req.packageName(), style, skip,
                        logical.get(key), notes.get(key), dialect, ns));
                Map<String, String> f = new LinkedHashMap<>();
                f.put("name", src.className() + ".java");
                f.put("source", src.text());
                files.add(f);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("files", files);
            out.put("unreadable", unreadable);
            if ("true".equals(ctx.queryParam("save"))) {
                Path dir = LogicalRoutes.outFile(active, "dto");
                Files.createDirectories(dir);
                for (Map<String, String> f : files) {
                    Files.writeString(dir.resolve(f.get("name")), f.get("source"), StandardCharsets.UTF_8);
                }
                out.put("path", dir.toString());
            }
            ctx.json(out);
        });
    }

    /** 테이블명(대문자) → (컬럼명(대문자) → 조립 한글). 토큰을 전부 찾은 이름만 — 덜 조립된 이름은 javadoc 에 안 쓴다 */
    static Map<String, Map<String, String>> logicalNames(List<Table> tables, DictStore dict, List<String> skip) throws java.sql.SQLException {
        LogicalRun.Result r = LogicalRun.run(ColumnInputs.fromSchemas(List.of(new Schema("", null, tables))), dict.load(), skip, true);
        Map<String, Map<String, String>> out = new HashMap<>();
        for (LogicalRun.Row row : r.rows()) {
            if (!row.src().equals("none") && row.missing().isEmpty() && !row.name().isEmpty()) {
                out.computeIfAbsent(row.table().toUpperCase(Locale.ROOT), k -> new HashMap<>()).put(row.col().toUpperCase(Locale.ROOT), row.name());
            }
        }
        return out;
    }
}
