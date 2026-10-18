# 설계 26 — 번들 42: 코드 검사 억제 주석 `tb-ignore` + 쓸모없는 억제 찾기 (Fable, 2026-10-18)

원본은 설계 세션 플랜 파일 — 설계 세션이 바로 옮겨 커밋했다(S0 없음). 실행은 `5-29a` 부터.

이 플랜은 실행 담당 모델(Opus)이 그대로 따라 실행한다. 실행자는 탐색 과정을 모른다. 판단이 필요한 지점을 남기지 않았다. 번들 모드 규칙(CLAUDE.md 「번들 모드」·「청크 규칙」·「마무리」)을 따른다.

사용자가 2026-10-18 정한 것(뒤집지 않는다):
- 억제 건수를 **H2 이력에 남긴다** — 마이그레이션 `V011`(`check_run.suppressed` 컬럼 하나). db 레인이 바뀌므로 마무리에 `verify.sh --db`(약 17분) 한 번.
- **나-7(쓸모없는 억제 주석)을 같이 넣는다** — 규칙 `common.unusedIgnore`(info) 하나로.

## 보정(번들 39~41 과 그 뒤 청크 ↔ 뒤 행)

이력(PROGRESS `2026-10-14`~`2026-10-16`)의 「정한 것(계획 밖)」·「드러난 것」을 대조했다. 이 번들의 행을 바꾸는 것은 없다.
- 번들 39(PR #65 `2c1a431`) — 정한 것: 번들 38 상태 칸 · CLI `alter` 도움말 「[확인:종류]」 · `business` 정규식 로드 때 검증 · 05·07 정확도는 05~07 고를 때만 · `LocalFiles.glob` 문자열 규칙(5-26). 드러난 것: 04 한글명은 코멘트만 · PK 공유 양방향 후보(2-31 이 닫음) · YAML 빈 원소 NPE(버그 기준으로 둠) · 로컬 빠른 검증은 SpotBugs 안 돔. → 코드 검사와 닿는 것은 5-26 `exclude` 뿐이고 이 번들은 `CheckRunner.run` 6인자를 그대로 쓴다.
- 번들 40(PR #66 `da9f053`) — 정한 것: 5-27 왼쪽 사이드바(사용자) · `pure/tools.zip` 추적 제외. 드러난 것: `PureFrozenTest` 가 새 파일을 못 잡음 → 0-55 로 닫힘(완료). → 이 번들은 `pure/` 를 안 건드린다.
- 0-56·4-23·4-23b·0-57·0-58·4-24·0-59·1-64·번들 41(1-61a·1-61b) — 전부 INSERT·순수본 반입·인덱스 쪽. 0-58 이 `fixtures/check/jsp/neg.jsp` 끝에 `<% /* 설명 주석 */ %>` 한 줄을 더했다(5-22 와 맞댐) — 이 번들의 새 픽스처는 **새 폴더 `fixtures/check/ignore/`** 라 그 파일과 안 부딪친다. 드러난 것 4-26(DdlReader UNIQUE)은 대기 행 그대로.
- 5-28(`1dfeab6`) — 내장 83 규칙 전부 `fix:`(parseError 둘 비움). 이 번들이 규칙 하나를 더해 **84** 가 된다 → `CheckRulesTest.builtinRulesCarryFixAndExampleCustomFileLoadsOff` 의 `assertEquals(83, …)` 을 84 로(§4 5-29b).
- 뒤 행 상태 그대로: 4-25·4-26 대기(Opus 가 따로 친다 — 이 번들 밖) · 2-17 입력 대기 · 보류 넷·조건부 셋.
- 비교 문서 `design/compare-2026-10-14.md` ② 축에서 남은 「가치 상」 은 나-1(이 번들)뿐. 나-5(`ref:` 번호)는 사람 몫. 나-2·나-3·나-6 은 끝났거나(5-26) 가치 하·중으로 둔다.

---

### 1. 목표와 범위

- **목표**: 코드 검사(폴더·붙여넣기·변경분·CLI `check`)에서 소스 줄에 `tb-ignore` 표식을 두면 **그 줄의 지적만** 결과에서 빠지고, 결과 머리에 「억제 n」 이 보이며, 아무 지적도 빼지 않은 표식은 `common.unusedIgnore` 로 그 줄에 지적된다. 규칙을 끄거나 정규식을 고치지 않고 오탐 한 줄을 뺄 길이 생긴다(비교 나-1·나-7).
- **사용자가 체감하는 변화**: 지적 줄 끝에 `// tb-ignore common.sysout 로그 유틸` 을 적고 다시 검사하면 그 건이 사라지고 머리줄에 「… · 억제 1 · 이력 #n」. 이력 목록 라벨에 「… · 12건 · 억제 1」. xlsx 는 그대로(억제된 건은 안 실린다).
- **In**: `5-29a` → `5-29b` → `5-29c` → `5-29d` → `5-29e` → 마무리. 가지 `work/2026-10-18`(이 설계 커밋이 이미 그 가지에 있다 — 그 위에 이어 친다). 머지 위임 없음(PR 열고 멈춤).
- **Out**: 순수본 `pure/tools/code_check.html`(동결 — portfolio 지시문으로, §4 5-29e) · 파일 전체 억제(`tb-ignore-file` 같은 것 — 프로필 `codecheck.rules.<id>: false`·`exclude` 가 있다) · 묶음 이름으로 억제(`security` 만 적기) · `@SuppressWarnings` 어노테이션 꼴 · 억제 이유 글 저장(절대 규칙 3 — 이유는 소스에만 남는다) · xlsx 에 억제 목록 · 4-25·4-26 · PMD.

---

### 2. 현재 구조 요약(2026-10-18 실측, main `e72dfe7`)

경로는 `src/main/java/kr/ejg/toolbox/` 기준.

#### 2.1 엔진 — 지적이 만들어지는 자리(여섯)
- `core/check/Rule.java` — `interface Rule { Def def(); List<Finding> apply(Source s); }`. `record Def(id, group, severity, kind, globs, regex, flags, skipComments, message, params, enabled, fix, ref)` + 11인자 호환 생성자. `kind` 는 regex(기본)·absent·file·jsUnusedImport·java·mybatis. `fileLevel()` 은 `file` 이고 `mixedIndent` 가 아닐 때.
- `core/check/Finding.java` — `record Finding(file, line, group, rule, severity, excerpt)`. `excerpt(String)` 160자. 파일 단위 규칙은 줄 1.
- `core/check/Source.java` — `record Source(rel, text, encoding, lineEnding, lang)`. `text` 는 `\n` 으로 맞춘 글. `Source.pasted(text, lang)` 은 rel 「(붙여넣기)」.
- `core/check/RegexRule.java`(:30-54) — 줄마다 `pattern.find` → Finding(i+1). `params.max` 면 건수 규칙: 첫 자리 한 건(`jsp.scriptletCount`). `stripComments(text, lang)`·`skipped` 는 **주석을 공백으로 지운 글**에 맞춘다 — `tb-ignore` 표식은 주석 안에 있으니 **원문(`raw`)에서 찾아야** 한다.
- `core/check/AbsentRule.java`(:43-59) — `params.when` 첫 줄에 한 건. `FileRule.java`(:32-) — 줄 1(또는 mixedIndent 실제 줄). `JsImportRule.java`(:57) — import 줄. `JavaRules.java`(:139 `finding(rule, s, line, excerpt)`, `finish()` :123 — `java.dupMapping` 은 **파일 사이** 규칙, 끝에 모아 낸다). `MyBatisRules.java`(:122 `finding`, `finish()` :104 — `mybatis.namespace` 도 파일 사이).
- `core/check/RuleSet.java` — `load(profile, base)` 가 `defs`(전체)·`enabled`(Rule 목록 — regex·absent·jsUnusedImport·file)·`parsedDefs`(java·mybatis) 로 가른다. `validate(d)`(:103) 는 id·group·severity·globs 비면 예외, regex·absent 만 정규식 검사. `build(d, p)`(:121) 의 `default -> throw "모르는 kind"`. `globs()`(:149) 는 `enabled`+`parsedDefs` 글롭 합집합 — **폴더 목록을 뜨는 데 쓴다**(`CheckRunner:49`). `apply(Source)`(:159) 는 `run().apply(s)`.
  - `final class Run`(:171) — `apply(Source)`: enabled 규칙 → javaRules → myBatisRules(`seeJava`·`apply`), 줄·규칙 순 정렬. `observe(Source)`: 변경분 검사에서 안 바뀐 Java 만(상태만). `finish()`: javaRules·myBatisRules 의 파일 사이 결과, 파일·줄·규칙 순.
- `core/check/CheckRunner.java` — `record RunResult(findings, files, skipped, parseErrors, truncated)`(:22), 생성자 호출 셋(:54·:98·:106 — 전부 이 파일 안, 시험은 `CheckRunner.run(...)` 결과만 읽는다). `run(root, rules, files, ctx, changed, exclude)`(:47) 가 파일마다 `Source` → `run.apply(s)`(바뀐 파일) 또는 `run.observe(s)`(안 바뀐 Java) → `run.finish()` → `changed` 로 거름 → 정렬. `runText(text, lang, rules)`(:101) 는 `Source.pasted` 하나 + `finish()`.
- 발췌·원문은 결과(메모리)에만. 이력·로그에 안 남긴다(절대 규칙 3).

#### 2.2 이력·API·CLI·화면
- `core/check/CheckStore.java` — `save(profile, path, changedOnly, groups, findings)`(:29) → `check_run` INSERT(profile·path·changed_only·rule_groups) + `check_finding` 배치. `record RunInfo(id, profile, startedAt, path, changedOnly, groups, findings)`, `runs()`·`run(id)` 가 `SELECT r.id, r.profile, r.started_at, r.path, r.changed_only, r.rule_groups, (SELECT COUNT(*) …)` → `info(rs)`(:94). `findings(runId)` 발췌 없이.
- `src/main/resources/db/migration/` — `V001__init.sql:114-132` `check_run`·`check_finding`. 마지막 `V010__analyze_screens.sql`. `core/db/Migrator.java` 가 classpath `db/migration/V###__이름.sql` 을 번호 순으로(Flyway 없음). `released-baseline` 은 아직 없다(반입 전) — 새 파일을 더하는 것이 규칙(옛 파일은 안 고친다).
- `web/CheckRoutes.java` — `POST /api/check/run`(:88) job 본문(:120-139): `r = CheckRunner.run/runText` → `store.save(...)` → `result` 맵 `runId·files·skipped·parseErrors·truncated·findings`. `GET /api/check/runs`(:181) 가 `store.runs()` 를 그대로 JSON(RunInfo 칸 이름이 JSON 키). `GET /api/check/runs/{id}` · `POST /api/check/runs/{id}/export`(xlsx — 이력에서, 열 `파일·줄·묶음·규칙·등급·설명·권장 수정·근거`).
- `cli/CodeCommands.java` `check`(:21-80) — `batch.job(POST /api/check/run)` → 요약 `검사 <id> — 파일 n · error a · warn b · info c` + `parseErrors` 면 ` · 파싱 못 함 n`(:59-64). `--fail-on` 은 등급별 수만 본다(억제된 건은 `findings` 에 없으니 자동으로 안 센다).
- `src/main/resources/tools/code_check_ext.js` — `poll(p, jobId)`(:253-271) 결과 머리 `'파일 ' + o.files + ' · 결과 ' + p.findings.length + (o.skipped ? …) + (o.parseErrors ? …) + (o.truncated ? …) + ' · 이력 #' + p.runId`(:266-267). `loadRuns`(:288-304) 이력 라벨 `'#' + r.id + ' ' + when(r.startedAt) + ' · ' + tail(r.path) + ' · ' + r.findings + '건' + (r.changedOnly ? ' · 변경분' : '')`(:299). `openRun`(:307-326) 요약 `'이력 #' + run.id + ' · ' + when(run.startedAt) + ' · ' + p.findings.length + '건 — …'`(:322-323). 결과 표 `render(p)`(:423) 열 `파일·줄·묶음·규칙·등급·원문·권장 수정`, `fixOf[r.id]`(:15·31) 는 `GET /api/check/rules` 의 `fix`.
- `src/main/resources/tools/code_check.html` — 결과 칸 id `dirRes`·`pasteRes`(머리줄, `TB.result`), `dirCount`·`pasteCount`, `runs`(이력 select). 화면 `<style>` 에 색·버튼 금지(`ToolsFolderTest.toolsHaveNoLocalTheme`).
- `src/main/resources/check/rules.yaml` — 83 규칙(regex 54 · absent 7 · file 4 · java 11 · jsUnusedImport 1 · mybatis 6). 머리 주석 :1-6 에 kind 목록. `common` 묶음은 :8-. `fix` 60자 안(`CheckRulesTest`).
- `profiles/example.yaml:129-138` `codecheck:` — groups·rules·customRules·exclude. 이 번들은 프로필 키를 **안 더한다**(억제는 소스 안 표식만, 끄려면 `rules: { common.unusedIgnore: false }`).

#### 2.3 시험 자리
- `core/check/CheckRulesTest` — `FIXTURES = src/test/resources/fixtures/check`, `profile(CodeCheck)` 도우미(UTF-8·LF·headerRequired true·egov35), `fixtureSources(dir, data)`. `fixturesGolden`(:76) — `java/`·`mybatis/` 빼고 **폴더 전체를 걷는다** → `neg*` 는 0 건이어야, 켜진 규칙(`rules.enabled()` — Rule 로 만든 것만) 은 `pos*` 에서 한 번 이상, 골든 `golden/check/fixtures.json`(file·line·rule). `builtinRulesCarryFixAndExampleCustomFileLoadsOff`(:146) — `assertEquals(83, builtin.size())` · security 33 · parseError 둘 빼고 `fix` 60자. `pastedSource`(:174).
- `core/check/CheckStoreTest` — H2 `db`, `saveList`(:38 — `store.save(…)` 셋, `info.findings()`), `runnerOverFixtureFolder`(:57 — `CheckRunner.run(FIXTURES, builtin, files, null)`, `files > 20`·`parseErrors 2`·dupMapping 있음), `runnerSkipsExcludedPaths`(:70).
- `core/check/PureCodeCheckTest`(`@Tag("corpus")`, node) — `DIRS = common·jsp·tsx·security·a11y` 픽스처를 순수본에 붙여 넣어 `fixtures.json` 의 정규식 규칙분과 맞댄다, **픽스처 수 28 단언**. → 새 픽스처는 이 다섯 폴더에 넣으면 안 된다(순수본은 억제를 모른다). `ignore/` 폴더는 DIRS 밖이라 안 본다. `PureRulesJsonTest` 는 `kind == regex` 만 → 새 규칙(kind marker)은 `pure-rules.json` 에 안 든다.
- `core/check/CheckCorpusTest`(`@Tag("corpus")`) — 표본 네 무리 규칙별 건수 골든 `golden/corpus/check-*.json` + 히트 목록. 표본에 `tb-ignore` 는 없다(grep 0) → 골든 불변이 기대값. `common.unusedIgnore` 도 0 건이라 골든 키에 안 생긴다.
- `web/SmokeHtmlUnitTest.codeCheckFolderRun`(:1640) — 임시 프로젝트 `a/A.java`(`// TODO 지운다` + `System.out.println(1)`)·`a/Crlf.java`, 프로필 `t`(UTF-8·LF), `#runDir` 클릭 → `#dirRes` 글·`#dirResult` 행. `codeCheckHistoryPicker`(:1497) 가 `#runs` 라벨을 본다.
- `cli/*CliTest` 에 `check` 를 도는 시험 — `grep -rn '"check"' src/test/java/kr/ejg/toolbox/cli` 로 찾는다(요약 글 단언이 있으면 「억제」 는 0 일 때 안 붙으니 안 깨진다).
- 골든 갱신은 `-Dgolden.update=true`, diff 를 보고 의도된 변화일 때만.

#### 2.4 검증 레인
- 바뀌는 것: `core/check`·`resources/check`·`resources/tools`·시험 → **corpus 레인**(`--corpus` 약 7분) + `resources/db/migration/V011` → **db 레인**(`--db` 약 17분, 사용자 결정). 둘 다 마무리에서 한 번씩(push 와 따로).
- Docker 가 꺼져 있으면 `bash scripts/docker-up.sh`. 표본 폴더 `C:/workspace/toolbox-corpus` + node 는 있다.

---

### 3. 설계 결정

**[고정] 표식 문법** — 소스 줄 **어디든**(주석 문법을 가리지 않는다 — Java `//`·`/* */`, JSP `<%-- --%>`, XML·HTML `<!-- -->`, SQL `--`, properties `#` 전부 같은 토큰):

```
tb-ignore [규칙id[,규칙id…] | *] [이유 자유 글]        ← 이 줄의 지적을 뺀다
tb-ignore-next [규칙id[,규칙id…] | *] [이유 자유 글]   ← 바로 다음 줄의 지적을 뺀다
```
- 규칙 id 는 `rules.yaml` 의 id 그대로(`common.sysout`). 쉼표로 여럿. 생략하거나 `*` 면 그 줄 전부. 묶음 이름(`common`)만 적는 것은 **없다** — 모르는 토큰으로 취급해 아무것도 안 빼고 `common.unusedIgnore` 가 잡는다(오타 방어).
- 이유 글은 읽지 않는다(원문 발췌에 섞여 화면에만 보인다 — 저장·로그 없음, 절대 규칙 3).
- 한 줄에 표식이 둘이면 둘 다 적용. `tb-ignore-next` 의 다음 줄이 없으면(마지막 줄) 쓸모없는 표식.
- 정규식(원문 줄에 적용, 대소문자 구분): `\btb-ignore(-next)?\b(?:[ \t]+([A-Za-z][\w.]*(?:,[A-Za-z][\w.]*)*|\*))?` — 2번 그룹이 없거나 `*` 면 전부. 토큰 뒤에 바로 글자가 오면(`tb-ignored`) `\b` 가 막는다.
- 버린 것: 같은 줄만(ESLint·Semgrep 이 둘 다 두는 까닭과 같다 — XML·JSP 의 긴 줄은 윗줄 주석이 읽기 쉽다) · `@SuppressWarnings` 어노테이션(Java 만 되고 JSP·XML 이 안 된다) · 주석 안인지 검사(언어 여섯의 주석 스캐너를 또 두는 값이 없다 — 문자열 안 `tb-ignore` 는 실물에 없다).

**[고정] 어느 지적에 먹나** — 표식의 줄(또는 다음 줄)과 `Finding.line` 이 같으면. 규칙 종류를 가리지 않는다: regex·absent(`when` 첫 줄)·jsUnusedImport·java(JavaParser 줄)·mybatis(SAX 줄)·파일 사이(`java.dupMapping`·`mybatis.namespace` — 그 파일의 그 줄)·건수 규칙(`jsp.scriptletCount` — 첫 자리 줄)·파일 단위(줄 1 — 1줄에 표식을 두면 빠진다, 권하진 않는다). `common.unusedIgnore` 자신은 억제 대상이 아니다(표식으로 표식을 끄지 않는다 — 끄려면 프로필).

**[고정] 엔진 자리** — 새 클래스 `core/check/Ignores.java`(package-private). 필터는 **`RuleSet.Run` 안**에서 — `apply`·`observe`·`finish` 가 전부 거기 모이고 붙여넣기·폴더·변경분·시험(`rules.apply`)이 같은 길을 탄다. `RegexRule` 은 손대지 않는다(요지의 「RegexRule」 은 실측 뒤 Run 으로 옮김 — 규칙 여섯 종이 다 지적을 내므로 규칙 하나에 두면 나머지가 샌다).

```java
/** 5-29 — 억제 표식. 원문 줄에서 찾는다(주석을 지운 글이 아니라). 이유 글은 읽지도 저장하지도 않는다 */
final class Ignores {
    static final Pattern MARK = Pattern.compile("\\btb-ignore(-next)?\\b(?:[ \\t]+([A-Za-z][\\w.]*(?:,[A-Za-z][\\w.]*)*|\\*))?");
    /** at = 표식이 있는 줄(1부터), line = 먹는 줄, rules 비면 전부 */
    record Mark(int at, int line, Set<String> rules) {}
    private final List<Mark> marks;          // 파일 순서대로
    private final boolean[] used;            // marks 와 같은 길이
    private int suppressed;

    static Ignores of(Source s)              // s.text().split("\n", -1) 을 훑어 Mark 목록. 표식 없으면 EMPTY(marks 비어 있음)
    boolean isEmpty()
    /** 남길 것만 돌려준다. 뺀 수는 suppressed() 에 더하고 쓰인 표식을 기억. common.unusedIgnore 는 안 뺀다 */
    List<Finding> filter(List<Finding> in)
    int suppressed()
    /** 아무것도 안 뺀 표식 — 나-7 */
    List<Mark> unused()
}
```
- `filter` 판정: Finding f 에 대해 `marks` 중 `m.line == f.line && (m.rules.isEmpty() || m.rules.contains(f.rule()))` 인 것이 하나라도 있으면 뺀다(그 표식들 전부 `used`). `f.rule().equals("common.unusedIgnore")` 는 건너뛴다.
- 표식 하나가 여러 지적을 빼도 `suppressed` 는 지적 수로 센다(화면 「억제 n」 = 빠진 지적 수).

**[고정] `RuleSet.Run` 변경**
```java
public final class Run {
    private final Map<String, Ignores> ignores = new LinkedHashMap<>();  // rel → 표식(표식 있는 파일만)
    private int suppressed;

    public List<Finding> apply(Source s) {
        … 기존 out …
        Ignores ig = Ignores.of(s);
        if (!ig.isEmpty()) {
            out = new ArrayList<>(ig.filter(out));
            ignores.put(s.rel(), ig);
        }
        out.sort(…기존…);
        return out;
    }
    public void observe(Source s) { … 기존 …; Ignores ig = Ignores.of(s); if (!ig.isEmpty()) ignores.put(s.rel(), ig); }
    public List<Finding> finish() {
        … 기존 out(javaRules·myBatisRules) …
        out.removeIf(f -> { Ignores ig = ignores.get(f.file()); return ig != null && ig.filter(List.of(f)).isEmpty(); });  // 파일 사이 지적도 표식에 먹는다
        if (markerDef != null) {                                             // 5-29b — 쓸모없는 표식
            ignores.forEach((rel, ig) -> ig.unused().forEach(m -> out.add(new Finding(rel, m.at, markerDef.group(), markerDef.id(),
                    markerDef.severity(), "tb-ignore" + (m.line != m.at ? "-next" : "") + (m.rules.isEmpty() ? "" : " " + String.join(",", m.rules))))));
        }
        suppressed = ignores.values().stream().mapToInt(Ignores::suppressed).sum();
        out.sort(…기존…);
        return out;
    }
    /** finish() 뒤에 뜻이 있다 */
    public int suppressed() { return suppressed; }
}
```
- `unused` 지적의 발췌는 **원문 줄이 아니라 표식만** 재구성한다(이유 글이 사용자 코드라도 응답에만 싣는 것은 다른 지적과 같지만, 표식 지적은 짧은 쪽이 읽기 좋다). 5-29a 에선 `markerDef` 가 아직 없다 — `if` 블록은 5-29b 가 더한다.
- `finish()` 에서 `removeIf` 가 한 건씩 `filter(List.of(f))` 를 불러도 `used`·`suppressed` 가 누적되므로 맞다.
- 변경분 검사: `CheckRunner` 가 끝에 `changed` 로 거르므로 안 바뀐 파일의 `unusedIgnore` 는 결과에서 빠진다 — 의도(변경분만).

**[고정] 규칙 `common.unusedIgnore`** — `rules.yaml` `common` 묶음 끝에:
```yaml
  - id: common.unusedIgnore
    group: common
    severity: info
    kind: marker
    globs: ['*']
    message: 아무 지적도 빼지 않은 tb-ignore 표식
    fix: '지적이 사라졌으면 표식을 지운다 — 규칙 id 오타면 바로잡는다'
```
- `kind: marker` 는 Rule 을 만들지 않는다 — `RuleSet.load` 에서 `d.on() && d.kind().equals("marker")` 면 `markerDef = d` 로 들고 `enabled`·`parsedDefs` 어디에도 안 넣는다(`build` 의 default 예외에 안 닿게 분기를 **앞에**). `globs()` 합집합에서도 뺀다(`'*'` 가 들어가면 폴더의 모든 파일을 읽는다). `validate` 는 그대로 통과(id·group·severity·globs 있음, regex 검사는 regex·absent 만).
- `GET /api/check/rules` 에 다른 규칙처럼 실린다(kind `marker`, `enabled`) → 화면 `common` 묶음에 체크박스 하나 더. 프로필 `codecheck.rules: { common.unusedIgnore: false }` 로 끈다. `PureRulesJsonTest` 는 regex 만 보니 `pure-rules.json` 불변.
- 버린 것: 머리줄 「안 쓴 표식 n」 만 세기(어디인지 모른다) · 별도 결과 표(표·xlsx·이력을 또 만든다).

**[고정] 결과·이력·API·CLI·화면**
- `CheckRunner.RunResult(findings, files, skipped, parseErrors, truncated, int suppressed)` — 6번째. 호출 셋 전부 이 파일(:54 `0`, :98·:106 `run.suppressed()` — **`run.finish()` 뒤에 읽는다**).
- `V011__check_suppressed.sql`: `ALTER TABLE check_run ADD COLUMN suppressed INT DEFAULT 0 NOT NULL;` 한 줄 + 머리 주석 한 줄(`-- 5-29 억제 건수 — 억제된 지적은 check_finding 에 없다. 이유 글은 저장하지 않는다(규칙 3)`).
- `CheckStore.save(profile, path, changedOnly, groups, findings, int suppressed)` — INSERT 에 `suppressed` 열. 5인자 호환 생성은 **안 둔다**(호출 셋 — `CheckRoutes` 하나·`CheckStoreTest` 둘 — 를 고친다). `RunInfo` 8번째 `int suppressed`. `runs()`·`run(id)` SELECT 에 `r.suppressed` 를 **마지막**에(기존 인덱스 1~7 안 흔들리게 8번째).
- `CheckRoutes` job: `store.save(…, r.suppressed())` · `result.put("suppressed", r.suppressed())`(`truncated` 뒤, `findings` 앞). `GET /api/check/runs` 는 RunInfo 가 늘어 자동.
- CLI `check` 요약: `parseErrors` 뒤에 `if (r.path("suppressed").asInt() > 0) sb.append(" · 억제 ").append(…)`. `--fail-on` 불변.
- 화면 `code_check_ext.js`: `poll` 머리 `(o.suppressed ? ' · 억제 ' + o.suppressed : '')` 를 `truncated` 조각 뒤·`' · 이력 #'` 앞에 · `loadRuns` 라벨 `'건' + (r.suppressed ? ' · 억제 ' + r.suppressed : '')` · `openRun` 요약 `p.findings.length + '건' + (run.suppressed ? ' · 억제 ' + run.suppressed : '') + (p.root ? ' — 미리보기는…' : ' — 붙여 넣은…')`(기존 글의 `'건 — '` 를 쪼갠다). 표·xlsx·복사 열은 **그대로**.
- 런처 `tools/index.html` 코드 검사 카드 설명 끝에 「억제 주석 `tb-ignore`」 한 마디(`LauncherTest` 는 카드 수·status·common.js 만 잰다 — 안전).

**[고정] 픽스처** — 새 폴더 `src/test/resources/fixtures/check/ignore/`(PureCodeCheckTest DIRS 밖):
- `neg.java` — `fixtures/check/common/neg.java` 머리 주석(수정일 표) 그대로 + `package a;` + 클래스 안에 ① `System.out.println("x"); // tb-ignore common.sysout 콘솔 유틸` ② `// tb-ignore-next` 다음 줄 `// TODO 지운다` ③ 한 줄에 둘: `e.printStackTrace(); // TODO x  // tb-ignore common.printStackTrace,common.todo`. 기대 **0 건**(억제가 안 먹으면 빨강 — 이 파일이 억제의 음성 증명이다). 실패 사다리: 다른 규칙(예 `java.emptyCatch` 는 java 묶음이라 `fixturesGolden` 에선 꺼져 있다)이 걸리면 그 코드 꼴을 바꾼다 — 표식을 더 붙여 끄지 않는다.
- `neg.jsp` — `fixtures/check/jsp/neg.jsp` 를 통째로 복사한 뒤 끝에 `<%= request.getParameter("q") %> <%-- tb-ignore * 레거시 --%>` 한 줄. 기대 0 건(`jsp.expression` 억제 — a11y 절대 규칙이 걸리면 jsp/neg.jsp 가 이미 만족시키는 꼴이다).
- `pos.java`(5-29b) — 같은 머리 + ① `int a = 1; // tb-ignore common.sysout 없는 지적` ② `// tb-ignore-next` 가 **마지막 줄**(다음 줄 없음) ③ `// tb-ignore commn.sysout 오타` 바로 뒤 줄이 아니라 **같은 줄**에 `System.out.println("y");` → `common.sysout` 은 남고(오타라 안 먹음) `common.unusedIgnore` 셋(①·②·③ 줄). 골든 `fixtures.json` 에 `ignore/pos.java` 행 넷(sysout 1 + unusedIgnore 3) 이 더해진다 — 그 외 행은 불변이어야 한다.

**[고정] 문서** — `docs/pure-code-check.md` 에 절 하나 「순수본엔 억제 주석이 없다 — 백엔드본 5-29 표식 문법 그대로 옮기면 PureCodeCheckTest 가 `fixtures/check/ignore/` 를 DIRS 에 넣어 맞댈 수 있다」. PROGRESS 「사람이 할 것 ④」 portfolio 지시문에 넷째 항목(표식 문법 §3 그대로). `PLAN.md` 3.1 표 아래 한 줄 「억제 주석 `tb-ignore[-next] [규칙id,…|*] [이유]` — 그 줄 지적만 뺀다, 억제 건수는 이력에, 쓸모없는 표식은 `common.unusedIgnore`(5-29)」. README `check` 행은 안 고친다(옵션이 안 늘었다).

---

### 4. 실행 스텝

청크 = 커밋 하나. `verify.sh` 와 `git commit` 은 따로. 커밋 제목 `feat|fix|test|docs: <번호> <무엇>`, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`(실행 세션 모델 이름으로). 청크마다 분할표 행 `완료` + PROGRESS 이력 한 줄(앞 줄 「(이 커밋)」 해시를 채운다). 시험 명령은 `bash scripts/mvn.sh -q -B test -Dtest=<클래스> -Dsurefire.failIfNoSpecifiedTests=false`. 가지 `work/2026-10-18`(설계 커밋이 이미 있다 — S0 없음).

#### 5-29a. 엔진 — `Ignores` + `Run` 필터 + `RunResult.suppressed` + 음성 픽스처
- 대상: `core/check/Ignores.java`(신설) · `core/check/RuleSet.java`(`Run` — §3 코드, `markerDef` 블록은 **빼고**) · `core/check/CheckRunner.java`(`RunResult` 6번째, 호출 셋) · 픽스처 `fixtures/check/ignore/neg.java`·`neg.jsp`(§3) · 시험 `core/check/CheckRulesTest.java`(새 `ignoreMarkers`) · `core/check/CheckStoreTest.runnerOverFixtureFolder`(`assertEquals(…, r.suppressed())` — neg.java 넷(sysout·todo·printStackTrace·todo)… 실제 수는 첫 실행 출력으로 확정해 단언에 적는다, 0 보다 커야 한다).
- `ignoreMarkers` 단언(`RuleSet.load(profile(new CodeCheck(Map.of("java", true, "mybatis", false), …)))` 로 java 묶음 켬 — `profile()` 도우미 그대로):
  - 같은 줄 규칙 지정: `"// 수정일\nclass A { void f() { System.out.println(1); // tb-ignore common.sysout 이유\n} }\n"` → `common.sysout` 없음.
  - 다음 줄: `"// tb-ignore-next\nSystem.out.println(1);\n"` → 없음. 그 다음 줄의 같은 문장은 남음.
  - 전부·여럿: `"System.out.println(1); // TODO  tb-ignore *\n"` → `common.sysout`·`common.todo` 둘 다 없음. `tb-ignore common.todo` 만이면 `common.sysout` 은 남음.
  - 모르는 토큰: `// tb-ignore commn.sysout` → `common.sysout` 남음.
  - 파일 사이: `RuleSet.Run run = rules.run()` 으로 Java 둘(같은 `@RequestMapping("/a")` — `fixtures/check/java/PosController.java` 의 dupMapping 꼴을 본다) 중 하나의 매핑 줄에 `// tb-ignore java.dupMapping` → `run.finish()` 에 그 파일 건은 없고 다른 파일 건은 있다 · `run.suppressed() == 1`.
  - `rules.apply(Source.pasted(…, "java"))` 길도 같은 결과(붙여넣기).
- 골든: `fixturesGolden` 이 `ignore/neg.*` 를 걷는다 — neg 는 0 건이라 `fixtures.json` **불변**. 바뀌면 억제가 안 먹은 것 — 골든을 갱신하지 말고 고친다.
- 검증: `-Dtest=CheckRulesTest,CheckStoreTest,JavaRulesTest,MyBatisRulesTest` 초록 → `bash scripts/verify.sh`. 커밋 `feat: 5-29a 코드 검사 억제 주석 tb-ignore — 그 줄 지적만 뺀다`.
- 의존: 없음

#### 5-29b. 쓸모없는 표식 — 규칙 `common.unusedIgnore`(kind marker)
- 대상: `src/main/resources/check/rules.yaml`(common 끝 규칙 하나 + 머리 주석 :2 kind 목록에 `marker`) · `core/check/RuleSet.java`(`markerDef` 칸 — 생성자 인자 하나 더 · `load` 분기 · `globs()` 제외 · `Run.finish()` §3 `if` 블록) · 픽스처 `fixtures/check/ignore/pos.java`(§3) · 시험 `CheckRulesTest`(`83`→`84` · `ignoreMarkers` 에 단언 둘 — pos.java 꼴의 pasted 소스에서 `common.unusedIgnore` 줄 셋, 프로필 `rules: { common.unusedIgnore: false }` 면 0) · 골든 `fixtures.json`(pos.java 행 넷 추가).
- 검증: `-Dtest=CheckRulesTest -Dgolden.update=true` → `git diff src/test/resources/golden/check/fixtures.json` 이 **`ignore/pos.java` 행 넷 추가뿐**인지 눈으로(다른 행이 바뀌면 중단 조건) → `-Dtest=CheckRulesTest,PureRulesJsonTest,CheckStoreTest` 초록(`pure-rules.json` 불변). `verify.sh`. 커밋 `feat: 5-29b 쓸모없는 억제 표식 — common.unusedIgnore`.
- 의존: 5-29a

#### 5-29c. 이력 — V011 + `CheckStore.suppressed`
- 대상: `src/main/resources/db/migration/V011__check_suppressed.sql`(신설, §3) · `core/check/CheckStore.java`(`save` 6인자 · `RunInfo` 8칸 · SELECT 둘 · `info`) · `web/CheckRoutes.java:122`(`store.save(…, r.suppressed())` 한 줄 — 컴파일 때문에 같이, `result.put` 은 5-29d) · 시험 `CheckStoreTest.saveList`(:40-43 호출 셋에 `suppressed` 인자 — `b` 는 2 · `info.suppressed() == 2`) · `core/db/DbTest`(:49·:64·:67 스키마 판 `10` → `11`, :49 주석 끝에 ` + V011(5-29)`, :67 글 「V001~V011」).
- 검증: `-Dtest=CheckStoreTest,DbTest` 초록 → `verify.sh`. 커밋 `feat: 5-29c 검사 이력에 억제 건수 — V011 check_run.suppressed`.
- 의존: 5-29a

#### 5-29d. API·CLI·화면 — 「억제 n」
- 대상: `web/CheckRoutes.java`(`result.put("suppressed", …)`) · `cli/CodeCommands.java`(요약 ` · 억제 n`) · `src/main/resources/tools/code_check_ext.js`(§3 세 자리) · `src/main/resources/tools/index.html`(카드 한 마디) · 시험 셋:
  - `web/SmokeHtmlUnitTest.codeCheckFolderRun`(:1645 `a/A.java` 글에 줄 하나 더 — `        System.out.println(2); // tb-ignore common.sysout 시험\n` 을 `println(1)` 줄 뒤에) → 결과 표에 `common.sysout` 행이 **하나뿐**(`println(1)`) · `#dirRes` 글에 `억제 1` 포함 · 검사 뒤 `#runs` 의 둘째 옵션(첫째는 「(이번 검사)」) 글에 `억제 1`. 뒤쪽 단언(행 수 ≥ 2 · 줄 칸 숫자 · 복사 행 수 · xlsx)은 행 수가 안 변해 그대로 선다.
  - `web/CheckRoutesTest.textRunAndRules`(:149) — `text` 를 `"class A { void f() { System.out.println(1); // tb-ignore common.sysout\n System.out.println(2); } }"` 꼴로 바꾸고 응답 `suppressed == 1` · `findings` 에 `common.sysout` 하나(2번 줄) 단언. 기존 단언이 건수를 고정하면 그 수를 맞춘다(행 안).
  - `cli/CodeCliTest.checkFailOnAndXlsx`(:41) — 픽스처 폴더 전체(`ignore/` 포함)를 돌므로 요약에 억제가 붙는다 → `assertTrue(r.out().contains(" · 억제 "), r.out())` 한 줄.
- 검증: `-Dtest=SmokeHtmlUnitTest#codeCheckFolderRun,SmokeHtmlUnitTest#codeCheckHistoryPicker,CheckRoutesTest,CodeCliTest,ToolsFolderTest,LauncherTest` 초록 → `-Dtest=SmokeHtmlUnitTest` 전체 초록 → `verify.sh`. 커밋 `feat: 5-29d 억제 건수 표시 — 결과 머리·이력 라벨·CLI`.
- 의존: 5-29c

#### 5-29e. 문서 — 순수본 지시문·PLAN 3.1
- 대상: `docs/pure-code-check.md`(절 하나, §3 문서) · `PLAN.md` 3.1(한 줄) · `PROGRESS.md` 「사람이 할 것 ④」(portfolio 지시문 넷째: 억제 주석 문법 §3 — 순수본 `code_check.html` 의 결과 모으는 자리에서 원문 줄 표식으로 거르기 + `fixtures/check/ignore/` 를 `PureCodeCheckTest.DIRS` 에 넣어 맞대기).
- 검증: `bash scripts/verify.sh`(docs 레인 — 존댓말 린트). 커밋 `docs: 5-29e 억제 주석 — 순수본 지시문·PLAN 3.1`.
- 의존: 5-29d

#### 마무리(CLAUDE.md 「마무리」 그대로)
독립 리뷰 → `gate-probe.sh` → `bash scripts/verify.sh` 끝 줄이 **corpus 와 db 둘 다** 알린다 — `verify.sh --corpus`(약 7분: `CheckCorpusTest` 골든 불변·`PureCodeCheckTest` 28 그대로) 와 `verify.sh --db`(약 17분: V011 마이그레이션이 컨테이너 시험에서도 돈다) 를 **따로**(push 와도 따로) → push → PR(본문에 청크마다 시작 세 줄 표) → CI 폴링 → AI 리뷰 처분(버그만) → 머지 위임 없음, PR 열고 멈춘다.

---

### 5. 금지 사항

- `pure/` 아무것도(동결 — `PureFrozenTest`). 순수본 차이는 지시문으로만.
- `RegexRule.stripComments`·`skipped`·`blankCommentScriptlets` 를 고치지 않는다(프로그램 분석도 쓴다). 표식은 원문에서 찾는다.
- `Finding` 레코드 칸을 늘리지 않는다(이력·xlsx·JS 가 칸 순서를 본다). 억제 이유 글을 `Finding`·H2·로그·xlsx 어디에도 싣지 않는다(절대 규칙 3).
- `V001~V010` 수정 금지. 새 파일 `V011` 만.
- 새 의존성 0. 프로필 키 추가 0(`Profile.CodeCheck` 불변).
- `fixtures/check/common·jsp·tsx·security·a11y` 에 파일을 더하지 않는다(`PureCodeCheckTest` 28 단언·순수본 대조가 깨진다). 새 픽스처는 `ignore/` 에만.
- `golden/corpus/check-*` 를 갱신하지 않는다 — 바뀌면 억제가 표본에 먹은 것이고 그건 결함이다(중단 조건).
- `globs()` 에 `'*'` 가 들어가게 두지 않는다(폴더의 모든 파일을 읽어 느려지고 `skipped` 가 는다).
- 시험을 고쳐 통과시키지 않는다(`83`→`84` 와 `save` 인자·스모크 픽스처 줄 추가는 행이 정한 것).

---

### 6. 최종 검증

- `bash scripts/verify.sh` 초록(java·tools·docs 빠른) + `--corpus` 초록(`CheckCorpusTest` 골든 diff 0 · `PureCodeCheckTest` 통과) + `--db` 초록(`CheckStoreTest` 포함 컨테이너 레인 — V011 적용).
- `gate-probe.sh` 아홉 산다(「패치 갱신 필요」 면 다시 떠서 커밋).
- 수동(일회, 저장소 밖 data 폴더로 `run.bat --port 41791` 또는 `toolbox.bat check <폴더>`): ① 임시 폴더에 `A.java`(sysout 둘, 하나에 `// tb-ignore common.sysout x`) → 화면 폴더 검사 머리 「파일 1 · 결과 1 · 억제 1 · 이력 #n」, 표에 sysout 한 행 ② 표식 줄의 sysout 을 지우고 다시 검사 → `common.unusedIgnore` 한 행(info) ③ 이력 select 라벨에 「억제 1」 ④ `toolbox.bat check <폴더>` 요약에 ` · 억제 1` ⑤ 붙여넣기 탭 Java 에 같은 글 → 같은 결과. 콘솔 오류 0.
- 회귀: `golden/check/fixtures.json` 차이는 `ignore/pos.java` 행 넷뿐 · `pure-rules.json`·`golden/corpus/check-*` 불변 · `SmokeHtmlUnitTest` 전체 초록.

---

### 7. 중단 조건

다음이면 임의로 해결하지 말고 멈추고 보고(어느 스텝 · 무엇이 예상과 달랐나 · 선택지):
- 플랜의 구조 설명이 실제 코드와 다름(예: `RuleSet.Run` 에 이미 필터가 있다 · `RunResult` 호출이 이 파일 밖에도 있다 · `CheckStore.save` 호출이 셋이 아니다).
- 검증이 2회 연속 실패하고 원인이 플랜 범위 밖.
- `fixtures.json` diff 에 `ignore/` 밖 행이 생기거나 `golden/corpus/check-*` 가 바뀜(억제가 다른 데 먹었다).
- 금지 사항을 어기지 않고는 진행 불가.
- 스텝에 없는 파일을 3개 이상 수정해야 함.
- 번들 모드 규칙대로 — 갈리면 행당 둘까지 정하고 「정한 것(계획 밖)」, 넘으면 `wip/<청크>`.

---

### 8. 불확실 항목

설계 세션이 2026-10-18 전부 확인해 §2·§4 에 넣었다 — 실행자가 따로 확인할 것 **없음**. 확인한 사실:
- `CheckStore.save` 호출 = `CheckRoutes:122` + `CheckStoreTest:40·41·43` 넷. `CheckRunner.RunResult(` 생성 = `CheckRunner.java` 안 셋(:54·:98·:106)뿐.
- 마이그레이션 수 단언은 `core/db/DbTest`(:49·:64·:67, `10`) — 5-29c 가 11 로. `MigratorTest` 는 없다.
- `web/CheckRoutesTest` 있음(`folderRunHistoryExport`·`fileLevelFindingsShowFileInExport`·`textRunAndRules`·`refusals`·`changedOnlyOnGitWorkingCopy`·`deployListOnGit`). CLI 는 `cli/CodeCliTest.checkFailOnAndXlsx`(픽스처 `fixtures/check` 통째 복사 뒤 `check check --profile t`) · `CliCorpusTest:53`(표본, 억제 0 이라 불변).
- `SmokeHtmlUnitTest.codeCheckFolderRun` 뒤쪽 단언은 행 수·줄 칸 숫자·복사 행 수·xlsx — 억제가 먹으면 행 수가 안 변해 그대로 선다.
- (확인됨 2026-10-18) portfolio 세션에 물었다 — 순수본 `code_check` 에 억제 주석 계획·작업 0, 진행 중 code_check 변경 0, portfolio main `f48c4d6`(0-57 로 이미 반입). 겹치지 않는다. 표식 문법은 이 문서 §3 이 기준이고 portfolio 는 지시문(5-29e)을 받으면 그대로 맞춘다. 순수본이 억제를 갖게 되면 그 반입 행에서 `fixtures/check/ignore/` 를 `PureCodeCheckTest.DIRS` 에 넣는다 — 이 번들 안에선 안 한다.

---

실행은 Opus 세션에서 「번들 해」 → `5-29a` 부터(가지 `work/2026-10-18` 에 이 문서 커밋이 있다).
