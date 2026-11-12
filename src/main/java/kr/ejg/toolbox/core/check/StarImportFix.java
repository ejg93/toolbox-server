package kr.ejg.toolbox.core.check;

import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.TypeParameter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 설계 64 §3-10 — 코드 정리 단계 imports. 비정적 별표 import 를 파일이 쓰는 형식의 단일 import 로 푼다(쓰는 것이 없으면 줄 삭제).
 * 풀 수 있는 패키지는 JDK(부트 레이어 — 존재는 플랫폼 로더에 초기화 없이 묻는다)와 같은 소스 루트의 패키지 폴더뿐.
 * 한 별표라도 못 풀거나 쓰는 이름이 두 별표에 다 있으면 파일 전체를 건너뛴다. 정적 별표는 손으로
 */
final class StarImportFix {

    static final String STAR = "java.starImport";
    static final String STATIC_STAR = "정적 별표 import — 손으로";
    static final String UNRESOLVED = "별표 패키지를 못 푼다(JDK·소스 루트 밖): ";
    static final String AMBIGUOUS = "이름이 두 별표 패키지에 — 손으로: ";
    static final String SPLIT = "import 가 줄을 나눠 쓴다";
    private static final Set<String> BOOT_PACKAGES = ModuleLayer.boot().modules().stream().flatMap(m -> m.getPackages().stream())
            .collect(Collectors.toUnmodifiableSet());

    private StarImportFix() {
    }

    /**
     * text 는 \n 정규화 · cur 는 지금 지적
     * @param packageTypes 패키지 → 같은 소스 루트의 그 폴더에 선언된 최상위 형식 이름(폴더가 없으면 empty)
     */
    static void apply(String text, List<Finding> cur, JavaSource parser, Function<String, Optional<Set<String>>> packageTypes, Edits e,
            List<Cleanup.Skip> skips) {
        String unsafe = Cleanup.javaUnsafe(text, cur);
        if (unsafe != null) {
            skips.add(new Cleanup.Skip(Cleanup.IMPORTS, unsafe));
            return;
        }
        ParseResult<CompilationUnit> r = parser.parse(text);
        if (!JavaSource.ok(r)) {
            skips.add(new Cleanup.Skip(Cleanup.IMPORTS, "구문을 못 읽음"));
            return;
        }
        CompilationUnit cu = r.getResult().orElseThrow();
        List<ImportDeclaration> stars = new ArrayList<>();
        for (ImportDeclaration d : cu.getImports()) {
            if (d.isAsterisk() && d.isStatic()) {
                skip(skips, d, STATIC_STAR);
            } else if (d.isAsterisk()) {
                stars.add(d);
            }
        }
        List<Set<String>> resolved = stars.isEmpty() ? List.of() : resolveAll(stars, used(cu, packageTypes), packageTypes, skips);
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < resolved.size(); i++) {
            ImportDeclaration d = stars.get(i);
            int n = d.getBegin().orElseThrow().line;
            if (!alone(lines, d.getRange().orElseThrow())) {
                skip(skips, d, SPLIT);
            } else if (resolved.get(i).isEmpty()) {
                e.delete(n);
            } else {
                String lead = lines[n - 1].substring(0, lines[n - 1].length() - lines[n - 1].stripLeading().length());
                e.replace(n, resolved.get(i).stream().map(x -> lead + "import " + d.getNameAsString() + "." + x + ";").collect(Collectors.joining("\n")));
            }
        }
    }

    /** 별표마다 푼 이름(오름차순) — 하나라도 못 풀거나 이름이 겹치면 빈 목록(사유는 skips) */
    private static List<Set<String>> resolveAll(List<ImportDeclaration> stars, Set<String> used, Function<String, Optional<Set<String>>> packageTypes,
            List<Cleanup.Skip> skips) {
        List<Set<String>> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ImportDeclaration d : stars) {
            String pkg = d.getNameAsString();
            Optional<Set<String>> got = resolve(pkg, used, packageTypes);
            if (got.isEmpty()) {
                skip(skips, d, UNRESOLVED + pkg);
                return List.of();
            }
            for (String n : got.get()) {
                if (!seen.add(n)) {
                    skip(skips, d, AMBIGUOUS + n);
                    return List.of();
                }
            }
            out.add(got.get());
        }
        return out;
    }

    private static Optional<Set<String>> resolve(String pkg, Set<String> used, Function<String, Optional<Set<String>>> packageTypes) {
        if (BOOT_PACKAGES.contains(pkg)) {
            return Optional.of(used.stream().filter(n -> jdkType(pkg + "." + n)).collect(Collectors.toCollection(TreeSet::new)));
        }
        return packageTypes.apply(pkg).map(s -> s.stream().filter(used::contains).collect(Collectors.toCollection(TreeSet::new)));
    }

    /** JDK 의 공개 최상위 형식 — 초기화 없이 플랫폼 로더에만 묻는다 */
    private static boolean jdkType(String name) {
        try {
            Class<?> k = Class.forName(name, false, ClassLoader.getPlatformClassLoader());
            return Modifier.isPublic(k.getModifiers()) && k.getEnclosingClass() == null;
        } catch (ClassNotFoundException | LinkageError x) {
            return false;
        }
    }

    /**
     * 파일이 쓰는 단순 이름 — 이름 전부(import·package 선언 밖) + 주석 단어(JavaRules.usedNames 꼴 복사)
     * − 파일이 선언한 형식·형식 매개변수 − 단일 import 의 이름 − 같은 패키지 폴더의 형식
     */
    private static Set<String> used(CompilationUnit cu, Function<String, Optional<Set<String>>> packageTypes) {
        Set<String> used = new HashSet<>();
        cu.findAll(SimpleName.class).forEach(n -> used.add(n.getIdentifier()));
        for (Name n : cu.findAll(Name.class)) {
            if (n.findAncestor(ImportDeclaration.class).isEmpty() && n.findAncestor(PackageDeclaration.class).isEmpty()) {
                used.add(n.getIdentifier());
            }
        }
        for (Comment cm : cu.getAllComments()) {
            used.addAll(List.of(cm.getContent().split("[^\\w$]+")));
        }
        cu.findAll(TypeDeclaration.class).forEach(t -> used.remove(t.getNameAsString()));
        cu.findAll(TypeParameter.class).forEach(t -> used.remove(t.getNameAsString()));
        cu.getImports().stream().filter(d -> !d.isAsterisk() && !d.isStatic()).forEach(d -> used.remove(d.getName().getIdentifier()));
        cu.getPackageDeclaration().flatMap(p -> packageTypes.apply(p.getNameAsString())).ifPresent(used::removeAll);
        return used;
    }

    /** import 가 줄을 혼자 쓴다 */
    private static boolean alone(String[] lines, Range g) {
        String line = lines[g.begin.line - 1];
        return g.begin.line == g.end.line && line.substring(0, g.begin.column - 1).isBlank() && line.substring(Math.min(line.length(), g.end.column)).isBlank();
    }

    private static void skip(List<Cleanup.Skip> skips, ImportDeclaration d, String why) {
        skips.add(new Cleanup.Skip(Cleanup.IMPORTS, "줄 " + d.getBegin().orElseThrow().line + ": " + why));
    }
}
