package kr.ejg.toolbox.core.db;

import java.nio.file.Path;

/** {@link DbTest} 의 자식 JVM — H2 를 열고 「ready」 를 찍은 뒤 stdin 이 닫힐 때까지 쥐고 있다. */
public final class DbHolder {

    private DbHolder() {
    }

    public static void main(String[] args) throws Exception {
        try (Db db = Db.open(Path.of(args[0]))) {
            System.out.println("ready");
            System.out.flush();
            while (System.in.read() != -1) {
                // stdin 이 닫힐 때까지
            }
        }
    }
}
