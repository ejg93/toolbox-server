package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.fs.FolderDiff;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.text.LineDiff;

/**
 * 4-2 — 폴더 비교(8장). 화면은 4-9(dev_tools 「폴더 비교」 탭).
 * <ul>
 *   <li>`POST /api/diff/folders {a, b, glob}` → `{items[{rel, status, sizeA, sizeB}], truncated}`</li>
 *   <li>`GET /api/diff/file?a=&b=` → `{ops[{op, line}], tooBig, encodingA, encodingB}`</li>
 * </ul>
 */
final class DiffRoutes {

    private DiffRoutes() {
    }

    record FoldersRequest(String a, String b, String glob) {
    }

    static void register(Javalin app, LocalFiles files) {
        app.post("/api/diff/folders", ctx -> {
            FoldersRequest req = ctx.bodyAsClass(FoldersRequest.class);
            List<String> globs = req.glob() == null ? List.of() : Arrays.asList(req.glob().split(","));
            ctx.json(FolderDiff.compare(files, req.a(), req.b(), globs));
        });
        app.get("/api/diff/file", ctx -> {
            LocalFiles.Text a = files.read(ctx.queryParam("a"));
            LocalFiles.Text b = files.read(ctx.queryParam("b"));
            LineDiff.Result r = LineDiff.diff(a.text(), b.text());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ops", r.ops());
            body.put("tooBig", r.tooBig());
            body.put("encodingA", a.encoding());
            body.put("encodingB", b.encoding());
            ctx.json(body);
        });
    }
}
