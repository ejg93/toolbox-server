package kr.ejg.toolbox.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** {@code version.properties}(빌드가 pom 버전을 채운다) 값. ping·{@code version} 명령이 같은 값을 쓴다. */
public final class Version {

    private static final String VALUE = read();

    private Version() {
    }

    public static String get() {
        return VALUE;
    }

    private static String read() {
        try (InputStream in = Version.class.getResourceAsStream("/version.properties")) {
            if (in == null) {
                return "unknown";
            }
            Properties p = new Properties();
            p.load(in);
            return p.getProperty("version", "unknown");
        } catch (IOException e) {
            return "unknown";
        }
    }
}
