package kr.ejg.toolbox.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.logical.DomainMatcher;
import kr.ejg.toolbox.core.meta.Column;
import kr.ejg.toolbox.core.meta.Index;
import kr.ejg.toolbox.core.meta.Schema;
import kr.ejg.toolbox.core.meta.Table;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * 1-9 — 경량 DDL 읽기의 정확도를 실물 DB 결과로 잰다. 컨테이너에 넣었던 샘플 DDL 넷을 읽어 1-3 이 그 DB 에서 떠 온
 * 스냅샷 골든과 테이블·컬럼 이름·타입 계열·NULL·PK·코멘트를 견준다. MSSQL 코멘트는 `sp_addextendedproperty` 라 안 읽는다.
 */
class DdlReaderTest {

    static final String LIKE = "like";

    /** V·C·N(3-6 규칙) · 날짜 D · 나머지는 이름 그대로 */
    static String family(String nativeType) {
        String u = TypeMapping.norm(nativeType);
        String c = DomainMatcher.typeCode(u);
        if (!c.isEmpty()) {
            return c;
        }
        return u.contains("DATE") || u.contains("TIME") ? "D" : u;
    }

    @ParameterizedTest
    @CsvSource({"postgres, postgres-vendor, true", "mariadb, mariadb, true", "mssql, mssql, false", "oracle, oracle, true"})
    void matchesSnapshotTakenFromTheSameDdl(String sample, String golden, boolean comments) throws Exception {
        DdlReader.Result r = DdlReader.read(Files.readString(Path.of("src/test/resources/sample/" + sample + ".sql"), StandardCharsets.UTF_8));
        assertEquals(List.of(), r.unreadable(), sample);
        List<Schema> snap = GoldenFiles.schemas("meta/" + golden + ".json");
        Map<String, Table> expected = snap.get(0).tables().stream()
                .collect(Collectors.toMap(t -> t.name().toUpperCase(Locale.ROOT), Function.identity()));
        assertEquals(expected.keySet(), r.tables().stream().map(t -> t.name().toUpperCase(Locale.ROOT)).collect(Collectors.toSet()), sample);
        for (Table t : r.tables()) {
            Table e = expected.get(t.name().toUpperCase(Locale.ROOT));
            String where = sample + " " + t.name();
            assertEquals(e.columns().size(), t.columns().size(), where);
            for (int i = 0; i < e.columns().size(); i++) {
                Column ec = e.columns().get(i);
                Column ac = t.columns().get(i);
                String at = where + "." + ec.name();
                assertTrue(ec.name().equalsIgnoreCase(ac.name()), at + " 이름 " + ac.name());
                assertEquals(family(ec.nativeType()), family(ac.nativeType()), at + " 타입 " + ec.nativeType() + " / " + ac.nativeType());
                assertEquals(ec.nullable(), ac.nullable(), at + " NULL");
                if (comments) {
                    assertEquals(ec.comment(), ac.comment(), at + " 코멘트");
                }
            }
            assertEquals(e.pk() == null ? List.of() : e.pk().columns().stream().map(s -> s.toUpperCase(Locale.ROOT)).toList(),
                    t.pk() == null ? List.of() : t.pk().columns().stream().map(s -> s.toUpperCase(Locale.ROOT)).toList(), where + " PK");
            // 4-26 — 제약 UNIQUE(샘플은 전부 uq_*). CREATE UNIQUE INDEX(ux_*) 출신은 뺀다 — JDBC 골든이 방언마다 uniques 에 넣기도 안 넣기도 한다(1-68)
            Set<String> fromIndex = t.indexes().stream().map(x -> x.name().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
            assertEquals(e.uniques().stream().filter(u -> u.name().toUpperCase(Locale.ROOT).startsWith("UQ_"))
                            .map(u -> u.name().toUpperCase(Locale.ROOT) + ":" + u.columns().stream().map(s -> s.toUpperCase(Locale.ROOT)).toList())
                            .collect(Collectors.toSet()),
                    t.uniques().stream().filter(u -> !fromIndex.contains(String.valueOf(u.name()).toUpperCase(Locale.ROOT)))
                            .map(u -> String.valueOf(u.name()).toUpperCase(Locale.ROOT) + ":"
                            + u.columns().stream().map(s -> s.toUpperCase(Locale.ROOT)).toList()).collect(Collectors.toSet()), where + " UNIQUE");
            // 1-68 — DDL 의 인덱스는 JDBC 인덱스에 이름·unique·컬럼이 같게 있다(⊆ — MySQL FK 자동 인덱스는 DDL 에 없다)
            Set<String> jdbcIndexes = e.indexes().stream().map(DdlReaderTest::indexKey).collect(Collectors.toSet());
            for (Index x : t.indexes()) {
                assertTrue(jdbcIndexes.contains(indexKey(x)), where + " 인덱스 " + indexKey(x) + " / " + jdbcIndexes);
            }
            if (comments) {
                assertEquals(e.comment(), t.comment(), where + " 테이블 코멘트");
            }
        }
    }

    static String indexKey(Index x) {
        return x.name().toUpperCase(Locale.ROOT) + ":" + x.unique() + ":" + x.columns().stream().map(c -> c.toUpperCase(Locale.ROOT)).toList();
    }

    /** V-4 실물 표본에서 드러남 — SQL*Plus 스크립트(Oracle 샘플 스키마)의 명령 줄이 CREATE 를 가려 0 테이블로 읽었다 */
    @Test
    void sqlPlusCommandLinesAreSkipped() {
        DdlReader.Result r = DdlReader.read("SET ECHO OFF\nSET DEFINE OFF\nrem 고객 표\nPrompt ****** Creating CUSTOMERS table ....\n\n"
                + "CREATE TABLE customers (\n  id INTEGER,\n  name VARCHAR2(20)\n)\n/\n"
                + "@@other_script.sql\nPROMPT next\nCREATE TABLE stores (id INTEGER);\n"
                + "UPDATE stores\nSET id = 1;\n");
        assertEquals(List.of("customers", "stores"), r.tables().stream().map(t -> t.name()).toList());
        assertEquals(List.of("id", "name"), r.tables().get(0).columns().stream().map(c -> c.name()).toList(), "/ 줄은 문장 끝");
    }

    /** V-29 실물 표본(RuoYi)에서 드러남 — CREATE TABLE 괄호 안 컬럼 줄 remark·host·column 은 SQL*Plus 명령이 아니다 */
    @Test
    void sqlPlusWordsInsideTableAreColumns() {
        DdlReader.Result r = DdlReader.read("rem 머리\ncreate table sys_post (\n  post_id bigint(20) not null,\n  host varchar(10),\n"
                + "  column_x int,\n  remark varchar(500) default null comment '备注',\n  primary key (post_id)\n) engine=innodb comment = '岗位信息表';\n"
                + "REMARK 끝\n");
        assertEquals(List.of("post_id", "host", "column_x", "remark"), r.tables().get(0).columns().stream().map(c -> c.name()).toList());
    }

    /** V-4 — 원본 오타로 CREATE 앞에 글이 붙은 문장(eGov PG DDL 「;s」)은 조용히 버리지 않고 unreadable 로 */
    @Test
    void buriedCreateIsReportedNotDropped() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE a (x INT);s\n\n/* 뉴스 */\nCREATE TABLE b (y INT);");
        assertEquals(List.of("a"), r.tables().stream().map(t -> t.name()).toList());
        assertEquals(1, r.unreadable().size());
        assertEquals("s", r.unreadable().get(0).line());
    }

    @Test
    void quotedNamesInlinePkAndSizes() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE IF NOT EXISTS `s`.`t_a` (\n `a` INT NOT NULL,\n [b] VARCHAR2(30 BYTE),\n"
                + " \"c\" NUMBER(12, 2) DEFAULT 0,\n d TIMESTAMP(6) WITH TIME ZONE,\n PRIMARY KEY (`a`)\n) COMMENT='표 ''a''';");
        Table t = r.tables().get(0);
        assertEquals("s", t.schema());
        assertEquals("t_a", t.name());
        assertEquals("표 'a'", t.comment());
        assertEquals(List.of("a", "b", "c", "d"), t.columns().stream().map(Column::name).toList());
        assertEquals(List.of("a"), t.pk().columns());
        assertEquals(30L, t.columns().get(1).length());
        assertEquals(12, t.columns().get(2).precision());
        assertEquals(2, t.columns().get(2).scale());
        assertEquals("0", t.columns().get(2).defaultValue());
        assertEquals("TIMESTAMP WITH TIME ZONE", t.columns().get(3).nativeType());
    }

    @Test
    void quotedNameWithSpacesAndNoCatastrophicBacktracking() {
        Table t = DdlReader.read("CREATE TABLE \"my schema\" . [my table] (A INT)").tables().get(0);
        assertEquals("my schema", t.schema());
        assertEquals("my table", t.name());
        String attack = "CREATE TABLE " + "!.".repeat(5000) + " x";
        long start = System.nanoTime();
        assertEquals(0, DdlReader.read(attack).tables().size());
        assertTrue(System.nanoTime() - start < 2_000_000_000L, "CodeQL 이 짚은 입력이 바로 끝난다");
        String longText = "가".repeat(20_000);
        DdlReader.Result r = DdlReader.read("CREATE TABLE T (A INT COMMENT '" + longText + "', B CHAR(1) DEFAULT '" + longText + "') COMMENT='"
                + longText + "'; COMMENT ON COLUMN T.B IS '" + longText + "'");
        assertEquals(longText, r.tables().get(0).comment(), "긴 문자열에서도 스택이 안 넘친다");
        assertEquals(longText, r.tables().get(0).columns().get(0).comment());
        assertEquals(longText, r.tables().get(0).columns().get(1).comment());
    }

    @Test
    void unreadableLineIsReportedNotDropped() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (a INT, 123bad stuff, b VARCHAR(5))");
        assertEquals(1, r.unreadable().size());
        assertEquals("123bad stuff", r.unreadable().get(0).line());
        Table t = r.tables().get(0);
        assertEquals(List.of("a", "unreadable2", "b"), t.columns().stream().map(Column::name).toList(), "자리를 지킨다");
        assertNull(t.columns().get(1).nativeType());
        assertNotNull(r.notes().get("T").get("UNREADABLE2"));
        assertTrue(r.notes().get("T").get("UNREADABLE2").startsWith("TODO 못 읽음: 123bad"));
    }

    @Test
    void commentsAndStringsDoNotConfuseSplitting() {
        DdlReader.Result r = DdlReader.read("-- 머리 주석; 여기도\nCREATE TABLE x ( /* 블록, 주석 */ a CHAR(1) DEFAULT ',' NOT NULL, b INT );\n"
                + "COMMENT ON COLUMN x.a IS '쉼표, 그리고 ; 세미콜론';\nGO\n");
        Table t = r.tables().get(0);
        assertEquals(2, t.columns().size());
        assertEquals("','", t.columns().get(0).defaultValue());
        assertFalse(t.columns().get(0).nullable());
        assertEquals("쉼표, 그리고 ; 세미콜론", t.columns().get(0).comment());
    }

    private static String fks(DdlReader.Result r, String table) {
        return r.tables().stream().filter(t -> t.name().equalsIgnoreCase(table)).findFirst().orElseThrow().fks().stream()
                .map(f -> f.name() + ":" + f.columns() + "->" + (f.refSchema() == null ? "" : f.refSchema() + ".") + f.refTable()
                        + f.refColumns())
                .collect(Collectors.joining(" | "));
    }

    /** 1-10 — eGov 꼴 제약 줄(이름 없음)·HR 꼴 이름 있는 제약 줄·컬럼 REFERENCES */
    @Test
    void readsInlineForeignKeys() {
        DdlReader.Result r = DdlReader.read("""
                CREATE TABLE COMTCCMMNCODE (
                  CODE_ID VARCHAR(6) NOT NULL,
                  CL_CODE CHAR(3),
                  PRIMARY KEY (CODE_ID),
                  FOREIGN KEY (CL_CODE) REFERENCES COMTCCMMNCLCODE(CL_CODE)
                );
                CREATE TABLE COMTCCMMNDETAILCODE (
                  CODE_ID VARCHAR(6) NOT NULL,
                  FOREIGN KEY COMTCCMMNDETAILCODE_FK1 (CODE_ID) REFERENCES COMTCCMMNCODE(CODE_ID)
                );
                CREATE TABLE emp (
                  id NUMBER(6) PRIMARY KEY,
                  dept_id NUMBER(4) REFERENCES dept,
                  job_id VARCHAR2(10),
                  loc_a NUMBER, loc_b NUMBER,
                  CONSTRAINT emp_job_fk FOREIGN KEY (job_id) REFERENCES jobs (job_id),
                  CONSTRAINT emp_loc_fk FOREIGN KEY (loc_a, loc_b) REFERENCES hr.locs (a, b)
                );
                """);
        assertEquals("null:[CL_CODE]->COMTCCMMNCLCODE[CL_CODE]", fks(r, "COMTCCMMNCODE"));
        assertEquals("COMTCCMMNDETAILCODE_FK1:[CODE_ID]->COMTCCMMNCODE[CODE_ID]", fks(r, "COMTCCMMNDETAILCODE"));
        assertEquals("null:[dept_id]->dept[] | emp_job_fk:[job_id]->jobs[job_id] | emp_loc_fk:[loc_a, loc_b]->hr.locs[a, b]",
                fks(r, "emp"));
        assertEquals(5, r.tables().get(2).columns().size());
    }

    /** 1-10 — chinook 꼴 ALTER(이름 따옴표 넷)·HR 꼴 `ADD ( CONSTRAINT …, CONSTRAINT … )` */
    @Test
    void readsAlterForeignKeys() {
        DdlReader.Result r = DdlReader.read("""
                CREATE TABLE `Album` (`AlbumId` INT NOT NULL, `ArtistId` INT NOT NULL, CONSTRAINT `PK_Album` PRIMARY KEY (`AlbumId`));
                CREATE TABLE "track" ("id" INT, "album_id" INT, "genre_id" INT);
                ALTER TABLE `Album` ADD CONSTRAINT `FK_AlbumArtistId`
                    FOREIGN KEY (`ArtistId`) REFERENCES `Artist` (`ArtistId`) ON DELETE NO ACTION ON UPDATE NO ACTION;
                ALTER TABLE "track"
                ADD ( CONSTRAINT track_album_fk FOREIGN KEY ("album_id") REFERENCES "album" ("id"),
                      CONSTRAINT track_genre_fk FOREIGN KEY ("genre_id") REFERENCES "genre" ("id") ) ;
                ALTER TABLE nowhere ADD CONSTRAINT x FOREIGN KEY (a) REFERENCES b (a);
                """);
        assertEquals("FK_AlbumArtistId:[ArtistId]->Artist[ArtistId]", fks(r, "Album"));
        assertEquals("track_album_fk:[album_id]->album[id] | track_genre_fk:[genre_id]->genre[id]", fks(r, "track"));
        assertEquals(List.of("AlbumId"), r.tables().get(0).pk().columns());
    }

    /** 1-10 — MSSQL 대괄호·같은 스키마 접두는 refSchema 없이, `WITH CHECK ADD` · GO 묶음 */
    @Test
    void readsBracketedForeignKeys() {
        DdlReader.Result r = DdlReader.read("""
                CREATE TABLE [dbo].[Album] ([AlbumId] INT NOT NULL, [ArtistId] INT NOT NULL, [OwnerId] INT NULL)
                GO
                ALTER TABLE [dbo].[Album] ADD CONSTRAINT [FK_AlbumArtistId]
                    FOREIGN KEY ([ArtistId]) REFERENCES [dbo].[Artist] ([ArtistId]) ON DELETE NO ACTION ON UPDATE NO ACTION
                GO
                ALTER TABLE [dbo].[Album] WITH CHECK ADD CONSTRAINT [FK_AlbumOwner] FOREIGN KEY([OwnerId]) REFERENCES [sec].[Owner] ([Id])
                GO
                """);
        assertEquals("FK_AlbumArtistId:[ArtistId]->Artist[ArtistId] | FK_AlbumOwner:[OwnerId]->sec.Owner[Id]", fks(r, "Album"));
        assertEquals("dbo", r.tables().get(0).schema());
    }

    /** 1-31a — 컬럼 안 CHECK 와 표 단위 CONSTRAINT … CHECK 를 Table.checks 로(이름·조건 글) */
    @Test
    void readsChecks() {
        Table t = DdlReader.read("CREATE TABLE T (A INT CHECK (A > 0), B CHAR(1) NOT NULL, C VARCHAR(10),"
                + " CONSTRAINT CK_B CHECK (B IN ('Y', 'N')), CONSTRAINT FK_C FOREIGN KEY (C) REFERENCES P (C))").tables().get(0);
        assertEquals(List.of(new kr.ejg.toolbox.core.meta.Check(null, "A > 0"), new kr.ejg.toolbox.core.meta.Check("CK_B", "B IN ('Y', 'N')")),
                t.checks());
        assertEquals(3, t.columns().size());
        assertEquals(1, t.fks().size());
    }

    /** 4-26 — UNIQUE 를 인라인·표 수준(CONSTRAINT 이름·MySQL KEY 이름·이름 없음)·ALTER 에서 읽는다. 타입에 안 섞인다 */
    @Test
    void readsUniqueConstraints() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (a INT UNIQUE, b VARCHAR(3), c CHAR(1) COMMENT 'UNIQUE 아님', d INT,"
                + " CONSTRAINT uq_bc UNIQUE (b, c), UNIQUE KEY ix_d (d), UNIQUE NONCLUSTERED ([b]));\n"
                + "ALTER TABLE t ADD CONSTRAINT uq_c UNIQUE (c);\n");
        assertEquals(List.of(), r.unreadable());
        Table t = r.tables().get(0);
        assertEquals(List.of("null:[a]", "uq_bc:[b, c]", "ix_d:[d]", "null:[b]", "uq_c:[c]"),
                t.uniques().stream().map(u -> u.name() + ":" + u.columns()).toList());
        assertEquals(List.of("INT", "VARCHAR", "CHAR", "INT"), t.columns().stream().map(Column::nativeType).toList());
    }

    /** 4-25a — ENUM·SET 은 나열 값까지 원문 그대로 타입에(길이 없음). 다른 칸은 그대로 읽는다 */
    @Test
    void readsEnumAndSetValues() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (id INT PRIMARY KEY, st ENUM('A','B','C') NOT NULL DEFAULT 'A' COMMENT '상태',"
                + " fl SET('X', 'Y'), nm VARCHAR(10))");
        assertEquals(List.of(), r.unreadable());
        List<Column> c = r.tables().get(0).columns();
        assertEquals(List.of("INT", "ENUM('A','B','C')", "SET('X', 'Y')", "VARCHAR"), c.stream().map(Column::nativeType).toList());
        assertNull(c.get(1).length());
        assertFalse(c.get(1).nullable());
        assertEquals("'A'", c.get(1).defaultValue());
        assertEquals("상태", c.get(1).comment());
        assertEquals(10L, c.get(3).length());
    }

    /** 마무리 리뷰 — 나열 값 안의 'not null'·'check'·'unique' 글이 타입을 자르거나 속성으로 읽히지 않는다. 따옴표 글 안 「UNIQUE (x)」 도 키가 아니다 */
    @Test
    void enumValuesAndQuotedTextDoNotLeak() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (st ENUM('pending check','not null','unique') DEFAULT 'pending check',"
                + " nm VARCHAR(10) COMMENT \"고유 UNIQUE 번호\", cd INT, KEY ix (cd) COMMENT 'UNIQUE (nm) 설명')");
        assertEquals(List.of(), r.unreadable());
        Table t = r.tables().get(0);
        assertEquals("ENUM('pending check','not null','unique')", t.columns().get(0).nativeType());
        assertTrue(t.columns().get(0).nullable(), "나열 값 'not null' 은 NOT NULL 이 아니다");
        assertEquals("'pending check'", t.columns().get(0).defaultValue());
        assertEquals(List.of(), t.uniques(), "나열 값·코멘트 글의 UNIQUE 는 키가 아니다");
        assertEquals(List.of(), t.checks());
    }

    /** 1-66(설계 46) — CTAS 는 컬럼 0 표 + notes ctas. unreadable 로 빼지 않는다(사용자 2026-10-31) */
    @Test
    void ctasIsTableWithoutColumns() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE T2 AS SELECT A FROM T1");
        assertEquals(List.of("T2"), r.tables().stream().map(Table::name).toList());
        assertEquals(List.of(), r.tables().get(0).columns());
        assertNotNull(r.notes().get("T2").get("ctas"));
        assertEquals(List.of(), r.unreadable());
    }

    /** 1-66 — CTAS 꼴 일곱(MariaDB 의 AS 없는 SELECT · AS (WITH …) · Oracle NOLOGGING·STORAGE·ON COMMIT · 컬럼 이름 목록) */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "CREATE OR REPLACE TEMPORARY TABLE `db`.T AS SELECT * FROM X",
        "CREATE TABLE T SELECT * FROM X",
        "CREATE TABLE T AS (WITH C AS (SELECT 1) SELECT * FROM C)",
        "CREATE TABLE T NOLOGGING AS SELECT * FROM X",
        "CREATE TABLE T STORAGE (INITIAL 1M) AS SELECT * FROM X",
        "CREATE GLOBAL TEMPORARY TABLE T ON COMMIT PRESERVE ROWS AS SELECT * FROM X",
        "CREATE TABLE T (C1, C2) AS SELECT A, B FROM X"})
    void ctasShapes(String ddl) {
        DdlReader.Result r = DdlReader.read(ddl);
        assertEquals(List.of("T"), r.tables().stream().map(Table::name).toList(), ddl);
        assertEquals(List.of(), r.tables().get(0).columns(), ddl);
        assertEquals(List.of(), r.unreadable(), ddl);
    }

    /** 1-66 — PG PARTITION OF 는 컬럼 0 표 + notes partition */
    @Test
    void partitionOf() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE P1 PARTITION OF P FOR VALUES FROM (1) TO (10);\n"
                +"CREATE TABLE P2 PARTITION OF P (CONSTRAINT X CHECK (A > 0)) FOR VALUES IN ('a');");
        assertEquals(List.of("P1", "P2"), r.tables().stream().map(Table::name).toList());
        assertNotNull(r.notes().get("P1").get("partition"));
        assertNotNull(r.notes().get("P2").get("partition"));
        assertEquals(List.of(), r.unreadable());
    }

    /** 1-66 — Oracle 18c PRIVATE TEMPORARY 는 보통 표처럼 컬럼까지 */
    @Test
    void privateTemporary() {
        DdlReader.Result r = DdlReader.read("CREATE PRIVATE TEMPORARY TABLE ORA$PTT_X (ID NUMBER)");
        assertEquals(List.of("ORA$PTT_X"), r.tables().stream().map(Table::name).toList());
        assertEquals(1, r.tables().get(0).columns().size());
    }

    /** 1-66 — ; 없이 이어진 CREATE(tsql 파일)는 줄머리 CREATE 에서 가른다. 「;s」 꼴은 buriedCreateIsReportedNotDropped 그대로 */
    @Test
    void consecutiveCreatesWithoutSemicolon() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE A (X INT)\n\nCREATE TABLE B (Y INT)");
        assertEquals(List.of("A", "B"), r.tables().stream().map(Table::name).toList());
        assertEquals(1, r.tables().get(1).columns().size());
        assertEquals(List.of(), r.unreadable());
    }

    /** 1-66 음성 — 계산 컬럼의 AS (식) 는 CTAS 가 아니다. 따옴표 글 안 「as select」 도 */
    @Test
    void normalCreateUnchanged() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE T (ID INT GENERATED ALWAYS AS (1) STORED, NM VARCHAR(10) DEFAULT 'as select')");
        assertEquals(2, r.tables().get(0).columns().size());
        assertNull(r.notes().get("T"));
    }

    /** 1-67(설계 47 §3-18) — CREATE TABLE y LIKE x 는 컬럼 0 표 + notes like. unreadable 로 빼지 않는다 */
    @Test
    void likeHead() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE Y LIKE X");
        assertEquals(List.of("Y"), r.tables().stream().map(Table::name).toList());
        assertEquals(List.of(), r.tables().get(0).columns());
        assertNotNull(r.notes().get("Y").get(LIKE));
        assertEquals(List.of(), r.unreadable());
    }

    /** 1-67 — PG·MySQL 의 괄호 안 LIKE x [INCLUDING …] 도 같다 */
    @Test
    void likeInParens() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE Y (LIKE X INCLUDING ALL)");
        assertEquals(List.of("Y"), r.tables().stream().map(Table::name).toList());
        assertEquals(List.of(), r.tables().get(0).columns());
        assertNotNull(r.notes().get("Y").get(LIKE));
        assertEquals(List.of(), r.unreadable());
    }

    /** 1-67 음성 — LIKE 로 시작하는 컬럼 이름은 컬럼이다 */
    @Test
    void likeColumnNameStillColumn() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE T (LIKE_CNT INT)");
        assertEquals(List.of("LIKE_CNT"), r.tables().get(0).columns().stream().map(c -> c.name()).toList());
        assertNull(r.notes().get("T"));
    }

    /** 1-68 — CREATE [UNIQUE] INDEX · 표 안 KEY/INDEX. ASC·DESC 는 떼고 함수식은 글 그대로, FULLTEXT 는 안 읽는다. 유니크는 uniques 에도 */
    @Test
    void readsCreateIndexAndInlineKey() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE t (a INT, b VARCHAR(10), KEY k_b (b), FULLTEXT KEY ft (b));\n"
                + "CREATE INDEX i_ab ON t (a ASC, b DESC);\nCREATE UNIQUE INDEX u_a ON t (a);\nCREATE INDEX i_up ON t (UPPER(b));\n"
                + "CREATE INDEX i_none ON nowhere (x);");
        assertEquals(List.of(), r.unreadable());
        Table t = r.tables().get(0);
        assertEquals(List.of("k_b:false:[b]", "i_ab:false:[a, b]", "u_a:true:[a]", "i_up:false:[UPPER(b)]"),
                t.indexes().stream().map(x -> x.name() + ":" + x.unique() + ":" + x.columns()).toList());
        assertEquals(List.of("u_a"), t.uniques().stream().map(u -> u.name()).toList());
        assertEquals(1, r.tables().size(), "앞에 없는 표의 인덱스는 버린다");
    }

    /** 1-68 — HR 꼴: ALTER TABLE … ADD ( CONSTRAINT … PRIMARY KEY … , CONSTRAINT … FOREIGN KEY … ). PK 이름과 같은 유니크 인덱스는 뺀다 */
    @Test
    void alterAddParenthesizedPrimaryKey() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE r (id INT, n VARCHAR(10));\nCREATE UNIQUE INDEX r_pk ON r (id);\n"
                + "CREATE TABLE c (id INT, r_id INT);\n"
                + "ALTER TABLE c ADD ( CONSTRAINT c_pk PRIMARY KEY (id) , CONSTRAINT c_r_fk FOREIGN KEY (r_id) REFERENCES r(id) );\n"
                + "ALTER TABLE r ADD ( CONSTRAINT r_pk PRIMARY KEY (id) );");
        Table rt = r.tables().get(0);
        Table ct = r.tables().get(1);
        assertEquals(List.of("ID"), ct.pk().columns().stream().map(x -> x.toUpperCase(Locale.ROOT)).toList());
        assertEquals(1, ct.fks().size());
        assertEquals(List.of("id"), rt.pk().columns());
        assertEquals(List.of(), rt.indexes(), "PK 이름 인덱스는 뺀다");
        assertEquals(List.of(), rt.uniques());
        assertEquals(false, rt.columns().get(0).nullable());
        assertNull(rt.pk().name(), "PK 이름은 안 채운다(alter-*.sql 골든)");
    }

    /** 마무리 리뷰 — MySQL 겹따옴표 COMMENT 안 「as select」 도 CTAS 가 아니다 */
    @Test
    void doubleQuotedCommentIsNotCtas() {
        DdlReader.Result r = DdlReader.read("CREATE TABLE T (A INT COMMENT \"used as select flag\")");
        assertEquals(1, r.tables().get(0).columns().size());
        assertNull(r.notes().get("T"));
    }
}
