package kr.ejg.toolbox.core.gen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.job.JobContext;
import kr.ejg.toolbox.core.meta.Table;

/**
 * CRUD 생성 한 번(7-3). 표마다 {@link GenModel} → 세트의 파일마다 경로 식·본문을 그린다. 경로가 결과 폴더 밖이면 그 세트를 통째로 거절하고
 * 아무것도 안 쓴다. 쓰기는 결과 폴더에만({@link LocalFiles#writeResult}, 9-3c — 절대 규칙 7). 프로젝트 폴더는 안 건드리고, 같은 상대 경로의
 * 파일이 프로젝트에 이미 있으면 상태를 {@code exists}(비교해 옮긴다), 없으면 {@code created}. 표 하나가 실패하면 경고를 남기고 다음 표.
 * 생성물은 파일이 결과다 — DB 이력은 안 남긴다.
 */
public final class Generator {

    public record GenFile(String rel, String table, String status) {
    }

    public record Result(List<GenFile> files, List<String> warnings) {

        public Result {
            files = List.copyOf(files);
            warnings = List.copyOf(warnings);
        }
    }

    private record Planned(Path target, String table, String text) {
    }

    private Generator() {
    }

    /**
     * @param logicalNames 표 이름(대문자) → 컬럼 이름(대문자) → 논리명. 없으면 빈 맵
     */
    public static Result run(TemplateSet set, List<Table> tables, GenModel.Options base, Map<String, Map<String, String>> logicalNames,
            TypeMapping types, Path outDir, LocalFiles files, String encoding, String lineEnding, JobContext ctx) throws IOException {
        return run(set, tables, base, logicalNames, Map.of(), types, outDir, null, files, encoding, lineEnding, ctx);
    }

    /**
     * @param tableGens   표 이름(대소문자 무시) → 표별 재정의(프로필 generator.tables, 7-15). 없으면 빈 맵
     * @param outDir      결과 폴더(호출자가 {@link LocalFiles#resultRoot} 로 고른 것)
     * @param projectRoot 프로젝트 폴더 — 같은 상대 경로가 이미 있는지만 본다(읽기만). 없으면 null
     */
    public static Result run(TemplateSet set, List<Table> tables, GenModel.Options base, Map<String, Map<String, String>> logicalNames,
            Map<String, kr.ejg.toolbox.core.profile.Profile.TableGen> tableGens, TypeMapping types, Path outDir, Path projectRoot, LocalFiles files,
            String encoding, String lineEnding, JobContext ctx) throws IOException {
        Map<String, kr.ejg.toolbox.core.profile.Profile.TableGen> byName = new java.util.HashMap<>();
        if (tableGens != null) {
            tableGens.forEach((k, v) -> byName.put(k.toUpperCase(java.util.Locale.ROOT), v));
        }
        Templates t = new Templates(set);
        Path out = outDir.toAbsolutePath().normalize();
        List<String> warnings = new ArrayList<>();
        List<Planned> plan = new ArrayList<>();
        int i = 0;
        for (Table table : tables) {
            if (ctx != null) {
                ctx.checkCancelled();
                ctx.progress(tables.isEmpty() ? 0 : i * 80 / tables.size(), table.name());
            }
            i++;
            kr.ejg.toolbox.core.profile.Profile.TableGen tg = byName.get(table.name().toUpperCase(java.util.Locale.ROOT));
            GenModel.Options o = new GenModel.Options(base.basePackage(), base.module(), base.skipTokens(),
                    logicalNames.getOrDefault(table.name().toUpperCase(java.util.Locale.ROOT), Map.of()), base.dialect(),
                    tg, base.audit());
            GenModel.Result m = GenModel.of(table, o, set.vars(), types);
            warnings.addAll(m.warnings());
            if (m.model().isEmpty()) {
                continue;
            }
            // 7-11 — 생성 파일의 인코딩 선언(JSP pageEncoding·charset, XML 선언)이 실제로 쓰는 바이트와 같게
            Map<String, Object> model = new java.util.LinkedHashMap<>(m.model());
            model.put("encoding", LocalFiles.MS949.equals(encoding) ? "MS949" : "UTF-8");
            List<Planned> mine = new ArrayList<>();
            try {
                for (TemplateSet.FileSpec f : set.files()) {
                    String rel = t.renderPath(f.path(), model).trim();
                    Path target = out.resolve(rel).normalize();
                    if (rel.isEmpty() || !target.startsWith(out) || target.equals(out)) {
                        throw new IllegalArgumentException(set.name() + " 의 경로 식이 출력 폴더 밖을 가리킨다: " + f.path());
                    }
                    String text = t.render(f.template(), model);
                    LocalFiles.checkEncodable(text, encoding, lineEnding); // 쓰기 단계에서 실패하지 않게 미리(7-10)
                    mine.add(new Planned(target, table.name(), text));
                }
            } catch (IOException | LocalFiles.Refused e) {
                warnings.add(table.name() + ": " + e.getMessage() + " — 건너뜀");
                continue;
            }
            plan.addAll(mine);
        }
        List<GenFile> done = new ArrayList<>();
        int k = 0;
        for (Planned p : plan) {
            if (ctx != null && k++ % 20 == 0) {
                ctx.checkCancelled();
                ctx.progress(80 + (plan.isEmpty() ? 0 : k * 20 / plan.size()), "쓰기");
            }
            String rel = out.relativize(p.target()).toString().replace('\\', '/');
            files.writeResult(out, rel, p.text(), encoding, lineEnding);
            boolean there = projectRoot != null && Files.exists(projectRoot.resolve(rel), LinkOption.NOFOLLOW_LINKS);
            done.add(new GenFile(rel, p.table(), there ? "exists" : "created"));
        }
        if (ctx != null) {
            ctx.progress(100, done.size() + " 파일");
        }
        return new Result(done, warnings);
    }
}
