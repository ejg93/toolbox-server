// 파일 머리 주석
var re = /\/\/ 정규식 안 슬래시 둘/g;
var url = 'http://example.local//x'; // 줄 끝 주석
var tpl = `템플릿 안 /* 이것도 */ 남는다 ${1 + /* 식 안 주석 */ 2}`;
/*
 * 블록 주석
 */
function f(x) {
  return x / 2 / 3; // 나눗셈 둘은 정규식이 아니다
}
