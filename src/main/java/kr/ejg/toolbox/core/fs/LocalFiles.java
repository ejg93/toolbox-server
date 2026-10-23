package kr.ejg.toolbox.core.fs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 로컬 파일 층(4-1, 5-5). 서버가 사용자 PC 의 파일을 직접 읽고 쓴다 — 폴더 비교·주석 삭제 폴더·jsp 폴더 일괄이 같이 쓴다.
 * <ul>
 *   <li>경로는 절대 경로만. Windows·Program Files·서버 자기 {@code data/} 안은 거절(읽기·쓰기 모두)</li>
 *   <li>읽기: BOM → UTF-8 엄격 → MS949({@code Csv.decode} 와 같은 규칙). 글자는 LF 로 돌려주고 줄바꿈은 따로 알린다</li>
 *   <li>쓰기: 고른 폴더({@code root}) 안의 있는 파일만. 원본을 먼저 백업하고, 백업이 안 되면 안 쓴다. 읽은 인코딩·줄바꿈 그대로</li>
 *   <li>최근 목록은 경로만 남긴다 — 파일 내용은 어디에도 안 남긴다(절대 규칙 3)</li>
 * </ul>
 */
public final class LocalFiles {

    public static final int MAX_FILES = 20_000;
    public static final int RECENT_MAX = 20;
    public static final String UTF8_BOM = "UTF-8-BOM";
    public static final String UTF8 = "UTF-8";
    public static final String MS949 = "MS949";
    public static final String CRLF = "CRLF";
    public static final String LF = "LF";
    /** 결과 폴더 표식(9-3a) — 이 도구가 만든 폴더라는 뜻. 있으면 비어 있지 않아도 다시 쓴다 */
    public static final String MARK = "결과_출처.txt";

    private static final Set<String> SKIP_DIRS = Set.of(".git", "target", "node_modules");
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** 거절 — 상태 코드(400·404)와 사유. 사유에 파일 내용은 없다 */
    public static final class Refused extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int status;

        public Refused(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    public record Entry(String rel, long size, long mtime) {
    }

    public record Listing(List<Entry> files, boolean truncated) {
        public Listing {
            files = List.copyOf(files);
        }
    }

    public record Text(String text, String encoding, String lineEnding) {
    }

    private final Path dataDir;
    private final List<Path> forbidden;

    /** @param dataDir 서버 자기 {@code data/} — 거절 목록에 들고, 최근 목록 파일이 여기 있다 */
    public LocalFiles(Path dataDir) {
        this.dataDir = dataDir.toAbsolutePath().normalize();
        List<Path> f = new ArrayList<>();
        f.add(this.dataDir);
        for (String env : new String[] {"WINDIR", "SystemRoot", "ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"}) {
            String v = System.getenv(env);
            if (v != null && !v.isBlank()) {
                f.add(Path.of(v).toAbsolutePath().normalize());
            }
        }
        this.forbidden = List.copyOf(f);
    }

    /** 사용자가 준 경로 → 절대·정규화 경로. 거절이면 {@link Refused} */
    public Path check(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new Refused(400, "path 가 있어야 한다");
        }
        Path p;
        try {
            p = Path.of(raw.trim());
        } catch (InvalidPathException e) {
            throw new Refused(400, "경로를 못 읽었다");
        }
        if (!p.isAbsolute()) {
            throw new Refused(400, "절대 경로만 받는다");
        }
        // UNC(\\서버\공유)·장치 경로(\\?\ \\.\) — 서버가 파일을 열면 SMB 로 127.0.0.1 밖에 나간다(절대 규칙 1). exists 전에 막는다
        String root = String.valueOf(p.getRoot()).replace('/', '\\');
        if (raw.trim().replace('/', '\\').startsWith("\\\\") || root.startsWith("\\\\")) {
            throw new Refused(400, "네트워크·장치 경로는 받지 않는다 — 로컬 드라이브만");
        }
        p = p.normalize();
        refuseForbidden(p);
        if (Files.exists(p)) {
            try {
                refuseForbidden(p.toRealPath()); // 정션·링크로 돌아 들어가는 것
            } catch (IOException e) {
                throw new Refused(400, "경로를 못 읽었다");
            }
        }
        return p;
    }

    private void refuseForbidden(Path p) {
        for (Path f : forbidden) {
            if (under(p, f)) {
                throw new Refused(400, "시스템·서버 폴더는 다루지 않는다");
            }
        }
    }

    /** Windows 는 대소문자를 안 가린다 */
    static boolean under(Path p, Path root) {
        String a = p.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        String b = root.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return a.equals(b) || a.startsWith(b + "/");
    }

    /** 폴더 아래 파일 — 상대 경로({@code /}) 정렬. 숨김·.git·target·node_modules 는 뺀다. globs 가 비면 전부 */
    public Listing list(String dir, List<String> globs, int maxFiles) throws IOException {
        return list(dir, globs, List.of(), maxFiles);
    }

    /**
     * excludes — 루트 기준 상대 경로({@code /})에 맞춰 뺄 글롭(5-26). {@code **} 는 폴더를 넘고 {@code *}·{@code ?} 는 한 조각 안,
     * 대소문자 무시, 맨 앞 {@code **}{@code /} 는 없어도 되고(루트 바로 아래도 맞음) 가운데 {@code /}{@code **}{@code /} 는 0단계 폴더도 맞는다. 폴더가 맞으면 아래를 통째로 건너뛴다
     */
    public Listing list(String dir, List<String> globs, List<String> excludes, int maxFiles) throws IOException {
        Path root = check(dir);
        List<java.util.regex.Pattern> ex = new ArrayList<>();
        for (String g : excludes) {
            if (g != null && !g.isBlank()) {
                ex.add(glob(g.trim()));
            }
        }
        if (!Files.isDirectory(root)) {
            throw new Refused(404, "폴더가 없다");
        }
        List<PathMatcher> ms = new ArrayList<>();
        for (String g : globs) {
            if (g != null && !g.isBlank()) {
                ms.add(FileSystems.getDefault().getPathMatcher("glob:" + g.trim()));
            }
        }
        List<Entry> out = new ArrayList<>();
        boolean[] truncated = {false};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                if (!d.equals(root) && (hidden(d) || SKIP_DIRS.contains(String.valueOf(d.getFileName())))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!d.equals(root) && !ex.isEmpty()) {
                    String rel = root.relativize(d).toString().replace('\\', '/');
                    if (ex.stream().anyMatch(p -> p.matcher(rel).matches() || p.matcher(rel + "/").matches())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                if (!a.isRegularFile() || hidden(f)) {
                    return FileVisitResult.CONTINUE;
                }
                if (!ex.isEmpty()) {
                    String rel = root.relativize(f).toString().replace('\\', '/');
                    if (ex.stream().anyMatch(p -> p.matcher(rel).matches())) {
                        return FileVisitResult.CONTINUE;
                    }
                }
                Path name = f.getFileName();
                if (!ms.isEmpty() && ms.stream().noneMatch(m -> m.matches(name))) {
                    return FileVisitResult.CONTINUE;
                }
                if (out.size() >= maxFiles) {
                    truncated[0] = true;
                    return FileVisitResult.TERMINATE;
                }
                out.add(new Entry(root.relativize(f).toString().replace('\\', '/'), a.size(), a.lastModifiedTime().toMillis()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path f, IOException e) {
                return FileVisitResult.CONTINUE; // 권한 없는 폴더는 건너뛴다
            }
        });
        out.sort(Comparator.comparing(Entry::rel));
        return new Listing(out, truncated[0]);
    }

    /** 상대 경로 글롭 → 정규식(코드 검사 규칙 글롭과 같은 뜻 + 맨 앞 {@code **}{@code /} 는 비어도 됨) */
    static java.util.regex.Pattern glob(String g) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        if (g.startsWith("**/")) {
            sb.append("(?:.*/)?");
            i = 3;
        }
        for (; i < g.length(); i++) {
            char c = g.charAt(i);
            if (c == '/' && g.startsWith("/**/", i)) {
                sb.append("/(?:.*/)?"); // 가운데 /**/ 는 0단계 폴더도(src/**/gen/** 가 src/gen/x 에 맞게)
                i += 3;
            } else if (c == '*' && i + 1 < g.length() && g.charAt(i + 1) == '*') {
                sb.append(".*");
                i++;
            } else if (c == '*') {
                sb.append("[^/]*");
            } else if (c == '?') {
                sb.append("[^/]");
            } else {
                sb.append(java.util.regex.Pattern.quote(String.valueOf(c)));
            }
        }
        return java.util.regex.Pattern.compile(sb.toString(), java.util.regex.Pattern.CASE_INSENSITIVE);
    }

    private static boolean hidden(Path p) {
        String n = String.valueOf(p.getFileName());
        if (n.startsWith(".")) {
            return true;
        }
        try {
            return Files.isHidden(p);
        } catch (IOException e) {
            return false;
        }
    }

    public Text read(String path) throws IOException {
        Path p = check(path);
        if (!Files.isRegularFile(p)) {
            throw new Refused(404, "파일이 없다");
        }
        byte[] b = Files.readAllBytes(p);
        String enc;
        String text;
        if (b.length >= 3 && b[0] == BOM[0] && b[1] == BOM[1] && b[2] == BOM[2]) {
            enc = UTF8_BOM;
            text = new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
        } else {
            try {
                text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(b)).toString();
                enc = UTF8;
            } catch (CharacterCodingException e) {
                text = new String(b, Charset.forName(MS949));
                enc = MS949;
            }
        }
        return new Text(text.replace("\r\n", "\n"), enc, lineEnding(text));
    }

    /** CRLF 가 LF 단독보다 많으면 CRLF. 줄바꿈이 없으면 LF */
    static String lineEnding(String text) {
        int crlf = 0;
        int lf = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                if (i > 0 && text.charAt(i - 1) == '\r') {
                    crlf++;
                } else {
                    lf++;
                }
            }
        }
        return crlf > lf ? CRLF : LF;
    }

    /**
     * 결과 폴더(9-3a, 절대 규칙 7) — 고친 파일을 원본 대신 쓰는 곳. {@code given} 이 비면 {@code defaultRoot}.
     * 거절(400): 원본 폴더 안·원본 자체·원본을 품는 폴더(이름으로·실제 경로로 둘 다) · 폴더가 아님 · 비어 있지 않은데 표식이 없음.
     * 통과하면 폴더를 만들고 표식이 없으면 쓴다(있는 표식은 안 덮는다 — 첫 출처).
     *
     * @param source 원본 폴더(없으면 null — 원본과의 관계를 안 잰다)
     */
    public Path resultRoot(String given, Path defaultRoot, Path source, String feature, String stamp) throws IOException {
        Path out = check(given == null || given.isBlank() ? defaultRoot.toAbsolutePath().normalize().toString() : given);
        if (source != null) {
            Path s = source.toAbsolutePath().normalize();
            if (under(out, s) || under(s, out)) {
                throw new Refused(400, "결과 폴더는 원본 폴더 밖이어야 하고 원본을 품어도 안 된다");
            }
            if (Files.exists(s)) {
                Path rs = s.toRealPath();
                Path near = out;
                while (near != null && !Files.exists(near)) {
                    near = near.getParent();
                }
                Path rn = near == null ? null : near.toRealPath();
                if (rn != null && (under(rn, rs) || Files.exists(out) && under(rs, rn))) {
                    throw new Refused(400, "결과 폴더가 링크로 원본 폴더 안을 가리킨다");
                }
            }
        }
        if (Files.exists(out, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(out)) {
                throw new Refused(400, "결과 폴더 자리에 파일이 있다");
            }
            boolean empty;
            try (java.util.stream.Stream<Path> l = Files.list(out)) {
                empty = l.findAny().isEmpty();
            }
            if (!empty && !Files.isRegularFile(out.resolve(MARK))) {
                throw new Refused(400, "비어 있지 않은 폴더 — 결과 폴더는 빈 폴더이거나 이 도구가 만든 폴더(" + MARK + ")");
            }
        }
        Files.createDirectories(out);
        Path mark = out.resolve(MARK);
        if (!Files.exists(mark)) {
            String text = String.join("\r\n", "toolbox-server 결과 폴더", "기능 " + feature,
                    "원본 " + (source == null ? "-" : source.toAbsolutePath().normalize()), "시각 " + stamp,
                    "이 폴더의 파일은 원본 폴더의 같은 상대 경로 파일을 규칙대로 고친 것이다. 원본은 바뀌지 않았다. 반영은 사람이 비교해 옮긴다.") + "\r\n";
            Files.writeString(mark, text, StandardCharsets.UTF_8);
        }
        return out;
    }

    /**
     * 결과 폴더 안 {@code rel}(슬래시 구분 상대 경로)에 쓴다(9-3a). 덮어쓰기 허용 — 이 도구의 폴더다. 원본은 건드리지 않는다.
     * 거절(400): 빈·절대 경로·결과 폴더 밖({@code ..})·표식 자리·링크로 밖·인코딩으로 못 쓰는 글자
     *
     * @return 쓴 파일
     */
    public Path writeResult(Path outRoot, String rel, String text, String encoding, String lineEnding) throws IOException {
        String r = rel == null ? "" : rel.replace('\\', '/').trim();
        if (r.isEmpty() || r.startsWith("/") || r.matches("^[A-Za-z]:.*")) {
            throw new Refused(400, "결과 폴더 안 상대 경로만");
        }
        Path root = outRoot.toAbsolutePath().normalize();
        Path p = root.resolve(r).normalize();
        if (!under(p, root) || p.equals(root) || p.equals(root.resolve(MARK))) {
            throw new Refused(400, "결과 폴더 밖에는 안 쓴다");
        }
        byte[] body = encode(text == null ? "" : text, encoding, lineEnding);
        Path parent = p.getParent();
        Files.createDirectories(parent);
        if (!under(parent.toRealPath(), root.toRealPath()) || Files.isSymbolicLink(p)) {
            throw new Refused(400, "결과 폴더 밖에는 안 쓴다(링크)");
        }
        Files.write(p, body);
        return p;
    }

    /** 이 인코딩·줄바꿈으로 쓸 수 있나 — 못 쓰면 {@link Refused}(400). 쓰기 전에 미리 잰다(7-10) */
    public static void checkEncodable(String text, String encoding, String lineEnding) {
        encode(text == null ? "" : text, encoding, lineEnding);
    }

    static byte[] encode(String text, String encoding, String lineEnding) {
        String t = text.replace("\r\n", "\n");
        if (CRLF.equals(lineEnding)) {
            t = t.replace("\n", "\r\n");
        } else if (!LF.equals(lineEnding)) {
            throw new Refused(400, "lineEnding 은 CRLF·LF");
        }
        String enc = encoding == null ? "" : encoding;
        Charset cs = switch (enc) {
            case UTF8, UTF8_BOM -> StandardCharsets.UTF_8;
            case MS949 -> Charset.forName(MS949);
            default -> throw new Refused(400, "encoding 은 UTF-8-BOM·UTF-8·MS949");
        };
        ByteBuffer bb;
        try {
            bb = cs.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(t));
        } catch (CharacterCodingException e) {
            throw new Refused(400, enc + " 로 못 쓰는 글자가 있다 — 원본은 그대로 두었다");
        }
        byte[] b = new byte[bb.remaining()];
        bb.get(b);
        if (UTF8_BOM.equals(enc)) {
            byte[] withBom = new byte[b.length + 3];
            System.arraycopy(BOM, 0, withBom, 0, 3);
            System.arraycopy(b, 0, withBom, 3, b.length);
            return withBom;
        }
        return b;
    }

    public Exists exists(String path) {
        Path p = check(path);
        return new Exists(Files.exists(p), Files.isDirectory(p));
    }

    public record Exists(boolean exists, boolean dir) {
    }

    /** 최근 폴더 — 경로만, 새것이 앞. 파일이 깨졌으면 빈 목록 */
    public synchronized List<String> recent() {
        Path f = dataDir.resolve("recent-paths");
        try {
            return Files.readAllLines(f, StandardCharsets.UTF_8).stream().filter(s -> !s.isBlank()).limit(RECENT_MAX).toList();
        } catch (NoSuchFileException e) {
            return List.of();
        } catch (IOException e) {
            return List.of();
        }
    }

    public synchronized void remember(Path dir) {
        Set<String> s = new LinkedHashSet<>();
        s.add(dir.toString());
        s.addAll(recent());
        try {
            Files.createDirectories(dataDir);
            Files.write(dataDir.resolve("recent-paths"), s.stream().limit(RECENT_MAX).toList(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 최근 목록은 편의다 — 못 적어도 본 동작은 간다
        }
    }
}
