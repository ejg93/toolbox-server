package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 5-4 — 픽스처를 복사한 폴더를 검사 → 결과·이력·비교, 붙여넣기, 거절 */
class CheckRoutesTest {

    static Javalin app;
    static Path project;
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    static Path tmp;

    @BeforeAll
    static void up() throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("t.yaml"), "name: t\nproject:\n  encoding: UTF-8\n  lineEnding: LF\nframework: egov35\n"
                + "output:\n  dir: " + tmp.resolve("out").toString().replace('\\', '/') + "\n"
                + "codecheck:\n  groups: { tsx: false }\n  rules: { common.todo: false }\n", StandardCharsets.UTF_8);
        project = tmp.resolve("proj");
        Path src = Path.of("src/test/resources/fixtures/check/java");
        Files.createDirectories(project);
        for (String n : new String[] {"PosController.java", "PosBadName.java", "NegController.java"}) {
            Files.copy(src.resolve(n), project.resolve(n));
        }
        app = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
    }

    @AfterAll
    static void down() {
        app.stop();
    }

    static HttpResponse<String> post(String path, Object body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    static JsonNode get(String path) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + path)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), path + " " + r.body());
        return JSON.readTree(r.body());
    }

    static JsonNode waitJob(HttpResponse<String> r) throws Exception {
        assertEquals(202, r.statusCode(), r.body());
        String jobId = JSON.readTree(r.body()).get("jobId").asText();
        JsonNode job = null;
        long end = System.nanoTime() + 30_000_000_000L;
        while (System.nanoTime() < end) {
            job = get("/api/jobs/" + jobId);
            if (!job.get("status").asText().matches("QUEUED|RUNNING")) {
                break;
            }
            Thread.sleep(50);
        }
        assertEquals("DONE", job.get("status").asText(), job.toString());
        return job.get("result");
    }

    @Test
    void folderRunHistoryExport() throws Exception {
        JsonNode first = waitJob(post("/api/check/run", Map.of("path", project.toString())));
        assertEquals(3, first.get("files").asInt());
        JsonNode rows = first.get("findings");
        assertTrue(rows.size() > 0);
        boolean dup = false;
        for (JsonNode f : rows) {
            dup |= f.get("rule").asText().equals("java.dupMapping");
            assertTrue(f.hasNonNull("excerpt"), "실행 결과에는 발췌가 있다");
        }
        assertTrue(dup, rows.toString());
        long a = first.get("runId").asLong();

        // 한 파일을 고치고 다시 — 이력 둘(5-24b — 「앞 실행과 비교」 는 없앴다)
        Path pc = project.resolve("PosController.java");
        Files.writeString(pc, Files.readString(pc).replace("import java.util.List;\n", ""), StandardCharsets.UTF_8);
        JsonNode second = waitJob(post("/api/check/run", Map.of("path", project.toString())));
        long b = second.get("runId").asLong();

        JsonNode runs = get("/api/check/runs");
        assertTrue(runs.size() >= 2);
        JsonNode one = get("/api/check/runs/" + b);
        assertEquals(project.toString(), one.get("run").get("path").asText());
        for (JsonNode f : one.get("findings")) {
            assertTrue(f.get("excerpt").isNull(), "이력에는 발췌가 없다");
        }
        assertTrue(a < b, "새 이력 id 가 크다");
        HttpResponse<String> x = post("/api/check/runs/" + b + "/export", Map.of());
        assertEquals(200, x.statusCode(), x.body());
        assertTrue(Files.size(Path.of(JSON.readTree(x.body()).get("path").asText())) > 0);

        // 5-36 추이 — 같은 폴더 두 번(지문 같음) → 두 점, 둘째에 고쳐짐 1(지운 import). 줄 수·지문이 응답에 있다
        assertTrue(second.get("lines").asInt() > 0, second.toString());
        JsonNode t = get("/api/check/trend?path=" + java.net.URLEncoder.encode(project.toString(), StandardCharsets.UTF_8));
        JsonNode pts = t.get("points");
        assertTrue(pts.size() >= 2, t.toString());
        JsonNode last = pts.get(pts.size() - 1);
        assertEquals(b, last.get("runId").asLong());
        assertFalse(last.get("rulesChanged").asBoolean(), t.toString());
        assertTrue(last.get("fixed").asInt() >= 1, t.toString());
        assertEquals(12, last.get("rulesHash").asText().length());
        HttpResponse<String> bad = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/check/trend")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, bad.statusCode());
        HttpResponse<String> tx = post("/api/check/trend/export", Map.of("path", project.toString(), "depth", 1));
        assertEquals(200, tx.statusCode(), tx.body());
        JsonNode txj = JSON.readTree(tx.body());
        assertEquals(pts.size(), txj.get("rows").asInt());
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                Files.newInputStream(Path.of(txj.get("path").asText())))) {
            assertEquals(List.of("추이", "폴더"), List.of(wb.getSheetName(0), wb.getSheetName(1)));
        }
    }

    /** 5-23 — 파일 단위 규칙(줄바꿈·인코딩·머리)은 rules 에 fileLevel, xlsx 의 줄 칸은 「파일」. mixedIndent 는 실제 줄이라 아니다 */
    @Test
    void fileLevelFindingsShowFileInExport() throws Exception {
        Path dir = tmp.resolve("crlf");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("B.java"), "class B {\r\n    void f() {\r\n        System.out.println(1);\r\n    }\r\n}\r\n",
                StandardCharsets.UTF_8);
        Map<String, Boolean> level = new java.util.HashMap<>();
        get("/api/check/rules").forEach(d -> level.put(d.get("id").asText(), d.get("fileLevel").asBoolean()));
        assertEquals(Boolean.TRUE, level.get("file.lineEnding"));
        assertEquals(Boolean.FALSE, level.get("file.mixedIndent"));
        assertEquals(Boolean.FALSE, level.get("common.sysout"));
        long id = waitJob(post("/api/check/run", Map.of("path", dir.toString()))).get("runId").asLong();
        HttpResponse<String> x = post("/api/check/runs/" + id + "/export", Map.of());
        assertEquals(200, x.statusCode(), x.body());
        try (var in = Files.newInputStream(Path.of(JSON.readTree(x.body()).get("path").asText()));
                var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in)) {
            Map<String, String> lineOf = new java.util.HashMap<>();
            var s = wb.getSheetAt(0);
            java.util.List<String> head = new java.util.ArrayList<>();
            s.getRow(0).forEach(c -> head.add(c.getStringCellValue()));
            assertEquals(java.util.List.of("파일", "줄", "묶음", "규칙", "등급", "설명", "권장 수정", "근거"), head, "5-25a — 끝에 두 열");
            for (int r = 1; r <= s.getLastRowNum(); r++) {
                var row = s.getRow(r);
                lineOf.put(row.getCell(3).getStringCellValue(), new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(row.getCell(1)));
            }
            assertEquals("파일", lineOf.get("file.lineEnding"), lineOf.toString());
            assertEquals("3", lineOf.get("common.sysout"), lineOf.toString());
        }
    }

    @Test
    void textRunAndRules() throws Exception {
        JsonNode r = waitJob(post("/api/check/run", Map.of("text", "class A { void f() { System.out.println(1); // tb-ignore common.sysout\n"
                + " System.out.println(2); } }", "lang", "java", "groups", Map.of("file", false))));
        assertEquals("[\"common.sysout\"]", JSON.writeValueAsString(r.get("findings").findValuesAsText("rule")));
        assertEquals(2, r.get("findings").get(0).get("line").asInt(), "5-29d — 표식 줄은 빠진다");
        assertEquals(1, r.get("suppressed").asInt());
        JsonNode rules = get("/api/check/rules");
        boolean todoOff = false;
        boolean tsxOff = false;
        for (JsonNode d : rules) {
            todoOff |= d.get("id").asText().equals("common.todo") && !d.get("enabled").asBoolean();
            tsxOff |= d.get("id").asText().equals("tsx.console") && !d.get("enabled").asBoolean();
            assertTrue(d.has("fix") && d.has("ref"), "5-25a — 규칙마다 fix·ref 칸: " + d.get("id"));
            if (d.get("id").asText().equals("security.sqlConcat")) {
                assertTrue(d.get("fix").asText().contains("바인드"), "5-25b 권장 수정: " + d.get("fix"));
            }
        }
        assertTrue(todoOff && tsxOff, "프로필 덮어쓰기 적용");
    }

    @Test
    void refusals() throws Exception {
        HttpResponse<String> notVcs = post("/api/check/run", Map.of("path", project.toString(), "changedOnly", true));
        assertEquals(400, notVcs.statusCode(), "형상 관리 폴더가 아니면 변경분만은 400(5-6b)");
        assertTrue(notVcs.body().contains(".git"), notVcs.body());
        assertEquals(400, post("/api/check/run", Map.of()).statusCode());
        assertEquals(400, post("/api/check/run", Map.of("text", "x", "ruleOverrides", Map.of("common.sysout", Map.of("regex", "(")))).statusCode());
        assertEquals(400, post("/api/check/run", Map.of("path", "relative/dir")).statusCode());
        assertEquals(404, post("/api/check/run", Map.of("path", tmp.resolve("none").toString())).statusCode());
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/check/runs/999999")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, r.statusCode());
        assertFalse(r.body().isEmpty());
    }

    /**
     * 5-38c — 묶음 ㅅ: 화면이 준 클래스·lib 폴더가 없으면 400(안 줬으면 파일 규칙만) · 묶음 ㅅ 을 끄면 안 본다 · 빈 클래스 폴더는 note ·
     * 프로필 값(project.root 기준 상대 — 없으면 note)과 defaults
     */
    @Test
    void externalFolders() throws Exception {
        String path = project.toString();
        HttpResponse<String> noClasses = post("/api/check/run", Map.of("path", path, "classesDir", tmp.resolve("none").toString()));
        assertEquals(400, noClasses.statusCode(), noClasses.body());
        assertTrue(JSON.readTree(noClasses.body()).get("message").asText().startsWith("클래스 폴더가 없다"), noClasses.body());
        Path cls = Files.createDirectories(tmp.resolve("cls"));
        HttpResponse<String> noLib = post("/api/check/run", Map.of("path", path, "classesDir", cls.toString(), "libDir", tmp.resolve("nolib").toString()));
        assertEquals(400, noLib.statusCode(), noLib.body());
        assertTrue(JSON.readTree(noLib.body()).get("message").asText().startsWith("lib 폴더가 없다"), noLib.body());
        JsonNode off = waitJob(post("/api/check/run", Map.of("path", path, "classesDir", tmp.resolve("none").toString(), "groups", Map.of("ext", false))));
        assertEquals(0, off.get("external").asInt(), "묶음 ㅅ 끔 — 폴더를 안 본다");
        JsonNode empty = waitJob(post("/api/check/run", Map.of("path", path, "classesDir", cls.toString())));
        assertTrue(empty.get("externalNote").asText().contains(".class 가 없다"), empty.toString());
        JsonNode plain = waitJob(post("/api/check/run", Map.of("path", path)));
        assertTrue(plain.get("externalNote").isNull(), "안 줬고 판별도 없으면 파일 규칙만 — " + plain.get("externalNote"));
        Path profiles = tmp.resolve("profiles");
        Files.writeString(profiles.resolve("u.yaml"), "name: u\nproject:\n  root: '" + project + "'\n  encoding: UTF-8\n  lineEnding: LF\n"
                + "codecheck:\n  classesDir: bin\n  libDir: lib\n", StandardCharsets.UTF_8);
        try {
            assertEquals(200, post("/api/profiles/active", Map.of("name", "u")).statusCode());
            // 프로필 값은 root 기준 · 없으면(빌드 전) 400 이 아니라 묶음 ㅅ 만 건너뛰고 note — 파일 규칙은 돈다
            JsonNode rel = waitJob(post("/api/check/run", Map.of("path", path)));
            assertEquals("프로필 classesDir 가 없다(빌드 전?) — " + project.resolve("bin") + " · 묶음 ㅅ 건너뜀", rel.get("externalNote").asText());
            assertTrue(rel.get("findings").size() > 0, "파일 규칙 결과는 그대로");
        } finally {
            assertEquals(200, post("/api/profiles/active", Map.of("name", "t")).statusCode());
        }
    }

    /**
     * 5-39b — 스택 판별: 매니페스트 옆 빌드 결과 폴더(dist·out·gradle build)는 안 걷고, 클래스 폴더는 판별이 찾은 것(빈 폴더면 note),
     * 결과에 스택 한 줄. {@code GET /api/check/stack} 400·404
     */
    @Test
    void stackDetected() throws Exception {
        Path root = kr.ejg.toolbox.core.check.ProjectStackTest.tree(tmp.resolve("stack"));
        write(root.resolve("frontend/src/util.js"), "export function f() {\n  debugger;\n}\n");
        JsonNode st = get("/api/check/stack?path=" + java.net.URLEncoder.encode(root.toString(), StandardCharsets.UTF_8));
        assertTrue(st.get("summary").asText().contains("frontend/ npm · React · Vite · 제외 dist,out"), st.toString());
        assertEquals(3, st.get("excludes").size(), st.toString());
        assertEquals(2, st.get("classesDirs").size(), st.toString());
        JsonNode r = waitJob(post("/api/check/run", Map.of("path", root.toString(), "groups", Map.of("tsx", true))));
        java.util.List<String> files = new java.util.ArrayList<>();
        r.get("findings").forEach(f -> files.add(f.get("file").asText() + " " + f.get("rule").asText()));
        assertTrue(files.contains("frontend/src/util.js tsx.debugger"), files.toString());
        assertTrue(files.stream().noneMatch(f -> f.startsWith("frontend/dist/") || f.startsWith("frontend/out/") || f.startsWith("gradle/build/")),
                "빌드 결과 폴더는 안 걷는다 — " + files);
        assertEquals(st.get("summary").asText(), r.get("stack").asText());
        assertTrue(r.get("externalNote").asText().contains(".class 가 없다"), "판별이 찾은 빈 target/classes — " + r.get("externalNote"));
        HttpResponse<String> none = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/check/stack?path="
                + java.net.URLEncoder.encode(tmp.resolve("none").toString(), StandardCharsets.UTF_8))).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, none.statusCode());
        HttpResponse<String> blank = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.port() + "/api/check/stack")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, blank.statusCode());
        // framework — 프로필 값이 있으면 그대로(null), 없으면 판별(eGov 모듈이 있으면 egov35)
        kr.ejg.toolbox.core.check.ProjectStack.Stack s = kr.ejg.toolbox.core.check.ProjectStack.detect(root);
        assertEquals("egov35", CheckRoutes.framework(null, s));
        assertEquals(null, CheckRoutes.framework(null, kr.ejg.toolbox.core.check.ProjectStack.detect(root.resolve("frontend"))), "npm 만이면 그대로");
        assertEquals("spring", CheckRoutes.framework(null, kr.ejg.toolbox.core.check.ProjectStack.detect(root.resolve("backend"))));
        assertEquals(java.util.List.of("a/**", "frontend/dist/**", "frontend/out/**", "gradle/build/**"),
                CheckRoutes.excludes(new kr.ejg.toolbox.core.profile.Profile.CodeCheck(null, null, null, java.util.List.of("a/**")), s));
    }

    /** 5-43 — 외부 분석기 유형별 권장 수정 표(상 57만) */
    @Test
    void extFixes() throws Exception {
        JsonNode f = get("/api/check/fixes");
        assertEquals(57, f.size(), f.toString());
        assertEquals("PreparedStatement 의 ? 바인딩으로 값을 넘긴다", f.get("findsecbugs.SQL_INJECTION_JDBC").asText());
        assertTrue(f.get("spotbugs.ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD") == null, "하 — 비움");
    }

    static void git(Path dir, String... args) throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of("git", "-c", "user.name=t", "-c", "user.email=t@t",
                "-c", "core.autocrlf=false"));
        cmd.addAll(java.util.List.of(args));
        Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), String.join(" ", cmd) + "\n" + out);
    }

    static void write(Path f, String text) throws Exception {
        Files.createDirectories(f.getParent());
        Files.writeString(f, text, StandardCharsets.UTF_8);
    }

    /**
     * 5-6b — git 작업 사본에서 변경분만: 바뀐 파일의 결과만 오고, 안 바뀐 Java 가 파일 사이 규칙의 짝이 된다
     * (바뀐 매퍼의 namespace 가 안 바뀐 DAO 로 맞음 · 같은 URL 의 매핑은 바뀐 쪽 한 건). 변경분 실행의 비교는 앞 변경분 실행과.
     */
    @Test
    void changedOnlyOnGitWorkingCopy() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(kr.ejg.toolbox.core.vcs.Cli.available(kr.ejg.toolbox.core.vcs.Cli.Exe.GIT, tmp), "git 없음");
        Path repo = tmp.resolve("gitproj");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        String ctrl = "package a;\n\nimport org.springframework.stereotype.Controller;\nimport org.springframework.web.bind.annotation.RequestMapping;\n\n"
                + "@Controller\npublic class %s {\n    @RequestMapping(\"/same.do\")\n    public String go() {\n        return \"x\";\n    }\n}\n";
        write(repo.resolve("src/a/OldController.java"), String.format(ctrl, "OldController"));
        write(repo.resolve("src/a/UserDAO.java"), "package a;\n\npublic class UserDAO {\n    void f() {\n        System.out.println(1);\n    }\n}\n");
        write(repo.resolve("src/a/user.xml"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<mapper namespace=\"UserDAO\">\n"
                + "  <select id=\"a\">SELECT id FROM t</select>\n</mapper>\n");
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "c1");
        // 바뀐 것: 새 컨트롤러(같은 URL) · 매퍼에 ${} · 새 파일에 sysout. 안 바뀐 UserDAO 의 sysout 은 결과에 없어야 한다
        write(repo.resolve("src/a/NewController.java"), String.format(ctrl, "NewController"));
        write(repo.resolve("src/a/user.xml"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<mapper namespace=\"UserDAO\">\n"
                + "  <select id=\"a\">SELECT id FROM t ORDER BY ${sort}</select>\n</mapper>\n");
        write(repo.resolve("src/a/Util.java"), "package a;\n\npublic class Util {\n    void f() {\n        System.out.println(2);\n    }\n}\n");

        JsonNode vcs = get("/api/check/vcs?path=" + java.net.URLEncoder.encode(repo.toString(), StandardCharsets.UTF_8));
        assertEquals("git", vcs.get("kind").asText());
        assertTrue(vcs.get("available").asBoolean(), vcs.toString());
        assertEquals(3, vcs.get("changed").asInt(), vcs.toString());
        JsonNode none = get("/api/check/vcs?path=" + java.net.URLEncoder.encode(project.toString(), StandardCharsets.UTF_8));
        assertEquals("none", none.get("kind").asText());
        assertFalse(none.get("available").asBoolean());

        JsonNode full = waitJob(post("/api/check/run", Map.of("path", repo.toString())));
        JsonNode first = waitJob(post("/api/check/run", Map.of("path", repo.toString(), "changedOnly", true)));
        assertEquals(3, first.get("files").asInt(), "검사한 파일은 바뀐 셋");
        java.util.Set<String> files = new java.util.TreeSet<>();
        java.util.List<String> rules = new java.util.ArrayList<>();
        for (JsonNode f : first.get("findings")) {
            files.add(f.get("file").asText());
            rules.add(f.get("file").asText() + " " + f.get("rule").asText());
        }
        assertFalse(files.contains("src/a/UserDAO.java") || files.contains("src/a/OldController.java"), "안 바뀐 파일의 결과는 없다: " + files);
        assertTrue(rules.contains("src/a/Util.java common.sysout"), rules.toString());
        assertTrue(rules.contains("src/a/user.xml mybatis.dollar"), rules.toString());
        assertFalse(rules.contains("src/a/user.xml mybatis.namespace"), "안 바뀐 UserDAO 가 namespace 짝이다: " + rules);
        assertEquals(1, rules.stream().filter(r -> r.endsWith("java.dupMapping")).count(), "짝이 안 바뀐 파일이면 바뀐 쪽 한 건: " + rules);
        boolean fullHasOld = false;
        for (JsonNode f : full.get("findings")) {
            fullHasOld |= f.get("file").asText().equals("src/a/OldController.java") && f.get("rule").asText().equals("java.dupMapping");
        }
        assertTrue(fullHasOld, "전체 검사는 두 자리 다");

        JsonNode second = waitJob(post("/api/check/run", Map.of("path", repo.toString(), "changedOnly", true)));
        // 5-24b — 비교 API 대신 이력 둘을 직접 — 같은 변경분을 다시 검사하면 같은 파일·줄·규칙
        JsonNode h1 = get("/api/check/runs/" + first.get("runId").asLong()).get("findings");
        JsonNode h2 = get("/api/check/runs/" + second.get("runId").asLong()).get("findings");
        assertEquals(h1.toString(), h2.toString(), "같은 변경분 → 같은 이력");
        assertTrue(get("/api/check/runs/" + second.get("runId").asLong()).get("run").get("changedOnly").asBoolean());
    }

    /** 5-8 — git 두 커밋 사이 배포 목록: A·M·D 행·건수·크기·확장자, xlsx 를 다시 읽어 행 수, 나쁜 ref·VCS 아님 400, /vcs 의 최근 커밋 */
    @Test
    void deployListOnGit() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(kr.ejg.toolbox.core.vcs.Cli.available(kr.ejg.toolbox.core.vcs.Cli.Exe.GIT, tmp), "git 없음");
        Path repo = tmp.resolve("deployproj");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        write(repo.resolve("web/a/A.java"), "a\n");
        write(repo.resolve("web/a/B.java"), "b\n");
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "첫 판");
        write(repo.resolve("web/a/A.java"), "a2\n");
        Files.delete(repo.resolve("web/a/B.java"));
        write(repo.resolve("web/jsp/화면.JSP"), "<p>x</p>\n");
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "둘째 판");

        HttpResponse<String> r = post("/api/check/deploy-list", Map.of("path", repo.toString(), "from", "HEAD~1", "to", "HEAD", "xlsx", true));
        assertEquals(200, r.statusCode(), r.body());
        JsonNode d = JSON.readTree(r.body());
        assertEquals("git", d.get("kind").asText());
        assertEquals("{\"A\":1,\"M\":1,\"D\":1}", d.get("counts").toString());
        java.util.List<String> rows = new java.util.ArrayList<>();
        for (JsonNode x : d.get("rows")) {
            rows.add(x.get("status").asText() + " " + x.get("file").asText() + " " + x.get("ext").asText() + " "
                    + (x.get("size").isNull() ? "-" : x.get("size").asText()));
        }
        assertEquals(java.util.List.of("M web/a/A.java java 3", "D web/a/B.java java -", "A web/jsp/화면.JSP jsp 9"), rows);
        Path xlsx = Path.of(d.get("xlsxPath").asText());
        try (java.io.InputStream in = Files.newInputStream(xlsx);
                org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in)) {
            org.apache.poi.ss.usermodel.Sheet s = wb.getSheetAt(0);
            assertEquals(3, s.getLastRowNum(), "머리 1 + 행 3");
            assertEquals("삭제", s.getRow(2).getCell(2).getStringCellValue());
        }
        assertTrue(Files.size(xlsx) > 0);

        assertEquals(400, post("/api/check/deploy-list", Map.of("path", repo.toString(), "from", "--output=x", "to", "HEAD")).statusCode());
        assertEquals(400, post("/api/check/deploy-list", Map.of("path", repo.toString(), "from", "nope", "to", "HEAD")).statusCode());
        assertEquals(400, post("/api/check/deploy-list", Map.of("path", project.toString(), "from", "HEAD~1", "to", "HEAD")).statusCode());
        JsonNode vcs = get("/api/check/vcs?path=" + java.net.URLEncoder.encode(repo.toString(), StandardCharsets.UTF_8));
        assertEquals("둘째 판", vcs.get("recent").get(0).get("subject").asText());
    }
}
