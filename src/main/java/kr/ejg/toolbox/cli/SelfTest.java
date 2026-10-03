package kr.ejg.toolbox.cli;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import kr.ejg.toolbox.core.vcs.Cli;
import kr.ejg.toolbox.web.LocalApp;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * 8-7 현장 스모크 — 실행 jar 안에서(브라우저 엔진 없이) 자바·폴더 쓰기·라우트·화면 파일·프로필·규칙·템플릿·사전·드라이버·git·svn 을 잰다.
 * JS 실행까지 보는 HtmlUnit 스모크는 현장 빌드 {@code build.bat test}(사용자 2026-10-03).
 */
@Command(name = "selftest", mixinStandardHelpOptions = true, description = "현장 점검 — 자바·폴더·화면 파일·프로필·규칙·템플릿·사전·드라이버·git·svn. 실패가 있으면 끝 코드 1.")
final class SelfTest extends BatchCommand {

    private static final Pattern DATA_FILE = Pattern.compile("data-file=\"([A-Za-z0-9_.-]+)\"");

    @Option(names = "--conn", description = "이 접속으로 DB 연결까지(비밀번호는 프롬프트 또는 환경변수)")
    String conn;

    private int pass;
    private int warn;
    private int fail;

    @Override
    boolean needsProfile() {
        return false;
    }

    private void line(String kind, String name, String detail) {
        switch (kind) {
            case "통과" -> pass++;
            case "경고" -> warn++;
            default -> fail++;
        }
        batch.out().println("[" + kind + "] " + name + (detail == null || detail.isEmpty() ? "" : " — " + detail));
        batch.out().flush();
    }

    private void check(boolean ok, boolean warnOnly, String name, String detail) {
        line(ok ? "통과" : warnOnly ? "경고" : "실패", name, detail);
    }

    @Override
    int body() throws Exception {
        LocalApp.Response r;
        // ① 자바
        int major = Runtime.version().feature();
        check(major >= 17, false, "자바", major + " · " + System.getProperty("java.home"));
        // ② 폴더 쓰기
        for (Path dir : new Path[] {batch.dataDir, Path.of("out"), Path.of("logs")}) {
            String why = Serve.unwritable(dir);
            check(why == null, false, "쓰기 " + dir, why);
        }
        // ③ 서버
        r = batch.raw("GET", "/api/ping", null);
        check(r.status() == 200, false, "서버", "ping " + r.status());
        // ④ 런처와 화면 파일
        check(batch.raw("GET", "/", null).status() == 302, false, "런처 주소", "/ → /tools/index.html");
        r = batch.raw("GET", "/tools/index.html", null);
        List<String> files = new ArrayList<>();
        Matcher m = DATA_FILE.matcher(r.text());
        while (m.find()) {
            files.add(m.group(1));
        }
        files.add("common.js");
        List<String> missing = new ArrayList<>();
        for (String f : files) {
            if (batch.raw("GET", "/tools/" + f, null).status() != 200) {
                missing.add(f);
            }
        }
        check(r.status() == 200 && files.size() > 1 && missing.isEmpty(), false, "화면 파일",
                (files.size() - 1) + "개 카드 + common.js" + (missing.isEmpty() ? "" : " · 없음 " + missing));
        // ⑤ 프로필
        JsonNode prof = batch.call("GET", "/api/profiles", null);
        String active = prof.path("active").asText(null);
        check(active != null && !active.equals("null"), true, "프로필", "활성 " + active + " · 전체 " + prof.path("names"));
        // ⑥ 규칙
        int rules = batch.call("GET", "/api/check/rules", null).size();
        check(rules > 0, false, "코드 검사 규칙", rules + "개");
        // ⑦ 생성기 템플릿
        int sets = batch.call("GET", "/api/generate/templates", null).size();
        check(sets > 0, true, "생성기 템플릿 세트", sets + "개" + (sets > 0 ? "" : " — templates/gen 이 profiles 옆에 없다"));
        // ⑧ 사전
        JsonNode moi = batch.call("GET", "/api/dict/moi", null);
        check(moi.path("count").asInt() > 0, false, "공통표준단어", moi.path("count").asInt() + "건 · " + moi.path("source").asText(""));
        // ⑨ 드라이버
        List<String> jars = new ArrayList<>();
        Path drivers = Path.of("drivers");
        if (Files.isDirectory(drivers)) {
            try (Stream<Path> s = Files.list(drivers)) {
                s.map(p -> p.getFileName().toString()).filter(n -> n.toLowerCase().endsWith(".jar")).sorted().forEach(jars::add);
            }
        }
        check(!jars.isEmpty(), true, "JDBC 드라이버", jars.isEmpty() ? "drivers/ 에 jar 가 없다" : jars.size() + "개 " + jars);
        // ⑩ git·svn
        for (Cli.Exe exe : Cli.Exe.values()) {
            boolean ok = Cli.available(exe, Path.of("").toAbsolutePath());
            check(ok, true, exe.name().toLowerCase(), ok ? "쓸 수 있다" : "명령줄 도구가 없다 — 변경분·배포 목록만 꺼진다");
        }
        // ⑪ 순수본
        check(Files.exists(Path.of("pure", "toolbox.html")), true, "순수본", "pure/toolbox.html");
        // ⑫ DB 연결
        if (conn != null) {
            batch.password(conn);
            LocalApp.Response t = batch.raw("POST", "/api/conn/" + Batch.enc(conn) + "/test", "{}");
            JsonNode n = Batch.parse(t);
            boolean ok = t.status() == 200 && n.path("ok").asBoolean();
            check(ok, false, "DB 연결 " + conn, ok ? n.path("productName").asText() + " " + n.path("productVersion").asText()
                    : n.path("message").asText(t.text()));
        }
        batch.out().println("통과 " + pass + " · 경고 " + warn + " · 실패 " + fail);
        return fail > 0 ? 1 : 0;
    }
}
