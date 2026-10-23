package egov.pos.metric;

/** 5-35a 양성 — 복잡도 16 · 길이 101 · 중첩 5 · 인자 8 을 서로 다른 메서드로 */
public class PosMetric {

    int branchy(int a) {
        int r = 0;
        if (a > 1) {
            r++;
        }
        if (a > 2) {
            r++;
        }
        if (a > 3) {
            r++;
        }
        if (a > 4) {
            r++;
        }
        if (a > 5) {
            r++;
        }
        if (a > 6) {
            r++;
        }
        if (a > 7) {
            r++;
        }
        if (a > 8) {
            r++;
        }
        if (a > 9) {
            r++;
        }
        if (a > 10) {
            r++;
        }
        if (a > 11) {
            r++;
        }
        if (a > 12) {
            r++;
        }
        if (a > 13) {
            r++;
        }
        if (a > 14) {
            r++;
        }
        if (a > 15) {
            r++;
        }
        return r;
    }

    int longOne() {
        int x = 0;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        x++;
        return x;
    }

    void deep(int a) {
        if (a > 0) {
            for (int i = 0; i < a; i++) {
                while (a > i) {
                    try {
                        if (i > 1) {
                            a--;
                        }
                    } finally {
                        a--;
                    }
                }
            }
        }
    }

    int many(int a, int b, int c, int d, int e, int f, int g, int h) {
        return a + b + c + d + e + f + g + h;
    }
}
