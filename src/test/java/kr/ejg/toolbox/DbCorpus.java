package kr.ejg.toolbox;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.meta.ForeignKey;
import kr.ejg.toolbox.core.meta.Table;
import kr.ejg.toolbox.core.text.Csv;

/**
 * 컨테이너 실물 표본(번들 10, PLAN 4장) — 표본의 원본 스크립트(eGov 방언별·chinook 방언별·Oracle HR)를 변환 없이 컨테이너에 넣는다.
 * 넣지 않는 것: DB·사용자 수준 문장(CREATE/DROP DATABASE·USE·\c·CREATE/DROP USER·GRANT·conn) — 스키마 하나에 다 넣는다.
 * DDL 과 데이터(INSERT·UPDATE·DELETE·COMMIT)를 나눠 넣는다 — INSERT 생성 표본(V-10)은 빈 표에서 돈다.
 */
public final class DbCorpus {

    public enum Dialect {
        POSTGRES("postgresql", "pg"), MARIA("mariadb", "mysql"), MSSQL("mssql", "mssql"), ORACLE("oracle", "oracle");

        /** MetaSources·InsertGen 방언 이름 */
        public final String meta;
        /** 순수본 sql_snippets 탭 */
        public final String tab;

        Dialect(String meta, String tab) {
            this.meta = meta;
            this.tab = tab;
        }
    }

    public enum Kind { DDL, DATA }

    public record Loaded(int ok, List<String> failed) {
    }

    private static final Pattern SKIP = Pattern.compile("(?is)^(?:DROP\\s+DATABASE|CREATE\\s+DATABASE|USE\\b|\\\\c\\b|\\\\connect\\b"
            + "|CREATE\\s+USER|DROP\\s+USER|ALTER\\s+USER|GRANT\\b|conn(?:ect)?\\s+\\S+/|IF\\s+EXISTS\\s*\\(\\s*SELECT\\s+name\\s+FROM\\s+master).*");
    /** 데이터 쪽 — HR populate 는 FK 를 끄고 넣고 다시 켠다(ALTER … DISABLE/ENABLE CONSTRAINT 도 데이터 쪽) */
    private static final Pattern DATA = Pattern.compile("(?is)^(?:INSERT|UPDATE|DELETE|COMMIT|MERGE"
            + "|ALTER\\s+TABLE\\s+\\S+\\s+(?:DISABLE|ENABLE)\\s+CONSTRAINT)\\b.*");
    private static final Pattern SQLPLUS = Pattern.compile("(?im)^[ \\t]*(?:REM(?:ARK)?\\b|PROMPT\\b|SET\\s+\\w+|SPOOL\\b|WHENEVER\\b"
            + "|DEFINE\\b|UNDEFINE\\b|COLUMN\\b|SHOW\\b|PAUSE\\b|EXIT\\b|CONN(?:ECT)?\\s+\\S+/|@)[^\\n]*$");

    private DbCorpus() {
    }

    /** 스크립트 하나를 kind 에 맞는 문장만 넣는다. 실패는 파일·첫 줄·SQLState 로 모은다(값·데이터 없이) */
    public static Loaded load(Connection c, Path file, Dialect d, Kind kind) throws IOException {
        String t = Csv.decode(Files.readAllBytes(file));
        if (t.startsWith(String.valueOf((char) 0xFEFF))) {
            t = t.substring(1);
        }
        int ok = 0;
        List<String> failed = new ArrayList<>();
        try (Statement s = c.createStatement()) {
            for (String st : statements(t, d)) {
                if (SKIP.matcher(st).matches() || DATA.matcher(st).matches() != (kind == Kind.DATA)) {
                    continue;
                }
                try {
                    s.execute(st);
                    ok++;
                } catch (SQLException e) {
                    String first = st.lines().findFirst().orElse("").strip();
                    failed.add(file.getFileName() + ": " + (first.length() > 50 ? first.substring(0, 50) : first) + " [" + e.getSQLState() + "]");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return new Loaded(ok, failed);
    }

    /** 문장 나누기 — MSSQL 은 GO 줄로 묶음째, 그 밖은 SQL*Plus 줄·줄 주석을 떼고 문자열 밖 ; 로. Oracle 의 BEGIN … END; / 는 벗긴다 */
    public static List<String> statements(String text, Dialect d) {
        List<String> out = new ArrayList<>();
        if (d == Dialect.MSSQL) {
            for (String batch : text.split("(?im)^[ \\t]*GO[ \\t]*$")) {
                // 묶음 머리의 /* … */ 는 뗀다 — chinook 은 데이터 묶음이 주석 머리로 시작해 DDL 로 잘못 갈렸다(첫 판 dataOk 0)
                String b = stripLineComments(batch).strip().replaceFirst("(?s)^(?:/\\*.*?\\*/\\s*)+", "");
                if (!b.isEmpty()) {
                    out.add(b);
                }
            }
            return out;
        }
        String t = text;
        if (d == Dialect.ORACLE) {
            t = SQLPLUS.matcher(t).replaceAll("");
            t = t.replaceAll("(?im)^[ \\t]*BEGIN[ \\t]*$", "").replaceAll("(?im)^[ \\t]*END;[ \\t]*$", "").replaceAll("(?m)^[ \\t]*/[ \\t]*$", "");
        }
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == '\'') {
                q = !q;
            } else if (!q && ch == '-' && i + 1 < t.length() && t.charAt(i + 1) == '-') {
                int e = t.indexOf('\n', i);
                i = e < 0 ? t.length() : e - 1;
                continue;
            } else if (!q && ch == '/' && i + 1 < t.length() && t.charAt(i + 1) == '*') {
                int e = t.indexOf("*/", i + 2);
                i = e < 0 ? t.length() : e + 1;
                continue;
            } else if (!q && ch == ';') {
                if (!cur.toString().isBlank()) {
                    out.add(cur.toString().strip());
                }
                cur.setLength(0);
                continue;
            }
            cur.append(ch);
        }
        if (!cur.toString().isBlank()) {
            out.add(cur.toString().strip());
        }
        return out;
    }

    static String stripLineComments(String s) {
        return s.replaceAll("(?m)^[ \\t]*--[^\\n]*$", "");
    }

    /**
     * 옛 판 접속(V-21) — jar 를 따로 연 클래스로더에서 {@code Driver} 를 만들어 직접 connect. 부모는 플랫폼 로더라 테스트 classpath 의
     * 같은 이름 드라이버(ojdbc11 의 {@code oracle.jdbc.OracleDriver})를 안 본다. DriverManager 를 거치지 않는다 — 드라이버가 정적 초기화에서
     * 스스로 등록해도 다른 로더의 클래스라 다른 테스트의 {@code DriverManager.getConnection} 이 고르지 않는다
     */
    public static Connection connectWith(Path jar, String driverClass, String url, String user, String pw) throws SQLException {
        Driver d;
        try {
            URLClassLoader cl = new URLClassLoader(new URL[] {jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
            d = (Driver) Class.forName(driverClass, true, cl).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | MalformedURLException e) {
            throw new IllegalStateException(jar + " 에서 " + driverClass + " 를 못 만든다", e);
        }
        Properties p = new Properties();
        p.setProperty("user", user);
        p.setProperty("password", pw);
        Connection c = d.connect(url, p);
        if (c == null) {
            throw new SQLException("No suitable driver found for " + url, "08001");
        }
        return c;
    }

    /** 부모 먼저 순서 — 순환에 든 테이블은 cyclic 에 */
    public static List<Table> parentsFirst(List<Table> ts, Set<String> cyclic) {
        Map<String, Table> by = new LinkedHashMap<>();
        ts.forEach(t -> by.put(t.name().toUpperCase(Locale.ROOT), t));
        List<Table> out = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        boolean moved = true;
        while (moved) {
            moved = false;
            for (Table t : by.values()) {
                String k = t.name().toUpperCase(Locale.ROOT);
                if (done.contains(k)) {
                    continue;
                }
                boolean ready = t.fks().stream().map(ForeignKey::refTable).map(r -> r.toUpperCase(Locale.ROOT))
                        .allMatch(r -> r.equals(k) || done.contains(r) || !by.containsKey(r));
                if (ready) {
                    out.add(t);
                    done.add(k);
                    moved = true;
                }
            }
        }
        by.keySet().stream().filter(k -> !done.contains(k)).forEach(cyclic::add);
        return out;
    }

    /** 표본 스크립트 — 방언별 (DDL 파일들, 데이터 파일들). 순서대로 넣는다 */
    public static List<Path> ddl(Dialect d) throws IOException {
        List<Path> out = new ArrayList<>();
        switch (d) {
            case ORACLE -> {
                out.add(CorpusFiles.root().resolve("db-samples/human_resources/hr_create.sql"));
                out.addAll(egov("oracle", "ddl"));
                out.addAll(egov("oracle", "comment"));
                out.add(chinook("Chinook_Oracle.sql"));
            }
            case POSTGRES -> {
                out.addAll(egov("postgres", "ddl"));
                out.addAll(egov("postgres", "comment"));
                out.add(chinook("Chinook_PostgreSql.sql"));
            }
            case MARIA -> {
                out.addAll(egov("maria", "ddl"));
                out.addAll(egov("maria", "comment"));
                out.add(chinook("Chinook_MySql.sql"));
            }
            case MSSQL -> out.add(chinook("Chinook_SqlServer.sql"));
            default -> throw new IllegalArgumentException(d.name());
        }
        return out;
    }

    public static List<Path> data(Dialect d) throws IOException {
        List<Path> out = new ArrayList<>();
        switch (d) {
            case ORACLE -> {
                out.add(CorpusFiles.root().resolve("db-samples/human_resources/hr_populate.sql"));
                out.addAll(egov("oracle", "dml"));
                out.add(chinook("Chinook_Oracle.sql"));
            }
            case POSTGRES -> {
                out.addAll(egov("postgres", "dml"));
                out.add(chinook("Chinook_PostgreSql.sql"));
            }
            case MARIA -> {
                out.addAll(egov("maria", "dml"));
                out.add(chinook("Chinook_MySql.sql"));
            }
            case MSSQL -> out.add(chinook("Chinook_SqlServer.sql"));
            default -> throw new IllegalArgumentException(d.name());
        }
        return out;
    }

    /** eGov 공통 스크립트(모듈별 src/script 는 공통을 되풀이해 빼고 script/<kind>/<방언>/ 만) */
    static List<Path> egov(String dialect, String kind) throws IOException {
        return CorpusFiles.files("egov", "*.sql").stream().filter(p -> CorpusFiles.rel(p).startsWith("egov/script/" + kind + "/" + dialect + "/")).toList();
    }

    static Path chinook(String name) {
        return CorpusFiles.root().resolve("chinook/ChinookDatabase/DataSources").resolve(name);
    }
}
