#!/usr/bin/env bash
# 트리 하나(HEAD·origin/main·임시 트리)의 레인별 지문을 낸다. 출력: `java <sha>` / `tools <sha>` / `docs <sha>`.
# ProjectShop `scripts/verify-fingerprint.sh` 에서 레인만 바꿔 옮겼다(2026-09-26).
#
# 빌드·테스트 결과를 바꾸는 경로만 고른다. 경로를 더할 때 여기 한 곳만 고친다.
# 받은 이름을 트리 해시로 먼저 푼다 — Git Bash 가 `origin/main:.claude/settings.json` 을 경로로 보고 슬래시·콜론을 바꾼다.
# 경로마다 `rev-parse` 를 안 부르고 `git cat-file --batch-check` 한 번으로 받는다(윈도에서 호출당 0.1초).
set -uo pipefail
cd "$(dirname "$0")/.."
tree=$(git rev-parse -q --verify "${1:-HEAD}^{tree}") || { echo "트리를 못 풀었다: ${1:-HEAD}" >&2; exit 1; }
listed=(
  src pom.xml .mvn mvnw mvnw.cmd config templates mappings
  scripts .claude/settings.json .claude/prompts .github
  CLAUDE.md PLAN.md PROGRESS.md README.md
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
