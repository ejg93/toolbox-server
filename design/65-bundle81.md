# 설계 65 — 번들 81: 코드 정리 잔손(5-93) · union catch interrupt · printStackTrace(System.out·err) · 떨어짐 지적 · 대시 없는 카드번호 (Fable 설계 · Opus 실행, 2026-11-12)

## 맥락
번들 80(설계 64, PR #123 머지 `8b7edd2`) 마무리 리뷰 Minor 아홉이 5-93 행으로 남았고, compare ㉚ 「후보로」 다섯이 있다. 사용자 2026-11-12 결정으로 5-93 전부 + 후보 넷을 한 번들로 닫는다. 제3자 별표는 뺀다(후보로 남김). 가지는 `work/2026-11-12`(번들 80 닫기 `cc1639a`·`e250c00` 이 로컬에만 있다 — 그 위에서 친다, main 은 `8b7edd2`). 레인 — `core/check`·`resources/check`·`golden/corpus` 는 java+corpus · `fixtures/**`·`golden/check`·`golden/cleanup`·`web/` 시험은 java · `core/job` 은 java → 끝에 `--corpus` 한 번(`--db` 안 돈다).

**실측(2026-11-12, `e250c00` 트리, 표본은 juliet·benchmark 뺌)**
- **`Cleanup` 991 줄 · 메서드 30 = 상한**. 표식 묶음 `removeMarkers`·`markerEdit`·`opener`(`:846-978`, 구역 주석 포함 133 줄)와 그 묶음만 쓰는 상수 여섯(`CROSS_FILE:57-59` · `C_LIKE:81` · `MARKUP:82` · `JSP:83` · `HASH_LINE:84` · `CLOSER:85`)은 다른 자리에서 안 쓴다(`C_LIKE` 854·855·955·973 · `MARKUP` 854·961 · `JSP` 854·959 · `HASH_LINE` 854·921·965 · `CLOSER` 932 · `CROSS_FILE` 888 — 전부 그 구역, `:40` 의 MARKUP 은 주석 글). 밖 참조 0(`Cleanup.removeMarkers`·`Cleanup.CROSS_FILE` grep 0). `clean:170` 한 줄이 `removeMarkers` 를 부른다. `self-check.txt:38·40` 이 `Cleanup.java|…markerEdit(8)` 두 줄(복잡도 34 · 인자 8)
- **`InterruptFix`(87 줄)** — `{` 뒤 글이 공백이 아니면 사유가 전부 `ONE_LINE`(「catch 몸이 한 줄」) — `{ // 주석` 도 같은 사유(5-93 ③). Allman(`catch (…)` 다음 줄 `{`)은 `lines[brace-1].substring(col)` 이 비어 끼우기는 되지만, 빈 catch 의 `java.emptyCatch` 는 catch 줄(안 고친 줄)에 걸려 「다른 지적이 사라짐」 으로 단계가 되돌려진다(5-93 ②). 루프 안인지는 안 본다(5-93 ①)
- **표본 `catch (InterruptedException`** 단일형 64 — interrupt 있음 19 · 없음 45. 없음 가운데 **루프 안 + 몸에 break·return·throw 없음 5**: `egov`·`egov-prev` `…/utl/sim/service/EgovFileMntrg.java:267`(`while (!interrupted) { try { Thread.sleep(delay); } catch …` — 끼우면 sleep 이 곧바로 다시 던져 빈 루프) · `…/sym/bat/service/BatchShellScriptJob.java:129`(for 안 `process.waitFor()`) · `jeesite/…/ibatis/thread/Runnable.java:102`(sleep 루프). 루프 안 + 탈출 있음 5(끼워도 된다). **Allman** 6 — `ruoyi`·`ruoyi-classic` `ruoyi-common/…/utils/Threads.java:29·58·89`
- **union catch + InterruptedException** 표본 2(`commons-lang/…/concurrent/BackgroundInitializer.java:347` · `roller/…/fetcher/RomeFeedFetcher.java:94`) · 자기 코드 `core/job/JobManager.java:77`(`catch (InterruptedException | JobContext.JobCancelledException e)` — interrupt 없음, 몸은 `job.emit(CANCELLED)` 한 줄) · `core/check/SpotBugsRun.java:137`(instanceof + interrupt 있음 — 지적 밖). 픽스처 `check/java/NegShape.java:68`·`cleanup/interrupt/Pos.java:48` 이 union 을 「지적 밖」 으로 든다
- **`common.printStackTrace` 정규식 `\.printStackTrace\s*\(\s*\)` 는 못 바꾼다** — `PureRulesJsonTest.pureHtmlEmbedsGolden:73` 이 순수본 `pure/tools/code_check.html` 의 `RULES` 와 골든 `pure-rules.json` 이 같은지 본다(순수본 동결). `fixtures/check/common/neg.java:11` 이 `e.printStackTrace(System.err);` 를 음성으로 든다 → **새 `kind: java` 규칙**으로 간다(§3-7)
- **표본 `printStackTrace(인자)`** 29 — `System.out` 1(`struts/core/…/util/StrutsTestCaseHelper.java:89`) · PrintWriter·스트림 변수 28(유틸·예외 클래스 재정의 — 고칠 자리 아님). 수신자가 이름 아닌 인자 없는 꼴 2(`roller/…/RollerException.java:105` `super.` · `…/FetcherException.java:51` `getRootCause().` — 예외 클래스 안, catch 밖)
- **`security.piiCard`** 는 순수본에 있는 정규식 규칙(`pure-rules.json:445`) → 정규식을 못 넓힌다. Luhn 은 정규식으로 못 건다 → **새 `kind: luhn` 규칙 `security.piiCardPlain`**(§3-9). 표본 16 자리 숫자(앞뒤가 `\w`·`.`·`-` 아님) 9 — Luhn 통과 4 = 전부 `shopizer/…/payment/impl/BeanStreamPayment.java:261·268·286·554`(`5100000020002000` · `4030000010001234`) · 떨어진 5(jeecg sql 4 · jsontest json 1)
- **떨어짐(fall-through)** — default 꼴 1(`jspwiki/…/tags/LinkTag.java:359`, 이미 `java.defaultLast` 「묶임」). case 일반은 줄 어림(앞 문장이 `;` 로 끝나고 break·return·throw·continue 아님, 「fall through」 주석 없음)으로 jspwiki 135 · commons-collections 9 · egov-msa 8 · roller 4 · jeecg 2(주석 달린 commons-lang 6 은 뺌) — AST 수는 V-61a 가 잰다. 자기 코드는 화살표 switch 라 어림 0
- **`StarImportFix`(208 줄)** — `used()` 가 모든 주석의 낱말을 쓰인 이름으로 센다(`:150-152`, `// Set …` → `java.util.Set` import, 5-93 ⑤) · `typeRefs():187-195` 가 대문자로 시작하는 수신자를 형식으로 센다(jspwiki `PROPERTY_APPROVER_PREFIX` — 파일 하나를 더 건너뜀) · 자기 패키지 목록이 empty 면 그 몫을 못 빼고 넘어가(`:156`) 같은 패키지 형식이 JDK 별표 형식에 가려질 수 있다(5-87 알려진 한계)
- **`PackageTypes.scan:45`** 가 `files.list(…).files()` 만 쓰고 `truncated()`(`LocalFiles.Listing:78`) 를 안 본다(5-93 ④)
- **`SwitchFix:56`** `from = d.getComment()…` — JavaParser 는 바로 위 주석 하나만 붙인다 → default 위 주석이 두 줄 이상이면 마지막 줄만 옮겨진다(5-93 ⑥). 표본 0
- **`SyntaxFix.block:282`** `decl` 이 지역 선언·지역 클래스만 본다 — `if (!(o instanceof String s)) return;` 꼴 패턴 변수는 블록 밖으로 범위가 흐른다(5-93 ⑦). 표본 `java.unneededBlock` 3. JavaParser 3.26.3
- **`LogFix.noCall:145`** — 지적 줄에 고칠 호출이 없으면 sysout 이 아닌 한 전부 「printStackTrace 수신자가 이름 하나가 아니다 — 손으로」(문자열 속 글 · 호출이 앞 줄에서 시작하는 꼴도, 5-93 ⑧)
- **`CleanupCorpusTest:82-83`** skip 키 — 「줄 N: 」 과 「다른 지적이 생김·사라짐: …」 만 떼고 `별표 패키지를 못 푼다(JDK·소스 루트 밖): javax.servlet` · `출처를 모르는 형식 이름 — 손으로: X` · `이름이 두 별표 패키지에 — 손으로: n` 은 이름째 키가 된다(5-93 ⑨)
- **수 단언 자리** — 규칙 144(`CheckRulesTest:169`) · `Cleanup.RULES` 30(`CleanupRoutesTest:109`) · 스모크 체크박스 30·켜진 23(`SmokeHtmlUnitTest:1885-1886`) · 정규식 63(`PureRulesJsonTest`, 불변 — 새 규칙 셋 가운데 정규식 kind 0) · `PureCodeCheckTest` 32(픽스처 파일 수 — 새 파일 없음)
- `CheckRulesTest.fixturesGolden:84-109` — `java/`·`mybatis/`·`ext/` 밖 픽스처에 켜진 규칙 전부가 양성에서 한 번 이상(`rules.enabled()`) → `kind: luhn` 규칙도 `check/security/pos.*` 에 양성 줄이 있어야 한다. `PureCodeCheckTest:57` 은 정규식 id 만 대조 → 새 kind 줄은 순수본 대조 밖
- `CleanupTest` 도우미 `cleanup(groups):33-35`·`cleanupIn:193-197` 은 셋짜리 생성자(패키지 목록 finder 없음 — 늘 empty). `starImportStage:327` 이 `starimport/Pos.java`(`package cleanup.starimport;`)의 `java.util.*` 를 셋으로 푸는 것을 단언한다 → 자기 패키지 한계 고침(§3-3)이 이 길을 건너뛰게 만든다 → 도우미가 finder 를 줘야 한다
- `tb-ignore` 는 같은 줄 꼬리 주석(`Main.java:11` 꼴 `// tb-ignore <규칙> <사유>`)

**범위 밖** — `pure/` · 제3자 별표(jar 목록으로 풀기 — 후보) · PrintWriter·스트림 변수 인자의 printStackTrace · 수신자가 이름이 아닌 printStackTrace 고침(사유만 고친다) · 떨어짐 고침(지적만) · `SwitchFix.escapes` 의 깊은 탈출 판정 · `RegexRule`·`AbsentRule`·`FileRule`·`MarkupFix`·`ValueFix`·`LocalFix`·`ResourceFix`·`OrderFix`·`JavaDefects`·`JavaRules` · 기존 규칙의 `regex`·`message` · 5-31·2-17·보류·조건부 행

## 사용자 결정(2026-11-12)
1. compare ㉚ 후보 — **넣는다 넷**: 대시 없는 카드번호 · 떨어짐 지적(고침 없음) · union catch interrupt 복원 · printStackTrace 는 **`System.out`·`System.err` 인자만**(PrintWriter·수신자 식 꼴은 그대로 건너뜀). **뺀다**: 제3자 별표(후보로 남김)
2. 5-93 — **아홉 + 한계 둘 전부**. ① 루프 안 catch 는 **건너뜀**(사유)
3. **머지 위임 있음** · 설계 Fable · 실행 Opus

설계가 정한 것(전부 [고정]):
- **행 차례** `S0`→`0-82`→`5-93a`→`5-93b`→`5-93c`→`5-93d`→`5-94`→`5-95`→`5-96`→`5-97`→`V-61a`→`R-28`→`V-61`→마무리. 행은 서로 독립(막히면 그 행만 `wip/` 로). 새 규칙 행(5-95·5-96·5-97)이 가장 갈릴 자리라 뒤
- **0-82 가 먼저** — `Cleanup` 의 표식 묶음을 `MarkerFix` 로(991 → 약 850 줄 · 메서드 30 → 27). 이 번들의 `Cleanup` 변경은 그 뒤 `RULES`·`OPTIONAL`·`clean` 두 줄뿐
- **기본 켬·끔은 물려받는다** — union interrupt 는 `java.interruptedIgnored`(끔) · 새 `java.printStackTraceStream`·`security.piiCardPlain` 은 `OPTIONAL`(끔 — 출력 자리·값 바뀜). `java.fallThrough` 는 지적만이라 `Cleanup.RULES` 밖
- **규칙 144 → 147**(`java.printStackTraceStream` · `java.fallThrough` · `security.piiCardPlain`) · **`Cleanup.RULES` 30 → 32** · `OPTIONAL` 7 → 9 · 화면 체크박스 32·켜진 23
- 기존 정규식 규칙은 한 글자도 안 바꾼다(순수본 동결 — `pureHtmlEmbedsGolden`). `pure-rules.json` diff 0
- 자기 코드 `JobManager.java:77` 은 고치지 않고 `tb-ignore` 한 줄(취소는 거기서 끝나고 작업 스레드는 곧 돌아간다 — interrupt 를 되세울 이유가 없다) → `self-check.txt`·`self-cleanup.txt` 불변

## 1. 목표와 범위
- 한 문장: 번들 80 리뷰 잔손 아홉과 알려진 한계 둘을 닫고, 코드 정리가 union catch 의 interrupt · `printStackTrace(System.out·err)` · 대시 없는 카드번호를 고치며, 코드 검사가 switch 떨어짐을 지적한다.
- 완료 시 체감: 코드 정리 화면 체크박스 32(켜진 23) · `catch (IOException | InterruptedException e) {` 다음에 `if (e instanceof InterruptedException) { Thread.currentThread().interrupt(); }` 세 줄 · sleep 루프 안 catch 는 「루프 안 catch」 사유로 건너뜀 · Allman 빈 catch 도 고쳐짐 · `e.printStackTrace(System.out);` 이 `로거.error(e.getMessage(), e);` · `"4030000010001234"` 가 `"************1234"` · 코드 검사에 `java.fallThrough`·`java.printStackTraceStream`·`security.piiCardPlain` 이 새로 보임 · `// Set …` 주석만으로 import 가 안 생김.
- In scope: `core/check` — `Cleanup`(표식 묶음 삭제·`RULES`·`OPTIONAL`·`clean` 두 줄) · 새 `MarkerFix`·`LuhnRule` · `InterruptFix` · `StarImportFix` · `PackageTypes` · `SwitchFix` · `SyntaxFix.block` · `LogFix` · `JavaShape` · `PiiFix` · `RuleSet`(kind 한 줄) · `Rule`(javadoc 한 낱말) · `core/job/JobManager.java`(꼬리 주석 한 줄) · `resources/check/rules.yaml`(규칙 셋) · 시험·픽스처·골든(행마다) · `design/compare-2026-10-28.md` ㉚ 처분·㉛ · `PLAN.md`·`PROGRESS.md`
- Out of scope: 「범위 밖」 전부 · 새 의존성 · 화면 JS(`code_cleanup_ext.js` 의 `STAGE` 는 그대로 — 새 단계 없음)

## 2. 현재 구조 요약
「맥락」 의 실측이 요약이다. 덧붙이는 함정:
- **자바를 고친 행마다 `bash scripts/mvn.sh -q -DskipTests verify` 를 먼저** — 빠른 verify 는 PMD·SpotBugs 를 안 본다. PMD 단골: `AvoidLiteralsInIfCondition`(글자·수 리터럴은 상수로) · `AvoidFieldNameMatchingMethodName`(상수와 메서드 이름이 대소문자만 다르면 안 된다) · `UseLocaleWithCaseConversions` · `LiteralsFirstInComparisons` · 시험 `AvoidDuplicateLiterals`(같은 글 넷 이상은 상수)
- **Bash heredoc·python 안 `\` 는 도구가 벗긴다** — 백슬래시 든 자바 문자열·정규식·yaml 정규식은 Edit 도구로만 넣는다
- `Edits.replace(line, text)` 는 첫 호출만 든다 · `touchedNew` 는 바꾼 줄만 · 같은 글로 `replace` 해도 고친 줄이 된다(`LogFix.catchHead` 가 쓰는 길)
- `commit` → `worse`: 대상 규칙·`file.*`·`metric.*` 는 비교 밖 · 고친 줄 위 지적은 규칙 이름 다중집합(사라짐은 `TOUCHED_MAY_VANISH` — INTERRUPT 는 `java.emptyCatch`, LOG 는 `java.swallow`) · 그 밖은 (규칙, 발췌) 다중집합
- 고침은 `JavaShape.flagged(cur, hit)` 로 지금 지적이 있는 노드만(`tb-ignore` 로 가린 줄은 안 고친다)
- `JavaRules.taint` 가 `JavaShape.apply(cu, on.keySet())` 를 이미 돈다 — `JavaShape` 에 규칙을 더하면 `rules.yaml` 의 `kind: java` 항목만 있으면 지적이 난다(`JavaRules` 는 안 고친다)
- `CleanupTest.fixturesGolden` 은 `fixtures/cleanup/**` 전부를 `cleanup(null)` 로 돌려 `cleanup/<dir>-<파일>.txt` + `steps.json`
- `SelfCheckTest.mainMatchesBaseline` 은 자기 `src/main` 에 모든 규칙 — 새 지적은 빨강 · `cleanupChangesNothing` 은 자기 코드 정리 변경 0
- `JavaRulesTest` 는 `fixtures/check/java/Pos*·Neg*` 골든 `check/java.json` — 켜진 java 규칙 전부가 양성에서 한 번 이상, Neg 히트 0
- 레인: `core/check`·`resources/check`·`golden/corpus` = java+corpus · `fixtures/**`·`golden/check`·`golden/cleanup` = java · `design/**` = 없음 · `PLAN.md`·`PROGRESS.md` = docs

## 3. 설계 결정 — 전부 [고정]

### 0-82 — `MarkerFix` 분리(§3-0)
- 새 `src/main/java/kr/ejg/toolbox/core/check/MarkerFix.java`(package-private `final class`, private 생성자):
  - javadoc: 「코드 정리 단계 marker — 쓸모없는 tb-ignore 표식 떼기. 0-82 — Cleanup 안에서 밖으로(길이 상한). 몸 그대로」
  - 상수 여섯을 그대로 옮긴다(주석 포함): `CROSS_FILE`(`static final`) · `C_LIKE` · `MARKUP` · `JSP` · `HASH_LINE` · `CLOSER`(`private static final`)
  - `Cleanup.removeMarkers` → `static void apply(Source s, List<Integer> lines, Set<String> enabledIds, Edits e, List<Cleanup.Skip> skips)`(몸 그대로, `Skip` → `Cleanup.Skip`, `MARKER` → `Cleanup.MARKER`) · `markerEdit` · `opener` 는 이름·몸 그대로
- `Cleanup.java`: 구역 주석 「수정기 ② 표식」 부터 `opener` 끝까지 삭제 · 상수 여섯 삭제 · `clean` 의 MARKER 줄을 `MarkerFix.apply(x.source(), lines(x.cur, t), enabledIds, e, l)` 로 · 안 쓰게 된 import 는 지운다(`java.unusedImport` 가 자기 검사에 걸린다 — `Matcher` 는 다른 자리가 쓰는지 grep 으로 보고 정한다)
- `self-check.txt` — `Cleanup.java|metric.complexity|복잡도 34 > 15 · markerEdit(8)` · `Cleanup.java|metric.params|인자 8 > 7 · markerEdit(8)` 두 줄의 파일 이름이 `MarkerFix.java` 로(줄 자리는 파일 이름 차례) — 이 둘 밖 diff 0
- 검증: `-Dtest='CleanupTest,SelfCheckTest,ArchitectureTest'` 초록 · `golden/cleanup` diff 0 · `Cleanup` 길이 ≤ 860 · 메서드 27

### 5-93a — `InterruptFix` 셋(①②③)(§3-1)
- 사유 상수: `ONE_LINE = "catch 몸이 한 줄"`(그대로) · 새 `AFTER_BRACE = "여는 중괄호 뒤에 다른 글"` · 새 `IN_LOOP = "루프 안 catch — interrupt 를 끼우면 대기가 곧바로 다시 깨진다, 손으로"`
- 지적 catch `k`(= `p.getParentNode()` 의 `CatchClause`)마다 차례대로:
  1. `otherThread` → `OTHER_THREAD`(그대로)
  2. **① 루프** — `inLoop(k) && !leaves(k.getBody())` → `IN_LOOP`
     ```java
     /** catch 에서 위로 메서드·람다·초기화 블록까지 가는 길에 for·foreach·while·do 가 있다 */
     private static boolean inLoop(Node k) {
         for (Node a = k.getParentNode().orElse(null); a != null && !(a instanceof BodyDeclaration) && !(a instanceof LambdaExpr); a = a.getParentNode().orElse(null)) {
             if (a instanceof Statement s && JavaShape.loopBody(s) != null) {
                 return true;
             }
         }
         return false;
     }

     /** 몸에 루프를 벗어나는 문장(break·return·throw)이 있다 — 람다·안쪽 클래스 안 것은 안 센다 */
     private static boolean leaves(BlockStmt body) {
         return body.findAll(Statement.class).stream().filter(s -> s instanceof BreakStmt || s instanceof ReturnStmt || s instanceof ThrowStmt)
                 .anyMatch(s -> s.findAncestor(LambdaExpr.class, a -> body.isAncestorOf(a)).isEmpty()
                         && s.findAncestor(BodyDeclaration.class, a -> body.isAncestorOf(a)).isEmpty());
     }
     ```
  3. **③ `{` 뒤 글** — `rest = lines[brace-1].substring(col(body.getBegin()))`: 공백뿐이거나 `rest.strip().startsWith("//")` 면 끼운다 · 아니고 `line(body.getEnd()) == brace` 면 `ONE_LINE` · 그 밖은 `AFTER_BRACE`
  4. 끼우기는 그대로(`e.replace(brace, lines[brace-1] + "\n" + indent + CALL)`)
  5. **② Allman** — `int head = line(k.getBegin()); if (head != brace) { e.replace(head, lines[head - 1]); }`(catch 줄을 그대로 다시 써 고친 줄로 — `java.emptyCatch` 가 사라져도 `TOUCHED_MAY_VANISH` 가 덮는다. `LogFix.catchHead` 와 같은 길)
- 픽스처 `fixtures/cleanup/interrupt/Pos.java` 끝(마지막 `}` 앞)에 메서드 넷 추가(기존 줄 불변): `allman()`(빈 몸 Allman — 고쳐짐) · `loop()`(`while (run) { try { Thread.sleep(10); } catch (InterruptedException e) { log(e); } }` — `IN_LOOP`) · `loopBreak()`(같은 꼴 + 몸 끝 `break;` — 고쳐짐) · `braceComment()`(`} catch (InterruptedException e) { // 무시` 다음 줄 `}` — 고쳐짐)
- `CleanupTest.interruptStage` 에 먼저 단언(RED): skips 에 `IN_LOOP` 한 건 · 결과 글에 Allman `{` 다음 줄 interrupt · `{ // 무시` 다음 줄 interrupt · `loopBreak` 고쳐짐 · 「다른 지적이 사라짐」 없음
- 골든: `cleanup/interrupt-Pos.java.txt` · `steps.json` 의 그 항목만

### 5-93b — 별표 풀기 넷(④⑤ · 자기 패키지 한계 · 상수 수신자)(§3-2·§3-3)
- **④ `PackageTypes.scan`** — `LocalFiles.Listing l = files.list(…); if (l.truncated()) { return Optional.empty(); }` 뒤 `l.files()` 를 돈다
- **⑤ `StarImportFix.used`** — 주석 낱말 전부 대신 **javadoc 의 참조 태그 뒤 이름만**:
  ```java
  /** javadoc 이 형식을 가리키는 자리 — {@link X}·{@linkplain X}·@see X·@throws X·@exception X·{@value X#…} 의 X 첫 마디 */
  private static final Pattern DOC_REF = Pattern.compile("@(?:link|linkplain|see|throws|exception|value)\\s+([A-Za-z_$][\\w$]*)");
  …
  for (Comment cm : cu.getAllComments()) {
      if (cm.isJavadocComment()) {
          Matcher m = DOC_REF.matcher(cm.getContent());
          while (m.find()) {
              used.add(m.group(1));
          }
      }
  }
  ```
  (`{@link #foo}` 는 안 걸린다 · `@see java.util.List` 는 `java` — 어느 별표에도 없어 무해. 클래스 javadoc 의 「주석 단어(JavaRules.usedNames 꼴 복사)」 → 「javadoc 참조 태그의 이름」)
- **자기 패키지 한계** — 새 사유 `OWN_UNKNOWN = "자기 패키지 형식 목록을 못 구함 — 손으로"`. `apply` 에서 `resolved` 를 구한 뒤(`stray` 판정 앞):
  ```java
  boolean ownKnown = cu.getPackageDeclaration().flatMap(p -> packageTypes.apply(p.getNameAsString())).isPresent();
  if (!ownKnown && resolved.stream().anyMatch(x -> !x.isEmpty())) {
      skip(skips, stars.get(0), OWN_UNKNOWN);
      return;
  }
  ```
  (package 선언이 없거나 폴더를 못 읽으면 unknown. 푼 이름이 0 인 별표만 있으면 — 줄 삭제뿐 — 그대로 지운다: `javaimport/Pos.java` 불변)
- **상수 수신자** — `typeRefs` 의 수신자 판정에 `!constantLike(name)` 을 더한다:
  ```java
  private static final Pattern CONSTANT = Pattern.compile("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+");
  /** 밑줄이 든 전부 대문자 이름은 상수다 — URL·UUID 같은 형식 이름은 밑줄이 없어 그대로 형식으로 센다 */
  private static boolean constantLike(String n) {
      return CONSTANT.matcher(n).matches();
  }
  ```
- `CleanupTest` 도우미 둘(`cleanup(groups)`·`cleanupIn`)을 다섯 인자 생성자로 — 넷째 `rel -> java.util.Optional.empty()` · 다섯째 `CleanupTest.OWN`:
  ```java
  /** 5-93b — 픽스처 패키지(cleanup.*)는 자기 패키지 형식 목록이 빈 것으로 안다. 그 밖은 모른다 */
  static final java.util.function.BiFunction<String, String, java.util.Optional<Set<String>>> OWN =
          (rel, pkg) -> pkg.startsWith("cleanup") ? java.util.Optional.of(Set.of()) : java.util.Optional.empty();
  ```
- `CleanupTest.starImportStage` 에 먼저 인라인 단언 넷(RED → GREEN): (a) `// Set 은 안 쓴다` 줄 주석만 있는 파일 — `import java.util.Set;` 이 안 생긴다 · `/** {@link Set} */` 는 생긴다 (b) 셋짜리 생성자(finder 없음)로 `import java.util.*;` + `List` 사용 → skips 에 `OWN_UNKNOWN`, 글 불변 (c) `LIMIT_MAX.length()` 수신자 + `import java.util.*;` + `List` → `STRAY` 없이 풀린다 (d) `PackageTypes` — `LocalFiles.MAX_FILES` 를 넘기 어려우니 `PackageTypesTest`(없으면 새로) 대신 **`scan` 의 truncated 분기는 `files.list` 의 max 인자를 받는 package-private 겹침 `scan(files, root, sub, parser, int max)` 로 빼고 max=1 로 파일 둘 폴더 → empty** 를 단언
- 골든: `cleanup/*` diff 0 기대(`starimport-*`·`javaimport-*` 불변 — 도우미가 OWN 을 준다)

### 5-93c — `SwitchFix` ⑥ · `SyntaxFix.block` ⑦(§3-4)
- **⑥** `SwitchFix.apply` — `from` 계산을 바꾼다:
  ```java
  String[] bare = RegexRule.stripComments(text, "java").split("\n", -1); // 주석을 공백으로 — 줄 수가 같다
  …
  int from = line(d.getBegin());
  if (bare.length == lines.length) {
      while (from > 1 && bare[from - 2].isBlank()) {
          from--; // default 위 주석·빈 줄
      }
      while (from < line(d.getBegin()) && lines[from - 1].isBlank()) {
          from++; // 앞쪽 빈 줄은 안 옮긴다
      }
  } else {
      from = d.getComment().map(c -> line(c.getBegin())).orElse(from); // 텍스트 블록 — 옛 길
  }
  ```
  (앞 entry 의 마지막 줄은 코드가 있어 `bare` 가 비지 않는다 — 거기서 멈춘다. default 가 첫 entry 면 switch 머리 줄에서 멈춘다)
- **⑦** `SyntaxFix.block` 의 `decl` 에 `|| b.findAll(InstanceOfExpr.class).stream().anyMatch(i -> i.getPattern().isPresent())` — 사유는 `DECL` 그대로
- 픽스처 — `switch/Pos.java` 끝에 메서드 하나(default 위 `//` 주석 두 줄, 전부 탈출 — 두 줄이 같이 옮겨짐) · `syntax/Pos.java` 끝에 메서드 하나(홀로 블록 안 `if (!(o instanceof String s)) { return; }` — `DECL` 건너뜀). `CleanupTest.switchStage`·`syntaxStage` 에 먼저 단언(RED)
- 골든: `switch-Pos.java.txt`·`syntax-Pos.java.txt`·`steps.json` 두 항목만

### 5-93d — `LogFix.noCall` ⑧ · corpus skip 키 ⑨(§3-5)
- **⑧** `noCall(cur, targets, no)` → `noCall(cu, cur, targets, no)`:
  ```java
  private static String noCall(CompilationUnit cu, List<Finding> cur, Set<String> targets, int no) {
      boolean sysout = targets.contains(SYSOUT) && cur.stream().anyMatch(f -> f.line() == no && SYSOUT.equals(f.rule()));
      if (sysout) {
          return "print·println 호출이 아니다";
      }
      List<MethodCallExpr> here = cu.findAll(MethodCallExpr.class).stream().filter(m -> PRINT_STACK_NAME.equals(m.getNameAsString()))
              .filter(m -> m.getName().getBegin().filter(p -> p.line == no).isPresent()).toList();
      if (here.isEmpty()) {
          return "printStackTrace 호출이 아니다(문자열 속 글)";
      }
      boolean named = here.stream().anyMatch(m -> m.getScope().filter(NameExpr.class::isInstance).isPresent());
      return named ? "여러 줄 식" : "printStackTrace 수신자가 이름 하나가 아니다 — 손으로";
  }
  ```
  (수신자가 이름인데 `calls` 에 없었다 = 호출이 앞 줄에서 시작)
- `CleanupTest.stackStage` 에 먼저 인라인 둘(RED): `String s = ".printStackTrace()";` 줄 → 「호출이 아니다(문자열 속 글)」 · `e\n    .printStackTrace();` → 「여러 줄 식」. 기존 `super.printStackTrace()` 단언 불변
- **⑨** `CleanupCorpusTest:82-83` — 키에서 이름을 뗀다:
  ```java
  String why = k.stage() + " " + k.reason().replaceFirst("^줄 \\d+: ", "");
  why = why.replaceFirst("^(\\S+ 다른 지적이 (?:생김|사라짐)): .*$", "$1");
  skips.merge(why.replaceFirst("(손으로|밖\\)): .*$", "$1"), 1, Integer::sum); // 5-93 ⑨ — 패키지·형식 이름은 키에서 뺀다
  ```
  corpus 골든(`cleanup-jspwiki.json`·`cleanup-roller.json` 의 skips 키)은 V-61a 가 다시 뜬다
- 골든(단위): diff 0

### 5-94 — union catch interrupt(§3-6)
- `JavaShape.interruptCatches` — `UnionType` 을 건너뛰는 대신:
  ```java
  Type t = c.getParameter().getType();
  boolean interrupted = t instanceof UnionType u ? u.getElements().stream().anyMatch(x -> INTERRUPTED_EXCEPTION.equals(simpleType(x)))
          : INTERRUPTED_EXCEPTION.equals(simpleType(t));
  if (!interrupted) {
      continue;
  }
  ```
  나머지(`restores` — interrupt() 호출·throw)는 그대로. 클래스 javadoc 의 「union catch 는 규칙 밖」 → 「union 도 본다(설계 65)」
- `InterruptFix` — union 이면 끼우는 글이 세 줄:
  ```java
  boolean union = p.getType() instanceof UnionType;
  String in = indent(lines, body, brace);
  String unit = in.indexOf('\t') >= 0 ? "\t" : "    ";
  String call = union ? in + "if (" + p.getNameAsString() + " instanceof InterruptedException) {\n" + in + unit + CALL + "\n" + in + "}" : in + CALL;
  e.replace(brace, lines[brace - 1] + "\n" + call);
  ```
  5-93a 의 ①②③ 판정은 union 에도 같이 선다. 클래스 javadoc 에 한 줄
- `core/job/JobManager.java:77` — 줄 끝에 ` // tb-ignore java.interruptedIgnored 취소는 여기서 끝난다 — 작업 스레드가 곧 돌아간다` (코드 불변)
- 픽스처: `check/java/NegShape.java:68` union catch 몸 첫 줄에 `if (e instanceof InterruptedException) { Thread.currentThread().interrupt(); }` 를 **더해** 음성으로 남긴다(줄 삭제 없음) · `check/java/PosShape.java` 끝에 union catch(interrupt 없음) 메서드 하나 · `cleanup/interrupt/Pos.java:48` `union()` 은 그대로 두고 주석만 「지적 밖」 → 「세 줄로」(고쳐진다)
- `JavaShapeTest` 에 먼저 줄 단언(union 양성·음성) · `CleanupTest.interruptStage` 에 union 결과 글 단언(RED → GREEN)
- 골든: `check/java.json`(PosShape 한 줄) · `cleanup/interrupt-Pos.java.txt`·`steps.json` 그 항목 · `self-check.txt`·`self-cleanup.txt` diff 0

### 5-95 — `java.printStackTraceStream` + 로거로(§3-7)
- `rules.yaml` — `java.interruptedIgnored` 항목 뒤에:
  ```yaml
  - id: java.printStackTraceStream
    group: java
    severity: warn
    kind: java
    globs: ['*.java']
    message: 'printStackTrace(System.out·err) — 로거로'
    fix: '로거의 error(메시지, 예외) 호출로 바꾼다 — 스택은 로그 파일에만'
    ref: 'Sonar S1148'
  ```
- `JavaShape` — `static final String STACK_STREAM = "java.printStackTraceStream";` · `apply` 에 `if (on.contains(STACK_STREAM)) { cu.findAll(MethodCallExpr.class).stream().filter(JavaShape::stackStream).forEach(m -> add(out, STACK_STREAM, m)); }`:
  ```java
  /** x.printStackTrace(System.out) · (System.err) — 수신자는 아무 식 */
  static boolean stackStream(MethodCallExpr m) {
      return "printStackTrace".equals(m.getNameAsString()) && m.getArguments().size() == 1
              && m.getArgument(0) instanceof FieldAccessExpr f && f.getScope() instanceof NameExpr n && "System".equals(n.getNameAsString())
              && ("out".equals(f.getNameAsString()) || "err".equals(f.getNameAsString()));
  }
  ```
  (`apply` 복잡도가 15 를 넘으면 규칙별 if 를 `shape(cu, on, out)`·`flow(cu, on, out)` 둘로 나눈다. 클래스 javadoc 목록에 한 줄)
- `LogFix` — `static final String STACK_STREAM = JavaShape.STACK_STREAM;` · `stackCall(m)` 을 `stackCall(m, targets)` 로:
  ```java
  private static boolean stackCall(MethodCallExpr m, Set<String> targets) {
      boolean named = PRINT_STACK_NAME.equals(m.getNameAsString()) && m.getScope().filter(NameExpr.class::isInstance).isPresent();
      return named && (targets.contains(PRINT_STACK) && m.getArguments().isEmpty() || targets.contains(STACK_STREAM) && JavaShape.stackStream(m));
  }
  ```
  `wanted` = `targets.contains(SYSOUT) && printCall(c) || stackCall(c, targets)` · `apply:110` `place(c, cu, stackCall(c, targets))` · `catchHead(c)` 는 `PRINT_STACK_NAME.equals(c.getNameAsString())` 로 판정 · `call` 은 stack 이면 지금 글 그대로(`로거.error(변수.getMessage(), 변수)`) — 인자 `System.out` 은 버린다 · `noCall` 은 5-93d 꼴 그대로(STACK_STREAM 지적 줄도 같은 사유 길)
- `Cleanup` — `RULES` 에 `"java.printStackTraceStream"`(`"common.printStackTrace"` 뒤) · `OPTIONAL` 에 추가 · `clean` 의 LOG 줄 `Set.of(LogFix.SYSOUT, LogFix.PRINT_STACK, LogFix.STACK_STREAM)`
- 픽스처: `check/java/PosShape.java` 에 `catch (Exception e) { e.printStackTrace(System.err); }` 메서드 · `NegShape.java` 에 `e.printStackTrace(new java.io.PrintWriter(w));`·`e.printStackTrace(out)`(지역 변수 `out`) · `cleanup/log/Stack.java` 끝에 catch 둘(`e.printStackTrace(System.out);` — 고쳐짐 · `e.printStackTrace(pw);` — 지적 밖). `CleanupTest.stackStage` 단언 먼저(RED)
- 수: 규칙 145 · `rulesListsFixable` 31 · 스모크 31·23
- 골든: `check/java.json` · `cleanup/log-Stack.java.txt` · `steps.json` 그 항목. `check/fixtures.json`·`pure-rules.json` diff 0(`common/neg.java:11` 은 `java/` 밖이라 java kind 규칙이 안 돈다 — 돌면 중단 조건)

### 5-96 — `java.fallThrough` 지적(§3-8)
- `rules.yaml` — `java.defaultLast` 항목 뒤에:
  ```yaml
  - id: java.fallThrough
    group: java
    severity: warn
    kind: java
    globs: ['*.java']
    message: 'case 가 break 없이 다음 case 로 떨어진다'
    fix: 'break·return 을 넣거나, 의도면 // fall through 주석을 단다'
    ref: 'checkstyle FallThrough · Sonar S128'
  ```
- `JavaShape` — `static final String FALL_THROUGH = "java.fallThrough";` · `apply` 에서 `SwitchStmt`·`SwitchExpr` 의 entries 마다:
  ```java
  private static final Pattern FALL_NOTE = Pattern.compile("(?i)falls?[ -]?thr(?:u|ough)|no break");

  /** 옛 꼴(case X:) entry 가 문장이 있는데 탈출로 안 끝나고, 다음 case 사이에 fall through 주석도 없다. 마지막 entry·빈 entry(묶인 label)는 밖 */
  private static void fallThrough(List<SwitchEntry> entries, List<Comment> comments, List<Taint.Hit> out) {
      for (int i = 0; i < entries.size() - 1; i++) {
          SwitchEntry x = entries.get(i);
          if (x.getType() != SwitchEntry.Type.STATEMENT_GROUP || x.getStatements().isEmpty()) {
              continue;
          }
          Statement last = x.getStatements().getLast().orElseThrow();
          int from = last.getEnd().map(p -> p.line).orElse(1);
          int to = line(entries.get(i + 1));
          boolean noted = comments.stream().anyMatch(c -> line(c) >= from && line(c) <= to && FALL_NOTE.matcher(c.getContent()).find());
          if (!exits(last) && !noted) {
              add(out, FALL_THROUGH, x);
          }
      }
  }

  /** 끝이 탈출 — break·return·throw·continue·yield, 블록이면 그 마지막 문장, if-else 면 두 갈래 다 */
  private static boolean exits(Statement s) {
      if (s instanceof BlockStmt b) {
          return b.getStatements().getLast().filter(JavaShape::exits).isPresent();
      }
      if (s instanceof IfStmt i) {
          return i.getElseStmt().filter(JavaShape::exits).isPresent() && exits(i.getThenStmt());
      }
      return s instanceof BreakStmt || s instanceof ReturnStmt || s instanceof ThrowStmt || s instanceof ContinueStmt || s instanceof YieldStmt;
  }
  ```
  `comments = cu.getAllComments()` 한 번. 지적 줄 = 떨어지는 entry 의 label 줄. `SwitchFix` 는 안 고친다(고침 없음 — `Cleanup.RULES` 밖)
- 픽스처 `PosShape.java` — 떨어지는 case 하나 · `NegShape.java` — `// fall through` 주석 · 묶인 label(`case 1: case 2:`) · if-else 두 갈래 return · 블록 끝 break · 화살표 switch. `JavaShapeTest` 줄 단언 먼저(RED)
- 수: 규칙 146
- 골든: `check/java.json`(PosShape 한 줄). `self-check.txt` diff 0 기대(자기 코드 어림 0 — 줄이 생기면 중단 조건)

### 5-97 — `security.piiCardPlain`(Luhn) + 가리기(§3-9)
- 새 `core/check/LuhnRule.java`:
  ```java
  /** 설계 65 — kind luhn. 정규식이 잡은 숫자열 가운데 Luhn 검사를 지나는 것만 지적한다(대시 없는 카드번호). 줄마다 한 건 */
  final class LuhnRule implements Rule {
      static final String CARD_PLAIN = "security.piiCardPlain";
      private final Def def;
      private final Pattern pattern;

      LuhnRule(Def def) {
          this.def = def;
          this.pattern = def.pattern();
      }

      @Override
      public Def def() {
          return def;
      }

      @Override
      public List<Finding> apply(Source s) {
          String[] raw = s.text().split("\n", -1);
          String bare = Boolean.TRUE.equals(def.skipComments()) ? RegexRule.stripComments(s.text(), s.lang()) : s.text();
          String[] code = bare.length() == s.text().length() ? bare.split("\n", -1) : raw;
          List<Finding> out = new ArrayList<>();
          for (int i = 0; i < raw.length && i < code.length; i++) {
              Matcher m = pattern.matcher(code[i]);
              while (m.find()) {
                  if (valid(m.group())) {
                      out.add(new Finding(s.rel(), i + 1, def.group(), def.id(), def.severity(), Finding.excerpt(raw[i])));
                      break;
                  }
              }
          }
          return out;
      }

      /** Luhn — 오른쪽에서 짝수째 자리를 두 배(9 넘으면 −9)해 더한 합이 10 의 배수 */
      static boolean valid(String digits) {
          int sum = 0;
          for (int i = 0; i < digits.length(); i++) {
              int d = digits.charAt(digits.length() - 1 - i) - '0';
              if (i % 2 == 1) {
                  d = d * 2 > 9 ? d * 2 - 9 : d * 2;
              }
              sum += d;
          }
          return sum % 10 == 0;
      }
  }
  ```
  (`Def.pattern()` 은 `PiiFix:25` 가 쓰는 그것. PMD 가 수 리터럴을 지적하면 상수로)
- `RuleSet` 의 kind switch(`:166-173`)에 `case "luhn" -> new LuhnRule(d);` 한 줄 · `Rule.Def` javadoc 의 kind 목록에 `luhn(설계 65)`
- `rules.yaml` — `security.piiCard` 항목 뒤에(정규식은 Edit 도구로):
  ```yaml
  - id: security.piiCardPlain
    group: security
    severity: warn
    kind: luhn
    globs: ['*.java', '*.jsp', '*.jspf', '*.js', '*.jsx', '*.ts', '*.tsx', '*.xml', '*.properties', '*.sql', '*.html']
    regex: '(?<![\w.-])[3-6]\d{15}(?![\w.-])'
    skipComments: true
    message: 개인정보 — 카드번호 꼴 값(대시 없음, Luhn 통과)
    fix: '실제 번호면 지우고 시험 값은 가짜 번호로, 화면엔 가운데를 가린다'
  ```
  (첫 자리 3~6 = 카드 발급사 대역 — 16 자리 일련번호 오탐을 줄인다)
- `PiiFix` — 규칙 둘을 돈다. `static final String MASK_PLAIN = "************";` · `apply` 를 규칙마다 `mask(id, …)` 로:
  - `CARD` — 지금 그대로(`MASK + m.group().substring(MASK.length())`)
  - `LuhnRule.CARD_PLAIN` — 일치 가운데 `LuhnRule.valid(m.group())` 인 것만 `MASK_PLAIN + m.group().substring(12)`, 아닌 일치는 그대로 둔다(`appendReplacement` 에 원문)
  - 한 줄에 두 규칙이 다 걸리면 `CARD` 를 먼저 적용한 글에 `CARD_PLAIN` 을 이어 적용해 **한 번** `e.replace`(줄 → 글 `Map<Integer, String>` 에 모았다가 끝에 쓴다)
  - 마스크는 두 정규식에 다시 안 걸린다(`*` 뒤 숫자 넷)
- `Cleanup` — `RULES` 에 `"security.piiCardPlain"`(`"security.piiCard"` 뒤) · `OPTIONAL` 에 추가 · `clean` 의 PII 줄 `Set.of(PiiFix.CARD, LuhnRule.CARD_PLAIN)`
- 픽스처: `check/security/pos.java` 에 한 줄 `String pan = "4030000010001234";` · `check/security/neg.java` 에 한 줄 `String serial = "4030000010001235";`(Luhn 실패) · `cleanup/pii/Pos.java`·`pos.properties` 에 각 두 줄(Luhn 통과 → 가려짐 · 실패 → 그대로). 새 `LuhnRuleTest`(valid 참·거짓 · 주석 속 번호는 지적 밖 · 20 자리 숫자 안의 16 자리는 경계로 안 걸림) 먼저(RED)
- 수: 규칙 147 · `rulesListsFixable` 32 · 스모크 32·23
- 골든: `check/fixtures.json`(pos 한 줄) · `cleanup/pii-*` 둘 · `steps.json` 두 항목. `pure-rules.json` diff 0(정규식 63 불변)

### V-61a — corpus 골든 재채점(§3-10)
- 순서: `bash scripts/mvn.sh -q -DskipTests verify` rc 0 → 여유 RAM 15GB 확인 + projectshop 세션 조율(§5) → `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='CheckCorpusTest,LegacyCorpusTest,CleanupCorpusTest,CleanupCompileCorpusTest,VcsCorpusTest' -Djunit.timeout=10m -Dgolden.update=true`(**다섯 — `VcsCorpusTest` 를 빼지 않는다**) → `git diff --stat src/test/resources/golden/corpus`
- 기대(이것만): `check-*`·`legacy-*`·`check-*-hits` — 새 규칙 셋 수(`java.fallThrough` 큰 수(jspwiki) · `java.printStackTraceStream` 0~1(struts 는 legacy) · `security.piiCardPlain` shopizer 4(legacy)) · `java.interruptedIgnored` +2 안팎(union) · `cleanup-*` — skips 키가 이름 없이 합쳐짐(⑨) · `interrupt` 단계 수(egov 루프 건너뜀으로 ↓ · union ↑) · `imports` 수(jspwiki 5 → 6 — 상수 수신자) · 새 skip 키 `interrupt 루프 안 catch …`·`imports 자기 패키지 형식 목록을 못 구함 …` · `vcs-egov.json` findings(새 규칙 지적) · 다른 단계 수 불변 · 등급 A 0 · `CleanupCompileCorpusTest` 초록
- 중단선: 등급 A · javac 실패 · `imports` 단계 파일 수가 줄어듦(18 → 미만 — `OWN_UNKNOWN` 이 실물을 넓게 건너뛴다) · `syntax`·`switch`·`log`·`markup`·`value`·`local`·`resource`·`order`·`eol`·`encoding` 수 변화(⑥⑦⑧ 은 표본 0) · 「다른 지적이 생김·사라짐」 새로 생김
- 건수 전후·벽시계를 이력에

### R-28 — 손 판정(§3-11)
- CLI(`toolbox.bat cleanup <폴더> --rules <id> --json [--write --out <scratch>]`, 프로필 example, 본 뒤 지움). **고침마다 둘 다 댄다 — 더한 것이 맞나 · 빠진 것이 없나**(번들 80 C1):
  - `java.interruptedIgnored` — ruoyi·ruoyi-classic `Threads.java:29·58·89`(Allman — 고쳐지고 되돌림 0) · egov·egov-prev `EgovFileMntrg.java:267`·`BatchShellScriptJob.java:129` · jeesite `Runnable.java:102`(`IN_LOOP` 건너뜀) · commons-lang `BackgroundInitializer.java:347` · roller `RomeFeedFetcher.java:94`(union — 세 줄, 지적됐다면) · 회귀: commons-io·roller·egov·jeesite 의 번들 80 고침 16 가운데 루프 밖 것은 그대로 고쳐지나(줄 수 대조)
  - `java.starImport` — egov-msa·zheng·jeecg·jspwiki: changed 21·10·10 대비 · `--write` 결과에서 **더한 import 가 몸에서 쓰이는 이름인가** + **고침 전 글의 형식 자리 이름 가운데 고친 글에서 import·선언·java.lang 으로 댈 수 없는 것이 0 인가**(전수) · `OWN_UNKNOWN` 건수와 그 파일 셋을 열어 사유가 맞나(패키지 폴더 불일치) · jspwiki `PROPERTY_APPROVER_PREFIX` 파일이 풀렸나 · 주석 낱말로 생기던 import 가 빠졌나(번들 80 `--write` 와 diff)
  - `security.piiCardPlain` — shopizer `BeanStreamPayment.java:261·268·286·554` 마스크 `************2000`·`************1234` · 다른 파일 지적 0 인가(오탐 전수)
  - `java.printStackTraceStream` — struts `StrutsTestCaseHelper.java:89`(고쳐지거나 사유) · PrintWriter 28 줄은 지적 0
  - `java.fallThrough` — jspwiki 지적 20 곳 열어 참·오탐 판정(주석 있는 의도·탈출 판정 누락) · commons-collections·egov-msa·roller·jeecg 전수
  - 회귀 — `java.unusedLocal` jeecg·shopizer·zheng 20·14·3 · `a11y.thScope --write` egov 472·758·2,380 · `common.printStackTrace` jeecg·zheng·roller 536·84·2 · `java.needBraces` jeecg·struts·jeesite 227·159·59
- 오탐·깨진 고침이면 음성 픽스처(또는 인라인) 먼저 RED → 고침 ≤ 2 → V-61a 다시(다섯 시험). 표는 compare ㉛ 「손 판정」, `파일:줄` 만

### V-61 — compare ㉛ · 정적 분석 · `--corpus`(§3-12)
- `design/compare-2026-10-28.md` — ㉚ 「후보로」 끝에 「처분(설계 65)」 블록(S0 가 쓴다) · 끝에 `## ㉛ 재측정 — 번들 81(2026-11-12)`: 한 줄 요약 · 「손 판정」 표(R-28) · 「측정」 표(규칙 144 → 147 · `RULES` 30 → 32 · corpus steps·skips 전후) · 「골든」 · 「정적 분석·벽시계」 · 「드러난 것」 · 「후보로」(제3자 별표 · 떨어짐 고침 · PrintWriter 꼴 등 남은 것)
- 순서(따로 낸다): `bash scripts/mvn.sh -DskipTests verify` rc 0 → `bash scripts/verify.sh` → RAM·projectshop 조율 → `bash scripts/verify.sh --corpus`(20~36분) → 끝 줄이 더 부르는 레인 없음

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·7 · 행의 축·강제 지점과 diff · **고침마다 「확실한 꼴만」 인가 — union 세 줄이 catch 변수 이름·들여쓰기를 맞추나 · 루프 판정이 람다·안쪽 클래스 경계를 넘나 · `OWN_UNKNOWN` 이 넓거나 좁지 않나 · javadoc 참조만 세어 빠지는 import 가 컴파일을 깨는 길 · `CONSTANT` 가 형식 이름을 상수로 보는 길 · default 위 주석 옮기기가 앞 case 의 꼬리 주석을 끌고 가는 길 · Luhn 마스크가 다른 규칙에 걸리나** · 새 규칙 셋의 양성·음성 · 골든 갱신이 §3 목록 안인가 · 사유 글에 코드 본문이 없나) → 리뷰 고침이 `core/check` 면 재채점 다섯 + `--corpus` 다시 → `gate-probe.sh` → `verify.sh`(끝 줄) → push → PR(본문에 행마다 시작 세 줄 표) → CI `gh api` 폴링 → claude[bot] 처분 → **머지 위임 있음** → 끝 보고(사람이 할 것 — 현장 프로필에서 기본 끔 아홉 가운데 켤 것 · 화면 체크박스 32 눈 확인 · `java.fallThrough` 가 시끄러우면 프로필에서 끄기)

## 4. 실행 스텝
스텝마다 끝에 `bash scripts/mvn.sh -q -DskipTests verify` rc 0(자바를 고친 행) → `bash scripts/verify.sh` 초록 → `git commit`(따로) → `task-done`. 커밋 제목 `<종류>: <행> <무엇>`. 분할표 행 상태·이력 한 줄은 커밋 앞에(CLAUDE.md 「Task 끝」).

### S0 — 설계 문서·행·번들 표(실행 세션의 첫 손)
- 대상: `design/65-bundle81.md`(이 플랜 파일 `C:\Users\EJG\.claude\plans\vast-cuddling-rabbit.md` 를 그대로 — 끝의 「S0 부록」 절은 빼고) · `PLAN.md`(부록 B) · `PROGRESS.md`(부록 C) · `design/compare-2026-10-28.md` ㉚ 끝(부록 D)
- 검증: `bash scripts/doc-lint.sh design/65-bundle81.md` · `bash scripts/verify.sh`(docs 레인) · 커밋 `docs: S0 설계 65 — 번들 81`. push 안 함
- 의존: 없음

### 0-82 — `MarkerFix` 분리
- 대상: 새 `core/check/MarkerFix.java` · `Cleanup.java` · `golden/check/self-check.txt`(두 줄 파일 이름)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupTest,SelfCheckTest,ArchitectureTest'` 초록(`self-check.txt` 는 `-Dgolden.update=true -Dtest=SelfCheckTest` 로 뜨고 diff 가 그 두 줄뿐인지 본다) · `git diff --stat src/test/resources/golden/cleanup` 0 · `wc -l Cleanup.java` ≤ 860. 커밋 `chore: 0-82 표식 떼기를 MarkerFix 로 — Cleanup 길이 상한`(`Ruling:` 순수 이동 — 시험 추가 없음)
- 의존: S0

### 5-93a — `InterruptFix` 셋
- 대상: `InterruptFix.java` · `fixtures/cleanup/interrupt/Pos.java` · `CleanupTest.interruptStage` · 골든 둘
- 순서: 픽스처 + 단언 → RED → 고침 → GREEN → `-Dgolden.update=true -Dtest=CleanupTest`
- 검증: `-Dtest='CleanupTest,SelfCheckTest'` 초록 · 골든 diff 가 `interrupt-Pos.java.txt`·`steps.json` 그 항목뿐. 커밋 `fix: 5-93a interrupt 복원 — 루프 안 건너뜀 · Allman 빈 catch · 중괄호 뒤 주석`
- 의존: 0-82

### 5-93b — 별표 풀기 넷
- 대상: `StarImportFix.java` · `PackageTypes.java` · `CleanupTest`(도우미 둘·`OWN`·`starImportStage` 인라인 넷)
- 순서: 인라인 단언 → RED → 고침 → GREEN
- 검증: `-Dtest='CleanupTest,CleanupRunnerTest,CleanupRoutesTest,SmokeHtmlUnitTest,SelfCheckTest'` 초록 · `golden/cleanup` diff 0. 커밋 `fix: 5-93b 별표 풀기 — javadoc 참조만 · 자기 패키지 모르면 건너뜀 · 상수 수신자 · 잘린 목록`
- 의존: 5-93a

### 5-93c — `SwitchFix` 주석 여러 줄 · `SyntaxFix` 패턴 변수
- 대상: `SwitchFix.java` · `SyntaxFix.java` · 픽스처 `switch/Pos.java`·`syntax/Pos.java` · `CleanupTest` · 골든 셋
- 검증: `-Dtest='CleanupTest,SelfCheckTest'` 초록 · 골든 diff 가 `switch-Pos.java.txt`·`syntax-Pos.java.txt`·`steps.json` 두 항목뿐. 커밋 `fix: 5-93c default 위 주석 전부 옮김 · 패턴 변수 든 블록 건너뜀`
- 의존: 5-93b

### 5-93d — `LogFix.noCall` · corpus skip 키
- 대상: `LogFix.java` · `CleanupTest.stackStage` · `CleanupCorpusTest.java`
- 검증: `-Dtest='CleanupTest,SelfCheckTest'` 초록 · 단위 골든 diff 0(corpus 골든은 V-61a). 커밋 `fix: 5-93d printStackTrace 못 고침 사유 셋 · corpus 건너뜀 키에서 이름 뗌`. 이 커밋 뒤 분할표 5-93 행을 「완료 — 번들 81(5-93a~d)」 로
- 의존: 5-93c

### 5-94 — union catch interrupt
- 대상: `JavaShape.java` · `InterruptFix.java` · `core/job/JobManager.java`(꼬리 주석) · 픽스처 `PosShape`·`NegShape`·`interrupt/Pos.java` · `JavaShapeTest`·`CleanupTest` · 골든
- 순서: 픽스처 + 단언 → RED → `JavaShape` → `InterruptFix` → GREEN → 골든(`JavaRulesTest`·`CleanupTest`)
- 검증: `-Dtest='JavaShapeTest,JavaRulesTest,CleanupTest,SelfCheckTest'` 초록 · `self-check.txt`·`self-cleanup.txt` diff 0 · 골든 diff 가 `check/java.json`(PosShape)·`interrupt-Pos.java.txt`·`steps.json` 뿐. 커밋 `feat: 5-94 union catch 의 InterruptedException — 지적 + instanceof 세 줄 복원`
- 의존: 5-93a

### 5-95 — `java.printStackTraceStream`
- 대상: `rules.yaml` · `JavaShape.java` · `LogFix.java` · `Cleanup.java`(세 자리) · 픽스처 `PosShape`·`NegShape`·`log/Stack.java` · `JavaShapeTest`·`CleanupTest.stackStage` · `CheckRulesTest:169` 145 · `CleanupRoutesTest:109` 31 · `SmokeHtmlUnitTest:1885-1886` 31·23 · 골든
- 검증: `-Dtest='JavaShapeTest,JavaRulesTest,CheckRulesTest,PureRulesJsonTest,CleanupTest,CleanupRoutesTest,SmokeHtmlUnitTest,SelfCheckTest'` 초록 · `fixtures.json`·`pure-rules.json`·`self-*` diff 0. 커밋 `feat: 5-95 printStackTrace(System.out·err) — 지적 + 로거 error 로`
- 의존: 5-93d · 5-94(`JavaShape` 같은 파일)

### 5-96 — `java.fallThrough`
- 대상: `rules.yaml` · `JavaShape.java` · 픽스처 `PosShape`·`NegShape` · `JavaShapeTest` · `CheckRulesTest:169` 146 · 골든 `check/java.json`
- 검증: `-Dtest='JavaShapeTest,JavaRulesTest,CheckRulesTest,PureRulesJsonTest,SelfCheckTest'` 초록 · `self-check.txt` diff 0. 커밋 `feat: 5-96 switch 떨어짐 지적 — fall through 주석은 뺀다`
- 의존: 5-95

### 5-97 — `security.piiCardPlain`
- 대상: 새 `LuhnRule.java`·`LuhnRuleTest.java` · `RuleSet.java`(한 줄) · `Rule.java`(javadoc) · `rules.yaml` · `PiiFix.java` · `Cleanup.java`(세 자리) · 픽스처 `security/pos.java`·`neg.java`·`pii/Pos.java`·`pii/pos.properties` · `CleanupTest.piiStage` · 수 단언(147 · 32 · 32·23) · 골든
- 순서: `LuhnRuleTest` → RED → `LuhnRule`·`RuleSet`·yaml → GREEN → 픽스처·`piiStage` 단언 → RED → `PiiFix`·`Cleanup` → GREEN → 골든
- 검증: `-Dtest='LuhnRuleTest,CheckRulesTest,PureRulesJsonTest,PureCodeCheckTest,CleanupTest,CleanupRoutesTest,SmokeHtmlUnitTest,SelfCheckTest'` 초록 · `pure-rules.json` diff 0 · 골든 diff 가 `fixtures.json`(한 줄)·`pii-*`·`steps.json` 뿐. 커밋 `feat: 5-97 대시 없는 카드번호 — Luhn 지적 + 가리기`
- 의존: 5-96

### V-61a — corpus 골든 재채점
- 대상: `golden/corpus/*`(§3-10)
- 검증: 갱신 명령(다섯 시험) 초록 · diff 가 §3-10 안 · 건수·벽시계 이력 · 커밋 `test: V-61a 재채점 — 잔손 · 규칙 셋 · 고침 셋`
- 의존: 5-97

### R-28 — 손 판정
- 대상: compare ㉛ 「손 판정」 표(§3-11) + 고침(≤ 2, 음성 먼저 RED)
- 검증: 표 + 합 줄 · 고침이 있으면 V-61a 다시 · 커밋 `docs: R-28 손 판정 — 잔손 · 고침 셋 · 떨어짐 · 회귀`(고침은 따로 `fix:`)
- 의존: V-61a

### V-61 — compare ㉛ · 정적 분석 · `--corpus`
- 대상: `design/compare-2026-10-28.md`(§3-12)
- 검증: `mvn.sh -DskipTests verify` rc 0 → `verify.sh` → `verify.sh --corpus` 셋 초록(따로) · `doc-lint` · 커밋 `docs: V-61 재측정 — compare ㉛ + 정적 분석 + --corpus`
- 의존: R-28

### 마무리
§3 「마무리」 그대로. 머지 위임 있음.

## 5. 금지 사항
- `pure/`·portfolio·satchel · `core/meta`·`core/dialect`·`core/gen`·`core/analyze`·`core/vcs` · `core/job` 은 `JobManager.java:77` 꼬리 주석 한 줄만 · `LocalFiles` 불변 · `RegexRule`·`AbsentRule`·`FileRule`·`MarkupFix`·`ValueFix`·`LocalFix`·`ResourceFix`·`OrderFix`·`JavaDefects`·`JavaRules` 불변
- `Cleanup` — 메서드 추가 금지 · 0-82 뒤 변경은 `RULES`·`OPTIONAL`·`clean` 의 MARKER·LOG·PII 세 줄뿐 · `worse`·`TOUCHED_MAY_VANISH`·`MAY_APPEAR`·단계 차례 불변 · 새 단계 없음
- 규칙 — 기존 144 의 `regex`·`globs`·`message`·`flags`·`kind` 불변 · id 삭제 금지 · `pure-rules.json` diff 0 · 새 규칙은 `kind: java` 둘·`kind: luhn` 하나(정규식 kind 63 불변)
- 고침 전부 — 「확실한 꼴만」. 사유 없이 건너뛰지 않는다(사유는 상수 글 + 「줄 N: 」, 코드 본문 안 붙인다) · AST + 줄 `Edits` 만 · 같은 줄 두 번 `replace` 금지 · 들여쓰기·줄바꿈 보존
- `StarImportFix` — 정적 별표·못 푸는 패키지·겹치는 이름·출처 모르는 형식·자기 패키지 모름은 고치지 않는다 · 제3자 jar 를 읽지 않는다 · `Class.forName(name, false, 플랫폼 로더)` 만
- `java.fallThrough` 는 고치지 않는다(`Cleanup.RULES` 에 안 넣는다) · `SwitchFix.escapes` 불변
- 픽스처 — 기존 줄 삭제 금지(5-94 의 `NegShape:68` 은 몸에 한 줄 **더하기**, `interrupt/Pos.java:48` 은 주석 글만) · `neg*`·`Neg*` 에 지적이 나는 꼴 금지 · `tb-ignore` 금지 · check 픽스처 폴더에 새 파일 금지(32) · `fixtures/check/common/*` 불변
- 골든 — §3 이 적은 변화 밖 금지 · `-Dgolden.update` 는 그 시험에만 · `self-cleanup.txt`·`pure-rules.json`·`meta/*`·`deliverable/*`·`corpus/db-*`·`security-*`·`js-summary` diff 0 · `self-check.txt` 는 0-82 의 두 줄 파일 이름 밖 diff 0
- 화면 JS·`common.js`·`common.css` 불변 · `scripts/`·훅·`VerifyLanesTest` 불변
- 새 의존성 금지 · `--full`·`--db` 안 돈다 · **무거운 단계(V-61a·`--corpus`·리뷰 고침 뒤 재채점) 앞에서 멈추고 사용자에게 알린다 — projectshop 세션이 컨테이너·JVM 을 내렸다는 답을 받은 뒤 돌리고, 끝나면 재개를 알린다**(RAM 은 toolbox 우선) · 무거운 것을 겹쳐 띄우지 않는다 · `wsl --shutdown` 금지
- **Bash heredoc·python 으로 `\` 가 든 글(자바 정규식·문자열·yaml 정규식)을 쓰지 않는다 — Edit 도구로만** · python 으로 문서를 「읽고 바로 쓰기」 금지(읽기를 변수에 받은 뒤 쓴다, `git diff --numstat` 로 삭제 줄 확인)
- corpus 재채점은 **다섯 시험**(`VcsCorpusTest` 포함) — 넷으로 줄이지 않는다
- 손 판정은 「더한 것이 맞나」 와 「빠진 것이 없나」 둘 다 — 한쪽만 대고 「깨진 고침 0」 이라 쓰지 않는다

## 6. 최종 검증
- `bash scripts/mvn.sh -DskipTests verify` rc 0 · `bash scripts/verify.sh` · `bash scripts/verify.sh --corpus` 초록(따로) · 끝 줄이 더 부르는 레인 없음 · `gate-probe.sh` 전부 빨개짐
- 수: 규칙 147 · 정규식 63 · `Cleanup.RULES` 32 · `OPTIONAL` 9 · 화면 체크박스 32·켜진 23 · `/api/cleanup/rules` 32 · `Cleanup` 메서드 27·길이 ≤ 870 · 새 클래스·고친 메서드마다 복잡도 ≤ 15 · `PureCodeCheckTest` 32
- 골든: `git diff --stat origin/main -- src/test/resources/golden` 에서 번들 80 뒤 변화가 `check/java.json`(PosShape 셋) · `check/fixtures.json`(한 줄) · `check/self-check.txt`(두 줄 파일 이름) · `cleanup/steps.json` · `cleanup/interrupt-*`·`switch-*`·`syntax-*`·`log-Stack*`·`pii-*` · `corpus/check-*`·`legacy-*`·`cleanup-*`·`vcs-egov.json` 안 · 나머지 diff 0
- 수동(한 번, 이력에 한 줄): CLI 로 shopizer `BeanStreamPayment.java` 를 `--rules security.piiCardPlain --write` 해 마스크 넷 눈 확인 · ruoyi `Threads.java` Allman 결과 눈 확인
- compare ㉛ 표 + 「드러난 것」 + 「후보로」 · 이력에 건수·벽시계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- 0-82 에서 `golden/cleanup` 변화 · `self-check.txt` 가 두 줄 파일 이름 밖으로 변함 · 옮긴 상수를 `Cleanup` 의 다른 자리가 쓴다(실측과 다름)
- 5-93a 에서 기존 `interruptStage` 단언·골든의 기존 줄이 바뀜 · 「다른 지적이 사라짐」 사유
- 5-93b 에서 `starimport-*`·`javaimport-*` 골든 변화 · `CleanupRunnerTest`·스모크 빨강(실제 finder 가 픽스처 패키지를 못 찾아 `OWN_UNKNOWN` — 그 시험 입력의 폴더 꼴을 보고)
- 5-94 에서 `self-check.txt`·`self-cleanup.txt` 변화(`tb-ignore` 가 안 먹었다) · NegShape 히트
- 5-95 에서 `check/fixtures.json` 변화(`common/neg.java:11` 이 걸렸다) · `LogFix` 기존 `log-*` 골든 변화(Stack 밖)
- 5-96 에서 `self-check.txt` 에 `java.fallThrough` 줄(그 자리를 보고 — 진짜 떨어짐이면 선택지: 코드 고침 / 기준선)
- 5-97 에서 `pure-rules.json` 변화 · `PureCodeCheckTest` 빨강 · 정규식 수 63 밖
- 어느 행이든 `pmd:check`·SpotBugs 새 짝 · 메서드·복잡도·길이 상한
- V-61a 에서 §3-10 밖 골든 변화 · 중단선
- R-28 에서 고침 셋째가 필요 · 회귀 수 어긋남 · 별표 풀기에서 빠진 이름 하나라도
- V-61 에서 `--corpus` 가 이 번들 밖 원인으로 빨강(고치기 둘까지) · 여유 RAM 모자람(사용자에게)
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① corpus 의 `java.fallThrough` AST 수(줄 어림 158 — 중단선 아님, 이력에. R-28 이 참·오탐을 판정) ② union 표본 둘의 몸에 throw 가 있어 지적 밖일 수 있다(V-61a 수로 확인) ③ `OWN_UNKNOWN` 이 corpus 에서 몇 파일을 건너뛰나(중단선 — `imports` 18 미만) ④ `JavaShape.apply` 복잡도(15 넘으면 §3-7 대로 둘로) ⑤ 여유 RAM. 첫 스텝 앞 확인 명령: `git status -sb`(깨끗 · `work/2026-11-12`) · `git log --oneline -1`(`e250c008`) · `wc -l src/main/java/kr/ejg/toolbox/core/check/Cleanup.java`(991)
- **사용자만 정할 수 있는 것** — 없음(후보 넷·제3자 뺌·5-93 전부·① 건너뜀·printStackTrace 는 System.out·err 만·머지 위임은 2026-11-12 에 받았다)
