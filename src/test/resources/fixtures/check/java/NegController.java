package egov.neg;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.Map;
import egov.neg.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 이름만 javadoc 에 나오는 import 도 쓰인 것으로 본다 — {@link Map} */
@Controller
@RequestMapping("/neg")
public class NegController {
    private UserService userService;

    @GetMapping("/item.do")
    public List<String> get() {
        return emptyList();
    }

    @PostMapping("/item.do")
    public String post() {
        return "x";
    }
}
