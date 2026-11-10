<%@ page contentType="text/html; charset=UTF-8" %>
<html lang="ko">
<head><title>새 창</title></head>
<body>
<a href="https://example.org/" target="_blank">밖</a>
<a href="<c:url value='/a.do'/>" target="_blank" title="새 창">안</a>
<a href="<c:out value="${vo.url}"/>" target='_blank'>값</a>
<a class="x"
   href="/b.do"
   target="_blank">여러 줄</a>
<a href="/c.do" target="_blank">하나</a> <a href="/d.do" target="_blank">둘</a>
<a href="/e.do" target="_blank" rel="noopener">있음</a>
<a href="/f.do" target=_blank>맨 값</a>
<script>var h = "<a href='" + u + "' target=_blank >x</a>";</script>
<%-- <a href="/g.do" target="_blank">주석</a> --%>
</body>
</html>
