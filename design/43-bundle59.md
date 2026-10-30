# 설계 43 — 번들 59: 철저화 R3-2 — 보안 오염 추적(메서드 사이·파일 사이) · 출처 보강 · Benchmark helpers (Fable 설계 · Opus 실행, 2026-10-30)

## 맥락
R3-1(번들 58, 설계 42)이 메서드 **안** 오염 추적을 넣어 Benchmark 재현율 46 → 82, Juliet 정밀도 50 → 84 를 얻었다. 남은 미탐의 뿌리는 둘이다. ① **값이 메서드·파일 경계를 넘는다** — Juliet 서블릿 재현율 24(1134/4541): 흐름 변형 58 중 메서드 안은 01~17·21·31 뿐이고 41·42·45(같은 파일 다른 메서드·필드), 51~54(파일 둘~다섯 사슬), 61(다른 파일의 출처 메서드), 66~74(배열·컨테이너·Object·Vector·LinkedList·HashMap 을 다른 파일로), 81(추상 기반 클래스 `action`)은 전부 0. Benchmark 미탐 247 중 약 60 은 `new SeparateClassRequest(request).getTheParameter(…)`(표본에 없는 helpers 클래스)·`doSomething(request, param)`(같은 파일 private·내부 클래스) 경유. ② **출처 목록 빈칸** — Benchmark 미탐 109 는 `request.getParameterNames()` → `names.nextElement()` → `param = name` 꼴인데 `getParameterNames` 가 `Taint.SOURCES` 에 없다(2026-10-30 실측: 참 취약 파일 중 `getParameterNames` 가 든 것은 cmdi 15·ldapi 3·pathtraver 11·sqli 38·trustbound 7·xpathi 2·xss 33 **전부** 미탐). Benchmark 오탐 491 의 큰 몫은 `if ((7 * 42) - num > 200) bar = "safe"; else bar = param;` 상수 조건(흐름 무관의 대가)이다 — 이 번들은 안 건드린다(사용자 결정).

**사용자 결정(2026-10-30, 뒤집지 않는다)**
- R3-2 의 축은 **메서드 사이 오염 추적**(후보 넷 중 — SQL 정확도·Java 결함 묶음·프로그램 분석·구조 점검은 후보 표에 남는다)
- **머지 위임 있음** — CI 초록 + AI 리뷰 처분 뒤 실행자가 머지
- **Benchmark helpers 를 표본에 더한다**(`org/owasp/benchmark/helpers/`) — 「요청을 감싼 래퍼 클래스」 꼴을 잰다. `corpus-fetch.sh`·`corpus/MANIFEST`·`SOURCES.md` 가 바뀌어 **db 레인**이 번들에 든다(`verify.sh --db` 약 17분, 네트워크 한 번)
- **상수 조건 가지치기는 미룬다**(실물 코드에 없는 꼴 — 후보 표에 「상수 조건 가지치기(Benchmark 정밀도)」 로)
- **손 판정 행을 넣는다** — eGov `trustBoundary`·roller·jeecg `xssServlet` 실물 지적을 읽고 참/오탐을 compare ⑨ 에 적는다. 고치지 않는다
- 설계 41·42 의 결정은 그대로 — 에이전트 최소(마무리 독립 리뷰 하나), 검증 인프라 최적화 금지, 번들 하나·PR 하나

**라운드** — R1(번들 57) → R3-1(번들 58) → **R3-2(이 번들)** → R3-3…(남은 후보 — 입력이 서면 설계). 재측정은 번들마다 V 행.

실행자는 Opus. 차례는 `5-54a` → `5-54b` → `5-54c` → `V-39a` → `V-39b` → `R-8` → `V-39` → 마무리. 가지 `work/2026-10-30`(main `0a174c3` 에서). 번들 하나·PR 하나.

---

## 1. 목표와 범위
- 한 문장: 코드 검사의 오염 추적을 **메서드 사이·파일 사이**로 넓힌다 — 메서드마다 「매개변수·호출 결과·필드가 어느 싱크·어느 호출·반환에 닿나」 를 요약으로 뽑아 두고, 폴더 검사가 끝날 때(`finish`) 호출 관계를 이름으로 이어 고정점으로 퍼뜨린다. 출처에 `getParameterNames` 를 더하고, Benchmark helpers 를 표본에 넣어 Benchmark·Juliet·실물 넷으로 전·후를 잰다.
- 완료 시 체감: 컨트롤러가 `@RequestParam String name` 을 `fileService.download(name)` 으로 넘기고 `FileServiceImpl.download` 가 `new File(dir + name)` 을 하면 **서비스 쪽 그 줄**이 `security.pathTraversal` 로 보인다(인터페이스 → 구현체). 같은 클래스의 private 도우미, `new Foo().sink(v)`, 정적 필드 `Holder.data` 경유, 출처를 돌려주는 메서드(`String p = req.getTheParameter("x")`)도 잡는다. `golden/corpus/security-juliet.json` 서블릿 재현율 24 → 85 안팎, `security-benchmark.json` 재현율 82 → 90 이상. compare ⑨ 에 전·후 표 + 손 판정표.
- In scope: `core/check/Taint.java`(씨앗 재실행·출처·대입 꼴) · 신설 `core/check/TaintLink.java`(요약·색인·해석·고정점) · `JavaRules`(요약 수집·`observe`·`finish`) · 시험(`TaintTest`·신설 `TaintLinkTest`) · 픽스처 `fixtures/check/java/link/` · 골든(`check/java.json` 는 안 바뀜 — security 꺼짐, `self-check.txt` 변화 보고, `corpus/security-*`·`check-*`) · `scripts/corpus-fetch.sh`·`corpus/SOURCES.md`·`corpus/MANIFEST`(benchmark helpers) · `SecurityBenchmarkCorpusTest`(루트·단언) · `design/compare-2026-10-28.md` ⑨ · PLAN 4장 표본 문단 한 줄 · 분할표·번들 표 · PROGRESS.
- Out of scope: 흐름 감응(상수 조건 가지치기) · 정화의 범주화(`escapeHtml` 이 trustbound 까지 씻는 것) · properties 파일 읽기(hash 미탐 40·crypto 오탐 27) · 람다 몸 · 직렬화·리플렉션 경유(Juliet 75) · 심볼 해석기 · `JavaGraph`·`core/analyze` 수정 · 순수본·YAML 규칙 변경(새 규칙 id 없음 — `pure-rules.json` 불변) · 화면 · 새 Maven 의존성 · 검증 속도.

## 2. 현재 구조 요약(탐색 2026-10-30 실측)

**엔진** — `Taint.java`(388줄)·`TaintConst.java`(285줄), package-private. `Taint(Map<String, Rule.Def> on)` · `List<Hit> scan(CompilationUnit)`(`:131`) — `cu.findAll(CallableDeclaration)` 마다 `method(m)`(`:150`) → `Set<String> t`(오염 이름)·`writers` 를 초기화(바인딩 애너테이션 매개변수 + 컨트롤러 매개변수, `:156-161`) → `grow(m, t, writers)`(`:181`, 고정점 `ROUNDS = 10`: `VariableDeclarator` 초기값 · `AssignExpr` **대상이 `NameExpr` 일 때만**(`:192`) · `ForEachStmt` · `MUTATORS` 호출) → 싱크 판정 `call(name, scope, args, at, t, writers, out)`(`:222`, `SINKS` 표 × `scopeOk` × `argTainted`) + `cookieFlag`·`redirectView`·`ReturnStmt` 의 `redirectConcat`. 판정 `tainted(e, t)`(`:300`): `NameExpr ∈ t` · `BinaryExpr +` · `taintedWrapped`(`:314` — Conditional·Enclosed·Cast·ObjectCreation·ArrayCreation·ArrayInitializer·ArrayAccess·**FieldAccess 는 스코프만**(`this.f`·`X.f` 는 거짓)) · `taintedCall`(`:342` — ① 이름 ∈ `SOURCES` ② ∈ `SANITIZER` → 거짓 ③ 스코프 오염 && ∉ `SCOPE_CLEAN` ④ 인자 오염(`ARG_CLEAN` 예외)). **메서드 호출 결과는 인자가 오염이면 오염**(`:356`) — 피호출 몸은 안 본다. 매개변수는 애너테이션·컨트롤러 조건으로만 오염(`plainMethodParameterIsNotSource`, `TaintTest.java:96`). `SOURCES`(`:46`)에 `getParameterNames` 가 **없다**(`getHeaderNames` 는 있다). 람다 몸 밖만(`outsideLambda`, `:379`). 결과는 `rule|line` 으로 중복 제거(`:143`). 메서드 수·복잡도 — `grow` 12 · `taintedWrapped` 10 · `method` 9(상한 15), `Taint` 메서드 약 22(상한 30 — `TaintConst` 는 그래서 뗀 것, `PROGRESS.md:775`).

**배선** — `JavaRules`(634줄): 필드 `taint`(`:101`, `Taint.wants(on.keySet())` 면 생성), `apply(Source)`(`:123`) 가 파싱 → `imports` → `taint(cu, c)`(`:211`, `Hit` 마다 `add(c, rule, node)` = `finding(rule, s, line, excerpt(원문 줄))`, `:255`) → 나머지 규칙. 파싱 실패는 `hybridFallback`(정규식). **파일 사이 상태**는 `mappings`(dupMapping)뿐 — `observe(Source)`(`:179`)는 `java.dupMapping` 이 켜졌고 글에 `Mapping` 이 있을 때만 파싱, `finish()`(`:196`)가 중복 매핑 지적을 돌려준다. `RuleSet.Run.apply`(`RuleSet.java:259`) → `javaRules.apply`, `observe`(`:291`, `.java` 면 `javaRules.observe`), `finish`(`:312`) → `javaRules.finish()`. `RuleSet.observed(rel)`(`:217`)은 `.java` 면 참 — 변경분 검사에서 안 바뀐 `.java` 도 읽어 `observe` 로 넘긴다. `CheckRunner.runFiles`(`:109-161`): 파일마다 `Source` 를 만들어 `apply`/`observe`, 끝에 `run.finish()`, **변경분이면 `out.removeIf(f -> !changed.test(f.file()))`**(`:153`) — 안 바뀐 파일의 지적은 버린다. `CheckRunner.runText`(`:163`) 도 `apply` + `finish`. `Finding(file, line, group, rule, severity, excerpt)`, `Source(rel, text, encoding, lineEnding, lang)`.

**패키지 방향** — `core/analyze` → `core/check` 한 방향(`JavaGraph.java:40-42` 가 `JavaSource`·`Source`·`SpringMappings` 를 import). `ArchitectureTest.noPackageCycles` 가 고리를 막으니 **`check` 가 `JavaGraph` 를 쓸 수 없다**. `JavaGraph` 의 해석(`lookup`: 단순 이름 → import FQCN → 같은 패키지 → 전부, `implementors`: 인터페이스 단순 이름 → 구현 Cls, 지역 변수 타입은 안 씀)은 참고만 한다. `JavaSource`(JavaParser 3.26.3, JAVA_21 → JAVA_8, 심볼 해석기 없음)는 공용.

**채점 하네스** — `SecurityBenchmarkCorpusTest`(`:47` `TESTCODE = "src/main/java/org/owasp/benchmark/testcode"`, `:56` `CheckRunner.run(root.resolve(TESTCODE)…)`, `:112-114` 단언: CSV 행 2740 · `rows == r.files()` · skipped 0). 표본 `benchmark` 는 `corpus-fetch.sh:49` `"benchmark|OWASP-Benchmark/BenchmarkJava|f40c618c…|src/main/java/org/owasp/benchmark/testcode/*.java expectedresults-1.2.csv"`(`fetch_one` 은 `git ls-files -- <pathspec…>` 로 복사), `corpus/MANIFEST:28` `benchmark 2741 3765743a…`, `corpus/SOURCES.md:39`. **helpers 는 표본에 없다**(`ls C:/workspace/toolbox-corpus/benchmark/src/main/java/org/owasp/benchmark/` → `testcode` 뿐). `JulietCorpusTest`(`:77` `score`) — CWE 폴더마다 `CheckRunner.run(dir…)`(하위 `s01…` 포함 — 51a·51b 는 같은 `sNN` 에 있다) → 메서드 영역 `bad*`·`good*` 으로 채점, 서블릿 지표(`_Servlet`). 골든 `security-benchmark.json`(tp 1168 · fp 491 · fn 247 · P 70 · R 82)·`security-juliet.json`(서블릿 1134/4541 = 24) · `-fp`·`-fn` conformance. 실물 넷 `check-<표본>.json` 건수 + `-hits.txt`(파일 목록만 — 줄·규칙 없음). Juliet 2분 18초 · Benchmark 1분 53초 · `LegacyCorpusTest` 2분 8초(상한 corpus 10분, `-Djunit.timeout=10m`).

**Benchmark 미탐·오탐 실물(2026-10-30 분류)** — 미탐 247: `getParameterNames` 뿌리 109 · `SeparateClassRequest`(표본 밖) 약 60 · hash 40(`getProperty("hashAlg1","SHA512")` 실제 값) · 나머지는 둘이 겹친 것·`idc.search` 스코프(`ldapi`). 오탐 491: 상수 조건 가지(`(7*42)-num > 200`, 삼항 포함)가 대부분, `valuesList.get(1)`(「마지막 안전 값」) 일부. 같은 파일 `doSomething(request, param)`·`new Test().doSomething(…)`(내부 클래스 `Test` — 파일마다 같은 이름!)은 인자가 오염이면 결과가 오염이라 **지금도 잡힌다** — 그 미탐은 전부 `getParameterNames` 때문.

**Juliet 변형 꼴(CWE89 getParameter_Servlet_executeQuery 로 실측)** — 41 `badSink(data, request, response)`(같은 파일 private) · 42 `String data = badSource(request, response)`(반환) · 45 `dataBad = data; badSink(request, response)` 뒤 `String data = dataBad;`(private 필드) · 51a→51b `(new X_51b()).badSink(data, …)` · 52a→52b→52c, 53a~d, 54a~e 사슬 · 61a `String data = (new X_61b()).badSource(request, response)` · 66 `dataArray[2] = data` → `badSink(dataArray …)` → `String data = dataArray[2]` · 67 `dataContainer.containerOne = data` → `badSink(dataContainer …)` → `dataContainer.containerOne` · 68 `X_68a.data = request.getParameter(…)`(public static) → 68b `String data = X_68a.data` · 71 `(Object)data` → `(String)dataObject` · 72 `dataVector.add(0, data)` → `dataVector.remove(2)` · 73 LinkedList · 74 `dataHashMap.put(…)` → `get(2)` · 75 직렬화(`ObjectOutputStream.writeObject` → `toByteArray` — 별칭) · 81 `X_81_base baseObject = new X_81_bad(); baseObject.action(data, …)`(추상 기반, 구현은 `_81_bad`·`_81_goodB2G`·`_81_goodG2B`). 서블릿 파일은 변형마다 78~79, 「81」 묶음 180.

**픽스처·골든 함정** — `security/pos.java`·`neg.java` 는 순수본 동기(`PureCodeCheckTest`·`fixtures.json` security 줄 diff 0 — 설계 42 §7). 둘 다 `q(...)` 가 자기를 다시 부르지만(`pos.java:12` `q(request, model, id)`, `neg.java:13` `q(model, id)`) 인자가 오염이 아니고(클래스에 `@Controller` 없음) 싱크 줄은 이미 출처 직접 꼴이라 **메서드 사이 추적이 새 줄을 만들지 않는다**(실행자가 확인 — 중단 조건). `PosTaint.java`·`NegTaint.java` 는 메서드끼리 안 부른다. `JavaRulesTest` 는 security 를 끄므로(`:30`) 새 픽스처는 `check/java.json` 에 안 든다 — 배선 시험은 `TaintTest.hybridThroughRuleSet`(`:217`) 꼴(security 만 켠 `RuleSet.Run` + `CheckRulesTest.fixtureSources`). 자기 검사 `SelfCheckTest.mainMatchesBaseline`(`self-check.txt`) — 우리 코드에 메서드 사이 지적이 새로 서면 빨강(진짜일 수 있다 — 중단 조건).

**함정** — ① corpus 시험 상한 10분, 씨앗 재실행이 파싱보다 싸지만 횟수가 는다(실측은 V-39b) ② `metric.complexity` ≤ 15 · `classMethods` ≤ 30 · `params` ≤ 7(`Taint` 가 이미 22·`secretArg` 인자 7) → 새 몫은 **새 클래스** `TaintLink` 에 ③ PMD 는 CI `verify` 단계만 — 새 클래스는 손으로 `bash scripts/mvn.sh -q pmd:check` ④ `corpus/MANIFEST`·`SOURCES.md`·`corpus-fetch.sh` 는 db 레인(`verify-fingerprint.sh`) — push 앞 `--db` ⑤ `git ls-files -- 'src/main/java/org/owasp/benchmark/helpers/*.java'` 는 하위 폴더까지 맞는다(pathspec `*` 는 `/` 를 넘는다) — helpers 의 정확한 파일 수는 받아야 안다.

## 3. 설계 결정 — 전부 [고정]

1. **씨앗 재실행(seed re-run) — 라벨 집합 대신.** `Taint.method` 를 「씨앗 집합 + 출처 켜기/끄기」 로 돌리는 한 함수로 바꾼다: `Facts run(CallableDeclaration m, Set<String> seed, boolean sources)`. **출처 켬 + 씨앗 ∅(컨트롤러·바인딩 매개변수)** 이 오늘의 판정(직접 `Hit`) — 동작 불변. 요약은 같은 메서드를 씨앗 하나씩 **출처 끔**으로 다시 돌려 얻는다: 매개변수 `p_i` 마다 · 호출로 초기화된 지역 변수 `x = scope.m(args)` 마다 · 읽는 필드 키마다. 한 메서드의 `decls`·`assigns`·`loops`·`calls`·`sinks` 목록은 한 번만 뽑아 재사용한다. 버린 대안 — ⓐ 라벨 집합(`Map<이름, Set<라벨>>`, 한 번에 전부): 맞지만 `tainted`·`grow`·시험 40 을 다 바꾸고 복잡도 15 안에 넣기 어렵다 ⓑ 파일을 두 번 파싱(요약 뒤 다시 판정): Juliet·레거시 파싱 시간이 곱절.
2. **요약은 더하기만 — 씻지 않는다.** 오늘 「인자가 오염이면 호출 결과도 오염」(`taintedCall:356`)은 그대로. 피호출 요약은 오염을 **더 퍼뜨리는 데만** 쓴다(피호출이 매개변수를 안 돌려줘도 결과를 깨끗하게 만들지 않는다). 그래서 `returns` 는 「출처·오염 필드·오염 호출 결과를 돌려주나」 만 본다 — 매개변수 의존 반환은 필요 없다. 정밀도는 오늘과 같고 재현율만 오른다. 버린 대안 — 요약으로 씻기(모호 해석 하나가 미탐을 만든다).
3. **요약 자료형**(`TaintLink`, package-private, `core/check`):
   ```java
   record Ref(String type, String name, int argc)                 // 피호출 — type 은 단순 이름, null 이면 자기 클래스(this·무한정)
   record Site(Ref ref, int arg, int line)                        // 씨앗이 이 호출의 arg 번 인자에 닿는다
   record SinkAt(String rule, int line, String excerpt)           // 씨앗이 이 싱크에 닿는다 — 발췌는 싱크 줄 원문(규칙 3, 160자)
   record Facts(List<SinkAt> sinks, List<Site> calls, Set<String> fieldWrites, boolean returns)
   record MethodKey(String cls, String name, int argc)            // cls 단순 이름(중첩 클래스도 단순 이름)
   record CallInit(String var, Ref ref)                           // x = scope.m(args) — 씨앗 var 의 피호출
   record Summary(String file, MethodKey key, Facts src, Map<Integer, Facts> params, Map<CallInit, Facts> callVars, Map<String, Facts> fields)
   record ClassInfo(String name, String pkg, String file, String superName, List<String> interfaces, boolean abstractOrInterface,
                    Map<String, String> fieldTypes, Set<MethodKey> bodies, Map<String, String> imports)   // bodies = 몸 있는 메서드
   ```
   `fields` 의 키는 `<클래스 단순 이름>.<필드>` — 같은 클래스의 bare 필드명·`this.f` 와 다른 클래스의 `X.f` 가 한 키로 만난다. `fieldWrites` 도 같은 키.
4. **씨앗 하나의 Facts 뽑기**(출처 끔, `t = {seed}` 에서 `grow` 뒤): `sinks` = 오늘의 `call`/`redirectView`/`redirectConcat` 이 잡는 싱크(규칙·줄·발췌) · `calls` = 람다 밖 `MethodCallExpr`·`ObjectCreationExpr` 마다 인자 j 가 `tainted` 면 `Site(ref(c), j, line)` — `ref` 는 §3-5 · `fieldWrites` = 대입 대상이 필드 키(§3-6)이고 값이 오염 · `returns` = 어느 `ReturnStmt` 식이든 오염. 씨앗 상한 **메서드당 32**(매개변수 + 호출 초기화 변수 + 읽는 필드 순, 넘치면 뒤를 버리고 `TaintLink.dropped++` — V-39b 가 건수를 이력에). `cookieFlag` 은 씨앗과 무관(src 에만).
5. **피호출 해석 `Ref` 와 색인** — 호출 `scope.m(args)`: 스코프 없음·`this` → `type = null`; `super` → `superName`; `new T(..)` → `T`; `NameExpr v` → 메서드 안 `localTypes`(매개변수 타입 + `VariableDeclarator` 타입, **초기값이 `new T(…)` 면 그 T 가 선언 타입을 이긴다** — Juliet 81 `X_base b = new X_bad()`) → 없으면 클래스 필드 타입 → 없으면 대문자 시작이면 정적 호출 `T = v` → 아니면 **해석 안 함**(Site 를 안 만든다); `FieldAccessExpr this.f` → 필드 타입; 사슬 `a().b()`·그 밖 → 해석 안 함. 제네릭·배열 표시는 뗀다(`simple()`). `finish` 에서 색인 `byName: 단순 이름 → List<ClassInfo>` · `subtypes: 단순 이름 → 그것을 extends·implements 하는 ClassInfo(추이 닫힘)`. **후보 고르기 순서**: ① 같은 파일의 클래스 ② 호출자 파일 `imports` 의 FQCN 과 `pkg.name` 이 같은 것 ③ 같은 패키지 ④ 전체에 **하나뿐**이면 그것 ⑤ 아니면 **건너뛴다**(`TaintLink.ambiguous++`, 이력). `type = null` 은 자기 클래스 → 없으면 `superName` 사슬(위로 12 단계). 고른 클래스에 `MethodKey(name, argc)` 몸이 없거나 클래스가 인터페이스·추상이면 `subtypes` 중 몸 있는 것 **전부**(Spring 인터페이스 → 구현체, Juliet 81). 버린 대안 — `JavaGraph` 재사용(패키지 방향) · 모호하면 전부 걷기(JavaGraph 식 — 오염은 모호 하나가 오탐을 만든다).
6. **필드·배열·객체 필드 대입(`grow` 보강, 메서드 안)** — `AssignExpr` 대상이 ⓐ `ArrayAccessExpr` → 배열 이름을 `t` 에(Juliet 66) ⓑ `FieldAccessExpr` 이고 스코프가 지역 이름(`localTypes` 에 있음) → 그 이름을 `t` 에(67 — 변경자처럼) ⓒ `FieldAccessExpr this.f` · 대문자 스코프 `X.f` · bare 이름이 클래스 필드(`FieldDeclaration` 이름 집합) → **필드 키 쓰기**(`fieldWrites`). 읽기 — 씨앗이 필드 키일 때 키를 그대로 `t` 에 넣고, `taintedWrapped(FieldAccessExpr)` 에서 스코프가 `this` → `t.contains(cls + "." + f)`, 스코프가 대문자 `NameExpr X` → `t.contains("X." + f)`, bare `NameExpr f` 가 클래스 필드면 `t.contains(f) || t.contains(cls + "." + f)`. 메서드 안에서 bare 필드에 오염을 넣는 오늘 동작(`t.add(name)`)은 그대로(같은 메서드 안 전파) + 키 쓰기를 더한다.
7. **고정점(`TaintLink.finish`)** — 집합 `taintedParam ⊆ (MethodKey, i)` · `taintedField ⊆ 키` · `returnsTainted ⊆ MethodKey`. 씨: 모든 요약의 `src.calls` → 해석한 피호출마다 `(callee, arg)` · `src.fieldWrites` → 필드 · `src.returns` → `returnsTainted(자기)`. 반복(상한 50 바퀴, 바뀜 없으면 끝): `(m, i)` 새로 오염 → `m.params[i]` 의 sinks 지적·calls 전파·fieldWrites 전파 / 필드 키 새로 오염 → 그 키를 읽는 모든 요약의 `fields[키]` 로 같은 셋 + `returns` → `returnsTainted` / `m` 이 새로 `returnsTainted` → `m` 을 피호출로 둔 모든 `callVars` 항목(`CallInit.ref` 해석이 `m` 을 포함)의 Facts 로 같은 셋. 지적은 `Finding(요약.file, line, "security", rule, severity, excerpt)` — **싱크 줄, 피호출 파일**. 중복 제거 `file|rule|line`, 그리고 `apply` 가 이미 낸 직접 지적과도 중복 제거(`TaintLink.seen` 에 `apply` 의 Hit 를 등록). 버린 대안 — 호출 줄에 지적(싱크가 둘이면 어느 규칙인지 모호·같은 싱크가 호출자마다 중복).
8. **배선** — `JavaRules`: 필드 `private final TaintLink link`(`taint != null` 이면 생성). `apply`: `taint(cu, c)` 뒤 `link.summarize(cu, s, hits)`(hits = 직접 지적 — seen 등록). `observe`: 조건을 `(dupMapping 조건) || link != null` 로 넓혀 `.java` 를 파싱하고 `link.summarize(cu, s, List.of())` 만(지적 없음). `finish`: 기존 `out` 에 `link.finish(on)` 을 더한다(`on` 으로 `Rule.Def` 의 severity·group). `RuleSet.Run`·`CheckRunner` 는 **안 바꾼다** — `finish` 와 `removeIf(!changed)` 가 이미 있다. 변경분 검사의 뜻: 안 바뀐 파일의 요약은 모으고, 지적은 바뀐 파일 것만(기존 `removeIf`) — 「바뀐 서비스가 안 바뀐 컨트롤러의 값을 받는다」 가 잡힌다. `runText`(붙여넣기 한 글)도 `finish` 를 타므로 같은 클래스 안 메서드 사이가 된다.
9. **출처 보강** — `SOURCES` 에 `getParameterNames` 추가(Benchmark 109). 그 밖 목록(`BINDING`·`NOT_INPUT`·`SCOPE_CLEAN`·`ARG_CLEAN`·`SANITIZER`·`MUTATORS`·`SINKS`)은 **안 바꾼다**. 버린 대안 — 정화 범주화·`idc` 스코프(후보).
10. **Benchmark helpers** — `corpus-fetch.sh:49` 의 pathspec 에 `src/main/java/org/owasp/benchmark/helpers/*.java` 를 더한다(같은 커밋 `f40c618c…`). `corpus/SOURCES.md:39` 「잘라 온 것」 에 helpers 한 구절. `bash scripts/corpus-fetch.sh benchmark` → `corpus/MANIFEST` 의 benchmark 줄 갱신(파일 수·지문). `SecurityBenchmarkCorpusTest`: 검사 루트를 `src/main/java/org/owasp/benchmark`(testcode + helpers)로, 단언 `rows == r.files()` → `rows == testcode 파일 수`(`CorpusFiles.files("benchmark/" + TESTCODE, "*.java").size()`), 채점은 CSV 행(testcode)만 그대로, `none` A 게이트는 전체 파일. helpers 자체의 지적은 세지 않는다. Juliet 은 그대로(MANIFEST 밖, `assumeTrue`).
11. **매핑·지표** — Benchmark·Juliet `MAP` 불변. Juliet `row()` 에 변형 묶음 지표를 더한다: `variants: {inMethod, sameFile, crossFile}` 각 `{files, tp, fn, recallPct}` — 파일 이름 끝 번호로 묶는다(`_01~_17,_21,_22a,_22b,_31` → inMethod · `_41,_42,_45` → sameFile · 나머지 → crossFile; `_bad`·`_good*`·`_base` 접미는 번호 앞에서 뗀다). 서블릿 파일만. 수치는 게이트가 아니다. 목표(안 맞아도 빨강 아님): Juliet 서블릿 재현율 ≥ 85 · crossFile ≥ 80 · Juliet 정밀도 ≥ 80 유지 · Benchmark 재현율 ≥ 90 · Benchmark 정밀도 ≥ 65(오탐이 늘면 안 된다 — 늘면 원인을 이력에).
12. **손 판정 R-8** — V-39b 재채점 **뒤** 수치로. 대상: eGov `security.trustBoundary`(전 15) · roller `security.xssServlet`(전 13) · jeecg `security.xssServlet`(전 17), 후 건수가 더 많아도 **규칙·표본 쌍마다 20 건 상한**(파일 경로 순). 방법: `toolbox.bat check <표본 폴더> --groups security --profile demo`(또는 `CheckRunner` 를 부르는 일회성 JUnit 을 `src/test/java/.../scratch/` 에 쓰지 **않는다** — CLI 결과 xlsx·이력에서 파일·줄·규칙을 읽는다) → 싱크 줄과 위·아래 10줄을 읽고 **참/오탐/모름** + 한 구절(「세션에 넣는 값이 DB 조회 결과」 등). 코드 본문은 compare 에 **안 옮긴다** — 파일 상대 경로·줄·판정·구절만(규칙 3). 표는 compare ⑨ 「손 판정」 절. 오탐 꼴은 묶어 「드러난 것」 에 후보로.
13. **compare ⑨** — `design/compare-2026-10-28.md` 끝에 `## ⑨ 재측정 — R3-2(번들 59, 2026-10-30)`: 올라간 후보 표시(나-1 후속 「메서드 사이」·`getParameterNames`) · Benchmark 범주 11 P/R 전(⑧ 후) → 후 · Juliet CWE 15 P/R + 서블릿 + 변형 묶음 셋 · 실물 넷 바뀐 규칙 건수 · 시험 벽시계(Benchmark·Juliet·Legacy, 전·후) · `ambiguous`·`dropped` 건수 · 손 판정표(§3-12) · 「드러난 것」. ①~⑧·「후보 행 제안」 은 안 고친다. 「후보 행 제안」 에 새 후보는 ⑨ 「드러난 것」 끝에 「후보로: …」 한 줄씩(상수 조건 가지치기 · 정화 범주화 · properties 읽기 · `idc` 스코프 · 직렬화).
14. 행 접두 — 엔진 `5-54a·b·c`, 표본·재측정 `V-39a·b`·`V-39`, 손 판정 `R-8`. 설계 문서 `design/43-bundle59.md`(이 글). 가지 `work/2026-10-30`.

## 4. 실행 스텝

### S0 — 설계 문서·행·번들 표 (설계 세션이 했다)
- 대상: 신설 `design/43-bundle59.md`(이 플랜의 「맥락」~8절 그대로, 머리 인용문 삭제) · `PLAN.md`(15장 번들 표 `:543` 「번들 58」 행 아래에 「번들 59」 행 · 16장 분할표 `:1036` 「V-38」 행 아래에 `5-54a`·`5-54b`·`5-54c`·`V-39a`·`V-39b`·`R-8`·`V-39` 일곱 행, 양식은 번들 58 행과 같은 압축 꼴 「설계 43 §… 가 원본 + 요지 + 강제 지점」) · `PROGRESS.md`(`:10` 「열려 있는 것」 머리를 「**번들 59(설계 43, 철저화 R3-2 — 메서드 사이 오염 추적) 설계 끝 — 가지 `work/2026-10-30`**, 다음은 「번들 해」」 로, 이력 끝에 S0 줄 — 해시 칸 `(이 커밋)`).
- 절차: `git checkout -b work/2026-10-30 main` → 세 파일 쓰기 → `bash scripts/doc-lint.sh design/43-bundle59.md PLAN.md PROGRESS.md` 출력 없음 → `bash scripts/verify.sh`(docs 레인) 초록 → `git commit -m "docs: S0 설계 43 — 번들 59 행·번들 표(철저화 R3-2 메서드 사이 오염 추적)"`(따로 낸다). push 는 실행 세션의 마무리에서.
- 검증: 커밋 하나, `git status` 깨끗, 도장 있음.

### 5-54a — `Taint` 씨앗 재실행 틀 + 출처·대입 보강 + 단위 시험
- 대상: `src/main/java/kr/ejg/toolbox/core/check/Taint.java` · `src/test/java/kr/ejg/toolbox/core/check/TaintTest.java`
- 변경:
  - `method(m)` 을 둘로 가른다 — 메서드의 노드 목록을 한 번 뽑는 `record Body(CallableDeclaration<?> m, List<VariableDeclarator> decls, List<AssignExpr> assigns, List<ForEachStmt> loops, List<MethodCallExpr> calls, List<ObjectCreationExpr> news, List<ReturnStmt> returns, Map<String, String> localTypes, Set<String> fieldNames, String cls)` 와 `Facts run(Body b, Set<String> seed, boolean sources)`:
    ```java
    /** 씨앗 집합에서 고정점을 키우고 싱크·호출 인자·필드 쓰기·반환을 적는다(설계 43 §3-1·4). sources=false 면 요청 출처를 안 켠다 */
    TaintLink.Facts run(Body b, Set<String> seed, boolean sources)
    List<Hit> scan(CompilationUnit cu)              // 그대로 — 메서드마다 run(b, 초기 매개변수 집합, true) 의 sinks 를 Hit 로
    static Body body(CallableDeclaration<?> m)       // 목록·localTypes(매개변수 + 선언, new T 우선)·fieldNames·cls
    ```
    `tainted`/`taintedCall`/`taintedWrapped`/`grow`/`flow`/`argTainted`/`scopeOk` 에 `sources` 플래그를 실어 나르는 방법 — **정적 메서드 시그니처는 그대로 두고** `Set<String> t` 에 표식 원소 `"\u0000src"` 를 넣는 것으로 「출처 켬」 을 나타낸다(`taintedCall` 의 `SOURCES.contains(n)` 앞에 `t.contains(SRC_ON)` 을 &&). 시험 도우미·`redirectConcat`·`writerCall` 호환. 복잡도가 15 를 넘는 메서드는 노드 종류별로 더 나눈다(`taintedWrapped` 10 → FieldAccess 분기를 `taintedField` 로 뗀다).
  - `SOURCES` 에 `"getParameterNames"`.
  - `grow`: §3-6 ⓐⓑⓒ — `AssignExpr` 대상 `ArrayAccessExpr` → 배열 이름 · `FieldAccessExpr` 스코프가 `localTypes` 의 이름 → 그 이름 · 필드 키(`this.f`·`X.f`·bare 필드) → `fieldWrites`(Facts) + bare 는 오늘처럼 `t` 에도.
  - `taintedField`(새): `this.f` → `t.contains(cls + "." + f)` · `X.f`(대문자 스코프) → `t.contains("X." + f)` · 그 밖 → 스코프 오염(오늘 동작). `NameExpr f` 가 `fieldNames` 에 있으면 `t.contains(f) || t.contains(cls + "." + f)`.
  - `TaintTest` 추가(각 `@Test` 단언 필수): ㉓ `getParameterNames` — `Enumeration<String> names = request.getParameterNames(); String n = (String) names.nextElement(); new File(n)` → 1 ㉔ 배열 요소 대입 — `String[] a = new String[3]; a[1] = request.getParameter("p"); new File(a[1])` → 1 ㉕ 객체 필드 대입 — `Box b = new Box(); b.v = request.getParameter("p"); new File(b.v)` → 1 ㉖ 씨앗 run — `run(body, {"p"}, false)` 로 `void f(String p) { new File(p); g(p, 1); this.k = p; return p; }` → sinks 1·calls `[Site(Ref(null,"g",2),0,…)]`·fieldWrites `{"A.k"}`·returns true ㉗ 출처 끔 — 같은 몸에 `new File(request.getParameter("x"))` 가 있어도 `run(…, false)` 의 sinks 에 안 든다 ㉘ 필드 키 읽기 — 씨앗 `{"A.k"}` 로 `void h() { new File(this.k); new File(k); }` → sinks 2 ㉙ 정적 필드 — 씨앗 `{"H.data"}` 로 `new File(H.data)` → 1 ㉚ 씨앗 상한 — 매개변수 40 개 메서드에서 `TaintLink.dropped` 가 8(5-54b 뒤) — 5-54a 에서는 `body()` 가 씨앗 후보 목록을 돌려주는지만.
- 검증: `bash scripts/mvn.sh -q test -Dtest=TaintTest` 초록(기존 40 + 새 8) · `bash scripts/mvn.sh -q pmd:check` 0 · `bash scripts/verify.sh` 초록(`SelfCheckTest` 복잡도·메서드 수 — `Taint` 가 30 을 넘으면 `Body`·`taintedField` 를 `TaintLink` 쪽 정적 도우미로 옮긴다).
- 의존: 없음.

### 5-54b — `TaintLink` 요약·색인·해석·고정점 + `JavaRules` 배선 + 단위 시험
- 대상: 신설 `src/main/java/kr/ejg/toolbox/core/check/TaintLink.java` · `JavaRules.java`(`:101` 필드·생성자·`apply`·`observe`·`finish`) · 신설 `src/test/java/kr/ejg/toolbox/core/check/TaintLinkTest.java`
- 변경:
  - `TaintLink`(§3-3·5·7):
    ```java
    /** 메서드 사이·파일 사이 오염 — 파일마다 요약을 모으고 finish 에서 이름으로 이어 고정점(설계 43 §3-3~8). JavaRules 가 가진다 */
    final class TaintLink {
        static final int SEEDS_MAX = 32, ROUNDS = 50, SUPER_MAX = 12;
        TaintLink(Taint taint, Map<String, Rule.Def> on)
        void summarize(CompilationUnit cu, Source s, List<Taint.Hit> direct)   // ClassInfo·Summary 를 모은다, direct 는 seen 에
        List<Finding> finish()                                                 // 색인 → 씨 → 고정점 → Finding, 뒤에 상태를 비운다
        int ambiguous(); int dropped();                                        // V-39b 가 이력에 적는 수(시험·통계용 — 로그 아님)
        private List<ClassInfo> resolve(Ref ref, ClassInfo from)               // §3-5 순서
        private List<MethodKey> targets(Ref ref, ClassInfo from)               // 몸 있는 메서드(하위 타입 포함)
    }
    ```
    `Finding` 은 `new Finding(file, line, d.group(), rule, d.severity(), excerpt)` — `JavaRules.finding` 과 같은 꼴(`on.get(rule)`). `summarize` 가 메서드마다 `Taint.body` → `run(b, ∅+초기, true)` 는 `apply` 가 이미 했으니 **다시 안 돈다**: `apply` 가 `taint.scan` 대신 `link.summarize` 를 부르고 `summarize` 가 src 실행의 `sinks` 를 `Hit` 로 돌려준다(`List<Taint.Hit> summarize(cu, s, boolean emit)`) — 한 번 파싱·한 번 src 실행. `observe` 는 `emit=false`.
  - `JavaRules`: `taint(cu, c)` → `for (Hit h : link.summarize(cu, c.s, true)) add(...)`. `observe`: `if ((on.containsKey("java.dupMapping") && text.contains("Mapping")) || link != null)` 로 파싱하고 link 면 `summarize(cu, s, false)`. `finish`: `out.addAll(link.finish())` (link != null). `Taint` 필드는 `link` 안으로(생성자에서 `new TaintLink(new Taint(on), on)`); `hybridFallback` 그대로. 기존 복잡도 기준선(`JavaRules.apply` 19)을 안 올리게 분기는 도우미로.
  - `TaintLinkTest` — security 만 켠 `RuleSet.Run`(`TaintTest.hybridThroughRuleSet` 의 프로필 꼴)으로 **글 여러 개**를 `apply`/`observe` 한 뒤 `finish` 의 지적을 `file:line rule` 로 단언. 사례(각 `@Test`): ① 같은 클래스 private — `@RequestParam String p` → `helper(p)` → `new File(x)` → 피호출 줄 1 ② 반환 출처 — `String d = src(request)` + `src` 가 `return request.getParameter("n")` → 호출자 `new File(d)` 1 ③ 파일 둘 — `A.java` 가 `new B().sink(p, 1)` · `B.java` `sink(String v, int n) { exec(v) }` → `B.java:<줄> osCommand` ④ 사슬 셋(A→B→C) ⑤ 인터페이스 → 구현 — `FileService svc` 필드 + `FileServiceImpl implements FileService` → 구현체 줄 ⑥ 추상 기반 — `Base b = new Bad(); b.action(p)` → `Bad.action` 만(`Good.action` 은 0) ⑦ 정적 필드 — `H.data = request.getParameter("x")` 한 파일, `new File(H.data)` 다른 파일 → 1 ⑧ private 필드(같은 클래스 메서드 둘) ⑨ 컨테이너 — `box.v = p; use(box)` → `use` 안 `new File(b.v)` ⑩ 배열·Vector·HashMap(72·74 꼴) ⑪ 모호 — 같은 단순 이름 `Svc` 가 두 패키지, import 없음 → 0 + `ambiguous() == 1` ⑫ 같은 파일 우선 — 내부 클래스 `Test` 가 다른 파일에도 있어도 자기 파일 것 ⑬ 씻지 않음 — 피호출이 매개변수를 안 돌려줘도 `String r = f(p); new File(r)` 은 1 ⑭ 변경분 — `observe(컨트롤러)` + `apply(서비스)` → 서비스 줄 지적 1 ⑮ 중복 — 직접 지적과 같은 줄이 두 번 안 든다 ⑯ `runText` 한 글 안 메서드 사이 1 ⑰ 람다 안 호출 인자는 Site 가 안 된다 ⑱ 깨끗 — 모든 호출자가 상수를 넘기면 0 ⑲ 씨앗 상한 — 매개변수 40 → `dropped() == 8`, 앞 32 는 산다 ⑳ `getParameterNames` 가 `names.nextElement()` 를 거쳐 다른 메서드로.
- 검증: `bash scripts/mvn.sh -q test -Dtest=TaintTest,TaintLinkTest,CheckRulesTest,JavaRulesTest,PureRulesJsonTest,SelfCheckTest,ArchitectureTest` 초록 · `check/fixtures.json`·`check/java.json`·`pure-rules.json` **diff 0** · `bash scripts/mvn.sh -q pmd:check` 0 · `bash scripts/verify.sh` 초록. `self-check.txt` 가 바뀌면 **멈춘다**(§7).
- 의존: 5-54a.

### 5-54c — 메서드 사이 픽스처 + 배선 시험 + 자기 코드 확인
- 대상: 신설 `src/test/resources/fixtures/check/java/link/PosLinkController.java`·`PosLinkService.java`·`PosLinkServiceImpl.java`·`PosLinkHolder.java`·`NegLinkController.java`·`NegLinkServiceImpl.java` · `TaintTest.java`(`linkThroughRuleSet`) · `CheckRulesTest.fixturesGolden`(`java/` 는 이미 건너뜀 — 그대로)
- 변경: 픽스처는 Spring 꼴 — 컨트롤러(`@RequestParam String name` → `svc.download(name)` · `Holder.last = name` · `return helper(name)`), 인터페이스, 구현체(`new File(dir + name)` · `jdbc.query("…" + q)` · `Holder.last` 읽어 `exec`), `NegLink*` 는 같은 사슬에 상수·`parseInt` 값만. `TaintTest.linkThroughRuleSet(@TempDir)`: `CheckRulesTest.fixtureSources(FIXTURES.resolve("java/link"), data)` 를 전부 `apply` 하고 `finish` → 기대 목록(파일:줄 규칙) 인라인, `NegLink*` 0. 픽스처가 `java.declarationOrder`·`metric.*` 에 걸려도 상관없다(security 만 켠다). **`CheckRulesTest.fixtureSources` 는 `Files.walk`(재귀)** 라 `JavaRulesTest.fixturesGolden`(`:40`)이 `java/link/` 도 읽어 `check/java.json` 에 넣는다 → 그 루프 첫 줄에 `if (s.rel().startsWith("link/")) { continue; } // 설계 43 — 메서드 사이 픽스처는 TaintTest.linkThroughRuleSet 이 잰다` 한 줄, 골든 diff 0. `TaintTest.hybridThroughRuleSet` 은 `fileName().contains("Taint")` 로 거르니 `link/` 가 안 섞인다.
- 검증: 위 시험 초록 · `bash scripts/verify.sh` 초록.
- 의존: 5-54b.

### V-39a — Benchmark helpers 표본화 + Benchmark 시험 루트
- 대상: `scripts/corpus-fetch.sh:49` · `corpus/SOURCES.md:39` · `corpus/MANIFEST`(benchmark 줄) · `SecurityBenchmarkCorpusTest.java`(`:47`·`:56`·`:112-114`)
- 변경: §3-10. `bash scripts/corpus-fetch.sh benchmark`(네트워크 — 캐시 `cache/benchmark@f40c618c…` 가 있으면 다시 안 받는다; `git ls-files` 가 helpers 를 집는지 파일 수로 확인) → `bash scripts/corpus-check.sh` 초록. 시험: 루트 `src/main/java/org/owasp/benchmark`, 단언 `rows == testcode 파일 수`, skipped 0 그대로.
- 검증: `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest=SecurityBenchmarkCorpusTest -Djunit.timeout=10m -Dgolden.update=true` → 골든 diff 를 읽는다(helpers 만으로 바뀌는 것은 `SeparateClassRequest`·`Thing` 경유 재현율 — 수치를 이력에) · `corpus/MANIFEST` 한 줄 diff · `bash scripts/verify.sh` 초록(끝 줄이 db·corpus 를 알린다 — V-39 에서).
- 의존: 5-54c(엔진이 먼저 — 한 번에 재채점).

### V-39b — 재채점 + 변형 묶음 지표 + 시간 실측
- 대상: `JulietCorpusTest.java`(`row()`·변형 묶음 §3-11) · `golden/corpus/security-*.json`·`-fp`·`-fn`·`check-*.json`·`-hits.txt`
- 변경: `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='SecurityBenchmarkCorpusTest,JulietCorpusTest,CheckCorpusTest,LegacyCorpusTest' -Djunit.timeout=10m -Dgolden.update=true` → diff 를 읽고 **전·후를 이력에**(Benchmark 범주 11 · Juliet CWE 15 + 서블릿 + 변형 묶음 · 실물 넷 규칙 건수 · 벽시계 셋 · `ambiguous`·`dropped` — 둘은 시험이 골든 json 의 `_link: {ambiguous, dropped}` 로 적는다(`LegacyCorpusTest`·`SecurityBenchmarkCorpusTest`·`JulietCorpusTest`에서 `RunResult` 가 못 주니 **`CheckRunner.RunResult` 에 필드를 안 더하고** `TaintLink` 의 정적 카운터 둘을 시험이 읽고 0 으로 되돌린다 — `static final AtomicInteger`, 운영 코드 로그 없음).
  - 기대(게이트 아님): Juliet 서블릿 R 24 → 85 안팎(75 변형·`_base` 파일 제외) · crossFile ≥ 80 · Benchmark R 82 → 90 이상(`getParameterNames` 109 + helpers 약 50) · Benchmark P 70 → 65~72(오탐은 `getParameterNames` 파일의 상수 조건 꼴이 같이 는다 — 그것만이어야 한다) · 실물 넷: 메서드 사이 지적이 **새로 선 건수**를 규칙별로.
  - 기대 밖이면 **고치지 않고** 이력에 「기대 밖 — 원인 추정」. 사다리(둘까지): 원인이 §3-5 해석 순서의 한 칸(같은 파일·import·패키지)이거나 §3-6 대입 꼴 하나면 거기만 고치고 다시. 시험 하나가 8분을 넘으면 `SEEDS_MAX` 32 → 16 으로 내리고 다시(이력에 시간 둘 다).
- 검증: 네 시험 초록(갱신 뒤 다시 돌려 diff 0) · 이력에 수치.
- 의존: V-39a.

### R-8 — 손 판정(eGov trustBoundary · roller·jeecg xssServlet)
- 대상: `design/compare-2026-10-28.md`(⑨ 「손 판정」 절 — V-39 가 ⑨ 를 열면 그 안에; R-8 이 먼저면 절 머리만 만들고 V-39 가 채운다) · `PROGRESS.md`
- 변경: §3-12. `run.bat` 없이 `toolbox.bat check C:/workspace/toolbox-corpus/egov --groups security --profile demo --xlsx`(roller·jeecg 도) → 결과에서 세 쌍의 파일·줄 → 상한 20 씩 → 파일을 열어 판정. 표 `| 표본 | 파일 | 줄 | 규칙 | 판정 | 근거 한 구절 |` + 요약 줄(참 n · 오탐 n · 모름 n, 오탐 꼴 묶음). 코드 발췌는 안 옮긴다.
- 검증: `bash scripts/doc-lint.sh design/compare-2026-10-28.md` 출력 없음 · 표 행 수 ≤ 60.
- 의존: V-39b.

### V-39 — compare ⑨ · PLAN 4장 한 줄 · `--corpus`·`--db`
- 대상: `design/compare-2026-10-28.md`(⑨) · `PLAN.md` 4장 표본 문단(benchmark 에 helpers 가 든다는 한 구절) · `PROGRESS.md`
- 변경: §3-13. 후보 한 줄들(「후보로: …」). `bash scripts/doc-lint.sh design/compare-2026-10-28.md PLAN.md`.
- 검증: `bash scripts/verify.sh --corpus` 초록(corpus 도장 full) → 따로 `bash scripts/verify.sh --db` 초록(MANIFEST — db 도장) → `bash scripts/verify.sh` 초록. **무거운 둘은 겹쳐 띄우지 않는다**(메모리).
- 의존: R-8.

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7 · 행의 축·강제 지점과 diff · 새 픽스처 양성·음성 · 골든 갱신이 의도된 변화인가 · **`TaintLink` 가 `analyze` 를 import 하지 않나 · 모호 해석이 「건너뜀」 인가**) → `gate-probe.sh` → `verify.sh`(도장은 V-39 에서 산다) → `git push -u origin work/2026-10-30` → `gh pr create --base main`(본문: 행마다 시작 세 줄 표 + 「검증」 + ⑨ 핵심 수치 여섯 줄 — Benchmark P/R·Juliet 서블릿 R·crossFile R·실물 새 지적 수·손 판정 요약·벽시계) → CI 폴링 → claude[bot] 처분 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → 끝 보고. PR 본문 끝 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## 5. 금지 사항
- `pure/`·portfolio 수정 금지 · `rules.yaml` 수정 금지(새 규칙 id 없음 — `pure-rules.json`·`fixtures.json` security 줄·`CheckRulesTest` 규칙 수 111 불변)
- `core/check` 가 `core/analyze` 를 import 금지(`ArchitectureTest.noPackageCycles`) · `JavaGraph`·`JpaIndex` 수정 금지 · 심볼 해석기·새 Maven 의존성 금지
- `Taint.SOURCES` 에 `getParameterNames` 외 추가 금지 · `SANITIZER`·`SCOPE_CLEAN`·`ARG_CLEAN`·`MUTATORS`·`SINKS` 변경 금지(수치를 흔드는 다른 축)
- 요약으로 오염을 **씻는** 코드 금지(§3-2) · 모호 해석을 「전부」 로 바꾸기 금지(§3-5)
- `CheckRunner`·`RuleSet.Run`·`CorpusFiles`·`GoldenFiles`·`verify.sh`·훅·CI 수정 금지 — 예외는 `SecurityBenchmarkCorpusTest`·`JulietCorpusTest`(채점 하네스) · 시험을 고쳐 통과시키기 금지 — 골든 갱신은 diff 를 읽고 이력에 적은 뒤에만
- 발췌·로그·골든·compare 에 출처 값·변수 값·코드 본문을 더 싣지 않는다(규칙 3) — `TaintLink` 는 로그를 안 쓴다
- `corpus/MANIFEST` 는 `corpus-fetch.sh` 만 고친다 · Juliet 을 MANIFEST 에 넣기·`assumeTrue` 제거 금지
- 화면(`tools/`)·`docs/` 사용자 문서 변경 금지 · 스타일 리팩터·이름 바꾸기 금지(`JavaRules` 는 배선 자리 넷만)
- 상수 조건 가지치기·정화 범주화·properties 읽기 구현 금지(사용자 2026-10-30 — 후보로)

## 6. 최종 검증
- `bash scripts/verify.sh` 초록 + `--corpus` 초록 + `--db` 초록(MANIFEST 지문 — 둘은 따로, 겹치지 않게)
- `bash scripts/mvn.sh -q pmd:check` 0 · `gate-probe.sh` 10/10
- 골든: `check/fixtures.json`·`check/java.json`·`check/pure-rules.json` diff 0 · `self-check.txt` diff 0(바뀌면 §7) · corpus 골든 전·후가 이력·compare ⑨ 와 일치 · `corpus/MANIFEST` benchmark 한 줄만
- 시험: `TaintTest` 48 이상 · `TaintLinkTest` 20 · 네 corpus 시험 각 10분 안
- 수동: `run.bat` 뒤 코드 검사 붙여넣기 탭에 `class C { void a(javax.servlet.http.HttpServletRequest r) { b(r.getParameter("f")); } void b(String p) { new java.io.FileInputStream(p); } }` → `security.pathTraversal` 한 건이 **`b` 의 줄**에, 같은 글에서 `b("x")` 로 바꾸면 0
- compare ⑨ 에 표 다섯(Benchmark·Juliet·실물·벽시계·손 판정) + 「드러난 것」 + 「후보로」 줄

## 7. 중단 조건
다음이면 임의로 해결하지 말고 멈추고 보고(어느 스텝 · 무엇이 예상과 달랐나 · 선택지):
- 5-54b·c 에서 `check/fixtures.json`(security 줄)·`pure-rules.json`·`check/java.json` 이 바뀐다 — 어느 줄·어느 규칙인지 적고 멈춘다
- `self-check.txt` 가 바뀐다 — 우리 코드에 메서드 사이 보안 지적이 섰다. **고치지 말고** 파일·줄·규칙을 보고(진짜면 사람이 판단할 보안 사항, 오탐이면 해석 결함)
- `ArchitectureTest` 빨강(패키지 고리) · `SelfCheckTest` 메서드 수·복잡도가 `Taint`·`TaintLink` 나누기로도 안 들어간다
- 플랜의 구조 설명(§2 의 줄 번호·메서드 이름)이 실제 코드와 다르다
- V-39a 에서 `corpus-fetch.sh benchmark` 가 helpers 를 0 개 집거나 `corpus-check.sh` 가 빨강
- V-39b 재채점에서 등급 A(parseError·예외)가 새로 생긴다(`TaintLink` 가 던진 예외 — 스택) · Benchmark 정밀도가 60 아래로 내려간다(오탐 꼴이 상수 조건이 아니다) · 시험 하나가 사다리 뒤에도 10분을 넘는다
- 검증이 2회 연속 실패하고 원인이 플랜 범위 밖 · 스텝에 없는 파일을 3개 이상 고쳐야 한다

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① 재채점 수치와 벽시계(V-39b 「기대」 는 추정) ② helpers 의 파일 수·하위 폴더(`git ls-files` 가 받아야 안다)와 helpers 자체의 parseError 유무(A 게이트) ③ `Taint` 메서드 수가 30 을 넘는지(5-54a 검증이 말한다 — 넘으면 `Body`·`taintedField` 를 `TaintLink` 로) ④ `ambiguous` 가 실물(egov-msa 모듈 중복 이름)에서 얼마나 큰지 — 수치만 적는다. 첫 스텝 전 확인할 명령: `bash scripts/verify.sh`(현재 트리 초록, 도장 있음) · `ls C:/workspace/toolbox-corpus | grep -c .`(표본 폴더 있음 — benchmark·juliet 포함) · `node -v` · `docker info`(V-39 의 `--db`).
- **사용자만 정할 수 있는 것** — 없음(축·머지 위임·helpers·상수 조건·손 판정은 2026-10-30 에 받았다).
