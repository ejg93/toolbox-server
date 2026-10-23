# 설계 31 — 번들 48: 반입 심사 하드닝 — 읽기 전용 DB · 결과 폴더 · 실행 전용 zip · 심의 자료 (Fable 설계 · Opus 실행, 2026-10-23)

## 맥락
폐쇄망 반입 심사에서 걸릴 지점을 2026-10-22 에 사용자가 추렸다 — SQL 을 제한 없이 실행하는 `SqlRunner`, 프로필 YAML 의 평문 비밀번호, 소스를 덮어쓰는 `cleanup --apply`, 외부 프로세스(svn·git), 소스 전체를 읽는 analyze, 동봉 jar 수. 2026-10-23 조사와 사용자 결정(메모리 `import-hardening-decisions`)으로 범위가 정해졌다.

하드닝 = 도구가 할 수 있는 일을 꼭 필요한 것까지만 남기고 나머지 길을 코드·시험·문서로 막는 것. 이 번들이 끝나면 심의 자료에 네 문장을 쓸 수 있다 — **외부 DB 는 읽기만 한다 · 사용자 소스는 고치지 않는다(쓰기는 결과 폴더뿐) · 밖으로 나가는 것은 프로필 DB 와 사용자가 누른 svn 뿐이다 · 동봉한 것은 실행에 필요한 것뿐이다(런타임 56MB·jar 48+드라이버)**.

실행자는 Opus. 이 플랜은 실행자가 그대로 따른다. 차례는 `S0`(설계 세션이 끝냄) → `9-1a` → `9-1b` → `9-2` → `9-3a` → `9-3b` → `9-3c` → `9-3d` → `9-4a` → `9-4b` → `9-5` → 마무리(번들 모드). 가지 `work/2026-10-23b`. 머지 위임 없음.

사용자 결정(뒤집지 않는다):
- **비밀번호는 YAML 평문 그대로**(절대 규칙 2 유지 — 매일 켜서 쓴다). 더하는 것은 `selftest` 경고와 심의 자료 문장뿐. 환경변수·콘솔 기본값·DPAPI 는 하지 않는다.
- **원본 소스를 안 고친다.** 코드 정리·JSP 포매터 폴더·소스 생성 셋 다 — 원본 폴더는 그대로 두고 **결과 폴더에 같은 상대 경로로 바뀐 파일만** 만든다. 기본 위치 `out/<프로필>/<시각>/<기능>/`, 화면·CLI 에서 다른 폴더를 줄 수 있다(원본 폴더 안·원본 자체·원본을 품는 폴더는 거부). `.gen` 옆 파일·백업·덮어쓰기 확인창·「쓰기 기본 꺼짐」 옵션은 없어진다.
- **반입 zip 은 실행 전용.** m2·Maven 배포본·소스·pom·mvnw·`build.bat`·`PLAN.md`·javadoc·픽스처를 뺀다. jre 는 jlink 축소 런타임. 드라이버는 `--with` 프로필의 dialect 것만(없으면 넷). 현장 수정은 밖에서 고쳐 다시 반입한다. 저장소의 `m2/` 오프라인 빌드(절대 규칙 5)는 개발용으로 그대로.
- SQL 실행 라우트 `/api/sql/run`·`/api/sql/export` 는 화면·CLI 가 부르지 않는다(4-11·1-36) — **지운다**. 읽기 전용은 옵션 없이 늘 건다(「쓰기 허용 옵션」 은 만들지 않는다 — DB 에 쓰는 기능이 0 이다).

---

## 1. 목표와 범위
- 목표: 반입 심사관이 코드·시험·문서로 확인할 수 있게 네 가지를 못 박는다 — 읽기 전용 DB 연결, 결과 폴더 쓰기, 실행 전용 묶음, 심의 자료 한 장.
- In scope: `core/conn/ReadOnlyJdbc`(연결·문장 래퍼) + `ConnectionRegistry.open` · `SqlRoutes` 삭제 · `ArchitectureTest` 규칙 하나 · `selftest` ⑬ · `LocalFiles.writeResult/resultRoot`(`write`·`create` 삭제) · `/api/fs/result`(`/api/fs/write` 삭제) · `CleanupRunner.apply`·`CleanupRoutes`·CLI `cleanup --write` · `Generator.run`·`GenerateRoutes`·CLI `generate` · 화면 셋(jsp_formatter·code_cleanup·spring_source_generator) + 런처 카드 글 · `package.sh`·`package-check.sh`·`rehearse.sh`·`bundle-fetch.sh`·`hooks-test.sh` · `build.bat` 삭제 · `docs/import-review.md` + `ImportReviewDocTest` · README·PLAN 11장·CLAUDE.md 절대 규칙 6·7.
- Out of scope: 비밀번호 저장 방식 변경 · 외부 프로세스 코드 변경(이미 `ArchitectureTest.processesOnlyFromVcsCliAndBrowserOpen`·`cliRunOnlyFromVcsPackage` 가 묶는다 — 문서에 적기만) · analyze 의 읽기 범위 · SBOM(CycloneDX — 빌드 플러그인이 새로 들어가 m2 확인이 필요, 새 행 후보) · 순수본 · H2 스키마 · 코드 정리 B 규칙.

## 2. 현재 구조 요약
실측은 전부 2026-10-23.

**DB 연결**
- 외부 DB 연결을 여는 자리는 하나 — `core/conn/ConnectionRegistry.open(id)`(`:81-101`, `DriverManager.getConnection(url, props)`). H2 자기 저장소는 `core/db/Db.java:37·67` 이 따로 연다. 운영 코드에 `unwrap`·`isWrapperFor` 호출 0.
- 외부 DB 에 문장을 보내는 자리: `SqlRunner.run`(`core/sqlrun/SqlRunner.java:63 ps.execute()`) · `core/dialect/*MetaSource`(`executeQuery` 만) · `core/meta/RowCounter:67`(`SELECT COUNT(*)`) · `ConnectionRegistry.test`(`SELECT 1`/`SELECT 1 FROM DUAL`) · `DatabaseMetaData`(드라이버 내부). 그 밖 `executeUpdate`·`executeBatch` 는 전부 H2 저장소(`AnalyzeStore`·`CheckStore`·`DictStore`·`SnapshotStore`·`Migrator`).
- `SqlRunner.run` 을 부르는 곳 셋 — `CodeAndLink:126`(`codeSql` — `SELECT DISTINCT …`), `DeliverableRoutes:426`(`dbLinkSql` — `SELECT …`), `InsertRoutes:148`(`SELECT DISTINCT … ORDER BY`) — 전부 도구가 만든 SELECT. `SqlRoutes`(`/api/sql/run`·`/api/sql/export`)는 `App.java:170` 에서 등록되지만 `tools/*.js`·`cli/*` 어디서도 안 부른다(`ToolsFolderTest:206` 이 화면에 `/api/sql/run` 이 없음을 단언). `MetaRoutesTest:201-204` 만 쓴다.
- `QualitySql.render` 는 SQL **글**만 만든다(실행 없음). 벤더 메타 SQL 에 `INSERT|UPDATE|DELETE|MERGE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|CALL|EXEC|EXECUTE|INTO|LOCK` 단어가 **단어 경계로** 나오는 것 0(`UPDATE_RULE`·`O.CREATED` 는 `\b` 경계가 아니다). 세미콜론 든 벤더 SQL 0.
- `setReadOnly(true)` 는 드라이버마다 뜻이 다르다 — PostgreSQL·MariaDB 는 세션 기본 읽기 전용, Oracle 은 **현재 트랜잭션만**(자동 커밋이면 다음 문장부터 무효), SQL Server 는 무시, H2 는 무시. **그래서 힌트일 뿐 가드가 못 된다** — 문장 래퍼가 가드다.
- 시험은 `ConnectionRegistryTest`·`CodeAndLinkTest`·`*MetaSourceTest`(db 태그, 컨테이너 넷)·`VendorFallbackTest`·`CliFixture` 가 `ConnectionRegistry.open` 으로 연다 → 래퍼를 끼우면 벤더 넷의 메타 수집이 래퍼를 지나며 저절로 재진다.

**사용자 폴더 쓰기**
- `core/fs/LocalFiles` — `check(raw)`(`:95`, 절대 경로만·UNC 거절·금지 루트 = data/·Windows·Program Files) · `write(path, root, text, enc, le, backupRoot)`(`:305-334`, 있는 파일 되쓰기 + 첫 원본 백업) · `create(path, root, text, enc, le, overwrite)`(`:343-376`, 새 파일·링크 검사·409) · `checkEncodable`·`encode`(UTF-8-BOM·UTF-8·MS949, CRLF·LF).
- `write` 를 부르는 곳: `web/FsRoutes` `/api/fs/write`(`:55-61`, jsp_formatter 폴더 탭) · `core/check/CleanupRunner.apply:182`. `create` 를 부르는 곳: `core/gen/Generator.run:104·107`(있으면 `<이름>.gen` 옆 파일 `sidecar`, 없으면 `created`). 그 밖 사용자 폴더에 쓰는 코드 없음(xlsx·결과 글은 `out/`).
- `FsRoutes.outRoot(active, stamp)` = `<profile.output.dir|out>/<프로필|default>/<stamp>` 절대 · `backupRoot` = 그 아래 `backup`. `backupRoot` 는 `CleanupRoutes:109` 와 **`ProfileRoutes:70`(프로필 YAML 저장 전 백업 — 그대로 둔다)** 가 쓴다. `FsRoutes.stamp(given)`.
- `CleanupRunner.apply(root, rules, wanted, lineEnding, files, ctx, refs, exclude, backupRoot, reportDir)` → `Applied(rows[Written{rel, ok, reason, backup}], written, failed, stale, report)` · 결과 글 `정리_결과.txt`(`REPORT`). `CleanupRoutes.ApplyRequest(path, rules, files, stamp)`(`:34`), 응답에 `backup`·`report`·`stamp`. CLI `CodeCommands.Cleanup`(`:85-150`) `--apply`·`--changed-only`·`--rules`. 화면 `code_cleanup.html:37` `#apply`(`.btn-red` 「체크한 파일 덮어쓰기(백업 후)」) · `code_cleanup_ext.js:179-199 apply()`(confirm → `/api/cleanup/apply`).
- jsp_formatter 폴더 탭: `jsp_formatter.html:161-169`(`#dir`·`#dirPreview`·`#dirApply` `.btn-red`·`#dirStop`·`#dirHint`) · `jsp_formatter_ext.js:424-452 dirApply()`(confirm → 파일마다 `/api/fs/write {path, root, text, encoding, lineEnding, stamp}` → `res.stamp`·`res.backupRoot`·`res.backup`) · `:98` `'덮어씀'` · `:209` 백업 메타 · `:217` 「덮어썼다 — 원본은 백업에 있다」. `DIR.rows[].rel` 은 `/api/fs/list` 가 준 상대 경로.
- 생성기: `GenerateRoutes` `POST /api/generate`(`:63-150`) — `outDir = pick(req.outDir, profile.generator.outDir)`(`:69`), 없으면 400, `files.exists(outDir).dir()` 아니면 400(`:95-98`), `Generator.run(set, tables, base, logical, tableGens, types, out, files, encoding, lineEnding, jc)`(`:147`), 결과 `created`·`sidecar`. CLI `DocCommands.Generate`(`:193-232`) `--out`. 화면 `spring_source_generator.html:41` `#out`(placeholder `C:\proj\gen`) · `_ext.js:60-62`(프로필 `generator.outDir` 기본값) · `:190-196 show()`(`'새 파일 n · 옆에 .gen m'`) · `:199-208 bindRow`(미리보기는 `outDir + rel` 을 `/api/fs/read`). 프로필 `Profile.Project.root`(프로젝트 루트)·`Profile.Generator.outDir`(`example.yaml:158` 「생성 파일을 쓸 폴더」).
- 런처 카드 글: `index.html:51`(jsp) · `:71`(cleanup — 「…백업하고 덮어써 고친다」) · `:95`(generator — 「…있는 파일은 안 덮는다」).
- 시험: `LocalFilesTest`(`writeKeepsEncodingLineEndingAndBacksUpFirst`·`noWriteWhenBackupFails`·`createMakesNewFilesOnly`·`createRefusesLinkOutOfRoot`·`writeRefusesLinkOutOfRoot`) · `FsRoutesTest.listReadWriteRecent` · `CleanupRunnerTest.applyBacksUpAndKeepsEncoding`·`staleHashIsSkipped`·`outOfScopeIsRefused` · `CleanupRoutesTest.previewThenApply`·`staleRefused` · `GeneratorTest.createsThenSidecars` · `GenerateRoutesTest.generatesWithProfileDefaults`·`perTableOverrideFromProfile`(`out` 폴더의 파일을 본다) · `SmokeHtmlUnitTest.codeCleanupPreviewApply`(confirm 거절 → 바이트 그대로)·`jspFormatterFolderBatch`(「덮어쓸 대상 2개」·「덮어씀 2/2」)·`jspFormatterFolderStops`·`crudGeneratorRuns` · corpus `CleanupCompileCorpusTest`(`CleanupRunner.apply` 로 사본에 적용 뒤 javac)·`GeneratorCorpusTest.hrRoundTrip`.

**반입 묶음**
- `scripts/package.sh` — ① 깨끗한 트리·`bundle-fetch.sh --check`·`offline-build.sh --check` ② `m2/` 오프라인으로 `app.jar`(37MB) ③ 무대: `git ls-files -- src pom.xml mvnw mvnw.cmd .mvn config pure profiles mappings rules templates run.bat toolbox.bat build.bat README.md PLAN.md docs LICENSE bundle`(golden/corpus 제외) + `app.jar` + `jre/`(Temurin 17 **JDK 통째 304MB**) + `drivers/`(23MB, alt 포함) + `m2/`(**369MB·jar 653**) + `docs/javadoc`(21MB) + `docs/dependencies.txt`(`dependency:list runtime`) + `fixtures/` + 빈 `data/logs/out/rules` + `--with` ④ `MANIFEST.txt` ⑤ `package-check.sh` ⑥ `jre/bin/jar.exe` 로 zip. `--full` 도장 필수.
- `scripts/package-check.sh` — 금지(.git·.claude·.github·corpus·target·golden/corpus·*.mv.db·settings.local.json·active-profile·`--with` 안 준 프로필) · `required=(app.jar run.bat toolbox.bat build.bat MANIFEST.txt jre/bin/javac.exe jre/bin/java.exe mvnw.cmd pom.xml LICENSE bundle/SOURCES.md README.md pure/toolbox.html profiles/example.yaml docs/dependencies.txt fixtures/README.txt)` 한 줄(`hooks-test.sh:312` 가 이 줄을 읽는다, `-ge 16`) · `m2/.mvn-home` 배포본 · `drivers/*.jar` · `docs/javadoc/*.jar` · 같은 벤더 jar 둘 금지 · `data/logs/out` 비어 있음.
- `scripts/rehearse.sh` — 일곱 단계(① 묶음 검사 ② PATH 에 java 없음 ③ version ④ selftest ⑤ **build.bat 오프라인 재빌드** ⑥ run.bat 기동·127.0.0.1 만 ⑦ H2 위치), 끝에 `[ "$step" -eq 7 ]`. `--break jre`.
- `scripts/bundle-fetch.sh` — ① JDK(Temurin 17 **JDK** zip, `jre/bin/javac.exe` 있어야 `--check` 통과 — jlink 의 `jre/jmods` 가 여기서 온다) ② 드라이버(레시피 pom 둘) ③ javadoc(`docs/javadoc`, `mvn dependency:copy`) · `bundle/MANIFEST` 지문(jre·drivers·javadoc). `hooks-test.sh:368-380` 이 `TOOLBOX_BUNDLE_ROOT` 가짜 폴더로 `--check` 를 잰다(javadoc jar 하나 포함).
- `build.bat` — 현장 빌드(`jre\bin\javac.exe` + `m2\`). `toolbox.bat`·`run.bat` 은 `jre\bin\java.exe` 만 본다(`toolbox.bat:13`).
- **jlink 실측**(이 PC, `jre/bin/jlink.exe --module-path jre/jmods`): 모듈 21개(아래 §3) · `--strip-debug --no-man-pages --no-header-files --compress=2` · **9.4초 · 56MB**(JDK 304MB) · `bin/` 에 `java.exe javaw.exe keytool.exe` 등 7개, `javac.exe` 없음 · 그 런타임으로 `java -jar target/app.jar selftest` → **통과 15 · 경고 0 · 실패 0**(H2·규칙·템플릿·사전·드라이버 넷 로드·git·svn). jdeps: 런타임 classpath jar 48개 + `target/classes` 의 JDK 모듈 합집합 = `java.base java.compiler java.desktop java.instrument java.management java.naming java.scripting java.sql java.xml jdk.net`; 드라이버 넷 = `java.management java.naming java.security.jgss java.sql jdk.net`(+ ojdbc `jdk.security.jgss`, pg `java.desktop`). fat jar 자체에 jdeps 를 걸면 안쪽 `module-info`(slf4j) 때문에 `java.base` 만 나온다 — 쓰지 않는다.
- 레인(`verify-fingerprint.sh:146-148`): `scripts` → tools, `README.md PLAN.md CLAUDE.md PROGRESS.md` → docs, `docs/` 는 **어느 레인에도 없다**. `src` → java.
- `ArchitectureTest` 에 이미 있는 것: `coreDoesNotDependOnWebCliOrJavalin`·`webDoesNotDependOnCli`·`noOutboundNetworkTypes`·`noOpeningUrls`·`processesOnlyFromVcsCliAndBrowserOpen`·`cliRunOnlyFromVcsPackage`. 프로세스를 띄우는 자리 셋 — `core.vcs.Cli`(git·svn, 인자는 `WorkingCopy` 가 `GIT_REF`·`SVN_REV` 정규식으로 거름) · `web.App.openBrowser`(`cmd /c start "" <127.0.0.1 주소>`). **외부 프로세스 행은 코드가 더 필요 없다 — 문서(9-5)에 적는다.**
- `SelfTest`(`cli/SelfTest.java`) ①~⑫, `check(ok, warnOnly, name, detail)`. `ProfileStore.list(Path)`·`load(Path)`(static). 시험 `SelfTestCliTest`.
- 함정: `CleanupRoutesTest.previewThenApply` 는 프로젝트 파일이 **바뀐 것**을 단언한다(`assertFalse(… contains("import java.util.Map;"))`) — 뒤집어야 한다. `SmokeHtmlUnitTest.codeCleanupPreviewApply` 는 confirm 핸들러 둘을 쓴다 — 확인창이 없어지면 그 단언 넷을 지운다. `GenerateRoutesTest` 는 응답 `outDir` 대신 미리 둔 `out` 변수로 파일을 찾는다 — 응답의 `outDir` 로 바꾼다. `CleanupCompileCorpusTest:56` 은 사본에 **덮어쓴 뒤** javac 를 돈다 — 결과 폴더 파일을 사본 위에 복사(overlay)한 뒤 javac 로 바꿔야 등급 A 판정이 같다.

## 3. 설계 결정

**9-1 읽기 전용 연결** [고정]
- `core/conn/ReadOnlyJdbc` — `java.lang.reflect.Proxy` 로 `Connection` 을 감싼다. `createStatement(*)` → `Statement` 래퍼 · `prepareStatement(sql, *)` → `check(sql)` 뒤 `PreparedStatement` 래퍼 · `prepareCall(*)` → 거절 · `setReadOnly(false)` → 거절 · 나머지 위임(`getMetaData`·`setAutoCommit`·`commit`·`close`·`unwrap`…). Statement 래퍼: `execute(String…)`·`executeQuery(String)`·`addBatch(String)` → `check(sql)` 뒤 위임 · `executeUpdate*`·`executeLargeUpdate*`·`executeBatch`·`executeLargeBatch` → 거절 · `getConnection()` → 래퍼 · PreparedStatement 의 `execute()`·`executeQuery()` 는 위임(prepare 때 검사했다), `executeUpdate()` 거절. 거절은 `SQLFeatureNotSupportedException("읽기 전용 연결 — <무엇>은 하지 않는다")`. **메시지에 SQL 본문을 넣지 않는다**(절대 규칙 3) — 첫 단어까지만.
- `ReadOnlyJdbc.check(sql)`(패키지 공개 static, 시험이 표로 잰다): ① 문자열 리터럴 `'…'`·따옴표 식별자 `"…"`·백틱·`[…]`·주석(`--…줄끝`·`/*…*/`)을 지운 글로 본다 ② 끝의 공백·`;` 을 떼고도 `;` 이 남으면 거절(「문장 하나만」) ③ 첫 단어(대소문자 무시)가 `SELECT`·`WITH` 가 아니면 거절 ④ 지운 글에 `\b(INSERT|UPDATE|DELETE|MERGE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|CALL|EXEC|EXECUTE|INTO|LOCK)\b` 또는 `\bFOR\s+UPDATE\b` 가 있으면 거절(쓰는 CTE `WITH … INSERT`·`SELECT … INTO`·잠금). `UPDATE_RULE`·`CREATED` 같은 식별자는 `\b` 가 안 걸린다(실측 — 벤더 SQL 충돌 0).
- `ConnectionRegistry.open` — `raw = DriverManager.getConnection(…)` → `try { raw.setReadOnly(true); } catch (SQLException ignored) {}`(힌트 — javadoc 에 드라이버별 뜻을 적는다) → `return ReadOnlyJdbc.wrap(raw)`. 이 한 자리가 외부 DB 연결의 전부다.
- `SqlRoutes.java` 삭제 + `App.java:170` 등록 삭제. `SqlRunner` 는 남긴다(세 호출자) — `ps.execute()` 가 false 를 돌려주는 분기는 래퍼 때문에 못 닿지만 그대로 둔다(건드릴 이유 없음).
- `ArchitectureTest.driverManagerOnlyFromRegistryAndH2` — `DriverManager.getConnection` 을 부르는 클래스는 `core.conn.ConnectionRegistry`·`core.db.Db` 뿐. 버린 대안: `setReadOnly` 만(Oracle·MSSQL 에서 무효) · 프로필 「쓰기 허용」 옵션(DB 에 쓰는 기능이 0 이라 켤 일이 없다).

**9-2 selftest ⑬ 프로필 비밀번호** [고정]
- `ProfileStore.list(Path.of("profiles"))` 의 이름마다 `ProfileStore.load(file)`(못 읽으면 그 프로필은 건너뛰고 detail 에 「<이름> 못 읽음」) → `connections()` 중 `password()` 가 비어 있지 않은 접속 id 목록. 하나라도 있으면 `check(false, true, "프로필 비밀번호", "<프로필>: 접속 <id>·<id> 에 평문 password — 파일 권한을 이 PC 계정만 읽기로(docs/import-review.md 7절)")`, 없으면 `check(true, …, "평문 password 없음")`. 경고라 끝 코드는 0.

**9-3 결과 폴더** [고정]
- `LocalFiles.resultRoot(String given, Path defaultRoot, Path root, String feature, String stamp) → Path` — `given` 이 있으면 `check(given)`, 없으면 `defaultRoot`(절대). 거절(400): `root` 가 있고 `under(out, root) || out.equals(root) || under(root, out)` 「결과 폴더는 원본 폴더 밖이어야 하고 원본을 품어도 안 된다」 · 있는데 폴더가 아님 · **폴더가 있고 비어 있지 않은데 표식 파일 `결과_출처.txt` 가 없음** 「비어 있지 않은 폴더 — 결과 폴더는 빈 폴더이거나 이 도구가 만든 폴더」(남의 프로젝트에 덮어 붙이는 사고를 막는다). 통과하면 폴더를 만들고 표식이 없으면 쓴다 — UTF-8, 다섯 줄: `toolbox-server 결과 폴더` / `기능 <feature>` / `원본 <root 또는 ->` / `시각 <stamp>` / `이 폴더의 파일은 원본 폴더의 같은 상대 경로 파일을 규칙대로 고친 것이다. 원본은 바뀌지 않았다. 반영은 사람이 비교해 옮긴다.`. 있는 표식은 안 덮는다(한 폴더에 여러 번 써도 첫 출처).
- `LocalFiles.writeResult(Path outRoot, String rel, String text, String encoding, String lineEnding) → Path` — `rel` 은 `/` 구분 상대 경로, 정규화 뒤 `outRoot` 안이어야 한다(`..`·절대 거절), `encode` 로 바이트, 부모 폴더 만들고 `Files.write`(덮어쓰기 허용 — 우리 폴더다). 링크 검사: `outRoot.toRealPath()` 와 `root.toRealPath()` 관계를 `resultRoot` 에서 한 번 더 잰다.
- `LocalFiles.write`·`create` 삭제(부르는 곳이 없어진다). `checkEncodable`·`encode`·`check`·`list`·`read`·`exists`·`recent` 그대로.
- `/api/fs/write` 삭제 → `POST /api/fs/result {root, rel, text, encoding, lineEnding, stamp?, outDir?}` → `{path, outRoot, stamp}`. 기본 `outRoot = FsRoutes.outRoot(active, stamp).resolve("jsp")`. 일괄 쓰기는 첫 응답의 `stamp`·`outRoot` 를 다시 보낸다(`outDir` 에 `outRoot` 를 넣으면 같은 폴더 — 표식이 있어 통과).
- 기능별 하위 폴더 이름 [고정]: `jsp`·`cleanup`·`generate`. 사용자가 `outDir` 를 주면 그 폴더 자체가 결과 루트(하위 폴더 없음).
- `CleanupRunner.apply(root, rules, wanted, lineEnding, files, ctx, refs, exclude, Path outRoot, Path reportDir)` — 쓰기는 `files.writeResult(outRoot, ref.rel(), r.text(), t.encoding(), r.lineEnding())`. `Written(rel, ok, reason, String path)`(백업 → 쓴 경로), `Applied(rows, written, failed, stale, Path outRoot, Path report)`. 결과 글 머리에 `결과 폴더 <outRoot> — 원본은 바뀌지 않았다` 한 줄. hash·outHash·`inScope` 낡음 판정은 그대로(미리보기와 같은 입력에서만 쓴다는 뜻은 유효).
- `CleanupRoutes.ApplyRequest(path, rules, files, stamp, outDir)` — `base = files.check(req.path())`, `outRoot = files.resultRoot(req.outDir(), FsRoutes.outRoot(active, stamp).resolve("cleanup"), base, "cleanup", stamp)`, `reportDir = FsRoutes.outRoot(active, stamp)`(사용자 폴더면 `reportDir = outRoot` — 결과 글도 그 폴더에). 응답 `backup` → `outRoot`.
- CLI `cleanup`: `--apply` → `--write`(「바뀐 파일을 결과 폴더에 쓴다 — 원본은 그대로」), `--out <폴더>`(없으면 `out/<프로필>/<시각>/cleanup`). 출력 `썼다 n/m · 실패 · 낡음` + `결과 폴더 <outRoot>` + `report`. README 표·설명 같이(`ReadmeCommandsTest`).
- `Generator.run(set, tables, base, logicalNames, tableGens, types, Path outRoot, Path projectRoot /*null 허용*/, LocalFiles files, encoding, lineEnding, ctx)` — 경로 식 검사(`target.startsWith(out)`)는 그대로, 쓰기는 `files.writeResult(outRoot, rel, text, …)`. 상태: `projectRoot` 가 있고 `projectRoot/rel` 이 있으면 `exists`(프로젝트에 이미 있음 — 비교해 옮긴다), 아니면 `created`. `.gen`·`sidecar` 없어진다. `Result(files, warnings)` 그대로. 4-인자 보조 `run` 도 같은 꼴로.
- `GenerateRoutes`: `outDir = pick(req.outDir, g.outDir)`(null 허용) → `projectRoot = p.project()==null||root==null ? null : files.check(root)` → `outRoot = files.resultRoot(outDir, FsRoutes.outRoot(active, stamp).resolve("generate"), projectRoot, "generate", stamp)`. 「outDir 가 있어야 한다」·「출력 폴더가 없다」 400 둘 삭제. 응답 `outDir`(= outRoot)·`created`·`exists`(`sidecar` 대신)·`warnings`. CLI `generate --out` 설명 「결과 폴더(없으면 out/<프로필>/<시각>/generate). 프로젝트 폴더 안은 거절」, 출력 `결과 폴더 … — 파일 n · 프로젝트에 이미 있음 m · 경고 k`. `profiles/example.yaml:158` 주석 같은 뜻으로.
- 화면: 버튼 유형은 CLAUDE.md 「화면 스타일」 — **파일을 만드는 것은 `.dl`**. 확인창(`confirm`)은 지운다(원본을 안 건드리니 되돌릴 일이 없다). 각 화면에 결과 폴더 칸 하나(`<input id="…Out">`, placeholder `비우면 out/<프로필>/<시각>/<기능>`). 결과 메시지는 `썼다 n/m개` + 결과 폴더 경로.
- 버린 대안: 폴더 통째 복사(안 바뀐 파일까지 — 용량·시간, 무엇이 바뀌었는지 안 보임, 사용자가 「바뀐 파일만」 고름) · 쓰기 기본 꺼짐 프로필 옵션(원본을 안 고치니 필요 없다) · 비어 있지 않은 폴더 무조건 거절(같은 폴더에 두 번 못 쓴다 — 표식으로 푼다).

**9-4 실행 전용 zip** [고정]
- `package.sh` 무대: `git ls-files -- pure profiles mappings rules templates run.bat toolbox.bat README.md docs LICENSE bundle`(golden/corpus 제외는 그대로 두어도 무해) + `app.jar` + **`jre/` = jlink 이미지** + `drivers/`(아래) + `docs/dependencies.txt`(지금처럼 `dependency:list -DincludeScope=runtime` + 끝에 `jar 수: app.jar 안 <n> · drivers <k>` 한 줄) + 빈 `data/logs/out/rules` + `--with`. **뺀다**: `src pom.xml mvnw mvnw.cmd .mvn config build.bat PLAN.md m2 docs/javadoc fixtures`. 앞 검사 ①(깨끗한 트리·`bundle-fetch.sh --check`·`offline-build.sh --check`)과 ② `m2/` 오프라인 빌드는 그대로(만드는 PC 쪽 보증) + `jre/jmods` 있음 검사.
- jlink [고정]: 스크립트 맨 위 한 줄 `JLINK_MODULES=java.base,java.compiler,java.desktop,java.instrument,java.management,java.naming,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.xml,java.xml.crypto,jdk.charsets,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.localedata,jdk.net,jdk.security.jgss,jdk.unsupported,jdk.zipfs`(jdeps 합집합 + 손으로 더한 것: `jdk.charsets` MS949 · `jdk.localedata` ko 로캘 · `jdk.crypto.ec`·`jdk.crypto.cryptoki` DB TLS(MSSQL 기본 암호화) · `jdk.unsupported` `sun.misc.Unsafe`(Jetty·H2) · `java.security.sasl`·`java.xml.crypto`·`java.sql.rowset`·`jdk.zipfs` 작고 드라이버가 선택적으로 쓴다). 명령 `"$R/jre/bin/jlink.exe" --module-path "$R/jre/jmods" --add-modules "$JLINK_MODULES" --strip-debug --no-man-pages --no-header-files --compress=2 --output "$stage/jre"`. 이 한 줄을 `hooks-test`·`ImportReviewDocTest`·심의 문서가 읽는다 — 다른 자리에 목록을 또 적지 않는다.
- 드라이버 [고정]: `--with` 로 준 파일 가운데 `profiles/*.yaml` 에서 `^\s*dialect:\s*(\S+)` 를 모아 벤더 → jar 머리 매핑(`oracle|tibero` → `ojdbc*|tibero*` · `postgresql|postgres|pg` → `postgresql*` · `mariadb|mysql` → `mariadb*|mysql-connector*` · `mssql|sqlserver` → `mssql-jdbc*|sqljdbc*`)으로 `drivers/`(alt 제외) 에서 고른다. 하나도 못 고르면 빨강. `--with` 에 프로필이 없으면 `drivers/*.jar` 넷(alt 제외). `drivers/alt/` 는 더는 안 넣는다(레시피 `bundle/drivers-alt/pom.xml` 과 SOURCES.md 는 남긴다 — 현장 요청 때 받아 넣는 길).
- `package-check.sh` [고정]: `required=(app.jar run.bat toolbox.bat MANIFEST.txt jre/bin/java.exe jre/release LICENSE bundle/SOURCES.md README.md pure/toolbox.html profiles/example.yaml docs/dependencies.txt docs/import-review.md)` · 있으면 빨강 더함: `jre/bin/javac.exe m2 src pom.xml mvnw mvnw.cmd build.bat fixtures docs/javadoc PLAN.md`(실행 전용) · `jre/release` 의 `MODULES=` 에 `java.sql`·`jdk.charsets`·`jdk.localedata` 셋 다 있어야 함(맨몸 이미지 방지) · m2·javadoc·fixtures 요구 삭제 · drivers ≥1·같은 벤더 둘 금지·data/logs/out 비어 있음 그대로.
- `rehearse.sh` [고정]: ⑤ build.bat 단계 삭제 → 여섯 단계(`[ "$step" -eq 6 ]`), 머리 주석·끝 글 「여섯 단계」. `run_cmd` 의 PATH 설명에서 mvnw 문구만 지운다.
- `bundle-fetch.sh`: javadoc 단계 ③·지문·`--check`·끝 줄 제거(`jre/bin/javac.exe` 요구는 그대로 — jlink 에 JDK 의 `jmods` 가 필요하다). `bundle/SOURCES.md` javadoc 절 삭제 + JDK 절에 「반입 zip 의 `jre/` 는 이 JDK 의 jlink 이미지 — 모듈 목록은 `scripts/package.sh` `JLINK_MODULES`」. `bundle/MANIFEST` 는 `--manifest` 로 다시. `build.bat` `git rm`. `.gitignore` 의 `docs/javadoc/` 줄 삭제.
- `hooks-test.sh`: package-check 케이스 — 가짜 무대의 필수 파일은 지금처럼 `required=(…)` 줄에서 읽는다(`-ge 16` → `-ge 12`) + `jre/release` 에 `MODULES="java.base java.sql jdk.charsets jdk.localedata"` 한 줄 · 케이스 추가 「`jre/bin/javac.exe` 있으면 빨강」·「`m2/` 있으면 빨강」·「`jre/release` 에 `jdk.charsets` 없으면 빨강」 · javadoc 케이스(`:317`) 삭제 · bundle-fetch 가짜 폴더에서 javadoc jar 줄 삭제.
- 크기 목표(실측 기반): jre 56MB + app.jar 37MB + 드라이버 1~4개(5~23MB) + 나머지 ≈ **100~130MB**(지금 약 650MB).
- 버린 대안: zip 둘(실행용·빌드용 — 사용자가 「실행만」 으로 정함) · m2 축소(시험 도구만 빼기 — 같은 이유) · JRE 배포본 따로 받기(Temurin JRE zip — 지문·출처가 둘이 되고 모듈을 못 고른다; jlink 는 이미 받은 JDK 로 만든다).

**9-5 심의 문서** [고정]
- `docs/import-review.md`(파일명 영문, 본문 한국어, 존댓말 금지). 절: 1 개요(무엇·어디서 도나·누가 쓰나) · 2 통신(듣기 `127.0.0.1` 만 — `LocalOnly`·`rehearse` netstat 판정 / 나가는 것 — 프로필 DB(JDBC)·사용자가 누른 svn 뿐 — `ArchitectureTest.noOutboundNetworkTypes`·`noOpeningUrls`) · 3 프로세스(셋: git·svn — `core.vcs.Cli` + 인자 정규식 `GIT_REF`·`SVN_REV` / 브라우저 열기 `cmd /c start` 127.0.0.1 주소 — `processesOnlyFromVcsCliAndBrowserOpen`·`cliRunOnlyFromVcsPackage`) · 4 DB 접근(읽기 전용 래퍼 `ReadOnlyJdbc` — 통과·거절 표, `setReadOnly` 가 힌트인 이유, `driverManagerOnlyFromRegistryAndH2`, DB 에 쓰는 기능 0) · 5 파일 쓰기(표: 어디에 — `data/`(H2)·`logs/`·`out/`(xlsx·결과 글·프로필 백업)·결과 폴더(`/api/fs/result`·`/api/cleanup/apply`·`/api/generate`, `결과_출처.txt` 표식, 원본 밖만) / 원본 소스는 읽기만 — analyze·check 는 읽어서 `파일·줄·규칙` 만 저장) · 6 저장 데이터(H2 표 요약 — 코드 본문 0, 예외 `analyze_program.descr` 100자, 비밀번호 0) · 7 비밀번호(프로필 YAML 평문 — 사용자 결정, 화면·로그·H2·API 응답 차단 시험, `selftest` 경고, 파일 권한 권고) · 8 동봉물(jre jlink 모듈 목록 — `package.sh` 와 글자 그대로, 56MB · app.jar 안 의존성 48(목록 `docs/dependencies.txt`) · 드라이버·라이선스 — `bundle/SOURCES.md` · `MANIFEST.txt` sha256) · 9 정적 검사(CI 워크플로 이름대로 CodeQL·gitleaks(secrets)·SpotBugs+findsecbugs(verify)·ArchUnit·offline — 결과는 GitHub Actions / 반입 PC 에서는 이 문서의 표) · 10 검증 절차(`package.sh` → `rehearse.sh` 여섯 단계 → `MANIFEST.txt` 대조 → `toolbox.bat selftest`).
- `ImportReviewDocTest`(java 레인, `src/test/java/kr/ejg/toolbox/ImportReviewDocTest.java`): ① `ArchitectureTest` 의 `@Test` 메서드 이름 전부가 문서에 있다 ② `scripts/package.sh` 의 `JLINK_MODULES=` 목록 == 문서 8절의 모듈 목록(백틱 안 쉼표 목록) ③ 문서에 `/api/fs/result`·`/api/cleanup/apply`·`/api/generate`·`ReadOnlyJdbc`·`결과_출처.txt` 가 있다 ④ `src/main` 어디에도 `/api/fs/write`·`/api/sql/run`·`/api/sql/export` 글자가 없다. 문서가 코드와 어긋나면 빨강 — 이것이 문서의 강제 지점.
- 레인: `docs` 폴더를 `verify-fingerprint.sh` docs 레인에 더한다(존댓말 lint). 일관성 시험은 java 레인·CI 가 돈다.

## 4. 실행 스텝

**S0 — 설계 파일 + PLAN + CLAUDE.md**(설계 세션이 끝냈다 — 실행자는 확인만)
- `design/31-bundle48.md`(이 파일) · PLAN §1 「반입 묶음에 넣을 것」·`:41`·`:435` 문장 · §11 목록 · §15 번들 48 행 · §16 행 열한 개 · 16장 끝 체크리스트 첫 줄 · CLAUDE.md 절대 규칙 6·7 + 구역표 `bundle/` 행 + 빌드·실행 표 세 줄 · PROGRESS 상태 한 줄.
- 검증: `bash scripts/verify.sh`(docs 레인 doc-lint) 초록.

**9-1a — `ReadOnlyJdbc` + 시험**
- 대상: 새 `core/conn/ReadOnlyJdbc.java` · 새 `src/test/java/kr/ejg/toolbox/core/conn/ReadOnlyJdbcTest.java`.
- 변경: §3 9-1 그대로. 공개 면: `public static Connection wrap(Connection real)` · `static void check(String sql) throws SQLException`. Proxy 인터페이스는 실물이 `PreparedStatement` 면 `PreparedStatement.class`, 아니면 `Statement.class`(`CallableStatement` 는 만들지 않는다). `InvocationTargetException` 은 원인을 벗겨 던진다.
- 시험(H2 메모리 `jdbc:h2:mem:ro;DB_CLOSE_DELAY=-1` 을 `DriverManager` 로 열어 `wrap`): 통과 — `SELECT 1` · `select 1` · `WITH t AS (SELECT 1 X) SELECT X FROM t` · `/* 주석 */ -- 줄\nSELECT 1` · `SELECT 1;`(끝 세미콜론) · `SELECT 'INSERT INTO x' AS S`(리터럴 안) · `SELECT "UPDATE" FROM T`(따옴표 식별자) · `SELECT UPDATE_RULE FROM T2`. 거절 — `INSERT INTO T VALUES(1)` · `UPDATE T SET A=1` · `DELETE FROM T` · `CREATE TABLE Z(A INT)` · `DROP TABLE T` · `MERGE INTO T …` · `CALL 1` · `SELECT * INTO Z FROM T` · `SELECT * FROM T FOR UPDATE` · `SELECT 1; SELECT 2` · `WITH x AS (SELECT 1) DELETE FROM T` · 빈 글. 그리고 `prepareCall` 거절 · `setReadOnly(false)` 거절 · prepared `SELECT` 의 `executeUpdate()` 거절 · `addBatch("INSERT …")` 거절 · `executeBatch` 거절 · 거절 뒤 표 내용 그대로(`SELECT COUNT(*)`) · `getMetaData().getTables(…)` 가 돈다 · `st.getConnection()` 이 래퍼(`Proxy.isProxyClass`) · 거절 메시지에 SQL 본문(`VALUES`)이 없다.
- 검증: `bash scripts/mvn.sh -q test -Dtest=ReadOnlyJdbcTest`.
- 의존: 없음.

**9-1b — 연결마다 래퍼 + SQL 라우트 삭제 + ArchUnit**
- 대상: `core/conn/ConnectionRegistry.java`(`open`) · `web/SqlRoutes.java` 삭제 · `web/App.java`(등록 줄·import) · `src/test/java/kr/ejg/toolbox/ArchitectureTest.java` · `src/test/java/kr/ejg/toolbox/web/MetaRoutesTest.java` · `src/test/java/kr/ejg/toolbox/core/conn/ConnectionRegistryTest.java`.
- 변경: `open` 끝을 §3 대로(`setReadOnly` 힌트 + `wrap`), javadoc 에 「드라이버별 뜻 — PG·MariaDB 세션, Oracle 현재 트랜잭션뿐, MSSQL·H2 무시. 가드는 ReadOnlyJdbc」. `MetaRoutesTest:200-205` 의 1-7 블록 → `POST /api/sql/run` 이 404 인 단언 하나. `ConnectionRegistryTest` 에 `openedConnectionIsReadOnly`: h2 접속으로 `open` → `createStatement().execute("INSERT …")` 가 `SQLException`, `SELECT 1` 통과. `ArchitectureTest.driverManagerOnlyFromRegistryAndH2`(`callMethodWhere(target(owner(equivalentTo(DriverManager.class))).and(target(name("getConnection"))))` 를 `ConnectionRegistry`·`Db` 밖 클래스가 부르면 빨강, `because("절대 규칙 6: 외부 DB 연결은 ConnectionRegistry.open 하나 — ReadOnlyJdbc 를 지난다")`). PLAN §8 에 `/sql/run`·`/sql/export` 글이 있으면 그 줄 끝에 「— 삭제(9-1b)」.
- 검증: `bash scripts/mvn.sh -q test -Dtest='ArchitectureTest,MetaRoutesTest,ConnectionRegistryTest,CodeAndLinkTest'` 초록 · `grep -rn "api/sql" src/main` 0.
- 의존: 9-1a.

**9-2 — selftest ⑬**
- 대상: `cli/SelfTest.java` · `src/test/java/kr/ejg/toolbox/cli/SelfTestCliTest.java`.
- 변경: §3 9-2. ⑪ 순수본 뒤, ⑫ DB 연결 앞. 시험: 임시 profiles 에 `password: x` 든 프로필 하나 + 없는 프로필 하나 → 출력에 `[경고] 프로필 비밀번호 — <이름>: 접속 <id>` 줄, 끝 코드 0 · 평문이 없으면 `[통과] 프로필 비밀번호`.
- 검증: `bash scripts/mvn.sh -q test -Dtest=SelfTestCliTest`.
- 의존: 없음.

**9-3a — `LocalFiles.resultRoot/writeResult` + `/api/fs/result`**
- 대상: `core/fs/LocalFiles.java` · `web/FsRoutes.java` · `LocalFilesTest` · `FsRoutesTest`.
- 변경: §3 9-3. `write`·`create` 와 그 시험 다섯을 지우고 새 시험: `writeResultMirrorsRelAndKeepsEncoding`(BOM·MS949·CRLF 그대로, 원본 바이트 그대로, `outRoot/a/bom.jsp` 생김) · `resultRootRefusesInsideEqualOrAround`(원본 안·원본 자체·원본의 부모 → 400) · `resultRootRefusesNonEmptyForeign`(파일 하나 든 남의 폴더 → 400, 빈 폴더 → 통과 + 표식 생김, 표식 있는 폴더 → 통과·표식 안 덮음) · `writeResultRefusesEscape`(`../x`·절대 → 400) · `resultRootRefusesLinkIntoRoot`(outRoot 가 원본 안을 가리키는 정션 — Windows 에서 못 만들면 `assumeTrue` 로 건너뜀, `createRefusesLinkOutOfRoot` 가 하던 꼴). `FsRoutesTest.listReadWriteRecent` → `listReadResultRecent`(`/api/fs/result` 두 번 — 둘째는 첫 응답의 `stamp`·`outRoot` 를 `outDir` 로 → 같은 폴더, 원본 그대로, `/api/fs/write` 404).
- 검증: `bash scripts/mvn.sh -q test -Dtest='LocalFilesTest,FsRoutesTest'`.
- 의존: 없음.

**9-3b — 코드 정리 → 결과 폴더**
- 대상: `core/check/CleanupRunner.java` · `web/CleanupRoutes.java` · `cli/CodeCommands.java`(`Cleanup`) · `README.md`(cleanup 줄) · `CleanupRunnerTest` · `CleanupRoutesTest` · `CleanupCompileCorpusTest`(overlay).
- 변경: §3 9-3. `CleanupRunnerTest.applyBacksUpAndKeepsEncoding` → `applyWritesResultAndKeepsOriginal`(원본 바이트 그대로 · `outRoot/<rel>` 인코딩·줄바꿈 · 표식 파일) · `staleHashIsSkipped`·`outOfScopeIsRefused` 그대로(인자만). `CleanupRoutesTest.previewThenApply`: 프로젝트 파일 **그대로** + `outRoot` 가 `stamp` 폴더 아래 `cleanup` + 결과 파일에 `import java.util.Map;` 없음 + `report` 는 `outRoot` 의 부모. `staleRefused` 그대로. 새 `userOutDir`(요청 `outDir` = 임시 빈 폴더 → 그 폴더에 결과·표식·`정리_결과.txt`; 프로젝트 안 폴더 → 400). `CleanupCompileCorpusTest`: 적용 뒤 `outRoot` 의 파일을 사본 위에 복사하고 javac.
- 검증: `bash scripts/mvn.sh -q test -Dtest='CleanupRunnerTest,CleanupRoutesTest,ReadmeCommandsTest'`.
- 의존: 9-3a.

**9-3c — 소스 생성 → 결과 폴더**
- 대상: `core/gen/Generator.java` · `web/GenerateRoutes.java` · `cli/DocCommands.java`(`Generate`) · `profiles/example.yaml`(`generator.outDir` 주석) · `README.md`(generate 줄) · `GeneratorTest` · `GenerateRoutesTest` · `GeneratorCorpusTest`(경로).
- 변경: §3 9-3. `GeneratorTest.createsThenSidecars` → `writesResultAndMarksExisting`(projectRoot 에 컨트롤러 하나 미리 둠 → 그 파일만 `exists`, 나머지 `created`, 결과 폴더에 10개 다 있음, projectRoot 그대로) · `refusesSetThatPointsOutside` 그대로. `GenerateRoutesTest`: 프로필에 `project.root` 를 두고 `outDir` 없이 호출 → 응답 `outDir` 아래 파일, `created` 10 · 두 번째 호출에 `outDir` = 프로젝트 안 → 400 · `perTableOverrideFromProfile` 은 응답 `outDir` 로 찾는다.
- 검증: `bash scripts/mvn.sh -q test -Dtest='GeneratorTest,GenerateRoutesTest,ReadmeCommandsTest'`.
- 의존: 9-3a.

**9-3d — 화면 셋 + 런처 카드**
- 대상: `tools/jsp_formatter.html`·`jsp_formatter_ext.js` · `tools/code_cleanup.html`·`code_cleanup_ext.js` · `tools/spring_source_generator.html`·`spring_source_generator_ext.js` · `tools/index.html` 카드 글 셋 · `SmokeHtmlUnitTest`(`codeCleanupPreviewApply`·`jspFormatterFolderBatch`·`jspFormatterFolderStops`·`crudGeneratorRuns`). 청크 규칙 초과 — 화면 셋은 같은 변경이라 한 커밋.
- 변경: jsp — `#dirApply` `.dl` 「체크한 파일을 결과 폴더에 쓰기」 · `#dirOut` 입력(라벨 「결과 폴더」, placeholder `비우면 out/<프로필>/<시각>/jsp`, `aria-label`) · `#dirHint` 「검사는 파일을 쓰지 않는다. 쓰기는 결과 폴더에 같은 상대 경로로 — 원본은 그대로」 · `#dirStop` title 「…이미 쓴 것은 그대로」 · `dirApply()` confirm 삭제, `/api/fs/result {root: DIR.root, rel: r.rel, text, encoding, lineEnding, stamp: DIR.stamp, outDir: DIR.outRoot || $id('dirOut').value}` → `DIR.stamp = res.stamp; DIR.outRoot = res.outRoot; r.written = res.path` · 글 `'덮어씀'`→`'썼다'` · `:209` 백업 메타 → 쓴 경로 · `:217` 「결과 폴더에 썼다 — 원본은 그대로」 · 요약 `썼다 n/m개` + `{ dir: DIR.root, out: DIR.outRoot }`(setDirMsg 의 `backup` 칸 → `out`, 라벨 「결과 폴더」). cleanup — `#apply` `.dl` 「체크한 파일을 결과 폴더에 쓰기」 · `#out` 입력 · `apply()` confirm 삭제, 요청에 `outDir`, 결과 `썼다 n/m개` + `{ dir: ROOT, out: o.outRoot }` · 머리 주석. generator — `#out` 라벨 「결과 폴더」, placeholder `비우면 out/<프로필>/<시각>/generate`, title 「결과 폴더 — 프로젝트 폴더 안은 안 된다」 · `show()` `'파일 n · 프로젝트에 이미 있음 m'` · 상태 열 `created`→`새 파일`, `exists`→`프로젝트에 있음`. 카드 글 — cleanup 「…미리보기한 뒤 고친 파일을 결과 폴더에 만든다(원본 그대로). 변경분만·중지·결과 글.」 · generator 「…한 번에 만든다. 결과 폴더에 만들고 프로젝트는 안 건드린다.」 · jsp 그대로. 스모크: `codeCleanupPreviewApply` — confirm 핸들러·단언 넷 삭제, 적용 뒤 `proj/Ms.java` 바이트 그대로(msOrig) + `out/t/<stamp>/cleanup/Ms.java` 가 MS949·LF·import 없음 + `res` 가 `썼다 3/3개` 로 시작 + `apply` 버튼 class 에 `dl` · `jspFormatterFolderBatch` — 「덮어쓸 대상」 글은 「쓸 대상」 으로(`countChecked` 라벨 같이), 적용 뒤 `a.jsp` 바이트 그대로 + `out/t/<stamp>/jsp/a.jsp`·`sub/b.jsp` 있음 + `dirMsg` `썼다 2/2개` · `jspFormatterFolderStops` 같은 꼴 · `crudGeneratorRuns` 응답 글 `파일 n`. `ToolsA11yTest`·`ToolsFolderTest` 가 새 입력의 라벨·버튼 유형을 본다.
- 검증: `bash scripts/mvn.sh -q test -Dtest='SmokeHtmlUnitTest,ToolsFolderTest,ToolsA11yTest,LauncherTest'`.
- 의존: 9-3b, 9-3c.

**9-4a — package.sh · package-check.sh · rehearse.sh**
- 대상: `scripts/package.sh` · `scripts/package-check.sh` · `scripts/rehearse.sh` · `scripts/hooks-test.sh`(package-check 케이스).
- 변경: §3 9-4 그대로. `JLINK_MODULES=` 한 줄은 `package.sh` 의 `set -uo pipefail` 바로 아래. 드라이버 고르기 함수 `pick_drivers`(`--with` 목록 → dialect 집합 → jar 목록 출력). `MANIFEST.txt` 머리에 `동봉 jar: app.jar 안 <n> · drivers <k>` 줄(`docs/dependencies.txt` 의 `:compile`·`:runtime` 줄 수). `package-check.sh` 의 `required=(…)` 는 한 줄 유지(hooks-test 가 읽는다).
- 검증: `bash scripts/hooks-test.sh` 초록 · 실제로 `bash scripts/package.sh` 는 `--full` 도장이 필요하다 — 이 청크에선 돌리지 않고(마무리 뒤 사람이), 대신 무대 만들기까지 손으로 한 번: `bash -x scripts/package.sh` 를 `--full` 검사 줄만 주석 처리한 **임시 사본**(`/tmp`)으로 돌려 `target/bundle/…` 에 `jre/bin/java.exe`·`jre/release`·`docs/dependencies.txt` 가 생기고 `jre/bin/javac.exe`·`m2`·`src` 가 없음을 `ls` 로 보고 `package-check.sh <무대>` 초록 · zip 크기를 이력에 적는다(목표 100~130MB). 사본은 커밋하지 않는다.
- 의존: 9-5(필수 파일 `docs/import-review.md` 가 있어야 무대 검사가 초록 — **9-5 를 먼저 쳐도 된다**; 순서를 바꾸면 이력에 적는다).

**9-4b — bundle-fetch · SOURCES · build.bat 삭제 · README · PLAN 11장**
- 대상: `scripts/bundle-fetch.sh` · `bundle/SOURCES.md` · `bundle/MANIFEST`(`--manifest` 재생성) · `scripts/hooks-test.sh`(bundle-fetch 케이스의 javadoc 줄) · `build.bat` 삭제 · `.gitignore` · `README.md`(「고쳐서 다시 빌드」 절 → 「실행 전용 묶음」 두 줄, 「만드는 쪽」 세 줄 글) · `CLAUDE.md` 빌드·실행 표(`bundle-fetch.sh` 설명에서 javadoc 제거 — S0 가 미리 고쳤으면 확인만).
- 변경: §3 9-4. README 「실행 전용 묶음 — 소스·빌드 재료는 없다. 고칠 것은 밖에서 고쳐 다시 반입한다(`docs/import-review.md` 10절)」.
- 검증: `bash scripts/hooks-test.sh` · `bash scripts/bundle-fetch.sh --check` 초록 · `git ls-files build.bat` 빈 출력.
- 의존: 9-4a.

**9-5 — 심의 문서 + 일관성 시험**
- 대상: 새 `docs/import-review.md` · 새 `src/test/java/kr/ejg/toolbox/ImportReviewDocTest.java` · `scripts/verify-fingerprint.sh`(docs 레인에 `docs`).
- 변경: §3 9-5. 문서 8절 모듈 목록은 `package.sh` 의 `JLINK_MODULES` 를 백틱 안에 그대로. 수치(56MB·48 jar·653→0)는 이 설계 §2 의 실측을 쓰고 9-4a 가 잰 zip 크기를 넣는다.
- 검증: `bash scripts/mvn.sh -q test -Dtest=ImportReviewDocTest` · `bash scripts/doc-lint.sh docs/import-review.md` 초록.
- 의존: 9-1b(래퍼 이름)·9-3a(라우트 이름)·9-4a(`JLINK_MODULES`). 9-4a 보다 먼저 치면 모듈 목록을 §3 의 것으로 적고, 9-4a 가 같은 글자를 쓴다.

**마무리** — CLAUDE.md 「마무리」 여덟. 바뀐 레인: java·tools·docs·corpus(`CleanupCompileCorpusTest`·`GeneratorCorpusTest`·화면 JS) → push 앞 `--corpus`. db 레인은 `ConnectionRegistry` 가 바뀌었으니 **`--db` 도**(벤더 넷의 메타 수집이 래퍼를 지나는 첫 실측 — 약 17분, 사용자 2026-10-10 원칙의 「DB 코드가 바뀐 번들」 에 해당). PR 본문에 청크마다 세 줄 표.

## 5. 금지 사항
- `pure/` · H2 마이그레이션 · 코드 검사 규칙·순수본 · 프로필 YAML 의 비밀번호 저장 방식 · `core.vcs` 프로세스 코드.
- 새 의존성 0(JNA·CycloneDX·jlink 플러그인 — jlink 는 JDK 의 실행 파일로).
- `SqlRunner` 삭제 금지(세 호출자) · `FsRoutes.backupRoot` 삭제 금지(`ProfileRoutes` 가 쓴다 — 이름 그대로).
- 시험을 지워서 초록 만들기 금지 — 바뀐 뜻대로 고친다(§2 함정 넷).
- `JLINK_MODULES` 목록을 두 자리에 적지 않는다(`package.sh` 한 줄 + 문서는 시험이 대조).
- 완료 행(8-8·8-10·8-11·4-1·7-3…)의 글을 고치지 않는다 — 바뀐 뜻은 새 행과 이력에.

## 6. 최종 검증
- `bash scripts/verify.sh` 초록 → `bash scripts/verify.sh --corpus --db`(push 와 따로) 초록 → `bash scripts/gate-probe.sh` 10 전부 산다 → `bash scripts/hooks-test.sh` 초록.
- 손 확인: `run.bat` → 코드 정리 화면에서 데모 폴더(`toolbox-demo/cleanup`) 미리보기 → 「결과 폴더에 쓰기」 → 원본 `git status` 깨끗 · `out/demo/<시각>/cleanup/` 에 바뀐 파일과 `결과_출처.txt`·`정리_결과.txt`.
- `grep -rn "api/fs/write\|api/sql/\|\.gen\"" src/main` 0.
- 회귀: `SmokeHtmlUnitTest` 전부 · `ToolsFolderTest.toolsHaveNoLocalTheme` · `LauncherTest`.
- 사람 몫(PR 뒤): `bash scripts/verify.sh --full` → `bash scripts/package.sh` → `bash scripts/rehearse.sh <zip>` 여섯 단계 → zip 크기 확인.

## 7. 중단 조건
- §2 의 줄 번호·시그니처가 실제와 다르다(머지된 PR #80 뒤 기준) → 멈추고 보고.
- `ReadOnlyJdbc` 를 끼운 뒤 벤더 메타 수집(db 레인) 가운데 **거절** 이 난다(벤더 SQL 에 금칙어) → 그 SQL 의 첫 단어·규칙을 이력에 적고 멈춘다(가드를 느슨하게 풀지 않는다).
- jlink 이미지로 `selftest` 가 빨강이거나 HtmlUnit 스모크가 모듈 누락(`ClassNotFoundException`·`UnsupportedCharsetException`)으로 깨진다 → 빠진 모듈 이름을 적고 멈춘다(목록은 사용자가 본 뒤 늘린다).
- 검증이 2회 연속 실패하고 원인이 플랜 범위 밖 · 금지 사항을 어기지 않고는 진행 불가 · 스텝에 없는 파일을 3개 이상 고쳐야 함.
- 보고 형식: 어느 스텝, 무엇이 예상과 달랐는지, 가능한 선택지.
