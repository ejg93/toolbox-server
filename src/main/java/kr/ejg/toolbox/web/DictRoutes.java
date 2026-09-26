package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import io.javalin.http.UploadedFile;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.dict.DictStore;
import kr.ejg.toolbox.core.dict.Dictionaries;

/**
 * 사전(3-4, 8장) — `GET /api/dict/words?q=&kind=` · `PUT /api/dict/user/{abbr} {ko}` · `DELETE /api/dict/user/{abbr}` ·
 * `POST /api/dict/org/import`(multipart file 또는 JSON {csv}) · `GET /api/dict/export?kind=`(CSV) ·
 * `POST /api/dict/import {lnUserDict, lnSkipTok, lnDialect}`(브라우저 localStorage 가져오기) · `GET /api/dict/import/needed`.
 * <p>무시토큰·방언은 응답으로만 돌려준다 — 프로필 YAML 을 코드로 다시 쓰면 사용자 주석이 사라진다(3-4 계획 밖).
 */
final class DictRoutes {

    record UserWord(String ko) {
    }

    record OrgCsv(String csv) {
    }

    record LocalStorageImport(Map<String, String> lnUserDict, String lnSkipTok, String lnDialect) {
    }

    private DictRoutes() {
    }

    static void register(Javalin app, DictStore dict) {
        app.get("/api/dict/words", ctx -> ctx.json(dict.words(ctx.queryParam("q"), ctx.queryParam("kind"))));

        app.put("/api/dict/user/{abbr}", ctx -> {
            UserWord w = ctx.bodyAsClass(UserWord.class);
            try {
                dict.putUser(ctx.pathParam("abbr"), w.ko());
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", e.getMessage()));
                return;
            }
            ctx.json(Map.of("ok", true));
        });

        app.delete("/api/dict/user/{abbr}", ctx -> ctx.json(Map.of("deleted", dict.deleteUser(ctx.pathParam("abbr")))));

        app.post("/api/dict/org/import", ctx -> {
            byte[] bytes;
            UploadedFile f = ctx.isMultipartFormData() ? ctx.uploadedFile("file") : null;
            if (f != null) {
                // 스트림을 닫아야 Jetty 가 multipart 임시 파일을 지운다(Windows 파일 잠금)
                try (java.io.InputStream in = f.content()) {
                    bytes = in.readAllBytes();
                }
            } else if (ctx.isMultipartFormData()) {
                ctx.status(400).json(Map.of("message", "file 칸이 없다"));
                return;
            } else {
                OrgCsv body = ctx.bodyAsClass(OrgCsv.class);
                bytes = body.csv() == null ? new byte[0] : body.csv().getBytes(StandardCharsets.UTF_8);
            }
            try {
                ctx.json(Map.of("imported", dict.importOrg(bytes)));
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("message", e.getMessage()));
            }
        });

        app.get("/api/dict/export", ctx -> {
            String kind = ctx.queryParam("kind") == null ? "user" : ctx.queryParam("kind");
            Dictionaries d = dict.load();
            Map<String, String> m = switch (kind) {
                case "org" -> d.org();
                case "word" -> d.word();
                default -> d.user();
            };
            StringBuilder sb = new StringBuilder().append((char) 0xFEFF).append("영문약어,한글\r\n");
            m.keySet().stream().sorted().forEach(k -> sb.append(csv(k)).append(',').append(csv(m.get(k))).append("\r\n"));
            ctx.header("Content-Disposition", "attachment; filename=\"dict-" + kind + ".csv\"");
            ctx.contentType("text/csv; charset=UTF-8").result(sb.toString());
        });

        // 첫 기동 안내 — 사용자 사전이 비었으면 화면이 브라우저 localStorage 가져오기를 한 번 권한다(2.2 A)
        app.get("/api/dict/import/needed", ctx -> ctx.json(Map.of("needed", dict.count("user") == 0)));

        app.post("/api/dict/import", ctx -> {
            LocalStorageImport body = ctx.bodyAsClass(LocalStorageImport.class);
            Map<String, String> user = body.lnUserDict() == null ? Map.of() : body.lnUserDict();
            Map<String, String> existing = dict.load().user();
            int imported = 0;
            int skipped = 0;
            for (Map.Entry<String, String> e : user.entrySet()) {
                String abbr = e.getKey() == null ? "" : e.getKey().trim().toUpperCase(java.util.Locale.ROOT);
                if (abbr.isEmpty() || e.getValue() == null || e.getValue().isBlank() || existing.containsKey(abbr)) {
                    skipped++; // 서버에 이미 있으면 서버 쪽을 둔다
                    continue;
                }
                dict.putUser(abbr, e.getValue());
                imported++;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("imported", imported);
            out.put("skipped", skipped);
            out.put("skipTokens", body.lnSkipTok() == null ? List.of()
                    : List.of(body.lnSkipTok().split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
            out.put("dialect", body.lnDialect());
            ctx.json(out);
        });
    }

    private static String csv(String s) {
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
