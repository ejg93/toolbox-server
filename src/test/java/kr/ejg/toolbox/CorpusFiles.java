package kr.ejg.toolbox;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * 실물 표본(V-1, PLAN 4장). 표본은 저장소 밖 폴더 — `TOOLBOX_CORPUS`, 없으면 저장소 형제 `toolbox-corpus`.
 * 폴더가 없으면 건너뛰지 않고 실패한다(로컬 `verify --full` 이 게이트다. CI 는 `corpus` 태그를 뺀다).
 * 쓰기 전에 {@link #verify()} 가 `corpus/MANIFEST`(출처별 지문)와 대조한다 — 표본이 바뀌면 baseline 이 뜻을 잃는다.
 */
public final class CorpusFiles {

    public static final Path MANIFEST = Path.of("corpus/MANIFEST");
    /** 등급 B baseline 이 표본의 이 비율을 넘으면 실패 */
    public static final double B_LIMIT = 0.02;
    /** 실패 보고에 싣는 줄 수 — 나머지는 건수만(로그·토큰) */
    public static final int REPORT = 20;

    private static Path root;
    private static boolean verified;

    private CorpusFiles() {
    }

    public static synchronized Path root() {
        if (root == null) {
            String env = System.getenv("TOOLBOX_CORPUS");
            Path p = env != null && !env.isBlank() ? Path.of(env) : Path.of("").toAbsolutePath().getParent().resolve("toolbox-corpus");
            if (!Files.isDirectory(p)) {
                fail("실물 표본 폴더가 없다: " + p + " — bash scripts/corpus-fetch.sh 먼저(PLAN 4장)");
            }
            root = p;
        }
        return root;
    }

    /** 출처별 지문을 MANIFEST 와 대조. 한 JVM 에서 한 번 */
    public static synchronized void verify() throws IOException {
        if (verified) {
            return;
        }
        Map<String, String> want = manifest(MANIFEST);
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, String> e : want.entrySet()) {
            String got = fingerprint(root().resolve(e.getKey()));
            if (!e.getValue().equals(got)) {
                bad.add(e.getKey() + " 지문 " + e.getValue() + " ≠ " + got);
            }
        }
        if (!bad.isEmpty()) {
            fail("표본이 MANIFEST 와 다르다 — corpus-fetch.sh 로 다시 받거나, 의도한 갱신이면 MANIFEST 를 커밋: " + bad);
        }
        verified = true;
    }

    /** 「이름 파일수 sha256」 → 이름 → 「파일수 sha256」 */
    static Map<String, String> manifest(Path file) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] p = line.trim().split("\\s+");
            out.put(p[0], p[1] + " " + p[2]);
        }
        return out;
    }

    /** corpus-fetch.sh 와 같은 셈 — 「sha256  상대경로」 줄(바이트 그대로 해시, 경로 정렬)의 목록 해시 */
    static String fingerprint(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return "없음";
        }
        TreeSet<String> rels = new TreeSet<>(CorpusFiles::byteOrder);
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(Files::isRegularFile).forEach(p -> rels.add(dir.relativize(p).toString().replace('\\', '/')));
        }
        StringBuilder list = new StringBuilder();
        for (String rel : rels) {
            list.append(sha256(Files.readAllBytes(dir.resolve(rel)))).append("  ").append(rel).append('\n');
        }
        return rels.size() + " " + sha256(list.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** LC_ALL=C 정렬 = UTF-8 바이트 순 */
    static int byteOrder(String a, String b) {
        return java.util.Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] b) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 출처 폴더 아래 glob(파일 이름 또는 상대 경로) 에 맞는 파일 — 상대 경로 정렬 */
    public static List<Path> files(String source, String... globs) throws IOException {
        verify();
        Path dir = root().resolve(source);
        List<PathMatcher> ms = new ArrayList<>();
        for (String g : globs) {
            ms.add(FileSystems.getDefault().getPathMatcher("glob:" + g));
        }
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> ms.isEmpty() || ms.stream().anyMatch(m -> m.matches(p.getFileName()) || m.matches(dir.relativize(p))))
                    .sorted()
                    .toList();
        }
    }

    /** 표본 루트 기준 상대 경로(`/`) — baseline·보고용 */
    public static String rel(Path p) {
        return root().relativize(p).toString().replace('\\', '/');
    }

    /**
     * 등급 B baseline(PLAN 4장). `golden/corpus/<이름>.txt`(상대 경로 한 줄씩)와 지금 깨진 목록을 대조한다.
     * 새로 깨짐·새로 고쳐짐 둘 다 실패(목록 갱신 요구), 표본의 {@link #B_LIMIT} 넘으면 실패.
     * {@code -Dgolden.update=true} 면 쓰고 상한만 본다.
     */
    public static void baseline(String name, List<String> broken, int total) throws IOException {
        baseline(GoldenFiles.DIR.resolve("corpus").resolve(name + ".txt"), name, broken, total, GoldenFiles.updating());
    }

    static void baseline(Path file, String name, List<String> broken, int total, boolean update) throws IOException {
        TreeSet<String> now = new TreeSet<>(broken);
        String over = now.size() > total * B_LIMIT
                ? name + ": 등급 B " + now.size() + "/" + total + " — 상한 " + (int) (B_LIMIT * 100) + "% 초과. " + head(now)
                : null;
        if (update) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, now.isEmpty() ? "" : String.join("\n", now) + "\n", StandardCharsets.UTF_8);
            if (over != null) {
                fail(over);
            }
            return;
        }
        if (!Files.exists(file)) {
            fail(name + ": baseline 이 없다 — -Dgolden.update=true 로 만들고 diff 를 이력에. 지금 " + now.size() + "건 " + head(now));
        }
        TreeSet<String> was = new TreeSet<>();
        for (String l : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!l.isBlank()) {
                was.add(l.trim());
            }
        }
        TreeSet<String> added = new TreeSet<>(now);
        added.removeAll(was);
        TreeSet<String> fixed = new TreeSet<>(was);
        fixed.removeAll(now);
        List<String> msg = new ArrayList<>();
        if (!added.isEmpty()) {
            msg.add("새로 깨짐 " + added.size() + " " + head(added));
        }
        if (!fixed.isEmpty()) {
            msg.add("새로 고쳐짐 " + fixed.size() + " — baseline 갱신 " + head(fixed));
        }
        if (over != null) {
            msg.add(over);
        }
        if (!msg.isEmpty()) {
            fail(name + ": " + String.join(" / ", msg));
        }
    }

    /** 등급 A — 비어야 한다 */
    public static void none(String what, List<String> broken, int total) {
        if (!broken.isEmpty()) {
            fail(what + ": 등급 A " + broken.size() + "/" + total + " — baseline 없이 0 이어야 한다. " + head(broken));
        }
    }

    static String head(java.util.Collection<String> c) {
        List<String> l = c.stream().limit(REPORT).toList();
        return l + (c.size() > REPORT ? " 외 " + (c.size() - REPORT) : "");
    }
}
