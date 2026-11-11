package fixture;

/** 설계 64 — 코드 정리 syntax(중괄호·빈 문장·홀로 블록) */
class Pos {

    int braces(boolean a, boolean b) {
        if (a) return 1;
        if (a)
            return 2;
        else if (b)
            return 3;
        else
            return 4;
    }

    void loops(int[] xs) {
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

    void skips(boolean a, boolean b) {
        if (a) // 주석
            use(1);
        if (a) if (b) use(2);
    }

    void use(int x) {
    }
}
