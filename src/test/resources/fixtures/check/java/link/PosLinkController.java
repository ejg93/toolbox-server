package egov.pos.link;

/** 설계 43 — 요청 값이 서비스 인터페이스 → 구현 · 정적 필드 · 같은 클래스 도우미를 거쳐 싱크에 닿는다 */
@Controller
public class PosLinkController {
    private PosLinkService svc;

    @GetMapping("/link")
    public String down(@RequestParam String name) {
        svc.download(name);
        svc.open("hunter2");
        PosLinkHolder.last = name;
        return helper(name);
    }

    private String helper(String v) {
        new java.io.File(v);
        return "ok";
    }
}
