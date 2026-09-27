#!/usr/bin/env bash
# CI offline 잡이 오프라인 빌드를 돌릴지(0-29·0-37·0-38). verify.yml 이 이것만 부른다 — 판단을 hooks-test 로 잰다.
#
#   bash scripts/offline-needed.sh <event> <before>
#
# stdout 끝 줄 `run=true|false`(GITHUB_OUTPUT 에 그대로 붙인다), 그 앞 줄에 기준 커밋과 판단 문구.
#   workflow_dispatch → 늘 돈다
#   before 가 비었거나 0 이거나 없는 커밋(새 가지 첫 push·강제 push) → main 과 갈라진 자리부터, 그것도 안 되면 HEAD~1
#   기준..HEAD 사이에 pom.xml·.mvn/ 이 바뀌었으면 돈다
set -uo pipefail
event=${1:-}
before=${2:-}
if [ "$event" = "workflow_dispatch" ]; then
  echo "손으로 부른 실행 — 오프라인 빌드를 돌린다"
  echo "run=true"
  exit 0
fi
if [ -z "$before" ] || [ "$before" = "0000000000000000000000000000000000000000" ] || ! git cat-file -e "$before^{commit}" 2>/dev/null; then
  base=$(git merge-base origin/main HEAD 2>/dev/null || git rev-parse HEAD~1)
else
  base=$before
fi
# 잡은 건너뛰어도 success 로 뜬다 — 돈 것처럼 보이니 기준 커밋과 판단을 찍는다(0-37)
echo "기준 $(git rev-parse --short "$base") → HEAD $(git rev-parse --short HEAD)"
if git diff --name-only "$base" HEAD | grep -qE '^(pom\.xml|\.mvn/)'; then
  echo "pom.xml·.mvn 이 바뀌었다 — 오프라인 빌드를 돌린다"
  echo "run=true"
else
  echo "pom.xml·.mvn 이 안 바뀌었다 — 건너뛴다"
  echo "run=false"
fi
