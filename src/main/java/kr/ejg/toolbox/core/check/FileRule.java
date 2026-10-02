package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 파일 묶음 ㅂ — {@code params.check} 하나: encoding(프로필 {@code project.encoding} 과 다름)·lineEnding({@code project.lineEnding} 과 다름)·
 * mixedIndent(탭 들여쓰기와 공백 둘 이상 들여쓰기가 한 파일에)·header({@code naming.headerRequired} 면 첫 60줄에 「수정일」 없음).
 * 기준 값이 프로필에 없으면 건이 없다. 붙여넣기는 인코딩·줄바꿈을 모른다 — 그 둘은 건너뛴다.
 */
public final class FileRule implements Rule {

    static final int HEADER_LINES = 60;
    private static final Pattern HEADER = Pattern.compile("수정일");

    private final Def def;
    private final String expected;

    /** @param expected encoding·lineEnding 의 기준 값. header 는 "true" 일 때만 본다 */
    public FileRule(Def def, String expected) {
        this.def = def;
        this.expected = expected;
    }

    @Override
    public Def def() {
        return def;
    }

    @Override
    public List<Finding> apply(Source s) {
        String check = String.valueOf(def.params().get("check"));
        List<Finding> out = new ArrayList<>();
        switch (check) {
            case "encoding" -> {
                if (expected != null && s.encoding() != null && !s.text().chars().allMatch(c -> c < 0x80)
                        && !norm(expected).equals(norm(s.encoding()))) {
                    out.add(at(1, s, s.encoding() + " ≠ " + expected));
                }
            }
            case "lineEnding" -> {
                if (expected != null && s.lineEnding() != null && s.text().indexOf('\n') >= 0
                        && !expected.equalsIgnoreCase(s.lineEnding())) {
                    out.add(at(1, s, s.lineEnding() + " ≠ " + expected));
                }
            }
            case "mixedIndent" -> mixed(s, out);
            case "header" -> {
                if ("true".equals(expected)) {
                    String[] lines = s.text().split("\n", HEADER_LINES + 1);
                    boolean found = false;
                    for (int i = 0; i < Math.min(lines.length, HEADER_LINES) && !found; i++) {
                        found = HEADER.matcher(lines[i]).find();
                    }
                    if (!found) {
                        out.add(at(1, s, "첫 " + HEADER_LINES + "줄에 개정이력(수정일) 표 없음"));
                    }
                }
            }
            default -> throw new IllegalArgumentException(def.id() + ": 모르는 params.check " + check);
        }
        return out;
    }

    /** 적은 쪽의 첫 줄에 한 건 */
    private void mixed(Source s, List<Finding> out) {
        String[] lines = s.text().split("\n", -1);
        int tabs = 0;
        int spaces = 0;
        int firstTab = -1;
        int firstSpace = -1;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            if (l.isBlank()) {
                continue;
            }
            if (l.charAt(0) == '\t') {
                tabs++;
                firstTab = firstTab < 0 ? i : firstTab;
            } else if (l.startsWith("  ")) {
                spaces++;
                firstSpace = firstSpace < 0 ? i : firstSpace;
            }
        }
        if (tabs > 0 && spaces > 0) {
            int line = tabs < spaces ? firstTab : firstSpace;
            out.add(at(line + 1, s, "탭 " + tabs + "줄 · 공백 " + spaces + "줄"));
        }
    }

    private Finding at(int line, Source s, String why) {
        return new Finding(s.rel(), line, def.group(), def.id(), def.severity(), why);
    }

    /** EUC-KR 과 MS949 는 같은 기준으로 본다. UTF-8-BOM 은 UTF-8 과 다르다 */
    static String norm(String enc) {
        String u = enc.toUpperCase(Locale.ROOT).replace("_", "-");
        return switch (u) {
            case "EUC-KR", "CP949", "MS949", "X-WINDOWS-949" -> "MS949";
            case "UTF8" -> "UTF-8";
            default -> u;
        };
    }
}
