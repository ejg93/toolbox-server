package kr.ejg.toolbox.core.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.gen.DdlReader.Draft;
import kr.ejg.toolbox.core.gen.DdlReader.Unreadable;
import kr.ejg.toolbox.core.meta.Column;

/** ALTER TABLE 한 문장(1-95, 설계 71 §3-10) — 아는 표만 고친다. 못 읽는 동작은 unreadable 로 */
final class DdlAlter {
    private static final Pattern HEAD = Pattern.compile("^ALTER\\s++TABLE\\s++(?:IF\\s++EXISTS\\s++)?(?:ONLY\\s++)?(\\S++)\\s++",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD_HEAD = Pattern.compile("^(?:WITH\\s++(?:NO)?CHECK\\s++)?ADD\\b\\s*+", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLUMN_WORD = Pattern.compile("^COLUMN\\b\\s*+(?:IF\\s++(?:NOT\\s++)?EXISTS\\s++)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_HEAD = Pattern.compile("^DROP\\b\\s*+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_OTHER = Pattern.compile("^(?:CONSTRAINT|PRIMARY|FOREIGN|INDEX|KEY|PARTITION|CHECK|DEFAULT|PERIOD|UNIQUE)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REDEFINE_HEAD = Pattern.compile("^(MODIFY|CHANGE)\\b\\s*+", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT = Pattern.compile("^COMMENT\\s*+=?\\s*+'((?:[^']|'')*+)'$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HARMLESS = Pattern.compile("^(?:OWNER\\s++TO|ENABLE|DISABLE|(?:NO)?CHECK\\s++CONSTRAINT|WITH\\s++(?:NO)?CHECK\\s++CHECK\\s++CONSTRAINT"
            + "|SET\\s++(?:TABLESPACE|SCHEMA|LOGGED|UNLOGGED|WITH|WITHOUT|\\()|CLUSTER\\s++ON|REPLICA\\s++IDENTITY|ATTACH\\s++PARTITION|DETACH\\s++PARTITION"
            + "|AUTO_INCREMENT|ENGINE|(?:DEFAULT\\s++)?(?:CHARSET|CHARACTER\\s++SET|COLLATE)|CONVERT\\s++TO|ROW_FORMAT|FORCE|(?:NO\\s++)?INHERIT"
            + "|VALIDATE\\s++CONSTRAINT|REBUILD|SWITCH|TRUNCATE\\s++PARTITION|(?:NO)?LOGGING|(?:NO)?PARALLEL|MOVE|(?:NO)?CACHE"
            + "|ALTER\\s++COLUMN\\s++\\S++\\s++SET\\s++(?:STATISTICS|STORAGE))(?![\\w$#])", Pattern.CASE_INSENSITIVE);
    /** 동사 없는 조각을 앞 ADD 에 잇지 않는 머리 — 다른 동사로 시작하면 못 읽음 */
    private static final Pattern OTHER_VERB = Pattern.compile("^(?:ALTER|RENAME|ADD|DROP|MODIFY|CHANGE)\\b", Pattern.CASE_INSENSITIVE);
    /** DROP 이름 뒤 낱말 */
    private static final Pattern DROP_TAIL = Pattern.compile("\\s++(?:CASCADE|RESTRICT)(?:\\s++CONSTRAINTS)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final int LINE_MAX = 80;

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
            add(d, p.substring(a.end()), unreadable);
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
        if (adding && !OTHER_VERB.matcher(p).lookingAt()) {
            add(d, p, unreadable);
            return true;
        }
        miss(d, p, unreadable);
        return false;
    }

    /** ADD 뒤 — 컬럼 정의 또는 제약. 괄호로 싼 목록이면 항목마다 */
    private static void add(Draft d, String text, List<Unreadable> unreadable) {
        for (String item : inner(column(text))) {
            String it = column(item);
            if (DdlPull.constraintLine(it)) {
                constraint(d, it);
            } else {
                DdlReader.item(d, it, unreadable);
            }
        }
    }

    /** 제약 하나 — FK·UQ·PK(이름 없는 PK 는 빈 이름)·CHECK */
    private static void constraint(Draft d, String it) {
        Matcher f = DdlReader.FK.matcher(it);
        while (f.find()) {
            d.fks.add(DdlReader.fk(d, f.group(1) != null ? f.group(1) : f.group(2), f.group(3), f.group(4), f.group(5)));
        }
        Matcher u = DdlReader.UQ.matcher(DdlReader.QUOTED.matcher(it).replaceAll("''"));
        while (u.find()) {
            d.uqs.add(DdlReader.uq(u));
        }
        DdlReader.alterPk(d, it);
        DdlReader.checks(d, it);
    }

    /** DROP [COLUMN] 이름 · DROP (이름, …). 컬럼 밖 대상(제약·인덱스·파티션)과 없는 이름은 못 읽음 */
    private static void drop(Draft d, String piece, String text, List<Unreadable> unreadable) {
        if (DROP_OTHER.matcher(text).lookingAt()) {
            miss(d, piece, unreadable);
            return;
        }
        boolean missed = false;
        for (String n : inner(column(text))) {
            String name = DdlReader.unquote(DROP_TAIL.matcher(n).replaceFirst(""));
            if (d.cols.removeIf(c -> c.name().equalsIgnoreCase(name))) {
                d.pk.removeIf(p -> p.equalsIgnoreCase(name));
            } else {
                missed = true;
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
        for (String it : inner(column(text))) {
            String[] head = SPACES.split(it, 2);
            String old = DdlReader.unquote(head[0]);
            String def = change ? (head.length > 1 ? head[1] : "") : it;
            if (!replace(d, old, def)) {
                miss(d, piece, unreadable);
            }
        }
    }

    /** 정의가 통째(타입 있는 컬럼 하나)일 때만 옛 자리에 넣는다. 순번은 옛 것, 코멘트는 새 것이 없으면 옛 것 */
    private static boolean replace(Draft d, String old, String def) {
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
        d.cols.set(at, with(c, was.ordinal(), c.comment() != null ? c.comment() : was.comment()));
        d.pk.replaceAll(p -> p.equalsIgnoreCase(old) ? c.name() : p);
        d.uqs.addAll(s.uqs);
        d.checks.addAll(s.checks);
        d.fks.addAll(s.fks);
        if (!s.pk.isEmpty()) {
            d.pk.clear();
            d.pk.addAll(s.pk);
        }
        return true;
    }

    /** 「(a, b)」 면 안쪽 항목들, 아니면 글 하나 */
    private static List<String> inner(String text) {
        String t = text.strip();
        if (!t.startsWith("(") || DdlReader.matching(t, 0) != t.length() - 1) {
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
