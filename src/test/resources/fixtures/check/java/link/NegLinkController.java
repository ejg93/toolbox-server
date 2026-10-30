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
        return "ok";
    }
}
