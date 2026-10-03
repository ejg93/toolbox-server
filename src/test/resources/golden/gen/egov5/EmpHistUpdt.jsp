<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>사원 이력 수정</title>
</head>
<body>
<h1>사원 이력 수정</h1>
<form:form modelAttribute="empHistVO" action="${pageContext.request.contextPath}/emphist/updateEmpHist.do" method="post">
<table>
    <caption>사원 이력 수정</caption>
    <tbody>
        <tr>
            <th scope="row"><label for="empNo">사원 번호</label></th>
            <td><form:input path="empNo" id="empNo" title="사원 번호" readonly="true"/> <form:errors path="empNo"/></td>
        </tr>
        <tr>
            <th scope="row"><label for="startDate">시작일</label></th>
            <td><form:input path="startDate" id="startDate" title="시작일" readonly="true"/> <form:errors path="startDate"/></td>
        </tr>
        <tr>
            <th scope="row"><label for="jobId">직무</label></th>
            <td><form:input path="jobId" id="jobId" title="직무" maxlength="10"/> <form:errors path="jobId"/></td>
        </tr>
        <tr>
            <th scope="row"><label for="deptNm">부서명</label></th>
            <td><form:input path="deptNm" id="deptNm" title="부서명" maxlength="30"/> <form:errors path="deptNm"/></td>
        </tr>
        <tr>
            <th scope="row"><label for="salary">급여</label></th>
            <td><form:input path="salary" id="salary" title="급여"/> <form:errors path="salary"/></td>
        </tr>
        <tr>
            <th scope="row"><label for="class_">등급</label></th>
            <td><form:input path="class_" id="class_" title="등급" maxlength="5"/> <form:errors path="class_"/></td>
        </tr>
        <tr>
            <th scope="row"><label for="note">NOTE</label></th>
            <td><form:input path="note" id="note" title="NOTE"/> <form:errors path="note"/></td>
        </tr>
    </tbody>
</table>
    <button type="submit">수정</button>
</form:form>
<a href="<c:url value='/emphist/selectEmpHistList.do'/>">목록</a>
</body>
</html>
