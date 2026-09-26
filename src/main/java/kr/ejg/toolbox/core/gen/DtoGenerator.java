package kr.ejg.toolbox.core.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Table;

/**
 * 테이블 → 자바 DTO 소스 한 파일(1-9). 모양 셋 — record·bean·egovVo. 이름은 순수본 dev_tools 카멜 규칙.
 * M7(3.3) 이 Freemarker 템플릿으로 옮길 때 이 클래스의 골든이 회귀 기준이다.
 */
public final class DtoGenerator {

    public enum Style {
        RECORD, BEAN, EGOV_VO;

        public static Style of(String s) {
            String t = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
            return switch (t) {
                case "", "record" -> RECORD;
                case "bean" -> BEAN;
                case "egovvo", "egov", "vo" -> EGOV_VO;
                default -> throw new IllegalArgumentException("모르는 모양: " + s + " — record·bean·egovVo");
            };
        }
    }

    /**
     * @param logicalNames 컬럼명(대문자) → 조립 한글(3-2). 코멘트가 없을 때 javadoc
     * @param notes        컬럼명(대문자) → TODO 문구(DDL 을 못 읽은 줄 등)
     * @param dialect      타입 매핑의 방언 조건(oracle DATE 등). 모르면 null
     */
    public record Options(String packageName, Style style, List<String> skipTokens, Map<String, String> logicalNames,
            Map<String, String> notes, String dialect) {
        public Options {
            skipTokens = skipTokens == null ? List.of() : List.copyOf(skipTokens);
            logicalNames = logicalNames == null ? Map.of() : Map.copyOf(logicalNames);
            notes = notes == null ? Map.of() : Map.copyOf(notes);
        }
    }

    public record Source(String className, String text) {
    }

    private static final Set<String> KEYWORDS = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw",
            "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "var", "record", "yield");

    private final TypeMapping types;

    public DtoGenerator(TypeMapping types) {
        this.types = types;
    }

    private record Field(String name, String type, String doc, String todo) {
    }

    public Source generate(Table t, Options o) {
        String cls = className(t.name(), o.skipTokens()) + (o.style() == Style.EGOV_VO ? "VO" : "");
        Set<String> imports = new TreeSet<>();
        List<Field> fields = new ArrayList<>();
        Set<String> used = new java.util.HashSet<>();
        for (Column c : t.columns()) {
            String key = c.name().toUpperCase(Locale.ROOT);
            String full = types.javaType(c, o.dialect());
            String todo = o.notes().get(key);
            if (full == null) {
                full = "Object";
                if (todo == null) {
                    todo = "TODO 타입 확인: " + (c.nativeType() == null ? "?" : c.nativeType());
                }
            }
            int dot = full.lastIndexOf('.');
            if (dot > 0) {
                imports.add(full);
            }
            String doc = c.comment() != null && !c.comment().isBlank() ? c.comment().trim() : o.logicalNames().get(key);
            String name = fieldName(c.name());
            while (!used.add(name)) {
                name = name + "_";
            }
            fields.add(new Field(name, dot > 0 ? full.substring(dot + 1) : full, doc, todo));
        }
        if (o.style() == Style.EGOV_VO) {
            imports.add("java.io.Serializable");
        }

        StringBuilder sb = new StringBuilder();
        if (o.packageName() != null && !o.packageName().isBlank()) {
            sb.append("package ").append(o.packageName().trim()).append(";\n\n");
        }
        imports.forEach(i -> sb.append("import ").append(i).append(";\n"));
        if (!imports.isEmpty()) {
            sb.append('\n');
        }
        sb.append("/**\n");
        if (t.comment() != null && !t.comment().isBlank()) {
            sb.append(" * ").append(doc(t.comment().trim())).append("\n *\n");
        }
        // 따옴표로 감싼 이름엔 */ 가 들어올 수 있다(번들 4 리뷰)
        String where = (t.schema() == null || t.schema().isEmpty() ? "" : t.schema() + ".") + t.name();
        sb.append(" * <p>테이블: ").append(doc(where)).append("</p>\n");
        if (o.style() == Style.RECORD) {
            fields.stream().filter(f -> f.doc() != null).forEach(f -> sb.append(" * @param ").append(f.name()).append(' ')
                    .append(doc(f.doc())).append('\n'));
        }
        sb.append(" */\n");
        switch (o.style()) {
            case RECORD -> record(sb, cls, fields);
            case BEAN -> bean(sb, cls, fields, false);
            case EGOV_VO -> bean(sb, cls, fields, true);
            default -> throw new IllegalStateException();
        }
        return new Source(cls, sb.toString());
    }

    private static void record(StringBuilder sb, String cls, List<Field> fields) {
        sb.append("public record ").append(cls).append("(\n");
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            if (f.todo() != null) {
                sb.append("        // ").append(f.todo()).append('\n');
            }
            sb.append("        ").append(f.type()).append(' ').append(f.name()).append(i < fields.size() - 1 ? ",\n" : "\n");
        }
        sb.append(") {\n}\n");
    }

    private static void bean(StringBuilder sb, String cls, List<Field> fields, boolean vo) {
        sb.append("public class ").append(cls).append(vo ? " implements Serializable" : "").append(" {\n\n");
        if (vo) {
            sb.append("    private static final long serialVersionUID = 1L;\n\n");
        }
        for (Field f : fields) {
            if (f.doc() != null) {
                sb.append("    /** ").append(doc(f.doc())).append(" */\n");
            }
            sb.append("    private ").append(f.type()).append(' ').append(f.name()).append(';');
            if (f.todo() != null) {
                sb.append(" // ").append(f.todo());
            }
            sb.append("\n\n");
        }
        sb.append("    public ").append(cls).append("() {\n    }\n");
        for (Field f : fields) {
            String cap = Character.toUpperCase(f.name().charAt(0)) + f.name().substring(1);
            sb.append("\n    public ").append(f.type()).append(" get").append(cap).append("() {\n        return ").append(f.name())
                    .append(";\n    }\n");
            sb.append("\n    public void set").append(cap).append('(').append(f.type()).append(' ').append(f.name())
                    .append(") {\n        this.").append(f.name()).append(" = ").append(f.name()).append(";\n    }\n");
        }
        sb.append("}\n");
    }

    /** javadoc 안에서 주석을 닫지 않게 */
    static String doc(String s) {
        return s.replace("*/", "*&#47;").replace("\r", " ").replace("\n", " ");
    }

    /** 순수본 toCamel — trim·소문자 뒤 「_x」 → 「X」(x 는 영문·숫자·_). 자바 키워드·숫자 시작은 뒤·앞에 _ */
    static String fieldName(String col) {
        String s = col.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_' && i + 1 < s.length() && isWord(s.charAt(i + 1))) {
                sb.append(Character.toUpperCase(s.charAt(i + 1)));
                i++;
            } else {
                sb.append(c);
            }
        }
        String n = sb.toString();
        if (n.isEmpty()) {
            return "_";
        }
        if (!Character.isJavaIdentifierStart(n.charAt(0))) {
            n = "_" + n;
        }
        StringBuilder safe = new StringBuilder();
        n.codePoints().forEach(cp -> safe.appendCodePoint(Character.isJavaIdentifierPart(cp) ? cp : '_'));
        n = safe.toString();
        return KEYWORDS.contains(n) ? n + "_" : n;
    }

    private static boolean isWord(char c) {
        return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    /** 순수본 toPascal. 앞쪽의 무시 토큰(TB·TN 등)은 뗀다 — 전부 떼지면 원래 이름 */
    static String className(String table, List<String> skipTokens) {
        Set<String> skip = new java.util.HashSet<>();
        skipTokens.forEach(t -> skip.add(t.trim().toUpperCase(Locale.ROOT)));
        String[] parts = table.trim().split("_", -1);
        int i = 0;
        while (i < parts.length - 1 && skip.contains(parts[i].toUpperCase(Locale.ROOT))) {
            i++;
        }
        String rest = String.join("_", java.util.Arrays.copyOfRange(parts, i, parts.length));
        String camel = fieldName(rest).replaceAll("_+$", "");
        if (camel.startsWith("_")) {
            camel = "T" + camel;
        }
        return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
    }
}
