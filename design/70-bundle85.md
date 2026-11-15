# 설계 70 — 번들 85: 코드 정리 Critical 열둘 + Minor 다섯 + 규칙 2·3·6 꼬리 넷 (Fable 설계 · Opus 실행, 2026-11-15)

## 맥락

가로 점검 2차(설계 68 4절 E)가 코드 정리에서 Critical 열둘을 냈다. 여덟은 기본 켬 규칙에 걸려 있어 `0-98`(PR #129 `b66624d`)이 그 규칙 아홉을 `Cleanup.OPTIONAL` 로 내리고 반입 zip 을 떴다. 번들 85 는 결함을 고치고 행마다 그 규칙을 다시 켠다. 끝에 `OPTIONAL` 은 0-98 이전 아홉으로 돌아간다.

**사용자 결정(2026-11-15, 고정 — 다시 묻지 않는다)**
1. Critical 열둘 — E-eqRecv · E-consoleSide · E-backtickParity · E-regexParen · E-htmlTextArea · E-switchHeadComment · E-xmlNoDecl · E-fieldOrder · E-loggerInit · E-interruptLoop · E-twrQuietClose · E-twrFormBCatch
2. 고치는 깊이 (가) — 위험한 꼴은 고치지 않고 건너뛴다(지적은 남는다). 형식 추론·새 파서 없음. E-xmlNoDecl 은 「선언 없으면 UTF-8」
3. Minor 다섯 — E-logDomainType · E-piiQuoteParity · E-mixedEol · E-fixQuadratic · E-consoleRegexTime
4. 꼬리 넷 — `0-97` · `6-42` · `6-41` · `0-93`. `0-94`·`0-95`·`0-96`·`0-86`·`0-87` 은 안 넣는다
5. 머지 위임

**보정(2026-11-15, `b66624d` 트리)**
- E-soe 닫힘 — `CleanupRunner.java:98`·`:166` 둘 다 `catch (RuntimeException | StackOverflowError e)`, 시험 `CleanupRunnerTest.stackOverflowFileIsSkipped:67`(0-92a)
- E-applyNull 닫힘 — `StrictJson.checked:66-74` 가 요청 레코드 첫 층 목록의 null 을 400 으로. 단 `JsonErrorTest.nullListElementIs400:154` 는 `path:"x"`(상대 경로)라 null 검사가 없어도 `folderOk` 가 400 을 낸다 — 시험이 0-91 을 못 가른다 → `5-113` 에서 단언 한 줄 보강
- 꼬리 넷의 사실은 전부 지금 코드와 맞다. 다른 점 셋 — ① `0-97` 한 인자 호출은 42 가 아니라 43(완전 이름 셋: `AnalyzeRoutesTest:142`·`CleanupTest:461·512`) ② `6-42` 문장 id 가운데 `#jdbc`·`#em`·`#qdsl` 꼬리 셋(`JavaGraph:510·744·825`)은 `STMT_ID` 에 안 맞는다 — 생성자 꼴은 꼬리를 허락해야 한다 ③ `6-42` 를 `JavaGraph.java` 에 줄을 더해 고치면 `self-check.txt` 의 `classLength 1012` 줄이 바뀐다
- 번들 84 「드러난 것」(corpus 레인이 Chrome 판에 묶임) · 「정한 것」 다섯은 이 번들과 안 겹친다
- 설계 68 의 줄 번호는 0-98 이 상수 넷을 넣어 `Cleanup.java` 에서 5줄쯤 밀렸다 — 아래 2절이 지금 줄이다
- 시험·골든은 `OPTIONAL` 을 안 탄다 — `CleanupTest.cleanup(null)`·`CleanupCorpusTest`·`CleanupCompileCorpusTest`·`SelfCheckTest.cleanupChangesNothing` 은 `Cleanup.RULES` 전부를 돌린다. `OPTIONAL`·`DEFAULT` 를 타는 것은 `CleanupRoutesTest`(`PICKED`·`rulesListsFixable`·`defaultLeavesRiskyRules`) · `SmokeHtmlUnitTest.codeCleanupPreviewApply` · `CodeCliTest.cleanupPreviewAndApply`(`Pos.java` 만 — 아홉과 무관) 셋뿐

## 1. 목표와 범위

목표 — 코드 정리가 「조용히 코드를 깨는」 꼴 열일곱을 건너뛰게 하고, 0-98 이 끈 규칙 아홉을 다시 기본 켬으로 돌린다. 사용자가 겪는 변화: 기본 체크가 14 → 23 으로 돌아온다 · 위험한 자리는 고쳐지지 않고 미리보기에 「줄 N: 사유」 로 남는다 · 섞인 줄끝 파일은 통째 건너뛴다.

In scope
- E Critical 열둘 + Minor 다섯(행 `5-101`~`5-113`)
- 0-98 되돌리기 — 행 끝에서 규칙을 `OPTIONAL` 에서 뺀다(§3-0 표)
- 꼬리 넷 `0-97`·`6-42`·`6-41`·`0-93`
- `V-64a`(corpus 골든) → `R-31`(손 판정·처분 표, compare ㉞) → `V-64`(정적 분석 + `--db`)

Out of scope
- 새 고침·새 규칙·`rules.yaml` 변경 · 지적(검사 규칙) 쪽 판정 변경 — 고침만 좁힌다
- JS·HTML 파서, 형식 추론(메서드 반환 형식·다른 파일 선언)
- 섞인 줄끝을 줄마다 보존해 쓰기(건너뛰기로 닫는다) · 파일 안 취소
- `0-94`·`0-95`(JavaGraph 쪼개기)·`0-96`·`0-86`·`0-87`·`5-100`·D-jspScriptComment
- 꼴 A(try 앞 선언) + catch 를 지적으로 올리기(지금처럼 지적 없음)

## 2. 현재 구조 요약

흐름 — `CleanupRunner.preview/apply` 가 `LocalFiles.read` 로 `Text(text(\n 정규화), encoding, lineEnding(다수 쪽))` 을 받아 `Cleanup.clean(Source)` 을 부른다. `clean:163-183` 은 단계를 고정 차례로 돌리고(`deleteStage` → `editStage` 열하나 → `orderStage` → `eolStage` → `encodingStage`), 단계마다 `commit:343` 이 재검사해 나빠지면 그 단계만 되돌린다. 쓰기는 `LocalFiles.writeResult` → `encode`(파일 전체를 `lineEnding` 하나로).

| 키 | 지금 자리 | 재현 꼴 |
|---|---|---|
| E-eqRecv | `ValueFix.rewrite:124-125` → `literalFirst:173-176` · 판정 `ValueAst.literalFirst:244-261`·`stringLike:264-267` | `o.equals("a")`(`Object o`·`StringBuilder`·도메인 형식·메서드 결과)를 `"a".equals(o)` 로 — `stringLike` 는 이름이 `equals` 면 형식을 안 본다 |
| E-switchHeadComment | `SwitchFix.from:75-97` — `:81` 이 `bare`(주석 공백) 빈 줄을 위로 올라간다 | `switch (x) { /* a`⏎` b */`⏎`default:` — ` b */` 줄이 default 와 같이 끝으로 가 사이 case 가 주석 안. 앞 case 끝 줄 `break; /* a`⏎` b */` 도 같다 |
| E-fieldOrder | `OrderFix.reorder:283-284` — `bucket` → `access` 정렬 | `private int a; public int b;` → b 가 앞으로. `getDeclaredFields` 차례(엑셀 열·직렬화)가 바뀐다 |
| E-xmlNoDecl | `EncodingFix.declaration:88-104` — 선언이 없으면 `any=false` 로 통과 | `<?xml` 선언 없는(또는 `encoding=` 없는) UTF-8 XML + 프로필 MS949 → MS949 로 다시 씀. XML 파서는 선언 없으면 UTF-8 로 읽는다 |
| E-backtickParity | `Cleanup.deleteDebugger:535-542·552` · `deleteConsole:570-577·590` — 주석 뺀 글의 백틱 수 홀짝 | 문자열 안 백틱(`'`'`) · 겹친 여러 줄 템플릿(`` `${xs.map(x => ` `` ⏎ `debugger;` ⏎ `` `)}` ``)에서 안쪽 줄이 「밖」 으로 판정 |
| E-regexParen | `Cleanup.consoleEnd:631-657` — 괄호 셈이 정규식 리터럴을 모른다 | `console.log(s.replace(/\(/g, ''));` — `\(` 가 깊이를 올려 다음 문장들까지 한 console 로 지운다 |
| E-consoleSide | `Cleanup.deleteConsole:604-623` — 인자를 안 본다 | `console.log(q.pop())`·`console.log(i++)`·`console.log(x = 5)` 를 줄째 지운다 |
| E-consoleRegexTime | `:534` `^\s*debugger\s*;?\s*$` · `:652` `matches("\\s*;?\\s*")` · `:600` `SCRIPT_OPEN`(`(?i)<script\b[^>]*>$`, `find`) | `console.log(1)` + 긴 공백 + 글자 · 앞 줄이 `<script ` 반복 — 역추적 제곱 |
| E-htmlTextArea | 삭제 `Cleanup:530-625`(구역 판정 없음) · 마크업 `MarkupFix.apply:44-67` → `ThScopeFix.scan:77-78`·`BlankRelRule.tags`(`dead:88-97` 은 주석·script·스크립틀릿·EL 만 지운다) | `<pre>`·`<textarea>` 안 `console.log(…);`·`debugger;` 줄 삭제 · 안의 `<th>`·`<a target="_blank">` 에 속성 끼움 |
| E-fixQuadratic | `MarkupFix.insertAll:95-104`(끼울 자리마다 `lastIndexOf` + 앞머리 `chars()` 셈 + 줄 다시 잇기) · `PiiFix.quoted:84-91`(일치마다 `substring(0, at)` × 따옴표 셋) | th 3만 개 · 한 줄에 카드 꼴 수천 개 |
| E-twrQuietClose | `ResourceAst.Planner.of:355-379` → `closeNames:506-515` → `closeName:518-529` 가 `closeQuietly(n)`·`try { n.close(); } catch …` 도 close 로 받는다 → `ResourceFix.edit:114-117` 이 finally 통째 삭제 | finally 가 삼키던 close 예외가 try-with-resources 에서 밖으로. 꼴 A' 는 같은 꼴을 `QUIET` 로 건너뛴다(`wrap:401`) — 어긋남 |
| E-twrFormBCatch | `ResourceAst.Planner.resourceAt:595-600` — 꼴 B(`= null` + try 안 대입)는 catch 를 안 본다. 꼴 A 는 `:589` 가 catch 있으면 거절 | `finally { if (c != null) c.close(); }` 의 close 예외는 메서드 밖으로 나갔는데 고친 뒤엔 그 try 의 `catch (IOException e) { return null; }` 로 들어간다 |
| E-interruptLoop | `InterruptFix.reason:87` → `leaves:109-113` — 몸 어디든 return·throw·break 가 있으면 참 | `while (run) { try { sleep } catch (InterruptedException e) { if (stop) { return; } } }` 에 interrupt 를 끼우면 다음 sleep 이 곧바로 깨져 바쁜 루프 |
| E-loggerInit | `LogFix.find:227-243`(로거 필드) · `slot:302-325`(끼울 자리 = 마지막 static 필드 뒤) — 초기화 차례를 안 본다 | `static final T INSTANCE = new T();`⏎`static final Logger log = …;`⏎`T() { System.out.println("x"); }` → `log.debug` 가 클래스 초기화 중 NPE |
| E-logDomainType | `LogFix.LOGGER_TYPES:48` = `{Logger, Log}` · `find:234` — 단순 이름만 | 도메인 형식 `Log`(감사 로그 엔티티) 필드를 로거로 봐 `log.debug(…)` — 컴파일 실패 |
| E-piiQuoteParity | `PiiFix.cover:60` → `quoted:84-91` — 따옴표 종류별 홀짝 | `"it's" + 4111111111111111` — `'` 가 홀수라 맨 숫자를 가려 JS 구문 오류 |
| E-mixedEol | `LocalFiles.read:337`(`\r\n` → `\n`) · `lineEnding:341-354`(다수 쪽) → `CleanupRunner.apply:184` `writeResult` | CRLF·LF 가 섞인 파일을 한 줄만 고쳐도 전 줄이 다수 쪽 줄끝으로 |

꼬리 — `0-97`: `LocalFiles(Path):93` 은 main 호출 0(`App.java:177` 은 두 인자), 시험 43. `ArchitectureTest` 는 ArchUnit 1.4.1(`ClassFileImporter` + `DO_NOT_INCLUDE_TESTS`, `callConstructor` 쓰는 규칙은 아직 없다). `6-42`: `JavaGraph.Stmt:66`(compact 생성자 없음), `new Stmt(` 아홉 자리 — 리터럴 셋(`:538·547·555`)만 `STMT_ID:105` 를 지나고 `:476·529·697·746·827` 은 fqcn 꼴·`#` 꼬리, `AnalyzeStore:308` 은 H2 에서 되읽는다. 넣는 자리는 `AnalyzeStore:122-127` 하나. `6-41`: `SqlProbe.names:283-285` 가 `LITERAL:63`(작은따옴표만)을 떼고 낱말을 모은다 — `"abc"` 안 글·숫자 낱말이 「자기 낱말」. `0-93`: `ReadOnlyJdbc.WRITE:37-43` 38 낱말, `words:131-148`(`seenFor` 는 안 꺼진다), `wordEnd:151-164`(`$`·`#` 는 한 글자 토막).

함정
- **SelfCheck 기준선은 발췌 글자까지 맞춘다** — `golden/check/self-check.txt` 에 `Cleanup` 의 `consoleEnd(3)` 복잡도 17 · `deleteConsole(4)` 21 · `worse` 36 · `OrderFix.reorder(8)` 복잡도 29·길이 103·인자 8 · `readsAndPurity(4)` 22 · `JavaGraph` 길이 1012·메서드 40 이 있다. 값이 **오르면** 새 지적이라 빨강. 내려가는 것은 골든 한 줄 갱신(의도된 변화). 기준선에 없는 메서드는 복잡도 15·길이 100, 클래스는 메서드 30·길이 1000 이 상한
- `Cleanup.java` 855줄. 메서드 수가 상한 근처라 새 도우미는 `Cleanup` 밖 새 클래스에 둔다
- 픽스처 골든 — `CleanupTest.fixturesGolden:59` 가 `fixtures/cleanup/<주제>/*`(order 뺌)를 전부 돌려 `golden/cleanup/<주제>-<파일>.txt` + `steps.json` 을 쓴다. 픽스처 파일을 더하면 골든 파일 하나 + `steps.json` 이 는다. `CleanupOrderTest.fixturesGolden:79` 는 `order/*` + `fixtures/check/java/PosOrder*.java`. 갱신은 `-Dgolden.update=true`
- 스킵 사유 글은 corpus baseline 의 키다(`golden/corpus/cleanup-*.json` `skips`) — 기존 사유 상수의 글자를 바꾸지 않는다. 새 사유는 새 상수
- `order/Pos.java`·`order/Mixed.java` 는 `private int a; public int b;` 를 고쳐지는 꼴로 들고 있다 — `5-103` 뒤 바깥 몸체가 건너뜀으로 바뀐다(의도된 골든 변화)
- `resource/Pos.java:50-64` 는 catch + finally 안 `try { is.close(); } catch` — `5-109` 뒤 건너뜀으로 바뀐다(의도)
- `CleanupRoutesTest.rulesListsFixable` 의 `opt` 는 `RULES` 차례다(`OPTIONAL` 선언 차례가 아니다)
- PMD `AvoidDuplicateLiterals`(넷째 등장) · Checkstyle `DeclarationOrder`(public 상수가 private 앞) — 0-98 이 겪었다. 빠른 `verify.sh` 는 PMD·SpotBugs 를 안 본다

레인(`scripts/verify-fingerprint.sh`) — `core/check`·`core/fs`·`core/analyze`·`core/conn` 은 corpus. `core/conn`(ReadOnlyJdbc)·`core/analyze/SqlProbe.java`·`web/DbCorpusBase.java`·`core/conn` 시험은 db 도. `ArchitectureTest`·`web/*Test` 는 java 만. → 끝에 `--corpus` 와 `--db` 둘 다 돈다(db 는 `6-41`·`0-93` 때문).

## 3. 설계 결정 — 전부 [고정]

### §3-0 0-98 되돌리기 — 어느 행이 어느 규칙을 켜나

| 행 | `OPTIONAL` 에서 빼는 규칙 | 닫힌 키 | `OPTIONAL` 크기 | 스모크 기본 체크 |
|---|---|---|---|---|
| (지금) | — | — | 18 | 14 |
| `5-101` | `java.equalsLiteralFirst` | E-eqRecv | 17 | 15 |
| `5-102` | `java.defaultLast` | E-switchHeadComment | 16 | 16 |
| `5-103` | `java.declarationOrder` | E-fieldOrder | 15 | 17 |
| `5-104` | `file.encoding` | E-xmlNoDecl | 14 | 18 |
| `5-106` | `common.debugger` · `tsx.debugger` | E-backtickParity · E-htmlTextArea(삭제 쪽) | 12 | 20 |
| `5-107` | `common.consoleLog` · `tsx.console` | E-regexParen · E-consoleSide · E-consoleRegexTime(+ 5-106 의 둘) | 10 | 22 |
| `5-108` | `a11y.thScope` | E-htmlTextArea(마크업 쪽) | 9 | 23 |

- 켜기는 그 행의 **같은 커밋**에서 세 파일을 고친다 — `Cleanup.java:38-40` `OPTIONAL` · `CleanupRoutesTest.rulesListsFixable` 의 `opt` 기대 목록(그 id 를 뺀다) · `SmokeHtmlUnitTest.codeCleanupPreviewApply:1886` 의 수. 이 셋은 「파일 1~3개」 셈 밖이다(사용자 지시 — 같은 행)
- `5-104` 는 스모크의 `r_file.encoding` `setChecked` 줄(`:1889`)을 지운다
- `5-106` 은 `CleanupRoutesTest` 를 0-98 앞 꼴로 — `PICKED` 상수·`Cleanup` import·`"rules", PICKED` 아홉 자리(`:152·162·169·173·186·188·192·200·205`)·`defaultLeavesRiskyRules` 를 지운다(`git show 83183e81 -- src/test/java/kr/ejg/toolbox/web/CleanupRoutesTest.java` 의 역, `opt` 목록만 그때 남은 것). 스모크의 `r_common.debugger` 줄·주석(`:1887-1888`)도 지운다
- `5-108` 끝 상태 — `OPTIONAL` = `"java.bigDecimalDouble", "java.tryWithResources", "java.defaultCharset", "java.interruptedIgnored", "security.piiCard", "security.piiCardPlain", "common.sysout", "common.printStackTrace", "java.printStackTraceStream"` · `Cleanup.java:37` javadoc 을 `/** 설계 50 — 값·구조가 바뀌는 고침(B). 목록에 있고 기본 끔 */` 로 · `opt` 단언 글 `"기본 끔 — B 둘(설계 50) · 문자셋(설계 60) · interrupt·pii·printStackTrace(설계 64)"` · 스모크 글 `"B 둘은 기본 끔(설계 50)"`
- 상수 `DEBUGGER`·`DEBUGGER_TS`·`CONSOLE`·`CONSOLE_TS` 는 남긴다(`RULES`·`DEBUGGERS`·`CONSOLES` 가 쓴다)
- 버린 대안 — 끝 행에서 한 번에 켜기: 어느 고침이 어느 규칙을 살렸는지가 커밋에서 안 보이고, 중간에 멈추면 고친 규칙도 꺼진 채 남는다

### §3-1 `5-101` E-eqRecv — 수신자가 String 선언일 때만 뒤집는다
- `DeclaredTypes.declaredType(수신자)` 가 `"String"` 일 때만 고친다(지역·매개변수·필드·`this.x`). 그 밖(다른 형식·못 찾음 — 메서드 결과·다른 파일 필드)은 사유 `ValueFix.RECEIVER` 로 건너뛴다
- `equalsIgnoreCase` 도 같은 조건(사용자 형식이 같은 이름 메서드를 가질 수 있다)
- 판정(`ValueAst`)은 안 바꾼다 — 지적은 그대로 선다
- 버린 대안 — 알려진 String 반환 메서드(`getParameter`·`toString`) 허용 목록: 형식 추론의 시작이다(결정 2)

### §3-2 `5-102` E-switchHeadComment — 위 경계에 걸친 블록 주석의 꼬리는 안 옮긴다
- `from` 을 정한 뒤, 시작 줄이 `from` 보다 앞이고 끝 줄이 `from` 이상인 블록 주석이 있으면 `from` 을 그 주석 끝 줄 다음으로 내린다. 주석이 통째 옮길 범위 안이면 지금처럼 같이 간다
- 텍스트 블록 길(`bare.length != lines.length`)은 그대로

### §3-3 `5-103` E-fieldOrder — 인스턴스 필드끼리 차례가 바뀌면 그 몸체를 건너뛴다
- 정렬 결과에서 인스턴스 필드(`FieldDeclaration` 이고 `effStatic` 거짓)만 뽑은 차례가 원래와 다르면 사유 `OrderFix.SKIP_FIELD_ORDER`. static 필드·생성자·메서드가 움직이는 것은 그대로 고친다
- `reorder` 의 줄 수·분기를 안 늘린다 — `:293` 의 `readsAndPurity(…)` 호출을 새 메서드 `pinned(…)` 로 바꾸고 `pinned` 가 필드 차례 → `readsAndPurity` 순으로 본다
- 버린 대안 — 인스턴스 필드는 제자리에 박고 나머지만 정렬: 슬롯 재조립 불변식을 새로 짜야 한다

### §3-4 `5-104` E-xmlNoDecl — 선언 없는 XML 은 UTF-8
- 확장자 `xml` 이고 글 머리에 `<?xml … encoding=` 이 없으면 선언 값을 UTF-8 로 친다 — 목표가 UTF-8·UTF-8-BOM 이 아니면 사유 `EncodingFix.XML_NO_DECL`. MS949 로 읽힌 선언 없는 XML 을 UTF-8 로 올리는 것은 통과(고쳐지는 쪽)
- `xhtml` 은 안 넣는다(`meta charset` 길이 따로 있다)

### §3-5 `5-105` 줄 구역 훑개 `Zones` — 새 클래스 하나
- `Zones.js(주석 뺀 글)` — 한 번 훑어 ① 글자마다 「코드 자리인가」(`'`·`"` 문자열·템플릿 글 밖) ② 줄마다 「줄 첫 자리에서 템플릿이 열려 있나」(`${ }` 안 코드도 참) ③ 끝에서 다 닫혔나. 상태는 `'`·`"`(줄 끝에서 끊김) · 템플릿 글 · `${` 마다의 중괄호 깊이 스택
- 정규식 리터럴은 모른다. 대신 **끝에서 안 닫혔으면(③ 거짓) 그 파일의 debugger·console 줄을 전부 건너뛴다** — 정규식 안 백틱 하나가 판정을 뒤집는 꼴이 여기 걸린다
- `Zones.rawLines(원문, lang)` — 마크업 꼴(`jsp`·`jspf`·`tag`·`html`·`htm`·`xhtml`·`vue`)에서 `<pre`·`<textarea` 여는 줄부터 닫는 줄까지 참(안 닫히면 끝까지). 주석(`RegexRule.stripComments`) 안 태그는 안 센다
- 이것은 파서가 아니라 상태 넷짜리 훑개다(결정 2 의 「새 파서」 는 JS·HTML 구문 트리). 버린 대안 — ① 홀짝에 따옴표만 더하기: 겹친 여러 줄 템플릿을 못 가른다 ② 백틱이 앞뒤에 있으면 전부 건너뜀: tsx 파일 대부분이 건너뛰어 규칙이 죽는다

### §3-6 `5-106` E-backtickParity · E-htmlTextArea(삭제 쪽)
- `deleteDebugger`·`deleteConsole` 의 `ticks` 배열·홀짝 판정을 지우고 `Zones` 로 — 사유는 `Zones.reason` 이 준다: 기존 글 `템플릿 리터럴 안 debugger`·`템플릿 리터럴 안 console` 그대로 + 새 글 둘(`UNBALANCED` · `<pre>·<textarea> 안 …`)

### §3-7 `5-107` E-regexParen · E-consoleSide · E-consoleRegexTime
- console 문장의 인자 글(여는 괄호 뒤 ~ 닫는 괄호 앞, `Zones.js` 의 코드 자리만)에 ① `/` 가 있으면 사유 `ConsoleArgs.SLASH`(나눗셈도 걸린다 — 정규식과 못 가른다) ② 호출·`++`·`--`·대입·`await`·`yield`·`delete`·`new` 가 있으면 `ConsoleArgs.SIDE_EFFECT`
- 호출 = `(` 바로 앞(공백 건너)이 이름·`)`·`]`. 이름이 허용 목록이면 호출로 안 센다 — `stringify` `String` `Number` `Boolean` `parseInt` `parseFloat` `toString` `toFixed` `typeof` `function`. **목록을 늘리지 않는다**(jQuery `val`·`text` 는 인자가 있으면 쓰기다)
- `/` 판정이 `consoleEnd` 뒤에 와도 된다 — `\(` 로 범위가 늘어난 꼴은 늘어난 범위 안에 그 `/` 가 있고, `\)` 로 일찍 끝난 꼴은 같은 줄에 정규식 꼬리가 남아 `-2`(다른 코드가 한 줄에)로 이미 건너뛴다
- 선형 — `:652` 를 `"\\s*+;?\\s*+"` · `:534` 를 `\\s*+;?\\s*+$` 로(소유 수량자) · `SCRIPT_OPEN` 정규식은 손 훑기 `ConsoleArgs.scriptOpen` 으로
- `consoleEnd` 의 분기 수는 안 바꾼다(17 그대로). `deleteConsole` 은 `ticks` 두 루프가 빠져 21 아래로 내려간다 — `self-check.txt` 한 줄 갱신(의도)

### §3-8 `5-108` E-htmlTextArea(마크업 쪽) · E-fixQuadratic(MarkupFix)
- `MarkupFix.apply` 가 `Zones.rawLines` 를 한 번 받아 — 끼울 자리(`rels`·`scopes`)가 날글 줄이면 버리고 사유 `MarkupFix.RAW`(줄마다 한 번), `a11y.htmlLang` 줄도 같다. `ThScopeFix`·`BlankRelRule` 은 안 고친다(판정·지적 불변)
- `insertAll` — 줄 시작 자리 배열을 한 번 만들고(`Arrays.binarySearch`) 줄마다 끼울 것을 모아 `StringBuilder` 로 한 번에 잇는다

### §3-9 `5-109` E-twrQuietClose · E-twrFormBCatch — 지적은 서고 고침은 손으로
- `Planner.of` 가 `Twr.manual` 을 채운다(꼴 A' 의 `QUIET`·`HOIST` 와 같은 길 — `ResourceFix.apply:71` 이 사유로 건너뛴다)
  - finally(꼴 C 는 몸 끝 close 들)의 close 문장 가운데 `bareClose` 가 아닌 것이 있으면 `SWALLOWED`
  - 꼴 B 자원이 있고 finally 가 있고 그 try 에 catch 가 있으면 `CATCH_B`. 꼴 C 는 해당 없음(close 가 원래 try 안이라 catch 로 가던 것)
- 기존 상수 `QUIET` 의 글은 안 바꾼다(corpus 키)

### §3-10 `5-110` E-interruptLoop
- 루프 안 catch 는 **몸의 마지막 문장**이 `return`·`throw`·바깥 `break` 일 때만 고친다. 조건부 탈출만 있으면 `IN_LOOP`

### §3-11 `5-111` E-loggerInit · E-logDomainType
- 쓸 로거가 static 이고, 그 로거 선언 줄(끼울 것이면 끼울 줄)보다 **앞에** 있는 static 필드 초기화식·static 초기화 블록에 `new <그 형식 이름>(…)` 또는 수신자 없는 메서드 호출이 있으면 그 형식의 출력은 전부 사유 `LogFix.EARLY_INIT` 로 건너뛴다. Lombok 로거(`@Slf4j` 등)는 안 본다(Lombok 이 필드를 맨 앞에 넣는다)
- 형식 이름이 `Log` 인 필드는 import 가 `….logging.Log` 로 끝나거나(`org.apache.commons.logging.Log`·`org.apache.ibatis.logging.Log`) `….logging.*` 별표이거나 선언 글이 `….logging.Log` 일 때만 로거다. 아니면 로거 필드가 아닌 것으로 지나간다(그 뒤는 기존 길 — 다른 로거를 찾거나 끼우거나 건너뜀). `Logger` 는 그대로

### §3-12 `5-112` E-piiQuoteParity · E-fixQuadratic(PiiFix)
- 줄을 왼쪽부터 한 번 훑는 따옴표 상태(`'` `"` `` ` ``, `\` 건너뜀)로 「따옴표 안」 을 정한다. 일치마다 앞머리를 다시 안 센다(앞 일치의 자리에서 이어 훑는다)

### §3-13 `5-113` E-mixedEol — 섞인 줄끝 파일은 건너뛴다
- `LocalFiles.Text` 에 넷째 성분 `mixed`(CRLF 와 LF 단독이 둘 다 있다). 세 인자 생성자를 남겨 기존 호출이 그대로 선다
- `CleanupRunner.preview`·`apply` — `t.mixed()` 이고 줄끝 통일을 고르지 않았으면(`lineEnding` 인자가 비었거나 `c.wanted()` 에 `file.lineEnding` 이 없다) `clean` 을 안 부르고 사유 `CleanupRunner.MIXED_EOL`. 통일을 골랐으면 지금처럼 전 줄이 프로필 줄끝으로 간다(그것이 고른 것이다)
- 버린 대안 — 줄마다 줄끝을 들고 다니기: 단계가 줄을 지우고 끼워 대응이 깨진다

### §3-14 `0-97` — ArchUnit 규칙 하나
- `ArchitectureTest.localFilesNeedsForbiddenList` — main 의 어떤 클래스도 `LocalFiles(Path)` 를 부르지 않는다(`LocalFiles` 자신 뺌). 생성자·시험 43 곳은 안 고친다
- 버린 대안 — 한 인자 생성자를 시험 팩터리로: 시험 28 파일을 고쳐야 한다

### §3-15 `6-42` — 생성자가 꼴을 잰다 · 스키마 CHECK 는 안 넣는다
- 새 클래스 `core/analyze/StmtId` — `plain(글)`(리터럴용: `[\p{L}\p{N}_.$-]{1,300}`) · `ok(글)`(plain + 꼬리 `#jdbc`·`#em`·`#qdsl` 하나) · `require(글)`(아니면 `IllegalArgumentException("문장 id 꼴이 아니다")` — 글을 안 싣는다, 규칙 3)
- `JavaGraph.Stmt` compact 생성자가 `StmtId.require(id)`. `STMT_ID` 상수는 지우고 세 자리가 `StmtId.plain` 을 쓴다. 미해결로 돌리는 꼴은 지금 그대로(`statement:559` 의 `note("statement", …)`)
- `AnalyzeStore:308` 되읽기는 `StmtId.ok` 가 아닌 줄을 버린다(V015 가 못 지운 옛 줄이 예외로 화면을 깨지 않게)
- 스키마 CHECK 를 버린 이유 — 넣는 길이 `Stmt` 하나(`AnalyzeStore:122`)라 생성자가 더 아래 지점이고, 마이그레이션은 옛 줄 지우기를 같이 져야 한다
- `JavaGraph.java` 는 한 줄 는다(`STMT_ID` 두 줄 빠지고 생성자 세 줄) — `self-check.txt` `classLength 1012` → `1013` 한 줄 갱신(의도). 이 행에서 오르는 것은 이것 하나만 허락한다

### §3-16 `6-41` — 큰따옴표 토막·숫자 낱말은 자기 낱말이 아니다
- `SqlProbe.names` — 낱말을 모으기 전에 `"…"` 토막도 뗀다(방언 인자 없이 전부 — PG·Oracle 인용 식별자는 `…` 로 잘린다. 규칙 3 쪽으로 엄격) · 숫자로 시작하는 낱말은 버린다
- `DbCorpusBase.roFileMessages` 단언에 `SELECT` · `TB_RO84 WHERE` 부재를 더한다(등급 A)

### §3-17 `0-93` — 의도된 거절 넷을 못 박고 잇기 낱말 둘
- `WRITE` 에 `RECEIVE`·`CONVERSATION`(38 → 40). `SEND`·`END` 는 안 넣는다 — `SEND ON CONVERSATION`·`END CONVERSATION` 은 `CONVERSATION` 이 잡는다. 표본 매퍼 XML 에 두 낱말 0 건(2026-11-15 grep)
- `ReadOnlyJdbcTest.intendedRefusals` — 리터럴 `'ENABLE'`·`'BACKUP'` · 이름이 통째 `ENABLE`·`BACKUP`·`CHECKPOINT` · `V$BACKUP`·`#BACKUP` · `FOR XML PATH('')` 뒤 `SHARE` 컬럼 — 전부 거절로 단언(설계 69 「엄격한 쪽」 을 시험이 든다)

## 4. 실행 스텝

행마다 끝은 CLAUDE.md 「Task 끝」 순서(분할표 행·이력 → `bash scripts/verify.sh` → `git commit` 따로 → `task-done`). `src/main/java` 를 고치는 행은 시험을 먼저 쓰고 RED 를 본 뒤 고친다. 커밋 제목 `fix: <번호> <무엇>`. 시험은 「음성」(위험 꼴 — 글 불변 + 사유) 과 「양성」(안전 꼴 — 여전히 고쳐짐)을 둘 다 든다. 시험 글은 `CleanupTest.quietCloseIsSkipped:808` 처럼 인라인 `Source`, 실행은 `cleanup(null)`(`RULES` 전부).

공통 사다리
- `SelfCheckTest.mainMatchesBaseline` 빨강 — 새 지적이면 도우미를 새 클래스로 빼거나 메서드를 쪼갠다. 값이 내린 줄만 `-Dgolden.update=true` 로 갱신. §3-7(`deleteConsole`)·§3-15(`JavaGraph 1013`) 밖에서 오르는 값은 안 받는다
- `golden/cleanup/*` diff — 그 행의 「의도된 골든 변화」 에 적힌 것만 갱신. 그 밖 diff 는 고침이 넓은 것이다 → 고친다
- 「켜기」 가 있는 행은 고침 GREEN 뒤 §3-0 세 파일을 고치고 `CleanupRoutesTest,SmokeHtmlUnitTest` 를 돌린다

### S0 — 가지 · 설계 문서 · 행 · 번들 표 (실행 세션의 첫 손 — 설계 세션은 플랜 파일까지만 썼다)
- 대상: `design/70-bundle85.md`(새 파일) · `PLAN.md` · `PROGRESS.md`
- 변경:
  1. `git checkout main && git pull && git checkout -b work/2026-11-15b`(main `b66624d` 에서)
  2. 이 플랜 파일(`C:\Users\EJG\.claude\plans\eager-hatching-hippo.md`) 전체를 `design/70-bundle85.md` 로(글 그대로)
  3. `PROGRESS.md:1163` 0-98 줄의 `(이 커밋)` → `83183e8`
  4. `PLAN.md:571` 번들 85 행을 채운다 — 차례 `S0→5-101→5-102→5-103→5-104→5-105→5-106→5-107→5-108→5-109→5-110→5-111→5-112→5-113→0-97→6-42→6-41→0-93→V-64a→R-31→V-64`, 요지, 필요 조건(표본 폴더 · node · Docker · 여유 RAM 15GB), 상태 `대기`, **머지 위임**
  5. `PLAN.md` 16장 표 끝(`0-98` 행 뒤)에 새 행 열여섯(아래 표). 꼴 `| <번호> | <청크> | **설계 70 §3-n·§4 가 원본**. <요지>. **축**: … **강제 지점**: … | <선행> | 대기 |`. 꼬리 넷(`0-97`·`6-42`·`6-41`·`0-93`, 대기 행)은 사실 칸 끝에 ` · **처방**: 설계 70 §3-14`(15·16·17) 만 붙인다
  6. `PROGRESS.md` 이력 한 줄 — `| 2026-11-15 | 설계 70 — 번들 85 | 설계 68 E 의 Critical 열둘 + Minor 다섯 + 꼬리 넷(0-97·6-42·6-41·0-93) = 키 21 을 행 열일곱(고침) + 검증 셋으로. 보정: E-soe·E-applyNull 은 번들 84 가 닫음(E-applyNull 시험은 못 가름 → 5-113 보강) · 0-97 호출 43 · 6-42 `#` 꼬리 id. 사용자 결정 다섯 — Critical 열둘 · 깊이 (가) 건너뜀 · Minor 다섯 · 꼬리 넷 · 머지 위임. 0-98 이 끈 아홉은 행 일곱에서 다시 켠다(§3-0) | (이 커밋) |` + 「현재 상태」 블록(가지 `work/2026-11-15b` · 진행중 = 번들 85)
- 검증: `bash scripts/verify.sh` 초록(docs 레인) → `git commit`(따로) `docs: 설계 70 — 번들 85`
- 의존: 없음

| # | 청크 | 요지 | 축 | 강제 지점 | 선행 |
|---|---|---|---|---|---|
| 5-101 | equals 앞세우기는 String 수신자만 | §3-1(E-eqRecv). 켜기 `java.equalsLiteralFirst` | 고침 안전(설계 68 E) | `CleanupTest.equalsFirstNeedsStringReceiver` · `rulesListsFixable` | S0 |
| 5-102 | default 옮기기 — 걸친 블록 주석 | §3-2(E-switchHeadComment). 켜기 `java.defaultLast` | 〃 | `CleanupTest.switchKeepsStraddlingComment` | S0 |
| 5-103 | 선언 순서 — 인스턴스 필드 차례 | §3-3(E-fieldOrder). 켜기 `java.declarationOrder` | 〃 | `CleanupOrderTest.skipsAreNamed`(`SkipFieldOrder`) · `fixturesGolden`(`PosStatic`) | S0 |
| 5-104 | 선언 없는 XML 은 UTF-8 | §3-4(E-xmlNoDecl). 켜기 `file.encoding` | 〃 | `CleanupTest.xmlWithoutDeclarationStaysUtf8` | S0 |
| 5-105 | 줄 구역 훑개 `Zones` | §3-5. 템플릿 줄·코드 자리·날글 줄 | 〃 | `ZonesTest` 넷 | S0 |
| 5-106 | debugger·console 지우기에 구역 | §3-6(E-backtickParity·E-htmlTextArea 삭제 쪽). 켜기 debugger 둘 | 〃 | `CleanupTest.templateAndRawTextAreKept` | 5-105 |
| 5-107 | console 인자 — `/`·부수 효과·선형 | §3-7(E-regexParen·E-consoleSide·E-consoleRegexTime). 켜기 console 둘 | 〃 | `CleanupTest.consoleArgsRisk`·`consoleLongInputsAreLinear` | 5-106 |
| 5-108 | 마크업 끼우기 — 날글 밖만·선형 | §3-8(E-htmlTextArea 마크업 쪽·E-fixQuadratic). 켜기 `a11y.thScope` — `OPTIONAL` 아홉으로 | 〃 | `CleanupTest.markupSkipsRawText`·`manyInsertsAreLinear` · `rulesListsFixable`(아홉) | 5-105 |
| 5-109 | twr — 삼키는 finally·꼴 B catch | §3-9(E-twrQuietClose·E-twrFormBCatch) | 〃 | `ResourceAstTest.swallowingFinallyIsManual`·`formBWithCatchIsManual` | S0 |
| 5-110 | interrupt — 마지막 문장이 탈출 | §3-10(E-interruptLoop) | 〃 | `CleanupTest.interruptConditionalLeaveIsLoop` | S0 |
| 5-111 | 로거 — 먼저 도는 static 초기화·도메인 Log | §3-11(E-loggerInit·E-logDomainType) | 〃 | `CleanupTest.logSkipsEarlyStaticInit`·`logDomainTypeIsNotLogger` | S0 |
| 5-112 | 카드 가리기 — 따옴표 훑기·선형 | §3-12(E-piiQuoteParity·E-fixQuadratic) | 〃 | `CleanupTest.piiQuoteScan`·`piiLongLineIsLinear` | S0 |
| 5-113 | 섞인 줄끝 파일은 건너뜀 | §3-13(E-mixedEol) + 보정(E-applyNull 단언) | 〃 | `LocalFilesTest.readDetectsEncodingAndLineEnding` · `CleanupRunnerTest.mixedEolFileIsSkipped` · `JsonErrorTest.nullListElementIs400` | S0 |
| V-64a | corpus 골든 확인 | §4. `--corpus` · 허용 변화 · 벽시계 | 4장 | `verify.sh --corpus` | 5-113·0-93 |
| R-31 | 손 판정 · 처분 표 | §4. compare ㉞ — 키 21 처분 · 건너뜀 수 · 기본 체크 23 | 4장 | compare ㉞ 절 | V-64a |
| V-64 | 정적 분석 · `--db` | §4. PMD·SpotBugs · 컨테이너 아홉 | 4장 | `verify.sh --db` | R-31 |

### 5-101 — equals 앞세우기는 String 수신자만
- 대상: `core/check/ValueFix.java`(`rewrite:124-125`·`literalFirst:173-176`) · `CleanupTest.java` + 켜기 셋
- 변경:
  ```java
  static final String RECEIVER = "수신자가 String 선언이 아니다 — 뒤집으면 equals 결과가 달라질 수 있어 손으로";

  // rewrite 의 case ValueAst.EQUALS_FIRST:
  return literalFirst((MethodCallExpr) n, line, no, skips);

  /** {@code S.equals("L")} → {@code "L".equals(S)} — S 의 선언 형식이 String 일 때만(설계 70 §3-1). scope 괄호는 벗긴다 */
  private static Optional<String> literalFirst(MethodCallExpr m, String line, int no, List<Cleanup.Skip> skips) {
      Expression scope = strip(m.getScope().orElseThrow());
      if (!"String".equals(DeclaredTypes.declaredType(scope))) {
          skip(skips, no, RECEIVER);
          return Optional.empty();
      }
      return Optional.of(slice(line, strip(m.getArgument(0))) + "." + m.getNameAsString() + "(" + slice(line, scope) + ")");
  }
  ```
- 시험(먼저): `CleanupTest.equalsFirstNeedsStringReceiver` — 음성 네 줄(각각 한 줄): `Object o` 매개변수 `o.equals("a")` · `StringBuilder sb` 지역 `sb.equals("b")` · 메서드 결과 `name().equals("c")` · 같은 파일에 선언 없는 필드 `other.equals("d")` → 글 불변, VALUE 사유 넷이 `줄 N: ` + `RECEIVER`. 양성 세 줄: `String s` 매개변수 · `String t` 지역 · `this.name`(String 필드) → `"x".equals(…)`. RED: 음성 넷이 뒤집힌다
- 의도된 골든 변화: 없음(`value/Pos.java:47·51` 은 `String x, y`). 바뀌면 사다리
- 켜기: §3-0 `5-101` 줄
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,ValueAstTest,CleanupRoutesTest,SmokeHtmlUnitTest'` → `bash scripts/verify.sh`
- 의존: S0

### 5-102 — default 옮기기: 걸친 블록 주석
- 대상: `core/check/SwitchFix.java`(`from:75-97`) · `CleanupTest.java` + 켜기 셋
- 변경: `from` 의 5-99c 루프(`:87-92`) 뒤, 끝의 빈 줄 건너뛰기(`:93-95`) 앞에
  ```java
  for (Range r : blocks) { // 설계 70 §3-2 — 위 경계에 걸친 블록 주석(switch 머리 줄·앞 case 끝 줄에서 열림)의 꼬리는 안 옮긴다
      if (r.begin.line < from && r.end.line >= from) {
          from = Math.min(at, r.end.line + 1);
      }
  }
  ```
- 시험(먼저): `CleanupTest.switchKeepsStraddlingComment` — 음성 둘: ① `switch (x) { /* 머리`⏎`   꼬리 */`⏎`default:`⏎`a(); break;`⏎`case 1:`⏎`b(); break;`⏎`}` ② 앞 case 끝 줄 `break; /* a`⏎` b */`⏎`default:` … → 고친 글에서 주석 꼬리 줄이 제자리(머리 줄 바로 다음)에 있고, 고친 글을 `JavaSource` 로 다시 읽어 `case` entry 수가 원래와 같다(주석에 먹히지 않았다). 양성: default 위 혼자 선 `/* 설명 */` 줄은 default 와 같이 끝으로 간다. RED: ①② 에서 entry 수가 준다
- 의도된 골든 변화: 없음
- 켜기: §3-0 `5-102` 줄
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,JavaShapeTest,CleanupRoutesTest,SmokeHtmlUnitTest'` → `bash scripts/verify.sh`
- 의존: S0

### 5-103 — 선언 순서: 인스턴스 필드 차례
- 대상: `core/check/OrderFix.java`(`reorder:293` · 새 메서드 둘) · `CleanupOrderTest.java` · 새 픽스처 `fixtures/cleanup/order/SkipFieldOrder.java`·`PosStatic.java` + 켜기 셋
- 변경:
  ```java
  static final String SKIP_FIELD_ORDER = "인스턴스 필드 차례가 바뀐다(리플렉션·직렬화 열 순서)";

  // reorder:293 한 줄만 — String r = pinned(slices, sorted, b, cu);

  /** 설계 70 §3-3 — 인스턴스 필드끼리의 차례가 바뀌면 건너뜀, 아니면 기존 읽기·부수 효과 판정 */
  private static String pinned(List<Slice> old, List<Slice> sorted, Body b, CompilationUnit cu) {
      return instanceFields(old, b).equals(instanceFields(sorted, b)) ? readsAndPurity(old, sorted, b, cu) : SKIP_FIELD_ORDER;
  }

  /** 조각 차례대로 인스턴스 필드의 시작 줄(같은 객체 비교 대신 — 줄은 몸체 안에서 하나뿐이다) */
  private static List<Integer> instanceFields(List<Slice> slices, Body b) {
      List<Integer> out = new ArrayList<>();
      for (Slice s : slices) {
          if (s.node() instanceof FieldDeclaration f && !effStatic(f, b)) {
              out.add(s.l0());
          }
      }
      return out;
  }
  ```
- 픽스처: `SkipFieldOrder.java` — `class SkipFieldOrder { private int a; public int b; void m() { } SkipFieldOrder() { } }`(필드 차례가 바뀌어야 풀리는 몸체) · `PosStatic.java` — 인스턴스 필드 둘(같은 접근 제어) 뒤에 `public static final int C = 1;`, 메서드 뒤 생성자(필드 차례 그대로 static·생성자만 움직인다)
- 시험(먼저): `CleanupOrderTest.skipsAreNamed:116` 에 `SkipFieldOrder.java` → `OrderFix.SKIP_FIELD_ORDER` · `fixturesGolden` 이 `order-PosStatic.java.txt` 를 쓴다(고쳐짐 — 인스턴스 필드 두 줄의 상대 차례가 원문과 같다는 단언 하나를 같은 시험에). RED: `SkipFieldOrder` 가 고쳐진다
- 의도된 골든 변화: `order-Pos.java.txt`·`order-Mixed.java.txt`·`order-check-PosOrder*.java.txt` 가운데 접근 제어 섞인 인스턴스 필드를 가진 몸체가 원문으로 돌아가고 `order-steps.json` 에 사유가 는다(익명·enum 몸체 고침은 남는다) · 새 골든 둘. 그 밖 몸체가 돌아가면 사다리
- 켜기: §3-0 `5-103` 줄
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupOrderTest,CleanupTest,SelfCheckTest,CleanupRoutesTest,SmokeHtmlUnitTest'` → `bash scripts/verify.sh`
- 의존: S0
- 사다리: `SelfCheckTest.cleanupChangesNothing` 의 `self-cleanup.txt` 에 새 사유 줄이 생기면 갱신(자기 소스의 건너뜀 — 의도)

### 5-104 — 선언 없는 XML 은 UTF-8
- 대상: `core/check/EncodingFix.java`(`declaration:88-104`) · `CleanupTest.java` + 켜기 셋
- 변경:
  ```java
  static final String XML_NO_DECL = "인코딩 선언 없는 XML 은 UTF-8 로 읽힌다 — 선언을 넣고 손으로";
  private static final String XML = "xml";
  /** 글 머리의 <?xml … encoding= — BOM·공백 뒤 */
  private static final Pattern XML_DECL = Pattern.compile("\\A\\uFEFF?\\s*+<\\?xml[^>]*?\\bencoding\\s*=");

  // declaration 의 while 뒤, bomOnly 앞
  if (XML.equals(ext) && !LocalFiles.UTF8.equals(base) && !XML_DECL.matcher(text).find()) {
      return XML_NO_DECL;
  }
  ```
- 시험(먼저): `CleanupTest.xmlWithoutDeclarationStaysUtf8` — `EncodingFix.refusal` 직접: 음성 ① `m.xml` `<mapper>한글</mapper>` 읽기 UTF-8·프로필 MS949 → `XML_NO_DECL` ② `<?xml version="1.0"?>`(encoding 없음) 같은 조건 → `XML_NO_DECL` ③ 본문에 `charset=EUC-KR` 글(JDBC url 따위 — `DECL` 이 선언으로 읽고 프로필과 같아 지금은 통과)만 있는 선언 없는 XML → `XML_NO_DECL`. 양성 ④ `<?xml version="1.0" encoding="EUC-KR"?>` + 프로필 MS949 → null ⑤ 선언 없음·읽기 MS949·프로필 UTF-8 → null ⑥ `a.jsp` 는 이 판정을 안 탄다. RED: ①②③ 이 null
- 의도된 골든 변화: 없음(`encoding/*` 픽스처는 java)
- 켜기: §3-0 `5-104` 줄 + 스모크 `r_file.encoding` `setChecked` 줄 삭제
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupRunnerTest,CleanupRoutesTest,SmokeHtmlUnitTest'` → `bash scripts/verify.sh`
- 의존: S0

### 5-105 — 줄 구역 훑개 `Zones`
- 대상: 새 파일 `core/check/Zones.java` · 새 시험 `core/check/ZonesTest.java`
- 변경:
  ```java
  /** 줄 구역 판정(설계 70 §3-5) — 지우기·끼우기가 손대면 안 되는 자리. 파서가 아니다: 따옴표·템플릿·${ } 깊이만 센다. 정규식 리터럴은 모른다 */
  final class Zones {
      static final String UNBALANCED = "백틱 짝이 안 맞아 템플릿 리터럴 판정을 못 믿는다";
      private static final Set<String> MARKUP = Set.of("jsp", "jspf", "tag", "html", "htm", "xhtml", "vue");
      private static final List<String> RAW_TAGS = List.of("pre", "textarea");

      /** code[k] — k 자리가 코드(' " 문자열·템플릿 글 밖) · inTemplate[i] — i 줄(0 기준) 첫 자리에서 템플릿이 열려 있다(${ } 안 코드 포함) · balanced — 끝에서 다 닫혔다 */
      record Js(boolean[] code, boolean[] inTemplate, boolean balanced) { }

      static Js js(String stripped)                         // 주석을 공백으로 바꾼 글
      static boolean[] rawLines(String text, String lang)   // 마크업 꼴이 아니면 전부 거짓
      /** 건너뛸 사유(「템플릿 리터럴 안 debugger」 꼴), 손대도 되면 null. word 는 debugger·console */
      static String reason(Js js, boolean[] raw, int i, String word)
  }
  ```
  `js` 의 상태 — `open`(열린 템플릿 수) · `text`(지금 템플릿 글 자리) · `quote`(`'` `"`, 줄 끝에서 0) · `Deque<Integer> braces`(`${` 마다 그 안 `{` 깊이).
  글 자리: `\` 는 다음 글자 건너뜀 · `` ` `` 는 `open--`·`text=false` · `${` 는 `braces.push(0)`·`text=false`.
  코드 자리: 따옴표 안이면 `\` 건너뜀·같은 따옴표에서 닫음 · `'` `"` 는 `quote` · `` ` `` 는 `open++`·`text=true` · `{` 는 스택 꼭대기 +1 · `}` 는 꼭대기가 0 이면 `pop`·`text=true`, 아니면 −1(스택이 비면 무시).
  줄바꿈: `quote=0`, 다음 줄 `inTemplate = open > 0`. 끝: `balanced = open == 0 && braces.isEmpty()`. `code[k]` 는 `!text && quote == 0`(따옴표·백틱 글자 자신은 거짓).
  `rawLines` — `RegexRule.stripComments(text, lang)` 에서 `<pre`·`<textarea`(대소문자 무시, 뒤가 공백·`>`·`/`·끝)를 찾아 닫는 `</pre`·`</textarea`(없으면 끝)까지의 줄을 참으로. `regionMatches(true, …)` 로 찾는다(소문자 변환 금지 — 길이가 바뀌는 글자가 있다).
  `reason` — `!js.balanced()` → `UNBALANCED` · `js.inTemplate()[i]` → `"템플릿 리터럴 안 " + word` · `raw[i]` → `"<pre>·<textarea> 안 " + word` · 아니면 null.
  메서드마다 복잡도 15 이하 — `js` 는 글 자리·코드 자리 걸음을 따로 뺀다(작은 상태 클래스 하나)
- 시험: `ZonesTest.templateLines`(한 줄 템플릿 뒤 줄은 거짓 · 여러 줄 템플릿 안 줄은 참 · `'`'` 문자열 안 백틱은 안 센다 · 겹친 여러 줄 템플릿의 안쪽 줄·`${ }` 안 코드 줄은 참 · 템플릿 닫힌 뒤 줄은 거짓) · `unbalancedIsFlagged`(백틱 하나 → `balanced` 거짓) · `codeMask`(`"a(b)"` 안 `(` 는 거짓 · `` `x ${f(1)} y` `` 의 `f(1)` 은 참·`x`·`y` 는 거짓) · `rawLines`(jsp `<pre>`·`<TEXTAREA>` 여는 줄~닫는 줄 참 · `<!-- <pre> -->` 는 거짓 · `<pretty>` 는 거짓 · lang `ts` 는 전부 거짓 · 안 닫힌 `<textarea>` 는 끝까지)
- 검증: `bash scripts/mvn.sh -q test -Dtest='ZonesTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0. 동작 변화 없음(아직 안 쓴다)

### 5-106 — debugger·console 지우기에 구역
- 대상: `core/check/Cleanup.java`(`deleteDebugger:530-560` · `deleteConsole:566-625`) · `CleanupTest.java` + 켜기 셋(+ `CleanupRoutesTest` 0-98 앞 꼴)
- 변경: 두 메서드에서 `ticks` 배열과 채우는 이중 루프를 지우고 머리에
  ```java
  Zones.Js js = Zones.js(stripped);
  boolean[] raw = Zones.rawLines(s.text(), s.lang());
  ```
  `ticks[i] % 2 != 0` 분기를 `String zone = Zones.reason(js, raw, i, "debugger")`(console 은 `"console"`) → `zone != null` 이면 `skips.add(new Skip(DELETE, "줄 " + line + ": " + zone))` 로. 분기 자리·차례는 그대로(debugger: `only` → 주석 → **구역** → 중괄호 없음 / console: `CONSOLE_DROP` → **구역** → …). `Cleanup` 에 메서드를 더하지 않는다
- 시험(먼저): `CleanupTest.templateAndRawTextAreKept` — 음성 ① ts: `` const h = `<ul>${xs.map(x => ` `` ⏎ `debugger;` ⏎ `console.log(x);` ⏎ `` `).join('')}</ul>`; `` → 두 줄 그대로, 사유 `템플릿 리터럴 안 debugger`·`템플릿 리터럴 안 console` ② js: `var q = '`';` ⏎ `` var t = ` `` ⏎ `debugger;` ⏎ `` `; `` → 그대로(RED 의 핵심 — 지금은 지운다) ③ jsp: `<pre>`⏎`console.log("예시");`⏎`debugger;`⏎`</pre>` · `<textarea>`⏎`debugger;`⏎`</textarea>` → 그대로, 사유 `<pre>·<textarea> 안 …` ④ js: 백틱 하나만 있는 파일(`var r = /`/;`)의 `debugger;` → 그대로, 사유 `UNBALANCED`. 양성: 같은 파일들의 구역 밖 `debugger;`·`console.log(1);` 줄은 지워진다
- 의도된 골든 변화: 없음(`guards/next.ts`·`console/pos.tsx` 의 기존 사유 글 그대로). 바뀌면 사다리
- 켜기: §3-0 `5-106` 줄 — `common.debugger`·`tsx.debugger` + `CleanupRoutesTest` 를 0-98 앞 꼴로 + 스모크 `r_common.debugger` 줄·주석 삭제
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupRunnerTest,SelfCheckTest,CleanupRoutesTest,SmokeHtmlUnitTest,CodeCliTest'` → `bash scripts/verify.sh`
- 의존: 5-105

### 5-107 — console 인자: `/`·부수 효과·선형
- 대상: 새 파일 `core/check/ConsoleArgs.java` · `core/check/Cleanup.java`(`:534` · `deleteConsole:600·604-612` · `consoleEnd:652`) · `CleanupTest.java` + 켜기 셋
- 변경:
  ```java
  /** console 문장 인자 판정(설계 70 §3-7) — 지우면 동작이 바뀌는 꼴 */
  final class ConsoleArgs {
      static final String SLASH = "인자에 / — 정규식 리터럴일 수 있어 괄호 셈을 못 믿는다";
      static final String SIDE_EFFECT = "인자에 호출·증감·대입 — 지우면 부수 효과가 사라질 수 있어 손으로";
      /** 호출로 안 세는 이름 — 늘리지 않는다 */
      private static final Set<String> PURE = Set.of("stringify", "String", "Number", "Boolean", "parseInt", "parseFloat", "toString", "toFixed", "typeof", "function");
      private static final Set<String> WORDS = Set.of("await", "yield", "delete", "new");

      /** sl[from] 의 open(여는 괄호) 뒤부터 sl[end] 의 마지막 ) 앞까지 — 줄은 \n 으로 잇는다 */
      static String text(String[] sl, int from, int open, int end)
      /** 지우면 안 되는 사유, 지워도 되면 null — Zones.js 의 코드 자리만 본다. / 가 먼저 */
      static String risk(String args)
      /** 끝 공백 뗀 앞 줄이 <script …> 로 끝나나(대소문자 무시, 손 훑기 — 선형) */
      static boolean scriptOpen(String prev)
  }
  ```
  `risk` — 코드 자리 글자만: `/` → `SLASH`(전체를 먼저 훑는다) · `++`·`--` → `SIDE_EFFECT` · `=` 인데 앞 글자가 `=!<>` 가 아니고 뒤 글자가 `=`·`>` 가 아니면 `SIDE_EFFECT` · `(` 앞(공백 건너)이 이름 글자(`[\w$]`)면 그 이름이 `PURE` 에 없을 때, 앞이 `)`·`]` 면 무조건 `SIDE_EFFECT` · 낱말이 `WORDS` 면 `SIDE_EFFECT`.
  `scriptOpen` — `prev` 가 `>` 로 끝나고, 그 앞 마지막 `>` 다음부터에서 `<script`(대소문자 무시) 뒤가 낱말 글자가 아닌 자리가 있다.
  `Cleanup`:
  - `:534` → `"^\\s*debugger\\s*+;?\\s*+$"` 두 꼴 다
  - `:652` → `l.substring(k + 1).matches("\\s*+;?\\s*+")`
  - `:600` 의 `!SCRIPT_OPEN.matcher(prev).find()` → `!ConsoleArgs.scriptOpen(prev)`, 상수 `SCRIPT_OPEN:74` 삭제
  - `:605-612` 의 `end == -1`·`end == -2` 두 분기 뒤, `marked` 판정 앞에
    ```java
    String risk = ConsoleArgs.risk(ConsoleArgs.text(sl, i, m.end() - 1, end));
    if (risk != null) {
        skips.add(new Skip(DELETE, "줄 " + line + ": " + risk));
        continue;
    }
    ```
- 시험(먼저): `CleanupTest.consoleArgsRisk` — 음성(tsx, 한 줄씩, 전부 그대로): `console.log(q.pop());` · `console.log(i++);` · `console.log(x = 5);` · `console.log(await load());` · `console.log(save(a));` → `SIDE_EFFECT` / `console.log(s.replace(/\(/g, ''));`⏎`next();`⏎`after();` → 세 줄 다 남고 `SLASH`(RED 의 핵심 — 지금은 셋을 지운다) / `console.log(a / b);` → `SLASH`. 양성(지워진다): `console.log("a", b);` · `console.log(JSON.stringify(o));` · `console.log('x: ' + o.p.q);` · `console.log(a == b, c >= d, e => e);` · `console.log("f(x)/2");`. `ConsoleArgs.scriptOpen` 참: `<script>` · `<SCRIPT type="text/javascript">` / 거짓: `<scripts>` · `<script> x` · `a > b`.
  `CleanupTest.consoleLongInputsAreLinear` — `assertTimeoutPreemptively(Duration.ofSeconds(5), …)` 셋: `console.log(1)` + 공백 20만 + `x` · `debugger` + 공백 20만 + `x` · 앞 줄이 `<script ` 2만 번 반복(닫는 `>` 없음) 뒤 `console.log(1);`. RED: 시간 초과
- 의도된 골든 변화: `console-pos.tsx.txt`·`console-pos.jsp.txt`·`guards-*.txt`·`steps.json` 에서 인자에 호출·`/` 가 든 console 줄이 남는 쪽으로 바뀌는 것만(`golden/check/self-check.txt` 의 `deleteConsole(4)` 값이 내려가거나 줄이 빠진다)
- 켜기: §3-0 `5-107` 줄
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,CleanupRunnerTest,SelfCheckTest,CleanupRoutesTest,SmokeHtmlUnitTest'` → `bash scripts/verify.sh`
- 의존: 5-106
- 사다리: `deleteConsole` 복잡도가 21 을 넘으면 `end < 0` 두 분기를 `ConsoleArgs` 의 사유 표(`-1`·`-2` → 기존 글 그대로)로 합친다. 기존 사유 글자는 안 바꾼다

### 5-108 — 마크업 끼우기: 날글 밖만 · 선형
- 대상: `core/check/MarkupFix.java`(`apply:44-67` · `insertAll:95-104`) · `CleanupTest.java` + 켜기 셋(`OPTIONAL` 아홉 · javadoc · 단언 글)
- 변경:
  ```java
  static final String RAW = "<pre>·<textarea> 안 글 — 마크업이 아니다";

  // apply: lines 뒤에
  boolean[] raw = Zones.rawLines(s.text(), s.lang());
  int[] starts = starts(s.text());                       // 줄 시작 자리(0 기준 줄 → 글 자리)
  // ins 를 모은 뒤 insertAll 앞
  ins.removeIf(i -> rawLine(raw, starts, i.at(), skips)); // 날글 줄이면 버리고 사유(줄마다 한 번)
  // byLine.forEach 안 — raw[no - 1] 이면 사유만 남기고 그 줄은 안 고친다(HTML_LANG·BLANK)
  ```
  `insertAll(lines, starts, ins, out)` — `ins` 를 `at` 오름차순으로, 줄 번호는 `Arrays.binarySearch(starts, at)`(음수면 `-(r) - 2`), 줄마다 `StringBuilder` 하나에 `줄 글[앞 자리, col)` + 끼울 글을 차례로 붙이고 끝에 나머지. 같은 줄 여러 삽입도 한 번에. `lastIndexOf`·`substring(0, start).chars()` 를 지운다.
  날글 사유는 `Set<Integer>` 로 줄마다 한 번(`줄 N: ` + `RAW`)
- 시험(먼저): `CleanupTest.markupSkipsRawText` — 음성(jsp): `<textarea>`⏎`<table><tr><th>a</th></tr></table>`⏎`<a href="x" target="_blank">y</a>`⏎`</textarea>` → 두 줄 그대로 + MARKUP 사유 `RAW` 둘. 양성: 같은 파일 textarea 밖의 같은 표·링크는 `scope="col"`·`rel=…` 이 끼워진다. RED: textarea 안이 바뀐다.
  `CleanupTest.manyInsertsAreLinear` — `assertTimeoutPreemptively(5초)`: `<table>` 안 `<tr><th>a</th></tr>` 3만 줄(줄마다 하나) · 같은 것을 한 줄로 이은 것. 끝에 `scope=` 수 = 3만. RED: 시간 초과
- 의도된 골든 변화: 없음(`markup/*` 에 pre·textarea 없음). `ThScopeFixTest` 그대로
- 켜기: §3-0 `5-108` 줄 — `a11y.thScope`, 끝 상태(`OPTIONAL` 아홉·javadoc·단언 글·스모크 23)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,ThScopeFixTest,BlankRelRuleTest,SelfCheckTest,CleanupRoutesTest,SmokeHtmlUnitTest'` → `bash scripts/verify.sh`
- 의존: 5-105

### 5-109 — twr: 삼키는 finally · 꼴 B catch
- 대상: `core/check/ResourceAst.java`(`Planner:349-379`) · `ResourceAstTest.java` · `CleanupTest.java`(`resourceStage:143` 기대값)
- 변경:
  ```java
  static final String SWALLOWED = "close 예외를 삼키며 닫는다(closeQuietly·try-catch) — 예외 경로가 바뀌어 손으로";
  static final String CATCH_B = "catch 가 있는 try — close 예외가 그 catch 로 들어가 손으로";

  // of() 끝의 return 을
  if (t.getTryBlock().getStatements().size() <= b.size() + tail.size() || !assignsFirst(t, b)) {
      return Optional.empty();
  }
  return Optional.of(new Twr(t, a, tail, manual(t, fin, tail, !b.isEmpty())));

  /** 설계 70 §3-9 — 지적은 서고 고침은 손으로: 삼키는 close · 꼴 B + catch */
  private static String manual(TryStmt t, boolean fin, List<Statement> tail, boolean formB) {
      List<Statement> closes = fin ? t.getFinallyBlock().orElseThrow().getStatements() : tail;
      if (closes.stream().anyMatch(c -> bareClose(c).isEmpty())) {
          return SWALLOWED;
      }
      return formB && fin && t.getCatchClauses().isNonEmpty() ? CATCH_B : "";
  }
  ```
  (`formB` 는 꼴 B 목록 `b` 가 비지 않았나 — `a.addAll(b)` 는 `b` 를 안 바꾼다)
- 시험(먼저): `ResourceAstTest.swallowingFinallyIsManual` — 음성 둘: finally `{ IOUtils.closeQuietly(is); }` · finally `{ if (is != null) { try { is.close(); } catch (IOException e) { } } }` → `manual()` = `SWALLOWED`, 지적은 선다. `formBWithCatchIsManual` — `InputStream is = null; try { is = new FileInputStream(p); return is.read(); } catch (IOException e) { return -1; } finally { if (is != null) is.close(); }`(메서드 `throws IOException`) → `CATCH_B`. 양성: catch 없는 꼴 B + 맨 close(`resource/Pos.java` 첫 메서드 꼴) · 꼴 C(catch 있음, 몸 끝 맨 close) → `manual()` 빈 글. RED: 셋 다 빈 글
- 의도된 골든 변화: `resource-Pos.java.txt`·`steps.json` 에서 `resource/Pos.java:50-64`(catch + finally 안 try/catch close) 메서드가 원문으로 돌아가고 사유가 는다. `CleanupTest.resourceStage:143` 의 그 메서드 단언·사유 목록을 그에 맞춘다(단언을 지우지 않고 「남는다 + 사유」 로 바꾼다). 그 밖 메서드가 돌아가면 사다리
- 검증: `bash scripts/mvn.sh -q test -Dtest='ResourceAstTest,CleanupTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0
- 사다리: `Planner` 메서드 수가 30 을 넘어 SelfCheck 가 빨강이면 `manual` 을 `ResourceAst`(바깥) 의 private static 으로 옮긴다

### 5-110 — interrupt: 마지막 문장이 탈출
- 대상: `core/check/InterruptFix.java`(`leaves:109-113`) · `CleanupTest.java`
- 변경:
  ```java
  /** 몸의 마지막 문장이 루프를 벗어난다(return·throw·바깥 break) — 조건부 탈출은 안 센다(설계 70 §3-10: 안 벗어난 길에서 다음 대기가 곧바로 깨진다) */
  private static boolean leaves(BlockStmt body) {
      return body.getStatements().getLast()
              .filter(s -> s instanceof ReturnStmt || s instanceof ThrowStmt || s instanceof BreakStmt b && outerBreak(b, body)).isPresent();
  }
  ```
  javadoc `:26` 의 「루프 안(몸에 break·return·throw 없음)」 → 「루프 안(몸의 마지막 문장이 break·return·throw 가 아님)」. 안 쓰게 된 import 는 지운다
- 시험(먼저): `CleanupTest.interruptConditionalLeaveIsLoop` — 음성: `while (run) { try { Thread.sleep(1); } catch (InterruptedException e) { if (stop) { return; } } }` · 같은 꼴 `if (stop) break;` → 글 불변, 사유 `IN_LOOP`. 양성: `catch (InterruptedException e) { log(e); return; }` · `… break; }`(`interrupt/Pos.java:85-91` 꼴) → interrupt 가 끼워진다. RED: 음성 둘에 끼워진다
- 의도된 골든 변화: 없음(`interrupt/Pos.java` 의 `loop` 는 탈출 없음, `loopBreak` 는 마지막 break)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,JavaShapeTest'` → `bash scripts/verify.sh`
- 의존: S0

### 5-111 — 로거: 먼저 도는 static 초기화 · 도메인 Log
- 대상: `core/check/LogFix.java`(`find:227-243` · `usable:246-266` · `insertable:285-299`) · `CleanupTest.java`
- 변경:
  ```java
  static final String EARLY_INIT = "로거보다 먼저 도는 static 초기화가 이 클래스 코드를 부른다 — 로거가 null 일 수 있어 손으로";
  private static final String LOG = "Log";
  private static final String LOGGING_LOG = ".logging.Log";

  /** 설계 70 §3-11 — before 줄보다 앞의 static 필드 초기화식·static 블록에 new <이 형식>(…) 또는 수신자 없는 호출이 있다 */
  private static boolean earlyInit(TypeDeclaration<?> t, int before) {
      String self = t.getNameAsString();
      return t.getMembers().stream()
              .filter(m -> m.getBegin().filter(p -> p.line < before).isPresent())
              .filter(m -> m instanceof FieldDeclaration f && f.isStatic() || m instanceof InitializerDeclaration i && i.isStatic())
              .anyMatch(m -> m.findAll(ObjectCreationExpr.class).stream().anyMatch(o -> self.equals(o.getType().getNameAsString()))
                      || m.findAll(MethodCallExpr.class).stream().anyMatch(c -> c.getScope().isEmpty()));
  }

  /** Logger 는 그대로 · Log 는 로깅 패키지에서 온 것만(도메인 형식 Log 는 로거가 아니다) */
  private static boolean loggerType(Type type, CompilationUnit cu) {
      String simple = DeclaredTypes.simpleType(type);
      if (!LOG.equals(simple)) {
          return LOGGER_TYPES.contains(simple);
      }
      return type.asString().endsWith(LOGGING_LOG) || cu.getImports().stream().filter(i -> !i.isStatic())
              .anyMatch(i -> i.isAsterisk() ? i.getNameAsString().endsWith(".logging") : i.getNameAsString().endsWith(LOGGING_LOG));
  }
  ```
  - `find:234` — `LOGGER_TYPES.contains(…)` 를 `loggerType(v.getType(), c.findCompilationUnit().orElseThrow())` 로
  - `usable` 머리(찾은 로거) — `!lg.insert() && lg.isStatic() && lg.owner() instanceof TypeDeclaration<?> t` 이고 그 필드가 있으면(`t.getFieldByName(lg.name())` — Lombok 은 없다) `earlyInit(t, 필드 시작 줄)` → `EARLY_INIT`
  - `insertable` 끝 — `slot.reason()` 이 비었고 `earlyInit(k, slot.line() + 1)` 이면 `EARLY_INIT`(끼울 자리는 마지막 static 필드 뒤라 그 필드까지가 「앞」)
  - 메서드가 15 를 넘으면 `usable` 의 JUL·System.Logger 두 분기를 도우미로 뺀다
- 시험(먼저): `CleanupTest.logSkipsEarlyStaticInit` — 음성: `class S { private static final S INSTANCE = new S(); private static final Logger log = LoggerFactory.getLogger(S.class); private S() { System.out.println("init"); } void m() { System.out.println("m"); } }`(줄 나눠서) → 두 println 그대로, LOG 사유 `EARLY_INIT` 둘 · 로거 없는 같은 꼴(베낄 꼴을 주는 `loggers` 로 — `loggerInserted:581` 의 방식) → 로거를 안 끼운다. 양성: 로거 선언이 `INSTANCE` 보다 위 → `log.debug` 로 바뀐다 · `static final Map<String, String> M = new HashMap<>();` 뒤 로거 → 바뀐다. RED: 음성이 바뀐다.
  `CleanupTest.logDomainTypeIsNotLogger` — 음성: import 없는 `private Log log;` 필드 + `System.out.println("x")` → `log.debug` 가 **안** 생긴다(사유는 기존 `NO_LOGGER`). 양성: `import org.apache.commons.logging.Log;` + `private static final Log log = LogFactory.getLog(T.class);` → `log.debug("x")`. RED: 음성에 `log.debug`
- 의도된 골든 변화: 없음(`log/*` 픽스처에 싱글턴·도메인 Log 없음)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,LoggerShapeTest,CleanupRunnerTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0

### 5-112 — 카드 가리기: 따옴표 훑기 · 선형
- 대상: `core/check/PiiFix.java`(`cover:52-72` · `quoted:84-91`) · `CleanupTest.java`
- 변경: `quoted(line, at)` 를 지우고 줄마다 훑개 하나
  ```java
  /** 설계 70 §3-12 — 줄을 왼쪽부터 한 번. to(자리) 까지 나아가 그 자리가 ' " ` 안인가 — 일치가 자리 오름차순이라 되돌아가지 않는다 */
  private static final class Quotes {
      private final String line;
      private int at;
      private char quote;

      Quotes(String line) { this.line = line; }

      boolean inside(int to) {
          for (; at < to; at++) {
              char c = line.charAt(at);
              if (quote != 0 && c == '\\') {
                  at++;
              } else if (quote != 0) {
                  quote = c == quote ? 0 : quote;
              } else if (QUOTES.indexOf(c) >= 0) {
                  quote = c;
              }
          }
          return quote != 0;
      }
  }
  ```
  `cover` — `while (m.find())` 앞에서 `Quotes q = new Quotes(line)`, `quoted(line, m.start())` → `q.inside(m.start())`
- 시험(먼저): `CleanupTest.piiQuoteScan` — 음성(js): `var a = "it's" + 4111111111111111;` → 그대로, 사유 `UNQUOTED`(RED: 가려져 `************1111` 이 맨 글자로 남는다) · `var b = 'a\'b' + 4111111111111111;` → 그대로. 양성: `var c = "it's", d = '4111111111111111';` → `'************1111'`.
  `CleanupTest.piiLongLineIsLinear` — `assertTimeoutPreemptively(5초)`: 한 줄에 `'4111111111111111',` 2만 번(js). 끝에 가린 수 = 2만. RED: 시간 초과
- 의도된 골든 변화: 없음. `pii-Pos.java.txt` 가 바뀌면 사다리
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,LuhnRuleTest'` → `bash scripts/verify.sh`
- 의존: S0
- 사다리: 시간 초과가 `PiiFix` 가 아니라 규칙 정규식(검사 쪽)에서 나면 입력을 5천 번으로 줄이고 이력 「드러난 것」 에 적는다(검사 규칙은 범위 밖)

### 5-113 — 섞인 줄끝 파일은 건너뜀
- 대상: `core/fs/LocalFiles.java`(`Text:86` · `read:337` · `lineEnding:341-354`) · `core/check/CleanupRunner.java`(`preview:95-103` · `apply:163-171`) · `LocalFilesTest.java` · `CleanupRunnerTest.java` · `JsonErrorTest.java`(보정 한 줄)
- 변경:
  ```java
  // LocalFiles
  /** mixed — CRLF 와 LF 단독이 한 파일에 같이 있다(lineEnding 은 다수 쪽). 쓰면 전 줄이 한 줄끝으로 간다 */
  public record Text(String text, String encoding, String lineEnding, boolean mixed) {
      public Text(String text, String encoding, String lineEnding) {
          this(text, encoding, lineEnding, false);
      }
  }
  // read:337 — return new Text(text.replace("\r\n", "\n"), enc, lineEnding(text), mixedEol(text));
  static boolean mixedEol(String text)   // \r\n 과 앞이 \r 이 아닌 \n 이 둘 다 있다 — 한 번 훑기

  // CleanupRunner
  static final String MIXED_EOL = "줄끝이 섞여 있다(CRLF·LF) — 고치면 안 고친 줄의 줄끝도 바뀐다. file.lineEnding 을 골라 통일한 뒤 다시";
  private static boolean keepsEol(Cleanup c, String lineEnding) {   // 통일을 고르지 않았다
      return lineEnding == null || lineEnding.isBlank() || !c.wanted().contains("file.lineEnding");
  }
  ```
  `preview` — `read++` 뒤 `clean` 앞: `t.mixed() && keepsEol(c, lineEnding)` 이면 `rows.add(new FileRow(rel, hash(…), "", 0, 0, false, t.lineEnding(), t.encoding(), List.of(), List.of(new Cleanup.Skip("file", MIXED_EOL))))` 뒤 `continue`(내부 오류 줄 `:100-101` 과 같은 꼴).
  `apply` — hash 비교 뒤 `clean` 앞: 같은 조건이면 `failed++`, `Written(ref.rel(), false, MIXED_EOL, null)`, 보고 줄 ` · 실패: ` + `MIXED_EOL`, `continue`.
  `Text` 의 JSON 은 화면으로 안 나간다(`DiffRoutes:34-35`·`AnalyzeRoutes:409` 는 성분만 꺼내 쓴다) — 응답 꼴 불변.
  보정: `JsonErrorTest.nullListElementIs400:154` 의 `"path":"x"` 를 임시 폴더 절대 경로로 바꾸고 본문에 `null` 글이 든 400 인지 단언(`목록 files 안에 null 이 있다`)
- 시험(먼저): `LocalFilesTest.readDetectsEncodingAndLineEnding:78` 에 — `a\r\nb\nc\r\n` → `mixed` 참·`lineEnding` CRLF · `a\r\nb\r\n` → 거짓 · `a\nb\n` → 거짓 · 줄바꿈 없음 → 거짓. `CleanupRunnerTest.mixedEolFileIsSkipped` — 음성: `Pos.java`(안 쓰는 import 하나)를 CRLF·LF 섞어 쓰고 `NO_EOL` 규칙으로 preview → 그 줄 `changed` 거짓·사유 `MIXED_EOL`, apply 에 그 ref 를 줘도 결과 폴더에 안 쓴다. 양성: 같은 파일을 `RULES` + `lineEnding "LF"` 로 → 바뀜, 결과 파일에 `\r` 0. 섞이지 않은 CRLF 파일은 `NO_EOL` 로도 고쳐지고 CRLF 그대로. RED: 음성이 바뀐다
- 의도된 골든 변화: 없음(빠른 레인). corpus 는 V-64a
- 검증: `bash scripts/mvn.sh -q test -Dtest='LocalFilesTest,CleanupRunnerTest,CleanupTest,JsonErrorTest,SelfCheckTest'` → `bash scripts/verify.sh`
- 의존: S0
- 사다리: `CleanupRunner.preview`·`apply` 는 인자 수로 기준선에 있다(값 불변). 복잡도가 새로 15 를 넘으면 건너뜀 줄 만들기를 도우미로 뺀다

### 0-97 — LocalFiles 한 인자 생성자를 main 이 못 부른다
- 대상: `src/test/java/kr/ejg/toolbox/ArchitectureTest.java`
- 변경:
  ```java
  /** 0-97 — 프로필 폴더 거절(절대 규칙 2)은 두 인자 생성자가 든다. main 은 한 인자 생성자를 안 부른다 */
  @Test
  void localFilesNeedsForbiddenList() {
      noClasses().that().doNotHaveFullyQualifiedName("kr.ejg.toolbox.core.fs.LocalFiles")
              .should().callConstructor(kr.ejg.toolbox.core.fs.LocalFiles.class, java.nio.file.Path.class)
              .because("절대 규칙 2: LocalFiles 는 프로필 폴더를 거절 목록에 받아야 한다 — App 처럼 두 인자로 만든다")
              .check(main);
  }
  ```
- 시험: 이 시험이 강제 지점이다. RED 확인 — `App.java:177` 을 한 인자로 잠깐 바꿔 빨강을 보고 되돌린다(되돌린 뒤 `git diff --stat` 에 `App.java` 없음)
- 검증: `bash scripts/mvn.sh -q test -Dtest='ArchitectureTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 java 만

### 6-42 — Stmt 생성자가 id 꼴을 잰다
- 대상: 새 파일 `core/analyze/StmtId.java` · `core/analyze/JavaGraph.java`(`Stmt:66` · `STMT_ID:104-105` · `statement:537·546·553`) · `core/analyze/AnalyzeStore.java:308` · `JavaGraphTest.java` · `AnalyzeStoreTest.java` · `golden/check/self-check.txt`(한 줄)
- 변경:
  ```java
  /** 문장 id 꼴(6-40·6-42, 규칙 3) — 공백·연산자가 든 글은 SQL 본문이라 id 로 안 담는다 */
  final class StmtId {
      private static final Pattern PLAIN = Pattern.compile("[\\p{L}\\p{N}_.$-]{1,300}");
      /** 곁 등록부 꼬리(JavaGraph:510·744·825) */
      private static final Pattern ANY = Pattern.compile("[\\p{L}\\p{N}_.$-]{1,300}(?:#(?:jdbc|em|qdsl))?");

      static boolean plain(String s) { return s != null && PLAIN.matcher(s).matches(); }
      static boolean ok(String s) { return s != null && ANY.matcher(s).matches(); }
      /** 글은 싣지 않는다(규칙 3) */
      static void require(String s) {
          if (!ok(s)) {
              throw new IllegalArgumentException("문장 id 꼴이 아니다");
          }
      }
  }

  // JavaGraph
  public record Stmt(String id, String resolution) {
      public Stmt {
          StmtId.require(id);
      }
  }
  ```
  `STMT_ID` 선언·javadoc 두 줄 삭제, 세 자리 `STMT_ID.matcher(x).matches()` → `StmtId.plain(x)`. `AnalyzeStore:308` — `StmtId.ok(rs.getString(2))` 일 때만 `new JavaGraph.Stmt(…)`(아니면 그 줄을 건너뛴다). `StmtId`·`JavaGraph`·`AnalyzeStore` 는 같은 패키지(`core.analyze`)라 package-private 로 둔다
- 시험(먼저): `JavaGraphTest.stmtRejectsSqlText` — `assertThrows(IllegalArgumentException.class, () -> new JavaGraph.Stmt("UPDATE TB_X SET A = 1", "literal"))`, 예외 글에 `UPDATE` 없음 · `new Stmt("a.b.Dao.find#jdbc", "jdbc")`·`"ns.selectList"`·`"com.x.Repo.findById"` 는 통과 · `"a#b"`·`""` 는 거절. 기존 `sqlTextIsNotAStatementId` 그대로 초록. `AnalyzeStoreTest.oddIdRowsAreDroppedOnRead` — `analyze_stmt` 에 `ns_id = 'a;b'`(공백 없음 — V015 가 못 지운 꼴) 줄을 직접 넣고 되읽기 → 예외 없이 그 줄만 빠진다. RED: 생성자 통과 · 되읽기에 `a;b`
- 의도된 골든 변화: `golden/check/self-check.txt` `JavaGraph` `길이 1012 > 1000` → `1013`(한 줄). `golden/analyze/*-graph.json` diff 0
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,AnalyzeStoreTest,SelfCheckTest,AnalyzeRoutesTest'` → `bash scripts/verify.sh`
- 의존: S0. 레인 corpus(V-64a 가 분석 골든을 잰다)
- 사다리: fqcn 꼴 다섯 자리(`:476·529·697·746·827`)에서 생성자 예외가 나는 시험이 있으면 그 id 의 글자를 이력에 적고(본문 없음) `ANY` 를 넓히지 않는다 — 그 자리를 `StmtId.ok` 로 먼저 재서 아니면 `note("statement", …)` 로 돌린다

### 6-41 — 파일 오류문: 큰따옴표 토막·숫자 낱말
- 대상: `core/analyze/SqlProbe.java`(`names:283-…` · 상수 `:60-65`) · `SqlProbeTest.java` · `web/DbCorpusBase.java`(`roFileMessages:780-783`)
- 변경:
  ```java
  /** 6-41 — MySQL 큰따옴표 문자열. 방언을 안 가르고 전부 뗀다(인용 식별자는 … 로 잘린다 — 규칙 3 쪽으로) */
  private static final Pattern DQUOTED = Pattern.compile("\"(?:[^\"]|\"\")*+\"");

  // names — LITERAL 을 뗀 글에서 DQUOTED 도 떼고, 모은 낱말 가운데 첫 글자가 숫자인 것은 버린다
  ```
  `DbCorpusBase.roFileMessages:781` 조건에 `|| up.contains("SELECT") || up.contains("TB_RO84 WHERE")`(`up` = 대문자 fileMessage)
- 시험(먼저): `SqlProbeTest.fileMessageDropsDoubleQuotedAndNumbers` — `forFile("Truncated incorrect DOUBLE value: 'abc'", "SELECT a FROM t WHERE a = \"abc\"")` 에 `abc` 없음 · `forFile("Unknown column '1' in 'where clause'", "SELECT a FROM t WHERE 1 = 1")` 에 `'1'` 없음 · `forFile("Unknown column 'x' in …", …)` 기존 단언(`fileMessageKeepsOnlyOwnIdentifiers:125-132`) 그대로 초록 · `forFile("ORA-00904: \"NOPE\": invalid identifier", "SELECT NOPE FROM T")` 그대로(SQL 쪽에 따옴표 없음). RED: 앞 둘이 남는다
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlProbeTest,AnalyzeRoutesTest'` → `bash scripts/verify.sh`. 실물은 V-64 `--db`
- 의존: S0. 레인 db + corpus
- 사다리(V-64 에서): 새 부재 단언이 어느 방언에서 빨강이면 그 방언·오류문 길이만 이력에 적고(글 없음) `forFile` 끝에 「오류문에 문장 SQL 의 첫 낱말(`SELECT`·`WITH`·`INSERT`·`UPDATE`·`DELETE`)이 낱말로 나오면 그 자리부터 `…`」 를 더한다(고침 둘 안). Tibero 는 컨테이너가 없어 미검증으로 남긴다

### 0-93 — ReadOnlyJdbc: 의도된 거절 고정 · 잇기 낱말 둘
- 대상: `core/conn/ReadOnlyJdbc.java`(`WRITE:37-43`) · `ReadOnlyJdbcTest.java`
- 변경: `WRITE` 의 9-6 잇기 줄에 `"RECEIVE", "CONVERSATION"`(주석 「9-6·0-93」). 판정 코드는 안 고친다
- 시험(먼저):
  ```java
  /** 0-93 — 엄격한 쪽(설계 69 §3-6)의 오거절 네 꼴은 의도다. 통과로 바꾸려면 판정을 원문 낱말에서 토큰으로 바꿔야 한다 */
  @Test
  void intendedRefusals() {
      for (String sql : List.of("SELECT 'ENABLE' FROM T", "SELECT 'BACKUP' FROM T", "SELECT ENABLE FROM T", "SELECT A FROM BACKUP",
              "SELECT CHECKPOINT FROM T", "SELECT * FROM V$BACKUP", "SELECT * FROM #BACKUP",
              "SELECT (SELECT A FROM T FOR XML PATH('')) AS L, SHARE FROM U",
              "SELECT 1 RECEIVE TOP(1) * FROM Q", "SELECT 1 SEND ON CONVERSATION @h MESSAGE TYPE M ('x')", "SELECT 1 END CONVERSATION @h")) {
          assertThrows(SQLFeatureNotSupportedException.class, () -> ReadOnlyJdbc.check(sql), sql);
      }
  }
  ```
  `stillPasses:118` 에 `"SELECT SEND_DT, RECEIVER, CASE WHEN A = 1 THEN 1 END FROM T"`(통과). RED: 끝 셋(RECEIVE·CONVERSATION)이 지난다
- 검증: `bash scripts/mvn.sh -q test -Dtest='ReadOnlyJdbcTest'` → `bash scripts/verify.sh`. 실물·db 골든은 V-64
- 의존: S0. 레인 db + corpus

### V-64a — corpus 골든 확인
- `bash scripts/verify.sh --corpus`(약 7~12분, 표본 폴더·node·Puppeteer 25.8.0). 허용 변화는 `golden/corpus/cleanup-*.json` 뿐 —
  ① `steps` 의 고친 수가 **주는** 것: value(`java.equalsLiteralFirst`)·order·resource·interrupt·log·delete(console·debugger)·markup·pii
  ② `skips` 에 새 사유 키가 **느는** 것: `RECEIVER` · `SKIP_FIELD_ORDER` · `XML_NO_DECL` · `Zones.UNBALANCED` · `템플릿 리터럴 안 …`(기존 키 수 변화) · `<pre>·<textarea> 안 …` · `ConsoleArgs.SLASH`·`SIDE_EFFECT` · `MarkupFix.RAW` · `SWALLOWED`·`CATCH_B` · `EARLY_INIT`
  ③ `changed` 가 주는 것
  고친 수가 **느는** 줄 · 기존 사유 키가 사라지는 줄 · 분석·검사·DB 골든의 변화는 허용 밖 — 고치기 둘까지, 넘으면 `wip/V-64a`
- `CleanupCompileCorpusTest`(고친 소스가 컴파일된다, 등급 A)·`written > 500` 이 초록인지 따로 본다. `written` 이 500 아래로 내려가 빨강이면 멈춘다(7절)
- diff 줄 수·규칙별 고친 수 전→후·새 사유별 건수·벽시계를 이력에
- 의존: 5-113 · 0-93(코드 행 전부)

### R-31 — 손 판정 · 처분 표
- `design/compare-2026-10-28.md` 끝에 절 `## ㉞ 번들 85 — 코드 정리 Critical 열둘·Minor 다섯·꼬리 넷(2026-11-15)`:
  1. 처분 표 — 키 21(E 열일곱 + 꼬리 넷) × (행 · 강제 지점 시험 · 닫힘/남음). E-soe·E-applyNull 은 「번들 84 가 닫음 — 보정 확인」 한 줄
  2. 켜기 표 — §3-0 을 실제 커밋 해시와 함께. `Cleanup.OPTIONAL` 아홉·기본 켬 23
  3. V-64a 의 규칙별 고친 수 전→후·새 사유별 건수. `ConsoleArgs.SIDE_EFFECT` 로 건너뛴 console 가운데 표본 스무 줄을 눈으로(파일:줄만 적는다 — 본문 없음) — 허용 목록 밖 「읽기뿐인 호출」 이 몇 줄인지 센다(목록을 늘릴지는 사람이 정한다 — 새 행 후보로만)
  4. `RECEIVER` 로 건너뛴 equals 가운데 스무 줄 — 수신자 꼴 분포(메서드 결과·다른 형식·못 찾은 필드)
  5. 끝에서 끝 — `run.bat --port 18850 --profile <예시>` 로 띄워 `/tools/code_cleanup.html` 기본 체크 23 을 `curl http://127.0.0.1:18850/api/cleanup/rules` 의 `opt` 아홉으로 확인. 끝에 서버를 끈다
- 코드·레인 지문이 안 바뀐다(고침이 나오면 그 행의 시험을 먼저 더해 따로 커밋)
- 의존: V-64a

### V-64 — 정적 분석 · `--db`
1. `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify`(PMD·SpotBugs·ErrorProne — 빠른 verify 가 안 본다). 새 짝이 뜨면 코드를 고친다(기준선에 더하지 않는다). 고치기 둘까지
2. 여유 RAM — PowerShell `(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB`. 15 미만이면 7절 중단
3. `bash scripts/docker-up.sh` → `bash scripts/verify.sh --db`(컨테이너 아홉, 17~35분). `--corpus` 와 겹쳐 띄우지 않는다. 그동안 다른 JVM(Maven)을 안 띄운다
4. db 골든 — `probe` 수가 바뀌면 PASS·ERROR 에서 `SKIP_REFUSED` 로 옮겨간 것만 허용(건수·걸린 낱말을 이력에). `RECEIVE`·`CONVERSATION` 이 표본 문장을 하나라도 거절하면 그 낱말을 빼고 이력 「정한 것(계획 밖)」(grep 0 건이었다)
5. `roFileMessages` 새 부재 단언의 방언별 결과를 compare ㉞ 에. 빨강은 6-41 사다리
- `verify.sh --db` 와 `git push` 는 따로 낸다
- 의존: R-31

### 마무리
CLAUDE.md 「마무리」 1~8 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: ① 건너뜀 조건이 설계보다 넓어 안전한 꼴까지 안 고치는 자리 ② 건너뜀이 좁아 위험 꼴이 아직 지나는 자리(키 열일곱을 하나씩) ③ 기존 사유 글자가 바뀐 자리 ④ `OPTIONAL` 이 0-98 앞 아홉과 같은가 ⑤ 절대 규칙 3 — 새 사유·예외 글에 코드 본문이 실리는 자리) → `bash scripts/gate-probe.sh` → `verify.sh`(무거운 레인은 끝 줄이 알리는 것만 다시 — 리뷰 고침이 `core/check` 면 `--corpus` 다시) → push → PR(본문에 행마다 시작 세 줄 표 + §3-0 켜기 표) → CI 폴링 → claude[bot] 처분 → **머지 위임** `gh pr merge N --merge --delete-branch` → 끝 보고.

## 5. 금지 사항

- `pure/` · `rules.yaml` · `ext-fixes.yaml` · `config/pmd/exclude-from-failure.properties` · 마이그레이션(`src/main/resources/db`) 을 고치지 않는다. 새 마이그레이션 0
- 새 의존성 0
- 판정(지적) 쪽을 고치지 않는다 — `ValueAst`·`JavaShape`·`JavaRules.orderViolations`·`BlankRelRule`·`ThScopeFix`·`FileRule`·`RegexRule` 불변. 고침만 좁힌다. 예외는 `ResourceAst.Planner.of`(`manual` 만 채운다 — 지적 수 불변)
- 기존 스킵 사유 상수의 글자를 바꾸지 않는다(corpus baseline 키). 새 사유는 새 상수
- 기존 시험 단언을 지우거나 느슨하게 바꿔 통과시키지 않는다. 지우는 것은 0-98 이 넣은 `defaultLeavesRiskyRules`·`PICKED`·스모크 `setChecked` 두 줄뿐(§3-0). 「의도된 골든 변화」 에 적힌 단언은 「남는다 + 사유」 로 바꾼다
- `ConsoleArgs.PURE` 를 늘리지 않는다 · `Zones` 에 정규식 리터럴 판정을 넣지 않는다 · JS·HTML 파서 라이브러리를 들이지 않는다
- `Cleanup.java` 에 메서드를 더하지 않는다 · 기준선(`self-check.txt`)에 있는 메서드의 값을 올리지 않는다(예외 §3-15 한 줄)
- `OPTIONAL` 에서 §3-0 표 밖 규칙을 빼거나 넣지 않는다 — `java.tryWithResources`·`java.interruptedIgnored`·`common.sysout`·pii 둘은 고쳐도 기본 끔 그대로
- `LocalFiles` 한 인자 생성자·시험 43 곳을 고치지 않는다 · `LocalFiles.encode`·`writeResult` 를 고치지 않는다
- `ReadOnlyJdbc` 판정 코드(`words`·`wordEnd`)를 고치지 않는다 · 방언 인자를 더하지 않는다
- `0-94`·`0-95`·`0-96`·`0-86`·`0-87`·`5-100` 을 같이 고치지 않는다
- 시험·사유·예외 글에 실제 사업 코드·SQL 을 쓰지 않는다 — 가짜 값만(`4111111111111111`·`ZZLIT9`·`TB_X`)
- worktree·`finishing-a-development-branch` 를 쓰지 않는다

## 6. 최종 검증

- `bash scripts/verify.sh` 초록 · `bash scripts/mvn.sh -q -DskipTests -Djacoco.skip=true verify` 초록 · `bash scripts/verify.sh --corpus` 초록(`CleanupCompileCorpusTest` 등급 A 0) · `bash scripts/verify.sh --db` 초록 · `bash scripts/gate-probe.sh` 열 전부 산다
- 강제 지점 시험이 전부 있다(§4 표) — 각 행 이력에 RED → GREEN 한 줄
- `CleanupRoutesTest.rulesListsFixable` 의 `opt` 가 0-98 앞 아홉 · 스모크 기본 체크 23 · `git diff 83183e81^ -- src/test/java/kr/ejg/toolbox/web/CleanupRoutesTest.java` 가 비었다(0-98 앞과 같다)
- 손 확인(R-31): `/api/cleanup/rules` `opt` 아홉 · compare ㉞ 표
- 회귀: `golden/` diff 는 행마다 적은 「의도된 골든 변화」 와 V-64a·V-64 의 허용 변화뿐 · 규칙 수 147 그대로 · `Cleanup.RULES` 32 그대로
- CI 8 초록 · claude[bot] 「아니오」 처분 · PR 머지

## 7. 중단 조건

다음이면 임의로 풀지 말고 멈추고 보고한다(어느 스텝 · 무엇이 예상과 달랐나 · 가능한 선택지).
- 2절 구조 설명이 실제 코드와 다르다(줄 번호 어긋남은 아님 — 메서드·흐름이 다를 때)
- 검증이 2회 연속 실패하고 원인이 이 플랜 범위 밖
- 5절을 어기지 않고는 진행할 수 없다(특히 — 판정 쪽을 고쳐야만 닫히는 키, 기준선 값을 올려야만 되는 고침)
- 스텝에 없는 파일을 3개 이상 고쳐야 한다(§3-0 켜기 셋·골든·픽스처는 스텝에 있는 것이다)
- V-64a 에서 `CleanupCompileCorpusTest` 의 `written > 500` 이 깨진다(건너뜀이 너무 넓다 — 설계를 다시 봐야 한다)
- V-64a 에서 등급 A(고친 소스 컴파일 실패)가 새로 나온다
- `--db` 앞 여유 RAM 15GB 미만(다른 세션 컨테이너 — 사람이 조율한다)
- 켜기 뒤 `CodeCliTest.cleanupPreviewAndApply` 의 출력(`Pos.java · delete,imports · 4 → 0`)이 바뀐다(기본 규칙 집합이 0-98 앞과 다르다는 뜻)

번들 모드의 「갈려도 안 멈춘다」 는 위 여덟 밖에서만 선다. 행당 계획 밖 결정 둘까지, 넘으면 `wip/<청크>`. 켜기가 있는 행이 `wip/` 로 빠지면 그 규칙은 `OPTIONAL` 에 남기고 끝 보고에 적는다(`5-108` 의 「아홉」 단언은 남은 수에 맞춘다).

## 8. 불확실 항목

사용자만 정할 수 있는 것 — 없음(다섯 다 답 받음).

실행해야만 알 수 있는 것 — 실행자가 해당 스텝에서 확인한다.
- **표본에서 건너뜀이 얼마나 느나**(표본 폴더) — `bash scripts/verify.sh --corpus`. 처분은 V-64a 허용 변화·7절 `written > 500`
- **행마다의 픽스처 골든 diff**(시험 실행) — `-Dgolden.update=true` 로 뜬 diff 를 그 행 「의도된 골든 변화」 와 댄다
- **SelfCheck 가 새 도우미를 상한 안으로 보나**(시험 실행) — `-Dtest=SelfCheckTest`. 공통 사다리
- **PMD·SpotBugs 새 짝**(빌드) — V-64 의 1. 새 정규식 상수(`XML_DECL`·`DQUOTED`·`StmtId`)와 `Zones` 의 상태 클래스가 후보다
- **실물 DB 가 오류문에 SQL 을 따옴표 없이 되비추나**(Testcontainers) — `bash scripts/verify.sh --db`. 처분은 6-41 사다리
- **ArchUnit 1.4.1 `callConstructor(Class, Class...)` 가 `this(…)` 위임을 어떻게 세나**(시험 실행) — `LocalFiles` 자신을 `that()` 에서 뺐다. RED 확인 절차가 0-97 에 있다

첫 스텝 전에 — `git status` 깨끗 · `git rev-parse --short origin/main` 이 `b66624d` · `ls C:/workspace/toolbox-corpus` · `node -v` · `docker info` 가 되는지.
