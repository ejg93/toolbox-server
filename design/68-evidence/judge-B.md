# 판정 B — Opus (2026-11-14)

(핸드백 글을 실행 세션이 저장)

판정자 탐침 폴더: `A/judge-B/p1`(#1 복사본, 서버 포트 18473→18731) · `A/judge-B/p2`(#2 복사본, 서버 포트 18652→18793, 프로필 INIT·매핑 경로를 복사본으로 바꿈, 출력 폴더는 비우고 Seed 로 스냅샷 1~4 를 다시 넣음). 공통 명령: `J="C:/Program Files/Java/jdk-17.0.19/bin"`, p1 은 `"$J/java" -Xmx3g -cp "classes;$(cat cp-full.txt)" P <이름>`·`S`, p2 는 `CP="A/main/tree/target/classes;$(cat A/main/cp.txt)"` 로 `"$J/java" -cp "classes;$CP" <탐침>` 과 `curl -H Content-Type:application/json -H Origin:http://127.0.0.1:18793 -X POST`.

| 결함 키 | #1 id | #2 id | 판정 | 등급 | 파일:줄 | 한 문장 | 재현(판정자 명령 + 실제 출력 한 토막) |
|---|---|---|---|---|---|---|---|
| B-01 xlsx 합계 수식 안 늘어남 | B/1-08 | B/2-01 | 확인 | Critical | core/report/XlsxFiller.java:93-95 | 데이터 행 아래를 shiftRows 로 밀기만 해서 양식 합계 수식이 첫 행만 센 채 나감. 주석이 「합계·서명란」 을 예상하므로 드문 입력이 아님 | p1 `P filler` → `행5: [1 FORMULA =SUM(B2:B2)]` `합계 수식 값 = 10.0 (기대 60)` · p2 `FillProbe fill` → `6: 합계 \| F:SUM(B3:B3)` |
| B-02 같은 이름 표 두 스키마 관계 오귀속 | B/1-09 | — | 확인 | Critical | core/deliverable/Relations.java:67 · Definitions.java:399 | 표를 대문자 이름만으로 putIfAbsent 색인해서 04 관계가 먼저 정렬된 스키마에 붙음. 둘째 스키마 관계는 표시 없이 빠짐 | p1 `P same` → `04 행 수 = 1` `부모 APP_A.CUSTOMER ← 자식 APP_A.ORDERS · 추정(조인 1문장)` |
| B-03 마스킹 과탐(여부·코드·내용 컬럼) | B/1-01 | B/2-03 | 확인 | Important | resources/logical/masking.yaml:15·19·24 · core/logical/Masking.java:113-149 | 키워드가 코멘트 어디에 있든, 또는 정규식 한 마디만 맞아도 후보가 됨. 그래서 _YN·_CD·_AT 컬럼을 `*` 로 덮는 UPDATE 와 03 개인정보 Y 가 나옴. 표본에서 되풀이됨 | p1 `P mask2` → `뒤 = 수신여부[*] OS코드[***] 메뉴이름[게*****]` · p2 `/api/logical/masking {"snapshotId":1}` → 후보 `EMAIL_YN`·`PHONE_CERT_YN`·`ADDR_TYPE_CD`·`PRGM_NM` · `MaskProbe run/cols.txt` → `email EMAIL_ANSWER_AT ×25` `email EMAIL_CN ×121` `address WEB_ADDRESS ×1` |
| B-04 마스킹 이름 예약어 안 감쌈 | B/1-02 | B/2-09 | 확인 | Important | core/logical/Masking.java:287-296 | 영문·숫자 꼴이면 예약어여도 그대로 씀. 같은 판정을 QualitySql.id 는 DdlGen.reserved 로 감싸므로 판정 중복이 이미 어긋남 | p1 `P mask2` → `mssql: UPDATE dbo.User SET` / `QualitySql 은: FROM   dbo.[User]`. `P mask` → `H2 FAIL UPDATE PUBLIC.USER :: SQLState 42001` · p2 `IdProbe` → `UPDATE public.user SET` 를 실행하면 `Syntax error … "UPDATE public.[*]user SET` (#2 의 대소문자 섞인 이름 몫은 ㉜ 와 꼴이 같아 따로 세지 않음) |
| B-05 내려받기 자리 프로필 없으면 500 | B/1-06 | — | 확인 | Important | web/LogicalRoutes.java:221 | outFile 이 orElseThrow 를 써서 프로필 없이 띄운 서버의 저장이 사유 없는 500 이 됨. Outputs.dir·TableRoutes 는 같은 경우 default 로 넘겨 판정이 어긋남 | p1 `S` → `POST /api/logical/candidates → 500 {"message":"서버 오류"}`(comments·masking 도 500), 로그 `NoSuchElementException @ LogicalRoutes.outFile:221`, 같은 서버 `POST /api/table/export → 200 …\out\default\…` |
| B-06 CSV 본문 1MB 상한 413 | B/1-07 | — | 확인 | Important | web/LogicalRoutes.java:56·156 (상한은 App.create 기본값) | 컬럼목록 CSV 를 JSON 글로 받는데 Javalin 기본 1,000,000 바이트를 넘으면 JSON 이 아닌 413 이 나옴 | p1 `S` → `컬럼 38040줄 CSV(1100004자) → POST /api/logical/run → 413 Content Too Large`, 로그 `Body greater than max size (1000000 bytes)` |
| B-07 이름 기반 관계 후보 폭증 | B/1-05 | — | 확인 | Important | core/deliverable/Relations.java:143-173 · DeliverableService.java:215-216 | PK 이름·형식이 같은 표를 전부 짝지어 n(n-1)/2 행이 됨. 1,500 표에서 xlsx 행 상한을 넘어 작업이 실패하고 0 바이트 작성안내가 남음 | p1 `P named 300` → `이름 기반 후보 행 = 44850` · `P named 1500` → `실패 java.lang.IllegalArgumentException: Invalid row number (1048576)…` `00_작성안내.xlsx 0 바이트` |
| B-08 keydup 키 수 1·3 처리 | — | B/2-16 | 확인 | Important | core/quality/QualitySql.java:99-102 | 키를 둘까지만 치환함. 그래서 키 셋이면 셋째를 말없이 버리고, 키 하나면 자리표시자 `후보키2` 가 남음. 화면 qKeys 는 자유 쉼표 입력 | p2 `/api/quality/sql {"kind":"keydup",…,"keys":["A","B","C"]}` → `SELECT A, B, COUNT(*) … GROUP  BY A, B` · keys 하나 → `SELECT USER_ID, 후보키2, …` |
| B-09 마스킹 제외 키에 스키마 없음 | — | B/2-04 | 확인 | Important | web/LogicalRoutes.java:109-116 | 제외 키가 「표.컬럼」 이라서 한 스키마 컬럼만 빼도 다른 스키마의 같은 컬럼이 UPDATE 에서 빠짐. 개인정보가 안 가려진 채 남음 | p2 `/api/logical/masking {"snapshotId":1,"exclude":[{"table":"TB_USER","col":"USER_NM"}]}` → `APP TB_USER USER_NM excluded=true` · `CMM TB_USER USER_NM excluded=true` · `-- 방언 Oracle, 표 2, 컬럼 6` |
| B-10 08 코드값 조회 DB 오류 500 | — | B/2-05 | 확인 | Important | web/DeliverableRoutes.java:430-435 | IllegalArgumentException 만 잡아서 DB 가 거절한 조회(없는 표 등)가 사유 없는 500 이 됨 | p2 `/api/deliverable/codes/rows {…"table":"NOPE"…}` → `500 {"message":"서버 오류"}`, 로그 `JdbcSQLSyntaxErrorException @ ReadOnlyJdbc$Base.call:176` |
| B-11 코드명 컬럼 비면 범위 초과 | — | B/2-06 | 확인 | Important | core/deliverable/CodeAndLink.java:135-138 | SELECT 는 nameCol 을 빼는데 행 읽기는 그 자리를 그대로 읽어 500 이 됨. 화면 표에서 nameCol 을 비울 수 있음 | p2 `/api/deliverable/codes/rows {…"nameCol":""…}` → `500`, 로그 `IndexOutOfBoundsException @ CodeAndLink.codeDoc:138` |
| B-12 CHECK 를 낱말 맞춤으로 컬럼에 붙임 | — | B/2-18 | 확인 | Important | core/deliverable/Definitions.java:357-362 | 글자 리터럴 안 낱말이 컬럼 이름과 같으면 남의 CHECK 가 03 「제약조건」 에 조용히 찍힘. 리터럴이 컬럼 이름과 같은 경우는 드물어 Critical 이 아니라 Important 로 매김 | p2 `kr.ejg.toolbox.core.deliverable.ChkProbe` → `ID     → [CHECK (STATUS IN ('ACTIVE', 'ID', 'OFF'))]` |
| B-13 COMMENT DDL 소유자 빈 글 | B/1-04 | B/2-08 | 확인 | Minor | core/logical/ColumnInputs.java:62 → Dialect.java:46-62 · CommentDdl.java:42-53 | 소유자(·표)가 비어도 점을 그대로 이어 `.EMP`·`..COL` 이 됨. 이 DDL 은 실행할 수 없는데 응답은 200. 화면에 스키마 칸이 있고 출력에 바로 보여 피할 수 있음 | p1 `P ddl` → `COMMENT ON TABLE  .EMP IS '직원';` `@level0name=N''` · p2 `/api/logical/comments {"csv":"COLUMN_NAME\nUSER_NM\n",…}` → `COMMENT ON COLUMN ..USER_NM IS '사용자명';` `[200]` |
| B-14 COMMENT DDL 예약어 컬럼 안 감쌈 | B/1-03 | — | 확인 | Minor | core/logical/Dialect.java:46·56 | 이름을 감싸지 않아 예약어 컬럼 줄이 DB 에서 문법 오류가 됨. 머리말은 이 줄을 「실행」 으로 셈. 판정이 아예 없어서 B-04 와 자리가 다름 | p1 `P ddl` → `H2 FAIL COMMENT ON COLUMN PUBLIC.EMP.ORDER IS '순서'; :: SQLState 42001` |
| B-15 nopk 표 없이 늘 거절 | — | B/2-02 | 확인 | Minor | core/quality/QualitySql.java:91 | 표가 비었는지 보기 전에 id(table) 를 불러 표 없는 nopk 가 400 이 됨. 화면은 `(테이블)` 이 Number('')=0 으로 첫 표를 실어 보내므로 표가 있는 스냅샷에서는 대개 비켜 감 | p2 `/api/quality/sql {"kind":"nopk","dialect":"oracle","schemas":["APP"]}` → `400 {"message":"SQL 에 그대로 넣을 수 없는 이름: null"}` |
| B-16 매핑 누락을 쓰는 중에 발견 | B/1-12 | B/2-13 | 확인 | Minor | core/deliverable/DeliverableService.java:126 | 문서 차례에 와서야 mapping.of 를 봐서 앞 문서만 쓴 폴더가 남음. 실패 사유는 보임 | p1 `P partial` → `실패 … 매핑에 문서 11 가 없다` `[01_…xlsx, 10_…xlsx]` · p2 프로필에 map-partial 지정 → job `FAILED 53 … 매핑에 문서 02 가 없다`, 폴더에 `01_데이터베이스정의서.xlsx` 만 |
| B-17 머리 글자 중복 시 뒤 열만 | B/1-10 | B/2-14 | 확인 | Minor | core/report/XlsxFiller.java:152-166 | 같은 머리 글이 둘이면 HashMap 에서 뒤 칸이 이기고, 앞 칸은 경고 없이 빔 | p1 `P filler` → `행2: … [2 BLANK ] [3 STRING =1+1]` · p2 `FillProbe` B → `3:  \| 10 \| a \|` |
| B-18 여권·운전면허·TEL_NO 미탐 | B/1-13 | B/2-10 | 확인 | Minor | resources/logical/masking.yaml (kinds·phone columns) | 고유식별정보 가운데 여권·운전면허 종류가 없고 phone 정규식이 `TEL_NO` 를 못 잡음. 표본에 없는 합성 입력뿐 | p1 `P mask` → 후보 넷에 `PASSPORT_NO`·`DRV_LIC_NO` 없음 · p2 masking 후보 여덟에 `TEL_NO`·`PASSPORT_NO` 없음 |
| B-19 StructureLint SCC 재귀 스택 | B/1-11 | — | 확인 | Minor | core/deliverable/StructureLint.java:558-566 | FK 사슬 깊이만큼 재귀함. 표 2만 개 사슬에서 StackOverflowError(극단 입력) | p1 `P chain 3000` → `지적 3001` · `P chain 20000` → `java.lang.StackOverflowError` |
| B-20 TableXlsx TAG 정규식 제곱 시간 | — | B/2-07 | 확인 | Minor | core/report/TableXlsx.java:66 | 닫는 `>` 없는 `<` 연속에서 제곱 시간이라 요청 스레드를 수십 초 잡음. #1 이 버린 PX 정규식과는 다른 자리 | p2 `RegexProbe fill 100000` → `n=100000 IllegalArgumentException after 17322 ms` · `/api/table/export --data-binary @run/tbl.json` → `[400] 50.679782s` |
| B-21 목록 안 null 500 | — | B/2-11 | 확인 | Minor | web/DeliverableRoutes.java:256 · LogicalRoutes.java:244-246 | docs·rules 의 null 원소를 안 걸러 NPE 500. 화면은 null 을 보내지 않음 | p2 `/api/deliverable/build {"docs":[null]}`·`/api/logical/audit {"rules":[null]}` → 둘 다 `500`, 로그 `NullPointerException @ DeliverableRoutes.postBuild:256` · `@ LogicalRoutes.lambda$registerAudit$5:246` |
| B-22 모르는 문서 번호 조용히 성공 | — | B/2-12 | 확인 | Minor | core/deliverable/DeliverableService.java:118-120 | 매핑에 없는 번호를 continue 로 건너뛰고 skipped 에도 안 적음 | p2 `/api/deliverable/build {"docs":["99"]}` → job `DONE "완료 — 0개 + 작성안내"`, `"skipped":[]` |
| B-23 데이터 행 병합 둘째 행부터 없음 | — | B/2-15 | 확인 | Minor | core/report/XlsxFiller.java:96-98 | 양식 데이터 행의 병합을 채운 행에 잇지 않아 첫 행만 병합됨 | p2 `FillProbe` C → `merged: [A2:B2], [A3:B3]`(4·5행 없음) |
| B-24 매핑 columns null 사유 | — | B/2-17 | 확인 | Minor | core/report/Mapping.java:21 | columns 값이 null 이면 사유가 예외 클래스 이름뿐임 | p2 `FillProbe` D → `ValueInstantiationException: … problem: java.lang.NullPointerException` |

n1 = 13 · n2 = 18 · m = 7 · N ≈ 13·18/7 ≈ 33.4 (서로 다른 확인 결함 24 = 13 + 18 − 7)

등급 분포(24): Critical 2(B-01·B-02) · Important 10(B-03~B-12) · Minor 12(B-13~B-24).

## 불확인 목록과 사유

없음. 31 개 지적이 모두 판정자 재실행에서 보고서의 「실제 출력」 과 같은 결함으로 다시 나왔다. 제외 겹침·설계 한계로 뺀 것도 없다.

## 판정 메모

- 다시 매긴 등급
  - B/2-02 → Minor. 보고서 Important. 화면 `fillTables` 의 `(테이블)` 값 `''` 가 `Number('')=0` 이라 첫 표를 실어 보내므로, 표가 있는 스냅샷이면 화면에서는 대개 실패하지 않는다. 라우트 결함 자체는 확인했다.
  - B/2-06 → Important. 보고서 Minor. 500 에 사유가 없고 화면 코드 표에서 nameCol 을 비울 수 있다.
  - B/2-18 → Important. 보고서 Minor. 산출물 값이 조용히 틀리지만 걸리는 입력이 좁다.
  - B/1-04·B/1-03 → Minor. 보고서 Important. 출력 글에 바로 보이고, DB 가 즉시 거절해서 피할 수 있다.
  - B/1-08·B/1-09 → Critical. 보고서 Important. 산출물이 조용히 틀린다. B-01 은 XlsxFiller 주석이 합계·서명란을 예상한다.
- 짝 판단
  - B/1-02 와 B/2-09 는 같은 자리(Masking.id 의 PLAIN 판정)라 짝으로 묶었다.
  - B/1-03(Dialect)은 #1 이 「같은 뿌리」 라 적었다. 하지만 이름을 감싸는 판정이 아예 없는 다른 자리라 따로 셌다. 하나로 보면 n1 = 12, N ≈ 30.9 이다.
- 제외 겹침: B/2-09 의 「대소문자 섞인 이름」 몫은 compare ㉜ 「QualitySql 대소문자 섞인 이름」 과 꼴이 같다. 자리가 Masking 이라 목록 항목 그대로는 아니어서 표시만 하고, 예약어 몫으로 짝을 셌다. 다른 지적은 8절 목록(0-86·0-87·5-100·5-31·㉜)과 겹치지 않는다. 「보류·조건부 행 전부」 는 저장소를 못 읽어 대조하지 못했다.
- 재현 조건
  - p2 Seed 는 스냅샷 5(스키마 null)에서 NOT NULL 로 멈춘다. 보고서대로 1~4 만 들어간 상태에서 돌렸다.
  - B/2-13 은 서버를 띄운 채 복사본 프로필에 `deliverable.mapping` 을 덧붙여 재현했다. 프로필을 다시 읽었다.
- 정리: 띄운 서버 둘(18731·18793)을 껐다. java 명령줄에 judge-B·18731·18793 이 든 프로세스는 남지 않았다. 18643 LISTEN 은 판정자가 띄운 것이 아니라 건드리지 않았다. 원래 탐침 폴더·tree·저장소는 고치지 않았다.
