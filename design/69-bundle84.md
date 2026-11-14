# 설계 69 — 번들 84: 가로 점검 2차의 절대 규칙 2·3·6 Critical + ⑦·⑧ 묶음 (Fable 설계 · Opus 실행, 2026-11-14)

## 맥락

가로 점검 2차(설계 67·68)가 확인 결함 173(Critical 34) + 보정 남음 19 를 냈다. 한 번들로 안 된다. 번들 84 는 절대 규칙에 닿는 것부터 닫는다. 원천은 `design/68-audit-findings.md` 와 `design/68-evidence/judge-*.md`·`recheck-*.md` 뿐이다. 탐침 소스는 저장소에 없다 — RED 시험은 판정 파일의 입력 꼴·실제 출력으로 다시 만든다.

**보정(2026-11-14, `ceb6881` 트리) — 이미 선 행과 겹치는 것**
- 번들 84 가 닫는 키는 열린 행 `0-86`·`0-87`·`5-100`·`5-31` 과 안 겹친다. `0-87`(UNC 판정 한 자리)은 `LocalFiles.check` 를 고치지만 이 번들은 생성자·거절 목록만 고친다 — 0-87 은 대기 그대로
- 뒤 번들에서 가를 것: `D-a11yMultiline`(`rules.yaml:629·637`)·`D-piiLogSOE`(`:477`)는 `5-100` 과 같은 뿌리 — 새 행을 안 세우고 5-100 에 붙인다. `F-K7` 은 compare ㉜ ⑪ 제외 그대로
- `G1-K02` 는 `0-83d`·`0-85g`(완료)의 남은 꼴, `G2-K1=F-K17` 은 `0-85b`(완료)의 남은 꼴, `C-RUN-SOE`·`E-soe`·`D-runTextSOE` 는 `0-85f`(완료)가 한 자리만 가둔 것 — 전부 새 행

**사용자 결정(2026-11-14)**
1. 코드 정리 기본 켬 Critical 여덟을 기본 끔으로 내리는 청크는 **안 넣는다** — 번들 85 가 바로 고친다. 번들 85 머지 전에 반입하게 되면 그때 내린다
2. 크기 — 규칙 2·3·6 + 6절 ⑦(본문 null 500)·⑧(StackOverflowError 가둠)
3. url 안 비밀 값 — **거절을 넓힌다**(응답에서 가리지 않는다)
4. **머지 위임**

## 1. 목표와 범위

목표 — 프로필 비밀번호·SQL 글이 응답·백업·xlsx·H2 로 새는 길 다섯과 읽기 전용 래퍼 우회 여섯을 닫고, 본문 null·파일 하나의 스택 넘침이 500·작업 전체 실패가 되는 자리를 한 번에 가둔다. 사용자가 겪는 변화: `/api/fs/read` 로 프로필을 못 읽는다 · 백업에 비밀번호 꼬리가 없다 · url 에 키 저장소 암호·토큰을 적으면 로드가 거절된다 · `DB대조-N.xlsx` 에 SQL 조각·리터럴이 없다 · 분석 이력에 SQL 글이 없다 · 겹친 주석·`ı`·`;` 없는 잇기가 거절된다 · 본문 `null` 은 400 · 깊은 파일 하나는 건너뛰고 나머지가 돈다.

**In scope**(결함 키 22 — Critical 10 + 보정 Critical 1 + Important 3 + Minor 8)

| 행 | 닫는 키 | 규칙 |
|---|---|---|
| `0-88` | G1-K01(=보정 G-2) | 2 |
| `0-89` | G1-K02(=보정 G-1) · G1-K17 | 2 |
| `0-90` | G2-K1 · F-K17(6절 ③) | 2 |
| `6-39` | C-PROBE-MSG | 3 |
| `6-40` | R-C-1 | 3 |
| `9-6` · `9-7` | F-K1 · K2 · K3 · K4 · K5 · K6(6절 ⑫) | 6 |
| `0-91` | G1-K05 · F-K14 · A-24 · B-21 · C-WEB-NULL · E-applyNull(6절 ⑦) | — |
| `0-92a` · `0-92b` | D-runTextSOE · E-soe · C-RUN-SOE(6절 ⑧) | — |

**Out of scope** — 코드 정리 Critical 열둘(번들 85) · 조용히 틀린 출력 Critical 열셋(번들 86: A-01·A-03·A-11·A-12·A-15·A-28·B-01·B-02·C-TBL-COMMA·F-K8·F-K9·F-K21·R-A-C1) · 나머지 Important·Minor(번들 87~) · `F-K7`(⑪ 부작용 함수) · `0-86`·`0-87`·`5-100`·`5-31` · `pure/` · `rules.yaml` · G1-K07·K08·K12(쓰기 자리·목록 걷기) · DAO 가 직접 넘긴 SQL 글에서 표를 뽑는 기능(6-40 은 저장을 막기만 한다) · 여러 문서 YAML 프로필의 둘째 문서 처리(G2-K4)

## 2. 현재 구조 요약

**규칙 2 — 프로필 비밀번호**
- `core/fs/LocalFiles.java:93-104` — 거절 목록 `forbidden` 은 `dataDir` + Windows·Program Files 환경 변수뿐. 프로필 폴더가 없다. `check:107-135` 가 정규화 경로와 `toRealPath` 를 `refuseForbidden:137` 에 댄다. 생성 자리는 `web/App.java:177` 하나(`new LocalFiles(config.dataDir())`), 시험은 40여 곳이 1-인자 생성자를 쓴다
- 읽기 라우트 `web/FsRoutes.java:58`(`/api/fs/read`)·`DiffRoutes`(`/api/diff/file?a=&b=`)가 `files.read` → `check` 를 탄다. 프로필 폴더는 `AppConfig.profilesDir`(기본 `profiles`), 시험은 전부 `tmp/profiles` 하위 폴더를 준다(프로젝트 폴더의 조상인 시험 없음)
- `web/ProfileRoutes.java:33-34` `PASSWORD` — 따옴표 없는 값을 `[^,}\r\n#]*?` 로 잘라 블록 꼴 `password: FAKE#tail`·`FAKE,tail`·`FAKE}tail` 의 꼬리가 남는다(YAML 블록 평문은 `#` 앞에 공백이 있을 때만 주석이고 `,`·`}` 는 글자다). 흐름 꼴 `{…, password: FAKE#tail}` 도 `#` 에서 끊긴다. `leaked:123-137` 은 온전한 값이 한 번 줄었는지만 세어 꼬리를 못 잡는다. 같은 식의 게으른 수량자 + 전방 탐색이 값 안 긴 공백에서 제곱 시간(G1-K17). `withoutBlockPasswords:170` 은 블록 스칼라·다음 줄 값을 먼저 `password: ''` 로 바꾼다 — 그대로 둔다
- `core/profile/Profile.java:61-63` `PASSWORD_IN_URL` — `[?;&:(,]` 뒤 `password|passwd|pwd` 만 본다. 지나는 꼴: `password1=`(MySQL MFA) · `%70assword=` · Teradata `/PASSWORD=` · `sslpassword=` · `trustStorePassword=` · `trustCertificateKeyStorePassword=` · `accessToken=` · Oracle `APP/"공백 든 값"@`. url 은 `ConnectionRegistry.list:48-51`(`/api/conn`)·`Profile.withoutPasswords`(`/api/profiles/{이름}`)로 그대로 나간다. 게이트 탐침 `scripts/probes/url-password.patch` 가 정규식 줄·오류 글 줄을 문맥으로 든다 → 다시 떠야 한다

**규칙 3 — 코드 본문 저장**
- `core/analyze/SqlProbe.java:240-242` `withoutSqlText` 는 `near '…'` 꼴만 뗀다. H2 `Syntax error in SQL statement "…"`·`Data conversion error converting "…"`, PG `invalid input syntax for type integer: "…"`, MSSQL `Conversion failed when converting the varchar value '…'` 는 그대로다. `web/AnalyzeRoutes.java:429-438` `postProbeXlsx` 는 **화면이 되보낸** `rows`(`List<SqlProbe.Outcome>`)의 `message` 에 그 함수를 대어 쓴다. 화면 `program_analysis_ext.js:586` 은 작업 결과 `probeRows` 를 객체째 되보낸다 → `Outcome` 에 성분을 더하면 JS 를 안 고쳐도 왕복한다. `Outcome` 생성은 `SqlProbe.java` 일곱 자리뿐
- `core/analyze/JavaGraph.java:506-532` `statement` — DAO 싱크(`SINKS:89` — insert·update·delete·select* 등, `sink:463-473`)의 첫 인자가 문자열 리터럴이면 그 글을 문장 id 로(`:510`), `리터럴 + x` 면 접두로(`:519-520`, 미해결 `prefix` detail 에도), 변수 리터럴도(`:527`). `update("UPDATE … SET NM='값' …")` 면 SQL 글이 `analyze_stmt.ns_id`(V002, VARCHAR 300)·`analyze_unresolved.detail`(`AnalyzeRunner.java:204` `missing`)·분석 xlsx 로 간다. 마이그레이션은 V014 까지, `DbTest:49·67` 이 수 14 를 단언한다. H2 는 2.3.232(`REGEXP_LIKE` 있음)

**규칙 6 — 읽기 전용 래퍼** `core/conn/ReadOnlyJdbc.java`
- `check:55-79` — 첫 낱말은 `strip`(주석·인용 뺀 글)으로, `;`·쓰기 낱말·`FOR SHARE` 는 원문으로 잰다(번들 48 마무리 리뷰 — 「엄격한 쪽」 유지)
- K1 `strip:102-105` 가 블록 주석을 첫 `*/` 에서 닫는다 — `/* /* */ SELECT */ COMMENT ON …` 의 첫 낱말이 SELECT 로 읽히고 H2 는 COMMENT 를 실행 · K6 `WRITE:35` 가 ASCII 로만 대소문자를 접는다 — H2 는 `ıNSERT ıNTO` 를 실행 · K2 여러 문장 판정이 `;` 뿐(`:69`) — SQL Server 는 `;` 없이 잇는다 · K3 `\b` 가 숫자와 글자 사이에 없다(`1INTO`) · K4 `/*! … */` 를 주석으로 지운다 · K5 `SHARE_LOCK:37` 을 `FOR/**/SHARE`·SQL Server 잠금 힌트가 지난다
- 래퍼를 지나는 SQL: 도구 자체 SQL(메타 수집·행 수·코드값 — `core/dialect`·`core/meta`·`core/sqlrun`, 새 금지 낱말 0 건·블록 주석 0 건 grep 확인) + `SqlProbe` 가 감싼 매퍼 SELECT 원문(받은 소스). 표본 XML 여덟 묶음에 새 금지 낱말 후보 스물둘이 0 파일(grep). `DbCorpusBase` 는 `probeRuns:645` 만 래퍼를 쓴다(스니펫·품질 SQL 은 raw)
- 시험 `ReadOnlyJdbcTest`(H2) — `readsPass`·`writesRefused`·`quotingBypassesRefused` 가 지켜야 할 기존 단언. 실물 DB 는 `web/DbCorpusBase.java`(컨테이너 아홉이 상속, `@Order` 절, 등급 A 는 `CorpusFiles.none`)

**⑦·⑧**
- `web/App.java:150` — `cfg.jsonMapper(new JavalinJackson()…)`. `JavalinJackson` 은 final. `Context.bodyAsClass` 는 `JsonMapper.fromJsonString`·`fromJsonStream` 을 부른다(javap). 본문 `null` 이면 Jackson 이 null 을 돌려주고 라우트가 NPE → 500. `bodyAsClass` 39 자리·16 파일. 요청 레코드의 목록 성분은 전부 문자열·레코드 목록이라 null 원소가 정상인 자리가 없다. `Content-Type` 의 모르는 charset 은 `UnsupportedCharsetException` → 500
- `core/check/CheckRunner.java:179` 만 `StackOverflowError` 를 가둔다. `runText:184-189` · `CleanupRunner.java:98·166`(`catch (RuntimeException)`) · `JavaGraph.run:181-207`(파일 파싱)·`:231`·DAO 훑기(`walk`) · `JpaIndex.run:192` 는 안 가둔다. 분석의 「파싱 실패」 미해결 kind 는 `parse`

**함정**
- 빠른 `verify.sh` 는 PMD·SpotBugs 를 안 돈다 — 정규식 상수에 겹친 수량자를 넣으면 CI 에서 SpotBugs `REDOS` 새 짝이 빨갛다(0-85g·h 이력). 아래 스니펫은 겹친 수량자를 피했다
- 새 메서드 순환 복잡도 ≤ 15(`SelfCheckTest.mainMatchesBaseline`) · 빈 catch 는 `// tb-ignore java.emptyCatch <사유>`
- `core/conn`·`SqlProbe.java`·`resources/db`·`DbCorpusBase.java` 는 db 레인, `core/fs`·`core/conn`·`core/analyze`·`core/check` 는 corpus 레인 — 둘 다 push 앞에 돌아야 한다
- 원격에 `origin/work/2026-11-14`(머지된 #127)가 남아 있다 → 가지 이름은 `work/2026-11-14b`

## 3. 설계 결정 — 전부 [고정]

### §3-1 `0-88` — fs 층이 프로필 폴더를 거절
`LocalFiles` 에 생성자 `LocalFiles(Path dataDir, List<Path> alsoForbidden)` 를 더하고 1-인자 생성자는 `this(dataDir, List.of())`. `App.create` 는 `List.of(config.profilesDir())` 를 준다. 프로필 폴더 **아래 전부**를 읽기·쓰기·목록 모두 거절한다(`data/` 와 같은 처분, 글 「시스템·서버 폴더는 다루지 않는다」 그대로).
버린 대안 — `*.yaml` 파일만 거절: 사용자가 둔 사본(`p1.yaml.bak`)이 샌다 · 라우트에서 거르기: `diff/file`·코드 검사·정리가 다 `LocalFiles` 를 타 한 자리가 아니다.

### §3-2 `0-89` — 백업 비밀번호 값 끝을 문맥으로
`PASSWORD` 정규식을 버리고 키만 정규식(`PASSWORD_KEY`), 값 끝은 손 스캐너로 찾는다.
- 키가 줄 처음(들여쓰기·`- ` 뒤)이면 **블록 문맥** — 평문 값은 줄 끝 또는 `공백+#` 앞까지
- 아니면 **흐름 문맥** — 평문 값은 `,`·`}`·`]`·줄 끝 또는 `공백+#` 앞까지
- 따옴표 값은 두 문맥 모두 닫는 따옴표까지(`\"`·`''` 이스케이프)
- 그물 `leaked` 는 「트리가 같은가」 로 바꾼다 — 원본과 지운 글을 Jackson YAML 로 **문서 전부** 읽어, 양쪽의 `password` 값 노드를 `""` 로 맞춘 뒤 트리가 다르면(또는 못 읽으면) 참. 별칭 `PASSWORD_ALIAS` 거절은 그대로
버린 대안 — 정규식만 고치기: 문맥을 못 가르고 게으른 수량자가 제곱 시간·`REDOS` 로 남는다 · 조각이 남았나 세기: 꼬리 길이를 모른다.

### §3-3 `0-90` — url 안 비밀 값 거절을 넓힌다
- 이름이 `password`(뒤 숫자 하나 허용)·`passwd`·`pwd`·`passphrase`·`secret`·`token` 으로 **끝나는** 파라미터(`…=` 바로 앞)면 거절 — 앞 구분자를 안 본다(Teradata `/PASSWORD=` 포함). `passwordCharacterEncoding=`·`sslpasswordcallback=`·`accessTokenCallbackClass=` 는 지난다
- Oracle `user/"따옴표 값"@` 을 더한다
- `%XX` 를 푼 글에도 같은 판정을 댄다
- 오류 글: `접속 <id> 의 url 에 비밀 값이 들어 있다. DB 비밀번호는 password 칸에 넣고, 키 저장소 암호·토큰은 url 에서 뺀다`
버린 대안 — 응답에서 가리기(사용자 결정 3).

### §3-4 `6-39` — 파일에 쓸 오류문은 대조 때 만든다
`Outcome` 에 여섯째 성분 `fileMessage` 를 더한다. `probe()` 가 문장 SQL 을 쥐고 있을 때 `forFile(오류 첫 줄, SQL)` 로 만든다. 규칙: 따옴표 토막(`'…'`·`"…"`·`` `…` ``)은 **식별자 꼴이고 마지막 마디가 그 SQL 의 리터럴 밖 낱말일 때만** 남긴다. 그렇지 않은 첫 따옴표(안 닫힌 것 포함)에서 뒤를 전부 `…` 로 자른다. 글자 사이 아포스트로피(`doesn't`)는 따옴표로 안 본다. `SKIP_REFUSED` 는 우리 글이라 `fileMessage = message`, 나머지는 `""`. xlsx 라우트는 `fileMessage` 만 쓴다(null 이면 `""`) — `message` 는 안 쓴다. `withoutSqlText` 는 지운다.
버린 대안 — 따옴표 토막 전부 지우기: 없는 컬럼 이름이 사라져 xlsx 가 쓸모없다 · 꼴만 보기: 한 낱말 리터럴(`'9001011234567'`)이 남는다.

### §3-5 `6-40` — 문장 id 꼴이 아닌 글은 id 로 안 담는다
`JavaGraph.statement` 의 리터럴·접두·변수 리터럴 셋 다 `[\p{L}\p{N}_.$-]{1,300}` 꼴일 때만 `Stmt`·`prefix` 미해결로 담는다. 아니면 미해결 `statement`(detail `클래스.메서드`) 하나. 옛 실행에 남은 글은 `V015` 가 지운다(공백 든 `ns_id`, 공백 든 `missing`·`prefix` detail — `DELETE`).
버린 대안 — 새 미해결 kind: `UnresolvedKindsTest`·화면 설명까지 번진다 · `UPDATE` 로 자리표시 글: 제약은 없지만 수가 그대로라 의미가 틀린다.

### §3-6 `9-6` — 판정을 원문 낱말로 다시
`check` 순서:
1. 끝 공백·`;` 뗌 → 비면 거절
2. **주석 꼴** — 원문에서 `/*` 마다: 바로 뒤가 `!`·`M!`·`m!` 면 거절 「실행 주석」 · 짝 `*/` 가 없으면 거절 「안 닫힌 주석」 · 짝 앞에 `/*` 가 또 있으면 거절 「겹친 주석」(따옴표 상태를 안 본다 — 엄격한 쪽)
3. **첫 낱말** — 원문 앞의 공백·`--` 줄·`/* */` 를 건너뛴 자리의 ASCII 글자 낱말이 SELECT·WITH
4. 원문에 `;` 면 거절
5. **낱말 훑기** — `Normalizer NFKC` + `toUpperCase(Locale.ROOT)` 한 글을 낱말로 쪼갠다. 낱말 글자 = 유니코드 글자·숫자·`_`. **숫자로 시작한 토막은 숫자·`.` 까지만**(뒤 글자는 새 낱말 — `1INTO` → `INTO`). 따옴표·주석 안도 훑는다
6. 금지 낱말이 있으면 거절 · `FOR` 뒤 어디든 `SHARE` 가 있으면 거절

금지 낱말 = 기존 열다섯(INSERT·UPDATE·DELETE·MERGE·DROP·ALTER·CREATE·TRUNCATE·GRANT·REVOKE·CALL·EXEC·EXECUTE·INTO·LOCK) + 잇기 열다섯(SHUTDOWN·BACKUP·RESTORE·KILL·DENY·DBCC·DISABLE·ENABLE·RECONFIGURE·CHECKPOINT·WAITFOR·SETUSER·REVERT·WRITETEXT·UPDATETEXT) + 잠금 힌트 여덟(UPDLOCK·HOLDLOCK·TABLOCK·TABLOCKX·XLOCK·PAGLOCK·SERIALIZABLE·REPEATABLEREAD). `NOLOCK`·`READPAST` 는 지난다. `SET`·`USE`·`COMMENT`·`REPLACE` 는 안 넣는다(SELECT 에 흔한 이름·함수 — 첫 낱말 판정과 실행 주석 거절이 덮는다). `strip` 은 지운다.
버린 대안 — SQL 문법 파서: 방언 다섯을 못 덮는다 · 방언별 판정: 래퍼가 방언을 모른다.

### §3-7 `9-7` — 실물 DB 단언(db 레인)
`DbCorpusBase` 에 `@Order(4)` 절 `readOnlyBypasses` 하나. 컨테이너 아홉이 다 돈다. 두 가지를 잰다: ① 그 DB 가 우회 꼴을 **실제로 실행한다**(전제 — raw 연결) ② 같은 글을 래퍼는 **거절한다**. 둘 다 등급 A(`CorpusFiles.none`). 같은 절에서 `SqlProbe` 손 문장 셋으로 `fileMessage` 를 실물 오류문에 댄다(6-39 의 db 강제 지점).

### §3-8 `0-91` — 본문 null·목록 안 null·모르는 charset 은 400
`web/StrictJson`(새 파일) — `JsonMapper` 를 구현해 `JavalinJackson` 에 넘기고, 읽은 결과가 null 이면 `BadRequestResponse("본문이 비었다")`, 결과가 레코드면 **첫 층** 목록 성분에 null 원소가 있을 때 `BadRequestResponse("목록 <성분> 안에 null 이 있다")`. `App.jsonErrors` 에 `IllegalCharsetNameException`·`UnsupportedCharsetException` → 400 「Content-Type 의 charset 을 모른다」.
버린 대안 — 라우트 39 자리에 null 검사: 청크 한도(파일 3)를 넘고 새 라우트가 또 빠진다 · Jackson `Nulls.FAIL` 전역: 안쪽 목록까지 번져 범위를 못 잰다 · NPE → 400 매퍼: 진짜 결함을 가린다.

### §3-9 `0-92a`·`0-92b` — 파일 하나의 StackOverflowError 는 그 파일만
- `CheckRunner.runText` — 잡아서 `skipped 1` 인 결과(화면 「못 읽음 1」)
- `CleanupRunner` 두 자리 — `catch (RuntimeException | StackOverflowError e)`, 사유 글 그대로(`내부 오류 — StackOverflowError`)
- `JavaGraph` — 파일 파싱과 메서드 훑기(`walk`)를 각각 도우미로 감싸 미해결 `parse`(파일, 줄)로 적고 건너뛴다 · `JpaIndex.run` — 파싱에서 잡아 건너뛴다(JavaGraph 가 `parse` 로 센다)

### 뒤 번들 — 결정 요지만(설계 전)
- **번들 85** 코드 정리 Critical 열둘 — 기본 켬 여덟(E-eqRecv·E-consoleSide·E-backtickParity·E-regexParen·E-htmlTextArea·E-switchHeadComment·E-xmlNoDecl·E-fieldOrder) 먼저, 고른 규칙 넷(E-loggerInit·E-interruptLoop·E-twrQuietClose·E-twrFormBCatch). 머지 전 반입이면 기본 끔 내리기를 앞머리에
- **번들 86** 조용히 틀린 출력 Critical 열셋 — 생성기(A-01·A-03·A-11·A-12·A-15·A-28·R-A-C1) · 산출물(B-01·B-02) · 분석(C-TBL-COMMA) · 스냅샷(F-K8·F-K9·F-K21). A-28·F-K21 은 db 레인 실물 RED
- **번들 87~** Important 35 + 보정 Important 12 · 묶음 Minor(6절 ①~⑥·⑨~⑪) · 5-100 에 붙일 D 둘

## 4. 실행 스텝

행마다 끝은 CLAUDE.md 「Task 끝」 순서(분할표·이력 → `bash scripts/verify.sh` → `git commit` 따로 → `task-done`). 시험을 먼저 쓰고 RED 를 본 뒤 고친다. 커밋 제목 `fix: <번호> <무엇>`.

### S0 — 가지 · 설계 문서 · 행 · 번들 표
- 대상: `design/69-bundle84.md`(새 파일) · `PLAN.md` · `PROGRESS.md`
- 변경:
  1. `git checkout main && git pull && git checkout -b work/2026-11-14b`
  2. 이 플랜 파일(`C:\Users\EJG\.claude\plans\pasted-content-id-4440-84-majestic-wilkes.md`) 전체를 `design/69-bundle84.md` 로 옮긴다(글 그대로)
  3. `PROGRESS.md` 이력 끝 줄(「PR #127 claude[bot] 처분」)의 `(이 커밋)` 을 `11b1b88` 로
  4. `PLAN.md` 15장 표 끝에 번들 행 넷:
     - `| 번들 84 | S0→0-88→0-89→0-90→6-39→6-40→9-6→9-7→0-91→0-92a→0-92b→V-63a→R-30→V-63 | 2026-11-14(Fable, 설계 69 — design/69-bundle84.md). **가로 점검 2차 — 절대 규칙 2·3·6 Critical + ⑦·⑧.** fs 층 프로필 폴더 거절 · 백업 비밀번호 꼬리 · url 비밀 값 거절 넓힘 · DB 대조 xlsx 오류문 · DAO SQL 글 저장 · ReadOnlyJdbc 판정 다시(겹친·실행 주석·ı·숫자 붙임·잇기·잠금 힌트) + 실물 DB 단언 · 본문 null 400 · 파일 하나의 스택 넘침 가둠. 사용자 결정 넷(2026-11-14): 기본 끔 청크 안 넣음 · ⑦⑧ 포함 · url 거절 넓힘 · 머지 위임. 결함 키 22. 마이그레이션 V015. 레인 java+corpus+db — --corpus → Docker → --db. **머지 위임** | 표본 폴더 · node · Docker | 대기 |`
     - `| 번들 85 | (설계 전) | 요지 — 코드 정리 Critical 열둘(설계 68 E). 기본 켬 여덟 먼저 | — | 설계 전 |`
     - `| 번들 86 | (설계 전) | 요지 — 조용히 틀린 출력 Critical 열셋(A 여섯·R-A-C1·B 둘·C-TBL-COMMA·F-K8·K9·K21). A-28·F-K21 은 db 레인 실물 | — | 설계 전 |`
     - `| 번들 87~ | (설계 전) | 요지 — Important 35 + 보정 Important 12 · 묶음 Minor(설계 68 6절 ①~⑥·⑨~⑪) · D-a11yMultiline·D-piiLogSOE 는 5-100 에 | — | 설계 전 |`
  5. `PLAN.md` 16장 표 끝(`0-87` 행 뒤)에 행 열셋. 꼴은 번들 83 행과 같다 — `| <번호> | <청크> | **설계 69 §3-n·§4 가 원본**. <요지>. **축**: <규칙>. **강제 지점**: <시험> | <선행> | 대기 |`

     | # | 청크 | 요지 | 축 | 강제 지점 | 선행 |
     |---|---|---|---|---|---|
     | 0-88 | fs 층이 프로필 폴더를 거절 | §3-1. `LocalFiles` 거절 목록에 프로필 폴더(G1-K01) | 규칙 2 | `FsRoutesTest.profileFolderIsRefused` · `LocalFilesTest.extraForbiddenFolderIsRefused` | S0 |
     | 0-89 | 백업 비밀번호 꼬리 | §3-2. 값 끝을 블록·흐름 문맥으로 · 그물은 트리 같음(G1-K02·K17) | 규칙 2 | `ProfileRoutesTest.backupDropsPasswordTails`·`fragmentLeftBehindIsRefused`·`longSpacesInValueAreLinear` | S0 |
     | 0-90 | url 안 비밀 값 거절 넓힘 | §3-3. 끝 낱말 판정 · Oracle 따옴표 · `%XX`(G2-K1·F-K17) | 규칙 2 | `ProfileStoreTest.passwordInUrlIsRejected` · gate-probe `url-password` | S0 |
     | 6-39 | DB 대조 xlsx 오류문 | §3-4. `Outcome.fileMessage` — 자기 SQL 의 식별자만 남김(C-PROBE-MSG) | 규칙 3 | `SqlProbeTest.fileMessageKeepsOnlyOwnIdentifiers` · `AnalyzeRoutesTest.probe` · db 는 9-7 | S0 |
     | 6-40 | DAO SQL 글을 문장 id 로 안 담는다 | §3-5. id 꼴 판정 + V015 지움(R-C-1) | 규칙 3 | `JavaGraphTest.sqlTextIsNotAStatementId` · `AnalyzeStoreTest.scrubDropsSqlTextIds` · `DbTest` 15 | S0 |
     | 9-6 | ReadOnlyJdbc 판정 다시 | §3-6. 주석 꼴 · 첫 낱말 · NFKC 낱말 훑기 · 금지 낱말 38 · FOR…SHARE(F-K1~K6) | 규칙 6 | `ReadOnlyJdbcTest.bypassesFromAudit2`·`stillPasses` + 기존 셋 | S0 |
     | 9-7 | 읽기 전용 우회 · 오류문 실물 DB 단언 | §3-7. `DbCorpusBase.readOnlyBypasses` — 전제(raw 실행) + 래퍼 거절 + `fileMessage`(F-K2~K5 H2 대역 → 실물) | 규칙 6·3 | `DbCorpusBase.readOnlyBypasses`(컨테이너 아홉, V-63 `--db`) | 9-6·6-39 |
     | 0-91 | 본문 null·목록 null·charset 400 | §3-8. `StrictJson` 한 자리(6절 ⑦ 키 여섯) | 관례(0-35) | `JsonErrorTest.nullBodyIs400`·`nullListElementIs400`·`unknownCharsetIs400` | S0 |
     | 0-92a | 스택 넘침 가둠 — 검사·정리 | §3-9. `runText` · `CleanupRunner` 둘(D-runTextSOE·E-soe) | 관례(0-85f) | `CheckRunnerTest.pastedStackOverflowIsSkipped` · `CleanupRunnerTest.stackOverflowFileIsSkipped` | S0 |
     | 0-92b | 스택 넘침 가둠 — 분석 | §3-9. `JavaGraph` 파싱·훑기 · `JpaIndex`(C-RUN-SOE) | 관례(0-85f) | `JavaGraphTest.deepFileIsSkippedNotFatal` | 0-92a |
     | V-63a | corpus 골든 확인 | §4. `--corpus` · diff 0 또는 허용 변화 · 벽시계 | 4장 | `verify.sh --corpus` | 0-92b·9-7 |
     | R-30 | 손 판정 · 처분 표 | §4. compare ㉝ — 키 22 처분 · 옮겨간 미해결 · 실물 프로필 로드 · curl | 4장 | compare ㉝ 절 | V-63a |
     | V-63 | 정적 분석 · `--db` | §4. PMD·SpotBugs · 컨테이너 아홉 · 전제 여섯 결과 · 벽시계 | 4장 | `verify.sh --db` | R-30 |
  6. `PROGRESS.md` 이력에 한 줄 — `| 2026-11-14 | 설계 69 — 번들 84 | 가로 점검 2차 Critical 34 가운데 절대 규칙 2·3·6 열 + R-C-1 + F-K5 + ⑦ 여섯 + ⑧ 셋 + G1-K17 = 키 22 를 행 열(고침) + 검증 셋으로. 보정: 열린 행과 겹침 0 · D 둘은 5-100 과 같은 뿌리. 사용자 결정 넷 — 기본 끔 청크 안 넣음 · ⑦⑧ 포함 · url 거절 넓힘 · 머지 위임. 번들 85·86·87~ 은 요지만 | (이 커밋) |`. 「현재 상태」 블록의 「진행중 청크」 머리를 `번들 84(설계 69) 실행 대기` 로
- 검증: `bash scripts/verify.sh` 초록(docs 레인 — doc-lint)
- 의존: 없음

### 0-88 — fs 층이 프로필 폴더를 거절
- 대상: `core/fs/LocalFiles.java` 생성자·클래스 javadoc(30행) · `web/App.java:177`
- 변경:
  ```java
  public LocalFiles(Path dataDir) {
      this(dataDir, List.of());
  }

  /** @param alsoForbidden 더 거절할 폴더 — 프로필 폴더(절대 규칙 2: 접속 비밀번호가 든 YAML 을 fs 층으로 못 읽는다) */
  public LocalFiles(Path dataDir, List<Path> alsoForbidden) {
      // 기존 몸 + f.add(this.dataDir) 뒤에
      for (Path p : alsoForbidden) {
          f.add(p.toAbsolutePath().normalize());
      }
  }
  ```
  `App`: `new LocalFiles(config.dataDir(), List.of(config.profilesDir()))`. javadoc 「서버 자기 data/ 안은 거절」 → 「서버 자기 data/·프로필 폴더 안은 거절」
- 시험(먼저): `FsRoutesTest.profileFolderIsRefused` — 시험 안에서 `tmp/profiles/p2.yaml` 에 `connections:\n  - {id: a, dialect: h2, url: 'jdbc:h2:mem:x', user: sa, password: FAKE-PW-88}` 를 쓴다 → `GET /api/fs/read?path=<p2.yaml>` 400·본문에 `FAKE-PW-88` 없음(RED: 200 + 값) · `GET /api/diff/file?a=<p2.yaml>&b=<p2.yaml>` 400 · `GET /api/fs/list?path=<tmp/profiles>` 400. `LocalFilesTest.extraForbiddenFolderIsRefused` — 2-인자 생성자, 그 폴더·하위 파일 `check` 가 `Refused(400)`, 형제 폴더는 통과
- 검증: `bash scripts/mvn.sh -q test -Dtest='FsRoutesTest,LocalFilesTest,DiffRoutesTest'` → `bash scripts/verify.sh`
- 의존: S0
- 사다리: 다른 시험이 프로필 폴더 아래를 `LocalFiles` 로 읽어 빨개지면 그 시험의 폴더 배치를 고친다(운영 코드 예외를 만들지 않는다)

### 0-89 — 백업 비밀번호 꼬리
- 대상: `web/ProfileRoutes.java` — `PASSWORD`(지움)·`withoutPasswords`·`leaked`·`count`(지움)·`collectPasswords`(지움)
- 변경:
  ```java
  /** password 키와 콜론 — 값은 valueEnd 가 문맥으로 끝을 찾는다(0-89) */
  private static final java.util.regex.Pattern PASSWORD_KEY =
          java.util.regex.Pattern.compile("((?:\"password\"|'password'|\\bpassword)[ \\t]*:)[ \\t]*");

  static String withoutPasswords(String yaml) {
      String s = withoutBlockPasswords(yaml);
      StringBuilder out = new StringBuilder(s.length());
      java.util.regex.Matcher m = PASSWORD_KEY.matcher(s);
      int at = 0;
      while (m.find(at)) {
          out.append(s, at, m.start()).append(m.group(1)).append(" ''");
          at = valueEnd(s, m.end(), flow(s, m.start()));
      }
      return out.append(s, at, s.length()).toString();
  }

  /** 키 앞이 들여쓰기·「- 」 뿐이면 블록 문맥(거짓), 아니면 흐름 문맥 */
  static boolean flow(String s, int keyStart) {
      int line = s.lastIndexOf('\n', keyStart - 1) + 1;
      return !s.substring(line, keyStart).replace("-", "").isBlank();
  }

  static int valueEnd(String s, int v, boolean flow)   // 첫 글자가 " 면 quotedEnd(s, v, '"', true), ' 면 quotedEnd(s, v, '\'', false), 아니면 plainEnd
  private static int quotedEnd(String s, int v, char q, boolean backslash)  // \" 는 두 글자 건너뜀 · '' 는 두 글자 건너뜀 · 닫는 따옴표 다음 자리, 없으면 s.length()
  private static int plainEnd(String s, int v, boolean flow)
      // 줄 끝(\r·\n)에서 멈춤 · # 이고 바로 앞이 공백·탭이면 멈춤 · flow 이고 , } ] 면 멈춤 → 멈춘 자리에서 뒤 공백·탭을 되물린 자리
  ```
  `leaked`:
  ```java
  static boolean leaked(String original, String scrubbed) {
      if (PASSWORD_ALIAS.matcher(original).find()) {
          return true;
      }
      try {
          return !blanked(original).equals(blanked(scrubbed));
      } catch (java.io.IOException | RuntimeException e) {
          return true; // 못 읽으면 남았다고 본다
      }
  }

  /** 문서 전부를 읽어 password 값 노드(값·null)를 "" 로 맞춘 트리 */
  private static java.util.List<JsonNode> blanked(String text) throws java.io.IOException {
      try (com.fasterxml.jackson.databind.MappingIterator<JsonNode> it = YAML.readerFor(JsonNode.class).readValues(text)) {
          java.util.List<JsonNode> docs = it.readAll();
          docs.forEach(ProfileRoutes::blank);
          return docs;
      }
  }
  private static void blank(JsonNode n)  // ObjectNode 면 필드마다: 키가 password 이고 값이 컨테이너가 아니면 put("password", ""), 아니면 재귀 · 배열은 원소 재귀
  ```
- 시험(먼저): `backupDropsPasswordTails` — 입력 넷(판정 G1-K02): `password: FAKE#tail` · `password: FAKE,tail` · `password: FAKE}tail` · `- {id: b, password: FAKE#tail}` → 출력에 `tail` 없음, 블록은 `password: ''` 로 끝나는 줄, 흐름은 `password: ''}`(RED: `''#tail` 등). `fragmentLeftBehindIsRefused` — `leaked("a:\n  password: FAKE#tail\n", "a:\n  password: ''#tail\n")` 참 · `leaked(같은 원본, "a:\n  password: ''\n  x: tail\n")` 참 · 바르게 지운 글은 거짓. `longSpacesInValueAreLinear` — `"password: a" + " ".repeat(200_000) + "b\n"` 를 `assertTimeoutPreemptively(5초)` 안에. 기존 `backupDropsPasswords`·`longQuotedPasswordDoesNotOverflow`·`multiLinePlainPasswordIsRefused`·`aliasPasswordIsCaught`·`conflictLeavesNoBackup`·`codecheckSaveBacksUpPreviousYaml` 은 안 고치고 초록
- 검증: `bash scripts/mvn.sh -q test -Dtest=ProfileRoutesTest` → `bash scripts/verify.sh`
- 의존: S0
- 사다리: 기존 단언 `password: '' # 주석` 이 깨지면 `plainEnd` 의 뒤 공백 되물림을 본다(주석 앞 공백 하나는 남아야 한다)

### 0-90 — url 안 비밀 값 거절 넓힘
- 대상: `core/profile/Profile.java:55-69` · `scripts/probes/url-password.patch`
- 변경:
  ```java
  private static final java.util.regex.Pattern PASSWORD_IN_URL = java.util.regex.Pattern.compile(
          "(?i)(?:(?:password\\d?|passwd|pwd|passphrase|secret|token)\\s*=)|(?://[^/@\\s]+:[^/@\\s]+@)"
                  + "|(?::(?:thin|oci8?):[^@/\\s]+/(?:\"[^\"]*\"|[^@\\s]+)@)"
                  + "|(?:^\\s*jdbc:cubrid:[^:]*:[^:]*:[^:]*:[^:]*:[^:?\\s]+:)");
  private static final java.util.regex.Pattern PERCENT = java.util.regex.Pattern.compile("%([0-9A-Fa-f]{2})");

  public Connection {
      if (url != null && (PASSWORD_IN_URL.matcher(url).find() || PASSWORD_IN_URL.matcher(unescaped(url)).find())) {
          throw new IllegalArgumentException("접속 " + id + " 의 url 에 비밀 값이 들어 있다. DB 비밀번호는 password 칸에 넣고, 키 저장소 암호·토큰은 url 에서 뺀다");
      }
  }

  /** %XX 를 글자로 — 드라이버가 키를 풀어 읽는 꼴(%70assword=) */
  private static String unescaped(String url)  // PERCENT 를 Matcher.appendReplacement(quoteReplacement((char) Integer.parseInt(g1, 16))) 로
  ```
  javadoc 에 0-90 한 줄(끝 낱말 판정 · Oracle 따옴표 · `%XX`). 게이트 탐침 다시 뜨기: 고친 `Profile.java` 를 `git add` → 조건 줄을 `if (url != null && false && (…)) { // gate-probe url-password` 로 바꾼다 → `git diff -- src/main/java/kr/ejg/toolbox/core/profile/Profile.java > scripts/probes/url-password.patch` → `git checkout -- src/main/java/kr/ejg/toolbox/core/profile/Profile.java`(색인의 고친 판으로 돌아온다)
- 시험(먼저): `ProfileStoreTest.passwordInUrlIsRejected` 거절 목록에 더한다(판정 F-K17·G2-K1) — `jdbc:mysql://h/db?user=u&password1=secret` · `jdbc:mysql://h/db?%70assword=secret` · `jdbc:teradata://h/PASSWORD=secret` · `jdbc:postgresql://h/db?sslpassword=secret` · `jdbc:sqlserver://h;trustStorePassword=secret` · `jdbc:mysql://h/db?trustCertificateKeyStorePassword=secret` · `jdbc:sqlserver://h;accessToken=secret` · `jdbc:oracle:thin:APP/"se cret"@h:1521/S`. 통과 목록에 — `jdbc:mysql://h/db?passwordCharacterEncoding=UTF-8` · `jdbc:postgresql://h/db?sslpasswordcallback=com.x.Cb` · `jdbc:sqlserver://h;accessTokenCallbackClass=com.x.Cb` · `jdbc:sqlserver://h;encrypt=true;trustServerCertificate=true` · `jdbc:h2:mem:x;DB_CLOSE_DELAY=-1`
- 검증: `bash scripts/mvn.sh -q test -Dtest='ProfileStoreTest,ProfilePasswordTest,ConnRoutesTest'` → `bash scripts/verify.sh`. 탐침은 마무리 `gate-probe.sh` 가 잰다
- 의존: S0
- 사다리: 저장소의 예시·시험 프로필 url 이 새 판정에 걸리면 그 url 이 실제 비밀 파라미터인지 본다 — 비밀이면 시험 값을 고치고, 아니면 그 키 이름을 통과 단언에 더하고 판정은 「끝 낱말」 규칙 안에서만 좁힌다(이력 「정한 것」)

### 6-39 — DB 대조 xlsx 오류문
- 대상: `core/analyze/SqlProbe.java` · `web/AnalyzeRoutes.java:433`
- 변경:
  ```java
  /** fileMessage — 파일(xlsx)에 쓸 글. 그 문장 SQL 의 식별자만 남긴 오류문(규칙 3). 화면은 message */
  public record Outcome(String nsId, String file, int line, String status, String message, String fileMessage) {
  }

  private static final Pattern QUOTED = Pattern.compile("(['\"`])(.*?)\\1");
  private static final Pattern PART = Pattern.compile("[\\p{L}\\p{N}_$#]+");
  private static final Pattern LITERAL = Pattern.compile("'(?:[^']|'')*+'");
  private static final Pattern NOT_NAME = Pattern.compile("[^\\p{L}\\p{N}_$#]+");

  /** 따옴표 토막은 「식별자 꼴 + 마지막 마디가 이 SQL 의 리터럴 밖 낱말」 일 때만 남기고, 그렇지 않은 첫 따옴표에서 뒤를 … 로 자른다 */
  static String forFile(String message, String sql) {
      String m = message.replaceAll("(?<=\\p{L})'(?=\\p{L})", "’"); // doesn't — 따옴표가 아니다
      java.util.Set<String> names = names(sql);
      java.util.regex.Matcher q = QUOTED.matcher(m);
      StringBuilder out = new StringBuilder();
      int at = 0;
      while (q.find()) {
          out.append(m, at, q.start());
          if (!own(q.group(2), names)) {
              return out.append('…').toString();
          }
          out.append(q.group());
          at = q.end();
      }
      String tail = m.substring(at);
      int open = firstQuote(tail); // 200자에서 잘려 안 닫힌 따옴표
      return out.append(open < 0 ? tail : tail.substring(0, open) + "…").toString();
  }
  static boolean own(String token, Set<String> names)   // 길이 1~128 · '.' 로 나눈 마디가 전부 PART 에 맞음 · 마지막 마디 toUpperCase(ROOT) 가 names 에
  static Set<String> names(String sql)                  // LITERAL 을 공백으로 → NOT_NAME 으로 쪼갠 낱말 대문자
  private static int firstQuote(String s)               // ' " ` 가운데 가장 앞 자리, 없으면 -1
  ```
  `probe()`: ERROR 는 `new Outcome(…, ERROR, msg, forFile(msg, text))`(msg = `firstLine(first)`, text = 끼운 원문) · `SKIP_REFUSED` 는 `(…, m, m)` · 나머지 다섯 자리는 `"", ""`. `withoutSqlText` 를 지운다. 클래스 javadoc 끝 문장을 「결과는 응답에만 — 파일에는 fileMessage 만」 으로. `AnalyzeRoutes`: `rows.add(Arrays.asList(o.nsId(), o.file(), o.line(), o.status(), o.fileMessage() == null ? "" : o.fileMessage()));`
- 시험(먼저): `SqlProbeTest.sqlTextIsCutForFiles` 를 `fileMessageKeepsOnlyOwnIdentifiers` 로 바꾼다 — H2 로 문장 셋(판정 C-PROBE-MSG 의 s01·s07 꼴): `SELECT A FROM T WHERE A = 'ZZLIT9' AND AND A = 1`(문법) · `SELECT A FROM T WHERE A = 'ZZLIT9'`(변환, A 는 INT) · `SELECT NOPE_COL FROM T`. ERROR 인 행마다 `fileMessage` 에 `ZZLIT9`·`AND AND`·`tb_probe` 없음(RED: 옛 함수는 남긴다), 셋째는 `NOPE_COL` 있음. 단위 단언 — `forFile("Unknown column 'x' in 'field list'", "SELECT x FROM t")` = `Unknown column 'x' in …` · `forFile("Table 'db.t' doesn't exist", "SELECT 1 FROM t")` 에 `'db.t'` 있음 · `forFile("ORA-00904: \"NOPE\": invalid identifier", "SELECT NOPE FROM T")` 그대로 · `forFile("… value 'abc' to int", "SELECT a FROM t WHERE a = 'abc'")` 에 `abc` 없음. `AnalyzeRoutesTest.probe` — 응답 행마다 `fileMessage` 필드가 있고 ERROR 행 `fileMessage` 에 `SELECT` 없음, xlsx 200 그대로
- 검증: `bash scripts/mvn.sh -q test -Dtest='SqlProbeTest,AnalyzeRoutesTest'` → `bash scripts/verify.sh`
- 의존: S0
- 사다리: 변환 문장이 H2 에서 PASS 면 그 단언은 「ERROR 일 때만」 조건으로 둔다

### 6-40 — DAO SQL 글을 문장 id 로 안 담는다
- 대상: `core/analyze/JavaGraph.java:506-532` · `src/main/resources/db/migration/V015__analyze_scrub_sql_text.sql`(새 파일)
- 변경:
  ```java
  /** 문장 id 꼴(이름공간.id) — 공백·연산자가 든 글은 SQL 본문이라 id 로 안 담는다(6-40, 규칙 3) */
  private static final Pattern STMT_ID = Pattern.compile("[\\p{L}\\p{N}_.$-]{1,300}");
  ```
  `statement`: 리터럴 — `STMT_ID` 에 맞을 때만 `Stmt(…, "literal")`, 아니면 아래 `note("statement", …)` 로 떨어진다 · 접두 — 맞을 때만 `Stmt(…, "prefix")` + `note("prefix", …)` · 변수 — `varLiterals` 결과를 `STMT_ID` 로 거른 것이 비지 않을 때만. 분기가 늘면 `private boolean idShaped(String v)` 로 뺀다.
  ```sql
  -- 6-40(설계 69) — 문장 id 자리에 저장된 SQL 글을 지운다(절대 규칙 3). 문장 id 에는 공백이 없다
  DELETE FROM analyze_stmt WHERE REGEXP_LIKE(ns_id, '\s');
  DELETE FROM analyze_unresolved WHERE kind IN ('missing', 'prefix') AND REGEXP_LIKE(detail, '\s');
  ```
- 시험(먼저): `JavaGraphTest.sqlTextIsNotAStatementId` — 인라인 소스 둘(`viewShapeIsNotAView` 꼴): `@Controller` 클래스가 필드 `LegacyDao dao` 의 `dao.save()` 를 부르고, `@Repository class LegacyDao { void save() { update("UPDATE TB_X SET NM = 'ZZLIT9' WHERE ID = 1"); int n = 1; delete("DELETE FROM TB_X WHERE ID = " + n); insert("Legacy.insertOne"); } }`. 단언 — 프로그램 문장 id 는 `Legacy.insertOne` 하나 · 어떤 `Stmt.id`·미해결 detail 에도 `ZZLIT9`·`TB_X` 없음(RED: 둘 다 있음) · 미해결 `statement` detail `LegacyDao.save`. `AnalyzeStoreTest.scrubDropsSqlTextIds` — 실행 하나를 저장한 뒤 `analyze_stmt`·`analyze_unresolved` 에 공백 든 글 행을 SQL 로 직접 넣고 `V015` 파일 글을 문장별로 실행 → id 꼴 행만 남음. `DbTest` 의 14 둘을 15 로
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,AnalyzeStoreTest,DbTest,AnalyzeRoutesTest'` → `bash scripts/verify.sh`
- 의존: S0
- 사다리: `golden/analyze/*.json` 이 바뀌면 바뀐 줄이 전부 「공백 든 id 가 빠지고 `statement` 미해결이 생김」 인지 보고 갱신(이력에 줄 수). 다른 변화면 멈춘다

### 9-6 — ReadOnlyJdbc 판정 다시
- 대상: `core/conn/ReadOnlyJdbc.java` — `WRITE`·`SHARE_LOCK`·`check`·`strip`(지움)
- 변경:
  ```java
  private static final Set<String> WRITE = Set.of("INSERT", "UPDATE", "DELETE", "MERGE", "DROP", "ALTER", "CREATE", "TRUNCATE", "GRANT", "REVOKE",
          "CALL", "EXEC", "EXECUTE", "INTO", "LOCK",
          // 9-6: ; 없이 이어지는 문장(SQL Server 배치)
          "SHUTDOWN", "BACKUP", "RESTORE", "KILL", "DENY", "DBCC", "DISABLE", "ENABLE", "RECONFIGURE", "CHECKPOINT", "WAITFOR", "SETUSER", "REVERT",
          "WRITETEXT", "UPDATETEXT",
          // 9-6: SQL Server 잠금 힌트
          "UPDLOCK", "HOLDLOCK", "TABLOCK", "TABLOCKX", "XLOCK", "PAGLOCK", "SERIALIZABLE", "REPEATABLEREAD");

  static void check(String sql) throws SQLException {
      String raw = trimEnd(sql == null ? "" : sql);
      if (raw.isEmpty()) {
          throw refuse("빈 문장");
      }
      comments(raw);
      String first = firstWord(raw);
      if (!FIRST.contains(first)) {
          throw refuse(first.isEmpty() ? "SELECT·WITH 가 아닌 문장" : first + " 문장");
      }
      if (raw.indexOf(';') >= 0) {
          throw refuse("여러 문장(리터럴·주석 안의 ; 포함)");
      }
      words(raw);
  }

  /** 블록 주석 꼴 — DB 마다 다르게 읽히는 것은 전부 거절: 실행 주석(/*! /*M!), 안 닫힘, 겹침. 따옴표 상태는 안 본다(엄격한 쪽) */
  private static void comments(String raw) throws SQLException
      // for (int i = raw.indexOf("/*"); i >= 0; i = raw.indexOf("/*", e + 2)):
      //   startsWith("!", i+2) || regionMatches(true, i+2, "M!", 0, 2) → refuse("실행 주석(/*! … */)")
      //   e = raw.indexOf("*/", i + 2); e < 0 → refuse("안 닫힌 주석")
      //   inner = raw.indexOf("/*", i + 2); 0 <= inner < e → refuse("겹친 주석")

  /** 앞의 공백·-- 줄·블록 주석을 건너뛴 자리의 ASCII 글자 낱말(대문자). 글자가 아니면 "" */
  private static String firstWord(String raw)

  /** NFKC + 대문자로 맞춘 원문의 낱말 — 글자·숫자·_ 이어짐. 숫자로 시작한 토막은 숫자·점까지만(1INTO 의 INTO 는 새 낱말). 따옴표·주석 안도 본다 */
  private static void words(String raw) throws SQLException
      // String s = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC).toUpperCase(Locale.ROOT);
      // boolean seenFor = false; 낱말마다: WRITE.contains(w) → refuse(w + " 이 든 문장")
      //   "FOR".equals(w) → seenFor = true · seenFor && "SHARE".equals(w) → refuse("행 잠금(FOR SHARE)")
  ```
  낱말 자르기는 `private static int wordEnd(String s, int i)`(글자·`_` 로 시작 → 글자·숫자·`_` 끝까지 / 숫자로 시작 → 숫자·`.` 끝까지 / 그 밖 → `i + 1`)로 빼 복잡도를 나눈다. `Character.isLetter`·`isDigit` 를 쓴다. 클래스·`check` javadoc 을 새 순서로 고친다
- 시험(먼저): `ReadOnlyJdbcTest.bypassesFromAudit2` —
  - K1 실행: `ro` 로 `/* /* */ SELECT */ COMMENT ON TABLE T IS 'changed-by-probe'` → `SQLFeatureNotSupportedException`, raw 로 `SELECT REMARKS FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'T'` 가 `changed-by-probe` 아님(RED: 실행됨)
  - K6 실행: `ro` 로 `"SELECT * FROM FINAL TABLE (\u0131NSERT \u0131NTO T VALUES (2))"` → 거절, `count()` 1(RED: 2)
  - `ReadOnlyJdbc.check` 거절 목록: `SELECT 1 SHUTDOWN WITH NOWAIT` · `SELECT 1 KILL 55` · `SELECT 1 DISABLE TRIGGER ALL ON T` · `SELECT 1 DENY SELECT ON T TO public` · `SELECT 1 DBCC CHECKDB` · `SELECT 1 BACKUP DATABASE x TO DISK='y'` · `SELECT 1INTO t2 FROM t` · `SELECT A FROM T WHERE A=1FOR SHARE` · `/*! REPLACE t */ SELECT 1` · `/*M! REPLACE t */ SELECT 1` · `SELECT A FROM T FOR/**/SHARE` · `SELECT A FROM T FOR--\nSHARE` · `SELECT A FROM T WITH (TABLOCKX, HOLDLOCK)` · `SELECT A FROM T WITH (UPDLOCK)` · `SELECT 1 /* 안 닫힘`
  - `stillPasses` — `check` 가 안 던짐: `SELECT A FROM T WITH (NOLOCK)` · `SELECT /*+ INDEX(T) */ A FROM T` · `SELECT FOR_SHARE_YN, ENABLED, DISABLE_AT FROM T` · `SELECT 이름 FROM 사원 WHERE 부서 = ?` · `SELECT T1UPDATE FROM T` · `SELECT 1e5, 0x1F FROM T`
  - 기존 `readsPass`·`writesRefused`·`quotingBypassesRefused`·`writePathsRefused`·`lobsAreReadOnly`·`metadataWorksWithoutLeak` 는 안 고치고 초록
- 검증: `bash scripts/mvn.sh -q test -Dtest='ReadOnlyJdbcTest,SqlProbeTest,ConnectionRegistryTest,VendorFallbackTest'` → `bash scripts/verify.sh`
- 의존: S0
- 사다리: 기존 단언 하나가 깨지면 새 판정을 그 단언에 맞춘다(기존 통과·거절을 뒤집지 않는다). `ImportReviewDocTest` 가 빨개지면 문서의 래퍼 설명 줄만 맞춘다

### 9-7 — 읽기 전용 우회 · 오류문 실물 DB 단언
- 대상: `src/test/java/kr/ejg/toolbox/web/DbCorpusBase.java`(시험 파일 하나)
- 변경: `probeRuns` 아래에 절 하나.
  ```java
  /** 9-7(설계 69) — 가로 점검 2차 F-K1~K5 를 실물 DB 에. 전제(이 DB 가 우회 꼴을 실제로 실행한다)와 래퍼 거절 둘 다 등급 A. 6-39 fileMessage 도 실물 오류문에 */
  @Test
  @Order(4)
  void readOnlyBypasses() throws Exception {
      skipIfOff("readOnlyBypasses");
      // boolean ac = conn.getAutoCommit(); conn.setAutoCommit(true); try { … } finally { 표 둘 DROP(오류 무시) · conn.setAutoCommit(ac); }
  }
  ```
  몸: raw `conn` 으로 `CREATE TABLE TB_RO84 (A INT)` + `INSERT INTO TB_RO84 VALUES (1)`. `ro = ReadOnlyJdbc.wrap(conn)`. 목록 `a`.
  1. **래퍼 거절(전 방언)** — 9-6 의 `check` 거절 목록 열다섯 + K1·K6 글을 `ro.createStatement().execute(sql)` 로. `SQLFeatureNotSupportedException` 이 아니면 `a` 에 「거절 안 됨: <몇째>」(SQL 글은 안 싣는다)
  2. **전제(방언별, raw)** — 실행 뒤 효과를 조회. 효과가 없으면 `a` 에 「전제 거짓: <이름>」
     - MSSQL: `stackedDeny` — `SELECT 1 DENY SELECT ON TB_RO84 TO public` 뒤 `SELECT COUNT(*) FROM sys.database_permissions WHERE major_id = OBJECT_ID('TB_RO84') AND state = 'D'` ≥ 1 · `digitInto` — `SELECT X = 1INTO TB_RO84B` 뒤 `SELECT OBJECT_ID('TB_RO84B')` 가 null 아님 · `tableHint` — `SELECT A FROM TB_RO84 WITH (TABLOCKX, HOLDLOCK)` 가 오류 없이 한 행
     - MARIA(MariaDB·MySQL): `execComment` — `/*! REPLACE TB_RO84 */ SELECT 2` 뒤 `SELECT COUNT(*) FROM TB_RO84` = 2
     - POSTGRES: `nestedComment` — `/* /* */ SELECT */ COMMENT ON TABLE TB_RO84 IS 'ro84'` 뒤 `SELECT obj_description('tb_ro84'::regclass)` = `ro84` · `commentShare` — `SELECT A FROM TB_RO84 FOR/**/SHARE` 가 오류 없이 한 행
     - ORACLE: 전제 없음
  3. **오류문(전 방언)** — 손 매퍼 셋을 `SqlProbe.run(…, ro, dialect().meta, null)`: `SELECT A FROM TB_RO84 WHERE A = 'ZZLIT9' AND AND A = 1` · `SELECT A FROM TB_RO84 WHERE A = 'ZZLIT9'` · `SELECT NO_SUCH_COL_84 FROM TB_RO84`. ERROR 행의 `fileMessage` 에 `ZZLIT9`·`AND AND` 가 있으면 `a` · 첫째가 ERROR 가 아니면 `a` · 셋째 `fileMessage` 에 `NO_SUCH_COL_84`(대소문자 무시)가 없으면 `a`
  끝: `CorpusFiles.none("db-" + goldenKey() + " 읽기 전용 우회", a, <잰 수>)`
- 검증: 이 행에서는 `bash scripts/verify.sh`(컴파일·빠른 시험) 초록까지. 실물은 V-63 `--db`
- 의존: 9-6 · 6-39
- 사다리(V-63 에서): 「전제 거짓」 이 뜨면 그 줄을 단언에서 빼고 이름·방언을 이력 「드러난 것」 과 compare ㉝ 에 적는다(그 DB 에선 결함이 성립 안 함 — 래퍼 거절 단언은 남긴다). 표 만들기가 권한으로 실패하는 판은 그 판 클래스 `skipped()` 에 `readOnlyBypasses` 를 넣고 이력에 적는다

### 0-91 — 본문 null·목록 null·charset 400
- 대상: `web/StrictJson.java`(새 파일) · `web/App.java:150`·`jsonErrors`
- 변경:
  ```java
  /** 0-91(설계 69) — 본문 JSON 이 null 이거나 요청 레코드의 목록 성분에 null 원소가 있으면 400. 나머지는 JavalinJackson 그대로 */
  final class StrictJson implements io.javalin.json.JsonMapper {
      private final io.javalin.json.JavalinJackson inner;

      StrictJson(io.javalin.json.JavalinJackson inner) { this.inner = inner; }

      @Override public String toJsonString(Object obj, java.lang.reflect.Type type) { return inner.toJsonString(obj, type); }
      @Override public java.io.InputStream toJsonStream(Object obj, java.lang.reflect.Type type) { return inner.toJsonStream(obj, type); }
      @Override public void writeToOutputStream(java.util.stream.Stream<?> s, java.io.OutputStream out) { inner.writeToOutputStream(s, out); }
      @Override public <T> T fromJsonString(String json, java.lang.reflect.Type type) { return checked(inner.fromJsonString(json, type)); }
      @Override public <T> T fromJsonStream(java.io.InputStream json, java.lang.reflect.Type type) { return checked(inner.fromJsonStream(json, type)); }

      static <T> T checked(T body) {
          if (body == null) {
              throw new io.javalin.http.BadRequestResponse("본문이 비었다");
          }
          if (body.getClass().isRecord()) {
              for (java.lang.reflect.RecordComponent rc : body.getClass().getRecordComponents()) {
                  if (value(rc, body) instanceof java.util.List<?> l && hasNull(l)) {
                      throw new io.javalin.http.BadRequestResponse("목록 " + rc.getName() + " 안에 null 이 있다");
                  }
              }
          }
          return body;
      }
      private static Object value(RecordComponent rc, Object body)  // rc.getAccessor().invoke(body) — ReflectiveOperationException 이면 null(검사 건너뜀)
      private static boolean hasNull(List<?> l)                     // for-each 로 == null (List.contains(null) 은 불변 목록에서 던진다)
  }
  ```
  `App`: `cfg.jsonMapper(new StrictJson(new JavalinJackson().updateMapper(m -> m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))));`. `jsonErrors` 에 `app.exception(java.nio.charset.IllegalCharsetNameException.class, …)`·`app.exception(java.nio.charset.UnsupportedCharsetException.class, …)` → 400 `{message: "Content-Type 의 charset 을 모른다"}`
- 시험(먼저): `JsonErrorTest` 에 셋 + `post(path, body)` 도우미 — `nullBodyIs400`: `put("null")` 400(RED: 500), `POST /api/fs/out` `null` 400 · `nullListElementIs400`: `POST /api/analyze/probe-xlsx {"rows":[null]}` 400 · `POST /api/cleanup/apply {"path":"x","files":[null]}` 400(RED: 500·202) · `unknownCharsetIs400`: `Content-Type: application/json; charset=x-nope` 로 PUT 400(RED: 500). 기존 넷은 그대로
- 검증: `bash scripts/mvn.sh -q test -Dtest='JsonErrorTest,FsRoutesTest,AnalyzeRoutesTest,ProfileRoutesTest'` → `bash scripts/verify.sh`
- 의존: S0
- 사다리: 기존 화면·CLI 시험이 「목록 안 null」 400 으로 빨개지면 그 요청이 null 을 실제로 보내는 정상 호출인지 본다 — 정상이면 그 성분 이름을 `StrictJson` 의 면제 집합(`Set<String>`)에 넣고 이력 「정한 것」

### 0-92a — 스택 넘침 가둠: 검사·정리
- 대상: `core/check/CheckRunner.java:184-189` · `core/check/CleanupRunner.java:98·166`
- 변경:
  ```java
  public static RunResult runText(String text, String lang, RuleSet rules) {
      RuleSet.Run run = rules.run();
      Source s = Source.pasted(text, lang == null || lang.isBlank() ? guessLang(text) : lang);
      List<Finding> out;
      try {
          out = new ArrayList<>(run.apply(s));
      } catch (StackOverflowError e) { // 0-92a — 폴더 길(0-85f)과 같게: 작업 실패가 아니라 「못 읽음 1」
          return new RunResult(List.of(), 0, 1, 0, false, 0, 0);
      }
      out.addAll(run.finish());
      return new RunResult(out, 1, 0, parseErrors(out), false, run.suppressed(), lineCount(s.text()));
  }
  ```
  `CleanupRunner` 두 자리: `catch (RuntimeException | StackOverflowError e)` — 몸 그대로
- 시험(먼저): `CheckRunnerTest.pastedStackOverflowIsSkipped` — `CheckRunner.runText("ab".repeat(200_000) + "\n", "txt", rules(tmp))` 가 안 던지고 `skipped() == 1`·`findings()` 빔(RED: `StackOverflowError`). `CleanupRunnerTest.stackOverflowFileIsSkipped` — 폴더에 `Deep.java`(`class Deep { int x = ` + `"(".repeat(6000) + "1" + ")".repeat(6000)` + `; }`)와 정상 파일 하나 → 미리보기가 안 던지고 `Deep.java` 행의 건너뜀 사유가 `내부 오류 — StackOverflowError`, 정상 파일은 처리(RED: 던짐)
- 검증: `bash scripts/mvn.sh -q test -Dtest='CheckRunnerTest,CleanupRunnerTest'` → `bash scripts/verify.sh`
- 의존: S0
- 사다리: 붙여넣기 `txt` 가 규칙 글롭에 안 맞아 RED 가 안 나오면 `lang` 을 `java` 로, 입력을 위 `Deep.java` 글로 바꾸고 규칙은 `SelfCheckTest` 가 쓰는 기본 세트로. 깊은 괄호가 스택 대신 파싱 문제로 끝나면 깊이를 20000 으로

### 0-92b — 스택 넘침 가둠: 분석
- 대상: `core/analyze/JavaGraph.java:179-207·231·DAO 훑기` · `core/analyze/JpaIndex.java:192`
- 변경:
  ```java
  /** 0-92b — 파일 하나의 파싱 실패·스택 넘침은 미해결 parse 로 적고 null */
  private CompilationUnit parsed(JavaSource parser, Source s) {
      try {
          ParseResult<CompilationUnit> r = parser.parse(s.text());
          if (JavaSource.ok(r)) {
              return r.getResult().get();
          }
          // 기존 줄 계산 그대로 → note("parse", s.rel(), line, "")
      } catch (StackOverflowError e) {
          note("parse", s.rel(), 1, "");
      }
      return null;
  }

  /** 0-92b — 메서드 하나를 훑다 스택이 넘치면 미해결 parse 로 적고 거짓 */
  private boolean walked(Cls c, MethodDeclaration m, Acc acc) {
      try {
          walk(c, m, 0, acc, Map.of());
          return true;
      } catch (StackOverflowError e) {
          note("parse", c.file, line(m), "");
          return false;
      }
  }
  ```
  `run` 의 파일 반복은 `CompilationUnit cu = parsed(parser, s); if (cu == null) continue;` · 프로그램 반복은 `if (!walked(c, m, acc)) continue;` · DAO 훑기는 `if (walked(c, m, acc)) daoStmts.addAll(acc.stmts);`. `JpaIndex.run`: `parser.parse(s.text())` 를 `try { r = parser.parse(s.text()); } catch (StackOverflowError e) { continue; }` 로 감싼다(catch 몸 위 주석 — 「0-92b — 스택 넘친 파일은 JavaGraph 가 parse 로 센다」)
- 시험(먼저): `JavaGraphTest.deepFileIsSkippedNotFatal` — 인라인 소스 둘: 정상 `@Controller`(매핑 하나) + `Deep.java`(0-92a 와 같은 글). `JavaGraph.scan` 이 안 던지고 프로그램 1 · 미해결에 kind `parse`·파일 `Deep.java`(RED: `StackOverflowError`)
- 검증: `bash scripts/mvn.sh -q test -Dtest='JavaGraphTest,JpaIndexTest,AnalyzeRoutesTest'` → `bash scripts/verify.sh`
- 의존: 0-92a
- 사다리: `golden/analyze/*-graph.json` diff 는 0 이어야 한다 — 바뀌면 멈춘다

### V-63a — corpus 골든 확인
- `bash scripts/verify.sh --corpus`(약 7분, 표본 폴더·node). 기대 diff 0. 허용 변화 둘뿐 — ① 분석 baseline·골든에서 공백 든 문장 id 가 빠지고 그 자리 `statement` 미해결이 생긴 줄(6-40) ② 깊은 파일이 `parse` 미해결로 옮겨간 줄(0-92b). 허용 변화면 diff 줄 수를 이력에 적고 갱신. 그 밖 변화는 고치기 둘까지, 넘으면 `wip/V-63a`
- 벽시계를 이력에

### R-30 — 손 판정 · 처분 표
- `design/compare-2026-10-28.md` 에 절 ㉝ 「번들 84 — 가로 점검 2차 규칙 2·3·6」:
  1. 처분 표 — 결함 키 22 × (행 · 강제 지점 시험 · 닫힘/남음)
  2. V-63a 에서 옮겨간 미해결 줄을 전부 눈으로 — 빠진 글이 실제 SQL 글이었나(아니면 id 꼴 판정이 좁다 → 고침 ≤ 2)
  3. 저장소 예시·로컬 프로필 — `toolbox.bat selftest` 가 새 url 판정으로 프로필을 못 읽는다고 하지 않는지
  4. 끝에서 끝 — `run.bat --port 18840 --profile <예시>` 로 띄워 `curl "http://127.0.0.1:18840/api/fs/read?path=<프로필 yaml 절대 경로>"` 400 · 본문 `null` POST 400. 끝에 서버를 끈다
- 코드·레인 지문이 안 바뀐다(고침이 나오면 그 고침은 해당 행의 시험을 먼저 더해 따로 커밋)

### V-63 — 정적 분석 · `--db`
1. `bash scripts/mvn.sh -q -DskipTests verify`(PMD·SpotBugs·ErrorProne — 빠른 verify 가 안 보는 것). 새 짝이 뜨면 코드를 고친다(기준선 파일에 더하지 않는다). 고치기 둘까지
2. 여유 RAM 확인 — PowerShell `(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB`. 15 미만이면 7절 중단
3. `bash scripts/docker-up.sh` → `bash scripts/verify.sh --db`(컨테이너 아홉, 약 17분). `--corpus` 와 겹쳐 띄우지 않는다
4. db 골든 — `probe` 수(`egovProbe`·`ruoyiProbe`)가 바뀌면 PASS·ERROR 에서 `SKIP_REFUSED` 로 옮겨간 것만 허용(건수와 걸린 낱말을 이력에). 한 낱말이 표본 문장 다섯 넘게 거절하면 그 낱말을 `WRITE` 에서 빼고 이력 「정한 것(계획 밖)」
5. `readOnlyBypasses` 전제 여섯의 참·거짓을 compare ㉝ 에 방언별로
- `verify.sh --db` 와 `git push` 는 따로 낸다

### 마무리
CLAUDE.md 「마무리」 1~8 그대로 — 독립 리뷰 하나(`model: fable`, Review Focus: 규칙 2·3·6 을 어긴 자리 · 행의 강제 지점과 diff · 새 판정의 오거절) → `bash scripts/gate-probe.sh`(`url-password` 포함) → `verify.sh`(무거운 레인은 끝 줄이 알리는 것만 다시) → push → PR(본문에 행마다 시작 세 줄 표) → CI 폴링 → claude[bot] 처분 → **머지 위임** `gh pr merge N --merge --delete-branch` → 끝 보고.

## 5. 금지 사항

- `pure/` · `rules.yaml` · `config/pmd/exclude-from-failure.properties` · 기존 마이그레이션 V001~V014 를 고치지 않는다
- 새 의존성 0
- 기존 시험 단언을 지우거나 느슨하게 바꿔 통과시키지 않는다(이름 바꾸기는 6-39 의 `sqlTextIsCutForFiles` 하나만)
- `ReadOnlyJdbc` 의 「원문으로 잰다」 를 「주석·리터럴을 뺀 글로 잰다」 로 되돌리지 않는다 · 방언 인자를 더하지 않는다
- url 비밀 값을 응답에서 가리는 코드를 넣지 않는다(사용자 결정 3) · `ConnectionRegistry` 를 고치지 않는다
- 라우트 파일마다 null 검사를 넣지 않는다(0-91 은 `StrictJson` 한 자리) · `NullPointerException` 매퍼를 만들지 않는다
- 코드 정리 규칙(`core/check/*Fix`·`Cleanup.OPTIONAL`)을 건드리지 않는다(번들 85)
- `0-86`·`0-87`·`5-100` 을 같이 고치지 않는다 · `LocalFiles.check` 의 UNC 판정을 옮기지 않는다
- 시험·로그·문서에 실제 비밀번호·실제 사업 SQL 을 쓰지 않는다 — 가짜 값(`FAKE…`·`ZZLIT9`)만
- worktree·`finishing-a-development-branch` 를 쓰지 않는다

## 6. 최종 검증

- `bash scripts/verify.sh` 초록 · `bash scripts/mvn.sh -q -DskipTests verify` 초록 · `bash scripts/verify.sh --corpus` 초록 · `bash scripts/verify.sh --db` 초록(컨테이너 아홉에서 `readOnlyBypasses` 등급 A 0) · `bash scripts/gate-probe.sh` 열 전부 산다
- 강제 지점 시험 스물 안팎이 전부 있다(§4 표) — 각 행 이력에 RED → GREEN 한 줄
- 손 확인(R-30): 프로필 YAML `fs/read` 400 · 본문 `null` 400 · `selftest` 가 프로필을 읽는다
- 회귀: `golden/` diff 는 V-63a·V-63 의 허용 변화뿐 · 규칙 수 147 그대로 · `DbTest` 15
- CI 8 초록 · claude[bot] 「아니오」 처분 · PR 머지

## 7. 중단 조건

다음이면 임의로 풀지 말고 멈추고 보고한다(어느 스텝 · 무엇이 예상과 달랐나 · 가능한 선택지).
- 2절 구조 설명이 실제 코드와 다르다(줄 번호 어긋남은 아님 — 메서드·흐름이 다를 때)
- 검증이 2회 연속 실패하고 원인이 이 플랜 범위 밖
- 5절을 어기지 않고는 진행할 수 없다
- 스텝에 없는 파일을 3개 이상 고쳐야 한다
- `--db` 앞 여유 RAM 15GB 미만(다른 세션 컨테이너 — 사람이 조율한다)
- 실물 DB 에서 **래퍼 거절이 안 된** 줄이 나온다(전제 거짓과 다르다 — 규칙 6 이 뚫린 것이라 고침 둘 안에 안 닫히면 `wip/9-7` 로 빼지 말고 보고)
- 마이그레이션 `V015` 가 기존 데이터에서 실패한다

번들 모드의 「갈려도 안 멈춘다」 는 위 일곱 밖에서만 선다. 행당 계획 밖 결정 둘까지, 넘으면 `wip/<청크>`.

## 8. 불확실 항목

사용자만 정할 수 있는 것 — 없음(넷 다 답 받음).

실행해야만 알 수 있는 것 — 실행자가 해당 스텝에서 확인한다.
- **실물 DB 전제 여섯의 참·거짓**(Testcontainers) — `bash scripts/verify.sh --db`. 처분은 9-7 사다리
- **표본·db 골든 변화**(표본 폴더·컨테이너) — V-63a·V-63 의 허용 변화 규칙
- **PMD·SpotBugs 새 짝**(빌드) — `bash scripts/mvn.sh -q -DskipTests verify`. 0-89·6-39·0-90 의 새 정규식 상수가 후보다. 뜨면 정규식을 코드 스캔으로 바꾼다
- **깊은 괄호 6000 이 스택 넘침으로 끝나는가**(JVM 스택) — 0-92a·b 의 RED 실행. 사다리는 그 스텝에
- **Jackson YAML 이 `password: ''#tail` 을 오류로 읽는가 다른 트리로 읽는가** — 어느 쪽이든 `leaked` 는 참이다(`fragmentLeftBehindIsRefused` 가 잰다)

첫 스텝 전에 — `git status` 깨끗 · `git rev-parse --short HEAD` 가 `ceb6881` · `ls C:/workspace/toolbox-corpus` · `node -v` · `docker info` 가 되는지.
