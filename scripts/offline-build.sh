#!/usr/bin/env bash
# 반입용 오프라인 저장소 `m2/` 를 채우고, 그것만으로 빌드되는지 본다(CLAUDE.md 절대 규칙 5, PLAN 13장 0-12).
#
#   bash scripts/offline-build.sh          m2/ 채우기 → -o 빌드
#   bash scripts/offline-build.sh --check  m2/ 를 안 채우고 -o 빌드만(네트워크 끊고 돌리는 리허설)
set -uo pipefail
cd "$(dirname "$0")/.."
repo="$(pwd)/m2"
if [ "${1:-}" != "--check" ]; then
  echo "== m2/ 채우기"
  bash scripts/mvn.sh -q -B -Dmaven.repo.local="$repo" dependency:go-offline dependency:resolve-plugins || exit 1
fi
echo "== 오프라인 빌드"
bash scripts/mvn.sh -q -B -o -Dmaven.repo.local="$repo" -DskipTests package || { echo "오프라인 빌드 실패 — m2/ 에 빠진 것이 있다"; exit 1; }
echo "오프라인 빌드 초록. m2/ 크기: $(du -sh "$repo" | cut -f1)"
