package kr.ejg.toolbox.core.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.core.deliverable.Doc;
import kr.ejg.toolbox.core.deliverable.Forms;

/**
 * 양식 셀 매핑(2-4, 5-7) — 문서 번호마다 양식 파일·시트·머리 행·첫 데이터 행과 「양식 머리 글자 → 값 표 열」.
 * 원본은 YAML(`mappings/deliverable/*.yaml`). 실물 양식이 오면 이 파일과 xlsx 만 바꾼다.
 */
public record Mapping(Map<String, DocMapping> docs) {

    /** headerRow·firstRow 는 엑셀 행 번호(1부터). sheet 가 비면 첫 시트 */
    public record DocMapping(String file, String sheet, int headerRow, int firstRow, Map<String, String> columns) {
        public DocMapping {
            columns = columns == null ? Map.of() : Map.copyOf(columns); // 순서는 안 쓴다 — 열 자리는 양식 머리 글자로 찾는다
            if (headerRow < 1 || firstRow <= headerRow) {
                throw new IllegalArgumentException(file + " — headerRow ≥ 1, firstRow > headerRow 여야 한다");
            }
        }
    }

    public Mapping {
        docs = Map.copyOf(docs);
    }

    public static Mapping load(Path yaml) throws IOException {
        ObjectMapper m = new ObjectMapper(new YAMLFactory());
        Mapping out = m.readValue(Files.readAllBytes(yaml), Mapping.class);
        if (out.docs() == null) {
            throw new IllegalArgumentException(yaml + " 에 docs 가 없다");
        }
        return out;
    }

    public DocMapping of(String no) {
        DocMapping d = docs.get(no);
        if (d == null) {
            throw new IllegalArgumentException("매핑에 문서 " + no + " 가 없다");
        }
        return d;
    }

    /**
     * 오타를 조용히 삼키지 않는다 — 매핑 키가 양식 머리에 없거나, 값이 값 표 열에 없으면 예외.
     *
     * @param header 양식 머리 행의 글자 → 열 번호(0부터)
     */
    static void check(DocMapping m, Map<String, Integer> header, Doc d) {
        List<String> problems = new ArrayList<>();
        m.columns().forEach((formCol, field) -> {
            if (!header.containsKey(formCol)) {
                problems.add("양식 머리에 「" + formCol + "」 가 없다");
            }
            if (!d.columns().contains(field)) {
                problems.add("값 표 " + d.no() + " 에 「" + field + "」 열이 없다");
            }
        });
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(m.file() + ": " + String.join(" / ", problems));
        }
    }

    /** 예시 매핑 YAML 글 — 양식 머리 = 값 표 열(같은 이름) */
    public static String exampleYaml(List<Forms.Form> forms, int headerRow, int firstRow) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 예시 양식(templates/deliverable/example) 매핑 — scripts/make-example-templates.sh 가 만든다. 손으로 고치지 않는다.\n");
        sb.append("# 실물 양식이 오면 이 파일을 복사해 file·sheet·headerRow·firstRow 와 왼쪽(양식 머리 글자)만 고친다.\n");
        sb.append("# 오른쪽은 값 표 열 이름이다. 양식에 없는 열은 지우면 기입하지 않는다.\n");
        sb.append("docs:\n");
        for (Forms.Form f : forms) {
            sb.append("  \"").append(f.no()).append("\":\n");
            sb.append("    file: \"").append(f.file()).append("\"\n");
            sb.append("    sheet: \"").append(f.name()).append("\"\n");
            sb.append("    headerRow: ").append(headerRow).append('\n');
            sb.append("    firstRow: ").append(firstRow).append('\n');
            sb.append("    columns:\n");
            for (String c : f.columns()) {
                sb.append("      \"").append(c).append("\": \"").append(c).append("\"\n");
            }
        }
        return sb.toString();
    }
}
