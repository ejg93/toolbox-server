#!/usr/bin/env bash
# 실물 표본이 있나(V-1) — verify.sh --full 이 mvn 앞에 부른다. 표본은 저장소 밖이라 CI 에는 없다.
#   로컬: 폴더가 없으면 빨강 + 받는 법. CI(CI=true): 건너뜀을 알리고 초록 — 호출한 쪽이 corpus 태그를 뺀다.
set -uo pipefail
R=$(cd "$(dirname "$0")/.." && pwd)
CORPUS="${TOOLBOX_CORPUS:-$(dirname "$R")/toolbox-corpus}"
if [ "${CI:-}" = "true" ]; then
  echo "표본 건너뜀(CI) — 실물 표본은 저장소 밖이라 로컬 --full 에서만 잰다"
  exit 0
fi
if [ ! -d "$CORPUS" ]; then
  echo "실물 표본 폴더가 없다: $CORPUS — bash scripts/corpus-fetch.sh 먼저(PLAN 4장)" >&2
  exit 1
fi
echo "표본: $CORPUS"
