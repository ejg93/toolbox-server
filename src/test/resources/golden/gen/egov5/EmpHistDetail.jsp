<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>사원 이력 상세</title>
</head>
<body>
<h1>사원 이력 상세</h1>
<table>
    <caption>사원 이력 상세</caption>
    <tbody>
        <tr>
            <th scope="row">사원 번호</th>
            <td><c:out value="${result.empNo}"/></td>
        </tr>
        <tr>
            <th scope="row">시작일</th>
            <td><c:out value="${result.startDate}"/></td>
        </tr>
        <tr>
            <th scope="row">직무</th>
            <td><c:out value="${result.jobId}"/></td>
        </tr>
        <tr>
            <th scope="row">부서명</th>
            <td><c:out value="${result.deptNm}"/></td>
        </tr>
        <tr>
            <th scope="row">급여</th>
            <td><c:out value="${result.salary}"/></td>
        </tr>
        <tr>
            <th scope="row">등급</th>
            <td><c:out value="${result.class_}"/></td>
        </tr>
        <tr>
            <th scope="row">NOTE</th>
            <td><c:out value="${result.note}"/></td>
        </tr>
    </tbody>
</table>
<form name="detailForm" method="post" action="<c:url value='/emphist/updateEmpHistView.do'/>">
    <input type="hidden" name="empNo" value="<c:out value='${result.empNo}'/>"/>
    <input type="hidden" name="startDate" value="<c:out value='${result.startDate}'/>"/>
    <button type="submit">수정</button>
    <button type="submit" formaction="<c:url value='/emphist/deleteEmpHist.do'/>">삭제</button>
</form>
<a href="<c:url value='/emphist/selectEmpHistList.do'/>">목록</a>
</body>
</html>
