# 설계 39 — 파일 읽기(`read`) · 코드 검사 빈칸

2026-10-25(Opus, 설계 세션 없음). 행의 원본은 PLAN 16장 `10-1`~`10-9`·`5-47`~`5-50`·`6-35`·`8-17`·`0-71`. 이 문서는 행이 기대는 결정·실측·출처만 적는다. **코드 꼴·번호 절차·골든 꼴은 착수 설계에서 정한다** — 지금 행은 「설계 대기」, 10-x 는 satchel 로 넘김(D8).

## 1. 출처

- 파일 읽기 — satchel 세션(Kiro 폐쇄망 kit, `C:/workspace/satchel`) 요청. 다음 사업(React + Spring Boot 과금, 폐쇄망, Kiro)에서 「이 파일 읽어·분석해」를 시키면 확장자별로 글·표를 뽑아야 한다. Anthropic 의 docx·xlsx·pdf·pptx skill 은 반출·재배포가 안 돼 kit 에 못 넣는다 — 그래서 toolbox 가 맡는다. kit 은 toolbox 를 MCP 가 아니라 `toolbox.bat` + 훅으로 부른다
- 코드 검사 빈칸 — 2026-10-25 projectshop(Java 25·Spring Boot 4.1·Next.js 16) 소스에 코드 검사·프로그램 분석을 읽기 전용으로 돌린 결과(지적 340, 처분은 projectshop 몫). 실물 목록은 저장소 밖 `out/projectshop/20261025-140436/findings.tsv`

## 2. 사용자 결정(2026-10-25, satchel 세션 경유 + 이 세션 질문)

| # | 결정 |
|---|---|
| D1 | OCR 넣는다 — Tesseract 5 + kor·eng **best** traineddata. 당분간 Windows 바이너리만. jar 밖 폴더로 반입 zip 에 |
| D2 | eml 뺀다 — jakarta.mail·angus-mail 반입 안 함. `.msg` 는 poi-scratchpad 로 |
| D3 | 고정길이 전문 레이아웃 원문은 현장에서 받는다(satchel `QUESTIONNAIRE.md`). toolbox 는 YAML 레이아웃으로 자르는 기능만(MS949 바이트 기준) |
| D4 | 우선순위 — 1차 xlsx·xls·csv·txt·docx·pdf(텍스트층)·hwp/hwpx · 2차 전문·zip·7z·pptx·doc·msg · 3차 OCR |
| D5 | OS — Windows 만 유지. Linux 는 런처(`toolbox.sh`)와 jlink jre 만 더하면 되는 수준으로 열어 둔다(reader 코드는 OS 무관) |
| D6 | 이 PR 은 행 + 실측 + 라이선스까지. 구현 착수 시점·범위는 따로 정한다 |
| D7 | 같은 PR 에 코드 검사 빈칸(파서 판·정적 import·tsx 오탐·JSX label 오탐·JdbcClient)·CLI 기준선·시험 코드 미사용 import 행 |
| D8 | (같은 날 뒤) 파일 읽기 `read`(10-1~10-9)는 toolbox 가 아니라 satchel 이 맡는다 — Kiro 쪽 일은 satchel 몫(사용자). 이 문서의 실측·라이선스·부품 조사(§3·§4·§5)는 satchel 이 넘겨받는 자료로 남긴다. 코드 검사 빈칸·CLI 기준선·0-71(§6)은 그대로 toolbox |

## 3. 지금 있는 부품(2026-10-25 확인)

- `core.text.Csv` — UTF-8 엄격 → 실패하면 MS949, 구분자 판별, BOM 제거. 쓰는 곳은 논리명(`LogicalCommands`·`LogicalRoutes`·`ColumnInputs`)·사전(`DictStore`)·INSERT 생성(`InsertGen`)·메뉴 CSV(`Menus`) — 전부 자기 기능의 입력이고 범용 읽기에는 안 쓰인다
- POI 5.4.1 `poi-ooxml` — XSSF·XWPF·XSLF 가 이미 런타임에 있다. HSSF(.xls)는 `poi` 본체. 쓰는 곳은 양식 채우기(`XlsxFiller`)·표 내보내기(`TableXlsx`)
- `poi-ooxml` 이 `commons-compress` 1.27.1·`commons-io` 2.18.0 을 끌고 온다(m2 의 `poi-ooxml-5.4.1.pom`) — zip·7z 의 아카이브 층은 이미 있다
- jlink 모듈에 `jdk.charsets`(MS949·EUC-KR)·`java.desktop`(이미지 렌더)
- 프로세스 실행 선례 — `core.vcs.Cli`(셸 없이 `ProcessBuilder`, 실행 파일 상수, 시간 상한). `ArchitectureTest` 가 프로세스 자리를 정해 둔다 — 새 자리는 규칙에 더해야 한다
- 범용 「파일 읽기」 명령·API 는 없다

## 4. 반입 재료 실측(2026-10-25, Maven Central `Content-Length`·pom, GitHub API)

| 용도 | 좌표 | 판 | 크기(바이트) | 라이선스 | 런타임 의존 |
|---|---|---|---|---|---|
| doc·ppt·msg | `org.apache.poi:poi-scratchpad` | 5.4.1(POI 와 같은 판 고정) | 1,909,132 | Apache-2.0 | poi·commons-math3·codec·io — 전부 이미 있음 |
| pdf | `org.apache.pdfbox:pdfbox` | 3.0.8 | 2,070,583 | Apache-2.0 | 아래 셋 |
| pdf | `org.apache.pdfbox:fontbox` | 3.0.8 | 1,647,000 | Apache-2.0 | |
| pdf | `org.apache.pdfbox:pdfbox-io` | 3.0.8 | 51,906 | Apache-2.0 | |
| pdf | `commons-logging:commons-logging` | 1.4.0 | 75,828 | Apache-2.0 | pdfbox 가 끈다 |
| hwp | `kr.dogfoot:hwplib` | 1.1.11 | 1,034,097 | Apache-2.0(pom) | 없음 |
| hwpx | `kr.dogfoot:hwpxlib` | 1.0.9 | 1,303,232 | Apache-2.0(pom) | 없음(junit 은 시험 범위) |
| 7z | `org.tukaani:xz` | 1.12 | 168,792 | 0BSD | commons-compress 의 LZMA·LZMA2 |
| 스캔 pdf 디코더(선택) | `org.apache.pdfbox:jbig2-imageio` | 3.0.5 | 151,132 | Apache-2.0(부모 pom) | JBIG2 압축 스캔본 |
| 스캔 pdf 디코더(선택) | `com.github.jai-imageio:jai-imageio-jpeg2000` | 1.4.0 | 489,144 | **JJ2000**(고유 라이선스 — 반입 판단 필요) | `jai-imageio-core` 1.4.0 628,053 · BSD-3(nuclear disclaimer) |

jar 합계(선택 디코더 뺌) 약 8.3MB — 지금 `app.jar` 43.7MB 가 약 52MB 가 된다. 판은 착수 때 `bundle-fetch.sh`·`offline-build.sh` 가 다시 잰다(지문은 그 스크립트만 고친다).

| OCR 재료 | 출처 | 판 | 크기(바이트) | 라이선스 |
|---|---|---|---|---|
| Tesseract Windows 설치본 | UB Mannheim `tesseract-ocr-w64-setup-5.4.0.20240606.exe`(digi.bib.uni-mannheim.de · GitHub `UB-Mannheim/tesseract` 최신 릴리스) | 5.4.0 | 50,175,248 | Apache-2.0 |
| `kor.traineddata` | `tesseract-ocr/tessdata_best` | main | 12,528,128 | Apache-2.0 |
| `eng.traineddata` | 같은 곳 | main | 15,400,601 | Apache-2.0 |
| `osd.traineddata`(선택 — 방향 판별 `--psm 0·1`) | 같은 곳 | main | 10,562,727 | Apache-2.0 |

- 업스트림 최신은 5.5.3 이지만 Windows 빌드는 UB Mannheim 5.4.0 이 최신이다
- 설치본은 NSIS exe 다. jar 밖 폴더(`ocr/`)로 만들려면 풀어야 한다 — 7z 로 푸는지, 한 번 설치해 폴더를 복사하는지, 풀린 폴더 크기는 **실행해야만 안다**(10-9 첫 스텝)
- traineddata best kor + eng = 27.9MB(satchel 의 「약 25MB」 보다 조금 크다)

## 5. 공통 꼴(행이 따른다)

- 명령 하나 `toolbox.bat read <파일> [--format md|json|text] [--max-rows N] [--sheet 이름] [--layout 전문.yaml]` + 같은 라우트(소켓 없는 서버 — 번들 18 꼴). 화면은 만들지 않는다(요청이 AI 훅용)
- 출력 — 표 꼴(xlsx·xls·csv·전문)은 Markdown 표 또는 JSON 행, 문서 꼴(docx·pdf·hwp·pptx)은 평문(제목·표는 md). 큰 파일은 `--max-rows` 와 글자 상한으로 자르고 잘랐다고 끝 줄에 적는다
- 규칙 1 — 런타임 망 0(OCR 모델·변환 API 받기 없음). 규칙 3 — 뽑은 내용은 stdout 으로만. H2·로그에는 파일 이름·확장자·건수·걸린 시간까지
- 안전 — 입력 크기 상한, XML 외부 엔티티 차단(`core.text.SafeSax` — 6-2), 압축 폭탄 상한(풀린 바이트·항목 수·깊이), 아카이브 안 경로는 표시만 하고 디스크에 안 푼다
- 패키지 `core.read`(web·cli 를 import 안 함). reader 하나 = 확장자 묶음 하나
- 새 jar 는 `offline-build.sh` + `bundle/SOURCES.md`(출처·판·라이선스) + 반입 심의 자료에 같이 적는다(규칙 5)

## 6. 코드 검사 빈칸 — projectshop 실측

| 행 | 실측 |
|---|---|
| 5-47 | `JavaSource` 가 `LanguageLevel.JAVA_17`·`JAVA_8` 두 파서. switch 패턴(Java 21) 세 파일이 `java.parseError` — `Allowed.java:52`·`Decider.java:91`·`PermissionMatrixTest.java:242`. JavaParser 3.26.3 |
| 5-48 | `JavaRules.imports` 가 `im.isStatic()` 이면 건너뛴다 — projectshop 이 Checkstyle `UnusedImports` 로 찾은 쓰지 않는 정적 import 둘(`AccountLivenessTest:3`·`ReturnRequestSchemaTest:3` `assertThat`)을 놓쳤다. 주석 안 낱말을 「쓰임」 으로 세는 것(javadoc `{@link}`)은 Checkstyle 기본(`processJavadoc=true`)과 같은 정책이라 그대로 둔다 |
| 5-49 | `tsx.unusedImport`(kind `jsUnusedImport`) 오탐 셋 — `import nextVitals …` 를 `...nextVitals` 스프레드로만 쓴 것(`eslint.config.mjs:3·4`), `import { type AdminOrderPage, … }` 인라인 `type` 지정자 줄(`admin/orders/page.tsx:6`, 여섯 이름 다 쓰임) — 보정(설계 40): 이 줄도 원인은 `...apiQueryOf(filter)` 스프레드, 인라인 `type` 은 이미 처리된다 |
| 5-50 | `a11y.jsxInputLabel`(정규식) 오탐 일곱 — `<label>` 로 감싼 `<input>` 여섯, `{...shared}` 스프레드로 `id` 를 넘긴 것 하나. 같은 뿌리의 JSP/HTML 규칙은 5-31(대기). 보정(설계 40): 감싼 label 다섯은 `<label>` 다음 줄 `<input>` — 줄 단위 정규식으로 못 고쳐 자바 규칙으로 |
| 6-35 | 프로그램 분석이 컨트롤러 메서드 132 를 잡고 SQL·표 0 — `JdbcClient.sql("…")`(Spring 6.1+) 를 문장 출처로 안 본다. `core/analyze` 에 JdbcTemplate·JdbcClient·NamedParameterJdbcTemplate 처리 없음 |
| 8-17 | `check --fail-on` 은 지적이 하나만 있어도 끝 코드 3 — 기존 코드베이스 훅에 걸면 첫날 빨강. `--changed-only` 는 `git status`(커밋 안 한 것 + 추적 안 하는 파일)라 CI 의 깨끗한 체크아웃에서는 0 파일 |
| 0-71 | 우리 `src/main` 미사용 import 는 `SelfCheckTest.mainMatchesBaseline`(기준선 0건)이 막는다. `src/test/java` 는 `everyTestAsserts` 만 돌아 미사용 import 가 안 막힌다. Checkstyle `UnusedImports` 는 projectshop 실측에서 javadoc 문법이 한글 식별자 `{@link #바깥}` 을 못 읽어 `processJavadoc=false` 가 필요했다 — 우리 시험 코드도 한글 식별자를 쓴다 |

정정 — 「단언 도우미를 프로젝트마다 못 정한다」 는 빈칸이 아니다. 프로필 `codecheck.rules: { java.testNoAssert: { params: { helpers: [...] } } }` 로 된다(`JavaRules` 214줄, `SelfCheckTest.everyTestAsserts` 가 쓰는 꼴).

## 7. 착수 설계에서 정할 것

- 각 행의 번호 절차·골든·픽스처 꼴(파일마다 손 픽스처 + 골든 출력)
- `--format md` 의 표 꼴(병합 셀·빈 행·수식 값 대 수식 글)
- hwplib·hwpxlib 의 표 읽기 API 실측
- JBIG2·JPEG2000 디코더를 넣을지(JJ2000 라이선스 — 사용자 판단)
- `osd.traineddata` 를 넣을지(방향이 틀어진 스캔본이 흔한지 — 현장 답)
- Tesseract 설치본을 폴더로 푸는 방법과 풀린 크기(10-9)
