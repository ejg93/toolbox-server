# 설계 58 — 번들 74: 생성기·DDL(A) + 구조 점검(B) + 코드 검사 철저화(C) (Fable 설계 · Opus 실행, 2026-11-08)

## 맥락
번들 73 리뷰 잔여(1-81·7-30)와 compare ㉓ 「후보로」 의 생성기·구조 점검 넷(A·B), 그리고 compare ⑭·⑮·⑯ 「후보로」 가운데 분할표에 행이 없는 코드 검사 항목 전부(C). 사용자 결정(2026-11-08, 메모리 `bundle74-decisions.md`): (A)+(B)+(C) 를 번들 하나·PR 하나로 최대한 크게 · (D) 개발자 도구 새 탭은 뺀다 · java.time 날짜 표시는 ISO 그대로 · 머지 위임 있음 · 실행은 Opus 「번들 해」.

**걸러낸 것**(PLAN·코드로 확인 — 이미 끝났거나 결함이 아닌 것):
- ⑮ `5-59d`(번들 66 완료) · 가-14 `equals` 상수 앞세우기(번들 66 `java.equalsLiteralFirst`) · 「빠른 길을 같은 두 이름으로 좁히기」(`a2e7729` `namesBoth`) — 완료
- ⑭ `getAcceptedIssuers` 단독 — 빈 `getAcceptedIssuers` 는 결함이 아니다(검사 메서드 둘이 비어야 결함이고 그것은 `trustMethods` 가 잡는다). 실물 0(R-13)
- ⑭ `SSLContext.init` 의 신뢰 관리자 인자 — `trustMethods` 가 `cu.findAll(MethodDeclaration)` 로 이름 있는 클래스·익명 클래스(배열 초기값 안 포함)를 다 잡는다(`SecurityAst:219-226`). 다른 파일의 TrustManager 클래스는 그 파일에서 잡히고, 호출 자리를 또 지적하면 같은 결함을 두 번 세는 것. 설계 48 「같은 파일 한 홉」 결정 그대로 — 안 함
- ⑮ `propagation` 판정 — `txSelfCall` 은 애너테이션 글이 다르면 지적한다(`TxAst.text`). `REQUIRES_NEW` 피호출은 이미 지적되고, 같은 글이면 같은 의미라 제외가 맞다. 정의가 더 없다 — 안 함
- ⑮ `Integer` 캐시 범위 밖 리터럴 — `refEquals` 가 래퍼끼리 `==` 를 값과 무관하게 지적한다. 리터럴 상대(`i == 200`)는 언박싱 비교라 결함이 아니다 — 안 함
- ⑯ 라-1·9·12(sysout → 로거 · 안 쓰는 지역 변수 · `getBytes` 인코딩) — 코드 정리 B 묶음(compare ④)이지 「Java 결함·자원 해제·XXE·SSL」 셋이 아니다. `pmd-baseline.sh` 다시 뜨기는 사람 몫 — 둘 다 범위 밖(후보로 남긴다)

**실측**(2026-11-08, 코드 읽기 — 줄 번호는 `3388d9a` 작업 트리):
- `DdlReader` 는 static 메서드 **30**(상한 30 — 더 못 더한다) · `DdlPull` 21. `DdlReader.item`(`:312-398`, 87줄)은 `self-check.txt:84` 「복잡도 30 · item(3)」 기준선 — 분기를 더하지 않는다. `DdlPull.java` 는 기준선 줄 0
- `INLINE_COMMENT`(`DdlReader:109`)는 `item:367` 에서 **원문 `rest`** 에 `find()` — 같은 줄의 `INLINE_PK`·NOT NULL·`DEFAULT`·`COL_REF` 는 1-78 마스크 `m`(`:360`). `a VARCHAR(20) DEFAULT 'x COMMENT ''y''' COMMENT 'real'` 은 글 안 `COMMENT ''` 가 먼저 맞아 comment 가 빈 글(jshell 확인 — `start=23, group(1)=""`)
- CTAS(`DdlPull:43-57·233-370`): `JOIN_SEP.split` 은 괄호를 모른다 → `USING (K, D)`·`ON f(a, b)` 안 쉼표에서 갈라 빈 Map → notes. NATURAL·USING 은 `JOIN_SEP` 가 받지만 `*`·`t.*`(`selected:303-316`)가 두 원본 컬럼을 그대로 이어 붙여 공통 컬럼이 둘. 식·함수 항목은 `ITEM_BAD`(`:57`)가 걸러 **목록 전체가 빈다** → `pull:112` false → `nameOnly` notes `ctas`(「AS SELECT — 컬럼을 못 읽는다」 `DdlReader:123`). `DdlReaderTest.ctasFromSource:607` javadoc 은 1-80 뒤 거짓. NATURAL·USING 시험 0건. `Column` record(`core/meta/Column.java:7`) 11칸 — `nativeType` 소비처는 전부 null 안전(`nz`·`TypeMapping.norm`·`InsertGen.type`·`StructureLint.norm`·`DtoGenerator:84`·`FieldRoles:257`·`DdlGen` `blank` 가드)
- 날짜 PK 바인딩: `templates/gen/egov35/List.jsp.ftl:57` `<c:param name="[=p.name]" value="${result.[=p.name]}"/>` · `Detail.jsp.ftl:31` hidden `value="<c:out value='${result.[=p.name]}'/>"` — egov35 `java.util.Date` PK 는 `toString`(「Mon Jan 01 …」)이라 VO `@DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")`(`FieldRoles.PATTERN:64`)에 안 묶인다. `pk` 목록 원소는 `fields` 와 **같은 Map** 이라 egov35 면 날짜 PK 에 `datePattern` 이 이미 있다(`GenModel.of:136·163`, `FieldRoles.dates:195-220`). `fmt` taglib 조건(`List:5`)은 `listFields` 만 본다. egov4·egov5 는 `set.yaml` 만(List 자체 템플릿 없음, `javaTime "true"` 라 `datePattern` 없음). 골든 — `golden/gen/egov35/EmpHistList.jsp:46`(PK `startDate` Date) · `EmpHistDetail.jsp:47` hidden · bbs PK 는 `BigDecimal`·`String`(불변) · `Updt.jsp.ftl` 은 `form:input readonly` 라 바인딩 문제 없음
- 7-30: `SmokeHtmlUnitTest` 공용 서버는 저장소 `templates/gen`(`AppTest.config`) — 깨진 세트를 두려면 `crudGeneratorRuns:1117-1130` 처럼 `@TempDir` 에 복사 + 제 `App.start`. `loadSets`(`spring_source_generator_ext.js:47-56`)는 `TB.api`(XHR + Promise) → `waitForBackgroundJavaScript` 로 받는다. `option(sel, value, text)` 뒤 `sel.lastElementChild.disabled/title`
- 구조 점검 detail(`StructureLint.java`): dupIndex `:463` `"인덱스 " + b.name() + " ⊂ " + a.name() + "(" + cols + ")"` · fkTypeMismatch `:401` `"FK " + nz(fk.name()) + " → " + …` · fkNoIndex `:154` `"FK " + nz(fk.name()) + " → " + fk.refTable()` — 이름 없으면 공백 둘(「인덱스  ⊂ X(…)」·「FK  → …」). `uniqueNullable`(`StructureLintColumns:57`)만 2-37 로 `trim`. **골든에 빈 이름 흔적 0**(`golden/**` 에 `인덱스  `·`⊂ (`·`FK  →` 0건) → 골든 diff 0 기대(사용자 「골든 변화 받음」 은 보험)
- `SecurityAst`(24 메서드): 앵커는 `MethodCallExpr` 만(`CREATORS`·`FACTORIES` `:43-45`) — dom4j `SAXReader`·jdom `SAXBuilder` 는 `new` 라 0건. 막음은 **메서드 단위**(`guarded:125-132` `anyGuard(body)`) — 같은 메서드에 팩토리 둘이면 하나만 막아도 둘 다 통과. 필드 초기값 앵커의 범위는 그 `FieldDeclaration` 하나(`findAncestor(BodyDeclaration)`)라 `static {}` 막음을 못 본다. `emptyDocument`(`:108-112`)만 사슬 제외. `newTransformer`·`DOMSource`·`transform` 0건. 표본 — jeecg `OfficeHtmlUtil.java:109-117` `DOMSource domSource = new DOMSource(htmlDocument); … tf.newTransformer(); … serializer.transform(domSource, streamResult)`(㉓ 「확인 대상」) · struts `DomHelper.java:168-181` static 블록 + `newTransformerHandler()`(transform 호출 없음)
- `JavaDefects`(17 메서드, `apply:60-76` if 넷) · `TxAst`(12) · `ValueAst`(**28**, 상한 근접) · `ResourceAst` 바깥 28 + `Planner` 15(「메서드 수 한계 때문에 따로 센다」 `:304`) · `ResourceFix` 9 · `ValueFix` 11 · `Cleanup` 29 · `JavaRules` **30**(새 클래스는 `taint:230-251` 루프 한 줄씩만). 기준선 `golden/check/self-check.txt` 에 이 일곱 클래스 줄 0(Cleanup·JavaRules 만 있음)
- `ValueAst.identityShortcut:129-145` — OR 의 **직접** 반대쪽만(세 항 `a == b || x || a.equals(b)` 는 안 뺌) · `returnsLiteral:155-158` 은 `LiteralExpr` 만(`return CONST;` 안 뺌) · `literalFirst:251-262` 인자 `isStringLiteralExpr()` 만(`x.equals(CONST)` 음성 `NegLiteral:14`) · `formatSlot:340-345` 는 `String.format` 도 날짜 자리 · `declaredType:175-191` 은 첫 `ClassOrInterfaceDeclaration` 에서 끝(enum·record 필드·record 구성요소 못 봄) · `constantLiteral:353-367` 이 CU 의 `static final String` 을 찾는 선례
- `TxAst.rollbacks:85-97` 는 `ROLLBACK_KEYS` 키 유무만(`rollbackFor = {}` 통과) · 메시지 `rules.yaml:917` 「rollbackFor 가 없다」(JTA 는 `rollbackOn`)
- `JavaDefects.lostCause:150-163` — 「썼다」 = 몸 어디든 `NameExpr`(중첩 catch 안 throw 도 바깥이 지적, `e::addSuppressed` 는 `MethodReferenceExpr` scope 가 `NameExpr` 인지 미확인) · `threadLocals:165-184` 는 `remove` 호출만(넘겨 지우는 `clear(tl)` 못 봄) · `DATE_FORMATS:51` = `SimpleDateFormat`·`DateFormat`. catch 형식(Throwable·Error)·로그+throw 규칙 없음. `JavaRules:78-79` `LOG_METHODS`·`LOGGER` 는 private(7-22 전례 — 가시성만 바꾸면 Checkstyle DeclarationOrder 빨강)
- `ResourceAst`: 소유 = 메모리 아닌 `new` + **모든** 호출(`creation:159-166`, 예외는 `servletOwned`·이름 `get`) · 빌린 것은 래퍼 첫 인자 재귀(`borrowed:172-184`) · `borrowedName:187-196` 은 **지역 변수의 초기화식을 안 따라간다**(jeecg `StreamGobbler:21`) · `SERVLET_TYPES`(`:70`) `ServletRequest`·`ServletResponse` 만 · `Files.*`·`Stream` 0건 · `Planner.of:310-332` 는 finally 가 없으면 바로 빈 값(try 몸 끝 close 는 `resourceLeak` 만 — `closeOutsideFinallyLeaks`). `Twr(TryStmt, List<Res>)`(`:90`). `ResourceFix.edit:80-107` 은 finally 줄 꼴 다섯 전제
- `ValueFix.refEquals:118-121` — `java.util.Objects` import 없으면 「이름끼리 비교 — java.util.Objects import 필요」 건너뜀(`cleanup-commons.json:9` 1건). core/check 에 import 끼우기 0. `Cleanup.Edits`(`:922-973`) `replace` 글에 `\n` 을 넣으면 `result()` 는 줄이 늘지만 `touchedNew:960-972` 는 한 줄로 세어 뒤 줄 번호가 어긋난다
- 규칙 `rules.yaml` `- id:` **132**(`CheckRulesTest:167`), security 45(`:168`). 새 java 규칙 자리는 `java.equalsLiteralFirst`(`:946-953`)와 `java.parseError`(`:954`) 사이. `JavaRulesTest.fixturesGolden:50-70` — java·metric 묶음 kind java 규칙은 `fixtures/check/java/Pos*.java` 에서 한 번 이상, `Neg*` 0, 골든 `golden/check/java.json`. security 묶음은 `fixtures.json` + 각 Ast 시험이 줄 번호를 직접 단언(`SecurityAstTest:33-35`). `pure-rules.json`(regex 63)은 무관. README·DocCommands·code_check.html 에 규칙 총수 글 없음
- 표본(R-14·R-15·R-13 판정표): `DecimalFormat`·`MessageFormat` static 은 레거시 전체 1 파일 · `catch (Throwable` 은 미집계(R-21 에서 센다) · jeecg xxe 1(변환기, DOM 만) · egov `RssTagManageDao:70·125` Spring 연결 2 · roller `MediaCollection:105·383` Atom 2 · struts `FreemarkerTemplateEngine:134`·`FreemarkerResult:154` 템플릿 writer 2 · jeecg `StreamGobbler:21` 지역 래퍼 사슬 1 · commons-lang `BooleanUtils:741` 상수 반환 빠른 길 1 · struts `AbstractLocalizedTextProvider:757` 표지 객체 1
- 레인(`scripts/verify-fingerprint.sh`): `core/gen`(main)은 db·corpus · `core/check`(main·test)·`resources/check`·`golden/corpus`·`core/gen` test 는 corpus · `core/deliverable`(main)은 corpus, **그 시험은 java 만** · `templates/`·`fixtures/check`·`golden/check`·`web` 시험은 java 만. 그래서 이 번들은 `--corpus` + `--db`(core/gen). 이름 지정 corpus 시험은 **`-Djunit.timeout=10m` 필수**(`DdlCorpusTest.dtosCompile`·Juliet 2분 초과)
- 자기 코드: `src/main` 에 `catch (Throwable|Error` 0 · static `DecimalFormat`류 0 · `Files.list|walk` 아홉 자리 전부 try-with-resources · `double` 지역 변수는 `Definitions.java` 하나 → 새 규칙이 기준선에 새 키를 낼 가능성 낮음(`SelfCheckTest` 가 잰다)
- `--db` 기준(번들 73 실측): 이 PC 는 커널 풀 8GB 라 앱을 다 내려도 여유 RAM 12GB 안팎. 12.1GB 출발로 989초 초록(최저 3.7GB). **projectshop-c0 세션이 우선**(메모리 `projectshop-priority`) — `docker ps` 에 shop-* 컨테이너가 있거나 그 세션이 큰 작업 중이면 `--db` 를 미루고 사용자에게 묻는다. `wsl --shutdown` 은 그 세션 컨테이너를 죽인다 — 내가 내지 않는다

**범위 밖(사용자 2026-11-08 + 걸러낸 것)**: 개발자 도구 새 탭(바-6·바-7) · java.time 날짜 표시 꼴(ISO 그대로) · `getAcceptedIssuers` 단독 · `SSLContext.init` 인자 · `propagation` · `Integer` 캐시 · 라-1·9·12 · `pmd-baseline.sh`(사람) · 5-31 · `--db` CI 이전 · 0-75 · 현장 MyBatis 판 · 보류·조건부 행 · CTAS 의 별칭 없는 식(`COUNT(*)` 홀로 — 추측 안 함, notes 그대로) · 다른 파일의 TrustManager · 메서드 밖 오염.

## 사용자 결정(2026-11-08)
- (A)+(B)+(C) 번들 하나·PR 하나 · (D) 뺌 · 머지 위임 있음 · 다시 묻지 않는다
- 세부: List.jsp `<c:param>` 은 `<fmt:formatDate pattern>` 으로 VO `@DateTimeFormat` 과 맞춤 · CTAS 식 컬럼은 **별칭 있으면 이름만 + 형식 null + notes** · `dupIndex`·`fkTypes` 빈 이름은 공백 없이(골든 변화 받음) · java.time 표시는 ISO 그대로
- 설계가 정한 것(기본값이 뚜렷해 안 물음, 전부 [고정]): 날짜 PK 바인딩 패턴은 `FieldRoles.PATTERN`(VO 와 같은 글) 을 모델 `bindPattern` 으로 · Detail hidden PK 도 같이 · NATURAL·USING 조인에서 `*`·`t.*` 는 빈 목록 + notes(추측 안 함), 명시 항목은 그대로 풀기 · 조인 분리는 괄호 깊이 0 에서만 · CTAS 식 컬럼의 notes 키는 새 `ctasExpr` · 새 규칙 넷 `java.catchThrowable`·`java.logAndThrow`(warn)·`java.mappingPrivate`(error)·`java.floatEquals`(warn) 은 `JavaDefects` 에 · `staticDateFormat` 은 id 그대로 형식 넷 추가 · XXE 막음은 **변수 단위**(변수 없으면 메서드 단위 그대로), 필드·static 블록은 클래스 범위 · 변환기는 `newTransformer()` 무인자 + `transform` 첫 인자 전부 `DOMSource` 일 때만 제외 · 자원 「빌린 것」 은 scope 의 선언 형식 접미(`Request`·`Response`·`Context`·`Template`·`Session`) 또는 scope 호출 이름(`getSqlSession`류) · `Files.lines|walk|list|find|newDirectoryStream` 의 `Stream`·`DirectoryStream` 만 자원 · twr 꼴 C = 꼴 B(`= null` + try 안 첫 대입) + try 몸 **마지막** 문장들이 close + finally 없음 · `Objects` import 는 마지막 import 줄 뒤에 끼운다(`Edits.touchedNew` 가 줄바꿈 수를 센다) · 상수 앞세우기는 **같은 파일 `static final String` 리터럴 상수**만(`NameExpr`)

실행자는 Opus. 차례 `S0` → `1-81` → `1-82` → `1-83` → `7-31` → `7-30` → `2-38` → `5-61a` → `5-61b` → `5-61c` → `5-62a` → `5-62b` → `5-62c` → `V-54a` → `R-21` → `V-54` → 마무리. 가지 `work/2026-11-08b`(로컬 닫기 `3388d9a` 같이). 번들 하나·PR 하나. 머지 위임 있음.

---

## 1. 목표와 범위
- 한 문장: DDL 읽기가 글 안 `COMMENT` 와 NATURAL·USING·괄호 쉼표 조인과 식 컬럼을 추측 없이 받고, egov35 생성 화면의 날짜 PK 가 바인딩되며, 구조 점검 글에 빈 이름 공백이 없고, 코드 검사가 XXE 를 변수·필드 단위로 가르고 Java 결함 넷·자원 넷·값 비교 셋을 더 정확히 잡으며 코드 정리가 try 몸 끝 close 와 `Objects` import 까지 고친다.
- 완료 시 체감: `DEFAULT 'x COMMENT ''y''' COMMENT 'real'` → comment `real` · `T1 t JOIN T0 s USING (K, D)` 단순 항목이 컬럼 · `SELECT *` + NATURAL → notes · `SELECT A, UPPER(B) AS UB` → 컬럼 `[A, UB]`(UB 형식 null) + notes 「식·함수 컬럼은 이름만」 · egov35 `EmpHistList.jsp` 의 `<c:param name="startDate">` 안에 `<fmt:formatDate … pattern="yyyy-MM-dd'T'HH:mm"/>` · 소스 생성 화면 스모크에서 `(오류)` 옵션 `disabled`·`title` 단언 · 「인덱스 ⊂ X(A)」·「FK → P.C」 · 한 메서드의 팩토리 둘 중 안 막은 것만 xxe · `static {}` 에서 막은 필드 팩토리는 조용 · jeecg `OfficeHtmlUtil` xxe 0 · dom4j `new SAXReader()` 안 막으면 xxe · `catch (Throwable t)`(다시 안 던짐) · 로그 + throw · private `@RequestMapping` · `double ==` · static `DecimalFormat` · `if (a == b) return CONST;`·표지 객체 비교는 refEquals 아님 · `x.equals(CONST)` 지적 + `CONST.equals(x)` 고침 · `Files.lines` 스트림 안 닫음 · `getSqlSession().getConnection()`·Atom·템플릿 writer·지역 래퍼 사슬은 소유 아님 · `T x = null; try { x = …; …; x.close(); } catch` → try-with-resources 고침 · `a == b` 이름끼리 → `Objects.equals` + import · compare ㉔.
- In scope: `core/gen/DdlReader.java`(`item` 한 줄 · `NAME_ONLY` 한 항목) · `core/gen/DdlPull.java`(`depthMask`·`MERGE_JOIN`·`EXPR_ALIAS`·`fromSources`·`selected`·`ctasColumns`·`pull`) · `core/gen/FieldRoles.java`(`K_BIND_PATTERN` 한 줄) · `templates/gen/egov35/List.jsp.ftl`·`Detail.jsp.ftl` · `core/deliverable/StructureLint.java`(detail 세 줄) · `core/check/SecurityAst.java`·`JavaDefects.java`·`ValueAst.java`·`TxAst.java`·`ResourceAst.java`·`ResourceFix.java`·`ValueFix.java`·`Cleanup.java`(`Edits.touchedNew`) · `resources/check/rules.yaml`(새 넷 + 글 둘) · 시험 `DdlReaderTest`·`GenTemplatesTest` 골든·`SmokeHtmlUnitTest`·`StructureLintTest`·`SecurityAstTest`·`JavaDefectsTest`·`ValueAstTest`·`TxAstTest`·`ResourceAstTest`·`CleanupTest`·`CheckRulesTest`·`JavaRulesTest` · 픽스처 `fixtures/check/security/pos.java`·`neg.java`·`fixtures/check/java/Pos*.java`·`Neg*.java`·`fixtures/cleanup/value/Pos.java`·`resource/Pos.java` · 골든 `golden/check/java.json`·`fixtures.json`·`golden/cleanup/*`·`golden/gen/egov35/EmpHistList.jsp`·`EmpHistDetail.jsp`·`golden/corpus/check-*.json`·`-hits.txt`·`cleanup-*.json`·`vcs-egov.json`·`security-benchmark.json` · `design/compare-2026-10-28.md` ㉔ · PLAN·PROGRESS.
- Out of scope: 「범위 밖」 전부 · `DdlReader` 새 메서드 · `matching`·`QUOTED`·`mask` 논리 · egov4·egov5·bbs 골든 · `Regist`·`Updt`·`input.ftl` · `TypeMapping` · `StructureLint.Rule` enum·`dupRelation` 글 · `trustAll` 논리 · `JavaRules`(메서드 30) 몸 · `Cleanup.RULES` 수(14 그대로 — 새 고침 규칙 없음) · `pure/`·portfolio.

## 2. 현재 구조 요약
「맥락」 의 실측이 요약이다. 덧붙이는 함정:
- `DdlReader.item` 의 `INLINE_COMMENT` 를 마스크 `m` 에 맞추면 group(1) 은 공백 — 값은 `rest.substring(ic.start(1), ic.end(1))` 로 원문에서 자르고 `unq` 에 넣는다(`DEFAULT` 와 같은 수법 `:365-366`). 분기 불변
- `JOIN_SEP.split` 을 괄호 깊이 0 으로 — 같은 길이 사본에서 괄호 안을 공백으로 비운 `depthMask` 에서 `JOIN_SEP` 매처로 구분자 자리를 찾고 **원문**에서 자른다(`mask` 와 같은 수법). `splitTop` 은 글자 하나 구분자라 못 쓴다
- NATURAL·USING 판정은 `fromSources` 가 받은 `tail` 에서 `MERGE_JOIN` 으로 — `ctasColumns` 가 `selected` 에 `boolean merged` 를 넘긴다(`selected` 분기 +2, 복잡도 ≤ 15)
- CTAS 식 컬럼: `Column(alias, 0, null, null, null, null, null, true, null, null, null)` — `pull` 이 `at(c, ordinal, name)` 으로 번호를 다시 매긴다(`:115`). notes 는 `pull` 에서 `cols.stream().anyMatch(c -> c.nativeType() == null)` 이면 `d.notes.put(CTAS_EXPR, DdlReader.NAME_ONLY.get(CTAS_EXPR))`. `NAME_ONLY` 는 `DdlReader:122-123` 의 Map — 항목 하나 더(메서드 아님). `DtoGenerator:84` 는 null 형식을 「TODO 타입 확인: ?」 로 낸다(표본 CTAS 0 이라 `dtosCompile` 영향 없음)
- `<c:param>` 은 몸 값이 된다 — `<c:param name="x"><fmt:formatDate value="${result.x}" pattern="…"/></c:param>`. hidden 은 `value="<fmt:formatDate value='${result.x}' pattern="yyyy-MM-dd'T'HH:mm"/>"` — 패턴에 `'T'` 가 있어 pattern 은 겹따옴표, value 는 홑따옴표(JSP 가 태그를 먼저 푼다, 기존 `<c:out value='…'/>` 와 같은 꼴). `fmt` taglib 조건은 `(listFields + pk)` 로
- `SmokeHtmlUnitTest` 새 시험은 제 `App` 을 띄운다(공용 서버는 저장소 `templates/gen`). 깨진 세트는 `@TempDir` 사본에만 — 정리는 TempDir 이 한다
- `SecurityAst.guarded` — 변수 이름이 있으면 `guardsOn(body, v)`(막음 호출의 scope 마지막 이름 == v) 또는 한 홉, 없으면(사슬) `anyGuard(body)` 그대로. 앵커가 `FieldDeclaration` 안이거나 `varName` 이 같은 클래스 **필드** 이름(지역 선언 없음)이면 body = 둘러싼 `TypeDeclaration`(static 블록·메서드 어디서든 그 변수의 막음). `new SAXReader()`·`new SAXBuilder()` 앵커는 `ObjectCreationExpr` — `guarded`·`varName` 의 매개변수를 `Expression` 으로 넓힌다(둘 다 부모 노드만 본다). jdom `setExpandEntities(false)` 는 `EXPAND` 와 같은 꼴 → `Set.of("setExpandEntityReferences", "setExpandEntities")`
- 변환기 DOM 전용: `domOnly(body, v)` — body 안 `newTransformer`·`newTransformerHandler` 호출 전부 인자 0 · `newTemplates`·`newXMLFilter`·`getAssociatedStylesheet` 호출 0 · `transform(` 호출이 **하나 이상**이고 전부 첫 인자가 `new DOMSource(…)` 또는 `ValueAst.declaredType(첫 인자) == "DOMSource"`. 하나라도 어긋나면 지적 그대로. struts `DomHelper` 는 `transform` 0 이라 그대로(CLI 확인 대상)
- `JavaDefects` 새 규칙 넷은 `apply` 에 `if (on.contains(…))` 넷(복잡도 ~10). `LOG_METHODS`·`LOGGER` 는 `JavaRules` 것을 **복사**한다(가시성 바꾸면 DeclarationOrder — 7-22 전례). `SpringMappings.ANNOTATIONS`(public) 재사용. `ValueAst.FLOATING`(`:66`) 은 package-private 로 — 그 줄은 이미 `static final` 묶음 안이라 순서 위반 없음(확인: `:64-70` 전부 private static final → 하나만 package 로 바꾸면 private 앞에 와야 한다. **`FLOATING` 선언을 `:64` `BOXED` 앞으로 옮기고 package-private**)
- `ValueAst` 는 28 → 29(`staticFinal` 하나). `returnsLiteral`·`identityShortcut`·`literalFirst`·`declaredType`·`formatSlot` 은 몸만 고친다(새 메서드 없음). `constantLiteral` 은 `staticFinal` 을 쓰도록 고쳐 중복을 줄인다
- `ResourceAst` 바깥 28 → 29(`filesStream`), `Planner` 15 → 17(`tailCloses`·`ofTail`). `Twr` 에 `List<Statement> closes` 칸 — finally 꼴은 빈 목록. `ResourceFix.edit` 는 finally 가 없으면 `closes` 줄을 지우고 `finallyLine` 0 — `apply:75` 는 `finallyLine > 0` 일 때만 `replace`
- `Edits.touchedNew` — 바꾼 글의 `\n` 수만큼 더 센다(각 줄을 `out` 에). `result()` 는 `String.join("\n")` 이라 이미 늘어난다. import 끼우기는 `ValueFix` 가 마지막 `ImportDeclaration` 줄(없으면 `package` 줄, 그것도 없으면 1줄)을 `원래 글 + "\nimport java.util.Objects;"` 로 `replace` — 파일에 한 번(`replace` 는 첫 것이 이긴다). 다른 `*.Objects` 단일 import 가 있으면 건너뜀 글 「이름끼리 비교 — 다른 Objects import 와 충돌」
- 새 규칙은 `JavaRulesTest` 가 `Pos*.java` 양성 ≥1·`Neg*` 0 을 요구 — 픽스처 줄은 **파일 끝에 덧붙인다**(기존 줄 번호 불변 → `java.json`·`SecurityAstTest` 단언 줄이 안 흔들린다). `SecurityAstTest:33-35` 는 줄을 직접 적으니 새 줄을 더한다
- corpus 골든은 새 규칙·판정 변화로 많이 바뀐다 — **의도된 변화는 V-54a 가 한 번에 갱신하고 건수를 compare ㉔ 표에**. 5-6x 행 안에서는 corpus 골든을 안 만진다(빠른 시험만). 단위 시험은 행마다
- 레인 — 1-81·1-82·1-83·7-31(FieldRoles) 이 db·corpus · 7-30·7-31(템플릿·골든) java · 2-38 java(시험)·corpus(main) · 5-6x 전부 corpus · V-54 에서 `--corpus` → `--db`

## 3. 설계 결정 — 전부 [고정]

### 1-81 — CTAS NATURAL·USING 같은 이름 · 괄호 안 쉼표 · 시험 javadoc(§3-1)
- `DdlPull`:
```java
/** 1-81 — 괄호 안을 공백으로 비운 같은 길이 사본(따옴표 글은 ctasColumns 가 이미 비웠다). 깊이 0 의 구분자만 찾으려고 */
static String depthMask(String s) {
    char[] a = s.toCharArray();
    int depth = 0;
    for (int i = 0; i < a.length; i++) {
        if (a[i] == '(') {
            depth++;
        } else if (a[i] == ')') {
            depth = Math.max(0, depth - 1);
        } else if (depth > 0) {
            a[i] = ' ';
        }
    }
    return new String(a);
}
/** 1-81 — 공통 컬럼을 하나로 합치는 조인 — `*`·`t.*` 는 추측하지 않는다 */
private static final Pattern MERGE_JOIN = Pattern.compile("(?i)\\bNATURAL\\s+(?:\\w+\\s+)*JOIN\\b|\\bUSING\\s*\\(");
```
- `fromSources` — `JOIN_SEP.split(list)` 대신 사본에서 자리를 찾고 원문에서 자른다:
```java
Matcher sep = JOIN_SEP.matcher(depthMask(list));
int from = 0;
while (sep.find()) {
    if (!source(list.substring(from, sep.start()).trim(), tables, out)) { return Map.of(); }
    from = sep.end();
}
return source(list.substring(from).trim(), tables, out) ? out : Map.of();
```
  (`for … split` 루프 자리 — 복잡도 +1, 새 메서드 없음. 괄호 사본에서 `(K, D)` 는 `(    )` 라 쉼표가 안 보인다. `JOIN_SEP` 자체는 그대로)
- `ctasColumns:251-252` → `boolean merged = MERGE_JOIN.matcher(s.substring(from.end())).find(); … selected(…, src, merged)` · `selected(String list, Map<String, Draft> srcs, boolean merged)` 첫 줄에 `if (merged && (STAR.equals(list) || STAR_OF.matcher(list.trim()).matches())) { return List.of(); }` 뒤 항목 루프의 `t.*` 가지에도 `merged` 면 `return List.of()`. 명시 항목은 그대로(`USING (K)` 의 맨 이름 `K` 는 `resolve` 가 모호 → 빈 목록 — 추측 안 함, 한정 이름 `t.K` 는 풀린다)
- `DdlReaderTest.ctasFromSource` javadoc → `/** 1-71·1-80·1-81 — CTAS 는 단순 SELECT 만 컬럼: *·t.*·맨 이름(원본 전체에서 하나)·한정 이름·AS 별칭 · FROM 원본 여럿(쉼표·ANSI JOIN, ON·USING 괄호 안 쉼표 포함). 집계·식·서브쿼리·없는 원본·모호한 맨 이름·NATURAL·USING 의 *·t.* 는 이름만 + notes */` · 입력에 `CREATE TABLE TB AS SELECT t.A, s.D FROM T1 t JOIN T0 s USING (K, D); CREATE TABLE TC AS SELECT * FROM T1 NATURAL JOIN T0; CREATE TABLE TD AS SELECT t.* FROM T1 t JOIN T0 s USING (B); CREATE TABLE TE AS SELECT t.A FROM T1 t JOIN T0 s ON t.A IN (1, 2);`(T0 에 `K`·`D`·`B` 가 있다 — USING (K, D) 가 T1 에 없어도 우리는 안 본다) → TB `[A, D]`(**RED** — 지금 괄호 쉼표로 빈 Map) · TC 컬럼 0 + notes(**RED** — 지금 `[A, B, K, D, B]`) · TD 컬럼 0 + notes(**RED** — 지금 `[A, B]`) · TE `[A]`(**RED**) · 기존 T2~TA 단언 불변
- 골든 diff 0 기대(표본 DDL 에 CTAS 없음 — 바뀌면 중단). `DdlPull` 메서드 21 → 22

### 1-82 — `INLINE_COMMENT` 는 마스크에서 찾고 원문에서 자른다(§3-2)
- `DdlReader.item:367-368`:
```java
Matcher ic = INLINE_COMMENT.matcher(m); // 1-82 — 글 안 COMMENT '…' 는 주석이 아니다
String comment = ic.find() ? unq(rest.substring(ic.start(1), ic.end(1))) : null;
```
  (분기 불변 · `INLINE_COMMENT` 패턴 `'((?:[^']++|'')*+)'` 은 마스크 `'   '` 에도 맞는다 — 공백은 `[^']`)
- `DdlReaderTest.inlineCommentIgnoresQuotedText`(새): `CREATE TABLE t (a VARCHAR(20) DEFAULT 'x COMMENT ''y''' COMMENT 'real', b VARCHAR(5) DEFAULT 'COMMENT ''no''', c INT COMMENT 'it''s');` → a comment `real`(**RED** — 지금 `""`) · a defaultValue `'x COMMENT ''y'''`(음성 — 1-78 그대로) · b comment null(**RED**) · c comment `it's`(음성)
- 골든 diff 0 기대(표본 컬럼 COMMENT 에 글 안 `COMMENT '` 없음 — 바뀌면 중단). `self-check.txt:84` 불변

### 1-83 — CTAS 식·함수 컬럼 — 별칭 있으면 이름만, 형식 null + notes(§3-3)
- `DdlReader:122-123` `NAME_ONLY` 에 항목 `CTAS_EXPR = "ctasExpr"` → `"AS SELECT — 식·함수 컬럼은 이름만(형식 없음)"`(상수는 `CTAS`·`LIKE`·`TYPED` 옆, package-private)
- `DdlPull`:
```java
/** 1-83 — 식·함수 항목의 AS 별칭 — g1 식, g2 별칭(식 글자가 없는 낱말) */
private static final Pattern EXPR_ALIAS = Pattern.compile("(?i)^(.+?)\\s+AS\\s+([^\\s('\"+\\-*/|<>=,]+)$", Pattern.DOTALL);
```
  `selected` 항목 루프의 `Column c = ITEM_BAD… ? null : resolve(…)` 뒤:
```java
if (c == null) {
    Matcher ex = EXPR_ALIAS.matcher(item);
    if (!ex.matches() || !ITEM_BAD.matcher(ex.group(1)).find()) { // 별칭 없는 식·못 푼 맨 이름은 지금처럼 빈 목록
        return List.of();
    }
    out.add(new Column(DdlReader.unquote(ex.group(2)), 0, null, null, null, null, null, true, null, null, null));
    continue;
}
```
  (`ITEM_BAD.matcher(ex.group(1)).find()` — 식 쪽에 식 글자가 있어야 식이다. `A AS X` 처럼 맨 이름을 못 푼 것은 그대로 빈 목록)
- `pull:111-116`:
```java
List<Column> cols = ctasColumns(after, tables);
if (cols.isEmpty()) { return false; }
cols.forEach(c -> d.cols.add(at(c, d.cols.size() + 1, c.name())));
if (cols.stream().anyMatch(c -> c.nativeType() == null)) {
    d.notes.put(DdlReader.CTAS_EXPR, DdlReader.NAME_ONLY.get(DdlReader.CTAS_EXPR)); // 1-83
}
return true;
```
  `NAME_ONLY` 가 private 이면 package-private 로(선언 묶음 순서 확인 — 같은 private static final 묶음이면 `COL_REF` 아래 package-private 묶음으로 옮긴다, 1-78 과 같은 수법)
- `selected` 복잡도: 1-81 +2 · 1-83 +3 → 기존 ~9 에서 ~14. **넘으면 식 가지를 `exprColumn(String item) → Optional<Column>` 으로 뗀다**(메서드 23)
- 시험 `ctasFromSource` 입력에 `CREATE TABLE TF AS SELECT A, UPPER(B) AS UB, COUNT(*) AS N FROM T1 GROUP BY A; CREATE TABLE TG AS SELECT A, B + 1 FROM T1;` → TF `[A, UB, N]`(**RED** — 지금 0 + notes ctas) · TF UB·N `nativeType()` null, `nullable()` true · `r.notes().get("TF").get(DdlReader.CTAS_EXPR)` 있음 · `r.notes().get("TF").get(CTAS)` 없음 · TG 컬럼 0 + notes `ctas`(별칭 없음 — 음성) · T5(`COUNT(*)` 별칭 없음) 그대로 notes ctas
- `InsertGenTest`·`DdlGenTest`·`DtoGeneratorTest` 가 null 형식 컬럼에 안 넘어진다 — 전부 가드 있음(「맥락」). 골든 diff 0 기대

### 7-31 — List `<c:param>`·Detail hidden 의 날짜 PK 바인딩(§3-4)
- `FieldRoles`: `static final String K_BIND_PATTERN = "bindPattern";` · `dates` legacy 가지 `f.put(K_DATE_PATTERN, SHOW.get(type));` 아래 `f.put(K_BIND_PATTERN, PATTERN.get(type));`(한 줄, 분기 불변 — VO `@DateTimeFormat(pattern)` 과 같은 글)
- `templates/gen/egov35/List.jsp.ftl:5` → `<#if (listFields + pk)?filter(f -> f.datePattern??)?has_content>` · `:57` 의 `<#list pk as p>…</#list>` 안:
```
<#list pk as p><#if p.bindPattern??><c:param name="[=p.name]"><fmt:formatDate value="${result.[=p.name]}" pattern="[=p.bindPattern]"/></c:param><#else><c:param name="[=p.name]" value="${result.[=p.name]}"/></#if></#list>
```
- `Detail.jsp.ftl:3` → `<#if (fields + pk)?filter(f -> f.datePattern??)?has_content>`(pk ⊂ fields 라 결과 같음 — List 와 글을 맞춘다) · `:31`:
```
<#if p.bindPattern??>
    <input type="hidden" name="[=p.name]" value="<fmt:formatDate value='${result.[=p.name]}' pattern="[=p.bindPattern]"/>"/>
<#else>
    <input type="hidden" name="[=p.name]" value="<c:out value='${result.[=p.name]}'/>"/>
</#if>
```
- 골든(의도된 변화 — 둘뿐): `golden/gen/egov35/EmpHistList.jsp:46` `<c:param name="startDate"><fmt:formatDate value="${result.startDate}" pattern="yyyy-MM-dd'T'HH:mm"/></c:param>` · `EmpHistDetail.jsp:47` hidden. `-Dgolden.update=true -Dtest=GenTemplatesTest` 뒤 `git diff --stat src/test/resources/golden` 이 그 둘뿐인지 — egov4·egov5·bbs·`model*.json` 이 바뀌면 중단. `GenModelTest`: `legacyDates` 에 `bindPattern` 단언 한 줄(`NTCE_BGNDE` → `yyyy-MM-dd'T'HH:mm`, `javaTime "true"` 면 키 없음) — 모델 골든 `model.json`·`model-bbs.json` 은 VARS 가 legacy 가 아니라 불변
- TDD 예외(템플릿·골든) — `Ruling:` 한 줄. `GeneratorJavacTest` 40 불변(JSP 는 javac 밖)

### 7-30 — 오류 세트 옵션 `disabled`·`title` 단언(§3-5)
- `SmokeHtmlUnitTest.generatorBrokenSetIsDisabled(@TempDir Path tmp)`(새, `crudGeneratorRuns` 옆):
```java
AnalyzeRoutesTest.copy(Path.of("templates/gen"), tmp.resolve("templates/gen"));
Path broken = tmp.resolve("templates/gen/broken");
Files.createDirectories(broken);
Files.writeString(broken.resolve("set.yaml"), "name: broken\nvars:\n  javaTime: flase\nfiles:\n  - { template: A.java.ftl, path: 'x' }\n");
Path profiles = tmp.resolve("profiles");
Files.createDirectories(profiles);
Files.writeString(profiles.resolve("t.yaml"), "name: t\n");
Javalin own = App.start(new AppConfig(0, "t", tmp.resolve("data"), profiles, tmp.resolve("drivers"), false));
try (WebClient wc = client(true)) {
    HtmlPage page = wc.getPage("http://127.0.0.1:" + own.port() + "/tools/spring_source_generator.html");
    wc.waitForBackgroundJavaScript(3000);
    HtmlOption o = ((HtmlSelect) page.getElementById("set")).getOptionByValue("broken");
    assertEquals("broken (오류)", o.getText());
    assertTrue(o.isDisabled(), "7-27 — 고를 수 없다");
    assertTrue(o.getAttribute("title").contains("vars.javaTime") && o.getAttribute("title").contains("flase"), o.getAttribute("title"));
    assertFalse(((HtmlSelect) page.getElementById("set")).getOptionByValue("egov35").isDisabled());
} finally {
    own.stop();
}
```
  (`t.yaml` 최소 프로필이 `AppConfig` 에 안 맞으면 `crudGeneratorRuns:1126-1129` 의 프로필 글에서 `generator` 절만 빼고 쓴다. 저장소 `templates/gen` 은 안 건드린다)
- **RED 가 안 선다**(7-27 이 이미 됐다 — 단언 추가 행) → TDD 예외(시험만) `Ruling:` 한 줄. 검증은 이 시험 초록 + `GenerateRoutesTest` 그대로

### 2-38 — `dupIndex`·`fkTypes`·`fkNoIndex` detail 빈 이름은 공백 없이(§3-6)
- `StructureLint.java:463` → `("인덱스 " + b.name()).trim() + " ⊂ " + (a.name().isEmpty() ? "" : a.name()) + "(" + String.join(",", a.cols()) + ")"`(a 이름이 없으면 「⊂ (A,B)」 — `⊂ ` 뒤 공백 하나는 구분자) · `:401` → `("FK " + nz(fk.name())).trim() + " → " + …` · `:154` → `("FK " + nz(fk.name())).trim() + " → " + fk.refTable()`. 세 줄, 분기 불변(`?:` 하나는 `dupIndex` 메서드 — 기준선 밖, 복잡도 확인)
- `StructureLintTest`: dupIndex 에 이름 없는 인덱스(`new Index(null, List.of("B"), false)` 꼴 — 생성자는 `keyed:309·315` 가 `nz` 로 받는 그 형식) 하나 → 「D8.B 인덱스 ⊂ PK(B)」 하나(**RED** — 지금 「인덱스  ⊂」) · fkTypeMismatch 에 이름 없는 FK(`new ForeignKey(null, …)`) → 「C10.PN FK → P.ID: 길이 …」(**RED**) · fkNoIndex 이름 없는 FK → 「FK → P」(**RED**). 기존 기대 글 불변
- 골든 diff 0 기대(「맥락」 — 빈 이름 흔적 0). `DdlCorpusTest`·`HrSnapshotCorpusTest`·`pg-lint.json` 은 V-54 에서 확인, 바뀌면 중단

### 5-61a — `SecurityAst` XXE 넷 — dom4j·jdom · 변수 단위 · 필드+static 블록 · 변환기 DOM 전용(§3-7)
- 상수: `PARSERS = Set.of("SAXReader", "SAXBuilder")` · `EXPANDS = Set.of("setExpandEntityReferences", "setExpandEntities")`(`EXPAND` 대신, `guards:139-156` 의 비교를 `EXPANDS.contains`) · `TRANSFORMERS = Set.of("newTransformer", "newTransformerHandler")` · `STYLESHEETS = Set.of("newTemplates", "newXMLFilter", "getAssociatedStylesheet")` · `TRANSFORM = "transform"` · `DOM_SOURCE = "DOMSource"`
- `xmlFactories`:
```java
private static void xmlFactories(CompilationUnit cu, List<Taint.Hit> out) {
    for (MethodCallExpr c : cu.findAll(MethodCallExpr.class)) {
        if (anchor(c) && !emptyDocument(c) && !guarded(c, cu)) {
            out.add(new Taint.Hit(XXE, c));
        }
    }
    for (ObjectCreationExpr o : cu.findAll(ObjectCreationExpr.class)) { // 5-61a — dom4j SAXReader · jdom SAXBuilder
        if (PARSERS.contains(o.getType().getNameAsString()) && !guarded(o, cu)) {
            out.add(new Taint.Hit(XXE, o));
        }
    }
}
```
- `guarded(Expression anchor, CompilationUnit cu)`:
```java
/** 5-61a — 변수가 있으면 그 변수의 막음(같은 몸 또는 한 홉)만, 필드면 클래스 전체에서. 변수 없는 사슬은 몸 안 아무 막음(설계 48 그대로) */
private static boolean guarded(Expression anchor, CompilationUnit cu) {
    Node body = anchor.findAncestor(BodyDeclaration.class).map(Node.class::cast).orElse(null);
    if (body == null) {
        return true;
    }
    String v = varName(anchor);
    if (v == null) {
        return anyGuard(body);
    }
    if (body instanceof FieldDeclaration || !declaresLocal(body, v)) { // 필드 — static 블록·메서드 어디서든
        body = anchor.findAncestor(TypeDeclaration.class).map(Node.class::cast).orElse(body);
    }
    return guardsOn(body, v) || hopGuarded(body, v, cu) || domOnly(body, v, anchor);
}
private static boolean guardsOn(Node body, String v) {
    return body.findAll(MethodCallExpr.class).stream().anyMatch(m -> guards(m) && m.getScope().map(SecurityAst::lastName).filter(v::equals).isPresent());
}
private static boolean declaresLocal(Node body, String v) {
    return body.findAll(VariableDeclarator.class).stream().anyMatch(d -> d.getNameAsString().equals(v));
}
```
  `varName(Expression)` — 몸은 그대로(부모 `VariableDeclarator`·`AssignExpr` 만 본다, 형 변환·괄호 지나기 그대로). 필드 초기값 앵커의 부모는 `VariableDeclarator` 라 v = 필드 이름
- 변환기:
```java
/** 5-61a — 변환기가 메모리 DOM 만 쓴다: newTransformer()·newTransformerHandler() 무인자 · 스타일시트 호출 0 · transform 첫 인자 전부 DOMSource(new 또는 선언 형식) */
private static boolean domOnly(Node body, String v, Expression anchor) {
    if (!(anchor instanceof MethodCallExpr a) || !a.getScope().map(SecurityAst::lastName).filter(s -> s.endsWith("TransformerFactory")).isPresent()) {
        return false;
    }
    List<MethodCallExpr> calls = body.findAll(MethodCallExpr.class);
    boolean noArgs = calls.stream().filter(m -> TRANSFORMERS.contains(m.getNameAsString())).allMatch(m -> m.getArguments().isEmpty());
    boolean noSheet = calls.stream().noneMatch(m -> STYLESHEETS.contains(m.getNameAsString()));
    List<MethodCallExpr> transforms = calls.stream().filter(m -> TRANSFORM.equals(m.getNameAsString()) && m.getArguments().isNonEmpty()).toList();
    return noArgs && noSheet && !transforms.isEmpty() && transforms.stream().allMatch(m -> domSource(m.getArgument(0)));
}
private static boolean domSource(Expression e) {
    return e instanceof ObjectCreationExpr o && DOM_SOURCE.equals(o.getType().getNameAsString()) || DOM_SOURCE.equals(ValueAst.declaredType(e));
}
```
  (`v` 는 안 쓰면 매개변수에서 뺀다 — PMD UnusedFormalParameter). 메서드 24 → 28
- 픽스처 `security/pos.java` 끝(`:84` `}` 앞 또는 새 클래스 — **기존 줄 번호 불변**, 새 줄만 더한다): ① 한 메서드에 팩토리 둘 — `DocumentBuilderFactory g1 = DocumentBuilderFactory.newInstance(); g1.setFeature(DISALLOW, true); DocumentBuilderFactory g2 = DocumentBuilderFactory.newInstance();` → g2 줄만 xxe ② `static final DocumentBuilderFactory FF = DocumentBuilderFactory.newInstance();` 막음 없음 → xxe ③ `new org.dom4j.io.SAXReader()`·`new org.jdom2.input.SAXBuilder()` 막음 없음 → xxe 둘 ④ 변환기 — `TransformerFactory tf2 = TransformerFactory.newInstance(); tf2.newTransformer(new StreamSource(f))` → xxe(스타일시트 인자). `neg.java` 끝: ① 팩토리 둘 다 막음 ② `static final DocumentBuilderFactory NF = …; static { NF.setFeature(DISALLOW, true); }` ③ `SAXReader r = new SAXReader(); r.setFeature(DISALLOW, true);` · `SAXBuilder b = new SAXBuilder(); b.setExpandEntities(false);` ④ jeecg 꼴 — `DOMSource ds = new DOMSource(doc); TransformerFactory tf = TransformerFactory.newInstance(); Transformer t = tf.newTransformer(); t.transform(ds, new StreamResult(w));` ⑤ `tf.newTransformer(); t.transform(new DOMSource(doc), r)`
- `SecurityAstTest.positiveFixture` 줄 목록에 새 줄 넷+(① g2 ② FF ③ 둘 ④) 추가 · `negativeFixture` 0 그대로 · 새 `twoFactoriesGuardedSeparately`(RED — 지금 둘 다 조용) · `fieldGuardedInStaticBlock`(RED — 지금 지적) · `domOnlyTransformerIsSilent`(RED) · `dom4jJdomAreAnchors`(RED). `fixtures.json` 새 security 행만(-Dgolden.update 뒤 diff 가 security/pos.java 새 줄뿐)
- 표본 기대(V-54a): `check-jeecg.json` xxe 1 → 0(`OfficeHtmlUtil:112`) · 다른 표본 xxe 불변 · trustAll 불변 · egov-prev(CLI) 0. 그 밖 변화는 R-21 에서 판정

### 5-61b — `JavaDefects` 새 규칙 넷 + `staticDateFormat` 형식 + lostCause·threadLocal Minor(§3-8)
- `rules.yaml` `java.equalsLiteralFirst` 와 `java.parseError` 사이(주석 `# ---- 설계 58(2026-11-08) — JavaDefects·ValueAst 가 본다`):
```yaml
  - id: java.catchThrowable
    group: java
    severity: warn
    kind: java
    globs: ['*.java']
    message: catch (Throwable·Error) — OutOfMemoryError 같은 치명 오류까지 삼킨다
    fix: 'Exception 또는 구체 예외를 잡고, Throwable 은 다시 던진다'
    ref: 'Sonar S1181'
  - id: java.logAndThrow
    group: java
    severity: warn
    kind: java
    globs: ['*.java']
    message: catch 가 로그를 남기고 다시 던진다 — 같은 오류가 두 번 기록된다
    fix: '로그 또는 던지기 하나만 — 던지면 받는 쪽이 기록한다'
    ref: 'Sonar S2139'
  - id: java.mappingPrivate
    group: java
    severity: error
    kind: java
    globs: ['*.java']
    message: 'private·static 메서드의 @RequestMapping 류는 요청을 못 받는다'
    fix: 'public 인스턴스 메서드로'
    ref: 'Sonar S3751'
  - id: java.floatEquals
    group: java
    severity: warn
    kind: java
    globs: ['*.java']
    message: float·double 을 == 로 비교한다 — 반올림 오차로 어긋난다
    fix: 'Math.abs(a - b) < eps 또는 BigDecimal.compareTo'
    ref: 'p3c AvoidDoubleOrFloatEqualCompareRule'
```
  `java.staticDateFormat:881-888` message → `static SimpleDateFormat·DateFormat·DecimalFormat·MessageFormat 공유 — 스레드 안전하지 않아 값이 섞인다`(fix·ref 그대로). `java.txRollbackFor:917` message → `'@Transactional 에 rollbackFor(JTA 는 rollbackOn)가 없다 — 체크 예외에 롤백이 안 된다'`. fix ≤ 60자(`CheckRulesTest:175`)
- `JavaDefects` 상수: `CATCH_THROWABLE`·`LOG_AND_THROW`·`MAPPING_PRIVATE`·`FLOAT_EQUALS` · `DATE_FORMATS = Set.of("SimpleDateFormat", "DateFormat", "DecimalFormat", "MessageFormat", "NumberFormat", "ChoiceFormat")` · `BROAD_CATCH = Set.of("Throwable", "Error")` · `LOG_METHODS`·`LOGGER`(`JavaRules:78-79` 와 같은 글 — javadoc 「JavaRules 와 같다. 가시성을 열면 DeclarationOrder」) · `PRINT_STACK = "printStackTrace"`. `apply` 에 `if (on.contains(X)) { x(cu, out); }` 넷
- 판정:
```java
/** 5-61b — catch 매개변수 형식(union 포함)이 Throwable·Error 이고 몸에서 그 이름을 다시 던지지 않는다 */
private static void catchThrowables(CompilationUnit cu, List<Taint.Hit> out) {
    for (CatchClause cc : cu.findAll(CatchClause.class)) {
        Parameter p = cc.getParameter();
        List<Type> types = p.getType() instanceof UnionType u ? u.getElements() : List.of(p.getType());
        boolean broad = types.stream().map(JavaDefects::simpleType).anyMatch(BROAD_CATCH::contains);
        if (broad && cc.getBody().findAll(ThrowStmt.class).stream().noneMatch(s -> rethrows(s, p.getNameAsString(), cc.getBody()))) {
            add(out, CATCH_THROWABLE, p);
        }
    }
}
private static boolean rethrows(ThrowStmt s, String name, BlockStmt body) {
    return !barrier(path(s, body)) && s.getExpression() instanceof NameExpr n && n.getNameAsString().equals(name);
}
/** 5-61b — catch 몸(람다·안쪽 클래스 밖)에 로거 호출(error·warn·…, scope 가 log·logger 꼴) 또는 e.printStackTrace() 와 throw 가 같이 있다 — 첫 throw 자리 */
private static void logAndThrows(CompilationUnit cu, List<Taint.Hit> out) {
    for (CatchClause cc : cu.findAll(CatchClause.class)) {
        BlockStmt b = cc.getBody();
        boolean logged = b.findAll(MethodCallExpr.class).stream().anyMatch(m -> !barrier(path(m, b)) && logs(m, cc.getParameter().getNameAsString()));
        b.findAll(ThrowStmt.class).stream().filter(s -> !barrier(path(s, b))).findFirst()
                .filter(s -> logged).ifPresent(s -> add(out, LOG_AND_THROW, s));
    }
}
private static boolean logs(MethodCallExpr m, String param) {
    String name = m.getNameAsString();
    String scope = m.getScope().map(JavaDefects::lastName).orElse("");
    return LOG_METHODS.contains(name) && LOGGER.matcher(scope).matches() || PRINT_STACK.equals(name) && scope.equals(param);
}
/** 5-61b — private·static 메서드의 매핑 애너테이션(SpringMappings.ANNOTATIONS) */
private static void mappingPrivates(CompilationUnit cu, List<Taint.Hit> out) {
    for (MethodDeclaration m : cu.findAll(MethodDeclaration.class)) {
        if (m.isPrivate() || m.isStatic()) {
            m.getAnnotations().stream().filter(a -> SpringMappings.ANNOTATIONS.contains(lastName(a.getName()))).findFirst()
                    .ifPresent(a -> add(out, MAPPING_PRIVATE, a));
        }
    }
}
/** 5-61b — ==·!= 의 한쪽이 double 리터럴·float/double(래퍼 포함) 선언 형식·그 형 변환. 같은 글끼리(x != x — NaN 검사)·null 은 뺀다 */
private static void floatEquals(CompilationUnit cu, List<Taint.Hit> out) {
    for (BinaryExpr b : cu.findAll(BinaryExpr.class)) {
        boolean eq = b.getOperator() == BinaryExpr.Operator.EQUALS || b.getOperator() == BinaryExpr.Operator.NOT_EQUALS;
        if (eq && !b.getLeft().toString().equals(b.getRight().toString()) && !b.getLeft().isNullLiteralExpr() && !b.getRight().isNullLiteralExpr()
                && (floating(b.getLeft()) || floating(b.getRight()))) {
            add(out, FLOAT_EQUALS, b);
        }
    }
}
private static boolean floating(Expression e) {
    Expression x = e instanceof EnclosedExpr en ? en.getInner() : e;
    return x instanceof DoubleLiteralExpr || x instanceof CastExpr c && ValueAst.FLOATING.contains(simpleType(c.getType()))
            || ValueAst.FLOATING.contains(ValueAst.declaredType(x));
}
```
  `lastName(Name)` 오버로드가 없으면 `a.getName().getIdentifier()`. `ValueAst.FLOATING` 은 §2 대로 package-private(선언을 `BOXED` 앞으로). `JavaDefects` 17 → 24(`catchThrowables`·`rethrows`·`logAndThrows`·`logs`·`mappingPrivates`·`floatEquals`·`floating`), 각 복잡도 ≤ 15
- Minor: `barrier:127-129` 에 `CatchClause` 추가(중첩 catch 의 throw 는 안쪽 판정 — lostCause·catchThrowable·logAndThrow 셋이 같이 쓴다. `finallyJumps`·`finallyThrows` 도 `barrier` 를 쓰면 영향을 본다 — `caughtInside` 가 따로 있으니 finally 안 try/catch 의 throw 판정이 바뀌면 안 된다: **`barrier` 를 두 벌로 — `barrier`(그대로) · `catchBarrier`(+CatchClause) 를 lostCause·새 둘에만**) · `threadLocals:165-184` — 필드 이름이 어느 `MethodCallExpr` 의 **인자**(`NameExpr`)로 나오면 remove 로 친다(넘겨 지우는 꼴) · `lostCause` 의 `e::addSuppressed` 는 픽스처로 못박는다(`NegDefects` 끝 `catch (IOException e) { Runnable r = e::addSuppressed; throw new X("m", e); }` 는 cause 라 당연 음성 — 메서드 참조만 쓴 꼴 `catch (IOException e) { list.forEach(e::addSuppressed); throw new X("m"); }` 를 음성으로. 실행에서 `MethodReferenceExpr` scope 가 `NameExpr` 가 아니라 양성이 되면 `lostCause` 의 「썼다」 에 `MethodReferenceExpr` scope 를 더한다 — 분기 하나)
- 픽스처 `PosDefects.java` 끝(새 클래스 `PosDefects58`): catchThrowable 둘(`catch (Throwable t) { log(t); }` · `catch (Error | RuntimeException e) {}`) · logAndThrow 둘(`LOGGER.error("x", e); throw new RuntimeException(e);` · `e.printStackTrace(); throw e;`) · mappingPrivate 둘(`@RequestMapping private String a()` · `@GetMapping static String b()`) · floatEquals 셋(`d == 0.0` · `Double x; x != y` · `(double) i == j`) · staticDateFormat 둘(`static DecimalFormat`·`static final MessageFormat`) · 중첩 catch — 바깥 `catch (Exception e)` 가 안 쓰고, 안쪽 `catch (IOException io) { throw new X("m"); }` → 안쪽 throw 는 **lostCause 한 건**(안쪽 몫) · `NegDefects.java` 끝: `catch (Throwable t) { throw t; }` · `catch (Throwable t) { log(t); throw new X(t); }` 는 catchThrowable(새 X 로 던짐 — 다시 던지기 아님) **양성** 이라 Pos 로 · 로그만 · throw 만 · public `@RequestMapping` · `int == int` · `d != d` · `Double x == null` · `static final SimpleDateFormat` 아닌 인스턴스 필드 · ThreadLocal 넘겨 지우기(`clear(TL)`) · 메서드 참조 꼴
- 시험 `JavaDefectsTest`: `positiveFixture`·`negativeFixture` 는 골든·0 이라 그대로 돈다 · 새 `rethrowIsNotCatchThrowable`·`logOnlyOrThrowOnlyIsSilent`·`nanCheckIsNotFloatEquals`·`nestedCatchJudgedInside`·`threadLocalPassedToCleaner` — 다섯 RED → GREEN. `JavaRulesTest.fixturesGolden` RED(새 규칙 넷 「양성에서 한 번도 안 걸린 규칙」) → 픽스처 뒤 `-Dgolden.update=true -Dtest=JavaRulesTest` → `java.json` diff 가 새 줄(Pos 끝 클래스)뿐 · `CheckRulesTest:167` 132 → **136**(`:168` 45 그대로)
- `SelfCheckTest.mainMatchesBaseline` — 새 키 0 기대(「맥락」). 새 키가 나오면 ≤ 5 자리는 우리 코드를 고친다(5-60c 전례, 같은 커밋), 넘으면 기준선 갱신 + 이력에 키 목록

### 5-61c — `ValueAst` 다섯 + `TxAst` 둘(§3-9)
- `ValueAst`:
```java
/** 5-61c — 같은 파일 어느 클래스든 static final 필드 x 의 선언자 */
private static Optional<VariableDeclarator> staticFinal(Node from, String x) {
    return from.findCompilationUnit().stream().flatMap(cu -> cu.findAll(FieldDeclaration.class).stream())
            .filter(f -> f.isStatic() && f.isFinal())
            .flatMap(f -> f.getVariables().stream()).filter(v -> v.getNameAsString().equals(x)).findFirst();
}
```
  ① `returnsLiteral(Statement t)` → `return` 식이 `LiteralExpr` **또는** `NameExpr` 로 `staticFinal` 이 있는 것(상수 반환 빠른 길 — commons-lang `BooleanUtils:741`) ② `identityShortcut` — OR 부모를 **연속으로** 올라가며(`while (p instanceof BinaryExpr o && OR)`) 각 단계의 반대쪽을 모아 `findAll(MethodCallExpr)` 검사(세 항 OR) · 그 앞에 **표지 객체**: 양쪽 중 하나가 `NameExpr`/`this.x` 이고 `staticFinal` 선언자의 초기화식이 `ObjectCreationExpr` 이면 true(struts 표지 비교) — 이 가지는 `!=` 도 받는다(`b.getOperator() != EQUALS → return false` 보다 앞에) ③ `literalFirst:253-254` — 인자가 `StringLiteralExpr` **또는** `NameExpr` 로 `staticFinal` 선언자의 형식이 `String` 이고 초기화식이 `StringLiteralExpr`(상수 앞세우기 — `ValueFix.literalFirst` 는 `slice` 라 그대로 `CONST.equals(x)` 를 낸다) ④ `formatSlot` — scope 마지막 이름이 `String` 이면 false(`String.format`·`String.valueOf` 는 날짜 자리가 아니다, `formatted` 는 scope 가 글이라 영향 없음) ⑤ `declaredType:182-184` — `ClassOrInterfaceDeclaration` 대신 `TypeDeclaration`(enum·record 포함) 에서 `getFieldByName`, `RecordDeclaration` 이면 `getParameters()` 도 `param(…)` 으로. `constantLiteral` 은 `staticFinal(cu, x)` 로 선언자를 받아 형식·초기화만 본다(중복 제거). 28 → 29, 바뀐 메서드 복잡도 ≤ 15(`identityShortcut` 이 넘으면 표지 가지를 `sentinel(BinaryExpr) → boolean` 으로 — 30)
- `TxAst.rollbacks:93-94` — `ROLLBACK_KEYS` 키가 있어도 값이 빈 `ArrayInitializerExpr`(`rollbackFor = {}`)이면 없는 것(`pairs` 값으로 판정, 분기 +1). JTA 는 글만(5-61b rules.yaml)
- 픽스처: `PosValue.java` 끝 — `static final Object MARK = new Object();` 를 쓰지 않는 **양성** 비교 `Integer p == q`(기존과 겹치면 생략) · `PosLiteral.java` 끝 `x.equals(CONST)`(`static final String CONST = "a"`) 양성 · `NegValue.java` 끝 — `if (a == b) return TRUE_CONST;`(상수 반환) · `a == b || x || a.equals(b)`(세 항) · `s == MARK`(표지) · `String.format("YYYY-MM", v)`(weekYear 음성) · enum 안 `Integer` 필드 `==`(**양성** — `PosValue` 로: enum·record 필드 형식이 보여야 지적된다 — `enum E { A; Integer n; boolean f(Integer o) { return n == o; } }`·`record R(Integer n) { boolean f(Integer o) { return n == o; } }`) · `NegLiteral.java` 끝 `x.equals(NOT_STRING)`(`static final Object`)·`x.equals(local)` 음성 · `NegTx.java:13` 옆이 아니라 끝에 `@Transactional(rollbackFor = {})` **양성** → `PosTx.java` 끝으로
- 시험 `ValueAstTest`: `constantReturnIsShortcut`·`threeTermOrIsShortcut`·`sentinelObjectIsSilent`·`stringFormatIsNotDateSlot`·`enumAndRecordFieldsResolve`·`constantArgIsLiteralFirst` 여섯 RED → GREEN · `TxAstTest.emptyRollbackForIsMissing` RED → GREEN · `java.json` 갱신(새 줄만) · `CleanupTest.valueStage` — `fixtures/cleanup/value/Pos.java` 끝에 `x.equals(CONST)` 한 줄 → 골든 `value-Pos.java.txt` 에 `CONST.equals(x)`(의도된 변화, `steps.json` value 건수 +1)
- 표본 기대: commons refEquals 1 → 0 · struts(CLI) refEquals 1 → 0 · equalsLiteralFirst 는 상수 인자만큼 **늘 수 있다**(R-21 에서 스물 읽기) · weekYear 변화 0 기대

### 5-62a — `ResourceAst` 넷 — `Files.*` 스트림 · 빌린 것 셋 · 지역 래퍼 사슬(§3-10)
- 상수: `STREAM_TYPES = Set.of("Stream", "DirectoryStream")` · `FILES = "Files"` · `FILES_STREAMS = Set.of("lines", "walk", "list", "find", "newDirectoryStream")` · `SERVLET_TYPES` → `OWNER_TYPES = List.of("Request", "Response", "Context", "Template", "Session")`(접미) · `SERVLET_GETTERS` → `OWNER_GETTERS = Set.of("getOutputStream", "getWriter", "getInputStream", "getReader", "getConnection")` · `OWNER_CALLS = Set.of("getSqlSession", "getSqlSessionTemplate", "getSession", "getRequest", "getResponse", "getContext")` · `KNOWN_PACKAGES` 에 `java.nio.file`·`java.util.stream`
```java
/** 5-62a — Files.lines·walk·list·find·newDirectoryStream 가 준 Stream·DirectoryStream 만 자원(다른 Stream 은 닫을 것 없음) */
private static boolean filesStream(String type, Expression init) {
    return STREAM_TYPES.contains(type) && strip(init) instanceof MethodCallExpr m && FILES_STREAMS.contains(m.getNameAsString())
            && m.getScope().map(ResourceAst::lastName).filter(FILES::equals).isPresent();
}
```
  `candidate:215-227` — `isResource(type, cu)` 자리를 `isResource(type, cu) || STREAM_TYPES.contains(type)` 로 열고, 초기화/첫 대입식 `creation(init, cu)` 자리를 `STREAM_TYPES.contains(type) ? filesStream(type, init) : creation(init, cu)` 로. `Planner.resourceAt:409-410·419·423` 도 같은 두 치환(`creation` 호출 둘). `closeException` — `DirectoryStream` → `IOException`, `Stream` → `""`
- `servletOwned` → `ownerOwned(MethodCallExpr m)`(이름 바꿈, 몸 확장):
```java
/** 빌린 것 — 요청·응답·문맥·템플릿·세션(선언 형식 접미 또는 이름)이나 그런 것을 돌려주는 호출(getSqlSession() 류)의 getter 는 소유가 아니다(5-62a) */
private static boolean ownerOwned(MethodCallExpr m) {
    if (!OWNER_GETTERS.contains(m.getNameAsString())) {
        return false;
    }
    Expression scope = m.getScope().map(ResourceAst::strip).orElse(null);
    if (scope instanceof MethodCallExpr c) {
        return OWNER_CALLS.contains(c.getNameAsString());
    }
    if (!(scope instanceof NameExpr s)) {
        return false;
    }
    String type = ValueAst.declaredType(s);
    return SERVLET_OWNERS.contains(s.getNameAsString()) || OWNER_TYPES.stream().anyMatch(type::endsWith);
}
```
  (`DataSource ds; ds.getConnection()` — 형식 `DataSource` 는 접미 밖 → 소유 그대로 · `HttpURLConnection c; c.getInputStream()` 소유 그대로(roller 참 12) · `SqlSession s; s.getConnection()` 빌림 · `getSqlSession().getConnection()` 빌림)
- `borrowedName:193-195` — 지역이면 그 선언자의 초기화식을 한 홉 따라간다:
```java
if (local) {
    Optional<Expression> init = enclosingBody(at).flatMap(b -> b.findAll(VariableDeclarator.class).stream()
            .filter(d -> d.getNameAsString().equals(x)).findFirst()).flatMap(VariableDeclarator::getInitializer);
    return init.map(i -> borrowed(i, at, cu)).orElse(MEMORY.contains(type)); // 5-62a — 지역 래퍼 사슬(jeecg StreamGobbler)
}
return MEMORY.contains(type) || isResource(type, cu);
```
  (`InputStream a = this.in; new BufferedReader(new InputStreamReader(a))` → `a` 의 초기화식 `this.in` 은 필드 → 빌림. `InputStream a = new FileInputStream(f); new BufferedReader(…(a))` → `a` 초기화식이 소유 `new` → `borrowed` false → 래퍼도 후보(래퍼 변수는 따로 후보 — 기존 주석 `:264` 그대로). 순환 없음 — 선언자 초기화식은 자기 이름을 못 쓴다)
- 바깥 메서드 28 → 29. `borrowedName`·`ownerOwned`·`candidate` 복잡도 ≤ 15 확인
- 픽스처: `security/pos.java` `SecLeak` 아래 새 클래스 `SecLeak58` — `Stream<Path> s = Files.list(dir); s.count();`(양성) · `DirectoryStream<Path> ds = Files.newDirectoryStream(dir);`(양성) · `InputStream a = new FileInputStream(f); BufferedReader br = new BufferedReader(new InputStreamReader(a));`(둘 다 양성 — 사슬이 소유로 끝난다) · `security/neg.java` 끝 — `Stream<String> st = list.stream();`(자원 아님) · `try (Stream<Path> s = Files.walk(d)) {}` · `Connection c = getSqlSession().getConnection();` · `SqlSession ss; Connection c2 = ss.getConnection();` · `void f(AtomRequest areq) { InputStream in = areq.getInputStream(); }` · `void g(TemplateContext ctx) { Writer w = ctx.getWriter(); }` · `InputStream a = this.in; BufferedReader br = new BufferedReader(new InputStreamReader(a));`(사슬이 필드로 끝남) · `InputStream b = param; Reader r = new InputStreamReader(b);`
- 시험 `ResourceAstTest`: `leakFixture` 줄 목록에 새 양성 넷 · `negativeFixture` 0 · 새 `filesStreamIsResource`·`ownerGettersAreBorrowed`·`localWrapperChainFollowsInitializer` 셋 RED → GREEN · `fixtures.json` 갱신(새 줄만)
- 표본 기대: egov resourceLeak 4 → 2 · jeecg 40 → 39 · roller 65 → 63 · struts(CLI) 10 → 8 · `Files.*` 미해제가 표본에 있으면 **늘 수 있다**(R-21 읽기)

### 5-62b — twr 꼴 C(try 몸 끝 close, finally 없음) + `ResourceFix`(§3-11)
- `Twr(TryStmt tryStmt, List<Res> resources, List<Statement> closes)` — finally 꼴은 `List.of()`. `plan` 호출자(`ResourceFix.edit`) 외 영향 없음
- `Planner.of:310-318`:
```java
if (t.getResources().isNonEmpty() || !(t.getParentNode().orElse(null) instanceof BlockStmt blk)) {
    return Optional.empty();
}
List<Statement> tail = t.getFinallyBlock().isPresent() ? List.of() : tailCloses(t); // 5-62b — 꼴 C: finally 없음, try 몸 끝 close 들
Optional<List<String>> names = t.getFinallyBlock().map(Planner::closeNames).orElseGet(() -> tailNames(tail));
if (names.isEmpty()) {
    return Optional.empty();
}
… (루프 그대로) …
return t.getTryBlock().getStatements().size() > b.size() + tail.size() && assignsFirst(t, b) ? Optional.of(new Twr(t, a, tail)) : Optional.empty();
```
```java
/** 꼴 C — try 몸 **뒤에서부터** close 꼴(closeName) 인 문장들(1~3). catch 가 있어야 한다(없으면 finally 도 없는 try 는 구문 오류) */
private static List<Statement> tailCloses(TryStmt t) {
    List<Statement> body = t.getTryBlock().getStatements();
    List<Statement> out = new ArrayList<>();
    for (int i = body.size() - 1; i >= 0 && out.size() < MAX_NAMES && closeName(body.get(i)).isPresent(); i--) {
        out.add(0, body.get(i));
    }
    return t.getCatchClauses().isEmpty() ? List.of() : out;
}
private static Optional<List<String>> tailNames(List<Statement> tail) {
    Set<String> names = new LinkedHashSet<>();
    tail.forEach(s -> closeName(s).ifPresent(names::add));
    return names.isEmpty() || names.size() != tail.size() ? Optional.empty() : Optional.of(List.copyOf(names));
}
```
  `resourceAt` 은 그대로 — 꼴 A 는 `catch` 없음 조건이라 꼴 C(catch 필수)에서 자연히 빠지고 꼴 B 만 남는다(`T x = null; try { x = new …; …; x.close(); } catch (…) {…}`). `usedOutside` 의 catch 안 사용 검사 그대로(catch 에서 `x.close()` 하는 꼴은 탈락 — 다른 모양). 꼴 C 에서는 try 몸의 `x.close()` 가 release 로 **안** 잡혀 `resourceLeak` 도 같은 변수에 난다(지금과 같다) — 고치면 둘 다 사라진다(`TOUCHED_MAY_VANISH` RESOURCE `*`, 선언 줄이 지워진다). `Planner` 15 → 17
- `ResourceFix.edit`:
```java
boolean fin = t.getFinallyBlock().isPresent();
int finOpen = fin ? line(t.getFinallyBlock().get()) : 0;
int finClose = fin ? t.getFinallyBlock().get().getEnd().map(x -> x.line).orElse(0) : 0;
boolean shape = TRY_OPEN.matcher(lines.get(tryLine - 1)).matches()
        && (!fin || FINALLY_OPEN.matcher(lines.get(finOpen - 1)).matches() && CLOSE_BRACE.matcher(lines.get(finClose - 1)).matches())
        && p.closes().stream().allMatch(s -> alone(s, lines)); // 5-62b — 꼴 C 의 close 줄은 혼자 있어야
… (자원 루프 그대로) …
p.closes().forEach(s -> deletes.add(line(s)));
for (int k = finOpen + 1; k <= finClose; k++) { deletes.add(k); } // fin 이 아니면 0 번 돈다
return Optional.of(new Edit(deletes, head, finOpen, fin ? indent(lines.get(finOpen - 1)) + "}" : ""));
```
  `apply:75` → `if (edit.get().finallyLine() > 0) { e.replace(…); }`. `alone(Statement, lines)` 이 `ExpressionStmt` 전용이면 `Node` 로 넓힌다. `edit` 복잡도가 15 를 넘으면 꼴 검사 셋을 `shapeOk(Twr, lines, fin) → boolean` 으로 뗀다(ResourceFix 9 → 10)
- 픽스처 `PosResource.java` 끝 — 꼴 C: `InputStream is = null; try { is = new FileInputStream(p); return is.read(); } catch (IOException e) { return -1; }` 는 **아니다**(close 없음 → resourceLeak 만) · `InputStream is = null; try { is = new FileInputStream(p); int n = is.read(); is.close(); return n; } catch (IOException e) { return -1; }` → twr 양성(try 줄) · 둘짜리 `in`·`out` 끝 close 둘 → 양성 · `NegResource.java` 끝 — close 가 마지막이 아닌 꼴(`is.close(); log();`) · finally 도 있는 꼴은 기존 판정 · close 뒤 변수 사용(`is.close(); is = null;` — `usedOutside` 의 try 안 재대입) · `fixtures/cleanup/resource/Pos.java` 끝 `formC` 메서드(위 양성 첫째 꼴, 줄 꼴 다섯) → 골든 `resource-Pos.java.txt` 에 `try (InputStream is = new FileInputStream(p)) { int n = is.read(); return n; } catch …` · `steps.json` resource 건수(+1)
- 시험 `ResourceAstTest.tryWithResourcesFixture` 줄 추가 · 새 `tailCloseIsTwr`·`tailCloseNotLastIsNotTwr` RED → GREEN · `CleanupTest.resourceStage`(skips 글 그대로)·`fixturesGolden`·`valueAndResourceCompile`(javac 왕복 — 꼴 C 결과가 컴파일돼야 한다) · `CleanupRoutesTest.rulesListsFourteen`·`SmokeHtmlUnitTest:1801` 14·12 불변(규칙 수 그대로)
- 표본 기대: tryWithResources jeecg 3 → 20 안팎(R-15 「close 가 try 몸에만」 참 19 가운데 꼴 B 인 것) · jeesite·zheng·roller 도 늘고 resourceLeak 은 그대로(같은 자리 둘) · `cleanup-roller.json` resource 1 → 늘어남 · `CleanupCompileCorpusTest`(commons — resourceLeak 0) 영향 없음. **고친 꼴이 컴파일되는지는 `valueAndResourceCompile` 이 잰다**

### 5-62c — `ValueFix` — `Objects` import 끼우기 + 상수 앞세우기 고침(§3-12)
- `Cleanup.Edits.touchedNew:960-972` — 바꾼 글의 줄 수만큼:
```java
} else if (repl.get(i + 1) != null) {
    for (int k = 0; k < repl.get(i + 1).split("\n", -1).length; k++) { // 5-62c — 끼운 줄도 고친 줄
        out.add(++n);
    }
}
```
  (`result()` 는 `String.join("\n")` 이라 그대로 늘어난다. `Cleanup` 메서드 29 그대로, 클래스 길이 1000 안)
- `ValueFix.refEquals:118-121`:
```java
if (!hasObjectsImport(cu)) {
    if (conflictingObjectsImport(cu)) {
        skip(skips, no, "이름끼리 비교 — 다른 Objects import 와 충돌");
        return Optional.empty();
    }
    e.replace(importLine(cu), lines[importLine(cu) - 1] + "\nimport java.util.Objects;"); // 5-62c — 파일에 한 번(첫 replace 가 이긴다)
}
return Optional.of(not + "Objects.equals(" + …);
```
  `refEquals`·`rewrite` 에 `Edits e`·`String[] lines` 를 넘긴다(`apply:63` 에서). `hasObjectsImport:159-171` 을 둘로 — `hasObjectsImport`(`java.util.Objects` 있음)·`conflictingObjectsImport`(다른 `*.Objects` 단일 import) · `importLine(cu)` = 마지막 `ImportDeclaration` 의 끝 줄, 없으면 `package` 줄, 그것도 없으면 1(그때는 `"import java.util.Objects;\n" + lines[0]`). ValueFix 11 → 13
- 상수 앞세우기 고침은 5-61c 의 `literalFirst` 판정이 `NameExpr` 인자를 Hit 로 내면 `ValueFix.literalFirst:137-139` 가 `slice` 로 그대로 `CONST.equals(x)` 를 낸다 — 코드 변화 없음, `CleanupTest` 골든만(5-61c 에서 이미)
- 픽스처 `fixtures/cleanup/value/Pos.java` 끝 — import 없는 두 번째 클래스는 못 둔다(한 파일) → **새 픽스처 폴더 없이** `Pos.java` 에 이름끼리 `==` 한 줄(`Integer`) 을 더하고 `Pos.java` 의 import 목록에 `java.util.Objects` 가 **없음**을 확인(있으면 그 줄을 지우고 기존 `Objects.equals` 사용처가 없는지 — 있으면 새 파일 `fixtures/cleanup/value/Pos2.java` + 골든 `value-Pos2.java.txt`) → 골든에 `import java.util.Objects;` 줄 + `Objects.equals(a, b)` · `steps.json` value +1 · `valueAndResourceCompile` 초록(끼운 import 가 컴파일된다)
- 시험 `CleanupTest.objectsImportInserted`(RED — 지금 skip) · `touchedNewCountsInsertedLines`(`Edits` 단위 — `replace(2, "a\nb")` 뒤 `touchedNew` 가 `{2, 3}`) RED → GREEN
- 표본 기대: `cleanup-commons.json` `"value 이름끼리 비교 — java.util.Objects import 필요"` 1 → 0 · value 4 → 5 · `CleanupCompileCorpusTest` 초록(commons 에 끼운 import 가 컴파일)

### V-54a — corpus 골든 재채점(§3-13)
- 순서: `bash scripts/mvn.sh -q -DskipTests verify`(PMD·SpotBugs·Checkstyle — rc 0 뒤에만) → `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='CheckCorpusTest,LegacyCorpusTest,VcsCorpusTest,CliCorpusTest,JulietCorpusTest,SecurityBenchmarkCorpusTest,SqlRuleCasesCorpusTest,SqlFixtureCorpusTest,CleanupCorpusTest,CleanupCompileCorpusTest' -Djunit.timeout=10m -Dgolden.update=true`(RAM 감시 — projectshop 세션이 큰 작업 중이면 미룬다) → `git diff --stat src/test/resources/golden/corpus`
- 기대 변화(이것만): `check-{egov,commons,jspwiki,roller,egov-react,jeecg,jeesite,zheng,ruoyi-classic}.json` 의 새 규칙 넷 행 · `security.xxe`(jeecg 1 → 0) · `security.resourceLeak`(egov −2 · jeecg −1 · roller −2 ± `Files.*`) · `java.tryWithResources`(늘어남) · `java.refEquals`(commons −1) · `java.equalsLiteralFirst`(상수 인자만큼) · `java.staticDateFormat`(≤ +1 파일) · `java.lostCause`(중첩 catch 만큼 **줄 수 있다**) · `java.threadLocalRemove`(넘겨 지우기만큼 줄 수 있다) · `-hits.txt`(파일 목록) · `vcs-egov.json` `fullFindings` · `cleanup-{egov,commons,roller,…}.json` value·resource 건수·skips · `security-benchmark.json` `otherRules` 만(채점 칸 불변) · `security-juliet*` 불변(채점 칸) · `sqlrules`·`sqltables`·`sqlfixture`·`analyze-*`·`ddl-*`·`hr-lint`·`gen-hr` **불변**(바뀌면 중단)
- 벽시계 열을 이력에(⑯ 482초 기준)

### R-21 — 손 판정(§3-14)
- CLI(`toolbox.bat check <표본> --groups java,security --json --profile example`, data 는 scratch) jeecg·jeesite·zheng·ruoyi-classic·egov·commons-lang·roller·shopizer·struts·egov-prev. 읽을 줄: `catchThrowable` 표본마다 ≤ 10 · `logAndThrow` ≤ 10 · `mappingPrivate` 전부 · `floatEquals` 전부(≤ 20) · `staticDateFormat` 새 것 전부 · `equalsLiteralFirst` 새로 생긴 것 ≤ 20 · `tryWithResources` 새로 생긴 것 ≤ 20(꼴 C 가 맞나 — close 가 마지막, catch 있음) · `resourceLeak` 사라진 것 전부(egov 2·jeecg 1·roller 2·struts 2 가 맞나) · `xxe` 사라진 것(jeecg 1) · `refEquals` 사라진 것(commons 1·struts 1) · `lostCause`·`threadLocalRemove` 사라진 것 전부. 판정 참·참(규칙 정의)·오탐 → 고침·오탐(남김). 결함 둘 이하는 고침(사다리 ① — 음성 픽스처 먼저 RED), 넘으면 새 행. 코드 본문은 안 옮긴다(파일:줄 만)
- 고침이 있으면 V-54a 를 다시(바뀐 골든만 — 같은 명령)

### V-54 — compare ㉔ · 정적 분석 · `--corpus` · `--db`(§3-15)
- `design/compare-2026-10-28.md` 끝 `## ㉔ 재측정 — 번들 74(2026-11-08)`: 「설계 58. 올라간 후보 — ㉓ 「후보로」 의 `INLINE_COMMENT`·CTAS 식 컬럼·`<c:param>` Date PK·`dupIndex`·`fkTypes` 빈 이름 · 1-81·7-30 · ⑭ 「후보로」 넷(변환기·팩토리 둘·필드+static·dom4j·jdom) · ⑮ 「후보로」(S3751·`DecimalFormat`·`Throwable`·중복 로그·float·refEquals 빠른 길 둘·리뷰 Minor 일곱) · ⑯ 「후보로」(꼴 C 고침·`Files.*`·빌린 것 셋·래퍼 사슬·`Objects` import·상수 앞세우기)」 · **뺀 것** — `getAcceptedIssuers`·`SSLContext.init`·`propagation`·`Integer` 캐시(결함 아님·이미 지적) · 라-1·9·12(코드 정리 B — 후보로 남김) · 개발자 도구 새 탭(사용자) · **새 규칙 넷 × 표본 13 표** + 바뀐 규칙 × 표본 표(xxe·resourceLeak·tryWithResources·refEquals·equalsLiteralFirst·staticDateFormat·lostCause·threadLocalRemove 전/후) + 정리 표(value·resource) + 시험 시간 + 측정 표(1-81~2-38 전/후) + 「골든」(의도된 변화 — `gen/egov35` 둘 · `cleanup/*` · `check/java.json`·`fixtures.json` · corpus 파일 목록) + 정적 분석·벽시계(㉓ 대비) + 「드러난 것」 + 「후보로」 + `### 손 판정 — 새 규칙·바뀐 판정(R-21)` 표
- 순서(따로 낸다): `bash scripts/mvn.sh -DskipTests verify` rc 0 → `bash scripts/verify.sh` → `bash scripts/verify.sh --corpus` → **projectshop-c0 확인**(`docker ps` 에 shop-*·그 세션 컨테이너가 있으면 사용자에게 묻고 기다린다 — `wsl --shutdown` 은 내가 내지 않는다) → 사용자가 Docker 를 정리해 주면 여유 RAM 확인(**이 PC 기준 12GB 안팎이면 간다** — 번들 73 실측 12.1GB 출발 989초 초록·최저 3.7GB, 10GB 아래면 사용자에게 앱 내리기 요청) → 다른 JVM 없이 `bash scripts/verify.sh --db` 혼자
- 골든: 1-81~2-38 은 `gen/egov35` 둘 밖 diff 0 · 5-6x 는 V-54a 목록 밖 diff 0 — 바뀌면 중단

## 4. 실행 스텝
### S0 — 설계 문서·행·번들 표(**실행 세션(Opus)이 첫 손으로 한다** — 사용자 2026-11-08)
- 대상: `design/58-bundle74.md`(A 절) · `PLAN.md` 「번들」 표 「번들 74」 행 + 분할표 새 행(`1-82`·`1-83`·`7-31`·`2-38`·`5-61a`·`5-61b`·`5-61c`·`5-62a`·`5-62b`·`5-62c`·`V-54a`·`R-21`·`V-54`) + 1-81·7-30 행 상태(B 절) · `PROGRESS.md`(C 절).
- 검증: `bash scripts/doc-lint.sh design/58-bundle74.md` · `bash scripts/verify.sh`(docs 레인) · `git commit -m "docs: S0 설계 58 — 번들 74"`. push 안 함.

### 1-81 — CTAS NATURAL·USING·괄호 쉼표 + javadoc
- 대상: `core/gen/DdlPull.java`(`depthMask`·`MERGE_JOIN`·`fromSources`·`ctasColumns` 한 줄·`selected` 시그니처+가지) · `DdlReaderTest.ctasFromSource`(javadoc + TB·TC·TD·TE)(§3-1).
- 순서: 시험 RED(TB·TC·TD·TE) → 구현 GREEN.
- 검증: `bash scripts/mvn.sh -q test -Dtest='DdlReaderTest,InsertGenTest,DdlGenTest,SelfCheckTest,ArchitectureTest'` 초록 · `bash scripts/mvn.sh -q -DskipTests verify` rc 0(PMD) · `git status` 골든 변화 없음 · `bash scripts/verify.sh` 초록 · 커밋 `fix: 1-81 CTAS NATURAL·USING 은 *·t.* 추측 안 함 + 괄호 깊이 0 에서만 조인 분리`.
- 의존: S0.

### 1-82 — `INLINE_COMMENT` 마스크
- 대상: `core/gen/DdlReader.java`(`item:367-368` 두 줄) · `DdlReaderTest.inlineCommentIgnoresQuotedText`(§3-2).
- 순서: 시험 RED → GREEN.
- 검증: `-Dtest='DdlReaderTest,SelfCheckTest'` 초록(`self-check.txt:84` 불변) · `mvn.sh -q -DskipTests verify` rc 0 · 골든 diff 0 · `verify.sh` 초록 · 커밋 `fix: 1-82 글 안 COMMENT '…' 는 컬럼 주석이 아니다 — 마스크에서 찾고 원문에서 자른다`.
- 의존: 1-81(독립 — 차례만).

### 1-83 — CTAS 식·함수 컬럼
- 대상: `core/gen/DdlReader.java`(`NAME_ONLY` 항목·`CTAS_EXPR` 상수·가시성) · `core/gen/DdlPull.java`(`EXPR_ALIAS`·`selected` 식 가지·`pull` notes) · `DdlReaderTest.ctasFromSource`(TF·TG)(§3-3).
- 순서: 시험 RED(TF) → GREEN.
- 검증: `-Dtest='DdlReaderTest,InsertGenTest,DdlGenTest,DtoGeneratorTest,SelfCheckTest'` 초록(`selected` 복잡도 ≤ 15·`DdlPull` ≤ 30·`DdlReader` 30 그대로) · `mvn.sh -q -DskipTests verify` rc 0 · 골든 diff 0 · `verify.sh` 초록 · 커밋 `feat: 1-83 CTAS 식·함수 컬럼 — AS 별칭이 있으면 이름만(형식 null) + notes ctasExpr`.
- 의존: 1-81.

### 7-31 — 날짜 PK 바인딩
- 대상: `core/gen/FieldRoles.java`(`K_BIND_PATTERN`·`dates` 한 줄) · `templates/gen/egov35/List.jsp.ftl`·`Detail.jsp.ftl` · 골든 `golden/gen/egov35/EmpHistList.jsp`·`EmpHistDetail.jsp` · `GenModelTest.legacyDates` 단언 한 줄(§3-4).
- 순서: `GenModelTest` 단언 RED → `FieldRoles` → 템플릿 → `-Dtest=GenTemplatesTest` RED(egov35 둘) → `-Dgolden.update=true -Dtest=GenTemplatesTest` → `git diff --stat src/test/resources/golden` 이 egov35 둘뿐(아니면 중단) → diff 본문이 `<c:param>` 줄·hidden 줄뿐.
- 검증: `-Dtest='GenTemplatesTest,GenModelTest,GeneratorTest,GeneratorJavacTest,SelfCheckTest'` 초록 · `mvn.sh -q -DskipTests verify` rc 0 · `verify.sh` 초록 · 커밋 `fix: 7-31 egov35 날짜 PK — List c:param·Detail hidden 을 fmt:formatDate 로 VO 패턴과 맞춘다`. TDD 예외(템플릿·골든) `Ruling:`.
- 의존: S0.

### 7-30 — 오류 세트 옵션 단언
- 대상: `SmokeHtmlUnitTest.generatorBrokenSetIsDisabled`(§3-5).
- 검증: `-Dtest='SmokeHtmlUnitTest#generatorBrokenSetIsDisabled,GenerateRoutesTest'` 초록 · `verify.sh` 초록 · 커밋 `test: 7-30 소스 생성 화면 — 오류 세트 옵션 disabled·title 단언`. TDD 예외(단언만) `Ruling:`.
- 의존: S0.

### 2-38 — detail 빈 이름
- 대상: `core/deliverable/StructureLint.java`(`:154·401·463` 세 줄) · `StructureLintTest` 셋(§3-6).
- 순서: 시험 RED → GREEN.
- 검증: `-Dtest='StructureLintTest,DeliverableRoutesTest,SelfCheckTest,ArchitectureTest'` 초록 · `mvn.sh -q -DskipTests verify` rc 0 · 골든 diff 0(`pg-lint.json` 포함) · `verify.sh` 초록 · 커밋 `fix: 2-38 dupIndex·fkTypeMismatch·fkNoIndex detail — 이름 없으면 공백 없이`.
- 의존: S0.

### 5-61a — SecurityAst XXE 넷
- 대상: `core/check/SecurityAst.java` · `fixtures/check/security/pos.java`·`neg.java`(끝에만) · `SecurityAstTest` · `golden/check/fixtures.json`(§3-7).
- 순서: 픽스처 + 시험 넷 RED → 구현 GREEN → `-Dgolden.update=true -Dtest=CheckRulesTest` → `git diff src/test/resources/golden/check/fixtures.json` 이 security/pos.java 새 줄뿐.
- 검증: `-Dtest='SecurityAstTest,CheckRulesTest,JavaRulesTest,SelfCheckTest,ArchitectureTest'` 초록(`SecurityAst` ≤ 30 · 새 메서드 복잡도 ≤ 15) · `mvn.sh -q -DskipTests verify` rc 0 · `verify.sh` 초록 · 커밋 `feat: 5-61a XXE — 변수 단위 막음·필드는 클래스 범위·dom4j SAXReader·jdom SAXBuilder·DOM 전용 변환기 제외`.
- 의존: S0.

### 5-61b — JavaDefects 새 규칙 넷 + 형식 + Minor
- 대상: `resources/check/rules.yaml`(새 넷 + 글 둘) · `core/check/JavaDefects.java` · `core/check/ValueAst.java`(`FLOATING` 가시성·자리) · `fixtures/check/java/PosDefects.java`·`NegDefects.java`(끝) · `JavaDefectsTest` 다섯 · `CheckRulesTest:167` 136 · `golden/check/java.json`(§3-8).
- 순서: rules.yaml → `CheckRulesTest` 136 → 픽스처 + 시험 RED(`JavaRulesTest` 「안 걸린 규칙」 넷 포함) → 구현 GREEN → `-Dgolden.update=true -Dtest=JavaRulesTest` → `java.json` diff 가 Pos 끝 새 줄뿐.
- 검증: `-Dtest='JavaDefectsTest,JavaRulesTest,CheckRulesTest,ValueAstTest,SelfCheckTest,ArchitectureTest'` 초록(`SelfCheckTest` 새 키 0 — 있으면 §3-8 규칙) · `mvn.sh -q -DskipTests verify` rc 0 · `verify.sh` 초록 · 커밋 `feat: 5-61b 규칙 넷 — catchThrowable·logAndThrow·mappingPrivate·floatEquals + staticDateFormat 형식 넷 + 중첩 catch·ThreadLocal 넘김`.
- 의존: 5-61a(독립 — 차례만).

### 5-61c — ValueAst 다섯 + TxAst 둘
- 대상: `core/check/ValueAst.java`(`staticFinal`·몸 다섯) · `core/check/TxAst.java`(`rollbacks` 한 분기) · 픽스처 `PosValue`·`NegValue`·`PosLiteral`·`NegLiteral`·`PosTx`(끝) · `fixtures/cleanup/value/Pos.java` 한 줄 · `ValueAstTest` 여섯 · `TxAstTest` 하나 · 골든 `java.json`·`cleanup/value-Pos.java.txt`·`cleanup/steps.json`(§3-9).
- 순서: 픽스처 + 시험 RED → GREEN → `-Dgolden.update=true -Dtest='JavaRulesTest,CleanupTest'` → diff 가 새 줄·`CONST.equals(x)` 줄·steps value 건수뿐.
- 검증: `-Dtest='ValueAstTest,TxAstTest,JavaRulesTest,CleanupTest,SelfCheckTest'` 초록(`ValueAst` ≤ 30) · `mvn.sh -q -DskipTests verify` rc 0 · `verify.sh` 초록 · 커밋 `fix: 5-61c refEquals 상수 반환·표지 객체·세 항 OR + 상수 앞세우기 + String.format·enum·record + rollbackFor {}`.
- 의존: 5-61b.

### 5-62a — ResourceAst 넷
- 대상: `core/check/ResourceAst.java`(`filesStream`·`ownerOwned`·`borrowedName`·`candidate`·`Planner.resourceAt`·`closeException`·상수) · `fixtures/check/security/pos.java`·`neg.java`(끝) · `ResourceAstTest` 셋 · `fixtures.json`(§3-10).
- 순서: 픽스처 + 시험 RED → GREEN → `-Dgolden.update=true -Dtest=CheckRulesTest` → `fixtures.json` diff 새 줄뿐.
- 검증: `-Dtest='ResourceAstTest,CheckRulesTest,CleanupTest,SelfCheckTest'` 초록(`ResourceAst` 바깥 ≤ 30) · `mvn.sh -q -DskipTests verify` rc 0 · `verify.sh` 초록 · 커밋 `fix: 5-62a 자원 — Files.* 스트림은 자원, 요청·문맥·템플릿·세션 getter 와 getSqlSession() 연결은 빌린 것, 지역 래퍼 사슬 한 홉`.
- 의존: 5-61c(독립 — 차례만).

### 5-62b — twr 꼴 C + ResourceFix
- 대상: `core/check/ResourceAst.java`(`Twr`·`Planner.of`·`tailCloses`·`tailNames`) · `core/check/ResourceFix.java`(`edit`·`apply:75`) · 픽스처 `PosResource`·`NegResource`·`fixtures/cleanup/resource/Pos.java`(끝) · `ResourceAstTest` 둘 · 골든 `java.json`·`cleanup/resource-Pos.java.txt`·`steps.json`(§3-11).
- 순서: 픽스처 + 시험 RED → `ResourceAst` GREEN → `ResourceFix` → `-Dgolden.update=true -Dtest='JavaRulesTest,CleanupTest'` → diff 가 새 줄·`formC` 고침·steps resource 건수뿐 → `CleanupTest.valueAndResourceCompile` 초록(javac).
- 검증: `-Dtest='ResourceAstTest,CleanupTest,CleanupRoutesTest,JavaRulesTest,SelfCheckTest'` 초록 · `mvn.sh -q -DskipTests verify` rc 0 · `verify.sh` 초록 · 커밋 `feat: 5-62b try 몸 끝 close(finally 없음)도 try-with-resources 판정·고침 — 꼴 C`.
- 의존: 5-62a.

### 5-62c — Objects import 끼우기
- 대상: `core/check/Cleanup.java`(`Edits.touchedNew`) · `core/check/ValueFix.java`(`refEquals`·`conflictingObjectsImport`·`importLine`·`apply` 인자) · `fixtures/cleanup/value/Pos.java`(또는 `Pos2.java`) · `CleanupTest` 둘 · 골든 `cleanup/value-*.txt`·`steps.json`(§3-12).
- 순서: 시험 RED → GREEN → `-Dgolden.update=true -Dtest=CleanupTest` → diff 가 import 줄·`Objects.equals` 줄·steps 뿐 → `valueAndResourceCompile` 초록.
- 검증: `-Dtest='CleanupTest,CleanupRoutesTest,SelfCheckTest'` 초록(`Cleanup` 29·길이 < 1000 — `SelfCheckTest`·`self-cleanup.txt` 불변) · `mvn.sh -q -DskipTests verify` rc 0 · `verify.sh` 초록 · 커밋 `feat: 5-62c 이름끼리 == 고침에 java.util.Objects import 끼우기`.
- 의존: 5-62b(독립 — 차례만).

### V-54a — corpus 골든 재채점
- 대상: `golden/corpus/*`(§3-13).
- 검증: 갱신 명령 초록 · `git diff --stat src/test/resources/golden/corpus` 가 §3-13 목록 안 · `ddl-*`·`hr-lint`·`gen-hr`·`analyze-*`·`sql*`·`security-juliet*` 불변 · 건수를 이력에 · 커밋 `test: V-54a 재채점 — 새 규칙 넷·바뀐 판정·정리`.
- 의존: 5-62c.

### R-21 — 손 판정
- 대상: compare ㉔ 「손 판정」 표(§3-14) + 고침(≤ 2, 음성 픽스처 먼저).
- 검증: 표 + 합 줄 · 고침이 있으면 V-54a 다시 · 커밋 `docs: R-21 손 판정 — 새 규칙·바뀐 판정`(고침은 따로 `fix:`).
- 의존: V-54a.

### V-54 — compare ㉔ · 정적 분석 · `--corpus` · `--db`
- 대상: `design/compare-2026-10-28.md` ㉔(§3-15).
- 검증: `mvn.sh -DskipTests verify` rc 0 → `verify.sh` → `--corpus` → projectshop 확인·RAM 확인 → `--db` 혼자 — 넷 초록(따로) · 끝 줄이 더 부르는 레인 없음 · 벽시계 둘 · `doc-lint` · 커밋 `docs: V-54 재측정 — compare ㉔ + 정적 분석 + --corpus + --db`.
- 의존: R-21.

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7 · 행의 축·강제 지점과 diff · `depthMask` 가 같은 길이이고 따옴표는 `ctasColumns` 가 먼저 비웠나 · NATURAL·USING 에서 `*`·`t.*` 만 비고 명시 항목은 그대로인가 · `INLINE_COMMENT` 값이 원문 자리인가 · `CTAS_EXPR` 컬럼이 `pull` 에서 번호를 받고 notes 가 붙나 · `bindPattern` 이 egov4·egov5 에 없나 · `fmt` taglib 이 날짜 PK 만 있을 때도 나오나 · 2-38 이 `dupRelation` 을 안 건드렸나 · `guarded` 가 변수 없는 사슬에서 설계 48 그대로인가 · `domOnly` 가 `transform` 0 이면 지적 그대로인가 · `catchBarrier` 가 finally 판정을 안 바꿨나 · `floatEquals` 가 `d != d` 를 안 잡나 · `staticFinal` 표지 가지가 `!=` 도 받나 · 꼴 C 가 catch 없는 try 를 안 받나 · `ResourceFix` 꼴 C 결과가 `valueAndResourceCompile` 에서 컴파일되나 · `touchedNew` 가 끼운 줄을 세나 · 새 규칙 넷에 양성·음성 픽스처가 다 있나 · 골든 변화가 의도된 목록 안인가 · `self-check.txt` 새 키 처분 · PMD 짝 diff 0) → `bash scripts/gate-probe.sh` → `bash scripts/mvn.sh -DskipTests verify` → `bash scripts/verify.sh`(리뷰 고침 뒤 `--corpus`·`--db` 는 바뀐 레인만 다시) → `git push -u origin work/2026-11-08b` → `gh pr create --base main`(행마다 시작 세 줄 표 + 로컬 닫기 `3388d9a`·S0 같이 + ㉔ 핵심 수치) → CI 폴링(`gh api … check-runs`) → claude[bot] 처분 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → 닫기 커밋은 다음.

## 5. 금지 사항
- `pure/`·portfolio·`docs/`·`core/meta`·`core/dialect`·`core/profile`·`core/quality`·`core/analyze`·`core/vcs` · `core/gen` 의 `GenModel`·`Generator`·`TemplateSet`·`TypeMapping`·`DtoGenerator`·`InsertGen`·`DdlGen` · `core/check` 의 `JavaRules`(몸·메서드 수 30)·`Taint`·`SpringMappings`·`RuleSet`·`Cleanup`(`Edits.touchedNew` 밖) · `web/*` · `pom.xml`·`scripts/verify-fingerprint.sh`·`corpus/MANIFEST`·표본 · `common.css`·`common.js`·화면 HTML/JS · `templates/gen/egov4`·`egov5`·`Regist`·`Updt`·`input.ftl`·`set.yaml` 셋 · `pure-rules.json` · `bundle/` 수정 금지 · 새 Maven 의존성·새 화면·라우트·프로필 키·세트 변수 키·구조 점검 규칙(enum)·`Cleanup.RULES` 변경 금지
- `DdlReader` — 새 메서드 금지(30) · `item`·`read` 분기 추가 금지 · `matching`·`QUOTED`·`mask`·`splitTop` 논리 변경 금지 · `NAME_ONLY` 기존 글 변경 금지
- `DdlPull` — 새 정규식은 `MERGE_JOIN`·`EXPR_ALIAS` 둘, 중첩 수량자 금지(SpotBugs REDOS) · 별칭 없는 식·서브쿼리·없는 원본·모호한 맨 이름 추측 금지 · notes `ctas` 글 변경 금지
- `FieldRoles` — `dates` 분기 추가 금지(한 줄) · `PATTERN`·`SHOW`·`ISO` 변경 금지 · 템플릿 — java.time 세트에 `fmt` 금지 · Updt·Regist 변경 금지
- `StructureLint` — 세 줄 밖 변경 금지 · 글 꼴(`⊂`·`→`·`: why`) 변경 금지 · `dupRelation`·`uniqueNullable` 변경 금지 · 메서드 추가 금지(30 근접)
- `SecurityAst` — `trustAll` 논리 변경 금지 · `guards`(키 목록) 변경 금지(`EXPANDS` 만) · 변수 없는 사슬의 메서드 단위 판정 변경 금지 · 새 정규식 금지
- `JavaDefects` — `finallyReturn`·`finallyThrow` 판정 변경 금지(`barrier` 그대로, `catchBarrier` 따로) · 규칙 id 는 §3-8 넷뿐 · `LOG_METHODS`·`LOGGER` 는 복사(`JavaRules` 가시성 변경 금지)
- `ValueAst` — 메서드 ≤ 30(새 메서드는 `staticFinal`(+ 필요시 `sentinel`) 뿐) · `BOXED`·`WEEK_YEAR_PATTERN` 변경 금지 · 다른 파일·`FieldAccessExpr` 상수(`Const.X`) 풀기 금지 · 상수 앞세우기는 `static final String` 리터럴 초기화만
- `ResourceAst` — 바깥 메서드 ≤ 30 · `Stream` 을 `EXACT` 에 넣기 금지(`filesStream` 만) · `OWNER_TYPES` 접미 다섯 밖 추가 금지 · 래퍼 사슬은 한 홉(초기화식) · 꼴 C 는 catch 있는 try·꼴 B 선언·마지막 문장 close 만 · `closeName` 변경 금지
- `ResourceFix`·`ValueFix` — 여러 줄 식·겹친 식 건너뜀 그대로 · `Objects` 충돌 import 면 건너뜀 · import 끼우기는 `java.util.Objects` 하나만
- 골든 — 의도된 변화(§3-4 egov35 둘 · §3-9·§3-11·§3-12 `cleanup/*` · `check/java.json`·`fixtures.json` 새 줄 · V-54a 목록) 밖 변화 금지 · `-Dgolden.update` 는 그 시험에만 · `self-check.txt` 새 키는 §3-8 규칙 · 이력·compare 에 표본 코드 본문 금지 · 원본 되쓰기 API·CLI 금지 · `pmd-baseline.sh` 안 돌린다 · `--db` 와 다른 JVM 겹치기 금지 · `--corpus` → Docker → `--db` 순서 · **`wsl --shutdown` 금지**(projectshop) · 이름 지정 corpus 시험은 `-Djunit.timeout=10m` 없이 돌리기 금지

## 6. 최종 검증
- `bash scripts/mvn.sh -DskipTests verify` rc 0 · `bash scripts/verify.sh` · `--corpus` · `--db` 초록(순서대로, 따로) · `gate-probe.sh` 10/10
- 골든: `git diff --stat origin/main -- src/test/resources/golden` 이 `gen/egov35/EmpHistList.jsp`·`EmpHistDetail.jsp` · `check/java.json`·`check/fixtures.json` · `cleanup/value-Pos*.txt`·`cleanup/resource-Pos.java.txt`·`cleanup/steps.json` · `corpus/check-*.json`·`check-*-hits.txt`·`vcs-egov.json`·`cleanup-*.json`·`security-benchmark.json` 안 · `self-check.txt` 새 키 0(또는 §3-8 처분) · `self-cleanup.txt` 불변
- 시험: `DdlReaderTest` +1(`inlineCommentIgnoresQuotedText`)·`ctasFromSource` 확장(TB~TG) · `GenModelTest.legacyDates` +1 단언 · `GenTemplatesTest` · `SmokeHtmlUnitTest` +1 · `StructureLintTest` 셋 확장 · `SecurityAstTest` +4 · `JavaDefectsTest` +5 · `ValueAstTest` +6 · `TxAstTest` +1 · `ResourceAstTest` +5 · `CleanupTest` +2 · `CheckRulesTest` 136·45 · `JavaRulesTest` 새 규칙 넷 양성 · `GeneratorJavacTest` 40 · corpus 열 + `DdlCorpusTest`·`HrSnapshotCorpusTest`·`GeneratorCorpusTest` · `ArchitectureTest`·`VerifyLanesTest`·`SelfCheckTest`
- 수동(한 번, 이력에 한 줄): `run.bat` → 코드 검사 화면에서 표본 하나(jeecg) 폴더 검사 → 새 규칙 넷 건수가 CLI 와 같다 · 코드 정리 화면 미리보기에서 꼴 C 고침 한 자리가 보인다(쓰지 않음)
- compare ㉔ 표 + 뺀 것 + 「드러난 것」 + 「후보로」 · 이력에 건수·벽시계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- 1-81·1-82·1-83·2-38 이 골든(`ddl-*`·`sqltables-diff`·`logical-summary`·`gen-hr`·`hr-lint`·`pg-lint`·`analyze-*`)을 바꿈 · `self-check.txt` 의 `item`·`read` 줄 변화 · `DdlReader` 메서드 31
- 7-31 골든 갱신 diff 가 egov35 둘 밖(egov4·egov5·bbs·`model*.json`) · `GeneratorJavacTest` 빨강
- 5-61b `SelfCheckTest` 새 키 > 5 이고 우리 코드 고침이 셋 넘는 파일 · 새 규칙이 `fixtures/check` 밖 픽스처(a11y·mybatis…)에 걸림
- 5-62b `valueAndResourceCompile` 빨강(꼴 C 결과가 컴파일 안 됨) · `cleanupChangesNothing`(`self-cleanup.txt`) 변화
- V-54a 에서 §3-13 목록 밖 골든 변화 · `security-juliet*` 채점 칸 변화 · Benchmark 채점 칸(otherRules 밖) 변화 · 새 규칙 하나가 표본 전체 1,000 건 넘음(소음 — 규칙 정의 재검토)
- `pmd:check`·SpotBugs 새 짝(REDOS 포함) · `ArchitectureTest`·`VerifyLanesTest` 빨강
- `verify.sh --db` 메모리로 끊김 — 고아 JVM 끄고 한 번 더, 또 끊기면 보고 · projectshop-c0 가 큰 작업 중인데 사용자 답이 없으면 `--db` 대기 상태로 보고
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① 1-81~2-38 골든 diff 0(표본에 글 안 `COMMENT '`·CTAS·빈 이름 인덱스가 없다고 실측했지만 돌려야 안다 — 바뀌면 중단) ② `e::addSuppressed` 의 JavaParser scope 꼴(픽스처가 못박는다 — §3-8 둘째 길) ③ 새 규칙 넷·바뀐 판정의 표본 건수(V-54a·R-21 이 잰다, 상한은 7절) ④ `SelfCheckTest` 새 키(0 기대, §3-8 처분) ⑤ `ValueAst`·`ResourceAst`·`SecurityAst` 메서드 수·새 메서드 복잡도(`SelfCheckTest` 가 잰다 — 넘으면 §3 에 적은 분리 자리로) ⑥ `--corpus`·`--db` 벽시계·RAM(projectshop 상태). 첫 스텝 전: `bash scripts/verify.sh` 초록 · `git log --oneline -1`(S0 커밋, 가지 `work/2026-11-08b`) · `git branch --list 'wip/*'` 빈 것 · `docker ps`(projectshop 컨테이너 유무) · node · `C:/workspace/toolbox-corpus` 있음(jeecg·struts 포함)
- **사용자만 정할 수 있는 것** — 없음(묶음·세부·(D)·머지 위임 전부 2026-11-08 결정. 라-1·9·12 는 사용자 괄호 셋 밖이라 범위 밖으로 두었다 — 넣으려면 새 번들)
