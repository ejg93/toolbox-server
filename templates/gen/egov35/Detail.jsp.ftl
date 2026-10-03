<%@ page contentType="text/html; charset=[=encoding!'UTF-8']" pageEncoding="[=encoding!'UTF-8']" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="[=encoding!'UTF-8']">
<title>[=comment] 상세</title>
</head>
<body>
<h1>[=comment] 상세</h1>
<table>
    <caption>[=comment] 상세</caption>
    <tbody>
<#list fields as f>
        <tr>
            <th scope="row">[=f.comment]</th>
            <td><c:out value="${result.[=f.name]}"/></td>
        </tr>
</#list>
    </tbody>
</table>
<form name="detailForm" method="post" action="<c:url value='[=urlBase]/update[=Name]View.do'/>">
<#list pk as p>
    <input type="hidden" name="[=p.name]" value="<c:out value='${result.[=p.name]}'/>"/>
</#list>
    <button type="submit">수정</button>
    <button type="submit" formaction="<c:url value='[=urlBase]/delete[=Name].do'/>">삭제</button>
</form>
<a href="<c:url value='[=urlBase]/select[=Name]List.do'/>">목록</a>
</body>
</html>
