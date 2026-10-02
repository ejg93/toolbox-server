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
}
