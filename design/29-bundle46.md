# 설계 29 — 번들 46: 코드 검사 규칙 `java.declarationOrder` (Fable, 2026-10-21)

## 맥락
번들 45 가 우리 저장소 코드에 Checkstyle `DeclarationOrder`(Java Code Conventions 3.1.3)를 켰다. 사용자가 같은 규칙을 도구의 「코드 검사」 화면(사용자 프로젝트를 보는 기능)에도 넣기로 했다(2026-10-21). 코드 검사는 **지적만** 한다 — 자동 수정은 뒤에 설계할 새 화면 「코드 정리」 몫이고 이 플랜 밖이다(메모리 `code-cleanup-screen-decisions`).

실행자는 Opus. 이 플랜은 실행자가 그대로 따른다. 차례는 `S0` → `5-32a` → `5-32b` → `5-32c` → `5-32d` → `5-32e` → 마무리(번들 모드).

사용자 결정(뒤집지 않는다):
- 기본 등급 **info**. 접근자 순서(public → protected → package → private)도 **본다**. 프로필로 끌 수 있게 한다.
- 이번 플랜은 규칙만. 자동 수정·새 화면은 다음 설계.
- 시험은 가장자리까지: 손 픽스처 + 우리 코드 303 음성 + **Checkstyle 과의 차이 비교를 corpus 시험으로 영구**(checkstyle 12.3.1 을 test 의존성으로 — pom 이 바뀌어 이 번들은 `--db` 도 한 번).

---

## 1. 목표와 범위
- 목표: 코드 검사가 Java 파일의 선언 순서 위반(static 변수 → 인스턴스 변수 → 생성자 → 메서드, 같은 종류 안 접근자 순)을 `java.declarationOrder`(info)로 지적한다. 판정은 Checkstyle `DeclarationOrder` 기본값과 같고, 실물 표본 약 7,000 Java 파일에서 Checkstyle 과 파일·줄이 맞는다.
- 체감 변화: 코드 검사 화면·CLI `check`·xlsx 에 ㄴ Java 구조 묶음의 새 규칙 한 줄이 나오고, 상수를 메서드 사이에 둔 파일이 파일·줄과 함께 잡힌다. 프로필 `codecheck.rules` 에 `java.declarationOrder: { params: { ignoreModifiers: true } }` 를 두면 접근자 순서는 안 본다.
- In scope: `rules.yaml` 항목 · `JavaRules` 판정 · 픽스처 넷 + 골든 둘 · 규칙 수 시험 85 · `ignoreModifiers` 경로 시험 · 우리 코드 음성 시험 · `ProfileStore.saveCodeCheck` 가 `params` 덮어쓰기를 지키게 · `profiles/example.yaml` 주석 · corpus 골든 갱신 · Checkstyle 차이 비교 corpus 시험(pom test 의존성) · `design/29-bundle46.md` · PLAN/PROGRESS.
- Out of scope: 자동 수정 · 순수본(`pure/`, 정규식 규칙만 간다) · 화면 JS(규칙 목록은 `/api/check/rules` 에서 동적) · tb-ignore(등록 불필요) · 다른 Checkstyle 규칙 · `rules.yaml` 머리 주석의 kind 목록 손질 · 차이 비교에서 드러난 Checkstyle 쪽 특이 판정을 우리가 흉내 내는 것(baseline 에 남기고 이력에 사유).

## 2. 현재 구조 요약
- **규칙 정의** `src/main/resources/check/rules.yaml:591-670` ㄴ Java 구조 묶음. 항목 꼴은 `id·group·severity·kind: java·globs·message·fix(·params·ref)`. `java.parseError`(665~670)가 묶음 마지막이고 `fix` 가 없다. `Rule.Def`(`core/check/Rule.java:27`) — `params` 는 null 이면 `Map.of()`, 프로필 덮어쓰기 `override`(`:60`)가 `params` 를 키별로 병합한다.
- **판정 엔진** `core/check/JavaRules.java` — 생성자(`:55`, package-private)가 켜진 정의를 `on`(id → Def)에 담고, `apply(Source)`(`:74`)가 파일을 한 번 파싱해 `imports` → `ClassOrInterfaceDeclaration` 마다 `type` → catch 순으로 돈다. 규칙마다 `on.containsKey("java.xxx")` 로 막는다. 지적은 `add(Ctx, rule, Node)`(`:142`) — 줄은 `Node.getBegin()`(필드는 첫 애너테이션·수식어 줄), 발췌는 그 줄 원문. 파싱 실패면 `java.parseError` 하나만(그 id 가 `on` 에 있을 때). 정렬은 줄 → 규칙(`:98`). 기존 규칙은 중첩·익명·enum·record 를 안 본다. `PARSE_ERROR` 상수가 `public static final`(`:35`).
- **디스패치** `RuleSet.java:94` 가 `kind: java` 를 `JavaRules` 로. globs 는 안 쓰고 `lang == java` 인 Source 전부(`:197`). `RuleSet.builtin()`(`:54`)·`load(profile, base)`(`:61`)·`defs()`. tb-ignore 는 `RuleSet.Run.apply`(`:205-210`)가 결과에 걸러 준다 — 규칙 쪽 작업 없음. `Source(rel, text, encoding, lineEnding, lang)`(`core/check/Source.java:10`). `LocalFiles.read(String path)`(`core/fs/LocalFiles.java:257`) → `Text(text, encoding, lineEnding)`(`:74`), 절대 경로만.
- **JavaParser 3.26.3**(`m2/com/github/javaparser/javaparser-core/3.26.3/`, jar 실측): `TypeDeclaration.getMembers()`·`EnumConstantDeclaration.getClassBody()`·`ObjectCreationExpr.getAnonymousClassBody()`(Optional) 는 `NodeList<BodyDeclaration<?>>`, `FieldDeclaration.isStatic()`, `getAccessSpecifier()` 는 `NodeWithModifiers` 의 default 메서드(`FieldDeclaration` 이 구현) → `AccessSpecifier{PUBLIC, PRIVATE, PROTECTED, NONE}`. `CompactConstructorDeclaration`·`AccessSpecifier` 클래스 있음. `cu.findAll(TypeDeclaration.class)` 는 중첩·지역 타입까지 준다.
- **Checkstyle 12.3.1**(`m2/com/puppycrawl/tools/checkstyle/12.3.1/`, 0-61e 플러그인이 받아 둔 것) — 시험에서 부를 API(jar javap 실측): `Checker`(`extends AbstractAutomaticBean`) `setModuleClassLoader(ClassLoader)`·`configure(Configuration)`·`setHaltOnException(boolean)`·`addListener(AuditListener)`·`process(List<File>)`·`destroy()`; `DefaultConfiguration(String)` `addProperty(k, v)`·`addChild(cfg)`; `AuditListener` 6 메서드(auditStarted·auditFinished·fileStarted·fileFinished·addError·addException); `AuditEvent.getFileName()`·`getLine()`·`getViolation()` → `Violation.getKey()`(`declaration.order.static`·`instance`·`constructor`·`access`). 전이 의존성 11개(picocli 4.7.7·antlr4-runtime 4.13.2·commons-beanutils 1.11.0·guava 33.5.0-jre·reflections 0.10.2·slf4j-api·**slf4j-simple** 2.0.17·Saxon-HE 12.9·checker-qual 3.52.1·doxia-core·doxia-module-xdoc 1.12.0) 전부 `m2/` 에 있다. 우리 로깅은 logback(`pom.xml:79-81`) → `slf4j-simple` 은 빼야 바인딩이 안 겹친다. picocli 는 우리 4.7.6 이 직접 의존이라 Maven 이 그걸 고른다(Checkstyle 은 CLI 에만 쓴다).
- **시험**
  - `JavaRulesTest.fixturesGolden`(`src/test/java/kr/ejg/toolbox/core/check/JavaRulesTest.java:44-61`) — `fixtures/check/java/` 전부를 java 묶음만 켜고(`egov35`, `profile(framework, naming)` 도우미 `:28`) 돌려 `Neg*` 히트 0, 켜진 java 규칙 전부 `Pos*` 에서 한 번 이상, 골든 `golden/check/java.json`(`{file, line, rule}`). `run(rules, data)`(`:33`)가 `CheckRulesTest.fixtureSources(FIXTURES.resolve("java"), data)` 로 파일을 든다. `profileNaming`(`:73`)은 `java.naming` 파일 목록을 `[NegController, NegParams, PosController]` 로 고정 → 새 픽스처에 `@Controller`·`@Service`·`@Repository` 금지. import 도 두지 않는다(`java.unusedImport`).
  - `CheckRulesTest.builtinRulesCarryFixAndExampleCustomFileLoadsOff`(`:150-162`) — `assertEquals(84, builtin.size())`(`:153`), 모든 규칙 `fix` 60자 이내(parseError 둘만 예외).
  - `CheckRulesTest.fixturesGolden`(`:76-108`)은 `java/` 폴더와 java 묶음을 뺀다 → `golden/check/fixtures.json` 불변. `CheckStoreTest.runnerOverFixtureFolder`(`:56-67`)는 전 묶음으로 픽스처 폴더 전체 → `parseErrors == 2`·`suppressed == 6`. 13.4.2 jar 실측(2026-10-21): 기존 픽스처 전부(`ignore/` 포함) 선언 순서 위반 0 → 음성·억제 건수 불변.
  - corpus(`@Tag("corpus")`) `CheckCorpusTest`(`:35-39` 무리 egov·commons·jspwiki·roller, other 는 A 만, `rules(egov)` `:48`) — 규칙별 건수 골든 `golden/corpus/check-<무리>.json`(`{rule: {count, files}}`)·히트 파일 목록 `check-<무리>-hits.txt`(`CorpusFiles.conformance` — 목록에 없는 파일이 새로 걸리면 「새로 깨짐」, 상한 없음). `CorpusFiles`(`src/test/java/kr/ejg/toolbox/CorpusFiles.java`): `verify()`·`root()`·`files(source, globs…)`(`:114`, 재귀, 상대 경로 정렬)·`rel(p)`(`:130`, 루트 기준 `/`)·`baseline(name, broken, total)`(`:139`, 등급 B — `golden/corpus/<name>.txt`, 새로 깨짐·새로 고쳐짐 둘 다 빨강, `B_LIMIT` 2% 초과 빨강)·`conformance(name, list)`(`:188`, 상한 없음)·`none(what, broken, total)`(`:193`, 등급 A). 갱신은 `-Dgolden.update=true`(`GoldenFiles.updating()` `:36`). `manifest()` 는 package-private(`kr.ejg.toolbox`) — `core.check` 에서 못 쓴다. 표본의 Java 원천과 파일 수(2026-10-21): egov 1089 · egov-prev 1089 · egov-portal 233 · egov-enterprise 233 · egov-homepage 114 · jspwiki 587 · roller 545 · struts 787 · commons-lang 259 · commons-io 276 · commons-collections 367 · shopizer 831 · egov-msa 763 = 7,173.
  - 순수본 짝 `PureRulesJsonTest:33` `kind != regex` 는 뺀다, `:61` 정규식 54 고정 → 영향 없음. `PureFrozenTest`·`docs/pure-code-check.md:20` 그대로.
  - 화면 `code_check_ext.js:9-12` 묶음 이름만 고정, 규칙은 API → 손댈 것 없음. `SmokeHtmlUnitTest.codeCheckFolderRun` 은 `[id^='r_java.']` 를 세어 맞춰진다.
  - `VerifyLanesTest` — corpus 꼬리표 시험 소스는 `LANE_CORPUS` 에 있어야: `src/test/java/kr/ejg/toolbox/core/check` 는 이미 있다. SpotBugs 는 test 를 안 본다. `ArchitectureTest` 는 core 가 web·cli 를 안 보는지·HttpClient 등 — 시험 클래스의 Checkstyle 의존은 무관.
- **프로필** `Profile.CodeCheck(groups, rules, customRules, exclude)`(`core/profile/Profile.java:123`, 3-인자 생성자도 있음 — `JavaRulesTest:30`). `profiles/example.yaml:129-138` codecheck 블록, `:132` 덮어쓰기 예시 주석. **함정**: `ProfileStore.saveCodeCheck`(`:125-157`)는 화면이 보낸 켬·끔(Boolean)이 오면 옛 값이 `{regex…}` 맵일 때만 보존한다(`:148` `m.containsKey("regex")`) — 손으로 적은 `{params: …}` 는 화면에서 규칙을 한 번 끄면 Boolean 으로 덮여 사라진다. 시험 선례 `ProfileStoreTest.saveCodeCheckMergesRules`(`:243-254`).
- **pom** — `<properties>`(`:12-26`, `picocli.version` 등) · test 의존성 블록들(`:109-160`, 꼴은 `groupId/artifactId/version/scope test`) · 0-61e 플러그인(`:253-285`)의 `<dependency>` checkstyle `12.3.1` 리터럴.
- **레인**: `core/check`·`resources/check`·`golden/corpus`·`test/.../core/check` 는 corpus 레인 → push 앞 `verify.sh --corpus`. **`pom.xml` 은 db 레인** → 5-32e 뒤 push 앞 `verify.sh --db`(약 17분, 메모리·`docker ps` 확인 후). `profiles/` 는 어느 레인 지문에도 없다.
- **Checkstyle 13.4.2 실측 판정**(2026-10-21, scratch 픽스처 — 3절 [고정] 상태기계의 근거, 전부 4절 픽스처에 들어 있다):
  - 전방 참조는 접근자 위반을 안 막는다(`private static B = Fwd.A + …; static A;` → A 에 access).
  - 인터페이스 필드(`static` 없음)는 인스턴스 변수로 본다 → 메서드 뒤면 instance.
  - 익명 클래스 몸체·enum 상수 몸체·지역 클래스·record·enum·중첩 클래스 몸체는 각자 따로 검사한다. 중첩 타입 선언 자체·`static {}`·인스턴스 초기화 블록·애너테이션 멤버(`int x() default 1`)는 자리를 안 바꾼다.
  - `ignoreModifiers=true` 면 접근자 순서를 안 볼 뿐 아니라, 생성자·메서드 전이면 인스턴스 변수 뒤 static 변수도 허용한다.
  - 접근자 추적은 static 그룹에서 인스턴스 그룹으로 넘어갈 때 public 으로 리셋된다. 자리가 틀린 필드(static/instance 위반)는 access 위반을 따로 안 내지만, 그 필드의 접근자가 지금 값 이상이면 추적값을 갱신한다(`public i1; static s3; protected i2;` → s3 static, i2 access). 위반 때는 추적값을 안 갱신한다(private 뒤 package·public 둘 다 지적).
  - 생성자·메서드는 접근자 순서를 안 본다. 줄은 애너테이션이 있으면 애너테이션 줄.
- **플랜의 픽스처 넷 사전 검증**(2026-10-21): 4절 글자 그대로를 Checkstyle 13.4.2 에 넣으면 기본 `PosOrder` 8줄·`PosOrderEdge` 24줄·`Neg*` 0, `ignoreModifiers` `PosOrder` 5줄·`PosOrderEdge` 15줄·`Neg*` 0(줄 목록은 4절). 넷 다 `javac -encoding UTF-8` 통과.

## 3. 설계 결정
- [고정] 규칙 id `java.declarationOrder`, `group: java`, `severity: info`, `kind: java`, `params: {ignoreModifiers: false}`. 파라미터 이름은 Checkstyle 과 같게. 버린 대안: `accessOrder: true` 같은 우리 식 이름 — Checkstyle 문서와 어긋난다.
- [고정] 판정 = 아래 상태기계. 타입 몸체(`TypeDeclaration.getMembers()` — 클래스·인터페이스·enum·record·애너테이션, 중첩·지역 포함) + enum 상수 몸체 + 익명 클래스 몸체마다 따로 돈다.
  ```
  stage: STATIC(0) → INSTANCE(1) → CTOR(2) → METHOD(3), 시작 0 · access: public=0 protected=1 package=2 private=3, 시작 0
  멤버 순서대로:
    FieldDeclaration(static):  valid=true; stage>0 이고 (mods 또는 stage>1) 이면 지적, valid=false
    FieldDeclaration(인스턴스): valid=true; stage>1 이면 지적, valid=false; 아니면 stage==0 일 때 stage=1, access=0
       (둘 다) r=rank(접근자); access>r 이면 { valid && mods 일 때 지적 } 아니면 access=r
    ConstructorDeclaration·CompactConstructorDeclaration: stage>2 이면 지적, 아니면 stage=2
    MethodDeclaration: stage=3
    그 밖(중첩 타입·InitializerDeclaration·AnnotationMemberDeclaration·EnumConstantDeclaration): 아무것도 안 함
  mods = !params.ignoreModifiers
  ```
  한 필드에 지적은 최대 하나. 2절 실측 케이스 전부 이 기계로 재현된다.
- [고정] 발췌는 다른 java 규칙과 같이 그 줄 원문(`add(c, ORDER, node)`). 위반 종류를 글에 안 싣는다. 지적 줄 = `Node.getBegin()`(필드는 애너테이션 줄) — Checkstyle 과 같고 tb-ignore 가 그 줄을 가리킬 수 있다.
- [고정] 시험 네 겹: ① 손 픽스처 넷(기본 32줄 + `ignoreModifiers` 20줄) ② `ignoreModifiers` 경로는 프로필 덮어쓰기로 같은 픽스처를 한 번 더(골든 `check/java-order-ignore.json`) ③ 우리 코드 `src/main/java`·`src/test/java` 303 파일 0건(0-61e 가 Checkstyle 로 보증하는 음성) ④ 표본 7,173 파일에서 Checkstyle 12.3.1 과 파일·줄 집합을 맞대는 `@Tag("corpus")` 시험 — 차이는 등급 B baseline `golden/corpus/order-diff.txt`(상한 2%), 어느 쪽이든 못 읽은 파일은 `order-skipped.txt`(conformance). `ignoreModifiers` 모드 비교는 egov·jspwiki 두 원천만(`order-diff-ignore.txt`, 실행 시간).
- [고정] ④ 를 위해 `com.puppycrawl.tools:checkstyle:${checkstyle.version}` 을 **test 스코프**로 pom 에 더하고 `slf4j-simple`·`doxia-core`·`doxia-module-xdoc` 을 뺀다(logback 바인딩 충돌·불필요). 판은 `<checkstyle.version>12.3.1</checkstyle.version>` 속성 하나로 플러그인과 공유. 버린 대안: 13.4.2 jar 를 저장소 밖에서 집 스크립트로 — CI 에 안 돌고 영구가 아니다(사용자가 영구를 골랐다).
- [고정] Checkstyle 은 시험 안에서 `DefaultConfiguration` 으로 조립(Checker{charset=UTF-8, localeLanguage=en} → TreeWalker → DeclarationOrder[ignoreModifiers]) — `config/checkstyle/declaration-order.xml` 을 안 읽는다(모드 둘을 돌려야 해서). 두 엔진에 **같은 글**을 준다 — `LocalFiles.read` 로 인코딩을 판별해 읽은 글을 임시 폴더에 UTF-8 로 쓰고 Checkstyle 에 그 파일을, 우리 규칙엔 그 글을 준다(표본에 EUC-KR 파일이 있다).
- [고정] 차이가 나면: 우리 판정이 틀린 것은 고친다(한 번들에 10건까지, 넘으면 baseline 에 남기고 새 행). Checkstyle 쪽 특이 판정(예: 못 읽는 문법)은 baseline 에 남기고 이력에 사유. 판정을 완화해 차이를 지우는 것 금지.
- [고정] `ProfileStore.saveCodeCheck` 보존 조건을 `regex` 또는 `params` 가 있는 맵으로 넓힌다.
- [고정] corpus 골든(`check-*.json`·`-hits.txt`)은 갱신한다(새 키 추가·히트 목록 추가만). diff 를 이력에 적는다.
- 청크: `S0`(설계 파일 + PLAN 행) → `5-32a`(규칙 + 판정 + 기본 픽스처 둘 + 골든 + 수 85) → `5-32b`(가장자리 픽스처 둘 + `ignoreModifiers` 시험 + 우리 코드 음성 시험 + 골든) → `5-32c`(ProfileStore params 보존 + 시험 + example 주석) → `5-32d`(corpus 골든 갱신) → `5-32e`(pom test 의존성 + 차이 비교 corpus 시험 + baseline 셋). 5-32a·b 는 파일 1~3 초과 — 한 규칙의 양성·음성·골든은 한 커밋이어야 verify 가 초록이라 묶는다(행에 적는다).
- 가지: `work/2026-10-22`(main `cc00b85` 에서). 머지 위임 없음 — PR 열고 멈춘다.

## 4. 실행 스텝

**스텝 0 — 가지**
- 변경: `gh pr list --state open` 이 비어 있는지 → `git checkout main && git pull` → `git checkout -b work/2026-10-22`.
- 검증: `git rev-parse --abbrev-ref HEAD` = `work/2026-10-22`.
- 의존: 없음. 열린 PR 이 있으면 7절.

**스텝 1 — S0 설계 파일 + PLAN 행**
- 대상: 새 `design/29-bundle46.md` · `PLAN.md` §15 번들 표(`| 번들 45` 행 뒤) · §16 분할표(`| 0-61f` 행 뒤, `반입 전날:` 앞).
- 변경: `design/29-bundle46.md` = 이 플랜의 머리(제목·Context·사용자 결정)와 1~7절을 그대로(`design/28-bundle44.md` 머리 꼴). 번들 표 행:
  `| 번들 46 | \`S0\`→\`5-32a\`→\`5-32b\`→\`5-32c\`→\`5-32d\`→\`5-32e\` | 2026-10-21(Fable, 설계 29 — \`design/29-bundle46.md\`). **코드 검사 규칙 java.declarationOrder** — Checkstyle DeclarationOrder 와 같은 판정(info, 접근자 순서 봄, \`params.ignoreModifiers\` 로 끔) · 가장자리 픽스처·우리 코드 303 음성 · ProfileStore 가 params 덮어쓰기를 지킴 · corpus 골든 갱신 · **Checkstyle 과 표본 7,173 파일 차이 비교(corpus 시험, checkstyle 12.3.1 test 의존성)**. 새 의존성: test 스코프 하나(m2 에 이미 있음). 새 화면 0, 프로필 키 0. 머지 위임 없음 | 표본 폴더 + node(\`--corpus\`) · Docker(\`--db\` — pom) | 대기 |`
  분할표 행 다섯 — 각 행에 사실·결정·강제 지점·건드리는 자리(설계 29 §3·§4 요약):
  `| 5-32a | 코드 검사 규칙 java.declarationOrder — 정의·판정·기본 픽스처·골든 | 설계 29 §3·§4 스텝 2. **사실**: Checkstyle 13.4.2 실측으로 상태기계를 세웠다(설계 29 §2). **강제 지점**: \`JavaRulesTest\`(음성 0·양성 전부·골든)·\`CheckRulesTest\` 85. **건드리는 자리**: \`rules.yaml\`·\`JavaRules\`·\`fixtures/check/java/{PosOrder,NegOrder}.java\`·\`golden/check/java.json\`·\`CheckRulesTest\`. 청크 규칙 초과 — 한 규칙의 양성·음성·골든은 한 커밋 | 없음 | 대기 |`
  `| 5-32b | 선언 순서 가장자리 — 픽스처 둘·ignoreModifiers 시험·우리 코드 303 음성 | 설계 29 §4 스텝 3. **강제 지점**: \`JavaRulesTest.declarationOrderIgnoreModifiers\`(골든 \`java-order-ignore.json\`)·\`ownSourcesAreInOrder\`. **건드리는 자리**: \`fixtures/check/java/{PosOrderEdge,NegOrderEdge}.java\`·\`JavaRulesTest\`·골든 둘. 청크 규칙 초과 — 같은 이유 | 5-32a | 대기 |`
  `| 5-32c | ProfileStore 가 codecheck.rules 의 params 덮어쓰기를 지킨다 | 설계 29 §4 스텝 4. **사실**: 켬·끔만 온 규칙의 옛 값이 \`{regex}\` 맵일 때만 보존(\`saveCodeCheck\`) — \`{params}\` 는 화면에서 한 번 끄면 사라진다. **강제 지점**: \`ProfileStoreTest.saveCodeCheckKeepsParams\`. **건드리는 자리**: \`ProfileStore\`·\`ProfileStoreTest\`·\`profiles/example.yaml\` 주석 | 5-32a | 대기 |`
  `| 5-32d | corpus 골든 갱신 — check-{egov,commons,jspwiki,roller} | 설계 29 §4 스텝 5. 새 키·히트 목록 추가만, diff 를 이력에 | 5-32a | 대기 |`
  `| 5-32e | Checkstyle 차이 비교 — DeclarationOrderCorpusTest | 설계 29 §3·§4 스텝 6. **결정**: checkstyle 12.3.1 test 의존성(slf4j-simple·doxia 제외, \`checkstyle.version\` 속성으로 플러그인과 공유), 두 엔진에 같은 UTF-8 글, 차이는 B baseline(2%)·못 읽은 파일은 conformance. **강제 지점**: \`DeclarationOrderCorpusTest\`(\`@Tag corpus\`). **건드리는 자리**: \`pom.xml\`·새 시험 1·\`golden/corpus/order-{diff,diff-ignore,skipped}.txt\`. pom 이라 db 레인 — 마무리에 \`--db\` | 5-32a | 대기 |`
- 검증: `bash scripts/verify.sh` 초록(docs). 커밋 `docs: S0 번들 46 설계 29 반영`.
- 의존: 0.

**스텝 2 — 5-32a 규칙·판정·기본 픽스처·골든**
- 대상 ①: `src/main/resources/check/rules.yaml` — `java.dupMapping` 항목(`:658-664`) 뒤, `java.parseError` 앞에 삽입:
  ```yaml
    - id: java.declarationOrder
      group: java
      severity: info
      kind: java
      globs: ['*.java']
      message: '선언 순서 — static 변수 → 인스턴스 변수 → 생성자 → 메서드, 같은 종류 안 public → protected → package → private'
      fix: '선언을 순서대로 옮긴다 — 초기화식이 읽는 필드는 그보다 앞에'
      ref: 'Java Code Conventions 3.1.3 · Checkstyle DeclarationOrder'
      params: {ignoreModifiers: false}
  ```
- 대상 ②: `src/main/java/kr/ejg/toolbox/core/check/JavaRules.java`
  - import 추가(기존 줄 사이, 알파벳순): `com.github.javaparser.ast.AccessSpecifier` · `com.github.javaparser.ast.NodeList` · `com.github.javaparser.ast.body.BodyDeclaration` · `com.github.javaparser.ast.body.CompactConstructorDeclaration` · `com.github.javaparser.ast.body.ConstructorDeclaration` · `com.github.javaparser.ast.body.EnumConstantDeclaration` · `com.github.javaparser.ast.body.TypeDeclaration` · `com.github.javaparser.ast.expr.ObjectCreationExpr`.
  - 상수(우리 코드에도 선언 순서 검사가 켜져 있다 — 자리를 지킨다): `PARSE_ERROR`(`:35`, public) 바로 뒤에 `public static final String ORDER = "java.declarationOrder";` · `LOGGER`(`:43`, private) 뒤에
    ```java
    /** 선언 순서 단계(0-61e Checkstyle DeclarationOrder 와 같은 판정, 설계 29) */
    private static final int STATIC = 0;
    private static final int INSTANCE = 1;
    private static final int CTOR = 2;
    private static final int METHOD = 3;
    ```
  - `apply`(`:90-92` 클래스 루프 뒤, catch 블록 앞)에:
    ```java
    if (on.containsKey(ORDER)) {
        boolean mods = !Boolean.TRUE.equals(on.get(ORDER).params().get("ignoreModifiers"));
        for (TypeDeclaration<?> t : cu.findAll(TypeDeclaration.class)) {
            order(t.getMembers(), mods, c);
        }
        for (EnumConstantDeclaration e : cu.findAll(EnumConstantDeclaration.class)) {
            order(e.getClassBody(), mods, c);
        }
        for (ObjectCreationExpr o : cu.findAll(ObjectCreationExpr.class)) {
            o.getAnonymousClassBody().ifPresent(b -> order(b, mods, c));
        }
    }
    ```
  - 새 구획(`// ---- catch` 앞에 `// ---------------------------------------------------------------- 선언 순서`):
    ```java
    /**
     * 선언 순서(5-32) — static 변수 → 인스턴스 변수 → 생성자 → 메서드, 같은 종류 안 public → protected → package → private.
     * 중첩 타입·초기화 블록·애너테이션 멤버는 자리를 안 바꾼다. 자리가 틀린 필드는 접근자 지적을 따로 안 낸다(한 필드에 하나).
     * {@code mods} 가 false(params.ignoreModifiers) 면 접근자 순서를 안 보고, 생성자·메서드 전의 「인스턴스 뒤 static」 도 허용한다
     */
    private void order(NodeList<BodyDeclaration<?>> members, boolean mods, Ctx c) {
        int stage = STATIC;
        int access = 0;
        for (BodyDeclaration<?> m : members) {
            if (m instanceof FieldDeclaration f) {
                boolean valid = true;
                if (f.isStatic()) {
                    if (stage > STATIC && (mods || stage > INSTANCE)) {
                        add(c, ORDER, f);
                        valid = false;
                    }
                } else if (stage > INSTANCE) {
                    add(c, ORDER, f);
                    valid = false;
                } else if (stage == STATIC) {
                    stage = INSTANCE;
                    access = 0;
                }
                int r = rank(f.getAccessSpecifier());
                if (access > r) {
                    if (valid && mods) {
                        add(c, ORDER, f);
                    }
                } else {
                    access = r;
                }
            } else if (m instanceof ConstructorDeclaration || m instanceof CompactConstructorDeclaration) {
                if (stage > CTOR) {
                    add(c, ORDER, m);
                } else {
                    stage = CTOR;
                }
            } else if (m instanceof MethodDeclaration) {
                stage = METHOD;
            }
        }
    }

    private static int rank(AccessSpecifier a) {
        return switch (a) {
            case PUBLIC -> 0;
            case PROTECTED -> 1;
            case NONE -> 2;
            case PRIVATE -> 3;
        };
    }
    ```
- 대상 ③: 새 `src/test/resources/fixtures/check/java/PosOrder.java` — **글자 그대로**(줄 번호가 골든):
  ```java
  package egov.pos.order;

  /** 5-32 — 선언 순서 양성: 접근자 역순·인스턴스 뒤 static·익명 클래스 몸체·애너테이션 줄·메서드 뒤 생성자·인스턴스·enum 상수 몸체 */
  public class PosOrder {
      private static final int A = 1;
      static final int B = 2;
      private int c;
      public int d;
      static int e;

      public PosOrder() {
      }

      void run() {
          Runnable r = new Runnable() {
              public void run() {
              }
              private int z;
          };
      }

      @Deprecated
      private static final int F = 3;

      PosOrder(int x) {
      }

      int g;

      enum Kind {
          X {
              void m() {
              }
              int y;
          };
          abstract void m();
      }
  }
  ```
  기대 지적 여덟(전부 `java.declarationOrder`): 6·8·9·18·22·25·28·34.
- 대상 ④: 새 `src/test/resources/fixtures/check/java/NegOrder.java`:
  ```java
  package egov.neg.order;

  /** 5-32 — 선언 순서 음성: 규약 순서 그대로. 중첩 타입·static 블록은 자리 무관, 생성자·메서드는 접근자 순서 안 봄 */
  public class NegOrder {
      public static final int A = 1;
      protected static final int B = 2;
      static final int C = 3;
      private static final int D = 4;
      private static final java.util.Map<String, Integer> E = new java.util.HashMap<>();

      static {
          E.put("a", A);
      }

      public int f;
      protected int g;
      int h;
      private int i;

      record Row(int id) {
      }

      public NegOrder() {
      }

      private NegOrder(int x) {
      }

      public void run() {
          Object o = new Object() {
              private int z;

              public String toString() {
                  return String.valueOf(z);
              }
          };
          o.toString();
      }

      private void helper() {
      }

      enum Kind {
          X, Y;

          private final int k = 1;

          int k() {
              return k;
          }
      }
  }
  ```
- 대상 ⑤: `src/test/java/kr/ejg/toolbox/core/check/CheckRulesTest.java:153` → `assertEquals(85, builtin.size()); // 5-32 java.declarationOrder`.
- 대상 ⑥: `src/test/resources/golden/check/java.json` — 갱신.
- 검증(차례대로):
  1. `bash scripts/mvn.sh -q -B -Dtest=JavaRulesTest -Dsurefire.failIfNoSpecifiedTests=false -Dgolden.update=true test` → `git diff src/test/resources/golden/check/java.json` 이 **PosOrder.java 여덟 줄(6·8·9·18·22·25·28·34) 추가뿐**. 아니면 7절.
  2. `bash scripts/mvn.sh -q -B -Dtest='JavaRulesTest,CheckRulesTest,CheckStoreTest,CheckRoutesTest' -Dsurefire.failIfNoSpecifiedTests=false test` 초록(음성 0·양성 전부·85·suppressed 6).
  3. `bash scripts/mvn.sh -q -B validate` 초록(우리 코드의 선언 순서 — 새 상수 자리).
  4. `bash scripts/verify.sh` 초록(끝 줄 corpus 알림은 정상).
  5. 커밋 `feat: 5-32a 코드 검사 규칙 java.declarationOrder` · PLAN 행 완료 · PROGRESS 이력(해시).
- 의존: 1.

**스텝 3 — 5-32b 가장자리 픽스처·ignoreModifiers 시험·우리 코드 음성**
- 대상 ①: 새 `src/test/resources/fixtures/check/java/PosOrderEdge.java` — **글자 그대로**:
  ```java
  package egov.pos.order;

  /** 5-32b — 선언 순서 가장자리(Checkstyle 13.4.2 실측 2026-10-21 과 같은 판정). 한 파일에 최상위 타입 여럿 */
  class Fwd {
      private static final String B = Fwd.A + "x";
      static final String A = "a";
      private int p;
      public int q;
      void m() {
      }
  }

  interface Iface {
      void m();
      String X = "x";
      int Y = 1;
  }

  class Anon {
      static final int K = 1;
      Runnable r = new Runnable() {
          public void run() {
          }
          private int z;
      };
      void m() {
          Object o = new Object() {
              public String toString() {
                  return "";
              }
              static final int W = 2;
          };
      }
  }

  record Rec(int a) {
      public Rec {
      }
      static final int K = 1;
      void m() {
      }
      private static int z;
  }

  enum En {
      A, B;
      void m() {
      }
      private static final int K = 1;
      private int x;
      En() {
      }
  }

  class Mods {
      private int a;
      static int s;
      public int b;
      Mods() {
      }
      static int t;
      void m() {
      }
      int c;
  }

  class Nested {
      void m() {
      }
      static class In {
          void x() {
          }
          static int k;
      }
      static int k2;
      static {
      }
      int i;
  }

  class Reset {
      private static int s1;
      public int i1;
      static int s3;
      protected int i2;
      private Reset() {
      }
      public Reset(int a) {
      }
      private void pm() {
      }
      public void qm() {
      }
  }

  class Grp {
      static int s1;
      private static int s2;
      static int s3;
      public static int s4;
      @Deprecated
      private int i1;
      int i2;
  }

  class Local {
      void m() {
          class L {
              void y() {
              }
              static final int Q = 1;
          }
      }
  }
  ```
  기대 지적(기본) 24줄: 6·8·15·16·24·31·39·42·49·50·51·57·58·61·64·73·75·78·84·85·99·100·103·111. (`ignoreModifiers`) 15줄: 15·16·24·31·39·42·49·50·51·61·64·73·75·78·111.
- 대상 ②: 새 `src/test/resources/fixtures/check/java/NegOrderEdge.java` — **글자 그대로**:
  ```java
  package egov.neg.order;

  /** 5-32b — 선언 순서 음성 가장자리: 애너테이션 멤버는 메서드가 아니다 · 인터페이스 상수 뒤 메서드 · record 는 static 뒤 compact 생성자 · enum 생성자 뒤 메서드 · 중첩 타입 어디든 */
  @interface Ann {
      int x() default 1;
      String K = "k";
  }

  interface Api {
      String X = "x";
      int Y = 1;
      void m();
      default void n() {
      }
  }

  record Pair(int a, int b) {
      static final int K = 1;
      public Pair {
      }
      int sum() {
          return a + b;
      }
  }

  enum Color {
      RED, BLUE;
      private static final int K = 1;
      private final int v = 2;
      Color() {
      }
      int v() {
          return v + K;
      }
  }

  class Outer {
      static class First {
      }
      static final int A = 1;
      private int b;
      Outer() {
      }
      class Inner {
          private int c;
          int c() {
              return c;
          }
      }
      void m() {
      }
  }
  ```
- 대상 ③: `JavaRulesTest.java` 에 시험 둘(기존 `profileNaming` 뒤). import 는 필요한 것만 더한다(`java.nio.file.Files`·`java.nio.charset.StandardCharsets`·`java.util.stream.Stream`).
  ```java
  /** 5-32b — params.ignoreModifiers: 접근자 순서를 안 보고, 생성자·메서드 전의 「인스턴스 뒤 static」 도 허용. 같은 픽스처를 덮어쓰기 프로필로 */
  @Test
  void declarationOrderIgnoreModifiers(@TempDir Path data) throws Exception {
      Profile p = new Profile("t", new Profile.Project(null, "UTF-8", "LF", null), null, null, null, null, null,
              new Profile.CodeCheck(ONLY_JAVA, Map.of(JavaRules.ORDER, Map.of("params", Map.of("ignoreModifiers", true))), null),
              "egov35", null, null, null);
      List<Map<String, Object>> golden = new ArrayList<>();
      for (Finding f : run(RuleSet.load(p, null), data)) {
          if (f.rule().equals(JavaRules.ORDER)) {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("file", f.file());
              row.put("line", f.line());
              golden.add(row);
          }
      }
      GoldenFiles.assertJson("check/java-order-ignore.json", golden);
  }

  /** 5-32b — 우리 코드(0-61e 가 Checkstyle 로 0 을 보증)는 이 규칙으로도 0 — 음성 303 파일 */
  @Test
  void ownSourcesAreInOrder() throws Exception {
      Rule.Def def = RuleSet.builtin().defs().stream().filter(d -> d.id().equals(JavaRules.ORDER)).findFirst().orElseThrow();
      List<String> bad = new ArrayList<>();
      for (Path root : List.of(Path.of("src/main/java"), Path.of("src/test/java"))) {
          try (Stream<Path> s = Files.walk(root)) {
              for (Path p : s.filter(x -> x.toString().endsWith(".java")).sorted().toList()) {
                  String rel = root.relativize(p).toString().replace('\\', '/');
                  for (Finding f : new JavaRules(List.of(def), null).apply(new Source(rel, Files.readString(p, StandardCharsets.UTF_8), "UTF-8", "LF", "java"))) {
                      bad.add(rel + ":" + f.line());
                  }
              }
          }
      }
      assertEquals(List.of(), bad, "0-61e 와 같은 판정이어야 한다");
  }
  ```
- 대상 ④: 골든 `golden/check/java.json`(PosOrderEdge 24줄 추가)·새 `golden/check/java-order-ignore.json`(PosOrder 5줄 + PosOrderEdge 15줄).
- 검증:
  1. `bash scripts/mvn.sh -q -B -Dtest=JavaRulesTest -Dsurefire.failIfNoSpecifiedTests=false -Dgolden.update=true test` → `java.json` diff 가 PosOrderEdge 24줄 추가뿐, `java-order-ignore.json` 이 PosOrder 5줄·PosOrderEdge 15줄(위 목록) 정확히. 아니면 7절.
  2. 같은 명령에서 `-Dgolden.update` 빼고 초록(`ownSourcesAreInOrder` 0 포함) → `bash scripts/verify.sh` 초록.
  3. 커밋 `test: 5-32b 선언 순서 가장자리·ignoreModifiers·우리 코드 음성`.
- 의존: 2.

**스텝 4 — 5-32c ProfileStore params 보존**
- 대상 ①: `src/main/java/kr/ejg/toolbox/core/profile/ProfileStore.java:140`·`:148`.
  - 주석 `:140` → `// 들어온 값이 켬·끔(Boolean)뿐이고 앞 값이 {enabled, regex}·{params} 맵이면 정규식·params 는 남기고 enabled 만 바꾼다(5-32c)`
  - `:148` → `if (v instanceof Boolean on && old instanceof java.util.Map<?, ?> m && (m.containsKey("regex") || m.containsKey("params"))) {`
- 대상 ②: `ProfileStoreTest.java` — `saveCodeCheckMergesRules`(`:243-254`) 뒤에:
  ```java
  /** 5-32c — 손으로 적은 params 덮어쓰기(java.declarationOrder ignoreModifiers)는 화면에서 켬·끔을 바꿔도 남는다 */
  @Test
  void saveCodeCheckKeepsParams(@TempDir Path dir) throws Exception {
      Path profiles = Files.createDirectories(dir.resolve("p"));
      Files.writeString(profiles.resolve("a.yaml"),
              "name: a\ncodecheck:\n  rules: {\"java.declarationOrder\": {\"params\": {\"ignoreModifiers\": true}}}\n", StandardCharsets.UTF_8);
      ProfileStore store = new ProfileStore(profiles, dir.resolve("data"));
      Profile saved = store.saveCodeCheck("a", java.util.Map.of(), java.util.Map.of("java.declarationOrder", false));
      assertEquals(java.util.Map.of("params", java.util.Map.of("ignoreModifiers", true), "enabled", false),
              saved.codecheck().rules().get("java.declarationOrder"), "켬·끔만 바꾸면 params 는 남는다");
      assertEquals(saved, store.load("a"));
  }
  ```
- 대상 ③: `profiles/example.yaml:132` 주석 → `  # 규칙 하나씩 덮어쓰기. 예) { common.sysout: false, common.todo: { severity: info }, java.declarationOrder: { params: { ignoreModifiers: true } } }`
- 검증: `bash scripts/mvn.sh -q -B -Dtest=ProfileStoreTest -Dsurefire.failIfNoSpecifiedTests=false test` 초록 → `bash scripts/verify.sh` 초록. 커밋 `fix: 5-32c 프로필 규칙 params 덮어쓰기 보존`.
- 의존: 2.

**스텝 5 — 5-32d corpus 골든 갱신(CheckCorpusTest)**
- 대상: `src/test/resources/golden/corpus/check-{egov,commons,jspwiki,roller}.json` · `check-{egov,commons,jspwiki,roller}-hits.txt`.
- 변경: `bash scripts/corpus-check.sh` 초록 확인 → `bash scripts/mvn.sh -q -B -Dgroups='corpus & !db' -Dtest=CheckCorpusTest -Dsurefire.failIfNoSpecifiedTests=false -Dgolden.update=true test`.
- 검증: `git diff --stat src/test/resources/golden/corpus` 가 위 여덟 파일 안이고, `.json` diff 는 `"java.declarationOrder" : { "count" : N, "files" : M }` 키 **추가뿐**, `-hits.txt` 는 줄 **추가뿐**(삭제 0). 아니면 7절. 무리별 count·files 를 이력에. 커밋 `test: 5-32d corpus 골든 — java.declarationOrder`.
- 의존: 2.

**스텝 6 — 5-32e Checkstyle 차이 비교 corpus 시험**
- 대상 ①: `pom.xml`
  - `<properties>`(`:12-26`)에 `<checkstyle.version>12.3.1</checkstyle.version>` 한 줄(주석: `<!-- 0-61e 플러그인 + 5-32e 시험 의존성 — 한 자리 -->`).
  - 0-61e 플러그인 블록의 `<version>12.3.1</version>` → `<version>${checkstyle.version}</version>`.
  - test 의존성 블록들 끝(archunit `:138-141` 뒤 어디든 `<dependencies>` 안)에:
    ```xml
        <dependency>
          <!-- 5-32e — 코드 검사 규칙 java.declarationOrder 를 표본에서 Checkstyle 판정과 맞댄다(시험만). 판은 0-61e 플러그인과 같다.
               slf4j-simple 은 logback 과 바인딩이 겹쳐 빼고, doxia 둘은 문서 생성용이라 뺀다 -->
          <groupId>com.puppycrawl.tools</groupId>
          <artifactId>checkstyle</artifactId>
          <version>${checkstyle.version}</version>
          <scope>test</scope>
          <exclusions>
            <exclusion>
              <groupId>org.slf4j</groupId>
              <artifactId>slf4j-simple</artifactId>
            </exclusion>
            <exclusion>
              <groupId>org.apache.maven.doxia</groupId>
              <artifactId>doxia-core</artifactId>
            </exclusion>
            <exclusion>
              <groupId>org.apache.maven.doxia</groupId>
              <artifactId>doxia-module-xdoc</artifactId>
            </exclusion>
          </exclusions>
        </dependency>
    ```
- 대상 ②: 새 `src/test/java/kr/ejg/toolbox/core/check/DeclarationOrderCorpusTest.java`:
  ```java
  package kr.ejg.toolbox.core.check;

  import static org.junit.jupiter.api.Assertions.assertTrue;

  import com.puppycrawl.tools.checkstyle.Checker;
  import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
  import com.puppycrawl.tools.checkstyle.api.AuditEvent;
  import com.puppycrawl.tools.checkstyle.api.AuditListener;
  import java.io.File;
  import java.nio.charset.StandardCharsets;
  import java.nio.file.Files;
  import java.nio.file.Path;
  import java.util.ArrayList;
  import java.util.List;
  import java.util.Map;
  import java.util.Set;
  import java.util.TreeMap;
  import java.util.TreeSet;
  import kr.ejg.toolbox.CorpusFiles;
  import kr.ejg.toolbox.core.fs.LocalFiles;
  import org.junit.jupiter.api.Tag;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.io.TempDir;

  /**
   * 5-32e — {@code java.declarationOrder} 를 실물 표본 Java 전부에서 Checkstyle {@code DeclarationOrder}(0-61e 와 같은 판)와 맞댄다.
   * 두 엔진에 같은 글을 준다 — {@link LocalFiles#read} 로 인코딩을 판별해 읽은 글을 임시 폴더에 UTF-8 로 쓰고 Checkstyle 은 그 파일을,
   * 우리 규칙은 그 글을 본다. 차이(파일:줄, 어느 쪽만)는 등급 B baseline, 어느 쪽이든 못 읽은 파일은 conformance 목록.
   * {@code ignoreModifiers} 모드는 egov·jspwiki 두 원천만(실행 시간).
   */
  @Tag("corpus")
  class DeclarationOrderCorpusTest {

      /** corpus/MANIFEST 의 Java 원천(2026-10-21 7,173 파일). egov-prev 는 egov 의 옛 판 사본이라 그대로 둔다 */
      static final List<String> SOURCES = List.of("egov", "egov-prev", "egov-portal", "egov-enterprise", "egov-homepage", "jspwiki", "roller",
              "struts", "commons-lang", "commons-io", "commons-collections", "shopizer", "egov-msa");
      static final List<String> IGNORE_SOURCES = List.of("egov", "jspwiki");

      @TempDir
      Path tmp;

      @Test
      void matchesCheckstyle() throws Exception {
          CorpusFiles.verify();
          Rule.Def order = RuleSet.builtin().defs().stream().filter(d -> d.id().equals(JavaRules.ORDER)).findFirst().orElseThrow();
          Rule.Def parse = RuleSet.builtin().defs().stream().filter(d -> d.id().equals(JavaRules.PARSE_ERROR)).findFirst().orElseThrow();
          List<String> skipped = new ArrayList<>();
          List<String> diff = new ArrayList<>();
          int total = compare(SOURCES, order, parse, false, skipped, diff);
          List<String> diffIgnore = new ArrayList<>();
          int totalIgnore = compare(IGNORE_SOURCES, order.override(Map.of("params", Map.of("ignoreModifiers", true))), parse, true,
                  new ArrayList<>(), diffIgnore);
          CorpusFiles.conformance("order-skipped", skipped);
          CorpusFiles.baseline("order-diff", diff, total);
          CorpusFiles.baseline("order-diff-ignore", diffIgnore, totalIgnore);
          assertTrue(total > 5000, "표본 Java 가 너무 적다: " + total);
      }

      /** 원천들을 두 엔진으로 돌려 차이를 모은다. 돌려주는 값은 비교한 파일 수 */
      private int compare(List<String> sources, Rule.Def order, Rule.Def parse, boolean ignoreModifiers, List<String> skipped, List<String> diff)
              throws Exception {
          LocalFiles lf = new LocalFiles(tmp.resolve("data"));
          int total = 0;
          for (String source : sources) {
              Path dir = CorpusFiles.root().resolve(source);
              Path copy = tmp.resolve(ignoreModifiers ? "ign" : "def").resolve(source);
              Map<String, Set<Integer>> ours = new TreeMap<>();
              Set<String> failed = new TreeSet<>();
              List<File> files = new ArrayList<>();
              for (Path p : CorpusFiles.files(source, "*.java")) {
                  String rel = CorpusFiles.rel(p);
                  LocalFiles.Text t = lf.read(p.toString());
                  Path c = copy.resolve(dir.relativize(p));
                  Files.createDirectories(c.getParent());
                  Files.writeString(c, t.text(), StandardCharsets.UTF_8);
                  files.add(c.toFile());
                  Set<Integer> lines = new TreeSet<>();
                  for (Finding f : new JavaRules(List.of(order, parse), null).apply(new Source(rel, t.text(), "UTF-8", t.lineEnding(), "java"))) {
                      if (f.rule().equals(JavaRules.PARSE_ERROR)) {
                          failed.add(rel);
                      } else {
                          lines.add(f.line());
                      }
                  }
                  ours.put(rel, lines);
              }
              Map<String, Set<Integer>> theirs = checkstyle(files, copy, source, ignoreModifiers, failed);
              for (String rel : ours.keySet()) {
                  if (failed.contains(rel)) {
                      skipped.add(rel);
                      continue;
                  }
                  total++;
                  Set<Integer> a = ours.get(rel);
                  Set<Integer> b = theirs.getOrDefault(rel, Set.of());
                  for (int l : a) {
                      if (!b.contains(l)) {
                          diff.add(rel + ":" + l + " 우리만");
                      }
                  }
                  for (int l : b) {
                      if (!a.contains(l)) {
                          diff.add(rel + ":" + l + " checkstyle만");
                      }
                  }
              }
          }
          return total;
      }

      /** Checkstyle 12.3.1 을 안에서 — 파일 → 지적 줄. 예외(못 읽음 등)가 난 파일은 failed 에 */
      private static Map<String, Set<Integer>> checkstyle(List<File> files, Path copy, String source, boolean ignoreModifiers, Set<String> failed)
              throws Exception {
          DefaultConfiguration rule = new DefaultConfiguration("DeclarationOrder");
          if (ignoreModifiers) {
              rule.addProperty("ignoreModifiers", "true");
          }
          DefaultConfiguration walker = new DefaultConfiguration("TreeWalker");
          walker.addChild(rule);
          DefaultConfiguration root = new DefaultConfiguration("Checker");
          root.addProperty("charset", "UTF-8");
          root.addProperty("localeLanguage", "en");
          root.addChild(walker);
          Map<String, Set<Integer>> out = new TreeMap<>();
          Checker checker = new Checker();
          checker.setModuleClassLoader(Checker.class.getClassLoader());
          checker.configure(root);
          checker.setHaltOnException(false);
          checker.addListener(new AuditListener() {
              @Override
              public void addError(AuditEvent e) {
                  String rel = source + "/" + copy.relativize(Path.of(e.getFileName())).toString().replace('\\', '/');
                  if (e.getViolation() != null && e.getViolation().getKey().startsWith("declaration.order")) {
                      out.computeIfAbsent(rel, k -> new TreeSet<>()).add(e.getLine());
                  } else {
                      failed.add(rel);
                  }
              }

              @Override
              public void addException(AuditEvent e, Throwable t) {
                  failed.add(source + "/" + copy.relativize(Path.of(e.getFileName())).toString().replace('\\', '/'));
              }

              @Override
              public void auditStarted(AuditEvent e) {
              }

              @Override
              public void auditFinished(AuditEvent e) {
              }

              @Override
              public void fileStarted(AuditEvent e) {
              }

              @Override
              public void fileFinished(AuditEvent e) {
              }
          });
          try {
              checker.process(files);
          } finally {
              checker.destroy();
          }
          return out;
      }
  }
  ```
  (`AuditEvent.getFileName()` 이 절대 경로가 아니면 `copy.relativize` 가 던진다 — 그때는 `Path.of(e.getFileName()).toAbsolutePath()` 로. `addException` 의 `e.getFileName()` 이 null 이면 `source + "/?"` 로 넣는다.)
- 대상 ③: 새 `golden/corpus/order-diff.txt`·`order-diff-ignore.txt`·`order-skipped.txt`(갱신으로 생성).
- 검증(차례대로):
  1. `bash scripts/mvn.sh -q -B -DskipTests test-compile` 초록(의존성 해석·컴파일). 의존성 해석이 네트워크를 타려 하면(`m2/` 밖) 7절.
  2. `bash scripts/corpus-check.sh` → `bash scripts/mvn.sh -q -B -Dgroups='corpus & !db' -Dtest=DeclarationOrderCorpusTest -Dsurefire.failIfNoSpecifiedTests=false -Dgolden.update=true test` → 셋이 생긴다. `order-diff.txt` 를 **줄마다** 본다: ① `우리만`·`checkstyle만` 쌍이 같은 파일 같은 줄이면 없는 것(둘 다 썼으면 집합 비교 버그 — 고친다) ② 2절 실측 규칙에 비춰 우리가 틀린 것은 `JavaRules.order` 를 고치고 다시 갱신(10건까지) ③ Checkstyle 쪽 특이 판정은 남기고 사유를 이력에. `order-skipped.txt` 는 JavaParser 한계(`CheckCorpusTest` 의 `check-parse-limit.txt` 와 비슷한 목록이어야 한다)·Checkstyle 이 못 읽은 파일.
  3. 같은 명령에서 `-Dgolden.update` 빼고 초록. 실행 시간을 재서 이력에(3분을 넘으면 `IGNORE_SOURCES` 는 그대로 두고 알림만).
  4. `bash scripts/offline-build.sh` 초록(규칙 5 — test 의존성이 `m2/` 만으로 풀리는지, `downloading` 없음).
  5. `bash scripts/verify.sh` 초록. 커밋 `test: 5-32e Checkstyle 차이 비교 — DeclarationOrderCorpusTest`.
- 의존: 2(5-32b 와는 독립이지만 차례는 뒤).

**스텝 7 — 마무리(CLAUDE.md 「마무리」, 7 은 위임 없음)**
- 독립 리뷰(`caveman:cavecrew-reviewer`, `git diff origin/main...HEAD`) — 2절 실측 표가 상태기계·픽스처와 맞는지, 차이 비교가 두 엔진에 같은 글을 주는지 보게 한다.
- `bash scripts/gate-probe.sh`(열 산다) → `bash scripts/verify.sh --corpus`(약 7분 + 새 시험) → `bash scripts/verify.sh --db`(약 17분, pom — 메모리·`docker ps` 확인 후, 다른 세션 컨테이너가 있으면 사용자에게 먼저) — push 와 따로.
- PROGRESS 「현재 상태」 가지·번들 46 갱신, PLAN 번들 46 행 완료 → 빠른 verify → 커밋 `docs: 번들 46 마무리`.
- `git push -u origin work/2026-10-22` → `gh pr create --base main`(청크 표 + 「새 의존성: test 스코프 checkstyle 12.3.1, m2 오프라인 확인」) → CI 폴링 → claude[bot] 지적 처분 → 멈춤.
- 의존: 6.

## 5. 금지 사항
- `pure/`·`golden/check/{fixtures,pure-rules}.json`·`check-parse-limit.txt`·화면 JS·`Ignores`·`RuleSet` 디스패치·`config/checkstyle/declaration-order.xml` 수정 금지.
- 판정 완화로 corpus 건수나 차이를 줄이기 금지(익명 클래스·enum 몸체 빼기, 차이 나는 케이스를 특례로 넣기). 다른 Checkstyle 규칙 추가 금지.
- 기존 픽스처(`Pos*`·`Neg*`·`ignore/*`) 내용 변경 금지. 새 픽스처 넷은 4절 글자 그대로(줄 번호가 골든) — 한 줄이라도 바꾸면 기대 줄 목록도 같이.
- 골든·baseline 은 `-Dgolden.update` 로만, diff 를 보고 추가만인지 확인한 뒤 커밋. `order-diff.txt` 가 비어 있지 않으면 줄마다 사유 없이 커밋 금지.
- checkstyle 을 compile 스코프로 두기·`slf4j-simple` 을 들이기·13.x 판 금지. 플러그인 판과 시험 판을 따로 두기 금지(`checkstyle.version` 하나).
- `JavaRules` 에 새 static 을 private static 무리 앞에 두는 등 우리 저장소의 선언 순서 검사(0-61e)를 어기기 금지 — `mvn validate` 가 막는다.
- `verify.sh` 와 `git commit`, `--corpus`·`--db` 와 `git push` 한 체인 금지. 맨몸 `mvnw` 금지.

## 6. 최종 검증
- `JavaRulesTest` 전부(음성 0·양성 전부·`java.json` 32줄 추가·`java-order-ignore.json` 20줄·우리 코드 0) · `CheckRulesTest` 85 · `ProfileStoreTest.saveCodeCheckKeepsParams`.
- `bash scripts/verify.sh` 초록 · `--corpus` 초록(`CheckCorpusTest` 골든 일치·`DeclarationOrderCorpusTest` baseline 일치·A 0) · `--db` 초록 · `offline-build.sh` 초록.
- 손 확인 하나(실행자): `run.bat` 뒤 코드 검사 화면에서 `src/test/resources/fixtures/check/java` 를 검사 → `PosOrder.java` 8건·`PosOrderEdge.java` 24건이 info 로 나온다. 또는 `toolbox.bat check <그 폴더 절대 경로>`.
- CI check-runs 전부 `completed success`.

## 7. 중단 조건
- 스텝 2·3 검증 1 에서 골든 diff 가 기대 줄 목록과 다르고, 한 번 고쳐서 안 맞음(판정이 2절 실측과 다른 것).
- 스텝 5 에서 `-hits.txt` 에 삭제 줄이 생기거나 `.json` 에 다른 규칙의 count 가 변함.
- 스텝 6 에서 `order-diff.txt` 가 비교 파일의 2% 를 넘거나, 우리 쪽 고침이 10건을 넘음, 또는 의존성 해석이 `m2/` 밖을 요구함(`offline-build.sh` 빨강).
- `CheckStoreTest` suppressed/parseErrors 가 바뀜(기존 픽스처가 새 규칙에 걸린 것 — 실측과 어긋남).
- `JavaRules`·시험 컴파일 실패가 한 번 고쳐서 안 풀림(API 는 2절에서 jar 로 확인했다).
- `--db`·`--corpus` 가 메모리로 끊김 → 다시 안 돌리고 고아 정리 뒤 보고(메모리 `full-verify-memory-contention`).
- 검증 2회 연속 실패·원인이 플랜 밖·스텝에 없는 파일 3개 이상.
- 보고: 어느 스텝, 예상과 다른 것, 선택지.

