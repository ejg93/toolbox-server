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

/** 설계 58 — 꼴 C: finally 없이 try 몸 끝 문장들이 close(catch 있음) */
class PosResource58 {
    int formC(String p) {
        int n = -1;
        InputStream is = null;
        try {
            is = new FileInputStream(p);
            n = is.read();
            is.close();
        } catch (IOException e) {
            n = -2;
        }
        return n;
    }

    void copyC(String a, String b) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(a);
            out = new FileOutputStream(b);
            out.write(in.read());
            out.close();
            in.close();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }
}

/** 5-63 — 꼴 A': try 없이 같은 블록에서 선언·close(resourceLeak 도 같은 줄) */
class PosResource63 {
    void one(String p, byte[] b) throws IOException {
        java.io.OutputStream out = new java.io.FileOutputStream(p);
        out.write(b);
        out.close();
    }

    void two(String a, String b) throws IOException {
        InputStream in = new FileInputStream(a);
        java.io.OutputStream out = new java.io.FileOutputStream(b);
        out.write(in.read());
        in.close();
        out.close();
    }

    void guardedClose(String p, boolean w) throws IOException {
        if (w) {
            java.io.FileWriter fw = new java.io.FileWriter(p, true);
            fw.write("x");
            if (fw != null) {
                fw.close();
            }
        }
    }
}
