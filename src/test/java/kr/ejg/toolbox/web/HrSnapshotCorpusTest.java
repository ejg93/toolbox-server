package kr.ejg.toolbox.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.CorpusHr;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.deliverable.Definitions;
import kr.ejg.toolbox.core.dialect.MetaSources;
import kr.ejg.toolbox.core.gen.InsertGen;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Scope;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * V-6 — 실물 Oracle HR 을 H2 에 옮겨 스냅샷(JdbcMetaSource)을 뜨고, 그 위에서 산출물 값 표·INSERT 생성이 컨테이너 없이 끝까지 도는지.
 * <ul>
 *   <li>등급 A: HR 의 테이블 7 이 다 들어오고 행이 있다 · 스냅샷 테이블·FK 수 · 정의서 값 표 예외 없음 ·
 *       INSERT 생성문(FK 는 {@link InsertRoutes#fkValues} 로 부모 실존 값)을 빈 HR 에 부모 먼저 넣어 성공</li>
 *   <li>등급 B: FK 가 순환하는 테이블(departments ↔ employees) — 부모 먼저가 성립하지 않는다</li>
 * </ul>
 */
@Tag("corpus")
class HrSnapshotCorpusTest {

    static CorpusHr.Loaded hr;
    static List<Schema> snap;

    @BeforeAll
    static void up() throws Exception {
        CorpusFiles.verify();
        hr = CorpusHr.open();
        snap = MetaSources.forDialect("h2", hr.conn()).collect(new Scope(List.of("PUBLIC"), null, null, null));
    }

    @AfterAll
    static void down() throws Exception {
        hr.conn().close();
    }

    static List<Table> tables() {
        return snap.get(0).tables().stream().filter(t -> t.type() == null || !t.type().toUpperCase(Locale.ROOT).contains("VIEW")).toList();
    }

    @Test
    void hrLoadedAndSnapshotted() throws Exception {
        Map<String, Integer> rows = new TreeMap<>();
        try (Statement s = hr.conn().createStatement()) {
            for (Table t : tables()) {
                try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + t.name())) {
                    r.next();
                    rows.put(t.name(), r.getInt(1));
                }
            }
        }
        List<String> bad = new ArrayList<>();
        if (tables().size() != 7) {
            bad.add("테이블 " + tables().size() + "/7");
        }
        rows.forEach((k, v) -> {
            if (v == 0) {
                bad.add(k + " 행 0");
            }
        });
        CorpusFiles.none("HR → H2", bad, 7);
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("statementsOk", hr.ok());
        g.put("statementsFailed", hr.failed());
        g.put("rows", rows);
        g.put("fks", tables().stream().mapToInt(t -> t.fks().size()).sum());
        GoldenFiles.assertJson("corpus/hr-h2.json", g);
    }

    @Test
    void definitionsBuild() {
        List<kr.ejg.toolbox.core.deliverable.Doc> docs = Definitions.build(snap, new Definitions.Options("홍길동", "기관", "팀", "인사", "인사 DB", null, "HR", "Linux"));
        assertEquals(false, docs.isEmpty(), "정의서 값 표");
    }

    /** 부모 먼저 순서 — 순환에 든 테이블은 따로 돌려준다 */
    static List<Table> parentsFirst(List<Table> ts, Set<String> cyclic) {
        Map<String, Table> by = new LinkedHashMap<>();
        ts.forEach(t -> by.put(t.name().toUpperCase(Locale.ROOT), t));
        List<Table> out = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        boolean moved = true;
        while (moved) {
            moved = false;
            for (Table t : by.values()) {
                String k = t.name().toUpperCase(Locale.ROOT);
                if (done.contains(k)) {
                    continue;
                }
                boolean ready = t.fks().stream().map(ForeignKey::refTable).map(r -> r.toUpperCase(Locale.ROOT))
                        .allMatch(r -> r.equals(k) || done.contains(r) || !by.containsKey(r));
                if (ready) {
                    out.add(t);
                    done.add(k);
                    moved = true;
                }
            }
        }
        by.keySet().stream().filter(k -> !done.contains(k)).forEach(cyclic::add);
        return out;
    }

    @Test
    void insertGeneratedRowsIntoEmptyHrParentsFirst() throws Exception {
        CorpusHr.Loaded empty = CorpusHr.open(false);
        Set<String> cyclic = new LinkedHashSet<>();
        List<String> checkLimited = new ArrayList<>();
        List<String> bad = new ArrayList<>();
        int n = 0;
        try (Connection c = empty.conn(); Statement s = c.createStatement()) {
            for (Table t : parentsFirst(tables(), cyclic)) {
                n++;
                InsertGen.Result r = InsertGen.generate(t, Map.of(), new InsertGen.Options("oracle", 3, false, false, LocalDate.of(2026, 1, 31)),
                        (fk, max) -> InsertRoutes.fkValues(c, fk, max));
                for (String stmt : r.sql().split("\n\n")) {
                    try {
                        s.execute(stmt.substring(0, stmt.lastIndexOf(';')));
                    } catch (java.sql.SQLException e) {
                        if ("23513".equals(e.getSQLState())) {
                            checkLimited.add(t.name()); // 여러 컬럼 CHECK(예 end_date > start_date) — 결정적 값이 못 맞춘다(순수본도 「복합 CHECK 자동 생성 불가」)
                        } else {
                            bad.add(t.name() + " " + e.getSQLState());
                        }
                        break;
                    }
                }
            }
        }
        CorpusFiles.none("HR INSERT 생성문 실행(부모 먼저)", bad, n);
        List<String> b = new ArrayList<>(cyclic);
        checkLimited.forEach(x -> b.add(x + " CHECK"));
        CorpusFiles.baseline("hr-insert-b", b, tables().size() * 50);
    }
}
