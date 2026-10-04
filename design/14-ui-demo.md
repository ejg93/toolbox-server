# 설계 14 명세 — UI 데모(2026-10-04)에서 나온 개선

PLAN.md 16장의 행 `0-43`·`1-11`~`1-23`·`2-10`~`2-19`·`4-11`~`4-14`·`6-13`~`6-16`·`7-12`·`V-21`~`V-25` 와 15장 번들 20~25 가 가리키는 상세다. 행에는 요지만 있고 코드 꼴·화면 배치·실측·근거는 여기 있다. 항목 id `U-*` 는 이 문서 안의 이름이다 — 행과의 대응은 4장 맨 앞 표.
egov 공통컴포넌트 실데이터(Oracle 컨테이너 + 소스 사본)로 Puppeteer 데모를 돌리며 사용자가 짚은 것, 그리고 그 과정의 실험으로 드러난 결함을 모았다. 사용자 결정은 3장과 8장에 「사용자」 로 표시했다.
같이 둔 문서: `design/14-standard-research.md`(공공 DB 산출물 표준 조사 — 원문 근거) · `design/14-feasibility.md`(역설계 가능 판정).
줄 번호는 HEAD `ef8bd33` 기준이다. 실행 때 다시 잰다.

## 1. 목표와 범위

- **목표**: egov 실데이터로 화면을 돌려 보며 사용자가 짚은 개선과 실험으로 드러난 결함·빈틈 18건을, 실행 담당이 파일·줄 단위로 따라 칠 수 있게 적고 번들 여섯으로 나눈다.
- **완료 시 체감**: 스냅샷을 멈출 수 있고 어디에 저장됐는지 보인다 · 작업을 멈춰도 DB 가 안 죽는다 · SQL 스니펫은 복사 전용으로 돌아간다 · 엑셀 병합 표를 그대로 붙인다 · 정의서가 공식 기준(행안부고시 제2025-19호)의 열 이름·표기를 따르고, 모르는 칸을 아는 것처럼 채우지 않는다 · JPA 프로젝트도 CRUD 매트릭스가 나온다 · JSP 포매터·INSERT 생성이 한 번에 한 가지만 보인다.
- **In scope**: 아래 4장의 U-0 ~ U-15.
- **Out of scope**: 번들 순서·행 번호 · `pure/`·portfolio 수정(지시문만 남긴다) · 코드 검사 JPA 규칙 · CRUD 생성기 JPA 템플릿 · 화면에서 프로필 접속(url·user) 추가 · 화면에서 대상 표·스냅샷 범위 고르기(YAML 로만 — 사용자 결정) · 산출물 화면 품질 SQL 「실행」(`deliverable_sql.html` `runSql`) · 「xlsx 파일 → html」 · 스냅샷 일시정지 · **물리·논리 ERD**(사용자 결정 — 안 만든다) · 오너십·업무규칙 정의서.

### 항목 상태

| id | 무엇 | 상태 |
|---|---|---|
| U-0 | 「DB 브라우저」 → **「DB 스냅샷 · DTO 생성」**, 카드·DTO 설명 | 확정 |
| U-1a | 스냅샷 완료 문구 — #id·걸린 시간·저장 위치 | 확정 |
| U-1b | 스냅샷 중지 + 표 단위 진행률 | 확정(U-11 뒤) |
| U-2 | 산출물 대상 표 거르기 — **프로필 YAML `deliverable.filter`** | 확정(사용자가 화면 대신 YAML 로 바꿈) |
| U-3 | SQL 스니펫 화면 백엔드 기능 제거 | 확정 |
| U-4 | 표 편집기 — 엑셀 병합 표·정렬 붙여넣기 | 확정. 실물 클립보드 픽스처는 사람 몫(⑤) |
| U-5a | 조사 문서 보존 | ① 끝, ② 번들 때 |
| U-5b | 메타 수집 보강 — FK 규칙·인덱스 정렬·CHECK·용량 | 확정 |
| U-5c | 표준 열 이름·표기·09 구조 | 확정(표준 이름, Not Null 여부 Y = Nullable, 09 항목 한 줄) |
| U-5d | 등급 드러내기 — `00_작성안내.xlsx` 한 장 + 관리 열 기본값 빈칸 | 확정 |
| U-5e | 없는 문서 — **상관도·엔터티정의서·애트리뷰트정의서**(역설계본). ERD 는 안 만든다 | 확정 |
| U-5f | 선언 안 된 FK 를 매퍼 조인에서 추정 — 부모가 뚜렷한 것만 04, 나머지는 「관계 후보」 시트 | 확정(egov 실측으로 규칙을 세움) |
| U-5g | 개인정보 여부 추정 | 확정 |
| U-6 | 프로그램 분석 JPA 저장소 해석 — Spring Data·`@Query`·`EntityManager` + QueryDSL 기본 꼴, 표본 둘 | 확정 |
| U-7 | 스냅샷 거른 범위 **기록만**(화면 입력란 없음) | 확정 |
| U-8 | 첫 사용 — 결함 둘 + 안내 둘 | 확정 |
| U-9 | JSP 포매터 탭 둘 — 「붙여넣기」·「폴더 일괄」(좌 목록 + 우 상세), 「결과 → 입력」 뺌, 덮어쓰기 확인 창 | 확정 |
| U-10 | 개발자 도구 INSERT 생성 탭 둘 — 「CREATE 문에서」·「스냅샷에서」, 공통 옵션 줄 | 확정 |
| U-11 | **결함**: 작업 취소(스레드 인터럽트)가 H2 를 닫는다 | 확정(실험으로 드러남) |
| U-12 | 벤더 딕셔너리 SQL 이 실패하면 JDBC 뼈대로 물러서고 경고를 남긴다 | 확정 |
| U-13 | 접속 실패 안내 — 드라이버 없음·드라이버와 DB 판 불일치 | 확정(안내 줄은 U-14 의 `V-21` 이 뜬 실제 오류문으로만 채운다) |
| U-14 | 옛 판 DB **전 기능** 검증 — Oracle 11g XE·MySQL 5.7·8.0·MSSQL 2017·PostgreSQL 12 | 확정(번들 20 바로 뒤, `--full` 에 든다) |
| U-15 | CRUD 생성기 `egov4` 템플릿 세트 | 확정 |

## 2. 현재 구조 요약

경로는 저장소 루트 `C:/workspace/toolbox-server/` 기준. 줄 번호는 HEAD `ef8bd33`.

**스냅샷** — `tools/db_browser.html` `takeSnapshot()`(237-248) → `POST /api/meta/snapshot`(`web/MetaRoutes.java:30-38`, 202 `{jobId}`) → `core/meta/SnapshotService.run`(38-54) → `MetaSource.collect(scope)`(`core/meta/MetaSource.java:42-61`) → `SnapshotStore.save`(43-60, 한 트랜잭션) → H2 `data/toolbox.mv.db`(`core/db/Db.java:32`, `Db.file()` 69).
- 화면은 `r.jobId` 와 `TB.sse` 반환값을 버린다(242). 중지 버튼 없음. done 은 `data.tables` 만 쓴다(244).
- `checkCancelled()` 는 수집 앞뒤 둘뿐(44·47). 진행률은 5→20→80→100.
- 표 하나에 `DatabaseMetaData` 호출 6 + 벤더 `UNIQUES` 문 1(`core/dialect/*MetaSource.loadConstraints`). 조회 시간 제한은 어디에도 없다(`setQueryTimeout` 은 `SqlRunner:53` 뿐).
- `Job`(`core/job/Job.java`)에 시작·종료 시각 없음. 취소는 `requestCancel()` → `Future.cancel(true)`(210-223). `JobCancelledException` 은 unchecked(`JobContext.java:48`).
- `snapshot` 표 열: id·profile·conn_id·taken_at·note·db_version·schemas(`db/migration/V001__init.sql:41-51`). 찍을 때의 scope 는 안 남는다.
- 마이그레이션은 `core/db/Migrator.java` — 적용된 파일의 SHA 가 바뀌면 기동 거절(83-108). `released-baseline` 파일은 아직 없다(첫 반입 전).
- **결함(실험으로 확인, U-11)**: H2 URL 이 `jdbc:h2:file:` 다. H2 2.3.232 에서 쓰는 도중 작업 스레드가 인터럽트되면 `MVStoreException: Reading from file … failed` 뒤 **DB 전체가 닫힌다**(「The database has been closed [90098]」) — 열어 둔 `keeper` 접속까지 죽어 서버를 다시 켜야 한다. `jdbc:h2:retry:`·`jdbc:h2:async:` 는 같은 실험 세 번 모두 살아남았다(8장 ①). 지금 화면에서 `DELETE /api/jobs` 를 부르는 곳은 없어 잠복해 있다 — U-1b 의 중지 버튼이 이것을 깨운다.
- **함정**: HtmlUnit 4.11.1 은 `EventSource` 가 없어 `TB.sse` 가 failed 를 낸다(`common.js:135`) — 스냅샷 클릭은 HtmlUnit 스모크로 못 잰다. 지금 스모크도 스냅샷은 생 HTTP 로 찍는다(`SmokeHtmlUnitTest:323`).

**프로필** — `profiles/<이름>.yaml`(사람이 `example.yaml` 을 복사). 엄격 파싱(`ProfileStore.java:29`, 모르는 키 실패). 활성 이름 = `--profile` → `data/active-profile` → 없음(`cli/Serve.java:46`). 서버는 부를 때마다 파일을 다시 읽는다(`web/App.java:124-127`).
- 활성 프로필이 없으면 `/api/conn` = `[]`, 스냅샷 작업은 FAILED 「활성 프로필이 없다」.
- **결함(실험으로 확인)**: `--profile nosuch`(파일 없음)로 켜도 서버가 뜬다. `/api/ping` 은 `profile: "nosuch"`, `/api/profiles` 는 `active: "nosuch"`, **`/api/conn` 은 500 「Server Error」**(로그 `WARN io.javalin.Javalin - Uncaught exception`), 스냅샷은 202 를 받고 작업 안에서 실패. `data/active-profile` 에 `nosuch` 가 적혀 다음 기동도 같다.
- **결함**: `db_browser.html:153` — `active == null` 이면 select 가 첫 이름을 고른 것처럼 보이지만 서버는 활성 없음.
- `ProfileStore.saveCodeCheck`(91, 128-129)가 `Profile` 을 통째로 다시 짓는다 — `Profile` 에 필드를 더하면 여기도 고쳐야 한다.
- README 에 「example.yaml 복사」 안내 없음(안내는 `profiles/example.yaml:4-10` 주석뿐).

**산출물** — `web/DeliverableRoutes.java` `BuildRequest`(30-33, 표 목록 없음) → `core/deliverable/DeliverableService.build`(52-102) → `Definitions.build`(01~04·10·11) · `Standards.build`(05~07) · `CodeAndLink`(08·09) → `core/report/XlsxFiller.fill` + `Mapping`.
- 열 이름의 원본은 `Definitions.COLS_*`·`Standards.COLS_*`·`CodeAndLink.COLS_*` → `Forms.all()`. `scripts/make-example-templates.sh` 가 여기서 `templates/deliverable/example/*.xlsx` 와 `mappings/deliverable/example.yaml` 을 만든다.
- **함정**: `XlsxFillerTest.committedTemplatesAndMappingMatchValueColumns`(142-151)가 커밋된 양식·YAML 과 `COLS_*` 를 맞춘다 — 열을 고치면 스크립트를 다시 돌려 같이 커밋해야 한다. `DeliverableRoutesTest`(100-139)는 02 의 최종수정자 위치(열 17)를 박아 둔다.
- 매핑에서 뺀 열은 조용히 안 쓴다. 매핑 키가 양식 머리에 없거나 값 표에 없는 열을 가리키면 예외(`Mapping.check` 56-69).
- 관리 열 규칙 `Definitions.tail()`(184-191): 최초등록일 = `createdAt`, 최종수정일 = `lastDdlAt` 없으면 최초등록일, 변경구분 = 「신규」 고정, 최종수정자 = 작성자 입력. 벤더 수집은 `lastDdlAt` 을 안 채운다(`VendorMetaSource:44-51`).
- 메타모델 빈틈: `ForeignKey`(삭제·갱신 규칙 없음) · `Index`(정렬 없음) · CHECK 없음 · 용량 없음. `JdbcMetaSource.foreignKeys`(175-205)가 `UPDATE_RULE`·`DELETE_RULE` 을, `indexInfo`(229-247)가 `ASC_OR_DESC` 를 안 읽는다. 그래서 04 삭제규칙 `""`·갱신규칙 Oracle `NO ACTION` 고정(131), 10 정렬 `ASC` 고정(164), 11 은 PK·UNIQUE 만.
- 서버 DDL 라우트는 `tables` 를 받는다(61-76). 화면에는 표 고르기 UI 가 없다 — `#ddlTarget` 은 방언 select. CLI 는 `ddl --tables` 만 있고 `deliverable` 에는 없다(`cli/DocCommands.java:47-93`).

**SQL 스니펫** — 백엔드본(2181줄) = 순수본(2082줄) + 덧붙인 여섯 군데: CSS 53-62 · `common.js` 71 · 접속 select 89-95 · 실행 줄 111-117 · `checkDialect();` 1935 · 함수 묶음 2062-2135. 순수본에서 지운 줄은 없다.
- `/api/sql/run` 은 산출물 `runSql`(`deliverable_sql.html:1032`)도 쓴다. `/api/sql/export` 는 화면 중 스니펫만 쓰지만 `MetaRoutesTest:108-112` 가 둘 다 직접 부른다.
- `SqlRunner.run` 은 `CodeAndLink:122`·`CommentApply:54`·`DeliverableRoutes:225`·`InsertRoutes:122` 가 쓴다.
- 스니펫 표본 `scripts/puppeteer/corpus-snippets.js` 는 `SNIPPETS`·`query(p)` 만 쓴다 — 접속·실행 버튼과 무관.

**표 편집기** — `tools/table_builder.html`. 모델 `G`(173). `importHtml(src)`(430-497): 첫 `<table>` 에서 colspan·rowspan·정렬·th/td 만 읽고 셀 **안쪽 HTML 은 `innerHTML` 그대로** 보존(453). caption·colgroup 너비·table class 도 읽는다. `snapshot()`(되돌리기)을 부른다(468).
- 붙여넣기 리스너(930-942)는 `getData('text')`(TSV)만 읽어 `pasteGrid`(912-929)로 **선택 칸부터** 덮어쓴다. 병합이 풀린다.
- 이 함수들은 순수본과 같다(백엔드본 차이는 common.js·xlsx 버튼·sandbox·`captionText` 뿐).
- 새 JS 규칙(PLAN 5장 #10, `PLAN.md:218`): `tools/<도구>_ext.js`, ES5, `defer`, `innerHTML` 에는 `''` 만(`ToolsFolderTest:81-100`).

**JSP 포매터** — `tools/jsp_formatter.html`(898줄) = 순수본(745줄) + 백엔드가 더한 네 군데: CSS 49-56 · `common.js` 58 · 폴더 줄 `.dirbar` 79-86 · 폴더 일괄 JS 678-813(`DIR`·`dirPreview`·`renderDir`·`dirApply`·`loadRecentDirs`). 나머지는 순수본 그대로다.
- 한 화면에 기능 둘이 섞여 있다. 위 줄(정리·결과 복사·결과 → 입력·비우기)은 붙여 넣은 글 하나를 정리하고, 둘째 줄(폴더·미리보기·적용)은 폴더 아래 `*.jsp` 를 통째로 다룬다.
- **함정**: 폴더 표(`renderDir` 734-773)가 붙여넣기의 「비교」 탭 자리 `#cmp` 에 그려지고 배지도 같이 쓴다 — 비교 탭이 상황마다 다른 것을 보여 준다. 순수본의 여러 파일 끌어다 놓기(`renderBatch` 646-663)도 `#cmp` 를 쓴다.
- 폴더 행은 `{rel, path, encoding, lineEnding, risk, unbal, changed, out, checked}` 만 든다(708-716). 원본 글과 `compareDoc` 결과는 버린다 — 지금은 파일별 위험 지점을 볼 길이 없다(「하나씩 열어 비교 탭을 볼 것」 이라고만 적혀 있다).
- 「적용」 은 확인 없이 바로 쓴다(`dirApply` 774-797, `/api/fs/write` — 서버가 먼저 `out/<프로필>/<시각>/backup` 에 백업).
- 붙여넣기 비교는 `renderCompare(r)`(527-585)가 `#cmp` 에 `innerHTML` 로 그리고, 항목 클릭은 `jumpLine`(595-603)이 `#in`·`#out` 으로 뛴다 — 둘 다 대상이 박혀 있어 다른 칸에 못 쓴다. `scrollTaToLine(ta, line)`(587-594)은 textarea 를 받으니 다시 쓸 수 있다.
- 물고 있는 테스트: `SmokeHtmlUnitTest.jspFormatterFolderBatch`(262-300) — `#dir` 기본값, `#dirPreview`·`#dirApply` 클릭, `#dirTable tr` 3, `#dirMsg` 가 「적용 대상 2개」 를 포함·「적용 2/2」 로 시작. 표본 `scripts/puppeteer/corpus-jspfmt.js` 는 `formatJsp`·`compareDoc` 만 부른다.

**개발자 도구 INSERT 생성 탭** — `tools/dev_tools.html` `#page-dummy`(308-352) + `tools/dev_tools_ext.js`(296줄). `dev_tools.html` 은 순수본 + 백엔드 덩어리(CSS 30-46 `.ext-*`, 「스냅샷/접속에서」 줄 327-341 등).
- 한 탭에 생성 길이 둘이고 결과 칸 `#dummy_out` 하나를 같이 쓴다.
  - 첫째 줄(순수본): 건수 `#dummy_count` · DB `#dummy_db`(oracle·mysql·pg·mssql·sybase) · 날짜 범위 `#dummy_years` · `#chk_commit` · `#chk_merge` · 「▷ INSERT 생성」(`genFromCreate()`) · 복사 · 지우기. 입력은 `#create_input` 하나.
  - 둘째 줄(백엔드, 327-341): `#ins_snap` · `#ins_table`(datalist `#ins_tables`) · `#ins_conn`(FK 조회) · `#ins_dialect`(oracle·tibero·postgresql·mariadb·mssql·sybase) · `#ins_csv`(켜면 `#create_input` 을 CSV 값 행으로 읽는다) · 「▷ 서버로 생성」 `#ins_srv_btn` · `#ins_srv_msg` · `#ins_warn`.
- `insRun`(`dev_tools_ext.js:177-192`)은 첫째 줄의 **건수·종료문·UPSERT 를 읽는다**(`rows`·`commit`·`upsert`). 안 읽는 것은 `#dummy_db`·`#dummy_years` 뿐 — 방언은 `#ins_dialect`, 날짜는 서버가 결정적(오늘 − 순번 일).
- **함정**: `genFromCreate`(1254-)가 `#create_input` 이 비면 `#dummy_out` 에 「-- CREATE TABLE 문 입력」 을 쓴다(1261). 이 함수는 첫째 줄 버튼 · `#dummy_db` onchange(312) · 이 탭의 Ctrl+Enter(1983) 가 부른다 — 서버로 만든 결과를 덮어쓴다. 데모에서 사용자가 이걸 겪었다.
- 물고 있는 것: HtmlUnit 은 `dev_tools` JS 를 끈다(`SmokeHtmlUnitTest.JS_OFF`, 38) — 화면 클릭 검증은 `scripts/puppeteer/smoke-devtools.js`(60-72: `switchTab('dummy')`·`#ins_snap`·`#ins_tables`·`#ins_table`·`#ins_srv_btn`·`#ins_srv_msg` 가 「생성함」). 서버는 `InsertRoutes.GenerateRequest`(37-38)와 `InsertGenTest` 가 잰다.

**DB 방언·드라이버·옛 판** — 방언 다섯(`core/dialect/MetaSources.forDialect` 14-22: oracle·tibero·postgresql·mariadb(mysql)·mssql, 그 밖은 `JdbcMetaSource`). 뼈대(표·컬럼·PK·FK·인덱스)는 `DatabaseMetaData`, 코멘트·행 수·생성일·UNIQUE 만 벤더 딕셔너리 SQL(`VendorMetaSource` + 방언 클래스의 `loadComments`·`loadStats`·`loadConstraints`).
- **빈틈**: 벤더 SQL 에 `catch` 가 없다 — 권한·뷰 부족으로 하나라도 실패하면 스냅샷 전체가 FAILED.
- 드라이버: `drivers/` 바로 아래 jar 만 등록(`core/conn/DriverLoader` 40-57, `DriverShim`). 기본 — ojdbc11 23.8·postgresql·mariadb-java-client·mssql-jdbc. `drivers/alt/`(등록 안 됨) — `ojdbc8-23.8.0.25.04.jar`·`ojdbc6-11.2.0.4.jar`·`mysql-connector-j-9.7.0.jar`. 같은 벤더 jar 가 둘이어도 말이 없다(열린 행 8-13).
- 접속 시험 `ConnectionRegistry.test`(97-109)는 실패하면 드라이버 오류문을 비밀번호만 가려 그대로 낸다. 안내 없음.
- 컨테이너로 잰 판은 최신뿐 — `gvenzl/oracle-free:23-slim-faststart`·`postgres:17-alpine`·`mariadb:11`·`mcr.microsoft.com/mssql/server:2022-latest`. **MySQL 은 MariaDB 로만 쟀다.** Tibero 는 골든만.
- 옛 판 이미지(2026-10-04 `docker manifest inspect`): 있음 — `gvenzl/oracle-xe:11-slim`·`:18-slim`·`:21-slim`, `mysql:5.7`·`mysql:8.0`, `mcr.microsoft.com/mssql/server:2017-latest`·`:2019-latest`. `postgres:12-alpine`(11·13 도). Oracle 12c 는 무료 이미지가 없다.

**CRUD 생성기 템플릿 세트** — `templates/gen/egov35/`(본문 ftl 11 + `set.yaml`: `rte: egovframework.rte`·`ee: javax`·`valid: "false"`) · `templates/gen/egov5/set.yaml`(`extends: egov35`, `rte: org.egovframe.rte`·`ee: jakarta`·`valid: "true"`). 화면 `crud_generator` 의 `#set` select 가 `GET /api/generate/templates` 로 폴더를 읽는다. `.gitignore` 32-34 가 `templates/gen/*` 를 막고 둘만 연다.
- **빈틈**: 프로필 `framework` 는 `egov35 | egov4 | spring` 인데 `egov4` 세트가 없다. 전자정부 4.x 는 `org.egovframe.rte` + `javax` 라 두 세트 어느 것과도 다르다.
- 물고 있는 것: `GenTemplatesTest`(46·68 `List.of("egov35", "egov5")`, 골든 `golden/gen/<세트>/`) · `GeneratorCorpusTest:80` · `GeneratorJavacTest`(egov35 만 javac — 3.5 jar 표본).

**프로그램 분석** — `core/analyze/`: `AnalyzeRunner` → `MapperIndex`(MyBatis XML → ns.id → `SqlTables.Ref`) + `JavaGraph`(JavaParser 3.26.3 호출 그래프) + `JspLinks`. 문장 참조는 여덟 싱크(`JavaGraph.java:79-80`)와 `@Mapper` 인터페이스(361-366)뿐. JPA 처리는 0. 저장은 `V002__analyze.sql`(`analyze_stmt.resolution VARCHAR(10)`, `analyze_unresolved.kind VARCHAR(20)`).
- 스냅샷은 정합성 탭만 쓴다(`Consistency.of`, `core/analyze/Consistency.java:49`). 분석 실행은 폴더만 받는다.
- 표본에 Spring Data JPA 프로젝트가 없다. `roller` 는 JPA 지만 orm.xml + `EntityManager` 래퍼 꼴이고 레시피가 xml 을 안 가져온다.

## 3. 설계 결정

| # | 결정 | 버린 대안과 이유 |
|---|---|---|
| D1 **[고정]** | 스냅샷 **일시정지는 안 넣는다.** 중지 + 표 단위 진행률만 | 일시정지 — 접속·커서를 무기한 잡고, 멈춘 사이 DDL 이 바뀌면 한 시점 구조가 아니게 된다 |
| D2 **[고정]** | 취소 단위는 **표 하나**. 저장은 한 트랜잭션 그대로 — 반쪽 스냅샷을 안 남긴다 | 다른 스레드에서 접속을 닫아 도는 호출을 끊기 — 드라이버마다 동작이 다르고 걸린 채 남을 수 있다 |
| D3 **[고정]** | 스냅샷에 열을 더할 때는 **새 마이그레이션 파일**(`V003__…`). V001·V002 는 안 고친다 | V001 수정 — `released-baseline` 이 없어 스크립트는 통과하지만, 이미 만든 로컬 `toolbox.mv.db` 가 `Migrator.verifyApplied` 에서 기동 거절된다 |
| D4 **[고정]** | SQL 스니펫은 **그 화면만** 기능을 뺀다. `SqlRunner`·`/api/sql/run`·`/api/sql/export` 는 남긴다(사용자 결정) | 라우트 삭제 — 산출물·논리명·INSERT 와 `MetaRoutesTest` 가 쓴다 |
| D5 **[고정]** | 스니펫 백엔드본 = **순수본 + `common.js` 한 줄**. 이 관계를 테스트로 못 박는다 | 버튼만 숨기기 — 죽은 코드가 남고 읽기 전용 가드 없는 실행 경로가 살아 있다 |
| D6 **[고정]** | 표 편집기는 **클립보드 HTML** 을 읽는다. 「xlsx 파일 → html」 은 안 만든다 | 파일 업로드 — 어느 파일·시트·범위를 고르게 하는 UI 와 POI 읽기가 필요하다. 범위는 사람이 엑셀에서 복사로 이미 고른다 |
| D7 **[고정]** | 엑셀에서 가져오는 것은 글자·셀 안 줄바꿈·병합·정렬뿐. 색·글꼴·크기·굵게·테두리·**열 너비**는 버린다. 머리줄은 굵게로 추측하지 않는다 | 서식 보존 — 붙여 넣을 시스템의 CSS 와 부딪친다. 엑셀 너비는 엑셀 글꼴 기준이라 웹에서 안 맞는다 |
| D8 **[고정]** | 클립보드 표에 **병합이 있으면** 표 전체를 바꾼다(되돌리기 가능). **병합이 없으면** 지금처럼 선택 칸부터 덮어쓰되 **정렬도 같이 넣는다**(사용자 결정 ⑥) — 글은 순수본 `pasteGrid` 가 넣고, 정렬은 ext.js 가 그 뒤 같은 칸에 덧입힌다 | 늘 표 전체 교체 — 있던 표의 한 구석에 값 몇 칸을 붙이는 지금 쓰임이 깨진다 |
| D19 **[고정]** | H2 접속 주소를 `jdbc:h2:retry:` 로 바꾼다(U-11). 같은 `toolbox.mv.db` 파일을 그대로 연다 | `async:` — 실험에서는 똑같이 버텼지만 H2 문서가 「실험적」 이라 적는다 · 작업 취소를 `Future.cancel(false)`(인터럽트 안 함)로 — 인터럽트에 기대는 작업(대기·sleep)이 안 멈추고, H2 를 쓰는 다른 경로(사용자 요청 스레드 인터럽트)는 그대로 남는다 |
| D20 **[고정]** | 산출물 대상 표는 **프로필 YAML `deliverable.filter`**(scope 와 같은 모양)로만 거른다. 화면에서 표를 고르지 않는다. 스냅샷은 전체로 찍고 정의서를 만들 때 거른다(사용자 결정) | 화면 체크 목록 — 사용자: 정규식·조건이 더 정확하다 · 스냅샷 scope 로만 — 거른 스냅샷을 비교·정합성에 쓰면 표가 빠져 보인다 |
| D21 **[고정]** | 스냅샷 범위(scope)는 **기록만** 한다 — 찍을 때 쓴 scope 를 스냅샷에 저장해 목록·상세에 보인다. 범위 설정은 YAML 로만(사용자 결정) | 화면 「이번만 이 표들」 입력란 — 사용자가 뺐다 |
| D22 **[고정]** | 물리·논리 ERD 는 **안 만든다**(사용자 결정). 관계는 04 테이블관계정의서로 갈음한다 | 자바 SVG·화면 배치 — 현장 모델링 도구 몫 |
| D9 **[고정]** | 산출물 양식 = **공식 기준 항목 + 지금 예시 양식에만 있는 확장 열**의 상위 집합. 기준은 행안부고시 제2025-19호 별표1·2·4 와 NIA 품질관리 매뉴얼 v2.1(사용자 결정) | 특정 사업 양식에 맞추기 — 이 도구는 어느 폐쇄망 사업에서나 쓰는 범용이다 |
| D10 **[고정]** | 역설계라 한계 있는 문서는 반드시 있다(사용자 조건). 항목마다 **자동·추정·수동**을 가르고 드러낸다 | 전부 채워 내기 — 모르는 값이 확인된 값처럼 보인다 |
| D11 **[고정]** | 04 관계·10 인덱스·11 제약, 관리 열(등록일·수정일·수정자·변경구분)은 표준 근거가 없지만 **확장으로 남긴다** | 표준에 없다고 빼기 — 사용자가 예외 대비로 남기라고 했다 |
| D12 | 최종수정일·변경구분을 스냅샷 비교로 채우는 일은 **보류**(사용자 결정 — 같은 시스템에 산출물을 두 번 이상 내야 뜻이 있다) | — |
| D13 **[고정]** | JPA 해석은 `JavaGraph` 의 **저장소 계층만** 넓힌다. JavaParser 만 쓴다. 범위는 Spring Data 저장소·`@Query`·`EntityManager` 리터럴 + QueryDSL 기본 꼴(D23) | Criteria·NamedQuery·cascade 까지 — 정적으로 못 읽는 자리가 많다. 미해결 종류로 세기만 한다 |
| D23 **[고정]** | QueryDSL 은 **기본 꼴을 읽는다**(Q클래스 → 엔티티, `selectFrom`·`from`·`join`·`update`·`delete`·`insert`) — D13 의 「세기만」 을 사용자가 뒤집었다(eGovFrame MSA 표본이 QueryDSL 중심). resolution `qdsl` 로 구분한다. Criteria·NamedQuery·cascade 는 여전히 세기만 | QueryDSL 을 미해결로만 — 국내 JPA 프로젝트의 CRUD 가 절반쯤 빈다 |
| D24 **[고정]** | 옛 판 DB 검증은 **전 기능**(지금 최신판 표본 절 전부)을 **번들 20 바로 뒤**에 세운다. 옛 판 클래스가 `DbCorpusBase` 를 상속해 `--full` 에 저절로 든다(사용자 결정) | 맨 마지막에 한 번 — 결함이 몰려 나오고 번들 23·24 의 새 벤더 SQL 을 다시 고친다 · 스냅샷 절만 — DDL·INSERT·품질·스니펫이 옛 판에서 도는지 여전히 모른다 |
| D25 **[고정]** | 선언 안 된 FK 는 **부모가 뚜렷한 쌍만**(한 문장에서 한쪽 PK 전체가 등식에 든 것) 04 에 「추정」 으로 싣는다. 나머지는 작성안내 「관계 후보」 시트(사용자 결정) | 전부 04 에 — 방향이 틀린 관계가 정의서에 섞인다 |
| D14 **[고정]** | 화면 파일 이름 `db_browser.html` 은 안 바꾼다. 보이는 이름만 바꾼다 | 파일 이름 변경 — 런처·스모크·`example.yaml:10` 이 문다 |
| D15 **[고정]** | JSP 포매터는 맨 위 **탭 둘** — 「붙여넣기」(지금의 입력 \| 결과·비교 그대로)·「폴더 일괄」. 폴더 탭은 **왼쪽 파일 목록 + 오른쪽 상세 한 칸**, 상세 안에서 [위험 지점 \| 원본 \| 정리본] 을 탭으로 바꾼다(사용자가 고름) | 3분할(목록 \| 원본 \| 정리본) — 칸이 좁아진다 · 위 목록 + 아래 상세 — 세로가 좁아진다 · 목록만 두고 클릭하면 붙여넣기 탭으로 — 탭을 오가야 한다 |
| D16 **[고정]** | 순수본에서 온 **JS 함수는 한 줄도 안 고친다**(`formatJsp`·`compareDoc`·`renderCompare`·`jumpLine`·`showPane`·끌어다 놓기). 갈라지는 자리는 마크업 감싸기와 CSS 뿐. 폴더 일괄 JS 는 `tools/jsp_formatter_ext.js` 로 옮기고 위험 지점 목록은 거기서 DOM 으로 새로 그린다 | `renderCompare(r, box, jump)` 로 인자를 늘려 같이 쓰기 — 순수본 사본이 갈라져 portfolio diff 를 패치로 못 붙인다(0-40 방식) |
| D18 **[고정]** | INSERT 생성은 탭 안에 **작은 탭 둘** — 「CREATE 문에서」·「스냅샷에서」(사용자 결정). 탭마다 입력·결과 칸·생성 버튼이 따로다. 순수본 `genFromCreate`·`switchTab`·`copyEl` 은 안 고친다 | 결과 칸을 하나로 두고 마지막 생성 길만 표시 — 지금처럼 덮어쓰기가 남는다 |
| D17 | 폴더 상세는 행을 누를 때 **원본을 다시 읽어** `compareDoc(원본, row.out)` 을 다시 계산한다. 행에 원본·비교 결과를 쌓아 두지 않는다 | 검사 때 전부 들고 있기 — 파일 수천 개면 메모리가 수백 MB 로 는다 |

## 4. 실행 스텝

먼저 번들과 행 대응 표, 그 뒤에 항목(U-*)별 명세. 실행 세션은 PLAN.md 의 행을 보고 치고, 코드 꼴·배치·근거는 이 문서의 U-절을 본다.

### 번들 계획 — 행 번호와 순서(설계 14)

행 번호는 PLAN 16장의 끝 번호를 잇는다(지금 끝: `0-42`·`1-10`·`2-8`·`4-10`·`6-12`·`7-11`·`8-14`·`V-20`). 접두 — 0 기반 · 1 DB·스냅샷·수집 · 2 산출물 · 4 화면 도구 · 6 분석 · 7 생성기 · V 실물 표본.

| 번들 | 순서 | 무엇 | 설치·선행 |
|---|---|---|---|
| 번들 20 | `0-43`→`8-13`→`8-14`→`1-11`→`1-12`→`1-13`→`1-14`→`1-15`→`1-16`→`1-17` | **결함·스냅샷** — H2 취소 결함 · 잔손 둘(열린 행) · 표 단위 진행률·중지·완료 문구 · 벤더 SQL 물러서기 · 범위·경고 기록 · 없는 프로필 · 화면 이름 「DB 스냅샷 · DTO 생성」. 새 의존성 0 | 없음. `--full` 은 Docker + 이미지 넷. 화면 검증은 Puppeteer(집) |
| 번들 21 | `V-21`→`V-22`→`1-18` | **옛 판 DB 전 기능**(사용자 2026-10-04 — 여태 최신판만 쟀다. 기준선을 먼저 세우고 뒤 번들은 옛 판까지 든 `--full` 로 닫는다) — Oracle 11g XE·MySQL 5.7·8.0·MSSQL 2017·PostgreSQL 12 에서 지금 최신판 표본이 도는 절 전부(INSERT·적재·메타·COMMENT·diff·품질·스니펫·DDL 왕복·마스킹) + 접속 실패 안내 줄. 새 의존성 0 | 번들 20 머지 뒤(물러서기가 있어야 옛 판에서 스냅샷이 선다). **네트워크**(이미지 다섯 — 수 GB, `docker-pull-old.sh`) · `drivers/alt/` 가 채워져 있을 것(`bundle-fetch.sh`) · 메모리 — 컨테이너는 한 번에 하나 |
| 번들 22 | `4-11`→`4-12`→`4-13`→`4-14`→`7-12` | **화면 정리** — 스니펫 복사 전용 · JSP 포매터 탭 둘 · INSERT 생성 탭 둘 · 엑셀 병합·정렬 붙여넣기 · `egov4` 세트. 새 의존성 0 | 번들 20 머지 뒤(같은 스모크 파일). 엑셀 실물 픽스처(⑤)는 없으면 손 픽스처로 |
| 번들 23 | `1-19`→`1-20`→`1-21`→`1-22`→`1-23`→`V-23` | **메타 수집 보강** — FK 삭제·갱신 규칙 · 인덱스 정렬 · CHECK · 용량 + 컨테이너 대조(최신 넷 + 옛 판 다섯). 마이그레이션 파일이 는다 | 번들 21 머지 뒤. Docker + 이미지 아홉 |
| 번들 24 | `2-10`→`2-11`→`2-12`→`2-13`→`2-14`→`2-15`→`2-16`→`2-17`→`2-18`→`6-13`→`2-19`→`V-24` | **정의서 표준** — 표준 열 이름·표기(Not Null 여부 Y = Nullable·PK01·AK_1-01) · 08·09 · 관리 열 빈칸·`00_작성안내` · 개인정보 여부 · `deliverable.filter` · 문서 16·17·18 · 매퍼 조인으로 추정한 관계(뚜렷한 것만 04) + 실물 표본 | 번들 23 머지 뒤 |
| 번들 25 | `6-14`→`6-15`→`6-16`→`V-25` | **JPA 분석** — `JpaIndex` · 그래프 연결 · QueryDSL 기본 꼴 · 표본 둘(shopizer·eGovFrame MSA 공통컴포넌트). JavaParser 만 | 번들 20 머지 뒤면 언제든(22~24 와 독립). **네트워크**(`corpus-fetch.sh`) |

- **머지 위임**: 여섯 다 「없음 — PR 열고 멈춘다」 로 적는다. 사용자가 위임을 주면 그때 표를 고친다.
- 번들 22·25 는 다른 번들과 독립이다(20 뒤면 순서를 바꿔도 된다). 21 → 23 → 24 는 이 순서다.

**행 → 명세 대응**(행 본문은 오른쪽 U-절에서 옮긴다)

| 행 | 청크 | 출처 | 선행 | 건드리는 자리 | 강제 지점 |
|---|---|---|---|---|---|
| `0-43` | 작업 취소가 H2 를 닫는다 — `retry:` | U-11 | — | `core/db/Db`, 새 `DbInterruptTest` | `DbInterruptTest`(`file:` 로 되돌리면 빨강) |
| `8-13`·`8-14` | (이미 있는 열린 행 — 그대로) | PLAN 647·648 | — | 행대로 | 행대로 |
| `1-11` | 스냅샷 수집 — 표 단위 진행률·취소 + 걸린 시간·저장 위치 | U-1b ①·U-1a 서버 쪽 | `0-43` | `core/meta/MetaSource`·`SnapshotService`·`SnapshotStore`, 새 `SnapshotServiceTest`, `MetaRoutesTest` | `SnapshotServiceTest` ①②③ |
| `1-12` | 벤더 문 물러서기·시간 제한 — 도우미 + Oracle | U-12 ①·U-1b ② | `1-11` | `core/dialect/VendorMetaSource`·`OracleMetaSource`, `core/meta/MetaSource`(`warnings()`)·`SnapshotService`, 새 `VendorFallbackTest` | `VendorFallbackTest`(H2 에 Oracle 수집기 → 뼈대 + 경고 셋) |
| `1-13` | 벤더 문 물러서기 — PG·MariaDB·MSSQL | U-12 ② | `1-12` | `core/dialect/{Postgres,Maria,Mssql}MetaSource`, 방언 `*MetaSourceTest` | 최신판 넷 `warnings()` 비어 있음 + `golden/meta/*` 불변 |
| `1-14` | 스냅샷에 범위·경고 기록 | U-7·U-12 ③ | `1-12` | `db/migration/V003__snapshot_scope.sql`, `SnapshotStore`·`SnapshotService`, `SnapshotStoreTest`·`MetaRoutesTest` | scope·warnings 왕복, 옛 행 null |
| `1-15` | 없는 프로필 — 기동 거절·활성 없음·400 | U-8 8e·8d | — | `cli/Serve`, `web/App`, `README.md`, `MainTest` | 종료 2 · `/api/conn` 200 `[]` |
| `1-16` | 화면 「DB 스냅샷 · DTO 생성」 — 이름·중지·완료 문구·프로필 안내·표 0개·범위 줄 | U-0·U-1a 화면·U-1b ③·U-8 8a·8b·8c·U-7 화면 | `1-11`·`1-14`·`1-15` | `tools/db_browser.html`·`index.html`·`deliverable_sql.html`(720 문구), 새 `scripts/puppeteer/smoke-dbbrowser.js`, `SmokeHtmlUnitTest` | 스모크(활성 없음 → `#profile` `""`) + Puppeteer(중지·완료 문구) |
| `1-17` | 스냅샷 라벨 「· 거름」·「· 경고」 | U-7 라벨 | `1-14` | `tools/common.js`(`TB.snapLabel(s)`) + 스냅샷 select 를 채우는 다섯 곳(`deliverable_sql.html`·`logical_name.html`·`crud_generator_ext.js`·`program_analysis_ext.js`·`dev_tools_ext.js`)이 그것을 부른다 — 한 줄씩 | 스모크 — 거른 스냅샷의 option 글에 「거름」 |
| `V-21` | 옛 판 표본 — 틀·접속·적재·메타 | U-14 ① | `1-13` | `web/DbCorpusBase`(`goldenKey()`·`skipped()`)·`DbCorpus`(`connectWith`), 새 옛 판 클래스 다섯(얇음), 새 `scripts/docker-pull-old.sh`, 골든 `golden/corpus/db-<판>.json`·`dbold-conn-errors.txt` | A — 판 다섯에서 스냅샷 예외 0·표 수·컬럼 수·PK(DDL 대조) |
| `V-22` | 옛 판 표본 — 나머지 절 기준선 + 앱 결함 고치기 | U-14 ② | `V-21` | 옛 판 클래스 다섯(절 켜기), 걸린 앱 코드(상한 10건), 골든·목록 | 앱 몫 A 0 · 순수본 몫(스니펫·품질 템플릿)은 알려진 목록 |
| `1-18` | 접속 실패 안내 줄 | U-13 | `V-21` | `core/conn/ConnectionRegistry`, 새 `core/conn/ConnHints` + `conn/hints.yaml`, `ConnHintsTest` | 줄마다 `V-21` 이 뜬 실제 오류문 픽스처 |
| `4-11` | SQL 스니펫 복사 전용 | U-3 | — | `tools/sql_snippets.html`, `SmokeHtmlUnitTest`(삭제 1), `ToolsFolderTest`(+1) | `sqlSnippetsIsPurePlusCommonJs` |
| `4-12` | JSP 포매터 탭 둘 | U-9 | — | `tools/jsp_formatter.html`, 새 `jsp_formatter_ext.js`, `SmokeHtmlUnitTest` | `jspFormatterFolderBatch`(행 클릭 → 상세) |
| `4-13` | INSERT 생성 탭 둘 | U-10 | — | `tools/dev_tools.html`·`dev_tools_ext.js`, `scripts/puppeteer/smoke-devtools.js` | Puppeteer — 스냅샷 탭 결과가 안 덮인다 |
| `4-14` | 표 편집기 엑셀 병합·정렬 붙여넣기 | U-4 | — | 새 `tools/table_builder_ext.js`, `table_builder.html`, `SmokeHtmlUnitTest`, 픽스처 3·골든 1 | `tableBuilderPastesExcelClipboard` |
| `7-12` | `egov4` 템플릿 세트 | U-15 | — | 새 `templates/gen/egov4/set.yaml`, `.gitignore`, `GenTemplatesTest`·`GeneratorCorpusTest` + 골든 | `GenTemplatesTest`(세트 셋) |
| `1-19` | FK 삭제·갱신 규칙 | U-5b FK | `1-14`(V 번호) | `core/meta/ForeignKey`·`JdbcMetaSource`·`SnapshotStore`, V 파일, `Definitions`(R13), 골든 | `DefinitionsTest` R13 새 값 + `golden/meta/*` diff |
| `1-20` | 인덱스 정렬 | U-5b 인덱스 | `1-19` | `core/meta/Index`·`JdbcMetaSource`·`SnapshotStore`, V 파일, `Definitions`(R15) | R15 새 값 |
| `1-21` | CHECK — 모델·저장·Oracle·11 문서 | U-5b CHECK | `1-20`·`1-12` | 새 `core/meta/Check`, `Table`·`SnapshotStore`, V 파일, `OracleMetaSource`, `Definitions`(R16) | R16 에 CHECK 행 |
| `1-22` | CHECK — PG·MariaDB·MSSQL | U-5b CHECK | `1-21` | `core/dialect/{Postgres,Maria,Mssql}MetaSource`, 방언 테스트·골든 | 방언 골든에 `checks` |
| `1-23` | 데이터 용량 | U-5b 용량 | `1-21` | `core/meta/Schema`·방언 넷·`SnapshotStore`, V 파일, `Definitions`(R17) | R17 새 값(권한 없으면 빈칸 + 경고) |
| `V-23` | 수집 보강 실물 표본 | U-5b 검증 | `1-19`~`1-23` | `web/DbCorpusBase` `meta` 절(옛 판 클래스도 같은 절을 탄다), `golden/corpus/db-*.json`(옛 판 것 포함) | A — 예외 0·최신판 `warnings()` 0·CHECK 수 = 딕셔너리 COUNT · B — 규칙·정렬별 건수 |
| `2-10` | 01·02·03 표준 열·표기 + 양식 재생성 | U-5c ① | `1-19`~`1-21` | `core/deliverable/Definitions`, `templates/deliverable/example/*`·`mappings/deliverable/example.yaml`(스크립트), `DefinitionsTest`·`XlsxFillerTest`·`DeliverableRoutesTest`·골든 | R7(Y = Nullable)·R10(`PK01`)·AK·FK 단언 |
| `2-11` | 04·10·11 열 이름 + 05~07 띄어쓰기 | U-5c ②③ | `2-10` | `Definitions`·`Standards`, 양식·골든 | 양식·YAML 일치 테스트 |
| `2-12` | 08 코드설명 + 09 항목 한 줄 | U-5c ④⑤ | `2-11` | `core/deliverable/CodeAndLink`, 양식·골든, `CodeAndLinkTest` | 09 행 수 = 후보 표 컬럼 합 |
| `2-13` | 관리 열 빈칸 + 등급표 + `00_작성안내.xlsx` | U-5d | `2-12` | `Definitions.tail`, 새 `core/deliverable/Grades`, `Doc`·`DeliverableService`·`XlsxWriter`, `GradesTest` | `GradesTest` + R5 새 값 |
| `2-14` | 개인정보 여부 추정 | U-5g | `2-13` | `DeliverableService`·`Definitions`, `DefinitionsTest` | `RRN` 꼴 → `Y`, 추정 건수 |
| `2-15` | `deliverable.filter` — 프로필 | U-2 ① | — | `core/profile/Profile`·`ProfileStore`, `profiles/example.yaml`, `ProfileStoreTest` | filter 왕복·`saveCodeCheck` 뒤 유지 |
| `2-16` | `deliverable.filter` — 거르기·건수 표시 | U-2 ② | `2-15` | `web/DeliverableRoutes`, 새 `Deliverables.filter`, `tools/deliverable_sql.html`, `DeliverableRoutesTest` | 표 하나만 → 02 한 행·01 테이블 수 1 |
| `2-17` | 16 엔터티·17 애트리뷰트 정의서 | U-5e 16·17 | `2-13` | `Forms`, 새 `core/deliverable/Models`, `DeliverableService`, 양식·골든, `ModelsTest` | 골든 `pg-16`·`pg-17` |
| `2-18` | 18 테이블 대 응용프로그램 상관도 | U-5e 18 | `2-17`·`2-16` | `DeliverableService`·`DeliverableRoutes`(`analyzeRunId`)·`deliverable_sql.html`, `DeliverableRoutesTest` | 실행을 주면 18, 안 주면 `skipped` |
| `6-13` | 매퍼 조인 조건 추출·저장 | U-5f ① | — | 새 `core/analyze/SqlJoins`, `MapperIndex`·`AnalyzeStore`, V 파일(`analyze_join`), `SqlJoinsTest`·`AnalyzeCorpusTest` | 골든 `sql-joins.json` + egov 수치 baseline |
| `2-19` | 추정 관계 — 뚜렷한 것만 04, 나머지는 「관계 후보」 시트 | U-5f ② | `6-13`·`2-18`·`2-13` | 새 `core/deliverable/Relations`, `Definitions`(04 「근거」 열)·`DeliverableService`, 양식·골든, `RelationsTest` | 부모 판정 케이스 넷 + 선언 FK 쌍은 안 싣는다 |
| `V-24` | 정의서 표준 실물 표본 | U-5c~f 검증 | `2-10`~`2-19` | `web/DbCorpusBase` 새 절(`@Order(8)` — 7 은 `maskingRuns` 가 쓴다. 옛 판 클래스도 탄다), `golden/corpus/deliverable-*.json` | A — 예외 0·양식 기입 통과·03 행 수 = 컬럼 수·Not Null 여부 `Y` 수 = nullable 컬럼 수·`PK\d\d` 수 = PK 컬럼 수 · B — 문서별 행 수·추정 건수 |
| `6-14` | `JpaIndex` — 엔티티·저장소 색인 | U-6 ① | — | 새 `core/analyze/JpaIndex`, 픽스처 `fixtures/analyze/jpa/`, `JpaIndexTest` | 골든 `jpa-index.json` |
| `6-15` | JPA 문장을 그래프·CRUD 에 잇기 | U-6 ② | `6-14` | `core/analyze/JavaGraph`·`AnalyzeRunner`·`Unresolved`, `JavaGraphTest`·`AnalyzeRoutesTest`, 골든 `jpa-graph.json` | 기존 `java-graph.json`·`analyze-egov*` 불변 |
| `6-16` | QueryDSL 기본 꼴 | U-6 ③ | `6-15` | `core/analyze/JpaIndex`(Q클래스 → 엔티티)·`JavaGraph`, 픽스처·골든 | 픽스처 — `selectFrom`·`join`·`update`·`delete` → 표·CRUD, 모르는 Q 는 `unresolved(querydsl)` |
| `V-25` | JPA 실물 표본 둘 | U-6 ④ | `6-16` | `scripts/corpus-fetch.sh`·`corpus/SOURCES.md`·`corpus/MANIFEST`, `AnalyzeCorpusTest` 새 절 | A — 예외·`parse` 0·엔티티 수·저장소 수 일치 |



### S0 문서 청크 — 끝냄

설계 세션(2026-10-04)이 PLAN.md 15장·16장에 번들과 행을 적고 이 문서들을 커밋했다. 실행 세션은 PLAN 「번들」 표의 번들 20 부터 친다.

### 항목별 명세

항목끼리는 따로 칠 수 있다. 의존은 항목마다 적었다. 「가름」 은 위 행 대응 표가 확정한 것이다 — 표와 다르면 표를 따른다.

### U-0 DB 브라우저 화면 이름·DTO 설명 — 확정(이름 「DB 스냅샷 · DTO 생성」)

- **사실**: 이름 「DB 브라우저」 가 DTO 생성을 못 담는다. 런처 카드 설명(`tools/index.html:89`)에 DTO 가 없다. `#dtoPkg`(`db_browser.html:107`)는 DTO 파일 맨 위 `package` 줄에만 쓰인다(`core/gen/DtoGenerator.java:95-96`). 「파일로 저장」 은 `out/<프로필>/<시각>/dto/` 에 평평하게 쓴다(`web/GenRoutes.java:121-127`).
- **대상**: `tools/db_browser.html`(6 `<title>`, 54 `<h1>`, 107 placeholder, 115 저장 버튼 옆) · `tools/index.html`(88 이름, 89 설명) · `tools/deliverable_sql.html:720`(「DB 브라우저에서 찍는다」 문구).
- **변경**:
  - 이름 세 곳을 **「DB 스냅샷 · DTO 생성」** 으로(`<title>`·`<h1>`·런처 카드). `deliverable_sql.html:720` 문구는 「(스냅샷 없음 — DB 스냅샷 · DTO 생성 화면에서 찍는다)」.
  - 카드 설명: 「DB 메타데이터를 접속별 스냅샷으로 떠서 테이블·컬럼·제약을 보고, 고른 테이블을 DTO(record·bean·eGov VO)로 만들고, 두 스냅샷을 구조로 비교한다.」
  - `#dtoPkg` placeholder: 「패키지 — 파일 맨 위 package 줄에만 쓴다(예: egovframework.xxx.service)」.
  - `#dtoMsg` 저장 성공 문구에 경로가 이미 오면 그대로. 버튼 줄 아래 안내 한 줄: 「파일로 저장은 out/ 아래 dto 폴더에 쓴다 — 프로젝트 소스에는 넣지 않는다」.
- **검증**: `LauncherTest`(카드 11) · `SmokeHtmlUnitTest.opensWithoutScriptErrors`·`dbBrowserMakesDtoFromDdl` 초록. 이름을 박은 테스트는 없다.
- **의존**: 없음. U-3 뒤에 치면 `sql_snippets.html:2085` 의 같은 이름은 이미 사라져 있다(앞에 치면 그 줄도 고친다). PLAN.md·`profiles/example.yaml:10` 의 「db_browser」 언급은 화면 이름을 같이 적는다(파일 이름은 그대로 — D14).

### U-1a 스냅샷 완료 문구 — 확정

- **대상**: `core/meta/SnapshotService.java` `run`(38-54) · `core/meta/SnapshotStore.java` · `tools/db_browser.html` `takeSnapshot` done 분기(244).
- **변경**:
  ```java
  // SnapshotStore — H2 파일 자리. Db.file() 을 그대로 내준다
  public Path file() { return db.file(); }

  // SnapshotService.run — 걸린 시간은 서버가 잰다
  long t0 = System.nanoTime();
  …
  return Map.of("snapshotId", id, "schemas", schemas.size(), "tables", tables,
          "elapsedMs", (System.nanoTime() - t0) / 1_000_000, "store", store.file().toString());
  ```
  ```js
  // db_browser.html done — 값은 msg() 가 textContent 로 넣는다
  msg('snapMsg', '완료 — 스냅샷 #' + data.snapshotId + ' · 테이블 ' + data.tables + ' · '
      + Math.round(data.elapsedMs / 1000) + '초 · 저장 ' + data.store + ' (H2)', 'ok');
  ```
  스냅샷은 폴더에 파일로 생기지 않는다 — H2 한 파일의 행이다. 문구 끝 「(H2)」 로 밝힌다.
- **검증**: `MetaRoutesTest.snapshotFlow`(68)에 단언 둘 — `result.elapsedMs >= 0`, `result.store` 가 `toolbox.mv.db` 로 끝난다. 화면 문구는 Puppeteer(`scripts/puppeteer/smoke-dbbrowser.js` 신설 — U-1b 와 같이 쓴다): `#snapMsg` 가 `완료 — 스냅샷 #` 으로 시작하고 `toolbox.mv.db` 를 포함.
- **의존**: 없음. CLI `MetaCommands:88` 은 세 필드만 읽어 영향 없다.

### U-1b 스냅샷 중지 + 표 단위 진행률 — 확정

- **대상**: `core/meta/MetaSource.java` `collect`(42-61) · `core/meta/SnapshotService.java` · `core/dialect/{Oracle,Postgres,Mssql,Maria}MetaSource.java`(벤더 `PreparedStatement`) · `tools/db_browser.html`(73-80 카드, 140 state, 237-248).
- **변경**:
  ```java
  // MetaSource
  interface TableListener { void onTable(int done, int total, String schema, String table); }

  default List<Schema> collect(Scope scope) throws SQLException {
      return collect(scope, (done, total, schema, table) -> { });
  }

  /** 1패스: 스키마마다 listTables → scope.accepts 로 거른 목록(순서 그대로)을 모아 total 을 센다.
   *  2패스: 표를 읽기 「전에」 listener.onTable 을 부른 뒤 loadIndexes(loadConstraints(loadColumns(t))).
   *  스키마 끝의 loadStats(loadComments(…)) 와 accepts 재적용은 지금과 같다 — 결과 순서·내용 불변 */
  default List<Schema> collect(Scope scope, TableListener listener) throws SQLException { … }

  // SnapshotService.run
  List<Schema> schemas = sources.apply(c.dialect(), conn).collect(scope, (done, total, schema, table) -> {
      ctx.checkCancelled();                       // unchecked JobCancelledException
      ctx.progress(20 + (total == 0 ? 0 : 60 * done / total), "테이블 " + done + "/" + total + " — " + table);
  });
  ```
  - 벤더 문 시간 제한 60초는 U-12 의 `VendorMetaSource.prepare` 가 건다(행 `1-12`). 시간 초과는 U-12 의 물러서기로 받는다. `DatabaseMetaData` 호출에는 시간 제한을 걸 수 없다 — 표 단위 확인으로 받는다(D2).
  - 화면: `state.job = null` 추가. `takeSnapshot` 이 `state.job = { id: r.jobId }` 를 들고, 카드에 `<button type="button" id="snapStop" disabled>중지</button>`. 진행 중에만 켠다. 클릭 → `TB.api('/api/jobs/' + state.job.id, { method: 'DELETE' })`(`common.js` `api` 가 `opts.method` 를 받는다). `cancelled` 이벤트 → 「중지함 — 저장하지 않았다」, done·failed·cancelled 모두에서 `#snapStop` 끄고 `#snapTake` 켠다.
- **검증**:
  - 새 `core/meta/SnapshotServiceTest` — 가짜 `MetaSource`(표 5, 표마다 래치로 멈춤) + **`Db.open(@TempDir)` 파일 DB**(in-memory 는 U-11 결함을 못 잰다). ① progress 메시지에 `테이블 1/5` … `5/5` ② 표 2 에서 `jobs.cancel(id)` → 상태 CANCELLED, `snapshots.list()` 크기 0 ③ 바로 다시 찍으면 DONE(취소 뒤 DB 가 살아 있다 — U-11).
  - `collect` 결과 불변: `golden/meta/*.json` 다섯·`SnapshotStoreTest`·방언 `*MetaSourceTest` 가 그대로 초록.
  - 화면: Puppeteer `smoke-dbbrowser.js` — 찍기 → 진행 문구에 `테이블 ` → 중지 클릭 → `#snapMsg` 「중지함」.
- **의존**: **U-11 먼저**(중지 버튼이 인터럽트를 부르면 지금 H2 가 닫힌다). U-1a 와 같은 파일 — 같은 묶음에 둔다.
- **실패 사다리**: U-11 을 친 뒤에도 ③ 이 깨지면 → `SnapshotService` 가 저장 단계는 취소를 안 받게 한다(저장 직전 `ctx.checkCancelled()` 가 마지막 확인, 저장은 `Thread.interrupted()` 로 플래그를 내려 두고 끝난 뒤 되올린다) + 진행률 80 부터 화면 중지 버튼을 끈다.
- **가름**: ① 서버 = 행 `1-11` ② 벤더 시간 제한 = 행 `1-12`(U-12 와 같이) ③ 화면 + Puppeteer = 행 `1-16`.

### U-2 산출물 대상 표 거르기 — 프로필 YAML `deliverable.filter` — 확정

- **사실**: 지금은 스냅샷의 표 전부로 01~11 을 만든다. 거르려면 프로필 `scope` 로 「정의서용 스냅샷」 을 따로 찍어야 하는데, 그 스냅샷을 비교·논리명·정합성에 쓰면 거기서도 표가 빠진다. `profiles/example.yaml` 메모(2026-09-27): 「산출물 대상 표는 산출물 화면에서 고정 목록으로 — 아직 설계 전」. 사용자 결정(2026-10-04): 화면에서 고르지 않고 YAML 의 정규식·조건으로 거른다(D20).
- **대상**: `core/profile/Profile.java:51` `Deliverable` · `core/profile/ProfileStore.java:128-129`(`saveCodeCheck` 재조립) · `web/DeliverableRoutes.java`(95-130 build) · `tools/deliverable_sql.html`(빌드 카드에 건수 한 줄) · `profiles/example.yaml`.
- **변경**:
  ```java
  // Profile — scope 와 같은 Scope 레코드를 그대로 쓴다(core/meta/Scope.java:19). schemas 도 걸러진다
  public record Deliverable(String author, String org, String templateDir, String mapping, Scope filter) {}

  // DeliverableRoutes.build — 스냅샷을 넘기기 전에 거른다. filter 가 null 이면 전부
  List<Schema> schemas = Deliverables.filter(snap.get(), profile.deliverable() == null ? null : profile.deliverable().filter());
  if (schemas.stream().allMatch(s -> s.tables().isEmpty())) → 400 「deliverable.filter 에 맞는 표가 없다」
  ```
  - 거르는 함수는 `Scope.acceptsSchema`·`Scope.accepts(Table)`(57-87)를 그대로 쓴다 — 규칙(include 가 exclude 보다 먼저, 대소문자 무시, regex 는 로드 때 검증)이 스냅샷 scope 와 같다. `Deliverables.filter` 는 `core/deliverable/` 의 정적 함수 하나(새 파일 또는 `DeliverableService` 안).
  - 거른 `schemas` 를 `DeliverableService.build` 에 넘긴다 — 01 테이블수·05~07·08 후보·09 후보도 거른 표 기준이 된다.
  - 응답 `{jobId, dir}` 에 `tables`(거른 뒤 수)·`snapshotTables`(스냅샷 전체 수)를 더한다. 화면 `build()`(846-862)가 `#buildMsg` 에 「만드는 중 — 표 40 / 스냅샷 184 (deliverable.filter)」. 거르지 않았으면 「표 184」.
  - DDL 카드(`ddl`)·08 코드 후보·09 연계 후보 라우트도 같은 필터를 쓴다(정의서와 같은 대상).
  - `saveCodeCheck` 의 `Profile` 재조립에 새 필드를 넘긴다(빠뜨리면 코드 검사 저장 때 filter 가 지워진다).
  - `example.yaml` `deliverable:` 아래:
    ```yaml
      # 정의서(01~11) 대상 표 — 스냅샷은 전체로 찍고 여기서 거른다. 모양은 위 scope 와 같다(비우면 전부)
      filter:
        schemas: []
        exclude: { prefixes: [TMP_, BAK_], suffixes: [_BAK, _OLD], regex: ['_\d{8}$'], tables: [] }
        include: { tables: [] }
        skipEmpty: false
    ```
    「아직 설계 전」 메모는 지운다.
- **검증**: `DeliverableRoutesTest` — filter `include.tables` 하나 → 02 xlsx 데이터 1행·01 테이블수 1·응답 `tables` 1 · `exclude.prefixes` → 그 표가 02 에 없다 · 맞는 표 0 → 400 · filter 없음 → 지금과 같다(기존 단언 그대로). `ProfileStoreTest` — `deliverable.filter` 왕복, `saveCodeCheck` 뒤에도 남는다, 잘못된 regex 는 로드 실패. CLI `toolbox.bat deliverable` 은 같은 라우트라 따로 손대지 않는다.
- **의존**: 없음. U-7(스냅샷 범위 기록)과 같은 `Scope` 레코드를 쓴다.
- **가름**: ① Profile·ProfileStore·example.yaml + 테스트 ② 라우트 거르기 + 화면 건수 + 테스트.

### U-3 SQL 스니펫 화면 백엔드 기능 제거 — 확정

- **사실(근거)**: 개발자는 DB 툴이 있다 · `SqlRunner` 는 문장을 안 나눠(1-7) `/` 로 끝나는 PL/SQL·여러 문장 스니펫이 구조적으로 실패한다(ORA-06550) · 읽기 전용 가드가 없어 `kill_session`·`seq_reset`·`gather_stats` 가 실제로 돈다. PLAN 2.5 는 `[확정] B`(실행 버튼)였다 — 이 항목이 뒤집는다.
- **대상**: `tools/sql_snippets.html` · `SmokeHtmlUnitTest.java`(181-218) · `ToolsFolderTest.java` · PLAN.md(2.5 본문 96, 2.1 의 53, 8장 326).
- **변경**:
  1. `tools/sql_snippets.html` 을 `pure/tools/sql_snippets.html` 사본으로 바꾸고, 순수본 60줄 뒤에 `<script src="/tools/common.js" defer></script>` 한 줄만 넣는다(지금 백엔드본 71줄과 같은 자리·같은 글자 — `BackendToolsTest:36-37` 이 본다). 접속 select·실행 줄·CSS·`checkDialect()`·함수 묶음이 전부 사라진다.
  2. `SmokeHtmlUnitTest.sqlSnippetsRunOnConnection` 삭제 — 없앤 기능의 테스트다.
  3. `ToolsFolderTest` 에 `sqlSnippetsIsPurePlusCommonJs` — 백엔드본에서 common.js 줄을 뺀 글이 순수본과 같다(CR 제거 뒤 비교) + `/api/` 문자열이 없다.
  4. 라우트·`SqlRunner`·`XlsxWriter.writeCsv` 는 손대지 않는다(D4).
  5. PLAN: 2.5 를 「`[확정]` 복사 전용 — 실행 버튼 뺌(2026-10-04 사용자)」 로, 53·326 줄에서 스니펫 언급을 뺀다. 4-5 완료 행은 안 고치고 새 행으로 적는다.
- **검증**: `ToolsFolderTest` 새 테스트 초록 · `SmokeHtmlUnitTest.opensWithoutScriptErrors`(sql_snippets, 배지 「백엔드 연결」) 초록 · `BackendToolsTest` 초록 · `MetaRoutesTest.snapshotFlow`(라우트 직접 호출) 초록 · `--full` 에서 방언 넷 `snippets` 절(V-8) 그대로.
- **의존**: 없음.
- **실패 사다리**: 순수본과 백엔드본이 common.js 말고도 다르게 나오면(순수본이 그 사이 바뀜) — 다른 줄을 이력에 적고 순수본을 따른다.

### U-4 표 편집기 — 엑셀 병합 표·정렬 붙여넣기 — 확정

- **대상**: `tools/table_builder_ext.js`(신설) · `tools/table_builder.html`(63 뒤 script 한 줄, 930-942 리스너) · 테스트·픽스처.
- **엑셀 클립보드 꼴(알려진 것 — 실물 확인은 ⑤)**: CF_HTML 조각 `<html … xmlns:x="urn:schemas-microsoft-com:office:excel"><head><style>.xl65{text-align:center;…}</style></head><body><table>…`. **정렬은 셀의 `style`·`align` 이 아니라 `<style>` 의 `.xlNN` 클래스 규칙에 있을 수 있다.** 병합은 `colspan`·`rowspan`. 글이 옆 칸으로 넘친 칸은 `colspan` + `style="mso-ignore:colspan"`(병합 아님). 셀 안 줄바꿈은 `<br style="mso-data-placement:same-cell">`. HtmlUnit 4.11.1 은 이 꼴을 `DOMParser` 로 읽는다(실험 — class·`<style>` 글·`mso-ignore` 까지 읽혔다).
- **변경**:
  ```js
  // table_builder_ext.js (ES5, defer, innerHTML 대입 없음)
  /** 클립보드 text/html → { merged: bool, html: 정리한 <table> 문자열, aligns: [[ 'left'|'center'|'right'|'' ]] } | null(표 없음) */
  function tbClipboardTable(html) {
    // 1. DOMParser 로 읽어 첫 table. 없으면 null
    // 2. <style> 글에서 `.클래스명 { … text-align: X … }` 를 모아 클래스 → 정렬 표
    // 3. tr 의 직계 td·th 만. colspan·rowspan 을 읽되 style 에 mso-ignore:colspan 이 있으면 병합이 아니다 —
    //    칸 수만큼 낱칸으로 편다(첫 칸에 글, 나머지 빈칸)
    // 4. 정렬 = 셀 style.textAlign → align 속성 → 클래스 표 순. left·center·right(·justify 는 left) 만, 그 밖은 ''
    // 5. 셀 글 = 자식 노드를 훑어 텍스트는 이스케이프, <br> 은 <br> 로, 그 밖 태그는 벗기고 안쪽만
    // 6. html = 정리한 표(태그 이름 td·th · colspan·rowspan · style="text-align:X" 만). caption·colgroup·class 는 내지 않는다
    // 7. merged = 병합(cs>1 또는 rs>1)이 하나라도 있나. aligns = 병합을 편 격자 기준 정렬
  }
  ```
  ```js
  // table_builder.html 붙여넣기 리스너 — cd 를 얻은 바로 뒤, getData('text') 앞
  var clip = cd.getData('text/html');
  var tb = clip && typeof tbClipboardTable === 'function' ? tbClipboardTable(clip) : null;
  if (tb && tb.merged) {                                   // D8 — 표 전체 교체
    e.preventDefault();
    var keepHead = G.theadRows, keepCap = G.caption;       // importHtml 이 0·'' 으로 덮는다
    if (importHtml(tb.html)) {                              // snapshot() 을 안에서 부른다 — Ctrl+Z 로 되돌림
      G.theadRows = Math.min(keepHead, G.rows); G.caption = keepCap;
      syncInputs(); render();
      toast('붙여넣기(병합 포함): 표를 바꿨다 — Ctrl+Z 로 되돌린다');
    }
    return;
  }
  if (tb) window.TB_PASTE_ALIGNS = { r: sel ? sel.r1 : 0, c: sel ? sel.c1 : 0, aligns: tb.aligns };
  // ↓ 아래 지금 TSV 코드가 그대로 돈다(pasteGrid). pasteGrid 끝의 render() 뒤에 ext.js 가 정렬을 덧입힌다
  ```
  - ext.js `tbApplyPastedAligns()`: `TB_PASTE_ALIGNS` 가 있으면 그 시작 칸부터 `aligns` 격자대로 `masterOf(r, c)` 칸의 `align` 을 넣고(빈 값이면 지움) `render()`, 그리고 `TB_PASTE_ALIGNS = null`. 리스너에서 `pasteGrid(rows)` 바로 뒤 한 줄 `if (typeof tbApplyPastedAligns === 'function') tbApplyPastedAligns();` — 순수본 `pasteGrid` 는 안 고친다. 되돌리기는 `pasteGrid` 가 부른 `snapshot()` 한 번으로 글·정렬이 같이 돌아간다.
- **검증**:
  - 픽스처 `src/test/resources/fixtures/table/`: `excel-merge.html`(병합 + `.xl` 클래스 정렬 + `<br style=mso-data-placement>`) · `excel-nomerge.html`(병합 없음, 클래스 정렬) · `excel-msoignore.html`. **실물 엑셀에서 뜬 것**(⑤)이 오면 그것으로 바꾸고, 그 전에는 위 꼴로 손으로 만든다 — 손으로 만든 것임을 파일 머리 주석에 적는다.
  - `SmokeHtmlUnitTest.tableBuilderPastesExcelClipboard` — 페이지에서 `tbClipboardTable(픽스처)` 결과 `{merged, aligns}` 단언 · 병합 픽스처는 `importHtml(결과.html)` 뒤 `JSON.stringify({rows, cols, grid, theadRows})` 를 골든 `golden/table/paste-merge.json` 과 비교 · 병합 없는 픽스처는 2×2 표에 `sel` 을 두고 `pasteGrid` + `tbApplyPastedAligns` 를 불러 칸 정렬 단언 · 정리한 `html` 에 `class=`·`<font`·`<span`·`mso-` 가 없다.
  - `ToolsFolderTest`(파일 이름·innerHTML 규칙) 초록 · `--full` 의 `JsCorpusTest.tableImportExportStable`(표 300 왕복) 그대로.
- **의존**: 없음.
- **실패 사다리**: 실물 엑셀 HTML 이 위 꼴과 다르면(⑤) 정렬 읽기만 그 꼴에 맞춘다. 병합 판정이 다르면 멈춘다(7장).
- **범위 밖**: 순수본. portfolio 지시문 한 줄 — 「`table_builder.html` 붙여넣기에서 클립보드 text/html 의 병합 표·정렬을 살린다(정리 규칙은 toolbox-server `table_builder_ext.js`)」 — 을 이력에 남긴다.

### U-5 산출물 표준 맞춤 — 확정

조사 결과는 부록 A·B. 원문 근거 전체는 `design/14-standard-research.md`.

#### U-5a 조사 문서 보존 — 끝냄
- 조사 `design/14-standard-research.md`, 가능 판정 `design/14-feasibility.md`. 받은 고시·매뉴얼 원문(HWP·PDF)은 저장소에 넣지 않았다 — URL 이 조사 문서 1장에 있다.

#### U-5b 메타 수집 보강 — FK 규칙·인덱스 정렬·CHECK·용량
- **대상**: `core/meta/{ForeignKey,Index,Table}.java` + 새 `Check.java` · `core/meta/JdbcMetaSource.java`(175-205, 229-247) · `core/dialect/*MetaSource.java` · `core/meta/SnapshotStore.java`(119-133 넣기, 238-284 읽기) · `db/migration/V003__meta_more.sql`(D3) · `core/deliverable/Definitions.java`(131 R13, 164 R15, 169-182 R16, 82 R17).
- **변경**:
  - FK: `getImportedKeys` 의 `DELETE_RULE`·`UPDATE_RULE`(short → `CASCADE`·`SET NULL`·`SET DEFAULT`·`RESTRICT`·`NO ACTION`) → `ForeignKey(…, String deleteRule, String updateRule)`. `snap_constraint` 에 `delete_rule`·`update_rule` 열.
  - 인덱스: `getIndexInfo` 의 `ASC_OR_DESC`(`A`·`D`·null) → `Index(name, unique, columns, List<String> sorts)`. `snap_index` 에 `sorts` 열(JSON 배열).
  - CHECK: 벤더 SQL(Oracle `ALL_CONSTRAINTS` `C` 형의 `SEARCH_CONDITION` 에서 `IS NOT NULL` 자동 생성분 제외 · PG `pg_constraint` `contype='c'` · MariaDB `CHECK_CONSTRAINTS` · MSSQL `sys.check_constraints`) → `record Check(String name, String condition)`, `Table.checks`. `snap_constraint.kind` 에 `CK`, 조건 글은 새 열 `condition`. `SnapshotStore.fill` 의 kind switch(263-268)에 `CK`.
  - 용량: 벤더 뷰(Oracle `USER_SEGMENTS`/`DBA_SEGMENTS` 등, 권한 없으면 null) → `Schema.sizeBytes`(스키마 합계, 모르면 null). 저장은 새 V 파일의 `snap_schema(snapshot_id, schema_name, size_bytes)`. 참고 SQL 은 `tools/deliverable_sql.html` 가이드 209·243-245(용량)·305-308(삭제규칙)·463-466(정렬)·484·493(CHECK)에 이미 있다.
  - `Definitions`: R13 삭제·갱신규칙 = 수집값(없으면 빈칸, Oracle `NO ACTION` 고정 제거) · R15 정렬 = 수집값(없으면 빈칸, `ASC` 고정 제거) · R16 에 CHECK 행(제약유형 `CHECK`, 제약내용 = 조건) · R17 데이터용량 = 수집값.
- **검증**: `golden/meta/{postgres,postgres-vendor,mariadb,mssql,oracle}.json` 과 `golden/deliverable/{pg-04,pg-10,pg-11,filled-10}.json` 갱신 — diff 가 새 필드·새 값뿐인지 보고 이력에 적는다. `DefinitionsTest` R13·R15·R16·R17 은 **규칙이 바뀌는 것**이라 새 기대값으로 고친다(이력에 규칙 변경으로). `SnapshotStoreTest` 왕복. `--full` 방언 넷 `meta` 절 A 0. Tibero 는 골든만.
- **의존**: 없음. U-5c 의 03 FK정보·제약조건 열과 04·10·11 이 이 값을 쓴다.
- **가름**: FK 규칙 / 인덱스 정렬 / CHECK / 용량을 따로. 저마다 모델 → 수집 → 저장(+V 파일) → 정의서 순.
- **금지**: `SnapshotDiff` 는 이번에 안 넓힌다 — `golden/meta/diff-*.json` 이 안 바뀌어야 한다.

#### U-5c 표준 열 이름·표기·09 구조 — 확정
- **원칙(D9·D11, 사용자 결정 ③)**: 값 표(`COLS_*`) 열 이름을 **표준 이름**으로 바꾸고, 표준에만 있는 열을 더하고, 확장 열은 뒤에 남긴다. 발주처 양식이 다르면 매핑 YAML 왼쪽(양식 머리)만 고친다.
- **열 이름 글자**: 행안부고시 제2025-19호 별표1·2·4 원문 글자 그대로(띄어쓰기 포함) — `design/14-standard-research.md` 3장 표에서 옮긴다. 별표2(산출물)를 먼저, 없으면 별표4.
- **문서별 열**(→ 는 지금 이름에서 바뀜, + 는 새 열, 「확장」 은 표준에 없어 뒤에 남김):

  | 문서 | 표준 열(순서대로) | 확장(뒤) |
  |---|---|---|
  | 01 | 기관명 · 부서명 · +관련법령 · 한글 DB명(←논리DB명) · 영문 DB명(←물리DB명) · +구축일자 · DB 설명(←DB설명) · +업무분류체계 · DBMS 정보(←DBMS명+DBMS버전, 「Oracle 23.26」 꼴 한 칸) · 운영체제정보(←운영체제명) · +DB 형태 · 테이블 수(←테이블수) · 데이터 용량(←데이터용량, U-5b) | 적용업무 |
  | 02 | +영문 DB명 · 테이블 소유자 · 한글 테이블명 · 영문 테이블명 · 테이블 유형(←테이블용도) · 관련 엔터티명 · 테이블 설명 · 발생주기(←갱신주기) · 테이블 볼륨(←테이블볼륨(건)) · +공개/비공개 여부 · +개방데이터목록 | 순번 · 보존기간 · 예상발생량(건) · 분류명 · 태그정보 · 담당부서 · 담당자 · 최초등록일 · 최종수정일 · 최종수정자 · 변경구분 |
  | 03 | 영문 테이블명(←테이블명) · 한글 컬럼명 · 영문 컬럼명 · 컬럼 설명 · 연관 엔터티명 · 연관 속성명 · 데이터 타입 · 데이터 길이 · **Not Null 여부**(←Not Null) · PK정보 · +AK정보 · +FK정보 · +제약조건 · +개인정보 여부 · +암호화 여부 · +공개/비공개 여부 | 순번 · 스키마명 · 기본값 · 담당부서 · 담당자 · 최초등록일 · 최종수정일 · 최종수정자 · 변경구분 |
  | 04 | (표준 문서 없음) 부모·자식 열 이름만 별표 용어로(영문 DB명·테이블 소유자·한글/영문 테이블명·한글/영문 컬럼명) · 삭제규칙 · 갱신규칙(U-5b) | 순번 |
  | 05~07 | 별표1 글자로 띄어쓰기만 맞춘다 | 지금 확장 그대로 |
  | 08 | 관리부서명 · 한글코드명(←코드명(한글)) · 영문코드명(←코드명(영문)) · +코드설명 · 데이터타입 · 데이터길이 · 코드값 · 코드값 의미 · 제정일자 | 순번 · 기관명 · DB명 · 코드값설명 · 사용여부 · 특이사항 |
  | 09 | 연계정보 구분(←송/수신, 값 「제공」·「활용」) · 연계 정보명 · 연계 주기 · 연계 항목명 · 연계 항목 설명 · 데이터 타입 · 데이터 길이 · 출처 DB명 · 출처 테이블명 · 출처 컬럼명 · 제공기관 · 활용기관 · 비고 | 순번 · 방식 · 연계기간 |
  | 10·11 | (표준 문서 없음) DB명 → 영문 DB명, 테이블명 → 영문 테이블명 | 그대로 |

- **값 표기(별표2 작성지침)**:
  - **Not Null 여부 = 「Y」 Nullable, 「N」 Not Null**(사용자 결정 ③-2). `Definitions.java:107` `c.nullable() ? "N" : "Y"` → `c.nullable() ? "Y" : "N"`. R7 이 뒤집힌다.
  - PK정보 = `PK` + 참여 순서 두 자리(`PK01`·`PK02`), 아니면 빈칸. 지금 `Y`/`N` — R10 이 바뀐다.
  - AK정보 = `AK_<유니크 키 순번>-<참여 순서 두 자리>`(`AK_1-01`). 유니크 키 순번은 `Table.uniques()` 순서(1부터). 한 컬럼이 여러 AK 에 들면 `, ` 로 잇는다.
  - FK정보 = `참조테이블.참조컬럼`(같은 스키마면 스키마 생략), 여럿이면 `, `.
  - 제약조건 = 기본값이 있으면 `DEFAULT <원문>`, CHECK(U-5b)가 이 컬럼 이름을 포함하면 `CHECK (<조건>)` — `; ` 로 잇는다. 확장 열 「기본값」 은 그대로 둔다.
  - 01 DB 형태 = 「정형」 고정(관계형 DB 만 다룬다). 구축일자·관련법령·업무분류체계 = 빈칸(수동).
- **09 구조(사용자 결정 ③-3)**: 연계 **항목 하나가 한 행**. `CodeAndLink.linkCandidates`(145) 후보 표마다 그 표의 컬럼을 항목으로 편다 — 연계 항목명 = 한글 컬럼명(없으면 영문), 연계 항목 설명 = 컬럼 코멘트, 타입·길이 = 컬럼, 출처 DB명·테이블명·컬럼명 = 스냅샷. 연계정보 구분·연계 정보명·연계 주기·제공기관·활용기관은 빈칸(수동). DB 링크 후보(`dbLinks`)는 컬럼을 모르니 링크 하나가 한 행(항목 칸 빈칸).
- **대상**: `Definitions.java`(COLS_01·02·03·04·10·11, 75-182 행 조립, R7·R10) · `Standards.java`(COLS_05·06·07 띄어쓰기) · `CodeAndLink.java`(COLS_08·09, `codeDoc` 118, `linkDoc` 212) → `bash scripts/make-example-templates.sh`(양식 + `example.yaml` 다시 생성) → 테스트·골든.
- **검증**: `XlsxFillerTest.committedTemplatesAndMappingMatchValueColumns`(새 양식·YAML 과 맞는다) · `DefinitionsTest` R7·R10 은 **규칙이 바뀐 것** — 새 기대값으로 고치고 이력에 「R7 표준 표기로 뒤집음(사용자 2026-10-04)」 · 새 단언 AK_1-01·FK정보·제약조건 · `DeliverableRoutesTest` 열 위치(02 최종수정자 17 → 새 위치) · 골든 `golden/deliverable/pg-*`·`filled-*`·`sample-05~07` 갱신(diff 가 열 이름·순서·새 열뿐인지 본다) · `CodeAndLinkTest` 09 행 수 = 후보 표 컬럼 합.
- **의존**: U-5b(FK·CHECK·용량 값) 뒤에 치면 빈칸이 덜하다. 앞에 쳐도 깨지지 않는다(빈칸). U-5d 와 같은 묶음.
- **가름**: ① 01·02·03 열 + R7·R10 + 양식 재생성 ② 04·10·11 이름 ③ 05~07 띄어쓰기 ④ 08 ⑤ 09 구조.

#### U-5d 등급 드러내기 + 관리 열 기본값 — 확정
- **관리 열 기본값(사용자 결정 ④)**: `Definitions.tail()`(184-191) — 최초등록일 = `createdAt`(없으면 빈칸, 그대로) · **최종수정일 = 빈칸**(지금 「최초등록일 복사」) · **변경구분 = 빈칸**(지금 「신규」) · 최종수정자 = 작성자 입력 그대로(비면 `__작성자_미입력__`) · 담당부서 = 옵션 그대로 · 담당자 빈칸. R5 가 바뀐다.
- **작성안내(사용자 결정 — 정의서는 깨끗이, 안내는 따로 한 장)**: 산출 폴더에 `00_작성안내.xlsx` 를 더 쓴다. 정의서 파일에는 색·메모를 넣지 않는다.
  - 시트 「항목」: 문서 · 열 · 등급(자동·추정·수동) · 채우는 법 · 이번 추정 건수.
  - 시트 「요약」: 스냅샷 #id·찍은 시각·접속 · `deliverable.filter` 요약(U-2) · 표 수 · 「이 문서들은 DB·소스에서 거꾸로 뽑은 역설계본이다 — 수동 칸은 사람이 채운다」 한 줄.
  - 등급 표는 새 `core/deliverable/Grades.java` 상수 — 문서 번호 → 열 → (등급, 채우는 법). 내용은 부록 B 와 같다. 추정 건수는 셀을 만들 때 센다 — 한글 테이블명·한글 컬럼명이 코멘트가 아니라 사전(논리명)에서 왔을 때, 개인정보 여부(U-5g), 선언 안 된 FK(U-5f).
  - `Doc` 에 `Map<String, Integer> estimated`(열 → 추정 셀 수). 생성자 검사는 열 이름이 `columns` 에 있는지만.
  - 쓰기는 기존 `core/report/XlsxWriter.write(ResultTable, Path)` 를 쓴다. 시트 둘을 한 파일에 쓰려면 시트 이름을 받는 오버로드 하나를 더한다.
- **검증**: `DefinitionsTest` R5 새 기대값(최종수정일·변경구분 빈칸) · `DeliverableRoutesTest` — 결과에 00 이 더해지고 「항목」 시트에 03·「개인정보 여부」·「추정」 행 · 새 `GradesTest` — `Grades` 의 모든 (문서, 열)이 그 문서 `COLS_*` 에 있다(열 이름이 바뀌면 같이 고치게).
- **의존**: U-5c(열 이름).

#### U-5e 없는 표준 문서 — 상관도·엔터티정의서·애트리뷰트정의서 — 확정
- **사용자 결정**: 셋을 만든다. 물리·논리 ERD 는 안 만든다(D22). 오너십·업무규칙 정의서도 안 만든다.
- **번호 [고정]**: 12~15 는 PLAN 2-8(뷰·프로시저·시퀀스·권한, 보류)이 잡아 두었다. 그래서 **16 엔터티정의서 · 17 애트리뷰트정의서 · 18 테이블대응용프로그램상관도**. `Forms.all()` 에 셋을 더하고 양식을 다시 만든다.
- **16 엔터티정의서**(열 이름은 별표2 「엔터티 정의서」 항목 원문 — research 문서에서 옮긴다): 표 1개 = 엔터티 1개. 엔터티명 = 한글 테이블명(코멘트 → 논리명 추정), 엔터티 설명 = 테이블 코멘트, 식별자 = PK 컬럼 한글명. 주제영역·엔터티 유형·정의는 빈칸(수동).
- **17 애트리뷰트정의서**: 컬럼 1개 = 속성 1개. 속성명 = 한글 컬럼명, 설명 = 컬럼 코멘트, 식별자 여부(PK·AK·FK), 참조 엔터티명·참조 속성명(선언된 FK), 도메인(05~07 매칭이 있으면). 속성 정의는 빈칸.
- **18 상관도**: 행 = 프로그램(클래스.메서드 + URL), 열 = 테이블, 칸 = CRUD 글자. 원천은 프로그램 분석 실행(`AnalyzeStore.crud(runId)`, `core/analyze/AnalyzeStore.java:266-271`). 표 열은 `deliverable.filter` 로 거른다(U-2).
  - `BuildRequest` 에 `Long analyzeRunId`(선택). 없으면 18 은 `skipped` 에 「프로그램 분석 실행을 고르지 않았다」(08 이 접속 없을 때와 같은 꼴).
  - 화면 빌드 카드에 「프로그램 분석 실행」 select(`GET /api/analyze/runs`, 「#id · 시각 · 경로 · 프로그램 n」, 처음 값 = 없음).
  - 열이 분석 xlsx 상한(`AnalyzeRoutes:124` `MAX_TABLES`)을 넘으면 같은 400.
- **세 문서 공통**: 양식 제목 행에 「역설계본」 을 붙이지 않는다 — 발주처 양식을 안 건드린다. 00 작성안내 「요약」 에 적는다(U-5d).
- **대상**: `core/deliverable/Forms.java`(58-71) · 새 `core/deliverable/Models.java`(16·17) · `DeliverableService.build`(52-102, 18 은 `AnalyzeStore` 를 받는다) · `web/DeliverableRoutes.java`(BuildRequest) · `tools/deliverable_sql.html`(문서 체크 16~18, 분석 실행 select) · 양식 재생성.
- **검증**: 새 `ModelsTest` — 골든 `golden/deliverable/pg-16.json`·`pg-17.json` · `DeliverableRoutesTest` — 분석 실행을 주면 18 이 생기고 행 = 프로그램 수, 안 주면 `skipped` 에 18 · 양식·YAML 일치 테스트.
- **의존**: U-5c(열 이름 원칙), U-2(필터).

#### U-5f 선언 안 된 FK 추정 — 확정(뚜렷한 것만 04)
- **사실(egov Oracle 매퍼 153개·문장 1,237개 실측, 2026-10-04)**: 국내 SI DB 는 FK 를 안 거는 곳이 많다. 매퍼 SQL 에서 `별칭.컬럼 = 별칭.컬럼` 등식 642개 중 596개가 서로 다른 두 표로 풀렸다(별칭을 못 푼 것 44, 같은 표 2). 조인으로 이어진 표 쌍 139 — 그중 FK 가 선언된 쌍은 25 뿐이다. 선언 없는 114쌍 가운데 **한쪽 표의 PK 전체가 한 문장의 등식에 든 쌍이 26**(24쌍은 두 문장 이상에서 반복), 양쪽 다 PK 전체가 든 쌍 2, 나머지 86(뷰가 낀 쌍 39). `SqlTables`(`core/analyze/SqlTables.java`)는 표·CRUD 만 뽑고 조인 조건은 안 본다.
- **결정(사용자 ⑰)**: 부모가 뚜렷한 쌍만 04 테이블관계정의서에 「추정」 으로 싣는다. 방향을 모르는 쌍은 `00_작성안내.xlsx` 의 「관계 후보」 시트에 목록으로만. 03 FK정보에는 선언된 것만 쓴다.
- **① 추출·저장(행 `6-13`)** — 대상: 새 `core/analyze/SqlJoins.java` · `core/analyze/MapperIndex.java`(117 — `SqlTables.extract` 옆에서 같이 부른다) · `core/analyze/AnalyzeStore.java` · 새 V 파일.
  ```java
  /** SQL 한 문장에서 서로 다른 두 표를 잇는 컬럼 등식. 주석 제거·토큰화는 SqlTables 와 같은 스캐너를 쓴다 */
  public final class SqlJoins {
      public record Join(String tableA, String colA, String tableB, String colB) {}   // 대문자·스키마 뗌, (tableA, colA) < (tableB, colB) 로 정렬
      public static List<Join> extract(String sql);
  }
  ```
  - 별칭 표: `FROM`·`JOIN`·쉼표 뒤의 `표 [AS] 별칭`(별칭이 없으면 표 이름이 별칭). 예약어(`WHERE`·`ON`·`LEFT` …)는 별칭이 아니다. 서브쿼리 범위는 가르지 않는다 — 문장 전체가 별칭 표 하나다.
  - 등식: 양쪽이 다 `별칭.컬럼` 인 `=` 만. Oracle `(+)` 가 붙어도 같다. 별칭을 못 풀거나 두 쪽이 같은 표면 버린다. 한 문장 안의 같은 등식은 한 번만.
  - 저장: `analyze_join(run_id, ns_id, table_a, col_a, table_b, col_b)` — 문장 단위(부모 판정에 「같은 문장」 이 필요하다). `ON DELETE CASCADE` 로 `analyze_run` 에 문다. 조회 `AnalyzeStore.joins(runId)`.
  - 검증: `SqlJoinsTest` — 명시 JOIN·쉼표 조인·`(+)`·별칭 없음·서브쿼리·자기 조인·주석 속 등식 → 골든 `golden/analyze/sql-joins.json` · `AnalyzeCorpusTest` egov 절에 수치(등식 수·표 쌍 수) B baseline — 위 실측(642·139)은 파이썬 정규식으로 잰 값이라 자바 스캐너 값과 조금 다를 수 있다. 처음 나온 값을 baseline 으로 얼린다.
- **② 판정·싣기(행 `2-19`)** — 대상: 새 `core/deliverable/Relations.java` · `Definitions`(04 에 확장 열 「근거」) · `DeliverableService`(18 과 같은 `analyzeRunId` 로 조인을 읽는다) · `Grades`·작성안내.
  ```java
  public final class Relations {
      public record Inferred(String childTable, List<String> childCols, String parentTable, List<String> parentCols, int statements) {}
      public record Candidate(String tableA, String colA, String tableB, String colB, int statements, boolean view) {}
      public record Result(List<Inferred> strong, List<Candidate> weak) {}
      public static Result infer(List<Schema> snapshot, List<AnalyzeStore.JoinRow> joins);
  }
  ```
  - 표 쌍마다: 두 표가 다 스냅샷에 있어야 한다(없으면 버린다). 스냅샷에 그 두 표 사이의 선언 FK 가 있으면 버린다.
  - **강**: 어느 한 문장에서 한쪽 표 P 의 PK 컬럼 전부가 다른 표 C 와의 등식에 들어 있고, 반대쪽은 그렇지 않다 → 부모 P, 자식 C. 자식 컬럼은 PK 컬럼에 맞선 컬럼(PK 순서대로). `statements` = 그 쌍이 나온 문장 수.
  - **약**: 그 밖 전부(양쪽 다 PK 전체가 든 쌍, PK 일부만, PK 아닌 컬럼끼리, PK 없는 표·뷰). 컬럼 쌍 단위로 목록.
  - 04: 선언 FK 행은 「근거」 = 「선언」, 강한 추정 행은 「추정(조인 n문장)」. 삭제규칙·갱신규칙은 추정 행에서 빈칸. 정렬은 지금 04 순서(R19) 뒤에 추정 행을 부모·자식 이름순으로.
  - 작성안내: 시트 「관계 후보」 — 표A·컬럼A·표B·컬럼B·문장 수·뷰 여부(약한 쌍). 「항목」 시트의 04 줄에 추정 건수.
  - `analyzeRunId` 가 없으면 추정 없이 선언 FK 만(지금과 같다).
  - 검증: `RelationsTest` — 단일 PK 부모 · 복합 PK 전부 일치 · 복합 PK 일부만(약) · 양쪽 다 PK(약) · 선언 FK 가 있는 쌍(안 싣는다) · 뷰가 낀 쌍(약, `view = true`). 표본(`V-24`) B — egov 에서 강·약 쌍 수.
- **의존**: U-5d(`Grades`·작성안내), U-5e 18(`analyzeRunId`), U-5c(04 열 이름).
- **한계(작성안내 「요약」 에 한 줄)**: 조인에 안 나오는 관계는 못 찾는다 · 동적 SQL 로 조립한 조건은 못 본다 · 방언별 매퍼가 여럿이면 프로그램 분석이 고른 방언의 매퍼만 본다.



#### U-5g 개인정보 여부 추정
- **재사용**: `core/logical/Masking.detect`(113-149) + `logical/masking.yaml`(rrn·phone·email·name·address·account·card·birth) — 논리명·코멘트 키워드, 컬럼명 정규식.
- **변경**: `DeliverableService.build` 가 스냅샷 컬럼 전부를 `Masking.Input(owner, table, col, logicalName, comment, dtype, dlen)` 으로 만들어 `Masking.detect` 를 한 번 돌리고(논리명은 05~07 용 `LogicalRun` 결과가 있으면 그것, 없으면 null — 그때는 코멘트 키워드와 컬럼명 정규식만 본다), 후보 열쇠 집합을 `Definitions` 에 넘긴다. 03 「개인정보 여부」 = 후보면 `Y`(추정 건수에 센다), 아니면 빈칸. 암호화 여부·공개/비공개 여부는 빈칸(수동).
- **검증**: `DefinitionsTest` — `RRN`·`TELNO` 꼴 컬럼은 `Y`, `USE_AT` 은 빈칸 · `Doc.estimated` 에 건수.
- **의존**: U-5c(열), U-5d(등급).

### U-6 프로그램 분석 — JPA 저장소 해석 — 확정

- **목표**: 분석을 돌린 폴더가 JPA 프로젝트여도 프로그램 → 테이블 CRUD 가 나온다. MyBatis 와 섞인 프로젝트는 둘을 합친다.
- **대상**: 새 `core/analyze/JpaIndex.java` · `core/analyze/JavaGraph.java`(문장 참조 378-416 옆) · `core/analyze/AnalyzeRunner.java`(117-144 CRUD 합치기) · `core/analyze/Unresolved.java`(종류 주석) · 픽스처 `fixtures/analyze/jpa/` · 골든 `golden/analyze/jpa-graph.json` · 표본 레시피.
- **변경**:
  ```java
  /** JPA 색인 — 엔티티 → 표, 저장소 인터페이스 → 엔티티, 저장소 메서드 → 표·CRUD */
  public final class JpaIndex {
      public record Entity(String className, String table, boolean guessed, List<String> extraTables) {}
      public static JpaIndex scan(List<JavaGraph.Source> java);          // JavaSource.parse 재사용
      public Optional<Entity> entity(String simpleName);
      public boolean isRepository(String typeName);
      /** 저장소 메서드 하나가 건드리는 표. 모르면 빈 목록 + unresolved */
      public List<SqlTables.Ref> refs(String repo, String method, List<Unresolved> out);
  }
  ```
  1. **엔티티 → 표**: `@Entity`(`javax.persistence`·`jakarta.persistence`, 단순 이름으로 판정). `@Table(name=)` 있으면 그 이름. 없으면 클래스 이름을 `SNAKE_UPPER` 로 바꾸고 `guessed = true` + `unresolved(entityName)`. `@SecondaryTable`·`@JoinTable(name=)`·`@CollectionTable(name=)` 은 `extraTables`. `@MappedSuperclass` 는 표 없음. 상속은 기본(SINGLE_TABLE)이면 루트 엔티티 표, `@Inheritance(strategy = JOINED | TABLE_PER_CLASS)` 면 자기 표. 표 이름은 `SqlTables.Ref` 관례대로 대문자·스키마 뗌.
  2. **저장소 판별**: `JpaRepository`·`CrudRepository`·`ListCrudRepository`·`PagingAndSortingRepository`·`Repository` 를 (사슬로) 상속한 인터페이스. 엔티티는 첫 타입 인자.
  3. **메서드 → CRUD**: `find·get·read·query·search·stream·count·exists` 로 시작 → R / `save`·`saveAll`·`saveAndFlush` → C·U / `delete`·`remove` 로 시작 → D. 그 밖 이름은 `unresolved(jpaMethod)`.
  4. **`@Query`**: `nativeQuery = true` 면 `SqlTables.extract(sql, null)`. 아니면 JPQL — 첫 낱말이 verb, `FROM`·`JOIN`·`UPDATE`·`DELETE FROM` 뒤 식별자를 엔티티 색인으로 표에 맞춘다. **JPQL 의 식별자는 엔티티 이름이다** — `@Entity(name = "bbsBbsMaster")` 가 있으면 그 이름, 없으면 클래스 단순 이름(eGovFrame MSA 표본이 `name` 을 쓴다). 못 맞추면 `unresolved(jpql)`. `org.springframework.data.jpa.repository.Query` 만 본다 — 다른 패키지의 `@Query`(표본 eladmin 의 자체 주석, QueryDSL `@QueryProjection`)는 import 로 가른다.
  4-1. **같은 단순 이름의 클래스가 여럿**(MSA 표본은 모듈마다 같은 이름이 되풀이된다): 엔티티·저장소를 찾을 때 참조하는 파일의 `import` → 같은 패키지 → 같은 모듈(경로에서 `src/main/java` 앞 폴더) 순으로 하나를 고른다. 그래도 여럿이면 `unresolved(ambiguous)`(기존 종류).
  4-2. `@Table` 은 `javax.persistence`·`jakarta.persistence` 것만. `org.springframework.data.relational.core.mapping.Table`(R2DBC)은 JPA 가 아니다.
  5. **`EntityManager`**: `persist(x)` C · `merge(x)` C·U · `remove(x)` D · `find(X.class, …)`·`getReference(X.class, …)` R. `x` 의 타입은 같은 메서드의 지역 변수·매개변수 선언에서. 못 찾으면 `unresolved(jpaType)`. `createQuery("…")`·`createNativeQuery("…")` 는 리터럴일 때 4 와 같이, 아니면 기존 `unresolved(statement)`.
  6. **QueryDSL 기본 꼴(행 `6-16`, 사용자 결정 — eGovFrame MSA 표본은 저장소 73개에 `JPAQueryFactory` 를 쓰는 파일이 37개다)**:
     - Q클래스 → 엔티티: 타입 이름이 `Q` + 엔티티 클래스 이름인 것(`QBbs` → `Bbs`). 식은 `QBbs.bbs`(정적 필드), 지역 변수·필드 `QBbs bbs = QBbs.bbs`·`new QBbs("b")`, `import static …QBbs.bbs` 로 들어온 이름.
     - 싱크: `JPAQueryFactory`(또는 `JPAQuery`·`JPAUpdateClause`·`JPADeleteClause`) 타입 필드·변수에서 시작한 메서드 사슬의 `selectFrom(q)`·`from(q…)`·`join`·`leftJoin`·`rightJoin`·`innerJoin(q…)` → R / `update(q)` → U / `delete(q)` → D / `insert(q)` → C. 인자의 첫 Q식만 본다(`join(a.b, c)` 꼴은 둘째 인자).
     - 문장: `Stmt(클래스단순이름 + "." + 메서드 + "#qdsl", "qdsl")`. refs 는 그 메서드 안에서 모은 (표, CRUD).
     - Q식이 엔티티로 안 풀리면(`@Embeddable` 의 Q, 색인에 없는 이름) `unresolved(querydsl)`. 서브쿼리(`JPAExpressions.select…from(q)`)의 `from` 도 같은 규칙으로 R.
     - 닿는 길: Spring Data 커스텀 저장소 — `XxxRepository extends JpaRepository<…>, XxxRepositoryCustom` 의 메서드가 구현 클래스(`XxxRepositoryCustomImpl`·`XxxRepositoryImpl`)에 있으면 `JavaGraph` 의 기존 「인터페이스 → 구현 클래스」 해결로 그 본문에 들어간다. 저장소 호출은 **구현 메서드가 있으면 그것 먼저**, 없으면 3 의 이름 규칙.
  6-1. **세기만 하는 것**: `createNamedQuery`·`@NamedQuery` → `unresolved(namedQuery)` · Criteria(`CriteriaBuilder`) → `unresolved(criteria)` · QueryDSL 의 동적 조립(`BooleanBuilder` 만 있고 싱크가 다른 메서드에 있는 경우 등) → `unresolved(querydsl)`. 연관 매핑(cascade·지연 로딩)은 안 따라간다.
  7. **그래프 연결**: `JavaGraph` 가 호출 대상 필드 타입이 `JpaIndex.isRepository` 면 `Stmt(저장소단순이름 + "." + 메서드, "jpa")` 를 낸다(`resolution` 은 `VARCHAR(10)` — `jpa` 가 들어간다). `AnalyzeRunner` 는 `resolution == "jpa"` 인 문장의 refs 를 `JpaIndex.refs` 에서 얻어 MyBatis refs 와 합친다. 새 미해결 종류는 전부 20자 안.
- **검증**:
  - `JpaIndexTest`·`JavaGraphTest` 에 jpa 픽스처 — `@Table` 있는 엔티티·없는 엔티티·상속 둘·저장소 파생 메서드·`@Query` JPQL·네이티브·`EntityManager` 넷·NamedQuery·QueryDSL 한 줄 → 골든 `jpa-graph.json`.
  - **기존 골든 불변**: `golden/analyze/java-graph.json`·`mapper-index.json` · `AnalyzeCorpusTest` 의 egov 수치(`analyze-egov.json`)와 목록 — egov 에 JPA 가 없으니 한 줄도 안 바뀌어야 한다.
  - 표본 둘(행 `V-25`, 사용자 결정 ⑦ — 둘 다 Apache-2.0, 2026-10-04 조사). `scripts/corpus-fetch.sh` `SOURCES` 에 아래 두 줄, `corpus/SOURCES.md` 에 출처·라이선스·커밋:
    ```
    "shopizer|shopizer-ecommerce/shopizer|6a4a0a65a3408ee8f62597b51d1b3aac24b77dee|sm-core-model/src/main/java/**/*.java sm-core/src/main/java/**/*.java sm-shop/src/main/java/**/*.java"
    "egov-msa|eGovFramework/egovframe-msa-common-components|4f5a895b3b1807da9c863884aba4650056e34237|**/src/main/java/**/*.java"
    ```
    - `shopizer`(태그 3.2.7, Boot 2.5.12, `javax`, java 831): 엔티티 81(`@Table` 없는 것 1)·`@Inheritance` 1·`@MappedSuperclass` 3·`@Query` 233개(네이티브 3)·`EntityManager` 10·컨트롤러 55·저장소 69. QueryDSL 없음. 기능을 고루 덮는 표본.
    - `egov-msa`(`main`, egovframe-boot 5.0.1, `jakarta`, java 763): 엔티티 98(전부 `@Table`, COMTN* 표)·저장소 73·`JPAQueryFactory` 37 파일·`@Query` 6개·컨트롤러 86. `@Entity(name=…)`·모듈마다 같은 클래스 이름. 국내 공공 꼴 표본.
    - 등급 A — 예외 0·`parse` 0·`@Entity` 파일 수 = 색인 엔티티 수·저장소 인터페이스 수 = 색인 저장소 수. 등급 B — 미해결 종류별 건수, 프로그램 수·CRUD 있는 프로그램 수 baseline(`golden/corpus/analyze-shopizer.json`·`analyze-egov-msa.json`).
    - `egov-msa` 덧대조(B 목록): CRUD 에 나온 표 중 egov 표본 Oracle DDL(`egov/script/ddl/oracle`)에 없는 표 — 같은 COMTN 표를 쓰는 판이라 목록이 짧아야 한다. 길면 엔티티 → 표 해석이 틀린 것이다.
  - 화면·정합성·영향도·xlsx 는 `analyze_crud` 를 읽어 그대로 따라온다 — `AnalyzeRoutesTest` 에 jpa 픽스처 실행 1건(CRUD 매트릭스에 엔티티 표가 열로 나온다).
- **의존**: 없음.
- **가름**: ① `JpaIndex` + 테스트 + 픽스처 = 행 `6-14` ② `JavaGraph`·`AnalyzeRunner` 연결 + 골든 = `6-15` ③ QueryDSL 기본 꼴 = `6-16` ④ 표본 둘 + `AnalyzeCorpusTest` 절 = `V-25`.
- **실패 사다리**: JPQL 식별자 파싱이 표본에서 A 를 10건 넘게 내면 JPQL 은 「verb + 저장소의 엔티티 표」 로만 보고 조인 엔티티는 `unresolved(jpql)` 로 내린다.

### U-7 스냅샷 거른 범위 기록 — 확정(기록만)

- **사실**: 프로필 `scope` 는 찍을 때 적용되고 걸러진 표는 스냅샷에 없다. 어떤 범위로 찍었는지 안 남아, 거른 스냅샷을 비교·정합성에 잘못 쓰기 쉽다. 사용자 결정(D21): 범위 설정은 YAML 로만 하고, 스냅샷에는 **무엇으로 거렀는지를 기록**해 알 수 있게 한다.
- **대상**: 새 마이그레이션 `db/migration/V003__snapshot_scope.sql`(D3 — `scope` 와 U-12 의 `warnings` 두 열을 한 파일에) · `core/meta/SnapshotStore.java`(67 넣기, 161-173 `list`, `Summary` 29) · `core/meta/SnapshotService.java`(scope 를 store 에 넘김) · `tools/db_browser.html`(스냅샷 select 라벨·범위 한 줄) · 스냅샷 select 를 가진 다른 화면의 라벨(`deliverable_sql`·`logical_name`·`crud_generator`·`program_analysis` 정합성·`dev_tools` INSERT).
- **변경**:
  - `ALTER TABLE snapshot ADD COLUMN scope VARCHAR(4000)` — 찍을 때 쓴 `Scope` 의 JSON(Jackson). 옛 행은 null.
  - `SnapshotStore.save(profile, connId, note, schemas, Scope scope)` · `Summary` 에 `boolean filtered` + `String scope`. `filtered` = `schemas` 지정·include·exclude 중 하나라도 비어 있지 않거나 `skipEmpty` 참. `GET /api/meta/snapshots` 에 두 필드.
  - 라벨: 걸렀으면 끝에 「 · 거름」. db_browser 에서 스냅샷을 고르면 아래 한 줄 「범위: 스키마 EGOV · 제외 접두 TMP_,BAK_ · 포함 표 0」(scope 없으면 「범위: 기록 없음」).
  - 화면에서 범위를 주는 입력란은 **만들지 않는다**.
- **검증**: `SnapshotStoreTest` — scope 왕복 · 옛 행(scope null) → `filtered=false` · `MetaRoutesTest.snapshotFlow` — 테스트 프로필이 `exclude.prefixes` 를 가지니 목록에 `filtered=true`·`scope` 에 `TMP_` · Migrator — 새 V 파일 적용 뒤 `schemaVersion`.
- **의존**: U-1a·U-1b 와 같은 파일(`SnapshotService`·`SnapshotStore`) — 그 뒤에 친다(행 `1-14`). 이 행이 V003 이고, U-5b 의 V 파일은 번들 23 에서 V004 부터 친 순서대로 매긴다.

### U-8 첫 사용 — 결함 둘 + 안내 둘 — 확정

- **8a 결함 — 프로필 select**: `db_browser.html:147-155` `loadProfiles` — `p.active` 가 없으면 맨 앞에 `<option value="">(프로필 고르기)</option>` 를 넣고 그것을 고른다. 사용자가 고르면 기존 change 리스너(316)가 `POST /api/profiles/active` 를 부른다.
- **8e 결함 — 없는 프로필(실험으로 확인, 2장)**:
  - `cli/Serve.java:46` 앞: `--profile` 을 줬는데 `profiles/<이름>.yaml` 이 없으면 기동하지 않는다 — `[오류] 프로필 파일이 없다: profiles/<이름>.yaml — profiles/example.yaml 을 복사해 만든다` + 종료 2(README 끝 코드 「2 사용법·프로필·비밀번호 없음」 과 같다). `data/active-profile` 을 쓰기 전에 검사한다.
  - `data/active-profile` 이 없는 파일을 가리키면(다른 PC 에서 옮겨 온 data 등): 기동은 하되 활성 없음으로 띄우고 로그 `WARN` 한 줄 「active-profile 의 <이름> 파일이 없어 활성 프로필 없이 띄운다」.
  - 실행 중에 파일이 사라지는 경우를 위해 `App` 에 예외 매핑 — `ProfileStore.load` 의 `UncheckedIOException` → 400 `{message: "프로필 파일을 못 읽는다: <이름>"}`(`App.jsonErrors` 190-195 꼴). 500 이 안 나게.
- **8b 안내 — 프로필 없음**: `loadConns`(161) — 활성 프로필이 없으면 「활성 프로필이 없다 — profiles/example.yaml 을 복사해 profiles/<사업>.yaml 을 만들고 위에서 고른다」, 있는데 접속이 없으면 지금 문구. README 「현장에서 켜기」 3번 앞에 프로필 만들기 단계(복사 → name·connections·scope.schemas → `run.bat --profile <사업>`).
- **8c 안내 — 표 0개**: 스냅샷 done 에서 `data.tables === 0` 이면 `msg(…, 'err')` 「표 0개 — 프로필 scope.schemas 가 접속 계정의 스키마와 맞는지 본다」. `example.yaml` 을 그대로 복사하면 `schemas: [APP, CMM]` 이 따라와 이렇게 된다.
- **대상**: `tools/db_browser.html` · `cli/Serve.java` · `web/App.java` · `README.md`.
- **검증**: `MainTest`(또는 새 `ServeTest`) — `--profile 없는이름` → 종료 2·메시지 · `data/active-profile` 이 없는 이름 → 기동, `/api/conn` 200 `[]`, `/api/ping` `profile` null · `SmokeHtmlUnitTest` — 활성 프로필 없이 연 db_browser 의 `#profile` 값 `""`·`#conns` 에 「활성 프로필이 없다」(지금 `dbBrowserFillsProfileAndConnections` 172 는 활성 있는 경우 — 그대로) · 8c 는 Puppeteer `smoke-dbbrowser.js`(U-1a)에서 표 0개가 나오는 프로필로 한 번.
- **의존**: 8c 는 U-1a 와 같은 줄을 만진다.
- **범위 밖**: 화면에서 접속(url·user) 추가 — 화면이 프로필 YAML 을 쓰게 되는 구조 결정이다.

### U-9 JSP 포매터 — 탭 둘로 가르기 — 확정

- **목표**: 「붙여 넣은 글 하나 정리」 와 「폴더 통째 정리」 를 한 번에 하나만 보이게 한다. 폴더 쪽은 파일마다 무엇이 문제인지 그 자리에서 본다. 버튼 이름이 대상과 결과를 말한다.
- **대상**: `tools/jsp_formatter.html`(마크업 61-114, CSS 49-56, 폴더 JS 678-813) · `tools/jsp_formatter_ext.js`(신설) · `SmokeHtmlUnitTest.jspFormatterFolderBatch`(262-300).
- **화면 배치**:
  ```
  [JSP/HTML 줄정리]  들여쓰기[탭] ☑짧은 태그 한 줄  한 줄 최대[120] ☑빈 줄 1개로 ☑스크립틀릿{}   ← 두 탭이 같이 쓰는 옵션
  [붙여넣기] [폴더 일괄]                                                                        ← #tab-paste · #tab-dir

  ── 붙여넣기(#pane-paste) ─────────────────────────────────────
  [정리 (Ctrl+Enter)] [결과 복사] [비우기]
  입력 (붙여넣기 · 파일 끌어다 놓기)        | [결과] [비교 n]
  <textarea #in>                            | <textarea #out> / <div #cmp>
  ※ 설명(.note — 지금 글 그대로)

  ── 폴더 일괄(#pane-dir) ──────────────────────────────────────
  폴더 [#dir                    ] [폴더 검사(안 씀)] [체크한 파일 덮어쓰기(백업 후)]  ☐ 위험 있는 것만   #dirMsg
  +----------------------+------------------------------------------------+
  | ☑  파일        위험  | #dTitle  EgovArticleList.jsp · UTF-8 · CRLF · 위험 3 · 불균형 1종 |
  |----------------------| [위험 지점] [원본] [정리본]                      |
  | ☑  A.jsp         0   |------------------------------------------------|
  | ☐  B.jsp         3 ◀ | 원본 12행 → 결과 14행  간격 변경 …              |
  | ☑  C.jsp         0   | 원본 40행 → 결과 41행  <pre> 블록 이동          |
  |    D.jsp   바뀜 없음 |                                                |
  +----------------------+------------------------------------------------+
  #dirSum  19개 · 위험 있음 3 · 바뀜 없음 11 · 덮어쓸 대상 5
  ```
- **변경 — `jsp_formatter.html`**:
  1. `<header>` 에는 제목과 옵션(`#o-indent`·`#o-merge`·`#o-width`·`#o-blank`·`#o-brace`)만 남긴다. `opts()` 를 두 탭이 같이 쓴다.
  2. 헤더 아래 `<div class="modes"><span class="t on" id="tab-paste">붙여넣기</span><span class="t" id="tab-dir">폴더 일괄</span></div>`.
  3. `#pane-paste` 로 감싼다: 버튼 셋(`run()`·`copyOut()`·`clearAll()` — `onclick` 그대로) + 지금의 `.main` + `.note`. **「결과 → 입력」 버튼은 뺀다**(사용자 결정 ⑬ — 마크업 한 줄만 지운다. 순수본 함수 `swap()` 은 안 쓰이는 채로 둔다, D16).
  4. `#pane-dir`(처음엔 `display:none`): 위 그림의 마크업. id — `dir`·`dirRecent`·`dirPreview`·`dirApply`·`dirMsg`·`dirTable` 은 **지금 이름 그대로**(스모크가 문다). 새 id — `dirOnlyRisk`·`dirSum`·`dTitle`·`dtab-risk`·`dtab-orig`·`dtab-out`·`dRisk`·`dOrig`·`dOut`. `#dOrig`·`#dOut` 은 `readonly` textarea. 새 마크업에는 `onclick` 속성을 안 쓴다 — ext.js 가 리스너를 단다.
  5. 인라인 스크립트에서 678-813(백엔드가 더한 폴더 JS 와 `loadRecentDirs`)을 **지운다** — ext.js 로 간다. 그 밖 JS 는 안 건드린다(D16).
  6. `</head>` 앞에 `<script src="/tools/jsp_formatter_ext.js" defer></script>`(common.js 줄 다음).
  7. CSS: `#cmp table.dirt …`(53-56)를 `#dirTable …` 로 옮기고, `.modes`·`.pane`·`.dirmain`(flex)·`.dirlist`(너비 34%, 최소 280px, `overflow:auto`)·`.dirdetail`(flex:1)·`#dRisk`(`#cmp` 의 `.sum`·`.grp`·`.it`·`.red`·`.yel`·`.ln`·`.why`·`.clean` 규칙을 `#cmp, #dRisk` 로 같이 건다)·`#dirTable tr.on`·`#dirSum`. 색·폰트는 순수본 변수 그대로.
- **변경 — `jsp_formatter_ext.js`**(ES5, `defer`, `innerHTML` 에는 `''` 만):
  ```js
  var DIR = { root: '', rows: [], stamp: null, sel: -1 };

  function showMode(which)            // 'paste' | 'dir' — pane 둘의 display 와 탭 .on
  function dirPreview()               // 지금 688-724 그대로 옮긴다. 끝에서 dirRenderList()
  function dirRenderList()            // #dirTable: 머리 행(빈칸·파일·위험) + 행. #dirOnlyRisk 가 켜지면 risk !== 0 || err 만
  function dirSelect(i)               // 행 클릭: /api/fs/read 로 원본을 다시 읽어 상세를 채운다(D17)
  function dirRenderRisks(r, box)     // compareDoc 결과 → DOM(createElement·textContent)
  function dirJump(srcLine, outLine)  // srcLine > 0 이면 원본 탭 + scrollTaToLine(#dOrig), 아니면 정리본 탭 + #dOut
  function dirShowDetail(which)       // 'risk' | 'orig' | 'out'
  function dirApply()                 // 지금 774-797 + 앞에 confirm(사용자 결정 ⑬)
  function dirSummary()               // #dirSum · #dirApply.disabled
  function loadRecentDirs()           // 지금 799-812 그대로
  ```
  - **목록 행**: 체크박스(바뀌고·오류 없고·아직 안 쓴 행만 — 지금 조건 그대로, 기본 체크는 `changed && risk === 0`) · 파일 상대 경로 · 위험 칸 — 수 / `한도 초과`(-1) / `바뀜 없음` / `실패` / 쓴 뒤 `덮어씀`. `risk !== 0` 이면 `warn`, 오류면 `bad` 클래스. 고른 행은 `tr.on`.
  - **`dirSelect`**: `#dTitle` = `rel · 인코딩 · 줄바꿈 · 위험 n · 불균형 n종`(쓴 행은 끝에 `· 백업 <경로>`). 오류 행은 `#dRisk` 에 사유만. 쓴 행(`done`)은 원본이 이미 바뀌었으니 `#dOut` 만 채우고 `#dRisk` 에 「덮어썼다 — 원본은 백업에 있다」. 그 밖에는 `TB.api('/api/fs/read?path=…')` → `#dOrig.value = t.text`, `#dOut.value = row.out`, `dirRenderRisks(compareDoc(t.text, row.out), #dRisk)`, 위험 지점 탭을 연다.
  - **`dirRenderRisks`**: `renderCompare`(527-585)와 같은 네 묶음·같은 글·같은 상한(묶음마다 40, 넘으면 「외 n건」) — 구조 변경(`struct[{side, line, raw}]`, red) · 간격 변경(`gapChg[{line, outLine, before, after, added}]`) · pre/textarea 이동(`preChg[{line, outLine, name}]`) · 태그 불균형(`unbalanced[{name, net}]`). `tooBig` 이면 한도 안내, 아무것도 없으면 「화면 출력이 달라질 지점 없음 — 들여쓰기·줄바꿈만 바뀌었다」. 항목 클릭 → `dirJump`. 긴 글은 순수본 `cut(s, n)` 으로 자른다.
  - **`dirApply`**: `var n = …체크 수; if (!confirm(n + '개 파일을 덮어쓴다. 원본은 out/<프로필>/<시각>/backup 에 백업한다. 계속?')) return;` 뒤는 지금 그대로. 끝나면 `dirRenderList()`·`dirSummary()`.
  - **문구**: `#dirMsg` 처음 글 「검사는 파일을 쓰지 않는다. 덮어쓰기는 원본을 out/<프로필>/<시각>/backup 에 둔 뒤 읽은 인코딩·줄바꿈 그대로 되쓴다」 · 검사 중 「검사 i/n」 · 검사 끝 「검사 n개 · 덮어쓸 대상 m개」(+ 「(목록 상한에서 끊김)」) · 쓰는 중 「덮어씀 i/n」 · 끝 「덮어씀 ok/n개 · 백업 stamp …」.
  - **리스너**: `#tab-paste`·`#tab-dir` 클릭 → `showMode` · `#dirPreview`·`#dirApply`·`#dirOnlyRisk`·상세 탭 셋 · `window` 의 `drop` 에서 파일이 있으면 `showMode('paste')`(순수본 끌어다 놓기 결과가 붙여넣기 탭의 `#cmp` 에 그려진다) · `load` 에서 `loadRecentDirs()`.
- **검증**:
  - `SmokeHtmlUnitTest.jspFormatterFolderBatch` 를 고친다(이 항목이 이름을 댄 수정): `#tab-dir` 클릭 → `#dir` 기본값 단언 그대로 → `#dirPreview` 클릭 → `#dirTable tr` 3 → `#dirMsg` 가 「덮어쓸 대상 2개」 포함 → **`a.jsp` 행 클릭 → `#dTitle` 에 `a.jsp`, `#dOrig` 값에 `<ul>`, `#dOut` 값에 `\t<ul>`, `#dRisk` 글이 비어 있지 않다** → `#dirApply` 클릭 → `#dirMsg` 가 「덮어씀 2/2」 로 시작 → 파일·백업 단언(294-300)은 그대로. 실패 글의 `#cmp` 는 `#dirTable` 로.
  - 같은 테스트에 단언 하나 — 폴더 검사 뒤 `#cmp` 글이 비어 있다(붙여넣기 비교 칸을 더는 안 쓴다).
  - `opensWithoutScriptErrors`(jsp_formatter)·`SmokeHtmlUnitTest` 243-256(토크나이저 병적 입력)·`BackendToolsTest`·`ToolsFolderTest`(파일 이름·innerHTML 규칙) 초록.
  - `--full` 의 `JspFmtCorpusTest`(`corpus-jspfmt.js` — `formatJsp`·`compareDoc`) baseline 불변 — 순수본 JS 를 안 건드렸다는 증거.
  - 수동(Puppeteer·실브라우저): egov `cop/bbs` 폴더 검사 → 위험 있는 행 클릭 → 위험 지점 항목 클릭 → 원본 탭의 그 줄로 간다 · 파일 둘을 끌어다 놓으면 붙여넣기 탭으로 넘어간다.
- **의존**: 없음.
- **실패 사다리**: HtmlUnit 이 HtmlUnit 의 `confirm` 기본값(처리기 없으면 true)과 다르게 굴면 테스트에서 `wc.setConfirmHandler((p, m) -> true)` · ext.js 가 500줄을 넘으면 위험 지점 그리기를 `jsp_formatter_risk.js` 로 가른다.
- **범위 밖**: 순수본(폴더 기능이 없어 탭이 필요 없다) · 검사 뒤 디스크의 파일이 바뀐 경우의 재검사(지금도 `row.out` 을 그대로 쓴다) · 탭 기억(localStorage).

### U-10 개발자 도구 INSERT 생성 — 탭 둘로 가르기 — 확정

- **목표**: 「CREATE 문을 붙여 넣어 만들기」 와 「스냅샷 테이블로 만들기」 를 한 번에 하나만 보이게 한다. 한쪽 생성이 다른 쪽 결과를 덮어쓰지 않는다.
- **대상**: `tools/dev_tools.html` `#page-dummy` 마크업(308-352) · `tools/dev_tools_ext.js` INSERT 부분(144-192, `init` 288-289) · `scripts/puppeteer/smoke-devtools.js`(60-72).
- **화면 배치**:
  ```
  [CREATE 문에서] [스냅샷에서]                         ← #dm-tab-create · #dm-tab-snap (처음엔 CREATE)
  건수 [5]  ☐ 트랜잭션 종료문 추가  ☐ UPSERT 템플릿    ← 두 탭이 같이 쓰는 줄(id 그대로)

  ── CREATE 문에서(#dm-create) ────────────────────────
  DB [Tibero/Oracle ✔]  날짜 범위(년) [3]  [▷ INSERT 생성] [복사] [지우기]
  CREATE TABLE 문 붙여넣기 #create_input   | 생성된 SQL #dummy_out

  ── 스냅샷에서(#dm-snap) ─────────────────────────────
  스냅샷 [#2 dev · 테이블 184]  테이블 [COMTNBBSMASTER]  FK 조회 [dev]  방언 [oracle]  ☐ 입력 = CSV 값 행
  [▷ INSERT 생성] [복사] [지우기]   #ins_srv_msg
  #ins_warn
  CSV 값 행(켰을 때만 보임) #ins_csv_in      | 생성된 SQL #ins_out
  ```
- **변경 — `dev_tools.html`**:
  1. `#page-dummy` 맨 위에 `<div class="dm-tabs"><span class="t on" id="dm-tab-create">CREATE 문에서</span><span class="t" id="dm-tab-snap">스냅샷에서</span></div>`.
  2. 같이 쓰는 줄: `#dummy_count`·`#chk_commit`·`#chk_merge` 를 첫째 줄에서 이 줄로 옮긴다(id·속성 그대로 — `genFromCreate`·`insRun` 둘 다 이 id 로 읽는다).
  3. `#dm-create`: 지금 첫째 줄의 나머지(`#dummy_db`·`#dummy_years`·생성·복사·지우기 버튼, 인라인 `onclick`·`onchange` 그대로) + 지금의 `<div class="row">`(`#create_input` | `#dummy_out`).
  4. `#dm-snap`(처음엔 `display:none`): 지금 둘째 줄 327-341 을 옮긴다. id 는 그대로. `#ins_srv_btn` 글을 「▷ INSERT 생성」 으로. 옆에 `<button class="btn-green" id="ins_copy">복사</button>`·`<button class="btn-g" id="ins_clear">지우기</button>`. 그 아래 `#ins_warn`, 그리고 `<div class="row">` — 왼쪽 `<div class="col" id="ins_csv_col" style="display:none">` 안에 `<textarea id="ins_csv_in" placeholder="머리 줄 = 컬럼명, 값 행(쉼표 구분)">`, 오른쪽 `#ins_out`(`class="out-area" readonly`). `#ins_csv` 의 `title` 은 「아래 칸의 CSV 값 행(머리 = 컬럼명)을 이 테이블 타입에 맞춰 INSERT 로」.
  5. CSS 는 `.ext-*` 옆에 `.dm-tabs`(JSP 포매터 U-9 의 `.modes` 와 같은 꼴 — 순수본 `.tabs .t` 규칙을 빌려 쓴다)·`#dm-snap`.
  6. 순수본 JS 는 안 고친다. `#dummy_db` onchange·Ctrl+Enter·`switchTab('dummy')` 가 부르는 `genFromCreate` 는 이제 CREATE 탭의 `#dummy_out` 에만 쓴다 — 스냅샷 탭 결과는 안 건드린다.
- **변경 — `dev_tools_ext.js`**:
  ```js
  function dmMode(which)   // 'create' | 'snap' — #dm-create·#dm-snap 표시, 탭 .on, try { localStorage 'devt_dmMode' } catch
  // insRun: 결과·CSV 칸만 바꾼다. 같이 쓰는 줄은 지금처럼 읽는다
  if ($('ins_csv').checked) body.csv = $('ins_csv_in').value;   // 지금은 #create_input
  …
  $('ins_out').value = r.sql;                                     // 지금은 #dummy_out
  ```
  - `#ins_csv` change → `#ins_csv_col` 표시 토글.
  - `#ins_copy` → 순수본 `copyEl('ins_out')`. `#ins_clear` → `#ins_out`·`#ins_csv_in`·`#ins_warn` 비우기.
  - 스냅샷 탭에서 Ctrl+Enter: `document` 에 **capture** 단계 keydown 을 달아, `#page-dummy` 가 active 이고 `#dm-snap` 이 보일 때만 `e.preventDefault(); e.stopImmediatePropagation(); insRun();`. 순수본 리스너(1975-)까지 안 내려간다.
  - `init` 에서 `dmMode(localStorage 값 || 'create')` 와 리스너들.
- **검증**:
  - `scripts/puppeteer/smoke-devtools.js` 의 INSERT 절을 고친다(이 항목이 이름을 댄 수정): `switchTab('dummy')` → `#dm-tab-snap` 클릭 → 지금 절차 → `#ins_srv_msg` 「생성함」 → **`#ins_out` 값이 `INSERT` 를 포함** → `#dm-tab-create` 클릭 → 「▷ INSERT 생성」 클릭 → `#dummy_out` 이 「-- CREATE TABLE 문 입력」 → `#dm-tab-snap` 클릭 → `#ins_out` 이 그대로(덮어쓰지 않았다). CSV 모드 한 번 — `#ins_csv` 켬 → `#ins_csv_col` 보임 → 값 행 → 생성 → 「생성함」.
  - `SmokeHtmlUnitTest.opensWithoutScriptErrors`(dev_tools, JS 끔 — 로드만) · `ToolsFolderTest`(innerHTML 규칙) 초록. 서버 쪽은 안 바뀐다 — `InsertGenTest`·`InsertRoutes` 테스트 그대로.
- **의존**: 없음. U-9 와 탭 CSS 꼴을 맞추려면 같은 묶음에 둔다.
- **범위 밖**: 방언 select 둘의 통합(순수본은 mysql·pg, 서버는 mariadb·postgresql·tibero — 값이 달라 합치려면 순수본 JS 를 고쳐야 한다) · `dev_tools` 를 HtmlUnit JS 켜고 돌리기(0-?? 사유 그대로).

### U-11 결함 — 작업 취소가 H2 를 닫는다 — 확정

- **사실(실험 2026-10-04)**: `app.jar` 의 H2 2.3.232 로 `jdbc:h2:file:` DB 를 열고(접속 하나는 계속 열어 둠 — `Db` 의 keeper 와 같다), 작업 스레드가 INSERT 하는 도중 `Future.cancel(true)` 로 인터럽트했다. 1회째 새 접속이 `MVStoreException: Reading from file … failed`, 2회째 **「The database has been closed [90098-232]」 — keeper 접속까지 죽었다.** 같은 실험을 `jdbc:h2:retry:`·`jdbc:h2:async:` 로 세 번씩 돌리면 새 접속·keeper 모두 살았다. 실험 코드는 부록 C(검증 테스트 `DbInterruptTest` 가 같은 꼴).
- **영향**: `JobManager.cancel` → `Job.requestCancel` → `Future.cancel(true)`(`Job.java:210-223`)를 타는 모든 작업 — 스냅샷·분석·코드 검사·산출물·논리명. 지금은 화면에서 `DELETE /api/jobs` 를 부르는 곳이 없어 잠복해 있다(API·CLI 로는 부를 수 있다). U-1b 가 처음으로 화면에 중지 버튼을 단다.
- **대상**: `core/db/Db.java:32` · 새 테스트.
- **변경**:
  ```java
  // Db.open — retry: 는 인터럽트로 닫힌 파일 채널을 다시 연다. 파일은 같은 toolbox.mv.db
  String url = "jdbc:h2:retry:" + dir.resolve("toolbox").toString().replace('\\', '/');
  ```
  잠금 판정 `isLocked`·`LockedException`(같은 data 폴더 두 번 기동)이 그대로 도는지 확인한다.
- **검증**:
  - 새 `core/db/DbInterruptTest` — `Db.open(@TempDir)` 위에서 실험과 같은 꼴(작업 스레드 INSERT 루프 → `Future.cancel(true)` → 새 접속 `SELECT COUNT(*)` · `db.connect()` 성공)을 세 번. URL 을 `file:` 로 되돌리면 빨갛다(부숴 봄 — `scripts/probes/` 에 패치로 남길지는 번들 때).
  - 기존 `Db` 잠금·마이그레이션 테스트 초록 · 이미 있는 `toolbox.mv.db`(데모 `C:/workspace/toolbox-demo/data`)를 새 URL 로 열어 스냅샷 목록이 그대로인지 한 번(수동).
  - `bash scripts/verify.sh --full` — H2 를 쓰는 테스트 전부.
- **의존**: 없음. **U-1b 보다 먼저.**
- **실패 사다리**: `retry:` 가 잠금 판정이나 성능을 깨면 `async:` 로(실험에서 같이 버텼다). 그것도 안 되면 `Job.requestCancel` 을 `Future.cancel(false)` 로 바꾸고 작업 본문이 `checkCancelled` 로만 멈추게 한다(이력에 사유).

### U-12 벤더 딕셔너리 SQL 실패 시 물러서기 — 확정

- **사실**: 코멘트·행 수·생성일·UNIQUE 를 읽는 벤더 SQL 은 방언 클래스가 `conn.prepareStatement` 로 직접 돌리고 `catch` 가 없다. 조회 권한이 모자라거나(예: 다른 스키마의 딕셔너리) 옛 판에 그 뷰·컬럼이 없으면 스냅샷 전체가 FAILED 다. 뼈대는 이미 JDBC 로 읽었는데 버려진다.
- **대상**: `core/dialect/VendorMetaSource.java`(도우미) · `core/dialect/{Oracle,Postgres,Maria,Mssql}MetaSource.java`(`loadComments`·`loadStats`·`loadConstraints`) · `core/meta/MetaSource.java`(`warnings()`) · `core/meta/SnapshotService.java`(결과에 경고) · `core/meta/SnapshotStore.java` + V003(경고 저장, U-7 과 같은 파일).
- **변경**:
  ```java
  // MetaSource — 수집 중 물러선 자리. 기본은 없음
  default List<String> warnings() { return List.of(); }

  // VendorMetaSource
  static final int QUERY_TIMEOUT_SEC = 60;                       // U-1b ②
  private final Map<String, Integer> fallbacks = new LinkedHashMap<>();   // 종류 → 횟수(표마다 도는 UNIQUE 는 한 줄로 모은다)

  @FunctionalInterface protected interface SqlCall<T> { T get() throws SQLException; }

  /** 벤더 SQL 한 덩이. 실패하면 fallback 을 돌려주고 종류·SQLState·벤더 코드만 적는다 — SQL 글과 오류문은 안 남긴다(규칙 3) */
  protected <T> T vendor(String kind, T fallback, SqlCall<T> call) {
      try { return call.get(); }
      catch (SQLException e) { fallbacks.merge(kind + " (SQLState " + e.getSQLState() + ", 코드 " + e.getErrorCode() + ")", 1, Integer::sum); return fallback; }
  }
  protected PreparedStatement prepare(String sql) throws SQLException {   // 방언 클래스의 conn.prepareStatement 를 이것으로
      PreparedStatement ps = conn.prepareStatement(sql); ps.setQueryTimeout(QUERY_TIMEOUT_SEC); return ps;
  }
  @Override public List<String> warnings()   // 「코멘트를 못 읽었다 (SQLState 42000, 코드 942) — JDBC 값으로 대신했다」 꼴. 횟수 > 1 이면 「× n」
  ```
  - 방언 클래스: `loadComments(s)` → `vendor("코멘트", s, () -> …지금 본문…)`, `loadStats(s)` → `vendor("행 수·생성일", s, …)`, `loadConstraints(t)` → `Table base = super.loadConstraints(t); return vendor("UNIQUE 제약", base, () -> …지금 벤더 부분…)`. 물러선 값은 JDBC 가 준 그대로다(코멘트는 JDBC `REMARKS`, 행 수 null, UNIQUE 는 유니크 인덱스에서).
  - `JobCancelledException`(런타임)·JDBC 뼈대의 `SQLException` 은 안 잡는다 — 뼈대가 실패하면 스냅샷은 지금처럼 FAILED.
  - `SnapshotService.run`: 수집 뒤 `List<String> w = source.warnings()` → 결과 `"warnings": w`, 로그 `WARN` 은 건수와 종류만. 화면 done 문구 끝에 「 · 경고 n」, `#snapMsg` 아래에 줄마다(`textContent`).
  - 저장: V003 에 `snapshot.warnings VARCHAR(4000)`(JSON 배열, U-7 의 `scope` 와 같은 파일). `Summary` 에 `int warningCount`. 라벨 「 · 경고」(1-17).
- **검증**:
  - 새 `VendorFallbackTest` — H2 in-memory 에 표 둘을 만들고 `new OracleMetaSource(h2conn).collect(Scope.all())` → 예외 없이 표 둘·컬럼·PK 가 나오고 `warnings()` 가 세 종류(코멘트·행 수·UNIQUE). H2 에는 `ALL_TAB_COMMENTS` 가 없어서 벤더 SQL 이 전부 실패한다. Postgres·Mssql 수집기도 같은 꼴로 한 번씩(1-13).
  - **가림 방지**: 컨테이너 방언 넷 `*MetaSourceTest` 와 `DbCorpusBase` `meta` 절에 `assertTrue(source.warnings().isEmpty())` — 물러서기가 진짜 결함을 삼키면 여기서 빨갛다. `golden/meta/*.json` 불변.
  - `MetaRoutesTest` — 결과에 `warnings` 배열.
- **의존**: `1-11`(SnapshotService 를 같이 만진다). U-14 가 이 물러서기에 기댄다.
- **실패 사다리**: Oracle 에서 실패한 문 뒤 접속이 트랜잭션 오류 상태로 남는 방언(PG `25P02`)이 있으면 `vendor` 가 실패 뒤 `conn.rollback()`(autoCommit 이 꺼져 있을 때만)을 부른다.

### U-13 접속 실패 안내 — 확정

- **사실**: 접속 시험이 실패하면 드라이버 오류문만 보인다. 드라이버 jar 가 없을 때(`No suitable driver`)도, 드라이버와 DB 판이 안 맞을 때도 무엇을 바꿔야 하는지 말하지 않는다. `drivers/alt/` 에 바꿔 넣을 jar 가 있다는 것은 README 에만 있다.
- **대상**: `core/conn/ConnectionRegistry.java` `test`(97-109) · 새 `core/conn/ConnHints.java` + 리소스 `src/main/resources/conn/hints.yaml` · 새 `ConnHintsTest`.
- **변경**:
  ```java
  /** 접속 오류 → 한글 안내 한 줄. 맞는 줄이 없으면 empty */
  public final class ConnHints {
      public record Hint(String dialect, String sqlState, Integer errorCode, String message, String hint) {}   // message 는 정규식. 빈 칸은 안 본다
      public static ConnHints load();                                   // conn/hints.yaml, 정규식은 로드 때 컴파일(틀리면 기동 실패)
      public Optional<String> hint(String dialect, SQLException e);     // 위에서부터 첫 줄
  }
  ```
  - `ConnectionRegistry.test` 의 실패 분기: `message = mask(원문) + (hint 가 있으면 "\n→ " + hint)`. 로그는 지금처럼 예외 클래스 이름만.
  - `hints.yaml` 첫 줄(확실한 것 하나): `message: 'No suitable driver'` → 「drivers 폴더에 이 URL 을 받는 드라이버 jar 가 없다 — README 의 JDBC 드라이버 표를 본다(다른 판은 drivers\alt 에 있다)」.
  - **나머지 줄은 `V-21` 이 실제로 뜬 오류문으로만 더한다.** 추측으로 넣지 않는다. 뜰 것으로 보는 후보(미확인): Oracle 11g 에 ojdbc11(프로토콜·판 미지원) → 「drivers\alt 의 ojdbc8(또는 ojdbc6)으로 바꾼다」 · MySQL 에 MariaDB 드라이버(스킴·인증 플러그인) → 「drivers\alt 의 mysql-connector-j 로 바꾸고 url 을 jdbc:mysql: 로」 · MSSQL 2017 TLS → 「url 에 encrypt=false」.
  - 화면: `db_browser.html` `#connMsg` 는 `textContent` 로 넣는다 — 줄바꿈이 보이게 `white-space: pre-wrap`(없으면 더한다).
- **검증**: `ConnHintsTest` — yaml 로드·정규식 컴파일 · `SQLException("No suitable driver found for jdbc:nosuch:")` → 안내 · `V-21` 이 남긴 오류문 목록(`golden/corpus/dbold-conn-errors.txt`)의 줄마다 안내가 하나씩 맞는다. `ConnRoutes` 테스트 — url `jdbc:nosuch:x` 접속 시험 → `message` 에 「→ 」.
- **의존**: `V-21`(오류문 입력). `8-13`(같은 벤더 jar 둘 경고)은 번들 20 에서 먼저 친다.

### U-14 옛 판 DB 전 기능 검증 — 확정(번들 20 바로 뒤, `--full` 에 든다)

- **사실**: 컨테이너로 잰 판은 최신뿐이다(Oracle Free 23·PostgreSQL 17·MariaDB 11·SQL Server 2022). 현장 DB 는 Oracle 11g·12c, MySQL 5.7·8 이 흔하다. MySQL 은 MariaDB 로만 쟀다. 판에 따라 달라질 수 있는 것은 스냅샷 수집만이 아니다 — DDL 생성·방언 변환, INSERT·MERGE 생성, COMMENT 적용, 마스킹 SQL, 품질 진단 SQL, 08 코드값·09 DB 링크, 스니펫이 다 걸린다.
- **사용자 결정(2026-10-04)**: 판 다섯 전부 · **지금 최신판에서 도는 절 전부**를 옛 판에도 돌린다 · 시점은 번들 20 바로 뒤(기준선을 먼저 세우고, 뒤 번들은 옛 판까지 든 `--full` 로 닫는다) · `--full` 에 넣는다.
- **틀**: 최신판 표본 `web/DbCorpusBase`(추상, `@Tag("db") @Tag("corpus")`, 절 `@Order` 1 `insertRuns` · 2 `loadData` · 3 `meta`·`commentDdlRuns`·`snapshotDiffTwoReleases` · 4 `qualitySqlRuns` · 5 `snippets` · 6 `ddlRoundTrip` · 7 `maskingRuns`)를 옛 판 클래스가 **그대로 상속**한다. 태그를 물려받으니 `verify.sh --full` 이 저절로 돌리고 CI 는 저절로 건너뛴다(`-DexcludedGroups=corpus`). `verify.sh` 는 안 고친다.
- **판 다섯 [고정]**(양 끝만 — 가운데 판은 안 잰다):

  | 클래스 | 이미지 | 컨테이너 | 방언(`DbCorpus.Dialect`) | 드라이버(먼저 → 안 되면) |
  |---|---|---|---|---|
  | `Oracle11CorpusTest` | `gvenzl/oracle-xe:11-slim` | `GenericContainer`(환경 `ORACLE_PASSWORD`·`APP_USER`·`APP_USER_PASSWORD`, 포트 1521, 로그 「DATABASE IS READY TO USE!」 대기, 공유 메모리 1GB) | `ORACLE` | 테스트 classpath 의 ojdbc11 → `drivers/alt/ojdbc8*.jar` → `ojdbc6*.jar` |
  | `Mysql57CorpusTest` | `mysql:5.7` | `GenericContainer`(환경 `MYSQL_ROOT_PASSWORD`·`MYSQL_DATABASE`, 포트 3306) | `MARIA` | mariadb-java-client(`jdbc:mariadb:`) → `drivers/alt/mysql-connector-j*.jar`(`jdbc:mysql:`) |
  | `Mysql80CorpusTest` | `mysql:8.0` | 같음 | `MARIA` | 같음 |
  | `Mssql2017CorpusTest` | `mcr.microsoft.com/mssql/server:2017-latest` | 지금 `MssqlCorpusTest` 와 같은 `MSSQLServerContainer` | `MSSQL` | mssql-jdbc |
  | `Postgres12CorpusTest` | `postgres:12-alpine` | `PostgreSQLContainer` | `POSTGRES` | postgresql |
  Oracle 12c 는 무료 이미지가 없어 안 잰다. Tibero 는 컨테이너가 없다. 새 Testcontainers 모듈을 더하지 않는다 — MySQL·Oracle XE 는 core 의 `GenericContainer` 로 띄운다(규칙 5).
- **① 틀·접속·적재·메타(행 `V-21`)**:
  - `DbCorpusBase` 손질 둘 — `String goldenKey()`(기본 = 방언 이름. 옛 판 클래스가 `oracle11`·`mysql57`·`mysql80`·`mssql2017`·`postgres12` 로 덮는다. 골든·목록 파일 이름이 `db-<goldenKey>…` 가 된다 — 최신판 넷의 파일 이름은 안 바뀐다) · 절을 끄는 훅 `Set<String> skipped()`(기본 빈 집합).
  - `DbCorpus` 에 `connectWith(Path jar, String driverClass, String url, String user, String pw)` — jar 를 따로 연 `URLClassLoader` 에서 `Driver` 를 만들어 직접 `connect`. `DriverManager` 에 등록하지 않는다(다른 테스트에 안 샌다).
  - 옛 판 클래스의 `open()`: 드라이버 후보를 순서대로 시도. 실패하면 `(판, 드라이버, SQLState, 벤더 코드, 오류문 첫 줄)` 을 `golden/corpus/dbold-conn-errors.txt` 목록에 적고 다음 후보로. 붙은 드라이버를 골든에. `drivers/alt` 가 없어 후보가 다 떨어지면 그 클래스는 실패(「`bash scripts/bundle-fetch.sh` 로 drivers/alt 를 채운다」).
  - 이 행에서는 `skipped()` 로 1·3 의 `commentDdlRuns`·`snapshotDiffTwoReleases`·4·5·6·7 을 끄고 `loadDdl`·`loadData`·`meta` 만 돈다.
  - 새 `scripts/docker-pull-old.sh` — 이미지 다섯을 받는다(`--check` 는 로컬에 있는지만). `--full` 앞에 사람이나 실행 담당이 한 번 돌린다.
  - **A**: 판 다섯에서 스냅샷 예외 0 · 표 수·컬럼 수·PK 가 DDL(`DdlReader`)과 같다(V-9 `meta` 와 같은 대조). **B**: FK·코멘트 불일치 목록, `warnings()` 목록(옛 판에서 물러선 종류 — U-12).
- **② 나머지 절 + 기준선 + 결함 고치기(행 `V-22`)**: `skipped()` 를 비워 절 전부를 돈다.
  - **앱 몫 = A**(고친다, 상한 10건 — 4장 규칙. 넘치면 B 로 내리고 새 행): `insertRuns`(INSERT·MERGE 생성문이 그 판에서 실행된다) · `commentDdlRuns` · `ddlRoundTrip`(대상 방언 CREATE 가 그 판에서 실행된다) · `maskingRuns` · `meta`.
  - **순수본 몫 = 알려진 목록**(안 고친다, portfolio 로 넘긴다 — V-8 선례): `snippets`·`qualitySqlRuns` 의 실패. 목록 `db-<goldenKey>-snippets-a.txt`·`-quality-a.txt` 로 얼리고 새로 깨지면 빨강.
  - 판에 없는 기능이라 원리상 못 도는 것(예: Oracle 11g 의 IDENTITY, MySQL 5.7 의 CHECK·CTE)은 「옛 판 한계」 목록 `db-<goldenKey>-unsupported.txt` 에 사유와 함께 얼린다 — DDL 생성기가 판을 모르고 최신 문법을 내는 경우다. 생성기에 「대상 판」 옵션을 넣는 일은 새 행으로 올린다(이 번들에서 안 한다).
  - 골든 `golden/corpus/db-<goldenKey>.json`(절별 실행·실패·건수), `--full` 소요 시간을 이력에(⑳).
- **검증**: `bash scripts/docker-pull-old.sh` → `bash scripts/verify.sh --full` — 옛 판 클래스 다섯 초록(A 0, 목록은 얼린 대로) · 최신판 넷의 골든 불변.
- **의존**: `1-13`(물러서기 — 없으면 옛 판에서 벤더 SQL 하나가 실패할 때 스냅샷이 통째로 실패해 뒤 절을 못 본다). 메모리 — 컨테이너는 한 번에 하나, `--full` 앞에 메모리 확인(메모리 `full-verify-memory-contention`).
- **뒤 번들에 미치는 것**: `V-23`(수집 보강)·`V-24`(정의서) 가 `DbCorpusBase` 에 더하는 절은 옛 판 클래스도 탄다 — 번들 23·24 의 새 벤더 SQL(CHECK·용량)은 옛 판에서 도는지까지 보고 닫는다.
- **실패 사다리**: 어느 판이 이 PC 에서 안 뜨면(이미지·아키텍처·메모리) 그 판을 빼고 이력에 · `--full` 이 60분을 넘기면 옛 판 클래스를 별도 태그로 떼어 `verify.sh --legacy` 로 돌리는 것을 새 행으로 올린다(임의로 떼지 않는다 — 사용자가 `--full` 을 골랐다) · Oracle XE 11 컨테이너가 표본 DDL 을 용량 한도(XE 는 사용자 데이터 11GB)나 문자 집합 때문에 못 받으면 egov 대신 HR 원본만 넣고 이력에.



### U-15 CRUD 생성기 `egov4` 템플릿 세트 — 확정

- **사실**: 전자정부 4.x 는 실행환경 패키지가 `org.egovframe.rte` 로 바뀌었고 `javax` 를 그대로 쓴다. 지금 세트는 `egov35`(`egovframework.rte` + `javax`)와 `egov5`(`org.egovframe.rte` + `jakarta`)뿐이다.
- **대상**: 새 `templates/gen/egov4/set.yaml` · `.gitignore`(32-34 에 `!templates/gen/egov4/`) · `GenTemplatesTest`(46·68 의 세트 목록)·`GeneratorCorpusTest:80` · 골든 `golden/gen/egov4/` · `profiles/example.yaml`(generator 주석에 `egov4`).
- **변경**:
  ```yaml
  # CRUD 생성기 템플릿 세트 — 전자정부 표준프레임워크 4.x 세대(org.egovframe.rte + javax). 본문은 egov35 와 같고 변수만 다르다.
  name: egov4
  extends: egov35
  vars:
    rte: org.egovframe.rte
    ee: javax
    valid: "false"
  ```
  `valid` 는 egov35 와 같게 둔다(4.x 코드 꼴은 3.x 와 같고 패키지만 다르다).
- **검증**: `GenTemplatesTest` — 세트 셋을 그려 골든과 견주고 자기 엔진으로 다시 읽는다. 골든 `egov4/` 는 `egov35/` 와 `egovframework.rte` → `org.egovframe.rte` 줄만 다른지 diff 로 본다 · `GeneratorCorpusTest` 세트 셋 · `/api/generate/templates` 가 셋을 낸다(화면 `#set` 은 폴더를 읽어 저절로 뜬다). javac 는 안 돈다(egov5 와 같다 — 4.x jar 표본이 없다).
- **의존**: 없음. `package.sh` 는 `git ls-files templates` 로 담으니 따로 손대지 않는다.

## 5. 금지 사항

- `pure/` 를 고치지 않는다. 순수본 몫은 portfolio 지시문으로만 남긴다.
- 새 의존성을 넣지 않는다 — JPA 해석은 JavaParser, 클립보드 정리는 DOMParser. JSqlParser·CDN 금지.
- 적용된 마이그레이션 `V001__init.sql`·`V002__analyze.sql` 을 고치지 않는다(D3).
- `SqlRunner`·`/api/sql/run`·`/api/sql/export`·`XlsxWriter.writeCsv` 를 지우지 않는다(D4). `deliverable_sql.html` 의 `runSql` 은 손대지 않는다.
- 테스트를 고쳐 통과시키지 않는다. 고쳐도 되는 것은 이 문서가 이름을 댄 것뿐이다 — U-3 의 `sqlSnippetsRunOnConnection` 삭제, U-5b 의 `DefinitionsTest` R13·R15·R16·R17, U-5c 의 R7·R10 과 열 이름·위치 단언, U-5d 의 R5, U-9 의 `jspFormatterFolderBatch`(탭 클릭·문구), U-10 의 `smoke-devtools.js` INSERT 절.
- U-10 에서 `dev_tools.html` 의 순수본 JS(`genFromCreate`·`switchTab`·`copyEl`·Ctrl+Enter 리스너)를 고치지 않는다(D18).
- U-9 에서 `jsp_formatter.html` 의 순수본 JS 함수를 고치지 않는다(D16). 지우는 것은 백엔드가 더한 678-813 뿐이다.
- 골든은 diff 를 보고 의도된 변화일 때만 갱신하고 diff 요지를 이력에 적는다. 이 문서가 「불변」 이라 적은 골든이 바뀌면 멈춘다.
- 화면이 프로필 YAML 을 통째로 다시 쓰게 하지 않는다(코드 검사 두 줄 저장만 예외).
- 로그에 SQL·코드·스니펫 본문을 남기지 않는다(규칙 3). `127.0.0.1` 밖 호출을 만들지 않는다(규칙 1).
- PLAN.md 완료 행을 고치지 않는다 — 뒤집는 결정은 새 행과 해당 장 본문으로.
- 스냅샷 일시정지·xlsx 파일 → html·코드 검사 JPA 규칙·생성기 JPA 템플릿·물리/논리 ERD 를 끼워 넣지 않는다.
- 화면에 대상 표 고르기·스냅샷 범위 입력란을 만들지 않는다 — 거르는 조건은 프로필 YAML 로만(D20·D21).
- 정의서 xlsx 에 색·셀 메모를 넣지 않는다 — 등급은 `00_작성안내.xlsx` 로만(U-5d).
- 물러서기(U-12)는 **벤더 딕셔너리 SQL 에만** 건다. JDBC 뼈대 실패·취소 예외를 삼키지 않는다. 경고·로그에 SQL 글과 드라이버 오류문 전문을 남기지 않는다(SQLState·코드만).
- 접속 안내(U-13)에 추측한 오류문을 넣지 않는다 — `V-21` 이 뜬 것과 `No suitable driver` 만.

## 6. 최종 검증

항목(청크)마다:
1. `bash scripts/verify.sh` 초록 — 그 뒤 `git commit` 을 따로.
2. 그 항목의 「검증」 칸에 적은 테스트가 새로 생겼고 초록.

번들 끝에서:
1. `bash scripts/gate-probe.sh` — 「패치 갱신 필요」 가 나오면 `scripts/probes/*.patch` 를 다시 뜬다(U-1b·U-5b 가 게이트가 무는 코드를 바꿀 수 있다).
2. `bash scripts/verify.sh --full` — 방언 넷 `meta`·`snippets` 절 A 0, 표본 baseline 은 이 문서가 적은 것만 변화. 번들 21 뒤로는 옛 판 다섯도 여기서 돈다 — 앞에 `bash scripts/docker-pull-old.sh --check`.
3. `bash scripts/offline-build.sh` — 의존성을 안 더했는지.
4. 수동(집, Puppeteer·실브라우저): 스냅샷 중지·완료 문구 · 작업을 멈춘 뒤 다른 화면이 그대로 돈다(U-11) · 스니펫 화면에 접속·실행 줄이 없다 · 엑셀에서 병합 범위를 복사해 표 편집기에 붙인다 · JPA 표본 폴더로 분석 → CRUD 탭 · JSP 포매터·INSERT 생성 탭 전환.
5. 회귀 확인 대상: 산출물 생성(00 + 01~11 + 16~18, `DeliverableRoutesTest`) · 스냅샷 비교 · 논리명 변환 · egov 분석 수치(`AnalyzeCorpusTest`).

## 7. 중단 조건

다음이면 임의로 풀지 말고 멈추고 보고한다. 보고 형식: 어느 항목·스텝, 무엇이 예상과 달랐는지, 가능한 선택지.

- 2장의 구조·줄 번호가 실제 코드와 다르다(파일이 바뀌었으면 같은 이름의 함수로 다시 찾되, 함수가 없으면 멈춘다).
- 검증이 2회 연속 실패하고 원인이 그 항목 범위 밖이다.
- 5장을 어기지 않고는 못 간다.
- 항목이 적지 않은 파일을 3개 이상 고쳐야 한다.
- 「불변」 이라 적은 골든(`java-graph.json`·`analyze-egov*`·`diff-*.json`·`golden/meta/*`(U-1b 에서))이 바뀐다.
- U-11·U-1b: `retry:` 로 바꾼 뒤에도 취소 뒤 H2 가 닫힌다 — 실패 사다리로 가고 그것도 안 되면 멈춘다. U-11 없이 U-1b 의 중지 버튼을 내보내지 않는다.
- U-4: 실제 엑셀 클립보드 HTML 이 2장·4장이 가정한 꼴(`<table>` + `colspan`·`rowspan`, `mso-ignore:colspan`)과 다르다.
- U-5c: 열 이름을 research 문서의 별표 원문에서 못 찾는다(추측으로 이름을 짓지 않는다).
- U-12: 물러서기를 넣은 뒤 컨테이너 방언 넷에서 `warnings()` 가 비지 않는다 — 진짜 결함을 삼킨 것이다. 그 벤더 SQL 을 고친다(물러서기 조건을 넓혀서 덮지 않는다).
- U-13: `V-21` 이 뜬 오류문 없이 안내 줄을 더한다.
- U-14: 옛 판에서 A 가 10건을 넘는다(4장 규칙 — 넘치면 B 로 내리고 새 행).

## 8. 불확실 항목

2026-10-04 에 실험·조사·사용자 결정으로 닫았다. 실행 담당이 다시 확인할 것은 「남음」 줄뿐이다.

| # | 무엇 | 어떻게 닫았나 | 결과 → 반영 |
|---|---|---|---|
| ① | 작업 취소 인터럽트가 H2 를 닫는가 | 실험(`app.jar` 의 H2 2.3.232, INSERT 도중 `Future.cancel(true)`) | **닫힌다** — `file:` 은 2회째에 DB 전체가 닫혔고 `retry:`·`async:` 는 세 번 다 살았다 → 새 항목 U-11, D19 |
| ② | DB 브라우저 새 이름 | 사용자 | **「DB 스냅샷 · DTO 생성」** → U-0 |
| ③ | 열 이름·Not Null 표기·09 구조 | 사용자 | 표준 이름 · Not Null 여부 **Y = Nullable** · 09 항목 한 줄 → U-5c |
| ④ | 관리 열 기본값 | 사용자 | 최종수정일·변경구분 **빈칸** → U-5d |
| ⑥ | 병합 없는 붙여넣기의 정렬 | 사용자 | **살린다** → D8, U-4 |
| ⑧ | 없는 프로필로 기동 | 실험(`--profile nosuch`) | 서버가 뜨고 `/api/conn` **500** → U-8 8e |
| ⑨ | HtmlUnit 4.11.1 의 `DOMParser` `text/html` | 실험(엑셀 꼴 HTML) | **된다** — `colSpan`·class·`<style>` 글·`mso-ignore` 까지 읽힌다 → U-4 검증은 HtmlUnit 스모크 |
| ⑪ | 02 `태그정보`·`분류명` 출처 | 조사 | 어느 판에도 없다 — 확장 열로 남기고 `Grades` 「채우는 법」 에 「표준 항목 아님」 |
| ⑫ | 후보 U-2·U-7·U-8 | 사용자 | U-2 는 **YAML `deliverable.filter`**(화면 고르기 아님, D20) · U-7 은 **기록만**(D21) · U-8 은 넣는다 |
| ⑬ | 「결과 → 입력」·덮어쓰기 확인 창 | 사용자 | 버튼 **뺀다** · 확인 창 **띄운다** → U-9 |
| ⑭ | `/api/fs/list` 상한 | 코드 | `LocalFiles.MAX_FILES = 20_000`(`core/fs/LocalFiles.java:39`) — D17(행에 원본을 안 쌓는다)의 근거 |
| ⑮ | INSERT 공통 옵션 줄 | 사용자 | **탭 위 공통 줄** → U-10 |
| — | 등급 표시 방법 | 사용자 | `00_작성안내.xlsx` 한 장, 정의서는 깨끗이 → U-5d |
| — | 없는 표준 문서 | 사용자 | 상관도·엔터티·애트리뷰트 정의서를 만든다. **물리·논리 ERD 는 안 만든다** → U-5e, D22 |
| — | DB 판·드라이버 빈틈 넷 | 사용자(전부 넣는다) | 벤더 SQL 물러서기 U-12 · 접속 실패 안내 U-13 · 옛 판 컨테이너 U-14(`--full` 에) · `egov4` 세트 U-15 |
| — | 옛 판 이미지가 있는가 | `docker manifest inspect` | 있음 — `gvenzl/oracle-xe:11-slim`·`18-slim`·`21-slim`, `mysql:5.7`·`8.0`, mssql `2017-latest`·`2019-latest`. `postgres:12-alpine` 도 있음(재시도) |
| — | 번들 가름 | 설계(사용자 「번들 계획도 진행」) | 여섯 — 20 결함·스냅샷 / 21 옛 판 전 기능 / 22 화면 / 23 수집 보강 / 24 정의서 표준 / 25 JPA(4장 맨 앞) |

**닫음(2차 — 2026-10-04 저녁)**

| # | 무엇 | 어떻게 닫았나 | 결과 → 반영 |
|---|---|---|---|
| ⑦ | JPA 표본 | 웹 조사(GitHub 트리·원문) + 사용자 | **둘 다** — `shopizer`(3.2.7@6a4a0a65)·`egov-msa`(eGovFrame MSA 공통컴포넌트 main@4f5a895b). 둘 다 Apache-2.0 → U-6 표본, 행 `V-25` |
| — | QueryDSL | 조사(MSA 표본 저장소 73 중 `JPAQueryFactory` 37 파일) + 사용자 | **기본 꼴을 읽는다** → D23, 행 `6-16` |
| ⑯ | HtmlUnit 에서 클릭 → XHR → DOM 갱신이 잡히는가 | 기존 테스트 | 잡힌다 — `SmokeHtmlUnitTest.jspFormatterFolderBatch`(282-285)가 지금 같은 흐름(`#dirPreview` 클릭 → `/api/fs/list`·`read` → 표)을 `waitForBackgroundJavaScript` 로 통과한다 |
| ⑰ | 선언 안 된 FK 추정 규칙 | egov Oracle 매퍼 153개 실측 + 사용자 | 등식 642·표 쌍 139·선언 25·**강 26**·약 88 → 규칙은 U-5f, 싣는 곳은 D25, 행 `6-13`·`2-19` |
| ⑱ | PostgreSQL 옛 판 이미지 | `docker manifest inspect` 재시도 | `postgres:12-alpine` 있음(11·13 도) — 앞의 실패는 시간 초과 |
| — | 옛 판 검증의 범위·시점 | 사용자 | 판 다섯 × **전 기능**, **번들 20 바로 뒤**(번들 21), `--full` 에 든다 → D24, U-14 |

**남음**

| # | 무엇 | 걸린 항목 | 누가·언제 |
|---|---|---|---|
| ⑤ | 엑셀이 실제로 클립보드에 싣는 HTML. **이 PC 에는 엑셀·LibreOffice 가 없다**(레지스트리·설치 폴더 확인) | U-4(`4-14`) | 사람 — 엑셀 있는 PC 에서 병합·정렬·셀 안 줄바꿈이 든 범위를 복사하고, 브라우저 콘솔에서 `document.addEventListener('paste', e => console.log(e.clipboardData.getData('text/html')))` 로 떠서 `fixtures/table/` 에 넣는다. 그 전에는 U-4 에 적은 알려진 꼴로 손 픽스처 |
| ⑩ | 범정부 메타데이터 관리시스템(meta.go.kr) 업로드 엑셀의 실제 열 — 공개 자료 없음, 계정은 기관 직원만 | U-5c | 사람 — 현장 투입 때 발주처에서 받는다. 그때까지 별표4 를 따른다 |
| ⑲ | 옛 판에 어느 드라이버가 붙는가 — ojdbc11 23.8 → Oracle 11g XE, mariadb-java-client → MySQL 5.7·8.0 | U-14(`V-21`)·U-13(`1-18`) | 실행 담당 — 번들 21 의 첫 행 `V-21` 이 이 실험이다. 붙은 드라이버와 오류문이 `1-18` 의 입력 |
| ⑳ | 옛 판 다섯을 더한 `--full` 이 얼마나 걸리는가(지금 30~40분) | U-14(`V-22`) | 실행 담당 — `V-22` 끝의 `--full` 에서 재서 이력에. 60분을 넘으면 새 행(U-14 실패 사다리) |
| ㉑ | 옛 판에서 앱 몫 결함(A)이 몇 건 나오는가 — 한 번도 안 돌려 봐서 모른다 | U-14(`V-22`) | 실행 담당 — 10건까지 고치고 넘치면 B 로 내려 새 행(4장 규칙) |

---

## 부록 A. 표준 항목 요약

출처(2026-10-04 원문 확인):
- **S1** 「공공기관의 데이터베이스 표준화 지침」 행정안전부고시 제2025-19호(2025-02-24) — https://www.law.go.kr/LSW/admRulInfoP.do?admRulSeq=2100000255382 . 2023-18호부터 별지 서식이 없고 별표 항목 목록만 있다. 별표1 표준사전 · 별표2 산출물 · 별표4 메타데이터(`*` 필수).
- **S3** NIA 「공공데이터 품질관리 매뉴얼 v2.1」(2024.12) — https://www.data.go.kr/bbs/rcr/selectRecsroom.do?pageIndex=1&originId=PDS_0000000000000516 . 서식 13 오너십 · 14 업무규칙 · 15 테이블 대 응용프로그램 상관도 · 21 연계 데이터 목록.
- 감리·사업관리 가이드(NIA 2022·2023), SW사업 고시(제2023-15호)는 열을 정하지 않고 S1 으로 미룬다. 「공공데이터 관리지침」 은 제2021-70호가 현행(구조 항목 없음). 인덱스 열의 1차 근거는 NIA CBD 가이드(2011) D9 뿐.

| 문서 | 표준 항목 | 우리에만(확장) | 이름만 다름 |
|---|---|---|---|
| 01 DB정의서 | 기관명·부서명·**관련법령**·한글 DB명·영문 DB명·**구축일자**·DB 설명·**업무분류체계**·DBMS 정보·운영체제정보·**DB 형태** (+별표4 테이블 수\*·데이터 용량) | 적용업무(구판 항목) | 논리DB명=한글 DB명 · 물리DB명=영문 DB명 · DBMS명+버전=DBMS 정보 |
| 02 테이블정의서 | **영문 DB명**·테이블 소유자·한글/영문 테이블명·테이블 유형·관련 엔터티명·테이블 설명·발생주기 (+별표4 테이블 볼륨·**공개/비공개 여부\***·**개방데이터목록**) | 순번·보존기간·예상발생량(구판)·분류명·태그정보(출처 없음)·담당부서·담당자·최초등록일·최종수정일·최종수정자·변경구분 | 갱신주기=발생주기 · 테이블용도≈테이블 유형(미확정) |
| 03 컬럼정의서 | 영문 테이블명·한글/영문 컬럼명·컬럼 설명·연관 엔터티명·연관 속성명·데이터 타입·데이터 길이·Not Null 여부·PK정보·**AK정보·FK정보·제약조건·개인정보 여부·암호화 여부·공개/비공개 여부** | 순번·스키마명·기본값(표준은 제약조건 칸 안)·관리 열 여섯 | Not Null — **표준 값은 Y = Nullable, N = Not Null** |
| 05 표준단어 | 표준단어명·영문명·영문약어명·설명·형식단어 여부·도메인 분류명·이음동의어·금칙어·제정일자 | 순번·기관명·DB명·관리부서명·특이사항 | — |
| 06 표준도메인 | 그룹명·분류명·도메인명·설명·데이터타입·길이·소수점 길이·저장형식·표현형식·단위·허용값·제정일자 | 같음 | — |
| 07 표준용어 | 표준용어명·영문명·영문약어명·설명·표준도메인명·허용값·관리부서명·표준코드명·업무분야·제정일자 | 순번·기관명·DB명·특이사항·행정표준코드명(별표3 항목) | — |
| 08 표준코드 | 관리부서명·한글/영문코드명·**코드설명**·데이터타입·길이·코드값·코드값 의미·제정일자 | 순번·기관명·DB명·사용여부·특이사항·코드값설명(구판) | 코드명(한글)=한글코드명 |
| 09 연계 목록(S3 서식21) | 연계정보 구분(제공·활용)·연계 정보명·연계 주기·연계 항목명·**설명·데이터 타입·길이·출처 DB·테이블·컬럼**·제공기관·활용기관·비고 — 항목 하나가 한 행 | 순번·방식·연계기간 | 송/수신=연계정보 구분 |
| 04 관계 · 10 인덱스 · 11 제약 | 표준에 문서 없음. 내용은 03 의 PK·AK·FK·제약조건 칸에 흩어져 있다. 04 는 데이터기반행정법 제16조 「데이터관계도」 를 보조 근거로 | 문서 전체 | — |

표준이 요구하는데 없는 문서(만드는 것은 U-5e — 엔터티·애트리뷰트 정의서·상관도. ERD 둘은 안 만든다): 엔터티정의서·애트리뷰트정의서·논리 ERD·물리 ERD(필수) · 오너십 정의서·업무규칙 정의서·테이블 대 응용프로그램 상관도(권장).
관리 열(최초등록일·최종수정일·최종수정자·변경구분)은 2017~2025 어느 판에도 테이블·컬럼 항목으로 없다. 담당부서·담당자는 오너십 정의서(S3 서식13)에 근거가 있다.

## 부록 B. 역설계 가능 판정

● 핵심 항목 자동 · ◐ 핵심 일부가 추정·수동 · ○ 핵심이 DB·소스에 없음

| 문서 | 근거 | 판정 | 자동 | 추정 | 수동 | 지금 |
|---|---|---|---|---|---|---|
| 테이블정의서 | 필수 | ● | 영문 DB명·소유자·영문 테이블명·볼륨·물리 유형 | 한글명(코멘트 없을 때)·관련 엔터티명·논리 유형 | 발생주기·공개 여부·개방데이터목록 | 02 |
| 컬럼정의서 | 필수 | ● | 영문명·타입·길이·Not Null·PK·AK·FK(선언분)·기본값 | 한글명·연관 엔터티/속성명·개인정보 여부·선언 안 된 FK | 암호화·공개 여부 | 03(열 부족) |
| 표준코드 | 필수 | ● | 코드값·의미 | 어느 표가 코드표인지 | 코드설명·관리부서 | 08 |
| 인덱스정의서 | 확장 | ● | 이름·구분·컬럼·순서·유니크·정렬(U-5b 뒤) | — | — | 10 |
| 제약조건정의서 | 확장 | ● | PK·UNIQUE·FK·CHECK(U-5b 뒤) | — | — | 11 |
| 테이블 대 응용프로그램 상관도 | 권장 | ● | — | 프로그램 × 테이블 CRUD | — | 분석 xlsx |
| 데이터베이스정의서 | 필수 | ◐ | DB명·DBMS 정보·테이블 수·용량(권한) | 구축일자·운영체제 | 기관·부서·관련법령·한글 DB명·설명·업무분류체계 | 01 |
| 표준단어·도메인·용어 | 필수 | ◐ | 타입·길이·약어명 | 컬럼명 쪼개기·사전 대조·접미어 분류 | 뜻·허용값·단위·업무분야 | 05·06·07 |
| 엔터티·애트리뷰트 정의서 | 필수 | ◐ | 식별자·선언된 참조 | 표·컬럼을 옮긴 판 | 주제영역·서브타입·정의 의도 | 없음 |
| 물리 ERD | 필수 | ◐ | 표·컬럼·PK·선언된 FK | 선언 안 된 관계 | 배치·주제영역 | **안 만든다**(D22) — 04 로 갈음 |
| 테이블관계정의서 | 확장 | ◐ | 선언된 FK·규칙(U-5b 뒤) | 선언 안 된 관계 | — | 04 |
| 연계 데이터 목록 | 필수(연계 시) | ◐ | DB 링크 | 이름 꼴·뷰 | 주기·기관·항목 출처 | 09(후보만) |
| 논리 ERD | 필수 | ○ | — | 물리 ERD 에 논리명을 붙인 대체물 | 논리 모델 | 안 만든다(D22) |
| 오너십 정의서 | 권장 | ○ | — | — | 전부 | 없음 |
| 업무규칙 정의서 | 권장 | ○ | — | CHECK·트리거가 단서 | 전부 | 없음 |

## 부록 C. H2 인터럽트 실험 코드(U-11)

`java -cp target/app.jar H2Interrupt.java` 로 돌렸다. URL 의 `file:` 을 `retry:`·`async:` 로 바꾸면 세 번 다 산다.

```java
import java.sql.*;
import java.nio.file.*;
import java.util.concurrent.*;

public class H2Interrupt {
    public static void main(String[] a) throws Exception {
        Path dir = Files.createTempDirectory("h2int");
        String url = "jdbc:h2:file:" + dir.resolve("toolbox").toString().replace(java.io.File.separatorChar, '/');
        Connection keeper = DriverManager.getConnection(url, "sa", "");   // Db 처럼 열어 두는 접속
        try (Statement s = keeper.createStatement()) { s.execute("CREATE TABLE t(id INT, v VARCHAR(4000))"); }
        ExecutorService ex = Executors.newFixedThreadPool(2);
        for (int round = 1; round <= 5; round++) {
            CountDownLatch started = new CountDownLatch(1);
            Future<?> f = ex.submit(() -> {
                try (Connection c = DriverManager.getConnection(url, "sa", "")) {
                    c.setAutoCommit(false);
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO t VALUES(?,?)")) {
                        for (int i = 0; i < 2_000_000; i++) {
                            ps.setInt(1, i); ps.setString(2, "x".repeat(500)); ps.executeUpdate();
                            if (i == 2000) started.countDown();
                            if (i % 5000 == 0) c.commit();
                        }
                    }
                    c.commit();
                } catch (Throwable e) { System.out.println("  worker: " + e.getClass().getSimpleName() + " " + String.valueOf(e.getMessage()).split("\n")[0]); }
                return null;
            });
            started.await();
            Thread.sleep(50);
            f.cancel(true);
            Thread.sleep(500);
            try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM t")) {
                rs.next(); System.out.println("round " + round + " after cancel: new conn OK, rows=" + rs.getLong(1));
            } catch (Throwable e) { System.out.println("round " + round + " after cancel: NEW CONN FAIL " + e.getMessage().split("\n")[0]); }
            try (Statement s = keeper.createStatement(); ResultSet rs = s.executeQuery("SELECT 1")) {
                rs.next(); System.out.println("  keeper OK");
            } catch (Throwable e) { System.out.println("  keeper FAIL " + e.getMessage().split("\n")[0]); }
        }
        ex.shutdownNow();
    }
}
```
