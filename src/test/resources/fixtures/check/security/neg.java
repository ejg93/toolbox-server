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
    void sec2(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response) throws Exception {
        SecureRandom r = new SecureRandom();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        response.sendRedirect("/fixed.do");
        URL u = new URL("https://fixed.example/api");
        response.addCookie(new Cookie("a", "b"));
        String key = KeyStoreHolder.load();
        // 예) 900101-1234567 · 010-1234-5678 — 주석은 안 센다
        String day = "2026-10-03";
        LOGGER.debug("{}.{}", className, methodName);
        log.error("Error creating password hash: {}", e2.getMessage());
        // 5-44a — 이어붙이기의 "password=" 는 값이 아니고, 설명·예외 글 속 SQL 낱말은 쿼리가 아니다
        String shown = "user=" + day + ", password=" + (key == null ? "null" : "****");
        // 설계 42 — 정화·상수 값은 오염이 아니다
        int n = Integer.parseInt(request.getParameter("n"));
        new java.io.File("f" + n);
        String fixed = "/home.do";
        response.sendRedirect(fixed);
        new java.net.URL(BASE + "/api");
        conn.createStatement().executeQuery("SELECT 1");
        response.getWriter().print("ok");
        ctx.search("ou=people", "(uid=admin)", sc);
        cookie.setSecure(true);
        request.getSession().setAttribute("u", userId);
        throw new IllegalArgumentException("method 는 GET·POST·PUT·DELETE: " + shown);
    }
}
