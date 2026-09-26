package kr.ejg.toolbox.core.meta;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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

    /** 접속 DB 버전({@code getDatabaseProductVersion()}) */
    String dbVersion() throws SQLException;

    /**
     * 범위 안 전부. 스키마 → 테이블 뼈대 → 범위 거름 → 컬럼·제약·인덱스 → 코멘트·통계 → skipEmpty 로 한 번 더.
     * 스키마·테이블은 이름순.
     */
    default List<Schema> collect(Scope scope) throws SQLException {
        String version = dbVersion();
        List<Schema> out = new ArrayList<>();
        for (String schemaName : listSchemas()) {
            if (!scope.acceptsSchema(schemaName)) {
                continue;
            }
            List<Table> tables = new ArrayList<>();
            for (Table t : listTables(schemaName)) {
                if (!scope.accepts(t)) {
                    continue;
                }
                tables.add(loadIndexes(loadConstraints(loadColumns(t))));
            }
            Schema s = loadStats(loadComments(new Schema(schemaName, version, tables)));
            List<Table> kept = s.tables().stream().filter(scope::accepts).toList();
            out.add(new Schema(s.name(), s.dbVersion(), kept));
        }
        return out;
    }
}
