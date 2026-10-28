package kr.ejg.toolbox.core.analyze;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import kr.ejg.toolbox.CorpusFiles;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.gen.DdlReader;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * V-37(설계 41) — sqlfluff 4.4.0 방언 픽스처의 파스 트리(yml)를 정답으로 표 이름 집합을 맞댄다.
 * DML(+ CTE·집합 연산) 만 든 파일은 {@link SqlTables#extract}, CREATE TABLE 이 든 파일은 {@link DdlReader#read} 의 표 이름과 비교.
 * 표 이름은 {@code table_reference} 잎의 마지막 식별자(스키마 뺌, 따옴표·백틱·대괄호 벗김, 대문자). 그 밖 문장은 skipped.
 * R1 은 고치지 않는다 — 차이·예외는 목록으로 얼리고 R3 가 행으로 만든다.
 */
@Tag("corpus")
class SqlFixtureCorpusTest {

    static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    static final Set<String> DML = Set.of("select_statement", "insert_statement", "update_statement", "delete_statement",
            "merge_statement", "with_compound_statement", "set_expression");

    @Test
    void tablesMatchParseTree() throws Exception {
        CorpusFiles.verify();
        List<String> a = new ArrayList<>();
        List<String> diff = new ArrayList<>();
        Map<String, int[]> byDialect = new TreeMap<>(); // files dml ddl skipped equal
        for (Path sql : CorpusFiles.files("sqlfluff-dialects", "test/fixtures/dialects/*/*.sql")) {
            Path yml = sql.resolveSibling(sql.getFileName().toString().replaceFirst("\\.sql$", ".yml"));
            if (!Files.exists(yml)) {
                continue;
            }
            String rel = CorpusFiles.rel(sql);
            int[] d = byDialect.computeIfAbsent(sql.getParent().getFileName().toString(), k -> new int[5]);
            d[0]++;
            JsonNode tree = YAML.readTree(yml.toFile()).path("file");
            Set<String> kinds = new TreeSet<>();
            statementKinds(tree, kinds);
            String text = Files.readString(sql, StandardCharsets.UTF_8);
            try {
                Set<String> expected;
                Set<String> ours = new TreeSet<>();
                if (!kinds.isEmpty() && DML.containsAll(kinds)) {
                    d[1]++;
                    expected = new TreeSet<>();
                    tableRefs(tree, expected);
                    expected.removeAll(cteNames(tree)); // WITH 이름은 표가 아니다 — 우리 SqlTables 도 안 센다
                    expected.remove("DUAL");             // SqlTables.DUMMY
                    for (SqlTables.Ref r : SqlTables.extract(text, null).refs()) {
                        ours.add(last(r.table()));
                    }
                    SqlJoins.extract(text); // 예외만 본다
                } else if (kinds.contains("create_table_statement")) {
                    d[2]++;
                    expected = new TreeSet<>();
                    createdTables(tree, expected);
                    for (Table t : DdlReader.read(text).tables()) {
                        ours.add(last(t.name()));
                    }
                } else {
                    d[3]++;
                    continue;
                }
                if (expected.equals(ours)) {
                    d[4]++;
                } else {
                    diff.add(rel + " 우리만=" + minus(ours, expected) + " 그쪽만=" + minus(expected, ours));
                }
            } catch (RuntimeException e) {
                a.add(rel + " 예외 " + e.getClass().getSimpleName());
            }
        }
        Map<String, Object> golden = new LinkedHashMap<>();
        int[] all = new int[5];
        byDialect.forEach((k, d) -> {
            golden.put(k, row(d));
            for (int i = 0; i < 5; i++) {
                all[i] += d[i];
            }
        });
        golden.put("_total", row(all));
        golden.put("_diff", diff.size());
        golden.put("_a", a.size());
        GoldenFiles.assertJson("corpus/sqlfixture.json", golden);
        CorpusFiles.conformance("sqltables-diff", diff);
        CorpusFiles.conformance("sqlfixture-a", a); // 설계 41 §3-1 — R1 은 얼린다, R3 가 none 으로 승격
        assertTrue(all[0] > 1000, "픽스처가 너무 적다: " + all[0]);
    }

    static Map<String, Object> row(int[] d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("files", d[0]);
        m.put("dml", d[1]);
        m.put("ddl", d[2]);
        m.put("skipped", d[3]);
        m.put("equal", d[4]);
        return m;
    }

    /** 트리의 모든 「statement」 아래 첫 필드 이름 */
    static void statementKinds(JsonNode n, Set<String> out) {
        if (n.isArray()) {
            n.forEach(x -> statementKinds(x, out));
        } else if (n.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = n.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getKey().equals("statement") && e.getValue().isObject() && e.getValue().fieldNames().hasNext()) {
                    out.add(e.getValue().fieldNames().next());
                }
                statementKinds(e.getValue(), out);
            }
        }
    }

    /** 모든 table_reference 의 표 이름 */
    static void tableRefs(JsonNode n, Set<String> out) {
        if (n.isArray()) {
            n.forEach(x -> tableRefs(x, out));
        } else if (n.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = n.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getKey().equals("table_reference")) {
                    String name = refName(e.getValue());
                    if (name != null) {
                        out.add(name);
                    }
                } else {
                    tableRefs(e.getValue(), out);
                }
            }
        }
    }

    /** common_table_expression 바로 아래 식별자(WITH 이름) */
    static Set<String> cteNames(JsonNode n) {
        Set<String> out = new TreeSet<>();
        if (n.isArray()) {
            n.forEach(x -> out.addAll(cteNames(x)));
        } else if (n.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = n.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getKey().equals("common_table_expression")) {
                    String name = refName(e.getValue());
                    if (name != null) {
                        out.add(name);
                    }
                }
                out.addAll(cteNames(e.getValue()));
            }
        }
        return out;
    }

    /** create_table_statement 마다 첫 table_reference */
    static void createdTables(JsonNode n, Set<String> out) {
        if (n.isArray()) {
            n.forEach(x -> createdTables(x, out));
        } else if (n.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = n.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getKey().equals("create_table_statement")) {
                    String name = firstRef(e.getValue());
                    if (name != null) {
                        out.add(name);
                    }
                } else {
                    createdTables(e.getValue(), out);
                }
            }
        }
    }

    static String firstRef(JsonNode n) {
        if (n.isArray()) {
            for (JsonNode x : n) {
                String r = firstRef(x);
                if (r != null) {
                    return r;
                }
            }
        } else if (n.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = n.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> e = it.next();
                String r = e.getKey().equals("table_reference") ? refName(e.getValue()) : firstRef(e.getValue());
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    /** table_reference 값({naked_identifier: X} 또는 [{…}, {dot: .}, {…}])의 마지막 식별자 */
    static String refName(JsonNode v) {
        String last = null;
        List<JsonNode> items = new ArrayList<>();
        if (v.isArray()) {
            v.forEach(items::add);
        } else {
            items.add(v);
        }
        for (JsonNode x : items) {
            for (String k : List.of("naked_identifier", "quoted_identifier")) {
                if (x.has(k) && x.get(k).isTextual()) {
                    last = x.get(k).asText();
                }
            }
        }
        return last == null ? null : last(last);
    }

    /** 점 뒤 마지막 조각, 따옴표(sqlite 는 홑따옴표도)·백틱·대괄호 벗김, 대문자 */
    static String last(String name) {
        String s = name.substring(name.lastIndexOf('.') + 1).replaceAll("[\"'`\\[\\]]", "");
        return s.toUpperCase(Locale.ROOT);
    }

    static Set<String> minus(Set<String> x, Set<String> y) {
        Set<String> r = new TreeSet<>(x);
        r.removeAll(y);
        return r;
    }
}
