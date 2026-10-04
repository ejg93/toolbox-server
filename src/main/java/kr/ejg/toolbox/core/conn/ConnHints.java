package kr.ejg.toolbox.core.conn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** 접속 오류 → 한글 안내 한 줄(1-18). 표는 {@code conn/hints.yaml}, 위에서부터 첫 줄. 맞는 줄이 없으면 empty */
public final class ConnHints {

    /** message 는 정규식(오류문 어디든). dialect·sqlState·errorCode·message 중 빈 칸은 안 본다 */
    public record Hint(String dialect, String sqlState, Integer errorCode, String message, String hint) {
    }

    private record Row(Hint h, Pattern p) {
    }

    private final List<Row> rows;

    private ConnHints(List<Row> rows) {
        this.rows = List.copyOf(rows);
    }

    /** 정규식은 여기서 컴파일한다 — 틀리면 기동 실패 */
    @SuppressWarnings("unchecked")
    public static ConnHints load() {
        try (InputStream in = ConnHints.class.getResourceAsStream("/conn/hints.yaml")) {
            if (in == null) {
                throw new IllegalStateException("conn/hints.yaml 이 없다");
            }
            Map<String, Object> doc = new ObjectMapper(new YAMLFactory()).readValue(in, Map.class);
            List<Row> rows = new ArrayList<>();
            for (Map<String, Object> r : (List<Map<String, Object>>) doc.get("hints")) {
                Hint h = new Hint(str(r.get("dialect")), str(r.get("sqlState")),
                        r.get("errorCode") == null ? null : ((Number) r.get("errorCode")).intValue(), str(r.get("message")), str(r.get("hint")));
                if (h.hint() == null) {
                    throw new IllegalStateException("conn/hints.yaml 에 hint 가 빈 줄이 있다");
                }
                try {
                    rows.add(new Row(h, h.message() == null ? null : Pattern.compile(h.message())));
                } catch (PatternSyntaxException e) {
                    throw new IllegalStateException("conn/hints.yaml 정규식이 틀렸다: " + h.message(), e);
                }
            }
            return new ConnHints(rows);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String str(Object o) {
        return o == null || String.valueOf(o).isBlank() ? null : String.valueOf(o);
    }

    public List<Hint> hints() {
        return rows.stream().map(Row::h).toList();
    }

    public Optional<String> hint(String dialect, SQLException e) {
        String d = dialect == null ? "" : dialect.toLowerCase(Locale.ROOT);
        String msg = String.valueOf(e.getMessage());
        for (Row r : rows) {
            Hint h = r.h();
            if ((h.dialect() == null || h.dialect().equalsIgnoreCase(d))
                    && (h.sqlState() == null || h.sqlState().equals(e.getSQLState()))
                    && (h.errorCode() == null || h.errorCode() == e.getErrorCode())
                    && (r.p() == null || r.p().matcher(msg).find())) {
                return Optional.of(h.hint());
            }
        }
        return Optional.empty();
    }
}
