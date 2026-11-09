package charset;

/** 설계 60 — 제 클래스 StandardCharsets 를 import 없이 쓰면 끼운 import 가 가린다 — 건너뜀 */
class Clash {
    byte[] a(String s) {
        StandardCharsets.touch();
        return s.getBytes();
    }
}

class StandardCharsets {
    static void touch() {
    }
}
