# 판정 G1 — Opus (2026-11-14)

(핸드백 글을 실행 세션이 저장)

판정자 탐침 폴더 `J` = `A/judge-G1`. `J/p1` = #1 탐침 사본(경로 바꿈, 포트 18511·18512(힙 256m)). `J/p2` = #2 탐침 사본(프로필 새로 만듦, 포트 18501·18502(힙 256m)). 원래 탐침 폴더는 손대지 않음. 띄운 서버 넷 모두 끔(LISTENING 0, judge-G1 든 java 0). 사본의 정션 둘(`p1/jdata`, `p1/loopdir/sub/back`)은 `cmd /c rmdir` 로 지움. 비밀번호는 가짜 값만, 출력에서 가림.

| 결함 키 | #1 id | #2 id | 판정 | 등급 | 보정 같음 | 파일:줄 | 한 문장 | 재현(판정자 명령 + 실제 출력 한 토막) |
|---|---|---|---|---|---|---|---|---|
| G1-K01 | 1-01 | 2-01 | 확인 | Critical | 보정 G-2 와 같음 | core/fs/LocalFiles.java:93-104 · web/FsRoutes.java:58 | 금지 목록에 프로필 폴더가 없어 `GET /api/fs/read` 가 프로필 YAML 을 비밀번호째 준다(규칙 2) | `bash J/p2/probes.sh 18501 01` → `{"text":"name: p1…password: <가짜 전체>…"} [200]` · `bash J/p1/repro.sh 01` 같음 |
| G1-K02 | 1-02 | 2-02 | 확인 | Critical | 보정 G-1 과 같음 | web/ProfileRoutes.java:33-34 · :123-137 | 따옴표 없는 값에 `#`·`,`·`}` 가 있으면 정규식이 앞부분만 지우고 `leaked` 도 꼬리를 못 잡아 백업에 비밀번호 꼬리가 남는다 | `probes.sh 18501 02` → `out/p1/…/backup/profiles/p1.yaml` 9줄 `password: ''#<꼬리>`, p5 는 `'',<꼬리>`·`''}<꼬리>`·흐름 꼴 `''#<꼬리>}` · ProbeRegex `[FAKE#tail] -> [password: ''#tail]` |
| G1-K03 | 1-03 | 2-03 | 확인 | Important | 보정 G-5 와 같음 | web/App.java:142 · web/TextRoutes.java:14 · core/fs/LocalFiles.java:41 | 본문 상한이 Javalin 기본값 1MB 라 logsql 5백만 자·읽기 10MB 보다 먼저 걸려 읽은 큰 파일의 결과를 못 쓴다 | `probes.sh 18501 03` → `Content Too Large [413]` / `read [200] 9200048 bytes` / `Content Too Large [413]`, 로그 `Body greater than max size (1000000 bytes)` |
| G1-K04 | 1-04 | 2-08 | 확인 | Minor | 보정 G-11 과 같음(글롭 꼴) | core/fs/LocalFiles.java:178 | 잘못된 glob(`[`·`*.{jsp,java}` 를 쉼표로 자른 조각)에서 `PatternSyntaxException` → 사유 없는 500 | `repro.sh 04` → `{"message":"서버 오류"} [500]` · 로그 `PatternSyntaxException @ LocalFiles.list:178` |
| G1-K05 | 1-05 | 2-09 | 확인 | Minor | — | web/FsRoutes.java:60·71 · TextRoutes.java:24 · ProfileRoutes.java:74·97 | 본문 JSON `null`·없는 charset 을 `bodyAsClass` 가 NPE·`UnsupportedCharsetException` 으로 받아 500 | `probes.sh 18501 09` → 다섯 라우트 `[500]` · `repro.sh 05` → charset=x `[500]`, 로그 `UnsupportedCharsetException @ ProfileRoutes…:97` |
| G1-K06 | 1-06 | 2-10 | 확인 | Minor | 보정 G-11 과 같음(rel 꼴) | core/fs/LocalFiles.java:390 · :403 · checkRel 407-414 | rel 의 Windows 금지 글자와 `...`·조각 끝 공백을 미리 안 걸러 500, 빈 결과 폴더가 남는다 | `repro.sh 06` → `[a?.jsp]…[...]` 넷 `[500]`, 로그 `InvalidPathException @ LocalFiles.writeResult:390`·`AccessDeniedException @ …:403` · `probes.sh 10` 셋 `[500]` |
| G1-K07 | 1-07 | 2-06 | 확인 | Minor | — | core/fs/LocalFiles.java:125-133 | 실제 경로 검사는 경로가 있을 때만 돌아 `data./새폴더` 와 data 로 가는 정션 아래 새 폴더가 결과 폴더로 통과해 서버 `data/` 안에 쓰인다 | `repro.sh 07` → `data/r07a` 400, `data./r07b` 200, `jdata/r07c` 200, `ls data` 에 `r07b r07c` · `probes.sh 06` → 200 + `run/data/planted-N/x.jsp` |
| G1-K08 | 1-08 | 2-12 | 확인 | Minor | 보정 G-12 와 같음 | core/fs/LocalFiles.java:391 | 표식 판정이 `Path.equals` 라 `결과_출처.txt.`(끝 점)를 다른 이름으로 봐 출처 표식이 사용자 글로 덮인다 | `repro.sh 08` → 정확한 이름 `[400]`, 끝 점 200, 표식 첫 줄 `OVERWRITTEN` · `probes.sh 12` 같음 |
| G1-K09 | 1-09 | 2-05 | 확인 | Minor | — | web/FsRoutes.java:103-108 · web/Outputs.java:31-34 | 프로필 `name`(·`output.dir`)을 검사 없이 경로 조각으로 써 `name: ../x` 이면 `out/` 밖에 쓴다 | `probes.sh 18501 05` → `"path":"…\\p2\\out\\..\\esc\\…\\정리_n.jsp" [200]` · `repro.sh 09` → `out\\..\\p13-escape\\…`, `output.dir: data` → `…\\data\\outdata\\…` |
| G1-K10 | 1-10 | — | 확인 | Minor | — | web/ProfileRoutes.java:76-93 · core/profile/ProfileStore.java:199 | 모르는 키가 든 프로필을 저장하면 `UncheckedIOException` 이 안 잡혀 500, 백업 파일은 남는다 | `repro.sh 10` → GET `…모르는 키 nosuchkey [400]`, PUT `{"message":"서버 오류"} [500]`, `out/demo/…/backup/profiles/bad.yaml` 남음 |
| G1-K11 | 1-11 | 2-17 | 확인 | Minor | — | core/job/JobManager.java:138-143 | 작업이 200 개에 닿으면 제출마다 끝난 작업을 전부 지워 방금 끝난 작업도 404 | `repro.sh 11` → `"status":"DONE" [200]` 뒤 `{"message":"작업이 없다"} [404]` · `probes.sh 17` → `after 200 more submits 404` |
| G1-K12 | 1-12 | 2-16 | 확인 | Minor | — | core/fs/LocalFiles.java:183-225 | 목록 걷기가 정션을 따라가고 금지 폴더도 안 걸러 순환 정션에서 파일이 되풀이되고 `data/` 안 이름·크기가 보인다 | `repro.sh 12` → `files 64 … 'sub/back/sub/a.txt'…`, `data/active-profile`·`jdata/active-profile` · `probes.sh 16` → `files 127`, `peek/toolbox.mv.db` |
| G1-K13 | 1-13 | — | 확인 | Minor | — | core/fs/LocalFiles.java:178·210 | 별이 여럿인 glob 이 JDK 글롭 정규식 역추적으로 요청 스레드를 붙잡는다. 취소 길 없음 | `repro.sh 13` → `*a*a*a*a*b [200] 1.55s`, `*a*a*a*a*a*a*b [000] 30.007s` |
| G1-K14 | 1-14 | 2-07(415 쪽) | 확인 | Minor | 보정 G-16 과 같음 | web/LocalOnly.java:68 | Content-Type 판정이 `contentLength() > 0` 일 때만 돌아 chunked `text/plain` 본문이 지나 파일이 써진다 | `repro.sh 14` → Content-Length 있음 `[415]`, chunked `…정리_r14b.txt"} [200]` · `curl … chunked text/plain`(J/p2) 200 |
| G1-K15 | 1-15 | 2-07(상한 쪽) | 확인 | Minor | — | web/App.java:142 · (호출 자리 DictRoutes.java:54 · FsRoutes.java:71) | 본문 상한이 Content-Length 로만 걸려 chunked·multipart 는 상한이 없어 큰 입력이 OOM → 본문 없는 500 | 256m: `repro.sh 15` → multipart 300MB ` [500]`, 로그 `OutOfMemoryError @ DictRoutes.lambda$register$3:54` · `node src/chunked-big.js 18502 400` → `sent=71MB status=500 body=`, `OutOfMemoryError @ FsRoutes.lambda$register$4:71` · chunked 1.5MB logsql 200(1MB 상한 통과) |
| G1-K16 | 1-16 | — | 확인 | Minor | — | web/App.java:83·244 | `serve --port 99999` 가 `isFree` 에서 `IllegalArgumentException` 을 내고 스택을 그대로 찍는다 | `repro.sh 16` → `java.lang.IllegalArgumentException: port out of range:99999` + `at …InetSocketAddress.checkPort`, 끝 코드 1 |
| G1-K17 | 1-17 | — | 확인 | Minor | — | web/ProfileRoutes.java:34 | PASSWORD 셋째 갈래가 값 안 긴 공백에서 이차 시간 — 그런 프로필은 저장이 수십 초 멈춘다 | `run-probe.sh ProbeRegex` → 공백 2만 1,974ms · 4만 9,562ms · 8만 39,141ms. 라우트 PUT `/api/profiles/sp/codecheck` n=40000 `[200] 10.9s`, n=80000 `[200] 42.3s` |
| G1-K18 | — | 2-04 | 확인 | Minor | — | core/text/LogSql.java:118-125 ← web/TextRoutes.java:33 | 타입 괄호 없는 파라미터 줄에서 logsql 시간이 제곱으로 늘고 취소도 없다(라우트가 G1 이라 G1 에 셈) | `probes.sh 18501 04` → `n=20000 [200] 1.83s`, `n=80000 [200] 26.48s` |
| G1-K19 | — | 2-11 | 확인 | Minor | — | core/fs/LocalFiles.java:293 ← FsRoutes.java:58 | 다른 프로세스가 잠근 파일을 읽으면 `IOException` 이 일반 처리기로 가 사유 없는 500 | `probes.sh 18501 11` → `LOCKED` 뒤 `{"message":"서버 오류"} [500]` |
| G1-K20 | — | 2-13 | 확인 | Minor | — | web/ProfileRoutes.java:79-83 · core/profile/ProfileStore.java:195 | 프로필 저장이 제자리 덮어쓰기라 원자적이지 않고 백업 이름도 경합 — 동시 읽기 400, 동시 저장 500 | `probes.sh 18501 13` → `"get":{"200":24810,"400":356}`(「1줄 1칸 근처 YAML 형식이 틀렸다」) · 저장 둘 `"put":{"200":471,"400":5,"500":17}`, 로그 `FileAlreadyExistsException @ ProfileRoutes…:83`·`UncheckedIOException ← IOException @ ProfileStore.saveCodeCheck:199` |
| G1-K21 | — | 2-14 | 확인 | Minor | — | core/fs/LocalFiles.java:296-309 | UTF-16(BOM FF FE) 파일을 MS949 로 풀어 깨진 글이 `encoding: MS949` 로 조용히 나간다. javadoc 이 UTF-16 한계를 밝히지 않아 설계 한계로 안 뺌 | `probes.sh 18501 14` → `{"text":"��<\u0000p\u0000>…","encoding":"MS949"} [200]` |
| G1-K22 | — | 2-15 | 확인 | Minor | — | core/fs/LocalFiles.java:311-328 · 422-425 | 줄끝을 다수결 하나로 알리고 쓸 때 전부 그 값으로 바꿔 섞인 줄끝 파일의 안 고친 줄까지 원본과 달라진다(클래스 javadoc 「읽은 … 줄바꿈 그대로」 와 어긋남) | `probes.sh 18501 15` → 원본 `a \r \n b \n c \r \n d \r \n`, 결과 `a \r \n b \r \n c \r \n d \r \n` |

n1 = 17 · n2 = 18 · m = 13 · N ≈ 17·18/13 ≈ 23.5

- 2-07 하나가 두 결함(K14 Content-Type 판정, K15 본문 상한)을 함께 짚어 #2 쪽에서 둘로 셌다. 하나로만 세면 n2 = 17, m = 12, N ≈ 24.1
- 한 보고서 안에서 같은 뿌리 둘을 낸 경우 없음
- 등급을 점검자와 다르게 매긴 것: K09(#2 Important → Minor — 사용자가 자기 프로필 `name` 에 `../` 를 적어야만, 새 시각 폴더만 만들어 덮어쓰기 없음. `output.dir` 은 원래 어디든 가리키는 설정) · K18(#2 Important → Minor — MyBatis 로그는 보통 타입 괄호가 붙고 수만 개 파라미터 한 줄은 드묾)

## 불확인 목록과 사유

- 없음. 34 건 모두 같은 꼴로 다시 나왔다.
- 숫자 차이: 1-17 35.5초 → 39.1초 · 2-04 27.9초 → 26.5초 · 2-13 저장 하나일 때 GET 400 21 → 356 건 · 1-15·2-07 OOM 은 힙 256m 로 chunked 400MB(71MB 에서 OOM)·multipart 300MB 로 다시 잼
- 1-16 의 「65535 점유 뒤 +1」 길은 안 돌림 — `--port 99999` 길로만 판정

## 제외 겹침·설계 한계

- 제외 겹침 없음. K07 은 UNC 판정이 아니라 `0-87` 과 다른 꼴, 자리가 `LocalFiles.check` 라 `0-86`(MakeTemplates)과 다름. K13·K17·K18 은 요청 스레드·프로필 저장·logsql 자리라 「파일당 시간 상한」 후보와 다름
- 설계 한계 없음. K21 은 javadoc 이 UTF-16 한계를 안 밝힘. K22 는 javadoc 이 오히려 「줄바꿈 그대로」 를 약속. K11 의 `KEEP_FINISHED = 200` 에 의도 주석 없음

## 보정 G-n 대응

- 본 점검에서 다시 나온 보정 결함: G-1(K02) · G-2(K01) · G-5(K03) · G-11(K04 글롭 꼴·K06 rel 꼴 — 「목록 안 null」 꼴은 다른 영역 라우트) · G-12(K08) · G-16(K14)
- 보정에서 남은 것 가운데 G1 영역인데 본 점검이 다시 못 낸 것은 없다. G-6·7·8·9·10·13·14·15 는 다른 영역 자리이거나 core/text 결함
