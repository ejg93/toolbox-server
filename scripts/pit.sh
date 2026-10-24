#!/usr/bin/env bash
# PIT 뮤테이션 시험(0-65, 설계 38) — core.check 만, 손으로만. 리포트 out/pit/index.html. 게이트 아님.
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/mvn.sh -B -DexcludedGroups=db,corpus -Djacoco.skip=true test-compile org.pitest:pitest-maven:mutationCoverage
echo "리포트: out/pit/index.html"
