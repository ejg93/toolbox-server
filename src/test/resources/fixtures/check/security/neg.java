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
}
