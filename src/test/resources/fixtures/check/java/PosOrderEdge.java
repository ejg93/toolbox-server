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

/** 5-33 — 옮겨도 컴파일되는 접근자 역순 필드는 그대로 지적(Checkstyle 은 같은 이름만 있으면 면제) */
class FwdNot {
    public static final String HEAD = "h";
    private static final String UTF_8 = "u";
    public static final String QUAL = FwdNot.UTF_8;
    public static final String OTHER = java.nio.charset.StandardCharsets.UTF_8.name();
    public static final Runnable ANON = new Runnable() {
        public void run() {
            System.out.println(UTF_8);
        }
    };
    public static final java.util.function.UnaryOperator<String> SHADOW = UTF_8 -> UTF_8;
    public static final String NOT_JUMPED = HEAD + "x";
    private int i;
    public int j = this.i;
}

/** 5-33 — 묶인 필드가 목표 자리 앞에 있으면 읽어도 옮길 수 있다 */
class FwdChainNot {
    protected static final String SEP = "/";
    public static final String ROOT = "r" + SEP;
    static final String MID = "m";
    protected static final String FILE = ROOT + "f";
}
