package egov.pos;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

/** 설계 50 — tryWithResources 양성: 꼴 B 하나 · 꼴 B 둘(catch Exception) · 꼴 A · null 지킴 + try/catch close */
public class PosResource {
    private static final Logger LOG = Logger.getLogger("pos");

    int formB(String path) throws IOException {
        InputStream is = null;
        try {
            is = new FileInputStream(path);
            return is.read();
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    void formBTwo(String url) throws SQLException {
        Connection c = null;
        PreparedStatement ps = null;
        try {
            c = DriverManager.getConnection(url);
            ps = c.prepareStatement(url);
            ps.execute();
        } catch (Exception e) {
            LOG.warning(e.getMessage());
        } finally {
            if (ps != null) {
                ps.close();
            }
            if (c != null) {
                c.close();
            }
        }
    }

    int formA(String path) throws IOException {
        FileInputStream in = new FileInputStream(path);
        try {
            return in.read();
        } finally {
            in.close();
        }
    }

    int guarded(String path) throws IOException {
        InputStream is = null;
        try {
            is = new FileInputStream(path);
            return is.read();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    LOG.warning(e.getMessage());
                }
            }
        }
    }
}
