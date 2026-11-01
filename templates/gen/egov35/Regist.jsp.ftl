<%@ page contentType="text/html; charset=[=encoding!'UTF-8']" pageEncoding="[=encoding!'UTF-8']" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<#include "input.ftl">
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="[=encoding!'UTF-8']">
<title>[=comment] 등록</title>
</head>
<body>
<h1>[=comment] 등록</h1>
<form:form modelAttribute="[=name]VO" action="${pageContext.request.contextPath}[=urlBase]/insert[=Name].do" method="post">
<table>
    <caption>[=comment] 등록</caption>
    <tbody>
<#list insertFields as f>
        <tr>
            <th scope="row"><@head f/></th>
            <td><@input f/> <form:errors path="[=f.name]"/></td>
        </tr>
</#list>
    </tbody>
</table>
    <button type="submit">등록</button>
</form:form>
<a href="<c:url value='[=urlBase]/select[=Name]List.do'/>">목록</a>
</body>
</html>
