#!/usr/bin/env bash
# 게이트 검증(0-33) — 게이트를 일부러 부수면 빨개지는지 잰다. ProjectShop gate-probe 를 옮겼다.
# 게이트는 만들 때 한 번 손으로 부숴 봤을 뿐이라, 누가 규칙을 느슨하게 고쳐 늘 초록이 돼도 모른다.
#
#   bash scripts/gate-probe.sh              전부
#   bash scripts/gate-probe.sh <이름>...    골라서
#   bash scripts/gate-probe.sh --self-test  판정기 자체 — 부수지 않는 패치를 대면 「게이트가 죽었다」 가 나와야 한다
#
# 프로브 하나 = 임시 git worktree(HEAD) + scripts/probes/<이름>.patch 적용 + 그 게이트 명령.
#   게이트 빨강 → 통과(게이트가 산다) / 게이트 초록 → 실패(게이트가 죽었다) / 패치가 안 맞음 → 실패(패치 갱신 필요)
# offline-download 는 패치가 아니라 m2/ 를 잠깐 건드린다(추적 안 하는 폴더) — m2/ 가 없으면(CI) 건너뛴다.
set -uo pipefail
R=$(cd "$(dirname "$0")/.." && pwd)
cd "$R"
ALL=(arch-outbound arch-url-open url-password local-only iframe-sandbox pure-frozen migration-immutable offline-download)

gate() { # 프로브 이름 → 게이트 명령(worktree 안에서 돈다)
  case "$1" in
    arch-outbound|arch-url-open) echo "bash scripts/mvn.sh -q -B -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false test" ;;
    url-password) echo "bash scripts/mvn.sh -q -B -Dtest=ProfileStoreTest -Dsurefire.failIfNoSpecifiedTests=false test" ;;
    local-only) echo "bash scripts/mvn.sh -q -B -Dtest=LocalOnlyTest -Dsurefire.failIfNoSpecifiedTests=false test" ;;
    iframe-sandbox) echo "bash scripts/mvn.sh -q -B -Dtest=ToolsFolderTest -Dsurefire.failIfNoSpecifiedTests=false test" ;;
    pure-frozen) echo "bash scripts/mvn.sh -q -B -Dtest=PureFrozenTest -Dsurefire.failIfNoSpecifiedTests=false test" ;;
    migration-immutable|self-test) echo "bash scripts/migration-immutable.sh" ;;
    *) return 1 ;;
  esac
}

marker() { # 빨강이 그 게이트 때문인지 — 게이트 출력에 있어야 할 글자(없으면 다른 까닭의 빨강: 컴파일 실패 등)
  case "$1" in
    arch-outbound|arch-url-open) echo "ArchitectureTest" ;;
    url-password) echo "ProfileStoreTest" ;;
    local-only) echo "LocalOnlyTest" ;;
    iframe-sandbox) echo "ToolsFolderTest" ;;
    pure-frozen) echo "PureFrozenTest" ;;
    migration-immutable|self-test) echo "반입된 마이그레이션을 고쳤다" ;;
  esac
}

fail=0; live=0; skip=0; bad=0
report() { # 이름, 결과(live|dead|stale|skip), 덧말
  case "$2" in live) live=$((live + 1)) ;; skip) skip=$((skip + 1)) ;; *) bad=$((bad + 1)) ;; esac
  case "$2" in
    live) echo "  [통과] $1 — 부수니 빨강(게이트가 산다)" ;;
    skip) echo "  [건너뜀] $1 — $3" ;;
    dead) echo "  [실패] $1 — 부쉈는데 초록: 게이트가 죽었다. $3"; fail=1 ;;
    stale) echo "  [실패] $1 — 패치가 안 맞는다: 패치 갱신 필요(게이트가 죽은 것과 다르다). $3"; fail=1 ;;
  esac
}

probe_patch() { # 이름, 패치 파일
  local name=$1 patch=$2 base wt out
  base=$(mktemp -d)
  wt=$base/wt
  git worktree add -q --detach "$wt" HEAD 2>/dev/null || { report "$name" stale "worktree 를 못 만들었다"; rm -rf "$base"; return; }
  if ! git -C "$wt" apply --whitespace=nowarn "$patch" 2>/dev/null; then
    report "$name" stale "$(basename "$patch")"
  else
    out=$(cd "$wt" && eval "$(gate "$name")" 2>&1)
    if [ $? -eq 0 ]; then
      report "$name" dead "$(echo "$out" | tail -1)"
    elif [[ "$out" == *"$(marker "$name")"* ]]; then
      report "$name" live
    else
      report "$name" stale "빨강이지만 「$(marker "$name")」 가 아닌 까닭: $(echo "$out" | grep -m1 -E 'ERROR|오류' | cut -c1-120)"
    fi
  fi
  git worktree remove --force "$wt" >/dev/null 2>&1 || rm -rf "$wt"
  rm -rf "$base"
  git worktree prune >/dev/null 2>&1
}

probe_offline() { # m2/.mvn-home 의 배포본 폴더 이름을 잠깐 바꾸면 --check 가 빨개야 한다
  local d
  d=$(ls -d m2/.mvn-home/wrapper/dists/*/* 2>/dev/null | head -1)
  if [ -z "$d" ]; then report offline-download skip "m2/.mvn-home 이 없다(CI) — 로컬에서 offline-build.sh 를 한 번 돌린 뒤"; return; fi
  mv "$d" "$d.probe" || { report offline-download stale "배포본 폴더를 못 옮겼다"; return; }
  trap 'rm -rf "'"$d"'"; mv "'"$d"'.probe" "'"$d"'" 2>/dev/null' EXIT
  if bash scripts/offline-build.sh --check >/dev/null 2>&1; then report offline-download dead "받기 흔적을 못 잡았다"; else report offline-download live; fi
  # 래퍼가 그사이 배포본을 새로 받아 같은 이름 폴더를 만든다 — 지우고 되돌린다(안 지우면 mv 가 그 안으로 들어가 한 겹씩 쌓인다, 8-10)
  rm -rf "$d"
  mv "$d.probe" "$d"
  trap - EXIT
}

if [ "${1:-}" = "--self-test" ]; then
  # 기준점만 적고 V001 은 안 고친 패치 — migration-immutable 은 초록이어야 하고 판정기는 그것을 「죽었다」 로 잡아야 한다
  p=$(mktemp)
  awk '/^diff --git /{skip = ($0 ~ /db\/migration\//)} !skip' scripts/probes/migration-immutable.patch > "$p"
  out=$(fail=0; probe_patch self-test "$p"; echo "fail=$fail")
  rm -f "$p"
  echo "$out"
  case "$out" in *"게이트가 죽었다"*"fail=1"*) echo "판정기 초록 — 안 부순 패치를 「죽었다」 로 잡는다"; exit 0 ;; *) echo "판정기 빨강" >&2; exit 1 ;; esac
fi

names=("$@")
[ ${#names[@]} -eq 0 ] && names=("${ALL[@]}")
for n in "${names[@]}"; do
  if [ "$n" = offline-download ]; then probe_offline; continue; fi
  gate "$n" >/dev/null || { echo "모르는 프로브: $n (있는 것: ${ALL[*]})" >&2; exit 2; }
  probe_patch "$n" "$R/scripts/probes/$n.patch"
done
# 끝 줄은 셋을 따로 센다(0-36) — 건너뛴 것을 「산다」 에 넣으면 재지 않은 게이트가 산 것처럼 읽힌다. 건너뜀은 종료 0
summary="산다 $live · 건너뜀 $skip · 실패 $bad"
if [ $fail -ne 0 ]; then echo "$summary — 죽은·낡은 게이트가 있다(위 [실패])" >&2
elif [ $skip -gt 0 ]; then echo "$summary — 건너뛴 게이트는 재지 않았다(위 [건너뜀])"
else echo "$summary — 게이트 $live개 전부 산다"; fi
exit $fail
