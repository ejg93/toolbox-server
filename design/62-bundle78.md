# 설계 62 — 번들 78: 되돌림 안전망 보정 · th scope 고침 · 인코딩 고침 · 잔손 (Fable 설계 · Opus 실행, 2026-11-10)

## 맥락
번들 77 이 남긴 잔손 셋(0-80 · 5-76 · 5-77)과 compare ③ 표의 코드 정리 B 후보 가운데 실측으로 고른 둘(`a11y.thScope` · `file.encoding`)을 한 번들로 닫는다(사용자 2026-11-10 — 범위 1+2). 레인 — `core/check`·`resources/check`·`resources/tools` 는 java+corpus · `web/CleanupRoutes` 는 java · `scripts/hooks-test.sh` 는 tools. db 레인은 안 건드린다 → 끝에 `--corpus` 한 번(`--db` 없음).

**보정(2026-11-10, `768fbae` 트리 — 행 글과 실제가 다른 자리)**
- **5-76 원인(실측)** — `target/app.jar` CLI 로 zheng `DemoController` 폴더를 돌렸다. `check --groups java`: 17줄 `java.unusedImport`(`RestController`, 원래 있던 것) · 39·46줄 `java.unusedLocal`. `cleanup --rules java.unusedLocal`(R-24 와 같은 호출) → `changed=false`, skip `local: 다른 지적이 사라짐: java.unusedImport`. `cleanup`(기본 규칙) → `delete/java.unusedImport 1→0 · local/java.unusedLocal 2→0`, 정상. 까닭은 `Cleanup.worse:427-436` — `MAY_APPEAR` 규칙(`local → java.unusedImport`, `delete → common.unusedIgnore`)을 **after 쪽에서만** 통째로 빼고(`continue`) before 쪽 `kb` 에는 남긴다. 원래 있던 지적이 그대로 남아도 after 에서 빠지니 「사라짐」 이 된다. `wanted` 에 `java.unusedImport` 가 없으면(화면에서 체크를 풀거나 `--rules` 로 하나만) delete 단계가 안 돌아 그 지적이 남아 있고, 그때 local 단계가 전부 되돌려진다. `LocalsAst`·`Imports.used` 는 무관(둘 다 안 지나는 길). 같은 비대칭이 delete 단계에도 있다 — 픽스처 `guards/next.ts`(`tb-ignore-next tsx.console` 아래 `debugger;`)가 지금 「사라짐: common.unusedIgnore」 로 되돌려지는데, 고친 뒤에는 **같은 파일이 「사라짐: tsx.console」 로 되돌려진다**(debugger 줄을 지우면 표식이 `console.warn` 줄에 붙어 그 지적이 사라진다 — 되돌림이 맞고 사유만 바뀐다). 골든 `steps.json` 그 항목의 reason 한 줄만 바뀐다(글 불변)
- **5-77 전제 틀림** — `Cleanup.editStage:229-233` 의 `targets = targets(st.cur, ids)` 는 **지금 지적에 있는 규칙만** 담는다(`targets():324-332`). `MarkupFix.apply:43` 의 `targets.contains(BLANK_JSP)` 는 그 파일에 `jsp.blankNoopener` 지적이 있을 때만 참이라 `tagRel` 은 이미 jsp 지적 있는 파일만 훑는다. Java·TSX 파일에서는 안 돈다. 행이 말한 `byLine` 조건과 같은 뜻 → **코드 변경 없이 닫는다**(S0 가 분할표 상태를 「닫음 — 전제 틀림」 으로)
- **0-80** — `hooks-test.sh` 에서 따옴표 없는 `$R/` 은 `:6` `H=$R/scripts/hooks` 하나(레인 안). `${R}` 꼴 0. 새 무늬 `\$R/[A-Za-z0-9_./-]+` 는 자기 자신(`:473` 의 `\$R/[A-Za-z…` — `[` 가 글자 집합 밖)·`:469` 주석(`$R/…` — `…` 밖)에는 안 걸린다
- **`a11y.thScope` 오탐** — ruoyi-classic 132 는 **전부 Thymeleaf `<th:block`**(`params.when: '<th\b'` 가 `<th:` 에 걸린다). 실제 `<th` 가 있는 html 8 파일은 scope 가 있다 → `when` 을 `'<th(?=[\s/>])'` 로. `pure-rules.json` 은 kind regex 만 담아 diff 0(`PureRulesJsonTest:32-35`). 픽스처 `check/a11y/pos.jsp:10` `<table><tr><th>제목</th></tr></table>` 그대로 걸린다
- `Cleanup` 메서드 29 · `metric.classMethods` 는 `v > max(30)` 라 **30 까지 허용**(`JavaRules:554` 꼴) → 단계 하나(`encodingStage`)를 더할 수 있다. 그 뒤로는 `Cleanup` 에 메서드를 못 더한다
- `CleanupRoutes.register` 복잡도 16 이 `self-check.txt:119` 기준선 — 라우트 람다에 분기를 더하면 17 로 새 지적 → `expected` 는 도우미 메서드로
- `LocalFiles.read:280-305` 가 아는 인코딩은 `UTF-8-BOM`·`UTF-8`(엄격)·`MS949`(나머지 전부, `new String(b, MS949)` — 못 읽는 바이트는 U+FFFD) 셋 · `encode:414-425` 가 쓰는 이름도 그 셋(아니면 `Refused 400`). `FileRule.norm` 은 EUC-KR·CP949 → MS949, UTF8 → UTF-8. `RuleSet.encoding()` 이 프로필 `project.encoding`(없으면 null)
- `CleanupRunner.apply:183-184` 가 `t.encoding()`(읽은 인코딩)으로 쓴다 · `outHash:277` 은 lineEnding+text 뿐 · `FileRow:34` 에 `lineEnding` 은 있고 encoding 은 없다 · 화면 `code_cleanup_ext.js:45` 는 `file.lineEnding` 만 `expected` 없음 처리
- 우리 저장소: BOM 파일은 `dict/*.csv`·`golden/logical/*.csv`·`sample/logical/*.csv` 뿐(`file.encoding` globs 밖) · `tools/*.html` 의 `<th` 는 전부 scope 있음 · `self-check.txt` 에 `a11y.thScope` 0 → `self-check.txt`·`self-cleanup.txt` 불변
- `.gitattributes` `* text=auto eol=lf` — MS949 픽스처는 NUL 이 없어 글로 보고 개행만 LF 로 맞춘다(바이트 보존) · BOM 은 보존(logical csv 전례)

**실측(같은 트리 — 표본 12: egov·egov-prev·egov-homepage·egov-portal·egov-enterprise·jeecg·jeesite·jspwiki·roller·zheng·shopizer·ruoyi-classic. 글자 훑기 node 스크립트 + `golden/corpus/check-*.json` 건수. 정확한 수는 V-58a 가 준다)**

| 규칙 | 표본 지적(check 골든) | 꼴 실측 | 처분 |
|---|---|---|---|
| `a11y.thScope` | egov 478 · ruoyi-classic 132 · jeesite 36 · jeecg 18 · roller 11 | egov 470 파일: thead 안만 92 · 첫 tr 만 22 · 행마다 th(행 머리, `<tr><th>항목</th><td>값</td>…`) 330 · 섞임 ~20 · jeesite 36(thead 32·첫 tr 4) · roller 11(첫 tr) · jeecg 18(thead 11·행 머리 4·섞임 2·첫 tr 1) · ruoyi 132 전부 `<th:block` 오탐 · ruoyi `<table` 없는 조각 66 | **올림**(5-78) + `when` 고침 |
| `file.encoding` | egov 211 · zheng 45 · egov-prev 19 · jeesite 1 | UTF-8-BOM 273 · MS949 4(한글 우세 · U+FFFD 0) | **올림**(5-79) |
| `a11y.nonStandardDom` | egov 48 · jeecg 1 | `document.all["Form"]` 류 — 같은 파일에 `id="…"` 증거 0 · `<form name>` 만 14 · `document.all` 단독(브라우저 판별) 20 · 같은 파일에 없음(jspf) 19 | 내림 — getElementById 로 바꿀 근거 0 |
| `file.mixedIndent` | 3,200 파일(egov 1,877 · egov-prev 1,813 …) | 다수 탭 76% · 공백 폭 2·4 배수 아님 46% · 소수 비율 ≥5% 68% | 내림 — 폭을 못 정함 · 전체 줄이 바뀌어 형상 diff 노이즈(포매터 몫) |
| `jsp.expression` | egov 223 · jspwiki 284 · zheng 48 · jeecg 43 · roller 11 · jeesite 5 | `<%= request.getParameter("x") %>` 단독 꼴 **0** · 맨 이름 576 · 호출 363(`LocaleSupport.getLocalizedMessage` 48 · `e.getMessage()` 25 …) · `request.getContextPath()` 16 | 내림 — param 꼴이 없다 |
| `jsp.escapeXmlFalse` | egov 15 · jspwiki 2 | 전부 `<c:out value="${egovc:sanitizeHtml(…)}" escapeXml="false"/>`(12) · jspwiki `${fact.value}`(2) — 의도된 HTML 출력 | 내림 — 속성을 지우면 화면이 깨진다 |
| `java.serviceName` | egov 69 | — | 기본 제외(프로그램 분석 그래프 필요) |

**범위 밖** — nonStandardDom·mixedIndent·expression·escapeXmlFalse·serviceName 고침 · `hooks-test.sh` 의 `cd "$R" && …` 상대 읽기 잡기 · `LocalsAst`·`Imports` · `BlankRelRule` 판정 · `tsx.blankNoopener` · `a11y.thScope` 의 `scope="colgroup|rowgroup"` · `<th>` 밖 `<td headers>` 짝 · 인코딩 판별 자체(`LocalFiles.read` 불변 — GBK 등 다른 코드페이지 판별 없음) · `file.lineEnding`·`eol` 단계 · 5-31·2-17·보류·조건부 행

## 사용자 결정(2026-11-10)
1. 범위 — 1+2(잔손 셋 + 코드 정리 B 둘)
2. 올릴 꼴 — `a11y.thScope`(col·row + 오탐 `when` 고침) · `file.encoding`. nonStandardDom·mixedIndent·jsp.expression·jsp.escapeXmlFalse 는 후보에서 내림(근거는 위 표) · `java.serviceName` 기본 제외
3. 새 고침 둘 다 **기본 켬**(`OPTIONAL` 불변 — 넷 그대로). 체크박스 22 · 켜진 18
4. 설계 Fable · 실행 Opus · **머지 위임 있음**

설계가 정한 것(전부 [고정]):
- 5-77 은 코드 없이 닫는다(전제 틀림 — 위 보정)
- 5-76 고침은 `worse` 한 자리 — `MAY_APPEAR` 규칙을 **before 쪽에서도** 뺀다(생겨도 되는 규칙은 비교에서 아예 빠진다). 다른 규칙의 사라짐 판정은 그대로라 `guards/next.ts` 는 계속 되돌려진다(사유만 `tsx.console`)
- thScope 판정 — thead 안 th → `col` · thead 밖에서 첫 행(그 표의 첫 `<tr>`)이 th 만이고 thead 에 th 가 없으면 그 행 th → `col` · td 가 있는 행에서 첫 셀이 th 이면 그 행의 th 전부 → `row`(egov 상세 표 `<th>a</th><td/><th>b</th><td/>` 둘 다 row) · 그 밖(th 만인 행이 첫 행이 아님 · td 뒤에 th · tr 없는 표)은 **표째 건너뜀**(사유) · `<table` 밖 th 는 파일에 한 번 사유 · 이미 scope·headers 가 있는 th 는 안 건드림 · 중첩 표는 안쪽이 자기 표로 판정 · 주석·script·스크립틀릿·EL 안은 안 본다(`BlankRelRule.dead`) · `scope` 값은 항상 큰따옴표, 자리는 `<th` 바로 뒤
- `MarkupFix` 는 rel 과 scope 끼우기를 **한 목록으로 모아 글 자리 내림차순으로 한 번에** 끼운다(같은 줄에 둘이 있으면 앞 삽입이 뒤 자리를 밀기 때문). `tagRel` 의 `out` 쓰기를 삽입 목록으로 바꾼다 — 기존 `markup-blank.jsp.txt` 골든 불변이 그 재배치의 강제 지점
- encoding 고침은 **글을 안 바꾸고 쓸 인코딩만** 바꾼다 — `Cleanup.Result.encoding` 을 새로 두고 `CleanupRunner` 가 그것으로 쓴다. 쓸 수 있는 이름은 `LocalFiles` 가 받는 셋(UTF-8·UTF-8-BOM·MS949, `FileRule.norm` 으로 맞춤) · 소스를 MS949 로 읽었는데 글에 U+FFFD 가 있으면 건너뜀(판별을 못 믿는다) · 목표가 MS949 인데 못 적는 글자면 건너뜀(`LocalFiles.checkEncodable`) · 프로필 인코딩이 셋 밖이면 건너뜀. 단계 이름 `encoding`, 차례는 맨 끝(⑩, eol 뒤)
- `Cleanup.RULES` 끝에 `"a11y.thScope", "file.encoding"`(22) · `DEFAULT` 18 · `OPTIONAL` 불변
- 골든 `steps.json` 의 `row()` 에 `encoding` 키를 **더하지 않는다**(항목 전부가 바뀌는 diff 를 피한다) — `encodingStage` 단위 시험이 `r.encoding()` 을 단언한다. `fixturesGolden:67` 의 changed 단언에는 encoding 비교를 더한다

## 1. 목표와 범위
- 한 문장: 코드 정리가 「일부 규칙만 고르면 호출만 남기기가 통째로 되돌려지던」 안전망 비대칭을 고치고, JSP·HTML 표의 th 에 scope 를 달고, 파일을 프로필 인코딩으로 다시 적으며, 번들 77 잔손 셋을 닫는다.
- 완료 시 체감: 코드 정리 화면에서 `java.unusedLocal` 만 체크해도 zheng `DemoController` 의 선언 둘이 `aliyunOssClient.putObject(…);` 로 바뀐다(되돌림 없음). 체크박스 22(켜진 18). egov 상세 JSP 미리보기에서 `<th scope="row">`·목록 JSP 에서 `<th scope="col">` 이 보인다. BOM 있는 파일이 프로필 UTF-8 로 결과 폴더에 BOM 없이 써진다(글 동일). 코드 검사에서 Thymeleaf `<th:block` 파일에 `a11y.thScope` 가 안 뜬다. `hooks-test.sh` 자기 검사가 따옴표 없는 `$R/` 읽기도 잰다.
- In scope: `core/check` — `Cleanup`(`worse` 한 줄 · `RULES` · `Result`·`State` · 새 `encodingStage`) · `MarkupFix`(삽입 목록 재배치 + scope) · 새 `ThScopeFix` · `BlankRelRule.attrs` 가시성 · `CleanupRunner`(`FileRow.encoding`·`outHash`·쓰기 둘) · `web/CleanupRoutes`(`expected` 도우미) · `resources/check/rules.yaml`(`a11y.thScope` `when`) · `tools/code_cleanup_ext.js`(한 줄) · `scripts/hooks-test.sh`(한 줄) · 픽스처·시험·골든(행마다) · `design/compare-2026-10-28.md` ㉗ 처분·㉘ · `PLAN.md`·`PROGRESS.md`
- Out of scope: 「범위 밖」 전부 · `pure/` · `LocalFiles.read`·`encode`(판별·쓰기 규칙) · `FileRule` 판정 · `Cleanup` 의 `commit`·`targets`·단계 차례(끝에 하나 더하는 것 밖) · `TOUCHED_MAY_VANISH`·`MAY_APPEAR` 내용 · `LocalFix`·`LogFix`·`ValueFix`·`OrderFix`·`ResourceFix` · `BlankRelRule` 판정·`tags` · 정규식 규칙의 `regex`·`globs`·`message`(`when` 한 자리 밖) · `core/meta`·`core/dialect`(db 레인) · 새 의존성

## 2. 현재 구조 요약
「맥락」 의 보정·실측이 요약이다. 덧붙이는 함정:
- **행마다 `bash scripts/mvn.sh -q -DskipTests verify` 를 먼저** 돌린다 — 빠른 verify 는 PMD·SpotBugs 를 안 본다. 새 클래스·메서드는 PMD 기준선 밖: `AvoidLiteralsInIfCondition`(수·글자는 상수로) · `UseLocaleWithCaseConversions` · `LiteralsFirstInComparisons` · `LooseCoupling`(`Deque`·`List` 로 선언) · 시험 `AvoidDuplicateLiterals`(같은 글 넷 이상은 상수). 정규식은 SpotBugs REDOS — 중첩 반복·`.*?` 금지(블록은 `indexOf`, `BlankRelRule.spaceOut` 전례)
- `Cleanup.Edits.replace(줄, 글)` 은 첫 쓰기가 이긴다 → `MarkupFix` 는 줄마다 글을 다 합친 뒤 한 번 쓴다(지금 `out` 맵 꼴). 한 줄에 rel 과 scope 가 같이 들면 **글 자리(`at`) 내림차순**으로 끼워야 앞 삽입이 뒤 자리를 안 민다
- `Cleanup.worse` — 고친 줄 위 지적은 규칙 이름 다중집합, 다른 줄은 (규칙, 발췌). `markup` 단계는 `TOUCHED_MAY_VANISH` 에 없다 → scope 를 끼운 줄의 다른 지적은 그대로여야 한다(글을 끼우기만 하니 그대로다). `file.*` 는 있음·없음만 — `encoding` 단계에서 `file.encoding` 은 target 이라 비교에서 빠진다
- `a11y.thScope` 는 kind `absent`(`AbsentRule:41-58`) — 파일에 `scope=|headers=` 가 **한 번도 없을 때** 첫 `when` 줄에 한 건. 하나만 끼워도 지적이 사라진다 → 고침은 파일의 판정 가능한 표 전부를 한 번에 끼운다(건너뛴 표는 사유로 남는다). 지적 줄에 `tb-ignore` 가 있으면 지적이 없어 `targets` 에 안 들고 고침도 안 돈다
- `CleanupTest.fixturesGolden:52-70` 은 `fixtures/cleanup/**` 전부(`order/` 밖)를 `Cleanup.RULES` 로 돌려 `cleanup/<dir>-<파일>.txt` 골든과 `steps.json` 을 쓴다 → 새 픽스처는 골든 둘을 새로 만든다(`-Dgolden.update=true -Dtest=CleanupTest`). 기존 cleanup 픽스처에 `<th` 는 없다(check `a11y/pos.jsp`·`neg.jsp` 만) → 기존 cleanup 골든은 5-76 의 `steps.json` 한 줄 밖 불변
- `CleanupTest.cleanup(groups)`·`profile(groups)` 는 프로필 `UTF-8`·`LF` · `cleanupIn(encoding)` 은 인코딩을 바꿔 준다(`:192-195`) — encoding 거절 꼴 시험에 쓴다
- `CleanupCorpusTest` 는 `CheckCorpusTest.rules(egov)`(프로필 `UTF-8`·`LF`, 묶음 전부 켬) + `Cleanup.RULES` 로 돈다 · 골든 `cleanup-{commons,egov,egov-react,jspwiki,roller}.json` 은 `changed`·`files`·`skips`(사유별 수 — 「다른 지적이 생김·사라짐」 은 규칙 이름을 뗀 키)·`steps`(단계별 파일 수) · egov 묶음 = egov·egov-portal·egov-enterprise·egov-homepage
- `CheckCorpusTest` 골든 `check-*.json` 은 규칙별 `count`·`files` → `when` 고침은 `check-ruoyi-classic.json` 의 `a11y.thScope` 132 → 0(실제 `<th` 8 파일은 scope 있음)만 바꾼다
- `CleanupRoutesTest:107` 20 · `SmokeHtmlUnitTest:1885-1886` 20·16 — 체크박스 수를 건다 · `CleanupRoutesTest:118` opt 넷 불변
- Bash 도구의 heredoc 은 `\` 를 벗긴다 → 셸 스크립트 고침은 **Edit 도구로**. BOM·MS949 픽스처 바이트는 PowerShell 로 만든다(아래 §3-5)
- 레인: `core/check`·`resources/check`·`resources/tools`·`golden/corpus` = java+corpus · `fixtures/**`·`web/` = java · `scripts` = tools · `docs`·`PLAN.md` = docs · `design/**` = 없음. db 레인 변경 없음 → `--db` 안 돈다

## 3. 설계 결정 — 전부 [고정]

### 0-80 — hooks-test 자기 검사 무늬(§3-1)
- `scripts/hooks-test.sh:473` 한 줄(Edit 도구):
```bash
for p in $(grep -oE '\$R/[A-Za-z0-9_./-]+' "$R/scripts/hooks-test.sh" | sed 's#^\$R/##' | sort -u) .gitignore; do
```
  (따옴표 유무와 상관없이 `$R/<경로>` 를 전부 잰다. `:6` `H=$R/scripts/hooks` → `scripts/hooks` ⊂ `scripts`)
- `:469` 주석을 「이 시험이 읽는 저장소 파일(`$R/…`, 따옴표 유무 무관)은 전부 tools 레인 경로 아래다 …」 로
- **RED 확인 절차(이력에 한 줄)**: 임시로 `:468` 뒤에 `x=$(cat $R/design/README.md 2>/dev/null)` 를 넣고 `bash scripts/hooks-test.sh` → 마지막 경우 `[실패] … tools 레인 밖 경로를 읽는다: design/README.md` 확인 → 줄 지움 → 통과. 지문이 남지 않게 `git diff --stat` 로 `hooks-test.sh` 한 파일만

### 5-77 — 닫기(§3-2)
- 코드 없음. S0 가 분할표 5-77 상태를 「닫음(설계 62, 2026-11-10) — 전제 틀림: `Cleanup.targets` 는 지적 있는 규칙만 담아 `tagRel` 은 이미 jsp 지적 있는 파일만 돈다」 로 고친다

### 5-76 — `worse`: 생겨도 되는 규칙은 비교에서 아예 뺀다(§3-3)
- `Cleanup.worse:417-420` before 루프의 첫 줄:
```java
        for (Finding f : before) {
            if (skipInCompare(f, targets) || mayAppear.contains(f.rule())) {
                continue; // 5-76 — 생겨도 되는 규칙은 before 에서도 뺀다. 원래 있던 것이 그대로 남아도 「사라짐」 이 아니다
            }
```
- `MAY_APPEAR:86` 주석을 「단계가 새로 세워도 되는 지적 — 비교에서 아예 뺀다(이미 있던 것이 남아도 사라짐이 아니다, 5-76). local 은 …」 로. `worse` 복잡도 35 는 그대로(조건 하나는 같은 식 안 `||`)
- `CleanupTest.worseRevertsStage` 끝에 **먼저 더해 RED**:
```java
        // 5-76 — 이미 있던 생겨도-되는 규칙(unusedImport·unusedIgnore)이 그대로 남은 것은 사라짐이 아니다
        List<Finding> lb = List.of(f(3, "java.unusedImport", "import a.B;"), f(9, "java.unusedLocal", "int n = f();"));
        assertNull(Cleanup.worse(Cleanup.LOCAL, Set.of("java.unusedLocal"), lb, List.of(f(3, "java.unusedImport", "import a.B;")), Set.of(9),
                Set.of(9)), "원래 있던 unusedImport 가 남았다");
        List<Finding> db = List.of(f(2, "tsx.debugger", "debugger;"), f(7, "common.unusedIgnore", "tb-ignore x.y"));
        assertNull(Cleanup.worse(Cleanup.DELETE, Set.of("tsx.debugger"), db, List.of(f(6, "common.unusedIgnore", "tb-ignore x.y")), Set.of(2),
                Set.of()), "원래 있던 unusedIgnore 가 남았다");
```
- 새 시험 `CleanupTest.localOnlyKeepsOtherUnusedImport`(RED → GREEN):
```java
    /** 5-76 — java.unusedLocal 만 고르면 delete 가 안 돌아 Date import 지적이 남는다. 그래도 local 단계는 적용된다(되돌림 없음) */
    @Test
    void localOnlyKeepsOtherUnusedImport(@TempDir Path data) throws Exception {
        Cleanup c = new Cleanup(RuleSet.load(profile(null), null), List.of(LocalsAst.UNUSED_LOCAL), "LF");
        Cleanup.Result r = c.clean(source("local", "Pos.java", data));
        assertTrue(r.changed() && r.text().contains("        xs.size();\n"), r.text());
        assertEquals(List.of(new Cleanup.Step(Cleanup.LOCAL, LocalsAst.UNUSED_LOCAL, 18, 9)), r.steps());
        assertEquals(List.of(), skipsOf(r, Cleanup.LOCAL).stream().filter(k -> k.startsWith("다른 지적이")).toList());
        assertEquals(1, r.before().stream().filter(x -> x.rule().equals("java.unusedImport")).count(), "Date import 지적은 처음부터 있다");
        assertEquals(1, r.after().stream().filter(x -> x.rule().equals("java.unusedImport")).count(), "그대로 남는다");
    }
```
  (`r.steps()` 전체가 local 하나 — 다른 단계는 wanted 밖이라 안 돈다)
- 골든: `-Dgolden.update=true -Dtest=CleanupTest` → diff 가 **`steps.json` 의 `guards/next.ts` reason 한 줄**(`common.unusedIgnore` → `tsx.console`)뿐. `guards-next.ts.txt` 불변
- 표본 기대(V-58a): `cleanup-*.json` 의 「다른 지적이 사라짐」 키는 지금 0 이라 불변. R-25 가 R-24 의 여덟 파일을 CLI 로 다시 잰다

### 5-78 — `a11y.thScope` 고침 — 새 `ThScopeFix` + `when` 오탐(§3-4)
- `rules.yaml` `a11y.thScope` 의 `params: {when: '<th\b'}` → `params: {when: '<th(?=[\s/>])'}` 주석 한 줄 「설계 62 — `<th:block`(Thymeleaf) 은 th 가 아니다」. `message`·`fix`·`regex`·`globs` 불변
- `BlankRelRule:117` `private static String attrs` → `static String attrs`(javadoc 끝에 「ThScopeFix 도 쓴다」)
- 새 파일 `core/check/ThScopeFix.java`:
```java
package kr.ejg.toolbox.core.check;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * a11y.thScope 고침(설계 62) — JSP·HTML 표의 {@code <th>} 에 scope. thead 안 th 와 첫 행만 th 인 행의 th 는 col, th 뒤에 td 가 오는 행(상세 표)의 th 는 row.
 * 그 밖(th 만인 행이 첫 행이 아님 · td 뒤에 th · tr 없는 표)은 표째 건너뛴다. 중첩 표는 안쪽이 자기 표로 판정. 주석·script·스크립틀릿·EL 안은 안 본다(BlankRelRule.dead)
 */
final class ThScopeFix {
    static final String TH_SCOPE = "a11y.thScope";
    static final String COL = "col";
    static final String ROW = "row";
    static final String NOT_FIRST = "th 만 있는 행이 첫 행이 아니다 — 표 구조를 못 가름";
    static final String MID_ROW = "td 뒤에 th — 표 구조를 못 가름";
    static final String NO_ROW = "tr 없는 표";
    static final String OUTSIDE = "table 밖 th(조각)";
    private static final Pattern TOKEN = Pattern.compile("(?i)<(/?)(table|thead|tr|th|td)(?=[\\s/>])");
    private static final Pattern SCOPED = Pattern.compile("(?i)(?<![\\w:-])(?:scope|headers)\\s*=");
    private static final String TABLE = "table";
    private static final String THEAD = "thead";
    private static final String TR = "tr";
    private static final String TH = "th";

    private ThScopeFix() {
    }

    /** 끼울 자리 — at 은 {@code <th} 바로 뒤 글 자리 */
    record Th(int at, String scope) {
    }

    /** 결과 — 끼울 자리(글 차례) + 건너뜀 사유(「줄 N: 사유」) */
    record Scan(List<Th> ths, List<String> skips) {
    }

    private static final class Cell {
        final int at;
        final boolean th;
        final boolean scoped;
        // 생성자
    }

    private static final class Row {
        final boolean inThead;
        final List<Cell> cells = new ArrayList<>();
    }

    private static final class Table {
        final int line;
        final List<Row> rows = new ArrayList<>();
        boolean inThead;
        boolean cellOutsideRow;
    }

    static Scan scan(String text, String lang) {
        String code = BlankRelRule.dead(text, lang);
        List<Table> tables = new ArrayList<>();
        Deque<Table> open = new ArrayDeque<>();
        List<String> skips = new ArrayList<>();
        Matcher m = TOKEN.matcher(code);
        int outside = -1; // table 밖 첫 th 의 줄(사유는 표 사유 뒤에 한 번)
        while (m.find()) {
            boolean close = !m.group(1).isEmpty();
            String tag = m.group(2).toLowerCase(java.util.Locale.ROOT);
            Table cur = open.peek();
            if (TABLE.equals(tag)) {
                if (close) { if (cur != null) { tables.add(open.pop()); } } else { open.push(new Table(lineOf(code, m.start()))); }
            } else if (cur == null) {
                if (TH.equals(tag) && !close && outside < 0) { outside = lineOf(code, m.start()); }
            } else if (THEAD.equals(tag)) {
                cur.inThead = !close;
            } else if (TR.equals(tag)) {
                if (!close) { cur.rows.add(new Row(cur.inThead)); }
            } else if (!close) { // th·td
                if (cur.rows.isEmpty()) { cur.cellOutsideRow = true; }
                else { cur.rows.get(cur.rows.size() - 1).cells.add(cell(code, m, TH.equals(tag))); }
            }
        }
        while (!open.isEmpty()) { tables.add(open.pop()); } // 안 닫힌 표(JSP 조각)도 판정
        List<Th> out = new ArrayList<>();
        for (Table t : tables) { classify(t, out, skips); } // 표는 닫힌 차례(안쪽 표가 바깥보다 먼저)
        if (outside > 0) { skips.add("줄 " + outside + ": " + OUTSIDE); }
        out.sort(java.util.Comparator.comparingInt(Th::at));
        return new Scan(out, skips);
    }

    private static Cell cell(String code, Matcher m, boolean th) {
        String attrs = th ? BlankRelRule.attrs(code, m.end()) : null;
        return new Cell(m.end(), th, attrs != null && SCOPED.matcher(attrs).find());
    }

    /** 표 하나 — 판정 못 하는 행이 하나라도 있으면 표째 건너뛴다 */
    private static void classify(Table t, List<Th> out, List<String> skips) {
        if (t.cellOutsideRow) { skips.add("줄 " + t.line + ": " + NO_ROW); return; }
        boolean theadTh = t.rows.stream().anyMatch(r -> r.inThead && r.cells.stream().anyMatch(c -> c.th));
        Row firstBody = t.rows.stream().filter(r -> !r.inThead).findFirst().orElse(null);
        List<Th> mine = new ArrayList<>();
        for (Row r : t.rows) {
            String scope = rowScope(r, r == firstBody && !theadTh);
            if (scope == null) { skips.add("줄 " + t.line + ": " + (hasTd(r) ? MID_ROW : NOT_FIRST)); return; }
            r.cells.stream().filter(c -> c.th && !c.scoped).forEach(c -> mine.add(new Th(c.at, scope)));
        }
        out.addAll(mine);
    }

    /** 행의 th 에 줄 scope — thead 안·첫 행 th 만 = col · th 뒤 td = row · th 없음 = col(빈 값, 안 쓰임) · 못 가름 = null */
    private static String rowScope(Row r, boolean firstBodyRow) {
        boolean th = r.cells.stream().anyMatch(c -> c.th);
        if (!th || r.inThead) { return COL; }
        if (!hasTd(r)) { return firstBodyRow ? COL : null; }
        return r.cells.get(0).th ? ROW : null;
    }

    private static boolean hasTd(Row r) { return r.cells.stream().anyMatch(c -> !c.th); }

    private static int lineOf(String code, int at) {
        return 1 + (int) code.substring(0, at).chars().filter(c -> c == '\n').count();
    }
}
```
  (스니펫의 한 줄 `if { }` 는 실행자가 저장소 꼴(중괄호 줄바꿈)로 편다. `scan` 복잡도 ≤ 15 가 안 되면 토큰 처리 `while` 몸을 `private static void token(…)` 으로 뺀다. 메서드 ≤ 9)
- `MarkupFix` — 삽입 목록 재배치 + scope:
```java
    static final String SCOPE_ATTR = " scope=\"";
    /** 글 자리에 끼울 조각 — rel·scope 를 한 목록으로 모아 at 내림차순으로 끼운다(같은 줄의 앞 삽입이 뒤 자리를 안 밀게) */
    record Insert(int at, String text) {
    }

    static void apply(Source s, List<Finding> cur, Set<String> targets, Cleanup.Edits e, List<Cleanup.Skip> skips) {
        String[] lines = s.text().split("\n", -1);
        Map<Integer, Set<String>> byLine = new TreeMap<>();
        cur.stream().filter(f -> targets.contains(f.rule())).forEach(f -> byLine.computeIfAbsent(f.line(), k -> new TreeSet<>()).add(f.rule()));
        Map<Integer, String> out = new TreeMap<>();
        List<Insert> ins = new ArrayList<>();
        if (targets.contains(BLANK_JSP)) {
            ins.addAll(rels(s, byLine, skips));
        }
        if (targets.contains(ThScopeFix.TH_SCOPE)) {
            ins.addAll(scopes(s, skips));
        }
        insertAll(s.text(), lines, ins, out);
        byLine.forEach((no, rules) -> { /* 지금 그대로 */ });
        out.forEach((no, text) -> { /* 지금 그대로 */ });
    }

    /** jsp.blankNoopener — 지적이 선 태그마다 target 값 바로 뒤 rel(값과 같은 따옴표). 지금 tagRel 의 몸에서 out 쓰기 대신 Insert 를 모은다 */
    private static List<Insert> rels(Source s, Map<Integer, Set<String>> byLine, List<Cleanup.Skip> skips) {
        List<Insert> out = new ArrayList<>();
        for (BlankRelRule.Tag t : BlankRelRule.tags(s.text(), s.lang())) {
            if (!byLine.getOrDefault(t.line(), Set.of()).contains(BLANK_JSP)) {
                continue; // 억제 표식으로 눌린 태그
            }
            if (t.quote() == 0) {
                skip(skips, t.line(), BARE);
                continue;
            }
            out.add(new Insert(t.at(), " rel=" + t.quote() + REL_WORDS + t.quote()));
        }
        return out;
    }

    /** a11y.thScope — ThScopeFix 가 준 자리에 scope. 표마다의 건너뜀 사유를 그대로 옮긴다 */
    private static List<Insert> scopes(Source s, List<Cleanup.Skip> skips) {
        ThScopeFix.Scan sc = ThScopeFix.scan(s.text(), s.lang());
        sc.skips().forEach(k -> skips.add(new Cleanup.Skip(Cleanup.MARKUP_STAGE, k)));
        List<Insert> out = new ArrayList<>();
        sc.ths().forEach(t -> out.add(new Insert(t.at(), SCOPE_ATTR + t.scope() + "\"")));
        return out;
    }

    /** at 내림차순으로 그 줄 글에 끼운다 — 줄은 out 에 모아 두고 apply 끝에 한 번 쓴다 */
    private static void insertAll(String text, String[] lines, List<Insert> ins, Map<Integer, String> out) {
        ins.sort(java.util.Comparator.comparingInt(Insert::at).reversed());
        for (Insert i : ins) {
            int start = text.lastIndexOf('\n', i.at() - 1) + 1;
            int no = 1 + (int) text.substring(0, start).chars().filter(c -> c == '\n').count();
            String line = out.getOrDefault(no, lines[no - 1]);
            int col = i.at() - start;
            out.put(no, line.substring(0, col) + i.text() + line.substring(col));
        }
    }
```
  (`tagRel` 은 없어진다 — `rels`·`scopes`·`insertAll` 로. `MarkupFix` 6 → 8. javadoc 첫 줄에 「· th 에 scope(ThScopeFix)」. `addLang`·`addRel`·`count`·`skip` 불변)
- `Cleanup` — `RULES` 끝에 `"a11y.thScope"`(21) · `clean:153` 의 markup 줄 `Set.of(MarkupFix.HTML_LANG, MarkupFix.BLANK, MarkupFix.BLANK_JSP, ThScopeFix.TH_SCOPE)`
- 픽스처 새 파일 `fixtures/cleanup/markup/th.jsp`(그대로):
```jsp
<%@ page contentType="text/html; charset=UTF-8" %>
<html lang="ko">
<head><title>표</title></head>
<body>
<table><caption>목록</caption>
<thead><tr><th>이름</th><th
  class="w">값</th></tr></thead>
<tbody><tr><td>가</td><td>1</td></tr></tbody>
</table>
<table><caption>첫 행</caption>
<tr><th>a</th><th>b</th></tr>
<tr><td>1</td><td>2</td></tr>
</table>
<table><caption>상세</caption>
<tr><th>제목</th><td>글</td><th>작성자</th><td>홍길동</td></tr>
<tr><th><a href="/x.do" target="_blank">링크</a></th><td colspan="3"><table><caption>안</caption><tr><TH>안쪽</TH><th>둘</th></tr><tr><td>1</td><td>2</td></tr></table></td></tr>
</table>
<table><caption>섞임</caption>
<tr><th>a</th><th>b</th></tr>
<tr><td>1</td><td>2</td></tr>
<tr><th colspan="2">구분</th></tr>
</table>
<table><caption>가운데</caption>
<tr><td>1</td><th>h</th></tr>
</table>
<th>조각</th>
<script>var t = "<table><tr><th>글</th></tr></table>";</script>
<%-- <table><tr><th>주석</th></tr></table> --%>
</body>
</html>
```
- `CleanupTest` 새 `thScopeStage`(먼저 써서 RED):
```java
    /** 설계 62 — a11y.thScope 고침: thead·첫 행 col · 상세 표 row · 중첩 표 · 대문자 TH · 여러 줄 th · 같은 줄의 rel 과 scope · 섞인 표·가운데 th·조각은 건너뜀 */
    @Test
    void thScopeStage(@TempDir Path data) throws Exception {
        Cleanup.Result r = clean("markup", "th.jsp", data);
        String t = r.text();
        assertTrue(t.contains("<thead><tr><th scope=\"col\">이름</th><th scope=\"col\"\n  class=\"w\">값</th>")
                && t.contains("<tr><th scope=\"col\">a</th><th scope=\"col\">b</th></tr>\n<tr><td>1</td><td>2</td></tr>\n</table>\n<table><caption>상세")
                && t.contains("<tr><th scope=\"row\">제목</th><td>글</td><th scope=\"row\">작성자</th><td>홍길동</td></tr>")
                && t.contains("<tr><th scope=\"row\"><a href=\"/x.do\" target=\"_blank\" rel=\"noopener noreferrer\">링크</a></th>")
                && t.contains("<tr><TH scope=\"col\">안쪽</TH><th scope=\"col\">둘</th></tr>"), t);
        assertTrue(t.contains("<table><caption>섞임</caption>\n<tr><th>a</th><th>b</th></tr>") && t.contains("<tr><td>1</td><th>h</th></tr>")
                && t.contains("\n<th>조각</th>\n") && t.contains("<script>var t = \"<table><tr><th>글</th></tr></table>\";</script>")
                && t.contains("<%-- <table><tr><th>주석</th></tr></table> --%>"), "그대로 — " + t);
        assertEquals(List.of(new Cleanup.Step(Cleanup.MARKUP_STAGE, "a11y.thScope", 1, 0), new Cleanup.Step(Cleanup.MARKUP_STAGE, "jsp.blankNoopener", 1, 0)),
                r.steps().stream().filter(k -> k.stage().equals(Cleanup.MARKUP_STAGE)).toList());
        assertEquals(List.of("줄 18: " + ThScopeFix.NOT_FIRST, "줄 23: " + ThScopeFix.MID_ROW, "줄 26: " + ThScopeFix.OUTSIDE), skipsOf(r, Cleanup.MARKUP_STAGE));
    }
```
  (Step 차례는 `targets` 의 `TreeSet` 차례 = 규칙 id 정렬 — `a11y.…` 가 `jsp.…` 앞. skips 차례는 표가 닫힌 차례(섞임 18 → 가운데 23) 뒤에 조각 26. 줄 번호는 위 글을 그대로 붙였을 때의 값(1 `<%@` … 5 목록 표 · 10 첫 행 표 · 14 상세 표 · 18 섞임 표 · 23 가운데 표 · 26 조각) — 다르면 픽스처가 아니라 단언의 줄 번호를 맞춘다. 사유 글·차례·건수는 못 바꾼다)
- 새 `ThScopeFixTest`(단위, `scan(text, "jsp")` 직접) — 표 하나씩 열두 꼴: thead col 둘 · 첫 행 th 만 col · 행 머리 row(한 행에 th 둘) · thead + 행 머리 섞임(둘 다 맞음) · th 만인 둘째 행 → 빈 목록 + `NOT_FIRST` · td 뒤 th → `MID_ROW` · 중첩 표(바깥 row·안쪽 col) · 이미 `scope="col"` 인 th 는 목록 밖(다른 th 는 든다) · `headers="x"` 도 같음 · `<table` 밖 th → `OUTSIDE` 한 번 · script·주석·`<%= %>` 안 th 는 안 든다 · `<th:block>` 은 th 가 아니다 · 안 닫힌 표(`</table>` 없음)도 판정한다 · 대문자 `<TH>` · 여러 줄 `<th\n class>` 의 at 이 `<th` 바로 뒤
- 수 — `SmokeHtmlUnitTest:1885-1886` 20·16 → **21·17**(5-79 뒤 22·18 — 두 행에서 한 번씩 올린다) · `CleanupRoutesTest:107` 20 → 21
- 골든: `-Dgolden.update=true -Dtest=CleanupTest` → 새 `cleanup/markup-th.jsp.txt` + `steps.json` 의 `markup/th.jsp` 항목뿐(기존 `markup-*.txt` 다섯 diff 0 — `rels`·`insertAll` 재배치의 강제 지점) · `-Dtest=CheckRulesTest` 초록, `check/fixtures.json` diff 0
- 표본 기대(V-58a): `check-ruoyi-classic.json` `a11y.thScope` 132 → 0 · `cleanup-egov.json` steps `markup` ↑(thead·첫 행·행 머리 ≈ 444 파일) + skips `markup th 만 있는 행이 첫 행이 아니다…`·`markup td 뒤에 th…`·`markup table 밖 th(조각)` 새 키 · `cleanup-roller.json` markup +11 · `cleanup-jspwiki.json`·`commons`·`egov-react` 불변

### 5-79 — `file.encoding` 고침 — 쓸 인코딩을 프로필로(§3-5)
- `Cleanup` — 상수 `public static final String ENCODING = "encoding";` · `private static final String FILE_ENCODING = "file.encoding";` · `private static final Set<String> WRITABLE = Set.of(LocalFiles.UTF8, LocalFiles.UTF8_BOM, LocalFiles.MS949);`(`import kr.ejg.toolbox.core.fs.LocalFiles;` — `CleanupRunner` 가 이미 같은 의존) · `RULES` 끝에 `"file.encoding"`(22) · 클래스 javadoc 단계 차례 끝에 「→ ⑩ 인코딩」
- `Result` 에 `encoding` 추가 — `record Result(String text, String encoding, String lineEnding, boolean changed, …)` · `clean` 끝:
```java
        encodingStage(st);
        boolean changed = !st.text.equals(s.text()) || !Objects.equals(st.le, s.lineEnding()) || !Objects.equals(st.enc, s.encoding());
        return new Result(st.text, st.enc, st.le, changed, start, st.cur, st.steps, st.skips);
```
- `State` — 필드 `private String enc;`(생성자에서 `src.encoding()`) · `source(String t, String lineEnd)` 는 `new Source(src.rel(), t, enc, lineEnd, src.lang())` · 새 `Source source(String t, String lineEnd, String encoding)`(세 번째가 명시)
- 새 메서드(`Cleanup` 29 → **30**, 상한):
```java
    /** ⑩ 인코딩(설계 62) — 글은 그대로, 쓸 인코딩만 프로필 project.encoding 으로. 쓸 수 있는 이름은 LocalFiles 가 받는 셋. MS949 로 읽은 글에 U+FFFD 가 있으면 판별을 못 믿어 건너뛴다 */
    private void encodingStage(State st) {
        Set<String> targets = targets(st.cur, Set.of(FILE_ENCODING));
        if (targets.isEmpty()) {
            return;
        }
        String want = rules.encoding() == null ? "" : FileRule.norm(rules.encoding());
        if (!WRITABLE.contains(want)) {
            st.skips.add(new Skip(ENCODING, "프로필 project.encoding " + rules.encoding() + " 로는 못 적는다(UTF-8·UTF-8-BOM·MS949)"));
            return;
        }
        if (LocalFiles.MS949.equals(st.enc) && st.text.indexOf('�') >= 0) {
            st.skips.add(new Skip(ENCODING, "MS949 로 읽은 글에 깨진 글자(U+FFFD) — 인코딩 판별을 못 믿는다"));
            return;
        }
        try {
            LocalFiles.checkEncodable(st.text, want, st.le == null ? LocalFiles.LF : st.le);
        } catch (LocalFiles.Refused e) {
            st.skips.add(new Skip(ENCODING, want + " 로 못 적는 글자가 있다"));
            return;
        }
        List<Finding> after = check(rules, st.source(st.text, st.le, want));
        String why = worse(ENCODING, targets, st.cur, after, Set.of(), Set.of());
        if (why != null) {
            st.skips.add(new Skip(ENCODING, why));
            return;
        }
        addSteps(st, ENCODING, targets, after);
        st.enc = want;
        st.cur = after;
    }
```
  (`FileRule.norm` 은 package-private static — 그대로 쓴다. `rules.encoding()` null 이면 `FileRule` 이 애초에 지적을 안 내 targets 가 빈다)
- `CleanupRunner` — `FileRow` 에 `String encoding` 을 `lineEnding` 다음에 · `preview:100·108` 와 `apply` 의 `new FileRow(…)` 둘에 `r.encoding()`(내부 오류 줄은 `t.encoding()`) · `outHash(r)` → `hash(r.encoding(), String.valueOf(r.lineEnding()), r.text())` · `apply:183-184` → `LocalFiles.checkEncodable(r.text(), r.encoding(), r.lineEnding()); … files.writeResult(outRoot, ref.rel(), r.text(), r.encoding(), r.lineEnding());` · 클래스 javadoc `:22` 「인코딩 그대로 — 9-3b」 → 「인코딩은 결과의 것(`file.encoding` 고침이 프로필 것으로 바꾼다, 설계 62) — 9-3b」. `apply(10)`·`preview(8)` 인자 수 불변(`self-check.txt:41-42`)
- `CleanupRoutes` — `:57` 을 `m.put("expected", expected(id, p, le));` 로, 새 도우미(복잡도는 도우미로 — `register` 16 불변):
```java
    /** 화면이 「→ 값」 으로 보여 주는 프로필 기준 — file.lineEnding·file.encoding 만 */
    private static String expected(String id, Profile p, String le) {
        if ("file.lineEnding".equals(id)) {
            return le;
        }
        return "file.encoding".equals(id) && p != null && p.project() != null ? p.project().encoding() : null;
    }
```
- `tools/code_cleanup_ext.js:45-48`:
```js
        } else if ((r.id === 'file.lineEnding' || r.id === 'file.encoding') && !r.expected) {
          cb.checked = false;
          cb.disabled = true;
          lb.title = '프로필 project.' + r.id.slice(5) + ' 없음';
```
- 픽스처 새 폴더 `fixtures/cleanup/encoding/` 둘 — 글(둘 다 같은 꼴, 클래스 이름만):
```java
package encoding;

/** 설계 62 — 프로필 UTF-8 로 다시 적는다(글 불변). 비 ASCII 가 있어야 file.encoding 이 선다 */
class Bom {
    String s = "가";
}
```
  바이트는 PowerShell 로(Write 도구로 LF 글을 쓴 뒤):
```powershell
$d='src/test/resources/fixtures/cleanup/encoding'
$b=[IO.File]::ReadAllBytes("$d/Bom.java"); [IO.File]::WriteAllBytes("$d/Bom.java", [byte[]](0xEF,0xBB,0xBF) + $b)
$t=[IO.File]::ReadAllText("$d/Ms949.java", [Text.Encoding]::UTF8); [IO.File]::WriteAllBytes("$d/Ms949.java", [Text.Encoding]::GetEncoding(949).GetBytes($t))
```
  확인: `xxd src/test/resources/fixtures/cleanup/encoding/Bom.java | head -1` 이 `efbb bf…` · `file …/Ms949.java` 가 `Non-ISO extended-ASCII` · `git diff --stat` 에 둘만
- `CleanupTest` 새 `encodingStage`(먼저 써서 RED):
```java
    /** 설계 62 — 인코딩 고침: BOM·MS949 → 프로필 UTF-8(글 불변) · 거절 셋(쓸 수 없는 프로필 인코딩 · U+FFFD · MS949 로 못 적는 글자) · EUC-KR 프로필은 MS949 로 */
    @Test
    void encodingStage(@TempDir Path data) throws Exception {
        for (String name : List.of("Bom.java", "Ms949.java")) {
            Source s = source("encoding", name, data);
            Cleanup.Result r = clean("encoding", name, data);
            assertEquals(name.startsWith("Bom") ? "UTF-8-BOM" : "MS949", s.encoding(), name);
            assertEquals("UTF-8", r.encoding(), name);
            assertTrue(r.changed() && r.text().equals(s.text()), name + " — 글은 그대로");
            assertEquals(List.of(new Cleanup.Step(Cleanup.ENCODING, "file.encoding", 1, 0)),
                    r.steps().stream().filter(k -> k.stage().equals(Cleanup.ENCODING)).toList(), name);
        }
        String ko = "package q;\n\n/** 가 */\nclass K {\n}\n";
        Cleanup.Result jp = cleanupIn("EUC-JP").clean(new Source("K.java", ko, "UTF-8", "LF", null));
        assertEquals("UTF-8", jp.encoding());
        assertEquals(List.of("프로필 project.encoding EUC-JP 로는 못 적는다(UTF-8·UTF-8-BOM·MS949)"), skipsOf(jp, Cleanup.ENCODING));
        Cleanup.Result bad = cleanup(null).clean(new Source("B.java", "package q;\n\n/** 가� */\nclass B {\n}\n", "MS949", "LF", null));
        assertEquals("MS949", bad.encoding());
        assertEquals(List.of("MS949 로 읽은 글에 깨진 글자(U+FFFD) — 인코딩 판별을 못 믿는다"), skipsOf(bad, Cleanup.ENCODING));
        Cleanup.Result emoji = cleanupIn("MS949").clean(new Source("E.java", "package q;\n\n/** 😀 */\nclass E {\n}\n", "UTF-8", "LF", null));
        assertEquals("UTF-8", emoji.encoding());
        assertEquals(List.of("MS949 로 못 적는 글자가 있다"), skipsOf(emoji, Cleanup.ENCODING));
        Cleanup.Result toMs = cleanupIn("EUC-KR").clean(new Source("U.java", ko, "UTF-8", "LF", null));
        assertEquals("MS949", toMs.encoding());
        assertTrue(toMs.changed());
        Cleanup.Result same = cleanupIn("EUC-KR").clean(new Source("S.java", ko, "MS949", "LF", null));
        assertFalse(same.changed(), "EUC-KR 과 MS949 는 같은 기준 — 지적 없음");
    }
```
  (`fixturesGolden:67` 의 changed 단언에 `|| !r.encoding().equals(s.encoding())` 을 더한다)
- 기존 `CleanupRoutesTest.rulesListsFixable` 루프에 `if (id.equals("file.encoding")) assertEquals("UTF-8", x.path("expected").asText());`(그 시험 프로필 `t.yaml` 이 `encoding: UTF-8`) · 수 21 → **22** · `SmokeHtmlUnitTest` 21·17 → **22·18**
- 골든: `-Dgolden.update=true -Dtest=CleanupTest` → 새 `cleanup/encoding-Bom.java.txt`·`encoding-Ms949.java.txt`(글 = 원문) + `steps.json` 두 항목뿐
- 표본 기대(V-58a): `cleanup-egov.json` steps 새 키 `encoding` ≈ 211(BOM 209 + MS949 2 — egov·egov-enterprise 하나씩) · `changed` ↑ · 다른 묶음 불변(BOM·MS949 없음)
- CLI·화면은 단계 라벨 `encoding` 이 그대로 보인다(`summary`·`code_cleanup_ext.js` 는 단계 이름을 그대로 찍는다 — 글 추가 없음)

### V-58a — corpus 골든 재채점(§3-6)
- 순서: `bash scripts/mvn.sh -q -DskipTests verify` rc 0 → `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='CheckCorpusTest,LegacyCorpusTest,CleanupCorpusTest,CleanupCompileCorpusTest' -Djunit.timeout=10m -Dgolden.update=true`(`check-ruoyi-classic.json` 은 `LegacyCorpusTest:82` 가 쓴다 · `CleanupCorpusTest` 묶음은 `CheckCorpusTest.GROUPS` 의 egov·commons·jspwiki·roller·egov-react — 다른 세션이 큰 작업 중이면 미룬다, 여유 RAM 확인) → `git diff --stat src/test/resources/golden/corpus`
- 기대 변화(이것만): `check-ruoyi-classic.json` `a11y.thScope` 132 → 0 / `cleanup-egov.json` — steps `markup` ↑ · 새 `encoding` ≈ 211 · `changed` ↑ · skips 새 키 셋(`markup th 만 있는 행이 첫 행이 아니다 — 표 구조를 못 가름` · `markup td 뒤에 th — 표 구조를 못 가름` · `markup table 밖 th(조각)`) / `cleanup-roller.json` markup +11 / 나머지(`check-egov`·`check-jeecg`·`check-jeesite`·`cleanup-commons`·`cleanup-jspwiki`·`cleanup-egov-react`·`vcs-*`·`security-*`·`db-*`·`js-summary`·`analyze-*`) 불변
- 중단선: ruoyi thScope 가 0 이 아닌데 그 파일에 scope 없는 실제 `<th` 가 없음 · `cleanup-egov.json` `markup` 증가가 400 아래(판정이 §3-4 와 다르다) 또는 skips 에 `markup 다른 지적이 생김·사라짐` 새 키 · `encoding` 이 211 ± 2 밖 · `CleanupCompileCorpusTest` javac 오류 · `CleanupCorpusTest` 등급 A
- 건수 전후·벽시계를 이력에

### R-25 — 손 판정(§3-7)
- 5-76 — `toolbox.bat cleanup <표본> --rules java.unusedLocal --json --profile example --data-dir <scratch>`(jeecg·shopizer·zheng): skip `다른 지적이 사라짐: java.unusedImport` 인 row **0** · changed 수가 R-24(jeecg 16·shopizer 10·zheng 2)보다 8 많음(jeecg 20 · shopizer 13 · zheng 3 어림) · zheng `DemoController` 가 changed=true. `--write --out <scratch 빈 폴더>` 로 그 여덟 파일의 남은 문장이 호출 하나인지(코드 본문은 안 옮긴다 — `파일:줄` 만)
- 5-78 — `toolbox.bat check <ruoyi-classic> --groups a11y --json …` 의 `a11y.thScope` 0 · `cleanup <egov> --rules a11y.thScope --write --out <scratch>`: 결과 파일에서 `scope="col"`·`scope="row"` 건수 · 세 꼴(thead·첫 행·행 머리) 각 10 파일을 열어 자리가 `<th` 바로 뒤이고 값이 맞는지 · skips 사유별 수(섞임·가운데·조각)와 그 파일 5 개를 열어 사유가 맞는지 · 깨진 태그 0
- 5-79 — `cleanup <egov> --rules file.encoding --write --out <scratch>`: 결과 211 파일 전부 첫 세 바이트가 BOM 이 아니고(`for f in …; do head -c3 "$f" | xxd -p; done | sort | uniq -c`) BOM 원본과 `cmp <(tail -c +4 원본) 결과` 가 같음(표본 20) · MS949 둘은 `iconv -f CP949 -t UTF-8 원본 | cmp - 결과` 같음 · `정리_결과.txt` 에 `encoding` 단계
- 오탐·깨진 고침이면 음성 픽스처(또는 인라인 시험) 먼저 RED → 고침 ≤ 2 → V-58a 다시. 표는 compare ㉘ 「손 판정」, `파일:줄` 만(코드 본문 안 옮김). scratch 는 본 뒤 지운다

### V-58 — compare ㉘ · 정적 분석 · `--corpus`(§3-8)
- `design/compare-2026-10-28.md` 끝 `## ㉘ 재측정 — 번들 78(2026-11-10)`: 「설계 62. 올라간 후보 — ③ 표 △ 가운데 `a11y.thScope`·`file.encoding` · 잔손 0-80·5-76·5-77」 · 「손 판정」 표(R-25) · 「측정」 표(되돌림: `--rules` 하나일 때 local 되돌림 8 파일 → 0 · thScope ruoyi 오탐 132 → 0 · thScope 고침 표본 × 꼴 · encoding 고침 211 · 규칙 139 그대로 · `RULES` 20 → 22, 켜진 16 → 18) · 「골든」 · 「드러난 것」 · 「후보로」
- 순서(따로 낸다): `bash scripts/mvn.sh -DskipTests verify` rc 0 → `bash scripts/verify.sh` → `bash scripts/verify.sh --corpus`(앞뒤로 다른 세션 알림 · 여유 RAM 확인) → 끝 줄이 더 부르는 레인 없음(db 변경 없음 — `--db` 안 돈다)
- 벽시계(V-58a·`--corpus`)를 이력에

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·7(3 — 건너뜀 사유·골든에 코드 본문이 안 실리나 · 7 — 인코딩 고침이 결과 폴더에만 쓰나, 원본 바이트 불변) · 행의 축·강제 지점과 diff · **`worse` 의 before 쪽 제외가 다른 단계의 사라짐 판정을 느슨하게 하는 길**(DELETE·LOCAL 밖 단계에는 mayAppear 가 비어 영향 0 인가) · `ThScopeFix` 가 엉뚱한 자리를 내는 꼴(속성 값 안 `>` · 중첩 표 · 안 닫힌 표 · `<th:block`) · 같은 줄 rel+scope 의 자리 · `insertAll` 재배치로 `markup-blank.jsp.txt` 가 바뀌지 않았나 · 인코딩 고침이 MS949 오판(GBK 등)을 바이트에 굳히는 길과 U+FFFD 가드의 빈틈 · `FileRow.encoding` 이 화면·CLI 에 안 깨지나 · 새 판정마다 양성·음성 · 골든 갱신이 §3 목록 안인가 · tools 자기 검사가 새 무늬로 `scripts/hooks/*` 자신을 잘못 잡지 않나) → `gate-probe.sh` → `verify.sh`(끝 줄) → push → PR(본문에 행마다 시작 세 줄 표) → CI `gh api` 폴링 → claude[bot] 처분 → **머지 위임 있음** → 끝 보고(사람이 할 것 — 현장 프로필이 `project.encoding` 을 EUC-KR 로 둔 곳에서 정리 한 번: 결과 폴더 파일이 편집기에서 바로 읽히는지 · compare ③ 표의 내린 넷은 지적만 유지)

## 4. 실행 스텝
스텝마다 끝에 `bash scripts/mvn.sh -q -DskipTests verify` rc 0(자바를 고친 행) → `bash scripts/verify.sh` 초록 → `git commit`(따로) → `task-done`. 아래 「검증」 은 그 앞에 도는 것만 적는다.

### S0 — 설계 문서·행·번들 표(**실행 세션(Opus)이 첫 손으로 한다**)
- 대상: `design/62-bundle78.md`(A 절) · `PLAN.md` 「번들」 표 「번들 78」 행 + 분할표 상태·새 행(B 절) · `PROGRESS.md`(C 절) · `design/compare-2026-10-28.md` ㉗ 끝 「처분」 블록(D 절).
- 검증: `bash scripts/doc-lint.sh design/62-bundle78.md` · `bash scripts/verify.sh`(docs 레인) · 커밋 `docs: S0 설계 62 — 번들 78`. push 안 함.

### 0-80 — hooks-test 자기 검사 무늬
- 대상: `scripts/hooks-test.sh` `:469·:473`(§3-1). Edit 도구.
- 순서: 임시 밖 경로 줄 → `bash scripts/hooks-test.sh` 빨강(출력 한 줄을 이력에) → 임시 줄 지움 → 무늬 고침 → 통과(경우 수 불변).
- 검증: `bash scripts/hooks-test.sh` 통과 · `git diff --stat` 한 파일. 커밋 `fix: 0-80 hooks-test 자기 검사 — 따옴표 없는 $R/ 도`.
- 의존: S0.

### 5-76 — `worse` 생겨도-되는 규칙은 비교에서 아예 뺀다
- 대상: `core/check/Cleanup.java`(`worse` 한 줄 · `MAY_APPEAR` 주석) · `CleanupTest`(`worseRevertsStage` 넷 줄 · 새 `localOnlyKeepsOtherUnusedImport`) · `golden/cleanup/steps.json`(§3-3).
- 순서: 시험 먼저 → `-Dtest=CleanupTest` RED(둘) → 한 줄 → GREEN → `-Dgolden.update=true -Dtest=CleanupTest` → diff 가 `steps.json` `guards/next.ts` reason 한 줄.
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest'` 초록 · 골든 diff 한 줄. 커밋 `fix: 5-76 되돌림 안전망 — 생겨도 되는 규칙은 before 에서도 뺀다`.
- 의존: S0.

### 5-78 — `a11y.thScope` 고침 + `when` 오탐
- 대상: `resources/check/rules.yaml`(한 자리) · 새 `core/check/ThScopeFix.java` · `MarkupFix.java`(`rels`·`scopes`·`insertAll`) · `BlankRelRule.java`(`attrs` 가시성) · `Cleanup.java`(`RULES` 21 · markup 줄) · 새 `ThScopeFixTest` · `CleanupTest.thScopeStage` · 픽스처 `markup/th.jsp` · `SmokeHtmlUnitTest` 21·17 · `CleanupRoutesTest` 21 · 골든(§3-4).
- 순서: 픽스처 + `thScopeStage` + `ThScopeFixTest` 먼저 → RED → `when` → `ThScopeFix` → `MarkupFix` → `Cleanup` → GREEN → 수 둘 → 골든 갱신(`CleanupTest`) → `CheckRulesTest` 초록(`fixtures.json` diff 0).
- 검증: `bash scripts/mvn.sh -q test -Dtest='ThScopeFixTest,CleanupTest,CheckRulesTest,PureRulesJsonTest,CleanupRoutesTest,SmokeHtmlUnitTest'` 초록 · 골든 diff 가 새 둘(`markup-th.jsp.txt`·`steps.json` 항목)뿐 · `pure-rules.json`·`self-check.txt` diff 0. 커밋 `feat: 5-78 th scope 고침 + <th:block 오탐`.
- 의존: 5-76.

### 5-79 — `file.encoding` 고침
- 대상: `Cleanup.java`(`ENCODING`·`Result`·`State`·`encodingStage`·`RULES` 22) · `CleanupRunner.java`(`FileRow`·`outHash`·쓰기) · `web/CleanupRoutes.java`(`expected` 도우미) · `tools/code_cleanup_ext.js`(한 줄) · 픽스처 `encoding/Bom.java`·`Ms949.java` · `CleanupTest.encodingStage` + `fixturesGolden:67` · `CleanupRoutesTest` 22 + expected 단언 · `SmokeHtmlUnitTest` 22·18 · 골든(§3-5).
- 순서: 픽스처(Write → PowerShell 바이트 → `xxd`/`file` 확인) + `encodingStage` 먼저 → RED(컴파일 — `Result.encoding` 없음) → `Cleanup` → `CleanupRunner` → 라우트·JS → GREEN → 수 → 골든 갱신.
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupRoutesTest,SmokeHtmlUnitTest,SelfCheckTest'` 초록 · 골든 diff 가 새 셋(`encoding-*.txt` 둘·`steps.json` 두 항목)뿐 · `self-check.txt` 불변(`Cleanup` 30 ≤ 30 · `register` 16). 커밋 `feat: 5-79 인코딩 고침 — 프로필 인코딩으로 다시 적기`.
- 의존: 5-78.

### V-58a — corpus 골든 재채점
- 대상: `golden/corpus/*`(§3-6).
- 검증: 갱신 명령 초록 · diff 가 §3-6 목록 안 · 건수 전후·벽시계를 이력에 · 커밋 `test: V-58a 재채점 — th scope · encoding · thScope 오탐`.
- 의존: 5-79.

### R-25 — 손 판정
- 대상: compare ㉘ 「손 판정」 표(§3-7) + 고침(≤ 2, 음성 먼저 RED).
- 검증: 표 + 합 줄 · 고침이 있으면 V-58a 다시 · 커밋 `docs: R-25 손 판정 — 되돌림 0 · th scope · encoding`(고침은 따로 `fix:`).
- 의존: V-58a.

### V-58 — compare ㉘ · 정적 분석 · `--corpus`
- 대상: `design/compare-2026-10-28.md` ㉘(§3-8).
- 검증: `mvn.sh -DskipTests verify` rc 0 → `verify.sh` → `verify.sh --corpus` — 셋 초록(따로) · 끝 줄이 더 부르는 레인 없음 · `doc-lint` · 커밋 `docs: V-58 재측정 — compare ㉘ + 정적 분석 + --corpus`.
- 의존: R-25.

### 마무리
§3 「마무리」 그대로. 머지 위임 있음.

## 5. 금지 사항
- `pure/`·portfolio·satchel · `core/meta`·`core/dialect`·`core/gen`·`core/analyze`·`core/vcs` · `LocalFiles.read`·`encode`·`writeResult`(판별·쓰기 규칙 불변) · `FileRule` 판정 · `AbsentRule`
- 정규식 규칙의 `regex`·`globs`·`message`·`flags` 불변 · 규칙 id 추가·삭제 금지(139 그대로) · `pure-rules.json` diff 0 · `a11y.thScope` 는 `params.when` 한 자리만
- `Cleanup` — `encodingStage` 밖 메서드 추가 금지(30 = 상한) · `worse` 는 §3-3 한 줄 밖 변경 금지 · `TOUCHED_MAY_VANISH`·`MAY_APPEAR` 내용·`OPTIONAL`·`commit`·`targets`·단계 차례(끝에 ⑩ 더하는 것 밖) 불변 · `Edits` 에 새 기능 금지
- `MarkupFix` — `addLang`·`addRel`·`count`·`skip` 불변 · `tsx.blankNoopener` 길(`addRel`) 결과 불변 · 삽입은 `insertAll` 한 곳(줄마다 한 번 쓰기)
- `ThScopeFix` — 판정 못 하는 표를 「대충 col」 로 끼우지 않는다(표째 건너뜀) · 이미 scope·headers 있는 th 를 건드리지 않는다 · `colgroup`·`rowgroup` 을 내지 않는다 · 블록을 잡는 정규식(`.*?`) 금지 · `BlankRelRule.dead` 밖에서 주석·script 를 따로 가리지 않는다
- 인코딩 — U+FFFD 가드를 빼거나 `WRITABLE` 밖 이름으로 쓰지 않는다 · 글(`st.text`)을 바꾸지 않는다 · 소스 인코딩 판별을 다시 하지 않는다(`LocalFiles.read` 결과가 전부) · `CleanupRunner` 가 `t.encoding()` 으로 되돌아가지 않는다
- `scripts/` — `verify.sh`·훅 셋·`package.sh`·`gate-probe.sh`·`probes/*.patch`·`verify-fingerprint.sh` 불변 · `hooks-test.sh` 는 §3-1 두 자리만 · `VerifyLanesTest` 불변
- 픽스처 — 기존 줄 삭제·삽입 금지 · `neg*` 에 지적이 나는 꼴 금지 · 픽스처에 `// tb-ignore` 금지 · check 픽스처 폴더(`common·jsp·tsx·security·a11y`)에 새 파일 금지(`PureCodeCheckTest:49` 가 32 를 건다) · `encoding/` 픽스처 둘의 바이트를 편집기로 다시 저장하지 않는다(BOM·MS949 가 깨진다)
- 골든 — §3 이 적은 변화 밖 금지 · `-Dgolden.update` 는 그 시험에만 · `steps.json` 의 `row()` 에 키를 더하지 않는다 · `self-check.txt`·`self-cleanup.txt`·`check/fixtures.json`·`meta/*`·`deliverable/*` diff 0
- 새 의존성 금지 · `--full`·`--db` 는 안 돈다 · 무거운 것을 겹쳐 띄우지 않는다 · `wsl --shutdown` 금지

## 6. 최종 검증
- `bash scripts/mvn.sh -DskipTests verify` rc 0 · `bash scripts/verify.sh` · `bash scripts/verify.sh --corpus` 초록(따로) · 끝 줄이 더 부르는 레인 없음 · `gate-probe.sh` 전부 빨개짐 · `bash scripts/hooks-test.sh` 통과
- 수: 규칙 139(`CheckRulesTest`) · 정규식 63(`PureRulesJsonTest`) · `Cleanup.RULES` 22 · `OPTIONAL` 4 · 화면 체크박스 22·켜진 18(`SmokeHtmlUnitTest`) · `/api/cleanup/rules` 22(`CleanupRoutesTest`) · `Cleanup` 메서드 30 · `MarkupFix` 8 · `ThScopeFix` ≤ 9 · `CleanupRoutes.register` 복잡도 16
- 골든: `git diff --stat origin/main -- src/test/resources/golden` 이 `cleanup/steps.json`(`guards/next.ts` 한 줄 + 새 항목 셋) · `cleanup/markup-th.jsp.txt`·`cleanup/encoding-Bom.java.txt`·`cleanup/encoding-Ms949.java.txt`(새) · `corpus/check-ruoyi-classic.json` · `corpus/cleanup-egov.json` · `corpus/cleanup-roller.json` 안 · `check/pure-rules.json`·`check/fixtures.json`·`check/self-check.txt`·`check/self-cleanup.txt`·`meta/*`·`corpus/db-*`·`corpus/security-*`·`corpus/js-summary.json`·`corpus/vcs-*` diff 0
- 수동(한 번, 이력에 한 줄): `run.bat --profile example` → 코드 정리 화면 체크박스 22(켜진 18) · `file.encoding → UTF-8` 라벨 · zheng `DemoController` 폴더에서 `java.unusedLocal` 만 체크 → 미리보기 `local` 단계 2 → 0(되돌림 없음) · egov 상세 JSP 폴더 미리보기에 `scope="row"`·목록 JSP 에 `scope="col"`(쓰지 않음)
- compare ㉘ 표 + 「드러난 것」 + 「후보로」 · 이력에 건수·벽시계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- 0-80 에서 임시 밖 경로 줄을 넣어도 자기 검사가 초록(무늬가 안 잡는다) · 고친 뒤 `scripts/hooks` 등 레인 안 경로가 빨강으로 잡힘
- 5-76 에서 한 줄 뒤에도 `localOnlyKeepsOtherUnusedImport` 빨강 · `steps.json` diff 가 `guards/next.ts` 한 줄 밖(다른 픽스처의 되돌림이 풀렸다 — 그 픽스처 이름과 바뀐 글을 보고) · `guards-next.ts.txt` 가 바뀜
- 5-78 에서 기존 `markup-*.txt` 다섯 또는 `blankRelStage`·`markupStage` 가 바뀜(`rels`·`insertAll` 재배치가 순수하지 않다) · `thScopeStage` 의 사유 글·차례·건수가 §3-4 와 다름(줄 번호 차이는 아님) · `fixtures.json` 이 바뀜 · `scan` 복잡도를 `token` 분리로도 15 아래로 못 내림
- 5-79 에서 `Cleanup` 이 31 이 되어야 함 · `register` 복잡도가 바뀜 · 픽스처 바이트 확인(`xxd`·`file`)이 다름 · `fixturesGolden` 에서 기존 픽스처의 changed 가 바뀜 · `CleanupRoutesTest`·HtmlUnit 이 `FileRow` 키 추가로 빨강
- V-58a 에서 §3-6 목록 밖 골든 변화 · 중단선
- R-25 에서 고침 셋째가 필요 · thScope 가 끼운 자리가 `<th` 바로 뒤가 아닌 파일 하나라도 · encoding 결과가 BOM 뗀 원본과 바이트가 다른 파일 하나라도 · 되돌림 8 파일 가운데 하나라도 여전히 「사라짐」
- V-58 에서 `--corpus` 가 이 번들 밖 원인으로 빨강(고치기 둘까지) · 여유 RAM 모자람(사용자에게 묻는다)
- 메서드 수·복잡도 상한(§6)을 넘어야 함 · `pmd:check`·SpotBugs 새 짝 · `VerifyLanesTest` 빨강
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① egov 표본의 thScope 세 꼴 건수와 건너뜀 수(설계 실측은 글자 훑기 — `cleanup-egov.json` 이 심판, V-58a 중단선 400) ② `cleanup-egov.json` `encoding` 211 ± 2(MS949 둘이 U+FFFD 없이 읽히는지 — 실측은 U+FFFD 0) ③ `ThScopeFix.scan` 의 복잡도(≤ 15 가 안 되면 `token` 분리 — §3-4) ④ 여유 RAM(V-58a·`--corpus` 앞 확인)
- 설계가 미리 잰 것(불확실 아님): 5-76 원인·재현(CLI 둘) · 5-77 전제(`targets` 코드) · thScope 오탐 132 가 전부 `<th:block` · 우리 코드 `<th` 전부 scope 있음·BOM 파일 globs 밖(`self-*` 골든 불변) · `classMethods` 상한이 `> 30` · `register` 16 기준선 · `hooks-test.sh` 의 따옴표 없는 `$R/` 하나
- **사용자만 정할 수 있는 것** — 없음(범위·올릴 꼴·기본 켬·머지 위임은 2026-11-10 에 받았다)
