package egov.pos.order;

/** 5-32b — 선언 순서 가장자리(Checkstyle 13.4.2 실측 2026-10-21 과 같은 판정). 한 파일에 최상위 타입 여럿 */
class Fwd {
    private static final String B = Fwd.A + "x";
    static final String A = "a";
    private int p;
    public int q;
    void m() {
    }
}

interface Iface {
    void m();
    String X = "x";
    int Y = 1;
}

class Anon {
    static final int K = 1;
    Runnable r = new Runnable() {
        public void run() {
        }
        private int z;
    };
    void m() {
        Object o = new Object() {
            public String toString() {
                return "";
            }
            static final int W = 2;
        };
    }
}

record Rec(int a) {
    public Rec {
    }
    static final int K = 1;
    void m() {
    }
    private static int z;
}

enum En {
    A, B;
    void m() {
    }
    private static final int K = 1;
    private int x;
    En() {
    }
}

class Mods {
    private int a;
    static int s;
    public int b;
    Mods() {
    }
    static int t;
    void m() {
    }
    int c;
}

class Nested {
    void m() {
    }
    static class In {
        void x() {
        }
        static int k;
    }
    static int k2;
    static {
    }
    int i;
}

class Reset {
    private static int s1;
    public int i1;
    static int s3;
    protected int i2;
    private Reset() {
    }
    public Reset(int a) {
    }
    private void pm() {
    }
    public void qm() {
    }
}

class Grp {
    static int s1;
    private static int s2;
    static int s3;
    public static int s4;
    @Deprecated
    private int i1;
    int i2;
}

class Local {
    void m() {
        class L {
            void y() {
            }
            static final int Q = 1;
        }
    }
}
