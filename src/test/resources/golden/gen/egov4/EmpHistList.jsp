<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="ui" uri="http://egovframework.gov/ctl/ui" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>사원 이력 목록</title>
<script>
function fn_select_page(pageNo) {
    document.listForm.pageIndex.value = pageNo;
    document.listForm.submit();
}
</script>
</head>
<body>
<h1>사원 이력 목록</h1>
<form:form modelAttribute="searchVO" name="listForm" action="${pageContext.request.contextPath}/emphist/selectEmpHistList.do" method="post">
    <label for="searchKeyword">직무</label>
    <form:input path="searchKeyword" id="searchKeyword" title="직무"/>
    <input type="hidden" name="pageIndex" value="<c:out value='${searchVO.pageIndex}'/>"/>
    <button type="submit">조회</button>
</form:form>
<table>
    <caption>사원 이력 목록</caption>
    <thead>
        <tr>
            <th scope="col">사원 번호</th>
            <th scope="col">시작일</th>
            <th scope="col">직무</th>
            <th scope="col">부서명</th>
            <th scope="col">급여</th>
            <th scope="col">등급</th>
            <th scope="col">NOTE</th>
        </tr>
    </thead>
    <tbody>
    <c:forEach items="${resultList}" var="result">
        <tr>
            <td><a href="<c:url value='/emphist/selectEmpHistDetail.do'><c:param name="empNo" value="${result.empNo}"/><c:param name="startDate" value="${result.startDate}"/></c:url>"><c:out value="${result.empNo}"/></a></td>
            <td><c:out value="${result.startDate}"/></td>
            <td><c:out value="${result.jobId}"/></td>
            <td><c:out value="${result.deptNm}"/></td>
            <td><c:out value="${result.salary}"/></td>
            <td><c:out value="${result.class_}"/></td>
            <td><c:out value="${result.note}"/></td>
        </tr>
    </c:forEach>
    </tbody>
</table>
<ui:pagination paginationInfo="${paginationInfo}" type="text" jsFunction="fn_select_page"/>
<a href="<c:url value='/emphist/insertEmpHistView.do'/>">등록</a>
</body>
</html>
