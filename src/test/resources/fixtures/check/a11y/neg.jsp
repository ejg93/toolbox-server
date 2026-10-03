<%@ page contentType="text/html; charset=UTF-8" %>
<%-- <img src="x"> <iframe src="y"> document.all --%>
<html lang="ko">
<head><title>목록</title></head>
<body>
<img src="/a.png" alt="로고">
<img src="/line.png" alt="">
<input type="hidden" name="h">
<input type="text" id="q" name="q">
<label for="q">검색어</label>
<input type="text" name="t" title="제목">
<select id="s" name="s"><option>1</option></select>
<textarea name="c" title="내용"></textarea>
<iframe src="/x.do" title="미리보기"></iframe>
<div onclick="go()" onkeypress="go()" tabindex="0">이동</div>
<table summary="목록"><caption>목록</caption><tr><th scope="col">제목</th></tr></table>
<script>
var el = document.getElementById("box");
</script>
</body>
</html>
