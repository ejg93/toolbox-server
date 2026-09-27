-- 사용자 조회
SELECT /*+ INDEX(U IX_USER_01) */ U.USER_ID -- 힌트는 기본으로 남는다
     , U.USER_NM /* 이름 */
     , '문자열 안 -- 와 /* */' AS TXT
     , q'[q-quote 안 -- 도 남는다]' AS QQ
FROM   TB_USER U
/* 여러 줄
   블록 */
WHERE  U.USE_YN = 'Y'; -- 끝
