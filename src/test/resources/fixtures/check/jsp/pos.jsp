<%@ page contentType="text/html; charset=UTF-8" %>
<% String name = request.getParameter("name"); %>
<p><%= name %></p>
<c:out value="${v}" escapeXml="false"/>
