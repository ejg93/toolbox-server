package egov.neg;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/** 설계 60 — java.defaultCharset 음성: 문자셋 인자 · String 아닌 수신자 · 못 푸는 수신자 · char[] · 여러 인자 */
class NegCharset {
    byte[] named(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    byte[] byName(String s) throws UnsupportedEncodingException {
        return s.getBytes("UTF-8");
    }

    byte[] upload(Blob0 mf) {
        return mf.getBytes();
    }

    byte[] unknown(Vo0 vo) {
        return vo.getName().getBytes();
    }

    String chars(char[] cs) {
        return new String(cs);
    }

    String withCharset(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }

    String slice(byte[] b) {
        return new String(b, 0, 1, StandardCharsets.UTF_8);
    }
}

/** 업로드 파일 꼴 — getBytes() 가 파일 내용 */
class Blob0 {
    byte[] getBytes() {
        return new byte[0];
    }
}

/** 형식을 못 푸는 호출 결과 */
class Vo0 {
    String getName() {
        return "";
    }
}
