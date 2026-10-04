# 밤 번들 계획 — 번들 23 → 24 → 25 → 26, 멈춤 없이(설계 15, 2026-10-05)

실행 담당은 이 문서와 `PLAN.md` 행·`design/14-ui-demo.md` U-절만 보고 친다. 행·U-절이 원본이고, 이 문서는 **보정(행과 실물이 어긋난 자리)** 과 **밤 운영 규칙** 을 얹는다. 둘이 부딪치면 이 문서가 이긴다.

## 1. 목표와 범위

- **목표**: 남은 번들 셋(23 메타 수집 보강 · 24 정의서 표준 · 25 JPA 분석)과 잔손 번들 26 을 사람 없이 연달아 쳐서 번들마다 PR → CI 초록 → 머지까지 끝낸다. 아침에 사용자는 main 에 다 들어간 것과 「사람이 할 것」 한 장을 본다.
- **In**: 행 `1-19~1-23·V-23` / `2-10~2-16·2-18·6-13·2-19·V-24` / `6-14~6-16·V-25` / `1-24·1-25·1-26` + 새 행 `1-27·4-15·4-16·4-17`(PR #40·#41 AI 리뷰 지적 넷) · 번들마다 마무리(리뷰·gate-probe·`--full`·push·PR·CI·**AI 리뷰 코멘트 처분**·머지) · 끝에 사람 몫 정리.
- **Out**: `2-17`(엔터티·애트리뷰트 정의서 — 접음, 사용자 2026-10-05: 별표2 원문 항목이 저장소에 없다) · 조건부 행(2-7·2-8·4-10·5-7·5-14·5-15·6-9) · `pure/`·portfolio · 실브라우저 수동 확인 · 엑셀 실물 픽스처.
- **사용자가 자기 전에**: 플러그 꽂아 둠(지금 AC·절전 「안 함」 확인) · 뚜껑 안 닫음 · Docker Desktop 켜 둠 · 다른 프로젝트 컨테이너 안 띄움(메모리) · 실행은 Opus 권장(`/model opus` — 기존 규약 「설계 Fable·실행 Opus」, 토큰 창이 덜 찬다).

## 2. 현재 구조 요약

- **상태**: main = `ac4dba0`(번들 22 머지). 가지 `work/2026-10-05-b23` 이 이미 있고 커밋 0. 열린 PR 0. Docker 이미지 아홉·표본 폴더 `C:/workspace/toolbox-corpus`·node·Puppeteer(`C:/workspace/node_modules`) 다 있다. `--full` 은 약 21분.
- **멈춤 요소(조사 결과)**:

| 요소 | 실물 | 대응(3장 결정) |
|---|---|---|
| 머지 위임 없음 | PLAN 번들 표 23~25 「머지 위임 없음」. `pr-guard` 는 열린 작업 PR 이 있으면 새 PR 을 막는다 → 위임 없이는 둘째 번들에서 선다 | D1 |
| AI 리뷰 코멘트 | CI `review` 잡은 코멘트만 남기고 초록이다. 번들 21·22 는 코멘트를 안 읽고 머지했다(지적 넷이 남았다) | D2, 번들 26 |
| CodeQL 새 경보 | 체크가 빨개지고 `pr-guard` 가 머지를 막는다 | D3 |
| 설계 중단 조건 | `design/14` 7장 「멈추고 보고」(불변 골든 변화·안 적힌 파일 3개 등) | D4 |
| 훅 순서 | `commit-guard`·`push-guard` 는 명령이 돌기 **전에** 잰다 — `verify && commit` 한 체인은 막힌다 | 4장 공통 절차 |
| 메모리 | `--full` 은 Oracle 컨테이너 + JVM. WSL 10GB. 모자라면 백그라운드 셸이 죽는다 | D5 |
| 긴 명령 | Bash 도구 한 번은 10분 상한. `--full` 은 21분+ | 4장 공통 절차 |
| 네트워크 | V-25 `corpus-fetch.sh shopizer egov-msa`(git, github.com) | D6 |
| 사람 입력 | 2-17 열 이름 원문 없음 | 접음(Out) |
| 토큰 5시간 창 | 차면 세션이 서고 스스로 안 잇는다. 잴 수 없다 | D7 |
| 권한 분류기 | `gh pr checks` 는 막힌다(훅 안에서 도는 것은 무관). 머지·dismiss 는 이 계획 승인이 위임 근거 | D1·D3·D8 |

- **행이 실물과 어긋난 자리**는 4장 각 스텝의 「보정」 에 적었다(줄 번호·빠진 호출 자리·깨질 테스트·설계 빈틈). 줄 번호가 다르면 함수 이름으로 다시 찾는다 — 멈추지 않는다.
- **함정(공통)**:
  - `GoldenFiles.JSON` 은 레코드 필드를 전부 싣는다 — 레코드에 필드를 더하면 그 레코드가 든 골든이 전부 바뀐다. 골든에 안 실을 것은 필드를 더하지 않거나 `@JsonIgnore`.
  - `PROGRESS.md`·`PLAN.md` 행은 **한 줄**이다. 파이썬·heredoc 으로 넣으면 `\n`·`\t`·`\a` 가 진짜 제어 문자로 들어간다 — 넣은 뒤 `grep -c $'\t' PROGRESS.md`·`tail -1 PROGRESS.md | cut -c1-12` 로 본다. 백슬래시가 든 자바·JS 는 Edit·Write 도구로 쓴다.
  - 파일은 LF. 파이썬으로 쓸 때 `newline=''`.
  - `core/db/DbTest` 49·64·67 이 마이그레이션 수(`3`·「V001~V003」)를 단언한다 — V 파일을 더할 때마다 올린다.
  - `VendorFallbackTest` 50·79 가 경고 종류를 `comments·stats·uniques` 셋으로 단언한다 — 벤더 SQL 종류를 더하면 같이 올린다.

## 3. 설계 결정

- **D1 [고정] 머지 위임** — 사용자 2026-10-05 「번들 전부 진행」. CI 체크 여덟이 전부 `completed success` 이고 D2·D3 을 끝냈으면 `gh pr merge N --merge --delete-branch` → `git checkout main && git pull` → 다음 가지. 버린 대안: PR 열고 멈춤(둘째 번들 PR 을 못 연다).
- **D2 [고정] AI 리뷰 처분** — CI 가 끝나면 `gh api repos/ejg93/toolbox-server/issues/N/comments --jq '.[]|select(.user.login=="claude[bot]")|.body'` 로 읽는다. 「아니오」 마다: 결함·보안·절대 규칙 위반 → 이 PR 에서 고친다(커밋 → `--full` → push → CI 다시). 시험 보강·강제 지점 내리기·문서 정합 → 새 행으로(번들 26 이 아직 안 닫혔으면 26 에 붙인다). 처분을 PROGRESS 이력 한 줄에. 기준은 PR #38 선례.
- **D3 [고정] CodeQL** — 주석 확인: `id=$(gh api repos/ejg93/toolbox-server/commits/$(git rev-parse HEAD)/check-runs --jq '.check_runs[]|select(.name=="CodeQL")|.id'); gh api repos/ejg93/toolbox-server/check-runs/$id/annotations`. 진짜면 코드로 고친다. 오탐이면 사유 달아 dismiss(사용자 위임 2026-10-05): `gh api -X PATCH repos/ejg93/toolbox-server/code-scanning/alerts/<번호> -f state=dismissed -f dismissed_reason="false positive" -f dismissed_comment="<근거>"` → `gh run list --commit <HEAD> --json databaseId,name` 에서 CodeQL 실행을 `gh run rerun`. 경보 번호·근거를 이력과 끝 보고에.
- **D4 [고정] 중단 조건은 「빼고 간다」** — `design/14` 7장·행의 「멈춘다」 에 걸리면: 실패 사다리 → 없으면 정하고 이력 「정한 것(계획 밖)」(행당 둘) → 셋째거나 verify 빨강을 두 번 못 고치면 `git stash -u` → `git checkout -b wip/<청크>` → `git stash pop` → 커밋 → 번들 가지로 돌아와 **의존 없는 다음 행**. 의존 표는 4장. 번들에 칠 행이 안 남으면 마무리로. 안 적힌 파일 3개 제한은 이 문서 「보정」 에 적힌 파일은 세지 않는다.
- **D5 [고정] 메모리** — `--full` 앞에 남은 메모리(PowerShell `Get-CimInstance Win32_OperatingSystem`)와 `docker ps`. 8GB 미만이면 5분 기다려 다시(세 번). 그래도면 그대로 한 번 돌린다. 셸이 죽으면(`rc=` 줄 없이 끝) `.git/mvn-reap.log`·`docker ps` 로 정리를 확인하고 한 번 더. 두 번 죽으면 그 번들은 커밋까지만 두고(빠른 도장) 끝 보고에 「`--full` 못 돌림」 — 다음 번들이 의존하면 그 가지 끝에서 새 가지를 따서 잇는다(D8).
- **D6 [고정] 표본 받기** — 사용자 위임. `bash scripts/corpus-fetch.sh shopizer egov-msa`. 두 번 실패(네트워크·권한)하면 V-25 는 접는다 — 레시피(`corpus-fetch.sh`·`SOURCES.md`·`MANIFEST`) 변경을 커밋하지 않는다(MANIFEST 에 없는 폴더가 오르면 표본 테스트 전부가 빨개진다).
- **D7 [고정] 토큰** — 잴 수 없으니 청크 하나 = 커밋 하나를 지킨다. 턴을 스스로 끝내지 않는다. 서면 아침에 「번들 해」 가 `PROGRESS.md` 와 `design/15-overnight-plan.md` 에서 잇는다(S0 이 이 문서를 저장소에 넣는다).
- **D8 [고정] PR·머지가 막힐 때** — 같은 명령을 되풀이하지 않는다. PR 을 못 열거나 머지를 못 하면 그 가지를 둔 채: 독립 번들(25·26)은 main 에서, 의존 번들(24)은 앞 가지 끝에서 새 가지를 따서 **커밋·`--full` 도장까지만** 한다. 끝 보고에 머지 순서를 적는다.
- **D9 [고정] 순서** — 23 → 24 → 25 → 26. 24 는 23 의 모델·V 번호에 올라선다. 26 은 작고 `SnapshotStore`(23)·`smoke-devtools.js` 를 건드려 맨 뒤.
- **D10** 묻지 않는다 — `AskUserQuestion` 을 쓰지 않는다. 사람 입력이 필요한 행은 접고 사람 몫에 적는다.

## 4. 실행 스텝

### 공통 절차(모든 행)

1. 그 행(`grep -E "^\| <번호> " PLAN.md`)과 행이 가리키는 U-절, 이 문서의 「보정」 만 읽는다.
2. 번호 절차대로 친다. 골든은 `-Dgolden.update=true` 로 뜬 뒤 diff 를 보고, 갱신 없이 한 번 더 돌려 초록을 본다. Maven 은 `bash scripts/mvn.sh -q -B test -Dtest='A,B' -Dsurefire.failIfNoSpecifiedTests=false`.
3. PLAN 행 상태 칸 `완료(날짜) — 이력`, PROGRESS 이력 한 줄(직전 행 해시를 채운다. 「정한 것(계획 밖)」·「드러난 것」 포함).
4. `git add -A` → `bash scripts/verify.sh`(초록) → **따로** `git commit -m "<종류>: <번호> <무엇>"`(끝줄은 그 세션 system-reminder 의 Co-Authored-By).
5. 한 줄 보고 뒤 바로 다음 행. 턴을 안 끝낸다.

### 번들 마무리(번들마다)

1. `Agent(subagent_type: "caveman:cavecrew-reviewer")` 로 `git diff origin/main...HEAD` 리뷰 + 동시에 `bash scripts/gate-probe.sh`. 「패치 갱신 필요」 면 `scripts/gate-probe.sh` 머리 주석대로 `scripts/probes/*.patch` 를 다시 떠서 커밋.
2. D5 → `bash scripts/verify.sh --full` 을 `run_in_background` 로(끝에 `echo rc=$?`), `until grep -q "^rc=" <출력>; do sleep 15; done` 으로 10분씩 기다린다. 60분을 넘기면 이력에 적고 새 행(옛 판을 `--legacy` 로 떼기)만 세운다 — 임의로 떼지 않는다.
3. 문서(PLAN 번들 표 상태·PROGRESS 「현재 상태」·마무리 이력 한 줄) → `git add -A` → `bash scripts/verify.sh --full`(문서 레인만 돈다) → 따로 커밋.
4. 따로 `git push -u origin <가지>` → `gh pr create --base main --title … --body-file <scratchpad>`(본문: 청크마다 닫는 것·축·강제 지점·커밋 표 + 계획 밖 결정 + 끝줄 `🤖 Generated with [Claude Code](https://claude.com/claude-code)`).
5. CI 폴링(30초): `gh api repos/ejg93/toolbox-server/commits/$(git rev-parse HEAD)/check-runs --jq '.check_runs[]|[.name,.status,.conclusion]|@tsv'` — 여덟 줄 전부 `completed`. `gh pr checks` 는 안 쓴다. 빨강은 D3 또는 고치기 둘까지.
6. D2(리뷰 코멘트 처분) → 이력에 PR 번호·처분 → 커밋 → push → CI 다시 초록.
7. D1 머지 → `git checkout main && git pull` → `git checkout -b work/2026-10-05-b<다음>`.

### S0 — 문서 청크(가지 `work/2026-10-05-b23`, 첫 커밋)

- **대상**: 새 `design/15-overnight-plan.md`(이 플랜 파일 `C:\Users\EJG\.claude\plans\logical-scribbling-stearns.md` 를 그대로 복사) · `PLAN.md` · `PROGRESS.md`.
- **변경**: ① PLAN 15장 번들 표 23·24·25 의 「머지 위임 없음」 → 「머지 위임(사용자 2026-10-05) · 보정 `design/15-overnight-plan.md`」, 새 줄 `번들 26 | 1-24→1-25→1-26→1-27→4-15→4-16→4-17 | 잔손 — PR #39·#40·#41 리뷰가 세운 행 | 번들 23 머지 뒤` ② 16장에 새 행 넷(아래 번들 26 의 글을 여덟 칸으로) ③ `2-17` 상태 칸 「접음(2026-10-05) — 별표2 엔터티·애트리뷰트 항목 원문이 조사 문서에 없다. 사람 몫」, `2-18`·`V-24` 는 2-17 없이 친다고 번들 24 줄에 ④ PROGRESS 이력 두 줄 — 「PR #40·#41 AI 리뷰 처분(머지 뒤 — 번들 21·22 마무리에서 코멘트를 안 읽었다): 지적 넷 전부 시험 보강 → 1-27·4-15·4-16·4-17」, 「설계 15 — 밤 번들 보정」 ⑤ 「현재 상태」 가지·다음 손.
- **검증**: `bash scripts/verify.sh` 초록(doc-lint). `grep -c "^| 번들 26 " PLAN.md` = 1.
- **의존**: 없음.

### 번들 23 — 가지 `work/2026-10-05-b23`(S0 뒤 같은 가지)

행 순서 `1-19 → 1-20 → 1-21 → 1-22 → 1-23 → V-23`. 의존: 1-20←1-19, 1-21←1-20, 1-22·1-23←1-21, V-23←전부(빠진 행의 단언은 뺀다).

**공통 보정**
- V 파일은 행마다 하나, 다음 빈 번호부터: `V004__fk_rules.sql` · `V005__index_sorts.sql` · `V006__check.sql` · `V007__snap_schema.sql`(U-5b 의 「V003__meta_more」 는 옛 글). 올릴 때마다 `DbTest` 49·64·67 의 수를 올린다(허용 — 1-14 선례).
- `SnapshotStore` 실제 줄: 제약 넣기 136-149(+`constraint()` 165-177) · 인덱스 넣기 150-162 · `fill` 281-327 · kind switch 306-311.
- 방언 테스트는 표본 DDL(`src/test/resources/sample/*.sql`)을 **안 고친다**(CHECK·ON DELETE·DESC 가 하나도 없다 — 고치면 `DdlReaderTest`·`JdbcMetaSourcePostgresTest`·`InsertGenPostgresTest`·정의서 골든 열둘이 흔들린다). 대신 방언 테스트 넷(`{Oracle,Postgres,Maria,Mssql}MetaSourceTest`)에 `@Test metaMore()` 하나 — 표 `ZZ_META_MORE` 를 만들고(부모 FK `ON DELETE CASCADE`, `CHECK`, `DESC` 인덱스) 수집해 값을 직접 단언한 뒤 `finally` 에서 지운다. 골든 없음. 행이 진행되며 단언을 더한다.
- `golden/meta/{postgres,postgres-vendor,mariadb,mssql,oracle}.json` 은 새 필드만 는다. `golden/meta/diff-*.json` 은 불변(`SnapshotDiff` 안 넓힘).

| 행 | 보정 |
|---|---|
| 1-19 | `new ForeignKey(` 자리 — main 3(`JdbcMetaSource:201`·`SnapshotStore:308`·**`core/gen/DdlReader:250`**), test 4(`DefinitionsTest:35`·`DdlGenTest:43,44`·`SnapshotDiffTest:97`). 옛 5칸 생성자를 보조 생성자로 남겨(규칙 null) 호출 자리를 덜 고친다. 규칙 읽기는 `rs.getShort` 뒤 `rs.wasNull()` — NULL 이 0(=CASCADE)으로 읽힌다. 매핑 0 `CASCADE`·1 `RESTRICT`·2 `SET NULL`·3 `NO ACTION`·4 `SET DEFAULT`, NULL → null. R13: 삭제·갱신 = 수집값, null 이면 빈칸. Oracle·Tibero 고정 `NO ACTION`(`Definitions:121,131`) 제거 |
| 1-20 | `new Index(` — main 2(`JdbcMetaSource:158` `loadIndexes`·`SnapshotStore:322`), test 4. `sorts` 는 null 원소 금지(`List.copyOf`) — `A`→`ASC`·`D`→`DESC`·NULL→`""`, 목록 자체가 null 이면 `List.of()`(옛 스냅샷·골든). 옛 3칸 보조 생성자 유지. R15(`Definitions:161-167` `indexRows`): 인덱스 행 = 수집값. **UNIQUE 행은 같은 이름 인덱스의 sorts, 없으면 빈칸 · PK 행은 빈칸**(PK 인덱스는 `loadIndexes:155` 가 건너뛴다 — 안 바꾼다). `DefinitionsTest.r15Indexes:158` 은 새 기대값으로. `pg-10`·`filled-10` 의 `ASC` 가 대부분 빈칸이 된다 — 이력에 |
| 1-21 | `Table` 에 `checks` — `new Table(` 은 `Table.java` 안 6곳 + test 10곳(7파일). 옛 꼴 보조 생성자 또는 `Table.of` 유지로 테스트 수정을 줄인다. `snap_constraint` PK 가 `(snapshot_id, schema, table, kind, name)` — CHECK 이름은 null 금지(이름 없으면 `CK_<순번>`), `condition` 은 `VARCHAR(4000)`. Oracle 은 `SEARCH_CONDITION`(LONG)을 `getString` 으로 읽고 **자바에서** `^"?\w+"? IS NOT NULL$` 를 거른다(11g·23 같은 SQL). 종류 이름 `checks` → `VendorFallbackTest` 50·79 기대 집합에 더한다(허용). 골든은 `oracle.json` 에 `checks: []` 만 — `pg-11` 은 `postgres-vendor.json` 에서 나와 이 행에선 안 바뀐다. R16 CHECK 행은 `DefinitionsTest` 단위 픽스처로 |
| 1-22 | MariaDB·MySQL 은 수집기가 하나(`MariaMetaSource`). `information_schema.CHECK_CONSTRAINTS` 를 `TABLE_NAME` 열로 먼저(MariaDB), `SQLException` 이면 `TABLE_CONSTRAINTS` 조인 꼴(MySQL 8), 둘 다 실패면 물러섬(MySQL 5.7 — 옛 판 `meta-warnings` 목록에 오른다). MariaDB 가 JSON 컬럼에 자동으로 붙이는 `json_valid` 는 거르지 않는다 |
| 1-23 | `new Schema(` — main 6(`VendorMetaSource:90,99`·`MetaSource:94,96`·`SnapshotStore:239`·`web/GenRoutes:135`), test 19(**`GoldenFiles:55`** 포함). 3칸 보조 생성자 유지 + `withSizeBytes`. 스키마를 다시 만드는 자리(위 여섯 + `JdbcMetaSourcePostgresTest:35`)가 값을 흘리지 않게. 수집 종류 이름 `size`(→ `VendorFallbackTest`). SQL 은 `deliverable_sql.html` 209-212·245-248 참고. Oracle 은 스키마 = 접속 사용자면 `USER_SEGMENTS`, 아니면 `DBA_SEGMENTS`(실패 → 물러섬). 골든 가림: `GoldenFiles.assertSchemas` 가 non-null 을 `1048576` 으로, null 은 null. R17: 스키마 합(전부 null 이면 빈칸), 1GiB 미만 `%.1f MB`, 이상 `%.1f GB`(`Locale.ROOT`). `pg-01` 데이터용량이 `1.0 MB` 로 바뀐다(허용) |
| V-23 | `DbCorpusBase.meta`(362-411). CHECK 수 대조 SQL 은 테스트 안 `conn.prepareStatement` 로(835 선례). 옛 판 다섯의 `db-<판>-meta-warnings.txt` 가 바뀐다(MySQL 5.7 `checks` 등) — diff 를 이력에. 건수는 공유 `golden` 맵 → `db-<판>.json` |

- **검증(번들)**: 마무리 2 의 `--full` 초록 — 방언 넷 `warnings()` 0, 옛 판 다섯 A 0. `git diff origin/main -- src/test/resources/golden/meta/diff-*.json` 빈 출력.

### 번들 24 — 가지 `work/2026-10-05-b24`

행 순서 `2-10 → 2-11 → 2-12 → 2-13 → 2-14 → 2-15 → 2-16 → 2-18 → 6-13 → 2-19 → V-24`(2-17 없음). 의존: 2-11←2-10 · 2-12←2-11 · 2-13←2-12 · 2-14←2-13 · 2-16←2-15 · 2-18←2-16 · 2-19←6-13·2-18·2-13 · V-24←친 행만. **2-10 이 빠지면** 2-11~2-14·2-19 를 접고 2-15·2-16·6-13·2-18 만 친다.

시작 전에 `Definitions`·`DefinitionsTest` 를 다시 읽는다(번들 23 이 R13·R15·R16·R17·`tail` 줄을 옮겼다). 양식·YAML 은 `bash scripts/make-example-templates.sh` 로만 만든다. 열 이름은 `design/14-ui-demo.md` U-5c 표의 글자 그대로(사용자 확정 표다 — 조사 문서와 글자가 달라도 멈추지 않는다).

| 행 | 보정 |
|---|---|
| 2-10 | `DBMS 정보` = 지금의 DBMS명 + 공백 + DBMS버전 을 한 칸으로(가공 없음). 제약조건의 CHECK 는 `Table.checks`(1-21 이 빠졌으면 DEFAULT 만). R7·R10 새 기대값, `DeliverableRoutesTest` 열 위치는 번호 대신 열 이름으로 찾게 고친다(허용) |
| 2-11 | 05·07 은 별표1 이름에서 괄호 주석(「(Full name)」 등)을 뗀 글자로 **띄어쓰기만** 맞춘다. 띄어쓰기 말고 다른 것이 다르면 안 바꾸고 이력에. `StandardsTest` 87-111 열 이름 단언 수정 허용. `COLS_07` 은 이미 같으면 그대로 |
| 2-12 | `linkDoc` 이 컬럼 단위 입력을 받게 바뀐다 — 호출 자리 `DeliverableRoutes:234`·`DeliverableService:81` 수정 허용. 연계정보 구분이 빈칸이 되어 `CodeAndLinkTest:92-94`·`DeliverableRoutesTest:189` 의 「수신」·「송신」 단언을 새 값으로(허용). 08·09 골든은 없다 — 만들지 않는다(단언으로 닫는다) |
| 2-13 | `Doc.estimated` 는 골든에 안 싣는다 — 접근자에 `@JsonIgnore`, 옛 꼴 보조 생성자 유지(`new Doc(` 11곳 불변). `XlsxWriter` 에 새 메서드 `write(LinkedHashMap<String, ResultTable> sheets, Path)`(시트 여럿), 기존 `write` 불변. R5 로 `pg-02`·`pg-03`·`filled-02`·`filled-03` 이 바뀐다(허용). `DeliverableRoutesTest` 118·124·132 의 개수·순번 단언은 문서 번호로 찾게 고친다(허용) |
| 2-14 | `Definitions.build` 옛 오버로드 유지(`HrSnapshotCorpusTest:93`·`XlsxFillerTest:40`). 골든 경로는 빈 후보 집합 — 골든 불변. `Y` 단언은 단위 픽스처로 |
| 2-16 | 08·09 후보 라우트가 프로필을 모른다 — `App.java` 가 활성 프로필 공급자를 넘기게 고친다(허용). 테스트는 필터 든 프로필로 앱을 하나 더 띄운다 |
| 2-18 | 16·17 없이 18 만. 18 은 열이 가변이라 `Forms`·`Mapping`·`XlsxFiller` 를 안 거친다 — `XlsxWriter` 로 `18_테이블대응용프로그램상관도.xlsx`. `docs` 에 `18` 이 있을 때만 다룬다(기본 01~11 불변) — `analyzeRunId` 없으면 `skipped`. 화면은 분석 실행을 고르면 18 체크를 켠다. `AnalyzeStore` 는 `App.java` 에서 넘긴다(허용). `SmokeHtmlUnitTest:159` 문서 체크 수 11 → 12(허용). 테스트의 분석 실행은 `AnalyzeRoutesTest:50-51` 방식(픽스처 복사 → 실행) |
| 6-13 | V 번호는 `ls src/main/resources/db/migration` 의 다음 번호. `AnalyzeRunner.Result` 에 joins — `AnalyzeStoreTest:41` 생성자 수정 허용. egov 수치는 **새 골든** `golden/corpus/analyze-egov-joins.json` 에(`analyze-egov*.json` 기존 파일은 불변 — 행의 「새 항목만 더해진다」 를 이것으로 바꾼다). `AnalyzeCorpusTest` 는 `egov()` 를 안 고치고 `@Test egovJoins()` 를 더한다 |
| 2-19 | 「관계 후보」 는 `00_작성안내.xlsx` 셋째 시트(2-13 의 여러 시트 메서드). `pg-04` 가 다시 바뀐다(「근거」 열) |
| V-24 | `@Order(8) deliverableBuilds` — 00·01~07·09~11 을 임시 폴더에(05~07 의 사전은 `commentDdlRuns` 처럼 `DictStore.importMoi`). 08(코드표 선택·접속 입력)·16·17·18 은 뺀다. 건수는 공유 `golden` 맵 키 `deliverable` → `db-<판>.json`(따로 `deliverable-<판>.json` 을 안 만든다). 강·약 관계 수는 `AnalyzeCorpusTest` 새 `@Test egovRelations()` — `com_DDL_oracle.sql` 을 `DdlReader` 로 읽은 스냅샷 + 조인 → `golden/corpus/deliverable-egov-relations.json`(컨테이너 없음) |

- **검증(번들)**: `--full` 초록 · `git diff origin/main --stat -- src/test/resources/golden/corpus/analyze-egov*.json src/test/resources/golden/analyze/java-graph.json` 에 기존 파일 변화 0(새 파일만).

### 번들 25 — 가지 `work/2026-10-05-b25`

행 순서 `6-14 → 6-15 → 6-16 → V-25`. 직렬 의존. 새 마이그레이션 없음.

| 행 | 보정 |
|---|---|
| 6-14 | 입력 타입은 `kr.ejg.toolbox.core.check.Source`(`JavaGraph.Source` 는 없다). static import·타입 인자는 `JpaIndex` 가 직접 읽는다(`JavaGraph` 는 둘 다 버린다) |
| 6-15 | `JavaGraph.scan(java, naming, JpaIndex jpa)` 오버로드 — 옛 2칸은 빈 색인으로 위임(호출 `AnalyzeRunner:109`·`JavaGraphTest:35`). **`Graph`·`Program`·`Stmt` 레코드에 필드를 더하지 않는다**(`java-graph.json` 불변). jpa 문장 id = 저장소 **FQCN** + `.메서드`(매퍼와 같은 꼴). 저장소 필드 호출 해석: ① 저장소 인터페이스에 선언됐거나 파생 이름(`findBy…` 등)이면 jpa 문장 ② `*Custom` 상위 인터페이스의 메서드면 `<Custom>Impl` 또는 `<저장소>Impl` 클래스(소스에 있으면)로 따라 들어간다 ③ 없으면 `unresolved`. `@Repository` 클래스가 `QuerydslRepositorySupport` 를 상속하거나 `EntityManager`·`JPAQueryFactory` 필드를 가지면 MyBatis sink(333·378-381) 판정에서 뺀다. 이름 충돌의 「모듈」 규칙은 `JpaIndex` 에만 — `JavaGraph.lookup`(287-295)은 안 고친다. 새 종류는 JPA 표지가 있을 때만 난다(`GeneratorCorpusTest` 109-113 미해결 0 유지) |
| 6-16 | qdsl 문장 = `Stmt("<FQCN>.<메서드>#qdsl", "qdsl")`, 표·CRUD 는 `JpaIndex` 의 곁 등록부(`recordQdsl(id, refs)`/`qdslRefs(id)`)에 — 레코드 불변. `AnalyzeRunner` 가 `jpa`·`qdsl` 을 색인에서 푼다(수정 허용). 필드 수신자가 아닌 체인 호출(343-352 가 건너뛴다)은 새 갈래로 |
| V-25 | 레시피는 `design/14-ui-demo.md` 528-529(shopizer 3.2.7@`6a4a0a65…`, egov-msa@`4f5a895b…`). `corpus-fetch.sh` `SOURCES` 배열·`corpus/SOURCES.md` 에 더하고 D6 대로 받는다(받는 곳 `C:/workspace/toolbox-corpus/<이름>`, MANIFEST 는 스크립트가 다시 쓴다). `AnalyzeCorpusTest` 에 `@Test shopizer()`·`egovMsa()` 를 더한다(`egov()` 불변). JPQL A 가 10 을 넘으면 U-6 사다리(동사 + 엔티티 표). egov-msa 의 같은 이름 서비스 인터페이스로 생기는 `ambiguous` 는 B 목록 |

- **검증(번들)**: `--full` 초록 · `golden/analyze/{java-graph,mapper-index,sql-tables,jsp-links}.json`·`golden/corpus/analyze-egov*` 기존 파일 변화 0.

### 번들 26 — 가지 `work/2026-10-05-b26`(잔손)

| 행 | 무엇 | 대상·검증 |
|---|---|---|
| 1-24 | PLAN 행대로. `Scope.isFiltered()` 에 `@JsonIgnore` 필수(없으면 scope JSON 에 `filtered` 가 실려 `SnapshotStore:212` 읽기가 깨진다). `prepareStatement` 금지 시험은 `SourceFilesTest`(17-22) 방식 grep — 허용 자리 `VendorMetaSource` 하나 | `SnapshotStoreTest`·새 시험 초록 |
| 1-25 | PLAN 행대로. 서버는 4-13 방식으로 띄운다 — `bash scripts/mvn.sh -q -B package -DskipTests` → scratchpad 에 임시 프로필(H2 mem, `INIT=CREATE TABLE …`)·data 폴더 → `java -jar target/app.jar serve --port 41791 --no-browser --data-dir … --profiles-dir … --profile smk` → `POST /api/meta/snapshot {"connId":"h2"}` → `node scripts/puppeteer/smoke-devtools.js http://127.0.0.1:41791` → 서버 종료 | 전부 통과를 이력에 |
| 1-26 | PLAN 행대로(README 한 줄) | doc-lint |
| 1-27 | 가림 한 번(PR #40) — `ConnectionRegistry.test` 실패 분기가 `message`(가린 값)에 `"\n→ " + 안내` 를 잇는다(`mask` 호출 하나). `ConnectionRegistryTest` 에 안내가 붙는 경로(url `jdbc:nosuch:x` + 비밀번호 설정)에서 응답에 비밀번호 없음·「→ 」 있음 | 그 시험 초록 |
| 4-15 | 덮어쓰기 확인 단언(PR #41) — `SmokeHtmlUnitTest.jspFormatterFolderBatch`: `wc.setConfirmHandler((p, m) -> false)` 로 덮어쓰기 → 파일 바이트 불변 → `true` 로 바꿔 → 「덮어씀 2/2」. 같이: 테스트 이름 `templatesListsBothSets`→`templatesListsAllSets`, `egov35AndEgov5Golden`→`setsGolden`, 288-291 javadoc 옛 낱말 | 스모크 초록 |
| 4-16 | INSERT 결과 칸 분리 JUnit(PR #41) — `ToolsFolderTest`: `dev_tools_ext.js` 의 `insRun` 본문에 `dummy_out` 없음·`ins_out` 있음, `dev_tools.html` 에 `id="ins_out"`·`id="dummy_out"` 각 1 | 초록 |
| 4-17 | 적대 픽스처(PR #41) — `fixtures/table/excel-hostile.html`(셀 안 `<script>`·`<img onerror>`·글자 `<b>`·따옴표 든 속성, 머리 주석에 손 픽스처) → `tableBuilderPastesExcelClipboard` 에 단언: 정리한 html 에 `<script`·`<img`·`onerror` 없음, `importHtml` 뒤 `document.querySelectorAll('#grid script, #grid img').length === 0` | 초록 |

### 끝 — 사람 몫 정리

- `PROGRESS.md` 「현재 상태」 의 「사람이 할 것」 을 한 번에 다시 쓴다(번들 26 마무리 문서 커밋에 포함): 항목마다 무엇·왜·명령/화면 절차. 넣을 것 — 실브라우저 확인 목록(JSP 포매터 폴더 탭·INSERT 탭·엑셀 붙여넣기·18 상관도·JPA 표본 → CRUD 탭) · 엑셀 실물 클립보드 · **별표2 엔터티·애트리뷰트 항목 원문(2-17)** · portfolio 지시문 넷(순수본 결함 셋 + table_builder) · meta.go.kr 양식 · 반입 전날 절차 · dismiss 한 CodeQL 경보 다시 보기 · wip/ 가지.
- 마지막 답: 번들별 PR·머지 커밋 · 계획 밖 결정 전부 · wip/ 로 뺀 행과 이유 · 새로 선 행 · dismiss 한 경보 · 사람 몫.

## 5. 금지 사항

- `pure/`·portfolio 를 고치지 않는다. 새 의존성을 넣지 않는다(pom·m2 불변).
- 적용된 마이그레이션 `V001`·`V002`·`V003` 을 고치지 않는다 — 새 V 파일만.
- 「불변」 골든을 갱신하지 않는다: `golden/meta/diff-*.json` · `golden/analyze/java-graph.json` · `golden/corpus/analyze-egov*` 기존 파일 · 최신판 `db-{postgres,maria,mssql,oracle}*` 는 새 키가 느는 것 말고 변화 없음.
- 테스트를 고쳐 통과시키지 않는다. 고쳐도 되는 것은 `design/14` 5장이 이름 댄 것 + 이 문서 「보정」 이 「허용」 이라 적은 것뿐.
- `verify.sh` 와 `git commit`, `verify.sh --full` 과 `git push` 를 한 명령으로 잇지 않는다. 빨간 트리를 `work/*` 에 안 올린다. main 에 직접 push 하지 않는다. `--no-verify`·훅 끄기·`.claude/settings.json`·`CLAUDE.md` 수정 금지.
- `gh pr checks` 를 직접 부르지 않는다. 체크가 덜 끝난 PR 을 머지하지 않는다. `AskUserQuestion` 금지(D10).
- 2-17 을 추측 이름으로 만들지 않는다. 접속 안내·열 이름을 추측으로 더하지 않는다.
- 다른 프로젝트 컨테이너·프로세스를 건드리지 않는다. 내가 띄운 서버·JVM·Monitor 는 내가 끈다.
- 로그·H2·골든에 사용자 코드 본문·비밀번호를 남기지 않는다(규칙 2·3). `127.0.0.1` 밖 런타임 호출을 만들지 않는다.

## 6. 최종 검증

- `gh pr list --state all --limit 6` — 번들 23·24·25·26 PR 이 `MERGED`(막힌 것은 D8 대로 끝 보고에).
- main 에서 `git log --oneline -8`, `git status` 깨끗, `git branch --list 'wip/*'` 를 끝 보고에.
- 마지막 번들의 `--full` 초록(시험 수·소요 시간을 이력에) · `bash scripts/gate-probe.sh` 게이트 여덟.
- `grep -cE "^\| (1-19|1-20|1-21|1-22|1-23|V-23|2-10|2-11|2-12|2-13|2-14|2-15|2-16|2-18|6-13|2-19|V-24|6-14|6-15|6-16|V-25|1-24|1-25|1-26|1-27|4-15|4-16|4-17) .*완료" PLAN.md` = 28(빠진 행은 끝 보고와 맞아야 한다).
- 회귀: `DeliverableRoutesTest`·`AnalyzeCorpusTest`·`SmokeHtmlUnitTest`·방언 표본 아홉.

## 7. 중단 조건

밤에는 「멈추고 보고」 가 곧 정지라 D4 로 바꿨다. **정말 멈추는 것은 넷뿐**:

1. 번들 26 까지 다 쳤다(끝 보고).
2. 저장소가 깨졌다 — `git status` 가 예상 못 한 상태(충돌·detached·남의 변경)이고 `git stash` 로도 깨끗해지지 않는다.
3. 금지 사항을 어기지 않고는 **어느 행도** 못 간다(예: main 의 CI 가 빨개 모든 PR 이 막힘).
4. 같은 도구 실패(Docker 못 켬·디스크·인증 만료)가 번들 둘에 걸쳐 되풀이된다.

멈출 때 보고: 어느 번들·행 · 무엇이 예상과 달랐나 · 선택지. 그 전에 진행 중 작업물은 `wip/<청크>` 에 커밋한다.

## 8. 불확실 항목 — 2026-10-05 실측으로 전부 닫음

| # | 무엇 | 어떻게 닫았나 | 결과 → 반영 |
|---|---|---|---|
| ① | `snap_constraint.kind` 값 제한 | `V001__init.sql:85-100` | `VARCHAR(10)`, CHECK 없음 → `CK` 그대로 들어간다. `name` 은 `NOT NULL` — 이름 없는 CHECK 는 `CK_<순번>` |
| ② | 드라이버 FK 규칙 | 컨테이너 다섯 실측(`ZC` 표, `ON DELETE CASCADE`) | Oracle 23·11g(ojdbc11 23.8): `DELETE_RULE=0`(CASCADE) · `UPDATE_RULE=NULL` → 갱신규칙 빈칸. MariaDB 11·MySQL 8.0·5.7: delete 0 · update 1(RESTRICT). `wasNull()` 필수 확인 |
| ③ | 드라이버 인덱스 정렬 | 같은 실측(`ZC_IX (V DESC, PID)`) | MariaDB·MySQL 8.0: `ASC_OR_DESC` `D`·`A` 정상. MySQL 5.7: DESC 를 무시해 `A`(판의 한계 — 그대로 싣는다). **Oracle: `ASC_OR_DESC` 늘 null, DESC 컬럼 이름이 `SYS_NC00004$`** → 1-20 에 Oracle 벤더 갈래 추가(아래) |
| ④ | CHECK 딕셔너리 꼴 | 같은 실측 | Oracle 23·11g: `USER_CONSTRAINTS.SEARCH_CONDITION` 이 `getString` 으로 읽힌다(LONG 문제 없음), 값 `V > 0`. MariaDB: `CHECK_CONSTRAINTS.TABLE_NAME` 있음, 값 `` `V` > 0 ``. MySQL 8.0: `TABLE_NAME` 없음(1054) → `TABLE_CONSTRAINTS` 조인 꼴, 값 `` (`V` > 0) ``. MySQL 5.7: 표가 없다(1109) → 물러섬 경고 `checks`(옛 판 B 목록) |
| ⑤ | 용량 | 같은 실측 | Oracle 11g `USER_SEGMENTS` 합 327680 · Oracle 23 은 빈 표에 세그먼트가 안 생겨 0(deferred segment creation) — 0 은 그대로 `0.0 MB` |
| ⑥ | `make-example-templates.sh` | 스크립트 읽음 | `mvn.sh package` → `java -jar target/app.jar make-templates`, JDK 경로 `C:/Program Files/Java/jdk-17.0.19`(있다) — 그대로 돈다 |
| ⑦ | gate-probe 패치 다시 뜨기 | `gate-probe.sh` 읽음(절차 글 없음) | 「패치 갱신 필요」 면: 패치의 `-`/`+` 줄을 지금 파일에 손으로 똑같이 바꾼다 → `git diff -- <그 파일> > scripts/probes/<이름>.patch` → `git checkout -- <그 파일>` → `bash scripts/gate-probe.sh <이름>` 이 「산다」 → 패치만 커밋 |
| ⑧ | github 닿는가·크기 | `git ls-remote`·`gh api` | 닿는다. shopizer `refs/heads/3.2.7` = `6a4a0a65…`(설계 SHA 와 같다), Apache-2.0, 저장소 566MB — 얕은 fetch 라 수백 MB 캐시(디스크 611GB 여유) |
| ⑨ | CI `review` 가 문서만 바뀐 push 에도 도는가 | PR #41 코멘트 시각 | 돈다 — `paths-ignore` 는 PR 전체 파일을 본다. 처분 커밋(15:34) 뒤 코멘트가 갱신됐다(15:39). 처분 뒤에는 코멘트 `updated_at` 이 마지막 push 보다 뒤인지 보고 다시 읽는다 |
| ⑩ | JPQL 실패 수·egov-msa `ambiguous` 수 | 결정 | 첫 실행 값을 baseline 으로 얼린다(행대로) |
| ⑪ | `--full` 소요 | 결정 | 60분 사다리(행대로) |
| ⑫ | 토큰 창 | 못 잰다 | D7 |

**④·③ 에서 바뀐 보정**
- 1-20 Oracle: `OracleMetaSource` 가 `loadIndexes` 를 덮어 `vendor("sorts", …)` 로 `ALL_IND_COLUMNS`(`INDEX_NAME`·`COLUMN_NAME`·`COLUMN_POSITION`·`DESCEND`) + `ALL_IND_EXPRESSIONS`(`COLUMN_EXPRESSION`) 를 읽는다 — `DESCEND='DESC'` 면 `DESC`, 이름이 `SYS_NC%$` 면 표현식에서 큰따옴표를 뗀 글로 바꾼다(`SYS_NC00004$` → `V`). 실패하면 JDBC 값(정렬 빈칸) + 경고. `VendorFallbackTest` 기대 집합에 `sorts`. Oracle 방언 테스트 `metaMore` 에 `ZZ_META_MORE` DESC 인덱스 단언(이름 `V`·정렬 `DESC`).
- 1-22 MySQL: MariaDB 꼴(`TABLE_NAME`) 먼저 → 1054 면 조인 꼴 → 1109(5.7) 면 물러섬. 순서 그대로.
