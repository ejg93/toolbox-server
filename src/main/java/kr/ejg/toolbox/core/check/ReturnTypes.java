package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.ejg.toolbox.core.fs.LocalFiles;

/**
 * 5-114 — 같은 소스 루트에 선언된 메서드의 반환 형식(단순 이름). 하나로 못 정하면 빈 값. 파일은 형식마다 한 번만 읽는다.
 * 형식 이름은 같은 파일 → 단일 형식 import → 같은 패키지 차례로 푼다(별 import 로만 올 수 있는 형식은 모름). 메서드는 이름·인자 수가 같은 것이
 * 정확히 하나일 때, 없으면 extends 조상으로 다섯 단계까지. 형식 해석기(symbol solver)는 안 들인다
 */
final class ReturnTypes {

    /** rel — 묻는 쪽 파일의 상대 경로 · cu — 그 파일 · type — 수신자 형식의 단순 이름 */
    @FunctionalInterface
    interface Finder {
        Optional<String> of(String rel, CompilationUnit cu, String type, String method, int argc);
    }

    /** 같은 파일 안에서만(붙여넣기·시험·다른 파일을 못 읽는 자리) */
    static final Finder SAME_FILE = (rel, cu, type, method, argc) -> new Resolver(null, null).find(rel, cu, type, method, argc);

    /** 조상 사다리 상한 */
    private static final int MAX_UP = 5;

    /** 형식 하나의 요약 — 「이름/인자 수」 → 반환 형식 단순 이름들(배열은 빈 글) · 조상 · 패키지 · 단일 import · 그 파일 rel */
    record Shape(Map<String, List<String>> returns, String superName, String pkg, Map<String, String> imports, String rel) {
    }

    private ReturnTypes() {
    }

    /** 폴더 작업용 — files 로 root 아래 파일을 읽는다. 결과는 파일마다 기억한다. 스레드 하나에서만 쓴다 */
    static Finder finder(LocalFiles files, String root) {
        Resolver r = new Resolver(files, root);
        return r::find;
    }

    private static final class Resolver {
        private final LocalFiles files;
        private final String root;
        /** rel → 그 파일의 형식 이름 → 요약(같은 이름이 둘이면 null). 못 읽은 파일은 empty */
        private final Map<String, Optional<Map<String, Shape>>> byFile = new HashMap<>();
        private final JavaSource parser = new JavaSource();

        Resolver(LocalFiles files, String root) {
            this.files = files;
            this.root = root;
        }

        Optional<String> find(String rel, CompilationUnit cu, String type, String method, int argc) {
            Map<String, Shape> here = byFile.computeIfAbsent(rel, k -> Optional.of(shapes(cu, rel))).orElse(Map.of());
            return locate(here, rel, imports(cu), type).flatMap(s -> returnOf(s, method, argc, 0));
        }

        /** 형식 이름 → 요약 — 같은 파일 · 단일 import · 같은 패키지 */
        private Optional<Shape> locate(Map<String, Shape> here, String rel, Map<String, String> imports, String type) {
            if (here.containsKey(type)) {
                return Optional.ofNullable(here.get(type));
            }
            String fq = imports.get(type);
            if (fq != null) {
                return fileShape(LoggerShape.sourceRoot(rel) + fq.replace('.', '/') + ".java", type);
            }
            String dir = rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/') + 1) : "";
            return fileShape(dir + type + ".java", type);
        }

        private Optional<Shape> fileShape(String rel, String type) {
            if (files == null) {
                return Optional.empty();
            }
            return byFile.computeIfAbsent(rel, this::read).map(m -> m.get(type));
        }

        private Optional<Map<String, Shape>> read(String rel) {
            try {
                ParseResult<CompilationUnit> r = parser.parse(files.read(Path.of(root).resolve(rel).toString()).text());
                return JavaSource.ok(r) ? Optional.of(shapes(r.getResult().orElseThrow(), rel)) : Optional.empty();
            } catch (IOException | LocalFiles.Refused | InvalidPathException | StackOverflowError e) {
                return Optional.empty(); // 못 읽음 · 없는 파일 · 깨진 경로 — 모름(PackageTypes.scan 과 같은 처분)
            }
        }

        /** 이름·인자 수가 같은 메서드가 정확히 하나면 그 반환 형식, 없으면 조상에서(MAX_UP 까지) */
        private Optional<String> returnOf(Shape s, String method, int argc, int depth) {
            List<String> r = s.returns().get(method + "/" + argc);
            if (r != null) {
                return r.size() == 1 && !r.get(0).isEmpty() ? Optional.of(r.get(0)) : Optional.empty();
            }
            if (s.superName() == null || depth >= MAX_UP) {
                return Optional.empty();
            }
            Map<String, Shape> there = byFile.getOrDefault(s.rel(), Optional.empty()).orElse(Map.of());
            return locate(there, s.rel(), s.imports(), s.superName()).flatMap(x -> returnOf(x, method, argc, depth + 1));
        }
    }

    /** 파일 하나의 형식 요약들 — 같은 단순 이름이 둘이면 null(하나로 못 정함) */
    static Map<String, Shape> shapes(CompilationUnit cu, String rel) {
        String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        Map<String, String> imports = imports(cu);
        Map<String, Shape> out = new HashMap<>();
        for (ClassOrInterfaceDeclaration t : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            Map<String, List<String>> returns = new HashMap<>();
            for (MethodDeclaration m : t.getMethods()) {
                String ret = m.getType().isArrayType() ? "" : DeclaredTypes.simpleType(m.getType());
                returns.computeIfAbsent(m.getNameAsString() + "/" + m.getParameters().size(), k -> new ArrayList<>()).add(ret);
            }
            String sup = t.getExtendedTypes().isEmpty() ? null : t.getExtendedTypes(0).getNameAsString();
            String name = t.getNameAsString();
            out.put(name, out.containsKey(name) ? null : new Shape(returns, sup, pkg, imports, rel));
        }
        return out;
    }

    /** 단일 형식 import — 단순 이름 → FQCN(static·별 import 는 뺀다) */
    private static Map<String, String> imports(CompilationUnit cu) {
        Map<String, String> out = new HashMap<>();
        for (ImportDeclaration i : cu.getImports()) {
            if (!i.isStatic() && !i.isAsterisk()) {
                out.put(i.getName().getIdentifier(), i.getNameAsString());
            }
        }
        return out;
    }
}
