package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 7-1 — 세트 읽기·extends·대괄호 보간·잠금(?new·?api·세트 밖 include) */
class TemplatesTest {

    @TempDir
    Path gen;

    void write(String rel, String text) throws IOException {
        Path p = gen.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text, StandardCharsets.UTF_8);
    }

    void base() throws IOException {
        write("base/set.yaml", "name: base\nvars:\n  rte: egovframework.rte\n  ee: javax\nfiles:\n"
                + "  - { template: A.java.ftl, path: 'src/[=packagePath]/[=Name].java' }\n");
        write("base/A.java.ftl", "package [=packageName];\nimport [=vars.rte].X;\nimport [=vars.ee].annotation.Resource;\n"
                + "<#list fields as f>[=f]\n</#list>EL ${param.x} BIND #{id}\n");
        write("child/set.yaml", "name: child\nextends: base\nvars:\n  ee: jakarta\n");
    }

    static Map<String, Object> model() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("packageName", "kr.go.x");
        m.put("packagePath", "kr/go/x");
        m.put("Name", "Emp");
        m.put("fields", List.of("a", "b"));
        return m;
    }

    static Map<String, Object> withVars(TemplateSet s) {
        Map<String, Object> m = model();
        m.put("vars", s.vars());
        return m;
    }

    @Test
    void rendersAndKeepsElAndBindAsText() throws Exception {
        base();
        TemplateSet s = TemplateSet.load(gen, "base");
        Templates t = new Templates(s);
        String out = t.render("A.java.ftl", withVars(s));
        assertEquals("package kr.go.x;\nimport egovframework.rte.X;\nimport javax.annotation.Resource;\na\nb\nEL ${param.x} BIND #{id}\n", out);
        assertEquals("src/kr/go/x/Emp.java", t.renderPath(s.files().get(0).path(), withVars(s)));
        assertEquals(List.of("base", "child"), TemplateSet.list(gen));
    }

    @Test
    void extendsOverridesVarsAndUsesParentTemplates() throws Exception {
        base();
        TemplateSet s = TemplateSet.load(gen, "child");
        assertEquals("jakarta", s.vars().get("ee"));
        assertEquals("egovframework.rte", s.vars().get("rte"));
        assertEquals(1, s.files().size(), "files 는 부모 것");
        assertTrue(new Templates(s).render("A.java.ftl", withVars(s)).contains("import jakarta.annotation.Resource;"));
    }

    @Test
    void newBuiltinCannotMakeClasses() throws Exception {
        base();
        write("base/Evil.ftl", "<#assign ex = \"freemarker.template.utility.Execute\"?new()>[=ex(\"whoami\")]");
        Templates t = new Templates(TemplateSet.load(gen, "base"));
        IOException e = assertThrows(IOException.class, () -> t.render("Evil.ftl", model()));
        assertTrue(e.getMessage().contains("그리기 실패"), e.getMessage());
    }

    @Test
    void apiBuiltinIsOff() throws Exception {
        base();
        write("base/Api.ftl", "[=fields?api.getClass().getName()]");
        Templates t = new Templates(TemplateSet.load(gen, "base"));
        assertThrows(IOException.class, () -> t.render("Api.ftl", model()));
    }

    @Test
    void includeCannotLeaveTheSetFolder() throws Exception {
        base();
        Files.writeString(gen.getParent().resolve("secret.txt"), "SECRET", StandardCharsets.UTF_8);
        write("base/Inc.ftl", "<#include \"../../secret.txt\">");
        Templates t = new Templates(TemplateSet.load(gen, "base"));
        IOException e = assertThrows(IOException.class, () -> t.render("Inc.ftl", model()));
        assertTrue(!e.getMessage().contains("SECRET"), e.getMessage());
    }

    @Test
    void badSetsSayWhy() throws Exception {
        write("nofiles/set.yaml", "name: nofiles\n");
        IllegalArgumentException a = assertThrows(IllegalArgumentException.class, () -> TemplateSet.load(gen, "nofiles"));
        assertTrue(a.getMessage().contains("files 가 없다"), a.getMessage());
        IllegalArgumentException b = assertThrows(IllegalArgumentException.class, () -> TemplateSet.load(gen, "none"));
        assertTrue(b.getMessage().contains("세트가 없다"), b.getMessage());
        assertThrows(IllegalArgumentException.class, () -> TemplateSet.load(gen, "../x"));
        base();
        IOException c = assertThrows(IOException.class, () -> new Templates(TemplateSet.load(gen, "base")).render("Missing.ftl", model()));
        assertTrue(c.getMessage().contains("템플릿이 없다"), c.getMessage());
    }
}
