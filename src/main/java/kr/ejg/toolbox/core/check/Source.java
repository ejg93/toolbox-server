package kr.ejg.toolbox.core.check;

import java.util.Locale;

/**
 * 검사 입력 하나 — 폴더의 파일 하나 또는 붙여넣기(5-1). {@code text} 는 줄바꿈을 {@code \n} 으로 맞춘 글({@code LocalFiles.read} 와 같다).
 * {@code encoding}·{@code lineEnding} 은 파일에서 읽었을 때만 있다(붙여넣기는 null — 파일 묶음이 건너뛴다).
 * {@code lang} 은 확장자 소문자(`java`·`jsp`·`tsx` …) — 없으면 {@code rel} 에서 뗀다.
 */
public record Source(String rel, String text, String encoding, String lineEnding, String lang) {

    public Source {
        text = text == null ? "" : text;
        lang = lang == null || lang.isBlank() ? ext(rel) : lang.toLowerCase(Locale.ROOT);
    }

    /** 붙여넣기 — 이름은 「(붙여넣기)」 */
    public static Source pasted(String text, String lang) {
        return new Source("(붙여넣기)", text, null, null, lang);
    }

    /** 경로의 파일 이름 — 글롭은 이 이름과 맞춘다 */
    public String fileName() {
        String r = rel == null ? "" : rel.replace('\\', '/');
        return r.substring(r.lastIndexOf('/') + 1);
    }

    static String ext(String rel) {
        if (rel == null) {
            return "";
        }
        String n = rel.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        int dot = n.lastIndexOf('.');
        return dot < 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
