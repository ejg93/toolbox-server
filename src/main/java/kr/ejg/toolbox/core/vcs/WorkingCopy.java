package kr.ejg.toolbox.core.vcs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.text.SafeSax;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 작업 사본 — git·svn 변경분과 커밋 구간(5-6a). 판별은 {@code .git}·{@code .svn} 표식 폴더(프로필 {@code project.vcs} 는 안 읽는다).
 * 경로는 전부 {@code root} 기준 상대·{@code /}. 사람이 읽는 출력이 아니라 git {@code -z}·svn {@code --xml} 을 읽는다(한글 경로).
 * svn 구간 조회만 저장소 서버에 묻는다(CLAUDE.md 규칙 1 예외) — 사용자가 조회를 눌렀을 때만 불린다.
 */
public final class WorkingCopy {

    public enum Kind { GIT, SVN, NONE }

    public record Info(Kind kind, boolean available, String reason) {
    }

    /** 바뀐 파일(추가·수정·이름 바꾼 새 경로·버전 없는 파일)과 버전 없는 폴더(안의 파일을 다 바뀐 것으로) */
    public record Changes(Set<String> files, Set<String> dirs) {
        public Changes {
            files = Set.copyOf(files);
            dirs = Set.copyOf(dirs);
        }

        public boolean contains(String rel) {
            if (files.contains(rel)) {
                return true;
            }
            for (String d : dirs) {
                if (rel.startsWith(d + "/")) {
                    return true;
                }
            }
            return false;
        }

        public int size() {
            return files.size() + dirs.size();
        }
    }

    /**
     * 구간 안 바뀐 파일 하나 — status 는 A·M·D. 「root 밖」 정책은 이 클래스 한 곳이다(5-11, PR #24 AI 리뷰 ③④):
     * 글자로 밖을 가리키는 경로(절대·드라이브 머리·{@code ..} 마디 — VCS 가 정상이면 안 내는 꼴)는 여기서 거부하고,
     * 실제 파일이 링크로 밖을 가리키는 것은 {@link #sizeInside} 가 실제 경로로 잰다. 이름 속 {@code :}·{@code \} 는 합법 파일명이라 둔다
     */
    public record Change(String rel, String status) {
        public Change {
            if (!relative(rel)) {
                throw new IllegalArgumentException("작업 사본 밖을 가리키는 경로: " + rel);
            }
        }
    }

    /** 드라이브 머리 — {@code C:}·{@code C:/x}·{@code c:\x}. {@code a:b.txt} 는 리눅스에서 합법 이름이라 둔다(5-12, PR #25 AI 리뷰 ①) */
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:(?:[/\\\\]|$)");

    /** root 기준 상대이고 글자로 위로 못 올라가는가 — 구분자는 {@code /}·{@code \} 둘 다로 마디를 나눈다 */
    static boolean relative(String rel) {
        if (rel == null || rel.isEmpty() || rel.startsWith("/") || rel.startsWith("\\") || DRIVE.matcher(rel).find()) {
            return false;
        }
        for (String seg : rel.split("[/\\\\]")) {
            if (seg.equals("..")) {
                return false;
            }
        }
        return true;
    }

    /**
     * root 안의 일반 파일이면 크기, 아니면 null(없음·폴더·root 밖). 링크는 따라간 실제 경로가 root 의 실제 경로 안일 때만 —
     * root 안 링크가 밖 파일을 가리키면 그 크기를 안 낸다(PR #24 AI 리뷰 ③)
     */
    public static Long sizeInside(Path root, String rel) {
        // Windows 는 c:x 를 드라이브 상대 경로로 풀어 같은 드라이브면 root\x 를 연다 — 목록에는 남기고 열지는 않는다(5-12)
        if (!relative(rel) || java.io.File.separatorChar == '\\' && rel.length() > 1 && rel.charAt(1) == ':') {
            return null;
        }
        try {
            Path f = root.resolve(rel);
            if (!Files.isRegularFile(f)) {
                return null;
            }
            return f.toRealPath().startsWith(root.toRealPath()) ? Files.size(f) : null;
        } catch (IOException | java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    public record Rev(String id, String date, String subject) {
    }

    /** git ref — 옵션 꼴(`-` 로 시작)·구간 표기(`..`)·빈 값을 막는다 */
    static final Pattern GIT_REF = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._/~^@{}-]{0,199}$");
    static final Pattern SVN_REV = Pattern.compile("^(?:[0-9]{1,10}|HEAD|BASE|COMMITTED|PREV)$");
    private static final Set<String> SVN_CHANGED = Set.of("modified", "added", "replaced", "conflicted", "merged", "unversioned");
    private static final Set<String> SVN_SKIP = Set.of("deleted", "missing", "external", "ignored", "incomplete", "none", "normal",
            "obstructed");

    private WorkingCopy() {
    }

    // ---------------------------------------------------------------- 판별

    /** 부모로 올라가며 표식 폴더를 찾는다. 같은 폴더에 둘 다면 git. 프로세스는 버전 확인에만 */
    public static Info detect(Path root) {
        Kind kind = Kind.NONE;
        for (Path d = root.toAbsolutePath().normalize(); d != null && kind == Kind.NONE; d = d.getParent()) {
            if (Files.exists(d.resolve(".git"))) {
                kind = Kind.GIT;
            } else if (Files.isDirectory(d.resolve(".svn"))) {
                kind = Kind.SVN;
            }
        }
        if (kind == Kind.NONE) {
            return new Info(Kind.NONE, false, "형상 관리 폴더가 아니다 — .git·.svn 이 없다");
        }
        Cli.Exe exe = kind == Kind.GIT ? Cli.Exe.GIT : Cli.Exe.SVN;
        boolean ok = Cli.available(exe, root);
        return new Info(kind, ok, ok ? "" : exe.command + " 명령을 못 찾았다 — PATH 에 " + exe.command + " 명령줄 도구가 있어야 한다");
    }

    private static Info require(Path root) {
        Info i = detect(root);
        if (i.kind() == Kind.NONE || !i.available()) {
            throw new IllegalStateException(i.reason());
        }
        return i;
    }

    // ---------------------------------------------------------------- 변경분

    public static Changes changed(Path root) {
        return require(root).kind() == Kind.GIT ? gitChanged(root) : svnChanged(root);
    }

    private static Changes gitChanged(Path root) {
        String prefix = gitPrefix(root);
        Cli.Result r = ok(Cli.run(Cli.Exe.GIT, List.of("-c", "core.quotepath=false", "status", "--porcelain=v1", "-z", "--untracked-files=all",
                "--", "."), root));
        Set<String> files = new TreeSet<>();
        String[] tok = r.text().split("\0");
        for (int i = 0; i < tok.length; i++) {
            String t = tok[i];
            if (t.length() < 4) {
                continue;
            }
            char x = t.charAt(0);
            char y = t.charAt(1);
            String path = t.substring(3);
            if (x == 'R' || x == 'C' || y == 'R' || y == 'C') {
                i++; // 뒤따르는 옛 경로
            }
            boolean untracked = x == '?' && y == '?';
            if ((untracked || "MARCTU".indexOf(x) >= 0 || "MARCTU".indexOf(y) >= 0) && path.startsWith(prefix)) {
                files.add(path.substring(prefix.length()));
            }
        }
        return new Changes(files, Set.of());
    }

    /** 저장소 루트에서 root 까지(`src/`). git 출력 경로는 하위 폴더에서 불러도 저장소 루트 기준이다 */
    private static String gitPrefix(Path root) {
        return ok(Cli.run(Cli.Exe.GIT, List.of("rev-parse", "--show-prefix"), root)).text().strip();
    }

    private static Changes svnChanged(Path root) {
        return svnChanges(ok(Cli.run(Cli.Exe.SVN, List.of("status", "--xml", "--non-interactive"), root)).out(), root);
    }

    /** `svn status --xml` 출력 → 변경분. 픽스처 테스트(svn 없는 CI)도 이 길로 */
    static Changes svnChanges(byte[] xml, Path root) {
        Set<String> files = new TreeSet<>();
        Set<String> dirs = new TreeSet<>();
        List<String[]> entries = new ArrayList<>();
        parseXml(xml, new DefaultHandler() {
            private String path;

            @Override
            public void startElement(String uri, String local, String q, Attributes a) {
                if (q.equals("entry")) {
                    path = a.getValue("path");
                } else if (q.equals("wc-status") && path != null) {
                    entries.add(new String[] {path, a.getValue("item")});
                }
            }
        });
        for (String[] e : entries) {
            String rel = svnRel(e[0]);
            String item = e[1] == null ? "" : e[1];
            if (rel.isEmpty() || SVN_SKIP.contains(item) && !SVN_CHANGED.contains(item)) {
                continue;
            }
            // 스키마에 없는 값도 바뀐 것으로 본다(덜 거르는 쪽)
            if (item.equals("unversioned") && Files.isDirectory(root.resolve(rel))) {
                dirs.add(rel);
            } else {
                files.add(rel);
            }
        }
        return new Changes(files, dirs);
    }

    private static String svnRel(String p) {
        String s = p.replace('\\', '/');
        while (s.startsWith("./")) {
            s = s.substring(2);
        }
        return s.equals(".") ? "" : s;
    }

    // ---------------------------------------------------------------- 구간

    public static List<Change> diff(Path root, String from, String to) {
        Info info = require(root);
        List<Change> out = info.kind() == Kind.GIT ? gitDiff(root, gitRef(from), gitRef(to)) : svnDiff(root, svnRev(from), svnRev(to));
        out.sort(Comparator.comparing(Change::rel));
        return out;
    }

    static String gitRef(String ref) {
        if (ref == null || !GIT_REF.matcher(ref).matches() || ref.contains("..")) {
            throw new IllegalArgumentException("git 커밋·태그·가지 이름이 아니다: " + (ref == null ? "" : ref));
        }
        return ref;
    }

    static String svnRev(String rev) {
        if (rev == null || !SVN_REV.matcher(rev.toUpperCase(Locale.ROOT)).matches()) {
            throw new IllegalArgumentException("svn 리비전은 숫자 또는 HEAD·BASE·COMMITTED·PREV: " + (rev == null ? "" : rev));
        }
        return rev.toUpperCase(Locale.ROOT);
    }

    private static List<Change> gitDiff(Path root, String from, String to) {
        String prefix = gitPrefix(root);
        Cli.Result r = ok(Cli.run(Cli.Exe.GIT, List.of("-c", "core.quotepath=false", "diff", "--name-status", "--no-renames", "-z",
                from + ".." + to, "--", "."), root));
        List<Change> out = new ArrayList<>();
        String[] tok = r.text().split("\0");
        for (int i = 0; i + 1 < tok.length; i += 2) {
            String st = tok[i].isEmpty() ? "M" : tok[i].substring(0, 1);
            String path = tok[i + 1];
            if (!path.startsWith(prefix)) {
                continue;
            }
            out.add(new Change(path.substring(prefix.length()), st.equals("A") || st.equals("D") ? st : "M"));
        }
        return out;
    }

    private static List<Change> svnDiff(Path root, String from, String to) {
        return svnDiffs(ok(Cli.run(Cli.Exe.SVN, List.of("diff", "--summarize", "--xml", "--non-interactive", "-r", from + ":" + to, "."), root)).out());
    }

    /** `svn diff --summarize --xml` 출력 → 구간. 픽스처 테스트도 이 길로 */
    static List<Change> svnDiffs(byte[] xml) {
        List<Change> out = new ArrayList<>();
        parseXml(xml, new DefaultHandler() {
            private String kind;
            private String item;
            private StringBuilder text;

            @Override
            public void startElement(String uri, String local, String q, Attributes a) {
                if (q.equals("path")) {
                    kind = a.getValue("kind");
                    item = a.getValue("item");
                    text = new StringBuilder();
                }
            }

            @Override
            public void characters(char[] ch, int start, int len) {
                if (text != null) {
                    text.append(ch, start, len);
                }
            }

            @Override
            public void endElement(String uri, String local, String q) {
                if (q.equals("path") && text != null) {
                    String st = switch (item == null ? "" : item) {
                        case "added" -> "A";
                        case "deleted" -> "D";
                        case "modified" -> "M";
                        default -> null;
                    };
                    String rel = svnRel(text.toString().strip());
                    if ("file".equals(kind) && st != null && !rel.isEmpty()) {
                        out.add(new Change(rel, st));
                    }
                    text = null;
                }
            }
        });
        return out;
    }

    // ---------------------------------------------------------------- 최근 커밋

    /** git 만. svn 은 서버를 타야 해서 자동으로 부르지 않는다 — 빈 목록 */
    public static List<Rev> recent(Path root, int n) {
        Info info = detect(root);
        if (info.kind() != Kind.GIT || !info.available()) {
            return List.of();
        }
        Cli.Result r = ok(Cli.run(Cli.Exe.GIT, List.of("-c", "core.quotepath=false", "log", "-n", String.valueOf(Math.max(1, Math.min(n, 100))),
                "--format=%h%x09%ad%x09%s", "--date=short", "--", "."), root));
        List<Rev> out = new ArrayList<>();
        for (String line : r.text().split("\n")) {
            String[] p = line.split("\t", 3);
            if (p.length == 3) {
                out.add(new Rev(p[0], p[1], p[2]));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 공통

    private static Cli.Result ok(java.util.Optional<Cli.Result> r) {
        Cli.Result res = r.orElseThrow(() -> new IllegalStateException("형상 관리 명령을 못 찾았다(PATH)"));
        if (!res.ok()) {
            String why = res.err().isEmpty() ? "종료 코드 " + res.exit() : res.err();
            throw new IllegalStateException(why);
        }
        return res;
    }

    /** svn --xml 읽기 — DOCTYPE 거부, 외부 DTD·엔티티를 안 읽는다(규칙 1, {@link SafeSax}) */
    static void parseXml(byte[] xml, DefaultHandler h) {
        try {
            InputSource in = new InputSource(new ByteArrayInputStream(xml));
            in.setEncoding(StandardCharsets.UTF_8.name());
            SafeSax.parse(in, h, false);
        } catch (SAXException | IOException e) {
            throw new IllegalStateException("svn XML 을 못 읽었다: " + e.getMessage(), e);
        }
    }
}
