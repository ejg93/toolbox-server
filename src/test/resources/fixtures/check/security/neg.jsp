<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<p><c:out value="${param.keyword}"/></p>
<c:if test="${param.mode == 'list'}"><p>${fn:escapeXml(param.keyword)}</p></c:if>
<script>
document.getElementById("box").innerHTML = '';
document.getElementById("box").textContent = data;
var v = obj.evaluate(x);
</script>
