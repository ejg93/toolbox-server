package kr.ejg.toolbox.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 5-39a — 스택 판별. 표본엔 매니페스트가 없고(레시피가 소스만) {@code target/}·{@code out/} 은 저장소가 무시하므로 시험 폴더를 손으로 짓는다.
 * {@link #tree(Path)} 는 라우트·화면 시험도 같이 쓴다
 */
class ProjectStackTest {

    @TempDir
    Path tmp;

    /** frontend(React+Vite, dist·out) · backend(Boot+MyBatis, target/classes) · egov(eGov, 클래스 없음) · gradle(Boot+JPA) · plain(매니페스트 없는 build 패키지) */
    public static Path tree(Path root) throws Exception {
        write(root.resolve("frontend/package.json"), "{\"name\":\"fe\",\"scripts\":{\"build\":\"vite build\"},"
                + "\"dependencies\":{\"react\":\"^19.0.0\",\"react-dom\":\"^19.0.0\"},\"devDependencies\":{\"vite\":\"^7.0.0\"}}");
        write(root.resolve("frontend/src/App.jsx"), "export default function App() {\n  return <div>app</div>;\n}\n");
        write(root.resolve("frontend/dist/assets/app.js"), "function a(){debugger;console.log(1)}\n");
        write(root.resolve("frontend/out/x.js"), "debugger;\n");
        write(root.resolve("backend/pom.xml"), "<project><parent><groupId>org.springframework.boot</groupId>"
                + "<artifactId>spring-boot-starter-parent</artifactId></parent><dependencies><dependency><groupId>org.mybatis.spring.boot</groupId>"
                + "<artifactId>mybatis-spring-boot-starter</artifactId></dependency></dependencies></project>");
        Files.createDirectories(root.resolve("backend/target/classes"));
        write(root.resolve("backend/src/main/java/a/Hello.java"), "package a;\n\npublic class Hello {\n}\n");
        write(root.resolve("egov/pom.xml"), "<project><dependencies><dependency><groupId>org.egovframe.rte</groupId>"
                + "<artifactId>org.egovframe.rte.ptl.mvc</artifactId></dependency></dependencies></project>");
        write(root.resolve("gradle/build.gradle"), "plugins { id 'org.springframework.boot' version '3.3.0' }\n"
                + "dependencies { implementation 'org.springframework.boot:spring-boot-starter-data-jpa' }\n");
        Files.createDirectories(root.resolve("gradle/build/classes/java/main"));
        write(root.resolve("gradle/build/generated/x.js"), "debugger;\n");
        write(root.resolve("plain/src/build/Pkg.java"), "package build;\n\npublic class Pkg {\n}\n");
        return root;
    }

    static void write(Path p, String text) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    @Test
    void modulesTagsAndFolders() throws Exception {
        ProjectStack.Stack s = ProjectStack.detect(tree(tmp));
        List<String> got = s.modules().stream().map(m -> m.rel() + " " + m.tool() + " " + m.tags() + " " + m.buildOut() + " " + m.classes()).sorted().toList();
        assertEquals(List.of(
                "backend maven [boot, mybatis] [] [target/classes]",
                "egov maven [egov] [] []",
                "frontend npm [react, vite] [dist, out] []",
                "gradle gradle [boot, jpa] [build] [build/classes/java/main]"), got);
        assertEquals(List.of("frontend/dist/**", "frontend/out/**", "gradle/build/**"), s.excludes().stream().sorted().toList());
        assertEquals(List.of("backend/target/classes", "gradle/build/classes/java/main"), s.classesDirs().stream().sorted().toList());
        assertTrue(s.egov());
        assertFalse(s.excludes().stream().anyMatch(e -> e.startsWith("plain")), "매니페스트 없는 build 는 소스 패키지 — 안 뺀다");
        assertTrue(s.summary().contains("frontend/ npm · React · Vite · 제외 dist,out"), s.summary());
        assertTrue(s.summary().contains("backend/ Maven · Boot · MyBatis · 클래스 target/classes"), s.summary());
    }

    @Test
    void rootModuleAndNoModule() throws Exception {
        write(tmp.resolve("package.json"), "{\"dependencies\":{\"next\":\"15\",\"react\":\"19\"}}");
        Files.createDirectories(tmp.resolve("build"));
        ProjectStack.Stack s = ProjectStack.detect(tmp);
        assertEquals(List.of("build/**"), s.excludes());
        assertEquals("(루트) npm · React · Next · 제외 build", s.summary());
        assertFalse(s.egov());
        Path empty = Files.createDirectories(tmp.resolve("empty-x"));
        assertEquals("판별 못 함(package.json·pom.xml·build.gradle 없음)", ProjectStack.detect(empty).summary());
        assertEquals(List.of(), ProjectStack.detect(tmp.resolve("nope")).modules());
    }

    @Test
    void brokenOrHiddenManifestsAreSkipped() throws Exception {
        write(tmp.resolve("a/package.json"), "{ not json");
        write(tmp.resolve("a/dist/x.js"), "x\n");
        write(tmp.resolve("node_modules/r/package.json"), "{\"dependencies\":{\"react\":\"1\"}}");
        write(tmp.resolve(".cache/package.json"), "{\"dependencies\":{\"react\":\"1\"}}");
        write(tmp.resolve("b/c/d/e/f/package.json"), "{\"dependencies\":{\"react\":\"1\"}}"); // 깊이 5 — 안 본다
        ProjectStack.Stack s = ProjectStack.detect(tmp);
        assertEquals(List.of(), s.modules());
        assertEquals(List.of(), s.excludes());
    }

    @Test
    void manyModulesSummaryIsShort() throws Exception {
        for (int i = 0; i < 6; i++) {
            write(tmp.resolve("m" + i + "/pom.xml"), "<project><artifactId>m" + i + "</artifactId></project>");
        }
        ProjectStack.Stack s = ProjectStack.detect(tmp);
        assertEquals(6, s.modules().size());
        assertTrue(s.summary().endsWith(" 외 2"), s.summary());
    }
}
