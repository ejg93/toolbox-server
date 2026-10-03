package sample.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import sample.service.BoardService;
import sample.service.OtherService;

@Controller
@RequestMapping("/other")
public class OtherController {

	@Autowired
	private BoardService anyBoard;

	@Autowired
	private OtherService otherService;

	/** 다른 컨트롤러의 목록。 둘째 */
	@GetMapping("/list.do")
	public String list() throws Exception {
		anyBoard.selectList(null);
		return "sample/other/List";
	}

	@RequestMapping(value = {"/a.do", "/b.do"}, method = RequestMethod.POST)
	public String two() throws Exception {
		otherService.run();
		return returnUrl();
	}

	@RequestMapping("/bbs/list.do")
	public String sameUrlAsBoard() {
		return "sample/other/SameUrl";
	}

	private String returnUrl() {
		return "x";
	}
}
