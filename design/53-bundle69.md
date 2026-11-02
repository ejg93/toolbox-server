# 설계 53 — 번들 69: 생성기 textarea 문턱·사람 감사 컬럼 채우기·문자형 감사 시각 + 리뷰 Minor 여섯 + DdlReader typed table (Fable 설계 · Opus 실행, 2026-11-02)

## 맥락
번들 68(설계 52)이 생성기에 컬럼 역할·입력 꼴·검색을 넣고 eGov 실물 181 표에 돌린 재측정(compare ⑱·R-17)에서 틈이 드러났다 — ① textarea 문턱 1,000 이 게시물 제목 `NTT_SJ`(VARCHAR2 2,000)를 여러 줄 입력·검색 없음으로 만든다 ② 사람 감사 컬럼(`FRST_REGISTER_ID`·`LAST_UPDUSR_ID`)이 INSERT·UPDATE 에 `#{…}` 인데 화면에도 컨트롤러에도 채우는 자리가 없어 생성물을 그대로 돌리면 NOT NULL 위반 ③ 마무리 리뷰 Minor 여섯(7-18) ④ PG typed table 을 `DdlReader` 가 조용히 빠뜨린다 ⑤ 문자형(`CHAR(14)`) 감사 시각 컬럼은 `now` 가 아니라 `#{…}` 로 남는다. 전부 `core/gen`(db·corpus 레인)이라 한 번들로 묶어 `--corpus`·`--db` 를 한 번에 돈다.

**번들 68 보정**(2026-11-02) — 이력 「정한 것(계획 밖)」·CI 처분·마무리 리뷰와 세 행을 대조한 결과:
- 7-16b ① 「픽스처 `bbs` 의 `NTT_SJ` 를 2,000 → 255 로」 — 7-19 가 실물 2,000 으로 되돌린다(이 설계 §3-2)
- 1-68 「`CREATE_INDEX` 머리에 `(NON)CLUSTERED`·`CONCURRENTLY`·`ON ONLY` 를 더 받는다」 — 코드는 이미 받는다. 7-18b 는 **시험만** 더한다(양성)
- V-48a 「드러난 것」 typed table — 사용자가 이 번들에 넣기로(1-69)
- V-48 「Minor 여섯을 새 행 7-18 로」 — 파일이 다섯이라 7-18a(생성기)·7-18b(DdlReader) 둘로 가른다
- CI 처분 둘(PMD `NullAssignment`·SpotBugs `BX_UNBOXING_IMMEDIATELY_REBOXED`) — 빠른 `verify.sh` 는 PMD·SpotBugs 를 안 돈다. 이 번들은 **무거운 레인 앞에 `bash scripts/mvn.sh -DskipTests verify` 한 번**을 V-49 와 마무리에 박는다. `FieldRoles` 의 Boolean 은 `orDefault` 꼴 그대로(풀었다 다시 싸지 않는다)
- claude[bot] ② 「사람 감사 컬럼을 컨트롤러가 안 채움」 — 7-20
- 7-20 행의 「기본 eGov `LoginVO` 의 `id`?」 — 실측은 `uniqId`(아래). 행을 고친다

탐색 실측(2026-11-02, `2ee60f2`, 표본 `C:/workspace/toolbox-corpus`, 외부 통신 0, grep 셈 — 코드 본문은 안 옮긴다):

| 재료 | 수치 | 뜻 |
|---|---|---|
| eGov oracle VARCHAR2 1,000~4,000 컬럼 | 91 — 1000: 25 · 2000: 21 · 2500: 30 · 4000: 7(+ CLOB 10) | 이름을 보면 제목·이름은 `NTT_SJ`(2000)·`WIKI_BKMK_NM`(1000) 둘뿐. 나머지 89 는 `*_CN`·`*_DC`·`*_INFO`·`MEMO`·`RM`·`*_MATTER`·`*_URL`·`*_COURS` — 글·설명·경로라 textarea 가 맞다 |
| eGov `*_SJ` 컬럼 길이 | 60: 2 · 100: 2 · 255: 8 · 2000: 1 | 제목 접미 `_SJ` 는 2000 하나 빼고 다 1,000 아래 |
| eGov `*_NM` ≥ 500 | `SRVC_NM` 500 · `WIKI_BKMK_NM` 1000 | 이름 접미 `_NM` 도 하나만 문턱 위 |
| eGov 게시판 실물 검색(`EgovArticle_SQL_oracle.xml`) | `searchCnd 0 → NTT_SJ LIKE` · `1 → NTT_CN LIKE`(CLOB) | 실물은 제목·내용 둘 다 LIKE |
| eGov 컨트롤러가 사용자를 얻는 길 | `EgovUserDetailsHelper.getAuthenticatedUser()` 119 컨트롤러 → `LoginVO` | 공통컴포넌트(`egovframework.com.cmm`) — rte jar 에 없다(7-5 [고정] 과 충돌) |
| `setFrstRegisterId(…)` 인자 | `uniqId` 꼴 126(`loginVO.getUniqId()` 44·`user.getUniqId()` 43·`uniqId` 21·그 밖) · `id` 꼴 9 · `"anonymous"` 2 | 감사 ID 는 **`LoginVO.uniqId`** 가 관례. `FRST_REGISTER_ID` 는 전부 VARCHAR2(20) |
| 로그인 뒤 세션 저장 | `session.setAttribute("loginVO", …)` — `EgovLoginController:232`(세션 로그인)·`EgovSpringSecurityLoginFilter:120`(시큐리티)·`EgovSSOLoginFilter:129` | 두 인증 길 다 세션 키 `loginVO` 에 LoginVO 를 둔다 |
| egov35-lib(`GeneratorJavacTest` 클래스패스) 35 jar | `javax.servlet-api-3.1.0` · `spring-beans-4.0.9` · `spring-webmvc-4.0.9` 포함 | `HttpServletRequest`·`HttpSession`·`org.springframework.beans.BeanWrapperImpl` 은 rte 의존 jar 만으로 컴파일된다. `@SessionAttribute` 는 Spring 4.3 부터라 못 쓴다 |
| `GeneratorJavacTest` 가 javac 하는 표 | H2 HR 7 — 감사 컬럼 없음 | 컨트롤러의 새 코드(감사 ID)가 HR 생성물엔 안 나와 지금 시험으론 컴파일 검증이 안 된다 → bbs 표도 같이 javac(§3-4) |
| `DdlReader.nameOnly` | PARTITION OF · LIKE · CTAS 셋을 이름만 표로 + notes(`NAME_ONLY` Map.of 셋) | typed table `OF 형식` 을 넷째로 넣을 자리 — `read` 의 `openParen` 판정 앞에서 돈다 |
| 기준선 | `self-check.txt` 84~89 — `DdlReader item(3) 30`·`read(1) 36`·중첩 6 · `GenModel of(4) 복잡도 34`·`길이 107` | 이 번들은 넉 줄 다 **불변** 이어야 한다(새 분기는 전부 도우미에) |
| PMD 기준선 짝 | `GenModelTest`=AvoidDuplicateLiterals · `DdlReader` 여섯 | **없는 것**: `FieldRoles`·`DdlReaderTest`·`GenTemplatesTest`·`GeneratorJavacTest`·`ProfileStoreTest` — 새 짝 금지(리터럴 ≥ 4 는 상수, `if` 에 리터럴 금지, 비교는 리터럴 앞) |

- `FieldRoles.apply`(`core/gen/FieldRoles.java:84-103`) — 키 열(audit·now·html·options·list·insert·edit·query·required·step). `now` = `STAMPS.contains(audit) && TIMES.contains(javaType)` — 재정의를 안 본다(Minor). `edit` = `orDefault(g.edit(), shown && !pk)` — PK 에 `edit: true` 를 주면 Updt 에 PK 줄이 둘(Minor). `stringHtml`(:150-158) — select → radio → 길이 null 또는 ≥ 1000 textarea → text. `queryOf(html, list, pk)`(:187-198) — textarea 는 NONE. `lists(fields, dialect)`(:106-116) — 목록 일곱 + 모델 `now`(방언 현재시각 식)
- `input.ftl` — `<#assign req>`(전역 변수 — Minor, `<#local>` 이어야) · 매크로 인자 `readonly` 는 아무 데서도 안 준다(Updt 의 PK 줄은 직접 쓴다)
- `Controller.java.ftl` — `insert[=Name]`·`update[=Name]` 가 `searchVO`·`[=name]VO`·`BindingResult`·`ModelMap` 만 받고 바로 서비스에 넘긴다. import 는 `[=vars.ee].annotation.Resource` 꼴로 ee 접두를 쓴다 → `[=vars.ee].servlet.http.HttpServletRequest` 도 같은 꼴
- `Mapper.xml.ftl` — INSERT `<#if f.now>[=now]<#else>#{[=f.name]}</#if>` · UPDATE 같은 꼴. 모델 `now` 와 필드 `f.now` 이름이 겹친다(claude[bot] ②)
- `Profile.Audit`(`core/profile/Profile.java:155-170`) — 넷 묶음 레코드, `EGOV` 상수, 컴팩트 생성자가 null → `List.of()`. `GenModelTest.auditOverride` 가 4인자 생성자를 쓴다
- `DdlReader.INDEX_SORT`(:58) `(?i)\s+(?:ASC|DESC)(?:\s+NULLS\s+(?:FIRST|LAST))?$` — `NULLS LAST` 단독·MySQL 접두 길이 `name(10)` 을 못 뗀다(Minor)
- `GenModelTest` — `bbs()`(static, 13 컬럼, `NTT_SJ` 255) · `bbsOpts` · `field`·`columns`·`role` 도우미 · 시험 여덟 · `GenTemplatesTest.bbsGolden` 은 `gen/bbs/` 다섯(List·Regist·Updt·VO·Mapper — Controller 없음)
- `GeneratorJavacTest.compilesAndRunsMappers` — HR 7 을 egov35 로 생성 → javac(클래스패스 egov35-lib 전부) → `_SQL_oracle.xml` MyBatis 파싱 → H2 건수·목록
- 레인 — `core/gen`(main·test)·`src/main/resources/gen` 은 db·corpus 둘 다. `core/profile`·`templates/`·`profiles/` 는 java 레인. push 앞 `--corpus` → Docker → `--db`(겹치지 않게)

## 사용자 결정(2026-11-02)
- 7-19: **(나)+(라)** — 문턱 1,000 유지. (나) 길이 있는 textarea(VARCHAR)도 LIKE 검색, CLOB(길이 없음)은 검색 없음. (라) 이름이 `_SJ`·`_NM`·`_TITLE`·`_NAME` 으로 끝나면 길이와 무관하게 text(한 줄, maxlength) + LIKE — `_AT` 여부 규칙과 같은 eGov 용어 규칙. (가) 문턱 4,000 은 내용 컬럼 55 가 한 줄이 돼 버림
- 7-20: **(가) 컨트롤러가 채운다** — 등록·수정 메서드에 `HttpServletRequest request` 인자 + private `loginId(request)` 하나: 세션 속성 `loginVO` 의 `uniqId`(`BeanWrapperImpl`), 없으면 `request.getUserPrincipal().getName()`, 그래도 없으면 null. 세션 키·속성은 프로필 `generator.audit.userSession`(기본 `loginVO.uniqId`, 빈 글이면 Principal 만). creator(등록)·NOT NULL updater(등록)·updater(수정) 중 String 컬럼만. `FieldRoles` 에 `now` 와 짝인 `user` 키
- ⑱ 작은 것 둘 **넣는다** — typed table(1-69) · 문자형 감사 시각(7-21)
- **머지 위임 있음**
- 설계가 정한 것(사용자가 안 물은 자리) — 7-18 을 7-18a(생성기)·7-18b(DdlReader) 로 가른다 · PK 의 `edit` 는 재정의가 있어도 false(템플릿 루프 대신 `FieldRoles` 한 자리 — Updt 줄 둘·UPDATE SET 의 PK 둘 다 막는다) · 감사 시각·사람 컬럼에 `insert`·`edit` 재정의가 하나라도 있으면 `now`·`user` 둘 다 false(사용자 값 `#{…}`) · 문자형 감사 시각은 길이 ≥ 14 또는 null → 14자리(`YYYYMMDDHH24MISS`), 8 ≤ 길이 < 14 → 8자리(`YYYYMMDD`), 8 미만 → `now` 아님 · 모델 수준 `now` 키를 없애고 필드 `nowExpr`(식 글, `now` 가 false 면 `""`)로 — 이름 겹침 해소 · `sessionKey`·`sessionProp` 는 `FieldRoles.lists` 가 모델에 넣는다(`GenModel.of` 길이 107 불변) · `GeneratorJavacTest` 가 HR 7 + `GenModelTest.bbs()` 를 같이 javac·매퍼 파싱(H2 실행은 HR 만) · `GenTemplatesTest.bbsGolden` 에 Controller 를 더한다(`gen/bbs/ComtnbbsController.java`) · 새 화면·순수본·표본 추가 없음 · 설계 41~52 그대로(에이전트 최소 · 검증 인프라 최적화 금지)

실행자는 Opus. 차례 `S0`(이 설계 세션이 했다) → `7-18a` → `7-18b` → `7-19` → `7-20a` → `7-20b` → `7-20c` → `7-21` → `1-69` → `V-49a` → `R-18` → `V-49` → 마무리. 가지 `work/2026-11-02`(로컬 docs 커밋 셋 `5a3d839`·`2ee60f2`·S0 같이). 번들 하나·PR 하나.

---

## 1. 목표와 범위
- 한 문장: 생성기가 제목·이름 컬럼을 길이와 무관하게 한 줄 입력·LIKE 로 내고 긴 문자 컬럼도 검색하며, 등록·수정 컨트롤러가 로그인 사용자 ID 를 사람 감사 컬럼에 채우고, 문자형 감사 시각에 방언별 날짜 글 식을 쓰며, 리뷰 Minor 여섯이 닫히고, `DdlReader` 가 typed table 을 이름만이라도 읽는다.
- 완료 시 체감: eGov `COMTNBBS` 꼴 표를 생성하면 등록 화면의 제목이 한 줄 칸(maxlength 2000)이고 목록 화면에 제목 검색칸이 있다. 내용(VARCHAR2 2500)도 검색칸이 있다(CLOB 은 없음). 생성 컨트롤러 등록 메서드가 `frstRegisterId` 를 세션 `loginVO` 의 `uniqId` 로 채워 NOT NULL 표에도 그대로 돈다. `CHAR(14)` 등록 시각 컬럼이 `TO_CHAR(SYSDATE, 'YYYYMMDDHH24MISS')` 로 들어간다. PG `CREATE TABLE x OF 형식` 이 표로 잡힌다(sqltables-diff 31 → 30 기대). compare ⑲.
- In scope: `core/gen/FieldRoles.java` · `templates/gen/egov35/{input.ftl,Controller.java.ftl,Mapper.xml.ftl}` · `core/profile/Profile.java`(`Audit.userSession`) · `profiles/example.yaml` · `PLAN.md` 6장 한 줄 · `core/gen/DdlReader.java`(`INDEX_SORT`·typed table) · 시험 `GenModelTest`·`GenTemplatesTest`·`GeneratorJavacTest`·`ProfileStoreTest`·`DdlReaderTest` · 골든 `gen/model.json`·`gen/model-bbs.json`·`gen/bbs/*`(+Controller)·`gen/egov35|egov4|egov5/*Controller.java`(불변 기대)·`gen/mapper-*.xml`·`corpus/gen-hr.json`·`corpus/sqltables-diff.txt`·`corpus/ddl-lint*`(7-18b 가 바꾸면) · `design/compare-2026-10-28.md` ⑲ · PLAN 분할표·번들 표 · PROGRESS.
- Out of scope: 문턱 수치 변경 · CLOB 검색 · 코드 사전(마-5)·트리·주부(마-7·8)·메뉴 SQL(마-9)·upload · 날짜 바인딩 `@DateTimeFormat` · `List`·`Regist`·`Updt`·`Detail`·`VO`·`Service`·`ServiceImpl`·`DAO`·`header` 템플릿(Regist·Updt 는 `input.ftl` 을 통해서만 바뀐다) · INSERT 에 NULL 허용 updater 넣기(설계 52 [고정]) · ⑰ Minor 넷(corpus 레인)·H2 FK 자동 인덱스·a11y `<form:*>` 규칙·PK 제약 이름 · 새 화면·순수본·표본 · `GenModel.Options` 인자 변경 · `core/meta` 레코드.

## 2. 현재 구조 요약
「맥락」 의 실측과 구조 줄이 요약이다. 덧붙이는 함정:
- `GenModel.of(4)` 는 길이 107 > 100 기준선 — **줄을 더하지 않는다**. `m.putAll(FieldRoles.lists(fields, dialect))` 한 줄을 `m.putAll(FieldRoles.lists(fields, o))` 로 바꿀 뿐
- `FieldRoles.apply` 복잡도는 지금 약 10 — `now`·`user`·`nowExpr` 판정은 도우미 `stampOf(…)`·`userOf(…)` 로 뺀다(≤ 15)
- `Profile.Audit` 에 다섯째 컴포넌트를 더하면 Jackson 은 정규 생성자(5)로 읽는다. `GenModelTest.auditOverride`·`EGOV` 는 4인자 생성자를 그대로 쓴다 → 4인자 보조 생성자 유지
- `GenTemplatesTest.setsGolden`(empHist × 세트 셋 × 10 파일) — empHist 엔 감사 컬럼이 없어 `hasUser` false → Controller 골든 셋 **불변** 이어야 한다(바뀌면 중단 조건). `bbsGolden` 만 Controller 가 는다
- `GeneratorCorpusTest` A 「프로그램 ≠ 7 × 표」 — HR 7 은 감사 컬럼이 없어 컨트롤러가 안 바뀐다. 프로그램 분석이 private `loginId` 를 프로그램으로 셀 일은 HR 엔 없다
- FreeMarker 2.3.35 — `?has_content`·`<#local>`·`?filter` 다 된다. 보간 `[=…]`, JSP EL `${}`·MyBatis `#{}` 는 글자 그대로
- `DdlReader.nameOnly` 는 `read` 의 `openParen` 앞에서 돈다 — typed table 은 여기 넷째 가지. `read`·`item` 복잡도 불변
- `-Dgolden.update=true` 는 행마다 해당 시험만(`-Dtest=`) 한 번, diff 를 읽고 이력에

## 3. 설계 결정 — 전부 [고정]

### 7-18a — `FieldRoles` 재정의 꼴 + `input.ftl` + 음성 시험(§3-1)
- `FieldRoles.apply`:
  - `boolean fixed = g.insert() != null || g.edit() != null;`(재정의가 하나라도 있으면 사용자 값)
  - `f.put(K_NOW, !fixed && STAMPS.contains(audit) && TIMES.contains(javaType));`
  - `f.put(K_EDIT, pk ? Boolean.FALSE : orDefault(g.edit(), shown));` — PK 는 재정의가 있어도 false(수정 화면 줄 둘·UPDATE SET 의 PK 를 한 자리에서 막는다). 경고는 안 낸다(javadoc 에 적는다)
- `input.ftl` — `<#assign req>` → `<#local req>` · 매크로 시그니처 `<#macro input f>`(`readonly` 인자 삭제, 그 `<#if readonly>` 조각 삭제). 출력 글자는 불변 → `gen/*` 골든 diff 0
- `GenModelTest` 새 시험 둘(단언 명시):
  - `negatives` — 표 하나(`col` 도우미): `MEMO VARCHAR2(999)` → html text·query LIKE · `SE_CD CHAR(1)`(이름이 `_AT` 아님) → text · `USE_AT VARCHAR2(1)`(nativeType CHAR 아님) → text · `DEL_AT CHAR(2)` → text
  - `overrideKeepsUserValue` — `bbs()` + `TableGen(null, null, {FRST_REGIST_PNTTM: ColumnGen(null, true, null, null, null, null), NTT_ID: ColumnGen(null, null, true, null, null, null)})` → `FRST_REGIST_PNTTM` now false·insert true·insertCols 에 있음 · `NTT_ID`(PK) edit false·editFields 에 없음·updateCols 에 없음 · 경고 0
- 기본값 출력 불변 — `gen/model*.json`·`gen/bbs/*`·`gen/egov*/*` diff 0

### 7-18b — `DdlReader` 인덱스 컬럼 정리 + 음성·양성 시험(§3-2)
- `INDEX_SORT` → `(?i)(?:\s+(?:ASC|DESC))?(?:\s+NULLS\s+(?:FIRST|LAST))?$` · 새 `INDEX_PREFIX = \(\d+\)$`(MySQL 접두 길이 `name(10)`). `indexColumns` 의 한 줄: `INDEX_SORT` 뗀 뒤 `INDEX_PREFIX` 뗀다(함수식 `UPPER(b)`·`LEFT(b, 10)` 은 `(` 바로 뒤가 숫자만이 아니라 안 맞는다). 끝이 빈 글이면 그대로(`""` 컬럼은 안 만든다 — 지금 꼴 유지)
- `DdlReaderTest` 새 시험 둘(+ 기존 `readsCreateIndexAndInlineKey` 는 그대로):
  - `inlineUniqueAndSpatialAreNotIndexes` — `CREATE TABLE t (a INT, g GEOMETRY, UNIQUE KEY uk_a (a), SPATIAL INDEX sp_g (g), FULLTEXT INDEX ft_a (a))` → `indexes` 0 · `uniques` 1(`UK_A`) · unreadable 0
  - `indexHeadsAndColumnDecorations` — `CREATE TABLE t (a INT, b VARCHAR(30), c INT); CREATE NONCLUSTERED INDEX i1 ON t (a); CREATE INDEX CONCURRENTLY i2 ON ONLY t (b); CREATE INDEX i3 ON t (c NULLS LAST); CREATE INDEX i4 ON t (b(10)); CREATE INDEX i5 ON t (a DESC NULLS FIRST, b(5))` → indexes 다섯 · `I3` 컬럼 `[c]` · `I4` `[b]` · `I5` `[a, b]` · unreadable 0
- 골든: `ddl-lint.json`·`ddl-lint-findings.txt` 는 표본에 `NULLS LAST` 단독·접두 길이가 있을 때만 바뀐다(실행해야 안다 — 바뀌면 diff 를 이력에, 바뀐 줄이 dupIndex 뿐이어야). `ddl-summary`·`ddl-unreadable`·`hr-lint`·`pg-lint`·`alter-*`·`ddl-*.sql` 불변
- `self-check.txt` `DdlReader` 석 줄 불변(`indexColumns` 는 기준선 밖)

### 7-19 — textarea 문턱: 이름 규칙 + 길이 있는 textarea LIKE(§3-3)
- `FieldRoles`:
  - `private static final List<String> TITLE_SUFFIXES = List.of("_SJ", "_NM", "_TITLE", "_NAME");` · `isTitle(c)` = 이름 대문자가 그중 하나로 끝남
  - `stringHtml` 순서: `inSet` 비지 않음 → select · `isFlag` → radio · **`isTitle` → text** · 길이 null 또는 ≥ 1000 → textarea · 그 밖 text
  - `queryOf(String html, boolean list, boolean pk, boolean sized)` — `sized` = `c.length() != null`: `!list || pk` → NONE · text → LIKE · **textarea && sized → LIKE** · select·radio·checkbox → EQ · date·datetime → BETWEEN · 그 밖(number·time·textarea 길이 없음) → NONE. 호출 자리에서 `c.length() != null` 을 넘긴다
- 매퍼·목록 템플릿은 안 바뀐다 — textarea LIKE 는 `f.string` 이라 `<if test="x != null and x != ''">` + LIKE 가 그대로 나오고, 목록 검색칸은 `<#else>` 가지의 `<form:input>`(한 줄)
- `GenModelTest`:
  - `bbs()` 의 `NTT_SJ` 길이 255 → **2000**(실물). `rolesAndInputKinds` 단언 `"text [] true/true/true LIKE true "` 그대로 통과해야 한다(이름 규칙 덕)
  - 새 `textareaBoundary` — 표 하나: `A999 VARCHAR2(999)`→text·LIKE · `B1000 VARCHAR2(1000)`→textarea·LIKE · `C2000 VARCHAR2(2000)`→textarea·LIKE · `D4000 VARCHAR2(4000)`→textarea·LIKE · `E_CN CLOB`(length null)→textarea·NONE · `F_SJ VARCHAR2(2000)`→text·LIKE · `G_NM VARCHAR2(1000)`→text·LIKE · `H_TITLE VARCHAR2(4000)`→text·LIKE · `I_NAME CLOB`→text·LIKE(이름 규칙이 길이 없음보다 앞) · `J_SJ CHAR(1)`(`_AT` 아님)→text
  - 골든 갱신 `-Dgolden.update=true -Dtest='GenModelTest,GenTemplatesTest'` 한 번 → `gen/model-bbs.json`(`NTT_SJ` length 2000) · `gen/bbs/ComtnbbsRegist.jsp`·`ComtnbbsUpdt.jsp`(`maxlength="2000"`) · `gen/mapper-*.xml`(bbs — `NTT_SJ` 그대로 LIKE, 변화 없음 기대) · `gen/model.json`·`gen/egov*/*`(empHist — `NOTE` 가 textarea·길이 있음이면 LIKE 로 바뀌어 List·Mapper·VO 가 바뀐다, egov4·egov5 는 egov35 와 같은 줄). diff 를 읽고 이력에 — 바뀐 자리가 위 목록 밖이면 중단 조건

### 7-20a — 프로필 `generator.audit.userSession`(§3-4)
```java
/** … userSession — 등록·수정 컨트롤러가 로그인 사용자 ID 를 읽는 세션 자리 「속성[.프로퍼티]」(7-20). 비면 Principal 이름만. null 은 eGov 기본 */
public record Audit(List<String> created, List<String> creator, List<String> updated, List<String> updater, String userSession) {
    public static final String EGOV_USER_SESSION = "loginVO.uniqId";
    public static final Audit EGOV = new Audit(List.of("FRST_REGIST_PNTTM"), List.of("FRST_REGISTER_ID"), List.of("LAST_UPDT_PNTTM"), List.of("LAST_UPDUSR_ID"));
    public Audit { /* 넷 null → List.of() · userSession null → EGOV_USER_SESSION, 아니면 trim */ }
    public Audit(List<String> created, List<String> creator, List<String> updated, List<String> updater) { this(created, creator, updated, updater, null); }
    public static Audit orDefault(Audit a) { return a == null ? EGOV : a; }
}
```
- `profiles/example.yaml` `audit:` 에 `userSession: loginVO.uniqId` + 주석 한 줄(「등록·수정 컨트롤러가 사람 감사 컬럼에 넣을 로그인 ID — 세션 속성[.프로퍼티]. 빈 글이면 request.getUserPrincipal() 이름만」). `PLAN.md` 6장 :296 `audit:` 예시에 `userSession: loginVO.uniqId` 추가
- `ProfileStoreTest` — `assertEquals("loginVO.uniqId", p.generator().audit().userSession())` 한 줄(RED → GREEN) + `new Profile.Audit(null, null, null, null, "  ").userSession()` 이 `""` 인 단언

### 7-20b — `FieldRoles` `user` 키 + 목록 + 세션 자리(§3-5)
- `apply` 새 키 `user`(boolean) = `!fixed && USERS.contains(audit) && T_STRING.equals(javaType)`(`USERS = Set.of(CREATOR, UPDATER)`). 키 순서: audit·now·user·html·…(모델 JSON 순)
- `lists(List<Map<String,Object>> fields, GenModel.Options o)` — 시그니처 교체(`dialect` 는 `o.dialect()`). 더하는 키: `insertUsers` = insertCols 중 `user` true · `updateUsers` = updateCols 중 `user` true · `sessionKey`·`sessionProp` = `Audit.orDefault(o.audit()).userSession()` 을 첫 `.` 에서 가른 둘(점이 없으면 prop `""`, 빈 글이면 둘 다 `""`). 모델 `now` 키는 **이 행에선 그대로**(7-21 이 없앤다)
- `GenModel.of` 의 `m.putAll(FieldRoles.lists(fields, dialect))` → `m.putAll(FieldRoles.lists(fields, o))` (한 줄, 길이 불변)
- `GenModelTest` — `rolesAndInputKinds` 에 단언: `FRST_REGISTER_ID` user true · `LAST_UPDUSR_ID` user true · `FRST_REGIST_PNTTM` user false · `columns(m, "insertUsers") == [FRST_REGISTER_ID]`(LAST_UPDUSR_ID 는 NULL 허용이라 INSERT 에 없음) · `columns(m, "updateUsers") == [LAST_UPDUSR_ID]` · `m.get("sessionKey") == "loginVO"`·`"sessionProp" == "uniqId"`. `auditOverride`(`Audit([REG_DT], [], [], [])`) → `insertUsers`·`updateUsers` 빈 목록. 새 `userSessionShapes` — `bbsOpts(null, new Audit(null,null,null,null,""))` → `sessionKey`·`sessionProp` 둘 다 `""` · `"userId"` → key `userId`·prop `""` · `overrideKeepsUserValue`(7-18a) 에 `FRST_REGISTER_ID: ColumnGen(null, true, …)` 하나 더 → user false·insertUsers 에 없음
- 골든 `-Dgolden.update=true -Dtest='GenModelTest'` → `gen/model.json`·`gen/model-bbs.json`(키 `user` + 목록 둘 + 세션 둘) — diff 가 그것뿐인지 읽는다

### 7-20c — `Controller.java.ftl` 로그인 ID 채우기 + javac(§3-6)
```
<#assign hasUser = insertUsers?has_content || updateUsers?has_content>
<#if hasUser>
import [=vars.ee].servlet.http.HttpServletRequest;
<#if sessionKey?has_content>
import [=vars.ee].servlet.http.HttpSession;
<#if sessionProp?has_content>
import org.springframework.beans.BeanWrapperImpl;
</#if>
</#if>
</#if>
```
- import 자리는 `import [=vars.ee].annotation.Resource;` 바로 아래(ee 묶음), `BeanWrapperImpl` 은 `org.springframework.stereotype.Controller` 앞(알파벳)
- `insert[=Name]` 시그니처 끝 `, ModelMap model<#if insertUsers?has_content>, HttpServletRequest request</#if>)`. `bindingResult` 검사 뒤·서비스 호출 앞에 `<#list insertUsers as f>        [=name]VO.set[=f.Name](loginId(request));\n</#list>`. `update[=Name]` 도 `updateUsers` 로 같은 꼴
- 클래스 끝(`delete` 뒤) 도우미:
```
<#if hasUser>

    /**
     * 로그인 사용자 ID — <#if sessionKey?has_content>세션 「[=sessionKey]」<#if sessionProp?has_content> 의 [=sessionProp]</#if>, 없으면 </#if>Principal 이름, 그래도 없으면 null(프로필 generator.audit.userSession).
     */
    private String loginId(HttpServletRequest request) {
<#if sessionKey?has_content>
        HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute("[=sessionKey]");
        if (user != null) {
<#if sessionProp?has_content>
            Object id = new BeanWrapperImpl(user).getPropertyValue("[=sessionProp]");
            return id == null ? null : id.toString();
<#else>
            return user.toString();
</#if>
        }
</#if>
        return request.getUserPrincipal() == null ? null : request.getUserPrincipal().getName();
    }
</#if>
```
- egov4·egov5 는 `extends` 라 자동 — `vars.ee` 가 `jakarta` 면 `jakarta.servlet.http.*`
- `GenTemplatesTest.bbsGolden` — 골든 파일 목록에 `ComtnbbsController.java` 추가(`gen/bbs/` 여섯). 자기 엔진 왕복(프로그램 분석 — 컨트롤러 메서드 일곱·CRUD 넷)은 bbs 에도 그대로 통과해야 한다
- `GeneratorJavacTest` — HR 7 생성 뒤 `Generator.run(set, List.of(GenModelTest.bbs()), new GenModel.Options("kr.go.egov", null, List.of("COMTN"), Map.of(), "oracle"), …)` 를 같은 `out` 에 한 번 더(같은 LocalFiles·인자) → javac 소스에 bbs 다섯이 들고 매퍼 파싱에 `Comtnbbs_SQL_oracle.xml` 이 든다. H2 건수·목록 루프는 HR 표 목록으로만(지금 그대로 — bbs 표는 H2 에 없다). 건너뛰기 사유 글 그대로
- 골든 `-Dgolden.update=true -Dtest='GenTemplatesTest'` → `gen/bbs/ComtnbbsController.java` 새 · `gen/egov35|egov4|egov5/*Controller.java` **불변**(empHist 는 감사 컬럼 없음 — 바뀌면 중단 조건) · 나머지 diff 0

### 7-21 — 문자형 감사 시각 `nowExpr`(§3-7)
- `FieldRoles`:
  - 필드 키 `nowExpr`(문자열) — `now` true 일 때 방언 식, 아니면 `""`. `now` 판정 확장: `!fixed && STAMPS.contains(audit) && (TIMES.contains(javaType) || T_STRING.equals(javaType) && fitsStamp(c.length()))`, `fitsStamp(len)` = `len == null || len >= 8`
  - 식: TIMES → `NOW.get(dialect)`(지금 맵 — oracle·tibero `SYSDATE` · postgresql·mariadb `NOW()` · mssql `GETDATE()`) · String 이고 `len == null || len >= 14` → `NOW_TEXT14` · 8 ≤ len < 14 → `NOW_TEXT8`:

| 방언 | 14자리 | 8자리 |
|---|---|---|
| oracle·tibero | `TO_CHAR(SYSDATE, 'YYYYMMDDHH24MISS')` | `TO_CHAR(SYSDATE, 'YYYYMMDD')` |
| postgresql | `TO_CHAR(NOW(), 'YYYYMMDDHH24MISS')` | `TO_CHAR(NOW(), 'YYYYMMDD')` |
| mariadb | `DATE_FORMAT(NOW(), '%Y%m%d%H%i%s')` | `DATE_FORMAT(NOW(), '%Y%m%d')` |
| mssql | `FORMAT(GETDATE(), 'yyyyMMddHHmmss')` | `FORMAT(GETDATE(), 'yyyyMMdd')` |

  - 판정·식은 도우미 `stampExpr(audit, javaType, c, dialect, fixed)` 하나(≤ 15) — `apply` 는 `String nowExpr = …; f.put(K_NOW, !nowExpr.isEmpty()); f.put(K_NOW_EXPR, nowExpr);`
  - `lists` 에서 모델 `now` 키 **삭제**
- `Mapper.xml.ftl` — INSERT `<#if f.now>[=f.nowExpr]<#else>#{[=f.name]}</#if>` · UPDATE SET `[=f.column] = <#if f.now>[=f.nowExpr]<#else>#{[=f.name]}</#if>`(모델 `now` 참조 둘을 바꾼다 — 그 밖 불변)
- `GenModelTest` 새 `textStamps` — 표 하나(감사 넷을 문자로): `REG_DT CHAR(14) NOT NULL`(created)·`REG_ID VARCHAR2(20) NOT NULL`(creator)·`UPD_DE CHAR(8)`(updated)·`UPD_ID VARCHAR2(20)`(updater)·`PK_ID NUMBER` PK + `Audit([REG_DT],[REG_ID],[UPD_DE],[UPD_ID])` × 방언 다섯 → `REG_DT` now true·nowExpr 14자리 식 · `UPD_DE` now true·8자리 식 · 같은 표에 `SHORT_DT CHAR(6)` 을 created 로 → now false·nowExpr `""`. `rolesAndInputKinds` 에 `FRST_REGIST_PNTTM` nowExpr `SYSDATE` 단언
- 골든 `-Dgolden.update=true -Dtest='GenModelTest,GenTemplatesTest'` → `gen/model*.json`(`now` 모델 키 사라짐·필드 `nowExpr`) · `gen/mapper-*.xml`·`gen/bbs/Comtnbbs_SQL_oracle.xml`·`gen/egov*/…_SQL_oracle.xml` 은 **글자 불변 기대**(식이 같은 글) — 바뀌면 중단 조건

### 1-69 — `DdlReader` typed table(§3-8)
- `TYPED_OF = Pattern.compile("(?i)OF\\s+[^\\s(]+")` · `NAME_ONLY` 에 `"typed", "OF 형식(typed table) — 컬럼은 형식"` 넷째. `nameOnly` 의 가지 순서: PARTITION OF → LIKE → **TYPED_OF** → CTAS. `nameOnly` 는 기준선 밖(복잡도 ≤ 15 유지)
- `DdlReaderTest` +2: `typedTable` — `CREATE TABLE employees OF employee_type (PRIMARY KEY (name)); CREATE TABLE t2 OF typ2;` → 표 둘 컬럼 0·notes `typed`·unreadable 0 · `ofColumnNameStillTable` — `CREATE TABLE OF_LIST (a INT, OF_CNT INT)` → 컬럼 2·notes 없음
- 기대: `sqltables-diff.txt` 31 → 30(postgres `create_table` 해소) — V-49a 에서 갱신. `gen/*`·`ddl-*` 골든 diff 0

### 측정·판정(§3-9)
- V-49a 재채점 — `bash scripts/mvn.sh -q test -Djunit.timeout=10m -Dtest='GeneratorCorpusTest,GeneratorJavacTest,SqlFixtureCorpusTest,DdlCorpusTest,AnalyzeCorpusTest,CliCorpusTest'` → A 0 · B 갱신 `-Dgolden.update=true` 는 `sqltables-diff`(31 → 30)·`gen-hr.json`(empHist 류 LIKE 늘면 checkSoft 변화 가능)·`ddl-lint*`(7-18b 가 바꾸면) 만 → diff 를 이력에. `GeneratorJavacTest` 가 bbs 를 javac 한 수(소스 수 35 + 5)를 이력에
- R-18 손 판정 — ① eGov oracle 181 표의 역할·꼴 재집계(jshell 로 `GenModel.of`, R-17 과 같은 셈 — 본문 안 옮김): text·textarea 건수(91 → 89 기대 — `NTT_SJ`·`WIKI_BKMK_NM`) · 검색 LIKE(797 → 878 안팎 — 길이 있는 textarea 81) · 검색 없는 표(20 → n) · `user` 컬럼(creator 135 + NOT NULL updater 5 = 140 안팎 INSERT · updater 135 UPDATE) · `nowExpr` 문자형 0(eGov 넷은 DATE) ② `sqltables-diff` 사라진 줄 1 판정 ③ `gen-hr.json` 변화 규칙 이름. 결함 둘 이하는 고침(음성 먼저), 넘으면 새 행
- V-49 — `design/compare-2026-10-28.md` 에 `## ⑲ 재측정 — 번들 69(날짜)`: 표 「역할·꼴 전후(⑱ 대비)」 · `sqltables-diff` 31 → N · javac 소스 수 · 벽시계 · 「드러난 것」 · 「후보로」(⑰ Minor 넷 · H2 FK 자동 인덱스 · 마-5·마-7·8 · `@DateTimeFormat` · PK 제약 이름 · a11y `<form:*>` · INSERT 의 NULL 허용 updater). 그 앞에 `bash scripts/mvn.sh -DskipTests verify`(PMD·SpotBugs·ErrorProne) rc 0 을 이력에

## 4. 실행 스텝
### S0 — 설계 문서·행·번들 표 (**이 설계 세션이 한다**, Opus 는 건너뛴다)
- 대상: `design/53-bundle69.md`(A 절) · `PLAN.md` 「번들」 표 「번들 69」 행 + 분할표(7-18 → `7-18a`·`7-18b` · `7-19`·`7-20` 다시 씀 · `7-20a`·`7-20b`·`7-20c`·`7-21`·`1-69`·`V-49a`·`R-18`·`V-49` 새 행, B 절) · `PLAN.md` 6장 :296 은 7-20a 몫 · `PROGRESS.md` 「열려 있는 것」 머리에 「번들 69(설계 53, …) 설계 끝 — 가지 `work/2026-11-02`, 다음은 「번들 해」」 + 이력 한 줄(직전 줄 해시 `2ee60f2`).
- 검증: `bash scripts/doc-lint.sh design/53-bundle69.md` · `bash scripts/verify.sh`(docs 레인) · `git commit -m "docs: S0 설계 53 — 번들 69"`. push 안 함.

### 7-18a — `FieldRoles` 재정의 꼴 + `input.ftl` + 음성 시험
- 대상: `core/gen/FieldRoles.java` · `templates/gen/egov35/input.ftl` · `core/gen/GenModelTest.java`(§3-1).
- 변경(TDD): ① `GenModelTest` `negatives`·`overrideKeepsUserValue` → RED(now·edit 단언) ② `FieldRoles` `fixed`·PK edit → GREEN ③ `input.ftl` `<#local>`·`readonly` 삭제(TDD 예외 — 템플릿, 출력 불변).
- 검증: `bash scripts/mvn.sh -q test -Dtest='GenModelTest,GenTemplatesTest,GeneratorTest,SelfCheckTest'` 초록 · `git status` 에 골든 변화 0 · `self-check.txt` diff 0.
- 의존: S0.

### 7-18b — `DdlReader` 인덱스 컬럼 정리 + 시험 둘
- 대상: `core/gen/DdlReader.java` · `core/gen/DdlReaderTest.java`(§3-2).
- 변경(TDD): ① 시험 둘 → RED(`I3`·`I4`·`I5` 컬럼) ② `INDEX_SORT`·`INDEX_PREFIX` → GREEN.
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,DdlGenTest,InsertGenTest,SelfCheckTest'` 초록 · `gen/*`·`ddl-*.sql`·`alter-*.sql` diff 0 · `self-check.txt` diff 0. `ddl-lint*` 는 V-49a 의 `DdlCorpusTest` 가 판정.
- 의존: 7-18a(가지 순서일 뿐).

### 7-19 — textarea 문턱: 이름 규칙 + 길이 있는 textarea LIKE
- 대상: `core/gen/FieldRoles.java` · `core/gen/GenModelTest.java`(`bbs()` 2000·`textareaBoundary`) · 골든 `gen/model.json`·`gen/model-bbs.json`·`gen/bbs/*`·`gen/egov35|egov4|egov5/*`·`gen/mapper-*.xml`(§3-3).
- 변경(TDD): ① `bbs()` 2000 + `textareaBoundary` → RED(`NTT_SJ` textarea·NONE) ② `isTitle`·`queryOf(sized)` → GREEN ③ `-Dgolden.update=true -Dtest='GenModelTest,GenTemplatesTest'` 한 번 → diff 읽기(§3-3 목록 밖이면 중단) · egov4·egov5 가 egov35 와 같은 줄인지.
- 검증: `bash scripts/mvn.sh -q test -Dtest='GenModelTest,GenTemplatesTest,GeneratorTest,GenerateRoutesTest,SmokeHtmlUnitTest#crudGeneratorRuns,SelfCheckTest'` 초록 · diff 를 이력에.
- 의존: 7-18a.

### 7-20a — 프로필 `generator.audit.userSession`
- 대상: `core/profile/Profile.java` · `profiles/example.yaml` · `core/profile/ProfileStoreTest.java` · `PLAN.md` 6장 :296(§3-4).
- 변경(TDD): ① `ProfileStoreTest` 단언 둘 → 컴파일 RED ② 레코드·yaml → GREEN.
- 검증: `bash scripts/mvn.sh -q test -Dtest='ProfileStoreTest,ProfileRoutesTest,GenModelTest,SelfCheckTest'` 초록 · PMD 새 짝 0.
- 의존: S0.

### 7-20b — `FieldRoles` `user` + 목록 + 세션 자리
- 대상: `core/gen/FieldRoles.java`(`user`·`lists(fields, o)`·`insertUsers`·`updateUsers`·`sessionKey`·`sessionProp`) · `core/gen/GenModel.java`(한 줄) · `core/gen/GenModelTest.java` · 골든 `gen/model.json`·`gen/model-bbs.json`(§3-5).
- 변경(TDD): ① 단언·`userSessionShapes` → RED ② `FieldRoles`·`GenModel` 한 줄 → GREEN ③ `-Dgolden.update=true -Dtest='GenModelTest'` → diff 가 새 키·목록뿐인지.
- 검증: `bash scripts/mvn.sh -q test -Dtest='GenModelTest,GenTemplatesTest,GeneratorTest,SelfCheckTest'` 초록 · `self-check.txt` diff 0(`GenModel.of` 길이 107 그대로) · `gen/egov*`·`gen/bbs`·`gen/mapper-*` diff 0(템플릿이 아직 안 쓴다).
- 의존: 7-20a·7-19.

### 7-20c — `Controller.java.ftl` + bbs Controller 골든 + javac bbs
- 대상: `templates/gen/egov35/Controller.java.ftl` · `core/gen/GenTemplatesTest.java`(bbsGolden 파일 하나 추가) · `core/gen/GeneratorJavacTest.java`(bbs 생성 추가) · 골든 `gen/bbs/ComtnbbsController.java`(새)(§3-6).
- 변경: TDD 예외(템플릿). RED→GREEN 은 `GenTemplatesTest.bbsGolden`(골든 없음 → 생성) + `GeneratorJavacTest`(egov35-lib 있을 때 — javac 0). `-Dgolden.update=true -Dtest='GenTemplatesTest'` 한 번 → `gen/egov35|egov4|egov5/*Controller.java` 불변 확인(바뀌면 중단).
- 검증: `bash scripts/mvn.sh -q test -Dtest='GenTemplatesTest,GenModelTest,GeneratorTest,GenerateRoutesTest,SmokeHtmlUnitTest#crudGeneratorRuns'` 초록 · `bash scripts/mvn.sh -q test -Djunit.timeout=10m -Dtest='GeneratorJavacTest'` 초록(건너뛰면 사유를 이력에) · `bash scripts/verify.sh` 초록.
- 의존: 7-20b.

### 7-21 — 문자형 감사 시각 `nowExpr`
- 대상: `core/gen/FieldRoles.java`(`stampExpr`·`NOW_TEXT14`·`NOW_TEXT8`·모델 `now` 삭제) · `templates/gen/egov35/Mapper.xml.ftl`(둘) · `core/gen/GenModelTest.java`(`textStamps`) · 골든 `gen/model*.json`(§3-7).
- 변경(TDD): ① `textStamps` → RED ② `FieldRoles` → GREEN ③ `Mapper.xml.ftl` `[=f.nowExpr]` ④ `-Dgolden.update=true -Dtest='GenModelTest,GenTemplatesTest'` → 매퍼 골든 글자 불변 확인·모델 JSON 만 변화.
- 검증: `bash scripts/mvn.sh -q test -Dtest='GenModelTest,GenTemplatesTest,GeneratorTest,SelfCheckTest'` 초록 · `gen/mapper-*.xml`·`gen/bbs/*_SQL_oracle.xml`·`gen/egov*/*_SQL_oracle.xml` diff 0.
- 의존: 7-20c.

### 1-69 — `DdlReader` typed table
- 대상: `core/gen/DdlReader.java`(`TYPED_OF`·`NAME_ONLY` 넷째·`nameOnly` 가지) · `core/gen/DdlReaderTest.java`(+2)(§3-8).
- 변경(TDD): ① 시험 둘 → RED ② `nameOnly` → GREEN.
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,DdlGenTest,SelfCheckTest'` 초록 · `self-check.txt` diff 0.
- 의존: 7-18b.

### V-49a — 재채점(corpus)
- 대상: 골든 `corpus/sqltables-diff.txt`·`corpus/gen-hr.json`·(7-18b 가 바꾸면) `corpus/ddl-lint.json`·`ddl-lint-findings.txt` · `PROGRESS.md`.
- 변경: §3-9 명령 → A 0 · B 갱신은 `-Dgolden.update=true` 로 바뀐 것만 → diff 를 이력에(sqltables-diff 31 → 30 · javac 소스 수).
- 검증: 위 명령 갱신 없이 초록 · `ddl-summary.json`·`ddl-unreadable.txt`·`hr-lint.json`·`pg-lint.json` diff 0.
- 의존: 1-69.

### R-18 — 손 판정
- 대상: `design/compare-2026-10-28.md`(⑲ 「손 판정」 절) · `PROGRESS.md` · (고침이 나면) `FieldRoles`·`DdlReader`·시험·골든.
- 변경: §3-9 ①~③. 표 하나(항목 · 표본 · 판정 · 근거) + 분류 합. 결함 둘 이하는 고치고(음성 먼저) 넘으면 「후보로」.
- 검증: `bash scripts/doc-lint.sh design/compare-2026-10-28.md` · (고침 있으면) 해당 행 검증 재실행.
- 의존: V-49a.

### V-49 — compare ⑲ · 정적 분석 · `--corpus` · `--db`
- 대상: `design/compare-2026-10-28.md`(⑲ 나머지) · `PROGRESS.md`.
- 검증: `bash scripts/mvn.sh -DskipTests verify` rc 0(PMD·SpotBugs·ErrorProne — 번들 68 CI 빨강 둘의 자리) → `bash scripts/verify.sh` 초록 → `bash scripts/verify.sh --corpus` 초록(다른 컨테이너·WSL 내리고) → `wsl --shutdown` · Docker 다시 → `bash scripts/verify.sh --db` 혼자 초록(약 17~20분, 다른 JVM 안 띄운다). 끝 줄이 더 부르는 레인이 없어야 한다. 벽시계 둘을 이력에.
- 의존: R-18.

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7 · 행의 축·강제 지점과 diff · 이름 규칙 넷의 음성(`_SJ` 가 아닌 2,000 은 textarea · CLOB 은 NONE · `J_SJ CHAR(1)` text) · 재정의 `fixed` 가 now·user 둘 다 끄는가 · PK edit 가 재정의에도 false · `loginId` 의 null 길 셋 · 세션 키 빈 글이면 Principal 만(import 도 안 남는가) · egov5 가 `jakarta.servlet.http` 인가 · `gen/egov*/*Controller.java`·매퍼 골든 불변 · `self-check.txt` 넉 줄 불변 · PMD 새 짝 0 · `sqltables-diff` 30 · 골든이 의도된 변화인가) → `bash scripts/gate-probe.sh` → `bash scripts/mvn.sh -DskipTests verify` → `bash scripts/verify.sh`(리뷰 고침 뒤 `--corpus`·`--db` 는 바뀐 레인만 다시) → `git push -u origin work/2026-11-02` → `gh pr create --base main`(행마다 시작 세 줄 표 + 로컬 docs 셋 `5a3d839`·`2ee60f2`·S0 같이 + ⑲ 핵심 수치) → CI 폴링(`gh api … check-runs`) → claude[bot] 처분 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → 닫기 커밋은 다음 가지 → 끝 보고. PR 본문 끝 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## 5. 금지 사항
- `pure/`·portfolio·`docs/`·`core/meta`·`core/dialect`·`JdbcMetaSource`·`tools/*.html`·`pom.xml`·`scripts/verify-fingerprint.sh`·`corpus/MANIFEST`·표본 수정 금지 · 새 Maven 의존성 금지 · 새 화면·라우트 금지
- `GenModel.Options` 인자 7 · `Generator.run` 인자 12 · `GenModel.of` 줄 수(길이 107) 불변 · `self-check.txt` 변화 금지(넉 줄 다 — 새 분기는 도우미에) · 새 메서드 복잡도 ≤ 15 · PMD 새 짝 금지(`FieldRoles`·`DdlReaderTest`·`GenTemplatesTest`·`GeneratorJavacTest`·`ProfileStoreTest` 는 기준선에 없다 — 시험의 되풀이 리터럴은 상수로) · SpotBugs: Boolean 은 `orDefault` 꼴, `Boolean.FALSE` 상수
- 문턱 1000 수치 변경 금지 · CLOB(길이 없음) 검색 금지 · INSERT 에 NULL 허용 updater 넣기 금지(설계 52) · `List`·`Regist`·`Updt`·`Detail`·`VO`·`Service`·`ServiceImpl`·`DAO`·`header` 템플릿 직접 수정 금지 · `@SessionAttribute`·스프링 시큐리티 클래스·eGov 공통컴포넌트 클래스 import 금지(rte 의존 jar 만) · `request.getSession()`(인자 없음 — 세션 생성) 금지, `getSession(false)` · 감사 컬럼이 없는 표의 컨트롤러에 import·인자·도우미가 남지 않는다
- 골든 `gen/egov35|egov4|egov5/*Controller.java` · 매퍼 골든(7-21) · `ddl-summary.json`·`ddl-unreadable.txt`·`hr-lint.json`·`pg-lint.json`·`alter-*.sql`·`ddl-*.sql`·`insert/*`·`meta/*` 불변 · `DdlCorpusTest` 판정 로직 수정 금지
- 골든·compare·이력에 표본 코드 본문·생성물 본문을 싣지 않는다(건수·이름·꼴만) · 원본 되쓰기 API·CLI 금지 · `pmd-baseline.sh` 안 돌린다 · `--db` 와 다른 JVM 겹치기 금지 · `--corpus` → Docker → `--db` 순서

## 6. 최종 검증
- `bash scripts/mvn.sh -DskipTests verify` rc 0 · `bash scripts/verify.sh` · `--corpus` · `--db` 초록(순서대로, 따로) · `gate-probe.sh` 10/10
- 골든: `gen/model.json`·`gen/model-bbs.json`·`gen/bbs/*`(+Controller)·`gen/egov*/{List,Regist,Updt,VO,Mapper}`(7-19 가 바꾼 만큼)·`gen/mapper-*.xml`(7-19)·`corpus/sqltables-diff.txt`(30)·`corpus/gen-hr.json`·(조건부) `corpus/ddl-lint*` 갱신 · 나머지 불변 · `self-check.txt` 불변
- 시험: `GenModelTest` +5(`negatives`·`overrideKeepsUserValue`·`textareaBoundary`·`userSessionShapes`·`textStamps`) · `DdlReaderTest` +4 · `ProfileStoreTest` +2 단언 · `GenTemplatesTest.bbsGolden` 파일 여섯 · `GeneratorJavacTest` 소스 35 + 5 · `GeneratorCorpusTest` A 0 · `SmokeHtmlUnitTest.crudGeneratorRuns` 초록 · `ArchitectureTest`·`VerifyLanesTest` 초록
- 수동: 데모 스냅샷 eGov 꼴 표 하나 생성 → 결과 폴더 `*Controller.java` 의 `insert…` 에 `setFrstRegisterId(loginId(request))` · `*Regist.jsp` 제목이 `<form:input … maxlength>` · `*List.jsp` 에 제목·내용 검색칸(눈으로 한 번, 이력에 한 줄)
- compare ⑲ 표 + R-18 표 + 「드러난 것」 + 「후보로」 · 이력에 건수·벽시계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- `self-check.txt` 변화 · `gen/egov35|egov4|egov5/*Controller.java` 변화(7-20c) · 매퍼 골든 글자 변화(7-21) · `gen/egov4`·`egov5` 변화가 egov35 와 다름(7-19) · `ddl-summary`·`ddl-unreadable`·`hr-lint`·`pg-lint`·`alter-*`·`ddl-*.sql` 변화 · `pmd:check`·SpotBugs 새 짝 · `ArchitectureTest`·`VerifyLanesTest` 빨강
- `GeneratorJavacTest` 가 bbs 컨트롤러에서 javac 오류(`BeanWrapperImpl`·`HttpSession` 못 찾음 등) — 고침 둘로 안 닫힘 · `GeneratorCorpusTest` A 고침 둘로 안 닫힘
- `sqltables-diff` 가 30 이 안 됨(typed table 을 못 읽는다) · `ddl-lint*` 변화가 dupIndex 밖
- `verify.sh --db` 메모리로 끊김 — 고아 JVM 끄고 한 번 더, 또 끊기면 보고
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① 7-19 가 empHist 골든(`gen/model.json`·`gen/egov*/*`·`gen/mapper-*.xml`)을 바꾸는 범위(`NOTE` 가 길이 있는 textarea 면 LIKE 가 생긴다) ② 7-18b 가 `ddl-lint*` 를 바꾸는지(표본에 `NULLS LAST` 단독·접두 길이가 있는가) ③ `gen-hr.json` checkSoft 변화 ④ `--corpus`·`--db` 벽시계. 첫 스텝 전: `bash scripts/verify.sh` 초록 · `git log --oneline -1`(S0 커밋) · `ls C:/workspace/toolbox-corpus/egov35-lib | wc -l`(35) · Docker Desktop 상태
- **사용자만 정할 수 있는 것** — 없음((나)+(라) · (가) · 작은 것 둘 · 머지 위임 전부 2026-11-02 결정)
