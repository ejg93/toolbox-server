package fixture;

import java.io.IOException;

/** 설계 64 음성 — 문장 꼴 다섯이 안 걸리는 꼴 */
class NegShape {

    static {
        System.setProperty("a", "b");
    }

    enum Kind {
        A, B;
    }

    int braces(boolean a, boolean b) {
        if (a) {
            return 1;
        } else if (b) {
            return 2;
        } else {
            return 3;
        }
    }

    void loops(int[] xs) {
        for (;;) {
            break;
        }
        while (xs.length > 9) {
            use(1);
        }
        Runnable r = () -> use(2);
        r.run();
    }

    int cases(int k) {
        switch (k) {
            case 1: {
                return 1;
            }
            default:
                return 0;
        }
    }

    int arrow(int k) {
        return switch (k) {
            case 1 -> 1;
            default -> 0;
        };
    }

    void sleeps() throws IOException {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        try {
            Thread.sleep(1);
            throw new IOException("x");
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) { Thread.currentThread().interrupt(); }
            use(e.hashCode());
        }
    }

    void use(int x) {
    }
}
