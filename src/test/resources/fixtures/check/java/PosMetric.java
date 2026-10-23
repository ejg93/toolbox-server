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
        x += 1;
        x += 2;
        x += 3;
        x += 4;
        x += 5;
        x += 6;
        x += 7;
        x += 8;
        x += 9;
        x += 10;
        x += 11;
        x += 12;
        x += 13;
        x += 14;
        x += 15;
        x += 16;
        x += 17;
        x += 18;
        x += 19;
        x += 20;
        x += 21;
        x += 22;
        x += 23;
        x += 24;
        x += 25;
        x += 26;
        x += 27;
        x += 28;
        x += 29;
        x += 30;
        x += 31;
        x += 32;
        x += 33;
        x += 34;
        x += 35;
        x += 36;
        x += 37;
        x += 38;
        x += 39;
        x += 40;
        x += 41;
        x += 42;
        x += 43;
        x += 44;
        x += 45;
        x += 46;
        x += 47;
        x += 48;
        x += 49;
        x += 50;
        x += 51;
        x += 52;
        x += 53;
        x += 54;
        x += 55;
        x += 56;
        x += 57;
        x += 58;
        x += 59;
        x += 60;
        x += 61;
        x += 62;
        x += 63;
        x += 64;
        x += 65;
        x += 66;
        x += 67;
        x += 68;
        x += 69;
        x += 70;
        x += 71;
        x += 72;
        x += 73;
        x += 74;
        x += 75;
        x += 76;
        x += 77;
        x += 78;
        x += 79;
        x += 80;
        x += 81;
        x += 82;
        x += 83;
        x += 84;
        x += 85;
        x += 86;
        x += 87;
        x += 88;
        x += 89;
        x += 90;
        x += 91;
        x += 92;
        x += 93;
        x += 94;
        x += 95;
        x += 96;
        x += 97;
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
