package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 코드 정리(5-34, 설계 30) — 코드 검사 지적 가운데 A 일곱을 글→글 함수로 고친다. 단계 차례 고정(① 삭제 → ② 표식 → ③ 선언 순서 → ④ 줄바꿈),
 * 단계마다 파일을 다시 검사해 (규칙, 발췌) 다중집합을 견주고 나빠지면 그 단계만 되돌린다. 파일을 쓰지 않는다(쓰기는 {@link CleanupRunner}).
 * 건너뜀·되돌림 사유는 사람이 읽는 한 줄이고 코드 본문을 싣지 않는다(절대 규칙 3).
 */
public final class Cleanup {

    public static final List<String> RULES = List.of("common.debugger", "tsx.debugger", "java.unusedImport", "tsx.unusedImport",
            "common.unusedIgnore", "java.declarationOrder", "file.lineEnding");
    public static final String DELETE = "delete";
    public static final String MARKER = "marker";
    public static final String ORDER = "order";
    public static final String EOL = "eol";

    /** JSX 팩터리 — 검사기는 JSX 를 몰라 안 쓰인 것으로 본다. 지우면 컴파일이 깨진다 */
    static final Set<String> KEEP_JS = Set.of("React", "h", "Fragment", "jsx", "jsxs");
    /** 파일 하나 재검사로는 표식이 쓰이는지 모르는 규칙 — 짝 파일이 폴더에 있다 */
    static final Set<String> CROSS_FILE = Set.of("java.dupMapping", "mybatis.namespace");

    private static final String UNUSED_IMPORT_JAVA = "java.unusedImport";
    private static final String UNUSED_IMPORT_TS = "tsx.unusedImport";
    private static final String LINE_ENDING = "file.lineEnding";
    private static final Set<String> DEBUGGERS = Set.of("common.debugger", "tsx.debugger");
    private static final Set<String> SEMI_OPTIONAL = Set.of("js", "ts", "mjs", "cjs");
    private static final Pattern DEBUGGER_WORD = Pattern.compile("\\bdebugger\\b");
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u+[0-9A-Fa-f]{4}");
    private static final Pattern ONE_LINE_TAIL = Pattern.compile("^\\s+from\\s*(['\"])[^'\"]*\\1\\s*;?\\s*(//.*)?$");
    private static final Pattern WORDS = Pattern.compile("[^\\w$]+");
    private static final Pattern MODULE = Pattern.compile("from\\s*(['\"])([^'\"]*)\\1");
    /** TS 가 아닌 JS — 쓰지 않는 import 도 모듈은 실행된다 */
    private static final Set<String> JS_ONLY = Set.of("js", "jsx", "mjs", "cjs");
    private static final Set<String> C_LIKE = Set.of("java", "js", "jsx", "ts", "tsx", "mjs", "cjs", "css", "scss", "kt", "groovy");
    private static final Set<String> MARKUP = Set.of("xml", "html", "htm", "xhtml", "vue");
    private static final Set<String> JSP = Set.of("jsp", "jspf", "tag");
    private static final Set<String> HASH_LINE = Set.of("properties", "yml", "yaml", "sh");
    private static final Map<String, String> CLOSER = Map.of("/*", "*/", "<!--", "-->", "<%--", "--%>");
    /** 주석 글에 걸리는 규칙 — ② 가 표식 주석을 지우면 같이 사라져도 된다 */
    private static final Set<String> COMMENT_RULES = Set.of("common.todo", "tsx.eslintDisable");
    /** 중괄호 없는 if·else·for·while·do 몸체 — 그 다음 줄 debugger 를 지우면 다음 문장이 몸체가 된다 */
    private static final Pattern BRACELESS = Pattern.compile("(?:\\)|\\belse|\\bdo)\\s*$");

    private final RuleSet rules;
    private final Set<String> wanted;
    private final Set<String> enabledIds;
    private final String lineEnding;
    private final JavaSource parser = new JavaSource();

    /**
     * @param wanted     고른 규칙 — {@link #RULES} 와 켜진 규칙만 남긴다
     * @param lineEnding 프로필 {@code project.lineEnding}(없으면 null — ④ 는 건너뛴다)
     */
    public Cleanup(RuleSet rules, Collection<String> wanted, String lineEnding) {
        this.rules = rules;
        Set<String> on = new LinkedHashSet<>();
        rules.defs().stream().filter(Rule.Def::on).forEach(d -> on.add(d.id()));
        this.enabledIds = on;
        Set<String> w = new LinkedHashSet<>();
        for (String id : RULES) {
            if (wanted.contains(id) && on.contains(id)) {
                w.add(id);
            }
        }
        this.wanted = w;
        this.lineEnding = lineEnding == null || lineEnding.isBlank() ? null : lineEnding.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** 적용한 단계 — 대상 규칙의 건수 전·후 */
    public record Step(String stage, String rule, int before, int after) {
    }

    /** 건너뜀·되돌림 — 사유는 사람이 읽는 한 줄(코드 본문 없음) */
    public record Skip(String stage, String reason) {
    }

    /** 고친 글(\n)·쓸 줄바꿈·바뀌었나·처음과 끝 지적·단계·건너뜀 */
    public record Result(String text, String lineEnding, boolean changed, List<Finding> before, List<Finding> after, List<Step> steps,
            List<Skip> skips) {
        public Result {
            before = List.copyOf(before);
            after = List.copyOf(after);
            steps = List.copyOf(steps);
            skips = List.copyOf(skips);
        }
    }

    public Set<String> wanted() {
        return java.util.Collections.unmodifiableSet(wanted);
    }

    /** 파일 하나 — s.text 는 \n 정규화, s.lineEnding 은 파일에서 읽은 값 */
    public Result clean(Source s) {
        State st = new State(s, check(rules, s));
        List<Finding> start = st.cur;
        deleteStage(st);
        markerStage(st);
        orderStage(st);
        eolStage(st);
        boolean changed = !st.text.equals(s.text()) || !Objects.equals(st.le, s.lineEnding());
        return new Result(st.text, st.le, changed, start, st.cur, st.steps, st.skips);
    }

    /** 단계 사이에 들고 다니는 것 */
    private static final class State {
        private final Source src;
        private final List<Step> steps = new ArrayList<>();
        private final List<Skip> skips = new ArrayList<>();
        private String text;
        private String le;
        private List<Finding> cur;

        State(Source src, List<Finding> cur) {
            this.src = src;
            this.text = src.text();
            this.le = src.lineEnding();
            this.cur = cur;
        }

        Source source() {
            return source(text, le);
        }

        Source source(String t, String lineEnd) {
            return new Source(src.rel(), t, src.encoding(), lineEnd, src.lang());
        }
    }

    // ---------------------------------------------------------------- 단계

    private void deleteStage(State st) {
        Set<String> targets = targets(st.cur, Set.of("common.debugger", "tsx.debugger", UNUSED_IMPORT_JAVA, UNUSED_IMPORT_TS));
        if (targets.isEmpty()) {
            return;
        }
        Edits e = new Edits(st.text);
        List<Skip> local = new ArrayList<>();
        Source s = st.source();
        Set<String> dbgRules = new TreeSet<>(targets);
        dbgRules.retainAll(DEBUGGERS);
        List<Integer> dbg = lines(st.cur, dbgRules);
        if (!dbg.isEmpty()) {
            deleteDebugger(s, dbg, e, local);
        }
        List<Integer> javaLines = targets.contains(UNUSED_IMPORT_JAVA) ? lines(st.cur, Set.of(UNUSED_IMPORT_JAVA)) : List.of();
        if (!javaLines.isEmpty()) {
            String unsafe = javaUnsafe(st.text, st.cur);
            if (unsafe != null) {
                local.add(new Skip(DELETE, unsafe));
            } else {
                deleteJavaImports(st.text, javaLines, parser, e, local);
            }
        }
        List<Integer> tsLines = targets.contains(UNUSED_IMPORT_TS) ? lines(st.cur, Set.of(UNUSED_IMPORT_TS)) : List.of();
        if (!tsLines.isEmpty()) {
            fixTsImports(s, tsLines, e, local);
        }
        commit(st, DELETE, targets, e, local);
    }

    private void markerStage(State st) {
        Set<String> targets = targets(st.cur, Set.of(Ignores.UNUSED));
        if (targets.isEmpty()) {
            return;
        }
        Edits e = new Edits(st.text);
        List<Skip> local = new ArrayList<>();
        removeMarkers(st.source(), lines(st.cur, targets), enabledIds, e, local);
        commit(st, MARKER, targets, e, local);
    }

    /** ③ 선언 순서 — OrderFix(줄 슬롯 재조립, 안전한 몸체만). 줄 수·토큰 불변식은 OrderFix 가, 지적 비교는 여기서 */
    private void orderStage(State st) {
        Set<String> targets = targets(st.cur, Set.of(JavaRules.ORDER));
        if (targets.isEmpty()) {
            return;
        }
        String unsafe = javaUnsafe(st.text, st.cur);
        if (unsafe != null) {
            st.skips.add(new Skip(ORDER, unsafe));
            return;
        }
        Rule.Def def = rules.defs().stream().filter(d -> d.id().equals(JavaRules.ORDER)).findFirst().orElseThrow();
        boolean mods = !Boolean.TRUE.equals(def.params().get("ignoreModifiers"));
        List<Skip> local = new ArrayList<>();
        String next = OrderFix.apply(st.text, mods, def, parser, st.src.rel(), local);
        if (next.equals(st.text)) {
            st.skips.addAll(local);
            return;
        }
        List<Finding> after = check(rules, st.source(next, st.le));
        String why = worse(ORDER, targets, st.cur, after, Set.of(), Set.of());
        if (why != null) {
            st.skips.add(new Skip(ORDER, why));
            st.skips.addAll(local);
            return;
        }
        addSteps(st, ORDER, targets, after);
        st.skips.addAll(local);
        st.text = next;
        st.cur = after;
    }

    private void eolStage(State st) {
        Set<String> targets = targets(st.cur, Set.of(LINE_ENDING));
        if (targets.isEmpty()) {
            return;
        }
        if (lineEnding == null) {
            st.skips.add(new Skip(EOL, "프로필 project.lineEnding 없음"));
            return;
        }
        List<Finding> after = check(rules, st.source(st.text, lineEnding));
        String why = worse(EOL, targets, st.cur, after, Set.of(), Set.of());
        if (why != null) {
            st.skips.add(new Skip(EOL, why));
            return;
        }
        addSteps(st, EOL, targets, after);
        st.le = lineEnding;
        st.cur = after;
    }

    /** 단계 결과를 다시 검사해 채택하거나 되돌린다. 고친 줄이 없으면 사유만 */
    private void commit(State st, String stage, Set<String> targets, Edits e, List<Skip> local) {
        if (e.isEmpty()) {
            st.skips.addAll(local);
            return;
        }
        String next = e.result();
        List<Finding> after = check(rules, st.source(next, st.le));
        String why = worse(stage, targets, st.cur, after, e.touchedOld(), e.touchedNew());
        if (why != null) {
            st.skips.add(new Skip(stage, why));
            st.skips.addAll(local);
            return;
        }
        addSteps(st, stage, targets, after);
        st.skips.addAll(local);
        st.text = next;
        st.cur = after;
    }

    private static void addSteps(State st, String stage, Set<String> targets, List<Finding> after) {
        for (String t : targets) {
            st.steps.add(new Step(stage, t, count(st.cur, t), count(after, t)));
        }
    }

    private Set<String> targets(List<Finding> cur, Set<String> ids) {
        Set<String> out = new TreeSet<>();
        for (Finding f : cur) {
            if (ids.contains(f.rule()) && wanted.contains(f.rule())) {
                out.add(f.rule());
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 재검사·비교

    /** 파일 하나를 처음부터 — apply 뒤 finish(쓸모없는 표식은 finish 에서만 나온다) */
    static List<Finding> check(RuleSet rules, Source s) {
        RuleSet.Run r = rules.run();
        List<Finding> out = new ArrayList<>(r.apply(s));
        out.addAll(r.finish());
        return out;
    }

    private static int count(List<Finding> fs, String rule) {
        int n = 0;
        for (Finding f : fs) {
            if (f.rule().equals(rule)) {
                n++;
            }
        }
        return n;
    }

    private static List<Integer> lines(List<Finding> fs, Set<String> ruleIds) {
        Set<Integer> out = new TreeSet<>();
        for (Finding f : fs) {
            if (ruleIds.contains(f.rule())) {
                out.add(f.line());
            }
        }
        return new ArrayList<>(out);
    }

    private static boolean fileRule(String rule) {
        return rule.startsWith("file.");
    }

    /**
     * 되돌림 판정(설계 30 §3 표) — null 이면 채택, 아니면 사유. 비교는 (규칙, 발췌) 다중집합 — 발췌는 줄 글이라 줄이 밀려도 따라간다.
     * {@code file.*} 는 발췌가 건수 글이라 있음·없음만. 고친 줄(touched) 위의 지적은 규칙 이름으로만 따로 본다
     */
    static String worse(String stage, Set<String> targets, List<Finding> before, List<Finding> after, Set<Integer> touchedOld,
            Set<Integer> touchedNew) {
        int pb = 0;
        int pa = 0;
        for (Finding f : before) {
            pb += f.rule().endsWith(".parseError") ? 1 : 0;
        }
        for (Finding f : after) {
            pa += f.rule().endsWith(".parseError") ? 1 : 0;
        }
        if (pa > pb) {
            return "구문이 깨짐";
        }
        int tb = 0;
        int ta = 0;
        for (String t : targets) {
            tb += count(before, t);
            ta += count(after, t);
        }
        if (ta >= tb) {
            return "효과 없음";
        }
        Set<String> mayAppear = DELETE.equals(stage) ? Set.of(Ignores.UNUSED) : Set.of();
        Set<String> mayVanish = DELETE.equals(stage) || MARKER.equals(stage) ? Set.of("file.mixedIndent", "file.header") : Set.of();
        boolean touchedMayVanish = MARKER.equals(stage);
        // file.* — 있음·없음
        Set<String> fb = new TreeSet<>();
        Set<String> fa = new TreeSet<>();
        before.stream().filter(f -> fileRule(f.rule()) && !targets.contains(f.rule())).forEach(f -> fb.add(f.rule()));
        after.stream().filter(f -> fileRule(f.rule()) && !targets.contains(f.rule())).forEach(f -> fa.add(f.rule()));
        for (String r : fa) {
            if (!fb.contains(r)) {
                return "다른 지적이 생김: " + r;
            }
        }
        for (String r : fb) {
            if (!fa.contains(r) && !mayVanish.contains(r)) {
                return "다른 지적이 사라짐: " + r;
            }
        }
        // 고친 줄 위 — 규칙 이름 다중집합
        Map<String, Integer> tob = new TreeMap<>();
        Map<String, Integer> toa = new TreeMap<>();
        Map<String, Integer> kb = new TreeMap<>();
        Map<String, Integer> ka = new TreeMap<>();
        for (Finding f : before) {
            if (skipInCompare(f, targets)) {
                continue;
            }
            if (touchedOld.contains(f.line())) {
                tob.merge(f.rule(), 1, Integer::sum);
            } else {
                kb.merge(f.rule() + "\u0000" + f.excerpt(), 1, Integer::sum);
            }
        }
        for (Finding f : after) {
            if (skipInCompare(f, targets) || mayAppear.contains(f.rule())) {
                continue;
            }
            if (touchedNew.contains(f.line())) {
                toa.merge(f.rule(), 1, Integer::sum);
            } else {
                ka.merge(f.rule() + "\u0000" + f.excerpt(), 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> x : toa.entrySet()) {
            if (x.getValue() > tob.getOrDefault(x.getKey(), 0)) {
                return "다른 지적이 생김: " + x.getKey();
            }
        }
        for (Map.Entry<String, Integer> x : tob.entrySet()) {
            // ② 는 지운 주석 안 지적만 사라져도 된다(설계 30 §3 표) — 코드에 걸리는 규칙이 사라지면 코드를 자른 것
            if (x.getValue() > toa.getOrDefault(x.getKey(), 0) && !(touchedMayVanish && COMMENT_RULES.contains(x.getKey()))) {
                return "다른 지적이 사라짐: " + x.getKey();
            }
        }
        for (Map.Entry<String, Integer> x : ka.entrySet()) {
            if (x.getValue() > kb.getOrDefault(x.getKey(), 0)) {
                return "다른 지적이 생김: " + x.getKey().substring(0, x.getKey().indexOf('\u0000'));
            }
        }
        for (Map.Entry<String, Integer> x : kb.entrySet()) {
            if (x.getValue() > ka.getOrDefault(x.getKey(), 0)) {
                return "다른 지적이 사라짐: " + x.getKey().substring(0, x.getKey().indexOf('\u0000'));
            }
        }
        return null;
    }

    private static boolean skipInCompare(Finding f, Set<String> targets) {
        // 5-35 — 크기·복잡도는 줄을 지우면 값(발췌)이 바뀌는 것이 정상이라 「나빠짐」 판정에 안 쓴다
        return targets.contains(f.rule()) || fileRule(f.rule()) || f.rule().endsWith(".parseError") || f.rule().startsWith("metric.");
    }

    /** Java 를 줄 단위로 못 다루는 경우 — 사유, 괜찮으면 null. 파싱 실패·홀로 \r(JavaParser 는 줄로 센다)·유니코드 이스케이프 */
    static String javaUnsafe(String text, List<Finding> findings) {
        for (Finding f : findings) {
            if (f.rule().equals(JavaRules.PARSE_ERROR)) {
                return "구문을 못 읽음";
            }
        }
        if (text.indexOf('\r') >= 0 || UNICODE_ESCAPE.matcher(text).find()) {
            return "\\r·유니코드 이스케이프 — 줄 매핑 불가";
        }
        return null;
    }

    // ---------------------------------------------------------------- 수정기 ① 삭제

    /** debugger 홀로 줄만 — 주석·템플릿 리터럴 안은 남긴다 */
    static void deleteDebugger(Source s, List<Integer> lines, Edits e, List<Skip> skips) {
        String stripped = RegexRule.stripComments(s.text(), s.lang());
        String[] tl = s.text().split("\n", -1);
        String[] sl = stripped.split("\n", -1);
        Pattern only = Pattern.compile(SEMI_OPTIONAL.contains(s.lang()) ? "^\\s*debugger\\s*;?\\s*$" : "^\\s*debugger\\s*;\\s*$");
        int[] ticks = new int[sl.length + 1];
        for (int i = 0; i < sl.length; i++) {
            int n = 0;
            for (int k = 0; k < sl[i].length(); k++) {
                n += sl[i].charAt(k) == '`' ? 1 : 0;
            }
            ticks[i + 1] = ticks[i] + n;
        }
        for (int line : lines) {
            int i = line - 1;
            if (i < 0 || i >= tl.length) {
                continue;
            }
            if (!only.matcher(tl[i]).matches()) {
                skips.add(new Skip(DELETE, "줄 " + line + ": debugger 와 다른 코드가 한 줄에 있다"));
            } else if (!DEBUGGER_WORD.matcher(sl[i]).find()) {
                skips.add(new Skip(DELETE, "줄 " + line + ": 주석 안 debugger"));
            } else if (ticks[i] % 2 != 0) {
                skips.add(new Skip(DELETE, "줄 " + line + ": 템플릿 리터럴 안 debugger"));
            } else if (BRACELESS.matcher(previousCode(sl, i)).find()) {
                skips.add(new Skip(DELETE, "줄 " + line + ": 중괄호 없는 제어문 몸체일 수 있음"));
            } else {
                e.delete(line);
            }
        }
    }

    /** 주석 뺀 글에서 그 줄 위의 마지막 비지 않은 줄(끝 공백 뗌) — 없으면 빈 글 */
    private static String previousCode(String[] stripped, int i) {
        for (int k = i - 1; k >= 0; k--) {
            if (!stripped[k].isBlank()) {
                return stripped[k].stripTrailing();
            }
        }
        return "";
    }

    /** 쓰지 않는 Java import — 다시 파싱해 그 줄의 ImportDeclaration. 줄에 import 말고 아무것도 없을 때만 줄째(여러 줄 import 도) */
    static void deleteJavaImports(String text, List<Integer> lines, JavaSource parser, Edits e, List<Skip> skips) {
        ParseResult<CompilationUnit> r = parser.parse(text);
        if (!JavaSource.ok(r)) {
            skips.add(new Skip(DELETE, "구문을 못 읽음"));
            return;
        }
        String[] tl = text.split("\n", -1);
        Set<Integer> want = new TreeSet<>(lines);
        for (ImportDeclaration im : r.getResult().get().getImports()) {
            if (im.getRange().isEmpty() || !want.contains(im.getRange().get().begin.line)) {
                continue;
            }
            com.github.javaparser.Range rg = im.getRange().get();
            int b = rg.begin.line;
            int en = rg.end.line;
            boolean clean = tl[b - 1].substring(0, Math.min(tl[b - 1].length(), rg.begin.column - 1)).isBlank()
                    && tl[en - 1].substring(Math.min(tl[en - 1].length(), rg.end.column)).isBlank();
            for (int k = b; k <= en && clean; k++) {
                clean = !tl[k - 1].contains("tb-ignore");
            }
            if (!clean) {
                skips.add(new Skip(DELETE, "줄 " + b + ": import 줄에 다른 코드"));
                continue;
            }
            for (int k = b; k <= en; k++) {
                e.delete(k);
            }
        }
    }

    /** 쓰지 않는 JS·TS import — 이름 일부만이면 한 줄 문장의 그 조각만, 전부면 문장째. JSX 팩터리·주석에 나오는 이름은 지킨다 */
    static void fixTsImports(Source s, List<Integer> lines, Edits e, List<Skip> skips) {
        String text = s.text();
        String[] tl = text.split("\n", -1);
        int[] lineStart = lineStarts(text);
        Set<String> keep = new java.util.HashSet<>(KEEP_JS);
        keep.addAll(commentWords(text, s.lang()));
        Set<Integer> want = new TreeSet<>(lines);
        for (JsImportRule.Stmt st : JsImportRule.statements(s)) {
            if (!want.contains(st.startLine()) || st.unused().isEmpty()) {
                continue;
            }
            int line = st.startLine();
            Set<String> removable = new LinkedHashSet<>(st.unused());
            removable.removeAll(keep);
            if (removable.isEmpty()) {
                skips.add(new Skip(DELETE, "줄 " + line + ": 지키는 이름(JSX 팩터리·주석에 나옴)"));
                continue;
            }
            boolean marked = false;
            for (int k = st.startLine(); k <= st.endLine(); k++) {
                marked |= tl[k - 1].contains("tb-ignore");
            }
            if (marked) {
                skips.add(new Skip(DELETE, "줄 " + line + ": 표식이 든 import"));
                continue;
            }
            if (removable.containsAll(new LinkedHashSet<>(st.names()))) {
                int endLineEnd = st.endLine() < tl.length ? lineStart[st.endLine() - 1] + tl[st.endLine() - 1].length() : text.length();
                String tail = text.substring(Math.min(st.stmtEnd(), endLineEnd), endLineEnd).strip();
                if (!tail.isEmpty() && !tail.startsWith("//")) {
                    skips.add(new Skip(DELETE, "줄 " + line + ": import 뒤에 다른 코드"));
                    continue;
                }
                String keepEval = sideEffectImport(s.lang(), text, st, tl[line - 1], tail);
                for (int k = st.startLine(); k <= st.endLine(); k++) {
                    e.delete(k);
                }
                if (keepEval != null) {
                    e.repl.put(line, keepEval); // JS 는 이름을 다 지워도 모듈 실행은 남긴다
                }
                continue;
            }
            if (st.startLine() != st.endLine()) {
                skips.add(new Skip(DELETE, "줄 " + line + ": 여러 줄 import 는 일부만 못 지운다"));
                continue;
            }
            int ls = lineStart[line - 1];
            String full = tl[line - 1];
            String rest = full.substring(st.clauseEnd() - ls);
            String clause = text.substring(st.clauseStart(), st.clauseEnd());
            String rebuilt = rebuildClause(clause, removable);
            if (!ONE_LINE_TAIL.matcher(rest).matches() || rebuilt == null || rebuilt.isEmpty()) {
                skips.add(new Skip(DELETE, "줄 " + line + ": import 꼴을 못 읽음"));
                continue;
            }
            e.replace(line, full.substring(0, st.clauseStart() - ls) + rebuilt + rest);
        }
    }

    /**
     * 이름을 다 지운 import 문을 무엇으로 바꾸나 — JS(js·jsx·mjs·cjs)는 모듈을 실행만 하는 {@code import 'm';} 로(부수 효과 — 폴리필·전역 등록 —
     * 를 지키려고), TS(ts·tsx)는 null(문장째 지움 — TS 는 쓰지 않는 값 import 를 원래 내보내지 않는다). 따옴표·{@code ;}·꼬리 주석·들여쓰기는 원래대로
     */
    static String sideEffectImport(String lang, String text, JsImportRule.Stmt st, String firstLine, String tail) {
        if (!JS_ONLY.contains(lang)) {
            return null;
        }
        Matcher m = MODULE.matcher(text.substring(st.clauseEnd(), st.stmtEnd()));
        if (!m.find()) {
            return null;
        }
        String indent = firstLine.substring(0, firstLine.length() - firstLine.stripLeading().length());
        boolean semi = text.substring(st.clauseEnd(), st.stmtEnd()).stripTrailing().endsWith(";");
        return indent + "import " + m.group(1) + m.group(2) + m.group(1) + (semi ? ";" : "") + (tail.isEmpty() ? "" : " " + tail);
    }

    /** `A, { b, c as d }` 에서 지울 이름의 조각을 뺀다. 중괄호가 비면 중괄호째. 못 읽으면 null */
    static String rebuildClause(String clause, Set<String> removable) {
        int brace = clause.indexOf('{');
        int close = clause.lastIndexOf('}');
        if (brace >= 0 && close < brace) {
            return null;
        }
        String head = brace < 0 ? clause : clause.substring(0, brace);
        List<String> out = new ArrayList<>();
        for (String part : head.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            List<String> n = JsImportRule.names(p);
            if (n.size() != 1) {
                return null;
            }
            if (!removable.contains(n.get(0))) {
                out.add(p);
            }
        }
        if (brace >= 0) {
            String inner = clause.substring(brace + 1, close);
            boolean trailingComma = inner.strip().endsWith(",");
            String pad = inner.startsWith(" ") ? " " : "";
            List<String> kept = new ArrayList<>();
            for (String part : inner.split(",")) {
                String p = part.trim();
                if (p.isEmpty()) {
                    continue;
                }
                List<String> n = JsImportRule.names("{" + p + "}");
                if (n.size() != 1) {
                    return null;
                }
                if (!removable.contains(n.get(0))) {
                    kept.add(p);
                }
            }
            if (!kept.isEmpty()) {
                out.add("{" + pad + String.join(", ", kept) + (trailingComma ? "," : "") + pad + "}");
            }
        }
        return String.join(", ", out);
    }

    /** 주석 글에 나오는 단어 — 원문과 주석 지운 글이 다른 자리만 */
    static Set<String> commentWords(String text, String lang) {
        String stripped = RegexRule.stripComments(text, lang);
        Set<String> out = new java.util.HashSet<>();
        if (stripped.length() != text.length()) {
            return out;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            sb.append(stripped.charAt(i) != text.charAt(i) ? text.charAt(i) : ' ');
        }
        for (String w : WORDS.split(sb)) {
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 수정기 ② 표식

    /**
     * 쓸모없는 tb-ignore 표식을 뗀다 — 주석 모양은 {@code stripComments} 로 판정. 줄 전체가 표식 주석이면 줄째, 주석 안 글 뒤면 표식부터 주석 끝까지,
     * 코드 뒤 꼬리 주석이면 주석째. 파일 사이 규칙·전부 표식·꺼진 규칙·표식 둘·모르는 주석 문법은 건너뛴다
     */
    static void removeMarkers(Source s, List<Integer> lines, Set<String> enabledIds, Edits e, List<Skip> skips) {
        String lang = s.lang();
        boolean known = C_LIKE.contains(lang) || "sql".equals(lang) || JSP.contains(lang) || MARKUP.contains(lang) || HASH_LINE.contains(lang);
        boolean textBlocks = C_LIKE.contains(lang) && s.text().contains("\"\"\"");
        String[] tl = s.text().split("\n", -1);
        String[] sl = RegexRule.stripComments(s.text(), lang).split("\n", -1);
        for (int line : lines) {
            int i = line - 1;
            if (i < 0 || i >= tl.length) {
                continue;
            }
            String why = markerEdit(tl[i], i < sl.length ? sl[i] : tl[i], lang, known, textBlocks, enabledIds, line, e);
            if (why != null) {
                skips.add(new Skip(MARKER, "줄 " + line + ": " + why));
            }
        }
    }

    /** 한 줄 — 고쳤으면 null, 아니면 사유 */
    private static String markerEdit(String line, String sline, String lang, boolean known, boolean textBlocks, Set<String> enabledIds, int no,
            Edits e) {
        Matcher mm = Ignores.MARK.matcher(line);
        int hits = 0;
        int pm = -1;
        int pe = -1;
        while (mm.find()) {
            hits++;
            if (pm < 0) {
                pm = mm.start();
                pe = mm.end();
            }
        }
        if (hits != 1) {
            return hits == 0 ? "표식을 못 찾음" : "표식 둘";
        }
        Set<String> ids = Ignores.ids(line, pe);
        if (ids.isEmpty() || ids.stream().anyMatch(CROSS_FILE::contains)) {
            return "파일 사이 규칙·전부 표식 — 폴더 검사에서만 판정 가능";
        }
        if (!enabledIds.containsAll(ids)) {
            return "꺼졌거나 없는 규칙의 표식";
        }
        if (!known) {
            return "주석 문법을 모르는 파일";
        }
        if (textBlocks) {
            return "텍스트 블록이 있는 파일 — 주석 판정을 못 믿는다"; // stripComments 는 """ 를 모른다
        }
        String shape = "표식이 든 주석 모양을 못 읽음";
        if (sline.length() != line.length() || sline.charAt(pm) != ' ') {
            return shape;
        }
        int q = 0;
        for (int k = pm - 1; k >= 0; k--) {
            if (!Character.isWhitespace(sline.charAt(k))) {
                q = k + 1;
                break;
            }
        }
        for (int k = q; k < sline.length(); k++) {
            if (!Character.isWhitespace(sline.charAt(k))) {
                return shape;
            }
        }
        boolean whole = line.substring(0, q).isBlank();
        String seg = line.substring(q);
        String st = seg.stripLeading();
        int segOff = q + seg.length() - st.length();
        String opener = opener(st, lang, whole);
        if (opener == null || HASH_LINE.contains(lang) && !whole) {
            return shape;
        }
        int afterOp = segOff + opener.length();
        if (afterOp > pm) {
            return shape;
        }
        String between = line.substring(afterOp, pm);
        if (between.contains("//") || between.contains("/*")) {
            return shape;
        }
        String closer = "*".equals(opener) ? "*/" : CLOSER.get(opener);
        int closeIdx = closer == null ? -1 : line.indexOf(closer, pe);
        if (closer != null && !"*".equals(opener) && closeIdx < 0) {
            return shape; // 블록 주석이 다음 줄로 이어진다
        }
        if (closeIdx >= 0 && !line.substring(closeIdx + closer.length()).isBlank()) {
            return shape;
        }
        boolean first = between.isBlank();
        if (!whole) {
            e.replace(no, line.substring(0, q).stripTrailing());
        } else if (first && !("*".equals(opener) && closeIdx >= 0)) {
            e.delete(no);
        } else {
            String prefix = line.substring(0, pm).stripTrailing();
            e.replace(no, closeIdx >= 0 ? prefix + " " + line.substring(closeIdx) : prefix);
        }
        return null;
    }

    /** 주석 여는 글 — 언어별. 줄 전체가 블록 주석 가운데 줄이면 「*」 */
    private static String opener(String st, String lang, boolean whole) {
        List<String> ops = new ArrayList<>();
        if (C_LIKE.contains(lang)) {
            ops.addAll(List.of("//", "/*"));
        } else if ("sql".equals(lang)) {
            ops.addAll(List.of("--", "/*"));
        } else if (JSP.contains(lang)) {
            ops.addAll(List.of("<%--", "<!--"));
        } else if (MARKUP.contains(lang)) {
            ops.add("<!--");
        } else if ("properties".equals(lang)) {
            ops.addAll(List.of("#", "!"));
        } else if (HASH_LINE.contains(lang)) {
            ops.add("#");
        }
        for (String op : ops) {
            if (st.startsWith(op)) {
                return op;
            }
        }
        if (whole && (C_LIKE.contains(lang) || "sql".equals(lang)) && st.startsWith("*") && !st.startsWith("*/")) {
            return "*";
        }
        return null;
    }

    // ---------------------------------------------------------------- 줄 편집

    static int[] lineStarts(String text) {
        String[] tl = text.split("\n", -1);
        int[] out = new int[tl.length];
        int off = 0;
        for (int i = 0; i < tl.length; i++) {
            out[i] = off;
            off += tl[i].length() + 1;
        }
        return out;
    }

    /** 줄 지우기·바꾸기 모음(줄 번호 1부터, 원래 글 기준) — 결과와 고친 줄(옛·새 번호)을 낸다 */
    static final class Edits {
        private final String[] lines;
        private final Map<Integer, String> repl = new HashMap<>();

        Edits(String text) {
            this.lines = text.split("\n", -1);
        }

        void delete(int line) {
            repl.put(line, null);
        }

        void replace(int line, String text) {
            if (!repl.containsKey(line)) {
                repl.put(line, text);
            }
        }

        boolean isEmpty() {
            return repl.isEmpty();
        }

        String result() {
            List<String> out = new ArrayList<>(lines.length);
            for (int i = 0; i < lines.length; i++) {
                if (!repl.containsKey(i + 1)) {
                    out.add(lines[i]);
                } else if (repl.get(i + 1) != null) {
                    out.add(repl.get(i + 1));
                }
            }
            return String.join("\n", out);
        }

        Set<Integer> touchedOld() {
            return new TreeSet<>(repl.keySet());
        }

        Set<Integer> touchedNew() {
            Set<Integer> out = new TreeSet<>();
            int n = 0;
            for (int i = 0; i < lines.length; i++) {
                if (!repl.containsKey(i + 1)) {
                    n++;
                } else if (repl.get(i + 1) != null) {
                    n++;
                    out.add(n);
                }
            }
            return out;
        }
    }
}
