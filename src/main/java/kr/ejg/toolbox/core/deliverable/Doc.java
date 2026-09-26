package kr.ejg.toolbox.core.deliverable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 산출물 문서 하나의 값 표(2-1~2-3). 열은 순수본 양식 열(`formCols`) 그대로, 값은 문자열·수(순번·건수)·빈칸("").
 * 양식 파일에 기입하는 것은 2-4 {@code XlsxFiller} 다 — 여기는 값만.
 */
public record Doc(String no, String name, List<String> columns, List<List<Object>> rows) {

    public Doc {
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

    /** 열 이름으로 한 칸 — 테스트·매핑 검사용 */
    public Object cell(int row, String column) {
        int i = columns.indexOf(column);
        if (i < 0) {
            throw new IllegalArgumentException(no + " 에 없는 열: " + column);
        }
        return rows.get(row).get(i);
    }
}
