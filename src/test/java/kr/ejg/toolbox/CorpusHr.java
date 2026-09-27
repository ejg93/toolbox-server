package kr.ejg.toolbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import kr.ejg.toolbox.core.text.Csv;

/**
 * 실물 표본 Oracle HR 스키마(db-sample-schemas v23.3)를 H2(Oracle 모드) 메모리 DB 로 옮긴다(V-6) — 컨테이너 없이 실데이터 스냅샷.
 * 변환은 문장 단위로 그대로 넣고 실패를 센다(변환본 파일을 두지 않는다):
 * SQL*Plus 줄을 빼고, INSERT 를 감싼 {@code BEGIN … END; /} 는 벗기고, ORGANIZATION INDEX·같은 이름 UNIQUE INDEX 를 빼고,
 * ALTER … ADD ( 제약, 제약 ) 을 나눈다. {@code DISABLE CONSTRAINT} 가 H2 에 없어 적재 동안만 참조 무결성을 끈다.
 */
public final class CorpusHr {

    public record Loaded(Connection conn, String url, int ok, List<String> failed) {
    }

    private static final Pattern SQLPLUS = Pattern.compile("(?im)^[ \\t]*(?:REM(?:ARK)?\\b|PROMPT\\b|SET\\s+\\w+|SPOOL\\b|WHENEVER\\b"
            + "|DEFINE\\b|UNDEFINE\\b|COLUMN\\b|CONNECT\\b|SHOW\\b|PAUSE\\b|EXIT\\b|@)[^\\n]*$");
    private static final AtomicInteger SEQ = new AtomicInteger();

    private CorpusHr() {
    }

    public static Loaded open() throws IOException, SQLException {
        return open(true);
    }

    /** populate 가 거짓이면 스키마만(INSERT 생성문 실행용 빈 DB) */
    public static Loaded open(boolean populate) throws IOException, SQLException {
        Path dir = CorpusFiles.root().resolve("db-samples").resolve("human_resources");
        String url = "jdbc:h2:mem:corpus-hr-" + SEQ.incrementAndGet() + ";MODE=Oracle;DB_CLOSE_DELAY=-1";
        Connection c = DriverManager.getConnection(url, "sa", "");
        List<String> failed = new ArrayList<>();
        int ok = 0;
        try (Statement s = c.createStatement()) {
            ok += run(s, dir.resolve("hr_create.sql"), failed);
            if (populate) {
                s.execute("SET REFERENTIAL_INTEGRITY FALSE");
                ok += run(s, dir.resolve("hr_populate.sql"), failed);
                s.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
        }
        return new Loaded(c, url, ok, failed);
    }

    static int run(Statement s, Path file, List<String> failed) throws IOException {
        String t = Csv.decode(Files.readAllBytes(file));
        // populate 는 INSERT 를 BEGIN … END; / 로 감쌌다 — 블록을 벗겨 안쪽 문장을 살린다(지우면 0 행)
        t = t.replaceAll("(?im)^[ \t]*BEGIN[ \t]*$", "").replaceAll("(?im)^[ \t]*END;[ \t]*$", "").replaceAll("(?m)^[ \t]*/[ \t]*$", "");
        t = SQLPLUS.matcher(t).replaceAll("");
        int ok = 0;
        for (String stmt : h2(statements(t))) {
            try {
                s.execute(stmt);
                ok++;
            } catch (SQLException e) {
                String first = stmt.lines().findFirst().orElse("").strip();
                failed.add(file.getFileName() + ": " + (first.length() > 60 ? first.substring(0, 60) : first));
            }
        }
        return ok;
    }

    private static final Pattern ALTER_ADD = Pattern.compile("(?is)^(ALTER\\s+TABLE\\s+\\S+\\s+ADD)\\s*\\((.*)\\)\\s*$");
    private static final Pattern PK_COLS = Pattern.compile("(?is)PRIMARY\\s+KEY\\s*\\(([^)]*)\\)");

    /** H2 가 못 받는 오라클 모양 둘 — ORGANIZATION INDEX 떼기, ALTER TABLE … ADD ( 제약, 제약 ) 을 제약마다 한 문장으로 */
    static List<String> h2(List<String> stmts) {
        List<String> out = new ArrayList<>();
        for (String st : stmts) {
            if (st.matches("(?is)^CREATE\\s+UNIQUE\\s+INDEX\\b.*")) {
                continue; // 같은 이름의 PK 제약이 제 인덱스를 만든다 — H2 에서 이름이 겹친다
            }
            String s = st.replaceAll("(?i)\\bORGANIZATION\\s+INDEX\\b", "");
            java.util.regex.Matcher m = ALTER_ADD.matcher(s);
            if (m.matches()) {
                for (String part : topLevel(m.group(2))) {
                    // Oracle 은 PK 를 더하면 컬럼을 NOT NULL 로 맞추고, H2 는 먼저 NOT NULL 이어야 한다
                    java.util.regex.Matcher pk = PK_COLS.matcher(part);
                    if (pk.find()) {
                        String table = m.group(1).replaceAll("(?is)^ALTER\\s+TABLE\\s+(\\S+)\\s+ADD$", "$1");
                        for (String col : pk.group(1).split(",")) {
                            out.add("ALTER TABLE " + table + " ALTER COLUMN " + col.strip() + " SET NOT NULL");
                        }
                    }
                    out.add(m.group(1) + " " + part.strip());
                }
            } else {
                out.add(s);
            }
        }
        return out;
    }

    /** 괄호 밖 쉼표로 나눈다 */
    static List<String> topLevel(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int from = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                out.add(s.substring(from, i));
                from = i + 1;
            }
        }
        out.add(s.substring(from));
        return out;
    }

    /** -- 줄 주석을 떼고 문자열 밖 ; 로 나눈다 */
    static List<String> statements(String t) {
        List<String> out = new ArrayList<>();
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
}
