package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.check.Source;
import org.junit.jupiter.api.Test;

/**
 * 6-14 — JPA 색인. 픽스처: @Table 있음(스키마)·없음(짓기)·@MappedSuperclass·SINGLE_TABLE 자식·JOINED 자식·@Entity(name)·@SecondaryTable·@JoinTable·
 * @CollectionTable·R2DBC @Table·javax·jakarta·와일드카드 import · 저장소(파생·JPQL·네이티브·텍스트 블록·모르는 이름·모르는 엔티티·커스텀·바탕 사슬·
 * 다른 패키지 @Query) · 모듈마다 같은 엔티티 이름
 */
class JpaIndexTest {

    static final Path DIR = Path.of("src/test/resources/fixtures/analyze/jpa");

    static List<Source> sources() throws IOException {
        List<Source> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(DIR)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                String rel = DIR.relativize(p).toString().replace('\\', '/');
                out.add(new Source(rel, Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n"), null, null, null));
            }
        }
        return out;
    }

    /** 색인 + 저장소마다 선언 메서드와 상속 메서드 넷(findById·save·deleteById·flush)의 표·글자 */
    @Test
    void golden() throws IOException {
        JpaIndex x = JpaIndex.scan(sources());
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("entities", x.entities());
        g.put("repositories", x.repositories());
        Map<String, Object> refs = new TreeMap<>();
        List<Unresolved> out = new ArrayList<>();
        for (JpaIndex.Repo r : x.repositories()) {
            List<String> methods = new ArrayList<>(List.of("findById", "save", "deleteById", "flush"));
            Files.readAllLines(DIR.resolve(r.file())).stream().map(String::trim).filter(l -> l.matches("^[\\w.<>]+ \\w+\\(.*\\);$"))
                    .map(l -> l.substring(l.indexOf(' ') + 1, l.indexOf('('))).forEach(methods::add);
            for (String m : methods) {
                Map<String, String> letters = new TreeMap<>();
                x.refs(r.fqcn(), m, out).forEach(ref -> letters.put(ref.table(), ref.letters()));
                refs.put(r.simpleName() + "." + m, letters);
            }
        }
        g.put("refs", refs);
        g.put("unresolved", x.unresolved());
        g.put("refsUnresolved", out);
        GoldenFiles.assertJson("analyze/jpa-index.json", g);
    }

    @Test
    void nameRules() {
        assertEquals(EnumSet.of(SqlTables.Crud.R), JpaIndex.crud("existsByEmail"));
        assertEquals(EnumSet.of(SqlTables.Crud.C, SqlTables.Crud.U), JpaIndex.crud("saveAllAndFlush"));
        assertEquals(EnumSet.of(SqlTables.Crud.D), JpaIndex.crud("removeByCode"));
        assertNull(JpaIndex.crud("archive"));
        assertEquals("BBS_MASTER", JpaIndex.snake("BbsMaster"));
        assertEquals("URL_INFO", JpaIndex.snake("URLInfo"));
        assertEquals("TB_X", JpaIndex.norm("\"sch\".\"tb_x\""));
        assertEquals("mod-b/", JpaIndex.module("mod-b/src/main/java/com/ex/b/Notice.java"));
    }
}
