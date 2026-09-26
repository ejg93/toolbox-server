#!/usr/bin/env bash
# PreToolUse(Bash·PowerShell). `mvnw` 를 맨몸으로 부르는 명령을 막는다 — 이 기계의 JAVA_HOME 은 JDK 11 이라 17 코드가 안 된다.
# `scripts/mvn.sh` 가 JDK 17 을 잡아 준다. JAVA_HOME 을 직접 붙인 줄과 한글이 든 산문 줄은 지나간다.
. "$(dirname "$0")/_tool-input.sh"
c=$(tool_field command)

if printf '%s\n' "$c" | perl -CS -ne 'print unless /\p{Hangul}/' | grep -qE '(^|[;&|])[[:space:]]*(\./)?mvnw(\.cmd)?([[:space:]]|$)' \
   && ! printf '%s' "$c" | grep -qE 'JAVA_HOME[[:space:]]*=|\$env:JAVA_HOME'; then
  echo 'mvnw 는 bash scripts/mvn.sh 로 부른다(CLAUDE.md 「빌드·실행」). 이 기계의 기본 JAVA_HOME 은 JDK 11 이다.' >&2
  exit 2
fi
exit 0
