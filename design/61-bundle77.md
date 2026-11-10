# 설계 61 — 번들 77: Tibero 열 중복 · 호출만 남기기 · JSP·HTML `_blank` 규칙 · 산출물 내장 양식·작업 실패 사유 · 잔손 셋 (Fable 설계 · Opus 실행, 2026-11-09)

## 맥락
번들 76 이 남긴 행 넷(1-84 · 0-77 · 0-78 · 5-73)과 compare ㉖ 「후보로」 가운데 올린 둘(② `local` 고침 「호출만 남기기」 · ③ JSP·HTML `target=_blank` 규칙), 그리고 설계 도중 들어온 현장 장애 하나(산출물 생성이 사유 없이 `FAILED` — 아래 「추가 건」)를 한 번들로 닫는다(사용자 2026-11-09 「최대한 크게」). 레인 — 1-84 는 db+corpus(`core/meta`·`core/dialect` 는 두 배열에 다 있다) · ②③·5-73·추가 건은 corpus(`core/deliverable`·`core/report`·`resources/tools` 가 corpus 배열에 있다) · 0-78·0-77 은 tools. 무거운 레인은 끝에 한 번씩, `--corpus` → `--db` 차례.

**추가 건(사용자 2026-11-09, 폐쇄망 PC)** — `deliverable_sql.html` 산출물 생성이 바로 `FAILED 01 데이터베이스 정의서` 로 끝나고 화면·로그 어디에도 사유가 없다. `/api/jobs/<id>` 를 직접 열어야 `error`(zip 서명을 못 찾음)가 보인다. 원인 — 그 PC 의 문서보안(DRM)이 `templates/deliverable/example` 의 예시 양식 xlsx 를 전부 암호화해 zip 이 아니게 됐고, `XlsxFiller.fill:33` `new XSSFWorkbook(in)` 이 첫 문서에서 터졌다. 예시 양식 11장은 코드가 찍어 낸 것(`Forms.all()` + `XlsxFiller.writeTemplate`)인데 생성 단계가 그 결과를 디스크에서 다시 읽는다.
- **보정** — ① **동봉 프로필 다섯(`example`·`site-ora`·`site-tibero`·`demo`·`docker-ora`)이 전부 `templateDir: templates/deliverable/example/`·`mapping: mappings/deliverable/example.yaml` 을 명시한다** → 「templateDir 가 없을 때만」 으로는 현장 프로필이 안 풀린다(사용자 결정 8) ② 화면 여섯 자리(다섯 파일)가 전부 `j.status + ' ' + (j.message || '')` 꼴이라 `j.error` 를 안 읽는다 — `deliverable_sql.html:941` · `code_check_ext.js:278` · `code_cleanup_ext.js:136` · `program_analysis_ext.js:131·:562` · `spring_source_generator_ext.js:186`(`db_browser.html:327` 은 SSE `failed` 의 `message` 를 이미 찍는다 · CLI `Batch:181` 도 `error` 를 찍는다). `j.message` 는 마지막 진행 글이다. `common.js` 에 공용 폴링 도우미는 없다 ③ **`JobEventRulesTest.failedJobMessageIsNotLogged:56-85` 가 예외 글이 로그에 안 실리는 것을 건다**(`JobManager:18` javadoc 「예외 메시지는 남기지 않는다 — 사용자 코드·SQL 이 섞일 수 있다」) → 로그에는 **글 없이** 예외 클래스 이름과 우리 코드 자리만 ④ 디스크에서 xlsx 를 읽는 자리는 `XlsxFiller.fill:33` **하나뿐**(18 상관도·`00_작성안내.xlsx` 는 `XlsxWriter` 로 새로 쓴다 · `WorkbookFactory`·`OPCPackage`·`ZipFile` 0) ⑤ CLI `deliverable`(`DocCommands:85`)은 같은 라우트를 탄다 ⑥ 커밋된 `mappings/deliverable/example.yaml` 은 `Mapping.exampleYaml(Forms.all(), 2, 3)` 과 글이 같다(`XlsxFillerTest:197` 이 건다) — 문자열을 읽는 로더는 없다(`Mapping.load(Path)` 뿐) ⑦ `self-check.txt` 가 `DeliverableService.build(8)` 복잡도 22·인자 8 · `XlsxFiller.fill(4)` 복잡도 25·중첩 5 · `DeliverableRoutes.postBuild(7)` 복잡도 23 을 건다 → 그 수를 안 바꾸게 고친다 ⑧ 반입 zip 은 `git ls-files … templates mappings` 를 그대로 넣는다(`package.sh:56`) · `package-check.sh:44` 의 필수 목록에 양식은 없다 · `rehearse.sh`·`SelfTest` 는 산출물 양식을 안 본다

**보정(2026-11-09, `ca61fbe` 트리 — 행 글과 실제가 다른 자리)**
- **③ 전제가 틀렸다** — `tsx.blankNoopener` 는 「jsx kind 엔진」 이 아니라 **줄 단위 정규식 규칙**이다(`rules.yaml:167`, kind 없음 = regex, `pure-rules.json` 63 에 든다). JSP·HTML 용 태그 훑기는 없다 — 여러 줄 태그를 보는 것은 `JsxLabelRule`(`kind: jsxInputLabel`, `<input>` 전용) 하나. → 새 kind `blankRel` + 새 클래스 `BlankRelRule` 을 `JsxLabelRule` 꼴로 만든다. `PureRulesJsonTest:32-35` 는 `kind == regex` 만 세므로 새 규칙은 `pure-rules.json` 에 안 든다
- `MarkupFix.addRel` 의 `A_TAG = <a\b[^<]*` 는 속성 값 안의 JSP 태그(`href="<c:url …/>"`)에서 끊긴다 — 표본 JSP 의 흔한 꼴이라 그대로 재사용하면 대부분 「여러 줄 태그」 로 건너뛴다. 재사용하는 것은 단계(`markup`)·`REL` 글·`apply` 틀이고, 자리 찾기는 `BlankRelRule.tags` 가 준다
- **1-84** — `TiberoMetaSource` 는 16줄 빈 껍데기(override 0) · `JdbcMetaSource.loadColumns:111-151` 에 이름 중복 제거 없음 · `SnapshotStore:135-156` 도 없음(PK 위반이 저장 전체를 되돌린다) · `ALL_TAB_COLUMNS` 를 읽는 자바 코드 0 · **딕셔너리 형식 글 → `java.sql.Types` 매핑이 어디에도 없다** · Tibero 시험·골든 0. 「meta-warnings」 는 골든 파일 이름이고 런타임 개념은 `MetaSource.Warning(kind, sqlState, vendorCode, count)` — `JdbcMetaSource` 에는 `unknownSorts` 계수기 하나뿐, `VendorMetaSource.vendor(kind, fallback, call)` 이 SQLException 을 경고로 바꾸고 물러선다. 행의 「가짜 `ResultSet` 단위 시험」 은 `VendorFallbackTest.sortSwapped:139-163` 의 Proxy 수법으로, 「SQL 골든」 은 **H2 의 가짜 `ALL_TAB_COLUMNS` 표에서 실제 실행**(`unknownOracleDescendIsCounted:101-106` 전례) + Oracle 컨테이너 대조로 바꾼다
- **0-78** — `VerifyLanesTest` 는 db·corpus 배열과 자바 소스만 본다(셸·tools 레인을 모른다) → 강제 지점을 **`hooks-test.sh` 자기 검사**로 옮긴다(tools 레인이 바뀌면 그 시험이 돌고, 자바 레인 시험은 tools 만 바뀐 커밋에서 안 돈다). `hooks-test.sh` 가 읽는 레인 밖 경로는 `bundle/SOURCES.md` 만이 아니다 — `bundle/drivers*/pom.xml` · `README.md`(docs) · `pom.xml`(java) · `run.bat` · `toolbox.bat` · `.gitignore`(`git check-ignore`) → 같은 사고가 다시 안 나게 **전부** tools 레인에 넣는다. 「release」 는 레인이 아니라 트리 전체 도장(`verify.sh:119`)이라 안 바뀐다
- **0-77** — 같은 틀린 글이 `bundle/drivers-alt/pom.xml:5` · `PLAN.md:40` · `PLAN.md:1223` 에도 있다 → 같이 고친다
- **5-73** — ① `addLang` 의 「이미 있다」 가지는 규칙 정규식(`<html\b(?!.*\blang\s*=)`)이 `xml:lang=` 도 lang 으로 봐 지적 자체가 안 서므로 정리 경로로는 안 닿는다(직접 부를 때만) ⑤ 인터페이스 판정은 `insertable:218` 안이라 `NO_LOGGER`(`:107` `orElse`)가 먼저 난다 ② 네 사유 글은 `LogFix.java` 와 설계 60 밖 어디에도 없다(시험 0)

**실측(같은 트리 · `target/app.jar` CLI `check <표본> --groups java --json --profile example`, data 는 scratch · 표본 글 훑기. 정확한 수는 V-57a 가 준다)**
- ② `java.unusedLocal` 134(egov 42 · jeecg 42 · shopizer 41 · roller 5 · zheng 4)의 초기화식 꼴:

| 꼴 | egov | jeecg | shopizer | roller | zheng | 합 | 처분 |
|---|---|---|---|---|---|---|---|
| 한 줄 · 호출 하나 | 42 | 19 | 14 | 4 | 4 | **83** | 호출만 남긴다 |
| 호출 하나 · 여러 줄 | — | 3 | 3 | 1 | — | 7 | 건너뜀 「여러 줄 선언」(그대로) |
| 리터럴·null | — | 4 | 7 | — | — | 11 | 줄 삭제(번들 76 그대로) |
| 캐스트 | — | 7 | 10 | — | — | 17 | 건너뜀 |
| `new`(배열 포함) | — | 5 | 5 | — | — | 10 | 건너뜀 |
| 삼항 | — | 3 | — | — | — | 3 | 건너뜀 |
| 이름·필드·비교식 | — | 1 | 2 | — | — | 3 | 건너뜀 |

  egov 42 는 전부 `EgovXxxController` 의 같은 꼴(로그인 검사 부작용 호출을 `_loginVO` 로 받음 — 29 파일). **그 42 자리 모두 `LoginVO` import 가 파일의 다른 곳에서도 쓰인다** → 남는 import 0. 한 줄 호출 83 가운데 람다·메서드 참조를 든 것 0(둘은 여러 줄 꼴 — shopizer `ReadableProductVariantGroupMapper:67` · `UserFacadeImpl:232`). 기본형으로 받는 것 2(jeecg `GenerateController:446` · zheng `SnowflakeIdWorker:179`)
- ③ JSP·HTML(`jsp`·`jspf`·`tag`·`html`·`htm`)의 rel 없는 `target=_blank` `<a>` — §3-6 의 훑기(`dead`·`attrs`·`tags`)를 그대로 옮겨 표본에 돌린 값 **121 태그**: jeesite 32 · jeecg 28 · ruoyi-classic 21(html) · egov 16 · egov-prev 16 · egov-homepage 3 · jspwiki 2 · roller 2 · zheng 1(check 골든 표본만 **102**). 끼울 자리 121 이 전부 `target="_blank"` 바로 뒤(어긋난 자리 0) · 따옴표 없는 target 0 · rel 이 있는데 `noopener` 가 없는 것 0. 꼴 — 속성 값 안 JSP 태그·EL(`href="<c:url …/>"` · `href="${ctx}/…"`) 다수 · 같은 따옴표가 겹친 값(`href="<c:out value="${…}"/>"`) egov 5 · 여러 줄 태그 2(jspwiki `AJAXSearch.jsp:112·117`) · 한 줄에 태그 둘 이상 2줄(jeesite `default.jsp:56` 넷 · jeecg `login3.jsp:132` 둘). 글자 훑기(주석 안 가림)로는 134 — 차이 13 은 **주석 안 12**(jeesite 9 · jeecg `function.jsp:126` · egov-portal·egov-enterprise `Intro.jsp` — 여러 줄 `<%-- --%>`·`<!-- -->`)와 **script 안 문자열 1**(jeecg `uploadView.jsp:21`, 따옴표 없는 `target=_blank` — 고치면 JS 문자열이 깨진다). 둘 다 판정에서 뺀다. 글롭 밖 — jeecg `.ftl` 90(안 본다)
- 우리 코드: `tools/*.html` 의 `_blank` 13 은 전부 `rel` 이 있다(`index.html` 12 · `deliverable_sql.html:921` 은 JS DOM) → 자기 검사 지적 0. `pure/tools/논리명_변환기.html` 에 rel 없는 `<a target="_blank">` 여섯(`:216·388·558·571·634·653`) — `pure/` 는 자기 검사 밖이고 동결이다(끝 보고 「사람이 할 것」 — portfolio 인계)
- 기존 픽스처: check·cleanup 의 JSP·HTML 픽스처에 `_blank` 0(`tsx/pos-react.tsx:5` · `cleanup/markup/pos.tsx` 뿐) → 기존 골든은 새 줄 밖 불변
- 메서드 수(상한 30, 생성자 제외): `Cleanup` 29 · `LogFix` 14 · `MarkupFix` 5 · `LocalFix` 2 · `LocalsAst` 4 · `Imports` 7. `self-check.txt` 에 이 클래스들 줄은 `Cleanup` 것뿐(`worse` 35 등 일곱)
- `golden/meta/oracle.json` 의 (nativeType, jdbcType) 짝: `NUMBER` 2 · `VARCHAR2` 12 · `TIMESTAMP(6)` 93 · `CHAR` 1(표본 DDL 에 DATE·LOB 없음 — 1-84b 의 임시 표가 넓힌다)
- `ReadOnlyJdbc.check:55-79` — 첫 낱말 SELECT·WITH · `;` 없음 · 쓰기 낱말 없음 → `SELECT … FROM ALL_TAB_COLUMNS WHERE OWNER = ? AND TABLE_NAME = ? ORDER BY COLUMN_ID` 는 지난다(`wrap` 은 public)

**범위 밖** — `security.jspElParam` 고침 · java.time 날짜 표시 · `--db` CI 이전(셋 다 후보에서 내림) · `java.unusedLocal` 의 `_` 접두 이름 면제(`IGNORED` 불변) · 여러 줄 선언의 호출만 남기기 · `.ftl`·`.vue`·`.xhtml` 의 `_blank` · `<area>`·`<form>` 의 target · `tsx.blankNoopener` 의 정규식·판정 · 5-31·2-17·보류·조건부 행 · 현장 MyBatis 판

## 사용자 결정(2026-11-09)
1. 넣는 행 — 1-84(고칠 길 **둘 다**) · 0-77 · 0-78 · 5-73
2. compare ㉖ 「후보로」 ① — `java.unusedLocal` 은 `_loginVO` 같은 `_` 접두 이름도 계속 지적한다(`IGNORED` 불변)
3. ② — `local` 고침에 「호출만 남기기」: 초기화식이 메서드 호출 하나일 때 선언만 떼고 호출 문장을 남긴다. `new`·캐스트·삼항은 건너뜀(사유 글). 남는 import 처리는 설계가 정한다
4. ③ — JSP·HTML `target="_blank"` rel 없음 규칙을 **정규식 아닌 백엔드 전용 규칙**으로(`pure-rules.json`·순수본 불변). 고침은 `MarkupFix` 의 rel 넣기를 쓴다
5. ④⑤⑥ — `jspElParam` 고침 · java.time 날짜 표시 · `--db` CI 이전은 후보에서 내린다(지적·로컬 `--db` 는 그대로)
6. **1-84 길 ① 은 형식까지 딕셔너리에서 읽는다**((나) — 「디테일하게 하는 건 (나)가 맞다. 오류는 나도 된다」). Tibero 드라이버 `getColumns` 를 안 부르고 `ALL_TAB_COLUMNS` 의 형식·길이·정밀도·NULL·기본값으로 열을 만든다. 현장에서 어긋나는 것은 받아들인다
7. 설계 Fable · 실행 Opus · **머지 위임 있음**
8. **산출물 양식 — `templateDir` 가 없거나 예시 폴더(`templates/deliverable/example`)를 가리키면 내장 양식을 메모리에서 만든다**((가)). 그 폴더의 xlsx 는 안 읽는다. 매핑도 없거나 예시 매핑(`mappings/deliverable/example.yaml`)이면 코드에서 만든다. 현장 프로필은 안 고쳐도 된다. 예시 폴더의 xlsx 를 제자리에서 고쳐 쓰던 것은 반영 안 됨(양식을 바꾸려면 다른 폴더로 복사하고 `templateDir` 를 그 폴더로)
9. 추가 건의 바라는 결과 넷 — 내장 양식 · 실패 사유를 화면에 · 실패 사유를 로그에(범위는 설계가 정함) · 지정한 양식이 xlsx 가 아니면 읽을 수 있는 사유

설계가 정한 것(전부 [고정]):
- 추가 건 — **내장 양식은 `writeTemplate` 이 쓰는 것과 같은 바이트를 메모리에 만들어 다시 연다**(디스크 길과 같은 코드 길 → 결과가 같다는 것이 구조로 선다. 골든 `deliverable/filled-*.json` 불변) · 내장 양식은 **문서 번호**(01~11)로 고른다(결과 파일 이름은 매핑의 `file`)
- 추가 건 — 넷 갈래: (양식 내장 · 매핑 내장) 디스크 안 읽음 / (양식 내장 · 매핑 사용자 것) 사용자 매핑을 내장 양식에(안 맞으면 `Mapping.check` 글) / (양식 사용자 것 · 매핑 내장) 지금과 같되 매핑을 디스크에서 안 읽음 / (둘 다 사용자 것) 지금 그대로
- 추가 건 — **로그에는 예외 글을 안 남긴다**(규칙 3 · 기존 시험). 남기는 것: 작업 id·이름 · 예외 사슬의 클래스 단순 이름(셋까지) · 스택에서 `kr.ejg.toolbox.` 로 시작하는 첫 프레임의 `클래스.메서드:줄`. 글은 화면(`error`)으로만 간다
- 추가 건 — 화면 글은 `TB.jobText(j)` 한 곳(`FAILED` 이고 `error` 가 있으면 「실패 — <error>」, 아니면 지금 꼴) · xlsx 판정은 **첫 네 바이트 `PK\3\4`**(POI 예외 종류에 안 기댄다) · 사유 글에는 파일 경로만 싣는다(내용 없음)
- 추가 건 — 반입 zip 에 `templates/deliverable/example`·`mappings/deliverable/example.yaml` 은 **계속 넣는다**(발주처 양식을 만들 때 복사할 견본 — 안 읽으니 암호화돼도 무해). `package.sh` 불변 · `make-templates` 불변
- 1-84 ① 의 과녁은 **Oracle JDBC 드라이버가 같은 딕셔너리에서 내는 값**이다(`TiberoMetaSource` 는 `OracleMetaSource` 를 물려받고, 뒤 단계 — DTO·DDL 생성·diff — 는 그 값으로 시험돼 있다). Oracle 컨테이너에서 「Tibero 길의 열 = Oracle 드라이버 길의 열」 을 `--db` 가 잰다. **매핑이 드라이버와 다르면 표를 드라이버 값에 맞춘다**(시험이 심판)
- 딕셔너리 SQL 이 실패하면(뷰·열 없음·권한) `vendor("columns", …)` 가 경고 `columns` 를 남기고 드라이버 길(길 ② 포함)로 물러선다. 표에 줄이 0 이면 경고 없이 드라이버 길
- `OracleMetaSource` 는 안 바꾼다(Oracle 은 드라이버 길 그대로 — `meta/oracle.json` 불변)
- 길 ② 의 경고 종류는 `dupColumns`(SQLState 빈칸 · 코드 0 · 건수 = 버린 줄 수)
- ② — **기본형으로 받는 선언도 호출만 남긴다**(언박싱 NPE 가 사라지는 것은 동작 변화로 안 본다) · 초기화식 어디든 **람다·메서드 참조가 있으면 건너뜀**(대입 대상 형식이 사라지면 추론이 달라져 컴파일이 깨질 수 있다) · 호출만 남길 때는 「블록에 이 선언뿐」 을 안 본다(문장이 남는다)
- ② 남는 import — **그대로 둔다**. 번들 76 의 `MAY_APPEAR`(`local` 단계는 `java.unusedImport` 가 새로 서도 채택 — 다음 실행의 delete 단계가 지운다)가 그대로 받는다. 실측으로 egov 42 는 남는 import 0
- ② 건너뜀 글 — 「초기화식이 리터럴이 아니다 — …」 를 「초기화식이 리터럴도 호출 하나도 아니다 — 지우면 동작이 바뀔 수 있다」 로 바꾼다(거짓이 된 글. 골든의 그 글이 같이 바뀐다)
- ③ 규칙 — id `jsp.blankNoopener` · group `jsp` · severity `warn`(tsx 짝과 같게) · kind `blankRel` · 글롭 `*.jsp *.jspf *.tag *.html *.htm`(`a11y.htmlLang` 과 같다) · 글·fix 는 tsx 짝과 같은 글. 판정은 **rel 속성이 없을 때만**(tsx 짝과 같다 — 값은 안 본다). href 는 안 본다(`#`·`javascript:` 도 지적). 태그마다 한 건(`JsxLabelRule` 과 같다 — 한 줄에 둘이면 두 건). 주석·script 블록·스크립틀릿 안은 안 본다. 규칙 138 → **139**
- ③ 고침 — 기본 켬(`RULES` 19 → **20**, `OPTIONAL` 불변 → 켜진 16) · rel 따옴표는 **target 값의 따옴표를 따른다**(`'_blank'` → `rel='noopener noreferrer'`) · 따옴표 없는 `target=_blank` 는 건너뜀 「따옴표 없는 target — 손으로」 · 여러 줄 태그도 고친다(target 이 있는 줄에 끼운다)
- 0-78 — tools 레인 = `scripts .claude/settings.json .claude/prompts .github bundle README.md pom.xml run.bat toolbox.bat .gitignore`. 레인이 겹쳐도 된다(`scripts/package.sh` 전례). README·pom 만 고쳐도 `hooks-test.sh` 가 돈다

실행자는 Opus. 차례 `S0` → `0-78` → `0-77` → `5-73a` → `5-73b` → `5-74` → `5-75a` → `5-75b` → `1-84a` → `1-84b` → `1-84c` → `2-39` → `2-40` → `0-79` → `1-85` → `V-57a` → `R-24` → `V-57` → 마무리. 가지 `work/2026-11-09b`(지금 가지 — 번들 76 닫기 `8c27627`·`ca61fbe` 가 로컬에만 있다, 그 위에 친다).

---

## 1. 목표와 범위
- 한 문장: Tibero 스냅샷이 같은 이름 인덱스가 있어도 찍히고, 산출물이 디스크의 예시 양식 없이도 나오며 작업이 실패하면 사유가 화면에 뜨고, 코드 정리가 안 쓰는 변수의 호출을 남기며 선언을 떼고, JSP·HTML 의 rel 없는 새 창 링크를 지적·고치고, 번들 76 이 남긴 잔손 셋을 닫는다.
- 완료 시 체감: 예시 양식 xlsx 가 암호화돼 있거나 없어도 산출물 01~11 이 나온다(프로필 수정 없이). 어느 화면이든 작업이 실패하면 결과 칸에 「실패 — <사유>」 가 뜨고 run.bat 로그에 「작업 실패 <id> <이름> — <예외 클래스> @ <자리>」 가 남는다. 발주처 양식 폴더의 파일이 xlsx 가 아니면 「양식이 xlsx 가 아니다: <파일> — 문서보안(DRM) 암호화 여부를 확인한다」. Tibero 「선택한 접속으로 찍기」 가 PK 위반 없이 끝난다(열은 딕셔너리에서 읽는다). 코드 정리 미리보기에서 `LoginVO _loginVO = egovAssertLoginUser();` 가 `egovAssertLoginUser();` 로 바뀐다. 코드 검사 JSP 묶음에 `jsp.blankNoopener` 가 뜨고 코드 정리 체크박스가 19 → 20(켜진 16). `bundle/`·README 만 고쳐도 로컬 `verify.sh` 가 `hooks-test.sh` 를 돌린다.
- In scope: `core/report/XlsxFiller`·`Mapping`(`parse`) · `core/deliverable/Forms`·`DeliverableService`(도우미 하나) · `cli/MakeTemplates`(상수 참조) · `web/DeliverableRoutes.postBuild`(두 줄·도우미 둘) · `core/job/JobManager`(로그 한 줄·도우미) · `tools/common.js`(`TB.jobText`) + 폴링 화면 다섯 파일 여섯 줄 · `profiles/example.yaml`·`site-ora.yaml`·`site-tibero.yaml`(주석) · `core/meta/JdbcMetaSource`·`MetaSource`(javadoc) · `core/dialect/TiberoMetaSource` · `tools/db_browser.html`(글 한 줄) · `core/check` 새 `BlankRelRule` · 수정 `LocalFix`·`MarkupFix`·`LogFix`·`RuleSet.build`(case 한 줄)·`Cleanup`(`RULES`·`clean` 한 줄) · `resources/check/rules.yaml`(규칙 하나) · `tools/dev_tools.html`(주석 한 줄) · `scripts/verify-fingerprint.sh`·`hooks-test.sh`·`bundle-fetch.sh` · `bundle/drivers-alt/pom.xml`(주석) · `docs/pure-code-check.md`(한 마디) · 픽스처·시험·골든(행마다) · `design/compare-2026-10-28.md` ㉖ 처분·㉗
- Out of scope: 「범위 밖」 전부 · `pure/` · `OracleMetaSource`·`VendorMetaSource`·`MariaMetaSource`·`SnapshotStore`·`SnapshotService` · `core/gen`·`core/analyze`·`core/vcs` · 정규식 규칙의 `regex`·`globs`·`message` · `Cleanup` 의 `worse`·`commit`·단계 차례 · `LocalsAst`(판정 불변) · `VerifyLanesTest` · 새 의존성 · 마이그레이션

## 2. 현재 구조 요약
「맥락」 의 보정·실측이 요약이다. 덧붙이는 함정:
- **행마다 `bash scripts/mvn.sh -q -DskipTests verify` 를 먼저** 돌린다 — 빠른 verify 는 PMD·SpotBugs 를 안 본다. 새 클래스·새 메서드는 PMD 기준선 밖: `AvoidLiteralsInIfCondition`(수·글자는 상수로) · `UseLocaleWithCaseConversions` · `LiteralsFirstInComparisons` · `LooseCoupling` · 시험 `AvoidDuplicateLiterals`(같은 글 넷 이상은 상수). 정규식은 SpotBugs REDOS — 중첩 반복·`.*?` 로 블록을 잡지 않는다(블록은 `indexOf` 로 훑는다, `RegexRule.blankCommentScriptlets:83` 전례)
- `Cleanup.Edits.replace(줄, 글)` 은 **첫 쓰기가 이긴다** → `MarkupFix` 는 한 줄에 쓸 것을 다 합친 뒤 한 번 쓴다. `Edits` 는 줄 단위라 여러 줄 태그의 rel 은 target 이 있는 **그 줄**을 바꾼다(지적 줄과 다를 수 있다 — `worse` 는 대상 규칙 건수 합으로 본다)
- `Cleanup.worse` — 고친 줄 위 지적은 규칙 이름 다중집합으로 견준다. `TOUCHED_MAY_VANISH` 에 `LOCAL → *` 가 있어 호출만 남긴 줄의 다른 지적(`java.naming` 등)이 사라져도 된다. `markup` 은 항목이 없다 — rel 을 끼운 줄의 다른 지적은 그대로여야 한다(글을 끼우기만 하므로 그대로다)
- `CleanupTest.cleanup():34` 는 `Cleanup.RULES` 전부·모든 묶음을 켠다 → 새 cleanup 픽스처는 다른 규칙 지적도 `steps.json` 의 before·after 에 센다(수는 실행이 적는다)
- `CleanupTest.compile()` 은 글을 `Pos.java` 로 쓰고 JDK 만으로 javac → `cleanup/local/Pos.java` 에 더하는 클래스는 package-private·JDK 형식만
- `CheckRulesTest.fixturesGolden:77-109` — `neg*` 파일에 **어느 규칙이든** 지적이 나면 빨강 · 켜진 규칙마다 양성 한 건 이상 · `fixtures.json` 은 `{file,line,rule}` 줄. `PureCodeCheckTest:49`(corpus)는 `common·jsp·tsx·security·a11y` 픽스처 **파일 수 32** 를 건다 → check 픽스처는 새 파일을 만들지 않고 `jsp/pos.jsp`·`jsp/neg.jsp` **끝에 덧붙인다**
- `RegexRule.stripComments(text, lang)` 은 길이를 지킨다(주석을 공백으로) — `JsxLabelRule:37-40` 처럼 길이가 다르면 원문으로 물러선다
- `JdbcMetaSource.loadColumns` 는 열 번호 차례로 한 번씩 읽는다(Oracle `COLUMN_DEF` 는 LONG — 뒤에 앞 열을 읽으면 ORA-17027). 딕셔너리 SQL 도 `DATA_DEFAULT`(LONG)를 **맨 끝 열로 두고 맨 끝에** 읽는다
- `VendorFallbackTest.oracleSourceOnH2FallsBackToJdbcWithWarnings:62` 는 Oracle 수집기의 경고 종류 집합을 **정확히** 건다 → `columns` 는 Tibero 에서만 나야 한다(`OracleMetaSource` 불변)
- `db_browser.html:323` — 경고가 하나라도 있으면 완료 칸이 노랑. 길 ① 이 서면 Tibero 에서는 `dupColumns` 가 안 난다
- `scripts/hooks-test.sh` 는 `verify-fingerprint.sh` 를 임시 저장소(`src/A.java`·`.claude/settings.json`·`pom.xml`·`scripts/`)에 복사해 쓴다 — 없는 경로는 `-` 로 해시된다(지금도 `docs`·`.github` 가 그렇다). 훅 경우들은 `src/A.java` 만 바꾸고 도장은 지문 전체를 쓴다 → tools 레인에 `pom.xml`·`README.md` 가 들어도 기대가 안 바뀐다(README·pom 을 바꾸는 `:207-211` 은 `offline-needed.sh` 용 다른 임시 저장소)
- Bash 도구의 heredoc 은 `\` 를 벗긴다 → 셸 스크립트 고침은 **Edit 도구로**
- 레인: `core/meta`·`core/dialect`·`web/DbCorpusBase` = db(+corpus) · `core/check`·`resources/check`·`resources/tools`·`golden/corpus` = java+corpus · `fixtures/**` = java · `scripts`·(0-78 뒤)`bundle`·`README.md` = tools · `docs`·`PLAN.md` = docs · `design/**` = 없음

## 3. 설계 결정 — 전부 [고정]

### 0-78 — tools 레인 = `hooks-test.sh` 가 읽는 것 전부(§3-1)
- `scripts/hooks-test.sh` — 끝의 「고정 JDK 경로」 블록 뒤, `echo` 앞에(Edit 도구로):
```bash
# 0-78 — 이 시험이 읽는 저장소 파일("$R/…")은 전부 tools 레인 경로 아래다. 레인 밖이면 그 파일만 바뀐 커밋에서 로컬 verify 가 이 시험을 안 돌린다(번들 76 CI)
n=$((n + 1))
lane_tools=$(sed -n 's/^lane tools \(.*\)$/\1/p' "$R/scripts/verify-fingerprint.sh" | sed 's/#.*//')
out_of_lane=""
for p in $(grep -oE '"\$R/[A-Za-z0-9_./-]+' "$R/scripts/hooks-test.sh" | sed 's#^"\$R/##' | sort -u) .gitignore; do
  ok=0; for l in $lane_tools; do case "$p" in "$l"|"$l"/*) ok=1 ;; esac; done
  [ "$ok" = 1 ] || out_of_lane="$out_of_lane $p"
done
if [ -n "$lane_tools" ] && [ -z "$out_of_lane" ]; then echo "  [통과] $n 이 시험이 읽는 경로는 tools 레인 안"
else echo "  [실패] $n tools 레인 밖 경로를 읽는다:$out_of_lane"; fail=1; fi
```
  (`"$R/bundle/$1/pom.xml"` 은 `bundle/` 로 잡혀 `bundle/*` 에 맞는다 · `.gitignore` 는 `git check-ignore` 가 읽어 글에 안 보이므로 손으로 넣는다 · 이 블록의 grep 무늬 글은 `"\$R/` 라 자기 자신에 안 맞는다)
- `scripts/verify-fingerprint.sh` — `listed=(` 의 둘째 줄을 `scripts .claude/settings.json .claude/prompts .github bundle run.bat toolbox.bat .gitignore` 로 · `lane tools` 줄을
  `lane tools scripts .claude/settings.json .claude/prompts .github bundle README.md pom.xml run.bat toolbox.bat .gitignore  # hooks-test.sh 가 읽는 것 전부 — 그 시험이 스스로 잰다(0-78)`
- TDD 예외(scripts) — 자기 검사를 먼저 넣고 `bash scripts/hooks-test.sh` 가 그 한 경우로 **빨강**(밖 경로 — `bundle/` `README.md` `pom.xml` `run.bat` `toolbox.bat` `.gitignore`)인 것을 본 뒤 레인을 고쳐 초록
- `VerifyLanesTest`·`verify.sh`·훅 셋·`package.sh`·`gate-probe` 패치는 안 고친다(패치 아홉 가운데 이 두 파일을 건드리는 것 0)

### 0-77 — Tibero 「Central 에 없다」 글 넷(§3-2)
- `scripts/bundle-fetch.sh:10` 「Tibero 드라이버는 Maven Central 에 없어 사람이 drivers/ 에 넣는다(없으면 경고만)」 → 「Tibero 드라이버는 현장 DB 판 jar 를 사람이 drivers/ 에 넣는다(Central 의 tbjdbc17 은 안 받는다, 없으면 경고만)」
- `:51` 경고 「Tibero 드라이버가 없다 — Maven Central 에 없어 사람이 drivers/ 에 넣는다(…)」 → 「Tibero 드라이버가 없다 — 현장 DB 판 jar 를 사람이 drivers/ 에 넣는다(벤더가 하나라 겹치지 않는다, bundle/SOURCES.md)」
- `bundle/drivers-alt/pom.xml:5` 「Tibero 는 Maven Central 에 없다 — 사람이 넣는다(SOURCES.md).」 → 「Tibero 는 현장 DB 판 jar 를 사람이 넣는다(SOURCES.md).」
- `PLAN.md:40` 「**Tibero 는 Maven Central 에 없다** — 개발자판…」 의 굵은 글을 「**Tibero 는 현장 DB 판 jar 를 사람이 넣는다**(Central 의 `tbjdbc17` 은 안 쓴다)」 로 · `PLAN.md:1223` 괄호 안 「Maven Central 에 없다. 」 를 「현장 DB 판. 」 로
- `ls drivers/tibero*.jar` 줄·지문·받는 jar·`MANIFEST`·`SOURCES.md` 불변(`hooks-test.sh:369-371` 이 그 글들을 건다)

### 5-73a — 마크업·로거 고침의 사유 글과 시험(§3-3)
- `MarkupFix.addLang:60-62` — `return line; // 이미 있다` 를 `skip(skips, no, "lang 속성이 이미 있다"); return line;` 으로(①. 정리 경로로는 안 닿는 가지 — 직접 부르는 시험이 건다)
- `MarkupFix.addRel` — 조각마다 `TARGET` 이 맞았는지를 `boolean sawTarget` 으로 모으고, 끝의 `if (!fixed)` 를 `skip(skips, no, sawTarget ? MULTI_LINE : TARGET_SHAPE)` 로. 새 상수 `private static final String TARGET_SHAPE = "target 꼴이 달라 못 고침";`(①. 규칙 정규식은 `target="_blank` 까지만 보고 고침은 닫힌 값만 받는다)
- `LogFix` — 새 상수 `private static final String NOT_CLASS = "인터페이스·enum·record — 로거 필드를 안 넣는다";`(`insertable:219` 의 글을 이 상수로) · `:107` 의 `.orElse(NO_LOGGER)` 를 `.orElseGet(() -> noShape(c))` 로 · 새 메서드
```java
    /** 5-73 ⑤ — 베낄 꼴이 없을 때도 설계 60 §3-9 표 차례: 클래스가 아니면 그 사유가 먼저 */
    private static String noShape(MethodCallExpr c) {
        return top(c) instanceof ClassOrInterfaceDeclaration k && !k.isInterface() ? NO_LOGGER : NOT_CLASS;
    }
```
  (`LogFix` 14 → 15. `apply` 의 분기 수 불변 — 람다에 분기 없음)
- `CleanupTest` 새 시험 둘(인라인 글 — 픽스처 파일 없음):
  - `markupReasons` — `Cleanup.Edits`·`MarkupFix.apply` 를 직접 부른다. ① 글 `"<html xml:lang=\"ko\">\n"` + 손으로 만든 `Finding(…, 1, "a11y", "a11y.htmlLang", "warn", "")` → skips `["줄 1: lang 속성이 이미 있다"]` · Edits 비어 있음 ② tsx 글 `"<a href=\"/o\" target={blank ? \"_blank\" : \"_self\"}>x</a>\n"` + `tsx.blankNoopener` 지적 → skips `["줄 1: target 꼴이 달라 못 고침"]` ③ 기존 `markupStage` 의 「줄 8: 여러 줄 태그」 는 그대로(안 고친다)
  - `logInsertRefusals` — `logSlotAndSystemLogger:266` 꼴(`new Cleanup(RuleSet.load(profile(null), null), Cleanup.RULES, "LF", rel -> Optional.of(sh))`)로 다섯 글, 글은 그대로·skips 는 한 줄씩:
    | 글(요지) | 로거 꼴 | 기대 사유 |
    |---|---|---|
    | `interface I { default void f() { System.out.println("a"); } }` | 있음 | `NOT_CLASS` 글 |
    | 같은 글 | 없음(`rel -> Optional.empty()`) | `NOT_CLASS` 글(⑤ — 전에는 「베낄 로거 꼴도 없다」) |
    | `class A extends B { void f() { logger.info("x"); System.out.println("a"); } }` | 있음(이름 `logger`) | 「물려받은 로거를 쓰는 것으로 보인다 — 손으로」 |
    | `import com.acme.Logger;` + `class C { Logger other; void f() { System.out.println("a"); } }` | 있음(slf4j) | 「로거 형식이 이 파일의 다른 것과 겹친다」 |
    | `class E { static class In { void f() { System.out.println("a"); } } }` | 있음 | 「빈 클래스 몸」 |
    글마다 package 줄·줄바꿈을 `logSlotAndSystemLogger` 처럼 두고 「줄 n」 은 그 글의 `System.out` 줄. 한 사유가 기대와 다르면 **글(시험 입력)을 그 가지에 닿게 고친다** — `LogFix` 의 다른 가지는 안 고친다
- 골든: `cleanup/steps.json`·`markup-*.txt`·`log-*.txt` diff 0(기존 픽스처에 새 사유가 서는 자리 없음)

### 5-73b — `ImportsTest` · `ccParse` 주석(§3-4)
- 새 `src/test/java/kr/ejg/toolbox/core/check/ImportsTest.java` — `new JavaSource().parse(글).getResult().orElseThrow()` 로 글을 읽어 표로 단언:
  - `has` — `import java.util.Objects;` 있음 → 참 · 없음 → 거짓 · `import java.util.*;` 만 → 거짓 · `java.util.Objects` 와 `com.acme.Objects` 둘 다 → 거짓(겹침)
  - `clash` — `com.acme.Objects` 만 있을 때 `java.util.Objects` 에 참 · 같은 fqn 만 → 거짓 · static import → 거짓
  - `used` — import 없이 `Objects.hash(1)`(NameExpr) → 참 · `Objects o;`(형식) → 참 · `java.util.Objects o;`(scope 있음)만 → 거짓
  - `line` — import 둘이면 마지막 import 줄 · import 없고 package 만 → package 줄 · 둘 다 없음 → 0
  - `text` — 필요한 둘 가운데 하나가 이미 있으면 한 줄 · 둘 다 없으면 `"import a.B;\nimport c.D;"`(준 차례) · 다 있으면 빈 글
  - `simple` — `a.b.C` → `C`
- `tools/dev_tools.html:2062` 주석에서 `·"가"` 를 뺀다(③ — `CC_FORMS` 는 코드 꼴만 받는다. 코드 불변)

### 5-74 — 안 쓰는 지역 변수: 호출만 남기기(§3-5)
- `LocalFix` — 상수: `IMPURE` 글을 「초기화식이 리터럴도 호출 하나도 아니다 — 지우면 동작이 바뀔 수 있다」 로 · 새 `private static final String TYPED = "초기화식에 람다·메서드 참조 — 호출만 남기면 형식 추론이 달라질 수 있다";`
- `apply` 의 끝 가지:
```java
            String why = reason(v, st, g, lines[no - 1]);
            if (!why.isEmpty()) {
                skips.add(new Cleanup.Skip(Cleanup.LOCAL, "줄 " + no + ": " + why));
            } else if (v.getInitializer().filter(MethodCallExpr.class::isInstance).isPresent()) {
                e.replace(no, callOnly(v.getInitializer().orElseThrow(), g, lines[no - 1]));
            } else {
                e.delete(no);
            }
```
- `reason` — `SHARED` 판정 뒤, `IMPURE` 판정 **앞**에:
```java
        Optional<Expression> init = v.getInitializer();
        if (init.filter(MethodCallExpr.class::isInstance).isPresent()) {
            return typed(init.orElseThrow()) ? TYPED : ""; // 호출 문장이 남으니 「블록에 이 선언뿐」 은 안 본다
        }
```
- 새 메서드 둘(`LocalFix` 2 → 4):
```java
    /** 대입 대상 형식에 기대는 식 — 선언을 떼면 추론이 달라질 수 있다 */
    private static boolean typed(Expression init) {
        return init.findFirst(LambdaExpr.class).isPresent() || init.findFirst(MethodReferenceExpr.class).isPresent();
    }

    /** 선언 머리(형식 이름 =)를 떼고 호출과 그 뒤(; · 꼬리 주석)를 남긴다 — 들여쓰기는 그대로 */
    private static String callOnly(Expression init, Range g, String line) {
        Range i = init.getRange().orElseThrow();
        return line.substring(0, g.begin.column - 1) + line.substring(i.begin.column - 1, i.end.column) + line.substring(g.end.column - 1);
    }
```
  (한 줄 선언만 여기 온다 — `MULTI` 가 앞에서 거른다. `g.end.column` 은 `;` 자리)
- 「호출 하나」 = 초기화식 **꼭대기가 `MethodCallExpr`**(`f()`·`a.b().c()`·`this.f(x)`·`T.<X>g()`). 괄호로 싼 것·캐스트·`new`·삼항·이항식·배열 접근·이름은 아니다(기존 `pure` 판정 그대로 → `IMPURE` 또는 삭제)
- `rules.yaml` 불변(fix 글이 이미 「초기화식에 호출이 있으면 호출만 남긴다」) · `LocalsAst`·`Cleanup` 불변
- 픽스처 `fixtures/cleanup/local/Pos.java` **끝에 덧붙인다**(빈 줄 하나 뒤, 52줄부터 — 기존 51줄 불변):
```java

/** 설계 61 — 호출만 남기기: 호출 하나(기본형·사슬+꼬리 주석·객체·블록에 홀로) · 건너뜀(new·캐스트·삼항·람다·여러 줄) */
class Pos77 {
    private final List<String> xs = new java.util.ArrayList<>();

    void keep() {
        int n = xs.size();
        String s = xs.get(0).trim(); // 메모
        Object stamp = now();
        System.identityHashCode(this);
    }

    void alone() {
        try {
            run();
        } catch (RuntimeException e) {
            String m = e.getMessage();
        }
    }

    void skipped(Object o) {
        Object made = new Object();
        String cast = (String) o;
        String pick = o == null ? "" : o.toString();
        java.util.function.Supplier<String> sup = wrap(() -> "x");
        String multi = String.valueOf(
                o);
        System.identityHashCode(o);
    }

    Object now() {
        return null;
    }

    void run() {
    }

    <T> T wrap(T t) {
        return t;
    }
}
```
- `CleanupTest.localStage` 기대를 고친다(먼저 고쳐 RED):
  - 글 — 기존 단언에서 `t.contains("int n = xs.size();")` 를 빼고 더한다: `t.contains("        xs.size();\n")`(20줄과 58줄 — 둘 다) · `"        xs.get(0).trim(); // 메모\n"` · `"        now();\n"` · `"            e.getMessage();\n"` · 건너뛴 꼴 그대로 `"Object made = new Object();"`·`"String cast = (String) o;"`·`"String pick ="`·`"sup = wrap("`·`"String multi = String.valueOf(\n"`
  - steps — `Step(LOCAL, UNUSED_LOCAL, 18, 9)`(전 9 + 새 9 · 남는 것 — 21·26·31·40 과 73·74·75·76·77 줄)
  - skips — `["줄 21: " + IMPURE 새 글, "줄 26: 한 문장에 선언 둘", "줄 31: 여러 줄 선언", "줄 40: 블록에 이 선언뿐 — 지우면 빈 블록", "줄 73: " + IMPURE, "줄 74: " + IMPURE, "줄 75: " + IMPURE, "줄 76: " + TYPED 글, "줄 77: 여러 줄 선언"]`(덧붙인 글의 줄 — 52 빈 줄 · 53 javadoc · 54 `class Pos77` · 58 `n` · 59 `s` · 60 `stamp` · 68 `m` · 73 `made` · 74 `cast` · 75 `pick` · 76 `sup` · 77 `multi`)
  - `java.unusedImport` 1(Date) 그대로
  - 줄 번호는 위 글을 **그대로** 붙였을 때의 값이다. 실제와 다르면 픽스처가 아니라 **단언의 줄 번호**를 실제에 맞춘다(사유 글·차례·건수는 못 바꾼다 — 다르면 §7)
- 골든: `-Dgolden.update=true -Dtest=CleanupTest` → diff 가 `cleanup/local-Pos.java.txt`(20줄 `xs.size();` + `Pos77`) · `cleanup/steps.json` 의 `local/Pos.java` 항목뿐. `valueAndResourceCompile`(javac, `local` 포함) 초록 — 람다 줄을 건너뛰어야 컴파일된다(`wrap(() -> "x");` 만 남으면 javac 오류)
- 표본 기대(V-57a): `cleanup-egov.json` `local` 0 → `java.unusedLocal` 42 → 0 · `cleanup-roller.json` 5 → 1(`WeblogSharedTheme:254` 여러 줄) · skips 의 옛 `IMPURE` 글 사라짐

### 5-75a — `jsp.blankNoopener` 판정 — 새 `BlankRelRule`(§3-6)
- `rules.yaml` ㄹ JSP 묶음 끝(`jsp.scriptlets` 뒤):
```yaml
  # 자바 규칙(설계 61) — 태그를 훑어 여러 줄 태그·속성 값 안의 JSP 태그(href="<c:url …/>")도 본다. 순수본에는 없다(tsx 짝은 tsx.blankNoopener)
  - id: jsp.blankNoopener
    group: jsp
    severity: warn
    kind: blankRel
    globs: ['*.jsp', '*.jspf', '*.tag', '*.html', '*.htm']
    message: target=_blank 에 rel 이 없다(새 탭이 원래 창을 바꿀 수 있다)
    fix: 'rel="noopener noreferrer" 를 단다'
```
- `RuleSet.build:165-173` 에 `case "blankRel" -> new BlankRelRule(d);`
- 새 파일 `core/check/BlankRelRule.java`:
```java
/**
 * jsp.blankNoopener(설계 61) — JSP·HTML 의 target=_blank 인 {@code <a>} 에 rel 속성이 없으면 그 태그 첫 줄에 한 건.
 * 줄 단위 정규식(tsx.blankNoopener)이 못 보는 둘을 본다 — 여러 줄에 걸친 태그 · 속성 값 안의 JSP 태그({@code href="<c:url value='/x'/>"}).
 * 안 보는 것: 주석 · script 블록 · 스크립틀릿 안(문자열로 만드는 태그 — 고치면 그 문자열이 깨진다). 자바 규칙이라 순수본에는 없다
 */
public final class BlankRelRule implements Rule {
    private static final Pattern A_OPEN = Pattern.compile("(?i)<a(?=\\s)");
    private static final Pattern TARGET = Pattern.compile("(?i)(?<![\\w:-])target\\s*=\\s*");
    private static final Pattern REL = Pattern.compile("(?i)(?<![\\w:-])rel\\s*=");
    private static final String BLANK = "_blank";
    /** 태그 하나의 글자 상한 — 넘으면 못 닫은 것으로 본다 */
    private static final int LIMIT = 4000;

    private final Def ruleDef;
    // 생성자·def() 는 JsxLabelRule 과 같은 꼴

    /** 지적할 태그 — line 은 태그 첫 줄(1부터), at 은 target 속성 값이 끝난 글 자리(rel 을 끼울 곳), quote 는 값의 따옴표(없으면 0) */
    record Tag(int line, int at, char quote) {
    }

    @Override
    public List<Finding> apply(Source s) {
        String[] raw = s.text().split("\n", -1);
        List<Finding> out = new ArrayList<>();
        for (Tag t : tags(s.text(), s.lang())) {
            out.add(new Finding(s.rel(), t.line(), ruleDef.group(), ruleDef.id(), ruleDef.severity(), Finding.excerpt(raw[t.line() - 1])));
        }
        return out;
    }

    /** rel 없는 target=_blank {@code <a>} 들(글 차례) — MarkupFix 도 이것으로 자리를 찾는다 */
    static List<Tag> tags(String text, String lang) {
        String code = dead(text, lang);
        List<Tag> out = new ArrayList<>();
        Matcher m = A_OPEN.matcher(code);
        while (m.find()) {
            String attrs = attrs(code, m.end());
            Matcher t = attrs == null ? null : TARGET.matcher(attrs);
            if (t == null || REL.matcher(attrs).find() || !t.find()) {
                continue;
            }
            int v = m.end() + t.end();                       // 값 첫 글자
            char q = code.charAt(v);
            int open = q == '"' || q == '\'' ? 1 : 0;
            int after = v + open + BLANK.length();           // _blank 바로 뒤
            boolean closed = open == 1 ? code.charAt(after) == q : " \t\r\n/>".indexOf(code.charAt(after)) >= 0;
            if (code.regionMatches(true, v + open, BLANK, 0, BLANK.length()) && closed) {
                out.add(new Tag(lineOf(code, m.start()), after + open, open == 1 ? q : 0));
            }
        }
        return out;
    }

    /** 주석·script 블록·스크립틀릿·EL 을 공백으로(줄바꿈은 남긴다, 길이 불변) — 그 안의 {@code <a} 는 문자열이고, 값 안의 > 와 따옴표는 태그 훑기를 흩뜨린다 */
    static String dead(String text, String lang) {
        String code = RegexRule.stripComments(text, lang);
        StringBuilder sb = new StringBuilder(code.length() == text.length() ? code : text);
        String low = sb.toString().toLowerCase(Locale.ROOT);
        String find = low.length() == sb.length() ? low : sb.toString();   // 소문자로 길이가 바뀌는 글자가 있으면 원문으로
        blank(sb, find, "<script", "</script");
        blank(sb, find, "<%", "%>");
        blank(sb, find, "${", "}");
        return sb.toString();
    }

    private static void blank(StringBuilder sb, String find, String open, String close) {
        int i = 0;
        while ((i = find.indexOf(open, i)) >= 0) {
            int end = find.indexOf(close, i + open.length());
            end = end < 0 ? find.length() : end + close.length();
            for (int k = i; k < end; k++) {
                if (sb.charAt(k) != '\n') {
                    sb.setCharAt(k, ' ');
                }
            }
            i = end;
        }
    }

    /**
     * from(= {@code <a} 바로 뒤)부터 태그 끝까지 — 따옴표 값 안을 공백으로 바꾼 속성 글(끝의 > 포함, 길이 = 훑은 글자 수). 못 닫으면 null.
     * 끝은 따옴표 밖·중첩 태그 밖의 >. 값 안에 같은 따옴표의 JSP 태그가 겹쳐도({@code href="<c:out value="x"/>"}) 따옴표가 짝수라 끝이 맞는다
     */
    private static String attrs(String code, int from) {
        StringBuilder sb = new StringBuilder();
        char quote = 0;
        int depth = 0;
        for (int i = from; i < code.length() && i - from < LIMIT; i++) {
            char ch = code.charAt(i);
            boolean in = quote != 0;
            if (in) {
                quote = ch == quote ? 0 : quote;
            } else if (ch == '"' || ch == '\'') {
                quote = ch;
            } else if (ch == '<') {
                depth++;
            } else if (ch == '>') {
                if (depth == 0) {
                    return sb.append(ch).toString();
                }
                depth--;
            }
            sb.append(in && quote != 0 && ch != '\n' ? ' ' : ch);
        }
        return null;
    }

    private static int lineOf(String code, int at) { … JsxLabelRule.lineOf 와 같다 … }
}
```
  글자 리터럴(`'"'`·`'\''`·`'<'`·`'>'`·`'\n'`·`' '`·`" \t\r\n/>"`)·수(0·1)는 PMD 가 걸면 상수로 뺀다. 메서드마다 복잡도 ≤ 15(`tags` 가 넘으면 값 판정을 `private static Tag blank(String code, int tagStart, int valueAt)` 로 뺀다)
- check 픽스처 — **끝에 덧붙인다**(새 파일 없음):
  - `jsp/pos.jsp` 5·6·7줄: `<a href="<c:url value='/x.do'/>" target="_blank">새 창</a>` / `<a class="b"` / `   target="_blank" href="/y.do">여러 줄</a>` → 지적 5줄·6줄
  - `jsp/neg.jsp` 6·7·8줄: `<a href="/x.do" target="_blank" rel="noopener noreferrer">있음</a>` / `<a href="/y.do" target="_self">같은 창</a>` / `<%-- <a href="/z.do" target="_blank">주석</a> --%>`
  - 덧붙인 줄에 **다른 규칙**이 서면(양성 줄은 `fixtures.json` 에 새 규칙 밖 줄이 생김 · 음성 줄은 `fixturesGolden` 빨강) 그 줄의 글(링크 글·속성)을 바꿔 새 규칙만 남긴다 — 줄 수·꼴(값 안 JSP 태그 · 여러 줄)은 유지
- 새 `BlankRelRuleTest`(인라인 — `BlankRelRule.tags(글, "jsp")` 의 `line`·`quote` 를 본다):
  - 지적: 맨 꼴 · `href="<c:url value='/a'/>"` 뒤 target · `href="<c:out value="${v}"/>" target='_blank'`(quote `'`) · 여러 줄(1줄에 `<a`, 3줄에 target → line 1) · 대문자 `<A HREF="/x" TARGET="_BLANK">` · `target=_blank>`(quote 0) · `<a href="/x" <c:if test="${n}">target="_blank"</c:if>>` · 한 줄 둘(두 건) · `href="<%= url %>" target="_blank"`
  - 지적 아님: `rel="nofollow"`(값은 안 본다) · rel 이 여러 줄 태그의 다음 줄 · `target="_self"` · `data-target="_blank"` · `th:target="_blank"` · `target="${t}"` · `<script>var h = "<a href='/z' target=_blank >";</script>` · `<%-- … --%>`·`<!-- … -->` 안 · `<% out.print("<a target=\"_blank\">"); %>` · 안 닫힌 태그(`<a target="_blank"` 로 글 끝) · `<abbr target="_blank">` · target 없는 `<a href="/x">`
  - `at` — 맨 꼴 한 건에서 `글.substring(0, at)` 이 `target="_blank"` 로 끝난다
- `CheckRulesTest:167` 138 → **139** · `PureRulesJsonTest` 63 그대로 · `docs/pure-code-check.md:13` 의 「ㄹ JSP 3」 뒤에 「(`jsp.blankNoopener` 는 백엔드 자바 규칙 — 설계 61)」 한 마디
- 골든: `-Dgolden.update=true -Dtest=CheckRulesTest` → `check/fixtures.json` diff 가 **`jsp/pos.jsp` 5·6줄 `jsp.blankNoopener` 두 줄뿐** · `pure-rules.json`·`self-check.txt`·`java.json`·`golden/cleanup/*` diff 0(이 행에서는 `Cleanup.RULES` 에 없어 정리가 안 건드린다 — 단 `steps.json` 의 기존 JSP·HTML 픽스처 before·after 가 바뀌면 §7. 실측 0)
- 우리 화면: `SelfCheckTest.mainMatchesBaseline`·`ToolsA11yTest` 그대로 초록(실측 지적 0)

### 5-75b — `_blank` 고침 — `MarkupFix` 가 태그 자리에 rel(§3-7)
- `MarkupFix` — 상수 `static final String BLANK_JSP = "jsp.blankNoopener";` · `private static final String BARE = "따옴표 없는 target — 손으로";` · `private static final String REL_WORDS = "noopener noreferrer";`(`REL` 글은 그대로 둔다 — tsx 길 불변)
- `apply` 를 「줄 → 고친 글」 로 모아 끝에 한 번씩 쓰게:
```java
    static void apply(Source s, List<Finding> cur, Set<String> targets, Cleanup.Edits e, List<Cleanup.Skip> skips) {
        String[] lines = s.text().split("\n", -1);
        Map<Integer, Set<String>> byLine = new TreeMap<>();
        cur.stream().filter(f -> targets.contains(f.rule())).forEach(f -> byLine.computeIfAbsent(f.line(), k -> new TreeSet<>()).add(f.rule()));
        Map<Integer, String> out = new TreeMap<>();
        if (targets.contains(BLANK_JSP)) {
            tagRel(s, lines, byLine, out, skips);
        }
        byLine.forEach((no, rules) -> {
            String line = out.getOrDefault(no, lines[no - 1]);
            line = rules.contains(BLANK) ? addRel(line, no, skips) : line;
            out.put(no, rules.contains(HTML_LANG) ? addLang(line, no, lines, skips) : line);
        });
        out.forEach((no, text) -> {
            if (!text.equals(lines[no - 1])) {
                e.replace(no, text);
            }
        });
    }

    /** jsp.blankNoopener — 지적이 선 태그마다 target 값 바로 뒤에 rel(값과 같은 따옴표). 뒤에서부터 끼워 같은 줄 앞쪽 자리가 안 밀린다 */
    private static void tagRel(Source s, String[] lines, Map<Integer, Set<String>> byLine, Map<Integer, String> out, List<Cleanup.Skip> skips) {
        String text = s.text();
        List<BlankRelRule.Tag> tags = new ArrayList<>(BlankRelRule.tags(text, s.lang()));
        java.util.Collections.reverse(tags);
        for (BlankRelRule.Tag t : tags) {
            if (!byLine.getOrDefault(t.line(), Set.of()).contains(BLANK_JSP)) {
                continue; // 억제 표식으로 눌린 태그
            }
            if (t.quote() == 0) {
                skip(skips, t.line(), BARE);
                continue;
            }
            int start = text.lastIndexOf('\n', t.at() - 1) + 1;                    // target 이 있는 줄의 첫 자리
            int no = 1 + (int) text.substring(0, start).chars().filter(c -> c == '\n').count();
            String line = out.getOrDefault(no, lines[no - 1]);
            int col = t.at() - start;
            out.put(no, line.substring(0, col) + " rel=" + t.quote() + REL_WORDS + t.quote() + line.substring(col));
        }
    }
```
  (`MarkupFix` 5 → 6. `addLang`·`addRel` 은 5-73a 꼴 그대로)
- `Cleanup` — `RULES` 끝에 `"jsp.blankNoopener"`(20) · `clean` 의 markup 줄 `Set.of(MarkupFix.HTML_LANG, MarkupFix.BLANK, MarkupFix.BLANK_JSP)`. 메서드 29 그대로 · `OPTIONAL`·`TOUCHED_MAY_VANISH`·`worse` 불변 · `code_cleanup_ext.js` 불변(단계 `markup` 라벨이 있다)
- cleanup 픽스처 새 파일 `fixtures/cleanup/markup/blank.jsp`:
```jsp
<%@ page contentType="text/html; charset=UTF-8" %>
<html lang="ko">
<head><title>새 창</title></head>
<body>
<a href="https://example.org/" target="_blank">밖</a>
<a href="<c:url value='/a.do'/>" target="_blank" title="새 창">안</a>
<a href="<c:out value="${vo.url}"/>" target='_blank'>값</a>
<a class="x"
   href="/b.do"
   target="_blank">여러 줄</a>
<a href="/c.do" target="_blank">하나</a> <a href="/d.do" target="_blank">둘</a>
<a href="/e.do" target="_blank" rel="noopener">있음</a>
<a href="/f.do" target=_blank>맨 값</a>
<script>var h = "<a href='" + u + "' target=_blank >x</a>";</script>
<%-- <a href="/g.do" target="_blank">주석</a> --%>
</body>
</html>
```
- `CleanupTest` 새 `blankRelStage` — `clean("markup", "blank.jsp", data)`:
  - 글에 있다: `target="_blank" rel="noopener noreferrer">밖` · `target="_blank" rel="noopener noreferrer" title="새 창">안` · `target='_blank' rel='noopener noreferrer'>값` · `   target="_blank" rel="noopener noreferrer">여러 줄` · `target="_blank" rel="noopener noreferrer">하나</a> <a href="/d.do" target="_blank" rel="noopener noreferrer">둘` · 그대로 `target="_blank" rel="noopener">있음` · `target=_blank>맨 값` · script 줄·주석 줄 원문
  - `Step(MARKUP_STAGE, "jsp.blankNoopener", 7, 1)` · skips(markup) `["줄 13: 따옴표 없는 target — 손으로"]`
- 기존 `markupStage`(tsx·html lang) 단언 불변 — `apply` 를 고쳐 쓴 것이 순수 재배치인지의 강제 지점
- 수 — `SmokeHtmlUnitTest:1827-1828` 19·15 → **20·16** · `CleanupRoutesTest:107` 19 → **20**(opt 목록 `:118` 불변)
- 골든: `-Dgolden.update=true -Dtest=CleanupTest` → 새 `cleanup/markup-blank.jsp.txt` + `steps.json` 의 `markup/blank.jsp` 항목뿐(기존 `markup-*.txt` 넷 diff 0)
- 표본 기대(V-57a): `cleanup-egov.json` markup 에 `jsp.blankNoopener` 16 → 0 · `cleanup-roller.json` 2 → 0 · `cleanup-jspwiki.json` 2 → 0(여러 줄 태그)

### 1-84a — 길 ②: 같은 이름 열은 첫 줄만 + 경고 `dupColumns`(§3-8)
- `JdbcMetaSource` — 필드 `private int dupColumns;`(javadoc 「1-84 — 드라이버가 같은 이름으로 겹쳐 준 열 줄 수. 경고 dupColumns(SQLState 빈칸)」) · `loadColumns` 끝을 `return t.withColumns(distinct(cols));` 로(정렬 뒤) · 새 메서드
```java
    /** 1-84 — 같은 이름의 열은 첫 줄만 둔다(Tibero 드라이버는 표와 이름이 같은 인덱스가 있으면 같은 열을 두 번 준다). 버린 줄 수는 경고로 */
    private List<Column> distinct(List<Column> cols) {
        Map<String, Column> first = new LinkedHashMap<>();
        for (Column c : cols) {
            first.putIfAbsent(c.name(), c);
        }
        dupColumns += cols.size() - first.size();
        return new ArrayList<>(first.values());
    }
```
  · `warnings()` 를 두 계수기 다 내게:
```java
        List<Warning> out = new ArrayList<>();
        if (unknownSorts != 0) { out.add(new Warning("sorts", "", 0, unknownSorts)); }
        if (dupColumns != 0) { out.add(new Warning("dupColumns", "", 0, dupColumns)); }
        return out;
```
- `MetaSource.Warning` javadoc 의 `@param kind` 에 `columns`(Tibero 열 딕셔너리)·`dupColumns`(드라이버가 겹쳐 준 열 줄 — 첫 줄만 둠) 를 더한다
- `tools/db_browser.html:303` 글 한 줄 — 「벤더 SQL 이 실패해 JDBC 값으로 물러섰다: 」 를 「벤더 SQL 이 실패해 JDBC 값으로 물러섰다(dupColumns 는 드라이버가 같은 열을 겹쳐 줘 첫 줄만 넣은 것): 」 로. 로직 불변
- `VendorFallbackTest` 새 시험 `duplicateColumnRowsKeepFirstAndWarn` + 도우미 `columnsTwice(Connection real)`(`sortSwapped:139-163` 꼴 — `getMetaData` → `getColumns` 의 `ResultSet` 을 Proxy 로 싸서 **`next()` 가 줄마다 두 번 참**을 준다: `boolean[] again` 하나로 「again 이면 false 로 돌리고 true 반환, 아니면 진짜 `next()` 결과를 again 에 넣고 반환」. 그 밖 메서드는 그대로 넘긴다):
  - H2 mem `CREATE TABLE T(ID INT PRIMARY KEY, NM VARCHAR(20))` → `new JdbcMetaSource(columnsTwice(real)).collect(…)` 의 `T` 열 이름 `[ID, NM]`(넷이 아니다) · `warnings()` 가 `[Warning("dupColumns", "", 0, 2)]`
  - 싸지 않은 연결로는 `warnings()` 빈 목록(기존 시험들이 이미 건다 — 새 단언 없음)
- `SnapshotStore`·`V*.sql` 불변(PK 는 그대로 — 중복이 저장까지 못 온다)

### 1-84b — 길 ①: `TiberoMetaSource` 가 열을 `ALL_TAB_COLUMNS` 에서(§3-9)
- `core/dialect/TiberoMetaSource.java`(클래스 javadoc 에 「열은 딕셔너리에서 직접(1-84) — 드라이버 getColumns 는 표와 이름이 같은 인덱스가 있으면 같은 열을 두 번 준다」 를 더한다):
```java
    /** 1-84 — DATA_DEFAULT 는 LONG 이라 맨 끝 열로 두고 맨 끝에 읽는다 */
    static final String TAB_COLUMNS = "SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, DATA_SCALE, NULLABLE, COLUMN_ID, CHAR_LENGTH,"
            + " DATA_DEFAULT FROM ALL_TAB_COLUMNS WHERE OWNER = ? AND TABLE_NAME = ? ORDER BY COLUMN_ID";
    private static final int TS_TZ = -101;        // oracle.jdbc.OracleTypes.TIMESTAMPTZ
    private static final int TS_LTZ = -102;       // TIMESTAMPLTZ
    private static final int INTERVAL_YM = -103;
    private static final int INTERVAL_DS = -104;
    private static final int BINARY_FLOAT = 100;
    private static final int BINARY_DOUBLE = 101;
    private static final int BFILE = -13;
    private static final int NO_SCALE = -127;     // NUMBER(정밀도·스케일 없음)의 드라이버 값
    private static final int MAX_PRECISION = 38;
    /** 딕셔너리 DATA_TYPE → Oracle JDBC 가 getColumns 로 주는 형식 번호. 과녁은 드라이버 값 — OracleMetaSourceTest 가 컨테이너에서 견준다 */
    private static final Map<String, Integer> TYPES = Map.ofEntries(
            Map.entry("CHAR", Types.CHAR), Map.entry("VARCHAR2", Types.VARCHAR), Map.entry("VARCHAR", Types.VARCHAR),
            Map.entry("NCHAR", Types.NCHAR), Map.entry("NVARCHAR2", Types.NVARCHAR), Map.entry("NVARCHAR", Types.NVARCHAR),
            Map.entry("NUMBER", Types.NUMERIC), Map.entry("FLOAT", Types.FLOAT), Map.entry("BINARY_FLOAT", BINARY_FLOAT),
            Map.entry("BINARY_DOUBLE", BINARY_DOUBLE), Map.entry("DATE", Types.TIMESTAMP), Map.entry("LONG", Types.LONGVARCHAR),
            Map.entry("RAW", Types.VARBINARY), Map.entry("LONG RAW", Types.LONGVARBINARY), Map.entry("BLOB", Types.BLOB),
            Map.entry("CLOB", Types.CLOB), Map.entry("NCLOB", Types.NCLOB), Map.entry("BFILE", BFILE),
            Map.entry("ROWID", Types.ROWID), Map.entry("UROWID", Types.ROWID), Map.entry("XMLTYPE", Types.SQLXML));
    private static final Set<Integer> CHARS = Set.of(Types.CHAR, Types.VARCHAR, Types.NCHAR, Types.NVARCHAR);
    private static final Set<Integer> BYTES = Set.of(Types.VARBINARY, Types.LONGVARCHAR, Types.LONGVARBINARY);

    @Override
    public Table loadColumns(Table t) throws SQLException {
        List<Column> cols = t.schema() == null ? List.of() : vendor("columns", List.of(), () -> dictionary(t));
        return cols.isEmpty() ? super.loadColumns(t) : t.withColumns(cols);   // 못 읽었거나 줄이 없으면 드라이버 길(같은 이름은 첫 줄만)
    }

    private List<Column> dictionary(Table t) throws SQLException {
        List<Column> out = new ArrayList<>();
        try (PreparedStatement ps = prepare(TAB_COLUMNS)) {
            ps.setString(1, t.schema());
            ps.setString(2, t.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(column(rs));
                }
            }
        }
        return out;
    }

    /** 한 줄 → Column. 열 번호 차례로 한 번씩 읽는다 */
    private static Column column(ResultSet rs) throws SQLException {
        String name = rs.getString(1);
        String type = rs.getString(2);
        Long bytes = longOrNull(rs, 3);
        Long precision = longOrNull(rs, 4);
        Long scale = longOrNull(rs, 5);
        boolean nullable = !"N".equals(rs.getString(6));
        int ordinal = rs.getInt(7);
        Long chars = longOrNull(rs, 8);
        String def = rs.getString(9);
        int jdbc = jdbcType(type);
        Long length = CHARS.contains(jdbc) ? chars : BYTES.contains(jdbc) ? bytes : null;
        boolean number = jdbc == Types.NUMERIC;
        return new Column(name, ordinal, type, jdbc, length, number ? precision(precision, scale) : null, number ? scale(precision, scale) : null,
                nullable, blankToNull(def == null ? null : def.strip()), null, null);
    }

    static int jdbcType(String dataType) {
        String t = dataType == null ? "" : dataType.toUpperCase(Locale.ROOT);
        Integer known = TYPES.get(t);
        if (known != null) {
            return known;
        }
        if (t.startsWith("TIMESTAMP")) {
            return t.contains("LOCAL") ? TS_LTZ : t.contains("TIME ZONE") ? TS_TZ : Types.TIMESTAMP;
        }
        if (t.startsWith("INTERVAL")) {
            return t.contains("YEAR") ? INTERVAL_YM : INTERVAL_DS;
        }
        return Types.OTHER;
    }

    /** 드라이버의 COLUMN_SIZE — 정밀도가 없으면 스케일도 없을 때 0, 스케일만 있으면 38 */
    private static Integer precision(Long precision, Long scale) {
        return precision != null ? Integer.valueOf(precision.intValue()) : Integer.valueOf(scale == null ? 0 : MAX_PRECISION);
    }

    /** 드라이버의 DECIMAL_DIGITS — 정밀도·스케일이 다 없으면 -127 */
    private static Integer scale(Long precision, Long scale) {
        return scale != null ? Integer.valueOf(scale.intValue()) : precision == null ? Integer.valueOf(NO_SCALE) : null;
    }
```
  `longOrNull` 은 `VendorMetaSource:189`(package-private static) · `blankToNull` 은 `JdbcMetaSource`(protected static) · 주석(comment)은 null — `OracleMetaSource.loadComments` 가 `ALL_COL_COMMENTS` 로 뒤에 채운다(이름으로 짝)
- `VendorFallbackTest` 새 시험 둘(H2 mem, 연결은 `ReadOnlyJdbc.wrap(columnsTwice(real))` — 읽기 전용 관문을 실제로 지난다):
  - `tiberoReadsColumnsFromDictionary` — `CREATE TABLE T(ID INT PRIMARY KEY, NM VARCHAR(20))` + 가짜 `CREATE TABLE ALL_TAB_COLUMNS(OWNER VARCHAR(30), TABLE_NAME VARCHAR(30), COLUMN_NAME VARCHAR(30), DATA_TYPE VARCHAR(40), DATA_LENGTH INT, DATA_PRECISION INT, DATA_SCALE INT, NULLABLE VARCHAR(1), COLUMN_ID INT, CHAR_LENGTH INT, DATA_DEFAULT VARCHAR(100))` 에 `('PUBLIC','T', …)` 일곱 줄:
    | COLUMN_NAME | DATA_TYPE | LEN | PREC | SCALE | NULL | ID | CHAR_LEN | DEFAULT | 기대(jdbcType · length · precision · scale · nullable · default) |
    |---|---|---|---|---|---|---|---|---|---|
    | ID | NUMBER | 22 | 10 | 0 | N | 1 | 0 | null | 2 · null · 10 · 0 · false · null |
    | NM | VARCHAR2 | 80 | null | null | Y | 2 | 20 | `'x' ` | 12 · 20 · null · null · true · `'x'` |
    | AMT | NUMBER | 22 | null | null | Y | 3 | 0 | null | 2 · null · 0 · -127 · true · null |
    | DT | DATE | 7 | null | null | Y | 4 | 0 | `SYSDATE` | 93 · null · null · null |
    | TS | TIMESTAMP(6) WITH TIME ZONE | 13 | null | 6 | Y | 5 | 0 | null | -101 · null · null · null |
    | BODY | CLOB | 4000 | null | null | Y | 6 | 0 | null | 2005 · null |
    | GEO | SDO_GEOMETRY | 1 | null | null | Y | 7 | 0 | null | 1111 · nativeType `SDO_GEOMETRY` |
    `new TiberoMetaSource(연결).collect(…)` 의 `T` → 열 이름 일곱(딕셔너리 차례 — 드라이버의 ID·NM 이 아니다) · 위 값 · `ordinal` 1~7 · `warnings()` 의 종류에 `columns`·`dupColumns` **없음**
  - `tiberoFallsBackWhenDictionaryFails` — `ALL_TAB_COLUMNS` 표 없이 같은 `T` → 열 `[ID, NM]`(드라이버 길 + 첫 줄만) · `warnings()` 에 `columns`(SQLState `42S02`) 와 `Warning("dupColumns", "", 0, 2)` 가 있다
- `OracleMetaSourceTest`(`@Tag("db")`) 새 시험 `tiberoColumnsMatchOracleDriver` — 임시 표 `ZZ_T84` 를 만들고 끝에 지운다(`metaMore` 처럼 표본 DDL 은 안 건드린다):
```sql
CREATE TABLE ZZ_T84 (N1 NUMBER, N2 NUMBER(*,2), N3 NUMBER(5) DEFAULT 1 NOT NULL, N4 NUMBER(12,2), F1 FLOAT, BF BINARY_FLOAT, BD BINARY_DOUBLE,
  D1 DATE DEFAULT SYSDATE, T1 TIMESTAMP, T2 TIMESTAMP(3) WITH TIME ZONE, T3 TIMESTAMP WITH LOCAL TIME ZONE, IY INTERVAL YEAR TO MONTH, ID1 INTERVAL DAY TO SECOND,
  C1 CHAR(3), C2 NCHAR(3), V1 VARCHAR2(10 CHAR), V2 VARCHAR2(10 BYTE) DEFAULT 'x', V3 NVARCHAR2(10), L1 CLOB, L2 NCLOB, L3 BLOB, R1 RAW(16), RI ROWID, LG LONG)
```
  스키마의 표마다(표본 여덟 + `ZZ_T84`) `new TiberoMetaSource(c).loadColumns(뼈대).columns()` 가 `MetaSources.forDialect("oracle", c).loadColumns(뼈대).columns()` 와 **같다**(`Column` record equals — 뼈대는 `listTables(owner)` 가 준 `Table`) · Tibero 수집기의 `warnings()` 에 `columns` 없음. **다르면 `TYPES`·`CHARS`·`BYTES`·`precision`·`scale` 을 드라이버 값에 맞춘다**(드라이버가 심판 — `ZZ_T84` 의 열을 빼서 통과시키지 않는다. 드라이버가 사용자 정의 형식에 2002·2003 을 주는 것처럼 딕셔너리 한 줄로 못 맞추는 형식만 열을 빼고 이력에 적는다)
- 돌리기: `bash scripts/docker-up.sh` → `bash scripts/mvn.sh -q test -Dgroups=db -Dtest=OracleMetaSourceTest -Djunit.timeout=10m`(Oracle 컨테이너 하나 — 여유 RAM 8GB 를 먼저 본다. 다른 세션이 컨테이너를 쓰는 중이면 SendMessage 로 묻고 기다린다. `wsl --shutdown` 은 안 낸다)
- `golden/meta/oracle.json`·`OracleMetaSource`·`MetaSources` 불변

### 1-84c — 표본 DDL 로 Tibero 길 대조(Oracle 23·11g)(§3-10)
- `web/DbCorpusBase.meta()` — `metaMore(snap, src, a);` 뒤에 한 줄 `tiberoColumns(a);` + 새 메서드:
```java
    /** 1-84 — Oracle 계열만: 딕셔너리에서 직접 읽는 Tibero 길의 열이 드라이버 길과 같다(Tibero 컨테이너가 없어 Oracle 딕셔너리로 잰다). 다르면 A */
    void tiberoColumns(List<String> a) throws SQLException {
        if (!"oracle".equals(dialect().meta)) {
            return;
        }
        kr.ejg.toolbox.core.meta.MetaSource driver = kr.ejg.toolbox.core.dialect.MetaSources.forDialect("oracle", conn);
        kr.ejg.toolbox.core.meta.MetaSource dict = new kr.ejg.toolbox.core.dialect.TiberoMetaSource(conn);
        for (kr.ejg.toolbox.core.meta.Table t : driver.listTables(names().schema())) {
            if (!driver.loadColumns(t).columns().equals(dict.loadColumns(t).columns())) {
                a.add(t.name() + " Tibero 길 열 ≠ 드라이버 길");
            }
        }
        if (dict.warnings().stream().anyMatch(w -> "columns".equals(w.kind()))) {
            a.add("Tibero 길 딕셔너리 SQL 물러섬 " + dict.warnings());
        }
    }
```
  (`MetaSource.listTables(String schema)` 는 그 스키마의 표 뼈대 `List<Table>` 를 준다(`MetaSource:19`). `DbCorpus.Dialect.ORACLE` 의 `meta` 는 `"oracle"` 이고 Oracle 23·11g 두 클래스가 같은 값을 쓴다(`DbCorpus:34`))
- 표본 DDL 이 드라이버가 2002·2003 을 주는 사용자 정의 형식 열을 가지면(실측 전 — 기대 0) 그 **열만** 비교에서 빼고(`jdbcType` 이 2002·2003 인 드라이버 열) 이력에 표·열을 적는다
- 돌리기: `bash scripts/mvn.sh -q test -Dgroups=db -Dtest=OracleCorpusTest -Djunit.timeout=10m`(컨테이너 하나). 11g 는 V-57 의 `--db` 가 잰다
- 골든 `golden/corpus/db-oracle*.json`·`*-meta-b.txt`·`*-meta-warnings.txt` diff 0(새 판정은 A 목록에만 선다)

### 2-39 — 산출물 내장 양식·내장 매핑 + xlsx 아님 사유(core)(§3-14)
- `core/report/XlsxFiller` — `fill` 을 셋으로 가른다(**기존 몸은 그대로 옮긴다** — `self-check.txt` 의 `fill(4)` 복잡도 25·중첩 5 가 그 메서드에 그대로 남는다):
```java
    /** xlsx(zip) 의 첫 네 바이트 */
    private static final byte[] ZIP = {'P', 'K', 3, 4};

    /** 디스크 양식 — 지정한 폴더의 파일. xlsx 가 아니면 POI 에 넘기기 전에 읽을 수 있는 사유로 멈춘다 */
    public static void fill(Path template, Mapping.DocMapping m, Doc d, Path out) throws IOException {
        requireXlsx(template);
        try (InputStream in = Files.newInputStream(template)) {
            fill(in, m, d, out);
        }
    }

    /** 내장 양식(설계 61) — writeTemplate 과 같은 바이트를 메모리에 만들어 연다. 디스크 xlsx 를 안 읽는다 */
    public static void fill(Form builtIn, Mapping.DocMapping m, Doc d, Path out) throws IOException {
        fill(new ByteArrayInputStream(templateBytes(builtIn)), m, d, out);
    }

    private static void fill(InputStream in, Mapping.DocMapping m, Doc d, Path out) throws IOException {
        try (Workbook wb = new XSSFWorkbook(in)) {
            … 기존 :34-115 그대로 …
        }
    }

    /** 문서보안(DRM)이 암호화한 파일은 zip 이 아니다 — 사유에는 경로만 싣는다 */
    private static void requireXlsx(Path template) throws IOException {
        if (!Files.isRegularFile(template)) {
            throw new IllegalArgumentException("양식 파일이 없다: " + template);
        }
        byte[] head;
        try (InputStream in = Files.newInputStream(template)) {
            head = in.readNBytes(ZIP.length);
        }
        if (!java.util.Arrays.equals(head, ZIP)) {
            throw new IllegalArgumentException("양식이 xlsx 가 아니다: " + template + " — 문서보안(DRM) 암호화 여부를 확인한다");
        }
    }
```
  · `writeTemplate(Form, Path)` 는 `Files.write(out, templateBytes(f))`(폴더 만들기는 그대로) · 새 `static byte[] templateBytes(Form f)` = 지금 `writeTemplate` 의 통합 문서 만들기 몸을 `ByteArrayOutputStream` 에 쓴 것(셀·모양·열 너비·틀 고정 불변)
- `core/report/Mapping` — `public static Mapping parse(String yaml) throws IOException`(`load` 와 같은 `ObjectMapper(new YAMLFactory())`·같은 docs null 검사, 글 「매핑에 docs 가 없다」). `load(Path)` 불변
- `core/deliverable/Forms` —
```java
    /** 예시 양식의 머리·첫 데이터 행(엑셀 행 번호) — make-templates 와 내장 매핑이 같이 쓴다 */
    public static final int HEADER_ROW = 2;
    public static final int FIRST_ROW = 3;
    /** 내장 양식·매핑이 찍혀 나가는 자리(make-templates 기본값). 프로필이 이것을 가리키면 디스크를 안 읽고 코드에서 만든다 */
    public static final String EXAMPLE_DIR = "templates/deliverable/example";
    public static final String EXAMPLE_MAPPING = "mappings/deliverable/example.yaml";

    /** 프로필 값이 비었거나 예시 자리(끝 구분자·./·절대 경로 꼴 포함)를 가리킨다 → 내장 */
    public static boolean builtIn(String configured, String example) {
        return configured == null || configured.isBlank()
                || Path.of(configured.trim()).toAbsolutePath().normalize().equals(Path.of(example).toAbsolutePath().normalize());
    }

    /** 내장 매핑 — 커밋된 예시 매핑 YAML 과 같은 글에서 읽는다 */
    public static Mapping mapping() throws IOException {
        return Mapping.parse(Mapping.exampleYaml(all(), HEADER_ROW, FIRST_ROW));
    }

    /** 문서 번호의 내장 양식 */
    public static Form of(String no) {
        return all().stream().filter(f -> f.no().equals(no)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("내장 양식이 없는 문서: " + no));
    }
```
- `cli/MakeTemplates` — `HEADER_ROW`·`FIRST_ROW` 를 `Forms.HEADER_ROW`·`Forms.FIRST_ROW` 로 잇는다(이름·값 불변 — 시험이 참조한다) · `@Option` 기본값 글 불변
- `core/deliverable/DeliverableService` — `build` 의 `:127` 한 줄을 `fill(templateDir, m, d, out);` 로 + 새 도우미(`build` 의 인자 8·복잡도 22 불변):
```java
    /** 양식 — templateDir 가 null 이면 내장 양식(Forms)을 메모리에서 만든다 */
    private static void fill(Path templateDir, Mapping.DocMapping m, Doc d, Path out) throws java.io.IOException {
        if (templateDir == null) {
            XlsxFiller.fill(Forms.of(d.no()), m, d, out);
        } else {
            XlsxFiller.fill(inside(templateDir, m.file()), m, d, out);
        }
    }
```
  `build` javadoc 에 「`templateDir` null = 내장 양식」 한 마디
- 시험(먼저 써서 RED):
  - `XlsxFillerTest.builtInFillEqualsDiskTemplate` — `Forms.all()` 열한 장마다 같은 `Doc`(열 = 그 양식의 열, 행 둘 — 글·수·빈칸)을 ① 디스크 양식(`TEMPLATES`) ② `fill(Form, …)` 로 기입해 `read()`(`:48-71`) 결과가 **같다** · 02·03·10 은 내장 길 결과도 기존 골든 `deliverable/filled-<no>.json` 과 같다(골든 불변)
  - `XlsxFillerTest.notXlsxSaysWhy` — 임시 폴더에 `x.xlsx`(바이트 `DRMFILE…`) → `IllegalArgumentException`, 글에 「양식이 xlsx 가 아니다」·`x.xlsx`·「문서보안」 · 없는 파일 → 「양식 파일이 없다」
  - `XlsxFillerTest.builtInMappingEqualsCommitted` — `Forms.mapping()` 이 `Mapping.load(MAPPING)` 과 같다(record equals) · `Forms.builtIn` 표: 참 — `null`·`" "`·`templates/deliverable/example`·`templates/deliverable/example/`·`./templates/deliverable/example`·`templates\\deliverable\\example\\`·그 절대 경로 / 거짓 — `templates/deliverable/site`·`templates/deliverable`
  - `DeliverableServiceTest.buildsWithoutTemplateDir` — 기존 시험의 스냅샷·요청 그대로 `templateDir = null`·`Forms.mapping()` → 파일 이름 목록이 디스크 길과 같다
- 골든·기준선: `golden/deliverable/*` diff 0 · `self-check.txt` diff 0(`:68·:70·:111·:112` 네 줄 그대로)

### 2-40 — 라우트: 없거나 예시 자리면 내장 + 프로필 주석(§3-15)
- `web/DeliverableRoutes.postBuild:238-246` — 두 삼항의 꼴(`&&` 하나·`?:` 하나씩)을 지켜 복잡도 23 을 그대로 둔다:
```java
        String templateDir = pd != null && !Forms.builtIn(pd.templateDir(), Forms.EXAMPLE_DIR) ? pd.templateDir() : null;
        String mappingFile = pd != null && !Forms.builtIn(pd.mapping(), Forms.EXAMPLE_MAPPING) ? pd.mapping() : null;
        kr.ejg.toolbox.core.report.Mapping mapping;
        try {
            mapping = mapping(mappingFile);
        } catch (java.io.IOException | IllegalArgumentException e) {
            ctx.status(400).json(Map.of("message", "매핑을 못 읽었다: " + mappingFile + " — " + e.getMessage()));
            return;
        }
```
  · `jobs.submit` 의 `java.nio.file.Path.of(templateDir)` 를 `dir(templateDir)` 로 · 새 도우미 둘:
```java
    /** null = 내장 매핑(Forms) */
    private static kr.ejg.toolbox.core.report.Mapping mapping(String file) throws java.io.IOException {
        return file == null ? kr.ejg.toolbox.core.deliverable.Forms.mapping() : kr.ejg.toolbox.core.report.Mapping.load(java.nio.file.Path.of(file));
    }

    /** null = 내장 양식 */
    private static java.nio.file.Path dir(String templateDir) {
        return templateDir == null ? null : java.nio.file.Path.of(templateDir);
    }
```
- `profiles/example.yaml:94`·`site-ora.yaml`·`site-tibero.yaml` 의 주석 한 줄 「# 발주처 양식 xlsx 가 든 폴더. 실제 양식은 저장소에 넣지 않는다 — templates/deliverable/<사업>/ 에 두고 여기를 고친다」 뒤에 한 줄을 더한다: 「# 비우거나 예시 폴더(아래 값)를 두면 내장 양식을 쓴다 — 그 폴더의 xlsx 는 읽지 않는다(고쳐도 반영 안 됨). mapping 도 같다」. 값 줄·`demo.yaml`·`docker-ora.yaml` 불변
- `DeliverableRoutesTest` 새 시험 둘(`up`·`job()` 꼴 그대로):
  - `exampleDirMeansBuiltIn` — 프로필에 `templateDir: templates/deliverable/example/`·`mapping: mappings/deliverable/example.yaml` 을 **명시** → 빌드 `DONE`·파일 11(지금 동봉 프로필 꼴의 회귀 시험)
  - `notXlsxTemplateFailsWithReason` — 임시 폴더에 내장 양식 이름(`Forms.of("01").file()` 등 열한 개)으로 쓰레기 바이트 파일을 두고 `templateDir` 를 그 폴더로 → 작업 `FAILED`·`error` 에 「양식이 xlsx 가 아니다」·「문서보안」
- 기존 `buildWritesElevenXlsx`·`deliverableFilter`·`doc18FromAnalyzeRun`(프로필에 `templateDir` 없음 → 이제 내장 길)이 **안 고치고** 초록이어야 한다 — 내장 길 결과가 디스크 길과 같다는 라우트 쪽 강제 지점
- `self-check.txt:121` `postBuild(7)` 복잡도 23 불변(달라지면 도우미로 분기를 옮겨 23 을 맞춘다 — 기준선을 안 고친다)

### 0-79 — 작업 실패를 로그에: 예외 클래스와 자리(글 없음)(§3-16)
- `core/job/JobManager.run` 의 `else` 가지(`:80-83`) 끝에 한 줄 `LOG.warn("작업 실패 {} {} — {}", job.id(), job.name(), where(e));` + 새 도우미:
```java
    /** 실패 자리 — 예외 사슬의 클래스 이름(셋까지)과 우리 코드 첫 프레임. 예외 글은 안 싣는다(사용자 코드·SQL·값이 섞인다 — 규칙 3) */
    static String where(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        for (int i = 0; t != null && i < CHAIN; i++, t = t.getCause()) {
            sb.append(i == 0 ? "" : " ← ").append(t.getClass().getSimpleName());
        }
        for (StackTraceElement f : e.getStackTrace()) {
            if (f.getClassName().startsWith(OURS)) {
                String c = f.getClassName();
                return sb.append(" @ ").append(c.substring(c.lastIndexOf('.') + 1)).append('.').append(f.getMethodName()).append(':')
                        .append(f.getLineNumber()).toString();
            }
        }
        return sb.toString();
    }
```
  상수 `private static final int CHAIN = 3;` · `private static final String OURS = "kr.ejg.toolbox.";`. 클래스 javadoc `:18` 을 「로그에는 작업 이름·id·상태, 실패면 예외 클래스 이름과 우리 코드 자리까지 — 본문 결과·예외 글은 남기지 않는다(…)」 로
- `JobEventRulesTest` — 기존 `failedJobMessageIsNotLogged` 는 **그대로 초록**이어야 한다(글 금지) · 새 `failedJobLogsWhere` — 같은 틀로 `IllegalStateException("비밀")` 을 던져 로그 줄 하나가 「작업 실패」·작업 id·`IllegalStateException`·` @ JobEventRulesTest.` 를 담고 「비밀」 은 어느 줄에도 없다 · `where(new RuntimeException("x", new java.sql.SQLException("SELECT 1")))` 가 `RuntimeException ← SQLException` 으로 시작하고 `SELECT` 가 없다
- 취소(`CANCELLED`)는 안 찍는다(실패가 아니다) · `Job`·SSE 페이로드·`/api/jobs` 응답 불변

### 1-85 — 작업 실패 사유를 화면에 — `TB.jobText`(§3-17)
- `tools/common.js` — `result` 근처에 함수 하나, `window.TB` 에 `jobText: jobText` 를 더한다:
```js
  /* 끝난 작업 한 줄 — 실패면 서버가 준 사유(error)를, 아니면 상태와 마지막 진행 글 */
  function jobText(j) {
    return j.status === 'FAILED' && j.error ? '실패 — ' + j.error : j.status + ' ' + (j.message || '');
  }
```
- 여섯 자리의 `j.status + ' ' + (j.message || '')` 를 `TB.jobText(j)` 로(그 줄의 다른 글·분기 불변): `deliverable_sql.html:941` · `code_check_ext.js:278` · `code_cleanup_ext.js:136` · `program_analysis_ext.js:131·:562` · `spring_source_generator_ext.js:186`. `deliverable_sql.html:940` 의 「중지 — 」 줄·`db_browser.html` 불변
- `ToolsFolderTest` 새 단언 — `tools/` 의 `.html`·`.js`(`common.js` 빼고) 어디에도 `j.status + ' ' +` 꼴이 없다(새 폴링 화면이 사유를 버리는 꼴로 못 돌아간다)
- `SmokeHtmlUnitTest` 새 `deliverableFailureShowsReason` — `logicalNameOpensFromDeliverable:893-960` 의 틀(프로필·스냅샷·생성 누르기)에 `templateDir` 만 쓰레기 xlsx 폴더(2-40 의 시험과 같은 꼴)로 → `#buildRes` 가 `res-fail` 이고 글에 「실패 — 」·「양식이 xlsx 가 아니다」 · 「TB.jobText({status:'FAILED', error:'e', message:'m'})」 = `실패 — e` · `{status:'CANCELLED', message:'m'}` = `CANCELLED m`
- TDD 예외(화면 JS) — 스모크 시험을 먼저 써서 RED(글이 `FAILED 01 데이터베이스 정의서`) → GREEN

### V-57a — corpus 골든 재채점(§3-11)
- 순서: `bash scripts/mvn.sh -q -DskipTests verify` rc 0 → `bash scripts/mvn.sh -q test -Dgroups=corpus -Dtest='CheckCorpusTest,LegacyCorpusTest,VcsCorpusTest,CliCorpusTest,JulietCorpusTest,SecurityBenchmarkCorpusTest,SqlRuleCasesCorpusTest,SqlFixtureCorpusTest,CleanupCorpusTest,CleanupCompileCorpusTest,JsCorpusTest' -Djunit.timeout=10m -Dgolden.update=true`(다른 세션이 큰 작업 중이면 미룬다) → `git diff --stat src/test/resources/golden/corpus`
- 기대 변화(이것만): `check-*.json`·`check-*-hits.txt` — 새 키 `jsp.blankNoopener`(설계 실측 egov 16 · jeecg 28 · jeesite 32 · ruoyi-classic 21 · jspwiki 2 · roller 2 · zheng 1 — 합 102) / `vcs-egov.json` findings·fullFindings / `cleanup-egov.json` — `local` 단계 `java.unusedLocal` 42 → 0 · markup 에 `jsp.blankNoopener` 16 → 0 · skips 의 옛 「리터럴이 아니다」 글 사라짐 / `cleanup-roller.json` — `local` 5 → 1 · markup 2 → 0 / `cleanup-jspwiki.json` — markup 2 → 0 / `security-benchmark.json`·`security-juliet*`(새 규칙이 걸리면 총 건수 칸만 — **채점 칸 불변**) / `js-summary.json`·`db-*`·`ddl-*`·`analyze-*` 불변
- 중단선: 한 표본의 `jsp.blankNoopener` 가 설계 실측과 3 넘게 다름(훑기를 그대로 옮겨 잰 값이다 — 다르면 구현이 §3-6 과 다르다) · `CleanupCompileCorpusTest` javac 오류 · `CleanupCorpusTest` 등급 A
- 건수 전후·벽시계를 이력에

### R-24 — 손 판정(§3-12)
- ② — `toolbox.bat cleanup <표본> --write --out <scratch 빈 폴더> --rules java.unusedLocal --profile example`(egov·jeecg·shopizer·roller·zheng, data 는 scratch, 본 뒤 지운다). 호출만 남긴 줄 **전부**(어림 83 — egov 42 는 한 꼴이라 10줄 + 나머지가 같은 글인지 grep) — 볼 것: 남은 문장이 호출 하나인가 · 들여쓰기·꼬리 주석 보존 · 그 파일에 새 `java.unusedImport` 가 섰나(건수) · 건너뜀 사유가 꼴과 맞나(캐스트 17 · new 10 · 삼항 3 · 여러 줄 7)
- ③ — CLI `check <표본> --groups jsp --json --profile example`(jeesite·jeecg·ruoyi-classic·egov·jspwiki·roller·zheng) 의 `jsp.blankNoopener` **전부**(102) 참·오탐 + `cleanup … --rules jsp.blankNoopener` 로 egov·jeesite·jspwiki 고침 꼴(rel 이 target 바로 뒤 · 겹친 따옴표 값 · 여러 줄 태그 · 한 줄 넷)
- 오탐·깨진 고침이면 음성 픽스처(또는 인라인 시험) 먼저 RED → 고침 ≤ 2 → V-57a 다시. 표는 compare ㉗ 「손 판정」, `파일:줄` 만(코드 본문 안 옮김)

### V-57 — compare ㉗ · 정적 분석 · `--corpus` · `--db`(§3-13)
- `design/compare-2026-10-28.md` 끝 `## ㉗ 재측정 — 번들 77(2026-11-09)`: 「설계 61. 올라간 후보 — ㉖ 「후보로」 ②③ · 1-84 · 0-77 · 0-78 · 5-73 · 현장 장애(산출물 내장 양식·작업 실패 사유)」 · 「손 판정」 표(R-24) · 「측정」 표(1-84 길 둘 · 산출물 양식 읽기 디스크 → 내장 · 실패 사유 화면 여섯 자리·로그 · 호출만 남기기 전후 · `jsp.blankNoopener` × 표본 · 규칙 138 → 139 · `RULES` 19 → 20) · 「골든」 · 「드러난 것」 · 「후보로」
- 규칙 수를 적은 현재형 문서 자리(`grep -n "138" README.md docs/*.md` 가운데 「규칙 138」 뜻인 줄)만 139 로
- 순서(따로 낸다): `bash scripts/mvn.sh -DskipTests verify` rc 0 → `bash scripts/verify.sh` → `bash scripts/verify.sh --corpus`(앞뒤로 다른 세션 알림) → projectshop 세션 확인(컨테이너를 쓰는 중이면 묻고 기다린다) · 여유 RAM 15GB 확인(모자라면 사용자에게 앱 내리기 요청) → `bash scripts/verify.sh --db` 혼자 → 끝 줄이 더 부르는 레인 없음
- 벽시계(V-57a·`--corpus`·`--db`)를 이력에

### 마무리
CLAUDE.md 「마무리」 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 절대 규칙 1·3·6·7(6 — Tibero 딕셔너리 SQL 이 `ReadOnlyJdbc` 를 지나는 SELECT 하나인가, 다른 자리에서 연결을 열지 않나 · 3 — 경고·건너뜀 글에 SQL·코드 본문이 안 실리나 · 7 — 고침이 결과 폴더에만 쓰나) · 행의 축·강제 지점과 diff · **컴파일이 깨질 길**(호출만 남긴 문장 — 람다·메서드 참조·제네릭 추론 · rel 을 끼운 자리가 JSP 태그·EL·문자열 안인 경우) · `BlankRelRule` 이 못 닫는 태그·겹친 따옴표에서 엉뚱한 자리를 내는 꼴 · Tibero 매핑이 드라이버와 갈리는 형식(시험에 없는 것) · 내장 양식 길이 디스크 길과 갈리는 자리 · 로그에 예외 글이 새는 길(`LOG.*(…, e)`) · 화면이 `error` 를 HTML 로 넣는 자리 · 새 판정마다 양성·음성 · 골든 갱신이 §3 목록 안인가 · tools 레인 자기 검사가 빠뜨리는 읽기 꼴) → `gate-probe.sh` → `verify.sh`(끝 줄) → push → PR(본문에 행마다 시작 세 줄 표) → CI `gh api` 폴링 → claude[bot] 처분 → **머지 위임 있음** → 끝 보고(사람이 할 것 — 폐쇄망 PC 에서 산출물 생성 한 번: 01~11 이 나오는지·결과 xlsx 가 문서보안에 암호화돼 안 열리는지 · 현장 Tibero 에서 스냅샷 한 번: 경고에 `columns` 가 뜨는지·형식 표기 · portfolio 에 `논리명_변환기.html` rel 여섯 자리 인계 · 메모리 「bundle/ 고치면 hooks-test 손으로」 는 0-78 로 끝)

## 4. 실행 스텝
스텝마다 끝에 `bash scripts/mvn.sh -q -DskipTests verify` rc 0(자바를 고친 행) → `bash scripts/verify.sh` 초록 → `git commit`(따로) → `task-done`. 아래 「검증」 은 그 앞에 도는 것만 적는다.

### S0 — 설계 문서·행·번들 표(**실행 세션(Opus)이 첫 손으로 한다**)
- 대상: `design/61-bundle77.md`(A 절) · `PLAN.md` 「번들」 표 「번들 77」 행 + 분할표 새 행·상태(B 절) · `PROGRESS.md`(C 절) · `design/compare-2026-10-28.md` ㉖ 끝 「처분」 블록(D 절).
- 검증: `bash scripts/doc-lint.sh design/61-bundle77.md` · `bash scripts/verify.sh`(docs 레인) · 커밋 `docs: S0 설계 61 — 번들 77`. push 안 함.

### 0-78 — tools 레인 넓히기 + 자기 검사
- 대상: `scripts/hooks-test.sh`(블록 하나) · `scripts/verify-fingerprint.sh`(두 줄)(§3-1).
- 순서: 자기 검사 블록 → `bash scripts/hooks-test.sh` 빨강(그 한 경우, 밖 경로 여섯 — 출력을 이력에) → 레인 두 줄 → 초록.
- 검증: `bash scripts/hooks-test.sh` 통과(경우 수 +1) · `bash -n scripts/verify-fingerprint.sh` · `bash scripts/verify-fingerprint.sh` 가 다섯 줄을 낸다 · 커밋 `fix: 0-78 tools 레인에 hooks-test 가 읽는 것 전부(bundle·README·pom·bat) + 자기 검사`.
- 의존: S0.

### 0-77 — Tibero 글 넷
- 대상: `scripts/bundle-fetch.sh:10·:51` · `bundle/drivers-alt/pom.xml:5` · `PLAN.md:40·:1223`(§3-2).
- 검증: `bash scripts/bundle-fetch.sh --check`(지문 불변) · `bash scripts/hooks-test.sh` 통과 · `grep -rn "Central 에 없" scripts bundle PLAN.md` 가 분할표 행(0-75·0-77·8-9) 밖 0 · `bash scripts/doc-lint.sh PLAN.md` · 커밋 `docs: 0-77 Tibero 드라이버 글 — 현장 판 jar 를 사람이 넣는다`.
- 의존: 0-78(`bundle/` 이 tools 레인에 들어 verify 가 hooks-test 를 돌린다).

### 5-73a — 사유 글·차례 + 시험 둘
- 대상: `MarkupFix`(두 자리) · `LogFix`(상수·`noShape`·한 줄) · `CleanupTest`(+2)(§3-3).
- 순서: `markupReasons`·`logInsertRefusals` RED → GREEN.
- 검증: `-Dtest='CleanupTest,CleanupRoutesTest,SelfCheckTest'` 초록(`LogFix` 15 · 골든 diff 0) · 커밋 `fix: 5-73a 마크업·로거 고침 — 건너뜀 사유 글 둘 · 클래스 아님이 먼저 · 가지 시험`.
- 의존: S0.

### 5-73b — `ImportsTest` · 주석
- 대상: 새 `ImportsTest` · `tools/dev_tools.html:2062`(§3-4).
- 검증: `-Dtest='ImportsTest,ToolsFolderTest,ToolsA11yTest,SelfCheckTest'` 초록 · 커밋 `test: 5-73b Imports 직접 시험 · ccParse 주석`.
- 의존: 없음(차례만).

### 5-74 — 호출만 남기기
- 대상: `LocalFix` · 픽스처 `cleanup/local/Pos.java`(끝) · `CleanupTest.localStage` · 골든 `cleanup/local-Pos.java.txt`·`steps.json`(§3-5).
- 순서: 픽스처 + 단언 RED → GREEN → `-Dgolden.update=true -Dtest=CleanupTest` → diff 읽기 → javac.
- 검증: `-Dtest='CleanupTest,CleanupOrderTest,CleanupRunnerTest,LocalsAstTest,SelfCheckTest'` 초록(`LocalFix` 4 · `valueAndResourceCompile` 초록 · `cleanupChangesNothing` 0 · `self-check.txt` diff 0) · 커밋 `feat: 5-74 코드 정리 — 안 쓰는 지역 변수의 초기화식이 호출 하나면 호출만 남긴다`.
- 의존: 5-73a(같은 시험 파일·골든).

### 5-75a — `jsp.blankNoopener` 판정
- 대상: `rules.yaml` · 새 `BlankRelRule` · `RuleSet.build`(한 줄) · 픽스처 `check/jsp/pos.jsp`·`neg.jsp`(끝) · 새 `BlankRelRuleTest` · `CheckRulesTest`(139) · `docs/pure-code-check.md`(한 마디) · 골든 `check/fixtures.json`(§3-6).
- 순서: 픽스처·시험 RED → GREEN → `-Dgolden.update=true -Dtest=CheckRulesTest` → diff 가 두 줄.
- 검증: `-Dtest='BlankRelRuleTest,CheckRulesTest,PureRulesJsonTest,CheckStoreTest,CleanupTest,SelfCheckTest,ToolsA11yTest'` 초록 · `pure-rules.json`·`self-check.txt`·`golden/cleanup/*` diff 0 · `bash scripts/doc-lint.sh docs/pure-code-check.md` · 커밋 `feat: 5-75a jsp.blankNoopener — JSP·HTML 의 rel 없는 target=_blank(태그 훑기)`.
- 의존: S0.

### 5-75b — `_blank` 고침
- 대상: `MarkupFix`(`apply`·`tagRel`) · `Cleanup`(`RULES`·`clean` 한 줄) · 픽스처 `cleanup/markup/blank.jsp` · 시험 `CleanupTest`(+1)·`CleanupRoutesTest`(20)·`SmokeHtmlUnitTest`(20·16) · 골든 `cleanup/markup-blank.jsp.txt`·`steps.json`(§3-7).
- 순서: 픽스처 + `blankRelStage` RED → GREEN → 골든.
- 검증: `-Dtest='CleanupTest,CleanupRunnerTest,CleanupRoutesTest,SmokeHtmlUnitTest,BlankRelRuleTest,SelfCheckTest,ToolsFolderTest'` 초록(`Cleanup` 29 · `MarkupFix` 6 · 기존 `markup-*.txt` 넷 diff 0 · `cleanupChangesNothing` 0) · 커밋 `feat: 5-75b 코드 정리 — JSP·HTML 의 target=_blank 에 rel(값과 같은 따옴표, 여러 줄 태그도)`.
- 의존: 5-75a · 5-73a(같은 `MarkupFix`) · 5-74(같은 골든 `steps.json`).

### 1-84a — 같은 이름 열은 첫 줄만
- 대상: `JdbcMetaSource`(`distinct`·`warnings`·필드) · `MetaSource`(javadoc) · `VendorFallbackTest`(+1·도우미) · `tools/db_browser.html`(글 한 줄)(§3-8).
- 순서: `duplicateColumnRowsKeepFirstAndWarn` RED(열 넷) → GREEN.
- 검증: `-Dtest='VendorFallbackTest,JdbcMetaSourceWildcardTest,SnapshotServiceTest,SmokeHtmlUnitTest,SelfCheckTest'` 초록 · 커밋 `fix: 1-84a 메타 수집 — 드라이버가 겹쳐 준 같은 이름 열은 첫 줄만(경고 dupColumns)`.
- 의존: S0.

### 1-84b — Tibero 는 열을 딕셔너리에서
- 대상: `TiberoMetaSource` · `VendorFallbackTest`(+2) · `OracleMetaSourceTest`(+1)(§3-9).
- 순서: H2 시험 둘 RED → GREEN → Docker 올리고 `OracleMetaSourceTest` 하나(다르면 표를 드라이버 값에 맞춘다 — 고치기 둘까지, 그래도면 `wip/1-84b`).
- 검증: `-Dtest='VendorFallbackTest,SelfCheckTest,ArchitectureTest'` 초록 · `bash scripts/mvn.sh -q test -Dgroups=db -Dtest=OracleMetaSourceTest -Djunit.timeout=10m` 초록(`meta/oracle.json` 불변 · 맞춘 것이 있으면 형식·값을 이력에) · 커밋 `fix: 1-84b Tibero 열을 ALL_TAB_COLUMNS 에서 직접 — 형식·길이·정밀도까지(실패하면 드라이버 길)`.
- 의존: 1-84a(물러설 길).

### 1-84c — 표본 DDL 대조
- 대상: `web/DbCorpusBase`(`meta` 한 줄 + `tiberoColumns`)(§3-10).
- 검증: `bash scripts/mvn.sh -q test -Dgroups=db -Dtest=OracleCorpusTest -Djunit.timeout=10m` 초록(A 0 · `golden/corpus/db-oracle*` diff 0) · `-Dtest='VerifyLanesTest'` 초록 · 커밋 `test: 1-84c 표본 DDL 에서 Tibero 길 열 = Oracle 드라이버 길`.
- 의존: 1-84b.

### 2-39 — 내장 양식·내장 매핑·xlsx 아님 사유
- 대상: `XlsxFiller` · `Mapping`(`parse`) · `Forms` · `DeliverableService`(도우미·한 줄) · `MakeTemplates`(상수 두 줄) · 시험 `XlsxFillerTest`(+3)·`DeliverableServiceTest`(+1)(§3-14).
- 순서: 시험 넷 RED(메서드 없음 — 컴파일 오류가 RED) → `XlsxFiller` 가르기(기존 시험 불변 초록) → `Mapping.parse`·`Forms` → `DeliverableService` → GREEN.
- 검증: `-Dtest='XlsxFillerTest,DeliverableServiceTest,DeliverableRoutesTest,TableXlsxTest,SelfCheckTest,ArchitectureTest'` 초록 · `golden/deliverable/*`·`self-check.txt` diff 0 · `bash scripts/make-example-templates.sh` 는 안 돌린다(커밋된 양식 불변) · 커밋 `feat: 2-39 산출물 — 내장 양식을 메모리에서 만들어 기입 · 양식이 xlsx 가 아니면 사유`.
- 의존: S0.

### 2-40 — 라우트: 없거나 예시 자리면 내장
- 대상: `DeliverableRoutes`(`postBuild` 두 줄·한 줄 + 도우미 둘) · `profiles/example.yaml`·`site-ora.yaml`·`site-tibero.yaml`(주석 한 줄씩) · `DeliverableRoutesTest`(+2)(§3-15).
- 순서: 시험 둘 RED(`notXlsxTemplateFailsWithReason` 은 2-39 뒤라 사유 글은 이미 서고, `exampleDirMeansBuiltIn` 은 디스크가 멀쩡해 처음부터 초록일 수 있다 — 그러면 RED 자리는 `notXlsx…` 하나와 `Forms.builtIn` 표(2-39)다) → GREEN.
- 검증: `-Dtest='DeliverableRoutesTest,SmokeHtmlUnitTest,CodeCliTest,ProfileStoreTest,SelfCheckTest'` 초록(프로필 예시를 읽는 시험이 주석 한 줄에 안 깨진다 · `postBuild` 23 그대로) · 커밋 `feat: 2-40 산출물 — templateDir 가 없거나 예시 폴더면 내장 양식(디스크 xlsx 를 안 읽는다)`.
- 의존: 2-39.

### 0-79 — 작업 실패 로그
- 대상: `JobManager`(한 줄·`where`·javadoc) · `JobEventRulesTest`(+1)(§3-16).
- 순서: `failedJobLogsWhere` RED → GREEN.
- 검증: `-Dtest='JobEventRulesTest,JobManagerTest,JobSseTest,SelfCheckTest'` 초록(`failedJobMessageIsNotLogged` 안 고치고 초록) · 커밋 `feat: 0-79 작업 실패를 로그에 — 예외 클래스와 우리 코드 자리(글은 안 남긴다)`.
- 의존: S0.

### 1-85 — 작업 실패 사유를 화면에
- 대상: `tools/common.js` · `deliverable_sql.html` · `code_check_ext.js` · `code_cleanup_ext.js` · `program_analysis_ext.js`(둘) · `spring_source_generator_ext.js` · `ToolsFolderTest`(+1) · `SmokeHtmlUnitTest`(+1)(§3-17).
- 순서: `deliverableFailureShowsReason`·`ToolsFolderTest` 단언 RED → `jobText` + 여섯 줄 → GREEN.
- 검증: `-Dtest='SmokeHtmlUnitTest,ToolsFolderTest,ToolsA11yTest,BackendToolsTest,SelfCheckTest,JobEventRulesTest'` 초록(`commonJsListensToEveryEventName` 불변) · `grep -rn "j.status + ' ' +" src/main/resources/tools` 가 `common.js` 한 줄뿐 · 커밋 `feat: 1-85 작업이 실패하면 화면에 사유(TB.jobText) — 폴링 화면 여섯 자리`.
- 의존: 2-40(스모크가 「양식이 xlsx 가 아니다」 사유를 쓴다).

### V-57a — corpus 골든 재채점
- 대상: `golden/corpus/*`(§3-11).
- 검증: 갱신 명령 초록 · diff 가 §3-11 목록 안 · 건수 전후·벽시계를 이력에 · 커밋 `test: V-57a 재채점 — 호출만 남기기 · jsp.blankNoopener`.
- 의존: 5-75b · 1-84c · 1-85.

### R-24 — 손 판정
- 대상: compare ㉗ 「손 판정」 표(§3-12) + 고침(≤ 2, 음성 먼저 RED).
- 검증: 표 + 합 줄 · 고침이 있으면 V-57a 다시 · 커밋 `docs: R-24 손 판정 — 호출만 남기기 · jsp.blankNoopener`(고침은 따로 `fix:`).
- 의존: V-57a.

### V-57 — compare ㉗ · 정적 분석 · `--corpus` · `--db`
- 대상: `design/compare-2026-10-28.md` ㉗ · 규칙 수 문서 자리(§3-13).
- 검증: `mvn.sh -DskipTests verify` rc 0 → `verify.sh` → `verify.sh --corpus` → RAM·다른 세션 확인 → `verify.sh --db` — 넷 초록(따로) · 끝 줄이 더 부르는 레인 없음 · `doc-lint` · 커밋 `docs: V-57 재측정 — compare ㉗ + 정적 분석 + --corpus + --db`.
- 의존: R-24.

### 마무리
§3 「마무리」 그대로. 머지 위임 있음.

## 5. 금지 사항
- `pure/`·portfolio·satchel · `core/gen`·`core/analyze`·`core/vcs` · `OracleMetaSource`·`VendorMetaSource`·`MariaMetaSource`·`MetaSources`·`SnapshotStore`·`SnapshotService`·마이그레이션 SQL · `web/`·`cli/` 본문
- **정규식 규칙을 더하거나 `regex`·`globs`·`message`·`flags` 를 바꾸지 않는다**(`pure-rules.json` diff 0) · 규칙 id 는 `jsp.blankNoopener` 하나만 더한다(139) · `tsx.blankNoopener` 의 판정·고침 결과 불변
- `LocalsAst`(판정·`IGNORED`·`pure`) 불변 — `_` 접두 이름 면제 금지(사용자 결정) · `LocalFix` — 여러 줄 선언·선언자 둘·한 줄에 다른 코드는 계속 건너뛴다 · 람다·메서드 참조가 든 호출을 남기지 않는다 · 호출이 아닌 식(`new`·캐스트·삼항)의 선언을 지우지 않는다 · import 줄을 건드리지 않는다
- `Cleanup` — 메서드 추가 금지(29) · `worse`·`commit`·`targets`·단계 차례·`OPTIONAL`·`TOUCHED_MAY_VANISH`·`MAY_APPEAR` 불변 · `Edits` 에 새 기능 금지
- `BlankRelRule` — script 블록·주석·스크립틀릿 안을 지적하지 않는다 · rel 값(`noopener` 유무)을 보지 않는다 · 블록을 잡는 정규식(`.*?`) 금지(`indexOf` 로) · `MarkupFix.tagRel` — 따옴표 없는 target 에 rel 을 끼우지 않는다 · `cur` 에 지적이 없는 태그를 고치지 않는다
- `LogFix` — 5-73a 의 상수·`noShape`·`orElseGet` 밖 변경 금지(다른 사유의 차례·글 불변) · `MarkupFix.addLang`·`addRel` — 5-73a 의 두 자리 밖 변경 금지
- `TiberoMetaSource` — 딕셔너리 SQL 은 상수 + 바인드만(SpotBugs SQL_INJECTION) · `conn` 을 새로 열지 않는다(규칙 6) · SQL 글·오류문을 경고·로그에 안 남긴다(규칙 3) · `getColumns` 길을 지우지 않는다(물러설 길) · `ALL_TAB_COLS`(숨은 열 포함) 를 쓰지 않는다
- `OracleMetaSourceTest.tiberoColumnsMatchOracleDriver` 를 통과시키려고 `ZZ_T84` 의 열을 빼지 않는다(§3-9 의 예외 하나 밖) · `meta/oracle.json` 을 갱신하지 않는다
- `scripts/` — `verify.sh`·훅 셋·`package.sh`·`gate-probe.sh`·`probes/*.patch` 불변 · `bundle/MANIFEST`·`SOURCES.md` 불변 · `VerifyLanesTest` 불변
- 픽스처 — 기존 줄 삭제·삽입 금지(끝 덧붙임만) · `neg*` 에 지적이 나는 꼴 금지 · 픽스처에 `// tb-ignore` 를 더하지 않는다(`CheckStoreTest:80`) · check 픽스처 폴더(`common·jsp·tsx·security·a11y`)에 새 파일 금지(`PureCodeCheckTest:49` 가 32 를 건다)
- 골든 — §3 이 적은 변화 밖 금지 · `-Dgolden.update` 는 그 시험에만 · `self-check.txt` 새 키 금지(우리 코드를 고친다) · `self-cleanup.txt` 새 사유 금지
- 추가 건 — `XlsxFiller.fill` 의 기입 몸(`:34-115`)을 고치지 않는다(옮기기만) · `writeTemplate` 이 만드는 통합 문서의 셀·모양 불변 · 커밋된 `templates/deliverable/example/*.xlsx`·`mappings/deliverable/example.yaml` 을 다시 만들거나 지우지 않는다 · `package.sh`·`package-check.sh`·`make-example-templates.sh` 불변 · `DeliverableService.build` 의 인자·분기 수 불변 · `writeGuide`·`write18` 불변 · 디스크 양식을 못 읽을 때 내장으로 **몰래 물러서지 않는다**(사용자 결정 8 — 지정한 폴더는 그대로 읽고, 못 읽으면 사유로 멈춘다)
- `JobManager` — **예외 글(`getMessage`)·스택 전체를 로그에 넘기지 않는다**(`LOG.warn(…, e)` 금지 — logback 이 글을 찍는다) · `Job`·`summary()`·SSE 페이로드 불변 · `failedJobMessageIsNotLogged` 를 고치지 않는다
- 화면 — `TB.jobText` 밖에서 `error` 를 `innerHTML` 로 넣지 않는다(서버 글은 `textContent` 길 — `TB.result`·`msg` 그대로) · 여섯 줄 밖의 폴링 로직 불변
- 새 의존성 금지 · `--full` 은 안 돈다 · `--db`·`--corpus` 는 V-57 에서 한 번씩(1-84b·c 의 단일 시험 밖) · 무거운 것을 겹쳐 띄우지 않는다 · `wsl --shutdown` 금지

## 6. 최종 검증
- `bash scripts/mvn.sh -DskipTests verify` rc 0 · `bash scripts/verify.sh` · `bash scripts/verify.sh --corpus` · `bash scripts/verify.sh --db` 초록(따로) · 끝 줄이 더 부르는 레인 없음 · `gate-probe.sh` 전부 빨개짐 · `bash scripts/hooks-test.sh` 통과
- 수: 규칙 139(`CheckRulesTest`) · security 45 · 정규식 63(`PureRulesJsonTest`) · `Cleanup.RULES` 20 · `OPTIONAL` 4 · 화면 체크박스 20·켜진 16(`SmokeHtmlUnitTest`) · `Cleanup` 29 · `LogFix` 15 · `MarkupFix` 6 · `LocalFix` 4
- 골든: `git diff --stat origin/main -- src/test/resources/golden` 이 `check/fixtures.json`(두 줄) · `cleanup/local-Pos.java.txt` · `cleanup/markup-blank.jsp.txt`(새) · `cleanup/steps.json` · `corpus/check-*` · `corpus/cleanup-{egov,roller,jspwiki}.json` · `corpus/vcs-egov.json` · (`corpus/security-*` 총 건수) 안 · `check/pure-rules.json`·`check/java.json`·`check/self-check.txt`·`check/self-cleanup.txt`·`meta/*`·`corpus/db-*`·`corpus/js-summary.json` diff 0
- 추가 건: `golden/deliverable/*` diff 0 · `self-check.txt` 의 `build(8)`·`fill(4)`·`postBuild(7)` 줄 불변 · `grep -rn "j.status + ' ' +" src/main/resources/tools` 가 `common.js` 한 줄
- 수동(추가 건, 한 번 — 이력에 한 줄): `templates/deliverable/example` 폴더 이름을 잠깐 바꾼 채 `run.bat --profile example` → 산출물 생성이 01~11 을 낸다(끝나면 이름을 되돌린다 — 커밋 전 `git status` 깨끗) · 임시 프로필의 `templateDir` 를 쓰레기 xlsx 폴더로 → 화면에 「실패 — 양식이 xlsx 가 아니다: …」, 콘솔 로그에 「작업 실패 … IllegalArgumentException @ XlsxFiller.requireXlsx:…」
- 수동(한 번, 이력에 한 줄): `run.bat` → 코드 정리 화면 체크박스 20(켜진 16) · egov 폴더 미리보기에서 `EgovXxxController` 의 `egovAssertLoginUser();` 와 JSP 의 `rel="noopener noreferrer"` 가 보인다(쓰지 않음) · 코드 검사 JSP 묶음에 `jsp.blankNoopener`
- compare ㉗ 표 + 「드러난 것」 + 「후보로」 · 이력에 건수·벽시계

## 7. 중단 조건
멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- 0-78 에서 자기 검사가 레인을 고친 뒤에도 빨강(읽는 경로가 실측 여섯과 다르다) · `hooks-test.sh` 의 임시 저장소 경우들이 새 레인 경로 때문에 빨강
- 5-73a 에서 `logInsertRefusals` 의 한 사유가 입력을 두 번 고쳐도 안 닿음 · 기존 `logStage`·`markupStage`·골든이 바뀜
- 5-74 에서 `localStage` 의 사유 글·차례·건수가 §3-5 와 다름(줄 번호 차이는 아님) · `valueAndResourceCompile` 빨강 · 기존 cleanup 골든(`local/Pos.java` 밖)이 바뀜 · `self-check.txt` 가 바뀜
- 5-75a 에서 `fixtures.json` diff 가 두 줄을 넘는데 덧붙인 줄을 고쳐도 안 줄어듦 · 우리 화면(`tools/*.html`)에 지적이 섬 · `steps.json` 의 기존 항목이 바뀜 · `PureRulesJsonTest` 빨강
- 5-75b 에서 기존 `markup-*.txt` 넷 또는 `markupStage` 가 바뀜(`apply` 재배치가 순수하지 않다) · `blankRelStage` 의 단계가 「다른 지적이 생김·사라짐」 으로 되돌려짐
- 1-84a 에서 `oracleSourceOnH2FallsBackToJdbcWithWarnings` 등 기존 경고 단언이 빨강
- 1-84b 에서 Oracle 컨테이너 대조가 고치기 둘 뒤에도 다름 · `meta/oracle.json` 이 바뀜 · Docker·이미지 부재로 못 돎(환경 — 그 행을 `wip/1-84b` 로 빼고 다음 행, 끝 보고에)
- 1-84c 에서 A 가 0 이 아닌데 원인이 사용자 정의 형식 열이 아님 · `db-oracle*` 골든이 바뀜
- 2-39 에서 `builtInFillEqualsDiskTemplate` 이 한 장이라도 다름(내장 길이 디스크 길과 같지 않다) · `golden/deliverable/*` 또는 `self-check.txt` 가 바뀜 · 없는 양식 파일에 다른 예외를 기대하는 기존 시험이 빨강
- 2-40 에서 기존 `buildWritesElevenXlsx`·`deliverableFilter`·`doc18FromAnalyzeRun` 이 빨강 · `postBuild` 복잡도를 23 으로 못 맞춤 · 프로필 예시를 읽는 시험이 주석 한 줄에 빨강
- 0-79 에서 `failedJobMessageIsNotLogged` 가 빨강(로그에 글이 샜다)
- 1-85 에서 `j.error` 가 비어 오는 화면이 있음(서버가 `error` 를 안 싣는 작업 종류) · HtmlUnit 이 산출물 실패 경로를 못 돌림
- V-57a 에서 §3-11 목록 밖 골든 변화 · 채점 칸 변화 · 중단선
- R-24 에서 고침 셋째가 필요 · `jsp.blankNoopener` 오탐이 본 줄의 10% 를 넘음 · 호출만 남긴 줄에서 컴파일이 깨질 꼴이 하나라도 나옴(고침 하나로 치고, 못 고치면 중단)
- V-57 에서 `--db` 가 1-84 밖 원인으로 빨강(고치기 둘까지) · 여유 RAM 이 모자라 `--db` 를 못 띄움(사용자에게 묻는다)
- 메서드 수 상한(§6)을 넘어야 함 · `pmd:check`·SpotBugs 새 짝 · `VerifyLanesTest` 빨강
- 검증 2회 연속 실패가 범위 밖 · 스텝 밖 파일 3개 이상

## 8. 불확실 항목
- **실행해야만 알 수 있는 것** — ① Oracle 드라이버가 `ZZ_T84` 의 형식마다 내는 번호·길이·정밀도(§3-9 표는 드라이버 질의를 아는 대로 적은 것 — `OracleMetaSourceTest` 가 심판이고 다르면 표를 맞춘다. 확인: 1-84b 의 단일 시험) ② 표본 DDL(Oracle 23·11g)의 형식 가짓수(1-84c·V-57 `--db`) ③ `logInsertRefusals` 다섯 입력이 그 가지에 닿는지(5-73a — 닿게 입력을 고친다) ④ 표본 건수 전부(V-57a·R-24) ⑤ Docker·Oracle 이미지·여유 RAM(1-84b 앞 `docker images | grep oracle-free` · V-57 앞 RAM)
- **현장에서만 알 수 있는 것(실행도 못 본다 — 사용자 결정 6 이 받았다)** — Tibero `ALL_TAB_COLUMNS` 에 `CHAR_LENGTH` 열이 있는지와 `DATA_TYPE` 글자 꼴. 없거나 실패하면 경고 `columns` 와 함께 드라이버 길로 물러서 스냅샷은 찍힌다. 끝 보고 「사람이 할 것」 에 적는다
- **현장에서만 알 수 있는 것(추가 건)** — 폐쇄망 PC 의 문서보안이 자바가 **쓴** xlsx(산출물 결과)도 암호화하는지. 암호화해도 이 번들 뒤에는 읽는 xlsx 가 없어 생성은 끝난다 — 결과 파일이 열리는지는 그 PC 에서 본다. 끝 보고 「사람이 할 것」 에 적는다
- 설계가 미리 잰 것(불확실 아님): ② 꼴별 건수와 egov 남는 import 0 · ③ 훑기를 옮겨 돌린 121 태그(표본별 수 · 끼울 자리 어긋남 0) · 우리 화면 지적 0 · 기존 JSP·HTML 픽스처에 `_blank` 0 · `hooks-test.sh` 가 읽는 레인 밖 경로 여섯 · `ReadOnlyJdbc` 가 딕셔너리 SQL 을 지나게 한다
- **사용자만 정할 수 있는 것** — 없음(1-84 길 ① 의 깊이 · 내장 양식 조건 (가) 는 2026-11-09 에 받았다)
