package kr.ejg.toolbox.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.out.println("toolbox-server " + version());
    }

    public static String version() {
        try (InputStream in = Main.class.getResourceAsStream("/version.properties")) {
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
