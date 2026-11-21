package kr.ejg.toolbox.core.logical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ejg.toolbox.GoldenFiles;
import org.junit.jupiter.api.Test;

/** 7-8 — 탐지 골든 · 방언 다섯 SQL 골든 · 제외 · 문자열 아님 경고 · H2 호환 모드에서 생성 UPDATE 를 돌려 값 꼴 */
class MaskingTest {

    static Masking.Input in(String table, String col, String logical, String comment, String dtype, Long len) {
        return new Masking.Input("APP", table, col, logical, comment, dtype, len);
    }

    /** eGov 꼴(코멘트 있음)·HR 영문 넷(코멘트 없음)·부서명·숫자형 주민번호·IP 주소·파일 이름 */
    static List<Masking.Input> fixture() {
        return List.of(
                in("COMTNEMPLYRINFO", "EMPLYR_ID", "업무사용자ID", "업무사용자ID", "VARCHAR2", 20L),
                in("COMTNEMPLYRINFO", "USER_NM", "사용자명", "사용자명", "VARCHAR2", 60L),
                in("COMTNEMPLYRINFO", "IHIDNUM", null, "주민등록번호", "VARCHAR2", 200L),
                in("COMTNEMPLYRINFO", "MBTLNUM", "이동전화번호", "이동전화번호", "VARCHAR2", 20L),
                in("COMTNEMPLYRINFO", "OFFM_TELNO", null, "사무실전화번호", "VARCHAR2", 20L),
                in("COMTNEMPLYRINFO", "EMAIL_ADRES", "이메일주소", "이메일주소", "VARCHAR2", 50L),
                in("COMTNEMPLYRINFO", "HOUSE_ADRES", null, "주택주소", "VARCHAR2", 100L),
                in("COMTNEMPLYRINFO", "ORGNZT_NM", "조직명", "조직명", "VARCHAR2", 60L),
                in("COMTNEMPLYRINFO", "DEPT_NM", "부서명", null, "VARCHAR2", 60L),
                in("COMTNEMPLYRINFO", "ATCH_FILE_NM", "첨부파일이름", null, "VARCHAR2", 255L),
                in("COMTNLOGINLOG", "CONECT_IP", null, "접속IP주소", "VARCHAR2", 23L),
                in("COMTNLOGINLOG", "SVR_ADDR", null, null, "VARCHAR2", 50L),
                in("EMPLOYEES", "FIRST_NAME", null, null, "VARCHAR2", 20L),
                in("EMPLOYEES", "LAST_NAME", null, null, "VARCHAR2", 25L),
                in("EMPLOYEES", "EMAIL", null, null, "VARCHAR2", 25L),
                in("EMPLOYEES", "PHONE_NUMBER", null, null, "VARCHAR2", 20L),
                in("EMPLOYEES", "SALARY", null, null, "NUMBER", null),
                in("TB_PAY", "RRN", null, null, "NUMBER", 13L),
                in("TB_PAY", "ACNUTNO", "계좌번호", null, "VARCHAR2", 30L),
                in("TB_PAY", "CARD_NO", null, null, "CHAR", 16L),
                in("TB_PAY", "BRTHDY", null, "생년월일", "CHAR", 8L));
    }

    @Test
    void detectGolden() {
        Masking.Detection d = Masking.detect(fixture(), Masking.rules());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Masking.Candidate c : d.candidates()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("table", c.table());
            m.put("col", c.col());
            m.put("kind", c.kind());
            m.put("reason", c.reason());
            m.put("text", c.text());
            rows.add(m);
        }
        Map<String, Object> golden = new LinkedHashMap<>();
        golden.put("candidates", rows);
        golden.put("warnings", d.warnings());
        GoldenFiles.assertJson("logical/masking-candidates.json", golden);
        List<String> cols = d.candidates().stream().map(Masking.Candidate::col).toList();
        for (String no : List.of("EMPLYR_ID", "ORGNZT_NM", "DEPT_NM", "ATCH_FILE_NM", "CONECT_IP", "SVR_ADDR", "SALARY")) {
            assertFalse(cols.contains(no), no + " 는 개인정보가 아니다: " + cols);
        }
        for (String yes : List.of("USER_NM", "IHIDNUM", "MBTLNUM", "OFFM_TELNO", "EMAIL_ADRES", "HOUSE_ADRES", "FIRST_NAME", "LAST_NAME",
                "EMAIL", "PHONE_NUMBER", "RRN", "ACNUTNO", "CARD_NO", "BRTHDY")) {
            assertTrue(cols.contains(yes), yes + " 를 놓쳤다: " + cols);
        }
        assertEquals("email", d.candidates().stream().filter(c -> c.col().equals("EMAIL_ADRES")).findFirst().orElseThrow().kind(),
                "이메일주소는 주소가 아니라 이메일");
        assertTrue(d.warnings().stream().anyMatch(w -> w.startsWith("TB_PAY.RRN:") && w.contains("문자열 아님")), d.warnings().toString());
    }

    @Test
    void sqlGoldenPerDialect() {
        List<Masking.Candidate> c = Masking.detect(fixture(), Masking.rules()).candidates();
        for (String d : List.of("oracle", "tibero", "postgresql", "mariadb", "mssql")) {
            String sql = Masking.sql(c, d, Masking.rules());
            assertFalse(sql.contains(" RRN ="), "문자열 아닌 후보는 SQL 에 없다");
            assertTrue(sql.startsWith("-- 개인정보 마스킹 UPDATE — 개발·시험 사본에만"), sql);
            GoldenFiles.assertText("logical/masking-" + d + ".sql", sql);
        }
        assertThrows(IllegalArgumentException.class, () -> Masking.sql(c, "sybase", Masking.rules()));
    }

    /** B-18 — 여권번호·운전면허번호 종류 · TEL_NO 는 전화번호. HOTEL_NO·PASSPORT_YN 은 아니다 */
    @Test
    void passportDriverTelNo() {
        List<Masking.Candidate> c = Masking.detect(List.of(
                in("T", "PASSPORT_NO", null, null, "VARCHAR2", 20L),
                in("T", "DRV_LIC_NO", null, null, "VARCHAR2", 20L),
                in("T", "TEL_NO", null, null, "VARCHAR2", 20L),
                in("T", "X", null, "여권번호", "VARCHAR2", 20L),
                in("T", "HOTEL_NO", null, null, "VARCHAR2", 20L),
                in("T", "PASSPORT_YN", null, null, "CHAR", 1L)), Masking.rules()).candidates();
        assertEquals(List.of("PASSPORT_NO passport", "DRV_LIC_NO driver", "TEL_NO phone", "X passport"),
                c.stream().map(x -> x.col() + " " + x.kind()).toList());
    }

    /** 3-18(B-03) — 여부·코드·내용 꼴(논리명·코멘트 꼬리 · 컬럼 꼬리)은 종류를 가리기 앞에 뺀다. 개인정보 값 컬럼은 그대로 */
    @Test
    void flagAndCodeColumnsAreNotPii() {
        List<Masking.Candidate> c = Masking.detect(List.of(
                in("T", "EMAIL_YN", null, "이메일수신여부", "CHAR", 1L),
                in("T", "PHONE_CERT_YN", null, null, "CHAR", 1L),
                in("T", "ADDR_TYPE_CD", null, null, "VARCHAR2", 10L),
                in("T", "EMAIL_ANSWER_AT", null, null, "CHAR", 1L),
                in("T", "EMAIL_CN", null, "이메일내용", "VARCHAR2", 4000L),
                in("T", "MENU_NM", null, "메뉴이름", "VARCHAR2", 60L),
                in("T", "WEB_ADDRESS", null, null, "VARCHAR2", 200L),
                in("T", "EMAIL", null, "이메일", "VARCHAR2", 50L),
                in("T", "MBTLNUM", null, null, "VARCHAR2", 20L),
                in("T", "BRTH_DE", null, "생년월일", "VARCHAR2", 8L),
                in("T", "USER_NM", null, "이름", "VARCHAR2", 50L),
                in("T", "EMAIL_ADRES", null, null, "VARCHAR2", 50L)), Masking.rules()).candidates();
        assertEquals(List.of("EMAIL", "MBTLNUM", "BRTH_DE", "USER_NM", "EMAIL_ADRES"), c.stream().map(Masking.Candidate::col).toList());
        // 마무리 리뷰 I1 — 사전이 만든 논리명 「…일시」·「…일자」 는 날짜 값 꼬리라 생년월일을 빼면 안 된다
        List<Masking.Candidate> birth = Masking.detect(List.of(in("T", "BIRTH_DT", "BIRTH일시", null, "VARCHAR2", 8L),
                in("T", "BRTH_DE", null, "출생일자", "VARCHAR2", 8L)), Masking.rules()).candidates();
        assertEquals(List.of("BIRTH_DT", "BRTH_DE"), birth.stream().map(Masking.Candidate::col).toList());
    }

    /** 3-19(B-04) — 예약어 표·컬럼 이름은 방언 따옴표로, 접는 방언은 접은 글자로. 예약어 아닌 이름은 그대로 */
    @Test
    void reservedNamesAreQuoted() {
        List<Masking.Candidate> kw = List.of(new Masking.Candidate("APP", "USER", "ORDER", "name", "keyword", null, null, "VARCHAR", 50L, true));
        String ms = Masking.sql(kw, "mssql", Masking.rules());
        assertTrue(ms.contains("[USER]") && ms.contains("[ORDER]"), ms);
        assertTrue(Masking.sql(kw, "mariadb", Masking.rules()).contains("`USER`"), Masking.sql(kw, "mariadb", Masking.rules()));
        String ora = Masking.sql(kw, "oracle", Masking.rules());
        assertTrue(ora.contains("\"USER\"") && ora.contains("\"ORDER\""), ora);
        String pg = Masking.sql(kw, "postgresql", Masking.rules());
        assertTrue(pg.contains("\"user\"") && pg.contains("\"order\""), pg);
        String plain = Masking.sql(List.of(new Masking.Candidate("APP", "TB_MBER", "MBER_NM", "name", "keyword", null, null, "VARCHAR", 50L, true)),
                "oracle", Masking.rules());
        assertTrue(plain.contains("APP.TB_MBER") && plain.contains("MBER_NM") && !plain.contains("\""), plain);
    }

    /** H2 호환 모드 — 함수를 받는 방언만(H2 에 STRPOS·REPLICATE 가 없어 PostgreSQL·MSSQL 은 V-19 컨테이너에서) */
    @Test
    void updatesMaskValuesOnH2() throws Exception {
        List<Masking.Candidate> c = Masking.detect(List.of(
                in("T", "USER_NM", "사용자명", null, "VARCHAR", 30L),
                in("T", "MBTLNUM", null, null, "VARCHAR", 20L),
                in("T", "EMAIL", null, null, "VARCHAR", 50L)), Masking.rules()).candidates();
        assertEquals(3, c.size(), c.toString());
        for (String[] mode : new String[][] { { "oracle", "Oracle" }, { "mariadb", "MariaDB" } }) {
            try (Connection h = DriverManager.getConnection("jdbc:h2:mem:mask" + mode[0] + ";MODE=" + mode[1], "sa", "");
                    Statement st = h.createStatement()) {
                st.execute("CREATE SCHEMA APP");
                st.execute("CREATE TABLE APP.T (ID INT, USER_NM VARCHAR(30), MBTLNUM VARCHAR(20), EMAIL VARCHAR(50))");
                st.execute("INSERT INTO APP.T VALUES (1, '홍길동', '010-1234-5678', 'abcd@x.kr'), (2, '김', '0101', 'nomail'),"
                        + " (3, NULL, NULL, NULL), (4, '남궁민수', '02-123-4567', 'a@b.c')");
                String sql = Masking.sql(c, mode[0], Masking.rules());
                for (String stmt : sql.split(";\n")) {
                    String body = stmt.lines().filter(l -> !l.startsWith("--")).reduce("", (a, b) -> a + "\n" + b).trim();
                    if (!body.isEmpty()) {
                        st.execute(body);
                    }
                }
                List<String> got = new ArrayList<>();
                try (ResultSet rs = st.executeQuery("SELECT USER_NM, MBTLNUM, EMAIL FROM APP.T ORDER BY ID")) {
                    while (rs.next()) {
                        got.add(rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3));
                    }
                }
                assertEquals(List.of("홍**|*********5678|ab**@x.kr", "*|****|no****", "null|null|null", "남***|*******4567|*@b.c"), got,
                        mode[0] + "\n" + sql);
            }
        }
    }
}
