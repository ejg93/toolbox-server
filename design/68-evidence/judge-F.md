# 판정 F — Opus (2026-11-14)

(핸드백 글을 실행 세션이 저장)

판정 폴더 `A/judge-F/`. `p1/` = probe-main-F-1 사본(run.sh·probe.yaml 경로를 judge-F/p1 로, P3 포트 18831). `p2/` = probe-main-F-2 사본(http.sh 경로 한 단계 위, 포트 18832). 점검자 원래 출력은 `p1/P*.orig`·`p2/orig/`, 판정자 출력은 `p1/P*.out`·`p2/out/`. 다시 돌린 결과: P1·P2(시간·경로만 다름)·ProbeRo·ProbeMeta·ProbeVendor·http.sh(pid 만 다름)는 원래와 diff 없음. P3 은 같은 결함이 모두 다시 나옴. P4 는 OOM 줄 번호만 120→122(같은 CLOB 읽기 루프). 띄운 서버 둘은 탐침이 스스로 껐다. 남은 java·18831·18832 LISTEN 없음 확인.

결함 키는 뿌리 하나에 한 행. F/1-03 은 뿌리 넷(①~④)으로, F/1-09 는 둘(①②)로 나눴다. #2 안에서 F/2-11 과 F/2-14 는 같은 뿌리라 하나로 셌다.

| 결함 키 | #1 id | #2 id | 판정 | 등급 | 파일:줄 | 한 문장 | 재현(판정자 명령 + 실제 출력 한 토막) |
|---|---|---|---|---|---|---|---|
| K1 겹친 블록 주석 | F/1-01 (F/1-03 의 COPY·VACUUM 줄 포함) | — | 확인 | Critical | core/conn/ReadOnlyJdbc.java:103(strip 이 첫 `*/` 에서 닫음)·:61-68 | 래퍼가 본 첫 낱말(SELECT)과 H2 가 실행하는 문장이 달라, COMMENT ON·SET PASSWORD·RUNSCRIPT·SHUTDOWN 이 읽기 전용 연결로 실행된다(절대 규칙 6) | `cd judge-F/p1; bash run.sh P1 "$(pwd -W)"` → `PASS /* /* */ SELECT */ COMMENT ON TABLE …` 뒤 `T.remarks=changed-by-probe` · `RUNSCRIPT FROM … updateCount=4` · `prepare COMMIT: PASS`(맨몸 COMMENT 는 `REFUSED/ERR`) |
| K2 `;` 없는 문장 잇기 | F/1-03① | F/2-02 | 확인(H2 대역) | Critical | ReadOnlyJdbc.java:69(여러 문장 판정이 `;` 뿐)·:35-36 | 첫 낱말 SELECT 뒤에 `;` 없이 이어 붙인 SHUTDOWN·BACKUP·KILL·DENY·DBCC 등이 `check` 를 지난다. SQL Server 는 이 꼴을 배치로 실행한다 | `bash run.sh P2` → `PASS SELECT 1 SHUTDOWN WITH NOWAIT` · `PASS SELECT 1 KILL 55` · ProbeRo → `PASS \| SELECT 1 DISABLE TRIGGER ALL ON T`(대조 `REFUSE \| SELECT 1 DROP TABLE T`) |
| K3 숫자에 붙인 낱말 | F/1-03② | — | 확인(H2 대역) | Critical | ReadOnlyJdbc.java:36·:37(`\b` 가 숫자와 글자 사이에 없음) | `1INTO`·`1FOR SHARE` 가 WRITE·SHARE_LOCK 에 안 걸린다(SQL Server 는 `1INTO` 를 `1 INTO` 로 읽는다) | P2 → `PASS SELECT 1INTO t2 FROM t` · `PASS … ID=1FOR SHARE`(대조 `REFUSED SELECT 1 INTO t2`). H2 는 `1FOR SHARE` 를 42000 으로 거절 |
| K4 MySQL 실행 주석 | F/1-03③ | — | 확인(H2 대역) | Critical | ReadOnlyJdbc.java:102-105 | strip 은 `/*! … */` 를 주석으로 지우지만 MySQL·MariaDB 는 그 안을 코드로 실행한다 | P2 → `PASS /*! REPLACE t */ SELECT 1` · `PASS /*M! REPLACE t */ SELECT 1` |
| K5 잠금 판정 우회 | F/1-03④ | F/2-03 | 확인(H2 대역) | Important | ReadOnlyJdbc.java:36-37·:76 | SQL Server 잠금 힌트(UPDLOCK·HOLDLOCK·TABLOCKX)와 `FOR/**/SHARE`·`FOR--\nSHARE` 가 잠금 판정을 지난다. 쓰기는 아니고 운영 DB 잠금이다 | ProbeRo → `REFUSE \| … FOR SHARE` 인데 `PASS \| … FOR/**/SHARE` · `PASS \| … WITH (TABLOCKX, HOLDLOCK)`. H2 실행은 42000(문법 없음) |
| K6 점 없는 ı 대소문자 | — | F/2-01 | 확인 | Critical | ReadOnlyJdbc.java:35-36·:72 | WRITE 정규식은 ASCII 로만 대소문자를 접는다. H2 는 `ı` 를 `I` 로 읽어 SELECT 안의 INSERT 가 실제로 행을 넣는다 | `java … kr.ejg.toolbox.core.conn.ProbeRo` → `PASS \| SELECT * FROM FINAL TABLE (ıNSERT …)` · `dotless-i INSERT: 실행됨` · `T 행 수 before=1 after=2` |
| K7 시퀀스 진행 | — | F/2-04 | 제외 겹침(⑪) | — | ReadOnlyJdbc.java:72 | `NEXT VALUE FOR`·`.NEXTVAL` 은 SELECT 식 안의 부작용으로 ⑪ 과 뿌리가 같다(점검자 스스로 `nextval('…')` 을 ⑪ 로 분류) | ProbeRo → `PASS \| SELECT NEXT VALUE FOR SEQ1` · `SEQ1 BASE_VALUE before=1 after=4`(재현은 됨, 세지 않음) |
| K8 diff 가 CHECK·FK 규칙을 안 봄 | F/1-04 | F/2-06 | 확인 | Critical | core/meta/SnapshotDiff.java:137-150(constraints)·:81-113 | CHECK 의 추가·변경·삭제와 FK 의 ON DELETE 규칙 변경이 「차이 없음」 이 되어 테이블 변경이력·반영 DDL 에서 조용히 빠진다(javadoc 이 한계로 밝히지 않음) | P2 → `equals(before, after)=false  diff.isEmpty()=true  changedTables=0` · ProbeMeta → `4a FK 삭제규칙 CASCADE→NO ACTION: isEmpty=true` · `4b … CHECK 지움: isEmpty=true` |
| K9 diff 표 키가 스냅샷마다 다름 | F/1-02 | F/2-07 | 확인 | Critical | SnapshotDiff.java:153-166(161) | 같은 이름 표가 한쪽 스냅샷에서만 두 스키마에 있으면 그대로인 표가 삭제+추가로 나온다. 반영 DDL 은 이미 있는 표의 CREATE TABLE 과 DROP TABLE 을 낸다 | `bash run.sh P3` → `diff a=1 b=4 -> {"addedTables":["APP.T1","APP_BAK.T1"],"removedTables":["T1"],"changedTables":[]}` · alter `표 추가 2 · 삭제 1 · 변경 0`, `CREATE TABLE APP.T1` 두 번 |
| K10 스냅샷 저장 길이 넘침 | F/1-05 | F/2-08 | 확인 | Important | core/meta/SnapshotStore.java:90-100·:155 + db/migration/V001(note 1000·schemas·comment 4000) | 길이 검사가 없어, 넘치는 값 하나 때문에 수집이 끝난 뒤 스냅샷이 통째로 FAILED 가 된다. 오류 글에는 내부 INSERT 문과 넘친 값의 앞머리가 실린다 | P3 → `FAILED progress 90 … Value too long for column "NOTE CHARACTER VARYING(1000)"` · `… "COMMENT CHARACTER VARYING(4000)"` · ProbeMeta → `수집 스키마=401 표=3` → `저장 실패 … "SCHEMAS CHARACTER VARYING(4000)"` |
| K11 행 수 SQL 이 자기 래퍼에 걸림 | F/1-06 | F/2-12 | 확인 | Minor | core/meta/RowCounter.java:67-74·:79-82 | 이름이 LOCK·CALL·GRANT 이거나 `;` 를 든 표는 COUNT(*) 가 거절된다. 그 경고는 시간 예산 넘김과 같은 키(빈 SQLState·0)로 합쳐진다 | P3 → `LOCK … rowCount:null` · `ORDER-UPDATE … rowCount:null` · `warnings [{"kind":"rowCount","sqlState":"","vendorCode":0,"count":2}]` · ProbeMeta → `CALL …rowCount=null` · `a;b …rowCount=null` |
| K12 사전 길이 넘침이 500, 가져오기는 반쯤 들어감 | F/1-07 | F/2-11·F/2-14(같은 뿌리, 하나로 셈) | 확인 | Important | core/dict/DictStore.java:310-321·:266-300 · web/DictRoutes.java:35-44·:64·:107-120 | 약어 100자·한글 200자 넘침이 SQLException 이 되어 500 「서버 오류」 가 나고 어느 줄인지 안 보인다. 기관표준 파일은 긴 줄 하나에 통째로 실패한다. `/api/dict/import` 는 앞 항목만 남고 다시 눌러도 같은 자리에서 멈춘다 | P3 → `PUT user ko 201자 -> 500` · `org import ko 201자 -> 500` · `dict import 긴 값 섞임 -> 500` 뒤 OK1 만 · http.sh → `1 org 한글 201자 : 500` · `6 import 중간 실패 : … 500 · 그 뒤 user=2` |
| K13 기관표준 0 건 가져오기가 기존 사전을 지움 | — | F/2-10 | 확인 | Important | DictStore.java:266-300 | 머리·구분자를 못 알아본 파일도 DELETE 뒤 0 건으로 커밋해 기관표준 사전이 빈다(공통표준 쪽은 0 건이면 예외) | ProbeVendor → `정상 파일 넣음=2 org=2` → `머리·구분자가 다른 파일: 넣음=0 · 그 뒤 org=0` · http.sh → `2 org 다른 머리(1열) : {"imported":0}`(200) |
| K14 JSON `null` 본문이 NPE 500 | F/1-08 | F/2-16 | 확인 | Minor | web/MetaRoutes.java:43·:67-68 · web/TableRoutes.java:29-30 · web/DictRoutes.java:36 | bodyAsClass 가 준 null 을 바로 역참조해, 400 이어야 할 것이 500 이 된다 | P3 → `POST alter null 본문 -> 500`(`NullPointerException @ MetaRoutes…:67`) · http.sh → `8 snapshot 본문 null : … 500` · `11 table export null : … 500` · `12 dict user 본문 null : … 500` |
| K15 사전 CSV 수식 인젝션 | F/1-09① | F/2-15 | 확인 | Minor | DictRoutes.java:99·:132(csv) | `=`·`+`·`-`·`@` 로 시작하는 칸을 그대로 써서 엑셀이 수식으로 연다 | P3 → `export user -> 200 … "=HYPERLINK(…)"` · http.sh → `4 export CSV 본문 : … ,=1+1` |
| K16 export `kind` 를 검증 없이 머리에 넣음 | F/1-09② | — | 확인 | Minor | DictRoutes.java:90-100 | 모르는 kind 도 사용자 사전을 내보내며, 그 글자가 Content-Disposition 에 그대로 들어가 머리 인자가 주입된다(CRLF 는 Jetty 가 공백으로 바꿈) | P3 → `content-disposition=[attachment; filename="dict-a";x="b.csv"]`(#2 는 CRLF 만 시험해 문제없음으로 버림 — 다른 꼴) |
| K17 URL 안 비밀번호 판정 우회 → /api/conn | F/1-10 | — | 확인 | Critical(절대 규칙 2, 사용자가 URL 에 비밀을 적은 드문 꼴) | core/profile/Profile.java:61-63 → core/conn/ConnectionRegistry.java:48-51 | `password1=`·`%70assword=`·Teradata `/PASSWORD=`·`sslpassword=`·`trustStorePassword=` 가 판정을 지나고, list 가 url 을 가리지 않고 응답에 싣는다 | P2 → `통과 jdbc:mysql://…&password1=…` 다섯 줄(대조 `거절 …&password=…`) · P3 → `GET /api/conn -> 200 … "url":"jdbc:sqlserver://…;trustStorePassword=<가짜 값 그대로>…"` |
| K18 SqlRunner CLOB 통째 읽기 OOM | F/1-11 | — (버린 후보, 안 돌림) | 확인 | Minor | core/sqlrun/SqlRunner.java:114-125 | 값 크기 상한 없이 CLOB 을 문자열로 다 읽어 요청 스레드가 OutOfMemoryError 를 낸다(호출처는 코드값·FK 참조값이라 드묾) | `JOPTS=-Xmx256m bash run.sh P4` → `실패 java.lang.OutOfMemoryError @ …SqlRunner.value(SqlRunner.java:122)` |
| K19 data 폴더 경로의 `;` 가 H2 설정이 됨 | F/1-12 | — (버린 후보, 안 돌림) | 확인 | Minor | core/db/Db.java:33 | 경로를 H2 URL 에 그대로 이어 붙여, `;` 든 폴더에서는 기동이 실패하거나 H2 파일이 data 폴더 밖에 생긴다 | P2 S4 → `da;ta -> IllegalStateException H2 90046` · `data;TRACE_LEVEL_FILE=3 -> … 42122` 뒤 `파일 data.mv.db`·`data.trace.db`(형제 자리) |
| K20 Array·SQLXML 값이 프록시 클래스 이름으로 나감 | — | F/2-05 | 확인 | Minor | SqlRunner.java:92-127(:126 toString) + ReadOnlyJdbc.java:160·:296-301 | value 가 Array·SQLXML 을 안 다뤄 toString 으로 떨어지는데, 래퍼 프록시가 값 대신 `ReadOnlyJdbc(JdbcArray)` 를 낸다(드문 타입, 결과에서 눈에 보임) | ProbeRo 3절 → `TAGS 칸 값=ReadOnlyJdbc(JdbcArray)` · 감싸지 않은 연결은 배열 글 |
| K21 PostgreSQL reltuples 0 을 실제 0 행으로 받음 | — | F/2-09 | 확인(H2 대역) | Critical | core/dialect/PostgresMetaSource.java:28·:86 | 음수만 모름으로 보고 0 은 그대로 받아, PG 13 이하에서 분석 안 된 작은 표가 0 행으로 실린다. RowCounter 가 다시 세지 않고, skipEmpty 면 표가 스냅샷에서 사라진다 | ProbeVendor → `code_tbl rowCount=0 (실제 3행) · RowCounter 대상=false` · `skipEmpty: 남은 표=[pg_class, pg_namespace]`(H2 PG 모드 흉내 표) |
| K22 Oracle 자동 NOT NULL 판정이 ASCII `\w` | — | F/2-13 | 확인(H2 대역) | Minor | core/dialect/OracleMetaSource.java:51·:150 | 한글·공백 든 컬럼의 자동 NOT NULL 제약이 CHECK 로 실린다 | ProbeVendor 2절 → `CHECK 로 실린 제약 이름=[SYS_C002, SYS_C003] (기대 [])` |
| K23 검색 LIKE 와일드카드 미이스케이프 | — (버린 후보 「넓어질 뿐」) | F/2-17 | 확인 | Minor | DictStore.java:340-347 | 검색어 `_`·`%` 한 글자로 전부 나온다 | ProbeVendor 3절 → `검색 q=_ (밑줄 든 약어는 없다): 2건 · q=% : 2건` |

n1 = 16 (K1·K2·K3·K4·K5·K8·K9·K10·K11·K12·K14·K15·K16·K17·K18·K19) · n2 = 15 (K2·K5·K6·K8·K9·K10·K11·K12·K13·K14·K15·K20·K21·K22·K23. K7 은 제외라 안 셈) · m = 9 (K2·K5·K8·K9·K10·K11·K12·K14·K15) · N ≈ 16·15/9 ≈ 26.7 (약 27)

- 「확인(H2 대역)」 을 빼고 실제 실행으로 확인된 것만 세면 n1 = 12, n2 = 11, m = 7, N ≈ 18.9
- 보고서 id 단위로 세면 n1 = 12(K3·K4·K16 이 F/1-03·F/1-09 에 흡수됨), m = 9, N ≈ 20

등급 합계(제외 K7 을 뺀 22): Critical 9(K1·K2·K3·K4·K6·K8·K9·K17·K21) · Important 4(K5·K10·K12·K13) · Minor 9(K11·K14·K15·K16·K18·K19·K20·K22·K23).

등급 근거: K8·K9·K21 은 7절 「조용히 틀린 산출물」 이라 Critical. K17 은 절대 규칙 2 위반이라 Critical(다만 URL 에 비밀을 직접 적은 드문 꼴). K5 는 쓰기가 아니라 잠금이라 Important. K20 은 드문 타입이고 값이 눈에 보이게 깨져 Minor.

설계 한계로 표시한 것은 없다. ReadOnlyJdbc javadoc 의 「엄격한 쪽」 은 오거절에 관한 것이고 위 결함은 모두 통과 쪽이다. SnapshotDiff javadoc 은 스냅샷별 키 규칙을 적었을 뿐 스냅샷 사이 불일치나 CHECK 제외를 한계로 밝히지 않았다.

## 불확인 목록과 사유

- 불확인 없음. 지적 29개(F/1-01~12, F/2-01~17)가 모두 탐침 재실행에서 같은 결함으로 다시 나왔다.
- 제외 겹침 1: F/2-04(시퀀스 진행) — 8절 ⑪ 부작용 함수와 같은 뿌리
- 「확인(H2 대역)」: K2·K3·K4·K5 는 `check` 통과까지만. SQL Server·MariaDB·PostgreSQL 실물 실행은 못 돌림. K21·K22 는 H2 호환 모드의 흉내 딕셔너리 표로만(PG ≤13 reltuples 0, Oracle 조건 글 꼴은 문서 지식)
- K17 재현 응답 url 에는 프로필의 가짜 키 저장소 비밀이 그대로 나온다 — 이 글에는 값을 싣지 않았다
