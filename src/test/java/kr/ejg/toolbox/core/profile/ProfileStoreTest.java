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
        assertEquals(List.of("TMP_", "BAK_"), p.deliverable().filter().exclude().prefixes(), "2-15 deliverable.filter");
        assertEquals(List.of("_\\d{8}$"), p.deliverable().filter().exclude().regex());
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

    /** 절대 규칙 2 — url 에 비밀번호를 넣어도 막힌다(2026-09-27 AI 리뷰) */
    @Test
    void passwordInUrlIsRejected() {
        for (String url : List.of(
                "jdbc:postgresql://h:5432/db?user=a&password=secret",
                "jdbc:mariadb://h/db;pwd=secret",
                "jdbc:postgresql://app:secret@h:5432/db",
                "jdbc:oracle:thin:app/secret@h:1521/SID")) {
            assertThrows(IllegalArgumentException.class, () -> new Profile.Connection("dev", "x", url, "app"), url);
        }
        new Profile.Connection("dev", "oracle", "jdbc:oracle:thin:@host:1521/SID", "APP");
        new Profile.Connection("dev", "postgresql", "jdbc:postgresql://host:5432/db?ssl=true", "app");
        new Profile.Connection("dev", "mssql", "jdbc:sqlserver://host:1433;databaseName=db", "app");
    }

    @Test
    void badRegexInProfileFailsAtLoad() throws Exception {
        Path f = tmp.resolve("badre.yaml");
        Files.writeString(f, "name: x\nscope:\n  exclude:\n    regex: ['_\\d{8']\n", StandardCharsets.UTF_8);
        assertThrows(RuntimeException.class, () -> ProfileStore.load(f));
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

    /** 5-5 — codecheck 두 키만 갈아 끼운다: 주석·다른 키·CRLF 유지, 여러 줄 블록, 블록 없음 */
    @Test
    void saveCodeCheckKeepsComments(@TempDir Path dir) throws Exception {
        Path profiles = dir.resolve("p");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("a.yaml"), "name: a\r\n# 머리 주석\r\ncodecheck:\r\n  # 묶음 설명\r\n  groups: { java: true }\r\n"
                + "  # 덮어쓰기 설명\r\n  rules:\r\n    common.todo: false\r\n    common.sysout:\r\n      severity: info\r\n\r\n"
                + "  customRules: rules/a.yaml\r\n\r\n# 꼬리 주석\r\nframework: egov35\r\n", StandardCharsets.UTF_8);
        Files.writeString(profiles.resolve("b.yaml"), "name: b\nframework: spring\n", StandardCharsets.UTF_8);
        ProfileStore store = new ProfileStore(profiles, dir.resolve("data"));
        java.util.Map<String, Object> rules = new java.util.LinkedHashMap<>();
        rules.put("common.todo", true);
        rules.put("java.naming", java.util.Map.of("severity", "error"));
        Profile a = store.saveCodeCheck("a", java.util.Map.of("java", false), rules);
        assertEquals(false, a.codecheck().groups().get("java"));
        assertEquals("rules/a.yaml", a.codecheck().customRules());
        String text = Files.readString(profiles.resolve("a.yaml"), StandardCharsets.UTF_8);
        assertTrue(text.contains("# 머리 주석\r\n") && text.contains("  # 묶음 설명\r\n") && text.contains("  # 덮어쓰기 설명\r\n")
                && text.contains("# 꼬리 주석\r\nframework: egov35"), text);
        assertTrue(!text.contains("common.sysout"), "옛 여러 줄 블록은 사라진다: " + text);
        assertEquals(a, store.load("a"));
        Profile b = store.saveCodeCheck("b", java.util.Map.of("tsx", true), java.util.Map.of());
        assertEquals(true, b.codecheck().groups().get("tsx"));
        assertEquals("spring", store.load("b").framework());
    }

    /** 5-20 — 화면이 보낸 묶음만 덮는다. 화면에 없는 묶음(pmd: false)은 남는다 — 안 그러면 5-7 이 열릴 때 끈 묶음이 저장 한 번에 켜진다 */
    @Test
    void saveCodeCheckMergesGroups(@TempDir Path dir) throws Exception {
        Path profiles = dir.resolve("p");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("a.yaml"), "name: a\ncodecheck:\n  groups: { java: true, pmd: false }\n", StandardCharsets.UTF_8);
        ProfileStore store = new ProfileStore(profiles, dir.resolve("data"));
        Profile saved = store.saveCodeCheck("a", java.util.Map.of("java", false, "jsp", true), java.util.Map.of());
        assertEquals(false, saved.codecheck().groups().get("pmd"), "화면에 없는 pmd 는 남는다 — " + saved.codecheck().groups());
        assertEquals(false, saved.codecheck().groups().get("java"));
        assertEquals(true, saved.codecheck().groups().get("jsp"));
        assertEquals(saved, store.load("a"));
    }

    /** 2-15 — deliverable.filter 왕복·코드 검사 저장 뒤에도 남는다·잘못된 regex 는 로드 실패 */
    @Test
    void deliverableFilterSurvives(@TempDir Path dir) throws Exception {
        Path profiles = dir.resolve("p");
        Files.createDirectories(profiles);
        Files.writeString(profiles.resolve("a.yaml"), "name: a\ndeliverable:\n  author: 홍\n  filter:\n    include: { tables: [TB_A] }\n"
                + "    exclude: { prefixes: [TMP_] }\ncodecheck:\n  groups: { java: true }\n", StandardCharsets.UTF_8);
        ProfileStore store = new ProfileStore(profiles, dir.resolve("data"));
        Profile a = store.load("a");
        assertEquals(List.of("TB_A"), a.deliverable().filter().include().tables());
        Profile saved = store.saveCodeCheck("a", java.util.Map.of("java", false), java.util.Map.of());
        assertEquals(a.deliverable(), saved.deliverable(), "코드 검사 저장이 filter 를 안 지운다");
        assertEquals(a.deliverable(), store.load("a").deliverable());
        Files.writeString(profiles.resolve("bad.yaml"), "name: bad\ndeliverable:\n  filter:\n    exclude: { regex: ['(unclosed'] }\n",
                StandardCharsets.UTF_8);
        assertThrows(RuntimeException.class, () -> store.load("bad"), "잘못된 regex 는 로드 때 실패");
    }
}
