package kr.ejg.toolbox.core.fs;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** fs 층의 경로·글롭 꼴 판정(0-110·0-111) — LocalFiles 가 메서드 상한에 가까워 따로 둔다 */
final class PathRules {

    /** 이름 글롭 하나의 * 묶음 상한 — JDK 글롭 정규식의 역추적은 별 수에 지수다(G1-K13) */
    static final int MAX_STARS = 3;
    private static final Pattern STARS = Pattern.compile("\\*+");
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:.*");
    /** 조각에 못 드는 글자 — Windows 금지 글자와 제어 글자(G1-K06) */
    private static final Pattern BAD_CHAR = Pattern.compile("[<>:\"|?*\\x00-\\x1f]");

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
            if (seg.isEmpty() || "..".equals(seg) || seg.endsWith(".") || seg.endsWith(" ") || BAD_CHAR.matcher(seg).find()) {
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
}
