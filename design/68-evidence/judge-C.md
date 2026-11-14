# 판정 C — Opus (2026-11-14)

(핸드백 글을 실행 세션이 저장)

판정자 탐침 폴더 `A/judge-C/p1`(C-1 탐침 사본, run.sh 의 P 만 바꿈) · `A/judge-C/p2`(C-2 탐침 사본, BigConcat 은 6000 항으로 다시 만듦). 서버는 127.0.0.1:18771 에 한 번 띄웠고(pid 19572) 끝에 껐다. 남은 java 없음. 표본 전수 탐침은 `P2Corpus` 한 번만. C-2 의 `T4` 는 안 돌림.

| 결함 키 | #1 id | #2 id | 판정 | 등급 | 보정 같음 | 파일:줄 | 한 문장 | 재현(판정자 명령 + 실제 출력 한 토막) |
|---|---|---|---|---|---|---|---|---|
| C-VCS-EQ | C/1-01 | — | 확인 | Important | — | core/vcs/WorkingCopy.java:161-177 | 필터 이름에 `=` 가 들면 `-c filter.<이름>.clean=` 이 첫 `=` 에서 다른 키로 갈려 저장소 설정의 clean 명령이 `git status` 에서 그대로 돈다 | `cd judge-C/p1; bash mkrepos.sh; bash run.sh kr.ejg.toolbox.core.vcs.P3Git <p1>/git` → `r1 filtersOff=[-c, filter.a=b.clean=, …] … 필터 실행 횟수 전=0 후=3`(대조군 `r0 … 전=0 후=0`) |
| C-VCS-SCOPE | C/1-02 + C/1-03(같은 뿌리) | C/2-02 | 확인 | Important | — | WorkingCopy.java:162 | 필터 이름을 바깥 저장소 `config --local --includes` 로만 모아 `config.worktree` 의 필터와 안에 든 저장소 자기 설정의 필터가 status 때 실행된다 | P3Git → `r2 filtersOff=[] … 전=0 후=3` · `r3 filtersOff=[] changed=[sub] … 전=0 후=3` · `probe.T2 repo-wt`·`repo-sub` 둘 다 `after: marker exists=true` · HTTP `GET /api/check/vcs?path=<repo-wt>` 200 + 표식 생김, `Sec-Fetch-Site: cross-site` 면 403·표식 없음 |
| C-PROBE-MSG | C/1-04 | C/2-01 | 확인 | Critical | — | core/analyze/SqlProbe.java:240-242 · web/AnalyzeRoutes.java:433 | `withoutSqlText` 는 `near '…'` 꼴만 떼고, xlsx 에 쓰는 DB 오류문에 H2 「Syntax error in SQL statement "…"」·「Data conversion error converting "…"」 의 SQL 원문과 리터럴이 남는다(규칙 3) | `bash run.sh …P4Probe` → `p.s01_syntax ERROR \| xlsx 로 갈 글에 SQL 글 남음=true \| 글 길이=200` · `p.s07_convert … 남음=true` · 끝에서 끝 `POST /api/analyze/probe-xlsx` 200, `DB대조-1.xlsx` sheet1.xml 에 `<리터럴>' AND [*]AND ID = 1?a) tb_probe WHERE 1=` |
| C-TBL-COMMA | C/1-05 | C/2-03 | 확인 | Critical | 보정 C-2 와 같음 | core/analyze/SqlTables.java:223-259·316-334 | 괄호(인라인 뷰·TABLE()·PARTITION·WITH(NOLOCK)·PIVOT·LATERAL)·`@DBLINK`·ON 절이 닫힌 뒤 쉼표가 표 문맥을 다시 열지 않아 뒤 표가 미해결 없이 CRUD 에서 빠진다 | P1Tables `g comma after subquery \| A=R B=R`(C 없음)·`partition \| T1=R` · T1 `sql-01.txt \| TB_A=R \| unresolved=[]`(sql-02·03·08·09·11·15·18·30 도) · `P2Corpus` → `꼴1 … 표 빠진 문장 92`, 예 `EgovBbsStats_SQL_altibase.xml:118 빠진 표=COMTNBBS` · HTTP `/c/legacy.do` crud `{"TB_ORDER":"R"}` 뿐, `impact?table=TB_CUST` `"rows":[]` |
| C-TBL-DELALIAS | C/1-06 | C/2-04 | 확인 | Important | 보정 I-1 과 같음 | SqlTables.java:412-437(`opener`·`target`)·245 | `DELETE 별칭 FROM 표 별칭`·`UPDATE 별칭 SET … FROM 표 별칭` 에서 별칭을 대상 표로 적고(D·U) 진짜 표는 R 만. 없는 표가 「DB 에 없는 표」 로 드러나 조용하지는 않다 — Important | P1Tables `p1 mssql update alias \| A=U TB_A=R TB_B=R` · `p2 delete alias \| A=D TB_A=R TB_B=R` · T1 `sql-23.txt \| A=D TB_A=R TB_B=R` · P2Corpus `꼴2 … 16`, 예 `EgovNoteTrnsmit_SQL_*.xml:212 … RNS=D` |
| C-TBL-KEYWORD | C/1-07 | — | 확인 | Minor | — | SqlTables.java:239 · core/text/SqlTokens.java:21(STOP) | 따옴표·백틱·대괄호를 벗긴 뒤 예약어 목록에 대어 이름이 예약어인 표가 미해결 없이 빠진다 | P1Tables `a1 backtick keyword \| USER=R`(ORDER 없음) · `a2 quoted keyword \|  \| unresolved=[]` · `a3 bracket keyword \|  \| unresolved=[]` |
| C-TBL-DOLLAR | C/1-08 | — | 확인 | Minor | 보정 M-2 와 같음 | SqlTables.java:239-251 | 표 이름 뒤 `${}` 꼴을 `이름_$` 라는 표로 적고 `table` 미해결을 안 남긴다 | P1Tables `q1 dollar suffix \| TB_LOG_$=R \| unresolved=[]`(q2·q3 은 `unresolved=[table]`) |
| C-TBL-CTE | C/1-09 | C/2-12 | 확인 | Minor | — | SqlTables.java:163 | WITH 이름을 문장 경계 없이 결과 전체에서 지워 같은 이름의 진짜 표가 빠진다. javadoc 「WITH 이름은 표가 아니다」 는 이 부작용까지 밝히지 않아 설계 한계 아님 | P1Tables `k cte shadows table \| CUST=R`(ORDERS 없음, joins 에는 있음) · T1 `sql-14.txt \| TB_B=R`(TB_A 없음) |
| C-TBL-BSLASH | C/1-10 | — | 확인 | Minor | — | SqlTokens.java:111-118 | 역슬래시로 벗어난 홑따옴표를 리터럴 끝으로 읽어 뒤 글이 리터럴로 먹히고 그 뒤 표가 빠진다. 제외 ⑥(DdlReader)과 자리가 다르다 | P1Tables `c backslash quote \| T1=R`(T2 없음) |
| C-TBL-HASH | C/1-11 | — | 확인 | Minor | — | SqlTables.extract(RegexRule.stripComments sql) | MySQL `#` 줄 주석을 안 지워 주석 안 `FROM 이름` 이 표가 된다. 제외 ⑦(줄바꿈)과 다른 결함 | P1Tables `hash comment \| T1=R T9=R` |
| C-MAP-INCLUDE | C/1-12 | C/2-07 | 확인 | Minor | — | core/analyze/MapperIndex.java:246-278 | 자기를 끼우는 `<sql>` 조각을 깊이 상한까지 n^깊이로 펼치고 펼친 수 상한·색인 중 취소 확인이 없다. 입력이 인위적이라 Minor | `P6Big include 30`(-Xmx1g) → `실패: java.lang.OutOfMemoryError` · `ms=54280`(12 는 885ms) · HTTP proj4(include 25) 5초 뒤 `DELETE` `{"cancelled":true}` 인데 t10~t25 `RUNNING 80 매퍼 색인`, t30 `CANCELLED` |
| C-RUN-SOE | C/1-13 | C/2-05 | 확인 | Important | 보정 I-4 와 같음(남은 꼴) | core/analyze/AnalyzeRunner.java:165-167(JavaGraph.scan) | 깊게 겹친 식이 든 Java 파일 하나의 StackOverflowError 를 파일 단위로 못 가둬 분석 전체가 FAILED, 어느 파일인지 안 알려 피할 수 없다 | `P5Run <p1>/proj-deep-plus plus 5000` → `분석 전체 실패: java.lang.StackOverflowError`(paren 3000 도) · HTTP proj2(6000 항) → `"status":"FAILED","progress":90,"message":"호출 그래프","error":"StackOverflowError"` |
| C-JSP-QUAD | C/1-14 | — | 확인 | Minor | — | core/analyze/JspLinks.java:135-152(`start`) | `}` 마다 `lastIndexOf("${")` 로 글 머리까지 훑어 제곱 시간. 원인이 제곱 알고리즘이라 제외 「파일당 시간 상한」 과 다른 결함. 입력 인위적 | `P6Big jsp 50000` `ms=2478` · `jsp 100000` `ms=10634`(2배 크기 4.3배 시간) |
| C-PROBE-REGEX | C/1-15 | — | 확인 | Minor | — | SqlProbe.java:57(`TAIL_ORDER_BY`) | `[^()']*$` 가 `order by` 자리마다 끝까지 갔다 되돌아와 제곱 시간. 수천 개 `order by` 인위 입력에서만 | `P6Big orderby 9000` `ms=1391` · `orderby 18000` `ms=5630` |
| C-PROBE-DUPCOL | — | C/2-06 | 확인 | Minor | 보정 M-3 과 같음(dupCols 꼴) | SqlProbe.java:188(`wrap`) | 파생 표로 감싸면 같은 이름 컬럼을 낸 정상 SELECT 가 H2 에서 거짓 ERROR. MSSQL 은 못 돌림 | `T3 in/probe-mapper.xml h2` → `p.a15_starJoin \| ERROR \| msg=Duplicate column name "ID"`, mysql 길 `a15_starJoin \| PASS` |
| C-PROBE-DBID | — | C/2-08 | 확인 | Minor | — | SqlProbe.java:133-158(`pick`) | 같은 파일 안 `databaseId` 변형 둘을 방언 파일 여럿으로 보고 파일 이름으로만 골라 대조가 건너뛰어진다(소스에 databaseId 처리 없음) | T3 `oracle`·`mysql` 모두 `p.a11_dbId \| SKIP_NO_DIALECT_FILE` |
| C-PROBE-SEMI | — | C/2-09 | 확인 | Minor | 보정 M-3 과 같음(trailSemiComment 꼴) | SqlProbe.java:176-179 | 끝 `;` 뒤에 주석이 있으면 `;` 를 못 떼어 「여러 문장」 거절로 건너뛰어진다. #1 은 버린 후보에 둠 | T3 h2 `p.a02_semicolonThenComment \| SKIP_REFUSED \| 읽기 전용 연결 — 여러 문장…`(a03 도) · P4Probe `s02`·`s08 SKIP_REFUSED` |
| C-TBL-MULTIUPD | — | C/2-10 | 확인 | Minor | — | SqlTables.java:245-249 | MySQL 여러 표 UPDATE 에서 첫 표만 U, SET 이 고치는 둘째 표는 R | T1 `sql-17.txt \| verb=UPDATE \| TB_A=U TB_B=R`(SET 대상 TB_B) |
| C-JOIN-SET | — | C/2-11 | 확인 | Minor | — | core/analyze/SqlJoins.java:27-55 | SET 대입 `B.X = A.X` 를 조인 등식으로 낸다. javadoc 이 대입을 밝히지 않아 설계 한계 아님(결함인지 갈릴 수 있음) | T1 `sql-17.txt … joins=[Join[tableA=TB_A, colA=X, tableB=TB_B, colB=X], Join[…ID…]]` · P1 `p1` 도 같은 꼴 |
| C-WEB-NULL | — | C/2-13 | 확인 | Minor | — | web/AnalyzeRoutes.java:119-120·250·430-433 · web/DiffRoutes.java:29-30 | 본문 JSON `null`·`rows:[null]` 이면 400 대신 500. 화면은 이 본문을 안 보냄 | `curl -X POST :18771/api/analyze/run -d null` → `{"message":"서버 오류"} [500]` · `/api/diff/folders`·`/api/analyze/probe-xlsx`(`null`·`{"rows":[null]}`) 모두 `[500]` |
| C-JSP-REL | — | C/2-14 | 설계 한계 | — | — | JspLinks.java:18(javadoc) | 상대 `.do` 링크를 버린다. javadoc 「/ 나 ${ 로 시작하는 토큰만 — 그 밖은 스크립트 주석·상대 경로(egov 실측 6)라 버린다」 가 밝힌 설계 | 소스 대조만, 세지 않음 |

n1 = 14 · n2 = 13 · m = 7 · N ≈ 14·13/7 = 26

- #1 의 C/1-02·C/1-03 은 같은 뿌리(필터 이름 수집 범위가 바깥 저장소 `--local` 하나)라 하나로 셌다 — id 열다섯에 결함 열넷
- #2 는 C/2-14(설계 한계)를 빼고 열셋
- 짝(m): VCS-SCOPE · PROBE-MSG · TBL-COMMA · TBL-DELALIAS · TBL-CTE · MAP-INCLUDE · RUN-SOE
- 제외 겹침 없음

## 불확인 목록과 사유

- 불확인 없음. 모든 지적이 판정자 재실행에서 같은 결함으로 다시 나왔다.
- 재현 정도가 보고서와 다른 것(판정 영향 없음): C/1-01~03 필터 실행 횟수 `전=1 후=2` → `전=0 후=3`(저장소를 새로 만듦) · C/2-07 CANCELLED 까지 약 45초 → 약 25초 · C/1-12 OOM 37s → 54s · C/2-03 표본 건수는 `T4`(132) 대신 `P2Corpus`(92, 꼴이 더 좁음)만
- 범위 한계: C/1-04·C/2-01·C/2-06 은 H2 엔진으로만. C/2-11 은 대입도 두 컬럼의 관계라 볼 수 있어 결함 여부가 갈릴 수 있다(출력은 재현됨)

## 등급을 다시 매긴 근거

- C/1-01~03·C/2-02 → Important(#1 Critical, #2 Important). 도구 자신이 절대 규칙을 어기지는 않고 심어 둔 설정이 있어야 하는 길. 0-83c 선례가 Important
- C/1-05·C/2-03 → Critical(#1 Important). 산출물에서 표가 미해결 표시 없이 조용히 빠지고 표본 92건 되풀이. 보정 C-2 와 같은 등급
- C/1-06·C/2-04 → Important. 결과가 틀리지만 없는 표가 「DB 에 없는 표」 로 드러난다. 보정 I-1 과 같은 등급
- C/1-13·C/2-05 → Important(#1 Minor). 작업이 FAILED 로 보이고 어느 파일인지 안 알려 피할 수 없다. 보정 I-4 와 같은 등급
- C/2-13 → Minor
