package kr.go.hr.comtnbbs.web;

import java.util.List;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import egovframework.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;
import kr.go.hr.comtnbbs.service.ComtnbbsService;
import kr.go.hr.comtnbbs.service.ComtnbbsVO;

/**
 * 게시물 컨트롤러
 *
 * <pre>
 * << 개정이력(Modification Information) >>
 *
 *   수정일      수정자          수정내용
 *  -------    --------    ---------------------------
 *   (생성)     toolbox      Table → Spring 소스 생성(egovframework.rte)
 * </pre>
 */
@Controller
public class ComtnbbsController {

    @Resource(name = "comtnbbsService")
    private ComtnbbsService comtnbbsService;

    /**
     * 게시물 목록을 조회한다.
     */
    @RequestMapping("/comtnbbs/selectComtnbbsList.do")
    public String selectComtnbbsList(@ModelAttribute("searchVO") ComtnbbsVO searchVO, ModelMap model) throws Exception {
        PaginationInfo paginationInfo = new PaginationInfo();
        paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
        paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
        paginationInfo.setPageSize(searchVO.getPageSize());
        searchVO.setFirstIndex(paginationInfo.getFirstRecordIndex());
        searchVO.setLastIndex(paginationInfo.getLastRecordIndex());
        searchVO.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());

        List<ComtnbbsVO> resultList = comtnbbsService.selectComtnbbsList(searchVO);
        model.addAttribute("resultList", resultList);
        int totCnt = comtnbbsService.selectComtnbbsListTotCnt(searchVO);
        paginationInfo.setTotalRecordCount(totCnt);
        model.addAttribute("paginationInfo", paginationInfo);
        return "kr/go/hr/comtnbbs/ComtnbbsList";
    }

    /**
     * 게시물 상세를 조회한다.
     */
    @RequestMapping("/comtnbbs/selectComtnbbsDetail.do")
    public String selectComtnbbsDetail(@ModelAttribute("searchVO") ComtnbbsVO searchVO, ModelMap model) throws Exception {
        model.addAttribute("result", comtnbbsService.selectComtnbbsDetail(searchVO));
        return "kr/go/hr/comtnbbs/ComtnbbsDetail";
    }

    /**
     * 게시물 등록 화면을 연다.
     */
    @RequestMapping("/comtnbbs/insertComtnbbsView.do")
    public String insertComtnbbsView(@ModelAttribute("searchVO") ComtnbbsVO searchVO, ModelMap model) throws Exception {
        model.addAttribute("comtnbbsVO", new ComtnbbsVO());
        return "kr/go/hr/comtnbbs/ComtnbbsRegist";
    }

    /**
     * 게시물 을(를) 등록한다.
     */
    @RequestMapping("/comtnbbs/insertComtnbbs.do")
    public String insertComtnbbs(@ModelAttribute("searchVO") ComtnbbsVO searchVO,
            @ModelAttribute("comtnbbsVO") ComtnbbsVO comtnbbsVO, BindingResult bindingResult, ModelMap model, HttpServletRequest request) throws Exception {
        if (bindingResult.hasErrors()) {
            return "kr/go/hr/comtnbbs/ComtnbbsRegist";
        }
        comtnbbsVO.setFrstRegisterId(loginId(request));
        comtnbbsService.insertComtnbbs(comtnbbsVO);
        return "forward:/comtnbbs/selectComtnbbsList.do";
    }

    /**
     * 게시물 수정 화면을 연다.
     */
    @RequestMapping("/comtnbbs/updateComtnbbsView.do")
    public String updateComtnbbsView(@ModelAttribute("searchVO") ComtnbbsVO searchVO, ModelMap model) throws Exception {
        model.addAttribute("comtnbbsVO", comtnbbsService.selectComtnbbsDetail(searchVO));
        return "kr/go/hr/comtnbbs/ComtnbbsUpdt";
    }

    /**
     * 게시물 을(를) 수정한다.
     */
    @RequestMapping("/comtnbbs/updateComtnbbs.do")
    public String updateComtnbbs(@ModelAttribute("searchVO") ComtnbbsVO searchVO,
            @ModelAttribute("comtnbbsVO") ComtnbbsVO comtnbbsVO, BindingResult bindingResult, ModelMap model, HttpServletRequest request) throws Exception {
        if (bindingResult.hasErrors()) {
            return "kr/go/hr/comtnbbs/ComtnbbsUpdt";
        }
        comtnbbsVO.setLastUpdusrId(loginId(request));
        comtnbbsService.updateComtnbbs(comtnbbsVO);
        return "forward:/comtnbbs/selectComtnbbsList.do";
    }

    /**
     * 게시물 을(를) 삭제한다.
     */
    @RequestMapping("/comtnbbs/deleteComtnbbs.do")
    public String deleteComtnbbs(@ModelAttribute("searchVO") ComtnbbsVO searchVO, ModelMap model) throws Exception {
        comtnbbsService.deleteComtnbbs(searchVO);
        return "forward:/comtnbbs/selectComtnbbsList.do";
    }

    /**
     * 로그인 사용자 ID — 세션 「loginVO」 의 uniqId, 없으면 Principal 이름, 그래도 없으면 null(프로필 generator.audit.userSession).
     */
    private String loginId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute("loginVO");
        if (user != null) {
            Object id = new BeanWrapperImpl(user).getPropertyValue("uniqId");
            return id == null ? null : id.toString();
        }
        return request.getUserPrincipal() == null ? null : request.getUserPrincipal().getName();
    }
}
