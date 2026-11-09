package egov.pos;

/** 설계 60 — java.unusedLocal 양성 */
class PosLocals {
    int plain() {
        int count = 0;
        String label = "x";
        return 1;
    }

    void call(java.util.List<String> xs) {
        int n = xs.size();
    }

    void two() {
        int a = 1, b = 2;
        System.identityHashCode(a);
    }

    void inSwitch(int k) {
        switch (k) {
            case 1:
                int z = 3;
                break;
            default:
                break;
        }
    }
}
