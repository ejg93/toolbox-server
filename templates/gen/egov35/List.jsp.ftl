<%@ page contentType="text/html; charset=[=encoding!'UTF-8']" pageEncoding="[=encoding!'UTF-8']" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="ui" uri="http://egovframework.gov/ctl/ui" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="[=encoding!'UTF-8']">
<title>[=comment] 목록</title>
<script>
function fn_select_page(pageNo) {
    document.listForm.pageIndex.value = pageNo;
    document.listForm.submit();
}
</script>
</head>
<body>
<h1>[=comment] 목록</h1>
<form:form modelAttribute="searchVO" name="listForm" action="${pageContext.request.contextPath}[=urlBase]/select[=Name]List.do" method="post">
<#if searchField??>
    <label for="searchKeyword">[=searchField.comment]</label>
    <form:input path="searchKeyword" id="searchKeyword" title="[=searchField.comment]"/>
</#if>
    <input type="hidden" name="pageIndex" value="<c:out value='${searchVO.pageIndex}'/>"/>
    <button type="submit">조회</button>
</form:form>
<table>
    <caption>[=comment] 목록</caption>
    <thead>
        <tr>
<#list fields as f>
            <th scope="col">[=f.comment]</th>
</#list>
        </tr>
    </thead>
    <tbody>
    <c:forEach items="${resultList}" var="result">
        <tr>
<#list fields as f>
<#if f?is_first>
            <td><a href="<c:url value='[=urlBase]/select[=Name]Detail.do'><#list pk as p><c:param name="[=p.name]" value="${result.[=p.name]}"/></#list></c:url>"><c:out value="${result.[=f.name]}"/></a></td>
<#else>
            <td><c:out value="${result.[=f.name]}"/></td>
</#if>
</#list>
        </tr>
    </c:forEach>
    </tbody>
</table>
<ui:pagination paginationInfo="${paginationInfo}" type="text" jsFunction="fn_select_page"/>
<a href="<c:url value='[=urlBase]/insert[=Name]View.do'/>">등록</a>
</body>
</html>
