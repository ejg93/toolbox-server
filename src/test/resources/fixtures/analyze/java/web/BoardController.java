package sample.web;

import javax.annotation.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import sample.service.BoardService;
import sample.service.BoardVO;

@Controller
public class BoardController {

	@Resource(name = "BoardService")
	private BoardService boardService;

	@Resource(name = "propertiesService")
	protected EgovPropertyService propertyService;

	/**
	 * 게시물 목록을 조회한다. 두 번째 문장은 안 싣는다.
	 *
	 * @param vo 조회 조건
	 * @return 목록 화면
	 */
	@RequestMapping("/bbs/list.do")
	public String list(BoardVO vo) throws Exception {
		boardService.selectList(vo);
		propertyService.getInt("pageUnit");
		return "sample/bbs/BoardList";
	}

	@PostMapping(value = "/bbs/add.do", params = "!cmd")
	public String addView(BoardVO vo) {
		return "sample/bbs/BoardRegist";
	}

	/**
	 * @param vo 등록 값
	 */
	@PostMapping(value = "/bbs/add.do", params = "cmd=Regist")
	public String add(BoardVO vo) throws Exception {
		boardService.insert(vo);
		return "forward:/bbs/list.do";
	}

	/** 게시물 상세 — 줄바꿈 앞까지
	 * 둘째 줄 */
	@RequestMapping("/bbs/detail.do")
	public String detail(BoardVO vo, boolean ok) throws Exception {
		String sLocationUrl = null;
		if (ok) {
			sLocationUrl = "sample/bbs/BoardDetail";
		} else {
			sLocationUrl = "redirect:/bbs/list.do?x=1";
		}
		sLocationUrl = sLocationUrl + "&y=" + vo.getY();
		helper(vo);
		return sLocationUrl;
	}

	private void helper(BoardVO vo) throws Exception {
		this.boardService.selectDetail(vo);
	}

	@RequestMapping("/bbs/json.do")
	@ResponseBody
	public BoardVO json(BoardVO vo) throws Exception {
		return boardService.selectDetail(vo);
	}

	@RequestMapping("/bbs/mav.do")
	public ModelAndView mav() {
		ModelAndView mav = new ModelAndView("jsonView");
		mav.setViewName("sample/bbs/Other");
		return mav;
	}

	@RequestMapping("/bbs/xml.do")
	public ModelAndView xml() {
		return new ModelAndView(new AjaxXmlView());
	}

	@RequestMapping("/bbs/redirect.do")
	public String redirect(String keyword) {
		return "redirect:/bbs/list.do?searchKeyword=" + keyword;
	}

	public String notAProgram() {
		return "x";
	}
}
