/**
 * 수정일 수정자 수정내용
 */
package a;

public class Sec {
    String dbPassword = "p@ssw0rd!";

    void q(javax.servlet.http.HttpServletRequest request, org.springframework.ui.Model model, String id) throws Exception {
        String sql = "SELECT * FROM users WHERE id = '" + id + "'";
        java.io.File f = new java.io.File(request.getParameter("path"));
        try { q(request, model, id); } catch (Exception e) { model.addAttribute("err", e.getMessage()); }
    }

    // 5-13 KISA 2차 — 한 줄에 한 규칙
    void sec2(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response, Upload file) throws Exception {
        Runtime.getRuntime().exec(request.getParameter("cmd"));
        Object o = new ObjectInputStream(request.getInputStream()).readObject();
        new ScriptEngineManager().getEngineByName("js");
        URL u = new URL(request.getParameter("url"));
        response.sendRedirect(request.getParameter("next"));
        response.setHeader("X-Name", request.getParameter("name"));
        MessageDigest md = MessageDigest.getInstance("MD5");
        Cipher c = Cipher.getInstance("DES/ECB/PKCS5Padding");
        int r = new Random().nextInt();
        SecretKeySpec k = new SecretKeySpec("0123456789abcdef".getBytes(), "AES");
        String name = file.getOriginalFilename();
        Cipher d = Cipher.getInstance("AES");
        String rrn = "900101-1234567";
        String card = "1234-5678-9012-3456";
        String phone = "010-1234-5678";
        log.debug("pw={}", password);
    }
}
