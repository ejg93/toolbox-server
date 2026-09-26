package kr.ejg.toolbox.core.profile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.meta.Scope;

/**
 * 사업 하나의 설정. {@code profiles/<이름>.yaml} 과 1:1. {@code scope} 는 수집 범위 타입 {@link Scope} 를 그대로 쓴다.
 * 비밀번호 필드는 두지 않는다 — 비밀번호는 메모리에만 있다(절대 규칙 2).
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

    public record Project(String root, String encoding, String lineEnding, String vcs) {
    }

    /** DB 접속. 비밀번호 없음. */
    public record Connection(String id, String dialect, String url, String user) {
    }

    public record Deliverable(String author, String org, String templateDir, String mapping) {
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
