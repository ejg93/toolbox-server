package kr.ejg.toolbox.cli;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** 8-4 — 스냅샷으로 만드는 것: 산출물 xlsx · DDL · DTO · CRUD 생성. 안 준 칸은 라우트가 프로필 기본값으로 채운다 */
final class DocCommands {

    private DocCommands() {
    }

    /** "이름" 또는 "스키마.이름" 목록 → [{schema, name}] */
    static List<Map<String, String>> tables(List<String> names) {
        List<Map<String, String>> out = new ArrayList<>();
        if (names == null) {
            return out;
        }
        for (String n : names) {
            String t = n.trim();
            if (t.isEmpty()) {
                continue;
            }
            Map<String, String> m = new LinkedHashMap<>();
            int dot = t.lastIndexOf('.');
            if (dot > 0) {
                m.put("schema", t.substring(0, dot));
                m.put("name", t.substring(dot + 1));
            } else {
                m.put("name", t);
            }
            out.add(m);
        }
        return out;
    }

    static void putIf(Map<String, Object> body, String key, Object v) {
        if (v != null) {
            body.put(key, v);
        }
    }

    @Command(name = "deliverable", mixinStandardHelpOptions = true, description = "스냅샷으로 산출물 xlsx(01~11)를 양식에 기입한다. 안 준 칸은 프로필 deliverable 기본값.")
    static final class Deliverable extends BatchCommand {

        @Option(names = "--snapshot", required = true, description = "스냅샷 id 또는 latest")
        String snapshot;

        @Option(names = "--docs", split = ",", description = "문서 번호(01,02,…). 없으면 전부")
        List<String> docs;

        @Option(names = "--author") String author;
        @Option(names = "--org") String org;
        @Option(names = "--dept") String dept;
        @Option(names = "--biz-area") String bizArea;
        @Option(names = "--db-desc") String dbDesc;
        @Option(names = "--db-name") String dbName;
        @Option(names = "--logical-db-name") String logicalDbName;
        @Option(names = "--os") String os;

        @Option(names = "--code-conn", description = "08 표준코드 값을 읽을 접속 id(비밀번호는 프롬프트 또는 환경변수)")
        String codeConn;

        @Override
        int body() throws Exception {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("snapshotId", batch.snapshotId(snapshot));
            putIf(b, "docs", docs);
            putIf(b, "author", author);
            putIf(b, "org", org);
            putIf(b, "dept", dept);
            putIf(b, "bizArea", bizArea);
            putIf(b, "dbDesc", dbDesc);
            putIf(b, "dbName", dbName);
            putIf(b, "logicalDbName", logicalDbName);
            putIf(b, "os", os);
            if (codeConn != null) {
                batch.password(codeConn);
                b.put("codeConnId", codeConn);
            }
            JsonNode accepted = batch.call("POST", "/api/deliverable/build", b);
            JsonNode r = batch.job(accepted);
            StringBuilder sb = new StringBuilder("산출물 " + r.path("files").size() + "개 — " + accepted.path("dir").asText(""));
            r.path("files").forEach(f -> sb.append('\n').append(f.asText()));
            r.path("skipped").forEach(f -> batch.err().println("[건너뜀] " + f.asText()));
            batch.print(r, sb.toString());
            return 0;
        }
    }

    @Command(name = "ddl", mixinStandardHelpOptions = true, description = "스냅샷 표를 대상 방언의 CREATE 스크립트로 만들어 파일로 저장한다(실행 안 함).")
    static final class Ddl extends BatchCommand {

        @Option(names = "--snapshot", required = true, description = "스냅샷 id 또는 latest")
        String snapshot;

        @Option(names = "--target", required = true, description = "oracle·tibero·postgresql·mariadb·mssql")
        String target;

        @Option(names = "--schema", description = "이름 앞에 붙일 스키마")
        String schema;

        @Option(names = "--tables", split = ",", description = "표(이름 또는 스키마.이름). 없으면 전부")
        List<String> tables;

        @Option(names = "--no-fk") boolean noFk;
        @Option(names = "--no-index") boolean noIndex;
        @Option(names = "--no-comments") boolean noComments;

        @Override
        int body() throws Exception {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("snapshotId", batch.snapshotId(snapshot));
            b.put("target", target);
            putIf(b, "schema", schema);
            b.put("tables", tables(tables));
            b.put("includeFk", !noFk);
            b.put("includeIndex", !noIndex);
            b.put("includeComments", !noComments);
            b.put("save", true);
            JsonNode r = batch.call("POST", "/api/deliverable/ddl", b);
            r.path("warnings").forEach(w -> batch.err().println("[경고] " + w.asText()));
            batch.print(r, r.path("path").asText() + " — 표 " + r.path("tables").asInt() + " · " + r.path("source").asText("원본 모름") + " → "
                    + target + " · 경고 " + r.path("warnings").size());
            return 0;
        }
    }

    @Command(name = "dto", mixinStandardHelpOptions = true, description = "스냅샷 표로 DTO·VO 자바 소스를 만들어 파일로 저장한다.")
    static final class Dto extends BatchCommand {

        @Option(names = "--snapshot", required = true, description = "스냅샷 id 또는 latest")
        String snapshot;

        @Option(names = "--tables", split = ",", required = true, description = "표(이름 또는 스키마.이름)")
        List<String> tables;

        @Option(names = "--package", description = "패키지")
        String packageName;

        @Option(names = "--style", description = "record·bean·egovVo. 없으면 record")
        String style;

        @Override
        int body() throws Exception {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("snapshotId", batch.snapshotId(snapshot));
            b.put("tables", tables(tables));
            putIf(b, "packageName", packageName);
            putIf(b, "style", style);
            JsonNode r = batch.call("POST", "/api/gen/dto?save=true", b);
            StringBuilder sb = new StringBuilder(r.path("path").asText() + " — 파일 " + r.path("files").size());
            r.path("files").forEach(f -> sb.append('\n').append(f.path("name").asText()));
            r.path("unreadable").forEach(u -> batch.err().println("[못 읽음] " + u));
            // --json 이어도 소스 본문은 파일에 있다 — 응답에서 source 를 빼 짧게
            r.path("files").forEach(f -> ((com.fasterxml.jackson.databind.node.ObjectNode) f).remove("source"));
            batch.print(r, sb.toString());
            return 0;
        }
    }

    @Command(name = "generate", mixinStandardHelpOptions = true, description = "스냅샷 표로 CRUD 소스(컨트롤러·서비스·매퍼·JSP)를 템플릿 세트로 만든다. 있는 파일은 안 덮고 .gen 옆 파일로.")
    static final class Generate extends BatchCommand {

        @Option(names = "--snapshot", required = true, description = "스냅샷 id 또는 latest")
        String snapshot;

        @Option(names = "--tables", split = ",", required = true, description = "표(이름 또는 스키마.이름)")
        List<String> tables;

        @Option(names = "--set", description = "템플릿 세트(egov35·egov5 …). 없으면 프로필 generator.templateSet")
        String set;

        @Option(names = "--package", description = "기본 패키지")
        String basePackage;

        @Option(names = "--module", description = "모듈(점으로 나눔)")
        String module;

        @Option(names = "--out", description = "출력 폴더(프로젝트 루트). 없으면 프로필 generator.outDir")
        String out;

        @Option(names = "--dialect", description = "매퍼 방언. 없으면 스냅샷 DB")
        String dialect;

        @Override
        int body() throws Exception {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("snapshotId", batch.snapshotId(snapshot));
            b.put("tables", tables(tables));
            putIf(b, "templateSet", set);
            putIf(b, "basePackage", basePackage);
            putIf(b, "module", module);
            putIf(b, "outDir", out == null ? null : batch.userPath(out).toString());
            putIf(b, "dialect", dialect);
            JsonNode r = batch.job(batch.call("POST", "/api/generate", b));
            r.path("warnings").forEach(w -> batch.err().println("[경고] " + w.asText()));
            batch.print(r, r.path("outDir").asText() + " — 새 파일 " + r.path("created").asInt() + " · 옆 파일(.gen) " + r.path("sidecar").asInt()
                    + " · 경고 " + r.path("warnings").size());
            return 0;
        }
    }
}
