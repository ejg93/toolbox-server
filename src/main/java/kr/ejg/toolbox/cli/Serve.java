package kr.ejg.toolbox.cli;

import io.javalin.Javalin;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import kr.ejg.toolbox.core.db.Db;
import kr.ejg.toolbox.core.profile.ProfileStore;
import kr.ejg.toolbox.web.App;
import kr.ejg.toolbox.web.AppConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "serve", description = "서버를 띄우고 브라우저를 연다. 창을 닫거나 Ctrl+C 로 끈다.")
public final class Serve implements Callable<Integer> {

    @Option(names = "--port", description = "시작 포트. 사용 중이면 +1 씩 찾는다. 기본 ${DEFAULT-VALUE}")
    int port = AppConfig.DEFAULT_PORT;

    @Option(names = "--profile", description = "활성 프로필 이름. 주면 data/active-profile 도 갱신")
    String profile;

    @Option(names = "--data-dir", description = "data 폴더. 기본 ${DEFAULT-VALUE}")
    Path dataDir = Path.of("data");

    @Option(names = "--profiles-dir", description = "프로필 YAML 폴더. 기본 ${DEFAULT-VALUE}")
    Path profilesDir = Path.of("profiles");

    @Option(names = "--no-browser", description = "기동 뒤 브라우저를 열지 않는다")
    boolean noBrowser;

    private final CountDownLatch stopped = new CountDownLatch(1);
    private volatile Javalin app;

    @Override
    public Integer call() throws InterruptedException {
        // run.bat 과 같은 검사 — java -jar 로 바로 띄워도 건너뛰지 않게(0-27, 12장)
        for (Path dir : new Path[] {dataDir, Path.of("out"), Path.of("logs")}) {
            String why = unwritable(dir);
            if (why != null) {
                System.err.println("[오류] " + dir.toAbsolutePath() + " 에 쓸 수 없다. Program Files 같은 보호 폴더에 풀면 이렇게 된다.");
                System.err.println("       사용자 폴더(예 C:\\Users\\이름\\toolbox-server)에 풀고 다시 실행하시오. (" + why + ")");
                return 1;
            }
        }
        String active = new ProfileStore(profilesDir, dataDir).resolveActive(profile).orElse(null);
        try {
            app = App.start(new AppConfig(port, active, dataDir, profilesDir, Path.of("drivers"), !noBrowser));
        } catch (Db.LockedException e) {
            System.err.println("[오류] 다른 toolbox-server 가 이미 이 data 폴더를 쓰고 있다: " + e.file());
            System.err.println("       먼저 켠 창을 닫거나, 그 창의 주소를 브라우저로 여시오.");
            return 1;
        }
        Thread hook = new Thread(this::stop, "shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
        stopped.await();
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // 종료 중이면 못 뺀다
        }
        return 0;
    }

    /** 폴더를 만들고 임시 파일을 쓰고 지워 본다. 되면 null, 안 되면 까닭 */
    static String unwritable(Path dir) {
        try {
            java.nio.file.Files.createDirectories(dir);
            Path probe = java.nio.file.Files.createTempFile(dir, ".w", ".tmp");
            java.nio.file.Files.delete(probe);
            return null;
        } catch (java.io.IOException | SecurityException e) {
            return e.getClass().getSimpleName();
        }
    }

    /** 떠 있는 서버. 기동 전이면 null */
    public Javalin app() {
        return app;
    }

    public void stop() {
        Javalin a = app;
        if (a != null) {
            a.stop();
        }
        stopped.countDown();
    }
}
