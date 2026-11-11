# 설계 64 — 번들 80: 잔손 셋 · 코드 정리 B 둘(printStackTrace·star import) · OpenRewrite 라 새 규칙 다섯 + 고침 · piiCard 가리기 (Fable 설계 · Opus 실행, 2026-11-11)

## 맥락
사용자 2026-11-11 결정(메모리 `bundle80-decisions`) 넷을 한 번들로 — ① 잔손 5-83·5-84 + compare ㉙ 후보 `a11y.thScope` `flags: [i]` ② 코드 정리 B 남은 둘(compare ④(b) △ — `common.printStackTrace` → 로거 `error(msg, e)` · `java.starImport` → 쓰는 클래스만 import) ③ OpenRewrite 라-8 NeedBraces · 라-14 InterruptedException `interrupt()` · 라-7 default 끝으로 — 새 지적 규칙 + 고침 ④ 라-16 `security.piiCard` 가리기 · 라-10 빈 세미콜론·불필요 블록. 뺀 것: 보류 넷(4-10·2-7·2-8·5-7) · 조건부 셋(5-14·5-15·6-9) · 5-31. 직전 번들 79(설계 63, PR #122 머지 `456dcf5`, 닫기 `5fe4123` 은 `work/2026-11-11b` 로컬). 레인 — `core/check`·`resources/check`·`resources/tools`·`golden/corpus` 는 java+corpus · `core/fs/LocalFiles` 는 java+corpus(db 아님 — `LANE_DB_SKIP` 에 `core/check`, `core/fs` 는 db 목록에 없음) · `web/`·`fixtures/**`·`golden/cleanup`·`golden/check` 는 java → 끝에 `--corpus` 한 번(`--db` 안 돈다).

**실측(2026-11-11, `5fe4123` 트리)**
- **`Cleanup` 길이 1000 = 상한**(`:27`~`:1026`) · 메서드 30 = 상한. 줄 하나·메서드 하나도 못 더한다. 안쪽 클래스 `Edits`(`:971-1025`, 54줄, javadoc 포함)는 `Cleanup.Edits` 로 밖(LocalFix 1·LogFix 2·MarkupFix 1·ResourceFix 1·ValueFix 1·CleanupTest 3)에서 쓴다 — 최상위로 빼면 946 줄. `metric.classMethods` 는 `t.getMethods().size()`(직접 메서드만) 라 안쪽 클래스 메서드는 안 센다 → `Edits` 를 빼도 30 그대로
- **`JavaRules` 메서드 30 = 상한**(길이 613). `taint(cu, c)`(`:230-254`) 가 `SecurityAst·JavaDefects·ValueAst·TxAst·ResourceAst·LocalsAst.apply(cu, on.keySet())` 를 for 로 줄줄이 돈다 — 새 규칙 클래스는 **for 하나 더**(메서드 없음). `JavaDefects` 메서드 26 — 다섯을 더하면 넘친다 → **새 클래스 `JavaShape`**
- 자기 코드 `self-check.txt`(`golden/check/`, 130줄)에 `Cleanup` 복잡도 여섯(`worse(6)` 36 포함)·`JavaRules` 셋. 새 규칙이 자기 코드에 걸리면 줄이 는다 — 자기 코드 실측: 중괄호 없는 if·for 0 · `;;` 0 · 빈 블록 0 · 별표 import 0 · `printStackTrace()` 0 · `default` 가운데 0 · `catch (InterruptedException e)` 단일형 둘(`vcs/Cli.java:97` interrupt 있음 · `SpotBugsRun.java:137` 은 union + instanceof) · **`job/JobManager.java:77` `catch (InterruptedException | JobCancelledException e)` 는 interrupt 없음** → union 은 규칙 밖으로 둔다(§3-6). `cleanupChangesNothing`(자기 코드 정리 0 변경)도 같은 이유로 불변
- **`Cleanup.worse` after 루프(`:455-466`)** — `mayAppear && ka >= kb` 를 `touchedNew` 보다 먼저 봐서, 같은 발췌 둘 가운데 고친 줄 위 것이 **뒤에** 오면 kb 예산을 앞 것이 써 버려 `continue`(toa 에 안 듦), **앞에** 오면 toa 로 가 「생김」 — 줄 차례에 갈린다(5-83 ①). DELETE 는 `touchedNew` 가 빈 집합(지우기만 하면 새 줄 번호가 없다 — `Edits.touchedNew`), LOCAL 은 unusedImport 가 import 줄이라 고친 줄이 아님 → 실물에서 안 갈리지만 판정을 굳힌다
- **`ThScopeFix.quietAttrs:214`** `Character.isLetter` — 한글 뒤 `<` 도 태그를 연다(5-83 ②). 태그 이름은 ASCII 로 시작한다. `a<b` 는 `<b` 가 태그 시작이라 음성이 못 된다(리뷰 목록과 다름 — `<1`·`<한`·`값<기준 "x"` 셋만 음성)
- **`EncodingFix.WRITABLE_NAMES:19`** `Set.of(UTF8, UTF8_BOM, MS949)` 와 **`LocalFiles.encode:422-426`** `switch` 가 같은 셋을 따로 적는다(5-84 ①). `encode` 는 package-private static(`core.fs`), `checkEncodable`·`writeResult` 가 쓴다 → 원천을 `LocalFiles` 의 `Map<String, Charset>` 하나로
- **`CleanupCorpusTest:70-79`** 파일마다 `stages`(TreeSet) 를 모은다 — xml·properties 단언 자리(5-84 ②)
- **`a11y.thScope`** `flags` 없음 · `AbsentRule.when` 은 `def.flags()` 로 `when` 도 컴파일(`:31`) → `flags: [i]` 하나로 `present`·`when` 둘 다 대소문자 무시. `pure-rules.json` 에 thScope 없음(absent 는 순수본 밖) → diff 0. `ThScopeFix.TOKEN`·`SCOPED` 는 이미 `(?i)`. **표본 대문자 `<TH` 0 파일**(전 폴더·전 확장자, `.ftl`·`.vm` 포함) — 올려도 표본 수 불변
- **`LogFix`**(318줄, 메서드 15) — `place`(여러 줄·인자 없음·main·catch 안·초기화·JUL) → `find`(Logger·Log 필드 → Lombok) → 없으면 `insertable`(LoggerShape 베끼기) → `usable`(JUL·System.Logger·그림자·static 문맥) → `call` 이 `name.debug|error(x)`. `printStackTrace` 는 이 길에 **catch 안이 필수**라 `place` 의 catch 거절만 뒤집으면 된다. `java.logAndThrow` 는 `e.printStackTrace()` 를 이미 로그로 센다(`JavaDefects.logs:274`) → 로거로 바꿔도 판정 불변 · `java.lostCause` 는 NameExpr `e` 가 남아 불변 · `java.swallow` 는 ServiceImpl catch 에 「throw·로거·processException 없음」 — printStackTrace 만 있던 catch 가 로거 호출을 얻으면 **사라진다** → `worse` 「사라짐」 되돌림 → 그 줄은 고친 줄(touched)이라 `TOUCHED_MAY_VANISH` 로 푼다
- **표본 `printStackTrace()`**(juliet·benchmark 뺀 합) 748 줄 · 253 파일 · 그중 로거 필드 있는 파일 114. jeecg 551 줄/177 파일(로거 83) · zheng 88 · jeesite 67 · roller 9 · shopizer 8 · egov 묶음 5 · jspwiki 3 · commons-lang 1. 수신자 = catch 변수 이름 100%(e 731 · ex 32 · e1 13 …). 인자 있는 꼴(`printStackTrace(response.getWriter())` 797, 거의 benchmark)은 규칙 정규식 `\(\s*\)` 밖
- **표본 별표 import**(juliet 25,882 뺌) 219 줄/171 파일 — egov-msa 84/73 · zheng 53/41 · jeecg 32/18 · roller 15/14(+정적 2) · jspwiki 13/8 · ruoyi-classic 8 · jeesite 5 · struts 4 · shopizer 4 · egov-portal 정적 2. **cleanup corpus 무리(egov 넷·commons 셋·jspwiki·roller·egov-react)엔 jspwiki 13 · roller 15 · 정적 4 뿐** — 레거시(jeecg·zheng·egov-msa)는 R-27 CLI 로. 패키지 분포(JDK 51 · 자기 소스 62 · 제3자 106)는 §3-10 끝
- **표본 중괄호 없음**(benchmark 뺌) — 한 줄 `if (…) stmt;` 175(struts 59 · jeecg 40 · jspwiki 37 · egov 묶음 ~20 · roller 6 · egov-msa 9) · 한 줄 `else stmt;` 6 · 여러 줄(머리 줄 끝 `)`, 다음 줄에 `{` 없음) 530(jeecg 183 · struts 96 · jeesite 56 · shopizer 38 · egov-enterprise 33 · egov-portal 30 · egov-homepage 26 · jspwiki 22 · roller 6 · egov 4) · `else` 홀로 줄 70 · `else if` 사슬 17 · 중괄호 없는 `do` 0. 예: `jeecg/…/core/util/BrowserUtils.java:138`(여러 줄) · `jspwiki/…/attachment/AttachmentServlet.java:139`(한 줄) · `jeecg/…/core/util/StringUtil.java:1399`(else-if 사슬) · `egov-portal/…/filter/HTMLTagFilterRequestWrapper.java:97`(else 홀로 줄)
- **표본 `catch (InterruptedException`** 65(commons-io 12 · roller 8 · egov 7 · commons-lang 6 · jeesite 6 · jspwiki 4 · ruoyi 셋 6 · struts 3 · egov-msa 2 · jeecg 1 · egov 작은 셋 3) · 6줄 안 `interrupt()` 있음 18 → 후보 ≈ 47. union(`IOException | InterruptedException`) 2(commons-lang `BackgroundInitializer:347` · roller `RomeFeedFetcher:94`) — 규칙 밖
- **표본 `default` 가 마지막 아님 1**(`jspwiki/…/tags/LinkTag.java:359` — `default:` 뒤 바로 `case ANCHOR:` 로 떨어지는 꼴, 고침 불가) · `default ->` 4(struts). 규칙은 두고 고침은 표본 0 을 받아들인다
- **표본 빈 문장** `;;` 24(jeecg 10 · egov-msa 4 · shopizer 4 · jeesite 3 · commons-lang 2 · jspwiki 1) · 홀로 `;` 줄 13 가운데 진짜 빈 문장 3(jeecg `ZipUtil:54`·`SystemController:650`·`RoleController:137`, 나머지 10 은 enum 상수 끝) · **불필요 블록**: juliet 916(cleanup 무리 밖), 나머지 12 는 초기화 블록·double-brace 오탐 → AST 로 「BlockStmt 의 직접 자식 BlockStmt」 만 보면 cleanup 무리에서 0
- **표본 카드번호 꼴** 14 줄/11 파일 가운데 진짜 6 — shopizer `BeanStreamPayment.java:261·268·286·554`(시험 PAN) · egov·egov-prev `EgovPiiMaskUtil.java:161`(javadoc — `skipComments` 라 지적 밖). 나머지 날짜·소수·URL 오탐은 규칙 정규식 `(?<![\d-])\d{4}-\d{4}-\d{4}-\d{4}(?![\d-])` 이 `-` 경계로 이미 거른다(표본 측정은 느슨한 식). test 경로 0
- **수 단언 자리**: `Cleanup.RULES` 22(`CleanupRoutesTest.rulesListsFixable:109` 22 · `SmokeHtmlUnitTest:1885` 체크박스 22·켜진 18) · 규칙 139(`CheckRulesTest:169`) · 정규식 63(`PureRulesJsonTest:64`, 불변 — 새 규칙 전부 `kind: java`) · `PureCodeCheckTest:49` 32(DIRS 에 java 없음 — 불변) · `JavaRulesTest` 골든 `check/java.json`(`fixtures/check/java/Pos*·Neg*`) · `CheckRulesTest.fixturesGolden` 은 `java/` 를 건너뜀 → `check/fixtures.json` 불변
- 화면 `code_cleanup_ext.js:9` `STAGE` 라벨 열 개 · `:210` `changed[s.stage]` 는 `java.bigDecimalDouble` 만 「값 바뀜」 표시 · `:216·222` 가 단계 글·건너뜀 글. 스모크 `codeCleanupPreviewApply:1866-1890` 은 `javaimport/Pos.java`·`debugger/pos.jsp`·MS949 `Ms.java` 셋 → 「파일 3 · 바뀜 3」. **`javaimport/Pos.java` 의 `import java.util.*;`** 는 5-34a 가 「star 는 그대로」 로 둔 것 — 단일 import 가 `List` 를 이미 대고 `java.util.*` 로 푸는 이름이 0 → §3-9 가 **지운다**(골든 `javaimport-Pos.java.txt`·`steps.json` 바뀜, 스모크 수 불변)
- `rules.yaml` `flags: [i]` 꼴(`:78·104·233`) · `kind: java` 규칙은 `RuleSet:119` 가 `parsedDefs` 로 · `ref:` 는 `java.floatEquals:996` 꼴 `'p3c AvoidDoubleOrFloatEqualCompareRule'`
- `Cleanup` 생성자 넷째 `Function<String, Optional<LoggerShape>> loggers`(`CleanupRunner:69·123` 이 `LoggerShape.finder(files, root)`) — 별표 풀기의 「자기 소스 패키지 형식 목록」 도 같은 꼴의 함수가 필요(§3-9)
- PMD(`config/pmd/ruleset.xml` errorprone·bestpractices, 기준선 `exclude-from-failure.properties` 249줄) — 번들 79 에서 `AvoidFieldNameMatchingMethodName`(상수 `WRITABLE`↔`writable`) 이 막았다. 새 이름은 상수와 메서드가 대소문자만 다르지 않게 적었다(§3)
- **표본 `<th` 소문자**(참고) egov 625 파일/4,039 태그 — 대문자 0

**범위 밖** — `pure/` · `LocalFiles.read`(판별) · `AbsentRule`·`RegexRule`·`FileRule`·`MarkupFix`·`BlankRelRule`·`OrderFix`·`ValueFix`·`LocalFix`·`ResourceFix` · `JavaDefects`(새 규칙은 `JavaShape`) · 라-10 `UnnecessaryParentheses` · 라-7 `FallThrough`·`MinimumSwitchCases` 지적 · `catch (A | InterruptedException e)` union(`JobManager` 꼴 — 「드러난 것」) · `printStackTrace(인자)` 꼴·catch 변수가 아닌 수신자(`t.printStackTrace()` 가 catch 밖) · 정적 별표 import(`import static x.*`) · 제3자·못 푸는 별표 패키지 · `default` 가 떨어지는 switch(고침은 건너뜀) · 중첩 같은 줄 `if (a) if (b) x();` · 5-31·2-17·보류·조건부 행

## 사용자 결정(2026-11-11)
1. 범위 넷(메모리 `bundle80-decisions`) 그대로 · 설계 Fable · 실행 Opus · **머지 위임 있음**
2. (가)/(나) 로 물어 받은 것(2026-11-11, 전부 (가)):
   - **printStackTrace 고침 꼴** — `로거.error(e.getMessage(), e)`(catch 변수 이름 그대로) · 로거가 없으면 sysout 과 같은 길로 `LoggerShape` 를 베껴 끼운다(베낄 꼴도 없으면 건너뜀)
   - **기본 끔(`OPTIONAL`)** — 동작·값이 바뀌는 셋만: `common.printStackTrace`·`java.interruptedIgnored`·`security.piiCard`. `java.needBraces`·`emptyStatement`·`unneededBlock`·`defaultLast`·`starImport` 는 기본 켬 → `OPTIONAL` 4 → 7 · 체크박스 30·켜진 23
   - **piiCard 마스크** — 끝 4자리 남김 `****-****-****-3456`
   - **`a11y.thScope` `flags: [i]`** — 표본 0 이어도 올린다(5-85)

설계가 정한 것(전부 [고정]):
- **행 차례** `S0`→`0-81`→`5-83`→`5-84`→`5-85`→`5-88`→`5-89`→`5-90`→`5-91`→`5-92`→`5-86`→`5-87`→`V-60a`→`R-27`→`V-60`→마무리. 가지 `work/2026-11-12`(번들 79 닫기 `5fe4123` 위). 고침 행은 서로 독립(파일이 안 겹친다) — 막히면 그 행만 `wip/` 로 빼고 다음 행. 별표 import(5-87)가 가장 갈릴 자리라 맨 뒤
- **0-81 `Edits` 분리** — `Cleanup.Edits` 를 최상위 `Edits.java`(package-private, 몸 그대로)로. `Cleanup` 1000 → 946. 이 번들의 `Cleanup` 변경(단계 다섯·상수·`RULES`·`OPTIONAL`·`TOUCHED_MAY_VANISH`·`worse` 재배치) 합이 +20 줄 안 — 상한까지 34 줄 여유. 메서드는 안 더한다(30)
- **새 규칙 다섯은 `JavaShape`**(`kind: java`, `java.needBraces`·`java.emptyStatement`·`java.unneededBlock`·`java.defaultLast`·`java.interruptedIgnored`) — `JavaRules.taint` 에 for 하나. 139 → 144
- **새 고침 여덟 → `Cleanup.RULES` 22 → 30**: `common.printStackTrace`(LOG 단계 — `LogFix` 확장) · `java.starImport`(새 단계 `imports` — `StarImportFix`) · `java.needBraces`·`java.emptyStatement`·`java.unneededBlock`(새 단계 `syntax` — `SyntaxFix`) · `java.defaultLast`(새 단계 `switch` — `SwitchFix`) · `java.interruptedIgnored`(새 단계 `interrupt` — `InterruptFix`) · `security.piiCard`(새 단계 `pii` — `PiiFix`). 단계 차례(`clean`): delete → marker → markup → value → local → log → resource → **syntax → switch → interrupt → pii → imports** → order → eol → encoding(새 다섯은 resource 뒤·order 앞 — order 는 줄 재조립이라 맨 뒤 셋 앞에 둔다)
- 고침마다 **먼저 지적(규칙)이 있어야 단계가 돈다**(`targets(st.cur, ids)`) — 지적 행 5-88 이 고침 행 앞
- 고침은 **줄 단위 `Edits`**(지우기·바꾸기, 줄마다 한 번). 한 단계 안에서 두 고침이 같은 줄을 건드리면 **앞 것이 이기고 뒤 것은 건너뜀**(사유 「같은 줄에 다른 고침」) — `SyntaxFix` 가 줄마다 prefix·suffix·치환을 모아 한 번 `replace`
- 모든 Java 고침은 `Cleanup.javaUnsafe`(파싱 실패·`\r`·유니코드 이스케이프) 뒤에만 · 람다·안쪽 클래스 몸(`barrier`)은 문맥 밖 — 각 Fix 가 `JavaDefects.path·barrier` 꼴을 자기 안에 둔다(JavaDefects 는 private — 가시성을 열지 않는다, 7-22 전례)
- 표본 기대는 cleanup 무리(egov 넷·commons 셋·jspwiki·roller·egov-react)로만 V-60a 가 재고, 레거시(jeecg·zheng·jeesite·egov-msa·shopizer·struts)는 R-27 이 CLI 로

## 1. 목표와 범위
- 한 문장: 코드 정리가 `printStackTrace`·별표 import·중괄호 없는 문장·빈 문장·불필요 블록·가운데 `default`·`interrupt()` 없는 `InterruptedException` catch·카드번호 리터럴을 고치고(새 지적 규칙 다섯 포함), 번들 79 잔손 다섯을 닫는다.
- 완료 시 체감: 코드 정리 화면 체크박스 30(켜진 23 — 기본 끔 일곱) · `e.printStackTrace();` 가 그 클래스 로거의 `error(…, e)` 로 · `import java.util.*;` 가 쓰는 클래스 import 로(안 쓰면 지움) · `if (x) return;` 이 `if (x) { return; }` 로 · `x();;` 의 `;` 하나 삭제 · 가운데 `default:` 블록이 끝으로 · `catch (InterruptedException e) {` 다음 줄에 `Thread.currentThread().interrupt();` · `"1234-5678-9012-3456"` 이 마스크로 · 코드 검사에 규칙 다섯이 새로 보인다 · 화면 단계 글에 「문장 꼴」「default 끝으로」「interrupt() 복원」「개인정보 가리기」「import 풀기」.
- In scope: `core/check` — `Cleanup`(단계 다섯·상수·`RULES`·`OPTIONAL`·`TOUCHED_MAY_VANISH`·`worse`) · 새 `Edits`·`JavaShape`·`SyntaxFix`·`SwitchFix`·`InterruptFix`·`PiiFix`·`StarImportFix`·`PackageTypes` · `LogFix`(printStackTrace) · `ThScopeFix`(`asciiLetter`) · `EncodingFix`(`WRITABLE_NAMES` 삭제) · `JavaRules.taint`(for 하나) · `CleanupRunner`(생성자 인자) · `core/fs/LocalFiles`(`CHARSETS`·`WRITABLE`) · `resources/check/rules.yaml`(규칙 다섯 + thScope `flags`) · `tools/code_cleanup_ext.js`(`STAGE` 다섯) · 시험·픽스처·골든(행마다) · `design/compare-2026-10-28.md` ㉙ 처분·㉚ · `PLAN.md`·`PROGRESS.md`
- Out of scope: 「범위 밖」 전부 · 새 의존성 · `JavaDefects`·`JavaRules` 의 다른 자리 · `Cleanup` 의 `clean` 단계 추가 다섯 줄과 상수·목록·`worse` 한 자리 밖

## 2. 현재 구조 요약
「맥락」 의 실측이 요약이다. 덧붙이는 함정:
- **자바를 고친 행마다 `bash scripts/mvn.sh -q -DskipTests verify` 를 먼저** — 빠른 verify 는 PMD·SpotBugs 를 안 본다. PMD 단골: `AvoidLiteralsInIfCondition`(글자·수 리터럴은 상수로) · `AvoidFieldNameMatchingMethodName` · `UseLocaleWithCaseConversions` · `LiteralsFirstInComparisons` · 시험 `AvoidDuplicateLiterals`(같은 글 넷 이상은 상수) · `AvoidCatchingGenericException`. SpotBugs: `Class.forName` 은 `initialize=false` + 플랫폼 로더(§3-9) — 지적되면 `exclude-from-failure` 가 아니라 코드를 바꾼다
- `Edits.replace(line, text)` 는 **첫 호출만** 든다(두 번째는 무시, 오류 없음) — 같은 줄을 두 번 고치려면 Fix 안에서 합쳐 한 번. `delete` 는 덮어쓴다
- `Edits.touchedNew` 는 바꾼 줄(치환 글의 `\n` 수만큼)만 — 지운 줄은 새 번호가 없다. 끼운 줄은 고친 줄로 센다(5-62c)
- `commit` → `worse` 의 비교: 대상 규칙·`file.*`·parseError·`metric.*` 는 비교 밖 · 고친 줄 위 지적은 규칙 이름 다중집합(사라짐은 `TOUCHED_MAY_VANISH`) · 그 밖은 (규칙, 발췌) 다중집합. 고침이 지적을 **새로** 내면 되돌림 — 이것이 안전망이라 Fix 는 「확실한 꼴만」 고치고 나머지는 사유로 건너뛴다
- `CleanupTest.fixturesGolden:52` 는 `fixtures/cleanup/**`(order 빼고) 전부를 `cleanup(null)`(`Cleanup.RULES`, 로거 finder 없음)로 돌려 `cleanup/<dir>-<파일>.txt` + `steps.json`. 새 픽스처 폴더는 골든 셋을 새로 만든다(`-Dgolden.update=true -Dtest=CleanupTest`). `steps.json` `row()` 키 불변
- `fixtures/check/java/Pos*·Neg*` 는 `JavaRulesTest` 골든 `check/java.json` — 새 `PosShape.java`·`NegShape.java` 는 다른 java 규칙(`declarationOrder`·`metric.*`·`unusedImport` 등)에 안 걸리게 쓴다(걸리면 골든에 그 줄이 같이 든다 — 허용하되 의도된 것만)
- `SelfCheckTest.mainMatchesBaseline` 은 자기 `src/main` 에 **모든 규칙**을 돈다 → 새 규칙이 자기 코드에 걸리면 `self-check.txt` 줄이 는다(실측 0 — §3-6 union 제외가 그 근거) · `cleanupChangesNothing` 은 자기 코드 정리 변경 0 — 새 고침이 자기 코드를 고치면 빨강(실측 0)
- `CleanupCompileCorpusTest`(corpus 레인) 는 commons 셋을 정리한 뒤 javac — 중괄호·블록·switch·import 고침의 컴파일 심판. V-60a 가 돈다
- 화면 JS(`resources/tools`)는 corpus 레인 — `STAGE` 라벨은 각 고침 행이 한 키씩 더한다(빠른 verify 는 tools 스모크만)
- Bash 도구 heredoc 은 `\` 를 벗긴다 → 정규식·자바 고침은 Edit 도구
- 레인: `core/check`·`core/fs`·`resources/check`·`resources/tools`·`golden/corpus` = java+corpus · `fixtures/**`·`web/`·`golden/cleanup`·`golden/check` = java · `design/**` = 없음 · `PLAN.md`·`PROGRESS.md` = docs

## 3. 설계 결정 — 전부 [고정]

### 0-81 — `Edits` 분리(§3-0)
- 새 `src/main/java/kr/ejg/toolbox/core/check/Edits.java`:
```java
package kr.ejg.toolbox.core.check;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** 줄 지우기·바꾸기 모음(줄 번호 1부터, 원래 글 기준) — 결과와 고친 줄(옛·새 번호)을 낸다. 0-81 — Cleanup 안에서 밖으로(길이 상한) */
final class Edits {
    … Cleanup.java:973-1024 몸 그대로(생성자 `Edits(String text)` 포함) …
}
```
- `Cleanup.java:971-1025` 삭제 · `Cleanup` 안의 `Edits` 참조는 이름 그대로(같은 패키지) · 밖 여섯 파일의 `Cleanup.Edits` → `Edits`(LocalFix 1 · LogFix 2 · MarkupFix 1 · ResourceFix 1 · ValueFix 1 · `CleanupTest` 3). `Cleanup` 의 `import java.util.HashMap;`(`:8`) 은 `Edits` 만 썼다(실측 — 다른 자리 0) → 같이 지운다(남기면 `java.unusedImport` 가 `self-check.txt` 에 든다). `TreeSet`·`ArrayList`·`List`·`Map`·`Set` 은 다른 자리가 쓴다 — 그대로
- 검증: `-Dtest='CleanupTest,SelfCheckTest,ArchitectureTest'` 초록 · `golden/cleanup`·`self-check.txt`·`self-cleanup.txt` diff 0 · `Cleanup` 길이 946(`:27`~`:972`)

### 5-83 — 잔손 셋(§3-1)
- ① `Cleanup.worse` after 루프(`:455-466`) → 고친 줄 판정을 먼저:
```java
        for (Finding f : after) {
            String key = f.rule() + "\u0000" + f.excerpt();
            if (skipInCompare(f, targets)) {
                continue;
            }
            if (touchedNew.contains(f.line())) {
                toa.merge(f.rule(), 1, Integer::sum);
            } else if (!mayAppear.contains(f.rule()) || ka.getOrDefault(key, 0) < kb.getOrDefault(key, 0)) {
                // 5-76·5-81·5-83 — 생겨도 되는 규칙은 before 수까지만 비교에 남긴다(그대로면 통과·사라지면 「사라짐」), 더 생긴 것은 새 것. 고친 줄 위 것은 차례와 무관하게 위에서 센다
                ka.merge(key, 1, Integer::sum);
            }
        }
```
  (12 줄 → 12 줄. 복잡도는 연산자 수가 같아 36 — 37 이 되면 `self-check.txt` 그 줄만 갱신(의도된 변화)). `CleanupTest.worseRevertsStage` 끝에 먼저 더해 RED(지금은 첫 단언이 null):
```java
        // 5-83 ① — 고친 줄 위의 생겨도-되는 지적은 줄 차례와 무관하게 고친 줄 쪽(toa)으로 — before 수 예산은 고친 줄 밖 것만 쓴다
        List<Finding> ab = List.of(f(6, "common.unusedIgnore", "tb-ignore x.y"), f(9, "common.unusedIgnore", "tb-ignore x.y"));
        List<Finding> ba = List.of(ab.get(1), ab.get(0));
        assertEquals("다른 지적이 생김: common.unusedIgnore", Cleanup.worse(Cleanup.DELETE, Set.of("tsx.debugger"), db, ab, Set.of(2), Set.of(9)));
        assertEquals("다른 지적이 생김: common.unusedIgnore", Cleanup.worse(Cleanup.DELETE, Set.of("tsx.debugger"), db, ba, Set.of(2), Set.of(9)), "줄 차례 무관");
```
- ② `ThScopeFix.quietAttrs:214` `Character.isLetter(sb.charAt(i + 1))` → `asciiLetter(sb.charAt(i + 1))`:
```java
    /** 5-83 — 태그 이름은 ASCII 글자로 시작한다. 본문 「값<기준 "x"」 의 한글 뒤 < 는 태그가 아니다(td 를 숨겨 행이 th 만으로 보이는 길을 막는다) */
    private static boolean asciiLetter(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }
```
  `ThScopeFixTest.quietAttrs` 에 먼저(RED — 지금은 `"x"` 가 공백이 된다):
```java
        for (String s : List.of("값<기준 \"x\"<td>", "<1 \"q\"", "<한 \"q\"")) {
            assertEquals(s, ThScopeFix.quietAttrs(s), "ASCII 글자가 아닌 것 뒤 < 는 태그가 아니다: " + s);
        }
        assertEquals("<b \"  \">", ThScopeFix.quietAttrs("<b \"xx\">"), "<b 는 태그 시작");
```
  (메서드 11 → 12. 클래스 javadoc 「속성 값 안 글도 안 본다(quietAttrs)」 뒤에 「— 태그는 ASCII 글자·/ 로 시작」)
- ③ `CheckRulesTest.thScopeWhenMatchesFixToken:293` 목록에 `"<th\n"`, `"<th\n>"` 둘 추가(javadoc 에 「· 줄 끝 `\n`(줄마다 vs 글 전체)」). 5-85 가 대문자 표본을 더한다
- 골든: `cleanup/*` diff 0 · `self-check.txt` 는 `worse` 복잡도 줄만(바뀌면)

### 5-84 — 쓸 인코딩 셋 한 곳 · xml·properties 단계 단언(§3-2)
- `LocalFiles`(`core/fs`) — `MS949` 상수 뒤:
```java
    /** 5-84 — 쓸 수 있는 인코딩 이름 → Charset. encode 와 EncodingFix.writable 이 같은 셋을 본다 */
    private static final Map<String, Charset> CHARSETS = Map.of(UTF8, StandardCharsets.UTF_8, UTF8_BOM, StandardCharsets.UTF_8, MS949,
            Charset.forName(MS949));
    public static final Set<String> WRITABLE = Set.copyOf(CHARSETS.keySet());
```
  `encode:422-426` 의 `switch` →
```java
        Charset cs = CHARSETS.get(enc);
        if (cs == null) {
            throw new Refused(400, "encoding 은 " + String.join("·", new TreeSet<>(WRITABLE)));
        }
```
  (메시지 글이 「UTF-8-BOM·UTF-8·MS949」 → 「MS949·UTF-8·UTF-8-BOM」 으로 바뀐다 — 그 글을 단언한 시험이 있으면 같이. 없으면 그대로. `import java.util.Map; Set; TreeSet` 확인)
- `EncodingFix.WRITABLE_NAMES:19` 삭제 · `writable` → `LocalFiles.WRITABLE.contains(target(profileEncoding))` · `refusal:54` 의 글 「(UTF-8·UTF-8-BOM·MS949)」 그대로
- `LocalFilesTest` 새 시험(먼저 써도 바로 GREEN — 단언 행, `Ruling:`):
```java
    /** 5-84 — WRITABLE 과 encode 가 한 셋: 목록 것은 다 써지고 밖 것(EUC-KR 이름 그대로·windows-1252)은 400 */
    @Test
    void writableNamesMatchEncode() {
        assertEquals(Set.of("UTF-8", "UTF-8-BOM", "MS949"), LocalFiles.WRITABLE);
        for (String enc : LocalFiles.WRITABLE) {
            assertTrue(LocalFiles.encode("가\n", enc, "LF").length > 0, enc);
        }
        for (String enc : List.of("EUC-KR", "windows-1252", "", "utf-8")) {
            assertEquals(400, assertThrows(LocalFiles.Refused.class, () -> LocalFiles.encode("가\n", enc, "LF")).status(), enc);
        }
    }
```
- `CleanupCorpusTest.allSources` — `stages` 를 모은 뒤(`:72` 다음):
```java
                    if (rel.endsWith(".xml") || rel.endsWith(".properties")) { // 5-84 ② — 그 확장자엔 encoding·eol 만(javadoc 의 글을 단언으로)
                        Set<String> extra = new TreeSet<>(stages);
                        r.skips().forEach(k -> extra.add(k.stage()));
                        extra.removeAll(Set.of(Cleanup.ENCODING, Cleanup.EOL));
                        if (!extra.isEmpty()) {
                            a.add(rel + " xml·properties 에 다른 단계 " + extra);
                        }
                    }
```
  (corpus 레인 — V-60a 가 돈다. `Ruling:` 시험 코드만)
- 골든 불변

### 5-85 — `a11y.thScope` `flags: [i]`(§3-3) — 표본 0 이어도 올린다(사용자 (가))
- `rules.yaml:734` 블록에 `flags: [i]`(`globs` 다음 줄) · 주석 「5-85 — 대문자 `<TH>`·`SCOPE=` 도(flags 는 when 에도 걸린다 — AbsentRule.when)」
- `CheckRulesTest.thScopeWhenMatchesFixToken` 목록에 `"<TH>"`, `"<TH "`, `"<Th\n"`, `"<THEAD>"` · javadoc 「소문자만 — when 은 flags 없음」 → 「대소문자 둘 다(5-85 flags i · TOKEN 은 (?i))」 · 새 단언(같은 시험 끝):
```java
        RuleSet rs = RuleSet.load(profile(new Profile.CodeCheck(Map.of("a11y", true), null, null)), null);
        assertEquals(List.of("a11y.thScope"), rs.apply(new Source("x.jsp", "<TABLE><TR><TH>a</TH></TR></TABLE>\n", "UTF-8", "LF", null)).stream()
                .filter(f -> f.rule().startsWith("a11y.th")).map(Finding::rule).toList(), "대문자 TH 도 잡는다");
        assertTrue(rs.apply(new Source("y.jsp", "<TABLE><TR><TH SCOPE=\"col\">a</TH></TR></TABLE>\n", "UTF-8", "LF", null)).stream()
                .noneMatch(f -> f.rule().equals("a11y.thScope")), "대문자 SCOPE= 도 있음으로");
```
- 골든: `fixtures.json`·`pure-rules.json` diff 0(표본 대문자 0 · absent 는 순수본 밖) · corpus `check-*.json` 불변 기대(대문자 0)

### 5-88 — `JavaShape` 지적 다섯(§3-4)
- 새 `JavaShape.java`(package-private final, `JavaDefects` 꼴 — `apply(cu, on)` 가 `List<Taint.Hit>`, `add` 가 같은 노드·규칙 한 건, 줄·규칙 정렬):
```java
    static final String NEED_BRACES = "java.needBraces";
    static final String EMPTY_STATEMENT = "java.emptyStatement";
    static final String UNNEEDED_BLOCK = "java.unneededBlock";
    static final String DEFAULT_LAST = "java.defaultLast";
    static final String INTERRUPTED = "java.interruptedIgnored";
    private static final String INTERRUPTED_EXCEPTION = "InterruptedException";
    private static final String INTERRUPT = "interrupt";
```
  - `needBraces` — `IfStmt`: then 이 `BlockStmt` 아님 · else 가 있고 `BlockStmt`·`IfStmt` 둘 다 아님 / `ForStmt`·`ForEachStmt`·`WhileStmt`·`DoStmt`: body 가 `BlockStmt` 아님. 지적 노드 = 그 문장(then·else 둘 다여도 한 건)
  - `emptyStatement` — `EmptyStmt` 가운데 부모가 `BlockStmt` 또는 `SwitchEntry` 인 것(제어문 몸 `if (x);`·`while (x);` 는 다른 결함 — 밖)
  - `unneededBlock` — `BlockStmt` 가운데 부모가 `BlockStmt` 인 것(초기화 블록·람다·switch 의 `case 1: { }`·synchronized 는 부모가 다르다 — 밖)
  - `defaultLast` — `SwitchStmt`·`SwitchExpr` 의 entries 에서 `getLabels().isEmpty()`(default) 인 entry 의 차례가 마지막이 아님. 지적 노드 = 그 entry
  - `interruptedIgnored` — `CatchClause` 매개변수 형식이 **union 이 아니고** 단순 이름이 `InterruptedException`, 몸(람다·안쪽 클래스·안쪽 catch 밖)에 이름 `interrupt` 인 `MethodCallExpr` 도 `ThrowStmt` 도 없음. 지적 노드 = 매개변수
  - `path·barrier·catchBarrier·same·simpleType` 은 `JavaDefects` 의 것을 **복사**(private 가시성 유지)
- `JavaRules.taint:251` 뒤에 `for (Taint.Hit h : JavaShape.apply(cu, on.keySet())) { add(c, h.rule(), h.node()); }` · javadoc 에 「+ 문장 꼴 다섯(설계 64, {@link JavaShape})」
- `rules.yaml` `java.floatEquals` 블록 뒤에 다섯(전부 `group: java` · `kind: java` · `globs: ['*.java']`):
  - `java.needBraces` info — message `if·else·for·while·do 몸에 중괄호가 없다` · fix `'몸을 { } 로 감싼다 — 코드 정리가 한다'` · ref `'p3c NeedBraceRule · OpenRewrite NeedBraces'`
  - `java.emptyStatement` info — `빈 문장(;) — 지워도 같다` · fix `'; 를 지운다 — 코드 정리가 한다'` · ref `'OpenRewrite RemoveExtraSemicolons'`
  - `java.unneededBlock` info — `블록 안 홀로 블록 { } — 범위를 안 바꾼다` · fix `'중괄호를 뺀다(선언이 있으면 손으로)'` · ref `'OpenRewrite RemoveUnneededBlock'`
  - `java.defaultLast` warn — `switch 의 default 가 마지막이 아니다` · fix `'default 를 끝으로 — case 가 떨어지면 손으로'` · ref `'checkstyle DefaultComesLast · p3c SwitchStatementRule'`
  - `java.interruptedIgnored` warn — `catch (InterruptedException) 이 interrupt() 를 안 돌려준다 — 중단 신호가 사라진다` · fix `'Thread.currentThread().interrupt() 를 넣거나 다시 던진다'` · ref `'Sonar S2142 · OpenRewrite InterruptedExceptionHandling'`
- `CheckRulesTest:169` 139 → 144(주석에 「설계 64 문장 꼴 다섯」)
- 픽스처 `fixtures/check/java/PosShape.java`·`NegShape.java` + `JavaShapeTest`(`JavaDefectsTest` 꼴 — 줄 단언, `ON` 다섯). `PosShape` 에 들 것(각 줄 단언): 한 줄 `if (a) return;` · 여러 줄 if/else-if/else 중괄호 없음 · `for`·`while`·`do` 몸 중괄호 없음 · `x();;` · 홀로 `;` 줄 · 블록 안 홀로 블록(선언 없음) · 블록 안 홀로 블록(선언 있음 — 지적은 난다) · default 가운데(break 있음·없음 둘) · `catch (InterruptedException e) { log(e); }` · 빈 몸 `{ }`. `NegShape` 에: 중괄호 있는 전부 · `else if` 사슬(중괄호) · `for (;;) { }` · enum 상수 끝 `;` · `case 1: { }` · 초기화 블록 · 람다 몸 · default 마지막 · `catch (InterruptedException e) { Thread.currentThread().interrupt(); }` · `catch (InterruptedException e) { throw new IllegalStateException(e); }` · `catch (IOException | InterruptedException e) { }`(union — 밖) · switch 식 `default ->` 마지막
- 골든 `check/java.json` 갱신(`-Dgolden.update=true -Dtest=JavaRulesTest`) — diff 가 `java/PosShape.java` 줄뿐(NegShape 0). `self-check.txt` diff 0(자기 코드 실측 0)

### 5-89 — `SyntaxFix`(단계 `syntax`)(§3-5)
- `Cleanup`: `public static final String SYNTAX = "syntax";` · `RULES` 에 `"java.needBraces", "java.emptyStatement", "java.unneededBlock"`(`"java.defaultCharset"` 뒤) · `clean` 의 `RESOURCE` 줄 뒤 `editStage(st, SYNTAX, Set.of(JavaShape.NEED_BRACES, JavaShape.EMPTY_STATEMENT, JavaShape.UNNEEDED_BLOCK), (x, t, e, l) -> SyntaxFix.apply(x.text, x.cur, t, parser, e, l));`
- `SyntaxFix.apply(String text, List<Finding> cur, Set<String> targets, JavaSource parser, Edits e, List<Cleanup.Skip> skips)` — `javaUnsafe` → 파싱 → 지적 줄(`cur` 의 세 규칙 줄) 에 해당하는 노드만 고친다. 줄마다 `Line(prefix, suffix, text)` 를 모아 끝에 한 번 `e.replace`(text 가 null 이면 `e.delete`). 두 고침이 같은 줄에 닿으면 **앞 것(줄 차례 → 규칙 차례 needBraces·emptyStatement·unneededBlock)이 이기고** 뒤 것은 `skip(「줄 N: 같은 줄에 다른 고침」)`
- **needBraces** — 몸 B(then·else·loop body)마다:
  - 머리 줄 H: then 은 조건 `)` 끝 줄 · else 는 then 끝과 B 시작 사이 원문에서 마지막 `\belse\b` 가 있는 줄 · while 은 조건 끝 줄 · for 는 init·compare·update 끝 줄 가운데 큰 것 · foreach 는 iterable 끝 줄 · do 는 문장 시작 줄
  - **같은 줄 꼴**(B 시작 줄 == H): 그 줄에서 B 의 범위 `[begin.column, end.column]` 앞에 `{ `, 뒤에 ` }`. 같은 줄의 다른 몸과 범위가 **겹치면**(중첩 `if (a) if (b) x();`) 둘 다 건너뜀(「줄 N: 중첩된 중괄호 없는 문장」) · 안 겹치면(`if (a) x(); else y();`) 열 내림차순으로 둘 다
  - **여러 줄 꼴**(B 시작 줄 > H): H 줄 끝(공백 뗀)이 `)`·`else`·`do` 로 끝나야 한다(`Cleanup.BRACELESS` 와 같은 식 — 복사) — 아니면(뒤에 주석) 건너뜀(「줄 N: 머리 줄 끝에 다른 글」) · B 시작 줄에서 B 앞은 공백뿐·B 끝 줄에서 B 뒤는 공백뿐 — 아니면 건너뜀(「줄 N: 몸 줄에 다른 글」). 고침: H 줄 suffix ` {` · B 끝 줄 suffix `\n` + indent(H) + `}` — 단, 바로 다음 비공백 줄이 **같은 if 의 `else`** 또는 **같은 do 의 `while`** 로 시작하면 그 줄 prefix `} ` 로 대신(`} else if (b) {` 꼴)
  - 한 if 의 then·else 둘 다 중괄호 없음 → 둘 다 위 규칙으로(else 줄은 then 의 닫기 prefix 와 else 몸의 머리 suffix 를 같이 받는다)
- **emptyStatement** — `EmptyStmt` 자리: 그 줄이 공백 + `;` 뿐이면 줄 삭제 · 아니면 그 열의 `;` 한 글자 삭제(같은 줄 여럿은 열 내림차순)
- **unneededBlock** — 블록에 `VariableDeclarationExpr`(직접 자식 `ExpressionStmt`)·`LocalClassDeclarationStmt`·`LocalRecordDeclarationStmt` 가 있으면 건너뜀(「줄 N: 블록 안 선언 — 범위가 바뀐다」) · `{` 줄이 공백 + `{` 뿐 · `}` 줄이 공백 + `}` 뿐이어야(아니면 「줄 N: 블록 줄에 다른 글」) · 둘 삭제 · 안 줄 전부가 `{` 줄 indent + 한 단위(첫 안 줄 indent − `{` 줄 indent)로 시작하면 그만큼 내어쓰기, 아니면 그대로 · `{ }`·`{}` 한 줄이면 그 줄 삭제
- `TOUCHED_MAY_VANISH` 안 더한다(머리 줄은 `)` 로 끝나 지적이 없고, 지운 `{`·`}` 줄엔 `metric.*` 뿐)
- 픽스처 `fixtures/cleanup/syntax/Pos.java` — 위 꼴 전부(고쳐지는 것 + 건너뛰는 것 셋: 머리 끝 주석 · 중첩 같은 줄 · 선언 있는 블록) · 골든 `syntax-Pos.java.txt`·`steps.json` 새 항목 · `CleanupTest.syntaxStage`: 고친 글 단언(`if (a) {\n            return;\n        }` · `} else if (b) {` · `x();` 한 `;` · 블록 내어쓰기) + skips 셋 + Step 수(needBraces n→0 · emptyStatement n→0 · unneededBlock n→1)
- JS `STAGE` 에 `syntax: '문장 꼴(중괄호·빈 문장·블록)'` · 수 단언: `rulesListsFixable` 22 → 25 · 스모크 체크박스 25·켜진 21. 행마다 수(체크박스·켜진): 5-89 25·21 → 5-90 26·22 → 5-91 27·22(끔) → 5-92 28·22(끔) → 5-86 29·22(끔) → 5-87 30·23

### 5-90 — `SwitchFix`(단계 `switch`)(§3-6)
- `Cleanup`: `SWITCH = "switch"` · `RULES` 에 `"java.defaultLast"` · `editStage(st, SWITCH, Set.of(JavaShape.DEFAULT_LAST), (x, t, e, l) -> SwitchFix.apply(x.text, x.cur, parser, e, l));`
- 지적 entry(default, 마지막 아님)마다 — 전부 만족해야 옮긴다, 아니면 사유로 건너뜀:
  - entry 마다 첫 줄의 첫 비공백이 그 entry 의 시작(label)이고 마지막 줄에서 entry 뒤는 공백뿐 · 다음 entry 는 더 뒤 줄에서 시작(「줄 N: case 가 줄을 나눠 쓴다」)
  - default entry 의 문장이 비어 있지 않고(「줄 N: default 가 다음 case 와 묶임」) 마지막 문장이 `BreakStmt`·`ReturnStmt`·`ThrowStmt`·`ContinueStmt`, 또는 entry 가 `->` 꼴(`getType() != STATEMENT_GROUP`)(「줄 N: default 가 다음 case 로 떨어진다」)
  - default 앞 entry(있으면)와 지금 마지막 entry 도 같은 「끝이 탈출」 조건(「줄 N: 앞 case 가 default 로 떨어진다」·「줄 N: 마지막 case 가 떨어진다」)
- 고침: default entry 의 줄(entry 의 주석 `getComment` 시작 줄부터 끝 줄)을 `e.delete` · 마지막 entry 끝 줄을 `e.replace(last, lines[last-1] + "\n" + 옮긴 줄 글(원문 그대로 \n 이음))`
- 픽스처 `fixtures/cleanup/switch/Pos.java` — 옮겨지는 것(break·return·`->`) · 건너뜀 넷(default 끝에 탈출 없음 · 앞 case 떨어짐 · 마지막 case 떨어짐 · `default:` 빈 몸 + case) · 골든 · `CleanupTest.switchStage`
- JS `STAGE` `'switch': 'default 끝으로'`(`switch` 는 JS 예약어 — 따옴표) · 수 단언 +1(26)

### 5-91 — `InterruptFix`(단계 `interrupt`)(§3-7)
- `Cleanup`: `INTERRUPT = "interrupt"` · `RULES` 에 `"java.interruptedIgnored"` · `OPTIONAL` 에 든다(동작 바뀜) · `editStage(st, INTERRUPT, Set.of(JavaShape.INTERRUPTED), (x, t, e, l) -> InterruptFix.apply(x.text, x.cur, parser, e, l));` · `TOUCHED_MAY_VANISH` 에 `INTERRUPT, Set.of("java.emptyCatch")`(빈 catch 가 문장을 얻어 사라지는 것은 의도)
- 지적 catch 마다: 몸 `{` 가 그 줄 끝(뒤는 공백뿐)이어야(아니면 「줄 N: catch 몸이 한 줄」) · `cu` 에 `java.lang.Thread` 가 아닌 `*.Thread` 단일 import 가 있으면 「줄 N: Thread 이름이 다른 형식」 · 고침: `e.replace(brace, lines[brace-1] + "\n" + indent + "Thread.currentThread().interrupt();")` — indent 는 몸의 첫 문장 줄 indent, 몸이 비면 `{` 줄 indent + (그 indent 에 탭이 있으면 `\t`, 아니면 네 칸)
- 픽스처 `fixtures/cleanup/interrupt/Pos.java` — 빈 몸(주석만 포함) · 로그만 · 한 줄 `{}`(건너뜀) · union(지적 밖) · 이미 interrupt(지적 밖) · 골든 · `CleanupTest.interruptStage`(emptyCatch 가 사라져도 되돌리지 않음을 단언 — `skipsOf` 빈 목록)
- JS `STAGE` `interrupt: 'interrupt() 복원(동작 바뀜)'` · 수 단언 +1(27)

### 5-92 — `PiiFix`(단계 `pii`)(§3-8)
- `Cleanup`: `PII = "pii"` · `RULES` 에 `"security.piiCard"` · `OPTIONAL` 에 든다(값 바뀜) · `editStage(st, PII, Set.of(PiiFix.CARD), (x, t, e, l) -> PiiFix.apply(x.text, x.cur, rules, e, l));`
- `PiiFix.apply(String text, List<Finding> cur, RuleSet rules, Edits e, List<Cleanup.Skip> skips)` — 언어 무관(java·jsp·js·xml·properties·sql·html 전부 줄 치환). `rules.defs()` 에서 `security.piiCard` 의 `pattern()` 을 받아 지적 줄마다 `Matcher` 로 모든 일치를 마스크로(끝 4자리 남김 — `"****-****-****-" + m.group().substring(15)`, 상수 `MASK = "****-****-****-"`). 일치 0(원문엔 있고 주석을 뗀 글에만 없을 수는 없다 — 방어) 이면 「줄 N: 줄에서 번호를 못 찾음」. `Edits.replace`
- 마스크는 규칙 정규식에 다시 안 걸린다(`\d{4}` 가 아님) · `security.piiPhone` 등 다른 pii 정규식에도 안 걸린다(숫자 앞에 `-` 가 없다)
- 픽스처 `fixtures/cleanup/pii/Pos.java`·`pos.properties`(둘 다 한 줄 둘·주석 속 번호 하나는 그대로) · 골든 · `CleanupTest.piiStage`
- JS `STAGE` `pii: '개인정보 가리기(값 바뀜)'` · 수 단언 +1(28)

### 5-86 — `common.printStackTrace` → 로거 `error`(§3-9, `LogFix` 확장)
- `Cleanup`: `RULES` 에 `"common.printStackTrace"`(`"common.sysout"` 뒤) · `OPTIONAL` 에 든다(출력 자리 바뀜 — sysout 과 같이) · `:158` `Set.of(LogFix.SYSOUT)` → `Set.of(LogFix.SYSOUT, LogFix.PRINT_STACK)` · `TOUCHED_MAY_VANISH` 에 `LOG, Set.of("java.swallow")`(ServiceImpl 의 printStackTrace 만 있던 catch 가 로거를 얻어 swallow 가 사라지는 것은 의도)
- `LogFix`: `static final String PRINT_STACK = "common.printStackTrace";` · `flagged` 에 두 규칙 줄 · 줄의 호출 = `printCall` 또는 `stackCall`:
```java
    /** 5-86 — e.printStackTrace() — 인자 없음, 수신자는 이름 하나 */
    private static boolean stackCall(MethodCallExpr m) {
        return PRINT_STACK_NAME.equals(m.getNameAsString()) && m.getArguments().isEmpty() && m.getScope().filter(NameExpr.class::isInstance).isPresent();
    }
```
  `place(c, cu)` → `place(c, cu, boolean stack)`: 여러 줄 식(공통) · `!stack && 인자 없음` · main(공통) · `!stack && catch 안` → 「catch 안 출력 …」 / `stack && !ownCatch(c)` → 「catch 매개변수의 printStackTrace 가 아니다 — 손으로」 · `stack && !(c.getParentNode() instanceof ExpressionStmt)` → 「식의 일부」 · 초기화(공통) · JUL(공통). `ownCatch` = 가장 가까운 `CatchClause` 까지 올라가는 길에 `LambdaExpr`·`BodyDeclaration`·다른 `CatchClause` 없음 + 수신자 이름 == 그 catch 매개변수 이름. 복잡도 ≤ 15 를 지킨다(안 되면 `place` 를 둘로)
  `call(c, lg, line)` → stack 이면 `lg.name() + ".error(" + param + ".getMessage(), " + param + ")"`(param = 수신자 이름 = catch 변수). 로거 없을 때 끼우기(`insertable`)·`usable`·`write` 는 그대로 — sysout 과 같은 길(사용자 (가))
  (메서드 15 → 18 · 길이 ≈ 360. 클래스 javadoc 에 5-86 한 줄)
- 픽스처 `fixtures/cleanup/log/Stack.java`(로거 필드 있음 — 고쳐지는 catch 둘(변수 `e`·`ex`) · catch 밖 `t.printStackTrace()`(건너뜀) · 람다 안(건너뜀) · `main` 안(건너뜀) · 식의 일부 `if (x) e.printStackTrace();`?? — 그건 ExpressionStmt 라 고쳐진다, 대신 `return e.printStackTrace()` 는 void 라 불가 → 「식의 일부」 표본은 `Runnable r = e::printStackTrace;`(MethodReference — 호출 아님, 지적 정규식 `\(\s*\)` 도 안 걸림) 대신 **람다 `() -> e.printStackTrace()`**(람다 몸이 식이면 부모가 ExpressionStmt 가 아님) 하나로 둘 다 댄다) · `StackNoLogger.java`(로거 없음 — `fixturesGolden` 에선 finder 가 없어 「로거가 없고 … 베낄 로거 꼴도 없다」 건너뜀, `stackStage` 가 `LoggerShape` 를 주어 끼움 골든 `log-StackNoLogger.inserted.java.txt` — `NoLogger` 전례 `:236-242`) · 골든 · `CleanupTest.stackStage`(+ `NoLogger` 전례 `:236-242` 꼴로 `LoggerShape` 주고 끼움 단언)
- 수 단언 +1(29) · JS `STAGE` `log` 라벨 「로거로(출력 자리 바뀜)」 그대로

### 5-87 — `java.starImport` → 쓰는 클래스만(§3-10, `StarImportFix` + `PackageTypes`)
- `Cleanup`: `IMPORTS = "imports"` · `RULES` 에 `"java.starImport"`(`"java.unusedImport"` 뒤) · 생성자 다섯째 인자 `BiFunction<String, String, Optional<Set<String>>> packageTypes`(rel, 패키지 → 그 소스 루트의 패키지 폴더에 선언된 최상위 형식 이름, 폴더가 없거나 못 읽으면 empty) — 넷짜리 생성자는 `(rel, pkg) -> Optional.empty()` 로 위임 · `editStage(st, IMPORTS, Set.of(StarImportFix.STAR), (x, t, e, l) -> StarImportFix.apply(x.text, x.cur, parser, packageTypes, e, l));` · `CleanupRunner:69·123` 에 `PackageTypes.finder(files, root)` · `CleanupCorpusTest:45` 도
- `PackageTypes.finder(LocalFiles files, String root)` — `LoggerShape.finder` 꼴(소스 루트 `LoggerShape.sourceRoot(rel)` + 패키지 경로로 폴더를 잡고, 그 폴더 **바로 아래** `*.java` 만(`files.list` 결과에서 `rel` 에 `/` 없는 것) `JavaSource` 로 파싱해 `cu.getTypes()` 이름 집합, 한 파일이라도 못 읽으면 empty — 기억(`computeIfAbsent`, 키 `sourceRoot + "\u0000" + pkg`)). 원본은 읽기만
- `StarImportFix.apply(String text, List<Finding> cur, JavaSource parser, BiFunction<…> packageTypes, Edits e, List<Cleanup.Skip> skips)`:
  - `javaUnsafe` → 파싱. 별표 import 목록 S(비정적). 정적 별표는 「줄 N: 정적 별표 import — 손으로」(고침 없음)
  - 파일이 **쓰는 단순 이름** U = `SimpleName` 전부(import·package 선언 밖) + 주석 단어(`JavaRules.usedNames` 꼴 — 복사, private 유지) − 파일이 선언한 형식 이름(중첩 포함)·형식 매개변수 − 단일 import 의 단순 이름 − 같은 패키지 폴더의 형식(`packageTypes(rel, 자기 패키지)` — empty 면 그 몫은 못 빼고 다음으로)
  - 별표 패키지 P 마다 **풀 수 있나**: (a) JDK — `P ∈ BOOT_PACKAGES`(`ModuleLayer.boot().modules()` 의 `getPackages()` 합집합, static 한 번) 이면 이름 n 의 존재 = `Class.forName(P + "." + n, false, ClassLoader.getPlatformClassLoader())` 성공(`ClassNotFoundException`·`LinkageError` → 없음) (b) 자기 소스 — `packageTypes(rel, P)` 가 있으면 그 집합 (c) 둘 다 아니면 **못 푼다** → 파일 전체 건너뜀 「줄 N: 별표 패키지를 못 푼다(JDK·소스 루트 밖): P」(한 패키지라도 못 풀면 다른 별표의 이름 귀속도 못 믿는다)
  - 이름 n ∈ U 가 두 별표 패키지에 다 있으면 「줄 N: 이름이 두 별표 패키지에 — 손으로: n」 로 파일 전체 건너뜀
  - 별표 줄마다: 그 줄에 다른 글이 있으면(두 import 가 한 줄) 「줄 N: import 가 줄을 나눠 쓴다」 · 푼 이름이 0 이면 **줄 삭제**(안 쓰는 별표) · 있으면 `import P.A;\nimport P.B;`(이름 오름차순) 로 치환
  - 틀린 import 를 더하면 `java.unusedImport` 가 새로 나 `worse` 가 되돌린다(안전망) · `java.declarationOrder` 는 import 를 안 본다
- 자기 코드엔 별표 0 → `self-cleanup.txt` 불변. `javaimport/Pos.java` 의 `java.util.*` 는 푼 이름 0 → 삭제(골든 `javaimport-Pos.java.txt` 한 줄 줄고 `steps.json` 항목에 `imports` step — 픽스처 주석 「star … 그대로」 → 「star 는 5-87 이 지운다」)
- 픽스처 `fixtures/cleanup/starimport/Pos.java`(`import java.util.*;` + `List`·`Map.Entry`·`Collections` 사용 → 셋으로 · `import java.io.*;` 안 씀 → 삭제 · `import static java.util.Collections.*;` 건너뜀) · `Third.java`(`import javax.servlet.*;` → 파일 건너뜀) · `CleanupTest.starImportStage` + 자기 소스 표본은 **인라인**(`new Cleanup(…, (rel, pkg) -> pkg.equals("a.b") ? Optional.of(Set.of("Foo")) : Optional.empty())` 로 `import a.b.*;` + `Foo f;` → `import a.b.Foo;` · `Bar` 도 쓰면 — `a.b` 에 없으니 그대로 안 더함) · 골든
- 수 단언 +1(30) · JS `STAGE` `imports: 'import 풀기'`
- **표본 분포**(비정적 별표 219 줄/171 파일/패키지 74, juliet·benchmark 뺌): (a) JDK 51(`java.util` 30 · `java.io` 13 · `java.awt` 2 · `java.util.concurrent`·`java.sql`·`java.net`·`java.lang.annotation`·`javax.crypto`·`javax.management` 1씩) · (b) 자기 소스(같은 `src/main/java` 루트 기준 — egov-msa 는 모듈마다 루트) 62(egov-msa 46 · jeecg 11 · roller 3 · struts 2) · (c) 제3자 106(`org.springframework.web.bind.annotation` 26 · `jakarta.persistence` 19 · `jakarta.validation.constraints` 9 · `jakarta.servlet` 6 · `javax.persistence` 4 · `javax.servlet*` 6 …) — **zheng 의 자기 패키지 16 은 다른 모듈**(루트가 달라 (c) 로 떨어진다 — 설계대로 건너뜀). 파일: JDK 별표만 35(그중 하나뿐 32) · 자기 소스 별표 있는 52 · 자기 소스 별표 둘 이상 9 · a·b·c 섞임 2(jeecg `JeecgListDemoController`·`SuperQueryMainController`) · 두 종류 이상 섞임 13 · **별표가 다른 글과 같은 줄 0**. 기대 — 제3자가 한 줄이라도 든 파일은 통째 건너뜀이라 고쳐지는 파일 ≈ 171 − (c 가 든 파일) — R-27 이 센다. 예: JDK 하나뿐 `jeecg/…/core/util/IpConfigMac.java:2`(`java.net`) · 자기 소스 `struts/core/…/config/AbstractBeanSelectionProvider.java:25`(`org.apache.struts2.inject`) · 자기 소스 셋 + JDK 둘 `jeecg/…/system/controller/core/SystemController.java:12·19·27·46·51`

### V-60a — corpus 골든 재채점(§3-11)
- 순서: `bash scripts/mvn.sh -q -DskipTests verify` rc 0 → 여유 RAM 15GB(아래면 사용자에게) → `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='CheckCorpusTest,LegacyCorpusTest,CleanupCorpusTest,CleanupCompileCorpusTest' -Djunit.timeout=10m -Dgolden.update=true` → `git diff --stat src/test/resources/golden/corpus`
- 기대(이것만): `check-*.json`·`legacy-*` — 새 규칙 다섯 수(needBraces 큰 수 · emptyStatement 작음 · unneededBlock ≈ 0 · defaultLast jspwiki 1 · interruptedIgnored ≈ 20) · `cleanup-*.json` — `steps` 새 키 `syntax`·`interrupt`·`imports`(jspwiki·roller)·`pii`(0 일 수 있음 — shopizer 는 무리 밖)·`switch`(0) · `log` ↑(printStackTrace — egov 묶음 5·roller 9·jspwiki 3·commons-lang 1) · `changed` ↑ · `skips` 새 키(사유 상수 그대로) · 다른 단계 수 불변 · `vcs-*`·`security-*`·`db-*`·`js-summary`·`analyze-*` 불변 · 등급 A 0 · `CleanupCompileCorpusTest` 초록(commons 셋이 고친 뒤 컴파일)
- 중단선: 등급 A · javac 실패(commons) · `syntax 다른 지적이 생김·사라짐`·`imports 다른 지적이 생김` 이 표본의 5% 넘음 · `markup`·`value`·`local`·`resource`·`order`·`eol`·`encoding` 수 변화 · `cleanup-egov.json` `log` 가 5 넘게 늘지 않음(표본 실측 2+1+1+1)
- 건수 전후·벽시계를 이력에

### R-27 — 손 판정(§3-12)
- CLI(`toolbox.bat cleanup <폴더> --rules <id> --json [--write --out <scratch>]`, 프로필 example, 본 뒤 지움):
  - `common.printStackTrace` — jeecg(551 줄/177 파일, 로거 83): changed 파일 수·건너뜀 사유 분포 · `--write` 결과 20 줄 열어 `log.error(…, e)` 꼴·catch 변수 이름 그대로(`ex`·`e1` 포함) · zheng 88 · roller 9
  - `java.starImport` — egov-msa(84)·zheng(53)·jeecg(32): 치환·삭제·건너뜀 사유 분포 · 고친 파일 10 열어 더한 import 가 실제 쓰이는 이름인지 · **commons 셋은 V-60a 의 javac 가 심판** · JDK 별표만인 파일 둘·자기 소스 별표 둘 꼭
  - `java.needBraces` — jeecg(183+40)·struts(96+59)·jeesite(56): changed·건너뜀 분포 · 20 곳 diff 열어 `{`·`}` 자리·else-if 사슬·들여쓰기 · 줄 수 증가 = 닫는 `}` 줄 수
  - `java.emptyStatement` — jeecg 10+3·egov-msa 4·shopizer 4 · `java.unneededBlock` — 표본 0 기대(`juliet` 은 안 돈다)
  - `java.defaultLast` — jspwiki `LinkTag.java:359` 가 「떨어진다」 사유로 건너뜀(changed 0)
  - `java.interruptedIgnored` — commons-io 12·roller 8·egov 7·jeesite 6: changed·건너뜀(한 줄 `{}`) · 10 곳 열어 들여쓰기·자리 · `java.emptyCatch` 되돌림 0
  - `security.piiCard` — shopizer `BeanStreamPayment.java` 4 줄 마스크 · 다른 지적 0
  - 회귀 — `cleanup <jeecg|shopizer|zheng> --rules java.unusedLocal` 20·14·3 그대로 · `cleanup <egov> --rules a11y.thScope --write` 472·758·2,380 그대로(5-83 ② 가 줄을 안 바꿨나)
- 오탐·깨진 고침이면 음성 픽스처(또는 인라인) 먼저 RED → 고침 ≤ 2 → V-60a 다시. 표는 compare ㉚ 「손 판정」, `파일:줄` 만

### V-60 — compare ㉚ · 정적 분석 · `--corpus`(§3-13)
- `design/compare-2026-10-28.md` — ㉙ 「후보로」 끝에 「처분(설계 64)」 블록(S0 가 쓴다 — D 절) · 끝에 `## ㉚ 재측정 — 번들 80(2026-11-12)`: 「설계 64. 잔손 다섯 · 고침 여덟 · 규칙 다섯」 · 「손 판정」 표(R-27) · 「측정」 표(규칙 139 → 144 · `RULES` 22 → 30 · 고침마다 표본 전후 · corpus steps·skips 전후) · ④ (a) 표의 라-7·8·10·14·16 행 「우리 현재」 칸 갱신 · (b) 표 starImport·printStackTrace 「△」 → 「○(번들 80)」 · 「골든」 · 「드러난 것」 · 「후보로」(union catch · `printStackTrace(인자)` · 제3자 별표 · default 떨어짐 지적 등)
- 순서(따로 낸다): `bash scripts/mvn.sh -DskipTests verify` rc 0 → `bash scripts/verify.sh` → `bash scripts/verify.sh --corpus`(여유 RAM · 다른 무거운 작업 없이) → 끝 줄이 더 부르는 레인 없음(db 변경 없음)
- 벽시계(V-60a·`--corpus`)를 이력에

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1(`Class.forName` 이 플랫폼 로더·initialize=false 인가 — 코드를 실행하는 길이 없나)·3(새 골든·사유 글에 코드 본문이 안 드나 — 사유는 「줄 N: 상수」 꼴)·7 · 행의 축·강제 지점과 diff · **고침마다 「확실한 꼴만」 인가 — 중괄호 끼우기가 dangling else·주석 줄·같은 줄 중첩에서 의미를 바꾸는 길 · switch 옮기기의 떨어짐 조건 셋 · 별표 풀기의 같은 패키지 우선·두 별표 겹침·못 푸는 패키지 · interrupt 끼우기의 Allman `{` 줄 · printStackTrace 수신자 == catch 매개변수 · 마스크가 정규식에 다시 안 걸리나** · 새 규칙마다 양성·음성 · 골든 갱신이 §3 목록 안인가 · `TOUCHED_MAY_VANISH` 둘(LOG swallow · INTERRUPT emptyCatch)이 넓지 않나) → `gate-probe.sh` → `verify.sh`(끝 줄) → push → PR(본문에 행마다 시작 세 줄 표) → CI `gh api` 폴링 → claude[bot] 처분 → **머지 위임 있음** → 끝 보고(사람이 할 것 — 현장 프로필에서 기본 끔 셋을 켤지 · 화면 체크박스 30 눈 확인)

## 4. 실행 스텝
스텝마다 끝에 `bash scripts/mvn.sh -q -DskipTests verify` rc 0(자바를 고친 행) → `bash scripts/verify.sh` 초록 → `git commit`(따로) → `task-done`. 아래 「검증」 은 그 앞에 도는 것만. 커밋 제목 `<종류>: <행> <무엇>`.

### S0 — 설계 문서·행·번들 표(**실행 세션(Opus)이 첫 손으로 한다**)
- 대상: `design/64-bundle80.md`(A 절) · `PLAN.md` 「번들」 표 「번들 80」 행 + 분할표 5-83·5-84 갱신·새 행(B 절) · `PROGRESS.md`(C 절) · `design/compare-2026-10-28.md` ㉙ 끝 「처분」 블록(D 절).
- 검증: `bash scripts/doc-lint.sh design/64-bundle80.md` · `bash scripts/verify.sh`(docs 레인) · 커밋 `docs: S0 설계 64 — 번들 80`. push 안 함.

### 0-81 — `Edits` 분리
- 대상: 새 `core/check/Edits.java` · `Cleanup.java`(`:971-1025` 삭제) · `LocalFix`·`LogFix`·`MarkupFix`·`ResourceFix`·`ValueFix`·`CleanupTest`(`Cleanup.Edits` → `Edits`).
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,SelfCheckTest,ArchitectureTest'` 초록 · `git diff --stat src/test/resources/golden` 0 · `Cleanup` 길이 946. 커밋 `chore: 0-81 Edits 를 Cleanup 밖으로 — 길이 상한`. (`Ruling:` 순수 이동 — 시험 추가 없음)
- 의존: S0.

### 5-83 — 잔손 셋
- 대상: `Cleanup.worse`(§3-1 ①) · `ThScopeFix.asciiLetter`(②) · `CheckRulesTest.thScopeWhenMatchesFixToken`(③) · `CleanupTest.worseRevertsStage` · `ThScopeFixTest.quietAttrs`.
- 순서: 단언 셋 먼저 → RED(①·②) → 고침 → GREEN.
- 검증: `-Dtest='CleanupTest,ThScopeFixTest,CheckRulesTest,SelfCheckTest'` 초록 · `golden/cleanup` diff 0 · `self-check.txt` 는 `worse` 줄만(바뀌면). 커밋 `fix: 5-83 되돌림 판정 줄 차례 무관 · quietAttrs ASCII 태그 · 줄 끝 <th 표본`.
- 의존: 0-81.

### 5-84 — 쓸 인코딩 셋 한 곳 · xml·properties 단계 단언
- 대상: `core/fs/LocalFiles`(`CHARSETS`·`WRITABLE`·`encode`) · `EncodingFix`(`WRITABLE_NAMES` 삭제·`writable`) · `LocalFilesTest.writableNamesMatchEncode` · `CleanupCorpusTest`(단언 블록).
- 검증: `-Dtest='LocalFilesTest,CleanupTest,CleanupRoutesTest,SelfCheckTest,VerifyLanesTest'` 초록 · 골든 diff 0. 커밋 `fix: 5-84 쓸 인코딩 셋 한 곳(LocalFiles.WRITABLE) · corpus xml·properties 단계 단언`.
- 의존: 5-83.

### 5-85 — `a11y.thScope` `flags: [i]`
- 대상: `rules.yaml`(한 줄) · `CheckRulesTest`(표본 넷 + 단언 둘).
- 검증: `-Dtest='CheckRulesTest,ThScopeFixTest,PureRulesJsonTest,CleanupTest'` 초록 · `fixtures.json`·`pure-rules.json` diff 0. 커밋 `feat: 5-85 a11y.thScope 대소문자 무시`.
- 의존: 5-84.

### 5-88 — `JavaShape` 지적 다섯
- 대상: 새 `JavaShape.java` · `JavaRules.taint`(for) · `rules.yaml`(다섯) · 픽스처 `PosShape.java`·`NegShape.java` · 새 `JavaShapeTest` · `CheckRulesTest:169` 144 · 골든 `check/java.json`.
- 순서: 픽스처·`JavaShapeTest`(줄 단언) 먼저 → RED(컴파일 — 상수만 먼저 두고 실행 RED) → `JavaShape` → GREEN → `rules.yaml`·`taint` → `JavaRulesTest` 골든 갱신 → `CheckRulesTest` 144.
- 검증: `-Dtest='JavaShapeTest,JavaRulesTest,CheckRulesTest,PureRulesJsonTest,SelfCheckTest'` 초록 · 골든 diff 가 `check/java.json` 의 `PosShape` 줄뿐 · `self-check.txt` diff 0. 커밋 `feat: 5-88 문장 꼴 규칙 다섯 — 중괄호·빈 문장·홀로 블록·default 자리·interrupt 복원`.
- 의존: 5-84(5-85 와 독립).

### 5-89 — `SyntaxFix`
- 대상: 새 `SyntaxFix.java` · `Cleanup`(`SYNTAX`·`RULES` 셋·`clean` 한 줄) · 픽스처 `syntax/Pos.java` · `CleanupTest.syntaxStage` · 골든 · `code_cleanup_ext.js`(`STAGE`) · `CleanupRoutesTest:109` 25 · `SmokeHtmlUnitTest:1885-1886`.
- 순서: 픽스처 + `syntaxStage` 먼저 → RED → `SyntaxFix` → GREEN → 골든 갱신(`CleanupTest`) → 수 단언·JS.
- 검증: `-Dtest='CleanupTest,CleanupRoutesTest,SmokeHtmlUnitTest,SelfCheckTest'` 초록 · 골든 diff 가 새 `syntax-Pos.java.txt` + `steps.json` 새 항목뿐 · `self-cleanup.txt` diff 0. 커밋 `feat: 5-89 코드 정리 — 중괄호 끼우기 · 빈 문장 · 홀로 블록`.
- 의존: 5-88.

### 5-90 — `SwitchFix`
- 대상: 새 `SwitchFix.java` · `Cleanup`(`SWITCH`·`RULES`·`clean`) · 픽스처 `switch/Pos.java` · `CleanupTest.switchStage` · 골든 · JS · 수 단언 26.
- 검증: 5-89 와 같은 묶음 · 골든 diff 가 `switch-Pos.java.txt`·`steps.json` 항목뿐. 커밋 `feat: 5-90 코드 정리 — default 를 끝으로(떨어지면 건너뜀)`.
- 의존: 5-89.

### 5-91 — `InterruptFix`
- 대상: 새 `InterruptFix.java` · `Cleanup`(`INTERRUPT`·`RULES`·`OPTIONAL`?·`TOUCHED_MAY_VANISH`·`clean`) · 픽스처 `interrupt/Pos.java` · `CleanupTest.interruptStage` · 골든 · JS · 수 단언 27.
- 검증: 같은 묶음. 커밋 `feat: 5-91 코드 정리 — InterruptedException catch 에 interrupt() 복원`.
- 의존: 5-90.

### 5-92 — `PiiFix`
- 대상: 새 `PiiFix.java` · `Cleanup`(`PII`·`RULES`·`OPTIONAL`?·`clean`) · 픽스처 `pii/Pos.java`·`pos.properties` · `CleanupTest.piiStage` · 골든 · JS · 수 단언 28.
- 검증: 같은 묶음. 커밋 `feat: 5-92 코드 정리 — 카드번호 가리기`.
- 의존: 5-91.

### 5-86 — `common.printStackTrace` → 로거
- 대상: `LogFix`(`PRINT_STACK`·`stackCall`·`place`·`ownCatch`·`call`) · `Cleanup`(`RULES`·`OPTIONAL`?·`:158`·`TOUCHED_MAY_VANISH`) · 픽스처 `log/Stack.java`·`log/StackNoLogger.java` · `CleanupTest.stackStage` · 골든 · 수 단언 29.
- 순서: 픽스처 + 시험 먼저 → RED → `LogFix` → GREEN → 골든 갱신.
- 검증: 같은 묶음 + `golden/cleanup/log-*` 기존 다섯 diff 0. 커밋 `feat: 5-86 코드 정리 — printStackTrace 를 로거 error 로`.
- 의존: 5-92.

### 5-87 — `java.starImport` → 쓰는 클래스만
- 대상: 새 `StarImportFix.java`·`PackageTypes.java` · `Cleanup`(`IMPORTS`·`RULES`·생성자·`clean`) · `CleanupRunner`(둘) · `CleanupCorpusTest:45` · 픽스처 `starimport/Pos.java`·`Third.java` · `javaimport/Pos.java` 주석 · `CleanupTest.starImportStage` · 골든(`javaimport-Pos.java.txt` 포함) · 수 단언 30 · JS.
- 순서: 픽스처 + 시험(인라인 자기 소스 표본 포함) 먼저 → RED → `PackageTypes` → `StarImportFix` → GREEN → 골든 갱신.
- 검증: 같은 묶음 + `CleanupRunnerTest` · 골든 diff 가 `starimport-*`·`javaimport-Pos.java.txt`(별표 한 줄 삭제)·`steps.json`(그 항목 + 새 둘)뿐. 커밋 `feat: 5-87 코드 정리 — 별표 import 를 쓰는 클래스로(JDK·자기 소스만)`.
- 의존: 5-86.

### V-60a — corpus 골든 재채점
- 대상: `golden/corpus/*`(§3-11).
- 검증: 갱신 명령 초록 · diff 가 §3-11 안 · 건수·벽시계 이력 · 커밋 `test: V-60a 재채점 — 규칙 다섯 · 고침 여덟`.
- 의존: 5-87(5-85 까지 포함).

### R-27 — 손 판정
- 대상: compare ㉚ 「손 판정」 표(§3-12) + 고침(≤ 2, 음성 먼저 RED).
- 검증: 표 + 합 줄 · 고침이 있으면 V-60a 다시 · 커밋 `docs: R-27 손 판정 — 고침 여덟 · 회귀`(고침은 따로 `fix:`).
- 의존: V-60a.

### V-60 — compare ㉚ · 정적 분석 · `--corpus`
- 대상: `design/compare-2026-10-28.md`(§3-13).
- 검증: `mvn.sh -DskipTests verify` rc 0 → `verify.sh` → `verify.sh --corpus` 셋 초록(따로) · 끝 줄이 더 부르는 레인 없음 · `doc-lint` · 커밋 `docs: V-60 재측정 — compare ㉚ + 정적 분석 + --corpus`.
- 의존: R-27.

### 마무리
§3 「마무리」 그대로. 머지 위임 있음.

## 5. 금지 사항
- `pure/`·portfolio·satchel · `core/meta`·`core/dialect`·`core/gen`·`core/analyze`·`core/vcs`·`core/job` · `LocalFiles.read`·`list`·`writeResult`(5-84 는 `encode` 의 switch → map 한 자리와 상수 둘만) · `AbsentRule`·`RegexRule`·`FileRule`·`MarkupFix`·`BlankRelRule`·`OrderFix`·`ValueFix`·`LocalFix`·`ResourceFix`·`JavaDefects`(복사만, 가시성 안 연다)
- `Cleanup` — 메서드 추가 금지(30) · 길이 1000 넘기 금지 · 단계 차례는 §사용자 결정 「설계가 정한 것」 그대로 · `MAY_APPEAR` 불변 · `TOUCHED_MAY_VANISH` 는 LOG `java.swallow`·INTERRUPT `java.emptyCatch` 둘만 더한다 · `worse` 는 after 루프 한 자리만
- 고침 전부 — 「확실한 꼴만」. 사유 없이 건너뛰지 않는다(사유는 상수 글 + 「줄 N: 」, 코드 본문 안 붙인다) · 블록 정규식(`.*?`) 으로 자바를 고치지 않는다(AST + 줄 Edits) · 같은 줄 두 번 `replace` 금지(Fix 안에서 합친다) · 원문 줄의 들여쓰기·줄바꿈을 보존(새 줄은 이웃 줄 indent)
- `JavaRules` — `taint` 의 for 하나 밖 변경 금지 · 메서드 추가 금지(30) · `imports`·`usedNames` 불변(복사해 쓴다)
- `StarImportFix` — 정적 별표·못 푸는 패키지·두 별표에 겹치는 이름은 고치지 않는다 · 클래스 존재 확인은 `Class.forName(name, false, ClassLoader.getPlatformClassLoader())` 만(앱 로더·`initialize=true` 금지 — 절대 규칙 1·코드 실행) · 소스 폴더 파싱은 그 패키지 폴더 바로 아래 `*.java` 만 · 원본 읽기만
- 규칙 — 기존 139 의 `regex`·`globs`·`message`·`flags`·`kind` 불변(5-85 의 thScope `flags` 한 줄 예외) · id 삭제 금지 · `pure-rules.json` diff 0 · 새 규칙은 전부 `kind: java`(정규식 63 불변)
- 픽스처 — 기존 줄 삭제 금지(`javaimport/Pos.java` 는 주석 한 줄만) · `neg*`·`Neg*` 에 지적이 나는 꼴 금지 · `tb-ignore` 금지 · check 픽스처 폴더(`common·jsp·tsx·security·a11y`)에 새 파일 금지(32) · 새 cleanup 픽스처는 §3 의 폴더 이름 그대로
- 골든 — §3 이 적은 변화 밖 금지 · `-Dgolden.update` 는 그 시험에만 · `steps.json` `row()` 키 불변 · `self-cleanup.txt`·`check/fixtures.json`·`pure-rules.json`·`meta/*`·`deliverable/*`·`corpus/db-*`·`security-*`·`vcs-*`·`js-summary` diff 0 · `self-check.txt` 는 5-83 의 `worse` 줄 하나 밖 diff 0
- 화면 JS — `STAGE` 키 다섯 밖 변경 금지 · `common.js` 불변
- `scripts/` — `verify.sh`·훅 셋·`package.sh`·`gate-probe.sh`·`probes/*.patch`·`verify-fingerprint.sh`·`hooks-test.sh` 불변 · `VerifyLanesTest` 불변
- 새 의존성 금지 · `--full`·`--db` 안 돈다 · 무거운 것을 겹쳐 띄우지 않는다 · `wsl --shutdown` 금지 · python 으로 문서를 「읽고 바로 쓰기」 금지(번들 79 사고 — 읽기를 변수에 받은 뒤 쓴다, `git diff --numstat` 로 삭제 줄 확인)

## 6. 최종 검증
- `bash scripts/mvn.sh -DskipTests verify` rc 0 · `bash scripts/verify.sh` · `bash scripts/verify.sh --corpus` 초록(따로) · 끝 줄이 더 부르는 레인 없음 · `gate-probe.sh` 전부 빨개짐 · `bash scripts/hooks-test.sh` 통과
- 수: 규칙 144(`CheckRulesTest`) · 정규식 63(`PureRulesJsonTest`) · `Cleanup.RULES` 30 · `OPTIONAL` 7 · 화면 체크박스 30·켜진 23 · `/api/cleanup/rules` 30 · `Cleanup` 메서드 30·길이 ≤ 1000 · `JavaRules` 메서드 30 · 새 클래스마다 메서드 ≤ 30·복잡도 ≤ 15·길이 ≤ 100/메서드 · `PureCodeCheckTest` 32
- 골든: `git diff --stat origin/main -- src/test/resources/golden` 이 `check/java.json`(PosShape) · `check/self-check.txt`(`worse` 줄, 바뀌면) · `cleanup/steps.json`(새 항목 일곱 + `javaimport/Pos.java` 항목) · 새 `cleanup/syntax-*`·`switch-*`·`interrupt-*`·`pii-*`·`log-Stack*`·`starimport-*` · `cleanup/javaimport-Pos.java.txt` · `corpus/check-*`·`legacy-*`·`cleanup-*` 안 · 나머지 diff 0
- 수동(한 번, 이력에 한 줄 — HtmlUnit·CLI 대체 가능): 코드 정리 화면 체크박스 30 · 단계 글에 새 라벨 다섯 · CLI 로 jeecg `printStackTrace` 한 파일 결과 열어 보기
- compare ㉚ 표 + 「드러난 것」 + 「후보로」 · 이력에 건수·벽시계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- 0-81 에서 `self-check.txt`·`golden/cleanup` 이 바뀜(이동이 아니었다) · `Cleanup` 길이가 946 이 아님
- 5-83 에서 `worse` 복잡도가 36·37 밖 · `cleanup/*` 골든 변화 · `colAndRow`·`alreadyScoped`·`thScopeStage` 빨강
- 5-84 에서 `LocalFiles` 다른 시험 빨강(글 단언) · `EncodingFix` 시험 빨강
- 5-88 에서 `self-check.txt` 에 새 규칙 줄(자기 코드 실측과 다름 — 그 자리를 보고) · `NegShape` 에 지적 · `java.json` 에 PosShape 밖 변화
- 5-89~5-92·5-86·5-87 에서 `self-cleanup.txt` 변화 또는 `cleanupChangesNothing` 빨강(자기 코드를 고쳤다) · 픽스처 골든에 「다른 지적이 생김·사라짐」 사유(설계의 `TOUCHED_MAY_VANISH` 가 모자라다 — 어느 규칙인지 보고) · 기존 `log-*`·`javaimport-*`(별표 줄 밖)·`markup-*`·`value-*` 골든 변화 · 메서드·복잡도·길이 상한 · `pmd:check`·SpotBugs 새 짝(특히 `Class.forName`)
- V-60a 에서 §3-11 밖 골든 변화 · 중단선 · `CleanupCompileCorpusTest` javac 실패(commons 에서 어느 고침이 깨뜨렸나 — 그 규칙만 `wip/` 후보)
- R-27 에서 고침 셋째가 필요 · 회귀 수(unusedLocal 20·14·3 · thScope 472·758·2,380) 어긋남 · 별표 풀기가 더한 import 가운데 안 쓰는 이름 하나라도(되돌림이 안 잡았다)
- V-60 에서 `--corpus` 가 이 번들 밖 원인으로 빨강(고치기 둘까지) · 여유 RAM 모자람(사용자에게)
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① corpus 새 규칙 수(needBraces·interruptedIgnored 등 — 중단선 아님, 이력에) ② `CleanupCompileCorpusTest` 가 commons 셋에서 중괄호·블록·import 고침 뒤 javac 를 지나는가(V-60a 가 심판) ③ `worse` 복잡도 36 → 37 여부(`self-check.txt` 한 줄) ④ `Class.forName` 을 SpotBugs·PMD 가 지적하나(`mvn.sh -DskipTests verify` 가 5-87 에서 답한다 — 지적되면 코드를 바꾸고 기준선은 안 늘린다) ⑤ 여유 RAM(V-60a·`--corpus` 앞)
- 설계가 미리 잰 것(불확실 아님): `Cleanup`·`JavaRules` 상한 · `Edits` 사용처 여섯 · `worse` 차례 의존 · `quietAttrs` ASCII · `LocalFiles.encode` switch · `AbsentRule.when` flags · 표본 수 전부(§맥락) · 자기 코드 0 · 수 단언 자리 · 스모크 입력 셋 · `javaimport/Pos.java` 별표 삭제
- **사용자만 정할 수 있는 것** — 없음(범위 넷·printStackTrace 꼴·기본 끔 셋·마스크 꼴·thScope flags·머지 위임은 2026-11-11 에 받았다)
