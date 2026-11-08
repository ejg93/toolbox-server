# 설계 57 — 번들 73: 생성기·DDL 읽기 정확도(가) + 구조 점검 다듬기(나) (Fable 설계 · Opus 실행, 2026-11-08)

## 맥락
번들 72 마무리 리뷰(2026-11-08) Minor 넷 + 판단 보류 하나(1-77)와 compare ㉑·㉒ 「후보로」 가운데 생성기·DDL 읽기·구조 점검 쪽 결함. 사용자 결정(2026-11-08): (가) 생성기·DDL 읽기 정확도 + (나) 구조 점검 다듬기를 번들 하나로 · **(다) a11y `<form:*>` 는 뺀다** — 5-17 에서 일부러 안 보기로 한 것(스프링 form 태그는 id 를 자동으로 만들어 label 과 잇는다 · 표본 egov 188 파일에서 title 없음 258 이 소음), 규칙 정규식은 순수본과 같아 바꾸면 pure-rules.json·portfolio 재주입(5-31 과 같은 처지), 지금 규칙 논리(id 있으면 통과)로는 보게 해도 잡히는 것 0 · 머지 위임 (가).

실측(2026-11-08, 코드 읽기):
- **따옴표 글 안 낱말**(1-77 ⑤) — 컬럼 가지 `DdlReader.item`(`core/gen/DdlReader.java:359-395`)은 `INLINE_PK`(`:360`)·`NOT NULL`(`:362`)·`DEFAULT`(`:363`)·`COL_REF`(`:391`)·`checks`(`:395`)를 **원문** `rest` 에서 찾고 `INLINE_UQ`(`:388`)만 따옴표 비운 사본. typed 가지 `DdlPull.typedOption`(`core/gen/DdlPull.java:169-197`)은 PK·NOT NULL·UNIQUE 를 사본 `bare`, `COL_REF`·`checks` 를 원문. 그래서 `DEFAULT 'PRIMARY KEY'` 가 컬럼 가지에서 PK, `DEFAULT 'REFERENCES p(id)'` 가 두 가지에서 FK, `DEFAULT 'CHECK (1)'` 가 두 가지에서 CHECK 가 된다. `DdlReader.checks`(`:516`)는 `CHECK_AT` 를 원문에서 찾고 `matching`(`:611`, 따옴표를 안다)으로 닫는다 — 찾는 자리가 글 안이면 닫기도 틀린다. `DdlPull.typedOption` javadoc(`:170-171`)·`DdlReaderTest:573` 주석의 「컬럼 가지와 같은 규칙」 은 틀린 글(1-77 ①). `QUOTED = '[^']*'`(`:43`) — `''` 는 빈 글 둘로 읽혀 토글이 맞는다. `DdlReader` 는 메서드 수 상한(`metric.classMethods` 30)에 가까워 `DdlPull` 로 나뉘었다(`DdlPull.java:17`) — 새 도우미는 `DdlPull` 에
- **typed `WITH OPTIONS DEFAULT`** — `TYPED_WORDS`(`DdlPull:37`)에 DEFAULT 가 있어 줄은 지나지만 `typedOption` 에 DEFAULT 처리가 없다 → 컬럼 `defaultValue` 는 CREATE TYPE 속성 값(보통 null). PK 줄도 `nullable` 을 안 바꾼다(컬럼 가지 `:362` 는 `notNull || inlinePk`). `DdlReaderTest.typedTableFromType:495` 의 `salary WITH OPTIONS DEFAULT 1000` 은 defaultValue 를 단언 안 함
- **CTAS 조인** — `DdlPull.ctasColumns`(`:199-229`) → `fromTable`(`:245-257`)이 `FROM 표 [별칭]` 하나만. JOIN 은 `ALIAS` 가 `JOIN` 을 별칭으로 먹고 뒤가 남아 null, 쉼표 조인은 `FROM_TABLE` 뒤가 `, T0` 라 null → notes `ctas`. `selected`(`:260-275`)는 `CTAS_ITEM` 한정자를 버리고 원본 한 표에서 찾는다. `t.*` 는 `ITEM_BAD` 의 `*` 에 걸린다. 식·함수는 「추측하지 않는다」(설계 46·55 결정 — 그대로). `DdlReaderTest.ctasFromSource:581-601` T6 `JOIN T9 ON 1 = 1`(T9 가 DDL 에 없다) → notes. DDL 표본에 CTAS 없음(1-66) → 골든 불변 기대
- **오류 세트**(1-77 ②) — `GET /api/generate/templates`(`web/GenerateRoutes.java:54-69`)는 `TemplateSet.load` IAE 를 `error` 칸에 담고 화면 `spring_source_generator_ext.js:47-55` 는 `이름 (오류)` 글만 붙인다(`option()` `:27-32` 는 value·textContent 만, 반환 없음). `disabled`·`title` 없음 → 고르고 「생성」 을 누르면 `postGenerate:139` 의 `TemplateSet.load` IAE 가 핸들러 없이 Javalin 500(`App.java:201-208` `jsonErrors` 는 `ProfileUnreadable`·Jackson 둘뿐). `postGenerate:81` 은 `setName == null || !TemplateSet.list(genDir).contains(setName)` 로 폴더만 본다. `self-check.txt:125` `GenerateRoutes.java|metric.complexity|복잡도 48 > 15 · postGenerate(7)` — 분기 수를 바꾸면 기준선 글이 바뀐다
- **세트 변수 빈 값**(1-77 ③) — `TemplateSet.load:84` `e.getValue().asText()` — `javaTime:`(빈 값)·`~` 는 NullNode 라 `"null"` 글 → `checkBools`(`:106-113`) 거절 글이 「… 만: null」. 다른 변수(`rte:` 빈 값)도 템플릿에 `null` 글로 들어간다
- **Detail·List 날짜** — `templates/gen/egov35/Detail.jsp.ftl:17`·`List.jsp.ftl:51·53` 이 모든 필드를 `<c:out value="${result.[=f.name]}"/>` — egov35 세트(`javaTime: "false"`)의 `java.util.Date` 는 `toString` 「Mon Jan 01 10:00:00 KST 2024」. egov4·egov5 는 `LocalDateTime` ISO 「2024-01-01T10:00」(`<fmt:formatDate>` 는 `java.util.Date` 만 받는다 — java.time 은 못 쓴다). `fmt` taglib 선언 없음(Detail 은 `c` 만, List 는 `c`·`form`·`ui`). 날짜 판정 자료 — `FieldRoles.dates`(`:192-216`)가 `legacy` 면 `f.javaType` 을 `"Date"` 로(`:204`), `f.html` 은 `date`·`datetime`·`time`(`:41-43`), `PATTERN`(바인딩용 `yyyy-MM-dd'T'HH:mm`)은 표시용이 아니다. 골든 `golden/gen/egov35/EmpHistDetail.jsp:20`·`EmpHistList.jsp:46` 에 `startDate`(egov35 VO `Date` + `@DateTimeFormat(pattern)`), egov4·egov5 는 `LocalDateTime`. `golden/gen` 을 쓰는 시험은 `GenTemplatesTest`. `templates/gen` 은 java 레인만
- **visual.js ⑥**(1-77 ④) — `scripts/puppeteer/visual.js:324-325` `limit` 이 `parseFloat(getComputedStyle(b).marginLeft)` — header 있는 화면은 `common.js profilePicker` 가 `marginLeft='auto'` 라 NaN → 항상 실패. 지금은 dev_tools 만 재서 영향 0
- **구조 점검**(`core/deliverable`, corpus 레인·db 레인 아님 — `verify-fingerprint.sh:18·74`) — `StructureLint.differ`(`:426-442`)는 `norm`(`:449`, 대문자·괄호 뗌·공백 하나)한 글을 **그대로 비교** → `SERIAL ↔ INTEGER`(PG 의 흔한 FK 꼴)·`INT ↔ INTEGER`·`VARCHAR ↔ CHARACTER VARYING` 이 「형식 다름」. 동의어 표 없음(`TypeMapping` 군은 TEXT·CLOB 까지 String 이라 너무 거칠다). 표본 소음 0(eGov 는 FK 컬럼 형식을 부모와 똑같이 둔다). `StructureLintColumns`(`:16-22`) `LOB_TYPES` 에 `LONG VARCHAR`(goldilocks)·`STRING`(cubrid)·`LONG`(Oracle·Tibero·MySQL=MEDIUMTEXT) 없음 — egov 세 표(`COMTNONLINEMANUAL`·`COMTNSTPLATINFO`·`COMTNWIKMNTHNGREPRT`)가 oracle 에선 CLOB 둘로 잡히고 cubrid `STRING`·goldilocks `LONG VARCHAR` 로는 0(`golden/corpus/ddl-lint.json:10-11`, 표본 `com_DDL_cubrid.sql:973·1087·2505` · `com_DDL_goldilocks.sql:969·1084·2456`). `uniqueNullable` detail(`StructureLintColumns:38`) `"유니크 " + k.name() + "("` — 이름 없는 유니크(`keyed:309` `nz` → `""`)는 「유니크 (dict_type)」(`ddl-lint-findings.txt:29`). `StructureLint` 는 메서드 수 상한 30 가까움(`:340` 주석) — 도우미는 `StructureLintColumns` 에. `DdlCorpusTest.FINDING_GROUPS`(`:54`)는 egov:oracle·db-samples:oracle·ruoyi:mariadb 셋뿐 — cubrid·goldilocks 건은 `ddl-lint.json` 글에만 든다. `self-check.txt:73` `StructureLint.java|metric.complexity|복잡도 50 > 15 · run(3)`
- 레인: `core/gen`·`web`·`templates/gen` 은 java(`core/gen` 은 db·corpus 도) · `tools/*.js`·`scripts/puppeteer`·`core/deliverable` 은 corpus · `web`·`templates/gen` 은 java 만. corpus 시험을 이름으로: `bash scripts/mvn.sh -q test -Dtest='DdlCorpusTest,HrSnapshotCorpusTest'`(번들 69 전례). 골든 갱신은 `-Dgolden.update=true`(`GoldenFiles:18`)

**범위 밖(사용자 2026-11-08)**: a11y `<form:*>`(5-17 결정 유지 — compare ㉓ 에서 후보 목록에서 뺀다) · 5-31 · `--db` CI 이전 · `README-SITE.txt` 저장소 반입(안 한다 — 회사 정보) · 0-75(`bundle/SOURCES.md`, release 지문 — 다음 반입 앞) · 개발자 도구 새 탭 · CTAS 의 식·함수 컬럼(형식을 모른다 — 추측 안 함) · java.time 날짜 표시 꼴(ISO 그대로 — JSTL 이 못 받고 VO getter 는 7-24 와 겹친다) · `dupIndex`·`fkTypes` detail 의 빈 이름(골든 영향 — 후보로) · `INLINE_COMMENT` 가 글 안 `COMMENT '…'` 를 먼저 잡는 경우(후보로) · List.jsp `<c:param>` 의 Date PK 바인딩(후보로) · 보류·조건부 행.

## 사용자 결정(2026-11-08)
- (가)+(나) 번들 하나·PR 하나 · (다) 뺀다 · 머지 위임 (가) · 다시 묻지 않는다
- 설계가 정한 것(기본값이 뚜렷해 안 물음): 따옴표 글은 **같은 길이 마스크**(`DdlPull.mask` — 글 안을 공백으로, 따옴표 글자는 둔다)에서 찾고 값(DEFAULT 리터럴·CHECK 식)은 원문 자리에서 자른다 — 두 가지에 같은 규칙 · typed DEFAULT 는 원문 리터럴, PK 는 NOT NULL · CTAS 는 ANSI JOIN·쉼표 조인까지, 항목은 한정 이름·유일한 맨 이름·`*`·`t.*`, 식·함수·원본 없음·모호한 이름은 지금처럼 빈 목록 + notes · 오류 세트는 옵션 `disabled` + `title`=오류 글, `POST /api/generate` 는 세트 읽기 IAE → 400(분기 수 불변) · 빈 값은 `""`, 거절 글은 「(빈 값)」 · 날짜 표시는 `java.util.Date` 세트만 `<fmt:formatDate>`(패턴은 `FieldRoles` 가 `datePattern` 으로 — date `yyyy-MM-dd` · datetime `yyyy-MM-dd HH:mm` · time `HH:mm`), `fmt` taglib 은 날짜 필드가 있을 때만 · fkTypeMismatch 는 비교만 동의어(`canon`), 글은 원문 꼴 · LOB 셋 추가 · 이름 없는 유니크 detail 「유니크(컬럼)」

실행자는 Opus. 차례 `S0` → `1-78` → `1-79` → `1-80` → `7-27` → `7-28` → `7-29` → `2-36` → `2-37` → `0-76` → `V-53` → 마무리. 가지 `work/2026-11-08`(로컬 닫기 `62d9245`·`40f8d75` 같이). 번들 하나·PR 하나. 머지 위임 (가).

---

## 1. 목표와 범위
- 한 문장: DDL 의 따옴표 글 안 낱말이 제약이 되지 않고, typed 표가 DEFAULT·PK 를 제대로 받고, CTAS 가 조인도 읽으며, 오류 세트는 고를 수 없고 고르면 400 이고, egov35 생성 화면의 날짜가 사람 꼴이며, 구조 점검이 동의어 형식을 같은 것으로 보고 LOB 셋을 세고 이름 없는 유니크 글이 깨끗하다.
- 완료 시 체감: `a VARCHAR(20) DEFAULT 'PRIMARY KEY'` 가 PK 가 아니고 `DEFAULT 'CHECK (1)'` 이 CHECK 가 아니다 · `salary WITH OPTIONS DEFAULT 1000` → defaultValue `1000` · `CREATE TABLE T7 AS SELECT t.A, s.D AS DD FROM T1 t LEFT JOIN T0 s ON t.A = s.K` → 컬럼 `[A, DD]` · 소스 생성 화면에서 `egov35 (오류)` 는 회색(고를 수 없고 hover 에 사유) · `POST /api/generate` 에 깨진 세트 → 400 「egov35/set.yaml 의 vars.javaTime 는 "true"|"false" 만: flase」 · `javaTime:` 빈 값 → 「… 만: (빈 값)」 · egov35 EmpHistDetail.jsp 의 `startDate` 가 `<fmt:formatDate … pattern="yyyy-MM-dd HH:mm"/>` · `SERIAL` 부모 ↔ `INTEGER` 자식 FK 가 fkTypeMismatch 아님 · egov cubrid·goldilocks `tooManyLobs 3` · 「유니크(dict_type)」 · compare ㉓.
- In scope: `core/gen/DdlPull.java`(`mask`·`typedBody`·`typedOption`·`restate`·`ctasColumns`·`fromSources`·`source`·`selected`·`resolve`) · `core/gen/DdlReader.java`(`item` 매처 입력·`checks`·`DEFAULT` 가시성) · `core/gen/TemplateSet.java`(`text`·`checkBools` 글) · `core/gen/FieldRoles.java`(`SHOW`·`K_DATE_PATTERN`·`dates` 한 줄) · `web/GenerateRoutes.java`(`postGenerate` 세트 읽기 자리) · `tools/spring_source_generator_ext.js`(`loadSets` 두 줄) · `templates/gen/egov35/Detail.jsp.ftl`·`List.jsp.ftl` · `core/deliverable/StructureLintColumns.java`(`CANON`·`canon`·`LOB_TYPES`·`uniqueNullable` 글) · `core/deliverable/StructureLint.java`(`differ` 한 줄) · `scripts/puppeteer/visual.js`(⑥ 한 줄) · 시험 `DdlReaderTest`·`TemplatesTest`·`GenerateRoutesTest`·`StructureLintTest`·`GenTemplatesTest` 골든 · 골든 `golden/corpus/ddl-lint.json`(2줄)·`ddl-lint-findings.txt`(1줄)·`golden/gen/egov35/EmpHistDetail.jsp`·`EmpHistList.jsp` · `design/compare-2026-10-28.md` ㉓ · PLAN·PROGRESS.
- Out of scope: 「범위 밖」 전부 · `DdlReader.matching`·`QUOTED`·`INLINE_UQ` 논리 · `GenerateRoutes.getTemplates` · `App.jsonErrors` · `common.js` · egov4·egov5 세트·`input.ftl`·`Regist`·`Updt` 템플릿 · `TypeMapping`·`type-mapping.yaml` · `StructureLint.Rule` enum(새 규칙 없음 — 「열다섯 규칙」 글 그대로) · `dupIndex`·`fkTypes` detail 글 · 순수본·표본.

## 2. 현재 구조 요약
「맥락」 의 실측이 요약이다. 덧붙이는 함정:
- `matching(s, open)` 은 `open` 자리부터 따옴표 토글을 시작한다 — 시작 자리가 글 안이면 상태가 뒤집힌다. 그래서 `CHECK_AT` 를 마스크에서 찾아야 한다(마스크는 같은 길이라 자리를 그대로 원문에 쓴다)
- `DEFAULT` 패턴(`DdlReader:106`)의 첫 대안 `'(?:[^']++|'')*+'` 은 마스크 `'    '` 에도 맞는다(공백은 `[^']`). 마스크에서 `start(1)..end(1)` 를 원문에서 자르면 리터럴이 산다. `[^\s,]+` 대안(`now()`·숫자)도 마스크와 원문이 같다
- `INLINE_PK.group(1)`·`COL_REF.group(1)·(2)`·`CHECK_AT.group(1)` 은 이름(겹따옴표 또는 맨 글) — 홑따옴표 마스크가 안 건드려 마스크의 그룹 글이 원문과 같다
- `DdlReader.item` 복잡도 30·`read` 36 이 `self-check.txt:84-85` 기준선 — **분기를 더하지 않는다**(매처 입력만 바꾼다). `DdlReader` 메서드 수 상한 30 — `mask` 는 `DdlPull` 에. `DdlPull` 메서드는 지금 18, 새 메서드 넷(`mask`·`restate`·`fromSources`·`source`·`resolve`)을 더해도 상한 안. 새 메서드 복잡도 ≤ 15
- `DdlReader.DEFAULT` 는 `private`(`:106`, private 정적 묶음 안) — typed 가지가 쓰려면 package-private 로. **그 자리에서 가시성만 바꾸면 Checkstyle DeclarationOrder 가 빨강**(7-22 전례 — private 뒤의 package 필드) → 선언을 `COL_REF`(`:40`) 바로 아래 package-private 묶음으로 옮긴다(글은 그대로)
- `typedBody:161` 의 `TYPED_WORDS` 판정은 `QUOTED…replaceAll("''")` 사본 — `mask` 로 바꿔도 결과가 같다(낱말 유무만 본다). 한 곳으로 모은다
- `postGenerate` 복잡도 48 은 `if (A || B)`(분기 2)를 `if (A)` + `try/catch`(분기 2)로 바꿔야 그대로다. `:139` 의 `TemplateSet.load` 를 지우고 `:81` 자리에서 읽는다(세트 읽기는 YAML 한 번 — 순서가 바뀌어도 부작용 없음). `SelfCheckTest` 가 기준선 글을 그대로 맞댄다
- `TemplateSet.list(genDir)` 는 폴더 목록 — `load` 가 이름 꼴(`NAME`)·세트 없음·`extends`·`files` 를 전부 IAE 로 거절하므로 `contains` 검사는 `load` 에 들어 있다. `nope` 도 IAE(`refusals` 시험은 400 만 단언)
- `GenerateRoutesTest` 는 `@TempDir` 에 `templates/gen` 을 복사해 쓴다(`:50`). 깨진 세트를 거기 쓰면 `templatesListed`(세트 3 단언)가 순서에 따라 깨진다 — `finally` 로 지운다
- `TemplatesTest.write(rel, text)` 로 세트를 만든다. YAML `javaTime:`(값 없음)은 NullNode
- FreeMarker 는 꺾쇠 지시자(`<#list>`·`<#if>`) + 대괄호 보간(`[=f.name]`). `?filter(f -> …)` 는 2.3.29+(잠금 2.3.35)
- `fields` 의 각 항목은 Java `Map` — 없는 키 `f.datePattern??` 는 false. `K_HTML` 값은 `FieldRoles.apply` 가 항상 넣는다
- `GenTemplatesTest` 가 `golden/gen/<세트>/*` 를 쓴다 — egov35 둘만 바뀌어야 한다(egov4·egov5 는 `datePattern` 이 없다)
- `StructureLint.differ` 복잡도는 기준선 밖(≤15) — `canon` 호출을 비교식 안에 넣어 분기 수 불변. 글(`형식 A ≠ B`)은 `norm` 원문 그대로 → `StructureLintTest:203` 「형식 VARCHAR2 ≠ NUMBER」 불변
- `DdlCorpusTest.FINDING_GROUPS` 가 셋이라 `ddl-lint-findings.txt` 는 ruoyi 줄 하나만 바뀐다. `ddl-lint.json` 은 cubrid·goldilocks 두 줄의 `tooManyLobs 0 → 3`. **그 밖 골든 변화는 전부 중단 신호**
- `visual.js` 는 `--only dev_tools` 로 ⑥ 만 돈다. RED 가 안 선다(지금 dev_tools 는 NaN 이 아니다) — TDD 예외(scripts)
- 레인 — 1-78·1-79·1-80·7-28·7-29(FieldRoles) 가 db·corpus 둘 다 · 7-27(JS) ·0-76·2-36·2-37 이 corpus · 7-27(web)·7-29(템플릿) java. V-53 에서 한 번에

## 3. 설계 결정 — 전부 [고정]

### 1-78 — 따옴표 글 마스크 — 컬럼·typed 두 가지(§3-1)
- `DdlPull`:
```java
/** 1-78 — 홑따옴표 글 안을 공백으로 비운 같은 길이 사본. 자리가 원문과 같아 사본에서 찾고 원문에서 자른다({@code ''} 는 빈 글 둘 — matching 과 같은 토글) */
static String mask(String s) {
    char[] a = s.toCharArray();
    boolean q = false;
    for (int i = 0; i < a.length; i++) {
        if (a[i] == '\'') {
            q = !q;
        } else if (q) {
            a[i] = ' ';
        }
    }
    return new String(a);
}
```
- `DdlReader.item` — 분기 불변, 입력만:
```java
String m = DdlPull.mask(rest); // 1-78 — 글 안 낱말(DEFAULT 'PRIMARY KEY')을 제약으로 안 본다
String upper = m.toUpperCase(Locale.ROOT);
Matcher ip = INLINE_PK.matcher(m); // 1-70 — 이름 대소문자를 살리려고 소문자화 안 한 사본에
boolean inlinePk = ip.find();
boolean notNull = Pattern.compile("\\bNOT\\s+NULL\\b").matcher(upper).find() || inlinePk;
Matcher df = DEFAULT.matcher(m);
String def = df.find() ? rest.substring(df.start(1), df.end(1)) : null; // 값은 원문에서 — 리터럴 보존
```
  `:388` `INLINE_UQ.matcher(DQUOTED.matcher(upper).replaceAll("\"\"")).find()`(홑따옴표는 이미 마스크) · `:391` `COL_REF.matcher(m)` · `:395` `checks(d, rest)` 그대로(안에서 마스크). `INLINE_COMMENT`(`:365`)는 원문 그대로(범위 밖)
- `DdlReader.checks`:
```java
/** 표 단위 줄·컬럼 항목 안의 CHECK (…) — 1-78 마스크에서 찾고(글 안 CHECK 는 안 본다) 원문 자리에서 괄호 균형으로 자른다(1-31a) */
static void checks(Draft d, String text) {
    String m = DdlPull.mask(text);
    Matcher mt = CHECK_AT.matcher(m);
    int from = 0;
    while (from < text.length() && mt.find(from)) {
        int open = mt.end() - 1;
        int close = matching(text, open);
        … (그대로 — group(1) 은 mt 에서, substring 은 text 에서)
```
- `DdlReader.DEFAULT` → `static final`(package-private), 선언을 `COL_REF` 아래로 옮긴다(§2 DeclarationOrder)
- `DdlPull.typedBody:161` — `TYPED_WORDS.matcher(mask(o.group(2))).find()`
- `DdlPull.typedOption` — `bare` 를 `String m = mask(rest);` 로, `INLINE_PK`·`NOT_NULL`·`UNIQUE`·`COL_REF` 네 매처 전부 `m`, `checks(d, rest)` 그대로. javadoc:
```java
/**
 * 1-78 — 매처는 전부 따옴표 글을 비운 같은 길이 사본({@link #mask})에서, CHECK 식 본문(1-74)·DEFAULT 값(1-79)은 원문 자리에서 자른다
 * — 컬럼 가지 {@link DdlReader#item} 과 같은 규칙. 1-79 — DEFAULT 값을 받고 PK 는 NOT NULL.
 */
```
- `DdlReaderTest.typedOptionFkAndCheck:573` 주석 → `// 1-74·1-78 — CHECK 식의 따옴표 글은 원문 자리에서 자른다(컬럼 가지와 같은 마스크 규칙)`
- 새 시험 `DdlReaderTest.quotedWordsAreNotConstraints`:
  - 입력 `CREATE TABLE p (id INT PRIMARY KEY); CREATE TABLE q (id INT NOT NULL, a VARCHAR(20) DEFAULT 'PRIMARY KEY', b VARCHAR(20) DEFAULT 'NOT NULL', c VARCHAR(30) DEFAULT 'REFERENCES p(id)', d VARCHAR(20) DEFAULT 'CHECK (1)', e VARCHAR(9) DEFAULT 'x' CHECK (e <> ')'), PRIMARY KEY (id)); CREATE TYPE ty AS (x text, y text); CREATE TABLE t OF ty (x WITH OPTIONS DEFAULT 'REFERENCES p(id)' CHECK (x <> ')'), y WITH OPTIONS DEFAULT 'CHECK (1)');`
  - q: pk `[id]`(**RED** — 지금 a 도 PK) · `a`·`b` nullable true(**RED** — b 가 NOT NULL) · fks 0(**RED**) · checks `[Check(null, "e <> ')'")]`(**RED** — `1` 도 든다) · defaultValue a `'PRIMARY KEY'` · b `'NOT NULL'` · c `'REFERENCES p(id)'` · d `'CHECK (1)'` · e `'x'`(**음성** — 마스크가 값을 안 깬다)
  - t: fks 0(**RED**) · checks `[Check(null, "x <> ')'")]`(**RED**) · unreadable 0
- 골든 전부 diff 0 기대(표본에 글 안 제약 낱말 없음 — 바뀌면 중단). `self-check.txt` 불변(`item` 분기 수 그대로)

### 1-79 — typed `WITH OPTIONS DEFAULT` 값 + PK 는 NOT NULL(§3-2)
- `DdlPull.typedOption` 몸(1-78 뒤):
```java
String m = mask(rest);
Matcher ip = DdlReader.INLINE_PK.matcher(m);
boolean pk = ip.find();
if (pk) {
    d.pk.add(name);
    d.pkName = ip.group(1) == null ? d.pkName : DdlReader.unquote(ip.group(1));
}
Matcher df = DdlReader.DEFAULT.matcher(m);
String def = df.find() ? rest.substring(df.start(1), df.end(1)) : null;
restate(d, name, pk || NOT_NULL.matcher(m).find(), def);
if (UNIQUE.matcher(m).find()) { … }
Matcher ref = DdlReader.COL_REF.matcher(m); …
DdlReader.checks(d, rest);
```
```java
/** 1-79 — 이름이 같은 컬럼을 NOT NULL·DEFAULT 로 다시 놓는다. 둘 다 없으면 그대로 */
private static void restate(Draft d, String name, boolean notNull, String def) {
    if (!notNull && def == null) {
        return;
    }
    for (int i = 0; i < d.cols.size(); i++) {
        Column c = d.cols.get(i);
        if (c.name().equalsIgnoreCase(name)) {
            d.cols.set(i, new Column(c.name(), c.ordinal(), c.nativeType(), c.jdbcType(), c.length(), c.precision(), c.scale(),
                    c.nullable() && !notNull, def == null ? c.defaultValue() : def, c.comment(), c.domain()));
        }
    }
}
```
- 시험 — `typedTableFromType`: `assertEquals("1000", e.columns().get(1).defaultValue())`(**RED**) · `typedOptionFkAndCheck`: t 의 `id` nullable false(**RED** — PK) · u 의 `qty` defaultValue `0` · `note` `'PRIMARY KEY'`(**RED**) · 입력 `ty` 에 `memo text`, u 에 `memo WITH OPTIONS DEFAULT 'A''B'` → `'A''B'`(**RED**, 리터럴 보존)
- 골든 diff 0 기대(DDL 표본의 Oracle 객체 표는 `WITH OPTIONS` 가 없다 — 바뀌면 중단)

### 1-80 — CTAS 조인(ANSI·쉼표)·한정 항목·`t.*`(§3-3)
- `DdlPull` 패턴 둘 추가(수량자 단순 — REDOS 없음):
```java
/** 1-80 — FROM 의 원본 사이 — 쉼표 또는 [NATURAL] [INNER|LEFT|RIGHT|FULL|CROSS] [OUTER] JOIN */
private static final Pattern JOIN_SEP = Pattern.compile("(?i)\\s*,\\s*|\\s+(?:NATURAL\\s+)?(?:(?:INNER|LEFT|RIGHT|FULL|CROSS)\\s+)?(?:OUTER\\s+)?JOIN\\s+");
/** 원본 하나 — 표 [[AS] 별칭] [ON …|USING (…)] */
private static final Pattern SOURCE = Pattern.compile("(?i)^([^\\s,()]+)(?:\\s+(?:AS\\s+)?(?!ON\\b|USING\\b)([^\\s,()]+))?(?:\\s+(?:ON|USING)\\b.*)?$", Pattern.DOTALL);
/** CTAS_END 낱말 — FROM 뒤 어디서든 첫 것에서 자른다 */
private static final Pattern CTAS_END_ANY = Pattern.compile("(?i)\\b(?:WHERE|ORDER|GROUP|HAVING|LIMIT|FETCH|OFFSET)\\b");
```
- `fromTable(tail, tables)` → `fromSources(tail, tables)` 가 `Map<String, Draft>`(LinkedHashMap, 키 = 별칭 또는 표 이름 대문자) 또는 null:
```java
/** 1-80 — FROM 뒤 원본들. 서브쿼리가 있거나 원본 하나라도 DDL 에 없거나 꼴이 다르면 null(추측하지 않는다) */
private static Map<String, Draft> fromSources(String tail, Map<String, Draft> tables) {
    Matcher end = CTAS_END_ANY.matcher(tail);
    String list = (end.find() ? tail.substring(0, end.start()) : tail).trim();
    if (SELECT_ANY.matcher(tail).find() || list.isEmpty()) {
        return null;
    }
    Map<String, Draft> out = new LinkedHashMap<>();
    for (String piece : JOIN_SEP.split(list)) {
        if (!source(piece.trim(), tables, out)) {
            return null;
        }
    }
    return out;
}

/** 원본 하나 — 표 이름으로 Draft, 키는 별칭(없으면 표 이름). 없으면 false */
private static boolean source(String piece, Map<String, Draft> tables, Map<String, Draft> out) {
    Matcher s = SOURCE.matcher(piece);
    Draft d = s.matches() ? tables.get(key(s.group(1))) : null;
    if (d == null) {
        return false;
    }
    out.put((s.group(2) == null ? key(s.group(1)) : DdlReader.unquote(s.group(2)).toUpperCase(Locale.ROOT)), d);
    return true;
}
```
  `ctasColumns:222-223` → `Map<String, Draft> src = fromSources(…); List<Column> cols = src == null ? List.of() : selected(list, src);` · `fromTable` 과 그것만 쓰던 `FROM_TABLE`·`ALIAS`·`CTAS_END` 는 지운다(PMD UnusedPrivateField/Method 가 verify 에서 빨강)
- `selected(list, Map<String, Draft> srcs)`:
  - `*` → 모든 원본 컬럼을 차례로(새 List)
  - 항목마다: `CTAS_ITEM` 앞에 `t.*` 꼴(`^([^\s.]+)\.\*$`, 새 패턴 `STAR_OF`)이면 그 원본 컬럼 전부 · 아니면 `ITEM_BAD`·`CTAS_ITEM` 지금대로, 컬럼은 `resolve(srcs, 한정자(또는 null), 이름)`:
```java
/** 한정자가 있으면 그 원본에서, 없으면 모든 원본에서 꼭 하나일 때만. 아니면 null */
private static Column resolve(Map<String, Draft> srcs, String qualifier, String name) {
    if (qualifier != null) {
        Draft d = srcs.get(DdlReader.unquote(qualifier).toUpperCase(Locale.ROOT));
        return d == null ? null : column(d, name);
    }
    Column found = null;
    for (Draft d : srcs.values()) {
        Column c = column(d, name);
        if (c != null && found != null) {
            return null;
        }
        found = c == null ? found : c;
    }
    return found;
}
```
  `CTAS_ITEM` 은 그룹 하나 더(한정자) — `^(?:([^\s.]+)\.)?([^\s.]+)(?:\s+(?:AS\s+)?(\S+))?$`, 뒤 그룹 번호를 +1. 한정자가 원본에 없는 이름(`x.A` 에 x 없음)은 null → 빈 목록
- javadoc(`:200-201`) — 「FROM 표 [별칭] (, 표 [별칭] | [LEFT|…] JOIN 표 [별칭] ON …)*」 · 「항목은 `*`·`t.*`·한정 이름·유일한 맨 이름·별칭. 식·함수·서브쿼리·없는 원본·모호한 이름이면 빈 목록(추측하지 않는다)」. notes 글 `ctas`(`DdlReader:122`) 불변
- 시험 `ctasFromSource` 확장 — 입력에 `CREATE TABLE T0 (K INT, D VARCHAR(5), B INT);` + `CREATE TABLE T7 AS SELECT t.A, s.D AS DD, t.B FROM T1 t LEFT OUTER JOIN T0 s ON t.A = s.K WHERE t.A > 0; CREATE TABLE T8 AS SELECT A, K FROM T1, T0; CREATE TABLE T9 AS SELECT B FROM T1 t JOIN T0 s ON t.A = s.K; CREATE TABLE TA AS SELECT t.* FROM T1 t INNER JOIN T0 s ON t.A = s.K;`
  - T7 `[A, DD, B]`(**RED**), DD 형식 `VARCHAR`·notes 없음 · T8 `[A, K]`(**RED**, 쉼표 조인·맨 이름 유일) · T9 컬럼 0 + notes(B 가 둘 다에 있어 모호) · TA `[A, B]`(**RED**) · T6(`JOIN T9 ON 1 = 1` — 이름이 T9 인 CTAS 표가 이제 DDL 에 있다!) → **T6 의 조인 상대를 `T99` 로 바꾼다**(없는 표 → 컬럼 0 + notes 그대로) · T5 집계는 그대로 notes
- 골든 diff 0 기대(DDL 표본에 CTAS 없음)

### 7-27 — 오류 세트는 고를 수 없고 고르면 400(§3-4)
- `spring_source_generator_ext.js` `loadSets`:
```js
(list || []).forEach(function (s) {
  option(sel, s.name, s.error ? s.name + ' (오류)' : s.name);
  if (s.error) { sel.lastElementChild.disabled = true; sel.lastElementChild.title = s.error; } // 7-27 — 고를 수 없고 hover 에 사유
});
```
  `loadDefaults` 의 `$('set').value = g.templateSet` 은 그대로(프로필 기본이 깨진 세트면 회색으로 보인다 — 생성은 400)
- `GenerateRoutes.postGenerate:81-84` →
```java
if (setName == null) {
    ctx.status(400).json(Map.of("message", "템플릿 세트가 없다: null"));
    return;
}
TemplateSet set;
try {
    set = TemplateSet.load(genDir, setName); // 7-27 — 세트 없음·이름 꼴·extends·files·불리언 변수 전부 여기서 400
} catch (IllegalArgumentException e) {
    ctx.status(400).json(Map.of("message", e.getMessage()));
    return;
}
```
  `:139` `TemplateSet set = TemplateSet.load(genDir, setName);` 삭제. 분기 수 불변(`||` 하나 빠지고 `catch` 하나) → `self-check.txt:125` 「복잡도 48」 그대로
- `GenerateRoutesTest.brokenSetIs400`:
```java
Path broken = tmp.resolve("templates/gen/broken");
try {
    Files.createDirectories(broken);
    Files.writeString(broken.resolve("set.yaml"), "name: broken\nvars:\n  javaTime: flase\nfiles:\n  - { template: A.java.ftl, path: 'x' }\n");
    HttpResponse<String> r = post("/api/generate", Map.of("snapshotId", snapshotId, "tables", List.of(Map.of("name", "TB_EMP")), "templateSet", "broken"));
    assertEquals(400, r.statusCode(), r.body());   // RED — 지금 500
    assertTrue(r.body().contains("vars.javaTime") && r.body().contains("flase"), r.body());
    JsonNode t = JSON.readTree(get("/api/generate/templates").body());
    assertTrue(t.toString().contains("\"error\""), "목록은 error 칸으로 — " + t);
} finally {
    AnalyzeRoutesTest 의 삭제 도우미가 있으면 그것, 없으면 Files.walk 로 역순 삭제
}
```
  (`get(...)` 도우미가 없으면 `templatesListed` 가 쓰는 방식 그대로)
- `refusals` 의 `nope` 400 은 그대로 지나야 한다(`load` IAE)

### 7-28 — 세트 변수 빈 값(§3-5)
- `TemplateSet`:
```java
/** 7-28 — YAML 빈 값(javaTime: · ~)은 "" — asText 의 "null" 글이 템플릿·거절 글에 새지 않게 */
private static String text(JsonNode n) {
    return n.isNull() ? "" : n.asText();
}
```
  `:84` → `vars.put(e.getKey(), text(e.getValue()))`. `checkBools` 글 → `… 만: " + (v.isEmpty() ? "(빈 값)" : v)`
- `TemplatesTest.boolVarsRefused` 에 둘: `write("t3/set.yaml", "name: t3\nvars:\n  javaTime:\n  rte:\nfiles:\n  - { template: A.java.ftl, path: 'x' }\n")` → IAE 글에 `(빈 값)`(**RED** — 지금 `null`) · `write("t4/set.yaml", "name: t4\nvars:\n  rte:\nfiles:\n  - { template: A.java.ftl, path: 'x' }\n")` → `load(gen,"t4").vars().get("rte")` 는 `""`(**RED** — 지금 `"null"`)

### 7-29 — Detail·List 날짜 표시 — `java.util.Date` 세트만 `<fmt:formatDate>`(§3-6)
- `FieldRoles`:
```java
static final String K_DATE_PATTERN = "datePattern";
/** 7-29 — java.util.Date 세트의 화면 표시 패턴(JSTL fmt:formatDate). 바인딩 PATTERN 과 다르다 */
private static final Map<String, String> SHOW = Map.of(T_DATE, "yyyy-MM-dd", T_DATETIME, "yyyy-MM-dd HH:mm", T_TIME, "HH:mm");
```
  `dates` 의 `if (legacy) { f.put(K_JAVA_TYPE, "Date"); }` 안에 `f.put(K_DATE_PATTERN, SHOW.get(type));` 한 줄(분기 불변 — `type` 은 ISO 에 있던 셋뿐)
- `templates/gen/egov35/Detail.jsp.ftl` — `:2` 뒤에 `<#if fields?filter(f -> f.datePattern??)?has_content><%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>\n</#if>` · `:17` →
```
<#if f.datePattern??>
            <td><fmt:formatDate value="${result.[=f.name]}" pattern="[=f.datePattern]"/></td>
<#else>
            <td><c:out value="${result.[=f.name]}"/></td>
</#if>
```
- `List.jsp.ftl` — taglib 같은 한 줄(`listFields` 기준: `<#if listFields?filter(f -> f.datePattern??)?has_content>`) · `:51`(첫 열 링크 안의 `<c:out>`)·`:53` 둘 다 같은 `<#if f.datePattern??>` 분기. `<c:param … value="${result.[=p.name]}">` 는 그대로(범위 밖)
- 골든: `golden/gen/egov35/EmpHistDetail.jsp`(taglib 줄 + `:20` `<fmt:formatDate value="${result.startDate}" pattern="yyyy-MM-dd HH:mm"/>`) · `EmpHistList.jsp`(taglib + `:46`). **egov4·egov5·bbs 불변.** `-Dgolden.update=true -Dtest=GenTemplatesTest` 로 갱신한 뒤 `git diff --stat src/test/resources/golden` 이 그 둘뿐인지 본다 — 다른 파일이 바뀌면 중단
- `hidden` 입력(`Detail.jsp.ftl:24`)과 `<c:param>` 은 바인딩용이라 안 바꾼다 — 드러난 것으로(Date PK 의 `toString` 은 바인딩이 안 된다)

### 2-36 — fkTypeMismatch 동의어(§3-7)
- `StructureLintColumns`:
```java
/** 2-36 — fkTypeMismatch 비교용 동의어(방언 안에서 같은 저장 형식). 글은 원문 꼴로 낸다. N-형식·DATETIME/TIMESTAMP 는 다른 것으로 둔다(Oracle·MySQL FK 가 거절한다) */
private static final Map<String, String> CANON = Map.ofEntries(
        Map.entry("INT", "INTEGER"), Map.entry("INT4", "INTEGER"), Map.entry("SERIAL", "INTEGER"), Map.entry("SERIAL4", "INTEGER"),
        Map.entry("INT8", "BIGINT"), Map.entry("BIGSERIAL", "BIGINT"), Map.entry("SERIAL8", "BIGINT"),
        Map.entry("INT2", "SMALLINT"), Map.entry("SMALLSERIAL", "SMALLINT"), Map.entry("SERIAL2", "SMALLINT"),
        Map.entry("VARCHAR2", "VARCHAR"), Map.entry("CHARACTER VARYING", "VARCHAR"),
        Map.entry("NVARCHAR2", "NVARCHAR"), Map.entry("NATIONAL CHARACTER VARYING", "NVARCHAR"),
        Map.entry("CHARACTER", "CHAR"), Map.entry("BPCHAR", "CHAR"), Map.entry("NATIONAL CHARACTER", "NCHAR"),
        Map.entry("NUMBER", "NUMERIC"), Map.entry("DECIMAL", "NUMERIC"), Map.entry("DEC", "NUMERIC"),
        Map.entry("DOUBLE PRECISION", "DOUBLE"), Map.entry("FLOAT8", "DOUBLE"), Map.entry("FLOAT4", "REAL"),
        Map.entry("BOOL", "BOOLEAN"),
        Map.entry("TIMESTAMP WITHOUT TIME ZONE", "TIMESTAMP"), Map.entry("TIMESTAMP WITH TIME ZONE", "TIMESTAMPTZ"));

/** norm 한 형식의 대표 이름 — 표에 없으면 그대로 */
static String canon(String normed) {
    return CANON.getOrDefault(normed, normed);
}
```
- `StructureLint.differ:432` → `if (!StructureLintColumns.canon(a).equals(StructureLintColumns.canon(b))) { return "형식 " + a + " ≠ " + b; }`(분기 수 불변, 글은 원문)
- `StructureLintTest.fkTypeMismatchComparesTypeLengthPrecision` 에 셋: `ps = typedTable("PS", "ID", typed("ID", 1, "SERIAL", null, null, null))` · `c7` `INT4` → PS: 없음(**RED** — 지금 「형식 INT4 ≠ SERIAL」) · `c8` `varchar` 10 → PV: 없음(**RED**) · `c9` `NVARCHAR2` 10 → PV: 「C9.PN… 형식 NVARCHAR2 ≠ VARCHAR2」(음성 — N-형식은 다르다). 기대 목록에 c9 줄 추가, 나머지 그대로
- 골든: `ddl-lint.json` 전 그룹 `fkTypeMismatch 0` 그대로(동의어는 건수를 줄이기만 한다) · `pg-lint.json` 불변

### 2-37 — LOB 셋 + 이름 없는 유니크 detail(§3-8)
- `StructureLintColumns.LOB_TYPES` 에 `"LONG VARCHAR"`(goldilocks·Derby), `"STRING"`(CUBRID), `"LONG"`(Oracle·Tibero 글 LOB, MySQL 은 MEDIUMTEXT 동의어) 셋. 주석 「TEXT·TINYTEXT 는 안 센다 — PG 의 보통 문자 형식과 겹친다. 2-37 — LONG VARCHAR·STRING·LONG 은 센다(eGov 가 oracle CLOB 자리에 쓰는 형식)」
- `uniqueNullable:38` detail → `("유니크 " + k.name()).trim() + "(" + String.join(",", k.cols()) + ")"`(이름 없으면 「유니크(dict_type)」)
- `StructureLintTest.uniqueNullableAndLobs` — `l5` `lob("X",1,"LONG VARCHAR",null,true)`·`lob("Y",2,"STRING",null,true)` → 「L5 LOB 2: X·Y」(**RED**) · `l6` `LONG` + `CLOB` → 「L6 LOB 2: X·Y」(**RED**) · `u2` = Table `U2` 컬럼 `A`(not null)·`B`(nullable) + `withConstraints(null, List.of(), List.of(new UniqueKey(null, List.of("B"))))` → 「U2.B 유니크(B)」(**RED** — 지금 「유니크 (B)」). 기대 목록 셋 갱신. 2-35 의 주석 「TEXT 둘(L2)…아니다」 그대로
- 골든(의도된 변화 — 이것뿐): `golden/corpus/ddl-lint.json:10` `egov:cubrid … tooManyLobs 0 → 3` · `:11` `egov:goldilocks … tooManyLobs 0 → 3` · `golden/corpus/ddl-lint-findings.txt:29` `유니크 (dict_type)` → `유니크(dict_type)`. `bash scripts/mvn.sh -q test -Dtest=DdlCorpusTest`(RED — 세 줄) → `-Dgolden.update=true` 로 갱신 → `git diff src/test/resources/golden` 이 정확히 그 세 줄이면 커밋. `hr-lint.json`·`pg-lint.json`·egov 다른 방언 줄이 바뀌면 중단
- `README.md:38`·`DocCommands:134` 「열다섯 규칙」 그대로(규칙 수 불변)

### 0-76 — visual.js ⑥ NaN 가드(§3-9)
- `visual.js:324-325`:
```js
const b = document.getElementById('tb-profile'); const cs = getComputedStyle(document.body);
const ml = b ? parseFloat(getComputedStyle(b).marginLeft) : 0; // 0-76 — header 화면은 margin-left:auto → NaN
const limit = Math.round(document.body.getBoundingClientRect().right - parseFloat(cs.paddingRight) - (isNaN(ml) ? 0 : ml));
```
- TDD 예외(scripts). `node scripts/puppeteer/visual.js b73 --only dev_tools` ⑥ 통과·콘솔 0

### V-53 — compare ㉓ · 정적 분석 · `--corpus` · `--db`(§3-10)
- `design/compare-2026-10-28.md` 끝에 `## ㉓ 재측정 — 번들 73(2026-11-08)`: 「설계 57. 올라간 후보 — 번들 72 리뷰 1-77 ①~⑤ · ㉑·㉒ 「후보로」 의 typed DEFAULT·CTAS 조인·Detail 날짜·「(오류)」 글·fkTypeMismatch 동의어·LOB·유니크 detail」 · **정정** 한 줄 — ㉒ 「후보로」 의 「화면은 고르기(`GET …`)에서 먼저 막는다」 는 틀린 글이었다(`(오류)` 글만, 7-27 이 막는다) · **뺀 것** — a11y `<form:*>` 는 5-17 결정(사용자 2026-11-08) — 후보 목록에서 지운다 · 측정 표(1-78 음성 다섯 · 1-79 DEFAULT 셋 · 1-80 CTAS 꼴 넷 · 7-27 400 글 · 7-28 빈 값 · 7-29 egov35 Detail·List 두 줄 · 2-36 c7·c8·c9 · 2-37 cubrid·goldilocks 0 → 3 · javac 소스 수 그대로) · 정적 분석·벽시계(㉒ 대비) · 드러난 것 · 후보로(남은 ㉑·㉒ 잔여 — 개발자 도구 새 탭 · `dupIndex`·`fkTypes` 빈 이름 · `INLINE_COMMENT` 글 안 · List `<c:param>` Date PK · CTAS 식 컬럼 · java.time 표시 꼴 · `--db` CI · 현장 MyBatis 판)
- 순서(따로 낸다): `bash scripts/mvn.sh -DskipTests verify` rc 0 → `bash scripts/verify.sh` → `bash scripts/verify.sh --corpus` → `wsl --shutdown` · Docker Desktop → 여유 RAM ≥ 15GB 확인(모자라면 사용자에게 앱 내리기 요청 — 11.4GB 에서 끊겼다, ㉒) → `bash scripts/verify.sh --db` 혼자
- 골든: 2-37 의 세 줄·7-29 의 두 파일 밖은 전부 diff 0 기대 — 바뀌면 중단

## 4. 실행 스텝
### S0 — 설계 문서·행·번들 표(**이 설계 세션이 한다**, Opus 는 건너뛴다)
- 대상: `design/57-bundle73.md`(A 절) · `PLAN.md` 「번들」 표 「번들 73」 행 + 분할표 새 행 아홉(`1-78`·`1-79`·`1-80`·`7-27`·`7-28`·`7-29`·`2-36`·`2-37`·`0-76`·`V-53`) + 1-77 행 상태(B 절) · `PROGRESS.md`(C 절).
- 검증: `bash scripts/doc-lint.sh design/57-bundle73.md` · `bash scripts/verify.sh`(docs 레인) · `git commit -m "docs: S0 설계 57 — 번들 73"`. push 안 함.

### 1-78 — 따옴표 글 마스크
- 대상: `core/gen/DdlPull.java`(`mask` · `typedBody` 한 인자 · `typedOption` 매처 입력·javadoc) · `core/gen/DdlReader.java`(`item` 매처 입력 넷 + `def` 자르기 · `checks` · `DEFAULT` 가시성) · `DdlReaderTest.quotedWordsAreNotConstraints` + `:573` 주석(§3-1).
- 순서: 시험 써서 RED(q 의 pk·b nullable·fks·checks, t 의 fks·checks) → 구현 GREEN.
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,InsertGenTest,DdlGenTest,SelfCheckTest,ArchitectureTest'` 초록 · `git status` 에 골든 변화 없음 · `bash scripts/verify.sh` 초록 · 커밋 `fix: 1-78 따옴표 글 마스크 — 글 안 PRIMARY KEY·REFERENCES·CHECK 는 제약이 아니다`.
- 의존: S0.

### 1-79 — typed DEFAULT + PK NOT NULL
- 대상: `core/gen/DdlPull.java`(`typedOption` DEFAULT·`restate`) · `DdlReaderTest.typedTableFromType`·`typedOptionFkAndCheck`(§3-2).
- 순서: 단언 더해 RED → 구현 GREEN.
- 검증: `-Dtest='DdlReaderTest,InsertGenTest,SelfCheckTest'` 초록 · 골든 diff 0 · `verify.sh` 초록 · 커밋 `fix: 1-79 typed WITH OPTIONS DEFAULT 값 + PK 는 NOT NULL`.
- 의존: 1-78.

### 1-80 — CTAS 조인
- 대상: `core/gen/DdlPull.java`(패턴 셋 · `fromSources`·`source`·`resolve` · `selected` · `ctasColumns` 두 줄 · javadoc) · `DdlReaderTest.ctasFromSource`(§3-3).
- 순서: 시험 확장(T0·T7·T8·T9·TA, T6 → T99) RED → 구현 GREEN.
- 검증: `-Dtest='DdlReaderTest,InsertGenTest,SelfCheckTest'` 초록(`SelfCheckTest` — 새 메서드 복잡도 ≤ 15·`DdlPull` 메서드 수 ≤ 30) · 골든 diff 0 · `verify.sh` 초록 · 커밋 `feat: 1-80 CTAS 조인·쉼표 조인·한정 항목·t.* — 원본 여럿에서 컬럼`.
- 의존: 1-78(독립 — 차례만).

### 7-27 — 오류 세트 disabled + POST 400
- 대상: `src/main/resources/tools/spring_source_generator_ext.js`(`loadSets` 두 줄) · `web/GenerateRoutes.java`(`postGenerate` 세트 읽기 자리) · `GenerateRoutesTest.brokenSetIs400`(§3-4).
- 순서: 시험 RED(500) → `GenerateRoutes` GREEN → JS 두 줄.
- 검증: `-Dtest='GenerateRoutesTest,SelfCheckTest,SmokeHtmlUnitTest,BackendToolsTest,ToolsFolderTest'` 초록 · `self-check.txt` 불변(「복잡도 48」) · `verify.sh` 초록 · 커밋 `fix: 7-27 오류 세트는 고를 수 없고 고르면 400 — disabled·title + 세트 읽기 IAE`.
- 의존: S0.

### 7-28 — 세트 변수 빈 값
- 대상: `core/gen/TemplateSet.java`(`text`·`checkBools` 글) · `TemplatesTest.boolVarsRefused`(§3-5).
- 순서: 단언 둘 RED → GREEN.
- 검증: `-Dtest='TemplatesTest,GenTemplatesTest,GeneratorTest,SelfCheckTest'` 초록 · 골든 diff 0 · `verify.sh` 초록 · 커밋 `fix: 7-28 세트 변수 빈 값은 "" — 거절 글 「(빈 값)」`.
- 의존: 7-27(독립 — 차례만).

### 7-29 — Detail·List 날짜 표시
- 대상: `core/gen/FieldRoles.java`(`K_DATE_PATTERN`·`SHOW`·`dates` 한 줄) · `templates/gen/egov35/Detail.jsp.ftl`·`List.jsp.ftl` · 골든 `golden/gen/egov35/EmpHistDetail.jsp`·`EmpHistList.jsp`(§3-6).
- 순서: `FieldRoles` + 템플릿 → `-Dtest=GenTemplatesTest` RED(egov35 둘 diff) → `-Dgolden.update=true -Dtest=GenTemplatesTest` → `git diff --stat src/test/resources/golden` 이 egov35 두 파일뿐인지(아니면 중단) → diff 본문이 taglib 한 줄 + `startDate` 줄(Detail 1·List 1)뿐인지.
- 검증: `-Dtest='GenTemplatesTest,GeneratorTest,GeneratorJavacTest,FieldRolesTest,SelfCheckTest'` 초록(`FieldRolesTest` 가 없으면 빼고 돈다) · `verify.sh` 초록 · 커밋 `fix: 7-29 Detail·List 날짜 — java.util.Date 세트는 fmt:formatDate`. TDD 예외(템플릿·골든) — `Ruling:` 한 줄.
- 의존: 7-28(독립 — 차례만).

### 2-36 — fkTypeMismatch 동의어
- 대상: `core/deliverable/StructureLintColumns.java`(`CANON`·`canon`) · `StructureLint.java`(`differ` 한 줄) · `StructureLintTest.fkTypeMismatchComparesTypeLengthPrecision`(§3-7).
- 순서: 시험 RED(c7·c8) → GREEN.
- 검증: `-Dtest='StructureLintTest,DeliverableRoutesTest,SelfCheckTest,ArchitectureTest'` 초록 · 골든 diff 0 · `verify.sh` 초록 · 커밋 `fix: 2-36 fkTypeMismatch 동의어 — SERIAL·INT·VARCHAR2 등은 같은 형식`.
- 의존: S0.

### 2-37 — LOB 셋 + 유니크 detail
- 대상: `core/deliverable/StructureLintColumns.java`(`LOB_TYPES`·`uniqueNullable` 글) · `StructureLintTest.uniqueNullableAndLobs` · 골든 `ddl-lint.json`(2줄)·`ddl-lint-findings.txt`(1줄)(§3-8).
- 순서: 시험 RED(l5·l6·u2) → GREEN → `bash scripts/mvn.sh -q test -Dtest=DdlCorpusTest`(RED 세 줄) → `-Dgolden.update=true -Dtest=DdlCorpusTest` → `git diff src/test/resources/golden` 정확히 세 줄.
- 검증: `-Dtest='StructureLintTest,DeliverableRoutesTest,SelfCheckTest'` 초록 · `-Dtest='DdlCorpusTest,HrSnapshotCorpusTest'` 초록(갱신 뒤) · `verify.sh` 초록 · 커밋 `fix: 2-37 LOB 에 LONG VARCHAR·STRING·LONG + 이름 없는 유니크 detail`.
- 의존: 2-36.

### 0-76 — visual.js ⑥ NaN 가드
- 대상: `scripts/puppeteer/visual.js`(두 줄)(§3-9).
- 순서: `bash scripts/mvn.sh -q package -DskipTests` → 고침 → `node scripts/puppeteer/visual.js b73 --only dev_tools`.
- 검증: `out/visual/b73/summary.txt` 「확인 실패 0 · 콘솔 오류 0」 · `verify.sh` 초록 · 커밋 `fix: 0-76 visual.js ⑥ margin-left auto 는 0 — NaN 가드`. TDD 예외(scripts) — `Ruling:` 한 줄.
- 의존: S0.

### V-53 — compare ㉓ · 정적 분석 · `--corpus` · `--db`
- 대상: `design/compare-2026-10-28.md` ㉓(§3-10).
- 검증: 넷 초록(따로) · 끝 줄이 더 부르는 레인 없음 · 벽시계 둘 · `doc-lint` · 커밋 `docs: V-53 재측정 — compare ㉓ + 정적 분석 + --corpus + --db`.
- 의존: 0-76.

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7 · 행의 축·강제 지점과 diff · `mask` 가 같은 길이이고 `''` 토글이 `matching` 과 같은가 · `item`·`checks`·`typedOption` 의 값 자르기가 전부 원문 자리인가 · `item`·`read`·`postGenerate` 분기 수 불변(`self-check.txt` diff 0) · `restate` 가 DEFAULT 없는 PK 줄에서 defaultValue 를 안 지우나 · `fromSources` 가 서브쿼리·없는 원본·모호한 이름에서 null/빈 목록인가 · `SOURCE` 가 `ON`·`USING` 을 별칭으로 안 먹나 · 7-27 이 `nope` 400 을 유지하나 · `text()` 가 `null` 글을 안 내나 · `datePattern` 이 egov4·egov5 에 없나 · `fmt` taglib 이 날짜 없는 표에 안 나오나 · `canon` 이 글을 안 바꾸나 · 골든 변화가 정확히 다섯 파일(ddl-lint.json 2줄·findings 1줄·egov35 Detail·List)인가 · 새 규칙 없음(픽스처 요구 없음) · PMD 짝 diff 0) → `bash scripts/gate-probe.sh` → `bash scripts/mvn.sh -DskipTests verify` → `bash scripts/verify.sh`(리뷰 고침 뒤 `--corpus`·`--db` 는 바뀐 레인만 다시) → `git push -u origin work/2026-11-08` → `gh pr create --base main`(행마다 시작 세 줄 표 + 로컬 닫기 `62d9245`·`40f8d75`·S0 같이 + ㉓ 핵심 수치) → CI 폴링(`gh api … check-runs`) → claude[bot] 처분 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → 닫기 커밋은 다음.

## 5. 금지 사항
- `pure/`·portfolio·`docs/`·`core/meta`·`core/dialect`·`core/profile`·`core/check`·`core/quality` · `core/gen` 의 `GenModel`·`Generator`·`Validation`·`TypeMapping`·`DtoGenerator`·`InsertGen`·`DdlGen` · `web/App.java`·`GenerateRoutes.getTemplates` · `pom.xml`·`scripts/verify-fingerprint.sh`·`corpus/MANIFEST`·표본 · `common.css`·`common.js`·다른 화면 HTML/JS · `templates/gen/egov4`·`egov5`·`input.ftl`·`Regist`·`Updt`·`set.yaml` 셋 · `check/rules.yaml`·`pure-rules.json` · `bundle/`·`README-SITE.txt` 수정 금지 · 새 Maven 의존성·새 화면·라우트·프로필 키·세트 변수 키·구조 점검 규칙(enum) 금지
- `DdlReader` — `item`·`read` 에 분기 추가 금지(매처 입력만) · `matching`·`QUOTED`·`INLINE_UQ` 논리 변경 금지 · 새 메서드 추가 금지(도우미는 `DdlPull`) · `INLINE_COMMENT` 입력 변경 금지(범위 밖)
- `DdlPull` — 새 정규식은 §3-3 의 셋(+`STAR_OF`)뿐, 중첩 수량자 금지(SpotBugs REDOS) · CTAS 식·함수·서브쿼리 컬럼 추측 금지 · notes 글 변경 금지
- `GenerateRoutes` — `postGenerate` 분기 수 변경 금지(「복잡도 48」) · 전역 IAE 핸들러 금지 · 400 글은 `e.getMessage()` 그대로
- `TemplateSet` — `BOOL_VARS` 변경 금지 · `load` 에 분기 추가 금지(`text` 도우미)
- `FieldRoles` — `dates` 분기 추가 금지(한 줄) · 바인딩 `PATTERN`·`ISO` 변경 금지
- 템플릿 — java.time 세트에 `fmt` 금지(JSTL 이 못 받는다) · `<c:param>`·hidden 변경 금지
- `StructureLint` — `differ` 분기 수 변경 금지 · 글 꼴(`형식 A ≠ B`) 변경 금지 · `StructureLint` 에 메서드 추가 금지(상한 30 — `StructureLintColumns` 에) · `dupIndex`·`fkTypes` detail 변경 금지 · `LOB_JDBC`·`TEXT` 제외 변경 금지
- `visual.js` — ⑥ 두 줄 밖 수정 금지
- 골든 — §3-6·§3-8 의 다섯 파일(ddl-lint.json 2줄·findings 1줄·egov35 Detail·List) 밖 변화 금지, `-Dgolden.update` 는 그 두 시험(`GenTemplatesTest`·`DdlCorpusTest`)에만 · `self-check.txt` 불변 · 이력·compare 에 표본 코드 본문 금지 · 원본 되쓰기 API·CLI 금지 · `pmd-baseline.sh` 안 돌린다 · `--db` 와 다른 JVM 겹치기 금지 · `--corpus` → Docker → `--db` 순서 · `--db` 앞 여유 RAM 15GB(못 맞추면 사용자에게 앱 내리기 요청)

## 6. 최종 검증
- `bash scripts/mvn.sh -DskipTests verify` rc 0 · `bash scripts/verify.sh` · `--corpus` · `--db` 초록(순서대로, 따로) · `gate-probe.sh` 10/10
- 골든: `git diff --stat origin/main -- src/test/resources/golden` 이 정확히 `corpus/ddl-lint.json`·`corpus/ddl-lint-findings.txt`·`gen/egov35/EmpHistDetail.jsp`·`gen/egov35/EmpHistList.jsp` 넷 · `self-check.txt` 불변
- 시험: `DdlReaderTest` +1(`quotedWordsAreNotConstraints`)·`typedTableFromType`·`typedOptionFkAndCheck`·`ctasFromSource` 확장 · `TemplatesTest.boolVarsRefused` +2 단언 · `GenerateRoutesTest` +1(`brokenSetIs400`)·`refusals` 그대로 · `StructureLintTest` 둘 확장 · `GenTemplatesTest`·`GeneratorTest`·`GeneratorJavacTest`(소스 40·javac 0) · `DdlCorpusTest`·`HrSnapshotCorpusTest`·`GeneratorCorpusTest` 초록 · `SmokeHtmlUnitTest`·`BackendToolsTest`·`ToolsFolderTest` · `ArchitectureTest`·`VerifyLanesTest`·`SelfCheckTest`
- 집 검증: `out/visual/b73/summary.txt` 확인 실패 0 · 콘솔 0
- 수동(한 번, 이력에 한 줄): `run.bat` → 소스 생성 화면 — `egov35/set.yaml` 의 `javaTime` 을 `"flase"` 로 잠깐 바꿔 세트 고르기에 `egov35 (오류)` 가 회색이고 hover 에 사유, 고른 채 「생성」 → 400 글이 화면에(되돌린다 — 커밋 금지) · egov35 로 날짜 컬럼 있는 표 생성 → Detail.jsp 에 `<fmt:formatDate>`
- compare ㉓ 표 + 정정 + 뺀 것 + 「드러난 것」 + 「후보로」 · 이력에 건수·벽시계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- 1-78·1-79·1-80 이 골든(`ddl-*`·`sqltables-diff`·`logical-summary`·`gen-hr`·`analyze-*`)을 바꿈 · 1-78 음성(`e` 의 defaultValue `'x'`)이 RED · `self-check.txt` 의 `item`·`read` 줄 변화
- 1-80 에서 T6(`T99`)·T9(모호)가 컬럼을 받음(추측이 샌다)
- 7-27 뒤 `self-check.txt:125` 「복잡도 48」 이 바뀜 · `refusals` 의 `nope` 가 400 아님
- 7-29 골든 갱신 diff 가 egov35 둘 밖(egov4·egov5·bbs)을 건드림 · `GeneratorJavacTest` 빨강
- 2-37 `DdlCorpusTest` 갱신 diff 가 세 줄 밖(다른 방언 `tooManyLobs`·`hr-lint.json`·`pg-lint.json`) · cubrid·goldilocks 가 3 이 아님
- `pmd:check`·SpotBugs 새 짝(REDOS 포함) · `ArchitectureTest`·`VerifyLanesTest` 빨강
- `verify.sh --db` 메모리로 끊김 — 고아 JVM 끄고 한 번 더, 또 끊기면 보고
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① 1-78·1-79·1-80 의 골든 diff 0(표본 DDL 에 글 안 제약 낱말·typed DEFAULT·CTAS 가 없다고 실측했지만 돌려야 안다 — 바뀌면 중단 조건) ② 2-37 의 cubrid·goldilocks 가 정확히 3(표본 줄 번호로 세 표 확인 — 다른 표에 `STRING` 둘이 더 있으면 4 이상, 그때 중단·보고) ③ `--corpus`·`--db` 벽시계. 첫 스텝 전: `bash scripts/verify.sh` 초록 · `git log --oneline -1`(S0 커밋, 가지 `work/2026-11-08`) · `git branch --list 'wip/*'` 빈 것 · Docker Desktop 상태 · node · `C:/workspace/node_modules/puppeteer` · `C:/workspace/toolbox-corpus` 있음
- **사용자만 정할 수 있는 것** — 없음((다) 뺌 · 머지 위임 (가) · 번들 하나·PR 하나 — 전부 2026-11-08 결정)

