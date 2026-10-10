# 설계 20 — 번들 34: 화면 전수표(6-20 쪼갬 6-25~6-29) · 프로그램 분석 중지 (Fable, 2026-10-10)

이 플랜은 실행 담당 모델(Opus)이 그대로 따라 실행한다. 실행자는 탐색 과정을 모른다. 판단이 필요한 지점을 남기지 않았다. 번들 모드 규칙(CLAUDE.md 「번들 모드」) 그대로 — 갈려도 안 멈추고, 행당 「정한 것(계획 밖)」 둘까지. 원본은 설계 세션 플랜 파일 — S0 에서 이 파일로 옮겼다(2026-10-10).

사용자가 2026-10-10 정한 것(뒤집지 않는다):
- 6-20 은 **화면 전수표**다 — 메뉴·URL 마다 개인정보 포함 여부를 판단하지 않는다.
- 자리는 **프로그램 분석의 새 탭**(실행 이력·xlsx 한 파일에 시트로). 따로 창을 안 만든다.
- **코드만으로 만드는 전수표가 바닥.** 메뉴·화면 목록은 **CSV 를 올려 덧입히는 선택 입력**. 스냅샷은 구조만이라 안 쓴다.
- **화면명·유형(목록·상세·등록) 추정 안 함** — 코드가 모르는 값은 안 채운다. 화면명은 메뉴 CSV 경로의 마지막 마디.
- CSV 는 **메뉴 경로 한 열**(`대 > 중 > 소 …`, 깊이 제한 없음) + URL. 트리 재귀는 사용자 SQL 쪽. 행 순서가 트리 순서. **사용여부(Y/N)** 열은 선택 — 행을 빼지 않고 열로만.
- **「JSP 파일」 열(있음/없음/여럿) + 정합성 「view 가 가리키는데 없는 JSP」 를 첫 판에.**
- 프로그램 분석에 **중지** 버튼(코드 검사 5-21 과 같은 꼴).

### 1. 목표와 범위

- **목표**: 프로그램 분석 화면에 「화면 전수」 탭이 생겨 분석 실행만으로 화면(뷰를 돌려주는 프로그램) 전수표가 나오고, 메뉴 CSV 를 올리면 메뉴 경로·화면명·연결 근거가 덧붙고, xlsx 한 파일에 시트로 같이 나간다. 정합성 탭이 「없는 JSP」 를 보이고, 분석을 중간에 중지할 수 있다.
- **In**: `S0`(문서) → `6-25a`(중지 — 엔진·라우트) → `6-25b`(중지 — 화면·스모크) → `6-26a`(V010 + JSP 파일 수 계산·저장) → `6-26b`(정합성 없는 JSP — 엔진·xlsx) → `6-26c`(정합성 탭 다섯째 칸) → `6-27`(JSP 링크 종류) → `6-28a`(화면 전수 엔진·API·xlsx 시트) → `6-28b`(화면 전수 탭) → `6-29a`(메뉴 CSV 파서·저장·API) → `6-29b`(메뉴 덧입히기 엔진·xlsx 「메뉴만」) → `6-29c`(탭의 메뉴 올리기·안내·열) → 마무리.
- **Out**: 화면명·유형 추정 · JSP 내용 열(화면 표시·입력칸·hidden·검색 조건 — 2판) · ajax 가 부르는 데이터 URL 열(2판, §8) · 프로필 DB 에서 메뉴 직접 조회(CSV 가 번거로우면 그때) · 개인정보 판단 · `pure/` · 산출물(2장) 양식 · 검증 인프라.

### 2. 현재 구조 요약(2026-10-10 실측)

#### 2.1 가지 상태
- `work/2026-10-10` = main `98c876f`(번들 33 머지) 위에 머지 기록 커밋 `fa535bc` 하나. 트리 깨끗. 이 가지에 번들 34 를 쌓고 마무리에서 민다.
- 분할표 6-20 행이 방향·열을 다 적고 있다(설계 대기). 6-25~6-29 번호는 비어 있다(확인함).

#### 2.2 분석 엔진·저장(건드리는 자리)
- `core/analyze/AnalyzeRunner.run(root, files, naming, ctx)`(226줄) — 파일 목록(`*.java *.xml *.jsp`) 읽기 루프에서만 `ctx.checkCancelled()`(파일마다) · 매퍼 색인 85% 앞 · JSP 링크 뒤 90% 앞. **`JavaGraph.scan`(호출 그래프, 가장 오래 돈다)과 그 뒤 프로그램 합치기 루프에는 취소 확인이 없다** → 중지를 눌러도 그래프가 끝날 때까지 돈다. `Result(rows, tables, unresolved, files, skipped, truncated, statements, jspLinks: Map<jsp, List<url>>, jsps, orphans, joins)` 11인자 + 10인자 보조 생성자(`AnalyzeStoreTest.result()` 가 쓴다). `orphans(index, graph, jsp)` 가 **뷰 → JSP 맞춤 규칙**을 쥔다: 뷰 이름 N(앞 `/` 뗌), JSP 상대 경로의 `.jsp` 뗀 것이 N 과 같거나 `/N` 으로 끝나면 가리킨 것. 어느 뷰에도 안 가리켜진 JSP 가 고아(`Orphan("jsp", rel)`).
- `web/AnalyzeRoutes` 의 job 본문 — `AnalyzeRunner.run` → **바로 `store.save`** → `files.remember` → RESULT. `JobManager.run` 은 본문이 끝난 뒤 `ctx.isCancelled()` 면 CANCELLED 로 보고하지만 **이미 저장된 이력은 남는다**(코드 검사도 같은 구조 — 거기는 파일 루프가 촘촘해 체감이 없다).
- `core/job/JobContext` — `checkCancelled()`(`JobCancelledException`) · `isCancelled()`. `JobRoutes` `DELETE /api/jobs/{id}` → `jobs.cancel(id)` → `{cancelled}`. `JobManager.run` 이 `JobCancelledException`·취소 뒤 종료를 CANCELLED 로 낸다.
- `core/analyze/JavaGraph.scan(java, naming, jpa)` → `new JavaGraph(naming, jpa).run(java)`: ① `for (Source s : java)` 파싱 ② 상속·구현 색인 ③ `for (Cls c : all)` 컨트롤러 메서드마다 프로그램. `Program(className, method, file, line, verb, url, params, kind, views, description, statements)` · `View(kind: view·forward·redirect·class, name)` · `kind(…)` = json(`@RestController`·`@ResponseBody`·리턴이 String·ModelAndView 아님·jsonView 만) 아니면 **view**(뷰 이름을 못 풀어도 view). 뷰 이름 동적이면 `viewDynamic` 미해결 + views 빔.
- `core/analyze/JspLinks.extract(Source)` → `Result(urls: 정렬·중복 없음, unresolved)`. `.do` 토큰을 손 주사로 모은다 — **어떤 꼴(href·form action·window.open·ajax)로 불렸는지는 안 본다.** 토큰 시작 위치 `start` 와 원문 `text`(주석 지운 것)를 안에서 갖고 있다. `JspLinksTest.golden` 은 `r.urls()`·`r.unresolved()` 만 골든에 쓴다(`golden/analyze/jsp-links.json`) — `urls()` 가 남으면 골든 불변.
- `core/analyze/AnalyzeStore` — `save(profile, path, Result)`: `analyze_run`·`analyze_program`·`analyze_view(program_id, kind, name)`·`analyze_stmt`·`analyze_crud`·`analyze_jsp_link(run_id, jsp, url)`·`analyze_join`·`analyze_orphan`·`analyze_unresolved`. 읽기 `programs(runId)`(`ProgramRow` 13인자 — views·statements·crud 포함) · `crud` · `impact(runId, table)`(`analyze_jsp_link` 를 URL 로 조인) · `orphans` · `unresolved` · `joins`. **JSP 링크를 통째로 읽는 메서드가 없다**(impact 가 표 기준으로만 조인).
- 마이그레이션 `V001~V009`(`src/main/resources/db/migration/`). 반입 전이라 새 V 파일은 자유(`released-baseline` 없음). `analyze_run.profile` 에 실행 때 활성 프로필 이름이 남는다.
- `core/analyze/Consistency.of(analyze, snapshots, runId, snapshotId)` → `Report(snapshotId, scopeSummary, missingInDb, unusedInCode, deadStatements, orphanJsps)`(번들 33). `AnalyzeCorpusTest` 의 정합성 골든(`analyze-egov-consistency.json`)은 **손으로 고른 키 여섯만**(ddlTables·ddlUnreadable·missingInDb·unusedInCode·deadStatements·orphanJsps) — `Report` 에 필드를 더해도 골든 불변. `analyze-egov-orphan-jsp.txt` 는 `cr.orphanJsps()` 그대로 — **고아 JSP 집합이 바뀌면 빨강**.
- `core/analyze/CrudViews`(번들 33) — `module(url)`(URL 앞 두 마디). 화면 전수의 「모듈」 열이 그대로 쓴다.
- `core/text/Csv` — `decode(byte[])`(BOM·인코딩) · `parse(String)`(구분자 자동) · `guess(header, candidates, allowNone)`(공백 뺀 이름 같음 → 포함 → -1) · `column(header, name)`. `core/logical/ColumnInputs.fromCsv` 가 쓰는 꼴(헤더 후보 목록·필수 열 없으면 `IllegalArgumentException`)을 메뉴 CSV 가 따른다.
- `web/Outputs` — `xlsx(dir, name, LinkedHashMap<String, ResultTable> sheets)` · `table(cols, rows)` · `text`·`num`(번들 33). 분석 export 시트 순서 프로그램목록 · CRUD목록 · CRUD모듈 · 미해결 · 정합성(스냅샷 있을 때). `AnalyzeRoutesTest.exportXlsx` 가 시트 이름 배열을 **정확히** 단언한다 — 시트를 더하면 그 단언을 같이 고친다.

#### 2.3 화면
- `tools/program_analysis.html` — `.dirbar`(폴더 입력 · `#run` 「분석」 · 이력 `#runs` · `#xlsx` · `#msg`) · 탭 다섯(`TABS` 배열 — 프로그램 목록·CRUD 매트릭스·미해결·영향도·정합성) · 정합성 `.grid`(2×2, `grid-template-columns: 1fr 1fr; grid-template-rows: 1fr 1fr`) 칸 넷(`#conMissing`·`#conUnused`·`#conDead`·`#conOrphan`). 로컬 `<style>` 규칙(`ToolsFolderTest.toolsHaveNoLocalTheme` — `:root`·`button{}`·`.btn-*`·`.dl`·토큰 밖 고정 색 금지).
- `tools/program_analysis_ext.js`(ES5) — `run()` → `POST /api/analyze/run` → `poll(jobId)` 400ms(QUEUED·RUNNING 이면 계속, DONE 이 아니면 `j.status` 글을 err 로) → `load(id)`(`/crud` + `/unresolved`). **중지 버튼·jobNow 없음.** `load` 가 탭 데이터를 한 번에 받는다. `TB.table(el, 머리, 행)` 은 글자 셀만(`.tb-table`), `cell(tag, text, cls)` 로 짠 표는 CRUD 탭. `TB.copy(src, notify)` 복사 한 꼴(1-35). 서버 값은 `textContent` 만.
- `tools/code_check_ext.js` 155~200 이 중지 본보기 — `start()` 가 `jobNow = r.jobId; $('runStop').disabled = false;` · `stop()` → `DELETE /api/jobs/<jobNow>` · `poll` 끝에서 `done()`(버튼 복구) · `CANCELLED` 면 「중지함 — 이력에 남기지 않았다」. html `<button class="btn-red" id="runStop" disabled>중지</button>`.
- `tools/logical_name.html` `readCsvFile()`(290~) — `<input type=file>` → `FileReader.readAsArrayBuffer` → UTF-8(`fatal`) 실패면 EUC-KR 로 디코드 → 숨은 textarea 에 글. 메뉴 CSV 올리기가 같은 꼴(서버엔 JSON `{csv: 글}`).
- `tools/index.html:87` 프로그램 분석 카드 설명.

#### 2.4 시험이 보는 것
- `SmokeHtmlUnitTest.programAnalysisRuns`(805~) — 픽스처 프로젝트(`AnalyzeRoutesTest.project`, 프로그램 13, JSP 셋: `jsp/bbs/BoardList.jsp`(`/bbs/list.do` 를 `<c:url>` 로 부름)·`jsp/bbs/Stf.jsp`(EL 링크)·`jsp/sample/bbs/BoardDetail.jsp`(뷰가 가리킴)). 픽스처 컨트롤러 뷰 이름: `sample/bbs/BoardList`·`sample/bbs/BoardRegist`·`sample/bbs/Other`·`sample/other/List`·`sample/other/SameUrl`·`sample/other/Both`·`x` · forward·redirect 둘 · jsonView·`AjaxXmlView` 클래스 뷰. → **`sample/bbs/BoardList` 의 JSP 파일은 없다**(픽스처 JSP 는 `jsp/bbs/BoardList.jsp` 라 `sample/bbs/…` 로 안 끝남) · `sample/bbs/BoardDetail` 은 있다(JSP 는 있는데 그 뷰를 돌려주는 프로그램은 grep 에 안 보임 — 고아 아님만 확인됨).
- `SmokeHtmlUnitTest.codeCheckStops` — 임시 폴더 `.java` 1,000(각 200줄) → `#runDir` → `#runStop` 켜질 때까지 100ms×50 → 클릭 → 20초 안에 「중지함 — 이력에 남기지 않았다」 · 이력 수 그대로. 중지 스모크 본보기.
- `AnalyzeRoutesTest`(번들 33 뒤: 프로필 `t.yaml` 에 H2 mem 접속 · `snapshot()` 헬퍼 · `exportXlsx` 시트 이름 정확 단언 · `consistency`). `AnalyzeStoreTest.result()` 가 `Result` 10인자 생성자로 픽스처를 만든다. `JavaGraphTest`(`sources()` 가 픽스처 java 목록 — 골든 `analyze/java-graph.json` 은 `Graph` 직렬화) · `JspLinksTest.golden`(urls·unresolved 만) · `ConsistencyTest`.
- `AnalyzeCorpusTest`(표본, 로컬 `--full`) — B baseline `analyze-egov.json` 의 `jspUrls`·`jspLinkedPrograms` 는 **URL 집합**에서 센다(종류 무관) · `analyze-egov-orphan-jsp.txt` 고아 JSP 목록.

### 3. 설계 결정

- **D1 [고정] 중지는 세 자리에서 받는다** — ① `JavaGraph.scan(java, naming, jpa, Runnable tick)` 새 오버로드: 파일 파싱마다·컨트롤러 클래스마다 `tick.run()`. `AnalyzeRunner` 가 `ctx == null ? null : ctx::checkCancelled` 를 넘긴다 ② `AnalyzeRunner` 프로그램 합치기 루프(`for Program p`) 50개마다 `checkCancelled` ③ **`AnalyzeRoutes` job 본문이 `store.save` 바로 앞에 `jc.checkCancelled()`** — 취소된 분석은 이력에 남지 않는다(5-21 과 같은 약속). 옛 3인자 `scan` 은 `tick=null` 로 위임(JavaGraphTest·표본 시험 불변).
- **D2 [고정] 뷰 → JSP 맞춤 규칙은 한 함수** — `AnalyzeRunner.viewFiles(graph, jsp, matched)` 가 뷰 이름마다 맞는 JSP 파일 수를 세고, 같은 한 번의 주사에서 「어느 뷰에라도 세인 JSP」 집합을 채운다. `orphans` 의 고아 JSP 는 그 집합의 보수 — **지금 규칙(같거나 `/N` 으로 끝남)과 결과가 똑같아야 한다**(`analyze-egov-orphan-jsp.txt` 불변이 증거). 수는 새 표 `analyze_view_file(run_id, name, files)` 에. 옛 실행(행 없음)은 「모름」.
- **D3 [고정] JSP 링크 꼴은 가장 가까운 단서 하나 — 추정값임을 열 이름에 적는다(「부르는 꼴(단서)」)**(사용자 2026-10-10: 넣고 실물로 재고 갈린다). `JspLinks` 가 토큰 앞 200자에서 정규식 `c:url\s+var|window\.open|\.open\(|action|location\.(href|replace)|location\s*=|href|c:import|url\s*:|\$\.(get|post|ajax)\(|\.load\(|ajax` 를 차례로 찾아 **마지막(가장 가까운) 것**으로 정한다: `c:url var` → `other`(변수에 담은 URL — 누가 쓰는지 안 따라간다) · `window.open`·`.open(` → `popup` · `action` → `form` · `location.*` → `script` · `href`·`c:import` → `link` · `url:`·`$.get/post/ajax(`·`.load(`·`ajax` → `ajax` · 없으면 `other`. (`location.href` 는 정규식이 `location.` 자리에서 한 덩이로 잡아 `href` 보다 앞서 매치되므로 script 로 간다.) 같은 JSP 에서 같은 URL 이 여러 꼴로 나오면 **서로 다른 꼴을 전부** 저장한다(행은 jsp·url·kind 당 하나 — URL 집합은 그대로라 `jspUrls` baseline 불변, `impact` 는 `DISTINCT p.url, j.jsp` 라 불변). 영문 코드 저장(`analyze_jsp_link.kind`), 화면 글은 링크·폼·팝업·ajax·스크립트·기타, 옛 행 null 은 「모름」. `Result.urls()` 는 남긴다(골든 불변). **명확도**: 단서가 URL 바로 옆에 있으면 맞고, 변수·자작 함수를 거치면 「기타」 — 틀리게 적는 경우는 자기 단서 없는 URL 앞 200자에 다른 URL 의 단서가 있을 때뿐. 그래서 **마무리에서 실물로 잰다**(S12 — 데모 egov 실행의 꼴별 수 + 손 대조 20개를 이력에; 「기타」 가 절반을 넘거나 오판이 20 중 3 넘으면 다음 번들에서 빼는 행을 세운다).
- **D4 [고정] 전수표 계산은 `core/analyze/Screens` 한 곳** — 입력은 저장된 것만(`programs(runId)`·`viewFiles(runId)`·`jspLinks(runId)`·메뉴 행). 행 = **kind `view` 프로그램 중 `view` 종류 뷰가 있거나 뷰가 하나도 없는 것**(동적 뷰 — JSP 「모름」). 뷰가 redirect·forward·class 만인 프로그램과 kind `json` 은 행에서 빼고 **제외 수**로만(`json`·`redirect`·`forward`·`class` — 첫 뷰의 종류). 순서 모듈 → URL → 파일·줄. 열은 6-20 행의 「코드만 열」 — No · 모듈(`CrudViews.module`) · URL · verb · params · 프로그램 · 소스(파일:줄) · JSP(view 이름들) · JSP 파일(있음/없음/여럿/모름 — 뷰마다 센 뒤 서로 다르면 `·` 로 잇는다) · 표·CRUD(`표(글자)` 를 ` · ` 로) · 부르는 화면(JSP 마다 `jsp (꼴·꼴)` — 한 JSP 가 여러 꼴로 부르면 꼴을 전부, 열 이름 「부르는 화면 · 부르는 꼴(단서)」). 「미해결 표시」·「같은 JSP 공유 수」·「화면이 부르는 데이터 URL」 은 첫 판에서 뺀다(§8).
- **D5 [고정] 메뉴 CSV 는 프로필 단위로 H2 에** — `analyze_menu(profile, seq, path, name, url, screen_id, use_yn, auth, uploaded_at)`. 올리면 그 프로필 행을 **통째로 바꾼다**. 화면 전수는 **실행의 프로필**(`analyze_run.profile`)의 메뉴를 쓴다. 분석을 다시 돌려도 메뉴는 남는다(메뉴는 드물게, 분석은 자주 바뀐다). 버린 대안: 실행마다 메뉴를 붙이기 — 분석마다 다시 올려야 한다.
- **D6 [고정] CSV 계약** — 헤더 후보(`Csv.guess`, 대소문자·공백 무시): 경로 `메뉴·메뉴경로·경로·menu·path·menupath` **필수** · URL `URL·url·주소·링크` **필수** · 화면ID `화면ID·화면번호·screenid·screen_id` · 사용여부 `사용여부·사용·useyn·use_yn·use_at` · 권한 `권한·auth·authority·role`. 둘 중 하나가 없으면 `IllegalArgumentException`(400). 경로 구분자 `>`(전각 `＞` 도) **고정** — 마디 앞뒤 공백·빈 마디 제거, 저장 꼴 `대 > 중 > 소`, 이름 = 마지막 마디. URL 은 trim·`?` 뒤 뗌·빈 글은 null(중간 메뉴 — 행은 저장하되 전수 이음에는 안 쓴다). 사용여부 `Y·y·1·true·사용·예` → Y, `N·n·0·false·미사용·아니오` → N, 그 밖은 원글 그대로 + 경고 수. seq = 데이터 행 번호(1부터) = 트리 순서. 버린 대안: 프로필 YAML 구분자 키 — 이름에 `>` 가 든 사업이 나오면 그때 더한다.
- **D7 [고정] 메뉴 이음 규칙** — URL 정확 일치(둘 다 `?` 뒤 뗀 뒤) → 근거 「일치」. 없으면 **한 단계** 경유: 그 화면을 부르는 JSP(종류 무관)가 「일치」 화면의 JSP 파일이면 그 화면의 메뉴를 이어 받아 「경유」(사용 Y 메뉴를 먼저, 다음은 seq 순). 그래도 없으면 「없음」. 컨텍스트 경로 첫 마디를 떼고 다시 맞추는 두 번째 시도는 **안 한다**(오탐 — demo 의 14% 불일치는 옛 메뉴 데이터). 메뉴에 URL 이 있는데 어느 프로그램과도 안 맞으면 「메뉴에만 있는 URL」 목록(seq 순). 같은 URL 에 메뉴가 여럿이면 경로를 전부 싣고(`;`) 이름·화면ID·사용여부는 **사용 Y 를 먼저, 다음 seq 순**의 첫 것.
- **D8** 화면 — 탭 「화면 전수」 는 CRUD 매트릭스 뒤·미해결 앞. 메뉴 올리기는 그 탭 안(파일 고르기 + 「메뉴 CSV 올리기」 `.btn-p` + 「메뉴 지우기」 `.btn-red` + 「CSV 꼴·SQL 예시」 접이 안내, 예시는 `TB.copy` 로 복사만). 행을 누르면 아래 well 에 부르는 화면·뷰·표·메뉴 경로. 제외 수는 표 아래 한 줄. 메뉴가 있으면 「메뉴에만 있는 URL」 상자가 보인다.
- **D9 [고정] xlsx** — 시트 순서 프로그램목록 · CRUD목록 · CRUD모듈 · **화면전수** · **메뉴만**(메뉴가 있을 때만) · 미해결 · 정합성(스냅샷 있을 때만). 화면전수 열 = 화면 표 열 + 메뉴 열(메뉴 없으면 빈 칸) + 부르는 화면 목록(`jsp(종류)` 를 `; ` 로). 정합성 시트에 구분 「없는 JSP」 행.
- **D10** 정합성 다섯째 칸 「view 가 가리키는데 없는 JSP」(6-18 표기 — 「뷰」 안 씀) — 스냅샷 없이도 나온다(코드만 보는 값). 그리드는 `grid-auto-rows: 1fr` 로 3행.
- **D11** 청크 가르기 — 엔진·API(a) / 화면(b) 로 가른다(파일 1~3 규칙, 시험 포함 4 까지). 분할표 행 번호 6-25~6-29, 커밋 제목에 `6-26a` 처럼.

### 4. 실행 스텝

검증 명령은 전부 `bash scripts/mvn.sh -q -B -Dtest='…' test`(맨몸 mvnw 금지). 청크 끝 `bash scripts/verify.sh` → 초록 → `git commit`(**따로** — 한 체인이면 훅이 막는다. 이력 글도 verify 앞에 쓴다).

#### S0 문서 — 설계 20 + 행 + 번들 표
- **대상**: `design/20-bundle34.md`(이 파일 그대로 옮김) · `PLAN.md` · `PROGRESS.md`.
- **변경**: ① 이 플랜을 `design/20-bundle34.md` 로 복사(머리에 「원본은 설계 세션 플랜, S0 에서 옮김」 한 줄) ② 분할표 6-20 행 상태 칸 「설계 20 — 6-25~6-29 로 쪼갬(2026-10-10)」 ③ 새 행 6-25~6-29(각 「**설계 20 S<n> 가 원본**(`design/20-bundle34.md`). 」 + 사실·결정 요지 두 줄 + 닫힘, 선행 6-24) — 아래 표 ④ 번들 표에 번들 34 행 ⑤ 8장 API 프로그램 분석 행에 `GET /analyze/runs/{id}/screens`(6-28) · `GET·POST·DELETE /analyze/menu`(6-29) · `DELETE /jobs/{id}` 로 분석 중지(6-25) ⑥ PROGRESS 「현재 상태」 진행중 칸 「번들 34 설계 끝 — Opus 「번들 해」」 + 이력 한 줄(설계 20).

| 행 | 청크 | 닫힘 |
|---|---|---|
| 6-25 | 프로그램 분석 중지(a 엔진·라우트 / b 화면·스모크) | `JavaGraphTest.scanStopsOnTick` · 스모크 `programAnalysisStops`(이력 수 그대로) |
| 6-26 | JSP 파일 확인(a V010·계산·저장 / b 정합성 없는 JSP·xlsx / c 정합성 탭 칸) | `AnalyzeStoreTest` 왕복 · `ConsistencyTest.missingJsps` · `AnalyzeRoutesTest.consistency` · 스모크 다섯째 칸 · corpus 고아 JSP 불변 |
| 6-27 | JSP 링크 꼴(단서) — 가장 가까운 단서 하나, 같은 URL 의 다른 꼴 전부 | `JspLinksTest.kinds` 여덟 꼴 · `AnalyzeStoreTest` kind 왕복 · 골든 `jsp-links.json` 불변 · 마무리 실측(수·비율·손 대조 20) 이력 |
| 6-28 | 화면 전수(a 엔진·API·xlsx / b 탭) | `ScreensTest`(행 포함·제외·JSP 파일·부르는 화면·순서) · `AnalyzeRoutesTest.screens`·`exportXlsx` 시트 · 스모크 탭 |
| 6-29 | 메뉴 CSV(a 파서·저장·API / b 덧입히기 엔진·「메뉴만」 시트 / c 탭 올리기·안내·열) | `MenusTest` · `AnalyzeRoutesTest.menuCsv` · `ScreensTest` 일치·경유·없음·메뉴만 · 스모크 올리기 |

- **검증**: `bash scripts/verify.sh`(docs 레인) 초록. 커밋 `docs: S0 번들 34 설계 20`.

#### S1 = 6-25a 중지 — JavaGraph tick · 합치기 루프 · 저장 앞 확인
- **대상**: `core/analyze/JavaGraph.java` · `core/analyze/AnalyzeRunner.java` · `web/AnalyzeRoutes.java` · `core/analyze/JavaGraphTest.java`.
- **변경**: ① `JavaGraph`

```java
    public static Graph scan(List<Source> java, Profile.Naming naming, JpaIndex jpa) {
        return scan(java, naming, jpa, null);
    }

    /** @param tick 파일 파싱마다·컨트롤러 클래스마다 부른다 — 취소 확인(6-25). 없으면 null */
    public static Graph scan(List<Source> java, Profile.Naming naming, JpaIndex jpa, Runnable tick) {
        return new JavaGraph(naming, jpa).run(java, tick);
    }

    private Graph run(List<Source> java, Runnable tick) {
        …
        for (Source s : java) {
            if (tick != null) {
                tick.run();
            }
            ParseResult<CompilationUnit> r = parser.parse(s.text());
            …
        for (Cls c : all) {
            if (!c.controller || c.iface) {
                continue;
            }
            if (tick != null) {
                tick.run();
            }
```
② `AnalyzeRunner.run` — `JavaGraph.scan(java, naming, jpa, ctx == null ? null : ctx::checkCancelled)`; 프로그램 합치기 `for (JavaGraph.Program p : graph.programs())` 안 첫 줄에 `if (ctx != null && ++done % PROGRESS_EVERY == 0) { ctx.checkCancelled(); }`(`int done = 0;` 루프 앞). javadoc 한 줄 「취소는 파일·그래프·합치기 어디서든 받는다(6-25)」.
③ `AnalyzeRoutes` job 본문 — `AnalyzeRunner.Result r = AnalyzeRunner.run(...)` 다음 줄에 `jc.checkCancelled(); // 6-25 — 취소된 분석은 이력에 남기지 않는다(5-21 과 같은 약속)`.
④ `JavaGraphTest.scanStopsOnTick`

```java
    /** 6-25 — tick 이 던지면 그래프가 바로 멈춘다(두 번째 파일에서). 예외는 그대로 올라온다 */
    @Test
    void scanStopsOnTick() throws IOException {
        int[] n = {0};
        assertThrows(IllegalStateException.class, () -> JavaGraph.scan(sources(), null, JpaIndex.empty(), () -> {
            if (++n[0] > 1) {
                throw new IllegalStateException("tick");
            }
        }));
        assertEquals(2, n[0]);
        assertEquals(JavaGraph.scan(sources(), null).programs().size(), JavaGraph.scan(sources(), null, JpaIndex.empty(), () -> { }).programs().size(), "tick 이 안 던지면 같다");
    }
```
- **검증**: `-Dtest='JavaGraphTest,AnalyzeRoutesTest,AnalyzeStoreTest'` 초록(골든 `java-graph.json` 불변). **닫힘**: ① 의 첫 `tick.run()` 을 빼면 `assertEquals(2, n[0])` 빨강(컨트롤러 루프에서야 던진다 — 수가 다르다).
- **사다리**: `sources()` 가 둘 미만이면 `fixtures/analyze/java` 가 바뀐 것 — 멈춘다(2장과 다름).

#### S2 = 6-25b 중지 — 화면 · 스모크
- **대상**: `tools/program_analysis.html` · `tools/program_analysis_ext.js` · `web/SmokeHtmlUnitTest.java`.
- **변경**: ① html `.dirbar` 의 `<button class="btn-p" id="run">분석</button>` 뒤에 `<button class="btn-red" id="runStop" disabled>중지</button>`. ② ext.js — `code_check_ext.js` 꼴:

```js
  var jobNow = null;

  function run() {
    var p = $('dir').value.trim();
    if (!p) { msg('폴더 경로를 넣는다', 'err'); return; }
    $('run').disabled = true;
    msg('분석 시작');
    TB.api('/api/analyze/run', { body: { path: p } }).then(function (r) {
      jobNow = r.jobId;
      $('runStop').disabled = false;
      poll(r.jobId);
    }, function (e) { done(); msg(e.message, 'err'); });
  }

  function done() {
    jobNow = null;
    $('run').disabled = false;
    $('runStop').disabled = true;
  }

  /* 6-25 — 분석 중지. 취소된 분석은 이력에 안 남는다(서버가 저장 전에 끊는다) */
  function stop() {
    if (!jobNow) return;
    $('runStop').disabled = true;
    msg('중지하는 중…');
    TB.api('/api/jobs/' + jobNow, { method: 'DELETE' }).then(null, function (e) { msg(e.message, 'err'); });
  }
```
`poll` — RUNNING 가지 그대로, 끝나면 `done();` → `if (j.status === 'CANCELLED') { msg('중지함 — 이력에 남기지 않았다', 'err'); return; }` → 기존 `DONE` 아님 가지 → 기존 결과 처리. `init()` 에 `$('runStop').onclick = stop;`. 옛 `$('run').disabled = false;` 두 줄은 `done()` 으로.
③ 스모크 `programAnalysisStops(@TempDir)` — `codeCheckStops` 꼴: 임시 `proj/src/main/java/g/C<i>.java` **1,500 개**

```java
package g;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
@Controller
public class C<i> {
    @RequestMapping("/g<i>/list.do")
    public String list() {
        return "g<i>/list";
    }
}
```
프로필 `name: t\nframework: egov35\nproject:\n  root: '<proj>'\n` → 화면 열기 → `#dir` 값 = proj → `#run` 클릭 → `#runStop` 이 켜질 때까지 `waitForBackgroundJavaScript(100)` ×50 → `#runStop` 클릭 → 30초 안(100ms ×300)에 `#msg` == 「중지함 — 이력에 남기지 않았다」 → `#run` 켜짐·`#runStop` 꺼짐 → `GET /api/analyze/runs` 가 `[]`.
- **검증**: `-Dtest='SmokeHtmlUnitTest#programAnalysis*,ToolsFolderTest'` 초록(`.btn-red` 는 공용). **닫힘**: `AnalyzeRoutes` 의 `jc.checkCancelled()`(S1 ③)를 빼면 이력이 1 이 되어 빨강 — 되돌린다.
- **사다리**: 중지 전에 끝나면 **4,000 개**로(허용 조건을 넓히지 않는다). 그래도면 `wip/6-25b`.

#### S3 = 6-26a V010 · 뷰 → JSP 파일 수 · 저장
- **대상**: `src/main/resources/db/migration/V010__analyze_screens.sql`(신설) · `core/analyze/AnalyzeRunner.java` · `core/analyze/AnalyzeStore.java` · `core/analyze/AnalyzeStoreTest.java`.
- **변경**: ① V010 — **이 번들의 마이그레이션은 이 파일 하나**(6-27·6-29 열·표도 여기)

```sql
-- 화면 전수(6-26~6-29) — 뷰 이름마다 맞는 JSP 파일 수 · JSP 링크 종류 · 메뉴 CSV(프로필마다). 식별자·경로·메뉴 이름만(규칙 3)
CREATE TABLE analyze_view_file (
  run_id  BIGINT       NOT NULL,
  name    VARCHAR(500) NOT NULL,   -- 뷰 이름(analyze_view.name, kind view)
  files   INT          NOT NULL,   -- 경로가 /<name>.jsp 로 끝나는 JSP 파일 수 — 0 없음 · 1 있음 · 2+ 여럿. 행이 없는 옛 실행은 모름
  PRIMARY KEY (run_id, name),
  FOREIGN KEY (run_id) REFERENCES analyze_run(id) ON DELETE CASCADE
);
-- JSP 가 URL 을 부른 꼴(6-27) — link·form·popup·ajax·script·other. 옛 행은 null(모름)
ALTER TABLE analyze_jsp_link ADD COLUMN kind VARCHAR(10);
-- 메뉴 CSV(6-29) — 프로필마다 한 벌, 올리면 통째로 바꾼다. 경로는 「대 > 중 > 소」, seq 는 CSV 행 순서(트리 순서)
CREATE TABLE analyze_menu (
  profile     VARCHAR(100)  NOT NULL,
  seq         INT           NOT NULL,
  path        VARCHAR(1000) NOT NULL,
  name        VARCHAR(300)  NOT NULL,
  url         VARCHAR(500),
  screen_id   VARCHAR(100),
  use_yn      VARCHAR(10),
  auth        VARCHAR(300),
  uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (profile, seq)
);
```
② `AnalyzeRunner` — `Result` 에 두 필드를 더한 13인자 정규 생성자 `(…, joins, Map<String, Integer> viewFiles, Map<String, List<JspLinks.Link>> jspLinkKinds)`(jsp → (url, kind) 목록 — 한 URL 에 꼴이 여럿이면 항목 여럿); 11인자·10인자 생성자는 빈 맵으로 위임(옛 시험 그대로). compact 에서 `viewFiles = unmodifiable(new TreeMap<>(viewFiles))`, `jspLinkKinds` 도 안쪽 목록까지 복사. `JspLinks.Link` record 는 S6 가 만든다 — **S3 에서는 `JspLinks` 에 `public record Link(String url, String kind) {}` 만 먼저 더해 둔다**(빈 껍데기, 다른 코드 변경 없음). `orphans(index, graph, jsp)` 를 가른다:

```java
    /**
     * 뷰 이름(kind view) → 경로가 /<이름>.jsp 로 끝나는(또는 같은) JSP 파일 수(6-26). 6-11 고아 JSP 와 한 규칙 — 어느 뷰에도 안 세인 JSP 가 고아.
     * @param matched 비어서 들어오고, 어느 뷰에라도 세인 JSP 상대 경로가 채워진다
     */
    static Map<String, Integer> viewFiles(JavaGraph.Graph graph, List<Source> jsp, Set<String> matched) {
        Map<String, Integer> out = new TreeMap<>();
        for (JavaGraph.Program p : graph.programs()) {
            for (JavaGraph.View v : p.views()) {
                if (v.kind().equals("view")) {
                    out.putIfAbsent(v.name().startsWith("/") ? v.name().substring(1) : v.name(), 0);
                }
            }
        }
        for (Source s : jsp) {
            String rel = s.rel().replace('\\', '/');
            String noExt = rel.substring(0, rel.length() - 4);
            for (int i = -1; ; i = noExt.indexOf('/', i + 1)) {
                String cand = noExt.substring(i + 1);
                if (out.containsKey(cand)) {
                    out.merge(cand, 1, Integer::sum);
                    matched.add(rel);
                }
                if (i < 0 ? noExt.indexOf('/') < 0 : noExt.indexOf('/', i + 1) < 0) {
                    break;
                }
            }
        }
        return out;
    }
```
(`i = -1` 첫 바퀴가 전체 경로, 그 뒤 `/` 뒤 접미마다 — 옛 `orphans` 의 `seen` 과 같은 후보 집합. **고아 결과가 같아야 한다.**) `orphans(index, graph, jsp, matched)`: 문장 고아는 그대로, JSP 고아는 `for (Source s : jsp) if (!matched.contains(rel)) out.add(new Orphan("jsp", rel))`. `run()` 끝: `Set<String> matched = new HashSet<>(); Map<String, Integer> vf = viewFiles(graph, jsp, matched);` → `new Result(…, orphans(index, graph, jsp, matched), index.joins(), vf, Map.of())`(jspLinkKinds 는 6-27 이 채운다).
③ `AnalyzeStore.save` — `analyze_view_file` 배치(`cut(name, 500)`), `analyze_jsp_link` INSERT 에 `kind` 열: jsp 마다 `jspLinks` 의 url 을 돌며 `jspLinkKinds.get(jsp)` 에서 그 url 의 kind 목록을 찾아 **kind 마다 한 행**, 목록이 없으면(6-27 전·옛 꼴) kind null 한 행. 읽기 `public Map<String, Integer> viewFiles(long runId)`(TreeMap) · `public record JspLink(String jsp, String url, String kind) {}` · `public List<JspLink> jspLinks(long runId)`(`ORDER BY jsp, url, kind`).
④ `AnalyzeStoreTest.result()` 를 13인자로(`viewFiles = Map.of("bbs/BoardList", 1)`, `jspLinkKinds = Map.of("bbs/BoardList.jsp", List.of(new JspLinks.Link("/bbs/list.do", "link"), new JspLinks.Link("/bbs/list.do", "popup")))`) · 왕복 단언 `store.viewFiles(id)` == 그 맵 · `store.jspLinks(id)` 에 `("bbs/BoardList.jsp", "/bbs/list.do", "link")`·`("bbs/BoardList.jsp", "/bbs/list.do", "popup")`·`("bbs/BoardList.jsp", "/bbs/add.do", null)`·`("bbs/Other.jsp", "/bbs/list.do", null)` 넷 · `impact(id, "COMTNBBS")` 의 jsps 는 그대로(중복 없음 — `DISTINCT`).
- **검증**: `-Dtest='AnalyzeStoreTest,AnalyzeRoutesTest,ConsistencyTest,JavaGraphTest'` 초록. 표본 폴더가 있으면 `AnalyzeCorpusTest`(`analyze-egov-orphan-jsp.txt` 불변이 D2 의 증거). **닫힘**: `viewFiles` 의 `matched.add` 를 빼면 corpus 고아 JSP 가 전부로 늘어 빨강(로컬) · `AnalyzeRoutesTest.consistency` 의 `orphanJsps` 에 `BoardDetail.jsp` 가 들어와 빨강(CI).
- **사다리**: corpus 고아 JSP 가 달라지면 후보 집합이 옛 코드와 다른 것 — 옛 `orphans` 루프와 글자 대조해 고친다(규칙을 바꾸지 않는다). 둘 넘으면 멈춘다.

#### S4 = 6-26b 정합성 「없는 JSP」 — 엔진 · xlsx
- **대상**: `core/analyze/Consistency.java` · `core/analyze/ConsistencyTest.java` · `web/AnalyzeRoutes.java` · `web/AnalyzeRoutesTest.java`.
- **변경**: ① `Consistency` — `public record MissingJsp(String view, int programs) {}` · `Report` 마지막 자리에 `List<MissingJsp> missingJsps`(compact 에서 copyOf). `of()` 앞부분(스냅샷 유무와 무관)에

```java
        // 6-26 — view 가 가리키는데 폴더에 없는 JSP(코드만 보는 값 — 스냅샷 없이도 나온다). 옛 실행(view_file 없음)은 빔
        Map<String, Integer> files = analyze.viewFiles(runId);
        Map<String, Integer> gone = new TreeMap<>();
        for (AnalyzeStore.ProgramRow r : analyze.programs(runId)) {
            for (JavaGraph.View v : r.views()) {
                String name = v.name().startsWith("/") ? v.name().substring(1) : v.name();
                if (v.kind().equals("view") && Integer.valueOf(0).equals(files.get(name))) {
                    gone.merge(name, 1, Integer::sum);
                }
            }
        }
        List<MissingJsp> missingJsps = new ArrayList<>();
        gone.forEach((v, n) -> missingJsps.add(new MissingJsp(v, n)));
```
두 `new Report(…)` 끝에 `missingJsps`. javadoc 목록에 한 줄. ② `ConsistencyTest` — `AnalyzeStoreTest.result()`(S3 뒤 `bbs/BoardList` = 1)로는 빔 → 새 시험 `missingJsps`: `result()` 의 Row 둘 중 `list`(뷰 `bbs/BoardList`)에 더해 뷰 `view:bbs/Gone` 인 Program 을 하나 더 넣은 Result(13인자, `viewFiles = Map.of("bbs/BoardList", 1, "bbs/Gone", 0)`)를 저장 → `of(…, null)` 의 `missingJsps() == List.of(new MissingJsp("bbs/Gone", 1))`; 옛 꼴(10인자 생성자) 저장 → 빔. ③ `AnalyzeRoutes` export 정합성 시트에 `r.missingJsps().forEach(x -> rows.add(Arrays.asList("없는 JSP", x.view(), "", "", String.valueOf(x.programs()), "")))`(「안 쓰는 표」 뒤). ④ `AnalyzeRoutesTest.consistency` — `c.get("missingJsps")` 의 view 집합에 `sample/bbs/BoardList` 가 있고 `sample/bbs/BoardDetail` 은 없다; `exportXlsx` 정합성 시트 행 글에 `없는 JSP|sample/bbs/BoardList|`.
- **검증**: `-Dtest='ConsistencyTest,AnalyzeRoutesTest'` 초록. **닫힘**: `Integer.valueOf(0).equals` 를 `files.get(name) == null` 로 바꾸면(옛 실행도 없음으로) `ConsistencyTest` 옛 꼴 단언 빨강.

#### S5 = 6-26c 정합성 탭 다섯째 칸
- **대상**: `tools/program_analysis.html` · `tools/program_analysis_ext.js` · `web/SmokeHtmlUnitTest.java`.
- **변경**: ① html 정합성 `.grid` 에 다섯째 `<div class="cell"><span class="opt">view 가 가리키는데 없는 JSP</span><div class="box" id="conMissingJsp"></div></div>`; style `#paneConsistency .grid { … grid-template-rows: 1fr 1fr; }` → `grid-auto-rows: 1fr;`. ② ext.js `consistency()` — 스냅샷 유무와 무관하게 `TB.table($('conMissingJsp'), ['view', '프로그램 수'], r.missingJsps.map(function (x) { return [x.view, x.programs]; }));` · `m.textContent` 끝에 `' · 없는 JSP ' + r.missingJsps.length`. ③ 스모크 `programAnalysisRuns` 정합성 부분 — `#conMissingJsp tbody tr` ≥ 1 · 그 글에 `sample/bbs/BoardList` · `conPane` 에 「없는 JSP」 있고 「뷰」 없음(기존 단언 유지).
- **검증**: `-Dtest='SmokeHtmlUnitTest#programAnalysisRuns,ToolsFolderTest'` 초록. **닫힘**: ② 를 스냅샷 가지 안으로 옮기면 스모크(스냅샷 없이) 빨강.

#### S6 = 6-27 JSP 링크 종류
- **대상**: `core/analyze/JspLinks.java` · `core/analyze/JspLinksTest.java` · `core/analyze/AnalyzeRunner.java` · (`AnalyzeStore` 는 S3 에서 kind 를 이미 쓴다).
- **변경**: ① `JspLinks`

```java
    /** 어떤 꼴로 불렸나(추정 — 단서) — link(href·c:import)·form(action)·popup(window.open)·ajax(url:·$.get/post/ajax·.load)·script(location)·other(단서 없음·변수에 담음) */
    public record Link(String url, String kind) {
    }

    /** 토큰 앞 글에서 가장 가까운 단서 하나(6-27, D3). c:url var 는 변수에 담는 꼴이라 other. location.href 는 한 덩이라 href 보다 앞에서 잡혀 script */
    private static final Pattern CUE = Pattern.compile(
            "c:url\\s+var|window\\.open|\\.open\\(|action|location\\.(?:href|replace)|location\\s*=|href|c:import|url\\s*:|\\$\\.(?:get|post|ajax)\\(|\\.load\\(|ajax");
    static final int CUE_WINDOW = 200;

    static String kind(String text, int start) {
        String win = text.substring(Math.max(0, start - CUE_WINDOW), start);
        Matcher m = CUE.matcher(win);
        String last = null;
        while (m.find()) {
            last = m.group();
        }
        if (last == null || last.startsWith("c:url")) return "other";
        if (last.startsWith("window.open") || last.equals(".open(")) return "popup";
        if (last.equals("action")) return "form";
        if (last.startsWith("location")) return "script";
        if (last.equals("href") || last.equals("c:import")) return "link";
        return "ajax";
    }
```
`Result(List<Link> links, List<Unresolved> unresolved)` + `public List<String> urls()`(links 의 url 을 중복 없이 정렬 — `TreeSet`). links 는 (url, kind) 쌍 중복 없이 url → kind 순 정렬(`TreeSet<Link>` 비교자 또는 `TreeMap<String, TreeSet<String>>` 로 모은 뒤 펼침). `extract` 의 `urls.add(url)` → 그 url 에 `kind(text, start)` 를 더한다(**같은 URL 의 다른 꼴은 전부 남긴다**). javadoc 에 D3 한 줄.
② `JspLinksTest.kinds` — 한 JSP 글에 일곱 꼴: `<a href="<c:url value='/a.do'/>">` → link · `<form:form action="<c:url value='/b.do'/>" method="post">` → form · `window.open("<c:url value='/c.do'/>", "pop")` → popup · `$.ajax({ url: "<c:url value='/d.do'/>",` → ajax · `location.href = "<c:url value='/e.do'/>";` → script · `<a href="/z.do">…</a>` 다음 줄 `<c:url var="x" value="/f.do"/>` → other(앞 줄의 `href` 가 아니라 `c:url var` 가 가깝다) · `<c:import url="/g.do"/>` → link; `urls()` 는 여덟 정렬(중복 없음); 같은 URL 두 꼴(`<a href="…/a.do">` 뒤 `window.open("…/a.do")`) → `links` 에 `(a.do, link)`·`(a.do, popup)` 둘, `urls()` 엔 하나. 기존 `golden`·`emptyText` 그대로(`urls()` 유지로 골든 불변).
③ `AnalyzeRunner.run` JSP 루프 — `jspLinks.put(s.rel(), jr.urls())` 유지 + `jspLinkKinds.put(s.rel(), jr.links());` → `Result(…, vf, jspLinkKinds)`.
- **검증**: `-Dtest='JspLinksTest,AnalyzeStoreTest,AnalyzeRoutesTest'` 초록 + 표본 있으면 `AnalyzeCorpusTest`(`jspUrls`·`jspLinkedPrograms` 불변 — URL 집합은 그대로). 라우트 단언은 S7 의 `/screens` 에서 `BoardList.jsp [link]` 로 잰다. **닫힘**: CUE 에서 `location\\.(?:href|replace)` 를 빼면 `/e.do` 가 link 로 나와 빨강 · `c:url\\s+var` 를 빼면 `/f.do` 가 link 로 나와 빨강.
- **사다리**: 정규식이 `.open(` 를 `$.ajax(` 안의 … 같은 데서 오잡으면 꼴 시험을 실물 꼴로 고치되 규칙 자체(가장 가까운 단서)는 유지. 둘 넘으면 `other` 로 두고 이력에.

#### S7 = 6-28a 화면 전수 — `Screens` · API · xlsx 시트
- **대상**: `core/analyze/Screens.java`(신설) · `core/analyze/ScreensTest.java`(신설) · `web/AnalyzeRoutes.java` · `web/AnalyzeRoutesTest.java`.
- **변경**: ① `Screens`

```java
/**
 * 화면 전수표(6-28) — 뷰(JSP)를 돌려주는 프로그램 하나가 한 행. 코드 사실만 적는다 — 화면명·유형은 추정하지 않는다(사용자 2026-10-10).
 * 메뉴 CSV(6-29)가 있으면 경로·이름·근거를 덧입힌다. 계산은 여기 한 곳 — 화면·xlsx 가 같은 값. 식별자·경로·메뉴 이름만(규칙 3)
 */
public final class Screens {

    public static final String FILE_YES = "있음";
    public static final String FILE_NO = "없음";
    public static final String FILE_MANY = "여럿";
    public static final String FILE_UNKNOWN = "모름";

    /** kinds — 그 JSP 가 이 URL 을 부른 꼴 전부(link·form·popup·ajax·script·other, 저장 kind null 은 「모름」), 정렬 */
    public record Caller(String jsp, List<String> kinds) {
    }

    /** menuBasis — 일치·경유·없음, 메뉴가 없으면 null. menuPaths 는 seq 순(사용 Y 먼저) */
    public record Row(int no, String module, String url, String verb, String params, String program, String file, int line, List<String> views,
            String jspFile, Map<String, String> crud, List<Caller> callers, List<String> menuPaths, String menuName, String screenId,
            String useYn, String auth, String menuBasis) {
    }

    public record Excluded(String kind, int count) {
    }

    public record MenuOnly(int seq, String path, String url, String useYn) {
    }

    public record Report(List<Row> rows, List<Excluded> excluded, boolean menuLoaded, int menuRows, List<MenuOnly> menuOnly) {
    }

    /** @param viewFiles 뷰 이름 → JSP 파일 수(옛 실행은 빈 맵 → 모름) @param menu 없으면 빈 목록 */
    public static Report of(List<AnalyzeStore.ProgramRow> programs, Map<String, Integer> viewFiles, List<AnalyzeStore.JspLink> links,
            List<Menus.Row> menu)
```
알고리즘(순서대로): (ㄱ) `callersByUrl`: links 를 url → jsp 로 묶어 `Caller(jsp, kinds 정렬·중복 없음, null 은 "모름")`(jsp 순). (ㄴ) 프로그램마다: `kind.equals("json")` → excluded `json`; views 중 `view` 종류 = `vs`; `vs` 비고 views 안 비면 → excluded(첫 뷰 kind); 그 밖 행. (ㄷ) 행 — `module = CrudViews.module(url)`, `views = vs 이름(앞 / 뗌)`, `jspFile`: vs 빈 → `모름`; 아니면 뷰마다 `viewFiles.get(name)` null → 모름 / 0 → 없음 / 1 → 있음 / ≥2 → 여럿, 서로 다르면 `·` 로 잇는다(순서 있음·여럿·없음·모름). `crud` = `r.crud()` 그대로. `callers = callersByUrl.getOrDefault(url, List.of())`. 메뉴 칸은 S9 까지 null·빈 목록. (ㄹ) 정렬 모듈 → URL → file → line, `no` 1부터. (ㅁ) excluded 는 kind 순 `json·redirect·forward·class`(0 은 뺀다). `menuLoaded = !menu.isEmpty()`, `menuRows = menu.size()`, `menuOnly` 는 S9.
② `ScreensTest` — `ProgramRow` 헬퍼(`CrudViewsTest.row` 꼴 + views·kind 인자): A `/bbs/list.do` view `sample/bbs/BoardList`(files 0) · B `/bbs/detail.do` view `sample/bbs/BoardDetail`(files 1) + view `sample/bbs/Other`(files 2) · C `/bbs/json.do` kind json · D `/bbs/go.do` views `redirect:/x` 만 · E `/bbs/dyn.do` views 빔(동적) · F `/other` view `sample/other/List`(viewFiles 에 없음 → 모름). links: `jsp/bbs/List.jsp → /bbs/detail.do (link)` · `jsp/bbs/List.jsp → /bbs/detail.do (popup)` · `jsp/bbs/List.jsp → /bbs/list.do (null)`. 단언: 행 넷(A·B·E·F) 순서 `bbs`(list·detail·dyn ⇢ URL 순 `/bbs/detail.do`·`/bbs/dyn.do`·`/bbs/list.do`) 뒤 `other`; A `jspFile == 없음`, B `있음·여럿`, E `모름`, F `모름`; B callers `[List.jsp [link, popup]]`, A callers `[List.jsp [모름]]`; excluded `[json 1, redirect 1]`; `menuLoaded false`.
③ `AnalyzeRoutes` — `GET /api/analyze/runs/{id}/screens` → `Screens.of(store.programs(id), store.viewFiles(id), store.jspLinks(id), List.of())`(메뉴는 S9 에서) · export 시트 「화면전수」 를 CRUD모듈 뒤에: 열 `No·모듈·URL·verb·params·프로그램·소스·JSP·JSP 파일·표·CRUD·부르는 화면 수·부르는 화면 · 부르는 꼴(단서)·메뉴·메뉴명·화면ID·사용여부·권한·근거`(소스 `file:line`, 표·CRUD 는 `표(글자)` 를 ` · ` 로, 부르는 화면은 `jsp (꼴·꼴)` 를 `; ` 로 — 꼴은 한글 글(링크·폼·팝업·ajax·스크립트·기타·모름), 메뉴 칸은 S9 전엔 빈 글) · 응답 `sheets` 가 따라온다. javadoc 한 줄.
④ `AnalyzeRoutesTest.screens`(runAndHistory 꼴로 실행 하나) — `rows` 중 `url == /bbs/list.do` 행: `views == ["sample/bbs/BoardList"]`, `jspFile == 없음`, `module == bbs`, `callers` 에 `{jsp: …/jsp/bbs/BoardList.jsp, kinds: ["link"]}`(6-27 — 픽스처 JSP 가 `<a href="<c:url value='/bbs/list.do'/>">`); `excluded` 에 `json` 이 있다; `menuLoaded false`; 404 `runs/999/screens`. `exportXlsx` 시트 이름 → `[프로그램목록, CRUD목록, CRUD모듈, 화면전수, 미해결]`(스냅샷 있으면 끝에 정합성) · 화면전수 머리 첫 셋 `No|모듈|URL`, 행 수 == `/screens` rows 수.
- **검증**: `-Dtest='ScreensTest,AnalyzeRoutesTest,CodeCliTest'` 초록(CLI 는 시트 이름 안 본다 — 파일 1). **닫힘**: (ㄴ) 에서 redirect 만인 프로그램을 행에 넣으면 `ScreensTest` 행 수 빨강.
- **사다리**: 픽스처 `/bbs/list.do` 의 `callers.kind` 가 `link` 가 아니면(`<c:url>` 앞 `href` 가 200자 밖?) 픽스처 JSP 를 보고 `CUE_WINDOW` 를 넓히지 말고 단언을 실물 종류로 — 단서가 없으면 `other` 가 맞다. 이력에.

#### S8 = 6-28b 화면 전수 탭
- **대상**: `tools/program_analysis.html` · `tools/program_analysis_ext.js` · `web/SmokeHtmlUnitTest.java` · (`tools/index.html` 설명 한 줄 — 파일 수 규칙 밖).
- **변경**: ① html — 탭바 `#tabCrud` 뒤 `<span class="t" id="tabScreens">화면 전수</span>`; 패널

```html
<div class="pane" id="paneScreens">
	<div class="bar">
		<span class="opt">찾기 <input type="text" id="fScr" spellcheck="false" placeholder="URL·프로그램·JSP·메뉴"></span>
		<span class="count" id="scrCount"></span>
		<span class="count" id="scrExcluded"></span>
	</div>
	<div class="bar" id="menuBar"></div>
	<div class="box pick" id="screens"></div>
	<div id="scrDetail" class="well">행을 누르면 부르는 화면 · view · 표 · 메뉴 경로</div>
	<div class="box" id="menuOnly" hidden></div>
</div>
```
style `#screens { flex: 3; } #scrDetail { flex: 1; min-height: 80px; overflow: auto; padding: 8px 10px; white-space: pre; line-height: 1.5; color: var(--code); } #menuOnly { flex: 1; }`. `#menuBar` 내용은 S10(6-29c)이 채운다 — 이 스텝에선 글 「메뉴 없음 — CSV 를 올리면 메뉴 경로·연결 근거가 붙는다(6-29)」 만. `TABS` 에 `['tabScreens', 'paneScreens']`(CRUD 뒤).
② ext.js — `var screens = { rows: [], excluded: [], menuLoaded: false, menuOnly: [] };` · `load(id)` 가 `/screens` 도 받아(`TB.api` 체인에 하나 더) `renderScreens()`.

```js
  // 6-28 — 화면 전수. 값은 전부 서버(Screens) — 여기서는 거르고 그릴 뿐
  var FILE_WORD = { '있음': '있음', '없음': '없음', '여럿': '여럿', '모름': '모름' };
  var KIND_WORD = { link: '링크', form: '폼', popup: '팝업', ajax: 'ajax', script: '스크립트', other: '기타', '모름': '모름' };

  // 부르는 꼴은 단서로 정한 추정값(6-27 D3) — 글에도 「(단서)」 를 붙인다
  function callerText(c) { return c.jsp + ' (' + c.kinds.map(function (k) { return KIND_WORD[k] || k; }).join('·') + ')'; }

  function renderScreens() {
    var q = low($('fScr').value.trim());
    var rows = screens.rows.filter(function (r) {
      if (!q) return true;
      return (low(r.url) + ' ' + low(r.program) + ' ' + low(r.views.join(' ')) + ' ' + low((r.menuPaths || []).join(' '))).indexOf(q) >= 0;
    });
    var heads = ['No', '모듈', 'URL', 'verb', '프로그램', 'JSP', 'JSP 파일', '표·CRUD', '부르는 화면'];
    if (screens.menuLoaded) heads = heads.concat(['메뉴', '화면ID', '사용', '근거']);
    var t = TB.table($('screens'), heads, rows.map(function (r) {
      var crud = Object.keys(r.crud || {}).map(function (k) { return k + '(' + r.crud[k] + ')'; }).join(' · ');
      var base = [r.no, r.module, r.url + (r.params ? ' ' + r.params : ''), r.verb, r.program, r.views.join(' · '), r.jspFile, crud, r.callers.length];
      if (screens.menuLoaded) base = base.concat([(r.menuPaths || []).join('; '), r.screenId || '', r.useYn || '', r.menuBasis || '']);
      return base;
    }));
    var trs = t.tBodies[0].rows;
    for (var i = 0; i < trs.length; i++) bindScreenRow(trs[i], rows[i]);
    $('scrCount').textContent = rows.length + ' / ' + screens.rows.length;
    $('scrExcluded').textContent = screens.excluded.length ? '제외 — ' + screens.excluded.map(function (x) { return x.kind + ' ' + x.count; }).join(' · ') : '';
    renderMenuOnly();
  }

  function bindScreenRow(tr, r) {
    tr.onclick = function () {
      if (scrSelected) scrSelected.className = '';
      scrSelected = tr;
      tr.className = 'sel';
      var lines = [r.program + '  ' + r.verb + ' ' + r.url + (r.params ? ' ' + r.params : ''), r.file + ':' + r.line, '', 'view'];
      r.views.forEach(function (v) { lines.push('  ' + v); });
      lines.push('', '부르는 화면 ' + r.callers.length + ' — 꼴은 단서로 정한 추정');
      r.callers.forEach(function (c) { lines.push('  ' + callerText(c)); });
      lines.push('', 'CRUD');
      Object.keys(r.crud || {}).sort().forEach(function (k) { lines.push('  ' + k + '  ' + crudWords(r.crud[k])); });
      if (screens.menuLoaded) {
        lines.push('', '메뉴 ' + (r.menuBasis || ''));
        (r.menuPaths || []).forEach(function (p) { lines.push('  ' + p); });
      }
      $('scrDetail').textContent = lines.join('\n');
    };
  }

  function renderMenuOnly() {
    var box = $('menuOnly');
    box.hidden = !screens.menuLoaded;
    if (!screens.menuLoaded) { box.innerHTML = ''; return; }
    TB.table(box, ['메뉴에만 있는 URL — 순서', '메뉴', 'URL', '사용'], screens.menuOnly.map(function (m) { return [m.seq, m.path, m.url, m.useYn || '']; }));
  }
```
`var scrSelected = null;` · `init()` 에 `$('fScr').oninput = renderScreens;`. `TB.table` 의 셀은 글자뿐이라 `KIND_WORD` 글은 상세에서만 — 표의 「부르는 화면」 칸은 수. `FILE_WORD` 는 쓰지 않으면 지운다(서버 글 그대로 보인다).
③ `index.html:87` 설명 끝에 「 화면 전수(메뉴 CSV 덧입히기)」. ④ 스모크 `programAnalysisRuns` 에 — `#tabScreens` 클릭 → `#screens tbody tr` 수 == `GET /api/analyze/runs/<id>/screens` 의 rows 수(HTTP 로 받아 비교, 또는 ≥ 3) · 첫 th 「No」 · `#scrExcluded` 글에 `json` · `/bbs/list.do` 행의 「JSP 파일」 칸 == 「없음」 · 그 행 클릭 → `#scrDetail` 에 `BoardList.jsp (링크)` · `#menuOnly` hidden.
- **검증**: `-Dtest='SmokeHtmlUnitTest#programAnalysisRuns,ToolsFolderTest,LauncherTest'` 초록. **닫힘**: `renderScreens` 가 `r.jspFile` 대신 빈 글을 넣으면 「없음」 단언 빨강.

#### S9 = 6-29a 메뉴 CSV — 파서 · 저장 · API
- **대상**: `core/analyze/Menus.java`(신설) · `core/analyze/MenusTest.java`(신설) · `core/analyze/AnalyzeStore.java` · `web/AnalyzeRoutes.java` · `web/AnalyzeRoutesTest.java`.
- **변경**: ① `Menus`

```java
/**
 * 메뉴·화면 목록 CSV(6-29) — 경로 한 열(「대 > 중 > 소」, 깊이 제한 없음)과 URL. 트리 재귀는 사용자가 뽑는 SQL 쪽, 행 순서가 트리 순서.
 * 열은 Csv.guess 로 헤더 이름을 맞춘다(ColumnInputs.fromCsv 와 같은 결). 메뉴 이름·URL 만(규칙 3)
 */
public final class Menus {

    static final List<String> PATH = List.of("메뉴경로", "메뉴", "경로", "menupath", "menu", "path");
    static final List<String> URL = List.of("url", "주소", "링크");
    static final List<String> SCREEN = List.of("화면id", "화면번호", "screenid", "screen_id");
    static final List<String> USE = List.of("사용여부", "useyn", "use_yn", "use_at", "사용");
    static final List<String> AUTH = List.of("권한", "authority", "auth", "role");
    static final Set<String> YES = Set.of("Y", "1", "TRUE", "사용", "예");
    static final Set<String> NO = Set.of("N", "0", "FALSE", "미사용", "아니오");

    public record Row(int seq, String path, String name, String url, String screenId, String useYn, String auth) {
    }

    /** withUrl — URL 있는 행(전수 이음에 쓰는 것) · warnings — 사용여부 값이 Y/N 밖인 행 수 등 한 줄씩 */
    public record Parsed(List<Row> rows, int withUrl, List<String> warnings) {
    }

    public static Parsed parse(String csv) {
        List<List<String>> rows = Csv.parse(csv);
        if (rows.size() < 2) throw new IllegalArgumentException("데이터가 부족하다 — 헤더와 한 줄 이상");
        List<String> h = rows.get(0);
        int pi = Csv.guess(h, PATH, true), ui = Csv.guess(h, URL, true);
        if (pi < 0 || ui < 0) throw new IllegalArgumentException("메뉴 경로 열(" + String.join("·", PATH) + ")과 URL 열(" + String.join("·", URL) + ")이 있어야 한다");
        int si = Csv.guess(h, SCREEN, true), yi = Csv.guess(h, USE, true), ai = Csv.guess(h, AUTH, true);
        … 행마다: path 마디 = split("[>＞]") → trim → 빈 마디 제거 → join(" > "); 비면 건너뛰고 경고 「n행 경로 없음」; name = 마지막 마디;
           url = cell.trim(), '?' 뒤 뗌, 비면 null; useYn = 정규화(위 집합, 대문자 비교) 아니면 원글 + 경고 수; seq = 데이터 행 번호(1부터)
    }

    /** 전수 이음용 — URL 정확 일치(쿼리 뗀 뒤). 경유·없음 판정은 Screens */
    public static String normUrl(String url) { … trim, '?' 뒤 뗌, 빈 글은 null }
}
```
② `MenusTest` — 헤더 `메뉴,URL,화면ID,사용여부` 와 영문 `menu,url,use_yn` 둘 다 읽힘 · `대 > 중 >소` → `대 > 중 > 소`·name `소` · 전각 `＞` · URL `?x=1` 뗌 · 빈 URL → null(withUrl 안 셈) · 사용여부 `1`→Y `아니오`→N `보류`→원글+경고 · 헤더 없으면 `IllegalArgumentException` · 데이터 한 줄 미만 예외 · `Csv.decode` 로 BOM 글도 통과(`parse(Csv.decode(bytes))`).
③ `AnalyzeStore` — `public record MenuInfo(int rows, int withUrl, LocalDateTime uploadedAt) {}` · `saveMenu(String profile, List<Menus.Row> rows)`(한 트랜잭션: `DELETE FROM analyze_menu WHERE profile = ?` → INSERT 배치, `cut` 길이) · `List<Menus.Row> menu(String profile)`(`ORDER BY seq`) · `Optional<MenuInfo> menuInfo(String profile)`(`COUNT(*)·COUNT(url)·MAX(uploaded_at)`, 0 이면 empty) · `int deleteMenu(String profile)`.
④ `AnalyzeRoutes` — `record MenuRequest(String csv) {}`:
- `GET /api/analyze/menu` → 활성 프로필 없으면 `{loaded:false}`; 있으면 `menuInfo` → `{loaded, rows, withUrl, uploadedAt}`.
- `POST /api/analyze/menu {csv}` → 프로필 없으면 400 「활성 프로필이 없다」; `csv` 비면 400; `Menus.parse` 의 `IllegalArgumentException` → 400 메시지 그대로; 저장 → `{rows, withUrl, warnings}`.
- `DELETE /api/analyze/menu` → `{deleted}`.
javadoc 「메뉴 CSV 는 프로필마다 한 벌(6-29)」. 글(CSV)은 저장 뒤 버린다 — 로그에 안 쓴다.
⑤ `AnalyzeRoutesTest.menuCsv` — `GET` → loaded false · `POST {csv: "메뉴,URL,사용여부\n게시판 > 목록,/bbs/list.do,Y\n게시판 > 없는,/nope.do,N\n관리,,Y\n"}` → rows 3·withUrl 2 · `GET` loaded true rows 3 · `POST` 헤더 틀린 CSV → 400 · `DELETE` → deleted 3 → `GET` false. (프로필 t 활성.)
- **검증**: `-Dtest='MenusTest,AnalyzeStoreTest,AnalyzeRoutesTest'` 초록. **닫힘**: `saveMenu` 의 DELETE 를 빼면 두 번 올렸을 때 PK 충돌 — 시험에 `POST` 두 번 뒤 rows 가 그대로 3 인 단언을 둔다.

#### S10 = 6-29b 메뉴 덧입히기 — `Screens` · 「메뉴만」 시트
- **대상**: `core/analyze/Screens.java` · `core/analyze/ScreensTest.java` · `web/AnalyzeRoutes.java` · `web/AnalyzeRoutesTest.java`.
- **변경**: ① `Screens.of(…, menu)` 메뉴 단계(행을 다 만든 뒤): (ㄱ) `byUrl`: `Menus.normUrl(m.url()) != null` 인 메뉴를 url → 목록(seq 순). (ㄴ) 행마다 `byUrl.get(normUrl(row.url))` 있으면 「일치」 — 정렬: 사용 Y 먼저, 다음 seq; `menuPaths` 전부, `menuName/screenId/useYn/auth` 는 첫 것. (ㄷ) 경유: 「일치」 가 아닌 행마다 `callers` 의 jsp 가운데 **「일치」 행의 뷰에 맞는 JSP**(D2 규칙 — jsp 경로 `.jsp` 뗀 것이 그 행의 view 이름과 같거나 `/이름` 으로 끝남)인 것을 찾아, 그 행들의 메뉴를 모아 같은 정렬 → `menuBasis = 경유`. (ㄹ) 나머지 「없음」. 메뉴가 비면 전부 null. (ㅁ) `menuOnly` = URL 있는 메뉴 중 어느 행 URL 과도 안 맞는 것(seq 순). `Row` 는 record 라 `with…` 보조 메서드 하나(`withMenu(paths, name, screenId, useYn, auth, basis)`)로 새 행을 만든다.
② `ScreensTest.menu` — S7 픽스처에 메뉴: `게시판 > 목록 | /bbs/list.do | Y` · `게시판 > 목록(옛) | /bbs/list.do?x=1 | N` · `게시판 > 없는 | /nope.do | Y` · `관리 | (없음)`: A(`/bbs/list.do`) 「일치」·`menuPaths == [게시판 > 목록, 게시판 > 목록(옛)]`(Y 먼저)·`menuName == 목록`·`useYn Y`; B(`/bbs/detail.do`, 부르는 JSP `jsp/bbs/List.jsp`)는 — A 의 뷰가 `sample/bbs/BoardList` 라 `jsp/bbs/List.jsp` 는 안 맞음 → 「없음」; 그래서 픽스처에 G `/bbs/pop.do` 를 더하고 links 에 `jsp/sample/bbs/BoardList.jsp → /bbs/pop.do (popup)` 를 두어 **G 가 「경유」**(A 의 메뉴 상속) · `menuOnly == [{3, 게시판 > 없는, /nope.do, Y}]` · `menuLoaded true, menuRows 4`.
③ `AnalyzeRoutes` — `/screens` 와 export 가 `store.menu(run.profile())`(`store.run(id).get().profile()` 이 null 이면 빈 목록)을 넘긴다; export 시트 「메뉴만」(`순서·메뉴·URL·사용여부`)을 화면전수 뒤에 **메뉴가 있을 때만**; 화면전수 메뉴 열 채움(경로 `; `).
④ `AnalyzeRoutesTest.screens` 에 — S9 의 메뉴를 올린 뒤 `/screens`: `menuLoaded true`, `/bbs/list.do` 행 `menuBasis 일치`·`menuPaths [게시판 > 목록]`, `menuOnly` 에 `/nope.do`; `exportXlsx`: 메뉴 올린 뒤 시트에 「메뉴만」 이 「화면전수」 뒤에 들어오고 지우면 빠진다(시험 끝에 `DELETE` 로 되돌려 다른 시험에 안 번지게).
- **검증**: `-Dtest='ScreensTest,AnalyzeRoutesTest'` 초록. **닫힘**: (ㄴ) 의 Y-먼저 정렬을 빼면 `menuName` 단언 빨강.

#### S11 = 6-29c 탭의 메뉴 올리기 · 안내 · 열
- **대상**: `tools/program_analysis.html` · `tools/program_analysis_ext.js` · `web/SmokeHtmlUnitTest.java`.
- **변경**: ① html `#menuBar` 안:

```html
<span class="count" id="menuMsg"></span>
<input type="file" id="menuFile" accept=".csv,.txt">
<button class="btn-p" id="menuUpload">메뉴 CSV 올리기</button>
<button class="btn-red" id="menuClear" disabled>메뉴 지우기</button>
<span class="t" id="menuHelpToggle" title="CSV 꼴과 메뉴 표에서 뽑는 SQL 예시">CSV 꼴·SQL 예시</span>
```
`#paneScreens` 안 `#menuBar` 뒤에 `<div id="menuHelp" class="well" hidden>…</div>` — 글: 「CSV 열 — 메뉴(경로 `대 > 중 > 소`, 깊이 제한 없음) · URL · 화면ID(선택) · 사용여부(선택, Y/N) · 권한(선택). 행 순서가 트리 순서(ORDER SIBLINGS BY 등). 재귀는 SQL 에서.」 + `<pre class="sql" id="menuSqlEgov">`(egov — `COMTNMENUINFO`+`COMTNPROGRMLIST`, Oracle CONNECT BY) · `<pre id="menuSqlRec">`(일반 자기 참조 표 — `WITH RECURSIVE`, PG·MySQL 8·MSSQL 공통 꼴, Oracle 은 위 CONNECT BY) 둘 + 각각 `<button class="btn-green">복사</button>`(`TB.copy(pre, msgEl)`). SQL 글은 복사만 — 실행 버튼 없음(1-36 규칙).

```sql
-- egov 메뉴 → CSV(Oracle). 경로는 CONNECT BY, 순서는 ORDER SIBLINGS BY
SELECT LTRIM(SYS_CONNECT_BY_PATH(m.MENU_NM, ' > '), ' > ') AS "메뉴",
       p.URL AS "URL", NULL AS "화면ID", 'Y' AS "사용여부"
FROM   COMTNMENUINFO m LEFT JOIN COMTNPROGRMLIST p ON p.PROGRM_FILE_NM = m.PROGRM_FILE_NM
START WITH m.UPPER_MENU_NO = 0
CONNECT BY PRIOR m.MENU_NO = m.UPPER_MENU_NO
ORDER SIBLINGS BY m.MENU_ORDR;
```

```sql
-- 자기 참조 메뉴 표 → CSV(PostgreSQL·MySQL 8·MSSQL — WITH RECURSIVE / WITH). 열 이름은 사업 표에 맞게
WITH RECURSIVE t AS (
  SELECT menu_id, parent_id, menu_nm AS path, url, use_yn, LPAD(CAST(sort_no AS CHAR(6)), 6, '0') AS ord
  FROM   menu WHERE parent_id IS NULL
  UNION ALL
  SELECT c.menu_id, c.parent_id, CONCAT(t.path, ' > ', c.menu_nm), c.url, c.use_yn, CONCAT(t.ord, '.', LPAD(CAST(c.sort_no AS CHAR(6)), 6, '0'))
  FROM   menu c JOIN t ON c.parent_id = t.menu_id
)
SELECT path AS "메뉴", url AS "URL", use_yn AS "사용여부" FROM t ORDER BY ord;
```
② ext.js — `loadMenuInfo()`(`GET /api/analyze/menu` → `#menuMsg` 「메뉴 없음 — CSV 를 올리면 메뉴 경로·연결 근거가 붙는다」 / 「메뉴 n행(URL m) · yyyy-MM-dd HH:mm」, `#menuClear` 켬·끔) — `init()`·`load()` 뒤·올리기·지우기 뒤. `menuUpload`: `#menuFile.files[0]` 없으면 `#menuMsg` err 「CSV 파일을 고른다」; `FileReader.readAsArrayBuffer` → UTF-8(`fatal`)/EUC-KR 디코드(`logical_name.html readCsvFile` 꼴) → `POST /api/analyze/menu {csv}` → 「올림 — n행(URL m)」 + 경고 수 → `loadMenuInfo()` → `runId` 있으면 `/screens` 다시 받아 `renderScreens()`. `menuClear`: `DELETE` → 같은 갱신. `menuHelpToggle`: `#menuHelp.hidden` 토글. 복사 둘 `TB.copy($('menuSqlEgov'), $('menuMsg'))`.
③ 스모크 `programAnalysisMenuCsv(@TempDir)`(픽스처 프로젝트로 분석 한 번 — `programAnalysisRuns` 와 같은 준비) — 탭 열기 → `#menuMsg` 「메뉴 없음」 로 시작 → 임시 `menu.csv`(`메뉴,URL,사용여부\n게시판 > 목록,/bbs/list.do,Y\n게시판 > 없는,/nope.do,Y\n`) → `HtmlFileInput.setFiles` + `fireEvent("change")` → `#menuUpload` 클릭 → `waitForBackgroundJavaScript(3000)` → `#menuMsg` `startsWith("메뉴 2행(URL 2)")` → `#screens thead` 에 「메뉴」·「근거」 → `/bbs/list.do` 행의 메뉴 칸 `게시판 > 목록`, 근거 `일치` → `#menuOnly` 보이고 글에 `/nope.do` → `#menuClear` 클릭 → 「메뉴 없음」·`#menuOnly` hidden. `#menuSqlEgov` 글에 `CONNECT BY`.
- **검증**: `-Dtest='SmokeHtmlUnitTest#programAnalysis*,ToolsFolderTest'` 초록(복사 버튼 `.btn-green` 은 공용 · `copyGoesThroughTbCopy` 최소 수에 `program_analysis_ext.js` 가 들면 그 수를 1 로 더한다). **닫힘**: `menuUpload` 가 `POST` 뒤 `/screens` 를 다시 안 받으면 메뉴 열 단언 빨강.
- **사다리**: HtmlUnit `FileReader.readAsArrayBuffer`+`TextDecoder` 가 안 되면(`logicalNameRunsFromCsvFile` 이 같은 길을 쓴다 — 될 것) `readAsText` 로 물러선다(EUC-KR 은 포기, 이력에).

#### S12 마무리
CLAUDE.md 「마무리」 여덟 — 독립 리뷰 → `gate-probe.sh` → `verify.sh --full`(데모 서버 끔 · 메모리 15 GB 넘게 비운 뒤 — 번들 33 에서 한 번 꺼졌다) → push → PR(청크마다 시작 세 줄 표) → CI 폴링 → 머지 위임은 **이 플랜을 승인할 때 사용자가 정한다**(없으면 PR 열고 멈춘다) → 끝 보고. `node scripts/puppeteer/visual.js bundle34 --only program_analysis` 한 번(집 검증).
**부르는 꼴 실측**(D3, 사용자 결정 「넣고 실물로 재고 갈린다」): 데모 서버(`--profile demo`, 데모 H2) 를 띄워 `C:/workspace/toolbox-demo/egov` 를 새로 분석 → `GET /api/analyze/runs/<id>/screens` 의 callers 를 꼴별로 센다(링크·폼·팝업·ajax·스크립트·기타·모름 수와 비율) → 꼴마다 2~4개씩 **20개**를 골라 그 JSP 줄을 눈으로 대조해 맞음/틀림을 적는다(JSP 원문은 이력에 안 옮긴다 — 파일:줄과 판정만). 이력에 「부르는 꼴 실측 — 수·비율·20 중 맞음 n」. 「기타」 가 절반을 넘거나 틀림이 3 을 넘으면 「6-27 빼기」 행을 새로 세운다(이 번들에서는 안 뺀다).

### 5. 금지 사항

- `pure/` · `sql_snippets.html` · `profiles/demo.yaml` 커밋 · 새 의존성 · V001~V009 수정(새 V010 하나만) · `AnalyzeStore` 의 기존 열·저장 값(crud 글자·kind 코드·뷰 이름) · `golden/corpus/*`(고아 JSP·jspUrls·unresolved baseline 불변이어야 한다 — 바뀌면 D2·D3 를 어긴 것) · `golden/analyze/*`(java-graph·jsp-links 불변) · `Unresolved.KINDS`(새 종류 없음) · 산출물(2장) 코드.
- 화면명·화면 유형·메뉴 경로를 **추정해 채우지 않는다**. 컨텍스트 경로를 떼고 두 번째로 맞추지 않는다. 경유는 한 단계만. 메뉴 사용 N 행을 전수표에서 빼지 않는다.
- 화면 JS 가 전수표·메뉴 이음·종류 글을 계산하지 않는다(서버 값을 거르고 그릴 뿐). `innerHTML` 은 비우기만. ES5. 메뉴 CSV 글·JSP 원문을 로그·H2 에 남기지 않는다(메뉴 이름·URL 열만).
- 로컬 `<style>` 에 `:root`·`button{}`·`.btn-*`·`.dl`·토큰 밖 고정 색 금지. 안내 SQL 은 복사만 — 실행 버튼 금지(1-36).
- 취소된 분석을 저장하지 않는다(S1 ③). `JobManager`·`Job`·`JobRoutes` 는 안 고친다.
- 시험을 고쳐 통과시키지 않는다 — 고쳐도 되는 것은 이 문서가 이름을 댄 것(`AnalyzeStoreTest.result()` 13인자 · `exportXlsx` 시트 이름 · `programAnalysisRuns` 추가 단언 · `JspLinksTest`·`JavaGraphTest`·`ConsistencyTest` 새 시험)뿐. 1,500/4,000 파일 수 외에 중지 스모크의 허용 조건을 넓히지 않는다.
- `scripts/verify*.sh`·훅·CI 수정 금지(프로젝트 기간 규칙).

### 6. 최종 검증

- 청크마다 `bash scripts/verify.sh` 초록 뒤 커밋(따로). 번들 끝 `--full` 초록(표본 포함 — `analyze-egov-orphan-jsp.txt`·`analyze-egov.json`·`jsp-links.json`·`java-graph.json` diff 0) · gate-probe 여덟 · `visual.js bundle34 --only program_analysis` 콘솔 오류 0.
- 회귀 — `SmokeHtmlUnitTest` 전부 · `AnalyzeRoutesTest`·`DeliverableRoutesTest`(산출물 18 은 `store.crud` 그대로) · `CodeCliTest`(xlsx 파일 1) · `MetaRoutesTest`.
- 수동(사람 — 실브라우저, `run.bat --profile demo`, 이력 #97 또는 새 분석): 분석 중 「중지」 → 「중지함」·이력 안 늚 · 화면 전수 탭 — 행 수가 json·redirect 를 뺀 수인지, JSP 파일 「없음」 행이 실제로 없는 파일인지 몇 개 확인 · 정합성 「없는 JSP」 칸 · 데모 egov 메뉴를 안내 SQL 로 뽑아 CSV 올리기 → 메뉴 경로·근거(일치·경유·없음)·「메뉴에만 있는 URL」(옛 메뉴 22개가 여기 나와야 한다) · xlsx 시트 화면전수·메뉴만.

### 7. 중단 조건

멈추고 보고(어느 스텝 · 무엇이 달랐나 · 선택지):
- 2장의 함수·줄이 실제와 다르다(같은 이름으로 다시 찾되 없으면 멈춘다).
- 검증이 2회 연속 실패, 원인이 그 스텝 밖.
- 5장을 어기지 않고는 못 간다 · 스텝에 없는 파일을 3개 이상 고쳐야 한다.
- S3 에서 corpus 고아 JSP 목록이 바뀐다(D2 위반) · S6 에서 `jspUrls` baseline 이나 `jsp-links.json` 이 바뀐다 · S2 사다리(4,000 파일)까지 중지가 안 잡힌다 · V010 이 `migration-immutable` 게이트에 걸린다(반입 전이라 안 걸려야 한다).

### 8. 불확실 항목 — 설계 세션이 푼 것 · 사용자 선택 · 남긴 것

- **푼 것(실측)**: 정합성 corpus 골든은 손으로 고른 키 여섯 → `Report` 필드 추가 무해 · `JspLinksTest.golden` 은 `urls()`·`unresolved()` 만 → `Result` 꼴을 바꿔도 `urls()` 를 남기면 불변 · `JavaGraphTest` 골든은 `Graph` 직렬화 → tick 오버로드 무해 · `JobManager` 는 본문 끝 뒤 취소면 CANCELLED 로 내지만 저장은 본문 안이라 **저장 앞 확인이 필수**(D1 ③) · 픽스처에서 `sample/bbs/BoardList` 의 JSP 는 없고(`jsp/bbs/BoardList.jsp` 는 `sample/…` 로 안 끝남) `BoardDetail` 은 고아 아님 · 메뉴 CSV 크기: Javalin 기본 요청 상한 1MB — 메뉴 1,000행 CSV 는 100KB 안.
- **사용자가 정한 것(2026-10-10, 플랜 질문)**: ① 「부르는 꼴(단서)」 열 — **넣고 실물로 재고 갈린다**(꼴 전부 저장 · 모르면 기타 · `c:url var` 단서 · S12 실측, D3) ② 메뉴 저장 단위 — **프로필마다 한 벌**(D5) ③ 「화면이 부르는 데이터 URL」 — **2판**(이 번들 밖) ④ 메뉴 URL 맞추기 — **정확 일치만**(D7). ⑤ 머지 위임 — 플랜 승인 때.
- **남긴 것(행 밖)**: JSP 내용 열(화면 표시·입력칸·hidden·검색 조건) 2판 · 프로필 DB 에서 메뉴 직접 조회 · 메뉴 경로 구분자 프로필 키(이름에 `>` 가 든 사업이 나오면) · 뷰 접두·접미 프로필 키(Tiles·Thymeleaf 사업 — 「없음」 오탐이 나오면) · 중지 확인을 `MapperIndex.scan`·`JpaIndex.scan` 안에도(지금은 그 앞뒤에서만).
