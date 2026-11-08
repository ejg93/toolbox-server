package kr.ejg.toolbox.core.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    /** 1-72 — PG LIKE 의 INDEXES|ALL 절. 뒤 절이 앞 절을 덮는다 */
    private static final Pattern LIKE_PK = Pattern.compile("(?i)\\b(INCLUDING|EXCLUDING)\\s+(INDEXES|ALL)\\b");
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
    /** 1-80 — FROM 의 원본 사이 — 쉼표 또는 [NATURAL] [INNER|LEFT|RIGHT|FULL|CROSS] [OUTER] JOIN */
    private static final Pattern JOIN_SEP = Pattern.compile("(?i)\\s*,\\s*|\\s+(?:NATURAL\\s+)?(?:(?:INNER|LEFT|RIGHT|FULL|CROSS)\\s+)?(?:OUTER\\s+)?JOIN\\s+");
    /** 1-81 — 공통 컬럼을 하나로 합치는 조인 — {@code *}·{@code t.*} 는 추측하지 않는다 */
    private static final Pattern MERGE_JOIN = Pattern.compile("(?i)\\bNATURAL\\s+(?:\\w+\\s+)*JOIN\\b|\\bUSING\\s*\\(");
    /** 원본 하나 — 표 [[AS] 별칭] [ON …|USING (…)] */
    private static final Pattern FROM_SOURCE = Pattern.compile("(?i)^([^\\s,()]+)(?:\\s+(?:AS\\s+)?(?!ON\\b|USING\\b)([^\\s,()]+))?(?:\\s+(?:ON|USING)\\b.*)?$", Pattern.DOTALL);
    /** CTAS_END 낱말 — FROM 뒤 어디서든 첫 것에서 자른다 */
    private static final Pattern CTAS_END_ANY = Pattern.compile("(?i)\\b(?:WHERE|ORDER|GROUP|HAVING|LIMIT|FETCH|OFFSET)\\b");
    /** SELECT 목록 항목 — [한정.]이름 [[AS] 별칭] — g1 한정자, g2 이름, g3 별칭 */
    private static final Pattern CTAS_ITEM = Pattern.compile("^(?:([^\\s.]+)\\.)?([^\\s.]+)(?:\\s+(?:AS\\s+)?(\\S+))?$", Pattern.CASE_INSENSITIVE);
    /** 1-80 — 항목 {@code t.*} — g1 한정자 */
    private static final Pattern STAR_OF = Pattern.compile("^([^\\s.]+)\\.\\*$");
    /** 식·함수·따옴표 글이 든 항목 — 컬럼을 추측하지 않는다 */
    private static final Pattern ITEM_BAD = Pattern.compile("[('\"+\\-*/|<>=]");
    private static final String PARTITION = "partition";
    private static final String STAR = "*";
    private static final char QUOTE = '\'';
    private static final char OPEN = '(';
    private static final char CLOSE = ')';

    private DdlPull() {
    }

    /** 1-78 — 홑따옴표 글 안을 공백으로 비운 같은 길이 사본. 자리가 원문과 같아 사본에서 찾고 원문에서 자른다({@code ''} 는 빈 글 둘 — matching 과 같은 토글) */
    static String mask(String s) {
        char[] a = s.toCharArray();
        boolean q = false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] == QUOTE) {
                q = !q;
            } else if (q) {
                a[i] = ' ';
            }
        }
        return new String(a);
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

    /** 괄호 안 {@code LIKE x [INCLUDING …]} — 원본이 앞에 있으면 그 자리에 컬럼, PK 는 INCLUDING INDEXES|ALL 일 때만(1-72) */
    static boolean like(Draft d, String item) {
        Matcher m = LIKE_SRC.matcher(item);
        return m.lookingAt() && copy(d, d.scope.get(key(m.group(1))), likePk(item));
    }

    /** 1-72 — 마지막 INCLUDING|EXCLUDING INDEXES|ALL 절이 INCLUDING 이면 참, 절이 없으면 거짓(PG 기본은 인덱스·제약을 안 옮긴다) */
    static boolean likePk(String item) {
        Matcher m = LIKE_PK.matcher(item);
        boolean including = false;
        while (m.find()) {
            including = "INCLUDING".equalsIgnoreCase(m.group(1));
        }
        return including;
    }

    private static Draft source(Pattern p, String after, Map<String, Draft> tables) {
        Matcher m = p.matcher(after);
        return m.lookingAt() ? tables.get(key(m.group(1))) : null;
    }

    /** 원본 컬럼(순번 다시)과 PK(앞 PK 를 비우고 — 뒤 PK 가 이긴다, 1-72). 원본이 없거나 컬럼이 없으면 거짓 — 이름만 읽은 원본은 끌어올 것이 없다 */
    private static boolean copy(Draft d, Draft src, boolean withPk) {
        if (src == null || src.cols.isEmpty()) {
            return false;
        }
        for (Column c : src.cols) {
            d.cols.add(at(c, d.cols.size() + 1, c.name()));
        }
        if (withPk && !src.pk.isEmpty()) {
            d.pk.clear();
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
            } else if (o.matches() && TYPED_WORDS.matcher(mask(o.group(2))).find()) {
                typedOption(d, DdlReader.unquote(o.group(1)), o.group(2));
            } else {
                unreadable.add(new Unreadable(d.name, item));
            }
        }
    }

    /**
     * 1-78 — 매처는 전부 따옴표 글을 비운 같은 길이 사본({@link #mask})에서, CHECK 식 본문(1-74)·DEFAULT 값(1-79)은 원문 자리에서 자른다
     * — 컬럼 가지 {@link DdlReader#item} 과 같은 규칙. 1-79 — DEFAULT 값을 받고 PK 는 NOT NULL.
     */
    private static void typedOption(Draft d, String name, String rest) {
        String bare = mask(rest);
        Matcher ip = DdlReader.INLINE_PK.matcher(bare);
        boolean pk = ip.find();
        if (pk) {
            d.pk.add(name);
            d.pkName = ip.group(1) == null ? d.pkName : DdlReader.unquote(ip.group(1));
        }
        Matcher df = DdlReader.DEFAULT.matcher(bare);
        String def = df.find() ? rest.substring(df.start(1), df.end(1)) : null;
        restate(d, name, pk || NOT_NULL.matcher(bare).find(), def);
        if (UNIQUE.matcher(bare).find()) {
            d.uqs.add(new UniqueKey(null, List.of(name)));
        }
        Matcher ref = DdlReader.COL_REF.matcher(bare);
        if (ref.find()) {
            d.fks.add(DdlReader.fk(d, null, name, ref.group(1), ref.group(2)));
        }
        DdlReader.checks(d, rest);
    }

    /** 1-79 — 이름이 같은 컬럼을 NOT NULL·DEFAULT 로 다시 놓는다. 둘 다 없으면 그대로 */
    private static void restate(Draft d, String name, boolean notNull, String def) {
        if (!notNull && def == null) {
            return;
        }
        for (int i = 0; i < d.cols.size(); i++) {
            Column c = d.cols.get(i);
            if (c.name().equalsIgnoreCase(name)) {
                d.cols.set(i, new Column(c.name(), c.ordinal(), c.nativeType(), c.jdbcType(), c.length(), c.precision(), c.scale(),
                        c.nullable() && !notNull, def == null ? c.defaultValue() : def, c.comment(), c.domain()));
            }
        }
    }

    /**
     * CTAS 컬럼 — 따옴표 글을 비운 뒤 {@code [(머리 이름 …)] [AS] [(] SELECT 목록 FROM 표 [별칭] (, 표 [별칭] | [LEFT|…] JOIN 표 [별칭] ON …)*
     * [WHERE|ORDER|GROUP|HAVING|LIMIT|FETCH|OFFSET …]} 만(1-80).
     * 항목은 {@code *}·{@code t.*}·한정 이름·유일한 맨 이름·별칭. 식·함수·서브쿼리·없는 원본·모호한 이름이면 빈 목록(추측하지 않는다)
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
        Map<String, Draft> src = fromSources(s.substring(from.end()).trim(), tables);
        boolean merged = MERGE_JOIN.matcher(s.substring(from.end())).find(); // 1-81
        List<Column> cols = src.isEmpty() ? List.of() : selected(s.substring(sel.end(), from.start()).trim(), src, merged);
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

    /** 1-80 — FROM 뒤 원본들. 서브쿼리가 있거나 원본 하나라도 DDL 에 없거나 꼴이 다르면 빈 Map(추측하지 않는다) */
    private static Map<String, Draft> fromSources(String tail, Map<String, Draft> tables) {
        Matcher end = CTAS_END_ANY.matcher(tail);
        String list = (end.find() ? tail.substring(0, end.start()) : tail).trim();
        if (SELECT_ANY.matcher(tail).find() || list.isEmpty()) {
            return Map.of();
        }
        Map<String, Draft> out = new LinkedHashMap<>();
        Matcher sep = JOIN_SEP.matcher(depthMask(list)); // 1-81 — 괄호 깊이 0 의 구분자만, 자르기는 원문에서
        int from = 0;
        while (sep.find()) {
            if (!source(list.substring(from, sep.start()).trim(), tables, out)) {
                return Map.of();
            }
            from = sep.end();
        }
        return source(list.substring(from).trim(), tables, out) ? out : Map.of();
    }

    /** 1-81 — 괄호 안을 공백으로 비운 같은 길이 사본(따옴표 글은 ctasColumns 가 이미 비웠다). 깊이 0 의 구분자만 찾으려고 */
    static String depthMask(String s) {
        char[] a = s.toCharArray();
        int depth = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] == OPEN) {
                depth++;
            } else if (a[i] == CLOSE) {
                depth = Math.max(0, depth - 1);
            } else if (depth > 0) {
                a[i] = ' ';
            }
        }
        return new String(a);
    }

    /** 원본 하나 — 표 이름으로 Draft, 키는 별칭(없으면 표 이름). 없으면 false */
    private static boolean source(String piece, Map<String, Draft> tables, Map<String, Draft> out) {
        Matcher s = FROM_SOURCE.matcher(piece);
        Draft d = s.matches() ? tables.get(key(s.group(1))) : null;
        if (d == null) {
            return false;
        }
        out.put(s.group(2) == null ? key(s.group(1)) : DdlReader.unquote(s.group(2)).toUpperCase(Locale.ROOT), d);
        return true;
    }

    /** SELECT 목록 → 원본 컬럼(별칭이면 이름만 바꿈). 못 찾거나 식·모호하면 빈 목록. merged(NATURAL·USING)면 *·t.* 도 빈 목록(1-81) */
    private static List<Column> selected(String list, Map<String, Draft> srcs, boolean merged) {
        if (merged && (STAR.equals(list) || STAR_OF.matcher(list.trim()).matches())) {
            return List.of();
        }
        if (STAR.equals(list)) {
            List<Column> all = new ArrayList<>();
            srcs.values().forEach(d -> all.addAll(d.cols));
            return all;
        }
        List<Column> out = new ArrayList<>();
        for (String it : DdlReader.splitTop(list, ',')) {
            String item = it.trim();
            Matcher star = STAR_OF.matcher(item);
            Draft of = star.matches() ? srcs.get(DdlReader.unquote(star.group(1)).toUpperCase(Locale.ROOT)) : null;
            if (of != null) {
                if (merged) {
                    return List.of();
                }
                out.addAll(of.cols);
                continue;
            }
            Matcher m = CTAS_ITEM.matcher(item);
            Column c = ITEM_BAD.matcher(item).find() || !m.matches() ? null : resolve(srcs, m.group(1), DdlReader.unquote(m.group(2)));
            if (c == null) {
                return List.of();
            }
            out.add(m.group(3) == null ? c : at(c, c.ordinal(), DdlReader.unquote(m.group(3))));
        }
        return out;
    }

    /** 한정자가 있으면 그 원본에서, 없으면 모든 원본에서 꼭 하나일 때만. 아니면 null */
    private static Column resolve(Map<String, Draft> srcs, String qualifier, String name) {
        if (qualifier != null) {
            Draft d = srcs.get(DdlReader.unquote(qualifier).toUpperCase(Locale.ROOT));
            return d == null ? null : column(d, name);
        }
        Column found = null;
        for (Draft d : srcs.values()) {
            Column c = column(d, name);
            if (c != null && found != null) {
                return null;
            }
            found = c == null ? found : c;
        }
        return found;
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
