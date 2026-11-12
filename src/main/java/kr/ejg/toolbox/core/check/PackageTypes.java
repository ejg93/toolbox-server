package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import kr.ejg.toolbox.core.fs.LocalFiles;

/**
 * 설계 64 §3-10 — 소스 경로·패키지 → 같은 소스 루트의 그 패키지 폴더 바로 아래 {@code *.java} 가 선언한 최상위 형식 이름.
 * 폴더가 없거나 한 파일이라도 못 읽거나 목록이 잘리면 empty. 결과는 기억한다. 원본은 읽기만
 */
final class PackageTypes {

    private static final String TEST_ROOT = "src/test/java/";
    private static final String MAIN_ROOT = "src/main/java/";

    private PackageTypes() {
    }

    static BiFunction<String, String, Optional<Set<String>>> finder(LocalFiles files, String root) {
        Map<String, Optional<Set<String>>> seen = new HashMap<>();
        JavaSource parser = new JavaSource();
        return (rel, pkg) -> {
            String prefix = LoggerShape.sourceRoot(rel);
            String sub = pkg.replace('.', '/');
            Optional<Set<String>> own = seen.computeIfAbsent(prefix + "\u0000" + pkg, k -> scan(files, root, prefix + sub, parser));
            if (own.isEmpty() || !prefix.endsWith(TEST_ROOT)) {
                return own;
            }
            // 번들 81 마무리 리뷰 I2 — 시험 루트 파일은 짝 main 루트의 같은 패키지 형식도 본다(main 은 시험 형식을 못 본다)
            String twin = prefix.substring(0, prefix.length() - TEST_ROOT.length()) + MAIN_ROOT + sub;
            if (!exists(files, root, twin)) {
                return own;
            }
            Optional<Set<String>> main = seen.computeIfAbsent(twin + "\u0000", k -> scan(files, root, twin, parser));
            if (main.isEmpty()) {
                return main;
            }
            Set<String> both = new TreeSet<>(own.get());
            both.addAll(main.get());
            return Optional.of(both);
        };
    }

    private static boolean exists(LocalFiles files, String root, String sub) {
        try {
            return Files.isDirectory(files.check(root).resolve(sub));
        } catch (LocalFiles.Refused | java.nio.file.InvalidPathException x) {
            return true; // 못 보면 있다고 보고 scan 이 empty 를 낸다(모른다)
        }
    }

    private static Optional<Set<String>> scan(LocalFiles files, String root, String sub, JavaSource parser) {
        return scan(files, root, sub, parser, LocalFiles.MAX_FILES);
    }

    /** max — 목록 상한(시험이 잘림 길을 본다) */
    static Optional<Set<String>> scan(LocalFiles files, String root, String sub, JavaSource parser, int max) {
        Set<String> out = new TreeSet<>();
        try {
            Path dir = files.check(root).resolve(sub);
            if (!Files.isDirectory(dir)) {
                return Optional.empty();
            }
            LocalFiles.Listing l = files.list(dir.toString(), List.of("*.java"), List.of(), max);
            if (l.truncated()) {
                return Optional.empty(); // 5-93b ④ — 잘린 목록이면 형식을 다 모른다
            }
            for (LocalFiles.Entry e : l.files()) {
                if (e.rel().contains("/")) {
                    continue; // 하위 패키지
                }
                ParseResult<CompilationUnit> r = parser.parse(files.read(dir.resolve(e.rel()).toString()).text());
                if (!JavaSource.ok(r)) {
                    return Optional.empty();
                }
                r.getResult().orElseThrow().getTypes().forEach(t -> out.add(t.getNameAsString()));
            }
        } catch (IOException | LocalFiles.Refused | java.nio.file.InvalidPathException x) {
            return Optional.empty(); // 폴더·파일을 못 읽음 — 못 푼다
        }
        return Optional.of(out);
    }
}
