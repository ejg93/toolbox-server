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
