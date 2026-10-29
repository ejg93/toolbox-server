package egov.pos;

/** 설계 42 — 요청 값이 지역 변수를 한 번 거쳐 싱크에 닿는다(정규식은 못 본다) */
public class PosTaint {
    private static final String IV = "0123456789abcdef";

    public String file(javax.servlet.http.HttpServletRequest request) throws Exception {
        javax.servlet.http.Cookie[] cs = request.getCookies();
        String name = cs[0].getValue();
        new java.io.FileInputStream(name).close();
        return "ok";
    }

    public String run(@RequestParam String cmd) throws Exception {
        Runtime r = Runtime.getRuntime();
        r.exec(cmd);
        return "ok";
    }

    public String fetch(@RequestParam String u) throws Exception {
        java.net.URL url = new java.net.URL(u);
        return url.getHost();
    }

    public String go(@RequestParam String next) {
        return "redirect:" + next;
    }

    public String lang(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) {
        String lang = request.getParameter("l");
        response.addCookie(new javax.servlet.http.Cookie("l", lang));
        return "ok";
    }

    public String hash() throws Exception {
        String algo = "MD5";
        java.security.MessageDigest.getInstance(algo);
        return "ok";
    }

    public String cipher(java.util.Properties props) throws Exception {
        javax.crypto.Cipher.getInstance(props.getProperty("alg", "DES/ECB/PKCS5Padding"));
        return "ok";
    }

    public String connect(String url, String user) throws Exception {
        java.sql.DriverManager.getConnection(url, user, "s3cret");
        return "ok";
    }

    public String iv() {
        new javax.crypto.spec.IvParameterSpec(IV.getBytes());
        return "ok";
    }
}
