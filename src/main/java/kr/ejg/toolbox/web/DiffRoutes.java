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
 *   <li>`GET /api/diff/file?a=&b=` → `{ops[{op, line}], tooBig, encodingA, encodingB, sameLines, byteDiffs}` — 줄 내용이 같으면 sameLines 와 다른 것 목록(R-G-15)</li>
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
        app.get("/api/diff/file", ctx -> file(ctx, files));
    }

    private static void file(io.javalin.http.Context ctx, LocalFiles files) throws java.io.IOException {
        LocalFiles.Text a = files.read(ctx.queryParam("a"));
        LocalFiles.Text b = files.read(ctx.queryParam("b"));
        LineDiff.Result r = LineDiff.diff(a.text(), b.text());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ops", r.ops());
        body.put("tooBig", r.tooBig());
        body.put("encodingA", a.encoding());
        body.put("encodingB", b.encoding());
        boolean same = !r.tooBig() && r.ops().stream().allMatch(o -> "=".equals(o.op()));
        body.put("sameLines", same);
        body.put("byteDiffs", same ? byteDiffs(a, b) : List.of());
        ctx.json(body);
    }

    /** 줄 내용이 같은 두 파일에서 다른 것 — 줄끝 · 끝 줄바꿈 · 인코딩(R-G-15). 셋 다 같으면 빈 목록 — 양쪽 다 줄끝이 섞였고 섞인 자리만 다르다(4-45) */
    static List<String> byteDiffs(LocalFiles.Text a, LocalFiles.Text b) {
        List<String> out = new java.util.ArrayList<>();
        if (!java.util.Objects.equals(a.lineEnding(), b.lineEnding()) || a.mixed() != b.mixed()) {
            out.add("줄끝 " + a.lineEnding() + (a.mixed() ? "(섞임)" : "") + " · " + b.lineEnding() + (b.mixed() ? "(섞임)" : ""));
        }
        if (endsWithNewline(a.text()) != endsWithNewline(b.text())) {
            out.add("끝 줄바꿈 " + (endsWithNewline(a.text()) ? "A 만" : "B 만"));
        }
        if (!java.util.Objects.equals(a.encoding(), b.encoding())) {
            out.add("인코딩 " + a.encoding() + " · " + b.encoding());
        }
        return out;
    }

    private static boolean endsWithNewline(String s) {
        return s != null && s.endsWith("\n");
    }
}
