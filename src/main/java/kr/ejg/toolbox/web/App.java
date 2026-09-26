package kr.ejg.toolbox.web;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.ejg.toolbox.core.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Javalin 서버. {@code 127.0.0.1} 에만 바인드한다. */
public final class App {

    /** 바인드 주소. 설정으로 바꾸지 않는다(절대 규칙 1). */
    public static final String HOST = "127.0.0.1";
    public static final int PORT_TRIES = 10;

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    private App() {
    }

    public static Javalin start(AppConfig config) {
        int port = config.port();
        for (int i = 0; ; i++) {
            if (port != 0 && i + 1 < PORT_TRIES && !isFree(port)) {
                LOG.info("포트 {} 사용 중. 다음 {}", port, port + 1);
                port++;
                continue;
            }
            Javalin app = create(config);
            try {
                app.start(HOST, port);
            } catch (RuntimeException e) {
                app.stop();
                if (!isBindFailure(e) || port == 0 || i + 1 >= PORT_TRIES) {
                    throw e;
                }
                LOG.info("포트 {} 사용 중. 다음 {}", port, port + 1);
                port++;
                continue;
            }
            String url = "http://" + HOST + ":" + app.port() + "/";
            LOG.info("toolbox-server {} 기동: {}", Version.get(), url);
            if (config.openBrowser()) {
                openBrowser(url);
            }
            return app;
        }
    }

    private static Javalin create(AppConfig config) {
        Javalin app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
            cfg.staticFiles.add(s -> {
                s.hostedPath = "/tools";
                s.directory = "/tools";
                s.location = Location.CLASSPATH;
            });
        });
        app.get("/api/ping", ctx -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("version", Version.get());
            body.put("profile", config.profileName());
            body.put("mode", "backend");
            ctx.json(body);
        });
        return app;
    }

    /** 미리 재 본다 — Javalin 은 바인드 실패를 ERROR 로 찍어서, 기동 때마다 붉은 줄이 보이지 않게. 경합은 아래 catch 가 받는다. */
    private static boolean isFree(int port) {
        try (ServerSocket s = new ServerSocket()) {
            s.setReuseAddress(false);
            s.bind(new InetSocketAddress(HOST, port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isBindFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof BindException || t.getClass().getSimpleName().equals("JavalinBindException")) {
                return true;
            }
        }
        return false;
    }

    private static void openBrowser(String url) {
        if (!System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
            LOG.info("브라우저 자동 열기는 Windows 만. 직접 여시오: {}", url);
            return;
        }
        try {
            new ProcessBuilder("cmd", "/c", "start", "", url).start();
        } catch (Exception e) {
            LOG.warn("브라우저를 못 열었다. 직접 여시오: {}", url);
        }
    }
}
