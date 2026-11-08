# 설계 59 — 번들 75: 코드 검사 철저화 — 자원 꼴 A' · 리뷰 Minor 다섯 · XXE 정밀도 (Fable 설계 · Opus 실행, 2026-11-08)

## 맥락
번들 74 가 남긴 새 행 둘(5-63 tryWithResources 꼴 넓히기 · 5-64 리뷰 Minor 다섯)과 compare ㉔ 「후보로」 의 xxe 둘(`setEntityResolver` 막음 판정 · Cyberneko HTML 파서). 전부 `core/check` 라 corpus 레인 한 번으로 닫는다. 라-1·9·12(코드 정리 B)는 사용자 결정으로 번들 76 몫.

**실측(2026-11-08, 표본 13 — JavaParser 3.26.3 스캐너, 코드 읽기. 줄 번호는 `72cb5ce` 트리)**:
- 5-63 ① 「close 뒤 자원을 안 쓰는 단순 문장(return·대입)이 오는 꼴 C」 — 표본 13 에서 **1건**(jeecg, 꼴 B 선언도 아님) → 효과 0. **안 함**. 기록만
- 5-63 ② 꼴 A'(try 없이 같은 블록에서 자원 선언 + 뒤에서 bare `x.close()`, close 뒤 그 이름 안 씀, 몸 안 선언 변수를 close 뒤 안 씀) — jeecg 29 · jeesite 5 · roller 5 · zheng 2 · ruoyi-classic 2 · jspwiki 2 · struts 1 · egov 0 · commons 셋 0 · egov-msa 0 · shopizer 0 = **46**(소유 판정 `creation` 전 — 빌린 것(`response.getOutputStream()` 래퍼 · 메모리 래퍼)이 빠지면 더 준다). 대표 자리 jeecg `core/util/FileUtils.java:235·243·251` · `core/util/ZipUtil.java:67·90` · `jwt/util/JwtHttpUtil.java:56·64·65`(잇닿은 선언 셋) · `core/common/dao/impl/CommonDao.java:228·236·244` · roller `webservices/atomprotocol/MediaCollection.java:398·402` · zheng `zheng-common/…/JarUtil.java:45·46`(선언 둘 잇닿음, 둘째 초기화식이 두 줄) · jspwiki `ui/WikiJSPFilter.java:112`(응답 스트림 래퍼 — 빌린 것이라 빠져야 맞다)
- 꼴 A' 는 지금 `security.resourceLeak` 만 난다 — `ResourceAst.handedOver:289` 가 scope 호출 `x.close()` 를 **finally 안일 때만** 해제로 치므로(`closer(name) && (!scope || inFinally(m))`) 지적은 이미 있고 **고침이 없다**. `java.tryWithResources` 는 `TryStmt` 만 돈다(`apply:113-117`)
- 우리 코드(`src/main/java` 186 파일) 꼴 A' 0 → `self-check.txt`·`self-cleanup.txt` 불변 기대
- ㉔ xxe: jspwiki `auth/authorize/WebContainerAuthorizer.java:324`(`new SAXBuilder()` + `setXMLReaderFactory(NONVALIDATING)` + `setEntityResolver(new LocalEntityResolver())`) · `htmltowiki/HtmlStringToWikiTranslator.java:105`(`new SAXBuilder(new XMLReaderSAX2Factory(true, CYBERNEKO_PARSER), null, null)`, import `org.codelibs.nekohtml.parsers.SAXParser`). 각 1자리
- 메서드 수: `ValueAst` **30**(상한) · `SecurityAst` 29 · `ResourceAst` 바깥 29 + `Planner` 17 · `ResourceFix` 9 · `JavaDefects` 25. `self-check.txt` 에 이 다섯 클래스 줄 없음(복잡도 ≤ 15 전부)
- `ValueAst.declaredType` 호출자: `JavaDefects:297` · `ResourceAst:203·224` · `SecurityAst:197` · ValueAst 안(`isBoxed:204`·`doubleArg:337`·`isFloat:358`). `scalarType:412` 가 `simpleType:417` 을 쓴다. `isBoxed:203` 은 private
- `JavaDefects.rethrows:248-252` — `throw t` 와 `throw helper(t)` 만 다시 던지기. `PosDefects:110-114` 셋째 catch 는 `log(t); throw new IllegalStateException(t);` 라 지금 **양성**(`java.json:80`) — ① 을 고치면 음성이 된다
- `JavaDefects.floatEquals:285-292` 와 `ValueAst.refEquals:114-128` 이 `Double x != y` 에 같은 노드 둘. `PosDefects:145` `boolean q = x != y;`(Double·Double) 가 그 자리
- `SecurityAst.guarded:171-184` — `varName` 이 매개변수 이름이면 `declaresLocal` 거짓 → `TypeDeclaration` 범위. JavaParser `NodeWithParameters.getParameterByName(String)` 있음(javap 확인)
- `SecurityAst.xmlFactories:105-116` PARSERS 루프에 import 검사 자리 없음. `rules.yaml:584-590` xxe fix 글 「disallow-doctype-decl 을 켜고 외부 개체 기능을 끈다」
- `ResourceFix.apply:60-62` 가 `(TryStmt) h.node()` 로 캐스트 — 꼴 A' 앵커(선언문)가 오면 `ClassCastException`. `Edits.replace` 는 첫 쓰기 우선, `delete` 는 `null`. `Edit(deletes, head, finallyLine, tail)` — `finallyLine > 0` 이면 그 줄을 `tail` 로 바꾼다(`apply:77-79`)
- 골든: `check/java.json`(catchThrowable 3 · floatEquals 3 · tryWithResources 6) · `cleanup/resource-Pos.java.txt` 70줄 · `cleanup/steps.json` `resource/Pos.java` before 10 after 2 · corpus `check-*.json` tryWithResources egov 13·jeecg 3·jeesite 1·roller 1·zheng 1, resourceLeak jeecg 37·roller 63·jeesite 10, xxe jspwiki 4·jeecg 1·jeesite 1, catchThrowable commons 6·jspwiki 3·roller 2·jeecg 1, floatEquals commons 12·egov 9·jeecg 1·ruoyi 1 · `cleanup-roller.json` resource 1
- 시험: `ResourceAstTest.tryWithResourcesFixture:64-68`(PosResource 아홉 · NegResource 0, ON = LEAK+TWR) · `JavaDefectsTest.rethrowIsNotCatchThrowable:49-52`(둘째 줄 `throw new RuntimeException(t)` 를 **양성**으로 단언 — 뒤집는다) · `SecurityAstTest.positiveFixture:32-37` 줄 목록 · `JavaRulesTest` — `Pos*` 양성 ≥ 1·`Neg*` 모든 java 규칙 0 → 꼴 A' 음성(해제 안 됨 = resourceLeak)은 `NegResource` 에 못 넣고 인라인 시험으로
- 레인: `core/check`(main·test)·`fixtures/check`·`fixtures/cleanup`·`golden/corpus` 전부 corpus · db 레인 안 닿음 → **`--db` 안 돈다**(`verify.sh` 끝 줄이 부르면 그때만)

**범위 밖(사용자 2026-11-08)**: 라-1·9·12(번들 76) · 개발자 도구 새 탭 · java.time 날짜 표시 · `--db` CI 이전 · 현장 MyBatis 판 · 5-63 ①(실측 0) · 5-31·0-75·2-17·보류 행

## 사용자 결정(2026-11-08)
- ㉔-1 `setEntityResolver` 는 **막음으로 안 본다** — `rules.yaml` xxe fix 글에 「EntityResolver 만으로는 막음이 아니다 — DOCTYPE 을 끈다」 한 마디. jspwiki `WebContainerAuthorizer:324` 는 참(규칙 정의)으로 남긴다
- ㉔-2 Cyberneko HTML 파서로 만든 `SAXBuilder` 는 **뺀다** — 조건 「파일 import 에 `nekohtml`·`cyberneko` + `new SAXBuilder(인자 있음)`」
- 라-1·9·12 는 번들 75 에 **안 넣는다**(번들 76 설계)
- **머지 위임 있음**
- 설계가 정한 것(전부 [고정]): 5-63 ① 안 함(실측 0) · 꼴 A' 의 close 는 bare `x.close()`(또는 `if (x != null) x.close()`)만 — `closeQuietly`·try 로 감싼 close 는 예외 경로가 바뀌어 제외 · 꼴 A' 고침은 **줄 수 불변**(선언 줄 → try 머리 · 마지막 close 줄 → `}` · 몸 줄 한 단 들여쓰기) · 앵커는 잇닿은 선언 묶음의 첫 선언문 · 꼴 A' 는 `covered` 검사 없음(bare close 가 이미 컴파일된다) · ① 다시 던지기 = throw 식 어디든 catch 매개변수가 나옴 · ② 래퍼끼리 `==`·`!=` 는 `floatEquals` 에서 뺀다(refEquals 몫) · ④ `DeclaredTypes` 새 클래스로 여덟 메서드 이동 · ⑤ 이 문서부터 표본은 `파일:줄` 만(완료)

실행자는 Opus. 차례 `S0` → `5-64c` → `5-64a` → `5-64b` → `5-65` → `5-63a` → `5-63b` → `V-55a` → `R-22` → `V-55` → 마무리. 가지 `work/2026-11-09`(또는 그날 날짜). `5-64c` 를 먼저 치는 까닭 — 뒤 행이 고치는 `JavaDefects`·`SecurityAst`·`ResourceAst` 가 전부 `ValueAst.declaredType` 호출자라, 이동을 먼저 끝내야 뒤 diff 가 섞이지 않는다.

---

## 1. 목표와 범위
- 한 문장: try 없이 선언·close 하는 자원(표본 46 자리)을 `java.tryWithResources` 로 판정하고 코드 정리가 try-with-resources 로 고치며, 번들 74 리뷰 Minor 다섯과 xxe 오탐 하나(Cyberneko)를 닫는다.
- 완료 시 체감: 코드 검사에서 jeecg `FileUtils:235` 류에 `resourceLeak` 옆에 `tryWithResources` 가 같이 서고, 코드 정리 자원 단계가 그 자리를 `try (OutputStream out = new FileOutputStream(p)) { … }` 로 바꾼다(미리보기에서 줄 수 그대로). `catch (Throwable t) { throw new X(t); }` 가 더는 `catchThrowable` 이 아니고, `Double a != b` 는 `refEquals` 하나만, 매개변수에 대입한 팩토리는 그 메서드 안에서만 막음을 찾고, Cyberneko `SAXBuilder` 는 xxe 가 아니다.
- In scope: `core/check/ValueAst.java`(이동) · 새 `core/check/DeclaredTypes.java` · `core/check/JavaDefects.java`(`rethrows`·`floatEquals`) · `core/check/SecurityAst.java`(`guarded` 한 줄·`xmlFactories` 두 줄·상수 하나) · `core/check/ResourceAst.java`(`Twr`·`apply`·`plan`·`Planner` 다섯) · `core/check/ResourceFix.java`(`apply`·`Edit`·`wrap`·`step`) · `resources/check/rules.yaml`(`java.catchThrowable` fix · `security.xxe` fix · `java.tryWithResources` message·fix 글) · 픽스처 `check/java/PosDefects·NegDefects·PosResource·NegResource` · `check/security/pos·neg` · `cleanup/resource/Pos.java` · 시험 `ValueAstTest`(필요시)·`JavaDefectsTest`·`SecurityAstTest`·`ResourceAstTest`·`CleanupTest` · 골든 `check/java.json`·`check/fixtures.json`(security 새 줄)·`cleanup/resource-Pos.java.txt`·`cleanup/steps.json`·`golden/corpus/*`(V-55a 목록) · `design/compare-2026-10-28.md` ㉕
- Out of scope: 「범위 밖」 전부 · `JavaRules`·`TxAst`·`ValueFix`·`Cleanup`(단계·되돌림 논리) · `ResourceAst` 의 `resourceLeak` 판정(`released`·`handedOver`) · `SecurityAst` `guards` 키 목록·`trustAll` · 규칙 id 추가(136 그대로) · 화면 HTML/JS · `pure/` · `core/gen`

## 2. 현재 구조 요약
「맥락」 의 실측이 요약이다. 덧붙이는 함정:
- `ResourceAst.Planner` 는 `private static final class`(`:334`). `ResourceFix` 가 꼴 A' 계획을 받으려면 `ResourceAst.plan(Node, cu)` 로 넓힌다 — 바깥 메서드 수 29 그대로(시그니처만)
- `Twr(TryStmt tryStmt, …)` 의 첫 칸을 `Node anchor` 로 — `ResourceFix.edit:85` `TryStmt t = p.tryStmt()` → `(TryStmt) p.anchor()`. 꼴 A' 앵커는 첫 선언 `ExpressionStmt`
- `Edits.replace` 는 같은 줄 두 번째 쓰기를 **버린다** — 꼴 A' 고침은 머리·몸·닫는 줄이 서로 다른 줄이라 충돌 없음. 중첩 꼴 A'(바깥 몸 안에 또 꼴 A')는 `used` 줄 겹침으로 안쪽이 「겹친 try」 건너뜀 — 다음 정리 실행이 잡는다
- 꼴 A' 고침이 몸 줄을 들여쓰면 그 줄의 `file.lineLength` 가 새로 날 수 있다 → `Cleanup` 의 「다른 지적이 생김」 되돌림이 그 파일만 되돌린다(의도된 보수성, 글 그대로)
- `JavaRulesTest` 가 `Neg*.java` 에 **모든** java 규칙 0 을 요구 → 꼴 A' 음성 가운데 「해제 안 된」 꼴(`resourceLeak` 이 나는 것)은 `NegResource` 에 못 넣는다 — 인라인 시험(`Set.of(TRY_WITH_RESOURCES)`)으로. `NegResource` 에는 해제된 꼴(`closeQuietly` · 빌린 것)만
- `PosDefects:113` 을 바꾸면 `java.json:80` 의 catchThrowable 줄이 빠진다 → 그 줄을 `throw new IllegalStateException("m")` 로 바꿔 **양성 셋을 유지**(줄 번호 불변, 골든 불변). 새 음성(`throw new X(t)`·`throw (Error) t`)은 `NegDefects` 끝
- `PosDefects:145` `x != y`(Double·Double)는 ② 뒤 `floatEquals` 가 빠지고 `refEquals` 만 남는다 → `java.json` 에서 floatEquals 145 줄 하나가 빠진다(의도). 래퍼 대 기본형 양성은 파일 끝 새 클래스에 한 줄(줄 번호 불변)
- 픽스처는 전부 **파일 끝에 덧붙인다**(기존 줄 번호 불변). `security/neg.java` 는 import 한 줄을 머리에 더해도 된다(음성은 줄 단언이 없다)
- `DeclaredTypes` 이동은 순수 이동 — 골든 diff 0. PMD `UseUtilityClass` 는 private 생성자로 · ErrorProne `UnusedMethod` 없음(전부 호출됨)
- V-55a 는 `check-*.json`·`check-*-hits.txt`·`cleanup-*.json`·`vcs-egov.json` 만 바뀐다 — `ddl-*`·`gen-*`·`analyze-*`·`sql*`·`security-juliet*`·`security-benchmark` 불변. **5-6x 행 안에서는 corpus 골든을 안 만진다**
- 레인 — 전부 corpus. `--db` 는 `verify.sh` 끝 줄이 부를 때만(기대: 안 부른다)

## 3. 설계 결정 — 전부 [고정]

### 5-64c — `ValueAst` 선언 형식 풀기를 `DeclaredTypes` 로(§3-1)
- 새 파일 `core/check/DeclaredTypes.java`:
```java
/** 이름(또는 this.x)의 선언 형식 풀기 — ValueAst 에서 뗐다(5-64 ④, 메서드 30 상한). ValueAst·JavaDefects·ResourceAst·SecurityAst 가 쓴다 */
final class DeclaredTypes {
    static final String UNKNOWN = "";

    private DeclaredTypes() {
    }

    /** 이름(또는 this.x)의 선언 형식 — 람다·메서드 매개변수 → 둘러싼 블록·for·catch·try 자원 → 가장 가까운 클래스 필드. 못 찾으면 빈 글 */
    static String declaredType(Expression e) { … ValueAst:210-227 그대로 … }
    private static Optional<Type> declIn(Node a, String x) { … }
    private static Optional<Type> param(List<Parameter> ps, String x) { … }
    private static Optional<Type> locals(List<Statement> ss, String x) { … }
    private static Optional<Type> declared(List<Expression> es, String x) { … }
    private static Optional<Type> varType(List<VariableDeclarator> vs, String x) { … }
    /** 선언 형식 이름 — 배열·varargs 는 빈 글 */
    private static String scalarType(Type t) { … }
    /** 선언 글에서 {@code <…>}·{@code []} 를 떼고 마지막 {@code .} 뒤 */
    static String simpleType(Type t) { … ValueAst:417-424 그대로 … }
}
```
  `ValueAst` 에서 여덟(`declaredType`·`declIn`·`param`·`locals`·`declared`·`varType`·`scalarType`·`simpleType`)을 지우고 호출을 `DeclaredTypes.declaredType`·`DeclaredTypes.simpleType` 으로. `ValueAst.UNKNOWN` 은 남는다(다른 자리가 쓴다). 30 → **22**
- 호출자 셋: `JavaDefects:297` · `ResourceAst:203·224` · `SecurityAst:197` → `DeclaredTypes.declaredType(…)`. `JavaDefects`·`ResourceAst` 의 제 `simpleType` 사본은 그대로(이번 범위 아님)
- `isBoxed(Expression)` 은 ValueAst 에 남기고 **package-private** 으로(②가 쓴다)
- ValueAst 머리 javadoc 「선언 형식」 설명이 있으면 `DeclaredTypes` 를 가리키게 한 줄
- 시험 변화 없음(전부 `hits`·픽스처 경유). 골든 diff 0

### 5-64a — `JavaDefects` ① 다시 던지기 · ② 래퍼끼리는 refEquals 몫(§3-2)
- ①:
```java
/** 5-64 ① — 다시 던진다 = catch 몸(안쪽 catch 밖)의 throw 식 어디든 catch 매개변수가 나온다 — throw t · throw new X(t) · throw wrap(t) · throw (Error) t */
private static boolean rethrows(ThrowStmt s, String name, BlockStmt body) {
    return !catchBarrier(path(s, body))
            && s.getExpression().findAll(NameExpr.class).stream().anyMatch(n -> n.getNameAsString().equals(name));
}
```
  (`findAll` 은 자기 노드를 포함한다 — `throw t` 의 `NameExpr` 도 잡힌다). `helper` 변수 삭제
- `rules.yaml` `java.catchThrowable` fix → `'Exception 또는 구체 예외를 잡고, Throwable 은 다시 던진다(감싸서 던져도 된다)'`
- ②: `floatEquals` 조건 끝에 `&& !(ValueAst.isBoxed(l) && ValueAst.isBoxed(r))` — `l`·`r` 은 `EnclosedExpr` 을 벗긴 양쪽(`floating` 의 첫 줄과 같은 수법으로 지역 변수 둘). 주석 `// 5-64 ② — 래퍼끼리는 refEquals 몫`. 복잡도 ≤ 15 확인
- 픽스처: `PosDefects:113` `throw new IllegalStateException(t);` → `throw new IllegalStateException("m");`(양성 셋 유지 · `log(t)` 가 있어 lostCause 아님) · `PosDefects` 끝 새 클래스 `PosDefects64 { boolean wrapperVsPrimitive(Double x, double j) { return x == j; } }`(floatEquals 양성 한 줄) · `NegDefects` 끝 `NegDefects64` — `catch (Throwable t) { throw new IllegalStateException(t); }` · `catch (Throwable t) { if (t instanceof Error) { throw (Error) t; } throw new RuntimeException(t); }` · `boolean same(Double a, Double b) { return a != b; }` 는 **넣지 않는다**(refEquals 가 나서 `Neg*` 0 깨짐 — 인라인 시험으로)
- 시험 `JavaDefectsTest.rethrowIsNotCatchThrowable` → 기대 `List.of()`(둘째 줄 뒤집힘) + 셋째 줄 `throw (Error) t;` · 새 `wrapperPairIsRefEqualsOnly` — `hits("class A { boolean f(Double w, Double v, double d) { return w != v || w == d; } }", ON)` → `List.of("1" + FE)` 하나(RED: 지금 둘)
- 골든 `check/java.json`: floatEquals 145 줄 빠짐 + `PosDefects64` 줄 더해짐 · catchThrowable 셋 그대로. 그 밖 diff 0

### 5-64b — `SecurityAst` ③ 매개변수는 메서드 범위(§3-3)
- `guarded:181`:
```java
boolean local = declaresLocal(body, v)
        || body instanceof CallableDeclaration<?> c && c.getParameterByName(v).isPresent(); // 5-64 ③ — 매개변수도 메서드 범위
if (body instanceof FieldDeclaration || !local) {
```
  새 메서드 없음(29 그대로)
- 픽스처 `security/pos.java` 끝:
```java
// 5-64 ③ — 매개변수에 대입한 팩토리는 그 메서드 범위(다른 메서드의 같은 이름 지역 막음을 안 받는다)
class SecXxe64 {
    void param(DocumentBuilderFactory f) throws Exception {
        f = DocumentBuilderFactory.newInstance();
    }

    void local() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    }
}
```
  `param` 의 대입 줄이 xxe(RED — 지금은 `local` 의 막음을 받아 조용). `positiveFixture` 줄 목록 끝에 그 줄 · `fixtures.json` security 새 줄 하나
- 새 시험 `parameterIsMethodScope`(위 꼴 인라인, 기대 1건)

### 5-65 — compare ㉔ xxe — Cyberneko 제외 · EntityResolver 는 글만(§3-4)
- 상수 `private static final List<String> HTML_PARSERS = List.of("nekohtml", "cyberneko");`
- `xmlFactories` PARSERS 루프 앞:
```java
boolean html = cu.getImports().stream().map(i -> i.getNameAsString().toLowerCase(Locale.ROOT))
        .anyMatch(n -> HTML_PARSERS.stream().anyMatch(n::contains)); // 5-65 — HTML 스캐너는 DOCTYPE 외부 개체를 안 푼다
for (ObjectCreationExpr o : cu.findAll(ObjectCreationExpr.class)) {
    if (PARSERS.contains(o.getType().getNameAsString()) && !(html && o.getArguments().isNonEmpty()) && !guarded(o, cu)) {
```
  인자 없는 `new SAXBuilder()` 는 그 파일에서도 그대로 지적. 새 메서드 없음(29)
- `rules.yaml:590` xxe fix → `'disallow-doctype-decl 을 켜고 외부 개체 기능을 끈다. EntityResolver 만으로는 막음이 아니다'`
- 픽스처 `security/neg.java`: import 묶음에 `import org.codelibs.nekohtml.parsers.SAXParser;` + 끝 `class SecNeko { void html() throws Exception { org.jdom2.input.SAXBuilder b = new org.jdom2.input.SAXBuilder(new org.jdom2.input.sax.XMLReaderSAX2Factory(true, SAXParser.class.getName()), null, null); } }`(RED — 지금 xxe)
- 새 시험 `nekoBuilderWithArgsIsSilent` — `hits("import org.codelibs.nekohtml.parsers.SAXParser; class A { void f() { SAXBuilder a = new SAXBuilder(new XMLReaderSAX2Factory(true, SAXParser.class.getName()), null, null);\n SAXBuilder b = new SAXBuilder(); } }")` → `b` 줄 하나
- 표본 기대: `check-jspwiki.json` xxe 4 → 3(V-55a). `WebContainerAuthorizer:324` 는 남는다(참 — 규칙 정의, 사용자 결정)

### 5-63a — 꼴 A' 판정 — `ResourceAst.Planner.wrap`(§3-5)
- 정의. **꼴 A'** = 같은 `BlockStmt` 안에서 ① 잇닿은 선언문 1~3(각각 `T x = <만든 자리>` — `isResource(type)` 또는 `Files.*` 스트림, `creation(init, cu)`/`filesStream`, 초기값 null 아님, `var` 아님) ② 그 뒤 어딘가 **한 묶음**(사이에 close 문장만 있는 잇닿은 close 들)의 bare close 가 묶음 이름 전부를 닫는다(묶음에 다른 이름의 close 가 섞여도 된다 — 그 줄은 몸에 남는다) ③ 선언 뒤 몸에서 묶음 이름을 다시 대입하지 않는다 ④ 마지막 묶음 close 뒤 문장들이 묶음 이름과 「몸 안에서 선언한 이름」 을 쓰지 않는다 ⑤ 몸(선언 끝 ~ 첫 묶음 close 전)에 문장이 하나 이상. 앵커 = 첫 선언문. **잇닿은 선언의 머리만 앵커** — 바로 앞 문장이 소유 자원 선언이면 빈 값(그 앞이 앵커가 된다)
- bare close = `x.close();` 또는 `if (x != null) x.close();`(블록 한 문장 포함). `closeQuietly(x)`·`try { x.close(); } catch …` 는 **아니다**(twr 로 옮기면 close 예외가 메서드 밖으로 나가 예외 경로가 바뀐다). bare close 는 이미 그 자리에서 컴파일되므로 `covered` 검사 없음
- `Twr(Node anchor, List<Res> resources, List<Statement> closes)` — 꼴 A·B·C 는 `TryStmt`, 꼴 A' 는 첫 선언 `ExpressionStmt`. `Res.assign()` 은 꼴 A' 에서 빈 값
- `apply:113-117`:
```java
if (on.contains(TRY_WITH_RESOURCES)) {
    for (TryStmt t : cu.findAll(TryStmt.class)) {
        plan(t, cu).ifPresent(p -> add(out, TRY_WITH_RESOURCES, t));
    }
    for (BlockStmt b : cu.findAll(BlockStmt.class)) { // 5-63 — 꼴 A'
        for (Statement s : b.getStatements()) {
            Planner.wrap(s, b, cu).ifPresent(p -> add(out, TRY_WITH_RESOURCES, s));
        }
    }
}
```
- `plan`:
```java
/** try(꼴 A·B·C) 또는 첫 선언문(꼴 A')을 try-with-resources 로 옮길 수 있으면 그 계획 — 판정 = 고침 전제 */
static Optional<Twr> plan(Node n, CompilationUnit cu) {
    if (n instanceof TryStmt t) {
        return Planner.of(t, cu);
    }
    return n instanceof ExpressionStmt s && s.getParentNode().orElse(null) instanceof BlockStmt b ? Planner.wrap(s, b, cu) : Optional.empty();
}
```
- `Planner` 새 메서드 다섯(17 → 22):
```java
/** 5-63 — 꼴 A': try 없이 같은 블록에서 자원을 선언(잇닿은 1~3)하고 뒤에서 bare close 한다. 앵커는 잇닿은 선언의 머리 */
static Optional<Twr> wrap(Statement first, BlockStmt blk, CompilationUnit cu) {
    int at = index(blk, first);
    if (at < 0 || at > 0 && owned(blk, at - 1, cu).isPresent()) {
        return Optional.empty(); // 머리가 아니다
    }
    List<Res> res = new ArrayList<>();
    for (int j = at; j < blk.getStatements().size() && res.size() < MAX_NAMES; j++) {
        Optional<Res> r = owned(blk, j, cu);
        if (r.isEmpty()) {
            break;
        }
        res.add(r.get());
    }
    for (int k = res.size(); k > 0; k--) { // 잇닿은 선언 가운데 close 가 한 묶음인 만큼만
        List<Res> group = res.subList(0, k);
        Optional<List<Statement>> closes = closeRun(blk, at + k, group);
        if (closes.isPresent() && tailClean(blk, at + k, closes.get(), group)) {
            return Optional.of(new Twr(first, List.copyOf(group), closes.get()));
        }
    }
    return Optional.empty();
}
/** j 번째 문장이 소유 자원 선언이면 Res — 꼴 A' 는 covered 를 안 본다(bare close 가 이미 컴파일된다) */
private static Optional<Res> owned(BlockStmt blk, int j, CompilationUnit cu) {
    Optional<VariableDeclarator> d = declaration(blk.getStatement(j));
    if (d.isEmpty() || d.get().getType().isVarType()) {
        return Optional.empty();
    }
    VariableDeclarator v = d.get();
    String type = typeOf(v);
    boolean stream = STREAM_TYPES.contains(type);
    Optional<Expression> init = v.getInitializer().map(ResourceAst::strip).filter(e -> !e.isNullLiteralExpr());
    if (init.isEmpty() || !isResource(type, cu) && !stream || !(stream ? filesStream(type, init.get()) : creation(init.get(), cu))) {
        return Optional.empty();
    }
    return Optional.of(new Res(v.getNameAsString(), v.getType(), (ExpressionStmt) blk.getStatement(j), Optional.empty(), init.get()));
}
/** {@code n.close();} 또는 그것을 감싼 {@code if (n != null)} — closeQuietly·try 로 감싼 close 는 아니다 */
private static Optional<String> bareClose(Statement s) {
    if (s instanceof IfStmt i && i.getElseStmt().isEmpty()) {
        Optional<String> guard = nullGuard(i.getCondition());
        Statement inner = single(i.getThenStmt());
        return guard.filter(g -> !(inner instanceof IfStmt) && bareClose(inner).filter(g::equals).isPresent());
    }
    return closeCall(s).filter(c -> CLOSE.equals(c.getNameAsString())).flatMap(Planner::target);
}
/** from 부터 묶음 이름 각각의 첫 bare close — 그 사이 문장이 전부 close 꼴(closeName)이어야 한 묶음. 묶음 이름이 하나라도 없으면 빈 값 */
private static Optional<List<Statement>> closeRun(BlockStmt blk, int from, List<Res> group) {
    List<Statement> ss = blk.getStatements();
    List<Statement> closes = new ArrayList<>();
    int lo = ss.size();
    int hi = -1;
    for (Res r : group) {
        int at = -1;
        for (int j = from; j < ss.size() && at < 0; j++) {
            at = bareClose(ss.get(j)).filter(r.name()::equals).isPresent() ? j : -1;
        }
        if (at < 0) {
            return Optional.empty();
        }
        closes.add(ss.get(at));
        lo = Math.min(lo, at);
        hi = Math.max(hi, at);
    }
    boolean run = ss.subList(lo, hi + 1).stream().allMatch(s -> closeName(s).isPresent());
    return run && lo >= from + ONE ? Optional.of(closes) : Optional.empty(); // 몸 문장 하나 이상
}
/** 몸에서 묶음 이름을 다시 대입하지 않고, 마지막 close 뒤 문장이 묶음 이름·몸 안 선언 이름을 안 쓴다 */
private static boolean tailClean(BlockStmt blk, int from, List<Statement> closes, List<Res> group) {
    List<Statement> ss = blk.getStatements();
    int last = closes.stream().mapToInt(c -> index(blk, c)).max().orElseThrow();
    Set<String> names = new HashSet<>();
    group.forEach(r -> names.add(r.name()));
    List<Statement> body = ss.subList(from, last);
    boolean reassigned = body.stream().anyMatch(s -> s.findAll(AssignExpr.class).stream()
            .anyMatch(a -> a.getTarget() instanceof NameExpr t && names.contains(t.getNameAsString())));
    body.forEach(s -> s.findAll(VariableDeclarator.class).forEach(d -> names.add(d.getNameAsString())));
    boolean usedAfter = ss.subList(last + 1, ss.size()).stream()
            .anyMatch(s -> s.findAll(NameExpr.class).stream().anyMatch(n -> names.contains(n.getNameAsString())));
    return !reassigned && !usedAfter;
}
```
  `index(blk, s)`(`:546`) 는 `same` 비교라 그대로 쓴다. `closeRun` 의 `lo >= from + ONE` 이 ⑤. `wrap` 복잡도 ≤ 15 확인(루프 둘 + 가지 셋). 꼴 A' 는 `resourceLeak` 과 같은 선언 줄에 선다(고치면 둘 다 사라짐 — `TOUCHED_MAY_VANISH` RESOURCE `*`, 꼴 C 와 같다)
- `ResourceFix.apply:61-62` — `(TryStmt) h.node()` 캐스트를 없앤다: `Node n = h.node(); int no = line(n);` · `done.stream().anyMatch(o -> o.isAncestorOf(n))` · `ResourceAst.plan(n, cu)` · 계획의 앵커가 `TryStmt` 가 아니면 이 행에서는 `skip(skips, no, "try 없는 선언·close 꼴 — 5-63b")` 뒤 `continue`(5-63b 가 뗀다). `done` 은 `List<Node>`
- 픽스처 `PosResource.java` 끝:
```java
/** 5-63 — 꼴 A': try 없이 같은 블록에서 선언·close(resourceLeak 도 같은 줄) */
class PosResource63 {
    void one(String p, byte[] b) throws IOException {
        OutputStream out = new FileOutputStream(p);
        out.write(b);
        out.close();
    }

    void two(String a, String b) throws IOException {
        InputStream in = new FileInputStream(a);
        OutputStream out = new FileOutputStream(b);
        out.write(in.read());
        in.close();
        out.close();
    }

    void guardedClose(String p, boolean w) throws IOException {
        if (w) {
            java.io.FileWriter fw = new java.io.FileWriter(p, true);
            fw.write("x");
            if (fw != null) {
                fw.close();
            }
        }
    }
}
```
  기대 — `one` 선언 줄 LEAK+TWR · `two` `in` 줄 LEAK+TWR·`out` 줄 LEAK · `guardedClose` `fw` 줄 LEAK+TWR. `NegResource.java` 끝(해제된 꼴만 — `Neg*` 는 모든 java 규칙 0):
```java
/** 5-63 — 꼴 A' 가 아닌 것(해제는 된 것): closeQuietly · 빌린 응답 스트림 */
class NegResource63 {
    void quietly(String p) throws IOException {
        InputStream is = new FileInputStream(p);
        is.read();
        IOUtils.closeQuietly(is);
    }

    void borrowed(HttpServletResponse response, byte[] b) throws IOException {
        OutputStream out = response.getOutputStream();
        out.write(b);
        out.close();
    }
}
```
  (`IOUtils`·`HttpServletResponse` 는 픽스처 머리 import 에 이미 있으면 그대로, 없으면 FQN 으로 — import 추가는 줄 번호를 밀므로 금지)
- 시험 `ResourceAstTest.tryWithResourcesFixture` 줄 목록 + 새 줄 · 새 `wrapIsTwr`(위 `one` 꼴 인라인, `Set.of(TRY_WITH_RESOURCES)`, 선언 줄 1건) · 새 `wrapNotTwr`(인라인 `Set.of(TRY_WITH_RESOURCES)` → `List.of()`) — close 뒤에 또 씀(`is.close(); return is.available();`) · try 로 감싼 close · 선언과 close 가 다른 블록(`if (w) { is.close(); }`) · 몸에서 재대입 · 몸 안 변수를 close 뒤 씀(`int n = is.read(); is.close(); return n;`) · 몸 없음(`InputStream is = new FileInputStream(p); is.close();`) · 둘째 선언만 소유인데 첫째가 빌린 것(`OutputStream a = response.getOutputStream(); OutputStream b = new FileOutputStream(p); … a.close(); b.close();` → `b` 가 머리라 1건 — 이것은 `wrapIsTwr` 쪽)
- 골든 `check/java.json` 새 줄(PosResource63) · 그 밖 diff 0. `steps.json` 불변(cleanup 픽스처는 5-63b)
- 표본 기대(V-55a): tryWithResources jeecg 3 → 25 안팎 · jeesite 1 → 5 안팎 · roller 1 → 5 안팎 · zheng 1 → 3 · ruoyi-classic 0 → 2 · jspwiki 0 → 1 안팎(`WikiJSPFilter:112` 는 빌린 것이라 빠진다) · egov·commons 불변 · resourceLeak 불변(같은 자리 둘)

### 5-63b — 꼴 A' 고침 — `ResourceFix.wrap`(§3-6)
- 줄 수 불변 고침: 첫 선언 줄 → `try (T1 a = …; T2 b = …) {` · 다른 선언 줄 삭제 · 묶음의 마지막 close 줄 → `}` · 다른 묶음 close 줄 삭제 · 몸 줄(첫 선언 다음 ~ 마지막 close 전, 삭제 줄·빈 줄 제외) 한 단 들여쓰기. 들여쓰기 한 단 = 「선언 줄 들여쓰기 − 블록을 가진 문장(`blk.getParentNode()`)의 시작 줄 들여쓰기」, 그 차가 빈 글이면 선언 줄에 탭이 있으면 `"\t"` 아니면 네 칸
- `Edit(List<Integer> deletes, String head, int finallyLine, String tail, Map<Integer, String> body)` — try 꼴은 `Map.of()`. `lines()` 에 `body.keySet()` 도. `apply` 끝에 `edit.get().body().forEach(e::replace)`. 꼴 A' 는 `finallyLine` = 마지막 close 줄, `tail` = 선언 줄 들여쓰기 + `}` 라 기존 `if (finallyLine > 0) replace` 가 그대로 닫는 줄을 쓴다
- `apply` — 5-63a 의 임시 skip 을 떼고 `Optional<Edit> edit = plan.flatMap(p -> p.anchor() instanceof TryStmt ? edit(p, lines) : wrap(p, lines));`
```java
/** 5-63 — 꼴 A': 선언 줄 → try 머리 · 마지막 close 줄 → 닫는 줄 · 몸 한 단 들여쓰기. 줄 수 불변 */
private static Optional<Edit> wrap(ResourceAst.Twr p, List<String> lines) {
    int first = line(p.anchor());
    List<Integer> deletes = new ArrayList<>();
    List<String> heads = new ArrayList<>();
    for (ResourceAst.Res res : p.resources()) {
        if (!alone(res.decl(), lines) || !oneLine(res.init())) {
            return Optional.empty();
        }
        if (line(res.decl()) != first) {
            deletes.add(line(res.decl()));
        }
        heads.add(slice(lines, res.type()) + " " + res.name() + " = " + slice(lines, res.init()));
    }
    if (!finalOnly(p) || !p.closes().stream().allMatch(c -> alone(c, lines))) {
        return Optional.empty();
    }
    int last = p.closes().stream().mapToInt(ResourceFix::line).max().orElseThrow();
    p.closes().stream().map(ResourceFix::line).filter(l -> l != last).forEach(deletes::add);
    String indent = indent(lines.get(first - 1));
    String step = step(lines, p.anchor(), indent);
    Map<Integer, String> body = new TreeMap<>();
    for (int k = first + 1; k < last; k++) {
        if (!deletes.contains(k) && !lines.get(k - 1).isBlank()) {
            body.put(k, step + lines.get(k - 1));
        }
    }
    return Optional.of(new Edit(deletes, indent + "try (" + String.join("; ", heads) + ") {", last, indent + "}", body));
}
/** 블록 한 단 — 선언 줄 들여쓰기에서 블록을 가진 문장 줄의 들여쓰기를 뗀 것. 못 재면 탭 또는 네 칸 */
private static String step(List<String> lines, Node anchor, String indent) {
    String outer = anchor.getParentNode().flatMap(Node::getParentNode).map(o -> indent(lines.get(line(o) - 1))).orElse("");
    if (indent.startsWith(outer) && indent.length() > outer.length()) {
        return indent.substring(outer.length());
    }
    return indent.contains("\t") ? "\t" : "    ";
}
```
  `alone(c, lines)` 가 거짓인 close(세 줄 `if (x != null) {`)는 SHAPE 건너뜀(지적은 남는다). `ResourceFix` 9 → 11. `apply` 복잡도 ≤ 15 확인
- 픽스처 `fixtures/cleanup/resource/Pos.java` 끝(머리 javadoc 에 「· 꼴 A' 하나·둘」 덧붙임):
```java
    void formAp(String path, byte[] b) throws IOException {
        OutputStream out = new FileOutputStream(path);
        out.write(b);
        out.close();
    }

    void formApTwo(String a, String b) throws IOException {
        InputStream in = new FileInputStream(a);
        OutputStream out = new FileOutputStream(b);
        out.write(in.read());
        in.close();
        out.close();
    }
```
  골든 `resource-Pos.java.txt` 기대:
```java
    void formAp(String path, byte[] b) throws IOException {
        try (OutputStream out = new FileOutputStream(path)) {
            out.write(b);
        }
    }

    void formApTwo(String a, String b) throws IOException {
        try (InputStream in = new FileInputStream(a); OutputStream out = new FileOutputStream(b)) {
            out.write(in.read());
        }
    }
```
  `steps.json` `resource/Pos.java` before 10 → 12, after 2 그대로(둘 다 고쳐짐), skips 둘 그대로. `CleanupTest.valueAndResourceCompile`(javac) 이 고친 글의 컴파일을 잰다
- 시험 `CleanupTest.resourceStage`(skips 글 그대로)·`fixturesGolden`·`valueAndResourceCompile` · `CleanupRoutesTest.rulesListsFourteen` 14 불변(규칙 수 그대로)
- 표본 기대(V-55a): `cleanup-roller.json` resource 1 → 5 안팎 · `cleanup-jspwiki.json` 0 → 1 안팎 · `cleanup-egov.json` 불변. `CleanupCompileCorpusTest`(commons) 영향 없음(commons 꼴 A' 0)

### V-55a — corpus 골든 재채점(§3-7)
- 순서: `bash scripts/mvn.sh -q -DskipTests verify` rc 0 뒤 → 설계 58 §3-13 의 갱신 명령 그대로(`-Dgroups=corpus -Dtest='CheckCorpusTest,LegacyCorpusTest,VcsCorpusTest,CliCorpusTest,JulietCorpusTest,SecurityBenchmarkCorpusTest,SqlRuleCasesCorpusTest,SqlFixtureCorpusTest,CleanupCorpusTest,CleanupCompileCorpusTest' -Djunit.timeout=10m -Dgolden.update=true`)
- 기대 변화(이것만): `check-{jeecg,jeesite,roller,zheng,ruoyi-classic,jspwiki}.json` tryWithResources 늘어남 · `check-jspwiki.json` xxe 4 → 3 · `java.catchThrowable`(commons 6·jspwiki 3·roller 2·jeecg 1 가운데 `throw new X(t)` 꼴만큼 줄어듦) · `java.floatEquals`(commons 12·egov 9·jeecg 1·ruoyi 1 가운데 래퍼끼리만큼 줄어듦) · `check-*-hits.txt` 같은 자리 · `cleanup-{roller,jspwiki}.json` resource · `vcs-egov.json` fullFindings(바뀌면). resourceLeak·refEquals·그 밖 규칙 불변. `security-juliet*`·`security-benchmark`·`ddl-*`·`gen-*`·`analyze-*`·`sql*`·`self-*` 불변
- 건수 전후를 이력에

### R-22 — 손 판정(§3-8)
- CLI `toolbox.bat check <표본> --groups java,security --json --profile example`(data 는 scratch) jeecg·jeesite·roller·zheng·ruoyi-classic·jspwiki·commons-lang·egov. 읽을 줄 — tryWithResources 새 것 **전부**(≤ 46 — 「맥락」 대표 자리와 맞대기) · catchThrowable 빠진 것 전부 · floatEquals 빠진 것 전부 · xxe jspwiki 셋(`WebContainerAuthorizer:324` 참(규칙 정의) 확인)
- 고침 꼴 눈 확인: `toolbox.bat cleanup <jeecg> --out <scratch 빈 폴더> --rules java.tryWithResources`(옵션 꼴은 `toolbox.bat --help`) → 결과 폴더의 `FileUtils.java`·`ZipUtil.java`·`JwtHttpUtil.java` diff 가 「선언 줄 → try 머리 · close 줄 → `}` · 몸 한 단」 인지, `ZipUtil:67` 의 두 줄 초기화식(`JarUtil:46` 꼴)이 SHAPE 건너뜀인지. 결과 폴더는 지운다(`out/` 밖 scratch)
- 오탐이면 음성 픽스처(또는 인라인 시험) 먼저 RED → 고침 ≤ 2 → V-55a 다시. 표는 compare ㉕ 「손 판정」, `파일:줄` 만

### V-55 — compare ㉕ · 정적 분석 · `--corpus`(§3-9)
- `design/compare-2026-10-28.md` 끝 `## ㉕ 재측정 — 번들 75(2026-11-08)`: 「설계 59. 올라간 후보 — 5-63 ②(꼴 A') · 5-64 ①~⑤ · ㉔ 「후보로」 xxe 둘」 · **뺀 것** — 5-63 ①(실측 1·꼴 B 0) · 라-1·9·12(번들 76) · 개발자 도구 탭·java.time·`--db` CI·MyBatis 판(그대로) · 「손 판정」 표(R-22) · 「측정」 표(규칙별 전후 — 설계 58 ㉔ 표 꼴) · 「드러난 것」 · 「후보로」(남은 것 — 라 셋 · 꼴 A' 의 `closeQuietly` 변형 건수 · 몸 안 변수를 close 뒤 쓰는 꼴(`int n = is.read(); is.close(); return n;`) 건수 — R-22 에서 센다)
- 순서(따로 낸다): `bash scripts/mvn.sh -DskipTests verify` rc 0 → `bash scripts/verify.sh` → `bash scripts/verify.sh --corpus`(앞뒤로 다른 세션 알림 — 메모리 `projectshop-priority`·`heavy-one-at-a-time`) → 끝 줄이 `--db` 를 부르지 않는 것 확인(부르면 그때 RAM 확인 뒤 혼자 돌린다)
- 벽시계(V-55a·`--corpus`)를 이력에

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7 · 행의 축·강제 지점과 diff · 꼴 A' 고침이 예외 경로를 바꾸는 자리가 있나(`closeQuietly`·try 감싼 close 가 bare 로 새지 않나) · `tailClean` 이 몸 안 선언 이름을 다 모으나(중첩 블록 선언 포함 — 보수적이면 됨) · `DeclaredTypes` 이동이 순수 이동인가 · 새 판정마다 양성·음성이 다 있나 · 골든 갱신이 §3-7 목록 안인가) → `gate-probe.sh` → `verify.sh`(끝 줄) → push → PR(본문에 행마다 시작 세 줄 표) → CI `gh api` 폴링 → claude[bot] 처분 → **머지 위임 있음** → 끝 보고

## 4. 실행 스텝
### S0 — 설계 문서·행·번들 표(**실행 세션(Opus)이 첫 손으로 한다** — 번들 74 전례)
- 대상: `design/59-bundle75.md`(A 절) · `PLAN.md` 「번들」 표 「번들 75」 행 + 분할표 새 행(`5-64c`·`5-64a`·`5-64b`·`5-65`·`5-63a`·`5-63b`·`V-55a`·`R-22`·`V-55`) + 5-63·5-64 행 상태(B 절) · `PROGRESS.md`(C 절).
- 검증: `bash scripts/doc-lint.sh design/59-bundle75.md` · `bash scripts/verify.sh`(docs 레인) · `git commit -m "docs: S0 설계 59 — 번들 75"`. push 안 함.

### 5-64c — `ValueAst` → `DeclaredTypes`
- 대상: 새 `core/check/DeclaredTypes.java` · `core/check/ValueAst.java`(여덟 삭제·호출 바꿈·`isBoxed` 가시성) · `JavaDefects:297`·`ResourceAst:203·224`·`SecurityAst:197` 한 줄씩(§3-1).
- 순서: 순수 이동이라 RED 없음(`Ruling:` 예외 — 리팩터, 시험 불변이 판정). 이동 → 컴파일 → 시험.
- 검증: `bash scripts/mvn.sh -q test -Dtest='ValueAstTest,JavaDefectsTest,ResourceAstTest,SecurityAstTest,JavaRulesTest,CleanupTest,SelfCheckTest,ArchitectureTest'` 초록 · `git status` 골든 변화 없음 · `bash scripts/mvn.sh -q -DskipTests verify` rc 0(PMD·ErrorProne) · `ValueAst` static 메서드 22 · `bash scripts/verify.sh` 초록 · 커밋 `chore: 5-64c ValueAst 선언 형식 풀기를 DeclaredTypes 로 — 메서드 30 → 22`.
- 의존: S0.

### 5-64a — `JavaDefects` ① ②
- 대상: `core/check/JavaDefects.java`(`rethrows`·`floatEquals`) · `resources/check/rules.yaml`(catchThrowable fix) · 픽스처 `PosDefects`(113 한 줄 + 끝)·`NegDefects`(끝) · `JavaDefectsTest`(하나 고침·하나 새로) · 골든 `check/java.json`(§3-2).
- 순서: 픽스처·시험 RED(`negativeFixture`·`rethrowIsNotCatchThrowable`·`wrapperPairIsRefEqualsOnly`) → GREEN → `-Dgolden.update=true -Dtest=JavaRulesTest` → diff 가 floatEquals 145 빠짐 + PosDefects64 줄뿐.
- 검증: `-Dtest='JavaDefectsTest,JavaRulesTest,ValueAstTest,SelfCheckTest'` 초록 · `mvn.sh -q -DskipTests verify` rc 0 · `verify.sh` 초록 · 커밋 `fix: 5-64a catchThrowable 은 매개변수를 던지는 throw 면 다시 던지기 · 래퍼끼리 비교는 refEquals 몫`.
- 의존: 5-64c.

### 5-64b — `SecurityAst` ③
- 대상: `core/check/SecurityAst.java`(`guarded` 두 줄) · `fixtures/check/security/pos.java`(끝) · `SecurityAstTest`(줄 목록 + 새 시험) · 골든 `check/fixtures.json`(§3-3).
- 순서: 픽스처·시험 RED → GREEN → `-Dgolden.update=true -Dtest=CheckRulesTest` → diff 가 security pos 새 줄뿐.
- 검증: `-Dtest='SecurityAstTest,CheckRulesTest,SelfCheckTest'` 초록(`SecurityAst` 29) · `mvn.sh -q -DskipTests verify` rc 0 · `verify.sh` 초록 · 커밋 `fix: 5-64b xxe 막음 범위 — 매개변수에 대입한 팩토리는 그 메서드 안에서만`.
- 의존: 5-64c.

### 5-65 — xxe Cyberneko 제외 · EntityResolver 글
- 대상: `core/check/SecurityAst.java`(`HTML_PARSERS`·`xmlFactories` 두 줄) · `resources/check/rules.yaml`(xxe fix) · `fixtures/check/security/neg.java`(import + 끝) · `SecurityAstTest`(새 시험)(§3-4).
- 순서: 픽스처·시험 RED(`negativeFixture` 가 xxe 1) → GREEN.
- 검증: `-Dtest='SecurityAstTest,CheckRulesTest,SelfCheckTest'` 초록 · 골든 diff 0(음성은 줄이 없다) · `mvn.sh -q -DskipTests verify` rc 0 · `verify.sh` 초록 · 커밋 `fix: 5-65 xxe — Cyberneko HTML 파서를 주는 SAXBuilder 는 제외 · EntityResolver 는 막음이 아니다(글)`.
- 의존: 5-64b.

### 5-63a — 꼴 A' 판정
- 대상: `core/check/ResourceAst.java`(`Twr`·`apply`·`plan`·`Planner` 다섯) · `core/check/ResourceFix.java`(`apply` 캐스트 제거 + 임시 skip) · 픽스처 `PosResource`·`NegResource`(끝) · `ResourceAstTest`(줄 목록·`wrapIsTwr`·`wrapNotTwr`) · 골든 `check/java.json`(§3-5).
- 순서: 픽스처·시험 RED → `ResourceAst` GREEN → `ResourceFix` 캐스트 제거(컴파일) → `-Dgolden.update=true -Dtest=JavaRulesTest` → diff 가 PosResource63 줄뿐.
- 검증: `-Dtest='ResourceAstTest,JavaRulesTest,CleanupTest,CleanupRoutesTest,SelfCheckTest'` 초록(`Planner` 22 · `wrap` 복잡도 ≤ 15 · `steps.json`·`self-check.txt`·`self-cleanup.txt` 불변) · `mvn.sh -q -DskipTests verify` rc 0 · `verify.sh` 초록 · 커밋 `feat: 5-63a try 없이 같은 블록에서 선언·close 하는 자원(꼴 A')도 tryWithResources 판정`.
- 의존: 5-64c(독립 — 차례만).

### 5-63b — 꼴 A' 고침
- 대상: `core/check/ResourceFix.java`(`Edit`·`apply`·`wrap`·`step`) · `fixtures/cleanup/resource/Pos.java`(끝) · 골든 `cleanup/resource-Pos.java.txt`·`cleanup/steps.json`(§3-6).
- 순서: 픽스처 + `fixturesGolden` RED → `ResourceFix` GREEN → `-Dgolden.update=true -Dtest=CleanupTest` → diff 가 `formAp`·`formApTwo` 고침 + steps before 12 뿐 → `valueAndResourceCompile` 초록(javac).
- 검증: `-Dtest='CleanupTest,CleanupRoutesTest,ResourceAstTest,SelfCheckTest'` 초록(`ResourceFix` 11 · `apply` 복잡도 ≤ 15 · `self-cleanup.txt` 불변) · `mvn.sh -q -DskipTests verify` rc 0 · `verify.sh` 초록 · 커밋 `feat: 5-63b 꼴 A' 고침 — 선언 줄을 try 머리로, close 줄을 닫는 줄로, 몸은 한 단 들여쓰기(줄 수 불변)`.
- 의존: 5-63a.

### V-55a — corpus 골든 재채점
- 대상: `golden/corpus/*`(§3-7).
- 검증: 갱신 명령 초록 · `git diff --stat src/test/resources/golden/corpus` 가 §3-7 목록 안 · 건수 전후·벽시계를 이력에 · 커밋 `test: V-55a 재채점 — 꼴 A'·다시 던지기·래퍼 비교·Cyberneko`.
- 의존: 5-63b.

### R-22 — 손 판정
- 대상: compare ㉕ 「손 판정」 표(§3-8) + 고침(≤ 2, 음성 먼저 RED).
- 검증: 표 + 합 줄 · 고침이 있으면 V-55a 다시 · 커밋 `docs: R-22 손 판정 — 꼴 A' 전수·빠진 catchThrowable·floatEquals·xxe`(고침은 따로 `fix:`).
- 의존: V-55a.

### V-55 — compare ㉕ · 정적 분석 · `--corpus`
- 대상: `design/compare-2026-10-28.md` ㉕(§3-9).
- 검증: `mvn.sh -DskipTests verify` rc 0 → `verify.sh` → `verify.sh --corpus` — 셋 초록(따로) · 끝 줄이 `--db` 를 안 부름 · 벽시계 · `doc-lint` · 커밋 `docs: V-55 재측정 — compare ㉕ + 정적 분석 + --corpus`.
- 의존: R-22.

### 마무리
§3 「마무리」 그대로. 머지 위임 있음.

## 5. 금지 사항
- `pure/`·portfolio·`docs/`·`core/gen`·`core/meta`·`core/analyze`·`core/vcs`·`web/`·`cli/`·`tools/` HTML/JS · `core/check` 의 `JavaRules`·`TxAst`·`ValueFix`·`Cleanup`(단계·되돌림·`TOUCHED_MAY_VANISH`)·`SpringMappings`·`Taint`
- 규칙 id 추가·삭제 금지(136) · `rules.yaml` 은 세 규칙의 message·fix 글만 · severity 변경 금지
- `ValueAst` — 이동 여덟 밖 논리 변경 금지 · `BOXED`·`FLOATING` 변경 금지 · `DeclaredTypes` 에 새 논리 금지(순수 이동)
- `JavaDefects` — `rethrows`·`floatEquals` 밖 변경 금지 · `catchBarrier`·`path` 변경 금지
- `SecurityAst` — `guards` 키 목록·`trustAll`·`anyGuard`·`hopGuarded`·`domOnly` 변경 금지 · `setEntityResolver` 를 막음으로 치는 코드 금지(사용자 결정) · 새 메서드 금지(29 유지 — 30 은 다음 분리 신호)
- `ResourceAst` — `released`·`handedOver`·`candidate`·`creation`·`borrowed` 변경 금지 · 바깥 메서드 ≤ 29 · `Planner` ≤ 25 · 꼴 A' 의 close 는 bare(+null 지킴)만 — `closeQuietly`·try 감싼 close 받기 금지 · `covered` 를 꼴 A' 에 붙이기 금지(불필요) · 선언 3 초과 금지
- `ResourceFix` — 줄 끼우기 금지(줄 수 불변) · 여러 줄 초기화식·한 줄에 다른 코드 건너뜀 그대로 · 들여쓰기 한 단은 §3-6 공식만
- 픽스처 — 기존 줄 삭제·삽입 금지(끝에 덧붙임, `PosDefects:113` 한 줄 교체만 예외) · `Neg*` 에 `resourceLeak`·`refEquals` 가 나는 꼴 금지
- 골든 — 의도된 변화(§3-2 java.json 둘 · §3-3 fixtures.json 한 줄 · §3-5 java.json 새 줄 · §3-6 `resource-Pos.java.txt`·`steps.json` · §3-7 목록) 밖 변화 금지 · `-Dgolden.update` 는 그 시험에만 · `self-check.txt`·`self-cleanup.txt` 변화 금지(우리 코드 꼴 A' 0 실측)
- 새 의존성 금지 · `--db` 는 `verify.sh` 끝 줄이 부를 때만

## 6. 최종 검증
- `bash scripts/mvn.sh -DskipTests verify` rc 0 · `bash scripts/verify.sh` · `bash scripts/verify.sh --corpus` 초록(따로) · 끝 줄이 더 부르는 레인 없음 · `gate-probe.sh` 10/10
- 골든: `git diff --stat origin/main -- src/test/resources/golden` 이 `check/java.json`·`check/fixtures.json`·`cleanup/resource-Pos.java.txt`·`cleanup/steps.json`·`corpus/check-*`·`corpus/cleanup-*`·`corpus/vcs-egov.json` 안 · 그 밖 0
- 시험: `ValueAstTest` 불변 · `JavaDefectsTest` +1·하나 뒤집음 · `SecurityAstTest` +2 · `ResourceAstTest` +2 · `CleanupTest` 골든 경유 · `CheckRulesTest` 136 · `CleanupRoutesTest` 14 · 스모크 14·12 불변
- 메서드 수: `ValueAst` 22 · `SecurityAst` 29 · `ResourceAst` 바깥 29 + `Planner` 22 · `ResourceFix` 11
- 수동(한 번, 이력에 한 줄): `run.bat` → 코드 정리 화면에 jeecg 폴더 → 자원 단계 미리보기에서 `FileUtils.java` 의 꼴 A' 고침 세 자리가 「선언 줄 try 머리 · 몸 한 단 · `}`」 로 보인다(쓰지 않음)
- compare ㉕ 표 + 뺀 것 + 「드러난 것」 + 「후보로」 · 이력에 건수·벽시계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- 5-64c 에서 골든 diff ≠ 0 또는 `SelfCheckTest` 새 키(순수 이동이 아니었다)
- 5-64a 에서 `java.json` diff 가 §3-2 둘 밖 · `Neg*` 에 새 양성
- 5-63a 에서 `steps.json`·`self-check.txt` 변화 · `wrap` 복잡도 > 15 로 쪼개야 하는데 `Planner` 25 초과
- 5-63b 에서 `valueAndResourceCompile` 빨강(고친 글이 컴파일 안 됨) · `self-cleanup.txt` 변화 · `cleanupChangesNothing` 변화
- V-55a 에서 §3-7 목록 밖 골든 변화 · tryWithResources 가 표본 어느 하나에서 60 넘음(판정이 샜다) · resourceLeak 변화 · `security-juliet*`·`security-benchmark` 채점 칸 변화
- R-22 에서 고침 셋째가 필요 · 꼴 A' 오탐이 참의 20% 넘음
- `pmd:check`·SpotBugs 새 짝 · `ArchitectureTest`·`VerifyLanesTest` 빨강
- `verify.sh` 끝 줄이 `--db` 를 부름(레인 지문이 예상과 다름 — 어느 파일이 db 레인인지 보고)
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① 꼴 A' 표본 실제 건수(스캐너 46 은 소유 판정 전 — `creation`·`borrowed` 뒤 수는 V-55a 가 준다, 중단선 60) ② `DeclaredTypes` 이동 뒤 `SelfCheckTest` 새 키 0(복잡도 ≤ 15 전부라 기대 0 — 5-64c 검증이 잰다) ③ `check/fixtures.json` 이 `java/` 픽스처의 정규식 규칙도 담는지(PosResource63 에 `System.*` 를 안 써서 영향 0 기대 — 5-63a 골든 diff 로 확인)
- **사용자만 정할 수 있는 것** — 없음(㉔ 둘·라 셋·머지 위임 전부 2026-11-08 결정)
