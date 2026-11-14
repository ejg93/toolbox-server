# 판정 E — Opus (2026-11-14)

(핸드백 글을 실행 세션이 저장)

판정자 폴더 `J` = `A/judge-E`. 탐침 둘을 `J/p1`(#1)·`J/p2`(#2)로 복사해 돌림(스크립트 `P=` 경로만 바꿈, #2 서버 포트 18755).

| 결함 키 | #1 id | #2 id | 판정 | 등급 | 파일:줄 | 한 문장 | 재현(판정자 명령 + 실제 출력 한 토막) |
|---|---|---|---|---|---|---|---|
| E-eqRecv | E/1-01 | — | 확인 | Critical | core/check/ValueAst.java:264 · ValueFix.java:173 | 기본 켬 `java.equalsLiteralFirst` 가 `equals` 의 수신자 형식을 안 본다. `this`·String 아닌 형식도 뒤집어 비대칭 equals 의 결과가 바뀐다 | `cd J/p1; bash run.sh …/in/EqVo.java DEFAULT; bash cmp.sh EqVo` → `value java.equalsLiteralFirst 2→0`, in `hit=true isA=true` / out `hit=false isA=false` |
| E-consoleSide | E/1-02 | E/2-01 | 확인 | Critical | core/check/Cleanup.java:561-620 (deleteConsole) | 기본 켬 console 지우기가 인자의 부수 효과(pop·증감·대입)째 줄을 지운다 | `cd J/p2; bash run.sh j1 src/side.js DEFAULT; bash nrun.sh j1 src/side.js` → in `queue=2 seq=11` / out `queue=3 seq=0`. p1 `side.js`: in `stack=2 n=11` / out `stack=3 n=0` |
| E-loggerInit | E/1-03 | — | 확인 | Critical | core/check/LogFix.java:175 (place) · 227 (find) | 고른 규칙 `common.sysout` 이 로거 필드보다 앞에 초기화되는 싱글턴 생성자의 println 을 `logger.debug` 로 바꾼다. 컴파일은 되고 클래스 로딩에서 NPE | `bash run.sh …/in/Single.java common.sysout; bash cmp.sh Single` → in `created`·`n=1` / out `ExceptionInInitializerError … "x.Single.logger" is null` |
| E-switchHeadComment | E/1-04 | — | 확인 | Critical | core/check/SwitchFix.java:80-82 | 기본 켬 `java.defaultLast` 가 switch 머리 줄에서 열린 블록 주석의 꼬리 줄까지 옮겨 사이의 case 가 주석 안으로 들어간다 | `bash run.sh …/in/SwHead.java DEFAULT; bash cmp.sh SwHead` → `switch java.defaultLast 1→0`, in `two other` / out `other other` |
| E-xmlNoDecl | E/1-05 | — | 확인 | Critical | core/check/EncodingFix.java:88-104 (declaration. 보고서의 451-466 은 틀린 줄 — 파일은 105줄) | 기본 켬 `file.encoding` 이 인코딩 선언 없는 XML(UTF-8 로 읽힘)을 프로필 MS949 로 바꿔 쓴다. 결과는 XML 파서가 못 읽는다 | `bash run2.sh J/p1/enc J/p1/enc-out DEFAULT MS949 -; java -cp bin X <in> <out>` → `step … file.encoding 1→0` `written=1`, in `OK SELECT …` / out `FAIL SAXParseException: Invalid byte 1 of 1-byte UTF-8 sequence.` |
| E-interruptLoop | E/1-06 | — | 확인 | Critical | core/check/InterruptFix.java:87 · 109 (leaves. 보고서의 249·271 은 틀린 줄 — 파일은 150줄) | 고른 규칙 `java.interruptedIgnored` 가 조건부 return 하나만 있어도 루프 밖으로 나간다고 보고 interrupt 를 끼워 바쁜 루프가 된다 | `bash run.sh …/in/Spin.java java.interruptedIgnored; bash cmp.sh Spin` → in `loops=7` / out `loops=85481` |
| E-soe | E/1-07 | E/2-07 | 확인 | Important | core/check/CleanupRunner.java:98 · 166 | 파일별 catch 가 RuntimeException 만 잡아 파일 하나의 StackOverflowError 로 폴더 미리보기·쓰기 작업 전체가 FAILED | `cd J/p2; bash frun.sh deep500 DEFAULT` → `PREVIEW THREW java.lang.StackOverflowError`. 서버 `POST /api/cleanup/preview` job → `"status":"FAILED","message":"0/2 파일","error":"StackOverflowError"`. p1 `run3.sh deep` → `THROWN java.lang.StackOverflowError at …TaintConst.concat` |
| E-logDomainType | E/1-08 | — | 확인 | Minor | core/check/LogFix.java:48 (LOGGER_TYPES) · 227 (find) | 단순 이름이 `Log` 인 도메인 형식 필드를 로거로 보고 `.debug` 를 건다. 결과는 컴파일이 안 된다 | `bash run.sh …/in/LogEnt.java common.sysout; bash cmp.sh LogEnt` → out javac `error: cannot find symbol … method debug(String) … variable lastLog of type Log` |
| E-piiQuoteParity | E/1-09 | — | 확인 | Minor | core/check/PiiFix.java:60 · 84 (quoted. 보고서의 396-403 은 틀린 줄 — 파일은 92줄) | 고른 규칙 `security.piiCardPlain` 이 따옴표 홀짝으로 문자열 안인지 판단해 `"it's"` 다음 맨 숫자를 가려 JS 구문이 깨진다 | `bash run.sh …/in/card.js security.piiCardPlain; node --check out/card.js` → `pii 2→0`, `SyntaxError: Unexpected token '**'` |
| E-backtickParity | E/1-10 | E/2-03 | 확인 | Critical | core/check/Cleanup.java:565-572 · 585 (console) · 530-537 · 547 (debugger) | 템플릿 리터럴 판정이 백틱 개수 홀짝이라 이스케이프된 백틱·문자열 속 백틱이 있으면 리터럴 안 console·debugger 줄을 지워 문자열 값이 바뀐다 | `cd J/p2; bash run.sh j1 src/tmpl.js DEFAULT; bash nrun.sh j1 src/tmpl.js` → 4·5줄 삭제, out 이 `const a = 1;` 만. p1 `tpl.js`: in `3 lines` / out `2 lines` |
| E-mixedEol | E/1-11 | E/2-10 | 확인 | Minor | core/fs/LocalFiles.java:311 (read) → CleanupRunner.java:184 (writeResult) | 줄끝이 섞인 파일은 다수 쪽 하나로 다시 써 고침이 안 닿은 줄의 줄끝도 바뀐다 | `cd J/p2; bash frun.sh b1 DEFAULT apply; od -c` → `var b = 2;\r\n` 가 `\n`. p1 `Mix.java`: `{\n` 가 `{\r\n` |
| E-fixQuadratic | E/1-12 | — | 확인 | Minor | core/check/MarkupFix.java:95-103 (insertAll: 99줄 substring 셈) · PiiFix.java:84 | 끼울 자리마다 글 앞머리를 다시 훑어 고침 쪽 시간이 크기의 제곱으로 는다. 파일 안에서 취소가 안 듣는다 | 링크 5천·1만·2만(blankNoopener − htmlLang 만): 655 → 2088 → 7336ms. 카드 7,500·15,000 한 줄: 495 → 1564ms(판정만 85·125ms) |
| E-regexParen | — | E/2-02 | 확인 | Critical | core/check/Cleanup.java:626-652 (consoleEnd) | 기본 켬 console 지우기가 정규식 리터럴 안 `\(` 를 괄호로 세어 끝을 뒤 줄로 잡고 무관한 문장 둘을 같이 지운다 | `bash run.sh j1 src/regex.js DEFAULT; bash nrun.sh j1 src/regex.js` → `6,8d5`, in `done=5 closing=string` / out `done=0 closing=undefined` |
| E-twrQuietClose | — | E/2-04 | 확인 | Critical | core/check/ResourceAst.java:518-527 (closeName) · ResourceFix.java:115 | 고른 규칙 `java.tryWithResources` 가 finally 의 `try{close}catch` 를 close 로 보고 바꿔, 삼키던 close 예외가 메서드 밖으로 나가고 catch 몸이 지워진다. closeQuietly 꼴은 QUIET 로 건너뛰어 처리가 어긋난다 | `bash run.sh r2 …/R2.java java.tryWithResources; bash jrun.sh r2 …/R2.java p.R2` → in `quiet=65`·`log=close-ignored` / out `quiet threw close failed` |
| E-twrFormBCatch | — | E/2-05 | 확인 | Critical | core/check/ResourceAst.java:584-606 (꼴 B 길) | 꼴 B(null 선언 + try 안 대입)는 try 에 catch 가 있어도 고쳐 close 예외가 그 catch 로 들어가 반환값이 바뀐다. 꼴 A 는 같은 이유로 거절(588줄 주석) | 같은 명령 → in `guarded threw close failed` / out `guarded=-1`·`log=read-failed` |
| E-fieldOrder | — | E/2-06 | 확인 | Critical | core/check/OrderFix.java:284 (bucket→access 정렬) · 232 (Lombok 만 거름) | 기본 켬 `java.declarationOrder` 가 접근 제어가 섞인 인스턴스 필드를 다시 놓아 리플렉션 필드 차례(엑셀·직렬화 열 차례)가 바뀐다 | `bash run.sh o1 …/O1.java DEFAULT; bash jrun.sh o1 …/O1.java p.O1` → in `id,name,phone,dept,` / out `dept,name,id,phone,` |
| E-consoleRegexTime | — | E/2-08 | 확인 | Minor | core/check/Cleanup.java:647 (`matches("\\s*;?\\s*")`) · 595 (SCRIPT_OPEN) | console 뒤 긴 공백과 `<script ` 반복 앞 줄에서 정규식 역추적이 제곱으로 는다 | `Time` 으로 공백 1만·2만·4만·8만: 100 → 265 → 1396 → 4428ms. `<script ` 6,250·12,500: 461 → 1827ms. 원 prev.js 2957ms |
| E-htmlTextArea | — | E/2-09 | 확인 | Critical | core/check/Cleanup.java:525-620 (HTML debugger·console) · ThScopeFix.java:77 (scan) | 기본 켬 규칙이 HTML `<pre>`·`<textarea>` 안 글을 코드·마크업으로 봐 화면에 보이는 글과 textarea 값이 바뀐다 | `bash run.sh j1 src/doc.html DEFAULT` → `<pre>` 안 `console.log(a);`·`debugger;` 줄 삭제. `bash run.sh m1 web/th.html DEFAULT` → 24줄 textarea 안 `<th>` 가 `<th scope="col">` |
| E-applyNull | — | E/2-11 | 확인 | Minor | core/check/CleanupRunner.java:140 · web/CleanupRoutes apply | `files:[null]` 이면 400 대신 작업이 FAILED 되고 응답에 NPE 글이 실린다(`files:["x"]` 는 400 「본문을 못 읽었다」) | `bash http.sh apply '{"path":"…/in/j1","files":[null]}'` 뒤 job → `"status":"FAILED"…"error":"Cannot invoke \"…CleanupRunner$FileRef.rel()\" because \"ref\" is null"` |

n1 = 12 · n2 = 11 · m = 4 · N ≈ 12·11/4 = 33

## 불확인 목록

없음. 23개 지적을 모두 다시 돌렸고 같은 결함이 나왔다.

## 판정 메모

- 줄 번호 바로잡음: #1 의 E/1-05·E/1-06·E/1-09 줄 번호는 지금 파일 길이를 넘는다. 표에 실제 자리를 적었다
- 등급 기준: 조용히 의미가 바뀌는 고침(컴파일·재검사 통과)은 드문 입력이어도 Critical(7절 「설계가 그 입력을 말하지 않았다는 등급을 낮추는 근거가 아니다」). 기본 켬 — E/1-01·E/1-04·E/1-05·consoleSide·backtick·E/2-02·E/2-06·E/2-09. 고른 규칙 — E/1-03·E/1-06·E/2-04·E/2-05. 컴파일·구문 오류로 바로 드러나는 것(E/1-08·E/1-09)은 Minor
- 설계 한계 검토: 해당 없음. `deleteConsole` javadoc 은 따옴표·템플릿 안 괄호만 다루고 정규식 리터럴·부수 효과 이야기가 없다. `OrderFix` 머리 javadoc 은 Lombok·초기화 순서만 위험으로 든다
- 제외 겹침 검토: 해당 없음. E/1-12·E/2-08 은 compare ㉜ 「파일당 시간 상한」 과 처방이 겹칠 수 있으나 원인이 특정 코드(MarkupFix·PiiFix 앞머리 재훑기, Cleanup 정규식 둘)라 셌다. E/1-07 의 넘침 자리(TaintConst)는 영역 D, 여기서는 CleanupRunner catch 만
- 임계값 차이(E-soe): #1 은 3,000 항에서 넘침(새 스레드), #2 는 cat3000 통과·1만 항과 괄호 500단에서 넘침 — 스레드 스택 차이. 뿌리는 같다
- 정리: 띄운 서버(18755) 끔. 판정자 java 남지 않음. 원래 탐침 폴더·트리·표본 안 고침
