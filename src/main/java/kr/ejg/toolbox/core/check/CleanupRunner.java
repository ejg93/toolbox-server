package kr.ejg.toolbox.core.check;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.job.JobContext;

/**
 * 코드 정리 폴더 단위(5-34c, 설계 30) — 미리보기는 쓰지 않고 파일마다 결과 요약과 hash 를 낸다. 적용은 파일을 다시 읽어 hash 가 같고
 * 다시 계산한 결과의 outHash 도 같을 때만 {@link LocalFiles#write}(백업 먼저, 인코딩 그대로)로 쓴다. 결과 글 파일엔 경로·단계·건수·사유만(코드 본문 없음).
 */
public final class CleanupRunner {

    public static final String REPORT = "정리_결과.txt";
    static final int PROGRESS_EVERY = 50;

    private CleanupRunner() {
    }

    /** 미리보기 한 줄 — before·after 는 고를 규칙의 지적 수 */
    public record FileRow(String rel, String hash, String outHash, int before, int after, boolean changed, String lineEnding,
            List<Cleanup.Step> steps, List<Cleanup.Skip> skips) {
        public FileRow {
            steps = List.copyOf(steps);
            skips = List.copyOf(skips);
        }
    }

    public record Preview(List<FileRow> rows, int files, int skipped, int changed, boolean truncated) {
        public Preview {
            rows = List.copyOf(rows);
        }
    }

    /** 적용 요청 한 줄 — 미리보기가 준 hash·outHash 그대로 */
    public record FileRef(String rel, String hash, String outHash) {
    }

    public record Written(String rel, boolean ok, String reason, String backup) {
    }

    public record Applied(List<Written> rows, int written, int failed, int stale, Path report) {
        public Applied {
            rows = List.copyOf(rows);
        }
    }

    /**
     * 미리보기 — 쓰지 않는다. 고칠 것·건너뛴 것·고를 규칙의 지적이 있는 파일만 줄로 낸다.
     *
     * @param changed 변경분만이면 root 기준 상대 경로 → 바뀌었나, 아니면 null
     */
    public static Preview preview(String root, RuleSet rules, Collection<String> wanted, String lineEnding, LocalFiles files, JobContext ctx,
            Predicate<String> changed, List<String> exclude) throws IOException {
        Cleanup c = new Cleanup(rules, wanted, lineEnding);
        List<String> globs = globs(rules, c.wanted());
        if (globs.isEmpty()) {
            return new Preview(List.of(), 0, 0, 0, false);
        }
        LocalFiles.Listing list = files.list(root, globs, exclude == null ? List.of() : exclude, LocalFiles.MAX_FILES);
        Path base = files.check(root);
        List<FileRow> rows = new ArrayList<>();
        int n = list.files().size();
        int read = 0;
        int skipped = 0;
        int changedCount = 0;
        for (int i = 0; i < n; i++) {
            progress(ctx, i, n);
            String rel = list.files().get(i).rel();
            if (changed != null && !changed.test(rel)) {
                continue;
            }
            LocalFiles.Text t;
            try {
                t = files.read(base.resolve(rel).toString());
            } catch (IOException | LocalFiles.Refused e) {
                skipped++;
                continue;
            }
            read++;
            Cleanup.Result r;
            try {
                r = c.clean(new Source(rel, t.text(), t.encoding(), t.lineEnding(), null));
            } catch (RuntimeException e) {
                // 파일 하나의 수정기 결함이 폴더 전체를 멈추지 않게 — 그 파일은 안 고친다(사유만, 코드 본문 없음)
                rows.add(new FileRow(rel, hash(t.encoding(), t.lineEnding(), t.text()), "", 0, 0, false, t.lineEnding(), List.of(),
                        List.of(new Cleanup.Skip("file", "내부 오류 — " + e.getClass().getSimpleName()))));
                continue;
            }
            int before = count(r.before(), c.wanted());
            int after = count(r.after(), c.wanted());
            changedCount += r.changed() ? 1 : 0;
            if (r.changed() || !r.skips().isEmpty() || before > 0) {
                rows.add(new FileRow(rel, hash(t.encoding(), t.lineEnding(), t.text()), outHash(r), before, after, r.changed(), r.lineEnding(),
                        r.steps(), r.skips()));
            }
        }
        progress(ctx, n, n);
        return new Preview(rows, read, skipped, changedCount, list.truncated());
    }

    /**
     * 적용 — 요청 차례대로. 다시 읽은 hash 가 다르면 「미리보기 뒤 바뀜」, 다시 계산한 결과가 다르면 「미리보기와 결과가 다름」 으로 안 쓴다.
     * 중지되면 그때까지 쓴 것은 그대로 두고 결과 글을 남긴 뒤 멈춘다(4-20)
     */
    public static Applied apply(String root, RuleSet rules, Collection<String> wanted, String lineEnding, LocalFiles files, JobContext ctx,
            List<FileRef> refs, Path backupRoot, Path reportDir) throws IOException {
        Cleanup c = new Cleanup(rules, wanted, lineEnding);
        Path base = files.check(root);
        List<Written> rows = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        int written = 0;
        int failed = 0;
        int stale = 0;
        Path report = reportDir.resolve(REPORT);
        boolean stopped = false;
        try {
            for (int i = 0; i < refs.size(); i++) {
                progress(ctx, i, refs.size());
                FileRef ref = refs.get(i);
                String path = base.resolve(ref.rel()).normalize().toString();
                LocalFiles.Text t;
                try {
                    t = files.read(path);
                } catch (IOException | LocalFiles.Refused e) {
                    failed++;
                    rows.add(new Written(ref.rel(), false, e.getMessage(), null));
                    lines.add(ref.rel() + " · 실패: " + e.getMessage());
                    continue;
                }
                if (!hash(t.encoding(), t.lineEnding(), t.text()).equals(ref.hash())) {
                    stale++;
                    rows.add(new Written(ref.rel(), false, "미리보기 뒤 바뀜", null));
                    lines.add(ref.rel() + " · 낡음: 미리보기 뒤 바뀜");
                    continue;
                }
                Cleanup.Result r;
                try {
                    r = c.clean(new Source(ref.rel(), t.text(), t.encoding(), t.lineEnding(), null));
                } catch (RuntimeException e) {
                    failed++;
                    rows.add(new Written(ref.rel(), false, "내부 오류 — " + e.getClass().getSimpleName(), null));
                    lines.add(ref.rel() + " · 실패: 내부 오류 — " + e.getClass().getSimpleName());
                    continue;
                }
                if (!outHash(r).equals(ref.outHash())) {
                    stale++;
                    rows.add(new Written(ref.rel(), false, "미리보기와 결과가 다름", null));
                    lines.add(ref.rel() + " · 낡음: 미리보기와 결과가 다름(규칙·프로필이 바뀜)");
                    continue;
                }
                if (!r.changed()) {
                    rows.add(new Written(ref.rel(), false, "바뀐 것 없음", null));
                    continue;
                }
                try {
                    LocalFiles.checkEncodable(r.text(), t.encoding(), r.lineEnding());
                    Path backup = files.write(path, root, r.text(), t.encoding(), r.lineEnding(), backupRoot);
                    written++;
                    rows.add(new Written(ref.rel(), true, null, backup.toString()));
                    lines.add(summary(ref.rel(), r, c.wanted()));
                } catch (IOException | LocalFiles.Refused e) {
                    failed++;
                    rows.add(new Written(ref.rel(), false, e.getMessage(), null));
                    lines.add(ref.rel() + " · 실패: " + e.getMessage());
                }
            }
            progress(ctx, refs.size(), refs.size());
        } catch (JobContext.JobCancelledException e) {
            stopped = true;
            throw e;
        } finally {
            writeReport(report, root, written, failed, stale, stopped, lines);
        }
        return new Applied(rows, written, failed, stale, report);
    }

    private static void writeReport(Path report, String root, int written, int failed, int stale, boolean stopped, List<String> lines)
            throws IOException {
        Files.createDirectories(report.getParent());
        List<String> out = new ArrayList<>();
        out.add("코드 정리 " + report.getParent().getFileName() + " — 폴더 " + root + " · 덮어씀 " + written + " · 실패 " + failed + " · 낡음 " + stale
                + (stopped ? " · 중지(나머지는 안 씀)" : ""));
        out.addAll(lines);
        Files.write(report, out, StandardCharsets.UTF_8);
    }

    /** 결과 글 한 줄 — 경로·단계·전후 건수·건너뜀 사유 */
    static String summary(String rel, Cleanup.Result r, Set<String> wanted) {
        Set<String> stages = new LinkedHashSet<>();
        r.steps().forEach(s -> stages.add(s.stage()));
        StringBuilder sb = new StringBuilder(rel).append(" · ").append(String.join(",", stages)).append(" · 전 ")
                .append(count(r.before(), wanted)).append(" → 후 ").append(count(r.after(), wanted));
        if (!r.skips().isEmpty()) {
            List<String> why = new ArrayList<>();
            r.skips().forEach(k -> why.add(k.stage() + " " + k.reason()));
            sb.append(" · ").append(String.join("; ", why));
        }
        return sb.toString();
    }

    private static void progress(JobContext ctx, int i, int n) {
        if (ctx != null) {
            ctx.checkCancelled();
            if (i % PROGRESS_EVERY == 0 || i == n) {
                ctx.progress(n == 0 ? 100 : i * 100 / n, i + "/" + n + " 파일");
            }
        }
    }

    private static int count(List<Finding> fs, Set<String> wanted) {
        int n = 0;
        for (Finding f : fs) {
            n += wanted.contains(f.rule()) ? 1 : 0;
        }
        return n;
    }

    /** 고를 규칙의 globs 합집합(파일 이름 쪽). 쓸모없는 표식을 고르면 검사가 읽는 파일 전부(표식은 어느 파일에나 있다) */
    static List<String> globs(RuleSet rules, Set<String> wanted) {
        Set<String> out = new TreeSet<>();
        for (Rule.Def d : rules.defs()) {
            if (wanted.contains(d.id())) {
                d.globs().stream().filter(g -> !"*".equals(g)).forEach(out::add);
            }
        }
        if (wanted.contains(Ignores.UNUSED)) {
            out.addAll(rules.globs());
        }
        List<String> names = new ArrayList<>();
        for (String g : out) {
            String n = g.contains("/") ? g.substring(g.lastIndexOf('/') + 1) : g;
            if (!names.contains(n)) {
                names.add(n);
            }
        }
        return names;
    }

    static String hash(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(String.join("|", parts).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String outHash(Cleanup.Result r) {
        return hash(String.valueOf(r.lineEnding()), r.text());
    }
}
