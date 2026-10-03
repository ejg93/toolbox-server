#!/usr/bin/env bash
# Stop·UserPromptSubmit. 고아가 된 Maven 실행을 끈다(0-42) — `scripts/mvn.sh` 의 감시 루프째 죽은 경우의 뒷문.
# `.git/mvn-runs/<mvn.sh PID>` = 「맨 위 셸 PID 시작 epoch」. mvn.sh 와 맨 위 셸이 둘 다 살아 있으면 정상 실행이라 둔다.
# 하나라도 죽었으면 그 시각 뒤에 뜬 이 저장소 Maven·surefire JVM 을 끄고(`reap-maven.sh`) 기록을 지운다.
#   bash orphan-reap.sh stop     끈 것이 있으면 exit 2 — 알리고 보고하게 한다
#   bash orphan-reap.sh prompt   끈 것이 있으면 stdout 으로 알린다(프롬프트는 막지 않는다)
# 시험은 TOOLBOX_REAP 로 끄는 명령을 바꾼다.
mode=${1:-stop}
. "$(dirname "$0")/_tool-input.sh"
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
[ -d .git/mvn-runs ] || exit 0
reap=${TOOLBOX_REAP:-bash scripts/reap-maven.sh}
msg=""
for f in .git/mvn-runs/*; do
  [ -f "$f" ] || continue
  read -r root start < "$f" || true
  pid=${f##*/}
  if kill -0 "$pid" 2>/dev/null && kill -0 "${root:-0}" 2>/dev/null; then
    continue
  fi
  killed=$($reap "${start:-0}" "훅 orphan-reap($mode)" | tr '\n' ' ')
  rm -f "$f"
  msg="${msg}고아 Maven 실행을 껐다 — mvn.sh $pid(맨 위 셸 $root) · JVM: ${killed:-없음}. "
done
[ -z "$msg" ] && exit 0
if [ "$mode" = stop ]; then
  [ "$(hook_field stop_hook_active)" = true ] && exit 0
  printf '%s남은 일(도장·push)이 있으면 메모리를 보고 다시 돌린다.\n' "$msg" >&2
  exit 2
fi
printf '%s\n' "$msg"
exit 0
