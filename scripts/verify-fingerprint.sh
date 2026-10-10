#!/usr/bin/env bash
# 트리 하나(HEAD·origin/main·임시 트리)의 레인별 지문을 낸다. 출력: `java <sha>` / `tools <sha>` / `docs <sha>` / `db <sha>` / `corpus <sha>`.
# ProjectShop `scripts/verify-fingerprint.sh` 에서 레인만 바꿔 옮겼다(2026-09-26). db·corpus 레인은 설계 21(2026-10-10).
#
# 빌드·테스트 결과를 바꾸는 경로만 고른다. 경로를 더할 때 여기 한 곳만 고친다 — LANE_DB·LANE_CORPUS 배열에 넣으면 listed 에도 든다.
# 받은 이름을 트리 해시로 먼저 푼다 — Git Bash 가 `origin/main:.claude/settings.json` 을 경로로 보고 슬래시·콜론을 바꾼다.
# 경로마다 `rev-parse` 를 안 부르고 `git cat-file --batch-check` 한 번으로 받는다(윈도에서 호출당 0.1초).
set -uo pipefail
cd "$(dirname "$0")/.."
tree=$(git rev-parse -q --verify "${1:-HEAD}^{tree}") || { echo "트리를 못 풀었다: ${1:-HEAD}" >&2; exit 1; }

# db 레인(설계 21) — DB 에 붙는 코드만. 바뀌면 push 앞 `verify.sh --db`(컨테이너 아홉, 약 17분).
# 스니펫 글·pure·골든·산출물 양식(mappings·templates)은 뺀다 → 반입 전 --full 에서만(package.sh 가 release 도장을 본다).
# 컨테이너 초기화 SQL(sample/*.sql)·표본 지문(corpus/MANIFEST — 표본의 DB 스크립트를 컨테이너에 넣는다)은 DB 입력이라 넣는다.
# LANE_DB_SKIP — 공용 바닥(H2·작업·프로필·파일·글)·산출물·파서. 여기만 바뀌면 빠른 시험 + CI 의 db 시험(메타 수집 넷 등)이 잡는다.
# VerifyLanesTest 가 db 꼬리표 시험의 core 닫힘(LANE_DB_SKIP 은 안 들어감)·시험 소스가 이 배열에 다 있는지 잰다.
# 배열은 `LANE_DB=(` 줄부터 `)` 줄까지 한 토큰이 경로 하나 — 시험이 그대로 읽는다. web·cli·src 통째는 금지.
LANE_DB_SKIP="core/db core/job core/profile core/fs core/text core/dict core/deliverable core/report core/analyze core/check core/vcs"
LANE_DB=(
  src/main/java/kr/ejg/toolbox/core/meta
  src/main/java/kr/ejg/toolbox/core/dialect
  src/main/java/kr/ejg/toolbox/core/conn
  src/main/java/kr/ejg/toolbox/core/sqlrun
  src/main/java/kr/ejg/toolbox/core/quality
  src/main/java/kr/ejg/toolbox/core/gen
  src/main/java/kr/ejg/toolbox/core/logical
  src/main/resources/db
  src/main/resources/conn
  src/main/resources/quality
  src/main/resources/gen
  src/main/resources/logical
  pom.xml
  .mvn
  src/test/resources/sample/mariadb.sql
  src/test/resources/sample/mssql.sql
  src/test/resources/sample/oracle.sql
  src/test/resources/sample/postgres.sql
  corpus/MANIFEST
  src/test/java/kr/ejg/toolbox/core/conn
  src/test/java/kr/ejg/toolbox/core/dialect
  src/test/java/kr/ejg/toolbox/core/meta
  src/test/java/kr/ejg/toolbox/core/quality
  src/test/java/kr/ejg/toolbox/web/InsertGenPostgresTest.java
  src/test/java/kr/ejg/toolbox/web/DbCorpusBase.java
  src/test/java/kr/ejg/toolbox/web/MariaCorpusTest.java
  src/test/java/kr/ejg/toolbox/web/Mssql2017CorpusTest.java
  src/test/java/kr/ejg/toolbox/web/MssqlCorpusTest.java
  src/test/java/kr/ejg/toolbox/web/Mysql57CorpusTest.java
  src/test/java/kr/ejg/toolbox/web/Mysql80CorpusTest.java
  src/test/java/kr/ejg/toolbox/web/Oracle11CorpusTest.java
  src/test/java/kr/ejg/toolbox/web/OracleCorpusTest.java
  src/test/java/kr/ejg/toolbox/web/Postgres12CorpusTest.java
  src/test/java/kr/ejg/toolbox/web/PostgresCorpusTest.java
  src/test/java/kr/ejg/toolbox/DbCorpus.java
  src/test/java/kr/ejg/toolbox/CorpusHr.java
  src/test/java/kr/ejg/toolbox/CorpusFiles.java
  src/test/java/kr/ejg/toolbox/GoldenFiles.java
  src/test/java/kr/ejg/toolbox/web/CorpusNode.java
)
# corpus 레인(설계 21) — 파서·규칙·표본 입력. 바뀌면 push 앞 `verify.sh --corpus`(약 7분). web·cli 는 접착제라 안 넣는다.
# `src/main/resources/tools` 가 들어 화면 JS 를 고치면 깬다 — Js·Strip·JspFmt 표본이 백엔드본 화면 JS 를 돌린다.
LANE_CORPUS_SKIP="core/db core/job core/profile"
LANE_CORPUS=(
  src/main/java/kr/ejg/toolbox/core/analyze
  src/main/java/kr/ejg/toolbox/core/check
  src/main/java/kr/ejg/toolbox/core/text
  src/main/java/kr/ejg/toolbox/core/vcs
  src/main/java/kr/ejg/toolbox/core/fs
  src/main/java/kr/ejg/toolbox/core/gen
  src/main/java/kr/ejg/toolbox/core/logical
  src/main/java/kr/ejg/toolbox/core/dict
  src/main/java/kr/ejg/toolbox/core/deliverable
  src/main/java/kr/ejg/toolbox/core/meta
  src/main/java/kr/ejg/toolbox/core/dialect
  src/main/java/kr/ejg/toolbox/core/sqlrun
  src/main/java/kr/ejg/toolbox/core/quality
  src/main/java/kr/ejg/toolbox/core/report
  src/main/resources/check
  src/main/resources/gen
  src/main/resources/logical
  src/main/resources/dict
  src/main/resources/tools
  pure
  scripts/puppeteer
  corpus/MANIFEST
  src/test/resources/golden/corpus
  src/test/resources/sample
  src/test/java/kr/ejg/toolbox/cli/CliCorpusTest.java
  src/test/java/kr/ejg/toolbox/core/analyze
  src/test/java/kr/ejg/toolbox/core/check
  src/test/java/kr/ejg/toolbox/core/fs
  src/test/java/kr/ejg/toolbox/core/gen
  src/test/java/kr/ejg/toolbox/core/logical
  src/test/java/kr/ejg/toolbox/core/meta
  src/test/java/kr/ejg/toolbox/core/text
  src/test/java/kr/ejg/toolbox/core/vcs
  src/test/java/kr/ejg/toolbox/web/CorpusNode.java
  src/test/java/kr/ejg/toolbox/web/JsCorpusTest.java
  src/test/java/kr/ejg/toolbox/web/JspFmtCorpusTest.java
  src/test/java/kr/ejg/toolbox/web/StripCorpusTest.java
  src/test/java/kr/ejg/toolbox/web/DbCorpusBase.java
  src/test/java/kr/ejg/toolbox/web/MariaCorpusTest.java
  src/test/java/kr/ejg/toolbox/web/Mssql2017CorpusTest.java
  src/test/java/kr/ejg/toolbox/web/MssqlCorpusTest.java
  src/test/java/kr/ejg/toolbox/web/Mysql57CorpusTest.java
  src/test/java/kr/ejg/toolbox/web/Mysql80CorpusTest.java
  src/test/java/kr/ejg/toolbox/web/Oracle11CorpusTest.java
  src/test/java/kr/ejg/toolbox/web/OracleCorpusTest.java
  src/test/java/kr/ejg/toolbox/web/Postgres12CorpusTest.java
  src/test/java/kr/ejg/toolbox/web/PostgresCorpusTest.java
  src/test/java/kr/ejg/toolbox/web/HrSnapshotCorpusTest.java
  src/test/java/kr/ejg/toolbox/CorpusFiles.java
  src/test/java/kr/ejg/toolbox/CorpusFilesTest.java
  src/test/java/kr/ejg/toolbox/DbCorpus.java
  src/test/java/kr/ejg/toolbox/CorpusHr.java
  src/test/java/kr/ejg/toolbox/GoldenFiles.java
  src/test/java/kr/ejg/toolbox/cli/CliFixture.java
  src/test/java/kr/ejg/toolbox/core/db/DbHolder.java
)

listed=(
  src pom.xml .mvn mvnw mvnw.cmd config templates mappings
  scripts .claude/settings.json .claude/prompts .github
  CLAUDE.md PLAN.md PROGRESS.md README.md
  "${LANE_DB[@]}" "${LANE_CORPUS[@]}"
)
declare -A entry
mapfile -t resolved < <(printf "$tree:%s\n" "${listed[@]}" | git cat-file --batch-check='%(objectname)')
for i in "${!listed[@]}"; do
  h=${resolved[$i]:-}
  case "$h" in *" missing"|"") h=- ;; esac
  entry[${listed[$i]}]=$h
done
lane() {
  local name=$1; shift
  local p h text=
  for p in "$@"; do
    if [ -n "${entry[$p]+x}" ]; then h=${entry[$p]}
    else h=$(git rev-parse -q --verify "$tree:$p" 2>/dev/null || echo -)
    fi
    text+="$p $h"$'\n'
  done
  printf '%s %s\n' "$name" "$(printf '%s' "$text" | git hash-object --stdin)"
}
lane java  src pom.xml .mvn mvnw mvnw.cmd config templates mappings  # 예시 양식·매핑은 XlsxFillerTest 가 읽는다(2-4)
lane tools scripts .claude/settings.json .claude/prompts .github
lane docs  CLAUDE.md PLAN.md PROGRESS.md README.md
lane db    "${LANE_DB[@]}"
lane corpus "${LANE_CORPUS[@]}"
