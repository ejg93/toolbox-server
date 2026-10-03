package kr.ejg.toolbox.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.http.staticfiles.Location;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Optional;
import kr.ejg.toolbox.core.Version;
import kr.ejg.toolbox.core.conn.ConnectionRegistry;
import kr.ejg.toolbox.core.conn.DriverLoader;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.db.Db;
import kr.ejg.toolbox.core.dict.DictStore;
import kr.ejg.toolbox.core.dialect.MetaSources;
import kr.ejg.toolbox.core.meta.SnapshotService;
import kr.ejg.toolbox.core.meta.SnapshotStore;
import kr.ejg.toolbox.core.profile.Profile;
import kr.ejg.toolbox.core.profile.ProfileStore;
import kr.ejg.toolbox.core.job.JobManager;
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

    /**
     * H2({@code dataDir}) 를 열고 서버를 띄운다. 다른 프로세스가 같은 data 폴더를 쓰고 있으면 {@link Db.LockedException}.
     * H2 는 뜬 서버가 멈출 때 닫힌다.
     */
    public static Javalin start(AppConfig config) {
        DriverLoader.load(config.driversDir());
        Db db = Db.open(config.dataDir());
        try {
            // 행안부 공통표준단어·도메인 — 첫 기동에만 적재(3-1, 2.2 A)
            int words = new DictStore(db).importMoi();
            if (words > 0) {
                LOG.info("공통표준단어 {}건 적재", words);
            }
            return bind(config, db);
        } catch (java.sql.SQLException e) {
            db.close();
            throw new IllegalStateException("사전 적재 실패: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            db.close();
            throw e;
        }
    }

    private static Javalin bind(AppConfig config, Db db) {
        int port = config.port();
        for (int i = 0; ; i++) {
            if (port != 0 && i + 1 < PORT_TRIES && !isFree(port)) {
                LOG.info("포트 {} 사용 중. 다음 {}", port, port + 1);
                port++;
                continue;
            }
            AtomicBoolean started = new AtomicBoolean();
            Javalin app = create(config, db, started);
            try {
                app.start(HOST, port);
                started.set(true);
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

    /** 바인드 시도 하나. 실패한 시도가 멈출 때는 H2 를 닫지 않는다({@code started}) */
    private static Javalin create(AppConfig config, Db db, AtomicBoolean started) {
        JobManager jobs = new JobManager();
        ProfileStore profiles = new ProfileStore(config.profilesDir(), config.dataDir());
        // 활성 프로필은 부를 때마다 읽는다 — YAML 을 고치면 재기동 없이 반영
        // 활성 프로필 이름 — 화면에서 바꿀 수 있다(1-8). ping 과 접속 목록이 이것을 따른다
        AtomicReference<String> activeName = new AtomicReference<>(config.profileName());
        Supplier<Optional<Profile>> active = () -> activeName.get() == null
                ? Optional.empty()
                : Optional.of(profiles.load(activeName.get()));
        ConnectionRegistry conns = new ConnectionRegistry(active);
        SnapshotStore snapshots = new SnapshotStore(db);
        DictStore dict = new DictStore(db);
        SnapshotService snapshotService = new SnapshotService(conns, MetaSources::forDialect, snapshots, active);
        Javalin app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
            // 날짜는 ISO 문자열로 — 기본은 [2026,9,27,1,27,19,…] 배열이라 화면이 다루기 나쁘다(1-5)
            cfg.jsonMapper(new JavalinJackson().updateMapper(m -> m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)));
            cfg.staticFiles.add(s -> {
                s.hostedPath = "/tools";
                s.directory = "/tools";
                s.location = Location.CLASSPATH;
            });
            cfg.events.serverStopped(() -> {
                jobs.shutdown();
                conns.clearAll();
                if (started.get()) {
                    db.close();
                }
            });
        });
        LocalOnly.register(app);
        jsonErrors(app);
        JobRoutes.register(app, jobs);
        ConnRoutes.register(app, conns);
        ProfileRoutes.register(app, profiles, activeName, conns);
        MetaRoutes.register(app, jobs, snapshotService, snapshots);
        SqlRoutes.register(app, conns, active);
        DictRoutes.register(app, dict);
        LogicalRoutes.register(app, dict, snapshots, active, jobs, conns);
        GenRoutes.register(app, dict, snapshots, active);
        DeliverableRoutes.register(app, snapshots, conns);
        DeliverableRoutes.registerBuild(app, snapshots, conns, dict, jobs, active);
        kr.ejg.toolbox.core.fs.LocalFiles localFiles = new kr.ejg.toolbox.core.fs.LocalFiles(config.dataDir());
        FsRoutes.register(app, localFiles, active);
        DiffRoutes.register(app, localFiles);
        CheckRoutes.register(app, jobs, db, localFiles, active);
        AnalyzeRoutes.register(app, jobs, db, localFiles, active);
        GenerateRoutes.register(app, jobs, snapshots, dict, localFiles, active,
                kr.ejg.toolbox.core.gen.TemplateSet.genDir(config.profilesDir()));
        TableRoutes.register(app, active);
        InsertRoutes.register(app, snapshots, conns);
        TextRoutes.register(app);
        app.get("/", ctx -> ctx.redirect("/tools/index.html"));
        app.get("/api/ping", ctx -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("version", Version.get());
            body.put("profile", activeName.get());
            body.put("mode", "backend");
            ctx.json(body);
        });
        return app;
    }

    /**
     * 본문 JSON 을 못 읽으면 500 대신 400(0-35). 필드 이름만 알리고 값은 안 싣는다(절대 규칙 3).
     * Javalin 은 예외 클래스에서 위로 올라가며 매퍼를 찾아, 모르는 필드가 먼저 걸린다.
     */
    static void jsonErrors(Javalin app) {
        app.exception(UnrecognizedPropertyException.class,
                (e, ctx) -> ctx.status(400).json(Map.of("message", "모르는 필드: " + e.getPropertyName())));
        app.exception(JsonProcessingException.class,
                (e, ctx) -> ctx.status(400).json(Map.of("message", "본문을 못 읽었다")));
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
