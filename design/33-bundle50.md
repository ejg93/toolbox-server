# 설계 33 — 번들 50: 프로그램 분석 「위험 문장」 탭 · SELECT 컬럼 DB 대조 (Fable 설계 · Opus 실행, 2026-10-23)

## 맥락
설계 32 와 같은 날의 결정. 코드 검사가 `${}`(`mybatis.dollar`·`security.sqlDollar`)를 한 줄 안에서 지적하지만 「그 문장에 어느 URL 이 닿나」는 프로그램 분석의 호출 그래프에만 있다. 정합성 탭(6-11)은 코드↔DB 를 **표** 단위까지만 맞댄다 — 컬럼 이름이 틀린 SELECT 는 실행해야 안다. 둘 다 프로그램 분석 화면에 둔다(새 화면 없음). 사용자 결정 2026-10-23: 「둘 다 다음 번들(50)로 설계」.

실행자는 Opus. 차례는 `S0`(설계 32 의 S0 에 든다) → `V-29`(표본 받기 — 네트워크) → `6-33a` → `6-33b` → `6-33c` → `6-34a` → `6-34b` → 마무리. 가지 `work/<날짜>`(번들 49 머지 뒤). 머지 위임(사용자 2026-10-23 — 셋 다 이어서: CI 초록·AI 리뷰 처분 뒤 머지하고 다음 번들로).

---

## 1. 목표와 범위
- 목표: 프로그램 분석이 ① `${}` 든 매퍼 문장마다 거기 닿는 프로그램(URL)을 보여 주고 ② 정합성 탭에서 사용자가 접속을 골라 누르면 정적 SELECT 를 실제 DB 에 `WHERE 1=0` 으로 던져 컬럼·문법 오류를 DB 가 말하게 한다.
- 체감: 탭 일곱째 「위험 문장」. 정합성 탭에 「DB 에 물어보기(SELECT 만)」 줄과 결과 표.
- In scope: `MapperIndex`(`${}` 이름·동적 표지) · `AnalyzeRunner.Result.risks` · V014 `analyze_risk` · API `GET /api/analyze/runs/{id}/risk` · 탭 · xlsx 시트 「위험문장」 · `core/analyze/SqlProbe` · 작업 `POST /api/analyze/runs/{id}/probe` · 정합성 탭 UI · H2 시험 · 벤더 넷 컨테이너 절 · 스모크.
- Out of scope: `${}` 를 안전하다고 판정하기(허용 목록 추적 — 못 한다, 「꼴」 추정까지) · 동적 SQL 의 가지 전개 · INSERT·UPDATE·DELETE 대조(읽기 전용 — 절대 규칙 6) · 결과 저장(H2 에 안 남긴다) · 컬럼 단위 영향도(6-9 조건부 그대로).

## 2. 현재 구조 요약
- `core/analyze/MapperIndex.scan(List<Source>)`: SAX `Handler` 가 `<select|insert|update|delete|sql id>` 를 `Raw(ns, id, tag, text, keys, file, line)` 로 모은다 — 동적 태그는 글만 남긴다(`when`/`otherwise` 는 `SqlTables.BRANCH` 표식, `where`/`set`/`trim` 은 접두어, 나머지 `\n`) · `<include refid>` 는 `INC_OPEN refid INC_CLOSE` 표식 → `include()` 가 조각을 끼운다 · `Statement(id, tag, verb, refs, selectKey, files, file, line)` 는 식별자뿐(규칙 3). 방언 파일 여럿이면 한 `nsId` 로 합친다(`files` 목록).
- `AnalyzeRunner.run`: 파일 목록 → `MapperIndex.scan(xml)` → `JspLinks` → `JpaIndex` → `JavaGraph.scan` → 프로그램마다 `statements` 를 `index` 에 맞춰(`literal`·`var`·`mapper` 는 `index.get`, `prefix` 는 `startsWith`) CRUD → `Result(rows, tables, unresolved, …, orphans, joins, viewFiles, jspLinkKinds)`. 「안 불리는 문장」은 `orphans(kind statement)`.
- `AnalyzeStore.save`: `analyze_run` → 프로그램마다 `analyze_program`(id 발급) + `analyze_view`·`analyze_stmt(program_id, ns_id, resolution)`·`analyze_crud` → `analyze_jsp_link`·`analyze_view_file`·`analyze_join`·`analyze_orphan`·`analyze_unresolved`. `analyze_stmt` 의 `prefix` 는 접두 글 그대로(전개 안 됨).
- `web/AnalyzeRoutes.register(app, jobs, db, files, active)` — `ConnectionRegistry` 를 안 받는다(`App.java:136` 의 `conns` 는 Conn·Profile·Meta·Deliverable 라우트만). 정합성 `GET /runs/{id}/consistency?snapshotId=` → `Consistency.of(store, snapshots, id, snap)`. xlsx `POST /runs/{id}/export` 는 `LinkedHashMap<String, ResultTable> sheets` 에 시트를 더하는 꼴.
- 화면 `program_analysis.html`: `.tabbar` 에 `.t` 여섯(`tabPrograms`…`tabConsistency`), `TABS` 배열(`program_analysis_ext.js:55`)·`showTab`. 정합성 pane: bar(`#conSnap`·`#conRun`·`#conMsg`) + `#conScope` + `.grid`(2열 — 셀 다섯 `#conMissing`·`#conUnused`·`#conDead`·`#conOrphan`·`#conMissingJsp`). `consistency()` 가 `TB.table` 로 채운다.
- DB 접속: `ConnectionRegistry.open(id)` 만(ArchUnit `driverManagerOnlyFromRegistryAndH2`) — `ReadOnlyJdbc.wrap` 을 지난다(9-1). `SqlRunner.run(conn, sql, binds, maxRows, timeoutSec)` 는 `setObject` 바인드라 Oracle 에서 null 바인드가 깨진다 → 대조는 제 `PreparedStatement` 로 `setNull(i, Types.VARCHAR)`.
- 시험: `AnalyzeRoutesTest.project(dir)` 픽스처(컨트롤러 13 프로그램·`Board_SQL.xml`·`Login_SQL.xml`, `${}` 없음) · `MapperIndexTest` · `AnalyzeCorpusTest`(`@Tag corpus`, egov·shopizer·egov-msa 골든) · `DbCorpusBase`(`@Order` 절, `conn` 필드, `golden.put`·`CorpusFiles.conformance`, 벤더 넷 + 옛 판 다섯 — 표본 DDL 의 `names()` 로 `invoice()`·`invoiceId()`·`customerId()`) · `SmokeHtmlUnitTest.programAnalysisRuns`·`programAnalysisMenuCsv`.
- 레인: `core/analyze`·tools·시험 `core/analyze` 는 corpus. V014(`resources/db`)·`DbCorpusBase` 는 db → 마무리에 `--db`.
- 함정: ① eGov 표본 매퍼는 문장 파일 153 × 방언 8 = 1,224 파일, 같은 `nsId` 가 여덟 번 — 대조는 **접속 방언에 맞는 파일 하나**를 골라야 한다. ② eGov 표본에 `${}` 가 **0** 이다(실측) — 위험 문장 골든은 픽스처로만, 표본은 「예외 없음·0건」 불변식. ③ SAX 가 CDATA 를 벗기므로 `text` 는 SQL 글 그대로. ④ `Raw.text` 는 메모리에만 — 대조는 매퍼 파일을 **다시 읽는다**(H2 에 SQL 이 없다, 규칙 3). ⑤ `AnalyzeRunner.run` 의 `prefix` 해석은 프로그램마다 `index.statements()` 를 훑는다 — 위험 문장의 「닿는 프로그램」도 그 루프에서 같이 모은다(두 번 훑지 않는다). ⑥ Oracle 은 서브쿼리 별칭에 `AS` 를 못 쓴다 → `) tb_probe` 꼴(AS 없이) — 넷 다 받는다. 끝 `;` 는 Oracle 이 거절 → 뗀다.

### 실측(2026-10-23)
eGov 공통컴포넌트 매퍼: 문장 9,903(방언 중복 7,413 → 고유 약 1,240) · select 5,734 가운데 동적 태그(`if·choose·foreach·where·set·trim·bind`) 없는 정적 select 2,758(48%) · `${}` 0. 즉 대조는 select 의 절반을 본다 — 나머지는 「건너뜀(동적)」으로 센다.

### 표본(2026-10-23 조사 — 사용자 「50·51 용 표본을 GitHub 에서 구해 받는 것까지 플랜에」)
- **이미 있는 것이 DB 대조를 받친다**: eGov 표본에 `script/{ddl,dml,comment}/<8방언>` SQL 960 이 들어 있고 `DbCorpus.ddl()` 이 oracle·postgres·maria 컨테이너에 **eGov DDL(표 182)** 을 이미 적재한다(`DbCorpusBase.loadDdl`). 그러니 eGov 매퍼 고유 약 1,240 문장(정적 select 약 345)을 그 컨테이너에 던지는 끝까지 가는 검증이 새 표본 없이 된다 — 6-34a `probeRuns` 가 한다. MSSQL 은 eGov DDL 이 없어 chinook 만(시험이 만든 문장 둘).
- **`${}` 실물은 없다** → 새 표본 `ruoyi`: `yangzongzhuan/RuoYi-Vue`(MIT, 커밋 `13db1fcef36bee9ce45d2d636a1d4e8f5ed5bbc3` 2026-08-26) — Spring Boot + MyBatis 전형(컨트롤러 25·Java 266·매퍼 XML 20), `${}` 6 자리(`SysDeptMapper`·`SysRoleMapper`·`SysUserMapper`×3 의 `${params.dataScope}` — AOP 가 끼우는 권한 조건, 「값」 꼴 · `GenTableMapper` 의 `${sql}`), `sql/ry_20260417.sql`(MySQL DDL 31 표, 723줄)·`sql/quartz.sql`(11 표). MariaDB 컨테이너에 그 DDL 을 넣으면 RuoYi 매퍼 대조도 끝까지 간다. 대안으로 본 JeecgBoot(Apache-2.0)는 매퍼 XML 9·`${}` 1(MyBatis-Plus 어노테이션 위주)이라 뺐다.
- 번들 51 용은 설계 34 §2(jspwiki-bin·ruoyi-bin).

## 3. 설계 결정

**6-33 위험 문장** [고정]
- `MapperIndex.Raw` 에 `boolean dynamic`(핸들러가 `if·choose·when·otherwise·foreach·where·set·trim·bind` 를 만나면 true) — `Statement` 에 `List<String> dollars`(글에서 `\$\{\s*([\w.]+)` 의 이름, 정렬·중복 없음, 방언 파일 합집합)·`boolean dynamic`(어느 방언 파일이든 true 면 true). `<sql>` 조각의 dynamic 은 끼운 문장에 전파(`include()` 가 돌려주는 글에 표식을 넣지 않는다 — `fragments` 옆에 `Map<String, Boolean> fragDynamic` 을 두고 `include` 할 때 OR).
- `AnalyzeRunner`: 프로그램 루프에서 `hits` 마다 `s.dollars()` 가 비어 있지 않으면 `riskPrograms.computeIfAbsent(nsId).add(rowIndex)`. 끝에 `index.statements()` 가운데 dollars 있는 것 전부로 `List<Risk>`:
  ```java
  /** ${} 든 문장(6-33). programs — 닿는 프로그램의 rows 색인(없으면 빈 목록 = 아무도 안 부른다) */
  public record Risk(String nsId, String file, int line, List<String> names, List<Integer> programs) {}
  ```
  `Result` 에 `List<Risk> risks`(기존 생성자 둘은 빈 목록).
- V014 `src/main/resources/db/migration/V014__analyze_risk.sql`:
  ```sql
  -- 6-33 ${} 든 매퍼 문장 — 식별자·${} 이름·닿는 프로그램. 문장 하나에 프로그램마다 한 행, 아무도 안 부르면 program_id NULL 한 행. SQL 글 없음(규칙 3)
  CREATE TABLE analyze_risk (
    run_id      BIGINT        NOT NULL,
    ns_id       VARCHAR(300)  NOT NULL,
    file        VARCHAR(1000),
    line        INT,
    names       VARCHAR(300)  NOT NULL,
    program_id  BIGINT,
    FOREIGN KEY (run_id) REFERENCES analyze_run(id) ON DELETE CASCADE
  );
  CREATE INDEX ix_analyze_risk_run ON analyze_risk(run_id);
  ```
  `names` 는 쉼표로 잇고 300자 컷.
- `AnalyzeStore.save`: 프로그램 INSERT 루프에서 `rowIndex → pid` 를 `long[]` 에 담아 두고, 끝에 `analyze_risk` INSERT. `AnalyzeStore.risks(runId)`:
  ```java
  public record RiskProgram(long programId, String className, String method, String verb, String url) {}
  /** shape — 정렬·컬럼 추정(이름이 (?i)(sort|order|col|column|field|dir|asc|desc|tbl|table|schema|by) 를 품음) / 값 */
  public record RiskRow(String nsId, String file, int line, List<String> names, String shape, List<RiskProgram> programs) {}
  public List<RiskRow> risks(long runId)
  ```
  정렬: 프로그램 수 내림차순 → nsId. `shape` 글은 「정렬·컬럼 추정」·「값」.
- API `GET /api/analyze/runs/{id}/risk` → `List<RiskRow>`. xlsx 시트 「위험문장」(문장·파일·줄·`${}` 이름·꼴·프로그램 수·닿는 URL(`verb url` 를 `; ` 로)).
- 화면: `.tabbar` 끝에 `<span class="t" id="tabRisk">위험 문장</span>` + `<div class="pane" id="paneRisk">`: bar — `찾기 <input id="fRisk">`(문장·이름·URL) · `<label class="opt"><input type="checkbox" id="riskOrphanOnly"> 안 불리는 것만</label>` · `<span class="count" id="riskCount">` · 안내 `.hint` 「`${}` 는 바인드가 아니라 글 치환이다 — 값이 사용자 입력에서 오면 SQL 삽입. 「정렬·컬럼 추정」은 이름으로 짐작한 것이라 Java 쪽 허용 목록을 사람이 확인한다」. 표 `#riskResult`(`TB.table`): 문장 · 파일:줄 · `${}` 이름 · 꼴 · 프로그램 수 · 닿는 URL(앞 셋 `verb url`, 더 있으면 ` 외 n`). 행 클릭 → `#riskDetail`(`.well`)에 프로그램 전부 `클래스.메서드 — verb url` 줄마다. `load(id)` 가 `/risk` 도 받아 `renderRisk()`.
- 버린 대안: 코드 검사 결과(`mybatis.dollar`)와 잇기(두 이력의 파일·줄을 맞춰야 하고 그래프는 여기 있다) · `${}` 이름을 안 저장(식별자라 저장해도 규칙 3 안 — `analyze_join` 의 컬럼 이름과 같은 급) · 허용 목록 추적(JavaGraph 로 변수 출처를 따라가는 것 — 번들 하나 더, 기한 밖).

**6-34 SELECT 컬럼 DB 대조** [고정]
- 새 `core/analyze/SqlProbe.java`(package-private 입력은 `MapperIndex` 의 `Raw` — 같은 패키지):
  ```java
  /** 판정 — PASS·ERROR·SKIP_DYNAMIC·SKIP_DOLLAR·SKIP_NOT_SELECT·SKIP_NO_DIALECT_FILE */
  public record Outcome(String nsId, String file, int line, String status, String message) {}
  public record Report(int total, int passed, int failed, int skipped, List<Outcome> rows) {}
  /** @param dialect 접속의 dialect 글(oracle·tibero·postgresql·mariadb·mysql·mssql·h2) — 방언 파일 고르기 */
  public static Report run(List<Source> xml, Connection conn, String dialect, JobContext ctx) throws IOException
  ```
  - 문장 모으기: `MapperIndex.scanRaw(xml)`(새 package-private — `scan` 의 앞 절반을 떼어 `List<Raw>` + `fragments` + `fragDynamic` 을 돌려준다; `scan` 도 이것을 쓴다). `nsId` 마다 `Raw` 가 여럿이면 파일 이름(소문자)에 방언 낱말이 든 것 — `oracle`→`oracle`, `tibero`→`tibero`(없으면 `oracle` 파일), `postgresql|postgres|pg`→`postgres`, `mariadb`→`maria`(없으면 `mysql`), `mysql`→`mysql`, `mssql|sqlserver`→`mssql`, `h2`→아무 파일(첫 것). 하나면 그것. 못 고르면 `SKIP_NO_DIALECT_FILE`.
  - 건너뜀: `tag != select` → `SKIP_NOT_SELECT`(센 뒤 rows 에는 안 싣는다 — total 에서 뺀다) · `dynamic` → `SKIP_DYNAMIC` · 글에 `${` → `SKIP_DOLLAR`.
  - SQL 만들기: `include()` 로 조각을 끼운 글 → `#{…}`(중괄호 안 `}` 까지 비탐욕) 을 `?` 로 → 양끝 공백·끝 `;` 떼기 → `"SELECT * FROM (\n" + sql + "\n) tb_probe WHERE 1=0"`.
  - 실행: `conn.prepareStatement(wrapped)` → `?` 수만큼 `setNull(i, Types.VARCHAR)` → `setQueryTimeout(10)`(모르는 드라이버면 무시) → `setMaxRows(1)` → `executeQuery()` 즉시 닫기 → `PASS`. `SQLException` → `ERROR`, message = 첫 줄(`DbCorpusBase.firstLine` 꼴 — `(conn=N)` 접두 떼기), 200자 컷. 문장 사이 `ctx.checkCancelled()`·`progress(i*100/n, "i/n 문장")`. 자동 커밋은 건드리지 않는다(읽기 전용 래퍼가 쓰기를 막는다, 9-1).
  - 결과는 응답에만 — H2 에 안 남긴다(사용자 코드 SQL 도, DB 오류 글도). 로그에 SQL 을 안 적는다(규칙 3).
- 라우트(`AnalyzeRoutes` — `register` 에 `ConnectionRegistry conns` 를 더하고 `App.java` 호출을 고친다): `POST /api/analyze/runs/{id}/probe {connId}` → 202 `{jobId}`; 작업: `store.run(id)` 의 `path` 에서 `files.list(path, ["*.xml"])` 로 매퍼만 읽어(`<mapper` 든 것) `conns.open(connId)` → `SqlProbe.run` → `conn.close()`(try-with-resources) → `RESULT` 이벤트에 `Report`. `connId` 없음 400 · 이력 없음 404 · 폴더 없음 404. `POST /api/analyze/probe-xlsx {rows: [Outcome…], runId}` → 화면이 받은 결과를 그대로 xlsx(「DB대조-<runId>.xlsx」, 열 문장·파일·줄·판정·DB 메시지) — 결과를 서버가 안 들고 있으니 돌려받는다.
- 화면(정합성 탭): bar 둘째 줄 `<div class="bar">`: `접속 <select id="probeConn" aria-label="접속">`(`GET /api/conn` 의 id, `hasPassword` 없으면 라벨에 「(비밀번호 없음)」) · `<button class="btn-p" id="probeRun">DB 에 물어보기(SELECT 만)</button>` · `<button class="btn-red" id="probeStop" disabled>중지</button>` · `<button class="dl" id="probeXlsx" disabled><span class="ico ico-xlsx" aria-hidden="true"></span>DB 대조</button>` · `<span class="count" id="probeMsg">정적 SELECT 를 WHERE 1=0 으로 보내 컬럼·문법 오류를 DB 가 말하게 한다 — 동적 태그·${} 든 문장은 건너뛴다</span>`. `.grid` 에 셀 여섯째 `<div class="cell" style="grid-column: 1 / -1">`(화면 `<style>` 에 `#paneConsistency .cell.wide { grid-column: 1 / -1; }` 로 — 인라인 style 금지) `DB 가 거절한 SELECT` → `#probeResult`(표: 문장·파일:줄·판정·DB 메시지 — `ERROR` 먼저, 그다음 `PASS`·건너뜀은 count 에만 `통과 n · 오류 n · 건너뜀 n(동적 a·${} b·방언 파일 없음 c)`). 작업은 `TB.sse`/폴링(화면의 `poll` 꼴 그대로), 중지는 `DELETE /api/jobs/{id}`.
- 버린 대안: `PreparedStatement.getMetaData()` 로 실행 없이 보기(MySQL·MariaDB 드라이버가 서버 준비 없이는 null 을 주거나 실행한다 — 벤더마다 다르다) · `SqlRunner` 재사용(`setObject(null)` 이 Oracle 에서 깨진다) · 동적 태그 전부 켜서 전개(가지마다 다른 SQL 이 되고 `<foreach>` 는 전개 자체가 없다) · 결과 저장(사용자 코드 꼴·DB 오류 글 — 규칙 3) · JSqlParser 로 컬럼 뽑기(6-9 조건부 그대로 — DB 에 물으면 파서가 필요 없다).

## 4. 실행 스텝

**V-29 — 표본 받기: `ruoyi`(소스·매퍼·DDL) + `jspwiki-bin`·`ruoyi-bin`(번들 51 용 클래스)** — 네트워크
- 대상: `scripts/corpus-fetch.sh` · `corpus/SOURCES.md` · `corpus/MANIFEST`(스크립트가 다시 쓴다) · 새 `corpus/jspwiki-bin/pom.xml` · `src/test/java/kr/ejg/toolbox/DbCorpus.java`(MARIA 에 RuoYi DDL) · `scripts/verify-fingerprint.sh`(`corpus/jspwiki-bin` 은 corpus 레인 — `corpus/MANIFEST` 가 이미 든다, pom 하나 더).
- 변경:
  - `SOURCES` 배열에 `"ruoyi|yangzongzhuan/RuoYi-Vue|13db1fcef36bee9ce45d2d636a1d4e8f5ed5bbc3|**/src/main/java/**/*.java **/src/main/resources/mapper/**/*.xml sql/*.sql"`.
  - `MAVEN` 배열에 `"jspwiki-bin|corpus/jspwiki-bin/pom.xml"` — pom 은 `corpus/egov35-lib/pom.xml` 꼴(groupId `toolbox.corpus`, packaging pom, 의존성 `org.apache.jspwiki:jspwiki-main:2.12.5` 하나 — Maven Central 에 있다, 실측 1,067,596 바이트·클래스 384). MAVEN 루프 끝에 **한 단계 더**: `jspwiki-main-*.jar` 를 `$CORPUS/jspwiki-bin/classes/` 에 `unzip -q -o`, 나머지 jar 는 `lib/` 로 옮긴다(SpotBugs 입력 = 클래스 폴더 + aux jar 폴더, 설계 34 §3). 소스 표본 `jspwiki` 가 같은 판(2.12.5)이라 줄 매핑을 잰다.
  - `BUILD` 배열(새 꼴 — 이름|SOURCES 이름|빌드 명령|가져올 경로): `"ruoyi-bin|ruoyi|mvn -q -B package -DskipTests|*/target/classes ruoyi-admin/target/*.jar"` — 캐시 클론(`$CACHE/ruoyi@<커밋>` 은 전체 저장소다)에서 저장소의 Maven(`m2/.mvn-home/wrapper/dists/apache-maven-3.9.9-bin/*/apache-maven-3.9.9/bin/mvn.cmd`, `JAVA_HOME` 은 `scripts/mvn.sh` 와 같은 JDK 17)으로 빌드 → 모듈마다 `target/classes` 를 `$CORPUS/ruoyi-bin/classes/` 에 합치고 `ruoyi-admin.jar` 의 `BOOT-INF/lib/*.jar` 를 `lib/` 에 푼다. egov35-lib 처럼 **MANIFEST 밖·이름을 골라야 받는다**(빌드 산출물은 바이트 재현을 약속하지 않는다). 빌드 실패면 `[실패] ruoyi-bin` 으로 끝 — 설계 세션 실측 결과는 이 절 아래(§8 처분).
  - `SOURCES.md` 표에 세 줄(ruoyi MIT · jspwiki-bin Apache-2.0 Maven Central · ruoyi-bin 「ruoyi 를 빌드한 것, MANIFEST 밖」) + 「쓰는 행」 V-29·6-33a·6-34a·5-38b.
  - `DbCorpus.ddl(MARIA)` 끝에 `ruoyi/sql/ry_20260417.sql`·`ruoyi/sql/quartz.sql`(MySQL 문법 — MariaDB·MySQL 5.7·8.0 컨테이너 셋 다. `DbCorpus.load` 가 못 넣는 문장은 `db-*-ddl-failed` conformance 가 잡는다 — 새로 생기면 목록 갱신을 이력에).
  - 실행: `bash scripts/corpus-fetch.sh ruoyi jspwiki-bin ruoyi-bin` → `corpus/MANIFEST` 에 `ruoyi` 줄(파일 수·지문) → 커밋. `jspwiki-bin`·`ruoyi-bin` 은 폴더만.
- 검증: `ls C:/workspace/toolbox-corpus/ruoyi/sql`(2 파일) · `find …/ruoyi -name '*Mapper.xml' | wc -l`(20) · `…/jspwiki-bin/classes/org/apache/wiki` 있음 · `…/ruoyi-bin/classes/com/ruoyi` 있음 · `bash scripts/corpus-fetch.sh --manifest` 가 같은 지문 · `bash scripts/mvn.sh -q test -Dtest=CorpusFilesTest`(있으면) 초록.
- 의존: 없음. 네트워크(GitHub·Maven Central). 빌드 약 2~5분(의존성 받기).

**6-33a — `${}` 이름·동적 표지 + `Result.risks`**
- 대상: `core/analyze/MapperIndex.java` · `core/analyze/AnalyzeRunner.java` · `MapperIndexTest` · `AnalyzeRoutesTest.project()`(픽스처에 `Board_SQL.xml` 문장 둘 추가 — `<select id="listSorted">SELECT * FROM COMTNBBS ORDER BY ${sortCol} ${sortDir}</select>`(컨트롤러의 한 프로그램이 부른다 — 기존 `selectList` 를 부르는 메서드 옆에 `listSorted` 를 부르는 메서드 하나) · `<select id="orphanDollar">SELECT * FROM COMTNBBS WHERE ${cond}</select>`(아무도 안 부름)) · 새 `AnalyzeRunnerTest.risks`(없으면 `JavaGraphTest` 옆 새 파일).
- 변경: §3 6-33. `MapperIndexTest`: `dollars` 이름·정렬·방언 합집합 · `dynamic` — `<if>` 있으면 true, `<include>` 가 동적 조각이면 true, 정적이면 false. `AnalyzeRunnerTest.risks`: 픽스처 → `risks` 둘, `listSorted` 의 programs 가 그 프로그램 하나, `orphanDollar` 는 빈 목록. 기존 골든(`analyze-*.json`)은 risks 를 안 싣는다 — 바뀌면 안 된다. **표본**: `AnalyzeCorpusTest.ruoyi`(새, `@Tag corpus`) — `ruoyi` 표본을 `AnalyzeRunner.run` 으로(naming 은 spring 기본) → 골든 `golden/corpus/analyze-ruoyi-risk.json`(문장 nsId·names·닿는 프로그램 수 — 기대: 문장 6, `params.dataScope` 5·`sql` 1, `SysUserMapper.selectUserList` 류는 프로그램 ≥ 1) + 기존 꼴의 `analyze-ruoyi.json`(프로그램·문장·표 수) · `egov` 는 risks 0 을 단언(불변식).
- 검증: `bash scripts/mvn.sh -q test -Dtest='MapperIndexTest,AnalyzeRunnerTest,AnalyzeRoutesTest,JavaGraphTest'` 초록 · `bash scripts/mvn.sh -q test -Dtest=AnalyzeCorpusTest`(표본 폴더 필요, 약 2분).
- 의존: V-29.

**6-33b — V014 + 저장·조회 + API + xlsx**
- 대상: `V014__analyze_risk.sql` · `core/analyze/AnalyzeStore.java` · `web/AnalyzeRoutes.java`(라우트 + export 시트) · `AnalyzeStoreTest` · `AnalyzeRoutesTest`.
- 변경: §3 6-33. `AnalyzeStoreTest`: save 뒤 `risks(runId)` — 행 둘, shape 가 `정렬·컬럼 추정`(sortCol)·`값`(cond), programs 1·0. `AnalyzeRoutesTest`: `GET /risk` 200 꼴 · export 에 시트 「위험문장」 행 2.
- 검증: `bash scripts/mvn.sh -q test -Dtest='AnalyzeStoreTest,AnalyzeRoutesTest,MigratorTest'` 초록.
- 의존: 6-33a.

**6-33c — 탭 「위험 문장」 + 스모크 + 집 검증 상태**
- 대상: `tools/program_analysis.html` · `tools/program_analysis_ext.js` · `SmokeHtmlUnitTest`(새 `programAnalysisRisk`) · `scripts/puppeteer/visual.js`(program_analysis 상태에 `{ name: 'risk', prep: p => click(p, '#tabRisk') }`) · 런처 카드 글(program_analysis 설명에 「위험 문장」).
- 변경: §3 6-33 화면. 스모크: `AnalyzeRoutesTest.project` 로 분석 → `#tabRisk` → `#riskResult tbody tr` 2 · 첫 행이 `listSorted`(프로그램 수 1) · `#riskOrphanOnly` 체크 → 1 행(`orphanDollar`) · 행 클릭 → `#riskDetail` 에 `verb url`.
- 검증: `bash scripts/mvn.sh -q test -Dtest='SmokeHtmlUnitTest#programAnalysisRisk+programAnalysisRuns,ToolsFolderTest,ToolsA11yTest,LauncherTest'` 초록 · `node scripts/puppeteer/visual.js b50`.
- 의존: 6-33b.

**6-34a — `SqlProbe` + H2 시험 + 벤더 절**
- 대상: 새 `core/analyze/SqlProbe.java` · `MapperIndex.java`(`scanRaw`·`Raw.dynamic` 은 6-33a 가 했다) · 새 `SqlProbeTest`(H2 `jdbc:h2:mem:probe;DB_CLOSE_DELAY=-1` 를 `DriverManager` 로 열어 `ReadOnlyJdbc.wrap` — `ReadOnlyJdbcTest` 꼴) · `DbCorpusBase`(새 절 `probeRuns`, `@Order(4)` — `qualitySqlRuns` 뒤) · `CheckCorpusTest`? 아니다 — `AnalyzeCorpusTest.egov` 에 「`SqlProbe.run` 이 예외 없이 돈다 + 건너뜀 분포」 를 더하려면 DB 가 필요하니 넣지 않는다(벤더 절이 그 몫).
- 변경: §3 6-34. `SqlProbeTest`(표 `T(A INT, B VARCHAR(10))`): ① `SELECT A, B FROM T WHERE A = #{a}` → PASS ② `SELECT A, C FROM T` → ERROR, message 에 `C` ③ `<if>` 든 select → SKIP_DYNAMIC ④ `${}` → SKIP_DOLLAR ⑤ `<include refid="cols">` 가 정적 `<sql>` → PASS, 동적 `<sql>` → SKIP_DYNAMIC ⑥ 끝 `;` 와 CDATA → PASS ⑦ `<update>` 는 rows 에 없고 total 에도 없다 ⑧ 방언 파일 — `X_SQL_oracle.xml`·`X_SQL_mysql.xml` 같은 nsId, dialect `mysql` 이면 mysql 파일(둘 가운데 하나만 틀리게 써서 판정으로 가른다), dialect `h2` 면 첫 것, `tibero` 면 oracle 파일 ⑨ 취소 — `JobContext` 가 첫 문장 뒤 취소면 `JobCancelledException`. `DbCorpusBase.probeRuns` — 셋을 한 절에: ① 손 문장 둘 — `Source` 둘을 글로 만들어(`<mapper namespace="p">` 에 `ok` = `SELECT <invoiceId()> FROM <invoice()> WHERE <customerId()> = #{c}`, `bad` = `SELECT NO_SUCH_COL_X FROM <invoice()>`) `SqlProbe.run(xml, conn, dialect().id, null)` → `golden.put("probePass", 1)`·`("probeFail", 1)` + `bad` 의 message 가 비어 있지 않다 + 어긋나면 A 목록(`CorpusFiles.conformance("db-" + goldenKey() + "-probe-a", a)`). ② **eGov 매퍼 실물**(oracle·postgres·maria — eGov DDL 이 든 셋; MSSQL 은 건너뜀): `CorpusFiles.root().resolve("egov/src/main/resources/egovframework/mapper")` 의 `*_<방언>.xml`(파일명이 그 방언인 것만 `Source` 로 — 방언 고르기도 그 길로 다시 검증된다) → `SqlProbe.run` → `golden.put("egovProbe", Map.of("pass", n, "fail", n, "dynamic", n, "dollar", n))` + ERROR 목록(`nsId` + 메시지 첫 40자)을 `db-<goldenKey>-probe-egov` conformance(B baseline — DDL 에 없는 표·컬럼이 실제로 있을 수 있다, 처음 값은 이력에). ③ **RuoYi 매퍼 실물**(maria·mysql57·mysql80 — V-29 가 DDL 을 넣은 판만, `goldenKey()` 로 가른다): `ruoyi/**/mapper/**/*.xml` → 같은 꼴 `ruoyiProbe` + conformance. 옛 판 다섯은 `skipped()` 에 넣지 않는다(같은 SQL 이면 돈다 — 안 돌면 이력에).
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlProbeTest,MapperIndexTest'` 초록 · `bash scripts/mvn.sh -q test -Dtest=PostgresCorpusTest`(컨테이너 하나, 약 3분) 초록 — 나머지 벤더는 마무리 `--db`.
- 의존: 6-33a.

**6-34b — 라우트 + 정합성 탭 UI + 스모크**
- 대상: `web/AnalyzeRoutes.java`(`register` 시그니처 + 라우트 둘) · `web/App.java`(호출) · `tools/program_analysis.html`·`program_analysis_ext.js` · `SmokeHtmlUnitTest`(새 `programAnalysisProbe`) · `AnalyzeRoutesTest`.
- 변경: §3 6-34 라우트·화면. `AnalyzeRoutesTest`: 프로필에 h2 접속(`ConnectionRegistryTest` 꼴 URL, 임시 파일 DB 에 `COMTNBBS(ID INT)`·`COMVNUSERMASTER(ID INT)`) → 픽스처에 `<select id="bad">SELECT NOPE FROM COMTNBBS</select>` 하나 더 → `POST /probe {connId: "h2"}` 202 → 작업 끝 → `RESULT` 의 `failed` 1(`bad`)·`passed` ≥ 2(`selectDetail`·`unusedOne` — `selectList` 는 JOIN 이라 두 표 다 있어야 PASS) · `connId` 없음 400 · `POST /probe-xlsx` 파일. 스모크 `programAnalysisProbe`: 같은 프로필로 화면 → 분석 → 정합성 탭 → `#probeConn` 에 `h2` → `#probeRun` → `#probeMsg` 가 `통과 ` 로 시작하고 `오류 1` 포함 → `#probeResult tbody tr` 첫 행에 `bad` → `#probeXlsx` → 파일.
- 검증: `bash scripts/mvn.sh -q test -Dtest='AnalyzeRoutesTest,SmokeHtmlUnitTest#programAnalysisProbe,ArchitectureTest'` 초록.
- 의존: 6-34a, 6-33c(같은 html — 충돌 없이 두 번 고치면 된다).

**마무리** — CLAUDE.md 「마무리」 여덟. 바뀐 레인: java·tools·corpus(`core/analyze`·tools) → `--corpus` · db(V014·`DbCorpusBase`) → `--db`(push 와 따로). `docs/import-review.md` 4절(DB 접근)에 「프로그램 분석 DB 대조 — 정적 SELECT 를 `WHERE 1=0` 으로, 읽기 전용 래퍼를 지난다, 결과 저장 없음」 한 줄(`ImportReviewDocTest.namedThingsExist` 에 `SqlProbe` 를 더한다).

## 5. 금지 사항
- `ConnectionRegistry.open` 밖에서 연결을 열지 않는다(ArchUnit) · 읽기 전용 래퍼를 우회하는 `unwrap` 금지.
- SQL 글·DB 오류 글을 H2·로그에 남기지 않는다(규칙 3). `analyze_risk.names` 는 `${}` 안 식별자만.
- 동적 SQL 을 전개하지 않는다 — 건너뜀으로 센다.
- 새 의존성 0(JSqlParser 반입 금지 — 6-9 조건부 그대로).
- 기존 골든(`analyze-*.json`·`check-*`) 의 값을 바꾸지 않는다 — risks 는 골든에 안 싣는다.
- 완료 행의 글을 고치지 않는다.

## 6. 최종 검증
- `bash scripts/verify.sh` 초록 → `bash scripts/verify.sh --corpus --db` 초록 → `bash scripts/gate-probe.sh`.
- 손 확인: `run.bat --profile demo`(데모 Oracle 컨테이너, 메모리 `demo-environment`) → 프로그램 분석(데모 프로젝트) → 위험 문장 탭 · 정합성 탭 「DB 에 물어보기」 → 통과/오류/건너뜀 수가 나오고 오류 행의 DB 메시지에 컬럼 이름.
- 회귀: `SmokeHtmlUnitTest` 전부 · `ArchitectureTest` · `AnalyzeCorpusTest` 골든 그대로.

## 7. 중단 조건
- 벤더 절에서 `bad` 가 PASS 로 나온다(DB 가 `WHERE 1=0` 서브쿼리의 컬럼을 검사하지 않는 판) → 그 벤더·판을 이력에 적고 멈춘다(래퍼 꼴을 벤더별로 바꾸지 않는다 — 사용자가 본다).
- `setNull(VARCHAR)` 바인드가 어느 벤더에서 타입 오류를 낸다 → 그 메시지를 적고 멈춘다(`ERROR` 로 보고하면 거짓 오류가 된다).
- `ReadOnlyJdbc` 가 대조 SQL 을 거절한다(`SELECT * FROM (…)` 꼴이 금칙에 걸림) → 멈춘다(가드를 안 푼다).
- 검증이 2회 연속 실패하고 원인이 플랜 범위 밖 · 금지 사항을 어기지 않고는 진행 불가 · 스텝에 없는 파일을 3개 이상 고쳐야 함.
- 보고 형식: 어느 스텝, 무엇이 예상과 달랐는지, 가능한 선택지.

## 8. 불확실 항목
- **실행해야만 아는 것**: 벤더 넷에서 `SELECT * FROM (…) tb_probe WHERE 1=0` + `setNull(VARCHAR)` 가 컬럼 오류를 내는지 — 6-34a 의 `probeRuns` 절이 잰다(PG 는 그 행에서, 나머지는 마무리 `--db`). 실패 사다리는 §7.
- **실행해야만 아는 것**: `ruoyi-bin` 빌드(저장소 Maven 3.9.9 + JDK 17 로 RuoYi-Vue `package`)가 되는지 — 설계 세션이 돌린 결과는 PROGRESS 이력 「번들 50·51 표본 조사」 줄 뒤에 적는다. 안 되면 V-29 는 `ruoyi-bin` 없이(`SpotBugsCorpusTest` 는 jspwiki-bin 만) 간다.
- **실행해야만 아는 것**: eGov 매퍼 실물 대조의 PASS/ERROR 분포(DDL 과 매퍼가 같은 판 v5.0.6 이라 ERROR 는 적어야 하지만 모듈 SQL(`src/script`)에만 있는 표가 있을 수 있다) — 처음 값은 conformance 로 굳히고 이력에 적는다. RuoYi DDL 이 MariaDB·MySQL 5.7 컨테이너에 전부 들어가는지(`db-*-ddl-failed` 가 새로 생기면 목록 갱신).
