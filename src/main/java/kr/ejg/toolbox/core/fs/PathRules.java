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
