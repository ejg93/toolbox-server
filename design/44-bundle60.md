# 설계 44 — 번들 60: 철저화 R3-3 — 보안 정밀도(정화 범주 · 응답 writer · 스코프 타입 · properties 값) (Fable 설계 · Opus 실행, 2026-10-30)

## 맥락
번들 59(설계 43)가 메서드 사이 오염을 넣어 재현율을 올렸다(Benchmark R 95 · Juliet 파일 사이 75). 손 판정(compare ⑨ R-8)이 보인 것은 **정밀도** — roller `xssServlet` 20 중 참 0, jeecg 20 중 참 2, eGov `trustBoundary` 19 중 참 13. 오탐 꼴은 일곱 묶음으로 이미 정리돼 있다(compare ⑨ 「오탐 꼴」·「후보로」). 그 가운데 엔진이 고칠 수 있는 넷을 친다.
- **정화가 범주를 모른다** — `SANITIZER` 하나(`Taint.java:75`)가 모든 싱크를 씻는다. 그래서 ⓐ `escapeHTML`(대문자)·`escapeJson` 처럼 목록 밖 이름은 XSS 오탐(roller 5) ⓑ `URLEncoder.encode` 가 목록 밖이라 Juliet CWE113 B2G(헤더·쿠키) 가 FP 648 ⓒ 반대로 `escapeHtml` 이 trustBoundary 까지 씻어 Benchmark trustbound FN 6.
- **응답 writer 를 이름으로만 본다** — `WRITER_NAME`(`:124`) `out|writer|pw|printWriter` 가 파일 스트림·나가는 HTTP 연결에도 맞는다(jeecg 5). JSON 응답(`setContentType("application/json")`)의 `pw.write(json)` 도 XSS 로 센다(jeecg 6).
- **싱크 스코프가 이름 정규식뿐** — `idc.search(…)`(변수는 `InitialDirContext`)가 `(?i)^(ctx|.*context|.*ldap.*)$` 밖이라 Benchmark ldapi FN 13.
- **설정 값을 못 본다** — `getProperty("hashAlg1", "SHA512")` 는 기본값으로 판정한다(설계 42 §3-6). `benchmark.properties` 는 `hashAlg1=MD5`·`cryptoAlg2=AES/CCM/NoPadding` 이라 hash FN 40·crypto FP 27. 실물도 설정으로 알고리즘을 고른다(egov `Globals`·jeesite `Digests.getInstance(algorithm)` 꼴).
- 메서드 안에서 `this.k` 와 bare `k` 를 다른 이름으로 본다(번들 59 마무리 리뷰 🟡③, 메서드 사이는 `aliases` 가 메운다).

**사용자 결정(2026-10-30, 뒤집지 않는다)**
- R3-3 축은 **보안 정밀도**(xssServlet 실물 정밀도가 낮다는 R-8 결과) — SQL 정확도·Java 결함·구조 점검은 후보 표에 남는다
- **머지 위임 있음**
- **properties 파일 읽기를 넣는다** — `benchmark.properties` 한 파일을 표본에 더한다(`corpus-fetch.sh`·`MANIFEST` → db 레인 `--db` 약 17분)
- **상수 조건 가지치기는 그대로 미룬다**(2026-10-30 앞 결정) · 설계 41·42·43 의 결정 그대로(에이전트 최소, 검증 인프라 최적화 금지, 번들 하나·PR 하나)
- 「인자가 오염이면 호출 결과도 오염」(설계 43 §3-2)은 안 바꾼다 — eGov 오탐 4(로그인 사용자·난수·OAuth 토큰)는 이번에도 둔다

**라운드** — R3-1(58) → R3-2(59) → **R3-3(이 번들)** → R3-4…(남은 후보). 재측정은 V 행.

실행자는 Opus. 차례 `5-55a` → `5-55b` → `5-55c` → `V-40a` → `V-40b` → `R-9` → `V-40` → 마무리. 가지 `work/2026-10-30b`. 번들 하나·PR 하나(닫기 커밋 `7d8991b` 가 같이 실린다).

---

## 1. 목표와 범위
- 한 문장: 오염 추적의 정밀도를 올린다 — 정화 함수를 싱크 범주별로 나누고(`URLEncoder.encode`·`escapeHTML`·`escapeJson` 추가), 응답 writer 를 「응답에서 온 것」만으로 좁히며(JSON 응답 메서드는 XSS 제외), 싱크 스코프를 선언 타입으로도 맞추고, `getProperty(키, 기본값)` 의 값을 검사 폴더의 `*.properties` 에서 찾아 판정한다.
- 완료 시 체감: roller·jeecg 의 `xssServlet` 오탐(파일 스트림 `out`·JSON 응답·`escapeJson`)이 사라지고 eGov·Benchmark 의 trustBoundary 가 `escapeHtml` 뒤에도 잡힌다. `Cipher.getInstance(props.getProperty("alg", "AES/GCM/NoPadding"))` 은 `app.properties` 에 `alg=DES/ECB/PKCS5Padding` 이 있으면 지적된다. Juliet CWE113 정밀도 50 → 100 안팎, Benchmark hash R 68 → 100·crypto P 82 → 100, ldapi R 51 → 100 안팎. compare ⑩ 에 전·후 표와 **같은 59 줄의 재판정**.
- In scope: `core/check/Taint.java`(정화 범주·writer·스코프 타입·alias) · 신설 `core/check/PropertiesIndex.java` · `TaintConst.java`(getProperty 지연) · `TaintLink.java`(보류 판정 `finish`) · `RuleSet.java`(`Run.apply`·`observe`·`observed` 에 properties 세 줄) · `JavaRules.java`(생성자에 색인) · 시험(`TaintTest`·`TaintLinkTest`·신설 `PropertiesIndexTest`) · 픽스처 `fixtures/check/java/link/`(properties 하나 + Pos·Neg 줄) · `scripts/corpus-fetch.sh`·`corpus/SOURCES.md`·`corpus/MANIFEST`(benchmark.properties) · 골든(corpus) · `design/compare-2026-10-28.md` ⑩ · PLAN 4장 한 구절 · 분할표·번들 표 · PROGRESS.
- Out of scope: 상수 조건 가지치기 · 「인자 오염 → 결과 오염」 완화 · 정수·날짜 반환 휴리스틱(roller 5 — 뿌리가 파일 사이 사슬이라 R-9 재판정으로만 본다) · 캐시·렌더 결과 바이트(roller 4) · `yml` 설정 · 심볼 해석기 · `rules.yaml`·순수본 변경(새 규칙 id 없음) · `CheckRunner` 변경 · 화면.

## 2. 현재 구조 요약(탐색 2026-10-30 실측, 번들 59 머지 `3df8bb9` 기준)

**정화** — `Taint.SANITIZER`(`Taint.java:75`) 한 집합: `parseInt…parseByte` · ESAPI `encodeForHTML…encodeForOS` · Spring `htmlEscape`·`htmlEscapeHex` · commons `escapeHtml`·`escapeHtml4`·`escapeXml*`·`escapeJava`·`escapeEcmaScript`·`escapeSql` · `cleanXSS` · OWASP encoder `forHtml…forUriComponent` · `getCanonicalPath`·`normalize`. `taintedCall`(`:431`) — 이름이 여기 있으면 **거짓**(스코프·인자 안 봄). 이름만 맞춘다 — `URLEncoder.encode` 의 `encode` 는 없다(Base64.encode 와 겹쳐 넣을 수 없었다), `escapeHTML`(roller `Utilities.escapeHTML`)·`escapeJson`(commons-text) 도 없다.

**writer 싱크** — `SINKS.put("security.xssServlet", sink(-1, WRITER, print…append) + sink(1, null, sendError))`. `scopeOk`(`:313`) `ANY_WRITER` 가지: 스코프가 `writerCall`(`getWriter()`·`getOutputStream()` 호출) · `writers` 집합의 이름 · `WRITER_NAME` 에 맞는 이름. `writers` 는 `flow`(`:290`)가 `VariableDeclarator`·`AssignExpr` 값이 `writerCall` 일 때 채운다. **`new X(…)` 로 초기화된 지역은 못 가른다** — `PrintWriter out = new PrintWriter(conn.getOutputStream())`(jeecg `HttpRequest.java:115`) · `OutputStreamWriter out = new OutputStreamWriter(fout,"gb2312")` · `OutputStream out = new FileOutputStream(savePath)` 가 이름으로 writer. 반대로 `OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8")`(jeecg `JeecgListDemoController.java:574`, `out` 이 응답 스트림)는 **참**이었다 — 래퍼는 안쪽이 writer 면 writer. 응답 형식은 안 본다 — jeecg `TagUtil.java:483` `response.setContentType("application/json")` 뒤 `pw.write(jsonStr)`.

**스코프 조건** — `Sink(names, arg, Pattern scope)`(`:43`), `scopeOk` 가 `lastName(스코프)` 전체 일치. `Body`(`:51`)에 `locals`(이름)는 있고 **타입은 없다**. `TaintLink.localTypes`(`TaintLink.java:353`)가 매개변수·선언 타입(초기값 `new T` 우선)을 따로 뽑는다. Benchmark ldapi FN 13 은 `javax.naming.directory.DirContext ctx = ads.getDirContext(); InitialDirContext idc = new InitialDirContext(ctx.getEnvironment()); … idc.search(base, filter, filters, sc)`.

**필드 별칭** — `target`(`:269`) 이 `this.f` → 「this.f」, bare `f` → 「f」. `taintedField`(`:420`) `this.f` → `t.contains("this.f")`. 메서드 안에서 둘을 잇는 자리가 없다. `TaintLink.aliases` 가 메서드 사이 씨앗에서만 셋(`키`·`this.f`·`f`)을 같이 넣는다.

**상수 전파·getProperty** — `TaintConst.resolve`(`:183`) → `resolveCall`(`:244`): `DEFAULTED = {getProperty, getOrDefault}` 이고 인자 둘이면 **둘째 인자(기본값)** 를 값으로. `algorithm`(`:160`)이 `getInstance(상수)` 의 값마다 `<cls>.getInstance("값")` 글을 그 규칙 정규식에 묻는다(`KEY_MAKERS` 는 `/CBC/PKCS5Padding` 을 붙인다). `namedSecret`·`secretArg` 도 같은 `resolve` 를 쓴다 — 기본값 비밀번호는 하드코딩이 맞으니 그대로 둬야 한다. `Taint.scan`(`:131`)이 메서드마다 `k.constants(m, fields)` 로 `Hit` 를 모은다 — `TaintLink.summarize`(`:109`)가 그 `scan` 을 부르고 `JavaRules.taint`(`:220`)가 `Hit` 를 `Finding` 으로. 검사 끝 `JavaRules.finish`(`:202`) → `link.finish()`(`:406`).

**properties 가 흐르는 자리** — `Source.lang` 은 확장자(`properties`). `RuleSet.Run.apply`(`RuleSet.java:259`)는 모든 글롭의 `Source` 를 받는다(`*.properties` 는 `common.*`·`security.cfg*`(kind file, `:513-540`) 글롭에 있다). `Run.observe`(`:291`)는 `.java` 또는 중복 글롭만 받고 `RuleSet.observed`(`:217`)도 `.java`·중복 글롭만 참 — 변경분 검사에서 안 바뀐 `.properties` 는 읽히지 않는다. `JavaRules` 는 `RuleSet.Run` 생성자(`:247`)에서 `new JavaRules(javaDefs(), naming)`.

**표본** — `corpus-fetch.sh:49` benchmark pathspec `testcode/*.java helpers/*.java expectedresults-1.2.csv`. `benchmark.properties` 는 저장소 `src/main/resources/benchmark.properties`(캐시 `C:/workspace/.corpus-src/benchmark@f40c618c…` 에서 확인: `cryptoAlg1=DES/ECB/PKCS5Padding` · `cryptoAlg2=AES/CCM/NoPadding` · `hashAlg1=MD5` · `hashAlg2=SHA-256`). 시험은 `benchmarkprops.getProperty("cryptoAlg1", "DESede/ECB/PKCS5Padding")` 33 · `("cryptoAlg2", "AES/ECB/PKCS5Padding")` 27 · `("hashAlg1", "SHA512")` 40 · `("hashAlg2", "SHA5")` 33. Juliet CWE113 B2G: `data = URLEncoder.encode(data, "UTF-8"); response.setHeader("Location", "/author.jsp?lang=" + data)`. Benchmark trustbound FN 6: `escapeHtml`·`encodeForHTML`·`htmlEscape` 뒤 `putValue`. Benchmark xss 음성은 `encodeForHTML` 1,781 · `htmlEscape` 71 · `escapeHtml` 59 — XSS 범주로 남으니 안 흔들린다. `encodeForHTML` 뒤 SQL·경로·명령 싱크에 넣는 참 취약(sqli 6·pathtraver 9·cmdi 2·ldapi 3)은 지금도 잡힌다(인코딩한 값이 싱크 인자가 아니다) — 범주화로 **늘지 않는다**(기대 밖이면 이력에).

**손 판정 R-8**(`design/compare-2026-10-28.md:541-591`) — 59 줄의 표 + 요약 + 오탐 꼴 일곱. R-9 는 이 표를 재판정한다.

**함정** — ① `TaintTest.hybridThroughRuleSet`(`TaintTest.java:221`)은 `run.apply` 만 부르고 `finish` 를 안 부른다 — `PosTaint.cipher`(`props.getProperty("alg", "DES/ECB/PKCS5Padding")`, 42줄)가 지연 판정이 되면 거기서 사라진다 → 시험에 `run.finish()` 를 더한다(기대 아홉 그대로) ② `TaintTest.algorithmThroughVariableAndDefault`(`:200`) 등 `hitsIn`(Taint 직접) 사례는 색인이 없을 때 **즉시** 기본값으로 판정해야 그대로 산다 → `Taint.scan(cu)`(링크 없음)은 보류를 빈 색인으로 바로 푼다 ③ `metric.complexity` ≤ 15 · `classMethods` ≤ 30(`Taint` 약 27 · `TaintConst` 약 20 · `TaintLink` 약 25) — 새 몫은 새 클래스로(`Sanitizers`·`PropertiesIndex`) ④ PMD 는 손으로 `bash scripts/mvn.sh -q pmd:check`(번들 59 에서 두 번 놓쳤다 — 행마다) ⑤ `corpus/MANIFEST` 는 db 레인 — push 앞 `--db` ⑥ `fixtures/check/security/pos.java`·`neg.java` 는 순수본 동기 — 23·24·28 줄(`getInstance("MD5")` 등 리터럴)은 즉시 판정이라 불변이어야 한다(§7).

## 3. 설계 결정 — 전부 [고정]

1. **정화 범주(family)** — 새 클래스 `core/check/Sanitizers.java`(package-private): `record San(String scope /* null = 어느 스코프든 */, String name, String family)` 표와 `static Optional<String> family(MethodCallExpr c)`. 범주: `NUM`(`parseInt…parseByte` — **모든 싱크**를 씻는다) · `XSS`(`encodeForHTML`·`encodeForHTMLAttribute`·`encodeForJavaScript`·`encodeForXML`·`htmlEscape`·`htmlEscapeHex`·`escapeHtml`·`escapeHtml4`·`escapeHTML`·`escapeXml`·`escapeXml10`·`escapeXml11`·`escapeJava`·`escapeEcmaScript`·`escapeJson`·`cleanXSS`·`forHtml`·`forHtmlAttribute`·`forJavaScript`·`forXml`·`forXmlAttribute`) · `SQL`(`encodeForSQL`·`escapeSql`) · `LDAP`(`encodeForLDAP`·`encodeForDN`) · `XPATH`(`encodeForXPath`) · `OS`(`encodeForOS`) · `URL`(`encodeForURL`·`forUri`·`forUriComponent`·스코프 `URLEncoder` 의 `encode` — 스코프 없는 `encode` 는 아니다) · `PATH`(`getCanonicalPath`·`normalize`). 싱크 → 받는 범주: `pathTraversal` {PATH} · `osCommand` {OS} · `sqlInjection` {SQL} · `xssServlet` {XSS} · `ldapInjection` {LDAP} · `xpathInjection` {XPATH} · `responseSplitting` {URL} · `openRedirect` ∅ · `ssrf` ∅ · `trustBoundary` ∅(+ 모두 NUM). `Taint.SANITIZER` 집합은 **지운다**(`Sanitizers` 가 대신). 버린 대안 — 전역 한 집합 유지 + 이름만 추가(trustBoundary FN·CWE113 FP 둘 중 하나는 못 고친다).
2. **정화 값의 전파** — 정화 호출은 **오염이지만 범주를 단다**: `taintedCall` 에서 `Sanitizers.family(c)` 가 있으면 참(오염)으로 두고, 범주는 별도 맵 `fam: Map<String, Set<String>>`(이름 → 씻긴 범주, `NUM` 은 `"*"`)에 적는다. 식의 범주 `families(e, t, fam)`: 정화 호출 → {범주} · `NameExpr` → `fam.get(이름)`(없으면 ∅) · `BinaryExpr +` → 오염인 쪽들의 **교집합**(오염 아닌 쪽은 전부) · `Enclosed`·`Cast` → 안 · 그 밖(다른 호출·생성·배열) → ∅. 대입 `flow(name, value)`: 값이 오염이면 `fam[name] = fam 에 처음이면 families(value), 있으면 교집합`(흐름 무관 — 한 번이라도 안 씻긴 대입이 있으면 ∅). 변경자(`sb.append(x)`)·for-each·배열 요소 대입은 범주를 **안 옮긴다**(∅). 싱크 판정 `argTainted` 뒤에 `!families(arg).contains(싱크 범주) && !families(arg).contains("*")` 일 때만 지적. 버린 대안 — 정화를 「깨끗」으로 두고 범주 밖 싱크만 다시 오염시키기(이름 집합으로는 불가능 — 값이 어디서 왔는지 잃는다).
3. **응답 writer** — `Body` 에 `Map<String, String> types`(매개변수·선언 타입, 초기값 `new T` 면 T — `TaintLink.localTypes` 를 `Taint.body` 로 **옮기고** `TaintLink` 는 `b.types()` 를 쓴다)와 `Set<String> fresh`(초기값이 `new …` 이고 그 인자에 writer 가 없는 지역 이름)를 더한다. `flow`: 값이 `ObjectCreationExpr` 이고 인자 중 하나가 `writerCall` 이거나 `writers` 의 이름이면 → `writers.add(name)`(래퍼 상속); 아니고 `new` 면 → `fresh.add(name)`. `scopeOk` 의 이름 휴리스틱 `WRITER_NAME` 은 `!fresh.contains(name)` 일 때만. 타입이 `JspWriter`·`ServletOutputStream` 인 매개변수·지역은 writer(`writers` 에 처음부터). **JSON 응답 메서드**: `Body.json`(boolean) = 람다 밖 `MethodCallExpr` 중 이름 `setContentType`·`setHeader`·`addHeader` 의 인자에 글자 상수가 있고 그 값(소문자)에 `json` 이 들어가거나, 인자가 `APPLICATION_JSON`·`APPLICATION_JSON_VALUE`·`APPLICATION_JSON_UTF8_VALUE` 이름이면 참 → 그 메서드의 `xssServlet` writer 싱크는 안 낸다(`sendError` 는 낸다). 버린 대안 — `@ResponseBody` 보기(writer 를 안 쓴다) · 응답 형식을 파일 단위로(한 서블릿이 HTML·JSON 을 둘 다 낸다).
4. **스코프 타입 조건** — `Sink` 에 `Set<String> types`(null = 없음)를 더한다. `scopeOk`: 이름 정규식 **또는** 스코프가 `NameExpr` 이고 `b.types().get(이름)` ∈ types. 표: `ldapInjection.search` {`DirContext`, `InitialDirContext`, `LdapContext`, `InitialLdapContext`, `LdapTemplate`} · `sqlInjection.execute|update|query` {`Statement`, `PreparedStatement`, `CallableStatement`, `Connection`, `JdbcTemplate`, `NamedParameterJdbcTemplate`, `SqlSession`, `SqlSessionTemplate`, `EntityManager`, `Session`, `Query`, `NativeQuery`} · `xpathInjection.compile|evaluate` {`XPath`} · `trustBoundary.setAttribute|putValue` {`HttpSession`} · writer {`PrintWriter`, `JspWriter`, `ServletOutputStream`, `Writer`} — writer 타입은 **`fresh` 가 아닐 때만**(`PrintWriter out = new PrintWriter(file)` 은 아니다). `scopeOk` 시그니처에 `Body` 를 더한다(복잡도 15 안에 — 타입 가지는 `scopeType` 도우미).
5. **메서드 안 필드 별칭** — `grow` 끝(한 바퀴마다): `t` 의 이름 `n` 이 `b.fields()` 에 있고 `b.locals()` 에 없으면 `this.n` 도 넣고, `this.n` 이 있으면 `n`(지역 아님)도 넣는다. `fam` 도 같이 복사. `TaintLink.aliases` 는 그대로.
6. **`PropertiesIndex`** — 새 클래스 `core/check/PropertiesIndex.java`(package-private): `void see(Source s)` 가 `.properties` 글을 줄마다 읽어(`#`·`!` 주석, 첫 `=`·`:` 로 키·값, 양끝 공백 제거, `\` 줄 이음은 안 받는다 — 값에 `\` 가 끝이면 그 줄만) `Map<String, Set<String>> values`(같은 키가 파일 여럿이면 합집합, 키마다 8 개 상한) 에 넣는다 · `Set<String> get(String key)`(없으면 빈 집합) · `int files()`. `RuleSet.Run`: 필드 `props = new PropertiesIndex()`; `apply`·`observe` 첫머리에 `if ("properties".equals(s.lang())) props.see(s);` · `RuleSet.observed(rel)` 에 `|| rel.endsWith(".properties")`(변경분 검사에서 안 바뀐 설정도 읽는다). `JavaRules(List<Rule.Def>, Profile.Naming, PropertiesIndex)`(셋째 인자, `Run` 이 넘긴다 — 시험용 둘짜리 생성자는 빈 색인) → `TaintLink` → `TaintConst`. 버린 대안 — `JavaRules` 가 properties 도 읽기(`RuleSet` 이 `.java` 만 넘긴다) · yml(다음).
7. **`getProperty` 지연 판정** — `TaintConst.resolveCall`: `DEFAULTED` 이고 인자 둘이고 **첫 인자가 글자 상수**면 `Optional.of(Set.of("\u0000prop\u0000" + 키 + "\u0000" + 기본값))`(보류 표식 값 — 둘째 인자가 상수가 아니면 표식의 기본값 자리는 빈 글). 첫 인자가 상수가 아니면 오늘처럼 둘째 인자. `concat` 은 표식이 든 값을 **버린다**(`Optional.empty()` — 이어붙인 설정 값은 못 푼다). `namedSecret`·`secretArg` 는 표식 값을 **없는 값**으로 친다(기본값 비밀번호 판정은 `resolve` 가 둘째 인자를 그대로 줄 때만 — 즉 키가 상수인 `getProperty("db.password", "abc")` 는 보류 없이 **기본값으로 즉시** 판정해야 한다 → `resolve(e, l, boolean props)` 에 플래그를 두고 `algorithm` 만 `true` 로 부른다). `algorithm`: 값이 표식이면 `Pending(rule, cls, mode, key, dflt, node)` 를 `TaintConst.pending`(리스트)에 넣고 지적은 안 낸다. **풀기** `TaintConst.resolvePending(Pending p, PropertiesIndex idx) → Optional<Hit>`: 값 집합 = `idx.get(key)` 가 비지 않으면 그것, 비면 `{dflt}`(dflt 빈 글이면 ∅) → 오늘의 합성·정규식. `Taint.scan(cu)`(링크 없음)은 메서드마다 보류를 **빈 색인으로 바로** 푼다(단위 시험 불변). `TaintLink.summarize` 는 보류를 모아 두고 `finish` 가 `idx` 로 풀어 `Finding`(같은 `seen` 중복 제거)으로 낸다. 버린 대안 — 파일 순서에 기대 즉시 판정(properties 가 뒤에 오면 틀린다) · 모든 상수 규칙 지연(비밀번호 기본값은 하드코딩이 맞다).
8. **표본** — `corpus-fetch.sh:49` pathspec 에 `src/main/resources/benchmark.properties` 추가, `SOURCES.md` 한 구절, `MANIFEST` benchmark 줄(2762 → 2763). `SecurityBenchmarkCorpusTest` 는 루트가 `src/main/java/org/owasp/benchmark` 라 **properties 가 밖**이다 → 루트를 `src/main`(java + resources)으로, testcode 파일 수 단언 그대로. Juliet 은 그대로.
9. **매핑·지표** 불변. 목표(게이트 아님): Juliet CWE113 P 50 → 100 안팎 · Benchmark hash R 68 → 100 · crypto P 82 → 100 · ldapi R 51 → 100 안팎 · trustbound R 92 → 100 안팎 · xss P 64 유지 이상 · Benchmark 전체 R 95 유지, P 69 → 73 안팎 · Juliet 전체 P 82 → 95 안팎. 실물: roller xssServlet 26 → 10 안팎 · jeecg 30 → 15 안팎 · eGov trustBoundary 19 → 19(참이 안 줄어야 한다).
10. **재판정 R-9** — V-40b 뒤 같은 세 쌍을 `toolbox.bat check … --groups security --profile demo --json` 으로 다시 뽑아 R-8 표(`compare-2026-10-28.md:541-581`)의 59 줄과 맞춘다: **사라짐**(R-8 판정이 오탐이면 「고침」, 참이면 「잃음」) · **남음** · **새로 생김**(≤ 20, 읽고 판정). 표 `| 표본 | 파일 | 줄 | R-8 판정 | 후 | 비고 |` + 요약(고침 n · 잃음 n · 남은 오탐 n · 새 참 n · 새 오탐 n). 코드 본문은 안 옮긴다(규칙 3).
11. **compare ⑩** — `design/compare-2026-10-28.md` 끝에 `## ⑩ 재측정 — R3-3(번들 60, 2026-10-30)`: 올라간 후보 넷(정화 범주화·xssServlet JSON/비응답 out·LDAP idc·properties) · Benchmark 11 · Juliet 15(+서블릿·변형 묶음) · 실물 바뀐 규칙 · 벽시계·`_link` · 재판정표 · 「드러난 것」 · 「후보로」. ①~⑨ 는 안 고친다.
12. 행 접두 — 엔진 `5-55a·b·c`, 표본·재측정 `V-40a·b`·`V-40`, 재판정 `R-9`. 설계 `design/44-bundle60.md`. 가지 `work/2026-10-30b`.

## 4. 실행 스텝

### S0 — 설계 문서·행·번들 표 (설계 세션이 했다)
- 대상: 신설 `design/44-bundle60.md`(「맥락」~8절, 머리 인용문 삭제) · `PLAN.md`(번들 표 `:544` 「번들 59」 아래 「번들 60」 · 분할표 `:1044` 「V-39」 아래 `5-55a`·`5-55b`·`5-55c`·`V-40a`·`V-40b`·`R-9`·`V-40` 일곱 행, 압축 꼴 「설계 44 §… 가 원본 + 요지 + 강제 지점」) · `PROGRESS.md`(「열려 있는 것」 머리 → 「**번들 60(설계 44, 철저화 R3-3 — 보안 정밀도) 설계 끝 — 가지 `work/2026-10-30b`**, 다음은 「번들 해」」, 이력 S0 줄 — 직전 줄(번들 59 머지) 해시 칸을 `7d8991b` 로 채운다).
- 절차: 가지 `work/2026-10-30b` 그대로 → 세 파일 → `bash scripts/doc-lint.sh design/44-bundle60.md PLAN.md PROGRESS.md` → `bash scripts/verify.sh` → `git commit -m "docs: S0 설계 44 — 번들 60 행·번들 표(철저화 R3-3 보안 정밀도)"`.

### 5-55a — 정화 범주 `Sanitizers` + 전파 + 단위 시험
- 대상: 신설 `src/main/java/kr/ejg/toolbox/core/check/Sanitizers.java` · `Taint.java`(`SANITIZER` 삭제, `taintedCall`·`flow`·`grow`·`call`·`argTainted`, `fam` 맵) · `TaintTest.java`
- 변경:
  - `Sanitizers`: §3-1 표(`static final List<San>`), `family(MethodCallExpr)`, `static final Map<String, Set<String>> ACCEPTS`(규칙 id → 범주), `static boolean cleans(Set<String> families, String rule)`(`"*"` 또는 `ACCEPTS.get(rule)` 교집합).
  - `Taint.run`: `Map<String, Set<String>> fam = new HashMap<>()` 를 `grow`·`call` 에 실어 나른다 — 정적 판정 시그니처를 안 바꾸려고 `fam` 도 **표식 원소**로 `t` 에 넣는다: `name + "\u0001" + family`(`SRC_ON` 과 같은 수법, `"\u0001*"` 는 NUM). `families(e, t)`(§3-2)는 `t` 에서 `name\u0001…` 을 모은다. `flow` 가 교집합 규칙으로 표식을 넣고 뺀다(한 번 뺀 범주는 다시 안 들어간다 — 집합이 줄 수 있으니 고정점 판정 `before` 는 **오염 이름 수**로만 센다, 표식 제외).
  - `taintedCall`: `SANITIZER.contains(n) → false` 가지 삭제. `call`/`argTainted`: 인자가 오염이고 `!Sanitizers.cleans(families(arg, t), rule)` 일 때만 `Hit`.
  - `TaintTest` 추가(단언 필수): ㉛ `String s = ESAPI.encoder().encodeForHTML(p); out.print(s)` → 0, `stmt.executeQuery(s)` → sqlInjection 1 ㉜ `String u = URLEncoder.encode(p, "UTF-8"); response.setHeader("L", u)` → 0, `response.sendRedirect(u)` → openRedirect 1 ㉝ `String h = StringEscapeUtils.escapeHtml(p); session.setAttribute("a", h)` → trustBoundary 1 ㉞ `escapeHTML`·`escapeJson` → xssServlet 0 ㉟ `int n = Integer.parseInt(p); new File("f" + n)` → 0(NUM, 기존 사례 유지) ㊱ 교집합 — `x = encodeForHTML(p); x = p; out.print(x)` → 1 ㊲ `+` — `out.print("<b>" + escapeHtml(p) + "</b>")` → 0, `out.print(escapeHtml(p) + p)` → 1 ㊳ 스코프 없는 `encode(p)` 는 정화 아님 → 1 ㊴ 기존 `sanitizerCleans` 등 40+ 사례 그대로 초록.
- 검증: `bash scripts/mvn.sh -q test -Dtest=TaintTest,TaintLinkTest` · `pmd:check` 0 · `bash scripts/verify.sh`(`SelfCheckTest` — `Taint` 메서드 수).
- 의존: 없음.

### 5-55b — 응답 writer · JSON 응답 · 스코프 타입 · 메서드 안 별칭
- 대상: `Taint.java`(`Body`·`body`·`flow`·`scopeOk`·`SINKS`·`Sink`·`grow`) · `TaintLink.java`(`localTypes` 삭제 → `b.types()`) · `TaintTest.java`
- 변경: §3-3·4·5. `Body` 에 `types`·`fresh`·`json`. `Sink(names, arg, Pattern scope, Set<String> types)` + `sink(int, String, Set<String>, String...)` 겹침. `scopeOk(Sink, Optional<Expression>, Set<String> writers, Body b)`.
  - `TaintTest`: ㊵ `PrintWriter out = new PrintWriter(conn.getOutputStream()); out.print(p)` → 0 ㊶ `OutputStream out = new FileOutputStream(f); out.write(p.getBytes())` → 0 ㊷ `OutputStreamWriter w = new OutputStreamWriter(response.getOutputStream()); w.append(p)` → 1(래퍼) ㊸ `void f(JspWriter out, String p){ out.print(p) }`(p 바인딩) → 1 ㊹ JSON — `response.setContentType("application/json;charset=UTF-8"); response.getWriter().write(p)` → 0, 같은 몸의 `response.sendError(500, p)` → 1 ㊺ `MediaType.APPLICATION_JSON_VALUE` 이름 → 0 ㊻ `InitialDirContext idc = new InitialDirContext(env); idc.search(b, p, sc)` → ldapInjection 1 ㊼ `Statement s = c.createStatement(); s.execute(p)` → sqlInjection 1 ㊽ `this.k = request.getParameter("a"); new File(k)` → 1, `k = …; new File(this.k)` → 1(기존 `thisFieldWithinMethod` 유지).
- 검증: 위 시험 + `TaintLinkTest` 22 초록 · `pmd:check` 0 · `verify.sh`.
- 의존: 5-55a.

### 5-55c — `PropertiesIndex` + `getProperty` 지연 + 배선 + 시험·픽스처
- 대상: 신설 `PropertiesIndex.java` · `TaintConst.java` · `TaintLink.java`(보류 수집·`finish`) · `Taint.java`(`scan` 즉시 풀기) · `RuleSet.java`(`Run.apply`·`observe`·`observed`·`JavaRules` 생성) · `JavaRules.java`(생성자) · 신설 `PropertiesIndexTest.java` · `TaintLinkTest.java` · `TaintTest.java:221`(`finish`) · 픽스처 `fixtures/check/java/link/app.properties` + `PosLinkServiceImpl.java` 한 메서드(`Cipher.getInstance(props.getProperty("crypto.alg", "AES/GCM/NoPadding"))`, `app.properties` 에 `crypto.alg=DES/ECB/PKCS5Padding`) + `NegLinkServiceImpl.java` 한 메서드(`getProperty("hash.alg", "MD5")`, `app.properties` 에 `hash.alg=SHA-256` → 0)
- 변경: §3-6·7. `JavaRules(List<Rule.Def>, Profile.Naming)` 는 남기고(`this(defs, naming, new PropertiesIndex())`) 셋짜리를 더한다. `TaintLink(Taint, Map, PropertiesIndex)`. `TaintConst.pending()`·`resolvePending`. `Taint.scan(cu)` 끝에서 `k.pending()` 을 `TaintConst.resolvePending(p, PropertiesIndex.EMPTY)` 로 바로 푼다; `scan(cu, each)` 는 `each` 가 있으면 안 풀고 `TaintLink` 가 가져간다(`List<Pending> pending()` 접근자).
  - `PropertiesIndexTest`: 주석·`=`·`:`·공백·같은 키 두 파일 합집합·8 상한·`get` 빈 집합.
  - `TaintLinkTest`: ㉑ properties 가 **뒤에** 와도(`apply(java)` 뒤 `apply(properties)`) `finish` 가 잡는다 ㉒ 키 없음 → 기본값 ㉓ 변경분 — `observe(properties)` + `apply(java)` ㉔ `getProperty("db.password", "abc")` 는 즉시 hardcodedPassword(지연 안 함).
  - `TaintTest.hybridThroughRuleSet` 에 `got.addAll(run.finish() …)`(기대 아홉 불변 — `PosTaint.java:42` 가 `finish` 로 옮겨 올 뿐) · `linkThroughRuleSet` 기대에 `PosLinkServiceImpl.java:<줄> weakCipher` 추가.
- 검증: `TaintTest`·`TaintLinkTest`·`PropertiesIndexTest`·`CheckRulesTest`·`JavaRulesTest`·`PureRulesJsonTest`·`SelfCheckTest`·`ArchitectureTest` 초록 · `fixtures.json`(security 줄)·`java.json`·`pure-rules.json`·`self-check.txt` diff 0 · `pmd:check` 0 · `verify.sh`.
- 의존: 5-55b.

### V-40a — `benchmark.properties` 표본화 + Benchmark 시험 루트
- 대상: `scripts/corpus-fetch.sh:49` · `corpus/SOURCES.md:39` · `corpus/MANIFEST` · `SecurityBenchmarkCorpusTest.java`(`BASE` → `src/main`)
- 변경: §3-8. `bash scripts/corpus-fetch.sh benchmark`(네트워크 — 캐시가 있으면 복사만, MANIFEST 는 전체 지문이라 수 분) → `bash scripts/corpus-check.sh`.
- 검증: MANIFEST benchmark 한 줄 diff(2763) · `SecurityBenchmarkCorpusTest` 초록(골든은 V-40b 에서).
- 의존: 5-55c.

### V-40b — 재채점 + 시간
- 대상: `golden/corpus/security-*`·`-fp`·`-fn`·`check-*.json`·`-hits.txt`·`vcs-egov.json`
- 변경: `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='SecurityBenchmarkCorpusTest,JulietCorpusTest,CheckCorpusTest,LegacyCorpusTest,VcsCorpusTest' -Djunit.timeout=10m -Dgolden.update=true` → diff 를 읽고 전·후를 이력에(Benchmark 11 · Juliet 15 · 실물 · 벽시계 · `_link`). 기대는 §3-9. 기대 밖이면 **고치지 않고** 이력에(사다리 둘: §3-1 표의 이름 하나 · §3-4 타입 하나).
- 검증: 다섯 시험 초록(갱신 뒤 다시 돌려 diff 0).
- 의존: V-40a.

### R-9 — 재판정(같은 59 줄)
- 대상: `design/compare-2026-10-28.md`(⑩ 「재판정」 절) · `PROGRESS.md`
- 변경: §3-10. CLI 로 세 쌍을 다시 뽑아 R-8 표와 대조. 새로 생긴 것만 읽는다(≤ 20).
- 검증: `bash scripts/doc-lint.sh design/compare-2026-10-28.md` · 요약 합이 맞음.
- 의존: V-40b.

### V-40 — compare ⑩ · PLAN 4장 · `--corpus`·`--db`
- 대상: `design/compare-2026-10-28.md`(⑩) · `PLAN.md` 4장 표본 문단(benchmark 에 `benchmark.properties` 한 구절) · `PROGRESS.md`
- 변경: §3-11. 「후보로」 — 상수 조건 가지치기(미룸 그대로) · yml 설정 · 정수·날짜 반환 · 캐시 바이트 · 호출 결과 완화 · 직렬화.
- 검증: `bash scripts/verify.sh --corpus` 초록 → 따로 `--db` 초록 → `verify.sh` 초록. 겹쳐 띄우지 않는다.
- 의존: R-9.

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7 · 행의 축·강제 지점 · 픽스처 양성·음성 · 골든이 의도된 변화인가 · **정화 범주 교집합이 흐름 무관에서 안전한가(한 번 씻긴 이름이 다시 오염될 때) · `getProperty` 보류가 비밀번호 규칙을 안 건드리나 · `observed` 가 `.properties` 를 받나**) → `gate-probe.sh` → `verify.sh` → `git push -u origin work/2026-10-30b` → `gh pr create --base main`(행마다 시작 세 줄 표 + 검증 + ⑩ 핵심 수치 — Benchmark P/R·Juliet P·CWE113 P·실물 xssServlet 건수·재판정 요약·벽시계) → CI 폴링 → claude[bot] 처분 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → 끝 보고. PR 본문 끝 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## 5. 금지 사항
- `pure/`·portfolio·`rules.yaml` 수정 금지(새 규칙 id 없음 — `pure-rules.json`·`fixtures.json` security 줄·규칙 수 111 불변)
- `core/check` 가 `core/analyze` import 금지 · `CheckRunner`·`CorpusFiles`·`GoldenFiles`·`verify.sh`·훅·CI 수정 금지 — `RuleSet.Run` 은 §3-6 의 세 줄(apply·observe·observed)과 `JavaRules` 생성 인자만
- `SINKS`·`SOURCES`·`SCOPE_CLEAN`·`ARG_CLEAN`·`MUTATORS` 의 **이름 목록** 변경 금지(타입 조건을 더하는 것만) · 「인자 오염 → 결과 오염」 완화 금지 · 요약으로 씻기 금지
- 비밀번호·키 규칙(`hardcodedPassword`·`hardcodedKey`)의 `getProperty` 기본값 판정을 바꾸지 않는다(§3-7)
- 발췌·로그·골든·compare 에 코드 본문·설정 값 본문을 싣지 않는다(규칙 3 — properties 값은 판정에만 쓰고 어디에도 적지 않는다, 보류 표식 값이 `Finding` 발췌에 들어가지 않게)
- `corpus/MANIFEST` 는 `corpus-fetch.sh` 만 · Juliet `assumeTrue` 유지 · 새 Maven 의존성 금지 · 화면·`docs/` 변경 금지 · 상수 조건 가지치기 구현 금지

## 6. 최종 검증
- `bash scripts/verify.sh` · `--corpus` · `--db` 초록(따로) · `pmd:check` 0 · `gate-probe.sh` 10/10
- 골든: `fixtures.json`·`java.json`·`pure-rules.json`·`self-check.txt` diff 0 · corpus 골든 전·후가 이력·⑩ 과 일치 · MANIFEST benchmark 한 줄
- 시험: `TaintTest` 60 안팎 · `TaintLinkTest` 26 · `PropertiesIndexTest` · 다섯 corpus 시험 10분 안
- 수동: 붙여넣기 탭 — `response.setContentType("application/json"); response.getWriter().write(request.getParameter("q"));` → 0 · `response.getWriter().write(request.getParameter("q"));` → `security.xssServlet` 1 · `String h = StringEscapeUtils.escapeHtml(request.getParameter("u")); request.getSession().setAttribute("u", h);` → `security.trustBoundary` 1
- compare ⑩ 표 다섯 + 재판정표 + 「드러난 것」 + 「후보로」

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- `fixtures.json` security 줄·`pure-rules.json`·`java.json`·`self-check.txt` 가 바뀐다(특히 `security/pos.java` 23·24·28 줄 알고리즘 지적이 사라진다 — 즉시 판정이 깨진 것)
- `TaintTest` 기존 사례가 범주화로 깨지는데 §3-1 표로 설명이 안 된다
- `SelfCheckTest` 메서드 수·복잡도가 `Sanitizers`·`PropertiesIndex` 로 나눠도 안 들어간다 · `ArchitectureTest` 빨강
- V-40a 에서 `benchmark.properties` 가 안 받아진다 · `corpus-check.sh` 빨강
- V-40b 에서 등급 A 가 새로 생긴다 · Benchmark 재현율이 95 아래로 내려간다(범주화가 참을 잃었다) · eGov trustBoundary 참 13 중 잃은 것이 있다(R-9 「잃음」 > 0 이면 보고만 하고 머지는 사용자 판단)
- 시험 하나가 10분을 넘는다 · 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① 재채점 수치(§3-9 는 추정) ② R-9 에서 roller 「정수·날짜」 5 가 어떻게 되나(뿌리가 파일 사이 사슬 — 범주화·writer 로는 안 바뀔 수 있다) ③ `benchmarkprops.getProperty` 의 `benchmarkprops` 가 `new Properties()` 지역이라 스코프 조건 없는 `getProperty` 이름만으로 잡히는지(`DEFAULTED` 는 이름만 본다 — 잡힌다, 확인은 V-40b) ④ 변경분 검사(`VcsCorpusTest`) 벽시계. 첫 스텝 전: `bash scripts/verify.sh` 초록 · `ls C:/workspace/toolbox-corpus | grep -c .` · `docker info`.
- **사용자만 정할 수 있는 것** — 없음(축·머지 위임·properties·상수 조건은 2026-10-30 에 받았다).
