package kr.ejg.toolbox.core.deliverable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.meta.UniqueKey;

/**
 * 구조 점검(2-28, 설계 25) — 스냅샷만으로 표·컬럼 구조의 흔한 결함을 짚는다. DB 에 다시 붙지 않는다. 결과는 저장하지 않는다(Audit 과 같은 결).
 * tbls lint·SchemaSpy anomalies 에서 고른 열세 규칙(설계 25 아홉 + 설계 51 넷 — SchemaCrawler 의 PK 없음·FK 형식·중복 인덱스·FK 순환). 뷰는 모든 규칙에서 뺀다.
 * noPk 는 유니크·인덱스는 있는데 PK 가 없는 표만 — 아무것도 없는 표는 noIndex, PK 없는 표 전체는 90 칸 「PK 없는 표」 버튼.
 */
public final class StructureLint {

    /** 규칙 — 순서가 결과 안의 순서다. id 는 프로필 {@code deliverable.lint.off}·API 의 글 */
    public enum Rule {
        FK_NO_INDEX("fkNoIndex", "FK 컬럼 인덱스 없음", "FK 컬럼 묶음으로 시작하는 인덱스·PK·유니크가 없다 — 조인·부모 삭제 검사가 느리다"),
        NO_RELATION("noRelation", "관계 없는 표", "FK 가 없고 다른 표의 FK 가 가리키지도 않는다(스냅샷 안) — 고립 표나 빠진 FK"),
        NO_INDEX("noIndex", "인덱스 없는 표", "PK·유니크·인덱스가 하나도 없다"),
        TOO_MANY_COLUMNS("tooManyColumns", "컬럼 수 상한 초과", "프로필 deliverable.lint.maxColumns 를 넘는다"),
        MISSING_REQUIRED("missingRequired", "필수 컬럼 없음", "프로필 deliverable.lint.requiredColumns 중 없는 것이 있다"),
        DUP_RELATION("dupRelation", "중복 관계", "같은 컬럼 묶음이 같은 표를 가리키는 FK 가 둘 이상"),
        SINGLE_COLUMN("singleColumn", "컬럼 하나뿐인 표", "컬럼이 하나다 — 설계 잔재나 미완성"),
        SERIAL_COLUMNS("serialColumns", "번호 붙은 반복 컬럼", "같은 이름에 1·2·3… 이 붙은 컬럼이 셋 이상 — 비정규화 의심"),
        DEFAULT_NULL_STRING("defaultNullString", "기본값이 글자 NULL", "DEFAULT 'NULL' — NULL 이 아니라 글자 N·U·L·L 이 들어간다"),
        NO_PK("noPk", "PK 없는 표", "PK 가 없다 — 유니크·인덱스는 있다(아무것도 없는 표는 noIndex, 전체 목록은 90 「PK 없는 표」)"),
        FK_TYPE_MISMATCH("fkTypeMismatch", "FK 형식 불일치", "FK 컬럼의 형식·길이·정밀도가 가리키는 부모 컬럼과 다르다 — 암묵 변환으로 인덱스를 못 타거나 값이 잘린다"),
        DUP_INDEX("dupIndex", "중복 인덱스", "앞 컬럼이 같은 다른 인덱스·PK·유니크에 덮인다 — 쓰기 비용만 든다"),
        FK_CYCLE("fkCycle", "FK 순환", "FK 로 서로 가리켜 되돌아온다 — 이관·삭제 순서가 안 정해진다(자기 참조는 제외)");

        public final String id;
        public final String label;
        public final String meaning;

        Rule(String id, String label, String meaning) {
            this.id = id;
            this.label = label;
            this.meaning = meaning;
        }

        /** 모르는 id 는 IllegalArgumentException(전체 id 나열) */
        public static Rule byId(String id) {
            for (Rule r : values()) {
                if (r.id.equals(id == null ? null : id.trim())) {
                    return r;
                }
            }
            List<String> all = new ArrayList<>();
            for (Rule r : values()) {
                all.add(r.id);
            }
            throw new IllegalArgumentException("구조 점검 규칙이 아니다: " + id + " — " + String.join(" ", all));
        }
    }

    /** maxColumns 0 이면 안 본다. requiredColumns 비면 안 본다. off 는 끈 규칙 */
    public record Options(int maxColumns, List<String> requiredColumns, Set<Rule> off) {
        public static final Options DEFAULT = new Options(0, List.of(), Set.of());

        public Options {
            requiredColumns = requiredColumns == null ? List.of() : List.copyOf(requiredColumns);
            off = off == null ? Set.of() : Set.copyOf(off);
        }

        /** 2-28b — 프로필 deliverable.lint 에서. null 이면 DEFAULT. off 의 모르는 id 는 IllegalArgumentException */
        public static Options of(kr.ejg.toolbox.core.profile.Profile.Lint l) {
            if (l == null) {
                return DEFAULT;
            }
            Set<Rule> off = java.util.EnumSet.noneOf(Rule.class);
            l.off().forEach(id -> off.add(Rule.byId(id)));
            return new Options(l.maxColumns() == null ? 0 : l.maxColumns(), l.requiredColumns(), off);
        }
    }

    /** column 이 null 이면 표 단위 */
    public record Finding(Rule rule, String schema, String table, String column, String detail) {
    }

    /** counts 는 켠 규칙 전부(0 포함, Rule 순서) · basis 는 화면·xlsx 에 보일 기준 글 */
    public record Result(List<Finding> findings, Map<Rule, Integer> counts, int tables, List<String> basis) {
        public Result {
            findings = List.copyOf(findings);
            counts = java.util.Collections.unmodifiableMap(new EnumMap<>(counts.isEmpty() ? new EnumMap<>(Rule.class) : counts));
            basis = List.copyOf(basis);
        }
    }

    /** 숫자 접미는 아홉 자리까지(마무리 리뷰 — 그 넘는 접미는 순번이 아니라 이름이고 parseLong 이 넘친다) */
    private static final Pattern SERIAL = Pattern.compile("^(.*[A-Za-z_])(\\d{1,9})$");
    private static final Pattern NULL_STRING = Pattern.compile("^'NULL'(::.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARENS = Pattern.compile("\\([^)]*\\)");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    /** 강연결 성분이 순환인 최소 크기 — 자기 참조는 간선에서 이미 버렸다 */
    private static final int MIN_CYCLE = 2;

    private StructureLint() {
    }

    public static Result run(List<Schema> snapshot, Options o) {
        return run(snapshot, snapshot, o);
    }

    /**
     * @param snapshot 점검할 표(프로필 deliverable.filter 로 거른 것)
     * @param whole    거르기 전 스냅샷 — 「관계 없는 표」 의 가리키는 쪽은 범위 밖 자식 표까지 본다(마무리 리뷰)
     */
    public static Result run(List<Schema> snapshot, List<Schema> whole, Options o) {
        Options opt = o == null ? Options.DEFAULT : o;
        List<Table> tables = new ArrayList<>();
        for (Table t : Definitions.sorted(snapshot)) {
            if (!isView(t)) {
                tables.add(t);
            }
        }
        // 다른 표의 FK 가 가리키는 표 — 「스키마.표」(refSchema 가 없으면 자식 스키마)
        Set<String> referenced = new HashSet<>();
        List<Schema> all = whole == null ? snapshot : whole;
        for (Table t : Definitions.sorted(all)) {
            for (ForeignKey fk : t.fks()) {
                referenced.add(refKey(t, fk));
            }
        }
        List<String> required = opt.requiredColumns().stream().filter(s -> s != null && !s.isBlank()).map(StructureLint::up).toList();
        List<Finding> out = new ArrayList<>();
        for (Table t : tables) {
            String s = t.schema() == null ? "" : t.schema();
            if (on(opt, Rule.FK_NO_INDEX)) {
                for (ForeignKey fk : t.fks()) {
                    if (!fk.columns().isEmpty() && !indexed(t, fk.columns())) {
                        out.add(new Finding(Rule.FK_NO_INDEX, s, t.name(), String.join(",", fk.columns()), "FK " + nz(fk.name()) + " → " + fk.refTable()));
                    }
                }
            }
            if (on(opt, Rule.NO_RELATION) && t.fks().isEmpty() && !referenced.contains(key(t.schema(), t.name()))) {
                out.add(new Finding(Rule.NO_RELATION, s, t.name(), null, ""));
            }
            if (on(opt, Rule.NO_INDEX) && (t.pk() == null || t.pk().columns().isEmpty()) && t.uniques().isEmpty() && t.indexes().isEmpty()) {
                out.add(new Finding(Rule.NO_INDEX, s, t.name(), null, t.rowCount() == null ? "행 수 모름" : "행 " + t.rowCount()));
            }
            if (on(opt, Rule.TOO_MANY_COLUMNS) && opt.maxColumns() > 0 && t.columns().size() > opt.maxColumns()) {
                out.add(new Finding(Rule.TOO_MANY_COLUMNS, s, t.name(), null, "컬럼 " + t.columns().size() + " > " + opt.maxColumns()));
            }
            if (on(opt, Rule.MISSING_REQUIRED) && !required.isEmpty()) {
                Set<String> have = new HashSet<>();
                t.columns().forEach(c -> have.add(up(c.name())));
                List<String> missing = required.stream().filter(r -> !have.contains(r)).toList();
                if (!missing.isEmpty()) {
                    out.add(new Finding(Rule.MISSING_REQUIRED, s, t.name(), null, String.join(",", missing)));
                }
            }
            if (on(opt, Rule.DUP_RELATION)) {
                Map<String, List<ForeignKey>> byShape = new LinkedHashMap<>();
                for (ForeignKey fk : t.fks()) {
                    byShape.computeIfAbsent(up(String.join(",", fk.columns())) + "→" + refKey(t, fk), k -> new ArrayList<>()).add(fk);
                }
                for (List<ForeignKey> same : byShape.values()) {
                    if (same.size() > 1) {
                        out.add(new Finding(Rule.DUP_RELATION, s, t.name(), String.join(",", same.get(0).columns()),
                                "FK " + String.join("·", same.stream().map(f -> nz(f.name())).toList())));
                    }
                }
            }
            if (on(opt, Rule.SINGLE_COLUMN) && t.columns().size() == 1) {
                out.add(new Finding(Rule.SINGLE_COLUMN, s, t.name(), t.columns().get(0).name(), ""));
            }
            if (on(opt, Rule.SERIAL_COLUMNS)) {
                Map<String, List<Object[]>> byStem = new LinkedHashMap<>();
                for (Column c : t.columns()) {
                    Matcher m = SERIAL.matcher(c.name());
                    if (m.matches()) {
                        byStem.computeIfAbsent(up(m.group(1)), k -> new ArrayList<>()).add(new Object[] {Long.parseLong(m.group(2)), c.name(), m.group(1)});
                    }
                }
                for (List<Object[]> group : byStem.values()) {
                    if (group.size() >= 3) {
                        group.sort(Comparator.comparingLong(x -> (Long) x[0]));
                        out.add(new Finding(Rule.SERIAL_COLUMNS, s, t.name(), group.get(0)[2] + "1…",
                                String.join("·", group.stream().map(x -> (String) x[1]).toList())));
                    }
                }
            }
            if (on(opt, Rule.DEFAULT_NULL_STRING)) {
                for (Column c : t.columns()) {
                    if (c.defaultValue() != null && NULL_STRING.matcher(c.defaultValue().trim()).matches()) {
                        out.add(new Finding(Rule.DEFAULT_NULL_STRING, s, t.name(), c.name(), c.defaultValue()));
                    }
                }
            }
        }
        structural(tables, byKey(all), opt, out);
        out.sort(Comparator.comparing((Finding f) -> f.rule().ordinal()).thenComparing(Finding::schema).thenComparing(Finding::table)
                .thenComparing(f -> f.column() == null ? "" : f.column()));
        Map<Rule, Integer> counts = new EnumMap<>(Rule.class);
        for (Rule r : Rule.values()) {
            if (on(opt, r)) {
                counts.put(r, 0);
            }
        }
        out.forEach(f -> counts.merge(f.rule(), 1, Integer::sum));
        List<String> basis = List.of("컬럼 수 상한 " + (opt.maxColumns() > 0 ? String.valueOf(opt.maxColumns()) : "안 봄"),
                "필수 컬럼 " + (required.isEmpty() ? "안 봄" : String.join(",", required)),
                "끈 규칙 " + (opt.off().isEmpty() ? "없음" : String.join("·", java.util.Arrays.stream(Rule.values()).filter(r -> opt.off().contains(r))
                        .map(r -> r.label).toList())));
        return new Result(out, counts, tables.size(), basis);
    }

    /** FK 컬럼 묶음(대문자 집합)과 같은 크기의 앞머리를 가진 PK·유니크·인덱스가 있나 */
    static boolean indexed(Table t, List<String> fkCols) {
        Set<String> want = new HashSet<>();
        fkCols.forEach(c -> want.add(up(c)));
        List<List<String>> cands = new ArrayList<>();
        if (t.pk() != null) {
            cands.add(t.pk().columns());
        }
        for (UniqueKey u : t.uniques()) {
            cands.add(u.columns());
        }
        for (Index ix : t.indexes()) {
            cands.add(ix.columns());
        }
        for (List<String> c : cands) {
            if (c.size() >= fkCols.size()) {
                Set<String> head = new HashSet<>();
                c.subList(0, fkCols.size()).forEach(x -> head.add(up(x)));
                if (head.equals(want)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 설계 51 — 새 규칙 넷. run(3) 의 분기를 늘리지 않으려고 따로 둔다 */
    private static void structural(List<Table> tables, Map<String, Table> whole, Options opt, List<Finding> out) {
        for (Table t : tables) {
            String s = nz(t.schema());
            if (on(opt, Rule.NO_PK)) {
                noPk(t, s, out);
            }
            if (on(opt, Rule.FK_TYPE_MISMATCH)) {
                fkTypes(t, s, whole, out);
            }
            if (on(opt, Rule.DUP_INDEX)) {
                dupIndexes(t, s, out);
            }
        }
        if (on(opt, Rule.FK_CYCLE)) {
            cycles(tables, out);
        }
    }

    /** 뷰 아닌 표를 「스키마.표」 로 — 같은 키는 첫 것 */
    private static Map<String, Table> byKey(List<Schema> snapshot) {
        Map<String, Table> m = new LinkedHashMap<>();
        for (Table t : Definitions.sorted(snapshot)) {
            if (!isView(t)) {
                m.putIfAbsent(key(t.schema(), t.name()), t);
            }
        }
        return m;
    }

    /** pk 는 PK 자리, unique 는 PK·유니크·유니크 인덱스. 같은 이름(대문자)은 먼저 든 것만 */
    private record Keyed(String name, boolean pk, boolean unique, List<String> cols) {
    }

    /** PK → 유니크 → 인덱스 순. 컬럼 없는 PK 는 없는 것으로, 이름 없는 것은 안 거른다. 제약을 받치는 유니크(이름이 달라도)는 뺀다 */
    private static List<Keyed> keyed(Table t) {
        List<Keyed> ks = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (t.pk() != null && !t.pk().columns().isEmpty()) {
            addKeyed(ks, seen, new Keyed(t.pk().name() == null ? "PK" : t.pk().name(), true, true, t.pk().columns()));
        }
        boolean[] pkBacked = new boolean[ks.size()];
        for (UniqueKey u : t.uniques()) {
            Keyed k = new Keyed(nz(u.name()), false, true, u.columns());
            if (!backs(ks, pkBacked, k)) {
                addKeyed(ks, seen, k);
            }
        }
        boolean[] backed = new boolean[ks.size()];
        for (Index ix : t.indexes()) {
            Keyed k = new Keyed(nz(ix.name()), false, ix.unique(), ix.columns());
            if (!ix.unique() || !backs(ks, backed, k)) {
                addKeyed(ks, seen, k);
            }
        }
        return ks;
    }

    /** 제약(PK·유니크)을 받치는 유니크 인덱스 — 컬럼이 똑같은 제약 하나에 한 번. H2 는 이름이 다르다(CONSTRAINT_8 ↔ PRIMARY_KEY_8) */
    private static boolean backs(List<Keyed> ks, boolean[] backed, Keyed k) {
        for (int i = 0; i < backed.length; i++) {
            if (!backed[i] && upAll(ks.get(i).cols()).equals(upAll(k.cols()))) {
                backed[i] = true;
                return true;
            }
        }
        return false;
    }

    private static void addKeyed(List<Keyed> ks, Set<String> seen, Keyed k) {
        if (k.name().isEmpty() || seen.add(up(k.name()))) {
            ks.add(k);
        }
    }

    /** PK 가 없는데 유니크·인덱스는 있다 — 셋 다 없으면 noIndex 가 본다 */
    private static void noPk(Table t, String s, List<Finding> out) {
        List<Keyed> ks = keyed(t);
        if ((t.pk() == null || t.pk().columns().isEmpty()) && !ks.isEmpty()) {
            int u = t.uniques().size();
            out.add(new Finding(Rule.NO_PK, s, t.name(), null, "유니크 " + u + " · 인덱스 " + (ks.size() - u)));
        }
    }

    /** FK 컬럼 ↔ 부모 컬럼. 부모가 whole 밖이거나 열 수가 다르면 안 짚는다 */
    private static void fkTypes(Table t, String s, Map<String, Table> whole, List<Finding> out) {
        for (ForeignKey fk : t.fks()) {
            Table parent = whole.get(refKey(t, fk));
            List<String> pcols = parent == null ? List.of() : parentColumns(parent, fk);
            if (pcols.size() == fk.columns().size()) {
                for (int i = 0; i < pcols.size(); i++) {
                    Column c = column(t, fk.columns().get(i));
                    Column p = column(parent, pcols.get(i));
                    String why = c == null || p == null ? null : differ(c, p);
                    if (why != null) {
                        out.add(new Finding(Rule.FK_TYPE_MISMATCH, s, t.name(), c.name(), "FK " + nz(fk.name()) + " → " + parent.name() + "." + p.name() + ": " + why));
                    }
                }
            }
        }
    }

    /** refColumns, 비면 부모 PK(없으면 빈 목록) */
    private static List<String> parentColumns(Table parent, ForeignKey fk) {
        if (!fk.refColumns().isEmpty()) {
            return fk.refColumns();
        }
        return parent.pk() == null ? List.of() : parent.pk().columns();
    }

    private static Column column(Table t, String name) {
        for (Column c : t.columns()) {
            if (up(c.name()).equals(up(name))) {
                return c;
            }
        }
        return null;
    }

    /** 형식 → 길이 → 정밀도. 둘 다 알 때만 본다, 같으면 null */
    static String differ(Column c, Column p) {
        String a = norm(c.nativeType());
        String b = norm(p.nativeType());
        if (a == null || b == null) {
            return null;
        }
        if (!a.equals(b)) {
            return "형식 " + a + " ≠ " + b;
        }
        if (c.length() != null && p.length() != null && !c.length().equals(p.length())) {
            return "길이 " + c.length() + " ≠ " + p.length();
        }
        if (c.precision() != null && p.precision() != null && (!c.precision().equals(p.precision()) || scale(c) != scale(p))) {
            return "정밀도 " + c.precision() + "," + scale(c) + " ≠ " + p.precision() + "," + scale(p);
        }
        return null;
    }

    private static int scale(Column c) {
        return c.scale() == null ? 0 : c.scale();
    }

    /** 대문자 · 괄호 뗌 · 공백 하나로. 비면 null */
    static String norm(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return SPACES.matcher(PARENS.matcher(type.toUpperCase(Locale.ROOT)).replaceAll(" ").trim()).replaceAll(" ");
    }

    /** 덮인 키마다 하나 — 덮는 첫 키를 적는다 */
    private static void dupIndexes(Table t, String s, List<Finding> out) {
        List<Keyed> ks = keyed(t);
        for (int i = 0; i < ks.size(); i++) {
            Keyed a = coverer(ks, i);
            if (a != null) {
                Keyed b = ks.get(i);
                out.add(new Finding(Rule.DUP_INDEX, s, t.name(), String.join(",", b.cols()), "인덱스 " + b.name() + " ⊂ " + a.name() + "(" + String.join(",", a.cols()) + ")"));
            }
        }
    }

    private static Keyed coverer(List<Keyed> ks, int i) {
        for (int j = 0; j < ks.size(); j++) {
            if (j != i && covered(ks.get(i), ks.get(j))) {
                return ks.get(j);
            }
        }
        return null;
    }

    /** b 가 a 에 덮인다 — b 의 컬럼(대문자)이 a 의 앞머리(b.size ≤ a.size). 정렬 방향은 안 본다 */
    static boolean covered(Keyed b, Keyed a) {
        if (b.cols().isEmpty() || b.cols().size() > a.cols().size() || !upAll(a.cols().subList(0, b.cols().size())).equals(upAll(b.cols()))) {
            return false;
        }
        boolean same = a.cols().size() == b.cols().size();
        if (b.unique()) {
            return same && a.unique() && (a.pk() || (!b.pk() && b.name().compareTo(a.name()) > 0)); // 유니크는 제약 — 똑같은 유니크가 또 있을 때만, PK 가 이긴다
        }
        return !same || a.unique() || b.name().compareTo(a.name()) > 0; // 비유니크 — 앞머리면 중복, 똑같으면 유니크 쪽·이름 앞인 쪽이 남는다
    }

    private static List<String> upAll(List<String> cols) {
        return cols.stream().map(StructureLint::up).toList();
    }

    /** 범위 안 FK 그래프(자식 → 부모, 자기 참조·범위 밖 부모 버림)의 강연결 성분 — 둘 이상이면 하나씩 */
    private static void cycles(List<Table> tables, List<Finding> out) {
        Map<String, Integer> node = new HashMap<>();
        for (int i = 0; i < tables.size(); i++) {
            node.putIfAbsent(key(tables.get(i).schema(), tables.get(i).name()), i);
        }
        List<Set<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < tables.size(); i++) {
            Set<Integer> to = new LinkedHashSet<>();
            for (ForeignKey fk : tables.get(i).fks()) {
                Integer j = node.get(refKey(tables.get(i), fk));
                if (j != null && j.intValue() != i) {
                    to.add(j);
                }
            }
            adj.add(to);
        }
        for (List<Integer> comp : new Scc(adj).run()) {
            if (comp.size() >= MIN_CYCLE) {
                out.add(cycleFinding(tables, comp));
            }
        }
    }

    /** 표 이름 오름차순 — 성분 안에 같은 이름이 둘이면 「스키마.표」 */
    private static Finding cycleFinding(List<Table> tables, List<Integer> comp) {
        Map<String, Integer> names = new HashMap<>();
        for (int i : comp) {
            names.merge(up(tables.get(i).name()), 1, Integer::sum);
        }
        NavigableMap<String, Table> byLabel = new TreeMap<>();
        for (int i : comp) {
            Table t = tables.get(i);
            byLabel.putIfAbsent(names.get(up(t.name())) > 1 ? nz(t.schema()) + "." + t.name() : t.name(), t);
        }
        Table first = byLabel.firstEntry().getValue();
        return new Finding(Rule.FK_CYCLE, nz(first.schema()), first.name(), null, "표 " + comp.size() + ": " + String.join("·", byLabel.keySet()));
    }

    /** Tarjan 강연결 성분. 재귀 깊이는 FK 사슬 길이 */
    private static final class Scc {
        private final List<Set<Integer>> adj;
        private final int[] index;
        private final int[] low;
        private final boolean[] onStack;
        private final Deque<Integer> stack = new ArrayDeque<>();
        private final List<List<Integer>> comps = new ArrayList<>();
        private int next = 1;

        Scc(List<Set<Integer>> adj) {
            this.adj = adj;
            this.index = new int[adj.size()];
            this.low = new int[adj.size()];
            this.onStack = new boolean[adj.size()];
        }

        List<List<Integer>> run() {
            for (int v = 0; v < adj.size(); v++) {
                if (index[v] == 0) {
                    strong(v);
                }
            }
            return comps;
        }

        private void strong(int v) {
            index[v] = next;
            low[v] = next;
            next++;
            stack.push(v);
            onStack[v] = true;
            for (int w : adj.get(v)) {
                if (index[w] == 0) {
                    strong(w);
                    low[v] = Math.min(low[v], low[w]);
                } else if (onStack[w]) {
                    low[v] = Math.min(low[v], index[w]);
                }
            }
            if (low[v] == index[v]) {
                comps.add(pop(v));
            }
        }

        private List<Integer> pop(int v) {
            List<Integer> c = new ArrayList<>();
            int w;
            do {
                w = stack.pop();
                onStack[w] = false;
                c.add(w);
            } while (w != v);
            return c;
        }
    }

    private static boolean on(Options o, Rule r) {
        return !o.off().contains(r);
    }

    private static boolean isView(Table t) {
        return t.type() != null && t.type().toUpperCase(Locale.ROOT).contains("VIEW");
    }

    /** FK 가 가리키는 「스키마.표」 — refSchema 가 없으면 자식 스키마 */
    private static String refKey(Table child, ForeignKey fk) {
        return key(fk.refSchema() == null || fk.refSchema().isBlank() ? child.schema() : fk.refSchema(), fk.refTable());
    }

    private static String key(String schema, String table) {
        return up(schema) + "." + up(table);
    }

    private static String up(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
