package egov.neg.link;

/** 설계 43 — 같은 사슬에 상수·정화 값만 */
@Controller
public class NegLinkController {
    private NegLinkServiceImpl svc;

    @GetMapping("/neg-link")
    public String down(@RequestParam String id) {
        int n = Integer.parseInt(id);
        svc.download("fixed");
        svc.page(n);
        svc.open(id);
        return "ok";
    }

    public void fail(javax.servlet.http.HttpServletResponse response) throws Exception {
        try { svc.page(1); } catch (Exception e) { log.error("x", e); response.getWriter().println("fail"); }
    }
}
