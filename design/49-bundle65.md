# 설계 49 — 번들 65: Java 결함 묶음 열하나 (Fable 설계 · Opus 실행, 2026-11-01)

## 맥락
compare ① 가-1·2·4·7·8·9·11·12·15·16 과 ④ 라-13 이 「후보 행 제안」 에 「`JavaRules` · `rules.yaml` java 묶음 · 상」 으로 올라 있고(설계 41), 번들 표 「번들 65」 행이 요지만 들고 있다(2026-10-31). 번들 66(자원 해제 + 코드 정리 고침 — `==` → equals · `"YYYY"` → `"yyyy"` · `BigDecimal.valueOf` · try-with-resources)이 이 번들의 지적 규칙을 짝으로 쓴다 — **65 는 검출만, 고침은 66**.

탐색 실측(2026-11-01, `bb740a8`):
- **기존 판정과 겹침** — `rules.yaml`·`JavaRules`·`Taint`·`SecurityAst`·`Cleanup` 어디에도 Transactional·ThreadLocal·SimpleDateFormat·BigDecimal·finally·`YYYY`·`==` 판정이 없다(grep 0). 가까운 것은 `java.emptyCatch`(빈 catch)·`java.swallow`(ServiceImpl 안 삼킴)뿐 — lostCause 와 자리는 겹칠 수 있어도 판정이 다르다(둘 다 `throw new` 가 있으면 swallow 는 안 난다). 규칙마다 「없음」.

| 규칙 | JavaRules | rules.yaml java 묶음 | Taint·SecurityAst | Cleanup(번들 66 몫) |
|---|---|---|---|---|
| finally 안 return·break·continue / throw | 없음 | 없음 | 없음 | 없음 |
| 원 예외 잃음 | 없음(`java.swallow` 는 다른 판정) | 없음 | 없음 | 없음 |
| `@Transactional` rollbackFor 없음 / 같은 클래스 호출 / private | 없음 | 없음 | 없음 | 없음 |
| ThreadLocal remove 없음 | 없음 | 없음 | 없음 | 없음 |
| static SimpleDateFormat | 없음 | 없음 | 없음 | 없음 |
| 래퍼·문자열 `==` | 없음 | 없음 | 없음 | 없음(66 라-6 이 고친다) |
| `new BigDecimal(double)` | 없음 | 없음 | 없음 | 없음(66 라-5) |
| `"YYYY"` | 없음 | 없음 | 없음 | 없음(66 라-13) |

- **표본 grep 추정**(파이썬 정규식 + 균형 괄호, 주석 뗌, `/test/` 제외. `건수/파일`. AST 판정은 이보다 좁다 — 특히 `==` 는 양쪽 선언 형식을 보고, 같은 클래스 호출은 호출자 애너테이션을 본다)

| 표본 | finally return | finally throw | 원 예외 잃음 | rollbackFor 없음 | 같은 클래스 tx 호출 | private tx | ThreadLocal | static SDF | `==` | BigDecimal(double) | `YYYY` |
|---|---|---|---|---|---|---|---|---|---|---|---|
| jeecg | 0 | 0 | 18/10 | 44/43(전부 클래스 머리, readOnly 메서드 63 제외) | 0 | 0 | 1/1 | 8/2 | 41/14(문자열 리터럴 25 · 형식 16) | 0 | 0 |
| jeesite | 0 | 0 | 0 | 66/23(`readOnly = false` 꼴) | 0 | 0 | 1/1 | 2/1 | ≤6/1 | 0 | 0 |
| zheng | 0 | 0 | 1/1 | 38/38(클래스 머리) | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| ruoyi-classic | 0 | 0 | 4/4 | 18/6 | 0 | 0 | 0 | 0 | ≤4/4 | 1/1 | 0 |
| commons 셋 | 0 | 0 | 14/11 | 0 | 0 | 0 | ≤2 | 0 | ≤30/11 | 0 | 0 |
| egov(= egov-prev) | 2/2(하나는 루프 안 break — AST 는 1) | 1/1 | 12/8 | 0(XML tx) | 0 | 0 | 0 | 0 | ≤6/4 | 0 | 0 |
| egov-msa(CLI) | 0 | 0 | 29/12 | 60/23 | 0(호출자도 전부 `@Transactional` 같은 글) | 0 | 0 | 0 | ≤1 | 0 | 0 |
| shopizer(CLI) | 0 | 0 | 27/22 | 8/2 | 3/1(`noRollbackFor` 메서드를 민 `@Transactional` 셋이 부른다) | 2/1 | 0 | 0 | ≤1 | 19/8 | 0 |
| struts(CLI) | 0 | 0 | 11/10 | 0 | 0 | 0 | 1/1 | 0 | ≤5/5 | 0 | 0 |
| jspwiki · roller · egov-react | 0 | 0 | 8/6 · 15/7 · 0 | 0 | 0 | 0 | 0 | 0 | 0 · ≤2 · 0 | 0 | 0 |

대표 자리(코드 본문 없이) — finally return: egov `com/utl/sys/nsm/service/NtwrkSvcMntrngChecker.java:57`(finally 안 catch 의 return) · finally throw: egov `com/utl/sys/srm/example/EgovServerResrceMntrngClient.java:73`(finally 안 catch 의 throw new) · 원 예외 잃음: jeecg `core/util/JSONHelper.java:397` · egov `com/cmm/service/FileSystemUtils.java:378` · commons-collections `CollectionUtils.java:1036` · roller `business/MailProvider.java:81` · rollbackFor: jeecg `demo/service/impl/JeecgDemoServiceImpl.java:24`(클래스) · jeesite `common/service/CrudService.java:72`(`readOnly = false`) · ruoyi-classic `generator/service/impl/GenTableServiceImpl.java:125` · 같은 클래스 호출: shopizer `services/shoppingcart/ShoppingCartServiceImpl.java:85·140·191` · private tx: 같은 파일 `:230·292` · ThreadLocal: jeecg `core/extend/hqlsearch/HqlGenerateUtil.java:51`(set 있음·remove 없음) · jeesite `modules/sys/interceptor/LogInterceptor.java`(NamedThreadLocal) · struts `inject/Scope.java:88` · static SDF: jeecg `core/util/DateUtils.java:24~`(8 필드) · jeesite `activiti/…/BpmnJsonConverter.java:85` · `==`: jeecg `demo/controller/JeecgFormDemoController.java:713`(`!= ""`) · `hibernate/qbc/CriteriaQuery.java:430` · commons-io `IOCase.java:173`(String 둘) · BigDecimal: ruoyi-classic `common/core/text/Convert.java:986`(double 매개변수) · shopizer `shipping/impl/DefaultPackagingImpl.java:95`. 표본 Java 에 `YY` 가 든 글자 리터럴은 0 — `java.weekYear` 는 픽스처로만 잰다.
- **grep 이 틀린 자리**(AST 설계에 반영) — jeesite `IdcardUtils.java:486` `Integer == int 상수` 는 언박싱이라 결함 아님(→ 양쪽 다 참조형일 때만) · egov `ProxyServer.java:139` break 는 finally 안 루프의 break(→ finally 안 루프·switch 가 감싸면 제외) · egov-msa 같은 클래스 호출 14 는 호출자도 `@Transactional`(→ 호출자가 무트랜잭션이거나 애너테이션 글이 다를 때만) · shopizer `noRollbackFor` 를 `rollbackFor` 로 셌다(→ 짝 이름 정확히) · commons-io `ThreadLocal.withInitial` 캐시(→ `set` 이 있어야 지적).
- **`JavaRules`** 654줄 · 메서드 30(`metric.classMethods` 한계) · `apply(1)` 19·`type(2)` 26·`orderViolations(2)` 17 기준선(`golden/check/self-check.txt:46~49`). PMD 기준선 `JavaRules=…CompareObjectsWithEquals…` — 새 클래스는 기준선이 없으니 노드 동일성 비교에 `==` 를 못 쓴다. **새 메서드·분기 금지** → 새 클래스 셋, `taint(cu, c)` 몸에 for 한 줄씩(설계 48 의 `SecurityAst` 배선과 같은 꼴, `:234`).
- **픽스처 자리** — `JavaRulesTest.fixturesGolden` 이 `fixtures/check/java/`(25 파일, 수 고정 없음 — `PureCodeCheckTest` 32 는 `common·jsp·tsx·security·a11y` 만) 전부를 java·metric 묶음으로 돌려 「`Neg*` 히트 0 · 켜진 java·metric kind java 규칙 전부 `Pos*` 에서 한 번 이상」 을 단언하고 `check/java.json`(63 행)을 골든으로 든다 — **새 java 규칙은 양성이 없으면 이 시험이 RED**(security 와 달리 kind java 를 센다). 그래도 줄 단위 단언은 `SecurityAstTest` 꼴 직접 시험으로. `metric` 묶음도 켜져 있다(`ONLY_JAVA` 에 없음 → 켬) — 새 픽스처가 `metric.*`·`java.declarationOrder`·`java.emptyCatch`·`java.unusedImport` 에 안 걸리게 쓴다(`profileNaming` 이 `java.naming` 파일 목록 셋을 고정 — 새 픽스처에 `@Service`·`@Controller`·`@Repository` 금지). `link/` 는 건너뛴다. `CheckRoutesTest`·`CleanupOrderTest`·`TaintTest` 는 이름을 짚어 쓰므로 무관.
- **수** — `CheckRulesTest:167` 내장 118 → 129 · `:168` security 44 그대로 · `:175` `fix` ≤ 60자 · java 묶음 13 → 24.
- **재채점 골든** — `LegacyCorpusTest`·`CheckCorpusTest`(전 묶음 → `check-<표본>.json` 규칙별 count·files + `-hits.txt` 히트 파일 목록 — 새 규칙 키가 생기고 hits 에 파일이 더해질 수 있다) · `VcsCorpusTest`(`vcs-egov.json` `fullFindings` 6705·`findings` 944 — egov 지적이 늘면 바뀐다) · `CliCorpusTest`(CLI = 엔진 대조, 골든 아님) · `JulietCorpusTest`(security·java 묶음을 켜지만 **MAP 의 security 규칙만 센다** → diff 0) · `SecurityBenchmarkCorpusTest`(`security.` 만 → diff 0) · `SqlRuleCasesCorpusTest`·`SqlFixtureCorpusTest`(SQL 불변 → diff 0). shopizer·egov-msa·struts·egov-prev 는 check 골든이 없다 → CLI 로만.
- **레인** — `core/check`·픽스처·골든 전부 corpus 레인 → push 앞 `--corpus`(약 16~20분, Docker 안 씀). `core/gen`·pom·MANIFEST 불변 → `--db` 안 돈다.
- **함정(실행자에게)** — Bash heredoc 이 `\\` 를 `\` 로 벗긴다(실측: 홑 `\d` 는 남는다) — 자바 글자 리터럴·정규식은 Edit 로 · `verify.sh` 와 `git commit` 은 따로 · 새 클래스는 PMD 기준선에 없다 — `CompareObjectsWithEquals`(노드 `==` 금지 → `Range` 비교) · `AvoidLiteralsInIfCondition` · `AvoidFieldNameMatchingMethodName` · `LiteralsFirstInComparisons` · `LooseCoupling`(`Set`·`List`·`Map` 선언) · `UseLocaleWithCaseConversions`(`toLowerCase(Locale.ROOT)`) · `AvoidReassigningLoopVariables` · 시험 `AvoidDuplicateLiterals`(같은 글자 넷 이상은 상수) · 복잡도 ≤ 15·길이 ≤ 100·중첩 ≤ 4·클래스 메서드 ≤ 30(`self-check.txt` 새 키 금지).

## 사용자 결정(2026-11-01)
- (가) 심각도 — **확정 다섯 error**(`java.finallyReturn`·`java.txPrivate`·`java.staticDateFormat`·`java.bigDecimalDouble`·`java.weekYear`) · **여섯 warn**(`java.finallyThrow`·`java.lostCause`·`java.txRollbackFor`·`java.txSelfCall`·`java.threadLocalRemove`·`java.refEquals`) — 설계 48 의 「쓰는 순간 → error, 봐야 안다 → warn」 기준
- (나) 열하나 중 뺄 것 — **없음, 다 넣는다**(`weekYear` 는 픽스처로만, `txSelfCall` 은 shopizer 셋)
- (다) 구현 행 쪼개기 — **셋, 클래스별** `5-59a`(JavaDefects) → `5-59b`(ValueAst) → `5-59c`(TxAst)
- (라) **머지 위임 있음**
- 설계가 정한 것(사용자가 안 물은 자리) — `readOnly = true` 인 `@Transactional` 은 rollbackFor 지적에서 뺀다 · `rollbackFor`·`rollbackForClassName`·`rollbackOn`(JTA) 짝 이름이 있으면 있음 · private 과 함께 **static** 메서드의 `@Transactional` 도 무시되므로 같이 잡는다 · 같은 클래스 호출은 호출자가 `@Transactional` 이 아니거나 두 애너테이션 글이 다를 때만 · ThreadLocal 은 같은 파일에 `set` 이 있고 `remove` 가 없을 때만(`withInitial` 캐시 제외) · `==` 는 양쪽이 다 String·래퍼로 풀릴 때(또는 한쪽이 문자열 리터럴) · `new BigDecimal` 첫 인자가 double·float 리터럴·변수·`doubleValue()` 류 · `YYYY` 는 날짜 형식 자리(SimpleDateFormat·ofPattern·format/parse 류 호출 인자, 같은 파일 상수 한 홉)의 리터럴만 · `java.weekYear` 는 표본 0 이라 픽스처로만 · `@RequestMapping` private(가-9 뒷부분)은 안 한다(요지 밖 — 「후보로」) · KISA·CWE 는 yaml 주석 후보만, `ref:` 칸은 p3c·Sonar 규칙 id(`java.declarationOrder` 의 Checkstyle 전례)
- 설계 41~48 그대로(에이전트 최소 · 검증 인프라 최적화 금지 · 표본 추가 없음 · 새 화면 없음 · 순수본 불변 — kind java 라 `pure-rules.json` 안 바뀐다)

실행자는 Opus. 차례 `S0` → `5-59a` → `5-59b` → `5-59c` → `V-45a` → `R-14` → `V-45` → 마무리. 가지 `work/2026-11-01`(닫기 `bb740a8` 로컬, 같은 PR). 번들 하나·PR 하나.

---

## 1. 목표와 범위
- 한 문장: 코드 검사 java 묶음이 `.java` 에서 Java 결함 열하나 — finally 안 탈출(return·break·continue) · finally 안 throw · 원 예외를 안 쓰고 새 예외 던짐 · `@Transactional` rollbackFor 없음 · 같은 클래스 트랜잭션 메서드 직접 호출 · private·static 메서드의 `@Transactional` · set 만 있는 ThreadLocal · static SimpleDateFormat·DateFormat 필드 · String·래퍼 `==` · `new BigDecimal(double)` · 날짜 형식 `YYYY` — 를 파일·줄로 지적한다.
- 완료 시 체감: 코드 검사에 jeecg 를 넣으면 `core/util/DateUtils.java` 에 static SimpleDateFormat 8 건, `JSONHelper.java` 에 원 예외 잃음 3 건, `demo/service/impl/*ServiceImpl.java` 머리마다 rollbackFor 없음이 나온다(화면 변경 없음 — 규칙은 yaml 에서 읽힌다). compare ⑮ 에 규칙 열하나 × 표본 표.
- In scope: 신설 `core/check/JavaDefects.java`(예외·동시성 다섯) · `core/check/ValueAst.java`(값·비교 셋) · `core/check/TxAst.java`(트랜잭션 셋) · `core/check/JavaRules.java`(`taint` 몸 for 셋 + javadoc) · `resources/check/rules.yaml`(java 묶음 열하나) · 픽스처 `fixtures/check/java/PosDefects.java`·`NegDefects.java`·`PosValue.java`·`NegValue.java`·`PosTx.java`·`NegTx.java` · 시험 `JavaDefectsTest`·`ValueAstTest`·`TxAstTest` · `CheckRulesTest`(129) · 골든 `check/java.json` · corpus 골든(위 목록) · `design/compare-2026-10-28.md` ⑮ · PLAN 9장 `core.check` 구절 · 분할표·번들 표 · PROGRESS.
- Out of scope: 고침(번들 66 — `Cleanup` 불변) · `@RequestMapping` private · `catch (Throwable)`(가-3) · 중복 로그(가-5) · `equals` 상수 앞세우기(가-14 — 66) · float·double `==`(가-17) · `DecimalFormat`·`MessageFormat` static · `propagation` 판정 · 인터페이스 기본 메서드의 tx · 타입 해석(심볼 솔버) · `.jsp` 안 자바 · 표본 추가 · 새 화면·순수본 · `core/gen`·pom · 레인 스크립트.

## 2. 현재 구조 요약
「맥락」 실측에 더해:

**`JavaRules`** — `apply(Source)`(`:128`): 파싱 → `imports` → `taint(cu, c)`(`:228`) → 타입마다 `type` → catch·order·metric·testNoAssert 분기. `taint` 몸은 `link` 요약 for 하나 + `SecurityAst.apply(cu, on.keySet())` for 하나(`:234~236`). `add(c, rule, node)`(`:275`)는 `on.containsKey(rule)` 일 때만 `finding(rule, s, line(node), 원문 줄 발췌)`. `Taint.Hit(String rule, Node node)` 는 같은 패키지 record(`Taint.java:44`). `on` 은 켜진 kind java 정의 전부(`RuleSet.javaDefs()` `:240`).

**`SecurityAst`**(`:39~278`) — 새 클래스의 본보기: `final class`, private 생성자, `static List<Taint.Hit> apply(CompilationUnit cu, Set<String> on)`, 안 켜진 규칙은 안 돈다, 끝에 줄·규칙 정렬, 글자 상수 `private static final`, `lastName(Expression)`(`NameExpr`·`FieldAccessExpr` 의 이름), `literal(e, bool)`.

**`rules.yaml`** — ㄴ Java 구조(`:748~844`): `java.unusedImport` … `java.declarationOrder`(`:830`, `ref:` 있음) · `java.parseError`(`:839~844`, fix 없음) · 빈 줄 · ㄷ MyBatis(`:846`). 새 열하나는 `java.declarationOrder` 뒤·`java.parseError` 앞(`:839` 자리)에 「설계 49」 구역으로.

**`JavaRulesTest`**(`:27~137`) — `ONLY_JAVA`(common·jsp·tsx·file·security·mybatis 끔, java 켬 → metric·a11y 는 기본 켬) · `run()` 이 `fixtures/check/java` 를 정렬해 돈다(`link/` 제외) · `fixturesGolden` 「음성 히트 0 · 양성에서 안 걸린 규칙 0(java·metric kind java, classLength 제외) · `check/java.json`」 · `profileNaming` 은 naming 지적 파일이 `NegController·NegParams·PosController` 셋 · `declarationOrderIgnoreModifiers` 골든 `java-order-ignore.json`(ORDER 행만 — 새 픽스처 ORDER 0 이면 불변).

**`SecurityAstTest`**(`:14~61`) — `new JavaSource().parse(code).getResult().orElseThrow()` → `SecurityAst.apply(cu, ON)` → `"줄 규칙"` 목록 단언. 픽스처 파일 단언 둘 + 인라인 셋.

**corpus 시험** — `CheckCorpusTest.rules(false)`(전 묶음, spring) · `LegacyCorpusTest`(같은 규칙, 레거시 넷, `.parseError` 는 등급 A) · `VcsCorpusTest.egovRules()` · `JulietCorpusTest.securityOnly()`(security·java 켬, 센 건 MAP 규칙만) · `SecurityBenchmarkCorpusTest`(`security.` 만).

**함정** — ① `JavaRules` 메서드 30·`apply` 19 ② 새 픽스처는 `Neg*` 가 java·metric 전부 0 이어야 한다 — 빈 catch(주석만도 빈 것)·선언 순서(static 필드 → 인스턴스 필드 → 생성자 → 메서드, 같은 종류 안 public → protected → package → private)·안 쓰는 import·`*` import 금지 ③ `Pos*` 도 다른 규칙 지적 0 을 목표로(나오면 골든에 들 뿐 실패는 아니다) ④ JavaParser 는 해석을 안 한다 — 형식은 선언 글로만, `@Transactional` 은 단순 이름·FQN 다 identifier `Transactional` ⑤ `Node.equals` 는 구조 비교, `==` 는 PMD — 노드 동일성은 `a.getRange().equals(b.getRange())` ⑥ `fix` ≤ 60자 ⑦ `VcsCorpusTest` 를 재채점에 넣는다 ⑧ 재채점 기대 밖 골든(juliet·benchmark·sqlrules·sqltables·sqlfixture·analyze-*)이 바뀌면 중단.

## 3. 설계 결정 — 전부 [고정]

공통(세 클래스) — `final class`, private 생성자, `static List<Taint.Hit> apply(CompilationUnit cu, Set<String> on)`: 켜진 규칙만 돌고 끝에 `line`·규칙 정렬(`SecurityAst.apply` 와 같은 꼴). 같은 노드·같은 규칙은 한 건(리스트에 넣기 전 `Range` 로 중복 확인). 람다·익명 클래스·지역 클래스 몸은 「그 자리의 문맥 밖」 으로 본다 — 탈출·던짐 판정에서 `LambdaExpr`·익명 몸(`ObjectCreationExpr.getAnonymousClassBody`)·`LocalClassDeclarationStmt` 가 사이에 끼면 제외. 도우미 `same(Node a, Node b)` = `a.getRange().equals(b.getRange())` · `simpleType(Type t)` = `t.asString()` 에서 `<…>` 와 `[]` 를 떼고 마지막 `.` 뒤 · `lastName(Expression)` 은 `SecurityAst` 와 같은 뜻(각 클래스에 private 로 — 30줄 미만이라 `metric.duplicate` 무관).

### JavaDefects(5-59a) — 예외·동시성 다섯
1. 상수 — `FINALLY_RETURN = "java.finallyReturn"` · `FINALLY_THROW = "java.finallyThrow"` · `LOST_CAUSE = "java.lostCause"` · `THREAD_LOCAL = "java.threadLocalRemove"` · `STATIC_DATE_FORMAT = "java.staticDateFormat"`.
2. **finallyReturn** — `cu.findAll(TryStmt.class)` 의 `getFinallyBlock()` F 마다 `F.findAll(Statement.class)` 중 `ReturnStmt`·`BreakStmt`·`ContinueStmt` s: s 에서 F 까지 부모를 올라가며(`same` 으로 F 에 닿으면 멈춤) `LambdaExpr`·익명 몸·`LocalClassDeclarationStmt`·`ClassOrInterfaceDeclaration` 을 만나면 제외 · `BreakStmt`·`ContinueStmt` 는 `ForStmt`·`ForEachStmt`·`WhileStmt`·`DoStmt`(break 는 `SwitchStmt`·`SwitchEntry` 도)를 만나면 제외(F 안 루프의 탈출은 결함 아님 — egov `ProxyServer:139`). 라벨 있는 break·continue 도 같은 판정. 남으면 Hit(FINALLY_RETURN, s).
3. **finallyThrow** — 같은 F 의 `ThrowStmt` s: 2 와 같은 제외(람다·익명·지역 클래스) + F 안의 다른 `TryStmt` t2 의 **try 블록**(`t2.getTryBlock()`) 안이고 t2 에 catch 가 하나 이상이면 제외(그 자리에서 잡힌 것으로 친다 — 형식은 모른다). t2 의 **catch 블록** 안 throw 는 지적(egov `EgovServerResrceMntrngClient:73`). Hit(FINALLY_THROW, s).
4. **lostCause** — `cu.findAll(CatchClause.class)` cc 마다 매개변수 이름 v(다중 catch 도 하나), 몸 B: `B.findAll(ThrowStmt.class)` 중 식이 `ObjectCreationExpr`(`throw new X(…)`)이고 람다·익명·지역 클래스 안이 아닌 s 에 대해, **B 전체**에 `NameExpr` 이름 v 가 하나도 없으면 Hit(LOST_CAUSE, s). v 가 어디든 쓰이면(cause 인자·`e.getMessage()`·로그 인자·`throw e`) 지적 없음(Sonar S1166 과 같은 관대함). `throw new X()` 인자 없음도 v 미사용이면 지적.
5. **threadLocalRemove** — `cu.findAll(FieldDeclaration.class)` f 중 `simpleType(f.getElementType())` 이 `ThreadLocal` 로 끝나는 것(`ThreadLocal`·`InheritableThreadLocal`·`NamedThreadLocal`), 변수 n 마다: `cu.findAll(MethodCallExpr.class)` 에서 scope 의 `lastName` 이 n 인 호출 가운데 이름 `set` 이 있고 이름 `remove` 가 없으면 Hit(THREAD_LOCAL, 그 `VariableDeclarator`). scope 없는 `set(`·`remove(` 는 안 센다(다른 객체). 익명 클래스 안 필드도 `findAll` 에 든다(struts `Scope:88`). `withInitial`·`initialValue` 캐시(get 만)는 지적 없음(commons-io).
6. **staticDateFormat** — `FieldDeclaration f` 가 `isStatic()` 이고 `simpleType(f.getElementType())` ∈ `DATE_FORMATS = {SimpleDateFormat, DateFormat}` 이면 변수마다 Hit(STATIC_DATE_FORMAT, `VariableDeclarator`). `ThreadLocal<SimpleDateFormat>`·`FastDateFormat`·`DateTimeFormatter`·인스턴스 필드는 아니다. 초기값은 안 본다(선언 형식만 — 가-12 「필드 선언만 보면 된다」).
7. 메서드 열둘 안팎(`apply`·`finallyJumps`·`finallyThrows`·`escapes`·`loopBetween`·`caughtInside`·`lostCause`·`threadLocals`·`scopeCall`·`staticDateFormats`·`same`·`simpleType`·`lastName`·`line`) — 전부 복잡도 ≤ 15·길이 ≤ 100.

### ValueAst(5-59b) — 값·비교 셋
8. 상수 — `REF_EQUALS = "java.refEquals"` · `BIG_DECIMAL = "java.bigDecimalDouble"` · `WEEK_YEAR = "java.weekYear"` · `BOXED = Set.of("String", "Integer", "Long", "Short", "Byte", "Character", "Boolean", "Double", "Float", "BigDecimal", "BigInteger")` · `FLOATING = Set.of("double", "float", "Double", "Float")` · `TO_DOUBLE = Set.of("doubleValue", "floatValue", "parseDouble", "parseFloat", "nextDouble", "nextFloat")`.
9. **선언 형식 찾기 `declaredType(NameExpr n)`** — 이름 x 를 n 의 조상에서 차례로 찾는다: `LambdaExpr` 매개변수(형식 글이 있을 때만) → `CallableDeclaration`(메서드·생성자) 매개변수 → 조상 `BlockStmt`·`ForStmt`·`ForEachStmt`·`CatchClause`·`TryStmt`(자원) 안 `VariableDeclarator`·`Parameter`(선언 위치 전후는 안 본다) → 가장 가까운 `ClassOrInterfaceDeclaration` 의 `getFieldByName(x)`. 찾으면 `simpleType`, 못 찾으면 null. `this.x`(`FieldAccessExpr` scope 가 `ThisExpr`)도 x 로 푼다. 그 밖 식(호출·다른 객체 필드)은 null.
10. **refEquals** — `cu.findAll(BinaryExpr.class)` 중 연산자 `EQUALS`·`NOT_EQUALS`, 양쪽 `EnclosedExpr` 벗김: 한쪽이 `NullLiteralExpr` 이면 지적 없음. (ⅰ) 한쪽이 `StringLiteralExpr` 이면 Hit(REF_EQUALS, b)(다른 쪽 불문 — `"a" == "b"` 도). (ⅱ) 아니면 양쪽이 각각 `NameExpr` 또는 `this.x` 이고 `declaredType` 이 **둘 다** `BOXED` 에 들 때 Hit. 한쪽이라도 못 풀거나 원시형이면 없음(jeesite `Integer == int` 상수 · commons-lang `int == int` 제외). `a == b || a.equals(b)` 관용구도 지적(R-14 가 오탐으로 판정하면 고침 ①: `||` 의 다른 쪽이 같은 두 이름의 `equals` 호출이면 제외).
11. **bigDecimalDouble** — `cu.findAll(ObjectCreationExpr.class)` 중 `getType().getNameAsString()` 이 `BigDecimal` 이고 인자 ≥ 1: 첫 인자 a(`EnclosedExpr` 벗김)가 `DoubleLiteralExpr` → Hit · `CastExpr` 로 형식이 `FLOATING` → Hit · `NameExpr`/`this.x` 의 `declaredType` ∈ `FLOATING` → Hit · `MethodCallExpr` 이름 ∈ `TO_DOUBLE` → Hit · `UnaryExpr`(부호) 안은 벗겨서 같은 판정. 그 밖(정수 리터럴·문자열·long·`BigInteger`·못 푼 이름) 없음. Hit(BIG_DECIMAL, 그 `ObjectCreationExpr`).
12. **weekYear** — `cu.findAll(StringLiteralExpr.class)` s 의 값 v 가 `WEEK_YEAR_PATTERN = Pattern.compile("^[A-Za-z0-9 '.,:/_-]{1,40}$")` 에 맞고 `Y` 를 포함하고 `w` 를 포함하지 않고(`M` 또는 `d` 를 포함) → **자리** 판정: s 의 부모(또는 `NameExpr` 상수 경유 — 아래)가 `ObjectCreationExpr`·`MethodCallExpr` 이고 그 형식 이름·메서드 이름·scope `lastName` 을 소문자(`Locale.ROOT`)로 이은 글에 `format`·`pattern`·`parse`·`date` 중 하나가 들어 있으면 Hit(WEEK_YEAR, s). 상수 한 홉: 그런 호출의 `NameExpr` 인자 x 가 같은 CU 의 `static final String` 필드(`getFieldByName`)이고 초기값이 위 조건의 `StringLiteralExpr` 이면 Hit(WEEK_YEAR, 그 리터럴 — 필드 줄). 한 리터럴은 한 건(`Range` 중복 제거). 라벨·메시지 글(`"YYYY-MM-DD 로 입력"` — 한글이 들어 패턴 글자 집합 밖 · 대입문 — 호출 인자 아님)은 지적 없음.
13. 메서드 열둘 안팎(`apply`·`refEquals`·`sides`·`isBoxed`·`declaredType`·`lookupBlock`·`bigDecimals`·`floating`·`weekYears`·`patternLike`·`formatSlot`·`constantLiteral`·`same`·`simpleType`·`lastName`·`line`) — 넘칠 듯하면 `declaredType`·`lookupBlock`·`simpleType` 을 package-private 유틸 `AstTypes`(core/check, 새 파일 하나 더)로 빼도 된다(메서드 30 한계 때문일 때만).

### TxAst(5-59c) — 트랜잭션 셋
14. 상수 — `TX_ROLLBACK = "java.txRollbackFor"` · `TX_SELF = "java.txSelfCall"` · `TX_PRIVATE = "java.txPrivate"` · `TRANSACTIONAL = "Transactional"` · `ROLLBACK_KEYS = Set.of("rollbackFor", "rollbackForClassName", "rollbackOn")` · `READ_ONLY = "readOnly"`.
15. `tx(NodeWithAnnotations<?> d)` = `d.getAnnotationByName(TRANSACTIONAL)`(단순 이름·FQN 다 identifier 로 맞는다). `pairs(AnnotationExpr a)` = `NormalAnnotationExpr` 이면 이름 → 값 식 Map, 그 밖(`Marker`·`SingleMember`)은 빈 Map. `text(a)` = `a.toString().replaceAll("\\s+", "")`(애너테이션 글 비교용).
16. **txRollbackFor** — `cu.findAll(BodyDeclaration.class)`(클래스·인터페이스·메서드) 중 `tx` 가 있는 d: pairs 에 `ROLLBACK_KEYS` 가 하나도 없고, `readOnly` 값이 `true` 리터럴(`BooleanLiteralExpr`)이 아니면 Hit(TX_ROLLBACK, 그 애너테이션). `readOnly = false`(jeesite)·마커 `@Transactional`·`noRollbackFor` 만(shopizer `:230`)은 지적. 클래스 머리도 한 건(jeecg 44).
17. **txPrivate** — `MethodDeclaration m` 에 `tx` 가 있고 `m.isPrivate() || m.isStatic()` 이면 Hit(TX_PRIVATE, 애너테이션). 16 과 같은 줄에 둘이 날 수 있다(허용 — 다른 규칙).
18. **txSelfCall** — `cu.findAll(ClassOrInterfaceDeclaration.class)` c 중 인터페이스가 아니고 `tx(c)` 가 **없는** 것: `txText` = c 의 직접 메서드(`c.getMethods()`) 중 `tx` 있는 것의 이름 → `text(애너테이션)`(이름이 겹치면 첫 것). c 의 직접 메서드 caller 마다(`callerText` = `tx(caller)` 의 text 또는 null) 몸의 `MethodCallExpr` call 중 scope 가 없거나 `ThisExpr` 이고 이름 ∈ `txText` 키이고 이름 ≠ caller 이름(재귀 제외)인 것: `callerText == null || !callerText.equals(txText.get(이름))` 이면 Hit(TX_SELF, call). 람다·익명 안 호출도 센다(같은 스레드). 오버로드는 이름으로 뭉뚱그린다. 클래스 머리 `@Transactional` 이 있으면 그 클래스는 통째 제외(public 메서드 전부 같은 트랜잭션).
19. 메서드 열 안팎(`apply`·`tx`·`pairs`·`text`·`rollbacks`·`privates`·`selfCalls`·`txMethods`·`selfCall`·`line`) — 복잡도 ≤ 15.

### JavaRules 배선(행마다 한 줄)
20. `taint(cu, c)` 몸 끝에 행마다 `for (Taint.Hit h : <클래스>.apply(cu, on.keySet())) { add(c, h.rule(), h.node()); }` 한 덩이씩(5-59a `JavaDefects` → 5-59b `ValueAst` → 5-59c `TxAst`). javadoc 에 「+ Java 결함 열하나(설계 49, {@link JavaDefects}·{@link ValueAst}·{@link TxAst})」. **메서드 추가 없음 · `apply` 분기 추가 없음** — `self-check.txt` 의 `JavaRules` 네 줄 불변(`taint` 는 기준선 키가 없고 복잡도 6 이하).

### rules.yaml(행마다 자기 규칙) — `java.declarationOrder` 뒤 · `java.parseError` 앞
21. 머리 주석 `# ---- 설계 49 — Java 결함 열하나(2026-11-01). JavaDefects·ValueAst·TxAst 가 본다(JavaParser, 해석 없음). ref 는 p3c·Sonar 규칙 id, KISA·CWE 는 주석 후보 — 확정은 사람 몫`. 전부 `group: java` · `kind: java` · `globs: ['*.java']`. 심각도는 「사용자 결정」 (가) — 아래 표의 열. id·message·fix·ref(초안 — fix 는 60자 안, 넘으면 뜻 유지하고 줄인다). yaml 의 `message` 에 `@`·`:` 가 들면 따옴표로 감싼다:

| id | severity | message | fix | ref | 주석 후보 |
|---|---|---|---|---|---|
| `java.finallyReturn` | error | finally 안 return·break·continue — try·catch 의 예외를 삼킨다 | `'finally 는 정리만 — 탈출문은 try·catch 로 옮긴다'` | `'p3c AvoidReturnInFinallyRule · Sonar S1143'` | KISA 4절 오류 처리 |
| `java.finallyThrow` | warn | finally 안 throw — 진행 중이던 예외를 덮어쓴다 | `'finally 안에서는 잡아서 로그만 남기고 던지지 않는다'` | `'Sonar S1163'` | 〃 |
| `java.lostCause` | warn | catch 가 원 예외를 안 쓰고 새 예외를 던진다 — 원인이 사라진다 | `'새 예외에 원 예외를 cause 로 넘기거나 먼저 로그를 남긴다'` | `'Sonar S1166 · p3c 예외 처리 규약'` | KISA 4절 |
| `java.txRollbackFor` | warn | '@Transactional 에 rollbackFor 가 없다 — 체크 예외에 롤백이 안 된다' | `'@Transactional(rollbackFor = Exception.class) — readOnly 는 제외'` | `'p3c TransactionMustHaveRollbackRule · Sonar S8989'` | — |
| `java.txSelfCall` | warn | '같은 클래스 안에서 @Transactional 메서드를 직접 부른다 — 프록시를 안 지나 트랜잭션이 안 걸린다' | `'그 메서드를 다른 빈으로 빼거나 호출자에 같은 @Transactional 을 건다'` | `'Sonar S2229'` | — |
| `java.txPrivate` | error | 'private·static 메서드의 @Transactional 은 무시된다' | `'public 인스턴스 메서드로 바꾸거나 애너테이션을 호출자로 옮긴다'` | `'Sonar S2230'` | — |
| `java.threadLocalRemove` | warn | ThreadLocal 에 set 만 있고 remove 가 없다 — 스레드 풀에서 값이 다음 요청으로 샌다 | `'요청 끝(finally·afterCompletion)에서 remove() 를 부른다'` | `'p3c ThreadLocalShouldRemoveRule · Sonar S5164'` | KISA 3절 |
| `java.staticDateFormat` | error | static SimpleDateFormat·DateFormat 공유 — 스레드 안전하지 않아 날짜가 섞인다 | `'메서드 안에서 만들거나 ThreadLocal·DateTimeFormatter 로'` | `'p3c AvoidCallStaticSimpleDateFormatRule'` | KISA 3절 |
| `java.refEquals` | warn | String·래퍼(Integer·Long…)를 == 로 비교한다 — 값이 같아도 거짓일 수 있다 | `'equals 로 비교한다 — "상수".equals(x) 꼴이면 NPE 도 막는다'` | `'p3c WrapperTypeEqualityRule · Sonar S4973'` | CWE-597 |
| `java.bigDecimalDouble` | error | new BigDecimal(double) — 0.1 이 0.1000000000000000055… 로 들어간다 | `'BigDecimal.valueOf(d) 또는 new BigDecimal("문자열")'` | `'p3c BigDecimalAvoidDoubleConstructorRule'` | — |
| `java.weekYear` | error | 날짜 형식 YYYY 는 주 기준 연도 — 연말·연초에 해가 틀린다 | `'yyyy 로 바꾼다(YYYY 는 ISO 주 연도, 주 번호 w 와 짝)'` | `'Sonar S3986 · OpenRewrite ReplaceWeekYearWithYear'` | — |

22. `CheckRulesTest:167` 118 → 123(5-59a) → 126(5-59b) → 129(5-59c), 주석에 `설계 49 Java 결함 열하나` · `:168` 44 그대로.

### 픽스처·시험(행마다 자기 것) — `fixtures/check/java/`, 새 파일(덧붙임 아님 — 이 폴더는 수 고정 없음)
23. 공통 규약 — 클래스에 `@Service`·`@Controller`·`@Repository` 금지(`profileNaming` 셋 고정) · 이름이 `ServiceImpl` 로 끝나지 않게(`java.swallow`·`egovBase` 회피) · import 는 쓰는 것만, `*` 없음 · 선언 순서 지킴(static 필드 → 인스턴스 필드 → 생성자 → 메서드 · public → protected → package → private) · 빈 catch 없음(주석만도 빈 것) · 메서드 ≤ 100줄·복잡도 ≤ 15·인자 ≤ 7 · 두 파일 사이 30줄 같은 덩어리 없음(`metric.duplicate`) · `Neg*` 는 java·metric 전부 0 · `Pos*` 는 지적 한 줄에 한 건(같은 줄 두 규칙은 txRollbackFor+txPrivate 만 허용) · 파일 머리 주석에 어느 설계·규칙 픽스처인지 한 줄.
24. `PosDefects.java` — 필드: `private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");`(staticDateFormat) · `static DateFormat DF = DateFormat.getDateInstance();`(staticDateFormat) · `private static final ThreadLocal<String> USER = new ThreadLocal<>();`(threadLocalRemove — 아래 `set` 만). 메서드: `finallyReturn()` — `try { work(); } finally { return; }` 꼴(void 면 `return;`) → 1 · `finallyBreak()` — `for` 안 `try { work(); } finally { break; }` → 1 · `finallyContinue()` — `while` 안 finally 의 `continue;` → 1 · `finallyThrow()` — `finally { throw new IllegalStateException("x"); }` → 1 · `finallyThrowInCatch()` — `finally { try { work(); } catch (IOException e) { log(e); throw new IllegalStateException("y"); } }` → finallyThrow 1(lostCause 는 e 를 썼으니 없음) · `lostCause()` — `catch (Exception e) { throw new IllegalStateException("fail"); }` → 1 · `lostCauseMulti()` — `catch (IllegalStateException | IllegalArgumentException ex) { throw new RuntimeException(); }` → 1 · `threadLocal()` — `USER.set("u");`(remove 없음 → 필드 줄에 1). 도우미 `work() throws IOException`·`log(Exception e)`(몸 있음). 기대 — finallyReturn 3 · finallyThrow 2 · lostCause 2 · threadLocalRemove 1 · staticDateFormat 2 = 10.
25. `NegDefects.java` — `finally { try { in.close(); } catch (IOException e) { log(e); } }`(탈출·throw 없음) · finally 안 `for` 의 `break;`·`while` 의 `continue;`(루프가 F 안) · finally 안 `Runnable r = () -> { return; };`(람다) · finally 안 `switch` 의 `break;` · finally 안 `try { work(); } catch (IOException e) { log(e); }` 의 try 블록에 `throw new IllegalStateException("z");`(catch 가 받는다 → 없음) · catch 셋: `throw new IllegalStateException("m", e);` · `throw new IllegalStateException(e);` · `log(e); throw new IllegalStateException("m");` · `throw e;`(RuntimeException) · `String s = e.getMessage(); throw new IllegalStateException(s);` · ThreadLocal 둘: `set` + `remove`(다른 메서드에서) · `ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy"))` 에 `get()` 만 · 인스턴스 `private final SimpleDateFormat sdf = …`(static 아님) · `private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_DATE;` · `private static final ThreadLocal<SimpleDateFormat> TL = …`(형식이 ThreadLocal). 기대 0.
26. `PosValue.java` — 필드 `private static final String FMT = "YYYYMMdd";`(weekYear — 아래 `new SimpleDateFormat(FMT)` 로 한 홉, 지적 줄은 이 필드 줄). 메서드 `refEquals(String a, String b, Integer i, Integer j, Long k, Long l)` — 줄마다 `boolean r1 = a == b;` · `boolean r2 = i != j;` · `boolean r3 = a == "x";` · `boolean r4 = "y" != b;` · `boolean r5 = k == l;` · 지역 `Boolean f = Boolean.TRUE; Boolean g = Boolean.FALSE; boolean r6 = f == g;` → 6 · `bigDecimal(double d, Double e, float f)` — `new BigDecimal(0.1)` · `new BigDecimal(d)` · `new BigDecimal(e)` · `new BigDecimal(f)` · `new BigDecimal((double) 3)` · `new BigDecimal(Double.parseDouble("1.5"))` · `new BigDecimal(e.doubleValue())` → 7 · `weekYear()` — `new SimpleDateFormat("YYYY-MM-dd")` · `DateTimeFormatter.ofPattern("YYYYMMdd HH:mm")` · `new SimpleDateFormat(FMT)`(필드 줄 1) · `DateFormatUtils.format(new Date(), "YYYY/MM/dd")`(scope 에 Format) → 3 + 필드 1. 기대 — refEquals 6 · bigDecimalDouble 7 · weekYear 4 = 17. `r1~r6` 변수는 한 번 쓴다(`return r1 && …`) — 안 쓰는 지역 변수 규칙은 없지만 깔끔하게.
27. `NegValue.java` — `a == null` · `null != b` · `int x == int y` · `i == 1`(Integer 대 int 리터럴) · `o == p`(Object) · `a.equals(b)` · `c == 'x'`(char) · `d == 1.0`(double) · `new BigDecimal("0.1")` · `BigDecimal.valueOf(d)` · `new BigDecimal(10)` · `new BigDecimal(longVar)` · `new BigDecimal(bigInteger)` · `new BigDecimal(i)`(Integer) · `"yyyy-MM-dd"` · `"YYYY-'W'ww"`(w 있음) · `String label = "YYYY-MM-DD";`(대입 — 호출 인자 아님) · `log("YYYY 형식으로")`(한글 — 패턴 글자 밖) · `private static final String OK = "yyyyMMdd";` + `new SimpleDateFormat(OK)`. 기대 0.
28. `PosTx.java` — public 클래스 `PosTx`(클래스 머리 tx 없음): `@Transactional public void a()` → txRollbackFor 1 · `@Transactional(readOnly = false) public void b()` → 1 · `@Transactional(noRollbackFor = IllegalStateException.class) public void c()` → 1 · `@Transactional(rollbackFor = Exception.class) private void d()` → txPrivate 1 · `@Transactional(rollbackFor = Exception.class) static void e()` → txPrivate 1 · `public void caller() { a(); this.b(); }` → txSelfCall 2(줄 따로) · `@Transactional(rollbackFor = Exception.class) public void caller2() { c(); }`(글 다름) → txSelfCall 1. 같은 파일 package-private 둘째 클래스 `@Transactional class PosTxHolder { public void x() { y(); } @Transactional(readOnly = true) public void y() { } }` → txRollbackFor 1(클래스 머리) · y 는 readOnly → 0 · x→y 는 클래스 머리 tx 라 0. 기대 — txRollbackFor 4 · txPrivate 2 · txSelfCall 3 = 9. 몸이 빈 메서드는 `{ }` 가 아니라 한 문장(`work();`)을 둔다(빈 몸 규칙은 없지만 순서·일관성).
29. `NegTx.java` — `@Transactional(rollbackFor = Exception.class) public void a()` · `@Transactional(readOnly = true) public void r()` · `@Transactional(rollbackForClassName = "Exception") public void n()` · `@Transactional(rollbackOn = Exception.class) public void j()`(JTA 꼴) · `@Transactional(rollbackFor = Exception.class) public void caller() { a(); }`(같은 글) · `public void other() { helper(); }`(helper 는 tx 없음) · `public void viaBean() { bean.a(); }`(scope 가 this 아님 — 필드 `private final NegTx bean;` 는 생성자로 받음) · `@Transactional(rollbackFor = Exception.class) public void rec() { if (ready()) { rec(); } }`(재귀) · 람다 안 `a()` 호출도 호출자가 같은 글이면 0. 기대 0. 인터페이스 `@Transactional(rollbackFor = Exception.class)` 메서드 하나 둘째 타입으로(인터페이스 메서드도 16 이 보되 rollbackFor 있어 0).
30. 시험 — `JavaDefectsTest`·`ValueAstTest`·`TxAstTest`(각각 `SecurityAstTest` 꼴): `positiveFixture`(파일 → `"줄 규칙"` 목록 정확히) · `negativeFixture`(빈 목록) · 인라인 둘 이상(5-59a: finally 안 루프 break 제외 · 중첩 try 의 try 블록 throw 제외 / 5-59b: `Integer == int` 제외 · 상수 한 홉 weekYear / 5-59c: 클래스 머리 tx 면 self-call 없음 · 글 다른 호출자) · `offRulesAreSilent`(안 켠 규칙은 빈 목록). 글자 넷 이상 반복은 상수(PMD `AvoidDuplicateLiterals`). 줄 번호는 픽스처를 쓴 뒤 센다(시험이 RED → 구현 → GREEN).
31. 골든 `check/java.json` — `PosDefects.java` 10 행 · `PosValue.java` 17 행 · `PosTx.java` 9 행이 파일 이름 순서 자리에 끼고 기존 63 행 불변. `java-order-ignore.json` 불변(새 픽스처 ORDER 0). `fixtures.json`·`pure-rules.json`·`self-check.txt`(새 키 0) 불변.

### 측정·판정
32. V-45a 재채점 — 설계 48 §3-22 와 같은 여덟(`CheckCorpusTest`·`LegacyCorpusTest`·`VcsCorpusTest`·`CliCorpusTest`·`JulietCorpusTest`·`SecurityBenchmarkCorpusTest`·`SqlRuleCasesCorpusTest`·`SqlFixtureCorpusTest`)을 `-Dgolden.update=true` 한 번 → `git diff --stat src/test/resources/golden/corpus` 를 **읽고** 규칙 열하나 × 표본 count·files 전·후와 벽시계를 이력에. 기대(grep 추정 상한 — AST 는 같거나 적다) — `check-jeecg.json` lostCause ≤18 · txRollbackFor ≈44 · threadLocalRemove 1 · staticDateFormat 8 · refEquals ≤41 · `check-jeesite.json` txRollbackFor ≈66 · threadLocalRemove 1 · staticDateFormat 2 · refEquals ≤6 · `check-zheng.json` txRollbackFor ≈38 · lostCause 1 · `check-ruoyi-classic.json` lostCause ≤4 · txRollbackFor ≈18 · refEquals ≤4 · bigDecimalDouble 1 · `check-commons.json` lostCause ≤14 · refEquals ≤30 · threadLocalRemove ≤2 · `check-egov.json` finallyReturn 1 · finallyThrow 1 · lostCause ≤12 · refEquals ≤6 · `check-jspwiki.json` lostCause ≤8 · `check-roller.json` lostCause ≤15 · refEquals ≤2 · `check-egov-react.json` 0 · `-hits.txt` 넷 안팎 갱신 · `vcs-egov.json` `fullFindings` 6705 → +20 안팎(`findings` 도) · weekYear·txSelfCall·txPrivate 는 corpus 골든 전부 0 · **diff 0** — `security-juliet.json`·`security-benchmark.json`·`sqlrules*`·`sqltables-diff.txt`·`sqlfixture.json`·`analyze-*.json`·`check-parse-limit.txt`. 수치는 게이트 아님. 사다리 — ① 한 표본 한 규칙이 200 건을 넘으면(소음) 판정을 좁히지 **않고** R-14 에서 본다 ② 등급 A(`parseError`·예외)가 새로 나면 고침 둘까지(새 클래스의 예외 — `orElseThrow`·`get()` 금지, `Optional` 로) ③ 시험 하나 10분 초과면 중단.
33. R-14 손 판정 — CLI(`toolbox.bat check <표본 폴더> --groups java --json --profile example`, data 는 scratch — 설계 48 R-13 과 같은 꼴. 결과 JSON 에서 `java.` 규칙 열하나 줄만)로 jeecg·jeesite·zheng·ruoyi-classic·egov·commons-lang·shopizer·egov-msa·struts 를 돌려 표 하나 — 규칙마다 ≤ 20 줄(건수가 그 아래면 전부: finally 셋·threadLocalRemove·staticDateFormat·bigDecimalDouble·txSelfCall·txPrivate 는 전부 · lostCause 는 jeecg 전부 + 표본마다 둘 · txRollbackFor 는 표본마다 셋 · refEquals 는 jeecg 문자열 리터럴 전부 + 형식 추정 열) — `파일:줄 · 규칙 · 판정(참/오탐/참(규칙 정의)) · 근거 한 구절`. 코드 본문은 안 옮긴다(규칙 3). 고침 사다리(둘까지) — ① refEquals `a == b || a.equals(b)` 관용구 → `||` 형제 `equals` 제외 ② lostCause 가 「변환 예외 — 메시지에 원인 글을 손으로 적는 꼴」 로 오탐이 많으면 `new X(…)` 인자에 **문자열 리터럴만** 있을 때로 좁히지 않는다 — 규칙 정의(참)로 적고 「후보로」 ③ txRollbackFor 클래스 머리 44(jeecg)는 참(규칙 정의) ④ threadLocalRemove 의 `ThreadLocal<SimpleDateFormat>` 캐시(jeecg `HqlGenerateUtil`)는 참(규칙 정의 — p3c 도 지적) ⑤ shopizer txSelfCall 3 — 호출자가 `@Transactional` 이고 피호출이 `noRollbackFor` 라 피호출 속성이 무시되는 꼴 → 참. 결함 둘 넘으면 새 행 번호를 「후보로」. 고친 뒤 V-45a 명령으로 골든 다시.
34. V-45 compare ⑮ — `## ⑮ 재측정 — 번들 65(2026-11-01)`: 규칙 열하나 × 표본(egov·commons·jspwiki·roller·egov-react·jeecg·jeesite·zheng·ruoyi-classic + CLI shopizer·egov-msa·struts·egov-prev) 건수·파일 표 · R-14 표 · 시험 시간 여덟 · 「드러난 것」(표본 Java 에 `YYYY` 리터럴 0 · 같은 클래스 tx 호출은 호출자도 tx 인 꼴이 대부분(egov-msa 14 → 0) — 결함은 shopizer 의 속성 다른 호출 셋뿐 · 레거시 `@Transactional` 은 클래스 머리(jeecg·zheng)와 `readOnly = false` 메서드(jeesite) 두 관례 · eGov 표준 프레임워크는 XML tx 라 `@Transactional` 0 · ThreadLocal 캐시(`withInitial`)와 요청 홀더(`set`)를 `set` 유무로 갈랐다) · 「후보로」(`@RequestMapping` private(S3751) · `DecimalFormat`·`MessageFormat` static · `catch (Throwable)`(가-3) · 중복 로그(가-5) · `equals` 상수 앞세우기(가-14 — 66 고침과 짝) · float `==`(가-17) · `propagation` 판정 · `Integer` 캐시 범위 밖 리터럴 · 번들 66~68). ① 표의 가-행은 안 고친다(전례). PLAN 9장 `core.check` 줄(`:377`) `SecurityAst — 파서·TLS 설정 결함, 5-58` 뒤에 `·JavaDefects·ValueAst·TxAst — Java 결함 열하나, 5-59`.

## 4. 실행 스텝

### S0 — 설계 문서·행·번들 표 (설계 세션이 한다)
- 대상: 신설 `design/49-bundle65.md`(이 파일의 「맥락」~8절) · `PLAN.md`(번들 표 「번들 65」 행(`:550`) — `S0`→`5-59a`→`5-59b`→`5-59c`→`V-45a`→`R-14`→`V-45`, 2026-11-01(Fable, 설계 49 — `design/49-bundle65.md`), 요지 + 머지 위임 있음, 선행 「번들 64 머지 · 표본 폴더 + node(`--corpus`)」, 상태 「대기 — 설계 끝」 · 분할표 — V-44 행(`:1090`) 아래에 `5-59a`·`5-59b`·`5-59c`·`V-45a`·`R-14`·`V-45` 여섯 행(각 「**설계 49 §3-n·§4 가 원본**」 + 강제 지점 + 선행 + 「대기」) · 9장은 V-45 가 고친다) · `PROGRESS.md`(「열려 있는 것」 머리 → 「**번들 65(설계 49, Java 결함 묶음 열하나) 설계 끝 — 가지 `work/2026-11-01`**, 다음은 「번들 해」」, 이력 S0 줄 — 직전 줄(번들 64 닫기) 해시 칸 `(이 커밋)` → `bb740a8`, 이 줄은 `(이 커밋)`).
- 절차: 가지 `work/2026-11-01` 그대로 → 세 파일 → `bash scripts/doc-lint.sh design/49-bundle65.md PLAN.md PROGRESS.md` → `bash scripts/verify.sh` → (따로) `git commit -m "docs: S0 설계 49 — 번들 65 행·번들 표(Java 결함 묶음 열하나)"`.

### 5-59a — `JavaDefects` 다섯 + yaml + 픽스처 + 시험 + 골든
- 대상: 신설 `core/check/JavaDefects.java` · `core/check/JavaRules.java`(`taint` for 한 줄 + javadoc) · `resources/check/rules.yaml`(다섯) · 신설 `fixtures/check/java/PosDefects.java`·`NegDefects.java` · 신설 `JavaDefectsTest` · `CheckRulesTest`(`:167` 123) · 골든 `check/java.json`
- 변경(TDD — 실패 먼저): ① yaml 다섯(§3-21) + `CheckRulesTest` 123 + 픽스처 둘(§3-24·25) + `JavaDefectsTest`(§3-30 — 기대 줄은 픽스처에서 센다) → `bash scripts/mvn.sh -q test -Dtest=JavaDefectsTest,JavaRulesTest,CheckRulesTest` → RED(컴파일 실패 또는 「양성에서 한 번도 안 걸린 규칙」 다섯 · 골든 diff). ② `JavaDefects`(§3-1~7) + `taint` 한 줄(§3-20) → 같은 시험 → 골든 갱신 `-Dgolden.update=true` 한 번 → `git diff src/test/resources/golden/check/java.json` 이 `PosDefects.java` 새 행 10 뿐인지 **읽는다** → 다시 돌려 GREEN. `Neg*` 히트 0.
- 검증: `bash scripts/mvn.sh -q test -Dtest=JavaDefectsTest,JavaRulesTest,CheckRulesTest,PureCodeCheckTest,TaintTest,SecurityAstTest,SelfCheckTest,ArchitectureTest` 초록 · `self-check.txt` diff 0 · `bash scripts/mvn.sh -q pmd:check` 0 · `bash scripts/verify.sh`.
- 의존: S0.

### 5-59b — `ValueAst` 셋 + yaml + 픽스처 + 시험 + 골든
- 대상: 신설 `core/check/ValueAst.java`(+ 필요하면 `AstTypes.java`) · `JavaRules.java`(for 한 줄) · `rules.yaml`(셋) · 신설 `PosValue.java`·`NegValue.java` · 신설 `ValueAstTest` · `CheckRulesTest`(126) · 골든 `check/java.json`
- 변경(TDD): ① yaml 셋 + 126 + 픽스처(§3-26·27) + 시험 → RED. ② `ValueAst`(§3-8~13) + 배선 → 골든 갱신 한 번 → diff 가 `PosValue.java` 17 행뿐인지 읽는다 → GREEN.
- 검증: 5-59a 와 같은 묶음 + `ValueAstTest` · `pmd:check` 0 · `verify.sh`.
- 의존: 5-59a(배선 자리·골든 순서).

### 5-59c — `TxAst` 셋 + yaml + 픽스처 + 시험 + 골든
- 대상: 신설 `core/check/TxAst.java` · `JavaRules.java`(for 한 줄) · `rules.yaml`(셋) · 신설 `PosTx.java`·`NegTx.java` · 신설 `TxAstTest` · `CheckRulesTest`(129) · 골든 `check/java.json`
- 변경(TDD): ① yaml 셋 + 129 + 픽스처(§3-28·29) + 시험 → RED. ② `TxAst`(§3-14~19) + 배선 → 골든 갱신 → diff 가 `PosTx.java` 9 행뿐인지 읽는다 → GREEN.
- 검증: 같은 묶음 + `TxAstTest` · `pmd:check` 0 · `verify.sh`.
- 의존: 5-59b.

### V-45a — 재채점(corpus 골든 갱신) + 시간
- 대상: `golden/corpus/check-*.json`·`-hits.txt`·`vcs-egov.json`(바뀜 기대) · 나머지 corpus 골든(diff 0 기대)
- 변경: `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='CheckCorpusTest,LegacyCorpusTest,VcsCorpusTest,CliCorpusTest,JulietCorpusTest,SecurityBenchmarkCorpusTest,SqlRuleCasesCorpusTest,SqlFixtureCorpusTest' -Djunit.timeout=10m -Dgolden.update=true` → `git diff --stat src/test/resources/golden/corpus` + 규칙 열하나 키의 전·후를 읽고 이력에(§3-32). 기대 밖 골든이 바뀌면 고치지 않고 중단 조건.
- 검증: 같은 명령(갱신 없이) 초록 · 시험 하나 10분 안 · 등급 A 0.
- 의존: 5-59c.

### R-14 — 손 판정(새 지적 · CLI 표본)
- 대상: `design/compare-2026-10-28.md`(⑮ 「손 판정」 절) · `PROGRESS.md` · (고침이 나면) 해당 클래스·픽스처·골든
- 변경: §3-33. 표 하나 + 분류 합. 결함 둘 이하는 고치고(픽스처 음성 한 줄 먼저 → RED → 고침 → V-45a 명령으로 골든 다시) 넘으면 새 행 번호를 「후보로」. 코드 본문은 안 옮긴다.
- 검증: `bash scripts/doc-lint.sh design/compare-2026-10-28.md` · 분류 합 = 줄 수 · (고침 있으면) 5-59 검증 묶음 + V-45a 재실행.
- 의존: V-45a.

### V-45 — compare ⑮ · PLAN 9장 · `--corpus`
- 대상: `design/compare-2026-10-28.md`(⑮ 나머지 — §3-34) · `PLAN.md` 9장 `:377` · `PROGRESS.md`
- 검증: `bash scripts/verify.sh --corpus` 초록(약 16~20분, Docker 안 씀) → `bash scripts/verify.sh` 초록. `--db` 는 안 돈다(`core/gen`·MANIFEST·pom 불변 — 끝 줄이 db 를 부르면 멈추고 보고).
- 의존: R-14.

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7 · 행의 축·강제 지점과 diff · 규칙마다 양성·음성이 다 있나(열하나 × 2) · 음성의 결정적 사례(루프 안 break · 중첩 try 의 try 블록 throw · `e.getMessage()` 사용 · `Integer == int` · `withInitial` 캐시 · `readOnly = true` · 재귀 · 클래스 머리 tx · `"YYYY-'W'ww"`) · 골든이 의도된 변화인가(`java.json` 새 36 행만 · corpus 는 check-*·hits·vcs-egov 만) · `JavaRules` 메서드 30·`self-check.txt` 새 키 0 · PMD 0 · `ArchitectureTest` 순환 0) → `bash scripts/gate-probe.sh` → `bash scripts/verify.sh` → `git push -u origin work/2026-11-01` → `gh pr create --base main`(행마다 시작 세 줄 표 + 닫기 `bb740a8` 같이 + ⑮ 핵심 수치) → CI 폴링(`gh api … check-runs`) → claude[bot] 처분 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → 닫기 커밋은 다음 가지 → 끝 보고. PR 본문 끝 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## 5. 금지 사항
- `pure/`·portfolio·`docs/`·화면 HTML/JS 수정 금지 · 새 Maven 의존성 금지(심볼 솔버 포함) · `corpus/MANIFEST`·표본 변경 금지 · `scripts/verify-fingerprint.sh` 수정 금지 · **`core/gen`·`pom.xml` 수정 금지**(db 레인)
- **`JavaRules` 에 메서드·분기 추가 금지**(메서드 30·`apply` 19) — 새 로직은 전부 새 클래스 셋 · `self-check.txt` 새 키 금지 · PMD 새 짝 금지(기준선 손으로 늘리지 않는다)
- `Cleanup`·`CleanupRunner`·`OrderFix` 수정 금지(고침은 번들 66) · `SecurityAst`·`Taint*`·`Sanitizers` 수정 금지 · 새 규칙을 `Taint.knows`·`SINKS`·하이브리드(`params.java`)에 넣지 않는다
- 기존 픽스처 파일 수정·삭제 금지(새 파일만) · `check/java.json` 기존 63 행 변경 금지 · `java-order-ignore.json`·`fixtures.json`·`pure-rules.json` 불변 · `fixtures/check/security/`·`common/` 등 다른 폴더에 파일 추가 금지
- 새 픽스처에 `@Service`·`@Controller`·`@Repository` 금지 · 이름 `*ServiceImpl` 금지 · `*` import 금지
- 기존 규칙 전부의 판정·줄 불변 · `rules.yaml` 기존 규칙 글 불변 · `ref:` 칸에 KISA·CWE 번호 금지(주석 후보만)
- `CheckCorpusTest`·`LegacyCorpusTest`·`VcsCorpusTest`·`JulietCorpusTest`·`SecurityBenchmarkCorpusTest` 판정 로직 수정 금지 · 골든 갱신은 5-59a~c(`java.json`)·V-45a(corpus)와 R-14 고침 뒤뿐
- 발췌·골든·compare·이력에 코드 본문을 싣지 않는다(규칙 3 — 파일·줄·규칙까지) · 표본 추가 금지 · 새 화면 금지

## 6. 최종 검증
- `bash scripts/verify.sh` · `--corpus` 초록 · `--db` 안 돈다(끝 줄 확인) · `pmd:check` 0 · `gate-probe.sh` 10/10
- 골든: `check/java.json` = 기존 63 + `PosDefects` 10 + `PosValue` 17 + `PosTx` 9 · `java-order-ignore.json`·`fixtures.json`·`pure-rules.json`·`sql-*.json`·`gen/*` diff 0 · `self-check.txt` 새 키 0(`JavaRules` 네 줄 그대로) · corpus 골든 변화 = `check-*.json`·`-hits.txt`·`vcs-egov.json` 뿐
- 시험: `CheckRulesTest` 129·44 · `JavaRulesTest` 음성 0 · `JavaDefectsTest`·`ValueAstTest`·`TxAstTest` 각 넷 이상 · `PureCodeCheckTest` 32 · corpus 시험 10분 안 · `JavaDefects`·`ValueAst`·`TxAst` 메서드 ≤ 30
- 수동: 코드 검사 화면에 `C:/workspace/toolbox-corpus/jeecg/src/main/java/org/jeecgframework/core/util` → `DateUtils.java` 에 `java.staticDateFormat` 8 건 · 붙여넣기 `.java` 에 `@Transactional public void save() { }` 한 메서드 → `java.txRollbackFor`(사람 몫 ① 에 한 줄)
- compare ⑮ 표 + R-14 표 + 「드러난 것」 + 「후보로」

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- `self-check.txt` 에 새 키 또는 `JavaRules` 줄 숫자 변화 · `pmd:check` 에 새 짝 · `ArchitectureTest` 빨강 · 새 클래스 메서드 30 초과(`AstTypes` 로 빼도 넘으면)
- `check/java.json` 기존 행이 바뀐다 · `java-order-ignore.json` 이 바뀐다 · `profileNaming` 이 빨강(새 픽스처가 naming 에 걸림) · 음성 픽스처 히트
- V-45a 에서 juliet·benchmark·sqlrules·sqltables·sqlfixture·analyze-* 골든이 바뀐다(기대 0) · 한 표본 한 규칙 200 건 초과 · 시험 하나 10분 초과 · 등급 A 가 고침 둘로 안 닫힌다
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① 재채점 수치(§3-32 는 grep 상한 — AST 는 같거나 적다; jeecg refEquals·commons refEquals 가 가장 크게 줄 수 있다) ② 세 클래스의 메서드 수가 30 아래인지(`SelfCheckTest` 첫 실행 — 넘으면 §3-13 `AstTypes`) ③ JavaParser 가 새 픽스처 여섯을 끝까지 읽는지(`JavaRulesTest` 첫 실행 — 못 읽으면 `java.parseError` 가 양성에 나와 골든에 든다 → 픽스처를 고친다) ④ `fix` 글 열하나가 60자 안인지(`CheckRulesTest:175` — 넘으면 줄인다). 첫 스텝 전: `bash scripts/verify.sh` 초록 · `git log --oneline -1`(S0 커밋) · `ls C:/workspace/toolbox-corpus | wc -l`(33).
- **사용자만 정할 수 있는 것** — 없음(심각도·뺄 규칙·행 쪼개기·머지 위임은 2026-11-01 받았다 — 「사용자 결정」).
