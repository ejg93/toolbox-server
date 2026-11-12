package fixture;

/** 설계 64 양성 — 문장 꼴 다섯 */
class PosShape {

    int braces(boolean a, boolean b, int[] xs) {
        if (a) return 1;
        if (a)
            return 2;
        else if (b)
            return 3;
        else
            return 4;
    }

    void loops(int[] xs) {
        for (int i = 0; i < xs.length; i++)
            xs[i] = 0;
        for (int x : xs)
            use(x);
        while (xs.length > 9)
            use(1);
        do
            use(2);
        while (xs.length > 9);
    }

    void empties() {
        use(1);;
        ;
        {
            use(2);
        }
        {
            int y = 3;
            use(y);
        }
    }

    int defaults(int k) {
        switch (k) {
            case 1:
                return 1;
            default:
                return 0;
            case 2:
                return 2;
        }
    }

    void defaultBreak(int k) {
        switch (k) {
            default:
                use(0);
                break;
            case 1:
                use(1);
                break;
        }
    }

    void sleeps() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            use(e.hashCode());
        }
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
        }
    }

    void use(int x) {
    }

    void union() {
        try {
            Thread.sleep(1);
        } catch (IllegalArgumentException | InterruptedException e) {
            use(e.hashCode());
        }
    }
}
