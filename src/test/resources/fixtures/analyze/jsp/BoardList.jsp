<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%-- 주석 안 URL 은 무시: <c:url value='/ignored/comment.do'/> --%>
<!-- <a href="/ignored/html.do">x</a> -->
<form name="f" action="${pageContext.request.contextPath}/bbs/add.do" method="post">
<a href="<c:url value='/bbs/list.do'/>">목록</a>
<a href="<c:url value="/bbs/detail.do"/>?id=1">상세</a>
<a href="javascript:fCallUrl('/sec/ram/EgovAuthorList.do');">권한</a>
<c:import url="/cmm/fms/selectFileInfs.do" charEncoding="utf-8"/>
<script>
function go() { location.href = "<c:url value='/bbs/list.do'/>"; }
$.ajax({ url: '${pageContext.request.contextPath}/uat/uia/refreshSessionTimeout.do', type: 'post' });
document.f.action = "<c:url value='RelativeOnly.do'/>";
var s = window.document.title + "a.done";
</script>
<a href="/cop/stf${prefix}/a.do">EL 중간</a>
<a href="/x.do?searchMode=1">쿼리 꼬리</a>
</form>
