package kr.ejg.toolbox.core.check;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.job.JobContext;

/**
 * 코드 검사 한 번(5-4). 폴더 — 켜진 규칙 글롭의 합집합으로 목록을 떠서 파일마다 {@link RuleSet.Run#apply}, 끝에 파일 사이 규칙.
 * 붙여넣기 — {@link Source#pasted} 하나. 글·발췌는 결과에만 있고 여기서 저장·로그하지 않는다(절대 규칙 3).
 */
public final class CheckRunner {

    static final int PROGRESS_EVERY = 50;

    /** 못 읽은 파일(권한·사라짐)은 {@code skipped}, 구문을 못 읽은 파일은 {@code parseErrors}. 목록이 상한에 걸리면 {@code truncated} */
    public record RunResult(List<Finding> findings, int files, int skipped, int parseErrors, boolean truncated) {
        public RunResult {
            findings = List.copyOf(findings);
        }
    }

    private CheckRunner() {
    }

    /** @param ctx 진행률·취소 — 없으면 null(테스트·표본) */
    public static RunResult run(String root, RuleSet rules, LocalFiles files, JobContext ctx) throws IOException {
        return run(root, rules, files, ctx, null);
    }

    /**
     * 변경분 검사(5-6b). {@code changed} 가 있으면 바뀐 파일만 검사하고, 안 바뀐 {@code *.java} 는 파일 사이 상태만 모은다
     * ({@link RuleSet.Run#observe} — namespace·dupMapping 이 전체 검사와 같은 답을 내게). 나머지 안 바뀐 파일은 안 읽는다.
     * 끝에 결과를 바뀐 파일로 거른다 — 짝이 안 바뀐 파일에 있는 dupMapping 은 바뀐 쪽만 남는다. {@code files} 는 검사한 파일 수
     *
     * @param changed root 기준 상대 경로 → 바뀌었나. null 이면 전부
     */
    public static RunResult run(String root, RuleSet rules, LocalFiles files, JobContext ctx, Predicate<String> changed)
            throws IOException {
        List<String> globs = rules.globs().stream().map(g -> g.contains("/") ? g.substring(g.lastIndexOf('/') + 1) : g).distinct()
                .toList();
        if (globs.isEmpty()) {
            return new RunResult(List.of(), 0, 0, 0, false);
        }
        LocalFiles.Listing list = files.list(root, globs, LocalFiles.MAX_FILES);
        Path base = files.check(root);
        RuleSet.Run run = rules.run();
        List<Finding> out = new ArrayList<>();
        int skipped = 0;
        int checked = 0;
        int n = list.files().size();
        for (int i = 0; i < n; i++) {
            if (ctx != null) {
                ctx.checkCancelled();
                if (i % PROGRESS_EVERY == 0) {
                    ctx.progress(n == 0 ? 0 : i * 100 / n, i + "/" + n + " 파일");
                }
            }
            String rel = list.files().get(i).rel();
            boolean apply = changed == null || changed.test(rel);
            if (!apply && !rel.toLowerCase(java.util.Locale.ROOT).endsWith(".java")) {
                continue;
            }
            LocalFiles.Text t;
            try {
                t = files.read(base.resolve(rel).toString());
            } catch (IOException | LocalFiles.Refused e) {
                skipped += apply ? 1 : 0;
                continue;
            }
            Source s = new Source(rel, t.text(), t.encoding(), t.lineEnding(), null);
            if (apply) {
                out.addAll(run.apply(s));
                checked++;
            } else {
                run.observe(s);
            }
        }
        out.addAll(run.finish());
        if (changed != null) {
            out.removeIf(f -> !changed.test(f.file()));
        }
        out.sort(Comparator.comparing(Finding::file).thenComparingInt(Finding::line).thenComparing(Finding::rule));
        if (ctx != null) {
            ctx.progress(100, n + "/" + n + " 파일");
        }
        return new RunResult(out, checked, skipped, parseErrors(out), list.truncated());
    }

    public static RunResult runText(String text, String lang, RuleSet rules) {
        RuleSet.Run run = rules.run();
        Source s = Source.pasted(text, lang == null || lang.isBlank() ? guessLang(text) : lang);
        List<Finding> out = new ArrayList<>(run.apply(s));
        out.addAll(run.finish());
        return new RunResult(out, 1, 0, parseErrors(out), false);
    }

    private static int parseErrors(List<Finding> out) {
        return (int) out.stream().filter(f -> f.rule().endsWith(".parseError")).map(Finding::file).distinct().count();
    }

    private static final Pattern TS_IMPORT = Pattern.compile("(?m)^\\s*import\\s.+\\sfrom\\s+['\"]");
    private static final Pattern JAVA = Pattern.compile("(?m)^\\s*(?:package\\s+[\\w.]+;|import\\s+[\\w.*]+;|(?:public\\s+)?(?:class|interface)\\s)");

    /** 붙여넣기 언어 추정 — XML(mapper) · JSP · Java · TS · 나머지 JS */
    static String guessLang(String text) {
        String t = text == null ? "" : text.stripLeading();
        if (t.startsWith("<?xml") || t.contains("<mapper")) {
            return "xml";
        }
        if (t.contains("<%") || t.contains("<c:") || t.contains("${")) {
            return "jsp";
        }
        if (JAVA.matcher(t).find()) {
            return "java";
        }
        return TS_IMPORT.matcher(t).find() || t.contains(": string") || t.contains(": number") ? "ts" : "js";
    }
}
