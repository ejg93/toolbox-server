package kr.ejg.toolbox.core.vcs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * 0-128(절대 규칙 1) — git 을 부르기 전에 저장소 설정 글을 자바로 읽어, git 이 스스로 여는 바깥 경로를 거절한다.
 * include·includeIf 구역은 통째 거절(대상 파일이 또 무엇을 가리킬지 모른다) · 경로를 여는 키(core.excludesfile·attributesfile·hookspath·worktree,
 * mailmap.file)의 값과 .git 파일의 gitdir·commondir·objects/info/alternates 줄이 네트워크 꼴(// 또는 \\ 로 시작)이면 거절.
 * 원격 주소(remote.*.url)는 안 본다 — 조회 명령(status·log·diff)은 원격에 안 닿는다
 */
final class GitConfigGuard {

    static final String INCLUDE = "저장소 설정에 include 가 있다 — git 조회를 하지 않는다";
    static final String REMOTE_PATH = "저장소 설정이 네트워크 경로를 가리킨다 — git 조회를 하지 않는다";
    static final String UNREADABLE = "저장소 설정을 읽을 수 없다 — git 조회를 하지 않는다";
    private static final long MAX = 1_048_576; // 설정 글 상한 — 넘으면 거절(읽지 않은 설정을 믿지 않는다)
    private static final Set<String> PATH_KEYS = Set.of("core.excludesfile", "core.attributesfile", "core.hookspath", "core.worktree", "mailmap.file",
            "diff.orderfile"); // 리뷰 I1 — status·diff 가 diffcore_order 로 연다
    private static final String GITDIR = "gitdir:";
    private static final int MAX_ALT = 5; // git 의 alternates 깊이 상한
    /** git 의 C 꼴 한 글자 탈출 → 같은 자리의 값 */
    private static final String C_ESCAPES = "\\\"abfnrtv";
    private static final String C_VALUES = "\\\"\u0007\b\f\n\r\t\u000B";

    private GitConfigGuard() {
    }

    /** dotGit — 작업 사본의 .git(폴더 또는 gitdir: 파일). 걸리면 IllegalStateException(사유) */
    static void check(Path dotGit) {
        refuseLink(dotGit);
        Path dir = Files.isRegularFile(dotGit) ? linked(dotGit) : dotGit;
        Path common = dir;
        String c = optional(dir.resolve("commondir"));
        if (c != null && !c.isBlank()) {
            String line = c.lines().findFirst().orElse("").strip();
            refuseNetwork(line);
            common = dir.resolve(line).normalize();
        }
        String cfg = optional(common.resolve("config"));
        checkText(cfg == null ? "" : cfg);
        String wt = optional(dir.resolve("config.worktree"));
        if (wt != null) {
            checkText(wt);
        }
        alternates(common.resolve("objects"), 0, new java.util.HashSet<>());
    }

    /** objects/info/alternates — 줄마다 다른 객체 폴더(상대면 이 objects 기준). 그 폴더의 info/alternates 도 git 처럼 깊이 MAX_ALT 까지 따라간다(0-132a) */
    private static void alternates(Path objects, int depth, Set<Path> seen) {
        String alt = depth > MAX_ALT || !seen.add(objects) ? null : optional(objects.resolve("info/alternates"));
        if (alt == null) {
            return;
        }
        for (String raw : alt.split("\r?\n|\r", -1)) {
            String s = raw.strip();
            if (s.isEmpty() || s.charAt(0) == '#') {
                continue;
            }
            String line = unquote(s);
            refuseNetwork(line);
            alternates(resolved(objects, line), depth + 1, seen);
        }
    }

    private static Path resolved(Path objects, String line) {
        try {
            return objects.resolve(line).toAbsolutePath().normalize();
        } catch (java.nio.file.InvalidPathException e) {
            throw new IllegalStateException(UNREADABLE, e);
        }
    }

    /**
     * 0-132a — alternates 줄의 따옴표 꼴을 git 처럼(C 꼴) 푼다: 한 글자 탈출(C_ESCAPES)과 8진 세 자리(첫 자리 0~3). 바이트를 UTF-8 로 읽는다.
     * " 로 안 시작하면 그대로 · 모르는 탈출·닫는 따옴표 없음은 UNREADABLE(닫힌 실패)
     */
    static String unquote(String s) {
        if (s.isEmpty() || s.charAt(0) != '"') {
            return s;
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int i = 1;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '"') {
                return out.toString(StandardCharsets.UTF_8);
            }
            if (ch == '\\') {
                i = escape(s, i + 1, out);
            } else {
                byte[] b = String.valueOf(ch).getBytes(StandardCharsets.UTF_8);
                out.write(b, 0, b.length);
                i++;
            }
        }
        throw new IllegalStateException(UNREADABLE);
    }

    /** 역슬래시 뒤 한 탈출을 풀어 out 에 쓰고 다음 자리를 돌려준다 */
    private static int escape(String s, int at, java.io.ByteArrayOutputStream out) {
        if (at >= s.length()) {
            throw new IllegalStateException(UNREADABLE);
        }
        char e = s.charAt(at);
        int simple = C_ESCAPES.indexOf(e);
        if (simple >= 0) {
            out.write(C_VALUES.charAt(simple));
            return at + 1;
        }
        if (e >= '0' && e <= '3' && at + 2 < s.length() && octal(s.charAt(at + 1)) && octal(s.charAt(at + 2))) {
            out.write(Integer.parseInt(s.substring(at, at + 3), 8));
            return at + 3;
        }
        throw new IllegalStateException(UNREADABLE);
    }

    private static boolean octal(char c) {
        return c >= '0' && c <= '7';
    }

    /** 0-132a(G4-C1) — 가드가 읽는 자리가 네트워크를 가리키는 링크면 읽지 않고 거절 */
    private static void refuseLink(Path f) {
        if (kr.ejg.toolbox.core.fs.LocalFiles.networkLink(f)) {
            throw new IllegalStateException(REMOTE_PATH);
        }
    }

    /** .git 이 파일 — 첫 줄 gitdir: 경로(상대면 그 파일의 부모 기준) */
    private static Path linked(Path dotGit) {
        String first = read(dotGit).lines().findFirst().orElse("").strip();
        String target = first.regionMatches(true, 0, GITDIR, 0, GITDIR.length()) ? first.substring(GITDIR.length()).strip() : first;
        refuseNetwork(target);
        Path abs = dotGit.toAbsolutePath();
        Path parent = abs.getParent();
        return (parent == null ? abs : parent).resolve(target).normalize();
    }

    /** 설정 글 한 덩이 — 줄마다 구역 머리([이름 "하위"])·키 = 값 을 읽는다. 주석(# ;)·빈 줄은 건너뛴다 */
    static void checkText(String config) {
        String section = "";
        String text = config.startsWith("\uFEFF") ? config.substring(1) : config; // 리뷰 I1 — git 은 머리 BOM 을 건너뛴다
        for (String raw : text.split("\r?\n|\r", -1)) {
            String line = raw.strip();
            while (line.startsWith("[")) { // 리뷰 I1 — 한 줄에 머리가 여럿이어도
                section = sectionName(line);
                if ("include".equals(section) || "includeif".equals(section)) {
                    throw new IllegalStateException(INCLUDE);
                }
                int close = line.indexOf(']');
                line = close < 0 ? "" : line.substring(close + 1).strip();
            }
            keyLine(section, line);
        }
    }

    /** [ 뒤 첫 공백·"·] 앞까지(소문자) */
    private static String sectionName(String line) {
        int end = 1;
        while (end < line.length() && " \t\"]".indexOf(line.charAt(end)) < 0) {
            end++;
        }
        return line.substring(1, end).toLowerCase(Locale.ROOT);
    }

    /** 키 = 값 한 줄 — 구역.키 가 경로를 여는 키이고 값이 네트워크 꼴이면 거절 */
    private static void keyLine(String section, String line) {
        if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == ';') {
            return;
        }
        int eq = line.indexOf('=');
        if (eq < 0) {
            return;
        }
        String key = section + "." + line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
        if (PATH_KEYS.contains(key)) {
            String value = line.substring(eq + 1).strip();
            if (value.endsWith("\\")) { // 리뷰 I1 — 다음 줄로 잇는 값은 이어 붙인 글을 못 믿는다(닫힌 실패)
                throw new IllegalStateException(UNREADABLE);
            }
            refuseNetwork(value);
        }
    }

    private static void refuseNetwork(String value) {
        if (network(value)) {
            throw new IllegalStateException(REMOTE_PATH);
        }
    }

    /** 큰따옴표를 전부 지우고(git 은 따옴표를 토글로 읽는다 — 리뷰 I1) 앞뒤 공백을 벗겨 / 를 \ 로 바꿔 \\ 로 시작하나 · fs 판정(0-132a) */
    static boolean network(String value) {
        return network(value, kr.ejg.toolbox.core.fs.LocalFiles::network);
    }

    /** 큰따옴표를 전부 지우고 // · \\ 머리거나, fs 판정(장치 경로 · 매핑된 네트워크 드라이브 — 0-132a)이 참 */
    static boolean network(String value, java.util.function.Predicate<String> fs) {
        String v = value.replace("\"", "").strip();
        return v.replace('/', '\\').startsWith("\\\\") || fs.test(v);
    }

    /** 있으면 글, 없으면 null */
    private static String optional(Path f) {
        refuseLink(f);
        return Files.isRegularFile(f) ? read(f) : null;
    }

    private static String read(Path f) {
        try {
            if (Files.size(f) > MAX) {
                throw new IllegalStateException(UNREADABLE);
            }
            return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(UNREADABLE, e);
        }
    }
}
