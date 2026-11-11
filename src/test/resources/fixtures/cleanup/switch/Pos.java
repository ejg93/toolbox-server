package fixture;

/** 설계 64 — 코드 정리 switch(default 를 끝으로) */
class Pos {

    int moved(int k) {
        switch (k) {
            case 1:
                return 1;
            default:
                return 0;
            case 2:
                return 2;
        }
    }

    void movedBreak(int k) {
        switch (k) {
            // 기본
            default:
                use(0);
                break;
            case 1:
                use(1);
                break;
        }
    }

    void arrow(int k) {
        switch (k) {
            default -> use(0);
            case 1 -> use(1);
        }
    }

    void falls(int k) {
        switch (k) {
            default:
                use(0);
            case 1:
                use(1);
                break;
        }
    }

    void prevFalls(int k) {
        switch (k) {
            case 1:
                use(1);
            default:
                use(0);
                break;
            case 2:
                use(2);
                break;
        }
    }

    void lastFalls(int k) {
        switch (k) {
            default:
                use(0);
                break;
            case 1:
                use(1);
        }
    }

    void grouped(int k) {
        switch (k) {
            default:
            case 1:
                use(1);
                break;
            case 2:
                use(2);
                break;
        }
    }

    void use(int x) {
    }
}
