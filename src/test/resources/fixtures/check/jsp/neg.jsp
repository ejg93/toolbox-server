<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%-- <% legacy(); %> <%= x %> escapeXml="false" --%>
<c:out value="${v}" escapeXml="true"/>
<% /* 설명 주석 */ %>
<a href="/x.do" target="_blank" rel="noopener noreferrer">있음</a>
<a href="/y.do" target="_self">같은 창</a>
<%-- <a href="/z.do" target="_blank">주석</a> --%>
