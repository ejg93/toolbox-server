package kr.ejg.toolbox.core.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 5-6a — 작업 사본 변경분·구간을 git·svn 실물로 잰다. git 은 CI·개발 PC 둘 다 있다. svn 은 없으면 실물을 건너뛰고
 * 픽스처({@code fixtures/vcs/svn-*.xml} — 이 PC 의 svn 1.14.2 실물 출력을 떠 둔 것)만 잰다(CI ubuntu-latest 에 svn 없음).
 */
class WorkingCopyTest {

    static final Path FIXTURES = Path.of("src/test/resources/fixtures/vcs");

    /** 테스트 준비용 — 셸 없이. 실패하면 출력과 함께 멈춘다 */
    static String exec(Path dir, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true);
        pb.environment().put("LC_ALL", "C");
        Process p = pb.start();
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), String.join(" ", cmd) + "\n" + out);
        return out;
    }

    static void git(Path dir, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of("git", "-c", "user.name=t", "-c", "user.email=t@t", "-c", "core.autocrlf=false"));
        cmd.addAll(List.of(args));
        exec(dir, cmd.toArray(String[]::new));
    }

    static void write(Path f, String text) throws IOException {
        Files.createDirectories(f.getParent());
        Files.writeString(f, text, StandardCharsets.UTF_8);
    }

    /** 첫 판 파일 다섯 — git·svn 이 같은 꼴로 쓴다 */
    static void firstTree(Path root) throws IOException {
        write(root.resolve("src/main/A.java"), "a\n");
        write(root.resolve("src/main/B.java"), "b\n");
        write(root.resolve("src/main/E.java"), "e\n");
        write(root.resolve("src/한글 폴더/가 나.jsp"), "c\n");
        write(root.resolve("docs/d.txt"), "d\n");
    }

    @Test
    void gitChangedDiffRecent(@TempDir Path tmp) throws Exception {
        assumeTrue(Cli.available("git", tmp), "git 없음");
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        firstTree(repo);
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "c1");
        git(repo, "tag", "v1");
        write(repo.resolve("src/main/A.java"), "a2\n");
        git(repo, "mv", "src/main/B.java", "src/main/B2.java");
        write(repo.resolve("src/한글 폴더/가 나.jsp"), "c2\n");
        git(repo, "rm", "-q", "src/main/E.java");
        write(repo.resolve("src/new/deep/N.java"), "n\n");
        write(repo.resolve("src/main/S.java"), "s\n");
        git(repo, "add", "src/main/S.java");
        write(repo.resolve("docs/d.txt"), "d2\n");

        Path src = repo.resolve("src");
        WorkingCopy.Info info = WorkingCopy.detect(src);
        assertEquals(WorkingCopy.Kind.GIT, info.kind());
        assertTrue(info.available(), info.reason());
        WorkingCopy.Changes ch = WorkingCopy.changed(src);
        assertEquals(Set.of("main/A.java", "main/B2.java", "main/S.java", "new/deep/N.java", "한글 폴더/가 나.jsp"), ch.files(),
                "하위 폴더 기준 상대 경로 — 삭제·옛 이름·root 밖(docs)은 빠진다");
        assertTrue(ch.contains("new/deep/N.java") && !ch.contains("main/E.java"));
        assertTrue(WorkingCopy.changed(repo).files().contains("docs/d.txt"));

        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "c2");
        assertEquals(List.of("A main/B2.java", "A main/S.java", "A new/deep/N.java", "D main/B.java", "D main/E.java", "M main/A.java",
                "M 한글 폴더/가 나.jsp"), WorkingCopy.diff(src, "v1", "HEAD").stream().map(c -> c.status() + " " + c.rel()).sorted().toList());
        List<WorkingCopy.Rev> recent = WorkingCopy.recent(src, 5);
        assertEquals(List.of("c2", "c1"), recent.stream().map(WorkingCopy.Rev::subject).toList());

        for (String bad : new String[] {"--output=x", "v1..HEAD", "", "-p"}) {
            assertThrows(IllegalArgumentException.class, () -> WorkingCopy.diff(src, bad, "HEAD"), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> WorkingCopy.diff(src, null, "HEAD"));
        IllegalStateException none = assertThrows(IllegalStateException.class, () -> WorkingCopy.diff(src, "nope", "HEAD"));
        assertTrue(none.getMessage().contains("nope") || none.getMessage().startsWith("fatal"), none.getMessage());
    }

    @Test
    void notAWorkingCopy(@TempDir Path tmp) {
        WorkingCopy.Info info = WorkingCopy.detect(tmp);
        assertEquals(WorkingCopy.Kind.NONE, info.kind());
        assertFalse(info.available());
        assertThrows(IllegalStateException.class, () -> WorkingCopy.changed(tmp));
        assertEquals(List.of(), WorkingCopy.recent(tmp, 5));
    }

    @Test
    void svnChangedAndDiff(@TempDir Path tmp) throws Exception {
        assumeTrue(Cli.available("svn", tmp), "svn 없음 — 픽스처 테스트만");
        Path repo = tmp.resolve("repo");
        exec(tmp, "svnadmin", "create", repo.toString());
        String url = "file:///" + repo.toAbsolutePath().toString().replace('\\', '/');
        exec(tmp, "svn", "checkout", "-q", url, "wc");
        Path wc = tmp.resolve("wc");
        firstTree(wc);
        exec(wc, "svn", "add", "-q", "src", "docs");
        exec(wc, "svn", "commit", "-q", "-m", "r1");
        exec(wc, "svn", "update", "-q");
        write(wc.resolve("src/main/A.java"), "a2\n");
        exec(wc, "svn", "mv", "-q", "src/main/B.java", "src/main/B2.java");
        write(wc.resolve("src/한글 폴더/가 나.jsp"), "c2\n");
        exec(wc, "svn", "rm", "-q", "src/main/E.java");
        write(wc.resolve("src/new/deep/N.java"), "n\n");
        write(wc.resolve("src/main/S.java"), "s\n");
        exec(wc, "svn", "add", "-q", "src/main/S.java");
        write(wc.resolve("src/main/U.java"), "u\n");
        exec(wc, "svn", "propset", "-q", "x", "y", "src/main");

        Path src = wc.resolve("src");
        WorkingCopy.Info info = WorkingCopy.detect(src);
        assertEquals(WorkingCopy.Kind.SVN, info.kind(), ".svn 은 작업 사본 루트에만 — 부모로 올라가 찾는다");
        WorkingCopy.Changes ch = WorkingCopy.changed(src);
        assertEquals(Set.of("main/A.java", "main/B2.java", "main/S.java", "main/U.java", "한글 폴더/가 나.jsp"), ch.files(),
                "속성만 바뀐 폴더(main)·삭제·옛 이름은 빠진다");
        assertEquals(Set.of("new"), ch.dirs(), "버전 없는 폴더는 한 줄 — 안의 파일은 접두로");
        assertTrue(ch.contains("new/deep/N.java"));

        exec(wc, "svn", "add", "-q", "--force", ".");
        exec(wc, "svn", "commit", "-q", "-m", "r2");
        assertEquals(List.of("A main/B2.java", "A main/S.java", "A main/U.java", "A new/deep/N.java", "D main/B.java", "D main/E.java",
                "M main/A.java", "M 한글 폴더/가 나.jsp"),
                WorkingCopy.diff(src, "1", "2").stream().map(c -> c.status() + " " + c.rel()).sorted().toList());
        assertEquals(List.of(), WorkingCopy.recent(src, 5), "svn 은 서버를 자동으로 안 탄다");
        assertThrows(IllegalArgumentException.class, () -> WorkingCopy.diff(src, "1:2", "3"));
        IllegalStateException bad = assertThrows(IllegalStateException.class, () -> WorkingCopy.diff(src, "1", "99"));
        assertTrue(bad.getMessage().startsWith("svn: E"), "LC_ALL=C 라 오류 글이 ASCII: " + bad.getMessage());
    }

    /** svn 이 없는 CI 도 파싱은 잰다 — 이 PC svn 1.14.2 실물 출력(역슬래시 경로·한글·버전 없는 폴더·속성만 바뀐 폴더) */
    @Test
    void svnFixtures(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("new"));
        WorkingCopy.Changes ch = WorkingCopy.svnChanges(Files.readAllBytes(FIXTURES.resolve("svn-status.xml")), tmp);
        assertEquals(Set.of("main/A.java", "main/B2.java", "main/S.java", "main/U.java", "한글 폴더/가 나.jsp"), ch.files());
        assertEquals(Set.of("new"), ch.dirs());
        assertEquals(List.of("A main/B2.java", "A main/S.java", "A main/U.java", "A new/deep/N.java", "D main/B.java", "D main/E.java",
                "M main/A.java", "M 한글 폴더/가 나.jsp"),
                WorkingCopy.svnDiffs(Files.readAllBytes(FIXTURES.resolve("svn-diff.xml"))).stream().map(c -> c.status() + " " + c.rel())
                        .sorted().toList());
    }

    @Test
    void refRules() {
        assertEquals("abc1234", WorkingCopy.gitRef("abc1234"));
        assertEquals("origin/main", WorkingCopy.gitRef("origin/main"));
        assertEquals("HEAD~3", WorkingCopy.gitRef("HEAD~3"));
        assertEquals("HEAD", WorkingCopy.svnRev("head"));
        assertEquals("1234", WorkingCopy.svnRev("1234"));
        assertThrows(IllegalArgumentException.class, () -> WorkingCopy.svnRev("-r1"));
        assertThrows(IllegalArgumentException.class, () -> WorkingCopy.svnRev("12345678901"));
        assertThrows(IllegalArgumentException.class, () -> Cli.run(List.of("cmd", "/c", "dir"), Path.of(".")));
    }

    /** 구간 결과 경로는 root 기준 상대만(PR #24 AI 리뷰 ③) */
    @Test
    void changePathsStayInside() {
        for (String bad : new String[] {"../x", "a/../../b", "/etc/passwd", "C:/x", "a\\b", "./a", ""}) {
            assertThrows(IllegalArgumentException.class, () -> new WorkingCopy.Change(bad, "A"), bad);
        }
        assertEquals("a/b..c/x.java", new WorkingCopy.Change("a/b..c/x.java", "M").rel(), "이름 속 점 둘은 된다");
    }
}
