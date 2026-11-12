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
 * 폴더가 없거나 한 파일이라도 못 읽으면 empty. 결과는 기억한다. 원본은 읽기만
 */
final class PackageTypes {

    private PackageTypes() {
    }

    static BiFunction<String, String, Optional<Set<String>>> finder(LocalFiles files, String root) {
        Map<String, Optional<Set<String>>> seen = new HashMap<>();
        JavaSource parser = new JavaSource();
        return (rel, pkg) -> {
            String prefix = LoggerShape.sourceRoot(rel);
            return seen.computeIfAbsent(prefix + "\u0000" + pkg, k -> scan(files, root, prefix + pkg.replace('.', '/'), parser));
        };
    }

    private static Optional<Set<String>> scan(LocalFiles files, String root, String sub, JavaSource parser) {
        Set<String> out = new TreeSet<>();
        try {
            Path dir = files.check(root).resolve(sub);
            if (!Files.isDirectory(dir)) {
                return Optional.empty();
            }
            for (LocalFiles.Entry e : files.list(dir.toString(), List.of("*.java"), List.of(), LocalFiles.MAX_FILES).files()) {
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
