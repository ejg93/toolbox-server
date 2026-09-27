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
import java.nio.file.StandardCopyOption;
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
        Path root = check(dir);
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
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                if (!a.isRegularFile() || hidden(f)) {
                    return FileVisitResult.CONTINUE;
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
     * 고른 폴더 안의 있는 파일을 되쓴다. 원본을 {@code backupRoot/<root 기준 상대 경로>} 에 먼저 복사하고(이미 있으면 첫 원본을 둔다),
     * 복사가 안 되면 안 쓴다.
     *
     * @return 백업 파일 경로
     */
    public Path write(String path, String root, String text, String encoding, String lineEnding, Path backupRoot) throws IOException {
        Path r = check(root);
        Path p = check(path);
        if (!under(p, r) || p.equals(r)) {
            throw new Refused(400, "고른 폴더 밖에는 안 쓴다");
        }
        if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) {
            throw new Refused(404, "파일이 없다 — 있는 파일만 되쓴다");
        }
        // 폴더 안의 정션·링크가 밖을 가리키면 이름으로는 안인데 실제로는 밖이다 — 실제 경로로 한 번 더
        if (!under(p.toRealPath(), r.toRealPath())) {
            throw new Refused(400, "고른 폴더 밖에는 안 쓴다(링크)");
        }
        byte[] body = encode(text == null ? "" : text, encoding, lineEnding);
        Path backup = backupRoot.resolve(r.relativize(p).toString()).normalize();
        Path backupDir = backup.getParent();
        if (backupDir == null || !backup.startsWith(backupRoot.normalize())) {
            throw new Refused(400, "백업 경로를 못 만들었다");
        }
        try {
            Files.createDirectories(backupDir);
            if (!Files.exists(backup)) {
                Files.copy(p, backup, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException e) {
            throw new IOException("백업을 못 만들어 쓰지 않았다", e);
        }
        Files.write(p, body);
        return backup;
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
