/**
 * 수정일 수정자 수정내용
 */
package a;

public class SecNeg {
    String passwordPolicy = PolicyHolder.get();

    void q(org.springframework.ui.Model model, String id) throws Exception {
        String sql = "SELECT * FROM users WHERE id = ?";
        // String s = "SELECT " + id;
        java.io.File f = new java.io.File(BASE, safeName(id));
        try { q(model, id); } catch (Exception e) { LOG.warn("실패", e); model.addAttribute("err", "처리 실패"); }
    }

    // 5-13 KISA 2차 — 안전한 꼴. 주석 안은 안 센다: Runtime.getRuntime().exec(cmd); new Random();
    void sec2(javax.servlet.http.HttpServletResponse response) throws Exception {
        SecureRandom r = new SecureRandom();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        response.sendRedirect("/fixed.do");
        URL u = new URL("https://fixed.example/api");
        response.addCookie(new Cookie("a", "b"));
        String key = KeyStoreHolder.load();
    }
}
