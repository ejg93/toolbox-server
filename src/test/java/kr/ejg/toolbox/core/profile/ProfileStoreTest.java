package kr.ejg.toolbox.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileStoreTest {

    private static final Path EXAMPLE = Path.of("profiles/example.yaml");

    @TempDir
    Path tmp;

    @Test
    void exampleRoundTrips() {
        Profile first = ProfileStore.load(EXAMPLE);
        Path copy = tmp.resolve("copy.yaml");
        ProfileStore.save(first, copy);
        Profile second = ProfileStore.load(copy);
        assertEquals(first, second);
    }

    @Test
    void exampleMapsEveryField() {
        Profile p = ProfileStore.load(EXAMPLE);
        assertEquals("example", p.name());
        assertEquals("svn", p.project().vcs());
        assertEquals(1, p.connections().size());
        assertEquals("oracle", p.connections().get(0).dialect());
        assertEquals("dev", p.defaultConnection());
        assertEquals(List.of("APP", "CMM"), p.scope().schemas());
        assertEquals(List.of("_\\d{8}$"), p.scope().exclude().regex());
        assertEquals(List.of(), p.scope().include().tables());
        assertEquals(Boolean.FALSE, p.scope().skipEmpty());
        assertEquals("홍길동", p.deliverable().author());
        assertEquals("^[A-Z]\\w*Controller$", p.naming().controller());
        assertEquals(Boolean.TRUE, p.naming().headerRequired());
        assertEquals(Boolean.FALSE, p.codecheck().groups().get("jsp"));
        assertEquals("egov35", p.framework());
        assertEquals("out/", p.output().dir());
        assertEquals("kr.go.xxx", p.generator().basePackage());
        assertEquals(List.of("TB", "TBL"), p.logicalName().skipTokens());
    }

    @Test
    void unknownKeyFails() throws Exception {
        Path f = tmp.resolve("typo.yaml");
        Files.writeString(f, "name: x\nconection: []\n", StandardCharsets.UTF_8);
        assertThrows(RuntimeException.class, () -> ProfileStore.load(f));
    }

    @Test
    void loadsWithoutConnections() throws Exception {
        Path f = tmp.resolve("bare.yaml");
        Files.writeString(f, "name: bare\n", StandardCharsets.UTF_8);
        Profile p = ProfileStore.load(f);
        assertEquals("bare", p.name());
        assertTrue(p.connections().isEmpty());
    }

    @Test
    void connectionHasNoPasswordField() {
        boolean any = Arrays.stream(Profile.Connection.class.getRecordComponents())
                .anyMatch(c -> c.getName().toLowerCase(Locale.ROOT).contains("pass"));
        assertFalse(any, "Connection 에 비밀번호 필드를 두지 않는다(절대 규칙 2)");
    }

    @Test
    void activeProfileFileAndCliOverride() throws Exception {
        Path profiles = tmp.resolve("profiles");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("b.yaml"), "name: b\n", StandardCharsets.UTF_8);
        Files.writeString(profiles.resolve("a.yaml"), "name: a\n", StandardCharsets.UTF_8);
        ProfileStore store = new ProfileStore(profiles, tmp.resolve("data"));

        assertEquals(List.of("a", "b"), store.list());
        assertEquals(Optional.empty(), store.active());

        store.setActive("a");
        assertEquals(Optional.of("a"), store.active());
        assertEquals(Optional.of("a"), store.resolveActive(null));

        assertEquals(Optional.of("b"), store.resolveActive("b"));
        assertEquals(Optional.of("b"), store.active());
        assertEquals("b", store.load("b").name());
    }
}
