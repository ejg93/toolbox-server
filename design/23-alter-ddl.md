원본은 설계 세션 플랜 파일 — S0 에서 이 파일로 옮겼다(2026-10-11). 실행 세션은 PLAN 분할표 행과 이 파일의 4 절만 읽는다.

# 설계 23 — 번들 38: 스냅샷 차이 → 반영 DDL(ALTER 스크립트) (Fable, 2026-10-10)

이 플랜은 실행 담당 모델(Opus)이 그대로 따라 실행한다. 실행자는 탐색 과정을 모른다. 판단이 필요한 지점을 남기지 않았다. 번들 모드 규칙(CLAUDE.md 「번들 모드」) 그대로 — 갈려도 안 멈추고, 행당 「정한 것(계획 밖)」 둘까지.

사용자가 2026-10-10 정한 것: 「지금 당장 할 만한 건 2번(스냅샷 차이 → 반영 DDL)」 — 이미 있는 스냅샷 비교(1-6)에 붙인다. 생성만 하고 실행은 안 한다(마스킹·DDL 생성과 같은 원칙).

## Context

공공 SI 에서 「개발계에서 바꾼 표를 운영계 반영 요청서에 붙일 DDL」 은 어느 사업이든 손으로 쓴다. 이 저장소엔 재료가 다 있다 — 접속이 달라도 되는 스냅샷 비교(`SnapshotDiff`, 1-6), 방언 다섯의 CREATE 생성·타입 매핑(`DdlGen`, 7-6·7-7), 컨테이너 아홉 종 왕복 시험틀(V-18). 이 번들은 두 스냅샷(앞 A → 뒤 B)의 차이를 A 쪽 DB 에 적용할 `ALTER`·`CREATE`·`DROP` 스크립트로 내는 엔진·API·CLI·화면 카드와, 컨테이너 왕복 시험을 더한다. 데이터가 깨질 수 있는 문장(표·컬럼 삭제)은 주석으로 내고 `[확인]` 을 단다. 새 의존성 0.

### 1. 목표와 범위

- **목표**: DB 브라우저 「스냅샷 비교」 카드에서 A→B 차이를 고른 방언의 반영 DDL 로 미리보기·복사·파일 저장. CLI `toolbox.bat alter` 도 같은 것. 컨테이너 아홉 종에서 「생성한 ALTER 를 실행하면 스냅샷이 B 와 같아진다」 가 A 급 0.
- **In**: `S0` → `1-60a`(엔진 `AlterGen` + 골든 다섯) → `1-60b`(API `POST /api/meta/alter` + CLI `alter`) → `1-60c`(화면 카드 + 스모크) → `V-26`(컨테이너 왕복) → 마무리. 가지 `work/2026-10-11`(main `63f7058` 위). 머지 위임 없음 — PR 열고 멈춘다.
- **Out**: 화면에서 실행 · 컬럼 이름 바꿈 추정(삭제+추가로 내고 경고) · CHECK 제약(메타모델엔 있으나 `SnapshotDiff`·`DdlGen` 이 안 다룬다 — 그대로) · 시퀀스·뷰·트리거·권한 · `SnapshotDiff` 결과에 인덱스를 보이게 하는 것(새 행으로) · Sybase.

### 2. 현재 구조 요약(2026-10-10 실측)

#### 2.1 스냅샷·비교
- `core/meta/SnapshotDiff.java` — `compare(List<Schema> a, List<Schema> b, boolean ignoreSchema)` → `Result(addedTables, removedTables, changedTables[TableDiff(name, changes, addedColumns, removedColumns, changedColumns, addedConstraints, removedConstraints)])`. `Change(name, field, before, after)` 는 글. 컬럼 field: `nativeType·length·precision·scale·nullable·defaultValue·comment`, 표: `type·comment`. **제약은 이름 없는 글** `PK(A,B)`·`FK(A)->T(X)`·`UQ(A)`(:106-116). **인덱스·CHECK·FK 규칙·제약 이름은 안 본다.** 표 키 `index(...)`:121-134 — `ignoreSchema` 면 이름만(같은 이름이 여러 스키마면 `스키마.이름`), 아니면 늘 `스키마.이름`. private.
- 메타모델: `Schema(name, dbVersion, tables, sizeBytes)` · `Table(schema, name, type, comment, columns, pk, fks, uniques, indexes, rowCount, createdAt, lastDdlAt, checks)` · `Column(name, ordinal, nativeType, jdbcType, length, precision, scale, nullable, defaultValue, comment, domain)` · `PrimaryKey(name, columns)` · `ForeignKey(name, columns, refSchema, refTable, refColumns, deleteRule, updateRule)` · `FkRule{CASCADE, SET_NULL, SET_DEFAULT, RESTRICT, NO_ACTION}` · `UniqueKey(name, columns)` · `Index(name, unique, columns, sorts)` · `SortOrder{ASC, DESC, UNKNOWN}`.
- `SnapshotStore.get(id)` → `Optional<List<Schema>>`(`Schema.dbVersion` 채움) · `list()` → `Summary(id, profile, connId, takenAt, note, dbVersion, tableCount, filtered, scope, warningCount)` + `dialect()`(`TypeMapping.dialectOf(dbVersion)`, 모르면 null — H2 는 null).
- `web/MetaRoutes.java:43-52` `GET /api/meta/diff?a=&b=&ignoreSchema=` → `Result` JSON(+`empty`), 없으면 404. `MetaRoutesTest:104-110`.
- 화면 `tools/db_browser.html:108-120` 「스냅샷 비교」 카드 — `#diffA`·`#diffB`(목록은 :194-203 에서 채움, 기본 A=둘째 B=첫째) · `#ignoreSchema` · `#diffRun`(`.btn-p`) · `#diffMsg` · `#diffTables`·`#diffCols`(`TB.table`). `runDiff()`:389-411. 스냅샷 목록 `state.snaps`(id → Summary, `s.dialect` 있음 — logical_name 이 쓰는 꼴).
- CLI `cli/MetaCommands.java:115-147` `diff --from --to [--keep-schema]` — `batch.snapshotId(latest|id)` · `batch.call("GET", "/api/meta/diff?…")` · `batch.print(json, 요약글)`.
- 컨테이너: `web/DbCorpusBase.snapshotDiffTwoReleases`:544-566 — PG 만, `CREATE SCHEMA prev` 에 `egov-prev/script/{ddl,comment}/postgres` 적재 → `compare(prev, public)`. 골든 `db-postgres.json` `egovDiff {added 11, changed 182, removed 0}`.

#### 2.2 DDL 생성
- `core/gen/DdlGen.java` — `generate(List<Table>, Options(source, target, schema, includeFk, includeIndex, includeComments, prefix), TypeMapping)` → `Result(sql, warnings, tables)`. `targets()` = oracle·tibero·postgresql·mariadb·mssql(`GenModel.DIALECTS` 글). **렌더 조각은 전부 `private static final class Gen`(:152) 안** — `column(c)`:271(`name type [DEFAULT] [NOT NULL] [mariadb COMMENT]`) · `type(c)`:287(같은 방언이면 `nativeWithSize`, 아니면 자바 타입 축 `ddl-types.yaml`) · `defaultOf`:341(수·문자열·TRUE/FALSE·now 류만, 그 밖 경고) · `fk(t, fk)`:379 · `comments(t)`:395(oracle·tibero·pg `COMMENT ON`, mssql `sp_addextendedproperty`, mariadb 인라인) · `tbl`:424 · `cname`:429 · `id`:437(예약어·특수 글자면 방언 인용 ``·[ ]·") · `idList`:449 · 인덱스 `CREATE [UNIQUE] INDEX`:257-268 · UQ `ALTER TABLE … ADD CONSTRAINT … UNIQUE`:247-256(PK 와 같은 컬럼이면 생략) · FK 는 끝 `-- 외래 키` 블록, 참조 표 없으면 `-- [건너뜀]`. 문장 종결 `;\n`, GO·`/` 없음. 경고 글 목록 :183-441.
- `TypeMapping` — `load()` · `javaType(Column, dialect)`(public) · `dialectOf(String)`(public) · `norm`(pkg). `gen/ddl-types.yaml` 에 `limits`·`maxPrecision`·`now`·`nowTarget`·`reserved`.
- API `POST /api/deliverable/ddl`(`DeliverableRoutes:36-96`) — 요청 `{snapshotId, tables, target, schema, includeFk, includeIndex, includeComments, save}`, target 검증 400 「대상 방언: …」, 응답 `{sql, warnings, tables, source, path?}`, 저장은 `Outputs.dir(profile)`(`out/<프로필>/<yyyyMMdd-HHmmss>/`) 아래 `ddl-<target>.sql`. CLI `DocCommands:95-131` `ddl --snapshot --target [--schema --tables --no-fk --no-index --no-comments]`(늘 save, 경고 stderr).
- 화면 패턴 `tools/deliverable_sql.html:101-123` DDL 카드 — `#ddlTarget`(옵션 다섯 하드코딩) · `#ddlSchema` · 체크 셋 · `#ddlMake`(`.btn-p`) · `#ddlCopy`(`.btn-green`) · `#ddlSave`(`.dl` `[SQL] DDL`) · `#ddlRes`(`TB.result`) · `#ddlMsg` · `#ddlOut`(textarea) · `#ddlWarn`(ul). JS `ddl(save)`:1052-1071.
- 시험: `DdlGenTest` 골든 `golden/gen/ddl-{방언}.sql`(픽스처 = HR 두 표, 원본 oracle) · `DbCorpusBase.ddlRoundTrip`:776-855(@Order(6), H2 HR → `Options("h2", ddlTarget(), null, true, true, true, "G18_")` → `;\n` 으로 나눠 `--` 줄 빼고 실행 → `G18_` 표 다시 스냅샷 → 컬럼·PK·FK 수·코멘트 수 같음(A) · 타입 드리프트(B `db-<키>-ddl-type-drift.txt`) · `finally dropG18()`:870-887(FK 먼저 — mariadb `DROP FOREIGN KEY`, 나머지 `DROP CONSTRAINT` — 뒤 `DROP TABLE`)). `ddlTarget()`:762-769 는 방언 넷(tibero 없음).

#### 2.3 검증 레인
- `core/gen`·`core/meta`·`web/DbCorpusBase.java`·`src/test/java/kr/ejg/toolbox/core/meta` 는 **db 레인** → push 앞 `verify.sh --db`(약 17분). `tools`·`core/meta`·`core/gen` 는 corpus 레인도 → `--corpus`(7분). 둘 다 한 번에 `--db --corpus`.

### 3. 설계 결정

- **[고정] 엔진은 비교 결과가 아니라 스냅샷 둘을 받는다** — `core/gen/AlterGen.generate(List<Schema> from, List<Schema> to, Options, TypeMapping)`. `SnapshotDiff.Result` 는 제약이 이름 없는 글이고 인덱스·FK 규칙이 없어 ALTER 를 못 만든다. 표 키는 `SnapshotDiff.index(...)` 를 `public static` 으로 열어 같이 쓴다(비교 화면과 같은 짝짓기). 컬럼·제약·인덱스는 이름으로 짝짓고(제약은 이름이 없으면 구조 글로), 인덱스는 `이름·unique·컬럼·정렬` 이 하나라도 다르면 DROP+CREATE.
- **[고정] `DdlGen.Gen` 을 패키지에 연다** — `private static final class Gen` → `static final class Gen`, 쓰는 메서드(`column`·`type`·`defaultOf`·`fk`·`tbl`·`cname`·`id`·`idList`·인덱스·UQ·코멘트 조각)를 package-private 로. `AlterGen` 은 같은 패키지 `core/gen`. 버린 대안: 조각 복제 — 타입 매핑·인용·경고가 둘로 갈린다.
- **[고정] 방향·대상 방언** — 스크립트는 **A(앞)를 B(뒤)로** 만든다. 적용 대상 DB 는 A 를 뜬 DB 라 대상 방언 기본값 = A 의 `Summary.dialect()`(없으면 — H2 등 — 화면이 고르게 하고 API 는 400 「대상 방언을 고른다」). 원본 방언(`Options.source`) = B 의 dialect(새 표·바뀐 타입은 B 의 모습이라). 같으면 원본 타입 그대로(7-6 규칙).
- **[고정] 스키마** — `Options.schema` 가 있으면 전부 그것. 없으면 있는 표는 A 쪽 표의 스키마, 새 표는 B 쪽 표의 스키마(A 의 스키마 집합에 없으면 경고 「새 표 스키마 확인」). `ignoreSchema` 는 비교와 같은 뜻.
- **[고정] 위험 문장은 주석 + `[확인]`** — 표 삭제·컬럼 삭제는 `-- [확인] 데이터가 사라진다` 한 줄 뒤에 문장을 `-- ` 로 주석 처리(사람이 풀어서 쓴다). 그 밖은 살아 있는 문장 앞에 `-- [확인] …` 경고 — 타입 축소(길이·정밀도 줄거나 자바 타입 계열이 바뀜) 「값이 잘리거나 변환이 실패할 수 있다」 · NOT NULL 추가 「NULL 행이 있으면 실패」 · 기본값 없는 NOT NULL 컬럼 추가 「기존 행이 있으면 실패 — DEFAULT 를 넣는다」 · 컬럼 삭제+추가 짝이 같은 표에 있으면 「이름이 바뀐 것이면 RENAME 으로」. `[확인]` 수를 결과에 센다(`review`).
- **[고정] 문장 순서** — ① 머리 주석(`-- 반영 DDL — #a(방언, 시각) → #b · 대상 <방언> · 표 추가 n · 삭제 n · 변경 n · 확인 m · 경고 k`) ② 지운 FK `DROP`(B 에 없는 FK + 삭제되는 표를 가리키는 FK) ③ 지운 인덱스·UQ `DROP` ④ 새 표 `CREATE`(`DdlGen.generate` 에 그 표들만, `includeFk=false`, 코멘트는 이 단계에서 같이) ⑤ 바뀐 표 — 컬럼 추가 → 컬럼 변경 → 컬럼 삭제(주석) → PK 변경(DROP 뒤 ADD) ⑥ 새 인덱스·UQ ⑦ 새 FK(규칙 포함) ⑧ 바뀐 코멘트 ⑨ 표 삭제(주석). 방언 문법:

  | 일 | oracle·tibero | postgresql | mariadb | mssql |
  |---|---|---|---|---|
  | 컬럼 추가 | `ALTER TABLE t ADD (col def)` | `ALTER TABLE t ADD COLUMN col def` | `ALTER TABLE t ADD COLUMN col def`(COMMENT 인라인) | `ALTER TABLE t ADD col def` |
  | 타입·길이 | `MODIFY (c TYPE)` | `ALTER COLUMN c TYPE x` | 전체 다시 `MODIFY COLUMN c TYPE [NOT NULL] [DEFAULT] [COMMENT]` | 전체 다시 `ALTER COLUMN c TYPE NULL\|NOT NULL` |
  | NULL 허용 | `MODIFY (c NOT NULL)` / `MODIFY (c NULL)` | `ALTER COLUMN c SET NOT NULL` / `DROP NOT NULL` | (위 전체 다시) | (위 전체 다시) |
  | 기본값 | `MODIFY (c DEFAULT x)` / `DEFAULT NULL` | `ALTER COLUMN c SET DEFAULT x` / `DROP DEFAULT` | `ALTER COLUMN c SET DEFAULT x` / `DROP DEFAULT` | 이름을 모른다 — 앞 값 없으면 `ADD CONSTRAINT DF_<t>_<c> DEFAULT x FOR c`, 있으면 `-- [손] 기본값 제약 이름을 sp_helpconstraint 로 찾아 DROP 뒤 ADD` |
  | 컬럼 삭제 | `-- ALTER TABLE t DROP COLUMN c` | 같음 | 같음 | 같음 |
  | PK | `DROP CONSTRAINT name` / `ADD CONSTRAINT name PRIMARY KEY (…)` | 같음 | `DROP PRIMARY KEY` / `ADD PRIMARY KEY (…)` | `DROP CONSTRAINT name` / `ADD CONSTRAINT …` |
  | UQ | `DROP CONSTRAINT` / `ADD CONSTRAINT … UNIQUE` | 같음 | `DROP INDEX name` / `ADD CONSTRAINT … UNIQUE` | 같음 |
  | FK | `DROP CONSTRAINT` / `ADD CONSTRAINT … FOREIGN KEY … REFERENCES … [ON DELETE r] [ON UPDATE r]`(oracle 은 ON UPDATE 없음 → 경고) | 같음 | `DROP FOREIGN KEY name` / 같음 | 같음 |
  | 인덱스 | `DROP INDEX name` / `CREATE [UNIQUE] INDEX` | 같음 | `DROP INDEX name ON t` / 같음 | `DROP INDEX name ON t` / 같음 |
  | 코멘트 | `COMMENT ON TABLE\|COLUMN … IS '…'` | 같음 | 표 `ALTER TABLE t COMMENT = '…'`, 컬럼은 MODIFY 에 인라인 | 앞 값 없으면 `sp_addextendedproperty`, 있으면 `sp_updateextendedproperty`(`DdlGen.comments` 꼴) |

  제약·인덱스 이름이 없으면(`null`·DB 자동 이름 — `DdlGen.nameOr`) DROP 은 `-- [손] 이름을 모른다 — <구조 글>` 주석. PG 타입 변경에 계열이 바뀌면 경고 「변환이 안 되면 USING 을 붙인다」. oracle 계열은 `MODIFY (c TYPE DEFAULT x NOT NULL)` 처럼 바뀐 것만 한 문장에 묶어도 되지만 **항목마다 한 문장**(읽기 쉽고 골든이 단순). mariadb·mssql 은 어느 항목이 바뀌어도 B 컬럼 정의를 통째로 한 번.
- **[고정] 결과** — `record Options(String source, String target, String schema, boolean ignoreSchema, boolean includeIndex, boolean includeComments)` · `record Result(String sql, List<String> warnings, int statements, int review, int addedTables, int removedTables, int changedTables)`. 차이가 없으면 sql = 머리 주석 한 줄 + `-- 차이 없음`.
- **[고정] API·CLI·화면** — `POST /api/meta/alter {a, b, ignoreSchema=true, target, schema, includeIndex=true, includeComments=true, save=false}` → `{sql, warnings, statements, review, addedTables, removedTables, changedTables, source, target, path?}`(`MetaRoutes`, 비교 옆). target 없으면 A 의 dialect, 그래도 없으면 400 「대상 방언을 고른다」, 다섯 밖이면 400, 스냅샷 없으면 404. save 면 `Outputs.dir` 아래 `alter-<a>-<b>-<target>.sql`. CLI `alter --from --to [--target] [--schema] [--keep-schema] [--no-index] [--no-comments]`(`MetaCommands`, 늘 save, 경고 stderr, `diff` 와 같은 `batch.snapshotId`). 화면 — 비교 카드 아래 줄: `대상 <select #alterTarget>`(다섯, 기본 = A 의 dialect, 없으면 빈 값) · `스키마 <input #alterSchema>` · `☑ 인덱스 #alterIdx` · `☑ 코멘트 #alterCmt` · `#alterMake`(`.btn-p` 「반영 DDL 만들기」) · `#alterCopy`(`.btn-green`) · `#alterSave`(`.dl` `[SQL] 반영 DDL`) → `#alterRes`(`TB.result` — 만들기는 「반영 DDL — 문장 n · 확인 m · 경고 k」, 저장은 + 경로) · `#alterOut`(textarea, `.value`) · `#alterWarn`(ul). 비교를 안 눌러도 된다(A·B 만 있으면).
- **[고정] V-26 컨테이너 왕복** — `DbCorpusBase` @Order(7) `alterRoundTrip`: H2 HR 스냅샷 → `DdlGen`(prefix `G26_`, FK·인덱스·코멘트)로 컨테이너에 만들고 스냅샷 S1 → 자바로 B 를 만든다(S1 복사 + 고정 변경 묶음: 컬럼 추가(NULL 허용 + NOT NULL DEFAULT) · VARCHAR 길이 늘림 · 컬럼 NULL 허용 바꿈 · 기본값 추가 · 코멘트 바꿈 · UQ 하나 삭제 · 인덱스 하나 추가 · FK 하나 삭제 · 새 표 하나(PK + 기존 표로 FK)) → `AlterGen(S1 → B, target = 컨테이너 방언)` → 주석 아닌 문장 실행(`;\n` 나눔, `DdlGen` 왕복과 같은 꼴) → 스냅샷 S2 → **A**: 실행 실패 0 · `SnapshotDiff.compare(B, S2)` 의 컬럼 추가·삭제·PK·UQ·FK 차이 0 · 인덱스 이름 집합 같음 · 코멘트 바뀐 컬럼 값 같음 **B**: 타입 드리프트 목록(`db-<키>-alter-type-drift.txt`). `finally` `G26_` 표 삭제(`dropG18` 꼴, prefix 인자). 골든 키 `alterStatements`·`alterReview`·`alterWarnings`. tibero 는 컨테이너 없음(기존과 같다).
- 시간: 1-60a 골든 다섯 · V-26 은 `--db` 17분 한 번(마무리). 새 의존성 0.

### 4. 실행 스텝

청크 = 커밋 하나. `verify.sh` 와 `git commit` 은 따로. 커밋 제목 `feat|chore|docs: <번호> <무엇>`, 끝에 `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

#### S0. 가지 · 문서 행
- `git checkout main && git pull && git checkout -b work/2026-10-11`. 이 플랜 → `design/23-alter-ddl.md`(맨 위 「원본은 설계 세션 플랜 파일 — S0 에서 옮김」). `PLAN.md`: 번들 표에 번들 38 행 · 3.5 표 「DDL 생성·방언 변환」 행 뒤에 「반영 DDL(스냅샷 차이 → ALTER)」 행 · 8장 API 표 「메타」 줄에 `POST /meta/alter` · 분할표에 `1-60a`·`1-60b`·`1-60c`·`V-26`(각 §4 요약 + 선행 + 「대기」, 1-59b 뒤·V-25 뒤) · 새 행 `1-61`(설계 대기 — `SnapshotDiff` 결과·비교 화면에 인덱스 차이 보이기, 지금은 반영 DDL 에만 든다). `PROGRESS.md` 현재 상태(가지·진행중) + 이력 한 줄(#60 리뷰 처분 「해시 칸·꺼진 묶음 정규식은 의도」 포함).
- 검증: `bash scripts/verify.sh`(docs) → 커밋 `docs: S0 번들 38 설계 23 — 반영 DDL 행`.

#### 1-60a. `AlterGen` 엔진 · 골든 다섯
- 대상: `core/gen/AlterGen.java`(신설) · `core/gen/DdlGen.java`(`Gen` 패키지 공개) · `core/meta/SnapshotDiff.java`(`index` 공개) · `core/gen/AlterGenTest.java`(신설) + `golden/gen/alter-{oracle,tibero,postgresql,mariadb,mssql}.sql`.
- 변경 ①: `DdlGen` — `Gen` 과 §3 조각 메서드를 package-private 로(동작 불변, `DdlGenTest` 골든 그대로). `SnapshotDiff.index` → `public static Map<String, Table> index(List<Schema>, boolean ignoreSchema)`.
- 변경 ②: `AlterGen` 뼈대:

  ```java
  public final class AlterGen {
      public record Options(String source, String target, String schema, boolean ignoreSchema, boolean includeIndex, boolean includeComments) {}
      public record Result(String sql, List<String> warnings, int statements, int review, int addedTables, int removedTables, int changedTables) {}
      public static Result generate(List<Schema> from, List<Schema> to, Options o, TypeMapping types) {
          // 1 표 짝짓기 — SnapshotDiff.index(from, o.ignoreSchema()) / index(to, …) → added·removed·common
          // 2 common 표마다 Plan(addCols, modCols[(before, after)], dropCols, pk(before, after), uqAdd/uqDrop, fkAdd/fkDrop, idxAdd/idxDrop, tableComment)
          // 3 §3 순서 ①~⑨ 로 문장 — DdlGen.Gen g = new DdlGen.Gen(o.source(), o.target(), schemaFor(table), types, warnings) 로 조각
          // 4 위험 문장은 comment(stmt, "[확인] …") — review++ ; 문장은 ";\n" 종결, 주석 줄은 "-- "
      }
  }
  ```
  컬럼 변경 판정은 `SnapshotDiff` 와 같은 일곱 field(`nativeType·length·precision·scale·nullable·defaultValue·comment`) — 타입 넷이 하나라도 다르면 「타입」, `nullable`·`defaultValue`·`comment` 는 각각. 축소 판정: 길이·정밀도가 줄거나 `types.javaType(before, source)` 와 `javaType(after, source)` 가 다름.
- 변경 ③: `AlterGenTest` — 픽스처 = `SnapshotStoreTest.golden("meta/postgres-vendor.json")`(SnapshotDiffTest 가 쓰는 것, 원본 postgresql) 을 A 로, B 는 자바로 바꾼 것: 새 표(PK + A 표로 FK) · 표 하나 삭제 · 컬럼 추가 둘(NULL 허용 · NOT NULL DEFAULT) · 컬럼 삭제 · VARCHAR 길이 늘림 · 길이 줄임 · nullable 양쪽 · 기본값 추가·제거 · 컬럼 코멘트·표 코멘트 · PK 컬럼 바뀜 · UQ 추가·삭제 · FK 추가(ON DELETE CASCADE)·삭제 · 인덱스 추가·삭제. 방언 다섯 `GoldenFiles.assertText("gen/alter-<방언>.sql", r.sql())` + `warnings` 도 같은 파일 끝에(`-- 경고` 블록) · `review` 수 단언 · `ignoreSchema=false` 한 케이스(스키마 달라 전부 추가·삭제) · 차이 없음 케이스(`-- 차이 없음`). 처음은 `-Dgolden.update=true` 로 만들고 **눈으로 읽어** §3 표와 맞는지 본 뒤 커밋(골든이 처음이라 diff 가 없다 — 문장 하나씩 표와 대조한 결과를 이력에).
- 검증: `DdlGenTest`·`SnapshotDiffTest`·`AlterGenTest` 초록 → 빠른 검증 → 커밋 `feat: 1-60a 반영 DDL 엔진 AlterGen — 방언 다섯 골든`.
- 의존: S0.

#### 1-60b. API · CLI
- 대상: `web/MetaRoutes.java` · `cli/MetaCommands.java` · `web/MetaRoutesTest.java`(+ `cli/BatchCliTest` 에 케이스 하나).
- 변경: `POST /api/meta/alter`(§3 요청·응답, `record AlterRequest(Long a, Long b, Boolean ignoreSchema, String target, String schema, Boolean includeIndex, Boolean includeComments, Boolean save)`) — `snapshots.get(a)`·`get(b)` 404, `source` = B `Summary.dialect()`(목록에서 찾음, 없으면 ""), target 기본 A dialect, save 는 `Outputs.dir(active)` 아래 `alter-<a>-<b>-<target>.sql`(UTF-8). CLI `alter`(`MetaCommands`, `diff` 뒤): `--from --to --target --schema --keep-schema --no-index --no-comments`, `batch.call("POST", "/api/meta/alter", body)` → `path`·요약 한 줄 stdout, 경고 stderr. 시험 — `MetaRoutesTest`: H2 스냅샷 둘(두 번째는 `ALTER TABLE … ADD COLUMN` 뒤) → `target=postgresql` 200 `sql` 에 `ADD COLUMN` · `statements≥1` · target 없음(H2 라 dialect null) 400 · 모르는 target 400 · 없는 id 404 · `save` 파일 존재. `BatchCliTest` 에 `alter --from 1 --to 2 --target postgresql` 한 케이스(파일 생김).
- 검증: 그 둘 초록 → 빠른 검증 → 커밋 `feat: 1-60b 반영 DDL API POST /api/meta/alter · CLI alter`.
- 의존: 1-60a.

#### 1-60c. 화면 — 비교 카드에 반영 DDL
- 대상: `tools/db_browser.html` · `SmokeHtmlUnitTest`(+`ToolsFolderTest.RESULT_SITES` db_browser 하한 +2).
- 변경: 비교 카드 `#diffCols` 뒤에 §3 의 줄·textarea·경고 목록. JS — `alterTargets()` 가 `#diffA` change 때 `state.snaps[a].dialect` 로 `#alterTarget` 기본값(없으면 '') · `alter(save)` = deliverable 의 `ddl(save)` 꼴(`TB.result('alterRes', …)`, `#alterOut.value = r.sql`, `#alterWarn` li) · 복사 `TB.copy($('alterOut'))`. 시험 `dbBrowserAlterDdl` — 자기 App(임시 프로필, H2 메모리 접속 `jdbc:h2:mem:alt;DB_CLOSE_DELAY=-1`): 표 만들고 API 로 스냅샷 #1 → 컬럼 추가 → 스냅샷 #2 → 화면 열고 `#diffA`=1·`#diffB`=2 · `#alterTarget`=postgresql → `#alterMake` → `#alterOut` 에 `ADD COLUMN` · `#alterRes` `res-ok` 「반영 DDL — 문장 」 → `#alterSave` → `#alterRes` `.res-p` 가 `.sql` 로 끝남 · 복사 「복사됨」.
- 검증: 스모크·`ToolsFolderTest` 초록 → 빠른 검증 → 커밋 `feat: 1-60c DB 브라우저 비교 카드에 반영 DDL`.
- 의존: 1-60b.

#### V-26. 컨테이너 왕복
- 대상: `web/DbCorpusBase.java`(절 하나 + `dropPrefixed(prefix)` 로 `dropG18` 일반화) · 골든 `golden/corpus/db-<키>.json`(키 셋) · `db-<키>-alter-type-drift.txt` 아홉.
- 변경: §3 `alterRoundTrip`. B 를 만드는 도우미 `mutate(List<Schema> s1)` 는 record `with` 꼴로 복사(메타모델은 record — 새 `Table(...)` 생성). 실행 실패는 `db-<키>-alter-failed.txt`(A — 비어 있어야). 돌리기: `bash scripts/verify.sh --db`(17분, Docker·이미지 아홉) — 빨강이면 그 방언 문법을 `AlterGen` 에서 고친다(A 상한 10, 넘으면 B 로 내리고 새 행).
- 검증: `--db` 초록(아홉 전부) → 골든 diff 를 이력에(방언별 문장 수·확인 수·드리프트 수) → 빠른 검증 → 커밋 `test: V-26 반영 DDL 컨테이너 왕복 — 방언 아홉`.
- 의존: 1-60a. 실패 사다리: Oracle 의 `MODIFY (c NULL)` 이 이미 NULL 이면 ORA-01451 — B 를 만들 때 바뀌는 컬럼만 고른다 · mssql `ALTER COLUMN` 이 PK·인덱스 컬럼이면 실패 — 변경 묶음에서 PK·인덱스 밖 컬럼만 바꾼다 · MariaDB `DROP INDEX` 가 UQ 이름과 겹치면 UQ 삭제는 `DROP INDEX`(표대로).

#### 마무리(CLAUDE.md 「마무리」 그대로)
독립 리뷰 → `gate-probe.sh` → `bash scripts/verify.sh` 끝 줄의 무거운 레인(db·corpus 둘) → `bash scripts/verify.sh --db --corpus`(약 25분, 메모리 10GB 여유·Docker) → push → PR(본문 표) → CI 폴링 → 머지 위임 없음 → 끝 보고. PROGRESS 이력·분할표·번들 38 상태.

### 5. 금지 사항

- 화면·API 에서 생성한 DDL 을 **실행하는 길을 만들지 않는다**(생성만 — 마스킹·DDL 생성과 같다).
- `SnapshotDiff.compare` 의 결과 모양·골든 넷·corpus `egovDiff` 를 바꾸지 않는다(1-61 로).
- `DdlGen` 의 출력(골든 `ddl-*.sql`)·`ddl-types.yaml`·`type-mapping.yaml` 을 바꾸지 않는다(조각을 여는 리팩터만).
- `pure/` · CI · `scripts/verify*` · 새 의존성 · 메타모델 record 에 필드 추가.
- 골든을 「초록 되게」 갱신하지 않는다 — 1-60a 첫 골든은 §3 표와 문장마다 대조, V-26 실패는 엔진을 고친다.
- 위험 문장(표·컬럼 삭제)을 살아 있는 문장으로 내지 않는다.

### 6. 최종 검증

- `AlterGenTest` 골든 다섯 + 케이스 셋 · `MetaRoutesTest` 반영 DDL 다섯 · `BatchCliTest` alter · `dbBrowserAlterDdl` 스모크 · `verify.sh --db` 에서 `DbCorpusBase.alterRoundTrip` 아홉 A 0.
- 사람이 볼 것: 데모(`demo` 프로필, 데모 H2 에 스냅샷이 여럿)에서 비교 카드 → 대상 oracle → 「반영 DDL 만들기」 → 문장·`[확인]` 주석 읽기 → `[SQL] 반영 DDL` 저장 경로.

### 7. 중단 조건

- ① `DdlGen.Gen` 을 열 때 `DdlGenTest` 골든이 바뀐다(동작이 변했다).
- ② 1-60a 첫 골든에서 §3 표와 다른 문장이 방언 하나에 셋 넘게 나온다(설계 표가 틀렸다 — 보고).
- ③ V-26 에서 A 가 방언 하나에 10 넘는다, 또는 같은 문장이 방언 셋 이상에서 실패한다(엔진이 아니라 설계 문제).
- ④ `Summary.dialect()` 가 컨테이너 방언을 못 알아본다(target 기본값이 늘 비어 화면 흐름이 깨짐).
- ⑤ 검증 2회 연속 빨강이고 원인이 플랜 밖 · 스텝에 없는 파일 3개 이상.

### 8. 불확실 항목(첫 스텝 전에 확인)

- `DdlGen.Gen` 생성자 시그니처·경고 목록을 바깥에서 넘길 수 있는지(`List<String> warnings` 를 받나) — 아니면 `Gen` 에 package-private 생성자 하나 더.
- `SnapshotStoreTest.golden("meta/postgres-vendor.json")` 이 `List<Schema>` 를 주는지(SnapshotDiffTest 가 쓰는 꼴 그대로).
- H2 메모리 접속으로 스냅샷을 뜨는 스모크 꼴 — `MetaRoutesTest` 가 이미 H2 스냅샷을 만든다면 그 도우미를 쓴다.
- MSSQL `sp_updateextendedproperty` 가 컨테이너(2017·2022)에서 코멘트 바꾸기에 되는지 — V-26 이 잰다.
- MariaDB 11 에서 `ALTER TABLE … ALTER COLUMN c SET DEFAULT` 문법 — 된다(10.2+). MySQL 5.7 은 `ALTER COLUMN c SET DEFAULT` 됨, `DROP DEFAULT` 됨.
