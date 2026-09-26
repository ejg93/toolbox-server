package kr.ejg.toolbox.core.logical;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kr.ejg.toolbox.core.dict.DictStore;
import kr.ejg.toolbox.core.dict.Dictionaries;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.text.Js;

/**
 * 표준 미준수 전수 리포트(3-7, 2.2 C) — 순수본에 없는 기능. 스냅샷의 테이블·컬럼마다 규칙 다섯을 본다.
 * 재료는 변환 결과(3-2 src·missing)·도메인 규격(3-6)·스냅샷 코멘트. 결과는 H2 에 저장하지 않는다(5-6 은 코드 검사만).
 */
public final class Audit {

    /** 규칙 — 순서가 리포트 안의 순서다 */
    public enum Rule {
        /** 코멘트 없음 */
        NO_COMMENT,
        /** 코멘트 ≠ 조립명(공백 제거 비교, 토큰을 전부 찾은 이름만). 기본 꺼짐 — 현장은 다른 것이 정상인 곳이 많다(2026-09-27 사용자) */
        COMMENT_MISMATCH,
        /** 사전에서 못 찾은 토큰이 있다 */
        UNMATCHED_TOKEN,
        /** 토큰이 기관·사용자 사전에만 있고 공통표준단어에 없다 */
        NON_STANDARD_ABBR,
        /** 도메인 개념어는 맞는데 규격(타입·길이·소수점)이 행안부 정의와 다르다 — 3-6 「W|」 */
        DOMAIN_SPEC
    }

    public static final Set<Rule> DEFAULT_RULES = Set.copyOf(EnumSet.complementOf(EnumSet.of(Rule.COMMENT_MISMATCH)));

    /** 한 건. column 이 null 이면 테이블 단위 */
    public record Finding(String schema, String table, String column, Rule rule, String detail) {
    }

    private Audit() {
    }

    public static List<Finding> run(List<Schema> schemas, Dictionaries dicts, List<DictStore.Domain> domains,
            List<String> skipTokens, boolean orgFirst, Set<Rule> rules) {
        LogicalRun.Result r = LogicalRun.run(ColumnInputs.fromSchemas(schemas), dicts, skipTokens, orgFirst);
        Map<String, LogicalRun.TableRow> tabs = new HashMap<>();
        r.tableRows().forEach(t -> tabs.put(key(t.owner(), t.table(), ""), t));
        Map<String, LogicalRun.Row> cols = new HashMap<>();
        r.rows().forEach(c -> cols.put(key(c.owner(), c.table(), c.col()), c));
        Set<String> skip = new java.util.HashSet<>();
        skipTokens.forEach(s -> skip.add(Js.trim(s).toUpperCase(Locale.ROOT)));
        DomainMatcher dm = new DomainMatcher(domains);

        List<Finding> out = new ArrayList<>();
        for (Schema s : schemas) {
            for (Table t : s.tables()) {
                LogicalRun.TableRow tr = tabs.get(key(t.schema(), t.name(), ""));
                if (tr != null) {
                    common(out, rules, t.schema(), t.name(), null, t.comment(), tr.name(), tr.src(), tr.missing(), t.name(), skip,
                            dicts);
                }
                for (Column c : t.columns()) {
                    LogicalRun.Row cr = cols.get(key(t.schema(), t.name(), c.name()));
                    if (cr == null) {
                        continue;
                    }
                    common(out, rules, t.schema(), t.name(), c.name(), c.comment(), cr.name(), cr.src(), cr.missing(), c.name(),
                            Set.of(), dicts);
                    if (rules.contains(Rule.DOMAIN_SPEC)) {
                        domainSpec(out, t, c, cr, dm);
                    }
                }
            }
        }
        return out;
    }

    private static void common(List<Finding> out, Set<Rule> rules, String schema, String table, String column, String comment,
            String name, String src, List<String> missing, String phys, Set<String> skip, Dictionaries d) {
        boolean noComment = comment == null || Js.trim(comment).isEmpty();
        if (rules.contains(Rule.NO_COMMENT) && noComment) {
            out.add(new Finding(schema, table, column, Rule.NO_COMMENT, ""));
        }
        // 덜 조립된 이름(「발주ITEMS」)과 견주면 잡음이다 — 못 찾은 토큰은 UNMATCHED_TOKEN 이 잡는다
        if (rules.contains(Rule.COMMENT_MISMATCH) && !noComment && !src.equals("none") && missing.isEmpty() && !name.isEmpty()
                && !Js.removeSpaces(Js.trim(comment)).equals(Js.removeSpaces(name))) {
            out.add(new Finding(schema, table, column, Rule.COMMENT_MISMATCH, "코멘트 「" + Js.trim(comment) + "」 / 조립 「" + name + "」"));
        }
        if (rules.contains(Rule.UNMATCHED_TOKEN) && !missing.isEmpty()) {
            out.add(new Finding(schema, table, column, Rule.UNMATCHED_TOKEN, String.join(",", missing)));
        }
        if (rules.contains(Rule.NON_STANDARD_ABBR)) {
            List<String> non = new ArrayList<>();
            for (String tok : phys.toUpperCase(Locale.ROOT).split("_", -1)) {
                if (!tok.isEmpty() && !skip.contains(tok) && !d.word().containsKey(tok)
                        && (d.org().containsKey(tok) || d.user().containsKey(tok)) && !non.contains(tok)) {
                    non.add(tok);
                }
            }
            if (!non.isEmpty()) {
                out.add(new Finding(schema, table, column, Rule.NON_STANDARD_ABBR, String.join(",", non)));
            }
        }
    }

    /** 3-6 domains 의 「W|」 판정과 같은 규칙 — 개념어는 찾았는데 규격 일치 후보가 없다 */
    private static void domainSpec(List<Finding> out, Table t, Column c, LogicalRun.Row cr, DomainMatcher dm) {
        if (cr.dtype().isEmpty()) {
            return;
        }
        String word = dm.word(cr.name());
        if (word == null || DomainMatcher.spec(dm.candidates(word), cr.dtype(), cr.dlen(), cr.dscale()) != null) {
            return;
        }
        String spec = cr.dtype() + (cr.dlen().isEmpty() ? "" : "(" + cr.dlen() + (cr.dscale().isEmpty() ? "" : "," + cr.dscale()) + ")");
        java.util.Set<String> allowed = new java.util.LinkedHashSet<>();
        dm.candidates(word).forEach(x -> allowed.add(x.name()));
        out.add(new Finding(t.schema(), t.name(), c.name(), Rule.DOMAIN_SPEC,
                "«" + word + "» 계열인데 " + spec + " — 행안부 " + String.join("·", allowed)));
    }

    private static String key(String owner, String table, String col) {
        return (Js.trim(owner == null ? "" : owner) + "|" + Js.trim(table) + "|" + Js.removeSpaces(Js.trim(col))).toUpperCase(Locale.ROOT);
    }
}
