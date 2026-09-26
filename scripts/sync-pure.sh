#!/usr/bin/env bash
# portfolio 의 순수본(toolbox.html + tools/*.html)을 `pure/` 로 끌어온다. portfolio 가 원본이고 여기는 동결본이다(CLAUDE.md 구역 표).
# 복사 뒤 SHA-256 을 `pure/MANIFEST` 에 남기고, 다시 돌리면 달라진 파일만 보고한다.
set -uo pipefail
cd "$(dirname "$0")/.."
SRC="${TOOLBOX_SRC:-C:/workspace/portfolio/frontend/public/toolbox}"
[ -d "$SRC/tools" ] || { echo "원본이 없다: $SRC"; exit 1; }
mkdir -p pure/tools
old=$(cat pure/MANIFEST 2>/dev/null || true)
cp "$SRC/toolbox.html" pure/
cp "$SRC"/tools/*.html pure/tools/
(cd pure && find . -name '*.html' -type f | sort | xargs sha256sum) > pure/MANIFEST
if [ -n "$old" ]; then
  diff <(echo "$old") pure/MANIFEST | grep '^[<>]' | sed 's/^</이전:/; s/^>/지금:/' || echo "달라진 파일 없음"
else
  echo "MANIFEST 생성: $(wc -l < pure/MANIFEST) 파일"
fi
