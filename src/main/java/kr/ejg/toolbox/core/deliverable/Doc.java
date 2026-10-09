package kr.ejg.toolbox.core.deliverable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 산출물 문서 하나의 값 표(2-1~2-3). 열은 순수본 양식 열(`formCols`) 그대로, 값은 문자열·수(순번·건수)·빈칸("").
 * 양식 파일에 기입하는 것은 2-4 {@code XlsxFiller} 다 — 여기는 값만.
 * {@code estimated} 는 열 → 추정으로 채운 셀 수(2-13 작성안내). 정의서 골든에는 안 싣는다.
 * {@code formats} 는 열 → 엑셀 표시 형식(2-21 — 02 테이블 볼륨 「#,##0"건"」). 수 셀에만 쓰고 골든에는 안 싣는다
 */
public record Doc(String no, String name, List<String> columns, List<List<Object>> rows, @JsonIgnore Map<String, Integer> estimated,
        @JsonIgnore Map<String, String> formats) {

    public Doc {
        estimated = estimated == null ? Map.of() : Map.copyOf(estimated);
        for (String c : estimated.keySet()) {
            if (!columns.contains(c)) {
                throw new IllegalArgumentException(no + " 추정 건수의 열이 없다: " + c);
            }
        }
        formats = formats == null ? Map.of() : Map.copyOf(formats);
        for (String c : formats.keySet()) {
            if (!columns.contains(c)) {
                throw new IllegalArgumentException(no + " 표시 형식의 열이 없다: " + c);
            }
        }
        columns = List.copyOf(columns);
        List<List<Object>> copy = new ArrayList<>(rows.size());
        for (List<Object> r : rows) {
            if (r.size() != columns.size()) {
                throw new IllegalArgumentException(no + " 행의 칸 수 " + r.size() + " ≠ 열 수 " + columns.size());
            }
            copy.add(Collections.unmodifiableList(new ArrayList<>(r)));
        }
        rows = Collections.unmodifiableList(copy);
    }

    /** 추정 셀이 없는 문서 */
    public Doc(String no, String name, List<String> columns, List<List<Object>> rows) {
        this(no, name, columns, rows, null, null);
    }

    /** 표시 형식이 없는 문서 */
    public Doc(String no, String name, List<String> columns, List<List<Object>> rows, Map<String, Integer> estimated) {
        this(no, name, columns, rows, estimated, null);
    }

    /** 열 이름으로 한 칸 — 테스트·매핑 검사용 */
    public Object cell(int row, String column) {
        int i = columns.indexOf(column);
        if (i < 0) {
            throw new IllegalArgumentException(no + " 에 없는 열: " + column);
        }
        return rows.get(row).get(i);
    }
}
