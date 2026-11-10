<%@ page contentType="text/html; charset=UTF-8" %>
<% String name = request.getParameter("name"); %>
<p><%= name %></p>
<c:out value="${v}" escapeXml="false"/>
<a href="<c:url value='/x.do'/>" target="_blank">새 창</a>
<a class="b"
   target="_blank" href="/y.do">여러 줄</a>
