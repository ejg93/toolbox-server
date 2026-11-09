package charset;

/** 설계 60 — 문자셋 고침: getBytes() 둘 · new String(b) · 이름끼리 == (StandardCharsets·Objects import 를 한 번에 끼운다) */
class Pos {
    byte[] a(String s) {
        return s.getBytes();
    }

    byte[] b() {
        return "가".getBytes();
    }

    String c(byte[] b) {
        return new String(b);
    }

    boolean d(String x, String y) {
        return x == y;
    }
}
