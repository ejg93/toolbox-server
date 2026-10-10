#!/usr/bin/env bash
# 건드린 것이 무엇을 돌릴지 정한다. `origin/main` 대비 레인 지문(`verify-fingerprint.sh`)이 다른 레인만 돈다.
# ProjectShop `scripts/verify.sh` 에서 레인만 바꿔 옮겼다(2026-09-26). 레인 다섯·무거운 레인 플래그는 설계 21(2026-10-10).
#
#   bash scripts/verify.sh            빠른 도장 — java: `db`·`corpus` 태그 뺀 테스트 / tools: 문법·훅 회귀 / docs: 존댓말.
#                                     db·corpus 레인이 바뀌었는데 안 돌았으면 끝 줄에 알림만(빨강 아님)
#   bash scripts/verify.sh --db       db 레인(컨테이너 아홉 — 메타 수집·접속·품질·INSERT·DDL·마스킹·스니펫 실행, 약 17분)
#   bash scripts/verify.sh --corpus   corpus 레인(실물 표본 — 파서·규칙·화면 JS, 약 7분). --db 와 같이 줄 수 있다
#   bash scripts/verify.sh --full     전부 — 레인 판정과 무관하게 `mvn verify`(컨테이너·실물 표본·SpotBugs, 약 20분). 끝나면 `release <트리> full` 도장 — package.sh 가 본다
#
# 청크를 닫을 땐 빠른 도장이면 되고(Stop hook), push 앞엔 바뀐 레인의 도장 — java·tools·docs 는 빠른, db·corpus 는 돌았어야(push hook).
# 같은 지문은 두 번 안 돈다 — 레인의 지금 지문이 도장에 요청 단계 이상으로 있으면 건너뛴다.
# 통과하면 `.git/verify-stamp` 에 「레인 지문 단계」를 적는다. db·corpus 는 단계가 full 하나(돌았다) — 안 돌았으면 줄이 없다.
set -uo pipefail
cd "$(dirname "$0")/.."

level=fast; want_db=0; want_corpus=0
for a in "$@"; do
  case "$a" in
    --full) level=full ;;
    --db) want_db=1 ;;
    --corpus) want_corpus=1 ;;
    *) echo "모르는 인자: $a — --db · --corpus · --full"; exit 2 ;;
  esac
done

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
ran_full=0   # 이번에 mvn verify 가 전부 돌았다(db·corpus 도 돈 것)

# --full 은 바뀌었든 아니든 전부 — 반입 전 한 번(설계 21 리뷰). 레인 판정은 origin/main 대비라, main 에 이미 들어간
# 「반입 전 --full 로 미룬 입력」(스니펫 글·pure·골든·산출물 양식)은 어느 레인도 안 바뀐 것처럼 보인다. 그래서 여기선 판정을 안 본다
if [ "$level" = full ] || { changed java && ! stamped java; }; then
  if [ ! -f pom.xml ]; then
    echo "== java 레인: pom.xml 이 없다"; fail=1
  elif [ "$level" = full ]; then
    echo "== --full → mvn verify 전부 (컨테이너·실물 표본·SpotBugs, 약 20분)"
    if [ "${CI:-}" = "true" ]; then
      bash scripts/corpus-check.sh; bash scripts/mvn.sh -q -B -DexcludedGroups=corpus verify || fail=1
    elif bash scripts/corpus-check.sh; then
      bash scripts/mvn.sh -q -B verify && ran_full=1 || fail=1
    else
      fail=1
    fi
  else
    echo "== java 바뀜 → mvn test (db·corpus 태그 제외)"
    bash scripts/mvn.sh -q -B -DexcludedGroups=db,corpus test || fail=1
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

# 무거운 레인(설계 21) — 바뀌었고 도장이 없으면: --full 이고 mvn verify 가 돌았으면 그걸로, 플래그가 있으면 그 꼬리표만, 아니면 알림만.
# db 레인 경로는 전부 java 레인(src·pom·.mvn) 안이라 java 가 full 로 찍혀 있으면 db 도 돈 것이다. corpus 는 pure·scripts·MANIFEST 가 밖이라 아니다.
ran_db=0; ran_corpus=0; pending=''
heavy() { # 레인 꼬리표식 분 플래그
  local lane=$1 expr=$2 minutes=$3 want=$4
  changed "$lane" || return 0
  grep -qx "$lane $(fp_of "$lane") full" "$st" 2>/dev/null && return 0
  if [ "$ran_full" = 1 ]; then eval "ran_$lane=1"; return 0; fi
  if [ "$lane" = db ] && grep -qx "java $(fp_of java) full" "$st" 2>/dev/null; then ran_db=1; return 0; fi
  if [ "$lane" = corpus ] && [ "${CI:-}" = "true" ]; then echo "표본 건너뜀(CI) — corpus 레인은 로컬에서"; return 0; fi
  if [ "$want" = 1 ]; then
    echo "== $lane 레인 바뀜 → mvn test -Dgroups='$expr' (약 $minutes분)"
    if bash scripts/corpus-check.sh && bash scripts/mvn.sh -q -B -Dgroups="$expr" test; then eval "ran_$lane=1"; else fail=1; fi
  else
    pending="$pending $lane"
  fi
}
heavy db "db" 17 "$want_db"
heavy corpus "corpus & !db" 7 "$want_corpus"

[ "$fail" -ne 0 ] && { echo "빨강($level)"; exit 1; }

# 이미 full 로 찍힌 지문은 낮추지 않는다(0-41) — `> "$st"` 가 먼저 비우므로 옛 도장을 읽어 둔다.
# 안 그러면 문서만 고친 뒤 빠른 검증 → --full 에서 건너뛰었던 java 레인이 처음부터 다시 돈다
old=$(cat "$st" 2>/dev/null || true)
for d in java tools docs db corpus; do
  h=$(fp_of "$d")
  if ! changed "$d"; then echo "$d $h full"
  elif printf '%s\n' "$old" | grep -qx "$d $h full"; then echo "$d $h full"
  else
    case "$d" in
      db|corpus) [ "$(eval echo "\$ran_$d")" = 1 ] && echo "$d $h full" ;;   # 안 돌았으면 줄 없음 — push hook 이 막는다
      *) eval "echo \"$d $h \$lv_$d\"" ;;
    esac
  fi
done > "$st"
# 반입 도장 — --full 이 이 트리에서 전부 돌았다. package.sh 가 HEAD 트리와 맞춰 본다(레인 판정과 무관)
if [ "$ran_full" = 1 ] || printf '%s\n' "$old" | grep -qx "release $tree full"; then echo "release $tree full" >> "$st"; fi
for d in $pending; do
  case "$d" in
    db) echo "== db 레인 바뀜(DB 에 붙는 코드) — push 앞에 bash scripts/verify.sh --db (컨테이너 아홉, 약 17분, 표본 폴더 필요)" ;;
    corpus) echo "== corpus 레인 바뀜(파서·규칙·화면 JS) — push 앞에 bash scripts/verify.sh --corpus (약 7분, 표본 폴더·node 필요)" ;;
  esac
done
[ -n "$pending" ] && echo "   전부 돌리려면 --full. 반입 전엔 --full 이 필수(package.sh 가 본다)"
echo "초록($level). 도장: $st"
