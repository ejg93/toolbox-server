# 설계 60 — 번들 76: 코드 정리 B · 코드 검사 잔여 · 개발자 도구 새 탭 둘 (Fable 설계 · Opus 실행, 2026-11-09)

## 맥락
번들 75 가 미룬 라-1·9·12(코드 정리 B)와 compare ㉕ 「후보로」(꼴 A' 변형 둘 · catchThrowable `getMessage`) · 새 행 5-66 · 대기 행 0-75 · 번들 74·75 가 두 번 뺀 개발자 도구 새 탭(바-6 해시 · 바-7 나머지 Charcode·진법)을 한 번들로 닫는다(사용자 2026-11-09 「최대한 크게」). 코드 검사·정리는 corpus 레인, 탭은 java+corpus 레인(`resources/tools`·`scripts/puppeteer`), `--db` 는 안 닿는다.

**보정(번들 75 이력 「정한 것(계획 밖)」 셋 — 이 설계가 받는 것)**: `CheckRulesTest` 가 fix 글 **60자 상한**을 건다(새 규칙 둘의 fix 글은 아래에 60자 안으로 적었다) · 꼴 A' 는 단위 시험 `closeOutsideFinallyLeaks` 꼴이라 tryWithResources 가 같이 선다 · `steps.json` 의 파일 before 는 **모든 지적**을 센다(대상 규칙만이 아니다 — 아래 기대 수는 「대상 규칙 전후」만 적고 파일 before 는 실행이 적는다).

**실측(2026-11-09, `d31cc0e` 트리 — 코드 읽기 + 표본 grep. grep 은 어림이고 정확한 수는 V-56a 가 준다)**:
- `Cleanup`(976줄) 메서드 **29**(상한 30 — `metric.classMethods` `v > 30` 이 빨강, `SelfCheckTest.mainMatchesBaseline`). 새 단계는 `clean:131-142` 안의 `editStage(st, 단계, 규칙들, 람다)` 한 줄 — 메서드를 안 늘린다. `RULES:27-29` 14 · `OPTIONAL:31` 둘 · `VALUE_RULES:75` · `TOUCHED_MAY_VANISH:78`(`MARKER→COMMENT_RULES`, `RESOURCE→{"*"}`)
- `Cleanup.worse:352-439` — 대상 규칙 건수 합이 **줄어야** 채택(`ta >= tb` 면 「효과 없음」) · 고친 줄(touched) 위 지적은 규칙 이름 다중집합, 그 밖은 (규칙, 발췌) 다중집합 — 줄이 밀려도 따라간다. 늘면 「다른 지적이 생김」, 줄면 「사라짐」(단 `TOUCHED_MAY_VANISH`). `metric.*`·`file.*`(있음·없음만)·`*.parseError` 는 따로
- `Cleanup.Edits:922-975` — `replace(줄, 글)` 은 **첫 쓰기가 이긴다** · 글에 `\n` 이 있으면 줄이 끼워진다(5-62c `Objects` import 전례, `touchedNew` 가 끼운 줄도 센다) · `delete` 는 줄 삭제
- 줄 단위 **정규식 규칙을 줄 안에서 고치는 단계는 지금 없다**(삭제뿐). 수정기는 파일 하나만 본다 — `CleanupRunner.preview:69`·`apply:123` 가 `new Cleanup(rules, wanted, lineEnding)` 을 만들고 파일마다 `clean` — **파일 사이 사전 훑기 없음**. 프로필 값은 `lineEnding` 만 수정기에 닿는다(`CleanupRoutes.lineEnding(p)`)
- `ValueFix`(220줄, 메서드 15) — `apply` 가 `ValueAst.apply(cu, targets)` 의 Hit 노드를 한 줄 안에서 바꾼다(`Change(from, to, text)` · `merge` 가 안에 든 것은 「겹친 식」). `refEquals:123-139` 가 `Objects` import 를 **그 자리에서** `e.replace` 로 끼운다 — 같은 단계에서 다른 import 를 또 끼우면 첫 쓰기에 져서 **import 가 빠진다**(→ 5-70b 가 import 를 한 번에 모아 끼우게 바꾼다)
- `RuleSet` 생성자 인자 **7**(`metric.params` 상한 7) — 마지막이 `Profile.Naming naming`(`:48-56`, `load:118`). 인자를 더 못 늘린다 → `Profile profile` 로 **바꿔** naming·encoding 둘을 뽑는다
- 메서드 수: `ValueAst` 22 · `JavaDefects` 25 · `SecurityAst` 29 · `ResourceAst` 바깥 29 + `Planner` 22 · `ResourceFix` 12 · `JavaRules` **30**(더 못 늘린다 — 새 AST 클래스는 `taint:230-251` 안에 for 한 줄) · `DeclaredTypes` 8
- `JavaDefects.rethrows:249-252` — throw 식 어디든 catch 매개변수 `NameExpr` 이 있으면 다시 던지기 → `throw new X(t.getMessage())` 도 다시 던지기로 친다(jeecg `DataBaseServiceImpl:333`). `lostCause:189-203` 는 매개변수 이름이 몸에 **한 번도** 안 나올 때만이라 그 꼴과 **안 겹친다**
- `ResourceAst.Planner` — `bareClose:418`(`x.close()`·null 지킴만) · `closeRun:428` · `tailClean:450`(묶음 이름 재대입·close 뒤 묶음 이름·**몸 안 선언 이름**을 쓰면 거절) · `target:514` 는 `closeQuietly(n)` 의 n 도 준다. `Twr(Node anchor, List<Res> resources, List<Statement> closes):103`. `ResourceFix.SHAPE:30` 「줄 꼴이 달라 못 고침(한 줄에 다른 코드·여러 줄 식)」 — 글이 `CleanupTest`·`golden/cleanup/steps.json`·`golden/corpus/cleanup-egov.json` 에 있다
- `SecurityAst.xmlFactories:109-123` — Cyberneko 제외가 `html && SAXBuilder && 인자 있음` 전부(5-66 ①)
- `common.sysout`(`rules.yaml:33`, 정규식 `\bSystem\s*\.\s*(?:out|err)\s*\.\s*print`, globs java·jsp·jspf) 표본 줄(시험 폴더 뺌): jeesite 69 · roller 45 · jeecg 35 · zheng 30 · jspwiki 27 · shopizer 14 · egov 10 · ruoyi 둘 8·7 · struts 3 · commons-io 1. `printf`·`format` 은 shopizer 2 · commons-io 2 뿐
- 로거 선언 꼴(표본, `static` + `<클래스>.class` 인자만): jeecg — log4j1 `private static final Logger logger = Logger.getLogger(` 54 · slf4j 35 / egov·zheng·shopizer·ruoyi·egov-msa — slf4j `private static final Logger … = LoggerFactory.getLogger(` / roller — commons-logging `private static final Log log = LogFactory.getLog(` 106 / jspwiki·struts — log4j2 `LogManager.getLogger(` / commons 셋 — 0. **라이브러리가 프로젝트마다·모듈마다 다르다** → 베끼는 범위는 「같은 소스 루트」
- `getBytes()` 무인자(표본 줄): jeecg 42 · egov 21 · shopizer 11 · jeesite 9 · roller 6 · zheng 5. 수신자 상위 — `mf.getBytes()` 20 · `file.getBytes()` 6(**`MultipartFile`** — String 이 아니다) → 수신자가 String 으로 풀릴 때만 판정해야 한다. `new String(x)` 한 인자 58줄(대부분 `char[]`·`String`)
- 우리 코드 `src/main/java`: `System.out/err` 6 자리 전부 `// tb-ignore common.sysout`(지적 없음) · `.getBytes()` 0 · `new String(pw)`·`new String(a)` 넷(`char[]` 로 보임 — 5-70a 가 확인) · 안 쓰는 지역 변수 **0**(JavaParser 3.26.3 로 아래 `LocalsAst` 정의를 돌린 실측 — 지역 선언자 4,148)
- 기존 픽스처에 `java.unusedLocal` 이 걸리는 자리(같은 실측): 시험이 java 묶음을 켜고 보는 곳은 **양성 셋**(`java/PosOrder.java:15` · `PosOrderEdge.java:27` · `PosServiceImpl.java:14` — `java.json` 에 세 줄) · `java/Neg*` **0** · cleanup 픽스처 **0**(기존 cleanup 골든 불변). `common/neg.java`·`file/neg.java`·`security/neg.java` 의 14 자리는 `CheckRulesTest.fixturesGolden` 이 java 묶음을 끄고 돌아 안 보인다(`:78`) → `check/fixtures.json` 은 **안 바뀐다**
- 픽스처에 `// tb-ignore` 를 달면 안 된다 — `CheckStoreTest:80` 이 `suppressed` 6 을 건다
- 정규식 규칙의 **순수본 짝** — `PureRulesJsonTest` 가 `golden/check/pure-rules.json`(정규식 63 — `id·group·severity·regex·flags·langs·message·skipComments·max`)과 `pure/tools/code_check.html` 의 RULES 가 같은지 건다 → **정규식 규칙을 더하거나 `regex`·`globs`·`message` 를 바꾸면 portfolio 재주입이 든다**(5-31 이 기다리는 까닭). `fix`·`params`(max 밖)·`kind: java` 규칙은 안 걸린다
- 묶음 C 표본: `a11y.htmlLang` **1,206줄**(egov 262 · jeecg 214 · egov-portal 122 · jeesite 102 · egov-enterprise 86 · struts 78 · zheng 58 …, 거의 맨 `<html>`. 함정 — ruoyi-classic `templates/demo/table/print.html:89` 는 JS 템플릿 리터럴 안 `<html>`) · `tsx.blankNoopener` 표본 지적 **0**(jsx·tsx 만 본다. JSP·HTML 104줄은 규칙이 없다) · `security.jspElParam` 지적 9 가운데 본문 단독 출력 **0**(전부 JS 문자열·속성·EL 조건식)
- 개발자 도구: `tools/dev_tools.html`(3,304줄) 탭 16(`#main-tabs:125-141`, 끝이 `fdiff`·`logsql`) · 「인코딩 판별」 탭(번들 67)이 **html 안 인라인**(순수 함수 `hx2`·`hexOf`·`hexDump`·`hexToBytes:1891-1928`) · 저장된 탭 순서에 없는 탭은 뒤에 붙는다(`:2062-2072`) · Ctrl+Enter 분기 `:2103-2115` · `copyEl`·`showToast`·`esc:1737-1745`
- **HtmlUnit 은 dev_tools JS 를 안 돌린다**(`SmokeHtmlUnitTest.JS_OFF = {"dev_tools"}`) · HtmlUnit 4.11.1 `SubtleCrypto` 는 열두 메서드 전부 `notImplemented()`. 탭 JS 는 **node + Puppeteer(크로미움)** 로 잰다 — `scripts/puppeteer/corpus-js.js` 가 `dev.evaluate(…)` 로 페이지 전역 함수를 부르고 `summary.json` 을 쓰면 `JsCorpusTest`(`@Tag("corpus")`)가 JDK 와 맞댄다(`encUnitVectorsMatchWhatwg:231`). `summaryGolden:316` 은 고른 키만 골든에 넣는다(새 키는 골든 불변). `http://127.0.0.1` 은 보안 문맥이라 크로미움에서 `crypto.subtle` 이 된다
- 탭을 거는 빠른 레인 시험: `ToolsFolderTest`(CDN 금지 · `TB.copy` · 「복사했다」 금지) · `ToolsA11yTest`(a11y 묶음 전부 — 새 `input`·`select`·`textarea` 는 `aria-label` 또는 `id`+`label for`) · `SelfCheckTest.mainMatchesBaseline`(tools html 도 검사 — `innerHTML =` 는 `security.domXss`)
- 0-75: `bundle/SOURCES.md:15` 「**Maven Central 에 없다** — …」. `bundle/` 은 verify 레인 밖(release 지문)
- 레인(`scripts/verify-fingerprint.sh`): `core/check`·`resources/check`·`resources/tools`·`scripts/puppeteer`·`golden/corpus` = java + corpus · `fixtures/**` = java · `README.md`·`PLAN.md` = docs · `design/**`·`bundle/**` = 없음

**범위 밖(사용자 2026-11-09 · 실측)**: `security.jspElParam` 고침(고칠 자리 0 — c 태그 고침은 찍히는 자리마다 맞는 이스케이프가 달라 기계 고침이 안 맞다) · JSP·HTML `_blank` 규칙(새 정규식 규칙 = 순수본 재주입 — 5-31 과 같이 다음) · 라-9 의 private 필드·메서드 · `common.printStackTrace` 고침 · `lostCause` 를 `getMessage` 꼴로 넓히기 · java.time 날짜 표시 · `--db` CI 이전 · 현장 MyBatis 판 · 5-31·2-17·보류 행

## 사용자 결정(2026-11-09)
1. 묶음 — A(라-1·9·12) + B(5-66 · 꼴 A' 변형 둘 · catchThrowable) + C + D(해시 · Charcode·진법) + E(0-75) **전부**
2. 라-1 — 클래스에 로거가 있으면 그것으로 바꾼다(`out`→`debug` · `err`→`error`). 없으면 **같은 프로젝트에서 이미 쓰는 로거 선언 꼴을 베껴** 필드·import 를 넣는다. 베낄 꼴이 없으면 건너뜀
3. 라-1 — **`public static void main` 안은 건너뜀**(콘솔 출력이 기능) · 고침은 **기본 끔**
4. 라-12 — 프로필 `project.encoding` 으로 채운다(없으면 UTF-8) · 고침은 **기본 끔**
5. 라-9 — 메서드 안 지역 변수만 · 지워도 동작이 안 바뀌는 것만 지운다
6. catchThrowable — `throw new X(e.getMessage())` 처럼 원인을 잃는 꼴은 다시 던지기로 안 친다
7. 꼴 A' 변형 둘(`closeQuietly` 로 닫음 · close 뒤 몸 안 변수를 씀) — **지적만**, 고침은 건너뜀
8. 해시 — 브라우저에서 계산(SHA·HMAC 은 `crypto.subtle`, MD5 는 JS 직접 구현, 외부 라이브러리 없음). 서버 라우트 없음
9. 묶음 C — `html lang` 넣기 + tsx `rel` 넣기 **둘만**. `c:out` 감싸기는 뺀다
10. 설계 Fable · 실행 Opus · **머지 위임 있음**

설계가 정한 것(전부 [고정]):
- 「같은 프로젝트」 = **같은 소스 루트**(`…/src/main/java/`·`…/src/test/java/` 까지, 없으면 첫 `…/src/` 까지, 그것도 없으면 폴더 전체) — 한 모듈은 한 클래스패스라 베낀 로거 import 가 반드시 풀린다. 베끼는 꼴은 `static` 이고 인자가 `<클래스>.class` 인 선언만(끼운 필드가 static 메서드에서도 돌아야 한다)
- 출력 인자는 String 으로 보이면 그대로, 아니면 `String.valueOf(…)` 로 감싼다 — `println` 의 오버로드와 뜻이 같고 로거 API(slf4j 는 String 만)를 가리지 않는다
- 새 지적 규칙 둘 — `java.unusedLocal`(info) · `java.defaultCharset`(warn). 둘 다 `kind: java` 라 순수본 짝에 안 걸린다. 규칙 136 → **138**
- `java.unusedLocal` 은 **이름이 한 번도 다시 안 나오는** 지역 변수만(대입만 하고 안 읽는 변수는 지적 안 함 — 이름이 나온다) · 고침은 초기화식이 없거나 「리터럴·null·`this.x` 와 그것들의 `+ - *`·괄호·삼항·단항」 일 때만 줄째 삭제. **다른 이름을 읽는 초기화식(`int b = a;`)은 안 지운다** — 지우면 `a` 가 새로 안 쓰는 변수가 되어 단계가 「효과 없음」 으로 되돌려진다
- 지역 변수를 지워 import 가 안 쓰이게 되면 그 `java.unusedImport` 는 **생겨도 된다**(다음 정리 실행의 delete 단계가 치운다 — delete 단계가 `common.unusedIgnore` 를 남기는 것과 같은 꼴)
- 로거로 바꾸는 고침은 **catch 몸 안의 출력을 건너뛴다** — 로거 호출이 생기면 `java.swallow`·`java.logAndThrow` 판정이 바뀌어 그 파일의 단계가 통째 되돌려진다(표본 catch 안 출력은 jeecg 11/82 · jeesite 9/110 — 소수)
- `java.defaultCharset` 은 수신자가 String 으로 풀리는 무인자 `getBytes()` 와 인자가 `byte[]` 로 풀리는 한 인자 `new String(b)` 만 — 못 풀면 지적 안 함(오탐보다 놓침)
- 코드 정리 단계 차례 `delete → marker → markup → value → local → log → resource → order → eol` · `RULES` 19(끝에 `java.unusedLocal`·`java.defaultCharset`·`common.sysout`·`a11y.htmlLang`·`tsx.blankNoopener`) · `OPTIONAL` 넷(`java.bigDecimalDouble`·`java.tryWithResources`·`java.defaultCharset`·`common.sysout`) → 기본 켬 15
- `lang` 값은 `ko` 고정(규칙 fix 글과 같다) · HMAC-MD5 도 넣는다(JS MD5 로 10줄) · 탭 둘은 탭 줄 **끝**에 붙인다(기존 16 순서·id 불변)
- 기존 픽스처에 새 규칙이 걸리면 — **양성**은 `check/java.json` 이 새 줄을 받는다(unusedLocal 은 실측 셋) · **음성**은 실측 0 이다. 그래도 걸리면 그 줄을 **변수를 쓰게** 고친다(줄 수 불변) — `// tb-ignore` 는 달지 않는다

실행자는 Opus. 차례 `S0` → `5-66` → `5-67` → `5-68` → `5-69a` → `5-69b` → `5-70a` → `5-70b` → `5-71a` → `5-71b` → `5-72` → `4-31` → `4-32` → `0-75` → `V-56a` → `R-23` → `V-56` → 마무리. 가지 `work/2026-11-09`(지금 가지 — 번들 75 닫기 `d31cc0e` 가 로컬에만 있다, 그 위에 친다).

---

## 1. 목표와 범위
- 한 문장: 코드 정리가 `System.out` 을 로거로·안 쓰는 지역 변수 삭제·`getBytes()` 문자셋 명시·`<html lang>`·`rel` 다섯을 더 고치고, 코드 검사 잔여 넷을 닫고, 개발자 도구에 해시·문자 코드 탭을 더한다.
- 완료 시 체감: 코드 정리 화면 체크박스가 14 → 19(기본 켬 15). 코드 검사에 `java.unusedLocal`·`java.defaultCharset` 이 뜨고, `catch (Throwable t) { throw new X(t.getMessage()); }` 가 다시 `catchThrowable` 로 뜬다. `closeQuietly` 로 닫는 자원에도 `tryWithResources` 가 선다(고침은 사유와 함께 건너뜀). 개발자 도구 탭 줄 끝에 「해시」·「문자 코드·진법」 이 생긴다.
- In scope: `core/check` 새 파일 `LocalsAst`·`LocalFix`·`Imports`·`LoggerShape`·`LogFix`·`MarkupFix` · 수정 `Cleanup`(상수·생성자·`clean` 줄)·`CleanupRunner`(생성 두 줄)·`ValueAst`·`ValueFix`·`DeclaredTypes`·`JavaDefects`·`SecurityAst`·`ResourceAst`·`ResourceFix`·`JavaRules.taint`(for 한 줄)·`RuleSet`(생성자 끝 인자·접근자) · `resources/check/rules.yaml`(새 규칙 둘) · `tools/code_cleanup_ext.js`(단계 라벨) · `tools/dev_tools.html`·`tools/index.html` · `scripts/puppeteer/corpus-js.js` · `cli/CodeCommands`(글)·`web/CleanupRoutes`(javadoc)·`README.md`(한 줄) · `bundle/SOURCES.md`(한 줄) · 픽스처·시험·골든(행마다) · `design/compare-2026-10-28.md` ㉖
- Out of scope: 「범위 밖」 전부 · `pure/` · `core/gen`·`core/meta`·`core/analyze` · 정규식 규칙의 `regex`·`globs`·`message` · `Cleanup` 의 `worse`·`commit`·`deleteStage`·`orderStage`·`eolStage` 논리 · `OrderFix` · 화면 HTML(`code_cleanup.html`) · 새 의존성

## 2. 현재 구조 요약
「맥락」 의 실측이 요약이다. 덧붙이는 함정:
- 새 클래스는 PMD 기준선 밖이다 — 노드 `==` 금지(`Range` 비교·기존 `same`) · `AvoidLiteralsInIfCondition`(수는 상수로) · `LiteralsFirstInComparisons` · `LooseCoupling`(`NodeList` 매개변수는 `List`) · `UseLocaleWithCaseConversions` · `UseUtilityClass`(private 생성자) · 시험 `AvoidDuplicateLiterals`(같은 글 넷 이상은 상수). **행마다 `bash scripts/mvn.sh -q -DskipTests verify` 를 먼저** 돌린다(빠른 verify 는 PMD·SpotBugs 를 안 본다)
- `SelfCheckTest.cleanupChangesNothing` 은 `src/main` 에 `Cleanup.RULES` **전부**(기본 끔 포함)를 돌려 `changed == 0` 을 건다 → 새 규칙이 우리 코드에 걸리면 그 행에서 **코드를 고친다**(기준선에 안 넣는다 — 번들 66 전례)
- `CleanupTest.cleanup():34` 는 `Cleanup.RULES` 전부를 고른다 → 새 고침이 **기존 cleanup 픽스처**에도 돈다. 실측으로는 기존 cleanup 픽스처에 unusedLocal 0 이라 기존 골든은 안 바뀐다(바뀌면 §7). `CleanupOrderTest:36` 은 `ORDER` 만 골라 영향 없다
- `JavaRulesTest` 는 `Neg*.java` 에 **모든** java 규칙 0 → 새 음성 픽스처는 다른 java 규칙도 안 걸려야 한다. `CheckRulesTest.fixturesGolden` 은 java 묶음을 끄고 `java/` 를 건너뛴다(`:78·:83`)
- `CleanupTest.compile():227-239` 는 글을 **`Pos.java` 라는 이름으로** 쓰고 JDK 만으로 javac 를 돌린다 → 그 길로 컴파일하는 픽스처는 `public` 최상위 클래스·외부 라이브러리(`IOUtils` 등)를 못 쓴다. slf4j 는 시험 클래스패스에 있다(`DdlCorpusTest:198` 이 `java.class.path` 로 javac 를 돌린 전례)
- `Cleanup:68` 에 이미 `private static final Set<String> MARKUP`(마크업 언어 집합)이 있다 → 새 단계 상수 이름은 `MARKUP_STAGE`
- `self-check.txt:59` `RuleSet.java|metric.complexity|복잡도 29 > 15 · load(2)` — `load` 의 삼항 하나가 빠지면 28 이 되어 키 글이 바뀐다(5-70b 가 그 한 줄만 갱신). `worse` 는 `복잡도 35` 기준선이라 **삼항 수를 그대로** 두고 고친다
- `README.md:43` 은 `ReadmeCommandsTest` 가 건다 — `cleanup <폴더>` 와 옵션 이름은 그대로 둔다
- `Edits.replace` 첫 쓰기 우선 — 한 줄에 쓸 것이 둘이면 **수정기 안에서 합쳐 한 번** 쓴다(`LogFix` 의 필드 끼움 줄 + 그 줄의 호출 바꿈, `ValueFix` 의 import 줄)
- `CleanupRunner.apply` 는 다시 계산한 `outHash` 가 미리보기와 같아야 쓴다 → 로거 꼴 훑기는 **원본 폴더만** 읽으므로(결과는 다른 폴더) 두 번 다 같다
- `CleanupCorpusTest:44` 는 `new Cleanup(…)` 을 직접 만든다(rel 은 표본 루트 기준) → 5-71a 에서 4-인자로 바꿔야 표본이 로거 끼움을 잰다
- `JsCorpusTest`·`corpus-js.js` 는 표본 폴더·node·`C:/workspace/node_modules/puppeteer` 가 있어야 돈다(빠른 verify 밖). 4-31·4-32 의 RED→GREEN 자리는 `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest=JsCorpusTest -Djunit.timeout=10m`
- 레인 — 4-31·4-32·5-xx 전부 corpus 가 바뀐다. `--db` 는 `verify.sh` 끝 줄이 부를 때만(기대: 안 부른다)

## 3. 설계 결정 — 전부 [고정]

### 5-66 — xxe Cyberneko 인자 조건 · SHAPE 글(§3-1)
- `SecurityAst` 상수 `private static final List<String> HTML_ARGS = List.of("saxparser", "neko");` — `xmlFactories:120` 의 `o.getArguments().isNonEmpty()` 를
  `o.getArguments().stream().anyMatch(a -> HTML_ARGS.stream().anyMatch(a.toString().toLowerCase(Locale.ROOT)::contains))` 로. 새 메서드 없음(29 유지)
- 시험 `SecurityAstTest` 새 `nekoImportPlainArgsStillFlagged` — `hits("import org.codelibs.nekohtml.parsers.SAXParser; class A { void f() { SAXBuilder a = new SAXBuilder(true); } }")` → `List.of("1" + X)`(RED: 지금 0). `nekoBuilderWithArgsIsSilent`(인자 글에 `SAXParser`) 그대로 초록
- `ResourceFix.SHAPE` → `"줄 꼴이 달라 못 고침(한 줄에 다른 코드·여러 줄 식·여러 줄 close)"`. `CleanupTest` 의 그 글 리터럴 · `golden/cleanup/steps.json` 의 그 글(갱신) — `golden/corpus/cleanup-egov.json` 은 V-56a 가 바꾼다
- 표본 기대: xxe 불변(jspwiki 3)

### 5-67 — catchThrowable: 수신자로만 쓴 매개변수는 다시 던지기가 아니다(§3-2)
```java
/** 5-64 ① · 5-67 — 다시 던진다 = catch 몸(안쪽 catch 밖)의 throw 식에 catch 매개변수가 값으로 나온다 — throw t · throw new X(t) · throw wrap(t) · throw (Error) t. t.getMessage() 처럼 호출의 수신자로만 나오면 원 예외를 안 싣는다 */
private static boolean rethrows(ThrowStmt s, String name, BlockStmt body) {
    return !catchBarrier(path(s, body)) && s.getExpression().findAll(NameExpr.class).stream()
            .anyMatch(n -> n.getNameAsString().equals(name) && !receiver(n));
}

/** 5-67 — n 이 메서드 호출의 수신자(scope)다 */
private static boolean receiver(NameExpr n) {
    return n.getParentNode().orElse(null) instanceof MethodCallExpr m && m.getScope().filter(sc -> same(sc, n)).isPresent();
}
```
- `JavaDefects` 25 → 26. `rules.yaml` 불변
- 픽스처 `PosDefects.java` **끝**에 `class PosDefects76 { void lost() { try { run(); } catch (Throwable t) { throw new IllegalStateException(t.getMessage()); } } void run() { } }` 를 여러 줄로(catch 줄이 양성) · `NegDefects.java` 끝에 `NegDefects76` — `catch (Throwable t) { throw new IllegalStateException(t.getMessage(), t); }`
- 시험 `JavaDefectsTest` 새 `receiverOnlyIsNotRethrow` — 인라인 `catch (Throwable t) { throw new RuntimeException(t.getMessage()); }` → catchThrowable 1 · `throw new RuntimeException(t.getMessage(), t)` → 0 · `throw new RuntimeException("m: " + t)` → 0. `positiveFixture` 줄 목록 + 새 줄
- 골든 `check/java.json` 새 줄 하나. 표본 기대: catchThrowable jeecg 0 → 1 안팎(V-56a)

### 5-68 — 꼴 A' 변형 둘: 지적만(§3-3)
- `Twr` 에 넷째 칸: `record Twr(Node anchor, List<Res> resources, List<Statement> closes, String manual)` — `manual` 이 빈 글이면 고칠 수 있고, 아니면 **손으로 고칠 사유**. `Planner.of`(꼴 A·B·C)는 `""`
- `Planner` 상수 `QUIET = "closeQuietly 로 닫는다 — close 예외 경로가 바뀌어 손으로"` · `HOIST = "close 뒤에서 몸 안 이름을 다시 쓴다 — 줄을 옮겨야 해 손으로"`(`tail` 은 몸 안 **중첩** 선언 이름까지 모으므로 close 뒤의 다른 `for (int i …)` 도 이 사유가 된다 — 보수적이면 된다)
- 새 메서드 `plainClose`(22 → 23):
```java
/** 5-68 — bareClose 또는 {@code X.closeQuietly(n);}(null 지킴 포함) — 꼴 A' 판정용. closeQuietly 가 끼면 고침은 손으로 */
private static Optional<String> plainClose(Statement s) {
    Optional<String> bare = bareClose(s);
    if (bare.isPresent()) {
        return bare;
    }
    Optional<String> guard = s instanceof IfStmt i && i.getElseStmt().isEmpty() ? nullGuard(i.getCondition()) : Optional.empty();
    Statement inner = guard.isPresent() ? single(((IfStmt) s).getThenStmt()) : s;
    Optional<String> quiet = closeCall(inner).filter(c -> CLOSE_QUIETLY.equals(c.getNameAsString())).flatMap(Planner::target);
    return guard.isPresent() ? quiet.filter(guard.get()::equals) : quiet;
}
```
  (노드 `==` 를 안 쓴다 — PMD `CompareObjectsWithEquals`)
- `closeRun:436` 의 `bareClose(ss.get(j))` → `plainClose(ss.get(j))`(뒤의 `allMatch(closeName…)` 는 그대로 — `plainClose` 가 받는 꼴은 `closeName` 의 부분집합이다)
- `tailClean` 을 `Optional<String> tail(…)` 로(이름·반환 바꿈, 메서드 수 불변): 묶음 이름 재대입 또는 close 뒤 **묶음 이름** 사용이면 `Optional.empty()`(거절) · close 뒤 **몸 안 선언 이름**만 쓰면 `Optional.of(HOIST)` · 아니면 `Optional.of("")`
- `wrap` 의 `for (int k …)` 몸:
```java
List<Res> group = res.subList(0, k);
int from = at + k; // 람다가 잡으려면 사실상 final 이어야 한다
Optional<List<Statement>> closes = closeRun(blk, from, group);
Optional<String> manual = closes.flatMap(cs -> tail(blk, from, cs, group));
if (manual.isPresent()) {
    boolean quiet = closes.get().stream().anyMatch(c -> bareClose(c).isEmpty());
    return Optional.of(new Twr(first, List.copyOf(group), closes.get(), quiet ? QUIET : manual.get()));
}
```
- `ResourceFix.apply` — `plan` 을 얻은 바로 뒤: `if (plan.isPresent() && !plan.get().manual().isEmpty()) { skip(skips, no, plan.get().manual()); continue; }`. `wrap`·`edit` 불변(12 유지)
- 픽스처 `PosResource.java` 끝 `PosResource76` — `quiet(String p)`: `InputStream is = new FileInputStream(p); is.read(); org.apache.commons.io.IOUtils.closeQuietly(is);`(import 를 안 더하려고 FQN. 선언 줄 TWR 만 — closeQuietly 는 finally 밖이어도 해제로 친다 `ResourceAst:299`) · `hoist(String p)`: `InputStream is = new FileInputStream(p); int n = is.read(); is.close(); return n;`(선언 줄 LEAK+TWR). **`NegResource.java:102` `org.apache.commons.io.IOUtils.closeQuietly(is);` 한 줄을 `if (p.isEmpty()) { org.apache.commons.io.IOUtils.closeQuietly(is); }` 로 그 자리에서 바꾼다**(다른 블록의 closeQuietly — 해제는 됐고 꼴 A' 는 아니다. 줄 수 불변. `:97` javadoc 글도 그 뜻으로 고친다)
- 시험 `ResourceAstTest` 새 `quietCloseIsTwrButManual`·`hoistIsTwrButManual`(인라인, `Set.of(TRY_WITH_RESOURCES)` → 선언 줄 1) · `wrapNotTwr:85-91` 의 일곱 꼴 a~g 가운데 **c(closeQuietly)·f(몸 안 변수를 close 뒤 씀)** 둘을 빼서 위 두 시험으로 옮긴다(다섯 남음, `:81` javadoc 도) · `tryWithResourcesFixture` 줄 목록 + 둘
- cleanup 픽스처 `fixtures/cleanup/resource/Pos.java` 끝에 `formHoist` **하나만**(javac 가 JDK 만으로 컴파일한다 — `IOUtils` 를 못 쓴다) → `resource-Pos.java.txt` 는 그 메서드가 **원문 그대로** · `steps.json` skips 에 「줄 n: close 뒤에서 몸 안 이름을 다시 쓴다 — …」 · `CleanupTest.resourceStage:146-148` 의 건너뜀 목록(지금 셋)에 그 한 줄
- `CleanupTest` 새 `quietCloseIsSkipped` — 인라인 글(`InputStream is = new FileInputStream(p); is.read(); IOUtils.closeQuietly(is);` 를 든 클래스, 컴파일 안 함)을 `clean` → 글 불변 · skips 에 「줄 n: closeQuietly 로 닫는다 — …」
- 골든 `check/java.json` 새 줄(PosResource76). 표본 기대: tryWithResources +2~10(ruoyi-classic `GenTableServiceImpl:244·357` · roller `MediaCollection:141` · ruoyi-classic `OnlineSessionFactory:37` + 몸 안 이름 재사용 꼴), resourceLeak 불변

### 5-69a — `java.unusedLocal` 판정 — 새 `LocalsAst`(§3-4)
- `rules.yaml` java 묶음 끝(`java.floatEquals` 뒤):
```yaml
  - id: java.unusedLocal
    group: java
    severity: info
    kind: java
    globs: ['*.java']
    message: 선언만 하고 쓰지 않는 지역 변수
    fix: '선언 줄을 지운다 — 초기화식에 호출이 있으면 호출만 남긴다'
    ref: 'Sonar S1481 · OpenRewrite RemoveUnusedLocalVariables'
```
- 새 파일 `core/check/LocalsAst.java`:
```java
/** 설계 60 — 지역 변수: 선언만 하고 이름이 다시 안 나오는 것. JavaRules.taint 가 부른다 */
final class LocalsAst {
    static final String UNUSED_LOCAL = "java.unusedLocal";
    /** 일부러 안 쓰는 이름 */
    private static final Pattern IGNORED = Pattern.compile("(?i)^(_+|ignored?|unused|dummy)\\d*$");
    private static final Set<String> SUPPRESS = Set.of("\"unused\"", "\"all\"");

    private LocalsAst() {
    }

    static List<Taint.Hit> apply(CompilationUnit cu, Set<String> on) {
        List<Taint.Hit> out = new ArrayList<>();
        if (!on.contains(UNUSED_LOCAL)) {
            return out;
        }
        for (ExpressionStmt es : cu.findAll(ExpressionStmt.class)) {
            Node parent = es.getParentNode().orElse(null);
            if (!(es.getExpression() instanceof VariableDeclarationExpr d) || !(parent instanceof BlockStmt || parent instanceof SwitchEntry)) {
                continue;
            }
            // 옛 꼴 case 묶음에서 선언은 뒤 case 에도 보인다 — 범위는 switch 전체
            Node scope = parent instanceof SwitchEntry ? parent.getParentNode().orElse(parent) : parent;
            for (VariableDeclarator v : d.getVariables()) {
                if (!IGNORED.matcher(v.getNameAsString()).matches() && !suppressed(v) && unused(v, scope)) {
                    out.add(new Taint.Hit(UNUSED_LOCAL, v));
                }
            }
        }
        out.sort(Comparator.comparingInt((Taint.Hit h) -> h.node().getBegin().orElseThrow().line));
        return out;
    }

    /** 선언을 가진 블록(중첩 블록·람다·익명 클래스 포함) 안에서 그 이름의 SimpleName 이 선언자 자신뿐이다 — 대입·증감·메서드 참조도 「나옴」 */
    private static boolean unused(VariableDeclarator v, Node scope) {
        Range own = v.getName().getRange().orElseThrow();
        return scope.findAll(SimpleName.class).stream()
                .noneMatch(n -> n.getIdentifier().equals(v.getNameAsString()) && !n.getRange().orElseThrow().equals(own));
    }

    /** 선언·둘러싼 메서드·클래스의 @SuppressWarnings("unused"|"all") */
    private static boolean suppressed(Node n) { … 조상을 올라가며 NodeWithAnnotations 의 SuppressWarnings 글에 SUPPRESS 가 있나 … }

    /** 지워도 동작도 다른 지적도 안 바뀌는 초기화식 — 리터럴·null·this.x 와 그것들의 + - * · 괄호 · 삼항 · 단항(+ - ! ~). 단순 이름은 아니다(지우면 그 이름이 새로 안 쓰는 변수가 된다) */
    static boolean pure(Expression e) { … 재귀. BinaryExpr 는 PLUS·MINUS·MULTIPLY 만(DIVIDE·REMAINDER 는 0 나눗셈) · FieldAccessExpr 는 scope 가 ThisExpr 일 때만 · 그 밖(NameExpr·MethodCallExpr·ObjectCreationExpr·ArrayAccessExpr·CastExpr·AssignExpr·LambdaExpr …)은 false … }
}
```
  메서드 넷(`apply`·`unused`·`suppressed`·`pure`) — 복잡도 ≤ 15(넘으면 `pure` 의 형식 판정을 `Set<Class<?>>` 상수로)
- 실측한 「쓰임」(전부 `SimpleName` 으로 잡힌다 — JavaParser 3.26.3): 메서드 참조 `x::f` · `x.new B()` · 배열 크기 · case 라벨 · 애너테이션 값 · 람다 · 익명·지역 클래스 · `try (in)` · `yield` · `c++` · `c += 1`
- `JavaRules.taint` 의 `ResourceAst` for 뒤에 한 줄 묶음: `for (Taint.Hit h : LocalsAst.apply(cu, on.keySet())) { add(c, h.rule(), h.node()); }`(메서드 수 30 그대로)
- 픽스처(머리 꼴 — package·import 유무 — 은 `PosValue.java`·`NegValue.java` 와 같게):
```java
/** 설계 60 — java.unusedLocal 양성 */
class PosLocals {
    int plain() {
        int count = 0;
        String label = "x";
        return 1;
    }

    void call(java.util.List<String> xs) {
        int n = xs.size();
    }

    void two() {
        int a = 1, b = 2;
        System.identityHashCode(a);
    }

    void inSwitch(int k) {
        switch (k) {
            case 1:
                int z = 3;
                break;
            default:
                break;
        }
    }
}
```
  `NegLocals.java` — 쓰는 것(`int a = 1; return a;`) · 대입만(`int b; b = 2;`) · 람다가 잡는 것 · 메서드 참조(`t::length`) · 이름 `ignored` · `@SuppressWarnings("unused") int k = 0;` · for 초기화·for-each·try 자원 변수 · **앞 case 에서 선언하고 뒤 case 에서 쓰는 것**(`case 1: int z = 3; break; case 2: z = 5; k = z; break;`). 다른 java 규칙이 걸리는 메서드는 그 메서드만 빼고 같은 꼴을 인라인 시험으로 옮긴다
- 새 시험 `LocalsAstTest` — `positiveFixture`: 지적 노드 이름이 `List.of("count", "label", "n", "b", "z")`(줄 번호가 아니라 **이름**으로 단언) · `negativeFixture` 0 · `switchScopeIsWholeSwitch`(위 case 꼴 인라인 0) · `pure` 표(참: `0`·`"x"`·`null`·`this.f`·`-1`·`1 + 2`·`(1)`·`true ? 1 : 2` / 거짓: `a`·`a + 1`·`f()`·`new A()`·`xs[0]`·`1 / 2`·`(T) o`·`o.f`)
- **기존 픽스처·우리 코드**(실측 — 「맥락」): `-Dgolden.update=true -Dtest=JavaRulesTest` 의 `java.json` diff 가 **새 픽스처 줄 + `PosOrder.java:15`·`PosOrderEdge.java:27`·`PosServiceImpl.java:14` 세 줄**(전부 `java.unusedLocal`)뿐인지 본다. `fixtures.json`·`self-check.txt` 는 안 바뀐다. 다르면 §7
- `CheckRulesTest:167` 136 → 137
- 표본 기대(V-56a): 표본마다 새 키 `java.unusedLocal`(건수는 실행이 적는다)

### 5-69b — 안 쓰는 지역 변수 고침 — 새 `LocalFix` + 단계 `local`(§3-5)
- 새 파일 `core/check/LocalFix.java`(메서드 둘 안팎):
```java
/** 코드 정리 지역 변수(설계 60) — LocalsAst 의 지적 가운데 지워도 동작이 안 바뀌는 선언 줄만 지운다(지적 = 고침 대상, 판정을 다시 안 한다) */
final class LocalFix {
    static void apply(String text, List<Finding> cur, JavaSource parser, Cleanup.Edits e, List<Cleanup.Skip> skips) {
        // ① Cleanup.javaUnsafe · 파싱 실패 → 사유 하나로 끝(ValueFix.apply:44-53 과 같은 꼴, 단계 이름만 Cleanup.LOCAL)
        // ② LocalsAst.apply(cu, Set.of(LocalsAst.UNUSED_LOCAL)) 의 Hit(VariableDeclarator v) 가운데
        //    **줄이 cur 의 java.unusedLocal 지적 줄인 것만**(tb-ignore 로 눌린 선언은 cur 에 없다 — 안 지운다). 줄 no = 선언 문장의 시작 줄:
        //    선언자 둘 이상        → skip "한 문장에 선언 둘"
        //    문장이 여러 줄         → skip "여러 줄 선언"
        //    문장 앞 글이 공백이 아니거나, 뒤 글이 공백도 "//" 로 시작하는 꼬리 주석도 아님 → skip "한 줄에 다른 코드"
        //    초기화식이 있고 !LocalsAst.pure(init) → skip "초기화식이 리터럴이 아니다 — 지우면 동작이 바뀔 수 있다"
        //    선언을 가진 블록(BlockStmt)의 문장이 이것 하나뿐 → skip "블록에 이 선언뿐 — 지우면 빈 블록"
        //    아니면 e.delete(no)
    }
}
```
  건너뜀 글은 이 다섯 + ① 의 둘뿐. 「홀로」 판정은 `LocalFix` 안에 둔다(`ResourceFix.alone:170-177` 은 꼬리 주석을 안 받는다 — 안 고친다)
- `Cleanup`: `public static final String LOCAL = "local";` · `RULES` 끝에 `"java.unusedLocal"`(15) · `TOUCHED_MAY_VANISH` 에 `LOCAL, Set.of("*")`(지운 줄 위 지적은 사라져도 된다) · `clean` 의 VALUE 줄 뒤에
  `editStage(st, LOCAL, Set.of(LocalsAst.UNUSED_LOCAL), (x, t, e, l) -> LocalFix.apply(x.text, x.cur, parser, e, l));`
- `Cleanup.worse:374` **한 줄만** — 새 상수 `private static final Map<String, Set<String>> MAY_APPEAR = Map.of(LOCAL, Set.of(UNUSED_IMPORT_JAVA));` 를 두고
  `Set<String> mayAppear = DELETE.equals(stage) ? Set.of(Ignores.UNUSED) : MAY_APPEAR.getOrDefault(stage, Set.of());`
  (삼항 하나 그대로 — `worse` 복잡도 35 불변). 지운 선언이 어떤 import 의 마지막 사용이면 `java.unusedImport` 가 새로 서도 채택한다. 그 import 는 다음 정리 실행의 delete 단계가 지운다
- **수 글 떼기**(뒤 행이 수를 또 안 고치게, 이 행에서 한 번): `Cleanup:20` javadoc 「열넷…기본 열둘」 → 「고칠 수 있는 규칙(`RULES`)을 글→글 함수로 고친다. 값·구조·출력 자리가 바뀌는 것(`OPTIONAL`)은 기본 끔」 · `CleanupRoutes:149` → 「고른 규칙 ∩ RULES — 비면 기본(OPTIONAL 은 골라야)」 · `CodeCommands:196` → 「…자동으로 고칠 수 있는 것을 미리보기한다(쓰지 않음). 기본 끔인 규칙은 --rules 로.」 · `:213` → 「고칠 규칙 id(없으면 기본 — 기본 끔인 것은 골라야)」 · `README.md:43` 같은 뜻으로
- `tools/code_cleanup_ext.js:9` `STAGE` 에 `local: '안 쓰는 변수'`
- 시험 — `CleanupRoutesTest.rulesListsFourteen:105-118` 을 `rulesListsFixable` 로 이름 바꾸고 15 · opt 둘 · `SmokeHtmlUnitTest:1827-1828` 15·13(`CodeCliTest` 에는 도움말 글 단언이 없다)
- cleanup 픽스처 `fixtures/cleanup/local/Pos.java`(최상위 클래스는 package-private — `compile()` 이 `Pos.java` 로 쓴다): 지워지는 것 셋(`int count = 0;` · `String label = "x";` · 꼬리 주석 달린 `int z = 3; // 메모`) · 건너뜀 다섯(`int n = xs.size();` · `int m = k;`(둘 다 「리터럴이 아니다」) · `int a = 1, b = 2;` + a 사용 · 두 줄에 걸친 `String s =\n "x";` · `catch (Exception e) { int only = 0; }` 의 「블록에 이 선언뿐」) · import 의 마지막 사용(`import java.util.Date;` + `Date d = null;` — 지워지고 `java.unusedImport` 가 남는다). 골든 `cleanup/local-Pos.java.txt` · `steps.json` 새 항목(`local` 단계 `java.unusedLocal` 9 → 5 · skips 다섯)
- `CleanupTest` 새 `localStage`(steps·skips 글 단언 · 결과 지적에 `java.unusedImport` 하나) · 새 `localKeepsSuppressed`(인라인 — `// tb-ignore-next java.unusedLocal 사유` 아래 `int kept = 0;` 은 그대로) · `valueAndResourceCompile` 의 폴더 목록에 `"local"`(javac)
- **기존 cleanup 골든**: `-Dgolden.update=true -Dtest=CleanupTest` 뒤 diff 가 **새 `local/Pos.java` 항목뿐**인지 읽는다(기존 픽스처에 unusedLocal 0 — 실측)
- 표본 기대(V-56a): `cleanup-*.json` steps 에 `local`

### 5-70a — `java.defaultCharset` 판정(§3-6)
- `rules.yaml` `java.unusedLocal` 뒤:
```yaml
  - id: java.defaultCharset
    group: java
    severity: warn
    kind: java
    globs: ['*.java']
    message: 문자셋 없는 getBytes()·new String(byte[]) — 실행 환경 기본 인코딩을 탄다
    fix: 'getBytes(StandardCharsets.UTF_8) 처럼 문자셋을 적는다'
    ref: 'SpotBugs DM_DEFAULT_ENCODING · OpenRewrite ExplicitCharsetOnStringGetBytes'
```
- `DeclaredTypes` — `declaredType` 의 몸을 `private static Optional<Type> find(Expression e)`(선언 `Type` 그대로)로 빼고 `declaredType(e) = find(e).map(DeclaredTypes::scalarType).orElse(UNKNOWN)` · 새 `static boolean isByteArray(Expression e) { return find(e).filter(t -> "byte[]".equals(t.asString().replace(" ", ""))).isPresent(); }`(8 → 10. `declaredType` 결과 불변 — 골든 diff 0 이 판정)
- `ValueAst`(22 → 25): `static final String DEFAULT_CHARSET = "java.defaultCharset";` · `private static final String GET_BYTES = "getBytes";`(`toByteArray`·`toString`·`valueOf`·`format` 도 상수로) · `apply` 에 `if (on.contains(DEFAULT_CHARSET)) { defaultCharsets(cu, out); }`
```java
/** 설계 60 — 수신자가 String 인 무인자 getBytes() · 인자가 byte[] 인 한 인자 new String(b) */
private static void defaultCharsets(CompilationUnit cu, List<Taint.Hit> out) {
    for (MethodCallExpr m : cu.findAll(MethodCallExpr.class)) {
        if (GET_BYTES.equals(m.getNameAsString()) && m.getArguments().isEmpty() && m.getScope().filter(ValueAst::stringy).isPresent()) {
            add(out, DEFAULT_CHARSET, m);
        }
    }
    for (ObjectCreationExpr o : cu.findAll(ObjectCreationExpr.class)) {
        if (STRING_TYPE.equals(o.getType().getNameAsString()) && o.getArguments().size() == ONE && bytes(o.getArgument(0))) {
            add(out, DEFAULT_CHARSET, o);
        }
    }
}

/** String 으로 보이는 식 — 문자열 리터럴(텍스트 블록) · 선언 형식 String 인 이름·this.x · 무인자 toString() · String.valueOf/format(…) · 한쪽이 stringy 인 + */
static boolean stringy(Expression e) { … strip 뒤 재귀 … }

/** byte[] 로 보이는 식 — 선언 형식 byte[] 인 이름·this.x · getBytes(…)·toByteArray() 호출 */
private static boolean bytes(Expression e) { … }
```
- 픽스처 `PosCharset.java` — `"가".getBytes()` · `String s` 매개변수의 `s.getBytes()` · `sb.toString().getBytes()` · `byte[] b` 의 `new String(b)` · `new String(bos.toByteArray())` / `NegCharset.java` — `s.getBytes(StandardCharsets.UTF_8)` · `s.getBytes("UTF-8")` · `MultipartFile mf` 꼴(선언 형식이 String 아님 — 픽스처에는 `Blob0 mf` 같은 제 클래스로)의 `mf.getBytes()` · 형식을 못 푸는 `vo.getName().getBytes()` · `char[] cs` 의 `new String(cs)` · `new String(b, StandardCharsets.UTF_8)` · `new String(b, 0, 1)`
- 시험 `ValueAstTest` 새 `defaultCharset`(픽스처 줄 목록)·`defaultCharsetNeedsStringReceiver`(인라인 — 못 푸는 수신자 0)
- 기존 픽스처·우리 코드: `java.json` diff 가 새 픽스처 줄 + 기존 **양성** 줄(있다면 `PosTaint.java` 의 `getBytes()` 한 자리)뿐인지 본다 · `java/Neg*` 에 걸리면 그 줄의 수신자를 문자셋 인자를 준 꼴로 고친다(`// tb-ignore` 금지) · 우리 코드에 걸리면 문자셋을 적어 고친다(기준선에 안 넣는다)
- `CheckRulesTest:167` 137 → 138
- 표본 기대(V-56a): 새 키 `java.defaultCharset` — jeecg·egov·shopizer·jeesite 에 수~수십

### 5-70b — 문자셋 고침 — `ValueFix` + 새 `Imports` + `RuleSet.encoding()`(§3-7)
- `RuleSet` — 생성자 끝 인자 `Profile.Naming naming` 을 `Profile profile` 로 **바꾼다**(인자 7 그대로): `this.naming = profile == null ? null : profile.naming();` · 새 필드 `private final String encoding;` = `profile == null || profile.project() == null ? null : profile.project().encoding()` · `load:118` 은 `profile` 을 그대로 넘긴다 · 새 접근자 `public String encoding()`(프로필 `project.encoding`, 없으면 null — 메서드 18 → 19). `:209` 의 지문 글 불변(naming 만 — 인코딩은 판정에 안 쓰인다). **`load` 의 삼항이 하나 줄어 `self-check.txt:59` 가 `복잡도 29` → `28` 로 바뀐다** — 그 한 줄만 `-Dgolden.update=true -Dtest=SelfCheckTest` 로 갱신하고 이력에 적는다(줄어든 것 — 새 키가 아니다)
- 새 파일 `core/check/Imports.java`(import 끼우기 공용 — `ValueFix` 의 다섯 도우미가 여기로):
```java
/** 설계 60 — 단일 import 끼우기 공용(ValueFix·LogFix). 줄은 Edits 에 한 번만 쓴다 */
final class Imports {
    /** 단일 import 로 fqn 이 있고 같은 단순 이름의 다른 단일 import 가 없다 */
    static boolean has(CompilationUnit cu, String fqn) { … }
    /** 같은 단순 이름의 다른 단일 import 가 있다 */
    static boolean clash(CompilationUnit cu, String fqn) { … }
    /** import 없이 그 단순 이름을 식의 이름(NameExpr)이나 형식(ClassOrInterfaceType)으로 쓴다 */
    static boolean used(CompilationUnit cu, String simple) { … }
    /** 끼울 줄 — 마지막 import 의 끝 줄, 없으면 package 줄, 그것도 없으면 0(맨 앞) */
    static int line(CompilationUnit cu) { … ValueFix.importLine 그대로 … }
    /** need 가운데 has 가 아닌 것을 "import a.B;" 줄들로(\n 이음, 주어진 차례). 없으면 빈 글 */
    static String text(CompilationUnit cu, Collection<String> need) { … }
}
```
- `ValueFix`:
  - `apply` 시그니처 끝에 `String encoding`(7 인자): `static void apply(String text, List<Finding> cur, Set<String> targets, JavaSource parser, String encoding, Cleanup.Edits e, List<Cleanup.Skip> skips)`
  - `apply` 안에 `Set<String> need = new LinkedHashSet<>();` — `refEquals` 는 `e.replace` 로 import 를 끼우는 대신 `need.add(OBJECTS_IMPORT)`(매개변수 `Cleanup.Edits e` 자리를 `Set<String> need` 로 — `rewrite` 도 같이). 충돌 판정은 `Imports.has`·`clash`·`used` 로(건너뜀 글 둘 그대로)
  - Hit 루프 안, `rewrite` 호출 앞: `DEFAULT_CHARSET` 이면 `charset(h.node(), lines[no - 1], no, encoding, cu, need, skips)` 의 결과를 `Change` 로 넣고 `continue`
  - 루프 뒤·`byLine.forEach` **앞**에 import 를 한 번:
```java
String imp = Imports.text(cu, need);
if (!imp.isEmpty()) { // 파일에 한 번 — 줄 바꾸기보다 먼저 써야 첫 쓰기가 이긴다(5-62c 차례 그대로)
    int at = Imports.line(cu);
    if (at == 0) {
        e.replace(1, imp + "\n" + lines[0]);
    } else {
        e.replace(at, lines[at - 1] + "\n" + imp);
    }
}
```
  - 새 메서드 둘:
```java
/** 설계 60 — x.getBytes() → x.getBytes(CS) · new String(b) → new String(b, CS). CS 는 프로필 인코딩(없으면 UTF-8) */
private static Optional<String> charset(Node n, String line, int no, String encoding, CompilationUnit cu, Set<String> need, List<Cleanup.Skip> skips) {
    String[] cs = charsetExpr(encoding);            // {식, import} — 꼴을 못 읽으면 길이 0
    // 길이 0 → skip "프로필 project.encoding 꼴을 못 읽음"
    // Imports.clash(cu, cs[1]) 또는 !Imports.has(cu, cs[1]) && Imports.used(cu, 단순 이름) → skip "다른 StandardCharsets·Charset 과 이름 충돌"
    // need.add(cs[1]) 뒤, 노드 글 t = slice(line, n) 의 마지막 ')' 앞에 끼운다:
    //   getBytes: t.substring(0, t.length() - 1).stripTrailing() + cs[0] + ")"
    //   new String: t.substring(0, t.length() - 1).stripTrailing() + ", " + cs[0] + ")"
}

/**
 * 대문자로 맞대 UTF-8·UTF8·UTF-8-BOM → StandardCharsets.UTF_8 · US-ASCII·ASCII → US_ASCII · ISO-8859-1·LATIN1 → ISO_8859_1 · UTF-16 → UTF_16
 * (import java.nio.charset.StandardCharsets). 그 밖은 글이 [A-Za-z0-9._-]+ 이고 Charset.isSupported(글) 일 때만 Charset.forName("프로필에 적힌 글 그대로")
 * (import java.nio.charset.Charset) — 아니면 길이 0. null·빈 글은 UTF-8. 이름을 지어 바꾸지 않는다(UTF-8-BOM 은 FileRule 이 받는 프로필 값이다)
 */
private static String[] charsetExpr(String encoding) { … }
```
  - 지운 다섯(`hasObjectsImport`·`usesObjects`·`conflictingObjectsImport`·`objectsImports`·`importLine`) → 15 − 5 + 2 = **12**
  - `new String(s.getBytes())` 처럼 안팎이 다 지적이면 `merge` 가 안쪽을 「겹친 식」 으로 건너뛴다(다음 정리 실행이 고친다 — 받아들임)
- `Cleanup`: `RULES` 끝에 `"java.defaultCharset"`(16) · `OPTIONAL` 에 더함(셋) · `VALUE_RULES` 에 `ValueAst.DEFAULT_CHARSET` · `clean` 의 VALUE 줄을 `ValueFix.apply(x.text, x.cur, t, parser, rules.encoding(), e, l)` 로
- 시험 — `CleanupRoutesTest.rulesListsFixable` 16 · opt 셋 · `SmokeHtmlUnitTest` 16·13 · `CleanupTest.objectsImportInserted` **불변 초록**(import 끼우기 왕복) · 새 `charsetStage`(프로필 UTF-8 → `StandardCharsets.UTF_8` + import 한 줄 · 한 파일에 `Objects` 와 `StandardCharsets` 가 **둘 다** 끼워진다) · 새 `charsetFromProfile`(프로필 `encoding: EUC-KR` → `Charset.forName("EUC-KR")` · `UTF-8-BOM` → `StandardCharsets.UTF_8` · `NO-SUCH-CS` → 건너뜀 「프로필 project.encoding 꼴을 못 읽음」)
- cleanup 픽스처 `fixtures/cleanup/charset/Pos.java`(`getBytes()` 둘 · `new String(b)` 하나 · 이름끼리 `==` 하나(같은 파일 Objects import) · `StandardCharsets` 라는 제 클래스를 쓰는 건너뜀은 **다른 파일** `Clash.java`). 골든 `cleanup/charset-Pos.java.txt`·`charset-Clash.java.txt`·`steps.json` · `valueAndResourceCompile` 폴더 목록에 `"charset"`
- 기존 `value-*.txt`·`steps.json` 의 value 항목 **불변**(import 차례·글 그대로)

### 5-71a — 로거 꼴 모으기 — 새 `LoggerShape`(배선은 5-71b)(§3-8)
- 새 파일 `core/check/LoggerShape.java`:
```java
/**
 * 설계 60 — 같은 소스 루트에서 가장 많이 쓰는 static 로거 선언 꼴. imports 는 끼울 단일 import(1~2), name 은 필드 이름,
 * template 은 선언 글(클래스 이름 자리는 {C}). 코드 본문이 아니라 선언 한 줄의 꼴만 든다
 */
record LoggerShape(List<String> imports, String name, String template) {
    private static final Pattern DECL = Pattern.compile("^\\s*((?:(?:private|protected|public|static|final)\\s+)+)(Logger|Log)\\s+(\\w+)\\s*=\\s*(\\w+)\\.(getLogger|getLog)\\(\\s*(\\w+)\\.class\\s*\\)\\s*;\\s*$");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+([\\w.]+\\.(\\w+))\\s*;");
    private static final Pattern SOURCE_ROOT = Pattern.compile("^(.*?/)?src/(?:main|test)/java/|^(.*?/)?src/");

    /** 파일 글 하나의 꼴 — static 이고, 인자가 그 파일에 선언된 클래스 이름이고, 형식·팩토리의 단일 import 가 그 파일에 있을 때만 */
    static Optional<LoggerShape> of(String text) { … 줄마다 DECL · 수식어에 static · `\bclass\s+<인자>\b` 가 글에 있음 · import 줄에서 단순 이름이 형식(group 2)·팩토리(group 4)인 것(같으면 하나) · template = 줄.strip() 에서 "(<인자>.class" 를 "({C}.class" 로 … }

    /** rel(/ 구분)의 소스 루트 접두 — "…/src/main/java/"·"…/src/test/java/" 까지, 없으면 첫 "…/src/" 까지, 그것도 없으면 "" */
    static String sourceRoot(String rel) { … }

    String decl(String className) {
        return template.replace("{C}", className);
    }

    /** root 아래를 소스 루트마다 한 번 훑어(게으르게, 결과 기억) 가장 흔한 꼴 — 동률이면 template 글 순. 못 읽는 파일은 건너뛴다 */
    static Function<String, Optional<LoggerShape>> finder(LocalFiles files, String root) { … 
        // 돌려주는 람다 **안에서**(게으르게 — finder 를 부르는 것만으로는 아무것도 안 읽는다): rel → sourceRoot(rel) → 기억에 없으면
        //   Path dir = files.check(root).resolve(접두);
        //   files.list(dir.toString(), List.of("*.java"), List.of(), LocalFiles.MAX_FILES).files() 의 e 마다 — e.rel() 은 **dir 기준**(LocalFiles:213)
        //   files.read(dir.resolve(e.rel()).toString()).text() → of(text) → Map<LoggerShape, Integer> 세기 (IOException·LocalFiles.Refused 는 그 파일만 건너뜀)
    }
}
```
  record 라 `metric.classMethods` 가 안 센다. `finder` 가 복잡도 15 를 넘으면 세는 부분을 `private static Optional<LoggerShape> scan(LocalFiles, Path dir)` 로 뺀다
- **이 행은 `LoggerShape` 와 그 시험만** — `Cleanup` 필드·생성자·`CleanupRunner` 배선은 5-71b 로 민다(안 쓰는 private 필드가 PMD `UnusedPrivateField`·SpotBugs `URF_UNREAD_FIELD` 에 걸린다)
- 새 시험 `LoggerShapeTest` — `of`: slf4j `private static final Logger logger = LoggerFactory.getLogger(A.class);`(import 둘) ✓ · log4j1 `Logger.getLogger(A.class)`(import 하나) ✓ · commons-logging `Log log = LogFactory.getLog(A.class)` ✓ · static 아님 ✗ · `getLogger(getClass())` ✗ · 와일드카드 import 만 ✗ · 다른 클래스 이름 인자 ✗ / `sourceRoot`: `a/src/main/java/p/A.java` → `a/src/main/java/` · `src/p/A.java` → `src/` · `p/A.java` → `` / `finder`(@TempDir — 같은 루트에 slf4j 꼴 둘·commons 꼴 하나 → slf4j · 다른 소스 루트의 것은 안 섞인다 · 꼴이 없는 루트는 빈 값)
- 골든 변화 없음(행동 불변)

### 5-71b — `System.out` → 로거 — 새 `LogFix` + 단계 `log`(§3-9)
- 새 파일 `core/check/LogFix.java`. 건너뜀 글은 아래 표의 것만:

| 조건(위에서부터) | 처리 |
|---|---|
| 파일이 Java 가 아님(`!"java".equals(s.lang())`) | 아무것도 안 함(사유도 없음 — jsp 는 지적만) |
| `Cleanup.javaUnsafe` · 파싱 실패 | 사유 하나로 끝 |
| 지적 줄(`cur` 의 `common.sysout` 줄 — `tb-ignore` 로 눌린 줄은 없다)에 `System.out/err.print·println` 호출 노드가 없다(`printf`·`format`·`PrintStream p = System.out` 등) | skip 「줄 n: print·println 호출이 아니다」 |
| 호출이 여러 줄 | skip 「줄 n: 여러 줄 식」 |
| 인자가 0 개(`println()`) | skip 「줄 n: 인자 없는 출력」 |
| 둘러싼 메서드가 `main`(이름 `main` · static · 매개변수 하나) | skip 「줄 n: main 의 콘솔 출력」 |
| 호출이 catch 몸 안 | skip 「줄 n: catch 안 출력 — 로그·던지기 판정이 바뀌어 손으로」 |
| 호출이 초기화 블록(`InitializerDeclaration`)·필드 초기화식 안 | skip 「줄 n: 초기화 블록·필드 초기화식 안」(로거 필드보다 앞서 돌면 앞쪽 참조가 된다) |
| 파일이 `java.util.logging.Logger` 또는 `java.util.logging.*` 를 import | skip 「줄 n: java.util.logging 로거 — debug·error 가 없다」 |
| 쓸·끼울 로거 이름과 같은 이름의 **매개변수·지역 변수**가 파일에 있다 | skip 「줄 n: 로거 이름과 같은 지역 변수·매개변수가 있다」 |
| 쓸 로거가 인스턴스 필드인데 호출 자리가 static 문맥 | skip 「줄 n: static 문맥 — 인스턴스 로거를 못 쓴다」 |
| 로거가 없고 최상위 형식이 클래스가 아님(인터페이스·enum·record·애너테이션) | skip 「줄 n: 인터페이스·enum·record — 로거 필드를 안 넣는다」 |
| 로거가 없고 `loggers.apply(rel)` 이 빈 값 | skip 「줄 n: 로거가 없고 같은 소스 루트에 베낄 로거 꼴도 없다」 |
| 로거가 없는데 끼울 이름이 파일 어디든 식의 이름(`NameExpr`)으로 나온다 | skip 「줄 n: 물려받은 로거를 쓰는 것으로 보인다 — 손으로」 |
| 끼울 import 가 다른 import·형식과 겹침(`Imports.clash` 또는 `!has && used`) | skip 「줄 n: 로거 형식이 이 파일의 다른 것과 겹친다」 |
| 그 밖 | 바꾼다 |

- **쓸 로거 찾기** — 호출에서 바깥으로 둘러싼 형식을 차례로: ① 그 형식의 필드 가운데 선언 형식 단순 이름이 `Logger`·`Log` 인 첫 필드(이름·static 여부) ② 그 형식에 Lombok `@Slf4j`·`@Log4j`·`@Log4j2`·`@CommonsLog`·`@XSlf4j`·`@JBossLog` → 이름 `log`(static). `@Log`(java.util.logging)·`@Flogger` 는 **안 친다**(`debug`·`error` 가 없다). 못 찾으면 「없음」
- **static 문맥** — 호출에서 그 필드를 선언한 형식까지 올라가는 길에 static 메서드·static 중첩 형식(인터페이스·enum·record 포함)이 있다
- **바꾸는 글** — 호출 노드 범위를 `<이름>.<수준>(<인자>)` 로: `System.out` → `debug` · `System.err` → `error` · 인자는 `ValueAst.stringy(arg)` 면 원문 그대로, 아니면 `String.valueOf(<원문>)`
- **끼우기(로거 없음)** — 최상위 클래스 T 마다, **그 클래스에서 실제로 바꾸는 호출이 하나 이상일 때만** 한 번(안 그러면 끼운 import 가 안 쓰여 단계가 되돌려진다):
  - import: `Imports.text(cu, shape.imports())` 를 `Imports.line(cu)` 줄에(5-70b 와 같은 꼴)
  - 필드 자리: T 의 멤버를 차례로 훑어 **첫 인스턴스 필드·생성자·메서드** M 앞까지 본다(초기화 블록·중첩 형식은 지나친다). 그 앞에 static 필드가 있으면 **마지막 static 필드 뒤**(그 선언 끝 줄 글 + `"\n"` + 그 줄 들여쓰기 + `shape.decl(T 이름)`), 없으면 M(주석·애너테이션 포함 시작 줄) **앞**(들여쓰기 + 선언 + `"\n\n"` + 그 줄 글). M 도 static 필드도 없으면 skip 「줄 n: 빈 클래스 몸」(표에 한 줄 더). 마지막 static 필드의 끝 줄에 다른 코드가 있으면 skip 「줄 n: 로거를 끼울 자리의 줄 꼴이 다르다」
  - 그 자리인 까닭 — `java.declarationOrder`(`JavaRules.orderViolations:432-469` — static 필드는 public·protected·package·private 순, 그 뒤 인스턴스)가 새로 안 걸리게. 그래도 걸리면 단계가 통째 되돌려진다(「다른 지적이 생김」 — 의도된 보수성)
- **단계가 통째 되돌려질 수 있는 것**(받아들임, 건너뜀 글은 `worse` 가 낸다): 바꾼 줄에 `security.piiLog`(`rules.yaml:464` — 비밀번호류 이름을 로그에 남김)가 새로 서는 파일 — 「다른 지적이 생김: security.piiLog」. 사람이 봐야 하는 줄이다. R-23 이 표본에서 몇 파일인지 센다
- **한 줄에 쓸 것은 합쳐 한 번** — `Map<Integer, String> out`(줄 → 새 글)에 호출 바꿈을 먼저 넣고(같은 줄 여럿은 열 내림차순), 끼움은 `out.getOrDefault(줄, 원문)` 에 덧붙인 뒤, 끝에 줄마다 `e.replace` 한 번
- `Cleanup`(5-71a 에서 민 배선 포함): 필드 `private final Function<String, Optional<LoggerShape>> loggers;` · 새 생성자 `public Cleanup(RuleSet rules, Collection<String> wanted, String lineEnding, Function<String, Optional<LoggerShape>> loggers)`(기존 몸 + 한 줄) · 기존 3-인자는 `this(rules, wanted, lineEnding, rel -> Optional.empty())`(생성자는 메서드 수에 안 든다 — 29 그대로) · `public static final String LOG = "log";` · `RULES` 끝에 `"common.sysout"`(17) · `OPTIONAL` 에 더함(넷) · `clean` 의 LOCAL 줄 뒤에
  `editStage(st, LOG, Set.of("common.sysout"), (x, t, e, l) -> LogFix.apply(x.source(), x.cur, parser, loggers, e, l));`
- `CleanupRunner.preview:69`·`apply:123` → `new Cleanup(rules, wanted, lineEnding, LoggerShape.finder(files, root))`(시그니처 불변 — `finder` 는 게을러서 `globs.isEmpty()` 이른 반환 길의 행동이 안 바뀐다) · `CleanupCorpusTest:44` → `new Cleanup(…, "LF", LoggerShape.finder(files, CorpusFiles.root().toString()))`
- `tools/code_cleanup_ext.js` `STAGE` 에 `log: '로거로(출력 자리 바뀜)'`
- cleanup 픽스처(최상위 클래스는 전부 **package-private** — `compile()` 이 `Pos.java` 라는 이름으로 쓴다) `fixtures/cleanup/log/Pos.java`(slf4j `private static final Logger logger` 가 있는 클래스 — `System.out.println("a")` → `logger.debug("a")` · `System.err.println(n)`(int) → `logger.error(String.valueOf(n))` · `System.out.print("x" + n)` → 그대로 인자 · `println()` 건너뜀 · `printf` 건너뜀 · `main` 안 건너뜀 · 두 줄 호출 건너뜀 · catch 안 건너뜀 · static 초기화 블록 안 건너뜀) · `log/Instance.java`(`private Logger logger` 인스턴스 필드 — 인스턴스 메서드는 바꿈 · static 메서드는 건너뜀) · `log/NoLogger.java`(로거 없음, `public static final String A` · 초기화 블록 · `protected static int B` · 인스턴스 필드 · 메서드 순 — 3-인자 `Cleanup` 이라 「베낄 로거 꼴도 없다」 건너뜀) · `log/Jul.java`(`java.util.logging.Logger` import — 건너뜀) · `log/Shadow.java`(`@Slf4j` 대신 `private static final Logger log` + `void save(String log)` — 건너뜀)
- 시험 `CleanupTest` 새 `logStage`(위 건너뜀 글들) · 새 `loggerInserted` — `new Cleanup(rules, Cleanup.RULES, "LF", rel -> Optional.of(new LoggerShape(List.of("org.slf4j.Logger", "org.slf4j.LoggerFactory"), "logger", "private static final Logger logger = LoggerFactory.getLogger({C}.class);")))` 로 `NoLogger.java` → 골든 `cleanup/log-NoLogger.inserted.java.txt`(import 두 줄이 마지막 import 뒤 · 필드가 **`B` 선언 뒤**(초기화 블록을 지난 마지막 static 필드) · 호출 바뀜 · `java.declarationOrder` 새 지적 없음) · javac 로 `Pos`·`Instance`·끼운 `NoLogger` 의 원문·고친 글 컴파일 — `compile` 에 클래스패스 인자를 받는 겹침(overload)을 더해 `"-classpath", System.getProperty("java.class.path")` 를 준다(slf4j — `DdlCorpusTest:198` 전례)
- 새 `CleanupRunnerTest.loggerShapeFromSameSourceRoot`(@TempDir — `src/main/java/p/A.java`(slf4j 로거 있음)·`B.java`(로거 없음 + `System.out.println`) → `preview`(wanted 에 `common.sysout`) 뒤 `apply` 가 B 를 결과 폴더에 쓰고 **원본 불변**(규칙 7) · B 결과에 필드·import)
- `CleanupRoutesTest.rulesListsFixable` 17 · opt 넷 · `SmokeHtmlUnitTest` 17·13
- 골든 `cleanup/log-*.txt` 다섯 + `log-NoLogger.inserted.java.txt` · `steps.json`
- 표본 기대(V-56a): `cleanup-*.json` steps 에 `log`, skips 에 위 글들. `SelfCheckTest.cleanupChangesNothing` 불변(우리 `System.err` 여섯은 `tb-ignore` 라 지적이 없다)

### 5-72 — 마크업 고침 둘 — 새 `MarkupFix` + 단계 `markup`(§3-10)
- 새 파일 `core/check/MarkupFix.java`:
```java
/** 코드 정리 마크업(설계 60) — 정규식 규칙 둘의 지적 줄을 줄 안에서 고친다: <html> 에 lang · target=_blank 인 <a> 에 rel */
final class MarkupFix {
    static final String HTML_LANG = "a11y.htmlLang";
    static final String BLANK = "tsx.blankNoopener";
    private static final String LANG = " lang=\"ko\"";
    private static final String REL = " rel=\"noopener noreferrer\"";
    /** 줄 머리(공백 뒤)가 <html 이고 바로 뒤가 공백이나 > — <html:form>(Struts)·문자열 안 "<html>" 은 아니다. group(1) 은 같은 줄에서 닫힐 때만 */
    private static final Pattern HTML_TAG = Pattern.compile("^\\s*<html(?=[\\s>])(?![^>]*\\blang\\s*=)([^>]*>)?");
    private static final Pattern A_TAG = Pattern.compile("<a\\b[^<]*");
    private static final Pattern TARGET = Pattern.compile("\\btarget\\s*=\\s*(?:\"_blank\"|'_blank'|\\{\\s*[\"']_blank[\"']\\s*\\})");
    private static final Pattern REL_ATTR = Pattern.compile("\\brel\\s*=");
    /** 태그를 닫는 > — 화살표 => 의 > 는 아니다 */
    private static final Pattern TAG_END = Pattern.compile("(?<!=)>");
    private static final Pattern SCRIPT_OPEN = Pattern.compile("<script\\b");
    private static final Pattern SCRIPT_CLOSE = Pattern.compile("</script\\s*>");

    static void apply(Source s, List<Finding> cur, Set<String> targets, Cleanup.Edits e, List<Cleanup.Skip> skips) {
        // 지적(cur) 가운데 규칙이 targets 에 든 줄마다:
        // HTML_LANG — 그 줄이 HTML_TAG 에 안 맞으면 skip "줄 n: 줄 머리가 <html> 이 아니다"
        //             맞았는데 group(1) 이 없으면(같은 줄에서 안 닫힘) skip "줄 n: 여러 줄 태그"
        //             그 줄 앞까지의 글에서 SCRIPT_OPEN 수 > SCRIPT_CLOSE 수 → skip "줄 n: script 안 <html>"
        //             "<html" 바로 뒤에 LANG 을 끼운다
        // BLANK     — 그 줄의 A_TAG 조각마다(오른쪽부터) TARGET 이 있고 REL_ATTR 가 없고 **TAG_END 가 있으면**(그 줄에서 닫힌다 —
        //             다음 줄에 rel 이 있을 수 있다) TARGET 끝 바로 뒤에 REL. 한 조각도 못 고치면 skip "줄 n: 여러 줄 태그"
        // 줄마다 e.replace 한 번
    }
}
```
  `apply` 가 복잡도 15 를 넘으면 `lang(String line, …)`·`rel(String line)` 둘로 나눈다
- `Cleanup`: `public static final String MARKUP_STAGE = "markup";`(이름 `MARKUP` 은 `:68` 의 언어 집합이 쓴다) · `RULES` 끝에 `"a11y.htmlLang"`, `"tsx.blankNoopener"`(19) · `clean` 의 MARKER 줄 뒤에
  `editStage(st, MARKUP_STAGE, Set.of(MarkupFix.HTML_LANG, MarkupFix.BLANK), (x, t, e, l) -> MarkupFix.apply(x.source(), x.cur, t, e, l));`
- `tools/code_cleanup_ext.js` `STAGE` 에 `markup: '마크업'`
- cleanup 픽스처 `fixtures/cleanup/markup/pos.jsp`(`<html>` → 고침 · 그 아래 `<script>` 안 줄 머리 `<html>` → 건너뜀 · `<% out.println("<html>"); %>` → 건너뜀(줄 머리 아님)) · `markup/attrs.html`(`<html xmlns="http://www.w3.org/1999/xhtml">` → `<html lang="ko" xmlns=…>`) · `markup/struts.jsp`(`<html:html>` — 건너뜀) · `markup/pos.tsx`(`<a href="/o" target="_blank">` → rel · `target={'_blank'}` → rel · 이미 rel 있는 줄 불변 · 한 줄에 `<a>` 둘 · `<a href="/o" target="_blank"` 로 줄이 끝나고 다음 줄에 `rel=…>` → 건너뜀)
- 골든 `cleanup/markup-pos.jsp.txt`·`markup-attrs.html.txt`·`markup-struts.jsp.txt`·`markup-pos.tsx.txt`·`steps.json`
- 시험 `CleanupTest` 새 `markupStage`(skips 글) · `CleanupRoutesTest.rulesListsFixable` 19 · opt 넷 · `SmokeHtmlUnitTest` 19·15
- `rules.yaml` 의 두 규칙은 **안 건드린다**(순수본 짝)
- 표본 기대(V-56a): `cleanup-egov.json` steps `markup` 480 안팎 · `cleanup-roller.json` 11 안팎 · `cleanup-egov-react.json` 0(tsx 지적 0). `SelfCheckTest.cleanupChangesNothing` 불변(우리 화면은 전부 `lang` 이 있다 — 실행이 확인)

### 4-31 — 개발자 도구 「해시」 탭(§3-11)
- `tools/dev_tools.html` — 탭 줄 **끝**(`logsql` 뒤): `<div role="tab" tabindex="0" class="tab" data-tab="hash" onclick="switchTab('hash')" title="글·Hex 바이트·파일의 MD5·SHA-1·SHA-256·SHA-384·SHA-512 해시(키를 넣으면 HMAC)를 브라우저에서 계산&#10;기대값과 맞대 같은지 표시 — 입력은 서버로 안 나간다">해시</div>`
- 페이지 `<div class="page" id="page-hash" style="overflow:auto">`(마지막 `.page` 뒤, `#toast` 앞) — 전부 정적 마크업, 기존 클래스(`.row`·`.col`·`.label`·`.enc-drop`·`.btn-p`·`.btn-green`)만:
  - 입력 `textarea#hs_in`(`aria-label="해시 입력"`) · 입력 꼴 `select#hs_mode`(`aria-label="입력 꼴"` — `text` 글(UTF-8) / `hex` Hex 바이트) · 파일 `div.enc-drop#hs_drop` + `input[type=file]#hs_file`(`aria-label="해시할 파일"`, `style="display:none"`)
  - 키 `input#hs_key`(`aria-label="HMAC 키"`, `placeholder="비우면 해시, 채우면 HMAC"`) · `select#hs_key_mode`(`aria-label="키 꼴"` — text / hex)
  - 출력 꼴 `select#hs_fmt`(`aria-label="출력 꼴"` — hex 소문자 / Base64) · 기대값 `input#hs_expect`(`aria-label="기대값"`)
  - `button.btn-p` 「계산」 `onclick="hashRun()"` · 판정 `div#hs_verdict`
  - 결과 다섯 줄 — 각 `div.row`: `label for` + `input#hs_md5|hs_sha1|hs_sha256|hs_sha384|hs_sha512`(`readonly`) + `button.btn-green` 「복사」 `onclick="copyEl('hs_…')"`
- 인라인 JS(`hexToBytes` 뒤) — **순수 함수(DOM 없음, corpus-js 가 부른다)**:
```js
/* 4-31(설계 60) — 해시 순수 함수. MD5 는 RFC 1321 직접 구현(crypto.subtle 에 MD5 가 없다), SHA·HMAC-SHA 는 crypto.subtle */
function hexPlain(bytes){ … 소문자 16진, 구분 없음 … }
function b64Of(bytes){ … btoa(String.fromCharCode.apply(null, bytes)) … }
function md5Bytes(bytes){ … Uint8Array(16). 32비트 덧셈은 (a+b)|0, 회전은 (x<<n)|(x>>>(32-n)), 길이는 비트 수 64비트 리틀엔디언(상위 32비트는 Math.floor(len/0x20000000)) … }
function hmacMd5(key, msg){ … 블록 64: 키가 64 넘으면 md5Bytes(key), ipad 0x36 · opad 0x5c … }
async function shaBytes(alg, bytes){ return new Uint8Array(await crypto.subtle.digest(alg, bytes)); }
async function hmacSha(alg, key, msg){ var k=await crypto.subtle.importKey('raw', key, {name:'HMAC', hash:alg}, false, ['sign']); return new Uint8Array(await crypto.subtle.sign('HMAC', k, msg)); }
/* key 가 null·길이 0 이면 해시, 아니면 HMAC. crypto.subtle 이 없으면 sha* 는 null */
async function hashAll(bytes, key){ … {md5, sha1, sha256, sha384, sha512} … }
/* 기대값(공백 뗌)이 어느 결과의 hex(대소문자 무시)·Base64 와 같으면 그 이름('MD5'·'SHA-1'…), 없으면 '' */
function hashMatch(expect, all){ … }
```
- DOM 함수 `hashRun()`(입력 꼴대로 `new TextEncoder().encode` 또는 `hexToBytes` → `hashAll` → 다섯 `input.value` · `hs_verdict.textContent` — 「기대값과 같다 — SHA-256」/「어느 것과도 다르다」/빈 글) · `hashFile(file)`(`file.arrayBuffer()` → 같은 길) · 파일 끌어놓기 배선은 `enc_drop` IIFE(`:2087-2094`) 꼴 그대로 · `crypto.subtle` 이 없으면 SHA 칸에 「이 문맥에서는 SHA 를 못 쓴다 — run.bat 이 연 주소로 연다」(글에 `127.0.0.1` 을 적지 않는다 — `common.localhost` 가 html 에도 걸린다). 오류는 `showToast(e.message)`. **`innerHTML` 을 안 쓴다**(`textContent`·`value` 만) · 입력·키·결과를 `lsSet` 으로 **안 남긴다**
- Ctrl+Enter 분기(`:2115` 뒤)에 `else if(id==='page-hash') hashRun();`
- `tools/index.html:48` 카드 글 끝 「…로그 SQL 복원 등.」 앞에 「, 해시(MD5·SHA·HMAC)」 를 끼운다
- `scripts/puppeteer/corpus-js.js` — `out.hexCases` 뒤:
```js
// 4-31 — 해시: 길이 경계(MD5·SHA 패딩 55·56·63·64·65 · SHA-512 111·112·127·128·129)와 키 없음·짧은 키·블록보다 긴 키
const lens = [0, 1, 3, 55, 56, 63, 64, 65, 111, 112, 119, 120, 127, 128, 129, 1000];
const hx = n => Array.from({ length: n }, (_, i) => ((i * 7 + 3) & 255).toString(16).padStart(2, '0')).join('');
out.hashCases = await dev.evaluate(async cs => {
  const r = [];
  for (const c of cs) {
    const all = await hashAll(hexToBytes(c.hex), c.key ? hexToBytes(c.key) : null);
    r.push({ hex: c.hex, key: c.key, md5: hexPlain(all.md5), sha1: hexPlain(all.sha1), sha256: hexPlain(all.sha256), sha384: hexPlain(all.sha384), sha512: hexPlain(all.sha512), b64: b64Of(all.sha256) });
  }
  return r;
}, [{ hex: '616263', key: '' }].concat(lens.flatMap(n => ['', '6b6579', hx(200)].map(key => ({ hex: hx(n), key })))));
out.hashMatch = await dev.evaluate(() => { const a = { md5: md5Bytes(new Uint8Array(0)) }; return [hashMatch(' D41D8CD98F00B204E9800998ECF8427E ', a), hashMatch('1B2M2Y8AsgTpgAmY7PhCfg==', a), hashMatch('00', a)]; });
```
  (`hashMatch` 가 `all` 의 없는 칸을 건너뛰게 쓴다)
- `JsCorpusTest` 새 `hashMatchesJdk` — `summary.json` 의 `hashCases` **49** 건(`abc` 하나 + 길이 16 × 키 셋)마다 `MessageDigest`(`MD5`·`SHA-1`·`SHA-256`·`SHA-384`·`SHA-512`) 또는 키가 있으면 `Mac`(`HmacMD5`·`HmacSHA1`·`HmacSHA256`·`HmacSHA384`·`HmacSHA512`)과 hex 가 같다 · `b64` = `Base64.getEncoder()` · RFC 1321 정답 둘을 리터럴로(첫 건 `abc` → `900150983cd24fb0d6963f7d28e17f72` · 둘째 건 빈 입력·키 없음 → `d41d8cd98f00b204e9800998ecf8427e`) · `hashMatch` = `["MD5", "MD5", ""]`. 도우미가 복잡도 15 를 넘으면 `digest(String alg, byte[] in, byte[] key)` 로 뺀다
- 화면만 고치는 행 — TDD 예외(`Ruling:` 한 줄). RED→GREEN 자리는 `JsCorpusTest.hashMatchesJdk`

### 4-32 — 개발자 도구 「문자 코드·진법」 탭(§3-12)
- 탭(4-31 의 `hash` 뒤): `data-tab="charcode"`, `title="글자를 유니코드 코드 포인트·UTF-8·UTF-16 값으로, 코드 값을 글자로 변환&#10;정수를 2·8·10·16진으로 변환"`, 글 「문자 코드·진법」
- 페이지 `#page-charcode` — 윗칸 문자 코드: `textarea#cc_in`(`aria-label="문자 코드 입력"`) · `textarea#cc_out.out-area`(`readonly`, `aria-label="문자 코드 출력"`) · `button.btn-p` 「글자 → 코드」 `ccEncode()` · `button.btn-p` 「코드 → 글자」 `ccDecode()` · `button.btn-green` 「복사」 `copyEl('cc_out')` / 아랫칸 진법: `input#rx_in`(`aria-label="정수 입력"`) · `select#rx_base`(`aria-label="입력 진법"` — 자동·2·8·10·16) · `button.btn-p` 「변환」 `radixRun()` · 결과 넷 `input#rx_2|rx_8|rx_10|rx_16`(`readonly`, `label for`) + 「복사」
- 순수 함수:
```js
/* 4-32(설계 60) — 문자 코드·진법 순수 함수 */
/* 코드 포인트마다 한 줄 "글자\tU+XXXX\t10진\tUTF-8 hex\tUTF-16 hex" — max 를 넘으면 앞 max 개. 제어 문자(< 0x20, 0x7F)는 글자 칸을 빈 글로 */
function ccRows(text, max){ … for (const ch of text) · codePointAt(0) · new TextEncoder().encode(ch) → hexOf · UTF-16 은 charCodeAt 으로 … }
/* "U+AC00"·"\uAC00"·"\u{1F600}"·"0xAC00"·"&#44032;"·"&#xAC00;"·10진 을 공백·쉼표·줄바꿈으로 끊어 코드 포인트 배열로. 못 읽는 토막·0x10FFFF 초과·서로게이트 단독은 Error('코드가 아니다: '+JSON.stringify(토막)) */
function ccParse(text){ … }
/* 부호·'_'·공백·0x/0b/0o 머리를 받아 BigInt 로. base 가 0 이면 머리로 정하고 머리가 없으면 10. 못 읽으면 Error('정수가 아니다: …') */
function radixParse(text, base){ … 자리마다 BigInt 곱·더하기(BigInt('0x…') 에 기대지 않는다 — 8·2진 머리 없는 입력) … }
function radixAll(n){ … {b2, b8, b10, b16(대문자)} — 음수는 '-' 를 앞에 … }
```
- DOM 함수 `ccEncode()`(앞 2,000 코드 포인트 · 라벨에 전체 수) · `ccDecode()`(`String.fromCodePoint.apply(null, ccParse(…))`) · `radixRun()`. 오류는 `showToast`. `innerHTML` 안 씀. Ctrl+Enter 분기에 `else if(id==='page-charcode') ccEncode();`
- `tools/index.html:48` 카드 글에 「, 문자 코드·진법」
- `corpus-js.js` — `out.ccRows`(`'A가😀\t'` 와 `'e\u0301'` 두 입력) · `out.ccParse`(`'U+AC00 \\uAC00 0xAC00 &#44032; &#xAC00; 44032 \\u{1F600}'` → 배열 · 오류 넷 `'U+110000'`·`'U+D800'`·`'xyz'`·`'&#;'`) · `out.radix`(입력 `['255', '0xFF', '-0b1010', '0o17', '1_000', '18446744073709551616']` × base 0 과, `'ff'` × 16 · `'777'` × 8 → `radixAll` / 오류 `'12'` × 2 · `''` × 10)
- `JsCorpusTest` 새 `charcodeAndRadixMatchJdk` — `ccRows` 를 Java `codePoints()`·`getBytes(UTF_8)`·`toChars` 와 맞댐(UTF-8·UTF-16 칸은 페이지의 `hexOf` 꼴 — **대문자·공백 구분** `EA B0 80` — 으로 Java 쪽도 만든다) · `ccParse` 결과 `[0xAC00 ×6, 0x1F600]` · 오류 넷이 오류 · `radix` 는 `new BigInteger(…)`.`toString(2|8|10|16)` 과 같음 · 오류 둘이 오류
- TDD 예외(`Ruling:`). RED→GREEN 자리는 `JsCorpusTest.charcodeAndRadixMatchJdk`

### 0-75 — `bundle/SOURCES.md` Tibero 줄(§3-13)
- `:15` 의 셋째 칸 「**Maven Central 에 없다** — TmaxData 기술 지원 사이트의 개발자판 `tibero*.jar` 와 라이선스 파일을 사람이 넣는다」 → 「Maven Central 에 `com.tmaxtibero:tbjdbc17`(7.2.6, Tibero Trial License)이 있으나 **현장 DB 판 jar 를 사람이 넣는다** — TmaxData 기술 지원 사이트의 `tibero*.jar` 와 라이선스 파일」. 다른 칸·`bundle-fetch.sh`·`MANIFEST`·드라이버 pom 불변
- verify 레인 밖(`bundle/` 은 release 지문 — 다음 `package.sh` 가 `--full` 을 다시 요구한다. 이 번들에서 `--full` 을 안 돈다)

### V-56a — corpus 골든 재채점(§3-14)
- 순서: `bash scripts/mvn.sh -q -DskipTests verify` rc 0 뒤 → `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='CheckCorpusTest,LegacyCorpusTest,VcsCorpusTest,CliCorpusTest,JulietCorpusTest,SecurityBenchmarkCorpusTest,SqlRuleCasesCorpusTest,SqlFixtureCorpusTest,CleanupCorpusTest,CleanupCompileCorpusTest,JsCorpusTest' -Djunit.timeout=10m -Dgolden.update=true`(RAM — 다른 세션이 큰 작업 중이면 미룬다) → `git diff --stat src/test/resources/golden/corpus`
- 기대 변화(이것만): `check-*.json`·`check-*-hits.txt` — 새 키 `java.unusedLocal`·`java.defaultCharset` · catchThrowable +1 안팎(jeecg) · tryWithResources +2~4(ruoyi-classic·roller) / `vcs-egov.json` fullFindings·findings / `cleanup-*.json` — steps 에 `local`·`log`·`markup`, `value` 수 변화(문자셋은 기본 끔이지만 이 시험은 `RULES` 전부) · skips 에 새 글과 SHAPE 새 글 / `security-benchmark.json`·`security-juliet*`(새 규칙 둘이 걸리면 총 건수 칸만 — **채점 칸(TP·FP·FN) 불변**) / `js-summary.json` 불변
- `CleanupCompileCorpusTest`(commons 셋 javac 오류 0)가 `local`·문자셋 고침의 컴파일을 잰다
- 건수 전후·벽시계를 이력에

### R-23 — 손 판정(§3-15)
- CLI `toolbox.bat check <표본> --groups java,security --json --profile example`(data 는 scratch) jeecg·jeesite·roller·zheng·ruoyi-classic·egov·shopizer·commons-lang. 읽을 줄 — `java.unusedLocal` 표본마다 **20줄**(참·오탐) · `java.defaultCharset` 전부(≤ 80 — 넘으면 표본마다 20) · catchThrowable 새 것 전부 · tryWithResources 새 것 전부
- 고침 꼴 눈 확인(결과는 scratch 빈 폴더, 본 뒤 지운다): `toolbox.bat cleanup <jeecg> --write --out <scratch> --rules common.sysout,java.unusedLocal,java.defaultCharset,a11y.htmlLang --profile example` → 로거 있는 파일 하나 · 로거를 끼운 파일 하나(끼운 꼴이 jeecg 의 지배 꼴 log4j1 `Logger.getLogger` 인지, import 가 `org.apache.log4j.Logger` 한 줄인지) · `main` 건너뜀 하나 · 지역 변수 삭제 하나 · `getBytes` 하나 · `<html lang="ko">` 하나. zheng(모듈 여럿 — 소스 루트마다 꼴이 따로인지) 한 번 더
- 오탐이면 음성 픽스처(또는 인라인 시험) 먼저 RED → 고침 ≤ 2 → V-56a 다시. 표는 compare ㉖ 「손 판정」, `파일:줄` 만

### V-56 — compare ㉖ · 정적 분석 · `--corpus`(§3-16)
- `design/compare-2026-10-28.md` 끝 `## ㉖ 재측정 — 번들 76(2026-11-09)`: 「설계 60. 올라간 후보 — 라-1·9·12 · ㉕ 「후보로」 셋(꼴 A' 변형 둘 · catchThrowable getMessage) · 5-66 · 바-6·바-7 나머지」 · **뺀 것** — `jspElParam` 고침(고칠 자리 0) · JSP·HTML `_blank` 규칙(순수본 재주입) · 라-9 필드·메서드 · java.time·`--db` CI·MyBatis 판(그대로) · 「손 판정」 표(R-23) · 「측정」 표(규칙·고침별 전후) · 「드러난 것」 · 「후보로」
- 규칙 수를 적은 현재형 문서 자리(`grep -n "136" README.md PLAN.md docs/*.md` 가운데 「규칙 136」 뜻인 줄)만 138 로
- 순서(따로 낸다): `bash scripts/mvn.sh -DskipTests verify` rc 0 → `bash scripts/verify.sh` → `bash scripts/verify.sh --corpus`(앞뒤로 다른 세션 알림 — 메모리 `projectshop-priority`·`heavy-one-at-a-time`) → 끝 줄이 `--db` 를 안 부르는 것 확인(부르면 그때 RAM 15GB 확인 뒤 혼자)
- 벽시계(V-56a·`--corpus`)를 이력에

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7(특히 7 — 로거 꼴 훑기가 원본을 읽기만 하나, 결과 폴더에만 쓰나 · 3 — `LoggerShape`·건너뜀 글에 코드 본문이 안 실리나 · 1 — 해시 탭이 fetch 를 안 하나) · 행의 축·강제 지점과 diff · 고침마다 **컴파일이 깨질 길**(끼운 import 가 다른 import 에 져서 빠지는 자리 · static 문맥의 인스턴스 로거 · `String.valueOf` 가 필요한 인자 · 지운 변수의 초기화식 부작용) · `unusedLocal` 이 쓰는 변수를 안 쓴다고 하는 꼴(람다·익명 클래스·메서드 참조·switch) · `defaultCharset` 이 String 아닌 수신자를 잡는 꼴 · 새 판정마다 양성·음성 · 골든 갱신이 §3 목록 안인가 · MD5 가 패딩 경계에서 맞나) → `gate-probe.sh` → `verify.sh`(끝 줄) → push → PR(본문에 행마다 시작 세 줄 표) → CI `gh api` 폴링 → claude[bot] 처분 → **머지 위임 있음** → 끝 보고

## 4. 실행 스텝
스텝마다 끝에 `bash scripts/mvn.sh -q -DskipTests verify` rc 0 → `bash scripts/verify.sh` 초록 → `git commit`(따로) → `task-done`. 아래 「검증」 은 그 앞에 도는 것만 적는다.

### S0 — 설계 문서·행·번들 표(**실행 세션(Opus)이 첫 손으로 한다**)
- 대상: `design/60-bundle76.md`(A 절) · `PLAN.md` 「번들」 표 「번들 76」 행 + 분할표 새 행·상태(B 절) · `PROGRESS.md`(C 절).
- 검증: `bash scripts/doc-lint.sh design/60-bundle76.md` · `bash scripts/verify.sh`(docs 레인) · 커밋 `docs: S0 설계 60 — 번들 76`. push 안 함.

### 5-66 — xxe Cyberneko 인자 조건 · SHAPE 글
- 대상: `SecurityAst`(상수 하나·조건 한 줄) · `ResourceFix`(SHAPE) · `SecurityAstTest`(+1) · `CleanupTest`(글) · 골든 `cleanup/steps.json`(§3-1).
- 순서: 시험 RED(`nekoImportPlainArgsStillFlagged`) → GREEN → SHAPE 글 → `-Dgolden.update=true -Dtest=CleanupTest` → diff 가 그 글뿐.
- 검증: `-Dtest='SecurityAstTest,CheckRulesTest,CleanupTest,SelfCheckTest'` 초록(`SecurityAst` 29) · 커밋 `fix: 5-66 xxe Cyberneko 제외는 인자가 HTML 파서일 때만 · SHAPE 글에 여러 줄 close`.
- 의존: S0.

### 5-67 — catchThrowable 수신자
- 대상: `JavaDefects`(`rethrows`·`receiver`) · 픽스처 `PosDefects`·`NegDefects`(끝) · `JavaDefectsTest`(+1·줄 목록) · 골든 `check/java.json`(§3-2).
- 순서: 픽스처·시험 RED → GREEN → `-Dgolden.update=true -Dtest=JavaRulesTest` → diff 가 새 줄 하나.
- 검증: `-Dtest='JavaDefectsTest,JavaRulesTest,SelfCheckTest'` 초록(`JavaDefects` 26) · 커밋 `fix: 5-67 catchThrowable — 수신자로만 쓴 매개변수(t.getMessage())는 다시 던지기가 아니다`.
- 의존: S0.

### 5-68 — 꼴 A' 변형 둘 판정만
- 대상: `ResourceAst`(`Twr`·`Planner.plainClose`·`closeRun`·`tail`·`wrap`·`of`) · `ResourceFix.apply`(세 줄) · 픽스처 `PosResource`(끝)·`NegResource`(`:102` 한 줄 교체 + `:97` javadoc)·`cleanup/resource/Pos.java`(끝 `formHoist`) · `ResourceAstTest`(+2 · `wrapNotTwr` 둘 뺌) · `CleanupTest`(`resourceStage` 목록 +1 · 새 `quietCloseIsSkipped`) · 골든 `check/java.json`·`cleanup/resource-Pos.java.txt`·`cleanup/steps.json`(§3-3).
- 순서: 픽스처·시험 RED → GREEN → 골든 갱신(`JavaRulesTest`·`CleanupTest`) → diff 가 PosResource76 줄 + `formHoist` 원문 + skips 하나.
- 검증: `-Dtest='ResourceAstTest,JavaRulesTest,CleanupTest,CleanupRoutesTest,SelfCheckTest'` 초록(`Planner` 23 · `ResourceFix` 12 · `valueAndResourceCompile` 초록) · 커밋 `feat: 5-68 closeQuietly 로 닫는 꼴·close 뒤 몸 안 변수를 쓰는 꼴도 tryWithResources 판정(고침은 손으로)`.
- 의존: 5-66(SHAPE 글 — 같은 골든).

### 5-69a — `java.unusedLocal` 판정
- 대상: `rules.yaml` · 새 `LocalsAst` · `JavaRules.taint`(for) · 픽스처 `PosLocals`·`NegLocals` · 새 `LocalsAstTest` · `CheckRulesTest`(137) · 골든 `check/java.json`(§3-4).
- 순서: 픽스처·시험 RED → GREEN → `-Dgolden.update=true -Dtest=JavaRulesTest` → diff 가 새 픽스처 줄 + 기존 양성 세 줄.
- 검증: `-Dtest='LocalsAstTest,JavaRulesTest,CheckRulesTest,CheckStoreTest,JavaDefectsTest,ValueAstTest,ResourceAstTest,SecurityAstTest,CleanupTest,SelfCheckTest,PureRulesJsonTest'` 초록 · `pure-rules.json`·`fixtures.json`·`self-check.txt`·`golden/cleanup/*` diff 0 · 커밋 `feat: 5-69a java.unusedLocal — 선언만 하고 이름이 다시 안 나오는 지역 변수`.
- 의존: S0.

### 5-69b — 지역 변수 고침 + 단계 `local`
- 대상: 새 `LocalFix` · `Cleanup`(상수 `LOCAL`·`MAY_APPEAR`·`RULES`·`TOUCHED_MAY_VANISH`·`clean` 한 줄·`worse:374` 한 줄·javadoc) · `CleanupRoutes`(javadoc) · `CodeCommands`(글 둘) · `README.md`(한 줄) · `code_cleanup_ext.js`(라벨) · 픽스처 `cleanup/local/Pos.java` · 시험 `CleanupTest`(+2)·`CleanupRoutesTest`·`SmokeHtmlUnitTest` · 골든 `cleanup/local-Pos.java.txt`·`steps.json`(§3-5).
- 순서: 픽스처 + `localStage`·`localKeepsSuppressed` RED → GREEN → `-Dgolden.update=true -Dtest=CleanupTest` → diff 가 새 항목뿐.
- 검증: `-Dtest='CleanupTest,CleanupOrderTest,CleanupRunnerTest,CleanupRoutesTest,SmokeHtmlUnitTest,CodeCliTest,ReadmeCommandsTest,SelfCheckTest,ToolsFolderTest'` 초록(`Cleanup` 29 · `worse` 복잡도 35 그대로 — `self-check.txt` diff 0 · `cleanupChangesNothing` 0) · `bash scripts/doc-lint.sh README.md` · 커밋 `feat: 5-69b 코드 정리 — 안 쓰는 지역 변수 선언 줄 삭제(초기화식이 순수할 때만)`.
- 의존: 5-69a · 5-68(같은 골든 `steps.json`).

### 5-70a — `java.defaultCharset` 판정
- 대상: `rules.yaml` · `DeclaredTypes`(`find`·`isByteArray`) · `ValueAst`(상수·`apply`·셋) · 픽스처 `PosCharset`·`NegCharset` · `ValueAstTest`(+2) · `CheckRulesTest`(138) · 골든 `check/java.json`(§3-6).
- 순서: 픽스처·시험 RED → GREEN → `-Dgolden.update=true -Dtest=JavaRulesTest` → diff 읽기(§3-6).
- 검증: `-Dtest='ValueAstTest,JavaRulesTest,CheckRulesTest,JavaDefectsTest,ResourceAstTest,SecurityAstTest,CleanupTest,SelfCheckTest'` 초록(`ValueAst` 25 · `DeclaredTypes` 10 · 기존 골든은 새 규칙 줄 추가뿐) · 커밋 `feat: 5-70a java.defaultCharset — 문자셋 없는 getBytes()·new String(byte[])`.
- 의존: 5-69a(같은 yaml·골든).

### 5-70b — 문자셋 고침
- 대상: `RuleSet`(생성자·`encoding()`) · 새 `Imports` · `ValueFix` · `Cleanup`(`RULES`·`OPTIONAL`·`VALUE_RULES`·VALUE 줄) · 픽스처 `cleanup/charset/Pos.java`·`Clash.java`(최상위 클래스 package-private) · 시험 `CleanupTest`(+2)·`CleanupRoutesTest`·`SmokeHtmlUnitTest` · 골든 `cleanup/charset-*.txt`·`steps.json`·`check/self-check.txt`(`RuleSet.load` 29 → 28 한 줄)(§3-7).
- 순서: `Imports` 로 옮기기(시험 불변 초록 — `objectsImportInserted`·기존 value 골든 diff 0) → `RuleSet` + `self-check.txt` 한 줄 → 픽스처 + `charsetStage` RED → GREEN → 골든.
- 검증: `-Dtest='CleanupTest,CleanupRunnerTest,CleanupRoutesTest,SmokeHtmlUnitTest,ValueAstTest,CheckRulesTest,JavaRulesTest,SelfCheckTest'` 초록(`ValueFix` 12 · `value-*.txt` 불변 · `self-check.txt` diff 가 그 한 줄) · 커밋 `feat: 5-70b 코드 정리 — getBytes()·new String(byte[]) 에 프로필 문자셋(기본 끔)`.
- 의존: 5-70a · 5-69b.

### 5-71a — 로거 꼴 모으기
- 대상: 새 `LoggerShape` · 새 `LoggerShapeTest`(§3-8). 배선은 5-71b.
- 순서: `LoggerShapeTest` RED → GREEN.
- 검증: `-Dtest='LoggerShapeTest,SelfCheckTest,ArchitectureTest'` 초록 · 골든 diff 0 · 커밋 `feat: 5-71a 같은 소스 루트의 로거 선언 꼴 모으기(LoggerShape)`.
- 의존: 5-70b.

### 5-71b — `System.out` → 로거
- 대상: 새 `LogFix` · `Cleanup`(필드·생성자 둘·상수·`RULES`·`OPTIONAL`·`clean` 한 줄) · `CleanupRunner`(두 줄) · `CleanupCorpusTest`(한 줄) · `code_cleanup_ext.js`(라벨) · 픽스처 `cleanup/log/` 다섯 · 시험 `CleanupTest`(+2 · `compile` 겹침)·`CleanupRunnerTest`(+1)·`CleanupRoutesTest`·`SmokeHtmlUnitTest` · 골든 `cleanup/log-*.txt` 여섯·`steps.json`(§3-9).
- 순서: 픽스처 + `logStage`·`loggerInserted` RED → GREEN → 골든 → javac.
- 검증: `-Dtest='CleanupTest,CleanupRunnerTest,CleanupRoutesTest,SmokeHtmlUnitTest,LoggerShapeTest,SelfCheckTest,ToolsFolderTest'` 초록(`Cleanup` 29 · `cleanupChangesNothing` 0 · 원본 불변 단언) · 커밋 `feat: 5-71b 코드 정리 — System.out/err 를 로거 호출로, 로거가 없으면 같은 소스 루트의 꼴을 끼운다(기본 끔)`.
- 의존: 5-71a · 5-70a(`ValueAst.stringy`).

### 5-72 — 마크업 고침 둘
- 대상: 새 `MarkupFix` · `Cleanup`(상수·`RULES`·`clean` 한 줄) · `code_cleanup_ext.js`(라벨) · 픽스처 `cleanup/markup/` 셋 · 시험 `CleanupTest`(+1)·`CleanupRoutesTest`·`SmokeHtmlUnitTest` · 골든 `cleanup/markup-*.txt` 셋·`steps.json`(§3-10).
- 순서: 픽스처 + `markupStage` RED → GREEN → 골든.
- 검증: `-Dtest='CleanupTest,CleanupRunnerTest,CleanupRoutesTest,SmokeHtmlUnitTest,CheckRulesTest,PureRulesJsonTest,SelfCheckTest,ToolsFolderTest'` 초록(`pure-rules.json`·`fixtures.json` diff 0 · `cleanupChangesNothing` 0) · 커밋 `feat: 5-72 코드 정리 — html 에 lang · target=_blank 인 a 에 rel`.
- 의존: 5-71b(같은 `Cleanup`·골든).

### 4-31 — 「해시」 탭
- 대상: `tools/dev_tools.html` · `tools/index.html`(카드 글) · `scripts/puppeteer/corpus-js.js` · `JsCorpusTest`(+1)(§3-11).
- 순서: `corpus-js.js` + `hashMatchesJdk` 먼저(RED — 함수 없음) → 탭·함수 → GREEN.
- 검증: `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest=JsCorpusTest -Djunit.timeout=10m` 초록(`pageErrors` 0 · `js-summary.json` diff 0) · `-Dtest='ToolsFolderTest,ToolsA11yTest,SelfCheckTest,SmokeHtmlUnitTest,BackendToolsTest'` 초록 · 커밋 `feat: 4-31 개발자 도구 해시 탭 — MD5·SHA·HMAC 을 브라우저에서`.
- 의존: S0(독립 — 차례만).

### 4-32 — 「문자 코드·진법」 탭
- 대상: 4-31 과 같은 넷(§3-12).
- 순서·검증: 4-31 과 같은 꼴(`charcodeAndRadixMatchJdk`) · 커밋 `feat: 4-32 개발자 도구 문자 코드·진법 탭`.
- 의존: 4-31(같은 파일).

### 0-75 — SOURCES Tibero 줄
- 대상: `bundle/SOURCES.md:15`(§3-13).
- 검증: `bash scripts/bundle-fetch.sh --check`(네트워크 없이, 지문 불변) · `bash scripts/doc-lint.sh bundle/SOURCES.md` · 커밋 `docs: 0-75 SOURCES Tibero 줄 — Central 에 있으나 현장 판 jar 를 넣는다`.
- 의존: 없음.

### V-56a — corpus 골든 재채점
- 대상: `golden/corpus/*`(§3-14).
- 검증: 갱신 명령 초록 · diff 가 §3-14 목록 안 · 건수 전후·벽시계를 이력에 · 커밋 `test: V-56a 재채점 — unusedLocal·defaultCharset·로거·마크업·꼴 A' 변형`.
- 의존: 5-72 · 4-32.

### R-23 — 손 판정
- 대상: compare ㉖ 「손 판정」 표(§3-15) + 고침(≤ 2, 음성 먼저 RED).
- 검증: 표 + 합 줄 · 고침이 있으면 V-56a 다시 · 커밋 `docs: R-23 손 판정 — unusedLocal·defaultCharset·로거 꼴`(고침은 따로 `fix:`).
- 의존: V-56a.

### V-56 — compare ㉖ · 정적 분석 · `--corpus`
- 대상: `design/compare-2026-10-28.md` ㉖ · 규칙 수 문서 자리(§3-16).
- 검증: `mvn.sh -DskipTests verify` rc 0 → `verify.sh` → `verify.sh --corpus` — 셋 초록(따로) · 끝 줄이 `--db` 를 안 부름 · `doc-lint` · 커밋 `docs: V-56 재측정 — compare ㉖ + 정적 분석 + --corpus`.
- 의존: R-23.

### 마무리
§3 「마무리」 그대로. 머지 위임 있음.

## 5. 금지 사항
- `pure/`·portfolio·`core/gen`·`core/meta`·`core/analyze`·`core/vcs` · `web/`(`CleanupRoutes` javadoc 한 줄 밖) · `cli/`(`CodeCommands` 글 둘 밖) · `tools/` 의 `code_cleanup.html`·`common.js`·`common.css`·`dev_tools_ext.js`
- **정규식 규칙을 더하거나 `regex`·`globs`·`message`·`flags` 를 바꾸지 않는다**(`pure-rules.json` diff 0) · 규칙 id 는 둘만 더한다(138) · 기존 규칙 severity 불변
- `Cleanup` — 메서드 추가 금지(29 유지, 새 단계는 `editStage` + 람다) · `worse` 는 `:374` `mayAppear` 한 줄 밖 변경 금지(삼항 수 그대로) · `commit`·`targets`·`deleteStage`·`orderStage`·`eolStage`·`Edits` 논리 변경 금지 · `Edits` 에 새 기능 금지 · 단계 차례는 §「설계가 정한 것」 그대로
- `JavaRules` — 메서드 추가 금지(30), `taint` 에 for 한 줄만
- `ValueFix` — 기존 넷(refEquals·weekYear·literalFirst·bigDecimal)의 바꾼 글 불변 · 건너뜀 글 불변 · import 는 **한 번의 `e.replace`** 로만
- `LogFix` — `System.out/err` 를 **지우는** 고침 금지 · `main` 안을 바꾸지 않는다(사용자 결정) · 로거 꼴을 지어내지 않는다(베낄 꼴이 없으면 건너뜀 — slf4j 를 기본으로 넣지 않는다) · `e.printStackTrace()` 를 건드리지 않는다 · `cur` 에 없는 줄(`tb-ignore`)을 안 고친다 · catch 몸·초기화 블록 안을 안 바꾼다
- `LocalFix` — 초기화식이 §3-4 `pure` 가 아닌 선언을 지우지 않는다(단순 이름 포함) · 필드를 지우지 않는다 · `cur` 에 없는 줄을 안 지운다
- `ResourceAst` — `released`·`handedOver`·`creation`·`borrowed` 변경 금지 · 바깥 29 · `Planner` ≤ 25 · try 로 감싼 close 는 여전히 꼴 A' 가 아니다 · `ResourceFix.wrap`·`edit`·`alone` 불변(변형 둘의 자동 고침 금지 — 사용자 결정)
- `LoggerShape.finder` — 원본 폴더를 **읽기만**(쓰기·삭제 없음, 규칙 7) · 읽은 글을 로그·H2·응답에 안 남긴다(규칙 3 — 든 것은 선언 꼴 한 줄)
- 해시·문자 코드 탭 — `fetch`·XHR·외부 스크립트 금지(규칙 1) · 서버 라우트 금지 · `innerHTML` 금지 · 입력·키·결과를 localStorage 에 안 남긴다 · 기존 탭 16 의 순서·id·함수 불변
- 픽스처 — 기존 줄 삭제·삽입 금지(끝 덧붙임 · `NegResource.java:102` 한 줄 교체와 `:97` javadoc 만 예외) · `Neg*` 에 지적이 나는 꼴 금지 · **픽스처에 `// tb-ignore` 를 더하지 않는다**(`CheckStoreTest:80`) · `compile()` 로 컴파일하는 cleanup 픽스처는 `public` 최상위 클래스·JDK 밖 형식 금지(log 픽스처의 slf4j 만 예외)
- 골든 — §3 이 적은 변화 밖 금지 · `-Dgolden.update` 는 그 시험에만 · `self-check.txt` 는 5-70b 의 `RuleSet.load` 한 줄(29 → 28)만 — 새 키 금지(우리 코드를 고친다) · `self-cleanup.txt` 새 사유 금지 · `fixtures.json`·`pure-rules.json` diff 0
- 새 의존성 금지 · `--db`·`--full` 은 `verify.sh` 끝 줄이 부를 때만 · 다른 저장소(portfolio·satchel) 안 고침

## 6. 최종 검증
- `bash scripts/mvn.sh -DskipTests verify` rc 0 · `bash scripts/verify.sh` · `bash scripts/verify.sh --corpus` 초록(따로) · 끝 줄이 더 부르는 레인 없음 · `gate-probe.sh` 전부 빨개짐
- 수: 규칙 138(`CheckRulesTest`) · security 45 · `Cleanup.RULES` 19 · `OPTIONAL` 4 · 화면 체크박스 19·켜진 15(`SmokeHtmlUnitTest`) · `Cleanup` 29 · `JavaRules` 30 · `SecurityAst` 29 · `JavaDefects` 26 · `ValueAst` 25 · `ValueFix` 12 · `Planner` 23
- 골든: `git diff --stat origin/main -- src/test/resources/golden` 이 `check/java.json`·`check/self-check.txt`(`RuleSet.load` 한 줄)·`cleanup/*`·`corpus/check-*`·`corpus/cleanup-*`·`corpus/vcs-egov.json`·(`corpus/security-*` 총 건수) 안 · `check/fixtures.json`·`check/pure-rules.json`·`corpus/js-summary.json`·`check/self-cleanup.txt` diff 0
- 수동(한 번, 이력에 한 줄): `run.bat` → 코드 정리 화면 — 체크박스 19, 기본 끔 넷에 「(기본 끔)」 · jeecg 폴더 미리보기에서 단계 「안 쓰는 변수」·「마크업」 이 보인다(쓰지 않음) → 개발자 도구 「해시」 탭에 `abc` → MD5 `900150983cd24fb0d6963f7d28e17f72` · SHA-256 `ba7816bf…` · 「문자 코드·진법」 탭에 `가` → `U+AC00`
- compare ㉖ 표 + 뺀 것 + 「드러난 것」 + 「후보로」 · 이력에 건수·벽시계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- 5-68 에서 `NegResource` 에 지적이 남 · `resource-Pos.java.txt` 의 기존 메서드가 바뀜
- 5-69a 에서 `java.json` 의 기존 픽스처 새 줄이 실측 셋(`PosOrder:15`·`PosOrderEdge:27`·`PosServiceImpl:14`)과 다름 · `java/Neg*` 에 지적 · `self-check.txt`·`fixtures.json`·`pure-rules.json`·`golden/cleanup/*` 가 바뀜(설계 실측과 판정 코드가 다르다는 뜻)
- 5-69b 에서 기존 cleanup 골든(`*.txt`·`steps.json` 의 기존 항목)이 바뀜 · `valueAndResourceCompile` 빨강 · `CleanupOrderTest` 빨강 · `self-check.txt` 의 `worse` 줄이 바뀜
- 5-70a 에서 `java/Neg*` 히트가 5 를 넘거나 우리 코드 히트가 10 을 넘음
- 5-70b 에서 기존 `value-*.txt` 또는 `objectsImportInserted` 가 바뀜(import 옮기기가 순수 이동이 아니었다) · `RuleSet` 생성자 인자가 7 을 넘어야 함 · `self-check.txt` 가 `RuleSet.load` 한 줄 밖에서 바뀜
- 5-71a 에서 `ArchitectureTest` 빨강 · `LoggerShape.finder` 가 `LocalFiles` 의 허용 범위 밖이라고 거절당함
- 5-71b 에서 `loggerInserted` 의 고친 글이 javac 에서 안 됨 · 끼운 필드에 `java.declarationOrder` 가 새로 섬 · `cleanupChangesNothing` 이 0 이 아님 · 원본 파일이 바뀜(규칙 7)
- 5-72 에서 우리 화면(`tools/*.html`)에 지적이 떠서 `cleanupChangesNothing` 이 0 이 아님
- 4-31·4-32 에서 `JsCorpusTest` 가 표본·node·puppeteer 부재로 못 돎(환경) · `pageErrors` 가 비지 않음 · MD5 가 JDK 와 한 건이라도 다름(고치기 둘까지)
- V-56a 에서 §3-14 목록 밖 골든 변화 · `security-juliet*`·`security-benchmark` **채점 칸** 변화 · `CleanupCompileCorpusTest` javac 오류 · `CleanupCorpusTest` 등급 A(예외·구문 깨짐·지적 늘어남)
- R-23 에서 고침 셋째가 필요 · `unusedLocal`·`defaultCharset` 오탐이 본 줄의 10% 를 넘음
- 메서드 수 상한(§6)을 넘어야 함 · `pmd:check`·SpotBugs 새 짝 · `VerifyLanesTest` 빨강 · `verify.sh` 끝 줄이 `--db` 를 부름
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① `java.defaultCharset` 이 기존 픽스처·우리 코드에 걸리는 수(판정 코드가 서야 센다 — grep 으로는 `java/PosTaint.java` 한 자리 · 우리 코드 0. 5-70a 의 골든 diff 가 센다, 중단선 §7) ② 표본 건수 전부와 `security.piiLog` 로 `log` 단계가 되돌려지는 파일 수(V-56a·R-23) ③ `JsCorpusTest` 가 도는 환경(표본·node·puppeteer — 번들 75 까지 같은 PC 에서 돌았다)
- 설계가 미리 잰 것(불확실 아님): `java.unusedLocal` 은 JavaParser 3.26.3 로 정의를 돌려 우리 코드 0 · 기존 픽스처 양성 셋 · 음성 0 · cleanup 픽스처 0 · slf4j 는 시험 클래스패스에 있고 javac 에 `java.class.path` 를 주는 전례가 있다
- **사용자만 정할 수 있는 것** — 없음(결정 열은 2026-11-09 에 다 받았다)
