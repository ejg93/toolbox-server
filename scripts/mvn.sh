#!/usr/bin/env bash
# `./mvnw` 를 JDK 17 로 부른다. 이 기계의 JAVA_HOME 은 JDK 11 이라 맨몸 mvnw 는 17 코드를 못 컴파일한다(java-home-guard 가 막는다).
# 리눅스 러너(CI)는 setup-java 가 JAVA_HOME 을 주니 그대로 둔다.
#
# 고아 감시(0-42, 2026-10-03): 메모리가 모자라면 Claude Code 가 백그라운드 셸만 죽이고 그 아래 Maven·테스트 JVM·
# Testcontainers 컨테이너는 남아 메모리를 계속 잡는다. 그래서 윈도에선 맨 위 셸(ppid 1 인 MSYS 조상)을 적어 두고
# 감시 루프가 그 셸이 사라지면 이 실행의 JVM 을 끈다(`reap-maven.sh`). 이 스크립트째 죽은 경우는
# 훅 `orphan-reap.sh`(Stop·UserPromptSubmit)가 `.git/mvn-runs/` 의 기록을 보고 끈다.
cd "$(dirname "$0")/.."
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) export JAVA_HOME="C:/Program Files/Java/jdk-17.0.19" ;;
  *) exec ./mvnw "$@" ;;
esac

root=$$
while pp=$(cat "/proc/$root/ppid" 2>/dev/null) && [ "$pp" != 1 ]; do root=$pp; done
start=$(date +%s)
mkdir -p .git/mvn-runs
rec=.git/mvn-runs/$$
echo "$root $start" > "$rec"

./mvnw "$@" &
m=$!
trap 'kill "$m" 2>/dev/null' INT TERM
(
  while kill -0 "$m" 2>/dev/null; do
    if ! kill -0 "$root" 2>/dev/null; then
      bash scripts/reap-maven.sh "$start" "감시(맨 위 셸 $root 없음)" >/dev/null
      rm -f "$rec"
      exit 0
    fi
    sleep 5
  done
) &
w=$!
wait "$m"; rc=$?
kill "$w" 2>/dev/null
# mvnw 셸이 먼저 죽으면(셸 그룹째 종료) 윈도 네이티브 java 는 남는다 — 이 실행 뒤에 뜬 Maven·surefire JVM 이 남았으면 끈다
left=$(bash scripts/reap-maven.sh "$start" "mvn.sh 끝" | tr "\n" " ")
[ -n "${left// /}" ] && { echo "mvn.sh: 남은 JVM 을 껐다 — ${left}" >&2; }
rm -f "$rec"
exit "$rc"
