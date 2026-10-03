<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>[=comment] 수정</title>
</head>
<body>
<h1>[=comment] 수정</h1>
<form:form modelAttribute="[=name]VO" action="${pageContext.request.contextPath}[=urlBase]/update[=Name].do" method="post">
<table>
    <caption>[=comment] 수정</caption>
    <tbody>
<#list fields as f>
        <tr>
            <th scope="row"><label for="[=f.name]">[=f.comment]</label></th>
            <td><form:input path="[=f.name]" id="[=f.name]" title="[=f.comment]"<#if f.pk> readonly="true"</#if><#if f.string && (f.length > 0)> maxlength="[=f.length?c]"</#if>/> <form:errors path="[=f.name]"/></td>
        </tr>
</#list>
    </tbody>
</table>
    <button type="submit">수정</button>
</form:form>
<a href="<c:url value='[=urlBase]/select[=Name]List.do'/>">목록</a>
</body>
</html>
