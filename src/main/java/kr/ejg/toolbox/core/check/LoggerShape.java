package kr.ejg.toolbox.core.check;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.fs.LocalFiles;

/**
 * 설계 60 — 같은 소스 루트에서 가장 많이 쓰는 static 로거 선언 꼴. imports 는 끼울 단일 import(1~2), name 은 필드 이름,
 * template 은 선언 글(클래스 이름 자리는 {C}). 코드 본문이 아니라 선언 한 줄의 꼴만 든다
 */
record LoggerShape(List<String> imports, String name, String template) {
    private static final Pattern DECL = Pattern.compile(
            "^\\s*((?:(?:private|protected|public|static|final)\\s+)+)(Logger|Log)\\s+(\\w+)\\s*=\\s*(\\w+)\\.(getLogger|getLog)\\(\\s*(\\w+)\\.class\\s*\\)\\s*;\\s*$");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+([\\w.]+\\.(\\w+))\\s*;");
    private static final Pattern SOURCE_ROOT = Pattern.compile("^(.*?/)?src/(?:main|test)/java/|^(.*?/)?src/");
    private static final Pattern STATIC = Pattern.compile("\\bstatic\\b");
    private static final String PLACE = "{C}";
    private static final String CLASS_SUFFIX = ".class";

    LoggerShape {
        imports = List.copyOf(imports);
    }

    /** 파일 글 하나의 꼴 — static 이고, 인자가 그 파일에 선언된 클래스 이름이고, 형식·팩토리의 단일 import 가 그 파일에 있을 때만 */
    static Optional<LoggerShape> of(String text) {
        Map<String, String> imported = new HashMap<>();
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            Matcher im = IMPORT.matcher(line.strip());
            if (im.find()) {
                imported.put(im.group(2), im.group(1));
            }
        }
        for (String line : lines) {
            Optional<LoggerShape> s = line(line, text, imported);
            if (s.isPresent()) {
                return s;
            }
        }
        return Optional.empty();
    }

    /** 선언 한 줄의 꼴 — static · 인자가 그 파일의 클래스 · 형식·팩토리 import 가 있을 때 */
    private static Optional<LoggerShape> line(String line, String text, Map<String, String> imported) {
        Matcher m = DECL.matcher(line);
        if (!m.matches() || !STATIC.matcher(m.group(1)).find()) {
            return Optional.empty();
        }
        String arg = m.group(6);
        String type = imported.get(m.group(2));
        String factory = imported.get(m.group(4));
        if (type == null || factory == null || !Pattern.compile("\\bclass\\s+" + Pattern.quote(arg) + "\\b").matcher(text).find()) {
            return Optional.empty();
        }
        List<String> imports = type.equals(factory) ? List.of(type) : List.of(type, factory);
        String template = line.strip().replaceFirst("\\(\\s*" + Pattern.quote(arg) + "\\.class", Matcher.quoteReplacement("(" + PLACE + CLASS_SUFFIX));
        return Optional.of(new LoggerShape(imports, m.group(3), template));
    }

    /** rel(/ 구분)의 소스 루트 접두 — "…/src/main/java/"·"…/src/test/java/" 까지, 없으면 첫 "…/src/" 까지, 그것도 없으면 "" */
    static String sourceRoot(String rel) {
        Matcher m = SOURCE_ROOT.matcher(rel);
        return m.find() ? m.group() : "";
    }

    String decl(String className) {
        return template.replace(PLACE, className);
    }

    /** root 아래를 소스 루트마다 한 번 훑어(게으르게, 결과 기억) 가장 흔한 꼴 — 동률이면 template 글 순. 못 읽는 파일은 건너뛴다. 원본은 읽기만 */
    static Function<String, Optional<LoggerShape>> finder(LocalFiles files, String root) {
        Map<String, Optional<LoggerShape>> seen = new HashMap<>();
        return rel -> seen.computeIfAbsent(sourceRoot(rel), prefix -> scan(files, files.check(root).resolve(prefix)));
    }

    private static Optional<LoggerShape> scan(LocalFiles files, Path dir) {
        Map<LoggerShape, Integer> count = new HashMap<>();
        List<LocalFiles.Entry> entries = new ArrayList<>();
        try {
            entries.addAll(files.list(dir.toString(), List.of("*.java"), List.of(), LocalFiles.MAX_FILES).files());
        } catch (IOException | LocalFiles.Refused e) {
            return Optional.empty(); // 폴더가 없거나 못 읽음 — 꼴 없음
        }
        for (LocalFiles.Entry e : entries) {
            try {
                of(files.read(dir.resolve(e.rel()).toString()).text()).ifPresent(s -> count.merge(s, 1, Integer::sum));
            } catch (IOException | LocalFiles.Refused x) {
                continue; // 그 파일만 건너뛴다
            }
        }
        return count.entrySet().stream()
                .min(Comparator.comparing((Map.Entry<LoggerShape, Integer> x) -> -x.getValue()).thenComparing(x -> x.getKey().template()))
                .map(Map.Entry::getKey);
    }
}
