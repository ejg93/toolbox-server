package [=packageName].service;

import java.io.Serializable;
<#list imports as i>
import [=i];
</#list>

<#assign what = "VO(검색·페이징 포함)"><#include "header.ftl">
public class [=Name]VO implements Serializable {

    private static final long serialVersionUID = 1L;
<#list fields as f>

    /** [=f.doc] */
<#list f.annotations as a>
    [=a]
</#list>
    private [=f.javaType] [=f.name];
</#list>
<#list search as f>
<#if f.query == "BETWEEN">

    /** [=f.comment] 검색 시작(yyyy-MM-dd) */
    private String [=f.name]From;

    /** [=f.comment] 검색 끝(yyyy-MM-dd, 그날 포함) */
    private String [=f.name]To;
</#if>
</#list>

    /** 검색 조건 */
    private String searchCondition = "";

    /** 검색어 */
    private String searchKeyword = "";

    /** 현재 페이지 */
    private int pageIndex = 1;

    /** 페이지당 건수 */
    private int pageUnit = 10;

    /** 페이지 목록 크기 */
    private int pageSize = 10;

    /** 첫 행 위치 */
    private int firstIndex = 1;

    /** 끝 행 위치 */
    private int lastIndex = 1;

    /** 페이지당 행 수 */
    private int recordCountPerPage = 10;
<#list fields as f>

    public [=f.javaType] get[=f.Name]() {
        return [=f.name];
    }

    public void set[=f.Name]([=f.javaType] [=f.name]) {
        this.[=f.name] = [=f.name];
    }
</#list>
<#list search as f>
<#if f.query == "BETWEEN">
<#list ["From", "To"] as e>

    public String get[=f.Name][=e]() {
        return [=f.name][=e];
    }

    public void set[=f.Name][=e](String [=f.name][=e]) {
        this.[=f.name][=e] = [=f.name][=e];
    }
</#list>
</#if>
</#list>
<#list [["String", "searchCondition", "SearchCondition"], ["String", "searchKeyword", "SearchKeyword"], ["int", "pageIndex", "PageIndex"], ["int", "pageUnit", "PageUnit"], ["int", "pageSize", "PageSize"], ["int", "firstIndex", "FirstIndex"], ["int", "lastIndex", "LastIndex"], ["int", "recordCountPerPage", "RecordCountPerPage"]] as p>

    public [=p[0]] get[=p[2]]() {
        return [=p[1]];
    }

    public void set[=p[2]]([=p[0]] [=p[1]]) {
        this.[=p[1]] = [=p[1]];
    }
</#list>
}
