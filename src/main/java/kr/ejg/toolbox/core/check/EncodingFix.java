package kr.ejg.toolbox.core.check;

import java.util.Set;
import kr.ejg.toolbox.core.fs.LocalFiles;

/** file.encoding 고침(설계 62)의 거절 판정 — 글은 그대로 두고 쓸 인코딩만 프로필 것으로 바꿀 수 있나. Cleanup 이 단계를 돌린다 */
final class EncodingFix {
    /** 쓸 수 있는 이름 — LocalFiles 가 받는 셋 */
    private static final Set<String> WRITABLE = Set.of(LocalFiles.UTF8, LocalFiles.UTF8_BOM, LocalFiles.MS949);
    /** 못 읽은 바이트 자리 — MS949 로 읽은 글에 있으면 판별을 못 믿는다 */
    private static final char REPLACEMENT = '�';

    private EncodingFix() {
    }

    /** 프로필 인코딩을 LocalFiles 이름으로(EUC-KR → MS949). 없으면 빈 글 */
    static String target(String profileEncoding) {
        return profileEncoding == null ? "" : FileRule.norm(profileEncoding);
    }

    /** 못 바꾸는 사유, 바꿀 수 있으면 null — 쓸 수 없는 프로필 인코딩 · MS949 로 읽은 글의 U+FFFD · 목표 인코딩으로 못 적는 글자 */
    static String refusal(String text, String readEncoding, String profileEncoding, String lineEnding) {
        String want = target(profileEncoding);
        if (!WRITABLE.contains(want)) {
            return "프로필 project.encoding " + profileEncoding + " 로는 못 적는다(UTF-8·UTF-8-BOM·MS949)";
        }
        if (LocalFiles.MS949.equals(readEncoding) && text.indexOf(REPLACEMENT) >= 0) {
            return "MS949 로 읽은 글에 깨진 글자(U+FFFD) — 인코딩 판별을 못 믿는다";
        }
        try {
            LocalFiles.checkEncodable(text, want, lineEnding == null ? LocalFiles.LF : lineEnding);
        } catch (LocalFiles.Refused e) {
            return want + " 로 못 적는 글자가 있다";
        }
        return null;
    }
}
