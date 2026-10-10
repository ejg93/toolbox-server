package kr.ejg.toolbox.core.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
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

    /** 프로필 YAML 의 전체 경로 — 저장 알림에 이름까지 보인다 */
    public Path file(String name) {
        return profilesDir.resolve(name + ".yaml").toAbsolutePath();
    }

    /**
     * 실패 글은 「프로필을 못 읽었다: <파일> — <사유>」. 파서 글에는 그 줄 원문(비밀번호일 수 있다)이 실리므로
     * 사유는 줄·칸·모르는 키 이름·생성자 검증 글만 쓰고 원인 예외를 잇지 않는다(1-41)
     */
    public static Profile load(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new UncheckedIOException("프로필을 못 읽었다: " + file + " — 파일이 없다", e);
        } catch (IOException e) {
            throw new UncheckedIOException("프로필을 못 읽었다: " + file + " — " + e.getClass().getSimpleName(), e);
        }
        try {
            return YAML.readValue(text, Profile.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("프로필을 못 읽었다: " + file + " — " + reason(e), new IOException(e.getClass().getSimpleName()));
        }
    }

    /** 원문 없는 사유 — 줄·칸 + (모르는 키 이름 | 이 저장소가 만든 검증 글 | 형식 틀림) */
    static String reason(JsonProcessingException e) {
        JsonLocation l = e.getLocation();
        String at = l == null ? "" : l.getLineNr() + "줄 " + l.getColumnNr() + "칸 근처 ";
        if (e instanceof UnrecognizedPropertyException u) {
            return at + "모르는 키 " + u.getPropertyName();
        }
        if (e.getCause() instanceof IllegalArgumentException iae) {
            return at + iae.getMessage();
        }
        return at + "YAML 형식이 틀렸다";
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
        Path file = file(name);
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            boolean crlf = raw.contains("\r\n");
            Profile before = YAML.readValue(raw, Profile.class);
            // 5-20 — 묶음은 병합: 화면이 보낸 키만 덮고 화면에 없는 키(pmd: false 등)는 YAML 그대로 둔다
            java.util.Map<String, Boolean> merged = new java.util.LinkedHashMap<>();
            if (before.codecheck() != null && before.codecheck().groups() != null) {
                merged.putAll(before.codecheck().groups());
            }
            if (groups != null) {
                merged.putAll(groups);
            }
            // 번들 37 리뷰 — 규칙도 병합. 화면은 이번에 바꾼 규칙만 보낸다 — 통째로 갈면 프로필의 기존 끔·정규식 덮어쓰기가 사라졌다.
            // 들어온 값이 켬·끔(Boolean)뿐이고 앞 값이 {enabled, regex} 면 정규식은 남기고 enabled 만 바꾼다
            java.util.Map<String, Object> mergedRules = new java.util.LinkedHashMap<>();
            if (before.codecheck() != null && before.codecheck().rules() != null) {
                mergedRules.putAll(before.codecheck().rules());
            }
            if (rules != null) {
                rules.forEach((id, v) -> {
                    Object old = mergedRules.get(id);
                    if (v instanceof Boolean on && old instanceof java.util.Map<?, ?> m && m.containsKey("regex")) {
                        java.util.Map<String, Object> keep = new java.util.LinkedHashMap<>();
                        m.forEach((k, x) -> keep.put(String.valueOf(k), x));
                        keep.put("enabled", on);
                        mergedRules.put(id, keep);
                    } else {
                        mergedRules.put(id, v);
                    }
                });
            }
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
            end = splice(lines, c, end, indent, "rules", JSON.writeValueAsString(mergedRules));
            splice(lines, c, end, indent, "groups", JSON.writeValueAsString(merged));
            String text = String.join("\n", lines);
            Profile after = YAML.readValue(text, Profile.class);
            Profile.CodeCheck want = new Profile.CodeCheck(merged, mergedRules,
                    before.codecheck() == null ? null : before.codecheck().customRules());
            Profile expected = new Profile(before.name(), before.project(), before.connections(), before.defaultConnection(), before.scope(),
                    before.deliverable(), before.naming(), want, before.framework(), before.output(), before.generator(), before.logicalName());
            if (!expected.equals(after)) {
                throw new IllegalStateException("codecheck 만 바꾸려 했는데 다른 값도 바뀐다 — 쓰지 않았다(YAML 모양을 손으로 고칠 것)");
            }
            Files.writeString(file, crlf ? text.replace("\n", "\r\n") : text, StandardCharsets.UTF_8);
            return after;
        } catch (JsonProcessingException e) {
            // 1-41 과 같다 — 파서 글의 원문(password 줄일 수 있다)을 잇지 않는다(PR #48 AI 리뷰)
            throw new UncheckedIOException("프로필을 못 썼다: " + file + " — " + reason(e), new IOException(e.getClass().getSimpleName()));
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
