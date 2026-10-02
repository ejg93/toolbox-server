package egov.pos;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/user")
public class UserCtrl {
    @RequestMapping(value = "/list.do")
    public String list() {
        return "x";
    }
}
