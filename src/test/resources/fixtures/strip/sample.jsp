<%@ page contentType="text/html; charset=UTF-8" %>
<%-- JSP 주석 --%>
<!-- HTML 주석 -->
<div class="box">
  <% // 스크립틀릿 안 자바 주석
     String s = "<!-- 문자열 안 -->"; /* 블록 */ %>
  <script>
    // 스크립트 주석
    var u = "//cdn.example.local/x.js";
  </script>
  <style>/* CSS 주석 */ .a { color: red; }</style>
</div>
