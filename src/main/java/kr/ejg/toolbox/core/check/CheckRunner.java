package kr.ejg.toolbox.core.check;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        int n = list.files().size();
        for (int i = 0; i < n; i++) {
            if (ctx != null) {
                ctx.checkCancelled();
                if (i % PROGRESS_EVERY == 0) {
                    ctx.progress(n == 0 ? 0 : i * 100 / n, i + "/" + n + " 파일");
                }
            }
            String rel = list.files().get(i).rel();
            LocalFiles.Text t;
            try {
                t = files.read(base.resolve(rel).toString());
            } catch (IOException | LocalFiles.Refused e) {
                skipped++;
                continue;
            }
            out.addAll(run.apply(new Source(rel, t.text(), t.encoding(), t.lineEnding(), null)));
        }
        out.addAll(run.finish());
        out.sort(Comparator.comparing(Finding::file).thenComparingInt(Finding::line).thenComparing(Finding::rule));
        if (ctx != null) {
            ctx.progress(100, n + "/" + n + " 파일");
        }
        return new RunResult(out, n, skipped, parseErrors(out), list.truncated());
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
