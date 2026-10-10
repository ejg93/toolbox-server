package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.profile.Profile;

/**
 * 4-1 — 로컬 파일 층 `/api/fs/*`(8장).
 * <ul>
 *   <li>`GET /api/fs/list?path=&glob=*.jsp,*.java` → `{files[{rel, size, mtime}], truncated}`</li>
 *   <li>`GET /api/fs/read?path=` → `{text, encoding, lineEnding}`</li>
 *   <li>`POST /api/fs/write {path, root, text, encoding, lineEnding, stamp?}` → `{backup, stamp}` —
 *       백업은 `out/<프로필>/<stamp>/backup/<root 기준 상대 경로>`. 일괄 쓰기는 첫 응답의 stamp 를 다시 보내 한 폴더에 모은다</li>
 *   <li>`POST /api/fs/out {name, text, stamp?}` → `{path, stamp}` — 1-58f jsp_formatter 파일 하나 정리 결과를
 *       `out/<프로필>/<stamp>/정리_<name>` 에 UTF-8 로. name 은 파일 이름만(경로·`..` 거절)</li>
 *   <li>`GET /api/fs/recent` · `GET /api/fs/exists?path=`</li>
 *   <li>`GET /api/fs/defaults` → `{projectRoot, recent[]}` — 화면의 경로 칸 기본값(5-5: 프로필 프로젝트 루트 + 최근 목록)</li>
 * </ul>
 * 로그에 경로·내용을 안 남긴다(절대 규칙 3).
 */
final class FsRoutes {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern STAMP_RE = Pattern.compile("\\d{8}-\\d{6}");
    /** 1-58f — 파일 이름에 못 오는 글자. 제어 문자(줄바꿈 포함)도 — matches(".*…") 는 줄바꿈 뒤를 못 봤다(리뷰) */
    private static final Pattern BAD_NAME = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private FsRoutes() {
    }

    record WriteRequest(String path, String root, String text, String encoding, String lineEnding, String stamp) {
    }

    record OutRequest(String name, String text, String stamp) {
    }

    static void register(Javalin app, LocalFiles files, Supplier<Optional<Profile>> active) {
        app.exception(LocalFiles.Refused.class, (e, ctx) -> ctx.status(e.status()).json(Map.of("message", e.getMessage())));

        app.get("/api/fs/list", ctx -> {
            String dir = ctx.queryParam("path");
            String glob = ctx.queryParam("glob");
            List<String> globs = glob == null ? List.of() : Arrays.asList(glob.split(","));
            LocalFiles.Listing l = files.list(dir, globs, LocalFiles.MAX_FILES);
            files.remember(files.check(dir));
            ctx.json(l);
        });
        app.get("/api/fs/read", ctx -> ctx.json(files.read(ctx.queryParam("path"))));
        app.post("/api/fs/write", ctx -> {
            WriteRequest req = ctx.bodyAsClass(WriteRequest.class);
            String stamp = req.stamp() != null && STAMP_RE.matcher(req.stamp()).matches()
                    ? req.stamp() : LocalDateTime.now().format(STAMP);
            Path root = backupRoot(active, stamp);
            Path backup = files.write(req.path(), req.root(), req.text(), req.encoding(), req.lineEnding(), root);
            ctx.json(Map.of("backup", backup.toString(), "stamp", stamp, "backupRoot", root.toString()));
        });
        app.post("/api/fs/out", ctx -> {
            OutRequest req = ctx.bodyAsClass(OutRequest.class);
            String name = req.name() == null ? "" : req.name().trim();
            if (name.isEmpty() || name.length() > 200 || name.contains("..") || BAD_NAME.matcher(name).find()) {
                throw new LocalFiles.Refused(400, "파일 이름만 — 경로·「..」·금지 문자 없이");
            }
            String stamp = req.stamp() != null && STAMP_RE.matcher(req.stamp()).matches()
                    ? req.stamp() : LocalDateTime.now().format(STAMP);
            Path file = outRoot(active, stamp).resolve("정리_" + name);
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, req.text() == null ? "" : req.text(), java.nio.charset.StandardCharsets.UTF_8);
            ctx.json(Map.of("path", file.toString(), "stamp", stamp));
        });
        app.get("/api/fs/recent", ctx -> ctx.json(files.recent()));
        app.get("/api/fs/defaults", ctx -> {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("projectRoot", active.get().map(Profile::project).map(Profile.Project::root).orElse(null));
            body.put("recent", files.recent());
            ctx.json(body);
        });
        app.get("/api/fs/exists", ctx -> ctx.json(files.exists(ctx.queryParam("path"))));
    }

    /** out/<프로필>/<stamp>/backup — 프로필이 없으면 `default`(12장) */
    static Path backupRoot(Supplier<Optional<Profile>> active, String stamp) {
        return outRoot(active, stamp).resolve("backup");
    }

    /** out/<프로필>/<stamp> — 1-58f 파일 하나 정리 결과가 여기. 여러 개는 첫 응답의 stamp 를 다시 보내 한 폴더에 */
    static Path outRoot(Supplier<Optional<Profile>> active, String stamp) {
        Optional<Profile> p = active.get();
        String base = p.map(Profile::output).map(Profile.Output::dir).orElse("out");
        String name = p.map(Profile::name).orElse("default");
        return Path.of(base, name, stamp).toAbsolutePath();
    }
}
