#!/usr/bin/env bash
# PreToolUse(Bash·PowerShell). `git push` 를 잡는다 — main 직접 금지, 현재 가지가 main 이면 금지,
# 바뀐 레인마다 HEAD 지문의 도장 — java·tools·docs 는 단계 무관(CI 가 push 마다 mvn verify 를 돈다),
# db·corpus 는 돌았어야(`full` 줄). 무거운 검증은 그 코드가 바뀌었을 때만(설계 21, 2026-10-10).
# PreToolUse 라 `verify.sh --full && git push` 를 한 체인으로 내면 막힌다. 따로 낸다.
. "$(dirname "$0")/_tool-input.sh"
c=$(tool_field command)

if printf '%s\n' "$c" | grep -qE '(^|[;&|])[[:space:]]*git push([[:space:]]+-[-A-Za-z=]+)*([[:space:]]+[A-Za-z0-9_./-]+)?[[:space:]]+([A-Za-z0-9_./-]+:)?(refs/heads/)?main([[:space:]]|$)'; then
  echo 'main 에 직접 안 민다(CLAUDE.md 「번들 모드」). work/<날짜> 가지에 밀고 마무리가 PR 을 연다.' >&2
  exit 2
fi

printf '%s\n' "$c" | grep -qE '(^|[;&|])[[:space:]]*git push([[:space:]]|$)' || exit 0

if [ "$(git rev-parse --abbrev-ref HEAD 2>/dev/null)" = main ]; then
  echo '현재 가지가 main 이다. work/<날짜> 가지를 따고 민다(CLAUDE.md 「번들 모드」).' >&2
  exit 2
fi

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
git rev-parse -q --verify origin/main >/dev/null 2>&1 || exit 0
st="$(git rev-parse --git-dir)/verify-stamp"
head=$(bash scripts/verify-fingerprint.sh HEAD)
main=$(bash scripts/verify-fingerprint.sh origin/main)
fail=''
for d in java tools docs; do
  h=$(echo "$head" | grep "^$d ")
  [ "$h" = "$(echo "$main" | grep "^$d ")" ] && continue
  grep -q "^$h " "$st" 2>/dev/null || fail="$fail $d"
done
heavy=''
for d in db corpus; do
  h=$(echo "$head" | grep "^$d ")
  [ -z "$h" ] && continue
  [ "$h" = "$(echo "$main" | grep "^$d ")" ] && continue
  grep -qx "$h full" "$st" 2>/dev/null || heavy="$heavy $d"
done
if [ -n "$fail" ]; then
  echo "검증 도장이 없다:$fail — push 앞엔 bash scripts/verify.sh 가 HEAD 에서 초록이어야 한다." >&2
  exit 2
fi
if [ -n "$heavy" ]; then
  flags=$(for d in $heavy; do printf ' --%s' "$d"; done)
  echo "무거운 레인이 바뀌었는데 안 돌았다:$heavy — bash scripts/verify.sh$flags (db 약 17분 · corpus 약 7분, 표본 폴더 필요). 반입 전엔 --full." >&2
  exit 2
fi
exit 0
