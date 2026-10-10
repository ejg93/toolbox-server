package kr.ejg.toolbox.core.analyze;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSP 가 URL 을 부른 꼴(6-27 단서 추정 · 6-30 enum 하나로). 코드는 H2 {@code analyze_jsp_link.kind}·화면 글이 쥐므로 바꾸지 않는다.
 * 화면 {@code program_analysis_ext.js} 의 {@code KIND_WORD} 는 {@code ToolsFolderTest.linkKindWordsMatchEnum} 이 이 목록과 맞댄다.
 * V010 주석의 코드 목록은 그때(6-27) 것 — 이후 꼴은 여기만 본다.
 */
public enum LinkKind {
    LINK("link", "링크"),
    FORM("form", "폼"),
    POPUP("popup", "팝업"),
    AJAX("ajax", "ajax"),
    SCRIPT("script", "스크립트"),
    /** 서버 쪽 포함 — {@code c:import}·{@code jsp:include}·{@code <iframe src>}(6-30, 번들 34 egov 손 대조) */
    INCLUDE("include", "포함"),
    OTHER("other", "기타");

    /** 꼴이 저장되기 전(옛 실행 — kind null) */
    public static final String UNKNOWN_WORD = "모름";

    private final String code;
    private final String word;

    LinkKind(String code, String word) {
        this.code = code;
        this.word = word;
    }

    public String code() {
        return code;
    }

    public String word() {
        return word;
    }

    /** 코드 → 글, 넣은 순서. 「모름」 은 넣지 않는다(코드가 아니다) */
    public static Map<String, String> words() {
        Map<String, String> m = new LinkedHashMap<>();
        for (LinkKind k : values()) {
            m.put(k.code, k.word);
        }
        return m;
    }
}
