# 설계 38 — 번들 55: 코딩할 때부터 — 작성 규약 · PMD · JUnit timeout · ErrorProne · PIT (+ jackson 2.18.11 · 순수본 sync) (Fable 설계 · Opus 실행, 2026-10-24)

## 맥락
번들 54(설계 37, PR #89 `b2b69b7`)가 「도구를 자기 코드에」 를 닫으며 다섯을 번들 55 로 미뤘다 — 작성 규약 · PMD · JUnit timeout · ErrorProne · PIT. 거기에 Dependabot 열린 경보 5(#14~#18, 전부 jackson-databind, 2.18.11 이 다 닫는다)와 portfolio 순수본(main `c38fcef` = PR #35 — `f791397` 이 순수본 엔진에 `when` 게이트·`tb-ignore` 를 구현)을 같은 번들에 싣는다. pom 변경 검증(`--db`·`offline-build.sh` 채움·CI)을 한 번에 치르려는 것.

사용자 결정(2026-10-24, 뒤집지 않는다):
- 첫 행은 jackson 2.18.11 + 순수본 sync.
- CLAUDE.md 작성 규약은 **강제되는 것만 7줄**. 스타일(이름·로깅)은 안 적는다.
- PMD 는 errorprone·bestpractices, **verify 단계만**(빠른 검증 불변). 기존 지적은 기준선으로 얼리고 새 지적만 빨강(래칫). 우리 규칙과 겹치는 PMD 규칙은 끈다. 판은 test 의존성 `pmd-java 7.28.0` 과 맞춘다.
- JUnit 전역 timeout 기본 **2분**, `db`·`corpus` 는 **10분**.
- ErrorProne 은 **ERROR 급만 빌드 실패**. 처음 걸린 것은 고친다 — 한 검사에 30건 넘으면 그 검사만 끄고 이력에. 빠른 검증 컴파일 시간 증가를 실측해 이력에.
- PIT 는 **`core.check` 하나 · 손으로만(`scripts/pit.sh`) · 리포트만**(빨강 없음, 게이트 승격은 다음 번들).
- 번들 하나·PR 하나 · 머지 위임.

실행자는 Opus. 차례는 `S0` → `0-66` → `0-69a` → `0-69b` → `0-70` → `0-68` → `0-65` → `0-67` → `V-33` → 마무리. 가지 `work/2026-10-24h`(S0 커밋 때 main 에서 딴다).

---

## 1. 목표와 범위
- 한 문장: 빌드가 우리 코드에 거는 기준을 넷 늘리고(ErrorProne ERROR · PMD 새 지적 · 시험 시간 상한 · 뮤테이션 리포트), 그 기준을 CLAUDE.md 7줄로 적고, jackson 경보 5 를 닫고 순수본을 portfolio 와 맞춘다. 완료 뒤 사용자가 보는 것: Dependabot 경보 0 · `bash scripts/pit.sh` 가 `out/pit/index.html` 을 낸다 · 새 코드의 빈 catch·단언 없는 시험·PMD 지적·ErrorProne ERROR·2분 넘는 시험이 CI 에서 빨강.
- In scope: `pom.xml`(jackson·플러그인 셋·속성) · `.mvn/jvm.config` · `src/test/resources/junit-platform.properties` · `config/pmd/*` · `scripts/pit.sh`·`scripts/pmd-baseline.sh` · `scripts/verify.sh`·`.github/workflows/verify.yml`(timeout 10분) · `pure/`(sync 로만) · CLAUDE.md 작성 규약 · `docs/import-review.md` §9 · ErrorProne ERROR 고치기.
- Out of scope: PLAN 5-7(PMD 를 **사업 코드** 검사 묶음 ㅅ으로 app.jar 에 내장 — 발주처 요구 뒤, 그대로 보류) · PIT 게이트(점수·기준선) · PMD codestyle·design 묶음 · ErrorProne WARNING 처리 · 시험 리팩터 · 라우트·로직 정리 · 순수본 수정(동결).

## 2. 현재 구조 요약(탐색 2026-10-24 — 에이전트 둘 + 실측)
- **pom.xml**(502줄): `<properties>` L12-30(`jackson.version` 2.18.9 L18 · `junit.version` 5.11.4 · 빈 `<argLine>`). 빌드 플러그인: compiler 3.13.0(L271, `<release>17</release>` 만) · resources · surefire 3.5.2(L284 — `argLine` `@{argLine} -da:org.h2... -Xmx3g`, `systemPropertyVariables` 에 `toolbox.logDir` 하나, groups·forkCount 없음) · jar · dependency(findsecbugs 복사) · checkstyle 3.6.0(validate) · jacoco 0.8.12(L368, prepare-agent·verify 에 report→check, 하한 BUNDLE 0.91/0.78·PACKAGE core.* 0.68) · spotbugs 4.9.3.0(verify) · shade. `<profiles>` 없음. test 의존성 `pmd-java 7.28.0`(L189-195, 5-35c).
- **`.mvn/`**: `wrapper/` 뿐. `mvnw` L307 이 `.mvn/jvm.config` 를 `MAVEN_OPTS` 에 잇는다 → 새 파일 하나로 CI(`./mvnw`)·로컬(`scripts/mvn.sh` → `./mvnw`) 둘 다 먹는다. `scripts/offline-needed.sh` 는 `pom.xml`·`.mvn/` 이 바뀌면 CI `offline` 잡을 돌린다.
- **시험**: `junit-platform.properties` 없음 · `@Timeout` 없음 · 메타 어노테이션 없음. `@Tag("db")`·`@Tag("corpus")` 는 클래스에 직접(db 8 + `web/DbCorpusBase` 상속 9 · corpus 22). 실측 938 시험·합 1527초·최장 93초(`DdlCorpusTest#dtosCompile`, corpus)·60초 넘는 7 은 전부 db·corpus → 빠른 집합은 전부 60초 아래.
- **시험이 도는 자리 넷**(timeout 10분을 줄 자리): `scripts/verify.sh` L57(CI=true `-DexcludedGroups=corpus verify`) · L59(`--full` `verify`) · L94(heavy `-Dgroups="$expr" test`) · `.github/workflows/verify.yml` L37(`./mvnw -B -q -DexcludedGroups=corpus verify`). 빠른 검증 L65(`-DexcludedGroups=db,corpus test`)와 `offline-build.sh` 채움(`-DexcludedGroups=db,corpus -Djacoco.skip=true verify`)·`--check`(`-o -DskipTests verify`)는 db·corpus 가 없다.
- **검증 레인**(`scripts/verify-fingerprint.sh` L147-151): java = `src pom.xml .mvn mvnw mvnw.cmd config templates mappings docs/import-review.md scripts/package.sh` · tools = `scripts .claude/settings.json .claude/prompts .github` · docs = `CLAUDE.md PLAN.md PROGRESS.md README.md docs` · db 에 `pom.xml`·`.mvn` 포함 · corpus 에 `pure` 포함. → 이 번들의 새 파일은 전부 기존 레인 안(`config/pmd/`·`.mvn/jvm.config`·`src/test/resources/…` = java, `scripts/*.sh` = tools). **레인 목록·`VerifyLanesTest` 손질 없음.**
- **자기 검사**: `SelfCheckTest`(`golden/check/self-check.txt` 133건 기준선, `HELPERS = none·baseline·conformance·jpaCorpus`) · `ArchitectureTest.noPackageCycles` · `CheckRulesTest.ignoreMarkers` · 복잡도 상한은 `rules.yaml:827` `params: {max: 15}`. 표식 문법 `docs/pure-code-check.md` §9 L69.
- **라우트 관례**(0-63a): `app.get("/api/check/stack", ctx -> getStack(ctx, files));` + `private static void getStack(Context ctx, LocalFiles files)` — `metric.complexity` 가 람다 분기를 `register` 에 세기 때문.
- **m2/**: gitignore, 382MB. `maven-pmd-plugin`·`error_prone_core`·`pitest` 셋 다 없다(`error_prone_annotations` 만 전이로 있음). `offline-build.sh` 채움 = `dependency:go-offline dependency:resolve-plugins` + 채움 verify(컴파일 → annotationProcessorPaths 의 ErrorProne 도 이때 받는다) → `--check` 는 `-o -DskipTests verify`(PMD check·ErrorProne 컴파일이 오프라인에서 돈다는 증명).
- **순수본**: `pure/MANIFEST` vs portfolio HEAD(`c38fcef`) — `tools/code_check.html` 만 다르다(`7fafad0d…` → `07aed014…`), `toolbox.html` 같음(나머지는 sync 가 알려 준다). `PureFrozenTest`(빠름)·`PureRulesJsonTest.pureHtmlEmbedsGolden`(빠름, `var RULES` = `golden/check/pure-rules.json`)·`PureCodeCheckTest`(corpus, node — 픽스처 32 를 순수본 엔진과 자바 골든으로 맞댐).
- **Maven Central 실측(2026-10-24, repo1 maven-metadata)**: `maven-pmd-plugin` 3.28.0 · `pmd-java` 7.28.0 · `error_prone_core` 2.51.0 · `pitest-maven` 1.30.1 · `pitest-junit5-plugin` 1.2.3 · `jackson-databind` 2.18.11(2.18.12 도 있으나 결정은 2.18.11). Dependabot: #14·#15·#16 은 `< 2.18.10`, #17·#18 은 `<= 2.18.10` → 2.18.11 이 다섯 다 닫는다.
- **PLAN 번호**: 번들 표 마지막 행 L536(번들 54), 분할표 마지막 L980(5-45b). 쓰인 최대 `0-64c`·`5-46`·`V-32`. `0-65` 는 설계 37 L21 이 PIT 조건부 행으로 예약한 번호.
- **`docs/import-review.md` §9**(L92-107) 정적 검사 표 — JaCoCo·자기 검사 행이 0-62a·0-64c 때 들어갔다. `ImportReviewDocTest` 가 이 문서를 읽는다(§8 모듈 줄).
- **함정**: ① commit hook 은 빨간 트리를 `work/*` 에 못 올리게 한다 → PMD 는 기준선 파일과 **같은 커밋**. ② `-q` 가 WARNING 을 숨긴다 → ErrorProne WARNING 은 로그에 안 보이는 게 정상. ③ `gate-probe.sh` 의 `pure-frozen.patch` 가 sync 로 낡을 수 있다(마무리 2 에서 다시 뜬다). ④ `--db` 는 `.wslconfig` memory=10GB 상한에서 초록(번들 54 마무리) — 돌리기 전 다른 JVM 이 없게.

## 3. 설계 결정 — 전부 [고정]

### A. jackson + 순수본 sync(0-66)
- `pom.xml:18` `2.18.9` → `2.18.11`. 2.18.12 를 안 쓰는 이유: 결정이 2.18.11 이고 경보 다섯이 2.18.11 로 닫힌다.
- `bash scripts/sync-pure.sh`(TOOLBOX_SRC 기본 `C:/workspace/portfolio/frontend/public/toolbox`, HEAD 내용만 복사) → `pure/tools/code_check.html`·`pure/MANIFEST`. `pure/` 는 손대지 않는다.
- 실패 사다리: `ProfileStoreTest` 빨강(record 역직렬화) → 0-23 전례대로 그 시험이 가리키는 필드만 손봄 · `PureCodeCheckTest` 빨강 → 차이가 순수본 엔진 쪽(portfolio)이면 **안 고치고** 이력 + 사람 몫(portfolio 지시문) · 자바 골든 쪽이면 중단 조건.

### B. JUnit timeout(0-69a·0-69b)
- 방법: **속성 하나 + 자리 넷**. 베이스 클래스·꼬리표별 `@Timeout` 은 안 쓴다(31 클래스 손질, 상속 안 하는 db 8 은 각각).
- `src/test/resources/junit-platform.properties`(IDE 에서도 먹는다):
  ```
  # 0-69(설계 38) — 시험 하나(생명주기 메서드 포함) 상한 2분. db·corpus 가 섞이는 실행은 surefire 가 -Djunit.timeout=10m 로 덮는다(verify.sh·verify.yml)
  junit.jupiter.execution.timeout.default = 2m
  junit.jupiter.execution.timeout.mode = disabled_on_debug
  ```
- `pom.xml` `<properties>` 에 `<junit.timeout>2m</junit.timeout>`, surefire `systemPropertyVariables` 에 `<junit.jupiter.execution.timeout.default>${junit.timeout}</junit.jupiter.execution.timeout.default>`. 명령줄 `-Djunit.timeout=10m` 이 pom 속성을 덮는다(Maven 보간 — surefire 의 사용자 속성 전파에 기대지 않는다).
- 10분 자리 넷: `verify.sh` L57·L59·L94, `verify.yml` L37 — 각 `mvn` 인자에 `-Djunit.timeout=10m`. 빠른 검증·offline-build 는 2분 그대로.
- 버린 대안: surefire 실행 둘(groups 별 속성) — 스크립트의 `-Dgroups`/`-DexcludedGroups` 와 꼬인다.

### C. ErrorProne(0-70)
- `.mvn/jvm.config` 새 파일(주석 없이 열 줄 — mvnw 가 줄을 그대로 JVM 인자로 잇는다):
  ```
  --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
  --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
  --add-exports jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED
  --add-exports jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED
  --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED
  --add-exports jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED
  --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
  --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
  --add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
  --add-opens jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED
  ```
- `pom.xml` 속성 `<errorprone.version>2.51.0</errorprone.version>`, compiler 플러그인:
  ```xml
  <configuration>
    <release>17</release>
    <!-- 0-70(설계 38) — Error Prone: ERROR 급만 빌드 실패, WARNING 은 안 막는다(-q 가 숨긴다). JDK 17 은 javac 내부를 .mvn/jvm.config 의 add-exports/opens 로 연다 -->
    <compilerArgs>
      <arg>-XDcompilePolicy=simple</arg>
      <arg>--should-stop=ifError=FLOW</arg>
      <arg>-Xplugin:ErrorProne</arg>
    </compilerArgs>
    <annotationProcessorPaths>
      <path>
        <groupId>com.google.errorprone</groupId>
        <artifactId>error_prone_core</artifactId>
        <version>${errorprone.version}</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
  ```
  (어노테이션 프로세서를 쓰는 의존성이 없다 — `annotationProcessorPaths` 가 다른 것을 막지 않는다.) main·test 둘 다 검사된다.
- 첫 컴파일의 ERROR: 고친다. 한 검사가 30건 넘으면 `<arg>-Xep:<검사>:OFF</arg>` 한 줄 + 이력. 고친 파일 수는 청크 규칙 예외(이력에 검사별 건수).
- 실측 둘을 이력에: `time bash scripts/mvn.sh -q -B -Djacoco.skip=true -DskipTests clean test-compile` 를 0-70 전(HEAD 0-69b)·후 각 한 번 · `-q` 없이 한 번 돌려 WARNING 검사별 건수(처리 안 함).
- 버린 대안: `<fork>true</fork>` + `-J--add-exports`(mvn.sh·CI 가 같은 mvnw 라 jvm.config 하나가 더 단순) · 2.3x 판(JDK 17 에서 2.51.0 이 돈다, 최신으로).

### D. PMD(0-68) — 플러그인 기본 기능으로 래칫
- `pom.xml` 속성 `<pmd.version>7.28.0</pmd.version>`, test 의존성 `pmd-java` 의 `7.28.0` → `${pmd.version}`. 플러그인:
  ```xml
  <plugin>
    <!-- 0-68(설계 38) — PMD errorprone·bestpractices 를 우리 코드(main·test)에, verify 단계만(빠른 검증 test 는 안 돈다). 기존 지적은 config/pmd/exclude-from-failure.properties(클래스=규칙 꼴, scripts/pmd-baseline.sh 가 뜬다)로 얼리고 새 클래스·새 규칙 짝만 빨강. 동봉 PMD 는 test 의존성 pmd-java(5-35c)와 같은 판 -->
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-pmd-plugin</artifactId>
    <version>3.28.0</version>
    <configuration>
      <rulesets><ruleset>config/pmd/ruleset.xml</ruleset></rulesets>
      <includeTests>true</includeTests>
      <linkXRef>false</linkXRef>
      <printFailingErrors>true</printFailingErrors>
      <excludeFromFailureFile>config/pmd/exclude-from-failure.properties</excludeFromFailureFile>
      <analysisCache>true</analysisCache>
    </configuration>
    <dependencies>
      <dependency><groupId>net.sourceforge.pmd</groupId><artifactId>pmd-core</artifactId><version>${pmd.version}</version></dependency>
      <dependency><groupId>net.sourceforge.pmd</groupId><artifactId>pmd-java</artifactId><version>${pmd.version}</version></dependency>
      <dependency><groupId>net.sourceforge.pmd</groupId><artifactId>pmd-javascript</artifactId><version>${pmd.version}</version></dependency>
      <dependency><groupId>net.sourceforge.pmd</groupId><artifactId>pmd-jsp</artifactId><version>${pmd.version}</version></dependency>
    </dependencies>
    <executions>
      <execution><id>check</id><phase>verify</phase><goals><goal>check</goal></goals></execution>
    </executions>
  </plugin>
  ```
  `check` 가 `pmd` 를 먼저 돌린다. verify 단계라 CI(`verify`)·`--full`·`offline-build.sh`(채움 verify·`--check`) 에서 돌고, 빠른 검증(`test`)·`--db`·`--corpus`(둘 다 `test` 단계)에서는 안 돈다 — JaCoCo `check` 와 같은 자리.
- `config/pmd/ruleset.xml`:
  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <ruleset name="toolbox" xmlns="http://pmd.sourceforge.net/ruleset/2.0.0"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://pmd.sourceforge.net/ruleset/2.0.0 https://pmd.sourceforge.io/ruleset_2_0_0.xsd">
    <description>0-68(설계 38) — errorprone·bestpractices 전부. 뺀 것은 우리 코드 검사 규칙과 겹치는 것(java.emptyCatch·java.sysout·java.testNoAssert)과 새 시험마다 걸리는 JUnit 글꼴 규칙 둘</description>
    <rule ref="category/java/errorprone.xml">
      <exclude name="EmptyCatchBlock"/>
    </rule>
    <rule ref="category/java/bestpractices.xml">
      <exclude name="SystemPrintln"/>
      <exclude name="UnitTestShouldIncludeAssert"/>
      <exclude name="UnitTestAssertionsShouldIncludeMessage"/>
      <exclude name="UnitTestContainsTooManyAsserts"/>
    </rule>
  </ruleset>
  ```
  실행자는 이 다섯 밖의 exclude 를 더하지 않는다 — 소음은 기준선이 얼린다.
- `config/pmd/exclude-from-failure.properties`: 플러그인 `excludeFromFailureFile` 꼴 `<패키지>.<클래스>=<규칙>,<규칙>`. `scripts/pmd-baseline.sh` 가 `target/pmd.xml` 에서 뜬다:
  ```bash
  #!/usr/bin/env bash
  # PMD 기준선(0-68, 설계 38) — pmd:pmd 를 돌려 target/pmd.xml 의 (패키지.클래스 → 규칙) 짝을
  # config/pmd/exclude-from-failure.properties 로 쓴다. 래칫: 새 클래스·새 규칙 짝만 pmd:check 가 빨강.
  # 다시 뜨는 때는 지적을 고쳐 줄였을 때뿐 — 늘리려고 뜨지 않는다(diff 를 이력에).
  set -euo pipefail
  cd "$(dirname "$0")/.."
  bash scripts/mvn.sh -q -B pmd:pmd
  out=config/pmd/exclude-from-failure.properties
  {
    echo "# scripts/pmd-baseline.sh 가 뜬다. 손으로 늘리지 않는다 — 줄어들 때만 다시 뜬다(0-68)"
    awk '
      /<file name="/ { match($0, /name="[^"]*"/); f = substr($0, RSTART+6, RLENGTH-7) }
      /<violation / {
        r = p = c = ""
        if (match($0, / rule="[^"]*"/))    r = substr($0, RSTART+7,  RLENGTH-8)
        if (match($0, / package="[^"]*"/)) p = substr($0, RSTART+10, RLENGTH-11)
        if (match($0, / class="[^"]*"/))   c = substr($0, RSTART+8,  RLENGTH-9)
        if (p == "" || c == "") { k = f; sub(/.*\/java\//, "", k); sub(/\.java$/, "", k); gsub(/\//, ".", k) } else k = p "." c
        if (!((k, r) in seen)) { seen[k, r] = 1; rules[k] = (k in rules) ? rules[k] "," r : r }
      }
      END { for (k in rules) print k "=" rules[k] }
    ' target/pmd.xml | LC_ALL=C sort
  } > "$out"
  echo "기준선 $(grep -vc '^#' "$out") 클래스 → $out"
  ```
  키 유도는 플러그인 `ExcludeViolationsFromFile` 과 같은 순서(패키지+클래스 속성, 없으면 파일 경로의 `/java/` 뒤).
- 실패 사다리(기준선 뒤 `pmd:check` 가 빨강이면): ① 빨간 키를 보고 awk 의 키 꼴을 그 키에 맞춘다(예: 내부 클래스 `$`) ② 그래도면 `<maxAllowedViolations>`=실측 건수(클래스 래칫 → 건수 래칫, 약해진 것을 이력에).
- 버린 대안: 자체 JUnit 기준선 시험(`SelfCheckTest` 꼴) — `pmd:pmd` 를 test 단계 앞에 묶어야 해서 빠른 검증 불변이 깨진다 · bash 비교 스크립트 — CI `verify.yml` 단계를 하나 더 만들어야 한다 · 5-7 과 합치기 — 5-7 은 app.jar 내장(사업 코드), 이건 빌드 플러그인(우리 코드).

### E. PIT(0-65)
- `pom.xml` 속성 `<pitest.version>1.30.1</pitest.version>`, 플러그인(phase 없음 — 손으로만):
  ```xml
  <plugin>
    <!-- 0-65(설계 38) — 뮤테이션 시험. core.check 만, 손으로만(scripts/pit.sh), 리포트만(게이트 아님 — 승격은 리포트를 본 뒤 다음 번들). db·corpus 꼬리표는 뺀다 -->
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>${pitest.version}</version>
    <dependencies>
      <dependency><groupId>org.pitest</groupId><artifactId>pitest-junit5-plugin</artifactId><version>1.2.3</version></dependency>
    </dependencies>
    <configuration>
      <targetClasses><param>kr.ejg.toolbox.core.check.*</param></targetClasses>
      <targetTests><param>kr.ejg.toolbox.core.check.*</param></targetTests>
      <excludedGroups><param>db</param><param>corpus</param></excludedGroups>
      <outputFormats><param>HTML</param></outputFormats>
      <reportsDirectory>${project.basedir}/out/pit</reportsDirectory>
      <timestampedReports>false</timestampedReports>
      <threads>4</threads>
      <timeoutConstant>10000</timeoutConstant>
      <jvmArgs><jvmArg>-Xmx2g</jvmArg></jvmArgs>
    </configuration>
  </plugin>
  ```
- `scripts/pit.sh`:
  ```bash
  #!/usr/bin/env bash
  # PIT 뮤테이션 시험(0-65, 설계 38) — core.check 만, 손으로만. 리포트 out/pit/index.html. 게이트 아님.
  set -euo pipefail
  cd "$(dirname "$0")/.."
  bash scripts/mvn.sh -B -DexcludedGroups=db,corpus -Djacoco.skip=true test-compile org.pitest:pitest-maven:mutationCoverage
  echo "리포트: out/pit/index.html"
  ```
  `out/` 은 gitignore(도구 자기 출력 자리, 규칙 7).
- 이력에 실측: 걸린 시간 · 변이 수 · 점수(line coverage·mutation score) · 생존 변이 수. 실패 사다리: 40분 넘으면 끊고 `<mutators><mutator>STRONGER</mutator></mutators>` 대신 **`<targetClasses>` 를 `kr.ejg.toolbox.core.check.JavaRules*` 로 좁혀** 한 번 더(이력에), 그래도 넘으면 설정만 커밋하고 「실측 못 함」 이력.
- 버린 대안: `--pit` 레인·CI(사용자 결정 — 손으로만) · `target/pit-reports`(기본) 대신 `out/pit` — `mvn clean` 에 안 지워져 사람이 본다.

### F. CLAUDE.md 작성 규약 + 반입 심의 표(0-67)
- CLAUDE.md 「## 검증」 끝(L110)과 「## 글 작성 규칙」(L112) 사이에 새 절. 표 7줄 + 빌드 게이트 한 줄. 글자 그대로:
  ```
  ## 작성 규약 — 빌드가 막는 것만

  | 규약 | 강제 |
  |---|---|
  | 새 메서드 순환 복잡도 ≤ 15 | `SelfCheckTest.mainMatchesBaseline` — `metric.complexity` 기준선, 새 지적이 빨강 |
  | 빈 catch 는 `// tb-ignore java.emptyCatch <사유>` | `SelfCheckTest.mainMatchesBaseline` — `java.emptyCatch` |
  | 패키지 의존은 한 방향 | `ArchitectureTest.noPackageCycles` |
  | 시험은 단언 필수. 단언하는 도우미는 `SelfCheckTest.HELPERS` 에 | `SelfCheckTest.everyTestAsserts` — `java.testNoAssert` |
  | `tb-ignore` 는 주석 첫 낱말 + 규칙 + 사유 | `CheckRulesTest.ignoreMarkers`(문법 `docs/pure-code-check.md` 9절) |
  | 라우트 몸은 `register` 안 람다 대신 `private static void <이름>(Context ctx, …)` | `metric.complexity` 가 람다 분기를 `register` 에 센다(0-63a) |
  | 커버리지 하한 — 전체 줄 0.91·분기 0.78, `core.*` 패키지 줄 0.68 | JaCoCo `check`(verify) |

  빌드가 더 거는 것(번들 55) — ErrorProne ERROR(컴파일) · PMD errorprone·bestpractices 새 지적(verify, 기준선 `config/pmd/exclude-from-failure.properties`) · 시험 하나 2분(db·corpus 10분). 스타일(이름·로깅)은 안 적는다 — 강제 없는 규약은 두지 않는다.
  ```
- CLAUDE.md 「빌드·실행」 코드 블록에 두 줄: `scripts/pit.sh                PIT 뮤테이션 — core.check 만, 리포트 out/pit/index.html(게이트 아님)` · `scripts/pmd-baseline.sh       PMD 기준선 다시 뜨기 — 지적을 줄였을 때만`.
- `docs/import-review.md` §9 표에 세 행(JaCoCo 행 L101 뒤):
  ```
  | ErrorProne | 컴파일 때 버그 패턴(ERROR 급이면 빌드 실패) | 워크플로 `verify`(`mvn verify`), `pom.xml` `maven-compiler-plugin` `-Xplugin:ErrorProne` |
  | PMD | errorprone·bestpractices 규칙(새 지적이면 빌드 실패, 기존은 `config/pmd/exclude-from-failure.properties` 기준선) | 워크플로 `verify`(`mvn verify`), `pom.xml` `maven-pmd-plugin` |
  | 시험 시간 상한 | 시험 하나 2분(db·corpus 10분) 넘으면 실패 | `src/test/resources/junit-platform.properties`·surefire `junit.timeout` |
  ```
  PIT 는 게이트가 아니라 표에 안 넣는다. §8 「빌드 도구는 안 넣는다」 그대로 — 셋 다 빌드 플러그인, `app.jar` 불변(`package-check.sh` 가 잰다).

### G. 공통
- 새 행 번호: `0-65`(PIT, 설계 37 예약) · `0-66`(jackson+sync) · `0-67`(규약·심의 표) · `0-68`(PMD) · `0-69a`·`0-69b`(timeout) · `0-70`(ErrorProne) · `V-33`(채움·무거운 레인·실측).
- 커밋 제목 `<종류>: <행> <무엇>`. 이력 한 줄에 「정한 것(계획 밖)」·실측.
- 빠른 검증 불변: 빠른 `verify.sh` 는 `test` 단계라 PMD·JaCoCo check 가 안 돈다. ErrorProne 만 컴파일에 얹힌다(C 의 실측).

## 4. 실행 스텝(= PLAN §16 행)
각 행: 대상 / 변경 / 검증 / 의존. 검증 명령은 저장소 루트에서. `scripts/mvn.sh` 만(맨몸 `mvnw` 는 훅이 막는다).

- **S0** — `design/38-bundle55.md`(이 파일 맥락~8절 그대로) + PLAN 번들 표 L536 뒤 번들 55 행 + §16 L980 뒤 행 여덟(`0-66`·`0-69a`·`0-69b`·`0-70`·`0-68`·`0-65`·`0-67`·`V-33`, 꼴은 L974·L978) + PROGRESS 「현재 상태」·이력 S0 한 줄. 가지 `work/2026-10-24h`. 설계 세션이 친다. 검증 `bash scripts/verify.sh`(docs 레인) → 커밋 `docs: S0 번들 55 설계 38`.
- **0-66** jackson 2.18.11 + 순수본 sync — 대상 `pom.xml:18` · `pure/`(sync 로만). 변경 §3 A. 검증 ① `bash scripts/sync-pure.sh` 출력에 `tools/code_check.html` 이전/지금 ② `bash scripts/mvn.sh -q -B -Dtest='PureFrozenTest,PureRulesJsonTest,ProfileStoreTest' test` 초록 ③ `bash scripts/mvn.sh -q -B -Dtest=PureCodeCheckTest test`(node·표본 필요, 약 1분) 초록 ④ `bash scripts/mvn.sh -q -B -DexcludedGroups=db,corpus test`. 의존 S0. 커밋 `chore: 0-66 jackson 2.18.11 + 순수본 sync`.
- **0-69a** JUnit timeout 속성 — 대상 `src/test/resources/junit-platform.properties`(새) · `pom.xml`(속성 `junit.timeout`, surefire `systemPropertyVariables`). 변경 §3 B. 검증 ① `bash scripts/mvn.sh -q -B -Djunit.timeout=1ms -Dtest=PureFrozenTest test` → 빨강, 출력에 `timed out after 1 millisecond`(게이트 실동작) ② `bash scripts/mvn.sh -q -B -DexcludedGroups=db,corpus test` 초록(2분 안에 다 든다). 의존 0-66. 커밋 `feat: 0-69a JUnit 시험 상한 2분`.
- **0-69b** db·corpus 10분 — 대상 `scripts/verify.sh` L57·L59·L94 · `.github/workflows/verify.yml` L37. 변경: 각 `mvn` 인자에 `-Djunit.timeout=10m`. 검증 ① `bash -n scripts/verify.sh` ② `grep -c 'junit.timeout=10m' scripts/verify.sh` → 3, `.github/workflows/verify.yml` → 1 ③ `bash scripts/verify.sh`(tools 레인 — hooks-test). 의존 0-69a. 커밋 `feat: 0-69b db·corpus 시험 상한 10분`.
- **0-70** ErrorProne — 대상 `.mvn/jvm.config`(새) · `pom.xml`(속성·compiler) · ERROR 고친 파일들. 변경 §3 C. 순서: ① 전 실측 `time bash scripts/mvn.sh -q -B -Djacoco.skip=true -DskipTests clean test-compile` ② 파일 둘 ③ 같은 명령 → ERROR 목록 → 고치기(검사별 30 넘으면 OFF) ④ 후 실측 ⑤ `-q` 빼고 한 번 → WARNING 검사별 건수. 검증 ① `test-compile` 초록 ② 게이트 실동작: `Version.java` 에 `new IllegalStateException("probe");`(버리는 식) 한 줄 → 컴파일 빨강 `[DeadException]` → 되돌림 ③ `bash scripts/mvn.sh -q -B -DexcludedGroups=db,corpus test` 초록 ④ `SelfCheckTest` 기준선이 고친 파일 때문에 흔들리면(줄 수) 골든 갱신 diff 를 이력에. 의존 0-69b. 커밋 `feat: 0-70 ErrorProne ERROR 급 빌드 실패`.
- **0-68** PMD — 대상 `pom.xml`(속성 `pmd.version`·플러그인·test 의존성 `${pmd.version}`) · `config/pmd/ruleset.xml`(새) · `scripts/pmd-baseline.sh`(새) · `config/pmd/exclude-from-failure.properties`(새, 스크립트가 뜬다). 파일 넷 = 청크 규칙 예외(기준선 없이는 빨강 — 이력에). 변경 §3 D. 순서 ① pom·ruleset·스크립트 ② `bash scripts/pmd-baseline.sh` → 기준선 ③ 기준선 통계를 이력에(클래스 수·규칙별 건수 상위 5 — `grep -o '[A-Za-z]*' … | sort | uniq -c | sort -rn | head -5`). 검증 ① `bash scripts/mvn.sh -q -B pmd:check` 초록 ② 게이트 실동작: `Version.java` 에 `static void probe() { try { probe(); } catch (Throwable t) { throw new RuntimeException(t); } }` → `pmd:check` 빨강 `AvoidCatchingThrowable` → 되돌림 ③ `bash scripts/mvn.sh -q -B -DexcludedGroups=db,corpus test` 초록(PMD 가 안 돈다 — 출력에 PMD 줄 없음). 의존 0-70. 커밋 `feat: 0-68 PMD errorprone·bestpractices 기준선 래칫`.
- **0-65** PIT — 대상 `pom.xml`(속성·플러그인) · `scripts/pit.sh`(새). 변경 §3 E. 검증 ① `bash scripts/pit.sh`(시간 재기) → `out/pit/index.html` 있음 ② 이력에 시간·변이 수·점수·생존 수 ③ `bash scripts/mvn.sh -q -B -DexcludedGroups=db,corpus test` 초록(PIT 는 phase 없음 — 빠른 검증 불변). 의존 0-68. 커밋 `feat: 0-65 PIT 뮤테이션 — core.check 손 실행`.
- **0-67** 작성 규약 + 심의 표 — 대상 `CLAUDE.md` · `docs/import-review.md`. 변경 §3 F 글자 그대로. 검증 ① `bash scripts/doc-lint.sh CLAUDE.md docs/import-review.md` ② `bash scripts/mvn.sh -q -B -Dtest=ImportReviewDocTest test` ③ `bash scripts/verify.sh`(docs·java 레인). 의존 0-65. 커밋 `docs: 0-67 작성 규약 7줄 + 심의 표`.
- **V-33** 채움·무거운 레인·실측 — 대상 `m2/`(gitignore)·`.git/verify-stamp`. 순서 ① `bash scripts/offline-build.sh`(네트워크, 채움 + `--check`) → `du -sh m2` 전후를 이력에 ② `bash scripts/verify.sh` ③ `bash scripts/verify.sh --db`(pom 레인, 약 17분 — 다른 JVM 없이, 10GB 상한) ④ `bash scripts/verify.sh --corpus`(pure 레인, 약 7분) — ③④ 는 따로 낸다 ⑤ 다 초록이면 커밋 없음(이력 한 줄은 마무리 커밋에). 의존 0-67.
- **마무리** — CLAUDE.md 「마무리」 1~8. 2 의 `gate-probe.sh` 에서 `pure-frozen.patch`·`local-only.patch` 가 낡으면 다시 떠 커밋. 5 의 PR 본문에 행마다 시작 세 줄. 6 의 CI 에 `offline` 잡이 돈다(pom·.mvn 바뀜). 머지 뒤 `gh api 'repos/ejg93/toolbox-server/dependabot/alerts?state=open' --jq length` → 0 을 끝 보고에.

## 5. 금지 사항
- `pure/` 직접 수정(sync 만) · `src/main/resources/tools/` · 화면 · 규칙 `rules.yaml` · 픽스처 · `golden/corpus/`(V-33 에서 빨강이면 중단 조건).
- 빠른 검증(`-DexcludedGroups=db,corpus test`)에 PMD·PIT·JaCoCo check 를 묶지 않는다. `verify-fingerprint.sh` 레인 목록·`VerifyLanesTest` 를 안 고친다.
- ErrorProne WARNING 을 ERROR 로 올리거나(`-Xep:…:ERROR`) 전부 끄지(`-XepDisableAllChecks`) 않는다. 검사 끄기는 30건 넘는 것만.
- PMD exclude 를 §3 D 다섯 밖으로 늘리지 않는다 · 기준선을 늘리려고 다시 뜨지 않는다(새 지적은 고친다) · codestyle·design 묶음을 더하지 않는다.
- PIT 에 phase·레인·CI 를 붙이지 않는다 · `mutationThreshold` 를 두지 않는다.
- `@Timeout` 을 클래스마다 달지 않는다 · 상한을 넘기려고 시험을 쪼개거나 `@Disabled` 하지 않는다(넘으면 중단 조건).
- 판은 §2 실측 그대로(`maven-pmd-plugin` 3.28.0 · PMD 7.28.0 · `error_prone_core` 2.51.0 · `pitest-maven` 1.30.1 · `pitest-junit5-plugin` 1.2.3 · jackson 2.18.11). 다른 새 의존성 0.
- `verify.sh` 와 `git commit`, `--db`·`--corpus` 와 `git push` 는 따로 낸다. 빨간 트리를 `work/*` 에 안 올린다.

## 6. 최종 검증
- `bash scripts/verify.sh` 초록 + `--db`·`--corpus` 도장(V-33) + `bash scripts/offline-build.sh --check` 초록(채움 뒤).
- CI `verify`(`offline` 잡 포함)·`gate-probe`·`codeql` 전부 `completed success`.
- 게이트 실동작 넷을 이력에: timeout(0-69a ①) · ErrorProne(0-70 ②) · PMD(0-68 ②) · PIT 리포트 존재(0-65 ①).
- 회귀: `bash scripts/mvn.sh -q -B -DexcludedGroups=db,corpus test` 시험 수가 0-66 전과 같다(938 중 빠른 집합) · `app.jar` 크기·`package-check.sh` 불변(빌드 플러그인은 zip 밖).
- 머지 뒤 Dependabot 열린 경보 0.

## 7. 중단 조건
다음이면 임의로 풀지 말고 멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- §2 구조 설명이 실제와 다름(줄 번호는 ±5 까지 허용, 내용이 다르면 중단).
- 검증 2회 연속 빨강이고 원인이 플랜 밖 — 특히 0-66 ③(`PureCodeCheckTest`)이 자바 골든 쪽 차이 · `--db`·`--corpus` 의 골든 변화 · ErrorProne 이 `--add-exports` 로도 안 뜸(`IllegalAccessError`).
- 금지 사항을 어겨야 진행 가능 — 2분 넘는 빠른 시험이 나옴 · PMD 기준선 뒤 사다리 ②까지 빨강 · ErrorProne 검사 넷 넘게 OFF 가 필요.
- 스텝에 없는 파일 3개 넘게 수정(0-70 의 ERROR 고치기는 예외 — 검사별 건수를 이력에).

## 8. 불확실 항목(실행해야만 알 수 있는 것 — 첫 스텝 전 확인 아님, 그 행에서 실측)
- 0-66 ③ `PureCodeCheckTest` — portfolio 새 엔진(`when`·`tb-ignore`)이 픽스처 32 에서 자바 골든과 같은지. portfolio Node 대조는 37 중 35(실패 2 는 백엔드 전용 「파일에 없음」 규칙 — regex 가 아니라 비교 밖). 빨강이면 §3 A 사다리.
- 0-70 첫 ERROR 건수·검사 종류 · 컴파일 시간 증가 — 실측해 이력에. 끌 검사가 생기면 이름과 건수.
- 0-68 기준선 키가 플러그인 매칭과 맞는지(내부 클래스 꼴) — `pmd:check` 초록으로 확인, 사다리 §3 D. 기준선 규모(클래스 수·규칙별 건수).
- 0-65 PIT 시간·변이 수·점수 — 실측. 40분 넘으면 §3 E 사다리.
- V-33 `m2/` 증가량 · `--db`·`--corpus` 초록 여부(메모리 10GB 상한).
- 마무리 — Dependabot 재평가 시점(머지 뒤 몇 분). 0 이 아니면 경보 번호와 판을 보고.
