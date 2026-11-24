package kr.ejg.toolbox.core.fs;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** fs 층의 경로·글롭 꼴 판정(0-110·0-111) — LocalFiles 가 메서드 상한에 가까워 따로 둔다 */
final class PathRules {

    /** GetDriveType 의 DRIVE_REMOTE(네트워크 드라이브) */
    static final int DRIVE_REMOTE = 4;
    static final String DRIVE_FAIL = "드라이브 종류를 확인할 수 없다 — run.bat·toolbox.bat(또는 java -jar app.jar)로 띄운다";

    /** 이름 글롭 하나의 * 묶음 상한 — JDK 글롭 정규식의 역추적은 별 수에 지수다(G1-K13) */
    static final int MAX_STARS = 3;
    private static final Pattern STARS = Pattern.compile("\\*+");
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:.*");
    /** 조각에 못 드는 글자 — Windows 금지 글자와 제어 글자(G1-K06) */
    private static final Pattern BAD_CHAR = Pattern.compile("[<>:\"|?*\\x00-\\x1f]");
    /** 0-131 — 드라이브 상대 경로 머리(X: 뒤가 구분자가 아니다 — X:foo · X:) */
    private static final Pattern DRIVE_REL = Pattern.compile("[A-Za-z]:(?![/\\\\])");
    /** 0-127 — Windows 예약 장치 이름(확장자가 붙어도) — 쓰면 파일이 아니라 장치로 간다 */
    private static final Pattern DEVICE = Pattern.compile("(?i)(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?");

    private PathRules() {
    }

    /** 파일 이름 글롭 → 매처. 문법 오류·별 묶음이 MAX_STARS 를 넘으면 Refused(400)(G1-K04·K13) */
    static PathMatcher nameMatcher(String glob) {
        Matcher m = STARS.matcher(glob);
        int n = 0;
        while (m.find()) {
            n++;
        }
        if (n > MAX_STARS) {
            throw new LocalFiles.Refused(400, "글롭의 * 는 " + MAX_STARS + "개까지: " + glob);
        }
        try {
            return FileSystems.getDefault().getPathMatcher("glob:" + glob);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            throw new LocalFiles.Refused(400, "글롭을 못 읽었다: " + glob, e);
        }
    }

    /**
     * 결과 폴더 안 상대 경로인가 — 비었거나 {@code /} 로 시작·드라이브 꼴·{@code ..} 조각은 아니다. 조각마다 빈 조각 · 끝이 {@code .} 이나 공백
     * (Windows 가 떼어 다른 이름이 된다 — 표식 파일을 덮는다) · 금지 글자·제어 글자가 없어야 한다(G1-K06·K08)
     */
    static boolean relOk(String r) {
        if (r.isEmpty() || r.startsWith("/") || DRIVE.matcher(r).matches()) {
            return false;
        }
        for (String seg : r.split("/", -1)) {
            if (seg.isEmpty() || "..".equals(seg) || seg.endsWith(".") || seg.endsWith(" ") || BAD_CHAR.matcher(seg).find()
                    || DEVICE.matcher(seg).matches()) {
                return false;
            }
        }
        return true;
    }

    /** 정션·링크로 다른 곳을 가리키는 폴더인가 — 속성이 other·link 이거나 실제 경로가 자기와 다르다. 못 읽으면 참(G1-K12) */
    static boolean linked(Path dir, BasicFileAttributes a) {
        if (a.isSymbolicLink() || a.isOther()) {
            return true;
        }
        try {
            Path real = dir.toRealPath();
            Path parent = dir.getParent();
            return parent != null && !real.equals(parent.toRealPath().resolve(dir.getFileName()));
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * 0-121(G-01) — p(또는 가장 가까운 있는 조상)가 dir 이거나 그 아래인가 — 조상으로 올라가며 {@link Files#isSameFile}(파일 키 — 볼륨 일련번호 + 파일 번호).
     * subst·8.3·대소문자·정션 표기를 가리지 않는다. dir 이 없거나 못 읽으면 거짓
     */
    /** 0-129 — a 가 dir 과 같은 파일인가(파일 키) — 없거나 못 읽으면 거짓 */
    static boolean same(Path a, Path dir) {
        try {
            return dir != null && Files.exists(a) && Files.exists(dir) && Files.isSameFile(a, dir);
        } catch (IOException e) {
            return false;
        }
    }

    static boolean sameOrInside(Path p, Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return false;
        }
        for (Path a = p == null ? null : p.toAbsolutePath(); a != null; a = a.getParent()) {
            try {
                if (Files.exists(a) && Files.isSameFile(a, dir)) {
                    return true;
                }
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    /** 가장 가까운 「있는」 조상(자기 포함). 없으면 null */
    static Path nearest(Path p) {
        Path near = p;
        while (near != null && !Files.exists(near)) {
            near = near.getParent();
        }
        return near;
    }

    /** 0-125a(G-10) — 드라이브 종류가 원격(4 = DRIVE_REMOTE)인가 */
    static boolean remote(int type) {
        return type == DRIVE_REMOTE;
    }

    /**
     * 0-125a(G-10, 절대 규칙 1) — 경로의 뿌리가 드라이브 글자(X:\)이고 OS 가 그 드라이브를 원격(매핑된 네트워크 드라이브)이라 답하나.
     * 뿌리가 그 꼴이 아니거나 종류를 모르면 거짓 · 못 읽는 경로도 거짓(뒤 판정이 거절한다)
     */
    static boolean remoteDrive(String raw) {
        Path root;
        String in = raw.trim();
        try {
            // 0-131(G4-06) — 드라이브 상대 경로(Q:foo)는 머리 두 글자로 뿌리를 구한다(없는 드라이브의 toAbsolutePath 는 IOError 를 던진다)
            root = DRIVE_REL.matcher(in).lookingAt() ? Path.of(in.substring(0, 2) + "\\") : Path.of(in).toAbsolutePath().getRoot();
        } catch (InvalidPathException e) {
            return false;
        }
        String r = root == null ? "" : root.toString();
        if (r.length() != 3 || r.charAt(1) != ':' || !Character.isLetter(r.charAt(0))) {
            return false;
        }
        OptionalInt t = driveType(r);
        return t.isPresent() && remote(t.getAsInt());
    }

    /**
     * 0-125a — OS 의 드라이브 종류(Win32 GetDriveType — JDK 내부 sun.nio.fs.WindowsNativeDispatcher, 새 의존성 없음). Windows 가 아니면 빈 값.
     * 열 수 없으면(Add-Opens 없이 띄움) {@link IllegalStateException} — 닫힌 실패. OS 가 거절한 뿌리는 빈 값
     */
    static OptionalInt driveType(String root) {
        Class<?> c;
        try {
            c = Class.forName("sun.nio.fs.WindowsNativeDispatcher");
        } catch (ClassNotFoundException e) {
            return OptionalInt.empty();
        }
        java.util.function.ToIntFunction<String> f;
        try {
            // privateLookupIn 은 그 패키지가 열려 있어야 된다(Add-Opens) — setAccessible 없이
            java.lang.invoke.MethodHandle mh = java.lang.invoke.MethodHandles.privateLookupIn(c, java.lang.invoke.MethodHandles.lookup())
                    .findStatic(c, "GetDriveType", java.lang.invoke.MethodType.methodType(int.class, String.class));
            f = toInt(mh);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new IllegalStateException(DRIVE_FAIL, e);
        }
        try {
            return OptionalInt.of(f.applyAsInt(root));
        } catch (java.lang.reflect.UndeclaredThrowableException e) {
            return OptionalInt.empty(); // OS 가 거절한 뿌리(WindowsException)
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.function.ToIntFunction<String> toInt(java.lang.invoke.MethodHandle mh) {
        return java.lang.invoke.MethodHandleProxies.asInterfaceInstance(java.util.function.ToIntFunction.class, mh);
    }

    /** 0-121(G-18) — 결과 폴더 만들기. 쓰기 실패(없는 드라이브·권한)는 500 대신 400 */
    static void mkdirs(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new LocalFiles.Refused(400, "결과 폴더에 못 썼다 — " + e.getClass().getSimpleName(), e);
        }
    }

    /** 0-121(G-18) — 결과 파일 쓰기. 실패(잠긴 파일 등)는 400 */
    static void put(Path p, byte[] body) {
        try {
            Files.write(p, body);
        } catch (IOException e) {
            throw new LocalFiles.Refused(400, "결과 폴더에 못 썼다 — " + e.getClass().getSimpleName(), e);
        }
    }
}
