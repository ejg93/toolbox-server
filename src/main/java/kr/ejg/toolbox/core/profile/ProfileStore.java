package kr.ejg.toolbox.core.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 프로필 YAML 로드·저장과 활성 프로필 이름.
 * 활성 프로필 이름은 {@code <dataDir>/active-profile} 한 줄. CLI {@code --profile} 이 있으면 그것이 우선이고 파일도 갱신한다.
 */
public final class ProfileStore {

    private static final String ACTIVE_FILE = "active-profile";

    private static final ObjectMapper YAML = new ObjectMapper(YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final Path profilesDir;
    private final Path dataDir;

    public ProfileStore(Path profilesDir, Path dataDir) {
        this.profilesDir = profilesDir;
        this.dataDir = dataDir;
    }

    public static Profile load(Path file) {
        try {
            return YAML.readValue(Files.readString(file, StandardCharsets.UTF_8), Profile.class);
        } catch (IOException e) {
            throw new UncheckedIOException("프로필을 못 읽었다: " + file, e);
        }
    }

    public static void save(Profile profile, Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, YAML.writeValueAsString(profile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프로필을 못 썼다: " + file, e);
        }
    }

    /** 폴더 안 {@code *.yaml} 의 이름(확장자 뺀 것), 정렬. 폴더가 없으면 빈 목록. */
    public static List<String> list(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".yaml"))
                    .map(n -> n.substring(0, n.length() - ".yaml".length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("프로필 폴더를 못 읽었다: " + dir, e);
        }
    }

    public List<String> list() {
        return list(profilesDir);
    }

    public Profile load(String name) {
        return load(profilesDir.resolve(name + ".yaml"));
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 코드 검사 화면의 체크 상태를 {@code codecheck.groups}·{@code codecheck.rules} 두 줄로만 갈아 끼운다(5-5).
     * 파일 전체를 다시 쓰면 YAML 주석(예시 프로필의 항목 설명)이 사라져 글을 잘라 붙인다 — 두 키의 원래 줄(딸린 줄 포함)을
     * 한 줄 흐름 꼴(JSON)로 바꾸고, 블록·키가 없으면 더한다. 결과를 다시 읽어 그 둘 말고 바뀐 것이 있으면 안 쓴다.
     */
    public Profile saveCodeCheck(String name, java.util.Map<String, Boolean> groups, java.util.Map<String, Object> rules) {
        Path file = profilesDir.resolve(name + ".yaml");
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            boolean crlf = raw.contains("\r\n");
            Profile before = YAML.readValue(raw, Profile.class);
            List<String> lines = new java.util.ArrayList<>(List.of(raw.replace("\r\n", "\n").split("\n", -1)));
            int c = -1;
            for (int i = 0; i < lines.size() && c < 0; i++) {
                c = lines.get(i).matches("codecheck:\\s*(#.*)?") ? i : -1;
            }
            if (c < 0) {
                if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                    lines.remove(lines.size() - 1);
                }
                lines.add("codecheck:");
                lines.add("");
                c = lines.size() - 2;
            }
            int end = c + 1;
            while (end < lines.size() && (lines.get(end).isBlank() || Character.isWhitespace(lines.get(end).charAt(0)))) {
                end++;
            }
            String indent = "  ";
            for (int i = c + 1; i < end; i++) {
                String l = lines.get(i);
                if (!l.isBlank() && !l.strip().startsWith("#")) {
                    indent = l.substring(0, l.length() - l.stripLeading().length());
                    break;
                }
            }
            end = splice(lines, c, end, indent, "rules", JSON.writeValueAsString(rules == null ? java.util.Map.of() : rules));
            splice(lines, c, end, indent, "groups", JSON.writeValueAsString(groups == null ? java.util.Map.of() : groups));
            String text = String.join("\n", lines);
            Profile after = YAML.readValue(text, Profile.class);
            Profile.CodeCheck want = new Profile.CodeCheck(groups, rules,
                    before.codecheck() == null ? null : before.codecheck().customRules());
            Profile expected = new Profile(before.name(), before.project(), before.connections(), before.defaultConnection(), before.scope(),
                    before.deliverable(), before.naming(), want, before.framework(), before.output(), before.generator(), before.logicalName());
            if (!expected.equals(after)) {
                throw new IllegalStateException("codecheck 만 바꾸려 했는데 다른 값도 바뀐다 — 쓰지 않았다(YAML 모양을 손으로 고칠 것)");
            }
            Files.writeString(file, crlf ? text.replace("\n", "\r\n") : text, StandardCharsets.UTF_8);
            return after;
        } catch (IOException e) {
            throw new UncheckedIOException("프로필을 못 썼다: " + file, e);
        }
    }

    /** 블록 [c, end) 안 {@code <indent>key:} 줄과 딸린 줄을 한 줄로. 없으면 블록 머리 뒤에 더한다. 새 end 를 돌려준다 */
    private static int splice(List<String> lines, int c, int end, String indent, String key, String json) {
        String line = indent + key + ": " + json;
        for (int i = c + 1; i < end; i++) {
            if (lines.get(i).startsWith(indent + key + ":")) {
                int j = i + 1;
                while (j < end && (lines.get(j).isBlank() || lines.get(j).length() - lines.get(j).stripLeading().length() > indent.length())) {
                    j++;
                }
                while (j > i + 1 && lines.get(j - 1).isBlank()) {
                    j--; // 블록 사이 빈 줄은 둔다
                }
                lines.subList(i, j).clear();
                lines.add(i, line);
                return end - (j - i) + 1;
            }
        }
        lines.add(c + 1, line);
        return end + 1;
    }

    public Optional<String> active() {
        Path f = dataDir.resolve(ACTIVE_FILE);
        if (!Files.isRegularFile(f)) {
            return Optional.empty();
        }
        try {
            String name = Files.readString(f, StandardCharsets.UTF_8).trim();
            return name.isEmpty() ? Optional.empty() : Optional.of(name);
        } catch (IOException e) {
            throw new UncheckedIOException("활성 프로필 파일을 못 읽었다: " + f, e);
        }
    }

    public void setActive(String name) {
        try {
            Files.createDirectories(dataDir);
            Files.writeString(dataDir.resolve(ACTIVE_FILE), name + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("활성 프로필 파일을 못 썼다: " + dataDir, e);
        }
    }

    /** CLI 인자가 있으면 그것을 활성으로 적고 돌려준다. 없으면 파일 값. */
    public Optional<String> resolveActive(String cliProfile) {
        if (cliProfile != null && !cliProfile.isBlank()) {
            setActive(cliProfile);
            return Optional.of(cliProfile);
        }
        return active();
    }
}
