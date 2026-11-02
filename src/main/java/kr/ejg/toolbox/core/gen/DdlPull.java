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
import kr.ejg.toolbox.core.meta.UniqueKey;

/**
 * 1-71(설계 54 §3-4) — 이름만 읽던 표(typed·CTAS·LIKE·PARTITION OF)가 같은 입력 앞의 원본 표·형식을 가리키면 컬럼을 끌어온다.
 * LIKE·PARTITION OF 는 컬럼 + PK, typed 는 형식 속성 + 몸의 PK·NOT NULL·UNIQUE·제약 줄, CTAS 는 단순 SELECT 의 컬럼만(제약은 안 옮긴다).
 * 유니크·인덱스·FK·CHECK 는 원본에서 복사하지 않는다. DdlReader 의 메서드 수 상한(metric.classMethods 30) 때문에 따로 둔다.
 */
final class DdlPull {

    /** CREATE TYPE 이름 [OID '…'] (AS OBJECT | AS | UNDER 부모) ( — g1 이름, g2 부모. AS TABLE OF·VARRAY·AS ENUM·TYPE BODY 는 안 맞는다 */
    private static final Pattern CREATE_TYPE = Pattern.compile(
            "^CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:(?:NON)?EDITIONABLE\\s+)?TYPE\\s+(?!BODY\\b)([^\\s(]+)\\s+(?:OID\\s+'[^']*'\\s+)?"
                    + "(?:AS\\s+OBJECT|AS|UNDER\\s+([^\\s(]+))\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    /** 형식 몸의 속성 아닌 줄 — 조용히 건너뛴다 */
    private static final Pattern TYPE_MEMBER = Pattern.compile(
            "^(?:NOT\\s+)?(?:INSTANTIABLE|FINAL|OVERRIDING|MAP|ORDER|MEMBER|STATIC|CONSTRUCTOR)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTITION_SRC = Pattern.compile("(?i)PARTITION\\s+OF\\s+([^\\s(]+)");
    private static final Pattern LIKE_SRC = Pattern.compile("(?i)LIKE\\s+([^\\s(,]+)");
    private static final Pattern TYPED_SRC = Pattern.compile("(?i)OF\\s+([^\\s(]+)");
    /** typed 몸의 컬럼 줄 — 이름 [WITH OPTIONS] 나머지 */
    private static final Pattern TYPED_OPT = Pattern.compile("^([^\\s(]+)(?:\\s+WITH\\s+OPTIONS)?\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** typed 몸 컬럼 줄에서 아는 낱말 — 하나도 없으면 못 읽은 줄 */
    private static final Pattern TYPED_WORDS = Pattern.compile("\\b(?:PRIMARY\\s+KEY|NOT\\s+NULL|UNIQUE|DEFAULT|NULL|CONSTRAINT|CHECK|REFERENCES)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_NULL = Pattern.compile("\\bNOT\\s+NULL\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIQUE = Pattern.compile("\\bUNIQUE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AS_HEAD = Pattern.compile("(?i)^AS\\b\\s*");
    private static final Pattern SELECT_HEAD = Pattern.compile("(?i)^SELECT\\s+");
    private static final Pattern FROM = Pattern.compile("(?i)\\bFROM\\b");
    private static final Pattern SELECT_ANY = Pattern.compile("(?i)\\bSELECT\\b");
    /** FROM 표 [별칭] 뒤에 와도 컬럼이 그대로인 절 */
    private static final Pattern CTAS_END = Pattern.compile("(?i)^(?:WHERE|ORDER|GROUP|HAVING|LIMIT|FETCH|OFFSET)\\b");
    private static final Pattern FROM_TABLE = Pattern.compile("^([^\\s,()]+)(.*)$", Pattern.DOTALL);
    private static final Pattern ALIAS = Pattern.compile("(?i)^(?:AS\\s+)?[^\\s,()]+");
    /** SELECT 목록 항목 — [한정.]이름 [[AS] 별칭] */
    private static final Pattern CTAS_ITEM = Pattern.compile("^(?:[^\\s.]+\\.)?([^\\s.]+)(?:\\s+(?:AS\\s+)?(\\S+))?$", Pattern.CASE_INSENSITIVE);
    /** 식·함수·따옴표 글이 든 항목 — 컬럼을 추측하지 않는다 */
    private static final Pattern ITEM_BAD = Pattern.compile("[('\"+\\-*/|<>=]");
    private static final String PARTITION = "partition";
    private static final String STAR = "*";

    private DdlPull() {
    }

    /** 속성 형식 CREATE TYPE 이면 속성(부모 먼저)을 types 에 두고 참. 못 읽은 속성 줄은 형식 이름으로 unreadable */
    static boolean createType(String stmt, Map<String, Draft> types, List<Unreadable> unreadable) {
        Matcher m = CREATE_TYPE.matcher(stmt);
        if (!m.lookingAt()) {
            return false;
        }
        Draft t = new Draft("", DdlReader.last(DdlReader.nameParts(m.group(1))));
        if (m.group(2) != null) {
            copy(t, types.get(key(m.group(2))), false);
        }
        int close = DdlReader.matching(stmt, m.end() - 1);
        for (String it : DdlReader.splitTop(stmt.substring(m.end(), close < 0 ? stmt.length() : close), ',')) {
            String item = it.trim();
            if (!TYPE_MEMBER.matcher(item).lookingAt()) {
                DdlReader.item(t, item, unreadable);
            }
        }
        types.put(t.name.toUpperCase(Locale.ROOT), t);
        return true;
    }

    /** 이름만 읽은 표 d 에 원본을 끌어오면 참(notes 를 안 단다). key 는 DdlReader 의 NAME_ONLY 키 */
    static boolean pull(Draft d, String key, String after, Map<String, Draft> tables, Map<String, Draft> types, List<Unreadable> unreadable) {
        if (PARTITION.equals(key)) {
            return copy(d, source(PARTITION_SRC, after, tables), true);
        }
        if (DdlReader.LIKE.equals(key)) {
            return copy(d, source(LIKE_SRC, after, tables), true);
        }
        if (DdlReader.TYPED.equals(key)) {
            return typed(d, after, types, unreadable);
        }
        List<Column> cols = ctasColumns(after, tables);
        if (cols.isEmpty()) {
            return false;
        }
        cols.forEach(c -> d.cols.add(at(c, d.cols.size() + 1, c.name())));
        return true;
    }

    /** 괄호 안 {@code LIKE x [INCLUDING …]} — 원본이 앞에 있으면 그 자리에 컬럼 + PK */
    static boolean like(Draft d, String item) {
        Matcher m = LIKE_SRC.matcher(item);
        return m.lookingAt() && copy(d, d.scope.get(key(m.group(1))), true);
    }

    private static Draft source(Pattern p, String after, Map<String, Draft> tables) {
        Matcher m = p.matcher(after);
        return m.lookingAt() ? tables.get(key(m.group(1))) : null;
    }

    /** 원본 컬럼(순번 다시)과 PK. 원본이 없거나 컬럼이 없으면 거짓 — 이름만 읽은 원본은 끌어올 것이 없다 */
    private static boolean copy(Draft d, Draft src, boolean withPk) {
        if (src == null || src.cols.isEmpty()) {
            return false;
        }
        for (Column c : src.cols) {
            d.cols.add(at(c, d.cols.size() + 1, c.name()));
        }
        if (withPk && !src.pk.isEmpty()) {
            d.pk.addAll(src.pk);
            d.pkName = src.pkName;
        }
        return true;
    }

    /** OF 형식 [(몸)] — 형식 속성을 끌어오고(있으면) 몸은 늘 읽는다. 끌어왔으면 참 */
    private static boolean typed(Draft d, String after, Map<String, Draft> types, List<Unreadable> unreadable) {
        Matcher m = TYPED_SRC.matcher(after);
        if (!m.lookingAt()) {
            return false;
        }
        boolean pulled = copy(d, types.get(key(m.group(1))), false);
        String rest = after.substring(m.end()).stripLeading();
        if (rest.startsWith("(")) {
            int close = DdlReader.matching(rest, 0);
            typedBody(d, rest.substring(1, close < 0 ? rest.length() : close), unreadable);
        }
        return pulled;
    }

    /** typed 몸 — 제약 줄은 item 에, 「컬럼 [WITH OPTIONS] PRIMARY KEY|NOT NULL|UNIQUE|DEFAULT …」 는 표식만, 그 밖은 unreadable */
    private static void typedBody(Draft d, String body, List<Unreadable> unreadable) {
        for (String it : DdlReader.splitTop(body, ',')) {
            String item = it.trim();
            Matcher o = TYPED_OPT.matcher(item);
            if (item.isEmpty()) {
                continue;
            }
            if (DdlReader.CONSTRAINT_LINE.matcher(item).find()) {
                DdlReader.item(d, item, unreadable);
            } else if (o.matches() && TYPED_WORDS.matcher(DdlReader.QUOTED.matcher(o.group(2)).replaceAll("''")).find()) {
                typedOption(d, DdlReader.unquote(o.group(1)), DdlReader.QUOTED.matcher(o.group(2)).replaceAll("''"));
            } else {
                unreadable.add(new Unreadable(d.name, item));
            }
        }
    }

    private static void typedOption(Draft d, String name, String rest) {
        Matcher ip = DdlReader.INLINE_PK.matcher(rest);
        if (ip.find()) {
            d.pk.add(name);
            d.pkName = ip.group(1) == null ? d.pkName : DdlReader.unquote(ip.group(1));
        }
        if (NOT_NULL.matcher(rest).find()) {
            for (int i = 0; i < d.cols.size(); i++) {
                Column c = d.cols.get(i);
                if (c.name().equalsIgnoreCase(name)) {
                    d.cols.set(i, new Column(c.name(), c.ordinal(), c.nativeType(), c.jdbcType(), c.length(), c.precision(), c.scale(), false,
                            c.defaultValue(), c.comment(), c.domain()));
                }
            }
        }
        if (UNIQUE.matcher(rest).find()) {
            d.uqs.add(new UniqueKey(null, List.of(name)));
        }
    }

    /**
     * CTAS 컬럼 — 따옴표 글을 비운 뒤 {@code [(머리 이름 …)] [AS] [(] SELECT 목록 FROM 표 [별칭] [WHERE|ORDER|GROUP|HAVING|LIMIT|FETCH|OFFSET …]} 만.
     * 목록은 {@code *} 또는 맨 이름·한정 이름·별칭. 조인·쉼표·서브쿼리·식·함수 또는 원본이 없으면 빈 목록(추측하지 않는다)
     */
    static List<Column> ctasColumns(String after, Map<String, Draft> tables) {
        String s = DdlReader.QUOTED.matcher(after).replaceAll("''").trim();
        List<String> heads = List.of();
        if (s.startsWith("(")) {
            int close = DdlReader.matching(s, 0);
            heads = close < 0 ? List.of() : headNames(s.substring(1, close));
            if (heads.isEmpty()) {
                return List.of();
            }
            s = s.substring(close + 1).trim();
        }
        s = AS_HEAD.matcher(s).replaceFirst("");
        if (s.startsWith("(") && s.endsWith(")")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        Matcher sel = SELECT_HEAD.matcher(s);
        Matcher from = FROM.matcher(s);
        if (!sel.lookingAt() || !from.find(sel.end())) {
            return List.of();
        }
        Draft src = fromTable(s.substring(from.end()).trim(), tables);
        List<Column> cols = src == null ? List.of() : selected(s.substring(sel.end(), from.start()).trim(), src);
        if (heads.isEmpty()) {
            return cols;
        }
        return heads.size() == cols.size() ? renamed(cols, heads) : List.of();
    }

    /** 머리의 컬럼 이름 목록 — 전부 맨 이름이어야 한다(아니면 빈 목록) */
    private static List<String> headNames(String list) {
        List<String> out = new ArrayList<>();
        for (String p : DdlReader.splitTop(list, ',')) {
            String n = DdlReader.unquote(p.trim());
            if (!DdlReader.NAME.matcher(n).matches()) {
                return List.of();
            }
            out.add(n);
        }
        return out;
    }

    /** FROM 뒤 — 표 하나 [별칭] 뒤가 비거나 CTAS_END 절이고 서브쿼리가 없을 때만 그 표 */
    private static Draft fromTable(String tail, Map<String, Draft> tables) {
        Matcher t = FROM_TABLE.matcher(tail);
        if (!t.matches()) {
            return null;
        }
        String rest = t.group(2).trim();
        if (!rest.isEmpty() && !CTAS_END.matcher(rest).lookingAt()) {
            Matcher a = ALIAS.matcher(rest);
            rest = a.lookingAt() ? rest.substring(a.end()).trim() : rest;
        }
        boolean simple = rest.isEmpty() || CTAS_END.matcher(rest).lookingAt() && !SELECT_ANY.matcher(rest).find();
        return simple ? tables.get(key(t.group(1))) : null;
    }

    /** SELECT 목록 → 원본 컬럼(별칭이면 이름만 바꿈). 못 찾거나 식이면 빈 목록 */
    private static List<Column> selected(String list, Draft src) {
        if (STAR.equals(list)) {
            return new ArrayList<>(src.cols);
        }
        List<Column> out = new ArrayList<>();
        for (String it : DdlReader.splitTop(list, ',')) {
            String item = it.trim();
            Matcher m = CTAS_ITEM.matcher(item);
            Column c = ITEM_BAD.matcher(item).find() || !m.matches() ? null : column(src, DdlReader.unquote(m.group(1)));
            if (c == null) {
                return List.of();
            }
            out.add(m.group(2) == null ? c : at(c, c.ordinal(), DdlReader.unquote(m.group(2))));
        }
        return out;
    }

    private static Column column(Draft src, String name) {
        for (Column c : src.cols) {
            if (c.name().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    private static List<Column> renamed(List<Column> cols, List<String> names) {
        List<Column> out = new ArrayList<>();
        for (int i = 0; i < cols.size(); i++) {
            out.add(at(cols.get(i), cols.get(i).ordinal(), names.get(i)));
        }
        return out;
    }

    /** 같은 컬럼을 다른 순번·이름으로 */
    private static Column at(Column c, int ordinal, String name) {
        return new Column(name, ordinal, c.nativeType(), c.jdbcType(), c.length(), c.precision(), c.scale(), c.nullable(), c.defaultValue(),
                c.comment(), c.domain());
    }

    /** 이름(한정·따옴표) → 표·형식 열쇠(마지막 조각 대문자) */
    private static String key(String qualified) {
        return DdlReader.last(DdlReader.nameParts(qualified)).toUpperCase(Locale.ROOT);
    }
}
