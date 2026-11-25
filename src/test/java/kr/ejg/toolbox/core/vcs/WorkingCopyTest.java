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
        assumeTrue(Cli.available(kr.ejg.toolbox.core.vcs.Cli.Exe.GIT, tmp), "git 없음");
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

    /** 0-83c — 받은 소스의 .git/config 에 든 core.fsmonitor 명령을 git 이 실행하지 않는다(status·rev-parse·diff·log 넷 다) */
    @Test
    void gitIgnoresRepoFsmonitorCommand(@TempDir Path tmp) throws Exception {
        assumeTrue(Cli.available(kr.ejg.toolbox.core.vcs.Cli.Exe.GIT, tmp), "git 없음");
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        firstTree(repo);
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "c1");
        git(repo, "tag", "v1");
        git(repo, "config", "core.fsmonitor", "echo hit > fsmon-hit.txt");
        write(repo.resolve("src/main/A.java"), "a2\n");
        Path hit = repo.resolve("fsmon-hit.txt");

        WorkingCopy.changed(repo);
        WorkingCopy.diff(repo, "v1", "HEAD");
        WorkingCopy.recent(repo, 1);
        assertFalse(Files.exists(hit), "작업 사본 설정의 fsmonitor 명령이 돌았다");
        assertEquals(List.of("git", "-c", "core.fsmonitor=false", "-c", "log.showSignature=false", "-c", "safe.bareRepository=explicit", "status"),
                Cli.command(Cli.Exe.GIT, List.of("status"))); // 리뷰 I2 — 암묵 bare 막기가 더해졌다
        assertEquals(List.of("svn", "status"), Cli.command(Cli.Exe.SVN, List.of("status")));
    }

    /** 0-85e — 시간 초과에 alias 셸 같은 손자 프로세스까지 끈다. 전엔 git 만 끄고 셸이 살아 파일을 썼다 */
    @Test
    void timeoutKillsGrandchildren(@TempDir Path tmp) throws Exception {
        assumeTrue(Cli.available(kr.ejg.toolbox.core.vcs.Cli.Exe.GIT, tmp), "git 없음");
        List<String> args = List.of("-c", "alias.tbhang=!sleep 4; echo hit > late.txt", "tbhang");
        assertThrows(IllegalStateException.class, () -> Cli.run(Cli.Exe.GIT, args, tmp, java.time.Duration.ofSeconds(1)));
        Thread.sleep(6000);
        assertFalse(Files.exists(tmp.resolve("late.txt")), "시간 초과 뒤 손자 셸이 살아 파일을 썼다");
    }

    /** 0-83c 마무리 리뷰 — 작업 사본 설정의 filter clean(.gitattributes) · log.showSignature + gpg.program 도 안 돈다 */
    @Test
    void gitIgnoresRepoFilterAndGpgCommands(@TempDir Path tmp) throws Exception {
        assumeTrue(Cli.available(kr.ejg.toolbox.core.vcs.Cli.Exe.GIT, tmp), "git 없음");
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        firstTree(repo);
        write(repo.resolve(".gitattributes"), "*.java filter=evil\n");
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "c1");
        // 파일을 바꾸는 서명 커밋 — 커밋 객체는 받은 저장소가 정한다
        write(repo.resolve("src/main/A.java"), "a2\n");
        git(repo, "add", "-A");
        String tree = exec(repo, "git", "write-tree").strip();
        String parent = exec(repo, "git", "rev-parse", "HEAD").strip();
        write(tmp.resolve("commit.txt"), "tree " + tree + "\nparent " + parent + "\nauthor t <t@t> 1700000000 +0900\n"
                + "committer t <t@t> 1700000000 +0900\ngpgsig -----BEGIN PGP SIGNATURE-----\n \n abc\n -----END PGP SIGNATURE-----\n\nsigned\n");
        String signed = exec(repo, "git", "hash-object", "-t", "commit", "-w", tmp.resolve("commit.txt").toString()).strip();
        git(repo, "update-ref", "HEAD", signed);
        git(repo, "config", "filter.evil.clean", "echo hit > filter-hit.txt; cat");
        git(repo, "config", "log.showSignature", "true");
        git(repo, "config", "gpg.program", "echo hit > gpg-hit.txt");
        Path a = repo.resolve("src/main/A.java");
        Files.setLastModifiedTime(a, java.nio.file.attribute.FileTime.fromMillis(Files.getLastModifiedTime(a).toMillis() + 5_000));

        WorkingCopy.changed(repo);
        assertEquals("signed", WorkingCopy.recent(repo, 1).get(0).subject());
        assertFalse(Files.exists(repo.resolve("filter-hit.txt")), "작업 사본 설정의 filter clean 명령이 돌았다");
        assertFalse(Files.exists(repo.resolve("gpg-hit.txt")), "작업 사본 설정의 gpg.program 이 돌았다");
    }

    static void later(Path f) throws IOException {
        Files.setLastModifiedTime(f, java.nio.file.attribute.FileTime.fromMillis(Files.getLastModifiedTime(f).toMillis() + 5_000));
    }

    /** 0-103(C-VCS-EQ) — 이름에 = 가 든 filter 는 -c 로 못 끈다 → 변경분 조회를 거절(명령이 안 돈다) */
    @Test
    void gitRefusesFilterNameWithEquals(@TempDir Path tmp) throws Exception {
        assumeTrue(Cli.available(kr.ejg.toolbox.core.vcs.Cli.Exe.GIT, tmp), "git 없음");
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        firstTree(repo);
        write(repo.resolve(".gitattributes"), "*.java filter=a=b\n");
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "c1");
        Files.writeString(repo.resolve(".git/config"), "[filter \"a=b\"]\n\tclean = \"echo hit > eq-hit.txt; cat\"\n", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
        later(repo.resolve("src/main/A.java"));
        assertThrows(IllegalStateException.class, () -> WorkingCopy.changed(repo));
        assertFalse(Files.exists(repo.resolve("eq-hit.txt")), "= 든 이름의 filter clean 명령이 돌았다");
    }

    /** 0-120(C-01) — git 이 없는 객체를 원격에서 받지 않게: 모든 git 호출 환경에 GIT_NO_LAZY_FETCH=1 */
    @Test
    void cliEnvForbidsLazyFetch() {
        assertEquals("1", Cli.env().get("GIT_NO_LAZY_FETCH"));
        assertEquals("0", Cli.env().get("GIT_OPTIONAL_LOCKS"));
        assertEquals("0", Cli.env().get("GIT_TERMINAL_PROMPT"));
        assertEquals("C", Cli.env().get("LC_ALL"));
    }

    /** 0-120(C-01) — promisor 원격·extensions.partialclone 이 설정된 저장소는 조회를 거절한다(옛 git 은 GIT_NO_LAZY_FETCH 를 모른다) */
    @Test
    void gitRefusesPartialClone(@TempDir Path tmp) throws Exception {
        assumeTrue(Cli.available(kr.ejg.toolbox.core.vcs.Cli.Exe.GIT, tmp), "git 없음");
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        firstTree(repo);
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "c1");
        git(repo, "config", "remote.o.promisor", "true");
        git(repo, "config", "remote.o.url", tmp.resolve("no-such-remote").toString());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> WorkingCopy.changed(repo));
        assertTrue(e.getMessage().contains("부분 클론"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> WorkingCopy.recent(repo, 5));
        Path other = tmp.resolve("other");
        Files.createDirectories(other);
        git(other, "init", "-q");
        firstTree(other);
        git(other, "add", "-A");
        git(other, "commit", "-q", "-m", "c1");
        git(other, "config", "extensions.partialclone", "o");
        assertThrows(IllegalStateException.class, () -> WorkingCopy.changed(other));
    }

    /** 0-126(M7·C2-03) — partialclonefilter 만 있는 원격 · 작업 트리 설정의 promisor 도 부분 클론으로 거절 */
    @Test
    void gitRefusesPartialCloneKeysElsewhere(@TempDir Path tmp) throws Exception {
        assumeTrue(Cli.available(kr.ejg.toolbox.core.vcs.Cli.Exe.GIT, tmp), "git 없음");
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        firstTree(repo);
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "c1");
        git(repo, "config", "remote.x.partialclonefilter", "blob:none");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> WorkingCopy.changed(repo));
        assertTrue(e.getMessage().contains("부분 클론"), e.getMessage());
        Path wt = tmp.resolve("wt");
        Files.createDirectories(wt);
        git(wt, "init", "-q");
        firstTree(wt);
        git(wt, "add", "-A");
        git(wt, "commit", "-q", "-m", "c1");
        boolean worktree;
        try {
            git(wt, "config", "extensions.worktreeConfig", "true");
            git(wt, "config", "--worktree", "remote.x.promisor", "true");
            worktree = true;
        } catch (AssertionError x) {
            worktree = false;
        }
        assumeTrue(worktree, "git 이 config --worktree 를 모른다");
        assertTrue(assertThrows(IllegalStateException.class, () -> WorkingCopy.changed(wt)).getMessage().contains("부분 클론"));
    }

    /** 0-128(C2-H1, 규칙 1) — 저장소 설정 글을 자바가 먼저 읽는다: include 구역 · 경로를 여는 키의 네트워크 꼴 값 · gitdir·alternates 줄 */
    @Test
    void gitConfigGuardRefusesIncludesAndRemotePaths(@TempDir Path tmp) throws Exception {
        for (String inc : List.of("[include]\n\tpath = ../x\n", "[includeIf \"gitdir:/a/\"]\n path = y\n", "[INCLUDE] path = z\n")) {
            assertEquals(GitConfigGuard.INCLUDE, guard(tmp, inc), inc);
        }
        for (String net : List.of("[core]\n excludesFile = //h/s/x\n", "[core]\n\tattributesfile = \"\\\\\\\\h\\\\s\\\\a\"\n",
                "[mailmap]\n file = //h/s/m\n", "[core]\n hooksPath = \\\\h\\s\n")) {
            assertEquals(GitConfigGuard.REMOTE_PATH, guard(tmp, net), net);
        }
        // 마무리 리뷰 I1 — BOM · 한 줄 머리 둘 · 이은 줄 · 값 안 따옴표 · diff.orderFile
        assertEquals(GitConfigGuard.INCLUDE, guard(tmp, "\uFEFF[include]\n path = //h/s/x\n"));
        assertEquals(GitConfigGuard.REMOTE_PATH, guard(tmp, "\uFEFF[core]\n hooksPath = //h/s\n"));
        assertEquals(GitConfigGuard.INCLUDE, guard(tmp, "[core] [include] path = ../x\n"));
        assertEquals(GitConfigGuard.UNREADABLE, guard(tmp, "[core]\n hooksPath = \\\n//h/s\n"));
        for (String q : List.of("[core]\n excludesFile = \"\"//h/s/x\n", "[core]\n excludesFile = /\"/h/s/x\"\n", "[diff]\n orderFile = //h/s/o\n")) {
            assertEquals(GitConfigGuard.REMOTE_PATH, guard(tmp, q), q);
        }
        Path linked = tmp.resolve("linked");
        Files.createDirectories(linked);
        write(linked.resolve(".git"), "gitdir: //h/s/repo.git\n");
        assertEquals(GitConfigGuard.REMOTE_PATH, assertThrows(IllegalStateException.class, () -> GitConfigGuard.check(linked.resolve(".git"))).getMessage());
        Path alt = tmp.resolve("alt");
        write(alt.resolve(".git/config"), "[core]\n bare = false\n");
        write(alt.resolve(".git/objects/info/alternates"), "//h/s/objects\n");
        assertEquals(GitConfigGuard.REMOTE_PATH, assertThrows(IllegalStateException.class, () -> GitConfigGuard.check(alt.resolve(".git"))).getMessage());
        // 지킴이 — 원격 주소 · 로컬 경로 · 주석 줄 · 다른 구역 · 빈 설정 · 상대 gitdir
        for (String ok : List.of("[remote \"origin\"]\n url = //h/s/repo.git\n", "[core]\n excludesFile = C:/x/.gitignore\n", "# [include]\n; path = x\n",
                "[filter \"lfs\"]\n clean = x\n", "")) {
            assertEquals("", guard(tmp, ok), ok);
        }
        Path sub = tmp.resolve("sub");
        write(sub.resolve("a/.git"), "gitdir: ../.git/modules/a\n");
        write(sub.resolve(".git/modules/a/config"), "[core]\n bare = false\n");
        GitConfigGuard.check(sub.resolve("a/.git"));
        // 실물 git — include 든 저장소는 변경분 조회 전에 거절
        assumeTrue(Cli.available(kr.ejg.toolbox.core.vcs.Cli.Exe.GIT, tmp), "git 없음");
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        firstTree(repo);
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "c1");
        Files.writeString(repo.resolve(".git/config"), "[include]\n\tpath = ../no-such\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        assertEquals(GitConfigGuard.INCLUDE, assertThrows(IllegalStateException.class, () -> WorkingCopy.changed(repo)).getMessage());
        assertThrows(IllegalStateException.class, () -> WorkingCopy.recent(repo, 5));
    }

    /** 마무리 리뷰 I2 — git 은 암묵 bare 저장소(검사 폴더가 HEAD·objects·refs 꼴)를 안 연다 — 가드가 읽은 .git 과 git 이 쓰는 저장소가 같다 */
    @Test
    void gitRefusesImplicitBareRepository() {
        List<String> cmd = Cli.command(Cli.Exe.GIT, List.of("status"));
        assertTrue(cmd.containsAll(List.of("-c", "safe.bareRepository=explicit")), cmd.toString());
    }

    /** .git 하나를 재는 가드 — 거절 사유, 안 던지면 "" (git 을 띄우지 않는다) */
    private static String guardAt(Path dotGit) {
        try {
            GitConfigGuard.check(dotGit);
            return "";
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
    }

    /** 0-132a(대기 0-132 ② · 사용자 결정 3·4) — alternates 사슬 · 따옴표 줄(C 꼴 8진) · 매핑 드라이브 값 · 링크. git 을 안 띄운다 */
    @Test
    void gitGuardFollowsAlternatesAndRefusesMappedDrives(@TempDir Path tmp) throws Exception {
        Path far = tmp.resolve("far");
        write(far.resolve("objects/info/alternates"), "//127.0.0.1/tb-none/o\n");
        Path a = tmp.resolve("a");
        write(a.resolve(".git/config"), "[core]\n bare = false\n");
        write(a.resolve(".git/objects/info/alternates"), far.resolve("objects").toString() + "\n");
        assertEquals(GitConfigGuard.REMOTE_PATH, guardAt(a.resolve(".git")), "2단 alternates");
        Path q = tmp.resolve("q");
        write(q.resolve(".git/config"), "[core]\n bare = false\n");
        write(q.resolve(".git/objects/info/alternates"), "\"\\057\\057127.0.0.1/tb-none/o\"\n");
        assertEquals(GitConfigGuard.REMOTE_PATH, guardAt(q.resolve(".git")), "따옴표 줄 8진 탈출");
        write(q.resolve(".git/objects/info/alternates"), "\"\\057\\057127.0.0.1/tb-none/o\n");
        assertEquals(GitConfigGuard.UNREADABLE, guardAt(q.resolve(".git")), "닫는 따옴표 없음");
        // 지킴이 — 로컬 2단 · 주석 줄 · 서로 가리키는 alternates
        Path near = tmp.resolve("near");
        write(near.resolve("objects/info/alternates"), "# //127.0.0.1/tb-none/x\n");
        Path b = tmp.resolve("b");
        write(b.resolve(".git/config"), "[core]\n bare = false\n");
        write(b.resolve(".git/objects/info/alternates"), near.resolve("objects").toString() + "\n");
        assertEquals("", guardAt(b.resolve(".git")), "로컬 2단 · 주석 줄");
        Path c = tmp.resolve("c");
        write(c.resolve(".git/config"), "");
        write(c.resolve(".git/objects/info/alternates"), b.resolve(".git/objects").toString() + "\n");
        write(b.resolve(".git/objects/info/alternates"), c.resolve(".git/objects").toString() + "\n");
        org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(5), () -> assertEquals("", guardAt(c.resolve(".git")), "고리"));
        // 매핑 드라이브 — 드라이브 판정을 넣는다(이 PC·CI 에 매핑 드라이브가 없다)
        assertTrue(GitConfigGuard.network("Z:/hooks", s -> s.startsWith("Z:")));
        assertFalse(GitConfigGuard.network("C:/x", s -> false));
        assertTrue(GitConfigGuard.network("\"//h\"/s", s -> false));
        assertTrue(GitConfigGuard.network("\\\\?\\C:\\x"), "장치 경로");
    }

    /** nested 한 번 — 거절 사유, 안 던지면 "" */
    private static String nestedAt(Path root) {
        try {
            GitConfigGuard.nested(root);
            return "";
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
    }

    /** bareShape 한 번 — 거절 사유, 안 던지면 "" */
    private static String bareAt(Path root, Path dotGit) {
        try {
            GitConfigGuard.bareShape(root, dotGit);
            return "";
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
    }

    /** 0-132b(대기 0-132 ①·③) — 하위 폴더 .git 의 gitdir·commondir · 검사 폴더가 저장소 폴더 꼴. git 을 안 띄운다 */
    @Test
    void gitGuardSeesNestedGitAndBareShape(@TempDir Path tmp) throws Exception {
        Path repo = tmp.resolve("repo");
        write(repo.resolve(".git/config"), "[core]\n bare = false\n");
        write(repo.resolve("sub/.git"), "gitdir: //127.0.0.1/tb-none/x\n");
        assertEquals(GitConfigGuard.REMOTE_PATH, nestedAt(repo), "하위 .git 파일의 gitdir");
        Path repo2 = tmp.resolve("repo2");
        write(repo2.resolve(".git/config"), "");
        write(repo2.resolve("mod/.git"), "gitdir: ../.git/modules/mod\n");
        write(repo2.resolve(".git/modules/mod/commondir"), "//127.0.0.1/tb-none/c\n");
        assertEquals(GitConfigGuard.REMOTE_PATH, nestedAt(repo2), "서브모듈 꼴의 commondir");
        Path repo3 = tmp.resolve("repo3");
        write(repo3.resolve(".git/config"), "");
        write(repo3.resolve("inner/.git/commondir"), "//127.0.0.1/tb-none/c\n");
        assertEquals(GitConfigGuard.REMOTE_PATH, nestedAt(repo3), "하위 .git 폴더의 commondir");
        // 지킴이 — 로컬 서브모듈 · .git 폴더 안 · 꼭대기 .git
        Path ok = tmp.resolve("ok");
        write(ok.resolve(".git/config"), "");
        write(ok.resolve("a/.git"), "gitdir: ../.git/modules/a\n");
        write(ok.resolve(".git/modules/a/config"), "");
        write(ok.resolve(".git/x/.git"), "gitdir: //127.0.0.1/tb-none/y\n");
        assertEquals("", nestedAt(ok), "로컬 서브모듈 · .git 폴더 안은 안 본다");
        Path top = tmp.resolve("top");
        write(top.resolve(".git"), "gitdir: //127.0.0.1/tb-none/t\n");
        assertEquals("", nestedAt(top), "꼭대기 .git 은 check 가 잰다");
        // bare 꼴 — 검사 폴더에서 .git 부모까지
        Path proj = tmp.resolve("proj");
        write(proj.resolve(".git/config"), "");
        write(proj.resolve("src/HEAD"), "ref: refs/heads/main\n");
        Files.createDirectories(proj.resolve("src/objects"));
        Files.createDirectories(proj.resolve("src/refs"));
        Files.createDirectories(proj.resolve("src/deep"));
        assertEquals(GitConfigGuard.BARE_SHAPE, bareAt(proj.resolve("src"), proj.resolve(".git")));
        assertEquals(GitConfigGuard.BARE_SHAPE, bareAt(proj.resolve("src/deep"), proj.resolve(".git")));
        assertEquals("", bareAt(proj, proj.resolve(".git")), "보통 저장소");
        Path half = tmp.resolve("half");
        write(half.resolve(".git/config"), "");
        write(half.resolve("w/HEAD"), "x\n");
        assertEquals("", bareAt(half.resolve("w"), half.resolve(".git")), "HEAD 만 있고 objects 없음");
    }

    /** 번들 93 마무리 리뷰 I1·I2·I3 — 닫는 따옴표 뒤 글 · HEAD+commondir 꼴 · .GIT 대소문자 · 깊은 하위 .git. git 을 안 띄운다 */
    @Test
    void gitGuardReviewShapes(@TempDir Path tmp) throws Exception {
        // I1 — git 은 닫는 따옴표 뒤를 다음 항목으로 읽는다
        Path q = tmp.resolve("q");
        write(q.resolve(".git/config"), "");
        write(q.resolve(".git/objects/info/alternates"), "\"x\" //127.0.0.1/tb-none/o\n");
        assertEquals(GitConfigGuard.UNREADABLE, guardAt(q.resolve(".git")), "닫는 따옴표 뒤 글");
        write(q.resolve(".git/objects/info/alternates"), "\"" + tmp.resolve("far").toString().replace("\\", "/") + "\"\n");
        assertEquals("", guardAt(q.resolve(".git")), "따옴표 한 덩이뿐이면 지난다");
        // I2 — HEAD + commondir 꼴(objects·refs 없이)도 저장소 폴더 꼴
        Path proj = tmp.resolve("proj");
        write(proj.resolve(".git/config"), "");
        write(proj.resolve("src/HEAD"), "ref: refs/heads/main\n");
        write(proj.resolve("src/commondir"), "//127.0.0.1/tb-none/c\n");
        assertEquals(GitConfigGuard.BARE_SHAPE, bareAt(proj.resolve("src"), proj.resolve(".git")));
        // I3 — .GIT(대소문자) · 65단 아래 .git 파일
        Path upper = tmp.resolve("upper");
        write(upper.resolve(".git/config"), "");
        write(upper.resolve("sub/.GIT"), "gitdir: //127.0.0.1/tb-none/x\n");
        assertEquals(GitConfigGuard.REMOTE_PATH, nestedAt(upper), ".GIT");
        Path deep = tmp.resolve("deep");
        write(deep.resolve(".git/config"), "");
        Path d = deep;
        for (int i = 0; i < 66; i++) {
            d = d.resolve("a");
        }
        write(d.resolve(".git"), "gitdir: //127.0.0.1/tb-none/y\n");
        assertEquals(GitConfigGuard.REMOTE_PATH, nestedAt(deep), "65단 아래");
    }

    /** 0-132a — 가드가 읽는 자리가 네트워크를 가리키는 링크면 따라가지 않고 거절(리눅스만 — Windows 에서는 UNC 대상 링크를 안 만든다) */
    @Test
    void gitGuardRefusesLinksToNetwork(@TempDir Path tmp) throws Exception {
        assumeTrue(!System.getProperty("os.name", "").startsWith("Windows"), "Windows 에서는 UNC 대상 링크를 안 만든다");
        Path a = tmp.resolve("a");
        Files.createDirectories(a.resolve(".git"));
        Path b = tmp.resolve("b");
        Files.createDirectories(b);
        try {
            Files.createSymbolicLink(a.resolve(".git/config"), Path.of("\\\\127.0.0.1\\tb-none\\c"));
            Files.createSymbolicLink(b.resolve(".git"), Path.of("\\\\127.0.0.1\\tb-none\\g"));
        } catch (IOException | UnsupportedOperationException e) {
            org.junit.jupiter.api.Assumptions.abort("심볼릭 링크를 못 만든다 — " + e.getClass().getSimpleName());
        }
        assertEquals(GitConfigGuard.REMOTE_PATH, guardAt(a.resolve(".git")), ".git/config 링크");
        assertEquals(GitConfigGuard.REMOTE_PATH, guardAt(b.resolve(".git")), ".git 자체가 링크");
        assertEquals(null, WorkingCopy.sizeInside(b, ".git"), "변경분 파일 크기도 안 따라간다");
    }

    /** 설정 글 하나를 새 폴더의 .git/config 로 — 거절 사유, 안 던지면 "" */
    private static String guard(Path tmp, String config) throws IOException {
        Path d = Files.createTempDirectory(tmp, "g");
        write(d.resolve(".git/config"), config);
        try {
            GitConfigGuard.check(d.resolve(".git"));
            return "";
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
    }

    /** 0-120(C-02) — filter 이름에 " 가 있으면 Windows 에서 -c 인자가 갈려 못 끈다 — 거절 */
    @Test
    void gitRefusesFilterNameWithQuote(@TempDir Path tmp) throws Exception {
        assumeTrue(Cli.available(kr.ejg.toolbox.core.vcs.Cli.Exe.GIT, tmp), "git 없음");
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        firstTree(repo);
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "c1");
        Files.writeString(repo.resolve(".git/config"), "[filter \"a\\\"\\\"b\"]\n\tclean = \"echo hit > q-hit.txt; cat\"\n", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
        later(repo.resolve("src/main/A.java"));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> WorkingCopy.changed(repo));
        assertTrue(e.getMessage().contains("따옴표"), e.getMessage());
        assertFalse(Files.exists(repo.resolve("q-hit.txt")), "따옴표 든 이름의 filter clean 명령이 돌았다");
    }

    /** 0-103(C-VCS-SCOPE) — 작업 트리 설정(config.worktree)의 filter 도 끈다 · 안에 든 저장소의 작업 트리는 안 들여다본다 */
    @Test
    void gitIgnoresWorktreeAndNestedFilters(@TempDir Path tmp) throws Exception {
        assumeTrue(Cli.available(kr.ejg.toolbox.core.vcs.Cli.Exe.GIT, tmp), "git 없음");
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-q");
        firstTree(repo);
        write(repo.resolve(".gitattributes"), "*.java filter=evil\n");
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "c1");
        boolean worktree;
        try {
            git(repo, "config", "extensions.worktreeConfig", "true");
            git(repo, "config", "--worktree", "filter.evil.clean", "echo hit > wt-hit.txt; cat");
            worktree = true;
        } catch (AssertionError e) {
            worktree = false;
        }
        assumeTrue(worktree, "git 이 config --worktree 를 모른다");
        later(repo.resolve("src/main/A.java"));
        WorkingCopy.changed(repo);
        assertFalse(Files.exists(repo.resolve("wt-hit.txt")), "작업 트리 설정의 filter clean 명령이 돌았다");

        Path sub = repo.resolve("sub");
        Files.createDirectories(sub);
        git(sub, "init", "-q");
        write(sub.resolve("S.java"), "s\n");
        write(sub.resolve(".gitattributes"), "*.java filter=evil\n");
        git(sub, "add", "-A");
        git(sub, "commit", "-q", "-m", "s1");
        git(sub, "config", "filter.evil.clean", "echo hit > sub-hit.txt; cat");
        git(repo, "add", "sub");
        git(repo, "commit", "-q", "-m", "c2");
        later(sub.resolve("S.java"));
        WorkingCopy.changed(repo);
        assertFalse(Files.exists(sub.resolve("sub-hit.txt")), "안에 든 저장소의 filter clean 명령이 돌았다");
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
        assumeTrue(Cli.available(kr.ejg.toolbox.core.vcs.Cli.Exe.SVN, tmp), "svn 없음 — 픽스처 테스트만");
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
    }

    /**
     * 5-11 — 「root 밖」 정책은 WorkingCopy 한 곳: 글자로 밖(절대·드라이브·`..` 마디)은 Change 가 거부, 이름 속 `:`·`\`·`./` 는 둔다
     * (PR #24 AI 리뷰 ④ — 합법 파일명으로 목록 전체가 400 이 되지 않게)
     */
    @Test
    void changePathsStayInside() {
        for (String bad : new String[] {"../x", "a/../../b", "a\\..\\b", "/etc/passwd", "\\x", "C:/x", "c:\\x", "c:", ""}) {
            assertThrows(IllegalArgumentException.class, () -> new WorkingCopy.Change(bad, "A"), bad);
        }
        // 5-12 — 한 글자 + : 로 시작해도 드라이브 꼴(뒤가 구분자·끝)이 아니면 리눅스 합법 이름(PR #25 AI 리뷰 ①)
        for (String ok : new String[] {"a/b..c/x.java", "a/b:c.txt", "./a", "x\\y.txt", "..a/b", "a:b.txt", "c:x"}) {
            assertEquals(ok, new WorkingCopy.Change(ok, "M").rel(), ok);
        }
    }

    /** 5-11 — root 안 링크가 밖 파일을 가리키면 크기를 안 낸다(PR #24 AI 리뷰 ③). 링크를 못 만드는 환경(권한 없는 Windows)은 건너뛴다 */
    @Test
    void sizeInsideFollowsLinksOnlyWithinRoot(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("root");
        write(root.resolve("a/in.txt"), "12345");
        write(tmp.resolve("outside.txt"), "secret!");
        assertEquals(5L, WorkingCopy.sizeInside(root, "a/in.txt"));
        assertEquals(null, WorkingCopy.sizeInside(root, "a/none.txt"));
        assertEquals(null, WorkingCopy.sizeInside(root, "a"), "폴더는 크기 없음");
        assertEquals(null, WorkingCopy.sizeInside(root, "../outside.txt"), "글자로 밖");
        // 5-12·6-8 — 둘째 글자가 : 면 OS 와 무관하게 열지 않는다. Windows 는 「c:x」 를 C 드라이브의 상대 경로로 풀어
        // root 가 C 에 있으면 root\x 를 연다 — root\x 를 만들어 둔다. 리눅스(CI)는 그 이름의 파일을 실제로 만들어 거부를 밟는다
        write(root.resolve("x"), "1");
        if (java.io.File.separatorChar == '/') {
            write(root.resolve("c:x"), "1");
        }
        assertEquals(null, WorkingCopy.sizeInside(root, "c:x"), "드라이브 꼴 이름");
        boolean linked;
        try {
            Files.createSymbolicLink(root.resolve("a/link.txt"), tmp.resolve("outside.txt"));
            Files.createSymbolicLink(root.resolve("a/inlink.txt"), root.resolve("a/in.txt"));
            linked = true;
        } catch (IOException | UnsupportedOperationException e) {
            linked = false;
        }
        assumeTrue(linked, "심볼릭 링크를 만들 수 없는 환경");
        assertEquals(null, WorkingCopy.sizeInside(root, "a/link.txt"), "링크로 밖");
        assertEquals(5L, WorkingCopy.sizeInside(root, "a/inlink.txt"), "링크라도 안이면 잰다");
    }
}
