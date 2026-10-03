package kr.ejg.toolbox.core.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 생성기 템플릿 세트(7-1) — {@code <genDir>/<이름>/set.yaml} 의 {@code name}·{@code vars}·{@code files[{template, path}]}·{@code extends}.
 * {@code extends} 는 한 단계: 템플릿은 제 폴더 먼저 찾고 없으면 부모 폴더에서, {@code vars} 는 부모 위에 덮고, {@code files} 는 제 것이
 * 없으면 부모 것. 세트 폴더는 실행 때 jar 옆 {@code templates/gen} — 프로필 폴더의 형제다.
 */
public record TemplateSet(String name, Map<String, String> vars, List<FileSpec> files, List<Path> dirs) {

    /** 템플릿 하나 — {@code template} 은 세트 폴더 안 파일 이름, {@code path} 는 출력 경로 식(대괄호 보간) */
    public record FileSpec(String template, String path) {
    }

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    public TemplateSet {
        vars = Collections.unmodifiableMap(new LinkedHashMap<>(vars));
        files = List.copyOf(files);
        dirs = List.copyOf(dirs);
    }

    /** 실행 때 세트 폴더 — 프로필 폴더의 형제 {@code templates/gen} */
    public static Path genDir(Path profilesDir) {
        return profilesDir.toAbsolutePath().normalize().resolveSibling("templates").resolve("gen");
    }

    /** {@code set.yaml} 이 있는 폴더 이름(정렬). 폴더가 없으면 빈 목록 */
    public static List<String> list(Path genDir) throws IOException {
        if (!Files.isDirectory(genDir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(genDir)) {
            return s.filter(d -> Files.isRegularFile(d.resolve("set.yaml"))).map(d -> d.getFileName().toString())
                    .filter(n -> NAME.matcher(n).matches()).sorted().toList();
        }
    }

    public static TemplateSet load(Path genDir, String name) throws IOException {
        return load(genDir, name, true);
    }

    private static TemplateSet load(Path genDir, String name, boolean allowExtends) throws IOException {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("템플릿 세트 이름은 영문·숫자·_·- 만: " + name);
        }
        Path dir = genDir.resolve(name);
        Path yaml = dir.resolve("set.yaml");
        if (!Files.isRegularFile(yaml)) {
            throw new IllegalArgumentException("템플릿 세트가 없다: " + name);
        }
        JsonNode n = YAML.readTree(Files.readString(yaml));
        Map<String, String> vars = new LinkedHashMap<>();
        List<FileSpec> files = new ArrayList<>();
        List<Path> dirs = new ArrayList<>();
        dirs.add(dir);
        String parentName = n.path("extends").asText("");
        if (!parentName.isBlank()) {
            if (!allowExtends) {
                throw new IllegalArgumentException("extends 는 한 단계만: " + name + " → " + parentName);
            }
            TemplateSet parent = load(genDir, parentName, false);
            vars.putAll(parent.vars());
            files.addAll(parent.files());
            dirs.addAll(parent.dirs());
        }
        n.path("vars").fields().forEachRemaining(e -> vars.put(e.getKey(), e.getValue().asText()));
        JsonNode fs = n.path("files");
        if (fs.isArray() && !fs.isEmpty()) {
            files.clear();
            for (JsonNode f : fs) {
                String t = f.path("template").asText("");
                String p = f.path("path").asText("");
                if (t.isBlank() || p.isBlank()) {
                    throw new IllegalArgumentException(name + "/set.yaml 의 files 항목에 template·path 가 다 있어야 한다");
                }
                files.add(new FileSpec(t, p));
            }
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException(name + "/set.yaml 에 files 가 없다");
        }
        String shown = n.path("name").asText(name);
        return new TemplateSet(shown, vars, files, dirs);
    }
}
