# 설계 55 — 번들 71: 생성기 오류 잡기 — Java 쪽 정확도(7-22·7-23·날짜 바인딩·egov35 Date) + DdlPull 1-72 + 프로필 고르기 nowrap (Fable 설계 · Opus 실행, 2026-11-03)

## 맥락
번들 69(설계 53)·70(설계 54)이 「대기」 로 남긴 생성기 행 셋(7-22·7-23·1-72)과 compare ⑲·⑳ 「후보로」 의 `@DateTimeFormat`, 그리고 검증 안 돈 곁가지 `wip/profile-nowrap` 을 한 번들로 닫는다. 판단 기준은 **생성 결과의 Java 쪽(VO·DAO·Service·ServiceImpl·Mapper·Controller) 정확도** — JSP 는 깨지지 않을 만큼만(사용자 2026-11-03). 새 화면·새 기능 없음.

실측(2026-11-03, 코드 읽기):
- `Profile.Audit`(`core/profile/Profile.java:158`) 컴팩트 생성자는 `userSession` 을 trim 만 한다. `"` `\` 이 들면 생성 Controller 의 `getAttribute("…")` 문자열이 깨져 컴파일 실패, 끝 점이면 `sessionProp` 이 `""` 라 `user.toString()` 가지로 간다. YAML 의 값 오류는 Jackson 이 `IllegalArgumentException` 을 원인으로 감싸고 `ProfileStore.reason` 이 「n줄 m칸 근처 <글>」 로 풀어 `UncheckedIOException` → `GET /api/profiles/{name}` 400(`ProfileRoutes:57`)·활성 프로필 400(`App:133`)
- `GenTemplatesTest.render(set, dialect, table)` 은 Options 5인자(audit 기본 EGOV)라 `userSession` 을 바꿔 렌더하는 길이 없다. 7-22 행이 적은 `Audit(null, null, null, null, "")` 는 **감사 묶음이 비어 `hasUser` 가 false** 가 되므로 가지 시험이 안 된다 — EGOV 넷 + 세션 글로 만들어야 한다
- `FieldRoles.sqlCols`(`:166`) — INSERT 는 `insert || CREATES || UPDATES && !nullable`. `K_NULLABLE` 은 여기서만 쓴다
- `GenTemplatesTest.mapperPerDialect`(`:66`)가 **bbs** 로 돈다 — 7-23 은 `gen/mapper-{oracle,tibero,postgresql,mariadb,mssql}.xml` 다섯도 바꾼다(설계 52·53 과 다름)
- 날짜 타입: `type-mapping.yaml` — Oracle·Tibero `DATE`·모든 `TIMESTAMP` 류 → `java.time.LocalDateTime`, 그 밖 `DATE` → `LocalDate`, `TIME` → `LocalTime`. VO 는 `[=f.javaType]` 그대로, 어노테이션은 `f.annotations`(`Validation.of` — `vars.valid` 가 `"true"` 일 때만, 아니면 빈 목록). 등록 화면은 `datetime-local`·`date`·`time`. 템플릿·`core/gen` 어디에도 `@DateTimeFormat`·`InitBinder` 없음
- Spring 4.0.9(egov35-lib)는 JSR-310 을 받지만 기본 꼴이 로캘 SHORT 라 `2026-11-03T10:30` 을 못 받는다. `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)` 은 `DateTimeFormatter.ISO_DATE_TIME` — 초 있고 없고 둘 다 읽고, 찍으면 `2026-11-03T10:30:00`(datetime-local 이 받는 꼴). `ISO.DATE` ↔ `type="date"`, `ISO.TIME` ↔ `type="time"` 같은 이치. `org.springframework.format.annotation.DateTimeFormat` 은 `spring-context` 에 있다(egov35-lib 에 `spring-context-4.0.9.RELEASE.jar`)
- **eGov 3.5.0 실행환경의 MyBatis 는 3.2.8**(egov35-lib `mybatis-3.2.8.jar`·`mybatis-spring-1.2.2.jar`). java.time 타입핸들러는 MyBatis 3.4.5 부터 내장 — 3.2.8 은 생성 매퍼 resultMap 의 DATE 컬럼 ↔ `LocalDateTime` 을 못 붙여 기동 때 「No typehandler found for property …」(판단 — 실행 안 함. `GeneratorJavacTest:121` 은 일부러 테스트 경로의 MyBatis 로 돌려서 이 차이를 못 본다). egov4·egov5 는 MyBatis 3.5 라 문제 없다
- `TemplateSet.load` — `extends` 한 단계, `vars` 는 **부모 위에 덮는다**(`:77·:81`). egov4·egov5 는 egov35 의 vars 를 물려받으니 새 키는 자식에 명시해야 한다. `GenModelTest.VARS` 는 `{rte}` 뿐 — 모델 골든은 새 키 없이 돈다
- `DdlPull`(`core/gen/DdlPull.java`): `LIKE_SRC = (?i)LIKE\s+([^\s(,]+)` 는 원본 이름만 잡고 `INCLUDING …` 은 안 본다. 괄호 안 LIKE 는 `like(d, item)`(`:98`, item 전체 글이 있다), 머리 LIKE(MySQL 꼴)는 `pull`(`:79`)이 `copy(d, src, true)`. `copy`(`:109`)는 `d.pk.addAll(src.pk)` — 안 비운다(`DdlReader.item` 의 PK 제약 줄은 `d.pk.clear()` 뒤 넣는다 `:319`). `typedOption`(`:156`)은 PK·NOT NULL·UNIQUE 만, `REFERENCES`·`CHECK` 는 `TYPED_WORDS` 에 들어 「읽음」 으로 지나가고 버려진다. `DdlReader` 의 `COL_REF`(`:98`)·`fk(…)`(`:537`)·`checks(d, text)`(`:516`)는 private
- `common.js` `profilePicker()`(`:203`) — `<header>` 있는 화면은 `marginLeft:auto` 로 header flex 끝에, 없는 화면(db_browser·deliverable_sql·**dev_tools**·logical_name)은 `.tb-profile-row`(float:right). `#tb-profile` 은 `.opt`(display:flex) — 375px 에서 글 「프로필」 이 꺾인다. `cc64dbf`(`wip/profile-nowrap`)는 `'#tb-profile{white-space:nowrap;flex-shrink:0}' +` 한 줄(`:67`). 검증 안 돈 상태. 그 가지의 PLAN·PROGRESS·compare 차이는 1-72·마-7·8 결정 커밋이 `work/2026-11-03` 에만 있어서다 — 코드 차이는 그 한 줄뿐
- 메서드 수 상한 30(`metric.classMethods`) — `FieldRoles` 17 · `DdlPull` 16. 여유 있음. `self-check.txt` 의 `GenModel.of(4)` 복잡도 34·길이 107 — **줄을 더하지 않고 바꾼다**
- 레인: `core/gen`·`src/main/resources/gen` 은 db·corpus 둘 다 · `templates/` 는 java 만 · `common.js` 는 java · `core/profile` 은 무거운 레인에서 뺀다(`LANE_*_SKIP`)

**이미 끝난 것 — 걷어 낸다**: 마-5(2026-11-02)·마-7·8(2026-11-03) 사용자 안 함. compare ⑳ 「후보로」 의 a11y `<form:*>`·개발자 도구 새 탭·fkTypeMismatch 동의어·`LONG VARCHAR`·`STRING` LOB·CTAS 조인·typed `WITH OPTIONS DEFAULT` 값·이름 없는 유니크 detail 은 이 번들 밖(오류가 아니거나 생성기 밖).

## 사용자 결정(2026-11-03)
- 생성 결과는 Java 쪽만 가져다 쓴다 — 판단 기준은 Java 쪽 정확도, JSP 는 깨지지 않을 만큼만. 마-5·마-7·8 안 함
- 7-23 (나)(2026-11-02): 등록 INSERT 에도 NULL 허용 수정자 둘
- 1-72 (나)(2026-11-03): PG `LIKE` 는 `INCLUDING INDEXES|ALL` 일 때만 PK. MySQL `LIKE`·`PARTITION OF` 는 그대로
- **egov35 날짜 (나)**: egov35 세트의 날짜 타입은 `java.util.Date`(MyBatis 3.2.8 — 지금 폐쇄망이 eGov 3.5.0). egov4·egov5 는 `java.time` 그대로. 버린 것 — (가) java.time 유지 + 현장에서 핸들러 jar 반입(폐쇄망에서 또 반입) · 세트 전부 Date(새 세대에서 잃을 이유 없음) · 프로필 키로 고르기(세트가 이미 세대를 나타낸다, 고를 칸 안 늘림) · String(BETWEEN·`now`·바인딩 전부 손이 간다)
- `wip/profile-nowrap` 은 1-73 으로 흡수한 뒤 실행 세션이 `git branch -D` 로 지운다(가)
- 번들 하나·PR 하나. 머지 위임 있음
- 설계가 정한 것(기본값이 뚜렷해 안 물음): `userSession` 허용 꼴은 자바 이름을 점으로 이은 것(`a.b.c` 허용 — `BeanWrapperImpl` 이 중첩 경로를 받는다) · `@DateTimeFormat` 은 java.time 필드 전부(감사 시각 포함 — 바인딩엔 안 쓰이지만 해롭지 않고 규칙이 하나) · 세트 변수 이름 `javaTime`(`"true"|"false"`, 없으면 `"true"`) · legacy 패턴 `yyyy-MM-dd` / `yyyy-MM-dd'T'HH:mm` / `HH:mm` · `copy` 는 원본 PK 가 있으면 `d.pk.clear()` 뒤 복사(뒤 PK 가 이긴다 — `item` 의 PK 줄과 같은 규칙) · `INCLUDING|EXCLUDING INDEXES|ALL` 이 여럿이면 **마지막** 것이 이긴다(PG 가 뒤 절로 앞 절을 덮는다)

실행자는 Opus. 차례 `S0`(이 설계 세션이 했다) → `7-22` → `7-23` → `7-24` → `7-25` → `1-72` → `1-73` → `V-51a` → `R-20` → `V-51` → 마무리. 가지 `work/2026-11-03`(로컬 닫기 `f7d732e`·결정 `3fe188e`·`bf5c83e`·S0 같이). 번들 하나·PR 하나.

---

## 1. 목표와 범위
- 한 문장: 생성 Java 가 eGov 3.5.0 현장(Spring 4.0.9·MyBatis 3.2.8)에 그대로 들어가 등록·수정이 돈다 — 등록 INSERT 가 수정자 둘도 채우고, 등록·수정 화면의 날짜가 VO 에 묶이며, egov35 세트의 VO 날짜가 `java.util.Date` 이고, 프로필의 세션 자리 오타가 저장 단계에서 걸린다. 덤으로 DDL 읽기의 PG `LIKE` PK 규칙·typed 몸 FK·CHECK 와 프로필 고르기 줄바꿈.
- 완료 시 체감: 데모 스냅샷 eGov 꼴 표 하나 생성 → `*_SQL_oracle.xml` INSERT 에 `LAST_UPDT_PNTTM`·`LAST_UPDUSR_ID` 가 있고 `*Controller.java` 등록에 `setLastUpdusrId(loginId(request))` 가 있다. `*VO.java`(egov35)의 날짜 필드가 `@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") private Date …` 이고 egov5 는 `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime …`. 프로필 `userSession: 'loginVO.'` 는 400. dev_tools 375px 에서 「프로필」 이 한 줄. compare ㉑.
- In scope: `core/profile/Profile.java`(Audit 검증) · `core/gen/FieldRoles.java`·`GenModel.java`(한 줄 교체)·`DdlPull.java`·`DdlReader.java`(가시성만) · `templates/gen/{egov35,egov4,egov5}/set.yaml` · `tools/common.js`(한 줄) · 시험 `ProfileStoreTest`·`GenModelTest`·`GenTemplatesTest`·`DdlReaderTest` · 골든 `gen/model*.json`·`gen/bbs/*`·`gen/egov*/EmpHistVO.java`·`gen/mapper-*.xml`·(조건부) `corpus/gen-hr.json` · `design/compare-2026-10-28.md` ㉑ · PLAN·PROGRESS.
- Out of scope: 마-5·마-7·8·마-9 · JSP 모양(Detail 의 날짜 표시 `<fmt:formatDate>` 포함) · `List`·`Regist`·`Updt`·`Detail`·`VO`·`Controller`·`Mapper`·`input`·`header` 템플릿 본문(이 번들은 `set.yaml` 과 모델만 바꾼다 — VO 는 `f.annotations`·`f.javaType`·`imports` 로 따라온다) · `type-mapping.yaml`·`TypeMapping`·`DtoGenerator`(dev_tools DTO 카드는 java.time 그대로) · `Validation` · 코드 검사 규칙 · 개발자 도구 새 탭 · CTAS 조인 · typed `WITH OPTIONS DEFAULT` 값 · 이름 없는 유니크 detail · 순수본·표본 · 현장 MyBatis 판 올리기(사람 몫).

## 2. 현재 구조 요약
「맥락」 의 실측이 요약이다. 덧붙이는 함정:
- `GenModel.of(4)` 는 기준선(복잡도 34·길이 107) — 새 분기·새 줄 금지. 날짜 어노테이션·타입 치환은 `m.put("imports", new ArrayList<>(imports))`(`:165`) 한 줄을 `m.put("imports", FieldRoles.dates(fields, imports[, vars]))` 로 **바꿔** 끼운다(줄 수 불변). `FieldRoles.apply` 가 html·now·query 를 **java.time 이름으로 먼저** 정한 뒤 `dates` 가 javaType 을 바꾸므로 역할은 그대로다
- `f.annotations` 는 `Validation.of` 가 준 불변 목록(`List.copyOf`) — 바꿀 땐 새 목록으로 `put`
- `imports` 는 `TreeSet` — `dates` 가 `java.time.*` 셋을 빼고 `java.util.Date` 를 넣은 뒤 `new ArrayList<>(…)` 로 돌려준다(정렬 유지)
- FreeMarker 보간 `[=a]` 는 `"` 를 그대로 낸다 — `Templates:63` `DISABLE_AUTO_ESCAPING_POLICY`(실측). `@DateTimeFormat(pattern = "…")` 이 그대로 나간다
- `sqlCols` 에서 `K_NULLABLE` 을 쓰지 않게 되면 상수를 지운다(PMD `UnusedPrivateField` 새 짝 방지)
- `GenModelTest.textStamps`(`:270`)의 `UPD_DE CHAR(8)`(updated, NULL 허용)이 7-23 뒤 `insertCols` 에 든다 — 그 시험은 `insertCols` 를 단언하지 않는다(실측 — `insertCols` 단언은 `:158`·`:195`·`:235` 셋뿐, `auditOverride`·`overrideKeepsUserValue` 는 그대로 통과)
- `DdlPull.like(d, item)` 은 PG 꼴(괄호 안)만, `pull` 의 LIKE 키는 MySQL 꼴(머리)만 — 문법으로 갈린다(PG 는 머리 LIKE 없음, MySQL 은 괄호 안 LIKE 없음). 사용자 결정의 「PG 만」 은 이 갈림 그대로
- `DdlReader.checks(d, rest)` 는 따옴표를 안 벗기고 `CHECK (` 를 찾는다 — 컬럼 가지도 같다. typed 몸에서 같은 도우미를 부르면 같은 한계(기본값 글 안의 `CHECK (`)를 공유한다. 바꾸지 않는다
- `common.js` 는 `src/main/resources` 라 java 레인 — `SmokeHtmlUnitTest.profilePickerOnProfileScreens`·`ToolsFolderTest` 가 돈다. 배치는 HtmlUnit 이 못 재므로 집 검증(Puppeteer 375px)으로만
- `-Dgolden.update=true` 는 행마다 해당 시험만(`-Dtest=`) 한 번, diff 를 읽고 이력에

## 3. 설계 결정 — 전부 [고정]

### 7-22 — `userSession` 값 검증 + 컨트롤러 가지·길이 있는 textarea 렌더 시험(§3-1)
- `Profile.Audit`:
```java
/** 세션 자리 꼴 — 자바 이름을 점으로 이은 것(BeanWrapperImpl 중첩 경로). 빈 글은 Principal 만 */
private static final Pattern SESSION_PATH = Pattern.compile("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*");
public Audit {
    … (넷 그대로)
    userSession = userSession == null ? EGOV_USER_SESSION : userSession.trim();
    if (!userSession.isEmpty() && !SESSION_PATH.matcher(userSession).matches()) {
        throw new IllegalArgumentException("generator.audit.userSession 은 「속성[.프로퍼티]」 꼴(자바 이름을 점으로 이음)이어야 한다: " + userSession);
    }
}
```
  - YAML 에서 틀린 값 → Jackson 이 감싸 `ProfileStore.reason` 의 `IllegalArgumentException` 가지 → `UncheckedIOException`(「n줄 m칸 근처 generator.audit.userSession 은 …」) → 읽기 400. 새 라우트·새 예외 없음. javadoc 한 줄
- `ProfileStoreTest` 새 `userSessionShapeRefused` — 생성자: `"login\"VO"`·`"login\\VO"`·`"loginVO."`·`"1a"` 넷 `assertThrows(IllegalArgumentException.class, …)` · 받는 것 `"a.b.c"`·`"$s"`·`""` 셋 `userSession()` 그대로 · 파일: `@TempDir` 에 `name: x\ngenerator:\n  audit:\n    userSession: 'loginVO.'\n` → `assertThrows(UncheckedIOException.class, () -> ProfileStore.load(f))` 의 `getMessage()` 가 `userSession` 을 담는다(`:73` 꼴과 같게)
- `GenTemplatesTest`:
  - `render(String setName, String dialect, Table table, Profile.Audit audit)` 4인자 추가 — Options 7인자(`"kr.go.hr", null, List.of("TB"), Map.of("DEPT_NM", "부서명"), dialect, null, audit`). 3인자는 `audit = null` 로 위임(`orDefault` → EGOV, 지금과 같음)
  - 도우미 `static Profile.Audit egovSession(String s)` = `new Profile.Audit(EGOV.created(), EGOV.creator(), EGOV.updated(), EGOV.updater(), s)` · `static String file(Map<String,String> out, String suffix)` = 경로가 suffix 로 끝나는 값 하나(없으면 fail)
  - 새 `controllerSessionShapes` — `render(EGOV35, ORACLE, bbs(), egovSession(""))` 의 `Controller.java`: `getUserPrincipal()`·`HttpServletRequest` 있음, `HttpSession`·`getAttribute(`·`BeanWrapperImpl` 없음 · `egovSession("userId")`: `getAttribute("userId")`·`user.toString()` 있음, `BeanWrapperImpl`·`getPropertyValue` 없음 · 기본(EGOV)은 bbs 골든이 이미 본다
  - 새 `sizedTextareaRendersSearch` — `render(EGOV35, ORACLE, one(col("C2000", 2, "VARCHAR2", 2000L, null, null, true, "설명")))`: `List.jsp` 에 `<form:input path="c2000"`·`<label for="c2000"` · `Regist.jsp` 에 `<form:textarea path="c2000"` · `_SQL_oracle.xml` 에 `C2000 LIKE`·`c2000 != null` — 골든 없이 `contains`
- 골든 diff 0(`self-check.txt` 포함). `core/profile` 은 빠른 레인, 시험 둘은 java 레인

### 7-23 — 등록 INSERT 에 NULL 허용 수정자(§3-2)
- `FieldRoles.sqlCols` — INSERT 조건 `Boolean.TRUE.equals(f.get(K_INSERT)) || CREATES.contains(audit) || UPDATES.contains(audit)`(`&& nullable` 가지 삭제), javadoc 「INSERT — 등록 칸 + 감사 넷(7-23 — NULL 허용 수정자도). UPDATE — 수정 칸 + 수정 감사」. `K_NULLABLE` 상수 삭제(그 밖에 안 쓴다 — 실측). `nowExpr`·`user` 는 필드 키라 INSERT 에서도 그대로 산다(`Mapper.xml.ftl` INSERT 가 `f.now` 면 `[=f.nowExpr]`)
- `GenModelTest.rolesAndInputKinds` — `insertCols` = `[NTT_ID, BBS_ID, NTT_SJ, NTT_CN, USE_AT, NTCE_BGNDE, RDCNT, SNTNC_SE, FRST_REGIST_PNTTM, FRST_REGISTER_ID, LAST_UPDT_PNTTM, LAST_UPDUSR_ID]`(글 「NULL 허용 LAST_* 도 INSERT 에 — 7-23」) · `insertUsers` = `[FRST_REGISTER_ID, LAST_UPDUSR_ID]` · `updateCols`·`updateUsers` 그대로. `overrideKeepsUserValue`·`auditOverride` 는 그대로 통과해야 한다(재정의 `fixed` 는 `user` 만 끈다)
- 골든 `-Dgolden.update=true -Dtest='GenModelTest,GenTemplatesTest'`: `gen/model-bbs.json`(insertCols +2 · insertUsers +1) · `gen/bbs/Comtnbbs_SQL_oracle.xml`(INSERT 컬럼 끝 `LAST_UPDT_PNTTM, LAST_UPDUSR_ID` · VALUES 끝 `SYSDATE, #{lastUpdusrId}`) · `gen/bbs/ComtnbbsController.java`(`insertComtnbbs` 에 `comtnbbsVO.setLastUpdusrId(loginId(request));` 한 줄 더, `setFrstRegisterId` 뒤) · `gen/mapper-{oracle,tibero,postgresql,mariadb,mssql}.xml`(bbs — INSERT 두 컬럼, 시각 식은 방언 NOW) · **불변**: `gen/model.json`·`gen/egov*/*`(empHist 는 감사 컬럼 없음)·`corpus/gen-hr.json`·`self-check.txt`. diff 가 그것뿐인지 읽는다

### 7-24 — 날짜 바인딩 `@DateTimeFormat`(§3-3)
- `FieldRoles` 새 상수 `K_ANNOTATIONS = "annotations"` · `T_TIME = "LocalTime"` · `DATE_TIME_FORMAT = "org.springframework.format.annotation.DateTimeFormat"` · `ISO = Map.of(T_DATE, "DATE", T_DATETIME, "DATE_TIME", T_TIME, "TIME")`
- 새 도우미(≤ 15):
```java
/** 7-24 — java.time 필드마다 @DateTimeFormat(iso = …) 을 annotations 앞에 끼우고 import 를 더한 imports 목록 */
static List<String> dates(List<Map<String, Object>> fields, Set<String> imports) {
    for (Map<String, Object> f : fields) {
        String iso = ISO.get(f.get(K_JAVA_TYPE));
        if (iso != null) {
            f.put(K_ANNOTATIONS, prepend("@DateTimeFormat(iso = DateTimeFormat.ISO." + iso + ")", f.get(K_ANNOTATIONS)));
            imports.add(DATE_TIME_FORMAT);
        }
    }
    return new ArrayList<>(imports);
}
```
  (`K_JAVA_TYPE = "javaType"`, `prepend` 는 새 `ArrayList` — 어노테이션 하나 + 기존 목록)
- `GenModel.of` `:165` — `m.put("imports", new ArrayList<>(imports));` → `m.put("imports", FieldRoles.dates(fields, imports));`(한 줄 교체, 길이·복잡도 불변)
- `GenModelTest` 새 `dateBinding` — bbs(VARS): `NTCE_BGNDE`·`FRST_REGIST_PNTTM` annotations `[@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)]` · `NTT_SJ` `[]` · imports 에 `org.springframework.format.annotation.DateTimeFormat` 있음 · `one(col("D", 2, "DATE", …))` × postgresql → `ISO.DATE` · `one(col("T", 2, "TIME", …))` × postgresql → `ISO.TIME` · 날짜 없는 `one(col("A", 2, "VARCHAR2", 10L, …))` → imports 에 없음. `rolesAndInputKinds` 의 html·query 단언은 그대로 통과(역할은 먼저 정해진다)
- 골든 `-Dgolden.update=true -Dtest='GenModelTest,GenTemplatesTest'`: `gen/model.json`·`gen/model-bbs.json`(annotations·imports) · `gen/egov35|egov4|egov5/EmpHistVO.java`(import 한 줄 + `START_DATE`·`END_DATE` 류에 어노테이션 — egov5 는 기존 `@Size` 류 앞) · `gen/bbs/ComtnbbsVO.java` · **불변**: 매퍼·Controller·JSP·`*-egovvo.java`(DtoGenerator)·`gen-hr.json`·`self-check.txt`
- `GeneratorJavacTest`: egov35 출력이 `spring-context-4.0.9` 로 컴파일된다(`@DateTimeFormat` 있음) — V-51a 가 잰다

### 7-25 — egov35 세트는 `java.util.Date`(§3-4)
- `set.yaml` — egov35 `vars` 에 `javaTime: "false"   # "false" 면 VO 날짜를 java.util.Date + @DateTimeFormat(pattern) 으로 — eGov 3.5 의 MyBatis 3.2.8 엔 java.time 핸들러가 없다(3.4.5 부터)` · egov4 `javaTime: "true"` · egov5 `javaTime: "true"`(부모 vars 를 덮는다 — 명시 필수). 다른 키 그대로
- `FieldRoles.dates(fields, imports, vars)` — 셋째 인자. `legacy = vars != null && "false".equals(vars.get("javaTime"))`. legacy 면 어노테이션이 `@DateTimeFormat(pattern = "<p>")`(`PATTERN = Map.of(T_DATE, "yyyy-MM-dd", T_DATETIME, "yyyy-MM-dd'T'HH:mm", T_TIME, "HH:mm")`), `f.put(K_JAVA_TYPE, "Date")`, 루프 뒤 `imports.removeAll(JAVA_TIME)`(`java.time.LocalDate`·`LocalDateTime`·`LocalTime`)·`imports.add("java.util.Date")`(날짜가 하나라도 있을 때). `string`·`html`·`query`·`now`·`nowExpr` 불변. 복잡도 ≤ 15(루프 + 분기 넷)
- `GenModel.of` `:165` — `FieldRoles.dates(fields, imports, vars)`(한 줄 교체)
- `GenModelTest` 새 `legacyDates` — `GenModel.of(bbs(), bbsOpts(null, null), Map.of("rte", "egovframework.rte", "javaTime", "false"), TYPES)`: `NTCE_BGNDE` javaType `Date`·annotations `[@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")]`·html `datetime`·query `BETWEEN` · `FRST_REGIST_PNTTM` now true·nowExpr `SYSDATE`·javaType `Date` · imports 에 `java.util.Date` 있고 `java.time.LocalDateTime` 없음 · `NTT_SJ` javaType `String` 그대로 · `Map.of("javaTime", "true")`·`VARS`(키 없음) → `LocalDateTime` + iso
- 골든 `-Dgolden.update=true -Dtest='GenTemplatesTest'`: `gen/egov35/EmpHistVO.java`(`import java.util.Date;` · `@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") private Date startDate;` 류) · `gen/bbs/ComtnbbsVO.java`(같은 꼴, 감사 시각 둘·`NTCE_BGNDE`) · **불변**: `gen/egov4|egov5/EmpHistVO.java`(7-24 꼴) · 모델 JSON(VARS 에 키 없음) · 매퍼·Controller·JSP · `self-check.txt`
- `GeneratorJavacTest`: egov35 HR 7 + bbs 가 `Date` 로 컴파일·매퍼 파싱·H2 건수(테스트 경로 MyBatis 는 Date 도 받는다) — V-51a

### 1-72 — `DdlPull` LIKE PK 규칙 + typed 몸 FK·CHECK(§3-5)
- `DdlPull`:
  - `copy(d, src, withPk)` — `withPk && !src.pk.isEmpty()` 면 `d.pk.clear();` 뒤 `addAll`·`pkName`(뒤 PK 가 이긴다)
  - 새 `LIKE_PK = (?i)\b(INCLUDING|EXCLUDING)\s+(INDEXES|ALL)\b` · 도우미 `static boolean likePk(String item)` — 마지막 매치가 `INCLUDING` 이면 참, 매치 없으면 거짓
  - `like(d, item)` — `copy(d, d.scope.get(key(m.group(1))), likePk(item))`. `pull` 의 LIKE 키(MySQL 머리 꼴)·PARTITION 은 `true` 그대로
  - `typedOption(d, name, rest)` 끝에 컬럼 가지와 같은 둘: `Matcher ref = DdlReader.COL_REF.matcher(rest); if (ref.find()) d.fks.add(DdlReader.fk(d, null, name, ref.group(1), ref.group(2)));` · `DdlReader.checks(d, rest);`. 복잡도 ≤ 15
- `DdlReader` — `COL_REF`·`fk(…)`·`checks(…)` 를 package-private 로(가시성만, 본문 불변). `self-check.txt` 불변
- `DdlReaderTest`:
  - `likeFromSource` 그대로(Z 는 `INCLUDING ALL` 이라 PK `[A]` 유지)
  - 새 `likeIncludingIndexesOnly` — `CREATE TABLE X (A INT NOT NULL, B INT, CONSTRAINT pk_x PRIMARY KEY (A)); CREATE TABLE Z1 (LIKE X); CREATE TABLE Z2 (LIKE X INCLUDING INDEXES); CREATE TABLE Z3 (LIKE X INCLUDING ALL EXCLUDING INDEXES); CREATE TABLE Z4 (C INT PRIMARY KEY, LIKE X INCLUDING ALL); CREATE TABLE Z5 (LIKE X INCLUDING CONSTRAINTS); CREATE TABLE Y LIKE X;` → Z1 `pk()` null · Z2 PK `[A]` 이름 `pk_x` · Z3 null · Z4 PK `[A]` 이름 `pk_x`(C 는 덮임) · Z5 null · Y(MySQL 머리) PK `[A]` · 컬럼은 다 `[A, B]`(Z4 는 `[C, A, B]`) · notes 없음 · unreadable 0
  - 새 `typedOptionFkAndCheck` — `CREATE TYPE ty AS (id int, pid int, qty int); CREATE TABLE p (id INT PRIMARY KEY); CREATE TABLE t OF ty (id WITH OPTIONS PRIMARY KEY, pid WITH OPTIONS REFERENCES p(id), qty WITH OPTIONS CHECK (qty > 0), CONSTRAINT ck2 CHECK (id > 0));` → t 컬럼 `[id, pid, qty]` · PK `[id]` · fks 1(`pid` → `p(id)`) · checks 2(이름 null `qty > 0` · `ck2` `id > 0`) · unreadable 0 · 음성 `CREATE TABLE u OF ty (qty WITH OPTIONS DEFAULT 0)` → fks 0 · checks 0
- 골든 diff 0(표본에 괄호 안 LIKE·typed `WITH OPTIONS REFERENCES|CHECK` 없음 — 설계 54 실측) · `ddl-summary`·`ddl-unreadable`·`sqltables-diff`(30)·`logical-summary` 불변은 V-51a

### 1-73 — 프로필 고르기 nowrap(§3-6)
- `git cherry-pick cc64dbf` → `common.js:67` 에 `'#tb-profile{white-space:nowrap;flex-shrink:0}' +` 한 줄(그 커밋의 코드 변화는 이것뿐 — 실측). 충돌·다른 파일 변화 없어야 한다
- TDD 예외(화면 JS) — RED→GREEN 자리는 `SmokeHtmlUnitTest.profilePickerOnProfileScreens`·`ToolsFolderTest`·`BackendToolsTest`(java 레인) 초록 + 집 검증 `node scripts/puppeteer/visual.js nowrap --only dev_tools,code_check`(375px 샷에서 「프로필」 한 줄, 콘솔 0 — 이력에 한 줄)
- 끝에 `git branch -D wip/profile-nowrap`(사용자 (가) 2026-11-03 — cherry-pick 뒤 `git diff wip/profile-nowrap -- src/main/resources/tools/common.js` 가 빈 것을 보고)

### 측정·판정(§3-7)
- V-51a 재채점 — `bash scripts/mvn.sh -q test -Djunit.timeout=10m -Dtest='GeneratorCorpusTest,GeneratorJavacTest,DdlCorpusTest,SqlFixtureCorpusTest,AnalyzeCorpusTest,CliCorpusTest,LogicalCorpusTest'` → A 0 · B 갱신(`-Dgolden.update=true`)은 `corpus/gen-hr.json`(HR 날짜 컬럼이 `Date` + 어노테이션 — checkSoft 규칙 이름이 바뀌면)만 → diff 를 이력에 · `GeneratorJavacTest` 소스 수 40 · H2 건수·목록 그대로
- R-20 손 판정 — ① eGov oracle 181 표 재집계(jshell `GenModel.of`, R-17·R-18 과 같은 셈 — 본문 안 옮김): `insertUsers` 139 → 269 안팎(creator 135 + updater 134) · INSERT 의 `now` 식 수(created 135 + updated 134 안팎) · `@DateTimeFormat` 붙는 필드 수(= DATE 류 컬럼 수) ② `gen-hr.json` 변화 규칙 이름 ③ `sqltables-diff`·`ddl-summary`·`logical-summary` 변화 0 확인. 결함 둘 이하 고침(음성 먼저), 넘으면 후보로
- V-51 — `design/compare-2026-10-28.md` 에 `## ㉑ 재측정 — 번들 71(날짜)`: 표 「역할·꼴 전후(⑲ 대비)」(insertUsers·now·DateTimeFormat·egov35 Date) · javac 소스 수 · 벽시계 · 「드러난 것」 · 「후보로」(⑳ 잔여 + Detail.jsp 날짜 표시 + 현장 MyBatis 판). 그 앞에 `bash scripts/mvn.sh -DskipTests verify` rc 0

## 4. 실행 스텝
### S0 — 설계 문서·행·번들 표 (**이 설계 세션이 한다**, Opus 는 건너뛴다)
- 대상: `design/55-bundle71.md`(A 절) · `PLAN.md` 「번들」 표 「번들 71」 행 + 분할표(7-22·7-23·1-72 행 고침 + `7-24`·`7-25`·`1-73`·`V-51a`·`R-20`·`V-51` 새 행, B 절) · `PROGRESS.md`(C 절).
- 검증: `bash scripts/doc-lint.sh design/55-bundle71.md` · `bash scripts/verify.sh`(docs 레인) · `git commit -m "docs: S0 설계 55 — 번들 71"`. push 안 함.

### 7-22 — `userSession` 검증 + 컨트롤러 가지·textarea 렌더 시험
- 대상: `core/profile/Profile.java`(Audit) · `ProfileStoreTest` · `GenTemplatesTest`(§3-1).
- 순서: `ProfileStoreTest.userSessionShapeRefused` RED → Audit 검증 GREEN → `GenTemplatesTest` 둘(렌더 시험은 지금 코드로 바로 GREEN 이어야 한다 — RED 면 동작 결함이니 중단 조건).
- 검증: `bash scripts/mvn.sh -q test -Dtest='ProfileStoreTest,GenTemplatesTest,GenModelTest'` 초록 · 골든 diff 0 · `verify.sh` 초록 · 커밋 `feat: 7-22 userSession 값 검증 + 컨트롤러 가지·textarea 렌더 시험`.
- 의존: S0.

### 7-23 — 등록 INSERT 에 NULL 허용 수정자
- 대상: `core/gen/FieldRoles.java`(`sqlCols`·`K_NULLABLE`) · `GenModelTest` · 골든(§3-2).
- 순서: `rolesAndInputKinds` 단언 둘 바꿔 RED → `sqlCols` GREEN → `-Dgolden.update=true -Dtest='GenModelTest,GenTemplatesTest'` 한 번 → diff 읽기(model-bbs · bbs 매퍼·Controller · mapper-* 다섯 — 그 밖 바뀌면 중단).
- 검증: 위 시험 초록 · `git diff --stat src/test/resources/golden` 이 목록대로 · `verify.sh` 초록 · 커밋 `feat: 7-23 등록 INSERT 에 NULL 허용 수정자 둘`.
- 의존: 7-22.

### 7-24 — `@DateTimeFormat(iso = …)`
- 대상: `core/gen/FieldRoles.java`(`dates`) · `core/gen/GenModel.java`(`:165` 한 줄) · `GenModelTest` · 골든(§3-3).
- 순서: `dateBinding` RED → `dates` + 한 줄 교체 GREEN → 골든 갱신 → diff 읽기(model 둘 · VO 넷 — 그 밖 바뀌면 중단).
- 검증: `-Dtest='GenModelTest,GenTemplatesTest,SelfCheckTest'` 초록(`self-check.txt` 불변) · `verify.sh` 초록 · 커밋 `feat: 7-24 날짜 바인딩 @DateTimeFormat`.
- 의존: 7-23.

### 7-25 — egov35 `java.util.Date`
- 대상: `templates/gen/{egov35,egov4,egov5}/set.yaml` · `core/gen/FieldRoles.java`(`dates` 셋째 인자) · `core/gen/GenModel.java`(한 줄) · `GenModelTest` · 골든(§3-4).
- 순서: `legacyDates` RED → 구현 GREEN → `set.yaml` 셋 → `-Dgolden.update=true -Dtest='GenTemplatesTest'` → diff 읽기(`egov35/EmpHistVO.java`·`bbs/ComtnbbsVO.java` 둘만 — egov4·egov5 VO 가 바뀌면 vars 상속 누락, 중단).
- 검증: `-Dtest='GenModelTest,GenTemplatesTest,SelfCheckTest'` 초록 · `verify.sh` 초록 · 커밋 `feat: 7-25 egov35 세트 날짜는 java.util.Date`.
- 의존: 7-24.

### 1-72 — `DdlPull` LIKE PK 규칙 + typed 몸 FK·CHECK
- 대상: `core/gen/DdlPull.java` · `core/gen/DdlReader.java`(가시성 셋) · `DdlReaderTest`(§3-5).
- 순서: 시험 둘 RED → `likePk`·`copy`·`typedOption` GREEN.
- 검증: `-Dtest='DdlReaderTest,InsertGenTest,SelfCheckTest'` 초록 · 골든 diff 0 · `verify.sh` 초록 · 커밋 `fix: 1-72 DdlPull PG LIKE PK 는 INCLUDING INDEXES|ALL 만 + typed 몸 FK·CHECK`.
- 의존: S0(7-25 와 독립 — 차례는 뒤).

### 1-73 — 프로필 고르기 nowrap
- 대상: `src/main/resources/tools/common.js`(cherry-pick `cc64dbf`)(§3-6).
- 순서: `git cherry-pick -n cc64dbf`(커밋 안 함 — 훅 순서를 지키려고) → `git diff --stat` 이 `common.js` 한 줄뿐인지 → `bash scripts/verify.sh` → `git commit -m "fix: 1-73 프로필 고르기 줄바꿈 — nowrap·flex-shrink 0"`(따로) → 집 검증 Puppeteer(`target/app.jar` 는 `mvn.sh -q package -DskipTests` 뒤) → `git branch -D wip/profile-nowrap`.
- 검증: `SmokeHtmlUnitTest`·`ToolsFolderTest`·`BackendToolsTest` 초록 · `out/visual/nowrap/` 375px dev_tools 샷에서 「프로필」 한 줄(이력에 한 줄) · 가지 삭제 뒤 `git branch --list 'wip/*'` 에 없음.
- 의존: S0.

### V-51a — 재채점(corpus)
- 대상: `corpus/gen-hr.json`(조건부)(§3-7).
- 검증: A 0 · B 갱신은 `gen-hr.json` 만 · `sqltables-diff`(30)·`ddl-*`·`logical-summary`·`analyze-*` diff 0 · javac 소스 40 · 커밋 `test: V-51a 재채점 — gen-hr` (변화 없으면 커밋 없음, 이력엔 한 줄).
- 의존: 7-25 · 1-72 · 1-73.

### R-20 — 손 판정
- 대상: `design/compare-2026-10-28.md` ㉑ 「손 판정」(§3-7).
- 검증: 표 + 분류 합 · `doc-lint` · 커밋 `docs: R-20 손 판정 — eGov 역할·꼴 재집계`.
- 의존: V-51a.

### V-51 — compare ㉑ · 정적 분석 · `--corpus` · `--db`
- 대상: `design/compare-2026-10-28.md` ㉑(§3-7).
- 순서(따로 낸다): `bash scripts/mvn.sh -DskipTests verify` rc 0 → `bash scripts/verify.sh` → `bash scripts/verify.sh --corpus` → `wsl --shutdown` · Docker Desktop → 여유 RAM ≥ 15GB 확인(모자라면 사용자에게 앱 내리기 요청) → `bash scripts/verify.sh --db` 혼자.
- 검증: 넷 초록 · 끝 줄이 더 부르는 레인 없음 · 벽시계 둘 · 커밋 `docs: V-51 재측정 — compare ㉑ + 정적 분석 + --corpus + --db`.
- 의존: R-20.

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7 · 행의 축·강제 지점과 diff · `userSession` 정규식이 `"`·`\`·끝 점·숫자 머리를 거절하고 `a.b.c`·`$`·빈 글을 받나 · 컨트롤러 가지 시험이 EGOV 넷 + 세션 글로 `hasUser` 를 살리나 · INSERT 에 updated·updater 둘 다 들고 `now` 식이 INSERT 에도 나오나 · 재정의 `fixed` 는 그대로 `user` 만 끄나 · `@DateTimeFormat` 이 java.time 셋에만 붙고 String 감사 시각엔 안 붙나 · legacy 가 html·query·now 를 안 바꾸나 · egov4·egov5 가 `javaTime: "true"` 를 명시했나 · `GenModel.of` 줄 수·복잡도 불변 · `likePk` 가 마지막 절을 따르나 · `copy` 가 뒤 PK 를 이기게 하나 · typed FK·CHECK 가 컬럼 가지와 같은 도우미인가 · `self-check.txt`·PMD 짝 diff 0 · 골든이 의도된 변화인가(mapper-* 다섯은 bbs 기반)) → `bash scripts/gate-probe.sh` → `bash scripts/mvn.sh -DskipTests verify` → `bash scripts/verify.sh`(리뷰 고침 뒤 `--corpus`·`--db` 는 바뀐 레인만 다시) → `git push -u origin work/2026-11-03` → `gh pr create --base main`(행마다 시작 세 줄 표 + 로컬 닫기·결정 셋·S0 같이 + ㉑ 핵심 수치 + 「사람 결정」 (나) 넷) → CI 폴링(`gh api … check-runs`) → claude[bot] 처분 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → 닫기 커밋은 다음.

## 5. 금지 사항
- `pure/`·portfolio·`docs/`·`core/meta`·`core/dialect`·`core/profile`(Audit 검증 밖)·`JdbcMetaSource`·`tools/*.html`·`pom.xml`·`scripts/verify-fingerprint.sh`·`corpus/MANIFEST`·표본 수정 금지 · 새 Maven 의존성 금지 · 새 화면·라우트·프로필 키 금지
- 템플릿 본문(`VO`·`Controller`·`Mapper`·`List`·`Regist`·`Updt`·`Detail`·`Service`·`ServiceImpl`·`DAO`·`header`·`input`) 수정 금지 — 이 번들은 `set.yaml` 과 모델만 · `type-mapping.yaml`·`TypeMapping`·`DtoGenerator`·`Validation` 수정 금지 · `GenModel.Options` 인자 7 · `Generator.run` 인자 12 · `GenModel.of` 줄 수·분기 수 불변(`self-check.txt` 변화 금지 — `of(4)` 34·107 · `DdlReader` 세 줄) · `FieldRoles.apply` 인자 7 유지(날짜 치환은 `dates` 에서) · 새 메서드 복잡도 ≤ 15 · PMD 새 짝 금지(`FieldRoles`·`DdlPull`·`DdlReaderTest`·`GenTemplatesTest`·`GenModelTest`·`ProfileStoreTest` 는 기준선에 없다 — 되풀이 리터럴은 상수, 안 쓰는 상수는 지운다) · SpotBugs 새 지적 금지
- `@DateTimeFormat` 을 String 감사 시각(`nowExpr` 문자형)·검색 From/To(String)에 붙이지 않는다 · `java.util.Date` 를 egov4·egov5·DTO 카드에 내지 않는다 · 세트 변수 기본은 java.time(키 없음 = `"true"`) · `@InitBinder`·`PropertyEditor` 금지 · `request.getSession()`(인자 없음) 금지
- `DdlPull` — MySQL 머리 `LIKE`·`PARTITION OF` 의 PK 복사 규칙 변경 금지 · LIKE 로 유니크·인덱스·FK·CHECK 복사 금지(PK·컬럼만) · typed `WITH OPTIONS DEFAULT` 값 읽기 금지 · `DdlReader.checks`·`fk`·`COL_REF` 본문 수정 금지(가시성만)
- `common.js` 는 cherry-pick 한 줄 밖 수정 금지 · `common.css` 수정 금지
- 골든 `gen/egov35|egov4|egov5/*`(VO 밖)·`gen/*-egovvo.java`·`ddl-*.sql`·`alter-*.sql`·`insert/*`·`meta/*`·`ddl-summary`·`ddl-unreadable`·`sqltables-diff`·`logical-summary`·`hr-lint`·`pg-lint`·`ddl-lint*`·`analyze-*`·`js-*` 불변 · `DdlCorpusTest`·`GeneratorCorpusTest` 판정 로직 수정 금지
- 골든·compare·이력에 표본 코드 본문·생성물 본문을 싣지 않는다(건수·이름·꼴만) · 원본 되쓰기 API·CLI 금지 · `pmd-baseline.sh` 안 돌린다 · `--db` 와 다른 JVM 겹치기 금지 · `--corpus` → Docker → `--db` 순서 · `--db` 앞 여유 RAM 15GB

## 6. 최종 검증
- `bash scripts/mvn.sh -DskipTests verify` rc 0 · `bash scripts/verify.sh` · `--corpus` · `--db` 초록(순서대로, 따로) · `gate-probe.sh` 10/10
- 골든: `gen/model.json`·`gen/model-bbs.json`(7-23·7-24) · `gen/bbs/{ComtnbbsVO.java,ComtnbbsController.java,Comtnbbs_SQL_oracle.xml}` · `gen/mapper-*.xml` 다섯 · `gen/egov35/EmpHistVO.java`(Date) · `gen/egov4|egov5/EmpHistVO.java`(iso) · (조건부) `corpus/gen-hr.json` 갱신 · 나머지 불변 · `self-check.txt` 불변
- 시험: `ProfileStoreTest` +1 · `GenTemplatesTest` +2(+ 4인자 `render`) · `GenModelTest` +2(`dateBinding`·`legacyDates`) + `rolesAndInputKinds` 단언 둘 · `DdlReaderTest` +2 · `GeneratorJavacTest` 소스 40·javac 0 · `GeneratorCorpusTest` A 0 · `SmokeHtmlUnitTest`·`ToolsFolderTest` 초록 · `ArchitectureTest`·`VerifyLanesTest` 초록
- 수동: 데모 스냅샷 eGov 꼴 표 하나 생성(egov35) → `*VO.java` 날짜 필드 `@DateTimeFormat(pattern = …) private Date` · `*_SQL_oracle.xml` INSERT 에 `LAST_UPDT_PNTTM`·`LAST_UPDUSR_ID` · `*Controller.java` 등록에 `setLastUpdusrId(loginId(request))` · 프로필 `userSession: 'loginVO.'` 로 바꿔 화면에서 프로필 고르면 400 글에 「userSession」 · dev_tools 375px 「프로필」 한 줄(Puppeteer) — 눈으로 한 번, 이력에 한 줄
- compare ㉑ 표 + R-20 표 + 「드러난 것」 + 「후보로」 · 이력에 건수·벽시계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- `self-check.txt` 변화 · 7-22 렌더 시험이 지금 코드로 RED(동작 결함) · 7-23 골든 변화가 목록(model-bbs · bbs 셋 · mapper-* 다섯) 밖(특히 `gen/egov*`·`gen/model.json`) · 7-24 변화가 모델 둘·VO 넷 밖 · 7-25 에서 egov4·egov5 VO 가 바뀜(vars 상속) · `GeneratorJavacTest` 가 `DateTimeFormat`·`Date` 에서 javac 오류 — 고침 둘로 안 닫힘
- 1-72 골든 변화(`ddl-*`·`sqltables-diff`·`logical-summary`) · 1-73 cherry-pick 충돌 또는 common.js 밖 변화
- `pmd:check`·SpotBugs 새 짝 · `ArchitectureTest`·`VerifyLanesTest` 빨강
- `verify.sh --db` 메모리로 끊김 — 고아 JVM 끄고 한 번 더, 또 끊기면 보고
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① `gen-hr.json` checkSoft 변화(HR egov35 VO 의 `Date`) ② eGov 181 표 재집계 수(269 안팎) ③ `--corpus`·`--db` 벽시계. 첫 스텝 전: `bash scripts/verify.sh` 초록 · `git log --oneline -1`(S0 커밋) · `ls C:/workspace/toolbox-corpus/egov35-lib | wc -l`(35) · `git branch --list wip/profile-nowrap`(있음) · Docker Desktop 상태 · node 있음
- **사용자만 정할 수 있는 것** — 없음(Java 쪽 기준 · 7-23 (나) · 1-72 (나) · egov35 Date (나) · wip 가지 (가) · 머지 위임 전부 2026-11-02~03 결정)

