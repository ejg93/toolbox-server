package [=packageName].service;

import java.io.Serializable;
<#list imports as i>
import [=i];
</#list>
<#assign hasSize = false>
<#list fields as f><#if f.string && (f.length > 0)><#assign hasSize = true></#if></#list>
<#if vars.valid == "true" && hasSize>
import [=vars.ee].validation.constraints.Size;
</#if>

<#assign what = "VO(검색·페이징 포함)"><#include "header.ftl">
public class [=Name]VO implements Serializable {

    private static final long serialVersionUID = 1L;
<#list fields as f>

    /** [=f.comment] */
<#if vars.valid == "true" && f.string && (f.length > 0)>
    @Size(max = [=f.length?c])
</#if>
    private [=f.javaType] [=f.name];
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
<#list [["String", "searchCondition", "SearchCondition"], ["String", "searchKeyword", "SearchKeyword"], ["int", "pageIndex", "PageIndex"], ["int", "pageUnit", "PageUnit"], ["int", "pageSize", "PageSize"], ["int", "firstIndex", "FirstIndex"], ["int", "lastIndex", "LastIndex"], ["int", "recordCountPerPage", "RecordCountPerPage"]] as p>

    public [=p[0]] get[=p[2]]() {
        return [=p[1]];
    }

    public void set[=p[2]]([=p[0]] [=p[1]]) {
        this.[=p[1]] = [=p[1]];
    }
</#list>
}
