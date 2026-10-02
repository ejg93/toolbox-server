package kr.ejg.toolbox.core.vcs;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.check.CheckRunner;
import kr.ejg.toolbox.core.check.Finding;
import kr.ejg.toolbox.core.check.RuleSet;
import kr.ejg.toolbox.core.fs.LocalFiles;
import kr.ejg.toolbox.core.profile.Profile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * V-12 — eGov 두 판(v5.0.5 {@code egov-prev} → v5.0.6 {@code egov})을 임시 git 저장소의 작업 트리·두 커밋으로 넣어
 * 5-6a·5-6b·5-8 을 실물 크기로 잰다. 정답은 두 표본 폴더를 바이트로 견줘 테스트가 만든다(추가·수정·삭제).
 * <ul>
 *   <li>등급 A: 실행 예외 · {@code changed} ≠ 추가∪수정 · {@code diff(prev, HEAD)} ≠ 정답 · 변경분 검사 결과 ≠ 전체 검사 결과를
 *       변경 집합으로 거른 것(파일 사이 규칙까지 — {@code observe} 가 맞는지) · 변경분 검사의 parseError</li>
 *   <li>골든 {@code corpus/vcs-egov.json}: 추가·수정·삭제 수, 검사한 파일 수, 결과 건수</li>
 * </ul>
 * 줄바꿈만 다른 파일이 「안 바뀜」 이 되지 않게 {@code core.autocrlf=false} + {@code * -text}. svn 은 표본에 안 돌린다(5-6a 의 작은 실물).
 */
@Tag("corpus")
class VcsCorpusTest {

    @TempDir
    Path tmp;

    static void git(Path dir, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of("git", "-c", "user.name=t", "-c", "user.email=t@t"));
        cmd.addAll(Arrays.asList(args));
        WorkingCopyTest.exec(dir, cmd.toArray(String[]::new));
    }

    /** 상대 경로(`/`) → 파일 */
    static Map<String, Path> tree(Path root) throws IOException {
        Map<String, Path> out = new TreeMap<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(Files::isRegularFile).forEach(p -> out.put(root.relativize(p).toString().replace('\\', '/'), p));
        }
        return out;
    }

    static void copyTree(Path from, Path to) throws IOException {
        for (Map.Entry<String, Path> e : tree(from).entrySet()) {
            Path dst = to.resolve(e.getKey());
            Files.createDirectories(dst.getParent());
            Files.copy(e.getValue(), dst, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static RuleSet egovRules() {
        Profile p = new Profile("corpus", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null,
                new Profile.Naming(null, null, null, null, null, true), new Profile.CodeCheck(null, null, null), "egov35", null, null, null);
        return RuleSet.load(p, null);
    }

    static List<String> keys(List<Finding> fs) {
        return fs.stream().map(f -> f.file() + ":" + f.line() + " " + f.rule()).sorted().toList();
    }

    @Test
    void twoReleasesAsCommits() throws Exception {
        CorpusFiles.verify();
        assumeTrue(Cli.available("git", tmp), "git 없음 — 로컬 --full 은 git 이 있어야 한다");
        Path prev = CorpusFiles.root().resolve("egov-prev");
        Path now = CorpusFiles.root().resolve("egov");

        // 정답 — 바이트 비교
        Map<String, Path> a = tree(prev);
        Map<String, Path> b = tree(now);
        Set<String> added = new TreeSet<>();
        Set<String> modified = new TreeSet<>();
        Set<String> deleted = new TreeSet<>(a.keySet());
        deleted.removeAll(b.keySet());
        for (Map.Entry<String, Path> e : b.entrySet()) {
            Path old = a.get(e.getKey());
            if (old == null) {
                added.add(e.getKey());
            } else if (Files.mismatch(old, e.getValue()) >= 0) {
                modified.add(e.getKey());
            }
        }

        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        git(repo, "config", "core.autocrlf", "false");
        git(repo, "config", "core.longpaths", "true");
        git(repo, "config", "core.excludesFile", repo.resolve(".git/info/none").toString()); // 이 PC 의 전역 ignore 를 안 탄다
        Files.writeString(repo.resolve(".git/info/attributes"), "* -text\n");
        copyTree(prev, repo);
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "prev");
        git(repo, "tag", "prev");
        try (Stream<Path> s = Files.list(repo)) {
            for (Path p : s.filter(p -> !p.getFileName().toString().equals(".git")).toList()) {
                try (Stream<Path> w = Files.walk(p)) {
                    for (Path q : w.sorted(java.util.Comparator.reverseOrder()).toList()) {
                        Files.delete(q);
                    }
                }
            }
        }
        copyTree(now, repo);

        List<String> bad = new ArrayList<>();
        // ① 작업 트리 변경분 = 추가 ∪ 수정
        WorkingCopy.Changes ch = WorkingCopy.changed(repo);
        Set<String> want = new TreeSet<>(added);
        want.addAll(modified);
        Set<String> got = new TreeSet<>(ch.files());
        diffSets("changed", want, got, bad);
        if (!ch.dirs().isEmpty()) {
            bad.add("changed: -uall 인데 폴더 줄 " + ch.dirs().size());
        }

        // ② 변경분 검사 = 전체 검사를 변경 집합으로 거른 것(파일 사이 규칙 포함)
        LocalFiles files = new LocalFiles(tmp.resolve("data"));
        RuleSet rules = egovRules();
        CheckRunner.RunResult full = CheckRunner.run(repo.toString(), rules, files, null);
        CheckRunner.RunResult part = CheckRunner.run(repo.toString(), rules, files, null, ch::contains);
        List<String> expected = keys(full.findings().stream().filter(f -> ch.contains(f.file())).toList());
        List<String> actual = keys(part.findings());
        if (!expected.equals(actual)) {
            Set<String> miss = new TreeSet<>(expected);
            actual.forEach(miss::remove);
            Set<String> extra = new TreeSet<>(actual);
            expected.forEach(extra::remove);
            bad.add("변경분 검사 ≠ 전체 검사 거름 — 빠짐 " + head(miss) + " · 더 있음 " + head(extra));
        }
        part.findings().stream().filter(f -> f.rule().endsWith(".parseError")).forEach(f -> bad.add("parseError " + f.file()));

        // ③ 커밋 구간 = 정답 A·M·D
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "now");
        Map<String, Set<String>> byStatus = new TreeMap<>();
        for (WorkingCopy.Change c : WorkingCopy.diff(repo, "prev", "HEAD")) {
            byStatus.computeIfAbsent(c.status(), k -> new TreeSet<>()).add(c.rel());
        }
        diffSets("diff A", added, byStatus.getOrDefault("A", Set.of()), bad);
        diffSets("diff M", modified, byStatus.getOrDefault("M", Set.of()), bad);
        diffSets("diff D", deleted, byStatus.getOrDefault("D", Set.of()), bad);

        CorpusFiles.none("변경분·배포 목록(추가 " + added.size() + "·수정 " + modified.size() + "·삭제 " + deleted.size() + ")", bad,
                added.size() + modified.size() + deleted.size());
        Map<String, Object> golden = new LinkedHashMap<>();
        golden.put("added", added.size());
        golden.put("modified", modified.size());
        golden.put("deleted", deleted.size());
        golden.put("checkedFiles", part.files());
        golden.put("findings", part.findings().size());
        golden.put("fullFindings", full.findings().size());
        GoldenFiles.assertJson("corpus/vcs-egov.json", golden);
        assertTrue(part.files() < full.files(), "변경분은 전체보다 적게 검사한다");
    }

    static void diffSets(String what, Set<String> want, Set<String> got, List<String> bad) {
        if (want.equals(got)) {
            return;
        }
        Set<String> miss = new TreeSet<>(want);
        miss.removeAll(got);
        Set<String> extra = new TreeSet<>(got);
        extra.removeAll(want);
        bad.add(what + " — 빠짐 " + miss.size() + " " + head(miss) + " · 더 있음 " + extra.size() + " " + head(extra));
    }

    static String head(Set<String> s) {
        return s.stream().limit(5).toList().toString();
    }
}
