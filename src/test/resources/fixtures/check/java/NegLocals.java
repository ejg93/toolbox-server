package egov.neg;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** 설계 60 — java.unusedLocal 음성: 씀 · 대입만 · 람다·메서드 참조 · 일부러 안 쓰는 이름 · 억제 · for·try 변수 · 앞 case 선언을 뒤 case 에서 씀 */
class NegLocals {
    int used() {
        int a = 1;
        return a;
    }

    int assignedOnly() {
        int b;
        b = 2;
        return 0;
    }

    IntSupplier captured() {
        int c = 3;
        return () -> c + 1;
    }

    Supplier<Integer> methodRef(String t) {
        String u = t;
        return u::length;
    }

    int named() {
        int ignored = 0;
        return 1;
    }

    int suppressed() {
        @SuppressWarnings("unused") int k = 0;
        return 1;
    }

    int loops(int[] xs, String p) throws java.io.IOException {
        int s = 0;
        for (int x : xs) {
            s++;
        }
        try (java.io.StringReader in = new java.io.StringReader(p)) {
            s++;
        }
        return s;
    }

    int inSwitch(int k) {
        int r = 0;
        switch (k) {
            case 1:
                int z = 3;
                break;
            case 2:
                z = 5;
                r = z;
                break;
            default:
                break;
        }
        return r;
    }
}
