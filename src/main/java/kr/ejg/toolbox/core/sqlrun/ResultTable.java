package kr.ejg.toolbox.core.sqlrun;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SQL 실행 결과. 조회면 columns·rows, 갱신·DDL 이면 updateCount(조회면 -1).
 * truncated = maxRows 를 넘는 행이 더 있었다. 값은 JSON·엑셀에 그대로 쓸 수 있게 바꿔 둔다(날짜 ISO 문자열 등).
 */
public record ResultTable(List<Col> columns, List<List<Object>> rows, boolean truncated, long updateCount, long elapsedMs) {

    public record Col(String name, String type) {
    }

    public ResultTable {
        columns = List.copyOf(columns);
        List<List<Object>> copy = new ArrayList<>(rows.size());
        for (List<Object> r : rows) {
            copy.add(Collections.unmodifiableList(new ArrayList<>(r))); // 값에 null 이 있어 List.copyOf 를 못 쓴다
        }
        rows = Collections.unmodifiableList(copy);
    }
}
