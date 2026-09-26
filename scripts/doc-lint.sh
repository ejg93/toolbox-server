#!/usr/bin/env bash
# 존댓말 금지(CLAUDE.md 「글 작성 규칙」)를 기계로 잡는다. portfolio `scripts/doc-lint.sh` 의 패턴을 옮겼다.
#
#   bash scripts/doc-lint.sh <파일...>   그 파일들의 「고친 줄」만 본다(HEAD 대비 추가된 줄. 미추적이면 전체). 걸리면 exit 1
#   bash scripts/doc-lint.sh             md·html 전체를 훑는다
#
# md 는 백틱·「」 안을 걷어낸다(인용). html 은 문자열이 곧 화면 문구라 안 걷는다.
# 해요체는 명사(「필요」「개요」)와 부딪혀 문장부호 앞으로 묶는다.
set -uo pipefail
cd "$(dirname "$0")/.."
PAT_HON='(습니다|읍니다|합니다|입니다|하세요)'
PAT_YO='(어요|아요|에요|예요|세요|셔요|지요|네요|군요|거든요|는데요|까요|나요|죠)[.!?…]'
PAT="$PAT_HON|$PAT_YO"

in_scope() {
  case "$1" in
    pure/*|m2/*|target/*|data/*|out/*|logs/*|node_modules/*) return 1 ;;
    *.md|*.html) return 0 ;;
    *) return 1 ;;
  esac
}
strip_quotes() { # md 만 — 백틱·「」 안을 지운다
  case "$1" in *.md) sed -E 's/`[^`]*`//g; s/「[^」]*」//g' ;; *) cat ;; esac
}

fail=0
check_lines() { # 파일, 줄 입력(stdin)
  local f=$1 hits
  hits=$(strip_quotes "$f" | grep -nE "$PAT" || true)
  [ -z "$hits" ] && return 0
  echo "[존댓말] $f"; echo "$hits" | head -20; fail=1
}

if [ $# -gt 0 ]; then
  for f in "$@"; do
    [ -f "$f" ] || continue
    in_scope "$f" || continue
    if git ls-files --error-unmatch "$f" >/dev/null 2>&1; then
      git diff HEAD -U0 -- "$f" | grep -E '^\+[^+]' | sed 's/^+//' | check_lines "$f"
    else
      check_lines "$f" < "$f"
    fi
  done
else
  while IFS= read -r f; do
    in_scope "$f" || continue
    check_lines "$f" < "$f"
  done < <(git ls-files '*.md' '*.html' 2>/dev/null; git ls-files --others --exclude-standard '*.md' '*.html' 2>/dev/null)
fi
exit "$fail"
