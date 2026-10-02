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
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.ejg.toolbox.GoldenFiles;
import kr.ejg.toolbox.core.logical.DomainMatcher;
import kr.ejg.toolbox.core.meta.Column;
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
            if (comments) {
                assertEquals(e.comment(), t.comment(), where + " 테이블 코멘트");
            }
        }
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
}
