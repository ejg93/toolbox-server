# 설계 32 — 번들 49: 코드 검사 「크기·복잡도」 묶음 · 중복 코드 · 「추이」 탭 (Fable 설계 · Opus 실행, 2026-10-23)

## 맥락
2026-10-23 사용자와 「이 도구가 못 덮는 품질 축」을 짚었다 — 정확성(요구 충족)·복잡도/중복 지표·보안 깊이(흐름 추적)·설계 지표. 그 가운데 기존 부품(JavaParser·코드 검사 이력)으로 되는 것을 번들 49 로, 프로그램 분석 쪽(`${}` 도달 경로·SELECT 컬럼 DB 대조)을 번들 50(설계 33)으로, SpotBugs/FindSecBugs 내장을 번들 51(설계 34)로 나눴다. 메모리 `quality-gaps-scoreboard`.

사용자 결정(뒤집지 않는다):
- **점수가 아니라 건수.** 가중 합산 점수는 만들지 않는다. 묶음×등급 건수·1천 줄당 밀도·억제 건수·고쳐짐/새로 생김.
- **집계 단위는 묶음·등급 + 폴더.** 작성자(blame) 집계는 하지 않는다.
- **새 화면 없음.** 크기·복잡도·중복은 코드 검사의 규칙 묶음 하나(「ㅊ 크기·복잡도」), 추이는 코드 검사의 넷째 탭.

실행자는 Opus. 차례는 `S0`(설계 세션이 끝냄) → `5-35a` → `5-35b` → `5-36a` → `5-36b` → `5-36c` → `5-35c`(PMD 대조 — 맨 뒤, 네트워크) → 마무리(번들 모드).

**비교 방식(사용자 2026-10-23)**: 다른 도구와의 비교는 조사(문서 읽기)가 아니라 **만든 뒤 같은 표본에 두 도구를 돌려 기계로 맞대는 시험**으로 한다 — 5-32e(Checkstyle `DeclarationOrder`)와 같은 선례. 판단이 끼지 않아 밤에 돌려도 되고, 어긋남 목록을 보고 아침에 사람이 정의를 고친다. 가지 `work/2026-10-23d`. 머지 위임 없음.

---

## 1. 목표와 범위
- 목표: 코드 검사가 「크기·복잡도·중복」을 지적하고, 같은 폴더를 거듭 검사한 이력을 묶음×등급 건수와 고쳐짐/새로 생김으로 보여 준다(화면 표 + xlsx).
- 사용자가 체감하는 변화: 규칙 칸에 묶음 「ㅊ 크기·복잡도」(규칙 여섯)가 생기고, 결과 표에 「복잡도 73 > 15 · summaryBbsStats(2)」·「…Blog.java:148 과 같음 — 정규화 40줄」 같은 행이 섞인다. 상단 탭이 넷(폴더 검사·붙여넣기 검사·배포 목록·**추이**)이 된다.
- In scope: `rules.yaml` 묶음 `metric` 여섯 · `JavaRules` 측정 넷+클래스 하나 · 새 `Duplicates`(kind `dup`) · `check_run` 열 셋(V013) · `RuleSet.fingerprint()` · `Trend` 엔진 · API 둘 · 화면 탭 · xlsx · 픽스처·골든·스모크·표본 골든 갱신.
- Out of scope: CLI 명령 추가(`toolbox.bat check` 는 그대로 — 새 묶음은 프로필 `groups` 로 켜진다) · 순수본 · 작성자 집계 · 도구를 가로지르는 「품질 현황」 화면(발주처 양식 확인 뒤) · 프로그램 분석(번들 50) · SpotBugs(번들 51).

## 2. 현재 구조 요약
- `core/check/RuleSet`(`load` — kind 로 가른다: regex·absent·jsUnusedImport·file 은 `Rule`, java·mybatis 는 `parsedDefs`, marker 는 `markerDef`) · `RuleSet.Run`(`apply`/`observe`/`finish`/`suppressed`) · `JavaRules`(파일 하나를 JavaParser 로 한 번 읽고 켜진 규칙만, `on` = id → Def, `finding(rule, s, line, excerpt)`·`add(c, rule, node)`) · `CheckRunner.run`(목록 → 파일마다 apply/observe → finish → `RunResult(findings, files, skipped, parseErrors, truncated, suppressed)`) · `CheckStore`(`save(profile, path, changedOnly, groups, findings, suppressed)`·`runs()`·`run(id)`·`findings(id)`, H2 `check_run`·`check_finding` V001 + `suppressed` V011) · `web/CheckRoutes`(`POST /api/check/run` job → `store.save`, `GET /api/check/runs`·`/runs/{id}`·`POST /runs/{id}/export`, `load(profile, groups, overrides)`) · 화면 `tools/code_check.html`(탭 셋 `.t` span + `.pane`, 규칙 칸 `#rulesCol`, 이력 `<select id="runs">`) · `code_check_ext.js`(`GROUPS` 배열 → 묶음 라벨, `showTab(name)` — deploy 면 규칙 칸을 숨긴다, `loadRuns`·`openRun`).
- 시험: `JavaRulesTest.fixturesGolden`(`fixtures/check/java/*` 를 **Run 하나**로 돌려 `golden/check/java.json`, 음성 `Neg*` 0 히트, 묶음 `java` 의 켜진 규칙은 양성에서 한 번 이상) · `CheckRulesTest.fixturesGolden`(java/·mybatis/ 폴더는 건너뛴다; `rules.enabled()` 의 규칙은 양성에서 한 번 이상 — `Rule` 이 아닌 kind(marker·dup)는 이 집합에 없다) · `CheckCorpusTest`(`@Tag corpus`, 무리마다 규칙별 건수·파일 수 `golden/corpus/check-<무리>.json` + 히트 목록 `check-<무리>-hits.txt`, B baseline) · `CheckStoreTest` · `SmokeHtmlUnitTest.codeCheckHistoryPicker`(같은 폴더 두 번 검사 → 이력 셀렉트).
- 레인: `core/check`·`resources/check`·`tools`·시험 `core/check` 는 corpus 레인 → 마무리에 `--corpus`. `src/main/resources/db`(V013)는 db 레인 → `--db` 도 돈다(push-guard).
- 함정: ① `JavaRulesTest` 의 「양성에서 안 걸린 규칙」 필터가 `d.group().equals("java")` — 새 묶음 `metric` 의 kind java 규칙은 이 필터 밖이라 둘 다 보게 고친다. ② `CheckStore.save` 호출자는 `CheckRoutes` 하나(CLI 는 `LocalApp` 으로 같은 라우트). ③ `check_finding.grp VARCHAR(20)` — `metric` 6자. ④ `LocalFiles.MAX_FILES` 20,000 — 중복 해시 표는 이 상한에서 약 300만 줄·블록 60만 개(아래 실측 비례), `HashMap<Long, Long>` 약 60MB. 상한 `MAX_BLOCKS` 2,000,000 을 둔다. ⑤ 변경분 검사(`changed`)는 안 바뀐 `.java` 만 `observe` 한다 — 중복 비교 상대가 안 바뀐 파일이어도 잡히고, 결과는 바뀐 파일로 걸러진다(dupMapping 과 같은 길). ⑥ `check_run.path` 는 요청 글 그대로(1000자 컷) — 추이의 「같은 폴더」는 양쪽을 `Path.of(p).toAbsolutePath().normalize()` 로 맞춘 뒤 대소문자 무시로 견준다(Windows).

### 실측(2026-10-23, 설계 세션 — 측정 프로그램은 저장소 밖)
JavaParser 3.26.3 으로 표본 넷의 메서드(본문 있는 메서드+생성자) 지표. 정의: 순환 복잡도 = 1 + if·for·foreach·while·do·catch·`?:`·라벨 있는 `case`·`&&`·`||` 수(Checkstyle `CyclomaticComplexity` 와 같은 꼴). 길이 = 선언 첫 줄~본문 끝 줄. 중첩 = if·for·foreach·while·do·switch·try 의 깊이(측정 때는 else-if 도 한 단계 — 설계는 else-if 를 같은 깊이로 둔다, §3). 인자 수. 클래스 메서드 수(`getMethods()`).

| 표본 | 파일 · 줄 · 메서드 | 복잡도 >15 | 길이 >100 | 중첩 >4 | 인자 >7 | 메서드/클래스 >30 | 중복(정규화 30줄) | 파싱+지표 |
|---|---|---|---|---|---|---|---|---|
| egov(공통컴포넌트) | 1,089 · 176,830 · 7,939 | 38(파일 32) | 19(파일 15) | 41 | 1 | 46 | 31 덩어리·1,441줄·24파일 (50줄: 13) | 5.5초 · 힙 121MB |
| shopizer | 831 · 95,950 · 4,855 | 50 | 53 | 53 | 14 | 18 | 7 | 3.9초 |
| egov-msa | 763 · 47,811 · 1,802 | 10 | 3 | 2 | 2 | 3 | 38(모듈 복붙) | 3.3초 |
| roller | 545 · 87,014 · 4,642 | 43 | 32 | 42 | 18 | 22 | 3 | 2.9초 |

가장 복잡한 메서드: eGov `BbsStatsDAO.summaryBbsStats` 73 · roller `PageServlet.doGet` 83. 중복 예: eGov `cop/cmy/service/Community.java:134 == cop/bbs/service/Blog.java:148`(40줄) · `EgovSmsInfoReceiver:125 == EgovSmsBasicReceiver:121`(126줄). 중복 탐지는 해시 표 3만 4천 항목·15ms — 비용은 파싱이 아니라 줄 해시뿐.
기준값 결론: 복잡도 15·길이 100·중첩 4·인자 7·메서드 30·클래스 1,000줄 이면 eGov 전체에서 약 150건 — 묶음을 끄면 0 이고, 켜면 「상위 2%」만 나온다. PMD `quickstart` 를 미룬 이유(소음 3,210 파일)와 다르다.

## 3. 설계 결정

**5-35 크기·복잡도 묶음** [고정]
- 묶음 id `metric`, 라벨 「ㅊ 크기·복잡도」. `rules.yaml` 끝에 절 `# ---- ㅊ 크기·복잡도(5-35)`. 규칙 여섯 — 전부 `globs: ['*.java']`, `fix` 한 줄, `ref` 없음:

| id | kind | severity | params | message | 지적 자리·발췌 |
|---|---|---|---|---|---|
| `metric.complexity` | java | warn | `{max: 15}` | 메서드 순환 복잡도가 기준을 넘는다 | 메서드 선언 줄 · `복잡도 73 > 15 · summaryBbsStats(1)` |
| `metric.methodLength` | java | warn | `{max: 100}` | 메서드가 너무 길다(선언~본문 끝 줄) | 선언 줄 · `길이 142 > 100 · doGet(2)` |
| `metric.nesting` | java | info | `{max: 4}` | 제어문 중첩이 깊다 | 가장 깊은 문장의 줄 · `중첩 6 > 4 · onMessage(1)` |
| `metric.params` | java | info | `{max: 7}` | 인자가 많다 — 객체로 묶는다 | 선언 줄 · `인자 9 > 7 · insert(9)` |
| `metric.classMethods` | java | info | `{max: 30}` | 클래스에 메서드가 많다 — 책임을 나눈다 | 클래스 선언 줄 · `메서드 46 > 30 · EgovBbsManageController` |
| `metric.classLength` | java | info | `{max: 1000}` | 클래스가 너무 길다 | 클래스 선언 줄 · `길이 1,312 > 1000 · …` |
| `metric.duplicate` | dup | warn | `{minLines: 30}` | 다른 자리와 같은 코드 덩어리 | 뒤 자리의 첫 줄 · `cop/bbs/service/Blog.java:148 과 같음 — 정규화 40줄` |

- 발췌(`excerpt`)는 응답에만 있고 저장하지 않는다(규칙 3) — 메서드·클래스 이름은 식별자라 발췌에 둔다. 측정값은 message 가 아니라 발췌에 — message 는 규칙당 한 줄 고정(xlsx 「설명」 열).
- 측정 정의(`JavaRules` 안, 메서드 = `MethodDeclaration`·`ConstructorDeclaration` 가운데 본문 있는 것, 중첩·익명 클래스 안도 센다):
  - 복잡도 `complexity(body)`: 1 + `IfStmt`·`ForStmt`·`ForEachStmt`·`WhileStmt`·`DoStmt`·`CatchClause`·`ConditionalExpr`·라벨 있는 `SwitchEntry`(`!getLabels().isEmpty()`)·`BinaryExpr`(`AND`·`OR`) 의 수(`body.findAll(Node.class)`). 람다 본문도 그 메서드에 든다(따로 안 센다).
  - 길이: `body.getEnd().line − decl.getBegin().line + 1`(주석은 Range 밖).
  - 중첩 `depth(node, d)`: 자식이 `IfStmt`·`ForStmt`·`ForEachStmt`·`WhileStmt`·`DoStmt`·`SwitchStmt`·`TryStmt` 면 d+1 — 단 부모가 `IfStmt` 이고 자식이 그 `elseStmt` 인 `IfStmt`(else-if) 는 d 그대로. 지적 줄은 깊이가 처음 max+1 이 되는 문장의 줄.
  - 인자: `getParameters().size()`.
  - 클래스: `ClassOrInterfaceDeclaration`(인터페이스 제외) 의 `getMethods().size()`·`end−begin+1`.
  - 한 메서드에 규칙마다 최대 한 건.
- 버린 대안: PMD 내장(5-7 조건부 그대로 — 룰셋 합의 전) · 인지 복잡도(Sonar — 정의가 길고 기준이 없다) · 중첩에서 else-if 를 한 단계로(eGov 41→ 더 적어진다; Checkstyle `NestedIfDepth` 도 else-if 를 같은 깊이로 본다).

**5-35b 중복 코드** [고정]
- 새 `core/check/Duplicates.java`(package-private, `Rule` 아님). `RuleSet.load` 에서 `kind.equals("dup")` 이면 `dupDef`(marker 처럼 `Rule` 목록 밖) — 단 `globs()` 에는 **넣는다**(파일이 목록에 들게). `Run` 이 `Duplicates dup = dupDef == null ? null : new Duplicates(dupDef)` 를 들고 `apply`/`observe` 에서 `def.matches(s)` 면 `dup.see(s)`, `finish()` 에서 `dup.finish()` 를 더한다(표식 필터·정렬은 지금 finish 흐름 그대로).
- `see(Source s)`: 줄마다 `strip()` → 건너뜀: 빈 줄 · `//` 로 시작 · `*`·`/*` 로 시작(블록 주석 안 줄 — `/*` 뒤 `*/` 까지 상태로) · `import `·`package ` 로 시작 · `{`·`}`·`};` 하나뿐. 남은 줄은 `replaceAll("\\s+", " ").hashCode()` → `int[] hashes`, 원 줄 번호 `int[] lines`. 블록 해시: 정규화 줄 `minLines` 개를 FNV-1a 64비트로 접는다(`h = (h ^ hashes[i+k]) * 0x100000001b3L`, 시작 `0xcbf29ce484222325L`). `HashMap<Long, Long> first`(값 = `fileIdx << 32 | i`). 같은 블록을 만나면: 같은 파일이고 `|pi − i| < minLines` 면 건너뜀(자기 겹침); 아니면 `n = minLines` 부터 두 줄 해시가 같은 동안 늘리고 Finding(뒤 파일 `s.rel()`, 줄 `lines[i]`, 발췌 `<앞 파일>:<앞 줄> 과 같음 — 정규화 n줄`) 하나, `i += n`. 처음 보는 블록은 `first.put`. `first.size() >= MAX_BLOCKS(2,000,000)` 면 더 안 넣는다(이미 든 것과는 계속 비교).
- 파일 사이 상태라 `observe` 한 안 바뀐 파일도 `see` 한다(상대가 된다). 결과는 `CheckRunner` 가 바뀐 파일로 거른다. 발췌는 규칙 3 에 맞다(경로·줄·수).
- 버린 대안: PMD CPD(토큰 기반 — 의존성) · 윈노잉(표본에서 해시 표가 3만 4천 — 줄일 이유가 없다) · 파일마다 전체 글 보관(메모리).

**5-36 추이** [고정]
- V013 `src/main/resources/db/migration/V013__check_trend.sql`:
  ```sql
  -- 5-36 추이 — 분모(검사한 파일·줄)와 규칙 지문. 옛 실행은 0·NULL(추이 표에 「지문 없음」)
  ALTER TABLE check_run ADD COLUMN file_count INT DEFAULT 0 NOT NULL;
  ALTER TABLE check_run ADD COLUMN line_count INT DEFAULT 0 NOT NULL;
  ALTER TABLE check_run ADD COLUMN rules_hash VARCHAR(16);
  ```
- `RuleSet.fingerprint()`: 켜진 정의(`defs()` 가운데 `on()`)를 id 순으로 `id|severity|kind|regex|flags|globs|skipComments|params(TreeMap 글)` 한 줄씩 `\n` 으로 이어 SHA-256 → 앞 12 hex. customRules·프로필 덮어쓰기·요청 덮어쓰기(`CheckRoutes.load`)가 다 합쳐진 뒤의 값이라 「같은 규칙으로 쟀나」를 한 글자로 말한다.
- `CheckRunner.RunResult` 에 `int lines`(검사한(`apply`) 파일의 `\n` 수 + 1 합) — `runText` 는 글의 줄 수.
- `CheckStore.save(profile, path, changedOnly, groups, findings, suppressed, files, lines, rulesHash)`(옛 시그니처는 지운다 — 호출자 하나) · `RunInfo` 에 `files`·`lines`·`rulesHash`. 새 `counts(long runId)` → `Map<String, Integer>` 키 `grp|severity`(SQL `GROUP BY grp, severity`).
- 새 `core/check/Trend.java`:
  ```java
  public record Point(long runId, LocalDateTime at, int files, int lines, int suppressed, String rulesHash, boolean rulesChanged,
          int total, Map<String, Integer> byGroup, Map<String, Integer> bySeverity, Integer fixed, Integer added) {}
  public record Folder(String folder, int now, int before) {}
  public record Report(String path, int depth, List<Point> points, List<Folder> folders, int skippedChangedOnly) {}
  public static Report of(CheckStore store, String profile, String path, int depth, int limit) throws SQLException
  ```
  `store.runs()` 를 프로필 같음(둘 다 null 이면 같음)·경로 같음(§2 ⑥ 정규화)·`changedOnly == false` 로 거르고(변경분 실행 수는 `skippedChangedOnly`) 오래된 것부터 마지막 `limit`(기본 20) 개. 점마다 `counts` 로 byGroup·bySeverity·total. `rulesChanged` = 앞 점과 `rulesHash` 가 다름(어느 쪽이든 null 이면 true). `fixed`/`added` = 앞 점과 (file, rule) 다중집합 차 — 앞에만 있는 수의 합 / 뒤에만 있는 수의 합(`store.findings` 둘을 `Map<String,Integer>` 키 `file + '\u0000' + rule` 로). `rulesChanged` 면 둘 다 null(비교 안 함). 첫 점은 null. 밀도(1천 줄당)는 화면이 `total / lines * 1000` 으로 센다(lines 0 이면 「-」).
  `folders` = 마지막 점과 그 앞 점의 지적을 파일 경로의 앞 `depth` 조각(`/` 기준, `\` 는 `/` 로)으로 묶은 건수 — now 내림차순, 같으면 이름순. 앞 점이 없으면 before 0. depth 1~6, 기본 2.
- API(`CheckRoutes`): `GET /api/check/trend?path=&depth=&limit=` → `Report`(path 없으면 400, 폴더 없음은 404 가 아니라 빈 점 목록) · `POST /api/check/trend/export {path, depth}` → xlsx 시트 둘 「추이」(실행 #·시각·파일·줄·전체·error·warn·info·억제·고쳐짐·새로 생김·규칙 지문·규칙 바뀜 + 묶음마다 한 열) · 「폴더」(폴더·최근·직전·차이). `Outputs.xlsx(dir, "코드검사-추이.xlsx", sheets)`.
- 화면(`code_check.html`·`code_check_ext.js`): 탭 `<span class="t" id="tabTrend">추이</span>` + `<div class="pane" id="paneTrend" hidden>`. 규칙 칸은 숨긴다(`showTab` 의 deploy 조건에 trend 추가). bar: `<span class="opt">폴더 <span id="trendDir" class="count"></span></span>`(폴더 검사 탭의 `#dir` 값을 보여 주기만) · `폴더 단계 <select id="trendDepth">`(1~6, 기본 2) · `<button class="btn-p" id="trendRun">보기</button>` · `<button class="dl" id="trendXlsx" disabled><span class="ico ico-xlsx" aria-hidden="true"></span>코드 검사 추이</button>` · `<span class="count" id="trendCount">`. 아래 셋: `#trendRuns`(실행별 — 최근이 위: `#id · 시각 · 파일 · 줄 · 1천줄당 · 전체 · error · warn · info · 억제 · 고쳐짐 · 새로 생김 · 규칙`; 규칙 칸은 지문 앞 6자, `rulesChanged` 면 `다름 ⚠`·고쳐짐/새로 생김 빈칸, 지문 null 이면 `없음`) · `#trendGroups`(묶음 × `최근 · 직전 · 차이`, 라벨은 `groupLabel`) · `#trendFolders`(폴더 × `최근 · 직전 · 차이`). `TB.table` 로. 변경분 실행이 빠졌으면 count 에 `· 변경분 실행 n 은 뺐다`. 검사 뒤(`afterRun`) 추이 탭이 열려 있으면 다시 그린다. `aria-label` 은 보이는 글 그대로(ToolsA11yTest).
- 버린 대안: 규칙이 다른 실행을 추이에서 빼기(사용자가 판단하게 표시만) · 점수(사용자 결정) · 파일별 줄 수 저장(규칙 3 과 무관하지만 열이 하나 더 — 밀도는 실행 단위로 충분) · 새 화면(1단계는 탭, 메모리 `quality-gaps-scoreboard`).

## 4. 실행 스텝

**S0 — 설계 파일 + PLAN + PROGRESS**(설계 세션이 끝냈다 — 실행자는 확인만)
- `design/32-bundle49.md`(이 파일)·`33-bundle50.md`·`34-bundle51.md` · PLAN §3.1 표(ㅅ 행을 「외부 분석기」로, ㅊ 행 추가) · §15 번들 49·50·51 행 · §16 행 · PROGRESS 상태 한 줄 + 이력 한 줄 · 메모리.
- 검증: `bash scripts/verify.sh`(docs 레인) 초록.

**5-35a — 크기·복잡도 다섯 + 규칙 정의 + 픽스처·골든**
- 대상: `src/main/resources/check/rules.yaml` · `core/check/JavaRules.java` · `tools/code_check_ext.js`(`GROUPS` 에 `['metric', 'ㅊ 크기·복잡도']` — 보안 다음, a11y 앞이 아니라 **맨 뒤**) · `profiles/example.yaml`(`groups` 주석에 `metric=ㅊ 크기·복잡도`, 값 `metric: true`) · 새 `fixtures/check/java/PosMetric.java`(복잡도 16 메서드 하나·101줄 메서드 하나·중첩 5 하나·인자 8 하나 — 서로 다른 메서드로, 한 파일) · `PosMetricClass.java`(메서드 31 개 — 한 줄짜리) · `NegMetric.java`(복잡도 15·100줄·중첩 4(else-if 사슬 포함)·인자 7·메서드 30 — 경계) · `golden/check/java.json` · `JavaRulesTest`.
- 변경: §3 5-35. `JavaRules` — 필드 `metricOn`(여섯 id 가운데 켜진 것), `apply` 의 `type` 루프 뒤 `metrics(cu, c)`: 클래스 둘(`classMethods`·`classLength`)은 `type()` 안에서, 메서드 넷은 `cu.findAll(CallableDeclaration.class)` 루프. 규칙 여섯 전부 `rules.yaml` 에 `kind: java`(duplicate 는 `kind: dup` — 5-35b 가 정의만 먼저 두면 `RuleSet.load` 가 모르는 kind 로 거절하니 **5-35b 에서 넣는다**). `JavaRulesTest.fixturesGolden` 의 필터 `d.group().equals("java")` → `Set.of("java", "metric").contains(d.group())`. `metric.classLength` 는 픽스처로 1,000줄을 만들지 않는다 — 시험에서 `params.max` 를 20 으로 덮어쓴 `RuleSet` 하나를 더 돌려(`profile(…, Map.of("metric.classLength", Map.of("params", Map.of("max", 20))))`) `PosMetricClass` 에 걸리는지 단언(골든 밖).
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaRulesTest,CheckRulesTest,ProfileStoreTest'` 초록 · `java.json` diff 가 `PosMetric*` 행 추가뿐.
- 의존: 없음.

**5-35b — 중복 코드 `Duplicates` + 표본 골든**
- 대상: 새 `core/check/Duplicates.java` · `RuleSet.java`(`dupDef`·`globs()`·`Run`) · `rules.yaml`(`metric.duplicate`) · 새 `src/test/java/kr/ejg/toolbox/core/check/DuplicatesTest.java` · 새 픽스처 `fixtures/check/java/PosDupA.java`·`PosDupB.java`(정규화 32줄 같은 메서드 둘 — 주석·빈 줄·import 는 다르게) · `NegDup.java`(`PosDupA` 와 29줄만 같음) · `golden/check/java.json` · `golden/corpus/check-{egov,commons,jspwiki,roller}.json`·`-hits.txt`.
- 변경: §3 5-35b. `DuplicatesTest`: ① 두 파일 32줄 → 뒤 파일에 한 건, 발췌가 `PosDupA.java:<줄> 과 같음 — 정규화 32줄` ② 29줄 → 0 ③ 같은 파일 안 자기 겹침(반복 줄 60개) → 0 ④ `minLines` 10 으로 덮어쓰면 ③ 도 아니고 29줄이 걸린다 ⑤ `observe` 로 넣은 파일이 상대가 된다(결과는 see 한 쪽에만) ⑥ 한 블록이 세 파일에 → 둘째·셋째에 한 건씩. `CheckRunnerTest`(있으면) 변경분 검사에서 안 바뀐 파일과의 중복이 바뀐 파일에만 남는지 한 케이스.
- 검증: `bash scripts/mvn.sh -q test -Dtest='DuplicatesTest,JavaRulesTest,CheckRulesTest,CheckRunnerTest'` 초록 → `bash scripts/verify.sh --corpus`(약 7분) — `CheckCorpusTest` 골든에 `metric.*` 키가 생긴다. 실측과 견준다(egov: complexity ≈ 38 · methodLength ≈ 19 · nesting ≤ 41 · params 1 · classMethods 46 · classLength 2 · duplicate ≈ 31). 크게 어긋나면(두 배·절반) 정의를 의심하고 멈춘다(§7). diff 요약을 이력에.
- 의존: 5-35a.

**5-36a — 분모·규칙 지문 저장(V013)**
- 대상: `V013__check_trend.sql` · `RuleSet.java`(`fingerprint`) · `CheckRunner.java`(`RunResult.lines`) · `CheckStore.java`(`save` 시그니처·`RunInfo`·`counts`) · `CheckRoutes.java`(`store.save(…, r.files(), r.lines(), rules.fingerprint())`, 응답 `lines`·`rulesHash`) · `CheckStoreTest` · 새 `RuleSetTest.fingerprint`(기존 `RuleSetTest` 가 없으면 `CheckRulesTest` 에).
- 변경: §3 5-36. 지문 시험: 같은 프로필 두 번 같음 · `params.max` 덮어쓰기면 다름 · 묶음 하나 끄면 다름 · 12자 hex. `CheckStoreTest`: 저장 뒤 `run(id)` 의 files·lines·rulesHash · `counts` 가 `grp|severity` 로 맞다 · 옛 행(열이 기본값) 읽기 — `runs()` 가 0·null 을 돌려준다(마이그레이션 뒤 INSERT 를 열 없이 한 행으로).
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckStoreTest,CheckRulesTest,CheckRoutesTest,MigratorTest'` 초록.
- 의존: 없음(5-35 와 독립).

**5-36b — `Trend` 엔진 + API + xlsx**
- 대상: 새 `core/check/Trend.java` · `CheckRoutes.java`(라우트 둘) · 새 `src/test/java/kr/ejg/toolbox/core/check/TrendTest.java` · `CheckRoutesTest`.
- 변경: §3 5-36. `TrendTest`(H2 임시 `Db` + `CheckStore` 에 실행 셋을 손으로 `save`): ① 같은 경로 셋 + 다른 경로 하나 + 변경분 하나 → 점 셋·`skippedChangedOnly` 1 ② 둘째 점 fixed/added — 첫 (A.java, common.sysout)×2 + (B.java, common.todo) → 둘째 (A.java, common.sysout)×1 + (C.java, common.todo) 이면 fixed 2(sysout 1 + B 1)·added 1 ③ 셋째 점은 지문이 달라 `rulesChanged`·fixed null ④ `folders` depth 1·2 ⑤ 경로 대소문자·슬래시가 달라도 같은 폴더. `CheckRoutesTest`: `GET /api/check/trend` 400(path 없음)·200 꼴 · `POST /api/check/trend/export` 파일 생김·시트 둘.
- 검증: `bash scripts/mvn.sh -q test -Dtest='TrendTest,CheckRoutesTest'` 초록.
- 의존: 5-36a.

**5-36c — 화면 「추이」 탭 + 스모크 + 집 검증 상태**
- 대상: `tools/code_check.html` · `tools/code_check_ext.js` · `SmokeHtmlUnitTest`(새 `codeCheckTrend`) · `scripts/puppeteer/visual.js`(code_check 상태에 `{ name: 'trend', prep: p => click(p, '#tabTrend') }` — run 상태 뒤) · 런처 카드 글(`tools/index.html` code_check 설명 끝에 「추이」 한 마디).
- 변경: §3 5-36 화면. `showTab` 의 `tabs` 에 `trend: ['tabTrend', 'paneTrend']`, 규칙 칸 숨김 조건 `name === 'deploy' || name === 'trend'`. `trendRun` → `GET /api/check/trend?path=<#dir>&depth=<#trendDepth>` → 표 셋 + count. `trendXlsx` → `POST /api/check/trend/export` → `TB.result('trendRes', 'ok', {summary, path})`(`#trendRes` 한 칸). 스모크 `codeCheckTrend`: `codeCheckHistoryPicker` 꼴로 프로필·폴더(`A.java` 에 sysout 하나) → 폴더 검사 → `A.java` 의 sysout 줄을 지우고 `B.java`(todo 하나) 추가 → 폴더 검사 → `#tabTrend` 클릭 → `#trendRun` 클릭 → `#trendRuns tbody tr` 2 · 첫 행(최근)의 고쳐짐 칸 `1`·새로 생김 `1` · `#trendFolders` 에 `a` 행 · `#trendXlsx` 클릭 → `#trendRes` 가 `out/` 경로를 가리키고 파일이 있다.
- 검증: `bash scripts/mvn.sh -q test -Dtest='SmokeHtmlUnitTest#codeCheckTrend+codeCheckHistoryPicker+codeCheckRulesFold,ToolsFolderTest,ToolsA11yTest,LauncherTest'` 초록 · `node scripts/puppeteer/visual.js b49`(콘솔 오류 0, 버튼 판정).
- 의존: 5-36b.

**5-35c — PMD 대조 시험 `MetricsCorpusTest`**(5-32e 꼴 — 네트워크, 맨 뒤)
- 대상: `pom.xml`(test 의존성 `net.sourceforge.pmd:pmd-java:7.28.0` — Maven Central 에 있다(2026-10-23 확인, 최신). 전이로 `pmd-core`·antlr4-runtime·Saxon-HE·gson 등 약 11MB, **test scope 만** — PLAN 5-7 의 「PMD 내장」과 다르다: 반입 zip 에 안 든다) · 새 `src/test/java/kr/ejg/toolbox/core/check/MetricsCorpusTest.java`(`@Tag corpus`) · `golden/corpus/metrics-diff-{cc,len,params,methods,dup}.txt` · `scripts/verify-fingerprint.sh`(pom 은 이미 db 레인).
- 변경: 표본 `egov`(1,089 파일)에 ① 우리 `JavaRules`(묶음 metric 만 켬, 기준값은 PMD 와 같게 덮어쓰기)와 ② PMD `PmdAnalysis`(프로그램 API — `PMDConfiguration` + `RuleSetLoader` 로 `category/java/design.xml/CyclomaticComplexity`(`methodReportLevel` 15)·`ExcessiveParameterList`(`minimum` 8)·`NcssCount`(`methodReportLevel` 100)·`TooManyMethods`(`maxmethods` 30), `Report` 의 `RuleViolation` 에서 파일·시작 줄·규칙) 를 돌려 (파일, 메서드 선언 줄, 규칙) 집합으로 맞댄다. ③ 중복은 PMD CPD(`CpdAnalysis`, `minimumTileSize` 를 우리 30 정규화 줄에 가까운 토큰 수 — 실측해 고른다, `ignoreIdentifiers` 끔)의 덩어리 (파일 A 줄, 파일 B 줄) 집합과 우리 `metric.duplicate` 를 「두 자리가 ±3줄 안」 으로 맞댄다. 차이(한쪽에만 있는 것)는 `metrics-diff-*.txt` conformance(B baseline 2% — 분모는 PMD 쪽 건수), 못 읽은 파일은 따로. **정의가 다른 것은 안 맞춘다** — NcssCount 는 빈 줄·주석 뺀 문장 수라 우리 「줄 수」 와 다르다 → 그 차이는 baseline 으로 두고 이력에 「정의 차이」로 적는다. 시험이 먼저 PMD 쪽 건수를 이력에 적을 수 있게 `golden.put("pmd", Map.of(...))` 꼴 요약도 골든에.
- 검증: `bash scripts/offline-build.sh`(m2 에 pmd 들어감, 규칙 5) → `bash scripts/mvn.sh -q test -Dtest=MetricsCorpusTest`(약 2분) → 처음 diff 수·PMD 건수를 이력에(아침에 사람이 본다 — §7 의 「두 배·절반」 기준과 맞대 정의를 의심할지 정한다).
- 의존: 5-35b. 마무리 직전. 어긋남이 커도 **이 번들에서 정의를 고치지 않는다** — 목록만 굳히고 보고.

**마무리** — CLAUDE.md 「마무리」 여덟. 바뀐 레인: java·tools·corpus(`core/check`·`resources/check`·tools·골든) → push 앞 `--corpus`(5-35c 가 돌린 corpus 도장) · db(`resources/db` V013 + `pom.xml`) → `--db`(약 17분, push 와 따로). PR 본문에 청크마다 세 줄 표.

## 5. 금지 사항
- `pure/` · 순수본 규칙 JSON(`golden/check/pure-rules.json` — kind java·dup 는 애초에 안 든다, 바뀌면 안 된다) · 옛 마이그레이션 파일 · `check_finding` 스키마(열 추가 금지 — 이력은 파일·줄·규칙까지, 규칙 3).
- 새 의존성 0.
- 점수·가중치·작성자 열을 만들지 않는다(사용자 결정).
- 발췌·메서드 본문·SQL 을 저장하거나 로그에 남기지 않는다(규칙 3) — 발췌의 메서드 이름·경로·수는 된다.
- 기존 규칙의 판정을 바꾸지 않는다 — `java.json`·corpus 골든 diff 는 `metric.*` 추가뿐이어야 한다.
- 완료 행의 글을 고치지 않는다.

## 6. 최종 검증
- `bash scripts/verify.sh` 초록 → `bash scripts/verify.sh --corpus --db`(push 와 따로) 초록 → `bash scripts/gate-probe.sh` 10 전부 산다.
- 손 확인: `run.bat --profile demo` → 코드 검사에서 데모 폴더를 두 번(사이에 파일 하나 고침) → 추이 탭 → 표 셋·xlsx.
- 회귀: `SmokeHtmlUnitTest` 전부 · `CheckCorpusTest` 골든 diff 가 `metric.*` 뿐 · `PureRulesJsonTest`·`PureCodeCheckTest` 그대로.

## 7. 중단 조건
- §2 의 시그니처·줄 번호가 실제와 다르다(PR #82 머지 뒤 기준) → 멈추고 보고.
- 5-35b 표본 실측이 §2 표와 두 배 이상·절반 이하로 어긋난다 → 정의(§3)와 측정 프로그램의 차이를 적고 멈춘다(골든을 그 수로 굳히지 않는다).
- `RuleSet.load` 가 모르는 kind 를 거절해 5-35a 와 5-35b 를 한 커밋으로 묶어야 한다면 — 그래도 된다(행 둘을 커밋 하나로, 이력에 적는다). 다른 길로 `rules.yaml` 을 쪼개지 않는다.
- 검증이 2회 연속 실패하고 원인이 플랜 범위 밖 · 금지 사항을 어기지 않고는 진행 불가 · 스텝에 없는 파일을 3개 이상 고쳐야 함.
- 보고 형식: 어느 스텝, 무엇이 예상과 달랐는지, 가능한 선택지.

## 8. 불확실 항목
없음 — 실측(§2)으로 지웠다. 실행해야만 아는 것 하나: 5-35b 의 표본 골든 수치(§4 5-35b 검증에 기대치와 중단 기준을 적었다).
