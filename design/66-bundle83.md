# 설계 66 — 번들 83: 가로 점검 Minor(0-84) · 코드 정리 잔손(5-98) (Fable 설계 · Opus 실행, 2026-11-13)

## 맥락
번들 82(설계 문서 없음, PR #125 머지 `7d4ce82`)가 가로 점검 Important 다섯을 닫고 Minor 열넷 + 사람 실측 여섯을 `0-84` 로 남겼다. 번들 81 마무리 리뷰 Minor 는 `5-98`(여덟). 사용자 2026-11-13 결정으로 둘을 한 번들로 닫는다. 가지는 `work/2026-11-13b`(번들 82 닫기 `4bdbe63` 이 로컬에만 있다 — 그 위에서 친다, main 은 `7d4ce82`).

**실측(2026-11-13, `4bdbe63` 트리 — 0-84 행의 사실과 다른 것 먼저)**
- **⑪ 「닿는 길 없음」 은 반만 맞다** — 화면에서 친 SQL 은 안 닿지만 `AnalyzeRoutes:105` `POST /api/analyze/runs/{id}/probe` → `SqlProbe.probe:209` 가 분석 폴더 매퍼 XML 의 SELECT 원문을 `SELECT 1 FROM (원문) tb_probe WHERE 1=0`(MySQL 은 `LIMIT 0`)으로 감싸 `executeQuery` 한다
- **⑭(a) 「둘째 줄이 백업에 남는다」 는 틀렸다** — `7e92a01` 의 그물 `ProfileRoutes.leaked:123` 이 400 으로 막는다(Jackson 은 여러 줄 평문을 `"abc def"` 로 접어 읽고, 그 글이 원문·지운 글 어디에도 없어 0-0<1). `backupText` 가 `createDirectories` 앞이라 백업 파일이 안 생긴다. 남는 것은 단언 없음
- **⑭(b)** — Javalin 6.7.0 `RouterConfig.javaLangErrorHandler` 는 Kotlin internal. `javap` 로 본 공개 메서드는 `setJavaLangErrorHandler$javalin(JavaLangErrorHandler)` 뿐(자바에서 부를 수 있다 — `$` 는 식별자 글자)
- **②에 행이 놓친 자리 하나** — `web/ProfileRoutes.java:34` `PASSWORD` 안 `\"(?:[^\"\\\\]|\\\\.)*\"`·`'(?:[^']|'')*'`
- **③④·②의 piiLog 는 순수본에 묶였다** — `rules.yaml` regex 규칙은 `golden/check/pure-rules.json` → portfolio → `pure/tools/code_check.html` 로 가고 `PureRulesJsonTest.pureHtmlEmbedsGolden`·`PureFrozenTest` 가 막는다. `portableToJavaScript` 의 `JAVA_ONLY` 가 `*+`·`++` 를 금지한다(piiLog 는 소유 수량자로 못 고친다). `RegexRule.applyText:44,65` 가 줄마다 맞춰 이차 시간은 한 줄 안에서만

**실측 — 0-84 넣는 것**
- **①** `web/LocalOnly.java:47` `if (SAFE_METHODS.contains(method)) return;` — GET·HEAD·OPTIONS 는 Origin·`Sec-Fetch-Site` 를 안 본다. 부작용·디스크·프로세스 GET 일곱: `/api/check/vcs`(`CheckRoutes:64` — git·svn 프로세스) · `/api/fs/list`(`FsRoutes:50` — `recent-paths` 쓰기) · `/api/fs/read`·`/api/fs/exists` · `/api/diff/file` · `/api/check/stack` · `/api/generate/templates`. CORS 헤더 없음(응답은 못 읽고 일으키기만). 정상 호출자 — 화면 XHR(`common.js:11-19`)·SSE(`:159,324`)는 `same-origin` · CLI(`LocalApp.send:91-116`, LocalConnector)·Puppeteer(node fetch)는 헤더 없음 · 주소 직접 입력은 `none` · tools 에 `<a href="/api…">`·`<img src=/api>` 없음. `LocalOnlyTest` 에 GET cross-site 시험 없음. `docs/import-review.md:19` 「쓰기 메서드는 같은 출처만」
- **②** 교대 반복(소유 수량자 없음, Java 전용 여섯): `core/analyze/JpaIndex.java:451` `text.replaceAll("'(?:[^']|'')*'", "''")`(사용자 Java 의 JPQL 리터럴) · `core/gen/Validation.java:70` `PAREN_ATOM`·`:268` `columnsIn`(CHECK 조건 — 붙여 넣은 DDL 이 `POST /api/gen/dto` → `GenRoutes:82`·`:113` 로 닿는다) · `core/gen/DdlGen.java:57` `STR`·`:59` `PG_CAST`(스냅샷 default, `defaultOf:346`) · `ProfileRoutes:34`. 선례 `DdlReader.java:43,69` `'(?:[^']++|'')*+'` · 시험 선례 `DdlReaderTest.quotedNameWithSpacesAndNoCatastrophicBacktracking`(`"가".repeat(20_000)`)
- **⑤** `CheckRunner.runFiles:124-151` — 파일마다 `ctx.checkCancelled()`(`:126`) 뒤 `run.apply(s)`(`:145`). `RuleSet.Run.apply:274-303` 의 규칙 반복 안에서는 취소를 안 본다. `StackOverflowError` 는 `JobManager.run:88` 이 받아 검사 전체가 FAILED. `CheckRunnerTest` 없음. 못 읽은 파일은 `skipped`(화면 「못 읽음 N」)
- **⑧** `core/quality/QualitySql.java:90-105` — `name()`(`:129`, `IDENT` 만 통과)을 그대로 넣는다. 예약어(`ORDER`·`DESC`·`KEY`)면 틀린 SQL. 이름 출처 — schema·table·column 은 스냅샷 카탈로그 그대로(`deliverable_sql.html:1032`), `keys` 는 사용자가 친 글. 예약어 목록은 `resources/gen/ddl-types.yaml:26` `reserved`(다섯 방언 합집합, 79 낱말) — `DdlGen.Spec.reserved`(`:104`)·`DdlGen.id:442`. `core/gen` 은 `core/quality` 를 import 하지 않는다(순환 없음). 골든 `golden/quality/*.sql` 40(값 `APP`·`TB_ORDER`·`ORD_DT`·`ORD_NO`·`LINE_NO` — 예약어 아님). `QualitySqlPostgresTest:48` 은 대문자 `APP`/`TB_ORDER` 로 소문자 표를 친다(접기에 기댄다) → **늘 감싸거나 대소문자 섞인 이름을 감싸면 깨진다**
- **⑨** `LocalFiles.check:120-123` 의 UNC 거절(`Refused(400, "네트워크·장치 경로는 받지 않는다 — 로컬 드라이브만")`)을 안 거치는 프로필 경로 넷: `output.dir`(`FsRoutes.outRoot:104`·`Outputs.dir:31`·`LogicalRoutes.outFile:220`) · `deliverable.templateDir`(`DeliverableRoutes.dir:279`) · `deliverable.mapping`(`:275`) · `codecheck.customRules`(`RuleSet.load:91`). 진입로는 손으로 고친 YAML 뿐. `Profile.Output` 은 compact 생성자 없음 · `Deliverable`·`CodeCheck` 는 있음
- **⑩** `core/vcs/Cli.java:104-113` — 시간 초과·인터럽트에 `p.destroyForcibly()` 만. `descendants()` 0 건. ssh·자격 증명 도우미·alias 셸 같은 손자가 남는다
- **⑫** `core/profile/Profile.java:60-61` `PASSWORD_IN_URL` — 놓치는 꼴: MySQL `(host=h,password=p)`(앞 글자 `(`·`,`) · CUBRID `jdbc:cubrid:h:p:db:user:pw:` · Oracle `:oci:u/p@`. 쓰는 곳은 `Connection` 생성자 하나. gate-probe `scripts/probes/url-password.patch` 가 정규식 줄을 문맥으로 든다 → 정규식을 바꾸면 패치를 다시 떠야 한다
- **⑬** `cli/MakeTemplates.java:24-42` — `--out`(기본 `templates/deliverable/example`)·`--mapping`(기본 `mappings/deliverable/example.yaml`)에 검사 없이 덮어쓴다. 부르는 곳 `scripts/make-example-templates.sh:8`. 직접 시험 없음
- **⑭(c)** `JobManager.run:79-94` — `catch (Exception e)` 몸 안에서 Error 가 나면(형제 `catch (Error)` 는 못 받는다) `finally` 는 로그뿐이라 RUNNING 에 남는다. `Job.finished():90`

**실측 — 5-98**
- **①** `JavaShape.java:212` `noted(comments, line(sw) - 1, line(entries.get(0)) - 1)` — 윗줄이 코드 줄이어도 꼬리 주석을 센다. **행 문구(「윗줄은 주석만 있는 줄로」)대로 고치면 `commons-collections/…/map/Flat3Map.java:658`(`if (value == null) {  // drop through` 다음 줄 `switch`)가 다시 지적된다** — R-28 이 일부러 면제했고 `JavaShapeTest.fallThroughHeadNote:63` 이 그 꼴을 음성으로 단언한다 → §3 에서 「앞 형제 문장의 꼬리 주석만 뺀다」 로 좁힌다
- **②** `JavaShape.java:61` `FALL_NOTE = (?i)falls?[ -]?thr(?:u|ough)|drop(?:s|ped)?[ -]?through|no break` — `no breaking change`·`no breakpoint` 에도 걸린다. 표본 Java 주석 0
- **③** `StarImportFix.java:50` `DOC_REF` 는 태그 뒤 첫 마디만(`{@link Foo#bar(Set)}` 의 `Set` 은 안 센다). 표본 — 별표 파일 171(juliet 뺌) 가운데 괄호 인자 든 참조 7 파일, 전부 그 형식을 코드에서도 써서 실물 깨짐 0
- **④** `SwitchFix.from:73-86` — default 위 주석·빈 줄을 소유 판정 없이 전부 끌고 간다. 표본 13 은 전부 마지막 default(대상 밖). 기존 단언(`CleanupTest.switchStage:242,247`)·픽스처(`switch/Pos.java:19,89-90`)의 주석은 `default:` 와 **같은 들여쓰기**
- **⑤** `StarImportFix.constantLike:215-217` — 밑줄 든 대문자 수신자를 늘 상수로 본다. 파일이 선언한 상수는 `unknown()` 의 `VariableDeclarator` 빼기가 이미 거른다 → `constantLike` 가 필요한 것은 물려받거나 static import 한 상수뿐. 기존 단언 `CleanupTest:400` 입력은 `class A extends B`
- **⑥** `PiiFix.apply:36-40` 의 `targets.contains(id)` 길 — 시험이 전부 `Cleanup.RULES` 전체. `InterruptFix:79-81` 탭 분기 — union 은 공백 입력만 단언
- **⑦** `StarImportFix.used:154` `cu.findAll(SimpleName.class)` 가 scope 있는 `ClassOrInterfaceType`(`java.io.Serializable`)의 이름도 담는다 · `:155-159` `Name` 도 한정자 있는 것(`@a.b.Foo`)까지. 표본 1(`jeecg/…/CgformEnhanceJsEntity.java:25` — cleanup corpus 밖)
- **⑧** `PiiFix.FREE`(`:25`, `private`, `properties`·`xml`) 와 `rules.yaml:455·464` globs 를 대조하는 자리 없음. 따옴표 밖 단언은 js·sql(`finalReview81:416-424`)·properties(`piiStage:303-309`)뿐
- **크기** — `JavaRules` 메서드 30 = 상한(안 고친다) · `JavaShape` 257 줄·17 · `StarImportFix` 228·10 · `SwitchFix` 142·8 · `PiiFix` 92·4 · `Cleanup` 850·27(안 고친다). 손어림 복잡도 `JavaShape.apply` 13 · `StarImportFix.typeRefs` 12 · `SwitchFix.refuse` 12
- **수 단언 불변** — 규칙 147 · 정규식 63 · `Cleanup.RULES` 32. 이 번들은 `rules.yaml` 을 안 고친다

**범위 밖** — `pure/` · `rules.yaml`(③④·piiLog → 새 대기 행 `5-100`, 5-31 과 같이) · ⑥ `DdlReader` 백슬래시 · ⑦ `RegexRule` SQL 모드 · ⑪ `ReadOnlyJdbc`(셋 다 compare ㉜ 「후보로」) · 시간 상한(끊을 수 있는 CharSequence) · `QualitySql` 대소문자 섞인 이름 · `Cli` 정상 종료 뒤 join 대기(손자가 파이프를 쥔 꼴) · `FieldAccessExpr` 정식 이름(`java.util.Collections.x()`) · `PiiFix` 대시 꼴의 따옴표 밖 가림(번들 80 결정 그대로) · 0-84 사람 실측 여섯(사람 몫) · 5-31·2-17·보류·조건부 행

## 사용자 결정(2026-11-13)
1. **db 레인** — ②(Validation·DdlGen)·⑧ 넣는다, **⑥ 뺀다**(후보) → `--db` 한 번
2. **순수본 묶임 셋(③·④·piiLog)** — 5-31 과 묶어 대기(새 행 `5-100`, portfolio 인계)
3. **⑦·⑪ 둘 다 뺀다** — 후보로, 근거를 compare 에
4. **⑤ 싼 판** — 규칙 사이 취소 + 파일 하나의 `StackOverflowError` 는 「못 읽음」 에 셈. 시간 상한 없음
5. **① `/api/*` 전부** — 메서드와 무관하게 cross-site·same-site 거절. 화면 페이지(`/`·`/tools/*`)는 그대로
6. **⑨ 프로필 로드 거절**
7. **⑬ 실행 폴더 아래만**(CLAUDE.md 규칙 7 글은 안 고친다)
8. **한 번들 · PR 하나 · 머지 위임 있음** · 설계 Fable · 실행 Opus

설계가 정한 것(전부 [고정]):
- **행 차례** `S0`→`0-85a`→`0-85b`→`0-85c`→`0-85d`→`0-85e`→`0-85f`→`0-85g`→`0-85h`→`0-85i`→`5-99a`→`5-99b`→`5-99c`→`5-99d`→`V-62a`→`R-29`→`V-62`→마무리. 행은 서로 독립(막히면 그 행만 `wip/` 로). java 레인만 타는 행이 앞, db 레인 둘(`0-85h`·`0-85i`)이 0-85 끝
- **`rules.yaml`·`pure-rules.json`·`golden/quality/*`·`golden/cleanup/*`·`golden/check/*` diff 0** — 이 번들의 골든 변화 기대는 0. corpus 골든도 0 이 기대(V-62a 가 확인)
- **⑧ 은 예약어만 감싼다** — 감쌀 때 방언의 접기를 그대로 적용(oracle 대문자 · pg 소문자 · mysql·mssql·sybase 그대로). 그래야 사용자가 친 `keys`·CLI 이름(접기에 기대던 것)이 안 깨진다. 대소문자 섞인 이름은 지금처럼 안 감싼다(범위 밖)
- **5-98 ①** 은 행 문구가 아니라 「switch 의 앞 형제 문장이 바로 윗줄에서 끝나면 그 줄 꼬리 주석은 머리 주석이 아니다」 — Flat3Map 면제 유지, corpus diff 0
- **5-98 ④** 소유 판정은 들여쓰기 — default 줄보다 깊이 들여쓴 `//` 줄 주석은 앞 case 것(그 줄과 그 위는 안 옮긴다). 블록 주석은 자르지 않는다
- **⑭(a)** 는 코드 불변 — 단언 하나로 굳힌다. **⑭(b)** 는 `setJavaLangErrorHandler$javalin`(시험이 판 올림을 잡는다)

## 1. 목표와 범위
- 한 문장: 가로 점검 Minor 가운데 지금 닫을 수 있는 열하나와 코드 정리 잔손 여덟을 닫고, 못 닫는 것(순수본 묶임 셋·후보 셋·사람 실측 여섯)은 행·후보·사람 몫으로 처분한다.
- 완료 시 체감: 다른 사이트 페이지가 `http://127.0.0.1:<포트>/api/…` 를 GET 으로 일으키면 403 · 프로필 경로에 `\\서버\공유` 를 적으면 로드 거절 · url 의 `(host=h,password=p)`·CUBRID·`:oci:` 비밀번호 거절 · 품질 SQL 에서 `ORDER`·`DESC` 컬럼이 따옴표로 감싸짐 · 깊은 중첩·긴 줄 파일 하나가 검사 전체를 FAILED 로 안 만들고 「못 읽음」 에 셈 · VCS 조회 시간 초과에 ssh 손자가 안 남음 · 붙여 넣은 DDL 의 긴 CHECK 리터럴이 500 을 안 냄 · `import java.io.*;` + `implements java.io.Serializable` 뿐인 파일은 import 줄이 사라짐 · `x(); // fall through` 다음 줄 switch 는 떨어짐 지적이 남.
- In scope: `web/LocalOnly`·`App`·`ProfileRoutes` · `core/profile/Profile` · `core/job/JobManager` · `cli/MakeTemplates` · `core/vcs/Cli` · `core/check/CheckRunner`·`RuleSet`(Run 두 줄)·`JavaShape`·`StarImportFix`·`SwitchFix`·`PiiFix`(가시성 한 낱말) · `core/analyze/JpaIndex` · `core/gen/Validation`·`DdlGen` · `core/quality/QualitySql` · 시험(행마다) · `scripts/probes/url-password.patch` · `docs/import-review.md:19` · `design/compare-2026-10-28.md` ㉜ · `PLAN.md`·`PROGRESS.md`
- Out of scope: 「범위 밖」 전부 · 새 의존성 · 화면 HTML·JS·CSS · `rules.yaml` · 픽스처 파일(단언은 전부 인라인)

## 2. 현재 구조 요약
「맥락」 의 실측이 요약이다. 덧붙이는 함정:
- **자바를 고친 행마다 `bash scripts/mvn.sh -q -DskipTests verify` 를 먼저** — 빠른 verify 는 PMD·SpotBugs 를 안 본다. PMD 단골: `AvoidLiteralsInIfCondition` · `AvoidFieldNameMatchingMethodName` · `UseLocaleWithCaseConversions` · `LiteralsFirstInComparisons` · 시험 `AvoidDuplicateLiterals`(같은 글 넷 이상은 상수). SpotBugs `REDOS` — 겹치는 선택 묶음·양옆 공백(번들 82 에서 `ProfileRoutes` 가 걸렸다)
- **Bash heredoc·python 안 `\` 는 도구가 벗긴다** — 백슬래시 든 자바 문자열·정규식은 Edit 도구로만
- `SelfCheckTest.mainMatchesBaseline` — 자기 `src/main` 에 모든 규칙. 새 지적은 빨강. `catch (Error)` 는 `java.catchThrowable`(JobManager 는 `tb-ignore` 꼬리 주석) — `catch (StackOverflowError e)` 가 걸리는지는 돌려 봐야 안다(§8)
- `metric.complexity` ≤ 15 · `metric.classMethods` ≤ 30(`JavaRules` 30 = 상한 — 안 고친다) · `metric.methodLength` ≤ 100 · 라우트 몸은 `private static void`
- `LocalOnly.check` 는 분기가 이미 열둘 안팎 — 새 검사는 메서드로 뺀다
- `JobManager.where(Throwable)` 은 public(0-83e) — 클래스 이름 + 우리 프레임만
- `Profile` 레코드 생성자의 `IllegalArgumentException` 은 `ProfileStore.load` 가 감싸 `ProfileUnreadable` 400 + 사유로 나간다(`badRegexInProfileFailsAtLoad` 꼴)
- `Cli.run(Exe, List<String>, Path, Duration)` 은 public — 시험이 짧은 시간 상한을 줄 수 있다. 실행 파일은 `Exe` 둘뿐(`ArchitectureTest` 가 `ProcessBuilder` 자리 고정 — `ProcessHandle` 은 안 건다)
- `RuleSet.load(profile, base)` — `codecheck.customRules` 가 `base.resolve` 로 풀려 시험이 임시 yaml 규칙을 줄 수 있다(`CheckRulesTest:119` 꼴)
- `CleanupTest` 도우미 `cleanup(null)`·`own`(finder 있는 것)·`skipsOf` · 인라인 입력은 `new Source("t/A.java", 글, null, "LF", "java")`
- 레인(`scripts/verify-fingerprint.sh`): `web/*`·`cli/*`·`core/profile`·`core/job` = java · `core/check`·`core/analyze`·`core/vcs`·그 시험 = java+corpus · `core/gen`·`core/quality` = java+db+corpus · `scripts/probes` = tools · `docs/import-review.md` = java+docs · `design/**` = 없음 · `PLAN.md`·`PROGRESS.md` = docs

## 3. 설계 결정 — 전부 [고정]

### 0-85a — GET 도 cross-site 거절(①)(§3-1)
- `web/LocalOnly.java` — Host 검사 뒤, `SAFE_METHODS` return **앞** 에 `crossSiteApi(ctx)` 한 줄. 새 메서드:
  ```java
  /** 0-85a — /api/ 는 메서드와 무관하게 다른 사이트가 일으킨 요청을 거절한다(GET 도 git 프로세스·recent-paths 쓰기를 일으킨다). 헤더가 없으면(CLI·옛 브라우저) 통과 */
  private static void crossSiteApi(Context ctx) {
      String site = ctx.header(SEC_FETCH_SITE);
      if (ctx.path().startsWith("/api/") && site != null && !SAME_ORIGIN.equals(site) && !NONE.equals(site)) {
          reject(ctx, new ForbiddenResponse("다른 출처의 요청"), "sec-fetch-site");
      }
  }
  ```
  상수 `SEC_FETCH_SITE = "Sec-Fetch-Site"`·`SAME_ORIGIN = "same-origin"`·`NONE = "none"`(기존 `:57-58` 도 이 상수로). 기존 쓰기 메서드 가지(`:50-61`)는 그대로(경로와 무관하게 건다)
- 클래스 javadoc 에 셋째 `<li>` 「교차 출처 읽기 요청 — `/api/` 는 GET 도 `Sec-Fetch-Site` 가 same-origin·none 이거나 없을 때만」
- `docs/import-review.md:19` — 「쓰기 메서드는 같은 출처만」 → 「쓰기 메서드는 같은 출처만, `/api/` 는 GET 도 다른 사이트 요청 거절(`Sec-Fetch-Site`)」
- 시험 `LocalOnlyTest.crossSiteGetIsForbidden`(기존 `raw(method, path, host, headers, body)` 도우미): `GET /api/ping` + `Sec-Fetch-Site: cross-site` → 403 · `same-site` → 403 · `same-origin` → 200 · `none` → 200 · 헤더 없음 → 200 · `GET /tools/common.js` + `cross-site` → 200(페이지는 통과)
- 골든 0

### 0-85b — 프로필: 경로 UNC 거절(⑨) · url 비밀번호 빈칸(⑫)(§3-2)
- `core/profile/Profile.java`
  - 새 `static String local(String field, String path)`(package-private): `path != null` 이고 `path.trim().replace('/', '\\').startsWith("\\\\")` 면 `IllegalArgumentException(field + ": 네트워크·장치 경로는 받지 않는다 — 로컬 드라이브만")`. 아니면 그대로 돌려준다. javadoc 「0-85b — 절대 규칙 1. 서버가 열면 SMB 로 나간다. `LocalFiles.check` 와 같은 글. 상대 경로는 그대로」
  - `Output` 에 compact 생성자 `local("output.dir", dir);` · `Deliverable` compact 첫 두 줄 `local("deliverable.templateDir", templateDir); local("deliverable.mapping", mapping);` · `CodeCheck` compact 첫 줄 `local("codecheck.customRules", customRules);`
  - `PASSWORD_IN_URL` 을 아래로(Edit 도구로):
    ```java
    "(?i)(?:[?;&:(,]\\s*(?:password|passwd|pwd)\\s*=)|(?://[^/@\\s]+:[^/@\\s]+@)|(?::(?:thin|oci8?):[^@/\\s]+/[^@\\s]+@)"
            + "|(?:^\\s*jdbc:cubrid:[^:]*:[^:]*:[^:]*:[^:]*:[^:?\\s]+:)"
    ```
    javadoc 에 「MySQL 키-값 꼴 `(host=h,password=p)` · Oracle oci · CUBRID 자리 꼴 `jdbc:cubrid:호스트:포트:DB:사용자:비밀번호:`(비밀번호 자리가 비면 통과)」
- 시험 `ProfileStoreTest`
  - `passwordInUrlIsRejected` 양성에 더한다: `jdbc:mysql://(host=h,port=3306,user=u,password=secret)/db` · `jdbc:mysql://address=(host=h)(password=secret)/db` · `jdbc:oracle:oci:app/secret@tns` · `jdbc:oracle:oci8:app/secret@tns` · `jdbc:cubrid:h:33000:db:dba:secret:?charset=utf8`. 음성에 더한다: `jdbc:mysql://(host=h,port=3306)/db` · `jdbc:cubrid:h:33000:db:dba::` · `jdbc:cubrid:h:33000:db:::` · `jdbc:oracle:oci:@tns`
  - 새 `networkPathsInProfileAreRejected`: `new Profile.Output("\\\\srv\\share\\out")`·`new Profile.Output("//srv/share/out")` → `IllegalArgumentException` · `Deliverable`(templateDir·mapping 각각)·`CodeCheck`(customRules) 같은 꼴 · `new Profile.Output("out")`·`("C:/tb/out")`·`(null)` 은 통과 · YAML 한 건(`output:\n  dir: '\\\\srv\\share'`)을 `ProfileStore.load` → `RuntimeException`
- `scripts/probes/url-password.patch` — 정규식 줄이 문맥이라 다시 뜬다: `Profile.java` 의 `if (url != null && PASSWORD_IN_URL` 을 `if (url != null && false && PASSWORD_IN_URL… { // gate-probe url-password` 로 잠깐 고쳐 `git diff -- src/main/java/kr/ejg/toolbox/core/profile/Profile.java > scripts/probes/url-password.patch` → `git checkout -- <그 파일>` 이 아니라 **Edit 로 되돌린다**(이 행의 다른 변경이 같은 파일에 있다 — 패치는 이 행 변경을 커밋할 내용으로 다 넣은 뒤, 탐침 한 줄만 더한 차이여야 한다: 먼저 `git add` 로 이 행 변경을 스테이지하고 `git diff`(스테이지 대비)로 뜬다)
- 골든 0

### 0-85c — Error 가 로그·RUNNING 에 안 남게(⑭ b·c)(§3-3)
- `core/job/JobManager.java` `run` 의 `finally` 맨 앞:
  ```java
  if (!job.finished()) { // 0-85c — catch 몸 안에서 다시 Error 가 나면(메모리 부족) 끝 이벤트가 안 나가 RUNNING 에 멈춘다
      job.emit(Job.EventName.FAILED, Map.of("message", "Error"));
  }
  ```
  (`"message"` 는 그 파일의 기존 상수가 있으면 그것 — PMD `AvoidDuplicateLiterals`)
- `web/App.java` — 새 `static void quietErrors(io.javalin.config.JavalinConfig cfg)`:
  ```java
  /** 0-85c — java.lang.Error 는 예외 매퍼를 안 탄다. Javalin 기본은 글·스택을 로그에 쓴다(규칙 3) — 500 + 클래스 이름과 우리 프레임만. 6.7.0 은 공개 설정이 없어 내부 이름으로 건다 */
  static void quietErrors(io.javalin.config.JavalinConfig cfg) {
      cfg.router.setJavaLangErrorHandler$javalin((res, error) -> {
          res.setStatus(500);
          LOG.error("처리 안 된 Error — {}", kr.ejg.toolbox.core.job.JobManager.where(error));
      });
  }
  ```
  `Javalin.create(cfg -> { … })` 람다 첫 줄에 `quietErrors(cfg);`
- 시험
  - `JobManagerTest.errorInsideCatchStillEndsFailed`: 본문이 `getMessage()` 가 `StackOverflowError` 를 던지는 `RuntimeException` 익명 하위 클래스를 던진다 → `awaitFinished` → `FAILED`(고치기 전엔 RUNNING 에 멈춰 시간 초과 — RED)
  - `JsonErrorTest.errorLeavesNoMessage`: `Javalin.create(App::quietErrors)` + `App.jsonErrors(own)` · `/boom` 이 `throw new AssertionError("rrn='800101-1234567'")` → 500 · 로그 이벤트 전체(`getFormattedMessage` + `ThrowableProxyUtil.asString`)에 `1234567` 없음(기존 `unexpectedExceptionLeavesNoMessage` 의 appender 꼴 그대로)
- 사다리: `setJavaLangErrorHandler$javalin` 이 컴파일 안 되거나 ErrorProne·PMD 가 막으면 — App 쪽을 빼고(JobManager 만) 이력 「정한 것」 에 「Javalin 6.7.0 공개 API 없음 — compare ㉜ 후보」

### 0-85d — `make-templates` 쓰기 자리(⑬)(§3-4)
- `cli/MakeTemplates.java` — `call()` 첫머리에서 `out = inside("--out", out); mapping = inside("--mapping", mapping);`
  ```java
  /** 0-85d — 실행 폴더 아래만(절대 규칙 7 — 도구가 제 폴더 밖에 쓰지 않는다). UNC 도 거절 */
  private Path inside(String option, Path p) {
      Path base = Path.of("").toAbsolutePath().normalize();
      Path abs = p.toAbsolutePath().normalize();
      if (p.toString().replace('/', '\\').startsWith("\\\\") || !abs.startsWith(base)) {
          throw new picocli.CommandLine.ParameterException(spec.commandLine(), option + " 는 실행 폴더 아래여야 한다: " + base);
      }
      return abs;
  }
  ```
- 새 시험 `cli/MakeTemplatesTest`(기존 cli 시험이 명령을 돌리는 꼴 그대로 — `MainTest`·`CliFixture` 를 보고 맞춘다):
  - `--out <@TempDir 밖 폴더>` → 종료 코드 ≠ 0 · 그 폴더에 파일 0
  - `--mapping <@TempDir>/m.yaml` → 종료 코드 ≠ 0
  - `--out target/tb-make-templates/forms --mapping target/tb-make-templates/m.yaml` → 0 · xlsx 11 · yaml 있음(`target/` 은 gitignore — 저장소 `templates/` 를 안 건드린다)
- `scripts/make-example-templates.sh` 불변(기본값이 실행 폴더 아래)

### 0-85e — VCS 시간 초과에 손자 프로세스(⑩)(§3-5)
- `core/vcs/Cli.java` — 새 `private static void kill(Process p)`:
  ```java
  /** 0-85e — ssh·자격 증명 도우미·alias 셸 같은 손자까지. 목록은 부모를 끄기 전에 뜬다 */
  private static void kill(Process p) {
      List<ProcessHandle> kids = p.descendants().toList();
      p.destroyForcibly();
      kids.forEach(ProcessHandle::destroyForcibly);
  }
  ```
  시간 초과 가지·`InterruptedException` 가지의 `p.destroyForcibly()` 둘을 `kill(p)` 로. 정상 종료 뒤 join 은 그대로
- 시험 `WorkingCopyTest.timeoutKillsGrandchildren(@TempDir Path tmp)`: git 없으면 `assumeTrue` 로 건너뜀(기존 `gitIgnoresRepoFsmonitorCommand` 꼴) · `Cli.run(Cli.Exe.GIT, List.of("-c", "alias.tbhang=!sleep 4; echo hit > late.txt", "tbhang"), tmp, Duration.ofSeconds(1))` → `IllegalStateException`(시간 초과) · 6초 기다린 뒤 `tmp/late.txt` 없음(고치기 전엔 셸 손자가 살아 파일이 생긴다 — RED)
- 사다리: 이 PC 의 git 이 alias 셸을 손자가 아닌 꼴로 띄워 RED 가 안 나오면 — 시험을 「시간 초과 예외 + 6초 뒤 파일 없음」 그대로 두고(GREEN 만) 이력에 「RED 못 봄」 한 줄

### 0-85f — 코드 검사: 규칙 사이 취소 · 파일 하나의 스택 넘침은 건너뜀(⑤)(§3-6)
- `core/check/RuleSet.java` `Run` — 필드 `private Runnable tick = () -> { };` · `public Run onRule(Runnable r) { tick = r; return this; }`(javadoc 「0-85f — 규칙 하나 앞마다 부른다(취소 확인)」) · `apply` 의 `for (Rule r : enabled) {` 몸 첫 줄 `tick.run();`
- `core/check/CheckRunner.java` `runFiles` — `RuleSet.Run run = rules.run();` 뒤 `if (ctx != null) { run.onRule(ctx::checkCancelled); }` · 파일 반복의 `if (apply) { … } else { run.observe(s); }` 를 감싼다:
  ```java
  try {
      … 기존 몸 …
  } catch (StackOverflowError e) { // 0-85f — 깊은 중첩·긴 줄 하나가 검사 전체를 FAILED 로 만들지 않게. 그 파일만 「못 읽음」 에
      skipped += apply ? 1 : 0;
  }
  ```
  `runFiles` 복잡도가 15 를 넘으면 반복 몸을 `private static` 메서드로 뺀다(반환은 `checked`·`lines`·`skipped` 를 실을 작은 record 또는 int[] — 실행이 고른다, 동작 같게)
- 새 시험 `core/check/CheckRunnerTest`
  - `stackOverflowFileIsSkipped(@TempDir Path tmp)`: `tmp/rules.yaml` 에 정규식 규칙 둘(`id: t.deep` regex `(?:a|b)*c` globs `['*.txt']` · `id: t.hit` regex `HIT` globs `['*.txt']`, 나머지 칸은 `rules.yaml` 의 regex 규칙 하나를 베낀다) · `tmp/src/deep.txt` = `"ab".repeat(200_000)` 한 줄 · `tmp/src/ok.txt` = `HIT` · `RuleSet.load(profile(new Profile.CodeCheck(null, null, tmp/rules.yaml 절대 경로)), tmp)` · `CheckRunner.run(tmp/src, rules, files, null)` → 예외 없음 · `skipped() == 1` · 지적에 `t.hit` 한 건(`ok.txt`)
  - `cancelBetweenRules`: `rules.run().onRule(() -> { throw new JobContext.JobCancelledException(); })` 의 `apply(source)` → `JobCancelledException`
- 사다리: `*.txt` 가 파일 목록에서 빠지면 `*.properties` 로 · `(?:a|b)*c` 가 20 만 자에서 안 넘치면 200 만 자로 · `catch (StackOverflowError)` 가 `java.catchThrowable` 에 걸리면 `JobManager:88` 꼴 `tb-ignore` 꼬리 주석
- 골든 0(`self-check.txt` 포함)

### 0-85g — 정규식 스택 넘침: JPQL 리터럴 · 프로필 비밀번호 식(②·⑭a)(§3-7)
- `core/analyze/JpaIndex.java:451` — `"'(?:[^']|'')*'"` → `"'(?:[^']++|'')*+'"`
- `web/ProfileRoutes.java:34` `PASSWORD` — `\"(?:[^\"\\\\]|\\\\.)*\"` → `\"(?:[^\"\\\\]++|\\\\.)*+\"` · `'(?:[^']|'')*'` → `'(?:[^']++|'')*+'`(그 밖 글자 불변)
- 시험
  - `JpaIndexTest.longLiteralDoesNotOverflow`: `jpql("select e from E e where e.name = '" + "a".repeat(200_000) + "'", …)` 가 예외 없이 돌고 표 하나(기존 jpql 시험의 인자 꼴 그대로)
  - `ProfileRoutesTest.longQuotedPasswordDoesNotOverflow`: `withoutPasswords("connections:\n  - id: a\n    password: '" + "a".repeat(200_000) + "'\n")` 결과에 `password: ''` · 큰따옴표 꼴도
  - `ProfileRoutesTest.multiLinePlainPasswordIsRefused`(⑭a — 코드 불변): `backupText("connections:\n  - id: a\n    password: abc\n      def\n")` → `BadRequestResponse`
- 골든 0. 잘 닫힌 리터럴에서 결과가 같다. 안 닫힌 리터럴(`'a''b`)은 되돌아가 짧게 맞추던 것이 안 맞게 된다(`DdlReader` 선례와 같은 처분) — corpus 골든이 움직이면 §7

### 0-85h — 정규식 스택 넘침: CHECK 조건 · 기본값(②, db 레인)(§3-8)
- `core/gen/Validation.java:70` `PAREN_ATOM` 의 `'(?:[^']|'')*'` → `'(?:[^']++|'')*+'` · `:268` `replaceAll` 식도 같게
- `core/gen/DdlGen.java:57` `STR` → `"^N?('(?:[^']++|'')*+')$"` · `:59` `PG_CAST` 의 같은 조각도
- 시험
  - `ValidationTest.longLiteralDoesNotOverflow`: `Validation.columnsIn("C IN ('" + "a".repeat(200_000) + "')", 표)` → `[C]` 예외 없음 · `Validation.normalize("(C = ('" + "a".repeat(200_000) + "'))")` 예외 없음
  - `DdlGenTest.longDefaultDoesNotOverflow`: 기본값이 `'` + `"a".repeat(200_000)` + `'` 인 컬럼 · `…'::character varying` 인 컬럼으로 기존 DDL 생성 도우미를 돌려 예외 없음(그 시험 클래스의 표·컬럼 만드는 꼴 그대로)
- 골든 0(`golden/gen/**`·`golden/corpus/db-*`·`ddl-*`)

### 0-85i — 품질 SQL 예약어 이름 감싸기(⑧, db 레인)(§3-9)
- `core/gen/DdlGen.java` — 새 `public static boolean reserved(String name)`: `name != null && spec().reserved().contains(name.toUpperCase(Locale.ROOT))`(스펙을 읽는 기존 정적 길 — `:95-116` 의 로더 이름을 그대로 쓴다). javadoc 「0-85i — `ddl-types.yaml` 의 예약어(다섯 방언 합집합). 품질 SQL 이 같이 쓴다」
- `core/quality/QualitySql.java`
  ```java
  /** 0-85i — 예약어 이름만 감싼다. 감쌀 때 방언의 접기를 그대로(oracle 대문자·pg 소문자) — 안 감싼 이름과 같은 객체를 가리킨다. 대소문자 섞어 만든 이름은 지금처럼 못 가리킨다 */
  static String id(String s, String dk) {
      String t = name(s);
      if (!DdlGen.reserved(t)) {
          return t;
      }
      return switch (dk) {
          case "mysql" -> "`" + t + "`";
          case "mssql", "sybase" -> "[" + t + "]";
          case "oracle" -> "\"" + t.toUpperCase(Locale.ROOT) + "\"";
          default -> "\"" + t.toLowerCase(Locale.ROOT) + "\"";
      };
  }
  ```
  `render` 의 `name(table)`·`name(schema)`·`name(column)`·`name(ks.get(i))` 넷을 `id(…, dk)` 로. `__SCHEMAS__` 리터럴(`'" + name(s) + "'`)은 그대로 `name`
- 시험 `QualitySqlTest.reservedNamesAreQuoted`: `render("format", "oracle", "APP", "order", "desc", …)` 의 sql 에 `APP."ORDER"`·`"DESC"` · `mysql` → `` APP.`order` `` · `mssql` → `APP.[order]` · `pg`(입력 `ORDER`·`DESC`) → `APP."order"`·`"desc"` · `keydup` 의 keys `["KEY", "ORD_NO"]` → 첫째만 감싸짐 · 기존 `goldenFortyAndNoCorePlaceholderLeft`·`QualitySqlPostgresTest` 불변
- 골든 `golden/quality/*.sql` 40 diff 0 · `ArchitectureTest.noPackageCycles` 초록(quality → gen 한 방향)

### 5-99a — 떨어짐 머리 주석 · `no break` 낱말 경계(①②)(§3-10)
- `core/check/JavaShape.java`
  - `FALL_NOTE` 의 `no break` → `\\bno break\\b`(그 밖 불변)
  - `fallThrough` 의 `noted(comments, line(sw) - 1, …)` → `noted(comments, headFrom(sw), …)`. 새 메서드:
    ```java
    /** 5-99a — 머리 주석 범위의 첫 줄. 바로 윗줄이 앞 형제 문장의 끝 줄이면 그 줄 꼬리 주석은 앞 문장 것(`x(); // no break`) — switch 줄부터. 블록을 여는 줄의 꼬리 주석은 그대로 머리(Flat3Map) */
    private static int headFrom(Node sw) {
        Node st = sw instanceof Statement ? sw : sw.findAncestor(Statement.class).map(Node.class::cast).orElse(sw);
        List<Statement> sibs = st.getParentNode().map(p -> p instanceof BlockStmt b ? (List<Statement>) b.getStatements()
                : p instanceof SwitchEntry e ? (List<Statement>) e.getStatements() : List.<Statement>of()).orElse(List.of());
        int at = sibs.indexOf(st);
        boolean tail = at > 0 && sibs.get(at - 1).getEnd().map(p -> p.line).orElse(0) == line(sw) - 1;
        return tail ? line(sw) : line(sw) - 1;
    }
    ```
    (`indexOf` 는 같음 비교라 같은 글 문장이 둘이면 앞 것을 준다 — 그러면 `for` 로 `==` 찾기. 실행이 고른다)
- 시험 `JavaShapeTest.fallThroughHeadNote` 에 더한다(기존 줄 불변 — `:63` Flat3Map 꼴 음성 그대로):
  - `x(); // fall through\n switch (k) {\n case 3: g();\n case 2: g();\n }` → `java.fallThrough` 1
  - `switch (k) {\n case 1: g(); // no breaking change\n case 2: g();\n }` → 1 · `// no breakpoint here` → 1 · `// no break` → 0 · `// no break here` → 0
- 골든 0(`check/java.json` — 픽스처 불변) · corpus `check-commons.json` 에 `java.fallThrough` 키가 **안 생긴다**(Flat3Map 면제 유지)

### 5-99b — 별표 풀기: javadoc 괄호 형식 · 정식 이름 · 상수 수신자(③⑤⑦)(§3-11)
- `core/check/StarImportFix.java`
  - **③** 새 상수 `DOC_ARGS = Pattern.compile("@(?:link|linkplain|see)\\s+[\\w.$]*#[\\w$]+\\(([^)]*)\\)")`. `used()` 의 javadoc 반복 안, `DOC_REF` 다음에: `DOC_ARGS` 가 맞을 때마다 `group(1)` 을 `,` 로 나눠 각 조각을 `replace('*', ' ').trim()` → 앞에서부터 `Character.isJavaIdentifierPart` 인 글자를 모은 낱말이 비지 않고 **바로 다음 글자가 `.` 이 아니면** `used.add(낱말)`(정식 이름 인자 `java.util.Set` 은 안 센다). 반복은 새 `private static void docArgs(String doc, Set<String> used)` 로(복잡도)
  - **⑦** `used()` 의 `cu.findAll(SimpleName.class).forEach(…)` → scope 있는 `ClassOrInterfaceType` 의 이름은 뺀다:
    ```java
    cu.findAll(SimpleName.class).stream()
            .filter(n -> n.getParentNode().filter(p -> p instanceof ClassOrInterfaceType t && t.getScope().isPresent()).isEmpty())
            .forEach(n -> used.add(n.getIdentifier()));
    ```
    `Name` 반복의 조건에 `n.getQualifier().isEmpty() &&` 를 더한다(`@a.b.Foo` 의 `Foo`·`b` 는 안 센다). javadoc 에 「5-99b — 정식 이름으로만 쓴 형식은 쓰인 이름이 아니다」
  - **⑤** `typeRefs` 의 `!constantLike(n.getNameAsString())` → `!(inherits && constantLike(n.getNameAsString()))`, `inherits` 는 메서드 첫 줄 `boolean inherits = mayInherit(cu);`. 새 메서드:
    ```java
    /** 5-99b — 밑줄 든 대문자 수신자가 상수일 수 있는 파일 — 물려받거나(상위 형식·익명 클래스) static import 가 있다. 아니면 선언 안 된 그 이름은 형식이다 */
    private static boolean mayInherit(CompilationUnit cu) {
        return cu.getImports().stream().anyMatch(ImportDeclaration::isStatic)
                || cu.findAll(ClassOrInterfaceDeclaration.class).stream().anyMatch(c -> !c.getExtendedTypes().isEmpty() || !c.getImplementedTypes().isEmpty())
                || cu.findAll(EnumDeclaration.class).stream().anyMatch(x -> !x.getImplementedTypes().isEmpty())
                || cu.findAll(RecordDeclaration.class).stream().anyMatch(x -> !x.getImplementedTypes().isEmpty())
                || cu.findAll(ObjectCreationExpr.class).stream().anyMatch(o -> o.getAnonymousClassBody().isPresent());
    }
    ```
- 시험 `CleanupTest.starImportStage` 에 인라인으로 더한다(기존 줄 불변, 입력은 `package cleanup.t;` + `cleanup(null)` 꼴 또는 `own` — 기존 `note`·`doc`·`cons` 줄 옆):
  - ③ `import java.util.*;` + `/** {@link A#f(Set)} */ class A { List<String> a; void f(Object s) {} }` → `import java.util.List;\nimport java.util.Set;` · `{@link A#f(java.util.Set)}` → `List` 만 · 여러 줄 `{@link A#f(List,\n * Set)}` → 둘 다
  - ⑦ `import java.io.*;` + `class A implements java.io.Serializable {\n}` → import 줄이 사라짐(빈 줄 처리는 기존 `e.delete` 그대로 — 결과 글을 그대로 단언) · `class A implements java.io.Serializable {\n    File f;\n}` → `import java.io.File;` 만 · `@java.lang.Deprecated` + `import java.util.*;` + `List` → `List` 만
  - ⑤ `own` 으로: `import a.b.*;`(`a.b` 폴더에 `Foo` 만) + `class A {\n    Foo f;\n    int n = GEN_CODES.size();\n}` → 글 불변 · skip `줄 3: 출처를 모르는 형식 이름 — 손으로: GEN_CODES` · 기존 `cons`(`class A extends B`) 단언 불변
- 골든 `golden/cleanup/starimport-*`·`javaimport-*`·`steps.json` diff 0

### 5-99c — default 위 주석의 소유(④)(§3-12)
- `core/check/SwitchFix.java` `from` — 위로 올라간 뒤(기존 첫 `while`) 자른다:
  ```java
  int own = indent(lines[at - 1]);
  for (int n = at - 1; n >= from; n--) { // 5-99c — default 보다 깊이 들여쓴 줄 주석은 앞 case 것. 그 줄과 그 위는 안 옮긴다
      String t = lines[n - 1].stripLeading();
      if (t.startsWith("//") && indent(lines[n - 1]) > own) {
          from = n + 1;
          break;
      }
  }
  ```
  그 뒤 기존 둘째 `while`(앞쪽 빈 줄 빼기). 새 `private static int indent(String line)` = `line.length() - line.stripLeading().length()`. 블록 주석(`/* … */`)은 안 자른다. 텍스트 블록 길(`bare.length != lines.length`)은 그대로. javadoc 한 줄 더한다
- 시험 `CleanupTest.switchStage` 에 인라인 하나(`cleanup(null).clean(new Source("t/A.java", …))`):
  ```
  switch (k) {
      case 1:
          a();
          break;
          // case 1 뒤 메모
      default:
          b();
          break;
      case 2:
          c();
          break;
  }
  ```
  (클래스·메서드로 감싼다) → 결과에서 `// case 1 뒤 메모` 가 `break;` 바로 다음에 남고 그 뒤가 `case 2:` · default 묶음은 끝. 같은 들여쓰기 주석(`    // 기본`)은 같이 옮겨지는 기존 단언 `:242`·`:247-248` 불변
- 골든 `cleanup/switch-Pos.java.txt`·`steps.json` diff 0

### 5-99d — 단언 보강: 가리기 규칙 하나만 · union 탭 · 따옴표 밖 · `FREE` 대조(⑥⑧)(§3-13)
- `core/check/PiiFix.java` — `FREE` 를 `private` → package-private(한 낱말). 그 밖 불변
- `CleanupTest`
  - `piiStage` — `new Cleanup(rules, Set.of("security.piiCard"), "LF")` 로 `pii/pos.properties` 글: 대시 꼴은 가려지고 맨 숫자 줄(`…2000` 꼴)은 그대로 · `Set.of("security.piiCardPlain")` 은 반대(기존 시험이 `Cleanup` 을 만드는 생성자 꼴 그대로)
  - `interruptStage` — 탭 들여쓰기 union 인라인: `\t\t} catch (IOException | InterruptedException e) {\n\t\t\tlog(e);` → `\t\t\tif (e instanceof InterruptedException) {\n\t\t\t\tThread.currentThread().interrupt();\n\t\t\t}` 가 끼워짐(`java.interruptedIgnored` 는 기본 끔 — 기존 union 단언이 켜는 꼴 그대로)
  - `finalReview81` — 따옴표 밖: html `<td>4030000010001234</td>`·jsp·ts(`const n = 4030000010001234;`) → `PiiFix.UNQUOTED` 건너뜀 · xml `<pan>4030000010001234</pan>` → 가려짐(`************1234`) · ts 따옴표 안 → 가려짐
  - 새 `piiFreeLangsAreInGlobs`: `RuleSet.builtin().defs()` 의 `security.piiCardPlain` globs 에서 확장자를 뽑아 `PiiFix.FREE` 가 그 부분집합 · globs 확장자 가운데 `FREE` 밖은 전부 「따옴표 안만」 길(언어마다 맨 숫자 한 줄 → `UNQUOTED`)임을 반복으로 단언(`sql` 은 `CleanupCorpusTest.GLOBS` 밖이어도 단위에서는 돈다)
- 사다리: 어느 단언이 지금 코드에서 빨강이면(숨은 결함) — 그 언어 한 줄을 이력 「드러난 것」 에 적고 고침은 `PiiFix` 안 ≤ 10 줄일 때만, 넘으면 새 행
- 골든 0

### V-62a — corpus 골든 확인(§3-14)
- 순서: `bash scripts/mvn.sh -q -DskipTests verify` rc 0 → 여유 RAM 15GB 확인 + projectshop 세션 조율(§5) → `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='CheckCorpusTest,LegacyCorpusTest,CleanupCorpusTest,CleanupCompileCorpusTest,VcsCorpusTest' -Djunit.timeout=10m -Dgolden.update=true`(**다섯 — `VcsCorpusTest` 를 빼지 않는다**) → `git diff --stat src/test/resources/golden/corpus`
- 기대: **diff 0**. 허용(이력에 수와 파일): `cleanup-*.json` 의 `imports` skip 키에 `출처를 모르는 형식 이름` 이 늘어남(⑤ — 상위 형식 없는 파일의 밑줄 대문자 수신자) · `analyze-*.json` 의 JPQL 표(② 안 닫힌 리터럴) — 둘 다 파일을 열어 사유가 맞는지 본다
- 중단선: 등급 A · javac 실패 · `check-commons.json` 에 `java.fallThrough` 키 · `imports` 단계 changed 수가 줄어듦 · `switch`·`interrupt`·`pii` 등 다른 단계 수 변화 · 「다른 지적이 생김·사라짐」 새로 생김
- diff 0 이면 커밋 없이 이력 한 줄(벽시계)만 — V-62 커밋에 같이 싣는다

### R-29 — 손 판정(§3-15)
- CLI·시험으로(본 뒤 scratch 지움), 표는 compare ㉜ 「손 판정」, `파일:줄` 만:
  - ⑦ `toolbox.bat cleanup <jeecg …/cgform/entity/enhance> --rules java.starImport --json --write --out <scratch>` — `CgformEnhanceJsEntity.java` 의 `import java.io.*;` 가 사라지고 `import java.io.Serializable;` 이 안 생김 · 같은 폴더 다른 파일의 더한 import 가 몸에서 쓰이는 이름인가 + 빠진 이름 0(둘 다 댄다)
  - ③ jspwiki `AbstractStep.java`·`PropertyReader.java`, struts `AbstractBeanSelectionProvider.java` — 풀린 import 에 javadoc 괄호 형식이 있다
  - ① `toolbox.bat check` 로 commons `Flat3Map.java` 에 `java.fallThrough` 0 · jspwiki 지적 수가 번들 81 수(compare ㉛)와 같다
  - ⑧ 손으로 한 번: `QualitySql.render("format", "oracle", "APP", "TB_ORDER", "DATE", …)` 글을 눈으로(시험 출력)
  - ① `curl -s -o NUL -w "%{http_code}" -H "Sec-Fetch-Site: cross-site" http://127.0.0.1:<포트>/api/ping` → 403 · 헤더 없이 → 200(서버를 잠깐 띄운다 — `run.bat --port` · 끝나면 끈다)
- 오탐·깨진 고침이면 음성 단언 먼저 RED → 고침 ≤ 2 → V-62a 다시

### V-62 — compare ㉜ · 정적 분석 · `--corpus` · `--db`(§3-16)
- `design/compare-2026-10-28.md` 끝에 `## ㉜ 재측정 — 번들 83(2026-11-13)`: 한 줄 요약 · 「처분」 표(0-84 열넷 + 5-98 여덟 — 닫음·대기 행·후보·사실 정정) · 「손 판정」(R-29) · 「골든」(diff 0 또는 허용 변화) · 「정적 분석·벽시계」 · 「드러난 것」 · 「후보로」: ⑥ `DdlReader` 백슬래시(방언을 모른다 · 스캐너 여덟 · Oracle `'\'` 충돌 · 표본 DDL 전부 읽힘) · ⑦ SQL 모드 줄바꿈 리셋(안 닫힌 따옴표 안전판) · ⑪ 부작용 함수(`SqlProbe` 길 — `WHERE 1=0`·`LIMIT 0`, 거절 목록은 selectKey 의 NEXTVAL 도 막는다) · 시간 상한(끊을 수 있는 CharSequence) · `QualitySql` 대소문자 섞인 이름 · `Cli` 정상 종료 뒤 join · `FieldAccessExpr` 정식 이름
- 순서(전부 따로 낸다, 무거운 것은 하나씩): `bash scripts/mvn.sh -DskipTests verify` rc 0 → `bash scripts/verify.sh` → RAM·projectshop 조율 → `bash scripts/verify.sh --corpus` → `bash scripts/docker-up.sh` → `bash scripts/verify.sh --db` → 끝 줄이 더 부르는 레인 없음

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·2·3·6·7 · 행의 축·강제 지점과 diff · **`crossSiteApi` 가 정상 호출자(CLI LocalConnector·SSE·HtmlUnit)를 막지 않나 · `Profile.local` 이 상대 경로·드라이브 경로를 막지 않나 · CUBRID 식이 비밀번호 없는 url 을 막지 않나 · 소유 수량자로 바꾼 여섯 식이 잘 닫힌 입력에서 같은 답인가 · `QualitySql.id` 의 접기가 안 감싼 이름과 같은 객체를 가리키나 · `catch (StackOverflowError)` 뒤 `Run` 의 파일 사이 상태가 깨지나 · `kill` 이 목록을 부모 끄기 전에 뜨나 · `headFrom` 이 Flat3Map 을 다시 지적하지 않나 · ⑦ 이 단순 이름으로도 쓰는 형식의 import 를 빼는 길 · ⑤ 가 상위 형식 있는 파일에서 넓어지지 않았나 · ④ 가 블록 주석을 반으로 자르는 길** · 골든 diff 0 인가 · 사유 글에 코드 본문이 없나) → 리뷰 고침이 `core/check`·`core/analyze`·`core/vcs` 면 `--corpus` 다시, `core/gen`·`core/quality` 면 `--db` 도 다시 → `gate-probe.sh`(`url-password` 가 산다) → `verify.sh`(끝 줄) → push → PR(본문에 행마다 시작 세 줄 표) → CI `gh api` 폴링 → claude[bot] 처분 → **머지 위임 있음** → 끝 보고(사람이 할 것 — 0-84 사람 실측 여섯 · portfolio 에 `5-100` 인계 · 현장 프로필에 UNC 경로가 있으면 로컬로)

## 4. 실행 스텝
스텝마다 끝에 `bash scripts/mvn.sh -q -DskipTests verify` rc 0(자바를 고친 행) → `bash scripts/verify.sh` 초록 → `git commit`(따로) → `task-done`. 커밋 제목 `<종류>: <행> <무엇>`. 분할표 행 상태·이력 한 줄은 커밋 앞에(CLAUDE.md 「Task 끝」). 시험이 있는 행은 전부 시험 먼저 → RED → 고침 → GREEN.

### S0 — 설계 문서·행·번들 표
- 대상: `design/66-bundle83.md`(이 플랜 파일을 그대로 — 끝의 「S0 부록」 절은 빼고) · `PLAN.md`(부록 B) · `PROGRESS.md`(부록 C)
- 검증: `bash scripts/doc-lint.sh design/66-bundle83.md` · `bash scripts/verify.sh`(docs 레인) · 커밋 `docs: S0 설계 66 — 번들 83`. push 안 함
- 의존: 없음. 실행 세션의 첫 손(번들 81 과 같다)

### 0-85a — GET 도 cross-site 거절
- 대상: `web/LocalOnly.java` · `web/LocalOnlyTest.java` · `docs/import-review.md`
- 검증: `bash scripts/mvn.sh -q test -Dtest='LocalOnlyTest,AppTest,FsRoutesTest,ImportReviewDocTest,SmokeHtmlUnitTest,MainTest,BatchCliTest,SelfTestCliTest'` 초록(CLI·스모크가 안 깨진다). 커밋 `fix: 0-85a /api GET 도 다른 사이트 요청 거절(Sec-Fetch-Site)`
- 의존: S0

### 0-85b — 프로필 경로 UNC · url 비밀번호 빈칸
- 대상: `core/profile/Profile.java` · `ProfileStoreTest.java` · `scripts/probes/url-password.patch`
- 검증: `-Dtest='ProfileStoreTest,ProfileRoutesTest,AppTest'` 초록 · `git apply --check scripts/probes/url-password.patch` rc 0 · `bash scripts/verify.sh`(tools 레인 — hooks-test). 커밋 `fix: 0-85b 프로필 경로 UNC 거절 · url 비밀번호 MySQL 키값·oci·CUBRID`
- 의존: S0

### 0-85c — Error 가 로그·RUNNING 에 안 남게
- 대상: `core/job/JobManager.java` · `web/App.java` · `JobManagerTest.java` · `JsonErrorTest.java`
- 검증: `-Dtest='JobManagerTest,JsonErrorTest,AppTest,SelfCheckTest'` 초록 · `self-check.txt` diff 0. 커밋 `fix: 0-85c catch 안 Error 도 FAILED · 웹 경로 Error 는 이름만 로그`
- 의존: S0

### 0-85d — `make-templates` 쓰기 자리
- 대상: `cli/MakeTemplates.java` · 새 `cli/MakeTemplatesTest.java`
- 검증: `-Dtest='MakeTemplatesTest,ReadmeCommandsTest,MainTest,SelfCheckTest'` 초록 · `git status --short templates mappings` 빈 출력. 커밋 `fix: 0-85d make-templates 는 실행 폴더 아래에만 쓴다`
- 의존: S0

### 0-85e — VCS 시간 초과에 손자 프로세스
- 대상: `core/vcs/Cli.java` · `WorkingCopyTest.java`
- 검증: `-Dtest='WorkingCopyTest,ArchitectureTest,SelfCheckTest'` 초록. 커밋 `fix: 0-85e git·svn 시간 초과에 손자 프로세스까지 끈다`
- 의존: S0

### 0-85f — 규칙 사이 취소 · 스택 넘침 파일 건너뜀
- 대상: `core/check/CheckRunner.java` · `core/check/RuleSet.java` · 새 `CheckRunnerTest.java`
- 검증: `-Dtest='CheckRunnerTest,CheckRulesTest,CheckStoreTest,SelfCheckTest,JobManagerTest'` 초록 · `self-check.txt` diff 0. 커밋 `fix: 0-85f 코드 검사 — 규칙 사이 취소 · 스택 넘친 파일은 못 읽음으로`
- 의존: S0

### 0-85g — 정규식 스택 넘침(JPQL · 프로필)
- 대상: `core/analyze/JpaIndex.java` · `web/ProfileRoutes.java` · `JpaIndexTest.java` · `ProfileRoutesTest.java`
- 검증: `-Dtest='JpaIndexTest,ProfileRoutesTest,ProfilePasswordTest,SelfCheckTest'` 초록 · SpotBugs 새 짝 0. 커밋 `fix: 0-85g 긴 리터럴에 스택 넘침 — JPQL · 프로필 비밀번호 식`
- 의존: S0

### 0-85h — 정규식 스택 넘침(CHECK · 기본값)
- 대상: `core/gen/Validation.java` · `core/gen/DdlGen.java` · `ValidationTest.java` · `DdlGenTest.java`
- 검증: `-Dtest='ValidationTest,DdlGenTest,AlterGenTest,DtoGeneratorTest,GeneratorTest,SelfCheckTest'` 초록 · `git diff --stat src/test/resources/golden` 0. 커밋 `fix: 0-85h 긴 리터럴에 스택 넘침 — CHECK 조건 · 기본값`
- 의존: S0

### 0-85i — 품질 SQL 예약어 이름
- 대상: `core/quality/QualitySql.java` · `core/gen/DdlGen.java`(메서드 하나) · `QualitySqlTest.java`
- 검증: `-Dtest='QualitySqlTest,DdlGenTest,ArchitectureTest,SelfCheckTest'` 초록 · `git diff --stat src/test/resources/golden/quality` 0. 커밋 `fix: 0-85i 품질 SQL — 예약어 이름은 방언 따옴표로`. 이 커밋 뒤 분할표 `0-84` 행을 「완료 — 번들 83(0-85a~i) · 대기 5-100 · 후보 compare ㉜」 로
- 의존: 0-85h(`DdlGen` 같은 파일)

### 5-99a — 떨어짐 머리 주석 · `no break`
- 대상: `core/check/JavaShape.java` · `JavaShapeTest.java`
- 검증: `-Dtest='JavaShapeTest,JavaRulesTest,CheckRulesTest,SelfCheckTest'` 초록 · `golden/check` diff 0. 커밋 `fix: 5-99a 떨어짐 — 앞 문장 꼬리 주석은 머리 주석이 아니다 · no break 낱말 경계`
- 의존: S0

### 5-99b — 별표 풀기 셋
- 대상: `core/check/StarImportFix.java` · `CleanupTest.java`
- 검증: `-Dtest='CleanupTest,CleanupRunnerTest,CleanupRoutesTest,SelfCheckTest'` 초록 · `golden/cleanup` diff 0. 커밋 `fix: 5-99b 별표 풀기 — javadoc 괄호 형식 · 정식 이름은 안 셈 · 상수 수신자는 물려받을 때만`
- 의존: S0

### 5-99c — default 위 주석의 소유
- 대상: `core/check/SwitchFix.java` · `CleanupTest.java`
- 검증: `-Dtest='CleanupTest,SelfCheckTest'` 초록 · `golden/cleanup` diff 0. 커밋 `fix: 5-99c default 를 옮길 때 앞 case 의 줄 주석은 남긴다`
- 의존: 5-99b(`CleanupTest` 같은 파일)

### 5-99d — 단언 보강
- 대상: `core/check/PiiFix.java`(한 낱말) · `CleanupTest.java`
- 검증: `-Dtest='CleanupTest,LuhnRuleTest,SelfCheckTest'` 초록 · 골든 diff 0. 커밋 `test: 5-99d 가리기 규칙 하나만 · union 탭 · 따옴표 밖 · FREE 대조`(`Ruling:` 미리 허락한 예외 아님 — 시험만 더하는 행이라 RED 는 없을 수 있다, 이력에 한 줄). 이 커밋 뒤 분할표 `5-98` 행을 「완료 — 번들 83(5-99a~d)」 로
- 의존: 5-99c

### V-62a — corpus 골든 확인
- 대상: `golden/corpus/*`(§3-14)
- 검증: 다섯 시험 초록 · diff 0(또는 허용 변화만) · 벽시계 이력. 변화가 있으면 커밋 `test: V-62a 재채점 — <무엇>`
- 의존: 5-99d · 0-85i

### R-29 — 손 판정
- 대상: compare ㉜ 「손 판정」 표(§3-15) + 고침(≤ 2, 음성 먼저 RED)
- 검증: 표 + 합 줄 · 고침이 있으면 V-62a 다시 · 커밋은 V-62 와 같이(고침은 따로 `fix:`)
- 의존: V-62a

### V-62 — compare ㉜ · 정적 분석 · `--corpus` · `--db`
- 대상: `design/compare-2026-10-28.md`(§3-16)
- 검증: `mvn.sh -DskipTests verify` rc 0 → `verify.sh` → `verify.sh --corpus` → `docker-up.sh` → `verify.sh --db` 넷 초록(전부 따로) · `doc-lint` · 커밋 `docs: V-62 재측정 — compare ㉜ + 정적 분석 + --corpus + --db`
- 의존: R-29

### 마무리
§3 「마무리」 그대로. 머지 위임 있음.

## 5. 금지 사항
- `pure/`·portfolio·satchel · `src/main/resources/check/rules.yaml` · `golden/check/pure-rules.json` · 화면 HTML·JS·CSS · `scripts/`(`probes/url-password.patch` 하나 밖)·훅·`VerifyLanesTest` · CLAUDE.md
- 범위 밖 코드 — `DdlReader`·`DdlPull`·`InsertGen` · `RegexRule`·`MyBatisRules`·`SqlTables` · `ReadOnlyJdbc`·`ConnectionRegistry`·`SqlProbe` · `LocalFiles` · `Cleanup`·`JavaRules`·`InterruptFix`·`LogFix`·`SyntaxFix`·`MarkerFix`
- `DdlGen` — `STR`·`PG_CAST` 두 식과 `reserved(String)` 메서드 하나뿐. `id`·`defaultOf` 몸 불변
- `QualitySql` — `name()`·`IDENT`·`d90.json` 불변. 늘 감싸기·대소문자 섞인 이름 감싸기 금지(§3-9)
- `LocalOnly` — Host 검사·쓰기 메서드 가지·415 가지 불변. 화면 페이지(`/`·`/tools/*`)에 cross-site 거절을 걸지 않는다
- `Profile` — `local()` 은 네 칸만. 상대 경로·드라이브 경로를 막지 않는다. `project.root`·`classesDir`·`libDir`·`generator.outDir` 는 안 건다(`LocalFiles.check` 를 이미 지난다)
- 정규식 — 소유 수량자는 Java 전용 여섯 자리만. 식의 다른 글자를 바꾸지 않는다
- 픽스처 파일 추가·수정 금지(단언은 인라인) · `tb-ignore` 는 §3-6 사다리의 한 줄만
- 골든 — diff 0 이 기대. `-Dgolden.update` 는 V-62a 의 다섯 시험에만. `golden/quality`·`golden/cleanup`·`golden/check`·`golden/gen`·`corpus/db-*` 가 움직이면 §7
- 새 의존성 금지 · `--full` 안 돈다 · **무거운 단계(V-62a·`--corpus`·`--db`·리뷰 고침 뒤 다시) 앞에서 멈추고 사용자에게 알린다 — projectshop 세션이 컨테이너·JVM 을 내렸다는 답을 받은 뒤 돌리고, 끝나면 재개를 알린다**(RAM 은 toolbox 우선) · 무거운 것을 겹쳐 띄우지 않는다(`--corpus` → Docker → `--db`) · `--db` 도는 동안 다른 JVM 금지 · `wsl --shutdown` 금지 · `toolbox-demo-ora` 컨테이너 `rm` 금지
- **Bash heredoc·python 으로 `\` 가 든 글(자바 정규식·문자열)을 쓰지 않는다 — Edit 도구로만** · python 으로 문서를 「읽고 바로 쓰기」 금지
- corpus 재채점은 **다섯 시험**(`VcsCorpusTest` 포함)
- 손 판정은 「더한 것이 맞나」 와 「빠진 것이 없나」 둘 다
- 0-84 사람 실측 여섯을 고치는 행으로 만들지 않는다

## 6. 최종 검증
- `bash scripts/mvn.sh -DskipTests verify` rc 0 · `bash scripts/verify.sh` · `bash scripts/verify.sh --corpus` · `bash scripts/verify.sh --db` 초록(따로) · 끝 줄이 더 부르는 레인 없음 · `gate-probe.sh` 전부 빨개짐(`url-password` 포함)
- 수 불변: 규칙 147 · 정규식 63 · `Cleanup.RULES` 32 · `PureCodeCheckTest` 32 · `JavaRules` 메서드 30 · 새 메서드마다 복잡도 ≤ 15
- 골든: `git diff --stat origin/main -- src/test/resources/golden` 이 비거나 §3-14 허용 변화뿐
- 수동(한 번, 이력에 한 줄): R-29 의 curl 둘(403·200) · jeecg `CgformEnhanceJsEntity.java` 결과 눈 확인
- compare ㉜ 처분 표가 0-84 열넷 + 5-98 여덟을 다 댄다(닫음 열하나+여덟 · 대기 행 `5-100` 셋 · 후보 셋)
- `PROGRESS.md` 「사람이 할 것」 에 0-84 사람 실측 여섯 · `5-100` 인계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- 0-85a 에서 `SmokeHtmlUnitTest`·CLI 시험이 403 으로 빨강(HtmlUnit·LocalConnector 가 `Sec-Fetch-Site` 를 cross-site 로 보낸다 — 실측과 다름)
- 0-85b 에서 기존 프로필 시험·예시 프로필(`profiles/example*.yaml`)이 `local()` 에 걸림 · `passwordInUrlIsRejected` 기존 음성 셋이 빨강
- 0-85c 에서 `self-check.txt` 변화 · 사다리(App 쪽 빼기)를 타고도 빨강
- 0-85d 에서 저장소 `templates/`·`mappings/` 가 바뀜
- 0-85f 에서 `self-check.txt` 변화(`tb-ignore` 한 줄 밖) · `CheckCorpusTest` 수 변화
- 0-85g·h 에서 단위 골든 변화 · SpotBugs `REDOS` 새 짝
- 0-85i 에서 `golden/quality` 변화 · `QualitySqlPostgresTest`(db) 빨강 · `noPackageCycles` 빨강
- 5-99a 에서 `JavaShapeTest:63`(Flat3Map 꼴) 빨강 · `check/java.json` 변화
- 5-99b 에서 `starimport-*`·`javaimport-*` 골든 변화 · 기존 `cons`·`note`·`doc` 단언 빨강
- 5-99c 에서 기존 `switchStage` 단언·`switch-Pos.java.txt` 변화
- 어느 행이든 `pmd:check`·SpotBugs 새 짝을 둘 안에 못 고침 · 메서드 수·복잡도·길이 상한
- V-62a 에서 §3-14 중단선
- R-29 에서 고침 셋째가 필요 · 별표 풀기에서 빠진 이름 하나라도
- V-62 에서 `--corpus`·`--db` 가 이 번들 밖 원인으로 빨강(고치기 둘까지) · 여유 RAM 모자람(사용자에게)
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① `catch (StackOverflowError e)` 가 `java.catchThrowable` 자기 검사에 걸리나(§3-6 사다리) ② `(?:a|b)*c` 가 20 만 자에서 이 JVM 스택으로 넘치나(사다리 — 200 만 자) ③ 이 PC git 의 alias 셸이 손자로 뜨나(§3-5 사다리) ④ `setJavaLangErrorHandler$javalin` 이 ErrorProne·PMD 를 지나나(§3-3 사다리) ⑤ corpus 골든 diff 0 인가(§3-14 허용·중단선) ⑥ `runFiles`·`LocalOnly.check`·`StarImportFix.used` 복잡도(넘으면 §3 대로 메서드로) ⑦ 여유 RAM. 첫 스텝 앞 확인 명령: `git status -sb`(깨끗 · `work/2026-11-13b`) · `git log --oneline -1`(`4bdbe63`) · `grep -c "setJavaLangErrorHandler" src/main/java/kr/ejg/toolbox/web/App.java`(0)
- **사용자만 정할 수 있는 것** — 없음(여덟 결정은 2026-11-13 에 받았다)

