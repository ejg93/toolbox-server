<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>게시물 수정</title>
</head>
<body>
<h1>게시물 수정</h1>
<form:form modelAttribute="comtnbbsVO" action="${pageContext.request.contextPath}/comtnbbs/updateComtnbbs.do" method="post">
<table>
    <caption>게시물 수정</caption>
    <tbody>
        <tr>
            <th scope="row"><label for="nttId">게시물 ID</label></th>
            <td><form:input path="nttId" id="nttId" title="게시물 ID" readonly="true"/> <form:errors path="nttId"/></td>
        </tr>
        <tr>
            <th scope="row"><label for="bbsId">게시판 ID</label></th>
            <td><form:input path="bbsId" id="bbsId" title="게시판 ID" readonly="true" maxlength="20"/> <form:errors path="bbsId"/></td>
        </tr>
        <tr>
            <th scope="row"><label for="nttSj">게시물 제목</label></th>
            <td><form:input path="nttSj" id="nttSj" title="게시물 제목" maxlength="2000" required="required"/> <form:errors path="nttSj"/></td>
        </tr>
        <tr>
            <th scope="row"><label for="nttCn">게시물 내용</label></th>
            <td><form:textarea path="nttCn" id="nttCn" title="게시물 내용" rows="5"/> <form:errors path="nttCn"/></td>
        </tr>
        <tr>
            <th scope="row">사용 여부</th>
            <td><form:radiobutton path="useAt" id="useAt_Y" value="Y"/><label for="useAt_Y">Y</label> <form:radiobutton path="useAt" id="useAt_N" value="N"/><label for="useAt_N">N</label> <form:errors path="useAt"/></td>
        </tr>
        <tr>
            <th scope="row"><label for="ntceBgnde">게시 시작일</label></th>
            <td><form:input path="ntceBgnde" id="ntceBgnde" title="게시 시작일" type="datetime-local"/> <form:errors path="ntceBgnde"/></td>
        </tr>
        <tr>
            <th scope="row"><label for="rdcnt">조회 수</label></th>
            <td><form:input path="rdcnt" id="rdcnt" title="조회 수" type="number"/> <form:errors path="rdcnt"/></td>
        </tr>
        <tr>
            <th scope="row"><label for="sntncSe">문장 구분</label></th>
            <td><form:select path="sntncSe" id="sntncSe" title="문장 구분"><form:option value="">선택</form:option><form:option value="A">A</form:option><form:option value="B">B</form:option><form:option value="C">C</form:option></form:select> <form:errors path="sntncSe"/></td>
        </tr>
    </tbody>
</table>
    <button type="submit">수정</button>
</form:form>
<a href="<c:url value='/comtnbbs/selectComtnbbsList.do'/>">목록</a>
</body>
</html>
