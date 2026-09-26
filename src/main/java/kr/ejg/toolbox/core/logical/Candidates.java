package kr.ejg.toolbox.core.logical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import kr.ejg.toolbox.core.dict.DictStore;
import kr.ejg.toolbox.core.dict.Dictionaries;
import kr.ejg.toolbox.core.text.Js;

/**
 * 산출물 05·06·07 후보 CSV — 순수본 {@code expTerms}·{@code expStdWords}·{@code expDomains}·{@code expWordUse} 를 옮겼다(3-6, 2.2 B —
 * 산출물과 변환기가 같은 코드). 모양도 순수본 그대로: 첫 줄 BOM + 머리줄(따옴표 없음), 값은 전부 큰따옴표, 줄바꿈 CRLF, 끝 줄바꿈 없음.
 */
public final class Candidates {

    private static final String BOM = String.valueOf((char) 0xFEFF);

    private Candidates() {
    }

    /** DB명 — 입력칸이 비면 스키마(owner)들을 / 로 */
    public static String dbName(String given, LogicalRun.Result r) {
        if (given != null && !Js.trim(given).isEmpty()) {
            return Js.trim(given);
        }
        Set<String> owners = new java.util.LinkedHashSet<>();
        r.rows().forEach(x -> {
            if (!x.owner().isEmpty()) {
                owners.add(x.owner());
            }
        });
        return String.join("/", owners);
    }

    /** 05 표준용어 후보 */
    public static String terms(LogicalRun.Result r, Dictionaries d, boolean orgFirst, String db, boolean excludeReview) {
        Converter conv = new Converter(d, orgFirst);
        List<String> out = new ArrayList<>();
        out.add("출처,DB명,표준용어명,영문약어명,용어설명,표준도메인명,출현횟수,검토필요");
        Map<String, Integer> tblFreq = new HashMap<>();
        r.tableRows().forEach(t -> tblFreq.merge(t.table().toUpperCase(Locale.ROOT), 1, Integer::sum));
        Map<String, Integer> colFreq = new HashMap<>();
        r.rows().forEach(c -> colFreq.merge(c.col().toUpperCase(Locale.ROOT), 1, Integer::sum));
        Set<String> seenT = new HashSet<>();
        for (LogicalRun.TableRow t : r.tableRows()) {
            String key = t.table().toUpperCase(Locale.ROOT);
            if (seenT.add(key)) {
                term(out, "테이블", db, t.name(), t.table(), "", tblFreq.get(key), flag(t.src(), t.missing()), excludeReview);
            }
        }
        Set<String> seen = new HashSet<>();
        for (LogicalRun.Row c : r.rows()) {
            String key = c.col().toUpperCase(Locale.ROOT);
            if (seen.add(key)) {
                term(out, "컬럼", db, c.name(), c.col(), domainOf(c.col(), d, conv), colFreq.get(key), flag(c.src(), c.missing()),
                        excludeReview);
            }
        }
        return BOM + String.join("\r\n", out);
    }

    private static void term(List<String> out, String kind, String db, String name, String phys, String domain, int freq,
            String flag, boolean excludeReview) {
        if (!flag.isEmpty() && excludeReview) {
            return;
        }
        out.add(q(kind, db, name, phys, "", domain, String.valueOf(freq), flag));
    }

    /** 순수본 termFlag — none 이면 미매칭, 못 찾은 토큰이 있으면 부분매칭 */
    static String flag(String src, List<String> missing) {
        return src.equals("none") ? "미매칭" : (!missing.isEmpty() ? "부분매칭" : "");
    }

    /** 순수본 domainOf — 마지막 토큰의 도메인(공통표준단어), 없으면 그 토큰 한글의 도메인 */
    static String domainOf(String phys, Dictionaries d, Converter conv) {
        List<String> toks = new ArrayList<>();
        for (String t : phys.toUpperCase(Locale.ROOT).split("_", -1)) {
            if (!t.isEmpty()) {
                toks.add(t);
            }
        }
        if (toks.isEmpty()) {
            return "";
        }
        String last = toks.get(toks.size() - 1);
        Dictionaries.WordMeta m = d.wordMeta().get(last);
        if (m != null && !m.domain().isEmpty()) {
            return m.domain();
        }
        Converter.Used u = conv.lookup(last);
        if (u != null) {
            Dictionaries.WordMeta k = d.domKor().get(u.kor());
            if (k != null && !k.domain().isEmpty()) {
                return k.domain();
            }
        }
        return "";
    }

    private static final Map<String, String> SRC_LABEL = Map.of("word", "공통표준단어", "given", "기관표준단어", "user", "사용자입력");

    /** 06 표준단어사전 — 이번 변환에 쓰인 약어(USEDTOK), 약어순 */
    public static String stdWords(LogicalRun.Result r, Dictionaries d, String db) {
        List<String> out = new ArrayList<>();
        out.add("DB명,표준단어명,영문약어명,형식단어여부,출처,중복");
        for (String a : new TreeSet<>(r.usedTokens().keySet())) {
            Converter.Used info = r.usedTokens().get(a);
            String fw = "";
            if (info.src().equals("word")) {
                Dictionaries.WordMeta m = d.wordMeta().get(a);
                fw = m == null ? "" : m.formWord();
            }
            if (fw.isEmpty()) {
                Dictionaries.WordMeta k = d.domKor().get(info.kor());
                fw = k != null && !k.formWord().isEmpty() ? k.formWord() : "N";
            }
            List<String> srcs = new ArrayList<>();
            if (d.word().containsKey(a)) {
                srcs.add("공통표준단어");
            }
            if (d.org().containsKey(a)) {
                srcs.add("기관표준단어");
            }
            if (d.user().containsKey(a)) {
                srcs.add("사용자입력");
            }
            out.add(q(db, info.kor(), a, fw, SRC_LABEL.getOrDefault(info.src(), info.src()), srcs.size() > 1 ? String.join("/", srcs) : ""));
        }
        return BOM + String.join("\r\n", out);
    }

    /** 07 표준도메인 후보 — 타입이 있는 컬럼만, (규격 일치·개념만 일치·없음) 키로 묶어 출현 수 */
    public static String domains(LogicalRun.Result r, DomainMatcher dm, String db) {
        record Resolved(LogicalRun.Row row, String word, DictStore.Domain spec, String key) {
        }
        List<Resolved> resolved = new ArrayList<>();
        for (LogicalRun.Row c : r.rows()) {
            if (c.dtype().isEmpty()) {
                continue;
            }
            String word = dm.word(c.name());
            List<DictStore.Domain> cands = word == null ? List.of() : dm.candidates(word);
            DictStore.Domain spec = cands.isEmpty() ? null : DomainMatcher.spec(cands, c.dtype(), c.dlen(), c.dscale());
            String dt = c.dtype().toUpperCase(Locale.ROOT);
            String key = spec != null ? "M|" + spec.cls() + "|" + spec.name()
                    : word != null ? "W|" + word + "|" + dt + "|" + c.dlen() + "|" + c.dscale()
                    : "U|" + dt + "|" + c.dlen() + "|" + c.dscale();
            resolved.add(new Resolved(c, word, spec, key));
        }
        Map<String, Integer> freq = new LinkedHashMap<>();
        resolved.forEach(x -> freq.merge(x.key(), 1, Integer::sum));
        List<String> out = new ArrayList<>();
        out.add("DB명,공통표준도메인그룹명,공통표준도메인분류명,공통표준도메인명,공통표준도메인설명,데이터타입,데이터길이,데이터소수점길이,"
                + "저장형식,표현형식,단위,허용값,출현횟수,검토필요");
        Set<String> seen = new HashSet<>();
        for (Resolved x : resolved) {
            if (!seen.add(x.key())) {
                continue;
            }
            LogicalRun.Row c = x.row();
            String cnt = String.valueOf(freq.get(x.key()));
            if (x.spec() != null) {
                DictStore.Domain s = x.spec();
                out.add(q(db, s.group(), s.cls(), s.name(), s.description(), s.dataType(), s.length(), s.scale(), s.storeFormat(),
                        s.dispFormat(), s.unit(), s.allowed(), cnt, ""));
                continue;
            }
            String review = x.word() != null
                    ? "«" + x.word() + "» 계열로 보이나 규격이 행안부 정의와 다름 — 도메인 위반 후보"
                    : "행안부 도메인에 매칭되는 개념어 없음 — 사내 전용이거나 미등록 약어";
            DomainMatcher.Fmt f = DomainMatcher.fmt(c.dtype(), c.dlen(), c.dscale());
            String domName = f != null ? f.name() : c.dtype() + (c.dlen().isEmpty() ? "" : "(" + c.dlen() + ")");
            out.add(q(db, "", "", domName, "", c.dtype(), c.dlen(), c.dscale(), f == null ? "" : f.store(), f == null ? "" : f.disp(),
                    "", "", cnt, review));
        }
        return BOM + String.join("\r\n", out);
    }

    /** 공통표준단어 원본에 「사용여부」 열 — 이번 변환에서 공통표준단어로 쓰인 약어(USEDWORD)면 Y */
    public static String wordUse(List<List<String>> moiRows, int abbrColumn, LogicalRun.Result r) {
        List<String> out = new ArrayList<>();
        List<String> head = new ArrayList<>(moiRows.get(0));
        head.add("사용여부");
        out.add(q(head.toArray(new String[0])));
        for (int i = 1; i < moiRows.size(); i++) {
            List<String> row = new ArrayList<>(moiRows.get(i));
            String ab = abbrColumn < row.size() ? Js.trim(row.get(abbrColumn)).toUpperCase(Locale.ROOT) : "";
            row.add(!ab.isEmpty() && r.usedWords().contains(ab) ? "Y" : "N");
            out.add(q(row.toArray(new String[0])));
        }
        return BOM + String.join("\r\n", out);
    }

    /** 순수본 q — 전부 큰따옴표, 안의 따옴표는 두 번 */
    static String q(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(cells[i] == null ? "" : cells[i].replace("\"", "\"\"")).append('"');
        }
        return sb.toString();
    }
}
