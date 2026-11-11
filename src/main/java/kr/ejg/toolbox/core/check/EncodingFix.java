package kr.ejg.toolbox.core.check;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.fs.LocalFiles;

/** file.encoding 고침(설계 62)의 거절 판정 — 글은 그대로 두고 쓸 인코딩만 프로필 것으로 바꿀 수 있나. Cleanup 이 단계를 돌린다 */
final class EncodingFix {
    /** 거절 사유 머리 — 뒤에 선언 값(인코딩 이름만) */
    static final String DECLARED = "파일 안 인코딩 선언이 프로필과 다르다 — 선언과 같이 손으로: ";
    static final String BOM_ONLY = "BOM 이 유일한 인코딩 표시(JSP) — 떼면 컨테이너가 다르게 읽는다";
    /** 쓸 수 있는 이름 — LocalFiles 가 받는 셋 */
    private static final Set<String> WRITABLE = Set.of(LocalFiles.UTF8, LocalFiles.UTF8_BOM, LocalFiles.MS949);
    /** 못 읽은 바이트 자리 — MS949 로 읽은 글에 있으면 판별을 못 믿는다 */
    private static final char REPLACEMENT = '�';
    /** 파일 안 선언으로 읽히는 꼴 · 그 가운데 선언이 없으면 BOM 이 표시인 JSP */
    private static final Set<String> DECLARING = Set.of("jsp", "jspf", "tag", "html", "htm", "xhtml", "xml", "css");
    private static final Set<String> JSP = Set.of("jsp", "jspf", "tag");
    /** pageEncoding="x" · charset=x(contentType·meta) · @charset "x" · <?xml … encoding="x" — 값은 인코딩 이름만 */
    private static final Pattern DECL = Pattern.compile(
            "(?i)(?:pageEncoding\\s*=\\s*[\"']|charset\\s*=\\s*[\"']?|@charset\\s+[\"']|<\\?xml[^>]*?encoding\\s*=\\s*[\"'])([A-Za-z0-9_-]+)");

    private EncodingFix() {
    }

    /** 프로필 인코딩을 LocalFiles 이름으로(EUC-KR → MS949). 없으면 빈 글 */
    static String target(String profileEncoding) {
        return profileEncoding == null ? "" : FileRule.norm(profileEncoding);
    }

    /** 못 바꾸는 사유, 바꿀 수 있으면 null — 쓸 수 없는 프로필 인코딩 · MS949 로 읽은 글의 U+FFFD · 파일 안 선언과 어긋남 · 목표 인코딩으로 못 적는 글자 */
    static String refusal(String rel, String text, String readEncoding, String profileEncoding, String lineEnding) {
        String want = target(profileEncoding);
        if (!WRITABLE.contains(want)) {
            return "프로필 project.encoding " + profileEncoding + " 로는 못 적는다(UTF-8·UTF-8-BOM·MS949)";
        }
        if (LocalFiles.MS949.equals(readEncoding) && text.indexOf(REPLACEMENT) >= 0) {
            return "MS949 로 읽은 글에 깨진 글자(U+FFFD) — 인코딩 판별을 못 믿는다";
        }
        String declared = declaration(rel, text, readEncoding, want);
        if (declared != null) {
            return declared;
        }
        try {
            LocalFiles.checkEncodable(text, want, lineEnding == null ? LocalFiles.LF : lineEnding);
        } catch (LocalFiles.Refused e) {
            return want + " 로 못 적는 글자가 있다";
        }
        return null;
    }

    /** 리뷰 C1 — 읽는 쪽이 파일 안 선언으로 인코딩을 정하는 꼴(JSP·HTML·XML·CSS). 선언이 목표와 다르거나, 선언 없는 JSP 의 BOM 을 떼면 사유. java·js 의 charset 글은 응답 머리라 안 본다 */
    private static String declaration(String rel, String text, String readEncoding, String want) {
        String ext = rel.substring(rel.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!DECLARING.contains(ext)) {
            return null;
        }
        String base = LocalFiles.UTF8_BOM.equals(want) ? LocalFiles.UTF8 : want;
        Matcher m = DECL.matcher(text);
        boolean any = false;
        while (m.find()) {
            any = true;
            if (!FileRule.norm(m.group(1)).equals(base)) {
                return DECLARED + m.group(1).toUpperCase(Locale.ROOT);
            }
        }
        boolean bomOnly = !any && LocalFiles.UTF8_BOM.equals(readEncoding) && !LocalFiles.UTF8_BOM.equals(want) && JSP.contains(ext);
        return bomOnly ? BOM_ONLY : null;
    }
}
