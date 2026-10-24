# 설계 35 — 번들 52: React + Spring Boot 투입 대비 — 스택 자동 판별 · React 규칙 · console 정리 · JSX 접근성 · TS interface · JSX 표 · JS 중복 (Fable 설계 · Opus 실행, 2026-10-24)

## 맥락
2026-10-24 사용자가 Spring Boot + React 프로젝트에 투입될 예정(Next.js 여부·MyBatis/JPA 미정). 화면 열둘을 그 스택에 대 봤더니 코드 검사·코드 정리는 확장자로 규칙이 붙어 대부분 돌지만 React 쪽이 얕고(빌드 결과 `dist/`·`build/` 가 검사돼 소음, JSX 접근성 0, JS 중복 0), 표 편집기·DTO 생성은 JSX·TS 꼴이 없다. 사용자 걱정은 「고를 게 많아지면 UI 가 나빠진다」 — 그래서 스택은 `package.json`·`pom.xml`·`build.gradle` 로 **자동 판별**하고 화면엔 한 줄만 보인다. 판별이 SpotBugs 클래스 폴더까지 찾으므로 번들 51 의 「클래스」 칸을 없앤다(칸이 하나 준다).

사용자 결정(2026-10-24, 뒤집지 않는다):
- 스택 자동 판별을 넣고 코드 검사의 「클래스」 칸을 없앤다. 틀리면 프로필로 덮는다(「조건은 YAML 에, 화면은 보여 주기만」).
- 번들 **하나**·PR 하나(번들 49~51 은 마무리·CI·AI 리뷰가 번들마다 반복돼 8시간 43분 중 63% — 사용자 「존나 오래 걸렸네」).
- 머지 위임.
- 코드 정리 console 지우기는 `log·debug·info·trace` — `warn·error` 는 남긴다.
- 범위는 A(빌드 결과 제외)·B(React 규칙)·C(console 정리)·D(JSX 접근성)·E(TS interface)·F(JSX 표)·G(JS 중복) 전부 + H(판별).

실행자는 Opus. 차례는 `S0`(설계 세션이 끝냄) → `5-39a` → `5-39b` → `5-39c` → `5-40` → `5-41` → `5-42` → `1-65` → `4-27` → `V-30` → 마무리(번들 모드). 가지 `work/2026-10-24c`.

---

## 1. 목표와 범위
- 목표: Spring Boot + React(Next 여부·MyBatis/JPA 미정) 프로젝트 폴더를 코드 검사·코드 정리에 넣었을 때 **고르는 칸 없이** 스택에 맞는 규칙만 돌고(빌드 결과 제외·클래스 폴더 자동), React 코드에 맞는 규칙·정리·출력 꼴이 생긴다.
- In scope(행 아홉, 차례): `S0` → `5-39a` 판별 엔진 → `5-39b` 라우트 연결(제외·클래스 폴더 여럿·framework) → `5-39c` 화면(칸 제거·감지 줄) → `5-40` React 규칙 5 + JSX 접근성 5 + 순수본 재주입 → `5-41` JS 중복 코드 → `5-42` console 정리 → `1-65` TS interface → `4-27` JSX 표 → `V-30` 표본(egov-react 무리 골든) → 마무리.
- Out of scope(입력 생기면): Next.js 전용 규칙(`"use client"`·API Routes) · React 화면 → API 호출 추적 · Boot 소스 생성 템플릿 · JPA(Hibernate 로그 복원·`@Query` 위험 문장·JPQL 대조) · JS 복잡도(파서 없음) · `vite.config` 의 `outDir` 읽기 · JSX 역파싱(표 편집기 불러오기).

## 2. 현재 구조 요약(탐색 2026-10-24)
- **걷기**: `LocalFiles.list(dir, globs, excludes, max)`(L154) — `SKIP_DIRS = {.git, target, node_modules}` 이름만·깊이 무관(L49,176), 숨김 폴더(`.next` 포함)도 건너뜀. exclude 글롭은 루트 상대 경로에 `glob()`(L237, `**/`·`/**/`·`*`·`?`, 대소문자 무시) — 폴더면 `SKIP_SUBTREE`. `excluded(rel, globs)`(L221)는 SpotBugs 지적용. **`build`·`dist` 를 `SKIP_DIRS` 에 넣으면 안 된다** — 표본 `commons-io/.../io/build` 는 Java 패키지(골든 hits 5줄). `list` 호출자 가운데 exclude 를 넘기는 곳은 `CheckRoutes`(L154→`CheckRunner.run`)·`CleanupRoutes`(L77·107→`CleanupRunner.preview/apply`, apply 는 같은 목록으로 범위 재확인)뿐. `FsRoutes`·`FolderDiff`·`AnalyzeRunner` 는 exclude 없이 — 건드리지 않는다.
- **규칙**: `Rule.Def(id, group, severity, kind, globs, regex, flags, skipComments, message, params, enabled, fix, ref)`. kind 분기 `RuleSet.load`(L94-112): regex(54)·absent(7)·jsUnusedImport·file·java·mybatis·marker·dup·external. 줄 단위 매치(`split("\n")`), `m`·`s` 플래그 무의미. `absent` 는 파일당 1건·태그 파싱 없음. **a11y 12 전부 JSP·HTML 글롭**(`mouseOnly` 는 소문자 `onclick` — JSX `onClick` 안 걸림). `Duplicates`(dup): 줄 정규화(공백 접기·주석·`import `·`package `·`{`·`}`·`};` 건너뜀) + FNV 블록 해시, 언어 무관 — 글롭만 `*.java`. `RuleSet.Run.observe`(L258) 는 **lang ≠ java 면 즉시 return**, `CheckRunner.runFiles`(L118) 는 변경분 모드에서 안 바뀐 비-Java 파일을 읽지도 않는다.
- **순수본 잠금**: `PureRulesJsonTest.goldenJson` = regex kind 전부 54 → `golden/check/pure-rules.json`; `pureHtmlEmbedsGolden` = `pure/tools/code_check.html` 의 `var RULES` 와 같아야(순수본 동결). regex 규칙을 더하면 **같은 청크 안에서** `node scripts/pure-rules-inject.js`(portfolio `C:/workspace/portfolio`, 깨끗 `b9cbbb3`) → `git -C C:/workspace/portfolio commit -am` → `bash scripts/sync-pure.sh`(MANIFEST 갱신) 까지 해야 초록(5-13·5-16·5-18 전례, PLAN 5-16 행). `PureCodeCheckTest` 는 `DIRS = common·jsp·tsx·security·a11y` 픽스처 수 28 고정, 언어는 확장자 → 순수본 `#lang` 에 **jsx 없음**(tsx 있음) → 새 픽스처는 `.tsx` 로. `CheckRulesTest` 내장 94·security 33 고정(갱신 관례).
- **SpotBugs**: `SpotBugsRun.run(sourceRoot, classesDir 하나, libDir, pluginJar, defs, ctx)` → `project.addFile(classesDir)`(L81) 한 번. `CheckRunner.External(classesDir, libDir, pluginJar)`. `CheckRoutes` L126-146 `folder(req, profile, p)` + 없으면 화면값 400 / 프로필값 note. `GET /api/check/defaults`(L52). 화면 `#classesWrap`·`#classes`(`syncGroup` L63, `runDir` L339-341), 스모크 `codeCheckExternal`(L1802), `CheckRoutesTest.externalFolders`(L207).
- **프레임워크**: `Profile.framework` 문자열(`egov35|egov4|spring`), `RuleSet.egov(p)` = `p == null || framework.startsWith("egov")` → 프로필에 값이 없으면 eGov 규칙 **꺼짐**. `CheckRoutes.load(p, groups, overrides)` 가 `CodeCheck` 6인자를 다시 만든다(L432-448).
- **코드 정리**: `Cleanup.RULES` 일곱, 단계 ①delete(debugger 둘·import 둘, `Edits` 하나로 `commit` 한 번) ②marker ③order ④eol. `deleteDebugger`(L424-454): 줄 패턴(js·ts 는 `;` 선택)·주석·백틱 홀수(`ticks[]`)·`BRACELESS`(`(?:\)|\belse|\bdo)\s*$` 직전 코드 줄) 가드 → `e.delete(line)`. `worse`(L314) 되돌림: 구문 깨짐·효과 없음·다른 지적 생김/사라짐. `tsx.console` 규칙 정규식은 `log|debug|info|warn|error|trace|dir|table` 전부, 줄 단위라 여러 줄 호출은 첫 줄만. 「일곱」 고정: `CleanupRoutesTest.rulesListsSeven`·스모크 7 체크박스(L1768)·`index.html:71`·`CodeCommands:87,104`·`Cleanup` javadoc. `CleanupCorpusTest` 는 `CheckCorpusTest.GROUPS` 에서 `other` 뺀 넷.
- **DTO**: `DtoGenerator.Style {RECORD, BEAN, EGOV_VO}`·`Style.of`·`generate(Table, Options)`→`Source(className, text)`, 타입 `TypeMapping.javaType`(`gen/type-mapping.yaml`, `decimal` 특수), 이름 `fieldName`(camel + 자바 키워드 `_`)·`className`. `GenRoutes` `POST /api/gen/dto` — **`name = className + ".java"` 고정**(L118), `?save=true` 는 `out/<프로필>/<시각>/dto`. 화면 `db_browser.html` `#dtoStyle`(record·bean·egovVo)·`#dtoSave` `.dl` 안 `<span class="ext ext-java">JAVA</span>`(common.css `.ext-java` L124 — `.ext-ts` 없음). 골든 `golden/gen/<table>-<style>.java` 9, `DtoGeneratorTest.goldenAndCompiles` CsvSource 9행(javac). CLI `dto --style`(DocCommands L166).
- **표 편집기**: 백엔드본 `tools/table_builder.html` 은 순수본과 이미 다르다(common.css·`_ext.js`·`mode_pure/cls/inline` 라벨·xlsx). 모델 `G{rows,cols,grid,theadRows,caption,colWidths}`, 셀 `{t,cs,rs,text,align}|{cover}`. `genHtml()`(L495-576, 전역) — `getMode()`·`loadClsOpts()`(L834 → `D{cls,scope,summary,colgroup}`)·색 `C`·`rowHtml`·`cellStyle`. `refreshOutput()`(L579) 가 `#out` 과 미리보기 `srcdoc` 둘 다 `genHtml()`. `toggleClsOpts`(L783)·`updateAlignBtns`(L791). `_ext.js` 는 전역 함수 + html 의 `typeof fn === 'function'` 훅(4-14 전례, 순수 함수 덮어쓰기 없음). 셀 text 는 저장된 HTML(이스케이프·`<br>`). `JsCorpusTest.tableImportExportStable` 은 기본 pure 모드.
- **표본**: 저장소 밖 `C:/workspace/toolbox-corpus` 에 **`package.json`·`pom.xml`·`build.gradle` 이 하나도 없다**(레시피가 소스만 받음) → 판별 실물 시험 불가, 픽스처로. `egov-react` = `src/` 99 파일(jsx 81·js 18, `.test.js(x)` 18), `<img` 21 중 4 가 여러 줄, `htmlFor` 55, `key={i|idx|index}` 6, `dangerouslySetInnerHTML` 0 — 지금 `CheckCorpusTest` 「other」(A 만, 골든 없음), `CleanupCorpusTest` 는 뺀다. `egov` 무리에 `.js` 277(`.min.js` 3). 상류 클론 `C:/workspace/.corpus-src/egov-react@…/package.json`(React 19·Vite 7, `vite build`, outDir 기본 `dist`).
- 레인: `core/check`·`core/fs`·tools·규칙 YAML·픽스처 = corpus 레인 → 마무리 `--corpus`. `pom.xml` 안 건드림 → `--db` 불필요(새 의존성 0 — JSON 은 Jackson, XML 은 정규식).

## 3. 설계 결정 — 전부 [고정]

### H. 스택 판별 `core/check/ProjectStack`
- **입력** 검사 폴더 루트. 루트에서 깊이 ≤ 4 로 `package.json`·`pom.xml`·`build.gradle`·`build.gradle.kts` 를 찾는다(`SKIP_DIRS`·숨김 건너뜀, 파일 50개 상한). 발견 폴더 하나 = `Module(rel, tool, tags, buildOut, classes)` — `tool` ∈ `npm|maven|gradle`, `tags` ⊂ `{react, next, vite, cra, boot, egov, mybatis, jpa}`, `buildOut`·`classes` 는 **실재하는** 루트 상대 경로만.
  - npm: Jackson 으로 `dependencies ∪ devDependencies` 키만 읽는다(값·스크립트 안 읽음). `react`→react, `next`→next, `vite`→vite, `react-scripts`→cra. buildOut 후보 `dist`·`build`·`out`·`coverage`(존재하는 것).
  - maven: `pom.xml` 글에 정규식 `<artifactId>([^<]+)</artifactId>` — `spring-boot`→boot, `egovframe|egovframework`→egov, `mybatis`→mybatis, `spring-boot-starter-data-jpa|hibernate`→jpa. classes 후보 `target/classes`·`src/main/webapp/WEB-INF/classes`.
  - gradle: 글 전체에 같은 키워드. classes 후보 `build/classes/java/main`·`build/classes/kotlin/main`. buildOut `build`(gradle 의 `build/` 는 `SKIP_DIRS` 가 아니다).
- **출력** `record Stack(List<Module> modules)` + `excludes()`(`<rel>/<dir>/**`, 루트면 `<dir>/**`) + `classesDirs()` + `egov()`(maven·gradle 모듈에 egov 태그) + `summary()` 한 줄 — 「backend/ Maven · Boot · MyBatis · 클래스 target/classes」 「frontend/ npm · React · Vite · 제외 dist」, 모듈 0 이면 「판별 못 함(package.json·pom.xml·build.gradle 없음)」. **코드 본문 저장 없음**(규칙 3 — 이름·태그만, 로그에도 경로만).
- **적용** `CheckRoutes` 폴더 검사·`CleanupRoutes` preview·apply 에서 `exclude = profile.codecheck.exclude + stack.excludes()`(apply 의 범위 재확인도 같은 목록). `classesDirs` = 화면/프로필 값 있으면 그것 하나, 없으면 `stack.classesDirs()`(여럿). `framework` 는 프로필 값이 없을 때만 `stack.egov() ? "egov35" : "spring"`. 프로필 `classesDir`·`libDir`·`exclude` 는 **덮어쓰기**로 남는다(「조건은 YAML 에, 화면은 보여 주기만」).
- `SpotBugsRun.run(sourceRoot, List<Path> classesDirs, libDir, pluginJar, defs, ctx)` — `addFile` 을 폴더마다, `webInfLib` 은 첫 WEB-INF 것. `CheckRunner.External(List<String> classesDirs, String libDir, Path pluginJar)`. 못 찾으면 note 「클래스 폴더를 못 찾았다(빌드 전?) — 묶음 ㅅ 건너뜀」.
- 라우트: `GET /api/check/stack?path=` → `{summary, modules:[{rel,tool,tags,buildOut,classes}], excludes, classesDirs}`. `GET /api/check/defaults` **삭제**(칸이 없어졌다). `RunRequest.classesDir`·`libDir` 는 API·CLI 덮어쓰기로 유지.
- 화면 `code_check`: `#classesWrap`·`#classes` 삭제, `#dir` 뒤 `<span class="opt" id="stackInfo"></span>` — `#dir` `change` 때 `/api/check/stack` 으로 채움(textContent), 결과 요약에 `· 스택: <summary>` 덧붙임. `code_cleanup`: 미리보기 요약에 `· 제외 dist` 꼴(stack.excludes 가 있을 때만, 라우트 응답 `excluded` 목록).
- 버린 대안: `SKIP_DIRS` 에 `build`·`dist` 고정(commons-io 패키지 오탐) · 화면 스택 고르기(사용자 — 칸 늘리기 금지) · `vite.config` 파싱(코드라 못 읽음, 다르면 프로필 `exclude`).

### B. React 규칙 5(regex, 순수본 재주입 대상)
`rules.yaml` ㅁ·ㅇ 묶음에(새 묶음 없음). 줄 단위 · 순수본 JS 이식 가능 문법만(`PureRulesJsonTest` 금지 목록 피함) · JSX 속성 안 화살표 `=>` 때문에 `[^>]*` 대신 **`[^<]*`**(다음 태그 전까지).
| id | 묶음·등급 | 글롭 | regex | message / fix |
|---|---|---|---|---|
| `tsx.keyIndex` | tsx·info | jsx·tsx | `\bkey\s*=\s*\{\s*(?:i\|idx\|index\|k)\s*\}` | 목록 key 가 인덱스 — 순서가 바뀌면 상태가 엉킨다 / 데이터의 고유 id 를 key 로 |
| `tsx.blankNoopener` | tsx·warn | jsx·tsx | `<a\b(?=[^<]*\btarget\s*=\s*(?:["']\|\{\s*["'])_blank)(?![^<]*\brel\s*=)` | target=_blank 에 rel 없음(탭 탈취) / `rel="noopener noreferrer"` |
| `security.jsEnvSecret` | security·error | js·jsx·ts·tsx·mjs | `\b(?:REACT_APP\|VITE\|NEXT_PUBLIC)_\w*(?:SECRET\|PRIVATE\|PASSWORD\|PASSWD\|TOKEN)\w*\b` | 번들에 박히는 환경 변수에 비밀 이름 / 서버 쪽으로 옮긴다 |
| `security.jsTokenStorage` | security·warn | js·jsx·ts·tsx·mjs | `\b(?:localStorage\|sessionStorage)\.setItem\s*\(\s*["'][^"']*(?:token\|jwt\|auth\|session\|password)` flags `[i]` | 토큰을 웹 저장소에(XSS 로 유출) / httpOnly 쿠키 또는 메모리 |
| `security.jsHrefJavascript` | security·warn | jsx·tsx | `\bhref\s*=\s*(?:["']\|\{\s*["'])\s*javascript:` flags `[i]` | href 에 javascript: (React 가 막는다) / onClick 또는 button |
+ `common.password` 글롭에 `*.jsx`·`*.ts`·`*.tsx`. JSP·HTML 에는 `javascript:` 규칙을 **안 단다**(eGov 표본에 흔해 B 골든 2% 상한을 넘긴다). 수: 내장 94→104, security 33→36, regex 54→64.

### D. JSX 접근성 5(regex, a11y 묶음, 글롭 jsx·tsx, 순수본 재주입 대상)
JSP 짝과 같은 등급·같은 받아들임 집합. 여러 줄에 걸친 태그는 **놓친다**(`(?=[^<]*>)` 로 같은 줄에 닫힌 태그만 — egov-react `<img` 21 중 4, 이력에 적는다).
| id | 등급 | regex |
|---|---|---|
| `a11y.jsxImgAlt` | warn | `<img\b(?=[^<]*>)(?![^<]*\balt\s*=)` |
| `a11y.jsxInputLabel` | info | `<input\b(?=[^<]*>)(?![^<]*\btype\s*=\s*(?:["']\|\{\s*["'])(?:hidden\|button\|submit\|image\|reset))(?![^<]*\b(?:id\|title\|aria-label\|aria-labelledby)\s*=)` |
| `a11y.jsxMouseOnly` | info | `<(?:div\|span\|td\|tr\|li\|img)\b(?=[^<]*\bonClick\s*=)(?![^<]*\b(?:onKey\w+\|tabIndex\|role)\s*=)` |
| `a11y.jsxAnchorHref` | info | `<a\b(?=[^<]*>)(?![^<]*\bhref\s*=)` |
| `a11y.jsxIframeTitle` | info | `<iframe\b(?=[^<]*>)(?![^<]*\btitle\s*=)` |
message·fix 는 JSP 짝(`a11y.imgAlt` 등)과 같은 글에 「(JSX)」. 기존 a11y 규칙의 글롭은 안 건드린다.

### G. JS 중복 코드
`metric.duplicate` 글롭 `['*.java','*.js','*.jsx','*.ts','*.tsx','*.mjs']`, `minLines` 30 그대로. `Duplicates.see` 건너뜀 집합에 `)`·`);`·`]`·`];`·`},`·`/>` 추가(JSX 닫힘 줄 소음). `RuleSet.Run.observe` 의 lang 가드를 「java 이거나 `dupDef.matches(s)`」로, `CheckRunner.runFiles` L118 의 `.java` 가드도 `rules.dupGlobs` 매치로. 결과 꼴·등급 그대로.

### C. console 정리
`Cleanup.RULES` 에 `tsx.console`·`common.consoleLog`(→ 아홉). 단계 ① 에 `deleteConsole`(debugger 와 같은 `Edits`): 지적 줄의 주석 벗긴 글이 `^\s*console\.(?:log|debug|info|trace)\s*\(` 로 시작해야 하고(warn·error·dir·table 은 손 안 댐 — 건너뜀 글 없이 그냥 둠), 같은 줄부터 괄호 깊이가 0 이 되는 줄(최대 20줄)까지가 한 문장이며 그 끝이 `\)\s*;?\s*$`, 첫 줄 백틱 홀수·`BRACELESS`·범위 안 `tb-ignore` 는 건너뜀(글 「줄 N: console 과 다른 코드가 한 줄에 있다」·「여러 줄 console 끝을 못 찾음」). 문장 줄 전부 `e.delete`. `worse` 는 그대로(warn·error 가 남아 수가 안 줄면 「효과 없음」 — 편집이 없으면 commit 이 안 돈다). 「일곱」 → 「아홉」 다섯 자리 갱신.

### E. TS interface
`DtoGenerator.Style.TS`(`of`: `ts|typescript|interface`). 타입: String→`string` · Integer·Long·Short·Byte·Double·Float·BigDecimal·BigInteger→`number` · Boolean→`boolean` · LocalDate·LocalDateTime·LocalTime·OffsetDateTime·Timestamp·Date→`string` · `byte[]`→`string` · Object→`unknown`(TODO 주석 그대로). nullable 이면 `: T | null`. 이름은 `fieldName` 의 camel 까지(자바 키워드 `_` 는 안 붙임 — TS 속성 이름엔 예약어 허용). 꼴:
```ts
/** 사용자 — 테이블: public.users */
export interface Users {
  /** 로그인 아이디 · UNIQUE */
  loginId: string;
  email: string | null;
}
```
검증 어노테이션·import 없음(`Validation.of` 의 notes 만 doc 에). `GenRoutes` 파일 이름 `className + (TS ? ".ts" : ".java")`. 화면 `#dtoStyle` 에 `<option value="ts">TypeScript interface</option>`, 고르면 `#dtoSave` 의 `.ext` 글 `TS`·클래스 `ext-ts`(common.css 에 `.ext-ts { background: #2b6cb0; }` 한 줄). CLI `--style` 설명에 `ts`.

### F. JSX 표 출력
백엔드본 `table_builder.html` 에 넷째 라디오 `<label class="opt" id="mode_jsx"><input type="radio" name="mode" value="jsx"> JSX</label>`. `genHtml(modeArg)` 첫 줄 `var mode = modeArg || getMode(); if (mode === 'jsx' && typeof tbGenJsx === 'function') return tbGenJsx();`. `refreshOutput` 은 `#out` 에 `genHtml()`, 미리보기엔 `getMode() === 'jsx' ? genHtml('cls') : 그 값`. `toggleClsOpts`·`updateAlignBtns` 는 jsx 를 cls 와 같이. `_ext.js` `tbGenJsx()`(전역, ES5): `G`·`loadClsOpts()`·`captionText()` 로 `<table className="…">`·`<caption>`·`<colgroup><col style={{ width: '20%' }} /></colgroup>`·`<thead>/<tbody>`·`<th colSpan={2} rowSpan={2} scope="col" style={{ textAlign: 'center' }}>`. 셀 글은 저장된 HTML 에서 `<br>`→`<br />` 만, 빈 칸은 nbsp 옵션이면 `&nbsp;` 아니면 빈 요소. 색(inline 모드 전용)은 jsx 에 안 쓴다. 순수본 함수 덮어쓰기 없음(4-14 꼴).

### 공통
- 새 의존성 0 · 새 화면 0 · 고르는 칸 0 증가(DTO 꼴 option 하나·표 라디오 하나만).
- 완료 행 불변. 기존 규칙 regex 불변(글롭만 더함).

## 4. 실행 스텝(= PLAN §16 행)

**S0** — 설계 35 파일 + PLAN §15·§16 + PROGRESS(번들 51 완료 표시 포함). 검증 `bash scripts/verify.sh`(docs). 설계 세션이 친다.

**5-39a — `ProjectStack` 판별 엔진 + 픽스처**
- 대상: 새 `core/check/ProjectStack.java` · 새 `fixtures/check/stack/`(`frontend/package.json` react+vite, `frontend/dist/app.js`(debugger 한 줄 — 제외돼야), `frontend/src/App.jsx`, `backend/pom.xml` spring-boot+mybatis, `backend/target/classes/.keep`, `egov/pom.xml` egovframe, `gradle/build.gradle` spring-boot+data-jpa, `gradle/build/classes/java/main/.keep`, `gradle/build/x.js`, `plain/src/build/Pkg.java`(package.json 없는 build 폴더 — 제외 안 됨)) · 새 `ProjectStackTest`.
- 검증: `-Dtest=ProjectStackTest` — 모듈 넷·태그·`excludes()` = `frontend/dist/**`·`gradle/build/**`(plain 은 없음)·`classesDirs()` 둘·`egov()`·`summary()` 글·`package.json` 깨진 JSON 은 모듈 없음(예외 없음).
- 의존: S0.

**5-39b — 라우트 연결**
- 대상: `CheckRoutes`(run 에 stack · exclude 합침 · classesDirs 여럿 · framework · `GET /api/check/stack` · `defaults` 삭제) · `CleanupRoutes`(exclude 합침·응답 `excluded`) · `SpotBugsRun`(List) · `CheckRunner.External`(List) · `CheckRoutesTest`(`externalFolders` 고침 + `stackDetected`: 픽스처 폴더 검사 → `frontend/dist/app.js` 지적 없음·`stack` 응답) · `CleanupRoutesTest`(dist 제외) · `SpotBugsRunTest`(classes 둘 합침 → 수 합).
- 검증: `-Dtest='CheckRoutesTest,CleanupRoutesTest,SpotBugsRunTest,SpotBugsLoadTest'`.
- 의존: 5-39a.

**5-39c — 화면**
- 대상: `code_check.html`(칸 삭제·`#stackInfo`) · `code_check_ext.js`(defaults 호출 삭제·`syncGroup` ext 줄 삭제·`runDir` classesDir 삭제·stack 줄·요약) · `code_cleanup_ext.js`(요약) · `SmokeHtmlUnitTest.codeCheckExternal`(프로필 `classesDir` 없이 — 픽스처에 `target/classes` 를 두고 판별로 돈다, `#stackInfo` 글) · `visual.js`(code_check run 상태 그대로).
- 검증: `-Dtest='SmokeHtmlUnitTest#codeCheckExternal+codeCheckFolderRun+codeCleanupPreviewApply,ToolsFolderTest,ToolsA11yTest'`.
- 의존: 5-39b.

**5-40 — React 규칙 5 + JSX 접근성 5 + 순수본 재주입**(한 청크 — 재주입 전엔 `pureHtmlEmbedsGolden` 이 빨강)
- 대상: `rules.yaml`(10 + `common.password` 글롭) · 픽스처 `tsx/pos-react.tsx`·`tsx/neg-react.tsx`·`a11y/pos.tsx`·`a11y/neg.tsx`(`.tsx` — 순수본 `#lang` 에 jsx 없음) · 골든 `check/fixtures.json`·`check/pure-rules.json` 갱신 · `CheckRulesTest`(104·36) · `PureRulesJsonTest`(64) · `PureCodeCheckTest`(32) · `node scripts/pure-rules-inject.js` → portfolio 커밋(`git -C C:/workspace/portfolio commit -am "toolbox: code_check 규칙 64 — React·JSX 접근성(toolbox-server 5-40)"`) → `bash scripts/sync-pure.sh` → `pure/`·`pure/MANIFEST`.
- 검증: `-Dtest='CheckRulesTest,PureRulesJsonTest,PureFrozenTest'` 초록 · `-Dtest=PureCodeCheckTest`(node, 약 1분).
- 의존: S0(5-39 와 독립).

**5-41 — JS 중복 코드**
- 대상: `rules.yaml`(글롭) · `Duplicates`(건너뜀 집합) · `RuleSet.Run.observe` · `CheckRunner.runFiles` 가드 · 새 `fixtures/check/dup/a.jsx`·`b.jsx`(정규화 30줄 같은 JSX) · `DuplicatesTest`(jsx 짝 1건·변경분 모드에서 안 바뀐 jsx 가 상대로) · `CheckRulesTest` 건너뜀 목록에 `dup/`.
- 검증: `-Dtest='DuplicatesTest,CheckRulesTest,CheckStoreTest'`.
- 의존: S0.

**5-42 — console 정리**
- 대상: `Cleanup`(RULES·`deleteConsole`·javadoc) · 픽스처 `fixtures/cleanup/console/pos.ts`(한 줄 log·여러 줄 log·`console.error` 남김·한 줄에 다른 코드·중괄호 없는 if·템플릿 리터럴 안)·`pos.jsp`(`<script>` 안 `console.log`) · 골든 `cleanup/console-*.txt`·`steps.json` · `CleanupRoutesTest.rulesListsSeven`→`rulesListsNine` · 스모크 9 · `index.html`·`CodeCommands` 「아홉」.
- 검증: `-Dtest='CleanupTest,CleanupRoutesTest,SmokeHtmlUnitTest#codeCleanupPreviewApply,LauncherTest'`.
- 의존: S0.

**1-65 — TS interface**
- 대상: `DtoGenerator`(Style·`ts()` 렌더·타입 표) · `GenRoutes`(이름) · `DocCommands`(설명) · `db_browser.html`(option·span) · `common.css`(`.ext-ts`) · 골든 `gen/{users,products,order_items}-ts.ts` · `DtoGeneratorTest`(CsvSource 3행, ts 는 javac 건너뜀 + `nullable → | null`·키워드 이름 단언) · `GenRoutesTest`(`.ts`) · `SmokeHtmlUnitTest.dbBrowserMakesDtoFromDdl`(ts 고르면 `export interface`·`TS`).
- 검증: `-Dtest='DtoGeneratorTest,GenRoutesTest,DocCliTest,SmokeHtmlUnitTest#dbBrowserMakesDtoFromDdl,ToolsFolderTest'`.
- 의존: S0.

**4-27 — JSX 표 출력**
- 대상: `table_builder.html`(라디오·`genHtml(modeArg)`·`refreshOutput`·`toggleClsOpts`·`updateAlignBtns`) · `table_builder_ext.js`(`tbGenJsx`) · `SmokeHtmlUnitTest.tableBuilderJsxOutput`(`merge.json` 모델 → `mode_jsx` → `#out` 골든 `table/jsx.txt`, 미리보기 srcdoc 은 `<table class=`) · `visual.js` table_builder 상태 `jsx`.
- 검증: `-Dtest='SmokeHtmlUnitTest#tableBuilderJsxOutput+tableBuilderPastesExcelClipboard,ToolsFolderTest,ToolsA11yTest'`.
- 의존: S0.

**V-30 — 표본**
- 대상: `CheckCorpusTest.GROUPS` 에 `egov-react` 무리(other 에서 뺌, 골든 `check-egov-react.json`·hits) · `CleanupCorpusTest` 가 따라 돈다(`cleanup-egov-react.json`) · egov 무리 골든 diff(`metric.duplicate` js·새 규칙) · 이력에 건수(jsx 접근성·keyIndex·console 정리 수, 여러 줄 img 놓침 수).
- 검증: `bash scripts/verify.sh --corpus`(A 0 · B 는 갱신 diff 를 이력에).
- 의존: 5-39~4-27 전부. 마무리 직전.

**마무리** — CLAUDE.md 여덟. 레인 java·tools·docs·corpus → `--corpus`. `--db` 불필요(pom 불변 — `verify.sh` 끝 줄이 말하면 그때). 머지 위임.

## 5. 금지 사항
- `LocalFiles.SKIP_DIRS` 에 `build`·`dist` 를 넣지 않는다 · `FsRoutes`·`FolderDiff`·`AnalyzeRunner` 의 걷기는 안 바꾼다.
- `pom.xml` 새 의존성 0 · `pure/` 는 `sync-pure.sh` 로만 · 기존 규칙 regex·골든·완료 행 불변 · 새 화면·새 고르기 칸 금지.
- 판별 엔진이 `package.json` 값·스크립트·`vite.config` 를 읽거나 저장하지 않는다(규칙 3).
- JSP·HTML 에 `javascript:` 규칙을 달지 않는다(표본 2% 상한).

## 6. 최종 검증
`bash scripts/verify.sh` → `bash scripts/verify.sh --corpus` → `bash scripts/gate-probe.sh` → PR → CI → AI 리뷰 처분 → 머지(위임). 집 검증 `node scripts/puppeteer/visual.js b52`(code_check·code_cleanup·table_builder jsx·db_browser).

## 7. 중단 조건
- `pure-rules-inject.js` 가 portfolio 에서 실패하거나 portfolio 트리가 더럽다 → 5-40 을 `wip/5-40` 로 빼고 보고(순수본 동결).
- SpotBugs 가 `addFile` 여럿에서 소스 매핑률이 95% 아래로 떨어진다(jspwiki-bin 골든) → 5-39b 멈춤.
- 그 밖은 CLAUDE.md 번들 모드(행당 정한 것 둘, verify 빨강 둘).

## 8. 불확실 항목(실행해야만 알 수 있는 것 — 실행자가 그 행 첫 스텝에서 잰다)
- 5-39b: `Project.addFile` 을 둘 이상 불렀을 때 `SortedBugCollection` 소스 매핑이 그대로인지(`SpotBugsRunTest` 가 픽스처 classes 를 둘로 나눠 잰다).
- 5-40: `pure-rules-inject.js` 가 CRLF·골든 64 로 돌아가는지(5-16 전례 — 끝에 「rules 64」).
- V-30: egov 무리 `metric.duplicate` js 건수(이력에만).
