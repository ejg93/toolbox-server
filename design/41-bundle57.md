# 설계 41 — 번들 57: 철저화 R1 — 표본 확장 · 채점 하네스 · 참고 도구 비교 (Fable 설계 · Opus 실행, 2026-10-28)

## 맥락
사용자 2026-10-28 「토큰을 써서 이 검증 도구의 기능을 철저하게」. 방법은 둘 — **표본을 늘려 실물에서 깨지는 곳을 찾는다**, **참고 도구의 규칙·아이디어를 비교표로 뽑아 다음 설계의 입력으로 삼는다**. 이 번들은 그 첫 라운드(R1)다. 규칙을 새로 넣거나 파서를 고치지 **않는다** — 드러난 것은 전부 목록으로 얼리고 `design/compare-2026-10-28.md` 에 적어 다음 설계 세션(R3)이 행으로 만든다.

**사용자 결정(2026-10-28, 뒤집지 않는다)**
- 장기 진행. 「한 달 제한」은 이 기능 철저화에 안 건다. 검증 인프라(verify 속도·훅) 최적화는 그대로 금지
- **에이전트 최소** — 조사는 참고 저장소를 `git clone --depth 1` 로 받아 grep·Read 로 직접 읽는다. 서브에이전트는 마무리 독립 리뷰 하나만. 웹 전용 문서가 꼭 필요하면 `web-fetch`(model `sonnet`) 하나까지
- 레거시 표본은 **넷 다**(jeecg · jeesite v1.2.7 · zheng · RuoYi 원판). 넷 다 GitHub 에 있어 받기 스크립트의 호스트는 안 바꾼다. Gitee 전용(jeesite4·guns)은 뺐다
- 보안 채점 표본 — **OWASP Benchmark 는 MANIFEST**(주 채점), **Juliet 은 선택 표본**(MANIFEST 밖, 없으면 건너뛴다). 둘 다 저장소 밖에만 둔다
- KISA 가이드는 **받아 두었다**(2026-10-28, kisa.or.kr 법령·가이드라인 게시판 postSeq 5·9) — `C:/workspace/toolbox-ref/kisa/post5-2-소프트웨어_개발보안_가이드(2021.12.29).pdf`(3.5MB) · `post9-1-소프트웨어_보안약점_진단가이드(2021).pdf`(32MB) + 같은 판 hwpx 둘. Git Bash curl 은 KISA 인증서 체인 때문에 실패하고 PowerShell `Invoke-WebRequest`(Windows TLS)는 된다 — 다시 받을 때 `https://www.kisa.or.kr/post/fileDownload?menuSeq=2060204&postSeq=<5|9>&attachSeq=<1|2>&lang_type=KO`
- **머지 위임 있음** — CI 초록 + AI 리뷰 처분 뒤 실행자가 머지

**라운드** — R1(이 번들: 수집 + 채점 + 비교표) → R3(설계 세션: 비교표·A 후보 목록 → 분할표 행) → R4~(번들 실행 → 표본 재측정). R2 하네스는 이 번들의 V 행에 흡수했다.

실행자는 Opus. 차례는 `S0` → `V-35a` → `V-35b` → `V-36a` → `V-36b` → `V-37` → `R-1` → `R-2` → `R-3` → `R-4` → `R-5` → `R-6` → `R-7` → `0-73` → 마무리. 가지 `work/2026-10-28`(S0 커밋 때 main 에서 딴다). 번들 하나·PR 하나.

---

## 1. 목표와 범위
- 한 문장: 실물 표본을 레거시 Java·JSP 넷 + 보안 정답지 둘 + SQL 파서 정답지 하나로 늘려 코드 검사·프로그램 분석·SQL 표 추출을 **숫자로 채점**하고, 참고 도구 일곱 축을 우리 규칙·화면과 맞댄 비교표를 만들어 다음 설계의 입력을 준비한다.
- 완료 시 체감: `verify.sh --corpus` 가 새 표본 여섯을 돌고, `golden/corpus/` 에 Benchmark 정밀도·재현율(범주별)·sqlfluff 표 추출 일치율·레거시 A 후보 목록이 생기고, `design/compare-2026-10-28.md` 에 축 일곱의 표와 「후보 행 제안」·「드러난 것」이 있다.
- In scope: `scripts/corpus-fetch.sh`(SOURCES 6줄 + ZIP 블록) · `scripts/puppeteer/corpus-common.js` SKIP · `corpus/SOURCES.md`·`MANIFEST` · 새 corpus 시험 넷(`LegacyCorpusTest`·`SecurityBenchmarkCorpusTest`·`JulietCorpusTest`·`SqlFixtureCorpusTest`) + 골든 · `design/41-bundle57.md` · `design/compare-2026-10-28.md` · PLAN 4장 문단·분할표 행·번들 표 · PROGRESS · 실브라우저 확인(0-73, 조건부).
- Out of scope: 규칙 추가·수정 · 파서 수정 · `pure/` · 화면 변경 · Benchmark 를 SpotBugs 로 채점(컴파일 필요 — 버림) · JHipster · Gitee 전용 저장소 · 새 Maven 의존성 · 기존 corpus 시험(`CheckCorpusTest.GROUPS` 등)에 새 표본 끼우기 · 반입 zip.

## 2. 현재 구조 요약(탐색 2026-10-28 — Explore 셋 + 실측)

**표본 체계**
- `scripts/corpus-fetch.sh:20-43` `SOURCES=("이름|owner/repo|태그·커밋|pathspec…")` — GitHub 고정(`:62`), `fetch --depth 1`, 캐시 `C:/workspace/.corpus-src/<이름>@<ref>`, 잘라 오기 `git ls-files -z -- $@ | xargs -0 cp --parents`(`:70`). `MANUAL=(csv)`(`:45`), `MAVEN`(`:48`), `BUILD`(`:52`) 는 MANIFEST 밖 선택 표본 — 이름을 골라야 받는다(`:110,119`). MANIFEST 는 실행마다 통째로 다시 쓴다(`:133-137`) — 로컬에 없는 출처 줄이 사라지니 diff 확인.
- `corpus/MANIFEST` 24줄(23 출처 · 14,698 파일). `corpus/SOURCES.md` 표 열 `| 이름 | 저장소 | 태그·커밋 | 라이선스 | 잘라 온 것 | 쓰는 행 |`, 선택 표본은 이름에 `(선택)` + 「MANIFEST 밖」(`:15,33,34`). 표 마지막 행은 34줄 `ruoyi-bin(선택)`.
- `src/test/java/kr/ejg/toolbox/CorpusFiles.java` — `root()`(`TOOLBOX_CORPUS` → `../toolbox-corpus`), `verify()`(MANIFEST 전 출처 지문 대조, 없으면 빨강), `files(source, globs…)`, `rel(p)`, `none(what, broken, total)`(등급 A), `baseline(name, broken, total)`(등급 B 2%), `conformance(name, list)`(상한 없는 목록 — 바뀌면 빨강). `GoldenFiles.assertJson(name, value)` · `-Dgolden.update=true` 로 골든 생성.
- 등급 A 전형 `CheckCorpusTest.java:56-104` — `CheckRunner.run(dir, rules, files, null)` → `.parseError`·예외는 A, 규칙별 `{count, files}` 를 `check-<무리>.json`, 히트 파일 목록은 `conformance("check-<무리>-hits")`. 규칙은 `CheckCorpusTest.rules(boolean egov)`(package-private static — 같은 패키지에서 재사용).
- `AnalyzeCorpusTest.java:112-129` — `AnalyzeRunner.run(root, new LocalFiles(tmp), null, null)` 요약 키 `files·programs·withCrud·statements·tables·risks·reachedRisks`.
- **함정 ①** `scripts/puppeteer/corpus-common.js:7` `SKIP = new Set(['egov-prev', '.manifest', 'egov35-lib', 'jspwiki-bin', 'ruoyi-bin'])` — 노드 표본 스크립트 셋(`corpus-jspfmt.js` 모든 `*.jsp` · `corpus-strip.js` java·jsp·js · `corpus-js.js` json·xml·jsp)이 **표본 루트의 폴더를 전부 걷는다**. 새 폴더를 SKIP 에 안 넣으면 `JspFmtCorpusTest`·`StripCorpusTest`·`JsCorpusTest` 가 레거시 JSP 에서 등급 A 로 빨개진다. 자바 쪽 시험은 전부 출처 이름을 명시한다(자동 포함 없음).
- **함정 ②** 레인 — `scripts/verify-fingerprint.sh:147-149`: `design/` 은 어느 레인에도 없다(지문 안 바뀜, doc-lint 훅도 안 본다 — 손으로 `bash scripts/doc-lint.sh <파일>`). `scripts/corpus-fetch.sh`·`corpus/SOURCES.md` 는 tools 레인, `corpus/MANIFEST`·`golden/corpus`·`scripts/puppeteer`·`src/test/.../core/{analyze,check,gen,…}` 는 corpus 레인. 새 시험을 `core/check`·`core/analyze` 아래 두면 `LANE_CORPUS` 변경 없음.
- **함정 ③** 시험 하나 상한 — corpus 태그 10분(CLAUDE.md 작성 규약). Juliet 은 파일 수가 많아 넘을 수 있다(§8).
- 저장소 밖 폴더: `C:/workspace/toolbox-corpus` 15,706 파일 · 290MB, 디스크 여유 593GB.

**규칙 체계(비교표 「우리 현재」 재료)**
- 내장 규칙 `src/main/resources/check/rules.yaml` 105개(`CheckRulesTest.java:167`) — security 36 · a11y 17 · java 13 · common 10 · tsx 7 · metric 7 · mybatis 6 · file 4 · jsp 3 · ext 2. ID `<group>.<camelCase>`. `ref:` 는 `java.declarationOrder` 하나뿐(`:764`), 보안 36 은 전부 비어 있다(사용자 결정 — 사람이 채운다, `rules.yaml:6`).
- 보안 규칙 줄: sqlDollar 211 · sqlConcat 220(`"\s*(?:SELECT|…)\b[^"]*"\s*\+`) · xssExpression 229(JSP 만 — `*.jsp *.jspf *.tag`) · pathTraversal 246(`new File…(…getParameter(`) · hardcodedPassword 254 · exceptionMessage 263 · osCommand 275(`Runtime.getRuntime().exec(|new ProcessBuilder(`) · deserialization 283 · codeInjection 291 · ssrf 299 · openRedirect 307 · responseSplitting 315 · weakHash 323 · weakCipher 332 · weakRandom 341 · hardcodedKey 349 · jsEval 357 · domXss 365 · jsxDangerousHtml 373 · jsEnvSecret 381 · jsTokenStorage 389 · jsHrefJavascript 398 · jspElParam 407 · fileUpload 415 · piiRrn 426 · piiCard 434 · piiPhone 442 · piiLog 450 · cfgDebugLog 459 · cfgDebugLogProps 468 · cfgStackTrace 477 · cfgErrorPage 486 · cfgSessionTimeout 496 · cfgCookieHttpOnly 506 · cfgCookieSecure 516(뒤 넷은 absent).
- SQL — 파서 라이브러리 없음. `core/analyze/SqlTables.java:75` `extract(String text, String tag)` → `Result(verb, refs[Ref(table, crud)], unresolved)`(SELECT·INSERT·UPDATE·DELETE·MERGE·TRUNCATE) · `SqlJoins.extract(sql)` · `core/gen/DdlReader.java:126` `read(ddl)` → `Result(tables[core.meta.Table], unreadable, notes)`. 구조 점검 `core/deliverable/StructureLint.java:28-37` 규칙 9 · 품질 SQL `core/quality/QualitySql.java` 8종 × 방언 5 · 표준 감사 `core/logical/Audit.java:24-35` 5.
- 코드 정리 `core/check/Cleanup.java:26-27` `RULES` 9(A). B 목록은 저장소에 없다(사용자 메모리에만 「B 18」).
- 프로그램 분석 — JavaParser 3.26.3(`JavaSource` 21 → 8 되읽기), `JavaGraph`·`MapperIndex`·`JpaIndex`·`JspLinks`.
- 차등 시험 선례 — `DeclarationOrderCorpusTest`(Checkstyle), `MetricsCorpusTest`(PMD), `SpotBugsSeverityTest`(sonar-findbugs 등급표 YAML 픽스처 → diff 골든 txt), `SpotBugsCorpusTest`. **정규식 보안 36 을 외부 정답지와 맞댄 시험은 없다.**
- 비교표 선례 `design/compare-2026-10-14.md`(132줄) — 머리 1~8줄(열·가치·판정 기준), 축마다 「외부 — …」 한 줄 + 표 `| # | 그쪽이 하는 것 | 우리 현재 | 판정 | 차이 | 가치 | 한 달 |` + `**버린 것**`, 끝에 `## 후보 행 제안`·`## 드러난 것`. 행 번호 가-n·나-n….

**후보 실측(2026-10-28, `gh api` 트리·`git ls-remote`·raw LICENSE)**

| 표본 | 저장소·고정 커밋 | 라이선스 | 파일 |
|---|---|---|---|
| jeecg | zhangdaiscott/jeecg `2608008efc47594c8920d4de74f3b3d475c348c2`(2021-08-17) | Apache-2.0 | JSP 388 · Java 608 · FTL 209 · SQL 23(`docs/*.sql`) — JSP 는 `src/main/webapp/`, Java·FTL 은 `src/main/java/` |
| jeesite | thinkgem/jeesite 태그 `v1.2.7`(`0856a3adcb414c503cc8338fe6aa93d2ecc80685`) | Apache-2.0 | JSP 122(`src/main/webapp/WEB-INF/views`) · Java 281 · 매퍼 34(`src/main/resources/mappings/**/*Dao.xml`) · SQL 60(`db/`) |
| zheng | shuzheng/zheng `7005c0a775e6d014d1dc8a8a809f7b1c13bf785a`(2019-09-01) | MIT | JSP 114(`*/src/main/webapp/WEB-INF/jsp`) · Java 379 · 매퍼 43(`*/src/main/java/**/*Mapper.xml`) · SQL 3(`project-datamodel/`) |
| ruoyi-classic | yangzongzhuan/RuoYi `9cd770d2ac839999d6a425f70f3bd2cf3a0ee5cd`(2026-10-10) | MIT | Thymeleaf html 149(`*/src/main/resources/templates`) · Java 285 · 매퍼 27 · 생성기 템플릿 vm 14(`ruoyi-generator/src/main/resources/vm/{html,java,sql,xml}`) · `sql/*.sql` 2 |
| benchmark | OWASP-Benchmark/BenchmarkJava `f40c618c5a709afce350ef06ed2899c85cf586e9`(2026-10-27) | **GPL-2.0** — 저장소 밖만, 골든은 파일 이름·건수 | `src/main/java/org/owasp/benchmark/testcode/BenchmarkTest*.java` 2,740 + `expectedresults-1.2.csv`(머리 `# test name, category, real vulnerability, cwe` · 행 `BenchmarkTest00001,pathtraver,true,22`) · 범주 11: cmdi crypto hash ldapi pathtraver securecookie sqli trustbound weakrand xpathi xss |
| juliet(선택) | NIST SARD zip `https://samate.nist.gov/SARD/downloads/test-suites/2017-10-01-juliet-test-suite-for-java-v1-3.zip`(HTTP 200, 76,798,417 B) | 공개(NIST, 미국 정부 저작물) | zip 안 경로 접두는 §8 ① — `src/testcases/CWE<n>_…/*.java` 꼴. 파일마다 `bad()`·`good*()` 메서드, 변형 51~81 은 `_bad`·`_good*` 파일로 갈라진다 |
| sqlfluff-dialects | sqlfluff/sqlfluff 태그 `4.4.0`(`0348222a3bcdb0226563f7349c5a39beb0641955`) | MIT | `test/fixtures/dialects/<방언>/*.sql` + 같은 이름 `.yml`(파스 트리 — `table_reference: naked_identifier: X`, 머리 `_hash`) — ansi 208 · oracle 106 · mysql 253 · mariadb 238 · postgres 175 · tsql 189 · sqlite 41 · db2 22 · `test/fixtures/rules/std_rule_cases/*.yml` 94(규칙마다 `fail_str`·`fix_str`) |

**참고 도구 실측(조사 축 재료 — 저장소 밖 `C:/workspace/toolbox-ref/` 에 받는다)**

| 축 | 저장소 | 라이선스 | 읽을 자리 · 수 |
|---|---|---|---|
| R-1 | alibaba/p3c | Apache-2.0(README 배지) | `p3c-pmd/src/main/resources/rulesets/java/ali-{comment,concurrent,constant,exception,flowcontrol,naming,oop,orm,other,set}.xml` `<rule name= message=>` 56(6·9·2·3·4·11·7·1·7·6), 글은 `p3c-pmd/src/main/resources/messages.xml` |
| R-1 | SonarSource/sonar-java | LGPL-3.0 — 메타데이터 제목만 읽는다 | `sonar-java-plugin/src/main/resources/org/sonar/l10n/java/rules/java/S*.json` 779(`title`·`type`·`defaultSeverity`·`tags`) |
| R-2 | find-sec-bugs/find-sec-bugs | LGPL-3.0 — 패턴 이름·설명만 | `findsecbugs-plugin/src/main/resources/metadata/findbugs.xml`(BugPattern) · `messages.xml`(ShortDescription) · Detector 125 |
| R-2 | semgrep/semgrep-rules(develop) | LGPL-2.1 — id·message·cwe 만 | `java/lang/security/**/*.yaml` 86(시험 파일 제외) |
| R-3 | sqlfluff/sqlfluff `4.4.0` | MIT | `src/sqlfluff/rules/{aliasing,ambiguous,capitalisation,convention,layout,references,structure,oracle,postgres,tsql,jinja}/*.py` 79 — `class Rule_XXnn` 바로 아래 docstring 첫 줄 |
| R-3 | jarulraj/sqlcheck | Apache-2.0 | README 반패턴 29(논리 7 · 물리 5 · 질의 16 · 응용 1) — raw README 만 받는다 |
| R-3 | schemacrawler/SchemaCrawler | Eclipse/GPL/LGPL 삼중 — 이름만 | `schemacrawler-lint/src/main/java/schemacrawler/tools/linter/LinterProvider*.java` 22 |
| R-4 | openrewrite/rewrite-static-analysis | Apache-2.0(`LICENSE`) | `src/main/java/org/openrewrite/staticanalysis/*.java` 201 — `getDisplayName()` 의 `return "…"` |
| R-5 | ruoyi-classic 표본의 `vm/` 14 + RuoYi-Vue 캐시 클론 `C:/workspace/.corpus-src/ruoyi@13db1fcef36bee9ce45d2d636a1d4e8f5ed5bbc3/ruoyi-generator/src/main/resources/vm/` | MIT | 템플릿 꼴·옵션(`tplCategory` crud·tree·sub) |
| R-6 | beautifier/js-beautify | MIT | `js/src/html/options.js`(4,287 B) |
| R-6 | gchq/CyberChef | Apache-2.0 | `src/core/config/Categories.json`(16,998 B) |
| R-2 | KISA 가이드 PDF | 사용자가 둔다 | `C:/workspace/toolbox-ref/kisa/*.pdf` — Read 도구 `pages` 로 목차·항목 이름 |

도구: Python 3.14 + pip 있음, node v22, `sqlfluff`·`semgrep` 미설치(안 깐다 — yml 정답지로 대신한다). Docker Desktop 있음.

## 3. 설계 결정 — 전부 [고정]
1. **R1 은 고치지 않는다.** 새 표본에서 나온 등급 A 후보(parseError·예외·느림)는 `conformance("legacy-a")` 등 **목록으로 얼린다**. 기존 규칙 「A 는 baseline 없이 0」은 R3 가 행을 세워 승격할 때 적용한다. 이유: 조사 번들에서 파서를 고치기 시작하면 끝이 없고, 어디가 얼마나 깨지는지 **먼저 한눈에** 보는 것이 목적이다. 버린 대안 — 새 표본을 `CheckCorpusTest.GROUPS` 에 끼우기(A 가 바로 빨강, 커밋 불가).
2. **새 표본 폴더는 `corpus-common.js` SKIP 에 넣는다**(함정 ①). 노드 시험 셋은 R3 가 레거시 JSP 를 받을 준비가 되면 뺀다. 버린 대안 — 표본 루트를 둘로 나누기(CorpusFiles·스크립트 둘 다 고쳐야 함).
3. **채점은 파일 단위**(Benchmark) · **메서드 영역 단위**(Juliet). Benchmark: CSV 행마다 「그 파일에 범주에 매핑된 규칙 지적이 하나라도 있나」 — real=true 면 TP/FN, false 면 FP/TN. Juliet: 지적 줄이 `bad` 메서드 영역이면 TP, `good*` 영역이면 FP, `_bad`·`_good*` 파일은 파일 전체가 그 영역. 줄 단위 정답은 Benchmark 에 없다.
4. **범주 → 규칙 매핑 초기값**(시험 코드의 상수, R3 가 고친다): Benchmark `cmdi→security.osCommand` · `sqli→security.sqlConcat` · `pathtraver→security.pathTraversal` · `crypto→security.weakCipher` · `hash→security.weakHash` · `weakrand→security.weakRandom` · `xss`·`ldapi`·`xpathi`·`trustbound`·`securecookie→(없음)`. Juliet `CWE78→osCommand` · `CWE89→sqlConcat` · `CWE23·CWE36→pathTraversal` · `CWE327→weakCipher` · `CWE328→weakHash` · `CWE330·CWE338→weakRandom` · `CWE601→openRedirect` · `CWE113→responseSplitting` · `CWE259→hardcodedPassword` · `CWE321→hardcodedKey` · `CWE209→exceptionMessage` · `CWE80·CWE81·CWE83→(없음)`. 매핑 없는 범주도 결과 json 에 `rules: []` 로 남긴다 — 그것이 미탐 범주 목록이다.
5. **Benchmark 는 정규식 36 만 잰다.** SpotBugs·findsecbugs 채점은 Benchmark 컴파일(Maven, 의존 수백)이 필요해 버린다. 결과 json 에 범주별 `otherRules`(매핑 밖 보안 규칙 지적 수)를 함께 적어 소음을 본다.
6. **sqlfluff 는 설치하지 않는다.** 저장소에 있는 `.yml` 파스 트리가 정답지다 — `table_reference` 잎의 **마지막 식별자**(스키마 뺀 표 이름, 대문자, 따옴표·백틱·대괄호 제거)를 모아 `SqlTables.extract` 의 표 집합과 맞댄다. DML(`select_statement`·`insert_statement`·`update_statement`·`delete_statement`·`merge_statement` 만 든 파일)은 `SqlTables`, `create_table_statement` 가 든 파일은 `DdlReader.read(...).tables()` 이름과 맞댄다. 그 밖(ALTER·함수·프로시저 등)은 `skipped` 로 센다. 차이는 상한 없는 `conformance("sqltables-diff")` — 비율이 아니라 **어떤 구문**이 어긋나는지가 R3 의 입력이다.
7. **비교표는 축 하나 = 커밋 하나**, 문서 하나(`design/compare-2026-10-28.md`). 열 `| # | 그쪽이 하는 것 | 우리 현재 | 판정 | 차이 | 가치 | 오탐 위험 | 재는 표본 |` — 2026-10-14 의 「한 달」 열을 「오탐 위험 상·중·하」·「재는 표본(어느 표본·시험으로 잴 수 있나, 없으면 ×)」 둘로 바꾼다. 축마다 **참 행 25 이하**(그쪽에 있고 우리에 없음), 거짓(이미 있음)은 수만 적는다. 참고 저장소는 `C:/workspace/toolbox-ref/<이름>` 에 `git clone --depth 1`, 커밋·라이선스를 문서 머리 표에 적는다. 받은 코드는 **읽기만** — 실행·빌드·import 금지(내려받은 파일은 신뢰하지 않는다).
8. **GPL·LGPL 자료는 데이터·이름만.** Benchmark(GPL-2.0)는 표본 폴더에만, 골든은 `BenchmarkTestNNNNN` 이름·건수. sonar-java·find-sec-bugs·semgrep·SchemaCrawler 는 규칙 제목·id·cwe 만 비교표에 옮긴다. 코드·정규식 본문을 옮기지 않는다(R3 가 새로 쓴다).
9. **Juliet 받기는 `ZIP` 블록**을 `corpus-fetch.sh` 에 더한다(curl + unzip, 선택 표본, 이름을 골라야 받는다). 버린 대안 — MANUAL 처럼 손으로 받기(재현 안 됨), 별도 스크립트(레시피가 흩어짐).
10. **0-73 실브라우저는 조건부** — Claude in Chrome MCP 도구(`mcp__claude-in-chrome__*`)가 세션에 없으면 건너뛰고 이력에 「건너뜀(도구 없음)」. 있으면 30분 상한, 결과는 compare 문서 §8.
11. 조사 행 접두 **`R-n`** 을 새로 쓴다(분할표 머리 설명 한 줄). 결과물이 `design/` 글이라 레인 지문이 안 바뀐다 — 커밋 전 verify 는 S0 도장이 그대로 유효하다. 손으로 `bash scripts/doc-lint.sh design/compare-2026-10-28.md` 는 돈다.

## 4. 실행 스텝

### S0 — 설계 문서·행·번들 표
- 대상: `design/41-bundle57.md`(신설 — 이 플랜 파일 본문 그대로), `PLAN.md`, `PROGRESS.md`
- 변경:
  - `git checkout -b work/2026-10-28` (main 에서)
  - 이 플랜을 `design/41-bundle57.md` 로 복사(제목부터 §8 까지)
  - `PLAN.md` 4장 219줄(`JSqlParser 테스트 스위트는…`) **앞**에 문단 하나:
    `설계 41 이 더하는 표본(R1, 2026-10-28 조사 — 사용자 「표본은 Gitee 계열 + OWASP Benchmark·Juliet」) — 레거시 넷 \`jeecg\`·\`jeesite\`·\`zheng\`·\`ruoyi-classic\`(Spring MVC + MyBatis + JSP·Thymeleaf 전형, JSP +624 · Java +1,553 · 매퍼 +104, Apache-2.0·MIT) → 코드 검사·프로그램 분석의 A 후보(\`legacy-a\`) · \`benchmark\`(OWASP Benchmark Java 1.2, GPL-2.0 — 저장소 밖만, 정답 CSV 2,740) → 보안 36 정밀도·재현율(V-36) · \`sqlfluff-dialects\`(MIT, SQL 1,232 + 파스 트리 yml) → \`SqlTables\`·\`DdlReader\` 표 추출 차등(V-37) · \`juliet\`(선택, NIST Juliet Java 1.3 zip 의 매핑된 CWE 16 폴더, MANIFEST 밖) → 메서드 영역 채점. 고정 커밋·라이선스는 \`corpus/SOURCES.md\`. 새 표본은 \`corpus-common.js\` SKIP 에 둔다 — R3 가 A 로 승격할 때 뺀다.`
  - `PLAN.md` 분할표(16장) 머리 546줄 뒤에 한 줄: `\`R-n\` = 조사 행(설계 41) — 결과물은 \`design/compare-2026-10-28.md\` 의 절 하나. 코드·레인 지문이 안 바뀐다.`
  - 분할표 끝(1008줄 `0-72` 행 뒤)에 행 추가 — 열 `| # | 청크 | 설계 | 선행 | 상태 |`, 둘째 칸 머리는 `**설계 41 §4 가 원본**(\`design/41-bundle57.md\`)`, 상태 `설계 끝 — 번들 57`:
    - `V-35` 표본 — 레거시 Java·JSP 넷 + `LegacyCorpusTest`
    - `V-36` 표본 — OWASP Benchmark(MANIFEST)·Juliet(선택) + 보안 36 채점
    - `V-37` 표본 — sqlfluff 방언 픽스처 + `SqlTables`·`DdlReader` 차등
    - `R-1` 비교 ① 코드 검사 Java — p3c·Sonar
    - `R-2` 비교 ② 보안 — find-sec-bugs·semgrep·Benchmark 결과·KISA `ref` 후보
    - `R-3` 비교 ③ SQL — sqlfluff 규칙·sqlcheck·SchemaCrawler lint·V-37 결과
    - `R-4` 비교 ④ 코드 정리 — OpenRewrite
    - `R-5` 비교 ⑤ 소스 생성기 — RuoYi 생성기 둘
    - `R-6` 비교 ⑥ JSP 포매터·개발 도구 — js-beautify·CyberChef
    - `R-7` 종합 — 후보 행 제안·드러난 것
    - `0-73` 실브라우저 확인(Claude in Chrome, 조건부) — 선행 `V-35`
  - 번들 표(15장) 539줄 뒤에 `| 번들 57 | \`S0\`→\`V-35a\`→\`V-35b\`→\`V-36a\`→\`V-36b\`→\`V-37\`→\`R-1\`→…→\`R-7\`→\`0-73\` | 2026-10-28(Fable, 설계 41 — \`design/41-bundle57.md\`). **철저화 R1** — 표본 여섯·채점 하네스·비교표 일곱 축. 번들 하나·PR 하나. 머지 위임 있음 | 번들 56 머지(#97) | 설계 끝 |`. 번들 56 행 상태를 `완료 — PR #97 머지 \`9e1024c\`` 로 고친다(아직 「PR 대기」다).
  - `PROGRESS.md` 「현재 상태」 열려 있는 것에 `번들 57(설계 41) 실행 중 — work/2026-10-28` 추가, 이력 끝에 `| 2026-10-28 | S0 설계 41 — 번들 57 | 설계 문서·행 11·번들 표. 사용자 결정 넷(표본 넷·Benchmark MANIFEST/Juliet 선택·KISA PDF 사람·머지 위임) | (이 커밋) |`
- 검증: `grep -c "설계 끝 — 번들 57" PLAN.md` → `11` · `bash scripts/verify.sh` 초록(docs 레인) · 커밋 `docs: S0 설계 41 — 번들 57 행·번들 표`
- 의존: 없음

### V-35a — 표본 레거시 넷 받기
- 대상: `scripts/corpus-fetch.sh` `SOURCES`, `scripts/puppeteer/corpus-common.js` `SKIP`, `corpus/SOURCES.md`, `corpus/MANIFEST`
- 변경:
  - `SOURCES` 42줄(`ruoyi|…`) 뒤에 넷:
    ```bash
      "jeecg|zhangdaiscott/jeecg|2608008efc47594c8920d4de74f3b3d475c348c2|src/main/webapp/**/*.jsp src/main/java/**/*.java **/*.ftl src/main/resources/**/*.xml docs/*.sql"
      "jeesite|thinkgem/jeesite|v1.2.7|src/main/webapp/**/*.jsp src/main/java/**/*.java src/main/resources/mappings/**/*.xml db/**/*.sql"
      "zheng|shuzheng/zheng|7005c0a775e6d014d1dc8a8a809f7b1c13bf785a|**/src/main/webapp/**/*.jsp **/src/main/java/**/*.java **/src/main/**/*Mapper.xml project-datamodel/*.sql"
      "ruoyi-classic|yangzongzhuan/RuoYi|9cd770d2ac839999d6a425f70f3bd2cf3a0ee5cd|**/src/main/java/**/*.java **/src/main/resources/mapper/**/*.xml **/src/main/resources/templates/**/*.html **/src/main/resources/vm/**/*.vm sql/*.sql"
    ```
  - `corpus-common.js:7` SKIP 에 `'jeecg', 'jeesite', 'zheng', 'ruoyi-classic', 'benchmark', 'juliet', 'sqlfluff-dialects'` 를 더하고 윗줄 주석에 `// 설계 41 R1 표본 — 등급 A 승격(R3) 전까지 노드 스크립트가 안 걷는다` 한 줄
  - `bash scripts/corpus-fetch.sh jeecg jeesite zheng ruoyi-classic` → MANIFEST 가 다시 써진다. `git diff corpus/MANIFEST` 가 **추가 4줄만**인지 확인(기존 23줄이 사라졌으면 로컬 표본이 빠진 것 — 멈춘다)
  - `corpus/SOURCES.md` 34줄(`ruoyi-bin(선택)`) 뒤에 행 넷 — 열 그대로, 쓰는 행 `V-35 · R-1 · R-5`(ruoyi-classic). 잘라 온 것 칸에 실제 파일 수(fetch 출력 `<이름>: N 파일`)
- 검증: `grep -c . corpus/MANIFEST` → `28`(머리 1 + 27) · `ls C:/workspace/toolbox-corpus/jeecg/src/main/webapp | head -3` 폴더 있음 · `bash scripts/mvn.sh -q -B test -Dgroups='corpus & !db' -Dtest=CorpusFilesTest -Dsurefire.failIfNoSpecifiedTests=false` 초록(지문 일치) · `bash scripts/mvn.sh -q -B test -Dgroups='corpus & !db' -Dtest=JspFmtCorpusTest -Dsurefire.failIfNoSpecifiedTests=false` 초록(SKIP 이 먹는다)
- 의존: S0

### V-35b — `LegacyCorpusTest`
- 대상: `src/test/java/kr/ejg/toolbox/core/check/LegacyCorpusTest.java`(신설), 골든 `src/test/resources/golden/corpus/check-{jeecg,jeesite,zheng,ruoyi-classic}.json`·`check-<이름>-hits.txt`·`analyze-<이름>.json`·`legacy-a.txt`
- 변경:
  ```java
  /** V-35(설계 41) — 레거시 표본 넷에 코드 검사·프로그램 분석을 돌려 숫자를 얼린다. R1 은 고치지 않는다 —
   *  parseError·예외·느림(파일 하나 5초 초과)은 등급 A 후보 목록 {@code legacy-a} 로만 남기고 R3 가 승격한다. 발췌는 안 싣는다 */
  @Tag("corpus")
  class LegacyCorpusTest {
      static final List<String> SOURCES = List.of("jeecg", "jeesite", "zheng", "ruoyi-classic");
      @TempDir Path tmp;

      @Test
      void checkAndAnalyze() throws Exception {
          CorpusFiles.verify();
          LocalFiles files = new LocalFiles(tmp.resolve("data"));
          RuleSet rules = CheckCorpusTest.rules(false);               // spring 스택
          List<String> aCandidates = new ArrayList<>();
          int total = 0;
          for (String s : SOURCES) {
              Path dir = CorpusFiles.root().resolve(s);
              // ① 코드 검사 — CheckCorpusTest:71-100 과 같은 집계, parseError 는 aCandidates 로
              CheckRunner.RunResult r;
              try { r = CheckRunner.run(dir.toString(), rules, files, null); }
              catch (RuntimeException e) { aCandidates.add(s + " 검사 예외 " + e.getClass().getSimpleName()); continue; }
              total += r.files();
              … counts/filesPerRule/hits → GoldenFiles.assertJson("corpus/check-" + s + ".json", golden) 에 "_files"·"_truncated" 키를 더 넣는다
              CorpusFiles.conformance("check-" + s + "-hits", new ArrayList<>(hits));
              // ② 프로그램 분석 — AnalyzeCorpusTest:122-129 와 같은 키 + unresolved 종류별 수
              AnalyzeRunner.Result a;
              try { a = AnalyzeRunner.run(dir.toString(), new LocalFiles(tmp.resolve("data-" + s)), null, null); }
              catch (RuntimeException e) { aCandidates.add(s + " 분석 예외 " + e.getClass().getSimpleName()); continue; }
              Map<String, Object> sum = … files·programs·withCrud·statements·tables·risks·reachedRisks·unresolved(종류 → 수, TreeMap)
              GoldenFiles.assertJson("corpus/analyze-" + s + ".json", sum);
          }
          CorpusFiles.conformance("legacy-a", aCandidates);
          assertTrue(total > 1500, "레거시 Java·JSP 가 너무 적다: " + total);
      }
  }
  ```
  `Unresolved` 는 `core/analyze/Unresolved.java:11` `record Unresolved(String kind, String file, int line, String detail)` — 종류 키는 `kind()`.
- 검증: 첫 실행 `bash scripts/mvn.sh -q -B test -Dgroups='corpus & !db' -Dtest=LegacyCorpusTest -Dgolden.update=true -Dsurefire.failIfNoSpecifiedTests=false` → 골든 13 파일 생성 · 둘째 실행(갱신 없이) 초록 · `wc -l src/test/resources/golden/corpus/legacy-a.txt` 를 이력에(A 후보 수) · 시험 시간 10분 이내(surefire 출력의 `Time elapsed`)
- 커밋 `test: V-35 레거시 표본 넷 + LegacyCorpusTest` (V-35a 와 한 커밋이어도 된다 — 파일 수가 넘으면 둘)
- 의존: V-35a

### V-36a — OWASP Benchmark 받기 + `SecurityBenchmarkCorpusTest`
- 대상: `scripts/corpus-fetch.sh` `SOURCES`, `corpus/SOURCES.md`·`MANIFEST`, `src/test/java/kr/ejg/toolbox/core/check/SecurityBenchmarkCorpusTest.java`(신설), 골든 `security-benchmark.json`·`security-benchmark-fp.txt`·`security-benchmark-fn.txt`
- 변경:
  - SOURCES 한 줄: `"benchmark|OWASP-Benchmark/BenchmarkJava|f40c618c5a709afce350ef06ed2899c85cf586e9|src/main/java/org/owasp/benchmark/testcode/*.java expectedresults-1.2.csv"` → `bash scripts/corpus-fetch.sh benchmark` → MANIFEST +1줄 확인. SOURCES.md 행 — 라이선스 칸 `GPL-2.0 — 저장소 밖 표본만, 골든은 파일 이름·건수(§3-8)`, 쓰는 행 `V-36 · R-2`
  - 시험:
    ```java
    @Tag("corpus")
    class SecurityBenchmarkCorpusTest {
        /** Benchmark 범주 → 우리 규칙(설계 41 §3-4 초기값). 비어 있으면 미탐 범주 */
        static final Map<String, List<String>> MAP = new LinkedHashMap<>();
        static { MAP.put("cmdi", List.of("security.osCommand")); MAP.put("sqli", List.of("security.sqlConcat"));
                 MAP.put("pathtraver", List.of("security.pathTraversal")); MAP.put("crypto", List.of("security.weakCipher"));
                 MAP.put("hash", List.of("security.weakHash")); MAP.put("weakrand", List.of("security.weakRandom"));
                 for (String c : List.of("xss", "ldapi", "xpathi", "trustbound", "securecookie")) MAP.put(c, List.of()); }

        @Test void score() throws Exception {
            CorpusFiles.verify();
            Path root = CorpusFiles.root().resolve("benchmark");
            CheckRunner.RunResult r = CheckRunner.run(root.resolve("src/main/java/org/owasp/benchmark/testcode").toString(),
                    CheckCorpusTest.rules(false), new LocalFiles(tmp.resolve("data")), null);
            List<String> a = new ArrayList<>();                      // parseError·예외 → 등급 A(여기는 0 이어야 한다 — 표본이 컴파일되는 코드)
            Map<String, Set<String>> rulesByTest = …;                 // "BenchmarkTest00001" → {security.osCommand, …}  (f.file() 의 파일 이름에서 .java 뗀 것, group 이 security 인 것만)
            // CSV: '#' 줄 건너뜀, 열 name,category,real,cwe
            for (String line : Files.readAllLines(root.resolve("expectedresults-1.2.csv"))) { … }
            // 범주마다 tp/fp/fn/tn, otherRules(매핑 밖 보안 규칙 지적 수); precisionPct = tp*100/(tp+fp) (분모 0 이면 -1), recallPct = tp*100/(tp+fn)
            GoldenFiles.assertJson("corpus/security-benchmark.json", perCategory /* LinkedHashMap 범주 → {rules, tests, tp, fp, fn, tn, precisionPct, recallPct, otherRules} + "_total" */);
            CorpusFiles.conformance("security-benchmark-fp", fpTests);   // "cmdi BenchmarkTest00012" 정렬
            CorpusFiles.conformance("security-benchmark-fn", fnTests);
            CorpusFiles.none("Benchmark 코드 검사", a, r.files());
            assertEquals(2740, csvRows, "정답 CSV 행 수");
        }
    }
    ```
- 검증: `-Dtest=SecurityBenchmarkCorpusTest -Dgolden.update=true` → 골든 셋 생성 → 갱신 없이 초록 · json 의 범주별 `precisionPct·recallPct` 를 이력에 표로(11 범주) · 시간 10분 이내
- 커밋 `test: V-36a OWASP Benchmark 표본 + 보안 36 채점`
- 의존: V-35a(SKIP 에 benchmark 가 이미 들어 있다)

### V-36b — Juliet(선택) — `ZIP` 블록 + `JulietCorpusTest`
- 대상: `scripts/corpus-fetch.sh`(ZIP 블록 + 루프), `corpus/SOURCES.md`(선택 행), `src/test/java/kr/ejg/toolbox/core/check/JulietCorpusTest.java`(신설), 골든 `security-juliet.json`·`security-juliet-fp.txt`
- 변경:
  - `corpus-fetch.sh` 52줄(`BUILD=`) 뒤:
    ```bash
    # zip 으로 받는 선택 출처(설계 41) — 이름|URL|zip 안 접두(이 아래만)|폴더 글롭(공백 구분). MANIFEST 밖(없는 PC 에서 다른 표본 시험을 막지 않게). 이름을 골라야만 받는다
    ZIP=("juliet|https://samate.nist.gov/SARD/downloads/test-suites/2017-10-01-juliet-test-suite-for-java-v1-3.zip|src/testcases|CWE78_* CWE89_* CWE23_* CWE36_* CWE327_* CWE328_* CWE330_* CWE338_* CWE601_* CWE113_* CWE259_* CWE321_* CWE209_* CWE80_* CWE81_* CWE83_*")
    ```
    BUILD 루프(132줄) 뒤에 루프:
    ```bash
    for z in "${ZIP[@]}"; do
      IFS='|' read -r name url prefix globs <<< "$z"
      [[ " ${want[*]} " == *" $name "* ]] || continue
      zip="$CACHE/$name.zip"
      [ -f "$zip" ] || { echo "  받기 $name ← $url"; curl -fsSL -o "$zip" "$url" || { echo "  [실패] $name — zip 을 못 받았다" >&2; exit 1; }; }
      rm -rf "${CORPUS:?}/$name"; mkdir -p "$CORPUS/$name"
      top=$(unzip -Z1 "$zip" | head -1 | cut -d/ -f1)        # zip 안 최상위 폴더(§8 ①)
      for g in $globs; do unzip -q -o "$zip" "$top/$prefix/$g/*.java" -d "$CACHE/$name-x" 2>/dev/null || true; done
      cp -r "$CACHE/$name-x/$top/$prefix"/. "$CORPUS/$name/"
      echo "  $name: $(find "$CORPUS/$name" -name '*.java' | wc -l) 파일"
    done
    ```
    주의 — 맨 위 `set -f` 가 글롭을 막으므로 `for g in $globs` 는 글자 그대로 돈다(의도). `unzip` 의 패턴은 unzip 이 푼다.
  - `bash scripts/corpus-fetch.sh juliet` → `ls C:/workspace/toolbox-corpus/juliet` 에 CWE 폴더 16 개(없는 CWE 는 §8 ① 에 따라 글롭 수정)
  - SOURCES.md 행 `juliet(선택)` — 라이선스 `공개(NIST SARD)`, 잘라 온 것 「**MANIFEST 밖**, `corpus-fetch.sh juliet`, CWE 16 폴더 — 설계 41 §3-4 매핑」
  - 시험: `assumeTrue(Files.isDirectory(CorpusFiles.root().resolve("juliet")), "juliet 없음 — bash scripts/corpus-fetch.sh juliet")`. CWE 폴더마다 `CheckRunner.run(folder…)` → 지적을 파일별로 모은다. 파일 영역 판정:
    ```java
    static final Pattern METHOD = Pattern.compile("^\\s*(?:public|private|protected)\\s+(?:static\\s+)?\\S+\\s+(bad|good\\w*)\\s*\\(");
    // 파일 이름에 "_bad" → 전체 bad, "_good" → 전체 good, 아니면 줄을 훑어 METHOD 매치마다 영역 시작(다음 매치 전까지)
    ```
    TP = bad 영역에 매핑 규칙 지적 ≥1 인 파일(또는 `_bad` 파일) · FN = 그 반대 · FP = good 영역(또는 `_good*` 파일)에 매핑 규칙 지적이 있는 파일. CWE 폴더 → 규칙은 §3-4(폴더 이름 앞 `CWE<n>_` 로 맞춘다). json `security-juliet.json` CWE → `{rules, files, tp, fp, fn, precisionPct, recallPct}` + `_total`; `conformance("security-juliet-fp", "CWE89 <파일 상대경로>" 정렬)`; parseError·예외 → `none`.
    파일 읽기는 `Files.readAllLines(p, StandardCharsets.UTF_8)` 실패 시 ISO_8859_1 로 되읽는다(Juliet 은 ASCII).
- 검증: `-Dtest=JulietCorpusTest -Dgolden.update=true` → 갱신 없이 초록 · `Time elapsed` 를 이력에. **10분을 넘으면** 실패 사다리: ZIP 글롭을 변형 `_01`~`_17` 만(`CWE78_*/*_0?.java CWE78_*/*_1[0-7].java`… 꼴로 글롭 칸을 바꾸고 unzip 패턴도 `$g` 그대로 쓴다) → 다시 받고 재측정. 그래도면 CWE 를 78·89·23·36·327·328·330·338 여덟으로 줄인다. 어느 사다리를 탔는지 이력에
- 커밋 `test: V-36b Juliet 선택 표본(ZIP 받기) + 메서드 영역 채점`
- 의존: V-36a

### V-37 — sqlfluff 방언 픽스처 + `SqlFixtureCorpusTest`
- 대상: `scripts/corpus-fetch.sh` `SOURCES`, `corpus/SOURCES.md`·`MANIFEST`, `src/test/java/kr/ejg/toolbox/core/analyze/SqlFixtureCorpusTest.java`(신설), 골든 `sqlfixture.json`·`sqltables-diff.txt`
- 변경:
  - SOURCES 한 줄: `"sqlfluff-dialects|sqlfluff/sqlfluff|4.4.0|test/fixtures/dialects/ansi/* test/fixtures/dialects/oracle/* test/fixtures/dialects/mysql/* test/fixtures/dialects/mariadb/* test/fixtures/dialects/postgres/* test/fixtures/dialects/tsql/* test/fixtures/dialects/sqlite/* test/fixtures/dialects/db2/* test/fixtures/rules/std_rule_cases/*.yml"` → `bash scripts/corpus-fetch.sh sqlfluff-dialects` → MANIFEST +1줄. SOURCES.md 행(MIT, 쓰는 행 `V-37 · R-3`)
  - 시험(`core/analyze` — `SqlTables`·`SqlJoins` 가 이 패키지, `DdlReader` 는 `core.gen` import):
    ```java
    @Tag("corpus")
    class SqlFixtureCorpusTest {
        static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());   // jackson-dataformat-yaml — pom:72 에 이미 있다
        static final Set<String> DML = Set.of("select_statement", "insert_statement", "update_statement", "delete_statement", "merge_statement");

        @Test void tablesMatchParseTree() throws Exception {
            CorpusFiles.verify();
            List<String> a = new ArrayList<>(), diff = new ArrayList<>();
            int files = 0, dml = 0, ddl = 0, skipped = 0, equal = 0;
            for (Path sql : CorpusFiles.files("sqlfluff-dialects", "test/fixtures/dialects/*/*.sql")) {
                Path yml = sql.resolveSibling(sql.getFileName().toString().replaceFirst("\\.sql$", ".yml"));
                if (!Files.exists(yml)) continue;
                files++;
                String rel = CorpusFiles.rel(sql), text = Files.readString(sql, StandardCharsets.UTF_8);
                JsonNode tree = YAML.readTree(yml.toFile()).path("file");
                Set<String> stmtKinds = … // tree 아래 "statement" 노드의 첫 필드 이름들(batch 배열·단일 둘 다)
                long t0 = System.nanoTime();
                try {
                    Set<String> expected, ours;
                    if (!stmtKinds.isEmpty() && DML.containsAll(stmtKinds)) {
                        dml++; expected = tableRefs(tree); ours = SqlTables.extract(text, rel).refs().stream().map(r -> r.table().toUpperCase(Locale.ROOT)).collect(toCollection(TreeSet::new));
                        SqlJoins.extract(text);                       // 예외만 본다
                    } else if (stmtKinds.contains("create_table_statement")) {
                        ddl++; expected = createdTables(tree); ours = DdlReader.read(text).tables().stream().map(t -> t.name().toUpperCase(Locale.ROOT)).collect(toCollection(TreeSet::new));
                    } else { skipped++; continue; }
                    if (expected.equals(ours)) equal++; else diff.add(rel + " 우리만=" + minus(ours, expected) + " 그쪽만=" + minus(expected, ours));
                } catch (RuntimeException e) { a.add(rel + " 예외 " + e.getClass().getSimpleName()); }
                long ms = (System.nanoTime() - t0) / 1_000_000;
                if (ms > 5000) a.add(rel + " 느림 " + ms + "ms");
            }
            GoldenFiles.assertJson("corpus/sqlfixture.json", Map.of(…files, dml, ddl, skipped, equal, diff=diff.size(), a=a.size()) /* LinkedHashMap */);
            CorpusFiles.conformance("sqltables-diff", diff);
            CorpusFiles.conformance("sqlfixture-a", a);              // 설계 41 §3-1 — R1 은 얼린다, R3 가 none 으로 승격
            assertTrue(files > 1000, "픽스처가 너무 적다: " + files);
        }
        /** 트리의 모든 table_reference 아래 식별자 잎(naked_identifier·quoted_identifier) 중 마지막 — 스키마 뺀 표 이름. 따옴표·백틱·대괄호 벗기고 대문자 */
        static Set<String> tableRefs(JsonNode tree) { … 재귀 … }
        /** create_table_statement 아래 첫 table_reference 만 */
        static Set<String> createdTables(JsonNode tree) { … }
    }
    ```
    `core.meta.Table` 은 `record Table(String schema, String name, String type, …)`(`Table.java:12`) — 이름은 `name()`.
- 검증: `-Dtest=SqlFixtureCorpusTest -Dgolden.update=true` → 갱신 없이 초록 · json 의 `equal/dml+ddl` 비율과 `diff`·`a` 수를 이력에 · `head -20 golden/corpus/sqltables-diff.txt` 에서 어긋남의 **종류**(CTE·서브쿼리 별칭·스키마 접두·대소문자…) 세 줄 요약을 이력에(R-3 입력)
- 커밋 `test: V-37 sqlfluff 방언 픽스처 + SqlTables·DdlReader 차등`
- 의존: V-35a

### R-1 — 비교 ① 코드 검사 Java — p3c · Sonar
- 대상: `design/compare-2026-10-28.md`(신설), 저장소 밖 `C:/workspace/toolbox-ref/{p3c,sonar-java}`
- 변경:
  - `mkdir -p C:/workspace/toolbox-ref && git -C C:/workspace/toolbox-ref clone -q --depth 1 https://github.com/alibaba/p3c && git -C C:/workspace/toolbox-ref clone -q --depth 1 https://github.com/SonarSource/sonar-java`
  - 문서 머리(2026-10-14 꼴을 따른다, 1~10줄): 제목 `# 참고 도구 비교 — 철저화 R1 (2026-10-28)`, 목적 한 줄(설계 41), 열 설명 `| # | 그쪽이 하는 것 | 우리 현재 | 판정 | 차이 | 가치 | 오탐 위험 | 재는 표본 |`, 가치·판정 기준은 2026-10-14 와 같게, 오탐 위험 `상: 정규식으로 못 가름 / 중: 파서 필요 / 하: 글자 매치로 충분`, 재는 표본 `어느 표본·골든으로 잴 수 있나(없으면 ×)`. 그 아래 **참고 저장소 표** `| 이름 | 저장소 | 커밋(git -C … rev-parse HEAD) | 라이선스 | 읽은 자리 |` — 축을 칠 때마다 행을 더한다.
  - `## ① 코드 검사 Java` — 외부 한 줄. **p3c**: `grep -h '<rule name=' p3c/p3c-pmd/src/main/resources/rulesets/java/*.xml | sed 's/.*name="\([^"]*\)".*/\1/'` 56 → 글은 `messages.xml` 의 `java.<묶음>.<Rule>.rule.msg`(영문 `.msg.en`? 없으면 중문 그대로 옮기지 않고 한 줄 요약) → 우리 java 13·common 10·metric 7·mybatis 6·file 4 와 하나씩 맞춘다. **Sonar**: `jq -r '[.tags|join(","), .type, .defaultSeverity, .title] | @tsv' sonar-java/sonar-java-plugin/src/main/resources/org/sonar/l10n/java/rules/java/S*.json` → 태그 빈도 표(상위 15) → 태그 `spring`·`sql`·`error-handling`·`unused`·`convention`·`cwe` 인 규칙 제목만 훑어 **참 25 이하**로 고른다. 「우리 현재」 칸은 `rules.yaml:<줄>` id.
  - `**버린 것**` — Sonar 규칙 본문·구현(LGPL), p3c 의 PMD 전용 XPath, 동시성(우리 범위 밖)
  - 레거시 넷의 `check-<이름>.json` 상위 규칙 10 을 절 끝에 한 표로(「우리 규칙이 레거시에서 실제로 무엇을 가장 많이 잡나」)
- 검증: `grep -c '^## ' design/compare-2026-10-28.md` → `1` · 표 행(`^| 가-`) 수 ≤ 25 · `bash scripts/doc-lint.sh design/compare-2026-10-28.md` 출력 없음(존댓말 0) · 커밋 `docs: R-1 비교 ① 코드 검사 Java — p3c·Sonar`
- 의존: V-35b(표 끝 레거시 수치)

### R-2 — 비교 ② 보안 — find-sec-bugs · semgrep · Benchmark 결과 · KISA `ref` 후보
- 대상: `design/compare-2026-10-28.md` `## ② 보안`, `C:/workspace/toolbox-ref/{find-sec-bugs,semgrep-rules}`, `C:/workspace/toolbox-ref/kisa/*.pdf`(있으면)
- 변경:
  - clone 둘(`semgrep-rules` 는 기본 가지 `develop`). find-sec-bugs: `grep -o 'type="[A-Z_]*"' findsecbugs-plugin/src/main/resources/metadata/findbugs.xml | sort -u`(BugPattern) + `messages.xml` 의 `<ShortDescription>` → 패턴 이름·설명. semgrep: `grep -h '^  - id:\|^    message:\|cwe:' java/lang/security/**/*.yaml`(시험 `.test.yaml` 제외) → id·cwe.
  - 표 **둘**: (a) 우리 보안 36 행 × 열 `| id | find-sec-bugs 짝 | semgrep 짝 | Benchmark 범주·재현율·정밀도(V-36a json) | Juliet CWE(V-36b, 있으면) | KISA 항목 후보 |` — 우리 규칙마다 「그쪽은 어떻게 잡나(패턴 요지 한 줄)」 · (b) **미탐 범주** 표(열은 공통 8열) — Benchmark 매핑 없음 5(xss Java·ldapi·xpathi·trustbound·securecookie)와 find-sec-bugs·semgrep 에 있고 우리에 없는 것 중 eGov·Spring MVC 코드에 나올 법한 것 **참 20 이하**(예 — XXE·LDAP 삽입·XPath 삽입·신뢰 경계·HTTP 파라미터 오염·세션 고정·CSRF 토큰 없음·안전하지 않은 역직렬화 라이브러리…는 **실측으로 고른다**, 이 예시를 그대로 옮기지 않는다)
  - KISA PDF: `ls C:/workspace/toolbox-ref/kisa/*.pdf` 있으면 Read(`pages`)로 목차 → 구현단계 보안약점 항목 이름·번호 목록을 절 안 표로, (a) 표의 KISA 칸을 **후보**로 채운다(확정은 사람 몫 — `rules.yaml` 은 안 고친다). 없으면 KISA 칸을 `—` 로 두고 절 머리에 「PDF 없음 — 사람 몫 ⑪」
  - 버린 것 — 바이트코드 전용 패턴(우리는 소스 정규식), Android·AWS·JWT 라이브러리 전용 semgrep 규칙
- 검증: `grep -c '^## ' …` → `2` · (a) 표 36 행 · 커밋 `docs: R-2 비교 ② 보안 — find-sec-bugs·semgrep·Benchmark·KISA 후보`
- 의존: V-36a(V-36b 는 있으면)

### R-3 — 비교 ③ SQL — sqlfluff 규칙 · sqlcheck · SchemaCrawler lint · V-37 결과
- 대상: `design/compare-2026-10-28.md` `## ③ SQL`, `C:/workspace/toolbox-ref/{sqlfluff,SchemaCrawler}`, `C:/workspace/toolbox-ref/sqlcheck-README.md`
- 변경:
  - `git clone -q --depth 1 --branch 4.4.0 https://github.com/sqlfluff/sqlfluff` · `git clone -q --depth 1 https://github.com/schemacrawler/SchemaCrawler` · `curl -fsSL -o sqlcheck-README.md https://raw.githubusercontent.com/jarulraj/sqlcheck/master/README.md`
  - sqlfluff: `grep -rh -A1 '^class Rule_' sqlfluff/src/sqlfluff/rules/*/*.py | grep -v '^class\|^--'`(docstring 첫 줄) → 79 코드·요지 → 묶음별(aliasing·ambiguous·capitalisation·convention·layout·references·structure·방언) 수와 **우리 산출물 SQL·스니펫·DB 탐색 화면에 뜻이 있는 것**(참조·구조·관례 묶음 위주, layout·capitalisation 은 수만) ≤ 15 행. `std_rule_cases/*.yml` 의 `fail_str` 가 곧 양성 픽스처 재료임을 적는다(규칙 코드 → yml 파일 이름)
  - sqlcheck 29 → 우리 `StructureLint` 9·`QualitySql` 8·`Audit` 5·mybatis 6 과 맞춤 — 논리·물리 설계 반패턴은 스냅샷(메타)으로 잴 수 있는 것만 참, 질의 반패턴은 매퍼 XML 에서 정규식으로 잴 수 있는 것만 참
  - SchemaCrawler `LinterProvider*.java` 22 이름 → `StructureLint` 9 와 맞춤(Eclipse/GPL/LGPL 삼중 — 이름만)
  - V-37 결과 — `sqlfixture.json` 수치 + `sqltables-diff.txt` 어긋남 종류 표(종류 · 건수 · 예 파일 하나 · 우리 쪽 원인 추정 `SqlTables.java:<줄>`)
  - 버린 것 — Jinja·dbt 규칙, 레이아웃 규칙 전부(우리는 포매터가 아니다), BigQuery·Snowflake 전용
- 검증: `grep -c '^## ' …` → `3` · 커밋 `docs: R-3 비교 ③ SQL — sqlfluff·sqlcheck·SchemaCrawler·차등 결과`
- 의존: V-37

### R-4 — 비교 ④ 코드 정리 — OpenRewrite
- 대상: `design/compare-2026-10-28.md` `## ④ 코드 정리`, `C:/workspace/toolbox-ref/rewrite-static-analysis`
- 변경:
  - clone → `grep -rh -A1 'getDisplayName' src/main/java/org/openrewrite/staticanalysis/*.java | grep 'return "' | sed 's/.*return "\(.*\)";/\1/' | sort` 201 → 우리 `Cleanup.RULES` 9 와 이름으로 맞춤(이미 있음 = 거짓) → 나머지 중 **우리 105 규칙에 짝이 있는 것**(규칙 id 가 있고 `fix:` 글이 기계적인 것 — 그것이 곧 B 후보)과 **우리 규칙도 없는 것** 둘로 나눈다. 참 ≤ 25. 열 「재는 표본」에는 `CleanupCorpusTest`(표본 Java 5,207) 와 `CleanupCompileCorpusTest`(commons 셋 컴파일) 가능 여부
  - B 목록 복원 — 저장소에 없다(§2). 우리 105 규칙을 훑어 `fix:` 가 「지워라·바꿔라」 꼴인 것을 표로 뽑고(id · fix 글 · 자동 가능 여부 ○△× · 왜), 메모리의 「B 18」과 수가 맞는지 적는다. 이 표가 R3 의 코드 정리 설계 입력
  - 버린 것 — 프레임워크 마이그레이션 레시피(Spring Boot 2→3 등), 포매팅 레시피(우리는 포매터가 따로 있다), Kotlin·Groovy
- 검증: `grep -c '^## ' …` → `4` · 커밋 `docs: R-4 비교 ④ 코드 정리 — OpenRewrite·B 후보 복원`
- 의존: R-1(문서 있음)

### R-5 — 비교 ⑤ 소스 생성기 — RuoYi 생성기 둘
- 대상: `design/compare-2026-10-28.md` `## ⑤ 소스 생성기`
- 변경:
  - 입력은 표본 안 — `C:/workspace/toolbox-corpus/ruoyi-classic/ruoyi-generator/src/main/resources/vm/**`(14) 와 캐시 클론 `C:/workspace/.corpus-src/ruoyi@13db1fcef36bee9ce45d2d636a1d4e8f5ed5bbc3/ruoyi-generator/src/main/resources/vm/**`(RuoYi-Vue 판 — `vm/vue`·`vm/js` 포함). 생성기 자바 `ruoyi-generator/src/main/java/**/GenTable*.java`·`VelocityUtils.java` 로 옵션(`tplCategory` crud·tree·sub, 사전(dict)·쿼리 타입·HTML 타입)을 읽는다
  - 우리 — `src/main/resources/tools/spring_source_generator.html`·`spring_source_generator_ext.js`·`core/gen/*`(`DtoGenerator`·`GeneratorCorpusTest`·`GeneratorJavacTest`) 가 내는 산출물 종류와 옵션을 표로 먼저 적고(파일 경로 근거), 그쪽 산출물 종류(controller·service·serviceImpl·mapper·domain·sub-domain·mapper.xml·sql 메뉴·html add/edit/list/tree/view·vue·js)와 옵션(tree·sub 표·dict·검색 종류)을 맞춘다. 참 ≤ 15
  - 버린 것 — Velocity 템플릿 엔진 도입(바닐라 JS·새 의존 0), Vue 산출물(백엔드 반입 도구 범위 밖), JHipster(Node·JDL — 스택이 멀다)
- 검증: `grep -c '^## ' …` → `5` · 커밋 `docs: R-5 비교 ⑤ 소스 생성기 — RuoYi 생성기 둘`
- 의존: V-35a(ruoyi-classic 표본)

### R-6 — 비교 ⑥ JSP 포매터 · 개발 도구 — js-beautify · CyberChef
- 대상: `design/compare-2026-10-28.md` `## ⑥ JSP 포매터 · 개발 도구`, `C:/workspace/toolbox-ref/{js-beautify,CyberChef}`
- 변경:
  - clone 둘(CyberChef 는 크다 — `--depth 1 --filter=blob:none` 뒤 `git sparse-checkout set src/core/config` 로 `Categories.json` 만). js-beautify `js/src/html/options.js` 의 옵션 이름 전부 → 우리 `jsp_formatter.html`·`jsp_formatter_ext.js`·순수본 옵션(화면에서 고를 수 있는 것)과 맞춘다. CyberChef `Categories.json` 의 범주·연산 이름 → 우리 `dev_tools.html` 탭 16 과 맞춰 **SI 현장에서 쓸 법한 것**(인코딩·해시·시각·UUID·정규식 시험·diff·JWT·바이트) 참 ≤ 15
  - 버린 것 — 암호 연산 대부분(현장 요구 없음), 이미지·압축, 포매터의 CSS·Markdown 옵션
- 검증: `grep -c '^## ' …` → `6` · 커밋 `docs: R-6 비교 ⑥ JSP 포매터·개발 도구 — js-beautify·CyberChef`
- 의존: R-1

### R-7 — 종합 — 후보 행 제안 · 드러난 것
- 대상: `design/compare-2026-10-28.md` `## ⑦ 종합`, `## 후보 행 제안`, `## 드러난 것`
- 변경:
  - `## ⑦ 종합` — 표본 수치 한 표: 표본 · 파일 수 · 등급 A 후보 수(`legacy-a`·`sqlfixture-a`) · 채점(Benchmark 범주별 재현율·정밀도 `_total`, Juliet `_total`, sqlfluff `equal/(dml+ddl)`) · 시험 시간
  - `## 후보 행 제안` — 2026-10-14 와 같은 열 `| 후보 | 축 | 화면 | 바뀌는 파일(추정) | 가치 | 선행 행 |` + 열 하나 `| 재는 표본 |`. 축 표에서 **참 · 가치 상·중 · 재는 표본 있음**인 것만, 가치 순. 행 번호는 안 붙인다 — R3 가 붙인다. 레거시 A 후보 승격(노드 SKIP 해제 포함)과 Benchmark 미탐 범주를 **맨 위**에 둔다
  - `## 드러난 것` — 「`위치` — 사실 — 할 일 또는 사람 몫」. KISA PDF 없었으면 여기에. 비교 중 발견한 우리 규칙의 오탐·정의 어긋남은 여기에만(코드는 안 고친다)
- 검증: `grep -c '^## ' …` → `9` · `bash scripts/doc-lint.sh design/compare-2026-10-28.md` 출력 없음 · 커밋 `docs: R-7 종합 — 후보 행 제안·드러난 것`
- 의존: R-1~R-6

### 0-73 — 실브라우저 확인(조건부)
- 대상: `design/compare-2026-10-28.md` `## ⑧ 실브라우저`(절 하나 더 — R-7 뒤에 둔다)
- 변경:
  - 세션에 `mcp__claude-in-chrome__*` 도구가 **없으면** 이 스텝 전체를 건너뛰고 이력에 `0-73 건너뜀(Claude in Chrome 도구 없음)` — 사람 몫 ① 은 그대로 남는다
  - 있으면: `TOOLBOX_NO_PAUSE=1 cmd //c run.bat --port 41780`(이미 떠 있으면 재사용 — `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:41780/` → 302) → `claude-in-chrome` 스킬을 읽고 새 탭으로 `http://127.0.0.1:41780/` → 화면 넷만, 30분 상한: ① JSP 포매터 「폴더」 탭에 `C:/workspace/toolbox-corpus/jeesite/src/main/webapp` 검사 → 덮어쓰기 확인창 **취소**(원본 수정 금지 — 절대 규칙 7) ② 코드 검사에 `C:/workspace/toolbox-corpus/zheng` → 결과 표·xlsx 내려받기 버튼이 뜨는지(내려받은 파일은 열지 않는다) ③ 프로그램 분석에 `C:/workspace/toolbox-corpus/egov-msa` → CRUD 탭 엔티티 표 ④ 개발 도구 탭 셋 클릭. 콘솔 오류는 `read console` 로 모은다
  - 결과 — 화면 · 눌러 본 것 · 보인 것 · 콘솔 오류 수 · 사람 눈으로 다른 점 — 표 하나. 스크린샷은 저장소에 넣지 않는다
- 검증: 절이 있거나 이력에 건너뜀 한 줄 · 커밋 `docs: 0-73 실브라우저 확인`(건너뛰면 커밋 없음)
- 의존: V-35a

### 마무리
- CLAUDE.md 「마무리」 1~8 그대로. 독립 리뷰는 `Agent(subagent_type: "caveman:cavecrew-reviewer")` 하나 — 지적은 버그만 고친다(나머지는 compare 「드러난 것」으로)
- `bash scripts/gate-probe.sh` → `bash scripts/verify.sh` → **`bash scripts/verify.sh --corpus`**(새 표본 여섯·시험 넷이 든 레인 — push-guard 가 요구한다. 전체 시간을 이력에) → push → PR(본문에 청크마다 시작 세 줄 표 + 채점 요약 표) → CI 폴링 → **머지 위임 있음** → `gh pr merge N --merge --delete-branch` → `git checkout main && git pull`
- 끝 보고 — 계획 밖 결정 · 건너뛴 것(Juliet 사다리·0-73) · A 후보 수 · 채점 수치 · 사람이 할 것(KISA PDF 가 없었으면 그것, Benchmark·Juliet 라이선스는 저장소 밖이라 사람 확인 불요)

## 5. 금지 사항
- `pure/` · `src/main/**`(규칙·파서·화면·CLI) — **한 줄도 안 고친다.** 드러난 결함은 목록·문서로만
- 기존 corpus 시험(`CheckCorpusTest.GROUPS`·`CleanupCorpusTest`·`AnalyzeCorpusTest`·`DdlCorpusTest`·노드 셋)에 새 표본을 끼우지 않는다. `corpus-common.js` 는 SKIP 추가만
- 기존 골든(`golden/corpus/*` 중 이 번들이 새로 만드는 것 밖) 갱신 금지 — 바뀌면 멈춘다(§7)
- 새 Maven 의존성 0(`jackson-dataformat-yaml` 은 이미 있다). pip·npm 설치 0 — sqlfluff·semgrep 을 깔지 않는다
- 참고 저장소(`C:/workspace/toolbox-ref/**`)와 표본의 코드를 **실행·빌드·import 하지 않는다**. 읽기만. GPL·LGPL 본문(코드·정규식·설명 전문)을 저장소 파일에 옮기지 않는다 — 이름·id·cwe·한 줄 요약만
- `corpus-fetch.sh` 의 GitHub 호스트 고정(`:62`)은 안 바꾼다. 받기는 고정 커밋·태그만(가지 이름 금지)
- 비교표에 검증 인프라·훅·verify 속도 제안을 적지 않는다(사용자 2026-10-06). 사견·추측 수치 금지 — 수는 전부 명령 출력에서
- `verify.sh` 와 `git commit`, `verify.sh --corpus` 와 `git push` 는 따로 낸다(훅). 빨간 트리를 `work/*` 에 안 올린다
- 서브에이전트는 마무리 독립 리뷰 하나(+ 웹 전용 문서가 꼭 필요할 때 `web-fetch` sonnet 하나). 조사는 clone + grep + Read

## 6. 최종 검증
- `bash scripts/verify.sh` 초록 + `bash scripts/verify.sh --corpus` 초록(새 시험 넷 포함, Juliet 은 있으면). 각 새 시험 `Time elapsed` ≤ 10분
- `corpus/MANIFEST` 29줄(머리 1 + 28 출처) · `corpus/SOURCES.md` 에 새 행 7(juliet 선택 포함) · `git diff origin/main -- corpus/MANIFEST` 가 추가만
- `golden/corpus/` 새 파일: `check-{jeecg,jeesite,zheng,ruoyi-classic}.json`·`-hits.txt` 8 · `analyze-<4>.json` 4 · `legacy-a.txt` · `security-benchmark.json`·`-fp.txt`·`-fn.txt` · `security-juliet.json`·`-fp.txt`(있으면) · `sqlfixture.json`·`sqltables-diff.txt`·`sqlfixture-a.txt` — 그 밖 기존 골든 diff 0
- `design/compare-2026-10-28.md` — `## ` 절 9(+실브라우저 1) · 축마다 참 행 ≤ 25 · 참고 저장소 표에 커밋·라이선스 · doc-lint 0 · 「후보 행 제안」 표 있음
- PLAN 분할표 행 11 상태가 실제와 같다(완료 — 커밋은 이력 / 건너뜀) · 번들 57 행 · PROGRESS 이력 청크마다 한 줄(해시) · 「정한 것(계획 밖)」 전부 적힘
- PR 머지 · main 최신 · 가지 삭제

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- `corpus-fetch.sh` 실행 뒤 MANIFEST 에서 **기존 줄이 사라짐**(로컬 표본 누락) — 지문을 커밋하지 말고 멈춘다
- `CheckCorpusTest.rules`·`CheckRunner.run`·`AnalyzeRunner.run`·`SqlTables.extract`·`DdlReader.read`·`CorpusFiles.*`·`GoldenFiles.assertJson` 의 시그니처가 §2 와 다름
- 새 표본을 넣은 뒤 **기존** corpus 시험이 빨개짐(SKIP 누락·`verify()` 지문 실패 외의 이유) — 기존 골든을 갱신하지 말고 멈춘다
- 새 시험 하나가 사다리를 다 타고도 10분을 넘김
- Benchmark `expectedresults-1.2.csv` 행이 2,740 이 아님 · sqlfluff `.yml` 에 `table_reference` 키가 없거나 꼴이 방언마다 다름(§8 ⑤)
- 금지 사항(특히 `src/main` 수정·의존성)을 어기지 않고는 진행 불가
- 스텝에 없는 파일을 3개 이상 고쳐야 함
- 「정한 것(계획 밖)」이 행당 둘을 넘음 → `wip/<청크>` 로 빼고 다음 행(번들 모드 규칙)

## 8. 불확실 항목

**실행해야만 알 수 있는 것**(실행자가 그 스텝 첫머리에 확인한다)
1. **Juliet zip 안 경로** — 최상위 폴더 이름과 `src/testcases/CWE<n>_<이름>/` 꼴, 폴더 이름(예 `CWE78_OS_Command_Injection`·`CWE23_Relative_Path_Traversal`·`CWE327_Use_Broken_Crypto`). 확인: `unzip -Z1 C:/workspace/.corpus-src/juliet.zip | head -5` · `unzip -Z1 … | cut -d/ -f1-3 | sort -u | grep -E 'CWE(78|89|23|36|327|328|330|338|601|113|259|321|209|80|81|83)_'`. 없는 CWE 는 ZIP 글롭에서 빼고 §3-4 매핑·이력에 적는다
2. **새 시험 넷의 시간** — Benchmark 2,740 · 레거시 ~3,000 · Juliet 수천~만 · sqlfluff 1,232. 10분 상한. 사다리는 V-36b 에
3. **Claude in Chrome 도구 유무** — 0-73 첫머리에 도구 목록을 본다. 없으면 건너뜀
4. **KISA PDF 판독** — 파일은 있다(§맥락). 32MB 진단가이드가 Read(`pages`) 로 글이 읽히는지(스캔 이미지면 목차만) R-2 첫머리에 `pages: "1-10"` 로 본다. 안 읽히면 같은 판 hwpx 를 `unzip -p <hwpx> Contents/section0.xml | sed 's/<[^>]*>/ /g'` 로 글만 뽑는다
5. **sqlfluff yml 의 `table_reference` 꼴이 방언마다 같은가** — ansi `select.yml` 에서만 확인했다(`table_reference: naked_identifier: X`, 스키마가 있으면 `- naked_identifier … - dot … - naked_identifier`). V-37 첫머리에 `grep -l table_reference` 수와 `quoted_identifier`·`dot` 꼴을 oracle·tsql·mysql 각 1 파일에서 눈으로 본다. 다르면 `tableRefs()` 에 분기 — 그래도 안 맞으면 §7
6. **jeecg(2021, Java 6·7 문법)·zheng(2019)이 JavaParser 21→8 되읽기로 읽히는가** — 안 읽히는 파일이 곧 `legacy-a` 목록이다. 수가 파일의 10% 를 넘으면 R-7 「드러난 것」 맨 위에
7. **`gh`·`git clone` 네트워크** — 집 PC 전용 번들이다(반입 zip 과 무관). 끊기면 그 스텝에서 멈춘다

**사용자만 정할 수 있는 것** — 없음(2026-10-28 넷 다 답함: 표본 넷 · Benchmark MANIFEST/Juliet 선택 · KISA PDF 사람이 둔다 · 머지 위임)
