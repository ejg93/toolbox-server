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

    /** 프로필 바꿔 끼우기 재시도(Windows 읽는 중 잠깐 거절, G1-K20) */
    private static final int MOVE_TRIES = 50;
    private static final String ACTIVE_FILE = "active-profile";

    private static final ObjectMapper YAML = new ObjectMapper(YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION) // G2-K4 — 같은 키 둘
            .build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS) // G2-K4 — 둘째 문서
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static final ObjectMapper JSON = new ObjectMapper();

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
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new UncheckedIOException("프로필을 못 읽었다: " + file + " — UTF-8 이 아니다(UTF-8 로 저장한다)", e);
        } catch (IOException e) {
            throw new UncheckedIOException("프로필을 못 읽었다: " + file + " — " + e.getClass().getSimpleName(), e);
        }
        refuseAliases(text, file);
        try {
            return YAML.readValue(text, Profile.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("프로필을 못 읽었다: " + file + " — " + reason(e), new IOException(e.getClass().getSimpleName()));
        }
    }

    /** 별칭(*이름)·병합 키(<<)는 Jackson YAML 이 안 풀어 값이 틀리게 읽힌다 — 줄·칸만 말하고 거절(G2-K3). 원문은 안 싣는다 */
    private static void refuseAliases(String text, Path file) {
        String why = null;
        try (com.fasterxml.jackson.dataformat.yaml.YAMLParser p = (com.fasterxml.jackson.dataformat.yaml.YAMLParser) YAML.getFactory().createParser(text)) {
            for (com.fasterxml.jackson.core.JsonToken t = p.nextToken(); t != null && why == null; t = p.nextToken()) {
                JsonLocation l = p.currentLocation();
                String at = l.getLineNr() + "줄 " + l.getColumnNr() + "칸 근처 ";
                if (p.isCurrentAlias()) {
                    why = at + "별칭(*이름)은 안 받는다 — 값을 그대로 적는다";
                } else if (t == com.fasterxml.jackson.core.JsonToken.FIELD_NAME && "<<".equals(p.currentName())) {
                    why = at + "병합 키(<<)는 안 받는다 — 값을 그대로 적는다";
                }
            }
        } catch (IOException e) {
            return; // 형식 오류는 뒤의 readValue 가 지금 글로 낸다
        }
        if (why != null) {
            throw new UncheckedIOException("프로필을 못 읽었다: " + file + " — " + why, new IOException("alias"));
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
        String m = String.valueOf(e.getOriginalMessage());
        if (m.startsWith("Duplicate field '")) {
            return at + "같은 키가 두 번: " + m.substring("Duplicate field '".length(), Math.max("Duplicate field '".length(), m.indexOf('\'', "Duplicate field '".length())));
        }
        if (m.contains("Trailing token")) {
            return at + "문서가 둘 이상이다 — --- 로 나눈 뒷부분을 지운다";
        }
        return at + "YAML 형식이 틀렸다";
    }

    /** 갈아 끼운 글을 다시 읽는다 — 깨지면(흐름 꼴 머리에 블록이 붙어 중복 키 등) 쓰지 않는다(409, G2-K5) */
    private static Profile reread(String text) {
        try {
            return YAML.readValue(text, Profile.class);
        } catch (JsonProcessingException e) {
            // 원인 글은 잇지 않는다 — 파서 글에 원문(비밀번호일 수 있다)이 실린다(1-41)
            throw new IllegalStateException("codecheck 만 바꾸려 했는데 YAML 이 깨진다 — 쓰지 않았다(YAML 모양을 손으로 고칠 것)",
                    new IOException(e.getClass().getSimpleName()));
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

    /**
     * 규칙 덮어쓰기 병합 — 들어온 값이 켬·끔(Boolean)뿐이고 앞 값이 {regex}·{params} 맵이면 그 맵의 enabled 만 바꾼다(5-32c), 그 밖은 들어온 값이 덮는다.
     * 화면은 이번에 바꾼 규칙만 보낸다 — 통째로 갈면 프로필의 기존 끔·정규식 덮어쓰기가 사라진다(번들 37 리뷰).
     * 미리 적재(ProfileRoutes.putCodeCheck)와 저장(saveCodeCheck)이 같이 쓴다(0-117)
     */
    public static java.util.Map<String, Object> mergeRules(java.util.Map<String, Object> before, java.util.Map<String, Object> incoming) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (before != null) {
            out.putAll(before);
        }
        if (incoming != null) {
            incoming.forEach((id, v) -> out.put(id, mergedRule(out.get(id), v)));
        }
        return out;
    }

    private static Object mergedRule(Object old, Object v) {
        if (v instanceof Boolean on && old instanceof java.util.Map<?, ?> m && (m.containsKey("regex") || m.containsKey("params"))) {
            java.util.Map<String, Object> keep = new java.util.LinkedHashMap<>();
            m.forEach((k, x) -> keep.put(String.valueOf(k), x));
            keep.put("enabled", on);
            return keep;
        }
        return v;
    }

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
            java.util.Map<String, Object> mergedRules = mergeRules(before.codecheck() == null ? null : before.codecheck().rules(), rules);
            List<String> lines = new java.util.ArrayList<>(List.of(raw.replace("\r\n", "\n").split("\n", -1)));
            int c = -1;
            for (int i = 0; i < lines.size() && c < 0; i++) {
                c = lines.get(i).matches("\uFEFF?(?:codecheck|\"codecheck\"|'codecheck'):\\s*(#.*)?") ? i : -1; // G2-K5 — 따옴표 키·BOM 첫 줄
            }
            if (c < 0) {
                if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
                    lines.remove(lines.size() - 1);
                }
                lines.add("codecheck:");
                lines.add("");
                c = lines.size() - 2;
            }
            int end = blockEnd(lines, c);
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
            Profile after = reread(text);
            Profile.CodeCheck cc = before.codecheck();
            Profile.CodeCheck want = new Profile.CodeCheck(merged, mergedRules, cc == null ? null : cc.customRules(), cc == null ? null : cc.exclude(),
                    cc == null ? null : cc.classesDir(), cc == null ? null : cc.libDir());
            Profile expected = new Profile(before.name(), before.project(), before.connections(), before.defaultConnection(), before.scope(),
                    before.deliverable(), before.naming(), want, before.framework(), before.output(), before.generator(), before.logicalName());
            if (!expected.equals(after)) {
                throw new IllegalStateException("codecheck 만 바꾸려 했는데 다른 값도 바뀐다 — 쓰지 않았다(YAML 모양을 손으로 고칠 것)");
            }
            replace(file, crlf ? text.replace("\n", "\r\n") : text); // G1-K20 — 읽는 쪽이 반쯤 쓴 파일을 안 본다
            return after;
        } catch (JsonProcessingException e) {
            // 1-41 과 같다 — 파서 글의 원문(password 줄일 수 있다)을 잇지 않는다(PR #48 AI 리뷰)
            throw new UncheckedIOException("프로필을 못 썼다: " + file + " — " + reason(e), new IOException(e.getClass().getSimpleName()));
        } catch (IOException e) {
            throw new UncheckedIOException("프로필을 못 썼다: " + file, e);
        }
    }

    /** 블록 [c, end) 의 끝 — 들여쓴 줄·빈 줄까지. 0열 주석은 건너뛰고 뒤 줄이 정한다(G2-K14) */
    private static int blockEnd(List<String> lines, int c) {
        int end = c + 1;
        for (int i = c + 1; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.startsWith("#")) {
                continue;
            }
            if (!l.isBlank() && !Character.isWhitespace(l.charAt(0))) {
                break;
            }
            end = i + 1;
        }
        return end;
    }

    /** [from, to) 의 0열 주석 — 한 줄로 갈아 끼울 때 남긴다(0-104) */
    private static List<String> columnZeroComments(List<String> lines, int from, int to) {
        List<String> out = new java.util.ArrayList<>();
        for (int k = from; k < to; k++) {
            if (lines.get(k).startsWith("#")) {
                out.add(lines.get(k));
            }
        }
        return out;
    }

    /** 블록 [c, end) 안 {@code <indent>key:} 줄과 딸린 줄을 한 줄로. 없으면 블록 머리 뒤에 더한다. 새 end 를 돌려준다 */
    private static int splice(List<String> lines, int c, int end, String indent, String key, String json) {
        String line = indent + key + ": " + json;
        for (int i = c + 1; i < end; i++) {
            if (lines.get(i).startsWith(indent + key + ":")) {
                int j = i + 1;
                while (j < end && (lines.get(j).isBlank() || lines.get(j).startsWith("#")
                        || lines.get(j).length() - lines.get(j).stripLeading().length() > indent.length())) {
                    j++;
                }
                while (j > i + 1 && (lines.get(j - 1).isBlank() || lines.get(j - 1).startsWith("#"))) {
                    j--; // 블록 사이 빈 줄·0열 주석은 둔다
                }
                List<String> notes = columnZeroComments(lines, i + 1, j);
                lines.subList(i, j).clear();
                lines.add(i, line);
                lines.addAll(i + 1, notes);
                return end - (j - i) + 1 + notes.size();
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

    /** 임시 파일에 쓰고 바꿔 끼운다 — 읽는 쪽이 반쯤 쓴 파일을 안 본다(G1-K20). 원자적 이동이 안 되는 파일 시스템이면 그냥 덮는다 */
    /** 임시 파일에 쓰고 바꿔 끼운다 — 어느 길로 실패해도 임시 파일(프로필 전문 — password 줄 포함)은 지운다(마무리 리뷰 I4, 절대 규칙 2) */
    static void replace(Path file, String text) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            for (int attempt = 1;; attempt++) {
                try {
                    move(tmp, file);
                    return;
                } catch (java.nio.file.AccessDeniedException e) {
                    if (attempt >= MOVE_TRIES) {
                        throw e;
                    }
                    pause(); // Windows — 다른 요청이 그 파일을 읽는 중이면 바꿔 끼우기가 잠깐 거절된다
                }
            }
        } finally {
            Files.deleteIfExists(tmp); // 옮긴 뒤면 없다
        }
    }

    private static void move(Path tmp, Path file) throws IOException {
        try {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void pause() throws IOException {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            java.io.InterruptedIOException x = new java.io.InterruptedIOException("프로필 저장이 끊겼다");
            x.initCause(e);
            throw x;
        }
    }
}
