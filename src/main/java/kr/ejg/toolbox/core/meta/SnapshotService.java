package kr.ejg.toolbox.core.meta;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import kr.ejg.toolbox.core.conn.ConnectionRegistry;
import kr.ejg.toolbox.core.job.JobContext;
import kr.ejg.toolbox.core.job.JobManager;
import kr.ejg.toolbox.core.profile.Profile;

/**
 * 스냅샷 찍기 = 작업(5-4): 접속(1-4) → 방언별 수집기(1-3) → 프로필 scope(1-1) → 저장(H2).
 * 수집기 팩토리는 주입받는다 — core.meta 가 core.dialect 를 부르면 패키지가 서로를 부른다.
 */
public final class SnapshotService {

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

    /** 작업 본문. 결과 {snapshotId, schemas, tables} */
    public JobManager.Body take(String connId, String note) {
        return ctx -> run(ctx, connId, note);
    }

    private Map<String, Object> run(JobContext ctx, String connId, String note) throws Exception {
        Profile profile = activeProfile.get().orElseThrow(() -> new IllegalStateException("활성 프로필이 없다"));
        Profile.Connection c = conns.find(connId).orElseThrow(() -> new IllegalArgumentException("접속이 없다: " + connId));
        Scope scope = profile.scope() == null ? Scope.all() : profile.scope();
        ctx.progress(5, "접속 " + connId);
        try (Connection conn = conns.open(connId)) {
            ctx.checkCancelled();
            ctx.progress(20, "메타데이터 수집");
            List<Schema> schemas = sources.apply(c.dialect(), conn).collect(scope);
            ctx.checkCancelled();
            int tables = schemas.stream().mapToInt(s -> s.tables().size()).sum();
            ctx.progress(80, "저장 — 테이블 " + tables);
            long id = store.save(profile.name(), connId, note, schemas);
            ctx.progress(100, "완료");
            return Map.of("snapshotId", id, "schemas", schemas.size(), "tables", tables);
        }
    }
}
