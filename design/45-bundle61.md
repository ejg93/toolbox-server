# 설계 45 — 번들 61: 철저화 R3-4 — 보안 마무리(상수 전파 사이 · 예외 출처 · 숫자 반환 · Juliet 판정 보정) (Fable 설계 · Opus 실행, 2026-10-30)

## 맥락
번들 60(설계 44)이 정화 범주·응답 writer·properties 값으로 정밀도를 올렸다(Benchmark R 100 · Juliet P 96). compare ⑩ 「드러난 것」·「후보로」 와 Juliet 표본 실측(2026-10-30 저녁)이 보인 남은 빈칸 — 전부 엔진이 닿는 꼴이다.
- **상수가 메서드·파일을 못 넘는다** — `TaintConst.constants`(`TaintConst.java:132`)는 한 메서드의 지역 + 같은 CU 필드 초기값(`fieldConstants`, `:111`)만 본다. 매개변수는 지운다(`:138`). `Other.X`(FieldAccessExpr)·메서드 호출 인자·반환은 `resolve` 가 빈 값(`:229-243`, `resolveWrapped` `:245`). `TaintLink` 는 상수 요약이 없다(`ClassInfo.fieldTypes` 는 타입만, `:74`). 그래서 Juliet CWE259 R 20(tp 36 / 174) · CWE321 R 31 — 변형 41(인자)·42(반환)·45(다른 메서드가 채운 필드)·51~54(파일 사이 인자 사슬)·61(파일 사이 반환)·68(다른 파일 static 필드)·71(Object 형변환)·81(추상 클래스)을 놓친다. `KerberosKey(principal, data.toCharArray(), …)` 는 `PASSWORD_CALLS`(`:44`)에 없어 kerberosKey 59 전부 미탐. 실물 꼴 — `Config.DB_PASSWORD`·`getPassword()` 헬퍼·`new SecretKeySpec(KEY.getBytes(), "AES")`.
- **예외가 출처가 아니다** — `security.exceptionMessage`(`rules.yaml:265-272`)는 정규식(`getWriter()…getMessage()` 꼴)이라 `e.printStackTrace(response.getWriter())`·`out.print(e)`·`sendError(500, msg)`(msg = e.getMessage() 지역)를 못 본다. Juliet CWE209 R 0(tp 0 / 34, 서블릿 17). `Taint` 씨앗은 요청 매개변수뿐(`SOURCES`·`BINDING`·`entry`, `Taint.java:65-69`·`:253`) — `CatchClause` 매개변수는 `Body` 어디에도 없다.
- **숫자·날짜 반환을 오염으로 본다** — `taintedCall`(`Taint.java:497-512`) 스코프 오염 → 결과 오염(`SCOPE_CLEAN` 밖), 인자 오염 → 결과 오염. roller `CalendarTag.java` 192(`formatTitle.format(day)`, `formatTitle` 은 지역 `SimpleDateFormat` 158줄)·284·292·309·317(`cal.get(Calendar.DAY_OF_MONTH)`, `cal` 은 지역 `Calendar` 122줄) 오탐 5(compare ⑨ 손 판정·⑩ 남음).
- **Juliet 서블릿 판정이 파일 이름 `_Servlet` 포함**(`JulietCorpusTest.java:136`)이다 — CWE80·81·83·601 은 **싱크**가 서블릿이라 File·PropertiesFile·URLConnection·connect_tcp·database·listen_tcp 출처 파일도 `CWE182_Servlet_…_Servlet` 이름을 가진다(CWE80 1080 중 요청 출처는 360). 「메서드 안 66 · 같은 파일 66 · 파일 사이 61」 은 출처 아홉 중 셋만 설계상 출처라서 나온 거짓 숫자다(compare ⑩ 「드러난 것」).
- `PropertiesIndex.see`(`PropertiesIndex.java:23-40`)는 `=`·`:` 만 구분자 — `key value`(공백)·`a\:b=c`(이스케이프)·줄 이음 `\` 을 못 읽는다(번들 60 마무리 리뷰 후보).

**사용자 결정(2026-10-30, 뒤집지 않는다)**
- R3-4 축은 **보안 마무리**(위 다섯) — SQL 정확도·Java 결함 묶음·jeesite CrudDao 는 후보 표에 남는다
- **머지 위임 있음**
- 상수 조건 가지치기는 그대로 미룬다 · 「인자 오염 → 결과 오염」 일반 완화는 안 한다(숫자·날짜 **반환 타입이 분명한 이름**만 예외) · 설계 41~44 의 결정 그대로(에이전트 최소, 검증 인프라 최적화 금지, 번들 하나·PR 하나)
- CWE327 P 50 은 **안 고친다** — Juliet good 이 `Cipher.getInstance("AES")`(모드 없음 = ECB)라 5-16 ECB 판정이 맞다. compare ⑪ 「드러난 것」 에 한 줄
- 표본 추가 없음 → `corpus/MANIFEST` 불변 → **`--db` 안 돈다**(db 도장 full 유지)

**라운드** — R3-1(58) → R3-2(59) → R3-3(60) → **R3-4(이 번들)**. 재측정은 V 행.

실행자는 Opus. 차례 `5-56a` → `5-56b` → `5-56c` → `V-41a` → `V-41b` → `R-10` → `V-41` → 마무리. 가지 `work/2026-10-30c`(번들 60 닫기 `c60cc6d`·5-55d `c0f8a89` 가 이미 있고 push 됨). 번들 하나·PR 하나.

---

## 1. 목표와 범위
- 한 문장: 하드코드 비밀번호·키 상수를 메서드·파일 사이로 좇고(요구 주도 — 비밀번호·키 싱크가 못 푼 인자만 `finish` 에서 호출자·피호출자·다른 클래스 필드로 푼다), 예외 객체를 출처로 하는 둘째 오염 실행으로 `security.exceptionMessage` 를 `.java` 에서 오염 추적으로 바꾸며(`.jsp` 는 정규식 그대로), 숫자·날짜를 돌려주는 호출은 깨끗으로 두고, Juliet 서블릿 판정을 출처 이름으로 고쳐 참값을 잰다.
- 완료 시 체감: `DriverManager.getConnection(url, user, Config.DB_PASSWORD)`(다른 파일 `static final`)·`conn(getPassword())`·`login(user, pw)` 로 넘긴 상수가 `security.hardcodedPassword` 로 잡힌다. `catch (Exception e) { response.getWriter().println(e.getMessage()); }`·`e.printStackTrace(response.getWriter())`·`response.sendError(500, e.toString())` 이 `security.exceptionMessage` 로 잡히고, `log.error("x", e.getMessage())`·`result.getMessage()` 출력은 안 잡힌다. roller 달력의 `cal.get(…)`·`format(day)` 출력이 xssServlet 에서 빠진다. Juliet CWE259 R 20 → 50 안팎 · CWE321 R 31 → 55 안팎 · CWE209 R 0 → 50(서블릿 17/17, 콘솔 `printStackTrace()` 17 은 `common.printStackTrace` 몫) · CWE80·81·83·601 변형 묶음 66 → 100 안팎(참값). compare ⑪.
- In scope: `core/check/TaintConst.java`(`KerberosKey` · `fieldConstants` 가 대입도 · 못 푼 인자의 `Need`) · 신설 `core/check/ConstLink.java`(상수 요약·고정점·`Need` 풀기) · `TaintLink.java`(요약에 상수 세 가지 수집 · `finish` 에서 `ConstLink`) · 신설 `core/check/Errors.java`(예외 씨앗·`printStackTrace(writer)`·EXC 싱크 표) · `Taint.java`(`scan` 둘째 실행 · `call` 의 규칙 가름 · `taintedCall` 숫자·날짜 예외 · `Body.types` 에 필드 타입) · `Writers.java`(안 바뀜) · `PropertiesIndex.java`(구분자·이스케이프·줄 이음) · `rules.yaml`(`security.exceptionMessage` 에 `params: {java: taint}` 한 줄) · 시험(`TaintTest`·`TaintLinkTest`·`PropertiesIndexTest`·신설 `ConstLinkTest`) · 픽스처 `fixtures/check/java/link/`(Pos·Neg 줄 + 신설 `PosLinkConfig.java`) · `JulietCorpusTest`(서블릿 판정 한 줄 + 주석 둘) · 골든(corpus) · `design/compare-2026-10-28.md` ⑪ · 분할표·번들 표 · PROGRESS.
- Out of scope: 상수 조건 가지치기 · 「인자 오염 → 결과 오염」 일반 완화 · 캐시·렌더 결과 바이트(roller 4) · FreeMarker 결과 HTML(jeecg 7) · 예외 오염의 **메서드 사이** 전파(`handle(e)` 헬퍼 — 다음) · 배열·컬렉션·직렬화 경유 상수(Juliet 66·72~75) · yml 설정 · `common.printStackTrace` 변경 · 순수본(`pure/`)·`docs/` 변경 · 새 규칙 id(없음 — 규칙 수 111·security 42 불변) · 표본 추가(`MANIFEST` 불변) · 화면.

## 2. 현재 구조 요약(탐색 2026-10-30 실측, main `364a440` + `c0f8a89` 기준)

**상수** — `TaintConst.constants(m, fields)`(`:132-165`): `l` = CU 필드 상수 사본 − 매개변수 이름, 선언·대입 고정점(`ROUNDS` 10, `merge` 는 **합집합** — `data = null` 이 안 지운다) → `namedSecret`(이름 = 상수)·`algorithm`·`secretArg`. `secretArg(name, args, l, at, rule, calls, out)`(`:189-201`): `calls.get(name)` 자리의 인자를 `resolve` → `plain` 이 비지 않고 규칙 정규식이 탐침(`password = "x"` · `SecretKeySpec("`)에 맞으면 `Hit`. `resolve`(`:229`): 글자 → 이름(`l.get`) → `+`(`concat`, 8 상한) → 호출(`resolveCall` `:294` — `DEFAULTED` 둘·`CONST_SCOPE` 의 스코프 재귀) → `resolveWrapped`(괄호·형변환·단항·배열 `<bytes>`) — **FieldAccessExpr 는 빈 값**. `PASSWORD_CALLS`(`:44`) `getConnection 2 · setPassword 0 · PasswordAuthentication 1 · login 1 · setCredentials 1 · UsernamePasswordCredentials 1 · connect 2` · `KEY_CALLS`(`:47`) `SecretKeySpec 0 · IvParameterSpec 0 · PBEKeySpec 0 · GCMParameterSpec 1`. `fieldConstants(cu)`(`:111`): `FieldDeclaration` 초기값만, 단순 이름 키, 선언 순 한 번. `Pending`(`:65`)·`weak`(`:84`) 는 알고리즘 보류(설계 44 §3-7) — 비밀번호·키는 즉시.

**메서드 사이** — `TaintLink`: `Ref(type|null, name, argc)`(`:51`) · `Site(ref, arg)`(`:55`) · `SinkAt(rule, line, excerpt)`(`:59`) · `Facts(sinks, calls, fieldWrites, returns)`(`:62`) · `Summary(file, cls, id, src, params, callVars, fields)`(`:69`) · `ClassInfo(id, name, pkg, file, superName, interfaces, abstractOrInterface, fieldTypes, bodies, imports)`(`:74`) · `Later`(`:79`) · `State(params, fields, returns, done, out)`(`:82`). `summarize(cu, s, emit)`(`:115-140`) → `info`(클래스 `파일!FQN`) → `taint.scan(cu, (b,f) -> method(...), ps)`. `method`(`:179-218`) 가 매개변수·`callInits`(`x = scope.m()` → `Ref` → 받는 지역, `:333`)·필드 읽기마다 `taint.run(b, seed, false)` 를 다시 돈다(`SEEDS_MAX` 32). `ref(m, types, c)`(`:351`)·`scopeType`(`:367`)이 호출 → `Ref`. `finish`(`:389-422`): `subtypes` → 고정점(`step`·`fire`·`targets`·`pick`·`up`, `ROUNDS` 50) → `Later` 를 `weak(p, props)` 로 → 상태 비움. **CU 를 안 남긴다**(요약은 글·줄·발췌만). `targets(ref, from)`(`:467`): `pick` 단일 → 몸 있으면 그 클래스, 없거나 추상이면 하위, 없으면 상위 12. 모호하면 `AMBIGUOUS`.

**오염 실행** — `Taint.scan(cu, each, later)`(`:163-193`): 메서드마다 `body(m)` → `run(b, entry(b), true)`(`SRC_ON`) → `each` → `k.constants`. `run(b, seed, sources)`(`:266-296`): writer 씨앗(`Writers.PARAMS` 타입) → `grow` → 호출마다 `call`(`:364-376` — `SINKS` 전부, `on` 에 있는 규칙만, `jsonWriter` 면 writer 싱크 건너뜀) → `cookieFlag`(sources 때만) → 생성자 → `redirectView`·`redirectConcat`. `Sink(names, arg, scope, types)`(`:48`), `arg -1` = 어느 인자든, `WRITER` 스코프 = `Writers.writerScope`. `taintedCall`(`:497-512`): `SOURCES`+`SRC_ON` → 정화 호출은 스코프·인자 오염 → 스코프 `reaches` 이고 `SCOPE_CLEAN`(`:74` — equals·length·size·isEmpty·contains·indexOf·hashCode·compareTo·getClass·read…) 밖이면 오염 → `ARG_CLEAN` 조회 → 인자 `reaches`. `Body.types`(`:54`)는 매개변수·지역만(`:211-222`, `new T` 우선, `var` 제외) — **필드 타입 없음**(`TaintLink.ClassInfo.fieldTypes` 에만). `body()` 는 `CatchClause` 를 안 본다 — `CatchClause` 는 `JavaRules.java:31·150·596·623`(emptyCatch·swallow·복잡도)에만.

**규칙 배선** — 하이브리드 = `kind` 정규식 + `params: {java: taint}` + `Taint.knows(id)`(`SINKS` 키) → `.java` 는 Taint, 다른 글롭은 정규식(`RuleSet.java:144-155`, `RegexRule.java:33`, 못 읽은 .java 는 정규식으로 `JavaRules.java:234`). 예 `ssrf`·`openRedirect`·`responseSplitting`(`rules.yaml:302-328`). `pure-rules.json` 은 `kind` regex 정의 전부(params 는 안 싣는다, `PureRulesJsonTest.java:33`) — 하이브리드로 바꿔도 **불변**. `PureCodeCheckTest` 는 순수본 JS 정규식 지적 == `fixtures.json` 의 그 규칙 줄 → **`security/pos.java`·`neg.java` 에 Taint 만 잡는 줄을 넣으면 빨강** → 새 꼴은 `fixtures/check/java/link/`(`java.json`, `JavaRulesTest`) 에. `fixtures.json:206-208` `security/pos.java:12 exceptionMessage`(`model.addAttribute("err", e.getMessage())`, `pos.java:12` — `try { q(request, model, id); } catch (Exception e) { … }`) 는 Taint 가 **같은 줄**을 내야 한다. `neg.java:28` `log.error("…", e2.getMessage())` 는 안 내야 한다. `CheckRulesTest.java:167-168` 111·42. `JulietCorpusTest.MAP` CWE209 → `security.exceptionMessage` 이미(`:50`).

**Juliet 채점** — `JulietCorpusTest.java:136` `if (p.getFileName().toString().contains("_Servlet"))` 안에서 `sv`(서블릿 tp·fn·files)와 `CASE`(`:59`, 묶음 키 = `s01/…_22`) → `cases` → `variants`(`:217`, 01~31 메서드 안 · 32~45 같은 파일 · 46~ 파일 사이). 주석 `:102`·`:203` 도 `_Servlet`. 골든 `security-juliet.json` — `servlet`·`variants` 는 수가 0 이면 안 붙는다(`:204`·`:230`). 요청 출처 이름 셋 `getParameter_Servlet`·`getCookies_Servlet`·`getQueryString_Servlet`(폴더별 수: CWE80 360 · 81/83/601 180 · 113 549 · 89 915 · 23/36/78 180 · 209 0). `securityOnly()`(`:70`) 가 common 을 끈다 → CWE209 콘솔 `printStackTrace()` 17 은 영영 FN(R 50 이 천장).

**Juliet 꼴(실측)** — CWE259: `data = "7e5tc4s3"` → `DriverManager.getConnection("data-url", "root", data)` · `new PasswordAuthentication("user", data.toCharArray())` · `new KerberosKey(principal, data.toCharArray(), null)`. 41 `badSink(String data)` 에 싱크 · 42 `String data = badSource()` · 45 `private String dataBad; bad(){ dataBad = "…"; badSink(); } badSink(){ String data = dataBad; … }` · 51a→51b·52a→b→c·53·54 다른 파일 `badSink(data)` 사슬 · 61 다른 파일 `badSource()` 반환 · 68a `public static String data; data = "…"; new 68b().badSink()` / 68b `String data = 68a.data` · 71 `Object dataObject = data; badSink(dataObject)` → `(String) dataObject` · 81 추상 클래스 `badSink` 구현. CWE321: `new SecretKeySpec(data.getBytes("UTF-8"), "AES")`, 같은 변형. CWE209: `catch (…Exception e) { e.printStackTrace(response.getWriter()); }`(서블릿 17) · `e.printStackTrace()`(콘솔 17). good: `response.getWriter().println("There was an unsupported operation error")`.

**실물** — roller `CalendarTag.java` 122 `Calendar cal;` 162 `cal = model.getCalendar();` 158 `SimpleDateFormat formatTitle = new SimpleDateFormat(…)` 192 `pw.print( formatTitle.format(day) )` 284·292·309·317 `pw.print(cal.get(Calendar.DAY_OF_MONTH))`. `cal`·`formatTitle` 은 **지역**이라 `Body.types` 에 있다. egov·jeecg 에 `printStackTrace(response|out`·`getWriter().print…getMessage`·`sendError(…getMessage` 꼴은 grep 0 — 새 꼴의 실물 지적은 R-10 이 센다.

**함정** — ① `metric.classMethods` 30 · `metric.complexity` 15 — `Taint`(메서드 약 36 은 `self-check.txt` 기준선에 **없다** = 30 아래로 세는 듯; 새 메서드를 더하지 말고 새 클래스 `Errors`·`ConstLink` 로) · `TaintLink`(45 줄 매치 중 메서드 약 25) · `TaintConst`(22) ② PMD 는 행마다 `bash scripts/mvn.sh -q pmd:check` ③ `TaintTest.hits`/`hitsIn` 은 `Taint.scan(cu)` 직접(링크 없음) — 상수 `Need` 는 링크 없으면 **버린다**(단위 시험 불변), 예외 실행은 링크 없이도 돈다 ④ `TaintTest.ALL`(`:30`)에 `security.exceptionMessage` 를 더해야 `hits` 가 그 규칙을 켠다 — `DEFS` 는 내장 정의(정규식 있음)를 쓴다 ⑤ `linkThroughRuleSet`(`TaintTest.java:270`) 기대 다섯 줄은 Pos 줄을 더하면 바뀐다(의도) ⑥ `Sanitizers.ACCEPTS` 에 `exceptionMessage` 없음 → NUM 만 씻는다(맞다) ⑦ `security/pos.java`·`neg.java` 는 **안 고친다**(순수본 동기·`PureCodeCheckTest`) ⑧ `jsonWriter`(`call` `:370`) 가 JSON 응답의 writer 싱크를 끈다 — 예외 실행에서는 **끄면 안 된다**(JSON 오류 응답도 노출) ⑨ `TaintLink.method` 는 매개변수 전부를 씨앗으로 다시 돈다 — 예외 타입 매개변수(`Exception e`)도 이미 요청 오염 씨앗으로 돈다(`NOT_INPUT` 에 없으면) → 예외 실행과 겹치지 않게 예외 실행은 `scan` 안에서만(요약 없음).

## 3. 설계 결정 — 전부 [고정]

1. **`KerberosKey`** — `PASSWORD_CALLS` 에 `"KerberosKey", 1`. `secretArg` 는 생성자 이름으로도 묻는다(`:161`) — 그대로.
2. **필드 상수는 대입도** — `TaintConst.fieldConstants(cu)`: `FieldDeclaration` 초기값에 더해 CU 안 모든 `AssignExpr` 중 대상이 `NameExpr`(이름이 필드이고 그 메서드의 지역·매개변수가 아님) 또는 `this.f` 인 것의 값을 `resolve(값, 필드맵)` 해서 **합집합**(`merge`). 두 바퀴(대입이 다른 필드 상수를 쓸 수 있다). 지역·매개변수 가림은 메서드마다 `Taint.outsideLambda(m, VariableDeclarator)`·`getParameters` 이름 집합으로. Juliet 45 가 링크 없이 풀린다. 버린 대안 — 필드 쓰기를 `TaintLink` 요약으로(같은 CU 는 바로 보는 게 싸다).
3. **못 푼 인자 = `Need`** — `TaintConst` 에 `record Need(String rule, Kind kind, String type /*null = 자기 클래스*/, String name, int index, Node at)` · `enum Kind { PARAM, CALL, FIELD }`. `secretArg` 에서 `resolve` 가 비었을 때 인자를 `unwrap`(괄호·형변환 · `CONST_SCOPE` 호출이면 그 스코프 — `data.toCharArray()`·`KEY.getBytes("UTF-8")`)한 식이
   - 매개변수 이름 → `PARAM(index = 그 매개변수 자리)`
   - 지역 이름이고 그 지역의 선언·대입 값(unwrap 뒤)이 매개변수 이름 → `PARAM`(한 홉, `ROUNDS` 로 사슬: `Object o = p; String s = (String) o;`)
   - 지역 이름이고 값이 메서드 호출 → `CALL(호출 노드)` · 식 자체가 메서드 호출(`CONST_SCOPE`·`DEFAULTED` 아님) → `CALL`
   - `FieldAccessExpr`(스코프가 `this` 아님) → `FIELD(type = 스코프 이름, name)` · 지역·매개변수가 아닌 bare 이름이 `l` 에 없음 → `FIELD(type = null, name)`(자기·상위 클래스 필드)
   `TaintConst.needs()` 리스트(`pending()` 과 같은 꼴). `Taint.scan(cu, each, later)` 는 `later != null` 일 때만 `k.needs()` 를 `later` 옆 새 인자 `List<TaintConst.Need> needs` 에 넘기고, 링크 없으면 버린다. 탐침(규칙 정규식 `password = "x"`·`SecretKeySpec("`)은 풀 때 한 번. 버린 대안 — 글자 상수 전부를 오염 씨앗으로(씨앗 수십 배·`VcsCorpusTest` 119초 위험) · 모든 지역을 상수 요약으로(필요한 건 비밀번호·키 자리뿐).
4. **상수 요약 세 가지** — 새 클래스 `core/check/ConstLink.java`(package-private, `TaintLink` 가 소유): `record Call(Ref ref, int arg, Set<String> values)`(호출 인자가 상수) · `record Pass(Ref ref, int arg, int param)`(호출 인자가 자기 매개변수 — 사슬) · `record Ret(Set<String> values)`(반환식이 상수) · 클래스마다 `Map<String, Set<String>> fields`(§3-2 의 `fieldConstants` 결과 — `TaintLink.info` 때 `ClassInfo` 옆에 `ConstLink.classFields.put(c.id(), …)`). `TaintLink.method` 끝에 `consts.see(id, b, l)` — 메서드의 `l`(`TaintConst.constants` 가 만든 지역 상수 맵, `Taint.scan` 의 `each` 콜백 인자에 더한다: `each.accept(b, f, l)` → `TriConsumer` 대신 `Facts` 에 싣지 말고 `Body` 에 `Map<String, Set<String>> consts` 필드로 — `body(m)` 뒤 `k.constants` 가 채운 맵을 `b.consts()` 에 넣는다(record 는 불변이라 `body()` 에서 빈 `HashMap` 을 만들고 `constants` 가 `putAll`)) → 호출마다 인자가 `resolve` 되면 `Call`, 매개변수 이름(한 홉 포함)이면 `Pass`, `ReturnStmt` 식이 `resolve` 되면 `Ret`. `Ref` 는 `TaintLink.ref(call, types, c)` 로(package-private 로 연다). `Need` 는 `summarize` 에서 `NeedAt(rule, file, line, excerpt, kind, type, name, index, methodId, classId)` 로 떼어 둔다(`Later` 와 같은 수법 — AST 안 남김).
5. **풀기(`finish`)** — `TaintLink.finish` 의 오염 고정점 뒤, `Later` 앞에 `consts.finish(this::targets, this::pick, this::up …)`: ① `paramVals: Map<String /*methodId#idx*/, Set<String>>` — 모든 `Call` 을 `targets(ref, from)` 로 풀어 `target#arg ∪= values` ② 사슬 — `Pass` 마다 `paramVals[자기 id#param]` 이 비지 않으면 `targets(ref)#arg ∪= 그 값`, 변화 없을 때까지(`ROUNDS` 50) ③ `NeedAt` 마다 값 = `PARAM` → `paramVals[methodId#index]` · `CALL` → `targets(ref)` 의 `Ret` 합집합(한 단계 — 반환이 매개변수인 꼴은 안 푼다) · `FIELD` → `type` 있으면 `pick(type)` 의 `fields[name]`, 없으면 자기 클래스 + `up`(상위 12)의 `fields[name]` → `plain(값)` 이 비지 않고 규칙 정규식이 탐침에 맞으면 `Finding(file, line, group, rule, severity, excerpt)`(`seen` 중복 제거). 값마다 8 상한. 버린 대안 — 오염 고정점에 상수를 태우기(`State` 가 불리언이라 값을 못 나른다).
6. **예외 출처** — 새 클래스 `core/check/Errors.java`(package-private): `static final String RULE = "security.exceptionMessage"` · `static final String EXC_ON = "\u0000exc"`(표식) · `static Set<String> seeds(Body b)` = `Taint.outsideLambda(b.m(), CatchClause.class)` 의 매개변수 이름 + `b.types()` 중 타입이 `Exception`·`Throwable`·`Error` 로 끝나는 매개변수·지역 이름 · `static final List<Sink> SINKS` = `sink(-1, WRITER, print, println, printf, format, write, append)` · `sink(1, null, sendError)` · `sinkT(1, "(?i)^(model|mav|modelAndView|modelMap|.*model)$", {Model, ModelMap, ModelAndView, Map}, addAttribute, addObject, put)` · `sink(-1, null, body)`(ResponseEntity `.body(msg)`) · `static boolean stackToWriter(MethodCallExpr c, Set<String> t, Set<String> writers, Body b)` = 이름 `printStackTrace` 이고 인자 ≥ 1 이고 스코프가 `Taint.tainted` 이고 인자 0 이 `Writers.writerScope(arg0, writers, b)` 또는 `new PrintWriter(응답 스트림)`(`Writers.fromResponse`). `Taint.SINKS.put(Errors.RULE, Errors.SINKS)`(`knows` 가 참이 되게). `Taint.call`: `t.contains(EXC_ON)` 이면 **`Errors.RULE` 싱크만**, 아니면 `Errors.RULE` 건너뜀; `jsonWriter` 는 `EXC_ON` 때 **안 본다**. `Taint.scan`: 메서드마다 `run(b, entry(b), true)` 뒤 `if (on.containsKey(Errors.RULE)) { Set<String> ex = Errors.seeds(b); if (!ex.isEmpty()) all.addAll(runExc(b, ex).sinks()); }` — `runExc` 는 `run` 에 `boolean` 하나 더 두지 말고 `run(b, seed ∪ {EXC_ON}, false)` 로(표식이 씨앗 집합에 실린다 — `SRC_ON` 과 같은 수법, 메서드 수 불변). `grow`·`taintedCall` 은 그대로(`e.getMessage()`·`e.toString()`·`"x" + e`·`ExceptionUtils.getStackTrace(e)`·`e` 자체 → 오염 — `SCOPE_CLEAN` 에 `getMessage` 없음). `stackToWriter` 는 `run` 의 호출 루프에서 `EXC_ON` 일 때 `Hit(Errors.RULE, c)`. `rules.yaml:265` `security.exceptionMessage` 에 `params: {java: taint}   # 설계 45 — .java 는 예외 출처 오염 추적(Errors), .jsp 는 이 정규식` 한 줄(정규식·글롭·message 불변 → `pure-rules.json`·`code_check.html` 불변). `TaintTest.ALL` 에 추가. 버린 대안 — 새 id `security.errorExposure`(`getMessage` 줄을 두 규칙이 센다) · 정규식 보강(`sendError(500, msg)` 지역 경유를 못 본다) · 예외 씨앗을 요청 씨앗과 한 실행에(`out.print(e)` 가 xssServlet 로 나온다).
7. **숫자·날짜 반환** — `Sanitizers` 에 `static boolean numeric(MethodCallExpr c)`(이름만 본다 — `tainted(Expression, Set<String>)` 은 `Body` 를 못 받는다): 이름 ∈ `NUMERIC = {size, length, count, indexOf, lastIndexOf, hashCode, ordinal, intValue, longValue, doubleValue, floatValue, shortValue, byteValue, getTime, getTimeInMillis, getYear, getMonth, getDate, getDay, getHours, getMinutes, getSeconds, isEmpty, isBlank, equals, equalsIgnoreCase, contains, startsWith, endsWith, matches, compareTo, compareToIgnoreCase, before, after}` 또는 (`get` 이고 스코프 `lastName` 이 `(?i)^(cal|calendar|.*calendar|gcal|gc)$`) 또는 (`format` 이고 스코프 `lastName` 이 `(?i)^(.*format|.*formatter|sdf|df|fmt)$`) → **깨끗**(불리언·숫자·날짜 글). roller `cal.get(…)`·`formatTitle.format(day)` 둘 다 맞는다. `Taint.taintedCall` 의 `SOURCES` 가지 바로 뒤에 `if (Sanitizers.numeric(c)) { return false; }`. `SCOPE_CLEAN` 이름 목록은 **안 건드린다**(`NUMERIC` 이 상위집합이라 겹쳐도 무해). `String.format(…)`(스코프 `String`)·`map.get(k)`·`list.get(i)` 는 그대로 오염. 버린 대안 — 선언 타입 조건(`Body` 를 `tainted` 까지 꿰면 `Taint` 시그니처 열 곳) · `SCOPE_CLEAN` 확장(인자 오염 가지를 못 막는다 — `format(day)`) · `Body.numeric` 노드 집합(같은 시그니처 문제).
8. **properties 구분자** — `PropertiesIndex.see`: 줄 이음(`\` 로 끝나는 줄은 다음 줄과 잇는다, 앞 공백 제거) → 키 끝 = 첫 **이스케이프 아닌** `=`·`:`·공백(`\\`·`\:`·`\=`·`\ ` 는 키 글자) → 값 = 그 뒤 `=`·`:`·공백을 건너뛴 나머지 → 키·값의 `\:`·`\=`·`\ `·`\\` 를 벗긴다(`\uXXXX` 는 안 푼다). 기존 시험 셋 불변.
9. **Juliet 서블릿 판정** — `JulietCorpusTest.java:136` → `SERVLET_SOURCE.matcher(name).find()`, `static final Pattern SERVLET_SOURCE = Pattern.compile("_(?:getParameter|getCookies|getQueryString)_Servlet_")`. 주석 `:102`·`:203` 을 「요청 출처(getParameter·getCookies·getQueryString)」 로. 골든 — CWE80·81·83·601·`_total` 의 `servlet`·`variants` 가 바뀌고 CWE209 의 `servlet` 블록이 사라진다(의도). 다른 CWE 는 불변(이름이 이미 출처).
10. **지표(게이트 아님)** — Juliet CWE259 R 20 → 50 안팎(driverManager·passwordAuth 20 → 30 안팎/59 · kerberosKey 0 → 30 안팎) · CWE321 R 31 → 55 안팎 · CWE209 P 100 · R 50(서블릿 17/17) · CWE80·81·83·601 `variants` 메서드 안·같은 파일 100 · 파일 사이 92 안팎 · 다른 CWE·Benchmark 11 범주 **불변**(상수·예외 실행은 다른 규칙을 안 건드린다 — 바뀌면 보고) · 실물 roller xssServlet 17 → 12 · 새 규칙 꼴 실물 지적은 R-10.
11. **R-10 손 판정** — 일곱 표본(egov 넷·roller·jeecg·zheng·ruoyi-classic 중 바뀐 것)에서 `hardcodedPassword`·`hardcodedKey`·`exceptionMessage` 의 **새로 생긴** 지적 ≤ 20 씩 읽고 참·오탐·모름 · roller `CalendarTag` 5 줄 사라짐 확인 · 사라진 `exceptionMessage`(정규식 → 오염 전환으로 빠진 것) ≤ 20 읽고 「고침(오탐)·잃음(참)」. 표 `| 표본 | 파일 | 줄 | 규칙 | 전 | 후 | 판정 | 근거 |`. 코드 본문은 안 옮긴다(규칙 3).
12. **compare ⑪** — `design/compare-2026-10-28.md` 끝에 `## ⑪ 재측정 — R3-4(번들 61, 2026-10-30)`: 올라간 후보 다섯 · Juliet 15(+서블릿 참값 · 변형 묶음) · Benchmark 11(불변 확인) · 실물 바뀐 규칙 · 시험 시간·`_link` · R-10 표 · 「드러난 것」(CWE327 P 50 = ECB 판정 한 줄 포함) · 「후보로」(예외 메서드 사이 · 컬렉션·직렬화 상수 · 캐시 바이트 · 템플릿 HTML · yml). ①~⑩ 은 안 고친다.
13. 행 접두 — 엔진 `5-56a·b·c`, 채점·재측정 `V-41a·b`·`V-41`, 손 판정 `R-10`. 설계 `design/45-bundle61.md`. 가지 `work/2026-10-30c`.

## 4. 실행 스텝

### S0 — 설계 문서·행·번들 표 (설계 세션이 한다)
- 대상: 신설 `design/45-bundle61.md`(이 파일의 「맥락」~8절, 머리 인용문 삭제) · `PLAN.md`(번들 표 `:545` 「번들 60」 아래 「번들 61」 · 분할표 `:1053` 「5-55d」 아래 `5-56a`·`5-56b`·`5-56c`·`V-41a`·`V-41b`·`R-10`·`V-41` 일곱 행, 압축 꼴 「설계 45 §… 가 원본 + 요지 + 강제 지점」) · `PROGRESS.md`(「열려 있는 것」 머리 → 「**번들 61(설계 45, 철저화 R3-4 — 보안 마무리) 설계 끝 — 가지 `work/2026-10-30c`**, 다음은 「번들 해」」, 이력 S0 줄 — 직전 줄(5-55d) 해시 칸을 `c0f8a89` 로 채운다).
- 절차: 가지 `work/2026-10-30c` 그대로 → 세 파일 → `bash scripts/doc-lint.sh design/45-bundle61.md PLAN.md PROGRESS.md` → `bash scripts/verify.sh` → `git commit -m "docs: S0 설계 45 — 번들 61 행·번들 표(철저화 R3-4 보안 마무리)"`.

### 5-56a — 상수 전파 사이(`KerberosKey` · 필드 대입 · `Need` · `ConstLink`) + 시험·픽스처
- 대상: `TaintConst.java`(`PASSWORD_CALLS`·`fieldConstants`·`Need`·`needs()`·`secretArg`) · 신설 `ConstLink.java` · `TaintLink.java`(`method` 끝 `consts.see` · `summarize` 의 `NeedAt` · `finish` 의 `consts.finish` · `ref` package-private) · `Taint.java`(`Body.consts` · `scan` 의 `needs` 인자) · `TaintTest.java` · `TaintLinkTest.java` · 신설 `ConstLinkTest.java` · 픽스처 `fixtures/check/java/link/` — 신설 `PosLinkConfig.java`(`public final class PosLinkConfig { public static final String DB_PW = "s3cret!"; static String key() { return "0123456789abcdef"; } }`) · `PosLinkServiceImpl.java` 끝에 메서드 셋(`DriverManager.getConnection(url, "sa", PosLinkConfig.DB_PW)` · `new javax.crypto.spec.SecretKeySpec(PosLinkConfig.key().getBytes(), "AES")` · `void open(String pw) { DriverManager.getConnection(url, "sa", pw); }` + `PosLinkController` 에서 `svc.open("hunter2")`) · `NegLinkServiceImpl.java` 에 `void open(String pw) { … getConnection(url, "sa", pw); }` 을 `NegLinkController` 가 `open(request.getParameter("pw"))` 로 부르는 줄(상수 아님 → 0).
- 변경: §3-1~5. `TaintConst`: `PASSWORD_CALLS` + KerberosKey · `fieldConstants` 대입 합집합 · `secretArg` 가 빈 `resolve` 때 `need(arg, m)` 으로 `Need` 를 `needs` 에. `Taint.scan(cu, each, later, needs)`(넷째 인자 — `scan(cu)` 는 `null`, 링크 없으면 버림). `Body` 에 `Map<String, Set<String>> consts`(`body()` 가 빈 맵, `k.constants(m, fields)` 가 `b.consts().putAll(l)` — 시그니처 `constants(m, fields, Body b)`). **순서**: `scan` 안에서 `k.constants(m, fields, b)` 를 `each.accept(b, f)` **앞**으로 옮긴다(지금은 `each` 뒤 `:179` — 그대로면 `TaintLink.method` 가 빈 `consts` 를 본다). 지적 순서는 끝에 줄 정렬이라 안 바뀐다. `ConstLink`: `see(methodId, Body b, ClassInfo c, Function<MethodCallExpr, Ref> ref)` · `classFields(classId, Map)` · `finish(...)` → `List<Finding>`. `TaintLink.finish` 가 `st.out().addAll(consts.finish(...))` 를 `Later` 앞에.
  - `TaintTest`(링크 없음): ㉠ `new KerberosKey(p, "7e5tc4s3".toCharArray(), null)` → hardcodedPassword 1 ㉡ 필드 대입 — `class A { String pw; void a(){ pw = "x1"; } void b(){ DriverManager.getConnection(u, s, pw); } }` → 1(b 줄) ㉢ 매개변수 인자 `void c(String pw){ getConnection(u, s, pw); }` 단독 → 0(링크 없으면 버림).
  - `ConstLinkTest`(`RuleSet.Run` 둘 이상 `apply` + `finish`, 픽스처 글을 시험 안 문자열로): ㉣ 다른 클래스 `static final` 필드 → 1 ㉤ 반환 상수 메서드(다른 파일) → 1 ㉥ 호출자가 상수 인자 → 피호출자 싱크 줄 1 ㉦ 두 홉 사슬(a → b → c) → 1 ㉧ `Object o = "x"; sink(o)` 피호출자 `(String) o` → 1 ㉨ 인자가 요청 값 → 0 ㉩ 모호(같은 이름 클래스 둘) → 0 · `AMBIGUOUS` 1 ㉪ 값 8 상한.
  - `TaintTest.linkThroughRuleSet` 기대에 Pos 새 줄 셋(hardcodedPassword 2 · hardcodedKey 1) 추가 · `TaintLinkTest` 26 그대로.
- 검증: `bash scripts/mvn.sh -q test -Dtest=TaintTest,TaintLinkTest,ConstLinkTest,JavaRulesTest,CheckRulesTest,SelfCheckTest` · `java.json` 에 link 새 줄(의도) · `fixtures.json`·`pure-rules.json`·`self-check.txt` diff 0 · `pmd:check` 0 · `bash scripts/verify.sh`.
- 의존: 없음.

### 5-56b — 예외 출처 `Errors` + `exceptionMessage` 하이브리드 + 시험·픽스처
- 대상: 신설 `Errors.java` · `Taint.java`(`SINKS.put` · `call` 가름 · `scan` 둘째 실행 · `run` 의 `stackToWriter`) · `rules.yaml:265`(`params` 한 줄) · `TaintTest.java`(`ALL` + 사례) · 픽스처 `fixtures/check/java/link/PosLinkController.java` 끝 메서드(`catch (Exception e) { e.printStackTrace(response.getWriter()); }` · `catch (Exception e) { String m = e.getMessage(); response.sendError(500, m); }`) · `NegLinkController.java`(`catch (Exception e) { log.error("x", e); response.getWriter().println("오류"); }` → 0).
- 변경: §3-6.
  - `TaintTest`: ㉫ `catch (Exception e) { response.getWriter().println(e.getMessage()); }` → exceptionMessage 1 ㉬ `e.printStackTrace(response.getWriter())` → 1 ㉭ `e.printStackTrace()`(인자 없음) → 0 ㉮ `String m = e.toString(); response.sendError(500, m)` → 1 ㉯ `model.addAttribute("err", e.getMessage())` → 1(`fixtures.json:206` 과 같은 꼴) ㉰ `log.error("x", e.getMessage()); out.print("오류")` → 0 ㉱ `out.print(result.getMessage())`(result 는 예외 아님) → 0 ㉲ JSON 응답 메서드에서 `pw.write("{\"e\":\"" + e.getMessage() + "\"}")` → 1(§3-6 — jsonWriter 무시) ㉳ 같은 메서드의 요청 값 `out.print(p)` 는 xssServlet 1 · exceptionMessage 0(두 실행이 안 섞인다) ㉴ `void h(Exception ex, HttpServletResponse response) { response.getWriter().print(ex.getMessage()); }` → 1(타입 씨앗).
  - `TaintTest.hybridThroughRuleSet`(`:221`)·`CheckRulesTest.fixturesGolden`: `security/pos.java:12` 가 그대로 `exceptionMessage` 한 줄(Taint 가 냄) — `fixtures.json` diff 0.
- 검증: 위 + `PureCodeCheckTest`·`PureRulesJsonTest` 초록 · `fixtures.json`·`pure-rules.json` diff 0 · `java.json` link 새 줄 둘 · `pmd:check` 0 · `verify.sh`.
- 의존: 5-56a.

### 5-56c — 숫자·날짜 반환 깨끗 + properties 구분자
- 대상: `Sanitizers.java`(`numeric`) · `Taint.java`(`taintedCall` 한 가지) · `PropertiesIndex.java` · `TaintTest.java` · `PropertiesIndexTest.java`
- 변경: §3-7·8.
  - `TaintTest`: ㉵ `Calendar cal = model.getCalendar(); out.print(cal.get(Calendar.DAY_OF_MONTH))`(model 오염) → 0 ㉶ `SimpleDateFormat f = new SimpleDateFormat(x); out.print(f.format(day))`(day 오염) → 0 ㉷ `out.print(list.size())`(list 오염) → 0 ㉸ `out.print(map.get(p))`(map 오염, 스코프 이름 `map`) → 1(그대로) ㉹ `out.print(String.format("%s", p))` → 1(그대로).
  - `PropertiesIndexTest`: `key value`·`key:value`·`a\:b = c`(키 `a:b`)·`k=v1\` + 다음 줄 `v2`(값 `v1v2`)·기존 셋.
- 검증: `TaintTest`·`PropertiesIndexTest` · 골든 diff 0 · `pmd:check` 0 · `verify.sh`.
- 의존: 5-56b.

### V-41a — Juliet 서블릿 판정 보정
- 대상: `JulietCorpusTest.java`(`:136` + 주석 `:102`·`:203`)
- 변경: §3-9. 골든은 V-41b 에서.
- 검증: 컴파일 · `verify.sh`(corpus 태그는 안 돈다).
- 의존: 5-56c.

### V-41b — 재채점 + 시간
- 대상: `golden/corpus/security-juliet.json`·`-fp.txt` · `security-benchmark*`(불변 기대) · `check-*.json`·`-hits.txt` · `vcs-egov.json`
- 변경: `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='SecurityBenchmarkCorpusTest,JulietCorpusTest,CheckCorpusTest,LegacyCorpusTest,VcsCorpusTest' -Djunit.timeout=10m -Dgolden.update=true` → diff 를 읽고 전·후를 이력에(Juliet 15 + 서블릿·변형 · Benchmark 11 불변 확인 · 실물 · 벽시계 · `_link`). 기대는 §3-10. 기대 밖이면 고치지 않고 이력에(사다리 둘: §3-3 `Need` 꼴 하나 · §3-7 이름 하나).
- 검증: 다섯 시험 초록(갱신 뒤 다시 돌려 diff 0) · 시험 하나 10분 안 · 등급 A 0.
- 의존: V-41a.

### R-10 — 손 판정(새 지적 · 사라진 지적 · roller 달력)
- 대상: `design/compare-2026-10-28.md`(⑪ 「손 판정」 절) · `PROGRESS.md`
- 변경: §3-11. CLI `toolbox.bat check <표본> --groups security --profile demo --json`(data 는 scratch)로 바뀐 표본을 뽑아 `check-*-hits.txt` 전·후 diff 와 맞춘다.
- 검증: `bash scripts/doc-lint.sh design/compare-2026-10-28.md` · 요약 합이 맞음.
- 의존: V-41b.

### V-41 — compare ⑪ · `--corpus`
- 대상: `design/compare-2026-10-28.md`(⑪ 나머지) · `PROGRESS.md`
- 변경: §3-12.
- 검증: `bash scripts/verify.sh --corpus` 초록 → `verify.sh` 초록. `--db` 는 안 돈다(MANIFEST·db 레인 불변 — 끝 줄이 db 를 부르면 보고).
- 의존: R-10.

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7 · 행의 축·강제 지점 · 픽스처 양성·음성 · 골든이 의도된 변화인가 · **예외 실행이 요청 규칙을 안 내나(`out.print(e)` 가 xssServlet 로 안 나오나) · `Need` 풀기가 요청 값 인자를 상수로 안 보나 · `numeric` 이름 휴리스틱이 `map.get`·`String.format` 을 안 씻나 · `security/pos.java`·`neg.java` 불변**) → `gate-probe.sh` → `verify.sh` → `git push -u origin work/2026-10-30c` → `gh pr create --base main`(행마다 시작 세 줄 표 + 번들 60 닫기·5-55d 가 같이 실림 + 검증 + ⑪ 핵심 수치 — Juliet CWE259·321·209 R · 변형 묶음 참값 · roller xssServlet · R-10 요약 · 벽시계) → CI 폴링 → claude[bot] 처분 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → 닫기 커밋은 다음 가지 → 끝 보고. PR 본문 끝 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## 5. 금지 사항
- `pure/`·portfolio·`docs/`·화면 수정 금지 · `rules.yaml` 은 `:265` 블록의 `params` 한 줄만(정규식·글롭·message·규칙 수 111·security 42 불변) · 새 규칙 id 금지
- `fixtures/check/security/pos.java`·`neg.java` 수정 금지(순수본 동기·`PureCodeCheckTest`) — 새 꼴은 `fixtures/check/java/link/` 에만
- `core/check` 가 `core/analyze` import 금지 · `CheckRunner`·`CorpusFiles`·`GoldenFiles`·`verify.sh`·훅·CI·`RuleSet`·`JavaRules` 수정 금지(이번 번들은 배선이 `Taint`·`TaintLink` 안에서 끝난다)
- `SINKS`·`SOURCES`·`SCOPE_CLEAN`·`ARG_CLEAN`·`MUTATORS`·`Sanitizers` 범주 표의 **기존 이름** 변경 금지(`Errors.SINKS` 와 `Sanitizers.numeric` 은 더하는 것) · 「인자 오염 → 결과 오염」 일반 완화 금지 · 요약으로 씻기 금지 · 글자 상수 전부를 씨앗으로 금지(§3-3)
- `Taint`·`TaintLink`·`TaintConst` 에 메서드 추가 최소(`classMethods` 30) — 새 몫은 `ConstLink`·`Errors`·`Sanitizers`
- 발췌·로그·골든·compare 에 코드 본문·상수 값 본문을 싣지 않는다(규칙 3 — 상수 값은 판정에만, `Finding` 발췌는 소스 줄 `Finding.excerpt` 그대로 · R-10 표는 파일·줄·판정만)
- `corpus/MANIFEST`·표본 변경 금지 · Juliet `assumeTrue` 유지 · 새 Maven 의존성 금지 · 상수 조건 가지치기 구현 금지

## 6. 최종 검증
- `bash scripts/verify.sh` · `--corpus` 초록 · `pmd:check` 0 · `gate-probe.sh` 10/10 · `--db` 는 안 돈다(db 도장 full 유지 — 끝 줄 확인)
- 골든: `fixtures.json`·`pure-rules.json`·`self-check.txt` diff 0 · `java.json` link 줄만 늘어남 · `security-benchmark*` diff 0 · `security-juliet.json` 전·후가 이력·⑪ 과 일치
- 시험: `TaintTest` 85 안팎 · `TaintLinkTest` 26 · `ConstLinkTest` 8 · `PropertiesIndexTest` 7 · 다섯 corpus 시험 10분 안
- 수동: 붙여넣기 탭 — `catch (Exception e) { response.getWriter().println(e.getMessage()); }` → `security.exceptionMessage` 1 · `catch (Exception e) { log.error("x", e); }` → 0 · `DriverManager.getConnection(u, s, Config.PW)` 단일 파일 → 0(링크 없음 — 폴더 검사로는 1)
- compare ⑪ 표 + R-10 표 + 「드러난 것」 + 「후보로」

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- `fixtures.json`(특히 `security/pos.java:12`)·`pure-rules.json`·`self-check.txt` 가 바뀐다 · `PureCodeCheckTest` 빨강
- `TaintTest` 기존 사례가 `numeric`·예외 실행으로 깨지는데 §3-6·7 로 설명이 안 된다(특히 `out.print(e)` 가 xssServlet 로 나온다)
- `SelfCheckTest` 메서드 수·복잡도가 `ConstLink`·`Errors` 로 나눠도 안 들어간다 · `ArchitectureTest` 빨강
- V-41b 에서 등급 A 가 새로 생긴다 · Benchmark 11 범주가 바뀐다 · Juliet CWE23·36·78·89·113 이 바뀐다(상수·예외 실행이 새어 나간 것) · CWE259 R 이 20 아래로 간다 · 시험 하나 10분 초과
- R-10 에서 사라진 `exceptionMessage` 중 「잃음(참)」 > 2(보고만 — 머지는 사용자 판단)
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① 재채점 수치(§3-10 은 추정) ② Juliet 81(추상 클래스 `badSink`)·71(Object 형변환)이 `targets`·한 홉 `PARAM` 으로 풀리는지 ③ 정규식 → 오염 전환으로 실물 `exceptionMessage` 가 몇 줄 사라지나(R-10 이 센다) ④ `VcsCorpusTest` 벽시계(둘째 실행 + 상수 요약). 첫 스텝 전: `bash scripts/verify.sh` 초록 · `ls C:/workspace/toolbox-corpus | grep -c .`(33) · `git log --oneline -1`(`c0f8a89`).
- **사용자만 정할 수 있는 것** — 없음(축·머지 위임은 2026-10-30 저녁에 받았다).
