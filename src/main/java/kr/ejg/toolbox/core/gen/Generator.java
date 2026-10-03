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
 * CRUD 생성 한 번(7-3). 표마다 {@link GenModel} → 세트의 파일마다 경로 식·본문을 그린다. 경로가 출력 폴더 밖이면 그 세트를 통째로 거절하고
 * 아무것도 안 쓴다. 쓰기는 {@link LocalFiles#create} — 없으면 만들고({@code created}), 있으면 사업 소스를 안 덮고 옆에 {@code <이름>.gen}
 * ({@code sidecar}). 표 하나가 실패하면 경고를 남기고 다음 표. 생성물은 파일이 결과다 — DB 이력은 안 남긴다.
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
            GenModel.Options o = new GenModel.Options(base.basePackage(), base.module(), base.skipTokens(),
                    logicalNames.getOrDefault(table.name().toUpperCase(java.util.Locale.ROOT), Map.of()), base.dialect(), set.vars());
            GenModel.Result m = GenModel.of(table, o, types);
            warnings.addAll(m.warnings());
            if (m.model().isEmpty()) {
                continue;
            }
            List<Planned> mine = new ArrayList<>();
            try {
                for (TemplateSet.FileSpec f : set.files()) {
                    String rel = t.renderPath(f.path(), m.model()).trim();
                    Path target = out.resolve(rel).normalize();
                    if (rel.isEmpty() || !target.startsWith(out) || target.equals(out)) {
                        throw new IllegalArgumentException(set.name() + " 의 경로 식이 출력 폴더 밖을 가리킨다: " + f.path());
                    }
                    mine.add(new Planned(target, table.name(), t.render(f.template(), m.model())));
                }
            } catch (IOException e) {
                warnings.add(table.name() + ": " + e.getMessage());
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
            if (Files.exists(p.target(), LinkOption.NOFOLLOW_LINKS)) {
                files.create(p.target() + ".gen", out.toString(), p.text(), encoding, lineEnding, true);
                done.add(new GenFile(rel + ".gen", p.table(), "sidecar"));
            } else {
                files.create(p.target().toString(), out.toString(), p.text(), encoding, lineEnding, false);
                done.add(new GenFile(rel, p.table(), "created"));
            }
        }
        if (ctx != null) {
            ctx.progress(100, done.size() + " 파일");
        }
        return new Result(done, warnings);
    }
}
