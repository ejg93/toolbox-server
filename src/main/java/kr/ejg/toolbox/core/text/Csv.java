package kr.ejg.toolbox.core.text;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV — 순수본 논리명 변환기의 `decode`·`detectDelim`·`parseCSV` 를 그대로 옮겼다(3-1). 자바 결과가 JS 와 같아야 골든(3-2)이 맞는다.
 * <ul>
 *   <li>바이트 → 글자: UTF-8 엄격 → 실패하면 CP949(EUC-KR 상위) — 현장 엑셀 저장본</li>
 *   <li>구분자: 따옴표 밖 앞 5줄에서 {@code , \t ; |} 중 가장 많은 것(같으면 앞 순서)</li>
 *   <li>따옴표 안 {@code ""} 는 따옴표 하나, CR 은 버린다, 첫 BOM 은 버린다</li>
 *   <li>모든 칸이 공백인 줄은 뺀다</li>
 * </ul>
 */
public final class Csv {

    private static final char[] DELIMS = {',', '\t', ';', '|'};

    /** UTF-8 BOM — 첫 글자면 버린다. 소스에 보이지 않는 글자를 안 두려고 코드값으로 */
    private static final String BOM = String.valueOf((char) 0xFEFF);

    private Csv() {
    }

    public static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("MS949"));
        }
    }

    public static char detectDelim(String text) {
        Map<Character, Integer> cnt = new LinkedHashMap<>();
        for (char d : DELIMS) {
            cnt.put(d, 0);
        }
        boolean q = false;
        int line = 0;
        for (int i = 0; i < text.length() && line < 5; i++) {
            char c = text.charAt(i);
            if (c == '"') {
                q = !q;
                continue;
            }
            if (q) {
                continue;
            }
            if (c == '\n') {
                line++;
                continue;
            }
            cnt.computeIfPresent(c, (k, v) -> v + 1);
        }
        char best = ',';
        int max = 0;
        for (char d : DELIMS) {
            if (cnt.get(d) > max) {
                max = cnt.get(d);
                best = d;
            }
        }
        return best;
    }

    public static List<List<String>> parse(String text) {
        String t = text.startsWith(BOM) ? text.substring(1) : text;
        return parse(t, detectDelim(t));
    }

    public static List<List<String>> parse(String text, char delim) {
        String t = text.startsWith(BOM) ? text.substring(1) : text;
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (q) {
                if (c == '"') {
                    if (i + 1 < t.length() && t.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        q = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                q = true;
            } else if (c == delim) {
                row.add(cur.toString());
                cur.setLength(0);
            } else if (c == '\n') {
                row.add(cur.toString());
                rows.add(row);
                row = new ArrayList<>();
                cur.setLength(0);
            } else if (c != '\r') {
                cur.append(c);
            }
        }
        if (cur.length() > 0 || !row.isEmpty()) {
            row.add(cur.toString());
            rows.add(row);
        }
        rows.removeIf(r -> r.stream().allMatch(x -> x.trim().isEmpty()));
        return rows;
    }

    /**
     * 순수본 `fillSel` 의 열 추측 — 공백 뺀 이름이 같은 첫 열, 없으면 이름을 포함하는 첫 열, 그래도 없으면
     * {@code allowNone} 이면 -1, 아니면 0.
     */
    public static int guess(List<String> header, List<String> candidates, boolean allowNone) {
        for (int i = 0; i < header.size(); i++) {
            String h = nospace(header.get(i));
            for (String k : candidates) {
                if (h.equals(nospace(k))) {
                    return i;
                }
            }
        }
        for (int i = 0; i < header.size(); i++) {
            String h = nospace(header.get(i));
            for (String k : candidates) {
                if (h.contains(nospace(k))) {
                    return i;
                }
            }
        }
        return allowNone ? -1 : 0;
    }

    private static String nospace(String s) {
        return s == null ? "" : s.replaceAll("\\s", "");
    }

    /** 헤더 이름으로 열 번호. 없으면 -1 */
    public static int column(List<String> header, String name) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).trim().equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
