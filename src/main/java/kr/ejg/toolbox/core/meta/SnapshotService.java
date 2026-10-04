package kr.ejg.toolbox.core.meta;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.conn.ConnectionRegistry;
import kr.ejg.toolbox.core.job.JobContext;
import kr.ejg.toolbox.core.job.JobManager;
import kr.ejg.toolbox.core.profile.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 스냅샷 찍기 = 작업(5-4): 접속(1-4) → 방언별 수집기(1-3) → 프로필 scope(1-1) → 저장(H2).
 * 수집기 팩토리는 주입받는다 — core.meta 가 core.dialect 를 부르면 패키지가 서로를 부른다.
 */
public final class SnapshotService {

    private static final Logger LOG = LoggerFactory.getLogger(SnapshotService.class);

    private final ConnectionRegistry conns;
    private final BiFunction<String, Connection, MetaSource> sources;
    private final SnapshotStore store;
    private final Supplier<Optional<Profile>> activeProfile;

    public SnapshotService(ConnectionRegistry conns, BiFunction<String, Connection, MetaSource> sources, SnapshotStore store,
            Supplier<Optional<Profile>> activeProfile) {
        this.conns = conns;
        this.sources = sources;
        this.store = store;
        this.activeProfile = activeProfile;
    }

    /** 작업 본문. 결과 {snapshotId, schemas, tables, elapsedMs, store, warnings} — store 는 스냅샷이 행으로 들어간 H2 파일 */
    public JobManager.Body take(String connId, String note) {
        return ctx -> run(ctx, connId, note);
    }

    private Map<String, Object> run(JobContext ctx, String connId, String note) throws Exception {
        long start = System.nanoTime();
        Profile profile = activeProfile.get().orElseThrow(() -> new IllegalStateException("활성 프로필이 없다"));
        Profile.Connection c = conns.find(connId).orElseThrow(() -> new IllegalArgumentException("접속이 없다: " + connId));
        Scope scope = profile.scope() == null ? Scope.all() : profile.scope();
        ctx.progress(5, "접속 " + connId);
        try (Connection conn = conns.open(connId)) {
            ctx.checkCancelled();
            ctx.progress(20, "메타데이터 수집");
            // 표마다 취소 확인 + 20~80 진행률(1-11)
            MetaSource src = sources.apply(c.dialect(), conn);
            List<Schema> schemas = src.collect(scope, (done, total, schema, table) -> {
                ctx.checkCancelled();
                ctx.progress(20 + 60 * done / total, "테이블 " + done + "/" + total + " — " + table);
            });
            // 벤더 SQL 이 실패해 JDBC 값으로 물러선 것(1-12) — 로그는 건수·종류만
            List<MetaSource.Warning> warnings = src.warnings();
            if (!warnings.isEmpty()) {
                LOG.warn("스냅샷 벤더 SQL 물러섬 {}건 — {}", warnings.stream().mapToInt(MetaSource.Warning::count).sum(),
                        warnings.stream().map(MetaSource.Warning::kind).distinct().toList());
            }
            ctx.checkCancelled();
            int tables = schemas.stream().mapToInt(s -> s.tables().size()).sum();
            ctx.progress(80, "저장 — 테이블 " + tables);
            List<String> warningLines = warnings.stream()
                    .map(w -> w.kind() + " " + w.sqlState() + "/" + w.vendorCode() + " ×" + w.count()).toList();
            long id = store.save(profile.name(), connId, note, schemas, scope, warningLines);
            ctx.progress(100, "완료");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("snapshotId", id);
            out.put("schemas", schemas.size());
            out.put("tables", tables);
            out.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
            out.put("store", store.file().toString());
            out.put("warnings", warnings);
            return out;
        }
    }
}
