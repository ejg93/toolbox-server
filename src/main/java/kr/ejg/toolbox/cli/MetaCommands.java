package kr.ejg.toolbox.cli;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import kr.ejg.toolbox.web.LocalApp;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** 8-3 — 모든 라우트의 뒷문 api · 스냅샷 뜨기·목록·비교 */
final class MetaCommands {

    private MetaCommands() {
    }

    @Command(name = "api", mixinStandardHelpOptions = true,
            description = {"화면이 쓰는 API 를 그대로 부른다(서버를 안 띄우고, 듣는 포트 없이). 응답 본문을 그대로 낸다.",
                    "본문은 JSON 만 — 파일 올리기(multipart)·실시간 진행(SSE) 라우트는 못 탄다."})
    static final class Api extends BatchCommand {

        @Parameters(index = "0", description = "GET·POST·PUT·DELETE")
        String method;

        @Parameters(index = "1", description = {"경로. 예 /api/ping, api/meta/diff?a=1&b=2 — 앞의 / 는 없어도 된다", "Git Bash 는 / 로 시작하는 인자를 윈도 경로로 바꾼다 — / 를 빼거나 MSYS_NO_PATHCONV=1"})
        String path;

        @Option(names = "--body", description = "JSON 본문")
        String body;

        @Option(names = "--body-file", description = "JSON 본문 파일(UTF-8)")
        String bodyFile;

        @Option(names = "--wait", description = "응답이 202 {jobId} 면 작업이 끝날 때까지 기다려 결과를 낸다")
        boolean waitJob;

        @Override
        boolean needsProfile() {
            return false;
        }

        @Override
        int body() throws Exception {
            String b = body;
            if (bodyFile != null) {
                if (b != null) {
                    throw batch.fail(2, "--body 와 --body-file 은 하나만");
                }
                b = Files.readString(batch.userPath(bodyFile), StandardCharsets.UTF_8);
            }
            LocalApp.Response r;
            try {
                r = batch.raw(method.toUpperCase(Locale.ROOT), path.startsWith("/") ? path : "/" + path, b);
            } catch (IllegalArgumentException e) {
                throw batch.fail(2, e.getMessage());
            }
            JsonNode node = Batch.parse(r);
            if (r.status() == 202 && waitJob && node.has("jobId")) {
                JsonNode result = batch.job(node);
                batch.out().println(result.toString());
                return 0;
            }
            batch.out().println(node.isTextual() ? node.asText() : node.toString());
            if (r.status() >= 400) {
                batch.err().println("[오류] " + r.status());
                return 1;
            }
            return 0;
        }
    }

    @Command(name = "snapshot", mixinStandardHelpOptions = true, description = "DB 접속으로 메타 스냅샷을 뜬다(표·컬럼·제약·코멘트). 비밀번호는 프롬프트 또는 환경변수.")
    static final class Snapshot extends BatchCommand {

        @Option(names = "--conn", required = true, description = "프로필의 접속 id")
        String conn;

        @Option(names = "--note", description = "메모")
        String note;

        @Override
        int body() throws Exception {
            batch.password(conn);
            JsonNode accepted = batch.call("POST", "/api/meta/snapshot", note == null ? Map.of("connId", conn) : Map.of("connId", conn, "note", note));
            JsonNode r = batch.job(accepted);
            batch.print(r, "스냅샷 " + r.path("snapshotId").asLong() + " — 스키마 " + r.path("schemas").asInt() + " · 표 " + r.path("tables").asInt());
            return 0;
        }
    }

    @Command(name = "snapshots", mixinStandardHelpOptions = true, description = "저장된 스냅샷 목록.")
    static final class Snapshots extends BatchCommand {

        @Override
        boolean needsProfile() {
            return false;
        }

        @Override
        int body() throws Exception {
            JsonNode list = batch.call("GET", "/api/meta/snapshots", null);
            StringBuilder sb = new StringBuilder("id\t시각\t프로필\t접속\t표\t메모");
            for (JsonNode n : list) {
                sb.append('\n').append(n.path("id").asLong()).append('\t').append(n.path("takenAt").asText("")).append('\t')
                        .append(n.path("profile").asText("")).append('\t').append(n.path("connId").asText("")).append('\t')
                        .append(n.path("tableCount").asInt()).append('\t').append(n.path("note").asText(""));
            }
            batch.print(list, sb.toString());
            return 0;
        }
    }

    @Command(name = "diff", mixinStandardHelpOptions = true, description = "두 스냅샷의 표·컬럼·제약 차이.")
    static final class Diff extends BatchCommand {

        @Option(names = "--from", required = true, description = "앞 스냅샷 id 또는 latest")
        String from;

        @Option(names = "--to", required = true, description = "뒤 스냅샷 id 또는 latest")
        String to;

        @Option(names = "--keep-schema", description = "스키마 이름이 다르면 다른 표로 본다(기본은 스키마를 무시)")
        boolean keepSchema;

        @Override
        boolean needsProfile() {
            return false;
        }

        @Override
        int body() throws Exception {
            long a = batch.snapshotId(from);
            long b = batch.snapshotId(to);
            JsonNode d = batch.call("GET", "/api/meta/diff?a=" + a + "&b=" + b + (keepSchema ? "&ignoreSchema=false" : ""), null);
            int added = d.path("addedTables").size();
            int removed = d.path("removedTables").size();
            int changed = d.path("changedTables").size();
            StringBuilder sb = new StringBuilder("스냅샷 " + a + " → " + b + " — 표 추가 " + added + " · 삭제 " + removed + " · 변경 " + changed);
            d.path("addedTables").forEach(t -> sb.append("\n+ ").append(t.asText()));
            d.path("removedTables").forEach(t -> sb.append("\n- ").append(t.asText()));
            d.path("changedTables").forEach(t -> sb.append("\n~ ").append(t.path("name").asText()));
            batch.print(d, sb.toString());
            return 0;
        }
    }
}
