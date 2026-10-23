# toolbox-server 반입 심의 자료

이 문서는 반입 zip 에 들어 있고, 문장마다 그 근거가 되는 코드·시험 이름을 단다. 시험 `ImportReviewDocTest` 가 이 문서와 코드를 맞대 어긋나면 빌드가 빨강이 된다(시험 이름·모듈 목록·라우트 이름).

요약 — **외부 DB 에는 읽기 문장만 보낸다 · 사용자 소스는 고치지 않는다(쓰기는 결과 폴더뿐) · 밖으로 나가는 것은 프로필 DB 와 사용자가 누른 svn 뿐이다 · 동봉한 것은 실행에 필요한 것뿐이다.**

## 1. 개요

- 무엇: 개발 PC 한 대에서 도는 로컬 도구. 브라우저 화면(서버) + 배치 CLI. 코드 검사·코드 정리·프로그램 분석·DB 메타 수집과 산출물(xlsx·SQL 글)·소스 생성.
- 어디서: `run.bat`(서버, `127.0.0.1` 의 한 포트) · `toolbox.bat <명령>`(CLI — 소켓 없이 같은 라우트를 안에서 부른다).
- 누가: 그 PC 의 개발자 한 명. 계정·권한 체계는 없다 — 다른 PC 에서 들어올 길이 없기 때문이다(2절).
- 언어·런타임: Java 17(동봉 jlink 런타임), Javalin(내장 Jetty), H2(파일 DB, 도구 자기 기록), JDBC 드라이버(프로필 DB 접속).

## 2. 통신

| 방향 | 무엇 | 근거 |
|---|---|---|
| 듣기 | `127.0.0.1` 한 포트만. `0.0.0.0`·외부 주소로 바인드하지 않는다 | `web.App`(바인드 주소 고정) · `scripts/rehearse.sh` ⑤ — netstat 으로 `127.0.0.1` 만 LISTENING 인지 판정 |
| 듣기 | DNS rebinding·교차 출처 쓰기 차단 — `Host` 가 `127.0.0.1:<포트>`·`localhost:<포트>` 가 아니면 403, 쓰기 메서드는 같은 출처만, 본문은 JSON·multipart 만 | `web.LocalOnly` |
| 나가기 | 프로필에 적힌 DB(JDBC) | `core.conn.ConnectionRegistry.open` 한 자리(4절) |
| 나가기 | 사용자가 「변경분만」·배포 목록을 눌렀을 때 `svn` 이 작업 사본의 저장소 서버에 묻는 것 | `core.vcs.Cli`(3절) |
| 나가기 | 그 밖 0 — HTTP 클라이언트·URLConnection·클라이언트 소켓·SSL 타입을 운영 코드가 쓰지 않는다, URL 을 열지 않는다 | `ArchitectureTest.noOutboundNetworkTypes` · `ArchitectureTest.noOpeningUrls` |

텔레메트리·업데이트 확인·CDN 은 없다. 화면은 jar 안의 정적 파일만 쓴다(외부 스크립트·글꼴 없음).

## 3. 외부 프로세스

프로세스를 띄우는 자리는 둘뿐이다 — `ArchitectureTest.processesOnlyFromVcsCliAndBrowserOpen`.

| 프로세스 | 언제 | 인자 | 근거 |
|---|---|---|---|
| `git` | 「변경분만」·배포 목록·작업 사본 판별 | 코드가 고정 목록으로 만든다. 사용자 값(ref)은 정규식 `GIT_REF` 로 거르고 `..` 거절 | `core.vcs.Cli`(실행 파일은 enum `GIT`·`SVN` 둘) · `core.vcs.WorkingCopy` · `ArchitectureTest.cliRunOnlyFromVcsPackage` |
| `svn` | 같음 | 리비전은 정규식 `SVN_REV`(숫자·HEAD·BASE·COMMITTED·PREV)만, `--non-interactive` | 같음 |
| `cmd /c start "" <주소>` | 서버 기동 직후 한 번 — 기본 브라우저로 런처 열기(`--no-browser` 로 끈다) | 주소는 `http://127.0.0.1:<포트>/` 고정 | `web.App.openBrowser` |

git·svn 은 묶음에 없다 — 현장 PC 에 있으면 쓰고, 없으면 그 두 기능만 꺼진다(`toolbox.bat selftest` 가 알린다). 표준 입력은 닫고(`GIT_TERMINAL_PROMPT=0`) 시간 초과면 끝낸다.

## 4. DB 접근 — 읽기 전용

- 외부 DB 연결은 `ConnectionRegistry.open` 한 자리에서만 열린다 — `ArchitectureTest.driverManagerOnlyFromRegistryAndH2`(`DriverManager.getConnection` 을 부르는 클래스는 그것과 H2 저장소 `core.db.Db` 뿐).
- 그 연결은 `ReadOnlyJdbc` 래퍼다. 문장 하나마다 첫 단어는 주석·인용을 뺀 글로, 여러 문장·쓰기 단어는 **원문으로** 잰다 — 인용·주석 규칙이 DB 마다 달라(PostgreSQL `$$`·`E'…'`, MariaDB `#`·백슬래시) 뺀 글만 보면 `;` 뒤 문장을 리터럴로 잘못 읽기 때문이다. 그래서 리터럴·주석 안의 `;`·쓰기 단어도 거절한다(엄격한 쪽 — 도구가 만드는 SQL 에는 없다).

| 통과 | 거절 |
|---|---|
| 첫 단어 `SELECT`·`WITH` 인 문장 하나(끝 `;` 하나는 허용) | 그 밖 첫 단어 전부(`INSERT`·`UPDATE`·`DELETE`·`MERGE`·`CREATE`·`DROP`·`ALTER`·`TRUNCATE`·`GRANT`·`CALL`…) |
| `DatabaseMetaData` 조회(표·컬럼·제약 목록) | 문장 안(리터럴·주석 포함)에 쓰기·잠금 단어(`INTO`·`FOR UPDATE`·`FOR SHARE`·`LOCK`·`WITH … DELETE` 등) |
| | `;` 가 끝 말고 하나라도 있는 문장 · `executeUpdate`·배치 실행 · `prepareCall`·`{call …}` · `setReadOnly(false)` · 갱신 가능 커서 · 결과 집합의 행 쓰기 · LOB·배열 만들기와 쓰기(`createBlob`·`setString`·`truncate` 등) · `unwrap` 으로 실물 꺼내기 |

- 시험: `ReadOnlyJdbcTest`(통과 8·거절 12·인용 우회 10·실물에 닿는 길·LOB) · `ConnectionRegistryTest.openedConnectionIsReadOnly` · 벤더 넷(Oracle·PostgreSQL·MariaDB·SQL Server) 메타 수집 시험이 이 래퍼를 지난다(db 레인).
- 연결에 `setReadOnly(true)` 도 건다. 다만 이것은 힌트다 — PostgreSQL·MariaDB 는 세션 기본, Oracle 은 현재 트랜잭션만, SQL Server 는 무시한다. 그래서 가드는 래퍼다.
- 임의 SQL 을 받아 실행하는 API 는 없다(`/api/sql/run`·`/api/sql/export` 는 지웠다). 도구가 보내는 SQL 은 메타 조회·행 수·코드표·DB 링크 목록·INSERT 생성용 참조값 조회 — 전부 SELECT 다. 데이터 품질 진단·반영 DDL·INSERT 는 SQL **글**만 만들고 실행하지 않는다.
- **한계** — 문장 단위 검사라 부작용이 있는 함수 호출(`SELECT nextval(…)`·`setval`·`pg_advisory_lock`·`dblink`·`LOAD_FILE` 등)은 못 막는다. 도구 자신은 그런 SQL 을 보내지 않고, 임의 SQL 을 받는 길도 없다. 그래도 접속 계정은 **조회 권한만 준 계정**을 쓴다(이중 방어 — 이것이 DB 쪽의 실제 보증이다).

## 5. 파일 쓰기

| 어디에 | 무엇 | 근거 |
|---|---|---|
| `data/` | H2 파일(도구 자기 기록, 6절) | `core.db.Db` |
| `logs/` | 실행 로그 — 경로·건수·사유만, 코드 본문·SQL 결과 없음 | 절대 규칙 3 |
| `out/<프로필>/<시각>/` | xlsx·결과 글·프로필 YAML 저장 앞 백업(비밀번호 칸을 비운 판) | `web.FsRoutes.outRoot` |
| `profiles/<사업>.yaml` | 코드 검사 화면에서 고른 규칙 켬·끔을 저장할 때(앞 판은 `out/` 에 백업) | `core.profile.ProfileStore.saveCodeCheck` |
| 결과 폴더 | 코드 정리·JSP 포매터 폴더 일괄·소스 생성이 만든 파일 — 원본 폴더와 같은 상대 경로로, 바뀐 파일만 | `POST /api/fs/result` · `POST /api/cleanup/apply` · `POST /api/generate` |

- 결과 폴더는 기본 `out/<프로필>/<시각>/<기능>/`, 사용자가 다른 폴더를 줄 수 있다. 원본 폴더 안·원본 자체·원본을 품는 폴더(이름으로·실제 경로로)는 거절한다. 비어 있지 않은 폴더는 표식 `결과_출처.txt` 가 있을 때만 쓴다 — 남의 프로젝트에 덮어 붙이는 사고를 막는다. 표식에는 기능·원본 경로·시각과 「원본은 바뀌지 않았다」 가 적힌다. 근거 `core.fs.LocalFiles.resultRoot`·`writeResult` · 시험 `LocalFilesTest`·`FsRoutesTest`·`CleanupRoutesTest`·`GenerateRoutesTest`.
- **사용자 소스는 읽기만 한다.** 원본을 되쓰는 API·CLI 는 없다. 코드 검사·프로그램 분석은 폴더를 읽어 `파일·줄·규칙`(분석은 표·URL·호출 관계)만 H2 에 저장한다.
- 폴더 입력은 절대 경로만, UNC·장치 경로(`\\서버\…`)는 거절한다(서버가 파일을 열면 SMB 로 밖에 나가기 때문). Windows·Program Files·서버 `data/` 는 다루지 않는다 — `core.fs.LocalFiles.check`.

## 6. 저장 데이터(H2)

- 표: 스냅샷(`snapshot`·`snap_schema`·`snap_table`·`snap_column`·`snap_constraint`·`snap_index` — DB 메타데이터만, 행 데이터 없음) · 검사 이력(`check_run`·`check_finding` — 파일·줄·규칙·등급) · 분석(`analyze_*` — 프로그램·표·CRUD·미해결 목록) · 사전(`dict_word`·`dict_domain`).
- 코드 본문은 저장하지 않는다. 예외 하나 — 프로그램 분석의 설명 100자(컨트롤러 메서드 javadoc·블록 주석 첫 문장, `analyze_program.descr`).
- 비밀번호는 저장하지 않는다(7절).

## 7. 비밀번호

- DB 비밀번호는 프로필 YAML(`profiles/<사업>.yaml`)의 접속 항목 `password` 에 평문으로 둔다 — 사용자 결정(매일 켜서 쓰므로 매번 입력하지 않는다).
- 화면·로그·H2·API 응답·오류 글에는 나가지 않는다 — `Profile.Connection.toString` 가림, `GET /api/profiles/{name}` 은 비밀번호를 뺀 사본, 접속 오류 글은 비밀번호를 `****` 로(시험 `ConnectionRegistryTest`). URL 안에 비밀번호를 넣는 꼴은 거절한다.
- `toolbox.bat selftest` 가 평문 password 가 든 프로필·접속을 경고한다.
- 권고: 프로필 폴더의 파일 권한을 그 PC 계정만 읽기로 둔다. 반입 zip 에 실제 프로필을 넣을 때(`package.sh --with`)는 그 zip 을 비밀 문서로 다룬다.

## 8. 동봉물

- `jre/` — Temurin 17 JDK 를 jlink 로 줄인 런타임(약 56MB, `javac` 없음). 모듈 `java.base,java.compiler,java.desktop,java.instrument,java.management,java.naming,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.localedata,jdk.net,jdk.security.jgss,jdk.unsupported,jdk.zipfs` — `scripts/package.sh` 의 `JLINK_MODULES` 한 줄이 원본이다.
- `app.jar` — 런타임 의존성 47 개가 든 실행 jar. 목록·판은 `docs/dependencies.txt`, 끝 줄에 jar 수.
- `drivers/` — 프로필 dialect 의 JDBC 드라이버만(없으면 Oracle·PostgreSQL·MariaDB·SQL Server 넷). 출처·판·라이선스는 `bundle/SOURCES.md`. Oracle JDBC 는 Oracle Free Use Terms and Conditions — 사내 반입 조건을 따로 확인한다.
- 소스·빌드 도구(Maven·`m2/` 653 jar)·javadoc·시험 픽스처는 넣지 않는다. `bundle/` 의 드라이버 레시피 pom 두 개는 출처 기록으로만 든다(실행하지 않는다). 고칠 것은 밖에서 고쳐 다시 반입한다.
- `MANIFEST.txt` — 버전·커밋·동봉 jar 수와 파일마다 sha256·크기. zip 자체의 sha256 은 zip 옆 `<zip>.sha256`.
- 2026-10-23 실측: zip 약 82MB · 파일 267.

## 9. 정적 검사

저장소의 CI(GitHub Actions)가 커밋마다 돈다. 반입 PC 에는 결과를 이 문서의 표로 낸다.

| 검사 | 무엇 | 자리 |
|---|---|---|
| CodeQL | Java·JavaScript·Actions 보안 분석 | 워크플로 `codeql` |
| gitleaks | 커밋에 비밀이 들었나 | 워크플로 `verify` 의 `secrets` 잡 |
| SpotBugs + findsecbugs | 버그·보안 패턴(빌드 실패 기준) | 워크플로 `verify`(`mvn verify`) |
| ArchUnit | 통신·프로세스·DB 연결 자리 고정(2·3·4절의 시험 이름) | `ArchitectureTest` — `coreDoesNotDependOnWebCliOrJavalin` · `webDoesNotDependOnCli` · `noOutboundNetworkTypes` · `noOpeningUrls` · `processesOnlyFromVcsCliAndBrowserOpen` · `cliRunOnlyFromVcsPackage` · `driverManagerOnlyFromRegistryAndH2` · `dialectPreparesOnlyThroughVendorMetaSource` |
| 오프라인 빌드 | 네트워크 없이 저장소 `m2/` 만으로 빌드되나 | 워크플로 `verify` 의 `offline` 잡 |
| 게이트 시험 | 위 게이트를 일부러 부수면 빨개지나 | 워크플로 `gate-probe` |

## 10. 검증 절차(반입 전)

1. 만드는 PC: `bash scripts/verify.sh --full`(시험 전부) → `bash scripts/package.sh [--with profiles/<사업>.yaml]`.
2. `bash scripts/rehearse.sh <zip>` — 저장소 밖에 풀어 PATH·JAVA_HOME 을 비우고 여섯 단계: ① 묶음 검사(빌드 재료·`javac`·실제 프로필이 끼면 빨강) ② PATH 에 java 없음 ③ `toolbox.bat version` ④ `toolbox.bat selftest`(동봉 런타임) ⑤ `run.bat` 기동 → `127.0.0.1` 만 LISTENING → 끔 ⑥ H2 가 푼 폴더에.
3. 반입 쪽: zip 의 sha256 을 `<zip>.sha256` 과 대조 → 풀어 `MANIFEST.txt` 의 sha256 과 대조 → `toolbox.bat selftest`.
