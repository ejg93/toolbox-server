package [=packageName].web;

import java.util.List;
import [=vars.ee].annotation.Resource;
<#if vars.valid == "true">
import [=vars.ee].validation.Valid;
</#if>
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import [=vars.rte].ptl.mvc.tags.ui.pagination.PaginationInfo;
import [=packageName].service.[=Name]Service;
import [=packageName].service.[=Name]VO;

<#assign what = "컨트롤러"><#include "header.ftl">
@Controller
public class [=Name]Controller {

    @Resource(name = "[=name]Service")
    private [=Name]Service [=name]Service;

    /**
     * [=comment] 목록을 조회한다.
     */
    @RequestMapping("[=urlBase]/select[=Name]List.do")
    public String select[=Name]List(@ModelAttribute("searchVO") [=Name]VO searchVO, ModelMap model) throws Exception {
        PaginationInfo paginationInfo = new PaginationInfo();
        paginationInfo.setCurrentPageNo(searchVO.getPageIndex());
        paginationInfo.setRecordCountPerPage(searchVO.getPageUnit());
        paginationInfo.setPageSize(searchVO.getPageSize());
        searchVO.setFirstIndex(paginationInfo.getFirstRecordIndex());
        searchVO.setLastIndex(paginationInfo.getLastRecordIndex());
        searchVO.setRecordCountPerPage(paginationInfo.getRecordCountPerPage());

        List<[=Name]VO> resultList = [=name]Service.select[=Name]List(searchVO);
        model.addAttribute("resultList", resultList);
        int totCnt = [=name]Service.select[=Name]ListTotCnt(searchVO);
        paginationInfo.setTotalRecordCount(totCnt);
        model.addAttribute("paginationInfo", paginationInfo);
        return "[=viewBase]/[=Name]List";
    }

    /**
     * [=comment] 상세를 조회한다.
     */
    @RequestMapping("[=urlBase]/select[=Name]Detail.do")
    public String select[=Name]Detail(@ModelAttribute("searchVO") [=Name]VO searchVO, ModelMap model) throws Exception {
        model.addAttribute("result", [=name]Service.select[=Name]Detail(searchVO));
        return "[=viewBase]/[=Name]Detail";
    }

    /**
     * [=comment] 등록 화면을 연다.
     */
    @RequestMapping("[=urlBase]/insert[=Name]View.do")
    public String insert[=Name]View(@ModelAttribute("searchVO") [=Name]VO searchVO, ModelMap model) throws Exception {
        model.addAttribute("[=name]VO", new [=Name]VO());
        return "[=viewBase]/[=Name]Regist";
    }

    /**
     * [=comment] 을(를) 등록한다.
     */
    @RequestMapping("[=urlBase]/insert[=Name].do")
    public String insert[=Name](@ModelAttribute("searchVO") [=Name]VO searchVO,
            <#if vars.valid == "true">@Valid </#if>@ModelAttribute("[=name]VO") [=Name]VO [=name]VO, BindingResult bindingResult, ModelMap model) throws Exception {
        if (bindingResult.hasErrors()) {
            return "[=viewBase]/[=Name]Regist";
        }
        [=name]Service.insert[=Name]([=name]VO);
        return "forward:[=urlBase]/select[=Name]List.do";
    }

    /**
     * [=comment] 수정 화면을 연다.
     */
    @RequestMapping("[=urlBase]/update[=Name]View.do")
    public String update[=Name]View(@ModelAttribute("searchVO") [=Name]VO searchVO, ModelMap model) throws Exception {
        model.addAttribute("[=name]VO", [=name]Service.select[=Name]Detail(searchVO));
        return "[=viewBase]/[=Name]Updt";
    }

    /**
     * [=comment] 을(를) 수정한다.
     */
    @RequestMapping("[=urlBase]/update[=Name].do")
    public String update[=Name](@ModelAttribute("searchVO") [=Name]VO searchVO,
            <#if vars.valid == "true">@Valid </#if>@ModelAttribute("[=name]VO") [=Name]VO [=name]VO, BindingResult bindingResult, ModelMap model) throws Exception {
        if (bindingResult.hasErrors()) {
            return "[=viewBase]/[=Name]Updt";
        }
        [=name]Service.update[=Name]([=name]VO);
        return "forward:[=urlBase]/select[=Name]List.do";
    }

    /**
     * [=comment] 을(를) 삭제한다.
     */
    @RequestMapping("[=urlBase]/delete[=Name].do")
    public String delete[=Name](@ModelAttribute("searchVO") [=Name]VO searchVO, ModelMap model) throws Exception {
        [=name]Service.delete[=Name](searchVO);
        return "forward:[=urlBase]/select[=Name]List.do";
    }
}
