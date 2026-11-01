<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="ui" uri="http://egovframework.gov/ctl/ui" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>게시물 목록</title>
<script>
function fn_select_page(pageNo) {
    document.listForm.pageIndex.value = pageNo;
    document.listForm.submit();
}
</script>
</head>
<body>
<h1>게시물 목록</h1>
<form:form modelAttribute="searchVO" name="listForm" action="${pageContext.request.contextPath}/comtnbbs/selectComtnbbsList.do" method="post">
    <label for="nttSj">게시물 제목</label>
    <form:input path="nttSj" id="nttSj" title="게시물 제목"/>
    <label for="useAt">사용 여부</label>
    <form:select path="useAt" id="useAt" title="사용 여부"><form:option value="">전체</form:option><form:option value="Y">Y</form:option><form:option value="N">N</form:option></form:select>
    <label for="ntceBgndeFrom">게시 시작일 시작</label>
    <form:input path="ntceBgndeFrom" id="ntceBgndeFrom" type="date" title="게시 시작일 시작"/> ~
    <label for="ntceBgndeTo">게시 시작일 끝</label>
    <form:input path="ntceBgndeTo" id="ntceBgndeTo" type="date" title="게시 시작일 끝"/>
    <label for="sntncSe">문장 구분</label>
    <form:select path="sntncSe" id="sntncSe" title="문장 구분"><form:option value="">전체</form:option><form:option value="A">A</form:option><form:option value="B">B</form:option><form:option value="C">C</form:option></form:select>
    <input type="hidden" name="pageIndex" value="<c:out value='${searchVO.pageIndex}'/>"/>
    <button type="submit">조회</button>
</form:form>
<table>
    <caption>게시물 목록</caption>
    <thead>
        <tr>
            <th scope="col">게시물 ID</th>
            <th scope="col">게시판 ID</th>
            <th scope="col">게시물 제목</th>
            <th scope="col">게시물 내용</th>
            <th scope="col">사용 여부</th>
            <th scope="col">게시 시작일</th>
            <th scope="col">조회 수</th>
            <th scope="col">문장 구분</th>
        </tr>
    </thead>
    <tbody>
    <c:forEach items="${resultList}" var="result">
        <tr>
            <td><a href="<c:url value='/comtnbbs/selectComtnbbsDetail.do'><c:param name="nttId" value="${result.nttId}"/><c:param name="bbsId" value="${result.bbsId}"/></c:url>"><c:out value="${result.nttId}"/></a></td>
            <td><c:out value="${result.bbsId}"/></td>
            <td><c:out value="${result.nttSj}"/></td>
            <td><c:out value="${result.nttCn}"/></td>
            <td><c:out value="${result.useAt}"/></td>
            <td><c:out value="${result.ntceBgnde}"/></td>
            <td><c:out value="${result.rdcnt}"/></td>
            <td><c:out value="${result.sntncSe}"/></td>
        </tr>
    </c:forEach>
    </tbody>
</table>
<ui:pagination paginationInfo="${paginationInfo}" type="text" jsFunction="fn_select_page"/>
<a href="<c:url value='/comtnbbs/insertComtnbbsView.do'/>">등록</a>
</body>
</html>
