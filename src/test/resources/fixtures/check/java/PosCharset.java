package egov.pos;

import java.io.ByteArrayOutputStream;

/** 설계 60 — java.defaultCharset 양성: String 수신자 무인자 getBytes() · byte[] 한 인자 new String */
class PosCharset {
    byte[] literal() {
        return "가".getBytes();
    }

    byte[] param(String s) {
        return s.getBytes();
    }

    byte[] built(StringBuilder sb) {
        return sb.toString().getBytes();
    }

    String fromBytes(byte[] b) {
        return new String(b);
    }

    String fromStream(ByteArrayOutputStream bos) {
        return new String(bos.toByteArray());
    }
}
