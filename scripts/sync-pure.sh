#!/usr/bin/env bash
# portfolio 의 순수본(toolbox.html + tools/*.html)을 `pure/` 로 끌어온다. portfolio 가 원본이고 여기는 동결본이다(CLAUDE.md 구역 표).
# 복사 뒤 SHA-256 을 `pure/MANIFEST` 에 남기고, 다시 돌리면 달라진 파일만 보고한다. `PureFrozenTest` 가 이것과 대조한다(0-34).
# 해시는 CR 을 뺀 LF 기준 — Windows 작업 사본(CRLF)과 CI(LF)가 같은 값을 낸다.
set -uo pipefail
cd "$(dirname "$0")/.."
SRC="${TOOLBOX_SRC:-C:/workspace/portfolio/frontend/public/toolbox}"
[ -d "$SRC/tools" ] || { echo "원본이 없다: $SRC"; exit 1; }
mkdir -p pure/tools
old=$(cat pure/MANIFEST 2>/dev/null || true)
# portfolio 가 git 이면 커밋된 판(HEAD)만 가져온다 — 작업 트리를 복사하면 그쪽의 커밋 안 된 수정이 동결본에 섞인다
# (번들 12 PR #24 리뷰: portfolio 작업 트리의 한 글자 `{n` 이 pure/tools/dev_tools.html 에 들어왔다)
if git -C "$SRC" rev-parse --show-toplevel >/dev/null 2>&1; then
  pre=$(git -C "$SRC" rev-parse --show-prefix)
  rev=$(git -C "$SRC" rev-parse --short HEAD)
  dirty=$(git -C "$SRC" -c core.quotepath=false status --porcelain -- toolbox.html tools)
  if [ -n "$dirty" ]; then
    echo "주의: portfolio 작업 트리에 커밋 안 된 수정이 있다 — 커밋된 판($rev)만 가져온다"
    echo "$dirty"
  fi
  git -C "$SRC" show "HEAD:${pre}toolbox.html" > pure/toolbox.html || exit 1
  # `ls-tree HEAD:경로` 꼴은 Git Bash 의 경로 변환에 걸려 빈 목록이 나온다 — cwd 기준 꼴로
  git -C "$SRC" -c core.quotepath=false ls-tree --name-only HEAD tools/ | grep '\.html$' | while IFS= read -r n; do
    git -C "$SRC" show "HEAD:${pre}$n" > "pure/$n" || exit 1
  done || exit 1
  echo "원본: portfolio $rev"
else
  cp "$SRC/toolbox.html" pure/
  cp "$SRC"/tools/*.html pure/tools/
fi
(cd pure && find . -name '*.html' -type f | LC_ALL=C sort | while IFS= read -r f; do
  echo "$(tr -d '\r' < "$f" | sha256sum | cut -d' ' -f1)  ${f#./}"
done) > pure/MANIFEST
if [ -n "$old" ]; then
  # pipefail 아래에선 diff 가 1 을 내 `|| echo` 가 늘 탔다 — 바뀐 줄을 받아 비었을 때만 「없음」(0-40)
  changed=$(diff <(echo "$old") pure/MANIFEST | grep '^[<>]' | sed 's/^</이전:/; s/^>/지금:/' || true)
  if [ -n "$changed" ]; then echo "$changed"; else echo "달라진 파일 없음"; fi
else
  echo "MANIFEST 생성: $(wc -l < pure/MANIFEST) 파일"
fi
