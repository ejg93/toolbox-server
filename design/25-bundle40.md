# 설계 25 — 번들 40: 구조 점검(스냅샷 린트) + 관계 후보 PK끼리 표시 (Fable, 2026-10-14)

원본은 설계 세션 플랜 파일 — S0 에서 옮김(2026-10-14).


이 플랜은 실행 담당 모델(Opus)이 그대로 따라 실행한다. 실행자는 탐색 과정을 모른다. 판단이 필요한 지점을 남기지 않았다. 번들 모드 규칙(CLAUDE.md 「번들 모드」·「청크 규칙」·「마무리」)을 따른다.

사용자가 2026-10-14 정한 것(뒤집지 않는다):
- 구조 린트는 **새 화면이 아니라 「산출물 SQL」 화면의 91 칸**으로. 기준값(컬럼 수 상한·필수 컬럼·끈 규칙)은 **프로필 YAML** 에 두고 화면은 기준을 보여 주기만 한다(메모리 `filters-in-yaml-not-ui`).
- 번들 39 실물(데모 실측 2026-10-14)에서 드러난 「관계 후보 196 중 50 이 PK끼리(LOG_ID 류)」 는 **지우지 않고 근거로 표시**한다 — 전자정부에는 PK 를 공유하는 1:1 표(COMVNUSERMASTER ↔ COMTNEMPLYRINFO 류)가 실제로 있어 지우면 진짜를 잃는다.

## 보정(번들 39 실물 ↔ 뒤 행)

- 번들 39 는 12행 + 리뷰·CI·AI 리뷰 고침으로 PR #65 `2c1a431` 머지. 「정한 것(계획 밖)」 넷은 전부 이력에 있고 뒤 행을 바꾸지 않는다.
- 뒤 행 상태 그대로: 1-61(인덱스 차이 비교 화면) 설계 대기 · 보류 넷·조건부 셋·2-17 입력 대기.
- 비교 문서 `design/compare-2026-10-14.md` 에서 남은 「가치 상」 셋 중 이 번들은 **구조 린트(가-1·가-2 ⓑ~ⓔ)** 만. 억제 주석(나-1)·나머지 50 규칙 `fix`(나-4 뒷절반)은 **다음 번들 결정 요지**(끝 절) — 이 번들에서 하지 않는다.

---

### 1. 목표와 범위

- **목표**: 산출물 SQL 화면에서 스냅샷을 고르고 「구조 점검」 을 누르면 DB 에 다시 붙지 않고 아홉 규칙(FK 컬럼 인덱스 없음 · 관계 없는 표 · 인덱스 없는 표 · 컬럼 수 상한 · 필수 컬럼 없음 · 중복 관계 · 컬럼 하나뿐인 표 · 번호 붙은 반복 컬럼 · 기본값이 글자 NULL)으로 표·컬럼 지적 목록이 나오고 xlsx 로 저장된다. 기준은 프로필 `deliverable.lint`, CLI 는 `lint --snapshot`. 작성안내 「관계 후보」 의 PK끼리 쌍은 근거 「이름(PK끼리)」 로 한 줄만 남는다.
- **In**: `S0` → `2-28a` → `2-28b` → `2-29` → `8-15` → `2-30` → `2-31` → `V-28` → 마무리. 가지 `work/2026-10-14b`(main `2c1a431` 위에 새로). 머지 위임 없음(PR 열고 멈춤).
- **Out**: 억제 주석 · 나머지 50 규칙 `fix` · 그림 글 복사 · PK 없는 표(기존 `nopk` 버튼 그대로 — 린트에 중복으로 넣지 않는다) · 코멘트 없음(`Audit` 에 있다) · 린트 결과 H2 저장(저장 안 함 — `Audit` 과 같은 결) · 컨테이너(db 레인) 왕복 · 1-61 · `pure/`.

---

### 2. 현재 구조 요약(2026-10-14 실측, main `2c1a431`)

경로는 `src/main/java/kr/ejg/toolbox/` 기준.

#### 2.1 메타(린트 재료 — 전부 스냅샷 안에 있다)
- `core/meta/Table.java` — `Table(schema, name, type, comment, columns, PrimaryKey pk, fks, uniques, indexes, Long rowCount, createdAt, lastDdlAt, checks)`, compact 생성자가 목록 null → 빈 목록. `Table.of(schema, name, type, comment)` + `withColumns`·`withConstraints(pk, fks, uniques)`·`withIndexes`·`withStats(rowCount, createdAt, lastDdlAt)`. 뷰 판정은 다른 곳과 같이 `type.toUpperCase().contains("VIEW")`.
- `core/meta/Column.java` — `Column(name, ordinal, nativeType, jdbcType, Long length, precision, scale, boolean nullable, defaultValue, comment, domain)`.
- `core/meta/PrimaryKey(name, columns)` · `UniqueKey(name, columns)` · `Index(name, unique, columns, sorts)` · `ForeignKey(name, columns, refSchema, refTable, refColumns, deleteRule, updateRule)`(5인자 생성자 있음).
- 벤더마다 PK·유니크를 받치는 인덱스가 `indexes` 에 **같이 들어오기도 하고(Oracle·PG·H2) 아니기도** 하다 — `Definitions.d10`(:418-439) 이 이름으로 중복을 거른다. 그래서 「인덱스 있나」 는 **pk·uniques·indexes 셋을 다 봐야** 한다(§3 [고정]).
- H2 는 FK 제약마다 비유니크 인덱스 `CONSTRAINT_INDEX_n` 을 스스로 만들고 JDBC 메타가 돌려준다(실측) → H2 픽스처에서는 `fkNoIndex` 가 0 이다. `JdbcMetaSource.loadIndexes`(:159) 는 `indexes` 에서 **PK 이름만** 뺀다(유니크 인덱스는 `uniques` 와 `indexes` 양쪽에 든다). `RowCounter`(`SnapshotService:66`) 가 통계 없는 표의 행 수를 세므로 H2 빈 표는 `rowCount 0`.
- `core/deliverable/Definitions.sorted(List<Schema>)`(:97) — 스키마·이름 순 표 목록. `Definitions.piiKey`·`nz`·`blank` 는 package-private/private.

#### 2.2 산출물 라우트·화면
- `web/DeliverableRoutes.java` — `registerQuality(app, snapshots)`(:269) 에 `GET /api/quality/kinds`·`POST /api/quality/sql`·`POST /api/quality/nopk`(:288 — `snapshot(ctx, id, snapshots)` 로 스냅샷, 거름 없음). `register(app, snapshots, conns, active)`(:304) 안의 라우트들은 `filtered(ctx, id, snapshots, active)`(:244 — 프로필 `deliverable.filter` 적용, 404·400 을 알아서 쓴다) 를 쓴다. `POST /api/deliverable/ddl`(:53-100) 이 **동기 + save 꼴**의 본보기 — `Boolean.TRUE.equals(req.save())` 면 `Outputs.dir(active.get().orElse(null))` 아래 파일을 쓰고 `path` 를 응답에 넣는다. `record DdlRequest(...)` 처럼 요청 레코드는 같은 파일 안.
- `web/Outputs.java` — `text(name)`·`num(name)`(`ResultTable.Col`) · `dir(Profile)` · `xlsx(Profile p, String name, List<Col> cols, List<List<Object>> rows)`(:54 → `Path`, 프로필 `output.dir` 아래 날짜 폴더) · `xlsx(Path dir, name, LinkedHashMap<String,ResultTable> sheets)`(:44).
- `src/main/resources/tools/deliverable_sql.html`(1108줄, JS 안에 든 단일 파일) — 카드 순서 「…08 표준코드 · 09 연계 · **90 데이터 품질 진단**(:83-99, `#qKind #qTable #qColumn #qKeys #qMake #qNoPk #qMsg #qOut #qNoPkTbl #qNotes`) · DDL 생성·방언 변환(:100-121, `#ddlMake .btn-p`·`#ddlCopy .btn-green`·`#ddlSave .dl`·`#ddlRes`(TB.result)·`#ddlMsg`·`#ddlOut`) · SQL 가이드」. JS — `var state = { snapshots, schemas, codes, conns, tables }`(:681) · `msg(id, text, kind)`(:684, kind `ok`·`err`) · `fail(id)`(:685) · `option(sel, v, t)`(:686) · 스냅샷 고르기 `$('snap')`(값 = 스냅샷 id) · `loadSchemas()`(:725) · `noPk()`(:1044-1050, `TB.table($('qNoPkTbl'), [...], rows)` + `msg('qMsg', …, list.length ? 'err' : 'ok')`) · `ddl(save)`(:1052-1071 — save 면 `TB.result('ddlRes', 'run'|'ok'|'fail', { summary, path })`). 버튼 바인딩은 파일 끝 쪽 `$('qNoPk').addEventListener('click', noPk)` 근처(실행자가 `grep -n "qNoPk" deliverable_sql.html` 로 찾는다).
- 런처 `tools/index.html:78` 산출물 카드 설명 「데이터 산출물 01~11 을 … 수기 메모 포함.」.
- 버튼 유형(CLAUDE.md 「화면 스타일」): 실행 `.btn-p` · xlsx 만들기 `.dl` + `<span class="ico ico-xlsx" aria-hidden="true"></span>` · 복사 `.btn-green`. 화면 `<style>` 에 새 색·버튼 정의 금지(`ToolsFolderTest.toolsHaveNoLocalTheme`).

#### 2.3 프로필
- `core/profile/Profile.java` — `record Deliverable(author, org, templateDir, mapping, Scope filter, Map<String,List<String>> business)`(:76, compact 생성자가 business 를 순서 유지 복사 + 정규식 검증). 직접 `new Profile.Deliverable(` 호출은 src 에 0건(Jackson 만) — 컴포넌트를 더해도 호환 생성자 불필요(설계 24 §3 결정과 같음). `ProfileStore` 는 `FAIL_ON_UNKNOWN_PROPERTIES` — **새 키는 레코드 필드부터**.
- `profiles/example.yaml` `deliverable:` 블록(:91-110) — `author`·`org`·`templateDir`·`mapping`·`filter`·`business`(:107-110 주석 두 줄 + 두 업무). `demo.yaml`·`docker-ora.yaml` 은 안 고친다(키 없으면 null).
- `core/profile/ProfileStoreTest.exampleMapsEveryField`(:33-) — `p.deliverable().business()` 단언 :47-49 뒤에 이어 붙인다.
- 의존 방향: `core/deliverable` → `core/profile`(`Definitions` 가 `Profile.Deliverable.isPattern` 을 쓴다). **`core/profile` → `core/deliverable` 는 없다** — 순환을 만들지 않는다(§3).

#### 2.4 CLI·README
- `cli/DocCommands.java` — `@Command(name = "ddl")`(:95-132) `static final class Ddl extends BatchCommand` — `@Option --snapshot`(필수, `batch.snapshotId(snapshot)` 가 `latest` 를 푼다) · `body()` 가 `batch.call("POST", "/api/deliverable/ddl", b)` → `JsonNode` · 경고는 `batch.err().println("[경고] …")` · `batch.print(r, 한 줄 요약)`(`--json` 이면 r 그대로). 하위 명령 등록은 **`cli/Main.java:14`** 의 `subcommands = { …, DocCommands.Deliverable.class, DocCommands.Ddl.class, DocCommands.Dto.class, DocCommands.Generate.class, … }`.
- `README.md:28-45` 배치 명령 표 — `ReadmeCommandsTest` 가 ① 하위 명령 이름이 표에 있나 ② 표의 옵션이 실제 있나 를 잰다. **명령을 더하면 README 줄도 같이**.
- `cli/DocCliTest.java` — `CliFixture`(H2 `TB_DEPT`(PK)·`TB_EMP`(PK, `DEPT_NO REFERENCES TB_DEPT`)) + `fx.run("ddl", "--snapshot", "latest", …, "--profile", "t", "--json")` → `CliFixture.Run`(stdout·stderr·code).

#### 2.5 시험 자리
- `web/DeliverableRoutesTest` — `@BeforeAll` H2 `TB_CMM_CD(GRP_CD, CD, CD_NM, USE_YN, PK(GRP_CD,CD))` + `IF_ORDER_RCV(ORD_NO)`(PK·인덱스 없음), 프로필 `t`(`output.dir` 있음), `snapshotId`. `qualitySqlAndNoPk`(:215) 가 `nopk` 를 잰다. `post(path, body)` 도우미.
- `web/SmokeHtmlUnitTest.deliverableDdlCard(@TempDir)`(:940-990) — 자기 H2(`TB_DEPT`)·자기 프로필(`output.dir`)·자기 서버(`App.start`) → `/tools/deliverable_sql.html` 열고 `#snap` 마지막 옵션 고르고 버튼 클릭 → `waitForBackgroundJavaScript` → 단언. **이 꼴을 복사**해 린트 카드 스모크를 만든다. HtmlUnit 은 `fetch`·옵셔널 체이닝을 못 읽는다(common.js 머리 주석) — 화면 JS 는 ES5 로.
- `core/deliverable/DefinitionsTest` — `col(name, ord, type, len, p, s, nullable, def, comment)` 도우미(:22-24) · `fixture()`(:27-45, `Table.of…withColumns…withConstraints…withIndexes…withStats`) · `GoldenFiles.schemas("meta/postgres-vendor.json")`·`GoldenFiles.assertJson("deliverable/pg-02.json", obj)`(갱신 `-Dgolden.update=true`).
- `golden/meta/postgres-vendor.json` 표 여덟(실측): `categories`(pk category_id, fk parent_id→categories, 인덱스 0) · `code_groups`(pk group_code) · `codes`(pk group_code+code, fk group_code→code_groups) · `logs`(pk log_id, fk 0, 아무도 안 가리킴) · `order_items`(pk order_id+line_no, fk order_id→orders, product_id→products, 인덱스 0) · `orders`(pk order_id, fk user_id→users, ix_orders_user_date(user_id, ordered_at)) · `products`(컬럼 6, pk product_id, fk category_id→categories, uq code, ix_products_category(category_id), uq_products_code) · `users`(pk user_id, uq login_id, uq_users_login, ux_users_email). 행 수 전부 null.
- `web/HrSnapshotCorpusTest`(`@Tag("corpus")`, V-6) — `static List<Schema> snap`(H2 에 옮긴 Oracle HR 7표), `definitionsBuild()`(:92) 가 `Definitions.build(snap, …)` 를 돈다. 골든 `corpus/hr-h2.json`.
- `core/deliverable/Relations.java` — `record Named(childTable, childCol, parentTable, parentCol)`(:~36) · `named(tables, declared)`(:139-170, 부모를 PK 이름으로 색인 `byPk`·`pkCol`, `for c … for col … for p : byPk.get(up(col.name))` 안에서 `p == c || declared … || 타입·길이 다름` 이면 continue, `out.add(new Named(c.name(), col.name(), p.name(), pc.name()))`, 끝에 childTable·childCol·parentTable 정렬). `DeliverableService.candidates(Relations.Result)`(:~233) — weak 행 근거 `조인`/`조인·이름`, named 행 `(child, col, parent, pkCol, "", "", "이름")`. 시험 `RelationsTest.namedSameNameAndTypeAsSinglePk`·`namedNeedsSameTypeAndLength`·`namedSkipsDeclaredFkPairCompositePkAndView`(4인자 `new Relations.Named(...)` 비교) · `DeliverableServiceTest.candidateRowsMergeJoinAndName`.

#### 2.6 검증 레인
- 바뀌는 코드는 java·tools·docs 레인 + **corpus 레인**(`HrSnapshotCorpusTest` 골든·화면 JS). db 레인(DB 접속·DDL·마이그레이션)은 안 바뀐다 → 마무리는 `--corpus` 만(약 7분). `verify.sh` 끝 줄이 db 를 요구하면 그때 돈다.

---

### 3. 설계 결정

**[고정] 규칙 아홉과 id** — YAML `off` 와 API `rule` 칸에 쓰는 id 는 lowerCamel. 라벨·뜻은 enum 에 둔다(화면·xlsx·CLI 가 한 글을 쓴다).

| id | enum | 라벨 | 판정(뷰는 모든 규칙에서 뺀다) | column 칸 | detail 칸 |
|---|---|---|---|---|---|
| fkNoIndex | FK_NO_INDEX | FK 컬럼 인덱스 없음 | FK 의 컬럼 묶음(대문자 집합)과 **같은 크기의 앞머리**를 가진 것이 pk·uniques·indexes 어디에도 없다 | FK 컬럼을 `,` 로 | `FK <이름> → <refTable>` |
| noRelation | NO_RELATION | 관계 없는 표 | 자기 FK 0 이고, 스냅샷 안 다른 표의 FK 가 이 표를 안 가리킨다(`refTable` 대소문자 무시, `refSchema` 가 null 이거나 같을 때) | null | `""` |
| noIndex | NO_INDEX | 인덱스 없는 표 | pk 없음(또는 컬럼 0) **그리고** uniques 0 **그리고** indexes 0 | null | `rowCount == null ? "행 수 모름" : "행 " + rowCount` |
| tooManyColumns | TOO_MANY_COLUMNS | 컬럼 수 상한 초과 | `maxColumns > 0 && columns.size() > maxColumns` | null | `컬럼 n > m` |
| missingRequired | MISSING_REQUIRED | 필수 컬럼 없음 | `requiredColumns` 가 비지 않았고, 그중 표에 없는 것(대문자 비교)이 있다 — 표마다 한 건 | null | 빠진 이름을 `,` 로 |
| dupRelation | DUP_RELATION | 중복 관계 | 같은 표 안에서 「컬럼 묶음(대문자, 순서대로 `,`) → refTable(대문자)」 가 같은 FK 가 둘 이상 — 묶음마다 한 건 | 컬럼 묶음 | `FK <이름1>·<이름2>…` |
| singleColumn | SINGLE_COLUMN | 컬럼 하나뿐인 표 | `columns.size() == 1` | 그 컬럼 | `""` |
| serialColumns | SERIAL_COLUMNS | 번호 붙은 반복 컬럼 | 이름이 `^(.*[A-Za-z_])(\d+)$` 에 맞는 컬럼을 어간(대문자)으로 묶어 **셋 이상** — 어간마다 한 건 | `<어간>1…` | 컬럼 이름을 `·` 로(순번 순) |
| defaultNullString | DEFAULT_NULL_STRING | 기본값이 글자 NULL | `defaultValue` 를 trim 해 `(?i)^'NULL'(::.*)?$` 에 맞는다(따옴표 없는 `NULL` 은 기본값 없음이라 안 잡는다) | 그 컬럼 | defaultValue 원문 |

- 버린 것: PK 없는 표(`nopk` 버튼이 이미 있다 — 같은 화면에 둘이면 헷갈린다) · 코멘트 없음(`Audit`) · 이름 규칙(Audit·코드 검사) · 행 수 0 인 표(스냅샷 `skipEmpty` 가 거른다) · SchemaSpy 「암시 관계」(2-27 에 이미).

**[고정] 엔진** — `core/deliverable/StructureLint.java`(신설, `core` 규칙대로 web·cli 를 import 하지 않는다).

```java
public final class StructureLint {
    public enum Rule {
        FK_NO_INDEX("fkNoIndex", "FK 컬럼 인덱스 없음", "FK 컬럼 묶음으로 시작하는 인덱스·PK·유니크가 없다 — 조인·부모 삭제 검사가 느리다"),
        NO_RELATION("noRelation", "관계 없는 표", "FK 가 없고 다른 표의 FK 가 가리키지도 않는다(스냅샷 안) — 고립 표나 빠진 FK"),
        NO_INDEX("noIndex", "인덱스 없는 표", "PK·유니크·인덱스가 하나도 없다"),
        TOO_MANY_COLUMNS("tooManyColumns", "컬럼 수 상한 초과", "프로필 deliverable.lint.maxColumns 를 넘는다"),
        MISSING_REQUIRED("missingRequired", "필수 컬럼 없음", "프로필 deliverable.lint.requiredColumns 중 없는 것이 있다"),
        DUP_RELATION("dupRelation", "중복 관계", "같은 컬럼 묶음이 같은 표를 가리키는 FK 가 둘 이상"),
        SINGLE_COLUMN("singleColumn", "컬럼 하나뿐인 표", "컬럼이 하나다 — 설계 잔재나 미완성"),
        SERIAL_COLUMNS("serialColumns", "번호 붙은 반복 컬럼", "같은 이름에 1·2·3… 이 붙은 컬럼이 셋 이상 — 비정규화 의심"),
        DEFAULT_NULL_STRING("defaultNullString", "기본값이 글자 NULL", "DEFAULT 'NULL' — NULL 이 아니라 글자 N·U·L·L 이 들어간다");
        public final String id, label, meaning;
        public static Rule byId(String id) // 없으면 IllegalArgumentException("구조 점검 규칙이 아니다: " + id + " — " + 전체 id 나열)
    }
    /** maxColumns 0 이면 안 본다. requiredColumns 비면 안 본다. off 는 끈 규칙 */
    public record Options(int maxColumns, List<String> requiredColumns, Set<Rule> off) {
        public static final Options DEFAULT = new Options(0, List.of(), Set.of());
        /** 2-28b — 프로필 키에서. null 이면 DEFAULT. off 의 모르는 id 는 IllegalArgumentException(byId) */
        public static Options of(kr.ejg.toolbox.core.profile.Profile.Lint l)
    }
    /** column 이 null 이면 표 단위 */
    public record Finding(Rule rule, String schema, String table, String column, String detail) {}
    /** counts 는 켠 규칙 전부(0 포함, Rule 순서) · basis 는 화면·xlsx 에 보일 기준 글 */
    public record Result(List<Finding> findings, Map<Rule, Integer> counts, int tables, List<String> basis) {}
    public static Result run(List<Schema> snapshot, Options o)
}
```
- `run` — `Definitions.sorted(snapshot)` 로 돌고 뷰를 뺀다. 「가리키는 표」 집합은 먼저 한 번 모은다(`up(refTable)`). findings 정렬: Rule 순 → schema → table → column(null 먼저). `counts` 는 `EnumMap`, 끈 규칙은 빠진다. `basis` 셋: `"컬럼 수 상한 " + (max > 0 ? max : "안 봄")` · `"필수 컬럼 " + (비면 "안 봄" : join(",", 대문자))` · `"끈 규칙 " + (비면 "없음" : 라벨 join("·"))`.
- `Options.of` 가 `Profile.Lint` 를 받는 쪽(deliverable → profile)만 둔다. **`Profile.Lint` 는 규칙 id 를 검증하지 않는다** — profile → deliverable 순환을 만들지 않으려고. 모르는 id 는 API 400·CLI 1 로 드러난다(§3 API).
- 버린 대안: `core/logical/Audit` 에 규칙 추가 — Audit 는 사전·변환이 재료라 결이 다르고 화면도 다르다(표준 사전). `core/quality` — 그쪽은 값 진단 SQL 글 생성기다.

**[고정] 프로필 키** — `deliverable.lint: { maxColumns: 50, requiredColumns: [...], off: [] }`.
```java
/** 91 구조 점검 기준(2-28b). maxColumns 0·null 은 안 봄. requiredColumns 비면 안 봄. off 는 끌 규칙 id */
public record Lint(Integer maxColumns, List<String> requiredColumns, List<String> off) {
    public Lint {
        requiredColumns = requiredColumns == null ? List.of() : List.copyOf(requiredColumns);
        off = off == null ? List.of() : List.copyOf(off);
    }
}
public record Deliverable(String author, String org, String templateDir, String mapping, Scope filter, Map<String, List<String>> business, Lint lint)
```
- `profiles/example.yaml` `business:` 뒤:
```yaml
  # 91 구조 점검(산출물 화면·lint 명령) 기준. 규칙 id — fkNoIndex noRelation noIndex tooManyColumns missingRequired dupRelation singleColumn serialColumns defaultNullString
  lint:
    maxColumns: 50                      # 컬럼 수 상한. 0 이면 안 본다
    requiredColumns: [FRST_REGIST_PNTTM, FRST_REGISTER_ID, LAST_UPDT_PNTTM, LAST_UPDUSR_ID]   # 모든 표에 있어야 할 컬럼(대소문자 무시). 비우면 안 본다
    off: []                             # 끌 규칙 id
```

**[고정] API** — `POST /api/deliverable/lint {snapshotId, save}` → `DeliverableRoutes.register` 안(`filtered` 를 쓴다 — 정의서와 같은 범위). 응답:
```
{ "tables": n, "basis": ["컬럼 수 상한 50", "필수 컬럼 …", "끈 규칙 없음"],
  "rules": [{"id","label","meaning","n"}…]   // 켠 규칙, Rule 순, n 은 0 포함
  "findings": [{"rule","label","schema","table","column","detail"}…],
  "path": "…\\구조점검-<snapshotId>.xlsx" }  // save 일 때만
```
- 모르는 `off` id → 400 `{message}`(`IllegalArgumentException` 을 잡는다). 스냅샷 없음 404·거른 표 0 은 400 — `filtered` 가 쓴다.
- xlsx: `Outputs.xlsx(profile, "구조점검-" + snapshotId + ".xlsx", [text 규칙, text 스키마, text 표, text 컬럼, text 내용, text 뜻], rows)` — 행 = findings(라벨·schema·table·column 빈칸·detail·meaning). 시트 하나. 지적 0 이어도 머리만 있는 파일을 쓴다.
- `record LintRequest(Long snapshotId, Boolean save)` 같은 파일 안.

**[고정] CLI** — `cli/DocCommands` 에 `@Command(name = "lint")` `static final class Lint extends BatchCommand`: `--snapshot`(필수) 하나. 늘 `save: true`. `batch.print(r, path + " — 표 " + tables + " · 지적 " + findings.size() + " · " + rules 를 "라벨 n" 으로 "·" join)`. 지적 한 줄씩 stdout 에? **안 쓴다**(xlsx 가 결과, `--json` 이 전부). README 표 `ddl` 줄 아래:
`| \`lint --snapshot <id·latest>\` | 구조 점검 xlsx — FK 컬럼 인덱스·관계 없는 표·필수 컬럼 등 아홉 규칙(기준은 프로필 deliverable.lint) |`

**[고정] 화면** — 90 카드 바로 뒤에 카드 하나(ID 들은 스모크가 잰다):
```html
<div class="card">
    <h2>91 구조 점검 — 스냅샷으로</h2>
    <div class="msg">DB 에 다시 붙지 않고 스냅샷만 본다. 기준(컬럼 수 상한·필수 컬럼·끈 규칙)은 프로필 deliverable.lint — 화면은 기준을 보이기만 한다. PK 없는 표는 위 90 의 버튼.</div>
    <div class="row">
        <button type="button" id="lintRun" class="btn-p">구조 점검</button>
        <button type="button" id="lintXlsx" class="dl"><span class="ico ico-xlsx" aria-hidden="true"></span>구조 점검</button>
    </div>
    <div id="lintRes"></div>
    <div class="msg" id="lintMsg"></div>
    <div class="msg" id="lintBasis"></div>
    <div class="tbl" id="lintTbl"></div>
</div>
```
```js
/* 91 — 구조 점검(2-30). 기준은 프로필, 화면은 보이기만 */
function lint(save) {
    if (!$('snap').value) {
        if (save) TB.result('lintRes', 'fail', { summary: '스냅샷을 먼저 고른다' });
        else msg('lintMsg', '스냅샷을 먼저 고른다', 'err');
        return;
    }
    if (save) TB.result('lintRes', 'run', { summary: '구조 점검 xlsx 만드는 중…' });
    TB.api('/api/deliverable/lint', { body: { snapshotId: Number($('snap').value), save: !!save } }).then(function (r) {
        TB.table($('lintTbl'), ['규칙', '스키마', '표', '컬럼', '내용'], r.findings.map(function (f) {
            return [f.label, f.schema || '', f.table, f.column || '', f.detail || ''];
        }));
        $('lintBasis').textContent = '기준 — ' + r.basis.join(' · ');
        var counts = r.rules.map(function (x) { return x.label + ' ' + x.n; }).join(' · ');
        msg('lintMsg', '구조 점검 — 표 ' + r.tables + ' · 지적 ' + r.findings.length + ' (' + counts + ')', r.findings.length ? 'err' : 'ok');
        if (save) TB.result('lintRes', 'ok', { summary: '구조 점검 — 지적 ' + r.findings.length, path: r.path });
    }, save ? function (e) { TB.result('lintRes', 'fail', { summary: (e && e.message) ? e.message : '실패' }); } : fail('lintMsg'));
}
```
바인딩 `$('lintRun')` → `lint(false)`, `$('lintXlsx')` → `lint(true)`. 런처 `index.html:78` 설명 끝에 「· 구조 점검」 한 마디(「문서별 추출 SQL 가이드(방언 5종)·수기 메모·구조 점검 포함.」).

**[고정] 2-31 관계 후보 PK끼리** — `Relations.Named` 에 `boolean pkPair` 5번째 + 4인자 호환 생성자(`pkPair = false`). `named()` 안, 후보 `(c, col, p)` 를 더하기 전에: `boolean pair = c.pk() != null && c.pk().columns().size() == 1 && up(c.pk().columns().get(0)).equals(up(col.name()))`(자식 컬럼이 자식의 단일 PK — 그러면 p 쪽도 같은 비교를 통과해 양방향이 된다). `pair` 면 **`up(c.name()).compareTo(up(p.name())) < 0` 일 때만** 넣는다(한 쌍 한 줄). `DeliverableService.candidates` named 행 근거 = `n.pkPair() ? "이름(PK끼리)" : "이름"`. weak 와 합칠 때(`조인·이름`)는 그대로. 「관계 추정의 한계」 문장 끝에 「 · PK끼리는 1:1 이거나 우연히 이름이 같은 것이다 — 방향을 모른다」.
- 버린 대안: 지우기(1:1 진짜를 잃는다) · 그대로 두기(사용자 2026-10-14 「헛짚음」).

**골든 갱신은 diff 를 읽고** — `pg-lint.json` 은 처음 만들 때 §2.5 픽스처 실측으로 예측한 목록(§4 2-28a)과 맞대고, 다르면 코드를 의심한다. `pg-04.json`·`hr-h2.json` 등 다른 골든이 바뀌면 중단 ②.

---

### 4. 실행 스텝

청크 = 커밋 하나. `verify.sh` 와 `git commit` 은 따로. 커밋 제목 `feat|fix|test|docs: <번호> <무엇>`, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. 청크마다 분할표 행 `완료` + PROGRESS 이력 한 줄(앞 줄 「(이 커밋)」 을 해시로).

#### S0. 문서 행
- `git checkout main && git pull` 뒤 `git checkout -b work/2026-10-14b`. 이 플랜 → `design/25-bundle40.md`(맨 위 「원본은 설계 세션 플랜 파일 — S0 에서 옮김」). `PLAN.md` 15장 번들 표 번들 39 행 아래 번들 40 행(`S0`→`2-28a`→`2-28b`→`2-29`→`8-15`→`2-30`→`2-31`→`V-28`, 「2026-10-14(Fable, 설계 25 — `design/25-bundle40.md`). **구조 점검** — 스냅샷만으로 아홉 규칙 … 산출물 SQL 91 칸·API·CLI·xlsx + 관계 후보 PK끼리 표시. 새 의존성 0, 새 화면 0. 머지 위임 없음」, 설치 「표본 폴더 + node(`--corpus`)」, 상태 「실행 중」). 16장 분할표 끝에 행 여덟(설계 칸 「설계 25 §3 <항목>」 + 건드리는 자리, 선행, 상태 `대기`). 3.5 추가 기능 표에 「구조 점검」 한 줄(행 「번들 40(2-28~2-30·8-15)」). PROGRESS 「현재 상태」 가지·진행중 청크 갱신 + 이력 한 줄. 메모리 `compare-research-2026-10-14.md` 끝에 「설계 25 = `design/25-bundle40.md`, 번들 40 실행 중」.
- 검증: `bash scripts/verify.sh`(docs) → 커밋 `docs: S0 번들 40 설계 25 — 구조 점검 행`.

#### 2-28a. 엔진 `StructureLint` + 골든
- 대상: `core/deliverable/StructureLint.java`(신설) · `src/test/java/kr/ejg/toolbox/core/deliverable/StructureLintTest.java`(신설) · `src/test/resources/golden/deliverable/pg-lint.json`(신설)
- 변경: §3 엔진 그대로(이 스텝에선 `Options.of(Profile.Lint)` 는 **아직 없음** — 2-28b 가 더한다). 시험:
  - 규칙마다 양성·음성 한 쌍 이상, `DefinitionsTest.col(...)` 꼴 도우미로 손 픽스처(한 메서드에 여러 단언 가능). 양성 예 — `fkNoIndex`: `ORD(CUST_ID fk→CUST, 인덱스 0)` 지적, `ORD2(ix(CUST_ID, X))` 는 앞머리라 안 지적, 복합 FK `(A,B)` 에 `ix(B,A)` 는 집합 같아 안 지적, pk 가 FK 를 앞머리로 가지면 안 지적 · `noRelation`: 혼자인 표 지적, 가리켜지는 표·뷰 안 지적 · `noIndex`: pk·uq·ix 없음 + `withStats(0L…)` → detail 「행 0」, rowCount null → 「행 수 모름」 · `tooManyColumns`: max 2 에 컬럼 3 지적, max 0 안 봄 · `missingRequired`: `[REG_DT, reg_id]` 에 `REG_DT` 만 있는 표 → detail `REG_ID`, 비면 안 봄 · `dupRelation`: 같은 묶음→같은 표 FK 둘 지적, 다른 표 가리키면 안 지적 · `singleColumn` · `serialColumns`: `ADDR1·ADDR2·ADDR3` 지적(detail 순번 순), 둘뿐이면 안 지적, `A1` 처럼 어간 없는 것 안 셈 · `defaultNullString`: `'NULL'`·`'null'::text` 지적, `NULL`·`'N'` 안 지적 · `off` 에 든 규칙은 counts 에도 없다 · 정렬(Rule 순 → 표).
  - 골든: `GoldenFiles.schemas("meta/postgres-vendor.json")` + `new Options(5, List.of("created_at"), Set.of())` → `GoldenFiles.assertJson("deliverable/pg-lint.json", result.findings())`. **처음 골든 예측**(§2.5 실측): `fkNoIndex` 2 = categories.parent_id · order_items.product_id(order_id 는 pk 앞머리, orders.user_id 는 ix 앞머리, products.category_id 는 ix, codes.group_code 는 pk 앞머리) · `noRelation` 1 = logs · `noIndex` 0 · `tooManyColumns` 1 = products(6 > 5) · `missingRequired` 7 = products 를 뺀 전부(`created_at` 은 products 에만 있다, detail `CREATED_AT`) · `dupRelation`·`singleColumn`·`serialColumns`·`defaultNullString` 0(기본값은 `0`·`CURRENT_TIMESTAMP`·`'Y'::bpchar` 뿐) — **합 11건**. 예측과 다르면 코드를 먼저 의심한다(§7 ③).
- 검증: `bash scripts/mvn.sh -q -B test -Dtest=StructureLintTest -Dgolden.update=true -Dsurefire.failIfNoSpecifiedTests=false` → `pg-lint.json` 을 읽어 예측과 대조 → 다시 `-Dtest=StructureLintTest,DefinitionsTest` 초록. `verify.sh`. 커밋 `feat: 2-28a 구조 점검 엔진 StructureLint — 규칙 아홉·골든`.
- 의존: S0

#### 2-28b. 프로필 `deliverable.lint`
- 대상: `core/profile/Profile.java`(`Lint` 레코드 + `Deliverable` 7번째) · `profiles/example.yaml` · `core/deliverable/StructureLint.java`(`Options.of(Profile.Lint)`) · `core/profile/ProfileStoreTest.java`(예시 단언 셋 — `maxColumns 50`·`requiredColumns` 넷·`off` 빈 목록) · `StructureLintTest`(`Options.of(null) == DEFAULT`, `of(new Lint(null, null, List.of("fkNoIndex")))` → off 에 FK_NO_INDEX, `of(new Lint(0, List.of(), List.of("nope")))` → IllegalArgumentException 메시지에 id 아홉)
- 검증: `-Dtest=ProfileStoreTest,StructureLintTest` 초록(`exampleRoundTrips` 포함). `verify.sh`. 커밋 `feat: 2-28b 프로필 deliverable.lint — 컬럼 수 상한·필수 컬럼·끈 규칙`.
- 의존: 2-28a

#### 2-29. API `POST /api/deliverable/lint` + xlsx
- 대상: `web/DeliverableRoutes.java`(`LintRequest` · 라우트 — `register` 안 `scope` 라우트 앞뒤 아무 데나) · `web/DeliverableRoutesTest.java`(새 `structureLint`)
- 변경: §3 API. 프로필 → `Options.of(p == null || p.deliverable() == null ? null : p.deliverable().lint())`, `IllegalArgumentException` → 400. save 면 `Outputs.xlsx(active.get().orElse(null), …)`.
- 시험: 픽스처(§2.5)로 `{snapshotId}` → 200, `tables == 2`, `rules.size() == 9`, findings 가 **정확히 넷** — `noRelation` TB_CMM_CD·IF_ORDER_RCV · `noIndex` IF_ORDER_RCV(detail 「행 0」 — RowCounter 가 센다) · `singleColumn` IF_ORDER_RCV(column `ORD_NO`); `rules` 의 `fkNoIndex` n 은 0 · `{snapshotId, save:true}` → `path` 가 `구조점검-<id>.xlsx` 로 끝나고 파일이 있으며 첫 행이 `규칙|스키마|표|컬럼|내용|뜻`(POI 로 읽기 — `CheckRoutesTest` 머리 행 읽는 꼴) · `{snapshotId: 999}` → 404.
- 검증: `-Dtest=DeliverableRoutesTest` 초록. `verify.sh`. 커밋 `feat: 2-29 구조 점검 API — POST /api/deliverable/lint · xlsx`.
- 의존: 2-28b

#### 8-15. CLI `lint`
- 대상: `cli/DocCommands.java`(`Lint` 명령) · `cli/Main.java:14`(`subcommands` 배열에 `DocCommands.Lint.class` 를 `DocCommands.Ddl.class` 뒤에) · `README.md`(표 한 줄, `ddl` 아래) · `cli/DocCliTest.java`(새 `lintWritesFile` — `fx.run("lint", "--snapshot", "latest", "--profile", "t", "--json")` → code 0, stdout JSON 의 `path` 파일 존재·`tables == 2`·`rules.size() == 9`·`findings` 의 `fkNoIndex` 0(H2 가 FK 인덱스를 스스로 만든다, §8)·`noRelation` 0(TB_DEPT 는 가리켜지고 TB_EMP 는 FK 가 있다))
- 검증: `-Dtest=DocCliTest,ReadmeCommandsTest,BatchCliTest` 초록. `verify.sh`. 커밋 `feat: 8-15 CLI lint — 구조 점검 xlsx`.
- 의존: 2-29

#### 2-30. 화면 91 카드
- 대상: `src/main/resources/tools/deliverable_sql.html`(카드 + `lint(save)` + 바인딩 둘) · `src/main/resources/tools/index.html:78`(설명 한 마디) · `web/SmokeHtmlUnitTest.java`(새 `deliverableLintCard(@TempDir)` — `deliverableDdlCard` 복사: H2 `TB_DEPT(DEPT_NO INTEGER PRIMARY KEY, DEPT_NM VARCHAR(30))` + `TB_EMP(EMP_NO INTEGER PRIMARY KEY, DEPT_NO INTEGER REFERENCES TB_DEPT(DEPT_NO), ADDR1 VARCHAR(10), ADDR2 VARCHAR(10), ADDR3 VARCHAR(10))`, 프로필에 `deliverable:\n  lint:\n    requiredColumns: [REG_DT]\n` · 스냅샷 고른 뒤 `#lintRun` 클릭 → `#lintMsg` 가 「구조 점검 — 표 2 · 지적 」 로 시작하고 class 에 `err` · `#lintTbl` 행에 「번호 붙은 반복 컬럼」(TB_EMP, 컬럼 `ADDR1…`, 내용 `ADDR1·ADDR2·ADDR3`) 와 「필수 컬럼 없음」 두 줄(detail `REG_DT`) · `#lintBasis` 가 「기준 — 」 로 시작하고 「필수 컬럼 REG_DT」 포함 · `#lintXlsx` 클릭 → `#lintRes` class `tb-result res-ok`, `.res-p` 글이 `구조점검-\d+\.xlsx` 로 끝남 · 콘솔 오류 0(기존 스모크의 콘솔 수집 꼴 그대로)
- 검증: `-Dtest=SmokeHtmlUnitTest#deliverableLintCard,ToolsFolderTest,LauncherTest` 초록 → `-Dtest=SmokeHtmlUnitTest` 전체 초록. `verify.sh`. 커밋 `feat: 2-30 산출물 SQL 91 구조 점검 카드`.
- 의존: 2-29

#### 2-31. 관계 후보 PK끼리
- 대상: `core/deliverable/Relations.java`(`Named` 5 + 4인자 호환 · `named()` 한 쌍 한 줄) · `core/deliverable/DeliverableService.java`(`candidates` 근거 · 한계 문장) · `core/deliverable/RelationsTest.java`(새 `namedPkPairOnceWithFlag` — `LOG_A(LOG_ID pk)`·`LOG_B(LOG_ID pk)` 같은 타입 → `List.of(new Relations.Named("LOG_A", "LOG_ID", "LOG_B", "LOG_ID", true))` 하나뿐 · 기존 `namedSameNameAndTypeAsSinglePk` 는 ORD.CUST_ID 가 ORD 의 PK 가 아니라 `pkPair false` 그대로 통과) · `DeliverableServiceTest.candidateRowsMergeJoinAndName`(named 에 `new Relations.Named("LOG_A","LOG_ID","LOG_B","LOG_ID", true)` 한 줄 더해 근거 「이름(PK끼리)」 단언)
- 검증: `-Dtest=RelationsTest,DeliverableServiceTest,DefinitionsTest` 초록, `pg-04.json` 불변. `verify.sh`. 커밋 `feat: 2-31 관계 후보 — PK끼리 쌍은 한 줄에 근거 「이름(PK끼리)」`.
- 의존: S0(2-28~2-30 과 독립)

#### V-28. 실물 표본 — HR 스냅샷 구조 점검
- 대상: `web/HrSnapshotCorpusTest.java`(새 `structureLint()` — `StructureLint.run(snap, new Options(0, List.of(), Set.of()))` → **A**: 예외 없음 · findings 의 표 이름이 전부 snap 에 있다 · `counts` 키 아홉 **B**: `GoldenFiles.assertJson("corpus/hr-lint.json", result.findings())`) · `golden/corpus/hr-lint.json`(신설)
- 검증: `bash scripts/mvn.sh -q -B test -Dtest=HrSnapshotCorpusTest -Dgroups=corpus -Dgolden.update=true -Dsurefire.failIfNoSpecifiedTests=false`(표본 폴더·node 필요 — `corpus-fetch.sh` 가 이미 받아 둔 것) → 골든을 읽어 HR 7표 기준으로 그럴듯한지(fk 는 H2 자동 인덱스라 `fkNoIndex` 0, `noRelation` 은 REGIONS 가 아니라 — COUNTRIES 가 가리킨다 — 아마 0, `serialColumns`·`defaultNullString` 0) 본다 → 다시 돌려 초록. 이력에 골든 내용 한 줄. `verify.sh`. 커밋 `test: V-28 HR 스냅샷 구조 점검 골든`.
- 의존: 2-28a

#### 마무리(CLAUDE.md 「마무리」 그대로)
독립 리뷰 → `gate-probe.sh` → `bash scripts/verify.sh` 끝 줄(corpus 가 나온다 — `--corpus` 약 7분; db 가 나오면 그것도) → push → PR(본문에 청크마다 시작 세 줄 표) → CI 폴링 → AI 리뷰 처분(버그만, 메모리 `read-ai-review-before-merge`) → 머지 위임 없음, PR 열고 멈춤 → 끝 보고(계획 밖 결정 · wip · 새 행 · 사람이 할 것 — 데모에서 91 카드 눌러 보기, 실제 사업 프로필에 `lint` 기준 적기).

---

### 5. 금지 사항

- `pure/` · CI · `scripts/verify*` · 새 의존성 · 정의서 `COLS_*`·양식·매핑 · `core/quality`(값 진단 SQL)·`core/logical/Audit` 에 규칙 추가 · `nopk` 버튼·API 변경 · 린트 결과의 H2 저장 · `demo.yaml`·`docker-ora.yaml`.
- `core/profile` 이 `core/deliverable` 를 import 하지 않는다(순환). `core` 가 `web`·`cli`·Javalin 을 import 하지 않는다.
- 레코드에 컴포넌트를 더하면서 기존 인자 수 생성자를 없애지 않는다(`Relations.Named` 4인자 유지). `Profile.Deliverable` 은 직접 호출이 0건이라 그냥 더한다.
- 골든을 「초록 되게」 갱신하지 않는다 — 새 골든은 `pg-lint.json`·`hr-lint.json` 둘뿐. 다른 골든이 바뀌면 중단.
- 화면 `<style>` 에 새 색·버튼 꼴을 넣지 않는다. 화면 JS 는 ES5(fetch·옵셔널 체이닝 금지).
- PK끼리 후보를 지우지 않는다(표시만).

---

### 6. 최종 검증

- 청크마다 적힌 `-Dtest=` 묶음 초록 + `verify.sh` 빠른 초록(커밋마다).
- 마무리 `verify.sh --corpus` 초록(`HrSnapshotCorpusTest` 골든 · `PureCodeCheckTest` 등 corpus 레인 불변). db 레인은 안 바뀌어야 한다(`verify.sh` 끝 줄이 db 를 요구하면 §7 ①).
- `gate-probe.sh` 게이트 8 산다.
- 사람이 볼 것(데모 프로필, `run.bat --profile demo` 또는 메모리 `demo-environment` 명령): 산출물 SQL → 스냅샷 #97 → 「구조 점검」 → 표 184 · 지적 목록(전자정부는 `noRelation` 이 많이 나온다 — 코드 표·로그 표) · 기준 줄 · xlsx. CLI `toolbox.bat lint --snapshot latest --profile demo` 가 같은 파일을 쓴다. 작성안내 「관계 후보」 에 「이름(PK끼리)」 가 25줄 남짓(196 → 171).

---

### 7. 중단 조건

- ① §2 의 파일·줄·시그니처가 실제와 다르다, 또는 `verify.sh` 가 db 레인 변경을 알린다(이 번들은 DB 코드를 안 건드린다).
- ② `pg-lint.json`·`hr-lint.json` 밖의 골든이 바뀐다.
- ③ `pg-lint.json` 첫 골든이 §4 2-28a 예측(11건 — fkNoIndex 2 · noRelation 1 · tooManyColumns 1 · missingRequired 7)과 어긋나는데 두 번 들여다봐도 원인이 안 보인다.
- ④ `ProfileStoreTest.exampleRoundTrips` 가 `Lint` 추가로 빨강(Jackson 직렬화) — 두 번 고쳐도면 보고.
- ⑤ HtmlUnit 스모크가 `#lintTbl` 을 못 채운다(JS 꼴 문제) — 두 번 고쳐도면 보고.
- ⑥ 검증 2회 연속 빨강이고 원인이 플랜 밖 · 스텝에 없는 파일 3개 이상.

보고 형식: 어느 청크 · 무엇이 예상과 달랐는지 · 선택지.

---

### 8. 불확실 항목

없음 — 설계 세션이 2026-10-14 에 전부 실측했다(실행자는 다시 조사하지 않는다):
- `Outputs.dir(Profile)`(:31-35) — `p == null` 이면 `out/default/<yyyyMMdd-HHmmss>`, 아니면 `<output.dir>/<프로필 이름>/<시각>`. 라우트는 `active` 가 있어 null 로는 안 간다.
- H2 는 FK 마다 비유니크 인덱스 `CONSTRAINT_INDEX_n`(FK 컬럼) 을 만들고 `DatabaseMetaData.getIndexInfo` 가 돌려준다(H2 2.3.232 직접 실행 확인). `JdbcMetaSource.loadIndexes`(:159-169) 는 **PK 이름만** 빼고 전부 `indexes` 에 넣는다 → H2 픽스처(`DeliverableRoutesTest`·`DocCliTest`·스모크)에서 `fkNoIndex` 는 **0 이 확실**하다. `indexes` 에 PK 가 없으니 「인덱스 있나」 는 pk 를 따로 봐야 한다(§3 [고정] 그대로).
- `RowCounter.fill`(`SnapshotService:66`) 은 통계 없는 표를 실제로 센다 → H2 의 빈 표 `IF_ORDER_RCV` 는 `rowCount 0` → `noIndex` detail 은 **「행 0」** 으로 단언한다(§4 2-29 고침).
- `SmokeHtmlUnitTest` 에 결과 칸 id 목록은 **없다**(`alterRes`·`dtoRes` 는 각 시험이 직접 잰다). 2-30 은 자기 시험에서 `lintRes` 만 잰다.
- `LauncherTest` 는 카드 수(11)·`data-status`·common.js 만 잰다 — `index.html:78` 설명 한 마디 추가는 안전하다.
- CLI 하위 명령 등록은 `DocCommands` 가 아니라 **`cli/Main.java:14`** 의 `subcommands = { …, DocCommands.Ddl.class, DocCommands.Dto.class, … }` 배열 — `DocCommands.Lint.class` 를 `Ddl.class` 뒤에 넣는다(§4 8-15 고침).
- `postgres-vendor.json` 의 `created_at` 은 **products 에만** 있다 → `missingRequired` 는 나머지 7표. 기본값은 products `0`·`CURRENT_TIMESTAMP`, users `'Y'::bpchar` 뿐 → `defaultNullString` 0(§4 2-28a 예측 확정).

실행은 Opus 세션에서 「번들 해」 → S0 부터.

---

## 다음 번들 결정 요지(이 번들에서 안 한다)

- **억제 주석(나-1)** — `// tb-ignore <규칙id> <이유>` 같은 줄 주석으로 그 줄 지적만 뺀다. 이유 글은 사용자 코드라 H2 이력엔 건수만(절대 규칙 3). 결과 머리 「억제 n건」. 건드릴 자리 `core/check/RegexRule`·`CheckRunner`·`code_check_ext.js`. 실물 뒤 보정.
- **나머지 50 규칙 `fix` 문구(나-4)** — security 밖 묶음(common·java·mybatis·jsp·tsx·file·a11y). 5-25b 와 같은 규칙(60자·「무엇을 무엇으로」).
- **구조 점검 규칙 추가 후보**(실물 뒤) — 전자정부 실측에서 `noRelation` 이 코드 표·로그 표로 넘치면 「`off` 말고 표 접두로 빼는 `lint.exclude`」 를 세운다. 지금은 입력이 없어 안 적는다.
