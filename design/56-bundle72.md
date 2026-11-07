# 설계 56 — 번들 72: 오류 잡기만 — typed CHECK 따옴표 글(1-74) · 세트 변수 `javaTime` 검증 · 375px dev_tools 프로필 줄 · 375px code_check 결과 칸 (Fable 설계 · Opus 실행, 2026-11-03)

## 맥락
번들 71(설계 55) compare ㉑ 「드러난 것」·「후보로」 가운데 오류 넷만 — 마무리 리뷰 Important 1(새 행 1-74)과 세트 변수 자유 글, 1-73 집 검증 샷에서 드러난 375px 배치 둘. 새 기능·새 화면 없음. 판단 기준은 Java 쪽 정확도, JSP 는 깨지지 않을 만큼만(사용자 2026-11-03).

실측(2026-11-03, 코드 읽기):
- **1-74** `DdlPull.typedBody`(`core/gen/DdlPull.java:161-162`) — `TYPED_WORDS` 판정과 `typedOption` 호출 둘 다 `DdlReader.QUOTED.matcher(o.group(2)).replaceAll("''")`(따옴표 글을 `''` 로 비운 사본)을 쓴다. `typedOption`(`:169-192`)은 그 글로 `INLINE_PK`·`NOT_NULL`·`UNIQUE`(`:170-186`, 1-70·설계 54)에 더해 1-72 가 붙인 `DdlReader.COL_REF`(`:187`)·`DdlReader.checks`(`:191`)까지 부른다 → CHECK 식 리터럴이 `''` 로 손상. 컬럼 가지 `DdlReader.item`(`:338-396`)은 `rest` **원문**으로 `INLINE_PK`(`:360`)·`NOT NULL`(`:362`)·`COL_REF`(`:391`)·`checks`(`:395`)를 부르고, `INLINE_UQ` 만 따옴표를 비운 사본(`:388`)으로 본다. `DdlReader.checks`(`:516`)는 `CHECK_AT` 뒤 괄호 균형으로 자른다 — 따옴표를 모른다(설계 55 §2 그대로, 안 바꾼다). `QUOTED` 는 작은따옴표 글만(`:43`)
- **세트 변수** `TemplateSet.load`(`core/gen/TemplateSet.java:57-99`) — `vars` 는 `asText()` 로 전부 글(`:81`, 부모 위에 덮음 `:77`). 검증은 세트 이름 꼴·세트 없음·`extends` 한 단계·`files` 항목·`files` 없음 다섯(전부 `IllegalArgumentException`). `javaTime` 은 `FieldRoles.dates`(`:193`)가 `"false".equals(vars.get("javaTime"))` 로만 보고, `valid` 는 `Validation.of` 가 `"true".equals` 로만 본다 — 둘 다 오타(`"flase"`·`"ture"`)가 조용히 기본 가지(java.time·검증 없음)로 간다. 세트 읽기 자리는 `GenerateRoutes.getTemplates`(`web/GenerateRoutes.java:60`, IAE → 응답의 `error` 칸, 화면 고르기에 `이름 (오류)`)와 `postGenerate`(`:139`, IAE 핸들러 없음 — `App.java:203-206` 은 `ProfileUnreadable`·Jackson 둘뿐 → Javalin 500). YAML 맨 불리언(`javaTime: false`)은 `asText()` 가 `"false"` 라 글과 같다
- **375px dev_tools** — `dev_tools.html:63` `select, input[type=text], input[type=date], input[type=number] { padding:5px 8px; width:100%; }` 가 화면 전역이라 `common.js` `profilePicker()`(`:204`)가 만드는 `#tb-profile-sel` 에도 `width:100%` 가 걸린다. dev_tools 는 `<header>`·`<h1>` 이 없어 `.tb-profile-row` 가 `body`(flex column) 첫 자식으로 들어가고(`:228`, `float:right` 는 flex 안에서 안 듣는다), 1-73 의 `#tb-profile{white-space:nowrap;flex-shrink:0}`(`:67`) 뒤 고르기가 375px 를 거의 다 먹어 `<small>접속</small>` 이 화면 밖(샷 `out/visual/nowrap/dev_tools-*-375.png` — 「접속」 오른쪽 잘림). `<header>` 있는 화면(code_check)은 전역 select 규칙이 없어 멀쩡하다. 다른 header 없는 화면 셋(db_browser·deliverable_sql·logical_name)엔 전역 `select{width}` 규칙이 없다(`deliverable_sql.html:21` 은 `.tb-table select{width:auto}`)
- **375px code_check** — `code_check.html:13-14·29` `.main{display:flex}` + `#rulesCol{width:34%;min-width:300px}` + `.panes{flex:1;min-width:0}` → 375px(안쪽 347px)에서 규칙 칸이 300px 를 먹고 결과 칸(`.panes` — 폴더·스택·검사 버튼)은 35px(샷 `code_check-run-*-375.png`). 좁은 폭 규칙은 `:53` `@media (max-width:700px){.trend{…}}` 하나뿐. `#rulesCol` 은 JS 가 `fold`(`code_check_ext.js:613`)·`hidden`(`:377`, 배포·추이 탭)만 바꾼다 — 폭은 CSS 뿐. `.pane .dirbar input[type=text]{width:380px}`(`:18`)도 375px 를 넘는다
- **집 검증** `scripts/puppeteer/visual.js` — 샷(1400·375 × dark·light) 뒤 「동작 확인」 `check(ok, what)` 다섯(①~⑤, `:259-318`)이 있고 실패는 label 에 상관없이 빨강(`:8`). `open(tool)`(`:202`)은 `#tb-mode-badge` 「백엔드 연결」 까지 기다리니 그 뒤엔 `#tb-profile` 이 있다. `--only a,b` 는 샷과 확인 둘 다 거른다. `scripts/puppeteer` 는 corpus 레인(`verify-fingerprint.sh:86`)
- 레인: `core/gen`(DdlPull·TemplateSet) db·corpus 둘 다 · `common.js`·`code_check.html` tools/java · `scripts/puppeteer` corpus. 기준선 `golden/check/self-check.txt` 에 `DdlPull`·`TemplateSet` 없음(`GenerateRoutes.postGenerate(7)` 48 만 — 안 건드린다). 표본에 typed `WITH OPTIONS CHECK` 없음(설계 54·55 실측) → 1-74 골든 불변 기대

**범위 밖(사용자 2026-11-03)**: compare ㉑ 「후보로」 의 기능 항목 전부(a11y `<form:*>` · 개발자 도구 새 탭 · fkTypeMismatch 동의어 · `LONG VARCHAR`/`STRING` LOB · CTAS 조인 · typed `DEFAULT` 값 · 유니크 detail 빈 칸 · Detail.jsp 날짜 표시) · 5-31 · `--db` 레인 CI 이전 · 보류·조건부 행.

## 사용자 결정(2026-11-03)
- 판단 기준 Java 쪽 정확도, JSP 는 깨지지 않을 만큼만 · 번들 하나·PR 하나 · 다시 묻지 않는다
- 머지 위임 있음 — (가)(2026-11-03, 번들 70·71 과 같게)
- 설계가 정한 것(기본값이 뚜렷해 안 물음): 세트 변수 검증은 `TemplateSet.load`(세트 읽기)에서 `"true"|"false"` 만 받는 키 목록 `BOOL_VARS = ["valid", "javaTime"]` — `valid` 도 같은 결함·같은 비용이라 같이 건다(키 목록 한 상수) · typed 몸은 컬럼 가지와 같은 규칙 — PK·NOT NULL·UNIQUE 매처만 따옴표 비운 사본, `COL_REF`·`checks` 는 원문(1-74 행 그대로) · dev_tools 고침은 `common.js` 가 끼우는 고르기 스타일에 `#tb-profile-sel{width:auto;max-width:60vw}` 한 줄(id 가 화면의 type 규칙을 이긴다 — `dev_tools.html:63` 을 좁히면 다른 화면이 같은 규칙을 둘 때 되풀이된다) · code_check 는 700px 아래에서 두 칸을 세로로(규칙 칸 위 40%, 결과 칸 나머지) — 규칙 칸을 접어 두는 안은 버림(처음 여는 사람이 규칙을 못 본다) · 375px 배치는 `visual.js` 「동작 확인」 ⑥·⑦ 로 잰다(눈 대신 수치 — RED→GREEN 자리)

실행자는 Opus. 차례 `S0`(이 설계 세션이 했다) → `1-74` → `7-26` → `1-75` → `1-76` → `V-52` → 마무리. 가지 `work/2026-11-03b`(로컬 닫기 `9c1f8b3`·S0 같이). 번들 하나·PR 하나.

---

## 1. 목표와 범위
- 한 문장: typed 표의 CHECK 식이 원문 리터럴을 지키고, 세트 변수 오타가 세트 읽기에서 걸리며, 375px 에서 dev_tools 프로필 줄과 code_check 결과 칸이 화면 안에 든다.
- 완료 시 체감: `CREATE TABLE t OF ty (status WITH OPTIONS CHECK (status IN ('A','B')))` → checks 식 `status IN ('A','B')`. `templates/gen/egov35/set.yaml` 의 `javaTime: "flase"` → 소스 생성 화면 세트 고르기에 `egov35 (오류)`, `GET /api/generate/templates` 의 `error` 에 「egov35/set.yaml 의 vars.javaTime 는 "true"|"false" 만: flase」. 375px dev_tools 에서 「프로필 [고르기] 접속」 이 한 줄에 다 보이고, 375px code_check 에서 폴더 칸·「폴더 검사」 버튼이 제 폭. compare ㉒.
- In scope: `core/gen/DdlPull.java`(`typedBody`·`typedOption`) · `core/gen/TemplateSet.java`(`load` + 도우미 하나) · `tools/common.js`(한 줄) · `tools/code_check.html`(`<style>` 미디어 블록) · `scripts/puppeteer/visual.js`(확인 둘) · 시험 `DdlReaderTest.typedOptionFkAndCheck`·`TemplatesTest.boolVarsRefused` · `design/compare-2026-10-28.md` ㉒ · PLAN·PROGRESS.
- Out of scope: `DdlReader` 본문(`checks`·`COL_REF`·`item`) · `FieldRoles`·`Validation`·`GenModel`·`Generator` · `GenerateRoutes`(POST 의 IAE 500 — 후보로) · `spring_source_generator_ext.js`(「(오류)」 옵션에 글 붙이기 — 후보로) · `common.css` · `dev_tools.html:63` 전역 규칙 · 다른 화면의 375px 배치 · 순수본·표본 · 「범위 밖」 전부.

## 2. 현재 구조 요약
「맥락」 의 실측이 요약이다. 덧붙이는 함정:
- `typedBody` 의 `TYPED_WORDS` 판정(`:161`)은 **따옴표 비운 사본으로 그대로** — `DEFAULT 'NOT NULL'` 같은 글이 아는 낱말 판정을 속이지 않게. 바꾸는 것은 `typedOption` 에 넘기는 인자(원문)와 `typedOption` 안에서 사본을 한 번 만드는 것뿐
- `INLINE_PK`(`DdlReader:38`) g1 은 `CONSTRAINT 이름` — 이름은 큰따옴표라 `QUOTED`(작은따옴표)가 안 건드린다. 사본으로 매칭해도 이름은 산다
- `TemplateSet.load` 는 부모를 `allowExtends=false` 로 먼저 읽는다(`:76`) — 검증을 `vars` 합친 뒤(`:81` 뒤)에 두면 부모 세트의 오타는 부모 이름으로, 자식의 오타는 자식 이름으로 글이 난다. 복잡도를 `load` 에 더하지 않게 도우미로 뺀다(`load` 는 지금 분기 아홉 안팎 — 상한 15)
- `TemplatesTest` 는 `@TempDir` 에 `write(rel, text)` 로 세트를 만든다(`:23-33`). YAML 1.1 의 `yes`·`no` 는 불리언으로 읽혀 `"true"` 가 되니 틀린 값 예시는 `flase`·`ture`·`enabled` 처럼 불리언이 아닌 글로
- `common.js` 가 끼우는 `<style>`(`:60-70`)은 `#tb-theme`·`#tb-profile`·`.tb-profile-row` 규칙 — 한 줄 덧붙인다. `ToolsFolderTest.toolsHaveNoLocalTheme` 는 화면 `<style>` 만 본다(`:443-454` — `:root`·`button{`·`.btn-*`·`.dl`·고정 색). code_check 의 미디어 블록은 그 다섯에 안 걸린다
- `code_check.html` `body{height:100vh;overflow:hidden}` — 세로 쌓기에서도 화면 스크롤 없이 두 칸이 높이를 나눠 쓴다. `.panes` 는 `min-width:0` 만 있어 column 방향에선 `min-height:0` 이 있어야 안쪽 `overflow` 가 듣는다. `#rulesCol[hidden]`·`.fold` 는 `display:none` 이라 미디어 블록과 안 부딪친다
- `visual.js` 「동작 확인」 은 `try` 안(`:202` 의 `open` 과 같은 범위) — ④ 블록(`:303-318`) 뒤에 ⑥·⑦ 을 둔다. 뷰포트를 375 로 바꾸고 재니 그 뒤 다른 확인이 없다(④ 가 마지막)
- `verify.sh` 레인: 1-74·7-26 이 db·corpus 둘 다, `visual.js` 가 corpus — V-52 에서 한 번에

## 3. 설계 결정 — 전부 [고정]

### 1-74 — typed 몸 CHECK·FK 는 원문으로(§3-1)
- `DdlPull.typedBody` `:162` — `typedOption(d, DdlReader.unquote(o.group(1)), o.group(2));`(원문). `:161` 의 `TYPED_WORDS` 판정은 그대로
- `DdlPull.typedOption` — 머리에 `String bare = DdlReader.QUOTED.matcher(rest).replaceAll("''");` 한 줄, `INLINE_PK`(`:170`)·`NOT_NULL`(`:175`)·`UNIQUE`(`:184`)는 `bare`, `COL_REF`(`:187`)·`checks`(`:191`)는 `rest`. javadoc 「PK·NOT NULL·UNIQUE 는 따옴표 비운 사본으로(글 안의 낱말을 안 본다), REFERENCES·CHECK 는 원문으로(1-74 — CHECK 식 리터럴을 지킨다) — 컬럼 가지 `DdlReader.item` 과 같은 규칙」. 분기 수 불변(6)
- `DdlReaderTest.typedOptionFkAndCheck` 고침(양성·음성 한 시험에):
  - 입력 `CREATE TYPE ty AS (id int, pid int, qty int, status text, note text); CREATE TABLE p (id INT PRIMARY KEY); CREATE TABLE t OF ty (id WITH OPTIONS PRIMARY KEY, pid WITH OPTIONS REFERENCES p(id), qty WITH OPTIONS CHECK (qty > 0), status WITH OPTIONS CHECK (status IN ('A','B')), CONSTRAINT ck2 CHECK (id > 0)); CREATE TABLE u OF ty (qty WITH OPTIONS DEFAULT 0, note WITH OPTIONS DEFAULT 'PRIMARY KEY');`
  - t: 컬럼 `[id, pid, qty, status, note]` · PK `[id]` · fks 1(`pid` → `p(id)`) · checks `[Check(null, "qty > 0"), Check(null, "status IN ('A','B')"), Check("ck2", "id > 0")]`(**양성** — 지금 코드는 `status IN ('','')` 라 RED)
  - u: `pk()` null(**음성** — `DEFAULT 'PRIMARY KEY'` 의 글 안 낱말은 PK 가 아니다, 사본 매칭이 산다) · fks 0 · checks 0 · unreadable 0
- 골든 diff 0(표본에 typed CHECK 없음). `self-check.txt` 불변

### 7-26 — 세트 변수 `"true"|"false"` 검증(§3-2)
- `TemplateSet`:
```java
/** 7-26 — "true"|"false" 만 받는 세트 변수. 오타("flase")가 조용히 기본 가지(java.time·검증 없음)로 가지 않게 세트 읽기에서 거절한다 */
private static final List<String> BOOL_VARS = List.of("valid", "javaTime");

/** 7-26 — BOOL_VARS 키가 있으면 "true"|"false" 여야 한다. YAML 맨 불리언도 asText 가 그 글이라 지난다. name 은 글 머리(부모 세트는 부모 이름) */
private static void checkBools(String name, Map<String, String> vars) {
    for (String k : BOOL_VARS) {
        String v = vars.get(k);
        if (v != null && !"true".equals(v) && !"false".equals(v)) {
            throw new IllegalArgumentException(name + "/set.yaml 의 vars." + k + " 는 \"true\"|\"false\" 만: " + v);
        }
    }
}
```
  `load` `:81` 뒤에 `checkBools(name, vars);` 한 줄. 새 메서드 복잡도 3. 클래스 javadoc(`:17-21`)에 「`vars` 의 `valid`·`javaTime` 은 `"true"|"false"` 만(7-26)」 한 토막
- 드러나는 자리(바꾸지 않는다): `GET /api/generate/templates` 의 `error` 칸 → 화면 고르기 `이름 (오류)` · `POST /api/generate` 는 IAE 핸들러가 없어 500(후보로 — 화면이 고르기에서 먼저 막는다)
- `TemplatesTest` 새 `boolVarsRefused`(`badSetsSayWhy` 꼴):
  - `t1/set.yaml` = `name: t1\nvars:\n  javaTime: flase\nfiles:\n  - { template: A.java.ftl, path: 'x' }\n` → `assertThrows(IllegalArgumentException.class, …)` 의 글에 `vars.javaTime` 과 `flase` 둘 다
  - `t2/set.yaml` 에 `valid: enabled` → IAE 글에 `vars.valid`
  - `base()` 뒤 `c2/set.yaml` = `name: c2\nextends: base\nvars:\n  javaTime: "ture"\n` → IAE 글이 `c2/` 로 시작(자식 이름)
  - `ok/set.yaml` = `name: ok\nextends: base\nvars:\n  javaTime: false\n  valid: "true"\n` → `load` 가 지나고 `vars().get("javaTime")` 가 `"false"`(맨 불리언도 글) · `base` 그대로(키 없음) 지남
- 실제 세트 셋(egov35 `"false"`/`"false"` · egov4·egov5 `"true"`/`"false"`·`"true"`)은 그대로 지난다 — `GenTemplatesTest`·`GeneratorTest` 가 잰다. 골든 diff 0

### 1-75 — dev_tools 375px 프로필 줄(§3-3)
- `common.js:67` 뒤 한 줄: `'#tb-profile-sel{width:auto;max-width:60vw}' +` — 주석 「1-75 — 화면 전역 `select{width:100%}`(dev_tools) 가 고르기를 늘려 375px 에서 뒤 글이 잘린다. id 선택자가 화면의 type 규칙을 이긴다」. `.tb-profile-row`·`#tb-profile` 은 그대로
- `visual.js` ④ 블록 뒤(`:318` 뒤, `finally` 앞):
```js
    // 1-75·1-76 — 375px 배치(HtmlUnit 은 배치를 못 잰다). 마지막 확인이라 뷰포트를 되돌리지 않는다
    if (!ONLY || ONLY.includes('dev_tools') || ONLY.includes('code_check')) {
      await page.setViewport({ width: 375, height: 900 });
      if (!ONLY || ONLY.includes('dev_tools')) {
        await open('dev_tools');
        const r = await page.evaluate(() => { const b = document.getElementById('tb-profile'); return b && b.lastElementChild ? Math.round(b.lastElementChild.getBoundingClientRect().right) : -1; });
        check(r > 0 && r <= 375, '⑥ 375px dev_tools 프로필 줄 끝(접속)이 화면 안 — right ≤ 375: ' + r);
      }
      if (!ONLY || ONLY.includes('code_check')) {
        await open('code_check');
        const w = await page.evaluate(() => { const p = document.querySelector('.panes'); return p ? Math.round(p.getBoundingClientRect().width) : -1; });
        check(w >= 300, '⑦ 375px code_check 결과 칸(.panes) 폭 ≥ 300: ' + w);
      }
    }
```
  머리 주석(`:5-8`)의 확인 목록에 ⑥·⑦ 한 토막
- TDD 예외(화면 JS·scripts) — RED→GREEN 자리: `visual.js` 확인 둘을 먼저 넣고 `node scripts/puppeteer/visual.js b72-red --only dev_tools,code_check` → 「확인 실패 2」(⑥·⑦) 를 이력에 → `common.js` 한 줄 → `node scripts/puppeteer/visual.js b72-a --only dev_tools` → ⑥ 통과·콘솔 0. 그 앞에 `bash scripts/mvn.sh -q package -DskipTests`(`target/app.jar`)
- java 레인 `SmokeHtmlUnitTest.profilePickerOnProfileScreens`·`ToolsFolderTest`·`BackendToolsTest` 초록

### 1-76 — code_check 375px 결과 칸(§3-4)
- `code_check.html:53` 의 `@media` 한 줄을 블록으로:
```css
/* 1-76 — 700px 아래: 규칙 칸을 위(40%)에, 결과 칸이 폭을 다 쓴다. 34%·min-width 300px 가 375px 에서 결과 칸을 35px 로 눌렀다 */
@media (max-width: 700px) {
  .trend { grid-template-columns: 1fr; grid-template-rows: auto; overflow: auto; }
  .main { flex-direction: column; }
  #rulesCol { width: auto; min-width: 0; flex: 0 0 40%; }
  .panes { min-height: 0; }
  .pane .dirbar input[type=text] { width: 100%; }
}
```
  `.trend` 규칙은 글자 그대로 옮긴다. 1400px 배치 불변(미디어 밖 규칙 안 건드림)
- TDD 예외(화면 HTML) — `node scripts/puppeteer/visual.js b72-b --only code_check` → ⑦ 통과·판정 0·콘솔 0(1-75 의 b72-red 가 RED). java 레인 `SmokeHtmlUnitTest`(code_check 여섯)·`ToolsFolderTest.toolsHaveNoLocalTheme` 초록

### V-52 — compare ㉒ · 정적 분석 · `--corpus` · `--db`(§3-5)
- `design/compare-2026-10-28.md` 끝에 `## ㉒ 재측정 — 번들 72(2026-11-03)`: 「설계 56. 이 번들로 올라간 후보 — ㉑ 「드러난 것」 셋(375px 둘 · 1-74)과 「후보로」 의 375px 둘 + 세트 변수 자유 글」 · 측정 표(1-74 픽스처 양성·음성 · 7-26 거절 글 · ⑥·⑦ 수치 전후 — b72-red 의 right·width 와 b72-a·b72-b 의 값 · javac 소스 수 그대로) · 정적 분석·벽시계(`mvn.sh -DskipTests verify` · `--corpus` · `--db`, ㉑ 대비) · 드러난 것 · 후보로(㉑ 잔여 + `POST /api/generate` 세트 IAE 500 + 「(오류)」 옵션에 글 없음 + `--db` CI)
- 순서(따로 낸다): `bash scripts/mvn.sh -DskipTests verify` rc 0 → `bash scripts/verify.sh` → `bash scripts/verify.sh --corpus` → `wsl --shutdown` · Docker Desktop → 여유 RAM ≥ 15GB 확인(모자라면 사용자에게 앱 내리기 요청 — 13GB 대에서도 초록이었다, ㉑) → `bash scripts/verify.sh --db` 혼자
- 골든 전부 diff 0 기대(`sqltables-diff`·`ddl-*`·`logical-summary`·`gen-hr`·`analyze-*`·`js-*`·`self-check.txt`) — 바뀌면 중단

## 4. 실행 스텝
### S0 — 설계 문서·행·번들 표 (**이 설계 세션이 한다**, Opus 는 건너뛴다)
- 대상: `design/56-bundle72.md`(A 절) · `PLAN.md` 「번들」 표 「번들 72」 행 + 분할표(1-74 행 고침 + `7-26`·`1-75`·`1-76`·`V-52` 새 행, B 절) · `PROGRESS.md`(C 절).
- 검증: `bash scripts/doc-lint.sh design/56-bundle72.md` · `bash scripts/verify.sh`(docs 레인) · `git commit -m "docs: S0 설계 56 — 번들 72"`. push 안 함.

### 1-74 — typed 몸 CHECK·FK 원문
- 대상: `core/gen/DdlPull.java`(`typedBody` 한 인자 · `typedOption` 한 줄 + 매처 셋) · `DdlReaderTest.typedOptionFkAndCheck`(§3-1).
- 순서: 시험 고쳐 RED(`status IN ('','')`) → 구현 GREEN.
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,InsertGenTest,SelfCheckTest'` 초록 · `git status` 에 골든 변화 없음 · `bash scripts/verify.sh` 초록 · 커밋 `fix: 1-74 typed 몸 CHECK·REFERENCES 는 원문으로 — 리터럴 보존`.
- 의존: S0.

### 7-26 — 세트 변수 검증
- 대상: `core/gen/TemplateSet.java`(`BOOL_VARS`·`checkBools`·`load` 한 줄·javadoc) · `TemplatesTest.boolVarsRefused`(§3-2).
- 순서: 시험 RED(IAE 안 남) → 구현 GREEN.
- 검증: `-Dtest='TemplatesTest,GenTemplatesTest,GeneratorTest,SelfCheckTest'` 초록 · 골든 diff 0 · `verify.sh` 초록 · 커밋 `fix: 7-26 세트 변수 valid·javaTime 은 "true"|"false" 만 — 세트 읽기에서 거절`.
- 의존: 1-74(독립 — 차례만).

### 1-75 — dev_tools 375px 프로필 줄
- 대상: `scripts/puppeteer/visual.js`(확인 ⑥·⑦) · `src/main/resources/tools/common.js`(한 줄)(§3-3).
- 순서: `bash scripts/mvn.sh -q package -DskipTests` → `visual.js` 에 ⑥·⑦ → `node scripts/puppeteer/visual.js b72-red --only dev_tools,code_check`(확인 실패 2 — 수치를 이력에) → `common.js` 한 줄 → `bash scripts/mvn.sh -q package -DskipTests` → `node scripts/puppeteer/visual.js b72-a --only dev_tools`(⑥ 통과).
- 검증: `-Dtest='SmokeHtmlUnitTest,ToolsFolderTest,BackendToolsTest'` 초록 · `out/visual/b72-a/summary.txt` 「확인 실패 0 · 콘솔 오류 0」 · `verify.sh` 초록 · 커밋 `fix: 1-75 프로필 고르기 폭 auto — dev_tools 375px 줄 넘침 + visual.js 375px 확인 둘`.
- 의존: S0.

### 1-76 — code_check 375px 결과 칸
- 대상: `src/main/resources/tools/code_check.html`(`:53` 미디어 블록)(§3-4).
- 순서: 블록 교체 → `bash scripts/mvn.sh -q package -DskipTests` → `node scripts/puppeteer/visual.js b72-b --only code_check`(⑦ 통과).
- 검증: `-Dtest='SmokeHtmlUnitTest,ToolsFolderTest'` 초록 · `out/visual/b72-b/summary.txt` 「판정 0 · 확인 실패 0 · 콘솔 오류 0」 · `verify.sh` 초록 · 커밋 `fix: 1-76 code_check 700px 아래 세로 배치 — 375px 결과 칸`.
- 의존: 1-75(⑦ 확인이 거기 들어간다).

### V-52 — compare ㉒ · 정적 분석 · `--corpus` · `--db`
- 대상: `design/compare-2026-10-28.md` ㉒(§3-5).
- 검증: 넷 초록 · 끝 줄이 더 부르는 레인 없음 · 벽시계 둘 · `doc-lint` · 커밋 `docs: V-52 재측정 — compare ㉒ + 정적 분석 + --corpus + --db`.
- 의존: 1-76.

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7 · 행의 축·강제 지점과 diff · `typedOption` 이 PK·NOT NULL·UNIQUE 는 사본·`COL_REF`·`checks` 는 원문인가(컬럼 가지와 같은가) · `TYPED_WORDS` 판정이 사본 그대로인가 · `checkBools` 가 부모·자식 이름을 맞게 내나 · 실제 세트 셋이 지나나 · `#tb-profile-sel` 규칙이 header 있는 화면 배치를 안 바꾸나 · 미디어 블록이 `toolsHaveNoLocalTheme` 다섯에 안 걸리나 · ⑥·⑦ 이 `--only` 를 따르나 · `self-check.txt`·PMD 짝 diff 0 · 골든 diff 0) → `bash scripts/gate-probe.sh` → `bash scripts/mvn.sh -DskipTests verify` → `bash scripts/verify.sh`(리뷰 고침 뒤 `--corpus`·`--db` 는 바뀐 레인만 다시) → `git push -u origin work/2026-11-03b` → `gh pr create --base main`(행마다 시작 세 줄 표 + 로컬 닫기 `9c1f8b3`·S0 같이 + ㉒ 핵심 수치) → CI 폴링(`gh api … check-runs`) → claude[bot] 처분 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → 닫기 커밋은 다음.

## 5. 금지 사항
- `pure/`·portfolio·`docs/`·`core/meta`·`core/dialect`·`core/profile`·`core/gen` 의 `DdlReader`·`FieldRoles`·`GenModel`·`Generator`·`Validation`·`TypeMapping`·`DtoGenerator` · `web/*`·`pom.xml`·`scripts/verify-fingerprint.sh`·`corpus/MANIFEST`·표본 · `common.css`·`dev_tools.html`·다른 화면 HTML · 템플릿 본문(`*.ftl`)·`set.yaml` 셋 수정 금지 · 새 Maven 의존성·새 화면·라우트·프로필 키·세트 변수 키 금지
- `DdlPull` — `TYPED_WORDS` 판정 입력(사본) 변경 금지 · `DdlReader.checks`·`COL_REF`·`QUOTED` 본문 수정 금지 · typed `DEFAULT` 값 읽기 금지 · 새 정규식 금지(SpotBugs REDOS — 필요하면 수량자 하나만)
- `TemplateSet` — 키 목록은 `valid`·`javaTime` 둘뿐(`rte`·`ee`·`sqlSession` 은 자유 글) · 대소문자 느슨하게 받기 금지(`FieldRoles`·`Validation` 의 `equals` 와 같은 엄격) · `load` 안에 분기 더하기 금지(도우미 호출 한 줄)
- `common.js` 는 한 줄 밖 수정 금지 · `code_check.html` 은 `<style>` 미디어 블록 밖 수정 금지(마크업·`code_check_ext.js` 불변) · 700px 밖 배치 변경 금지 · `:root`·`button{`·`.btn-*`·`.dl`·고정 색 금지
- `visual.js` — 확인 ⑥·⑦ 밖 수정 금지(샷·판정·a11y 불변) · 경로에 한글 금지
- 골든 전부 불변(`self-check.txt` 포함) · `-Dgolden.update` 금지 · 이력·compare 에 표본 코드 본문 금지 · 원본 되쓰기 API·CLI 금지 · `pmd-baseline.sh` 안 돌린다 · `--db` 와 다른 JVM 겹치기 금지 · `--corpus` → Docker → `--db` 순서 · `--db` 앞 여유 RAM 15GB(못 맞추면 사용자에게 앱 내리기 요청)

## 6. 최종 검증
- `bash scripts/mvn.sh -DskipTests verify` rc 0 · `bash scripts/verify.sh` · `--corpus` · `--db` 초록(순서대로, 따로) · `gate-probe.sh` 10/10
- 골든: 전부 불변(`git diff --stat origin/main -- src/test/resources/golden` 빈 것) · `self-check.txt` 불변
- 시험: `DdlReaderTest.typedOptionFkAndCheck` 양성·음성 · `TemplatesTest` +1(`boolVarsRefused` — 거절 셋·통과 둘) · `GenTemplatesTest`·`GeneratorTest`·`GeneratorJavacTest`(소스 40·javac 0) 초록 · `SmokeHtmlUnitTest`·`ToolsFolderTest`·`BackendToolsTest` 초록 · `ArchitectureTest`·`VerifyLanesTest` 초록
- 집 검증: `out/visual/b72-red/summary.txt` 확인 실패 2(⑥·⑦) · `b72-a` ⑥ 통과 · `b72-b` ⑦ 통과·판정 0·콘솔 0 — 수치(right·width 전후)를 이력에
- 수동(한 번, 이력에 한 줄): `run.bat` → 375px(개발자 도구 기기 모드) dev_tools 「프로필 … 접속」 한 줄 · code_check 「폴더 검사」 버튼 제 폭·규칙 칸 접기 뒤 결과 칸 전체 · 1400px 두 화면 배치 그대로 · `egov35/set.yaml` 의 `javaTime` 을 `"flase"` 로 잠깐 바꿔 소스 생성 화면 세트 고르기에 `egov35 (오류)` 가 뜨는지(되돌린다 — 커밋 금지)
- compare ㉒ 표 + 「드러난 것」 + 「후보로」 · 이력에 건수·벽시계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- 1-74 가 골든(`ddl-*`·`sqltables-diff`·`logical-summary`·`gen-hr`)을 바꿈(표본에 typed CHECK 가 있었다는 뜻) · 1-74 음성(u.pk null)이 RED(사본 매칭이 안 산다)
- 7-26 에서 `GenTemplatesTest`·`GeneratorTest`·`GeneratorJavacTest` 가 세트 거절로 빨강(실제 세트 값이 목록 밖 — 그 값을 보고)
- 1-75 뒤 ⑥ 이 그대로 실패(원인이 `select{width:100%}` 가 아니었다) · 1-76 뒤 ⑦ 이 그대로 실패 또는 1400px 판정 새로 생김 · `toolsHaveNoLocalTheme` 빨강
- `self-check.txt` 변화 · `pmd:check`·SpotBugs 새 짝 · `ArchitectureTest`·`VerifyLanesTest` 빨강
- `verify.sh --db` 메모리로 끊김 — 고아 JVM 끄고 한 번 더, 또 끊기면 보고
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① b72-red 의 ⑥·⑦ 수치(right 는 375 넘김·width 는 35 안팎 기대) ② `--corpus`·`--db` 벽시계. 첫 스텝 전: `bash scripts/verify.sh` 초록 · `git log --oneline -1`(S0 커밋, 가지 `work/2026-11-03b`) · `git branch --list 'wip/*'` 빈 것 · Docker Desktop 상태 · node 있음 · `C:/workspace/node_modules/puppeteer` 있음
- **사용자만 정할 수 있는 것** — 없음(Java 쪽 기준 · 번들 하나·PR 하나 · 머지 위임 (가) 전부 2026-11-03 결정)
