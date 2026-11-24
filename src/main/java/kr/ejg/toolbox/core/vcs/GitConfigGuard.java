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
    private static final Set<String> PATH_KEYS = Set.of("core.excludesfile", "core.attributesfile", "core.hookspath", "core.worktree", "mailmap.file");
    private static final String GITDIR = "gitdir:";

    private GitConfigGuard() {
    }

    /** dotGit — 작업 사본의 .git(폴더 또는 gitdir: 파일). 걸리면 IllegalStateException(사유) */
    static void check(Path dotGit) {
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
        String alt = optional(common.resolve("objects/info/alternates"));
        if (alt != null) {
            alt.lines().forEach(GitConfigGuard::refuseNetwork);
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
        for (String raw : config.split("\r?\n|\r", -1)) {
            String line = raw.strip();
            if (line.startsWith("[")) {
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
            refuseNetwork(line.substring(eq + 1));
        }
    }

    private static void refuseNetwork(String value) {
        if (network(value)) {
            throw new IllegalStateException(REMOTE_PATH);
        }
    }

    /** 앞뒤 공백·큰따옴표를 벗기고 / 를 \ 로 바꿔 \\ 로 시작하나 */
    static boolean network(String value) {
        String v = value.strip();
        if (v.startsWith("\"")) {
            v = v.substring(1).strip();
        }
        return v.replace('/', '\\').startsWith("\\\\");
    }

    /** 있으면 글, 없으면 null */
    private static String optional(Path f) {
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
