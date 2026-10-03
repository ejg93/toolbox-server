package kr.ejg.toolbox.cli;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kr.ejg.toolbox.core.text.Csv;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * 8-5 — 논리명: COMMENT DDL · 표준 후보 CSV · 표준 미준수 리포트 · 개인정보 마스킹 SQL.
 * DB 에 COMMENT 를 실행하는 길(comments/apply)은 이름 붙은 명령을 안 만든다 — 되돌리기 어려운 쓰기라 화면에서 사람이 누른다.
 */
@Command(name = "logical", mixinStandardHelpOptions = true, description = "논리명 — COMMENT DDL·표준 후보·미준수 리포트·마스킹 SQL",
        subcommands = {LogicalCommands.Comments.class, LogicalCommands.Candidates.class, LogicalCommands.Audit.class, LogicalCommands.Masking.class})
final class LogicalCommands implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    /** 입력 — 스냅샷 또는 컬럼목록 CSV */
    abstract static class Input extends BatchCommand {

        @Option(names = "--snapshot", description = "스냅샷 id 또는 latest")
        String snapshot;

        @Option(names = "--csv", description = "컬럼목록 CSV 파일(순수본 양식)")
        String csv;

        @Option(names = "--owner", description = "CSV 에 소유자 열이 없을 때 쓸 소유자")
        String owner;

        @Option(names = "--skip-tokens", split = ",", description = "무시 토큰(표 이름에만). 없으면 프로필 logicalName.skipTokens")
        List<String> skipTokens;

        Map<String, Object> input() throws Exception {
            Map<String, Object> b = new LinkedHashMap<>();
            if (csv != null && snapshot != null) {
                throw batch.fail(2, "--snapshot 과 --csv 는 하나만");
            }
            if (csv != null) {
                b.put("csv", Csv.decode(Files.readAllBytes(batch.userPath(csv))));
                DocCommands.putIf(b, "owner", owner);
            } else if (snapshot != null) {
                b.put("snapshotId", batch.snapshotId(snapshot));
            } else {
                throw batch.fail(2, "입력이 없다 — --snapshot <id·latest> 또는 --csv <파일>");
            }
            DocCommands.putIf(b, "skipTokens", skipTokens);
            return b;
        }
    }

    @Command(name = "comments", mixinStandardHelpOptions = true, description = "논리명으로 COMMENT DDL 을 만들어 파일로 저장한다(DB 에 실행 안 함).")
    static final class Comments extends Input {

        @Option(names = "--dialect", description = "oracle·tibero·postgresql·mariadb·mssql·sybase. 없으면 oracle")
        String dialect;

        @Option(names = "--no-tables", description = "테이블 COMMENT 는 빼고 컬럼만")
        boolean noTables;

        @Override
        int body() throws Exception {
            Map<String, Object> b = input();
            DocCommands.putIf(b, "dialect", dialect);
            b.put("includeTables", !noTables);
            JsonNode r = batch.call("POST", "/api/logical/comments?save=true", b);
            batch.print(r, r.path("path").asText() + " — " + r.path("lines").asInt() + "줄 · 검토 " + r.path("reviewCount").asInt());
            return 0;
        }
    }

    @Command(name = "candidates", mixinStandardHelpOptions = true, description = "산출물 05·06·07 후보와 공통표준단어 사용여부를 CSV 로 저장한다.")
    static final class Candidates extends Input {

        @Option(names = "--kind", required = true, description = "terms(05 표준용어)·words(06 표준단어)·domains(07 표준도메인)·wordUse(사용여부)")
        String kind;

        @Option(names = "--db-name", description = "DB명 칸")
        String dbName;

        @Option(names = "--exclude-review", description = "검토필요 줄을 뺀다")
        boolean excludeReview;

        @Override
        int body() throws Exception {
            Map<String, Object> b = input();
            b.put("kind", kind);
            DocCommands.putIf(b, "dbName", dbName);
            b.put("excludeReview", excludeReview);
            JsonNode r = batch.call("POST", "/api/logical/candidates", b);
            batch.print(r, r.path("path").asText() + " — " + r.path("lines").asInt() + "행");
            return 0;
        }
    }

    @Command(name = "audit", mixinStandardHelpOptions = true, description = "스냅샷의 표준 미준수 리포트(xlsx). 코멘트는 스냅샷에만 있어 CSV 입력은 안 받는다.")
    static final class Audit extends BatchCommand {

        @Option(names = "--snapshot", required = true, description = "스냅샷 id 또는 latest")
        String snapshot;

        @Option(names = "--rules", split = ",", description = "규칙 이름(NO_COMMENT 등). 없으면 기본 규칙")
        List<String> rules;

        @Option(names = "--skip-tokens", split = ",", description = "무시 토큰. 없으면 프로필 logicalName.skipTokens")
        List<String> skipTokens;

        @Override
        int body() throws Exception {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("snapshotId", batch.snapshotId(snapshot));
            DocCommands.putIf(b, "rules", rules);
            DocCommands.putIf(b, "skipTokens", skipTokens);
            JsonNode r = batch.call("POST", "/api/logical/audit", b);
            StringBuilder sb = new StringBuilder(r.path("path").asText() + " — 미준수 " + r.path("findings").size());
            r.path("counts").fields().forEachRemaining(e -> sb.append(" · ").append(e.getKey()).append(' ').append(e.getValue().asInt()));
            batch.print(r, sb.toString());
            return 0;
        }
    }

    @Command(name = "masking", mixinStandardHelpOptions = true,
            description = {"개인정보 컬럼(주민번호·전화·이름·이메일·주소·계좌·카드·생년월일)을 찾아 마스킹 UPDATE 를 파일로 저장한다.",
                    "실행하지 않는다 — 개발·시험 사본에서 사람이 돌린다."})
    static final class Masking extends Input {

        @Option(names = "--dialect", description = "oracle·tibero·postgresql·mariadb·mssql. 없으면 스냅샷 DB, 그것도 없으면 oracle")
        String dialect;

        @Option(names = "--exclude", split = ",", description = "뺄 컬럼(표.컬럼)")
        List<String> exclude;

        @Override
        int body() throws Exception {
            Map<String, Object> b = input();
            DocCommands.putIf(b, "dialect", dialect);
            List<Map<String, String>> ex = new ArrayList<>();
            if (exclude != null) {
                for (String e : exclude) {
                    int dot = e.lastIndexOf('.');
                    if (dot <= 0) {
                        throw batch.fail(2, "--exclude 는 표.컬럼: " + e);
                    }
                    ex.add(Map.of("table", e.substring(0, dot), "col", e.substring(dot + 1)));
                }
            }
            b.put("exclude", ex);
            b.put("save", true);
            JsonNode r = batch.call("POST", "/api/logical/masking", b);
            r.path("warnings").forEach(w -> batch.err().println("[경고] " + w.asText()));
            Map<String, Integer> kinds = new TreeMap<>();
            int used = 0;
            for (JsonNode c : r.path("candidates")) {
                if (!c.path("excluded").asBoolean() && c.path("text").asBoolean()) {
                    used++;
                    kinds.merge(c.path("kind").asText(), 1, Integer::sum);
                }
            }
            batch.print(r, r.path("path").asText() + " — " + r.path("dialect").asText() + " · 후보 " + r.path("candidates").size() + " · SQL 에 든 컬럼 "
                    + used + " " + kinds);
            return 0;
        }
    }
}
