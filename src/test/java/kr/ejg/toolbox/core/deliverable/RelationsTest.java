package kr.ejg.toolbox.core.deliverable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import kr.ejg.toolbox.core.analyze.AnalyzeStore;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.PrimaryKey;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.api.Test;

/** 2-19 — 부모 판정: 단일 PK · 복합 PK 전부 · 복합 PK 일부(약) · 양쪽 다 PK(약) · 선언 FK 쌍(안 싣는다) · 뷰(약) */
class RelationsTest {

    static Table table(String name, String type, List<String> cols, List<String> pk, List<ForeignKey> fks) {
        List<Column> cs = new ArrayList<>();
        for (int i = 0; i < cols.size(); i++) {
            cs.add(new Column(cols.get(i), i + 1, "VARCHAR", 12, 10L, null, null, true, null, null, null));
        }
        return Table.of("A", name, type, null).withColumns(cs).withConstraints(pk.isEmpty() ? null : new PrimaryKey("PK_" + name, pk), fks,
                List.of());
    }

    static AnalyzeStore.JoinRow j(String ns, String ta, String ca, String tb, String cb) {
        return new AnalyzeStore.JoinRow(ns, ta, ca, tb, cb);
    }

    static List<Schema> snap(Table... ts) {
        return List.of(new Schema("A", "Oracle Database 19c", List.of(ts)));
    }

    @Test
    void singlePkParent() {
        Table user = table("USR", "TABLE", List.of("USER_ID", "NM"), List.of("USER_ID"), List.of());
        Table bbs = table("BBS", "TABLE", List.of("BBS_ID", "WRITER_ID"), List.of("BBS_ID"), List.of());
        Relations.Result r = Relations.infer(snap(user, bbs), List.of(j("Bbs.list", "BBS", "WRITER_ID", "USR", "USER_ID"),
                j("Bbs.detail", "BBS", "WRITER_ID", "USR", "USER_ID")));
        assertEquals(1, r.strong().size(), r.toString());
        Relations.Inferred i = r.strong().get(0);
        assertEquals("USR", i.parentTable());
        assertEquals("BBS", i.childTable());
        assertEquals(List.of("WRITER_ID"), i.childCols());
        assertEquals(List.of("USER_ID"), i.parentCols());
        assertEquals(2, i.statements());
        assertEquals(List.of(), r.weak());
    }

    @Test
    void compositePkAllMatched() {
        Table code = table("CODE", "TABLE", List.of("GRP", "CD", "NM"), List.of("GRP", "CD"), List.of());
        Table use = table("USE_T", "TABLE", List.of("ID", "GRP_CD", "CD_V"), List.of("ID"), List.of());
        Relations.Result r = Relations.infer(snap(code, use), List.of(j("X.a", "CODE", "GRP", "USE_T", "GRP_CD"), j("X.a", "CODE", "CD", "USE_T", "CD_V")));
        assertEquals(1, r.strong().size(), r.toString());
        assertEquals(List.of("GRP_CD", "CD_V"), r.strong().get(0).childCols(), "부모 PK 순서대로");
    }

    @Test
    void compositePkPartialIsWeak() {
        Table code = table("CODE", "TABLE", List.of("GRP", "CD"), List.of("GRP", "CD"), List.of());
        Table use = table("USE_T", "TABLE", List.of("ID", "GRP_CD"), List.of("ID"), List.of());
        Relations.Result r = Relations.infer(snap(code, use), List.of(j("X.a", "CODE", "GRP", "USE_T", "GRP_CD")));
        assertEquals(List.of(), r.strong());
        assertEquals(1, r.weak().size());
        assertEquals("CODE", r.weak().get(0).tableA());
    }

    @Test
    void bothPkFullIsWeak() {
        Table a = table("TA", "TABLE", List.of("ID"), List.of("ID"), List.of());
        Table b = table("TB", "TABLE", List.of("ID"), List.of("ID"), List.of());
        Relations.Result r = Relations.infer(snap(a, b), List.of(j("X.a", "TA", "ID", "TB", "ID")));
        assertEquals(List.of(), r.strong(), "양쪽 다 PK 전부 — 방향을 모른다");
        assertEquals(1, r.weak().size());
    }

    /** 2-20 — 문장 하나는 TA 가 부모, 다른 하나는 TB 가 부모(두 방향이 섞임) → 약(2-19 계획 밖 결정) */
    @Test
    void mixedDirectionsIsWeak() {
        Table a = table("TA", "TABLE", List.of("ID", "B_ID"), List.of("ID"), List.of());
        Table b = table("TB", "TABLE", List.of("ID", "A_ID"), List.of("ID"), List.of());
        Relations.Result r = Relations.infer(snap(a, b), List.of(j("X.one", "TA", "ID", "TB", "A_ID"), j("X.two", "TA", "B_ID", "TB", "ID")));
        assertEquals(List.of(), r.strong(), "방향이 섞이면 04 에 안 싣는다 — " + r);
        assertEquals(2, r.weak().size(), r.toString());
    }

    @Test
    void declaredFkPairIsSkipped() {
        Table user = table("USR", "TABLE", List.of("USER_ID"), List.of("USER_ID"), List.of());
        Table bbs = table("BBS", "TABLE", List.of("BBS_ID", "WRITER_ID"), List.of("BBS_ID"),
                List.of(new ForeignKey("FK_BBS_USR", List.of("WRITER_ID"), null, "USR", List.of("USER_ID"))));
        Relations.Result r = Relations.infer(snap(user, bbs), List.of(j("Bbs.list", "BBS", "WRITER_ID", "USR", "USER_ID")));
        assertEquals(List.of(), r.strong(), "선언 FK 가 있는 쌍은 안 싣는다");
        assertEquals(List.of(), r.weak());
    }

    @Test
    void viewIsWeak() {
        Table user = table("USR", "TABLE", List.of("USER_ID"), List.of("USER_ID"), List.of());
        Table v = table("V_BBS", "VIEW", List.of("WRITER_ID"), List.of(), List.of());
        Relations.Result r = Relations.infer(snap(user, v), List.of(j("Bbs.list", "USR", "USER_ID", "V_BBS", "WRITER_ID")));
        assertEquals(List.of(), r.strong());
        assertTrue(r.weak().get(0).view(), r.toString());
    }

    /** 04 — 선언은 「선언」, 강한 추정은 선언 뒤에 「추정(조인 n문장)」, 삭제·갱신규칙 빈칸, 추정 건수 */
    @Test
    void doc04CarriesInferredRows() {
        Table user = table("USR", "TABLE", List.of("USER_ID"), List.of("USER_ID"), List.of());
        Table bbs = table("BBS", "TABLE", List.of("BBS_ID", "WRITER_ID"), List.of("BBS_ID"), List.of());
        List<Schema> s = snap(user, bbs);
        Relations.Result r = Relations.infer(s, List.of(j("Bbs.list", "BBS", "WRITER_ID", "USR", "USER_ID")));
        Doc d04 = Definitions.build(s, Definitions.Options.empty(), java.util.Set.of(), r).stream().filter(d -> d.no().equals("04")).findFirst()
                .orElseThrow();
        assertEquals(1, d04.rows().size());
        assertEquals("추정(조인 1문장)", d04.cell(0, "근거"));
        assertEquals("USR", d04.cell(0, "부모 영문 테이블명"));
        assertEquals("WRITER_ID", d04.cell(0, "자식 영문 컬럼명"));
        assertEquals("", d04.cell(0, "삭제규칙"));
        assertEquals(1, d04.estimated().get("근거"));
    }
}
