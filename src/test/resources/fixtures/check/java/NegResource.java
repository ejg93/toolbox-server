package egov.neg;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/** 설계 50 — tryWithResources 음성: 이미 try-with-resources · finally 에 다른 문장 · catch 안 사용 · try 뒤 사용 ·
 * 선언과 try 사이 다른 문장 · close 예외를 못 받음 · 자원 아닌 형식 */
public class NegResource {
    private static final Logger LOG = Logger.getLogger("neg");

    int already(String path) throws IOException {
        try (InputStream is = new FileInputStream(path)) {
            return is.read();
        }
    }

    int logged(String path) throws IOException {
        InputStream is = new FileInputStream(path);
        try {
            return is.read();
        } finally {
            is.close();
            LOG.info("closed");
        }
    }

    int inCatch(String path) throws IOException {
        InputStream is = null;
        try {
            is = new FileInputStream(path);
            return is.read();
        } catch (IOException e) {
            LOG.warning(is + e.getMessage());
            return 0;
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    int after(String path) throws IOException {
        InputStream is = new FileInputStream(path);
        try {
            is.read();
        } finally {
            is.close();
        }
        return is.available();
    }

    int between(String path) throws IOException {
        InputStream is = new FileInputStream(path);
        int n = path.length();
        try {
            return is.read() + n;
        } finally {
            is.close();
        }
    }

    int uncovered(String path) {
        InputStream is = null;
        try {
            is = open(path);
            return is.read();
        } catch (RuntimeException e) {
            LOG.warning(e.getMessage());
            return 0;
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

    void notResource(String path) {
        Session s = Session.open(path);
        try {
            s.run();
        } finally {
            s.close();
        }
    }

    private InputStream open(String path) {
        return getClass().getResourceAsStream(path);
    }
}
