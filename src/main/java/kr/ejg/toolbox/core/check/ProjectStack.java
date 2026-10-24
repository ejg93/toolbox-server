package kr.ejg.toolbox.core.check;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 스택 판별(5-39a, 설계 35) — 검사 폴더 아래 {@code package.json}·{@code pom.xml}·{@code build.gradle(.kts)} 로 모듈마다 도구·태그·빌드 결과
 * 폴더·클래스 폴더를 정한다. 빌드 결과는 매니페스트 바로 옆 폴더만(Java 패키지 {@code …/io/build} 같은 소스 폴더를 안 뺀다).
 * 읽는 것은 의존성 이름뿐 — 값·스크립트·설정 코드는 안 읽고 안 남긴다(규칙 3)
 */
public final class ProjectStack {

    static final int MAX_DEPTH = 4;
    static final int MAX_MANIFESTS = 50;
    static final long MAX_BYTES = 2L * 1024 * 1024;
    /** {@code LocalFiles} 걷기와 같은 고정 건너뜀 */
    private static final Set<String> SKIP_DIRS = Set.of(".git", "target", "node_modules");
    private static final List<String> NPM_OUT = List.of("dist", "build", "out", "coverage");
    private static final List<String> MAVEN_CLASSES = List.of("target/classes", "src/main/webapp/WEB-INF/classes");
    private static final List<String> GRADLE_CLASSES = List.of("build/classes/java/main", "build/classes/kotlin/main");
    private static final Pattern ARTIFACT = Pattern.compile("<artifactId>\\s*([^<\\s]+)\\s*</artifactId>");
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 매니페스트 하나.
     * @param rel 검사 폴더 기준 상대 경로({@code /}, 루트는 빈 글)
     * @param tool npm · maven · gradle
     * @param tags react·next·vite·cra·boot·egov·mybatis·jpa 가운데 걸린 것
     * @param buildOut 검사에서 뺄 빌드 결과 폴더(매니페스트 옆, 실재하는 것만)
     * @param classes SpotBugs 가 읽을 클래스 폴더(모듈 기준, 실재하는 것만)
     */
    public record Module(String rel, String tool, List<String> tags, List<String> buildOut, List<String> classes) {
        public Module {
            tags = List.copyOf(tags);
            buildOut = List.copyOf(buildOut);
            classes = List.copyOf(classes);
        }

        String at(String sub) {
            return rel.isEmpty() ? sub : rel + "/" + sub;
        }
    }

    public record Stack(List<Module> modules) {
        public Stack {
            modules = List.copyOf(modules);
        }

        /** 걷기에서 뺄 글롭 — {@code <모듈>/<폴더>/**} */
        public List<String> excludes() {
            Set<String> out = new LinkedHashSet<>();
            for (Module m : modules) {
                for (String b : m.buildOut()) {
                    out.add(m.at(b) + "/**");
                }
            }
            return List.copyOf(out);
        }

        /** 클래스 폴더 — 검사 폴더 기준 상대 경로 */
        public List<String> classesDirs() {
            Set<String> out = new LinkedHashSet<>();
            for (Module m : modules) {
                for (String c : m.classes()) {
                    out.add(m.at(c));
                }
            }
            return List.copyOf(out);
        }

        /** 전자정부 프레임워크 모듈이 있나 — 프로필 {@code framework} 가 없을 때 eGov 전용 규칙을 켤지 */
        public boolean egov() {
            return modules.stream().anyMatch(m -> !m.tool().equals("npm") && m.tags().contains("egov"));
        }

        /** 화면 한 줄 — 모듈 넷까지, 나머지는 수만 */
        public String summary() {
            if (modules.isEmpty()) {
                return "판별 못 함(package.json·pom.xml·build.gradle 없음)";
            }
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < modules.size() && i < 4; i++) {
                Module m = modules.get(i);
                StringBuilder sb = new StringBuilder(m.rel().isEmpty() ? "(루트)" : m.rel() + "/").append(' ').append(label(m.tool()));
                m.tags().forEach(t -> sb.append(" · ").append(label(t)));
                if (!m.classes().isEmpty()) {
                    sb.append(" · 클래스 ").append(String.join(",", m.classes()));
                }
                if (!m.buildOut().isEmpty()) {
                    sb.append(" · 제외 ").append(String.join(",", m.buildOut()));
                }
                parts.add(sb.toString());
            }
            String s = String.join(" / ", parts);
            return modules.size() > 4 ? s + " 외 " + (modules.size() - 4) : s;
        }
    }

    private ProjectStack() {
    }

    static String label(String t) {
        return switch (t) {
            case "npm" -> "npm";
            case "maven" -> "Maven";
            case "gradle" -> "Gradle";
            case "react" -> "React";
            case "next" -> "Next";
            case "vite" -> "Vite";
            case "cra" -> "CRA";
            case "boot" -> "Boot";
            case "egov" -> "eGov";
            case "mybatis" -> "MyBatis";
            case "jpa" -> "JPA";
            default -> t;
        };
    }

    /** 폴더가 아니면 빈 판별. 읽기 실패는 그 매니페스트만 건너뛴다(예외 없음) */
    public static Stack detect(Path root) {
        List<Module> out = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) {
            return new Stack(out);
        }
        Path base = root.toAbsolutePath().normalize();
        Set<Path> skip = new LinkedHashSet<>();
        try {
            Files.walkFileTree(base, Set.of(), MAX_DEPTH + 1, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    if (!d.equals(base) && (SKIP_DIRS.contains(String.valueOf(d.getFileName())) || hidden(d) || skip.contains(d))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (base.relativize(d).getNameCount() > MAX_DEPTH) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    String rel = d.equals(base) ? "" : base.relativize(d).toString().replace('\\', '/');
                    for (String name : List.of("package.json", "pom.xml", "build.gradle", "build.gradle.kts")) {
                        if (out.size() >= MAX_MANIFESTS) {
                            return FileVisitResult.TERMINATE;
                        }
                        Path f = d.resolve(name);
                        if (!Files.isRegularFile(f)) {
                            continue;
                        }
                        Module m = name.equals("package.json") ? npm(d, f, rel) : jvm(d, f, rel, name.equals("pom.xml") ? "maven" : "gradle");
                        if (m != null) {
                            out.add(m);
                            m.buildOut().forEach(b -> skip.add(d.resolve(b)));
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return new Stack(sorted(out));
        }
        return new Stack(sorted(out));
    }

    /** 걷기 순서는 OS 마다 다르다(CI 리눅스에서 드러남) — 경로 순, 같은 폴더면 npm·maven·gradle 순으로 고정 */
    private static List<Module> sorted(List<Module> ms) {
        List<String> tools = List.of("npm", "maven", "gradle");
        List<Module> out = new ArrayList<>(ms);
        out.sort(java.util.Comparator.comparing(Module::rel).thenComparingInt(m -> tools.indexOf(m.tool())));
        return out;
    }

    static Module npm(Path dir, Path file, String rel) {
        JsonNode doc;
        try (InputStream in = Files.newInputStream(file)) {
            if (Files.size(file) > MAX_BYTES) {
                return null;
            }
            doc = JSON.readTree(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (doc == null || !doc.isObject()) {
            return null;
        }
        Set<String> deps = new LinkedHashSet<>();
        for (String k : List.of("dependencies", "devDependencies")) {
            JsonNode n = doc.get(k);
            if (n != null && n.isObject()) {
                for (Iterator<String> it = n.fieldNames(); it.hasNext();) {
                    deps.add(it.next());
                }
            }
        }
        List<String> tags = new ArrayList<>();
        if (deps.contains("react")) {
            tags.add("react");
        }
        if (deps.contains("next")) {
            tags.add("next");
        }
        if (deps.contains("vite")) {
            tags.add("vite");
        }
        if (deps.contains("react-scripts")) {
            tags.add("cra");
        }
        return new Module(rel, "npm", tags, existing(dir, NPM_OUT), List.of());
    }

    static Module jvm(Path dir, Path file, String rel, String tool) {
        String text;
        try {
            if (Files.size(file) > MAX_BYTES) {
                return null;
            }
            text = Files.readString(file, StandardCharsets.ISO_8859_1); // 이름만 본다 — 인코딩과 무관한 ASCII
        } catch (IOException e) {
            return null;
        }
        String names;
        if (tool.equals("maven")) {
            StringBuilder sb = new StringBuilder();
            Matcher m = ARTIFACT.matcher(text);
            while (m.find()) {
                sb.append(m.group(1)).append('\n');
            }
            names = sb.toString();
        } else {
            names = text;
        }
        names = names.toLowerCase(Locale.ROOT);
        List<String> tags = new ArrayList<>();
        if (names.contains("spring-boot")) {
            tags.add("boot");
        }
        if (names.contains("egovframe")) { // egovframework·egovframe 둘 다
            tags.add("egov");
        }
        if (names.contains("mybatis")) {
            tags.add("mybatis");
        }
        if (names.contains("data-jpa") || names.contains("hibernate")) {
            tags.add("jpa");
        }
        boolean gradle = tool.equals("gradle");
        return new Module(rel, tool, tags, gradle ? existing(dir, List.of("build")) : List.of(), existing(dir, gradle ? GRADLE_CLASSES : MAVEN_CLASSES));
    }

    private static List<String> existing(Path dir, List<String> subs) {
        List<String> out = new ArrayList<>();
        for (String s : subs) {
            if (Files.isDirectory(dir.resolve(s))) {
                out.add(s);
            }
        }
        return out;
    }

    private static boolean hidden(Path p) {
        if (String.valueOf(p.getFileName()).startsWith(".")) {
            return true;
        }
        try {
            return Files.isHidden(p);
        } catch (IOException e) {
            return false;
        }
    }
}
