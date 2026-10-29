package egov.neg;

/** 설계 42 — 같은 싱크에 정화·상수 값만, 매개변수는 요청 값이 아니다 */
public class NegTaint {
    private static final String BASE = "/data/";

    public String file(javax.servlet.http.HttpServletRequest request) throws Exception {
        int id = Integer.parseInt(request.getParameter("id"));
        new java.io.FileInputStream(BASE + id).close();
        return "ok";
    }

    public String run(String cmd) throws Exception {
        Runtime.getRuntime().exec(cmd);
        return "ok";
    }

    public String go() {
        return "redirect:" + BASE;
    }

    public String lang(javax.servlet.http.HttpServletResponse response) {
        response.addCookie(new javax.servlet.http.Cookie("l", "ko"));
        return "ok";
    }

    public String hash() throws Exception {
        String algo = "SHA-256";
        java.security.MessageDigest.getInstance(algo);
        return "ok";
    }

    public String connect(String url, String user, String pw) throws Exception {
        java.sql.DriverManager.getConnection(url, user, pw);
        return "ok";
    }
}
