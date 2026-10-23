# 설계 34 — 번들 51: 코드 검사 묶음 「ㅅ 외부 분석기」 — SpotBugs + FindSecBugs 내장 (Fable 설계 · Opus 실행, 2026-10-23)

## 맥락
설계 32·33 과 같은 날. 코드 검사는 정규식·JavaParser 로 한 파일 안의 꼴만 본다 — null 역참조·자원 안 닫음·`equals` 오용 같은 **실행하면 터지는 결함**과, 입력이 메서드를 건너 SQL 로 가는 **흐름**은 못 본다. SpotBugs(바이트코드 버그 패턴 400+)와 FindSecBugs(보안 패턴 130+, 오염 추적)는 이 저장소가 빌드 때 자기 코드에 이미 쓰는 엔진(`pom.xml` `spotbugs-maven-plugin` 4.9.3.0 + `findsecbugs-plugin` 1.14.0, 제외는 `config/spotbugs/exclude.xml`). 이 번들은 그 엔진을 `app.jar` 에 넣어 현장에서 **사업의 빌드 산출물**(`WEB-INF/classes`)을 대상으로 돌린다. PLAN 3.1 표의 ㅅ(PMD) 자리를 「외부 분석기」로 바꾼다 — PMD(5-7)는 그 안의 둘째 엔진 후보로 조건부 그대로.

전제(사용자에게 알린다): 현장에 **컴파일된 클래스**가 있어야 한다(소스만 있으면 묶음 ㅅ 은 아무것도 안 낸다). 줄 번호는 디버그 정보(`-g`, Maven 기본)에서 온다.

실행자는 Opus. 차례는 `S0`(설계 32 의 S0) → `5-38a` → `5-38b` → `5-38c` → `5-38d` → `5-38e`(sonar-findbugs 등급표 대조 — 네트워크) → 마무리. 비교 방식은 설계 32 머리와 같다(만든 뒤 기계로 맞대고 아침에 사람이 고른다). 가지 `work/<날짜>`(번들 50 머지 뒤 — 49 와는 `rules.yaml`·`code_check_ext.js` 를 같이 고치므로 뒤에). 머지 위임(사용자 2026-10-23 — 셋 다 이어서: CI 초록·AI 리뷰 처분 뒤 머지하고 다음 번들로).

---

## 1. 목표와 범위
- 목표: 코드 검사 규칙 묶음 「ㅅ 외부 분석기」(규칙 둘 `ext.spotbugs`·`ext.findsecbugs`)를 켜고 「클래스 폴더」를 주면 SpotBugs·FindSecBugs 결과가 같은 결과 표(파일·줄·규칙·등급)에 섞여 나오고 이력·xlsx·추이에 든다.
- In scope: 의존성(런타임) · `core/check/SpotBugsRun` · kind `external` · `CheckRunner` 연결 · 요청·프로필 `classesDir`·`libDir` · 화면 입력 칸 · 시험(JavaCompiler 로 픽스처 컴파일) · 반입 문서·SOURCES·m2.
- Out of scope: PMD · 소스에서 컴파일하기 · SpotBugs 제외 필터 편집 UI(프로필 `params.exclude` 패턴 목록으로 충분) · 순수본 · `tb-ignore` 로 외부 지적 억제(외부 지적은 소스 줄을 안 읽는다 — 문서에 적는다).

## 2. 현재 구조 요약
- `RuleSet.load` 가 kind 로 가른다(설계 32 §2) — 새 kind `external` 은 `Rule` 이 아니다(marker·dup 처럼 정의만 들고 `externalDefs`). `globs()` 에는 안 넣는다(파일 목록과 무관).
- `CheckRunner.run(root, rules, files, ctx, changed, exclude)` → 파일 루프 → `run.finish()` → 정렬 → `RunResult`. 외부 분석은 이 뒤에 붙는다(입력이 다르다 — 클래스 폴더).
- `CheckRoutes.RunRequest(path, text, lang, changedOnly, groups, ruleOverrides)` · `Profile.CodeCheck(groups, rules, customRules, exclude)`(생성자 둘 — 3인자·4인자, 호출 21곳) · `Profile.Project(root, encoding, lineEnding, vcs)`.
- 반입: `scripts/package.sh` 가 `dependency:list -DincludeScope=runtime` 으로 `docs/dependencies.txt` 와 「동봉 jar」 수를 만든다 · `docs/import-review.md` 8절 「런타임 의존성 47 개」·9절 정적 검사 · `bundle/SOURCES.md`(반입 재료 출처·라이선스) · `m2/` 는 `.gitignore`, `scripts/offline-build.sh` 가 `dependency:go-offline` 으로 채운다(절대 규칙 5) · shade 는 `META-INF/*.SF|DSA|RSA`·`module-info.class` 만 뺀다, `ServicesResourceTransformer`.
- 레인: `pom.xml` 은 db 레인 → 마무리에 `--db`. `core/check`·tools 는 corpus → `--corpus`.

### 실측(2026-10-23, 설계 세션 — `~/.m2` 의 jar 로 프로그램 API 호출, 저장소 밖)
- 대상 이 저장소 `target/classes` 437 클래스, aux 는 javalin·jackson·h2·poi·javaparser·picocli 여섯 jar 만: **23초**, 지적 179(SECURITY 132 · STYLE 24 · BAD_PRACTICE 12 · MALICIOUS_CODE 6 · I18N 4 · PERFORMANCE 1), 줄 번호 없는 것 3(메서드 선언 단위 지적 `THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION`), 못 찾은 클래스 49(aux 에 없는 freemarker·jackson-core·jetty — 결과엔 영향 없이 경고), 힙 340MB.
- 유형 상위: `IMPROPER_UNICODE` 90(FindSecBugs — `toLowerCase` 마다, 소음) · `CRLF_INJECTION_LOGS` 12 · `MODIFICATION_AFTER_VALIDATION` 11 · `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` 10 · `UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR` 9 · `SQL_INJECTION_JDBC` 4 · `COMMAND_INJECTION` 2 · `DMI_EMPTY_DB_PASSWORD` 2. 우선순위 3(low) 까지 받은 수다 — Maven 플러그인 기본(`threshold Default` = medium 이상)이면 훨씬 준다.
- API(그대로 쓴다): `Project.addFile(classesDir)`·`addAuxClasspathEntry(jar)` · `Plugin.loadCustomPlugin(File, Project)` → `getPluginId()`·`getDetectorFactories()` · `FindBugs2`(`setProject`·`setBugReporter(BugCollectionBugReporter)`·`setDetectorFactoryCollection(DetectorFactoryCollection.instance())`·`setUserPreferences(UserPreferences.createDefaultUserPreferences())`·`setProgressCallback(FindBugsProgress — predictPassCount(int[])·startAnalysis(int)·finishClass()·…)`·`finishSettings()`·`execute()`) · `BugInstance.getType()`·`getPriority()`(1 high·2 medium·3 low)·`getBugRank()`(1~20)·`getBugPattern().getCategory()`·`getShortDescription()`·`getMessageWithoutPrefix()`·`getPrimarySourceLineAnnotation().getSourcePath()`(`kr/ejg/toolbox/cli/Batch.java` — 패키지 경로 + `SourceFile` 속성)·`getStartLine()`(없으면 ≤ 0) · `SortedBugCollection.missingClassIterator()`.
- 의존성(4.9.3 pom, `~/.m2` 에 전부 있음): `spotbugs` 3.9MB · `spotbugs-annotations` 16KB · asm 9.7.1 다섯(asm·analysis·commons·tree·util, 합 380KB) · `bcel` 6.10.0 740KB · `dom4j` 2.1.4 325KB · `jaxen` 2.0.0 224KB(runtime — dom4j XPath, 플러그인 설명 읽기에 쓴다) · `commons-lang3` 3.17.0 674KB · `commons-text` 1.13.0 258KB · `gson` 2.12.1 286KB · `jsr305` 3.0.2 20KB · `jcip-annotations` 1.0-1(stephenc) 5KB · `slf4j-api`(이미 있음). **빼도 되는 것**(실측에서 없이 돌았다): `Saxon-HE` 12.5(5.6MB, XSLT 보고서)·`log4j-core`(runtime, logback 이 바인딩). `findsecbugs-plugin` 1.14.0 486KB — 전이 의존성 없음(pom 없이 jar 만 캐시에 있다; 플러그인은 SpotBugs 안에서 돈다).
- 합계 약 7MB(Saxon 제외). app.jar 37MB → 약 44MB.

### 표본(2026-10-23 조사 — 사용자 「50·51 용 표본을 GitHub 에서 구해 받는 것까지 플랜에」)
소스 표본은 전부 소스라 클래스가 없다. 번들 50 의 **V-29**(설계 33 §4)가 받는다:
- `jspwiki-bin`: Maven Central `org.apache.jspwiki:jspwiki-main:2.12.5`(Apache-2.0, 실측 1,067,596 바이트·클래스 384)를 `corpus/jspwiki-bin/pom.xml` 레시피로 받아 `classes/`(풀어 둔 것)·`lib/`(의존성 jar). 소스 표본 `jspwiki` 가 **같은 판 2.12.5** 라 지적의 소스 줄 매핑률을 잴 수 있다.
- `ruoyi-bin`: `ruoyi` 표본(RuoYi-Vue, MIT) 캐시 클론을 저장소 Maven 으로 `package` 해 모듈 `target/classes` 합침 + `BOOT-INF/lib`. 전형적인 Spring Boot + MyBatis 라 FindSecBugs 의 SQL·경로·XSS 패턴이 실제로 걸리는지 본다. 설계 세션이 빌드를 실측했다(§8).
둘 다 MANIFEST 밖(이름을 골라야 받는다) — 없으면 `SpotBugsCorpusTest` 는 `assumeTrue` 로 건너뛴다(CI 가 그렇다).

## 3. 설계 결정

**의존성·묶음 방식** [고정]
- `pom.xml` 런타임: `com.github.spotbugs:spotbugs:4.9.3`(exclusions `net.sf.saxon:Saxon-HE`·`org.apache.logging.log4j:log4j-core`) + `com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0` 은 **의존성으로 넣지 않고** `maven-dependency-plugin` `copy`(phase `generate-resources`, `outputDirectory ${project.build.outputDirectory}/analyzers`, `destFileName findsecbugs-plugin.jar`)로 **자원**으로 넣는다 — SpotBugs 가 플러그인을 **파일**로만 읽기 때문(`Plugin.loadCustomPlugin(File)`), 그리고 shade 로 풀면 플러그인 jar 루트의 `findbugs.xml`·`messages.xml` 이 코어의 같은 이름과 부딪힌다. 런타임에 자원을 `<dataDir>/analyzers/findsecbugs-plugin.jar` 로 한 번 꺼내(크기가 같으면 안 다시 씀) 그 파일을 로드한다.
- 코어 플러그인(`spotbugs.jar` 루트의 `findbugs.xml`·`messages.xml`·`messages_ko.xml`·`bugrank.txt`·`messageCollection.xml`·`etc/`)은 shade 로 `app.jar` 루트에 든다. SpotBugs 는 코어를 `Plugin` 클래스의 `CodeSource` 위치(= app.jar)에서 찾는다 — **5-38a 가 가장 먼저 실측한다**(§4). 안 되면 사다리: `app.jar` 밖 `analyzers/spotbugs/*.jar` 를 `URLClassLoader` 로 — 그러면 번들을 멈추고 보고(§7).
- 라이선스: SpotBugs LGPL-2.1 · FindSecBugs LGPL-3.0 · asm BSD-3 · bcel Apache-2.0 · dom4j BSD · jaxen BSD-3 · commons Apache-2.0 · gson Apache-2.0 · jsr305 BSD-3 · jcip CC-BY-2.5. 동적 링크(별도 클래스, 수정 없음)라 LGPL 조건은 고지·출처로 충족 — `bundle/SOURCES.md` 절 + `docs/import-review.md` 8절.
- 버린 대안: 외부 프로세스로 SpotBugs CLI(절대 규칙 1 의 프로세스 목록이 git·svn·브라우저뿐 — ArchUnit) · 사용자가 SpotBugs 를 따로 깔기(폐쇄망) · PMD 를 같이(5-7 조건부) · 소스 컴파일(JDK 가 반입 zip 에 없다 — jlink 런타임).

**규칙·결과 꼴** [고정]
- `rules.yaml` 절 `# ---- ㅅ 외부 분석기(5-38)`: 
  ```yaml
  - id: ext.spotbugs
    group: ext
    severity: warn            # 표시용 — 실제 등급은 우선순위로(아래)
    kind: external
    globs: ['*.class']        # 파일 목록엔 안 든다(RuleSet.globs 가 external 을 뺀다). validate 가 globs 를 요구해 둔다
    message: SpotBugs 버그 패턴(바이트코드) — 클래스 폴더를 준 폴더 검사에서만
    params: {minPriority: 2, exclude: []}   # 1 high · 2 medium(Maven 플러그인 기본) · 3 low. exclude 는 BUG_TYPE 글롭(예 'IMPROPER_UNICODE', 'SE_*')
  - id: ext.findsecbugs
    group: ext
    severity: warn
    kind: external
    globs: ['*.class']
    message: FindSecBugs 보안 패턴(오염 추적) — 클래스 폴더를 준 폴더 검사에서만
    params: {minPriority: 2, exclude: ['IMPROPER_UNICODE']}   # 실측 90/179 — toLowerCase 마다 울린다
  ```
- 결과 `Finding`: `rule` = `spotbugs.<BUG_TYPE>` / `findsecbugs.<BUG_TYPE>`(검출기의 플러그인 id 가 `com.h3xstream.findsecbugs` 면 뒤) · `group` = `ext` · `severity` = 우선순위 1 → error · 2 → warn · 3 → info · `file` = 소스 루트에서 `endsWith("/" + sourcePath)` 인 `.java` 상대 경로(색인은 한 번, 여럿이면 짧은 것) — 못 찾으면 `sourcePath` 그대로 · `line` = `getStartLine()`, ≤ 0 이면 1 · `excerpt` = `getShortDescription() + " — " + getMessageWithoutPrefix()`(클래스·메서드 이름만 든다, 저장 안 함). `rule` 100자 컷(BUG_TYPE 최장 60 안팎).
- `RuleSet`: `externalDefs`(켜진 external 정의) + `external()` 접근자. `fingerprint()` 에 든다(params 가 바뀌면 추이 지문이 바뀐다 — 설계 32).
- `CheckRunner`: 새 오버로드 `run(root, rules, files, ctx, changed, exclude, External ext)` — `record External(String classesDir, String libDir)`(둘 다 null 이면 안 돈다). 파일 루프·finish 뒤 `if (ext != null && !rules.external().isEmpty()) out.addAll(SpotBugsRun.run(…))`. `changed` 가 있으면 외부 지적도 바뀐 파일로 거른다(같은 `removeIf`). `RunResult` 에 `int external`(외부 지적 수)·`String externalNote`(못 돈 이유 — 클래스 폴더 없음·클래스 0·플러그인 로드 실패 글, 없으면 null).
- `core/check/SpotBugsRun.java`:
  ```java
  /** @param sourceRoot 검사 폴더(상대 경로 매핑) · classesDir 클래스 폴더 · libDir aux jar 폴더(null 이면 classesDir 가 …/WEB-INF/classes 일 때 옆 lib) */
  public static Result run(Path sourceRoot, Path classesDir, Path libDir, Path pluginJar, List<Rule.Def> defs, JobContext ctx)
  public record Result(List<Finding> findings, int classes, int missingClasses, String note) {}
  ```
  `Project` 에 `classesDir` + `libDir/*.jar` 전부 aux. `UserPreferences` effort default. `BugCollectionBugReporter.setPriorityThreshold(LOW)` 로 다 받고 `minPriority`·`exclude`(글롭 → 정규식, `Rule.Def.glob` 재사용) 로 거른다. 진행: `FindBugsProgress.startAnalysis(n)` 에서 `progress(…, "SpotBugs 단계 k/m")`, `finishClass()` 마다 `ctx.isCancelled()` 면 `JobCancelledException` 을 던진다(FindBugs2 가 삼키면 § 7). SpotBugs 가 slf4j 로 찍는 경고(`missing class`)는 로그 수준 WARN — `logback.xml` 에 `edu.umd.cs.findbugs` 를 ERROR 로.
  플러그인 파일: `Analyzers.findSecBugsJar(Path dataDir)` — 자원 `/analyzers/findsecbugs-plugin.jar` 를 `dataDir/analyzers/` 로 복사(크기 같으면 생략) → `Path`. `ext.findsecbugs` 가 꺼져 있으면 로드하지 않는다(코어만).
- 요청·프로필: `RunRequest` 에 `classesDir`·`libDir` · `Profile.CodeCheck` 에 `classesDir`·`libDir`(5번째·6번째 — 옛 생성자 둘은 유지하고 null) — `project.root` 기준 상대 경로 허용(`LocalFiles.check` 전에 `root.resolve`). 화면 값이 프로필 값을 이긴다. 폴더가 없으면 400 「클래스 폴더가 없다」(ㅅ 묶음이 켜졌을 때만 검사).
- 화면(`code_check.html`·`code_check_ext.js`): `GROUPS` 에 `['ext', 'ㅅ 외부 분석기']`(metric 앞 — PLAN 표 순서 ㅅ·ㅊ). `.dirbar` 에 `<span class="opt" id="classesWrap" hidden>클래스 <input type="text" id="classes" spellcheck="false" placeholder="…\WEB-INF\classes — 묶음 ㅅ 이 켜졌을 때" aria-label="클래스 폴더"></span>` — 묶음 ㅅ 체크(`g_ext`)가 켜지면 보이고, 프로필 `codecheck.classesDir` 가 있으면 미리 채운다(`GET /api/check/rules` 응답에 `classesDir`·`libDir` 를 더한다 — 규칙 목록 옆 메타로, 응답 꼴은 `{rules:[…], classesDir, libDir}` 로 바꾸지 말고 **별도 `GET /api/check/defaults`** 로). 결과 요약에 `· 외부 분석기 n`·`externalNote` 가 있으면 노랑으로 덧붙인다. 붙여넣기 탭은 무관.
- 버린 대안: 등급을 `rank` 로(1~20 — 사용자에게 낯설다; 우선순위 셋이 Maven 플러그인과 같다) · BUG_TYPE 마다 `rules.yaml` 규칙(400+ 줄 — 화면 체크박스가 터진다; `exclude` 글롭이 같은 일을 한다) · 외부 지적에 `tb-ignore` 적용(소스 줄을 안 읽는다 — 억제는 `exclude` 로).

## 4. 실행 스텝

**5-38a — 의존성 + 묶음 실측 + m2**
- 대상: `pom.xml` · `scripts/offline-build.sh`(돌리기만) · 새 `src/test/java/kr/ejg/toolbox/core/check/SpotBugsLoadTest.java`.
- 변경: §3 의존성. `SpotBugsLoadTest`: ① `DetectorFactoryCollection.instance().getCorePlugin()` 이 null 아님 + 검출기 100 이상 ② `Analyzers.findSecBugsJar(tmp)` 가 파일을 만들고 `Plugin.loadCustomPlugin` 이 `com.h3xstream.findsecbugs` 를 돌려준다. 그리고 **shaded jar 로 한 번 더**: `bash scripts/mvn.sh -q package -DskipTests` 뒤 `java -cp target/app.jar` 로 같은 두 가지를 찍는 임시 클래스(저장소 밖 scratch)로 — 코어 플러그인이 app.jar 에서 로드되는지. 이것이 이 번들의 첫 관문이다. `bash scripts/offline-build.sh` 로 `m2/` 채움 + `-o` 빌드.
- 검증: `bash scripts/mvn.sh -q test -Dtest=SpotBugsLoadTest` 초록 · shaded 실측 출력(검출기 수)을 이력에 · `bash scripts/offline-build.sh --check` 초록 · `ls -la target/app.jar`(약 44MB).
- 의존: 없음.

**5-38b — `SpotBugsRun` + kind external + `CheckRunner` + 시험**
- 대상: 새 `core/check/SpotBugsRun.java` · 새 `core/check/Analyzers.java` · `RuleSet.java` · `CheckRunner.java` · `rules.yaml`(규칙 둘) · `src/main/resources/logback.xml` · 새 `fixtures/check/ext/Pos.java`(`String s = null; s.length();` 꼴 NP + `Statement.execute("SELECT … " + arg)` SQL 조립 + `new File(req)` 경로 — 패키지 `fx`) · `Neg.java`(깨끗) · 새 `SpotBugsRunTest`.
- 변경: §3. `SpotBugsRunTest`: `javax.tools.ToolProvider.getSystemJavaCompiler()` 로 픽스처 둘을 임시 `classes/` 에 `-g` 로 컴파일(시험은 JDK 에서 돈다; null 이면 `assumeTrue` 로 건너뜀) → `SpotBugsRun.run(fixtureRoot, classes, null, plugin, defs, null)` → `findings` 에 `spotbugs.NP_ALWAYS_NULL`(또는 `NP_*` 접두)·`findsecbugs.SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE`(또는 `SQL_*`) 가 `Pos.java` 줄 번호와 함께 있고 `Neg.java` 는 0 · `file` 이 `ext/Pos.java`(소스 루트 상대) · `minPriority: 1` 이면 줄어든다 · `exclude: ['NP_*']` 면 NP 가 빠진다 · 취소: `JobContext` 가 바로 취소 상태면 `JobCancelledException`(안 던져지면 §7). `CheckRunnerTest`: `External` 없이는 외부 지적 0 · 있으면 `external` 수 > 0 · 변경분 거르기.
- 표본: 새 `SpotBugsCorpusTest`(`@Tag corpus`, `src/test/java/kr/ejg/toolbox/core/check/`) — ① `jspwiki-bin/classes` + `lib`, 소스 루트 `jspwiki` → `SpotBugsRun.run` → **A 불변식**: 예외 없음 · 소스 파일을 찾은 지적 비율 ≥ 95%(`file` 이 표본에 실재) · 줄 > 0 비율 ≥ 95% → **B 골든** `golden/corpus/spotbugs-jspwiki.json`(BUG_TYPE 별 건수·우선순위별 건수) + 히트 파일 목록 `spotbugs-jspwiki-hits.txt`(B baseline) · 시간을 이력에(384 클래스 — 이 저장소 437 클래스 23초가 기준). ② `ruoyi-bin` 같은 꼴 `spotbugs-ruoyi.json` + `findsecbugs.SQL_*`·`PATH_TRAVERSAL_*` 가 1 이상(MyBatis 앱에서 보안 패턴이 실제로 걸리는가). 폴더가 없으면 `assumeTrue` 건너뜀.
- 검증: `bash scripts/mvn.sh -q test -Dtest='SpotBugsRunTest,CheckRunnerTest,CheckRulesTest,JavaRulesTest'` 초록(`CheckRulesTest.fixturesGolden` 은 `ext/` 폴더를 java/·mybatis/ 처럼 건너뛴다 — 픽스처가 regex 규칙에 걸리지 않게 하거나 건너뛰기 목록에 더한다) · `bash scripts/mvn.sh -q test -Dtest=SpotBugsCorpusTest`(표본 폴더 필요, 약 1분).
- 의존: 5-38a · V-29(번들 50 — 표본이 없으면 표본 시험만 건너뛴다).

**5-38c — 요청·프로필·API·화면 + 스모크**
- 대상: `CheckRoutes.java`(`RunRequest`·`GET /api/check/defaults`·400) · `core/profile/Profile.java`(`CodeCheck` 필드 둘) · `core/profile/ProfileStore.java`(`saveCodeCheck` 가 두 값을 지킨다 — 5-32c 꼴) · `profiles/example.yaml` · `tools/code_check.html`·`code_check_ext.js` · `SmokeHtmlUnitTest`(새 `codeCheckExternal`) · `ProfileStoreTest` · `CheckRoutesTest`.
- 변경: §3 요청·화면. 스모크: JavaCompiler 로 픽스처 컴파일(없으면 건너뜀) → 프로필 `codecheck.classesDir` → 화면 → `g_ext` 체크 → `#classes` 가 보이고 값이 차 있다 → 폴더 검사 → 결과 표에 `spotbugs.` 행 ≥ 1 · 요약에 `외부 분석기 ` → 이력 xlsx 에 그 행. `CheckRoutesTest`: ㅅ 켜고 `classesDir` 없음 400 · 폴더 없음 400 · `GET /api/check/defaults`.
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRoutesTest,ProfileStoreTest,SmokeHtmlUnitTest#codeCheckExternal+codeCheckFolderRun,ToolsFolderTest,ToolsA11yTest'` 초록.
- 의존: 5-38b.

**5-38d — 반입 문서·SOURCES·README·PLAN 3.1**
- 대상: `docs/import-review.md`(8절 jar 수·LGPL 고지 한 줄·9절 「내장 SpotBugs/FindSecBugs 가 사업 클래스를 본다 — 밖으로 안 나간다」) · `bundle/SOURCES.md`(「app.jar 안 분석 엔진」 절 — 좌표·판·라이선스·출처 URL) · `README.md` 코드 검사 줄 · `ImportReviewDocTest.namedThingsExist` 에 `SpotBugsRun` · `scripts/package-check.sh`(`required` 에 더할 것 없음 — app.jar 안) · PLAN §3.1 표 ㅅ 행은 S0 가 고쳤다(확인만).
- 검증: `bash scripts/mvn.sh -q test -Dtest=ImportReviewDocTest` · `bash scripts/doc-lint.sh docs/import-review.md` 초록.
- 의존: 5-38b.

**5-38e — sonar-findbugs 등급표 대조**(네트워크, 맨 뒤)
- 대상: 새 `src/test/resources/fixtures/check/ext/sonar-findbugs-v4.6.0.yaml`(받아 뽑은 표 — 출처·태그를 머리 주석에) · 새 `SpotBugsSeverityTest` · `golden/check/ext-severity-diff.txt`.
- 변경: `spotbugs/sonar-findbugs` **태그 `v4.6.0`**(2026-10-23 확인 — 최신 태그) 의 `src/main/resources/org/sonar/plugins/findbugs/rules-findbugs.xml`(규칙 497, 꼴 `<rule key='NP_…' priority='MAJOR'>`)·`rules-findsecbugs.xml` 을 raw 로 받아(`curl`, 스크립트 아닌 일회 — 받은 파일은 픽스처로 커밋, 라이선스 LGPL-3.0 은 데이터 표라 출처만 적는다) `BUG_TYPE → BLOCKER·CRITICAL·MAJOR·MINOR·INFO` 표 YAML 로 뽑는다(executor 가 `grep -o` 로 — 코드 없음). `SpotBugsSeverityTest`(java 레인): `SpotBugsRunTest`·`SpotBugsCorpusTest`(jspwiki-bin 있으면) 가 낸 지적마다 우리 등급(우선순위 1·2·3 → error·warn·info)과 sonar 등급(BLOCKER·CRITICAL → error, MAJOR → warn, MINOR·INFO → info)을 견줘 다른 BUG_TYPE 목록을 `ext-severity-diff.txt` conformance 로 굳힌다 + 우리 기본 제외(`IMPROPER_UNICODE`)가 sonar 에서 어느 등급인지 이력에.
- 검증: `bash scripts/mvn.sh -q test -Dtest=SpotBugsSeverityTest` 초록 · diff 목록 길이·상위 10 을 이력에. **이 번들에서 등급 규칙을 바꾸지 않는다** — 아침에 사용자가 「우선순위 그대로 / sonar 표를 `params.severityMap` 으로」 를 고른다(설계 34 §3 「버린 대안」 의 rank 는 그대로 버림).
- 의존: 5-38b. 마무리 직전.

**마무리** — CLAUDE.md 「마무리」 여덟. 바뀐 레인: java·tools·docs·corpus(`core/check`·tools) → `--corpus` · db(`pom.xml`) → `--db`. `--full` 은 반입 전(사람). 이력에 shaded 실측·app.jar 크기·의존성 수(`dependency:list`)를 적는다.

## 5. 금지 사항
- 외부 프로세스로 돌리지 않는다(ArchUnit `processesOnlyFromVcsCliAndBrowserOpen`).
- Saxon-HE·log4j-core 를 들이지 않는다(크기·바인딩 충돌).
- SpotBugs 의 `-xml`·`-html` 보고서 파일을 만들지 않는다 — 결과는 `Finding` 으로만(규칙 3 — 보고서엔 메서드 본문 일부가 든다).
- `config/spotbugs/exclude.xml`(이 저장소 자기 검사)과 런타임 `params.exclude` 를 섞지 않는다.
- 기존 규칙·골든·순수본 JSON 불변. 완료 행 불변.

## 6. 최종 검증
- `bash scripts/verify.sh` → `bash scripts/verify.sh --corpus --db` → `bash scripts/gate-probe.sh` → `bash scripts/offline-build.sh --check`.
- 손 확인: 데모 프로젝트를 Maven 으로 빌드해 `target/classes` 를 「클래스 폴더」에 → 폴더 검사(ㅅ 켬) → `spotbugs.`·`findsecbugs.` 행 · 이력 xlsx · 추이 탭(번들 49)에 묶음 `ext` 열.
- 반입: `bash scripts/package.sh` 가 app.jar 크기·jar 수를 새로 센다(사람, 반입 전).

## 7. 중단 조건
- 5-38a shaded 실측에서 코어 플러그인을 못 찾는다(`getCorePlugin()` null 또는 검출기 0) → 멈추고 보고. 선택지: `analyzers/` 폴더 + `URLClassLoader`(package.sh·MANIFEST·import-review 가 같이 바뀐다 — 설계 세션이 다시 적는다).
- `FindBugs2.execute()` 가 취소 예외를 삼켜 중지가 안 먹는다 → 「중지는 분석 끝에」 로 문서에 적고 계속(멈추지 않는다 — 이력에).
- `SpotBugsCorpusTest` 의 줄 매핑률이 95% 에 못 미친다 → 매핑 규칙(§3 `endsWith`)의 빈 구멍(중첩 클래스·패키지 없는 클래스·코틀린)을 적고 멈춘다 — 기준을 내리지 않는다.
- 검증이 2회 연속 실패하고 원인이 플랜 범위 밖 · 금지 사항을 어기지 않고는 진행 불가 · 스텝에 없는 파일을 3개 이상 고쳐야 함.
- 보고 형식: 어느 스텝, 무엇이 예상과 달랐는지, 가능한 선택지.

## 8. 불확실 항목
- **실행해야만 아는 것**: shaded `app.jar` 에서 SpotBugs 코어 플러그인 로드(§4 5-38a 가 첫 검증, §7 첫 줄이 사다리) · `FindBugs2` 안에서 던진 취소 예외의 전파(5-38b 시험). 둘 다 실행 행에 검증과 사다리를 적었다.
