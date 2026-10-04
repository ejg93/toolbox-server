package kr.ejg.toolbox.core.meta;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 메타데이터 수집(7장). 뼈대는 JDBC({@link JdbcMetaSource}), 코멘트·통계는 벤더 구현이 덮어쓴다.
 * 메타모델이 불변이라 {@code load*} 는 채운 새 값을 돌려준다.
 */
public interface MetaSource {

    List<String> listSchemas() throws SQLException;

    /** 뼈대만 — 이름·종류·JDBC 가 주는 코멘트 */
    List<Table> listTables(String schema) throws SQLException;

    Table loadColumns(Table t) throws SQLException;

    /** PK·FK·UNIQUE */
    Table loadConstraints(Table t) throws SQLException;

    Table loadIndexes(Table t) throws SQLException;

    /** 벤더 SQL 로 코멘트를 덮어쓴다. 기본은 그대로 */
    default Schema loadComments(Schema s) throws SQLException {
        return s;
    }

    /** 벤더 SQL 로 rowCount·createdAt·lastDdlAt. 기본은 그대로(null) */
    default Schema loadStats(Schema s) throws SQLException {
        return s;
    }

    /** 벤더 뷰로 스키마 데이터 용량(1-23). 기본은 그대로(null) */
    default Schema loadSize(Schema s) throws SQLException {
        return s;
    }

    /** 접속 DB 버전({@code getDatabaseProductVersion()}) */
    String dbVersion() throws SQLException;

    /**
     * 벤더 SQL 이 실패해 JDBC 값으로 물러선 것(1-12) — 종류·SQLState·벤더 코드·건수만. SQL 글·오류문은 안 남긴다(규칙 3).
     * @param kind {@code comments}·{@code stats}·{@code uniques}·{@code sorts}(Oracle 인덱스 정렬)·{@code checks}·{@code size}
     */
    record Warning(String kind, String sqlState, int vendorCode, int count) {
    }

    /** 이 수집기가 지금까지 물러선 것. 기본은 없음 */
    default List<Warning> warnings() {
        return List.of();
    }

    /** 표를 읽기 직전마다 불린다(1-11) — 진행률·취소 자리 */
    @FunctionalInterface
    interface TableListener {
        /** @param done 지금 읽을 표의 순번(1부터) @param total 범위 안 표 수 */
        void onTable(int done, int total, String schema, String table) throws SQLException;
    }

    /** {@link #collect(Scope, TableListener)} 를 빈 listener 로 */
    default List<Schema> collect(Scope scope) throws SQLException {
        return collect(scope, (done, total, schema, table) -> {
        });
    }

    /**
     * 범위 안 전부. 스키마 → 테이블 뼈대 → 범위 거름 → 컬럼·제약·인덱스 → 코멘트·통계 → skipEmpty 로 한 번 더.
     * 스키마·테이블은 이름순. 1패스로 거른 표 목록과 수를 세고, 2패스로 표마다 listener 를 부른 뒤 읽는다.
     */
    default List<Schema> collect(Scope scope, TableListener listener) throws SQLException {
        String version = dbVersion();
        Map<String, List<Table>> skeletons = new LinkedHashMap<>();
        int total = 0;
        for (String schemaName : listSchemas()) {
            if (!scope.acceptsSchema(schemaName)) {
                continue;
            }
            List<Table> accepted = new ArrayList<>();
            for (Table t : listTables(schemaName)) {
                if (scope.accepts(t)) {
                    accepted.add(t);
                }
            }
            skeletons.put(schemaName, accepted);
            total += accepted.size();
        }
        List<Schema> out = new ArrayList<>();
        int done = 0;
        for (Map.Entry<String, List<Table>> e : skeletons.entrySet()) {
            List<Table> tables = new ArrayList<>();
            for (Table t : e.getValue()) {
                listener.onTable(++done, total, e.getKey(), t.name());
                tables.add(loadIndexes(loadConstraints(loadColumns(t))));
            }
            Schema s = loadSize(loadStats(loadComments(new Schema(e.getKey(), version, tables))));
            List<Table> kept = s.tables().stream().filter(scope::accepts).toList();
            out.add(s.withTables(kept));
        }
        return out;
    }
}
