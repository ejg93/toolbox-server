package kr.ejg.toolbox.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import picocli.CommandLine;

/** CLI 시험 바탕(8-3~) — 임시 data·profiles + H2 메모리 접속 프로필 t + 명령 한 번 돌리기 */
final class CliFixture implements AutoCloseable {

    static final String SECRET = "pw-비밀-9271";

    /** 명령 한 번 — 끝 코드·stdout·stderr */
    record Run(int code, String out, String err) {
    }

    final Path dir;
    final Connection holder;

    /** @param ddl H2 메모리 DB 에 먼저 돌릴 문장들 */
    CliFixture(Path dir, String... ddl) throws Exception {
        this.dir = dir;
        String url = "jdbc:h2:mem:cli" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        holder = DriverManager.getConnection(url, "sa", "");
        try (Statement st = holder.createStatement()) {
            for (String s : ddl) {
                st.execute(s);
            }
            st.execute("ALTER USER SA SET PASSWORD '" + SECRET + "'");
        }
        Files.createDirectories(dir.resolve("profiles"));
        Files.writeString(dir.resolve("profiles/t.yaml"), "name: t\nconnections:\n  - id: h2\n    dialect: h2\n    url: " + url + "\n    user: sa\n"
                + "output:\n  dir: '" + dir.resolve("out").toString().replace('\\', '/') + "'\n", StandardCharsets.UTF_8);
        Batch.hooks(id -> SECRET.toCharArray(), () -> dir);
    }

    /** 생성기 템플릿 세트를 profiles 옆 templates/gen 으로(TemplateSet.genDir) */
    CliFixture withTemplates() throws IOException {
        copy(Path.of("templates/gen"), dir.resolve("templates/gen"));
        return this;
    }

    static void copy(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : s.toList()) {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(t);
                } else {
                    Files.copy(p, t);
                }
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    Run run(String... args) {
        List<String> a = new ArrayList<>(List.of(args));
        a.add("--data-dir");
        a.add(dir.resolve("data").toString());
        a.add("--profiles-dir");
        a.add(dir.resolve("profiles").toString());
        CommandLine cmd = Main.commandLine();
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        int code = cmd.execute(a.toArray(String[]::new));
        return new Run(code, out.toString(), err.toString());
    }

    /** snapshot 한 번 → id */
    long snapshot() {
        Run s = run("snapshot", "--conn", "h2", "--profile", "t");
        if (s.code() != 0) {
            throw new IllegalStateException("snapshot 실패: " + s.err());
        }
        return Long.parseLong(s.out().split(" ")[1]);
    }

    @Override
    public void close() throws Exception {
        Batch.hooks(null, null);
        holder.close();
    }
}
