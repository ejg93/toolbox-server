# 설계 63 — 번들 79: MS949 오판 거절 · 번들 78 리뷰 잔손 여덟 · corpus 정리 범위 xml·properties (Fable 설계 · Opus 실행, 2026-11-11)

## 맥락
번들 78(설계 62)이 남긴 셋을 한 번들로 닫는다 — 5-80(독립 리뷰 I1: GBK·EUC-JP·Latin-1 바이트가 MS949 로 U+FFFD 없이 읽혀 UTF-8 로 굳는 길) · 5-81(리뷰 M1~M5·M7 + PR #121 claude[bot] ③·④ — 잔손 여덟) · 5-82(compare ㉘ 「후보로」 — `CleanupCorpusTest.GLOBS` 에 xml·properties). 결정은 전부 2026-11-11 사용자 결정(메모리 `bundle79-decisions`). 레인 — `core/check`·`resources/check`·`resources/tools`·`golden/corpus` 는 java+corpus · `web/CleanupRoutes`·`fixtures/**` 는 java · `tools/*.js` 는 corpus(화면 JS 를 표본 시험이 돌린다) → 끝에 `--corpus` 한 번(db 레인 변경 없음 — `--db` 안 돈다).

**실측(2026-11-11, `2778758` 트리)**
- **`Cleanup` 클래스 길이 999**(`:27`~`:1025`, `metric.classLength` 는 `끝줄 − 선언줄 + 1 > 1000` 이 지적 — `JavaRules:593`). **한 줄 여유**. 메서드 30 = 상한. → 5-81a 의 `worse` 고침은 순 +1 줄까지(§3-1 꼴은 +1 — `key` 변수 하나)
- **`worse` 5-76 꼴(`Cleanup.java:457`)** — `mayAppear.contains(f.rule()) && !kb.containsKey(f.rule() + "\u0000" + f.excerpt())`. before 에 같은 발췌가 하나 있으면 after 의 같은 발췌 **둘 다** 비교에 남아 `ka 2 > kb 1` → 「다른 지적이 생김: common.unusedIgnore」 → delete 되돌림(M1). 고침은 **kb 수까지만 남기기** — `ka` 가 이미 kb 만큼 찼으면 새 것
- **`ThScopeFix.TOKEN:24`** `(?i)<(/?)(table|thead|tr|th|td)(?=[\s/>])` 가 `BlankRelRule.dead`(주석·script·`<%…%>`·`${…}` 만 지움) 결과에 바로 돈다 → 속성 값 안 `onclick="…<table>…"`·`value='<th>'` 이 표·칸으로 센다(M2). `BlankRelRule.attrs` 는 따옴표를 스스로 다룬다 — 값을 공백으로 바꿔도(따옴표는 남김) `scope=`·`headers=` 판정 불변
- **`when`·`TOKEN` 경계** — `AbsentRule.apply:48-56` 은 `when` 을 **줄마다**(`split("\n")`) `find()` 하므로 줄 끝 `<th` 는 `(?=[\s/>])` 가 못 본다 → `'<th(?=[\s/>]|$)'`(M3). `TOKEN` 은 글 전체라 `\n` 이 `\s` — 파일 끝 `<th` 만 다르다 → 같은 `|$`. `a11y.thScope` 는 `flags` 없음(대소문자 구분) · `TOKEN` 은 `(?i)` — 경계 시험은 소문자 표본만 댄다(대문자 `<TH>` 검사 미검출은 범위 밖, 「드러난 것」 으로)
- **`ThScopeFix.rowScope:148-156`** — td 있는 행은 첫 칸이 th 면 그 행 th 전부 row. R-25 「같은 행 뒤 th 110」 은 `<th>a</th><td/><th>b</th><td/>` 두 쌍 꼴(이웃 아님). 사용자 결정 — **th 가 이웃한 채(`<th>…</th><th>…</th>`) td 가 섞인 행은 표째 건너뜀**(새 사유). 두 쌍 꼴은 그대로 row
- **`EncodingFix.refusal:35-52`** 차례 — WRITABLE → U+FFFD(MS949) → 선언(C1) → `checkEncodable`. `WRITABLE` 은 private · 클래스는 package-private → web 의 `CleanupRoutes.expected:162-167`(`switch`, package-private 아님 — `private static`) 이 못 본다(bot ④)
- **MS949 오판 꼴 실측**(.NET 949·51949 로 전수 — Java 와 같은 KS X 1001 판정, 실행자는 Java 시험으로 다시 잰다): 표본 가운데 BOM 없이 UTF-8 엄격 해독이 깨지는 파일 **10** — egov `EgovProcessMon.jsp` · egov-enterprise `accessDenied.jsp` · egov-prev `EgovProcessMon.jsp` · egov-msa java 6 · **jeesite `src/main/webapp/test/session.jsp`**. 앞 아홉은 한자 0·사용자 영역 0·KS X 1001 밖 0(한글만) → 새 거절에 **안 걸린다**(cleanup corpus 무리 egov 의 MS949 둘은 지금 사유 `DECLARED EUC-KR` 그대로). jeesite `session.jsp` 는 **GBK 중국어**(`離댕코닸: … 綠롸토코닸櫓돨假岱왕쇌` — 한자 6) → 새 거절이 잡는다(cleanup corpus 무리 밖이라 골든 불변, R-26 이 CLI 로 본다)
- **픽스처 바이트 실측**(아래 §3-4 글, .NET 으로 MS949 해독): GBK → 한자 있음·깨진 짝 0 · EUC-JP → 한자 있음·깨진 짝 0 · Latin-1 `10°Celsius, 5µsek` → `°C`(B0 43)·`µs`(B5 73) 가 UHC 확장 음절(KS X 1001 밖)·깨진 짝 0. **함정** — Latin-1 은 악센트 바이트 뒤가 `A-Z`·`a-z`·`0x81-0xFE` 가 아니면(`é ` · `é"`) MS949 해독이 U+FFFD 를 내 **기존 가드가 먼저 잡는다**(5-80 꼴이 아님). 또 Ç(0xC7) 이상 리드는 UHC 확장이 없어 ASCII 뒤가 오면 U+FFFD. 그래서 `°`(0xB0)·`µ`(0xB5) 뒤에 글자. GBK `你好` 만·EUC-JP 가나만은 KS X 1001 한글 자리에 떨어져 **안 걸린다**(사용자가 받아들인 비용 — 한자 한 자라도 있어야 잡는다)
- 한자(U+4E00–9FFF)는 KS X 1001 **안**(4,888자) — `EUC-KR` 인코더 `canEncode` 로는 못 거른다 → ② 를 따로 본다. `EUC-KR` charset 은 `jdk.charsets`(`package.sh` `JLINK_MODULES` 에 이미 있음 — MS949 와 같은 모듈)
- `CleanupCorpusTest.GLOBS:29` 열(java·jsp·jspf·html·htm·js·jsx·ts·tsx·mjs). `file.encoding`·`file.lineEnding` globs(`rules.yaml:189·197`)에는 `*.xml`·`*.properties`·`*.css`·`*.sql`·`*.tag` 도 있다 — 사용자 결정은 **xml·properties 둘만**. `CleanupCompileCorpusTest` 는 `*.java` 만(`:46`) — 무관. xml·properties 에 도는 단계는 `encoding`·`eol` 뿐(다른 단계 규칙이 그 확장자에 없다) → 등급 A 「지적이 늘어남」 위험 0
- `cleanup-egov.json` 지금 — files 2,963 · changed 1,294 · markup 735 · encoding 191 · skips 키 14. compare ㉘ — CLI(check 범위) encoding 211 = 골든 191 + 선언 거절 2 + xml BOM 18
- 화면 `code_cleanup_ext.js:9` `STAGE` 에 `encoding` 없음 → 단계 글에 `encoding` 원문(M5). `:45-48` 은 `!r.expected` 만 본다. 스모크 `codeCleanupPreviewApply:1871-1908` 는 프로필 UTF-8 + MS949 `Ms.java` 라 미리보기 뒤 `#rows` 글에 encoding 단계가 보인다 — 라벨 단언 자리
- `CleanupRunner.outHash:276-277` 은 `hash(r.encoding(), lineEnding, text)` — 단언만 없다(M7). `Cleanup.Result` 는 public record(8 인자) — 시험에서 바로 만든다
- `th.jsp` 픽스처 줄 — 18 섞임 · 23 가운데 · 26 조각 · 27 script · 28 주석(`thScopeStage:305` 단언 「줄 18·23·26」)
- `CleanupRoutesTest.up:40-47` 프로필 `t.yaml` 은 `encoding: UTF-8` 고정 · `rulesListsFixable:107` 22 · `file.encoding → UTF-8` 단언. `expected` 단위 시험은 `Profile` 을 직접 만든다(`CheckRulesTest.profile:34-37` 꼴, 12 인자)

**범위 밖** — 인코딩 판별 자체(`LocalFiles.read` 불변 — 다른 코드페이지로 **다시 읽지 않는다**, 거절만) · `a11y.thScope` 의 `flags: [i]`(대문자 `<TH>` 검사) · `scope="colgroup|rowgroup"` · `CleanupCorpusTest.GLOBS` 의 css·sql·tag · `CheckCorpusTest` 범위 · `LocalsAst`·`Imports` · `Cleanup` 의 단계 차례·`TOUCHED_MAY_VANISH`·`MAY_APPEAR` 내용 · 5-31·2-17·보류·조건부 행 · EUC-JP 가나만·GBK 한글 자리만인 글(비용으로 받아들임)

## 사용자 결정(2026-11-11)
1. **5-80 (가)** — `file.encoding` 기본 켬 유지 + MS949 로 읽은 글에 ① EUC-KR(KS X 1001)로 못 적는 글자 ② CJK 한자 U+4E00–9FFF ③ 사용자 정의 영역 U+E000–F8FF 가 **하나라도** 있으면 거절. 「한글 음절 0」 안은 버림. 비용(한자 주석 있는 진짜 한국어 파일도 거절 — 지적은 남음) 받아들임. 픽스처 GBK·EUC-JP·Latin-1 바이트 셋 + 표본 MS949 가 안 걸리는지 실측
2. **5-81** — 잔손 여덟 전부. th 가 이웃한 채 td 섞인 머리 행은 **표째 건너뜀**(설계 62 고정 판정 변경 — egov 「같은 행 뒤 th 110」 에서 몇이 빠지는지 재채점에서 잰다)
3. **5-82** — `CleanupCorpusTest.GLOBS` 에 xml·properties 둘 다(다른 단계 골든도 커짐 — V 행에서 사유별 설명)
4. 설계 Fable · 실행 Opus · **머지 위임 있음**

설계가 정한 것(전부 [고정]):
- 5-81 은 커밋 셋으로 — **5-81a**(`worse` 발췌 중복 M1 + `outHash` 단언 M7) · **5-81b**(`ThScopeFix` — 속성 값 안 토큰 M2 · `when`·`TOKEN` 줄 끝 M3 · 이웃 th 건너뜀 M4 · 경계 묶는 시험 bot ③) · **5-81c**(화면 `STAGE` 라벨 M5 · `expected` 쓸 수 없는 인코딩 bot ④). 분할표 5-81 행은 「분할 → 5-81a·b·c」
- `worse` 고침은 **조건 한 자리**: `!kb.containsKey(key)` → `ka.getOrDefault(key, 0) >= kb.getOrDefault(key, 0)`(before 수까지만 비교에 남기고 그 뒤 같은 발췌는 새 것). 복잡도 36 불변(연산자 수 같음) · 클래스 길이 999 → 1000(`key` 변수 한 줄, 상한 `> 1000`) · 메서드 30 불변. 고친 줄 위(`touchedNew`)의 생겨도-되는 지적은 지금처럼 `toa` 로 — 그 꼴은 안 바꾼다
- `ThScopeFix.scan` 은 `dead` 결과에 **`quietAttrs`** 를 한 번 더 — 태그(`<글자`·`</`) 안 따옴표 값을 공백으로(길이·줄바꿈 보존, 따옴표 글자는 남긴다). 안 닫힌 따옴표는 파일 끝까지 공백(안전한 쪽 — 고침이 줄지 늘지 않는다)
- `TOKEN` → `(?i)<(/?)(table|thead|tr|th|td)(?=[\s/>]|$)` · `when` → `'<th(?=[\s/>]|$)'`. `TOKEN` 을 package-private 로(시험이 두 벌을 같은 표본에 댄다). `message`·`fix`·`regex`·`globs` 불변 · `pure-rules.json` diff 0
- 이웃 th 판정 — td 가 있는 행에서 **연속한 두 칸이 모두 th** 면 표째 건너뜀, 사유 상수 `TH_PAIR = "td 가 있는 행에 th 가 이웃 — 표 구조를 못 가름"`. 판정 차례(행마다): th 없음·thead → col / td 없음 → 첫 행이면 col, 아니면 `NOT_FIRST` / 첫 칸이 td → `MID_ROW` / 이웃 th → `TH_PAIR` / 그 밖 → row. `rowScope`(scope 또는 null)와 사유 고르기(`hasTd ? MID_ROW : NOT_FIRST`)를 **`problem(Row, first)`(사유 또는 null) + `rowScope(Row)`(사유 없을 때 scope)** 둘로 가른다
- `EncodingFix` 를 **`public final class`** 로, 새 `public static boolean writable(String profileEncoding)`(= `WRITABLE.contains(target(…))`), `refusal` 첫 분기가 그것을 쓴다. 다른 멤버 가시성 불변. `CleanupRoutes.expected` 는 `file.encoding` 에서 **쓸 수 있을 때만 프로필 원문**(EUC-KR 은 「EUC-KR」 그대로 — 라벨은 프로필 글, MS949 로 안 바꾼다) · 못 쓰면 null → 화면이 끈다. `expected` 를 package-private static 로(단위 시험)
- 5-80 거절은 `EncodingFix.refusal` 의 **U+FFFD 다음·선언 앞**: `LocalFiles.MS949.equals(readEncoding) && suspect(text)` → 상수 `SUSPECT`. `suspect` 는 `char` 훑기 — `< 0x80` 건너뜀 · 한자 · 사용자 영역 · `EUC-KR` `CharsetEncoder.canEncode(char)` 거짓. 사유 글은 **하나**(corpus skip 키가 안 흩어지게 — 어느 글자인지 안 붙인다)
- 픽스처 셋은 `fixtures/cleanup/encoding/`(`Gbk.java`·`EucJp.java`·`Latin1.java`) — `fixturesGolden` 이 골든 셋을 새로 만든다(글 = MS949 로 읽은 원문 그대로, changed=false). 바이트는 PowerShell(936·51932·28591)
- `GLOBS` 끝에 `"*.xml", "*.properties"` 둘. 시험 코드 다른 자리 불변
- 새 corpus 재채점 행 V-59a · 손 판정 R-26 · 재측정 V-59(compare **㉙**). R-26 은 CLI 로 jeesite `session.jsp` 가 새 사유로 건너뛰는지 · egov thScope 결과가 R-25(472 파일·col 758·row 2,380) 에서 얼마나 빠지는지(빠진 파일 ≤ 5 열어 이웃 th 확인)

## 1. 목표와 범위
- 한 문장: 인코딩 고침이 GBK·EUC-JP·Latin-1 을 MS949 로 오판한 파일을 UTF-8 로 굳히지 않게 거절하고, 번들 78 리뷰 잔손 여덟을 닫고, corpus 정리 범위에 xml·properties 를 넣는다.
- 완료 시 체감: 코드 정리에서 중국어(GBK) JSP 는 「MS949 로 읽은 글에 한자·… 판별을 못 믿는다」 로 건너뛰고 한국어 MS949 파일은 전처럼 고쳐진다. `tb-ignore` 같은 줄이 둘 생겨도 delete 가 안 되돌려진다. `onclick="…<table>…"` 안 글이 표로 안 센다. 줄 끝 `<th` 도 검사·고침이 같이 본다. `<tr><th>a</th><th>b</th><td>1</td></tr>` 꼴 표는 건너뛴다(사유 보임). 화면 단계 글에 「인코딩」. 프로필 `project.encoding: windows-1252` 면 `file.encoding` 체크박스가 꺼진다. corpus 골든 `files` 에 xml·properties 가 든다.
- In scope: `core/check` — `Cleanup`(`worse` 한 자리) · `ThScopeFix`(`quietAttrs`·`TOKEN`·`problem`·`TH_PAIR`) · `EncodingFix`(public·`writable`·`suspect`·`SUSPECT`) · `web/CleanupRoutes`(`expected`) · `resources/check/rules.yaml`(`when` 한 자리) · `tools/code_cleanup_ext.js`(두 자리) · 시험 — `CleanupTest`·`CleanupRunnerTest`·`ThScopeFixTest`·`CheckRulesTest`·`CleanupRoutesTest`·`SmokeHtmlUnitTest`·`CleanupCorpusTest.GLOBS` · 픽스처 `encoding/` 셋 · `markup/th.jsp` 세 줄 · 골든(행마다) · `design/compare-2026-10-28.md` ㉘ 처분·㉙ · `PLAN.md`·`PROGRESS.md`
- Out of scope: 「범위 밖」 전부 · `pure/` · `LocalFiles` · `FileRule` · `AbsentRule` · `MarkupFix` · `BlankRelRule` · `Cleanup` 의 `worse` 한 자리 밖 · 새 의존성

## 2. 현재 구조 요약
「맥락」 의 실측이 요약이다. 덧붙이는 함정:
- **행마다 `bash scripts/mvn.sh -q -DskipTests verify` 를 먼저** — 빠른 verify 는 PMD·SpotBugs 를 안 본다. PMD: `AvoidLiteralsInIfCondition`(0x80·0x4E00·0x9FFF·0xE000·0xF8FF·따옴표·`>` 는 상수) · `UseLocaleWithCaseConversions` · `LiteralsFirstInComparisons` · 시험 `AvoidDuplicateLiterals`(같은 글 넷 이상은 상수)
- `Cleanup` 길이 999·메서드 30 — **`Cleanup` 에 메서드·줄을 더하지 않는다**(5-81a 의 `key` 한 줄만). `SelfCheckTest.mainMatchesBaseline` 이 `metric.classLength`·`classMethods` 새 지적을 빨강으로
- `CleanupTest.fixturesGolden:53` 은 `fixtures/cleanup/**` 전부를 `Cleanup.RULES` 로 돌려 `cleanup/<dir>-<파일>.txt` + `steps.json` → 새 픽스처 셋·`th.jsp` 세 줄은 골든을 바꾼다(`-Dgolden.update=true -Dtest=CleanupTest`). `steps.json` 의 `row()` 에 키를 더하지 않는다
- `CleanupCorpusTest` skip 키 = `단계 + " " + 사유(줄 번호 뗌)`. 사유 글이 상수라야 키가 하나
- `CheckCorpusTest`·`LegacyCorpusTest` 골든 `check-*.json` — `when` `|$` 로 줄 끝 `<th` 파일이 새로 잡히면 `a11y.thScope` 수가 는다(V-59a 가 잰다 — 중단선 아님)
- 화면 JS(`resources/tools`)는 **corpus 레인** — 5-81c 뒤 `--corpus` 가 필요한 것은 어차피 V-59 가 돈다
- Bash 도구의 heredoc 은 `\` 를 벗긴다 → 정규식·자바 고침은 Edit 도구. 픽스처 바이트는 PowerShell
- 픽스처 바이트를 편집기로 다시 저장하지 않는다(MS949 꼴이 깨진다). `.gitattributes` `* text=auto eol=lf` — NUL 없는 바이트는 글로 보고 LF 만 맞춘다(기존 `Ms949.java` 전례)
- 레인: `core/check`·`resources/check`·`resources/tools`·`golden/corpus` = java+corpus · `fixtures/**`·`web/`·`golden/cleanup` = java · `design/**` = 없음 · `PLAN.md`·`PROGRESS.md` = docs

## 3. 설계 결정 — 전부 [고정]

### 5-81a — `worse` 발췌 중복 · `outHash` 인코딩 단언(§3-1)
- `Cleanup.worse` after 루프(`:455-465`) — 지금:
```java
        for (Finding f : after) {
            // 5-76 — 생겨도 되는 규칙은 새로 생긴 것만 뺀다. 원래 있던 것(같은 발췌)은 비교에 남아 그대로면 통과, 사라지면 「사라짐」(표식이 다른 줄을 덮은 꼴)
            if (skipInCompare(f, targets) || mayAppear.contains(f.rule()) && !kb.containsKey(f.rule() + "\u0000" + f.excerpt())) {
                continue;
            }
            if (touchedNew.contains(f.line())) {
                toa.merge(f.rule(), 1, Integer::sum);
            } else {
                ka.merge(f.rule() + "\u0000" + f.excerpt(), 1, Integer::sum);
            }
        }
```
  고친 뒤(**순 +1 줄** — `key`. 주석 한 줄은 그대로 한 줄):
```java
        for (Finding f : after) {
            String key = f.rule() + "\u0000" + f.excerpt();
            // 5-76·5-81 — 생겨도 되는 규칙은 새로 생긴 것만 뺀다. 원래 있던 것(같은 발췌, before 수까지)은 비교에 남아 그대로면 통과, 사라지면 「사라짐」. 같은 발췌가 더 생기면 새 것
            if (skipInCompare(f, targets) || mayAppear.contains(f.rule()) && ka.getOrDefault(key, 0) >= kb.getOrDefault(key, 0)) {
                continue;
            }
            if (touchedNew.contains(f.line())) {
                toa.merge(f.rule(), 1, Integer::sum);
            } else {
                ka.merge(key, 1, Integer::sum);
            }
        }
```
- `CleanupTest.worseRevertsStage` 끝(`:601` 뒤)에 **먼저 더해 RED**:
```java
        // 5-81 — 같은 발췌의 생겨도-되는 지적이 하나 더 생긴 것은 새 것(비교 밖) — 지운 줄 뒤 표식 둘이 같은 글일 때 delete 를 되돌리지 않는다
        assertNull(Cleanup.worse(Cleanup.DELETE, Set.of("tsx.debugger"), db,
                List.of(f(6, "common.unusedIgnore", "tb-ignore x.y"), f(9, "common.unusedIgnore", "tb-ignore x.y")), Set.of(2), Set.of()),
                "같은 발췌 둘째는 새로 생긴 것");
        assertEquals("다른 지적이 생김: common.todo", Cleanup.worse(Cleanup.DELETE, Set.of("tsx.debugger"), db,
                List.of(f(6, "common.todo", "// TODO"), f(9, "common.todo", "// TODO")), Set.of(2), Set.of()), "생겨도 되는 규칙이 아니면 둘 다 센다");
```
  (`db` 는 `:598` 의 목록 그대로 — `tsx.debugger@2` · `common.unusedIgnore@7 "tb-ignore x.y"`. 지금은 첫 단언이 「다른 지적이 생김: common.unusedIgnore」 로 RED)
- `CleanupRunnerTest` 새 시험(M7):
```java
    /** 5-81 — outHash 는 쓸 인코딩을 센다: 글·줄바꿈이 같고 인코딩만 달라도 다른 해시(적용이 낡은 미리보기를 거절하는 근거) */
    @Test
    void outHashCountsEncoding() {
        Cleanup.Result utf = new Cleanup.Result("x\n", "UTF-8", "LF", true, List.of(), List.of(), List.of(), List.of());
        Cleanup.Result ms = new Cleanup.Result("x\n", "MS949", "LF", true, List.of(), List.of(), List.of(), List.of());
        assertNotEquals(CleanupRunner.outHash(utf), CleanupRunner.outHash(ms));
        assertEquals(CleanupRunner.outHash(utf),
                CleanupRunner.outHash(new Cleanup.Result("x\n", "UTF-8", "LF", false, List.of(), List.of(), List.of(), List.of())), "changed 는 해시 밖");
    }
```
  (`import static …assertNotEquals` 추가. 지금 코드로 바로 GREEN — 단언 행이라 TDD 예외 `Ruling:` 한 줄)
- 골든 — `cleanup/*` diff 0(픽스처에 같은 발췌 표식 둘이 생기는 꼴 없음). 표본 기대 — `cleanup-*.json` 「delete 다른 지적이 생김」 키는 지금 0 이라 불변

### 5-81b — `ThScopeFix` 셋 + `when` 경계(§3-2)
- `rules.yaml:744` — `params: {when: '<th(?=[\s/>]|$)'}` · 위 주석을 「설계 62 — `<th:block`(Thymeleaf) 은 th 가 아니다 · 5-81 — 줄 끝 `<th` 도(줄마다 보므로 `$`)」
- `ThScopeFix`:
```java
    /** 토큰 — <th 경계는 rules.yaml a11y.thScope 의 when 과 같은 판정(줄 끝 포함). CheckRulesTest 가 둘을 같은 표본에 댄다 */
    static final Pattern TOKEN = Pattern.compile("(?i)<(/?)(table|thead|tr|th|td)(?=[\\s/>]|$)");
    static final String TH_PAIR = "td 가 있는 행에 th 가 이웃 — 표 구조를 못 가름";
    private static final char DQ = '"';
    private static final char SQ = '\'';
    private static final char LT = '<';
    private static final char GT = '>';
    private static final char SLASH = '/';
    private static final char NL = '\n';
    private static final char SPACE = ' ';
    private static final char NONE = 0;
```
  `scan` 첫 줄 `String code = quietAttrs(BlankRelRule.dead(text, lang));` · 새 메서드:
```java
    /** 5-81 — 태그 안 따옴표 값(onclick="…<table>…" · value='<th>')을 공백으로. 길이·줄바꿈 보존, 따옴표 글자는 남긴다(attrs 가 짝을 센다). 안 닫힌 따옴표는 끝까지 */
    static String quietAttrs(String code) {
        StringBuilder sb = new StringBuilder(code);
        char quote = NONE;
        boolean inTag = false;
        for (int i = 0; i < sb.length(); i++) {
            char ch = sb.charAt(i);
            if (quote != NONE) {
                if (ch == quote) {
                    quote = NONE;
                } else if (ch != NL) {
                    sb.setCharAt(i, SPACE);
                }
            } else if (inTag) {
                if (ch == DQ || ch == SQ) {
                    quote = ch;
                } else if (ch == GT) {
                    inTag = false;
                }
            } else if (ch == LT && i + 1 < sb.length() && (Character.isLetter(sb.charAt(i + 1)) || sb.charAt(i + 1) == SLASH)) {
                inTag = true;
            }
        }
        return sb.toString();
    }
```
  `classify` 의 행 루프와 `rowScope` 를 가른다:
```java
        for (Row r : t.rows) {
            boolean first = firstLeft && !r.inThead;
            firstLeft &= r.inThead;
            String why = problem(r, first);
            if (why != null) {
                skips.add(LINE + t.line + ": " + why);
                return;
            }
            String scope = rowScope(r);
            r.cells.stream().filter(c -> c.th() && !c.scoped()).forEach(c -> mine.add(new Th(c.at(), scope)));
        }
```
```java
    /** 행을 못 가르는 사유, 가르면 null — th 만인 행이 첫 행이 아님 · 첫 칸이 td · td 가 있는데 th 가 이웃(5-81) */
    private static String problem(Row r, boolean firstBodyRow) {
        if (r.inThead || !hasTh(r)) {
            return null;
        }
        if (!hasTd(r)) {
            return firstBodyRow ? null : NOT_FIRST;
        }
        if (!r.cells.get(0).th()) {
            return MID_ROW;
        }
        return adjacentTh(r) ? TH_PAIR : null;
    }

    /** 사유가 없는 행의 scope — td 가 있는 몸 행은 row, 그 밖(thead·th 만인 첫 행)은 col */
    private static String rowScope(Row r) {
        return !r.inThead && hasTd(r) ? ROW : COL;
    }

    private static boolean hasTh(Row r) {
        return r.cells.stream().anyMatch(Cell::th);
    }

    /** 연속한 두 칸이 모두 th */
    private static boolean adjacentTh(Row r) {
        for (int i = 1; i < r.cells.size(); i++) {
            if (r.cells.get(i - 1).th() && r.cells.get(i).th()) {
                return true;
            }
        }
        return false;
    }
```
  (옛 `rowScope(Row, boolean)` 은 없어진다. 클래스 javadoc 「그 밖(…)」 에 「· td 가 있는 행에 th 가 이웃」 과 「속성 값 안 글은 안 본다(quietAttrs)」 를 더한다. 메서드 7 → 11)
- `ThScopeFixTest` — 먼저 써서 RED:
  - `skipsWholeTable` 끝에: `String pair = "<table><tr><th>a</th><th>b</th><td>1</td></tr>\n<tr><th>c</th><td>2</td></tr>" + TABLE_END;` → `fix(pair)` 가 그대로 · `skips(pair)` 가 `List.of(LINE1 + ThScopeFix.TH_PAIR)`(「이웃 th 가 있으면 행 머리 행도 안 끼운다」) · 두 쌍 꼴 `"<table><tr><th>a</th><td>1</td><th>b</th><td>2</td></tr>" + TABLE_END` 는 `colAndRow` 에 이미 있다(row 둘, 불변)
  - `notTh` 에: `String attr = "<table><tr><th>a</th><td><input value=\"<th>x</th>\" onclick=\"f('<table><tr><td>')\"></td></tr>" + TABLE_END;` → `fix(attr)` = `<th scope="row">a</th>` 만 끼운 글 · `skips` 빈 목록 · `String phantom = "<input value=\"<table>\"><tr><th>a</th></tr>";` → `fix(phantom)` 그대로 · `skips(phantom)` = `List.of(LINE1 + ThScopeFix.OUTSIDE)`(「값 안 `<table` 이 표를 열지 않는다 — 조각은 조각」. 지금은 값 안 `<table>` 이 표를 열어 `<th scope="col">` 을 끼운다 → RED)
  - 새 `lineEndTh`: `"<table><tr><th\nclass=\"w\">a</th></tr>" + TABLE_END` 는 이미 `colAndRow` 에 있다 → 파일 끝 `"<table><tr><th"` → `fix` 가 `"<table><tr><th scope=\"col\""`(안 닫힌 th 도 자리) · 새 `quietAttrs` 단위: `assertEquals("<a b=\"   \">x<c d='  '>", ThScopeFix.quietAttrs("<a b=\"<th\">x<c d='<t'>"))` · 줄바꿈 보존 `"<a b=\"\n\">"` 그대로
- `CheckRulesTest` 새 시험(bot ③):
```java
    /** 번들 78 bot ③ — rules.yaml a11y.thScope 의 when 과 ThScopeFix.TOKEN 은 <th 경계 판정이 같다(줄 끝 · 공백 · / · > · <th:block · <thead). 소문자만 — when 은 flags 없음 */
    @Test
    void thScopeWhenMatchesFixToken() {
        Rule.Def d = RuleSet.load(profile(new Profile.CodeCheck(Map.of("a11y", true), null, null)), null).defs().stream()
                .filter(x -> x.id().equals("a11y.thScope")).findFirst().orElseThrow();
        Pattern when = AbsentRule.when(d);
        for (String s : List.of("<th>", "<th ", "<th/>", "<th", "<th\tclass", "<th:block", "<thead>", "<the", "<td>", "<tr><th>x")) {
            Matcher m = ThScopeFix.TOKEN.matcher(s);
            boolean token = false;
            while (m.find() && !token) {
                token = m.group(2).equals("th");
            }
            assertEquals(when.matcher(s).find(), token, s);
        }
    }
```
  (「`<th`」 홀로가 둘 다 참인 것이 M3 의 강제 지점 — `|$` 를 어느 한쪽에서 빼면 빨강)
- 픽스처 `markup/th.jsp` — `:25` `</table>`(가운데 표) 뒤에 세 줄 삽입:
```jsp
<table><caption>이웃</caption>
<tr><th>a</th><th>b</th><td>1</td></tr>
</table>
```
  → 조각 26 → **29**. `CleanupTest.thScopeStage:305` 단언을 `List.of("줄 18: " + NOT_FIRST, "줄 23: " + MID_ROW, "줄 26: " + ThScopeFix.TH_PAIR, "줄 29: " + OUTSIDE)` 로 · 그대로 단언에 `t.contains("<table><caption>이웃</caption>\n<tr><th>a</th><th>b</th><td>1</td></tr>")` 추가 · javadoc 에 「이웃 th」. Step 수(thScope 1→0 · blankNoopener 1→0) 불변
- 골든: `-Dgolden.update=true -Dtest=CleanupTest` → `markup-th.jsp.txt`(세 줄 더함, 다른 줄 불변) + `steps.json` 의 `markup/th.jsp` 항목(before·after 불변이면 diff 0)뿐. 기존 `markup-*.txt` 다섯 diff 0 · `check/fixtures.json`·`pure-rules.json` diff 0
- 표본 기대(V-59a): `cleanup-egov.json` markup 735 → **↓(≤ 110)** + skips 새 키 `markup td 가 있는 행에 th 가 이웃 — 표 구조를 못 가름` · `check-*.json` `a11y.thScope` 줄 끝 `<th` 만큼 ↑(0 이어도 됨)

### 5-81c — 화면 `STAGE` 라벨 · `expected` 쓸 수 없는 인코딩(§3-3)
- `EncodingFix` — `public final class EncodingFix` · 새:
```java
    /** 쓸 수 있는 프로필 인코딩인가(UTF-8·UTF-8-BOM·MS949, EUC-KR 은 MS949) — 화면이 체크박스를 켤지 본다(번들 78 bot ④) */
    public static boolean writable(String profileEncoding) {
        return WRITABLE.contains(target(profileEncoding));
    }
```
  `refusal` 첫 분기 `if (!WRITABLE.contains(want))` → `if (!writable(profileEncoding))`. 다른 멤버 가시성 불변
- `CleanupRoutes` — `import kr.ejg.toolbox.core.check.EncodingFix;` · `expected` 를 `static String expected(…)`(package-private):
```java
    /** 화면이 「→ 값」 으로 보여 주는 프로필 기준 — file.lineEnding·file.encoding 만(설계 62). 쓸 수 없는 인코딩(windows-1252)은 없음으로 — 화면이 끈다(5-81) */
    static String expected(String id, Profile p, String le) {
        String enc = p == null || p.project() == null ? null : p.project().encoding();
        return switch (id) {
            case "file.lineEnding" -> le;
            case "file.encoding" -> EncodingFix.writable(enc) ? enc : null;
            default -> null;
        };
    }
```
  (`EncodingFix.target(null)` 은 빈 글 → `writable(null)` 거짓 ✓. `register` 복잡도 불변)
- `CleanupRoutesTest` 새 시험(먼저 써서 RED):
```java
    /** 번들 78 bot ④ — 쓸 수 없는 프로필 인코딩은 expected 없음(화면이 체크박스를 끈다) · EUC-KR 은 프로필 글 그대로 */
    @Test
    void expectedHidesUnwritableEncoding() {
        assertNull(CleanupRoutes.expected("file.encoding", profile("windows-1252"), "LF"));
        assertEquals("EUC-KR", CleanupRoutes.expected("file.encoding", profile("EUC-KR"), "LF"), "MS949 로 쓰지만 라벨은 프로필 글");
        assertNull(CleanupRoutes.expected("file.encoding", profile(null), "LF"));
        assertEquals("LF", CleanupRoutes.expected("file.lineEnding", profile("windows-1252"), "LF"));
    }

    static Profile profile(String encoding) {
        return new Profile("x", new Profile.Project(null, encoding, "LF", null), null, null, null, null, null, new Profile.CodeCheck(null, null, null),
                "egov35", null, null, null);
    }
```
  (`Profile` import · `assertNull` import. `rulesListsFixable` 의 `file.encoding → UTF-8` 단언 불변)
- `tools/code_cleanup_ext.js` — `:9` `STAGE` 끝에 `, encoding: '인코딩'` · `:48`:
```js
          lb.title = r.id === 'file.encoding' ? '프로필 project.encoding 이 없거나 UTF-8·UTF-8-BOM·MS949(EUC-KR) 밖' : '프로필 project.lineEnding 없음';
```
- `SmokeHtmlUnitTest.codeCleanupPreviewApply` — 미리보기 뒤(`:1890` `res.startsWith("파일 3 · 바뀜 3")` 다음) 한 줄:
```java
            assertTrue(page.getElementById("rows").getTextContent().contains("인코딩"), "5-81 — encoding 단계 라벨: " + page.getElementById("rows").getTextContent());
```
  (`Ms.java` 가 MS949 → 프로필 UTF-8 로 encoding 단계. `#rows` 칸은 적용 전에는 `stages(r)`(`code_cleanup_ext.js:246`) — 적용 뒤 단언에 두면 결과 글로 바뀌어 못 본다. 지금은 `encoding` 원문이라 RED)
- 골든 불변(`self-check.txt` — `expected` 복잡도 ≤ 15 · `register` 불변)

### 5-80 — MS949 오판 거절(§3-4)
- `EncodingFix`:
```java
    /** 5-80 — MS949 로 읽혔지만 GBK·EUC-JP·Latin-1 일 수 있는 글. 글자 자리는 안 붙인다(corpus skip 키 하나) */
    static final String SUSPECT = "MS949 로 읽은 글에 한자·EUC-KR(KS X 1001) 밖 한글·사용자 정의 글자 — GBK·EUC-JP·Latin-1 오판일 수 있어 판별을 못 믿는다";
    /** KS X 1001 — UHC 확장 음절(똠)은 여기 없다. 한자는 있어서 따로 본다 */
    private static final Charset KSX1001 = Charset.forName("EUC-KR");
    private static final char ASCII_END = 0x80;
    private static final char CJK_FROM = 0x4E00;
    private static final char CJK_TO = 0x9FFF;
    private static final char PUA_FROM = 0xE000;
    private static final char PUA_TO = 0xF8FF;
```
  `refusal` — U+FFFD 분기 **바로 뒤**, `declaration` 앞:
```java
        if (LocalFiles.MS949.equals(readEncoding) && suspect(text)) {
            return SUSPECT;
        }
```
```java
    /** 5-80 — 한자(U+4E00–9FFF) · 사용자 정의 영역(U+E000–F8FF) · KS X 1001 로 못 적는 글자(UHC 확장 한글 — Latin-1 오판 꼴)가 하나라도 */
    static boolean suspect(String text) {
        CharsetEncoder ksx = KSX1001.newEncoder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < ASCII_END) {
                continue;
            }
            if (c >= CJK_FROM && c <= CJK_TO || c >= PUA_FROM && c <= PUA_TO || !ksx.canEncode(c)) {
                return true;
            }
        }
        return false;
    }
```
  (`import java.nio.charset.Charset; import java.nio.charset.CharsetEncoder;`. 클래스 javadoc·`refusal` javadoc 에 「MS949 오판(5-80)」. `EncodingFix` 메서드 5 → 7(5-81c 의 `writable` 포함))
- 픽스처 `fixtures/cleanup/encoding/` 셋 — Write 도구로 LF 글을 쓴 뒤 PowerShell 로 바이트를 바꾼다:
  - `Gbk.java`: `package encoding;\n\n/** 5-80 - GBK: 中文注释 */\nclass Gbk {\n    String s = "你好";\n}\n`
  - `EucJp.java`: `package encoding;\n\n/** 5-80 - EUC-JP: 日本語のコメント */\nclass EucJp {\n    String s = "東京";\n}\n`
  - `Latin1.java`: `package encoding;\n\n/** 5-80 - Latin-1: 10°Celsius, 5µsek */\nclass Latin1 {\n    String s = "°C";\n}\n`
```powershell
$d='src/test/resources/fixtures/cleanup/encoding'
foreach ($p in @(@('Gbk.java',936),@('EucJp.java',51932),@('Latin1.java',28591))) { $t=[IO.File]::ReadAllText("$d/$($p[0])", [Text.Encoding]::UTF8); [IO.File]::WriteAllBytes("$d/$($p[0])", [Text.Encoding]::GetEncoding($p[1]).GetBytes($t)) }
```
  확인: `file src/test/resources/fixtures/cleanup/encoding/{Gbk,EucJp,Latin1}.java` 셋 다 `Non-ISO extended-ASCII` 꼴(UTF-8 아님) · `git diff --stat` 에 셋만. **글을 바꾸지 않는다** — `°`·`µ` 뒤에 글자가 오는 것(`°C`·`µs`)이 U+FFFD 를 피하는 조건(「맥락」 함정). 시험이 「U+FFFD 없음」 을 단언하니 바이트가 틀리면 그 단언이 먼저 빨강
- `CleanupTest` 새 시험(먼저 써서 RED — 지금은 셋 다 `UTF-8` 로 바뀐다):
```java
    /** 5-80 — MS949 로 읽혔지만 GBK·EUC-JP·Latin-1 일 수 있는 글은 거절: 한자 · KS X 1001 밖 한글(UHC 확장) · 사용자 정의 영역. 픽스처 셋은 U+FFFD 없이 MS949 로 읽힌다(기존 가드가 못 잡는 꼴). 한자 주석의 진짜 한국어 파일도 거절(사용자 2026-11-11 — 받아들인 비용) */
    @Test
    void encodingSuspect(@TempDir Path data) throws Exception {
        for (String name : List.of("Gbk.java", "EucJp.java", "Latin1.java")) {
            Source s = source("encoding", name, data);
            assertEquals("MS949", s.encoding(), name);
            assertTrue(s.text().indexOf('�') < 0, name + " — U+FFFD 가 없어야 5-80 이 잡는 꼴(바이트가 틀렸다)");
            Cleanup.Result r = clean("encoding", name, data);
            assertEquals("MS949", r.encoding(), name);
            assertEquals(List.of(EncodingFix.SUSPECT), skipsOf(r, Cleanup.ENCODING), name);
        }
        assertFalse(EncodingFix.suspect("가나다 ①㈜〔 ascii"), "KS X 1001 한글·기호");
        assertTrue(EncodingFix.suspect("똠방각하"), "UHC 확장 음절");
        assertTrue(EncodingFix.suspect("漢字 주석"), "한자 — 받아들인 비용");
        assertTrue(EncodingFix.suspect(""), "사용자 정의 영역");
        Cleanup.Result utf = cleanup(null).clean(new Source("U.java", "package q;\n\n/** 漢字 */\nclass U {\n}\n", "UTF-8", "LF", null));
        assertTrue(skipsOf(utf, Cleanup.ENCODING).isEmpty(), "UTF-8 로 읽힌 글은 오판 판정 밖");
        Cleanup.Result bom = cleanup(null).clean(new Source("B.java", "package q;\n\n/** 漢字 */\nclass B {\n}\n", "UTF-8-BOM", "LF", null));
        assertEquals("UTF-8", bom.encoding(), "BOM 도 오판 판정 밖");
    }
```
  (`cleanup(null)` 은 프로필 UTF-8 — `:206` 전례. `encodingStage` javadoc 「거절 셋」 → 「거절 넷(… · MS949 오판 5-80)」)
- 골든: `-Dgolden.update=true -Dtest=CleanupTest` → 새 `cleanup/encoding-Gbk.java.txt`·`encoding-EucJp.java.txt`·`encoding-Latin1.java.txt`(= MS949 로 읽은 원문, 깨진 한글 글 — 우리 픽스처라 절대 규칙 3 밖) + `steps.json` 세 항목(changed false)뿐
- 표본 기대(V-59a): `cleanup-egov.json` skips 에 `encoding MS949 로 읽은 글에 한자…` 키 **없음**(MS949 둘은 한글만 — 「맥락」 실측) · `encoding` 수 불변(5-82 몫 밖). R-26 이 jeesite `session.jsp` 를 CLI 로

### 5-82 — `CleanupCorpusTest.GLOBS` xml·properties(§3-5)
- `:29` → `List.of("*.java", "*.jsp", "*.jspf", "*.html", "*.htm", "*.js", "*.jsx", "*.ts", "*.tsx", "*.mjs", "*.xml", "*.properties")` · 클래스 javadoc 에 「xml·properties 는 encoding·eol 만 돈다(5-82 — check 의 `file.*` 범위와 맞춤, css·sql·tag 은 밖)」
- 골든은 V-59a 가 갱신한다(이 행은 코드만, 빠른 verify 는 corpus 를 안 돈다 — `Ruling:` 시험 코드만 고치는 행)
- 표본 기대(V-59a, 사유별): `cleanup-*.json` 다섯 전부 `files` ↑(xml·properties 수) · `cleanup-egov.json` `encoding` 191 → **≈ 209**(xml BOM 18 — compare ㉘ 「211 − 2 − 18」 의 역) · `changed` 같은 만큼 ↑ · `eol` 키가 새로 생길 수 있다(CRLF xml·properties — 프로필 LF) · 다른 단계 수 불변 · 등급 A 0

### V-59a — corpus 골든 재채점(§3-6)
- 순서: `bash scripts/mvn.sh -q -DskipTests verify` rc 0 → 여유 RAM 확인(15GB 아래면 사용자에게) → `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='CheckCorpusTest,LegacyCorpusTest,CleanupCorpusTest,CleanupCompileCorpusTest' -Djunit.timeout=10m -Dgolden.update=true` → `git diff --stat src/test/resources/golden/corpus`
- 기대 변화(이것만): `cleanup-egov.json` — files ↑ · encoding 191 → ≈ 209 · changed ↑ · markup 735 → ↓(≤ 110) · skips 새 키 `markup td 가 있는 행에 th 가 이웃…`(≥ 0) · `eol` 키 생길 수 있음 / `cleanup-commons`·`jspwiki`·`roller`·`egov-react` — files ↑, encoding·eol 생길 수 있음, 다른 수 불변 / `check-*.json` `a11y.thScope` ↑(줄 끝 `<th`, 0 이어도 됨) / 나머지(`vcs-*`·`security-*`·`db-*`·`js-summary`·`analyze-*`·`check-*` 의 다른 규칙) 불변
- 중단선: `cleanup-egov.json` markup 감소가 110 넘음(이웃 th 판정이 두 쌍 꼴까지 먹는다) · skips 에 `encoding MS949 로 읽은 글에 한자…` 키(egov MS949 둘이 걸렸다 — 실측과 다름) · `markup 다른 지적이 생김·사라짐` 새 키 · 등급 A · javac 오류 · `delete 다른 지적이 생김` 키가 늘어남(5-81a 가 거꾸로 갔다)
- 건수 전후·벽시계를 이력에(xml·properties 가 늘어 시간이 는다 — 전 361초)

### R-26 — 손 판정(§3-7)
- 5-80 — `toolbox.bat cleanup <jeesite> --rules file.encoding --json --profile example --data-dir <scratch>`: `src/main/webapp/test/session.jsp` 가 skip `SUSPECT` 글 · changed 0 그 파일. `cleanup <egov> --rules file.encoding --json`: `EgovProcessMon.jsp`·egov-enterprise `accessDenied.jsp` 는 사유 `DECLARED EUC-KR` 그대로(한글만) · `cleanup <egov-msa> --rules file.encoding --json`: MS949 java 6 의 결과(changed 또는 사유)를 적는다 — SUSPECT 는 0 이어야(한글만 — 실측 .NET 기준, Java 로 확인하는 자리)
- 5-81b — `cleanup <egov> --rules a11y.thScope --write --out <scratch>`: changed 파일 수와 `scope="col"`·`scope="row"` 건수를 R-25(472 · 758 · 2,380)와 비교 → 빠진 파일 목록(≤ 5 열어 `<th>…</th><th>…</th><td>` 이웃 꼴 확인) · 두 쌍 꼴(`<th>a</th><td/><th>b</th><td/>`) 파일 3 이 여전히 row · 속성 값 안 `<table`·`<th` 가 있는 파일(grep `="[^"]*<t[ha]`)이 있으면 그 파일 결과가 깨지지 않았나
- 5-81a — `cleanup <jeecg|shopizer|zheng> --rules java.unusedLocal --json`: changed 20·14·3 그대로(회귀 없음)
- 5-81c·5-82 — CLI 없음(HtmlUnit·corpus 가 본다)
- 오탐·깨진 고침이면 음성 픽스처(또는 인라인 시험) 먼저 RED → 고침 ≤ 2 → V-59a 다시. 표는 compare ㉙ 「손 판정」, `파일:줄` 만(코드 본문 안 옮김). scratch 는 본 뒤 지운다

### V-59 — compare ㉙ · 정적 분석 · `--corpus`(§3-8)
- `design/compare-2026-10-28.md` 끝 `## ㉙ 재측정 — 번들 79(2026-11-11)`: 「설계 63. 5-80 MS949 오판 거절 · 5-81 잔손 여덟 · 5-82 corpus 범위」 · 「손 판정」 표(R-26) · 「측정」 표(MS949 오판 거절 — 표본 10 가운데 jeesite 1 거절·9 통과 · thScope 이웃 th 건너뜀 n 파일·col·row 전후 · corpus files 전후·encoding 191 → n · 규칙 139 그대로 · `RULES` 22 그대로) · 「골든」 · 「드러난 것」 · 「후보로」
- 순서(따로 낸다): `bash scripts/mvn.sh -DskipTests verify` rc 0 → `bash scripts/verify.sh` → `bash scripts/verify.sh --corpus`(여유 RAM 확인 · 다른 무거운 작업 없이) → 끝 줄이 더 부르는 레인 없음(db 변경 없음)
- 벽시계(V-59a·`--corpus`)를 이력에

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·7(3 — 새 골든 셋의 깨진 한글이 우리 픽스처인가 · `SUSPECT` 사유에 글자 자리가 안 붙나) · 행의 축·강제 지점과 diff · **`worse` 의 `>=` 가 before 수를 넘는 같은 발췌만 빼는가(before 에 없던 발췌는 지금처럼 빠지나 · touched 줄 꼴 불변)** · `quietAttrs` 가 태그 밖 `<`(`a<b`·JSP 조각)에서 inTag 를 잘못 켜 표를 숨기는 길(숨김은 안전한 쪽인가) · `problem`·`rowScope` 분리가 설계 62 의 col·row 판정을 바꾸지 않았나(`ThScopeFixTest.colAndRow` 불변) · `suspect` 가 MS949 가 아닌 읽기에 안 도나 · `EncodingFix` public 이 web 에 노출한 것이 `writable` 뿐인가 · `expected` 가 EUC-KR 을 그대로 내는가 · 새 판정마다 양성·음성 · 골든 갱신이 §3 목록 안인가) → `gate-probe.sh` → `verify.sh`(끝 줄) → push → PR(본문에 행마다 시작 세 줄 표) → CI `gh api` 폴링 → claude[bot] 처분 → **머지 위임 있음** → 끝 보고(사람이 할 것 — 현장 프로필이 EUC-KR 인 곳에서 한자 주석 파일이 거절되면 그 파일만 손으로 · 체크박스 라벨 눈 확인)

## 4. 실행 스텝
스텝마다 끝에 `bash scripts/mvn.sh -q -DskipTests verify` rc 0(자바를 고친 행) → `bash scripts/verify.sh` 초록 → `git commit`(따로) → `task-done`. 아래 「검증」 은 그 앞에 도는 것만 적는다.

### S0 — 설계 문서·행·번들 표(**실행 세션(Opus)이 첫 손으로 한다**)
- 대상: `design/63-bundle79.md`(A 절) · `PLAN.md` 「번들」 표 「번들 79」 행 + 분할표 5-80·5-81 갱신·새 행(B 절) · `PROGRESS.md`(C 절) · `design/compare-2026-10-28.md` ㉘ 끝 「처분」 블록(D 절).
- 검증: `bash scripts/doc-lint.sh design/63-bundle79.md` · `bash scripts/verify.sh`(docs 레인) · 커밋 `docs: S0 설계 63 — 번들 79`. push 안 함.

### 5-81a — `worse` 발췌 중복 · `outHash` 단언
- 대상: `core/check/Cleanup.java`(`worse` after 루프 — §3-1) · `CleanupTest.worseRevertsStage`(단언 둘) · `CleanupRunnerTest.outHashCountsEncoding`.
- 순서: 단언 먼저 → `-Dtest=CleanupTest` RED(첫 단언 「생김: common.unusedIgnore」) → `key`·조건 한 자리 → GREEN → `CleanupRunnerTest` 추가(바로 GREEN, `Ruling:`).
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupRunnerTest,SelfCheckTest'` 초록 · `golden/cleanup` diff 0 · `self-check.txt` diff 0(`worse` 36 · 길이 1000 ≤ 1000). 커밋 `fix: 5-81a 되돌림 안전망 — 같은 발췌 중복은 새 것 · outHash 인코딩 단언`.
- 의존: S0.

### 5-81b — `ThScopeFix` 속성 값·줄 끝·이웃 th + `when` 경계 시험
- 대상: `resources/check/rules.yaml`(한 자리) · `core/check/ThScopeFix.java`(§3-2) · `ThScopeFixTest` · `CheckRulesTest.thScopeWhenMatchesFixToken` · 픽스처 `markup/th.jsp`(세 줄) · `CleanupTest.thScopeStage`(단언) · 골든.
- 순서: `ThScopeFixTest`·`CheckRulesTest`·`th.jsp`·`thScopeStage` 먼저 → RED(컴파일 — `TH_PAIR`·`quietAttrs`·`TOKEN` 없음 → 상수만 먼저 두고 실행 RED 를 본다) → `when` → `TOKEN` → `quietAttrs` → `problem`·`rowScope`·`hasTh`·`adjacentTh` → GREEN → 골든 갱신(`CleanupTest`) → `CheckRulesTest`·`PureRulesJsonTest` 초록.
- 검증: `bash scripts/mvn.sh -q test -Dtest='ThScopeFixTest,CleanupTest,CheckRulesTest,PureRulesJsonTest,SelfCheckTest'` 초록 · 골든 diff 가 `markup-th.jsp.txt`(세 줄 추가)·`steps.json`(그 항목, 수 불변이면 0)뿐 · `pure-rules.json`·`fixtures.json`·`markup-*.txt` 다섯·`self-check.txt` diff 0. 커밋 `fix: 5-81b th scope — 속성 값 안 토큰 · 줄 끝 <th · 이웃 th 는 표째 건너뜀`.
- 의존: 5-81a.

### 5-81c — 화면 `STAGE` 라벨 · `expected` 쓸 수 없는 인코딩
- 대상: `core/check/EncodingFix.java`(public·`writable`) · `web/CleanupRoutes.java`(`expected`) · `tools/code_cleanup_ext.js`(두 자리) · `CleanupRoutesTest.expectedHidesUnwritableEncoding` · `SmokeHtmlUnitTest`(한 줄).
- 순서: 시험 둘 먼저 → RED(`expected` private 컴파일 → package-private 로 두고 실행 RED — windows-1252 가 그대로 나옴 · 스모크 「인코딩」 없음) → `EncodingFix` → `expected` → JS → GREEN.
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupRoutesTest,SmokeHtmlUnitTest,ArchitectureTest,SelfCheckTest'` 초록 · 스모크 22·18 불변 · `self-check.txt` diff 0. 커밋 `fix: 5-81c 화면 — 인코딩 단계 라벨 · 못 쓰는 프로필 인코딩은 체크박스 끔`.
- 의존: 5-81b.

### 5-80 — MS949 오판 거절
- 대상: `core/check/EncodingFix.java`(`SUSPECT`·`suspect` — §3-4) · 픽스처 `encoding/Gbk.java`·`EucJp.java`·`Latin1.java` · `CleanupTest.encodingSuspect` · 골든.
- 순서: 픽스처(Write → PowerShell 바이트 → `file` 확인) + 시험 먼저 → RED(셋 다 `UTF-8` 로 바뀜) → `EncodingFix` → GREEN → 골든 갱신(`CleanupTest`).
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,SelfCheckTest'` 초록 · 골든 diff 가 새 셋 + `steps.json` 세 항목뿐 · 기존 `encoding-Bom`·`encoding-Ms949`·`encodingStage`·`encodingDeclaration` 불변. 커밋 `feat: 5-80 인코딩 고침 — MS949 오판(한자·확장 한글·사용자 영역) 거절`.
- 의존: 5-81c.

### 5-82 — `CleanupCorpusTest.GLOBS` xml·properties
- 대상: `CleanupCorpusTest.java`(한 줄 + javadoc — §3-5).
- 검증: `bash scripts/mvn.sh -q test -Dtest='VerifyLanesTest,SelfCheckTest'` 초록(corpus 골든은 V-59a 가). 커밋 `test: 5-82 corpus 정리 범위 — xml·properties`.
- 의존: 5-80.

### V-59a — corpus 골든 재채점
- 대상: `golden/corpus/*`(§3-6).
- 검증: 갱신 명령 초록 · diff 가 §3-6 목록 안 · 건수 전후·벽시계를 이력에 · 커밋 `test: V-59a 재채점 — 이웃 th · xml·properties · 오판 거절`.
- 의존: 5-82.

### R-26 — 손 판정
- 대상: compare ㉙ 「손 판정」 표(§3-7) + 고침(≤ 2, 음성 먼저 RED).
- 검증: 표 + 합 줄 · 고침이 있으면 V-59a 다시 · 커밋 `docs: R-26 손 판정 — 오판 거절 · 이웃 th · 회귀`(고침은 따로 `fix:`).
- 의존: V-59a.

### V-59 — compare ㉙ · 정적 분석 · `--corpus`
- 대상: `design/compare-2026-10-28.md` ㉙(§3-8).
- 검증: `mvn.sh -DskipTests verify` rc 0 → `verify.sh` → `verify.sh --corpus` — 셋 초록(따로) · 끝 줄이 더 부르는 레인 없음 · `doc-lint` · 커밋 `docs: V-59 재측정 — compare ㉙ + 정적 분석 + --corpus`.
- 의존: R-26.

### 마무리
§3 「마무리」 그대로. 머지 위임 있음.

## 5. 금지 사항
- `pure/`·portfolio·satchel · `core/meta`·`core/dialect`·`core/gen`·`core/analyze`·`core/vcs` · `LocalFiles`(읽기·쓰기·판별 불변 — **다른 코드페이지로 다시 읽지 않는다**) · `FileRule` · `AbsentRule` · `MarkupFix` · `BlankRelRule`
- 정규식 규칙의 `regex`·`globs`·`message`·`flags` 불변 · 규칙 id 추가·삭제 금지(139) · `pure-rules.json` diff 0 · `a11y.thScope` 는 `params.when` 한 자리만(`flags: [i]` 안 더한다)
- `Cleanup` — `worse` 의 after 루프 한 자리 밖 변경 금지 · 메서드 추가 금지(30) · 순 +1 줄 넘기 금지(길이 1000 = 상한) · `MAY_APPEAR`·`TOUCHED_MAY_VANISH`·`OPTIONAL`·`RULES`(22)·단계 차례 불변
- `ThScopeFix` — 이웃 th 행을 「대충 row」 로 끼우지 않는다(표째 건너뜀) · 두 쌍 꼴(`th td th td`)은 row 그대로 · 이미 scope·headers 있는 th 불변 · `colgroup`·`rowgroup` 안 냄 · `quietAttrs` 는 따옴표 글자·줄바꿈·길이를 바꾸지 않는다 · 블록 정규식(`.*?`) 금지
- `EncodingFix` — public 으로 여는 멤버는 `writable` 하나 · `SUSPECT` 글에 글자·자리를 붙이지 않는다 · U+FFFD 가드·선언 거절·`checkEncodable` 차례 불변 · 글(`text`) 을 바꾸지 않는다 · `suspect` 는 MS949 로 읽은 글에만
- `CleanupRoutes` — `expected` 밖 변경 금지 · `register` 복잡도 불변 · 라벨에 MS949 를 내지 않는다(프로필 글)
- 화면 JS — `STAGE` 한 키·`title` 한 줄 밖 변경 금지 · `pure/` 와 공유하는 `common.js` 불변
- `scripts/` — `verify.sh`·훅 셋·`package.sh`·`gate-probe.sh`·`probes/*.patch`·`verify-fingerprint.sh`·`hooks-test.sh` 불변 · `VerifyLanesTest` 불변
- 픽스처 — 기존 줄 삭제 금지(`th.jsp` 는 세 줄 삽입만, 삽입 자리 고정) · `neg*` 에 지적이 나는 꼴 금지 · `tb-ignore` 금지 · check 픽스처 폴더(`common·jsp·tsx·security·a11y`)에 새 파일 금지(`PureCodeCheckTest:49` 가 32 를 건다) · `encoding/` 바이트를 편집기로 다시 저장하지 않는다 · 픽스처 글(`°C`·`µs`·한자)을 바꾸지 않는다
- 골든 — §3 이 적은 변화 밖 금지 · `-Dgolden.update` 는 그 시험에만 · `steps.json` `row()` 키 불변 · `self-check.txt`·`self-cleanup.txt`·`check/fixtures.json`·`meta/*`·`deliverable/*`·`corpus/db-*`·`security-*`·`vcs-*`·`js-summary` diff 0
- `CleanupCorpusTest` — `GLOBS` 두 항목 밖 변경 금지(css·sql·tag 안 더한다)
- 새 의존성 금지 · `--full`·`--db` 안 돈다 · 무거운 것을 겹쳐 띄우지 않는다 · `wsl --shutdown` 금지

## 6. 최종 검증
- `bash scripts/mvn.sh -DskipTests verify` rc 0 · `bash scripts/verify.sh` · `bash scripts/verify.sh --corpus` 초록(따로) · 끝 줄이 더 부르는 레인 없음 · `gate-probe.sh` 전부 빨개짐 · `bash scripts/hooks-test.sh` 통과
- 수: 규칙 139(`CheckRulesTest`) · 정규식 63(`PureRulesJsonTest`) · `Cleanup.RULES` 22 · `OPTIONAL` 4 · 화면 체크박스 22·켜진 18 · `/api/cleanup/rules` 22 + `file.encoding → UTF-8` · `Cleanup` 메서드 30·길이 ≤ 1000·`worse` 36 · `ThScopeFix` 메서드 11 · `EncodingFix` 7 · `CleanupRoutes.register` 복잡도 불변
- 골든: `git diff --stat origin/main -- src/test/resources/golden` 이 `cleanup/steps.json`(`markup/th.jsp` 항목 + 새 항목 셋) · `cleanup/markup-th.jsp.txt` · `cleanup/encoding-Gbk.java.txt`·`encoding-EucJp.java.txt`·`encoding-Latin1.java.txt`(새) · `corpus/cleanup-*.json` 다섯 · `corpus/check-*.json`(`a11y.thScope` 만) 안 · 나머지 diff 0
- 수동(한 번, 이력에 한 줄 — 브라우저 대신 HtmlUnit·CLI 로 대체해도 된다, 번들 78 전례): 코드 정리 화면 단계 글에 「인코딩」 · jeesite `test/session.jsp` 가 「MS949 로 읽은 글에 한자…」 로 건너뜀 · 이웃 th 표가 사유로 보임
- compare ㉙ 표 + 「드러난 것」 + 「후보로」 · 이력에 건수·벽시계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- 5-81a 에서 `self-check.txt` 에 `Cleanup` `metric.classLength` 새 줄(길이가 1000 을 넘었다 — 줄을 더 쓴 것) · `worse` 복잡도가 36 아님 · `cleanup/*` 골든이 바뀜(다른 픽스처의 되돌림이 풀렸다 — 그 픽스처 이름과 바뀐 사유를 보고)
- 5-81b 에서 `colAndRow`·`alreadyScoped` 가 빨강(col·row 판정이 바뀌었다) · 기존 `markup-*.txt` 다섯 또는 `blankRelStage`·`markupStage` 바뀜 · `thScopeStage` 의 사유 글·차례·건수가 §3-2 와 다름(줄 번호 차이는 아님 — 단언 줄 번호를 맞춘다) · `fixtures.json`·`pure-rules.json` 바뀜 · `thScopeWhenMatchesFixToken` 이 `|$` 둘 다 넣고도 빨강(표본 가운데 둘이 다른 것 — 그 표본을 보고)
- 5-81c 에서 `ArchitectureTest` 빨강 · 스모크 22·18 바뀜 · `rulesListsFixable` 의 `UTF-8` 단언 빨강
- 5-80 에서 픽스처 셋 가운데 하나라도 `s.encoding()` 이 MS949 가 아니거나 U+FFFD 가 있음(바이트가 틀렸다 — PowerShell 코드페이지·글을 보고, 글은 안 바꾼다) · 기존 `encoding-Bom`·`encoding-Ms949` 골든 또는 `encodingStage`·`encodingDeclaration` 바뀜 · `Charset.forName("EUC-KR")` 예외
- V-59a 에서 §3-6 목록 밖 골든 변화 · 중단선
- R-26 에서 고침 셋째가 필요 · jeesite `session.jsp` 가 거절되지 않음 · egov MS949 둘 또는 egov-msa java 가 `SUSPECT` 로 걸림(실측과 다름 — .NET 과 Java 의 KS X 1001 판정 차이면 그 글자를 보고) · 이웃 th 로 빠진 파일 가운데 이웃 th 가 없는 파일 하나라도 · 두 쌍 꼴 파일이 row 를 잃음
- V-59 에서 `--corpus` 가 이 번들 밖 원인으로 빨강(고치기 둘까지) · 여유 RAM 모자람(사용자에게 묻는다)
- 메서드 수·복잡도·길이 상한(§6)을 넘어야 함 · `pmd:check`·SpotBugs 새 짝 · `VerifyLanesTest` 빨강
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① egov 묶음에서 이웃 th 로 빠지는 파일 수(설계 실측은 .NET 글자 훑기가 아니라 R-25 의 「같은 행 뒤 th 110」 이 상한 — `cleanup-egov.json` markup 감소 ≤ 110 이 중단선) ② xml·properties 를 넣은 `files`·`encoding`·`eol` 수(xml BOM 18 은 compare ㉘ 의 역산 — ±2) ③ Java `MS949` 해독·`EUC-KR` 인코더가 .NET 949·51949 와 같은 판정인가(픽스처 셋의 「U+FFFD 없음」 단언과 R-26 의 egov-msa 6 이 심판) ④ 줄 끝 `<th` 로 `check-*.json` thScope 가 느는 수(0 이어도 됨) ⑤ 여유 RAM(V-59a·`--corpus` 앞 확인)
- 설계가 미리 잰 것(불확실 아님): `Cleanup` 길이 999·메서드 30 · `worse` 지금 조건 · `TOKEN`·`when` 꼴과 `AbsentRule` 줄 단위 · `rowScope` 판정 · `EncodingFix` 차례·가시성 · 표본 MS949 10 파일의 글자 꼴(.NET) · 픽스처 셋 바이트 꼴(.NET) · `GLOBS`·`file.*` globs · `th.jsp` 줄 번호 · `CleanupRoutesTest` 프로필 꼴 · 스모크 `Ms.java` 가 encoding 단계를 지난다
- **사용자만 정할 수 있는 것** — 없음(범위·거절 셋·이웃 th 건너뜀·GLOBS 둘·머지 위임은 2026-11-11 에 받았다)
