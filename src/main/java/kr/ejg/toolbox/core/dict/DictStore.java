package kr.ejg.toolbox.core.dict;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kr.ejg.toolbox.core.db.Db;
import kr.ejg.toolbox.core.text.Csv;

/**
 * 표준단어 사전(H2 dict_word·dict_domain, 2.2 A·3-1). 행안부 공통표준단어·도메인은 jar 에 동봉하고 첫 기동에 적재한다.
 * 기관표준단어는 파일로 통째 교체, 사용자 입력은 한 줄씩. 순수본 applyDict·applyOv·setUser 와 같은 규칙.
 */
public final class DictStore {

    public static final String MOI_WORDS = "/dict/moi-words-20251101.csv";
    public static final String MOI_DOMAINS = "/dict/moi-domains.csv";
    static final String MOI_SOURCE = "moi-20251101";

    /** 행안부 CSV 헤더 — 이름을 고정으로 찾는다(순수본의 「포함 검색」 대신 — 판이 바뀌면 조용히 틀리지 않고 멈춘다) */
    private static final String H_KO = "공통표준단어명";
    private static final String H_ABBR = "공통표준단어영문약어명";
    private static final String H_EN = "공통표준단어 영문명";
    private static final String H_DESC = "공통표준단어 설명";
    private static final String H_FORM = "형식단어여부";
    private static final String H_DOMAIN = "공통표준도메인분류명";
    private static final String H_SYN = "이음동의어 목록";
    private static final String H_FORBID = "금칙어 목록";

    /** 기관표준단어 열 후보 — 순수본 takeOv 의 fillSel 후보 그대로 */
    static final List<String> ORG_PHYS = List.of("물리명", "영문명", "TABLE_NAME", "COLUMN_NAME", "컬럼명", "테이블명", "영문");
    static final List<String> ORG_KO = List.of("논리명", "한글명", "한글", "설명", "명칭");

    /** 사전 한 줄(검색 응답) */
    public record Word(String kind, String abbr, String ko, String en, String domain, String formWord) {
    }

    /** 공통표준도메인 한 줄 — 순수본 DOMAINDB 11열 */
    public record Domain(String group, String cls, String name, String dataType, String length, String scale,
            String storeFormat, String dispFormat, String unit, String allowed, String description) {
    }

    private final Db db;

    public DictStore(Db db) {
        this.db = db;
    }

    /** 동봉 사전 적재 — 이미 있으면 건너뛴다. 새로 넣은 공통표준단어 수 */
    public int importMoi() throws SQLException {
        int words = 0;
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                if (count(c, "word") == 0) {
                    words = insertMoiWords(c);
                }
                if (countDomains(c) == 0) {
                    insertMoiDomains(c);
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
        return words;
    }

    private static int insertMoiWords(Connection c) throws SQLException {
        List<List<String>> rows = Csv.parse(Csv.decode(resource(MOI_WORDS)));
        List<String> h = rows.get(0);
        int ko = need(h, H_KO);
        int abbr = need(h, H_ABBR);
        int en = need(h, H_EN);
        int desc = need(h, H_DESC);
        int form = need(h, H_FORM);
        int dom = need(h, H_DOMAIN);
        int syn = need(h, H_SYN);
        int forbid = need(h, H_FORBID);
        Set<String> seen = new HashSet<>();
        int n = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO dict_word(kind, word_ko, abbr, word_en, domain, description, form_word, synonyms, forbidden, source)"
                + " VALUES ('word', ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 1; i < rows.size(); i++) {
                List<String> r = rows.get(i);
                String a = cell(r, abbr).trim().toUpperCase(Locale.ROOT);
                String k = cell(r, ko).trim();
                if (a.isEmpty() || k.isEmpty() || !seen.add(a)) {
                    continue; // 빈 약어·한글, 같은 약어 둘째 줄부터 — 순수본 applyDict
                }
                ps.setString(1, k);
                ps.setString(2, a);
                ps.setString(3, blankToNull(cell(r, en)));
                ps.setString(4, blankToNull(cell(r, dom)));
                ps.setString(5, blankToNull(cell(r, desc)));
                ps.setString(6, blankToNull(cell(r, form)));
                ps.setString(7, blankToNull(cell(r, syn)));
                ps.setString(8, blankToNull(cell(r, forbid)));
                ps.setString(9, MOI_SOURCE);
                ps.addBatch();
                n++;
            }
            ps.executeBatch();
        }
        return n;
    }

    private static void insertMoiDomains(Connection c) throws SQLException {
        List<List<String>> rows = Csv.parse(Csv.decode(resource(MOI_DOMAINS)), ',');
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO dict_domain(grp, cls, name, data_type, length, scale, store_format, disp_format, unit, allowed,"
                + " description) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 1; i < rows.size(); i++) {
                List<String> r = rows.get(i);
                for (int j = 0; j < 11; j++) {
                    ps.setString(j + 1, cell(r, j));
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** 기관표준단어 파일로 통째 교체. 넣은 수 */
    public int importOrg(byte[] csv) throws SQLException {
        List<List<String>> rows = Csv.parse(Csv.decode(csv));
        if (rows.size() < 2) {
            throw new IllegalArgumentException("빈 파일이다");
        }
        int phys = Csv.guess(rows.get(0), ORG_PHYS, false);
        int ko = Csv.guess(rows.get(0), ORG_KO, false);
        if (phys == ko) {
            ko = phys == 0 ? 1 : 0;
        }
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM dict_word WHERE kind = 'org'")) {
                    del.executeUpdate();
                }
                Set<String> seen = new HashSet<>();
                int n = 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dict_word(kind, word_ko, abbr, source) VALUES ('org', ?, ?, 'org')")) {
                    for (int i = 1; i < rows.size(); i++) {
                        String p = cell(rows.get(i), phys).trim().toUpperCase(Locale.ROOT);
                        String k = cell(rows.get(i), ko).trim();
                        if (p.isEmpty() || k.isEmpty() || !seen.add(p)) {
                            continue;
                        }
                        ps.setString(1, k);
                        ps.setString(2, p);
                        ps.addBatch();
                        n++;
                    }
                    ps.executeBatch();
                }
                c.commit();
                return n;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** 사용자 입력 — 약어 대문자, 있으면 바꾼다 */
    public void putUser(String abbr, String ko) throws SQLException {
        String a = abbr == null ? "" : abbr.trim().toUpperCase(Locale.ROOT);
        String k = ko == null ? "" : ko.trim();
        if (a.isEmpty() || k.isEmpty()) {
            throw new IllegalArgumentException("약어와 한글이 둘 다 있어야 한다");
        }
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "MERGE INTO dict_word(kind, abbr, word_ko, source) KEY(kind, abbr) VALUES ('user', ?, ?, 'user')")) {
            ps.setString(1, a);
            ps.setString(2, k);
            ps.executeUpdate();
        }
    }

    public boolean deleteUser(String abbr) throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM dict_word WHERE kind = 'user' AND abbr = ?")) {
            ps.setString(1, abbr == null ? "" : abbr.trim().toUpperCase(Locale.ROOT));
            return ps.executeUpdate() > 0;
        }
    }

    public int count(String kind) throws SQLException {
        try (Connection c = db.connect()) {
            return count(c, kind);
        }
    }

    /** 약어·한글 부분 검색, 최대 200. kind 가 null 이면 전부 */
    public List<Word> words(String q, String kind) throws SQLException {
        String like = "%" + (q == null ? "" : q.trim().toUpperCase(Locale.ROOT)) + "%";
        List<Word> out = new ArrayList<>();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT kind, abbr, word_ko, word_en, domain, form_word FROM dict_word"
                + " WHERE (? IS NULL OR kind = ?) AND (UPPER(abbr) LIKE ? OR word_ko LIKE ?) ORDER BY kind, abbr LIMIT 200")) {
            ps.setString(1, kind);
            ps.setString(2, kind);
            ps.setString(3, like);
            ps.setString(4, "%" + (q == null ? "" : q.trim()) + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Word(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                            rs.getString(6)));
                }
            }
        }
        return out;
    }

    public List<Domain> domains() throws SQLException {
        List<Domain> out = new ArrayList<>();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT grp, cls, name, data_type, length, scale, store_format, disp_format, unit, allowed, description"
                + " FROM dict_domain ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Domain(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11)));
            }
        }
        return out;
    }

    /** 변환기 입력. 공통표준단어는 넣은 순서(= CSV 순서)로 읽어 DOMKOR 규칙을 순수본과 같게 */
    public Dictionaries load() throws SQLException {
        Map<String, String> word = new LinkedHashMap<>();
        Map<String, String> org = new LinkedHashMap<>();
        Map<String, String> user = new LinkedHashMap<>();
        Map<String, Dictionaries.WordMeta> meta = new LinkedHashMap<>();
        Map<String, Dictionaries.WordMeta> domKor = new LinkedHashMap<>();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT kind, abbr, word_ko, form_word, domain, word_en, description, synonyms, forbidden FROM dict_word ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String kind = rs.getString(1);
                String abbr = rs.getString(2);
                String ko = rs.getString(3);
                switch (kind) {
                    case "word" -> {
                        Dictionaries.WordMeta m = new Dictionaries.WordMeta(nz(rs.getString(4)), nz(rs.getString(5)), nz(rs.getString(6)),
                                nz(rs.getString(7)), nz(rs.getString(8)), nz(rs.getString(9)));
                        word.put(abbr, ko);
                        meta.put(abbr, m);
                        Dictionaries.WordMeta prev = domKor.get(ko);
                        if (prev == null || (prev.domain().isEmpty() && !m.domain().isEmpty())) {
                            domKor.put(ko, m);
                        }
                    }
                    case "org" -> org.put(abbr, ko);
                    case "user" -> user.put(abbr, ko);
                    default -> {
                        // 모르는 종류는 무시
                    }
                }
            }
        }
        return new Dictionaries(word, org, user, meta, domKor);
    }

    private static int count(Connection c, String kind) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM dict_word WHERE kind = ?")) {
            ps.setString(1, kind);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int countDomains(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM dict_domain"); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static byte[] resource(String path) {
        try (InputStream in = DictStore.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("동봉 사전이 없다: " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int need(List<String> header, String name) {
        int i = Csv.column(header, name);
        if (i < 0) {
            throw new IllegalStateException("행안부 CSV 에 열이 없다: " + name + " — 판이 바뀌었으면 DictStore 헤더를 맞춘다");
        }
        return i;
    }

    private static String cell(List<String> row, int i) {
        return i >= 0 && i < row.size() ? row.get(i) : "";
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
