package kr.ejg.toolbox.core.profile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.meta.Scope;

/**
 * 사업 하나의 설정. {@code profiles/<이름>.yaml} 과 1:1. {@code scope} 는 수집 범위 타입 {@link Scope} 를 그대로 쓴다.
 * 접속 비밀번호는 {@link Connection#password()} — 프로필 YAML 에만 두고 응답·로그·H2 로 내보내지 않는다(절대 규칙 2, 2026-10-06).
 */
public record Profile(
        String name,
        Project project,
        List<Connection> connections,
        String defaultConnection,
        Scope scope,
        Deliverable deliverable,
        Naming naming,
        CodeCheck codecheck,
        String framework,
        Output output,
        Generator generator,
        LogicalName logicalName) {

    public Profile {
        connections = connections == null ? List.of() : List.copyOf(connections);
    }

    /** 응답용 사본 — 접속 비밀번호를 뺀다(1-42) */
    public Profile withoutPasswords() {
        return new Profile(name, project, connections.stream().map(Connection::withoutPassword).toList(), defaultConnection,
                scope, deliverable, naming, codecheck, framework, output, generator, logicalName);
    }

    public record Project(String root, String encoding, String lineEnding, String vcs) {
    }

    /** DB 접속. password 는 프로필 YAML 에만 — 응답·로그·H2 로 안 나간다(1-42). 없으면 null */
    public record Connection(String id, String dialect, String url, String user, String password) {

        /**
         * URL 에 비밀번호를 넣는 모양을 막는다(2026-09-27 AI 리뷰) — url 은 화면 목록(/api/conn)에 그대로 나간다. password 칸에 넣는다.
         * `password=`·`pwd=` 파라미터, `//user:pass@host`, 오라클 thin `user/pass@`.
         */
        private static final java.util.regex.Pattern PASSWORD_IN_URL = java.util.regex.Pattern.compile(
                "(?i)(?:[?;&:]\\s*(?:password|passwd|pwd)\\s*=)|(?://[^/@\\s]+:[^/@\\s]+@)|(?::thin:[^@/\\s]+/[^@\\s]+@)");

        public Connection {
            if (url != null && PASSWORD_IN_URL.matcher(url).find()) {
                throw new IllegalArgumentException("접속 " + id + " 의 url 에 비밀번호가 들어 있다. url 에서 빼고 password 칸에 넣는다");
            }
        }

        public Connection(String id, String dialect, String url, String user) {
            this(id, dialect, url, user, null);
        }

        public Connection withoutPassword() {
            return new Connection(id, dialect, url, user, null);
        }

        /** 자동 toString 은 값을 찍는다 — 가린다 */
        @Override
        public String toString() {
            return "Connection[id=" + id + ", dialect=" + dialect + ", url=" + url + ", user=" + user
                    + ", password=" + (password == null ? "null" : "****") + "]";
        }
    }

    /** filter — 정의서 대상 표(2-15). scope 와 같은 규칙(include 가 먼저, 대소문자 무시, regex 는 로드 때 검증). 없으면 스냅샷의 표 전부 */
    public record Deliverable(String author, String org, String templateDir, String mapping, Scope filter) {
    }

    public record Naming(
            String controller,
            String service,
            String serviceImpl,
            String dao,
            String serviceImplBase,
            Boolean headerRequired) {
    }

    public record CodeCheck(Map<String, Boolean> groups, Map<String, Object> rules, String customRules) {
        public CodeCheck {
            // 수정 불가 사본. rules 값은 YAML 에서 null 일 수 있어 Map.copyOf 를 못 쓴다
            groups = groups == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(groups));
            rules = rules == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(rules));
        }
    }

    public record Output(String dir) {
    }

    public record Generator(String templateSet, String basePackage, String outDir) {
    }

    public record LogicalName(List<String> skipTokens) {
        public LogicalName {
            skipTokens = skipTokens == null ? List.of() : List.copyOf(skipTokens);
        }
    }
}
