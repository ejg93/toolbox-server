package kr.ejg.toolbox.cli;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** 8-6 — 소스 폴더: 코드 검사 · 프로그램 분석 · 배포 파일 목록. 폴더는 부른 폴더 기준으로 절대 경로로 바꿔 보낸다 */
final class CodeCommands {

    static final List<String> SEVERITIES = List.of("error", "warn", "info");

    private CodeCommands() {
    }

    @Command(name = "check", mixinStandardHelpOptions = true,
            description = {"폴더를 코드 검사한다(규칙은 프로필 codecheck). 결과는 이력에 남고 xlsx 로도 낸다.",
                    "--fail-on 등급 이상이 한 건이라도 있으면 끝 코드 3 — 배치·형상 훅에서 쓴다."})
    static final class Check extends BatchCommand {

        @Parameters(index = "0", description = "검사할 폴더")
        String folder;

        @Option(names = "--changed-only", description = "git·svn 변경분만")
        boolean changedOnly;

        @Option(names = "--groups", split = ",", description = "켤 규칙 묶음(나머지는 끈다). 없으면 프로필 그대로")
        List<String> groups;

        @Option(names = "--xlsx", description = "결과를 xlsx 로도")
        boolean xlsx;

        @Option(names = "--fail-on", description = "error·warn·info — 이 등급 이상이 있으면 끝 코드 3")
        String failOn;

        @Override
        int body() throws Exception {
            if (failOn != null && !SEVERITIES.contains(failOn.toLowerCase(Locale.ROOT))) {
                throw batch.fail(2, "--fail-on 은 error·warn·info: " + failOn);
            }
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("path", batch.userPath(folder).toString());
            b.put("changedOnly", changedOnly);
            if (groups != null) {
                Map<String, Boolean> g = new LinkedHashMap<>();
                groups.forEach(x -> g.put(x.trim(), true));
                b.put("groups", g);
            }
            JsonNode r = batch.job(batch.call("POST", "/api/check/run", b));
            Map<String, Integer> bySeverity = new TreeMap<>();
            SEVERITIES.forEach(s -> bySeverity.put(s, 0));
            for (JsonNode f : r.path("findings")) {
                bySeverity.merge(f.path("severity").asText(), 1, Integer::sum);
            }
            StringBuilder sb = new StringBuilder("검사 " + r.path("runId").asLong() + " — 파일 " + r.path("files").asInt() + " · error "
                    + bySeverity.get("error") + " · warn " + bySeverity.get("warn") + " · info " + bySeverity.get("info"));
            if (r.path("parseErrors").asInt() > 0) {
                sb.append(" · 파싱 못 함 ").append(r.path("parseErrors").asInt());
            }
            if (r.path("suppressed").asInt() > 0) {
                sb.append(" · 억제 ").append(r.path("suppressed").asInt());
            }
            if (xlsx) {
                JsonNode x = batch.call("POST", "/api/check/runs/" + r.path("runId").asLong() + "/export", Map.of());
                sb.append('\n').append(x.path("path").asText());
            }
            batch.print(r, sb.toString());
            if (failOn != null) {
                int at = SEVERITIES.indexOf(failOn.toLowerCase(Locale.ROOT));
                for (int i = 0; i <= at; i++) {
                    if (bySeverity.get(SEVERITIES.get(i)) > 0) {
                        batch.err().println("[실패] " + failOn + " 이상 " + SEVERITIES.subList(0, at + 1));
                        return 3;
                    }
                }
            }
            return 0;
        }
    }

    @Command(name = "cleanup", mixinStandardHelpOptions = true,
            description = {"폴더의 코드 검사 지적 가운데 자동으로 고칠 수 있는 아홉을 미리보기한다(쓰지 않음).",
                    "--write 면 바뀐 파일을 결과 폴더에 같은 상대 경로로 쓴다(원본은 그대로). 규칙은 프로필 codecheck 와 같다.",
                    "결과 폴더는 --out, 없으면 out/<프로필>/<시각>/cleanup. 결과 글은 out/<프로필>/<시각>/정리_결과.txt"})
    static final class Cleanup extends BatchCommand {

        @Parameters(index = "0", description = "고칠 폴더")
        String folder;

        @Option(names = "--write", description = "미리보기 뒤 바뀐 파일을 결과 폴더에 쓴다(원본은 그대로)")
        boolean write;

        @Option(names = "--out", description = "결과 폴더 — 빈 폴더이거나 이 도구가 만든 폴더. 원본 폴더 안은 거절")
        String out;

        @Option(names = "--changed-only", description = "git·svn 변경분만")
        boolean changedOnly;

        @Option(names = "--rules", split = ",", description = "고칠 규칙 id(없으면 아홉 전부)")
        List<String> rules;

        @Override
        int body() throws Exception {
            String path = batch.userPath(folder).toString();
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("path", path);
            b.put("rules", rules == null ? List.of() : rules);
            b.put("changedOnly", changedOnly);
            JsonNode pv = batch.job(batch.call("POST", "/api/cleanup/preview", b));
            List<Map<String, Object>> refs = new java.util.ArrayList<>();
            List<String> lines = new java.util.ArrayList<>();
            int skipped = 0;
            for (JsonNode row : pv.path("rows")) {
                skipped += row.path("skips").size();
                if (!row.path("changed").asBoolean()) {
                    continue;
                }
                java.util.Set<String> stages = new java.util.LinkedHashSet<>();
                row.path("steps").forEach(x -> stages.add(x.path("stage").asText()));
                lines.add("  " + row.path("rel").asText() + " · " + String.join(",", stages) + " · " + row.path("before").asInt() + " → "
                        + row.path("after").asInt());
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("rel", row.path("rel").asText());
                ref.put("hash", row.path("hash").asText());
                ref.put("outHash", row.path("outHash").asText());
                refs.add(ref);
            }
            lines.add(0, "정리 미리보기 — 파일 " + pv.path("files").asInt() + " · 바뀜 " + pv.path("changed").asInt() + " · 건너뜀 " + skipped);
            if (!write || refs.isEmpty()) {
                batch.print(pv, String.join("\n", lines));
                return 0;
            }
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("path", path);
            a.put("rules", rules == null ? List.of() : rules);
            a.put("files", refs);
            if (out != null) {
                a.put("outDir", batch.userPath(out).toString());
            }
            JsonNode ap = batch.job(batch.call("POST", "/api/cleanup/apply", a));
            lines.add("썼다 " + ap.path("written").asInt() + "/" + refs.size() + " · 실패 " + ap.path("failed").asInt() + " · 낡음 "
                    + ap.path("stale").asInt());
            lines.add("결과 폴더 " + ap.path("outRoot").asText() + " — 원본은 그대로");
            lines.add(ap.path("report").asText());
            batch.print(ap, String.join("\n", lines));
            return ap.path("failed").asInt() > 0 || ap.path("stale").asInt() > 0 ? 1 : 0; // 낡음도 배치엔 실패 — 다시 미리보기해야
        }
    }

    @Command(name = "analyze", mixinStandardHelpOptions = true, description = "소스 폴더를 프로그램 분석한다(컨트롤러 → 매퍼 → SQL → 표, CRUD 매트릭스).")
    static final class Analyze extends BatchCommand {

        @Parameters(index = "0", description = "소스 폴더")
        String folder;

        @Option(names = "--xlsx", description = "프로그램 분석 xlsx 한 파일(시트 프로그램목록·CRUD목록·CRUD모듈·미해결)")
        boolean xlsx;

        @Override
        int body() throws Exception {
            JsonNode r = batch.job(batch.call("POST", "/api/analyze/run", Map.of("path", batch.userPath(folder).toString())));
            StringBuilder sb = new StringBuilder("분석 " + r.path("runId").asLong() + " — 파일 " + r.path("files").asInt() + " · 프로그램 "
                    + r.path("programs").size() + " · 표 " + r.path("tables").size() + " · 미해결 " + r.path("unresolved").size());
            if (xlsx) {
                JsonNode x = batch.call("POST", "/api/analyze/runs/" + r.path("runId").asLong() + "/export", Map.of("format", "xlsx"));
                x.path("files").forEach(f -> sb.append('\n').append(f.path("path").asText()));
            }
            batch.print(r, sb.toString());
            return 0;
        }
    }

    @Command(name = "deploy-list", mixinStandardHelpOptions = true, description = "git·svn 작업 사본의 두 리비전 사이 바뀐 파일 목록(배포 목록).")
    static final class DeployList extends BatchCommand {

        @Parameters(index = "0", description = "작업 사본 폴더")
        String folder;

        @Option(names = "--from", required = true, description = "앞 리비전")
        String from;

        @Option(names = "--to", required = true, description = "뒤 리비전")
        String to;

        @Option(names = "--xlsx", description = "xlsx 로도")
        boolean xlsx;

        @Override
        int body() throws Exception {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("path", batch.userPath(folder).toString());
            b.put("from", from);
            b.put("to", to);
            b.put("xlsx", xlsx);
            JsonNode r = batch.call("POST", "/api/check/deploy-list", b);
            JsonNode c = r.path("counts");
            StringBuilder sb = new StringBuilder(r.path("kind").asText() + " " + from + " → " + to + " — 추가 " + c.path("A").asInt() + " · 수정 "
                    + c.path("M").asInt() + " · 삭제 " + c.path("D").asInt());
            if (r.hasNonNull("xlsxPath")) {
                sb.append('\n').append(r.path("xlsxPath").asText());
            }
            batch.print(r, sb.toString());
            return 0;
        }
    }
}
