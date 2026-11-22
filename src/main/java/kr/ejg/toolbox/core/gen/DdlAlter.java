package kr.ejg.toolbox.core.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.gen.DdlReader.Draft;
import kr.ejg.toolbox.core.gen.DdlReader.Unreadable;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.SortOrder;
import kr.ejg.toolbox.core.meta.UniqueKey;

/** ALTER TABLE 한 문장(1-95, 설계 71 §3-10) — 아는 표만 고친다. 못 읽는 동작은 unreadable 로 */
final class DdlAlter {
    private static final Pattern HEAD = Pattern.compile("^ALTER\\s++TABLE\\s++(?:IF\\s++EXISTS\\s++)?(?:ONLY\\s++)?(" + DdlReaderParts.QNAME + ")\\s++",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD_HEAD = Pattern.compile("^(?:WITH\\s++(?:NO)?CHECK\\s++)?ADD\\b\\s*+", Pattern.CASE_INSENSITIVE);
    /** 1-122 — 머리의 COLUMN 과 IF [NOT] EXISTS 를 뗀다(ADD IF NOT EXISTS c … 도) */
    private static final Pattern COLUMN_WORD = Pattern.compile("^(?:COLUMN\\b\\s*+)?(?:IF\\s++(?:NOT\\s++)?EXISTS\\s++)?", Pattern.CASE_INSENSITIVE);
    /** MODIFY COLUMN · CHANGE 꼴 머리(1-101 재정의 꼴) */
    private static final Pattern COLUMN_HEAD = Pattern.compile("^COLUMN\\b", Pattern.CASE_INSENSITIVE);
    /** 1-122(A1-05) — IF [NOT] EXISTS 머리 */
    private static final Pattern IF_EXISTS = Pattern.compile("^(?:COLUMN\\b\\s*+)?IF\\s++(?:NOT\\s++)?EXISTS\\b", Pattern.CASE_INSENSITIVE);
    /** 1-122(A1-15) — 동사 없는 「이름=값」 조각은 앞 ADD 를 잇지 않는다 */
    private static final Pattern ASSIGN = Pattern.compile("^[\\w$#]++\\s*+=");
    private static final Pattern DROP_HEAD = Pattern.compile("^DROP\\b\\s*+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_OTHER = Pattern.compile("^(?:CONSTRAINT|PRIMARY|FOREIGN|INDEX|KEY|PARTITION|CHECK|DEFAULT|PERIOD|UNIQUE)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REDEFINE_HEAD = Pattern.compile("^(MODIFY|CHANGE)\\b\\s*+", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT = Pattern.compile("^COMMENT\\s*+=?\\s*+'((?:[^']|'')*+)'$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HARMLESS = Pattern.compile("^(?:OWNER\\s++TO|ENABLE|DISABLE|(?:NO)?CHECK\\s++CONSTRAINT|WITH\\s++(?:NO)?CHECK\\s++CHECK\\s++CONSTRAINT"
            + "|SET\\s++(?:TABLESPACE|SCHEMA|LOGGED|UNLOGGED|WITH|WITHOUT|\\()|CLUSTER\\s++ON|REPLICA\\s++IDENTITY|ATTACH\\s++PARTITION|DETACH\\s++PARTITION"
            + "|AUTO_INCREMENT|ENGINE|(?:DEFAULT\\s++)?(?:CHARSET|CHARACTER\\s++SET|COLLATE)|CONVERT\\s++TO|ROW_FORMAT|FORCE|(?:NO\\s++)?INHERIT"
            + "|VALIDATE\\s++CONSTRAINT|REBUILD|SWITCH|TRUNCATE\\s++PARTITION|(?:NO)?LOGGING|(?:NO)?PARALLEL|MOVE|(?:NO)?CACHE"
            + "|ALTER\\s++COLUMN\\s++\\S++\\s++SET\\s++(?:STATISTICS|STORAGE)"
            + "|(?:ALGORITHM|LOCK)\\s*+=?\\s*+(?:DEFAULT|INSTANT|INPLACE|COPY|NOCOPY|NONE|SHARED|EXCLUSIVE))(?![\\w$#])", Pattern.CASE_INSENSITIVE);
    /** 동사 없는 조각을 앞 ADD 에 잇지 않는 머리 — 다른 동사로 시작하면 못 읽음 */
    private static final Pattern OTHER_VERB = Pattern.compile("^(?:ALTER|RENAME|ADD|DROP|MODIFY|CHANGE)\\b", Pattern.CASE_INSENSITIVE);
    /** DROP 이름 뒤 낱말 */
    private static final Pattern DROP_TAIL = Pattern.compile("(?<!\\s)\\s++(?:CASCADE|RESTRICT)(?:\\s++CONSTRAINTS)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACES = Pattern.compile("\\s+");
    /** 1-100 — ADD 뒤가 컬럼도 제약도 아닌 것 */
    private static final Pattern ADD_OTHER = Pattern.compile("^(?:PARTITION|SUBPARTITION|SUPPLEMENTAL|OVERFLOW)\\b", Pattern.CASE_INSENSITIVE);
    /** 1-100 — MSSQL {@code [CONSTRAINT 이름] DEFAULT 식 FOR 컬럼} */
    private static final Pattern DEFAULT_FOR = Pattern.compile("^(?:CONSTRAINT\\s++\\S++\\s++)?DEFAULT\\s++(.+?)(?<!\\s)\\s++FOR\\s++(\\S++)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final int LINE_MAX = 80;
    private static final Pattern NULL_WORD = Pattern.compile("\\bNULL\\b", Pattern.CASE_INSENSITIVE);
    /** 1-106 — DEFAULT NULL 의 NULL 은 「NULL 을 적었다」 가 아니다 */
    private static final Pattern DEFAULT_NULL = Pattern.compile("\\bDEFAULT\\s++NULL\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEFAULT_WORD = Pattern.compile("\\bDEFAULT\\b", Pattern.CASE_INSENSITIVE);

    /** 1-101 재정의 꼴 — KEEP: MODIFY (…) 괄호(Oracle·Tibero) · ASK: 맨 MODIFY(방언 모름) · WHOLE: MODIFY COLUMN·CHANGE(MySQL 전용 문법) */
    private enum Redef { KEEP, ASK, WHOLE }

    private DdlAlter() {
    }

    /** ALTER TABLE 이 아니면 거짓. 표를 모르면 later 에 미루고(later 가 null 이면 조용히 넘긴다) 참 */
    static boolean apply(String stmt, Map<String, Draft> tables, List<Unreadable> unreadable, List<String> later) {
        Matcher h = HEAD.matcher(stmt);
        if (!h.lookingAt()) {
            return false;
        }
        Draft d = DdlPull.find(tables, DdlReader.nameParts(h.group(1)));
        if (d == null) {
            if (later != null) {
                later.add(stmt);
            }
            return true;
        }
        boolean adding = false;
        for (String piece : DdlReader.splitTop(stmt.substring(h.end()), ',')) {
            String p = piece.strip();
            if (!p.isEmpty()) {
                adding = action(d, p, adding, unreadable);
            }
        }
        return true;
    }

    /** 동작 하나. 돌려주는 값 — 다음 동사 없는 조각이 ADD 를 잇는가(MSSQL {@code ADD a INT, b INT}) */
    private static boolean action(Draft d, String p, boolean adding, List<Unreadable> unreadable) {
        Matcher a = ADD_HEAD.matcher(p);
        if (a.lookingAt()) {
            add(d, p, p.substring(a.end()), unreadable);
            return true;
        }
        Matcher dr = DROP_HEAD.matcher(p);
        if (dr.lookingAt()) {
            drop(d, p, p.substring(dr.end()), unreadable);
            return false;
        }
        Matcher r = REDEFINE_HEAD.matcher(p);
        if (r.lookingAt()) {
            redefine(d, p, "CHANGE".equalsIgnoreCase(r.group(1)), p.substring(r.end()), unreadable);
            return false;
        }
        Matcher c = COMMENT.matcher(p);
        if (c.matches()) {
            d.comment = c.group(1).replace("''", "'");
            return false;
        }
        if (HARMLESS.matcher(p).lookingAt()) {
            return false;
        }
        if (ASSIGN.matcher(p).lookingAt()) {
            miss(d, p, unreadable); // 1-122 — 모르는 옵션(이름=값)은 컬럼이 아니다
            return false;
        }
        if (adding && !OTHER_VERB.matcher(p).lookingAt()) {
            add(d, p, p, unreadable);
            return true;
        }
        miss(d, p, unreadable);
        return false;
    }

    /** ADD 뒤 — 컬럼 정의 또는 제약. 괄호로 싼 목록이면 항목마다. 컬럼도 제약도 아닌 것·아무것도 못 읽은 제약은 못 읽음(1-100) */
    private static void add(Draft d, String piece, String text, List<Unreadable> unreadable) {
        String body = column(text);
        if (ADD_OTHER.matcher(body).lookingAt()) {
            miss(d, piece, unreadable);
            return;
        }
        boolean ifNot = IF_EXISTS.matcher(text.strip()).lookingAt();
        for (String item : inner(body)) {
            String it = column(item);
            if ((ifNot || IF_EXISTS.matcher(item.strip()).lookingAt()) && has(d, it)) {
                continue; // 1-122 A1-05 — ADD … IF NOT EXISTS: 이미 있는 컬럼은 그대로
            }
            if (DEFAULT_FOR.matcher(it).matches()) {
                if (!defaultFor(d, it)) { // 이름 없는 MSSQL 꼴도(마무리 리뷰 I2) — constraintLine 은 DEFAULT 로 시작하는 줄을 모른다
                    miss(d, piece, unreadable);
                }
            } else if (!DdlPull.constraintLine(it)) {
                DdlReader.item(d, it, unreadable);
            } else if (!constraint(d, it)) {
                miss(d, piece, unreadable);
            }
        }
    }

    /** 제약 하나 — FK·UQ·PK(이름 없는 PK 는 빈 이름)·CHECK·KEY/INDEX·MSSQL DEFAULT … FOR. 하나라도 읽었으면 참 */
    private static boolean constraint(Draft d, String it) {
        int before = keyCount(d);
        Matcher f = DdlReader.FK.matcher(it);
        while (f.find()) {
            d.fks.add(DdlReader.fk(d, f.group(1) != null ? f.group(1) : f.group(2), f.group(3), f.group(4), f.group(5)));
        }
        Matcher u = DdlReader.UQ.matcher(DdlReader.QUOTED.matcher(it).replaceAll("''"));
        while (u.find()) {
            d.uqs.add(DdlReader.uq(u));
        }
        boolean pk = DdlReader.alterPk(d, it);
        DdlReader.checks(d, it);
        DdlReader.inlineIndex(d, it);
        boolean dflt = defaultFor(d, it);
        return pk || dflt || keyCount(d) > before;
    }

    private static int keyCount(Draft d) {
        return d.fks.size() + d.uqs.size() + d.checks.size() + d.indexes.size();
    }

    /** 1-100 — MSSQL {@code DEFAULT 식 FOR 컬럼} 을 그 컬럼의 기본값으로. 컬럼이 없으면 거짓 */
    private static boolean defaultFor(Draft d, String it) {
        Matcher m = DEFAULT_FOR.matcher(it);
        if (!m.matches()) {
            return false;
        }
        String name = DdlReader.unquote(m.group(2));
        for (int i = 0; i < d.cols.size(); i++) {
            Column c = d.cols.get(i);
            if (c.name().equalsIgnoreCase(name)) {
                d.cols.set(i, new Column(c.name(), c.ordinal(), c.nativeType(), c.jdbcType(), c.length(), c.precision(), c.scale(), c.nullable(),
                        m.group(1).strip(), c.comment(), c.domain(), c.autoIncrement()));
                return true;
            }
        }
        return false;
    }

    /** DROP [COLUMN] 이름 · DROP (이름, …). 컬럼 밖 대상(제약·인덱스·파티션)과 없는 이름은 못 읽음 */
    private static void drop(Draft d, String piece, String text, List<Unreadable> unreadable) {
        if (DROP_OTHER.matcher(text).lookingAt()) {
            miss(d, piece, unreadable);
            return;
        }
        boolean missed = false;
        boolean ifExists = IF_EXISTS.matcher(text.strip()).lookingAt(); // 1-122 — DROP … IF EXISTS 없는 이름은 못 읽음이 아니다
        for (String n : inner(column(text))) {
            String name = DdlReader.unquote(DROP_TAIL.matcher(n).replaceFirst(""));
            if (d.cols.removeIf(c -> c.name().equalsIgnoreCase(name))) {
                d.pk.removeIf(p -> p.equalsIgnoreCase(name));
                dropFromKeys(d, name);
            } else {
                missed |= !ifExists;
            }
        }
        for (int i = 0; i < d.cols.size(); i++) {
            Column c = d.cols.get(i);
            d.cols.set(i, with(c, i + 1, c.comment()));
        }
        if (missed) {
            miss(d, piece, unreadable);
        }
    }

    /** MODIFY 정의 · CHANGE 옛이름 정의 — 괄호 목록이면 항목마다 */
    private static void redefine(Draft d, String piece, boolean change, String text, List<Unreadable> unreadable) {
        String body = column(text);
        Redef mode = change || COLUMN_HEAD.matcher(text.strip()).lookingAt() ? Redef.WHOLE : listed(body.strip()) ? Redef.KEEP : Redef.ASK;
        for (String it : inner(body)) {
            String[] head = SPACES.split(it, 2);
            String old = DdlReader.unquote(head[0]);
            String def = change ? (head.length > 1 ? head[1] : "") : it;
            if (!replace(d, old, def, mode)) {
                miss(d, piece, unreadable);
            }
        }
    }

    /**
     * 정의가 통째(타입 있는 컬럼 하나)일 때만 옛 자리에 넣는다. 순번은 옛 것, 코멘트는 새 것이 없으면 옛 것.
     * 1-101 — 괄호 꼴은 안 적은 NULL·DEFAULT 를 옛 것으로, 맨 꼴이 실제로 지웠으면 notes 에 「방언 모호」
     */
    private static boolean replace(Draft d, String old, String def, Redef mode) {
        int at = -1;
        for (int i = 0; i < d.cols.size() && at < 0; i++) {
            at = d.cols.get(i).name().equalsIgnoreCase(old) ? i : -1;
        }
        if (at < 0 || def.isBlank()) {
            return false;
        }
        Draft s = new Draft(d.schema, d.name);
        s.scope = d.scope;
        List<Unreadable> u = new ArrayList<>();
        DdlReader.item(s, def, u);
        if (!u.isEmpty() || s.cols.size() != 1 || s.cols.get(0).nativeType() == null) {
            return false;
        }
        Column c = s.cols.get(0);
        Column was = d.cols.get(at);
        put(d, at, c, was, def, mode);
        d.pk.replaceAll(p -> p.equalsIgnoreCase(old) ? c.name() : p);
        renameInKeys(d, old, c.name());
        d.uqs.addAll(s.uqs);
        d.checks.addAll(s.checks);
        d.fks.addAll(s.fks);
        if (!s.pk.isEmpty()) {
            d.pk.clear();
            d.pk.addAll(s.pk);
        }
        return true;
    }

    /** 1-100 — 지운 컬럼을 유니크·인덱스에서 뺀다(다 빠지면 키도 지운다). 그 컬럼을 품은 FK 는 통째로 */
    private static void dropFromKeys(Draft d, String name) {
        List<UniqueKey> uqs = new ArrayList<>();
        for (UniqueKey u : d.uqs) {
            List<String> cols = u.columns().stream().filter(c -> !c.equalsIgnoreCase(name)).toList();
            if (!cols.isEmpty()) {
                uqs.add(cols.size() == u.columns().size() ? u : new UniqueKey(u.name(), cols));
            }
        }
        d.uqs.clear();
        d.uqs.addAll(uqs);
        List<Index> indexes = new ArrayList<>();
        for (Index x : d.indexes) {
            Index left = without(x, name);
            if (!left.columns().isEmpty()) {
                indexes.add(left);
            }
        }
        d.indexes.clear();
        d.indexes.addAll(indexes);
        d.fks.removeIf(f -> f.columns().stream().anyMatch(c -> c.equalsIgnoreCase(name)));
    }

    /** 인덱스에서 컬럼 하나를 뺀다 — 같은 자리 정렬도(정렬이 더 짧으면 있는 만큼만) */
    private static Index without(Index x, String name) {
        List<String> cols = new ArrayList<>();
        List<SortOrder> sorts = new ArrayList<>();
        for (int i = 0; i < x.columns().size(); i++) {
            if (!x.columns().get(i).equalsIgnoreCase(name)) {
                cols.add(x.columns().get(i));
                if (i < x.sorts().size()) {
                    sorts.add(x.sorts().get(i));
                }
            }
        }
        return cols.size() == x.columns().size() ? x : new Index(x.name(), x.unique(), cols, sorts);
    }

    /** 1-100 — 이름 바꾼 컬럼을 유니크·인덱스·FK 의 columns 에서 새 이름으로. 다른 표 FK 의 refColumns 는 안 고친다 */
    private static void renameInKeys(Draft d, String old, String neu) {
        if (old.equalsIgnoreCase(neu)) {
            return;
        }
        d.uqs.replaceAll(u -> new UniqueKey(u.name(), renamed(u.columns(), old, neu)));
        d.indexes.replaceAll(x -> new Index(x.name(), x.unique(), renamed(x.columns(), old, neu), x.sorts()));
        d.fks.replaceAll(f -> new ForeignKey(f.name(), renamed(f.columns(), old, neu), f.refSchema(), f.refTable(), f.refColumns(), f.deleteRule(),
                f.updateRule()));
    }

    private static List<String> renamed(List<String> cols, String old, String neu) {
        return cols.stream().map(c -> c.equalsIgnoreCase(old) ? neu : c).toList();
    }

    /** 1-101 — 옛 자리에 새 정의를 넣는다. 맨 MODIFY 가 NOT NULL·DEFAULT 를 실제로 지웠으면 notes 에 「방언 모호」 */
    private static void put(Draft d, int at, Column c, Column was, String def, Redef mode) {
        String bare = DdlReader.QUOTED.matcher(def).replaceAll("''");
        boolean saysNull = NULL_WORD.matcher(DEFAULT_NULL.matcher(bare).replaceAll("DEFAULT")).find();
        boolean saysDefault = DEFAULT_WORD.matcher(bare).find();
        d.cols.set(at, merged(c, was, mode == Redef.KEEP, saysNull, saysDefault));
        if (mode == Redef.ASK && (!saysNull && !was.nullable() || !saysDefault && was.defaultValue() != null)) {
            d.notes.merge(c.name().toUpperCase(Locale.ROOT), DdlReader.AMBIGUOUS, (a, b) -> a.contains(b) ? a : a + " / " + b);
        }
    }

    /** 1-101 — 새 정의에 옛 순번·코멘트를 얹고, keep 이면 안 적은 NULL·DEFAULT(와 자동증가)도 옛 것 */
    private static Column merged(Column c, Column was, boolean keep, boolean saysNull, boolean saysDefault) {
        return new Column(c.name(), was.ordinal(), c.nativeType(), c.jdbcType(), c.length(), c.precision(), c.scale(),
                keep && !saysNull ? was.nullable() : c.nullable(), keep && !saysDefault ? was.defaultValue() : c.defaultValue(),
                c.comment() != null ? c.comment() : was.comment(), c.domain(), c.autoIncrement() || keep && was.autoIncrement());
    }

    /** 「(a, b)」 꼴인가 */
    private static boolean listed(String t) {
        return t.startsWith("(") && DdlReader.matching(t, 0) == t.length() - 1;
    }

    /** 「(a, b)」 면 안쪽 항목들, 아니면 글 하나 */
    private static List<String> inner(String text) {
        String t = text.strip();
        if (!listed(t)) {
            return List.of(t);
        }
        List<String> out = new ArrayList<>();
        for (String s : DdlReader.splitTop(t.substring(1, t.length() - 1), ',')) {
            if (!s.isBlank()) {
                out.add(s.strip());
            }
        }
        return out;
    }

    /** 머리 「COLUMN [IF [NOT] EXISTS]」 를 뗀다 */
    /** 컬럼 정의 항목의 이름이 이미 표에 있나(대소문자 무시) */
    private static boolean has(Draft d, String item) {
        String name = DdlReader.unquote(DdlReader.firstToken(item)[0]);
        return d.cols.stream().anyMatch(c -> c.name().equalsIgnoreCase(name));
    }

    private static String column(String text) {
        Matcher cw = COLUMN_WORD.matcher(text.strip());
        return cw.lookingAt() ? text.strip().substring(cw.end()) : text.strip();
    }

    private static Column with(Column c, int ordinal, String comment) {
        return new Column(c.name(), ordinal, c.nativeType(), c.jdbcType(), c.length(), c.precision(), c.scale(), c.nullable(), c.defaultValue(),
                comment, c.domain(), c.autoIncrement());
    }

    private static void miss(Draft d, String piece, List<Unreadable> unreadable) {
        String line = "ALTER TABLE " + d.name + " " + SPACES.matcher(piece).replaceAll(" ");
        unreadable.add(new Unreadable(d.name, line.length() > LINE_MAX ? line.substring(0, LINE_MAX) : line));
    }
}
