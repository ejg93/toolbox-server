#!/usr/bin/env bash
# 2-4 — 산출물 예시 양식(templates/deliverable/example/*.xlsx)과 예시 매핑(mappings/deliverable/example.yaml)을 값 표 열로 다시 만든다.
# 값 표 열(core.deliverable 의 COLS_*)을 바꾸면 이걸 돌려 커밋한다. XlsxFillerTest 가 커밋된 양식과 열이 같은지 잰다.
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/mvn.sh -q -B -DskipTests package
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) JAVA_HOME="C:/Program Files/Java/jdk-17.0.19" ;; esac
"${JAVA_HOME:?JAVA_HOME 이 없다}/bin/java" -jar target/app.jar make-templates \
  --out templates/deliverable/example --mapping mappings/deliverable/example.yaml
