package kr.ejg.toolbox.core.vcs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 형상 관리 명령줄 도구를 부른다(5-6a). 셸을 거치지 않고 실행 파일은 {@link Exe} 둘뿐이다(타입으로 — 5-11) — 인자는 상수와
 * {@link WorkingCopy} 가 검사한 ref. 출력·경로를 로그에 남기지 않는다(절대 규칙 3). git·svn 이 PATH 에 없으면 비어 있는 결과.
 * 환경: {@code GIT_OPTIONAL_LOCKS=0}(status 가 인덱스를 다시 쓰지 않게) · {@code GIT_TERMINAL_PROMPT=0}(자격 증명을 묻지 않게) ·
 * {@code LC_ALL=C}(svn 오류 글이 콘솔 언어 MS949 로 나와 깨진다 — 설계 7 실측).
 */
public final class Cli {

    /** 부를 수 있는 실행 파일 — 열거형이라 다른 것은 컴파일이 막는다(PR #24 AI 리뷰 ①) */
    public enum Exe {
        GIT("git"), SVN("svn");

        final String command;

        Exe(String command) {
            this.command = command;
        }
    }

    static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** {@code err} 는 stderr 첫 줄(사유). 종료 코드가 0 이 아니면 {@code out} 을 파싱하지 않는다 — svn 은 오류 때도 XML 앞머리를 낸다 */
    public record Result(int exit, byte[] out, String err) {
        public Result {
            out = out == null ? new byte[0] : out.clone();
        }

        @Override
        public byte[] out() {
            return out.clone();
        }

        public boolean ok() {
            return exit == 0;
        }

        public String text() {
            return new String(out, StandardCharsets.UTF_8);
        }
    }

    private Cli() {
    }

    public static Optional<Result> run(Exe exe, List<String> args, Path cwd) {
        return run(exe, args, cwd, TIMEOUT);
    }

    /**
     * @return 실행 파일이 없으면 비어 있다
     * @throws IllegalStateException 시간 초과(프로세스는 끝낸다)
     */
    public static Optional<Result> run(Exe exe, List<String> args, Path cwd, Duration timeout) {
        List<String> cmd = new java.util.ArrayList<>(args.size() + 1);
        cmd.add(exe.command);
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(cwd.toFile());
        Map<String, String> env = pb.environment();
        env.put("GIT_OPTIONAL_LOCKS", "0");
        env.put("GIT_TERMINAL_PROMPT", "0");
        env.put("LC_ALL", "C");
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            return Optional.empty(); // 실행 파일이 PATH 에 없다
        }
        try {
            p.getOutputStream().close(); // stdin 닫음 — 묻는 프롬프트가 기다리지 않게
        } catch (IOException e) {
            // 이미 끝난 프로세스
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Thread to = drain(p.getInputStream(), out);
        Thread te = drain(p.getErrorStream(), err);
        try {
            if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException(exe.command + " 시간 초과(" + timeout.toSeconds() + "초)");
            }
            to.join(timeout.toMillis());
            te.join(timeout.toMillis());
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exe.command + " 중단됨", e);
        }
        String first = new String(err.toByteArray(), StandardCharsets.UTF_8).lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        return Optional.of(new Result(p.exitValue(), out.toByteArray(), first.strip()));
    }

    private static Thread drain(InputStream in, ByteArrayOutputStream sink) {
        Thread t = new Thread(() -> {
            try (in) {
                in.transferTo(sink);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, "cli-drain");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** 실행 파일이 있고 버전 확인이 0 으로 끝나는지 */
    public static boolean available(Exe exe, Path cwd) {
        List<String> args = exe == Exe.SVN ? List.of("--version", "--quiet") : List.of("--version");
        try {
            return run(exe, args, cwd, Duration.ofSeconds(10)).map(Result::ok).orElse(false);
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
