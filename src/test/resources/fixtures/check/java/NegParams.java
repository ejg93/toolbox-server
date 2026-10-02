package egov.neg;

import egovframework.rte.fdl.cmmn.EgovAbstractServiceImpl;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;

/** params 가 다르면 같은 URL 이라도 다른 매핑 · @Service 이름이 인터페이스 이름 그대로(eGov 관례) */
@Controller
public class NegParamsController {
    @PostMapping(value = "/neg/add.do", params = "!cmd")
    public String form() {
        return "form";
    }

    @PostMapping(value = "/neg/add.do", params = "cmd=Regist")
    public String add() {
        return "add";
    }
}

@Service("EgovCmmUseService")
class EgovCmmUseServiceImpl extends EgovAbstractServiceImpl implements EgovCmmUseService {
}
