package [=packageName].web;

import java.util.List;
import [=vars.ee].annotation.Resource;
<#assign hasUser = insertUsers?has_content || updateUsers?has_content>
<#if hasUser>
import [=vars.ee].servlet.http.HttpServletRequest;
<#if sessionKey?has_content>
import [=vars.ee].servlet.http.HttpSession;
</#if>
</#if>
<#if vars.valid == "true">
import [=vars.ee].validation.Valid;
</#if>
<#if hasUser && sessionKey?has_content && sessionProp?has_content>
import org.springframework.beans.BeanWrapperImpl;
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
            <#if vars.valid == "true">@Valid </#if>@ModelAttribute("[=name]VO") [=Name]VO [=name]VO, BindingResult bindingResult, ModelMap model<#if insertUsers?has_content>, HttpServletRequest request</#if>) throws Exception {
        if (bindingResult.hasErrors()) {
            return "[=viewBase]/[=Name]Regist";
        }
<#list insertUsers as f>
        [=name]VO.set[=f.Name](loginId(request));
</#list>
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
            <#if vars.valid == "true">@Valid </#if>@ModelAttribute("[=name]VO") [=Name]VO [=name]VO, BindingResult bindingResult, ModelMap model<#if updateUsers?has_content>, HttpServletRequest request</#if>) throws Exception {
        if (bindingResult.hasErrors()) {
            return "[=viewBase]/[=Name]Updt";
        }
<#list updateUsers as f>
        [=name]VO.set[=f.Name](loginId(request));
</#list>
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
<#if hasUser>

    /**
     * 로그인 사용자 ID — <#if sessionKey?has_content>세션 「[=sessionKey]」<#if sessionProp?has_content> 의 [=sessionProp]</#if>, 없으면 </#if>Principal 이름, 그래도 없으면 null(프로필 generator.audit.userSession).
     */
    private String loginId(HttpServletRequest request) {
<#if sessionKey?has_content>
        HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute("[=sessionKey]");
        if (user != null) {
<#if sessionProp?has_content>
            Object id = new BeanWrapperImpl(user).getPropertyValue("[=sessionProp]");
            return id == null ? null : id.toString();
<#else>
            return user.toString();
</#if>
        }
</#if>
        return request.getUserPrincipal() == null ? null : request.getUserPrincipal().getName();
    }
</#if>
}
