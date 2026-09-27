package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.profile.Profile;
import kr.ejg.toolbox.core.report.TableXlsx;

/**
 * 4-6 — `POST /api/table/export {model, format: xlsx}` → `{path}`. 파일은 `out/<프로필>/<yyyyMMdd-HHmmss>/table.xlsx`(12장).
 * 셀 글은 파일에만 쓰고 로그에 안 남긴다(절대 규칙 3).
 */
final class TableRoutes {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private TableRoutes() {
    }

    record ExportRequest(TableXlsx.TableModel model, String format) {
    }

    static void register(Javalin app, Supplier<Optional<Profile>> active) {
        app.post("/api/table/export", ctx -> {
            ExportRequest req = ctx.bodyAsClass(ExportRequest.class);
            if (req.model() == null) {
                ctx.status(400).json(Map.of("message", "model 이 있어야 한다"));
                return;
            }
            if (req.format() != null && !req.format().equals("xlsx")) {
                ctx.status(400).json(Map.of("message", "format 은 xlsx 만 — hwp 는 2-7 조건부"));
                return;
            }
            Optional<Profile> p = active.get();
            String base = p.map(Profile::output).map(Profile.Output::dir).orElse("out");
            String name = p.map(Profile::name).orElse("default");
            Path file = Path.of(base, name, LocalDateTime.now().format(STAMP), "table.xlsx").toAbsolutePath();
            try {
                TableXlsx.write(req.model(), file);
            } catch (IllegalArgumentException | IllegalStateException e) {
                // 크기가 안 맞거나 병합이 겹친다 — 모델이 깨진 것. 사유에 셀 글은 없다
                ctx.status(400).json(Map.of("message", "표를 못 썼다: " + e.getMessage()));
                return;
            }
            ctx.json(Map.of("path", file.toString()));
        });
    }
}
