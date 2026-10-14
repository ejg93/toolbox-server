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

    /**
     * filter — 정의서 대상 표(2-15). scope 와 같은 규칙(include 가 먼저, 대소문자 무시, regex 는 로드 때 검증). 없으면 스냅샷의 표 전부.
     * business — 02 「분류명」(2-25). 업무명 → 표 이름 접두 또는 정규식 목록, YAML 에 적은 순서가 우선순위
     */
    public record Deliverable(String author, String org, String templateDir, String mapping, Scope filter, Map<String, List<String>> business,
            Lint lint) {
        public Deliverable {
            Map<String, List<String>> b = new LinkedHashMap<>();
            if (business != null) {
                business.forEach((k, v) -> b.put(k, v == null ? List.of() : List.copyOf(v)));
            }
            business = Collections.unmodifiableMap(b);
            // 잘못된 정규식은 정의서를 만들 때가 아니라 프로필을 읽을 때 터지게(scope.exclude.regex 와 같은 결)
            b.forEach((k, v) -> v.forEach(r -> {
                if (isPattern(r)) {
                    try {
                        java.util.regex.Pattern.compile(r);
                    } catch (java.util.regex.PatternSyntaxException e) {
                        throw new IllegalArgumentException("deliverable.business." + k + " 가 정규식이 아니다: " + r, e);
                    }
                }
            }));
        }

        /** 2-25 — 글에 정규식 글자({@code ^ $ . [ ( * \}) 가 있으면 정규식(표 이름 전체, 대소문자 무시), 없으면 접두 */
        public static boolean isPattern(String s) {
            return s != null && s.chars().anyMatch(ch -> "^$.[(*\\".indexOf(ch) >= 0);
        }
    }

    /**
     * 91 구조 점검 기준(2-28b). maxColumns 0·null 은 안 봄. requiredColumns 비면 안 봄. off 는 끌 규칙 id.
     * 규칙 id 검증은 여기서 안 한다(core/profile → core/deliverable 순환을 안 만들려고) — 모르는 id 는 API 400·CLI 1
     */
    public record Lint(Integer maxColumns, List<String> requiredColumns, List<String> off) {
        public Lint {
            requiredColumns = requiredColumns == null ? List.of() : List.copyOf(requiredColumns);
            off = off == null ? List.of() : List.copyOf(off);
        }
    }

    public record Naming(
            String controller,
            String service,
            String serviceImpl,
            String dao,
            String serviceImplBase,
            Boolean headerRequired) {
    }

    /** exclude — 폴더 검사에서 뺄 상대 경로 글롭(5-26). 고정으로 빼는 .git·target·node_modules·숨김 위에 더한다 */
    public record CodeCheck(Map<String, Boolean> groups, Map<String, Object> rules, String customRules, List<String> exclude) {
        public CodeCheck {
            // 수정 불가 사본. rules 값은 YAML 에서 null 일 수 있어 Map.copyOf 를 못 쓴다
            groups = groups == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(groups));
            rules = rules == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(rules));
            exclude = exclude == null ? List.of() : List.copyOf(exclude);
        }

        public CodeCheck(Map<String, Boolean> groups, Map<String, Object> rules, String customRules) {
            this(groups, rules, customRules, null);
        }
    }

    public record Output(String dir) {
    }

    /** tables — 표 이름(대소문자 무시) → 표별 재정의(7-15). 스키마는 안 본다 — 같은 이름 표가 스키마 둘에 있으면 둘 다 */
    public record Generator(String templateSet, String basePackage, String outDir, Map<String, TableGen> tables) {
        public Generator {
            tables = Collections.unmodifiableMap(tables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tables));
        }
    }

    /** className — 비지 않으면 그 이름 그대로(PascalCase 를 믿는다). skipColumns — 생성에서 뺄 컬럼(PK 는 안 빠진다) */
    public record TableGen(String className, List<String> skipColumns) {
        public TableGen {
            skipColumns = skipColumns == null ? List.of() : List.copyOf(skipColumns);
        }
    }

    public record LogicalName(List<String> skipTokens) {
        public LogicalName {
            skipTokens = skipTokens == null ? List.of() : List.copyOf(skipTokens);
        }
    }
}
