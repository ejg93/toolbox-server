package ext;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** 5-38b 양성 — SpotBugs 늘 null 역참조 · FindSecBugs SQL 조립·경로 조작(인자가 그대로) */
public class Pos {

    public int npe() {
        String s = null;
        return s.length();
    }

    public boolean sql(Connection c, String name) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM T WHERE NAME = '" + name + "'")) {
            return rs.next();
        }
    }

    public File path(String name) {
        return new File("/data/" + name);
    }
}
