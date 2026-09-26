#!/usr/bin/env bash
# 건드린 것이 무엇을 돌릴지 정한다. `origin/main` 대비 레인 지문(`verify-fingerprint.sh`)이 다른 레인만 돈다.
# ProjectShop `scripts/verify.sh` 에서 레인만 바꿔 옮겼다(2026-09-26).
#
#   bash scripts/verify.sh          빠른 도장 — java: `db` 태그 뺀 테스트 / tools: 문법·훅 회귀 / docs: 존댓말
#   bash scripts/verify.sh --full   full 도장 — java: 컨테이너 테스트까지 `verify` / tools·docs 는 같다
#
# 청크를 닫을 땐 빠른 도장이면 되고(Stop hook), push 앞엔 full 이어야 한다(push hook).
# 같은 지문은 두 번 안 돈다 — 레인의 지금 지문이 도장에 요청 단계 이상으로 있으면 건너뛴다.
# 통과하면 `.git/verify-stamp` 에 「레인 지문 단계」를 적는다.
set -uo pipefail
cd "$(dirname "$0")/.."

level=fast
[ "${1:-}" = "--full" ] && level=full

git rev-parse -q --verify origin/main >/dev/null || { echo "origin/main 이 없다 — 기준이 없어서 못 잰다"; exit 1; }

tmp=$(mktemp); trap 'rm -f "$tmp"' EXIT
GIT_INDEX_FILE="$tmp" git read-tree HEAD
GIT_INDEX_FILE="$tmp" git add -A . 2>/dev/null
tree=$(GIT_INDEX_FILE="$tmp" git write-tree)

fp_work=$(bash scripts/verify-fingerprint.sh "$tree"); fp_main=$(bash scripts/verify-fingerprint.sh origin/main)
changed() { [ "$(echo "$fp_work" | grep "^$1 ")" != "$(echo "$fp_main" | grep "^$1 ")" ]; }
fp_of() { echo "$fp_work" | grep "^$1 " | cut -d' ' -f2; }
st="$(git rev-parse --git-dir)/verify-stamp"
stamped() { # 레인 — 지금 지문이 요청 단계 이상으로 도장에 있나
  local h; h=$(fp_of "$1")
  grep -qx "$1 $h full" "$st" 2>/dev/null && return 0
  [ "$level" = fast ] && grep -qx "$1 $h fast" "$st" 2>/dev/null && return 0
  return 1
}

fail=0
lv_java=$level; lv_tools=$level; lv_docs=$level

if changed java && ! stamped java; then
  if [ ! -f pom.xml ]; then
    echo "== java 레인: pom.xml 이 없다"; fail=1
  elif [ "$level" = full ]; then
    echo "== java 바뀜 → mvn verify (컨테이너 포함)"
    bash scripts/mvn.sh -q -B verify || fail=1
  else
    echo "== java 바뀜 → mvn test (db 태그 제외)"
    bash scripts/mvn.sh -q -B -DexcludedGroups=db test || fail=1
  fi
  # 반입된 마이그레이션 불변(0-20). 반입 전엔 기준점이 없어 통과
  bash scripts/migration-immutable.sh || fail=1
fi

if changed tools && ! stamped tools; then
  echo "== tools 바뀜 → 셸 문법·settings.json·훅 회귀"
  for f in scripts/*.sh scripts/hooks/*.sh; do bash -n "$f" || { echo "문법: $f"; fail=1; }; done
  node -e 'JSON.parse(require("fs").readFileSync(".claude/settings.json","utf8"))' || { echo "settings.json 파싱 실패"; fail=1; }
  bash scripts/hooks-test.sh || fail=1
fi

if changed docs && ! stamped docs; then
  echo "== docs 바뀜 → doc-lint"
  bash scripts/doc-lint.sh CLAUDE.md PLAN.md PROGRESS.md README.md || fail=1
fi

[ "$fail" -ne 0 ] && { echo "빨강($level)"; exit 1; }

for d in java tools docs; do
  h=$(fp_of "$d")
  if changed "$d"; then eval "echo \"$d $h \$lv_$d\""; else echo "$d $h full"; fi
done > "$st"
echo "초록($level). 도장: $st"
