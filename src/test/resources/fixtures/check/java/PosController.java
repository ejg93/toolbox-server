package egov.pos;

import java.util.List;
import java.util.*;
import egov.pos.service.UserDAO;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class PosController {
    private UserDAO userDAO;

    @RequestMapping("/user/list.do")
    public String list() {
        return "x";
    }
}
